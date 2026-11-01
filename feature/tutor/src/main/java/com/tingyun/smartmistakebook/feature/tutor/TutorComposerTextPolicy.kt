package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun boundedTutorInput(value: TextFieldValue, maxChars: Int): TextFieldValue {
    require(maxChars >= 0)
    // The IME owns its active candidate. Apply the bound after it commits.
    if (value.composition != null || value.text.length <= maxChars) return value
    var end = maxChars
    if (end > 0 && Character.isHighSurrogate(value.text[end - 1]) &&
        Character.isLowSurrogate(value.text[end])) end--
    return TextFieldValue(
        text = value.text.take(end),
        selection = TextRange(value.selection.start.coerceIn(0, end), value.selection.end.coerceIn(0, end)),
    )
}
