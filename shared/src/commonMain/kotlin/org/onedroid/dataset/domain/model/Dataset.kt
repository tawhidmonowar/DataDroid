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

/**
 * A labelled image dataset: each class (label) holds the ids of its images, oldest first. New images are
 * stored with [imageSettings]; [outdatedImageCount] images were stored with earlier settings.
 */
data class Dataset(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val classes: List<DatasetClass>,
    val imageSettings: ImageSettings = ImageSettings(),
    val outdatedImageCount: Int = 0,
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

/** A zip's images, unread, and the dataset name it suggests (from its `metadata.json` or wrapper folder). */
class ArchiveContents(val name: String?, val images: List<LabeledImage>)

/**
 * Outcome of adding a batch of images: images that couldn't be decoded are [skipped]. [imageIds] are the
 * added images' ids in the order they were given, when the operation reports them.
 */
data class ImportResult(val added: Int, val skipped: Int, val imageIds: List<Long> = emptyList())

/** A file written for the user to share or save. */
data class ExportedFile(val path: String, val mimeType: String)
