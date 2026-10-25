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
 * 归档、外部读取该 PNG/JPEG）时仍在；DOCX 导出按**原字节嵌入**资产（`DocxImageSource`
 * 原样读出待嵌字节并按记录 sha256 复核，`MistakeExportVisualRoleTest` 钉住 Word 包 media
 * 部件与资产逐字节一致），隐式标识随字节进 Word 包；但 **PDF** 渲染器把 clean image 解码
 * 成 Bitmap 再画进 PDF，文件级元数据不会进 PDF（不新增 PDF 元数据机制，已登记为边界；
 * PDF 卷面由导出侧显式图注覆盖——批次 20 G3，`core:export` `AI_GENERATED_IMAGE_CAPTION`）。
 *
 * 载体选型：项目已锁定 `androidx.exifinterface:exifinterface:1.4.1`（vault 的 EXIF 读取已在
 * 用）——**不引新库**。写入能力按 1.4.1 发布构件字节码实测（R2
 * `docs/research/2026-10-25-implicit-labeling-xmp-selection.md` §3.1，非文档推断）：
 * EXIF 对 JPEG/PNG/WebP 可写（PNG 写 eXIf 块）；XMP 包对 JPEG 写独立 APP1 段、PNG 写
 * iTXt 块（`TAG_XMP` 写路径）；WebP 的 XMP 库不写（本格式只产 PNG/JPEG，不在此路径上）。
 *
 * 两层标识（《办法》第五条三要素各写一遍；**备份范围不等价**，如实登记）：
 * - EXIF 私有约定层（4B 批 2）：
 *   - ImageDescription = "AI 生成"（属性）
 *   - Software          = "SmartMistakeBook;model=<提供者>"（提供者）
 *   - Artist            = figureId（编号；与规范资产 id 同源，可在库里对上）
 *   - UserComment       = "AI-GENERATED;kind=<用途>;model=<提供者>;id=<编号>"（机器可读的一行，
 *                         也是"记账"的用途/模型两列在文件里的留痕）
 * - TC260 规范层（批次 21 T5）：`TAG_XMP` 挂 TC260-PG-20259A 的 AIGC JSON 包
 *   （[aigcXmpPacket]），只写规范必填三字段（[aigcJson]）——第三方 TC260 检测器按此字段
 *   读，不再依赖本项目私有约定。命名空间 URI 为**单一来源待验证**（R2 §5-U2：官方 PDF
 *   有会话下载门槛未取得原文），若被官方原文推翻只改 `AIGC_NAMESPACE_URI` 一个常量，
 *   与 EXIF 层互不牵连。
 *
 * **备份范围（复核修正）**：XMP 层只有属性（Label）、提供者名称（ContentProducer）与编号
 * （ProduceID）；EXIF 被剥掉而 XMP 仍在时，[readGeneratedFigureMetadata] 能恢复 `aiGenerated`
 * 与 `figureId`，但**恢复不了 model 与 purpose**（TC260 必填三字段里没有它们，本实现也不写
 * 非必填字段）。因此 XMP 不是 EXIF 的完整备份；需要 model/purpose 的消费方必须容忍 null
 * （fail-closed），不得把"XMP 在"当成四字段齐全。
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
 * 把 [provenance] 写进 [file] 的字节（EXIF/eXIf + TC260 XMP；PNG/JPEG 由 ExifInterface 分流）。
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
    // TC260 规范层（批次 21 T5）：同一个 saveAttributes 里随 TAG_XMP 走（JPEG→独立 APP1、
    // PNG→iTXt，容器由 exifinterface 1.4.1 分流，见类 KDoc）。只写规范必填三字段；写不进
    // 一样抛——无标识的生成图同样不放行。
    exif.setAttribute(ExifInterface.TAG_XMP, aigcXmpPacket(aigcJson(provenance)))
    exif.saveAttributes()
}

/** 读回隐式标识；读不出/没有标识时返回 `aiGenerated=false`（fail-closed，绝不猜）。 */
internal fun readGeneratedFigureMetadata(file: File): GeneratedFigureMetadata {
    val exif = ExifInterface(file)
    // TC260 规范层读回（批次 21 T5）：包在、命名空间对得上、JSON 三字段齐、且 Label=="1"
    // 才算——别家 XMP、伪造的同名 tag、损坏包、Label=="2"/"3"（传播平台语义）一律不算。
    // 伪标识是《办法》第十条红线，宁可漏判不误判。
    return resolveGeneratedFigureMetadata(
        description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
        comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT),
        software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
        artist = exif.getAttribute(ExifInterface.TAG_ARTIST),
        aigc = parseAigcXmpPacket(exif.getAttribute(ExifInterface.TAG_XMP)),
    )
}

