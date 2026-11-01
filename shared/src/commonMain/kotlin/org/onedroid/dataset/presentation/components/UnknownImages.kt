package org.onedroid.dataset.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.core.platform.image.decodeSquare
import org.onedroid.dataset.core.platform.image.toImageBitmap
import org.onedroid.dataset.domain.model.UnknownImage
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.presentation.detail.ClassUi
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_create
import org.onedroid.dataset.resources.class_default_name
import org.onedroid.dataset.resources.unknown_add_to
import org.onedroid.dataset.resources.unknown_body
import org.onedroid.dataset.resources.unknown_discard
import org.onedroid.dataset.resources.unknown_discard_body
import org.onedroid.dataset.resources.unknown_discard_title
import org.onedroid.dataset.resources.unknown_new_class
import org.onedroid.dataset.resources.unknown_new_class_title
import org.onedroid.dataset.resources.unknown_remove
import org.onedroid.dataset.resources.unknown_review
import org.onedroid.dataset.resources.unknown_title

/** A row inviting the user to sort the images auto-sort couldn't place, with a peek at a few of them. */
@Composable
fun UnknownImagesCard(unknowns: List<UnknownImage>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    pluralStringResource(Res.plurals.unknown_title, unknowns.size, unknowns.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(Res.string.unknown_review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Newest first, overlapping like a stack of prints.
            Box {
                unknowns.takeLast(PEEK_COUNT).reversed().forEachIndexed { index, unknown ->
                    UnknownThumbnail(unknown, size = 36.dp, modifier = Modifier.padding(start = (index * 20).dp))
                }
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The images auto-sort couldn't place, with what to do with them: start a class with them, add them to a
 * class, or discard them. Closes itself once none are left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownImagesSheet(
    unknowns: List<UnknownImage>,
    classes: List<ClassUi>,
    enabled: Boolean,
    onFileToClass: (classId: Int) -> Unit,
    onFileToNewClass: (name: String) -> Unit,
    onDiscard: (ids: Set<Long>?) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(unknowns.isEmpty()) { if (unknowns.isEmpty()) onDismiss() }
    var naming by remember { mutableStateOf(false) }
    var choosingClass by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    pluralStringResource(Res.plurals.unknown_title, unknowns.size, unknowns.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(stringResource(Res.string.unknown_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Newest first, so the latest capture is in view.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            ) {
                items(unknowns.asReversed(), key = { it.id }) { unknown ->
                    RemovableThumbnail(unknown, onRemove = { onDiscard(setOf(unknown.id)) }, modifier = Modifier.animateItem())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { naming = true }, enabled = enabled) {
                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.unknown_new_class), modifier = Modifier.padding(start = 8.dp))
                }
                if (classes.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { choosingClass = true }, enabled = enabled) {
                            Text(stringResource(Res.string.unknown_add_to))
                        }
                        DropdownMenu(expanded = choosingClass, onDismissRequest = { choosingClass = false }) {
                            classes.forEach { imageClass ->
                                DropdownMenuItem(
                                    text = { Text(imageClass.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(classColor(imageClass.colorIndex))) },
                                    onClick = {
                                        choosingClass = false
                                        onFileToClass(imageClass.id)
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { discarding = true }, enabled = enabled) {
                    Text(stringResource(Res.string.unknown_discard), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (naming) {
        val names = classes.map { it.name }
        NameDialog(
            title = stringResource(Res.string.unknown_new_class_title),
            initial = uniqueName(stringResource(Res.string.class_default_name, classes.size + 1), names),
            confirmLabel = stringResource(Res.string.action_create),
            taken = names,
            onConfirm = { name ->
                naming = false
                onFileToNewClass(name)
            },
            onDismiss = { naming = false },
        )
    }

    if (discarding) {
        ConfirmDialog(
            title = pluralStringResource(Res.plurals.unknown_discard_title, unknowns.size, unknowns.size),
            text = stringResource(Res.string.unknown_discard_body),
            confirmLabel = stringResource(Res.string.unknown_discard),
            onConfirm = {
                discarding = false
                onDiscard(null)
            },
            onDismiss = { discarding = false },
        )
    }
}

@Composable
private fun RemovableThumbnail(unknown: UnknownImage, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f)) {
        UnknownThumbnail(unknown, size = 76.dp, modifier = Modifier.matchParentSize())
        val removeLabel = stringResource(Res.string.unknown_remove)
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).semantics { contentDescription = removeLabel },
        ) {
            Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.padding(3.dp))
        }
    }
}

@Composable
fun UnknownThumbnail(unknown: UnknownImage, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, unknown.id) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeSquare(unknown.preview, THUMBNAIL_PIXELS).toImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

private const val PEEK_COUNT = 3
private const val THUMBNAIL_PIXELS = 160
