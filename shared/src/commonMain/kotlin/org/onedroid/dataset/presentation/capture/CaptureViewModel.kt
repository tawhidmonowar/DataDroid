package org.onedroid.dataset.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.onedroid.dataset.domain.DataError
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.model.Dataset
import org.onedroid.dataset.domain.model.EmbeddingModel
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.model.ImportResult
import org.onedroid.dataset.domain.onFailure
import org.onedroid.dataset.domain.onSuccess
import org.onedroid.dataset.domain.repository.DatasetRepository
import org.onedroid.dataset.domain.repository.ImageSorter
import org.onedroid.dataset.domain.repository.UnknownImageRepository
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.ClassMatcher
import org.onedroid.dataset.domain.usecase.FileUnknownImages
import org.onedroid.dataset.domain.usecase.ImageConversions
import org.onedroid.dataset.domain.usecase.UnknownDestination
import org.onedroid.dataset.domain.usecase.validateName
import org.onedroid.dataset.core.platform.camera.CameraFacing
import org.onedroid.dataset.presentation.components.TransferKind
import org.onedroid.dataset.presentation.components.TransferUi
import org.onedroid.dataset.presentation.components.UiText
import org.onedroid.dataset.presentation.components.toUiText
import org.onedroid.dataset.presentation.detail.toClassUi
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.message_images_added
import org.onedroid.dataset.resources.message_images_skipped
import org.onedroid.dataset.resources.message_images_sorted
import org.onedroid.dataset.resources.message_images_unmatched
import org.onedroid.dataset.resources.message_unknowns_full

/**
 * The capture screen: saves camera frames and picked photos to the chosen class, or with auto-sort to the
 * class each looks like, holding the ones like no class with the unknowns.
 */
