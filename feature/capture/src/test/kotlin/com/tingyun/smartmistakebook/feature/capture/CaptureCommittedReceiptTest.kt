package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureWorkflowPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4C·B5 去重可见化在录入界面上的那一半：提交回执里的复用指示最终变成「已存入错题本」卡片上
 * 如实的一句话——新建入库就说是新建，复用了既有同一题就说是复用，两者不再混成一句"已入库"。
 */
class CaptureCommittedReceiptTest {

    @Test
    fun `the two receipt lines tell new and reused apart`() {
        val newLine = captureCommittedReceiptLine(reusedExistingProblem = false)
        val reusedLine = captureCommittedReceiptLine(reusedExistingProblem = true)

        assertNotEquals(newLine, reusedLine)
        assertTrue("新建入库那句要说清是新建：$newLine", newLine.contains("新建"))
        assertFalse("新建那句不许说复用：$newLine", newLine.contains("复用"))

        assertTrue("复用那句要说清复用了既有同一题：$reusedLine", reusedLine.contains("复用"))
        assertTrue("复用那句要说清没有重复建题：$reusedLine", reusedLine.contains("没有重复建题"))
        assertFalse("复用那句不许说新建：$reusedLine", reusedLine.contains("新建"))
    }

    @Test
    fun `a library save records the reuse receipt on the committed screen state`() {
        val state = CaptureScreenState()
        val commands = workflowEvents(state)

        commands.applySaved(
            phase = CaptureWorkflowPhase.SAVED,
            origin = CaptureEntryOrigin.LIBRARY,
            currentRevisionNumber = 1,
            savedEntryId = "entry-1",
            reusedExistingProblem = true,
        )

        assertEquals("entry-1", state.committedEntryId)
        assertTrue(
            "复用了既有同一题：卡片上的回执必须是复用那句",
            state.committedReusedExistingProblem,
        )
    }

    @Test
    fun `a library save without reuse records the new-entry receipt`() {
        val state = CaptureScreenState().apply { committedReusedExistingProblem = true }
        val commands = workflowEvents(state)

        commands.applySaved(
            phase = CaptureWorkflowPhase.SAVED,
            origin = CaptureEntryOrigin.LIBRARY,
            currentRevisionNumber = 1,
            savedEntryId = "entry-2",
            reusedExistingProblem = false,
        )

        assertEquals("entry-2", state.committedEntryId)
        assertFalse(
            "新建入库：上一笔的复用指示不许留下",
            state.committedReusedExistingProblem,
        )
    }

    @Test
    fun `a tutor save leaves no library receipt on the screen state`() {
        val state = CaptureScreenState()
        val commands = workflowEvents(state)

        commands.applySaved(
            phase = CaptureWorkflowPhase.SAVED,
            origin = CaptureEntryOrigin.TUTOR,
            currentRevisionNumber = 1,
            savedEntryId = "entry-1",
            reusedExistingProblem = false,
        )

        // 讲题会话这一步还没有入库（保存到错题本是后面用户点的那一下）：没有入库回执，
        // 也就没有任何复用/新建的话可说。
        assertNull(state.committedEntryId)
        assertFalse(state.committedReusedExistingProblem)
    }

    private fun workflowEvents(state: CaptureScreenState) = CaptureWorkflowEventCommands(
        state = state,
        draftState = CaptureDraftStateCommands(state),
        onDeleteOwnedUri = {},
        onConsumeDraftImported = {},
        onConsumeTutorSession = {},
        onTutorSessionReady = {},
    )
}
