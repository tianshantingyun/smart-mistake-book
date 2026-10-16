package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.database.entity.LearningSequenceEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionOutboxEntity
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import kotlinx.coroutines.flow.Flow

/**
 * Chat evidence is a first-class ledger event (spec model-intent-routing §5):
 * every write allocates a learning sequence and appends a projection_outbox
 * row in the same transaction, so both the incremental batch path and the
 * full-replay path see the event. The evidence row stays the authoritative
 * payload; the outbox row only carries identity/sequence/fingerprint.
 *
 * 幂等的粒度是**身份**（evidence_id）：同身份重放 = 一笔（不推进序列、不写第二条 outbox），
 * 不同身份 = 各自一笔（各自的序列与 outbox）。AUD-01：改前 accepted 的 outbox 行用 ABORT，
 * 于是"同一会话同一题面同一选项的第二笔"会撞 `event_kind+event_id` 唯一键——整个事务回滚，
 * 证据行也没落，学生看到的是"回写失败"。同身份重放不是故障，是重试，必须在读口之前就被
 * 按身份吃掉。
 *
 * **这条"同身份 ⇒ 同语义"的前提有一个已知反例**（AUD-11 ③，登记 KD-43）：先前被**拒**的写
 * 也占住同一个 evidence_id（观察行），冷却/门条件变化后同一身份的合法写会被 IGNORE 静默吞掉。
 * 本层不替它做裁决（纠正语义留 5B），但不再让它静默：[insertAsLedgerEvents] 回报回执，
 * 把这一档单独列出来（[ChatEvidenceWriteReceipt.acceptedBlockedByRejectedRowEvidenceIds]）。
 */
@Dao
internal abstract class ChatEvidenceDao {    /**
     * IGNORE (not ABORT): the evidence_id is a deterministic idempotency key —
     * a retried write of the same evidence (same request + tool + KC) must
     * silently no-op instead of failing the whole transaction on a PK clash.
     * Id derivation guarantees same id ⇒ same semantics.
     *
     * 逐条返回插入结果：`-1` = 该身份已在库（重放），否则是新行的 rowId。
     * [insertAsLedgerEvents] 只对**新行**分配学习序列并写 outbox——重放因此不推进序列
     * （账本不留空洞）、也不产生第二条同 event_id 的 outbox 行。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAll(entries: List<LearnerChatEvidenceEntity>): List<Long>

    /** 按身份回读既有行：IGNORE 命中时用它分清"正常重放"与"身份粘性"（见 [ChatEvidenceWriteReceipt]）。 */
    @Query("SELECT * FROM learner_chat_evidence WHERE evidence_id IN (:evidenceIds)")
    protected abstract suspend fun readByEvidenceIds(evidenceIds: List<String>): List<LearnerChatEvidenceEntity>

