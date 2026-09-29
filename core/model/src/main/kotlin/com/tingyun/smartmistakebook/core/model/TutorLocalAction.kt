package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.Serializable

/**
 * 本地动作白名单第一版（规格 `docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md`
 * §3.2；D-K2e 白名单表已收下）。
 *
 * 模型**只能选不能造**：动作的 ID 与参数形状都由本地定义，模型只从这份名单里挑一个。
 * 名单之外的动作在协议里根本没有可表达的形状——不是"服务端会拒绝"。
 *
 * 这份名单消灭的具体失败：改前"它能给你东西"的出口是 `TutorLocalIntentPanel`，而它的可见性
 * 取决于模型本轮的 `intentDecision`——基础出口被模型输出绑架（事实基线研究报告 §4.1#2）。
 * 动作请求改为模型的**输出**、执行永远等学生点卡（确认卡）之后，名单就是"卡上可能出现什么"
 * 的唯一定点。
 *
 * ## 一处声明的两套广告（同源纪律）
 *
 * 这份名单同时喂**两套协议广告**：Route A 的严格 function schema
 * （`OpenAiModelProtocol.localActionSchemas`：name/description/parameters 全部从这里出）与
 * Route B 的提示词文本块（`OpenAiModelTaskAdapters.localActionPromptBlock`：同样是这里的
 * 名字、用途与参数）。此前工具面的文案漂过三次（`TutorToolDescriptions` 的注释记着那次
 * 事故），本地动作从第一天起就只有这一份：[purposeDescription] / [nativePurposeDescription]
 * 是唯一出处，[TutorLocalActionAdvertisementsTest] 把两套广告与这份声明逐项对拍。
 *
 * ## 参数形状为什么都是空的
 *
 * 四个动作都**不接受模型给的参数**——不是"本版先不放"，而是白名单本身决定的：动作的执行目标
 * （哪份拍照草稿、哪张附图、哪道库里的题、本轮在讲的那道题）只能由**本轮本地上下文**解析
 * （见 `core:domain` 的 `TutorLocalActionContext`），模型手里没有任何可核对的本地 id。
 * 让模型填一个本地核不了的 id，就是把"它选了哪件事"退化成"它编了哪件事"。
 * 形状仍然要声明，因为**形状是拒绝的依据**：解析器按 [TutorLocalAction] 逐字核对键集，
 * 出现名单里没有的键就是无效输出（模型造了参数 = 这条输出不可用，不是"忽略一下"）。
 *
 * 权限档在 `core:domain` 的 `TutorPermissionPolicy`：这四个动作全部 **ask（确认卡）**。
 * 重拆提议（`PROPOSE_RECLASSIFY`）**不在本版**：D-K2/K2b 与 D-K3 把它整体推迟到阶段 3B，
 * 它的档位由重拆 hook 判据决定（被拦 → ask），届时按同一机制接入。
 */
enum class TutorLocalAction {
    /** 打开某题（跳错题本详情）；本轮没有具体题时打开错题本本身。 */
    OPEN_PROBLEM,

    /**
     * 当前题加入错题本 = `NOTEBOOK_WRITE` 的 ask 档（D-K2e 白名单表）：
     * 同一件事的两种协议拼写——工具拼写（模型在工具面里选）与动作拼写（模型在本地动作通道里选）。
     */
    SAVE_TO_NOTEBOOK,

    /** 发起选择导出（检索 → 勾选 → 本地排版 → 导出可打印文件）。插眼 5 点名的高风险流程。 */
    START_EXPORT,

    /** 提出复习计划调整（把某题纳入计划）：模型提出行为，请求学生同意。 */
    ADD_TO_REVIEW_PLAN,
    ;

    /**
     * 提示词侧的长文案（Route B）——也是这套动作在**模型眼里**的语义边界（写给学生看的
     * 那一句在确认卡上，两者不是同一份文本）。
     */
    val purposeDescription: String
        get() = when (this) {
            OPEN_PROBLEM ->
                "打开本轮的那道题（跳到错题本详情）；本轮没有具体题时打开错题本本身。" +
                    "学生点确认之后才会打开，不得声称已经打开。"
            SAVE_TO_NOTEBOOK ->
                "把本轮的内容（附上的图片、这一轮正在处理的题）收进错题本。冒号后面不接任何" +
                    "自造内容；本地会挂一张确认卡，学生点了才会真正保存，不得声称已经存好。"
            START_EXPORT ->
                "发起一次选择导出（先检索、再由学生勾选、本地排版、导出可打印文件）。" +
                    "本地会请学生确认，不得声称文件已经生成。"
            ADD_TO_REVIEW_PLAN ->
                "提出一次复习计划调整（例如把本轮这道题纳入复习计划）。这是**提议**：" +
                    "本地会把这件事交给学生确认，不得声称计划已经改好。"
        }

