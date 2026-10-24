package com.tingyun.smartmistakebook.core.domain

/**
 * 回收编排：判定（纯函数）→ 记录答卷（幂等）→ 只对 LOCAL_CHECKED 档回灌证据（T5/5B-G8）。
 *
 * 消灭的失败：判定与回灌之间若无编排点——
 * 1. 同一张答卷重传会各走一遍流程，计分是否重复完全看消费侧自觉；
 * 2. "待核验/不可机判"题没有统一的"不写数值证据"执行点，容易顺着提交路径混进去。
 *
 * 回灌**总是**提交（不因答卷已记录而跳过）：证据端口按 attemptKey 幂等（created=false 即已
 * 计过），首回收在"记录后、回灌前"被杀的恢复重传也能收敛到只计一次，不丢证据。
 */
class PaperAnswerSheetRecycler(
    private val sheetStore: PaperAnswerSheetStore,
    private val evidenceFeedback: PaperAnswerEvidenceFeedbackPort,
) {
    suspend fun recycle(
        chain: PaperAnswerKeyChain,
        sheet: PaperAnswerSheet,
    ): PaperAnswerRecycleOutcome {
        val judged = judgePaperAnswerSheet(chain, sheet)
        if (judged is PaperAnswerSheetJudgeResult.PaperMismatch) {
            // 整张拒绝发生在任何写之前：不落答卷记录、不提交任何证据。
            return PaperAnswerRecycleOutcome.RejectedPaperMismatch(
                sheetExportId = judged.sheetExportId,
                sheetFingerprint = judged.sheetFingerprint,
                chainExportId = judged.chainExportId,
                chainFingerprint = judged.chainFingerprint,
            )
        }
        val verdicts = (judged as PaperAnswerSheetJudgeResult.Judged).verdicts
        val recordReceipt = sheetStore.recordAnswerSheet(sheet)

        // 注：不用 mapNotNull——它的 lambda 非 inline，装不下挂起调用。
        val feedbackReceipts = ArrayList<PaperEvidenceFeedbackReceipt>(verdicts.size)
        for (verdict in verdicts) {
            when (verdict) {
                is PaperAnswerRecycleVerdict.LocalCheckedCorrect ->
                    feedbackReceipts += submitLocalChecked(sheet, verdict, isCorrect = true)

                is PaperAnswerRecycleVerdict.LocalCheckedIncorrect ->
                    feedbackReceipts += submitLocalChecked(sheet, verdict, isCorrect = false)

                else -> Unit // 待核验/不可机判/缺映射：不写任何数值证据
            }
        }
        return PaperAnswerRecycleOutcome.Recorded(
            sheetCreated = recordReceipt.created,
            verdicts = verdicts,
            feedbackReceipts = feedbackReceipts,
        )
    }

    private suspend fun submitLocalChecked(
        sheet: PaperAnswerSheet,
        verdict: PaperAnswerRecycleVerdict.PaperLocalCheckedVerdict,
        isCorrect: Boolean,
    ): PaperEvidenceFeedbackReceipt = evidenceFeedback.submitLocalCheckedEvidence(
        PaperLocalCheckedEvidenceCommand(
            attemptKey = verdict.attemptKey,
            paperExportId = sheet.identity.exportId,
            practiceUnitId = verdict.practiceUnitId,
            problemRevisionId = verdict.problemRevisionId,
            selectedChoiceId = verdict.selectedChoiceId,
            correctChoiceId = verdict.correctChoiceId,
            isCorrect = isCorrect,
            keyVersion = verdict.keyVersion,
            keyVerification = verdict.keyVerification,
            occurredAtEpochMillis = sheet.recycledAtEpochMillis,
        ),
    )
}

/** 一次回收的结果。 */
sealed interface PaperAnswerRecycleOutcome {
    data class Recorded(
        /** false = 同 sheetKey 重传（答卷记录幂等，未产生第二条）。 */
        val sheetCreated: Boolean,
        val verdicts: List<PaperAnswerRecycleVerdict>,
        /** 与 LOCAL_CHECKED 档一一对应的回灌回执（重传时均为 created=false）。 */
        val feedbackReceipts: List<PaperEvidenceFeedbackReceipt>,
    ) : PaperAnswerRecycleOutcome

    /** 卷面身份对不上：整张拒绝（未记录、未提交任何证据）。 */
    data class RejectedPaperMismatch(
        val sheetExportId: String,
        val sheetFingerprint: String,
        val chainExportId: String,
        val chainFingerprint: String,
    ) : PaperAnswerRecycleOutcome
}
