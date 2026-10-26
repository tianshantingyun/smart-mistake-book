package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5-A 核验升级的领域判据（KD-67 写方 API 的 domain 半边）。
 *
 * 消灭的失败：升级 API 只收目标枚举时，`USER_ASSERTED` 会退化成"谁都能写的一档"——
 * 没有用户明确确认的模型生成键也能被标成"用户断言"，选择器的 `contentVerified`
 * 判据随即被绕过。本用例把 PRD-02 的来源语义钉在类型与判据上：VERIFIED 只认独立核验，
 * USER_ASSERTED 只认本人明确确认，且只有 UNKNOWN 能被升级。
 */
class AnswerKeyVerificationUpgradePolicyTest {

    private val learnerId = "learner:local"
    private val snapshotId = "knowledge-quiz-item:$learnerId:5000:kc-a"

    private fun request(
        current: AnswerKeyVerification = AnswerKeyVerification.UNKNOWN,
        target: AnswerKeyVerification = AnswerKeyVerification.USER_ASSERTED,
        source: AnswerKeyVerificationSource = userAssertion(),
        learner: String = learnerId,
    ) = AnswerKeyVerificationUpgradeRequest(
        learnerId = learner,
        assessmentItemSnapshotId = snapshotId,
        currentVerification = current,
        targetVerification = target,
        source = source,
    )

    private fun userAssertion(
        confirmationId: String = "key-correction:req-1:evidence-1",
        confirmedBy: String = learnerId,
    ) = AnswerKeyVerificationSource.UserAssertion(
        confirmationId = confirmationId,
        confirmedByLearnerId = confirmedBy,
        confirmedAtEpochMillis = 1_000L,
    )

    private fun independentVerification() = AnswerKeyVerificationSource.IndependentVerification(
        verificationId = "manual-review:2026-10-26:item-1",
        verifiedAtEpochMillis = 1_000L,
    )

    private fun rejectionOf(
        decision: AnswerKeyVerificationUpgradeDecision,
    ): AnswerKeyVerificationUpgradeRejection {
        assertTrue("expected a rejection, got $decision", decision is AnswerKeyVerificationUpgradeDecision.Rejected)
        return (decision as AnswerKeyVerificationUpgradeDecision.Rejected).reason
    }

    @Test
    fun `verified target only accepts an independent verification source`() {
        val allowed = decideAnswerKeyVerificationUpgrade(
            request(target = AnswerKeyVerification.VERIFIED, source = independentVerification()),
        )
        assertTrue(allowed is AnswerKeyVerificationUpgradeDecision.Allowed)
        assertEquals(
            AnswerKeyVerification.VERIFIED,
            (allowed as AnswerKeyVerificationUpgradeDecision.Allowed).targetVerification,
        )

        // 用户确认不能冒充独立核验：两条来源轴不能互换。
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.SOURCE_DOES_NOT_MATCH_TARGET,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(target = AnswerKeyVerification.VERIFIED, source = userAssertion()),
                ),
            ),
        )
    }

    @Test
    fun `user asserted target only accepts a confirmation from the same learner`() {
        val allowed = decideAnswerKeyVerificationUpgrade(request())
        assertTrue(allowed is AnswerKeyVerificationUpgradeDecision.Allowed)

        // 缺用户确认字段（拿独立核验来源冒充）→ 拒绝。
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.SOURCE_DOES_NOT_MATCH_TARGET,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(source = independentVerification()),
                ),
            ),
        )
        // 确认者是别的学习者 → 不是在确认自己的题面。
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.USER_CONFIRMATION_LEARNER_MISMATCH,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(source = userAssertion(confirmedBy = "learner:other")),
                ),
            ),
        )
    }

    @Test
    fun `only unknown rows are upgradable and unknown is never a target`() {
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(current = AnswerKeyVerification.VERIFIED, target = AnswerKeyVerification.VERIFIED),
                ),
            ),
        )
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(
                        current = AnswerKeyVerification.USER_ASSERTED,
                        target = AnswerKeyVerification.VERIFIED,
                        source = independentVerification(),
                    ),
                ),
            ),
        )
        // 目标 UNKNOWN 不是"升级"，且优先级高于来源检查。
        assertEquals(
            AnswerKeyVerificationUpgradeRejection.TARGET_IS_NOT_AN_UPGRADE,
            rejectionOf(
                decideAnswerKeyVerificationUpgrade(
                    request(target = AnswerKeyVerification.UNKNOWN, source = independentVerification()),
                ),
            ),
        )
    }

    @Test
    fun `user assertion source fields cannot be blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnswerKeyVerificationSource.UserAssertion(
                confirmationId = " ",
                confirmedByLearnerId = learnerId,
                confirmedAtEpochMillis = 1_000L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnswerKeyVerificationSource.UserAssertion(
                confirmationId = "confirmation-1",
                confirmedByLearnerId = "",
                confirmedAtEpochMillis = 1_000L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnswerKeyVerificationSource.IndependentVerification(
                verificationId = "",
                verifiedAtEpochMillis = 1_000L,
            )
        }
    }
}
