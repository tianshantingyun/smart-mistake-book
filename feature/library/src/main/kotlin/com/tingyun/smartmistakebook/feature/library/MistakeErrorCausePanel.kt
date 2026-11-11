package com.tingyun.smartmistakebook.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.ErrorCause
import com.tingyun.smartmistakebook.core.model.ErrorCauseAcceptanceSource
import com.tingyun.smartmistakebook.core.model.ErrorCauseChannel
import com.tingyun.smartmistakebook.core.model.ErrorCauseDetail
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip
import com.tingyun.smartmistakebook.core.ui.SmartColors
import kotlin.math.roundToInt

/**
 * 批次 50 / 5c：错题详情页的**教学候选**面板。
 *
 * **它消灭的具体失败**：错因一旦以"标签"形态出现在错题本，就会变成可见分类/筛选项——
 * 这是 D-001 冻结合同与 IA 明令禁止的（`docs/product-information-architecture.md:287,408`、
 * `docs/model-first-product-boundaries.md:9`）。本面板因此：
 *
 * - 标题固定为 [ERROR_CAUSE_PANEL_TITLE]（"教学候选（未成为标签）"），落款再次说明
 *   "不进入错题本的分类、筛选或卡片标签"；
 * - 只展示**当前题**的候选与至多一条现行标签，不做列表/筛选/分组，不写任何可见归类；
 * - 候选默认只读：模型自动落账门未认证时明确写出"模型不会自动写"，学生点"确认"才留下一条
 *   （人的选择是权威，见 `ErrorCauseRetractor.confirmByUser`）。
 *
 * **为什么面板本身不判门**：门是数据层事实（`ErrorCauseReviewService.autoBookingClosed`），
 * 由装配处传入；UI 只如实展示，不自己推断"能不能写"。
 */
