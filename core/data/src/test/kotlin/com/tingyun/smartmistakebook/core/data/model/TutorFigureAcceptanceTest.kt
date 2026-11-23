package com.tingyun.smartmistakebook.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地视觉验收门（READ_FIGURE）：只判机器可证伪的失败，非 PASS 必须给出具体原因。
 *
 * 判别格：空白 → BLANK；内容过少 / 对比度低 / 贴边 → REVIEW + 对应 reason；正常图 → PASS。
 * 这些用例用纯像素摘要跑，不依赖设备解码。
 */
class TutorFigureAcceptanceTest {
    private fun summary(
        width: Int = 100,
        height: Int = 100,
        minLuma: Int = 20,
        maxLuma: Int = 240,
        inkPixels: Int = 900,
        left: Int = 20,
        top: Int = 20,
        rightExclusive: Int = 80,
        bottomExclusive: Int = 80,
        hasInkBounds: Boolean = true,
    ) = FigurePreviewSummary(
        width = width,
        height = height,
        minLuma = minLuma,
        maxLuma = maxLuma,
        inkPixels = inkPixels,
        inkLeft = left,
        inkTop = top,
        inkRightExclusive = rightExclusive,
        inkBottomExclusive = bottomExclusive,
        hasInkBounds = hasInkBounds,
    )

    @Test
    fun aBlankImageIsRejectedLocallyBeforeAnyModelJudgment() {
        val (status, reasons) = figureAcceptance(summary(inkPixels = 0, hasInkBounds = false))
        assertEquals("BLANK", status)
        assertEquals(listOf("NO_INK"), reasons)
    }

    @Test
    fun everyMachineCheckableDefectHasItsOwnReason() {
        val (sparseStatus, sparseReasons) = figureAcceptance(summary(inkPixels = 10))
        assertEquals("REVIEW", sparseStatus)
        assertTrue("SPARSE_INK" in sparseReasons)

        val (contrastStatus, contrastReasons) =
            figureAcceptance(summary(minLuma = 230, maxLuma = 240))
        assertEquals("REVIEW", contrastStatus)
        assertTrue("LOW_CONTRAST" in contrastReasons)

        val (edgeStatus, edgeReasons) = figureAcceptance(
            summary(left = 0, top = 0, rightExclusive = 100, bottomExclusive = 100),
        )
        assertEquals("REVIEW", edgeStatus)
        assertTrue("EDGE_CLIPPED" in edgeReasons)
    }

    @Test
    fun aCleanImagePassesWithNoReasons() {
        val (status, reasons) = figureAcceptance(summary())
        assertEquals("PASS", status)
        assertTrue(reasons.isEmpty())
    }
}
