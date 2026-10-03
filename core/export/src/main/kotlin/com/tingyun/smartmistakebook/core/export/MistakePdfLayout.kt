package com.tingyun.smartmistakebook.core.export

/**
 * 题面块的类型代号：`blockOrder` 白名单的合法取值。
 *
 * 为什么要有这个枚举：`blockOrder` 是模型可提议、学生可改的排版参数（B3），
 * 字符串白名单必须有一处**唯一的**权威定义，否则模型侧、payload 校验侧与渲染侧
 * 会各认一套拼写（这类不一致不会报错，只会静默按默认顺序排版）。
 */
enum class MistakePdfBlockKind(val token: String) {
    PARAGRAPH("paragraph"),
    SECTION_HEADING("section_heading"),
    FORMULA("formula"),
    CHOICE_GROUP("choice_group"),
    FIGURE("figure"),
    ;

    companion object {
        fun fromToken(token: String): MistakePdfBlockKind? =
            entries.firstOrNull { it.token == token }
    }
}

/** [MistakePdfLayout.validate] 的违规项（空集 = 合法）。 */
enum class MistakePdfLayoutViolation {
    UNKNOWN_TEMPLATE,
    MARGIN_OUT_OF_RANGE,
    FONT_SCALE_OUT_OF_RANGE,
    COLUMN_COUNT_UNSUPPORTED,
    IMAGE_SCALE_OUT_OF_RANGE,
    UNKNOWN_BLOCK_ORDER_ENTRY,
    DUPLICATE_BLOCK_ORDER_ENTRY,
}

/**
 * 导出 A4 的版式参数（阶段 4B 批 3 · B1）。
 *
 * 它消灭的失败：改前所有排版常数硬编码在渲染器里，"两栏""字大点""不要答案"这类学生
 * 口头要求无处落地，且换版式后缓存键不变（会拿旧版式的文件冒充新版式）。这里把版式
 * 收成**不可变值对象**：合法性一处校验（[validate]），身份一处序列化（[canonicalForm]，
 * 进 [exportFingerprint] 全字段），渲染层只读它、不再自带常数。
 *
 * 三个模板：
 * - [TEMPLATE_COMPACT]（默认）：与参数化之前的固定版式逐位等价（只做题面，无作答区）；
 * - [TEMPLATE_PRACTICE_SHEET]：练习卷——选择/段落块后留作答空白区；
 * - [TEMPLATE_WITH_ANSWERS]：答案卷——等同于同时把 [includeAnswer] 打开（答案独立成区）。
 *
 * 答案解析的裁定（用户 2026-10-01）：题目区在前，答案/解析**独立成区**排在后部，
 * 不逐题附在题尾。
 */
