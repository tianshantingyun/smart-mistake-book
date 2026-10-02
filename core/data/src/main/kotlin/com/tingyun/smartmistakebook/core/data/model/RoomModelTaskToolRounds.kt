package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.data.study.TutorToolExecution
import com.tingyun.smartmistakebook.core.model.TutorAdvisoryScope
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome

/**
 * 工具环里**一轮工具调用**的判定与执行，整体抽出来是为了可测：这一段长在 `execute()` 里
 * 只有仪器化用例够得着，而"被拒的调用不触达执行器"的接线此前没有任何本机可跑的用例钉住
 * （复核意见二），所以判定与执行一起抽成具名函数。
 *
 * 2026-09-21 裁定（ADR 0001 / D6/D7）之后，这里**没有场景维度**——不判"这一轮来自哪个入口"，
 * 也不判"这一轮有没有题"（无题轮不再结构性拒写；写不写由模型语义判定，系统提示词教会，
 * 低置信/无引文的写入由统一本地门 MasteryWriteGate 挡）。逐次裁决只剩两条，都取自
 * **调用本身**：
 * - 意图授权矩阵：[authorizedTools]（模型这一轮自己的意图 × 置信度 × 声明集，
 *   [com.tingyun.smartmistakebook.core.model.tutorToolAuthorization] 的产物）；
 * - 单一代号通道（D5）：MASTERY_UPDATE 的 `terms[0]` 必须在本会话已披露代号集合
 *   （[disclosedKnowledgeCodes]）内——非法/编造/未披露代号结构性拒，走协议错误路径
 *   （[INVALID_KNOWLEDGE_CODE]），不进执行器、不进门。Route A 的 schema enum 约束解码
 *   是前哨，这里是 Route B（json_object 信封）与越界复述的背底。
 *
 * 其余读工具（含 MASTERY_READ / KNOWLEDGE_READ）不受限（D7：MASTERY_READ 无场景分支，
 * 输出形态/轮预算按旧裁定不变）；没有科目上下文的边界由 runner 自己失败关闭
 * （no_subject），不在轮次层分叉。被拒的调用**不会**触达 [runTool]。
 *
 * D-M M7：`ADVISORY_WRITE` 的 **NODE 作用域**走同一条代号白名单（编造代号结构性拒）；
 * PROBLEM/SUBJECT 作用域不在轮次层判——它们的目标由 runner 从会话/科目上下文解析，
 * 不经模型给的 id。`ADVISORY_READ` 与其余读工具同路（无结构性拒）。
 *
 * @param consumeExtendedResult 调用方在"一次扩展结果预算被用掉"时调用；同一轮只放一次。
 */
internal suspend fun tutorToolRoundOutcomes(
    calls: List<TutorToolCall>,
    authorizedTools: Set<TutorToolName>,
    disclosedKnowledgeCodes: Set<String>,
    runTool: suspend (TutorToolCall, Boolean) -> TutorToolExecution,
    consumeExtendedResult: () -> Unit,
): List<TutorToolExecution> {
    var extendedResultUsed = false
    return calls.map { call ->
        when {
            call.tool !in authorizedTools -> TutorToolExecution(
                TutorToolOutcome(
                    tool = call.tool,
                    ok = false,
                    summaryMarkdown = "该意图下未授权此查询。",
                    errorKind = "not_authorized",
                ),
            )
            // 无披露集 = **没有可写目标**（K2a）：大厅/无题轮没有代号通道，写工具在这里
            // 如实说"没得写"，而不是报错（报错会让模型下一轮换着法再试，白烧派遣预算）。
            // 非空白名单里对不上的代号仍走结构性拒——那是协议错误（编造 id），不是空范围。
            usesDisclosedKnowledgeCode(call) && disclosedKnowledgeCodes.isEmpty() ->
                TutorToolExecution(
                    TutorToolOutcome(
                        tool = call.tool,
                        ok = true,
                        summaryMarkdown = EMPTY_SCOPE_WRITE_SUMMARY,
                    ),
                )
            usesDisclosedKnowledgeCode(call) &&
                call.terms.firstOrNull() !in disclosedKnowledgeCodes -> TutorToolExecution(
                TutorToolOutcome(
                    tool = call.tool,
                    ok = false,
                    summaryMarkdown = "该代号不在本会话已披露的知识点中，未执行。",
                    errorKind = INVALID_KNOWLEDGE_CODE,
                ),
            )
            else -> {
                val allowsExtendedResult = !extendedResultUsed
                val execution = runTool(call, allowsExtendedResult)
                if (call.extendedResult && allowsExtendedResult) {
                    extendedResultUsed = true
                    consumeExtendedResult()
                }
                execution
            }
        }
    }
}

/**
 * 这次调用是否以**本会话已披露代号**为写目标（白名单的适用范围）：
 * MASTERY_UPDATE 的 terms[0]，以及 ADVISORY_WRITE 的 NODE 作用域 terms[0]。
 * ADVISORY_WRITE 的 PROBLEM/SUBJECT 作用域不接受模型给的 id，不走白名单。
 */
private fun usesDisclosedKnowledgeCode(call: TutorToolCall): Boolean = when (call.tool) {
    TutorToolName.MASTERY_UPDATE -> true
    TutorToolName.ADVISORY_WRITE -> call.advisoryScope == TutorAdvisoryScope.NODE
    else -> false
}

/** MASTERY_UPDATE 的代号不在本会话已披露集合：协议层结构性拒（不是门控语义拒）。 */
internal const val INVALID_KNOWLEDGE_CODE = "invalid_knowledge_code"

/**
 * 写工具在**没有可写目标**时给模型的那句话（K2a / spec §3.1）：ok=true、不报错。
 * 与 runner 里那份逐字一致（两处都是"无范围"，说同一句话，模型才不会把一处当失败）。
 */
internal const val EMPTY_SCOPE_WRITE_SUMMARY =
    "本轮无可写目标：这次对话还没有已披露的知识点。不必重复尝试，直接回答学生的问题。"
