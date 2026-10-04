package org.onedroid.dataset.data.storage

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

/**
 * Blocking file access for datasets under [root]. Layout per dataset: `<id>/dataset.json`,
 * `<id>/images/<imageId>.<ext>` and `<id>/thumbs/<imageId>.jpg`. Not thread-safe; callers serialise writes.
 */
internal class DatasetFiles(private val root: Path, private val fileSystem: FileSystem) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Ids of every dataset folder, including ones whose record can't be read. */
    fun datasetIds(): List<String> {
        if (!fileSystem.exists(root)) return emptyList()
        return fileSystem.list(root)
            .filter { !it.name.startsWith(".") && fileSystem.metadataOrNull(it)?.isDirectory == true }
            .map { it.name }
    }

    fun exists(id: String): Boolean = fileSystem.exists(Path(root, id))

    fun readRecord(id: String): DatasetRecord? {
        val file = recordFile(id)
        if (!fileSystem.exists(file)) return null
        return json.decodeFromString(DatasetRecord.serializer(), read(file).decodeToString())
    }

    fun writeRecord(id: String, record: DatasetRecord) =
        writeAtomically(recordFile(id), json.encodeToString(DatasetRecord.serializer(), record).encodeToByteArray())

    fun imageFile(id: String, image: ImageRecord) = Path(root, id, IMAGES_DIR, "${image.id}.${image.extension}")

    fun thumbnailFile(id: String, imageId: Long) = Path(root, id, THUMBS_DIR, "$imageId.jpg")

    fun writeImage(id: String, image: ImageRecord, bytes: ByteArray, thumbnail: ByteArray) {
        writeAtomically(imageFile(id, image), bytes)
        writeAtomically(thumbnailFile(id, image.id), thumbnail)
    }

    fun deleteImage(id: String, image: ImageRecord) {
        fileSystem.delete(imageFile(id, image), mustExist = false)
        fileSystem.delete(thumbnailFile(id, image.id), mustExist = false)
    }

    fun read(path: Path): ByteArray = fileSystem.source(path).buffered().use { it.readByteArray() }

    fun readOrNull(path: Path): ByteArray? = if (fileSystem.exists(path)) read(path) else null

    /** Removes image and thumbnail files [record] doesn't reference, left behind by an interrupted write. */
    fun deleteUnreferencedFiles(id: String, record: DatasetRecord) {
        val referenced = record.classes.flatMap { it.images }.map { it.id }.toSet()
        for (dir in listOf(Path(root, id, IMAGES_DIR), Path(root, id, THUMBS_DIR))) {
            if (!fileSystem.exists(dir)) continue
            for (file in fileSystem.list(dir)) {
                val imageId = file.name.substringBefore('.').toLongOrNull()
                if (imageId == null || imageId !in referenced) fileSystem.delete(file, mustExist = false)
            }
        }
    }

    fun deleteDataset(id: String) = deleteRecursively(Path(root, id))

    fun deleteRecursively(path: Path) {
        if (!fileSystem.exists(path)) return
        if (fileSystem.metadataOrNull(path)?.isDirectory == true) fileSystem.list(path).forEach(::deleteRecursively)
        fileSystem.delete(path)
    }

    private fun recordFile(id: String) = Path(root, id, RECORD_FILE)

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val parent = requireNotNull(target.parent)
        fileSystem.createDirectories(parent)
        val temp = Path(parent, "${target.name}.tmp")
        fileSystem.sink(temp).buffered().use { it.write(bytes) }
        fileSystem.atomicMove(temp, target)
    }

    companion object {
        const val RECORD_FILE = "dataset.json"
        const val IMAGES_DIR = "images"
        const val THUMBS_DIR = "thumbs"
    }
}
