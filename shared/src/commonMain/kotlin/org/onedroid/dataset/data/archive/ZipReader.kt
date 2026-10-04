package org.onedroid.dataset.data.archive

/**
 * Minimal ZIP reader: reads the central directory, and supports stored and DEFLATE-compressed entries,
 * which covers [ZipWriter] and archives made by desktop zip tools. No ZIP64 or encryption. Throws
 * [IllegalArgumentException] for anything it can't read, or [ZipTooLargeException] past the size limit.
 */
object ZipReader {

    /** A file in the archive, decoded only when [read] is called. */
    class Entry internal constructor(
        val name: String,
        /** Uncompressed size in bytes, as recorded in the archive; [read] fails if the data doesn't match. */
        val size: Int,
        private val decode: () -> ByteArray,
    ) {
        fun read(): ByteArray = decode()
    }

    /**
     * The archive's files in central directory order, without decoding them; directory entries are
     * skipped. Fails up front if the recorded sizes add up to more than [maxTotalSize] bytes, so a small
     * crafted archive can't exhaust memory.
     */
    fun entries(zip: ByteArray, maxTotalSize: Long = DEFAULT_MAX_TOTAL_SIZE): List<Entry> {
        val end = findEndOfCentralDirectory(zip)
        val count = zip.shortLe(end + 10)
        var at = zip.intLe(end + 16)
        val entries = ArrayList<Entry>(count)
        var totalSize = 0L
        repeat(count) {
            require(at >= 0 && at + 46 <= zip.size && zip.intLe(at) == CENTRAL_HEADER) { "Corrupt zip central directory" }
            val flags = zip.shortLe(at + 8)
            val method = zip.shortLe(at + 10)
            val crc = zip.intLe(at + 16)
            val compressedSize = zip.intLe(at + 20)
            val size = zip.intLe(at + 24)
            val nameLength = zip.shortLe(at + 28)
            val extraLength = zip.shortLe(at + 30)
            val commentLength = zip.shortLe(at + 32)
            val localOffset = zip.intLe(at + 42)
            require(at + 46 + nameLength <= zip.size) { "Corrupt zip central directory" }
            val name = zip.decodeToString(at + 46, at + 46 + nameLength)
            at += 46 + nameLength + extraLength + commentLength

            if (name.endsWith("/")) return@repeat
            require(flags and FLAG_ENCRYPTED == 0) { "Encrypted zip entries aren't supported ($name)" }
            require(method == STORED || method == DEFLATED) { "Unsupported zip compression method $method ($name)" }
            // Negative sizes mean over 2 GB, i.e. ZIP64, which isn't supported.
            require(compressedSize >= 0 && size >= 0 && localOffset >= 0) { "Unsupported zip entry ($name)" }
            require(method == DEFLATED || compressedSize == size) { "Corrupt zip entry ($name)" }
            totalSize += size
            if (totalSize > maxTotalSize) throw ZipTooLargeException()
            require(localOffset.toLong() + 30 <= zip.size && zip.intLe(localOffset) == LOCAL_HEADER) { "Corrupt zip entry ($name)" }
            val start = localOffset + 30 + zip.shortLe(localOffset + 26) + zip.shortLe(localOffset + 28)
            require(start.toLong() + compressedSize <= zip.size) { "Truncated zip entry ($name)" }

            entries += Entry(name, size) {
                val data = if (method == DEFLATED) {
                    inflateRaw(zip, start, compressedSize, size)
                } else {
                    zip.copyOfRange(start, start + size)
                }
                require(Crc32.of(data) == crc) { "Checksum mismatch in zip entry ($name)" }
                data
            }
        }
        return entries
    }

    /** Every file in the archive, decoded; see [entries]. */
    fun read(zip: ByteArray, maxTotalSize: Long = DEFAULT_MAX_TOTAL_SIZE): Map<String, ByteArray> =
        entries(zip, maxTotalSize).associate { it.name to it.read() }

    private fun findEndOfCentralDirectory(zip: ByteArray): Int {
        // The end record is 22 bytes plus a comment of up to 64 KB.
        val lowest = maxOf(0, zip.size - 22 - 0xFFFF)
        for (at in zip.size - 22 downTo lowest) {
            if (zip.intLe(at) == END_OF_CENTRAL_DIRECTORY) return at
        }
        throw IllegalArgumentException("Not a zip file")
    }

    private fun ByteArray.intLe(at: Int) =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or ((this[at + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.shortLe(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    /** Below a typical Android heap, so a picked archive can be held in memory while it is read. */
    const val DEFAULT_MAX_TOTAL_SIZE = 256L shl 20

    private const val STORED = 0
    private const val DEFLATED = 8
    private const val FLAG_ENCRYPTED = 1
    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
}
