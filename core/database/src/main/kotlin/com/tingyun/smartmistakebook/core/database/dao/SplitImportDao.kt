package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.entity.SplitImportJobEntity
import com.tingyun.smartmistakebook.core.database.entity.SplitImportQuestionEntity
import kotlinx.coroutines.flow.Flow

internal data class SplitImportQuestionRow(
    @ColumnInfo(name = "job_id")
    val jobId: String,
    @ColumnInfo(name = "question_ordinal")
    val questionOrdinal: Int,
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    @ColumnInfo(name = "region_left")
    val regionLeft: Double,
    @ColumnInfo(name = "region_top")
    val regionTop: Double,
    @ColumnInfo(name = "region_right")
    val regionRight: Double,
    @ColumnInfo(name = "region_bottom")
    val regionBottom: Double,
    @ColumnInfo(name = "selected")
    val selected: Boolean,
    @ColumnInfo(name = "confirm_state")
    val confirmState: String,
    @ColumnInfo(name = "split_draft_id")
    val splitDraftId: String?,
)

internal data class SplitImportJobWithQuestions(
    @Embedded
    val job: SplitImportJobEntity,
    @Relation(
        parentColumns = ["job_id"],
        entityColumns = ["job_id"],
    )
    val questions: List<SplitImportQuestionEntity>,
)

@Dao
internal interface SplitImportDao {
    @Transaction
    @Query(
        """
        SELECT * FROM split_import_job
        WHERE status IN ('PREPARING', 'READY')
        ORDER BY updated_at_epoch_millis DESC, job_id DESC
        """,
    )
    fun observeActiveJobs(): Flow<List<SplitImportJobWithQuestions>>

    @Transaction
    @Query("SELECT * FROM split_import_job WHERE job_id = :jobId")
    suspend fun readJobWithQuestions(jobId: String): SplitImportJobWithQuestions?

    @Query("SELECT * FROM split_import_job WHERE job_id = :jobId")
    suspend fun readJob(jobId: String): SplitImportJobEntity?

    @Query(
        """
        SELECT * FROM split_import_job
        WHERE source_kind = 'BATCH' AND status = 'READY'
          AND job_id LIKE 'split:' || :batchJobId || ':%'
        ORDER BY updated_at_epoch_millis DESC, job_id DESC
        LIMIT 1
        """,
    )
    suspend fun readLatestReadyBatchSplitJob(batchJobId: String): SplitImportJobEntity?

    @Query("SELECT * FROM split_import_job WHERE source_fingerprint = :sourceFingerprint")
    suspend fun readJobByFingerprint(sourceFingerprint: String): SplitImportJobEntity?

    @Query("SELECT * FROM split_import_question WHERE job_id = :jobId ORDER BY question_ordinal")
    suspend fun readQuestions(jobId: String): List<SplitImportQuestionEntity>

    /**
     * 提交回执的反查口：一份草稿属于哪道切分题。`split_draft_id` 上有索引，这条是按点查写的。
     * 同一份草稿理论上只挂一道题（预建草稿与题一一对应），仍取最小编号以保证结果稳定。
     */
    @Query(
        """
        SELECT * FROM split_import_question
        WHERE split_draft_id = :splitDraftId
        ORDER BY job_id, question_ordinal
        LIMIT 1
        """,
    )
    suspend fun readQuestionBySplitDraftId(splitDraftId: String): SplitImportQuestionEntity?

    /**
     * 读侧对账（KD-39）的取数口：**草稿已经入库**（`problem_draft.status = 'COMMITTED'`）而台账
     * 仍写着 PENDING 的题。
     *
     * 一条 JOIN 取代"逐题读草稿状态"的 N+1：草稿是否入库只在这个两表之间的等值条件里问，
     * 不把整份草稿（修订 + 资产）读出来。两列都有索引（`split_draft_id`、`problem_draft.draft_id`），
     * 且 `split_import_question.split_draft_id` 的外键指向 `problem_draft.draft_id`，JOIN 不会凭空匹配。
     *
     * 不加作业状态过滤：回执本身也不要求任务还是 READY（`RoomSplitImportStore.markQuestionSavedByDraftId`
     * 的 KDoc 说明了为什么——一道已经入库的题不因任务被收尾而改变事实）。代价是这条在历史题行上
     * 也走一遍索引扫描；它只在三个读触发点各跑一次（启动对账 / 进复核页 / 逐题续做），不在热路径上。
     */
    @Query(
        """
        SELECT question.* FROM split_import_question AS question
        JOIN problem_draft AS draft ON draft.draft_id = question.split_draft_id
        WHERE question.confirm_state = 'PENDING' AND draft.status = 'COMMITTED'
        ORDER BY question.job_id, question.question_ordinal
        """,
    )
    suspend fun findPendingQuestionsWithCommittedDraft(): List<SplitImportQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJob(job: SplitImportJobEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestions(questions: List<SplitImportQuestionEntity>)

    @Query(
        """
        UPDATE split_import_job
        SET status = :nextStatus, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE job_id = :jobId AND status = :expectedStatus
        """,
    )
    suspend fun updateJobStatus(
        jobId: String,
        expectedStatus: String,
        nextStatus: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE split_import_question
        SET selected = :selected
        WHERE job_id = :jobId AND question_ordinal = :questionOrdinal
        """,
    )
    suspend fun updateSelected(
        jobId: String,
        questionOrdinal: Int,
        selected: Boolean,
    ): Int

    @Query(
        """
        UPDATE split_import_question
        SET confirm_state = :confirmState, split_draft_id = :splitDraftId
        WHERE job_id = :jobId AND question_ordinal = :questionOrdinal
        """,
    )
    suspend fun updateConfirmState(
        jobId: String,
        questionOrdinal: Int,
        confirmState: String,
        splitDraftId: String?,
    ): Int

    @Query(
        """
        UPDATE split_import_job
        SET question_count = :questionCount, status = 'READY',
            updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE job_id = :jobId AND status = 'PREPARING'
        """,
    )
    suspend fun markReady(
        jobId: String,
        questionCount: Int,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE split_import_job
        SET updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE job_id = :jobId
        """,
    )
    suspend fun touchJob(jobId: String, updatedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM split_import_question
        WHERE job_id = :jobId AND selected = 1 AND confirm_state = 'PENDING'
        """,
    )
    suspend fun countSelectedPending(jobId: String): Int
}