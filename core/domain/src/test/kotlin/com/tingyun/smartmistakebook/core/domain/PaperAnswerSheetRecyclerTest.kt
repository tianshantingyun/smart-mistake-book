package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5/5B-G8：证据回灌编排（重传幂等 / 不重复计分 / 待核验不写数值 / 无 MODEL_JUDGED·REVEAL 污染）。
 *
 * 消灭的失败：判定（纯函数）与回灌（既有通道）之间若无编排点，"同一张答卷重传"会把同一
 * attemptKey 的证据提交两遍（重复计分），"待核验"题会顺着提交路径混进数值证据。
 */
class PaperAnswerSheetRecyclerTest {

    private val identity = PaperAnswerSheetIdentity(
        exportId = EXPORT_ID,
        paperFingerprint = FINGERPRINT,
        recycledAtEpochMillis = 5L * 3_600_000L,
        utcOffsetMinutes = 0,
    )

    @Test
    fun firstRecycleSubmitsEvidenceOnlyForLocalCheckedVerdicts() = runBlocking {
        val feedback = RecordingFeedbackPort()
        val store = RecordingSheetStore()
        val recycler = PaperAnswerSheetRecycler(sheetStore = store, evidenceFeedback = feedback)
        val sheet = mixedSheet()

        val outcome = recycler.recycle(chain = trustedChain(), sheet = sheet)

        assertTrue(outcome is PaperAnswerRecycleOutcome.Recorded)
        outcome as PaperAnswerRecycleOutcome.Recorded
        assertTrue("首次记录 created=true", outcome.sheetCreated)
        assertEquals(3, outcome.verdicts.size)
        assertEquals(
            "只有 LOCAL_CHECKED 档回灌证据（待核验/不可机判都不提交）",
            listOf("question-0", "question-1").map(identity::attemptKeyFor),
            feedback.submitted.map(PaperLocalCheckedEvidenceCommand::attemptKey),
        )
        assertEquals(2, outcome.feedbackReceipts.size)
        assertTrue(outcome.feedbackReceipts.all(PaperEvidenceFeedbackReceipt::created))
    }

    @Test
    fun reUploadingTheSameSheetDoesNotScoreTwice() = runBlocking {
        val feedback = RecordingFeedbackPort()
        val store = RecordingSheetStore()
        val recycler = PaperAnswerSheetRecycler(sheetStore = store, evidenceFeedback = feedback)
        val sheet = mixedSheet()

        val first = recycler.recycle(chain = trustedChain(), sheet = sheet)
        val replay = recycler.recycle(chain = trustedChain(), sheet = sheet)

        assertTrue(first is PaperAnswerRecycleOutcome.Recorded)
        assertTrue(replay is PaperAnswerRecycleOutcome.Recorded)
        first as PaperAnswerRecycleOutcome.Recorded
        replay as PaperAnswerRecycleOutcome.Recorded
        assertTrue("首次计分：每个 LOCAL_CHECKED 档 created=true", first.feedbackReceipts.all { it.created })
        assertTrue("重传不产生第二条答卷记录", !replay.sheetCreated)
        assertTrue(
            "重放回执必须全部 created=false（不重复计分）",
            replay.feedbackReceipts.none { it.created },
        )
        assertEquals(
            "证据端口按 attemptKey 幂等：重放提交再多，真正计分的 attemptKey 只有两个",
            2,
            feedback.submitted.map(PaperLocalCheckedEvidenceCommand::attemptKey).distinct().size,
        )
        assertEquals(1, store.storedCount)
    }

    @Test
    fun pendingVerificationQuestionsKeepTheStudentAnswerAndWriteNoEvidence() = runBlocking {
        val feedback = RecordingFeedbackPort()
        val recycler = PaperAnswerSheetRecycler(
            sheetStore = RecordingSheetStore(),
            evidenceFeedback = feedback,
        )

        val outcome = recycler.recycle(chain = trustedChain(), sheet = pendingOnlySheet())

        outcome as PaperAnswerRecycleOutcome.Recorded
        val pending = outcome.verdicts
            .filterIsInstance<PaperAnswerRecycleVerdict.PendingVerification>()
            .single()
        assertEquals(
            "待核验题保留学生原始作答（会话反馈路径）",
            "opt-b",
            pending.selectedChoiceId,
        )
        assertTrue("待核验不得写任何数值证据", feedback.submitted.isEmpty())
    }

