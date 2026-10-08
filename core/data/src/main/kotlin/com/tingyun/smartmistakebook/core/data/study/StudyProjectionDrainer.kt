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
import com.tingyun.smartmistakebook.core.domain.PracticeUnitBindingFacts
import com.tingyun.smartmistakebook.core.domain.derivePracticeUnitBindingAttributions
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
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

    /**
     * S20：后继映射缓存。映射是**内容**输入（只随内容调和的合并退役变化），不必每次 drain
     * 重读全表；失效由仓库订阅 `knowledge_node` 表级失效信号后调用
     * [invalidateKnowledgeNodeSuccessors]。加载仍走 `onDatabase`（S10 的调度器纪律）。
     */
    private val knowledgeNodeSuccessorsCache = KnowledgeNodeSuccessorsCache {
        KnowledgeNodeSuccessors(onDatabase { database.readKnowledgeNodeSuccessors() })
    }

    /**
     * S20：`knowledge_node` 表变化（Room 表级失效）时由仓库调用。只清缓存——在飞的那次
     * drain 已持有映射实例，**一次 drain 内映射恒定**的不变量不受影响（见缓存类 KDoc）。
     */
    fun invalidateKnowledgeNodeSuccessors() {
        knowledgeNodeSuccessorsCache.invalidate()
    }

    suspend fun drain(): PersistedLearnerSnapshot? {
        var consecutiveCasConflicts = 0
        // 合并重定向在**一次排空内是常量**，且增量投影与全量重放必须用同一份——
        // 两条路用不同的映射会算出不同的掌握度，而重放的职责正是复现增量的结果。
        // S20：映射跨 drain 复用缓存（内容调和写入时失效）；这里取一次，整个 drain 共用。
        val knowledgeNodeSuccessors = knowledgeNodeSuccessorsCache.current()
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
        // KF-32（3B 步骤三）：重放的输入是"账本 + **当前绑定表** + 取代链"三件套——
        // `BINDING_CHANGED` 事件宣告改绑，但重派生读的是重放时刻的 `practice_unit_knowledge_binding`
        // 现役集合（upcasting：历史行不动，归属按今天的绑定算）。读一次、整本重放共用同一份。
        val currentPracticeUnitBindings = onDatabase {
            readCurrentPracticeUnitBindings(ledger.validPrefix.map { it.event })
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
                // KF-32：改绑后历史证据按当前绑定重挂；K1：当前绑定为空的题在本侧物化
                // pseudo 兜底事实（题不在映射里等异常输入才由投影器退回写时快照）。
                currentBindingsByPracticeUnit = currentPracticeUnitBindings,
                // W0-1 ①：跨版本覆盖要在重放入口声明"被替换的那份已经归档"（同版本或空库无需声明）。
                displacedSnapshot = displacedSnapshot,
                displacedSnapshotArchived = archived,
                // S8：读边界（loadLearningLedger）已经用 SHA-256 校验过每行的规范指纹，重放直接复用，
                // 不在投影 pass 里对同一批事件重算一遍（值仍会在提交侧与账本行比对）。
                canonicalFingerprints = ledger.validPrefix.associate {
                    it.event.ledgerEventId to it.canonicalFingerprint
                },
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
     * KF-32：把账本里出现过的每道题的**当前绑定**读出（题 → 绑定事实）。
     *
     * "当前" = 最近一次确认那一批（`readCurrentPracticeUnitKnowledgeBindings`）——不是表里的
     * 全部行：改绑后被证据引用而保留的旧绑定是审计遗迹（RESTRICT 外键挡住物删），把它算进来
     * 历史证据会同时挂到新旧两个节点、旧节点永远清不掉。
     *
     * 重放自己的 `basisRevisionId` 口径是"该 attempt 快照的 revision"（历史归属锚点），所以这里
     * 只交事实、不预先造归属——归属由投影器用每个 attempt 自己的 revision 现场派生（与写入期
     * 同一单源函数 `derivePracticeUnitBindingAttributions`）。
     *
     * K1（空绑定 = 诚实未分类）：**当前绑定集合为空**的题在这里按写路径同一规则物化 pseudo
     * 兜底桶（见 [pseudoBindingFacts]），交给投影器派生——历史证据因此改挂 `pseudo:<科目>`
     * 而不是永远留在旧知识点上。映射对每个查询过的题都会放入条目；条目为空列表只剩"无法物化"
     * 的异常（题没有评估记录 / 端口拒绝），投影器对空列表仍退回写时快照的 `ifEmpty` 兜底。
     *
     * 物化只对**证据归属的两个消费点**（attempt / 揭示）触发：曝光事件（
     * `TutorAnswerExposureOutcome`）的投影不读归属（`LearningProjector.replay` 只对
     * attempt/reveal 查本映射），为它写 pseudo 绑定没有消费方——重放不制造无消费者的行。
     */
    private suspend fun readCurrentPracticeUnitBindings(
        ledger: List<LearningLedgerEvent>,
    ): Map<String, List<PracticeUnitBindingFacts>> {
        val practiceUnitIds = ledger.mapNotNull { event: LearningLedgerEvent ->
            when (event) {
                is Attempt -> event.practiceUnitId
                is AnswerRevealOutcome -> event.practiceUnitId
                is TutorAnswerExposureOutcome -> event.practiceUnitId
                is AttemptCorrection -> null
                is ChatEvidenceSubmitted -> null
                is BindingChanged -> null
            }
        }.distinct()
        if (practiceUnitIds.isEmpty()) return emptyMap()
        val attributionConsumers = ledger.mapNotNullTo(linkedSetOf()) { event: LearningLedgerEvent ->
            when (event) {
                is Attempt -> event.practiceUnitId
                is AnswerRevealOutcome -> event.practiceUnitId
                is TutorAnswerExposureOutcome -> null
                is AttemptCorrection -> null
                is ChatEvidenceSubmitted -> null
                is BindingChanged -> null
            }
        }
        return practiceUnitIds.associateWith { practiceUnitId ->
            val current = database.readCurrentPracticeUnitKnowledgeBindings(practiceUnitId)
            if (current.isNotEmpty()) {
                current.map { binding ->
                    PracticeUnitBindingFacts(
                        bindingId = binding.bindingId,
                        practiceUnitId = binding.practiceUnitId,
                        knowledgeNodeId = binding.knowledgeNodeId,
                        taxonomyVersion = binding.taxonomyVersion,
                    )
                }
            } else if (practiceUnitId in attributionConsumers) {
                pseudoBindingFacts(practiceUnitId)
            } else {
                emptyList()
            }
        }
    }

    /**
     * K1：空绑定题在重放侧的 pseudo 兜底事实——与写路径
     * `StudyPracticeUnitFacts.attributionSetFor` 的空绑定分支**逐条同规则**：
     * 复用既有 `ensurePseudoKnowledgeBinding`（不新增第二条节点/绑定创建路径），
     * taxonomy 用同一常量 `PSEUDO_ATTRIBUTION_TAXONOMY_VERSION`、绑定的 revision 取该题
     * **当前** revision、科目取 problem.subject、时间取 revision 创建时刻（确定性，重放幂等）。
     *
     * 派生交给投影器（`derivePracticeUnitBindingAttributions`）：单条绑定 → 权重 1.0、
     * `PRIMARY`、`DIRECT`，`basisRevisionId` 用该 attempt 快照自己的 revision——与写路径
     * 当时的归属（同样单条伪绑定、权重 1.0、PRIMARY、DIRECT）逐条同规则。
     *
     * 返回空列表 = 无法物化（题没有评估记录，或端口/数据拒绝）——这不是"空绑定"形态，
     * 交给投影器的 `ifEmpty` 异常兜底退回写时快照，不静默丢证据。
     */
    private suspend fun pseudoBindingFacts(practiceUnitId: String): List<PracticeUnitBindingFacts> {
        val record = database.readPracticeUnitAssessment(practiceUnitId) ?: return emptyList()
        val pseudo = database.ensurePseudoKnowledgeBinding(
            practiceUnitId = practiceUnitId,
            problemRevisionId = record.problemRevisionId,
            taxonomyVersion = StudyPracticeUnitFacts.PSEUDO_ATTRIBUTION_TAXONOMY_VERSION,
            subject = record.subject,
            acceptedAtEpochMillis = record.revisionCreatedAtEpochMillis,
        ) ?: return emptyList()
        return listOf(
            PracticeUnitBindingFacts(
                bindingId = pseudo.bindingId,
                practiceUnitId = pseudo.practiceUnitId,
                knowledgeNodeId = pseudo.knowledgeNodeId,
                taxonomyVersion = pseudo.taxonomyVersion,
            ),
        )
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
                is BindingChanged -> EVENT_KIND_BINDING_CHANGED
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
        /** KF-32 改绑补偿事件：仅全量重放消费（`loadProjectionBatch` 遇它即 FULL_REPLAY_REQUIRED）。 */
        const val EVENT_KIND_BINDING_CHANGED = "BINDING_CHANGED"
    }
}
