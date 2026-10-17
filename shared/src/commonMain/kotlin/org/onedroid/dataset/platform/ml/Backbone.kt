package org.onedroid.dataset.platform.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import org.onedroid.dataset.platform.image.RgbImage

/** Frozen pretrained feature extractor. Implementations are not required to be thread-safe. */
interface Backbone : AutoCloseable {
    fun embed(input: FloatArray): FloatArray
}

fun interface BackboneLoader {
    suspend fun load(modelBytes: ByteArray): Backbone
}

/** MediaPipe MobileNetV3-Small image embedder: NHWC 224x224 RGB in [0, 1] -> 1024 floats. */
object MobileNetV3Small {
    const val MODEL_PATH = "files/mobilenet_v3_small.tflite"
    const val INPUT_SIZE = 224
    const val EMBEDDING_SIZE = 1024
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

class FeatureExtractor(private val backbone: Backbone) : AutoCloseable {
    private val mutex = Mutex()

    /** Guarded by [mutex]. */
    private var closed = false

    @Volatile
    private var closeRequested = false

    suspend fun embed(image: RgbImage): FloatArray = withContext(Dispatchers.Default) {
        require(image.width == MobileNetV3Small.INPUT_SIZE && image.height == MobileNetV3Small.INPUT_SIZE) {
            "expected ${MobileNetV3Small.INPUT_SIZE}px image, got ${image.width}x${image.height}"
        }
        val input = image.toInputTensor()
        try {
            mutex.withLock {
                check(!closed) { "Backbone is closed" }
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
