package org.onedroid.dataset.domain.repository

import kotlinx.coroutines.flow.Flow
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.UnknownImage

/**
 * Images auto-sort couldn't place in any class. They're kept with their dataset, through the camera closing and
 * the app restarting, until the user files them in a class or discards them.
 */
interface UnknownImageRepository {

    /** The dataset's unknown images, oldest first; emits again after any change. */
    fun observe(datasetId: String): Flow<List<UnknownImage>>

    /** Keeps [image] (encoded bytes) with [preview], a small JPEG of it, and returns its id. */
    suspend fun add(datasetId: String, image: ByteArray, preview: ByteArray): Result<Long, DataError.Local>

    /** The unknown image's bytes, as they were added. */
    suspend fun read(datasetId: String, id: Long): Result<ByteArray, DataError.Local>

    /** Deletes the unknown images with [ids]; ids that aren't there are ignored. */
    suspend fun remove(datasetId: String, ids: Set<Long>): EmptyResult<DataError.Local>
}
