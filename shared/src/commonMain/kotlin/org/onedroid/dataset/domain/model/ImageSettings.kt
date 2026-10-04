package org.onedroid.dataset.domain.model

import kotlinx.serialization.Serializable

/**
 * How images are stored when they're added to a dataset. The defaults keep every image exactly as it was
 * captured or imported.
 */
@Serializable
data class ImageSettings(
    val resize: ResizeMode = ResizeMode.ORIGINAL,
    /** Longest side for [ResizeMode.FIT], side length for [ResizeMode.SQUARE]; unused for [ResizeMode.ORIGINAL]. */
    val size: Int = DEFAULT_SIZE,
    val format: OutputFormat = OutputFormat.ORIGINAL,
    /** Encoder quality, 1 to 100; only used for lossy formats. */
    val quality: Int = DEFAULT_QUALITY,
) {
    /** True when images are stored untouched, so there's nothing to convert. */
    val isPassthrough: Boolean get() = resize == ResizeMode.ORIGINAL && format == OutputFormat.ORIGINAL

    /** Whether [quality] affects the output: the format is lossy, or images keep a format that may be. */
    val usesQuality: Boolean
        get() = format == OutputFormat.JPEG || format == OutputFormat.WEBP || (format == OutputFormat.ORIGINAL && resize != ResizeMode.ORIGINAL)

    /** These settings with values a stored file might have broken brought back into range. */
    fun sanitized(): ImageSettings = copy(size = size.coerceIn(MIN_SIZE, MAX_SIZE), quality = quality.coerceIn(MIN_QUALITY, 100))

    companion object {
        const val DEFAULT_SIZE = 224
        const val DEFAULT_QUALITY = 90
        const val MIN_SIZE = 32
        const val MAX_SIZE = 4096
        const val MIN_QUALITY = 10

        /** Common input sizes for image models. */
        val SIZE_PRESETS = listOf(128, 224, 256, 384, 512, 640, 1024)
    }
}

enum class ResizeMode {
    /** Keep the image's own dimensions. */
    ORIGINAL,

    /** Scale down, keeping the aspect ratio, so the longest side is at most the size. */
    FIT,

    /** Center-crop to a square, then scale down to the size. */
    SQUARE,
}

enum class OutputFormat(val extension: String?) {
    /** Keep each image's own format (re-encoded in that format only when it's resized). */
    ORIGINAL(null),
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp"),
}
