package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一轮对话里**智能体查阅了什么**的痕迹（B1）。
 *
 * 为什么要有它：工具调用的两条活标签（「正在查阅…」/「已查阅（N 条结果）」）此前只活在内存里，
 * 轮次一完就消失——重开会话的学生看不到"它刚才查过什么、有没有被拒"，而"被拒给理由"是本项目
 * 明确的纪律（`MasteryWriteGate` 每次写入含被拒都要可见）。痕迹落进助手消息行
 * （`tutor_message.tool_trace_json`）之后，重开会话点开还是同一份事实。
 *
 * 三条边界：
 * - **只进 UI，不进模型**：它不参与 `ModelTaskInput`、不参与指纹（模型看见的是工具结果本身的
 *   markdown 摘要，与这份痕迹无关）；
 * - **本地用词，不是原始数据**：只存工具名、条数与结果码，不存模型给的 rationale/terms，
 *   也不存结果正文（正文在消息行、账本各自的位置）；
 * - **有体积上限**：超了按序丢尾部条目并在 [omittedCount] 里标注（见 [encodeTutorTurnToolTrace]）。
 */
@Serializable
data class TutorTurnToolTrace(
    val entries: List<TutorToolTraceEntry> = emptyList(),
    /** 因体积上限被丢掉的条目数；> 0 时详情里标注"另有 N 项未记录"。 */
    val omittedCount: Int = 0,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** 这一轮发起了几次调用 = 折叠态那行小字里的「N 项」。 */
    val callCount: Int get() = entries.size

    init {
        require(omittedCount >= 0) { "Omitted tool trace entries must not be negative" }
    }
}

/**
 * 一次工具调用的痕迹。
 *
 * [resultCount] 只在**本地**存在（工具执行器知道它拿到了几行）：它不是模型可见字段，因此
 * 不碰模型输入的指纹（`bf8be888` 教训约束的是模型输入，这里从头到尾没有进过输入）。
 */
@Serializable
data class TutorToolTraceEntry(
    val tool: TutorToolName,
    /** 这次拿到的结果条数；null = 该调用不产出行集（写工具，或被拒而根本没执行）。 */
    val resultCount: Int? = null,
    val ok: Boolean,
    /** 未通过时本地给出的种类码（`invalid_knowledge_code` / `rejected:*` / `failed`…）。 */
    val errorKind: String? = null,
    /**
     * A2（4B）：GENERATE_FIGURE 这一条调用的**生图事实**——资产 id、kind、模型与本轮是否真的
     * 出网生成。它是界面重建配图的指针（消息行上的痕迹 + 规范资产行 = 持久引用），也是"一次
     * 生成一次付费"的记账行（次数/时间/模型/用途四样里的三样，时间由消息行给）。
     *
     * 只进界面、不进模型：与痕迹整体一样不参与模型输入指纹。其它工具的条目为 null。
     */
    val figure: TutorToolFigureTrace? = null,
) {
    init {
        require(errorKind == null || errorKind.isNotBlank()) {
            "A tool trace error kind must be null or non-blank"
        }
        require(ok || errorKind != null) { "A refused tool trace entry must carry its error kind" }
        require(resultCount == null || resultCount >= 0) {
            "A tool trace result count must not be negative"
        }
        require(ok || resultCount == null) { "A refused tool trace entry carries no result count" }
        require(figure == null || tool == TutorToolName.GENERATE_FIGURE) {
            "Only a figure tool call may carry figure facts"
        }
        require(figure?.figureId == null || ok) {
            "A figure id only exists on a successful figure call"
        }
    }
}

