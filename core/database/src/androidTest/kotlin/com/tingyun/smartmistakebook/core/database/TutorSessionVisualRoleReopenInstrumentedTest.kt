package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorToolFigureTrace
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-06（D1 设备轨补强）：**保存 → 进程式关库 → 重开 → 详情仍有图**。
 *
 * 消灭的失败：T2a 的保存复制用例用的是内存库，能证明同一连接里角色不混，却证不了
 * "落盘后进程重启，详情读取仍能看到本会话生成的干净题面图与讲解图"。这里用文件库走
 * 真实提交，`close()` 后重新 `open()`（等价于进程被杀后重开），再从详情读回角色与资产。
 *
 * 注意：这是数据库层的进程式重开，不是 `killProcess` 级 UI 重启；完整 UI 链在 D1 报告中
 * 单列为未验证项。（生成步骤由 fake 生成器产出的资产行直接登记，不发起真实模型调用。）
 */
@RunWith(AndroidJUnit4::class)
class TutorSessionVisualRoleReopenInstrumentedTest {
    @Test
    fun committedTutorFiguresSurviveProcessStyleCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "wg06-visual-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        var store = StudyDatabaseFactory.open(context, databaseName)
        val entryId: String
        try {
            store.createProblemDraft(createCommand())
            val confirmed = document("已知函数 f(x)=x²，判断它的单调性。")
            store.confirmTutorSession(
                ConfirmTutorSessionCommand(
                    sessionId = SESSION_ID,
                    draftId = DRAFT_ID,
                    expectedRevisionNumber = 1,
                    confirmedRevision = ProblemDraftRevisionRecord(
                        draftId = DRAFT_ID,
                        revisionNumber = 2,
                        basisRevisionNumber = 1,
                        subject = "MATH",
                        title = "函数单调性讲解",
                        questionDocument = confirmed,
                        documentFingerprint = CapturedQuestionDocumentFingerprint.of(confirmed),
                        author = StudyDbValue.ProblemDraftAuthor.USER,
                        createdAtEpochMillis = 2_000,
                    ),
                    createdAtEpochMillis = 2_000,
                ),
            )

            val clean = generatedAsset(CLEAN_ASSET_ID, "1")
            val explanation = generatedAsset(EXPLANATION_ASSET_ID, "2")
            store.registerCanonicalSourceAsset(clean)
            store.registerCanonicalSourceAsset(explanation)
            store.appendTutorAssistantMessage(
                AppendTutorAssistantMessageDatabaseCommand(
                    conversationId = CONVERSATION_ID,
                    messageId = "assistant-1",
                    replyToMessageId = null,
                    bodyMarkdown = "题面重绘完成，另附过程图。",
                    toolTraceJson = traceOf(
                        TutorFigureKind.REDRAW_PROBLEM to CLEAN_ASSET_ID,
                        TutorFigureKind.GENERATE_PROCESS to EXPLANATION_ASSET_ID,
                    ),
                    sourceImageAssetIds = listOf(CLEAN_ASSET_ID, EXPLANATION_ASSET_ID),
                    logicalOperationId = "request-1",
                    status = "SUCCEEDED",
                    createdAtEpochMillis = 3_000,
                    completedAtEpochMillis = 3_000,
                    errorCode = null,
                ),
            )

            entryId = store.commitTutorSession(
                CommitTutorSessionCommand(SESSION_ID, commitCommand()),
            ).commit.receipt.errorBookEntryId
        } finally {
            store.close()
        }

