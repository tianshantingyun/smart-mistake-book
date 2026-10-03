package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A bounded free-text entry on the Tutor home screen.
 *
 * It deliberately carries no question document, learning ledger, or write authority. The model
 * may understand the message and request one of two local reads, but local policy remains the
 * authority and free text never becomes learning evidence.
 */
@Serializable
@SerialName("tutor_lobby")
data class TutorLobbyInput(
    val conversationId: String,
    val messageOrdinal: Int,
    val studentMessage: String,
    val priorMessages: List<TutorChatHistoryEntry> = emptyList(),
    /**
     * 更早轮次的确定性摘要（超出预算被挤出原样窗口的那些轮）：让模型知道前面聊过什么、
     * 已经给过什么结论，而不是让它们无声消失。为空表示没有轮次被挤出。
     */
    val priorDigest: String? = null,
    /** 本条消息附带的图片（学生主动选择，最多 9 张）；空表示纯文字。 */
    val sourceImageAssetRefs: List<CaptureSourceAssetRef> = emptyList(),
    /**
     * 上文图片：本会话里学生此前发过的图片（取最近一次带图消息的那几张）。
     *
     * 图片仍属于那条历史消息，只是随本次发送一并出网。它存在的理由是一条实测失败：学生先
     * 发一张题图问"解一下这个题吧"，再追问"第三题"时上下文里只剩文字，模型自己在回答里写
     * 「这道题的题面细节我这边看不到」——追问全部落空。
     */
    val contextImageAssetRefs: List<CaptureSourceAssetRef> = emptyList(),
    /** Non-empty enables the tool protocol for this dispatch (spec §3.1). */
    val toolDeclarations: List<TutorToolName> = emptyList(),
    /** Results of prior tool rounds; round 1 dispatch always leaves this empty. */
    val toolRoundResults: List<TutorToolRoundResult> = emptyList(),
    /**
     * 上一轮本地动作（确认卡）的裁决结果（A4 回喂，D-K2e）：本地动作 ≈ 工具的一种，执行结果
     * 照常回喂模型上下文——否则模型下一轮会重新请求同一件事，或者以为学生点了。
     *
     * **空载体必须抹平**（`bf8be888` 教训）：空列表在指纹里不落键，否则升级读回旧行即崩。
     */
    val localActionOutcomes: List<TutorLobbyLocalActionOutcome> = emptyList(),
    /**
     * 本轮的交互模式（D-Q9）：正常 = 有问即答，引导 = 按脚手架刻度给。
     *
     * 由**会话行**决定（[TutorInteractionMode.AREA_DEFAULT_NAME] 是智能体栏的默认，复习栏默认
     * 引导属阶段 5），学生在会话里可以切换；本地把它连同 [scaffoldLevel] 一起送进提示词。
     * 非空载体（枚举的默认值就是正常模式），但**旧行读回必须按"当年没有这个键"重算指纹**
     * （见 `ModelTaskRequest.TUTOR_INTERACTION_MODE_SCHEMA_VERSION`）。
     */
    val interactionMode: TutorInteractionMode = TutorInteractionMode.NORMAL,
    /**
     * 本轮起步的脚手架档（引导模式才有；**本地纯策略算出来的**，不是模型填的）：
     * 卡点自动升档的结论（[tutorScaffoldDirective]）。null = 无阶梯（正常模式，或引导模式下
     * 还没算出起步档）——空载体不落键、不渲染（`bf8be888` 教训）。
     */
    val scaffoldLevel: TutorScaffoldLevel? = null,
    /**
     * 本**逻辑操作**里模型已经提出过的本地动作（白名单），最近一轮的在后：
     * 它们在等学生点确认卡，**不是已经完成的事实**（提示词据此要求模型不要重复提出）。
     *
     * 为什么它要在输入里（而不只在输出里）：原生 tool_calls 路由的模型把动作表达成一次
     * 函数调用，那一轮的 `content` 是空的——动作请求唯一可靠的落点就是"下一轮输入"。
     * 大厅 Route B 的信封路由则直接落在 [TutorLobbyOutput.localActions]；两条路由最后都到
     * 本地同一个挂卡入口（`TutorLocalActionDispatcher`）。
     *
     * **空载体必须抹平**（`bf8be888`）：空列表在指纹路径里不落键。
     */
    val requestedLocalActions: List<TutorLocalActionRequest> = emptyList(),
) : ModelTaskInput {
    override val kind: ModelTaskKind
        get() = ModelTaskKind.TUTOR_LOBBY

    override val subjectId: String
        get() = conversationId

    /** 消息带图时要求 provider 具备图片输入能力（Lobby 不在 agent-eligible 集合内，此位只驱动能力门）。 */
    override val requestsImageBytes: Boolean
        get() = sourceImageAssetRefs.isNotEmpty() || contextImageAssetRefs.isNotEmpty()

    init {
        conversationId.requireSafeModelText(
            "Tutor lobby conversation id",
            ModelTaskRequest.MAX_ID_CHARS,
            false,
        )
        require(messageOrdinal > 0) { "Tutor lobby message ordinal must be positive" }
        studentMessage.requireSafeModelText(
            "Tutor lobby student message",
            TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS,
            true,
        )
        require(sourceImageAssetRefs.size <= ModelEgressManifest.MAX_LOBBY_IMAGE_ASSETS) {
            "Tutor lobby message carries too many images"
        }
        require(sourceImageAssetRefs.map { it.assetId }.distinct().size == sourceImageAssetRefs.size) {
            "Tutor lobby message image ids must be unique"
        }
        require(
            sourceImageAssetRefs.map { it.pageIndex } == sourceImageAssetRefs.indices.toList(),
        ) {
            "Tutor lobby message image order must be contiguous from zero"
        }
        require(contextImageAssetRefs.size <= ModelEgressManifest.MAX_LOBBY_IMAGE_ASSETS) {
            "Tutor lobby context carries too many images"
        }
        require(contextImageAssetRefs.map { it.assetId }.distinct().size == contextImageAssetRefs.size) {
            "Tutor lobby context image ids must be unique"
        }
        require(
            contextImageAssetRefs.map { it.pageIndex } == contextImageAssetRefs.indices.toList(),
        ) {
            "Tutor lobby context image order must be contiguous from zero"
        }
        require(
            sourceImageAssetRefs.map { it.assetId }
                .intersect(contextImageAssetRefs.map { it.assetId }.toSet())
                .isEmpty(),
        ) {
            "Tutor lobby must not disclose the same image twice in one message"
        }
        require(priorMessages.size <= TutorRespondInput.MAX_PRIOR_MESSAGES) {
            "Tutor lobby contains too many prior messages"
        }
        require(
            priorMessages.sumOf { message ->
                message.studentMessage.length + message.assistantMarkdown.length
            } <= TutorRespondInput.MAX_PRIOR_MESSAGE_CHARS,
        ) { "Tutor lobby prior messages exceed their text budget" }
        priorDigest?.requireSafeModelText(
            "Tutor lobby prior digest",
            TutorRespondInput.MAX_PRIOR_DIGEST_CHARS,
            true,
        )
        require(toolDeclarations.size <= MAX_TOOL_DECLARATIONS) {
            "Tutor lobby declares too many tools"
        }
        require(toolDeclarations.distinct().size == toolDeclarations.size) {
            "Tutor lobby tool declarations must be distinct"
        }
        require(toolRoundResults.size <= TutorToolRoundResult.MAX_TOOL_ROUNDS) {
            "Tutor lobby carries too many tool rounds"
        }
        require(toolRoundResults.isEmpty() || toolDeclarations.isNotEmpty()) {
            "Tutor lobby tool rounds require declared tools"
        }
        require(
            toolRoundResults.map(TutorToolRoundResult::roundOrdinal) ==
                (1..toolRoundResults.size).toList(),
        ) {
            "Tutor lobby tool round ordinals must be sequential from one"
        }
        require(localActionOutcomes.size <= MAX_LOCAL_ACTION_OUTCOMES) {
            "Tutor lobby carries too many local action outcomes"
        }
        require(requestedLocalActions.size <= MAX_REQUESTED_LOCAL_ACTIONS) {
            "Tutor lobby carries too many requested local actions"
        }
        require(requestedLocalActions.distinct().size == requestedLocalActions.size) {
            "Tutor lobby must not repeat a requested local action"
        }
        require(
            interactionMode == TutorInteractionMode.GUIDED || scaffoldLevel == null,
        ) {
            "A normal-mode lobby round carries no scaffold level"
        }
    }

    companion object {
        const val MAX_STUDENT_MESSAGE_CHARS = TutorRespondInput.MAX_STUDENT_MESSAGE_CHARS
        const val MAX_PRIOR_MESSAGES = TutorRespondInput.MAX_PRIOR_MESSAGES
        const val MAX_PRIOR_MESSAGE_CHARS = TutorRespondInput.MAX_PRIOR_MESSAGE_CHARS

        /**
         * 一次回喂带几条裁决结果：确认卡是给学生看的**一张**卡，回喂的是"上一轮那几张"，
         * 不是可翻页的历史（与 `AgentPendingRequestRepository.MAX_RESOLVED_PENDING_REQUESTS` 同量级）。
         */
        const val MAX_LOCAL_ACTION_OUTCOMES = 8

        /**
         * 一次派发里最多带几条"已提出、在等确认"的本地动作。与
         * [MAX_LOCAL_ACTION_REQUESTS_PER_ROUND] 同量级再留一点余量：工具环最多 5 轮，每轮最多
         * 两个动作，但同一个动作重复提出会被去重、且模型没有理由每轮都提。
         */
        const val MAX_REQUESTED_LOCAL_ACTIONS = 4
    }
}

