package org.onedroid.dataset.data.archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.MAX_WBITS
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual fun inflateRaw(data: ByteArray, offset: Int, length: Int, uncompressedSize: Int): ByteArray {
    // One spare byte, so data that expands past uncompressedSize is caught rather than cut off. It also
    // keeps the buffer non-empty, since pinning an empty array gives no address.
    val output = ByteArray(uncompressedSize + 1)
    // Pinning gives no address past the end, so empty input points at a dummy byte instead.
    val input = if (length == 0) ByteArray(1) else data
    val inputOffset = if (length == 0) 0 else offset
    memScoped {
        val stream = alloc<z_stream>()
        // Null allocators select zlib's defaults; set explicitly since the struct isn't guaranteed zeroed.
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        stream.next_in = null
        stream.avail_in = 0u
        // Negative window bits select raw DEFLATE, without a zlib header.
        check(inflateInit2_(stream.ptr, -MAX_WBITS, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK) { "zlib init failed" }
        try {
            input.usePinned { pinnedIn ->
                output.usePinned { pinnedOut ->
                    stream.next_in = pinnedIn.addressOf(inputOffset).reinterpret<UByteVar>()
                    stream.avail_in = length.toUInt()
                    stream.next_out = pinnedOut.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = output.size.toUInt()
                    val result = inflate(stream.ptr, Z_FINISH)
                    require(result == Z_STREAM_END) { "Corrupt compressed data (zlib $result)" }
                    require(stream.total_out.toLong() == uncompressedSize.toLong()) { "Compressed data doesn't match its recorded size" }
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return output.copyOf(uncompressedSize)
}
