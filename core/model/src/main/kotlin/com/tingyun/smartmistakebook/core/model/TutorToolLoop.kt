package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Local read tools the model may request during a tool-loop round
 * (spec model-intent-routing §2). All execution is local and deterministic;
 * the model only ever supplies an intent-gated request with anchored terms.
 */
@Serializable
enum class TutorToolName {
    KNOWLEDGE_READ,
    NOTEBOOK_READ,
    MASTERY_READ,
    /** 写工具 T6：模型提语义证据、本地门控落库（spec §5）；T4 需学生明确命令，当前阶段仅声明不启用。 */
    NOTEBOOK_WRITE,
    MASTERY_UPDATE,
    /**
     * D-M M7：教学咨询层（`llm_teaching_advisory`）的读数与写入，两枚**一等工具**
     * （任何轮次可读写"该生典型误区/有效讲法"）。读侧按节点/题/科目取最近 N 条；
     * 写侧三 kind 限枚举、稳定键 upsert（同一目标同一 kind 更新而不堆积）。
     * 咨询层由模型独家拥有，与投影行互不污染（mastery-scheduling §3）。
     */
    ADVISORY_READ,
    ADVISORY_WRITE,
}

/**
 * ADVISORY_* 的作用域（D-M M7）：咨询行挂在哪个目标上。
 *
 * - [NODE]：`terms[0]` 是**本会话已披露的知识点代号**（D5：绝不用原始 id）——写侧解析该
 *   节点并校验它真实存在且属于本会话科目；读侧按节点过滤。
 * - [PROBLEM]：当前会话正在处理的题（本地从会话记录解析 practice unit，不接受模型给 id）。
 * - [SUBJECT]：本会话科目。
 */
@Serializable
enum class TutorAdvisoryScope {
    NODE,
    PROBLEM,
    SUBJECT,
}

/**
 * 咨询行的三档（与 [TeachingAdvisoryRecord] 的三个 kind 常量一一对应；enum 白名单是写侧
 * 第一道门）。[DIFFICULTY_TIER] 的 payload 只能是 EASY/MEDIUM/HARD（消费方
 * `StudyReviewPlannerService` 按 `TutorDifficultyTier` 解析）。
 */
@Serializable
enum class TutorAdvisoryKind {
    TEACHING_FOCUS,
    MISCONCEPTION,
    DIFFICULTY_TIER,
}

/**
 * Model-judged evidence direction for a MASTERY_UPDATE call. The model
 * decides the semantic sign; the local gate and weight table decide
 * everything numeric (research tutor-evidence-gate §0.1).
 */
@Serializable
enum class TutorEvidenceDirection {
    POSITIVE,
    NEGATIVE,
}

/**
 * Model-judged tier of how well the student understands the current
 * knowledge point (research tutor-evidence-gate §1: a noisy predictor, never
 * a fact — self-report of understanding is systematically overconfident).
 * The mapping to an evidence weight is local and constant (FSRS-grade
 * analogy: STRUGGLING↔Again, UNCERTAIN↔Hard, CONFIDENT↔Good,
 * MASTERED-with-behavioral-support↔Easy).
 */
@Serializable
enum class TutorUnderstandingTier {
    STRUGGLING,
    UNCERTAIN,
    CONFIDENT,
    MASTERED,
}

/**
 * Model-judged difficulty tier of the current problem. Advisory only —
 * audited with the evidence, never enters the forgetting curve.
 */
@Serializable
enum class TutorDifficultyTier {
    EASY,
    MEDIUM,
    HARD,
}

