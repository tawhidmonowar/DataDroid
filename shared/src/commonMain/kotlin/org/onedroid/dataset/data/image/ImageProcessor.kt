package org.onedroid.dataset.data.image

import org.onedroid.dataset.platform.image.decodeSquare
import org.onedroid.dataset.platform.image.encodeJpeg

/** Image work the data layer needs, behind an interface so storage can be tested without codecs. */
interface ImageProcessor {
    /** A [size]-pixel square JPEG preview; throws [IllegalArgumentException] if [bytes] can't be decoded. */
    fun thumbnail(bytes: ByteArray, size: Int): ByteArray
}

class PlatformImageProcessor : ImageProcessor {
    override fun thumbnail(bytes: ByteArray, size: Int): ByteArray = decodeSquare(bytes, size).encodeJpeg(THUMBNAIL_QUALITY)

    private companion object {
        const val THUMBNAIL_QUALITY = 85
    }
}
