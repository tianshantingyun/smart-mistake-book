package com.tingyun.smartmistakebook.core.data.visual

/**
 * **已持久视觉程序**的①档输入（X-03）。
 *
 * 刻意与 `tutor_visual_program` 行（core:database 的 `TutorVisualProgramEntity`）同形但
 * 独立命名：本类型是判定门的**输入边界**——只有从持久行读出的字段能构造它。它没有
 * "新源码"入口：strictOffline 禁止获取新模型源码（契约 :149），而"生成新 program"必须经
 * 规划模型远程派发——判定门在类型上就拿不到那条路
 * （[com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine] 的源码参数只能由
 * [sourceJs] 即持久行的原文供给）。
 *
 * 版本字段口径与 `tutor_visual_program.api_version/sdk_version` 一致（写侧
 * [TutorVisualProgramInput]），是"能否再执行"的判据；它们不参与矢量重放门（重放不需要
 * 运行时，见 `VisualRendererVersion` 的纪律）。
 */
internal data class PersistedVisualProgram(
    val programId: String,
    val sourceJs: String,
    val apiVersion: Int,
    val sdkVersion: String,
) {
    init {
        require(programId.isNotBlank()) { "A persisted visual program needs a non-blank id" }
        require(sourceJs.isNotEmpty()) { "A persisted visual program needs its source" }
        require(apiVersion >= 1) { "A persisted visual program api version starts at 1" }
        require(sdkVersion.isNotBlank()) { "A persisted visual program needs a non-blank sdk version" }
    }
}

/**
 * ①档不可达时降级读快照/预览/文字的**有码**原因（契约 :149"版本未知或执行熔断时读兼容
 * 快照/预览/文字"）。调用方按码分流；与 [VisualRestoreFallbackReason] 正交——前者回答
 * "为什么不能重布局"，后者回答"快照为什么也不能重放"。
 */
internal enum class StrictOfflineRelayoutFallbackReason(val code: String) {
    /** 本地执行已关闭（没有引擎/引擎已 close；契约"本地执行未关闭"的否定侧）。 */
    LOCAL_EXECUTION_CLOSED("STRICT_OFFLINE_LOCAL_EXECUTION_CLOSED"),

    /** 程序行的 API/SDK 版本不在本机兼容集内（契约"版本未知"）。 */
    RUNTIME_VERSION_INCOMPATIBLE("STRICT_OFFLINE_RUNTIME_VERSION_INCOMPATIBLE"),

    /** 该 program 已执行熔断（硬终止后未解；契约"执行熔断"）。 */
    EXECUTION_FUSED("STRICT_OFFLINE_EXECUTION_FUSED"),
}

/** ①档判定结论：已持久程序可本地重布局，或带码降级读快照。 */
internal sealed interface StrictOfflineVisualDecision {
    /**
     * ①档：允许把 [sourceJs]（持久行原文）交给本地引擎重布局/拖动/播放。
     *
     * 本判定**不**授权任何远程派发：重布局走 core:data 协调器的 JS 路径——那条路结构上
     * 不触 [com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger]、不建图像
     * 通道，attempt 行 `reservation_id = NULL`、operation 终态 0/0（"0 远程派发"的现成
     * 证据位，`DefaultFigureGenerationCoordinator.beginLocalAttempt`）。
     */
    data class RelayoutAllowed(
        val programId: String,
        val sourceJs: String,
    ) : StrictOfflineVisualDecision

    /** ①档不可达：按 [reason] 降级到静态冷恢复（[VisualSnapshotRestore] 的 ②/③档）。 */
    data class FallBackToSnapshot(
        val reason: StrictOfflineRelayoutFallbackReason,
    ) : StrictOfflineVisualDecision
}

