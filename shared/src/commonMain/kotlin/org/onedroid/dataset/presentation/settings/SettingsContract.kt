package org.onedroid.dataset.presentation.settings

import org.onedroid.dataset.presentation.components.UiText
import org.onedroid.dataset.domain.model.ImageSettings

data class SettingsState(
    val loading: Boolean = true,
    /** The settings as last saved. */
    val saved: ImageSettings = ImageSettings(),
    /** The settings being edited. */
    val draft: ImageSettings = ImageSettings(),
    val saving: Boolean = false,
    /** Datasets offered for the just-saved settings, or null when not asking. */
    val applyChoices: List<DatasetChoiceUi>? = null,
) {
    val hasChanges: Boolean get() = !loading && draft != saved
}

/** A dataset that could switch to the new settings; [matches] when it already uses them. */
data class DatasetChoiceUi(val id: String, val name: String, val imageCount: Int, val matches: Boolean)

sealed interface SettingsAction {
    data class OnChange(val settings: ImageSettings) : SettingsAction
    data object OnSave : SettingsAction
    data class OnApplyToDatasets(val ids: Set<String>) : SettingsAction
    data object OnDismissApply : SettingsAction
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: UiText) : SettingsEvent
}
