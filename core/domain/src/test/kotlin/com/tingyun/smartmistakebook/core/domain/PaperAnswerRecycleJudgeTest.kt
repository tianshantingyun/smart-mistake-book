package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5/5B-G8：客观题本地判（表驱动）。
 *
 * 消灭的失败：回收判定若只做"选项 ID 对照"，未核验/争议键的对照结果会被写成数值掌握
 * （PRD-02 红线："本地比较确定性"不等于"键本身已验证"）。判定必须把
 * ① 形态可对照、② 键可信、③ 作答与卷面选项同源 三件事拆开。
 */
class PaperAnswerRecycleJudgeTest {

    private val sheetIdentity = PaperAnswerSheetIdentity(
        exportId = EXPORT_ID,
        paperFingerprint = FINGERPRINT,
        recycledAtEpochMillis = 5L * 3_600_000L,
        utcOffsetMinutes = 0,
    )

    /** 判定表：一行 = (卷面形态, 键信任, 学生作答) → 期望档。 */
    private val table: List<Row> = listOf(
        Row(
            name = "可信键+选中即键 → LOCAL_CHECKED 对",
            link = link(correctChoiceId = "opt-a", status = "VERIFIED"),
            selectedChoiceId = "opt-a",
            expected = Expected.LocalChecked(isCorrect = true),
        ),
        Row(
            name = "可信键+选错 → LOCAL_CHECKED 错",
            link = link(correctChoiceId = "opt-a", status = "VERIFIED"),
            selectedChoiceId = "opt-b",
            expected = Expected.LocalChecked(isCorrect = false),
        ),
        Row(
            name = "用户更正键（USER_ASSERTED）同样可判，来源如实保留",
            link = link(correctChoiceId = "opt-b", status = "USER_ASSERTED"),
            selectedChoiceId = "opt-b",
            expected = Expected.LocalChecked(isCorrect = true),
        ),
        Row(
            name = "未核验键（UNKNOWN）→ 待核验，不写数值掌握",
            link = link(correctChoiceId = "opt-a", status = null),
            selectedChoiceId = "opt-a",
            expected = Expected.PendingVerification,
        ),
        Row(
            name = "争议状态字符串 → 同样待核验（fail-closed）",
            link = link(correctChoiceId = "opt-a", status = "DISPUTED"),
            selectedChoiceId = "opt-b",
            expected = Expected.PendingVerification,
        ),
        Row(
            name = "作答不在卷面选项集 → 不可机判（对照不同源）",
            link = link(correctChoiceId = "opt-a", status = "VERIFIED"),
            selectedChoiceId = "opt-elsewhere",
            expected = Expected.NotMachineCheckable(PaperNotMachineCheckableReason.ANSWER_OUTSIDE_OPTIONS),
        ),
        Row(
            name = "键落在选项集外 → 不可机判（键损坏）",
            link = link(correctChoiceId = "opt-dangling", status = "VERIFIED"),
            selectedChoiceId = "opt-a",
            expected = Expected.NotMachineCheckable(PaperNotMachineCheckableReason.KEY_OUTSIDE_OPTIONS),
        ),
        Row(
            name = "无键（开放题）→ 判词通道形态",
            link = link(correctChoiceId = null, status = "VERIFIED", choiceIds = listOf("opt-a", "opt-b")),
            selectedChoiceId = null,
            expected = Expected.NotMachineCheckable(PaperNotMachineCheckableReason.OPEN_ANSWER),
        ),
        Row(
            name = "选项不足两枚 → 不可机判",
            link = link(correctChoiceId = "opt-a", status = "VERIFIED", choiceIds = listOf("opt-a")),
            selectedChoiceId = "opt-a",
            expected = Expected.NotMachineCheckable(PaperNotMachineCheckableReason.NO_OBJECTIVE_FORM),
        ),
    )

    @Test
    fun judgeFollowsTheDecisionTable() {
        table.forEach { row ->
            val chain = chainOf(row.link)
            val sheet = sheetOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = row.selectedChoiceId))
            val judged = judgePaperAnswerSheet(chain, sheet)

            assertTrue("行「${row.name}」应判出结果", judged is PaperAnswerSheetJudgeResult.Judged)
            val verdict = (judged as PaperAnswerSheetJudgeResult.Judged).verdicts.single()

