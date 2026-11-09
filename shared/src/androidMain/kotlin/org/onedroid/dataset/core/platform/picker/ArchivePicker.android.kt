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
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Composable
actual fun rememberArchivePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        currentOnPicked(
            PickedFile { maxSize ->
                withContext(Dispatchers.IO) {
                    // Copied to disk rather than memory, so archives larger than the heap can be imported.
                    val directory = File(context.cacheDir, IMPORT_DIRECTORY).apply { mkdirs() }
                    deleteStaleCopies(directory)
                    val copy = File.createTempFile("archive", ".zip", directory)
                    try {
                        requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open $uri" }.use { input ->
                            copy.outputStream().use { input.copyAtMost(it, maxSize) }
                        }
                    } catch (e: Throwable) {
                        copy.delete()
                        throw e
                    }
                    copy.absolutePath
                }
            }
        )
    }
    // Some file managers label zips as octet-stream, so accept that too; the import checks the contents.
    return remember(launcher) { { launcher.launch(arrayOf("application/zip", "application/octet-stream")) } }
}

/** Copies the stream, failing as soon as it passes [maxSize] bytes; the provider's reported size can't be trusted. */
private fun InputStream.copyAtMost(out: OutputStream, maxSize: Long) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return
        total += count
        require(total <= maxSize) { "File is too large" }
        out.write(buffer, 0, count)
    }
}

/** Copies are read until their import ends, so only ones old enough to be finished with are removed. */
private fun deleteStaleCopies(directory: File) {
    val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
    directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
}

private const val IMPORT_DIRECTORY = "imports"
private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