data class MistakePdfLayout(
    val templateId: String = TEMPLATE_COMPACT,
    /** 四边页边距（pt）：24..72，默认 48（等于参数化前的固定边距）。 */
    val marginPt: Int = DEFAULT_MARGIN_PT,
    /** 字号档位 1..3，默认 2 = 参数化前的固定字号表。 */
    val fontScale: Int = DEFAULT_FONT_SCALE,
    /** 1 = 单栏（默认），2 = 双栏。 */
    val columnCount: Int = DEFAULT_COLUMN_COUNT,
    /**
     * 空 = 默认顺序；非空 = 块类型顺序白名单（[MistakePdfBlockKind.token] 的有序子集）。
     *
     * 白名单只改变**排列顺序**，不丢弃未列出的块：排不进白名单的块按题面原顺序排在
     * 已列出块之后。静默丢内容比顺序不符合预期严重得多，导出宁可"顺序不完美"也不
     * 允许"题目少一块"。
     */
    val blockOrder: List<String> = emptyList(),
    /** 图形/干净题面图的缩放：0.5..1.0，默认 1.0。 */
    val imageScale: Float = DEFAULT_IMAGE_SCALE,
    /** 答案区（ChoiceGroup 已选项文本）：默认关。 */
    val includeAnswer: Boolean = false,
    /** 解析区：默认关；当前无解析数据源，打开时按 UNSUPPORTED_LAYOUT_FEATURE fail-closed。 */
    val includeSolution: Boolean = false,
    /** 备注区（error_book_entry.user_note）：默认关。 */
    val includeNote: Boolean = false,
) {
    /** 域校验：返回空集表示合法；调用方（payload 校验 / 导出入口）必须先过这里。 */
    fun validate(): Set<MistakePdfLayoutViolation> = buildSet {
        if (templateId !in TEMPLATE_IDS) add(MistakePdfLayoutViolation.UNKNOWN_TEMPLATE)
        if (marginPt !in MIN_MARGIN_PT..MAX_MARGIN_PT) {
            add(MistakePdfLayoutViolation.MARGIN_OUT_OF_RANGE)
        }
        if (fontScale !in MIN_FONT_SCALE..MAX_FONT_SCALE) {
            add(MistakePdfLayoutViolation.FONT_SCALE_OUT_OF_RANGE)
        }
        if (columnCount !in SUPPORTED_COLUMN_COUNTS) {
            add(MistakePdfLayoutViolation.COLUMN_COUNT_UNSUPPORTED)
        }
        if (!(imageScale in MIN_IMAGE_SCALE..MAX_IMAGE_SCALE)) {
            add(MistakePdfLayoutViolation.IMAGE_SCALE_OUT_OF_RANGE)
        }
        val kinds = blockOrder.map(MistakePdfBlockKind::fromToken)
        if (kinds.any { it == null }) add(MistakePdfLayoutViolation.UNKNOWN_BLOCK_ORDER_ENTRY)
        val knownKinds = kinds.filterNotNull()
        if (knownKinds.size != knownKinds.distinct().size) {
            add(MistakePdfLayoutViolation.DUPLICATE_BLOCK_ORDER_ENTRY)
        }
    }

    /**
     * 布局身份的确定性序列化（进 [exportFingerprint]）。
     *
     * 用行内 `key=value;` 串而不是 `data class.toString()`：后者依赖属性声明顺序，
     * 重构属性时可能悄悄换掉全部缓存的指纹。浮点用 [Float.toRawBits] 的**无损**整数值
     * （不用 `%.Nf` 量化——两个合法值只要差在量化精度内就会指纹相同，等于"换了参数没换
     * 缓存键"，学生会拿到旧版式的文件）。
     */
    fun canonicalForm(): String = buildString {
        append("templateId=").append(templateId)
        append(";marginPt=").append(marginPt)
        append(";fontScale=").append(fontScale)
        append(";columnCount=").append(columnCount)
        append(";imageScaleBits=").append(imageScale.toRawBits())
        append(";blockOrder=").append(blockOrder.joinToString(separator = ","))
        append(";includeAnswer=").append(includeAnswer)
        append(";includeSolution=").append(includeSolution)
        append(";includeNote=").append(includeNote)
    }

    companion object {
        const val TEMPLATE_PRACTICE_SHEET = "practice_sheet"
        const val TEMPLATE_COMPACT = "compact"
        const val TEMPLATE_WITH_ANSWERS = "with_answers"

        val TEMPLATE_IDS = setOf(
            TEMPLATE_PRACTICE_SHEET,
            TEMPLATE_COMPACT,
            TEMPLATE_WITH_ANSWERS,
        )

        const val MIN_MARGIN_PT = 24
        const val MAX_MARGIN_PT = 72
        const val DEFAULT_MARGIN_PT = 48

        const val MIN_FONT_SCALE = 1
        const val MAX_FONT_SCALE = 3
        const val DEFAULT_FONT_SCALE = 2

        const val DEFAULT_COLUMN_COUNT = 1
        val SUPPORTED_COLUMN_COUNTS = setOf(1, 2)

        const val MIN_IMAGE_SCALE = 0.5f
        const val MAX_IMAGE_SCALE = 1.0f
        const val DEFAULT_IMAGE_SCALE = 1.0f

        /** 参数化之前的固定版式 = compact + 全常数。 */
        val DEFAULT = MistakePdfLayout()
    }
}
