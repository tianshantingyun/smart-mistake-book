package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSemanticContrastTest {
    @Test fun normalSemanticTextHasAtLeastFourPointFiveContrast() {
        val backgrounds = listOf(SmartColors.Paper, SmartColors.ContentSurface, SmartColors.SubtleSurface,
            SmartColors.ReadingSurface, SmartColors.LearningSurface, SmartColors.PendingSurface)
        val foregrounds = listOf(SmartColors.Ink, SmartColors.InkSecondary, SmartColors.InkMuted, SmartColors.ErrorText)
        for (background in backgrounds) {
            for (foreground in foregrounds) {
                assertTrue("Normal information must remain readable on its surface", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    @Test fun primaryActionTextIsReadable() {
        assertTrue(contrast(SmartColors.OnJade, SmartColors.Jade) >= 4.5f)
    }

    @Test fun forestHeaderAndInverseActionTextAreReadable() {
        assertTrue(contrast(SmartColors.OnJade, SmartColors.Forest) >= 4.5f)
    }

    @Test fun codeAndItsToolbarUseReadableTextOnBothDarkSurfaces() {
        assertTrue(contrast(SmartColors.OnCode, SmartColors.CodeSurface) >= 4.5f)
        assertTrue(contrast(SmartColors.OnCode, SmartColors.CodeHeader) >= 4.5f)
    }

    @Test fun supportingAccentsAreReadableOnTheirOwnSurfaces() {
        for ((foreground, background) in listOf(
            SmartColors.ReadingAccent to SmartColors.ReadingSurface,
            SmartColors.LearningAccent to SmartColors.LearningSurface,
            SmartColors.PendingAccent to SmartColors.PendingSurface,
        )) assertTrue("Supporting colors must stay readable", contrast(foreground, background) >= 4.5f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
