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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
        flow {
            // 复核页装配即对账（KD-39）：列表的第一份快照必须是追平之后的。否则学生进复核页
            // 看到的是一道"题已经进了错题本、这里还写着待提交"的过期行，点「录入所选」还会
            // 重新打开一道已经录过的题（那个按钮按台账里最早的 PENDING 题选草稿）。
            reconcileBeforeRead()
            emitAll(
                database.observeActiveSplitImports().map { jobs ->
                    jobs.map(SplitImportJobSummaryMapper::toSummary)
                },
            )
        }

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

    /**
     * 任务级确认口：把 (jobId, 题号) 直接置成某个 `confirm_state`。
     *
     * **无生产调用方，保留给对账/续做路径**（T1/KD-39 顺带裁定，不删）。今天两条生产写路径
     * 都按证据落账：[recordCommittedDraft] 由提交回执驱动、[reconcileCommittedQuestionDrafts]
     * 由库内草稿状态驱动，两者都只需要草稿 id；而它需要调用方先知道 (jobId, 题号)，正是
     * 复核页最早那版"打开草稿前先预标 SAVED"的接口形状（已被 T4/AUD-03 废除，见
     * `SplitImportReviewRoute` 的 KDoc）。留着的理由是它仍然是**任务级**改状态的唯一口：
     * 将来若出现"按题号如实标注 REJECTED/TUTOR_SESSION"的路径，落账应从它走。
     * 语义与约束（任务必须 READY）见 [com.tingyun.smartmistakebook.core.database.SplitImportPort]
     * / `RoomSplitImportStore.markQuestionConfirmed`。
     */
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
            // 逐题续做前先对账（KD-39）：一份已经入库的题如果没有回执，会被这里的
            // "最早的未提交题"当成还没提交——学生交完第二题之后被送回第一题（一道录过的题）。
            // 追平之后这道题不再进入候选，续做落在真正还没交的那道题上。
            reconcileBeforeRead()
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

    /**
     * 读侧对账（KD-39）的落点：把「草稿已经入库、台账还写着待提交」的题补写成 SAVED，
     * 返回本次补写的笔数。
     *
     * 为什么放在**读**这一侧：提交侧的两步（`confirmAndCommit` 落库 → `recordCommittedDraft`
     * 写回执）之间被杀进程时，题已经在错题本里，只有台账落后；台账的每一条读路径都能用库内的
     * 既有事实（草稿状态）把这一笔追平，不需要提交侧重放、也不需要把回执挤进提交事务。
     *
     * 幂等且与提交侧的回执共用同一判据（草稿 COMMITTED）；对账的写是一个数据库写事务，
     * 与并发的提交回执相撞时两笔写的是同一行同一个值（见 RoomSplitImportStore）。
     *
     * 启动路径（`SmartMistakeBookApplication`）用这个方法：不进复核页、不续做下一题也能追平；
     * 调用方负责记失败，这一层不吞异常。
     */
    suspend fun reconcileCommittedQuestionDrafts(occurredAtEpochMillis: Long): Int =
        withContext(Dispatchers.IO) {
            database.reconcileCommittedQuestionDrafts(occurredAtEpochMillis)
        }

    /**
     * 读路径版本的对账：对账失败**不改变读的结论**，也不把读路径一起断掉（取消照旧上抛）。
     *
     * 失败后果只能是"台账维持原样"——正是补偿之前的状态（复核页照旧、续做照旧），而不是一个
     * 新的假象：让异常穿出去会中止复核页的 Flow 采集，界面回落到"当前没有待确认的拆分结果"，
     * 或者让续做路径崩在半路。真失败仍然可见：题在库里的状态没变，学生看到的就是没追平。
     */
    private suspend fun reconcileBeforeRead() {
        try {
            reconcileCommittedQuestionDrafts(System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 台账维持原样，见上面的 KDoc。
        }
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