/**
 * GENERATE_FIGURE 一条痕迹里的事实半（A2/A4）。
 *
 * [kind] 失败时也要有（"图重绘未完成，已保留原图"只对 REDRAW_PROBLEM 说）；
 * [figureId] 只有成功生成/命中缓存才有（规范资产 id，UI 由它经持久引用拿回本地 URI）；
 * [generatedNow] 区分"这次真的出网生成（计费）"与"幂等命中已生成资产（不计费）"——UI 的
 * "本次生成已计入额度"只在前者出现，不然就是一句假账；[model] 是生成用的模型标识（记账的
 * "模型"一列，取不到时为 null，界面就不说模型）。
 *
 * **记账四样（次数/时间/模型/用途）的落点**（零 schema：没有也不新增"额度账"表，阶段 6 的
 * 查看入口从这些既有持久事实聚合）：
 * - 次数 = `canonical_source_asset` 里 `source_type=GENERATED_FIGURE` 的行数；
 * - 时间 = 该行 `created_at_epoch_millis`（以及承载这条痕迹的消息行时间）；
 * - 模型/用途 = 本痕迹 JSON 的 `figure.model` / `figure.kind`，并同时写进生成图**文件元数据**
 *   （Software / UserComment，见 `GeneratedFigureMetadata`）。
 * 金额不在任何地方记录（不猜）。
 */
@Serializable
data class TutorToolFigureTrace(
    val kind: TutorFigureKind,
    val figureId: String? = null,
    val model: String? = null,
    val generatedNow: Boolean = false,
) {
    init {
        require(figureId == null || figureId.isNotBlank()) {
            "A figure trace id must be null or non-blank"
        }
        require(model == null || model.isNotBlank()) {
            "A figure trace model must be null or non-blank"
        }
        require(figureId != null || !generatedNow) {
            "Only a generated figure can claim a fresh generation"
        }
    }
}

/**
 * 一行痕迹的**存储上限**（字节意义上的字符数）。正常一轮最多 5 轮 × 3 次调用，条数有天然上界；
 * 这个上限是"病态情况也不把消息行撑爆"的硬门，超出的尾部条目按序丢弃并标注。
 */
const val TUTOR_TOOL_TRACE_MAX_CHARS = 8_192

/** 痕迹的 JSON 编解码器：与账本/指纹无关的一份独立载荷，键名短且稳定。 */
private val tutorToolTraceJson = Json { encodeDefaults = false }

/**
 * 痕迹 → 存储形状（JSON）。空痕迹返回 **null**：没有痕迹就不写列（与"空载体抹平"同一纪律，
 * 旧行读回同样是 null = 无痕迹）。
 *
 * 超上限时从**尾部**丢条目（前面的是学生先看到的），丢一条记一次 [TutorTurnToolTrace.omittedCount]，
 * 直到放得下。单条自身就超上限时保留它——宁可留下一条超标的痕迹，也不留下一份"什么都没查过"
 * 的假象（本地生成的三段文字不可能到这个体积，这条只是兜底）。
 */
fun encodeTutorTurnToolTrace(
    trace: TutorTurnToolTrace,
    maxChars: Int = TUTOR_TOOL_TRACE_MAX_CHARS,
): String? {
    require(maxChars > 0) { "A tool trace budget must be positive" }
    if (trace.isEmpty) return null
    var entries = trace.entries
    var omitted = trace.omittedCount
    while (true) {
        val encoded = tutorToolTraceJson.encodeToString(
            TutorTurnToolTrace.serializer(),
            TutorTurnToolTrace(entries = entries, omittedCount = omitted),
        )
        if (encoded.length <= maxChars || entries.size <= 1) return encoded
        entries = entries.dropLast(1)
        omitted += 1
    }
}

/** 存储形状 → 痕迹。null/空白/解不开/空痕迹一律返回 null（旧行与坏行都是"没有痕迹"）。 */
fun decodeTutorTurnToolTrace(json: String?): TutorTurnToolTrace? {
    val text = json?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        tutorToolTraceJson.decodeFromString(TutorTurnToolTrace.serializer(), text)
    }.getOrNull()?.takeIf { trace -> trace.entries.isNotEmpty() }
}

/**
 * 折叠态那一行小字：**中性**，不出现"0 条结果"这种读起来像失败的说法（B4）。
 *
 * 条数为 0（本轮无可读范围、无匹配）时说「已查阅 · N 项」——N 是**发起过几次查阅**，
 * 不是拿到几条结果；被拒的调用单独说，因为那是"没执行"，值得学生知道。
 */
