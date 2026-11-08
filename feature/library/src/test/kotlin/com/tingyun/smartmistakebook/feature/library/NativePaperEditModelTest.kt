package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.export.MistakePdfBlock
import com.tingyun.smartmistakebook.core.export.MistakePdfChoice
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperEdit
import com.tingyun.smartmistakebook.core.export.MistakePdfPaperQuestion
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureSchema
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditModelException
import com.tingyun.smartmistakebook.core.model.PaperEditModelPort
import com.tingyun.smartmistakebook.core.model.PaperEditOutput
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-3 自然语言改卷的宿主侧判据（卷面 / 详情入口共用）。
 *
 * 钉住五件事：
 * 1. **快照有界且无本地路径**：`MistakePdfPaperEdit -> PaperEditInput` 只带题序/标题/文字块/
 *    选项/已删来源题；题面图 URI、资产摘要、备注（无备注区时）都不进模型可见文本。
 * 2. **预览不改原卷面**：白名单命令在副本上逐条应用，产出差异清单；来源卷面实例保持不变。
 * 3. **整批拒绝**：任一命令越界（未知 id / 图块 / 空文 / 越界调序）都整批拒绝，卷面保持原样。
 * 4. **失败不是"没有修改"**：端口失败/取消/未落终态返回如实拒绝，不伪造模型回执；
 *    学生原输入不因失败被清空（由入口持有，本层不产出任何"已改好"）。
 * 5. **采用有 CAS**：预览只能应用到生成它时的那份卷面；等待/预览期间手动改过就作废。
 */
class NativePaperEditModelTest {

    @Test
    fun `the snapshot carries bounded paper text and never a local image location`() {
        val paper = paperOf(listOf(stateWithCleanImage("entry-a", "题 A", "原始文字AAA")))
            .withQuestionAdded(syntheticImageQuestion("entry-img"))

        val input = NativePaperEditModel.buildInput(
            paper = paper,
            instruction = "把第 1 题标题改短",
            paperId = "native-paper-edit:test",
            revision = 7L,
        )

        assertEquals(ModelTaskKind.PAPER_EDIT, input.kind)
        assertEquals("native-paper-edit:test", input.subjectId)
        assertEquals(7L, input.revision)
        assertTrue(input.questions.any { it.entryId == "entry-a" })
        val imageQuestion = input.questions.single { it.entryId == "entry-img" }
        val imageBlock = imageQuestion.blocks.single()
        assertEquals(PaperEditBlockKind.IMAGE, imageBlock.kind)
        assertEquals("", imageBlock.text)
        assertTrue(imageBlock.choices.isEmpty())
        // 题面图与图块的本地地址都不在模型可见文本里（结构上也没有这个字段）。
        assertFalse(input.toString().contains("content://"))
        assertFalse(input.toString().contains("secret/clean.png"))
        assertFalse(input.toString().contains("secret/diagram.png"))
    }

    @Test
    fun `notes enter the snapshot only when the layout has a note area`() {
        val state = readyState("entry-a", "题 A", "原始文字AAA")

        val withNote = NativePaperEditModel.buildInput(
            paper = paperOf(listOf(state), MistakePdfLayout(includeNote = true)),
            instruction = "把备注改短",
            paperId = "paper-with-note",
            revision = 1L,
        )
        val withoutNote = NativePaperEditModel.buildInput(
            paper = paperOf(listOf(state), MistakePdfLayout(includeNote = false)),
            instruction = "把备注改短",
            paperId = "paper-without-note",
            revision = 1L,
        )

        assertEquals("这题的备注", withNote.questions.single().note)
        assertNull(withoutNote.questions.single().note)
    }

