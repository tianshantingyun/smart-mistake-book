package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyTrustDecision
import com.tingyun.smartmistakebook.core.model.answerKeyTrustDecision

/**
 * 一份导出卷面的**答案键链**：卷面题 ↔ 库题 ↔ 答案来源（T5/5B-G8）。
 *
 * 消灭的失败：G8 现状里出卷侧没有答案键/题映射链（`MistakeExportRecord` 无 answerKey 字段族，
 * 批次 19 计划 §0 已核），回收时"学生选的是哪个选项、该对照哪个库题的哪个键、键是否可信"
 * 无处对齐——回收判定要么无从做起，要么各处自造对照规则。
 *
 * [exportId]/[paperFingerprint] 与 [MistakeExportRecord] 的同名字段同义（导出记录身份与卷面
 * 内容指纹），只读消费：导出侧在成功导出时生成并持久化（真实接线见他线热区 `core/export`
 * 目录，登记遗留；本批由 [PaperAnswerKeyChainStore] 的内存实现承担契约）。
 */
data class PaperAnswerKeyChain(
    val exportId: String,
    val paperFingerprint: String,
    val createdAtEpochMillis: Long,
    val links: List<PaperAnswerKeyLink>,
) {
    init {
        require(exportId.isNotBlank()) { "A key chain needs its export id" }
        require(paperFingerprint.isNotBlank()) { "A key chain needs its paper fingerprint" }
        require(createdAtEpochMillis >= 0) { "A key chain creation time must not be negative" }
        require(links.isNotEmpty()) { "A key chain needs at least one link" }
        require(links.map(PaperAnswerKeyLink::paperQuestionId).distinct().size == links.size) {
            "One paper question maps to at most one library link"
        }
    }

    fun linkFor(paperQuestionId: String): PaperAnswerKeyLink? =
        links.firstOrNull { link -> link.paperQuestionId == paperQuestionId }
}

/**
 * 键链的一条映射。答案来源复用 PRD-02 的两轴判定（[AnswerKeyTrustDecision]，core:model
 * 单源），不新造语义：`AUTO_VERIFIED` 式"本地可比较"不蕴含键已核验。
 */
data class PaperAnswerKeyLink(
    val paperQuestionId: String,
    val practiceUnitId: String,
    val problemRevisionId: String,
    /** 卷面该题的选项 ID 集（与渲染卷面同一版本同源）。 */
    val choiceIds: List<String>,
    /** 答案规格里的正确选项 ID（导出侧从库内答案规格解析）；null = 无键（开放题/拍摄保存题）。原值保留，不因形态问题改写。 */
    val correctChoiceId: String?,
    /** 键信任判定（fold 后结果，来源见 [buildPaperAnswerKeyLink]）。 */
    val keyTrust: AnswerKeyTrustDecision,
) {
    init {
        require(paperQuestionId.isNotBlank()) { "A paper question id must not be blank" }
        require(practiceUnitId.isNotBlank()) { "A library practice-unit id must not be blank" }
        require(problemRevisionId.isNotBlank()) { "A problem revision id must not be blank" }
        require(choiceIds.none(String::isBlank)) { "Choice ids must not be blank" }
        require(choiceIds.distinct().size == choiceIds.size) {
            "Choice ids must be distinct"
        }
        require(correctChoiceId == null || correctChoiceId.isNotBlank()) {
            "A correct choice id must not be blank when provided"
        }
    }

    /**
     * 客观题本地判可用 = 键可信（可写数值证据）**且**形态可对照（≥2 选项、键落在选项集内）。
     * 两轴缺一即 false——键不可信对照结果是噪声，键不在选项集内根本无从对照（与库内机判
     * 门槛 `StudyPracticeUnitFacts.assessmentItemOf` 同规）。
     */
    val locallyCheckable: Boolean
        get() = keyTrust.mayWriteNumericEvidence &&
            correctChoiceId != null &&
            choiceIds.contains(correctChoiceId) &&
            choiceIds.size >= 2
}

/**
 * 导出侧装配键链时的原始分量（尚未 fold 信任）：正确选项 ID 由导出侧从库内答案规格
 * （`single-choice.v1`）解析，核验状态原值来自库内列——未知字符串一律按 UNKNOWN 处理。
 */
data class PaperAnswerKeyLinkDescriptor(
    val paperQuestionId: String,
    val practiceUnitId: String,
    val problemRevisionId: String,
    val choiceIds: List<String>,
    val correctChoiceId: String?,
    /** 库内答案核验状态落库原值（`StudyDbValue.VerificationStatus` 字符串）；null/未知 = UNKNOWN。 */
    val answerVerificationStatus: String?,
    /** 键版本（answerSpecId）；null = 版本缺失，如实报 unknown-key-version。 */
    val answerSpecId: String?,
)

/**
 * 装配一条键链映射：把库内核验状态原值 fail-closed 地 fold 成 [AnswerKeyTrustDecision]。
 *
 * `itemReadable = true`：能给出描述子本身即说明卷面题是从可读的结构化题面渲染出来的
 * （导出先读了题面才渲染）；题面读不回的题根本不会出现在卷面上。
 */
fun buildPaperAnswerKeyLink(descriptor: PaperAnswerKeyLinkDescriptor): PaperAnswerKeyLink =
    PaperAnswerKeyLink(
        paperQuestionId = descriptor.paperQuestionId,
        practiceUnitId = descriptor.practiceUnitId,
        problemRevisionId = descriptor.problemRevisionId,
        choiceIds = descriptor.choiceIds,
        correctChoiceId = descriptor.correctChoiceId,
        keyTrust = answerKeyTrustDecision(
            verificationStatus = descriptor.answerVerificationStatus,
            itemReadable = true,
            keyVersion = descriptor.answerSpecId,
        ),
    )

/** 装配整条键链（逐条 fold 信任；重复卷面题/非法分量在 [PaperAnswerKeyChain] 构造时显式失败）。 */
fun buildPaperAnswerKeyChain(
    exportId: String,
    paperFingerprint: String,
    createdAtEpochMillis: Long,
    descriptors: List<PaperAnswerKeyLinkDescriptor>,
): PaperAnswerKeyChain = PaperAnswerKeyChain(
    exportId = exportId,
    paperFingerprint = paperFingerprint,
    createdAtEpochMillis = createdAtEpochMillis,
    links = descriptors.map(::buildPaperAnswerKeyLink),
)

/**
 * 键链持久化口：出卷侧写（随导出记录）、回收侧读。
 *
 * 真实持久化（随导出记录落库）登记为**遗留**——导出侧 `core/export` 目录是他线热区，本批不
 * 接线；契约先行（内存实现见 core:data `InMemoryPaperAnswerKeyChainStore`）。
 */
interface PaperAnswerKeyChainStore {
    /** 保存/替换一条键链：同 (exportId, 指纹) 以最新为准（同卷面重导出覆盖，不同指纹互不影响）。 */
    suspend fun saveChain(chain: PaperAnswerKeyChain)

    /** 读回键链；null = 该导出没有键链（旧导出/导出侧未接线）。 */
    suspend fun readChain(exportId: String, paperFingerprint: String): PaperAnswerKeyChain?
}