/** One model-issued tool request for the current round. */
@Serializable
data class TutorToolCall(
    val tool: TutorToolName,
    val rationale: String,
    val terms: List<String> = emptyList(),
    /**
     * Model-judged semantic direction — MASTERY_UPDATE only. The model
     * decides the sign (it is the semantic element only it can judge from
     * the dialogue); the numeric weight stays local.
     */
    val direction: TutorEvidenceDirection? = null,
    /**
     * Model-judged understanding tier — MASTERY_UPDATE only. Semantic
     * judgment; the tier→weight mapping stays local and constant.
     */
    val understanding: TutorUnderstandingTier? = null,
    /** Model-judged difficulty tier (advisory, audited with the evidence). */
    val difficultyTier: TutorDifficultyTier? = null,
    /**
     * 这一**次调用**声明锚在哪一道题（逐次题锚，两种解析路由都能表达的落点）。
     *
     * 判据与轮次声明完全相同（候选必须在派发前的菜单内、锚词必须在学生消息里逐字出现且在该题
     * 自身文本里可核对），但落点不同：轮次声明要整轮的信封才能表达，而原生 tool_calls 路由的
     * 标准形态 content=null，轮次声明无处可放；逐次锚把复述放回**两种路由都能表达**的地方。
     *
     * 2026-09-21 裁定（D6）之后它**不再是写工具准入的来源**：无题轮不再结构性拒写，写不写
     * 由模型语义判定，MASTERY_UPDATE 的准入改为代号白名单（本会话已披露集合）。此字段仍被
     * 解析（模型可能照旧复述），保留供审计与轮次绑定的旁证；不消费它做放/拒判定。
     */
    val boundQuestion: TutorRoundQuestionDeclaration? = null,
    /**
     * Model's own confidence in its semantic judgment (0..1), MASTERY_UPDATE
     * only. The local gate thresholds it against
     * [com.tingyun.smartmistakebook.core.domain.MasteryWriteGate.EVIDENCE_CONFIDENCE_THRESHOLD].
     */
    val confidence: Double = 0.8,
    /**
     * Asks for the larger result budget on this one call — MASTERY_READ only.
     * A **semantic** request, not a number: the model says "this query may need
     * more room" and the ceiling stays a local constant
     * ([TutorToolOutcome.MAX_TOOL_RESULT_CHARS_EXTENDED]). Letting the model
     * name a size would hand it control over how much learning data leaves the
     * device — the same split every other numeric decision follows (the model
     * supplies semantics, the local layer supplies numbers).
     *
     * Only MASTERY_READ may set it: it is the one read tool whose result grows
     * with the learner's history. The round-level guard that at most one such
     * call is honoured per round lives in the executor, because that is where
     * the round's other calls are visible.
     */
    val extendedResult: Boolean = false,
    /**
     * ADVISORY_READ / ADVISORY_WRITE 的作用域（D-M M7）。读侧可省略：省略时按 terms
     * 推断（terms 空 → SUBJECT，非空 → NODE）。写侧必填且必须是合法组合。
     */
    val advisoryScope: TutorAdvisoryScope? = null,
    /** ADVISORY_WRITE 的咨询档（三 kind 白名单）；其余工具不得携带。 */
    val advisoryKind: TutorAdvisoryKind? = null,
    /** ADVISORY_WRITE 的正文（上限 [MAX_ADVISORY_PAYLOAD_CHARS]）；其余工具不得携带。 */
    val payloadMarkdown: String? = null,
) {
    init {
        require(rationale.isNotBlank() && rationale.length <= MAX_TOOL_RATIONALE_CHARS) {
            "A tool call must state an anchored reason of at most $MAX_TOOL_RATIONALE_CHARS chars"
        }
        require(confidence in 0.0..1.0) { "Tool call confidence must be in 0..1" }
        require(
            terms.size <= TutorIntentDecision.MAX_LOOKUP_TERMS &&
                terms.all { term ->
                    term == term.trim() &&
                        term.length in 1..TutorIntentDecision.MAX_LOOKUP_TERM_CHARS &&
                        term.none(Char::isISOControl)
                } &&
                terms.distinctBy(String::lowercase).size == terms.size,
        ) {
            "Tool call terms must be short distinct student-derived words"
        }
        // MASTERY_READ（本科目清单）与 ADVISORY_*（作用域可能是当前题/科目）允许空 terms；
        // ADVISORY_WRITE 的 NODE 作用域另有"必须有代号"的专门校验（见下）。
        if (
            tool != TutorToolName.MASTERY_READ &&
            tool != TutorToolName.ADVISORY_READ &&
            tool != TutorToolName.ADVISORY_WRITE
        ) {
            require(terms.isNotEmpty()) {
                "The $tool tool requires at least one lookup term"
            }
        }
        require(!extendedResult || tool == TutorToolName.MASTERY_READ) {
            "Only MASTERY_READ may request an extended result budget"
        }
        if (tool == TutorToolName.MASTERY_UPDATE) {
            require(direction != null) {
                "A MASTERY_UPDATE call must state a model-judged evidence direction"
            }
            require(understanding != null) {
                "A MASTERY_UPDATE call must state a model-judged understanding tier"
            }
        } else {
            require(direction == null && understanding == null) {
                "Only MASTERY_UPDATE carries a direction or understanding tier"
            }
        }
        if (tool == TutorToolName.ADVISORY_WRITE) {
            require(advisoryScope != null) {
                "An ADVISORY_WRITE call must state its scope"
            }
            require(advisoryKind != null) {
                "An ADVISORY_WRITE call must state its advisory kind"
            }
            require(payloadMarkdown != null && payloadMarkdown.isNotBlank()) {
                "An ADVISORY_WRITE call must carry a non-blank payload"
            }
            require(payloadMarkdown.length <= MAX_ADVISORY_PAYLOAD_CHARS) {
                "An ADVISORY_WRITE payload must be at most $MAX_ADVISORY_PAYLOAD_CHARS chars"
            }
            require(payloadMarkdown.all { char -> !char.isISOControl() || char == '\n' }) {
                "An ADVISORY_WRITE payload must not carry control characters"
            }
            // NODE 作用域的目标是代号（terms[0]）；PROBLEM/SUBJECT 的写不接受多余 terms——
            // 模型给了没有意义、本地又不读的参数，就是一条静默丢弃的输入。
            when (advisoryScope) {
                TutorAdvisoryScope.NODE -> require(terms.isNotEmpty()) {
                    "A node-scoped ADVISORY_WRITE must name the disclosed knowledge code"
                }

                TutorAdvisoryScope.PROBLEM,
                TutorAdvisoryScope.SUBJECT,
                -> require(terms.isEmpty()) {
                    "A ${advisoryScope.name}-scoped ADVISORY_WRITE must not carry terms"
                }
            }
            // 难度档是**题目级**语义（消费方按 practice unit 读）；挂到节点或科目上没有读者，
            // 会变成一条"写了但没人用"的假记录——在校验层挡住，而不是写进去再说。
            if (advisoryKind == TutorAdvisoryKind.DIFFICULTY_TIER) {
                require(advisoryScope == TutorAdvisoryScope.PROBLEM) {
                    "A DIFFICULTY_TIER advisory only makes sense for the current problem"
                }
            }
        } else {
            require(advisoryKind == null && payloadMarkdown == null) {
                "Only ADVISORY_WRITE carries an advisory kind or payload"
            }
            if (tool == TutorToolName.ADVISORY_READ) {
                // 读侧作用域与 terms 的组合必须自洽：PROBLEM/SUBJECT 不接受筛选词，
                // NODE（显式或由非空 terms 推断）要能落到一个节点上。
                when (advisoryScope) {
                    TutorAdvisoryScope.PROBLEM,
                    TutorAdvisoryScope.SUBJECT,
                    -> require(terms.isEmpty()) {
                        "A ${advisoryScope.name}-scoped ADVISORY_READ must not carry terms"
                    }

                    TutorAdvisoryScope.NODE -> require(terms.isNotEmpty()) {
                        "A node-scoped ADVISORY_READ must name a code or keyword"
                    }

                    null -> Unit
                }
            } else {
                require(advisoryScope == null) {
                    "Only the ADVISORY tools may state an advisory scope"
                }
            }
        }
    }

    companion object {
        const val MAX_TOOL_RATIONALE_CHARS = 200

        /**
         * ADVISORY_WRITE 一条 payload 的字符上限。取值理由：读侧一次最多回
         * [TutorToolOutcome.MAX_TOOL_RESULT_CHARS]（2k）字符，读回渲染要给每条留摘要位；
         * 600 ≈ 3-4 句中文，够写清"典型误区"或"有效讲法"一条持久共识，又不至于一条写满
         * 半个读预算。与 debrief 通道（1000）不同是刻意的：那是本地摘要（无工具参数预算），
         * 这是模型逐次调用参数。
         */
        const val MAX_ADVISORY_PAYLOAD_CHARS = 600
    }
}

