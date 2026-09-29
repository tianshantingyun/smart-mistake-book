package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/**
 * 智能体交互模式（D-Q9，`docs/agent-first-refactor-decisions-2026-09-23.md`）。
 *
 * - [NORMAL]（智能体栏默认）：有问即答，**无提示阶梯限制**——学生要答案就给答案；
 * - [GUIDED]（引导模式，学生自己切）：答复按 [TutorScaffoldLevel] 的脚手架刻度给，
 *   卡点自动升档（见 [tutorScaffoldDirective]）；复习栏默认引导模式属阶段 5。
 *
 * 消灭的具体失败：改前只有一种交互形态，于是"想自己试试"的学生与"就想看解答"的学生拿到
 * 同一种答复——前者被直接喂答案（bottom-out 损害学习），后者被一步一步问（用户原话：
 * "万一用户想要解答时还一步一步问就不好了"）。模式是**学生的选择**，不是模型的行为。
 */
@Serializable
enum class TutorInteractionMode {
    NORMAL,
    GUIDED,
    ;

    /** 学生看得懂的一句（界面上**不出现**模式名本身，见 `TutorInteractionModeSwitch`）。 */
    val displayLabel: String
        get() = when (this) {
            NORMAL -> "直接回答"
            GUIDED -> "带着我一步步来"
        }

    /** 切到另一种模式时那句可点的话（人话，不出现"模式""脚手架"这类内部词）。 */
    val switchActionLabel: String
        get() = when (this) {
            NORMAL -> "换成一步步引导"
            GUIDED -> "换成直接回答"
        }

    /** 界面上的一句话说明（当前模式意味着什么）。 */
    val displayDescription: String
        get() = when (this) {
            NORMAL -> "有问题就直说，它直接回答，不限制谁能说到哪一步。"
            GUIDED -> "它先给你最小的一步，卡住了再往上加；你随时可以要完整解法。"
        }

    companion object {
        /** 会话区的默认模式（D-Q9：智能体栏默认正常；复习栏默认引导留给阶段 5）。 */
        const val AREA_DEFAULT_NAME: String = "NORMAL"

        fun fromName(name: String?): TutorInteractionMode =
            entries.firstOrNull { mode -> mode.name == name } ?: NORMAL
    }
}

/**
 * 五级脚手架刻度（D-Q9 定稿方向；外部依据见研究报告 §7f：Assistance Dilemma / SHARP 卡点
 * 检测 / bottom-out 损害学习）。
 *
 * 刻度**只在引导模式内存在**：正常模式不检测卡点、不限制阶梯（[tutorScaffoldDirective]
 * 对 NORMAL 返回 null）。文案同时是给模型的行为契约——它和"本地自动升档"共用这一份顺序，
 * 顺序错了（比如把 L3 当成"给答案"）模型与本地就会互相打架。
 */
@Serializable
enum class TutorScaffoldLevel {
    /** 最小帮助：只确认学生卡在哪、把方向指给他，不展开任何一步。 */
    L0,

    /** 引导（自己试）：把问题拆成学生自己能走的**一小步**，让他先试。 */
    L1,

    /** 提示（指错误位置，不给解法）：指出卡在哪一处、怎么自己核对，不给后续步骤。 */
    L2,

    /** 讲解（讲思路，学生仍自己做）：把思路与方法讲清，最后一步留给学生。 */
    L3,

    /** 完整解法：直接给完整过程与结论（学生明确索要答案时就到这里）。 */
    L4,
    ;

    /** 写给模型的一句（进提示词的阶梯表；**唯一出处**）。 */
    val guidance: String
        get() = when (this) {
            L0 -> "最小帮助：只确认学生卡在哪，指出方向，不展开任何一步。"
            L1 -> "引导：把问题拆成**他能自己走的一小步**，让他先试，不给过程。"
            L2 -> "提示：指出他卡在哪一处、怎么自己核对，**不给解法与后续步骤**。"
            L3 -> "讲解：把思路与方法讲清楚，最后一步仍然留给他自己做。"
            L4 -> "完整解法：直接给出完整过程与结论。"
        }

    /** 升档：到顶就停在 [L4]。 */
    fun escalate(): TutorScaffoldLevel = entries[minOf(ordinal + 1, entries.lastIndex)]

    /** 降档（封顶用）：到底就停在 [L0]。 */
    fun cappedAt(ceiling: TutorScaffoldLevel): TutorScaffoldLevel =
        entries[minOf(ordinal, ceiling.ordinal)]
}

/**
 * 卡点证据（**只来自本地可观察的事实**，不信模型自述）：连续失败轮数与该轮耗时。
 *
 * 为什么是这两件：本地能观察到的"学生卡住了"只有两种痕迹——**连续几轮没能拿到能用的答复**
 * （派发失败/被中断，一条一条接着发生）与**一轮明显拖长**（耗时异常）。学生"还是不懂"这种
 * 语义信号由模型自己判（提示词里明说），本地不猜语义——这条与 `TutorIntentAuthority` 被删
 * 的理由同源：本地不做语义判断，语义在模型，本地只做可核对的事实与数值。
 */
