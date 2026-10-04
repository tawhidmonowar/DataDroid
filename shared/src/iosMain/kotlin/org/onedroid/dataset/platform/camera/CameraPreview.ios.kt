package org.onedroid.dataset.platform.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.platform.image.cropScaleToSquare
import org.onedroid.dataset.platform.image.encodeJpeg
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.camera_denied
import org.onedroid.dataset.resources.camera_unavailable
import org.onedroid.dataset.resources.camera_waiting
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSDate
import platform.Foundation.NSString
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

private enum class CameraAccess { Unknown, Granted, Denied }

@Composable
actual fun CameraPreview(facing: CameraFacing, onCapture: ((ByteArray) -> Unit)?, modifier: Modifier) {
    var access by remember { mutableStateOf(currentCameraAccess()) }
    LaunchedEffect(Unit) {
        if (access == CameraAccess.Unknown) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    access = if (granted) CameraAccess.Granted else CameraAccess.Denied
                }
            }
        }
    }
    when (access) {
        CameraAccess.Unknown -> return CameraMessage(stringResource(Res.string.camera_waiting), modifier)
        CameraAccess.Denied -> return CameraMessage(stringResource(Res.string.camera_denied), modifier)
        CameraAccess.Granted -> Unit
    }

    val device = remember(facing) {
        AVCaptureDevice.defaultDeviceWithDeviceType(
            AVCaptureDeviceTypeBuiltInWideAngleCamera,
            AVMediaTypeVideo,
            if (facing == CameraFacing.Front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack,
        )
    }
    if (device == null) {
        CameraMessage(stringResource(Res.string.camera_unavailable), modifier)
        return
    }

    val currentOnCapture by rememberUpdatedState(onCapture)
    val controller = remember(device) {
        CameraController(device, mirror = facing == CameraFacing.Front) { currentOnCapture }
    }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    UIKitView(factory = { CameraPreviewView(controller.session) }, modifier = modifier)
}

private fun currentCameraAccess(): CameraAccess = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
    AVAuthorizationStatusAuthorized -> CameraAccess.Granted
    AVAuthorizationStatusNotDetermined -> CameraAccess.Unknown
    else -> CameraAccess.Denied
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class CameraController(
    device: AVCaptureDevice,
    private val mirror: Boolean,
    /** The current capture callback, or null while not capturing; read on the camera queue. */
    private val capture: () -> ((ByteArray) -> Unit)?,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    val session = AVCaptureSession()
    private val queue = dispatch_queue_create("org.onedroid.dataset.camera", null)
    private var lastFrameAt = 0.0

    init {
        session.sessionPreset = if (session.canSetSessionPreset(AVCaptureSessionPreset1280x720)) {
            AVCaptureSessionPreset1280x720
        } else {
            AVCaptureSessionPreset640x480
        }
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
        if (input != null && session.canAddInput(input)) session.addInput(input)

        val output = AVCaptureVideoDataOutput()
        output.videoSettings = mapOf<Any?, Any?>(
            interpretObjCPointer<NSString>(kCVPixelBufferPixelFormatTypeKey!!.rawValue) to kCVPixelFormatType_32BGRA,
        )
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(this, queue)
        if (session.canAddOutput(output)) session.addOutput(output)
        // Deliver portrait-oriented buffers so frames match what the preview shows.
        output.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
            if (connection.isVideoRotationAngleSupported(90.0)) connection.videoRotationAngle = 90.0
        }
    }

    // startRunning blocks, so it must not run on the main thread.
    fun start() = dispatch_async(queue) { session.startRunning() }

    fun stop() = dispatch_async(queue) { session.stopRunning() }

    @ObjCSignatureOverride
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val onCapture = capture() ?: return
        val now = NSDate().timeIntervalSince1970 * 1000
        if (now - lastFrameAt < CAPTURE_INTERVAL_MS) return
        lastFrameAt = now

        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        try {
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val rowBytes = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            val base = CVPixelBufferGetBaseAddress(pixelBuffer)?.reinterpret<UByteVar>() ?: return
            val frame = cropScaleToSquare(width, height, minOf(CAPTURE_SIZE, width, height), mirror) { x, y ->
                val offset = y * rowBytes + x * 4 // BGRA
                (0xFF shl 24) or (base[offset + 2].toInt() shl 16) or (base[offset + 1].toInt() shl 8) or base[offset].toInt()
            }
            onCapture(frame.encodeJpeg(CAPTURE_JPEG_QUALITY))
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}