/**
 * 一条本地动作的裁决结果（回喂模型的那一行）。
 *
 * [kind] / [decision] 存的是**枚举名**（`AgentPendingRequestKind` / `AgentPendingRequestStatus`）：
 * 落在输入里的是稳定字符串，`core:model` 不反向依赖 `core:domain` 的枚举类型。
 * [detail] 是**留痕里那一句话**（`agent_pending_request.resolution_note`）：它既是学生看到的
 * 结果/被拒理由，也是模型读到的"本地到底做成了没有"——一份文本，不写两遍，也不额外加一个
 * 需要与它保持一致的布尔位。
 */
@Serializable
data class TutorLobbyLocalActionOutcome(
    val kind: String,
    val decision: String,
    val detail: String? = null,
) {
    init {
        require(kind.isNotBlank()) { "A local action outcome needs a kind" }
        require(decision.isNotBlank()) { "A local action outcome needs a decision" }
        require(detail == null || detail.isNotBlank()) {
            "A local action outcome detail must be null or non-blank"
        }
    }
}

/** A persistable response that cannot claim or request a local write. */
@Serializable
@SerialName("tutor_lobby_output")
data class TutorLobbyOutput(
    val conversationId: String,
    val messageOrdinal: Int,
    val messageMarkdown: String,
    val intentDecision: TutorIntentDecision = TutorIntentDecision.ambiguousDefault(),
    /** Optional student-visible reasoning trace; folded by default, never re-fed to the model. */
    val thinkingMarkdown: String? = null,
    /**
     * **遗留解码载体，不再由任何解析器写入**（A6）：大厅的 `attachedImages` 是死分支
     * （解析后 `TutorLobbyRoute` 零引用、从不渲染），wire 键已从
     * `TUTOR_LOBBY_WIRE_KEYS` 移除——模型再吐这个键按未知键整条拒。
     *
     * 字段本身保留，是为了**旧行仍可读**：历史持久化的输出行里带着这个键（编码
     * `encodeDefaults=true` 使空列表也落键，且旧解析器确实接受过非空值），而
     * `ModelTaskCodec` 的 `ignoreUnknownKeys=false` 会让删掉字段后的旧行直接抛。
     * 删字段需要一套对非空数组也稳健的 JSON 手术，消灭的失败与"保留字段"完全相同，
     * 所以保留它并在此说明，而不是加一层多余机制。
     */
    val attachedImages: List<AttachedImage> = emptyList(),
    /**
     * 模型这一轮申请的本地动作（规格 §3.2 白名单；D-K2e）。**模型的输出**，不是本地事实：
     * 每一项都要过本地的权限档（ask → 确认卡）与目标解析（本轮有没有可执行的东西）才会
     * 变成一张卡；模型**不得**在正文里声称已经做过（提示词明说，见
     * [TutorLocalAction.purposeDescription]）。
     *
     * 排序即模型的提出顺序（本地按顺序挂卡，学生按顺序看到）。空列表不落键（`bf8be888`）。
     */
    val localActions: List<TutorLocalActionRequest> = emptyList(),
    val modelVersion: String,
) : ModelTaskOutput {
    init {
        conversationId.requireSafeModelText(
            "Tutor lobby output conversation id",
            ModelTaskRequest.MAX_ID_CHARS,
            false,
        )
        require(messageOrdinal > 0) { "Tutor lobby output message ordinal must be positive" }
        messageMarkdown.requireTutorSceneText(
            "Tutor lobby response",
            TutorRespondOutput.MAX_MESSAGE_MARKDOWN_CHARS,
            true,
        )
        thinkingMarkdown.requireThinkingMarkdown("Tutor thinking")
        require(attachedImages.size <= AttachedImage.MAX_ATTACHED_IMAGES) {
            "A tutor lobby reply may attach at most ${AttachedImage.MAX_ATTACHED_IMAGES} figures"
        }
        require(localActions.size <= MAX_LOCAL_ACTION_REQUESTS_PER_ROUND) {
            "A tutor lobby reply may request at most $MAX_LOCAL_ACTION_REQUESTS_PER_ROUND local actions"
        }
        require(localActions.distinct().size == localActions.size) {
            "A tutor lobby reply must not request the same local action twice"
        }
        require(intentDecision.requestedLocalCapability in ALLOWED_LOCAL_CAPABILITIES) {
            "Tutor lobby cannot request a local write or current-question action"
        }
        modelVersion.requireSafeModelText(
            "Tutor lobby model version",
            MAX_MODEL_VERSION_CHARS,
            false,
        )
    }

    companion object {
        /**
         * Lobby 允许申请的本地能力，只有两条：什么都不申请，或检索错题本。
         *
         * **掌握情况读取被刻意排除**（`READ_LEARNING_PROGRESS`）：它是"某个知识点你
         * 掌握得怎样"，没有当前题就没有锚点；而且它的产出无法归入
         * `TUTOR_LOBBY_DISCLOSURE`（仅学生消息 + 会话上下文），任其进入就要放宽这条
         * 通道的披露面。掌握情况读取因此只保留在有题上下文的地方——讲题会话的
         * `MASTERY_READ` 工具。
         *
         * 这条枚举此前与 Lobby 提示词**互相矛盾**（枚举允许、提示词却写"本地不提供该
         * 查询"），而没有测试会因此变红；`TutorLobbyCapabilityBoundaryTest` 现在锁住
         * 它与提示词的一致性。
         */
        val ALLOWED_LOCAL_CAPABILITIES = setOf(
            TutorRequestedLocalCapability.NONE,
            TutorRequestedLocalCapability.READ_MISTAKE_NOTEBOOK,
        )
    }
}
