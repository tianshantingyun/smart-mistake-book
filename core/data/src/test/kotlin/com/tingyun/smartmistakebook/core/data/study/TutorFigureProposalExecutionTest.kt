package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.data.model.TutorFigureGenerator
import com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest
import com.tingyun.smartmistakebook.core.data.model.TutorFigureResult
import com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftRecord
import com.tingyun.smartmistakebook.core.database.ProblemDraftRevisionRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorSessionRecord
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：模型选择的 IMAGE_MODEL EDIT/GENERATE 必须真正到达执行缝，并把
 * method/purpose/operationId/status 记进本地痕迹（T3）。
 *
 * 消灭的具体失败：解析层收下 method/imageOperation/imagePrompt，runner 却仍按 kind 走固定
 * 提示词——"模型能选"变成一句只存在于 JSON 里的假话；或者生成结束后痕迹只剩 id，看不出
 * 这次用的方法、逻辑操作与终态。
 */
class TutorFigureProposalExecutionTest {

    private val sessionId = "tutor-session-figure"

    private fun draft(sourceAsset: CanonicalSourceAssetRecord?) = ProblemDraftRecord(
        draftId = "draft-figure",
        sourceAsset = sourceAsset,
        origin = "TUTOR",
        status = "COMMITTED",
        currentRevision = ProblemDraftRevisionRecord(
            draftId = "draft-figure",
            revisionNumber = 1,
            basisRevisionNumber = null,
            subject = "MATH",
            title = "单调性练习",
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "question-1",
                    blocks = listOf(ContentBlock.Paragraph("stem-1", "求函数的单调区间。")),
                ),
                blockEvidence = emptyList(),
            ),
            documentFingerprint = "fp-figure-draft",
            author = "STUDENT",
            createdAtEpochMillis = 1_000,
        ),
        createdAtEpochMillis = 1_000,
        updatedAtEpochMillis = 1_000,
    )

    private fun sourceSheet() = CanonicalSourceAssetRecord(
        sourceAssetId = "asset-sheet-1",
        contentSha256 = "b".repeat(64),
        relativePath = "source-assets/sheet-1.png",
        mimeType = "image/png",
        byteSize = 1,
        width = 1,
        height = 1,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1_000,
    )

    private fun sessionRecord(draftId: String) = TutorSessionRecord(
        sessionId = sessionId,
        draftId = draftId,
        draftRevisionNumber = 1,
        createdAtEpochMillis = 1_000,
        origin = "CAPTURE",
        draftStatus = "COMMITTED",
        confirmedRevision = draft(null).currentRevision,
        sourceAsset = sourceSheet(),
        commitReceipt = null,
    )

    private class FigurePort(
        private val draft: ProblemDraftRecord?,
        private val session: TutorSessionRecord,
    ) : FakeStudyDatabasePort() {
        init {
            tutorSessions[session.sessionId] = session
        }

        override suspend fun readProblemDraft(draftId: String): ProblemDraftRecord? =
            draft?.takeIf { it.draftId == draftId }
    }

    private fun context() = RoomTutorToolRunner.Context(
        subject = "MATH",
        conversationId = "tutor-conv-1",
        tutorSessionId = sessionId,
    )

    private fun figureCall(
        kind: TutorFigureKind,
        operation: FigureImageOperation,
        prompt: String,
    ) = TutorToolCall(
        tool = TutorToolName.GENERATE_FIGURE,
        rationale = "图形是这道题的理解关键",
        figureKind = kind,
        figureDescription = "数轴标注导数符号区间",
        figureMethod = FigureGenerationMethod.IMAGE_MODEL,
        figureImageOperation = operation,
        figureImagePrompt = prompt,
    )

    @Test
    fun aChosenEditOperationAndPromptReachTheGenerator() = runBlocking {
        val requests = mutableListOf<TutorFigureRequest>()
        val runner = RoomTutorToolRunner(
            FigurePort(draft(sourceSheet()), sessionRecord("draft-figure")),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            TutorFigureGenerator { request ->
                requests += request
                TutorFigureResult(
                    figureId = "figure-1",
                    status = TutorFigureStatus.GENERATED,
                    model = "gpt-image-2",
                    generatedNow = true,
                    operationId = "figure-op:REDRAW_PROBLEM:figure-1",
                )
            },
        )

        val execution = runner.runTraced(
            figureCall(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                operation = FigureImageOperation.EDIT,
                prompt = "保留题干文字、标注和单位，只清除手写。",
            ),
            context(),
        )

        val request = requests.single()
        assertEquals(FigureImageOperation.EDIT, request.imageOperation)
        assertEquals("保留题干文字、标注和单位，只清除手写。", request.imagePrompt)
        assertEquals("asset-sheet-1", request.sourceSheet?.sourceAssetId)

        val figure = requireNotNull(execution.figure)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, figure.method)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, figure.purpose)
        assertEquals("figure-op:REDRAW_PROBLEM:figure-1", figure.operationId)
        assertEquals(FigureGenerationStatus.GENERATED, figure.status)
    }

    @Test
    fun aChosenGenerateOperationForAnExplanationReachesTheGenerator() = runBlocking {
        val requests = mutableListOf<TutorFigureRequest>()
        val runner = RoomTutorToolRunner(
            FigurePort(draft(sourceSheet()), sessionRecord("draft-figure")),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            TutorFigureGenerator { request ->
                requests += request
                TutorFigureResult(
                    figureId = "figure-2",
                    status = TutorFigureStatus.GENERATED,
                    generatedNow = true,
                    operationId = "figure-op:GENERATE_PROCESS:figure-2",
                )
            },
        )

        val execution = runner.runTraced(
            figureCall(
                kind = TutorFigureKind.GENERATE_PROCESS,
                operation = FigureImageOperation.GENERATE,
                prompt = "画一条数轴，标出导数为正、负的区间。",
            ),
            context(),
        )

        val request = requests.single()
        assertEquals(FigureImageOperation.GENERATE, request.imageOperation)
        assertEquals("画一条数轴，标出导数为正、负的区间。", request.imagePrompt)
        assertNull("GENERATE 不携带源图", request.sourceSheet)
        assertEquals(
            FigureGenerationPurpose.EXPLANATION,
            requireNotNull(execution.figure).purpose,
        )
    }

    @Test
    fun editWithoutASourceIsAnEmptyScopeAndNeverCallsTheGenerator() = runBlocking {
        var generatorCalls = 0
        val runner = RoomTutorToolRunner(
            FigurePort(draft(null), sessionRecord("draft-figure")),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            TutorFigureGenerator {
                generatorCalls += 1
                TutorFigureResult(figureId = "should-not-exist", status = TutorFigureStatus.GENERATED)
            },
        )

        val execution = runner.runTraced(
            figureCall(
                kind = TutorFigureKind.GENERATE_PROCESS,
                operation = FigureImageOperation.EDIT,
                prompt = "在干净题面上补充辅助线。",
            ),
            context(),
        )

        assertTrue(execution.outcome.ok)
        assertTrue(execution.outcome.summaryMarkdown.contains("没有原图"))
        assertEquals(0, generatorCalls)
        val figure = requireNotNull(execution.figure)
        assertEquals(FigureGenerationStatus.NEED_SOURCE, figure.status)
        assertNull(figure.operationId)
    }

    @Test
    fun aCachedResultIsTracedAsCached() = runBlocking {
        val runner = RoomTutorToolRunner(
            FigurePort(draft(sourceSheet()), sessionRecord("draft-figure")),
            MutableStateFlow(KnowledgeBaseAvailability.Ready),
            TutorFigureGenerator {
                TutorFigureResult(
                    figureId = "figure-cached",
                    status = TutorFigureStatus.GENERATED,
                    model = "gpt-image-2",
                    generatedNow = false,
                    operationId = "figure-op:REDRAW_PROBLEM:figure-cached",
                )
            },
        )

        val execution = runner.runTraced(
            figureCall(
                kind = TutorFigureKind.REDRAW_PROBLEM,
                operation = FigureImageOperation.EDIT,
                prompt = "保留原题版式。",
            ),
            context(),
        )

        assertEquals(FigureGenerationStatus.CACHED, requireNotNull(execution.figure).status)
    }
}
