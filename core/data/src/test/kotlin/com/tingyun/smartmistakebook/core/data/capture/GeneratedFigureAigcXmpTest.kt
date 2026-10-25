package com.tingyun.smartmistakebook.core.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5（批次 21）：TC260 AIGC 隐式标识 **XMP 包序列化层**的 JVM 钉住（《办法》第五条三要素；
 * 配方 `docs/research/2026-10-25-implicit-labeling-xmp-selection.md` §4）。
 *
 * **为什么只测包层**：文件级容器写入由 `androidx.exifinterface` 完成，而它在 Android 运行时
 * 之外不可用——本批 JVM 探针实测（jshell + 1.4.1 发布构件 classes.jar）：裸 JVM 上
 * `ExifInterface(File)` 构造即抛 `NoClassDefFoundError:
 * android/content/res/AssetManager$AssetInputStream`，且其 `saveAttributes` 依赖
 * `android.system.Os.lseek` 的真实文件描述符语义（mockable jar 默认值下不可靠）。文件级
 * 写入→读回由仪器化层盖（`GeneratedFigureMetadataInstrumentedTest`，同批扩展 PNG 共存/
 * JPEG/非 AI 三用例）。本文件钉住的是本批唯一自写的层（R2 §0.3："需要'自写'的只有 XMP
 * 包字符串本身"）。
 *
 * 钉住四件事：
 * 1. 包结构合 XMP Part 1（xpacket 头尾 + 固定 magic id）+ TC260 命名空间（**单一来源待
 *    验证**，R2 §5-U2）；
 * 2. **只写规范必填三字段**（Label/ContentProducer/ProduceID）——传播要素与 Reserved
 *    字段一个都不出现（《办法》第六条是传播平台义务，本 App 不适用）；
 * 3. 写入→读回 round-trip（含 `& < >` XML 特殊字符）；
 * 4. fail-closed：无包/别家 XMP/损坏 JSON/缺字段一律"无标识"——伪标识是《办法》第十条
 *    红线。
 *
 * 与导出侧显式图注判据（`core:export` `isAiGeneratedExportImage`，批次 20 G3）的关系：
 * 两套判据指向同一集合——凡携带 [GeneratedFigureProvenance] 的图恰是 CLEAN_IMAGE/
 * TUTOR_EXPLANATION/旧调用方 null 三个角色（`AiGeneratedImageCaption.kt` KDoc 已证），
 * 而 QUESTION_DIAGRAM 等非生成图在生产路径上不产生 provenance、进不了
 * [embedGeneratedFigureMetadata]（vault `provenance?.let { … }` 单点选择，:373）——
 * "非 AI 图不写"的文件级效果由仪器化用例 `anImagePersistedWithoutProvenanceCarriesNoAigcLabel` 盖。
 */
class GeneratedFigureAigcXmpTest {

    private fun provenance(figureId: String) = GeneratedFigureProvenance(
        model = "gpt-image-2",
        figureId = figureId,
        purpose = "CLEAN_REDRAW",
    )

    @Test
    fun `the packet carries the xpacket frame and the tc260 namespace`() {
        val packet = aigcXmpPacket(aigcJson(provenance("figure-1")))
        assertTrue(
            "xpacket 头（含 XMP Part 1 固定 magic id）必须在",
            packet.startsWith("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"),
        )
        assertTrue("xpacket 尾必须在", packet.trimEnd().endsWith("<?xpacket end=\"w\"?>"))
        assertTrue(
            "TC260 命名空间必须在（R2 §1.4，单一来源待验证）",
            packet.contains("xmlns:TC260=\"http://www.tc260.org.cn/ns/AIGC/1.0/\""),
        )
    }

    @Test
    fun `only the three required tc260 fields are written`() {
        val packet = aigcXmpPacket(aigcJson(provenance("figure-1")))
        val json = packet.substringAfter("<TC260:AIGC>").substringBefore("</TC260:AIGC>")
        assertTrue("Label 必须是 \"1\"（AI 生成合成）", json.contains("\"Label\":\"1\""))
        assertTrue(
            "ContentProducer 必须是名称口径（编码口径是单源转述，未采）",
            json.contains("\"ContentProducer\":\"SmartMistakeBook\""),
        )
        assertTrue("ProduceID 必须与 figureId 同源（可对账）", json.contains("\"ProduceID\":\"figure-1\""))
        assertFalse(
            "《办法》第六条传播要素与 Reserved 预留位不写（R2 §0.4）",
            listOf("ContentPropagator", "PropagateID", "ReservedCode1", "ReservedCode2")
                .any(json::contains),
        )
    }

