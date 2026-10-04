package org.onedroid.dataset.platform.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class CameraFacing { Front, Back }

/**
 * Live camera viewfinder. While [onCapture] is non-null it receives JPEG frames, center-cropped to a
 * [CAPTURE_SIZE] square to match the square viewfinder, at most every [CAPTURE_INTERVAL_MS], on a
 * background thread. Handles its own permission and "no camera" states.
 */
@Composable
expect fun CameraPreview(facing: CameraFacing, onCapture: ((ByteArray) -> Unit)?, modifier: Modifier = Modifier)

/** Five frames a second: fast enough to record variety, slow enough to avoid near-duplicates. */
internal const val CAPTURE_INTERVAL_MS = 200L

internal const val CAPTURE_SIZE = 640

internal const val CAPTURE_JPEG_QUALITY = 90

@Composable
internal fun CameraMessage(message: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            action?.let { (label, onClick) -> FilledTonalButton(onClick = onClick) { Text(label) } }
        }
    }
}
