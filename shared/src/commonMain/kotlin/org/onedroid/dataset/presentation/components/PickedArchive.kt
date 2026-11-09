package org.onedroid.dataset.presentation.components

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The local copy of a picked archive. Its images are read from it until the import ends (including while
 * an import preview waits for the user), and then it is deleted, as it can be gigabytes.
 */
internal class PickedArchive {
    private var path: String? = null

    /** Takes over [path], deleting any copy held before. */
    fun hold(path: String) {
        release()
        this.path = path
    }

    fun release() {
        val held = path ?: return
        path = null
        runCatching { SystemFileSystem.delete(Path(held), mustExist = false) }
    }
}