    @Test
    fun `every block kind maps to the bounded snapshot shape`() {
        val paper = paperOf(listOf(readyState("entry-a", "题 A", "原始文字AAA")))
            .withQuestionAdded(
                MistakePdfPaperQuestion(
                    entryId = "entry-kinds",
                    title = "多块题",
                    subtitle = "MATH · 第 1 版",
                    blocks = listOf(
                        MistakePdfBlock.SectionHeading("heading-1", "小标题"),
                        MistakePdfBlock.Formula("formula-1", "x^2", "x 的平方", true),
                        MistakePdfBlock.ChoiceGroup(
                            id = "choice-1",
                            prompt = "选一个",
                            choices = listOf(
                                MistakePdfChoice("choice-a", "甲", selected = false, enabled = true),
                            ),
                            enabled = true,
                        ),
                        MistakePdfBlock.Figure(
                            id = "figure-1",
                            title = "示意图",
                            alternativeText = "一个坐标系",
                            schema = FigureSchema.SymbolTable(emptyList(), emptyList()),
                        ),
                        MistakePdfBlock.Note("note-1", "备注块文字不应进快照"),
                    ),
                    sourceRevisionId = "revision-entry-kinds",
                ),
            )

        val input = NativePaperEditModel.buildInput(paper, "改点东西", "paper-kinds", 1L)
        val question = input.questions.single { it.entryId == "entry-kinds" }

        assertEquals(
            listOf(
                PaperEditBlockKind.SECTION_HEADING,
                PaperEditBlockKind.FORMULA,
                PaperEditBlockKind.CHOICE_GROUP,
                PaperEditBlockKind.FIGURE,
                PaperEditBlockKind.NOTE,
            ),
            question.blocks.map { it.kind },
        )
        assertEquals("小标题", question.blocks[0].text)
        assertEquals("x^2", question.blocks[1].text)
        assertEquals("选一个", question.blocks[2].text)
        assertEquals(listOf("choice-a"), question.blocks[2].choices.map { it.choiceId })
        assertEquals("", question.blocks[3].text)
        assertTrue(question.blocks[3].choices.isEmpty())
        assertEquals("", question.blocks[4].text)
        assertFalse(input.toString().contains("备注块文字不应进快照"))
    }

    @Test
    fun `a blank instruction and an over-budget instruction are rejected before the port runs`() =
        runBlocking {
            val paper = paperOf(listOf(readyState("entry-a", "题 A", "原始文字AAA")))
            val port = RecordingPort(reply = { commands("""{"commands":[]}""") })

            val blank = NativePaperEditModel.request(
                port = port,
                paper = paper,
                instruction = "   ",
                paperId = "paper-1",
                revision = 1L,
                agentConsentGranted = true,
            )
            val tooLong = NativePaperEditModel.request(
                port = port,
                paper = paper,
                instruction = "改".repeat(PaperEditInput.MAX_INSTRUCTION_CHARS + 1),
                paperId = "paper-1",
                revision = 1L,
                agentConsentGranted = true,
            )

            assertTrue(blank is NativePaperEditPreview.Rejected)
            assertTrue((blank as NativePaperEditPreview.Rejected).reason.contains("修改要求"))
            assertTrue(tooLong is NativePaperEditPreview.Rejected)
            assertEquals(0, port.calls)
        }

    @Test
    fun `whitelisted commands produce a preview without touching the source paper`() {
        val paper = twoQuestionPaper()
        val preview = NativePaperEditModel.previewFor(
            paper = paper,
            output = commands(
                """{"commands":[""" +
                    """{"type":"set_question_title","entryId":"entry-a","title":"题 A 新"},""" +
                    """{"type":"move_question","entryId":"entry-b","offset":-1},""" +
                    """{"type":"set_block_text","entryId":"entry-b","blockId":"stem-entry-b","text":"改后的 BBB"}""" +
                    """]}""",
            ),
        )

        assertTrue(preview is NativePaperEditPreview.Ready)
        val ready = preview as NativePaperEditPreview.Ready
        assertSame(paper, ready.baseline)
        assertEquals("题 A 新", ready.edit.questions.first { it.entryId == "entry-a" }.title)
        assertEquals(listOf("entry-b", "entry-a"), ready.edit.questions.map { it.entryId })
        assertEquals(3, ready.commandCount)
        assertEquals(3, ready.diffs.size)
        assertTrue(ready.diffs.any { it.change.contains("标题") })
        assertTrue(ready.diffs.any { it.change.contains("顺序") })
        assertTrue(ready.diffs.any { it.change.contains("段落") })
        // 原卷面（模型输入的那份）逐字不变：只有副本被改。
        assertEquals("题 A", paper.questions.first { it.entryId == "entry-a" }.title)
        assertEquals(
            "原始文字BBB",
            paragraphText(paper, "entry-b", "stem-entry-b"),
        )
        assertTrue(ready.edit.toDraft().edited)
    }

