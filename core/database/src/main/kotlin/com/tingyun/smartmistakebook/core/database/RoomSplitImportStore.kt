package com.tingyun.smartmistakebook.core.database

import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.dao.SplitImportDao
import com.tingyun.smartmistakebook.core.database.dao.SplitImportJobWithQuestions
import com.tingyun.smartmistakebook.core.database.entity.SplitImportJobEntity
import com.tingyun.smartmistakebook.core.database.entity.SplitImportQuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomSplitImportStore(
    private val database: StudyDatabase,
) {
    fun observe(): Flow<List<SplitImportJobRecord>> =
        database.splitImportDao().observeActiveJobs().map { jobs ->
            jobs.map(SplitImportJobWithQuestions::toRecord)
        }

    suspend fun create(
        command: CreateSplitImportJobCommand,
        questions: List<SplitImportQuestionSeed>,
    ): SplitImportJobRecord = database.withWriteTransaction {
        require(command.jobId.isNotBlank())
        require(command.sourceFingerprint.matches(SHA_256))
        require(questions.size in 1..SPLIT_JOB_MAX_QUESTIONS) {
            "A split import job must contain 1..$SPLIT_JOB_MAX_QUESTIONS questions"
        }
        val dao = database.splitImportDao()
        dao.readJobByFingerprint(command.sourceFingerprint)?.let { existing ->
            if (existing.jobId != command.jobId) {
                throw ImmutablePayloadConflictException("split_import_source", command.sourceFingerprint)
            }
            return@withWriteTransaction existing.toRecord(dao.readQuestions(existing.jobId))
        }
        val job = SplitImportJobEntity(
            jobId = command.jobId,
            sourceKind = command.sourceKind,
            sourceFingerprint = command.sourceFingerprint,
            sourceUri = command.sourceUri,
            pageCount = command.pageCount,
            questionCount = questions.size,
            status = StudyDbValue.SplitImportStatus.PREPARING,
            createdAtEpochMillis = command.createdAtEpochMillis,
            updatedAtEpochMillis = command.createdAtEpochMillis,
        )
        val rows = questions.mapIndexed { index, question ->
            question.toEntity(command.jobId, index)
        }
        dao.insertJob(job)
        dao.insertQuestions(rows)
        job.toRecord(rows)
    }

    suspend fun read(jobId: String): SplitImportJobRecord? {
        require(jobId.isNotBlank())
        return database.splitImportDao().readJobWithQuestions(jobId)?.toRecord()
    }

    suspend fun readLatestReadyBatchSplitJob(batchJobId: String): SplitImportJobRecord? {
        require(batchJobId.isNotBlank())
        return database.splitImportDao().readLatestReadyBatchSplitJob(batchJobId)
            ?.toRecord(emptyList())
    }

    suspend fun updateSelected(
        jobId: String,
        questionOrdinal: Int,
        selected: Boolean,
        occurredAtEpochMillis: Long,
    ): Boolean = database.withWriteTransaction {
        val dao = database.splitImportDao()
        val job = dao.readJob(jobId) ?: return@withWriteTransaction false
        require(job.status == StudyDbValue.SplitImportStatus.READY) {
            "Split-import selection requires a ready job"
        }
        val changed = dao.updateSelected(jobId, questionOrdinal, selected) == 1
        if (changed && job.updatedAtEpochMillis < occurredAtEpochMillis) {
            dao.touchJob(jobId, occurredAtEpochMillis)
        }
        changed
    }

    suspend fun markQuestionConfirmed(
        jobId: String,
        questionOrdinal: Int,
        confirmState: String,
        splitDraftId: String?,
        occurredAtEpochMillis: Long,
    ): Boolean = database.withWriteTransaction {
        val dao = database.splitImportDao()
        val job = dao.readJob(jobId) ?: return@withWriteTransaction false
        require(job.status == StudyDbValue.SplitImportStatus.READY) {
            "Split-import confirmation requires a ready job"
        }
        val question = dao.readQuestions(jobId).firstOrNull { it.questionOrdinal == questionOrdinal }
            ?: return@withWriteTransaction false
        val resolvedDraftId = when (confirmState) {
            StudyDbValue.SplitImportConfirmState.SAVED,
            StudyDbValue.SplitImportConfirmState.TUTOR_SESSION,
            -> splitDraftId
            else -> null
        }
        val changed = dao.updateConfirmState(jobId, questionOrdinal, confirmState, resolvedDraftId) == 1
        if (changed) dao.touchJob(jobId, occurredAtEpochMillis)
        changed
    }

    /**
     * 切分台账的**提交回执**：按草稿反查被切出来的那道题并置 SAVED（幂等）。
     *
     * 与 [markQuestionConfirmed] 的两点不同，都是这条链的语义要求：
     *
     * - 不要求任务还是 READY。回执到达时任务可能已经被学生先收尾了；压掉一笔真实入库的
     *   回执，留下的是"题已经进错题本、复核页还写着待提交"的假象。
     * - 不要求调用方知道 (jobId, 题号)：只有草稿 id 是提交侧知道的东西。任务级动作仍走上面那条。
     *
     * 草稿反查不中返回 false——不是切分来的草稿，这一笔本来就不该有。
     */
    suspend fun markQuestionSavedByDraftId(
        splitDraftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean = database.withWriteTransaction {
        require(splitDraftId.isNotBlank()) { "Split draft id must not be blank" }
        val dao = database.splitImportDao()
        val question = dao.readQuestionBySplitDraftId(splitDraftId)
            ?: return@withWriteTransaction false
        if (question.confirmState == StudyDbValue.SplitImportConfirmState.SAVED) {
            return@withWriteTransaction true
        }
        val changed = dao.updateConfirmState(
            question.jobId,
            question.questionOrdinal,
            StudyDbValue.SplitImportConfirmState.SAVED,
            question.splitDraftId,
        ) == 1
        if (changed) dao.touchJob(question.jobId, occurredAtEpochMillis)
        changed
    }

    /** 按草稿反查它所属的分题任务（含全部题）；不是切分来的草稿返回 null。 */
    suspend fun readBySplitDraftId(splitDraftId: String): SplitImportJobRecord? {
        require(splitDraftId.isNotBlank()) { "Split draft id must not be blank" }
        val dao = database.splitImportDao()
        val question = dao.readQuestionBySplitDraftId(splitDraftId) ?: return null
        return dao.readJobWithQuestions(question.jobId)?.toRecord()
    }

    suspend fun markReady(
        jobId: String,
        questionCount: Int,
        occurredAtEpochMillis: Long,
    ): Boolean = database.withWriteTransaction {
        database.splitImportDao().markReady(jobId, questionCount, occurredAtEpochMillis) == 1
    }

    suspend fun complete(jobId: String, occurredAtEpochMillis: Long): Boolean =
        database.withWriteTransaction {
            database.splitImportDao().updateJobStatus(
                jobId,
                StudyDbValue.SplitImportStatus.READY,
                StudyDbValue.SplitImportStatus.COMPLETED,
                occurredAtEpochMillis,
            ) == 1 ||
                database.splitImportDao().updateJobStatus(
                    jobId,
                    StudyDbValue.SplitImportStatus.PREPARING,
                    StudyDbValue.SplitImportStatus.COMPLETED,
                    occurredAtEpochMillis,
                ) == 1
        }

    suspend fun abandon(jobId: String, occurredAtEpochMillis: Long): Boolean =
        database.withWriteTransaction {
            database.splitImportDao().updateJobStatus(
                jobId,
                StudyDbValue.SplitImportStatus.READY,
                StudyDbValue.SplitImportStatus.ABANDONED,
                occurredAtEpochMillis,
            ) == 1 ||
                database.splitImportDao().updateJobStatus(
                    jobId,
                    StudyDbValue.SplitImportStatus.PREPARING,
                    StudyDbValue.SplitImportStatus.ABANDONED,
                    occurredAtEpochMillis,
                ) == 1
        }
}

private val SHA_256 = Regex("[a-f0-9]{64}")

internal const val SPLIT_JOB_MAX_QUESTIONS = 120

private fun SplitImportQuestionSeed.toEntity(
    jobId: String,
    ordinal: Int,
) = SplitImportQuestionEntity(
    jobId = jobId,
    questionOrdinal = ordinal,
    pageIndex = pageIndex,
    regionLeft = regionLeft(),
    regionTop = regionTop(),
    regionRight = regionRight(),
    regionBottom = regionBottom(),
    selected = prioritised,
    confirmState = StudyDbValue.SplitImportConfirmState.PENDING,
    splitDraftId = splitDraftId,
)

private fun SplitImportJobWithQuestions.toRecord() =
    job.toRecord(questions.sortedBy(SplitImportQuestionEntity::questionOrdinal))

private fun SplitImportJobEntity.toRecord(questions: List<SplitImportQuestionEntity>) =
    SplitImportJobRecord(
        jobId = jobId,
        sourceKind = sourceKind,
        sourceFingerprint = sourceFingerprint,
        sourceUri = sourceUri,
        pageCount = pageCount,
        questionCount = questionCount,
        status = status,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        questions = questions.map(SplitImportQuestionEntity::toRecord),
    )

private fun SplitImportQuestionEntity.toRecord() = SplitImportQuestionRecord(
    jobId = jobId,
    questionOrdinal = questionOrdinal,
    pageIndex = pageIndex,
    left = regionLeft,
    top = regionTop,
    right = regionRight,
    bottom = regionBottom,
    selected = selected,
    confirmState = confirmState,
    splitDraftId = splitDraftId,
)