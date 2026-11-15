package org.onedroid.dataset.core.platform.ml

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import org.onedroid.dataset.domain.model.EmbeddingModel
import org.onedroid.dataset.resources.Res

/**
 * Loads the chosen [model]'s backbone on first use and shares it between every open dataset. When [model]
 * changes, the next [await] loads the new one and frees the old one; anything still using the old one gets
 * a [BackboneClosedException] and should ask again.
 */
class SharedBackbone(
    private val loader: BackboneLoader?,
    private val scope: CoroutineScope,
    /** The model to use; read on each [await]. */
    val model: Flow<EmbeddingModel> = flowOf(EmbeddingModel.DEFAULT),
    private val readModel: suspend (EmbeddingModel) -> ByteArray = { Res.readBytes(it.spec.modelPath) },
) : AutoCloseable {

    @Volatile
    private var closed = false

    private val lock = Mutex()

    /** Guarded by [lock]: the model loaded or loading, and its extractor. */
    private var current: Loading? = null

    private class Loading(val model: EmbeddingModel, val extractor: Deferred<Result<FeatureExtractor>>)

    suspend fun await(): Result<FeatureExtractor> {
        while (true) {
            if (closed) return Result.failure(BackboneClosedException())
            val wanted = model.first()
            val loading = lock.withLock {
                current?.takeIf { it.model == wanted } ?: run {
                    current?.let(::release)
                    Loading(wanted, load(wanted)).also { current = it }
                }
            }
            try {
                return loading.extractor.await()
            } catch (e: CancellationException) {
                // The load was dropped for a newer model (or closed): unless we were cancelled, ask again.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private fun load(model: EmbeddingModel): Deferred<Result<FeatureExtractor>> = scope.async(start = CoroutineStart.LAZY) {
        runCatching {
            checkNotNull(loader) { "On-device backbone isn't available on this platform yet." }
            val modelBytes = readModel(model)
            // Cancelling can't stop native setup midway, and a cancelled load would drop the finished
            // backbone without freeing it, so let it finish and close it here if we were dropped meanwhile.
            val loaded = withContext(NonCancellable) { loader.load(modelBytes) }
            if (closed || !isActive) {
                loaded.close()
                throw CancellationException("Backbone dropped while loading")
            }
            FeatureExtractor(loaded, model)
        }
    }

    /** Frees [loading]'s backbone, or stops it loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun release(loading: Loading) {
        val extractor = loading.extractor
        if (extractor.isCompleted && !extractor.isCancelled) extractor.getCompleted().onSuccess { it.close() }
        extractor.cancel()
    }

    override fun close() {
        closed = true
        // Without the lock: close() isn't suspending. A racing await() sees [closed] on its next pass.
        current?.let(::release)
    }
}

/** The backbone was freed, because another model was chosen or the app is closing. */
class BackboneClosedException : IllegalStateException("Backbone is closed")
