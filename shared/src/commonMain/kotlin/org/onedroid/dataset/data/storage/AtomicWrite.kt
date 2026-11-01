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

/** Whether this failure, or one that caused it, is the disk being full. */
internal fun Throwable.isDiskFull(): Boolean {
    val text = generateSequence(this) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    return "ENOSPC" in text || "No space left" in text
}
