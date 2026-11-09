package org.onedroid.dataset.data.archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.open
import platform.posix.pread

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFileRange(path: String, position: Long, length: Int): ByteArray {
    val bytes = ByteArray(length)
    // Pinning an empty array gives no address, and there is nothing to read anyway.
    if (length == 0) return bytes
    val fd = open(path, O_RDONLY)
    check(fd >= 0) { "Could not open $path" }
    try {
        bytes.usePinned { pinned ->
            var done = 0
            while (done < length) {
                val count = pread(fd, pinned.addressOf(done), (length - done).convert(), (position + done).convert())
                check(count > 0) { "Could not read $path" }
                done += count.toInt()
            }
        }
    } finally {
        close(fd)
    }
    return bytes
}
