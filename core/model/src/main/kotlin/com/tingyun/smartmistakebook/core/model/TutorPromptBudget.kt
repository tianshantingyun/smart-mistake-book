package com.tingyun.smartmistakebook.core.model

/**
 * 提示词侧的**有界工具历史**（LangChain `trim_messages` / Pydantic AI history processor
 * 同构思路）：完整结果仍留在本地请求与工具痕迹里，模型提示词只保留最近一段，
 * 较早轮次退成**摘要**而不是整段消失。
 *
 * 它消灭的具体失败：工具轮上限提高后，8 轮 × 每轮多条 dataJson 可以把提示词推过 Provider
 * 的上下文窗口；直接截断正文会产生看不懂的半截 JSON，直接丢弃又让模型忘记自己查过什么。
 * 这里按“最新优先”保留完整结果，预算耗尽后只保留工具名/成败/短摘要，并明确标记压缩。
 */
object TutorPromptBudget {
    /** 工具历史在提示词里的字符预算（不是工具执行/持久化预算）。 */
    const val MAX_TOOL_HISTORY_CHARS: Int = 32_000

    /** 被压缩结果保留的摘要长度。 */
    const val COMPACT_SUMMARY_CHARS: Int = 240

    const val COMPACTED_MARK: String = "（较早结果已压缩）"
}

fun List<TutorToolRoundResult>.compactedForTutorPrompt(
    maxChars: Int = TutorPromptBudget.MAX_TOOL_HISTORY_CHARS,
): List<TutorToolRoundResult> {
    if (isEmpty()) return this
    var remaining = maxChars.coerceAtLeast(0)
    val compacted = arrayOfNulls<TutorToolRoundResult>(size)
    for (index in indices.reversed()) {
        val round = this[index]
        val fullChars = round.outcomes.sumOf { outcome ->
            outcome.summaryMarkdown.length + (outcome.dataJson?.length ?: 0)
        }
        if (fullChars <= remaining) {
            compacted[index] = round
            remaining -= fullChars
        } else {
            compacted[index] = round.copy(
                outcomes = round.outcomes.map { outcome ->
                    outcome.copy(
                        summaryMarkdown = TutorPromptBudget.COMPACTED_MARK +
                            outcome.summaryMarkdown.take(TutorPromptBudget.COMPACT_SUMMARY_CHARS),
                        dataJson = null,
                    )
                },
            )
        }
    }
    return compacted.map { requireNotNull(it) }
}
