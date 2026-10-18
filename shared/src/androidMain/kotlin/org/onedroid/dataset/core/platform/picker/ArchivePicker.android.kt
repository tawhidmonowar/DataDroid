package org.onedroid.dataset.core.platform.picker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
actual fun rememberArchivePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        currentOnPicked(
            PickedFile { maxSize ->
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openInputStream(uri)) { "Could not open $uri" }.use { it.readAtMost(maxSize) }
                }
            }
        )
    }
    // Some file managers label zips as octet-stream, so accept that too; the import checks the contents.
    return remember(launcher) { { launcher.launch(arrayOf("application/zip", "application/octet-stream")) } }
}

/** Reads the stream, failing as soon as it passes [maxSize] bytes; the provider's reported size can't be trusted. */
private fun InputStream.readAtMost(maxSize: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        require(out.size() + count <= maxSize) { "File is too large" }
        out.write(buffer, 0, count)
    }
}