/** Locally computed outcome of one executed tool call — the model never sees raw rows. */
@Serializable
data class TutorToolOutcome(
    val tool: TutorToolName,
    val ok: Boolean,
    val summaryMarkdown: String,
    val errorKind: String? = null,
) {
    init {
        summaryMarkdown.requireSafeModelText(
            label = "Tutor tool outcome summary",
            maxChars = MAX_TOOL_RESULT_CHARS_EXTENDED,
            allowLineBreaks = true,
        )
        require(ok || errorKind != null) { "A failed tool outcome must carry an error kind" }
    }

    companion object {
        /**
         * Default result budget every tool targets. A tool that emits more must
         * have a reason recorded on it (today only `MASTERY_READ` does, and only
         * for a call that asked for the extended budget).
         */
        const val MAX_TOOL_RESULT_CHARS = 2_000

        /**
         * Absolute ceiling for one outcome, applied by [TutorToolOutcome]'s own
         * validation. It is the larger value because a model-requested extended
         * read is legitimate here; the pipeline still needs a hard bound so a
         * mis-sized result can never silently inflate the next prompt.
         *
         * Sized so a full subject list stays inside it with room to spare: a
         * subject carries on the order of a few hundred knowledge nodes *with*
         * mastery state, and one compact line each fits well under this.
         *
         * The total-across-a-round budget is a separate, implemented concern:
         * [TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS] caps a round at
         * 4k — spec §3.1's ≤4k/round — plus
         * [TutorToolRoundResult.EXTENDED_ROUND_RESULT_INCREMENT_CHARS] when the
         * round spent its single extended call. [tutorToolRoundResult] applies
         * the cap; the executor honours at most one extended call per round.
         */
        const val MAX_TOOL_RESULT_CHARS_EXTENDED = 6_000
    }
}

