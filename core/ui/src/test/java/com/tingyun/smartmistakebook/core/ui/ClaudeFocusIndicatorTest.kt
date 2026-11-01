package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeFocusIndicatorTest {
    @Test fun focusContrastsWithTheActualOpaqueOrTintedControlSurface() {
        val surfaces = listOf(Paper, ContentSurface, SubtleSurface, Jade, JadeActive, JadeSoft,
            ErrorText, Color.Black, Color.Gray, Jade.copy(alpha = .5f), Ink.copy(alpha = .06f))
        for (surface in surfaces) {
            val foreground = claudeFocusIndicatorColor(surface).luminance()
            val background = surface.compositeOver(Paper).luminance()
            val ratio = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
            assertTrue("Focus must contrast with $surface; ratio=$ratio", ratio >= 3f)
        }
    }

    @Test fun transparentControlsRemainVisibleOnLightAndDarkNeutralSurfaces() {
        val foreground = claudeFocusIndicatorColor(Color.Transparent).luminance()
        for (surface in listOf(Paper, ContentSurface, Color(0xFF0A0A0A))) {
            val background = surface.luminance()
            val ratio = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
            assertTrue("Focus must remain visible on $surface; ratio=$ratio", ratio >= 3f)
        }
    }
}
