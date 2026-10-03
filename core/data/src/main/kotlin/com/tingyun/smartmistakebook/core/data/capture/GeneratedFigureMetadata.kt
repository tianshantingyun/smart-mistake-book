package com.tingyun.smartmistakebook.core.data.capture

import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * A2（4B）：生成图的**隐式 AI 标识**（用户 2026-10-03 裁定：只做隐式元数据，显式角标不做）。
 *
 * 依据《人工智能生成合成内容标识办法》第 5 条的三样事实：属性（AI 生成）+ 提供者 + 编号。
 * 写的是**生成图的文件字节**（不是内存对象、不是数据库列）。
 *
 * **保留边界（如实说）**：标识随文件本身走——文件被**原样复制**（系统分享发送原图、备份
 * 归档、外部读取该 PNG/JPEG）时仍在；但 App 的导出/分享出口是 **PDF**，渲染器把 clean image
 * 解码成 Bitmap 再画进 PDF，文件级 eXIf 不会进 PDF（不新增 PDF 元数据机制，已登记为边界）。
 *
 * 载体选型：项目已有 `androidx.exifinterface:exifinterface:1.4.1`（vault 的 EXIF 读取已在用），
 * 它对 JPEG/PNG/WebP 的 EXIF（PNG 写 eXIf 块）与 XMP 有读写支持——**不引新库**。
 * - ImageDescription = "AI 生成"（属性）
 * - Software          = "SmartMistakeBook;model=<提供者>"（提供者）
 * - Artist            = figureId（编号；与规范资产 id 同源，可在库里对上）
 * - UserComment       = "AI-GENERATED;kind=<用途>;model=<提供者>;id=<编号>"（机器可读的一行，
 *                       也是"记账"的用途/模型两列在文件里的留痕）
 *
 * 消灭的失败：生成图此前只是普通 PNG——文件被原样带走后无法分辨是不是 AI 生成，也没有
 * 提供者与编号可追溯；把标识只存在内存或数据库里，文件一离开 App 就丢。
 *
 * public：配图链的公开解析器（`AttachedImageGenerator`）把它作为落盘参数透传，装配层要用。
 */
data class GeneratedFigureProvenance(
    /** 生成用的模型标识（提供者），如 `gpt-image-2`。 */
    val model: String,
    /** 规范资产 id（编号）。 */
    val figureId: String,
    /** 用途（生成 kind），如 `REDRAW_PROBLEM` / `GENERATE_PROCESS`。 */
    val purpose: String,
) {
    init {
        require(model.isNotBlank()) { "A generated figure provenance needs its model" }
        require(figureId.isNotBlank()) { "A generated figure provenance needs its figure id" }
        require(purpose.isNotBlank()) { "A generated figure provenance needs its purpose" }
    }
}

/** 从文件字节读回的隐式标识（验收口径：读回三样事实；文件没有标识时 aiGenerated=false）。 */
internal data class GeneratedFigureMetadata(
    val aiGenerated: Boolean,
    val model: String?,
    val figureId: String?,
    val purpose: String?,
)

/** AI 生成属性的固定文案（读回判据与写入共用一处）。 */
internal const val AI_GENERATED_IMAGE_DESCRIPTION = "AI 生成"

/**
 * 把 [provenance] 写进 [file] 的字节（EXIF/eXIf；PNG/JPEG/WebP 由 ExifInterface 处理）。
 *
 * 失败即抛：调用方（生成图落盘）把"写不进标识"当成这次生成失败——宁可让这张图不可用，
 * 也不放一张**没有 AI 标识**的生成图进库里（原样复制出去时它必须带着标识）。
 */
internal fun embedGeneratedFigureMetadata(file: File, provenance: GeneratedFigureProvenance) {
    val exif = ExifInterface(file)
    exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, AI_GENERATED_IMAGE_DESCRIPTION)
    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "$SOFTWARE_PREFIX;model=${provenance.model}")
    exif.setAttribute(ExifInterface.TAG_ARTIST, provenance.figureId)
    exif.setAttribute(
        ExifInterface.TAG_USER_COMMENT,
        "$USER_COMMENT_PREFIX;kind=${provenance.purpose};model=${provenance.model};" +
            "id=${provenance.figureId}",
    )
    exif.saveAttributes()
}

/** 读回隐式标识；读不出/没有标识时返回 `aiGenerated=false`（fail-closed，绝不猜）。 */
internal fun readGeneratedFigureMetadata(file: File): GeneratedFigureMetadata {
    val exif = ExifInterface(file)
    val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
    val comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
    val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
    val artist = exif.getAttribute(ExifInterface.TAG_ARTIST)
    val model = software?.substringAfter("model=", missingDelimiterValue = "")
        ?.takeIf(String::isNotBlank)
        ?: comment?.substringAfter("model=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.takeIf(String::isNotBlank)
    return GeneratedFigureMetadata(
        aiGenerated = description == AI_GENERATED_IMAGE_DESCRIPTION ||
            comment?.startsWith(USER_COMMENT_PREFIX) == true,
        model = model,
        figureId = artist?.takeIf(String::isNotBlank)
            ?: comment?.substringAfter("id=", missingDelimiterValue = "")?.takeIf(String::isNotBlank),
        purpose = comment?.substringAfter("kind=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.takeIf(String::isNotBlank),
    )
}

private const val SOFTWARE_PREFIX = "SmartMistakeBook"
private const val USER_COMMENT_PREFIX = "AI-GENERATED"