    @Test
    fun `the packet reads back with all three fields intact`() {
        // 写入→读回 round-trip：本批唯一自写的序列化层必须无损往返。
        val readBack = requireNotNull(
            parseAigcXmpPacket(aigcXmpPacket(aigcJson(provenance("figure-1")))),
        ) { "写入的包必须能读回" }
        assertEquals("1", readBack.label)
        assertEquals("SmartMistakeBook", readBack.contentProducer)
        assertEquals("figure-1", readBack.produceId)
    }

    @Test
    fun `xml special characters in the figure id survive the round trip`() {
        val readBack = requireNotNull(
            parseAigcXmpPacket(aigcXmpPacket(aigcJson(provenance("clean-a&b<c>d")))),
        ) { "XML 特殊字符必须转义后完整读回" }
        assertEquals("clean-a&b<c>d", readBack.produceId)
    }

    @Test
    fun `a missing foreign or malformed packet reads back as no label`() {
        assertNull("无包不得有标识", parseAigcXmpPacket(null))
        assertNull(
            "别家 XMP（无 TC260 标记）不得读出标识",
            parseAigcXmpPacket(
                "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
                    "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF/></x:xmpmeta>" +
                    "<?xpacket end=\"w\"?>",
            ),
        )
        assertNull("只有开标记没有闭标记不得读出标识", parseAigcXmpPacket("<TC260:AIGC>{oops"))
        assertNull(
            "缺任一必填字段不得读出标识（三字段全在才算）",
            parseAigcXmpPacket(
                aigcXmpPacket("{\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\"}"),
            ),
        )
        assertNull(
            "截断 JSON 不得读出标识",
            parseAigcXmpPacket(
                aigcXmpPacket("{\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\",\"ProduceID\":\"x"),
            ),
        )
    }

    @Test
    fun `a same-named tag in a foreign namespace does not count as our label`() {
        val json = "{\"Label\":\"1\",\"ContentProducer\":\"SmartMistakeBook\",\"ProduceID\":\"forged\"}"
        val forged = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
            "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:TC260=\"https://example.invalid/ns/AIGC/\">" +
            "<TC260:AIGC>$json</TC260:AIGC>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"
        assertNull(
            "同名字面 tag 但 xmlns:TC260 不是官方 URI：必须 fail-closed（不得误判为 AI）",
            parseAigcXmpPacket(forged),
        )

        val missingNamespace = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
            "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\">" +
            "<TC260:AIGC>$json</TC260:AIGC>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"
        assertNull(
            "没有任何 xmlns:TC260 声明：必须 fail-closed",
            parseAigcXmpPacket(missingNamespace),
        )
    }

    @Test
    fun `xmp-only read-back recovers the attribute and id but not model or purpose`() {
        val resolved = resolveGeneratedFigureMetadata(
            description = null,
            comment = null,
            software = null,
            artist = null,
            aigc = AigcXmpFields(
                label = "1",
                contentProducer = "SmartMistakeBook",
                produceId = "figure-xmp-only",
            ),
        )

        assertTrue("XMP 的 Label=1 足以判定 AI 生成", resolved.aiGenerated)
        assertEquals("ProduceID 必须在 EXIF 缺失时补上编号", "figure-xmp-only", resolved.figureId)
        assertNull("TC260 三字段不含 model，不得编造", resolved.model)
        assertNull("TC260 三字段不含 purpose，不得编造", resolved.purpose)
    }

    @Test
    fun `exif fields win over xmp and missing metadata stays fail-closed`() {
        val exifWins = resolveGeneratedFigureMetadata(
            description = AI_GENERATED_IMAGE_DESCRIPTION,
            comment = "AI-GENERATED;kind=CLEAN_REDRAW;model=gpt-image-2;id=figure-exif",
            software = "SmartMistakeBook;model=gpt-image-2",
            artist = "figure-exif",
            aigc = AigcXmpFields("1", "SmartMistakeBook", "figure-xmp"),
        )
        assertEquals("figure-exif", exifWins.figureId)
        assertEquals("gpt-image-2", exifWins.model)
        assertEquals("CLEAN_REDRAW", exifWins.purpose)

        val empty = resolveGeneratedFigureMetadata(null, null, null, null, null)
        assertFalse(empty.aiGenerated)
        assertNull(empty.model)
        assertNull(empty.figureId)
        assertNull(empty.purpose)
    }

    @Test
    fun `only label one counts as ai generated`() {
        assertTrue(isAigcAiGeneratedLabel("1"))
        assertFalse("Label \"2\" 是传播平台语义（可能），不构成本方生成判定", isAigcAiGeneratedLabel("2"))
        assertFalse(isAigcAiGeneratedLabel("3"))
        assertFalse(isAigcAiGeneratedLabel(null))
        assertFalse(isAigcAiGeneratedLabel(""))
    }
}
