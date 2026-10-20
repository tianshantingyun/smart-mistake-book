package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.ReviewSelfReportKey
import com.tingyun.smartmistakebook.core.domain.ReviewStoredAnswer
import com.tingyun.smartmistakebook.core.domain.StudyCatalogEntry
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-05 三键/undo/无模型路径的**界面合同**（不依赖 Compose 渲染）。
 *
 * 消灭的失败：
 * - 三键文案混同（Hard 被读成失败、Again 被塞进 Hard、或偷偷出现 Easy）；
 * - 只接了提交没接 undo 的半接线通道抢走旧机判路径，学生提交后无法撤销；
 * - "没有可信答案"（不可信）与"答案未接线"（未知）混成一句话，界面谎称题意；
 * - 提交中断后允许换键重试，制造第二笔互相矛盾的 attempt；
 * - 同一题换了 problemRevision 或跨日新会话仍复用旧 presentation，旧回执串到新题上。
 */
class ReviewSelfReportUiContractTest {

    @Test
    fun threeKeysStayDistinctAndThereIsNoEasyKey() {
        assertEquals(
            "三键就是三个评分档，多一个都会破坏 PRD-05 的裁定",
            3,
            ReviewSelfReportKey.values().size,
        )
        val presentations = ReviewSelfReportKey.values().map(::selfReportKeyPresentation)
        assertEquals(
            "三个键的标签必须互不相同",
            3,
            presentations.map { it.label }.toSet().size,
        )
        assertEquals(
            "三个键的辅助说明必须互不相同",
            3,
            presentations.map { it.supporting }.toSet().size,
        )

        val hard = selfReportKeyPresentation(ReviewSelfReportKey.HARD)
        assertEquals("卡顿", hard.label)
        assertTrue("Hard 必须是'费力但想起来了'，不能是失败", hard.supporting.contains("想起"))

        val again = selfReportKeyPresentation(ReviewSelfReportKey.AGAIN)
        assertEquals("错", again.label)
        assertTrue("Again 必须承接'没想起来/答错'", again.supporting.contains("没想起来"))

        val good = selfReportKeyPresentation(ReviewSelfReportKey.GOOD)
        assertEquals("对", good.label)
        assertTrue(good.supporting.contains("顺利"))
    }

    @Test
    fun halfWiredSelfReportChannelNeverTakesOverTheLegacyPath() {
        assertTrue(selfReportChannelReady(true, true, "session-1", 3))
        assertFalse("没有提交通道不能宣称支持自评", selfReportChannelReady(false, true, "session-1", 3))
        assertFalse("没有撤销通道的半接线会让学生提交后无法撤回", selfReportChannelReady(true, false, "session-1", 3))
        assertFalse("缺少持久会话身份时不能渲染自评", selfReportChannelReady(true, true, null, 3))
        assertFalse("空白会话身份不是有效身份", selfReportChannelReady(true, true, "  ", 3))
        assertFalse("缺少会话版本时不能渲染自评", selfReportChannelReady(true, true, "session-1", null))
        assertFalse("负版本不是有效版本", selfReportChannelReady(true, true, "session-1", -1))
    }

    @Test
    fun onlyThePendingJudgementStaysTappableAfterAFailedSubmit() {
        assertTrue(
            "首次提交前三个键都可选",
            selfReportKeyEnabled(
                key = ReviewSelfReportKey.HARD,
                selected = ReviewSelfReportKey.GOOD,
                enabled = true,
                pendingSubmit = false,
            ),
        )
        assertTrue(
            "提交失败后原判定键仍可用于重试（同一 request id）",
            selfReportKeyEnabled(
                key = ReviewSelfReportKey.GOOD,
                selected = ReviewSelfReportKey.GOOD,
                enabled = true,
                pendingSubmit = true,
            ),
        )
        assertFalse(
            "提交失败后不能悄悄换键，否则会制造第二笔互相矛盾的 attempt",
            selfReportKeyEnabled(
                key = ReviewSelfReportKey.AGAIN,
                selected = ReviewSelfReportKey.GOOD,
                enabled = true,
                pendingSubmit = true,
            ),
        )
        assertFalse(
            "提交中/已记录时全部禁用",
            selfReportKeyEnabled(
                key = ReviewSelfReportKey.GOOD,
                selected = ReviewSelfReportKey.GOOD,
                enabled = false,
                pendingSubmit = true,
            ),
        )
    }

