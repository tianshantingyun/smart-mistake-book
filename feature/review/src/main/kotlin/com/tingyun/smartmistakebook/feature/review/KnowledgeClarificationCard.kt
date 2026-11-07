package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/**
 * KD-67 确认卡（产品面）。
 *
 * 内容按学生能做的决定组织，而不是按内部状态：
 * 1. **为什么停下来**——原因行（人话，未知原因码不原样外泄）；
 * 2. **确认的是哪道题**——候选列表（决策时刻的题面投影；确认只作用于列表里的 id）；
 * 3. **三条出路**——确认答案 / 换一道题 / 取消。
 *
 * 与 `TutorPendingRequestCard` 的区别（**不复制那条链路**）：讲题确认卡的挂起/恢复/超时
 * 依赖 `agent_pending_request` 持久表与工具轮状态机；本卡是知识点复习会话内的**瞬时**
 * 决策，不持久化挂起请求、不跨回合恢复——重放由 `KnowledgeClarificationPresenter` 的
 * 请求键幂等吸收，进程死亡后由会话恢复重新选择，而不是复活一张旧卡。
 *
 * 确认按钮的可用性由调用方给出（[confirmEnabled]）：只有学生**已作答**时才能确认答案——
 * 确认的语义是"把我这次的作答按这个答案计分"，没有作答就没有可确认的事实。
 */
@Composable
internal fun KnowledgeClarificationCard(
    state: KnowledgeClarificationState.Asking,
    confirmEnabled: Boolean,
    /** 屏幕上这道题的题面 id；只有它本身在候选里才允许确认（错题行候选没有键面）。 */
    currentItemId: String?,
    /** 知识点 id → 学生可读名字；题面 id 只做内部键，不上屏。 */
    knowledgeNodeLabels: Map<String, String>,
    onConfirm: (candidateId: String) -> Unit,
    onNewQuestion: (candidateId: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = knowledgeClarificationCardModel(state.request)
    val confirmCandidateId = knowledgeClarificationConfirmCandidateId(
        request = state.request,
        currentItemId = currentItemId,
    )
    val swapCandidateId = confirmCandidateId ?: model.candidates.firstOrNull()?.assessmentItemId

    WorkspacePanel(modifier = modifier.testTag("knowledge_clarification_card")) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                color = SmartColors.Ink,
            )
            Spacer(Modifier.height(8.dp))
            model.reasonLines.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_clarification_reason"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartColors.InkSecondary,
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            model.candidates.forEachIndexed { index, candidate ->
                val label = candidate.knowledgeNodeIds
                    .firstNotNullOfOrNull { knowledgeNodeLabels[it] }
                    ?: "当前知识点"
                Text(
                    text = if (model.candidates.size == 1) {
                        "待确认的题面：$label"
                    } else {
                        "第 ${index + 1} 道待确认的题面：$label"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("knowledge_clarification_candidate_${candidate.assessmentItemId}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartColors.Ink,
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_clarification_error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartColors.ErrorText,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (model.confirmable && confirmCandidateId != null) {
                PrimaryActionButton(
                    text = when {
                        state.busy -> "正在记录你的确认…"
                        !confirmEnabled -> "先做完这道题，再确认答案"
                        else -> "确认这道题的答案"
                    },
                    onClick = { onConfirm(confirmCandidateId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_clarification_confirm"),
                    enabled = confirmEnabled && !state.busy,
                    contentDescription = if (confirmEnabled) {
                        "确认这道题的答案，并让这次作答计入掌握度"
                    } else {
                        "作答后才能确认这道题的答案"
                    },
                )
                Spacer(Modifier.height(8.dp))
            } else {
                // 不是键未核验引起的停题：确认答案不是解法，不给一个点不动的按钮。
                Text(
                    text = "这次不是答案对错的问题：换一道题，或取消后继续。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_clarification_no_confirm"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartColors.InkSecondary,
                )
                Spacer(Modifier.height(8.dp))
            }
            ClaudeSecondaryButton(
                onClick = { swapCandidateId?.let(onNewQuestion) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_clarification_new_question"),
                enabled = !state.busy,
            ) {
                Text("换一道题（跳过这个知识点）")
            }
            Spacer(Modifier.height(4.dp))
            ClaudeSecondaryButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_clarification_cancel"),
                enabled = !state.busy,
            ) {
                Text("取消")
            }
            Text(
                text = "选择器版本：${model.selectorVersion}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("knowledge_clarification_selector_version"),
                style = MaterialTheme.typography.labelSmall,
                color = SmartColors.InkSecondary,
            )
        }
    }
}

/**
 * 屏幕侧入口：只在有确认卡时渲染。放在一个函数里，让 `KnowledgeReviewSessionScreen`
 * 的主干保持"状态 → 分支"的形状，不把卡片内容混进会话布局。
 */
@Composable
internal fun KnowledgeClarificationSlot(
    state: KnowledgeClarificationState,
    confirmEnabled: Boolean,
    currentItemId: String?,
    knowledgeNodeLabels: Map<String, String>,
    onConfirm: (candidateId: String) -> Unit,
    onNewQuestion: (candidateId: String) -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        is KnowledgeClarificationState.Asking -> {
            Spacer(Modifier.height(12.dp))
            KnowledgeClarificationCard(
                state = state,
                confirmEnabled = confirmEnabled,
                currentItemId = currentItemId,
                knowledgeNodeLabels = knowledgeNodeLabels,
                onConfirm = onConfirm,
                onNewQuestion = onNewQuestion,
                onCancel = onCancel,
            )
        }
        KnowledgeClarificationState.Hidden,
        is KnowledgeClarificationState.Resolved,
        -> Unit
    }
}
