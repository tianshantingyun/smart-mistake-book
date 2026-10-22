package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.ClaimCleanRedrawTaskAttemptCommand
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.FinishCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.entity.CleanRedrawTaskEntity
import com.tingyun.smartmistakebook.core.database.port.RevisionImageSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 净化作业台账（AUD-09）的读写口。三条不变量在这里、不在调用方：
 *
 * 1. **一个身份一行**：`task_id` 是主键，`INSERT IGNORE`——同一身份重复触发是重放，返回已
 *    存在的那一行（它的状态与尝试次数才是权威，绝不被重放覆盖）。
 * 2. **领取是比较交换**：`attempt_count` 只有"仍是读到的那一版、状态可试、未达上限"才 +1。
 *    领取失败 = 别人领走了或次数耗尽——两种都意味着这次**不要再跑**（费用上界）。
 * 3. **终态按领取版本写**：落终态比对 `attempt_count`（领到的那一版）。旧尝试的迟到结果写不进
 *    新一轮的判定里。
 *
 * 与 `MistakeExportRecordDao` 的分工：那张表的"运行中"是显式状态（`RUNNING`），这里的
 * `PENDING` 同时覆盖"待做"与"在途"——区分二者的是 `attempt_count` + `updated_at`：
 * 首次领取（`attempt_count == 0`）总可领；已领取的在途行只有超过租约截止（启动对账传
 * "本进程启动时刻"）才允许再领，防止两个并发请求各领一次、同一作业重复出网付费。
 */
