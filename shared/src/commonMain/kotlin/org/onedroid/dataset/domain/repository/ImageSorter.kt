package org.onedroid.dataset.domain.repository

import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import kotlinx.coroutines.flow.Flow
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.EmbeddingModel
import org.onedroid.dataset.domain.usecase.ClassMatcher

/** Recognises which class an image belongs to, from what each class's images look like. */
interface ImageSorter {

    /**
     * The model images are compared with. When it changes, matchers from [matcherFor] no longer fit what
     * [embed] returns and must be fitted again.
     */
    val model: Flow<EmbeddingModel>

    /**
     * A matcher for [dataset]'s classes as they are now. Images not seen before are embedded first (reported
     * through [onProgress]) and remembered, so later calls only pay for new images. Images that can't be read
     * are left out.
     */
    suspend fun matcherFor(
        dataset: Dataset,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ClassMatcher, DataError.Sorting>

    /**
     * The embedding [ClassMatcher] compares, for encoded image bytes; null if they aren't a decodable image.
     * [saving] says these exact bytes are about to be added to a dataset, so the embedding is kept for
     * [matcherFor] rather than worked out again from the stored image.
     */
    suspend fun embed(image: ByteArray, saving: Boolean = false): Result<FloatArray?, DataError.Sorting>

    /** A small square JPEG to show an image that isn't saved yet; null if it can't be decoded. */
    suspend fun preview(image: ByteArray): ByteArray?
}
