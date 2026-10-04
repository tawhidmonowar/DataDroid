package org.onedroid.dataset.data.archive

/**
 * Decompresses [length] bytes of raw DEFLATE data (no zlib or gzip header) starting at [offset] in
 * [data], as stored in zip entries, which must expand to exactly [uncompressedSize] bytes. Throws
 * [IllegalArgumentException] for corrupt data.
 */
internal expect fun inflateRaw(data: ByteArray, offset: Int, length: Int, uncompressedSize: Int): ByteArray
