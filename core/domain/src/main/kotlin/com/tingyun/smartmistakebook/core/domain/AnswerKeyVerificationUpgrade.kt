package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AnswerKeyVerification

/**
 * 答案键**核验升级**的来源（PRD-02 两轴语义的显式载体）。
 *
 * PRD-02 把"本地机械可判"（`scoringMode`）与"键本身有独立支持"（`verificationStatus`）
 * 拆成两条轴，后者只有三档（[AnswerKeyVerification]）：`VERIFIED` = 独立核验；
 * `USER_ASSERTED` = 用户明确更正/确认且来源如实保留；`UNKNOWN` = 模型生成且未核验。
 * 本类型把"凭什么升级"变成调用方必须显式给出的值——不允许只传一个目标枚举就完成升级，
 * 因为那样 `USER_ASSERTED` 就会退化成"谁都能写的一档"，与 PRD-02 的来源语义相悖。
 *
 * 两个来源各自要求可追溯的凭据字段：
 * - [IndependentVerification.verificationId]：独立核验记录/流程的 id（人工复核、正式题源等）。
 * - [UserAssertion.confirmationId] + [UserAssertion.confirmedByLearnerId]：用户这次**明确
 *   确认/更正**的记录 id 与确认者。future 确认流应传既有更正回执的
 *   `AnswerKeyCorrectionReceipt.correctionId`（或等价的确认记录 id），不是自由文本；
 *   确认者必须是该题面所属学习者（由 [decideAnswerKeyVerificationUpgrade] 校验）。
 *
 * 消灭的失败：升级 API 只收"目标状态"时，调用方可以用一条与本题无关的来源把模型生成的键
 * 标成 `VERIFIED`，或把"没有任何用户确认"的行标成 `USER_ASSERTED`，选择器的
 * `contentVerified` 判据随即被绕过——这正是 KD-67 死端最危险的错误解法。
 */
sealed interface AnswerKeyVerificationSource {
    /** 独立核验（人工/独立流程）来源。 */
    data class IndependentVerification(
        /** 独立核验记录/流程 id；空白不可构造。 */
        val verificationId: String,
        val verifiedAtEpochMillis: Long,
    ) : AnswerKeyVerificationSource {
        init {
            require(verificationId.isNotBlank()) {
                "Independent verification id must not be blank"
            }
            require(verifiedAtEpochMillis >= 0) {
                "Independent verification time must not be negative"
            }
        }
    }

    /** 用户明确确认/更正来源。两个字段缺一不可，且确认者必须与题面学习者一致。 */
    data class UserAssertion(
        /** 用户确认/更正记录的 id（如更正回执的 correctionId）。 */
        val confirmationId: String,
        /** 做出确认的学习者 id。 */
        val confirmedByLearnerId: String,
        val confirmedAtEpochMillis: Long,
    ) : AnswerKeyVerificationSource {
        init {
            require(confirmationId.isNotBlank()) {
                "User confirmation id must not be blank"
            }
            require(confirmedByLearnerId.isNotBlank()) {
                "User confirmation learner id must not be blank"
            }
            require(confirmedAtEpochMillis >= 0) {
                "User confirmation time must not be negative"
            }
        }
    }
}

/** 一次核验升级请求；[currentVerification] 必须是写方刚读到的库内值，不得由调用方猜。 */
data class AnswerKeyVerificationUpgradeRequest(
    val learnerId: String,
    val assessmentItemSnapshotId: String,
    val currentVerification: AnswerKeyVerification,
    val targetVerification: AnswerKeyVerification,
    val source: AnswerKeyVerificationSource,
) {
    init {
        require(learnerId.isNotBlank()) { "Learner id must not be blank" }
        require(assessmentItemSnapshotId.isNotBlank()) {
            "Assessment item snapshot id must not be blank"
        }
    }
}

/** 升级被拒的具体原因（消费方可据此区分"已升级"与"来源不符"）。 */
enum class AnswerKeyVerificationUpgradeRejection {
    /** 目标不是一次升级（`UNKNOWN` 不能作为目标）。 */
    TARGET_IS_NOT_AN_UPGRADE,

    /** 只有 `UNKNOWN` 可以升级；已核验行不得改写/降级。 */
    CURRENT_IS_NOT_UNVERIFIED,

    /** 来源类型与目标不匹配（`VERIFIED` ↔ 独立核验；`USER_ASSERTED` ↔ 用户确认）。 */
    SOURCE_DOES_NOT_MATCH_TARGET,

    /** `USER_ASSERTED` 的确认者不是该学习者——不是在确认自己的题面。 */
    USER_CONFIRMATION_LEARNER_MISMATCH,

