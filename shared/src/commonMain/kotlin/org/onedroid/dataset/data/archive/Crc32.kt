package org.onedroid.dataset.data.archive

/** CRC-32 as used by zip (IEEE 802.3 polynomial). */
object Crc32 {
    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(data: ByteArray): Int {
        var crc = -1
        for (b in data) crc = (crc ushr 8) xor TABLE[(crc xor b.toInt()) and 0xFF]
        return crc.inv()
    }
}