fun tutorToolTraceHeadline(trace: TutorTurnToolTrace): String {
    if (trace.entries.isEmpty()) return TUTOR_TOOL_TRACE_EMPTY_HEADLINE
    val refused = trace.entries.count { entry -> !entry.ok }
    val consulted = "已查阅 · ${trace.entries.size} 项"
    return when {
        refused == 0 -> consulted
        refused == trace.entries.size -> "$consulted（均未执行）"
        else -> "$consulted（$refused 项未执行）"
    }
}

/** 生成中那一行小字：「正在查阅错题本、掌握情况…」（与完成态同源，避免两套文案漂移）。 */
fun tutorToolTraceRunningText(tools: List<TutorToolName>): String {
    val labels = tools.map(TutorToolName::displayLabel).distinct()
    return if (labels.isEmpty()) {
        TUTOR_TOOL_TRACE_RUNNING_HEADLINE
    } else {
        "$TUTOR_TOOL_TRACE_RUNNING_HEADLINE${labels.joinToString("、")}…"
    }
}

/** 没有条目可展示时的折叠态文案（正常路径走不到：痕迹本来就是"有调用"才写）。 */
private const val TUTOR_TOOL_TRACE_EMPTY_HEADLINE = "本轮没有发起查阅"
private const val TUTOR_TOOL_TRACE_RUNNING_HEADLINE = "正在查阅"

/** 展开后的每一行：一行主文 + 可选的一句说明（拒因就在这里）。 */
data class TutorToolTraceRow(
    val text: String,
    val detail: String? = null,
    val ok: Boolean,
)

/** 痕迹 → 渲染模型（纯函数，UI 只消费它；单测直接钉文案与分段）。 */
data class TutorToolTraceDisplay(
    val headline: String,
    val rows: List<TutorToolTraceRow>,
    val omittedCount: Int = 0,
) {
    /** 有没有可展开的明细：只有折叠态那行字时不给展开出口（不给假按钮）。 */
    val hasDetails: Boolean get() = rows.isNotEmpty()

    /** 被丢掉的条目在详情末尾如实说出来（截断不能看起来像完整）。 */
    val omittedNote: String?
        get() = if (omittedCount > 0) "另有 $omittedCount 项没有记录（体积超限）" else null
}

/** 把痕迹变成可渲染的形状：学生看到的每个字都在这里一次性定下来。 */
fun tutorToolTraceDisplay(trace: TutorTurnToolTrace): TutorToolTraceDisplay = TutorToolTraceDisplay(
    headline = tutorToolTraceHeadline(trace),
    rows = trace.entries.map { entry -> entry.toDisplayRow() },
    omittedCount = trace.omittedCount,
)

private fun TutorToolTraceEntry.toDisplayRow(): TutorToolTraceRow = TutorToolTraceRow(
    text = rowText(),
    // A4：生图条目有一句自己的一行说明（成功=记账事实；REDRAW 失败=已保留原图），
    // 它优先于通用拒因——通用拒因说的是"没生成"，学生更要知道"原图还在"。
    detail = figureDetail() ?: errorKind?.takeIf { !ok }?.let(::tutorToolRefusalText),
    ok = ok,
)

/**
 * A4：生图条目的那句说明（唯一出处，UI 直接消费）。
 *
 * 三态与文案的对应：
 * - `READY` 且本次真出网 → "本次生成已计入额度"（只记事实，不猜金额）；
 * - `READY` 且幂等命中 → 只说"此前生成的配图"，不重复计费、也不重复许账；
 * - `FAILED`（errorKind=figure_failed）的 REDRAW_PROBLEM → "图重绘未完成，已保留原图"
 *   （题面原图仍在）——**只覆盖真失败**：通道不可用（figure_unavailable）保留"没有可用
 *   通道"的通用解释，学生才知道该去配模型而不是干等；
 * - 其它失败/不可用 → null，落回通用拒因。
 */
private fun TutorToolTraceEntry.figureDetail(): String? {
    val figure = figure ?: return null
    if (ok) {
        // 只有真的拿到资产 id 才谈"生成/复用"；空范围（没有可画的题）什么都不说。
        if (figure.figureId == null) return null
        return if (figure.generatedNow) {
            FIGURE_CHARGE_NOTE
        } else {
            FIGURE_REUSED_NOTE
        }
    }
    return FIGURE_REDRAW_FAILED_NOTE.takeIf {
        figure.kind == TutorFigureKind.REDRAW_PROBLEM && errorKind == FIGURE_FAILED_KIND
    }
}

