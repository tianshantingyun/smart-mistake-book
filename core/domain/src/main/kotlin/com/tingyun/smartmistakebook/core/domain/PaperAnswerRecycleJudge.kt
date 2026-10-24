package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification

/**
 * 客观题本地判的逐题判定（T5/5B-G8，纯函数）。
 *
 * 判定拆三件事（消灭的失败：只做"选项 ID 对照"会把未核验键的对照结果写成数值掌握，
 * PRD-02 红线"本地比较确定性 ≠ 键本身已验证"）：
 * 1. **同卷**：答卷与键链必须是同一份卷面（指纹一致），否则整张拒绝——不同卷的键不互通；
 * 2. **同源对照**：作答选项 ID 必须落在键链的卷面选项集内，键也必须落在选项集内；
 * 3. **键可信**：只有 VERIFIED / USER_ASSERTED 的键才判对错（LOCAL_CHECKED 档）；
 *    未核验/争议键 → 待核验（保留学生作答与会话反馈路径，不写数值掌握）。
 */
sealed interface PaperAnswerRecycleVerdict {
    val paperQuestionId: String
    val attemptKey: String

    /** 学生原始作答：任何档都不丢弃（待核验/不可机判也保留，会话反馈路径）。 */
    val selectedChoiceId: String?

    /**
     * 本地判档（对/错）的公共形态：回灌命令需要的全部字段。只有这两档可构造
     * [PaperLocalCheckedEvidenceCommand]。
     */
    sealed interface PaperLocalCheckedVerdict : PaperAnswerRecycleVerdict {
        /** 本地判档必然有客观作答（收窄为非空）。 */
        override val selectedChoiceId: String
        val correctChoiceId: String
        val practiceUnitId: String
        val problemRevisionId: String
        val keyVersion: String
        val keyVerification: AnswerKeyVerification
    }

    /** 键可信 + 对照=对：唯一可回灌正向 LOCAL_CHECKED 证据的档。 */
    data class LocalCheckedCorrect(
        override val paperQuestionId: String,
        override val attemptKey: String,
        override val selectedChoiceId: String,
        override val correctChoiceId: String,
        override val practiceUnitId: String,
        override val problemRevisionId: String,
        override val keyVersion: String,
        override val keyVerification: AnswerKeyVerification,
    ) : PaperLocalCheckedVerdict

    /** 键可信 + 对照=错：回灌负向 LOCAL_CHECKED 证据。 */
    data class LocalCheckedIncorrect(
        override val paperQuestionId: String,
        override val attemptKey: String,
        override val selectedChoiceId: String,
        override val correctChoiceId: String,
        override val practiceUnitId: String,
        override val problemRevisionId: String,
        override val keyVersion: String,
        override val keyVerification: AnswerKeyVerification,
    ) : PaperLocalCheckedVerdict

    /**
     * 客观题形态齐备、可对照，但键不可信（UNKNOWN/争议/读不回）→ 该题"待核验"：
     * 不写数值掌握，学生作答与键原值保留（PRD-02 纠错链核验后可重判，重判接线随 PRD-02 全量落地）。
     */
    data class PendingVerification(
        override val paperQuestionId: String,
        override val attemptKey: String,
        override val selectedChoiceId: String,
        val correctChoiceId: String,
        val practiceUnitId: String,
        val problemRevisionId: String,
        val keyVersion: String,
    ) : PaperAnswerRecycleVerdict

    /** 非客观形态 / 对照不同源 / 无键：不写任何证据；开放作答走判词通道（接口，本批无实现）。 */
    data class NotMachineCheckable(
        override val paperQuestionId: String,
        override val attemptKey: String,
        override val selectedChoiceId: String?,
        val reason: PaperNotMachineCheckableReason,
    ) : PaperAnswerRecycleVerdict

    /** 卷面上有题、键链里没有映射（装配缺口/卷面被改）：显式暴露，不判、不写、不静默跳过。 */
    data class UnmappedQuestion(
        override val paperQuestionId: String,
        override val attemptKey: String,
        override val selectedChoiceId: String?,
    ) : PaperAnswerRecycleVerdict
}

enum class PaperNotMachineCheckableReason {
    /** 无客观作答（开放题/未识别出选项）——判词通道形态。 */
    OPEN_ANSWER,

    /** 学生作答的选项 ID 不在卷面选项集内：跨选项集对照无意义。 */
    ANSWER_OUTSIDE_OPTIONS,

    /** 键不在卷面选项集内（键损坏）：无从对照（与库内机判门槛同规）。 */
    KEY_OUTSIDE_OPTIONS,

    /** 无键或选项不足两枚：不构成客观题形态。 */
    NO_OBJECTIVE_FORM,
}

/** 整张答卷的判定结果。 */
sealed interface PaperAnswerSheetJudgeResult {
    data class Judged(val verdicts: List<PaperAnswerRecycleVerdict>) : PaperAnswerSheetJudgeResult {
        init {
            require(verdicts.isNotEmpty()) { "A judged sheet carries at least one verdict" }
        }
    }

