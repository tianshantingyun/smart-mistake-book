package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.StudyProjectionIssue
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 投影专用状态卡的文案合同（批次 49 P2-13）。
 *
 * 钉的是"专用"两个字：冲突与缺口必须给出**不同**的原因句（两个账本事实不同），
 * 且每个状态都带唯一动作"重新整理"与一句"不会删除作答"的后果说明——不得退回通用错误句。
 */
class StudyProjectionIssueCopyTest {

    @Test
    fun conflictAndGapExplainDifferentLedgerFacts() {
        val conflicted = studyProjectionIssueCopy(
            StudyProjectionIssue(ProjectionStatus.CONFLICTED),
        )
        val gap = studyProjectionIssueCopy(
            StudyProjectionIssue(ProjectionStatus.WAITING_FOR_GAP),
        )

        assertNotEquals(
            "冲突（矛盾记录）与缺口（缺一段）必须各说各的原因",
            conflicted.reason,
            gap.reason,
        )
        assertTrue(conflicted.reason.contains("矛盾"))
        assertTrue(gap.reason.contains("缺少"))
    }

    @Test
    fun everyIssueOffersTheSingleReprojectActionAndItsConsequence() {
        ProjectionStatus.entries
            .filter { it != ProjectionStatus.CURRENT }
            .forEach { status ->
                val copy = studyProjectionIssueCopy(StudyProjectionIssue(status))
                assertEquals("重新整理", copy.actionLabel)
                assertTrue(copy.title.isNotBlank())
                assertTrue(copy.reason.isNotBlank())
                assertTrue(
                    "后果句必须说明不会删除作答：$status -> ${copy.consequence}",
                    copy.consequence.isNotBlank() && copy.consequence.contains("不"),
                )
            }
    }

    @Test
    fun currentIsNotAnIssue() {
        val failure = runCatching { StudyProjectionIssue(ProjectionStatus.CURRENT) }
        assertTrue("CURRENT 不是一种问题，构造必须拒绝：$failure", failure.isFailure)
    }
}