    /**
     * outbox 行保持 ABORT：它不再承担"重放去重"（那已由证据行的身份去重在上游完成），
     * 留着 LOUD 是把它当**完整性哨兵**——一条新证据行必然带一个全新 event_id，正常路径
     * 不可能撞键；真撞上说明库里有一条不来自本事务的 outbox 行，那是必须响的账本故障，
     * 不能静默 IGNORE 掉。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOutboxRows(rows: List<ProjectionOutboxEntity>)

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

    /**
     * 落库并**回报**：返回 [ChatEvidenceWriteReceipt]，把 Room 的 IGNORE 从"静默"变成可读的结果。
     *
     * AUD-11 ③-2（可观察性先行）：写入口此前对"accepted 行被 IGNORE 吃掉"无感知——调用方
     * （UI/工具环）看到的是"已记录"，而库里那一行可能是**被拒观察行**、账本永远不会有事件
     * （身份粘性静默丢写）。本事务顺手把 IGNORE 命中的身份回读出来分成两档：
     * 正常重放（既有行也是 accepted）与身份粘性（既有行是被拒行）。语义一字不放松：
     * 重放仍不推进序列、不写第二条 outbox、既有行仍不被改写。
     */
    @Transaction
    open suspend fun insertAsLedgerEvents(
        entries: List<LearnerChatEvidenceEntity>,
    ): ChatEvidenceWriteReceipt {
        if (entries.isEmpty()) return ChatEvidenceWriteReceipt.EMPTY
        // Rejected rows are observation-only (research tutor-evidence-gate
        // §3.3): they stay as an audit trail but never enter the ledger —
        // no learning-sequence allocation, no projection_outbox row, so the
        // projector can never see them.
        val (accepted, rejected) = entries.partition { !it.isRejected }
        if (rejected.isNotEmpty()) {
            insertAll(rejected)
        }
        if (accepted.isEmpty()) return ChatEvidenceWriteReceipt.EMPTY
        // 先落 accepted 证据行，再按插入结果分出**新行**（-1 = 该身份已在库，是重放）：
        // 重放到此为止——不分配学习序列、不写 outbox 行，库里仍旧只有那一行。
        val rowIds = insertAll(accepted)
        val freshAccepted = accepted.filterIndexed { index, _ -> rowIds[index] != -1L }
        val ignoredAccepted = accepted.filterIndexed { index, _ -> rowIds[index] == -1L }
        if (freshAccepted.isNotEmpty()) {
            val outboxRows = freshAccepted.map { entry ->
                val sequence = allocateSequence(entry.learner_id)
                val event = entry.toChatEvidenceModel(sequence)
                ProjectionOutboxEntity(
                    outboxId = "learning-outbox:${entry.learner_id}:$EVENT_KIND_CHAT_EVIDENCE:${entry.evidence_id}",
                    learnerId = entry.learner_id,
                    outboxSequence = sequence,
                    eventKind = EVENT_KIND_CHAT_EVIDENCE,
                    eventId = entry.evidence_id,
                    canonicalFingerprint = LearningLedgerFingerprint.chatEvidence(event),
                    status = StudyDbValue.OutboxStatus.PENDING,
                    createdAtEpochMillis = entry.created_at_epoch_millis,
                )
            }
            insertOutboxRows(outboxRows)
        }
        if (ignoredAccepted.isEmpty()) {
            return ChatEvidenceWriteReceipt(insertedAcceptedEvidenceIds = freshAccepted.map { it.evidence_id })
        }
        // IGNORE 命中的身份：既有行是被拒观察行 ⇒ 这笔 accepted 进不了账本（身份粘性）。
        // 正常重放（既有行也是 accepted）与它必须分开报——前者是重试，后者是丢写。
        val blockedIds = readByEvidenceIds(ignoredAccepted.map { it.evidence_id }.distinct())
            .asSequence()
            .filter { it.isRejected }
            .map { it.evidence_id }
            .toSet()
        return ChatEvidenceWriteReceipt(
            insertedAcceptedEvidenceIds = freshAccepted.map { it.evidence_id },
            replayedAcceptedEvidenceIds = ignoredAccepted
                .map { it.evidence_id }
                .filterNot(blockedIds::contains),
            acceptedBlockedByRejectedRowEvidenceIds = ignoredAccepted
                .map { it.evidence_id }
                .filter(blockedIds::contains),
        )
    }

    @Query("SELECT * FROM learner_chat_evidence WHERE conversation_id = :conversationId ORDER BY created_at_epoch_millis")
    abstract suspend fun readByConversation(conversationId: String): List<LearnerChatEvidenceEntity>

    @Query("SELECT * FROM learner_chat_evidence WHERE learner_id = :learnerId ORDER BY created_at_epoch_millis DESC LIMIT :limit")
    abstract fun observeRecent(learnerId: String, limit: Int): Flow<List<LearnerChatEvidenceEntity>>

    @Query("SELECT * FROM learner_chat_evidence WHERE learner_id = :learnerId ORDER BY created_at_epoch_millis")
    abstract suspend fun readByLearner(learnerId: String): List<LearnerChatEvidenceEntity>

    /**
     * Gate queries (batch-scale design: indexed, O(log n) instead of a full
     * table pull). All three only count accepted evidence — rejected rows are
     * observation-only and never gate.
     */

    /**
     * Most recent accepted write **of one channel** to one KC by the learner.
     *
     * AUD-11 ①（已裁定）：冷却原来是通道无关的——查询只过滤 rejected，于是知识点小测
     * （KNOWLEDGE_QUIZ，本地机械判定、置信 1.0）的 accepted 行会被当成"讲题通道下一笔的
     * 冷却起点"，反之亦然：一晚讲题后当晚的小测客观证据落库 0/3（实测见报告 §2.2）。
     * 文档口径本来就是"before the same KC accepts another **model** evidence write"
     *（[com.tingyun.smartmistakebook.core.domain.MasteryWriteGate.SAME_KC_COOLDOWN_MILLIS]），
     * 本查询把这条口径落到 SQL：冷却起点只认**同通道**的 accepted 行，通道由
     * [sourceKind] 给（值取 `StudyDbValue.ChatEvidenceSourceKind`，与写入侧同源）。
     */
    @Query(
        """
        SELECT MAX(created_at_epoch_millis) FROM learner_chat_evidence
        WHERE learner_id = :learnerId AND knowledge_node_id = :knowledgeNodeId
          AND source_kind = :sourceKind
          AND rejected_reason IS NULL
        """,
    )
    abstract suspend fun lastAcceptedAtForKc(
        learnerId: String,
        knowledgeNodeId: String,
        sourceKind: String,
    ): Long?

    /** Accepted writes by the learner since [sinceEpochMillis] (learner rolling-window quota). */
    @Query(
        """
        SELECT COUNT(*) FROM learner_chat_evidence
        WHERE learner_id = :learnerId AND rejected_reason IS NULL
          AND created_at_epoch_millis >= :sinceEpochMillis
        """,
    )
    abstract suspend fun countAcceptedSince(learnerId: String, sinceEpochMillis: Long): Int

