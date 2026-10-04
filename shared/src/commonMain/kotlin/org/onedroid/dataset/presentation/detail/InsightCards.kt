package org.onedroid.dataset.presentation.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.model.BalanceIssue
import org.onedroid.dataset.domain.model.ClassBalance
import org.onedroid.dataset.domain.usecase.ExportNaming
import org.onedroid.dataset.presentation.components.AppIcons
import org.onedroid.dataset.presentation.theme.classColor
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.balance_balanced
import org.onedroid.dataset.resources.balance_details
import org.onedroid.dataset.resources.balance_status_balanced
import org.onedroid.dataset.resources.balance_status_empty
import org.onedroid.dataset.resources.balance_status_tips
import org.onedroid.dataset.resources.classes_count
import org.onedroid.dataset.resources.balance_empty
import org.onedroid.dataset.resources.balance_issue_empty_classes
import org.onedroid.dataset.resources.balance_issue_few_images
import org.onedroid.dataset.resources.balance_issue_imbalanced
import org.onedroid.dataset.resources.balance_issue_two_classes
import org.onedroid.dataset.resources.balance_title
import org.onedroid.dataset.resources.export_button
import org.onedroid.dataset.resources.export_description
import org.onedroid.dataset.resources.export_empty_classes_note
import org.onedroid.dataset.resources.export_title
import org.onedroid.dataset.resources.images_count
import org.onedroid.dataset.resources.stat_average
import org.onedroid.dataset.resources.stat_classes
import org.onedroid.dataset.resources.stat_images
import kotlin.math.roundToInt

/**
 * One line, pinned above the class list on phones: image and class counts and whether the balance needs
 * attention. Tapping it opens the full [ClassBalanceContent].
 */
@Composable
fun BalanceSummaryStrip(balance: ClassBalance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(Res.plurals.images_count, balance.totalImages, balance.totalImages) + " · " +
                        pluralStringResource(Res.plurals.classes_count, balance.classes.size, balance.classes.size),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (icon, text, tint) = when {
                    balance.totalImages == 0 -> Triple(AppIcons.BarChart, stringResource(Res.string.balance_status_empty), MaterialTheme.colorScheme.onSurfaceVariant)
                    balance.isBalanced -> Triple(AppIcons.CheckCircle, stringResource(Res.string.balance_status_balanced), MaterialTheme.colorScheme.primary)
                    else -> Triple(
                        AppIcons.Warning,
                        pluralStringResource(Res.plurals.balance_status_tips, balance.issues.size, balance.issues.size),
                        MaterialTheme.colorScheme.tertiary,
                    )
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(start = 12.dp).size(18.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                Icon(AppIcons.ChevronRight, contentDescription = stringResource(Res.string.balance_details), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Images per class as bars, headline numbers, and anything worth fixing before training. */
@Composable
fun ClassBalanceCard(balance: ClassBalance, modifier: Modifier = Modifier, onClassClick: ((Int) -> Unit)? = null) {
    ElevatedCard(modifier.fillMaxWidth()) {
        CardHeader(AppIcons.BarChart, stringResource(Res.string.balance_title))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ClassBalanceContent(balance, Modifier.padding(16.dp), onClassClick)
    }
}

/** The body of [ClassBalanceCard]; with [onClassClick], tapping a class's bar reports its id. */
@Composable
fun ClassBalanceContent(balance: ClassBalance, modifier: Modifier = Modifier, onClassClick: ((Int) -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(Res.string.stat_images), balance.totalImages.toString(), Modifier.weight(1f))
            StatTile(stringResource(Res.string.stat_classes), balance.classes.size.toString(), Modifier.weight(1f))
            StatTile(stringResource(Res.string.stat_average), balance.meanCount.oneDecimal(), Modifier.weight(1f))
        }
        if (balance.totalImages == 0) {
            Text(
                stringResource(Res.string.balance_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                balance.classes.forEachIndexed { index, share ->
                    BalanceBar(
                        name = share.name,
                        count = share.count,
                        share = share.share,
                        fill = if (balance.maxCount == 0) 0f else share.count.toFloat() / balance.maxCount,
                        colorIndex = index,
                        onClick = onClassClick?.let { { it(share.classId) } },
                    )
                }
            }
        }
        if (balance.totalImages > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (balance.isBalanced) {
                    Note(AppIcons.CheckCircle, stringResource(Res.string.balance_balanced), MaterialTheme.colorScheme.primary)
                } else {
                    balance.issues.forEach { issue -> Note(AppIcons.Warning, issue.text(), MaterialTheme.colorScheme.tertiary) }
                }
            }
        }
    }
}

@Composable
private fun BalanceIssue.text(): String = when (this) {
    BalanceIssue.NeedsTwoClasses -> stringResource(Res.string.balance_issue_two_classes)
    is BalanceIssue.EmptyClasses -> stringResource(Res.string.balance_issue_empty_classes, names.joinToString(", "))
    is BalanceIssue.FewImages -> stringResource(Res.string.balance_issue_few_images, recommended, names.joinToString(", "))
    is BalanceIssue.Imbalanced -> stringResource(Res.string.balance_issue_imbalanced, largest, ratio.oneDecimal(), smallest)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun BalanceBar(name: String, count: Int, share: Float, fill: Float, colorIndex: Int, onClick: (() -> Unit)?) {
    val animatedFill by animateFloatAsState(fill, label = "balance")
    val clickable = if (onClick == null) Modifier else Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp)
    Column(clickable, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(classColor(colorIndex)))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text(
                "$count · ${(share * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animatedFill).clip(RoundedCornerShape(5.dp)).background(classColor(colorIndex)))
        }
    }
}

/** What the export will contain, and the button that writes and shares it. */
@Composable
fun ExportCard(
    datasetName: String,
    classes: List<ClassUi>,
    enabled: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        CardHeader(AppIcons.Download, stringResource(Res.string.export_title))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ExportContent(datasetName, classes, enabled, onExport, Modifier.padding(16.dp))
    }
}

/** The body of [ExportCard]. */
@Composable
fun ExportContent(
    datasetName: String,
    classes: List<ClassUi>,
    enabled: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nonEmpty = classes.filter { it.imageIds.isNotEmpty() }
    val folders = ExportNaming.classFolders(nonEmpty.map { it.name })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.export_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TreeLine("${ExportNaming.rootFolder(datasetName)}/", bold = true)
                TreeLine("${if (folders.isEmpty()) "└" else "├"}── ${ExportNaming.METADATA_FILE}")
                folders.forEachIndexed { index, folder ->
                    val branch = if (index == folders.lastIndex) "└" else "├"
                    val count = nonEmpty[index].imageIds.size
                    TreeLine("$branch── $folder/  (${pluralStringResource(Res.plurals.images_count, count, count)})")
                }
            }
        }
        if (nonEmpty.size < classes.size) {
            Text(
                stringResource(Res.string.export_empty_classes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onExport, enabled = enabled && nonEmpty.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.export_button), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A bottom sheet holding one of the insight panels, under [title]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightSheet(icon: ImageVector, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            CardHeader(icon, title)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun TreeLine(text: String, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
    }
}

private fun Float.oneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
