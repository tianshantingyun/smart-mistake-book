package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/**
 * 应用动作的统一声明，同时生成原生函数 schema 与 Route B 提示词。
 * 已接通的动作默认自主执行，真实回执进入当前模型回合。
 * entryId / entryIds 只能选择本轮本地检索或保存所得的条目。
 */
enum class TutorLocalAction {
    /** 打开某题（跳错题本详情）；本轮没有具体题时打开错题本本身。 */
    OPEN_PROBLEM,

    /** 保存本轮题目，附图进入既有录入链路。 */
    SAVE_TO_NOTEBOOK,

    /** 使用本轮选定条目和版式生成本地导出文件。 */
    START_EXPORT,

    /** 将本轮真实题目写入复习计划，旧确认卡的历史载荷仍可读。 */
    ADD_TO_REVIEW_PLAN,
    /** 把本轮已知题目从今天的复习计划中移除（只在计划未开始/未完成时可改）。 */
    REMOVE_FROM_REVIEW_PLAN,
    EDIT_PROBLEM,
    SET_FAVORITE,
    ARCHIVE_PROBLEM,
    RESTORE_PROBLEM,
    UPDATE_SCHEDULING,
    CREATE_BACKUP,
    RESTORE_BACKUP,
    /** 往考试日历写一场考试（科目/日期/名称），与调度设置同一存储。 */
    ADD_EXAM,
    /** 按考试条目 id 移除一场考试。 */
    REMOVE_EXAM,
    /** 把本轮已知题目的知识点关联改成本轮从知识目录读到的节点。 */
    RECLASSIFY_PROBLEM,
    /** 把本轮已知的重复题目合并到当前题（保留当前题，重复项归档并留下关联）。 */
    MERGE_PROBLEM,
    ;

    /**
     * 提示词侧的长文案（Route B）——也是这套动作在**模型眼里**的语义边界（写给学生看的
     * 那一句在确认卡上，两者不是同一份文本）。
     */
    val purposeDescription: String
        get() = when (this) {
            OPEN_PROBLEM ->
                "打开本轮的那道题（跳到错题本详情）；本轮没有具体题时打开错题本本身。" +
                    "本地自动执行，并回传实际打开结果。"
            SAVE_TO_NOTEBOOK ->
                "保存本轮真实题目。拍照草稿加入错题本，聊天附图进入录入队列；" +
                    "本地自动执行，按实际结果说明保存或录入进度。"
            START_EXPORT ->
                "导出本轮检索到的题目（先检索、本地排版、生成可打印文件）。" +
                    "学生的口头排版要求（「两栏」「字大点」「不要答案」）映射到 templateId 与版式参数，" +
                    "没要求就用练习卷默认版式；不要输出任何排版标记。本地自动入队并回传进度，" +
                    "只有成功回执才能说明文件已生成。"
            ADD_TO_REVIEW_PLAN ->
                "把本轮已知题目纳入复习计划，按真实队列写入回执说明结果。"
            REMOVE_FROM_REVIEW_PLAN ->
                "把本轮已知题目从今天的复习计划中移除；计划已开始/已完成时如实拒绝，不假装改过。"
            EDIT_PROBLEM -> "按instruction修改本轮已知题目，走持久化修改作业与版本校验；仅成功提交新版本后说明已改。"
            SET_FAVORITE -> "设置本轮已知题目的收藏状态，favorite=true收藏，false取消收藏。"
            ARCHIVE_PROBLEM -> "归档本轮已知题目，保留题目历史。"
            RESTORE_PROBLEM -> "恢复本轮已知的归档题目。"
            UPDATE_SCHEDULING -> "修改每日复习分钟数或目标保留率，并回传已保存设置和生效时机。"
            CREATE_BACKUP -> "创建本机备份文件并返回真实backupRef，可供后续恢复。"
            RESTORE_BACKUP -> "从本轮已授权备份backupRef恢复，返回实际恢复结果和重启状态。"
            ADD_EXAM ->
                "把一场考试写进考试日历（科目、日期、名称），回传实际保存结果；" +
                    "日期是本地 epoch day（1970-01-01 起的天数）。"
            REMOVE_EXAM -> "按考试条目 id 从考试日历移除一场考试，回传实际移除结果。"
            RECLASSIFY_PROBLEM ->
                "把本轮已知题目的知识点关联改为本轮从知识目录读到的节点" +
                    "（nodeId@taxonomyVersion，逗号分隔）；板块与手动分类保留，" +
                    "只在保存成功后说明已改。"
            MERGE_PROBLEM ->
                "把本轮已知的重复题目合并到当前题：保留当前题，重复条目归档并留下一条" +
                    "重复关联；不删除题/修订/学习证据，归档可恢复。"
        }

