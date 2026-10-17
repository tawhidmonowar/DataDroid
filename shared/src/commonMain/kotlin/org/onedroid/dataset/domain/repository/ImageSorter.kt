package org.onedroid.dataset.domain.repository

import org.onedroid.dataset.core.domain.DataError
import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.usecase.ClassMatcher

/** Recognises which class an image belongs to, from what each class's images look like. */
interface ImageSorter {

    /**
     * A matcher for [dataset]'s classes as they are now. Images not seen before are embedded first (reported
     * through [onProgress]) and remembered, so later calls only pay for new images. Images that can't be read
     * are left out.
     */
    suspend fun matcherFor(
        dataset: Dataset,
        onProgress: ProgressListener = { _, _ -> },
    ): Result<ClassMatcher, DataError.Sorting>

    /** The embedding [ClassMatcher] compares, for encoded image bytes; null if they aren't a decodable image. */
    suspend fun embed(image: ByteArray): Result<FloatArray?, DataError.Sorting>

    /** A small square JPEG to show an image that isn't saved yet; null if it can't be decoded. */
    suspend fun preview(image: ByteArray): ByteArray?
}
