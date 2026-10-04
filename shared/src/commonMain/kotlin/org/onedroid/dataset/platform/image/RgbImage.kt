package org.onedroid.dataset.platform.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.min

/** Decoded pixels, row-major ARGB with opaque alpha. */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixel count ${pixels.size} doesn't match ${width}x$height" }
    }
}

/**
 * Decodes [bytes] (any format the platform supports), applying EXIF orientation, center-cropped to a
 * square and scaled to [size]. Throws [IllegalArgumentException] if the bytes aren't a supported image.
 */
expect fun decodeSquare(bytes: ByteArray, size: Int): RgbImage

/** Like [decodeSquare] but keeps the aspect ratio, scaling down so neither side exceeds [maxSide]. */
expect fun decodeFitted(bytes: ByteArray, maxSide: Int): RgbImage

expect fun RgbImage.toImageBitmap(): ImageBitmap

expect fun RgbImage.encodeJpeg(quality: Int = 90): ByteArray

/**
 * Center-crops a [width]x[height] frame to a square and box-filters it down to [targetSize].
 * [pixelAt] returns the source pixel as ARGB. [mirror] flips horizontally (front cameras).
 */
fun cropScaleToSquare(
    width: Int,
    height: Int,
    targetSize: Int,
    mirror: Boolean = false,
    pixelAt: (x: Int, y: Int) -> Int,
): RgbImage {
    val side = min(width, height)
    require(side >= targetSize) { "frame ${width}x$height is smaller than $targetSize" }
    val left = (width - side) / 2
    val top = (height - side) / 2
    val pixels = IntArray(targetSize * targetSize)

    for (oy in 0 until targetSize) {
        val y0 = top + oy * side / targetSize
        val y1 = top + (oy + 1) * side / targetSize
        for (ox in 0 until targetSize) {
            val x0 = left + ox * side / targetSize
            val x1 = left + (ox + 1) * side / targetSize
            var r = 0
            var g = 0
            var b = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val argb = pixelAt(x, y)
                r += (argb shr 16) and 0xFF
                g += (argb shr 8) and 0xFF
                b += argb and 0xFF
            }
            val count = (y1 - y0) * (x1 - x0)
            val outX = if (mirror) targetSize - 1 - ox else ox
            pixels[oy * targetSize + outX] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
    return RgbImage(targetSize, targetSize, pixels)
}
