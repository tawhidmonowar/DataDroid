package org.onedroid.dataset.presentation.detail

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
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
import org.onedroid.dataset.domain.usecase.RECOMMENDED_IMAGES_PER_CLASS
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_capture
import org.onedroid.dataset.resources.action_options_for
import org.onedroid.dataset.resources.action_rename_item
import org.onedroid.dataset.resources.action_save_name
import org.onedroid.dataset.resources.class_delete
import org.onedroid.dataset.resources.class_empty
import org.onedroid.dataset.resources.class_folder
import org.onedroid.dataset.resources.class_more_needed
import org.onedroid.dataset.resources.class_name_label
import org.onedroid.dataset.resources.class_ready
import org.onedroid.dataset.resources.class_remove_all
import org.onedroid.dataset.resources.class_see_all
import org.onedroid.dataset.resources.class_view_all
import org.onedroid.dataset.resources.images_count
import org.onedroid.dataset.resources.source_upload
import org.onedroid.dataset.resources.view_image

/**
 * A class: its name, how close it is to the recommended number of images, its newest images, and ways to
 * add more. Tapping the card anywhere else opens all its images, as "See all" does.
 */
@Composable
fun ClassCard(
    datasetId: String,
    imageClass: ClassUi,
    enabled: Boolean,
    onRename: (String) -> Unit,
    onCapture: () -> Unit,
    onUpload: () -> Unit,
    onAddFolder: () -> Unit,
    onViewAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = imageClass.imageIds.size
    // An empty class has nothing to open, but shouldn't look disabled for it.
    val colors = CardDefaults.elevatedCardColors().let { it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor) }
    ElevatedCard(onClick = onViewAll, enabled = count > 0, colors = colors, modifier = modifier.fillMaxWidth()) {
        ClassHeader(imageClass, enabled, onRename, onAddFolder, onViewAll, onClear, onDelete)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Progress(count, classColor(imageClass.colorIndex))
            if (count > 0) {
                ImageStrip(datasetId, imageClass.imageIds, onOpenImage, onViewAll)
            } else {
                Text(
                    stringResource(Res.string.class_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCapture, enabled = enabled) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.action_capture), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onUpload, enabled = enabled) {
                    Icon(AppIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.source_upload), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** How far the class is towards the recommended number of images. */
@Composable
private fun Progress(count: Int, color: Color) {
    val ready = count >= RECOMMENDED_IMAGES_PER_CLASS
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.images_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (ready) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(Res.string.class_ready),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else {
                val missing = RECOMMENDED_IMAGES_PER_CLASS - count
                Text(
                    pluralStringResource(Res.plurals.class_more_needed, missing, missing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { (count.toFloat() / RECOMMENDED_IMAGES_PER_CLASS).coerceAtMost(1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth(),
        )
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
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                modifier = Modifier.weight(1f).padding(start = 12.dp, top = 8.dp),
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
                        modifier = Modifier.size(20.dp),
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

private const val THUMBNAIL_SIZE = 64

/** The newest images first, so fresh captures are in view, ending in a tile that opens them all. */
@Composable
private fun ImageStrip(datasetId: String, imageIds: List<Long>, onClick: (Long) -> Unit, onViewAll: () -> Unit) {
    val listState = rememberLazyListState()
    val newestId = imageIds.lastOrNull()
    // LazyRow keeps the previously first item anchored when new items are prepended, which hides
    // fresh captures off to the left. Follow the newest one while the user is at the start.
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val viewLabel = stringResource(Res.string.view_image)
    LazyRow(Modifier.fillMaxWidth().height(THUMBNAIL_SIZE.dp), state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(imageIds.asReversed().take(STRIP_LIMIT), key = { it }) { imageId ->
            DatasetThumbnail(
                datasetId = datasetId,
                imageId = imageId,
                size = THUMBNAIL_SIZE.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .animateItem()
                    .clickable(onClickLabel = viewLabel, role = Role.Image) { onClick(imageId) },
            )
        }
        item(key = "all") {
            Surface(
                onClick = onViewAll,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(THUMBNAIL_SIZE.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(AppIcons.Grid, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.class_see_all), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Thumbnails shown before the "see all" tile; the gallery has the rest. */
private const val STRIP_LIMIT = 30
