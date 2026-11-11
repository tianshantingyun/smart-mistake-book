package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationCandidate
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationConsumer
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * KD-67 产品面：`CLARIFY_OR_CONFIRM` 决策的消费出口（feature/review 侧实现）。
 *
 * 消灭的失败（三重，均可在改前现场指认）：
 * 1. **决策被算出来就丢掉**：`AdaptiveSelectionAssembler.select` 早已把澄清载荷
 *    (`AdaptiveSelectionOutcome.clarification`) 算出来，但 feature/app 全仓没有一个
 *    实现方——学生看不到"这道题的答案还没核对"，也没有确认/换题/取消的出口；
 * 2. **重复回调冲掉学生操作**：`AdaptiveClarificationConsumer` 的契约写明"同一决策重放可能
 *    重复调用，实现方需幂等"。若每次回调都重建卡片，学生正在点的"确认"会被重置回未忙态，
 *    同一次确认可能被重复发起；
 * 3. **确认没有落到具体题面**：卡片必须带着决策时刻的候选列表，确认只能是列表里的某个
 *    `assessmentItemId`；否则确认结果可能落到另一道题上（核验升级按 `(会话, 知识点)` 定位，
 *    与候选无关的 id 只会让写方回 `SnapshotNotFound`，学生看到的是"确认成功"却没有变化）。
 *
 * 本类**不写库、不调模型**：确认的真正写路径由 app 注入的
 * `KnowledgeQuizItemVerificationUpgrader`（单列 CAS）承担；卡片只表达"学生确认了哪个候选"
 * 与"这次确认失败了、可以重试"。
 *
 * 线程：状态是 [StateFlow]，可在任意线程写；[onClarificationRequired] 是挂起函数但只做
 * 纯内存状态迁移（接口是 suspend 便于消费者将来接异步 UI）。
 */
class KnowledgeClarificationPresenter : AdaptiveClarificationConsumer {

    private val _state = MutableStateFlow<KnowledgeClarificationState>(KnowledgeClarificationState.Hidden)

    /** 屏幕观察的卡片状态。 */
    val state: StateFlow<KnowledgeClarificationState> = _state.asStateFlow()

    /**
     * 已解决的请求键：同一决策重放**不得**把卡片重新打开（学生已经确认/换题/取消过）。
     * 键包含决策时刻与候选集合——选择器在下一时刻给出新决策时是新事实，必须能再打开。
     */
    private val resolvedRequestKeys = mutableSetOf<String>()

    override suspend fun onClarificationRequired(request: AdaptiveClarificationRequest) {
        val key = request.presentationKey()
        if (key in resolvedRequestKeys) return
        val current = _state.value
        // 同一请求重放（重组/重试）：保留忙态与上次错误，不重置学生正在做的操作。
        if (current is KnowledgeClarificationState.Asking && current.request.presentationKey() == key) {
            return
        }
        _state.value = KnowledgeClarificationState.Asking(request)
    }

    /** 当前待处理请求（确认写路径据此定位目标知识点）；没有 = null。 */
    fun currentRequest(): AdaptiveClarificationRequest? =
        (_state.value as? KnowledgeClarificationState.Asking)?.request

    /**
     * 学生点了"确认"：只把卡片切到忙态。返回 false 的四种情形都不发起写路径：
     * 当前没有卡片 / 已在忙 / 候选不在本次决策的候选列表里 / **本次决策不是因为键未核验**
     * （`CLARIFY_OR_CONFIRM` 也会由证据过期、预测越界等原因触发，那些原因下"确认答案"
     * 不是解法——见 `docs/research/2026-11-08-clarification-ux.md` §2.4）。
     */
    fun beginConfirm(candidateId: String): Boolean {
        val current = _state.value as? KnowledgeClarificationState.Asking ?: return false
        if (current.busy) return false
        if (!current.request.isAnswerKeyConfirmationAllowed()) return false
        if (current.request.candidates.none { it.assessmentItemId == candidateId }) return false
        _state.value = current.copy(busy = true, error = null)
        return true
    }

    /** 确认写路径成功（已升级为 `USER_ASSERTED`，或该行本来已是目标状态）→ 卡片关闭。 */
    fun completeConfirm() {
        val current = _state.value as? KnowledgeClarificationState.Asking ?: return
        resolvedRequestKeys += current.request.presentationKey()
        _state.value = KnowledgeClarificationState.Resolved(KnowledgeClarificationOutcome.CONFIRMED)
    }

    /**
     * 确认写路径失败：回到可重试的卡片并显示**写方给出的真实结果**（不把拒绝/异常说成
     * "已确认"）。失败不标记为已解决——学生可以再试一次。
     */
    fun failConfirm(message: String) {
        val current = _state.value as? KnowledgeClarificationState.Asking ?: return
        _state.value = current.copy(busy = false, error = message)
    }

    /** 换题/取消：卡片关闭，且同一决策重放不再打开（学生已经做过选择）。 */
    fun finish(outcome: KnowledgeClarificationOutcome) {
        val current = _state.value as? KnowledgeClarificationState.Asking ?: return
        resolvedRequestKeys += current.request.presentationKey()
        _state.value = KnowledgeClarificationState.Resolved(outcome)
    }
}

/** 确认卡状态机。 */
sealed interface KnowledgeClarificationState {
    /** 没有待确认的决策（会话正常、或已关闭卡片）。 */
    data object Hidden : KnowledgeClarificationState

    /** 有一张待学生处理的确认卡。[error] 非 null = 上次确认失败的真实原因。 */
    data class Asking(
        val request: AdaptiveClarificationRequest,
        val busy: Boolean = false,
        val error: String? = null,
    ) : KnowledgeClarificationState

