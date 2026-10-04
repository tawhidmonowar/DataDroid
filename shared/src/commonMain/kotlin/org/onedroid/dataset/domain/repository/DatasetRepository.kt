package org.onedroid.dataset.domain.repository

import kotlinx.coroutines.flow.Flow
import org.onedroid.dataset.core.domain.DataError
import org.onedroid.dataset.core.domain.EmptyResult
import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.DatasetSummary
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.ImportResult

/** Reports progress of a long operation: [done] of [total] items handled. */
typealias ProgressListener = (done: Int, total: Int) -> Unit

interface DatasetRepository {

    /** Every dataset, most recently changed first; emits again after any change. */
    fun observeDatasets(): Flow<List<DatasetSummary>>

    /** The dataset with [id], or null once it doesn't exist; emits again after any change to it. */
    fun observeDataset(id: String): Flow<Dataset?>

    /** Creates an empty dataset with two starter classes and returns its id. */
    suspend fun createDataset(name: String): Result<String, DataError.Local>

    suspend fun renameDataset(id: String, name: String): EmptyResult<DataError.Local>

    suspend fun deleteDataset(id: String): EmptyResult<DataError.Local>

    /** Adds a class and returns its id. */
    suspend fun addClass(datasetId: String, name: String): Result<Int, DataError.Local>

    suspend fun renameClass(datasetId: String, classId: Int, name: String): EmptyResult<DataError.Local>

    /** Deletes the class and all its images. */
    suspend fun deleteClass(datasetId: String, classId: Int): EmptyResult<DataError.Local>

    /** Adds [sources] to the class; images that can't be decoded are skipped, not failed. */
    suspend fun addImages(
        datasetId: String,
        classId: Int,
        sources: List<ImageSource>,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ImportResult, DataError.Local>

    /** Adds each group's images to the class with that name, creating classes that don't exist yet. */
    suspend fun importLabeledImages(
        datasetId: String,
        groups: Map<String, List<ImageSource>>,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ImportResult, DataError.Local>

    suspend fun deleteImages(datasetId: String, imageIds: Set<Long>): EmptyResult<DataError.Local>

    suspend fun moveImages(datasetId: String, imageIds: Set<Long>, toClassId: Int): EmptyResult<DataError.Local>

    /** The image's original bytes. */
    suspend fun readImage(datasetId: String, imageId: Long): Result<ByteArray, DataError.Local>

    /** A small square JPEG preview of the image. */
    suspend fun readThumbnail(datasetId: String, imageId: Long): Result<ByteArray, DataError.Local>

    /**
     * Writes the dataset as a zip: one folder named after the dataset holding `metadata.json` and a folder
     * of images per non-empty class.
     */
    suspend fun exportDataset(datasetId: String, onProgress: ProgressListener = { _, _ -> }): Result<ExportedFile, DataError>

    /** Creates a dataset from a zip of class folders (such as an export) and returns its id. */
    suspend fun importArchive(
        archive: ByteArray,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<String, DataError>

    /** Adds the class folders in a zip to an existing dataset, merging classes with the same name. */
    suspend fun importArchiveInto(
        datasetId: String,
        archive: ByteArray,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ImportResult, DataError>
}
