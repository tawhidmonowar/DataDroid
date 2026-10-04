package org.onedroid.dataset.data

import org.onedroid.dataset.data.image.ImageFormat
import org.onedroid.dataset.testutil.fakeJpeg
import org.onedroid.dataset.testutil.fakePng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageFormatTest {

    @Test
    fun detectsCommonFormats() {
        assertEquals("jpg", ImageFormat.extensionOf(fakeJpeg("x")))
        assertEquals("png", ImageFormat.extensionOf(fakePng("x")))
        assertEquals("gif", ImageFormat.extensionOf("GIF89a....".encodeToByteArray()))
        assertEquals("webp", ImageFormat.extensionOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
        assertEquals("bmp", ImageFormat.extensionOf("BM......".encodeToByteArray()))
        assertEquals("heic", ImageFormat.extensionOf("\u0000\u0000\u0000\u0018ftypheic".encodeToByteArray()))
    }

    @Test
    fun rejectsUnknownAndShortInput() {
        assertNull(ImageFormat.extensionOf("hello world".encodeToByteArray()))
        assertNull(ImageFormat.extensionOf(ByteArray(0)))
        assertNull(ImageFormat.extensionOf(byteArrayOf(0xFF.toByte())))
    }
}
