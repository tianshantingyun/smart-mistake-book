package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.study.TutorToolExecution
import com.tingyun.smartmistakebook.core.domain.TutorPermissionSubject
import com.tingyun.smartmistakebook.core.domain.TutorPermissionTier
import com.tingyun.smartmistakebook.core.domain.tutorPermissionDecision
import com.tingyun.smartmistakebook.core.model.TOOL_AWAITING_CONSENT_ERROR_KIND
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome

/**
 * 工具环里的**权限分档**（规格 §3.3 / D-K2c-K2d）：哪些工具这一轮真的执行、哪些只留下
 * 一句"请学生点确认"。
 *
 * 抽出来是为了两件事：① `RoomModelTaskRepository` 的主循环只留"调一次、拿两半"，
 * 不至于因为这段策略长在循环体里而把策略与流程混在一起；② 这段判定本身可单测
 * （真库、真网关都不需要）。
 *
 * | 档 | 工具 | 这一轮怎么办 |
 * |---|---|---|
 * | allow | 三个读工具 | 自动执行，结果进痕迹（灰色小字） |
 * | auto+visible | `MASTERY_UPDATE` | 自动执行（写门被拒照样给理由，理由在结果里） |
 * | ask | `NOTEBOOK_WRITE` | **不在工具环里执行**：本地会请学生点确认卡（D-K2e 的 ask 档） |
 *
 * ask 档的调用被**如实回答**而不是静默丢弃：模型拿到的是一句"本地会请学生确认，这一轮没有执行"，
 * 于是它下一轮既不会重复申请、也不会以为已经存好了。学生那一侧的卡由交互面按同一轮的痕迹
 * （`awaiting_consent`）挂出来（见 `TutorPendingRequestCoordinator`）。
 */
internal data class TutorToolRoundsByPermission(
    /** 这一轮真的交给执行器的调用（allow / auto+visible）。 */
    val executable: List<TutorToolCall>,
    /** 这一轮不执行的 ask 档调用，已经带着那句诚实的回执结果。 */
    val awaitingConsent: List<TutorToolExecution>,
)

/**
 * 按本轮授权结果给调用分档。
 *
 * [allowedTools] 是授权矩阵的产物（意图 × 置信度 × 声明集）：没被放行的调用连档位都不适用，
 * 它会照旧走"未授权"那条路（由 [tutorToolRoundOutcomes] 自己给结果），所以这里只看**放行的那些**
 * 调用里哪些属于 ask 档。
 */
internal fun tutorToolRoundsByPermission(
    calls: List<TutorToolCall>,
    allowedTools: Set<TutorToolName>,
): TutorToolRoundsByPermission {
    val executable = calls.filter { call ->
        when (
            tutorPermissionDecision(
                subject = TutorPermissionSubject.Tool(call.tool),
                // 本地动作不走工具环：这条通道里的对象只有工具，动作集合恒空。
                context = com.tingyun.smartmistakebook.core.domain.TutorRoundPermissionContext(
                    allowedTools = allowedTools,
                    allowedActions = emptySet(),
                ),
            ).tier
        ) {
            TutorPermissionTier.ASK -> false
            TutorPermissionTier.ALLOW,
            TutorPermissionTier.AUTO_VISIBLE,
            -> true
        }
    }
    return TutorToolRoundsByPermission(
        executable = executable,
        awaitingConsent = calls.filterNot { call -> call in executable }.map(::awaitingConsentExecution),
    )
}

/**
 * 一个 **ask 档工具**在工具环里给模型的诚实结果：本地不执行它，而是请学生点确认卡
 * （D-K2e：同一件事的两种拼写——工具拼写与本地动作拼写）。
 *
 * [TOOL_AWAITING_CONSENT_ERROR_KIND] 同时是**痕迹的行码**（B1）：界面上那一行小字说
 * "错题本 · 未保存"并给出"已经请你确认，还没保存"，与本节流出的文字同一口径。
 */
internal fun awaitingConsentExecution(call: TutorToolCall): TutorToolExecution = TutorToolExecution(
    outcome = TutorToolOutcome(
        tool = call.tool,
        ok = false,
        summaryMarkdown = AWAITING_CONSENT_SUMMARY,
        errorKind = TOOL_AWAITING_CONSENT_ERROR_KIND,
    ),
)

private const val AWAITING_CONSENT_SUMMARY =
    "本地会请学生点确认卡，这一轮没有执行；不要重复申请，也不要声称已经保存。"