    /** 原生 function schema 的短描述（与 [purposeDescription] 同一份声明、措辞收紧）。 */
    val nativePurposeDescription: String
        get() = when (this) {
            OPEN_PROBLEM -> "自动打开本轮的那道题或错题本，回传实际结果"
            SAVE_TO_NOTEBOOK -> "自动保存本轮真实题目或将附图加入录入，回传实际结果"
            START_EXPORT -> "自动导出本轮检索到的题目，可指定模板与版式，回传生成进度"
            ADD_TO_REVIEW_PLAN, REMOVE_FROM_REVIEW_PLAN,
            EDIT_PROBLEM, SET_FAVORITE, ARCHIVE_PROBLEM,
            RESTORE_PROBLEM, UPDATE_SCHEDULING, CREATE_BACKUP, RESTORE_BACKUP,
            ADD_EXAM, REMOVE_EXAM, RECLASSIFY_PROBLEM, MERGE_PROBLEM -> purposeDescription
        }

    /** 两条协议与本地解析器共享参数形状，条目选择在执行时核对本轮已知结果。 */
    val parameters: List<TutorLocalActionParameter>
        get() = when (this) {
            START_EXPORT -> listOf(
                TutorLocalActionParameter(
                    parameterName = "entryIds",
                    description = "要导出的条目 id，逗号分隔；只能选本轮已检索或刚保存成功的条目。省略时导出本轮检索结果。",
                ),
                TutorLocalActionParameter(
                    parameterName = "format",
                    description = "交付格式：pdf=PDF（默认）、docx=Word 文档；学生说「Word」时填 docx。",
                    allowedValues = listOf("pdf", "docx"),
                ),
                TutorLocalActionParameter(
                    parameterName = "templateId",
                    description = "导出模板。practice_sheet=练习卷（含作答空白区）、" +
                        "compact=紧凑版（只做题面）、with_answers=答案卷（附答案区）。" +
                        "学生没指定时用 practice_sheet。",
                    required = true,
                    allowedValues = listOf(
                        MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
                        MistakePdfLayout.TEMPLATE_COMPACT,
                        MistakePdfLayout.TEMPLATE_WITH_ANSWERS,
                    ),
                ),
                TutorLocalActionParameter(
                    parameterName = "columnCount",
                    description = "栏数：1=单栏，2=双栏（学生说「两栏」时填 2）。",
                    allowedValues = listOf("1", "2"),
                ),
                TutorLocalActionParameter(
                    parameterName = "fontScale",
                    description = "字号档 1/2/3（学生说「字大点」填 3，「字小点」填 1）。",
                    allowedValues = listOf("1", "2", "3"),
                ),
                TutorLocalActionParameter(
                    parameterName = "marginPt",
                    description = "页边距（pt）：${MistakePdfLayout.MIN_MARGIN_PT}.." +
                        "${MistakePdfLayout.MAX_MARGIN_PT}，默认 ${MistakePdfLayout.DEFAULT_MARGIN_PT}。",
                ),
                TutorLocalActionParameter(
                    parameterName = "imageScale",
                    description = "图形缩放：0.5..1.0，默认 1.0（学生说「图小一点」时调小）。",
                ),
                TutorLocalActionParameter(
                    parameterName = "blockOrder",
                    description = "题面块顺序：逗号分隔的 " +
                        "paragraph/section_heading/formula/choice_group/figure 子集（可省略）。",
                ),
                TutorLocalActionParameter(
                    parameterName = "includeAnswer",
                    description = "是否附答案区：true/false（学生说「不要答案」时 false，默认 false）。",
                    allowedValues = listOf("true", "false"),
                ),
                TutorLocalActionParameter(
                    parameterName = "includeSolution",
                    description = "是否附解析区：true/false；当前版本没有解析数据源，" +
                        "打开也会被跳过并如实说明（默认 false）。",
                    allowedValues = listOf("true", "false"),
                ),
                TutorLocalActionParameter(
                    parameterName = "includeNote",
                    description = "是否附「我的备注」区：true/false（默认 false）。",
                    allowedValues = listOf("true", "false"),
                ),
            )
            OPEN_PROBLEM -> listOf(
                TutorLocalActionParameter(
                    parameterName = "entryId",
                    description = "要打开的条目 id，只能选本轮已检索或刚保存成功的条目。省略时打开本轮唯一条目或错题本。",
                ),
            )
            ADD_TO_REVIEW_PLAN, REMOVE_FROM_REVIEW_PLAN, ARCHIVE_PROBLEM, RESTORE_PROBLEM ->
                listOf(entryParameter())
            SET_FAVORITE -> listOf(entryParameter(), TutorLocalActionParameter(
                "favorite", "是否收藏", required = true, allowedValues = listOf("true", "false"),
            ))
            EDIT_PROBLEM -> listOf(entryParameter(), TutorLocalActionParameter(
                "instruction", "对当前真实题面的修改要求", required = true,
            ))
            UPDATE_SCHEDULING -> listOf(
                TutorLocalActionParameter("dailyReviewMinutes", "每日复习分钟数，5..120"),
                TutorLocalActionParameter("desiredRetention", "目标保留率，0.7..0.97"),
            )
            CREATE_BACKUP -> listOf(TutorLocalActionParameter(
                "selectionMask", "备份内容选择，9=题目和图片，13=加学习记录，15=全部", allowedValues = listOf("9", "13", "15"),
            ))
            RESTORE_BACKUP -> listOf(TutorLocalActionParameter(
                "backupRef", "从本轮已授权备份列表选择的备份身份", required = true,
            ))
            ADD_EXAM -> listOf(
                TutorLocalActionParameter("subject", "考试科目（如 数学）", required = true),
                TutorLocalActionParameter(
                    "examEpochDay", "考试日期：本地 epoch day（1970-01-01 起的天数）", required = true,
                ),
                TutorLocalActionParameter("title", "考试名称（如 期中考试）", required = true),
            )
            REMOVE_EXAM -> listOf(TutorLocalActionParameter(
                "examEntryId", "要从考试日历移除的考试条目 id（取自 REVIEW_PLAN_READ 的考试列表）",
                required = true,
            ))
            RECLASSIFY_PROBLEM -> listOf(
                entryParameter(),
                TutorLocalActionParameter(
                    "knowledgeNodes",
                    "新知识点关联：逗号分隔的 nodeId@taxonomyVersion，只能取本轮 " +
                        "KNOWLEDGE_CATALOG_READ 真实返回的值。",
                    required = true,
                ),
            )
            MERGE_PROBLEM -> listOf(TutorLocalActionParameter(
                "duplicateEntryId",
                "要合并掉的重复条目 id（取自本轮错题本检索结果；当前题作为保留项）。",
                required = true,
            ))
            SAVE_TO_NOTEBOOK -> emptyList()
        }

