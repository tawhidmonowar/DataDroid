package org.onedroid.dataset.presentation.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.core.platform.window.LightSystemBarIcons
import org.onedroid.dataset.core.platform.window.fullScreenDialogProperties
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.components.LocalImageLoader
import org.onedroid.dataset.presentation.theme.CameraTheme
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_close
import org.onedroid.dataset.resources.action_delete
import org.onedroid.dataset.resources.action_move_to
import org.onedroid.dataset.resources.action_select_all
import org.onedroid.dataset.resources.gallery_hint
import org.onedroid.dataset.resources.gallery_selected
import org.onedroid.dataset.resources.image_load_failed
import org.onedroid.dataset.resources.viewer_position

private sealed interface FullImage {
    data object Loading : FullImage
    data object Failed : FullImage
    class Loaded(val bitmap: ImageBitmap) : FullImage
}

/**
 * A class's images full screen, newest first, starting at [initialImageId]. Swipe between them, pinch or
 * double-tap to zoom, and move or delete the one showing. Closes once the class has no images left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewer(
    datasetId: String,
    imageClass: ClassUi,
    initialImageId: Long,
    otherClasses: List<ClassUi>,
    enabled: Boolean,
    snackbar: SnackbarHostState,
    onMove: (imageId: Long, toClassId: Int) -> Unit,
    onDelete: (imageId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val imageIds = imageClass.imageIds.asReversed()
    // The pager reads the page count and keys at different times; reading both from one state keeps them in
    // step when an image is deleted or moved.
    val pages by rememberUpdatedState(imageIds)
    val pager = rememberPagerState(initialPage = imageIds.indexOf(initialImageId).coerceAtLeast(0)) { pages.size }
    LaunchedEffect(imageIds.isEmpty()) { if (imageIds.isEmpty()) onDismiss() }
    if (imageIds.isEmpty()) return
    val current = imageIds[pager.currentPage.coerceIn(imageIds.indices)]

    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        CameraTheme {
            LightSystemBarIcons()
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = stringResource(Res.string.action_close)) }
                        },
                        title = {
                            Column {
                                Text(imageClass.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(Res.string.viewer_position, pager.currentPage + 1, imageIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                    )
                },
                bottomBar = {
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MoveToButton(otherClasses, enabled) { target -> onMove(current, target) }
                        TextButton(onClick = { onDelete(current) }, enabled = enabled) {
                            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.action_delete), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = Color.Black,
            ) { padding ->
                HorizontalPager(pager, key = { pages.getOrElse(it) { _ -> -1L - it } }, modifier = Modifier.padding(padding).fillMaxSize()) { page ->
                    pages.getOrNull(page)?.let { ZoomableImage(datasetId, it) }
                }
            }
        }
    }
}

/** One full image fitted to the page; pinch or double-tap to zoom, and drag around while zoomed. */
@Composable
private fun ZoomableImage(datasetId: String, imageId: Long) {
    val loader = LocalImageLoader.current
    val image by produceState<FullImage>(FullImage.Loading, loader, datasetId, imageId) {
        value = loader?.fullImage(datasetId, imageId)?.let { FullImage.Loaded(it) } ?: FullImage.Failed
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                    offset = Offset.Zero
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // One finger on an unzoomed image is left to the pager, to swipe to the next one.
                        if (fingers > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                            val limit = Offset(size.width * (scale - 1) / 2, size.height * (scale - 1) / 2)
                            val moved = offset + event.calculatePan()
                            offset = if (scale == 1f) Offset.Zero else Offset(moved.x.coerceIn(-limit.x, limit.x), moved.y.coerceIn(-limit.y, limit.y))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val loaded = image) {
            is FullImage.Loaded -> Image(
                loaded.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
            FullImage.Loading -> {
                DatasetThumbnail(datasetId, imageId, size = 240.dp, shape = RoundedCornerShape(8.dp))
                CircularProgressIndicator(Modifier.size(32.dp))
            }
            FullImage.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatasetThumbnail(datasetId, imageId, size = 240.dp, shape = RoundedCornerShape(8.dp))
                Text(stringResource(Res.string.image_load_failed), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MoveToButton(classes: List<ClassUi>, enabled: Boolean, onMove: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menuOpen = true }, enabled = enabled && classes.isNotEmpty()) {
            Icon(AppIcons.MoveTo, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.action_move_to), modifier = Modifier.padding(start = 8.dp))
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
 * taps toggle images and the selection can be moved or deleted together (with undo).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassGalleryDialog(
    datasetId: String,
    imageClass: ClassUi,
    otherClasses: List<ClassUi>,
    enabled: Boolean,
    snackbar: SnackbarHostState,
    onOpenImage: (Long) -> Unit,
    onMove: (Set<Long>, toClassId: Int) -> Unit,
    onDelete: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(imageClass.id) { mutableStateOf(emptySet<Long>()) }
    // Drop ids that were moved or deleted.
    val selection = selected.intersect(imageClass.imageIds.toSet())

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
                            IconButton(
                                onClick = {
                                    onDelete(selection)
                                    selected = emptySet()
                                },
                                enabled = enabled,
                            ) {
                                Icon(AppIcons.Delete, contentDescription = stringResource(Res.string.action_delete))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
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
                            .animateItem()
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
}

private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 5f
