package org.onedroid.dataset.presentation.capture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.onedroid.dataset.core.platform.camera.CameraPreview
import org.onedroid.dataset.core.platform.picker.rememberImagePicker
import org.onedroid.dataset.core.platform.window.LightSystemBarIcons
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.DatasetThumbnail
import org.onedroid.dataset.presentation.components.UnknownThumbnail
import org.onedroid.dataset.presentation.components.NameDialog
import org.onedroid.dataset.presentation.components.ObserveAsEvents
import org.onedroid.dataset.presentation.components.ProgressBanner
import org.onedroid.dataset.presentation.components.UnknownImagesSheet
import org.onedroid.dataset.presentation.components.label
import org.onedroid.dataset.presentation.detail.ClassUi
import org.onedroid.dataset.presentation.theme.CameraTheme
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_close
import org.onedroid.dataset.resources.action_create
import org.onedroid.dataset.resources.auto_sort_closest
import org.onedroid.dataset.resources.auto_sort_no_classes
import org.onedroid.dataset.resources.auto_sort_point
import org.onedroid.dataset.resources.auto_sort_preparing
import org.onedroid.dataset.resources.auto_sort_preparing_count
import org.onedroid.dataset.resources.auto_sort_similar
import org.onedroid.dataset.resources.auto_sort_strictness
import org.onedroid.dataset.resources.auto_sort_strictness_hint
import org.onedroid.dataset.resources.auto_sort_strictness_off
import org.onedroid.dataset.resources.auto_sort_strictness_off_hint
import org.onedroid.dataset.resources.auto_sort_title
import org.onedroid.dataset.resources.camera_recording
import org.onedroid.dataset.resources.camera_switch
import org.onedroid.dataset.resources.capture_add_class
import org.onedroid.dataset.resources.capture_auto
import org.onedroid.dataset.resources.capture_auto_settings
import org.onedroid.dataset.resources.capture_auto_settings_body
import org.onedroid.dataset.resources.capture_converting
import org.onedroid.dataset.resources.capture_recent_empty
import org.onedroid.dataset.resources.capture_record_start
import org.onedroid.dataset.resources.capture_record_stop
import org.onedroid.dataset.resources.capture_hint
import org.onedroid.dataset.resources.capture_library
import org.onedroid.dataset.resources.capture_saved
import org.onedroid.dataset.resources.capture_saving_to
import org.onedroid.dataset.resources.capture_shutter
import org.onedroid.dataset.resources.capture_unknowns
import org.onedroid.dataset.resources.class_default_name
import org.onedroid.dataset.resources.error_sorting_unavailable
import org.onedroid.dataset.resources.images_count
import org.onedroid.dataset.resources.unknown_label
import kotlin.math.roundToInt

@Composable
fun CaptureRoot(
    datasetId: String,
    classId: Int?,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = koinViewModel { parametersOf(datasetId, classId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pickPhotos = rememberImagePicker { images -> viewModel.onAction(CaptureAction.OnPhotosPicked(images)) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            CaptureEvent.NavigateBack -> onBack()
            is CaptureEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.message.asStringAsync()) }
        }
    }

    CaptureScreen(state = state, onAction = viewModel::onAction, onPickPhotos = pickPhotos, snackbar = snackbar)
}

