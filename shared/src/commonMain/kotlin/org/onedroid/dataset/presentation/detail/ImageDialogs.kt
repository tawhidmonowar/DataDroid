package org.onedroid.dataset.presentation.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.ConfirmDialog
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.components.LocalImageLoader
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_close
import org.onedroid.dataset.resources.action_delete
import org.onedroid.dataset.resources.action_move_to
import org.onedroid.dataset.resources.action_select_all
import org.onedroid.dataset.resources.gallery_delete_message
import org.onedroid.dataset.resources.gallery_delete_title
import org.onedroid.dataset.resources.gallery_hint
import org.onedroid.dataset.resources.gallery_selected
import org.onedroid.dataset.resources.image_delete
import org.onedroid.dataset.resources.image_load_failed

private sealed interface FullImage {
    data object Loading : FullImage
    data object Failed : FullImage
    class Loaded(val bitmap: ImageBitmap) : FullImage
}

/** One image, as large as the dialog allows, with actions to move it to another class or delete it. */
@Composable
fun ImageDialog(
    datasetId: String,
    imageId: Long,
    className: String,
    otherClasses: List<ClassUi>,
    enabled: Boolean,
    onMove: (toClassId: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val loader = LocalImageLoader.current
    val image by produceState<FullImage>(FullImage.Loading, loader, datasetId, imageId) {
        value = loader?.fullImage(datasetId, imageId)?.let { FullImage.Loaded(it) } ?: FullImage.Failed
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(className, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentAlignment = Alignment.Center) {
                val bitmap = (image as? FullImage.Loaded)?.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    DatasetThumbnail(datasetId, imageId, size = 240.dp, shape = RoundedCornerShape(8.dp))
                    if (image == FullImage.Failed) {
                        Text(
                            stringResource(Res.string.image_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
            }
        },
        confirmButton = {
            MoveToButton(otherClasses, enabled, onMove)
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(Res.string.image_delete), color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )
}

@Composable
private fun MoveToButton(classes: List<ClassUi>, enabled: Boolean, onMove: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menuOpen = true }, enabled = enabled && classes.isNotEmpty()) {
            Text(stringResource(Res.string.action_move_to))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            classes.forEach { target ->
                DropdownMenuItem(text = { Text(target.name) }, onClick = { menuOpen = false; onMove(target.id) })
            }
        }
    }
}

/**
 * Every image of a class in a grid. Tapping opens an image; a long press starts selecting, after which
 * taps toggle images and the selection can be moved or deleted together.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassGalleryDialog(
    datasetId: String,
    imageClass: ClassUi,
    otherClasses: List<ClassUi>,
    enabled: Boolean,
    onOpenImage: (Long) -> Unit,
    onMove: (Set<Long>, toClassId: Int) -> Unit,
    onDelete: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(imageClass.id) { mutableStateOf(emptySet<Long>()) }
    // Drop ids that were moved or deleted.
    val selection = selected.intersect(imageClass.imageIds.toSet())
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (selection.isEmpty()) onDismiss() else selected = emptySet() }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(Res.string.action_close))
                        }
                    },
                    title = {
                        Text(
                            if (selection.isEmpty()) imageClass.name
                            else pluralStringResource(Res.plurals.gallery_selected, selection.size, selection.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (selection.isEmpty()) {
                            TextButton(onClick = { selected = imageClass.imageIds.toSet() }, enabled = enabled) {
                                Text(stringResource(Res.string.action_select_all))
                            }
                        } else {
                            MoveToButton(otherClasses, enabled) { target -> onMove(selection, target); selected = emptySet() }
                            IconButton(onClick = { confirmDelete = true }, enabled = enabled) {
                                Icon(AppIcons.Delete, contentDescription = stringResource(Res.string.action_delete))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(Res.string.gallery_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                items(imageClass.imageIds.asReversed(), key = { it }) { imageId ->
                    val isSelected = imageId in selection
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = {
                                    when {
                                        selection.isEmpty() -> onOpenImage(imageId)
                                        isSelected -> selected = selection - imageId
                                        else -> selected = selection + imageId
                                    }
                                },
                                onLongClick = { if (enabled) selected = selection + imageId },
                            )
                            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier),
                    ) {
                        DatasetThumbnail(datasetId, imageId, size = 160.dp, modifier = Modifier.matchParentSize(), shape = RoundedCornerShape(6.dp))
                        if (isSelected) {
                            Icon(
                                AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(Res.plurals.gallery_delete_title, selection.size, selection.size),
            text = stringResource(Res.string.gallery_delete_message),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete(selection)
                selected = emptySet()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
