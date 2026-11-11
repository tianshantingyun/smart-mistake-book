package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSemanticContrastTest {
    @Test fun normalSemanticTextHasAtLeastFourPointFiveContrast() {
        val backgrounds = listOf(SmartColors.Paper, SmartColors.ContentSurface, SmartColors.SubtleSurface)
        val foregrounds = listOf(SmartColors.Ink, SmartColors.InkSecondary, SmartColors.ErrorText)
        for (background in backgrounds) {
            for (foreground in foregrounds) {
                assertTrue("Normal information must remain readable on its surface", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    @Test fun primaryActionTextIsReadable() {
        assertTrue(contrast(SmartColors.OnJade, SmartColors.Jade) >= 4.5f)
    }

    @Test fun editorialSurfacesKeepReadableTextAndQuietInputBoundaries() {
        for (surface in listOf(SmartColors.QuestionSurface, SmartColors.FigureSurface, SmartColors.ReadingSurface)) {
            assertTrue(contrast(SmartColors.Ink, surface) >= 4.5f)
            assertTrue(contrast(SmartColors.InkSecondary, surface) >= 4.5f)
        }
        assertTrue(contrast(SmartColors.ReadingAccent, SmartColors.ReadingSurface) >= 4.5f)
        assertTrue(SmartColors.InputOutline.luminance() > SmartColors.Outline.luminance())
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
