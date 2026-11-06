package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.entity.ErrorCauseBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ErrorCauseObservationEntity

/** 显式账本冲突：同一作用域已有 ACTIVE、observation 不在允许的前置状态、或 scope 不匹配。 */
internal class ErrorCauseLedgerConflictException(message: String) : IllegalStateException(message)

/**
 * 错因两表的**唯一写入口**（批次 40 / WS2 items 11/16）。
 *
 * 三条事务函数各自把"observation 状态迁移 + binding 新建/结束"放进**同一个数据库事务**：
 * 任一步返回 0 行或抛错 → 整个事务回滚，不留下"有 observation 无 binding"或
 * "binding 已 VOID 但无撤回理由行"的半行（R2 §6.5）。
 *
 * 所有 SQL 都是常量 + 绑定参数；没有任何字符串拼接/`format`/f-string。
 * **不提供 DELETE**：撤回是状态，不是删除。
 */
@Dao
internal interface ErrorCauseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(entity: ErrorCauseObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBinding(entity: ErrorCauseBindingEntity): Long

    @Query("SELECT * FROM error_cause_observation WHERE observation_id = :observationId")
    suspend fun findObservation(observationId: String): ErrorCauseObservationEntity?

    @Query("SELECT * FROM error_cause_binding WHERE binding_id = :bindingId")
    suspend fun findBinding(bindingId: String): ErrorCauseBindingEntity?

    /** 现行标签读侧：必须 `state='ACTIVE'` **且**来源 observation 已 ACCEPTED（I6 fail-closed）。 */
    @Query(
        """
        SELECT binding.* FROM error_cause_binding AS binding
        INNER JOIN error_cause_observation AS observation
            ON observation.observation_id = binding.source_observation_id
           AND observation.status = 'ACCEPTED'
        WHERE binding.active_key = :activeKey
          AND binding.state = 'ACTIVE'
        """,
    )
    suspend fun findActiveBindingWithAcceptedSource(activeKey: String): ErrorCauseBindingEntity?

    @Query(
        """
        SELECT COUNT(*) FROM error_cause_binding
        WHERE active_key = :activeKey AND state = 'ACTIVE'
        """,
    )
    suspend fun activeBindingCount(activeKey: String): Int

    @Query("SELECT COUNT(*) FROM error_cause_binding WHERE binding_id = :bindingId")
    suspend fun bindingCount(bindingId: String): Int

    @Query("SELECT COUNT(*) FROM error_cause_observation WHERE observation_id = :observationId")
    suspend fun observationCount(observationId: String): Int

    @Query(
        """
        UPDATE error_cause_observation
        SET status = 'ACCEPTED',
            decided_at_epoch_millis = :decidedAtEpochMillis,
            decision_source = :decisionSource
        WHERE observation_id = :observationId AND status = 'PROPOSED'
        """,
    )
    suspend fun markObservationAccepted(
        observationId: String,
        decidedAtEpochMillis: Long,
        decisionSource: String,
    ): Int

    @Query(
        """
        UPDATE error_cause_binding
        SET state = 'VOIDED',
            active_key = NULL,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE binding_id = :bindingId AND state = 'ACTIVE'
        """,
    )
    suspend fun voidActiveBinding(bindingId: String, endedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE error_cause_binding
        SET state = 'SUPERSEDED',
            active_key = NULL,
            superseded_by_binding_id = :newBindingId,
            ended_at_epoch_millis = :endedAtEpochMillis
        WHERE binding_id = :bindingId AND state = 'ACTIVE'
        """,
    )
    suspend fun supersedeActiveBinding(
        bindingId: String,
        newBindingId: String,
        endedAtEpochMillis: Long,
    ): Int

    /**
     * 自动落账 / 用户确认：把 PROPOSED 的 observation 置 ACCEPTED，并插入 ACTIVE binding。
     *
     * 幂等重放：同一 observation 已 ACCEPTED 且同一 binding 仍 ACTIVE（同 active_key）→
     * 返回 true 且不再写；其余不一致状态返回 false（调用方映射为 CONFLICT）。
     * 同 active_key 已有别的 ACTIVE → false，**不覆盖**（换标签必须走 supersede 事务）。
     */
    @Transaction
    suspend fun acceptObservationAndActivateBinding(
        observationId: String,
        binding: ErrorCauseBindingEntity,
        decidedAtEpochMillis: Long,
        decisionSource: String,
    ): Boolean {
        if (binding.state != ErrorCauseBindingStateWire.ACTIVE || binding.activeKey == null) {
            return false
        }
        val observation = findObservation(observationId) ?: return false
        if (!observation.matchesScopeAndCause(binding)) return false
        if (observation.status == ErrorCauseObservationStatusWire.ACCEPTED) {
            val existing = findBinding(binding.bindingId)
            return existing != null &&
                existing.state == ErrorCauseBindingStateWire.ACTIVE &&
                existing.activeKey == binding.activeKey
        }
        if (observation.status != ErrorCauseObservationStatusWire.PROPOSED) return false
        if (activeBindingCount(binding.activeKey) != 0) return false
        if (markObservationAccepted(observationId, decidedAtEpochMillis, decisionSource) != 1) {
            throw ErrorCauseLedgerConflictException(
                "observation $observationId 未从 PROPOSED 迁到 ACCEPTED",
            )
        }
        insertBinding(binding)
        return true
    }

    /**
     * 撤回：插一条 RETRACTED observation（`supersedes_observation_id` 指向原证据），
     * 并把 ACTIVE binding 置 VOIDED、`active_key=NULL`。
     *
     * 幂等重放：同一撤回 observation 已存在且 binding 已非 ACTIVE → true。
     */
    @Transaction
    suspend fun retractActiveBinding(
        retraction: ErrorCauseObservationEntity,
        bindingId: String,
        endedAtEpochMillis: Long,
    ): Boolean {
        val binding = findBinding(bindingId) ?: return false
        val existing = findObservation(retraction.observationId)
        if (existing != null) {
            return existing.status == ErrorCauseObservationStatusWire.RETRACTED &&
                existing.supersedesObservationId == binding.sourceObservationId &&
                binding.state != ErrorCauseBindingStateWire.ACTIVE
        }
        if (binding.state != ErrorCauseBindingStateWire.ACTIVE) return false
        if (retraction.status != ErrorCauseObservationStatusWire.RETRACTED) return false
        if (!retraction.matchesScopeAndCause(binding)) return false
        if (retraction.supersedesObservationId != binding.sourceObservationId) return false
        if (insertObservation(retraction) < 0) return false
        if (voidActiveBinding(bindingId, endedAtEpochMillis) != 1) {
            throw ErrorCauseLedgerConflictException("binding $bindingId 未从 ACTIVE 迁到 VOIDED")
        }
        return true
    }

    /**
     * 改判：新 observation 置 ACCEPTED，旧 binding 置 SUPERSEDED，新 binding ACTIVE。
     *
     * 新旧 binding 必须属于同一 `active_key`（同一 scope）；旧行先腾出活键，再插新行。
     */
    @Transaction
    suspend fun supersedeActiveBinding(
        newObservationId: String,
        newBinding: ErrorCauseBindingEntity,
        oldBindingId: String,
        decidedAtEpochMillis: Long,
        decisionSource: String,
    ): Boolean {
        val old = findBinding(oldBindingId) ?: return false
        if (old.state != ErrorCauseBindingStateWire.ACTIVE) return false
        if (newBinding.state != ErrorCauseBindingStateWire.ACTIVE || newBinding.activeKey == null) {
            return false
        }
        if (newBinding.activeKey != old.activeKey) return false
        if (newBinding.sourceObservationId != newObservationId) return false
        if (newBinding.bindingId != oldBindingId) {
            val existingBinding = findBinding(newBinding.bindingId)
            if (existingBinding != null) {
                return existingBinding.state == ErrorCauseBindingStateWire.ACTIVE &&
                    existingBinding.sourceObservationId == newObservationId
            }
        }

        val existingNew = findObservation(newObservationId)
        if (existingNew == null) {
            return false
        }
        if (!existingNew.matchesScopeAndCause(newBinding)) return false
        if (existingNew.status != ErrorCauseObservationStatusWire.ACCEPTED &&
            markObservationAccepted(newObservationId, decidedAtEpochMillis, decisionSource) != 1
        ) {
            throw ErrorCauseLedgerConflictException(
                "observation $newObservationId 未从 PROPOSED 迁到 ACCEPTED",
            )
        }
        if (supersedeActiveBinding(oldBindingId, newBinding.bindingId, decidedAtEpochMillis) != 1) {
            throw ErrorCauseLedgerConflictException("binding $oldBindingId 未从 ACTIVE 迁到 SUPERSEDED")
        }
        insertBinding(newBinding)
        return true
    }

    /** 答案键被纠正为"学生其实答对"：唯一允许的系统自动撤回入口（decision_source=SYSTEM）。 */
    @Transaction
    suspend fun voidForAnswerKeyCorrection(
        retraction: ErrorCauseObservationEntity,
        bindingId: String,
        endedAtEpochMillis: Long,
    ): Boolean {
        return retractActiveBinding(retraction, bindingId, endedAtEpochMillis)
    }

    /** 只给审计/设备用例：现行标签条数。 */
    @Query("SELECT COUNT(*) FROM error_cause_binding WHERE state = 'ACTIVE'")
    suspend fun activeBindingTotal(): Int
}

/** observation 与 binding 必须描述同一条标签（scope + cause/detail 都相同）。 */
private fun ErrorCauseObservationEntity.matchesScopeAndCause(
    binding: ErrorCauseBindingEntity,
): Boolean = learnerId == binding.learnerId &&
    practiceUnitId == binding.practiceUnitId &&
    problemRevisionId == binding.problemRevisionId &&
    knowledgeNodeId == binding.knowledgeNodeId &&
    cause == binding.cause &&
    detailCode == binding.detailCode

/** 字符串常量镜像 DB 列值；集中在这里避免 DAO 里散落裸串。 */
internal object ErrorCauseObservationStatusWire {
    const val PROPOSED = "PROPOSED"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val RETRACTED = "RETRACTED"
    const val SUPERSEDED = "SUPERSEDED"
}

internal object ErrorCauseBindingStateWire {
    const val ACTIVE = "ACTIVE"
    const val VOIDED = "VOIDED"
    const val SUPERSEDED = "SUPERSEDED"
}
