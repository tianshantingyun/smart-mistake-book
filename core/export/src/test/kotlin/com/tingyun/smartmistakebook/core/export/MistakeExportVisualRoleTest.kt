package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 4E WG-07：导出按**视觉角色**取图（T2a 角色落盘之后）。
 *
 * 钉住四件事：
 * 1. 题目区：CLEAN_IMAGE 优先，其次 QUESTION_DIAGRAM；讲解图（TUTOR_EXPLANATION）永不进题目区。
 * 2. 解析区：只收 TUTOR_EXPLANATION，且只有 `layout.includeSolution=true` 才进输入并排版；
 *    没有讲解图时仍按原口径 fail-closed 跳过（不留占位脏字）。
 * 3. 指纹：进过渲染的每一张图，其 role + 资产位置 + sha 都进 `inputSha256`；不渲染的不进。
 * 4. DOCX 与 PDF 同一过滤；必图（题目图/解析图/卷面图块）读不出或记录不符时 fail-closed，
 *    不再静默省略。旧版单 CLEAN_IMAGE 输入的输出逐位不变。
 */
class MistakeExportVisualRoleTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val measure = PdfTextMeasure { text, start, end, style ->
        (end - start) * style.textSize
    }

    // ---- 题目区：CLEAN_IMAGE 优先，其次 QUESTION_DIAGRAM ----

    @Test
    fun `the question area prefers the clean image then falls back to the question diagram`() {
        val clean = asset(VisualRole.CLEAN_IMAGE, "clean", "file:///clean.png", sha = 'a')
        val diagram = asset(VisualRole.QUESTION_DIAGRAM, "diagram", "file:///diagram.png", sha = 'b')
        val explanation = asset(VisualRole.TUTOR_EXPLANATION, "explain", "file:///explain.png", sha = 'c')

        val both = eligible(readyState("entry", source = present(explanation, diagram, clean)))
        assertEquals("file:///clean.png", both.cleanImageLocalUri)
        assertEquals(VisualRole.CLEAN_IMAGE.name, both.questionImageRole)
        assertEquals("a".repeat(64), both.cleanImageSha256)

        val withoutClean = eligible(readyState("entry", source = present(explanation, diagram)))
        assertEquals("file:///diagram.png", withoutClean.cleanImageLocalUri)
        assertEquals(VisualRole.QUESTION_DIAGRAM.name, withoutClean.questionImageRole)

        val explanationOnly = eligible(readyState("entry", source = present(explanation)))
        assertNull("讲解图不是题目图", explanationOnly.cleanImageLocalUri)
        assertNull(explanationOnly.questionImageRole)

        // 有记录但文件不可用：不占着题目区的位置，继续退到题图。
        val unavailableClean = clean.copy(location = MistakeSourceLocation.Unavailable)
        val fallback = eligible(readyState("entry", source = present(unavailableClean, diagram)))
        assertEquals("file:///diagram.png", fallback.cleanImageLocalUri)
        assertEquals(VisualRole.QUESTION_DIAGRAM.name, fallback.questionImageRole)
    }

    @Test
    fun `the solution area only accepts the tutor explanation role and only when requested`() {
        val clean = asset(VisualRole.CLEAN_IMAGE, "clean", "file:///clean.png", sha = 'a')
        val early = asset(
            VisualRole.TUTOR_EXPLANATION,
            "explain-early",
            "file:///explain-early.png",
            sha = 'b',
            createdAt = 10L,
        )
        val late = asset(
            VisualRole.TUTOR_EXPLANATION,
            "explain-late",
            "file:///explain-late.png",
            sha = 'c',
            createdAt = 20L,
        )
        val evidence = asset(VisualRole.SOURCE_EVIDENCE, "evidence", "file:///evidence.png", sha = 'd')
        val diagram = asset(VisualRole.QUESTION_DIAGRAM, "diagram", "file:///diagram.png", sha = 'e')
        val source = present(late, clean, evidence, early, diagram)

        val requested = eligible(
            readyState("entry", source = source),
            MistakePdfLayout(includeSolution = true),
        )
        assertEquals(
            listOf("file:///explain-early.png", "file:///explain-late.png"),
            requested.solutionImages.map(MistakePdfExportImage::localUri),
        )
        assertEquals(
            setOf(VisualRole.TUTOR_EXPLANATION.name),
            requested.solutionImages.map(MistakePdfExportImage::role).toSet(),
        )

        val notRequested = eligible(readyState("entry", source = source))
        assertTrue(
            "没请求解析区时讲解图不得进导出输入（否则会以任何形式漏进渲染）",
            notRequested.solutionImages.isEmpty(),
        )
    }

    // ---- 指纹 ----

    @Test
    fun `the input fingerprint covers every rendered role asset and sha`() {
        val layout = MistakePdfLayout(includeSolution = true)
        val clean = asset(VisualRole.CLEAN_IMAGE, "clean", "file:///clean.png", sha = 'a')
        val explanation = asset(
            VisualRole.TUTOR_EXPLANATION,
            "explain",
            "file:///explain.png",
            sha = 'b',
        )
        fun fingerprint(source: MistakeSourceSet): String =
            eligible(readyState("entry", source = source), layout).inputSha256

        val base = fingerprint(present(clean, explanation))
        assertEquals(
            "同一份输入重跑必须命中同一把键",
            base,
            fingerprint(present(clean, explanation)),
        )
        assertNotEquals(
            "解析图字节变了必须换键",
            base,
            fingerprint(present(clean, explanation.copy(contentSha256 = "f".repeat(64)))),
        )
        assertNotEquals(
            "解析图换了资产位置必须换键",
            base,
            fingerprint(
                present(
                    clean,
                    explanation.copy(location = MistakeSourceLocation.Available("file:///moved.png")),
                ),
            ),
        )
        assertNotEquals(
            "角色变了（同一张图当题目图还是题图）必须换键",
            base,
            fingerprint(
                present(
                    clean.copy(role = VisualRole.QUESTION_DIAGRAM.name),
                    explanation,
                ),
            ),
        )

        // 不请求解析区时，讲解图既不渲染也不进键（不让没进产物的资产白换缓存键）。
        // 两侧必须用同一个 layout 算键：canonicalForm 含 includeSolution，拿
        // includeSolution=true 的键去比默认版式，比的是两种渲染契约而不是"多一张图"。
        assertEquals(
            eligible(readyState("entry", source = present(clean)), MistakePdfLayout.DEFAULT)
                .inputSha256,
            eligible(readyState("entry", source = present(clean, explanation)), MistakePdfLayout.DEFAULT)
                .inputSha256,
        )
    }

    // ---- PDF 计划：旧输出逐位一致；解析区只在请求时出现 ----

    @Test
    fun `a legacy single clean image plans exactly as before`() {
        val legacyUri = "file:///legacy-clean.png"
        val roleAware = eligible(
            readyState(
                "entry",
                source = present(asset(VisualRole.CLEAN_IMAGE, "clean", legacyUri, sha = 'a')),
            ),
        )
        val legacyShape = withoutRole(roleAware)
        val size = PdfImageSize(widthPx = 200, heightPx = 100)

        val expected = listOf(
            "line/TITLE/28.00/false/0/测试题",
            "line/META/16.00/false/0/MATH · 第 1 版",
            "line/SPACER/8.00/false/0/",
            "line/SECTION_HEADING/22.00/false/0/干净题面",
            "image/249.50/499.00/0",
            "line/SPACER/8.00/false/0/",
            "line/BODY/18.00/false/0/题干",
            "line/SPACER/8.00/false/0/",
        )
        val roleAwarePlan = MistakePdfPagePlanner.plan(roleAware, measure, size)
        assertEquals(expected, describe(roleAwarePlan))
        assertEquals(
            "旧调用方（不携带角色/解析图）的计划必须与角色感知输入逐项一致",
            describe(MistakePdfPagePlanner.plan(legacyShape, measure, size)),
            describe(roleAwarePlan),
        )
        assertTrue(roleAwarePlan.skipped.isEmpty())
    }

    @Test
    fun `the solution section is drawn after the question blocks only when requested`() {
        val clean = asset(VisualRole.CLEAN_IMAGE, "clean", "file:///clean.png", sha = 'a')
        val explanation = asset(
            VisualRole.TUTOR_EXPLANATION,
            "explain",
            "file:///explain.png",
            sha = 'b',
        )
        val sizes = mapOf(
            "file:///clean.png" to PdfImageSize(widthPx = 200, heightPx = 100),
            "file:///explain.png" to PdfImageSize(widthPx = 100, heightPx = 50),
        )

        val requested = eligible(
            readyState("entry", source = present(clean, explanation)),
            MistakePdfLayout(includeSolution = true),
        )
        val plan = MistakePdfPagePlanner.plan(
            requested,
            measure,
            sizes["file:///clean.png"],
            sizes,
        )
        val items = plan.flattened()
        assertEquals(
            listOf("file:///clean.png", "file:///explain.png"),
            items.filterIsInstance<PdfPlannedItem.Image>().map(PdfPlannedItem.Image::localUri),
        )
        val texts = items.filterIsInstance<PdfPlannedItem.Line>().map(PdfPlannedItem.Line::text)
        assertEquals(1, texts.count { it == "解析" })
        assertTrue("讲解图必须排在题目区之后", texts.indexOf("解析") > texts.indexOf("题干"))
        assertTrue(
            "真的排了解析图就不能再登记“跳过解析区”",
            plan.skipped.isEmpty(),
        )

        // 未请求：同一份来源也不排解析区，且不要求解析图尺寸（拿不到尺寸也不该报错）。
        val notRequested = eligible(readyState("entry", source = present(clean, explanation)))
        val plain = MistakePdfPagePlanner.plan(notRequested, measure, sizes["file:///clean.png"])
        assertFalse(
            plain.flattened().any { item ->
                item is PdfPlannedItem.Line && item.text == "解析"
            },
        )
        assertEquals(
            listOf("file:///clean.png"),
            plain.flattened().filterIsInstance<PdfPlannedItem.Image>().map(PdfPlannedItem.Image::localUri),
        )
        assertTrue(plain.skipped.isEmpty())
    }

    @Test
    fun `a requested solution section without explanation images is still skipped fail closed`() {
        val clean = asset(VisualRole.CLEAN_IMAGE, "clean", "file:///clean.png", sha = 'a')
        val input = eligible(
            readyState("entry", source = present(clean)),
            MistakePdfLayout(includeSolution = true),
        )

        val plan = MistakePdfPagePlanner.plan(
            input,
            measure,
            PdfImageSize(widthPx = 200, heightPx = 100),
        )

        assertEquals(
            listOf(
                PdfLayoutSkip(
                    feature = com.tingyun.smartmistakebook.core.model.MistakePdfLayoutFeature
                        .SOLUTION_SECTION,
                    reason = MistakePdfLayoutSkipReason.UNSUPPORTED_LAYOUT_FEATURE,
                ),
            ),
            plan.skipped,
        )
        assertFalse(
            plan.flattened().any { item ->
                item is PdfPlannedItem.Line && item.text == "解析"
            },
        )
    }

    @Test
    fun `explanation images carried by an input never leak into the question area`() {
        val input = manualInput(
            blocks = listOf(MistakePdfBlock.Paragraph(id = "stem", text = "题干")),
            cleanImageUri = "file:///clean.png",
            questionImageRole = VisualRole.CLEAN_IMAGE.name,
            solutionImages = listOf(
                MistakePdfExportImage(
                    role = VisualRole.TUTOR_EXPLANATION.name,
                    localUri = "file:///explain.png",
                    contentSha256 = "b".repeat(64),
                    byteSize = 1_024L,
                ),
            ),
            layout = MistakePdfLayout.DEFAULT,
        )

        val plan = MistakePdfPagePlanner.plan(
            input,
            measure,
            PdfImageSize(widthPx = 200, heightPx = 100),
            mapOf(
                "file:///clean.png" to PdfImageSize(widthPx = 200, heightPx = 100),
                "file:///explain.png" to PdfImageSize(widthPx = 100, heightPx = 50),
            ),
        )

        assertEquals(
            listOf("file:///clean.png"),
            plan.flattened().filterIsInstance<PdfPlannedItem.Image>().map(PdfPlannedItem.Image::localUri),
        )
        assertFalse(
            plan.flattened().any { item ->
                item is PdfPlannedItem.Line && item.text == "解析"
            },
        )
    }

    // ---- DOCX：同过滤 + 必图 fail-closed + 旧输出逐位一致 ----

    @Test
    fun `docx embeds the question image and every requested explanation image`() {
        val clean = png(width = 4, height = 2)
        val early = png(width = 2, height = 2)
        val late = png(width = 3, height = 2)
        val cleanFile = temporaryFolder.newFile("clean.png").apply { writeBytes(clean) }
        val earlyFile = temporaryFolder.newFile("explain-early.png").apply { writeBytes(early) }
        val lateFile = temporaryFolder.newFile("explain-late.png").apply { writeBytes(late) }

        val input = eligible(
            readyState(
                "entry",
                source = present(
                    fileAsset(VisualRole.CLEAN_IMAGE, "clean", cleanFile, clean),
                    fileAsset(VisualRole.TUTOR_EXPLANATION, "explain-early", earlyFile, early, createdAt = 10L),
                    fileAsset(VisualRole.TUTOR_EXPLANATION, "explain-late", lateFile, late, createdAt = 20L),
                ),
            ),
            MistakePdfLayout(includeSolution = true),
        )

        val parts = readZip(renderDocx(input, "solution.docx"))

        assertArrayEquals(clean, parts.getValue("word/media/image1.png"))
        assertArrayEquals(early, parts.getValue("word/media/image2.png"))
        assertArrayEquals(late, parts.getValue("word/media/image3.png"))
        val documentXml = parts.getValue(MinimalDocxWriter.DOCUMENT_PART).decodeToString()
        assertTrue(documentXml.contains("r:embed=\"rId1\""))
        assertTrue(documentXml.contains("r:embed=\"rId2\""))
        assertTrue(documentXml.contains("r:embed=\"rId3\""))
        val texts = documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART))
        assertTrue(texts.indexOf("干净题面") >= 0)
        assertTrue("解析区必须排在题目内容之后", texts.indexOf("解析") > texts.indexOf("题干"))
    }

    @Test
    fun `docx does not render or embed explanation images when the solution area is off`() {
        val clean = png(width = 4, height = 2)
        val explanation = png(width = 2, height = 2)
        val cleanFile = temporaryFolder.newFile("only-clean.png").apply { writeBytes(clean) }
        val explanationFile = temporaryFolder.newFile("unused-explain.png")
            .apply { writeBytes(explanation) }

        val input = eligible(
            readyState(
                "entry",
                source = present(
                    fileAsset(VisualRole.CLEAN_IMAGE, "clean", cleanFile, clean),
                    fileAsset(VisualRole.TUTOR_EXPLANATION, "explain", explanationFile, explanation),
                ),
            ),
        )

        val parts = readZip(renderDocx(input, "no-solution.docx"))
        assertEquals(
            setOf("word/media/image1.png"),
            parts.keys.filter { it.startsWith("word/media/") }.toSet(),
        )
        assertArrayEquals(clean, parts.getValue("word/media/image1.png"))
        assertFalse(
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART)).contains("解析"),
        )

        // 二道闸：即使输入自己带着解析图（例如手写输入），没请求解析区也不嵌、不排。
        val carried = manualInput(
            blocks = listOf(MistakePdfBlock.Paragraph(id = "stem", text = "题干")),
            solutionImages = listOf(
                MistakePdfExportImage(
                    role = VisualRole.TUTOR_EXPLANATION.name,
                    localUri = explanationFile.toURI().toString(),
                    contentSha256 = sha256Hex(explanation),
                    byteSize = explanation.size.toLong(),
                ),
            ),
            layout = MistakePdfLayout.DEFAULT,
        )
        val carriedParts = readZip(renderDocx(carried, "carried-but-off.docx"))
        assertFalse(carriedParts.keys.any { it.startsWith("word/media/") })
        assertFalse(
            documentTexts(carriedParts.getValue(MinimalDocxWriter.DOCUMENT_PART)).contains("解析"),
        )
    }

    @Test
    fun `docx fails closed when a required question or explanation image is missing`() {
        val missing = File(temporaryFolder.root, "gone.png").toURI().toString()

        val missingQuestion = eligible(
            readyState(
                "entry",
                source = present(asset(VisualRole.CLEAN_IMAGE, "clean", missing, sha = 'a')),
            ),
        )
        val questionFailure = assertThrows(MistakePdfExportException::class.java) {
            DocxMistakeRenderer.render(
                missingQuestion,
                File(temporaryFolder.root, "missing-question.docx"),
            )
        }
        assertEquals(MistakePdfExportFailure.GENERATED_PDF_INVALID, questionFailure.failure)
        assertFalse(File(temporaryFolder.root, "missing-question.docx").exists())

        val missingExplanation = eligible(
            readyState(
                "entry",
                source = present(
                    fileAsset(
                        VisualRole.CLEAN_IMAGE,
                        "clean",
                        temporaryFolder.newFile("present.png").apply { writeBytes(png(2, 2)) },
                        png(2, 2),
                    ),
                    asset(VisualRole.TUTOR_EXPLANATION, "explain", missing, sha = 'b'),
                ),
            ),
            MistakePdfLayout(includeSolution = true),
        )
        val explanationFailure = assertThrows(MistakePdfExportException::class.java) {
            DocxMistakeRenderer.render(
                missingExplanation,
                File(temporaryFolder.root, "missing-explanation.docx"),
            )
        }
        assertEquals(MistakePdfExportFailure.GENERATED_PDF_INVALID, explanationFailure.failure)
        assertFalse(File(temporaryFolder.root, "missing-explanation.docx").exists())
    }

    @Test
    fun `docx fails closed when a required image no longer matches its recorded bytes`() {
        val recorded = png(width = 4, height = 2)
        val replacement = png(width = 8, height = 6)
        val swapped = temporaryFolder.newFile("swapped.png").apply { writeBytes(replacement) }
        val input = eligible(
            readyState(
                "entry",
                source = present(
                    MistakeSourceAsset(
                        role = VisualRole.CLEAN_IMAGE.name,
                        sourceAssetId = "clean",
                        contentSha256 = sha256Hex(recorded),
                        mimeType = "image/png",
                        byteSize = recorded.size.toLong(),
                        width = 100,
                        height = 100,
                        sourceType = "GENERATED_FIGURE",
                        createdAtEpochMillis = 1L,
                        location = MistakeSourceLocation.Available(swapped.toURI().toString()),
                    ),
                ),
            ),
        )

        assertThrows(MistakePdfExportException::class.java) {
            DocxMistakeRenderer.render(input, File(temporaryFolder.root, "swapped.docx"))
        }
    }

    @Test
    fun `docx never renders an explanation image block inside the question area`() {
        val explanation = png(width = 2, height = 2)
        val explanationFile = temporaryFolder.newFile("block-explain.png")
            .apply { writeBytes(explanation) }
        val input = manualInput(
            blocks = listOf(
                MistakePdfBlock.Paragraph(id = "stem", text = "题干"),
                MistakePdfBlock.Image(
                    id = "explain-image",
                    localUri = explanationFile.toURI().toString(),
                    title = "讲解过程图",
                    role = VisualRole.TUTOR_EXPLANATION.name,
                ),
            ),
            layout = MistakePdfLayout.DEFAULT,
        )

        val parts = readZip(renderDocx(input, "leak-check.docx"))

        assertFalse(parts.keys.any { it.startsWith("word/media/") })
        assertFalse(
            documentTexts(parts.getValue(MinimalDocxWriter.DOCUMENT_PART)).contains("讲解过程图"),
        )
    }

    @Test
    fun `a legacy single CLEAN_IMAGE document is byte identical`() {
        val clean = png(width = 4, height = 2)
        val cleanFile = temporaryFolder.newFile("legacy.png").apply { writeBytes(clean) }
        val roleAware = eligible(
            readyState(
                "entry",
                source = present(fileAsset(VisualRole.CLEAN_IMAGE, "clean", cleanFile, clean)),
            ),
        )
        val legacyShape = withoutRole(roleAware)

        val roleAwareFile = renderDocx(roleAware, "role-aware.docx")
        val legacyFile = renderDocx(legacyShape, "legacy-shape.docx")

        assertArrayEquals(legacyFile.readBytes(), roleAwareFile.readBytes())
        assertTrue(
            documentTexts(readZip(legacyFile)
                .getValue(MinimalDocxWriter.DOCUMENT_PART)).contains("干净题面"),
        )
    }

    // ---- 卷面副本：保留全部视觉引用 ----

    @Test
    fun `a paper copy keeps every question's image role and explanation images`() {
        val layout = MistakePdfLayout(includeSolution = true)
        val states = listOf(
            readyState(
                "entry-a",
                source = present(
                    asset(VisualRole.CLEAN_IMAGE, "a-clean", "file:///q-a-clean.png", sha = 'a'),
                    asset(
                        VisualRole.TUTOR_EXPLANATION,
                        "a-explain",
                        "file:///explain-a.png",
                        sha = 'b',
                    ),
                ),
            ),
            readyState(
                "entry-b",
                source = present(
                    asset(
                        VisualRole.QUESTION_DIAGRAM,
                        "b-diagram",
                        "file:///q-b-diagram.png",
                        sha = 'c',
                    ),
                    asset(
                        VisualRole.TUTOR_EXPLANATION,
                        "b-explain",
                        "file:///explain-b.png",
                        sha = 'd',
                    ),
                ),
            ),
        )
        val inputs = states.map { state ->
            (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible).input
        }
        val direct = MistakePdfBatchEligibility.check(states, layout) as
            MistakePdfBatchEligibilityResult.Eligible
        assertEquals(
            listOf("file:///explain-a.png", "file:///explain-b.png"),
            direct.input.solutionImages.map(MistakePdfExportImage::localUri),
        )

        val edit = MistakePdfPaperEdit.fromInputs(inputs, layout)
        val unedited = edit.toDraft()
        assertEquals(direct.input.solutionImages, unedited.input.solutionImages)
        assertEquals(direct.input.inputSha256, unedited.input.inputSha256)

        // 编辑文字后：两道题的题目图与讲解图都还在，题目图带自己的角色。
        val edited = edit.withBlockText("entry-a", "stem-entry-a", "改过的题干").toDraft()
        assertEquals(2, edited.input.solutionImages.size)
        val imageBlocks = edited.input.blocks.filterIsInstance<MistakePdfBlock.Image>()
        assertEquals(
            listOf(VisualRole.CLEAN_IMAGE.name, VisualRole.QUESTION_DIAGRAM.name),
            imageBlocks.map(MistakePdfBlock.Image::role),
        )
        assertEquals(
            listOf("file:///q-a-clean.png", "file:///q-b-diagram.png"),
            imageBlocks.map(MistakePdfBlock.Image::localUri),
        )
        assertEquals("第 1 题 · 干净题面", imageBlocks[0].title)
        assertEquals("第 2 题 · 题图", imageBlocks[1].title)

        // 删题只去掉那道题的视觉引用，不在附录里留下"幽灵图"。
        val removed = edit.withQuestionRemoved("entry-b").toDraft()
        assertEquals(
            listOf("file:///explain-a.png"),
            removed.input.solutionImages.map(MistakePdfExportImage::localUri),
        )
    }

    @Test
    fun `the draft store keeps the question image role and every explanation image`() {
        val layout = MistakePdfLayout(includeSolution = true)
        val input = eligible(
            readyState(
                "entry-a",
                source = present(
                    asset(VisualRole.CLEAN_IMAGE, "a-clean", "file:///q-a-clean.png", sha = 'a'),
                    asset(
                        VisualRole.TUTOR_EXPLANATION,
                        "a-explain",
                        "file:///explain-a.png",
                        sha = 'b',
                    ),
                ),
            ),
            layout,
        )
        // 直接落一份单题快照：题目图在顶层字段里（batch 形态则在内联图块里，二者都要能存）。
        val draft = MistakePaperDraft(
            input = input,
            edited = false,
            questionCount = 1,
            sourceInputSha256 = input.inputSha256,
        )
        val store = MistakePaperDraftStore(temporaryFolder.newFolder("drafts-role"))

        store.write("draft-role", draft)
        val read = requireNotNull(store.read("draft-role"))

        assertEquals(input.questionImageRole, read.input.questionImageRole)
        assertEquals(input.cleanImageLocalUri, read.input.cleanImageLocalUri)
        assertEquals(input.cleanImageSha256, read.input.cleanImageSha256)
        assertEquals(input.cleanImageByteSize, read.input.cleanImageByteSize)
        assertEquals(input.solutionImages, read.input.solutionImages)
        assertEquals(draft.input.inputSha256, read.input.inputSha256)
    }

    // ---- helpers ----

    private fun eligible(
        state: MistakeDetailState.Ready,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput =
        (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible).input

    private fun present(vararg assets: MistakeSourceAsset): MistakeSourceSet =
        MistakeSourceSet.Present(assets.toList())

    private fun asset(
        role: VisualRole,
        sourceAssetId: String,
        uri: String,
        sha: Char = 'a',
        createdAt: Long = 1L,
        byteSize: Long = 1_024L,
    ): MistakeSourceAsset = MistakeSourceAsset(
        role = role.name,
        sourceAssetId = sourceAssetId,
        contentSha256 = sha.toString().repeat(64),
        mimeType = "image/png",
        byteSize = byteSize,
        width = 100,
        height = 100,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = createdAt,
        location = MistakeSourceLocation.Available(uri),
    )

    private fun fileAsset(
        role: VisualRole,
        sourceAssetId: String,
        file: File,
        bytes: ByteArray,
        createdAt: Long = 1L,
    ): MistakeSourceAsset = MistakeSourceAsset(
        role = role.name,
        sourceAssetId = sourceAssetId,
        contentSha256 = sha256Hex(bytes),
        mimeType = "image/png",
        byteSize = bytes.size.toLong(),
        width = 100,
        height = 100,
        sourceType = "GENERATED_FIGURE",
        createdAtEpochMillis = createdAt,
        location = MistakeSourceLocation.Available(file.toURI().toString()),
    )

    private fun readyState(entryId: String, source: MistakeSourceSet): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", "题干")
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = entryId,
                    problemId = "problem-$entryId",
                    problemRevisionId = "revision-$entryId",
                    revisionNumber = 1,
                    title = "测试题",
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = source,
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = "测试题",
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "source-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun manualInput(
        blocks: List<MistakePdfBlock>,
        cleanImageUri: String? = null,
        questionImageRole: String? = null,
        solutionImages: List<MistakePdfExportImage> = emptyList(),
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = "entry-manual",
        problemId = "problem-manual",
        problemRevisionId = "revision-manual",
        revisionNumber = 1,
        title = "测试题",
        subject = "MATH",
        subtitle = "MATH · 第 1 版",
        documentTitle = null,
        blocks = blocks,
        questionDocumentSha256 = "0".repeat(64),
        inputSha256 = "1".repeat(64),
        cleanImageLocalUri = cleanImageUri,
        cleanImageSha256 = cleanImageUri?.let { "a".repeat(64) },
        cleanImageByteSize = cleanImageUri?.let { 1_024L },
        questionImageRole = questionImageRole,
        solutionImages = solutionImages,
        layout = layout,
    )

    /** 同一份输入，去掉 WG-07 新增的角色/解析图字段（模拟旧调用方）。 */
    private fun withoutRole(input: MistakePdfExportInput): MistakePdfExportInput =
        MistakePdfExportInput(
            errorBookEntryId = input.errorBookEntryId,
            problemId = input.problemId,
            problemRevisionId = input.problemRevisionId,
            revisionNumber = input.revisionNumber,
            title = input.title,
            subject = input.subject,
            subtitle = input.subtitle,
            documentTitle = input.documentTitle,
            blocks = input.blocks,
            questionDocumentSha256 = input.questionDocumentSha256,
            inputSha256 = input.inputSha256,
            maxPages = input.maxPages,
            maxRenderedLines = input.maxRenderedLines,
            maxPdfBytes = input.maxPdfBytes,
            cleanImageLocalUri = input.cleanImageLocalUri,
            cleanImageSha256 = input.cleanImageSha256,
            cleanImageByteSize = input.cleanImageByteSize,
            layout = input.layout,
            userNote = input.userNote,
        )

    private fun describe(plan: PdfPlan): List<String> = plan.pages.flatMap { page ->
        page.map { item ->
            when (item) {
                is PdfPlannedItem.Line ->
                    "line/${item.style.name}/${format(item.height)}/${item.keepWithNext}/" +
                        "${item.column}/${item.text}"
                is PdfPlannedItem.Formula ->
                    "formula/${item.latex}/${format(item.fontSizePx)}/${format(item.height)}/${item.column}"
                is PdfPlannedItem.Figure ->
                    "figure/${item.block.id}/${item.block.schema::class.simpleName}/" +
                        "${format(item.height)}/${format(item.width)}/${item.column}"
                is PdfPlannedItem.Image ->
                    "image/${format(item.height)}/${format(item.width)}/${item.column}"
                is PdfPlannedItem.AnswerBlank ->
                    "blank/${item.rows}/${format(item.height)}/${item.column}"
            }
        }
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)

    private var renderCounter = 0

    private fun renderDocx(input: MistakePdfExportInput, name: String): File {
        renderCounter += 1
        val file = File(temporaryFolder.root, "$renderCounter-$name")
        DocxMistakeRenderer.render(input, file)
        return file
    }

    private fun readZip(file: File): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
            }
        }
        return parts
    }

    private fun documentTexts(documentXml: ByteArray): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml))
        val nodes = document.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
            "t",
        )
        return (0 until nodes.length).map { index -> nodes.item(index).textContent }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 结构完整的 PNG（签名 + IHDR + IEND）；渲染器只读头部。 */
    private fun png(width: Int, height: Int): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply {
                update(typeBytes)
                update(data)
            }.value
            return byteArrayOf(
                (data.size ushr 24).toByte(),
                (data.size ushr 16).toByte(),
                (data.size ushr 8).toByte(),
                data.size.toByte(),
            ) + typeBytes + data + byteArrayOf(
                (crc ushr 24).toByte(),
                (crc ushr 16).toByte(),
                (crc ushr 8).toByte(),
                crc.toByte(),
            )
        }

        val header = byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(),
            (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(),
            (height ushr 8).toByte(), height.toByte(),
            8, 6, 0, 0, 0,
        )
        return byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", header) +
            chunk("IEND", ByteArray(0))
    }
}
