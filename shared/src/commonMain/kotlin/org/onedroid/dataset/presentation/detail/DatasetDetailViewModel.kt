package org.onedroid.dataset.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onedroid.dataset.core.domain.DataError
import org.onedroid.dataset.core.domain.Result
import org.onedroid.dataset.core.domain.onFailure
import org.onedroid.dataset.core.domain.onSuccess
import org.onedroid.dataset.core.presentation.UiText
import org.onedroid.dataset.core.presentation.toUiText
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.model.LabeledImage
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.SettingsRepository
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.domain.usecase.validateName
import org.onedroid.dataset.platform.camera.CameraFacing
import org.onedroid.dataset.platform.picker.PickedFile
import org.onedroid.dataset.platform.picker.PickedFolder
import org.onedroid.dataset.presentation.components.ImportPreviewUi
import org.onedroid.dataset.presentation.components.TransferKind
import org.onedroid.dataset.presentation.components.TransferUi
import org.onedroid.dataset.presentation.components.toUiText
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.class_default_name
import org.onedroid.dataset.resources.message_images_added
import org.onedroid.dataset.resources.message_images_converted
import org.onedroid.dataset.resources.message_images_skipped
import org.jetbrains.compose.resources.getString

class DatasetDetailViewModel(
    private val datasetId: String,
    private val repository: DatasetRepository,
    private val settingsRepository: SettingsRepository,
    private val conversions: ImageConversions,
) : ViewModel() {

    private val _state = MutableStateFlow(DatasetDetailState(datasetId))
    val state = _state.asStateFlow()

    private val _events = Channel<DatasetDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Camera frames waiting to be saved; if saving falls behind, the oldest are dropped. */
    private val captures = Channel<Pair<Int, ByteArray>>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var transferJob: Job? = null

    init {
        repository.observeDataset(datasetId)
            .onEach { dataset -> if (dataset == null) onDatasetMissing() else onDataset(dataset) }
            .launchIn(viewModelScope)

        settingsRepository.observeImageSettings()
            .onEach { settings -> _state.update { it.copy(defaultImageSettings = settings) } }
            .launchIn(viewModelScope)

        conversions.progress
            .map { it[datasetId] }
            .distinctUntilChanged()
            .onEach { progress ->
                _state.update { state ->
                    val conversion = progress?.let { TransferUi(TransferKind.CONVERT, it.done, it.total) }
                    state.copy(conversion = conversion, recording = state.recording && conversion == null)
                }
            }
            .launchIn(viewModelScope)

        conversions.outcomes
            .filter { it.datasetId == datasetId }
            .onEach { outcome -> outcome.result.onSuccess { reportConversion(it) }.onFailure { showError(it) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            for ((classId, jpeg) in captures) {
                repository.addImages(datasetId, classId, listOf(ImageSource { jpeg })).onFailure { showError(it) }
            }
        }
    }

    fun onAction(action: DatasetDetailAction) {
        when (action) {
            DatasetDetailAction.OnBack -> send(DatasetDetailEvent.NavigateBack)
            is DatasetDetailAction.OnRenameDataset -> renameDataset(action.name)
            DatasetDetailAction.OnAddClass -> addClass()
            is DatasetDetailAction.OnRenameClass -> renameClass(action.classId, action.name)
            is DatasetDetailAction.OnDeleteClass -> launchEdit { repository.deleteClass(datasetId, action.classId) }
            is DatasetDetailAction.OnClearClass -> {
                val imageIds = _state.value.classes.firstOrNull { it.id == action.classId }?.imageIds.orEmpty().toSet()
                if (imageIds.isNotEmpty()) launchEdit { repository.deleteImages(datasetId, imageIds) }
            }
            is DatasetDetailAction.OnOpenCamera -> _state.update { it.copy(cameraClassId = action.classId, recording = false) }
            DatasetDetailAction.OnCloseCamera -> _state.update { it.copy(cameraClassId = null, recording = false) }
            DatasetDetailAction.OnFlipCamera -> _state.update {
                it.copy(cameraFacing = if (it.cameraFacing == CameraFacing.Back) CameraFacing.Front else CameraFacing.Back)
            }
            DatasetDetailAction.OnStartRecording -> _state.update { it.copy(recording = it.cameraClassId != null && it.canEdit) }
            DatasetDetailAction.OnStopRecording -> _state.update { it.copy(recording = false) }
            is DatasetDetailAction.OnCapture -> {
                val current = _state.value
                val classId = current.cameraClassId
                if (current.recording && classId != null) captures.trySend(classId to action.jpeg)
            }
            is DatasetDetailAction.OnImagesPicked -> runImport { onProgress ->
                repository.addImages(datasetId, action.classId, action.images, onProgress)
            }
            is DatasetDetailAction.OnClassFolderPicked -> runImport { onProgress ->
                val images = ImageFolders.allImages(action.folder.images())
                if (images.isEmpty()) Result.Error(DataError.Archive.NO_IMAGES)
                else repository.addImages(datasetId, action.classId, images, onProgress)
            }
            is DatasetDetailAction.OnDatasetFolderPicked -> importFolders { Result.Success(action.folder.images()) }
            is DatasetDetailAction.OnDatasetZipPicked -> importFolders {
                val bytes = try {
                    action.file.readBytes(maxSize = MAX_ARCHIVE_BYTES)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@importFolders Result.Error(if (e is IllegalArgumentException) DataError.Archive.TOO_LARGE else DataError.Archive.UNKNOWN)
                }
                when (val read = repository.readArchive(bytes)) {
                    is Result.Error -> read
                    is Result.Success -> Result.Success(read.data.images)
                }
            }
            is DatasetDetailAction.OnImportNamingChange -> _state.update { it.copy(importPreview = it.importPreview?.copy(naming = action.naming)) }
            is DatasetDetailAction.OnImportMergeSplitsChange -> _state.update { it.copy(importPreview = it.importPreview?.copy(mergeSplits = action.merge)) }
            DatasetDetailAction.OnConfirmImport -> {
                val preview = _state.value.importPreview
                _state.update { it.copy(importPreview = null) }
                if (preview != null) runImport { onProgress -> repository.importLabeledImages(datasetId, preview.groups, onProgress) }
            }
            DatasetDetailAction.OnDismissImport -> _state.update { it.copy(importPreview = null) }
            DatasetDetailAction.OnCancelTransfer -> transferJob?.cancel()
            is DatasetDetailAction.OnDeleteImages -> launchEdit { repository.deleteImages(datasetId, action.imageIds) }
            is DatasetDetailAction.OnMoveImages -> launchEdit { repository.moveImages(datasetId, action.imageIds, action.toClassId) }
            DatasetDetailAction.OnExport -> export()
            is DatasetDetailAction.OnSaveImageSettings -> saveImageSettings(action.settings, action.convertExisting)
            DatasetDetailAction.OnConvertImages -> if (_state.value.canEdit) conversions.start(datasetId)
            DatasetDetailAction.OnCancelConversion -> conversions.cancel(datasetId)
        }
    }

    private fun onDataset(dataset: Dataset) {
        _state.update { state ->
            val classes = dataset.classes.mapIndexed { index, c -> c.toClassUi(index) }
            // Close the camera if its class was deleted.
            val cameraOpen = state.cameraClassId != null && classes.any { it.id == state.cameraClassId }
            state.copy(
                loading = false,
                name = dataset.name,
                classes = classes,
                balance = computeClassBalance(dataset.classes),
                cameraClassId = if (cameraOpen) state.cameraClassId else null,
                recording = state.recording && cameraOpen,
                imageSettings = dataset.imageSettings,
                outdatedImageCount = dataset.outdatedImageCount,
            )
        }
    }

    private suspend fun onDatasetMissing() {
        if (_state.value.loading) showError(DataError.Local.NOT_FOUND)
        _events.send(DatasetDetailEvent.NavigateBack)
    }

    private fun renameDataset(name: String) {
        when (val valid = validateName(name)) {
            is Result.Error -> send(DatasetDetailEvent.ShowMessage(valid.error.toUiText()))
            is Result.Success -> launchEdit { repository.renameDataset(datasetId, name) }
        }
    }

    private fun addClass() {
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            val classes = _state.value.classes
            val name = uniqueName(getString(Res.string.class_default_name, classes.size + 1), classes.map { it.name })
            repository.addClass(datasetId, name)
                .onSuccess { _events.send(DatasetDetailEvent.FocusClass(it)) }
                .onFailure { showError(it) }
        }
    }

    private fun renameClass(classId: Int, name: String) {
        val others = _state.value.classes.filter { it.id != classId }.map { it.name }
        when (val valid = validateName(name, others)) {
            is Result.Error -> send(DatasetDetailEvent.ShowMessage(valid.error.toUiText()))
            is Result.Success -> launchEdit { repository.renameClass(datasetId, classId, name) }
        }
    }

    private fun export() {
        runTransfer(TransferKind.EXPORT) {
            repository.exportDataset(datasetId, onProgress = ::onProgress)
                .onSuccess { _events.send(DatasetDetailEvent.ShareFile(it)) }
                .onFailure { showError(it) }
        }
    }

    private fun saveImageSettings(settings: ImageSettings, convertExisting: Boolean) {
        if (!_state.value.canEdit) return
        viewModelScope.launch {
            repository.updateImageSettings(datasetId, settings)
                .onSuccess { if (convertExisting) conversions.start(datasetId) }
                .onFailure { showError(it) }
        }
    }

    private suspend fun reportConversion(result: ImportResult) {
        if (result.added > 0) {
            _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_converted, result.added, result.added)))
        }
        if (result.skipped > 0) {
            _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_skipped, result.skipped, result.skipped)))
        }
    }

    /**
     * Imports class folders from [load]. Unambiguous layouts are imported straight away; for nested or
     * split folders the user first chooses how folders become classes, and OnConfirmImport continues.
     */
    private fun importFolders(load: suspend () -> Result<List<LabeledImage>, DataError>) {
        runTransfer(TransferKind.IMPORT) {
            val images = when (val loaded = load()) {
                is Result.Error -> return@runTransfer showError(loaded.error)
                is Result.Success -> loaded.data
            }
            val layout = ImageFolders.analyze(images)
            val preview = ImportPreviewUi.defaultFor(layout)
            when {
                preview.groups.isEmpty() -> showError(DataError.Archive.NO_IMAGES)
                layout.needsChoice -> _state.update { it.copy(importPreview = preview) }
                else -> repository.importLabeledImages(datasetId, preview.groups, ::onProgress)
                    .onSuccess { reportImport(it) }
                    .onFailure { showError(it) }
            }
        }
    }

    private fun runImport(block: suspend (onProgress: (Int, Int) -> Unit) -> Result<ImportResult, DataError>) {
        runTransfer(TransferKind.IMPORT) {
            block(::onProgress)
                .onSuccess { result -> reportImport(result) }
                .onFailure { showError(it) }
        }
    }

    private suspend fun reportImport(result: ImportResult) {
        if (result.added > 0) {
            _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_added, result.added, result.added)))
        }
        if (result.skipped > 0) {
            _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_skipped, result.skipped, result.skipped)))
        }
    }

    private fun runTransfer(kind: TransferKind, block: suspend () -> Unit) {
        if (!_state.value.canEdit) return
        _state.update { it.copy(transfer = TransferUi(kind), recording = false) }
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

    private fun launchEdit(block: suspend () -> Result<*, DataError>) {
        viewModelScope.launch { block().onFailure { showError(it) } }
    }

    private suspend fun showError(error: DataError) = _events.send(DatasetDetailEvent.ShowMessage(error.toUiText()))

    private fun send(event: DatasetDetailEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** Archives are read into memory, so keep well below a typical heap. */
        const val MAX_ARCHIVE_BYTES = 256L shl 20
    }
}
