package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.core.platform.camera.CameraFacing
import org.onedroid.dataset.core.platform.camera.CameraPreview
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_close
import org.onedroid.dataset.resources.camera_hold_to_record
import org.onedroid.dataset.resources.camera_recording
import org.onedroid.dataset.resources.camera_switch

@Composable
fun CameraPanel(
    facing: CameraFacing,
    onCapture: ((ByteArray) -> Unit)?,
    onFlip: () -> Unit,
    onClose: () -> Unit,
    controls: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Square, because captured frames are center-cropped to a square.
        CameraPreview(
            facing = facing,
            onCapture = onCapture,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
        )
        controls()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onFlip) {
                Icon(AppIcons.SwitchCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.camera_switch), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onClose) {
                Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.action_close), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Captures while held down. */
@Composable
fun HoldToRecordButton(recording: Boolean, enabled: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnStop by rememberUpdatedState(onStop)
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        when {
            !enabled -> colors.surfaceVariant
            recording -> colors.error
            else -> colors.primary
        },
        label = "record",
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    currentOnStart()
                    tryAwaitRelease()
                    currentOnStop()
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (recording) Res.string.camera_recording else Res.string.camera_hold_to_record),
                color = if (recording) colors.onError else colors.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
