package org.onedroid.dataset.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_cancel

/** A long-running task: [fraction] null shows an indeterminate bar. */
@Composable
fun ProgressBanner(text: String, fraction: Float?, onCancel: (() -> Unit)?, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = if (onCancel != null) 4.dp else 12.dp)) {
            if (fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp))
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
            }
            Row(Modifier.padding(top = 4.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
    }
}
