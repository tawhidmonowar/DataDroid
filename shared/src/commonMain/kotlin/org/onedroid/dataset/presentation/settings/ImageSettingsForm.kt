package org.onedroid.dataset.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.model.ImageSettings
import org.onedroid.dataset.domain.model.OutputFormat
import org.onedroid.dataset.domain.model.ResizeMode
import org.onedroid.dataset.core.platform.image.canEncodeWebp
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.compression_balanced
import org.onedroid.dataset.resources.compression_high
import org.onedroid.dataset.resources.compression_label
import org.onedroid.dataset.resources.compression_small
import org.onedroid.dataset.resources.compression_unused_original
import org.onedroid.dataset.resources.compression_unused_png
import org.onedroid.dataset.resources.compression_value
import org.onedroid.dataset.resources.format_jpeg_hint
import org.onedroid.dataset.resources.format_original
import org.onedroid.dataset.resources.format_original_hint
import org.onedroid.dataset.resources.format_png_hint
import org.onedroid.dataset.resources.format_webp_hint
import org.onedroid.dataset.resources.image_format_label
import org.onedroid.dataset.resources.image_size_label
import org.onedroid.dataset.resources.resize_fit
import org.onedroid.dataset.resources.resize_fit_hint
import org.onedroid.dataset.resources.resize_original
import org.onedroid.dataset.resources.resize_original_hint
import org.onedroid.dataset.resources.resize_square
import org.onedroid.dataset.resources.resize_square_hint
import org.onedroid.dataset.resources.settings_lossy_note
import org.onedroid.dataset.resources.settings_passthrough_note
import org.onedroid.dataset.resources.size_px
import kotlin.math.roundToInt

/** Formats this device can write. */
val availableFormats: List<OutputFormat> = OutputFormat.entries.filter { it != OutputFormat.WEBP || canEncodeWebp }

private val QUALITY_PRESETS = listOf(
    Res.string.compression_high to 95,
    Res.string.compression_balanced to 85,
    Res.string.compression_small to 70,
)

/** Size, format and compression controls; shared by the app settings screen and a dataset's settings sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageSettingsForm(
    settings: ImageSettings,
    onChange: (ImageSettings) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(Res.string.image_size_label))
        Segments(
            options = ResizeMode.entries,
            selected = settings.resize,
            label = { it.label() },
            enabled = enabled,
            onSelect = { onChange(settings.copy(resize = it)) },
        )
        if (settings.resize != ResizeMode.ORIGINAL) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in ImageSettings.SIZE_PRESETS) {
                    FilterChip(
                        selected = settings.size == size,
                        onClick = { onChange(settings.copy(size = size)) },
                        label = { Text(stringResource(Res.string.size_px, size)) },
                        enabled = enabled,
                    )
                }
            }
        }
        Hint(
            when (settings.resize) {
                ResizeMode.ORIGINAL -> stringResource(Res.string.resize_original_hint)
                ResizeMode.FIT -> stringResource(Res.string.resize_fit_hint, settings.size)
                ResizeMode.SQUARE -> stringResource(Res.string.resize_square_hint, settings.size)
            }
        )

        SectionLabel(stringResource(Res.string.image_format_label), Modifier.padding(top = 12.dp))
        Segments(
            options = availableFormats,
            selected = settings.format,
            label = { it.label() },
            enabled = enabled,
            onSelect = { onChange(settings.copy(format = it)) },
        )
        Hint(
            when (settings.format) {
                OutputFormat.ORIGINAL -> stringResource(Res.string.format_original_hint)
                OutputFormat.JPEG -> stringResource(Res.string.format_jpeg_hint)
                OutputFormat.PNG -> stringResource(Res.string.format_png_hint)
                OutputFormat.WEBP -> stringResource(Res.string.format_webp_hint)
            }
        )

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(Res.string.compression_label), Modifier.weight(1f))
            if (settings.usesQuality) {
                Text(stringResource(Res.string.compression_value, settings.quality), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (settings.usesQuality) {
            Slider(
                value = settings.quality.toFloat(),
                onValueChange = { onChange(settings.copy(quality = (it / QUALITY_STEP).roundToInt() * QUALITY_STEP)) },
                valueRange = ImageSettings.MIN_QUALITY.toFloat()..100f,
                steps = (100 - ImageSettings.MIN_QUALITY) / QUALITY_STEP - 1,
                enabled = enabled,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, quality) in QUALITY_PRESETS) {
                    FilterChip(
                        selected = settings.quality == quality,
                        onClick = { onChange(settings.copy(quality = quality)) },
                        label = { Text(stringResource(label)) },
                        enabled = enabled,
                    )
                }
            }
        } else {
            Hint(
                stringResource(
                    if (settings.format == OutputFormat.PNG) Res.string.compression_unused_png else Res.string.compression_unused_original
                )
            )
        }

        Text(
            stringResource(if (settings.isPassthrough) Res.string.settings_passthrough_note else Res.string.settings_lossy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** A short description such as "Square 224 px · JPEG · quality 85", for confirmations. */
@Composable
fun ImageSettings.summary(): String = buildList {
    add(if (resize == ResizeMode.ORIGINAL) resize.label() else resize.label() + " " + stringResource(Res.string.size_px, size))
    add(format.label())
    if (usesQuality) add(stringResource(Res.string.compression_label) + " " + quality)
}.joinToString(" · ")

@Composable
private fun ResizeMode.label(): String = stringResource(
    when (this) {
        ResizeMode.ORIGINAL -> Res.string.resize_original
        ResizeMode.FIT -> Res.string.resize_fit
        ResizeMode.SQUARE -> Res.string.resize_square
    }
)

@Composable
private fun OutputFormat.label(): String = when (this) {
    OutputFormat.ORIGINAL -> stringResource(Res.string.format_original)
    OutputFormat.JPEG -> "JPEG"
    OutputFormat.PNG -> "PNG"
    OutputFormat.WEBP -> "WebP"
}

@Composable
private fun <T> Segments(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val QUALITY_STEP = 5
