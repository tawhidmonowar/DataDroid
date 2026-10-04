package org.onedroid.dataset.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DatasetListRoute

@Serializable
data class DatasetDetailRoute(val datasetId: String)