    @Test
    fun aSheetFromAnotherPaperIsRejectedBeforeAnyWrite() = runBlocking {
        val feedback = RecordingFeedbackPort()
        val store = RecordingSheetStore()
        val recycler = PaperAnswerSheetRecycler(sheetStore = store, evidenceFeedback = feedback)
        val stranger = PaperAnswerSheet(
            identity = identity.copy(paperFingerprint = "b".repeat(64)),
            recycledAtEpochMillis = identity.recycledAtEpochMillis,
            answers = listOf(PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a")),
        )

        val outcome = recycler.recycle(chain = trustedChain(), sheet = stranger)

        assertTrue(outcome is PaperAnswerRecycleOutcome.RejectedPaperMismatch)
        assertTrue("整张拒绝：不落答卷记录", store.storedCount == 0)
        assertTrue("整张拒绝：不提交任何证据", feedback.submitted.isEmpty())
    }

    @Test
    fun submittedCommandsCarryTheLocalCheckedSourceSemantics() = runBlocking {
        val feedback = RecordingFeedbackPort()
        val recycler = PaperAnswerSheetRecycler(
            sheetStore = RecordingSheetStore(),
            evidenceFeedback = feedback,
        )

        recycler.recycle(chain = trustedChain(), sheet = mixedSheet())

        feedback.submitted.forEach { command ->
            assertEquals("pu-1", command.practiceUnitId)
            assertEquals("rev-1", command.problemRevisionId)
            assertEquals(EXPORT_ID, command.paperExportId)
            assertTrue(command.attemptKey.isNotBlank())
        }
        // 端口只有一个入口且只接受本地判结果：MODEL_JUDGED / REVEAL 在此形状下无从产生
        // （判词通道是另一个显式接口，本批无实现）。
    }

    // ------------------------------------------------------------------

    /** 端口测试替身：按 attemptKey 幂等（首个 created=true，重放 created=false）。 */
    private class RecordingFeedbackPort : PaperAnswerEvidenceFeedbackPort {
        val submitted = mutableListOf<PaperLocalCheckedEvidenceCommand>()
        private val scored = mutableSetOf<String>()

        override suspend fun submitLocalCheckedEvidence(
            command: PaperLocalCheckedEvidenceCommand,
        ): PaperEvidenceFeedbackReceipt {
            submitted += command
            return PaperEvidenceFeedbackReceipt(
                attemptKey = command.attemptKey,
                created = scored.add(command.attemptKey),
            )
        }
    }

    private class RecordingSheetStore : PaperAnswerSheetStore {
        val storedCount get() = sheets.size
        private val sheets = LinkedHashMap<String, PaperAnswerSheet>()

        override suspend fun recordAnswerSheet(
            sheet: PaperAnswerSheet,
        ): PaperAnswerSheetRecordReceipt {
            val existing = sheets.putIfAbsent(sheet.identity.sheetKey, sheet)
            return PaperAnswerSheetRecordReceipt(
                sheetKey = sheet.identity.sheetKey,
                created = existing == null,
            )
        }

        override suspend fun readAnswerSheet(sheetKey: String): PaperAnswerSheet? = sheets[sheetKey]
    }

    /** question-0 对（可信键）、question-1 错（可信键）、question-2 待核验（未核验键）。 */
    private fun mixedSheet() = PaperAnswerSheet(
        identity = identity,
        recycledAtEpochMillis = identity.recycledAtEpochMillis,
        answers = listOf(
            PaperQuestionAnswer(paperQuestionId = "question-0", selectedChoiceId = "opt-a"),
            PaperQuestionAnswer(paperQuestionId = "question-1", selectedChoiceId = "opt-c"),
            PaperQuestionAnswer(paperQuestionId = "question-2", selectedChoiceId = "opt-b"),
        ),
    )

    private fun pendingOnlySheet() = PaperAnswerSheet(
        identity = identity,
        recycledAtEpochMillis = identity.recycledAtEpochMillis,
        answers = listOf(PaperQuestionAnswer(paperQuestionId = "question-2", selectedChoiceId = "opt-b")),
    )

    private fun trustedChain() = PaperAnswerKeyChain(
        exportId = EXPORT_ID,
        paperFingerprint = FINGERPRINT,
        createdAtEpochMillis = 0L,
        links = listOf(
            link("question-0", correct = "opt-a", status = "VERIFIED"),
            link("question-1", correct = "opt-a", status = "VERIFIED"),
            link("question-2", correct = "opt-a", status = null),
        ),
    )

    private fun link(
        paperQuestionId: String,
        correct: String?,
        status: String?,
    ) = buildPaperAnswerKeyLink(
        PaperAnswerKeyLinkDescriptor(
            paperQuestionId = paperQuestionId,
            practiceUnitId = "pu-1",
            problemRevisionId = "rev-1",
            choiceIds = listOf("opt-a", "opt-b", "opt-c"),
            correctChoiceId = correct,
            answerVerificationStatus = status,
            answerSpecId = "spec-single-choice-v1",
        ),
    )

    private companion object {
        const val EXPORT_ID = "export-1"
        val FINGERPRINT = "a".repeat(64)
    }
}
