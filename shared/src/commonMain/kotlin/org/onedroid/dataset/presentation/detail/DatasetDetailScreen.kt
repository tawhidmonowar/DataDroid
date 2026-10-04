package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.onedroid.dataset.core.presentation.ObserveAsEvents
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.platform.picker.rememberArchivePicker
import org.onedroid.dataset.platform.picker.rememberFileSharer
import org.onedroid.dataset.platform.picker.rememberFolderPicker
import org.onedroid.dataset.platform.picker.rememberImagePicker
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.components.ImportPreviewDialog
import org.onedroid.dataset.presentation.components.NameDialog
import org.onedroid.dataset.presentation.components.ProgressBanner
import org.onedroid.dataset.presentation.components.label
import org.onedroid.dataset.presentation.theme.DataDroidTheme
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_back
import org.onedroid.dataset.resources.action_delete
import org.onedroid.dataset.resources.action_more_options
import org.onedroid.dataset.resources.action_remove
import org.onedroid.dataset.resources.action_rename
import org.onedroid.dataset.resources.class_add
import org.onedroid.dataset.resources.class_clear_message
import org.onedroid.dataset.resources.class_clear_title
import org.onedroid.dataset.resources.class_delete_message
import org.onedroid.dataset.resources.class_delete_title
import org.onedroid.dataset.resources.export_title
import org.onedroid.dataset.resources.balance_title
import org.onedroid.dataset.resources.dataset_image_settings
import org.onedroid.dataset.resources.dataset_import_folder
import org.onedroid.dataset.resources.dataset_import_zip
import org.onedroid.dataset.resources.dataset_loading
import org.onedroid.dataset.resources.dataset_rename
import org.onedroid.dataset.resources.images_count

/** What the next picked images, or picked folder, are for. */
private sealed interface PickTarget {
    data class Class(val classId: Int) : PickTarget
    data object Dataset : PickTarget
}

