package com.tingyun.smartmistakebook.feature.library

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.splitimport.SplitImportRepositoryFactory
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CreateProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.CreateSplitImportJobCommand
import com.tingyun.smartmistakebook.core.database.ProblemDraftRevisionRecord
import com.tingyun.smartmistakebook.core.database.SplitImportQuestionSeed
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.SplitImportRepository
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T4 / AUD-03：复核页的逐题提交与 SAVED 语义——用**真实 Room** 断言 confirm_state。
 *
 * 三条钉住的行为：
 *
 * 1. 点「录入所选」只打开最早的那道未提交题，**不预标 SAVED**（过去在这里就把勾选题全部
 *    标成已录入，学生只录了第一题、其余题却显示「已录入」）；
 * 2. 只有拿到提交回执（[SplitImportRepository.recordCommittedDraft]）的题才显示「已录入」，
 *    其余题如实显示「待提交」；恢复（重开数据库 = 同一份持久数据）之后从最早的未提交题续做；
 * 3. 全部逐题入库之后，收尾只把任务标记完成，不再打开任何草稿。
 *
 * 边界（如实声明）：这里的"恢复"是重开同一份数据库 + 重新组合，等价于杀进程后回到同一页；
 * 真正的 force-stop 恢复属于设备级走查，不在本用例内。
 */
