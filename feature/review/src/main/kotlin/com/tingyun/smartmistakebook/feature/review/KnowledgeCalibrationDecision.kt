package com.tingyun.smartmistakebook.feature.review

import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind

/**
 * 「先做校准题」的选择结果（批次 49 修复环）。
 *
 * 消灭的失败：`SmartMistakeBookRoot` 调 `selectKnowledgeReviewQuestion(CALIBRATION)`
 * 后把 `AdaptiveDecision` 丢弃，屏幕只能笼统显示「本次按校准题处理」。学生与验收都
 * 无法知道选择器是否真的给出校准题面——按钮看起来生效、实际决策没人消费。
 *
 * 本类型把决策原样带回 feature 侧，由会话状态机持久化并显示：
 * - `decisionKind == ASK_CALIBRATION` 且 [selectedAssessmentItemId] 非空 = 选择器给出了
 *   校准题面（现有候选池里是本次会话已落库的那道题）；
 * - 其余决策（CLARIFY/NO_SAFE/SKIP/ASK）= 选择器未给出校准题面，屏幕必须如实说明，
 *   不能继续显示「按校准题处理」而不说结果。
 *
 * 刻意不带写入能力：确认/升级仍走既有单列 CAS，本对象只读。
 */
data class KnowledgeCalibrationDecision(
    val decisionKind: AdaptiveDecisionKind,
    val reasonCodes: Set<String>,
    val selectedAssessmentItemId: String?,
) {
    /** 选择器是否真的选中了一道校准题面。 */
    val isCalibrationQuestion: Boolean
        get() = decisionKind == AdaptiveDecisionKind.ASK_CALIBRATION &&
            !selectedAssessmentItemId.isNullOrBlank()

    /** SavedStateHandle 持久化形态；reason code 是常量名，不含分隔符。 */
    fun serialized(): String =
        "${decisionKind.name}|${reasonCodes.sorted().joinToString(",")}|" +
            selectedAssessmentItemId.orEmpty()

    companion object {
        fun parse(raw: String): KnowledgeCalibrationDecision? {
            val parts = raw.split('|', limit = 3)
            if (parts.size != 3) return null
            val kind = runCatching { AdaptiveDecisionKind.valueOf(parts[0]) }.getOrNull()
                ?: return null
            val reasons = parts[1].split(',').filter(String::isNotEmpty).toSet()
            return KnowledgeCalibrationDecision(
                decisionKind = kind,
                reasonCodes = reasons,
                selectedAssessmentItemId = parts[2].takeIf(String::isNotEmpty),
            )
        }
    }
}

/** 会话屏上「本次按校准题处理」一行的状态。 */
internal enum class KnowledgeCalibrationDecisionStatus {
    /** 卡还没点，或本节点没有校准请求。 */
    IDLE,

    /** 已点「先做校准题」，选择器调用进行中。 */
    SELECTING,

    /** 选择器返回了决策（是否给出校准题面由 [KnowledgeCalibrationDecision] 决定）。 */
    READY,

    /** 选择器调用失败/返回 null：本次仍按正常复习记录。 */
    FAILED,
}

/**
 * 校准状态文案（纯函数，JVM 可测）。
 *
 * 文案是**行为承诺**：READY 且未选中时明确说「未给出校准题」，避免把「按校准题处理」
 * 读成「多了一道校准题」；这正是不再写「校准题只多问一道」的原因。
 */
internal fun knowledgeCalibrationStatusText(
    status: KnowledgeCalibrationDecisionStatus,
    decision: KnowledgeCalibrationDecision?,
): String = when (status) {
    KnowledgeCalibrationDecisionStatus.IDLE ->
        "本次按校准题处理：作答仍计入这个知识点的掌握度。"
    KnowledgeCalibrationDecisionStatus.SELECTING ->
        "本次按校准题处理：正在按校准走廊选择…"
    KnowledgeCalibrationDecisionStatus.FAILED ->
        "本次按校准题处理；校准选择没有完成，本次仍按正常复习记录。"
    KnowledgeCalibrationDecisionStatus.READY -> when {
        decision == null ->
            "本次按校准题处理；没有拿到校准决策，本次仍按正常复习记录。"
        decision.isCalibrationQuestion ->
            "本次按校准题处理：选择器已选中校准题面（${decision.selectedAssessmentItemId}）。" +
                "作答仍计入这个知识点的掌握度。"
        else ->
            "本次按校准题处理；选择器未给出校准题" +
                "（${decision.reasonCodes.sorted().joinToString("、").ifEmpty { "无原因码" }}），" +
                "本次仍按正常复习记录。"
    }
}
