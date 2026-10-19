package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.MasteryWriteGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 掌握度回写被门控拒收时的学生文案：内部枚举名必须被翻译成人话，
 * 且映射要覆盖 MasteryWriteGate 的全部拒收原因（新增枚举时这里先红）。
 */
class KnowledgeQuizRejectedReasonTextTest {

    @Test
    fun everyRejectReasonMapsToAReadableSentence() {
        MasteryWriteGate.RejectReason.entries.forEach { reason ->
            val text = knowledgeQuizRejectedReasonText(reason.name)

            assertTrue("为空：${reason.name}", text.isNotBlank())
            assertFalse(
                "仍是枚举名：${reason.name} -> $text",
                text.contains(reason.name),
            )
            assertFalse(
                "含下划线枚举风格：${reason.name} -> $text",
                text.contains("_"),
            )
        }
    }

    @Test
    fun unknownReasonFallsBackToAGenericSentence() {
        val text = knowledgeQuizRejectedReasonText("SOME_FUTURE_REASON")

        assertTrue(text.isNotBlank())
        assertFalse(text.contains("SOME_FUTURE_REASON"))
    }

    /**
     * PRD-02：键信任/纠错门的三个新拒因必须各有可读且**不撒谎**的文案——
     * 都不得让用户以为这次答题已经计入掌握度。
     */
    @Test
    fun answerKeyTrustReasonsExplainThatNothingWasCounted() {
        val unverified = knowledgeQuizRejectedReasonText("ANSWER_KEY_UNVERIFIED")
        assertTrue(unverified.contains("独立核对"))
        assertTrue(unverified.contains("不计入掌握度"))

        val stale = knowledgeQuizRejectedReasonText("ANSWER_KEY_VERSION_STALE")
        assertTrue(stale.contains("更正"))
        assertTrue(stale.contains("不再计入"))

        val disputed = knowledgeQuizRejectedReasonText("ANSWER_KEY_DISPUTED")
        assertTrue(disputed.contains("争议"))
        assertTrue(disputed.contains("暂停计分"))
    }
}
