package com.tingyun.smartmistakebook.core.data.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.splitimport.SplitImportRepositoryFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.domain.CaptureDraftImportRequest
import com.tingyun.smartmistakebook.core.domain.CaptureDraftSplitResult
import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.CaptureInputSource
import com.tingyun.smartmistakebook.core.domain.CaptureTranscriptionReview
import com.tingyun.smartmistakebook.core.domain.CaptureWritingLayer
import com.tingyun.smartmistakebook.core.domain.SplitCaptureDraftRequest
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-39 / T1：**提交落库与台账回执之间被杀进程**留下的台账落后，由读侧对账追平。
 *
 * 消灭的失败：`confirmAndCommit` 两步不同事务——先落库（题真的进了错题本），再写台账回执；
 * 两步之间被杀，台账永远停在那道题的 PENDING 上。之后（a）复核页把已入库的题显示成「待提交」、
 * 点「录入所选」还会重新打开它，（b）逐题续做把"最早的未提交题"算成一道已经录过的题。
 *
 * 故障态的模拟是**逐字**的：用不带台账的仓库装配跑同一条生产提交路径（`recordSplitImportCommit`
 * 在拿到回执之后才写台账，装配里台账为 null 就写不成），留下的数据库状态与"两步之间被杀"相同。
 */
@RunWith(AndroidJUnit4::class)
class SplitImportReceiptReconciliationInstrumentedTest : CaptureWorkflowTestBase() {