    @Test
    fun missingStoredAnswerIsUnknownNotAnUntrustedClaim() {
        assertEquals(
            "未接线不等于'没有可信答案'——界面不得替数据层下结论",
            StoredAnswerAvailability.UNKNOWN,
            storedAnswerAvailability(null),
        )
        assertEquals(
            StoredAnswerAvailability.UNTRUSTED,
            storedAnswerAvailability(
                ReviewStoredAnswer(answerMarkdown = null, explanationMarkdown = "已有解析"),
            ),
        )
        assertEquals(
            StoredAnswerAvailability.TRUSTED,
            storedAnswerAvailability(
                ReviewStoredAnswer(answerMarkdown = "标准答案", explanationMarkdown = "解析"),
            ),
        )
    }

    @Test
    fun submitAndUndoStatesCarryDifferentMessages() {
        assertNull(
            "空闲态不该占用一行状态文案",
            selfReportStatusMessage(SelfReportStatus.IDLE, SelfReportUndoStatus.IDLE, null),
        )
        assertNotNullText(
            selfReportStatusMessage(SelfReportStatus.RECORDING, SelfReportUndoStatus.IDLE, null),
            "正在记录",
        )
        assertNotNullText(
            selfReportStatusMessage(SelfReportStatus.FAILED, SelfReportUndoStatus.IDLE, null),
            "重试",
        )

        val receipt = ReviewUndoReceipt(
            correctionId = "correction:void:1",
            created = true,
            nextPracticeUnitId = "unit-1",
        )
        val undone = selfReportStatusMessage(
            SelfReportStatus.IDLE,
            SelfReportUndoStatus.UNDONE,
            receipt,
        )
        assertNotNullText(undone, "已撤销")
        assertNotNullText(undone, receipt.correctionId)

        val replayed = selfReportStatusMessage(
            SelfReportStatus.IDLE,
            SelfReportUndoStatus.UNDONE,
            receipt.copy(created = false),
        )
        assertNotNullText(replayed, "此前已经生效")
        assertNotNullText(replayed, "不会重复回退")
        assertNotNullText(replayed, receipt.correctionId)
    }

    @Test
    fun recordedReceiptNamesTheExactJudgement() {
        val good = selfReportRecordedMessage(key = ReviewSelfReportKey.GOOD, evidenceReason = "SELF_REPORTED_RECALL")
        val hard = selfReportRecordedMessage(key = ReviewSelfReportKey.HARD, evidenceReason = "SELF_REPORTED_RECALL")
        val again = selfReportRecordedMessage(key = ReviewSelfReportKey.AGAIN, evidenceReason = "SELF_REPORTED_STUCK")

        assertTrue(good.contains("对"))
        assertTrue(hard.contains("卡顿"))
        assertTrue(again.contains("错"))
        assertNotEquals(
            "Good 与 Hard 写入同一证据口径，但界面必须让学生看见自己选的是哪一档",
            good,
            hard,
        )
        assertTrue("Good/Hard 的证据是自评回忆成功", good.contains("回忆"))
        assertTrue(again.contains("没想起来"))
    }

    @Test
    fun presentationIdentityChangesWithQuestionRevisionOrReviewSession() {
        val base = catalogEntry(revisionId = "revision-1")
        assertEquals(
            "同一题、同一会话的 presentation 必须稳定（旋转/重组不产生新 attempt 身份）",
            capturedReviewPresentationId(base, "session-1"),
            capturedReviewPresentationId(base, "session-1"),
        )
        assertNotEquals(
            "题版本变更后不能复用旧 presentation 的 attempt 身份",
            capturedReviewPresentationId(base, "session-1"),
            capturedReviewPresentationId(catalogEntry(revisionId = "revision-2"), "session-1"),
        )
        assertNotEquals(
            "跨日/新会话不能把旧会话的回执串到新题上",
            capturedReviewPresentationId(base, "session-1"),
            capturedReviewPresentationId(base, "session-2"),
        )
        assertTrue(
            "presentation 身份不能为空",
            capturedReviewPresentationId(base, "session-1").isNotBlank(),
        )
        assertEquals(
            "未接线会话身份时保持旧格式，不凭空编造会话",
            "presentation:captured-review:revision-1:unit-1",
            capturedReviewPresentationId(base, null),
        )
    }

    // ---- fixtures ----

    private fun assertNotNullText(text: String?, expectedFragment: String) {
        val value = requireNotNull(text) { "expected a status message containing $expectedFragment" }
        assertTrue("'$value' must contain '$expectedFragment'", value.contains(expectedFragment))
    }

    private fun catalogEntry(revisionId: String) = StudyCatalogEntry(
        entryId = "entry-1",
        problemId = "problem-1",
        problemRevisionId = revisionId,
        practiceUnitId = "unit-1",
        subject = "GENERAL",
        title = "标题",
        problemMarkdown = "题干",
        sourceKey = null,
        isCuratedExample = false,
        masteryStatus = MasteryStatus.UNKNOWN,
        nextReviewAtEpochMillis = null,
        retrievability = null,
    )
}
