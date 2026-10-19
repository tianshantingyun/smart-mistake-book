package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureDispatchParent

/**
 * 一次保存期净化请求。
 *
 * 4E.1 起，净化生成也必须携带**持久派发父操作**（`CleanRedrawTaskRunner` 传入它已领取的
 * 持久作业尝试）；没有父操作的旧直调入口 fail-closed（返回 null，不生成）。
 *
 * [mimeType] 只允许 JPEG/PNG（由底层 `ImageRedrawRequest` 复核）。
 */
data class CleanImageRequest(
    val originalBytes: ByteArray,
    val mimeType: String,
    val parent: FigureDispatchParent,
    /** 可选稳定操作 id；缺省由源图 sha 派生（同一张图的重试仍是同一操作）。 */
    val operationId: String? = null,
) {
    init {
        require(originalBytes.isNotEmpty()) { "A clean redraw needs source bytes" }
        require(mimeType == "image/jpeg" || mimeType == "image/png") {
            "Only JPEG/PNG source images are supported"
        }
        require(operationId == null || operationId.isNotBlank()) {
            "A clean redraw operation id must be null or non-blank"
        }
    }
}

/**
 * The one seam through which a captured problem's original photo is redrawn into a
 * clean sheet at commit time.
 *
 * 4E.1：实现必须经统一 `FigureGenerationCoordinator`（共享 policy/ledger/图像通道），
 * 不得自己构造图像 HTTP 客户端。没有父操作的旧入口 [generateClean] 默认 fail-closed。
 */
fun interface CleanImageGenerator {
    /**
     * 统一入口：带持久派发父操作。null = 本轮没有产出（凭证/能力/预算/生成失败/
     * 无可用预留）——调用方保留原图，绝不把失败当成功。
     */
    suspend fun generateClean(request: CleanImageRequest): CleanImageResult?

    /**
     * 旧直调入口（无预留）：fail-closed。保存在默认实现里，避免任何调用点绕过账本；
     * 生产路径经 [CleanRedrawTaskRunner] 的带父操作入口。
     */
    suspend fun generateClean(originalBytes: ByteArray, mimeType: String): CleanImageResult? = null
}

data class CleanImageResult(
    val bytes: ByteArray,
    val mimeType: String,
)
