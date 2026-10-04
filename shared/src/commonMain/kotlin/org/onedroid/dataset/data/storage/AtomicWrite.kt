package org.onedroid.dataset.data.storage

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

/** Writes [bytes] to a temporary file beside [target] and moves it into place, so readers never see half a file. */
internal fun FileSystem.writeAtomically(target: Path, bytes: ByteArray) {
    val parent = requireNotNull(target.parent)
    createDirectories(parent)
    val temp = Path(parent, "${target.name}.tmp")
    sink(temp).buffered().use { it.write(bytes) }
    atomicMove(temp, target)
}
