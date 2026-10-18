package org.onedroid.dataset.data.storage

import kotlinx.serialization.Serializable
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.model.DatasetSummary
import org.onedroid.dataset.domain.model.ImageSettings

/** `dataset.json`: everything about a dataset except the image files themselves. */
@Serializable
data class DatasetRecord(
    val version: Int = CURRENT_VERSION,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val nextClassId: Int,
    val nextImageId: Long,
    val classes: List<ClassRecord>,
    /** How images added now are stored. */
    val imageSettings: ImageSettings = ImageSettings(),
    /** Goes up each time [imageSettings] change; images stored with an older revision are outdated. */
    val settingsRevision: Int = 0,
) {
    val outdatedImages: List<ImageRecord> get() = classes.flatMap { c -> c.images.filter { it.revision != settingsRevision } }

    companion object {
        /** 2 added image settings; version 1 records read as storing images untouched. */
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class ClassRecord(val id: Int, val name: String, val images: List<ImageRecord> = emptyList())

/**
 * An image file, `images/<id>.<extension>`, with its thumbnail at `thumbs/<id>.jpg`. [revision] is the
 * dataset's settings revision the file was stored with.
 */
@Serializable
data class ImageRecord(val id: Long, val extension: String, val revision: Int = 0)

/** The `metadata.json` written at the top of an exported dataset zip. */
@Serializable
data class DatasetMetadata(val name: String, val classes: List<String>, val imageCount: Int)

internal fun DatasetRecord.toDataset(id: String) = Dataset(
    id = id,
    name = name,
    updatedAt = updatedAt,
    classes = classes.map { record -> DatasetClass(record.id, record.name, record.images.map { it.id }) },
    imageSettings = imageSettings,
    outdatedImageCount = outdatedImages.size,
)

internal fun DatasetRecord.toSummary(id: String) = DatasetSummary(
    id = id,
    name = name,
    classCount = classes.size,
    imageCount = classes.sumOf { it.images.size },
    updatedAt = updatedAt,
    coverImageId = classes.firstNotNullOfOrNull { it.images.lastOrNull()?.id },
)
