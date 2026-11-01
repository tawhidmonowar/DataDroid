package org.onedroid.dataset.data.archive

import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.onedroid.dataset.data.storage.DatasetMetadata
import org.onedroid.dataset.data.storage.DatasetRecord
import org.onedroid.dataset.data.storage.ImageRecord
import org.onedroid.dataset.data.image.ImageFormat
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.domain.usecase.ExportNaming.METADATA_FILE

/**
 * Writes a dataset as a zip of its main folder:
 * ```
 * Animal_Dataset/metadata.json
 * Animal_Dataset/cat/cat_0001.jpg
 * Animal_Dataset/dog/dog_0001.png
 * unknown/unknown_0001.jpg
 * ```
 * Empty classes are left out, since training tools reject class folders without images. Unknown images go
 * beside the dataset folder rather than in it, since training tools take every folder in it for a class.
 */
internal class DatasetExporter(private val fileSystem: FileSystem) {
    private val json = Json { prettyPrint = true }

    /**
     * Streams [record]'s images, read with [readImage], and [unknownImages] into [target]. Images whose file
     * has gone missing, or unknown images that can't be read or aren't images, are skipped. The zip is written
     * next to [target] and moved into place once complete.
     */
    suspend fun export(
        record: DatasetRecord,
        target: Path,
        readImage: (ImageRecord) -> ByteArray?,
        unknownImages: List<ImageSource>,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val root = ExportNaming.rootFolder(record.name)
        val classes = record.classes.filter { it.images.isNotEmpty() }
        val folders = ExportNaming.classFolders(classes.map { it.name })
        val unknownFolder = ExportNaming.unknownFolder(root)
        val total = classes.sumOf { it.images.size } + unknownImages.size

        val temp = Path(requireNotNull(target.parent), "${target.name}.tmp")
        try {
            var exported = 0
            val exportedPerClass = IntArray(classes.size)
            fileSystem.sink(temp).buffered().use { sink ->
                val zip = ZipWriter(sink)
                classes.forEachIndexed { index, imageClass ->
                    val folder = folders[index]
                    val digits = maxOf(4, imageClass.images.size.toString().length)
                    imageClass.images.forEach { image ->
                        val bytes = readImage(image)
                        if (bytes != null) {
                            exportedPerClass[index]++
                            val number = exportedPerClass[index].toString().padStart(digits, '0')
                            zip.add("$root/$folder/${folder}_$number.${image.extension}", bytes)
                            exported++
                        }
                        onProgress(exported, total)
                    }
                }
                val labelled = exported
                val digits = maxOf(4, unknownImages.size.toString().length)
                var unknownCount = 0
                unknownImages.forEach { source ->
                    val bytes = try {
                        source.read()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    val extension = bytes?.let { ImageFormat.extensionOf(it) }
                    if (bytes != null && extension != null) {
                        unknownCount++
                        val number = unknownCount.toString().padStart(digits, '0')
                        zip.add("$unknownFolder/${ExportNaming.UNKNOWN_FOLDER}_$number.$extension", bytes)
                    }
                    exported++
                    onProgress(exported, total)
                }
                val metadata = DatasetMetadata(
                    name = record.name,
                    classes = folders.filterIndexed { index, _ -> exportedPerClass[index] > 0 },
                    imageCount = labelled,
                    unknownFolder = unknownFolder.takeIf { unknownCount > 0 },
                    unknownImageCount = unknownCount,
                )
                zip.add("$root/$METADATA_FILE", json.encodeToString(DatasetMetadata.serializer(), metadata).encodeToByteArray())
                zip.finish()
            }
            fileSystem.atomicMove(temp, target)
        } catch (e: Throwable) {
            fileSystem.delete(temp, mustExist = false)
            throw e
        }
    }

    companion object {
        const val MIME_TYPE = "application/zip"
    }
}
