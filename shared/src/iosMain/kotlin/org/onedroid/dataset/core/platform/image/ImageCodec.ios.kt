package org.onedroid.dataset.core.platform.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class)
actual fun decodeSquare(bytes: ByteArray, size: Int): RgbImage {
    val image = uiImage(bytes)
    // UIImage.size and drawInRect already account for EXIF orientation.
    val (width, height) = image.size.useContents { width to height }
    val scale = size / min(width, height)
    val drawWidth = width * scale
    val drawHeight = height * scale
    return render(image, size, size, (size - drawWidth) / 2, (size - drawHeight) / 2, drawWidth, drawHeight)
}

@OptIn(ExperimentalForeignApi::class)
actual fun decodeFitted(bytes: ByteArray, maxSide: Int): RgbImage {
    val image = uiImage(bytes)
    val (width, height) = image.size.useContents { width to height }
    val scale = min(1.0, maxSide / max(width, height))
    val outWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val outHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return render(image, outWidth, outHeight, 0.0, 0.0, outWidth.toDouble(), outHeight.toDouble())
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun uiImage(bytes: ByteArray): UIImage {
    require(bytes.isNotEmpty()) { "Unsupported image format" }
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    return requireNotNull(UIImage.imageWithData(data)) { "Unsupported image format" }
}

/** Draws [image] into an [outWidth]x[outHeight] RGB canvas at the given rect, on white. */
@OptIn(ExperimentalForeignApi::class)
private fun render(
    image: UIImage,
    outWidth: Int,
    outHeight: Int,
    x: Double,
    y: Double,
    drawWidth: Double,
    drawHeight: Double,
): RgbImage {
    val rgba = ByteArray(outWidth * outHeight * 4)
    rgba.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0), outWidth.toULong(), outHeight.toULong(), 8u,
            (outWidth * 4).toULong(), colorSpace, CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
        CGColorSpaceRelease(colorSpace)
        requireNotNull(context) { "Could not create bitmap context" }

        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, outWidth.toDouble(), outHeight.toDouble()))
        // UIKit drawing expects a top-left origin.
        CGContextTranslateCTM(context, 0.0, outHeight.toDouble())
        CGContextScaleCTM(context, 1.0, -1.0)
        UIGraphicsPushContext(context)
        image.drawInRect(CGRectMake(x, y, drawWidth, drawHeight))
        UIGraphicsPopContext()
        CGContextRelease(context)
    }

    val pixels = IntArray(outWidth * outHeight) { i ->
        val r = rgba[i * 4].toInt() and 0xFF
        val g = rgba[i * 4 + 1].toInt() and 0xFF
        val b = rgba[i * 4 + 2].toInt() and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return RgbImage(outWidth, outHeight, pixels)
}

actual fun RgbImage.toImageBitmap(): ImageBitmap {
    val rgba = toRgbaBytes()
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(info, rgba, width * 4)
    return bitmap.asComposeImageBitmap()
}

actual fun RgbImage.encodeJpeg(quality: Int): ByteArray =
    requireNotNull(withUIImage { UIImageJPEGRepresentation(it, quality / 100.0) }) { "JPEG encoding failed" }.toByteArray()

actual fun RgbImage.encodePng(): ByteArray =
    requireNotNull(withUIImage { UIImagePNGRepresentation(it) }) { "PNG encoding failed" }.toByteArray()

actual fun RgbImage.encodeWebp(quality: Int): ByteArray = throw UnsupportedOperationException("iOS can't encode WebP")

actual val canEncodeWebp: Boolean = false

@OptIn(ExperimentalForeignApi::class)
actual fun imageDimensions(bytes: ByteArray): Pair<Int, Int> =
    uiImage(bytes).size.useContents { width.roundToInt() to height.roundToInt() }

/** Runs [block] with the pixels wrapped in a UIImage, which only lives for the call. */
@OptIn(ExperimentalForeignApi::class)
private fun <T> RgbImage.withUIImage(block: (UIImage) -> T): T {
    val rgba = toRgbaBytes()
    return rgba.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0), width.toULong(), height.toULong(), 8u,
            (width * 4).toULong(), colorSpace, CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
        )
        CGColorSpaceRelease(colorSpace)
        val cgImage = CGBitmapContextCreateImage(requireNotNull(context) { "Could not create bitmap context" })
        CGContextRelease(context)
        try {
            block(UIImage.imageWithCGImage(requireNotNull(cgImage)))
        } finally {
            CGImageRelease(cgImage)
        }
    }
}

private fun RgbImage.toRgbaBytes(): ByteArray {
    val rgba = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val argb = pixels[i]
        rgba[i * 4] = (argb shr 16).toByte()
        rgba[i * 4 + 1] = (argb shr 8).toByte()
        rgba[i * 4 + 2] = argb.toByte()
        rgba[i * 4 + 3] = 0xFF.toByte()
    }
    return rgba
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}
