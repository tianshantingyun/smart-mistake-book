package com.tingyun.smartmistakebook.core.domain

/**
 * 4E.2 交互图的宿主端口：读回消息上的视觉引用、执行 `reduce(state, event)`、重画当前帧、
 * 持久化未提交草稿。
 *
 * 它消灭的具体失败：`tutor_message_visual` 与 `tutor_visual_snapshot/program` 里的可重放
 * 数据此前没有任何 UI 消费者——图只能以静态 PNG 存在。本端口把"消息 → 程序/快照/状态 →
 * 新帧"收成一条**只读 + 有界状态写**的路径，并明确禁止：
 * - 由模型或 UI 改写 `sourceJs`（程序不可变，身份即 programId）；
 * - 在拖动的每一帧写库（`saveDraft` 是显式 checkpoint，由 UI 节流）；
 * - 用过期 revision 覆盖新状态（`expectedRevision` CAS）。
 */
data class TutorInteractiveVisualScene(
    val messageId: String,
    val ordinal: Int,
    val revision: Int,
    val programId: String,
    val sourceJs: String,
    val initialStateJson: String,
    val stateJson: String,
    val sceneJson: String,
    val drawCommandsJson: String,
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    val fontScaleMilli: Int,
    val theme: String,
    val timeMs: Long,
    val seed: Long,
    val sceneRevision: Int,
    val uiStateJson: String?,
    val formDigest: String?,
) {
    init {
        require(messageId.isNotBlank() && ordinal >= 0)
        require(revision >= 1)
        require(programId.isNotBlank() && sourceJs.isNotEmpty())
        require(stateJson.isNotEmpty() && sceneJson.isNotEmpty() && drawCommandsJson.isNotEmpty())
        require(viewportWidthDu in 1..4_096 && viewportHeightDu in 1..4_096)
        require(fontScaleMilli > 0)
    }
}

/** 一帧已画好的 PNG（只在内存里；不落资产、不登记行）。 */
class TutorInteractiveVisualFrame(
    val stateJson: String,
    val sceneJson: String,
    val drawCommandsJson: String,
    val pngBytes: ByteArray,
) {
    init {
        require(stateJson.isNotEmpty() && sceneJson.isNotEmpty() && drawCommandsJson.isNotEmpty())
        require(pngBytes.isNotEmpty())
    }
}

sealed interface TutorInteractiveVisualFrameResult {
    class Rendered(val frame: TutorInteractiveVisualFrame) : TutorInteractiveVisualFrameResult

    data class Rejected(val diagnostic: String) : TutorInteractiveVisualFrameResult
}

sealed interface TutorInteractiveVisualReduceResult {
    class Updated(val frame: TutorInteractiveVisualFrame) : TutorInteractiveVisualReduceResult

    data class Rejected(val diagnostic: String) : TutorInteractiveVisualReduceResult
}

interface TutorInteractiveVisualPort {
    /**
     * 读消息上的第一张（ordinal 默认 0）交互图；没有链接时，若调用方带着痕迹里的
     * program/snapshot 身份，则**幂等补建** LIVE 引用后再读（消息行已存在、快照已 LIVE）。
     * 仍没有程序/快照时返回 null。
     */
    suspend fun read(
        messageId: String,
        ordinal: Int = 0,
        programId: String? = null,
        snapshotId: String? = null,
    ): TutorInteractiveVisualScene?

    /** 用给定 state 重画（首次打开、草稿恢复、减少后重画共用同一条渲染路径）。 */
    suspend fun render(
        scene: TutorInteractiveVisualScene,
        stateJson: String = scene.stateJson,
    ): TutorInteractiveVisualFrameResult

    /**
     * 宿主调用 `reduce(state, event)`，然后**用返回的 state 重画**。程序没有 `reduce`
     * 或返回非法 StateV1 时拒绝，不把半个状态画到屏幕上。
     */
    suspend fun reduce(
        scene: TutorInteractiveVisualScene,
        eventJson: String,
    ): TutorInteractiveVisualReduceResult

    /**
     * 草稿 checkpoint（拖动结束/输入停顿/进后台）。`expectedRevision` 是读回时的指针版本；
     * 命中 0 行 = 指针已换（换图/重渲染），调用方必须重新读取而不是覆盖。
     */
    suspend fun saveDraft(
        messageId: String,
        ordinal: Int,
        expectedRevision: Int,
        uiStateJson: String?,
        formDigest: String?,
    ): Boolean
}
