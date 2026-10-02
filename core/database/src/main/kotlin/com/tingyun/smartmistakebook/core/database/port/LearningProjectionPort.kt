package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteCommand
import com.tingyun.smartmistakebook.core.database.AnswerRevealWriteResult
import com.tingyun.smartmistakebook.core.database.AssessmentEventSeedRecord
import com.tingyun.smartmistakebook.core.database.AssessmentItemSnapshotSeedRecord
import com.tingyun.smartmistakebook.core.database.AttemptAdvanceProofRecord
import com.tingyun.smartmistakebook.core.database.AttemptCorrectionRecord
import com.tingyun.smartmistakebook.core.database.AttemptCorrectionResult
import com.tingyun.smartmistakebook.core.database.AttemptPersistenceRecord
import com.tingyun.smartmistakebook.core.database.AttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.ReviewLogEntry
import com.tingyun.smartmistakebook.core.database.ReviewLogSampleRecord
import com.tingyun.smartmistakebook.core.database.AttemptWriteResult
import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealFact
import com.tingyun.smartmistakebook.core.database.PersistedAnswerRevealP0
import com.tingyun.smartmistakebook.core.database.PersistedAttemptP0
import com.tingyun.smartmistakebook.core.database.PersistedCorrectionP0
import com.tingyun.smartmistakebook.core.database.PersistedLearnerSnapshot
import com.tingyun.smartmistakebook.core.database.PersistedProjectionArchive
import com.tingyun.smartmistakebook.core.database.PersistedReviewLogLast
import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionCommit
import com.tingyun.smartmistakebook.core.database.ReviewAttemptWriteCommand
import com.tingyun.smartmistakebook.core.database.ReviewAttemptWriteResult
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot

/**
 * Port for assessment snapshot/event writes (fixture seeding retired with D-M M1).
 */
interface SeedAssessmentPort {
    suspend fun saveAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)

    suspend fun saveAssessmentEvidenceSnapshot(snapshot: AssessmentEvidenceSnapshot)

    suspend fun appendAssessmentEvent(event: AssessmentEventSeedRecord)
}

/**
 * Port for attempt, answer-reveal, and correction writes plus deprecated P0 inspection.
 */
interface AttemptWritePort {
    suspend fun recordAttempt(command: AttemptWriteCommand): AttemptWriteResult

    suspend fun recordReviewAttempt(
        command: ReviewAttemptWriteCommand,
    ): ReviewAttemptWriteResult

    suspend fun recordAnswerReveal(command: AnswerRevealWriteCommand): AnswerRevealWriteResult

    suspend fun reconcileAnswerRevealOutcomes(
        learnerId: String,
        limit: Int = 100,
    ): List<AnswerRevealWriteResult>

    /**
     * 读该呈现上**已经发生**的答案揭示（W1-3/KF-02）：提交前的证据定价分支要据此判断
     * "本次作答之前答案是否已被揭示"。没有揭示返回 null。
     *
     * 刻意与 [recordAnswerReveal] 同置一处：揭示的读与写共享同一语义（揭示让呈现终止，
     * 其后的每次作答都算"看过答案"）；轻量事实、不走 P0 校验链（那是审计读回的语义）。
     */
    suspend fun findAnswerRevealForPresentation(
        learnerId: String,
        presentationId: String,
    ): PersistedAnswerRevealFact?

    suspend fun appendAttemptCorrection(correction: AttemptCorrectionRecord): AttemptCorrectionResult

    /**
     * Raw collected review evidence (spec mastery-scheduling 3.1). Insert is
     * idempotent per (learner, source id); collection is decoupled from
     * scheduling and never blocks the ledger path on failure.
     */
    suspend fun recordReviewLogEntries(entries: List<ReviewLogEntry>)

    suspend fun readReviewLogSamples(learnerId: String, limit: Int): List<ReviewLogSampleRecord>

    /** Most recent review-log timestamp for one card and evidence kind. */
    suspend fun readLastReviewLogAt(
        learnerId: String,
        practiceUnitId: String,
        sourceKind: String,
    ): Long?

    /**
     * W2-4/KF-23：该卡**最近一次**复习行的（rating, 时间戳）——写入方据此派生新行的
     * `review_log.state`（前条 AGAIN→Relearning、同学习日→Learning、跨学习日→Review、
     * 无前条→New），与迁移回填同一口径。
     */
    suspend fun findLastReviewLogRow(
        learnerId: String,
        practiceUnitId: String,
    ): PersistedReviewLogLast?

    suspend fun findAttemptPersistence(submissionId: String): AttemptPersistenceRecord?

    suspend fun findAttemptAdvanceProof(attemptId: String): AttemptAdvanceProofRecord? = null

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAssessmentSnapshotP0(
        assessmentItemSnapshotId: String,
    ): AssessmentItemSnapshotSeedRecord?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAttemptP0(attemptId: String): PersistedAttemptP0?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readCorrectionP0(correctionId: String): PersistedCorrectionP0?

