package org.onedroid.dataset.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.onedroid.dataset.presentation.components.ObserveAsEvents
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.theme.DataDroidTheme
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_back
import org.onedroid.dataset.resources.action_convert
import org.onedroid.dataset.resources.action_discard
import org.onedroid.dataset.resources.action_not_now
import org.onedroid.dataset.resources.action_save
import org.onedroid.dataset.resources.apply_existing_message
import org.onedroid.dataset.resources.apply_existing_title
import org.onedroid.dataset.resources.images_count
import org.onedroid.dataset.resources.settings_discard_message
import org.onedroid.dataset.resources.settings_discard_title
import org.onedroid.dataset.resources.settings_image_body
import org.onedroid.dataset.resources.settings_image_heading
import org.onedroid.dataset.resources.settings_title

@Composable
fun SettingsRoot(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.message.asStringAsync()) }
        }
    }

    SettingsScreen(state = state, onAction = viewModel::onAction, onBack = onBack, snackbar = snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmingDiscard by remember { mutableStateOf(false) }
    val leave = { if (state.hasChanges) confirmingDiscard = true else onBack() }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.hasChanges) {
        confirmingDiscard = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                title = { Text(stringResource(Res.string.settings_title), fontWeight = FontWeight.Medium) },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { onAction(SettingsAction.OnSave) }, enabled = state.hasChanges) {
                            Text(stringResource(Res.string.action_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(stringResource(Res.string.settings_image_heading), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.settings_image_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        ImageSettingsForm(
                            settings = state.draft,
                            onChange = { onAction(SettingsAction.OnChange(it)) },
                            enabled = !state.saving,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmingDiscard) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_discard_title),
            text = stringResource(Res.string.settings_discard_message),
            confirmLabel = stringResource(Res.string.action_discard),
            onConfirm = {
                confirmingDiscard = false
                onBack()
            },
            onDismiss = { confirmingDiscard = false },
        )
    }

    state.applyChoices?.let { choices ->
        ApplyToDatasetsDialog(
            choices = choices.filterNot { it.matches },
            onApply = { onAction(SettingsAction.OnApplyToDatasets(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissApply) },
        )
    }
}

/** Lets the user pick datasets to convert to the just-saved settings; none are picked to start with. */
@Composable
private fun ApplyToDatasetsDialog(
    choices: List<DatasetChoiceUi>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.apply_existing_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.apply_existing_message), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.padding(top = 12.dp).heightIn(max = 280.dp)) {
                    items(choices, key = { it.id }) { choice ->
                        val checked = choice.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (checked) selected - choice.id else selected + choice.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(choice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(Res.plurals.images_count, choice.imageCount, choice.imageCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selected) }, enabled = selected.isNotEmpty()) {
                Text(
                    stringResource(Res.string.action_convert),
                    color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_not_now)) } },
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    DataDroidTheme {
        SettingsScreen(state = SettingsState(loading = false), onAction = {}, onBack = {})
    }
}
