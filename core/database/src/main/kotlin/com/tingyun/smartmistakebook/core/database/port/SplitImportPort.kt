package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CreateSplitImportJobCommand
import com.tingyun.smartmistakebook.core.database.SplitImportJobRecord
import com.tingyun.smartmistakebook.core.database.SplitImportQuestionSeed
import kotlinx.coroutines.flow.Flow

/**
 * Split-import ledger: one import action (PDF / batch / single page) cut
 * into individual questions the student confirms before they enter the
 * normal draft workflows.
 */
interface SplitImportPort {
    fun observeActiveSplitImports(): Flow<List<SplitImportJobRecord>>

    suspend fun readSplitImportJob(jobId: String): SplitImportJobRecord?

    /** Newest READY review job the batch job produced, or null when none. */
    suspend fun readLatestReadyBatchSplitJob(batchJobId: String): SplitImportJobRecord?

    suspend fun createSplitImportJob(
        command: CreateSplitImportJobCommand,
        questions: List<SplitImportQuestionSeed>,
    ): SplitImportJobRecord

    suspend fun markSplitImportReady(
        jobId: String,
        questionCount: Int,
        occurredAtEpochMillis: Long,
    ): Boolean

    suspend fun updateSplitImportSelection(
        jobId: String,
        questionOrdinal: Int,
        selected: Boolean,
        occurredAtEpochMillis: Long,
    ): Boolean

    suspend fun markSplitImportQuestionConfirmed(
        jobId: String,
        questionOrdinal: Int,
        confirmState: String,
        splitDraftId: String?,
        occurredAtEpochMillis: Long,
    ): Boolean

    /**
     * 提交回执：按草稿反查被切出来的那道题并置 SAVED（幂等）。提交侧只知道草稿 id，
     * 也知道任务可能已被收尾，所以这条不要求任务处于 READY（详见 RoomSplitImportStore）。
     */
    suspend fun markSplitImportQuestionSavedByDraftId(
        splitDraftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean

    /**
     * 读侧对账（KD-39）：把「草稿已入库（COMMITTED）、台账仍 PENDING」的题补写成 SAVED（幂等），
     * 返回本次补写的笔数。
     *
     * 与 [markSplitImportQuestionSavedByDraftId] 是同一判据的两种到达方式：那一条由提交侧主动
     * 上报，这一条在提交与上报之间被杀进程之后，由台账的读路径（复核页装配、逐题续做、启动）
     * 按库内事实自行追平。语义细节见 RoomSplitImportStore 的实现。
     */
    suspend fun reconcileCommittedQuestionDrafts(occurredAtEpochMillis: Long): Int

    /** 按草稿反查它所属的分题任务（含全部题）；不是切分来的草稿返回 null。 */
    suspend fun readSplitImportJobByDraft(splitDraftId: String): SplitImportJobRecord?

    suspend fun completeSplitImportJob(jobId: String, occurredAtEpochMillis: Long): Boolean

    suspend fun abandonSplitImportJob(jobId: String, occurredAtEpochMillis: Long): Boolean
}