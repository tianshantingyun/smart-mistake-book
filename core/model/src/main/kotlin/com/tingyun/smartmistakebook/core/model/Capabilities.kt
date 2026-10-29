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
 * 2. **配置/依赖就绪**——由 [withDependencies] 把图像后端、JS 双就绪位（A-05：引擎与
 *    度量）与知识材料的就绪位折进工具集：没就绪的工具既不广告、也不会被授权执行
 *    （"广告一个必然不可用的工具"与"点了一样的没反应"同罪）；
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
     * 折进**配置/依赖就绪**：知识材料不就绪时不广告 `KNOWLEDGE_READ`；`GENERATE_FIGURE`
     * 按**方法级**就绪裁（A-05）：图像后端就绪（IMAGE_MODEL 可执行）或 JS 双就绪位同时
     * 成立（[jsEngineReady] 本地执行引擎已装配且未 close ∧ [jsMeasurementReady] 真实度量器
     * 已装配，JS 静态程序能完整走完 prepare→度量→render 事务）时保留，否则不广告。
     * JS runtime ready 不依赖图像后端；反方向同样成立——只有半边就绪的 JS（有引擎无度量/
     * 有度量无引擎）不广告，事务必然在"取引擎"或"prepare 度量批次"一步失败，广告它就是
     * 本快照 KDoc 明文禁止的"广告一个必然不可用的工具"。
     *
     * 失败传播语义（A-05 裁定，R1 §3.3）：**执行期失败不降位**——程序错/熔断/忙是"这一次
     * 的输入或编排"问题，走 coordinator 的 outcome 与有界诊断通道；唯一降位路径是
     * **装配级死亡**（引擎 close、进程死亡未重建、度量器被摘除），由装配方把对应就绪位翻
     * false，下一轮 `toolDeclarationsFor` 重算时 JS 从广告面消失。本快照是每轮重算的纯函数
     * 派生，不持有跨轮健康度状态机——任何降位都必须能回答"装配态哪一位从 1 变 0"。
     *
     * 两个 JS 就绪位缺省 false = 批次 35 之前的生产现状（生产装配值由 L3 引擎装配批接线）；
     * 旧两参调用的行为逐位不变。
     */
    fun withDependencies(
        imageBackendReady: Boolean,
        knowledgeMaterialReady: Boolean,
        jsEngineReady: Boolean = false,
        jsMeasurementReady: Boolean = false,
    ): TutorCapabilitySnapshot = copy(
        tools = tools.filterNot { tool ->
            (tool == TutorToolName.GENERATE_FIGURE &&
                !(imageBackendReady || (jsEngineReady && jsMeasurementReady))) ||
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
