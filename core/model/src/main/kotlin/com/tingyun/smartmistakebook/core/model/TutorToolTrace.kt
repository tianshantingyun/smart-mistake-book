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
    detail = errorKind?.takeIf { !ok }?.let(::tutorToolRefusalText),
    ok = ok,
)

private fun TutorToolTraceEntry.rowText(): String {
    val label = tool.displayLabel()
    if (!ok) {
        return when (tool) {
            TutorToolName.NOTEBOOK_WRITE -> "$label · 未保存"
            TutorToolName.MASTERY_UPDATE -> "$label · 未计入掌握度"
            else -> "$label · 未执行"
        }
    }
    return when (tool) {
        // 写工具没有"条数"可言：写成/没写过是它唯一的产出。
        TutorToolName.NOTEBOOK_WRITE -> "$label · 已保存"
        TutorToolName.MASTERY_UPDATE -> "$label · 已记录"
        // 读工具：条数为 0 = 本轮无可读范围（B4 的中性说法，不给"失败"的读感）。
        TutorToolName.KNOWLEDGE_READ,
        TutorToolName.NOTEBOOK_READ,
        TutorToolName.MASTERY_READ,
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
