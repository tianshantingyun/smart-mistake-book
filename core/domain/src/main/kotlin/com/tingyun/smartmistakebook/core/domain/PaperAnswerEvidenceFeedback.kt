package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification

/**
 * 纸质答卷 LOCAL_CHECKED 证据的回灌端口（T5/5B-G8）。
 *
 * 消灭的失败：判定结果若由消费侧各自落库，会绕开既有 attempt/review_log 写入路径形成旁路
 * 投影写，并把 MODEL_JUDGED / REVEAL 语义混进回收样本（AUD-17 样本污染）。端口把语义钉死：
 *
 * - **只收本地判结果**（[PaperAnswerRecycleVerdict.LocalCheckedCorrect] /
 *   [LocalCheckedIncorrect] 档才允许构造本命令）——source kind 恒 `ReviewSample.LOCAL_CHECKED_KIND`
 *   （台账裁决 18：本地核对是干净信号、参与拟合；先例 `TutorJudgedReviewSettler`）；
 * - **幂等**：同一 [PaperLocalCheckedEvidenceCommand.attemptKey] 重放 created=false，不重复计分；
 * - **不新建旁路投影写**：实现必须落在既有 attempt / review_log 写入路径（与
 *   `StudyExperienceRepository.submitReviewChoice` 同型的入口语义），由实现侧保证。
 */
interface PaperAnswerEvidenceFeedbackPort {
    suspend fun submitLocalCheckedEvidence(
        command: PaperLocalCheckedEvidenceCommand,
    ): PaperEvidenceFeedbackReceipt
}

/** 一笔本地判证据回灌命令（只描述 LOCAL_CHECKED 档；键来源随行携带供 PRD-02 迟到校验）。 */
data class PaperLocalCheckedEvidenceCommand(
    /** 幂等键 = [PaperAnswerSheetIdentity.attemptKeyFor]；同键重放不重复计分。 */
    val attemptKey: String,
    /** 卷面来源导出记录 id（溯源，不参与计分身份）。 */
    val paperExportId: String,
    val practiceUnitId: String,
    val problemRevisionId: String,
    val selectedChoiceId: String,
    val correctChoiceId: String,
    val isCorrect: Boolean,
    /** 键版本（answerSpecId；缺失时为 unknown-key-version）——迟到旧判分按它校验。 */
    val keyVersion: String,
    /** 键来源（VERIFIED / USER_ASSERTED；只有这两档能走到本命令）。 */
    val keyVerification: AnswerKeyVerification,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(attemptKey.isNotBlank()) { "An evidence command needs its attempt key" }
        require(paperExportId.isNotBlank()) { "An evidence command needs its paper export id" }
        require(practiceUnitId.isNotBlank()) { "An evidence command needs its practice unit id" }
        require(problemRevisionId.isNotBlank()) { "An evidence command needs its problem revision id" }
        require(selectedChoiceId.isNotBlank()) { "A selected choice id must not be blank" }
        require(correctChoiceId.isNotBlank()) { "A correct choice id must not be blank" }
        require(occurredAtEpochMillis >= 0) { "An evidence time must not be negative" }
        require(
            keyVerification == AnswerKeyVerification.VERIFIED ||
                keyVerification == AnswerKeyVerification.USER_ASSERTED,
        ) { "Only independently supported keys may produce local-checked evidence" }
    }
}

/** 回灌回执：created=false = 该 attemptKey 已计过（幂等重放）。 */
data class PaperEvidenceFeedbackReceipt(
    val attemptKey: String,
    val created: Boolean,
)

/**
 * 开放题判词通道的**接口形态**（G8 半边只留接口；模型侧实现待 5B 契约后接入，本批无实现）。
 *
 * 语义对齐讲题判定的模型判词档（`ReviewSample.MODEL_JUDGED_KIND`：κ≈0.70 判定噪声、不参与
 * 拟合、校准单列）——实现落地时不得把判词写成 LOCAL_CHECKED 或 ATTEMPT。默认抛错与
 * `StudyExperienceRepository.submitReviewSelfReport` 同型：旧实现必须显式接入，不静默吞掉。
 */
interface PaperOpenAnswerJudgingChannel {
    suspend fun judgeOpenAnswer(
        request: PaperOpenAnswerJudgeRequest,
    ): PaperOpenAnswerJudgeOutcome =
        throw UnsupportedOperationException(
            "开放题判词通道本批未实现（5B 契约后模型侧接入；G8 半边只留接口形态）",
        )
}

/** 判词请求：[studentAnswerEvidence] 必须是学生原始作答证据的引用（生成图/清图不得冒充，WG-09）。 */
data class PaperOpenAnswerJudgeRequest(
    val attemptKey: String,
    val practiceUnitId: String,
    val problemRevisionId: String,
    val studentAnswerEvidence: String,
) {
    init {
        require(attemptKey.isNotBlank()) { "A judging request needs its attempt key" }
        require(practiceUnitId.isNotBlank()) { "A judging request needs its practice unit id" }
        require(problemRevisionId.isNotBlank()) { "A judging request needs its problem revision id" }
        require(studentAnswerEvidence.isNotBlank()) { "A judging request needs the student's answer evidence" }
    }
}

/** 判词结论：UNCERTAIN = 模型不下结论（不写任何证据，不是猜一个档）。 */
enum class PaperOpenAnswerJudgeOutcome {
    CORRECT,
    INCORRECT,
    UNCERTAIN,
}
