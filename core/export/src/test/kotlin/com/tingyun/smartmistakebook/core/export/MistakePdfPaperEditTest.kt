package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 6B-2 卷面编辑模型：增删题 / 调序 / 改文字只作用于卷面快照。
 *
 * 钉住四件事：
 * 1. **未编辑逐位不变**：草稿的输入与正常批量导出同块、同指纹；docx 字节逐一相同（金样）。
 * 2. **编辑必变**：文字/顺序/题数一改，导出块序列与产物字节都变。
 * 3. **编辑不污染错题条目**：来源输入与 `MistakeDetailState` 的值不变，来源文档指纹保留。
 * 4. **可逆**：删掉再加回、改文字再改回，重新等于来源快照（`isEdited == false`）。
 */
class MistakePdfPaperEditTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `an unedited paper draft is the same snapshot and the same docx bytes as a direct batch export`() {
        val layout = MistakePdfLayout(includeNote = true)
        val states = listOf(
            readyState("entry-a", "题 A", "原始文字AAA"),
            readyState("entry-b", "题 B", "原始文字BBB"),
        )
        val direct = MistakePdfBatchEligibility.check(states, layout) as
            MistakePdfBatchEligibilityResult.Eligible
        val inputs = states.map { state ->
            (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible)
                .input
        }

        val draft = MistakePdfPaperEdit.fromInputs(inputs, layout).toDraft()

