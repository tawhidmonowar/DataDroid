package org.onedroid.dataset.platform.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiteRtBackbone(modelBytes: ByteArray, numThreads: Int = 4) : Backbone {
    private val interpreter = Interpreter(
        ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).apply {
            put(modelBytes)
            rewind()
        },
        Interpreter.Options().setNumThreads(numThreads),
    )
    private val inputBuffer = ByteBuffer.allocateDirect(interpreter.getInputTensor(0).numBytes())
        .order(ByteOrder.nativeOrder())
    private val embeddingSize = interpreter.getOutputTensor(0).shape().last()

    override fun embed(input: FloatArray): FloatArray {
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(input)
        val output = arrayOf(FloatArray(embeddingSize))
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    override fun close() = interpreter.close()
}

val AndroidBackboneLoader = BackboneLoader { bytes ->
    withContext(Dispatchers.Default) { LiteRtBackbone(bytes) }
}
