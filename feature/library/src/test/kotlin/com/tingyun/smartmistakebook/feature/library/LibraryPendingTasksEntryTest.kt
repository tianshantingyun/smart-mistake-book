package com.tingyun.smartmistakebook.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错题本空态旁的「待处理任务」入口（PRD-06 空态半边）。
 *
 * 消灭的失败：有待处理录入草稿时，错题本只显示"暂无错题"，学生找不到上次没处理完的题，
 * 只能重新拍一遍。入口只在真有待处理数且接了继续回调时出现——没接线就不渲染假按钮。
 */
class LibraryPendingTasksEntryTest {
    @Test
    fun pendingTasksEntryNeedsARealCountAndContinueTarget() {
        assertTrue(libraryPendingTasksEntryVisible(count = 2, hasContinue = true))
        assertFalse(libraryPendingTasksEntryVisible(count = 2, hasContinue = false))
        assertFalse(libraryPendingTasksEntryVisible(count = 0, hasContinue = true))
    }

    @Test
    fun pendingTasksEntryCopyIsExactAndAvoidsModelClaims() {
        val label = libraryPendingTasksEntryLabel(3)

        assertEquals("继续处理 3 道题", label)
        listOf("模型", "AI", "智能").forEach { claim ->
            assertFalse("入口文案不应承诺 $claim：$label", label.contains(claim))
        }
    }

    @Test
    fun emptyLibraryHintPointsToTheLocalManualEntry() {
        assertEquals("也可在录入页选择「手动建题」。", LIBRARY_EMPTY_MANUAL_HINT)
        assertTrue(LIBRARY_EMPTY_MANUAL_HINT.contains("手动建题"))
        assertFalse(LIBRARY_EMPTY_MANUAL_HINT.contains("模型"))
    }
}
