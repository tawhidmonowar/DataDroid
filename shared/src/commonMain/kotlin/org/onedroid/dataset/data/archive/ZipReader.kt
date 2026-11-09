package org.onedroid.dataset.data.archive

/**
 * Minimal ZIP reader: reads the central directory, and supports stored and DEFLATE-compressed entries,
 * which covers [ZipWriter] and archives made by desktop zip tools, up to 4 GB. No ZIP64 or encryption. Throws
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
     * skipped. Only the central directory and local headers are read up front, so the archive can live on
     * disk. Fails up front if the recorded sizes add up to more than [maxTotalSize] bytes, so a small
     * crafted archive can't claim an absurd amount of data.
     */
    fun entries(source: ZipSource, maxTotalSize: Long = DEFAULT_MAX_TOTAL_SIZE): List<Entry> {
        val tailLength = minOf(source.size, 22L + 0xFFFF).toInt()
        if (tailLength < 22) throw IllegalArgumentException("Not a zip file")
        val tailStart = source.size - tailLength
        val tail = source.read(tailStart, tailLength)
        val end = findEndOfCentralDirectory(tail)
        val count = tail.shortLe(end + 10)
        val directorySize = tail.uintLe(end + 12)
        val directoryOffset = tail.uintLe(end + 16)
        require(directorySize <= MAX_DIRECTORY_SIZE && directoryOffset + directorySize <= tailStart + end) { "Corrupt zip central directory" }
        val directory = source.read(directoryOffset, directorySize.toInt())

        var at = 0
        val entries = ArrayList<Entry>(count)
        var totalSize = 0L
        repeat(count) {
            require(at + 46 <= directory.size && directory.intLe(at) == CENTRAL_HEADER) { "Corrupt zip central directory" }
            val flags = directory.shortLe(at + 8)
            val method = directory.shortLe(at + 10)
            val crc = directory.intLe(at + 16)
            val compressedSize = directory.intLe(at + 20)
            val size = directory.intLe(at + 24)
            val nameLength = directory.shortLe(at + 28)
            val extraLength = directory.shortLe(at + 30)
            val commentLength = directory.shortLe(at + 32)
            val localOffset = directory.uintLe(at + 42)
            require(at + 46 + nameLength <= directory.size) { "Corrupt zip central directory" }
            val name = directory.decodeToString(at + 46, at + 46 + nameLength)
            at += 46 + nameLength + extraLength + commentLength

            if (name.endsWith("/")) return@repeat
            require(flags and FLAG_ENCRYPTED == 0) { "Encrypted zip entries aren't supported ($name)" }
            require(method == STORED || method == DEFLATED) { "Unsupported zip compression method $method ($name)" }
            // Negative sizes mean an entry over 2 GB, i.e. ZIP64, which isn't supported.
            require(compressedSize >= 0 && size >= 0) { "Unsupported zip entry ($name)" }
            require(method == DEFLATED || compressedSize == size) { "Corrupt zip entry ($name)" }
            // DEFLATE never grows data by more than a few bytes per 64 KB block, and the compressed bytes are
            // read whole, so an entry can't claim a tiny size but make us read gigabytes.
            require(compressedSize <= size + size / 1000L + 1024) { "Corrupt zip entry ($name)" }
            totalSize += size
            if (totalSize > maxTotalSize) throw ZipTooLargeException()
            require(localOffset + 30 <= source.size) { "Corrupt zip entry ($name)" }
            val header = source.read(localOffset, 30)
            require(header.intLe(0) == LOCAL_HEADER) { "Corrupt zip entry ($name)" }
            val start = localOffset + 30 + header.shortLe(26) + header.shortLe(28)
            require(start + compressedSize <= source.size) { "Truncated zip entry ($name)" }

            entries += Entry(name, size) {
                val raw = source.read(start, compressedSize)
                val data = if (method == DEFLATED) inflateRaw(raw, 0, compressedSize, size) else raw
                require(Crc32.of(data) == crc) { "Checksum mismatch in zip entry ($name)" }
                data
            }
        }
        return entries
    }

    /** The files in a zip held in memory; see the [ZipSource] overload. */
    fun entries(zip: ByteArray, maxTotalSize: Long = DEFAULT_MAX_TOTAL_SIZE): List<Entry> =
        entries(ByteArrayZipSource(zip), maxTotalSize)

    /** Every file in the archive, decoded; see [entries]. */
    fun read(zip: ByteArray, maxTotalSize: Long = DEFAULT_MAX_TOTAL_SIZE): Map<String, ByteArray> =
        entries(zip, maxTotalSize).associate { it.name to it.read() }

    private fun findEndOfCentralDirectory(tail: ByteArray): Int {
        // The end record is 22 bytes plus a comment of up to 64 KB.
        for (at in tail.size - 22 downTo 0) {
            if (tail.intLe(at) == END_OF_CENTRAL_DIRECTORY) return at
        }
        throw IllegalArgumentException("Not a zip file")
    }

    /** Offsets and sizes in the archive are unsigned 32-bit values. */
    private fun ByteArray.uintLe(at: Int): Long = intLe(at).toLong() and 0xFFFFFFFFL

    private fun ByteArray.intLe(at: Int) =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or ((this[at + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.shortLe(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    /** For archives held in memory; [ArchiveImporter] reads from disk and sets its own limit. */
    const val DEFAULT_MAX_TOTAL_SIZE = 256L shl 20

    /** About 1 KB per entry at the 65,535-entry limit; real archives use far less. */
    private const val MAX_DIRECTORY_SIZE = 64L shl 20

    private const val STORED = 0
    private const val DEFLATED = 8
    private const val FLAG_ENCRYPTED = 1
    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
}
