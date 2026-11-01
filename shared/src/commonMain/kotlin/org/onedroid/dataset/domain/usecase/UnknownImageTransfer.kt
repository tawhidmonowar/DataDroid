package org.onedroid.dataset.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.getOrNull
import org.onedroid.dataset.domain.model.ExportedFile
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.domain.repository.ProgressListener
import org.onedroid.dataset.domain.repository.UnknownImageRepository

/** Exports a dataset with the images auto-sort couldn't place, in an `unknown` folder beside it. */
class ExportDataset(
    private val repository: DatasetRepository,
    private val unknownImages: UnknownImageRepository,
) {
    suspend operator fun invoke(datasetId: String, onProgress: ProgressListener = { _, _ -> }): Result<ExportedFile, DataError> {
        val unknowns = unknownImages.observe(datasetId).first().map { image ->
            ImageSource { unknownImages.read(datasetId, image.id).getOrNull() ?: error("Unknown image ${image.id} is gone") }
        }
        return repository.exportDataset(datasetId, unknowns, onProgress)
    }
}

/** Adds images to a dataset's unknowns, as when an export with an `unknown` folder is imported. */
class KeepUnknownImages(
    private val sorter: ImageSorter,
    private val unknownImages: UnknownImageRepository,
) {
    /** Returns how many were kept; images that can't be read or decoded are skipped. */
    suspend operator fun invoke(datasetId: String, images: List<ImageSource>): Result<Int, DataError.Local> {
        var kept = 0
        for (source in images) {
            val bytes = try {
                source.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            val preview = sorter.preview(bytes) ?: continue
            when (val added = unknownImages.add(datasetId, bytes, preview)) {
                is Result.Error -> return added
                is Result.Success -> kept++
            }
        }
        return Result.Success(kept)
    }
}