    /** Accepted writes in one conversation (conversation quota). */
    @Query(
        """
        SELECT COUNT(*) FROM learner_chat_evidence
        WHERE conversation_id = :conversationId AND rejected_reason IS NULL
        """,
    )
    abstract suspend fun countAcceptedInConversation(conversationId: String): Int

    /**
     * Rejected-write counts grouped by gate reason (calibration input).
     *
     * AUD-11 ①：分桶带 `source_kind` 维（校准要能回答"被挡掉的是哪一路"——报告 §2.3.3：
     * 收窄冷却前后都得能分辨模型写与小测写）。行数 = 通道 × 拒因；总数由调用方求和，
     * 口径不变。
     */
    @Query(
        """
        SELECT source_kind AS sourceKind, rejected_reason AS reason, COUNT(*) AS count
        FROM learner_chat_evidence
        WHERE learner_id = :learnerId AND rejected_reason IS NOT NULL
        GROUP BY source_kind, rejected_reason
        """,
    )
    abstract suspend fun countRejectedByReason(learnerId: String): List<RejectedReasonCountRow>

    /**
     * Accepted-write counts bucketed by epoch hour (calibration: window pressure),
     * **per channel** (AUD-11 ①): 同一张表上的两个问题——"总压力是否顶到滚动窗上限"
     * （通道无关，配额本来就是 learner 级）与"哪条通道在灌量"（冷却收窄后的洪泛面读数）。
     */
    @Query(
        """
        SELECT source_kind AS sourceKind, created_at_epoch_millis / 3600000 AS hourBucket,
               COUNT(*) AS count
        FROM learner_chat_evidence
        WHERE learner_id = :learnerId AND rejected_reason IS NULL
          AND created_at_epoch_millis >= :sinceEpochMillis
        GROUP BY source_kind, hourBucket
        ORDER BY hourBucket DESC
        """,
    )
    abstract suspend fun countAcceptedPerHour(
        learnerId: String,
        sinceEpochMillis: Long,
    ): List<HourlyAcceptedCountRow>

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

internal fun LearnerChatEvidenceEntity.toChatEvidenceModel(eventSequence: Long): ChatEvidenceSubmitted =
    ChatEvidenceSubmitted(
        evidenceId = evidence_id,
        conversationId = conversation_id,
        knowledgeNodeId = knowledge_node_id,
        direction = LearningEvidenceDirection.valueOf(direction),
        weight = weight,
        reasonMarkdown = reason_markdown,
        confidence = confidence,
        occurredAtEpochMillis = created_at_epoch_millis,
        eventSequence = eventSequence,
    )


/**
 * 一次证据落库的**回执**（AUD-11 ③-2）：三条互斥的分支，把 Room 的 IGNORE 从静默变成可读。
 *
 * 消灭的失败：写入口回报 `Accepted` 而账本里没有任何事件——调用方（UI/工具环）以为"已记录"，
 * 实际那笔 accepted 写被同身份的在库行吃掉（身份粘性，报告 §3.3.2 附带测到的静默丢失）。
 * 三个列表各自对应一种**不同的现实**，不能合并成一堆 id：
 *
 * - [insertedAcceptedEvidenceIds]：新行落成（已分配学习序列、已写 outbox）；
 * - [replayedAcceptedEvidenceIds]：同身份重放（既有行也是 accepted）——这是**重试**，
 *   AUD-01 的既有语义，不推进序列、不写第二条 outbox；
 * - [acceptedBlockedByRejectedRowEvidenceIds]：同身份但既有行是**被拒观察行**——这笔 accepted
 *   永远进不了账本，且既有行按 IGNORE 语义不被改写（KD-43 的静默丢写面）。
 *
 * 三列之和 = 本次请求里 accepted 的笔数（重放与粘性都算"没新落行"）。
 */
data class ChatEvidenceWriteReceipt(
    val insertedAcceptedEvidenceIds: List<String> = emptyList(),
    val replayedAcceptedEvidenceIds: List<String> = emptyList(),
    val acceptedBlockedByRejectedRowEvidenceIds: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = ChatEvidenceWriteReceipt()
    }
}

/** Projection row for rejected-write counts grouped by channel and gate reason (calibration input). */
data class RejectedReasonCountRow(
    /** `learner_chat_evidence.source_kind`（通道维，见 [StudyDbValue.ChatEvidenceSourceKind]）。 */
    val sourceKind: String,
    val reason: String,
    val count: Int,
)

/** Projection row for accepted-write counts per epoch hour **per channel** (calibration input). */
data class HourlyAcceptedCountRow(
    /** `learner_chat_evidence.source_kind`（通道维，见 [StudyDbValue.ChatEvidenceSourceKind]）。 */
    val sourceKind: String,
    val hourBucket: Long,
    val count: Int,
)