        assertFalse(draft.edited)
        assertEquals(2, draft.questionCount)
        assertEquals(draft.input.inputSha256, draft.sourceInputSha256)
        assertSameInput(direct.input, draft.input)
        assertArrayEquals(
            renderDocx(direct.input, "direct.docx").readBytes(),
            renderDocx(draft.input, "draft.docx").readBytes(),
        )
    }

    @Test
    fun `editing a paragraph changes the exported blocks and the rendered document`() {
        val direct = directBatch()
        val edited = MistakePdfPaperEdit.fromInputs(direct.inputs)
            .withBlockText("entry-a", "stem-entry-a", "修改文字BBB")
            .toDraft()

        assertTrue(edited.edited)
        assertNotEquals(direct.eligible.input.inputSha256, edited.input.inputSha256)
        assertNotEquals(direct.eligible.input.blocks, edited.input.blocks)

        val texts = documentTexts(renderDocx(edited.input, "edited.docx"))
        assertTrue(texts.contains("修改文字BBB"))
        assertFalse(texts.contains("原始文字AAA"))
    }

    @Test
    fun `moving a question renumbers the headings and moves its blocks`() {
        val direct = directBatch()

        val moved = MistakePdfPaperEdit.fromInputs(direct.inputs)
            .withQuestionMoved("entry-b", -1)
            .toDraft()

        val headings = moved.input.blocks.filterIsInstance<MistakePdfBlock.SectionHeading>()
        assertEquals(listOf("1. 题 B", "2. 题 A"), headings.map { it.text })
        assertEquals("question-0-heading", headings.first().id)
        // 第一题的块现在来自 entry-b：meta 文本与命名空间 id 都跟着位置走。
        val firstMeta = moved.input.blocks.filterIsInstance<MistakePdfBlock.Paragraph>().first()
        assertEquals("MATH · 第 3 版", firstMeta.text)
        assertTrue(moved.input.blocks.any { it.id == "question-0-stem-entry-b" })
        assertTrue(moved.input.blocks.any { it.id == "question-1-stem-entry-a" })
        assertNotEquals(direct.eligible.input.inputSha256, moved.input.inputSha256)
    }

    @Test
    fun `removing a question updates the paper count in the rendered meta line`() {
        val direct = directBatch()

        val removed = MistakePdfPaperEdit.fromInputs(direct.inputs)
            .withQuestionRemoved("entry-b")
            .toDraft()

        assertEquals("按当前筛选整理 · 共 1 道", removed.input.subtitle)
        val texts = documentTexts(renderDocx(removed.input, "removed-count.docx"))
        assertTrue(texts.contains("按当前筛选整理 · 共 1 道"))
        assertFalse(texts.contains("2. 题 B"))
    }

    @Test
    fun `removing and restoring a question returns to the exact source snapshot`() {
        val direct = directBatch()
        val edit = MistakePdfPaperEdit.fromInputs(direct.inputs)

        val removed = edit.withQuestionRemoved("entry-b")
        assertEquals(1, removed.questions.size)
        assertEquals(listOf("entry-a"), removed.questions.map { it.entryId })
        assertTrue(removed.isEdited)

        val restored = removed.withQuestionRestored("entry-b")
        assertFalse(restored.isEdited)
        assertEquals(direct.eligible.input.inputSha256, restored.toDraft().input.inputSha256)
        assertArrayEquals(
            renderDocx(direct.eligible.input, "before-remove.docx").readBytes(),
            renderDocx(restored.toDraft().input, "after-restore.docx").readBytes(),
        )
    }

    @Test
    fun `the last question cannot be removed`() {
        val single = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        )

        assertEquals(1, single.withQuestionRemoved("entry-a").questions.size)
        assertFalse(single.withQuestionRemoved("entry-a").isEdited)
    }

    @Test
    fun `an externally added question is assembled at the requested position`() {
        val edit = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        )
        val added = paperQuestionOf(paperInput("entry-b", "题 B", "原始文字BBB"))

        val draft = edit.withQuestionAdded(added, atIndex = 0).toDraft()

        assertTrue(draft.edited)
        assertEquals(2, draft.questionCount)
        val headings = draft.input.blocks.filterIsInstance<MistakePdfBlock.SectionHeading>()
        assertEquals(listOf("1. 题 B", "2. 题 A"), headings.map { it.text })
        // 外部加入的题不属于来源快照：删掉它就回到来源（不会留在"可加回"列表里）。
        assertFalse(edit.withQuestionAdded(added).withQuestionRemoved("entry-b").isEdited)
    }

    @Test
    fun `text edits flag the retained figure for review while reordering does not`() {
        val withFigure = paperInput(
            entryId = "entry-a",
            title = "图题",
            blockText = "原始文字AAA",
            extraBlocks = listOf(
                MistakePdfBlock.Figure(
                    id = "figure-1",
                    title = "受力分析图",
                    alternativeText = "斜面受力",
                    schema = FigureSchema.SymbolTable(
                        headers = listOf("项"),
                        rows = listOf(listOf("重力")),
                    ),
                ),
            ),
        )
        val edit = MistakePdfPaperEdit.fromInputs(
            listOf(withFigure, paperInput("entry-b", "题 B", "原始文字BBB")),
        )

        assertFalse(edit.figureReviewRequired("entry-a"))
        assertFalse(edit.withQuestionMoved("entry-b", -1).figureReviewRequired("entry-a"))
        // 标题/题干改动都可能是条件改动：原图仍复用，但必须提示复核。
        assertTrue(edit.withQuestionTitle("entry-a", "改过的标题").figureReviewRequired("entry-a"))
        assertTrue(
            edit.withBlockText("entry-a", "stem", "改过的条件").figureReviewRequired("entry-a"),
        )
    }

    @Test
    fun `editing never writes back to the source inputs or the source document fingerprint`() {
        val state = readyState("entry-a", "题 A", "原始文字AAA")
        val source = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input
        val sourceBlocks = source.blocks
        val sourceDocumentSha = source.questionDocumentSha256

        val unedited = MistakePdfPaperEdit.fromInputs(listOf(source))
        val paperDocumentSha = unedited.toDraft().input.questionDocumentSha256
        val paperInputSha = unedited.toDraft().input.inputSha256
        val draft = unedited
            .withBlockText("entry-a", "stem-entry-a", "改过的文字")
            .withQuestionTitle("entry-a", "改过的标题")
            .toDraft()

        // 来源输入没被改（块列表与题面文档指纹都保持原值）。
        assertEquals(sourceBlocks, source.blocks)
        assertEquals("题 A", source.title)
        assertEquals(sourceDocumentSha, source.questionDocumentSha256)
        // 编辑后的卷面仍指回同一份来源文档：只换渲染输入指纹，不声称新的 revision。
        assertEquals(paperDocumentSha, draft.input.questionDocumentSha256)
        assertEquals(paperInputSha, draft.sourceInputSha256)
        assertEquals("batch", draft.input.errorBookEntryId)
        assertEquals("batch", draft.input.problemId)
        // 原状态重新判定仍得到同一份来源输入（没有被"编辑"这条路径写过任何东西）。
        val reRead = MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible
        assertEquals(source.inputSha256, reRead.input.inputSha256)
    }

    @Test
    fun `editing the paper note only changes the draft and never the entry note`() {
        val state = readyState("entry-a", "题 A", "原始文字AAA")
        val source = (MistakePdfEligibility.check(state) as MistakePdfEligibilityResult.Eligible)
            .input
        val draft = MistakePdfPaperEdit.fromInputs(
            inputs = listOf(source),
            layout = MistakePdfLayout(includeNote = true),
        )
            .withNote("entry-a", "卷面里改的备注")
            .toDraft()

        assertTrue(draft.edited)
        assertTrue(
            draft.input.blocks.any { block ->
                block is MistakePdfBlock.Note && block.text == "卷面里改的备注"
            },
        )
        // 条目备注没有被写：改的只是这份卷面草稿。
        assertEquals("这题的备注", source.userNote)
    }

    @Test
    fun `blank required text blocks the draft and is reported before rendering`() {
        val edit = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        )

        val blank = edit.withBlockText("entry-a", "stem", "")
        assertTrue(blank.hasBlankText)

        val failure = runCatching { blank.toDraft() }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
    }

    @Test
    fun `unknown ids are ignored instead of corrupting another question`() {
        val direct = directBatch()
        val edit = MistakePdfPaperEdit.fromInputs(direct.inputs)

        assertFalse(edit.withBlockText("entry-missing", "stem", "x").isEdited)
        assertFalse(edit.withBlockText("entry-a", "block-missing", "x").isEdited)
        assertFalse(edit.withQuestionMoved("entry-missing", 1).isEdited)
        assertFalse(edit.withQuestionTitle("entry-missing", "x").isEdited)
    }

    @Test
    fun `fromStates skips ineligible entries and counts them`() = runBlocking {
        val edit = MistakePdfPaperEdit.fromStates(
            entryIds = listOf("entry-a", "entry-old"),
            layout = MistakePdfLayout.DEFAULT,
            readByEntryId = { entryId ->
                if (entryId == "entry-a") {
                    readyState("entry-a", "题 A", "原始文字AAA")
                } else {
                    MistakeDetailState.Legacy(
                        MistakeDetail(
                            identity = identity(entryId),
                            fallbackMarkdown = "旧题面",
                            source = MistakeSourceSet.Missing,
                        ),
                    )
                }
            },
        )

        assertEquals(listOf("entry-a"), edit?.questions?.map { it.entryId })
        assertEquals(1, edit?.omittedCount)
    }

    @Test
    fun `fromStates returns null when nothing is eligible`() = runBlocking {
        val edit = MistakePdfPaperEdit.fromStates(
            entryIds = listOf("entry-old"),
            layout = MistakePdfLayout.DEFAULT,
            readByEntryId = {
                MistakeDetailState.NotFound
            },
        )

        assertNull(edit)
    }

    @Test
    fun `a draft round trips through the store with every block shape`() {
        val input = paperInput(
            entryId = "entry-a",
            title = "综合题",
            extraBlocks = listOf(
                MistakePdfBlock.Figure(
                    id = "figure-1",
                    title = "受力分析图",
                    alternativeText = "斜面受力",
                    schema = FigureSchema.SymbolTable(
                        headers = listOf("项", "值"),
                        rows = listOf(listOf("重力", "mg")),
                    ),
                ),
                // 新协议图的解码边界：schema 自己也带一个 `type` 字段，不能与类判字段撞键。
                MistakePdfBlock.Figure(
                    id = "figure-2",
                    title = "新图",
                    alternativeText = "新协议图形",
                    schema = FigureSchema.Unknown(type = "future_chart", fallbackText = "新图说明"),
                ),
            ),
        )
        val withImageAndNote = MistakePdfExportInput(
            errorBookEntryId = input.errorBookEntryId,
            problemId = input.problemId,
            problemRevisionId = input.problemRevisionId,
            revisionNumber = input.revisionNumber,
            title = input.title,
            subject = input.subject,
            subtitle = input.subtitle,
            documentTitle = "题面",
            blocks = input.blocks + listOf(
                MistakePdfBlock.Formula("formula-1", "x^2", "x 的平方", display = false),
                MistakePdfBlock.ChoiceGroup(
                    id = "choice-1",
                    prompt = "选择正确的一项",
                    choices = listOf(
                        MistakePdfChoice("a", "甲", selected = true, enabled = true),
                        MistakePdfChoice("b", "乙", selected = false, enabled = true),
                    ),
                    enabled = true,
                ),
                MistakePdfBlock.SectionHeading("heading-1", "解析"),
            ),
            questionDocumentSha256 = input.questionDocumentSha256,
            inputSha256 = input.inputSha256,
            cleanImageLocalUri = "file:///tmp/clean.png",
            // KD-55：编辑草稿必须把同一条资产记录的完整性期望带进图块。
            cleanImageSha256 = "a".repeat(64),
            cleanImageByteSize = 1_024L,
            userNote = "我的备注",
            layout = MistakePdfLayout(includeNote = true, includeAnswer = true),
        )
        val draft = MistakePdfPaperEdit.fromInputs(
            inputs = listOf(withImageAndNote),
            layout = withImageAndNote.layout,
        )
            .withBlockText("entry-a", "formula-1", "x^3")
            .toDraft()
        val store = MistakePaperDraftStore(temporaryFolder.newFolder("drafts"))

        assertTrue(
            draft.input.blocks.any { block ->
                block is MistakePdfBlock.Image && block.localUri == "file:///tmp/clean.png"
            },
        )
        val image = draft.input.blocks.filterIsInstance<MistakePdfBlock.Image>().single()
        assertEquals("a".repeat(64), image.expectedContentSha256)
        assertEquals(1_024L, image.expectedByteSize)
        store.write("draft-1", draft)
        val read = store.read("draft-1")

        assertEquals(true, read?.edited)
        assertEquals(draft.questionCount, read?.questionCount)
        assertEquals(draft.sourceInputSha256, read?.sourceInputSha256)
        assertSameInput(draft.input, read!!.input)
    }

    @Test
    fun `an unedited draft round trips through the store and keeps the source identity`() {
        val direct = directBatch()
        val draft = MistakePdfPaperEdit.fromInputs(direct.inputs).toDraft()
        val store = MistakePaperDraftStore(temporaryFolder.newFolder("drafts-unedited"))

        store.write("draft-1", draft)
        val read = store.read("draft-1")

        assertEquals(false, read?.edited)
        assertEquals(draft.sourceInputSha256, read?.sourceInputSha256)
        assertEquals(draft.input.inputSha256, read?.input?.inputSha256)
        assertSameInput(draft.input, read!!.input)
    }

    @Test
    fun `a corrupted or missing draft reads as null instead of a half document`() {
        val store = MistakePaperDraftStore(temporaryFolder.newFolder("drafts-broken"))
        val draft = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        ).toDraft()
        store.write("draft-1", draft)

        File(temporaryFolder.root, "drafts-broken/draft-1.draft").appendText("x")
        assertNull(store.read("draft-1"))
        assertNull(store.read("never-written"))

        store.delete("draft-1")
        assertNull(store.read("draft-1"))
    }

    @Test
    fun `a draft id cannot escape the draft directory`() {
        val store = MistakePaperDraftStore(temporaryFolder.newFolder("drafts-escape"))
        val draft = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        ).toDraft()

        val failure = runCatching { store.write("../escape", draft) }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
    }

    @Test
    fun `cleanup removes drafts older than the ttl`() {
        val root = temporaryFolder.newFolder("drafts-ttl")
        val store = MistakePaperDraftStore(root)
        val draft = MistakePdfPaperEdit.fromInputs(
            listOf(paperInput("entry-a", "题 A", "原始文字AAA")),
        ).toDraft()
        store.write("draft-1", draft)
        val file = File(root, "draft-1.draft")
        assertTrue(file.setLastModified(System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000))

        store.cleanup()

        assertNull(store.read("draft-1"))
    }

    // ---- fixtures ----

    private data class DirectBatch(
        val inputs: List<MistakePdfExportInput>,
        val eligible: MistakePdfBatchEligibilityResult.Eligible,
    )

    private fun directBatch(layout: MistakePdfLayout = MistakePdfLayout.DEFAULT): DirectBatch {
        val states = listOf(
            readyState("entry-a", "题 A", "原始文字AAA"),
            readyState("entry-b", "题 B", "原始文字BBB"),
        )
        val inputs = states.map { state ->
            (MistakePdfEligibility.check(state, layout) as MistakePdfEligibilityResult.Eligible)
                .input
        }
        return DirectBatch(
            inputs = inputs,
            eligible = MistakePdfBatchEligibility.check(states, layout) as
                MistakePdfBatchEligibilityResult.Eligible,
        )
    }

    private fun assertSameInput(expected: MistakePdfExportInput, actual: MistakePdfExportInput) {
        assertEquals(expected.errorBookEntryId, actual.errorBookEntryId)
        assertEquals(expected.problemId, actual.problemId)
        assertEquals(expected.problemRevisionId, actual.problemRevisionId)
        assertEquals(expected.revisionNumber, actual.revisionNumber)
        assertEquals(expected.title, actual.title)
        assertEquals(expected.subject, actual.subject)
        assertEquals(expected.subtitle, actual.subtitle)
        assertEquals(expected.documentTitle, actual.documentTitle)
        assertEquals(expected.blocks, actual.blocks)
        assertEquals(expected.questionDocumentSha256, actual.questionDocumentSha256)
        assertEquals(expected.inputSha256, actual.inputSha256)
        assertEquals(expected.maxPages, actual.maxPages)
        assertEquals(expected.maxRenderedLines, actual.maxRenderedLines)
        assertEquals(expected.maxPdfBytes, actual.maxPdfBytes)
        assertEquals(expected.cleanImageLocalUri, actual.cleanImageLocalUri)
        assertEquals(expected.cleanImageSha256, actual.cleanImageSha256)
        assertEquals(expected.cleanImageByteSize, actual.cleanImageByteSize)
        assertEquals(expected.questionImageRole, actual.questionImageRole)
        assertEquals(expected.solutionImages, actual.solutionImages)
        assertEquals(expected.layout, actual.layout)
        assertEquals(expected.userNote, actual.userNote)
    }

    private fun paperInput(
        entryId: String,
        title: String,
        blockText: String? = null,
        extraBlocks: List<MistakePdfBlock> = emptyList(),
    ): MistakePdfExportInput = MistakePdfExportInput(
        errorBookEntryId = entryId,
        problemId = "problem-$entryId",
        problemRevisionId = "revision-$entryId",
        revisionNumber = 3,
        title = title,
        subject = "MATH",
        subtitle = "MATH · 第 3 版",
        documentTitle = null,
        blocks = listOfNotNull(blockText?.let { MistakePdfBlock.Paragraph("stem", it) }) +
            extraBlocks,
        questionDocumentSha256 = exportFingerprint("test-doc", entryId),
        inputSha256 = exportFingerprint("test-input", entryId),
        userNote = "这题的备注",
    )

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = identity(entryId, title),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Missing,
                userNote = "这题的备注",
            ),
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-$entryId",
                    title = title,
                    blocks = listOf(block),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = block.id,
                        sourceAssetId = "asset-$entryId",
                        sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                        writingLayer = WritingLayer.PRINTED,
                        provenance = QuestionBlockProvenance.USER_CORRECTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )
    }

    private fun identity(entryId: String, title: String = "题") = MistakeDetailIdentity(
        errorBookEntryId = entryId,
        problemId = "problem-$entryId",
        problemRevisionId = "revision-$entryId",
        revisionNumber = 3,
        title = title,
        subject = "MATH",
    )

    private fun renderDocx(input: MistakePdfExportInput, name: String): File {
        val file = File(temporaryFolder.root, name)
        DocxMistakeRenderer.render(input, file)
        return file
    }

    private fun documentTexts(file: File): List<String> {
        val documentXml = readZip(file).getValue(MinimalDocxWriter.DOCUMENT_PART)
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml))
        val nodes = document.getElementsByTagNameNS(W_NS, "t")
        return (0 until nodes.length).map { index -> nodes.item(index).textContent }
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

    private companion object {
        const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    }
}
