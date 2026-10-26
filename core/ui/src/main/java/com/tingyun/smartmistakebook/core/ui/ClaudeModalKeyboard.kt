package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Android Dialog handles Back; keyboard Escape follows the same caller-owned dismissal policy. */
fun Modifier.claudeDismissOnEscape(enabled: Boolean = true, onDismiss: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (enabled && event.key == Key.Escape) {
            if (event.type == KeyEventType.KeyUp) onDismiss()
            true
        } else false
    }