@Dao
internal abstract class CleanRedrawTaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTask(entity: CleanRedrawTaskEntity): Long

    @Query("SELECT * FROM clean_redraw_task WHERE task_id = :taskId LIMIT 1")
    protected abstract suspend fun findTask(taskId: String): CleanRedrawTaskEntity?

    @Query(
        """
        SELECT * FROM clean_redraw_task
        WHERE request_fingerprint = :requestFingerprint
        ORDER BY created_at_epoch_millis ASC, task_id ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun findTaskByFingerprint(
        requestFingerprint: String,
    ): CleanRedrawTaskEntity?

    @Query(
        """
        SELECT * FROM clean_redraw_task
        WHERE status IN (:statuses)
          AND attempt_count < :maxAttempts
          AND updated_at_epoch_millis <= :staleBeforeEpochMillis
        ORDER BY created_at_epoch_millis ASC, task_id ASC
        """,
    )
    protected abstract suspend fun findResumableTasks(
        statuses: List<String>,
        maxAttempts: Int,
        staleBeforeEpochMillis: Long,
    ): List<CleanRedrawTaskEntity>

    /**
     * 某条修订上最近一次作业。[likePattern] 是 SQL LIKE 模式（如 `explicit-redraw-op:%`）；
     * null = 不按操作类型筛。显式重绘详情区只看自己那一类行，不把保存期净化当成用户请求。
     */
    @Query(
        """
        SELECT * FROM clean_redraw_task
        WHERE revision_id = :revisionId
          AND (:likePattern IS NULL OR operation_id LIKE :likePattern)
        ORDER BY created_at_epoch_millis DESC, task_id DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun findLatestTask(
        revisionId: String,
        likePattern: String?,
    ): CleanRedrawTaskEntity?

    @Query(
        """
        SELECT * FROM clean_redraw_task
        WHERE revision_id = :revisionId
          AND (:likePattern IS NULL OR operation_id LIKE :likePattern)
        ORDER BY created_at_epoch_millis DESC, task_id DESC
        LIMIT 1
        """,
    )
    protected abstract fun observeLatestTask(
        revisionId: String,
        likePattern: String?,
    ): Flow<CleanRedrawTaskEntity?>

    /**
     * 修订上已登记的视觉引用（角色 + 资产 id + 内容 sha）。显式重绘只据此选源图；
     * 文件字节仍由 vault 按规范记录读取并在发送前核对 sha。
     */
    @Query(
        """
        SELECT link.role AS role,
               asset.source_asset_id AS sourceAssetId,
               asset.content_sha256 AS contentSha256
        FROM problem_revision_source_asset AS link
        JOIN canonical_source_asset AS asset
          ON asset.source_asset_id = link.source_asset_id
        WHERE link.problem_revision_id = :problemRevisionId
        ORDER BY link.role ASC, asset.source_asset_id ASC
        """,
    )
    protected abstract suspend fun findRevisionImageSources(
        problemRevisionId: String,
    ): List<RevisionImageSourceRow>

    /**
     * 领取一次尝试的比较交换。**同时清空 `last_failure_code`**：那一列记的是"最近一次**已结束**
     * 尝试失败在哪一环"，领取之后这一行又回到"在途"（`PENDING`），上一轮的失败码对该状态不成立
     * ——[CleanRedrawTaskRecord] 的契约是有码 ⟺ `FAILED`，不清就会在续跑一条 FAILED 行时直接抛
     * `IllegalArgumentException`（首轮真实缺陷：失败行的续跑必断）。
     *
     * 代价如实记：一次被中断的重试会把"上次为什么失败"从行上抹掉（日志与下一次失败仍会补回来），
     * 换来的是"在途"的状态语义只有一种读法。
     */
    @Query(
        """
        UPDATE clean_redraw_task
        SET status = :pendingStatus,
            attempt_count = :nextAttemptCount,
            last_failure_code = NULL,
            updated_at_epoch_millis = :claimedAtEpochMillis
        WHERE task_id = :taskId
          AND attempt_count = :expectedAttemptCount
          AND attempt_count < :maxAttempts
          AND (
              status = :failedStatus
              OR (status = :pendingStatus AND attempt_count = 0)
              OR (status = :pendingStatus AND updated_at_epoch_millis <= :staleBeforeEpochMillis)
          )
        """,
    )
    protected abstract suspend fun claimAttempt(
        taskId: String,
        expectedAttemptCount: Int,
        nextAttemptCount: Int,
        pendingStatus: String,
        failedStatus: String,
        maxAttempts: Int,
        staleBeforeEpochMillis: Long,
        claimedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE clean_redraw_task
        SET status = :status,
            last_failure_code = :lastFailureCode,
            updated_at_epoch_millis = :finishedAtEpochMillis
        WHERE task_id = :taskId
          AND attempt_count = :expectedAttemptCount
        """,
    )
    protected abstract suspend fun finishAttempt(
        taskId: String,
        expectedAttemptCount: Int,
        status: String,
        lastFailureCode: String?,
        finishedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun createTask(command: CreateCleanRedrawTaskCommand): CleanRedrawTaskRecord {
        val candidate = CleanRedrawTaskEntity(
            taskId = command.taskId,
            revisionId = command.revisionId,
            sourceAssetId = command.sourceAssetId,
            sourceImageSha256 = command.sourceImageSha256,
            requestFingerprint = command.requestFingerprint,
            subjectId = command.subjectId,
            status = CleanRedrawTaskStatus.PENDING,
            attemptCount = 0,
            lastFailureCode = null,
            createdAtEpochMillis = command.createdAtEpochMillis,
            updatedAtEpochMillis = command.createdAtEpochMillis,
            operationId = command.operationId,
        )
        if (insertTask(candidate) != -1L) return candidate.toRecord()
        // 已有行 = 重放：身份必须逐位一致，否则是调用方拿另一个身份撞了同一个 taskId。
        val persisted = checkNotNull(findTask(command.taskId)) {
            "Clean redraw task insert was not readable"
        }
        require(persisted.hasSameIdentity(candidate)) {
            "Clean redraw task id ${command.taskId} already describes another identity"
        }
        return persisted.toRecord()
    }

    open suspend fun readTask(taskId: String): CleanRedrawTaskRecord? {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        return findTask(taskId)?.toRecord()
    }

    open suspend fun readTaskByFingerprint(requestFingerprint: String): CleanRedrawTaskRecord? {
        require(requestFingerprint.length == 64) {
            "A clean redraw request fingerprint must be sha256"
        }
        return findTaskByFingerprint(requestFingerprint)?.toRecord()
    }

    open suspend fun readResumableTasks(
        maxAttempts: Int,
        staleBeforeEpochMillis: Long,
    ): List<CleanRedrawTaskRecord> {
        require(maxAttempts >= 1) { "A clean redraw attempt limit must be positive" }
        require(staleBeforeEpochMillis >= 0) {
            "A clean redraw resume cutoff must not be negative"
        }
        return findResumableTasks(
            statuses = CleanRedrawTaskStatus.RESUMABLE,
            maxAttempts = maxAttempts,
            staleBeforeEpochMillis = staleBeforeEpochMillis,
        ).map(CleanRedrawTaskEntity::toRecord)
    }

    /** 某条修订上最近一次作业（含终态）；[operationIdPrefix] null = 不筛操作类型。 */
    open suspend fun readLatestTaskForRevision(
        revisionId: String,
        operationIdPrefix: String? = null,
    ): CleanRedrawTaskRecord? {
        require(revisionId.isNotBlank()) { "A clean redraw revision id must not be blank" }
        require(operationIdPrefix == null || operationIdPrefix.isNotBlank()) {
            "A clean redraw operation id prefix must be null or non-blank"
        }
        return findLatestTask(revisionId, operationIdPrefix?.let { "$it%" })?.toRecord()
    }

    /** 与 [readLatestTaskForRevision] 同口径的观察流。 */
    open fun observeLatestTaskForRevision(
        revisionId: String,
        operationIdPrefix: String? = null,
    ): Flow<CleanRedrawTaskRecord?> {
        require(revisionId.isNotBlank()) { "A clean redraw revision id must not be blank" }
        require(operationIdPrefix == null || operationIdPrefix.isNotBlank()) {
            "A clean redraw operation id prefix must be null or non-blank"
        }
        return observeLatestTask(revisionId, operationIdPrefix?.let { "$it%" })
            .map { entity -> entity?.toRecord() }
    }

    open suspend fun readRevisionImageSources(
        problemRevisionId: String,
    ): List<RevisionImageSource> {
        require(problemRevisionId.isNotBlank()) {
            "A problem revision id must not be blank"
        }
        return findRevisionImageSources(problemRevisionId).map { row ->
            RevisionImageSource(
                role = row.role,
                sourceAssetId = row.sourceAssetId,
                contentSha256 = row.contentSha256,
            )
        }
    }

    /**
     * 领取一次尝试；返回领取后的行，领不到（别人领走 / 次数耗尽 / 已是终态）返回 null。
     */
    @Transaction
    open suspend fun claimTaskAttempt(
        command: ClaimCleanRedrawTaskAttemptCommand,
    ): CleanRedrawTaskRecord? {
        val claimed = claimAttempt(
            taskId = command.taskId,
            expectedAttemptCount = command.expectedAttemptCount,
            nextAttemptCount = command.expectedAttemptCount + 1,
            pendingStatus = CleanRedrawTaskStatus.PENDING,
            failedStatus = CleanRedrawTaskStatus.FAILED,
            maxAttempts = command.maxAttempts,
            staleBeforeEpochMillis = command.staleBeforeEpochMillis,
            claimedAtEpochMillis = command.occurredAtEpochMillis,
        )
        // 比较交换以 UPDATE 的命中行数为准：命中 0 行 = 别人先领走 / 次数耗尽 / 已是终态。
        // 不能靠回读到的 `attemptCount == expected+1` 判——并发下赢家刚把它加到 expected+1，
        // 输家回读到的也是 expected+1，会把「没领到」误报成「领到了」（重复付费 + 双写终态）。
        if (claimed == 0) return null
        return findTask(command.taskId)?.toRecord()
    }

    /**
     * 落一次尝试的终态；返回写成功后的行。写不进（这一版的尝试已被别人接手/终结）返回 null。
     */
    @Transaction
    open suspend fun finishTaskAttempt(
        command: FinishCleanRedrawTaskCommand,
    ): CleanRedrawTaskRecord? {
        finishAttempt(
            taskId = command.taskId,
            expectedAttemptCount = command.expectedAttemptCount,
            status = command.status,
            lastFailureCode = command.lastFailureCode,
            finishedAtEpochMillis = command.occurredAtEpochMillis,
        )
        return findTask(command.taskId)
            ?.takeIf { it.status == command.status && it.attemptCount == command.expectedAttemptCount }
            ?.toRecord()
    }
}

/** 修订视觉引用查询的行形状（列别名与 [RevisionImageSource] 的字段同名）。 */
internal data class RevisionImageSourceRow(
    val role: String,
    val sourceAssetId: String,
    val contentSha256: String,
)

internal fun CleanRedrawTaskEntity.toRecord() = CleanRedrawTaskRecord(
    taskId = taskId,
    revisionId = revisionId,
    sourceAssetId = sourceAssetId,
    sourceImageSha256 = sourceImageSha256,
    requestFingerprint = requestFingerprint,
    subjectId = subjectId,
    status = status,
    attemptCount = attemptCount,
    lastFailureCode = lastFailureCode,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    operationId = operationId,
)

private fun CleanRedrawTaskEntity.hasSameIdentity(other: CleanRedrawTaskEntity): Boolean =
    taskId == other.taskId &&
        revisionId == other.revisionId &&
        sourceAssetId == other.sourceAssetId &&
        sourceImageSha256 == other.sourceImageSha256 &&
        requestFingerprint == other.requestFingerprint &&
        subjectId == other.subjectId &&
        operationId == other.operationId
