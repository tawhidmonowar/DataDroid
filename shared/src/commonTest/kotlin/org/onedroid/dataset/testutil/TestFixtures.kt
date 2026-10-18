package org.onedroid.dataset.testutil

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.onedroid.dataset.data.image.EncodedImage
import org.onedroid.dataset.data.image.ImageFormat
import org.onedroid.dataset.data.image.ImageProcessor
import org.onedroid.dataset.data.storage.StoragePaths
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.OutputFormat
import kotlin.random.Random

/** Bytes that pass format detection as a JPEG; [tag] makes each one distinct. */
fun fakeJpeg(tag: String): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + tag.encodeToByteArray()

fun fakePng(tag: String): ByteArray = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + tag.encodeToByteArray()

fun source(bytes: ByteArray) = ImageSource { bytes }

/**
 * "Decodes" anything except images whose bytes contain [CORRUPT_MARKER]. A converted image is its source
 * prefixed with a description of the settings, as `converted(<settings>):<source>`.
 */
class FakeImageProcessor : ImageProcessor {
    override val supportedFormats: Set<OutputFormat> = OutputFormat.entries.toSet()

    override fun thumbnail(bytes: ByteArray, size: Int): ByteArray {
        require(!bytes.decodeToString().contains(CORRUPT_MARKER)) { "Unsupported image format" }
        return "thumb:$size".encodeToByteArray()
    }

    override fun convert(bytes: ByteArray, settings: ImageSettings): EncodedImage {
        require(!bytes.decodeToString().contains(CORRUPT_MARKER)) { "Unsupported image format" }
        val extension = settings.format.extension ?: requireNotNull(ImageFormat.extensionOf(bytes))
        val tag = "converted(${settings.resize}:${settings.size}:$extension:${settings.quality}):"
        return EncodedImage(tag.encodeToByteArray() + bytes, extension)
    }

    companion object {
        const val CORRUPT_MARKER = "corrupt"
    }
}

/** A fresh, empty directory for one test. */
fun tempStorage(): StoragePaths {
    val root = Path(SystemTemporaryDirectory, "datadroid-test-" + Random.nextLong().toULong().toString(16))
    SystemFileSystem.createDirectories(root)
    return StoragePaths(datasetsRoot = Path(root, "datasets"), exportDirectory = Path(root, "exports"), settingsFile = Path(root, "settings.json"))
}
