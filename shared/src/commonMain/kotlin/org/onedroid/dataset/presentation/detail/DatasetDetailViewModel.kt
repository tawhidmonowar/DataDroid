package org.onedroid.dataset.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
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
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.domain.repository.SettingsRepository
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.ClassMatcher
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
import org.onedroid.dataset.resources.message_images_sorted
import org.onedroid.dataset.resources.message_images_unmatched
import org.jetbrains.compose.resources.getString

class DatasetDetailViewModel(
    private val datasetId: String,
    private val repository: DatasetRepository,
    private val settingsRepository: SettingsRepository,
    private val conversions: ImageConversions,
    private val sorter: ImageSorter,
) : ViewModel() {

    private val _state = MutableStateFlow(DatasetDetailState(datasetId))
    val state = _state.asStateFlow()

    private val _events = Channel<DatasetDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Camera frames waiting to be saved; if saving falls behind, the oldest are dropped. */
    private val captures = Channel<Pair<Int, ByteArray>>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var transferJob: Job? = null

    /** The latest dataset, which the auto-sort matcher follows. */
    private val dataset = MutableStateFlow<Dataset?>(null)

    /** Auto-sort: the open camera's work, and the matcher fitted to the dataset's current images. */
    private var autoSortJob: Job? = null
    private var matcher: ClassMatcher? = null

    /** Auto-sort frames to save, and the latest frame to preview (dropped while sorting is busy). */
    private val autoCaptures = Channel<ByteArray>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val previewFrames = Channel<ByteArray>(Channel.CONFLATED)
    private var nextUnknownId = 0L

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
            is DatasetDetailAction.OnOpenCamera -> {
                closeAutoSort()
                _state.update { it.copy(cameraClassId = action.classId, recording = false, shotPending = false) }
            }
            DatasetDetailAction.OnCloseCamera -> _state.update { it.copy(cameraClassId = null, recording = false, shotPending = false) }
            DatasetDetailAction.OnFlipCamera -> _state.update {
                it.copy(cameraFacing = if (it.cameraFacing == CameraFacing.Back) CameraFacing.Front else CameraFacing.Back)
            }
            DatasetDetailAction.OnStartRecording -> _state.update {
                // A tap shorter than the frame interval still captures the next frame.
                val canCapture = it.canEdit && (it.cameraClassId != null || it.autoSort?.ready == true)
                it.copy(recording = canCapture, shotPending = canCapture)
            }
            DatasetDetailAction.OnStopRecording -> _state.update { it.copy(recording = false) }
            is DatasetDetailAction.OnCapture -> onCapture(action.jpeg)
            DatasetDetailAction.OnOpenAutoSort -> openAutoSort()
            DatasetDetailAction.OnCloseAutoSort -> closeAutoSort()
            is DatasetDetailAction.OnStrictnessChange -> updateAutoSort { it.copy(strictness = action.strictness.coerceIn(0, 100)) }
            is DatasetDetailAction.OnAutoSortImagesPicked -> sortPickedImages(action.images)
            is DatasetDetailAction.OnUnknownsToNewClass -> fileUnknownsInNewClass(action.name, action.imageIds)
            is DatasetDetailAction.OnUnknownsToClass -> fileUnknowns(action.imageIds) { onProgress, sources ->
                repository.addImages(datasetId, action.classId, sources, onProgress)
            }
            is DatasetDetailAction.OnDiscardUnknowns -> updateAutoSort { autoSort ->
                autoSort.copy(unknowns = autoSort.unknowns.filterNot { action.imageIds == null || it.id in action.imageIds })
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
        this.dataset.value = dataset
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
                recording = state.recording && (cameraOpen || state.autoSort != null),
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

    private fun onCapture(jpeg: ByteArray) {
        // Frames arrive on the camera's thread; claim a pending single shot atomically.
        val current = _state.getAndUpdate { if (it.shotPending) it.copy(shotPending = false) else it }
        val save = current.capturing && current.canEdit
        when {
            current.autoSort != null -> if (save) autoCaptures.trySend(jpeg) else previewFrames.trySend(jpeg)
            save && current.cameraClassId != null -> captures.trySend(current.cameraClassId to jpeg)
        }
    }

    private fun openAutoSort() {
        if (_state.value.autoSort != null) return
        _state.update { it.copy(autoSort = AutoSortUi(), cameraClassId = null, recording = false, shotPending = false) }
        // Frames left from an earlier session belong to it.
        while (autoCaptures.tryReceive().isSuccess || previewFrames.tryReceive().isSuccess) Unit
        matcher = null
        autoSortJob = viewModelScope.launch {
            launch { followDataset() }
            sortFrames()
        }
    }

    private fun closeAutoSort() {
        autoSortJob?.cancel()
        autoSortJob = null
        matcher = null
        _state.update { if (it.autoSort == null) it else it.copy(autoSort = null, recording = false, shotPending = false) }
    }

    /** Refits the matcher whenever the dataset changes, so new images and classes count straight away. */
    private suspend fun followDataset() {
        dataset.filterNotNull().conflate().collect { current ->
            val first = matcher == null
            sorter.matcherFor(current) { done, total ->
                if (first) updateAutoSort { it.copy(prepared = done, toPrepare = total) }
            }
                .onSuccess { fitted ->
                    matcher = fitted
                    updateAutoSort { it.copy(preparing = false) }
                }
                .onFailure { updateAutoSort { it.copy(preparing = false, unavailable = true) } }
        }
    }

    /** Sorts camera frames: saved ones go to their class (or the unknowns), others just update the preview. */
    private suspend fun sortFrames() {
        while (true) {
            // Frames to save come first; a preview frame only when none is waiting.
            val (jpeg, save) = select {
                autoCaptures.onReceive { it to true }
                previewFrames.onReceive { it to false }
            }
            val match = classify(jpeg) ?: continue
            updateAutoSort { it.copy(prediction = match) }
            if (save) file(match, preview = jpeg, source = ImageSource { jpeg })
        }
    }

    /** Where [image] belongs, or null if sorting isn't ready or it isn't a decodable image. */
    private suspend fun classify(image: ByteArray): ClassMatch? {
        val fitted = matcher ?: return null
        val strictness = _state.value.autoSort?.strictness ?: return null
        return when (val embedded = sorter.embed(image)) {
            is Result.Error -> {
                updateAutoSort { it.copy(unavailable = true) }
                null
            }
            is Result.Success -> embedded.data?.let { fitted.match(it, strictness) }
        }
    }

    /** Saves a sorted image to its class, or holds it with the unknowns for the user to decide. */
    private suspend fun file(match: ClassMatch, preview: ByteArray, source: ImageSource) {
        val classId = match.classId
        if (classId == null) {
            updateAutoSort { autoSort ->
                val unknown = UnknownImage(nextUnknownId++, preview, source)
                autoSort.copy(unknowns = (autoSort.unknowns + unknown).takeLast(MAX_UNKNOWNS))
            }
            return
        }
        repository.addImages(datasetId, classId, listOf(source))
            .onSuccess { result -> if (result.added > 0) countSorted(classId, result.added) }
            .onFailure { showError(it) }
    }

    private fun countSorted(classId: Int, count: Int) =
        updateAutoSort { it.copy(sorted = it.sorted + (classId to (it.sorted[classId] ?: 0) + count)) }

    /** Sorts picked images: matches are saved to their classes, the rest join the unknowns. */
    private fun sortPickedImages(images: List<ImageSource>) {
        val fitted = matcher
        if (fitted == null || _state.value.autoSort?.ready != true) return
        runTransfer(TransferKind.SORT) {
            val byClass = mutableMapOf<Int, MutableList<ImageSource>>()
            var unmatched = 0
            var skipped = 0
            onProgress(0, images.size)
            images.forEachIndexed { index, source ->
                val bytes = try {
                    source.read()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val match = bytes?.let { classify(it) }
                val classId = match?.classId
                when {
                    match == null -> skipped++
                    classId != null -> byClass.getOrPut(classId) { mutableListOf() } += source
                    else -> {
                        val preview = sorter.preview(bytes)
                        if (preview == null) skipped++ else {
                            unmatched++
                            file(match, preview, source)
                        }
                    }
                }
                onProgress(index + 1, images.size)
            }
            var sorted = 0
            for ((classId, sources) in byClass) {
                repository.addImages(datasetId, classId, sources)
                    .onSuccess { result ->
                        sorted += result.added
                        skipped += result.skipped
                        countSorted(classId, result.added)
                    }
                    .onFailure { showError(it) }
            }
            if (sorted > 0) _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_sorted, sorted, sorted)))
            if (unmatched > 0) _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_unmatched, unmatched, unmatched)))
            if (skipped > 0) _events.send(DatasetDetailEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_skipped, skipped, skipped)))
        }
    }

    private fun fileUnknownsInNewClass(name: String, imageIds: Set<Long>?) {
        val valid = validateName(name, _state.value.classes.map { it.name })
        if (valid is Result.Error) return send(DatasetDetailEvent.ShowMessage(valid.error.toUiText()))
        fileUnknowns(imageIds) { onProgress, sources ->
            when (val created = repository.addClass(datasetId, name.trim())) {
                is Result.Error -> created
                is Result.Success -> repository.addImages(datasetId, created.data, sources, onProgress)
            }
        }
    }

    /** Saves the chosen unknowns with [save], and takes them off the list once they're saved. */
    private fun fileUnknowns(
        imageIds: Set<Long>?,
        save: suspend (onProgress: (Int, Int) -> Unit, sources: List<ImageSource>) -> Result<ImportResult, DataError>,
    ) {
        val chosen = _state.value.autoSort?.unknowns.orEmpty().filter { imageIds == null || it.id in imageIds }
        if (chosen.isEmpty()) return
        runImport { onProgress ->
            save(onProgress, chosen.map { it.source }).onSuccess {
                val filed = chosen.map { it.id }.toSet()
                updateAutoSort { autoSort -> autoSort.copy(unknowns = autoSort.unknowns.filterNot { it.id in filed }) }
            }
        }
    }

    private fun updateAutoSort(transform: (AutoSortUi) -> AutoSortUi) {
        _state.update { state -> state.autoSort?.let { state.copy(autoSort = transform(it)) } ?: state }
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
        _state.update { it.copy(transfer = TransferUi(kind), recording = false, shotPending = false) }
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

        /** Unknown images held in memory at once; older ones make way for new. */
        const val MAX_UNKNOWNS = 100
    }
}
