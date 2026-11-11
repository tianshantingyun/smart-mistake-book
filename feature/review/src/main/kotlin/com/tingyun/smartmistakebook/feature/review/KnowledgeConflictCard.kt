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
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton
import com.tingyun.smartmistakebook.core.ui.PrimaryActionButton
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel

/**
 * 冲突 KC 卡（K5，批次 49 P2-14）。
 *
 * 设计出处：`docs/research/2026-11-08-clarification-ux.md` §4.8——
 * 该知识点 `effectiveStatus == CONFLICTED` 时，先用一句人话说清"最近有波动"，
 * 给两个动作「先做校准题」/「仍然复习」。
 *
 * 硬语义（硬约束：冲突 KC 默认仍可选）：
 * - **默认 = 仍然复习**：卡不阻断出题/作答，学生什么都不点也能走完这次复习；
 * - 「先做校准题」只是把本次选择换成选择器已有的 `CALIBRATION` 走廊，不新建第二条出题路径、
 *   不把节点从队列里排除；
 * - 只有 `CONFLICTED_KNOWLEDGE` 触发。`CALIBRATION_CHECK` 对未知/过期/薄弱点都会出现，
 *   拿它当判据等于每个节点弹一张卡（正是 K5 要避免的打断）。
 */
internal enum class KnowledgeConflictCardAction {
    START_CALIBRATION,
    CONTINUE_REVIEW,
}

internal data class KnowledgeConflictCardModel(
    val knowledgeNodeId: String,
    val title: String,
    val reason: String,
    /**
     * 动作后果说明。批次 49 修复环：旧文案「校准题只多问一道」承诺了一个实现不存在的
     * 第二道题（题面仍由 `KnowledgeReviewQuizLoader.loadQuiz` 现场生成，选择器的
     * `ASK_CALIBRATION` 决策在现有候选池上没有可达题面）。文案改为只承诺真实会发生的
     * 两件事：本次选择改走校准走廊、作答仍计入掌握度、不把这次复习算作完成。
     */
    val consequence: String,
    val actions: List<KnowledgeConflictCardAction>,
    val defaultAction: KnowledgeConflictCardAction,
    /** 必须点的动作数：0 = 纯粹可选（K5 的设计不变式）。 */
    val requiredTaps: Int,
)

/**
 * 队列项 → 冲突卡模型；null = 不显示卡（无冲突证据）。
 *
 * 纯函数：卡片内容、动作数、默认动作都能在 JVM 单测里钉住，不需要渲染 Compose
 * （`feature/review` 没有 compose-ui-test 源集）。
 */
internal fun knowledgeConflictCardModel(
    entry: KnowledgeReviewQueueEntry,
): KnowledgeConflictCardModel? {
    if (!entry.isConflicted) return null
    return KnowledgeConflictCardModel(
        knowledgeNodeId = entry.knowledgeNodeId,
        title = "这个知识点最近有波动",
        reason = "之前掌握过，最近又出现了错误。可以先按校准评估确认现在的水平，也可以直接继续复习。",
        consequence = "校准评估只改本次选择口径：作答仍计入掌握度，不会把这次复习算作完成。",
        actions = listOf(
            KnowledgeConflictCardAction.START_CALIBRATION,
            KnowledgeConflictCardAction.CONTINUE_REVIEW,
        ),
        defaultAction = KnowledgeConflictCardAction.CONTINUE_REVIEW,
        requiredTaps = 0,
    )
}

/**
 * 屏幕侧入口：null 模型不渲染任何东西。动作只有两个，且「仍然复习」是主按钮——
 * 默认动作必须是最省事的那个（"什么都不改，继续"）。
 */
@Composable
internal fun KnowledgeConflictCardSlot(
    model: KnowledgeConflictCardModel?,
    onStartCalibration: () -> Unit,
    onContinueReview: () -> Unit,
) {
    if (model == null) return
    Spacer(Modifier.height(12.dp))
    WorkspacePanel(modifier = Modifier.testTag("knowledge_conflict_card")) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = model.title,
                modifier = Modifier.testTag("knowledge_conflict_title"),
                style = MaterialTheme.typography.titleMedium,
                color = SmartColors.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = model.reason,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_conflict_reason"),
                style = MaterialTheme.typography.bodyMedium,
                color = SmartColors.InkSecondary,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(
                text = "仍然复习",
                onClick = onContinueReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_conflict_continue"),
                contentDescription = "按正常复习继续这个知识点",
            )
            Spacer(Modifier.height(8.dp))
            ClaudeSecondaryButton(
                onClick = onStartCalibration,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_conflict_start_calibration"),
            ) {
                Text("先做校准题")
            }
            Text(
                text = model.consequence,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("knowledge_conflict_consequence"),
                style = MaterialTheme.typography.labelSmall,
                color = SmartColors.InkSecondary,
            )
        }
    }
}
