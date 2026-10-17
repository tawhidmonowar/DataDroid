package org.onedroid.dataset.core.platform.image

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageCodecIosTest {
    /** A JPEG whose left half is red and right half blue. */
    private fun splitJpeg(width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height) { i -> if (i % width < width / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        return RgbImage(width, height, pixels).encodeJpeg(95)
    }

    private fun RgbImage.channel(x: Int, y: Int, shift: Int) = (pixels[y * width + x] shr shift) and 0xFF

    @Test
    fun decodeSquareCropsTheCenterAtTheRequestedSize() {
        val image = decodeSquare(splitJpeg(1600, 800), 224)
        assertEquals(224, image.width)
        assertEquals(224, image.height)
        // Center crop of a half-red, half-blue image: red on the left edge, blue on the right.
        assertTrue(image.channel(10, 112, 16) > 200 && image.channel(10, 112, 0) < 60)
        assertTrue(image.channel(213, 112, 0) > 200 && image.channel(213, 112, 16) < 60)
    }

    @Test
    fun decodeFittedKeepsTheAspectRatio() {
        val image = decodeFitted(splitJpeg(1600, 800), 400)
        assertEquals(400, image.width)
        assertTrue(abs(image.height - 200) <= 1)
    }

    @Test
    fun decodeFittedNeverScalesUp() {
        val image = decodeFitted(splitJpeg(300, 200), Int.MAX_VALUE)
        assertEquals(300, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun rejectsBytesThatArentAnImage() {
        assertFailsWith<IllegalArgumentException> { decodeSquare("not an image".encodeToByteArray(), 64) }
    }
}
