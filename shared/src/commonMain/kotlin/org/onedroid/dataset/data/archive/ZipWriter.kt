package org.onedroid.dataset.data.archive

import kotlinx.io.Sink
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe

/**
 * Minimal streaming ZIP writer: each entry is written to [sink] as soon as it's added, so only one file is
 * in memory at a time. Entries are stored uncompressed (images are already compressed). No ZIP64, so an
 * archive must stay under 4 GB and 65,535 entries; [ZipTooLargeException] is thrown past either limit.
 * Call [finish] to write the central directory; the sink is not closed.
 */
class ZipWriter(private val sink: Sink) {
    private class Entry(val name: ByteArray, val crc: Int, val size: Int, val offset: Long)

    private val entries = mutableListOf<Entry>()
    private var offset = 0L
    private var finished = false

    fun add(name: String, data: ByteArray): ZipWriter {
        check(!finished) { "Zip already finished" }
        if (entries.size >= MAX_ENTRIES) throw ZipTooLargeException()
        val nameBytes = name.encodeToByteArray()
        val headerSize = 30L + nameBytes.size
        if (offset + headerSize + data.size > MAX_OFFSET) throw ZipTooLargeException()
        val entry = Entry(nameBytes, Crc32.of(data), data.size, offset)
        sink.writeIntLe(LOCAL_HEADER)
        sink.writeShortLe(20) // version needed
        sink.writeShortLe(UTF8_NAMES)
        sink.writeShortLe(0) // stored
        sink.writeShortLe(0) // mod time
        sink.writeShortLe(DOS_DATE_1980)
        sink.writeIntLe(entry.crc)
        sink.writeIntLe(entry.size)
        sink.writeIntLe(entry.size)
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0) // extra
        sink.write(nameBytes)
        sink.write(data)
        offset += headerSize + data.size
        entries += entry
        return this
    }

    fun finish() {
        check(!finished) { "Zip already finished" }
        finished = true
        val centralOffset = offset
        var centralSize = 0L
        for (e in entries) {
            sink.writeIntLe(CENTRAL_HEADER)
            sink.writeShortLe(20) // version made by
            sink.writeShortLe(20) // version needed
            sink.writeShortLe(UTF8_NAMES)
            sink.writeShortLe(0) // stored
            sink.writeShortLe(0)
            sink.writeShortLe(DOS_DATE_1980)
            sink.writeIntLe(e.crc)
            sink.writeIntLe(e.size)
            sink.writeIntLe(e.size)
            sink.writeShortLe(e.name.size)
            sink.writeShortLe(0) // extra
            sink.writeShortLe(0) // comment
            sink.writeShortLe(0) // disk
            sink.writeShortLe(0) // internal attributes
            sink.writeIntLe(0) // external attributes
            sink.writeIntLe(e.offset.toInt())
            sink.write(e.name)
            centralSize += 46 + e.name.size
        }
        if (centralOffset + centralSize > MAX_OFFSET) throw ZipTooLargeException()
        sink.writeIntLe(END_OF_CENTRAL_DIRECTORY)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(entries.size)
        sink.writeShortLe(entries.size)
        sink.writeIntLe(centralSize.toInt())
        sink.writeIntLe(centralOffset.toInt())
        sink.writeShortLe(0)
    }

    private fun Sink.writeShortLe(value: Int) = writeShortLe(value.toShort())

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
        const val UTF8_NAMES = 0x0800
        const val DOS_DATE_1980 = 0x21
        const val MAX_ENTRIES = 0xFFFF
        const val MAX_OFFSET = 0xFFFFFFFFL
    }
}

class ZipTooLargeException : IllegalStateException("Zip archive would exceed 4 GB or 65,535 files")
