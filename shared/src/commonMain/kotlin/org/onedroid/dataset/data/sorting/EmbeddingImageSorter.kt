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
import org.onedroid.dataset.data.archive.Crc32
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
import kotlinx.coroutines.flow.Flow
import org.onedroid.dataset.core.platform.ml.BackboneClosedException
import org.onedroid.dataset.core.platform.ml.FeatureExtractor
import org.onedroid.dataset.core.platform.ml.SharedBackbone
import org.onedroid.dataset.core.platform.ml.spec
import org.onedroid.dataset.domain.model.EmbeddingModel

/**
 * Sorts images by comparing embeddings from the chosen [EmbeddingModel]. Each image's embedding is cached in
 * a file per model in its dataset's folder (appended as images are embedded, so it goes when the dataset
 * does) and kept in memory for the dataset and model sorted most recently. Images embedded just before
 * they're saved, as auto-sort does, are recognised by their bytes when they turn up in the dataset, so they
 * aren't embedded a second time.
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

    /** Guarded by [mutex]: the cached dataset and model, its embeddings by image id, and records in its file. */
    private var cachedDatasetId: String? = null
    private var cachedModel: EmbeddingModel? = null
    private var cache = HashMap<Long, FloatArray>()
    private var fileRecords = 0

    /** Images that couldn't be read or decoded, so they aren't retried on every change. */
    private val unreadable = HashSet<Long>()

    /** Guarded by [mutex]: refits the cached dataset's matcher, reusing work from the last fit. */
    private var fitter = ClassMatcher.Fitter()

    /** Serialises fits, which run off [mutex] as they take a while. */
    private val fitMutex = Mutex()

    /** Embeddings of images about to be saved, by their bytes and model, oldest first. Guarded by [recentMutex]. */
    private val recent = LinkedHashMap<RecentKey, FloatArray>()
    private val recentMutex = Mutex()

    override val model: Flow<EmbeddingModel> get() = backbone.model

    override suspend fun matcherFor(dataset: Dataset, onProgress: ProgressListener): Result<ClassMatcher, DataError.Sorting> =
        withExtractor { extractor -> fitFor(dataset, extractor, onProgress) }

    private suspend fun fitFor(dataset: Dataset, extractor: FeatureExtractor, onProgress: ProgressListener): ClassMatcher {
        val model = extractor.model
        val imageIds = dataset.classes.flatMap { it.imageIds }
        val missing = mutex.withLock {
            load(dataset.id, model)
            imageIds.filter { it !in cache && it !in unreadable }
        }
        if (missing.isNotEmpty()) {
            onProgress(0, missing.size)
            var done = 0
            missing.chunked(PARALLELISM).forEach { chunk ->
                val embedded = coroutineScope {
                    chunk.map { imageId -> async { imageId to embedStored(extractor, dataset.id, imageId) } }.awaitAll()
                }
                withContext(NonCancellable) { mutex.withLock { remember(dataset.id, model, embedded) } }
                done += chunk.size
                onProgress(done, missing.size)
            }
        }
        val (byClass, classFitter) = mutex.withLock {
            // The model changed while embedding: the next try embeds again with the new one.
            if (cachedModel != model) throw BackboneClosedException()
            compactIfStale(dataset.id, imageIds.toSet())
            dataset.classes.associate { c -> c.id to c.imageIds.mapNotNull { cache[it] } } to fitter
        }
        // Fitting compares each class's images with each other, too slow for the caller's (often main) thread.
        return withContext(Dispatchers.Default) { fitMutex.withLock { classFitter.fit(byClass) } }
    }

    override suspend fun embed(image: ByteArray, saving: Boolean): Result<FloatArray?, DataError.Sorting> =
        withExtractor { extractor ->
            val embedding = embedBytes(extractor, image)
            if (saving && embedding != null) {
                val key = RecentKey(withContext(Dispatchers.Default) { ContentKey.of(image) }, extractor.model)
                recentMutex.withLock {
                    recent.remove(key)
                    recent[key] = embedding
                    if (recent.size > RECENT_CAPACITY) recent.remove(recent.keys.first())
                }
            }
            embedding
        }

    override suspend fun preview(image: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        decodingOrNull { imageProcessor.thumbnail(image, PREVIEW_SIZE) }
    }

    /**
     * Runs [block] with the chosen model's extractor, or with the next one if the model is changed meanwhile
     * (which frees the extractor [block] had).
     */
    private suspend fun <T> withExtractor(block: suspend (FeatureExtractor) -> T): Result<T, DataError.Sorting> {
        repeat(MODEL_ATTEMPTS) {
            val extractor = backbone.await().getOrNull() ?: return Result.Error(DataError.Sorting.MODEL_UNAVAILABLE)
            try {
                return Result.Success(block(extractor))
            } catch (e: BackboneClosedException) {
                // Try again with the model chosen now.
            }
        }
        return Result.Error(DataError.Sorting.MODEL_UNAVAILABLE)
    }

    private suspend fun embedStored(extractor: FeatureExtractor, datasetId: String, imageId: Long): FloatArray? {
        val bytes = repository.readImage(datasetId, imageId).getOrNull() ?: return null
        return takeRecent(bytes, extractor.model) ?: embedBytes(extractor, bytes)
    }

    /**
     * The embedding [embed] kept for [bytes] with [model], if it kept one; each is handed out once, then lives
     * in the cache.
     */
    private suspend fun takeRecent(bytes: ByteArray, model: EmbeddingModel): FloatArray? {
        if (recentMutex.withLock { recent.isEmpty() }) return null
        val key = RecentKey(withContext(Dispatchers.Default) { ContentKey.of(bytes) }, model)
        return recentMutex.withLock { recent.remove(key) }
    }

    private suspend fun embedBytes(extractor: FeatureExtractor, bytes: ByteArray): FloatArray? {
        val image = withContext(Dispatchers.Default) {
            decodingOrNull { decode(bytes, extractor.model.spec.inputSize) }
        } ?: return null
        return extractor.embed(image)
    }

    /** Adds freshly embedded images to the cache and appends them to the file. Call while locked. */
    private suspend fun remember(datasetId: String, model: EmbeddingModel, embedded: List<Pair<Long, FloatArray?>>) {
        if (cachedDatasetId != datasetId || cachedModel != model) return
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
        buffer.writeInt(embeddingSize())
        cache.forEach { (id, embedding) -> buffer.writeRecord(id, embedding) }
        fileSystem.writeAtomically(cacheFile(datasetId), buffer.readByteArray())
        fileRecords = cache.size
    }

    /** Switches the in-memory cache to [datasetId]'s embeddings from [model], reading its file. Call while locked. */
    private suspend fun load(datasetId: String, model: EmbeddingModel) {
        if (cachedDatasetId == datasetId && cachedModel == model) return
        cachedDatasetId = datasetId
        cachedModel = model
        cache = HashMap()
        unreadable.clear()
        fitter = ClassMatcher.Fitter()
        fileRecords = 0
        withContext(ioDispatcher) {
            val file = cacheFile(datasetId)
            if (!fileSystem.exists(file)) return@withContext
            try {
                val size = model.spec.embeddingSize
                fileSystem.source(file).buffered().use { source ->
                    if (!source.request(HEADER_BYTES) || source.readInt() != MAGIC || source.readInt() != size) {
                        return@withContext
                    }
                    // A record cut short by an interrupted write is dropped, and fileRecords stays 0 so the next
                    // write replaces the file rather than appending after it.
                    var records = 0
                    while (source.request(8L + size * 4L)) {
                        val id = source.readLong()
                        cache[id] = FloatArray(size) { source.readFloat() }
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

    /** The cached model's cache file for [datasetId]. Call while locked, after [load]. */
    private fun cacheFile(datasetId: String) = Path(paths.datasetsRoot, datasetId, checkNotNull(cachedModel).spec.cacheFile)

    private fun embeddingSize() = checkNotNull(cachedModel).spec.embeddingSize

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

    /**
     * Identifies an image by its bytes: their size and two independent hashes, so a mix-up would need both to
     * collide at once.
     */
    private data class ContentKey(val size: Int, val crc: Int, val hash: Int) {
        companion object {
            fun of(bytes: ByteArray) = ContentKey(bytes.size, Crc32.of(bytes), bytes.contentHashCode())
        }
    }

    /** An image's bytes and the model that embedded them: embeddings from different models don't mix. */
    private data class RecentKey(val content: ContentKey, val model: EmbeddingModel)

    private companion object {
        /** Tries at an embedding job, each with the model chosen at the time; more than one only on a switch. */
        const val MODEL_ATTEMPTS = 3

        /** Embeddings kept for images about to be saved: enough for a long burst or a big pick, about 2 MB. */
        const val RECENT_CAPACITY = 512

        /** "DDE1": DataDroid embeddings, format 1. */
        const val MAGIC = 0x44444531
        const val HEADER_BYTES = 8L

        /** Stale records tolerated before compacting, so small datasets don't rewrite on every delete. */
        const val COMPACT_SLACK = 64
        const val PARALLELISM = 4
        const val PREVIEW_SIZE = 192
    }
}