            when (val expected = row.expected) {
                is Expected.LocalChecked -> {
                    assertTrue(
                        "行「${row.name}」应为 LOCAL_CHECKED 档，实际 ${verdict::class.simpleName}",
                        verdict is PaperAnswerRecycleVerdict.LocalCheckedCorrect ||
                            verdict is PaperAnswerRecycleVerdict.LocalCheckedIncorrect,
                    )
                    val local = verdict as PaperAnswerRecycleVerdict.PaperLocalCheckedVerdict
                    val isCorrect = verdict is PaperAnswerRecycleVerdict.LocalCheckedCorrect
                    assertEquals("行「${row.name}」对错", expected.isCorrect, isCorrect)
                    assertEquals("pu-1", local.practiceUnitId)
                    assertEquals("rev-1", local.problemRevisionId)
                }
                is Expected.PendingVerification -> {
                    assertTrue(
                        "行「${row.name}」应为待核验，实际 ${verdict::class.simpleName}",
                        verdict is PaperAnswerRecycleVerdict.PendingVerification,
                    )
                    verdict as PaperAnswerRecycleVerdict.PendingVerification
                    assertEquals(
                        "行「${row.name}」学生原始作答必须保留（会话反馈路径）",
                        row.selectedChoiceId,
                        verdict.selectedChoiceId,
                    )
                }
                is Expected.NotMachineCheckable -> {
                    assertTrue(
                        "行「${row.name}」应为不可机判，实际 ${verdict::class.simpleName}",
                        verdict is PaperAnswerRecycleVerdict.NotMachineCheckable,
                    )
                    verdict as PaperAnswerRecycleVerdict.NotMachineCheckable
                    assertEquals("行「${row.name}」原因", expected.reason, verdict.reason)
                }
            }
        }
    }

    @Test
    fun everyVerdictCarriesTheStableAttemptKey() {
        val chain = chainOf(link(correctChoiceId = "opt-a", status = "VERIFIED"))
        val sheet = sheetOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a"))
        val verdict = (judgePaperAnswerSheet(chain, sheet) as PaperAnswerSheetJudgeResult.Judged).verdicts.single()

        assertEquals(sheetIdentity.attemptKeyFor("question-0"), verdict.attemptKey)
    }

    @Test
    fun aQuestionMissingFromTheChainIsExplicitlyUnmapped() {
        val chain = chainOf(link(paperQuestionId = "question-0"))
        val sheet = sheetOf(PaperQuestionAnswer(paperQuestionId = "question-99", selectedChoiceId = "opt-a"))
        val verdict = (judgePaperAnswerSheet(chain, sheet) as PaperAnswerSheetJudgeResult.Judged).verdicts.single()

        assertTrue("键链缺题必须显式暴露，不得静默跳过", verdict is PaperAnswerRecycleVerdict.UnmappedQuestion)
    }

    @Test
    fun aSheetFromAnotherPaperIsRejectedWhole() {
        val chain = chainOf(link())
        val otherPaperSheet = PaperAnswerSheet(
            identity = sheetIdentity.copy(paperFingerprint = "b".repeat(64)),
            recycledAtEpochMillis = sheetIdentity.recycledAtEpochMillis,
            answers = listOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a")),
        )

        val judged = judgePaperAnswerSheet(chain, otherPaperSheet)

        assertTrue("不同卷面指纹整张拒绝（不同卷的键不互通）", judged is PaperAnswerSheetJudgeResult.PaperMismatch)
    }

    @Test
    fun aSheetFromAnotherExportRecordOfTheSameContentIsAccepted() {
        val chain = chainOf(link())
        val sameContentSheet = PaperAnswerSheet(
            identity = sheetIdentity.copy(exportId = "export-other"),
            recycledAtEpochMillis = sheetIdentity.recycledAtEpochMillis,
            answers = listOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a")),
        )

        val judged = judgePaperAnswerSheet(chain, sameContentSheet)

        assertTrue("同内容重导出的答卷仍可回收（指纹一致）", judged is PaperAnswerSheetJudgeResult.Judged)
    }

    @Test
    fun localCheckedVerdictsCarryKeyProvenanceForAudit() {
        val chain = chainOf(link(correctChoiceId = "opt-a", status = "USER_ASSERTED", answerSpecId = "spec-v9"))
        val sheet = sheetOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a"))
        val verdict = (judgePaperAnswerSheet(chain, sheet) as PaperAnswerSheetJudgeResult.Judged)
            .verdicts
            .single() as PaperAnswerRecycleVerdict.LocalCheckedCorrect

        assertEquals("spec-v9", verdict.keyVersion)
        assertEquals(AnswerKeyVerification.USER_ASSERTED, verdict.keyVerification)
        assertEquals("opt-a", verdict.correctChoiceId)
    }

    // ------------------------------------------------------------------

    private data class Row(
        val name: String,
        val link: PaperAnswerKeyLink,
        val selectedChoiceId: String?,
        val expected: Expected,
    )

    private sealed interface Expected {
        data class LocalChecked(val isCorrect: Boolean) : Expected
        object PendingVerification : Expected
        data class NotMachineCheckable(val reason: PaperNotMachineCheckableReason) : Expected
    }

    private fun link(
        paperQuestionId: String = "question-0",
        choiceIds: List<String> = listOf("opt-a", "opt-b", "opt-c"),
        correctChoiceId: String? = "opt-a",
        status: String? = "VERIFIED",
        answerSpecId: String? = "spec-single-choice-v1",
    ): PaperAnswerKeyLink = buildPaperAnswerKeyLink(
        PaperAnswerKeyLinkDescriptor(
            paperQuestionId = paperQuestionId,
            practiceUnitId = "pu-1",
            problemRevisionId = "rev-1",
            choiceIds = choiceIds,
            correctChoiceId = correctChoiceId,
            answerVerificationStatus = status,
            answerSpecId = answerSpecId,
        ),
    )

    private fun chainOf(vararg links: PaperAnswerKeyLink) = PaperAnswerKeyChain(
        exportId = EXPORT_ID,
        paperFingerprint = FINGERPRINT,
        createdAtEpochMillis = 0L,
        links = links.toList(),
    )

    private fun sheetOf(vararg answers: PaperQuestionAnswer) = PaperAnswerSheet(
        identity = sheetIdentity,
        recycledAtEpochMillis = sheetIdentity.recycledAtEpochMillis,
        answers = answers.toList(),
    )

    private companion object {
        const val EXPORT_ID = "export-1"
        val FINGERPRINT = "a".repeat(64)
    }
}