    /** 学生已处理完这张卡（确认 / 换题 / 取消）。 */
    data class Resolved(val outcome: KnowledgeClarificationOutcome) : KnowledgeClarificationState
}

/** 卡片关闭时学生的选择。 */
enum class KnowledgeClarificationOutcome {
    /** 学生确认了题面答案（写路径走单列 CAS 升级为 `USER_ASSERTED`）。 */
    CONFIRMED,

    /** 学生不愿确认这道题：换一道（当前实现 = 跳过该知识点，见屏幕 KDoc）。 */
    NEW_QUESTION,

    /** 学生取消：这次决策不再打扰，本次会话继续。 */
    CANCELLED,
}

/** 卡片上一条候选（题面）的展示投影。 */
internal data class KnowledgeClarificationCandidateRow(
    val assessmentItemId: String,
    val knowledgeNodeIds: List<String>,
)

/**
 * 卡片展示模型（纯函数产出，便于单测）。
 *
 * [reasonLines] 只出现面向学生的人话；**未知原因码不原样外泄**（内部枚举名对学生的
 * 信息量为零，还会暴露实现细节），退化为一条诚实的通用说明。
 */
internal data class KnowledgeClarificationCardModel(
    val title: String,
    val reasonLines: List<String>,
    val candidates: List<KnowledgeClarificationCandidateRow>,
    val selectorVersion: String,
    /** 本次决策是否因"题面键未核验"而起：只有这种原因下"确认答案"才是解法。 */
    val confirmable: Boolean,
)

/** 只有这个原因码意味着"确认这个键"能解开选择器的阻塞（选择器 `noCandidateDecision` 同源）。 */
internal const val CLARIFY_REASON_TARGET_CONTENT_NOT_VERIFIED = "TARGET_CONTENT_NOT_VERIFIED"

internal fun AdaptiveClarificationRequest.isAnswerKeyConfirmationAllowed(): Boolean =
    CLARIFY_REASON_TARGET_CONTENT_NOT_VERIFIED in reasonCodes

/**
 * 这一次决策里"可以确认"的题面 id（`null` = 不提供确认动作）。
 *
 * 三条判据缺一不可：
 * 1. 原因必须是"题面键未核验"（其他原因下确认答案不是解法）；
 * 2. 屏幕上**确实有**这道题（[currentItemId] 非空）；
 * 3. 这个 id 在决策时刻的候选列表里（不在列表里 = 决策之后题面换了，不能拿旧候选确认新题）。
 * 错题行候选即使出现在列表里也没有键面（装配器恒 `contentVerified=false`），
 * 判据 2 会把它们挡在外面——那正是活按钮变死按钮的那条路径。
 */
internal fun knowledgeClarificationConfirmCandidateId(
    request: AdaptiveClarificationRequest,
    currentItemId: String?,
): String? {
    if (currentItemId == null) return null
    if (!request.isAnswerKeyConfirmationAllowed()) return null
    return request.candidates
        .firstOrNull { it.assessmentItemId == currentItemId }
        ?.assessmentItemId
}

internal fun knowledgeClarificationCardModel(
    request: AdaptiveClarificationRequest,
): KnowledgeClarificationCardModel = KnowledgeClarificationCardModel(
    title = if (request.isAnswerKeyConfirmationAllowed()) {
        "这道题的答案还没有核对"
    } else {
        "这个知识点先换了道题"
    },
    reasonLines = knowledgeClarificationReasonLines(request.reasonCodes),
    candidates = request.candidates
        .sortedBy(AdaptiveClarificationCandidate::assessmentItemId)
        .map { candidate ->
            KnowledgeClarificationCandidateRow(
                assessmentItemId = candidate.assessmentItemId,
                knowledgeNodeIds = candidate.knowledgeNodeIds.sorted(),
            )
        },
    selectorVersion = request.selectorVersion,
    confirmable = request.isAnswerKeyConfirmationAllowed(),
)

/**
 * 原因码 → 学生可读说明（顺序稳定：按码字典序，去重）。
 *
 * 与 `knowledgeQuizRejectedReasonText` 同一口径：内部枚举名不直接端上屏幕。
 */
internal fun knowledgeClarificationReasonLines(reasonCodes: Set<String>): List<String> =
    reasonCodes.sorted().distinct().map { code ->
        when (code) {
            "TARGET_CONTENT_NOT_VERIFIED" ->
                "这道题的答案还没有经过独立核对，这次只记录你的选择，不计入掌握度。"
            "PREDICTION_UNKNOWN_STALE_OR_OUTSIDE_CORRIDOR" ->
                "系统暂时算不准你对这个知识点的把握，先确认一道合适的题。"
            "LEARNING_EVIDENCE_NOT_CURRENT" ->
                "你的学习记录还没结算完，先确认一道题继续。"
            "PROJECTION_NOT_CURRENT" ->
                "学习记录正在更新，先确认一道题继续。"
            "NO_SAFE_CANDIDATE" ->
                "这个知识点暂时没有可以直接用的题。"
            else -> "这道题暂时不能直接用于复习。"
        }
    }

/**
 * 请求的展示身份：同一决策重放 = 同键；新决策（时刻/候选/原因变了） = 新键。
 *
 * 不把 [AdaptiveClarificationRequest.learnerId] 排除在外：同一屏幕换了学习者必须重新确认。
 */
internal fun AdaptiveClarificationRequest.presentationKey(): String = buildString {
    append(learnerId)
    append('|')
    append(selectorVersion)
    append('|')
    append(decisionAtEpochMillis)
    append('|')
    append(targetKnowledgeNodeIds.sorted().joinToString(","))
    append('|')
    append(candidates.map(AdaptiveClarificationCandidate::assessmentItemId).sorted().joinToString(","))
    append('|')
    append(reasonCodes.sorted().joinToString(","))
}
