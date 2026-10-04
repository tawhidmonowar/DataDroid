package org.onedroid.dataset.domain.model

/** A dataset as listed in the library. [coverImageId] is the image shown on its card, if it has any. */
data class DatasetSummary(
    val id: String,
    val name: String,
    val classCount: Int,
    val imageCount: Int,
    val updatedAt: Long,
    val coverImageId: Long?,
)

/** A labelled image dataset: each class (label) holds the ids of its images, oldest first. */
data class Dataset(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val classes: List<DatasetClass>,
) {
    val imageCount: Int get() = classes.sumOf { it.imageIds.size }
}

data class DatasetClass(
    val id: Int,
    val name: String,
    val imageIds: List<Long>,
)

/** Raw bytes of an image the user picked or captured, read only when needed. */
fun interface ImageSource {
    suspend fun read(): ByteArray
}

/** An image found in a picked folder or zip; [folders] is its directory path below the picked root. */
class LabeledImage(val folders: List<String>, val name: String, val source: ImageSource)

/** Outcome of adding a batch of images: images that couldn't be decoded are [skipped]. */
data class ImportResult(val added: Int, val skipped: Int)

/** A file written for the user to share or save. */
data class ExportedFile(val path: String, val mimeType: String)
