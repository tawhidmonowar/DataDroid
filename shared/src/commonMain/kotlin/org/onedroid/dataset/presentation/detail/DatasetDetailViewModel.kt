package org.onedroid.dataset.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.model.ArchiveContents
import org.onedroid.dataset.domain.onFailure
import org.onedroid.dataset.domain.onSuccess
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.SettingsRepository
import org.onedroid.dataset.domain.repository.UnknownImageRepository
import org.onedroid.dataset.domain.usecase.ExportDataset
import org.onedroid.dataset.domain.usecase.FileUnknownImages
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.domain.usecase.ImageFolders
import org.onedroid.dataset.domain.usecase.KeepUnknownImages
import org.onedroid.dataset.domain.usecase.UnknownDestination
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.domain.usecase.validateName
import org.onedroid.dataset.presentation.components.PickedArchive
import org.onedroid.dataset.presentation.components.ImportPreviewUi
import org.onedroid.dataset.presentation.components.TransferKind
import org.onedroid.dataset.presentation.components.TransferUi
import org.onedroid.dataset.presentation.components.UiText
import org.onedroid.dataset.presentation.components.toUiText
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.class_default_name
import org.onedroid.dataset.resources.message_images_added
import org.onedroid.dataset.resources.message_images_converted
import org.onedroid.dataset.resources.message_images_deleted
import org.onedroid.dataset.resources.message_images_skipped

