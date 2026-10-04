package org.onedroid.dataset.presentation.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.progress_exporting
import org.onedroid.dataset.resources.progress_importing
import org.onedroid.dataset.resources.progress_preparing

/** An import or export in progress; [total] is 0 while the amount of work isn't known yet. */
data class TransferUi(val kind: TransferKind, val done: Int = 0, val total: Int = 0) {
    val fraction: Float? get() = if (total == 0) null else done.toFloat() / total
}

enum class TransferKind { IMPORT, EXPORT }

@Composable
fun TransferUi.label(): String = when {
    total == 0 -> stringResource(Res.string.progress_preparing)
    kind == TransferKind.EXPORT -> stringResource(Res.string.progress_exporting, done, total)
    else -> stringResource(Res.string.progress_importing, done, total)
}
