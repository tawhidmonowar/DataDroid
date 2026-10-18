package org.onedroid.dataset.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.SerializationException
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.data.archive.ArchiveImporter
import org.onedroid.dataset.data.archive.DatasetExporter
import org.onedroid.dataset.data.archive.ZipTooLargeException
import org.onedroid.dataset.data.image.ImageFormat
import org.onedroid.dataset.data.image.ImageProcessor
import org.onedroid.dataset.domain.model.ArchiveContents
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.DatasetSummary
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.model.ResizeMode
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.ProgressListener
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.domain.usecase.uniqueName
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Datasets stored as folders of image files plus a JSON record (see [DatasetFiles]). All records are
 * kept in memory once loaded, so observers never wait on disk; writes go through one lock and update the
 * file before the in-memory copy.
 */
class FileDatasetRepository(
    private val paths: StoragePaths,
    private val imageProcessor: ImageProcessor,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** The image settings new datasets start with. */
    private val defaultImageSettings: suspend () -> ImageSettings = { ImageSettings() },
) : DatasetRepository {
    private val files = DatasetFiles(paths.datasetsRoot, fileSystem)
    private val exporter = DatasetExporter(fileSystem)
    private val mutex = Mutex()

    /** Null until first loaded from disk. */
    private val records = MutableStateFlow<Map<String, DatasetRecord>?>(null)

    override fun observeDatasets(): Flow<List<DatasetSummary>> = flow {
        locked { }
        emitAll(
            records.filterNotNull().map { all ->
                all.map { (id, record) -> record.toSummary(id) }
                    .sortedWith(compareByDescending<DatasetSummary> { it.updatedAt }.thenBy { it.name.lowercase() })
            }
        )
    }.distinctUntilChanged()

    override fun observeDataset(id: String): Flow<Dataset?> = flow {
        locked { }
        emitAll(records.filterNotNull().map { it[id] }.distinctUntilChanged().map { it?.toDataset(id) })
    }

    override suspend fun createDataset(name: String): Result<String, DataError.Local> = catching {
        val settings = defaultImageSettings()
        locked { createLocked(name, listOf(STARTER_CLASS), settings) }
    }

    override suspend fun renameDataset(id: String, name: String): EmptyResult<DataError.Local> = catching {
        locked { commit(id, current(id).copy(name = name.trim())) }
    }

    override suspend fun deleteDataset(id: String): EmptyResult<DataError.Local> = catching {
        locked {
            files.deleteDataset(id)
            records.update { it.orEmpty() - id }
        }
    }

    override suspend fun addClass(datasetId: String, name: String): Result<Int, DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            val classId = record.nextClassId
            commit(datasetId, record.copy(nextClassId = classId + 1, classes = record.classes + ClassRecord(classId, name.trim())))
            classId
        }
    }

    override suspend fun renameClass(datasetId: String, classId: Int, name: String): EmptyResult<DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            if (record.classes.none { it.id == classId }) throw NotFoundException()
            commit(datasetId, record.copy(classes = record.classes.map { if (it.id == classId) it.copy(name = name.trim()) else it }))
        }
    }

    override suspend fun deleteClass(datasetId: String, classId: Int): EmptyResult<DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            val removed = record.classes.firstOrNull { it.id == classId } ?: throw NotFoundException()
            commit(datasetId, record.copy(classes = record.classes - removed))
            removed.images.forEach { files.deleteImage(datasetId, it) }
        }
    }

    override suspend fun addImages(
        datasetId: String,
        classId: Int,
        sources: List<ImageSource>,
        onProgress: ProgressListener,
    ): Result<ImportResult, DataError.Local> = catching {
        addAll(datasetId, sources.map { classId to it }, onProgress)
    }

    override suspend fun importLabeledImages(
        datasetId: String,
        groups: Map<String, List<ImageSource>>,
        onProgress: ProgressListener,
    ): Result<ImportResult, DataError.Local> = catching {
        val classIds = locked { ensureClasses(datasetId, groups.keys) }
        addAll(datasetId, groups.flatMap { (name, sources) -> sources.map { classIds.getValue(name) to it } }, onProgress)
    }

    override suspend fun deleteImages(datasetId: String, imageIds: Set<Long>): EmptyResult<DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            val removed = record.classes.flatMap { it.images }.filter { it.id in imageIds }
            commit(datasetId, record.copy(classes = record.classes.map { c -> c.copy(images = c.images.filter { it.id !in imageIds }) }))
            removed.forEach { files.deleteImage(datasetId, it) }
        }
    }

    override suspend fun moveImages(datasetId: String, imageIds: Set<Long>, toClassId: Int): EmptyResult<DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            if (record.classes.none { it.id == toClassId }) throw NotFoundException()
            val moving = record.classes.filter { it.id != toClassId }.flatMap { it.images }.filter { it.id in imageIds }
            if (moving.isEmpty()) return@locked
            val movingIds = moving.map { it.id }.toSet()
            val classes = record.classes.map { c ->
                if (c.id == toClassId) c.copy(images = c.images + moving) else c.copy(images = c.images.filter { it.id !in movingIds })
            }
            commit(datasetId, record.copy(classes = classes))
        }
    }

    override suspend fun readImage(datasetId: String, imageId: Long): Result<ByteArray, DataError.Local> = catching {
        val image = locked { current(datasetId).findImage(imageId) }
        withContext(ioDispatcher) { files.readOrNull(files.imageFile(datasetId, image)) } ?: throw NotFoundException()
    }

    override suspend fun readThumbnail(datasetId: String, imageId: Long): Result<ByteArray, DataError.Local> = catching {
        ensureLoaded()
        withContext(ioDispatcher) { files.readOrNull(files.thumbnailFile(datasetId, imageId)) } ?: throw NotFoundException()
    }

    override suspend fun exportDataset(datasetId: String, onProgress: ProgressListener): Result<ExportedFile, DataError> {
        val record = when (val snapshot = catching { locked { current(datasetId) } }) {
            is Result.Error -> return snapshot
            is Result.Success -> snapshot.data
        }
        if (record.classes.all { it.images.isEmpty() }) return Result.Error(DataError.Archive.EMPTY_DATASET)
        return catching {
            withContext(ioDispatcher) {
                val directory = paths.exportDirectory
                // Only the latest export is kept; older ones have been shared already.
                files.deleteRecursively(directory)
                fileSystem.createDirectories(directory)
                val target = Path(directory, ExportNaming.fileName(record.name))
                exporter.export(
                    record = record,
                    target = target,
                    readImage = { image -> files.readOrNull(files.imageFile(datasetId, image)) },
                    onProgress = onProgress,
                )
                ExportedFile(target.toString(), DatasetExporter.MIME_TYPE)
            }
        }
    }

    override suspend fun readArchive(archive: ByteArray): Result<ArchiveContents, DataError.Archive> {
        val contents = try {
            withContext(Dispatchers.Default) { ArchiveImporter.read(archive) }
        } catch (e: ZipTooLargeException) {
            return Result.Error(DataError.Archive.TOO_LARGE)
        } catch (e: IllegalArgumentException) {
            return Result.Error(DataError.Archive.NOT_A_ZIP)
        }
        if (contents.images.isEmpty()) return Result.Error(DataError.Archive.NO_IMAGES)
        return Result.Success(contents)
    }

    override suspend fun importDataset(
        name: String?,
        groups: Map<String, List<ImageSource>>,
        onProgress: ProgressListener,
    ): Result<String, DataError> {
        if (groups.isEmpty()) return Result.Error(DataError.Archive.NO_IMAGES)
        val created = catching {
            val settings = defaultImageSettings()
            locked { createLocked(name?.takeIf { it.isNotBlank() } ?: IMPORTED_NAME, classNames = emptyList(), settings) }
        }
        val id = when (created) {
            is Result.Error -> return created
            is Result.Success -> created.data
        }
        val imported = try {
            importLabeledImages(id, groups, onProgress)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { deleteDataset(id) }
            throw e
        }
        return when {
            imported is Result.Error -> {
                deleteDataset(id)
                imported
            }
            (imported as Result.Success).data.added == 0 -> {
                deleteDataset(id)
                Result.Error(DataError.Archive.NO_IMAGES)
            }
            else -> Result.Success(id)
        }
    }

    override suspend fun updateImageSettings(datasetId: String, settings: ImageSettings): EmptyResult<DataError.Local> = catching {
        locked {
            val record = current(datasetId)
            val sanitized = settings.sanitized()
            if (sanitized == record.imageSettings) return@locked
            val revision = record.settingsRevision + 1
            // Untouched originals can't be brought back, so with passthrough settings every image is current.
            val classes = if (!sanitized.isPassthrough) record.classes else record.classes.map { c ->
                c.copy(images = c.images.map { it.copy(revision = revision) })
            }
            commit(datasetId, record.copy(imageSettings = sanitized, settingsRevision = revision, classes = classes))
        }
    }

    /**
     * Converts outdated images a few at a time off the lock, then swaps each into the record on its own.
     * A new file under a different extension sits beside the old one until the record pointing to it is
     * saved; only then is the old file deleted, so an interruption never leaves the record pointing at a
     * missing file. Leftovers are removed by [DatasetFiles.deleteUnreferencedFiles] on the next load.
     */
    override suspend fun convertImages(datasetId: String, onProgress: ProgressListener): Result<ImportResult, DataError.Local> = catching {
        val (snapshot, pending) = locked { current(datasetId).let { it to it.outdatedImages } }
        val settings = snapshot.imageSettings
        val revision = snapshot.settingsRevision
        var converted = 0
        var skipped = 0
        var unsaved = 0
        val replaced = mutableListOf<ImageRecord>()

        // Deletes old files once the record no longer refers to them. Call while locked.
        fun save() {
            records.value?.get(datasetId)?.let { files.writeRecord(datasetId, it) }
            replaced.forEach { files.deleteImageFile(datasetId, it) }
            replaced.clear()
            unsaved = 0
        }

        onProgress(0, pending.size)
        try {
            for (chunk in pending.chunked(parallelism(settings))) {
                val results = coroutineScope {
                    chunk.map { image ->
                        async {
                            val bytes = withContext(ioDispatcher) { files.readOrNull(files.imageFile(datasetId, image)) }
                            bytes?.let { process(it, settings) }
                        }
                    }.awaitAll()
                }
                val stillCurrent = locked {
                    val record = records.value?.get(datasetId) ?: return@locked false
                    // The settings changed again; the rest are converted by the next run.
                    if (record.settingsRevision != revision) return@locked false
                    var updated = record
                    chunk.zip(results) { image, result ->
                        // Deleted meanwhile; nothing has been written for it yet.
                        val existing = updated.findImageOrNull(image.id) ?: return@zip
                        val next = if (result == null) {
                            skipped++
                            existing.copy(revision = revision)
                        } else {
                            val next = ImageRecord(existing.id, result.extension, revision)
                            files.writeImage(datasetId, next, result.bytes, result.thumbnail)
                            if (next.extension != existing.extension) replaced += existing
                            converted++
                            next
                        }
                        updated = updated.replaceImage(next)
                        unsaved++
                    }
                    commit(datasetId, updated, persist = false)
                    if (unsaved >= SAVE_EVERY) save()
                    true
                }
                onProgress(converted + skipped, pending.size)
                if (!stillCurrent) break
            }
        } finally {
            withContext(NonCancellable) { locked { if (unsaved > 0 || replaced.isNotEmpty()) save() } }
        }
        ImportResult(converted, skipped)
    }

    /**
     * Adds images in order. Decoding runs a few at a time off the lock; each image is then committed on
     * its own, writing the record every [SAVE_EVERY] images and once more at the end (even if cancelled).
     */
    private suspend fun addAll(
        datasetId: String,
        items: List<Pair<Int, ImageSource>>,
        onProgress: ProgressListener,
    ): ImportResult {
        var added = 0
        var skipped = 0
        var unsaved = false
        // Images keep the settings they were prepared with, so a change mid-import marks the rest outdated.
        val (settings, revision) = locked { current(datasetId).let { it.imageSettings to it.settingsRevision } }
        onProgress(0, items.size)
        try {
            items.chunked(parallelism(settings)).forEach { chunk ->
                val prepared = coroutineScope { chunk.map { (_, source) -> async { prepare(source, settings) } }.awaitAll() }
                chunk.zip(prepared) { (classId, _), image ->
                    val committed = image != null && locked {
                        val record = current(datasetId)
                        val target = record.classes.firstOrNull { it.id == classId } ?: return@locked false
                        val imageRecord = ImageRecord(record.nextImageId, image.extension, revision)
                        files.writeImage(datasetId, imageRecord, image.bytes, image.thumbnail)
                        val updated = record.copy(
                            nextImageId = record.nextImageId + 1,
                            classes = record.classes.map { if (it === target) it.copy(images = it.images + imageRecord) else it },
                        )
                        unsaved = (added + 1) % SAVE_EVERY != 0
                        commit(datasetId, updated, persist = !unsaved)
                        true
                    }
                    if (committed) added++ else skipped++
                    onProgress(added + skipped, items.size)
                }
            }
        } finally {
            if (unsaved) {
                withContext(NonCancellable) {
                    locked { records.value?.get(datasetId)?.let { files.writeRecord(datasetId, it) } }
                }
            }
        }
        return ImportResult(added, skipped)
    }

    private class PreparedImage(val bytes: ByteArray, val extension: String, val thumbnail: ByteArray)

    /** Reads, validates and converts an image, or returns null if it can't be read or decoded. */
    private suspend fun prepare(source: ImageSource, settings: ImageSettings): PreparedImage? {
        val bytes = try {
            source.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return process(bytes, settings)
    }

    /** [bytes] stored as [settings] say, with a thumbnail; null if they aren't a decodable image. */
    private suspend fun process(bytes: ByteArray, settings: ImageSettings): PreparedImage? {
        if (bytes.size > ArchiveImporter.MAX_IMAGE_SIZE) return null
        val extension = ImageFormat.extensionOf(bytes) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val thumbnail = imageProcessor.thumbnail(bytes, THUMBNAIL_SIZE)
                if (settings.isPassthrough) {
                    PreparedImage(bytes, extension, thumbnail)
                } else {
                    val converted = imageProcessor.convert(bytes, settings)
                    PreparedImage(converted.bytes, converted.extension, thumbnail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Full-size decodes take far more memory, so they run one at a time. */
    private fun parallelism(settings: ImageSettings) =
        if (!settings.isPassthrough && settings.resize == ResizeMode.ORIGINAL) 1 else DECODE_PARALLELISM

    /** Ids of the classes named [names] (ignoring case), creating the missing ones. Call while locked. */
    private fun ensureClasses(datasetId: String, names: Collection<String>): Map<String, Int> {
        var record = current(datasetId)
        val ids = names.associateWith { name ->
            record.classes.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id ?: run {
                val classId = record.nextClassId
                record = record.copy(nextClassId = classId + 1, classes = record.classes + ClassRecord(classId, name.trim()))
                classId
            }
        }
        commit(datasetId, record)
        return ids
    }

    private fun createLocked(name: String, classNames: List<String>, settings: ImageSettings): String {
        val taken = records.value.orEmpty().values.map { it.name }
        val id = newId()
        val now = clock()
        val record = DatasetRecord(
            name = uniqueName(name.trim(), taken),
            createdAt = now,
            updatedAt = now,
            nextClassId = classNames.size + 1,
            nextImageId = 1,
            classes = classNames.mapIndexed { index, className -> ClassRecord(index + 1, className) },
            imageSettings = settings.sanitized(),
        )
        files.writeRecord(id, record)
        records.update { it.orEmpty() + (id to record) }
        return id
    }

    private fun current(id: String): DatasetRecord = records.value?.get(id) ?: throw NotFoundException()

    private fun DatasetRecord.findImage(imageId: Long): ImageRecord = findImageOrNull(imageId) ?: throw NotFoundException()

    private fun DatasetRecord.findImageOrNull(imageId: Long): ImageRecord? =
        classes.firstNotNullOfOrNull { c -> c.images.firstOrNull { it.id == imageId } }

    private fun DatasetRecord.replaceImage(image: ImageRecord): DatasetRecord =
        copy(classes = classes.map { c -> c.copy(images = c.images.map { if (it.id == image.id) image else it }) })

    /** Saves [record] (unless [persist] is false) and publishes it. Call while locked. */
    private fun commit(id: String, record: DatasetRecord, persist: Boolean = true) {
        val stamped = record.copy(updatedAt = clock())
        if (persist) files.writeRecord(id, stamped)
        records.update { it.orEmpty() + (id to stamped) }
    }

    private suspend fun ensureLoaded() {
        if (records.value == null) locked { }
    }

    /**
     * Queues on the lock before hopping to [ioDispatcher], so calls run in the order they were made, and
     * loads every record on first use.
     */
    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        withContext(ioDispatcher) {
            if (records.value == null) records.value = loadAll()
            block()
        }
    }

    /** Every readable record; datasets whose record is missing or corrupt are left out. */
    private fun loadAll(): Map<String, DatasetRecord> = files.datasetIds().mapNotNull { id ->
        runCatching { files.readRecord(id) }.getOrNull()?.let { record ->
            runCatching { files.deleteUnreferencedFiles(id, record) }
            id to record
        }
    }.toMap()

    private fun newId(): String {
        var id: String
        do {
            id = "d" + Random.nextLong().toULong().toString(16)
        } while (files.exists(id))
        return id
    }

    /** Runs [block], turning expected storage failures into [DataError.Local]. */
    private suspend fun <T> catching(block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: SerializationException) {
        Result.Error(DataError.Local.CORRUPT)
    } catch (e: IOException) {
        Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
    } catch (e: Exception) {
        Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
    }

    private fun Throwable.isDiskFull(): Boolean {
        val text = generateSequence(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return "ENOSPC" in text || "No space left" in text
    }

    private class NotFoundException : Exception()

    private companion object {
        const val STARTER_CLASS = "Class 1"
        const val IMPORTED_NAME = "Imported dataset"
        const val THUMBNAIL_SIZE = 192
        const val SAVE_EVERY = 25
        const val DECODE_PARALLELISM = 4
    }
}
