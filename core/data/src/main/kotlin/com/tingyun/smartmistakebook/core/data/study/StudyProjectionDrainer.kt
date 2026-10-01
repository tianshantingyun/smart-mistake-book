package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ConsumedLedgerEventReceipt
import com.tingyun.smartmistakebook.core.database.LearningLedgerIntegrityException
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.PersistedLearnerSnapshot
import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.ProjectionCasConflictException
import com.tingyun.smartmistakebook.core.database.ProjectionCommit
import com.tingyun.smartmistakebook.core.database.ProjectionCommitMode
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeSuccessors
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drains the immutable learning ledger into the learner projection (spec §6):
 * incremental batches with CAS retries, and a bounded full replay when the
 * ledger demands one. Extracted from the study repository so the ledger/CAS
 * mechanics stay readable on their own; every database and projector access is
 * an explicit constructor dependency.
 *
 * S10（W4-2 投影热路径）：数据库/归档与投影计算各自**显式**切调度器（默认
 * `Dispatchers.IO` / `Dispatchers.Default`），不再把工作跑在调用方给的 dispatcher 上
 * ——`drain()` 的调用方可能是 UI 或任意协程上下文，投影放大（最多 64 步 × 100 事件）
 * 不该占着那个上下文跑。
 */
internal class StudyProjectionDrainer(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val learningProjector: LearningProjector,
    /** 归档行的 `archived_at_epoch_millis` 取这里的当前时刻（不猜、不借用投影时刻）。 */
    private val clock: Clock,
    /** S10：投影/重放计算的显式调度器。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** S10：数据库读写与归档 JSON 编码的显式调度器。 */
    private val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun drain(): PersistedLearnerSnapshot? {
        var consecutiveCasConflicts = 0
        // 合并重定向在**一次排空内是常量**，且增量投影与全量重放必须用同一份——
        // 两条路用不同的映射会算出不同的掌握度，而重放的职责正是复现增量的结果。
        val knowledgeNodeSuccessors = KnowledgeNodeSuccessors(
            onDatabase { database.readKnowledgeNodeSuccessors() },
        )
        // S4（W4-2 投影热路径）：批内快照复用——一次 drain 调用内每个已提交的批次结果直接
        // 作为下一轮的"当前快照"，不再每步重读 9 张投影表。正确性由 `loadProjectionBatch`
        // 每步现读的 header checkpoint 守住：别的写入者若插进来，checkpoint 对不上就进 CAS
        // 冲突分支，并强制丢弃复用副本重读。
        var current: PersistedLearnerSnapshot? = null
        var currentLoaded = false
        repeat(MAX_PROJECTION_DRAIN_STEPS) {
            if (!currentLoaded) {
                current = onDatabase { database.readCurrentLearnerSnapshot(PROJECTION_NAME, learnerId) }
                currentLoaded = true
            }
            val batch = onDatabase {
                database.loadProjectionBatch(
                    projectionName = PROJECTION_NAME,
                    learnerId = learnerId,
                    limit = PROJECTION_BATCH_SIZE,
                )
            }
            val expectedCheckpoint = current?.snapshot?.checkpoint?.lastSequence ?: 0L
            if (batch.previousCheckpoint != expectedCheckpoint) {
                consecutiveCasConflicts++
                if (consecutiveCasConflicts >= MAX_CAS_RETRIES) {
                    throw ProjectionCasConflictException("Projection checkpoint changed during drain")
                }
                currentLoaded = false
                return@repeat
            }
            when (batch.stopReason) {
                ProjectionBatchStopReason.GAP,
                ProjectionBatchStopReason.CONFLICT,
                -> throw LearningLedgerIntegrityException(
                    batch.detail ?: "Learning ledger stopped at ${batch.blockedAtSequence}",
                )

                ProjectionBatchStopReason.FULL_REPLAY_REQUIRED -> {
                    try {
                        current = commitFullReplay(current, knowledgeNodeSuccessors)
                        currentLoaded = true
                        consecutiveCasConflicts = 0
                    } catch (conflict: ProjectionCasConflictException) {
                        consecutiveCasConflicts++
                        currentLoaded = false
                        if (consecutiveCasConflicts >= MAX_CAS_RETRIES) throw conflict
                    }
                }

                ProjectionBatchStopReason.END_OF_LEDGER,
                ProjectionBatchStopReason.LIMIT_REACHED,
                -> {
                    val previous = current?.snapshot ?: LearnerSnapshot.empty(
                        learnerId = learnerId,
                        projectorVersion = LearningProjector.VERSION,
                    )
                    val requiresReplay = previous.checkpoint.projectorVersion != LearningProjector.VERSION ||
                        (
                            batch.events.isEmpty() &&
                                (
                                    previous.freshness != LearnerSnapshotFreshness.CURRENT ||
                                        previous.projectionStatus != ProjectionStatus.CURRENT
                                    )
                            )
                    if (requiresReplay) {
                        try {
                            current = commitFullReplay(current, knowledgeNodeSuccessors)
                            currentLoaded = true
                            consecutiveCasConflicts = 0
                        } catch (conflict: ProjectionCasConflictException) {
                            consecutiveCasConflicts++
                            currentLoaded = false
                            if (consecutiveCasConflicts >= MAX_CAS_RETRIES) throw conflict
                        }
                    } else if (batch.events.isEmpty()) {
                        return current
                    } else {
                        val result = onCompute {
                            learningProjector.project(
                                previous = previous,
                                events = batch.events.map { it.event },
                                knownLedgerHeadSequence = batch.ledgerHeadSequence,
                                authoritativePresentationStates = batch.authoritativePresentationStates,
                                knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                            )
                        }
                        check(
                            result.missingSequence == null &&
                            result.conflictedAttemptIds.isEmpty() &&
                                result.conflictedAnswerRevealOutcomeIds.isEmpty() &&
                                result.conflictedTutorAnswerExposureOutcomeIds.isEmpty() &&
                                result.deferredAttemptIds.isEmpty() &&
                                result.deferredAnswerRevealOutcomeIds.isEmpty() &&
                                result.deferredTutorAnswerExposureOutcomeIds.isEmpty(),
                        ) { "Projector rejected a database-validated incremental prefix" }
                        val commit = ProjectionCommit(
                            projectionName = PROJECTION_NAME,
                            learnerId = learnerId,
                            expectedPreviousCheckpoint = expectedCheckpoint,
                            expectedPreviousStateVersion = current?.stateVersion ?: 0L,
                            mode = ProjectionCommitMode.INCREMENTAL,
                            knownLedgerHeadSequence = batch.ledgerHeadSequence,
                            consumedLedgerEvents = batch.events.map { persisted ->
                                ConsumedLedgerEventReceipt(
                                    eventKind = persisted.outbox.eventKind,
                                    eventId = persisted.outbox.eventId,
                                    eventSequence = persisted.outbox.outboxSequence,
                                    canonicalFingerprint = persisted.canonicalFingerprint,
                                )
                            },
                            presentationProjectionStates = result.presentationProjectionStates,
                            snapshot = result.snapshot,
                            expectedProjectorVersion = LearningProjector.VERSION,
                        )
                        try {
                            current = onDatabase { database.commitProjection(commit) }
                            currentLoaded = true
                            consecutiveCasConflicts = 0
                        } catch (conflict: ProjectionCasConflictException) {
                            consecutiveCasConflicts++
                            currentLoaded = false
                            if (consecutiveCasConflicts >= MAX_CAS_RETRIES) throw conflict
                        }
                    }
                }
            }
        }
        throw ProjectionCasConflictException("Projection did not drain within the bounded work limit")
    }

    private suspend fun commitFullReplay(
        current: PersistedLearnerSnapshot?,
        knowledgeNodeSuccessors: KnowledgeNodeSuccessors,
    ): PersistedLearnerSnapshot {
        val ledger = onDatabase { database.loadLearningLedger(learnerId) }
        if (ledger.status != LearningLedgerReadStatus.COMPLETE) {
            throw LearningLedgerIntegrityException(
                ledger.detail ?: "Full replay blocked at ${ledger.blockedAtSequence}",
            )
        }
        // W0-1 ③：重放会原地覆盖投影表，被覆盖的那份必须先整份落进 `projection_archive`
        // （`current` 读的是重放前的状态，所以这一行就是"旧投影"）。顺序即机制：先归档再重放，
        // 反过来就只剩新值——而"改数值可回退"正是 Wave 0 要建立的前提。
        val displacedSnapshot = current?.snapshot
        val archived = archiveDisplacedSnapshot(displacedSnapshot)
        val result = onCompute {
            learningProjector.replay(
                learnerId = learnerId,
                ledger = ledger.validPrefix.map { it.event },
                knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                // W0-1 ①：跨版本覆盖要在重放入口声明"被替换的那份已经归档"（同版本或空库无需声明）。
                displacedSnapshot = displacedSnapshot,
                displacedSnapshotArchived = archived,
            )
        }
        val expectedCheckpoint = current?.snapshot?.checkpoint?.lastSequence ?: 0L
        val consumed = ledger.validPrefix
            .filter { it.event.eventSequence > expectedCheckpoint }
            .map { persisted -> persisted.toReceipt() }
        return onDatabase {
            database.commitProjection(
                ProjectionCommit(
                    projectionName = PROJECTION_NAME,
                    learnerId = learnerId,
                    expectedPreviousCheckpoint = expectedCheckpoint,
                    expectedPreviousStateVersion = current?.stateVersion ?: 0L,
                    mode = ProjectionCommitMode.FULL_REPLAY,
                    knownLedgerHeadSequence = result.snapshot.knownLedgerHeadSequence,
                    consumedLedgerEvents = consumed,
                    presentationProjectionStates = result.presentationProjectionStates,
                    snapshot = result.snapshot,
                    expectedProjectorVersion = LearningProjector.VERSION,
                ),
            )
        }
    }

    /**
     * 把即将被重放覆盖的投影整份归档；返回"是否落了行"。
     *
     * 空库/首次投影（[snapshot] 为 null）没有可归档的东西，返回 false —— 那时重放不是替换，
     * 而是从空开始，入口守卫也据此放行（`LearningProjector.replay` 的契约）。
     * 归档失败**不吞**：重放马上要覆盖它，吞掉就等于把这一版投影悄悄丢掉。
     */
    private suspend fun archiveDisplacedSnapshot(snapshot: LearnerSnapshot?): Boolean {
        if (snapshot == null) return false
        onDatabase {
            database.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = PROJECTION_NAME,
                    learnerId = learnerId,
                    snapshotJson = LearnerSnapshotJson.encode(snapshot),
                    projectorVersion = snapshot.checkpoint.projectorVersion,
                    archivedAtEpochMillis = clock.millis(),
                ),
            )
        }
        return true
    }

    private suspend fun <T> onDatabase(block: suspend () -> T): T =
        withContext(databaseDispatcher) { block() }

    private suspend fun <T> onCompute(block: suspend () -> T): T =
        withContext(computeDispatcher) { block() }

    private fun com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent.toReceipt() =
        ConsumedLedgerEventReceipt(
            eventKind = when (event) {
                is Attempt -> EVENT_KIND_ATTEMPT
                is AttemptCorrection -> EVENT_KIND_CORRECTION
                is com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome -> EVENT_KIND_ANSWER_REVEAL
                is TutorAnswerExposureOutcome -> EVENT_KIND_TUTOR_ANSWER_EXPOSURE
                is ChatEvidenceSubmitted -> EVENT_KIND_CHAT_EVIDENCE
            },
            eventId = event.ledgerEventId,
            eventSequence = event.eventSequence,
            canonicalFingerprint = canonicalFingerprint,
        )

    private companion object {
        const val PROJECTION_NAME = "study-experience-v1"
        const val PROJECTION_BATCH_SIZE = 100
        const val MAX_CAS_RETRIES = 4
        const val MAX_PROJECTION_DRAIN_STEPS = 64
        const val EVENT_KIND_ATTEMPT = "ATTEMPT"
        const val EVENT_KIND_CORRECTION = "ATTEMPT_CORRECTION"
        const val EVENT_KIND_ANSWER_REVEAL = "ANSWER_REVEAL_OUTCOME"
        const val EVENT_KIND_TUTOR_ANSWER_EXPOSURE = "TUTOR_ANSWER_EXPOSURE_OUTCOME"
        const val EVENT_KIND_CHAT_EVIDENCE = "CHAT_EVIDENCE_SUBMITTED"
    }
}
