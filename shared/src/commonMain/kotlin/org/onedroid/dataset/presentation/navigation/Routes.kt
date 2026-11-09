package org.onedroid.dataset.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DatasetListRoute

@Serializable
data class DatasetDetailRoute(val datasetId: String)

@Serializable
data object SettingsRoute

/** The full-screen camera for a dataset, capturing into [classId], or with auto-sort when it's null. */
@Serializable
data class CaptureRoute(val datasetId: String, val classId: Int? = null)
