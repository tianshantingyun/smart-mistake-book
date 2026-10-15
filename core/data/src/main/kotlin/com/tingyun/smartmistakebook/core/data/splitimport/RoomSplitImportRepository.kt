package com.tingyun.smartmistakebook.core.data.splitimport

import com.tingyun.smartmistakebook.core.database.CreateSplitImportJobCommand
import com.tingyun.smartmistakebook.core.database.SplitImportQuestionSeed
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.SplitImportJobSummary
import com.tingyun.smartmistakebook.core.domain.SplitImportQuestionSummary
import com.tingyun.smartmistakebook.core.domain.SplitImportConfirmState
import com.tingyun.smartmistakebook.core.domain.SplitImportRepository
import com.tingyun.smartmistakebook.core.domain.SplitImportRecovery
import com.tingyun.smartmistakebook.core.domain.SplitRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomSplitImportRepository(
    private val database: StudyDatabasePort,
) : SplitImportRepository {
    internal suspend fun createSplitJob(
        command: CreateSplitImportJobCommand,
        questions: List<SplitImportQuestionSeed>,
    ): com.tingyun.smartmistakebook.core.database.SplitImportJobRecord =
        database.createSplitImportJob(command, questions)

    internal suspend fun markReady(jobId: String, questionCount: Int, occurredAtEpochMillis: Long): Boolean =
        database.markSplitImportReady(jobId, questionCount, occurredAtEpochMillis)

    /**
     * Completes split jobs that were created but never flipped to READY, so the
     * review screen stops offering work it refuses to operate on. See
     * [SplitImportRecovery] for why a PREPARING job always has its questions and
     * why the grace window is needed. Returns how many jobs were promoted.
     *
     * Promotion reuses the stored `questionCount` rather than recomputing it, so
     * a job whose count is already wrong keeps that count instead of gaining a
     * second, different one.
     *
     * Public (unlike [createSplitJob] and [markReady], which only this module
     * needs) because the repair is driven from application startup.
     */
    suspend fun reconcileStuckJobs(nowEpochMillis: Long): Int =
        withContext(Dispatchers.IO) {
            database.observeActiveSplitImports().first()
                .filter { job ->
                    SplitImportRecovery.isStuckPreparing(
                        status = job.status,
                        createdAtEpochMillis = job.createdAtEpochMillis,
                        nowEpochMillis = nowEpochMillis,
                    )
                }
                .count { job -> markReady(job.jobId, job.questionCount, nowEpochMillis) }
        }
    override fun observeActiveImports(): Flow<List<SplitImportJobSummary>> =
        database.observeActiveSplitImports().map { jobs -> jobs.map(SplitImportJobSummaryMapper::toSummary) }

    override suspend fun readImport(jobId: String): SplitImportJobSummary? =
        withContext(Dispatchers.IO) {
            database.readSplitImportJob(jobId)?.let(SplitImportJobSummaryMapper::toSummary)
        }

    override suspend fun updateSelection(
        jobId: String,
        questionOrdinal: Int,
        selected: Boolean,
        occurredAtEpochMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        database.updateSplitImportSelection(jobId, questionOrdinal, selected, occurredAtEpochMillis)
    }

    override suspend fun markConfirmed(
        jobId: String,
        questionOrdinal: Int,
        confirmState: String,
        splitDraftId: String?,
        occurredAtEpochMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        database.markSplitImportQuestionConfirmed(
            jobId,
            questionOrdinal,
            confirmState,
            splitDraftId,
            occurredAtEpochMillis,
        )
    }

    /**
     * 提交回执（T4/AUD-03）：这一份草稿真的入库了，就把台账里对应的那道题置 SAVED。
     * 提交侧只知道草稿 id，所以反查在数据库侧按 `split_draft_id` 做（那一列上有索引）。
     */
    override suspend fun recordCommittedDraft(
        splitDraftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        database.markSplitImportQuestionSavedByDraftId(splitDraftId, occurredAtEpochMillis)
    }

    /**
     * 逐题续做（T4/AUD-03）：同一分题任务里**序号最小的那道未提交题**的草稿 id。
     *
     * 三个条件都必要：勾选的才可能被录入（未勾选的题学生本来就不要）、PENDING 才算未提交
     * （SAVED 的题已经进错题本）、必须有草稿才打得开。刚提交的这份草稿明确排除，这样即使
     * 回执那一笔没落上，也不会把学生原地送回同一题。
     */
    override suspend fun readNextUnsubmittedDraft(splitDraftId: String): String? =
        withContext(Dispatchers.IO) {
            val job = database.readSplitImportJobByDraft(splitDraftId) ?: return@withContext null
            job.questions
                .filter { question ->
                    question.selected &&
                        question.confirmState == SplitImportConfirmState.PENDING &&
                        question.splitDraftId != null &&
                        question.splitDraftId != splitDraftId
                }
                .minByOrNull { it.questionOrdinal }
                ?.splitDraftId
        }

    override suspend fun complete(jobId: String, occurredAtEpochMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            database.completeSplitImportJob(jobId, occurredAtEpochMillis)
        }

    override suspend fun abandon(jobId: String, occurredAtEpochMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            database.abandonSplitImportJob(jobId, occurredAtEpochMillis)
        }
}

object SplitImportRepositoryFactory {
    fun create(database: StudyDatabasePort): SplitImportRepository =
        RoomSplitImportRepository(database)

    fun createConcrete(database: StudyDatabasePort): RoomSplitImportRepository =
        RoomSplitImportRepository(database)
}

internal object SplitImportJobSummaryMapper {
    fun toSummary(record: com.tingyun.smartmistakebook.core.database.SplitImportJobRecord) =
        SplitImportJobSummary(
            jobId = record.jobId,
            sourceKind = record.sourceKind,
            pageCount = record.pageCount,
            questionCount = record.questionCount,
            status = record.status,
            createdAtEpochMillis = record.createdAtEpochMillis,
            updatedAtEpochMillis = record.updatedAtEpochMillis,
            sourceUri = record.sourceUri,
            questions = record.questions.map { question ->
                SplitImportQuestionSummary(
                    jobId = question.jobId,
                    questionOrdinal = question.questionOrdinal,
                    pageIndex = question.pageIndex,
                    region = SplitRegion(
                        left = question.left,
                        top = question.top,
                        right = question.right,
                        bottom = question.bottom,
                    ),
                    selected = question.selected,
                    confirmState = question.confirmState,
                    splitDraftId = question.splitDraftId,
                )
            },
        )
}