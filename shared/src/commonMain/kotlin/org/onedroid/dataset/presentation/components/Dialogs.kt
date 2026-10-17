package org.onedroid.dataset.presentation.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.compose.resources.stringResource
import org.onedroid.dataset.domain.Result
import org.onedroid.dataset.domain.usecase.MAX_NAME_LENGTH
import org.onedroid.dataset.domain.usecase.NameError
import org.onedroid.dataset.domain.usecase.validateName
import org.onedroid.dataset.resources.Res
import org.onedroid.dataset.resources.action_cancel
import org.onedroid.dataset.resources.error_name_blank
import org.onedroid.dataset.resources.error_name_duplicate
import org.onedroid.dataset.resources.error_name_too_long
import org.onedroid.dataset.resources.label_name

fun NameError.toUiText(): UiText = when (this) {
    NameError.BLANK -> UiText.Resource(Res.string.error_name_blank)
    NameError.TOO_LONG -> UiText.Resource(Res.string.error_name_too_long, MAX_NAME_LENGTH)
    NameError.DUPLICATE -> UiText.Resource(Res.string.error_name_duplicate)
}

/**
 * Asks for a name, starting with [initial] selected so typing replaces it. Names already in [taken]
 * (other than [initial]) are rejected, as are blank or overlong ones.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    taken: Collection<String> = emptyList(),
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val others = remember(taken, initial) { taken.filterNot { it.equals(initial, ignoreCase = true) } }
    val error = (validateName(text.text, others) as? Result.Error)?.error
    // A blank field is the starting point, not a mistake worth flagging.
    val shownError = error?.takeIf { it != NameError.BLANK }
    val submit = { if (error == null) onConfirm(text.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH + 10) text = it },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
                isError = shownError != null,
                supportingText = shownError?.let { { Text(it.toUiText().asString()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = error == null) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
