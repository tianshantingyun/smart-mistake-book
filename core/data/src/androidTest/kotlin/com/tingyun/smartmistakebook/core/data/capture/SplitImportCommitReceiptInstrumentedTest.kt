package com.tingyun.smartmistakebook.core.data.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.splitimport.SplitImportRepositoryFactory
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T4 / AUD-03：切分题的 SAVED 只由**提交回执**写成，且逐题流转从最早的未提交题续。
 *
 * 这一组用真实 Room（[CaptureWorkflowTestBase]）跑真实的切分 + 真实的入库提交：夹具不是
 * 手写的台账行，而是 `splitDraft` 走完整条链之后留下的作业与题。
 *
 * 消灭的失败：复核页在打开任何草稿前就把勾选题标成 SAVED（学生只录了第一题，其余题却显示
 * 「已录入」），以及真正入库之后没有任何生产回写方。
 */
@RunWith(AndroidJUnit4::class)
class SplitImportCommitReceiptInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun committingOneQuestionMarksOnlyThatQuestionSavedAndHandsOverTheEarliestUnsubmitted() =
        runBlocking {
            val split = splitThreeQuestionPage()
            val jobId = requireNotNull(split.splitJobId) { "A capture split must register a job" }
            val splitImports = SplitImportRepositoryFactory.createConcrete(database)
            // 切分只建作业、只挂草稿，绝不预标 SAVED：三题都是待提交。
            assertEquals(
                List(3) { StudyDbValue.SplitImportConfirmState.PENDING },
                confirmStates(jobId),
            )

            val secondDraftId = split.splitDrafts[1].draftId
            val current = checkNotNull(repository.readPendingCapture(secondDraftId))
            val committed = repository.confirmAndCommit(
                ConfirmCapturedProblemRequest(
                    requestId = "commit-second-question",
                    draftId = secondDraftId,
                    expectedRevisionNumber = current.currentRevisionNumber,
                    subject = "MATH",
                    title = "第 2 题",
                    transcription = "求函数 f(x)=x^2 的单调区间。",
                    writingLayer = CaptureWritingLayer.PRINTED,
                    transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
                    occurredAtEpochMillis = 30_000,
                ),
            )
            assertTrue(committed.created)

            // 提交成功的那一题变 SAVED，其余两题仍是 PENDING。
            assertEquals(
                listOf(
                    StudyDbValue.SplitImportConfirmState.PENDING,
                    StudyDbValue.SplitImportConfirmState.SAVED,
                    StudyDbValue.SplitImportConfirmState.PENDING,
                ),
                confirmStates(jobId),
            )
            // 逐题续做：从最早的未提交题续，而不是"序号 + 1"。
            assertEquals(
                split.splitDrafts[0].draftId,
                splitImports.readNextUnsubmittedDraft(secondDraftId),
            )
        }

    @Test
    fun committingADraftThatIsNotInTheLedgerLeavesEveryQuestionPending() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        val splitImports = SplitImportRepositoryFactory.createConcrete(database)
        val plainDraft = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "plain-capture",
                localUri = privateUri(createPng(width = 120, height = 160)).toString(),
                source = CaptureInputSource.CAMERA,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = 31_000,
            ),
        )

        val committed = repository.confirmAndCommit(
            ConfirmCapturedProblemRequest(
                requestId = "commit-plain-draft",
                draftId = plainDraft.draftId,
                expectedRevisionNumber = plainDraft.revisionNumber,
                subject = "MATH",
                title = "与切分无关的一道题",
                transcription = "普通录入的题面。",
                writingLayer = CaptureWritingLayer.PRINTED,
                transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
                occurredAtEpochMillis = 31_100,
            ),
        )
        assertTrue(committed.created)

        // 不是切分来的草稿，回执落在台账之外：三道题一道都不能被顺带标成已录入，
        // 也不该被当成"还有下一题"。
        assertEquals(
            List(3) { StudyDbValue.SplitImportConfirmState.PENDING },
            confirmStates(jobId),
        )
        assertNull(splitImports.readNextUnsubmittedDraft(plainDraft.draftId))
    }

    @Test
    fun committingEveryQuestionInTurnLeavesNothingToContinue() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        val splitImports = SplitImportRepositoryFactory.createConcrete(database)

        for (index in split.splitDrafts.indices) {
            val draft = split.splitDrafts[index]
            val current = checkNotNull(repository.readPendingCapture(draft.draftId))
            repository.confirmAndCommit(
                ConfirmCapturedProblemRequest(
                    requestId = "commit-question-$index",
                    draftId = draft.draftId,
                    expectedRevisionNumber = current.currentRevisionNumber,
                    subject = "MATH",
                    title = "第 ${index + 1} 题",
                    transcription = "第 ${index + 1} 题的题面。",
                    writingLayer = CaptureWritingLayer.PRINTED,
                    transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
                    occurredAtEpochMillis = 32_000L + index,
                ),
            )
            val expectedNext = split.splitDrafts
                .drop(index + 1)
                .map { it.draftId }
                .firstOrNull()
            assertEquals(expectedNext, splitImports.readNextUnsubmittedDraft(draft.draftId))
        }

        // 全部逐题入库之后没有下一题，台账也整整齐齐都是 SAVED。
        assertEquals(
            List(3) { StudyDbValue.SplitImportConfirmState.SAVED },
            confirmStates(jobId),
        )
    }

    /** 一页三道题：真的走 `splitDraft`，作业与每题草稿都由生产链留下。 */
    private suspend fun splitThreeQuestionPage(): CaptureDraftSplitResult {
        val original = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "split-three-questions",
                localUri = privateUri(createPng(width = 200, height = 600)).toString(),
                source = CaptureInputSource.PHOTO_PICKER,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = 29_000,
            ),
        )
        return repository.splitDraft(
            SplitCaptureDraftRequest(
                requestId = "split-three-questions-once",
                draftId = original.draftId,
                expectedRevisionNumber = original.revisionNumber,
                assessmentRequestId = "capture-assess:split-three-questions",
                sourceAssetId = original.sourceAssetId,
                regions = listOf(
                    NormalizedSourceRegion(0.05, 0.02, 0.95, 0.30),
                    NormalizedSourceRegion(0.05, 0.35, 0.95, 0.63),
                    NormalizedSourceRegion(0.05, 0.68, 0.95, 0.96),
                ),
                occurredAtEpochMillis = 29_100,
            ),
        )
    }

    private suspend fun confirmStates(jobId: String): List<String> =
        checkNotNull(database.readSplitImportJob(jobId)).questions
            .sortedBy { it.questionOrdinal }
            .map { it.confirmState }
}