@Composable
fun DatasetDetailRoot(
    datasetId: String,
    onBack: () -> Unit,
    viewModel: DatasetDetailViewModel = koinViewModel { parametersOf(datasetId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sharer = rememberFileSharer()
    val currentState by rememberUpdatedState(state)

    // One launcher per picker for the whole screen, so it survives while the system picker is open.
    var pickTarget by remember { mutableStateOf<PickTarget>(PickTarget.Dataset) }
    val pickImages = rememberImagePicker { images ->
        (pickTarget as? PickTarget.Class)?.let { viewModel.onAction(DatasetDetailAction.OnImagesPicked(it.classId, images)) }
    }
    val pickFolder = rememberFolderPicker { folder ->
        when (val target = pickTarget) {
            is PickTarget.Class -> viewModel.onAction(DatasetDetailAction.OnClassFolderPicked(target.classId, folder))
            PickTarget.Dataset -> viewModel.onAction(DatasetDetailAction.OnDatasetFolderPicked(folder))
        }
    }
    val pickZip = rememberArchivePicker { viewModel.onAction(DatasetDetailAction.OnDatasetZipPicked(it)) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DatasetDetailEvent.NavigateBack -> onBack()
            is DatasetDetailEvent.ShareFile -> sharer.share(event.file.path, event.file.mimeType)
            is DatasetDetailEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.message.asStringAsync()) }
            is DatasetDetailEvent.FocusClass -> scope.launch {
                // Let the new class reach the list before scrolling to it.
                withFrameNanos { }
                val index = currentState.classes.indexOfFirst { it.id == event.classId }
                if (index >= 0) listState.animateScrollToItem(index + if (currentState.showsBanner) 1 else 0)
            }
        }
    }

    DatasetDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onPickImages = { classId ->
            pickTarget = PickTarget.Class(classId)
            pickImages()
        },
        onPickClassFolder = { classId ->
            pickTarget = PickTarget.Class(classId)
            pickFolder()
        },
        onPickDatasetFolder = {
            pickTarget = PickTarget.Dataset
            pickFolder()
        },
        onPickDatasetZip = pickZip,
        listState = listState,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetDetailScreen(
    state: DatasetDetailState,
    onAction: (DatasetDetailAction) -> Unit,
    onPickImages: (classId: Int) -> Unit = {},
    onPickClassFolder: (classId: Int) -> Unit = {},
    onPickDatasetFolder: () -> Unit = {},
    onPickDatasetZip: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var renaming by remember { mutableStateOf(false) }
    var deletingClass by remember { mutableStateOf<ClassUi?>(null) }
    var clearingClass by remember { mutableStateOf<ClassUi?>(null) }
    var openImage by remember { mutableStateOf<Long?>(null) }
    var galleryClassId by remember { mutableStateOf<Int?>(null) }
    var editingImageSettings by remember { mutableStateOf(false) }
    var confirmingConversion by remember { mutableStateOf(false) }
    var showingBalance by remember { mutableStateOf(false) }
    var showingExport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Shrinks to its icon while scrolling down the classes, and grows back when scrolling up.
    val fabExpanded by remember { derivedStateOf { !listState.canScrollBackward || listState.lastScrolledBackward } }
    // Hidden while the camera is open, where it would cover the record button.
    val showFab = !state.loading && state.canEdit && state.cameraClassId == null
    val scrollToClass = { classId: Int ->
        val index = state.classes.indexOfFirst { it.id == classId }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index + if (state.showsBanner) 1 else 0) }
    }

    BoxWithConstraints {
        // Wide windows show balance and export in a side column; phones keep them in sheets.
        val wide = maxWidth >= WIDE_LAYOUT_WIDTH
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onAction(DatasetDetailAction.OnBack) }) {
                            Icon(AppIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                    },
                    title = { Text(state.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = { renaming = true }, enabled = !state.loading) {
                            Icon(AppIcons.Edit, contentDescription = stringResource(Res.string.dataset_rename))
                        }
                        if (!wide) {
                            IconButton(onClick = { showingExport = true }, enabled = !state.loading && state.imageCount > 0) {
                                Icon(AppIcons.Download, contentDescription = stringResource(Res.string.export_title))
                            }
                        }
                        DatasetMenu(
                            enabled = state.canEdit,
                            onImportFolder = onPickDatasetFolder,
                            onImportZip = onPickDatasetZip,
                            onImageSettings = { editingImageSettings = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            floatingActionButton = {
                if (!wide && showFab) AddClassButton(expanded = fabExpanded, onClick = { onAction(DatasetDetailAction.OnAddClass) })
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Crossfade(state.loading, Modifier.padding(padding).fillMaxSize(), label = "dataset") { loading ->
                if (loading) {
                    LoadingState(Modifier.fillMaxSize())
                } else {
                    DatasetContent(
                        state = state,
                        wide = wide,
                        listState = listState,
                        onAction = onAction,
                        onPickImages = onPickImages,
                        onPickClassFolder = onPickClassFolder,
                        onOpenImage = { openImage = it },
                        onViewAll = { galleryClassId = it },
                        onClearClass = { clearingClass = it },
                        onConvertImages = { confirmingConversion = true },
                        onDeleteClass = { imageClass ->
                            // Deleting an empty class loses nothing, so it needs no confirmation.
                            if (imageClass.imageIds.isEmpty()) onAction(DatasetDetailAction.OnDeleteClass(imageClass.id))
                            else deletingClass = imageClass
                        },
                        onShowBalance = { showingBalance = true },
                        onClassClick = { scrollToClass(it) },
                        addClassButton = {
                            if (showFab) AddClassButton(expanded = fabExpanded, onClick = { onAction(DatasetDetailAction.OnAddClass) })
                        },
                    )
                }
            }
        }
    }

    if (showingBalance) {
        InsightSheet(AppIcons.BarChart, stringResource(Res.string.balance_title), onDismiss = { showingBalance = false }) {
            ClassBalanceContent(
                balance = state.balance,
                onClassClick = { classId ->
                    showingBalance = false
                    scrollToClass(classId)
                },
            )
        }
    }

    if (showingExport) {
        InsightSheet(AppIcons.Download, stringResource(Res.string.export_title), onDismiss = { showingExport = false }) {
            ExportContent(
                datasetName = state.name,
                classes = state.classes,
                enabled = state.canEdit,
                onExport = {
                    showingExport = false
                    onAction(DatasetDetailAction.OnExport)
                },
            )
        }
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onNamingChange = { onAction(DatasetDetailAction.OnImportNamingChange(it)) },
            onMergeSplitsChange = { onAction(DatasetDetailAction.OnImportMergeSplitsChange(it)) },
            onConfirm = { onAction(DatasetDetailAction.OnConfirmImport) },
            onDismiss = { onAction(DatasetDetailAction.OnDismissImport) },
        )
    }

    if (editingImageSettings) {
        ImageSettingsSheet(
            current = state.imageSettings,
            defaults = state.defaultImageSettings,
            imageCount = state.imageCount,
            onSave = { settings, convert ->
                editingImageSettings = false
                onAction(DatasetDetailAction.OnSaveImageSettings(settings, convert))
            },
            onDismiss = { editingImageSettings = false },
        )
    }

    if (confirmingConversion) {
        ConvertConfirmDialog(
            count = state.outdatedImageCount,
            settings = state.imageSettings,
            onConfirm = {
                confirmingConversion = false
                onAction(DatasetDetailAction.OnConvertImages)
            },
            onDismiss = { confirmingConversion = false },
        )
    }

    if (renaming) {
        NameDialog(
            title = stringResource(Res.string.dataset_rename),
            initial = state.name,
            confirmLabel = stringResource(Res.string.action_rename),
            onConfirm = { name ->
                renaming = false
                onAction(DatasetDetailAction.OnRenameDataset(name))
            },
            onDismiss = { renaming = false },
        )
    }

    deletingClass?.let { imageClass ->
        ConfirmDialog(
            title = stringResource(Res.string.class_delete_title, imageClass.name),
            text = stringResource(
                Res.string.class_delete_message,
                pluralStringResource(Res.plurals.images_count, imageClass.imageIds.size, imageClass.imageIds.size),
            ),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = {
                deletingClass = null
                onAction(DatasetDetailAction.OnDeleteClass(imageClass.id))
            },
            onDismiss = { deletingClass = null },
        )
    }

    clearingClass?.let { imageClass ->
        ConfirmDialog(
            title = stringResource(Res.string.class_clear_title, imageClass.name),
            text = stringResource(
                Res.string.class_clear_message,
                pluralStringResource(Res.plurals.images_count, imageClass.imageIds.size, imageClass.imageIds.size),
            ),
            confirmLabel = stringResource(Res.string.action_remove),
            onConfirm = {
                clearingClass = null
                onAction(DatasetDetailAction.OnClearClass(imageClass.id))
            },
            onDismiss = { clearingClass = null },
        )
    }

    galleryClassId?.let { classId ->
        val imageClass = state.classes.firstOrNull { it.id == classId }
        if (imageClass == null || imageClass.imageIds.isEmpty()) {
            SideEffect { galleryClassId = null }
        } else {
            ClassGalleryDialog(
                datasetId = state.datasetId,
                imageClass = imageClass,
                otherClasses = state.classes - imageClass,
                enabled = state.canEdit,
                onOpenImage = { openImage = it },
                onMove = { ids, target -> onAction(DatasetDetailAction.OnMoveImages(ids, target)) },
                onDelete = { onAction(DatasetDetailAction.OnDeleteImages(it)) },
                onDismiss = { galleryClassId = null },
            )
        }
    }

    // After the gallery, so an image opened from it shows on top.
    openImage?.let { imageId ->
        val owner = state.classes.firstOrNull { imageId in it.imageIds }
        if (owner == null) {
            SideEffect { openImage = null }
        } else {
            ImageDialog(
                datasetId = state.datasetId,
                imageId = imageId,
                className = owner.name,
                otherClasses = state.classes - owner,
                enabled = state.canEdit,
                onMove = { target ->
                    openImage = null
                    onAction(DatasetDetailAction.OnMoveImages(setOf(imageId), target))
                },
                onDelete = {
                    openImage = null
                    onAction(DatasetDetailAction.OnDeleteImages(setOf(imageId)))
                },
                onDismiss = { openImage = null },
            )
        }
    }
}

/**
 * Lazy, so only the class cards on screen are composed; datasets with many classes would otherwise lay
 * out every card and its thumbnail strip on each frame.
 */
@Composable
private fun DatasetContent(
    state: DatasetDetailState,
    wide: Boolean,
    listState: LazyListState,
    onAction: (DatasetDetailAction) -> Unit,
    onPickImages: (Int) -> Unit,
    onPickClassFolder: (Int) -> Unit,
    onOpenImage: (Long) -> Unit,
    onViewAll: (Int) -> Unit,
    onClearClass: (ClassUi) -> Unit,
    onDeleteClass: (ClassUi) -> Unit,
    onConvertImages: () -> Unit,
    onShowBalance: () -> Unit,
    onClassClick: (Int) -> Unit,
    addClassButton: @Composable () -> Unit,
) {
    val banner: LazyListScope.() -> Unit = {
        val transfer = state.transfer
        val conversion = state.conversion
        when {
            transfer != null -> item(key = "transfer") {
                ProgressBanner(
                    text = transfer.label(),
                    fraction = transfer.fraction,
                    onCancel = { onAction(DatasetDetailAction.OnCancelTransfer) },
                    modifier = Modifier.animateItem(),
                )
            }
            conversion != null -> item(key = "conversion") {
                ProgressBanner(
                    text = conversion.label(),
                    fraction = conversion.fraction,
                    onCancel = { onAction(DatasetDetailAction.OnCancelConversion) },
                    modifier = Modifier.animateItem(),
                )
            }
            state.offersConversion -> item(key = "outdated") {
                OutdatedImagesCard(
                    count = state.outdatedImageCount,
                    enabled = state.canEdit,
                    onConvert = onConvertImages,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    val classes: LazyListScope.() -> Unit = {
        classItems(state, onAction, onPickImages, onPickClassFolder, onOpenImage, onViewAll, onClearClass, onDeleteClass)
    }
    val spacing = Arrangement.spacedBy(16.dp)
    // Room at the end so the last class can scroll clear of the add-class button.
    val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)

    if (wide) {
        // Classes on the left; balance and export always visible beside them.
        Row(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1.3f).fillMaxHeight()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding, verticalArrangement = spacing) {
                    banner()
                    classes()
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { addClassButton() }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = spacing) {
                ClassBalanceCard(state.balance, onClassClick = onClassClick)
                ExportCard(
                    datasetName = state.name,
                    classes = state.classes,
                    enabled = state.canEdit,
                    onExport = { onAction(DatasetDetailAction.OnExport) },
                )
            }
        }
    } else {
        // Balance and export live in sheets, so the summary stays in reach however many classes there are.
        Column(Modifier.fillMaxSize().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            BalanceSummaryStrip(state.balance, onClick = onShowBalance)
            LazyColumn(Modifier.widthIn(max = 672.dp).fillMaxWidth().weight(1f), state = listState, contentPadding = listPadding, verticalArrangement = spacing) {
                banner()
                classes()
            }
        }
    }
}

/** Adds a class; shown floating over the class list so it's in reach however long the list is. */
@Composable
private fun AddClassButton(expanded: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(AppIcons.Add, contentDescription = null) },
        text = { Text(stringResource(Res.string.class_add)) },
    )
}