/** 生图"通道在但这次没成"的结果码（与 `RoomTutorToolRunner` 的同名字面量一致）。 */
private const val FIGURE_FAILED_KIND = "figure_failed"

/** 成功生图的那句事实（只记账不确认；金额不猜）。 */
const val FIGURE_CHARGE_NOTE = "本次生成已计入额度"

/** 幂等命中已有生成图：复用了原图，不重复付费。 */
const val FIGURE_REUSED_NOTE = "已使用此前生成的配图"

/** 重绘失败但题面原图仍在：学生需要知道这一点，否则会以为题面也坏了。 */
const val FIGURE_REDRAW_FAILED_NOTE = "图重绘未完成，已保留原图"

/**
 * A2：一条痕迹里**成功生成的**配图资产 id（按条目顺序、去重）。
 *
 * 存在意义："这一轮生成了哪些图"的唯一提取点——助手消息落库时按它建立
 * `tutor_message_source_asset` 引用（UI 与孤儿回收都靠这条持久引用），界面重建也读同一份。
 * 解不开/旧行/没有生图 → 空列表（按"没有配图"处理，不猜）。
 */
fun tutorTurnFigureAssetIds(traceJson: String?): List<String> =
    decodeTutorTurnToolTrace(traceJson)
        ?.entries
        ?.mapNotNull { entry -> entry.figure?.figureId }
        ?.distinct()
        .orEmpty()

private fun TutorToolTraceEntry.rowText(): String {
    val label = tool.displayLabel()
    if (!ok) {
        return when (tool) {
            TutorToolName.NOTEBOOK_WRITE -> "$label · 未保存"
            TutorToolName.MASTERY_UPDATE -> "$label · 未计入掌握度"
            TutorToolName.ADVISORY_WRITE -> "$label · 未记录"
            // A1：生图没有"条数"可言——出没出图是它唯一的产出。
            TutorToolName.GENERATE_FIGURE -> "$label · 未生成"
            else -> "$label · 未执行"
        }
    }
    return when (tool) {
        // 写工具没有"条数"可言：写成/没写过是它唯一的产出。
        TutorToolName.NOTEBOOK_WRITE -> "$label · 已保存"
        TutorToolName.MASTERY_UPDATE -> "$label · 已记录"
        TutorToolName.ADVISORY_WRITE -> "$label · 已记录"
        // A4：生图必须看**事实半**——没有当前题的空范围也是 ok=true（K2a），此前它会渲染成
        // "已生成"（一句假话）。带事实半而没有 id = 本轮没有可画的题；旧行（批 1 无事实半）
        // 保持原文案。
        TutorToolName.GENERATE_FIGURE -> when {
            figure?.figureId != null -> "$label · 已生成"
            figure != null -> "$label · 本轮没有可画的题"
            else -> "$label · 已生成"
        }
        // 读工具：条数为 0 = 本轮无可读范围（B4 的中性说法，不给"失败"的读感）。
        TutorToolName.KNOWLEDGE_READ,
        TutorToolName.NOTEBOOK_READ,
        TutorToolName.MASTERY_READ,
        TutorToolName.ADVISORY_READ,
        -> if ((resultCount ?: 0) > 0) {
            "$label · $resultCount 条"
        } else {
            "$label · 本轮无可读范围"
        }
    }
}

/**
 * 工具名 → **学生看得懂的说法**（内部工具名不进界面）。
 *
 * 与 `TutorToolDescriptions` 的模型侧文案分工不同：那一份是写给模型看的（形参、披露边界、
 * 规则），这一份只出现在学生眼前的那行小字里，两处不共用文本。
 */
