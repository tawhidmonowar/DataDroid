package org.onedroid.dataset.domain.usecase

import org.onedroid.dataset.domain.EmptyResult
import org.onedroid.dataset.domain.Error
import org.onedroid.dataset.domain.Result

enum class NameError : Error {
    BLANK,
    TOO_LONG,
    DUPLICATE,
}

/** Checks a dataset or class name; [taken] are names already in use, compared ignoring case. */
fun validateName(name: String, taken: Collection<String> = emptyList()): EmptyResult<NameError> {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> Result.Error(NameError.BLANK)
        trimmed.length > MAX_NAME_LENGTH -> Result.Error(NameError.TOO_LONG)
        taken.any { it.trim().equals(trimmed, ignoreCase = true) } -> Result.Error(NameError.DUPLICATE)
        else -> Result.Success(Unit)
    }
}

/** [base] if free, else the first of "base 2", "base 3", … that is. */
fun uniqueName(base: String, taken: Collection<String>): String {
    val lower = taken.map { it.trim().lowercase() }.toSet()
    if (base.lowercase() !in lower) return base
    var n = 2
    while ("$base $n".lowercase() in lower) n++
    return "$base $n"
}

const val MAX_NAME_LENGTH = 40
