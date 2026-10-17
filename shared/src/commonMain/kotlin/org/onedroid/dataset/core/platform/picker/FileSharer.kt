package org.onedroid.dataset.core.platform.picker

import androidx.compose.runtime.Composable

/** Hands a file in the app's export directory to the system share sheet. */
fun interface FileSharer {
    fun share(path: String, mimeType: String)
}

@Composable
expect fun rememberFileSharer(): FileSharer
