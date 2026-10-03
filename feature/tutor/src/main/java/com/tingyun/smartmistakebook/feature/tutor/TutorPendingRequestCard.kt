package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequest
import com.tingyun.smartmistakebook.core.domain.agentPendingRequestExportPayloadIsLegacy
import com.tingyun.smartmistakebook.core.domain.tutorLocalActionExportProposal
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestDecision
import com.tingyun.smartmistakebook.core.domain.AgentPendingRequestKind
import com.tingyun.smartmistakebook.core.ui.Ink
import com.tingyun.smartmistakebook.core.ui.InkSecondary
import com.tingyun.smartmistakebook.core.ui.JadeActive
import com.tingyun.smartmistakebook.core.ui.OutlineActionChip

/**
 * 确认卡（A4 / 插眼 5）：**本地动作的确认**——学生点了才会执行。
 *
 * 消灭的具体失败（三条，都是实测）：
 * 1. 大厅不渲染意图面板 → 模型申请的"存进错题本"被静默丢弃（学生根本不知道它在问什么）；
 * 2. 错题入口的「确认加入错题本」只导航 → 点了什么也没发生；
 * 3. 挂起之后没有恢复路径 → 进程一死，这张卡就悬死。
 *
 * 卡是**行**的视图（[AgentPendingRequest] 来自 `agent_pending_request` 表）：进程死亡后重建的是
 * 同一张卡，裁决后行落终态（不在界面上留一份会漂的状态）。
 */
@Composable
internal fun TutorPendingRequestCard(
    request: AgentPendingRequest,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDecide: (AgentPendingRequestDecision) -> Unit,
    testTagPrefix: String = "tutor_pending_request",
) {
    val copy = pendingRequestCopy(request.kind, request.payloadJson)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagPrefix),
        color = JadeActive.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JadeActive.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = copy.title,
                color = Ink,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = copy.detail,
                color = InkSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineActionChip(
                    text = copy.acceptLabel,
                    onClick = { if (enabled) onDecide(AgentPendingRequestDecision.ACCEPT) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_accept"),
                )
                OutlineActionChip(
                    text = TUTOR_PENDING_DECLINE_LABEL,
                    onClick = { if (enabled) onDecide(AgentPendingRequestDecision.DECLINE) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_decline"),
                )
            }
        }
    }
}

internal const val TUTOR_PENDING_DECLINE_LABEL = "先不用"

/** 一张确认卡的两句文案：它要做什么、做了之后落到哪。 */
internal data class TutorPendingRequestCopy(
    val title: String,
    val detail: String,
    val acceptLabel: String,
)

/**
 * kind → 学生看得懂的话（界面不出现内部机制词：工具名、kind 一律翻译成人话）。
 *
 * `START_EXPORT` 自 4B B3-2 起展示**模型提议的模板名与关键参数**（"练习卷 · 双栏 · 字号大 ·
 * 不含答案"）——学生确认前就知道将要导出什么版式；payload 读不出来时（理论上不该发生，行在
 * 落库口已校验）退化为不带版式的一句，不崩、也不假装知道。
 */
internal fun pendingRequestCopy(
    kind: AgentPendingRequestKind,
    payloadJson: String? = null,
): TutorPendingRequestCopy = when (kind) {
    AgentPendingRequestKind.NOTEBOOK_WRITE,
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK,
    -> TutorPendingRequestCopy(
        title = "把这一轮的内容加入错题本？",
        detail = "点了才会存：这一轮附上的图片会进入录入，稍后在错题本里就能看到。",
        acceptLabel = "加入错题本",
    )
    AgentPendingRequestKind.OPEN_PROBLEM -> TutorPendingRequestCopy(
        title = "打开这道题？",
        detail = "去错题本里看这道题的详情。",
        acceptLabel = "打开错题本",
    )
    AgentPendingRequestKind.START_EXPORT -> {
        val layout = payloadJson?.let(::tutorLocalActionExportProposal)
        val legacy = payloadJson != null && agentPendingRequestExportPayloadIsLegacy(payloadJson)
        TutorPendingRequestCopy(
            title = "现在导出这份练习？",
            detail = when {
                layout != null ->
                    "版式：${exportLayoutSummary(layout)}。" +
                        "确认后打开导出设置，可以改版式、勾选要导出的题；点「开始导出」才会生成文件。"
                // 升级前落库的行是空形状：读回不抛（读回宽容），这里如实说是旧版本的请求，
                // 确认后用默认版式继续——不崩、也不假装模型提过版式。
                legacy ->
                    "这张导出请求来自旧版本（没有版式提议）。" +
                        "确认后用默认版式打开导出设置，可以改版式、勾选要导出的题。"
                else ->
                    "确认后打开导出设置：可以改版式、勾选要导出的题，点「开始导出」才会生成文件。"
            },
            acceptLabel = "去导出",
        )
    }
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN -> TutorPendingRequestCopy(
        title = "把这题放进复习计划？",
        detail = "复习计划调整还在单独设计；这一版点了不会改动计划。",
        acceptLabel = "知道了",
    )
}