    /**
     * 答卷与键链不是同一份卷面（指纹不一致）：整张拒绝，不判任何题。
     * exportId 不一致但指纹一致时**不**拒绝——同内容重导出的答卷仍可回收（键链内容相同）。
     */
    data class PaperMismatch(
        val sheetExportId: String,
        val sheetFingerprint: String,
        val chainExportId: String,
        val chainFingerprint: String,
    ) : PaperAnswerSheetJudgeResult
}

/**
 * 判一张回收答卷（纯函数，无 IO）。判定表见 `PaperAnswerRecycleJudgeTest` 的表驱动用例。
 */
fun judgePaperAnswerSheet(
    chain: PaperAnswerKeyChain,
    sheet: PaperAnswerSheet,
): PaperAnswerSheetJudgeResult {
    if (sheet.identity.paperFingerprint != chain.paperFingerprint) {
        return PaperAnswerSheetJudgeResult.PaperMismatch(
            sheetExportId = sheet.identity.exportId,
            sheetFingerprint = sheet.identity.paperFingerprint,
            chainExportId = chain.exportId,
            chainFingerprint = chain.paperFingerprint,
        )
    }
    val verdicts = sheet.answers.map { answer ->
        val attemptKey = sheet.identity.attemptKeyFor(answer.paperQuestionId)
        val link = chain.linkFor(answer.paperQuestionId)
        when {
            link == null -> PaperAnswerRecycleVerdict.UnmappedQuestion(
                paperQuestionId = answer.paperQuestionId,
                attemptKey = attemptKey,
                selectedChoiceId = answer.selectedChoiceId,
            )

            answer.selectedChoiceId == null -> PaperAnswerRecycleVerdict.NotMachineCheckable(
                paperQuestionId = answer.paperQuestionId,
                attemptKey = attemptKey,
                selectedChoiceId = null,
                reason = PaperNotMachineCheckableReason.OPEN_ANSWER,
            )

            answer.selectedChoiceId !in link.choiceIds -> PaperAnswerRecycleVerdict.NotMachineCheckable(
                paperQuestionId = answer.paperQuestionId,
                attemptKey = attemptKey,
                selectedChoiceId = answer.selectedChoiceId,
                reason = PaperNotMachineCheckableReason.ANSWER_OUTSIDE_OPTIONS,
            )

            !hasObjectiveForm(link) -> PaperAnswerRecycleVerdict.NotMachineCheckable(
                paperQuestionId = answer.paperQuestionId,
                attemptKey = attemptKey,
                selectedChoiceId = answer.selectedChoiceId,
                reason = if (link.correctChoiceId != null && link.correctChoiceId !in link.choiceIds) {
                    PaperNotMachineCheckableReason.KEY_OUTSIDE_OPTIONS
                } else {
                    PaperNotMachineCheckableReason.NO_OBJECTIVE_FORM
                },
            )

            link.keyTrust.mayWriteNumericEvidence -> {
                val correct = link.correctChoiceId
                checkNotNull(correct) { "An objective form guarantees a correct choice id" }
                val isCorrect = answer.selectedChoiceId == correct
                if (isCorrect) {
                    PaperAnswerRecycleVerdict.LocalCheckedCorrect(
                        paperQuestionId = answer.paperQuestionId,
                        attemptKey = attemptKey,
                        selectedChoiceId = answer.selectedChoiceId,
                        correctChoiceId = correct,
                        practiceUnitId = link.practiceUnitId,
                        problemRevisionId = link.problemRevisionId,
                        keyVersion = link.keyTrust.keyVersion,
                        keyVerification = link.keyTrust.verification,
                    )
                } else {
                    PaperAnswerRecycleVerdict.LocalCheckedIncorrect(
                        paperQuestionId = answer.paperQuestionId,
                        attemptKey = attemptKey,
                        selectedChoiceId = answer.selectedChoiceId,
                        correctChoiceId = correct,
                        practiceUnitId = link.practiceUnitId,
                        problemRevisionId = link.problemRevisionId,
                        keyVersion = link.keyTrust.keyVersion,
                        keyVerification = link.keyTrust.verification,
                    )
                }
            }

            else -> PaperAnswerRecycleVerdict.PendingVerification(
                paperQuestionId = answer.paperQuestionId,
                attemptKey = attemptKey,
                selectedChoiceId = answer.selectedChoiceId,
                correctChoiceId = checkNotNull(link.correctChoiceId) {
                    "A pending-verification question passed the objective-form gate"
                },
                practiceUnitId = link.practiceUnitId,
                problemRevisionId = link.problemRevisionId,
                keyVersion = link.keyTrust.keyVersion,
            )
        }
    }
    return PaperAnswerSheetJudgeResult.Judged(verdicts)
}

/** 客观形态：≥2 选项且键存在且键落在选项集内（不含键信任轴）。 */
private fun hasObjectiveForm(link: PaperAnswerKeyLink): Boolean =
    link.choiceIds.size >= 2 &&
        link.correctChoiceId != null &&
        link.correctChoiceId in link.choiceIds
