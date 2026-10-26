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
 *
 * AUD-02 追加读口：知识点复习的题面快照同样落在 `assessment_item_snapshot`（同一写口
 * [saveAssessmentItemSnapshot]），恢复路径要把它读回来——按 id 读，读写共享同一
 * "呈现在哪张表、以什么身份"的语义，就不会出现"写一张表、读另一张表"的分叉。
 * 刻意不复用 `@Deprecated` 的 P0 审计读口（`readAssessmentSnapshotP0`）：那句弃用说的正是
 * "数据层自己起一个带语义的读口，别把审计读口当生产读路径"。
 */
interface SeedAssessmentPort {
    suspend fun saveAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)

    /**
     * 按 id 读回一条已物化的呈现快照；无行 = null。
     *
     * 缺失与损坏由上层区分处置：无行 = 这个身份没有题面（恢复方走失败出口，不猜题面）；
     * 有行但解不开 = 同样按损坏走失败出口。
     */
    suspend fun findAssessmentItemSnapshot(assessmentItemSnapshotId: String): AssessmentItemSnapshotSeedRecord?

    /**
     * G5-A / KD-67：`verification_status` 的**单列 CAS**（不新建行、不重写整行）。
     *
     * 消灭的失败：核验确认流要显式把一条 `UNKNOWN` 快照升为 `VERIFIED`/`USER_ASSERTED`，
     * 但既有两个写口都不适用——[saveAssessmentItemSnapshot] 的不可变闸把"同身份不同载荷"
     * 判冲突（升级必然撞），[overwriteAssessmentItemSnapshot] 是 KD-41 损坏行修复口
     * （契约只允许"既有行解不开"）。没有本口，确认流只能整行覆盖：题面/揭示态/时间戳被
     * 顺带重写，"首见即真"与修复口的边界一起失守。
     *
     * 语义（一条参数化 `UPDATE`，禁止拼接 SQL）：
     * - `SET verification_status = :newVerificationStatus
     *    WHERE assessment_item_snapshot_id = :id AND verification_status = :expectedCurrentStatus`；
     * - 返回**真实受影响行数**（主键唯一，故只可能是 1 或 0）：1 = 已写入；0 = 行不存在
     *   或当前状态已变，调用方重新读取后处置；
     * - 只允许 `UNKNOWN` 作为 [expectedCurrentStatus]，目标只允许 `VERIFIED` /
     *   `USER_ASSERTED`（已核验状态之间不得互换、不得降级）；非法输入抛契约异常；
     * - 不触碰任何其他列（题干/选项/键载荷/揭示态/时间戳全部原样）。
     */
    suspend fun compareAndSetAssessmentItemVerificationStatus(
        assessmentItemSnapshotId: String,
        expectedCurrentStatus: String,
        newVerificationStatus: String,
    ): Int

    /**
     * KD-41：**原地覆盖**一条已知损坏的呈现行（同 id 整行载荷重写，不新增行）。
     *
     * 为什么单开一口：损坏行**不可复用**——它身上没有"首见即真"要保护的题面，而
     * [saveAssessmentItemSnapshot] 的冲突闸会把"重试出题"生成的新题面一直顶回来，调用方
     * 因此被永久锁在失败出口（每次重试白烧一次模型调用、题面永远显示不出来）。让重试可恢复
     * 就需要一条能重写这一行的写口。
     *
     * 契约（调用方负责，本口不替它判定）：
     * - 只对**既有行解不开**用。解得开的既有行一律走 [saveAssessmentItemSnapshot] 的冲突路径
     *   回读（首见即真），**绝不**走本口——正常行的不可变语义不因本口改变；
     * - 覆盖写进来的内容照走同一套契约校验：不能借"修复"把非法载荷塞进库；
     * - 无该 id 行 → 无操作（不新增行），调用方随后按读回结果处置。
     */
    suspend fun overwriteAssessmentItemSnapshot(item: AssessmentItemSnapshotSeedRecord)

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
     * PRD-05：原子撤销（VOID 更正 + 会话回退 + review_log 有效样本标记）。
     * 默认抛错：旧替身必须显式实现，不能静默把撤销当作 no-op。
     */
    suspend fun undoReviewAttempt(
        command: com.tingyun.smartmistakebook.core.database.ReviewAttemptUndoCommand,
    ): com.tingyun.smartmistakebook.core.database.ReviewAttemptUndoResult =
        throw UnsupportedOperationException("Review undo is not implemented by this database port")

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