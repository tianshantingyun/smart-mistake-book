@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Local read tools the model may request during a tool-loop round
 * (spec model-intent-routing §2). All execution is local and deterministic;
 * the model supplies requests with anchored terms and observes their results.
 */
@Serializable
enum class TutorToolName {
    APP_CONTEXT,
    SELECT_QUESTION,
    READ_FIGURE,
    RUN_JAVASCRIPT,
    REVIEW_PLAN_READ,
    /**
     * 已审知识目录检索：返回可用于「改知识点关联」的精确身份（nodeId + taxonomyVersion）。
     *
     * 与 KNOWLEDGE_READ 的分工：后者用会话代号讲“这个学生在这个知识点上怎么样”，
     * 原始 id 不进结果；本工具只回答“目录里有哪些节点、它们的精确身份是什么”，
     * 写侧（RECLASSIFY_PROBLEM）只接受这里真实返回过的 nodeId@taxonomyVersion 对。
     */
    KNOWLEDGE_CATALOG_READ,
    KNOWLEDGE_READ,
    NOTEBOOK_READ,
    MASTERY_READ,
    /** 保存当前已识别题面，执行结果与目标状态由本地处理器返回。 */
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
    /**
     * 阶段 4B A1：生图工具（第 8 枚）。模型只给**语义**（kind + description），
     * 源图与一切生成参数由本地决定——REDRAW_PROBLEM 的源图自动取自当前题的规范资产
     * （绝不接受模型供图），生成结果只回 id + 状态（不回字节）。
     */
    GENERATE_FIGURE,
    /**
     * 回读被 6,000 字符工具结果上限挡下的完整本地结果（`resultId` 由产生结果的那次工具
     * 调用返回）。只接受本会话真实产生过的 id，分页有界返回；它是“大结果不再整轮失败”的
     * 读侧，而不是放大单工具结果预算。
     */
    READ_RESULT,
}

/**
 * GENERATE_FIGURE 的两种图（A1 的 kind 枚举，两条路由同名同义）：
 * - [REDRAW_PROBLEM]：重绘当前题的题面图（去手写）；源图由本地从当前题的规范资产解析，
 *   模型既不能指定也不能提供图片。
 * - [GENERATE_PROCESS]：按 [TutorToolCall.figureDescription] 文生图（解析过程图）。
 */
@Serializable
enum class TutorFigureKind {
    REDRAW_PROBLEM,
    GENERATE_PROCESS,
}

/**
 * kind 只映射**用途**，不映射方法（4E.1 契约 §3）：入口不得用 kind 固定后端。
 */
fun TutorFigureKind.figureGenerationPurpose(): FigureGenerationPurpose = when (this) {
    TutorFigureKind.REDRAW_PROBLEM -> FigureGenerationPurpose.SOURCE_RECONSTRUCTION
    TutorFigureKind.GENERATE_PROCESS -> FigureGenerationPurpose.EXPLANATION
}

/**
 * kind 的**默认**图像操作（仅在模型没有显式声明 imageOperation 时使用）：
 * 重绘题面默认 EDIT（有源图），生成过程图默认 GENERATE（无源图）。它不是方法/用途映射。
 */
