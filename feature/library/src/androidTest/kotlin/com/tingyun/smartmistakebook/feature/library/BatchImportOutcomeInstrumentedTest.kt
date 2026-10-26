package com.tingyun.smartmistakebook.feature.library

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.capture.BatchImportRepositoryFactory
import com.tingyun.smartmistakebook.core.data.capture.CaptureWorkflowRepositoryFactory
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CommitProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.CreateBatchImportJobCommand
import com.tingyun.smartmistakebook.core.database.CreateProblemDraftCommand
import com.tingyun.smartmistakebook.core.database.ProblemDraftRevisionRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.BatchImportRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchImportOutcomeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: StudyDatabasePort
    private lateinit var repository: BatchImportRepository
    private var openedDraft: String? = null
    private var openedSavedEntry: String? = null

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "batch-outcome-${System.nanoTime()}.db"
        database = StudyDatabaseFactory.open(context, databaseName)
        repeat(2) { createDraft(it) }
        database.createBatchImportJob(
            CreateBatchImportJobCommand(
                jobId = "batch-outcome",
                requestId = "batch-outcome",
                requestFingerprint = "a".repeat(64),
                sourceUris = listOf("content://fixture/0", "content://fixture/1"),
                occurredAtEpochMillis = 1_000,
            ),
        )
        repeat(2) { index ->
            database.claimNextBatchImportPage("batch-outcome", 2_000L + index)
            database.completeBatchImportPage("batch-outcome", index, "draft-$index", 2_100L + index)
            database.settleBatchImportPageSplit("batch-outcome", index, 2_200L + index)
        }
        database.finishBatchImportIfSettled("batch-outcome", 2_300)
        // No processing is needed: exercise the real read projection while the draft changes.
        val stoppedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { it.cancel() }
        repository = BatchImportRepositoryFactory.create(
            context = context,
            database = database,
            capture = CaptureWorkflowRepositoryFactory.create(context, database),
            processingScope = stoppedScope,
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun committedPageStopsOfferingDraftResumeOnTheOpenBatchScreen() {
        showBatch()
        runBlocking {
            database.commitProblemDraft(
                CommitProblemDraftCommand(
                    commandId = "commit-page-0",
                    draftId = "draft-0",
                    expectedRevisionNumber = 1,
                    problemId = "problem-0",
                    problemRevisionId = "revision-0",
                    practiceUnitId = "practice-0",
                    errorBookEntryId = "entry-0",
                    estimatedSeconds = 180,
                    committedAtEpochMillis = 3_000,
                ),
            )
        }

        awaitLabel("已存入错题本，查看题目")
        composeRule.onNodeWithTag("batch_import_page_0").performClick()
        assertEquals("entry-0", openedSavedEntry)
        assertNull(openedDraft)
    }

    @Test
    fun removedPageStopsOfferingDraftResumeOnTheOpenBatchScreen() {
        showBatch()
        runBlocking { database.abandonPendingProblemDraft("draft-0", 1, 3_000) }

        awaitLabel("已移除")
        composeRule.onNodeWithTag("batch_import_page_0").assertIsNotEnabled()
    }

    private fun showBatch() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                BatchImportRoute(
                    repository,
                    onOpenDraft = { openedDraft = it },
                    onOpenSavedEntry = { openedSavedEntry = it },
                    onBack = {},
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已保存，点此继续").fetchSemanticsNodes().size == 2
        }
        composeRule.onNodeWithTag("batch_import_page_0").performScrollTo()
    }

    private fun awaitLabel(label: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(label).assertExists()
    }

    private suspend fun createDraft(index: Int) {
        val assetId = "asset-$index"
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document-$index",
                blocks = listOf(ContentBlock.Paragraph("stem-$index", "求 1 + 1 的值。")),
            ),
            blockEvidence = listOf(
                QuestionBlockEvidence(
                    blockId = "stem-$index",
                    sourceAssetId = assetId,
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.USER_CORRECTION,
                    confidence = null,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                ),
            ),
        )
        val hash = ('a' + index).toString().repeat(64)
        database.createProblemDraft(
            CreateProblemDraftCommand(
                sourceAsset = CanonicalSourceAssetRecord(
                    sourceAssetId = assetId,
                    contentSha256 = hash,
                    relativePath = "source-assets/$hash.jpg",
                    mimeType = "image/jpeg",
                    byteSize = 100,
                    width = 100,
                    height = 100,
                    sourceType = StudyDbValue.SourceAssetType.PHOTO_PICKER,
                    createdAtEpochMillis = 1_000,
                ),
                draftId = "draft-$index",
                origin = StudyDbValue.CaptureOrigin.LIBRARY,
                initialRevision = ProblemDraftRevisionRecord(
                    draftId = "draft-$index",
                    revisionNumber = 1,
                    basisRevisionNumber = null,
                    subject = "MATH",
                    title = "加法题",
                    questionDocument = document,
                    documentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
                    author = StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT,
                    createdAtEpochMillis = 1_000,
                ),
            ),
        )
    }
}
