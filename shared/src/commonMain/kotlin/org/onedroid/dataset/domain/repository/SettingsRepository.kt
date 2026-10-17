package org.onedroid.dataset.domain.repository

import kotlinx.coroutines.flow.Flow
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.model.ImageSettings

/** App-wide preferences. */
interface SettingsRepository {

    /** The image settings new datasets start with; emits again after they're saved. */
    fun observeImageSettings(): Flow<ImageSettings>

    suspend fun saveImageSettings(settings: ImageSettings): EmptyResult<DataError.Local>
}
