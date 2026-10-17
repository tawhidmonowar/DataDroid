package org.onedroid.dataset.core.domain

sealed interface DataError : Error {

    /** Failures reading or writing the app's own storage. */
    enum class Local : DataError {
        NOT_FOUND,
        DISK_FULL,
        CORRUPT,
        UNKNOWN,
    }

    /** Failures reading an archive someone picked, or writing an export. */
    enum class Archive : DataError {
        NOT_A_ZIP,
        TOO_LARGE,
        NO_IMAGES,
        EMPTY_DATASET,
        UNKNOWN,
    }

    /** Failures sorting images into classes automatically. */
    enum class Sorting : DataError {
        /** The on-device image model couldn't be loaded. */
        MODEL_UNAVAILABLE,
    }
}
