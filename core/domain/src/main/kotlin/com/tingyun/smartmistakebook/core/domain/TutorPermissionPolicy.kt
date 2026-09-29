package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorToolName

/**
 * 权限三档（规格 §3.3；D-K2c/K2d 已裁定）。
 *
 * | 档 | 对象 | 行为 |
 * |---|---|---|
 * | [ALLOW] | 三个读工具 | 自动执行 + 结果可见（灰色小字内联） |
 * | [ASK] | `NOTEBOOK_WRITE` / 四个本地动作 | 确认卡：学生点了才执行 |
 * | [AUTO_VISIBLE] | `MASTERY_UPDATE` | **全自动、不需批准**；行为全程可见，被拒给理由 |
 *
 * **档位由「对象 + 本轮上下文」决定，不由入口/栏决定**（ADR 0001 内核部分继续有效：
 * 单一工具面、禁止按栏分工具面）。同一个工具在任何栏都是同一档——栏只影响本轮附件与绑定，
 * 那两件事已经由 `TutorRoundQuestionBindingPolicy` 与本策略的上下文分开处理。
 *
 * ## 档位与"门"的分工（不重复裁决）
 *
 * - **准入**（这一轮允不允许调用）：`core:model` 的 `tutorToolAuthorization`（意图 × 置信度 ×
 *   声明集）与本策略的 [TutorRoundPermissionContext]。没准入的对象连卡都不出现——"模型这次
 *   调用被拒"和"学生还没点卡"是两件事。
 * - **档位**（要不要问学生）：本策略。
 * - **数值**（写多少、给不给过）：`MasteryWriteGate` 是 MASTERY_UPDATE 的**唯一信任边界**
 *   （D-K2d 升档）。它被拒不改档位——被拒照样自动、照样可见、照样给理由，只是不落库。
 *
 * ## 本策略里唯一会随上下文翻转的档位（未实现，留档）
 *
 * 重拆提议（D-K3c）：默认自动执行 + hook 拦，**被 hook 拦下才问人**——那是"同一对象、档位随
 * 本地门结论翻转"的第一例。它随 K3 生命周期的**实现**一起进本文件（阶段 3B）；现在不预置
 * 无消费者的上下文字段。
 */

/** 三档。集合开放的前提是每一档都有执行语义，加档必须同时加执行路径。 */
enum class TutorPermissionTier {
    /** 自动执行，行为可见。 */
    ALLOW,

    /** 挂起等学生点卡（确认卡，插眼 5 的持久化对象）。 */
    ASK,

    /** 全自动、不需批准；行为可见、被拒给理由。 */
    AUTO_VISIBLE,
}

/** 策略可裁决的对象：5 个模型工具 + 4 个本地动作。 */
sealed interface TutorPermissionSubject {
    data class Tool(val tool: TutorToolName) : TutorPermissionSubject

    data class LocalAction(val action: TutorLocalAction) : TutorPermissionSubject
}

/**
 * 本轮上下文：**与入口无关**的本轮事实。档位本身不随它变（档位是对象的属性），它决定
 * "这一轮是否真的按档执行"——没准入就不执行、也不出现确认卡。
 *
 * 两个集合的**来源**都是已有的本地裁决，不在这里重算：
 * - [allowedTools] = `core:model` 的 `tutorToolAuthorization(...).allowedTools`（意图 × 置信度
 *   × 声明集）；
 * - [allowedActions] = 本地动作通道对 ID + 参数形状的校验结果（白名单第一版，见
 *   [TutorLocalAction]）。
 *
 * 无题轮（K2a）**不进这里**：读工具返回"本轮无可读范围"、写工具返回"无可写目标"是**结果**
 * 语义（不报错、不消耗预算），不是档位语义——档位只回答"要不要问学生"。
 */
data class TutorRoundPermissionContext(
    val allowedTools: Set<TutorToolName> = TutorToolName.entries.toSet(),
    val allowedActions: Set<TutorLocalAction> = TutorLocalAction.entries.toSet(),
)

/** 一轮里对一个对象的裁决：档位 + 本轮是否放行。 */
data class TutorPermissionDecision(
    val subject: TutorPermissionSubject,
    val tier: TutorPermissionTier,
    val admitted: Boolean,
) {
    /** 需要挂确认卡 = 档位是 ask 且本轮放行。没放行时连卡都不该出现（调用被拒，不是待确认）。 */
    val requiresConsentCard: Boolean
        get() = tier == TutorPermissionTier.ASK && admitted
}