fun TutorFigureKind.defaultImageOperation(): FigureImageOperation = when (this) {
    TutorFigureKind.REDRAW_PROBLEM -> FigureImageOperation.EDIT
    TutorFigureKind.GENERATE_PROCESS -> FigureImageOperation.GENERATE
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
    /**
     * GENERATE_FIGURE 的图种类（A1；wire 键名 `kind`，两条路由同名同义）；其余工具不得携带。
     */
    val figureKind: TutorFigureKind? = null,
    /**
     * GENERATE_FIGURE 的画面说明（A1；wire 键名 `description`，上限
     * [MAX_FIGURE_DESCRIPTION_CHARS]）；其余工具不得携带。它是**语义**输入：生成提示词由它
     * 与本地固定的说明拼成，模型无法经它传图片、URL 或生成参数。
     */
    val figureDescription: String? = null,
    /**
     * 模型选择的生图**方法**（wire 键 `method`）。A-02 起**契约层**同时接受
     * [FigureGenerationMethod.IMAGE_MODEL] 与 [FigureGenerationMethod.JS]——两条解析路由
     * 对同一选择必须给出同形结果，而不是"先收下再按图像方法执行"。
     *
     * 接受 ≠ 可执行：是否真能执行由下游决定（`FigureGenerationPolicy` 的
     * `availableMethods` 门 + 执行器）。JS 执行器尚未接线到工具环时，执行侧必须继续
     * fail-closed，不得把 JS 调用落进图像生成器。
     * 省略 = 旧调用（只有 kind/description），适配层按真实可用方法构造显式 proposal。
     */
    val figureMethod: FigureGenerationMethod? = null,
    /**
     * JS 方法的完整源码（wire 键 `program`，UTF-8 ≤
     * [FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES]）。它只作为**独立字段**存在：
     * 不从 description/imagePrompt 二次提取，也不得与图像载荷同时出现（两者互斥）。
     * 非 JS 方法携带 program 一律拒——不是"先收下再忽略"。
     */
    val figureProgram: String? = null,
    /** IMAGE_MODEL 内部的图像操作（wire 键 `imageOperation`）：EDIT/GENERATE。 */
    val figureImageOperation: FigureImageOperation? = null,
    /**
     * 具体生图提示词（wire 键 `imagePrompt`，UTF-8 ≤
     * [FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES]）。它只进图像 API，不替代
     * 宿主冻结的 purpose/target/constraints。
     */
    val figureImagePrompt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val parameters: Map<String, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val script: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val stateJson: String? = null,
) {
    init {
        require(parameters.keys.all { it in tool.parameterNames() }) { "Unsupported tool parameter" }
        require(parameters.values.all { it.isNotBlank() && it.length <= 2_000 && it.none(Char::isISOControl) })
        if (tool == TutorToolName.SELECT_QUESTION) require(!parameters["entryId"].isNullOrBlank())
        if (tool == TutorToolName.READ_FIGURE) require(!parameters["assetId"].isNullOrBlank())
        if (tool == TutorToolName.READ_RESULT) require(!parameters["resultId"].isNullOrBlank())
        if (tool == TutorToolName.RUN_JAVASCRIPT) {
            require(!script.isNullOrBlank() && figureUsageUtf8Bytes(script) <= FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES)
        } else require(script == null) { "Only RUN_JAVASCRIPT carries script" }
        require(stateJson == null || tool == TutorToolName.RUN_JAVASCRIPT || tool == TutorToolName.GENERATE_FIGURE)
        stateJson?.let {
            require(it.length <= 64 * 1_024) { "JavaScript state is too large" }
            val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it) }.getOrNull()
            require(parsed is kotlinx.serialization.json.JsonObject) {
                "JavaScript state must be a JSON object"
            }
        }
        if (tool == TutorToolName.GENERATE_FIGURE && stateJson != null) {
            require(figureMethod == FigureGenerationMethod.JS && figureUsageUtf8Bytes(stateJson) <= 16 * 1_024)
        }
        parameters["limit"]?.let {
            val value = it.toIntOrNull()
            require(
                value != null && if (tool == TutorToolName.READ_RESULT) {
                    value in 1..MAX_RESULT_CHUNK_CHARS
                } else {
                    value in 1..30
                },
            )
        }
        parameters["offset"]?.let { require(it.toIntOrNull()?.let { offset -> offset >= 0 } == true) }
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
        // MASTERY_READ（本科目清单）、ADVISORY_*（作用域可能是当前题/科目）与 GENERATE_FIGURE
        // （语义全在 figureDescription 里）允许空 terms；ADVISORY_WRITE 的 NODE 作用域另有
        // "必须有代号"的专门校验，GENERATE_FIGURE 另要求 terms **必须为空**（见下）。
        if (
            tool != TutorToolName.MASTERY_READ &&
            tool != TutorToolName.NOTEBOOK_READ &&
            tool != TutorToolName.APP_CONTEXT &&
            tool != TutorToolName.SELECT_QUESTION &&
            tool != TutorToolName.READ_FIGURE &&
            tool != TutorToolName.RUN_JAVASCRIPT &&
            tool != TutorToolName.REVIEW_PLAN_READ &&
            tool != TutorToolName.READ_RESULT &&
            tool != TutorToolName.NOTEBOOK_WRITE &&
            tool != TutorToolName.ADVISORY_READ &&
            tool != TutorToolName.ADVISORY_WRITE &&
            tool != TutorToolName.GENERATE_FIGURE
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
        if (tool == TutorToolName.GENERATE_FIGURE) {
            require(figureKind != null) {
                "A GENERATE_FIGURE call must state its figure kind"
            }
            require(terms.isEmpty()) {
                "A GENERATE_FIGURE call must not carry lookup terms"
            }
            require(figureDescription != null && figureDescription.isNotBlank()) {
                "A GENERATE_FIGURE call must carry a non-blank description"
            }
            require(figureDescription.length <= MAX_FIGURE_DESCRIPTION_CHARS) {
                "A GENERATE_FIGURE description must be at most $MAX_FIGURE_DESCRIPTION_CHARS chars"
            }
            // 说明是一行文字、会进生成提示词：控制字符（含换行）只会是夹带，不是描述。
            require(figureDescription.none(Char::isISOControl)) {
                "A GENERATE_FIGURE description must not carry control characters"
            }
            val operation = figureImageOperation
            val imagePrompt = figureImagePrompt
            when (figureMethod) {
                // A-02：JS 只在**独立字段**里带完整 program 时成立；图像载荷与 program 互斥
                // （同一份 proposal 不能被两种方法各读一半）。
                FigureGenerationMethod.JS -> {
                    val program = requireNotNull(figureProgram) {
                        "A JS GENERATE_FIGURE call must carry a complete program"
                    }
                    require(program.isNotBlank()) {
                        "A JS GENERATE_FIGURE program must not be blank"
                    }
                    require(
                        figureUsageUtf8Bytes(program) <= FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES,
                    ) {
                        "A JS program must be at most " +
                            "${FigureGenerationPolicy.MAX_PROGRAM_UTF8_BYTES} UTF-8 bytes"
                    }
                    require(operation == null && imagePrompt == null) {
                        "A JS GENERATE_FIGURE call must not carry an imageOperation or imagePrompt"
                    }
                }

                // IMAGE_MODEL 与旧调用（method 省略）形状不变：program 一律拒，操作与提示词
                // 必须成对（显式选择方法时两者都必填）。
                FigureGenerationMethod.IMAGE_MODEL,
                null,
                -> {
                    require(figureProgram == null) {
                        "The JS program carrier is only valid for method=JS"
                    }
                    // 新载荷必须成对：只有操作没有提示词，或只有提示词没有操作，都是不完整的选择。
                    require((operation == null) == (imagePrompt == null)) {
                        "An imageOperation must be paired with an imagePrompt"
                    }
                    if (figureMethod != null) {
                        require(operation != null && imagePrompt != null) {
                            "IMAGE_MODEL must state both imageOperation and imagePrompt"
                        }
                    }
                }
            }
            imagePrompt?.let { prompt ->
                require(prompt.isNotBlank()) { "An imagePrompt must not be blank" }
                require(prompt.none { char -> char.isISOControl() && char != '\n' }) {
                    "An imagePrompt must not carry control characters"
                }
                require(
                    figureUsageUtf8Bytes(prompt) <= FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES,
                ) {
                    "An imagePrompt must be at most " +
                        "${FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES} UTF-8 bytes"
                }
            }
        } else {
            require(
                figureKind == null &&
                    figureDescription == null &&
                    figureMethod == null &&
                    figureProgram == null &&
                    figureImageOperation == null &&
                    figureImagePrompt == null,
            ) {
                "Only GENERATE_FIGURE carries figure fields"
            }
        }
    }

    /**
     * 这次调用对应的统一 proposal（4E WG-04b / A-02）。
     *
     * 两种方法产出**同一种** proposal，载荷按方法互斥：
     * - `IMAGE_MODEL`（含旧调用只有 kind/description 的形态）：带 imageOperation/imagePrompt，
     *   旧调用由 kind 补默认；不带 program。
     * - `JS`：只带 program；imageOperation/imagePrompt 恒为 null（构造器已保证它们没被携带）。
     *
     * 返回 null 仅当不是生图工具或缺 kind。
     */
    fun toFigureGenerationProposal(): FigureGenerationProposal? {
        if (tool != TutorToolName.GENERATE_FIGURE) return null
        val kind = figureKind ?: return null
        val method = figureMethod ?: FigureGenerationMethod.IMAGE_MODEL
        val js = method == FigureGenerationMethod.JS
        return FigureGenerationProposal(
            policyVersion = FigureGenerationPolicyVersion.VALUE,
            decision = FigureGenerationDecision.GENERATE,
            purpose = kind.figureGenerationPurpose(),
            method = method,
            reason = rationale,
            initialStateJson = stateJson,
            imageOperation = if (js) null else (figureImageOperation ?: kind.defaultImageOperation()),
            imagePrompt = if (js) null else (figureImagePrompt ?: figureDescription),
            program = if (js) figureProgram else null,
        )
    }

    companion object {
        const val MAX_TOOL_RATIONALE_CHARS = 200

        /** READ_RESULT 单次回读的字符上限（留出 JSON 信封后的 6,000 工具结果预算）。 */
        const val MAX_RESULT_CHUNK_CHARS = 3_500

        /**
         * ADVISORY_WRITE 一条 payload 的字符上限。取值理由：读侧一次最多回
         * [TutorToolOutcome.MAX_TOOL_RESULT_CHARS]（2k）字符，读回渲染要给每条留摘要位；
         * 600 ≈ 3-4 句中文，够写清"典型误区"或"有效讲法"一条持久共识，又不至于一条写满
         * 半个读预算。与 debrief 通道（1000）不同是刻意的：那是本地摘要（无工具参数预算），
         * 这是模型逐次调用参数。
         */
        const val MAX_ADVISORY_PAYLOAD_CHARS = 600

        /**
         * GENERATE_FIGURE 一条画面说明的字符上限（A1 契约：description ≤200）。
         * 取值理由：200 字符 ≈ 2-3 句中文，够写清"这张图要表达什么"；它同时是出网提示词的
         * 组成段，本地硬上限不靠 provider 的软约束（与 ADVISORY_WRITE payload 同一纪律）。
         */
        const val MAX_FIGURE_DESCRIPTION_CHARS = 200
    }
}