    /** 动作 id（两套广告与解析器都用它，不做任何大小写/别名容忍）。 */
    val actionId: String get() = name

    companion object {
        val APPLICATION_ACTIONS: Set<TutorLocalAction> = setOf(
            ADD_TO_REVIEW_PLAN, REMOVE_FROM_REVIEW_PLAN, EDIT_PROBLEM, SET_FAVORITE, ARCHIVE_PROBLEM,
            RESTORE_PROBLEM, UPDATE_SCHEDULING, CREATE_BACKUP, RESTORE_BACKUP,
            ADD_EXAM, REMOVE_EXAM, RECLASSIFY_PROBLEM, MERGE_PROBLEM,
        )
        val ENTRY_TARGET_ACTIONS: Set<TutorLocalAction> = setOf(
            OPEN_PROBLEM, SAVE_TO_NOTEBOOK, ADD_TO_REVIEW_PLAN, REMOVE_FROM_REVIEW_PLAN,
            EDIT_PROBLEM, SET_FAVORITE, ARCHIVE_PROBLEM, RESTORE_PROBLEM, RECLASSIFY_PROBLEM,
            MERGE_PROBLEM,
        )
        private fun entryParameter() = TutorLocalActionParameter(
            "entryId", "本轮已知的题目条目 id，缺省使用当前题目。",
        )
        /**
         * 解析模型给的动作 id：**只认逐字相同的名单内 id**，大小写/空白/别名一律无效。
         * 返回 null = 这条动作请求不是白名单里的东西（调用方必须丢弃，不得当成"未知动作"放行）。
         */
        fun fromActionId(actionId: String): TutorLocalAction? =
            entries.firstOrNull { action -> action.name == actionId }
    }
}