private fun LazyListScope.classItems(
    state: DatasetDetailState,
    onAction: (DatasetDetailAction) -> Unit,
    onPickImages: (Int) -> Unit,
    onPickClassFolder: (Int) -> Unit,
    onOpenImage: (Long) -> Unit,
    onViewAll: (Int) -> Unit,
    onClearClass: (ClassUi) -> Unit,
    onDeleteClass: (ClassUi) -> Unit,
) {
    val editable = state.canEdit
    items(state.classes, key = { it.id }) { imageClass ->
        val capturing = state.cameraClassId == imageClass.id
        ClassCard(
            datasetId = state.datasetId,
            imageClass = imageClass,
            enabled = editable,
            onRename = { onAction(DatasetDetailAction.OnRenameClass(imageClass.id, it)) },
            onCamera = { onAction(DatasetDetailAction.OnOpenCamera(imageClass.id)) },
            onUpload = { onPickImages(imageClass.id) },
            onAddFolder = { onPickClassFolder(imageClass.id) },
            onViewAll = { onViewAll(imageClass.id) },
            onClear = { onClearClass(imageClass) },
            onDelete = { onDeleteClass(imageClass) },
            onOpenImage = onOpenImage,
            modifier = Modifier.animateItem(),
            camera = if (!capturing) null else {
                {
                    CameraPanel(
                        facing = state.cameraFacing,
                        // Frames are only converted while recording.
                        onCapture = if (state.recording) { jpeg -> onAction(DatasetDetailAction.OnCapture(jpeg)) } else null,
                        onFlip = { onAction(DatasetDetailAction.OnFlipCamera) },
                        onClose = { onAction(DatasetDetailAction.OnCloseCamera) },
                    ) {
                        HoldToRecordButton(
                            recording = state.recording,
                            enabled = editable,
                            onStart = { onAction(DatasetDetailAction.OnStartRecording) },
                            onStop = { onAction(DatasetDetailAction.OnStopRecording) },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun DatasetMenu(
    enabled: Boolean,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onImageSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(AppIcons.MoreVert, contentDescription = stringResource(Res.string.action_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.dataset_import_folder)) },
                leadingIcon = { Icon(AppIcons.Folder, contentDescription = null) },
                enabled = enabled,
                onClick = { menuOpen = false; onImportFolder() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.dataset_import_zip)) },
                leadingIcon = { Icon(AppIcons.Upload, contentDescription = null) },
                enabled = enabled,
                onClick = { menuOpen = false; onImportZip() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.dataset_image_settings)) },
                leadingIcon = { Icon(AppIcons.Image, contentDescription = null) },
                enabled = enabled,
                onClick = { menuOpen = false; onImageSettings() },
            )
        }
    }
}

private val WIDE_LAYOUT_WIDTH = 840.dp

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.dataset_loading), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
    }
}

@Preview
@Composable
private fun DatasetDetailScreenPreview() {
    val classes = listOf(
        DatasetClass(1, "cat", (1L..42L).toList()),
        DatasetClass(2, "dog", (43L..70L).toList()),
        DatasetClass(3, "bird", emptyList()),
    )
    DataDroidTheme {
        DatasetDetailScreen(
            state = DatasetDetailState(
                datasetId = "preview",
                loading = false,
                name = "Animal Dataset",
                classes = classes.mapIndexed { index, c -> c.toClassUi(index) },
                balance = computeClassBalance(classes),
            ),
            onAction = {},
        )
    }
}