/** Parameter keys shared by the model DTO, native schema and JSON-envelope parser. */
fun TutorToolName.parameterNames(): Set<String> = when (this) {
    TutorToolName.SELECT_QUESTION -> setOf("entryId")
    TutorToolName.READ_FIGURE -> setOf("assetId")
    TutorToolName.RUN_JAVASCRIPT -> emptySet()
    TutorToolName.REVIEW_PLAN_READ -> emptySet()
    TutorToolName.KNOWLEDGE_CATALOG_READ -> emptySet()
    TutorToolName.READ_RESULT -> setOf("resultId", "offset", "limit")
    TutorToolName.NOTEBOOK_READ -> setOf("offset", "limit", "subject", "sectionId", "masteryId", "sort", "createdFrom", "createdTo")
    else -> setOf("subject")
}

/** Locally computed outcome of one executed tool call — the model never sees raw rows. */
@Serializable
data class TutorToolOutcome(
    val tool: TutorToolName,
    val ok: Boolean,
    val summaryMarkdown: String,
    val errorKind: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dataJson: String? = null,
) {
    init {
        dataJson?.let { require(it.length <= 6_000); kotlinx.serialization.json.Json.parseToJsonElement(it) }
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
        const val MAX_TOOL_ROUNDS = 8

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
        val laterReserve = (outcomes.size - index - 1) * (DROPPED_OUTCOME_SUMMARY.length + 1)
        val availableForThis = remaining - laterReserve - 1
        val data = outcome.dataJson?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).toString() }
        val dataFits = data == null || outcome.summaryMarkdown.length + data.length + TOOL_DATA_PREFIX.length <= availableForThis
        val carriedData = data.takeIf { dataFits }
        val summary = outcome.summaryMarkdown + if (dataFits) "" else " [结构化结果超过本轮预算，请缩小查询范围]"
        val summaryBudget = availableForThis - (carriedData?.let { it.length + TOOL_DATA_PREFIX.length } ?: 0)
        val truncatedFits = summaryBudget >= TRUNCATED_OUTCOME_NOTE.length + MIN_TRUNCATED_PREFIX_CHARS
        val replacement = when {
            summary.length <= summaryBudget -> null
            truncatedFits ->
                summary.take(summaryBudget - TRUNCATED_OUTCOME_NOTE.length) + TRUNCATED_OUTCOME_NOTE
            else -> DROPPED_OUTCOME_SUMMARY
        }
        val budgetedSummary = replacement ?: summary
        remaining -= budgetedSummary.length + 1 + (carriedData?.let { it.length + TOOL_DATA_PREFIX.length } ?: 0)
        outcome.copy(summaryMarkdown = budgetedSummary, dataJson = carriedData)
    }
}