@RunWith(AndroidJUnit4::class)
class SplitImportReviewCommitFlowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: StudyDatabasePort
    private lateinit var splitImports: SplitImportRepository

    private val jobId = "split:review-commit-flow:0"
    private val draftIds = listOf("draft-q1", "draft-q2", "draft-q3")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "split-review-commit-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = StudyDatabaseFactory.open(context, databaseName)
        splitImports = SplitImportRepositoryFactory.createConcrete(database)
        runBlocking {
            for (index in draftIds.indices) {
                seedQuestionDraft(
                    draftId = draftIds[index],
                    title = "切分题目${index + 1}",
                    occurredAt = 1_000L + index,
                )
            }
            seedJob()
        }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun tappingRecordSelectedOpensTheEarliestQuestionAndMarksNothingSaved() {
        var openedDraftId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                SplitImportReviewRoute(
                    repository = splitImports,
                    initialJobId = jobId,
                    onOpenDraft = { openedDraftId = it },
                    onFinished = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("split_review_confirm")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("split_review_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { openedDraftId != null }
        assertEquals(draftIds[0], openedDraftId)
        // 关键回归：点完「录入所选」之后，三题都还是 PENDING——没有提交回执就不许标 SAVED。
        assertEquals(allPending(), runBlocking { confirmStates() })
    }

    @Test
    fun theListResumesFromTheEarliestUnsubmittedQuestionWithTheRealConfirmState() {
        // 第一题真的入库了（生产提交路径写的就是这一笔），然后杀进程式地重开同一份数据库。
        runBlocking { splitImports.recordCommittedDraft(draftIds[0], 5_000) }
        reopenDatabase()

        var openedDraftId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                SplitImportReviewRoute(
                    repository = splitImports,
                    initialJobId = jobId,
                    onOpenDraft = { openedDraftId = it },
                    onFinished = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("已录入").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("已录入").assertExists()
        assertEquals(
            2,
            composeRule.onAllNodesWithText("待提交").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("split_review_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) { openedDraftId != null }
        // 恢复之后从最早的未提交题续，而不是从头再来。
        assertEquals(draftIds[1], openedDraftId)
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            runBlocking { confirmStates() },
        )
    }

    @Test
    fun aFullyCommittedJobIsRetiredInsteadOfOpeningAnotherDraft() {
        runBlocking {
            for (index in draftIds.indices) {
                splitImports.recordCommittedDraft(draftIds[index], 6_000L + index)
            }
        }
        var openedDraftId: String? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                SplitImportReviewRoute(
                    repository = splitImports,
                    initialJobId = jobId,
                    onOpenDraft = { openedDraftId = it },
                    onFinished = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("已录入").fetchSemanticsNodes().size == draftIds.size
        }

        composeRule.onNodeWithTag("split_review_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("split_review_empty").fetchSemanticsNodes().isNotEmpty()
        }
        // 全部入库的任务在复核页收尾：不再打开任何草稿。
        assertNull(openedDraftId)
        assertEquals(
            StudyDbValue.SplitImportStatus.COMPLETED,
            runBlocking { database.readSplitImportJob(jobId)?.status },
        )
    }

    /** 重开同一份数据库：持久数据照旧，进程内的对象全部换新（杀进程恢复的等价物）。 */
    private fun reopenDatabase() {
        database.close()
        database = StudyDatabaseFactory.open(context, databaseName)
        splitImports = SplitImportRepositoryFactory.createConcrete(database)
    }

    private suspend fun seedJob() {
        database.createSplitImportJob(
            CreateSplitImportJobCommand(
                jobId = jobId,
                sourceKind = StudyDbValue.SplitImportSourceKind.BATCH,
                sourceFingerprint = sha256Hex("split-review-job:$jobId"),
                sourceUri = "content://fixture/split-review-page",
                pageCount = 1,
                createdAtEpochMillis = 1_000,
            ),
            draftIds.mapIndexed { index, draftId ->
                SplitImportQuestionSeed(
                    left = 0.05,
                    top = 0.02 + index * 0.32,
                    right = 0.95,
                    bottom = 0.02 + index * 0.32 + 0.28,
                    pageIndex = 0,
                    prioritised = true,
                    splitDraftId = draftId,
                )
            },
        )
        database.markSplitImportReady(jobId, draftIds.size, 1_000)
    }

    /** 一道真实的问题草稿行：切分题要挂得上 `split_draft_id`，外键要求它真的存在。 */
    private suspend fun seedQuestionDraft(draftId: String, title: String, occurredAt: Long) {
        val contentSha = sha256Hex("split-review-draft:$draftId")
        val sourceAssetId = "asset-$draftId"
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-$draftId",
                title = title,
                blocks = listOf(ContentBlock.Paragraph("stem", "${title}的题面。")),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem",
                    sourceAssetId = sourceAssetId,
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.LOCAL_OCR,
                    // 非用户来源的证据必须带 producerVersion，否则 codec 判 MISSING_PRODUCER_VERSION。
                    producerVersion = "split-review-fixture-v1",
                    reviewStatus = QuestionBlockReviewStatus.CANDIDATE,
                ),
            ),
        )
        database.createProblemDraft(
            CreateProblemDraftCommand(
                sourceAsset = CanonicalSourceAssetRecord(
                    sourceAssetId = sourceAssetId,
                    contentSha256 = contentSha,
                    relativePath = "source-assets/$contentSha.png",
                    mimeType = "image/png",
                    byteSize = 2_048,
                    width = 200,
                    height = 400,
                    sourceType = StudyDbValue.SourceAssetType.CAMERA,
                    createdAtEpochMillis = occurredAt,
                ),
                draftId = draftId,
                origin = StudyDbValue.CaptureOrigin.LIBRARY,
                initialRevision = ProblemDraftRevisionRecord(
                    draftId = draftId,
                    revisionNumber = 1,
                    basisRevisionNumber = null,
                    subject = null,
                    title = title,
                    questionDocument = document,
                    documentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
                    author = StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT,
                    createdAtEpochMillis = occurredAt,
                ),
            ),
        )
    }

    private suspend fun confirmStates(): List<String> =
        checkNotNull(database.readSplitImportJob(jobId)).questions
            .sortedBy { it.questionOrdinal }
            .map { it.confirmState }

    private fun allPending(): List<String> =
        List(draftIds.size) { StudyDbValue.SplitImportConfirmState.PENDING }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
