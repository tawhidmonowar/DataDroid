package org.onedroid.dataset.data.image

/** Identifies image formats by their leading bytes, so a file's extension can't mislabel it. */
object ImageFormat {
    /** The file extension for [bytes]' format, or null if it isn't a known image format. */
    fun extensionOf(bytes: ByteArray): String? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
        bytes.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) -> "png"
        bytes.startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "gif"
        bytes.startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) && bytes.ascii(8, 4) == "WEBP" -> "webp"
        bytes.startsWith('B'.code, 'M'.code) -> "bmp"
        bytes.ascii(4, 4) == "ftyp" && bytes.ascii(8, 4) in HEIF_BRANDS -> "heic"
        else -> null
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private fun ByteArray.ascii(offset: Int, length: Int): String? =
        if (size < offset + length) null else decodeToString(offset, offset + length)
}
