package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest

/**
 * 宿主帧输入的提供方（视觉契约 §3.2 的 `FrameInput` 中**由宿主冻结**的部分）。
 *
 * **它消灭的具体失败**：协调器执行 JS 时如果自己编 viewport/theme/seed/timeMs，等于伪造一个
 * 不存在的布局环境——同一 program 两次执行得到不同几何，且违反契约"布局事务冻结这些值"。
 * 帧输入属于宿主（屏幕尺寸、当前主题、native clock、PRNG 种子），必须由装配方在请求时
 * 一次性给出 [VisualFrameSnapshot]，协调器只负责把它带进 [VisualExecutionRequest]。
 *
 * 只在注入 `visualEngine` 时才被调用；`programId`/`sceneRevision`/`state`/源码**不**由本接口
 * 提供——它们由协调器从 proposal 派生（程序身份必须与源码同源，见 `figureJsBasisFingerprint`）。
 */
fun interface VisualFrameInputs {
    /** 为这次生成冻结一帧输入。返回 null 表示本机此刻给不出可信帧（协调器如实回 UNAVAILABLE）。 */
    fun forGeneration(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): VisualFrameSnapshot?
}

/** 一帧的宿主冻结输入（对应契约 §3.2 `FrameInput` 的 `viewport/theme/seed/timeMs`）。 */
data class VisualFrameSnapshot(
    val viewport: VisualViewport,
    val theme: String,
    val seed: Long,
    val timeMs: Long,
) {
    init {
        require(theme.isNotBlank()) { "A visual frame needs a theme" }
        require(timeMs >= 0L) { "A visual frame time must not be negative" }
    }
}
