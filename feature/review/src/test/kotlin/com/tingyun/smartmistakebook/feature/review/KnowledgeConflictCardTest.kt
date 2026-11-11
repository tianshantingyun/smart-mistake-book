package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.model.ReviewReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冲突 KC 卡（K5）的 JVM 合同（批次 49 P2-14；`docs/research/2026-11-08-clarification-ux.md`
 * §4.8 与 §5.1 的 CX-E01/CX-E02）。
 *
 * 判据只测**可观测的模型**，不渲染 Compose（`feature/review` 没有 compose-ui-test）：
 * - 动作数 ≤ 2（K5 有两动作，不是五动作的问卷）；
 * - 必选动作 = 0（卡不阻断，默认继续复习）；
 * - 触发只认 `CONFLICTED_KNOWLEDGE`，不认 `CALIBRATION_CHECK`（后者对每个薄弱点都会出现，
 *   拿它当判据会变成"每节点一张卡"，正是 K5 要避免的打断）；
 * - 无冲突节点没有卡（回归）。
 */
class KnowledgeConflictCardTest {

    @Test
    fun aConflictedNodeOffersExactlyTwoOptionalActions() {
        val model = requireNotNull(
            knowledgeConflictCardModel(entry(setOf(ReviewReason.CONFLICTED_KNOWLEDGE))),
        )

        assertEquals("冲突卡只能有 2 个动作（先做校准题 / 仍然复习）", 2, model.actions.size)
        assertEquals(
            listOf(
                KnowledgeConflictCardAction.START_CALIBRATION,
                KnowledgeConflictCardAction.CONTINUE_REVIEW,
            ),
            model.actions,
        )
        assertEquals("冲突卡不得要求任何必点动作", 0, model.requiredTaps)
        assertEquals(
            "默认动作必须是仍然复习（冲突 KC 默认仍可选，不硬排除）",
            KnowledgeConflictCardAction.CONTINUE_REVIEW,
            model.defaultAction,
        )
        assertTrue(model.title.isNotBlank())
        assertTrue(model.reason.isNotBlank())
    }

    @Test
    fun aConflictedNodeStaysSelectableInTodayPlan() {
        val conflicted = entry(setOf(ReviewReason.CONFLICTED_KNOWLEDGE))
        val plan = KnowledgeReviewSessionPlan(queue = listOf(conflicted))

        assertEquals("冲突点不得被排程硬排除：它仍是一条正常队列项", 1, plan.queue.size)
        assertNotNull(knowledgeConflictCardModel(plan.queue.single()))
        assertFalse(plan.queue.single().knowledgeNodeId.isBlank())
    }

    @Test
    fun aNodeWithoutConflictEvidenceHasNoConflictCard() {
        assertNull(
            "无冲突证据的节点不得出现冲突卡",
            knowledgeConflictCardModel(entry(emptySet())),
        )
    }

    @Test
    fun calibrationCheckAloneDoesNotTriggerTheConflictCard() {
        assertNull(
            "CALIBRATION_CHECK 对未知/过期/薄弱点都会出现；拿它当触发判据会让每个节点都弹卡",
            knowledgeConflictCardModel(entry(setOf(ReviewReason.CALIBRATION_CHECK))),
        )
        assertNull(
            knowledgeConflictCardModel(
                entry(setOf(ReviewReason.CALIBRATION_CHECK, ReviewReason.STALE_KNOWLEDGE)),
            ),
        )
    }

    @Test
    fun theConflictTriggerComesFromThePlannerReasons() {
        val conflicted = entry(
            setOf(ReviewReason.CONFLICTED_KNOWLEDGE, ReviewReason.CALIBRATION_CHECK),
        )

        assertTrue(conflicted.isConflicted)
        assertNotNull(knowledgeConflictCardModel(conflicted))
        assertNull(knowledgeConflictCardModel(conflicted.copy(reasons = emptySet())))
    }

    @Test
    fun theConsequenceCopyDoesNotPromiseAnExtraQuestion() {
        val model = requireNotNull(
            knowledgeConflictCardModel(entry(setOf(ReviewReason.CONFLICTED_KNOWLEDGE))),
        )

        assertFalse(
            "旧文案「校准题只多问一道」承诺了实现不存在的第二道题：题面仍由 " +
                "KnowledgeReviewQuizLoader.loadQuiz(entry) 现场生成",
            model.consequence.contains("多问一道"),
        )
        assertFalse(
            "理由句也不得承诺会新出一道校准题",
            model.reason.contains("一道校准题"),
        )
        assertTrue(
            "真实后果必须说清：作答仍计入掌握度、不把这次复习算作完成",
            model.consequence.contains("作答仍计入掌握度") &&
                model.consequence.contains("不会把这次复习算作完成"),
        )
    }

    private fun entry(reasons: Set<ReviewReason>) = KnowledgeReviewQueueEntry(
        knowledgeNodeId = "kc-confl",
        subject = "MATH",
        displayName = "冲突的知识点",
        masteryScore = 0.3,
        lastEvidenceAtEpochMillis = 1_000L,
        reasons = reasons,
    )
}
