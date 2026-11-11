package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.data.study.ErrorCauseReviewWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 50 / 5c：错因面板写回执的文案契约（JVM）。
 *
 * 来源是设备走查发现的真实缺陷：撤销成功后面板仍显示"已记下"（同一个 APPLIED 文案被套在
 * 两个相反动作上）。这里把两类动作的文案分开钉住，避免回归。
 */
class ErrorCauseWriteMessageTest {

    @Test
    fun `confirm and undo never share the same success sentence`() {
        val confirm = errorCauseWriteMessage(ErrorCauseReviewWriteResult.APPLIED, ErrorCauseAction.CONFIRM)
        val undo = errorCauseWriteMessage(ErrorCauseReviewWriteResult.APPLIED, ErrorCauseAction.UNDO)
        assertNotEquals("撤销成功不能说成确认成功", confirm, undo)
        assertTrue("撤销回执必须说清是撤销", undo.contains("撤销"))
        assertTrue("确认回执必须说清已记下", confirm.contains("已记下"))
    }

    @Test
    fun `neither sentence promises a mastery change`() {
        listOf(ErrorCauseAction.CONFIRM, ErrorCauseAction.UNDO).forEach { action ->
            val message = errorCauseWriteMessage(ErrorCauseReviewWriteResult.APPLIED, action)
            assertTrue(
                "错因不回写掌握度，文案不得暗示掌握度变化：$message",
                !message.contains("掌握度会") && !message.contains("提高掌握"),
            )
        }
    }

    @Test
    fun `failure outcomes are told apart instead of collapsing to one sentence`() {
        val conflict = errorCauseWriteMessage(ErrorCauseReviewWriteResult.CONFLICT, ErrorCauseAction.CONFIRM)
        val missing = errorCauseWriteMessage(ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND, ErrorCauseAction.CONFIRM)
        val gone = errorCauseWriteMessage(ErrorCauseReviewWriteResult.NOT_FOUND, ErrorCauseAction.CONFIRM)
        val unknown = errorCauseWriteMessage(null, ErrorCauseAction.CONFIRM)
        assertEquals(
            "四种失败态必须是四句不同的话",
            4,
            setOf(conflict, missing, gone, unknown).size,
        )
        assertTrue("未写入时不得声称成功", listOf(conflict, missing, gone, unknown).none {
            it.contains("已记下") || it.contains("已撤销")
        })
    }
}
