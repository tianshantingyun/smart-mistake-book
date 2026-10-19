package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.StudyReviewOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRoutePolicyTest {
    @Test
    fun savedQuestionsShowTodaysPlanBeforeAnyLearningEvidenceExists() {
        assertEquals(
            ReviewSurfaceState.NORMAL,
            resolveReviewSurfaceState(
                scheduledCount = 1,
                activeSessionId = null,
                completedToday = false,
                hasLearningEvidence = false,
                savedQuestionCount = 1,
                pendingFailedTaskCount = 0,
            ),
        )
    }

    @Test
    fun anEmptyProfileDoesNotShowAReviewSummary() {
        assertNotEquals(
            ReviewSurfaceState.NORMAL,
            resolveReviewSurfaceState(
                scheduledCount = 0,
                activeSessionId = null,
                completedToday = false,
                hasLearningEvidence = false,
                savedQuestionCount = 0,
                pendingFailedTaskCount = 0,
            ),
        )
    }

    @Test
    fun completingThePlanIsTheOnlyActionNeededToRecordContinuity() {
        assertEquals(
            "今天已完成 · 连续复习 3 天",
            reviewContinuityText(
                StudyReviewOverview(
                    completedToday = true,
                    completionStreakDays = 3,
                ),
            ),
        )
        assertEquals(
            "连续复习 3 天 · 完成今天的安排后自动记录",
            reviewContinuityText(StudyReviewOverview(completionStreakDays = 3)),
        )
        assertNull(reviewContinuityText(StudyReviewOverview()))
    }

    // PRD-06 空态半边四态：空库 / 仅历史题（有题无到期）/ 待处理失败 / 正常。
    // 文案测试只断言"说了什么"，不断言模型能力——本轨不引入任何模型调用。

    @Test
    fun firstInstallEmptyLibraryOffersCaptureWithoutModelClaims() {
        val state = resolveReviewSurfaceState(
            scheduledCount = 0,
            activeSessionId = null,
            completedToday = false,
            hasLearningEvidence = false,
            savedQuestionCount = 0,
            pendingFailedTaskCount = 0,
        )

        assertEquals(ReviewSurfaceState.EMPTY_LIBRARY, state)
        val copy = requireNotNull(reviewStateCopy(state))
        assertEquals("还没有可复习的题", copy.title)
        assertEquals("录入第一道题", copy.actionLabel)
        assertTrue(copy.supportingText.contains("手动输入题面"))
        assertFalse(copy.supportingText.contains("拍照"))
    }

    @Test
    fun savedQuestionsWithoutDueAreNotReportedAsNoLearningRecord() {
        val state = resolveReviewSurfaceState(
            scheduledCount = 0,
            activeSessionId = null,
            completedToday = false,
            hasLearningEvidence = false,
            savedQuestionCount = 3,
            pendingFailedTaskCount = 0,
        )

        assertEquals(ReviewSurfaceState.NO_DUE_QUESTIONS, state)
        val copy = requireNotNull(reviewStateCopy(state))
        assertEquals("今天没有到期的题", copy.title)
        assertNotEquals("暂无学习记录", copy.title)
        // "有题无到期"不是学习记录为空，说明文字里也不得混入该说法。
        assertFalse(copy.supportingText.contains("学习记录"))
        assertNull(copy.actionLabel)

        // 快照没接题数但确有历史证据时，同样按"有题无到期"呈现，而不是"暂无学习记录"。
        assertEquals(
            ReviewSurfaceState.NO_DUE_QUESTIONS,
            resolveReviewSurfaceState(
                scheduledCount = 0,
                activeSessionId = null,
                completedToday = false,
                hasLearningEvidence = true,
                savedQuestionCount = null,
                pendingFailedTaskCount = 0,
            ),
        )

        // 待引入的新题也是"已保存的题"：不能因为还没学习证据就显示成"暂无学习记录"。
        assertEquals(
            ReviewSurfaceState.NO_DUE_QUESTIONS,
            resolveReviewSurfaceState(
                scheduledCount = 0,
                activeSessionId = null,
                completedToday = false,
                hasLearningEvidence = false,
                savedQuestionCount = null,
                pendingFailedTaskCount = 0,
                intakeBacklogCount = 45,
            ),
        )
    }

    @Test
    fun failedPendingTasksKeepTheContinueEntry() {
        val state = resolveReviewSurfaceState(
            scheduledCount = 0,
            activeSessionId = null,
            completedToday = false,
            hasLearningEvidence = false,
            savedQuestionCount = 0,
            pendingFailedTaskCount = 2,
        )

        assertEquals(ReviewSurfaceState.PENDING_FAILED_TASKS, state)
        val copy = requireNotNull(reviewStateCopy(state, pendingFailedTaskCount = 2))
        assertEquals("继续处理", copy.actionLabel)
        assertTrue(copy.supportingText.contains("2 道"))
        assertTrue(copy.supportingText.contains("本机"))
        // 继续入口不能只存在于文案：没有接回调时界面不渲染它。
        assertTrue(reviewPendingTasksEntryVisible(count = 2, hasContinue = true))
        assertFalse(reviewPendingTasksEntryVisible(count = 2, hasContinue = false))
        assertFalse(reviewPendingTasksEntryVisible(count = 0, hasContinue = true))
    }

    @Test
    fun scheduledReviewsStayInTheNormalSummaryState() {
        val state = resolveReviewSurfaceState(
            scheduledCount = 3,
            activeSessionId = null,
            completedToday = false,
            hasLearningEvidence = false,
            savedQuestionCount = 3,
            pendingFailedTaskCount = 0,
        )

        assertEquals(ReviewSurfaceState.NORMAL, state)
        assertNull(reviewStateCopy(state))

        // 进行中的会话与今日已完成也属于"正常"，不能被空态覆盖。
        assertEquals(
            ReviewSurfaceState.NORMAL,
            resolveReviewSurfaceState(
                scheduledCount = 0,
                activeSessionId = "session-1",
                completedToday = false,
                hasLearningEvidence = true,
                savedQuestionCount = 3,
                pendingFailedTaskCount = 0,
            ),
        )
        assertEquals(
            ReviewSurfaceState.NORMAL,
            resolveReviewSurfaceState(
                scheduledCount = 0,
                activeSessionId = null,
                completedToday = true,
                hasLearningEvidence = true,
                savedQuestionCount = 3,
                pendingFailedTaskCount = 0,
            ),
        )
    }

    @Test
    fun failedPendingTasksRemainReachableWhenThePlanIsNormal() {
        assertEquals(
            ReviewSurfaceState.NORMAL,
            resolveReviewSurfaceState(
                scheduledCount = 2,
                activeSessionId = null,
                completedToday = false,
                hasLearningEvidence = true,
                savedQuestionCount = 4,
                pendingFailedTaskCount = 2,
            ),
        )
        assertTrue(reviewPendingTasksEntryVisible(count = 2, hasContinue = true))
    }

    @Test
    fun anUnloadedSnapshotDoesNotClaimTheLibraryIsEmpty() {
        val state = resolveReviewSurfaceState(
            scheduledCount = 0,
            activeSessionId = null,
            completedToday = false,
            hasLearningEvidence = false,
            savedQuestionCount = null,
            pendingFailedTaskCount = 0,
        )

        // 题数未知时保留既有"暂无学习记录"说法，不得谎称空库。
        assertEquals(ReviewSurfaceState.UNKNOWN, state)
        assertEquals("暂无学习记录", requireNotNull(reviewStateCopy(state)).title)
    }

    @Test
    fun everyReviewStateCopyAvoidsModelCapabilityClaims() {
        ReviewSurfaceState.entries.forEach { state ->
            val copy = reviewStateCopy(state, pendingFailedTaskCount = 1) ?: return@forEach
            val text = copy.title + copy.supportingText + copy.actionLabel.orEmpty()
            listOf("模型", "AI", "智能").forEach { claim ->
                assertFalse("$state 文案不应承诺 $claim：$text", text.contains(claim))
            }
        }
    }

    @Test
    fun knowledgeEntryIsDrivenByTheComputedCountNotTheMistakePlan() {
        // 入口显隐由 app 层实算的 knowledgeReviewCount 决定（见 ReviewRoute 的渲染条件）：
        // 有今日错题计划但知识点全无材料可出题（count=null/0）时不显示入口；
        // 即使错题复习已完成，只要还有可复习知识点，入口仍在（两者独立、自由选）。
        assertTrue(knowledgeReviewEntryVisible(count = 3, hasStartCallback = true))
        assertFalse(knowledgeReviewEntryVisible(count = 0, hasStartCallback = true))
        assertFalse(knowledgeReviewEntryVisible(count = null, hasStartCallback = true))
        assertFalse(knowledgeReviewEntryVisible(count = 3, hasStartCallback = false))
    }
}