    @Test
    fun `an illegal or out-of-range command rejects the whole batch and keeps the paper`() {
        val paper = twoQuestionPaper().withQuestionAdded(syntheticFigureQuestion("entry-fig"))
        val preview = NativePaperEditModel.previewFor(
            paper = paper,
            output = commands(
                """{"commands":[""" +
                    """{"type":"set_question_title","entryId":"entry-a","title":"先改这个"},""" +
                    """{"type":"set_block_text","entryId":"entry-fig","blockId":"fig-1","text":"改图"}""" +
                    """]}""",
            ),
        )

        assertTrue(preview is NativePaperEditPreview.Rejected)
        val reason = (preview as NativePaperEditPreview.Rejected).reason
        assertTrue(reason.contains("第 2 条"))
        assertTrue(reason.contains("图块"))
        // 整批不落：连第一条合法命令也没有被应用。
        assertEquals("题 A", paper.questions.first { it.entryId == "entry-a" }.title)
        assertEquals("原始文字BBB", paragraphText(paper, "entry-b", "stem-entry-b"))
    }

    @Test
    fun `an unknown question id is rejected instead of guessed`() {
        val paper = twoQuestionPaper()
        val preview = NativePaperEditModel.previewFor(
            paper = paper,
            output = commands(
                """{"commands":[{"type":"remove_question","entryId":"entry-missing"}]}""",
            ),
        )

        assertTrue(preview is NativePaperEditPreview.Rejected)
        assertTrue((preview as NativePaperEditPreview.Rejected).reason.contains("entry-missing"))
        assertEquals(2, paper.questions.size)
    }

    @Test
    fun `an empty command list is not a success receipt`() {
        val preview = NativePaperEditModel.previewFor(twoQuestionPaper(), commands("""{"commands":[]}"""))

        assertTrue(preview is NativePaperEditPreview.Rejected)
        assertTrue(
            (preview as NativePaperEditPreview.Rejected).reason.contains("没有给出可执行的修改"),
        )
    }

    @Test
    fun `commands that change nothing are not reported as applied`() {
        val preview = NativePaperEditModel.previewFor(
            twoQuestionPaper(),
            commands(
                """{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"题 A"}]}""",
            ),
        )

        assertTrue(preview is NativePaperEditPreview.Rejected)
        assertTrue((preview as NativePaperEditPreview.Rejected).reason.contains("没有产生任何实际修改"))
    }

    @Test
    fun `a model failure keeps the paper and does not echo the provider message as a receipt`() =
        runBlocking {
            val paper = twoQuestionPaper()
            val port = object : PaperEditModelPort {
                override suspend fun commandsFor(
                    input: PaperEditInput,
                    agentConsentGranted: Boolean,
                ): PaperEditOutput = throw PaperEditModelException(
                    status = ModelTaskStatus.PERMANENT_FAILURE,
                    failureCode = ModelFailureCode.INVALID_RESPONSE,
                    retryable = false,
                    message = "provider said: bad request body",
                )
            }

            val outcome = NativePaperEditModel.request(
                port = port,
                paper = paper,
                instruction = "把第 1 题标题改短",
                paperId = "paper-1",
                revision = 3L,
                agentConsentGranted = true,
            )

            assertTrue(outcome is NativePaperEditPreview.Rejected)
            val reason = (outcome as NativePaperEditPreview.Rejected).reason
            assertTrue(reason.contains("卷面保持原样"))
            assertFalse(reason.contains("provider said"))
            assertEquals(2, paper.questions.size)
            assertEquals("题 A", paper.questions.first().title)
        }

    @Test
    fun `a cancelled task is reported as cancelled and keeps the paper`() = runBlocking {
        val paper = twoQuestionPaper()
        val port = object : PaperEditModelPort {
            override suspend fun commandsFor(
                input: PaperEditInput,
                agentConsentGranted: Boolean,
            ): PaperEditOutput = throw PaperEditModelException(
                status = ModelTaskStatus.CANCELLED,
                failureCode = null,
                retryable = false,
                message = "已停止",
            )
        }

        val outcome = NativePaperEditModel.request(
            port = port,
            paper = paper,
            instruction = "改标题",
            paperId = "paper-1",
            revision = 3L,
            agentConsentGranted = true,
        )

        assertTrue(outcome is NativePaperEditPreview.Rejected)
        assertTrue((outcome as NativePaperEditPreview.Rejected).reason.contains("取消"))
        assertEquals("题 A", paper.questions.first().title)
    }