    @Test
    fun aCommitThatLostItsReceiptIsCaughtUpWhenTheNextQuestionIsRead() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId) { "A capture split must register a job" }
        val firstDraftId = split.splitDrafts[0].draftId
        val secondDraftId = split.splitDrafts[1].draftId
        val thirdDraftId = split.splitDrafts[2].draftId

        // 第一题入了库、回执没写：草稿 COMMITTED，台账三题全是待提交。
        commitWithoutReceipt(firstDraftId, "commit-first-question", "第 1 题", 40_000)
        assertEquals(
            StudyDbValue.ProblemDraftStatus.COMMITTED,
            database.readProblemDraft(firstDraftId)?.status,
        )
        assertEquals(allPending(), confirmStates(jobId))

        // 「重启」：同一份持久数据上换一套进程内对象（杀进程恢复的等价物）。
        val restarted = SplitImportRepositoryFactory.createConcrete(database)

        // 学生录完第二题之后问「下一题」：对账把第一题追平，续做落在第三题。
        // 没有这一笔对账，第一题会被当成"最早的未提交题"，学生被送回一道已经录过的题。
        assertEquals(thirdDraftId, restarted.readNextUnsubmittedDraft(secondDraftId))
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            confirmStates(jobId),
        )
        // 幂等：再问一次答案不变，追平过的题也不会被翻回去。
        assertEquals(thirdDraftId, restarted.readNextUnsubmittedDraft(secondDraftId))
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            confirmStates(jobId),
        )
    }

    @Test
    fun theStartupReconciliationCatchesUpALostReceiptAndIsIdempotent() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        commitWithoutReceipt(split.splitDrafts[0].draftId, "commit-first-question", "第 1 题", 41_000)
        assertEquals(allPending(), confirmStates(jobId))

        // 「重启」：关掉数据库再打开同一份——持久数据照旧，进程内对象全部换新。
        reopenDatabase()
        val restarted = SplitImportRepositoryFactory.createConcrete(database)

        // 启动对账（`SmartMistakeBookApplication` 调的就是这一笔）把丢失的回执补上。
        assertEquals(1, restarted.reconcileCommittedQuestionDrafts(60_000))
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            confirmStates(jobId),
        )
        // 幂等：已经是 SAVED 的题不再进取数集，第二次对账 0 笔、状态不动。
        assertEquals(0, restarted.reconcileCommittedQuestionDrafts(61_000))
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            confirmStates(jobId),
        )
    }

    @Test
    fun theReviewAssemblyCatchesUpALostReceiptBeforeItShowsTheLedger() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        commitWithoutReceipt(split.splitDrafts[0].draftId, "commit-first-question", "第 1 题", 42_000)

        val restarted = SplitImportRepositoryFactory.createConcrete(database)
        val active = restarted.observeActiveImports().first()

        // 复核页装配（`observeActiveImports`）拿到的**第一份**台账就是追平后的：第一题「已录入」。
        val job = active.first { it.jobId == jobId }
        assertEquals(
            listOf(
                StudyDbValue.SplitImportConfirmState.SAVED,
                StudyDbValue.SplitImportConfirmState.PENDING,
                StudyDbValue.SplitImportConfirmState.PENDING,
            ),
            job.questions.sortedBy { it.questionOrdinal }.map { it.confirmState },
        )
        // 紧接着按同一份台账点「录入所选」：打开的是第二题，而不是重开第一题。
        assertEquals(
            split.splitDrafts[1].draftId,
            restarted.readNextUnsubmittedDraft(split.splitDrafts[0].draftId),
        )
    }

    @Test
    fun anAbandonedDraftIsNotACommitAndStaysPending() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        val abandonedDraftId = split.splitDrafts[0].draftId
        val current = checkNotNull(repository.readPendingCapture(abandonedDraftId))
        assertTrue(
            repository.abandonPendingCapture(
                draftId = abandonedDraftId,
                expectedRevisionNumber = current.currentRevisionNumber,
                abandonedAtEpochMillis = 43_000,
            ),
        )

        val restarted = SplitImportRepositoryFactory.createConcrete(database)
        // 废弃不是入库：草稿停在 ABANDONED，对账既不该补回执，也不该改动任何一行。
        assertEquals(0, restarted.reconcileCommittedQuestionDrafts(43_100))
        assertEquals(allPending(), confirmStates(jobId))
    }

    @Test
    fun aCommittedDraftOutsideTheLedgerIsNotInventedIntoIt() = runBlocking {
        val split = splitThreeQuestionPage()
        val jobId = requireNotNull(split.splitJobId)
        val plainDraft = repository.importDraft(
            CaptureDraftImportRequest(
                requestId = "plain-capture",
                localUri = privateUri(createPng(width = 120, height = 160)).toString(),
                source = CaptureInputSource.CAMERA,
                origin = CaptureEntryOrigin.LIBRARY,
                occurredAtEpochMillis = 44_000,
            ),
        )
        commitWithoutReceipt(plainDraft.draftId, "commit-plain-draft", "与切分无关的一道题", 44_100)

        val restarted = SplitImportRepositoryFactory.createConcrete(database)
        // 不是切分来的草稿入库：台账里没有可落账的题，一道也不许被顺带标成已录入。
        assertEquals(0, restarted.reconcileCommittedQuestionDrafts(44_200))
        assertEquals(allPending(), confirmStates(jobId))
    }

    /**
     * 「提交落库了、回执没写」的模拟：用**没有接台账**的仓库装配跑同一条生产提交路径。
     *
     * `RoomCaptureWorkflowRepository.confirmAndCommit` 是在拿到提交回执之后才写台账
     * （`recordSplitImportCommit`）；这里的装配 `splitImports` 为 null，于是这一步之后数据库就是
     * "进程在这两步之间被杀"留下的状态：草稿 COMMITTED、题进了错题本、台账没有回执。
     */
    private suspend fun commitWithoutReceipt(
        draftId: String,
        requestId: String,
        title: String,
        occurredAtEpochMillis: Long,
    ) {
        val receiptless = RoomCaptureWorkflowRepository(
            database,
            AndroidCanonicalAssetVault(context),
            noTextRecognizer(),
        )
        val request = ConfirmCapturedProblemRequest(
            requestId = requestId,
            draftId = draftId,
            expectedRevisionNumber = checkNotNull(repository.readPendingCapture(draftId))
                .currentRevisionNumber,
            subject = "MATH",
            title = title,
            transcription = "$title 的题面。",
            writingLayer = CaptureWritingLayer.PRINTED,
            transcriptionReview = CaptureTranscriptionReview.MANUAL_ENTRY,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        assertTrue(receiptless.confirmAndCommit(request).created)
    }

    /** 重开同一份数据库：持久数据照旧，进程内对象全部换新（杀进程恢复的等价物）。 */
    private fun reopenDatabase() {
        database.close()
        database = StudyDatabaseFactory.open(context, databaseName)
    }

    private suspend fun confirmStates(jobId: String): List<String> =
        checkNotNull(database.readSplitImportJob(jobId)).questions
            .sortedBy { it.questionOrdinal }
            .map { it.confirmState }

    private fun allPending(): List<String> =
        List(SPLIT_QUESTION_COUNT) { StudyDbValue.SplitImportConfirmState.PENDING }

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

    private companion object {
        const val SPLIT_QUESTION_COUNT = 3
    }
}
