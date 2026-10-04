package org.onedroid.dataset.presentation.datasets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onedroid.dataset.core.domain.DataError
import org.onedroid.dataset.core.domain.onFailure
import org.onedroid.dataset.core.domain.onSuccess
import org.onedroid.dataset.core.presentation.UiText
import org.onedroid.dataset.core.presentation.toUiText
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.platform.picker.PickedFile
import org.onedroid.dataset.presentation.components.TransferKind
import org.onedroid.dataset.presentation.components.TransferUi
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.message_dataset_imported

class DatasetListViewModel(private val repository: DatasetRepository) : ViewModel() {

    private val _state = MutableStateFlow(DatasetListState())
    val state = _state.asStateFlow()

    private val _events = Channel<DatasetListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var transferJob: Job? = null

    init {
        repository.observeDatasets()
            .onEach { datasets -> _state.update { it.copy(loading = false, datasets = datasets.map { d -> d.toDatasetItemUi() }) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DatasetListAction) {
        when (action) {
            is DatasetListAction.OnOpenDataset -> send(DatasetListEvent.NavigateToDataset(action.id))
            is DatasetListAction.OnCreateDataset -> create(action.name)
            is DatasetListAction.OnRenameDataset -> viewModelScope.launch {
                repository.renameDataset(action.id, action.name).onFailure { showError(it) }
            }
            is DatasetListAction.OnDeleteDataset -> viewModelScope.launch {
                repository.deleteDataset(action.id).onFailure { showError(it) }
            }
            is DatasetListAction.OnExportDataset -> export(action.id)
            is DatasetListAction.OnImportArchive -> import(action.file)
            DatasetListAction.OnCancelTransfer -> transferJob?.cancel()
        }
    }

    private fun create(name: String) {
        viewModelScope.launch {
            repository.createDataset(name)
                .onSuccess { _events.send(DatasetListEvent.NavigateToDataset(it)) }
                .onFailure { showError(it) }
        }
    }

    private fun export(id: String) = runTransfer(TransferKind.EXPORT) {
        repository.exportDataset(id, onProgress = ::onProgress)
            .onSuccess { _events.send(DatasetListEvent.ShareFile(it)) }
            .onFailure { showError(it) }
    }

    private fun import(file: PickedFile) = runTransfer(TransferKind.IMPORT) {
        val bytes = try {
            file.readBytes(maxSize = MAX_ARCHIVE_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showError(if (e is IllegalArgumentException) DataError.Archive.TOO_LARGE else DataError.Archive.UNKNOWN)
            return@runTransfer
        }
        repository.importArchive(bytes, onProgress = ::onProgress)
            .onSuccess { id ->
                _events.send(DatasetListEvent.ShowMessage(UiText.Resource(Res.string.message_dataset_imported)))
                _events.send(DatasetListEvent.NavigateToDataset(id))
            }
            .onFailure { showError(it) }
    }

    private fun runTransfer(kind: TransferKind, block: suspend () -> Unit) {
        if (_state.value.transfer != null) return
        _state.update { it.copy(transfer = TransferUi(kind)) }
        transferJob = viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(transfer = null) }
            }
        }
    }

    private fun onProgress(done: Int, total: Int) {
        _state.update { state -> state.copy(transfer = state.transfer?.copy(done = done, total = total)) }
    }

    private suspend fun showError(error: DataError) = _events.send(DatasetListEvent.ShowMessage(error.toUiText()))

    private fun send(event: DatasetListEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** Archives are read into memory, so keep well below a typical heap. */
        const val MAX_ARCHIVE_BYTES = 256L shl 20
    }
}