fun TutorToolName.displayLabel(): String = when (this) {
    TutorToolName.KNOWLEDGE_READ -> "知识点"
    TutorToolName.NOTEBOOK_READ -> "错题本"
    TutorToolName.MASTERY_READ -> "掌握情况"
    TutorToolName.NOTEBOOK_WRITE -> "错题本"
    TutorToolName.MASTERY_UPDATE -> "掌握记录"
    TutorToolName.ADVISORY_READ -> "教学备注"
    TutorToolName.ADVISORY_WRITE -> "教学备注"
    // A1：学生看得懂的说法——产品里这一功能就叫"配图"。
    TutorToolName.GENERATE_FIGURE -> "配图"
}

/**
 * 结果码 → 学生看得懂的一句说明（拒因/失败原因的**唯一**翻译点）。
 *
 * 码是本地产生的稳定字符串（见 `RoomTutorToolRunner` / `tutorToolRoundOutcomes`），不是模型文本，
 * 所以这里的 when 可以逐条覆盖；未收录的码回落到中性说法——宁可少说，也不把内部机制词端上屏幕。
 */
fun tutorToolRefusalText(errorKind: String): String = when {
    errorKind.startsWith(REJECTED_REASON_PREFIX) -> {
        when (errorKind.removePrefix(REJECTED_REASON_PREFIX)) {
            "EVIDENCE_BELOW_CONFIDENCE" -> "证据不够确定，没有计入掌握度"
            "KNOWLEDGE_NODE_NOT_ANCHORED" -> "这个知识点不在本轮题的范围内，没有计入掌握度"
            "MASTERED_WITHOUT_EVIDENCE_ANCHOR",
            "POSITIVE_WITHOUT_EVIDENCE_ANCHOR",
            -> "没有能在这次对话里核对到的依据，没有计入掌握度"

            "SAME_KC_IN_COOLDOWN" -> "这个知识点刚记过一次，没有重复计入"
            "CONVERSATION_QUOTA_EXHAUSTED" -> "这次会话里的记录已经够多了，没有继续计入"
            "LEARNER_WINDOW_QUOTA_EXHAUSTED" -> "最近的记录已经够多了，没有继续计入"
            "ATTENTION_BELOW_FLOOR" -> "这一轮注意力不够（可能很久没有互动），没有计入"
            "CONTRADICTORY_SEMANTICS" -> "这次判断自己互相矛盾，没有计入掌握度"
            "OBJECTIVE_ANSWER_CONTRADICTS_POSITIVE" -> "刚答错过这道检查题，没有计入掌握度"
            "INTENT_BELOW_ROUTE_CONFIDENCE" -> "这一轮的意图不够明确，没有执行"
            "missing_semantics" -> "这条记录缺少方向或理解判断，没有计入掌握度"
            else -> "没有通过校验，没有计入掌握度"
        }
    }
    errorKind == "invalid_knowledge_code" -> "这个知识点本轮没有披露，没有执行"
    errorKind == "not_authorized" -> "这一轮不允许这类查询，没有执行"
    errorKind == "no_conversation" || errorKind == "no_tutor_session" -> "这次会话没有可保存的题目"
    errorKind == "reference_not_found" -> "这道题还没有识别出来，没有保存"
    errorKind == "not_ready" -> "这道题还没准备就绪，没有保存"
    errorKind == "awaiting_consent" -> "已经请你确认，还没执行"
    errorKind == "failed" -> "这次查询没有完成"
    // A1 生图的两条结果码（本地产生，见 RoomTutorToolRunner.generateFigure）。
    errorKind == "figure_unavailable" -> "当前没有可用的生图通道，没有生成"
    errorKind == "figure_failed" -> "这次配图没有生成成功"
    else -> "没有完成"
}

private const val REJECTED_REASON_PREFIX = "rejected:"

/**
 * 一条痕迹的**结果码**：这次调用是 ask 档（`NOTEBOOK_WRITE`），本地没有执行——它在等学生点
 * 确认卡（D-K2e：工具拼写与动作拼写是同一件事）。码本身是本地稳定字符串，与工具结果里的
 * `errorKind` 是同一个值：生成中那行小字、重开会话看到的那行小字、以及回喂模型的那句话
 * 读的都是它。
 */
const val TOOL_AWAITING_CONSENT_ERROR_KIND = "awaiting_consent"
