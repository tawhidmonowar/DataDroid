package org.onedroid.dataset.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.settings.ImageSettingsForm
import org.onedroid.dataset.presentation.settings.summary
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_convert
import org.onedroid.dataset.resources.action_save
import org.onedroid.dataset.resources.convert_confirm_message
import org.onedroid.dataset.resources.convert_confirm_title
import org.onedroid.dataset.resources.dataset_convert_existing
import org.onedroid.dataset.resources.dataset_image_settings
import org.onedroid.dataset.resources.dataset_image_settings_body
import org.onedroid.dataset.resources.dataset_use_defaults
import org.onedroid.dataset.resources.outdated_images

/**
 * Edits a dataset's image settings. When they change and the dataset has images, the user can also convert
 * those images; that's confirmed first because it replaces the stored files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSettingsSheet(
    current: ImageSettings,
    defaults: ImageSettings,
    imageCount: Int,
    onSave: (settings: ImageSettings, convertExisting: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var convertExisting by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf(false) }
    // Converting to passthrough settings would change nothing: originals can't be restored.
    val canConvert = imageCount > 0 && draft != current && !draft.isPassthrough
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.dataset_image_settings), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.dataset_image_settings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft != defaults) {
                // Shifted left so the label lines up with the text above, not the button's padding.
                TextButton(onClick = { draft = defaults }, modifier = Modifier.offset(x = (-12).dp)) {
                    Text(stringResource(Res.string.dataset_use_defaults))
                }
            }
            ImageSettingsForm(settings = draft, onChange = { draft = it }, modifier = Modifier.padding(top = 8.dp))
            if (canConvert) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable { convertExisting = !convertExisting },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = convertExisting, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                    Text(pluralStringResource(Res.plurals.dataset_convert_existing, imageCount, imageCount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = { if (canConvert && convertExisting) confirming = true else onSave(draft, false) },
                enabled = draft != current,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }

    if (confirming) {
        ConvertConfirmDialog(
            count = imageCount,
            settings = draft,
            onConfirm = {
                confirming = false
                onSave(draft, true)
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Confirms replacing [count] stored images with versions in [settings]. */
@Composable
fun ConvertConfirmDialog(count: Int, settings: ImageSettings, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = pluralStringResource(Res.plurals.convert_confirm_title, count, count),
        text = stringResource(Res.string.convert_confirm_message, settings.summary()),
        confirmLabel = stringResource(Res.string.action_convert),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Offers to convert images stored with earlier settings. */
@Composable
fun OutdatedImagesCard(count: Int, enabled: Boolean, onConvert: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                pluralStringResource(Res.plurals.outdated_images, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onConvert, enabled = enabled) { Text(stringResource(Res.string.action_convert)) }
        }
    }
}
