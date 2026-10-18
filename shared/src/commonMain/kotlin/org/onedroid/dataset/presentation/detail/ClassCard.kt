package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.usecase.MAX_NAME_LENGTH
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_options_for
import org.onedroid.dataset.resources.action_rename_item
import org.onedroid.dataset.resources.action_save_name
import org.onedroid.dataset.resources.class_add_images
import org.onedroid.dataset.resources.class_capture_hint
import org.onedroid.dataset.resources.class_delete
import org.onedroid.dataset.resources.class_folder
import org.onedroid.dataset.resources.class_image_count
import org.onedroid.dataset.resources.class_name_label
import org.onedroid.dataset.resources.class_remove_all
import org.onedroid.dataset.resources.class_view_all
import org.onedroid.dataset.resources.source_camera
import org.onedroid.dataset.resources.source_upload
import org.onedroid.dataset.resources.view_image

@Composable
fun ClassCard(
    datasetId: String,
    imageClass: ClassUi,
    enabled: Boolean,
    onRename: (String) -> Unit,
    onCamera: () -> Unit,
    onUpload: () -> Unit,
    onAddFolder: () -> Unit,
    onViewAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (Long) -> Unit,
    camera: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        ClassHeader(imageClass, enabled, onRename, onAddFolder, onViewAll, onClear, onDelete)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Grows smoothly when the camera opens or closes instead of jumping.
        Column(Modifier.animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val count = imageClass.imageIds.size
            Text(
                if (count == 0) stringResource(Res.string.class_add_images) else pluralStringResource(Res.plurals.class_image_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (camera != null) {
                // Reserve the strip's slot even while empty, so the first capture doesn't push the
                // camera and the Hold to Record button down under the user's finger.
                Box(Modifier.fillMaxWidth().height(THUMBNAIL_SIZE.dp), contentAlignment = Alignment.CenterStart) {
                    if (count > 0) {
                        ImageStrip(datasetId, imageClass.imageIds, Modifier.fillMaxWidth(), onOpenImage)
                    } else {
                        Text(
                            stringResource(Res.string.class_capture_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                camera()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceButton(AppIcons.Camera, stringResource(Res.string.source_camera), enabled, onCamera)
                    SourceButton(AppIcons.Upload, stringResource(Res.string.source_upload), enabled, onUpload)
                    if (count > 0) {
                        ImageStrip(datasetId, imageClass.imageIds, Modifier.weight(1f), onOpenImage)
                    } else {
                        SourceButton(AppIcons.Folder, stringResource(Res.string.class_folder), enabled, onAddFolder)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassHeader(
    imageClass: ClassUi,
    enabled: Boolean,
    onRename: (String) -> Unit,
    onAddFolder: () -> Unit,
    onViewAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(classColor(imageClass.colorIndex)))
        if (editing) {
            ClassNameField(
                initial = imageClass.name,
                onDone = { name ->
                    if (name.trim() != imageClass.name) onRename(name)
                    editing = false
                },
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        } else {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    imageClass.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
                )
                IconButton(onClick = { editing = true }, enabled = enabled) {
                    Icon(
                        AppIcons.Edit,
                        contentDescription = stringResource(Res.string.action_rename_item, imageClass.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(AppIcons.MoreVert, contentDescription = stringResource(Res.string.action_options_for, imageClass.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.class_view_all)) },
                    leadingIcon = { Icon(AppIcons.Grid, contentDescription = null) },
                    enabled = imageClass.imageIds.isNotEmpty(),
                    onClick = { menuOpen = false; onViewAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.class_folder)) },
                    leadingIcon = { Icon(AppIcons.Folder, contentDescription = null) },
                    enabled = enabled,
                    onClick = { menuOpen = false; onAddFolder() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.class_remove_all)) },
                    leadingIcon = { Icon(AppIcons.Close, contentDescription = null) },
                    enabled = enabled && imageClass.imageIds.isNotEmpty(),
                    onClick = { menuOpen = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.class_delete)) },
                    leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                    enabled = enabled,
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun ClassNameField(initial: String, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    // Start with the whole name selected so typing replaces it.
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { value -> if (value.text.length <= MAX_NAME_LENGTH) text = value },
        singleLine = true,
        label = { Text(stringResource(Res.string.class_name_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
        keyboardActions = KeyboardActions(onDone = { onDone(text.text) }),
        trailingIcon = {
            IconButton(onClick = { onDone(text.text) }) {
                Icon(AppIcons.Check, contentDescription = stringResource(Res.string.action_save_name))
            }
        },
        modifier = modifier.focusRequester(focusRequester),
    )
}

@Composable
private fun SourceButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = colors.secondaryContainer,
        contentColor = if (enabled) colors.onSecondaryContainer else colors.onSurface.copy(alpha = 0.38f),
        modifier = Modifier.size(width = 76.dp, height = 64.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private const val THUMBNAIL_SIZE = 56

@Composable
private fun ImageStrip(datasetId: String, imageIds: List<Long>, modifier: Modifier = Modifier, onClick: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val newestId = imageIds.lastOrNull()
    // LazyRow keeps the previously first item anchored when new items are prepended, which hides
    // fresh captures off to the left. Follow the newest one while the user is at the start.
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val viewLabel = stringResource(Res.string.view_image)
    // Newest first, so fresh captures are visible without scrolling.
    LazyRow(modifier.height(THUMBNAIL_SIZE.dp), state = listState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(imageIds.asReversed(), key = { it }) { imageId ->
            DatasetThumbnail(
                datasetId = datasetId,
                imageId = imageId,
                size = THUMBNAIL_SIZE.dp,
                modifier = Modifier
                    .animateItem()
                    .clickable(onClickLabel = viewLabel, role = Role.Image) { onClick(imageId) },
            )
        }
    }
}
