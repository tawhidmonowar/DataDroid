package org.onedroid.dataset.data.archive

import java.io.RandomAccessFile

internal actual fun readFileRange(path: String, position: Long, length: Int): ByteArray =
    RandomAccessFile(path, "r").use { file ->
        val bytes = ByteArray(length)
        file.seek(position)
        file.readFully(bytes)
        bytes
    }