    /** 原生 function schema 的短描述（与 [purposeDescription] 同一份声明、措辞收紧）。 */
    val nativePurposeDescription: String
        get() = when (this) {
            OPEN_PROBLEM -> "打开本轮的那道题（需学生确认后才会打开）"
            SAVE_TO_NOTEBOOK -> "把本轮内容收进错题本（需学生确认后才保存）"
            START_EXPORT -> "发起一次选择导出（需学生确认）"
            ADD_TO_REVIEW_PLAN -> "提出把本轮这道题纳入复习计划（需学生确认）"
        }

    /**
     * 这个动作的参数形状：**空集**（见类注释）。声明成一个属性而不是写死在各处，
     * 是为了让两套广告与解析器读同一个空集——真有一天要放开某个参数，改这里一处、
     * 三处消费者（schema / 提示词 / 解析器）同时跟上。
     */
    val parameters: List<TutorLocalActionParameter>
        get() = emptyList()

    /** 动作 id（两套广告与解析器都用它，不做任何大小写/别名容忍）。 */
    val actionId: String get() = name

    companion object {
        /**
         * 解析模型给的动作 id：**只认逐字相同的名单内 id**，大小写/空白/别名一律无效。
         * 返回 null = 这条动作请求不是白名单里的东西（调用方必须丢弃，不得当成"未知动作"放行）。
         */
        fun fromActionId(actionId: String): TutorLocalAction? =
            entries.firstOrNull { action -> action.name == actionId }
    }
}

/**
 * 动作参数的一项声明。
 *
 * 本版四个动作的参数形状都是空集（见 [TutorLocalAction]），所以这个类型目前**没有实例**；
 * 它随"第一个真的能核对的参数"一起出现——那时 Route A 的 schema 生成、Route B 的文案与
 * 解析器的键集核对都从这里读，而不是各自抄一份。
 */
data class TutorLocalActionParameter(
    val parameterName: String,
    /** 写给模型看的一句（同时进 Route A 的 property description 与 Route B 的文本）。 */
    val description: String,
    /** true = 缺了它这条请求不成立；false = 可省（省了表示"按本轮上下文默认"）。 */
    val required: Boolean = false,
) {
    init {
        require(parameterName.isNotBlank()) { "A local action parameter needs a name" }
        require(description.isNotBlank()) { "A local action parameter needs a description" }
    }
}

/**
 * 模型的一条本地动作请求（**模型的输出**，不是本地事实）。
 *
 * 它只带一个动作 id 与一组参数——[parameters] 的键必须在 [TutorLocalAction.parameters]
 * 声明过（解析器负责核对，见 `OpenAiModelResponseParsers.toTutorLocalActions`）。
 * 目标（哪道题、哪份草稿）不在这里：那是本地上下文的事。
 */
@Serializable
data class TutorLocalActionRequest(
    val action: TutorLocalAction,
    /**
     * 动作参数。本版恒为空（四个动作的形状都是空集）——**空载体抹平**：空 map 不落键、
     * 不进指纹，也不渲染（`bf8be888` 教训）。
     */
    val parameters: Map<String, String> = emptyMap(),
) {
    init {
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
 * **就是 1**：一轮只挂一张卡。这不是节制，是相位机的事实——`TutorTurnSendStateMachine` 的
 * `AWAITING_CONSENT` 是**回合级**的单一相位，一条回合不可能同时等两张卡的确认；广告两个而本地
 * 只能接一个，就是把"提了没反应"写进契约。学生点了这张卡之后的那一轮，模型可以再提下一件事。
 */
const val MAX_LOCAL_ACTION_REQUESTS_PER_ROUND = 1

/** 本地动作通道在请求/输出里的键名（Route B 信封与解析器共用一处）。 */
const val TUTOR_LOCAL_ACTIONS_WIRE_KEY = "localActions"
