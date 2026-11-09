package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.onedroid.dataset.core.platform.picker.rememberArchivePicker
import org.onedroid.dataset.core.platform.picker.rememberFileSharer
import org.onedroid.dataset.core.platform.picker.rememberFolderPicker
import org.onedroid.dataset.core.platform.picker.rememberImagePicker
import org.onedroid.dataset.domain.model.DatasetClass
import org.onedroid.dataset.domain.usecase.computeClassBalance
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.components.ImportPreviewDialog
import org.onedroid.dataset.presentation.components.NameDialog
import org.onedroid.dataset.presentation.components.ObserveAsEvents
import org.onedroid.dataset.presentation.components.ProgressBanner
import org.onedroid.dataset.presentation.components.UnknownImagesCard
import org.onedroid.dataset.presentation.components.UnknownImagesSheet
import org.onedroid.dataset.presentation.components.label
import org.onedroid.dataset.presentation.theme.DataDroidTheme
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_back
import org.onedroid.dataset.resources.action_capture
import org.onedroid.dataset.resources.action_delete
import org.onedroid.dataset.resources.action_export
import org.onedroid.dataset.resources.action_more_options
import org.onedroid.dataset.resources.action_remove
import org.onedroid.dataset.resources.action_rename
import org.onedroid.dataset.resources.action_undo
import org.onedroid.dataset.resources.balance_title
import org.onedroid.dataset.resources.class_add
import org.onedroid.dataset.resources.class_clear_message
import org.onedroid.dataset.resources.class_clear_title
import org.onedroid.dataset.resources.class_delete_message
import org.onedroid.dataset.resources.class_delete_title
import org.onedroid.dataset.resources.dataset_image_settings
import org.onedroid.dataset.resources.dataset_import_folder
import org.onedroid.dataset.resources.dataset_import_zip
import org.onedroid.dataset.resources.dataset_loading
import org.onedroid.dataset.resources.dataset_rename
import org.onedroid.dataset.resources.export_title
import org.onedroid.dataset.resources.images_count

/** Which picker result is for: a class, or the whole dataset. */
private sealed interface PickTarget {
    data class Class(val classId: Int) : PickTarget
    data object Dataset : PickTarget

    companion object {
        /** Saved as the class id, or -1 for the whole dataset. */
        val Saver: Saver<PickTarget, Int> = Saver(
            save = { (it as? Class)?.classId ?: -1 },
            restore = { if (it >= 0) Class(it) else Dataset },
        )
    }
}

