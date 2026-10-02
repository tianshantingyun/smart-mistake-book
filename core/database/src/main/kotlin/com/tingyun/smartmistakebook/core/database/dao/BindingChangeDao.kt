package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.DatabaseContractValidator
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.BindingChangeEventEntity
import com.tingyun.smartmistakebook.core.database.entity.LearningSequenceEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionOutboxEntity
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint

/**
 * KF-32（3B 步骤三）改绑补偿事件的写入口：一次离线纠正/自动整理的确认里，**当且仅当**
 * 该题的知识绑定集合真的变化时，追加一条 `BINDING_CHANGED` 账本事件。
 *
 * 形状照 [ChatEvidenceDao.insertAsLedgerEvents]：载荷行与 outbox 行在**同一事务**里落地，
 * 序列从 `learning_sequence` 分配（与 attempt / chat 证据共用同一条账本序列，保证全量重放的
 * "序列 1..N 连续"前提）；事件 id 由调用方用 `commandId` 做内容寻址（确定性，重复确认幂等）。
 *
 * **不追加**的情况由调用方（`RoomProblemOrganizationStore.confirm`）把住：绑定集合未变
 * （幂等重跑 / 同一份整理再确认）不调用本 DAO，验收含"无改绑不触发重放"。
 */
@Dao
internal abstract class BindingChangeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEvent(entry: BindingChangeEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOutboxRow(row: ProjectionOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun initializeSequence(sequence: LearningSequenceEntity): Long

    @Query(
        """
        UPDATE learning_sequence
        SET last_allocated_sequence = :next
        WHERE learner_id = :learnerId AND last_allocated_sequence = :expected
        """,
    )
    protected abstract suspend fun compareAndSetSequence(
        learnerId: String,
        expected: Long,
        next: Long,
    ): Int

    @Query("SELECT last_allocated_sequence FROM learning_sequence WHERE learner_id = :learnerId")
    protected abstract suspend fun lastAllocatedSequence(learnerId: String): Long?

    /** 读取一条改绑事件的载荷行（账本读边界重建事件 + 校验规范指纹用）。 */
    @Query("SELECT * FROM binding_change_event WHERE binding_change_id = :bindingChangeId LIMIT 1")
    abstract suspend fun findBindingChange(bindingChangeId: String): BindingChangeEventEntity?

    /** S5 批量读：`readLedgerChunk` 的每块每表一次 `IN` 查询。 */
    @Query(
        "SELECT * FROM binding_change_event WHERE binding_change_id IN (:bindingChangeIds)",
    )
    abstract suspend fun findBindingChangesByIds(
        bindingChangeIds: List<String>,
    ): List<BindingChangeEventEntity>

    /**
     * 追加一条改绑事件：**先分配序列，再构造事件模型**（序列是账本位置的一部分，指纹含它），
     * 然后在同一事务里落载荷行 + outbox 行。
     *
     * 为什么模型构造与校验在这里而不是调用方：`LearningLedgerEvent` 的序列语义是
     * **1 起**——四个既有写入者（attempt / 修正 / 揭示 / 曝光 / chat）共用同一个分配器
     * （`initializeSequence(learnerId, 0)` 是"还没分配过"的头哨兵，`next = current + 1`，
     * 空账本首值 = 1），六个事件类一律 `require(eventSequence > 0)`，`replay` 也要求账本恰为
     * `1..N` 连续。调用方（`confirm`）手里没有序列，若先造一个占位序列的模型再交给这里，
     * 占位值必然与 `> 0` 冲突——序列的所有权在分配点，模型就该在分配点之后构造。
     */
    @Transaction
    open suspend fun appendAsLedgerEvent(
        learnerId: String,
        bindingChangeId: String,
        practiceUnitId: String,
        previousKnowledgeNodeIds: List<String>,
        newKnowledgeNodeIds: List<String>,
        occurredAtEpochMillis: Long,
    ) {
        val sequence = allocateSequence(learnerId)
        val event = BindingChanged(
            bindingChangeId = bindingChangeId,
            practiceUnitId = practiceUnitId,
            previousKnowledgeNodeIds = previousKnowledgeNodeIds,
            newKnowledgeNodeIds = newKnowledgeNodeIds,
            occurredAtEpochMillis = occurredAtEpochMillis,
            eventSequence = sequence,
        )
        DatabaseContractValidator.validateBindingChangeEvent(event)
        insertEvent(
            BindingChangeEventEntity(
                bindingChangeId = event.bindingChangeId,
                learnerId = learnerId,
                practiceUnitId = event.practiceUnitId,
                previousKnowledgeNodeIds = encodeKnowledgeNodeIds(event.previousKnowledgeNodeIds),
                newKnowledgeNodeIds = encodeKnowledgeNodeIds(event.newKnowledgeNodeIds),
                occurredAtEpochMillis = event.occurredAtEpochMillis,
            ),
        )
        insertOutboxRow(
            ProjectionOutboxEntity(
                outboxId = "learning-outbox:$learnerId:$EVENT_KIND_BINDING_CHANGED:" +
                    event.bindingChangeId,
                learnerId = learnerId,
                outboxSequence = sequence,
                eventKind = EVENT_KIND_BINDING_CHANGED,
                eventId = event.bindingChangeId,
                canonicalFingerprint = LearningLedgerFingerprint.bindingChanged(event),
                status = StudyDbValue.OutboxStatus.PENDING,
                createdAtEpochMillis = occurredAtEpochMillis,
            ),
        )
    }

    private suspend fun allocateSequence(learnerId: String): Long {
        initializeSequence(LearningSequenceEntity(learnerId, 0))
        val current = checkNotNull(lastAllocatedSequence(learnerId))
        check(current < Long.MAX_VALUE) { "Learning sequence exhausted for $learnerId" }
        val next = current + 1
        check(compareAndSetSequence(learnerId, current, next) == 1) {
            "Learning sequence CAS failed inside a serialized Room transaction"
        }
        return next
    }
}

/**
 * 知识点 id 集合的持久化编码：字典序、换行分隔；空集合 = 空串。
 * 与 [BindingChangeEventEntity] 的 KDoc 同源，读侧 [decodeKnowledgeNodeIds] 是唯一逆函数。
 */
internal fun encodeKnowledgeNodeIds(knowledgeNodeIds: Collection<String>): String =
    knowledgeNodeIds.distinct().sorted().joinToString("\n")

internal fun decodeKnowledgeNodeIds(encoded: String): List<String> =
    if (encoded.isEmpty()) emptyList() else encoded.split('\n')

/** `BindingChanged.bindingChangeId` 的命名空间（`confirm` 侧用稳定 id 派生，确定性即幂等键）。 */
internal const val BINDING_CHANGE_ID_NAMESPACE = "binding-change"
