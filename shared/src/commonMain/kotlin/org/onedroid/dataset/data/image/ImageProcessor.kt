package org.onedroid.dataset.data.image

import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.OutputFormat
import org.onedroid.dataset.domain.model.ResizeMode
import org.onedroid.dataset.core.platform.image.RgbImage
import org.onedroid.dataset.core.platform.image.canEncodeWebp
import org.onedroid.dataset.core.platform.image.decodeFitted
import org.onedroid.dataset.core.platform.image.decodeSquare
import org.onedroid.dataset.core.platform.image.encodeJpeg
import org.onedroid.dataset.core.platform.image.encodePng
import org.onedroid.dataset.core.platform.image.encodeWebp
import org.onedroid.dataset.core.platform.image.imageDimensions
import kotlin.math.min

/** An image written in some format; [extension] names the format. */
class EncodedImage(val bytes: ByteArray, val extension: String)

/** Image work the data layer needs, behind an interface so storage can be tested without codecs. */
interface ImageProcessor {
    /** Formats [convert] can write here; always includes [OutputFormat.ORIGINAL]. */
    val supportedFormats: Set<OutputFormat>

    /** A [size]-pixel square JPEG preview; throws [IllegalArgumentException] if [bytes] can't be decoded. */
    fun thumbnail(bytes: ByteArray, size: Int): ByteArray

    /**
     * [bytes] resized and encoded as [settings] say, never scaled up. Throws [IllegalArgumentException] if
     * the bytes can't be decoded. Call only for settings that aren't passthrough.
     */
    fun convert(bytes: ByteArray, settings: ImageSettings): EncodedImage
}

class PlatformImageProcessor : ImageProcessor {
    override val supportedFormats: Set<OutputFormat> =
        OutputFormat.entries.filter { it != OutputFormat.WEBP || canEncodeWebp }.toSet()

    override fun thumbnail(bytes: ByteArray, size: Int): ByteArray = decodeSquare(bytes, size).encodeJpeg(THUMBNAIL_QUALITY)

    override fun convert(bytes: ByteArray, settings: ImageSettings): EncodedImage {
        val image = when (settings.resize) {
            ResizeMode.ORIGINAL -> decodeFitted(bytes, Int.MAX_VALUE)
            ResizeMode.FIT -> decodeFitted(bytes, settings.size)
            ResizeMode.SQUARE -> {
                val (width, height) = imageDimensions(bytes)
                decodeSquare(bytes, min(settings.size, min(width, height)))
            }
        }
        val format = settings.format.takeIf { it != OutputFormat.ORIGINAL } ?: sourceFormat(bytes)
        return EncodedImage(image.encode(format, settings.quality), requireNotNull(format.extension))
    }

    /** The format an image is re-encoded in when it should keep its own: its own if writable, else JPEG. */
    private fun sourceFormat(bytes: ByteArray): OutputFormat {
        val own = OutputFormat.entries.firstOrNull { it.extension != null && it.extension == ImageFormat.extensionOf(bytes) }
        return own?.takeIf { it in supportedFormats } ?: OutputFormat.JPEG
    }

    private fun RgbImage.encode(format: OutputFormat, quality: Int): ByteArray = when (format) {
        OutputFormat.PNG -> encodePng()
        OutputFormat.WEBP -> encodeWebp(quality)
        OutputFormat.JPEG, OutputFormat.ORIGINAL -> encodeJpeg(quality)
    }

    private companion object {
        const val THUMBNAIL_QUALITY = 85
    }
}
