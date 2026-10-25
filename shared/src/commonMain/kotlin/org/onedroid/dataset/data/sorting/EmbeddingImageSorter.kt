package org.onedroid.dataset.data.sorting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readFloat
import kotlinx.io.writeFloat
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.getOrNull
import org.onedroid.dataset.data.image.ImageProcessor
import org.onedroid.dataset.data.storage.DatasetFiles
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.data.storage.writeAtomically
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.domain.repository.ProgressListener
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.core.platform.image.RgbImage
import org.onedroid.dataset.core.platform.image.decodeSquare
import org.onedroid.dataset.core.platform.ml.FeatureExtractor
import org.onedroid.dataset.core.platform.ml.MobileNetV3Small
import org.onedroid.dataset.core.platform.ml.SharedBackbone

/**
 * Sorts images by comparing MobileNet embeddings. Each image's embedding is cached in its dataset's
 * `embeddings.bin` (appended as images are embedded, so it goes when the dataset does) and kept in memory
 * for the dataset sorted most recently.
 */
class EmbeddingImageSorter(
    private val paths: StoragePaths,
    private val repository: DatasetRepository,
    private val backbone: SharedBackbone,
    private val imageProcessor: ImageProcessor,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Decodes image bytes to a square of the given size; see [decodeSquare]. */
    private val decode: (bytes: ByteArray, size: Int) -> RgbImage = ::decodeSquare,
) : ImageSorter {

    private val mutex = Mutex()

    /** Guarded by [mutex]: the cached dataset, its embeddings by image id, and records in its file. */
    private var cachedDatasetId: String? = null
    private var cache = HashMap<Long, FloatArray>()
    private var fileRecords = 0

    /** Images that couldn't be read or decoded, so they aren't retried on every change. */
    private val unreadable = HashSet<Long>()

    override suspend fun matcherFor(dataset: Dataset, onProgress: ProgressListener): Result<ClassMatcher, DataError.Sorting> {
        val extractor = extractor() ?: return Result.Error(DataError.Sorting.MODEL_UNAVAILABLE)
        val imageIds = dataset.classes.flatMap { it.imageIds }
        val missing = mutex.withLock {
            load(dataset.id)
            imageIds.filter { it !in cache && it !in unreadable }
        }
        if (missing.isNotEmpty()) {
            onProgress(0, missing.size)
            var done = 0
            missing.chunked(PARALLELISM).forEach { chunk ->
                val embedded = coroutineScope {
                    chunk.map { imageId -> async { imageId to embedStored(extractor, dataset.id, imageId) } }.awaitAll()
                }
                withContext(NonCancellable) { mutex.withLock { remember(dataset.id, embedded) } }
                done += chunk.size
                onProgress(done, missing.size)
            }
        }
        val byClass = mutex.withLock {
            compactIfStale(dataset.id, imageIds.toSet())
            dataset.classes.associate { c -> c.id to c.imageIds.mapNotNull { cache[it] } }
        }
        // Fitting compares each class's images with each other, too slow for the caller's (often main) thread.
        return Result.Success(withContext(Dispatchers.Default) { ClassMatcher.fit(byClass) })
    }

    override suspend fun embed(image: ByteArray): Result<FloatArray?, DataError.Sorting> {
        val extractor = extractor() ?: return Result.Error(DataError.Sorting.MODEL_UNAVAILABLE)
        return Result.Success(embedBytes(extractor, image))
    }

    override suspend fun preview(image: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        decodingOrNull { imageProcessor.thumbnail(image, PREVIEW_SIZE) }
    }

    private suspend fun extractor(): FeatureExtractor? = backbone.await().getOrNull()

    private suspend fun embedStored(extractor: FeatureExtractor, datasetId: String, imageId: Long): FloatArray? {
        val bytes = repository.readImage(datasetId, imageId).getOrNull() ?: return null
        return embedBytes(extractor, bytes)
    }

    private suspend fun embedBytes(extractor: FeatureExtractor, bytes: ByteArray): FloatArray? {
        val image = withContext(Dispatchers.Default) {
            decodingOrNull { decode(bytes, MobileNetV3Small.INPUT_SIZE) }
        } ?: return null
        return extractor.embed(image)
    }

    /** Adds freshly embedded images to the cache and appends them to the file. Call while locked. */
    private suspend fun remember(datasetId: String, embedded: List<Pair<Long, FloatArray?>>) {
        if (cachedDatasetId != datasetId) return
        val fresh = embedded.mapNotNull { (id, embedding) ->
            if (embedding == null) unreadable += id
            embedding?.let { id to it }
        }
        if (fresh.isEmpty()) return
        fresh.forEach { (id, embedding) -> cache[id] = embedding }
        persist {
            val file = cacheFile(datasetId)
            if (fileRecords > 0 && fileSystem.exists(file)) {
                fileSystem.sink(file, append = true).buffered().use { sink ->
                    fresh.forEach { (id, embedding) -> sink.writeRecord(id, embedding) }
                }
                fileRecords += fresh.size
            } else {
                rewrite(datasetId)
            }
        }
    }

    /** Rewrites the file without deleted images once they make up most of it. Call while locked. */
    private suspend fun compactIfStale(datasetId: String, imageIds: Set<Long>) {
        if (cachedDatasetId != datasetId) return
        cache.keys.retainAll(imageIds)
        unreadable.retainAll(imageIds)
        if (fileRecords <= cache.size * 2 + COMPACT_SLACK) return
        persist { rewrite(datasetId) }
    }

    /** Replaces the file with everything cached. Call while locked, from [persist]. */
    private fun rewrite(datasetId: String) {
        val buffer = Buffer()
        buffer.writeInt(MAGIC)
        buffer.writeInt(EMBEDDING_SIZE)
        cache.forEach { (id, embedding) -> buffer.writeRecord(id, embedding) }
        fileSystem.writeAtomically(cacheFile(datasetId), buffer.readByteArray())
        fileRecords = cache.size
    }

    /** Switches the in-memory cache to [datasetId], reading its file. Call while locked. */
    private suspend fun load(datasetId: String) {
        if (cachedDatasetId == datasetId) return
        cachedDatasetId = datasetId
        cache = HashMap()
        unreadable.clear()
        fileRecords = 0
        withContext(ioDispatcher) {
            val file = cacheFile(datasetId)
            if (!fileSystem.exists(file)) return@withContext
            try {
                fileSystem.source(file).buffered().use { source ->
                    if (!source.request(HEADER_BYTES) || source.readInt() != MAGIC || source.readInt() != EMBEDDING_SIZE) {
                        return@withContext
                    }
                    // A record cut short by an interrupted write is dropped, and fileRecords stays 0 so the next
                    // write replaces the file rather than appending after it.
                    var records = 0
                    while (source.request(RECORD_BYTES)) {
                        val id = source.readLong()
                        cache[id] = FloatArray(EMBEDDING_SIZE) { source.readFloat() }
                        records++
                    }
                    if (source.exhausted()) fileRecords = records
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cache.clear()
            }
        }
    }

    /**
     * Writes the cache file unless the dataset has been deleted (which would bring its folder back). The cache
     * only saves time, so a failed write just leaves it to be rebuilt.
     */
    private suspend fun persist(write: () -> Unit) = withContext(ioDispatcher) {
        val datasetDir = cacheFile(cachedDatasetId ?: return@withContext).parent ?: return@withContext
        if (!fileSystem.exists(Path(datasetDir, DatasetFiles.RECORD_FILE))) return@withContext
        try {
            write()
        } catch (e: Exception) {
            fileRecords = 0
            runCatching { fileSystem.delete(cacheFile(cachedDatasetId!!), mustExist = false) }
        }
    }

    private fun cacheFile(datasetId: String) = Path(paths.datasetsRoot, datasetId, CACHE_FILE)

    private fun Sink.writeRecord(id: Long, embedding: FloatArray) {
        writeLong(id)
        embedding.forEach { writeFloat(it) }
    }

    private inline fun <T> decodingOrNull(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val CACHE_FILE = "embeddings.bin"

        /** "DDE1": DataDroid embeddings, format 1. */
        const val MAGIC = 0x44444531
        const val EMBEDDING_SIZE = MobileNetV3Small.EMBEDDING_SIZE
        const val HEADER_BYTES = 8L
        const val RECORD_BYTES = 8L + EMBEDDING_SIZE * 4L

        /** Stale records tolerated before compacting, so small datasets don't rewrite on every delete. */
        const val COMPACT_SLACK = 64
        const val PARALLELISM = 4
        const val PREVIEW_SIZE = 192
    }
}
