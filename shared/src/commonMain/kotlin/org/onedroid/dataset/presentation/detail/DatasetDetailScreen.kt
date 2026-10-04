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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import org.onedroid.dataset.resources.dataset_export_menu
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
                if (index >= 0) listState.animateScrollToItem(index + if (currentState.transfer != null) 1 else 0)
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
                    DatasetMenu(
                        enabled = state.canEdit,
                        canExport = state.imageCount > 0,
                        onImportFolder = onPickDatasetFolder,
                        onImportZip = onPickDatasetZip,
                        onExport = { onAction(DatasetDetailAction.OnExport) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
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
                    listState = listState,
                    onAction = onAction,
                    onPickImages = onPickImages,
                    onPickClassFolder = onPickClassFolder,
                    onOpenImage = { openImage = it },
                    onViewAll = { galleryClassId = it },
                    onClearClass = { clearingClass = it },
                    onDeleteClass = { imageClass ->
                        // Deleting an empty class loses nothing, so it needs no confirmation.
                        if (imageClass.imageIds.isEmpty()) onAction(DatasetDetailAction.OnDeleteClass(imageClass.id))
                        else deletingClass = imageClass
                    },
                )
            }
        }
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
    listState: LazyListState,
    onAction: (DatasetDetailAction) -> Unit,
    onPickImages: (Int) -> Unit,
    onPickClassFolder: (Int) -> Unit,
    onOpenImage: (Long) -> Unit,
    onViewAll: (Int) -> Unit,
    onClearClass: (ClassUi) -> Unit,
    onDeleteClass: (ClassUi) -> Unit,
) {
    val banner: LazyListScope.() -> Unit = {
        state.transfer?.let { transfer ->
            item(key = "transfer") {
                ProgressBanner(
                    text = transfer.label(),
                    fraction = transfer.fraction,
                    onCancel = { onAction(DatasetDetailAction.OnCancelTransfer) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    val classes: LazyListScope.() -> Unit = {
        classItems(state, onAction, onPickImages, onPickClassFolder, onOpenImage, onViewAll, onClearClass, onDeleteClass)
    }
    val balancePanel = @Composable { ClassBalanceCard(state.balance) }
    val exportPanel = @Composable {
        ExportCard(
            datasetName = state.name,
            classes = state.classes,
            enabled = state.canEdit,
            onExport = { onAction(DatasetDetailAction.OnExport) },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        val spacing = Arrangement.spacedBy(16.dp)
        val contentPadding = PaddingValues(16.dp)
        if (maxWidth >= 840.dp) {
            // Classes on the left; balance and export beside them.
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1.3f).fillMaxHeight(), state = listState, contentPadding = contentPadding, verticalArrangement = spacing) {
                    banner()
                    classes()
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(contentPadding), verticalArrangement = spacing) {
                    balancePanel()
                    exportPanel()
                }
            }
        } else {
            LazyColumn(Modifier.widthIn(max = 672.dp).fillMaxSize(), state = listState, contentPadding = contentPadding, verticalArrangement = spacing) {
                banner()
                classes()
                item(key = "balance") { Box(Modifier.animateItem()) { balancePanel() } }
                item(key = "export") { Box(Modifier.animateItem()) { exportPanel() } }
            }
        }
    }
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
    item(key = "add-class") {
        OutlinedButton(
            onClick = { onAction(DatasetDetailAction.OnAddClass) },
            enabled = editable,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.animateItem().fillMaxWidth().height(72.dp),
        ) {
            Icon(AppIcons.Add, contentDescription = null)
            Text(stringResource(Res.string.class_add), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DatasetMenu(
    enabled: Boolean,
    canExport: Boolean,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
    onExport: () -> Unit,
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
                text = { Text(stringResource(Res.string.dataset_export_menu)) },
                leadingIcon = { Icon(AppIcons.Download, contentDescription = null) },
                enabled = enabled && canExport,
                onClick = { menuOpen = false; onExport() },
            )
        }
    }
}

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