/**
 * 读回行的纯解析层（[readGeneratedFigureMetadata] 之外可 JVM 测试；ExifInterface 离开
 * Android 运行时不可用）。优先级：EXIF 四字段 > XMP 三字段；XMP 只补属性与编号，
 * model/purpose 缺失就是 null（见类 KDoc"备份范围"）。
 */
internal fun resolveGeneratedFigureMetadata(
    description: String?,
    comment: String?,
    software: String?,
    artist: String?,
    aigc: AigcXmpFields?,
): GeneratedFigureMetadata {
    val model = software?.substringAfter("model=", missingDelimiterValue = "")
        ?.takeIf(String::isNotBlank)
        ?: comment?.substringAfter("model=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.takeIf(String::isNotBlank)
    return GeneratedFigureMetadata(
        aiGenerated = description == AI_GENERATED_IMAGE_DESCRIPTION ||
            comment?.startsWith(USER_COMMENT_PREFIX) == true ||
            isAigcAiGeneratedLabel(aigc?.label),
        model = model,
        figureId = artist?.takeIf(String::isNotBlank)
            ?: comment?.substringAfter("id=", missingDelimiterValue = "")?.takeIf(String::isNotBlank)
            // XMP-only（EXIF 被剥掉）时的编号来源：TC260 ProduceID。
            ?: aigc?.produceId?.takeIf(String::isNotBlank),
        purpose = comment?.substringAfter("kind=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.takeIf(String::isNotBlank),
    )
}

// ---- TC260 AIGC 隐式标识：XMP 包层（批次 21 T5；配方与字段表出处 R2 §1/§4） ----

/**
 * TC260-PG-20259A 的 AIGC 命名空间 URI（属性名 `AIGC`、前缀 `TC260`）。
 *
 * **单一来源待验证**（R2 §1.4/§5-U2：博客园实现文引官方指南示例码；官方 PDF 有会话下载
 * 门槛，本批未取得原文）。字段名与必选性已三独立源交叉（腾讯云/阿里云/掘金，R2 §1.3）；
 * 本 URI 若被官方原文推翻，只改这一个常量。
 */
private const val AIGC_NAMESPACE_URI = "http://www.tc260.org.cn/ns/AIGC/1.0/"

/** `Label=="1"` = AI 生成合成（三独立源一致；`"2"/"3"` 是传播平台专用语义，本方不写）。 */
private const val AIGC_LABEL_AI_GENERATED = "1"

/**
 * `ContentProducer`：服务提供者**名称口径**（R2 §1.3）。与 [SOFTWARE_PREFIX] 同值但语义
 * 不同——这是 TC260 字段、那是 EXIF 字段；官方若改用 27 位编码口径（阿里云单源转述
 * R2-U4，未采）只改这里。
 */
private const val AIGC_CONTENT_PRODUCER = "SmartMistakeBook"

/**
 * 《办法》第五条三要素 → TC260 AIGC JSON。**只写规范必填三字段**：传播要素
 * （ContentPropagator/PropagateID）与预留防篡改位（ReservedCode1/2）是《办法》第六条
 * 传播平台/预留语义，本 App 是本地生成方不是传播平台，不写（R2 §0.4）。
 *
 * 已知边界：`ProduceID` 直接进 JSON 字符串，生产值都是 hash 派生（4 个生产写入点实测，
 * 如 `"clean-" + sha256Hex`），不含引号/反斜杠；若未来调用方传入此类字符，包的 JSON 会
 * 损坏且**读回 fail-closed**（[parseAigcXmpPacket] 返回 null，退回 EXIF 层判据），不崩、
 * 不误标。
 */
internal fun aigcJson(provenance: GeneratedFigureProvenance): String =
    "{\"Label\":\"$AIGC_LABEL_AI_GENERATED\"," +
        "\"ContentProducer\":\"$AIGC_CONTENT_PRODUCER\"," +
        "\"ProduceID\":\"${provenance.figureId}\"}"

/**
 * TC260 JSON → 完整 XMP 包：xpacket 头尾（`id` 为 XMP Spec Part 1 的固定 magic）+ 标准骨架
 * + TC260 命名空间（各段出处 R2 §4.1）。JSON 整体做 XML 文本节点转义（`& < >`，XML 必需）。
 */
internal fun aigcXmpPacket(json: String): String =
    "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
        " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
        "  <rdf:Description rdf:about=\"\" xmlns:TC260=\"$AIGC_NAMESPACE_URI\">\n" +
        "   <TC260:AIGC>${xmlEscape(json)}</TC260:AIGC>\n" +
        "  </rdf:Description>\n" +
        " </rdf:RDF>\n" +
        "</x:xmpmeta>\n" +
        "<?xpacket end=\"w\"?>"

/** 从 XMP 包解析出的 TC260 AIGC 字段（三字段全部在才算解析成功）。 */
internal data class AigcXmpFields(
    val label: String,
    val contentProducer: String,
    val produceId: String,
)

/**
 * 解析 TC260 AIGC XMP 包；**fail-closed**：无包/没有本标记/**命名空间不是
 * [AIGC_NAMESPACE_URI]**/别家 XMP/JSON 损坏/缺任一必填字段一律返回 null——把别家或伪造的
 * 包读成 AI 标识是伪标识（《办法》第十条），宁可漏判不误判。
 *
 * 只按字面 `<TC260:AIGC>` 搜索会把"别家 XMP 恰好用了同名前缀"或攻击者手写同名 tag 误读为
 * 本方标识；这里要求最近的 `rdf:Description` 作用域内声明了精确的 TC260 命名空间 URI。
 * 这不是完整 XML 解析（无新依赖），但把"同名字面 tag"挡在 fail-closed 一侧。
 */
internal fun parseAigcXmpPacket(packet: String?): AigcXmpFields? {
    if (packet == null) return null
    val openTag = "<TC260:AIGC>"
    val closeTag = "</TC260:AIGC>"
    val start = packet.indexOf(openTag)
    if (start < 0) return null
    val end = packet.indexOf(closeTag, start + openTag.length)
    if (end < 0) return null
    if (!declaresAigcNamespaceInScope(packet, start)) return null
    val json = xmlUnescape(packet.substring(start + openTag.length, end))
    return AigcXmpFields(
        label = aigcJsonField(json, "Label") ?: return null,
        contentProducer = aigcJsonField(json, "ContentProducer") ?: return null,
        produceId = aigcJsonField(json, "ProduceID") ?: return null,
    )
}

/**
 * 元素前最近的 `rdf:Description` 起始标签必须声明精确的 `xmlns:TC260` URI。
 *
 * 允许单/双引号与等号周围空白；不接受仅在其他元素上声明（命名空间不在作用域内）。
 * 找不到 `rdf:Description` → false（fail-closed）。
 */
private fun declaresAigcNamespaceInScope(packet: String, elementStart: Int): Boolean {
    val descriptionStart = packet.lastIndexOf("<rdf:Description", elementStart)
    if (descriptionStart < 0) return false
    val descriptionTagEnd = packet.indexOf('>', descriptionStart)
    if (descriptionTagEnd < 0 || descriptionTagEnd > elementStart) return false
    val descriptionTag = packet.substring(descriptionStart, descriptionTagEnd + 1)
    return Regex(
        """xmlns:TC260\s*=\s*["']${Regex.escape(AIGC_NAMESPACE_URI)}["']""",
    ).containsMatchIn(descriptionTag)
}

/** TC260 隐式标识的"AI 生成"判据：仅 `Label=="1"`（`"2"/"3"` 为传播平台语义，不算）。 */
internal fun isAigcAiGeneratedLabel(label: String?): Boolean = label == AIGC_LABEL_AI_GENERATED

/** 取 JSON 字符串字段值（`\"`/`\\` 逃逸按字面还原）；字段缺失、未闭合或空值返回 null。 */
private fun aigcJsonField(json: String, key: String): String? {
    val needle = "\"$key\":\""
    val valueStart = json.indexOf(needle)
    if (valueStart < 0) return null
    var index = valueStart + needle.length
    val value = StringBuilder()
    while (index < json.length) {
        val c = json[index]
        when {
            c == '\\' && index + 1 < json.length -> {
                value.append(json[index + 1])
                index += 2
            }
            c == '"' -> return value.toString().takeIf(String::isNotEmpty)
            else -> {
                value.append(c)
                index += 1
            }
        }
    }
    return null
}

/** XML 文本节点转义（只需这三个字符；引号在文本节点合法）。 */
private fun xmlEscape(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** 与 [xmlEscape] 对称；`&amp;` 必须最后解（`&amp;lt;` 的正确还原是字面 `&lt;`）。 */
private fun xmlUnescape(value: String): String =
    value.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

private const val SOFTWARE_PREFIX = "SmartMistakeBook"
private const val USER_COMMENT_PREFIX = "AI-GENERATED"