data class TutorScaffoldEvidence(
    val consecutiveFailedRounds: Int = 0,
    val lastRoundDurationMillis: Long? = null,
) {
    init {
        require(consecutiveFailedRounds >= 0) { "Consecutive failed rounds must not be negative" }
        require(lastRoundDurationMillis == null || lastRoundDurationMillis >= 0L) {
            "A round duration must not be negative"
        }
    }

    companion object {
        val NONE = TutorScaffoldEvidence()
    }
}

/**
 * 卡点升档的**纯策略**（D-Q9：纯策略函数 + 单测，只活在引导模式内）。
 *
 * 规则：起步 [TutorScaffoldLevel.L1]（引导，自己试），每 [FAILED_ROUNDS_PER_STEP] 轮连续失败
 * 升一档，上一轮耗时达到 [LONG_ROUND_MILLIS] 再升一档，最多自动升 [MAX_AUTO_STEPS] 档
 * （自动最多到 L3；**L4 完整解法只由学生明确索要**，不由本地自动给——那是这条刻度的底线，
 * 自动升到顶等于把"自己试"这一档整体取消）。给了 [ceiling] 就压在它之下（复习栏"作答前封顶
 * L2"在阶段 5 用同一个参数接线）。
 *
 * 正常模式返回 null：不检测、不限制（D-Q9 第 3 条）。返回值就是本轮**起步档**，也是提示词里
 * 那句"这次多给一步"的事实依据（高于 L1 即升过档）。
 *
 * 三个常数都是**占位值，待校准**（与 `MasteryWriteGate` 的门常数同一条纪律：常数集中、
 * 可配置、带实测校准流程）——集中在这里，是为了校准时只改一个地方。
 */
fun tutorScaffoldDirective(
    mode: TutorInteractionMode,
    evidence: TutorScaffoldEvidence = TutorScaffoldEvidence.NONE,
    ceiling: TutorScaffoldLevel? = null,
): TutorScaffoldLevel? {
    if (mode != TutorInteractionMode.GUIDED) return null
    val failureSteps = (evidence.consecutiveFailedRounds / FAILED_ROUNDS_PER_STEP)
        .coerceIn(0, MAX_AUTO_STEPS)
    val slowRoundStep = if (
        (evidence.lastRoundDurationMillis ?: 0L) >= LONG_ROUND_MILLIS
    ) {
        1
    } else {
        0
    }
    val steps = (failureSteps + slowRoundStep).coerceIn(0, MAX_AUTO_STEPS)
    val start = TutorScaffoldLevel.L1.let { level ->
        var current = level
        repeat(steps) { current = current.escalate() }
        current
    }
    return ceiling?.let(start::cappedAt) ?: start
}

/** 连续几轮没拿到可用回复就自动升一档（占位值，待校准）。 */
const val FAILED_ROUNDS_PER_STEP = 1

/** 本地自动最多升几档（占位值，待校准）：到 L3 为止，L4 只能由学生索要。 */
const val MAX_AUTO_STEPS = 2

/** 一轮耗时超过这个毫秒数按"耗时异常"记一次升档（占位值，待校准）。 */
const val LONG_ROUND_MILLIS = 120_000L

/**
 * 模式 + 阶梯写进提示词的那一段（**唯一出处**：本地策略算出来的档位在这里变成文字，
 * 与 [TutorLocalAction.purposeDescription] 一样的纪律——一份声明、一处渲染）。
 *
 * [level] 来自 [TutorLobbyInput.scaffoldLevel]（本地策略的结论，见 [tutorScaffoldDirective]）；
 * 它高于 [TutorScaffoldLevel.L1]（"自己试"那一档）时说一句"这次多给一步"——那句提示的**事实
 * 依据**就是档位本身（只有卡点升档才会把它抬上去），不需要再传一个布尔位。
 */
fun tutorScaffoldPromptBlock(
    mode: TutorInteractionMode,
    level: TutorScaffoldLevel?,
): String = when (mode) {
    TutorInteractionMode.NORMAL ->
        "交互模式：正常。学生要什么就给什么，**不设提示阶梯**：他明确要答案就直接给完整解法，" +
            "不要先反问、不要先给一步再等他。"
    TutorInteractionMode.GUIDED -> buildString {
        append("交互模式：引导（学生自己选的）。按下面的刻度给帮助，不要越档：")
        TutorScaffoldLevel.entries.forEach { entry ->
            append("\n- ").append(entry.name).append("：").append(entry.guidance)
        }
        append("\n学生明确索要答案时，直接给完整解法（").append(TutorScaffoldLevel.L4.name)
        append("），并告诉他随时可以换成直接回答。")
        if (level != null) {
            append("\n本轮起步档：").append(level.name)
            append("（").append(level.guidance).append("）")
            if (level.ordinal > TutorScaffoldLevel.L1.ordinal) {
                append("——他在这里卡了一会儿了，这次多给一步；不要说这是本地算出来的，直接给就好。")
            }
        }
    }
}
