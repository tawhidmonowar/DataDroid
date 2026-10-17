package org.onedroid.dataset.core.platform.ml

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.memcpy
import tflite.TfLiteInterpreterAllocateTensors
import tflite.TfLiteInterpreterCreate
import tflite.TfLiteInterpreterDelete
import tflite.TfLiteInterpreterGetInputTensor
import tflite.TfLiteInterpreterGetOutputTensor
import tflite.TfLiteInterpreterInvoke
import tflite.TfLiteInterpreterOptionsCreate
import tflite.TfLiteInterpreterOptionsDelete
import tflite.TfLiteInterpreterOptionsSetNumThreads
import tflite.TfLiteModelCreate
import tflite.TfLiteModelDelete
import tflite.TfLiteTensorByteSize
import tflite.TfLiteTensorCopyFromBuffer
import tflite.TfLiteTensorCopyToBuffer
import tflite.kTfLiteOk

@OptIn(ExperimentalForeignApi::class)
class TfLiteBackbone(modelBytes: ByteArray, numThreads: Int = 4) : Backbone {
    // TfLiteModelCreate doesn't copy the buffer, so it must stay alive as long as the model.
    private val modelBuffer: CPointer<ByteVar> = nativeHeap.allocArray<ByteVar>(modelBytes.size).also { buffer ->
        modelBytes.usePinned { memcpy(buffer, it.addressOf(0), modelBytes.size.toULong()) }
    }
    private val model = checkNotNull(TfLiteModelCreate(modelBuffer, modelBytes.size.toULong())) { "Invalid TFLite model" }
    private val options = checkNotNull(TfLiteInterpreterOptionsCreate()).also {
        TfLiteInterpreterOptionsSetNumThreads(it, numThreads)
    }
    private val interpreter = checkNotNull(TfLiteInterpreterCreate(model, options)) { "Could not create interpreter" }

    init {
        check(TfLiteInterpreterAllocateTensors(interpreter) == kTfLiteOk) { "Could not allocate tensors" }
    }

    private val inputTensor = checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0))
    private val outputTensor = checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0))
    private val inputBytes = TfLiteTensorByteSize(inputTensor)
    private val outputBytes = TfLiteTensorByteSize(outputTensor)

    override fun embed(input: FloatArray): FloatArray {
        require(input.size.toULong() * Float.SIZE_BYTES.toULong() == inputBytes) {
            "input has ${input.size} floats, model expects ${inputBytes / Float.SIZE_BYTES.toULong()}"
        }
        input.usePinned {
            check(TfLiteTensorCopyFromBuffer(inputTensor, it.addressOf(0), inputBytes) == kTfLiteOk) { "Input copy failed" }
        }
        check(TfLiteInterpreterInvoke(interpreter) == kTfLiteOk) { "Inference failed" }
        val output = FloatArray((outputBytes / Float.SIZE_BYTES.toULong()).toInt())
        output.usePinned {
            check(TfLiteTensorCopyToBuffer(outputTensor, it.addressOf(0), outputBytes) == kTfLiteOk) { "Output copy failed" }
        }
        return output
    }

    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        TfLiteInterpreterDelete(interpreter)
        TfLiteInterpreterOptionsDelete(options)
        TfLiteModelDelete(model)
        nativeHeap.free(modelBuffer.rawValue)
    }
}

val IosBackboneLoader = BackboneLoader { bytes ->
    withContext(Dispatchers.Default) { TfLiteBackbone(bytes) }
}
