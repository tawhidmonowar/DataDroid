package org.onedroid.dataset.domain.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.getOrNull
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.model.UnknownImage
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.ProgressListener
import org.onedroid.dataset.domain.repository.UnknownImageRepository

/** Where unknown images go: a class the dataset has, or a new one. */
sealed interface UnknownDestination {
    data class ExistingClass(val classId: Int) : UnknownDestination

    /** A class created for them; [name] is expected to be valid and free. */
    data class NewClass(val name: String) : UnknownDestination
}

/**
 * Saves unknown images into a class and takes each off the unknowns once it's saved. If saving stops
 * partway (cancelled or failed), the ones already saved still leave the unknowns and the rest stay, and an
 * image that couldn't be saved stays too, so none is saved twice or lost.
 */
class FileUnknownImages(
    private val repository: DatasetRepository,
    private val unknownImages: UnknownImageRepository,
) {
    suspend operator fun invoke(
        datasetId: String,
        images: List<UnknownImage>,
        destination: UnknownDestination,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ImportResult, DataError> {
        val classId = when (destination) {
            is UnknownDestination.ExistingClass -> destination.classId
            is UnknownDestination.NewClass -> when (val created = repository.addClass(datasetId, destination.name.trim())) {
                is Result.Error -> return created
                is Result.Success -> created.data
            }
        }
        val sources = images.map { image ->
            ImageSource { unknownImages.read(datasetId, image.id).getOrNull() ?: error("Unknown image ${image.id} is gone") }
        }
        // Only images actually saved leave the unknowns: a skipped one (unreadable, or its class gone) stays.
        val saved = HashSet<Long>()
        try {
            return repository.addImages(datasetId, classId, sources, onProgress, onSaved = { saved += images[it].id })
        } finally {
            withContext(NonCancellable) { unknownImages.remove(datasetId, saved) }
        }
    }
}
