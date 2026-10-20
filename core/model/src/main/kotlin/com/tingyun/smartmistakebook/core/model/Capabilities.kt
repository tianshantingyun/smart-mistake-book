package com.tingyun.smartmistakebook.core.model

enum class NetworkMode {
    STRICT_OFFLINE,
    LOCAL_FIRST,
}

/** A point-in-time view of capabilities that the current build can honestly offer. */
data class AppCapabilitySnapshot(
    val networkMode: NetworkMode,
    val cameraCaptureAvailable: Boolean,
    val trustedOcrAvailable: Boolean,
    val tutorTeachingEnabled: Boolean,
    val remoteModelConfigured: Boolean,
    val remoteModelCapabilitiesTested: Boolean = false,
    val remoteModelImageInputVerified: Boolean = false,
    val remoteModelStructuredOutputVerified: Boolean = false,
    /** 上次能力测试是鉴权失败：文案要提示"API Key 可能失效"而不是"模型不兼容"。 */
    val remoteModelAuthenticationFailed: Boolean = false,
) {
    val networkRequestsAllowed: Boolean
        get() = networkMode == NetworkMode.LOCAL_FIRST

    val remoteModelAvailable: Boolean
        get() = networkRequestsAllowed && remoteModelConfigured &&
            remoteModelStructuredOutputVerified
}

/**
 * 一轮 tutor 调用的**能力快照**（PRD-06）：模型这一轮真正能看见、能选、能被执行的东西。
 *
 * 它是三份事实的交集，且**只有这一份**：
 * 1. **已实现处理器**——[IMPLEMENTED_TOOLS] / [IMPLEMENTED_LOCAL_ACTIONS]。没有执行路由的
 *    动作不进快照（`ADD_TO_REVIEW_PLAN` 落 `NOT_WIRED_YET`，接线前不广告、也不准入）；
 * 2. **配置/依赖就绪**——由 [withDependencies] 把图像后端与知识材料的就绪位折进工具集：
 *    没就绪的工具既不广告、也不会被授权执行（"广告一个必然不可用的工具"与"点了一样的
 *    没反应"同罪）；
 * 3. **本轮声明/权限**——[forRound] 只认本轮输入里的声明集；准入与执行再分别与
 *    `TutorRoundPermissionContext` 相交。模型自己伪造的、未广告的请求到不了执行端。
 *
 * 模型就绪不单列一位：**这一轮请求存在**本身就是"模型已配置"的事实；没有模型时根本
 * 没有派遣，也就没有可广告的面。
 *
 * 消费者（native schema / Route B 提示词 / 准入 / 执行端）都从 [forRound] 或
 * [IMPLEMENTED_LOCAL_ACTIONS] 读，禁止各自抄一份名单。
 */
data class TutorCapabilitySnapshot(
    /** 工具面的广告集。顺序保留本轮声明集顺序，wire 稳定。 */
    val tools: Set<TutorToolName> = emptySet(),
    /** 本轮的广告面（本地动作）。只含 [IMPLEMENTED_LOCAL_ACTIONS]，且只在大厅轮非空。 */
    val localActions: Set<TutorLocalAction> = emptySet(),
) {
    init {
        require(tools.all { tool -> tool in IMPLEMENTED_TOOLS }) {
            "A capability snapshot must not advertise a tool without an implemented handler"
        }
        require(localActions.all { action -> action in IMPLEMENTED_LOCAL_ACTIONS }) {
            "A capability snapshot must not advertise a local action without an implemented handler"
        }
    }

    fun advertises(tool: TutorToolName): Boolean = tool in tools

    fun advertises(action: TutorLocalAction): Boolean = action in localActions

    /**
     * 折进**配置/依赖就绪**：图像后端不就绪时不广告 `GENERATE_FIGURE`；知识材料不就绪时
     * 不广告 `KNOWLEDGE_READ`。两个就绪位都来自本机装配态，模型无法影响。
     */
    fun withDependencies(
        imageBackendReady: Boolean,
        knowledgeMaterialReady: Boolean,
    ): TutorCapabilitySnapshot = copy(
        tools = tools.filterNot { tool ->
            (tool == TutorToolName.GENERATE_FIGURE && !imageBackendReady) ||
                (tool == TutorToolName.KNOWLEDGE_READ && !knowledgeMaterialReady)
        }.toSet(),
    )

    companion object {
        /**
         * 已经有**真实执行处理器**的工具（当前八枚全部有执行分支）。
         *
         * 刻意**逐枚列出**而不是 `entries.toSet()`：新增一枚枚举而忘了执行分支时，它默认
         * 不在广告面里（fail-closed），而不是自动被广告、等模型调用后回一句"不可用"。
         * `core:domain` 的 `TutorToolGateTest` 钉住它与页面声明面是同一组工具。
         */
        val IMPLEMENTED_TOOLS: Set<TutorToolName> = linkedSetOf(
            TutorToolName.KNOWLEDGE_READ,
            TutorToolName.NOTEBOOK_READ,
            TutorToolName.MASTERY_READ,
            TutorToolName.MASTERY_UPDATE,
            TutorToolName.NOTEBOOK_WRITE,
            TutorToolName.ADVISORY_READ,
            TutorToolName.ADVISORY_WRITE,
            TutorToolName.GENERATE_FIGURE,
        )

        /**
         * 已经有**真实执行路由**的本地动作。`ADD_TO_REVIEW_PLAN` 不在其中：它执行时落
         * `TutorLocalActionTarget.NOT_WIRED_YET`，接线前不广告、不准入；升级前落库的旧卡
         * 仍由执行端如实说"还不能自动做"（旧卡不是广告面）。
         */
        val IMPLEMENTED_LOCAL_ACTIONS: Set<TutorLocalAction> = setOf(
            TutorLocalAction.OPEN_PROBLEM,
            TutorLocalAction.SAVE_TO_NOTEBOOK,
            TutorLocalAction.START_EXPORT,
        )

        /**
         * 本轮输入 → 快照。工具面读本轮声明集（生成方已按就绪位裁过，见
         * `RoomModelTaskRepository.toolDeclarationsFor`）；本地动作只在**有确认卡承接面**的
         * 大厅轮出现——讲题/计划轮没有卡的生产者，广告了就是广告一个没人接的东西。
         */
        fun forRound(input: ModelTaskInput): TutorCapabilitySnapshot {
            val declaredTools = when (input) {
                is TutorLobbyInput -> input.toolDeclarations
                is TutorRespondInput -> input.toolDeclarations
                is TutorPlanInput -> input.toolDeclarations
                else -> emptyList()
            }
            return TutorCapabilitySnapshot(
                tools = declaredTools.filterTo(linkedSetOf()) { tool ->
                    tool in IMPLEMENTED_TOOLS
                },
                localActions = if (input is TutorLobbyInput) {
                    IMPLEMENTED_LOCAL_ACTIONS
                } else {
                    emptySet()
                },
            )
        }
    }
}
