package org.onedroid.dataset.presentation.datasets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.onedroid.dataset.core.presentation.ObserveAsEvents
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.components.NameDialog
import org.onedroid.dataset.presentation.components.ProgressBanner
import org.onedroid.dataset.presentation.components.label
import org.onedroid.dataset.presentation.theme.DataDroidTheme
import org.onedroid.dataset.platform.picker.rememberArchivePicker
import org.onedroid.dataset.platform.picker.rememberFileSharer
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_create
import org.onedroid.dataset.resources.action_delete
import org.onedroid.dataset.resources.action_export
import org.onedroid.dataset.resources.action_import_dataset
import org.onedroid.dataset.resources.action_more_options
import org.onedroid.dataset.resources.action_options_for
import org.onedroid.dataset.resources.action_rename
import org.onedroid.dataset.resources.app_name
import org.onedroid.dataset.resources.classes_count
import org.onedroid.dataset.resources.dataset_delete_message
import org.onedroid.dataset.resources.dataset_delete_title
import org.onedroid.dataset.resources.dataset_new
import org.onedroid.dataset.resources.dataset_new_empty
import org.onedroid.dataset.resources.dataset_rename
import org.onedroid.dataset.resources.datasets_empty_body
import org.onedroid.dataset.resources.datasets_empty_import
import org.onedroid.dataset.resources.datasets_empty_title
import org.onedroid.dataset.resources.datasets_heading
import org.onedroid.dataset.resources.images_count

private sealed interface DatasetDialog {
    data object Create : DatasetDialog
    data class Rename(val item: DatasetItemUi) : DatasetDialog
    data class Delete(val item: DatasetItemUi) : DatasetDialog
}

@Composable
fun DatasetListRoot(
    onOpenDataset: (String) -> Unit,
    viewModel: DatasetListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sharer = rememberFileSharer()
    val pickArchive = rememberArchivePicker { viewModel.onAction(DatasetListAction.OnImportArchive(it)) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DatasetListEvent.NavigateToDataset -> onOpenDataset(event.id)
            is DatasetListEvent.ShareFile -> sharer.share(event.file.path, event.file.mimeType)
            is DatasetListEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.message.asStringAsync()) }
        }
    }

    DatasetListScreen(state = state, onAction = viewModel::onAction, onPickArchive = pickArchive, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetListScreen(
    state: DatasetListState,
    onAction: (DatasetListAction) -> Unit,
    onPickArchive: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by remember { mutableStateOf<DatasetDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    when {
                        state.transfer != null -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        // The empty screen has its own import link.
                        state.datasets.isNotEmpty() -> LibraryMenu(onImport = onPickArchive)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (state.datasets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = DatasetDialog.Create },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.dataset_new)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.datasets.isEmpty() && state.transfer == null -> EmptyDatasets(
                    onCreate = { dialog = DatasetDialog.Create },
                    onImport = onPickArchive,
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.transfer?.let { transfer ->
                        item(key = "transfer") {
                            ProgressBanner(
                                text = transfer.label(),
                                fraction = transfer.fraction,
                                onCancel = { onAction(DatasetListAction.OnCancelTransfer) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    item(key = "heading") {
                        Text(
                            stringResource(Res.string.datasets_heading),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.animateItem().padding(bottom = 4.dp),
                        )
                    }
                    items(state.datasets, key = { it.id }) { item ->
                        DatasetCard(
                            item = item,
                            enabled = state.transfer == null,
                            modifier = Modifier.animateItem(),
                            onOpen = { onAction(DatasetListAction.OnOpenDataset(item.id)) },
                            onRename = { dialog = DatasetDialog.Rename(item) },
                            onExport = { onAction(DatasetListAction.OnExportDataset(item.id)) },
                            onDelete = { dialog = DatasetDialog.Delete(item) },
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        DatasetDialog.Create -> NameDialog(
            title = stringResource(Res.string.dataset_new),
            initial = "",
            confirmLabel = stringResource(Res.string.action_create),
            onConfirm = { name ->
                dialog = null
                onAction(DatasetListAction.OnCreateDataset(name))
            },
            onDismiss = { dialog = null },
        )
        is DatasetDialog.Rename -> NameDialog(
            title = stringResource(Res.string.dataset_rename),
            initial = current.item.name,
            confirmLabel = stringResource(Res.string.action_rename),
            onConfirm = { name ->
                dialog = null
                onAction(DatasetListAction.OnRenameDataset(current.item.id, name))
            },
            onDismiss = { dialog = null },
        )
        is DatasetDialog.Delete -> ConfirmDialog(
            title = stringResource(Res.string.dataset_delete_title, current.item.name),
            text = stringResource(
                Res.string.dataset_delete_message,
                pluralStringResource(Res.plurals.classes_count, current.item.classCount, current.item.classCount),
                pluralStringResource(Res.plurals.images_count, current.item.imageCount, current.item.imageCount),
            ),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = {
                dialog = null
                onAction(DatasetListAction.OnDeleteDataset(current.item.id))
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun LibraryMenu(onImport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(AppIcons.MoreVert, contentDescription = stringResource(Res.string.action_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_import_dataset)) },
                leadingIcon = { Icon(AppIcons.Upload, contentDescription = null) },
                onClick = { menuOpen = false; onImport() },
            )
        }
    }
}

@Composable
private fun DatasetCard(
    item: DatasetItemUi,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.coverImageId != null) {
                DatasetThumbnail(item.id, item.coverImageId, size = 56.dp)
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(Res.plurals.classes_count, item.classCount, item.classCount) + " · " +
                        pluralStringResource(Res.plurals.images_count, item.imageCount, item.imageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.MoreVert, contentDescription = stringResource(Res.string.action_options_for, item.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_rename)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_export)) },
                        leadingIcon = { Icon(AppIcons.Download, contentDescription = null) },
                        enabled = enabled && item.imageCount > 0,
                        onClick = { menuOpen = false; onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_delete)) },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                        enabled = enabled,
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDatasets(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            stringResource(Res.string.datasets_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(Res.string.datasets_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onCreate) {
            Icon(AppIcons.Add, contentDescription = null)
            Text(stringResource(Res.string.dataset_new_empty), modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.datasets_empty_import))
        }
    }
}

@Preview
@Composable
private fun DatasetListScreenPreview() {
    DataDroidTheme {
        DatasetListScreen(
            state = DatasetListState(
                loading = false,
                datasets = listOf(
                    DatasetItemUi("1", "Animal Dataset", classCount = 3, imageCount = 2438, coverImageId = null),
                    DatasetItemUi("2", "Plant diseases", classCount = 5, imageCount = 312, coverImageId = null),
                ),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun DatasetListEmptyPreview() {
    DataDroidTheme {
        DatasetListScreen(state = DatasetListState(loading = false), onAction = {})
    }
}
