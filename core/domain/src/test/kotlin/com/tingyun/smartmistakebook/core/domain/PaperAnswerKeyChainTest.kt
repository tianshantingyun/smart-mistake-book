package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5/5B-G8：出卷答案键链（卷面题 ↔ 库题 ↔ 答案来源）。
 *
 * 消灭的失败：G8 现状里出卷侧没有答案键/题映射链（`MistakeExportRecord` 无 answerKey 字段族，
 * 计划 §0 已核），回收时"学生选的是哪个选项、该对照哪个键、键是否可信"无处对齐。
 */
class PaperAnswerKeyChainTest {

    @Test
    fun builderFoldsKeyTrustFailClosedFromTheStoredVerificationStatus() {
        val verified = buildLink(answerVerificationStatus = "VERIFIED")
        val userAsserted = buildLink(answerVerificationStatus = "USER_ASSERTED")

        assertEquals(AnswerKeyVerification.VERIFIED, verified.keyTrust.verification)
        assertEquals(AnswerKeyVerification.USER_ASSERTED, userAsserted.keyTrust.verification)
        assertTrue(verified.keyTrust.mayWriteNumericEvidence)
        assertTrue(userAsserted.keyTrust.mayWriteNumericEvidence)
        assertEquals("spec-single-choice-v1", verified.keyTrust.keyVersion)
    }

    @Test
    fun unknownGarbageOrNullStatusStaysUnknown() {
        listOf(null, "", "UNKNOWN", "SOMETHING_ELSE").forEach { status ->
            val link = buildLink(answerVerificationStatus = status)
            assertEquals(
                "status=$status 不得被猜成已验证（PRD-02 fail-closed）",
                AnswerKeyVerification.UNKNOWN,
                link.keyTrust.verification,
            )
            assertFalse("status=$status 不得写数值掌握证据", link.keyTrust.mayWriteNumericEvidence)
        }
    }

    @Test
    fun missingKeyVersionIsReportedAsUnknownInsteadOfInvented() {
        val link = buildLink(answerSpecId = null)

        assertEquals("unknown-key-version", link.keyTrust.keyVersion)
    }

    @Test
    fun aCompleteTrustedObjectiveLinkIsLocallyCheckable() {
        val link = buildLink(
            choiceIds = listOf("opt-a", "opt-b", "opt-c"),
            correctChoiceId = "opt-b",
            answerVerificationStatus = "VERIFIED",
        )

        assertTrue(link.locallyCheckable)
    }

    @Test
    fun aDanglingCorrectChoiceIdIsNotLocallyCheckable() {
        val link = buildLink(
            choiceIds = listOf("opt-a", "opt-b"),
            correctChoiceId = "opt-elsewhere", // 键不在卷面选项集内：无从对照
            answerVerificationStatus = "VERIFIED",
        )

        assertFalse("键落在选项集外 = 无法对照（与库内机判门槛同规）", link.locallyCheckable)
    }

    @Test
    fun fewerThanTwoOptionsIsNotLocallyCheckable() {
        val link = buildLink(
            choiceIds = listOf("opt-a"),
            correctChoiceId = "opt-a",
            answerVerificationStatus = "VERIFIED",
        )

        assertFalse("单选项不构成客观题形态", link.locallyCheckable)
    }

    @Test
    fun untrustedKeyIsNeverLocallyCheckableEvenWhenComparable() {
        val link = buildLink(
            choiceIds = listOf("opt-a", "opt-b"),
            correctChoiceId = "opt-a",
            answerVerificationStatus = null,
        )

        assertFalse("键不可信时形态再齐备也不得本地判（PRD-02 两轴拆分）", link.locallyCheckable)
        assertEquals("opt-a", link.correctChoiceId) // 键原值保留（待核验路径要能展示它）
    }

    @Test
    fun chainLooksUpLinksByPaperQuestionId() {
        val chain = buildChain(
            descriptors = listOf(
                buildDescriptor(paperQuestionId = "question-0"),
                buildDescriptor(paperQuestionId = "question-1"),
            ),
        )

        assertEquals("pu-1", chain.linkFor("question-0")?.practiceUnitId)
        assertEquals("rev-1", chain.linkFor("question-1")?.problemRevisionId)
        assertNull(chain.linkFor("question-missing"))
    }

    @Test
    fun duplicatePaperQuestionIdsAreRejected() {
        var rejected = false
        try {
            buildChain(
                descriptors = listOf(
                    buildDescriptor(paperQuestionId = "question-0"),
                    buildDescriptor(paperQuestionId = "question-0"),
                ),
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("同一卷面题出现两条映射是装配错误，必须显式失败", rejected)
    }

    @Test
    fun anEmptyChainIsRejected() {
        var rejected = false
        try {
            buildChain(descriptors = emptyList())
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("一张卷至少一题（与卷面草稿契约一致）", rejected)
    }

    @Test
    fun blankLinkComponentsAreRejected() {
        assertDescriptorRejected(buildDescriptor(practiceUnitId = " "))
        assertDescriptorRejected(buildDescriptor(problemRevisionId = ""))
        assertDescriptorRejected(buildDescriptor(paperQuestionId = " "))
        assertDescriptorRejected(
            buildDescriptor(choiceIds = listOf("opt-a", "opt-a")), // 选项 ID 重复
        )
    }

    private fun assertDescriptorRejected(descriptor: PaperAnswerKeyLinkDescriptor) {
        var rejected = false
        try {
            buildChain(descriptors = listOf(descriptor))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("非法映射分量必须被拒绝：$descriptor", rejected)
    }

    private fun buildLink(
        paperQuestionId: String = "question-0",
        choiceIds: List<String> = listOf("opt-a", "opt-b"),
        correctChoiceId: String? = "opt-a",
        answerVerificationStatus: String? = "VERIFIED",
        answerSpecId: String? = "spec-single-choice-v1",
    ): PaperAnswerKeyLink = buildPaperAnswerKeyLink(
        PaperAnswerKeyLinkDescriptor(
            paperQuestionId = paperQuestionId,
            practiceUnitId = "pu-1",
            problemRevisionId = "rev-1",
            choiceIds = choiceIds,
            correctChoiceId = correctChoiceId,
            answerVerificationStatus = answerVerificationStatus,
            answerSpecId = answerSpecId,
        ),
    )

    private fun buildDescriptor(
        paperQuestionId: String = "question-0",
        practiceUnitId: String = "pu-1",
        problemRevisionId: String = "rev-1",
        choiceIds: List<String> = listOf("opt-a", "opt-b"),
    ) = PaperAnswerKeyLinkDescriptor(
        paperQuestionId = paperQuestionId,
        practiceUnitId = practiceUnitId,
        problemRevisionId = problemRevisionId,
        choiceIds = choiceIds,
        correctChoiceId = "opt-a",
        answerVerificationStatus = "VERIFIED",
        answerSpecId = "spec-single-choice-v1",
    )

    private fun buildChain(descriptors: List<PaperAnswerKeyLinkDescriptor>) = buildPaperAnswerKeyChain(
        exportId = "export-1",
        paperFingerprint = "a".repeat(64),
        createdAtEpochMillis = 0L,
        descriptors = descriptors,
    )
}
