package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.figureSha256Hex

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
 * 净化/重绘产物的**类型化引用**（4E WG-03 T3）。
 *
 * **它消灭的具体失败**：改前回调链只搬裸 `ByteArray`，"这条图是哪条方法产的、provider 报的
 * 模型是什么、内容 sha 是多少"全靠挂接处自己猜——`attachCleanRedrawImage` 于是把
 * `provenance.model` 硬编码成 edits 通道的默认模型、资产幂等键自己重算 sha。换方法
 * （4E.2 的 JS）或 provider 换模型版本后，这套硬编码会**伪报来源**；字节在生成与挂接之间
 * 被替换也无从核对。引用把这三样事实（方法/模型/内容 sha）从生成侧**随产物一起**带到
 * 挂接侧：挂接按 [method] 填 sourceType/provenance，按 [CanonicalImage.contentSha256]
 * 核对字节，核对通过才绑定题目修订。
 */
sealed interface FigureArtifactRef {
    /** 产物由哪条方法生成（真实值来自生成侧，挂接侧不猜）。 */
    val method: FigureGenerationMethod

    /**
     * 这次生成在审计台账里的尝试 id（4E WG-08）。旧实现/测试替身不给时为 null；
     * 入口落盘成功后用它与产物资产 id 回填 `figure_generation_attempt.output_asset_id`
     * （保存期净化与显式重绘两条链都负责）。它只是审计身份，不是预算句柄。
     */
    val attemptId: String?

    /**
     * canonical 图片（IMAGE_MODEL 产物）：字节 + 内容 sha + provider 报告的模型标识。
     *
     * [imageBytes] 构造时复制、只读视图取出（与 `FigureArtifact.ImageAsset` 同一手法：
     * 产物随后被落盘/挂接，每次读取再复制会把同一张图在内存里放大数倍）。
     */
    class CanonicalImage(
        imageBytes: ByteArray,
        val mimeType: String,
        val contentSha256: String,
        /** provider 响应里的真实模型版本；没报时 null（挂接侧按请求模型兜底，不猜别的）。 */
        val modelId: String?,
        override val attemptId: String? = null,
    ) : FigureArtifactRef {
        override val method: FigureGenerationMethod
            get() = FigureGenerationMethod.IMAGE_MODEL

        private val bytes: ByteArray = imageBytes.copyOf()

        init {
            require(mimeType == "image/jpeg" || mimeType == "image/png") {
                "A canonical clean image must be JPEG or PNG"
            }
            require(contentSha256.length == SHA_256_HEX_CHARS) {
                "A canonical clean image sha must be SHA-256"
            }
            require(bytes.isNotEmpty()) { "A canonical clean image must carry bytes" }
            require(contentSha256 == figureSha256Hex(bytes)) {
                "A canonical clean image sha must match its bytes"
            }
        }

        fun imageBytes(): ByteArray = bytes
    }

    /**
     * JS 快照位（4E.2）。**本批没有任何生产路径构造它**——JS 方法在 4E.2 的门之前仍是
     * UNAVAILABLE（已裁边界：不广告）；形状先落在这里，是为了让回调/挂接缝从今天起就按
     * "canonical 图或 JS 快照"两形设计，4E.2 接通时不再改缝（与 `FigureArtifact.Program`
     * 同一手法）。
     */
    class JsSnapshot(
        val programId: String,
        val snapshotId: String,
        override val attemptId: String? = null,
    ) : FigureArtifactRef {
        override val method: FigureGenerationMethod
            get() = FigureGenerationMethod.JS

        init {
            require(programId.isNotBlank()) { "A JS visual program id must not be blank" }
            require(snapshotId.isNotBlank()) { "A JS visual snapshot id must not be blank" }
        }
    }
}

private const val SHA_256_HEX_CHARS = 64

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
     *
     * 产物以类型化 [FigureArtifactRef] 返回（4E WG-03：方法/模型/内容 sha 随产物流动，
     * 挂接侧不再从裸字节里猜来源）。
     */
    suspend fun generateClean(request: CleanImageRequest): FigureArtifactRef?

    /**
     * 旧直调入口（无预留）：fail-closed。保存在默认实现里，避免任何调用点绕过账本；
     * 生产路径经 [CleanRedrawTaskRunner] 的带父操作入口。
     */
    suspend fun generateClean(originalBytes: ByteArray, mimeType: String): FigureArtifactRef? = null

    /**
     * 当前是否具备真正生成的条件（凭证/能力测试；不发送任何请求）。
     *
     * 4E WG-06 T3：显式重绘的"先配置模型"判据必须**建任务行之前**回答。只看
     * "generator 非 null" 不够——生产装配在"已配置 provider"时就创建 generator，而真正的
     * 凭证门在 [generateClean] 内部；那会让一次注定失败的请求先建行、先领取尝试次数。
     * 默认 true 保持旧实现/测试替身的行为不变。
     */
    suspend fun isAvailable(): Boolean = true
}
