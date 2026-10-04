package org.onedroid.dataset.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onedroid.dataset.core.domain.DataError
import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.core.presentation.UiText
import org.onedroid.dataset.core.presentation.toUiText
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.SettingsRepository
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.message_conversion_started
import org.onedroid.dataset.resources.message_settings_saved

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val datasetRepository: DatasetRepository,
    private val conversions: ImageConversions,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.observeImageSettings().first()
            _state.update { it.copy(loading = false, saved = settings, draft = settings) }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnChange -> _state.update { it.copy(draft = action.settings.sanitized()) }
            SettingsAction.OnSave -> save()
            is SettingsAction.OnApplyToDatasets -> apply(action.ids)
            SettingsAction.OnDismissApply -> _state.update { it.copy(applyChoices = null) }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.hasChanges || current.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = settingsRepository.saveImageSettings(current.draft)) {
                is Result.Error -> {
                    _state.update { it.copy(saving = false) }
                    showError(result.error)
                }
                is Result.Success -> {
                    val datasets = datasetRepository.observeDatasets().first()
                    val choices = datasets.mapNotNull { summary ->
                        val dataset = datasetRepository.observeDataset(summary.id).first() ?: return@mapNotNull null
                        DatasetChoiceUi(dataset.id, dataset.name, dataset.imageCount, matches = dataset.imageSettings == current.draft)
                    }
                    _state.update {
                        it.copy(saving = false, saved = current.draft, applyChoices = choices.takeIf { c -> c.any { !it.matches } })
                    }
                    _events.send(SettingsEvent.ShowMessage(UiText.Resource(Res.string.message_settings_saved)))
                }
            }
        }
    }

    private fun apply(ids: Set<String>) {
        val settings = _state.value.saved
        _state.update { it.copy(applyChoices = null) }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var started = 0
            for (id in ids) {
                when (val result = datasetRepository.updateImageSettings(id, settings)) {
                    is Result.Error -> showError(result.error)
                    is Result.Success -> {
                        conversions.start(id)
                        started++
                    }
                }
            }
            if (started > 0) {
                _events.send(SettingsEvent.ShowMessage(UiText.Plural(Res.plurals.message_conversion_started, started, started)))
            }
        }
    }

    private suspend fun showError(error: DataError) = _events.send(SettingsEvent.ShowMessage(error.toUiText()))
}
