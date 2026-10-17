package org.onedroid.dataset.presentation.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A user-facing string that is either already formatted or still a resource to resolve. */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    class Resource(val id: StringResource, vararg val args: Any) : UiText

    class Plural(val id: PluralStringResource, val quantity: Int, vararg val args: Any) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is Resource -> stringResource(id, *args)
        is Plural -> pluralStringResource(id, quantity, *args)
    }

    suspend fun asStringAsync(): String = when (this) {
        is DynamicString -> value
        is Resource -> getString(id, *args)
        is Plural -> getPluralString(id, quantity, *args)
    }
}
