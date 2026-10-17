package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.usecase.ClassMatch
import org.onedroid.dataset.domain.usecase.uniqueName
import org.onedroid.dataset.core.platform.camera.CameraFacing
import org.onedroid.dataset.core.platform.image.decodeSquare
import org.onedroid.dataset.core.platform.image.toImageBitmap
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.components.NameDialog
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_create
import org.onedroid.dataset.resources.auto_sort_body
import org.onedroid.dataset.resources.auto_sort_closest
import org.onedroid.dataset.resources.auto_sort_no_classes
import org.onedroid.dataset.resources.auto_sort_photos
import org.onedroid.dataset.resources.auto_sort_point
import org.onedroid.dataset.resources.auto_sort_preparing
import org.onedroid.dataset.resources.auto_sort_preparing_count
import org.onedroid.dataset.resources.auto_sort_saved
import org.onedroid.dataset.resources.auto_sort_similar
import org.onedroid.dataset.resources.auto_sort_start
import org.onedroid.dataset.resources.auto_sort_strictness
import org.onedroid.dataset.resources.auto_sort_strictness_hint
import org.onedroid.dataset.resources.auto_sort_strictness_off
import org.onedroid.dataset.resources.auto_sort_strictness_off_hint
import org.onedroid.dataset.resources.auto_sort_title
import org.onedroid.dataset.resources.class_default_name
import org.onedroid.dataset.resources.error_sorting_unavailable
import org.onedroid.dataset.resources.action_close
import org.onedroid.dataset.resources.unknown_add_to
import org.onedroid.dataset.resources.unknown_body
import org.onedroid.dataset.resources.unknown_discard
import org.onedroid.dataset.resources.unknown_label
import org.onedroid.dataset.resources.unknown_new_class
import org.onedroid.dataset.resources.unknown_new_class_title
import org.onedroid.dataset.resources.unknown_remove
import org.onedroid.dataset.resources.unknown_title
import kotlin.math.roundToInt

/** Invites the user to capture without choosing a class first. */
@Composable
fun AutoSortEntryCard(enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(Res.string.auto_sort_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.auto_sort_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpen, enabled = enabled) {
                Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.auto_sort_start), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * The auto-sort camera: shows where the current frame would go, saves captures to that class, and holds
 * images that match no class until the user starts a class with them, files them elsewhere, or drops them.
 */
@Composable
fun AutoSortCard(
    autoSort: AutoSortUi,
    classes: List<ClassUi>,
    facing: CameraFacing,
    recording: Boolean,
    enabled: Boolean,
    onAction: (DatasetDetailAction) -> Unit,
    onPickPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(Res.string.auto_sort_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (autoSort.unavailable) {
                Text(stringResource(Res.string.error_sorting_unavailable), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onAction(DatasetDetailAction.OnCloseAutoSort) }) {
                    Text(stringResource(Res.string.action_close))
                }
                return@Column
            }
            CameraPanel(
                facing = facing,
                // Every frame is sorted for the live preview; the record button decides which are saved.
                onCapture = { jpeg -> onAction(DatasetDetailAction.OnCapture(jpeg)) },
                onFlip = { onAction(DatasetDetailAction.OnFlipCamera) },
                onClose = { onAction(DatasetDetailAction.OnCloseAutoSort) },
            ) {
                PredictionStatus(autoSort, classes)
                HoldToRecordButton(
                    recording = recording,
                    enabled = enabled && autoSort.ready,
                    onStart = { onAction(DatasetDetailAction.OnStartRecording) },
                    onStop = { onAction(DatasetDetailAction.OnStopRecording) },
                )
                if (autoSort.unknowns.isNotEmpty()) {
                    UnknownTray(autoSort.unknowns, classes, enabled, onAction)
                }
                SortedSummary(autoSort.sorted, classes)
                StrictnessControl(autoSort.strictness) { onAction(DatasetDetailAction.OnStrictnessChange(it)) }
                OutlinedButton(onClick = onPickPhotos, enabled = enabled && autoSort.ready, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.auto_sort_photos), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Where the camera's current view would be saved. Fixed height, so the record button doesn't jump. */
@Composable
private fun PredictionStatus(autoSort: AutoSortUi, classes: List<ClassUi>) {
    val match = autoSort.prediction
    val closest = match?.closestClassId?.let { id -> classes.firstOrNull { it.id == id } }
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
        when {
            autoSort.preparing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (autoSort.toPrepare == 0) stringResource(Res.string.auto_sort_preparing)
                    else stringResource(Res.string.auto_sort_preparing_count, autoSort.prepared, autoSort.toPrepare),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (autoSort.toPrepare == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { autoSort.prepared.toFloat() / autoSort.toPrepare }, modifier = Modifier.fillMaxWidth())
                }
            }
            match != null && match.closestClassId == null -> Hint(stringResource(Res.string.auto_sort_no_classes))
            match == null || closest == null -> Hint(stringResource(Res.string.auto_sort_point))
            match.isUnknown -> MatchRow(
                dot = null,
                title = stringResource(Res.string.unknown_label),
                detail = stringResource(Res.string.auto_sort_closest, closest.name, match.percent()),
            )
            else -> MatchRow(
                dot = closest.colorIndex,
                title = closest.name,
                detail = stringResource(Res.string.auto_sort_similar, match.percent()),
            )
        }
    }
}

