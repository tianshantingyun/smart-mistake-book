package com.tingyun.smartmistakebook.core.model

/**
 * 历史 `attachedImages` 意图的解析/重生成结果（WG-05 / KD-58）。
 *
 * 消灭的具体失败：旧兼容链把“这张图为什么没有”压成一个 `null`——渲染层只能少一张图，
 * 学生看不到原因，也没有任何可点的出口。这里把**本地命中**与**不可用**分开，不可用带
 * 有界原因；只有用户显式触发“重新生成”才会进入共享生图协调器。
 *
 * [Unavailable.detail] 是可阅读的诊断（例如共享派发预算被拒的原文），有界、不得包含
 * 密钥或源图字节；UI 只按 [Unavailable.reason] 给主文案，detail 作为补注。
 */
sealed interface AttachedImageResolution {
    /** 本地资产命中且文件核对通过；[localUri] 是 `file://`/`content://`。 */
    data class Ready(val localUri: String) : AttachedImageResolution {
        init {
            require(localUri.isNotBlank()) { "A ready attached image needs a local uri" }
        }
    }

    /**
     * 这次没有可渲染的本地图。[reason] 决定主文案与是否提供“重新生成”动作；
     * [detail] 是可选的有界诊断，不参与分支判断。
     */
    data class Unavailable(
        val reason: AttachedImageUnavailableReason,
        val detail: String? = null,
    ) : AttachedImageResolution {
        init {
            require(detail == null || detail.length <= AttachedImageResolution.MAX_DETAIL_CHARS) {
                "An attached image diagnostic must be at most " +
                    "${AttachedImageResolution.MAX_DETAIL_CHARS} chars"
            }
        }
    }

    companion object {
        const val MAX_DETAIL_CHARS: Int = FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS
    }
}

/**
 * 历史配图的生产解析能力：带原因的本地读取 + 显式重生成。
 *
 * 为什么需要这个接口：路由/面板既有的 `attachedImageResolver` 参数是
 * `suspend (AttachedImage) -> String?`，在 `TutorSessionPanel` 禁改时无法再加第二个
 * 参数。本接口**继承**该函数类型；生产解析器实现本接口，`TutorChatConversation` 对
 * 函数值做能力检测，拿到真实原因与显式重生成出口；测试里的普通 lambda 仍走旧回退。
 *
 * 这是用户可见的 KD-58/WG-05 收口：缓存未命中不再是“没有生成过”，损坏/缺源/读失败
 * 各自有原因；只有显式重生成才会进入共享协调器（不在这里建第二个通道）。
 */
interface AttachedImageResolutionPort : suspend (AttachedImage) -> String? {
    /** cache-only；返回带原因的 [AttachedImageResolution]。 */
    suspend fun resolve(image: AttachedImage): AttachedImageResolution

    /** 用户显式动作；绑定原消息的模型任务 requestId，复用共享协调器。 */
    suspend fun regenerate(
        parentRequestId: String,
        image: AttachedImage,
    ): AttachedImageResolution
}

/**
 * 历史配图不可用的原因。主文案在 `core:ui` 的 `attachedImageFailureMessage` 里，
 * 这里只表达可分支的事实。
 */
enum class AttachedImageUnavailableReason {
    /** 这条历史意图在本机从未落过资产（缓存未命中；打开消息不会自动联网生成）。 */
    NOT_GENERATED,

    /** 资产行在，但文件缺失/长度或 sha 与登记不一致——不自动覆盖重生成。 */
    DAMAGED,

    /** 本机读取资产行失败（数据库错误；不是联网失败，也没有发起生图）。 */
    LOCAL_READ_FAILED,

    /** 重绘所需的原题面证据不可得（例如手动建题没有原图）。 */
    SOURCE_UNAVAILABLE,

    /** 本装配没有可用的重生成通道（未配置模型或未注入协调器）。 */
    REGENERATION_UNAVAILABLE,

    /** 共享派发账本/策略拒绝了这次显式重生成（预算、绑定、配置）。 */
    REGENERATION_REJECTED,

    /** 协调器执行失败（网络、解码、落盘），或结果不是可持久化的图像产物。 */
    REGENERATION_FAILED,
}
