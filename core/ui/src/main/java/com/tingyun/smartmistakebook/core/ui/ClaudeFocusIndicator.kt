package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** Borders draw inside native controls, so a green button needs a different focus color. */
internal fun claudeFocusIndicatorColor(containerColor: Color): Color {
    if (containerColor.alpha == 0f) return JadeActive
    val surface = containerColor.compositeOver(Paper)
    fun contrast(color: Color): Float {
        val a = color.luminance(); val b = surface.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
    if (contrast(JadeActive) >= 3f) return JadeActive
    return if (contrast(Ink) > contrast(OnJade)) Ink else OnJade
}
