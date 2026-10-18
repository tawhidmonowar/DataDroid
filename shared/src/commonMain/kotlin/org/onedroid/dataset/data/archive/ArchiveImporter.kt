package org.onedroid.dataset.data.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.onedroid.dataset.data.storage.DatasetMetadata
import org.onedroid.dataset.domain.model.ArchiveContents
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.domain.usecase.ImageFolders

/** Reads a zip of class folders, such as one [DatasetExporter] wrote or a desktop tool zipped. */
internal object ArchiveImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lists [archive]'s usable images with their folders; each is decoded from the zip only when read. The
     * name comes from `metadata.json`, else the folder wrapping everything. Throws [IllegalArgumentException]
     * if it isn't a readable zip, or [ZipTooLargeException].
     */
    fun read(archive: ByteArray): ArchiveContents {
        val entries = ZipReader.entries(archive, maxTotalSize = MAX_TOTAL_SIZE)
            .map { it.name.replace('\\', '/').trim('/') to it }
            .filter { (path, _) -> path.isNotEmpty() }
        val images = entries.mapNotNull { (path, entry) ->
            val parts = path.split('/').filter { it.isNotEmpty() }
            if (entry.size > MAX_IMAGE_SIZE) return@mapNotNull null
            LabeledImage(parts.dropLast(1), parts.last(), ImageSource { withContext(Dispatchers.Default) { entry.read() } })
        }

        val metadataName = entries
            .filter { (path, _) -> path == ExportNaming.METADATA_FILE || path.count { it == '/' } == 1 && path.endsWith("/${ExportNaming.METADATA_FILE}") }
            .firstNotNullOfOrNull { (_, entry) ->
                runCatching { json.decodeFromString(DatasetMetadata.serializer(), entry.read().decodeToString()).name }.getOrNull()
            }
        val usable = images.filter { ImageFolders.isImageFile(it.name) && it.folders.none(ImageFolders::isIgnoredFolder) }
        // As in ImageFolders.analyze: a stray image beside the class folders doesn't stop a folder being a wrapper.
        val wrapperName = usable.mapNotNull { it.folders.firstOrNull() }.distinct().singleOrNull()
            ?.takeIf { usable.any { it.folders.size >= 2 } }

        return ArchiveContents(
            name = metadataName?.takeIf { it.isNotBlank() } ?: wrapperName?.replace('_', ' '),
            images = usable,
        )
    }

    /** Entries are decoded one at a time, so this only guards against archives claiming absurd sizes. */
    private const val MAX_TOTAL_SIZE = 1L shl 32
    const val MAX_IMAGE_SIZE = 64 shl 20
}
