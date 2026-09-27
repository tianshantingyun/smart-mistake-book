package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus

internal enum class OrganizationProviderAvailability {
    LOADING,
    LOAD_FAILED,
    UNAVAILABLE,
    READY,
}

internal data class MistakeOrganizationSurfaceFacts(
    val providerAvailability: OrganizationProviderAvailability,
    val hasPreparation: Boolean = false,
    val isPreparing: Boolean = false,
    val preparationDismissed: Boolean = false,
    val hasRecoveredRequest: Boolean = false,
    val isContinuingRecoveredRequest: Boolean = false,
    val taskStatus: ModelTaskStatus? = null,
    val taskMessage: String? = null,
    val taskRequestId: String? = null,
    val hasUsableOutput: Boolean = false,
    val applyState: AutomaticOrganizationState = AutomaticOrganizationState.Idle,
    /**
     * 知识能力就绪位（D-Q3）。默认 [KnowledgeBaseAvailability.Ready] = 只看模型侧事实，
     * 生产接线由 app 层传真实就绪位（见 MistakeOrganizationSection 的调用点）。
     */
    val knowledgeBaseAvailability: KnowledgeBaseAvailability = KnowledgeBaseAvailability.Ready,
)

internal sealed interface MistakeOrganizationSurfaceState {
    data object CapabilityLoading : MistakeOrganizationSurfaceState
    data object CapabilityLoadFailed : MistakeOrganizationSurfaceState
    data object ProviderUnavailable : MistakeOrganizationSurfaceState
    data object PreparationLoading : MistakeOrganizationSurfaceState
    data object PreparationRetry : MistakeOrganizationSurfaceState

    /**
     * 知识内容还没就位（D-Q3）。
     *
     * 它消灭的失败：改前"整理"在 prepare() 里**自己触发一次安装**并阻塞（首装 16.7 秒），
     * 装失败还会被上层统一 catch 成"暂时无法准备智能整理 + 去配置模型"——把学生指向一个
     * 与故障无关的页面。现在如实说"准备中"，就绪后本屏自动继续。
     */
    data class KnowledgePreparing(
        val availability: KnowledgeBaseAvailability,
    ) : MistakeOrganizationSurfaceState

    data class Consent(
        val paused: Boolean,
        val running: Boolean,
    ) : MistakeOrganizationSurfaceState

    data class ExecutionFailed(val message: String) : MistakeOrganizationSurfaceState
    data class Running(val message: String?) : MistakeOrganizationSurfaceState
    data object UnusableResult : MistakeOrganizationSurfaceState
    data object Applying : MistakeOrganizationSurfaceState
    data object Incomplete : MistakeOrganizationSurfaceState
    data class ApplyFailed(val message: String) : MistakeOrganizationSurfaceState
    data object PreservedUserCorrection : MistakeOrganizationSurfaceState
    data object Applied : MistakeOrganizationSurfaceState
}

internal fun resolveMistakeOrganizationSurface(
    facts: MistakeOrganizationSurfaceFacts,
): MistakeOrganizationSurfaceState = when {
    facts.providerAvailability == OrganizationProviderAvailability.LOAD_FAILED ->
        MistakeOrganizationSurfaceState.CapabilityLoadFailed

    facts.providerAvailability == OrganizationProviderAvailability.LOADING ->
        MistakeOrganizationSurfaceState.CapabilityLoading

    facts.providerAvailability == OrganizationProviderAvailability.UNAVAILABLE ->
        MistakeOrganizationSurfaceState.ProviderUnavailable

    facts.hasRecoveredRequest && facts.hasPreparation -> MistakeOrganizationSurfaceState.Consent(
        paused = true,
        running = facts.isContinuingRecoveredRequest,
    )

    facts.hasRecoveredRequest -> MistakeOrganizationSurfaceState.PreparationRetry

    // D-Q3：知识内容还没就位 → 不发起整理、也不把学生指向模型设置页；就绪后本屏自动继续。
    // 位置在"恢复既有请求"之后：那条路是**重放已持久化的请求**，不需要知识内容。
    !facts.knowledgeBaseAvailability.isReady -> MistakeOrganizationSurfaceState.KnowledgePreparing(
        facts.knowledgeBaseAvailability,
    )

    !facts.hasPreparation && (facts.isPreparing || !facts.preparationDismissed) ->
        MistakeOrganizationSurfaceState.PreparationLoading

    !facts.hasPreparation -> MistakeOrganizationSurfaceState.PreparationRetry

    facts.taskStatus == ModelTaskStatus.PERMANENT_FAILURE ||
        facts.taskStatus == ModelTaskStatus.CANCELLED -> {
        MistakeOrganizationSurfaceState.ExecutionFailed(
            message = facts.taskMessage?.takeIf(String::isNotBlank) ?: "这次整理没有完成",
        )
    }

    facts.taskStatus in RUNNING_ORGANIZATION_STATUSES ->
        MistakeOrganizationSurfaceState.Running(facts.taskMessage)

    facts.taskStatus != ModelTaskStatus.SUCCEEDED -> MistakeOrganizationSurfaceState.Consent(
        paused = false,
        running = false,
    )

    !facts.hasUsableOutput || facts.taskRequestId == null ->
        MistakeOrganizationSurfaceState.UnusableResult

    facts.applyState.requestId != null &&
        facts.applyState.requestId != facts.taskRequestId ->
        MistakeOrganizationSurfaceState.Applying

    else -> when (val applyState = facts.applyState) {
        AutomaticOrganizationState.Idle,
        is AutomaticOrganizationState.Applying,
        -> MistakeOrganizationSurfaceState.Applying

        is AutomaticOrganizationState.Incomplete ->
            MistakeOrganizationSurfaceState.Incomplete

        is AutomaticOrganizationState.Failed ->
            MistakeOrganizationSurfaceState.ApplyFailed(applyState.message)

        is AutomaticOrganizationState.PreservedUserCorrection ->
            MistakeOrganizationSurfaceState.PreservedUserCorrection

        is AutomaticOrganizationState.Applied ->
            MistakeOrganizationSurfaceState.Applied
    }
}

private val RUNNING_ORGANIZATION_STATUSES = setOf(
    ModelTaskStatus.WAITING_FOR_MODEL,
    ModelTaskStatus.QUEUED,
    ModelTaskStatus.RUNNING,
    ModelTaskStatus.STREAMING,
)

internal sealed interface AutomaticOrganizationState {
    val requestId: String?

    data object Idle : AutomaticOrganizationState {
        override val requestId: String? = null
    }

    data class Applying(override val requestId: String) : AutomaticOrganizationState
    data class Applied(override val requestId: String) : AutomaticOrganizationState
    data class PreservedUserCorrection(
        override val requestId: String,
    ) : AutomaticOrganizationState
    data class Incomplete(override val requestId: String) : AutomaticOrganizationState
    data class Failed(
        override val requestId: String,
        val message: String,
    ) : AutomaticOrganizationState
}