    @Deprecated("P0 inspection only; keep data-layer wiring behind the repository boundary")
    suspend fun readAnswerRevealP0(outcomeId: String): PersistedAnswerRevealP0?
}

/**
 * Port for learning projection and ledger reads/writes.
 */
interface LearningProjectionPort {
    suspend fun loadProjectionBatch(
        projectionName: String,
        learnerId: String,
        limit: Int,
    ): ProjectionBatch

    suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead

    suspend fun readCurrentLearnerSnapshot(
        projectionName: String,
        learnerId: String,
    ): PersistedLearnerSnapshot?

    suspend fun commitProjection(commit: ProjectionCommit): PersistedLearnerSnapshot

    /**
     * 归档一份即将被重放覆盖的投影（内核修复路线图 W0-1 ③）。
     *
     * 调用点在 `StudyProjectionDrainer.commitFullReplay` 里、`LearningProjector.replay` **之前**：
     * 顺序本身就是机制——重放原地覆盖投影表，晚一步归档就只剩新值。
     */
    suspend fun archiveProjectionSnapshot(record: ProjectionArchiveRecord)

    /**
     * 读回该 learner **最近一份**归档投影（回退工具，runbook §4 第 1 步）。
     * 没有归档返回 null（不是空记录——"没有可恢复的东西"由恢复口显式拒绝）。
     */
    suspend fun readLatestArchivedProjection(
        projectionName: String,
        learnerId: String,
    ): PersistedProjectionArchive?

    /**
     * **回退一步**：把当前投影换成最近一次被覆盖的那份归档，并把被换下的当前投影追加归档
     * （回退工具，runbook §4 第 2–4 步；专用路径，不复用 `commitProjection`）。
     *
     * 判定与写入分两段，判定全部在写之前（reason 见 [ProjectionRestoreRejection]）：
     * 1. 无归档 → `NO_ARCHIVE`；
     * 2. 跨版本恢复拒：`archive.projectorVersion`（以及 JSON 载荷里的 checkpoint 版本）必须
     *    等于 [expectedProjectorVersion] → 否则 `VERSION_MISMATCH`；
     * 3. 归档 JSON 解不回 → `MALFORMED_ARCHIVE`（原始解码异常只作 cause）；
     * 4. [restoredAtEpochMillis] 早于目标归档时刻 → `RESTORED_AT_IN_PAST`；
     * 5. 归档 checkpoint 超前当前账本头 → `CHECKPOINT_AHEAD`（写回去会静默跳过后续事件）；
     * 6. 恢复目标早于既有呈现事实（`presentation_projection_state` 有
     *    `terminal_event_sequence > checkpoint` 的行）→ `PRESENTATION_AHEAD`：增量排空以
     *    **未回滚的呈现行**为权威，工具不静默回滚呈现态（没有历史版本可精确退到当时状态）；
     * 7. `schema_ddl` 与现库现读口径比对，不一致 → `SCHEMA_MISMATCH`；
     * 8. decode（已）→ **单事务**重建投影表；`state_version` 递增；`checkpoint` /
     *    `known_ledger_head` / `projector_version` 与归档逐字一致。
     *
     * **呈现态前置条件**：工具不写 `presentation_projection_state`（它是账本派生态，不在归档
     * JSON 内）。因此恢复目标必须**不早于**现存的每一个终局揭示；较早的 save-point 会被
     * `PRESENTATION_AHEAD` 拒掉——出路是先处理呈现态，或走全量重放路径（重放会按账本整体
     * 重派生呈现权威）。
     *
     * **版本陷阱（必须按 KDoc 的口径使用）**：跨版本一律拒是刻意的——归档 JSON 是旧二进制
     * 算出来的快照，用新二进制恢复它，排空会立刻按新公式判定"版本不匹配 → 全量重放"，
     * 把刚恢复的那份**再次归档**并重算一遍，看起来像回退没生效（runbook §5）。真实灾难恢复
     * 要回到旧算法，必须**同时回滚应用版本**，让二进制与 `archive.projector_version` 对应；
     * 本工具只在**同版本内**回退。
     *
     * 恢复是维护操作：应在排空静止时执行；`presentation_projection_state` 不在归档 JSON 内
     * （呈现态是账本派生态），恢复不触碰它。
     *
     * [restoredAtEpochMillis] 是恢复时刻，落成"被换下那份"归档行的 `archived_at`：调用方取
     * **晚于任何既有归档行**的时刻（生产即 now）。"最近一份"按 `archived_at DESC, archive_id
     * DESC` 取，时间倒挂会让刚归档的那份排不到最前、连续回退原地打转。
     */
    suspend fun restoreArchivedProjection(
        projectionName: String,
        learnerId: String,
        expectedProjectorVersion: String,
        restoredAtEpochMillis: Long,
    ): PersistedLearnerSnapshot
}