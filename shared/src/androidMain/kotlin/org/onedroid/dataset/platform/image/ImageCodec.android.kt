package org.onedroid.dataset.platform.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

actual fun decodeSquare(bytes: ByteArray, size: Int): RgbImage {
    val decoded = decodeSampled(bytes) { width, height -> min(width, height) / size }
    val exif = readExif(bytes)
    return decoded.toSquareRgbImage(size, exif?.rotationDegrees ?: 0, exif?.isFlipped == true)
}

actual fun decodeFitted(bytes: ByteArray, maxSide: Int): RgbImage {
    val decoded = decodeSampled(bytes) { width, height -> max(width, height) / maxSide }
    val exif = readExif(bytes)
    val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate((exif?.rotationDegrees ?: 0).toFloat())
        if (exif?.isFlipped == true) postScale(-1f, 1f)
    }
    val fitted = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    return fitted.toRgbImage()
}

/** Decodes at the largest power-of-two subsampling that keeps [ratio] of the full size at least 1. */
private inline fun decodeSampled(bytes: ByteArray, ratio: (width: Int, height: Int) -> Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image format" }
    val limit = ratio(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (sampleSize * 2 <= limit) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Unsupported image format" }
}

private fun readExif(bytes: ByteArray): ExifInterface? = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()

/** Center-crops, rotates by [rotationDegrees], optionally mirrors, and scales to [targetSize]. */
internal fun Bitmap.toSquareRgbImage(targetSize: Int, rotationDegrees: Int, mirror: Boolean): RgbImage {
    val side = min(width, height)
    val scale = targetSize.toFloat() / side
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotationDegrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val cropped = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side, matrix, true)
    val square = if (cropped.width == targetSize && cropped.height == targetSize) {
        cropped
    } else {
        Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
    }
    return square.toRgbImage()
}

private fun Bitmap.toRgbImage(): RgbImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) pixels[i] = pixels[i] or (0xFF shl 24)
    return RgbImage(width, height, pixels)
}

actual fun RgbImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun RgbImage.encodeJpeg(quality: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

