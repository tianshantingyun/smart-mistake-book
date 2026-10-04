package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_STATUS_PENDING
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_STATUS_REVIEWED
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_STATUSES
import com.tingyun.smartmistakebook.core.database.BINDING_AUDIT_VERDICTS
import com.tingyun.smartmistakebook.core.database.BindingAuditSampleRow
import com.tingyun.smartmistakebook.core.database.RecordBindingAuditSampleCommand
import com.tingyun.smartmistakebook.core.database.entity.BindingAuditSampleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 绑定抽样队列（KF-29）的读写口。三条不变量在这里、不在调用方：
 *
 * 1. **每科每周首 N 条**：`record` 在**同一个写事务**里先数配额再插入（`@Transaction`），
 *    写事务串行化 ⇒ 并发确认也恰好封顶 N，不会"先读后写"超发。
 * 2. **同一次绑定只有一条样本**：`sample_id` 内容寻址 + `INSERT IGNORE` ⇒ 确认重放
 *    不会产生第二条样本，也不会重复占用配额。
 * 3. **判定只落一次**：`review` 走 `WHERE status = 'PENDING'` 的比较交换；已 REVIEWED 的行
 *    不被覆盖（重放/误触不产生互相矛盾的两次判定），复核屏重新读取时拿到的是第一条判定。
 *
 * 默认查询按 `sample_id` 升序：前缀含周键，天然按"科 → 周 → 摘要"稳定排序。
 */
@Dao
internal abstract class BindingAuditSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSample(entity: BindingAuditSampleEntity): Long

    @Query("SELECT * FROM binding_audit_sample WHERE sample_id = :sampleId LIMIT 1")
    protected abstract suspend fun findSample(sampleId: String): BindingAuditSampleEntity?

    @Query("SELECT COUNT(*) FROM binding_audit_sample WHERE sample_id LIKE :quotaPrefix || ':%'")
    protected abstract suspend fun countSamplesForQuota(quotaPrefix: String): Int

    @Query(
        """
        SELECT * FROM binding_audit_sample
        WHERE (:status IS NULL OR status = :status)
        ORDER BY sample_id
        """,
    )
    protected abstract fun observeSamples(status: String?): Flow<List<BindingAuditSampleEntity>>

    @Query(
        """
        SELECT * FROM binding_audit_sample
        WHERE (:status IS NULL OR status = :status)
        ORDER BY sample_id
        """,
    )
    protected abstract suspend fun readSamples(status: String?): List<BindingAuditSampleEntity>

    @Query(
        """
        UPDATE binding_audit_sample
        SET status = :reviewedStatus,
            verdict = :verdict,
            reviewed_at = :reviewedAtEpochMillis
        WHERE sample_id = :sampleId
          AND status = :pendingStatus
        """,
    )
    protected abstract suspend fun markReviewed(
        sampleId: String,
        pendingStatus: String,
        reviewedStatus: String,
        verdict: String,
        reviewedAtEpochMillis: Long,
    ): Int

    /**
     * 入队一条抽样；配额已满（该科该周已有 [maxPerSubjectWeek] 条）时返回 false 且不插入。
     * 返回 true 只代表"这一条由本次插入落库"（重复 id 被忽略时返回 false——已有样本在场）。
     */
    @Transaction
    open suspend fun record(
        command: RecordBindingAuditSampleCommand,
        maxPerSubjectWeek: Int,
    ): Boolean {
        require(command.sampleId.startsWith("${command.quotaPrefix}:")) {
            "A binding audit sample id must be prefixed by its quota"
        }
        require(command.practiceUnitId.isNotBlank()) { "A binding audit sample needs a practice unit" }
        require(command.bindingSnapshotJson.isNotBlank()) {
            "A binding audit sample needs its binding snapshot"
        }
        require(maxPerSubjectWeek >= 1) { "A binding audit quota must keep at least one sample" }
        if (countSamplesForQuota(command.quotaPrefix) >= maxPerSubjectWeek) return false
        return insertSample(command.toEntity()) != -1L
    }

    /** 落判（PENDING→REVIEWED）；返回操作后该行的权威快照（已判定时是第一条判定）。 */
    @Transaction
    open suspend fun review(
        sampleId: String,
        verdict: String,
        reviewedAtEpochMillis: Long,
    ): BindingAuditSampleRow? {
        require(sampleId.isNotBlank()) { "A binding audit sample id must not be blank" }
        require(verdict in BINDING_AUDIT_VERDICTS) { "Unknown binding audit verdict" }
        require(reviewedAtEpochMillis > 0) { "A binding audit review time must be positive" }
        markReviewed(
            sampleId = sampleId,
            pendingStatus = BINDING_AUDIT_STATUS_PENDING,
            reviewedStatus = BINDING_AUDIT_STATUS_REVIEWED,
            verdict = verdict,
            reviewedAtEpochMillis = reviewedAtEpochMillis,
        )
        return findSample(sampleId)?.toRow()
    }

    open fun observe(status: String?): Flow<List<BindingAuditSampleRow>> {
        require(status == null || status in BINDING_AUDIT_STATUSES) {
            "Unknown binding audit status filter"
        }
        return observeSamples(status).map { rows -> rows.map(BindingAuditSampleEntity::toRow) }
    }

    open suspend fun read(status: String?): List<BindingAuditSampleRow> {
        require(status == null || status in BINDING_AUDIT_STATUSES) {
            "Unknown binding audit status filter"
        }
        return readSamples(status).map(BindingAuditSampleEntity::toRow)
    }
}

internal fun RecordBindingAuditSampleCommand.toEntity() = BindingAuditSampleEntity(
    sampleId = sampleId,
    practiceUnitId = practiceUnitId,
    bindingSnapshotJson = bindingSnapshotJson,
    status = BINDING_AUDIT_STATUS_PENDING,
)

internal fun BindingAuditSampleEntity.toRow() = BindingAuditSampleRow(
    sampleId = sampleId,
    practiceUnitId = practiceUnitId,
    bindingSnapshotJson = bindingSnapshotJson,
    status = status,
    verdict = verdict,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
)
