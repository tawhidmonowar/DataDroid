package org.onedroid.dataset.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.UnknownImage
import org.onedroid.dataset.domain.repository.UnknownImageRepository

/**
 * Keeps unknown images in their dataset's `unknown` folder: `<id>.image` holds the bytes as they were added and
 * `<id>.jpg` the preview. An image is written before its preview and only counts once both are there, so an
 * interrupted add or remove never leaves half an image; leftovers go when the folder is next read. Previews of
 * datasets read so far are held in memory.
 */
class FileUnknownImageRepository(
    private val paths: StoragePaths,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UnknownImageRepository {
    private val mutex = Mutex()

    /** Each dataset's unknown images read so far, oldest first. */
    private val loaded = MutableStateFlow<Map<String, List<UnknownImage>>>(emptyMap())

    /** Guarded by [mutex]: the id each dataset's next image gets. Ids aren't reused while the app runs. */
    private val nextIds = HashMap<String, Long>()

    override fun observe(datasetId: String): Flow<List<UnknownImage>> = flow {
        locked(datasetId) { }
        emitAll(loaded.map { it[datasetId].orEmpty() }.distinctUntilChanged())
    }

    override suspend fun add(datasetId: String, image: ByteArray, preview: ByteArray): Result<Long, DataError.Local> = catching {
        locked(datasetId) { images ->
            // Writing into a deleted dataset would bring its folder back.
            if (!fileSystem.exists(Path(paths.datasetsRoot, datasetId, DatasetFiles.RECORD_FILE))) throw NotFoundException()
            val id = nextIds.getValue(datasetId)
            fileSystem.writeAtomically(imageFile(datasetId, id), image)
            fileSystem.writeAtomically(previewFile(datasetId, id), preview)
            nextIds[datasetId] = id + 1
            loaded.update { it + (datasetId to images + UnknownImage(id, preview)) }
            id
        }
    }

    override suspend fun read(datasetId: String, id: Long): Result<ByteArray, DataError.Local> = catching {
        withContext(ioDispatcher) {
            val file = imageFile(datasetId, id)
            if (!fileSystem.exists(file)) throw NotFoundException()
            fileSystem.source(file).buffered().use { it.readByteArray() }
        }
    }

    override suspend fun remove(datasetId: String, ids: Set<Long>): EmptyResult<DataError.Local> = catching {
        locked(datasetId) { images ->
            val removed = images.filter { it.id in ids }
            if (removed.isEmpty()) return@locked
            // Preview first: an image left without one is cleared away on the next read.
            removed.forEach { image ->
                fileSystem.delete(previewFile(datasetId, image.id), mustExist = false)
                fileSystem.delete(imageFile(datasetId, image.id), mustExist = false)
            }
            loaded.update { it + (datasetId to images - removed.toSet()) }
        }
    }

    /** Runs [block] with the dataset's unknown images, reading them first if they haven't been. */
    private suspend fun <T> locked(datasetId: String, block: (List<UnknownImage>) -> T): T = mutex.withLock {
        withContext(ioDispatcher) {
            val images = loaded.value[datasetId] ?: readAll(datasetId).also { images ->
                loaded.update { it + (datasetId to images) }
                nextIds[datasetId] = (images.lastOrNull()?.id ?: 0L) + 1
            }
            block(images)
        }
    }

    /** The complete images in the dataset's folder, oldest first; anything else there is deleted. */
    private fun readAll(datasetId: String): List<UnknownImage> {
        val directory = directory(datasetId)
        if (!fileSystem.exists(directory)) return emptyList()
        val files = fileSystem.list(directory)
        val names = files.map { it.name }.toSet()
        val ids = names.mapNotNull { it.removeSuffix(IMAGE_SUFFIX).takeIf { id -> id != it }?.toLongOrNull() }
            .filter { "$it$PREVIEW_SUFFIX" in names }
            .sorted()
        val complete = ids.flatMap { listOf("$it$IMAGE_SUFFIX", "$it$PREVIEW_SUFFIX") }.toSet()
        files.filter { it.name !in complete }.forEach { runCatching { fileSystem.delete(it, mustExist = false) } }
        return ids.map { id -> UnknownImage(id, fileSystem.source(previewFile(datasetId, id)).buffered().use { it.readByteArray() }) }
    }

    private fun directory(datasetId: String) = Path(paths.datasetsRoot, datasetId, DIRECTORY)

    private fun imageFile(datasetId: String, id: Long) = Path(directory(datasetId), "$id$IMAGE_SUFFIX")

    private fun previewFile(datasetId: String, id: Long) = Path(directory(datasetId), "$id$PREVIEW_SUFFIX")

    private suspend fun <T> catching(block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: Exception) {
        Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
    }

    private class NotFoundException : Exception()

    private companion object {
        const val DIRECTORY = "unknown"
        const val IMAGE_SUFFIX = ".image"
        const val PREVIEW_SUFFIX = ".jpg"
    }
}