@Serializable
data class TutorBackupReference(
    val ref: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val sizeBytes: Long,
) {
    init {
        ref.requireSafeModelText("Backup reference", 256, false)
        displayName.requireSafeModelText("Backup display name", 256, false)
        require(createdAtEpochMillis >= 0 && sizeBytes >= 0)
    }
}

/** 原生 schema、提示词与本地解析器共享的动作参数。 */
data class TutorLocalActionParameter(
    val parameterName: String,
    /** 写给模型看的一句（同时进 Route A 的 property description 与 Route B 的文本）。 */
    val description: String,
    /** true = 缺了它这条请求不成立；false = 可省（省了表示"按本轮上下文默认"）。 */
    val required: Boolean = false,
    /**
     * 非空 = 这个参数的取值枚举（Route A 出 `enum` 约束、Route B 文案里列出可选值）。
     * 空 = 自由字符串，语义由本地校验（范围/格式）收口。
     */
    val allowedValues: List<String> = emptyList(),
) {
    init {
        require(parameterName.isNotBlank()) { "A local action parameter needs a name" }
        require(description.isNotBlank()) { "A local action parameter needs a description" }
        require(allowedValues.all(String::isNotBlank)) {
            "A local action parameter enum value must not be blank"
        }
    }
}

/**
 * 模型的一条本地动作请求（**模型的输出**，不是本地事实）。
 *
 * 它只带一个动作 id 与一组参数——[parameters] 的键必须在 [TutorLocalAction.parameters]
 * 声明过（解析器负责核对，见 `OpenAiModelResponseParsers.toTutorLocalActions`）。
 * 目标参数由执行器与本轮本地结果核对，草稿与附图继续取真实上下文。
 */
@Serializable
data class TutorLocalActionRequest(
    val action: TutorLocalAction,
    /** 声明过的目标与版式参数，空 map 沿用旧版本编码。 */
    val parameters: Map<String, String> = emptyMap(),
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val callId: String? = null,
) {
    init {
        callId?.requireSafeModelText("A local action call id", ModelTaskRequest.MAX_ID_CHARS, false)
        require(parameters.keys.all { key -> key.isNotBlank() }) {
            "A local action request parameter name must not be blank"
        }
        require(parameters.values.all { value -> value.isNotBlank() }) {
            "A local action request parameter value must not be blank"
        }
    }

    /** 这条请求的逐字广告（回喂/留痕用）：`OPEN_PROBLEM` 或 `OPEN_PROBLEM(k=v)`。 */
    val requestLabel: String
        get() = if (parameters.isEmpty()) {
            action.actionId
        } else {
            parameters.entries.sortedBy { entry -> entry.key }
                .joinToString(separator = ",", prefix = "(", postfix = ")") { entry ->
                    "${entry.key}=${entry.value}"
                }
                .let { suffix -> action.actionId + suffix }
        }
}

/**
 * 一轮里模型最多能提几个本地动作。
 *
 * 每个动作依次自动执行并回传独立结果，单轮上限保持在四个。
 */
const val MAX_LOCAL_ACTION_REQUESTS_PER_ROUND = 6

/** 本地动作通道在请求/输出里的键名（Route B 信封与解析器共用一处）。 */
const val TUTOR_LOCAL_ACTIONS_WIRE_KEY = "localActions"