        // 进程式重开：新连接读同一文件库。
        store = StudyDatabaseFactory.open(context, databaseName)
        try {
            val detail = checkNotNull(store.readMistakeDetail(entryId)) {
                "重开后详情必须仍可读"
            }
            val rolesByAsset = detail.sourceAssets.associate { asset ->
                asset.sourceAsset.sourceAssetId to asset.role
            }
            assertEquals("QUESTION_SOURCE", rolesByAsset[ASSET_ID])
            assertEquals(
                "重开后干净题面图仍在详情，且角色不被猜成题目原图",
                "CLEAN_IMAGE",
                rolesByAsset[CLEAN_ASSET_ID],
            )
            assertEquals(
                "重开后讲解图仍在详情，且角色不与题面混",
                "TUTOR_EXPLANATION",
                rolesByAsset[EXPLANATION_ASSET_ID],
            )
            assertEquals(3, detail.sourceAssets.size)
            assertNotNull(
                "被引用的生成图资产必须仍可读",
                store.readCanonicalSourceAsset(CLEAN_ASSET_ID),
            )
            assertTrue(
                "详情图片列表必须包含可渲染的生成图行",
                detail.sourceAssets.any { it.sourceAsset.sourceAssetId == CLEAN_ASSET_ID },
            )
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createCommand() = CreateProblemDraftCommand(
        sourceAsset = CanonicalSourceAssetRecord(
            sourceAssetId = ASSET_ID,
            contentSha256 = "a".repeat(64),
            relativePath = "source-assets/${"a".repeat(64)}.jpg",
            mimeType = "image/jpeg",
            byteSize = 4_096,
            width = 1_200,
            height = 1_600,
            sourceType = StudyDbValue.SourceAssetType.PHOTO_PICKER,
            createdAtEpochMillis = 1_000,
        ),
        draftId = DRAFT_ID,
        origin = StudyDbValue.CaptureOrigin.TUTOR,
        initialRevision = ProblemDraftRevisionRecord(
            draftId = DRAFT_ID,
            revisionNumber = 1,
            basisRevisionNumber = null,
            subject = null,
            title = "待校对题目",
            questionDocument = document("原题照片转写"),
            documentFingerprint = CapturedQuestionDocumentFingerprint.of(
                document("原题照片转写"),
            ),
            author = StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT,
            createdAtEpochMillis = 1_000,
        ),
    )

    private fun commitCommand() = CommitProblemDraftCommand(
        commandId = "commit-tutor-visual-reopen",
        draftId = DRAFT_ID,
        expectedRevisionNumber = 2,
        problemId = PROBLEM_ID,
        problemRevisionId = REVISION_ID,
        practiceUnitId = "practice-visual-reopen",
        errorBookEntryId = "entry-visual-reopen",
        estimatedSeconds = 180,
        committedAtEpochMillis = 4_000,
    )

    private fun generatedAsset(id: String, hashCharacter: String) = CanonicalSourceAssetRecord(
        sourceAssetId = id,
        contentSha256 = hashCharacter.repeat(64),
        relativePath = "source-assets/$id.png",
        mimeType = "image/png",
        byteSize = 2_048,
        width = 1_024,
        height = 1_024,
        sourceType = StudyDbValue.SourceAssetType.GENERATED_FIGURE,
        createdAtEpochMillis = 1_500,
    )

    private fun document(markdown: String) = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "document-tutor-visual-reopen",
            title = "题目转写",
            blocks = listOf(ContentBlock.Paragraph("stem", markdown)),
        ),
        blockEvidence = listOf(
            QuestionBlockEvidence(
                blockId = "stem",
                sourceAssetId = ASSET_ID,
                sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                writingLayer = WritingLayer.PRINTED,
                provenance = QuestionBlockProvenance.USER_CORRECTION,
                reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
            ),
        ),
    )

    private fun traceOf(vararg figures: Pair<TutorFigureKind, String>): String =
        checkNotNull(
            encodeTutorTurnToolTrace(
                TutorTurnToolTrace(
                    entries = figures.map { (kind, figureId) ->
                        TutorToolTraceEntry(
                            tool = TutorToolName.GENERATE_FIGURE,
                            ok = true,
                            figure = TutorToolFigureTrace(
                                kind = kind,
                                figureId = figureId,
                                generatedNow = true,
                            ),
                        )
                    },
                ),
            ),
        )

    private companion object {
        const val SESSION_ID = "tutor-session-visual-reopen"
        const val CONVERSATION_ID = "tutor-conv:captured:tutor-session-visual-reopen"
        const val DRAFT_ID = "draft-tutor-visual-reopen"
        const val PROBLEM_ID = "problem-tutor-visual-reopen"
        const val REVISION_ID = "revision-tutor-visual-reopen"
        const val ASSET_ID = "asset-tutor-source-reopen"
        const val CLEAN_ASSET_ID = "figure-clean-reopen"
        const val EXPLANATION_ASSET_ID = "figure-explain-reopen"
    }
}