/**
 * A full-screen camera. The chips under the viewfinder choose where images go: Auto sorts each into the class
 * it looks like, a class chip saves everything to that class. Tap the shutter for one image, hold it to record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    state: CaptureState,
    onAction: (CaptureAction) -> Unit,
    onPickPhotos: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var addingClass by remember { mutableStateOf(false) }
    var showingUnknowns by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    val held = rememberHeld(state.recording)

    CameraTheme {
        LightSystemBarIcons()
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                val viewfinder = @Composable { modifier: Modifier -> Viewfinder(state, held, onAction, modifier) }
                val controls = @Composable { modifier: Modifier ->
                    Controls(
                        state = state,
                        held = held,
                        onAction = onAction,
                        onPickPhotos = onPickPhotos,
                        onAddClass = { addingClass = true },
                        onShowUnknowns = { showingUnknowns = true },
                        modifier = modifier,
                    )
                }
                if (maxWidth > maxHeight) {
                    // Landscape and tablets: viewfinder on the left, controls beside it.
                    Row(Modifier.fillMaxSize()) {
                        viewfinder(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            TopBar(state, onAction, onShowSettings = { showingSettings = true })
                            controls(Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(state, onAction, onShowSettings = { showingSettings = true })
                        viewfinder(Modifier.fillMaxWidth().aspectRatio(1f))
                        controls(Modifier.weight(1f))
                    }
                }
            }
        }

        if (addingClass) {
            val names = state.classes.map { it.name }
            NameDialog(
                title = stringResource(Res.string.capture_add_class),
                initial = uniqueName(stringResource(Res.string.class_default_name, state.classes.size + 1), names),
                confirmLabel = stringResource(Res.string.action_create),
                taken = names,
                onConfirm = { name ->
                    addingClass = false
                    onAction(CaptureAction.OnAddClass(name))
                },
                onDismiss = { addingClass = false },
            )
        }

        if (showingUnknowns) {
            UnknownImagesSheet(
                unknowns = state.unknowns,
                classes = state.classes,
                enabled = state.canEdit,
                onFileToClass = { onAction(CaptureAction.OnUnknownsToClass(it)) },
                onFileToNewClass = { onAction(CaptureAction.OnUnknownsToNewClass(it)) },
                onDiscard = { onAction(CaptureAction.OnDiscardUnknowns(it)) },
                onDismiss = { showingUnknowns = false },
            )
        }

        if (showingSettings) {
            ModalBottomSheet(onDismissRequest = { showingSettings = false }, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.capture_auto_settings), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(Res.string.capture_auto_settings_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StrictnessControl(state.strictness) { onAction(CaptureAction.OnStrictnessChange(it)) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: CaptureState, onAction: (CaptureAction) -> Unit, onShowSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(CaptureAction.OnBack) }) {
            Icon(AppIcons.Close, contentDescription = stringResource(Res.string.action_close))
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(state.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.savedCount > 0) {
                Text(
                    pluralStringResource(Res.plurals.capture_saved, state.savedCount, state.savedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.target == CaptureTarget.Auto) {
            IconButton(onClick = onShowSettings) {
                Icon(AppIcons.Tune, contentDescription = stringResource(Res.string.capture_auto_settings))
            }
        }
        IconButton(onClick = { onAction(CaptureAction.OnFlipCamera) }) {
            Icon(AppIcons.SwitchCamera, contentDescription = stringResource(Res.string.camera_switch))
        }
    }
}

/** Square, because captured frames are center-cropped to a square: what's shown is what's saved. */
@Composable
private fun Viewfinder(state: CaptureState, held: Boolean, onAction: (CaptureAction) -> Unit, modifier: Modifier) {
    val currentOnAction by rememberUpdatedState(onAction)
    val auto = state.target == CaptureTarget.Auto
    Box(modifier.background(Color.Black)) {
        if (!state.loading) {
            CameraPreview(
                facing = state.facing,
                // Auto-sort reads every frame for its live label; a class only needs frames it saves.
                onCapture = if (auto || state.capturing) { jpeg -> currentOnAction(CaptureAction.OnCapture(jpeg)) } else null,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (held) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Text(
                    stringResource(Res.string.camera_recording),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (auto) {
            PredictionPill(state, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

/** Where the current view would go under auto-sort, over the bottom of the viewfinder. */
@Composable
private fun PredictionPill(state: CaptureState, modifier: Modifier) {
    val autoSort = state.autoSort
    val match = autoSort.prediction
    val closest = match?.closestClassId?.let { id -> state.classes.firstOrNull { it.id == id } }
    val (dot, text) = when {
        autoSort.unavailable -> null to stringResource(Res.string.error_sorting_unavailable)
        autoSort.preparing -> null to (
            if (autoSort.toPrepare == 0) stringResource(Res.string.auto_sort_preparing)
            else stringResource(Res.string.auto_sort_preparing_count, autoSort.prepared, autoSort.toPrepare)
            )
        match != null && match.closestClassId == null -> null to stringResource(Res.string.auto_sort_no_classes)
        match == null || closest == null -> null to stringResource(Res.string.auto_sort_point)
        match.isUnknown -> null to stringResource(Res.string.unknown_label) + " · " +
            stringResource(Res.string.auto_sort_closest, closest.name, match.percent())
        else -> closest.colorIndex to closest.name + " · " + stringResource(Res.string.auto_sort_similar, match.percent())
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    dot != null -> Box(Modifier.size(10.dp).clip(CircleShape).background(classColor(dot)))
                    match?.isUnknown == true && !autoSort.preparing -> Icon(
                        AppIcons.Help,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (dot != null) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = if (dot != null || match?.isUnknown == true) 8.dp else 0.dp),
                )
            }
            if (autoSort.preparing && autoSort.toPrepare > 0) {
                LinearProgressIndicator(
                    progress = { autoSort.prepared.toFloat() / autoSort.toPrepare },
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    state: CaptureState,
    held: Boolean,
    onAction: (CaptureAction) -> Unit,
    onPickPhotos: () -> Unit,
    onAddClass: () -> Unit,
    onShowUnknowns: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Under Auto the selected chip already says where images go.
        if (state.converting || state.targetClass != null) StatusLine(state)
        TargetChips(state, onAction, onAddClass)
        RecentCaptures(state, onShowUnknowns)
        Spacer(Modifier.weight(1f))
        state.transfer?.let { transfer ->
            ProgressBanner(
                text = transfer.label(),
                fraction = transfer.fraction,
                onCancel = { onAction(CaptureAction.OnCancelTransfer) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        ShutterRow(state, held, onAction, onPickPhotos, onShowUnknowns)
        Text(
            stringResource(Res.string.capture_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Tall phones leave room under the square viewfinder; sit the shutter in it rather than at the very bottom.
        Spacer(Modifier.weight(0.5f))
    }
}

@Composable
private fun StatusLine(state: CaptureState) {
    val target = state.targetClass
    val text = when {
        state.converting -> stringResource(Res.string.capture_converting)
        target != null -> stringResource(Res.string.capture_saving_to, target.name) + " · " +
            pluralStringResource(Res.plurals.images_count, target.imageIds.size, target.imageIds.size)
        else -> stringResource(Res.string.auto_sort_title)
    }
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (target != null) Box(Modifier.size(10.dp).clip(CircleShape).background(classColor(target.colorIndex)))
        else Icon(AppIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Where images go: Auto, one chip per class, and a chip to start a class. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetChips(state: CaptureState, onAction: (CaptureAction) -> Unit, onAddClass: () -> Unit) {
    val listState = rememberLazyListState()
    val selectedIndex = when (val target = state.target) {
        CaptureTarget.Auto -> 0
        is CaptureTarget.Class -> state.classes.indexOfFirst { it.id == target.classId } + 1
    }
    // Keep the chip before the selected one in view too, so it's clear the row scrolls.
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fadingEdges(start = listState.canScrollBackward, end = listState.canScrollForward),
    ) {
        item(key = "auto") {
            FilterChip(
                selected = state.target == CaptureTarget.Auto,
                onClick = { onAction(CaptureAction.OnSelectTarget(CaptureTarget.Auto)) },
                label = { Text(stringResource(Res.string.capture_auto)) },
                leadingIcon = { Icon(AppIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        items(state.classes, key = { it.id }) { imageClass ->
            ClassChip(
                imageClass = imageClass,
                selected = (state.target as? CaptureTarget.Class)?.classId == imageClass.id,
                onClick = { onAction(CaptureAction.OnSelectTarget(CaptureTarget.Class(imageClass.id))) },
            )
        }
        item(key = "add") {
            FilterChip(
                selected = false,
                enabled = state.canEdit,
                onClick = onAddClass,
                label = { Text(stringResource(Res.string.capture_add_class)) },
                leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassChip(imageClass: ClassUi, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row {
                Text(imageClass.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" ${imageClass.imageIds.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(classColor(imageClass.colorIndex))) },
    )
}

/**
 * What's been captured since the screen opened, newest first, each marked with the class it went to (or "?"
 * for unknown). Tapping an unknown one opens the unknowns. Keeps its height while empty, so the shutter
 * doesn't move under the user's finger when the first image arrives.
 */
@Composable
private fun RecentCaptures(state: CaptureState, onShowUnknowns: () -> Unit) {
    val listState = rememberLazyListState()
    val newest = state.recent.firstOrNull()?.key
    // Follow new captures while the user is at the start, rather than leaving them off to the left.
    LaunchedEffect(newest) {
        if (newest != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    Box(Modifier.fillMaxWidth().height(RECENT_SIZE.dp), contentAlignment = Alignment.CenterStart) {
        if (state.recent.isEmpty()) {
            Text(
                stringResource(Res.string.capture_recent_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            val colorIndex = state.classes.associate { it.id to it.colorIndex }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.recent, key = { it.key }) { capture ->
                    Box(Modifier.animateItem().size(RECENT_SIZE.dp)) {
                        when (capture) {
                            is RecentCapture.Saved -> {
                                DatasetThumbnail(state.datasetId, capture.imageId, size = RECENT_SIZE.dp, shape = RoundedCornerShape(8.dp))
                                colorIndex[capture.classId]?.let { index ->
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp)
                                            .border(1.5.dp, Color.White, CircleShape).clip(CircleShape).background(classColor(index)),
                                    )
                                }
                            }
                            is RecentCapture.Unknown -> {
                                Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).clickable(onClick = onShowUnknowns)) {
                                    UnknownThumbnail(capture.image, size = RECENT_SIZE.dp, modifier = Modifier.matchParentSize())
                                }
                                Icon(
                                    AppIcons.Help,
                                    contentDescription = stringResource(Res.string.unknown_label),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterRow(state: CaptureState, held: Boolean, onAction: (CaptureAction) -> Unit, onPickPhotos: () -> Unit, onShowUnknowns: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideButton(enabled = state.canCapture, onClick = onPickPhotos, label = stringResource(Res.string.capture_library)) {
            Icon(AppIcons.Collections, contentDescription = null)
        }
        val target = state.targetClass
        ShutterButton(
            recording = state.recording,
            held = held,
            enabled = state.canCapture,
            color = target?.let { classColor(it.colorIndex) } ?: MaterialTheme.colorScheme.primary,
            onStart = { onAction(CaptureAction.OnStartRecording) },
            onStop = { onAction(CaptureAction.OnStopRecording) },
        )
        if (state.unknowns.isEmpty()) {
            Spacer(Modifier.size(SIDE_BUTTON_SIZE.dp))
        } else {
            SideButton(enabled = true, onClick = onShowUnknowns, label = stringResource(Res.string.capture_unknowns)) {
                BadgedBox(badge = { Badge { Text(state.unknowns.size.toString()) } }) {
                    Icon(AppIcons.Inbox, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SideButton(enabled: Boolean, onClick: () -> Unit, label: String, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier.size(SIDE_BUTTON_SIZE.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

/**
 * Captures one image on a tap and keeps capturing while held; turns into a red stop square while recording.
 * [recording] is the press as the view model sees it, [held] whether it has lasted long enough to show.
 */
@Composable
private fun ShutterButton(
    recording: Boolean,
    held: Boolean,
    enabled: Boolean,
    color: Color,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnStop by rememberUpdatedState(onStop)
    val fill by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            held -> MaterialTheme.colorScheme.error
            else -> color
        },
        label = "shutter",
    )
    val innerSize by animateDpAsState(if (held) 32.dp else 60.dp, label = "shutterSize")
    val corner by animateDpAsState(if (held) 8.dp else 30.dp, label = "shutterCorner")
    val label = stringResource(Res.string.capture_shutter)
    val recordingLabel = stringResource(Res.string.camera_recording)
    val startLabel = stringResource(Res.string.capture_record_start)
    val stopLabel = stringResource(Res.string.capture_record_stop)
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, if (enabled) Color.White else Color.White.copy(alpha = 0.38f), CircleShape)
            // Screen readers can't press and hold, so a long press there starts recording and a tap stops it.
            .semantics {
                role = Role.Button
                contentDescription = label
                if (recording) stateDescription = recordingLabel
                if (!enabled) disabled()
                onClick(label = if (recording) stopLabel else label) {
                    if (!recording) currentOnStart()
                    currentOnStop()
                    true
                }
                if (!recording) {
                    onLongClick(label = startLabel) {
                        currentOnStart()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    currentOnStart()
                    tryAwaitRelease()
                    currentOnStop()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(innerSize).clip(RoundedCornerShape(corner)).background(fill))
    }
}

/** Fades the content out towards an edge that has more to scroll to. */
private fun Modifier.fadingEdges(start: Boolean, end: Boolean): Modifier =
    if (!start && !end) this
    else graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val fade = FADE_WIDTH.toPx() / size.width
        drawRect(
            Brush.horizontalGradient(
                0f to if (start) Color.Transparent else Color.Black,
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to if (end) Color.Transparent else Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val FADE_WIDTH = 32.dp

/** How alike an image must be to a class to go to it; 0 turns unknowns off. */
@Composable
private fun StrictnessControl(strictness: Int, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.auto_sort_strictness), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (strictness == 0) stringResource(Res.string.auto_sort_strictness_off) else "$strictness",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = strictness.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = 0f..100f)
        Text(
            stringResource(if (strictness == 0) Res.string.auto_sort_strictness_off_hint else Res.string.auto_sort_strictness_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Whether a shutter press has lasted long enough to show as recording. Every tap briefly records too (the
 * view model starts on press and stops on release), so showing it at once would flash the badge on each shot.
 */
@Composable
private fun rememberHeld(recording: Boolean): Boolean {
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        if (recording) delay(HOLD_TO_RECORD_MS)
        held = recording
    }
    return held
}

private const val HOLD_TO_RECORD_MS = 250L

private fun ClassMatch.percent(): String = "${(similarity * 100).roundToInt()}%"

private const val SIDE_BUTTON_SIZE = 52
private const val RECENT_SIZE = 64
