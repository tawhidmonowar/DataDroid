package org.onedroid.dataset.data.archive

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.onedroid.dataset.data.storage.DatasetMetadata
import org.onedroid.dataset.data.storage.DatasetRecord
import org.onedroid.dataset.data.storage.ImageRecord
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.domain.usecase.ExportNaming.METADATA_FILE

/**
 * Writes a dataset as a zip of its main folder:
 * ```
 * Animal_Dataset/metadata.json
 * Animal_Dataset/cat/cat_0001.jpg
 * Animal_Dataset/dog/dog_0001.png
 * ```
 * Empty classes are left out, since training tools reject class folders without images.
 */
internal class DatasetExporter(private val fileSystem: FileSystem) {
    private val json = Json { prettyPrint = true }

    /**
     * Streams [record]'s images, read with [readImage], into [target]. Images whose file has gone missing
     * are skipped. The zip is written next to [target] and moved into place once complete.
     */
    fun export(
        record: DatasetRecord,
        target: Path,
        readImage: (ImageRecord) -> ByteArray?,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val root = ExportNaming.rootFolder(record.name)
        val classes = record.classes.filter { it.images.isNotEmpty() }
        val folders = ExportNaming.classFolders(classes.map { it.name })
        val total = classes.sumOf { it.images.size }

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
                val metadata = DatasetMetadata(
                    name = record.name,
                    classes = folders.filterIndexed { index, _ -> exportedPerClass[index] > 0 },
                    imageCount = exported,
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
