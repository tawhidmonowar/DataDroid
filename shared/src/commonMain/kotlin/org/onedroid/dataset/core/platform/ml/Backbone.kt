package org.onedroid.dataset.core.platform.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import org.onedroid.dataset.core.platform.image.RgbImage
import org.onedroid.dataset.domain.model.EmbeddingModel

/** Frozen pretrained feature extractor. Implementations are not required to be thread-safe. */
interface Backbone : AutoCloseable {
    fun embed(input: FloatArray): FloatArray
}

fun interface BackboneLoader {
    suspend fun load(modelBytes: ByteArray): Backbone
}

/**
 * A bundled image embedder: NHWC [inputSize] x [inputSize] RGB in [0, 1] -> [embeddingSize] floats. Any other
 * input normalisation is built into the model. [cacheFile] names the per-dataset file its embeddings are kept in.
 */
data class BackboneSpec(val modelPath: String, val inputSize: Int, val embeddingSize: Int, val cacheFile: String)

val EmbeddingModel.spec: BackboneSpec
    get() = when (this) {
        // Keeps the cache file name from before models could be chosen, so existing caches stay valid.
        EmbeddingModel.MOBILENET_V3_SMALL -> BackboneSpec("files/mobilenet_v3_small.tflite", 224, 1024, "embeddings.bin")
        EmbeddingModel.MOBILENET_V3_LARGE ->
            BackboneSpec("files/mobilenet_v3_large.tflite", 224, 1280, "embeddings-mobilenet_v3_large.bin")
        // Converted by docs/scripts/export_dinov2.py (int8 weights); outputs the layer-normed CLS token.
        EmbeddingModel.DINOV2_SMALL -> BackboneSpec("files/dinov2_small.tflite", 224, 384, "embeddings-dinov2_small.bin")
    }

/** Single source of truth for backbone input normalization on every platform. */
fun RgbImage.toInputTensor(): FloatArray {
    val tensor = FloatArray(width * height * 3)
    for (i in pixels.indices) {
        val argb = pixels[i]
        tensor[i * 3] = ((argb shr 16) and 0xFF) / 255f
        tensor[i * 3 + 1] = ((argb shr 8) and 0xFF) / 255f
        tensor[i * 3 + 2] = (argb and 0xFF) / 255f
    }
    return tensor
}

/** Embeds images with [model]'s [backbone]. */
class FeatureExtractor(private val backbone: Backbone, val model: EmbeddingModel) : AutoCloseable {
    private val inputSize = model.spec.inputSize

    private val mutex = Mutex()

    /** Guarded by [mutex]. */
    private var closed = false

    @Volatile
    private var closeRequested = false

    suspend fun embed(image: RgbImage): FloatArray = withContext(Dispatchers.Default) {
        require(image.width == inputSize && image.height == inputSize) {
            "expected ${inputSize}px image, got ${image.width}x${image.height}"
        }
        val input = image.toInputTensor()
        try {
            mutex.withLock {
                if (closed) throw BackboneClosedException()
                backbone.embed(input)
            }
        } finally {
            // A close() that found the lock taken left the native backbone for whoever held it.
            if (closeRequested) closeIfIdle()
        }
    }

    /**
     * Frees the backbone now if no [embed] is running, otherwise as soon as the running one finishes.
     * Cancelling a coroutine doesn't stop native inference already in progress, so closing
     * unconditionally could free the interpreter mid-run.
     */
    override fun close() {
        closeRequested = true
        closeIfIdle()
    }

    private fun closeIfIdle() {
        if (!mutex.tryLock()) return
        try {
            if (!closed) {
                closed = true
                backbone.close()
            }
        } finally {
            mutex.unlock()
        }
    }
}
