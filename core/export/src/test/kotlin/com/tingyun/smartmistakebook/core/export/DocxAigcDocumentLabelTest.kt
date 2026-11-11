package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.pdf.PdfAigcMetadata
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Document

/**
 * DOCX 文档级隐式标识（20258A §6.1 的对称缺口；KD-75 关闭边界里"另立条目"的那一条）。
 *
 * 依据是 **TC260-PG-20258A §6.1** 原文（本批从官方 PDF 重取，sha256
 * `6bf6c1189208856099bfbf648bdf386da288b1df00f430502b395b874dbf3b0e`）：
 * 存储路径为 `docProps/custom.xml` 的元素 `Properties/property`；新建 `property` 其属性
 * `name` 为 `"AIGC"`；子元素 `lpwstr` 的值是七键 JSON 字符串。
 *
 * 判据与 PDF 同一次 `PdfAigcContentPolicy.shouldLabel`：含 AI 图或模型解析文本才写；
 * 纯用户内容**不写**（《办法》第十条不得伪造标识）。
 *
 * 同时钉住**显式**那一半：含模型文本的 Word 文件正文第一屏有一行用户可见的
 * "本文件含 AI 生成内容"，不依赖读者去翻文档属性。
 */
class DocxAigcDocumentLabelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `model text docx carries the AIGC custom property with the seven-field json`() {
        val input = input(hasModelParsedText = true)
        val file = renderToFile(input)
        maybeExportEvidence(file, "model-text-labeled.docx")

        val parts = readZip(file)
        assertTrue(
            "含模型文本的 Word 必须写 docProps/custom.xml（20258A §6.1）",
            parts.containsKey(CUSTOM_PROPERTIES_PART),
        )

        val expectedJson = PdfAigcMetadata.document(input.inputSha256).toJson()
        val property = customProperty(parts.getValue(CUSTOM_PROPERTIES_PART), name = "AIGC")
        assertNotNull("custom.xml 必须有 name=\"AIGC\" 的 property", property)
        assertEquals(expectedJson, property!!.textContent)
        // 七键顺序与 PDF 侧同一 builder 逐字节一致（Label=1 / ProduceID=输入指纹）。
        assertTrue(expectedJson.contains("\"Label\":\"1\""))
        assertTrue(expectedJson.contains("\"ProduceID\":\"${input.inputSha256}\""))

        val contentTypes = parts.getValue(MinimalDocxWriter.CONTENT_TYPES_PART).decodeToString()
        assertTrue(
            "custom.xml 必须有 Content_Types Override",
            contentTypes.contains(
                "<Override PartName=\"/$CUSTOM_PROPERTIES_PART\" " +
                    "ContentType=\"application/vnd.openxmlformats-officedocument.custom-properties+xml\"/>",
            ),
        )

        val packageRels = parts.getValue(MinimalDocxWriter.PACKAGE_RELS_PART).decodeToString()
        assertTrue(
            "包关系必须声明 custom-properties 关系，Word 才认得这个部件",
            packageRels.contains("relationships/custom-properties") &&
                packageRels.contains("Target=\"$CUSTOM_PROPERTIES_PART\""),
        )

        // 显式可见：正文里有一行说明，而不是只在元数据里。
        assertTrue(
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART))
                .contains("本文件含 AI 生成内容"),
        )
    }

    @Test
    fun `pure user docx stays byte free of an AIGC property and of the visible notice`() {
        val file = renderToFile(input(hasModelParsedText = false))
        maybeExportEvidence(file, "pure-user-unlabeled.docx")

        val parts = readZip(file)
        assertFalse(
            "纯用户内容不得伪标（《办法》第十条）",
            parts.containsKey(CUSTOM_PROPERTIES_PART),
        )
        assertFalse(
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART))
                .contains("本文件含 AI 生成内容"),
        )
        assertFalse(
            parts.getValue(MinimalDocxWriter.CONTENT_TYPES_PART).decodeToString()
                .contains("custom-properties"),
        )
    }

    // ---- fixtures ----

    private fun renderToFile(input: MistakePdfExportInput): File {
        val file = temporaryFolder.newFile("aigc-${System.nanoTime()}.docx")
        DocxMistakeRenderer.render(input, file)
        return file
    }

    /**
     * 证据出口：设置 `SMBK_AIGC_EVIDENCE_DIR` 时把产物复制过去，供**宿主侧独立读者**
     * （Python zipfile + XML 解析，不经本项目任何代码）读回。未设置时什么都不做。
     */
    private fun maybeExportEvidence(file: File, name: String) {
        val directory = System.getenv("SMBK_AIGC_EVIDENCE_DIR")?.takeIf(String::isNotBlank)
            ?: return
        val target = File(directory).apply { mkdirs() }
        file.copyTo(File(target, name), overwrite = true)
    }

    private fun input(hasModelParsedText: Boolean): MistakePdfExportInput =
        MistakePdfExportInput(
            errorBookEntryId = "entry-aigc",
            problemId = "problem-aigc",
            problemRevisionId = "revision-1",
            revisionNumber = 1,
            title = "函数单调区间",
            subject = "MATH",
            subtitle = "MATH · 第 1 版",
            documentTitle = "题面",
            blocks = listOf(MistakePdfBlock.Paragraph(id = "p1", text = "题干文字")),
            questionDocumentSha256 = "a".repeat(64),
            hasModelParsedText = hasModelParsedText,
            inputSha256 = "c".repeat(64),
            layout = MistakePdfLayout.DEFAULT,
        )

    private fun readZip(file: File): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(file.readBytes())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        return parts
    }

    /** 命名空间感知地找 `property[@name=…]/lpwstr`，不靠字符串搜 XML。 */
    private fun customProperty(bytes: ByteArray, name: String): org.w3c.dom.Node? {
        val document: Document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
        assertEquals(CUSTOM_PROPERTIES_NS, document.documentElement.namespaceURI)
        assertEquals("Properties", document.documentElement.localName)
        val properties = document.getElementsByTagNameNS(CUSTOM_PROPERTIES_NS, "property")
        for (index in 0 until properties.length) {
            val property = properties.item(index)
            if (property.attributes?.getNamedItem("name")?.nodeValue == name) {
                val values = (property as org.w3c.dom.Element)
                    .getElementsByTagNameNS(VT_NS, "lpwstr")
                return values.item(0)
            }
        }
        return null
    }

    private fun documentTexts(documentXml: ByteArray): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml))
        val nodes = document.getElementsByTagNameNS(W_NS, "t")
        return (0 until nodes.length).map { index -> nodes.item(index).textContent }
    }

    private companion object {
        const val CUSTOM_PROPERTIES_PART = "docProps/custom.xml"
        const val CUSTOM_PROPERTIES_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
        const val VT_NS = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"
        const val W_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
