package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyTrustDecision
import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import com.tingyun.smartmistakebook.core.model.answerKeyTrustDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2/PRD-02：答案键信任的两轴拆分（`docs/superpowers/specs/2026-10-19-product-readiness-design.md`
 * §3.2）。
 *
 * 消灭的失败：`KnowledgeQuizItemStore` 此前仅凭"本地 `selectedChoiceId == correctChoiceId`
 * 比较成功"就把题面写成 `VERIFIED + AUTO_VERIFIED`——本地比较的**确定性**被当成了
 * **键本身已被独立核验**。这份用例钉住拆分后的判定表：
 *
 * - `AUTO_VERIFIED`（本地机械可判）不再蕴含键已验证；
 * - 只有 `VERIFIED`（独立支持）与 `USER_ASSERTED`（用户更正的键，来源如实保留）可以写数值
 *   掌握证据；
 * - `UNKNOWN`/缺行/读不回一律降级为"只保留会话反馈与真实选择"，不新增数值证据。
 */
class AnswerKeyTrustPolicyTest {

    @Test
    fun anIndependentlyVerifiedKeyMayWriteNumericMasteryEvidence() {
        val decision = answerKeyTrustDecision(
            verificationStatus = "VERIFIED",
            itemReadable = true,
            keyVersion = "knowledge-quiz-item.v1",
        )

        assertEquals(AnswerKeyVerification.VERIFIED, decision.verification)
        assertTrue(decision.mayWriteNumericEvidence)
        assertEquals("knowledge-quiz-item.v1", decision.keyVersion)
    }

    @Test
    fun aUserAssertedKeyMayWriteButKeepsItsOwnProvenance() {
        val decision = answerKeyTrustDecision(
            verificationStatus = "USER_ASSERTED",
            itemReadable = true,
            keyVersion = "knowledge-quiz-item.v2",
        )

        assertEquals(AnswerKeyVerification.USER_ASSERTED, decision.verification)
        assertTrue("用户明确更正的键有来源，可走现有规则", decision.mayWriteNumericEvidence)
        assertEquals("knowledge-quiz-item.v2", decision.keyVersion)
    }

    @Test
    fun anUnknownOrMissingVerificationStatusStaysUnverified() {
        listOf(null, "", "UNKNOWN", "SOMETHING_ELSE").forEach { status ->
            val decision = answerKeyTrustDecision(
                verificationStatus = status,
                itemReadable = true,
                keyVersion = "knowledge-quiz-item.v1",
            )
            assertEquals(
                "status=$status 不得被猜成已验证",
                AnswerKeyVerification.UNKNOWN,
                decision.verification,
            )
            assertFalse("status=$status 不得写数值掌握证据", decision.mayWriteNumericEvidence)
        }
    }

    @Test
    fun anUnreadableItemCannotBeVerifiedEvenWhenTheStatusColumnSaysVerified() {
        val decision = answerKeyTrustDecision(
            verificationStatus = "VERIFIED",
            itemReadable = false,
            keyVersion = "knowledge-quiz-item.v1",
        )

        assertEquals(AnswerKeyVerification.UNKNOWN, decision.verification)
        assertFalse("题面读不回 = 无法核对键与选项映射", decision.mayWriteNumericEvidence)
    }

    @Test
    fun anAbsentKeyVersionIsReportedAsUnknownInsteadOfInvented() {
        val decision = answerKeyTrustDecision(
            verificationStatus = "VERIFIED",
            itemReadable = true,
            keyVersion = null,
        )

        assertEquals(AnswerKeyTrustDecision.UNKNOWN_KEY_VERSION, decision.keyVersion)
        assertTrue("键版本缺失不改变键本身的核验状态", decision.mayWriteNumericEvidence)
    }
}
