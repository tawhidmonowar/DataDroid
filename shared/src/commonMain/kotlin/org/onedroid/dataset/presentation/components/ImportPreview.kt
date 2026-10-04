package org.onedroid.dataset.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.model.ImageSource
import org.onedroid.dataset.domain.usecase.ClassNaming
import org.onedroid.dataset.domain.usecase.FolderLayout
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_cancel
import org.onedroid.dataset.resources.action_import
import org.onedroid.dataset.resources.images_count
import org.onedroid.dataset.resources.import_class_count
import org.onedroid.dataset.resources.import_loose_images
import org.onedroid.dataset.resources.import_merge_splits
import org.onedroid.dataset.resources.import_naming_example
import org.onedroid.dataset.resources.import_naming_full
import org.onedroid.dataset.resources.import_naming_label
import org.onedroid.dataset.resources.import_naming_last
import org.onedroid.dataset.resources.import_naming_top
import org.onedroid.dataset.resources.import_preview_body
import org.onedroid.dataset.resources.import_preview_body_splits
import org.onedroid.dataset.resources.import_preview_title

/**
 * A folder or zip whose layout is ambiguous (nested folders, or train/val/test splits), waiting for the
 * user to choose how its folders become classes. [datasetName] is the suggested name of a new dataset.
 */
data class ImportPreviewUi(
    val layout: FolderLayout,
    val naming: ClassNaming = ClassNaming.TOP_FOLDER,
    val mergeSplits: Boolean = layout.splitFolders.isNotEmpty(),
    val datasetName: String? = null,
) {
    val groups: Map<String, List<ImageSource>> = layout.groups(naming, mergeSplits)

    /** The naming choice only matters while some class folder still holds subfolders. */
    val showsNaming: Boolean get() = layout.hasNesting(mergeSplits)

    companion object {
        /** How [layout] is imported when there's nothing to ask: top folders, splits merged. */
        fun defaultFor(layout: FolderLayout, datasetName: String? = null) = ImportPreviewUi(layout, datasetName = datasetName)
    }
}

@Composable
fun ImportPreviewDialog(
    preview: ImportPreviewUi,
    onNamingChange: (ClassNaming) -> Unit,
    onMergeSplitsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = preview.layout
    val splits = layout.splitFolders.joinToString(", ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.import_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (layout.splitFolders.isEmpty()) Res.string.import_preview_body else Res.string.import_preview_body_splits, splits),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (layout.splitFolders.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onMergeSplitsChange(!preview.mergeSplits) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = preview.mergeSplits, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(stringResource(Res.string.import_merge_splits, splits), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (preview.showsNaming) {
                    val sample = layout.samplePath(preview.mergeSplits)
                    Text(
                        stringResource(Res.string.import_naming_label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    NamingOption(ClassNaming.TOP_FOLDER, Res.string.import_naming_top, sample.firstOrNull(), preview.naming, onNamingChange)
                    NamingOption(ClassNaming.LAST_FOLDER, Res.string.import_naming_last, sample.lastOrNull(), preview.naming, onNamingChange)
                    NamingOption(
                        ClassNaming.FULL_PATH,
                        Res.string.import_naming_full,
                        sample.joinToString(FolderLayout.PATH_SEPARATOR),
                        preview.naming,
                        onNamingChange,
                    )
                }
                Text(
                    pluralStringResource(Res.plurals.import_class_count, preview.groups.size, preview.groups.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                preview.groups.entries.forEachIndexed { index, (name, images) ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            pluralStringResource(Res.plurals.images_count, images.size, images.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (layout.looseImageCount > 0) {
                    Text(
                        pluralStringResource(Res.plurals.import_loose_images, layout.looseImageCount, layout.looseImageCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = preview.groups.isNotEmpty()) { Text(stringResource(Res.string.action_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun NamingOption(
    naming: ClassNaming,
    label: StringResource,
    example: String?,
    selected: ClassNaming,
    onSelect: (ClassNaming) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = naming == selected, role = Role.RadioButton, onClick = { onSelect(naming) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = naming == selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            if (!example.isNullOrBlank()) {
                Text(
                    stringResource(Res.string.import_naming_example, example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