class DatasetDetailViewModel(
    private val datasetId: String,
    private val repository: DatasetRepository,
    private val settingsRepository: SettingsRepository,
    private val conversions: ImageConversions,
    private val unknownImages: UnknownImageRepository,
    private val fileUnknowns: FileUnknownImages,
    private val exportDataset: ExportDataset,
    private val keepUnknownImages: KeepUnknownImages,
    /** Outlives this view model, to finish deletes still waiting on undo when the screen goes. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(DatasetDetailState(datasetId))
    val state = _state.asStateFlow()

    private val _events = Channel<DatasetDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var transferJob: Job? = null
    private val archive = PickedArchive()

    /**
     * Images deleted while undo is offered: hidden straight away, but only deleted for good once the screen
     * reports the offer has gone (by [DatasetDetailAction.OnCommitDelete]), or when the screen goes.
     * [undoGroups] holds each delete's images, so undo and commit act only on deletes still on offer.
     */
    private val pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())
    private val undoGroups = HashSet<Set<Long>>()

    init {
        combine(repository.observeDataset(datasetId), pendingDeletes) { dataset, pending -> dataset?.withoutImages(pending) }
            .onEach { dataset -> if (dataset == null) onDatasetMissing() else onDataset(dataset) }
            .launchIn(viewModelScope)

        unknownImages.observe(datasetId)
            .onEach { unknowns -> _state.update { it.copy(unknowns = unknowns) } }
            .launchIn(viewModelScope)

        settingsRepository.observeImageSettings()
            .onEach { settings -> _state.update { it.copy(defaultImageSettings = settings) } }
            .launchIn(viewModelScope)

        conversions.progress
            .map { it[datasetId] }
            .distinctUntilChanged()
            .onEach { progress ->
                _state.update { state -> state.copy(conversion = progress?.let { TransferUi(TransferKind.CONVERT, it.done, it.total) }) }
            }
            .launchIn(viewModelScope)

        conversions.outcomes
            .filter { it.datasetId == datasetId }
            .onEach { outcome -> outcome.result.onSuccess { reportConversion(it) }.onFailure { showError(it) } }
            .launchIn(viewModelScope)
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
            is DatasetDetailAction.OnUnknownsToClass -> fileAllUnknowns(UnknownDestination.ExistingClass(action.classId))
            is DatasetDetailAction.OnUnknownsToNewClass -> {
                val valid = validateName(action.name, _state.value.classes.map { it.name })
                if (valid is Result.Error) send(DatasetDetailEvent.ShowMessage(valid.error.toUiText()))
                else fileAllUnknowns(UnknownDestination.NewClass(action.name))
            }
            is DatasetDetailAction.OnDiscardUnknowns -> launchEdit {
                unknownImages.remove(datasetId, action.imageIds ?: _state.value.unknowns.map { it.id }.toSet())
            }
            is DatasetDetailAction.OnImagesPicked -> runImport { onProgress ->
                repository.addImages(datasetId, action.classId, action.images, onProgress)
            }
            is DatasetDetailAction.OnClassFolderPicked -> runImport { onProgress ->
                val images = ImageFolders.allImages(action.folder.images())
                if (images.isEmpty()) Result.Error(DataError.Archive.NO_IMAGES)
                else repository.addImages(datasetId, action.classId, images, onProgress)
            }
            is DatasetDetailAction.OnDatasetFolderPicked -> importFolders { Result.Success(ArchiveContents(null, action.folder.images())) }
            is DatasetDetailAction.OnDatasetZipPicked -> importFolders {
                val path = try {
                    action.file.localPath(maxSize = MAX_ARCHIVE_BYTES)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@importFolders Result.Error(if (e is IllegalArgumentException) DataError.Archive.TOO_LARGE else DataError.Archive.UNKNOWN)
                }
                archive.hold(path)
                repository.readArchive(path)
            }
            is DatasetDetailAction.OnImportNamingChange -> _state.update { it.copy(importPreview = it.importPreview?.copy(naming = action.naming)) }
            is DatasetDetailAction.OnImportMergeSplitsChange -> _state.update { it.copy(importPreview = it.importPreview?.copy(mergeSplits = action.merge)) }
            DatasetDetailAction.OnConfirmImport -> {
                val preview = _state.value.importPreview
                _state.update { it.copy(importPreview = null) }
                if (preview != null) {
                    runTransfer(TransferKind.IMPORT) {
                        try {
                            importPreview(preview)
                        } finally {
                            archive.release()
                        }
                    }
                }
            }
            DatasetDetailAction.OnDismissImport -> {
                _state.update { it.copy(importPreview = null) }
                archive.release()
            }
            DatasetDetailAction.OnCancelTransfer -> transferJob?.cancel()
            is DatasetDetailAction.OnDeleteImages -> deleteWithUndo(action.imageIds)
            is DatasetDetailAction.OnUndoDelete -> undoDelete(action.imageIds)
            is DatasetDetailAction.OnCommitDelete -> commitDelete(action.imageIds)
            is DatasetDetailAction.OnMoveImages -> launchEdit { repository.moveImages(datasetId, action.imageIds, action.toClassId) }
            DatasetDetailAction.OnExport -> export()
            is DatasetDetailAction.OnSaveImageSettings -> saveImageSettings(action.settings, action.convertExisting)
            DatasetDetailAction.OnConvertImages -> if (_state.value.canEdit) conversions.start(datasetId)
            DatasetDetailAction.OnCancelConversion -> conversions.cancel(datasetId)
        }
    }

    override fun onCleared() {
        archive.release()
        val pending = pendingDeletes.value
        if (pending.isNotEmpty()) appScope.launch { repository.deleteImages(datasetId, pending) }
    }

    private fun onDataset(dataset: Dataset) {
        _state.update { state ->
            state.copy(
                loading = false,
                name = dataset.name,
                classes = dataset.classes.mapIndexed { index, c -> c.toClassUi(index) },
                balance = computeClassBalance(dataset.classes),
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

    /**
     * Hides [imageIds] at once and offers undo. They're deleted for good when the screen reports the offer
     * has gone, not on a timer here: the offer may wait behind other messages, or stay up longer for
     * accessibility, and undo must work for as long as it's shown.
     */
    private fun deleteWithUndo(imageIds: Set<Long>) {
        if (imageIds.isEmpty() || !_state.value.canEdit) return
        undoGroups += imageIds
        pendingDeletes.update { it + imageIds }
        send(DatasetDetailEvent.ShowUndo(UiText.Plural(Res.plurals.message_images_deleted, imageIds.size, imageIds.size), imageIds))
    }

    private fun undoDelete(imageIds: Set<Long>) {
        if (!undoGroups.remove(imageIds)) return
        pendingDeletes.update { it - imageIds }
    }

    private fun commitDelete(imageIds: Set<Long>) {
        if (!undoGroups.remove(imageIds)) return
        viewModelScope.launch {
            // Unhidden only after deleting, so a failed delete shows the images again.
            repository.deleteImages(datasetId, imageIds).onFailure { showError(it) }
            pendingDeletes.update { it - imageIds }
        }
    }

    private fun fileAllUnknowns(destination: UnknownDestination) {
        val unknowns = _state.value.unknowns
        if (unknowns.isEmpty()) return
        runImport { onProgress -> fileUnknowns(datasetId, unknowns, destination, onProgress) }
    }

    private fun export() {
        runTransfer(TransferKind.EXPORT) {
            exportDataset(datasetId, onProgress = ::onProgress)
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
    private fun importFolders(load: suspend () -> Result<ArchiveContents, DataError>) {
        runTransfer(TransferKind.IMPORT) {
            // A new pick replaces any preview still showing, and its archive.
            _state.update { it.copy(importPreview = null) }
            archive.release()
            var awaitingChoice = false
            try {
                val contents = when (val loaded = load()) {
                    is Result.Error -> return@runTransfer showError(loaded.error)
                    is Result.Success -> loaded.data
                }
                val layout = ImageFolders.analyze(contents.images)
                val preview = ImportPreviewUi.defaultFor(layout, unknownImages = contents.unknownImages)
                when {
                    preview.groups.isEmpty() -> showError(DataError.Archive.NO_IMAGES)
                    layout.needsChoice -> {
                        _state.update { it.copy(importPreview = preview) }
                        awaitingChoice = true
                    }
                    else -> importPreview(preview)
                }
            } finally {
                // An archive's images are read until the import ends, so its copy goes only then.
                if (!awaitingChoice) archive.release()
            }
        }
    }

    /** Imports [preview]'s classes, and adds the images it kept apart as unknown to this dataset's unknowns. */
    private suspend fun importPreview(preview: ImportPreviewUi) {
        repository.importLabeledImages(datasetId, preview.groups, ::onProgress)
            .onSuccess {
                reportImport(it)
                keepUnknownImages(datasetId, preview.unknownImages).onFailure { error -> showError(error) }
            }
            .onFailure { showError(it) }
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

    private fun launchEdit(block: suspend () -> Result<*, DataError>) {
        viewModelScope.launch { block().onFailure { showError(it) } }
    }

    private suspend fun showError(error: DataError) = _events.send(DatasetDetailEvent.ShowMessage(error.toUiText()))

    private fun send(event: DatasetDetailEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** Archives are read from disk; zip offsets are 32-bit, so this is the most it can address. */
        const val MAX_ARCHIVE_BYTES = (1L shl 32) - 1
    }
}

/** This dataset as it looks with [imageIds] gone. */
private fun Dataset.withoutImages(imageIds: Set<Long>): Dataset =
    if (imageIds.isEmpty()) this else copy(classes = classes.map { c -> c.copy(imageIds = c.imageIds.filter { it !in imageIds }) })