    /**
     * 来源 id 没有对应的真实回执/核验记录，或该记录不属于本学习者/本题面。
     * 只校验"非空字符串"会让任意 UI 用一条自造 id 把 UNKNOWN 写成已核验。
     */
    SOURCE_NOT_VERIFIED,
}

/**
 * 来源回执校验端口：确认流在调用升级 API 前，必须证明
 * [AnswerKeyVerificationSource.UserAssertion.confirmationId] /
 * [AnswerKeyVerificationSource.IndependentVerification.verificationId] 指向一条真实存在、
 * 且属于同一学习者与同一题面的记录。
 *
 * 消灭的失败（批次 22 复核 #13）：升级 API 只校验 id 非空时，任何调用方都能用
 * `confirmationId = "随便一个非空串"` + 当前学习者 id 把 UNKNOWN 写成 USER_ASSERTED；
 * 纯领域函数无法查库，所以把"回执存在性"作为显式端口放进写路径，而不是靠调用方自觉。
 * 生产 adapter（更正回执/独立核验记录）与 UI 接线同批落地；未接线前该端口没有实现。
 */
fun interface AnswerKeyVerificationSourceVerifier {
    suspend fun verify(
        source: AnswerKeyVerificationSource,
        learnerId: String,
        assessmentItemSnapshotId: String,
    ): Boolean
}

sealed interface AnswerKeyVerificationUpgradeDecision {
    data class Allowed(
        val targetVerification: AnswerKeyVerification,
        val source: AnswerKeyVerificationSource,
    ) : AnswerKeyVerificationUpgradeDecision

    data class Rejected(
        val reason: AnswerKeyVerificationUpgradeRejection,
    ) : AnswerKeyVerificationUpgradeDecision
}

/**
 * 核验升级的纯决策函数（PRD-02 键来源语义，KD-67 写方 API 的领域半边）。
 *
 * 判据（顺序即拒绝优先级）：
 * 1. 目标只能是 `VERIFIED` / `USER_ASSERTED`——升级不是"写回 UNKNOWN"；
 * 2. 当前必须是 `UNKNOWN`——已核验行不可借升级口改写（包括 `USER_ASSERTED` → `VERIFIED`
 *    的二次升级，本批不裁决其来源语义，保持 fail-closed）；
 * 3. 来源必须与目标同型：`VERIFIED` 只收 [AnswerKeyVerificationSource.IndependentVerification]，
 *    `USER_ASSERTED` 只收 [AnswerKeyVerificationSource.UserAssertion]；
 * 4. `USER_ASSERTED` 的确认者必须等于 [AnswerKeyVerificationUpgradeRequest.learnerId]。
 *
 * 本函数不读写数据库、不看时钟：库内当前值由写方读入 [AnswerKeyVerificationUpgradeRequest]；
 * 同一请求重复执行得到同一决策（幂等由写方的 CAS 与"已升级"出口承担）。
 */
fun decideAnswerKeyVerificationUpgrade(
    request: AnswerKeyVerificationUpgradeRequest,
): AnswerKeyVerificationUpgradeDecision {
    if (request.targetVerification == AnswerKeyVerification.UNKNOWN) {
        return AnswerKeyVerificationUpgradeDecision.Rejected(
            AnswerKeyVerificationUpgradeRejection.TARGET_IS_NOT_AN_UPGRADE,
        )
    }
    if (request.currentVerification != AnswerKeyVerification.UNKNOWN) {
        return AnswerKeyVerificationUpgradeDecision.Rejected(
            AnswerKeyVerificationUpgradeRejection.CURRENT_IS_NOT_UNVERIFIED,
        )
    }
    val sourceMatchesTarget = when (request.targetVerification) {
        AnswerKeyVerification.VERIFIED ->
            request.source is AnswerKeyVerificationSource.IndependentVerification

        AnswerKeyVerification.USER_ASSERTED ->
            request.source is AnswerKeyVerificationSource.UserAssertion

        AnswerKeyVerification.UNKNOWN -> false
    }
    if (!sourceMatchesTarget) {
        return AnswerKeyVerificationUpgradeDecision.Rejected(
            AnswerKeyVerificationUpgradeRejection.SOURCE_DOES_NOT_MATCH_TARGET,
        )
    }
    if (request.targetVerification == AnswerKeyVerification.USER_ASSERTED) {
        val assertion = request.source as AnswerKeyVerificationSource.UserAssertion
        if (assertion.confirmedByLearnerId != request.learnerId) {
            return AnswerKeyVerificationUpgradeDecision.Rejected(
                AnswerKeyVerificationUpgradeRejection.USER_CONFIRMATION_LEARNER_MISMATCH,
            )
        }
    }
    return AnswerKeyVerificationUpgradeDecision.Allowed(
        targetVerification = request.targetVerification,
        source = request.source,
    )
}