/** Everything the model gets back for one tool round, injected into the next prompt. */
@Serializable
data class TutorToolRoundResult(
    val roundOrdinal: Int,
    val outcomes: List<TutorToolOutcome>,
) {
    init {
        require(roundOrdinal in 1..MAX_TOOL_ROUNDS) {
            "Tool round ordinal $roundOrdinal is outside 1..$MAX_TOOL_ROUNDS"
        }
        require(outcomes.isNotEmpty()) { "A tool round must carry at least one outcome" }
    }

    companion object {
        const val MAX_TOOL_ROUNDS = 5

        /**
         * Total characters of tool results one round may add to the next prompt
         * (spec model-intent-routing §3.1).
         *
         * The spec's 4k was written for a round of at most two calls, and the
         * implementation allows three — so this is deliberately a cap on the
         * *sum*, not a restatement of the per-call budget: three calls at the
         * default 2k would otherwise add 6k with nothing bounding the total.
         */
        const val MAX_TOOL_ROUND_RESULT_CHARS = 4_000

        /**
         * Extra round budget granted when the round contains a call that asked
         * for the extended result budget. Without this the 4k round cap would
         * make a 6k single result unreachable, which would quietly cancel the
         * extended budget the model is allowed to request.
         */
        const val EXTENDED_ROUND_RESULT_INCREMENT_CHARS = 4_000
    }
}

/**
 * Builds the round result the next dispatch will carry, applying the round's
 * result budget.
 *
 * The budget decision lives here rather than in the repository that assembles
 * the round: which number applies, and how a round that asked for the extended
 * budget differs, is policy — and policy belongs where it can be tested without
 * a database. The repository keeps only the call.
 */
fun tutorToolRoundResult(
    roundOrdinal: Int,
    outcomes: List<TutorToolOutcome>,
    extendedResultUsed: Boolean,
): TutorToolRoundResult = TutorToolRoundResult(
    roundOrdinal = roundOrdinal,
    outcomes = budgetTutorToolOutcomes(
        outcomes = outcomes,
        budgetChars = TutorToolRoundResult.MAX_TOOL_ROUND_RESULT_CHARS +
            if (extendedResultUsed) {
                TutorToolRoundResult.EXTENDED_ROUND_RESULT_INCREMENT_CHARS
            } else {
                0
            },
    ),
)

/**
 * Fits a round's outcomes into [budgetChars], in order.
 *
 * In order, not proportionally: the model asked for these queries in a
 * sequence, so the earlier ones are the ones it wanted first. What does not fit
 * is still reported — a silently shortened result reads as a complete one.
 *
 * Each step first sets aside enough for every *later* outcome to at least carry
 * its "not sent" line. Without that reserve the round overshoots the very budget
 * it is enforcing: admitting that a result was dropped costs characters too.
 *
 * A squeezed outcome keeps its `ok` and `errorKind`: those describe whether the
 * tool ran, and shortening the text does not change that. Only the summary is
 * rewritten, and always to something non-blank so the outcome stays valid.
 */
