package com.tingyun.smartmistakebook.core.ui

import androidx.compose.ui.graphics.luminance
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionAndMasteryVisualTest {
    @Test fun pressTravelRemainsSmallForEveryControlSize() {
        for (edge in listOf(24f, 48f, 120f, 320f, 600f)) {
            val scale = ClaudeMotion.pressedScale(edge)
            assertTrue(scale in .96f..1f)
            assertTrue("Wide controls must not jump", edge * (1f - scale) <= 2.001f)
        }
        assertTrue(ClaudeMotion.pressedScale(320f) > ClaudeMotion.pressedScale(48f))
    }

    @Test fun unmeasuredControlsAndInvalidEstimatesStayStable() {
        for (edge in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, ClaudeMotion.pressedScale(edge), 0f)
        }
        assertEquals(0f, normalizedMasteryProgress(Float.NaN), 0f)
        assertEquals(0f, normalizedMasteryProgress(-1f), 0f)
        assertEquals(1f, normalizedMasteryProgress(2f), 0f)
    }

    @Test fun masteryStatesRemainDistinctAndReadable() {
        assertTrue(masteryStatusColor(MasteryStatus.MASTERED) != masteryStatusColor(MasteryStatus.LEARNING))
        assertTrue(masteryStatusColor(MasteryStatus.UNKNOWN) != masteryStatusColor(MasteryStatus.MASTERED))
        for (state in MasteryStatus.entries) {
            val a = masteryStatusColor(state).luminance()
            val b = SmartColors.ContentSurface.luminance()
            assertTrue("Status labels must remain readable", (b + .05f) / (a + .05f) >= 4.5f)
        }
    }

    @Test fun aggregateEstimateColorsUseTheExistingDisplayBands() {
        assertEquals(masteryStatusColor(MasteryStatus.MASTERED), masteryEstimateColor(MASTERY_STRONG_THRESHOLD))
        assertEquals(masteryStatusColor(MasteryStatus.LEARNING), masteryEstimateColor(MASTERY_FAIR_THRESHOLD))
        assertEquals(masteryStatusColor(MasteryStatus.CONFLICTED), masteryEstimateColor(MASTERY_FAIR_THRESHOLD - .01))
        assertEquals(masteryStatusColor(MasteryStatus.UNKNOWN), masteryEstimateColor(Double.NaN))
    }
}
