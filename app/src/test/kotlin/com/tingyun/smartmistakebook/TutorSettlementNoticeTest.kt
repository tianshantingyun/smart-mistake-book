package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.TutorJudgedReviewSettlementStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 讲题判定结算的可见状态（KD-36）：NO_VERDICT / 异常都不再静默。
 *
 * 这条只管"复习项上该说什么、什么时候说"——它是纯判据，所以能在 JVM 上钉住：
 * 结算成功不说；没有判定且学生去过讲题页才说；写入失败（此前被 `getOrNull()` 吞掉）
 * 无论何时都说，并且每种"要说"的状态都带着一个可点击的重试出口。
 */
class TutorSettlementNoticeTest {
    @Test
    fun aSettledReviewItemSaysNothing() {
        assertNull(
            tutorSettlementNoticeFor(
                failed = false,
                status = TutorJudgedReviewSettlementStatus.RECORDED,
                tutorVisitedForItem = true,
            ),
        )
    }

    @Test
    fun aVerdictlessTutorVisitSaysThereIsNoVerdictAndOffersRetry() {
        val notice = requireNotNull(
            tutorSettlementNoticeFor(
                failed = false,
                status = TutorJudgedReviewSettlementStatus.NO_VERDICT,
                tutorVisitedForItem = true,
            ),
        )
        assertTrue("还没有形成判定" in notice.message)
        assertTrue(notice.retryLabel.isNotBlank())
    }

    @Test
    fun theFirstEntryDoesNotClaimThatNoVerdictWasFormed() {
        // 第一次进来时讲题还没发生：把"没有判定"当状态显示出来只会是噪声。
        assertNull(
            tutorSettlementNoticeFor(
                failed = false,
                status = TutorJudgedReviewSettlementStatus.NO_VERDICT,
                tutorVisitedForItem = false,
            ),
        )
    }

    @Test
    fun aSwallowedSettlementFailureBecomesAVisibleRetryableState() {
        val notice = requireNotNull(
            tutorSettlementNoticeFor(
                failed = true,
                status = null,
                tutorVisitedForItem = false,
            ),
        )
        // 失败是事实，和"学生去没去过讲题页"无关：必须说出来，并且有重试出口。
        assertTrue(notice.message.isNotBlank())
        assertTrue(notice.retryLabel.isNotBlank())
        // 文案面向学生，不夹内部术语。
        listOf("Exception", "null", "Room").forEach { internalTerm ->
            assertFalse(internalTerm in notice.message)
        }
    }
}