    @Test
    fun `the app consent value is forwarded as given and never hardcoded`() = runBlocking {
        val paper = twoQuestionPaper()
        val port = RecordingPort(reply = {
            commands("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"新"}]}""")
        })

        NativePaperEditModel.request(port, paper, "改标题", "paper-1", 3L, agentConsentGranted = false)
        NativePaperEditModel.request(port, paper, "改标题", "paper-1", 3L, agentConsentGranted = true)

        assertEquals(listOf(false, true), port.consents)
        assertEquals(listOf("paper-1", "paper-1"), port.inputs.map { it.paperId })
        assertEquals(listOf(3L, 3L), port.inputs.map { it.revision })
        assertEquals(listOf("改标题", "改标题"), port.inputs.map { it.instruction })
    }

    @Test
    fun `a preview can only be adopted on the exact paper it was generated from`() {
        val paper = twoQuestionPaper()
        val preview = NativePaperEditModel.previewFor(
            paper,
            commands("""{"commands":[{"type":"set_question_title","entryId":"entry-a","title":"新标题"}]}"""),
        ) as NativePaperEditPreview.Ready

        val adopted = NativePaperEditModel.adopt(paper, preview)
        val stale = NativePaperEditModel.adopt(
            paper.withBlockText("entry-b", "stem-entry-b", "等待期间手动改过"),
            preview,
        )

        assertTrue(adopted is NativePaperEditAdoption.Adopted)
        assertEquals("新标题", (adopted as NativePaperEditAdoption.Adopted).edit.questions.first().title)
        assertTrue(stale is NativePaperEditAdoption.Stale)
        assertTrue((stale as NativePaperEditAdoption.Stale).reason.contains("重新生成"))
    }

    @Test
    fun `the revision is stable for the same paper and changes with its content`() {
        val paper = twoQuestionPaper()
        val first = NativePaperEditModel.revisionFor(paper)
        val second = NativePaperEditModel.revisionFor(paper)
        val edited = NativePaperEditModel.revisionFor(
            paper.withQuestionTitle("entry-a", "改过的标题"),
        )

        assertEquals(first, second)
        assertNotEquals(first, edited)
        assertTrue(first >= 0)
        assertTrue(edited >= 0)
    }

    @Test
    fun `the host paper id is stable for the same ticket and bounded for long tickets`() {
        val short = NativePaperEditModel.paperIdFor(listOf("entry-a", "entry-b"))
        val long = NativePaperEditModel.paperIdFor((1..40).map { "entry-$it" })

        assertEquals(short, NativePaperEditModel.paperIdFor(listOf("entry-a", "entry-b")))
        assertNotEquals(short, NativePaperEditModel.paperIdFor(listOf("entry-b", "entry-a")))
        assertTrue(short.length <= PaperEditInput.MAX_ID_CHARS)
        assertTrue(long.length <= PaperEditInput.MAX_ID_CHARS)
    }

    @Test
    fun `an input that cannot be represented is rejected before the port runs`() = runBlocking {
        val paper = paperOf(listOf(readyState("entry-a", "题 A", "原始文字AAA")))
        val port = RecordingPort(reply = { commands("""{"commands":[]}""") })

        val outcome = NativePaperEditModel.request(
            port = port,
            paper = paper,
            instruction = "改标题\u0000",
            paperId = "paper-1",
            revision = 1L,
            agentConsentGranted = true,
        )

        assertTrue(outcome is NativePaperEditPreview.Rejected)
        assertEquals(0, port.calls)
        assertEquals("题 A", paper.questions.first().title)
    }

    @Test
    fun `a blank required text blocks the request before any model task`() = runBlocking {
        val paper = paperOf(listOf(readyState("entry-a", "题 A", "原始文字AAA")))
            .withQuestionTitle("entry-a", "   ")
        val port = RecordingPort(reply = { commands("""{"commands":[]}""") })

        val outcome = NativePaperEditModel.request(
            port = port,
            paper = paper,
            instruction = "改标题",
            paperId = "paper-1",
            revision = 1L,
            agentConsentGranted = true,
        )

        assertTrue(outcome is NativePaperEditPreview.Rejected)
        assertTrue((outcome as NativePaperEditPreview.Rejected).reason.contains("必填文字"))
        assertEquals(0, port.calls)
    }