/**
 * 档位：**只看对象**。写成一个独立函数是因为"要看这一档"的调用方（工具环、界面、测试）远多于
 * "要算准入"的调用方——档位是对象的属性，不该被上下文的存在与否干扰。
 */
fun tutorPermissionTier(subject: TutorPermissionSubject): TutorPermissionTier = when (subject) {
    is TutorPermissionSubject.Tool -> when (subject.tool) {
        TutorToolName.KNOWLEDGE_READ,
        TutorToolName.NOTEBOOK_READ,
        TutorToolName.MASTERY_READ,
        -> TutorPermissionTier.ALLOW
        TutorToolName.NOTEBOOK_WRITE -> TutorPermissionTier.ASK
        TutorToolName.MASTERY_UPDATE -> TutorPermissionTier.AUTO_VISIBLE
    }
    is TutorPermissionSubject.LocalAction -> TutorPermissionTier.ASK
}

/** 本轮裁决：档位（对象属性）+ 是否放行（上下文属性）。 */
fun tutorPermissionDecision(
    subject: TutorPermissionSubject,
    context: TutorRoundPermissionContext,
): TutorPermissionDecision = TutorPermissionDecision(
    subject = subject,
    tier = tutorPermissionTier(subject),
    admitted = when (subject) {
        is TutorPermissionSubject.Tool -> subject.tool in context.allowedTools
        is TutorPermissionSubject.LocalAction -> subject.action in context.allowedActions
    },
)

/**
 * 需要确认卡的对象 → [AgentPendingRequestKind]（落库的 `kind`）。不需要卡的对象返回 null。
 *
 * `SAVE_TO_NOTEBOOK`（动作拼写）与 `NOTEBOOK_WRITE`（工具拼写）**是同一件事的两种拼写**
 * （D-K2e 白名单表："`SAVE_TO_NOTEBOOK` = `NOTEBOOK_WRITE` 的 ask 档"），所以它们各自
 * 映射到自己的 kind：行里留下的是**模型当初用的是哪种拼写**，执行路径按 kind 分派，
 * 语义判定（"这题要不要存"）两者相同。
 */
fun agentPendingRequestKind(subject: TutorPermissionSubject): AgentPendingRequestKind? =
    when (subject) {
        is TutorPermissionSubject.Tool -> when (subject.tool) {
            TutorToolName.NOTEBOOK_WRITE -> AgentPendingRequestKind.NOTEBOOK_WRITE
            else -> null
        }
        is TutorPermissionSubject.LocalAction -> when (subject.action) {
            TutorLocalAction.OPEN_PROBLEM -> AgentPendingRequestKind.OPEN_PROBLEM
            TutorLocalAction.SAVE_TO_NOTEBOOK -> AgentPendingRequestKind.SAVE_TO_NOTEBOOK
            TutorLocalAction.START_EXPORT -> AgentPendingRequestKind.START_EXPORT
            TutorLocalAction.ADD_TO_REVIEW_PLAN -> AgentPendingRequestKind.ADD_TO_REVIEW_PLAN
        }
    }

/**
 * [agentPendingRequestKind] 的逆：一个待确认 kind 回到它对应的**策略对象**（两种拼写各自回到
 * 自己那一边——工具拼写回工具面、动作拼写回本地动作面）。
 *
 * 消费者只有一处：交互面读工具痕迹里那条"在等学生确认"的 `NOTEBOOK_WRITE`，把它送回同一个
 * 准入函数挂出**同一张卡**（见 `TutorPendingRequestCoordinator.suspendRequestedActionsIfAny`）。
 */
fun tutorPermissionSubject(kind: AgentPendingRequestKind): TutorPermissionSubject = when (kind) {
    AgentPendingRequestKind.NOTEBOOK_WRITE -> TutorPermissionSubject.Tool(TutorToolName.NOTEBOOK_WRITE)
    AgentPendingRequestKind.SAVE_TO_NOTEBOOK ->
        TutorPermissionSubject.LocalAction(TutorLocalAction.SAVE_TO_NOTEBOOK)
    AgentPendingRequestKind.OPEN_PROBLEM -> TutorPermissionSubject.LocalAction(TutorLocalAction.OPEN_PROBLEM)
    AgentPendingRequestKind.START_EXPORT -> TutorPermissionSubject.LocalAction(TutorLocalAction.START_EXPORT)
    AgentPendingRequestKind.ADD_TO_REVIEW_PLAN ->
        TutorPermissionSubject.LocalAction(TutorLocalAction.ADD_TO_REVIEW_PLAN)
}
