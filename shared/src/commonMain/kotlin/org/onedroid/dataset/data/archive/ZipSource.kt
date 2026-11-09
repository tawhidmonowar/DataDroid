package org.onedroid.dataset.data.archive

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Random-access bytes a zip is read from, so an archive never has to be held in memory as a whole. */
interface ZipSource {
    val size: Long

    /** Reads exactly [length] bytes from [position]; throws [IllegalArgumentException] past the end. */
    fun read(position: Long, length: Int): ByteArray
}

/** A zip already in memory, as written by [ZipWriter] into a buffer. */
class ByteArrayZipSource(private val bytes: ByteArray) : ZipSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        require(position >= 0 && length >= 0 && position + length <= bytes.size) { "Truncated zip" }
        val start = position.toInt()
        return bytes.copyOfRange(start, start + length)
    }
}

/**
 * The file at [path]. It is opened afresh for each read, so nothing needs closing and entries can be read
 * whenever an import gets to them; the size is read once, up front.
 */
class FileZipSource(private val path: String) : ZipSource {
    override val size: Long = SystemFileSystem.metadataOrNull(Path(path))?.size ?: 0L

    override fun read(position: Long, length: Int): ByteArray {
        require(position >= 0 && length >= 0 && position + length <= size) { "Truncated zip" }
        return readFileRange(path, position, length)
    }
}

/** Reads exactly [length] bytes of the file at [path], starting at [position]. */
internal expect fun readFileRange(path: String, position: Long, length: Int): ByteArray