    // ---- fixtures ----

    private class RecordingPort(
        private val reply: () -> PaperEditOutput,
    ) : PaperEditModelPort {
        val inputs = mutableListOf<PaperEditInput>()
        val consents = mutableListOf<Boolean>()
        var calls = 0
            private set

        override suspend fun commandsFor(
            input: PaperEditInput,
            agentConsentGranted: Boolean,
        ): PaperEditOutput {
            calls += 1
            inputs += input
            consents += agentConsentGranted
            return reply()
        }
    }

    private fun commands(json: String): PaperEditOutput =
        PaperEditOutput(commandsJson = json, modelVersion = "fake-paper-edit-1")

    private fun twoQuestionPaper(): MistakePdfPaperEdit = paperOf(
        listOf(
            readyState("entry-a", "题 A", "原始文字AAA"),
            readyState("entry-b", "题 B", "原始文字BBB"),
        ),
    )

    private fun paperOf(
        states: List<MistakeDetailState>,
        layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ): MistakePdfPaperEdit {
        val byId = states.associateBy { state ->
            (state as MistakeDetailState.Ready).detail.identity.errorBookEntryId
        }
        val entryIds = states.map { (it as MistakeDetailState.Ready).detail.identity.errorBookEntryId }
        return checkNotNull(
            runBlocking {
                MistakePdfPaperEdit.fromStates(entryIds, layout) { entryId ->
                    byId.getValue(entryId)
                }
            },
        )
    }

    private fun paragraphText(
        paper: MistakePdfPaperEdit,
        entryId: String,
        blockId: String,
    ): String = paper.questions
        .single { it.entryId == entryId }
        .blocks
        .filterIsInstance<MistakePdfBlock.Paragraph>()
        .single { it.id == blockId }
        .text

    private fun syntheticImageQuestion(entryId: String): MistakePdfPaperQuestion =
        MistakePdfPaperQuestion(
            entryId = entryId,
            title = "图题",
            subtitle = "MATH · 第 1 版",
            blocks = listOf(
                MistakePdfBlock.Image(
                    id = "img-1",
                    localUri = "content://secret/diagram.png",
                    title = "题图",
                ),
            ),
            sourceRevisionId = "revision-$entryId",
        )

    private fun syntheticFigureQuestion(entryId: String): MistakePdfPaperQuestion =
        MistakePdfPaperQuestion(
            entryId = entryId,
            title = "图形题",
            subtitle = "MATH · 第 1 版",
            blocks = listOf(
                MistakePdfBlock.Figure(
                    id = "fig-1",
                    title = "示意图",
                    alternativeText = "一个坐标系",
                    schema = FigureSchema.SymbolTable(headers = emptyList(), rows = emptyList()),
                ),
            ),
            sourceRevisionId = "revision-$entryId",
        )

    private fun stateWithCleanImage(
        entryId: String,
        title: String,
        paragraphText: String,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = entryId,
                    problemId = "problem-$entryId",
                    problemRevisionId = "revision-$entryId",
                    revisionNumber = 3,
                    title = title,
                    subject = "MATH",
                ),
                fallbackMarkdown = "备用题面",
                source = MistakeSourceSet.Present(
                    listOf(
                        MistakeSourceAsset(
                            role = "CLEAN_IMAGE",
                            sourceAssetId = "asset-$entryId",
                            contentSha256 = "a".repeat(64),
                            mimeType = "image/png",
                            byteSize = 128L,
                            width = 640,
                            height = 480,
                            sourceType = "CAMERA",
                            createdAtEpochMillis = 1L,
                            location = MistakeSourceLocation.Available(
                                localUri = "content://secret/clean.png",
                            ),
                        ),
                    ),
                ),
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

    private fun readyState(
        entryId: String,
        title: String,
        paragraphText: String,
    ): MistakeDetailState.Ready {
        val block = ContentBlock.Paragraph("stem-$entryId", paragraphText)
        return MistakeDetailState.Ready(
            detail = MistakeDetail(
                identity = MistakeDetailIdentity(
                    errorBookEntryId = entryId,
                    problemId = "problem-$entryId",
                    problemRevisionId = "revision-$entryId",
                    revisionNumber = 3,
                    title = title,
                    subject = "MATH",
                ),
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
}
