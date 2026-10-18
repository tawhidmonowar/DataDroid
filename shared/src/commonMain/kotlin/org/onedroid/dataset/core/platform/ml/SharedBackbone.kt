package org.onedroid.dataset.core.platform.ml

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import org.onedroid.dataset.resources.Res

/** Loads the backbone once, on first use, and shares it between every open dataset. */
class SharedBackbone(
    private val loader: BackboneLoader?,
    scope: CoroutineScope,
    private val readModel: suspend () -> ByteArray = { Res.readBytes(MobileNetV3Small.MODEL_PATH) },
) : AutoCloseable {

    @Volatile
    private var closed = false

    private val extractor = scope.async(start = CoroutineStart.LAZY) {
        runCatching {
            checkNotNull(loader) { "On-device backbone isn't available on this platform yet." }
            val modelBytes = readModel()
            // Cancelling can't stop native setup midway, and a cancelled load would drop the finished
            // backbone without freeing it, so let it finish and close it here if we were closed meanwhile.
            val loaded = withContext(NonCancellable) { loader.load(modelBytes) }
            if (closed || !isActive) {
                loaded.close()
                throw CancellationException("Backbone closed while loading")
            }
            FeatureExtractor(loaded)
        }
    }

    suspend fun await(): Result<FeatureExtractor> = extractor.await()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        closed = true
        if (extractor.isCompleted && !extractor.isCancelled) extractor.getCompleted().onSuccess { it.close() }
        extractor.cancel()
    }
}
