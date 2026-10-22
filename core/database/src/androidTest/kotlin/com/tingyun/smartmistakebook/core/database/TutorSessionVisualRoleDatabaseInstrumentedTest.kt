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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 4E WG-06 T2a：讲题会话「保存到错题本」时，**本会话助手消息的视觉引用**必须与提交回执
 * 在同一个事务里复制到 `problem_revision_source_asset`，且角色来自生成痕迹里的 kind。
 *
 * 这些用例钉住四个用户可见后果：
 * 1. 保存后详情/导出能看到本轮生成的干净题面图与讲解图，且两者角色不混；
 * 2. 已经在 `QUESTION_SOURCE` 里的原题照片不会被复制成第二份（否则详情会把原图当生成图）；
 * 3. 旧行角色 NULL 不猜（跳过），不会把未知图塞进题面；
 * 4. 删除讲题会话只删对话，不删已保存题目的视觉引用（删会话保题）。
 */
@RunWith(AndroidJUnit4::class)
class TutorSessionVisualRoleDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: StudyDatabasePort

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = StudyDatabaseFactory.openInMemory(context)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun savingCopiesAssistantFigureRolesFromTheToolTrace() = runBlocking {
        seedConfirmedTutorSession()
        val clean = generatedAsset("figure-clean", "c")
        val explanation = generatedAsset("figure-explain", "d")
        val studentPhoto = generatedAsset("student-photo", "e")
        store.registerCanonicalSourceAsset(clean)
        store.registerCanonicalSourceAsset(explanation)
        store.registerCanonicalSourceAsset(studentPhoto)

        store.appendTutorStudentMessage(
            AppendTutorStudentMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = "student-1",
                bodyMarkdown = "这一步怎么来的？",
                logicalOperationId = "request-0",
                createdAtEpochMillis = 2_500,
                sourceImageAssetIds = listOf("student-photo"),
            ),
        )
        store.appendTutorAssistantMessage(
            AppendTutorAssistantMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = "assistant-1",
                replyToMessageId = "student-1",
                bodyMarkdown = "我重画了题面，也补了一张过程图。",
                toolTraceJson = traceOf(
                    TutorFigureKind.REDRAW_PROBLEM to "figure-clean",
                    TutorFigureKind.GENERATE_PROCESS to "figure-explain",
                ),
                sourceImageAssetIds = listOf("figure-clean", "figure-explain"),
                logicalOperationId = "request-1",
                status = "SUCCEEDED",
                createdAtEpochMillis = 3_000,
                completedAtEpochMillis = 3_000,
                errorCode = null,
            ),
        )

        // 消息链先各自带上角色（T2a 的 trace kind→role），保存事务复制的是同一份事实。
        assertEquals(
            "CLEAN_IMAGE",
            store.readTutorMessageSourceAssets(listOf("assistant-1"))
                .single { it.sourceAssetId == "figure-clean" }
                .role,
        )
        assertEquals(
            "TUTOR_EXPLANATION",
            store.readTutorMessageSourceAssets(listOf("assistant-1"))
                .single { it.sourceAssetId == "figure-explain" }
                .role,
        )
        assertNull(
            "学生消息附图不是生成图，角色保持未知（不猜）",
            store.readTutorMessageSourceAssets(listOf("student-1")).single().role,
        )

        val receipt = store.commitTutorSession(
            CommitTutorSessionCommand(SESSION_ID, commitCommand()),
        ).commit.receipt

        val detail = checkNotNull(store.readMistakeDetail(receipt.errorBookEntryId))
        val rolesByAsset = detail.sourceAssets.associate { asset ->
            asset.sourceAsset.sourceAssetId to asset.role
        }
        assertEquals("QUESTION_SOURCE", rolesByAsset[ASSET_ID])
        assertEquals("CLEAN_IMAGE", rolesByAsset["figure-clean"])
        assertEquals("TUTOR_EXPLANATION", rolesByAsset["figure-explain"])
        assertFalse("学生附图不随保存进题面", "student-photo" in rolesByAsset)
        assertEquals(
            "同一角色下按消息顺序排号",
            listOf(0),
            detail.sourceAssets.filter { it.role == "CLEAN_IMAGE" }
                .map { asset -> ordinalOf(receipt.problemRevisionId, asset.sourceAsset.sourceAssetId) },
        )
    }

    @Test
    fun savingDoesNotDuplicateAnAssetThatIsAlreadyQuestionSource() = runBlocking {
        seedConfirmedTutorSession()
        // 同一张原题照片在消息里被错误地当作 CLEAN_IMAGE 图引用：保存时不得复制成第二份。
        store.appendTutorAssistantMessage(
            AppendTutorAssistantMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = "assistant-duplicate",
                replyToMessageId = null,
                bodyMarkdown = "原图重绘好了。",
                toolTraceJson = traceOf(TutorFigureKind.REDRAW_PROBLEM to ASSET_ID),
                sourceImageAssetIds = listOf(ASSET_ID),
                logicalOperationId = "request-duplicate",
                status = "SUCCEEDED",
                createdAtEpochMillis = 3_000,
                completedAtEpochMillis = 3_000,
                errorCode = null,
            ),
        )

        val receipt = store.commitTutorSession(
            CommitTutorSessionCommand(SESSION_ID, commitCommand()),
        ).commit.receipt

        assertEquals(
            "原题照片只保留 QUESTION_SOURCE 一条",
            listOf("QUESTION_SOURCE"),
            sourceLinkRoles(receipt.problemRevisionId),
        )
        assertEquals(
            "1",
            scalar(
                "SELECT COUNT(*) FROM problem_revision_source_asset " +
                    "WHERE problem_revision_id = '$REVISION_ID' AND source_asset_id = '$ASSET_ID'",
            ),
        )
    }

    @Test
    fun legacyMessageLinksWithoutARoleAreSkippedInsteadOfGuessed() = runBlocking {
        seedConfirmedTutorSession()
        val legacy = generatedAsset("figure-legacy", "f")
        store.registerCanonicalSourceAsset(legacy)
        // 旧写入路径：有图引用、没有痕迹、没有角色（迁移后读回 NULL）。
        store.appendTutorAssistantMessage(
            AppendTutorAssistantMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = "assistant-legacy",
                replyToMessageId = null,
                bodyMarkdown = "这是之前的配图。",
                toolTraceJson = null,
                sourceImageAssetIds = listOf("figure-legacy"),
                logicalOperationId = "request-legacy",
                status = "SUCCEEDED",
                createdAtEpochMillis = 2_000,
                completedAtEpochMillis = 2_000,
                errorCode = null,
            ),
        )
        assertNull(
            store.readTutorMessageSourceAssets(listOf("assistant-legacy")).single().role,
        )

        val receipt = store.commitTutorSession(
            CommitTutorSessionCommand(SESSION_ID, commitCommand()),
        ).commit.receipt

        assertEquals(
            listOf("QUESTION_SOURCE"),
            sourceLinkRoles(receipt.problemRevisionId),
        )
        assertNull(
            "NULL 角色不得被猜成 CLEAN_IMAGE/TUTOR_EXPLANATION",
            scalar(
                "SELECT source_asset_id FROM problem_revision_source_asset " +
                    "WHERE problem_revision_id = '$REVISION_ID' " +
                    "AND source_asset_id = 'figure-legacy'",
            ),
        )
    }

    @Test
    fun deletingTheTutorConversationKeepsTheCommittedRevisionLinks() = runBlocking {
        seedConfirmedTutorSession()
        // 哈希不能与 createCommand() 的原题资产（"a"*64）相同：canonical_source_asset 的
        // content_sha256 唯一，INSERT OR IGNORE 会把这张图静默丢掉，后续消息引用行触发 FK 失败。
        val clean = generatedAsset("figure-kept", "b")
        store.registerCanonicalSourceAsset(clean)
        store.appendTutorAssistantMessage(
            AppendTutorAssistantMessageDatabaseCommand(
                conversationId = CONVERSATION_ID,
                messageId = "assistant-kept",
                replyToMessageId = null,
                bodyMarkdown = "题面重绘完成。",
                toolTraceJson = traceOf(TutorFigureKind.REDRAW_PROBLEM to "figure-kept"),
                sourceImageAssetIds = listOf("figure-kept"),
                logicalOperationId = "request-kept",
                status = "SUCCEEDED",
                createdAtEpochMillis = 3_000,
                completedAtEpochMillis = 3_000,
                errorCode = null,
            ),
        )
        val receipt = store.commitTutorSession(
            CommitTutorSessionCommand(SESSION_ID, commitCommand()),
        ).commit.receipt
        assertEquals(
            setOf("QUESTION_SOURCE", "CLEAN_IMAGE"),
            sourceLinkRoles(receipt.problemRevisionId).toSet(),
        )

        store.deleteTutorConversation(CONVERSATION_ID)

        assertEquals(
            "删会话只删对话：题目的视觉引用仍在",
            setOf("QUESTION_SOURCE", "CLEAN_IMAGE"),
            sourceLinkRoles(receipt.problemRevisionId).toSet(),
        )
        assertNotNull(
            "被题目 revision 引用的图资产必须仍可读（RESTRICT 不许被级联删）",
            store.readCanonicalSourceAsset("figure-kept"),
        )
        assertTrue(
            "消息引用随会话级联删除",
            store.readTutorMessageSourceAssets(listOf("assistant-kept")).isEmpty(),
        )
    }

    private suspend fun seedConfirmedTutorSession() {
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
        commandId = "commit-tutor-visual",
        draftId = DRAFT_ID,
        expectedRevisionNumber = 2,
        problemId = PROBLEM_ID,
        problemRevisionId = REVISION_ID,
        practiceUnitId = "practice-visual",
        errorBookEntryId = "entry-visual",
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
            id = "document-tutor-visual",
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

    private suspend fun sourceLinkRoles(revisionId: String): List<String> =
        queryStrings(
            "SELECT role FROM problem_revision_source_asset " +
                "WHERE problem_revision_id = '$revisionId' ORDER BY role ASC",
        )

    private suspend fun ordinalOf(revisionId: String, assetId: String): Int =
        checkNotNull(
            scalar(
                "SELECT ordinal FROM problem_revision_source_asset " +
                    "WHERE problem_revision_id = '$revisionId' AND source_asset_id = '$assetId'",
            ),
        ).toInt()

    private suspend fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        (store as RoomStudyDatabase).database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                while (statement.step()) values += checkNotNull(statement.getText(0))
            }
        }
        return values
    }

    private suspend fun scalar(sql: String): String? {
        var value: String? = null
        (store as RoomStudyDatabase).database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                if (statement.step()) value = statement.getText(0)
            }
        }
        return value
    }

    private companion object {
        const val SESSION_ID = "tutor-session-visual"
        const val CONVERSATION_ID = "tutor-conv:captured:tutor-session-visual"
        const val DRAFT_ID = "draft-tutor-visual"
        const val PROBLEM_ID = "problem-tutor-visual"
        const val REVISION_ID = "revision-tutor-visual"
        const val ASSET_ID = "asset-tutor-source"
    }
}