@Composable
private fun MatchRow(dot: Int?, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        if (dot != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(classColor(dot)))
        } else {
            Icon(AppIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun ClassMatch.percent(): String = "${(similarity * 100).roundToInt()}%"

/** Captured images that matched no class, with what to do with them. */
@Composable
private fun UnknownTray(
    unknowns: List<UnknownImage>,
    classes: List<ClassUi>,
    enabled: Boolean,
    onAction: (DatasetDetailAction) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var choosingClass by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    pluralStringResource(Res.plurals.unknown_title, unknowns.size, unknowns.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(stringResource(Res.string.unknown_body), style = MaterialTheme.typography.bodySmall)
            // Newest first, so the latest capture is in view.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(unknowns.asReversed(), key = { it.id }) { unknown ->
                    UnknownThumbnail(unknown, onRemove = { onAction(DatasetDetailAction.OnDiscardUnknowns(setOf(unknown.id))) }, modifier = Modifier.animateItem())
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
                                        onAction(DatasetDetailAction.OnUnknownsToClass(imageClass.id))
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { onAction(DatasetDetailAction.OnDiscardUnknowns()) }) {
                    Text(stringResource(Res.string.unknown_discard))
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
                onAction(DatasetDetailAction.OnUnknownsToNewClass(name))
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun UnknownThumbnail(unknown: UnknownImage, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, unknown.id) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeSquare(unknown.preview, UNKNOWN_THUMBNAIL_PIXELS).toImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.size(UNKNOWN_THUMBNAIL_SIZE.dp)) {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        val removeLabel = stringResource(Res.string.unknown_remove)
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).semantics { contentDescription = removeLabel },
        ) {
            Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.padding(3.dp))
        }
    }
}

/** How many images went to each class since the camera opened. */
@Composable
private fun SortedSummary(sorted: Map<Int, Int>, classes: List<ClassUi>) {
    val entries = classes.mapNotNull { c -> sorted[c.id]?.let { c to it } }
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.auto_sort_saved), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEach { (imageClass, count) ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(classColor(imageClass.colorIndex)))
                        Text("${imageClass.name} +$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

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
        Hint(stringResource(if (strictness == 0) Res.string.auto_sort_strictness_off_hint else Res.string.auto_sort_strictness_hint))
    }
}

private const val UNKNOWN_THUMBNAIL_SIZE = 56
private const val UNKNOWN_THUMBNAIL_PIXELS = 112
