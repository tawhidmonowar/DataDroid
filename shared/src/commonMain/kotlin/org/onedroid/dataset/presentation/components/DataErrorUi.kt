package org.onedroid.dataset.presentation.components

import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.error_archive_empty_dataset
import org.onedroid.dataset.resources.error_archive_no_images
import org.onedroid.dataset.resources.error_archive_not_zip
import org.onedroid.dataset.resources.error_archive_too_large
import org.onedroid.dataset.resources.error_corrupt
import org.onedroid.dataset.resources.error_disk_full
import org.onedroid.dataset.resources.error_not_found
import org.onedroid.dataset.resources.error_sorting_unavailable
import org.onedroid.dataset.resources.error_unknown

fun DataError.toUiText(): UiText = UiText.Resource(
    when (this) {
        DataError.Local.NOT_FOUND -> Res.string.error_not_found
        DataError.Local.DISK_FULL -> Res.string.error_disk_full
        DataError.Local.CORRUPT -> Res.string.error_corrupt
        DataError.Archive.NOT_A_ZIP -> Res.string.error_archive_not_zip
        DataError.Archive.TOO_LARGE -> Res.string.error_archive_too_large
        DataError.Archive.NO_IMAGES -> Res.string.error_archive_no_images
        DataError.Archive.EMPTY_DATASET -> Res.string.error_archive_empty_dataset
        DataError.Sorting.MODEL_UNAVAILABLE -> Res.string.error_sorting_unavailable
        DataError.Local.UNKNOWN, DataError.Archive.UNKNOWN -> Res.string.error_unknown
    }
)
