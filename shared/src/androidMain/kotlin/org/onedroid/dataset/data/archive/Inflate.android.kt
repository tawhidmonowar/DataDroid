package org.onedroid.dataset.data.archive

import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal actual fun inflateRaw(data: ByteArray, offset: Int, length: Int, uncompressedSize: Int): ByteArray {
    // One spare byte, so data that expands past uncompressedSize is caught rather than cut off.
    val output = ByteArray(uncompressedSize + 1)
    val inflater = Inflater(true)
    try {
        inflater.setInput(data, offset, length)
        var written = 0
        var paddedInput = false
        while (!inflater.finished()) {
            val count = try {
                inflater.inflate(output, written, output.size - written)
            } catch (e: DataFormatException) {
                throw IllegalArgumentException("Corrupt compressed data", e)
            }
            written += count
            if (count == 0) {
                // With nowrap, zlib may need one extra byte past the data to see the end of the stream.
                require(inflater.needsInput() && !paddedInput) { "Compressed data is truncated or larger than expected" }
                inflater.setInput(ByteArray(1))
                paddedInput = true
            }
        }
        require(written == uncompressedSize) { "Compressed data doesn't match its recorded size" }
        return output.copyOf(uncompressedSize)
    } finally {
        inflater.end()
    }
}
