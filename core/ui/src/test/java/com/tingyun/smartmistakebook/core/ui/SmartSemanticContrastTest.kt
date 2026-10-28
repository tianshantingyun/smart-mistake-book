package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSemanticContrastTest {
    @Test fun normalSemanticTextHasAtLeastFourPointFiveContrast() {
        val backgrounds = listOf(SmartColors.Paper, SmartColors.ContentSurface, SmartColors.SubtleSurface,
            SmartColors.ReadingSurface, SmartColors.LearningSurface, SmartColors.PendingSurface,
            SmartColors.HeaderSurface, SmartColors.ErrorSurface,
            SmartLightColorScheme.surfaceContainerHigh, SmartLightColorScheme.surfaceContainerHighest)
        val foregrounds = listOf(SmartColors.Ink, SmartColors.InkSecondary, SmartColors.InkMuted, SmartColors.ErrorText)
        for (background in backgrounds) {
            for (foreground in foregrounds) {
                assertTrue("Normal information must remain readable on its surface", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    @Test fun primaryActionTextIsReadable() {
        for (surface in listOf(SmartColors.Jade, SmartColors.JadeActive, SmartColors.JadeDark)) {
            assertTrue(contrast(SmartColors.OnJade, surface) >= 4.5f)
        }
    }

    @Test fun lightHeaderTitleSubtitleAndActionsAreReadable() {
        for (foreground in listOf(SmartColors.HeaderInk, SmartColors.HeaderSecondary)) {
            assertTrue(contrast(foreground, SmartColors.HeaderSurface) >= 4.5f)
        }
    }

    @Test fun inputOutlinesAreVisibleAgainstTheActualControlSurfaces() {
        for (surface in listOf(SmartColors.ContentSurface, SmartColors.Paper, SmartColors.SubtleSurface)) {
            assertTrue("Input boundaries must remain visible", contrast(SmartColors.Outline, surface) >= 3f)
        }
    }

    @Test fun materialComponentsUseReadableSemanticPairs() {
        with(SmartLightColorScheme) {
            for ((foreground, background) in listOf(
                onPrimary to primary, onPrimaryContainer to primaryContainer,
                onSecondary to secondary, onSecondaryContainer to secondaryContainer,
                onTertiary to tertiary, onTertiaryContainer to tertiaryContainer,
                onError to error, onErrorContainer to errorContainer,
                onSurface to surface, onSurfaceVariant to surfaceVariant,
                inverseOnSurface to inverseSurface, inversePrimary to inverseSurface,
            )) assertTrue("Material color roles must stay readable", contrast(foreground, background) >= 4.5f)
        }
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