@Composable
fun DatasetDetailRoot(
    datasetId: String,
    onBack: () -> Unit,
    onOpenCapture: (classId: Int?) -> Unit,
    viewModel: DatasetDetailViewModel = koinViewModel { parametersOf(datasetId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sharer = rememberFileSharer()
    val currentState by rememberUpdatedState(state)

    // One launcher per picker for the whole screen, so it survives while the system picker is open.
    // Saved, as the system picker can outlive this activity (rotation, or the process being reclaimed).
    var pickTarget by rememberSaveable(stateSaver = PickTarget.Saver) { mutableStateOf<PickTarget>(PickTarget.Dataset) }
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
            is DatasetDetailEvent.ShowUndo -> scope.launch {
                var undone = false
                try {
                    val result = snackbar.showSnackbar(
                        message = event.message.asStringAsync(),
                        actionLabel = getString(Res.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    undone = result == SnackbarResult.ActionPerformed
                } finally {
                    // Also when the offer is cut short, e.g. by rotation, so a delete never stays pending.
                    viewModel.onAction(
                        if (undone) DatasetDetailAction.OnUndoDelete(event.imageIds) else DatasetDetailAction.OnCommitDelete(event.imageIds)
                    )
                }
            }
            is DatasetDetailEvent.FocusClass -> scope.launch {
                // Let the new class reach the list before scrolling to it.
                withFrameNanos { }
                val index = currentState.classes.indexOfFirst { it.id == event.classId }
                if (index >= 0) listState.animateScrollToItem(index + FIRST_CLASS_INDEX)
            }
        }
    }

    DatasetDetailScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenCapture = onOpenCapture,
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

/**
 * A dataset at a glance: how ready each class is, its newest images, and the two things to do next, capture
 * more or export, always in reach at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetDetailScreen(
    state: DatasetDetailState,
    onAction: (DatasetDetailAction) -> Unit,
    onOpenCapture: (classId: Int?) -> Unit = {},
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
    var viewing by remember { mutableStateOf<ViewerTarget?>(null) }
    val openImage: (Long) -> Unit = { imageId ->
        state.classes.firstOrNull { imageId in it.imageIds }?.let { viewing = ViewerTarget(it.id, imageId) }
    }
    var galleryClassId by remember { mutableStateOf<Int?>(null) }
    var editingImageSettings by remember { mutableStateOf(false) }
    var confirmingConversion by remember { mutableStateOf(false) }
    var showingBalance by remember { mutableStateOf(false) }
    var showingExport by remember { mutableStateOf(false) }
    var showingUnknowns by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollToClass = { classId: Int ->
        val index = state.classes.indexOfFirst { it.id == classId }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index + FIRST_CLASS_INDEX) }
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
                        DatasetMenu(
                            enabled = state.canEdit,
                            onRename = { renaming = true },
                            onImportFolder = onPickDatasetFolder,
                            onImportZip = onPickDatasetZip,
                            onImageSettings = { editingImageSettings = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!state.loading) {
                    ActionBar(
                        canExport = state.imageCount > 0 && state.canEdit,
                        canCapture = state.canEdit,
                        onExport = { showingExport = true },
                        onCapture = { onOpenCapture(null) },
                    )
                }
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
                        onOpenCapture = onOpenCapture,
                        onPickImages = onPickImages,
                        onPickClassFolder = onPickClassFolder,
                        onOpenImage = openImage,
                        onViewAll = { galleryClassId = it },
                        onClearClass = { clearingClass = it },
                        onConvertImages = { confirmingConversion = true },
                        onDeleteClass = { imageClass ->
                            // Deleting an empty class loses nothing, so it needs no confirmation.
                            if (imageClass.imageIds.isEmpty()) onAction(DatasetDetailAction.OnDeleteClass(imageClass.id))
                            else deletingClass = imageClass
                        },
                        onShowBalance = { showingBalance = true },
                        onShowUnknowns = { showingUnknowns = true },
                        onClassClick = { scrollToClass(it) },
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
                unknownCount = state.unknowns.size,
                enabled = state.canEdit,
                onExport = {
                    showingExport = false
                    onAction(DatasetDetailAction.OnExport)
                },
            )
        }
    }

    if (showingUnknowns) {
        UnknownImagesSheet(
            unknowns = state.unknowns,
            classes = state.classes,
            enabled = state.canEdit,
            onFileToClass = { onAction(DatasetDetailAction.OnUnknownsToClass(it)) },
            onFileToNewClass = { onAction(DatasetDetailAction.OnUnknownsToNewClass(it)) },
            onDiscard = { onAction(DatasetDetailAction.OnDiscardUnknowns(it)) },
            onDismiss = { showingUnknowns = false },
        )
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
                snackbar = snackbar,
                onOpenImage = openImage,
                onMove = { ids, target -> onAction(DatasetDetailAction.OnMoveImages(ids, target)) },
                onDelete = { onAction(DatasetDetailAction.OnDeleteImages(it)) },
                onDismiss = { galleryClassId = null },
            )
        }
    }

    // After the gallery, so an image opened from it shows on top. It follows the class rather than the image,
    // so deleting or moving the one showing moves on to the next.
    viewing?.let { target ->
        val owner = state.classes.firstOrNull { it.id == target.classId }
        if (owner == null) {
            SideEffect { viewing = null }
        } else {
            ImageViewer(
                datasetId = state.datasetId,
                imageClass = owner,
                initialImageId = target.imageId,
                otherClasses = state.classes - owner,
                enabled = state.canEdit,
                snackbar = snackbar,
                onMove = { id, target -> onAction(DatasetDetailAction.OnMoveImages(setOf(id), target)) },
                onDelete = { onAction(DatasetDetailAction.OnDeleteImages(setOf(it))) },
                onDismiss = { viewing = null },
            )
        }
    }
}

/** The image the viewer opened on, and the class it pages through. */
private data class ViewerTarget(val classId: Int, val imageId: Long)

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
    onOpenCapture: (Int?) -> Unit,
    onPickImages: (Int) -> Unit,
    onPickClassFolder: (Int) -> Unit,
    onOpenImage: (Long) -> Unit,
    onViewAll: (Int) -> Unit,
    onClearClass: (ClassUi) -> Unit,
    onDeleteClass: (ClassUi) -> Unit,
    onConvertImages: () -> Unit,
    onShowBalance: () -> Unit,
    onShowUnknowns: () -> Unit,
    onClassClick: (Int) -> Unit,
) {
    val spacing = Arrangement.spacedBy(16.dp)
    val listPadding = PaddingValues(16.dp)
    val items: LazyListScope.() -> Unit = {
        // One item for everything above the classes. A banner that arrives later (unknown images load after
        // the classes) then grows this item instead of being inserted above it, where LazyColumn would keep
        // the item below it anchored and leave the banner scrolled out of view.
        item(key = "header") {
            Column(Modifier.animateContentSize(), verticalArrangement = spacing) {
                val transfer = state.transfer
                val conversion = state.conversion
                when {
                    transfer != null -> ProgressBanner(
                        text = transfer.label(),
                        fraction = transfer.fraction,
                        onCancel = { onAction(DatasetDetailAction.OnCancelTransfer) },
                    )
                    conversion != null -> ProgressBanner(
                        text = conversion.label(),
                        fraction = conversion.fraction,
                        onCancel = { onAction(DatasetDetailAction.OnCancelConversion) },
                    )
                    state.offersConversion -> OutdatedImagesCard(
                        count = state.outdatedImageCount,
                        enabled = state.canEdit,
                        onConvert = onConvertImages,
                    )
                }
                if (state.unknowns.isNotEmpty()) UnknownImagesCard(state.unknowns, onClick = onShowUnknowns)
                AddClassCard(enabled = state.canEdit, onClick = { onAction(DatasetDetailAction.OnAddClass) })
            }
        }
        // Oldest first, so classes read top to bottom in the order they were made.
        items(state.classes, key = { it.id }) { imageClass ->
            ClassCard(
                datasetId = state.datasetId,
                imageClass = imageClass,
                enabled = state.canEdit,
                onRename = { onAction(DatasetDetailAction.OnRenameClass(imageClass.id, it)) },
                onCapture = { onOpenCapture(imageClass.id) },
                onUpload = { onPickImages(imageClass.id) },
                onAddFolder = { onPickClassFolder(imageClass.id) },
                onViewAll = { onViewAll(imageClass.id) },
                onClear = { onClearClass(imageClass) },
                onDelete = { onDeleteClass(imageClass) },
                onOpenImage = onOpenImage,
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (wide) {
        // Classes on the left; balance and export always visible beside them.
        Row(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1.3f).fillMaxHeight(), state = listState, contentPadding = listPadding, verticalArrangement = spacing) {
                items()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = spacing) {
                ClassBalanceCard(state.balance, onClassClick = onClassClick)
                ExportCard(
                    datasetName = state.name,
                    classes = state.classes,
                    unknownCount = state.unknowns.size,
                    enabled = state.canEdit,
                    onExport = { onAction(DatasetDetailAction.OnExport) },
                )
            }
        }
    } else {
        // Balance lives in a sheet, so the summary stays in reach however many classes there are.
        Column(Modifier.fillMaxSize().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            BalanceSummaryStrip(state.balance, onClick = onShowBalance)
            LazyColumn(Modifier.widthIn(max = 672.dp).fillMaxWidth().weight(1f), state = listState, contentPadding = listPadding, verticalArrangement = spacing) {
                items()
            }
        }
    }
}

/** The screen's two main actions, side by side above the navigation bar. */
@Composable
private fun ActionBar(canExport: Boolean, canCapture: Boolean, onExport: () -> Unit, onCapture: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExport, enabled = canExport, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.action_export), modifier = Modifier.padding(start = 8.dp))
                }
                Button(onClick = onCapture, enabled = canCapture, modifier = Modifier.weight(1.4f).height(52.dp)) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.action_capture), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Heads the class list, so adding a class is in reach without scrolling past every class. */
@Composable
private fun AddClassCard(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(Res.string.class_add),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun DatasetMenu(
    enabled: Boolean,
    onRename: () -> Unit,
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
                text = { Text(stringResource(Res.string.dataset_rename)) },
                leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename() },
            )
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

/** List index of the first class, after the header item. */
private const val FIRST_CLASS_INDEX = 1

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
        DatasetClass(2, "dog", (43L..56L).toList()),
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