fun budgetTutorToolOutcomes(
    outcomes: List<TutorToolOutcome>,
    budgetChars: Int,
): List<TutorToolOutcome> {
    val minimumRoundCost = outcomes.size * (DROPPED_OUTCOME_SUMMARY.length + 1)
    require(budgetChars >= minimumRoundCost) {
        "A tool round budget of $budgetChars chars cannot report ${outcomes.size} outcomes"
    }
    var remaining = budgetChars
    return outcomes.mapIndexed { index, outcome ->
        val summary = outcome.summaryMarkdown
        val laterReserve = (outcomes.size - index - 1) * (DROPPED_OUTCOME_SUMMARY.length + 1)
        val availableForThis = remaining - laterReserve - 1
        val truncatedFits = availableForThis >= TRUNCATED_OUTCOME_NOTE.length + MIN_TRUNCATED_PREFIX_CHARS
        val replacement = when {
            summary.length <= availableForThis -> null
            truncatedFits ->
                summary.take(availableForThis - TRUNCATED_OUTCOME_NOTE.length) + TRUNCATED_OUTCOME_NOTE
            else -> DROPPED_OUTCOME_SUMMARY
        }
        val budgetedSummary = replacement ?: summary
        remaining -= budgetedSummary.length + 1
        if (replacement == null) outcome else outcome.copy(summaryMarkdown = replacement)
    }
}

/**
 * How much of a truncated outcome's own text must survive for the truncation to
 * be worth doing at all: a two-character prefix followed by "已截断" tells the
 * model nothing it can use, so at that point the dropped-summary line is the
 * more honest answer.
 */
private const val MIN_TRUNCATED_PREFIX_CHARS = 12

/**
 * The model-visible marker appended to a result the round budget had to cut.
 * Public because it is part of the budget's observable contract — a consumer
 * that renders or asserts on tool results has to be able to recognise it
 * without re-spelling the text and drifting from it.
 */
const val TRUNCATED_OUTCOME_NOTE = "…[本轮结果预算已用尽，本条已截断]"

/**
 * The model-visible stand-in for a result the round budget could not carry at
 * all. Kept non-blank because a tool outcome's summary is required to be.
 */
const val DROPPED_OUTCOME_SUMMARY = "本轮结果预算已用尽，本条摘要未随请求发送。"

/**
 * The model's request for a tool round: it must restate its intent decision so the
 * local authorization matrix can gate every call against intent + confidence.
 * The looping repository supplies kind/subject context — this output is a
 * protocol artifact, never persisted as a terminal task result.
 */
@Serializable
@SerialName("tutor_tool_requests_output")
data class TutorToolRequestsOutput(
    val intentDecision: TutorIntentDecision,
    val calls: List<TutorToolCall>,
    val modelVersion: String,
    /**
     * 这一轮里模型申请的**本地动作**（D-K2e 白名单）：原生 tool_calls 路由下，本地动作与工具
     * 是同一个 `tools` 数组里的两种函数——工具走工具环（本地执行、结果回喂），本地动作**不走
     * 工具环**（执行要等学生点确认卡），所以它们在这一层就被分出来，不进入 [calls]。
     *
     * 空列表不落键（`bf8be888`）：Route B 信封与本类型早期的编码里都没有这个键。
     */
    val localActions: List<TutorLocalActionRequest> = emptyList(),
) : ModelTaskOutput {
    init {
        require(calls.isNotEmpty() || localActions.isNotEmpty()) {
            "A tool round must request at least one tool call or local action"
        }
        require(calls.size <= MAX_TOOL_CALLS_PER_ROUND) {
            "A tool round must request 1..$MAX_TOOL_CALLS_PER_ROUND calls"
        }
        require(calls.distinctBy(TutorToolCall::tool).size == calls.size) {
            "A tool round must not request the same tool twice"
        }
        require(localActions.size <= MAX_LOCAL_ACTION_REQUESTS_PER_ROUND) {
            "A tool round must not request too many local actions"
        }
        require(localActions.distinct().size == localActions.size) {
            "A tool round must not request the same local action twice"
        }
        modelVersion.requireSafeModelText(
            label = "Tutor tool request model version",
            maxChars = MAX_TOOL_REQUEST_MODEL_VERSION_CHARS,
            allowLineBreaks = false,
        )
    }

    companion object {
        const val MAX_TOOL_CALLS_PER_ROUND = 3
        const val MAX_TOOL_REQUEST_MODEL_VERSION_CHARS = 64
    }
}

/** Result of the local authorization matrix applied to one tool round (spec §3.2). */
data class TutorToolAuthorization(
    val allowedTools: Set<TutorToolName>,
    val routeEligible: Boolean,
    val reason: String,
)

/**
 * Intent × confidence × declared-set gate for one tool round. Quantified per
 * spec §9.4: below [ROUTE_CONFIDENCE_THRESHOLD] the intent is untrusted and no
 * tool runs; per-intent tool sets follow the local boundary matrix; the
 * declared set the repository announced in the prompt is intersected last.
 */