/**
 * ①档的**本机兼容集**（可注入；默认 [CURRENT]）。
 *
 * 默认值 = 随包运行时的 V1 词表：`visual-sdk.js` 资产头注（V1）、设备链夹具
 * （`V01VisualChainInstrumentedTest` 提交 `apiVersion=1`/`sdkVersion="visual-sdk-v1"`）。
 * 换引擎代次时宿主注入自己的集合——本类不藏"当前是哪代引擎"的第二个真相源。
 */
internal data class StrictOfflineRelayoutPolicy(
    val supportedApiVersions: Set<Int> = SUPPORTED_API_VERSIONS,
    val supportedSdkVersions: Set<String> = SUPPORTED_SDK_VERSIONS,
) {
    init {
        require(supportedApiVersions.isNotEmpty()) { "A relayout policy needs at least one api version" }
        require(supportedSdkVersions.isNotEmpty()) { "A relayout policy needs at least one sdk version" }
    }

    companion object {
        /** 随包 SDK 资产的 API 代次（V1）。 */
        val SUPPORTED_API_VERSIONS: Set<Int> = setOf(1)

        /** 随包 SDK 资产的版本词（visual-sdk-v1）。 */
        val SUPPORTED_SDK_VERSIONS: Set<String> = setOf("visual-sdk-v1")

        val CURRENT: StrictOfflineRelayoutPolicy = StrictOfflineRelayoutPolicy()
    }
}

/**
 * X-03：strictOffline 下**已持久程序**能否本地重布局的判定门。
 *
 * 契约口径（`visual-generation-contract.md` :149）："strictOffline 禁止外部派发和获取新
 * 模型源码，但允许已持久程序在 API/SDK/运行库兼容且本地执行未关闭时重新布局、拖动和播放；
 * 静态冷恢复优先读快照。版本未知或执行熔断时读兼容快照/预览/文字。"
 *
 * **它消灭的具体失败**：改前"0 派发只是口头承诺"——重布局的三个前置（引擎在、版本兼容、
 * 未熔断）散落在各调用方的直觉里，没有一处类型化判定；任何一条前置缺失时调用方要么自己
 * 拼条件（漏一条就把"离线可交互"写成了"离线能生成新讲解"），要么干脆跳过判定直接执行。
 * 本门把判定序收成单一纯函数：
 * 1. 本地执行已关闭 → 降级（不泄露版本兼容性这类次要事实，判定序也是契约的一部分）；
 * 2. 程序行 API/SDK 版本不在兼容集 → 降级（"版本未知"）；
 * 3. 该 program 熔断 → 降级（"执行熔断"）；
 * 4. 全过 → ①档放行，源码只取持久行原文（[PersistedVisualProgram] 在类型上堵死"新源码"）。
 *
 * **组合**：降级结论由调用方接 [VisualSnapshotRestore.restore]（②档重放/③档只读，含
 * `previewVerified` 的预览字节核对缝）——那是既有判定，本门不复制它。
 * **禁新外发**由执行路径的结构保证：重布局走协调器 JS 路径（不预留、不建通道），本门
 * 不需要也不可能消费任何网络开关——它根本没有网络参数。
 *
 * 纯函数、无 IO：JVM 门可直接钉住每一档（`StrictOfflineVisualRelayoutTest`）。
 */
internal object StrictOfflineVisualRelayout {

    fun decide(
        program: PersistedVisualProgram,
        localEngineAvailable: Boolean,
        programFused: Boolean,
        policy: StrictOfflineRelayoutPolicy = StrictOfflineRelayoutPolicy.CURRENT,
    ): StrictOfflineVisualDecision = when {
        !localEngineAvailable ->
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.LOCAL_EXECUTION_CLOSED,
            )

        program.apiVersion !in policy.supportedApiVersions ||
            program.sdkVersion !in policy.supportedSdkVersions ->
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.RUNTIME_VERSION_INCOMPATIBLE,
            )

        programFused ->
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.EXECUTION_FUSED,
            )

        else ->
            StrictOfflineVisualDecision.RelayoutAllowed(
                programId = program.programId,
                sourceJs = program.sourceJs,
            )
    }
}
