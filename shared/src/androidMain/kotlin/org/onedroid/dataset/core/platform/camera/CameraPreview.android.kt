package org.onedroid.dataset.core.platform.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.core.platform.image.encodeJpeg
import org.onedroid.dataset.core.platform.image.toSquareRgbImage
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.camera_allow
import org.onedroid.dataset.resources.camera_permission_needed
import org.onedroid.dataset.resources.camera_unavailable
import java.util.concurrent.Executors

@Composable
actual fun CameraPreview(facing: CameraFacing, onCapture: ((ByteArray) -> Unit)?, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        CameraMessage(
            stringResource(Res.string.camera_permission_needed),
            modifier,
            action = stringResource(Res.string.camera_allow) to { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCapture by rememberUpdatedState(onCapture)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(facing, lifecycleOwner) {
        unavailable = false
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
        val executor = Executors.newSingleThreadExecutor()
        var lastCaptureAt = 0L
        analysis.setAnalyzer(executor) { proxy ->
            proxy.use {
                val capture = currentOnCapture ?: return@use
                val now = SystemClock.elapsedRealtime()
                if (now - lastCaptureAt < CAPTURE_INTERVAL_MS) return@use
                lastCaptureAt = now
                val frame = it.toBitmap().toSquareRgbImage(
                    targetSize = CAPTURE_SIZE,
                    rotationDegrees = it.imageInfo.rotationDegrees,
                    // Match the mirrored viewfinder so images look like what the user saw.
                    mirror = facing == CameraFacing.Front,
                )
                capture(frame.encodeJpeg(CAPTURE_JPEG_QUALITY))
            }
        }
        val selector = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        try {
            if (!provider.hasCamera(selector)) {
                unavailable = true
                return@LaunchedEffect
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            awaitCancellation()
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
            surfaceRequest = null
        }
    }

    if (unavailable) {
        CameraMessage(stringResource(Res.string.camera_unavailable), modifier)
        return
    }
    val request = surfaceRequest
    if (request == null) {
        Box(modifier.background(Color.Black))
    } else {
        CameraXViewfinder(surfaceRequest = request, modifier = modifier)
    }
}