fun tutorToolAuthorization(
    decision: TutorIntentDecision,
    declaredTools: Set<TutorToolName>,
): TutorToolAuthorization {
    if (decision.confidence < ROUTE_CONFIDENCE_THRESHOLD) {
        return TutorToolAuthorization(
            allowedTools = emptySet(),
            routeEligible = false,
            reason = "intent confidence ${decision.confidence} below $ROUTE_CONFIDENCE_THRESHOLD",
        )
    }
    if (decision.intent == TutorMessageIntent.AMBIGUOUS) {
        return TutorToolAuthorization(
            allowedTools = emptySet(),
            routeEligible = false,
            reason = "intent is ambiguous",
        )
    }
    val byIntent = when (decision.intent) {
        TutorMessageIntent.CURRENT_QUESTION_HELP ->
            setOf(
                TutorToolName.KNOWLEDGE_READ,
                TutorToolName.NOTEBOOK_READ,
                TutorToolName.MASTERY_READ,
                TutorToolName.MASTERY_UPDATE,
                TutorToolName.NOTEBOOK_WRITE,
                TutorToolName.ADVISORY_READ,
                TutorToolName.ADVISORY_WRITE,
            )
        TutorMessageIntent.MISTAKE_NOTEBOOK_LOOKUP ->
            setOf(TutorToolName.NOTEBOOK_READ, TutorToolName.ADVISORY_READ, TutorToolName.ADVISORY_WRITE)
        TutorMessageIntent.LEARNING_PROGRESS_LOOKUP ->
            setOf(TutorToolName.MASTERY_READ, TutorToolName.ADVISORY_READ, TutorToolName.ADVISORY_WRITE)
        // D-M M7：两枚咨询工具是**任何轮次**的一等工具（学生可能在闲聊里说出一个持续误区，
        // 也可能在任何一轮要求回顾已记的讲法）——意图矩阵不为它们设场景门；写档是
        // AUTO_VISIBLE（自动执行、行为可见、被拒给理由），不需要 explicitActionRequest。
        TutorMessageIntent.APP_HELP_OR_SETTINGS,
        TutorMessageIntent.CASUAL_CONVERSATION,
        TutorMessageIntent.END_OR_PAUSE,
        -> setOf(TutorToolName.ADVISORY_READ, TutorToolName.ADVISORY_WRITE)
    }
    // 写工具（MASTERY_UPDATE / NOTEBOOK_WRITE）的确认门：MASTERY_UPDATE 由本地 gate 全权
    // 决定（spec §5.2：模型提交证据+本地门控）；NOTEBOOK_WRITE 需要学生明确要求
    // （explicitActionRequest，spec §9.7），模型自报"学生说收"才放行——本地无法独立验证，
    // 靠授权矩阵 + 学生明确命令双重兜底。未明确确认的写工具申请一律不放行。
    val allowed = byIntent.intersect(declaredTools).filterTo(mutableSetOf()) { tool ->
        when (tool) {
            TutorToolName.NOTEBOOK_WRITE -> decision.explicitActionRequest
            // MASTERY_UPDATE 不需要 explicitActionRequest（本地 gate 做主）。
            else -> true
        }
    }
    return TutorToolAuthorization(
        allowedTools = allowed,
        routeEligible = true,
        reason = if (allowed.isEmpty()) {
            if (TutorToolName.NOTEBOOK_WRITE in byIntent && !decision.explicitActionRequest) {
                "NOTEBOOK_WRITE requires an explicit student request"
            } else {
                "intent ${decision.intent} has no declared tools"
            }
        } else {
            "allowed ${allowed.map(TutorToolName::name)}"
        },
    )
}

/** Thresholds are contract constants pending calibration (spec §9.4). */
const val TUTOR_TOOL_ROUTE_CONFIDENCE_THRESHOLD = 0.45
private const val ROUTE_CONFIDENCE_THRESHOLD = TUTOR_TOOL_ROUTE_CONFIDENCE_THRESHOLD

/**
 * Upper bound on simultaneously declared tools for one dispatch（spec §2 core five +
 * D-M M7 的两枚咨询工具）。提示词里的声明块要整表渲染并逐轮持久化，上界是防"无限声明
 * 挤占讲解预算"的硬门；加工具必须同时上调它，否则生产装配的 7 工具面在构造输入时就抛。
 */
const val MAX_TOOL_DECLARATIONS = 7