@Composable
fun MistakeErrorCausePanel(
    state: ErrorCausePanelState,
    onConfirm: (observationId: String) -> Unit,
    onUndo: (bindingId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MistakeSectionCard(
        title = ERROR_CAUSE_PANEL_TITLE,
        modifier = modifier
            .fillMaxWidth()
            .testTag("mistake_detail_error_cause"),
    ) {
        Text(
            text = ERROR_CAUSE_LEGEND_TEXT,
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("mistake_detail_error_cause_legend"),
        )
        Spacer(Modifier.height(8.dp))

        if (state.unavailable) {
            Text(
                text = ERROR_CAUSE_UNAVAILABLE_TEXT,
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("mistake_detail_error_cause_unavailable"),
            )
            return@MistakeSectionCard
        }

        state.active?.let { active ->
            ErrorCauseActiveRow(
                active = active,
                busy = state.busy,
                onUndo = onUndo,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.candidates.isEmpty()) {
            Text(
                text = ERROR_CAUSE_EMPTY_CANDIDATES_TEXT,
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("mistake_detail_error_cause_empty"),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.candidates.forEach { candidate ->
                    ErrorCauseCandidateRow(
                        candidate = candidate,
                        busy = state.busy,
                        onConfirm = onConfirm,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = buildString {
                append(ERROR_CAUSE_FOOTNOTE)
                if (state.autoBookingClosed) {
                    append(" ")
                    append(ERROR_CAUSE_GATE_CLOSED_FOOTNOTE)
                }
            },
            color = SmartColors.InkSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("mistake_detail_error_cause_footnote"),
        )
        state.message?.let { message ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = SmartColors.Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("mistake_detail_error_cause_message"),
            )
        }
    }
}

@Composable
private fun ErrorCauseActiveRow(
    active: ErrorCauseActiveTag,
    busy: Boolean,
    onUndo: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mistake_detail_error_cause_active"),
        shape = RoundedCornerShape(10.dp),
        color = SmartColors.ContentSurface,
        border = BorderStroke(1.dp, SmartColors.Divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = errorCauseLabel(active.cause),
                    color = SmartColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                active.detail?.let { detail ->
                    Text(
                        text = errorCauseDetailLabel(detail),
                        color = SmartColors.InkSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "${errorCauseAcceptanceLabel(active.acceptanceSource)} · 置信 ${errorCauseConfidenceText(active.confidence)}",
                    color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InlineActionButton(
                text = "撤销",
                onClick = { onUndo(active.bindingId) },
                enabled = !busy,
                modifier = Modifier.testTag("mistake_detail_error_cause_undo"),
                contentDescription = "撤销这条错因候选（不改变这次作答的对错）",
            )
        }
    }
}

@Composable
private fun ErrorCauseCandidateRow(
    candidate: ErrorCauseCandidateItem,
    busy: Boolean,
    onConfirm: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mistake_detail_error_cause_candidate_${candidate.observationId}"),
        shape = RoundedCornerShape(10.dp),
        color = SmartColors.ContentSurface,
        border = BorderStroke(1.dp, SmartColors.Divider),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = errorCauseLabel(candidate.cause),
                color = SmartColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            candidate.detail?.let { detail ->
                Text(
                    text = errorCauseDetailLabel(detail),
                    color = SmartColors.InkSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${errorCauseChannelLabel(candidate.channel)} · 置信 " +
                    "${errorCauseConfidenceText(candidate.confidence)} · " +
                    "证据 ${candidate.evidenceCount} 条",
                color = SmartColors.InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (candidate.rationaleMarkdown.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = candidate.rationaleMarkdown,
                    color = SmartColors.Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlineActionChip(
                text = "确认这条",
                onClick = { onConfirm(candidate.observationId) },
                enabled = !busy,
                selected = false,
                modifier = Modifier.testTag(
                    "mistake_detail_error_cause_confirm_${candidate.observationId}",
                ),
                contentDescription = "把这条候选记为本次错误的内部教学候选（不改变作答与掌握度）",
            )
        }
    }
}

/** 面板标题：D-001 要求错因不作为可见标签，标题必须自己说明"还不是标签"。 */
const val ERROR_CAUSE_PANEL_TITLE: String = "教学候选（未成为标签）"

/** 四类图例的固定顺序（E01→E04），与 `ErrorCause` 稳定码一致。 */
val ERROR_CAUSE_LEGEND_ORDER: List<ErrorCause> = listOf(
    ErrorCause.CONCEPTUAL,
    ErrorCause.PROCEDURAL,
    ErrorCause.CARELESS,
    ErrorCause.MISIDENTIFICATION,
)

val ERROR_CAUSE_LEGEND_TEXT: String = ERROR_CAUSE_LEGEND_ORDER.joinToString(" · ") {
    errorCauseLabel(it)
}

const val ERROR_CAUSE_EMPTY_CANDIDATES_TEXT: String =
    "还没有候选。只有讲题或重做里留下证据时，才可能出现候选。"

const val ERROR_CAUSE_FOOTNOTE: String =
    "这里的内容不进入错题本的分类、筛选或卡片标签；它只是讲解与复习时的内部教学候选。"

const val ERROR_CAUSE_GATE_CLOSED_FOOTNOTE: String =
    "模型自动落账门未认证，保持关闭：模型不会自动写，只有你点「确认」才会留下一条。"

const val ERROR_CAUSE_UNAVAILABLE_TEXT: String =
    "候选暂时读不出来（本地账本不可用）。这道题的其它内容不受影响。"

/** 顶层四类的展示名：用行为词而不是类别名，稳定码保留在括号外便于回查。 */
fun errorCauseLabel(cause: ErrorCause): String = when (cause) {
    ErrorCause.CONCEPTUAL -> "E01 规则不成立"
    ErrorCause.PROCEDURAL -> "E02 步骤执行"
    ErrorCause.CARELESS -> "E03 手快失误"
    ErrorCause.MISIDENTIFICATION -> "E04 读题偏差"
}

/** 细码展示名：15 条各一句，学生看得懂"哪一步"，不出现分类术语。 */
fun errorCauseDetailLabel(detail: ErrorCauseDetail): String = when (detail) {
    ErrorCauseDetail.CONCEPT_RULE_INVALID -> "规则本身不成立"
    ErrorCauseDetail.CONCEPT_OVERGENERALIZED -> "规则用过头（过度推广）"
    ErrorCauseDetail.CONCEPT_PREREQUISITE_GAP -> "前置知识缺口"
    ErrorCauseDetail.FACTUAL_BASIC_FACT -> "基本事实记错"
    ErrorCauseDetail.FACTUAL_VOCABULARY -> "术语或符号记错"
    ErrorCauseDetail.FACTUAL_FORMULA -> "公式记错"
    ErrorCauseDetail.PROC_STEP_ORDER -> "步骤顺序错"
    ErrorCauseDetail.PROC_REGROUPING -> "进位或退位错"
    ErrorCauseDetail.PROC_SIGN -> "符号处理错"
    ErrorCauseDetail.PROC_DECIMAL_ALIGN -> "小数位没对齐"
    ErrorCauseDetail.PROC_FRACTION_RULE -> "分数运算规则用错"
    ErrorCauseDetail.PROC_ALGORITHM_SELECTION -> "选错算法"
    ErrorCauseDetail.SLIP_FAST_WRONG -> "做得太快漏看"
    ErrorCauseDetail.SLIP_INTERRUPTED -> "中途被打断"
    ErrorCauseDetail.SLIP_TRANSCRIPTION -> "抄写时出错"
    ErrorCauseDetail.READ_SIGN -> "把符号读错"
    ErrorCauseDetail.READ_DIGIT -> "把数字读错"
    ErrorCauseDetail.READ_CONDITION -> "漏看或看错条件"
    ErrorCauseDetail.READ_TARGET -> "看错所求"
}

fun errorCauseChannelLabel(channel: ErrorCauseChannel): String = when (channel) {
    ErrorCauseChannel.LOCAL_RULE -> "本地规则"
    ErrorCauseChannel.TUTOR_TRACE -> "讲题记录"
    ErrorCauseChannel.SELF_REPORT -> "学生自述"
    ErrorCauseChannel.REDO -> "重做证据"
    ErrorCauseChannel.MODEL -> "模型提议"
}

fun errorCauseAcceptanceLabel(source: ErrorCauseAcceptanceSource): String = when (source) {
    ErrorCauseAcceptanceSource.AUTO_GATE -> "模型自动落账"
    ErrorCauseAcceptanceSource.USER_CONFIRMED -> "你确认"
    ErrorCauseAcceptanceSource.USER_CORRECTED -> "你改判"
}

fun errorCauseConfidenceText(confidence: Double): String =
    "${(confidence * 100).roundToInt()}%"

/** 一条候选的展示快照（UI 层不依赖 core:database 的记录类型）。 */
data class ErrorCauseCandidateItem(
    val observationId: String,
    val cause: ErrorCause,
    val detail: ErrorCauseDetail?,
    val confidence: Double,
    val channel: ErrorCauseChannel,
    val rationaleMarkdown: String,
    val observedAtEpochMillis: Long,
    val evidenceCount: Int,
)

/** 至多一条现行标签的展示快照。 */
data class ErrorCauseActiveTag(
    val bindingId: String,
    val cause: ErrorCause,
    val detail: ErrorCauseDetail?,
    val acceptanceSource: ErrorCauseAcceptanceSource,
    val confidence: Double,
)

/**
 * 面板状态。`autoBookingClosed` 来自数据层的门，不是 UI 猜测；`message` 只放动作结果，
 * 不放分类推断。
 *
 * [unavailable] 是**读失败**态：账本读不出来时不能落到"还没有候选"——那会把"读不到"
 * 说成"没有"，是两种不同的产品事实。
 */
data class ErrorCausePanelState(
    val active: ErrorCauseActiveTag? = null,
    val candidates: List<ErrorCauseCandidateItem> = emptyList(),
    val autoBookingClosed: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val unavailable: Boolean = false,
)