class CaptureViewModel(
    private val datasetId: String,
    initialClassId: Int?,
    private val repository: DatasetRepository,
    private val conversions: ImageConversions,
    private val sorter: ImageSorter,
    private val unknownImages: UnknownImageRepository,
    private val fileUnknowns: FileUnknownImages,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CaptureState(datasetId, target = initialClassId?.let { CaptureTarget.Class(it) } ?: CaptureTarget.Auto),
    )
    val state = _state.asStateFlow()

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The latest dataset, which the auto-sort matcher follows. */
    private val dataset = MutableStateFlow<Dataset?>(null)

    /** Frames for a chosen class waiting to be saved; if saving falls behind, the oldest are dropped. */
    private val classCaptures = Channel<Pair<Int, ByteArray>>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Auto-sort frames to save, and the latest frame to preview (dropped while sorting is busy). */
    private val autoCaptures = Channel<ByteArray>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val previewFrames = Channel<ByteArray>(Channel.CONFLATED)

    /** The matcher fitted to the dataset's current images; kept while capturing into a class, for a quick return. */
    private var matcher: ClassMatcher? = null

    private var transferJob: Job? = null

    /** Whether the user has been told no more unknowns can be held, since there was last room. */
    private var warnedUnknownsFull = false

    /**
     * Dataset images and unknowns seen so far, null until the first of each arrives; whatever turns up after
     * that was added while the screen is open, and joins [CaptureState.recent].
     */
    private var seenImageIds: MutableSet<Long>? = null
    private var seenUnknownIds: MutableSet<Long>? = null

    init {
        repository.observeDataset(datasetId)
            .onEach { dataset -> if (dataset == null) _events.send(CaptureEvent.NavigateBack) else onDataset(dataset) }
            .launchIn(viewModelScope)

        unknownImages.observe(datasetId)
            .onEach { unknowns ->
                if (unknowns.size < MAX_UNKNOWNS) warnedUnknownsFull = false
                val fresh = newSince(seenUnknownIds, unknowns.map { it.id }).also { seenUnknownIds = it.seen }.ids
                val added = unknowns.filter { it.id in fresh }.asReversed().map { RecentCapture.Unknown(it) }
                val current = unknowns.map { it.id }.toSet()
                _state.update { state ->
                    // Filed or discarded unknowns leave the strip; filed ones come back as the images they became.
                    val kept = state.recent.filter { it !is RecentCapture.Unknown || it.image.id in current }
                    state.copy(unknowns = unknowns, recent = (added + kept).take(MAX_RECENT))
                }
            }
            .launchIn(viewModelScope)

        conversions.progress
            .map { it[datasetId] != null }
            .distinctUntilChanged()
            .onEach { converting -> _state.update { it.copy(converting = converting, recording = it.recording && !converting) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            for ((classId, jpeg) in classCaptures) save(classId, listOf(ImageSource { jpeg }))
        }

        // Auto-sort only runs while it's the target, so capturing into a class doesn't pay for it.
        viewModelScope.launch {
            _state.map { it.target == CaptureTarget.Auto }.distinctUntilChanged().collectLatest { auto ->
                if (auto) coroutineScope {
                    launch { followDataset() }
                    sortFrames()
                }
            }
        }
    }

    fun onAction(action: CaptureAction) {
        when (action) {
            CaptureAction.OnBack -> send(CaptureEvent.NavigateBack)
            is CaptureAction.OnSelectTarget -> _state.update {
                it.copy(target = action.target, recording = false, shotPending = false, autoSort = it.autoSort.copy(prediction = null))
            }
            is CaptureAction.OnAddClass -> addClass(action.name)
            CaptureAction.OnFlipCamera -> _state.update {
                it.copy(facing = if (it.facing == CameraFacing.Back) CameraFacing.Front else CameraFacing.Back)
            }
            CaptureAction.OnStartRecording -> _state.update {
                // A tap shorter than the frame interval still captures the next frame.
                val canCapture = it.canCapture
                it.copy(recording = canCapture, shotPending = canCapture)
            }
            CaptureAction.OnStopRecording -> _state.update { it.copy(recording = false) }
            is CaptureAction.OnCapture -> onCapture(action.jpeg)
            is CaptureAction.OnStrictnessChange -> _state.update { it.copy(strictness = action.strictness.coerceIn(0, 100)) }
            is CaptureAction.OnPhotosPicked -> when (val target = _state.value.target) {
                CaptureTarget.Auto -> sortPickedImages(action.images)
                is CaptureTarget.Class -> runTransfer(TransferKind.IMPORT) {
                    repository.addImages(datasetId, target.classId, action.images, ::onProgress)
                        .onSuccess { result ->
                            countSaved(target.classId, result.added)
                            report(result)
                        }
                        .onFailure { showError(it) }
                }
            }
            is CaptureAction.OnUnknownsToClass -> fileAllUnknowns(UnknownDestination.ExistingClass(action.classId))
            is CaptureAction.OnUnknownsToNewClass -> {
                val valid = validateName(action.name, _state.value.classes.map { it.name })
                if (valid is Result.Error) send(CaptureEvent.ShowMessage(valid.error.toUiText()))
                else fileAllUnknowns(UnknownDestination.NewClass(action.name))
            }
            is CaptureAction.OnDiscardUnknowns -> viewModelScope.launch {
                unknownImages.remove(datasetId, action.imageIds ?: _state.value.unknowns.map { it.id }.toSet()).onFailure { showError(it) }
            }
            CaptureAction.OnCancelTransfer -> transferJob?.cancel()
        }
    }

    private fun onDataset(dataset: Dataset) {
        this.dataset.value = dataset
        val owners = HashMap<Long, Int>()
        dataset.classes.forEach { c -> c.imageIds.forEach { owners[it] = c.id } }
        // Image ids only grow, so the highest are the newest.
        val fresh = newSince(seenImageIds, owners.keys).also { seenImageIds = it.seen }.ids.sortedDescending()
        val added = fresh.map { RecentCapture.Saved(it, owners.getValue(it)) }
        _state.update { state ->
            // Deleted images leave the strip, and moved ones take their new class's color.
            val kept = state.recent.mapNotNull { capture ->
                when (capture) {
                    is RecentCapture.Saved -> owners[capture.imageId]?.let { capture.copy(classId = it) }
                    is RecentCapture.Unknown -> capture
                }
            }
            val recent = (added + kept).take(MAX_RECENT)
            val classes = dataset.classes.mapIndexed { index, c -> c.toClassUi(index) }
            val target = when {
                // A new dataset has nothing for auto-sort to compare with, so start with its first class.
                state.loading && state.target == CaptureTarget.Auto && classes.isNotEmpty() && classes.all { it.imageIds.isEmpty() } ->
                    CaptureTarget.Class(classes.first().id)
                // The class being captured into was deleted.
                state.target is CaptureTarget.Class && classes.none { it.id == state.target.classId } -> CaptureTarget.Auto
                else -> state.target
            }
            state.copy(
                loading = false,
                name = dataset.name,
                classes = classes,
                target = target,
                recording = state.recording && target == state.target,
                recent = recent,
            )
        }
    }

    private class NewIds(val seen: MutableSet<Long>, val ids: List<Long>)

    /** Which of [ids] weren't in [seen]; the first time, when nothing has been seen, none count as new. */
    private fun newSince(seen: MutableSet<Long>?, ids: Collection<Long>): NewIds {
        if (seen == null) return NewIds(ids.toHashSet(), emptyList())
        val fresh = ids.filter { seen.add(it) }
        return NewIds(seen, fresh)
    }

    private fun addClass(name: String) {
        if (!_state.value.canEdit) return
        val valid = validateName(name, _state.value.classes.map { it.name })
        if (valid is Result.Error) return send(CaptureEvent.ShowMessage(valid.error.toUiText()))
        viewModelScope.launch {
            repository.addClass(datasetId, name.trim())
                .onSuccess { classId -> _state.update { it.copy(target = CaptureTarget.Class(classId), recording = false, shotPending = false) } }
                .onFailure { showError(it) }
        }
    }

    private fun onCapture(jpeg: ByteArray) {
        // Frames arrive on the camera's thread; claim a pending single shot atomically.
        val current = _state.getAndUpdate { if (it.shotPending) it.copy(shotPending = false) else it }
        val save = current.capturing && current.canCapture
        when (val target = current.target) {
            CaptureTarget.Auto -> if (save) autoCaptures.trySend(jpeg) else previewFrames.trySend(jpeg)
            is CaptureTarget.Class -> if (save) classCaptures.trySend(target.classId to jpeg)
        }
    }

    /**
     * Refits the matcher whenever the dataset changes, so new images and classes count straight away, or the
     * embedding model does. A new model analyses every image again, so auto-sort waits for it as it does at first.
     */
    private suspend fun followDataset() {
        var fittedModel: EmbeddingModel? = null
        combine(dataset.filterNotNull(), sorter.model) { current, model -> current to model }.conflate().collect { (current, model) ->
            if (fittedModel != null && model != fittedModel) {
                matcher = null
                updateAutoSort { it.copy(preparing = true, prepared = 0, toPrepare = 0) }
            }
            fittedModel = model
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
        // A preview from before auto-sort was last left is out of date.
        do {
            val stale = previewFrames.tryReceive()
        } while (stale.isSuccess)
        while (true) {
            // Frames to save come first; a preview frame only when none is waiting.
            val (jpeg, save) = select {
                autoCaptures.onReceive { it to true }
                previewFrames.onReceive { it to false }
            }
            val match = classify(jpeg, saving = save) ?: continue
            updateAutoSort { it.copy(prediction = match) }
            if (save) file(match, jpeg)
        }
    }

    /**
     * Where [image] belongs, or null if sorting isn't ready or it isn't a decodable image. [saving] says the
     * image will be saved, so its embedding is kept rather than worked out again once it's in the dataset.
     */
    private suspend fun classify(image: ByteArray, saving: Boolean): ClassMatch? {
        val fitted = matcher ?: return null
        return when (val embedded = sorter.embed(image, saving)) {
            is Result.Error -> {
                updateAutoSort { it.copy(unavailable = true) }
                null
            }
            // A different length means the model changed since the fit; the refit under way replaces it.
            is Result.Success -> embedded.data
                ?.takeIf { fitted.isEmpty || it.size == fitted.dimensions }
                ?.let { fitted.match(it, _state.value.strictness) }
        }
    }

    /** Saves a sorted image to its class, or holds it with the unknowns for the user to decide. */
    private suspend fun file(match: ClassMatch, image: ByteArray) {
        val classId = match.classId
        if (classId == null) holdUnknown(image) else save(classId, listOf(ImageSource { image }))
    }

    private suspend fun save(classId: Int, sources: List<ImageSource>) {
        repository.addImages(datasetId, classId, sources)
            .onSuccess { result -> countSaved(classId, result.added) }
            .onFailure { showError(it) }
    }

    /**
     * Keeps [image] with the unknowns, on disk so it outlasts the camera and the app. False if it wasn't kept:
     * it can't be decoded, saving failed, or [MAX_UNKNOWNS] are already waiting (which the user is told once).
     */
    private suspend fun holdUnknown(image: ByteArray): Boolean {
        if (_state.value.unknowns.size >= MAX_UNKNOWNS) {
            if (!warnedUnknownsFull) {
                warnedUnknownsFull = true
                _events.send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_unknowns_full, MAX_UNKNOWNS, MAX_UNKNOWNS)))
            }
            return false
        }
        val preview = sorter.preview(image) ?: return false
        return when (val added = unknownImages.add(datasetId, image, preview)) {
            is Result.Error -> {
                showError(added.error)
                false
            }
            is Result.Success -> true
        }
    }

    /** Sorts picked photos: matches are saved to their classes, the rest join the unknowns. */
    private fun sortPickedImages(images: List<ImageSource>) {
        if (matcher == null || !_state.value.autoSort.ready) return
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
                val match = bytes?.let { classify(it, saving = true) }
                val classId = match?.classId
                when {
                    match == null -> skipped++
                    classId != null -> byClass.getOrPut(classId) { mutableListOf() } += source
                    holdUnknown(bytes) -> unmatched++
                    else -> skipped++
                }
                onProgress(index + 1, images.size)
            }
            var sorted = 0
            for ((classId, sources) in byClass) {
                repository.addImages(datasetId, classId, sources)
                    .onSuccess { result ->
                        sorted += result.added
                        skipped += result.skipped
                        countSaved(classId, result.added)
                    }
                    .onFailure { showError(it) }
            }
            if (sorted > 0) send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_sorted, sorted, sorted)))
            if (unmatched > 0) send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_unmatched, unmatched, unmatched)))
            if (skipped > 0) send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_skipped, skipped, skipped)))
        }
    }

    private fun fileAllUnknowns(destination: UnknownDestination) {
        val unknowns = _state.value.unknowns
        if (unknowns.isEmpty()) return
        runTransfer(TransferKind.IMPORT) {
            fileUnknowns(datasetId, unknowns, destination, ::onProgress)
                .onSuccess { report(it) }
                .onFailure { showError(it) }
        }
    }

    private fun countSaved(classId: Int, count: Int) {
        if (count > 0) _state.update { it.copy(saved = it.saved + (classId to (it.saved[classId] ?: 0) + count)) }
    }

    private fun updateAutoSort(transform: (AutoSortStatus) -> AutoSortStatus) {
        _state.update { it.copy(autoSort = transform(it.autoSort)) }
    }

    private fun report(result: ImportResult) {
        if (result.added > 0) send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_added, result.added, result.added)))
        if (result.skipped > 0) send(CaptureEvent.ShowMessage(UiText.Plural(Res.plurals.message_images_skipped, result.skipped, result.skipped)))
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

    private suspend fun showError(error: DataError) = _events.send(CaptureEvent.ShowMessage(error.toUiText()))

    private fun send(event: CaptureEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private companion object {
        /** Unknown images kept at once, to bound the space they take; more aren't kept until there's room. */
        const val MAX_UNKNOWNS = 200

        /** Recent captures shown; older ones are still in their class or under Unknown. */
        const val MAX_RECENT = 60
    }
}