private const val TOOL_DATA_PREFIX = "dataJson=\n"

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
 * The model's request for a tool round, including its current intent classification.
 * Actual availability comes from the capability declaration and local handlers.
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
     * 工具环**（由动作处理器执行），所以它们在这一层就被分出来，不进入 [calls]。
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
        require(calls.distinct().size == calls.size) {
            "A tool round must not repeat an identical call"
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
        const val MAX_TOOL_CALLS_PER_ROUND = 4
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
 * The current capability declaration is the executable tool boundary.
 * Intent classification remains context for the model's orchestration.
 * Each handler validates its actual target, data and execution prerequisites.
 */
@Suppress("UNUSED_PARAMETER")
fun tutorToolAuthorization(
    decision: TutorIntentDecision,
    declaredTools: Set<TutorToolName>,
): TutorToolAuthorization = TutorToolAuthorization(
    allowedTools = declaredTools.intersect(TutorCapabilitySnapshot.IMPLEMENTED_TOOLS),
    routeEligible = true,
    reason = "available declared tools",
)

/** Retained for compatibility with historical classification and audit consumers. */
const val TUTOR_TOOL_ROUTE_CONFIDENCE_THRESHOLD = 0.45

/**
 * Upper bound on simultaneously declared tools for one dispatch（spec §2 core five +
 * D-M M7 的两枚咨询工具 + 4B A1 的生图工具 = 8）。提示词里的声明块要整表渲染并逐轮持久化，
 * 上界是防"无限声明挤占讲解预算"的硬门；加工具必须同时上调它，否则生产装配的 8 工具面
 * 在构造输入时就抛。
 */
const val MAX_TOOL_DECLARATIONS = 16
