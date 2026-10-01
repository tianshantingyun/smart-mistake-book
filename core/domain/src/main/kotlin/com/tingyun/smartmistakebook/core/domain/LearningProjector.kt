package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.AppliedAttemptRecord
import com.tingyun.smartmistakebook.core.model.AppliedAnswerRevealRecord
import com.tingyun.smartmistakebook.core.model.AppliedCorrectionRecord
import com.tingyun.smartmistakebook.core.model.AppliedTutorAnswerExposureRecord
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EventTimeTrust
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.IncrementalLearningEvent
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.StudyDayMath
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.PresentationProjectionState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted

data class LearningProjectionResult(
    val snapshot: LearnerSnapshot,
    val appliedAttemptIds: Set<String>,
    val ignoredAttemptIds: Set<String>,
    val conflictedAttemptIds: Set<String>,
    val deferredAttemptIds: Set<String> = emptySet(),
    val missingSequence: Long? = null,
    val ambiguousAttemptIds: Set<String> = emptySet(),
    val correctedAttemptIds: Set<String> = emptySet(),
    val appliedAnswerRevealOutcomeIds: Set<String> = emptySet(),
    val ignoredAnswerRevealOutcomeIds: Set<String> = emptySet(),
    val conflictedAnswerRevealOutcomeIds: Set<String> = emptySet(),
    val deferredAnswerRevealOutcomeIds: Set<String> = emptySet(),
    val appliedTutorAnswerExposureOutcomeIds: Set<String> = emptySet(),
    val ignoredTutorAnswerExposureOutcomeIds: Set<String> = emptySet(),
    val conflictedTutorAnswerExposureOutcomeIds: Set<String> = emptySet(),
    val deferredTutorAnswerExposureOutcomeIds: Set<String> = emptySet(),
    val presentationProjectionStates: Map<String, PresentationProjectionState> = emptyMap(),
)

/** Deterministic projection of a gap-free append-only attempt prefix into learner state. */
class LearningProjector(
    private val forgettingCurve: ForgettingCurve = ForgettingCurve(),
    private val memoryUpdateModel: MemoryUpdateModel = FsrsMemoryUpdateModel(
        desiredRetention = DEFAULT_DESIRED_RETENTION,
    ),
) {
    /** Test-only convenience. Production adapters must supply persistence-authoritative state. */
    internal fun project(
        previous: LearnerSnapshot,
        events: List<IncrementalLearningEvent>,
        knownLedgerHeadSequence: Long,
    ): LearningProjectionResult = project(
        previous = previous,
        events = events,
        knownLedgerHeadSequence = knownLedgerHeadSequence,
        authoritativePresentationStates = inferPresentationStates(previous, events),
    )

    /**
     * Projects against presentation rows read at [previous]'s checkpoint. The persistence adapter
     * must enforce response-ordinal CAS and one terminal reveal before supplying this pure state.
     */
    fun project(
        previous: LearnerSnapshot,
        events: List<IncrementalLearningEvent>,
        knownLedgerHeadSequence: Long,
        authoritativePresentationStates: Map<String, PresentationProjectionState>,
        /**
         * 已退役知识点的取代链。投影把历史证据按它算到当前节点上——"合并并入"由此生效，
         * 而历史行一字未动。默认空映射＝既有行为逐位不变（见 [KnowledgeNodeSuccessors]）。
         */
        knowledgeNodeSuccessors: KnowledgeNodeSuccessors = KnowledgeNodeSuccessors.EMPTY,
    ): LearningProjectionResult {
        requireCompatibleSnapshot(previous)
        require(knownLedgerHeadSequence >= previous.knownLedgerHeadSequence) {
            "Known ledger head must be monotonic"
        }
        require(knownLedgerHeadSequence >= (events.maxOfOrNull { it.eventSequence } ?: 0)) {
            "Known ledger head must cover every supplied event"
        }
        require(authoritativePresentationStates.all { (id, state) -> id == state.presentationId }) {
            "Authoritative presentation-state keys must match their values"
        }
        val incomingPresentationIds = events.mapNotNullTo(linkedSetOf()) { event ->
            when (event) {
                is Attempt -> event.presentationId
                is AnswerRevealOutcome -> event.presentationId
                is TutorAnswerExposureOutcome -> null
                is ChatEvidenceSubmitted -> null
            }
        }
        require(incomingPresentationIds.all(authoritativePresentationStates::containsKey)) {
            "Every supplied event requires persistence-authoritative presentation state"
        }
        require(incomingPresentationIds.all {
            authoritativePresentationStates.getValue(it).asOfLedgerSequence == previous.checkpoint.lastSequence
        }) { "Presentation state must be read at the learner snapshot checkpoint" }

        val incomingAttemptIds = events.filterIsInstance<Attempt>().mapTo(linkedSetOf(), Attempt::attemptId)
        val incomingRevealIds = events.filterIsInstance<AnswerRevealOutcome>()
            .mapTo(linkedSetOf(), AnswerRevealOutcome::outcomeId)
        val incomingTutorExposureIds = events.filterIsInstance<TutorAnswerExposureOutcome>()
            .mapTo(linkedSetOf(), TutorAnswerExposureOutcome::outcomeId)
        val fingerprinted = events.map { it to LearningLedgerFingerprint.event(it) }
        val byEventId = fingerprinted.groupBy { it.first.ledgerEventId }
        val sameIdConflictVariants = byEventId
            .filterValues { variants -> variants.map { it.second }.distinct().size > 1 }
        val sameIdConflicts = sameIdConflictVariants.keys
        val unique = fingerprinted
            .filterNot { it.first.ledgerEventId in sameIdConflicts }
            .distinctBy { it.first.ledgerEventId }

        val ignored = linkedSetOf<String>()
        val historicalConflicts = sameIdConflictVariants
            .filterValues { variants ->
                variants.minOf { it.first.eventSequence } <= previous.checkpoint.lastSequence
            }
            .keys
            .toMutableSet()
        val batchPayloadConflicts = sameIdConflictVariants
            .filterKeys { it !in historicalConflicts }
            .entries
            .groupBy(
                keySelector = { (_, variants) -> variants.minOf { it.first.eventSequence } },
                valueTransform = { it.key },
            )
        val fresh = mutableListOf<Pair<IncrementalLearningEvent, String>>()
        unique.forEach { (event, fingerprint) ->
            val recorded = when (event) {
                is Attempt -> previous.appliedAttemptRecords[event.attemptId]?.let {
                    it.eventSequence to it.canonicalFingerprint
                }
                is AnswerRevealOutcome -> previous.appliedAnswerRevealRecords[event.outcomeId]?.let {
                    it.eventSequence to it.canonicalFingerprint
                }
                is TutorAnswerExposureOutcome ->
                    previous.appliedTutorAnswerExposureRecords[event.outcomeId]?.let {
                        it.eventSequence to it.canonicalFingerprint
                    }
                is ChatEvidenceSubmitted ->
                    null
            }
            val idExistsAsAnotherType = when (event) {
                is Attempt -> event.attemptId in previous.appliedAnswerRevealRecords ||
                    event.attemptId in previous.appliedTutorAnswerExposureRecords
                is AnswerRevealOutcome -> event.outcomeId in previous.appliedAttemptRecords ||
                    event.outcomeId in previous.appliedTutorAnswerExposureRecords
                is TutorAnswerExposureOutcome -> event.outcomeId in previous.appliedAttemptRecords ||
                    event.outcomeId in previous.appliedAnswerRevealRecords
                is ChatEvidenceSubmitted -> false
            }
            when {
                recorded != null &&
                    recorded.first == event.eventSequence &&
                    recorded.second == fingerprint -> ignored += event.ledgerEventId

                idExistsAsAnotherType || recorded != null || event.eventSequence <= previous.checkpoint.lastSequence ->
                    historicalConflicts += event.ledgerEventId

                else -> fresh += event to fingerprint
            }
        }

        if (previous.projectionStatus == ProjectionStatus.CONFLICTED || historicalConflicts.isNotEmpty()) {
            return unchangedConflict(
                previous = previous,
                ignored = ignored,
                conflicts = historicalConflicts,
                deferred = fresh.mapTo(linkedSetOf()) { it.first.ledgerEventId }.apply {
                    batchPayloadConflicts.values.flatten().forEach(::add)
                },
                incomingAttemptIds = incomingAttemptIds,
                incomingRevealIds = incomingRevealIds,
                incomingTutorExposureIds = incomingTutorExposureIds,
                knownLedgerHeadSequence = knownLedgerHeadSequence,
                presentationProjectionStates = authoritativePresentationStates,
            )
        }

        val memoryStates = previous.problemMemoryStates.toMutableMap()
        val masteryStates = previous.knowledgeMasteryStates.toMutableMap()
        val attemptRecords = previous.appliedAttemptRecords.toMutableMap()
        val revealRecords = previous.appliedAnswerRevealRecords.toMutableMap()
        val tutorExposureRecords = previous.appliedTutorAnswerExposureRecords.toMutableMap()
        val presentationProjectionStates = authoritativePresentationStates.toMutableMap()
        val appliedAttempts = linkedSetOf<String>()
        val appliedReveals = linkedSetOf<String>()
        val appliedTutorExposures = linkedSetOf<String>()
        val appliedChatEvidences = linkedSetOf<String>()
        val chatEvidenceRecords = mutableMapOf<String, ChatEvidenceSubmitted>()
        val ambiguous = linkedSetOf<String>()
        val sequenceConflicts = linkedSetOf<String>()
        val deferred = linkedSetOf<String>()
        var expectedSequence = previous.checkpoint.lastSequence + 1
        var projectedAt = previous.generatedAtEpochMillis
        var missingSequence: Long? = null

        val bySequence = fresh.groupBy { it.first.eventSequence }
        val sequences = (bySequence.keys + batchPayloadConflicts.keys).sorted()
        // KF-17（裁决 8）：同批/source 兄弟姐妹的错峰序号只在**本次调用内**累计；跨批不携带
        // （见 [SourceBatchDispersion] 的口径注释）。
        val dispersion = SourceBatchDispersion()
        fun pendingIdsAfter(sequence: Long): Set<String> = buildSet {
            bySequence.filterKeys { it > sequence }.values.flatten().forEach {
                add(it.first.ledgerEventId)
            }
            batchPayloadConflicts.filterKeys { it > sequence }.values.flatten().forEach(::add)
        }
        for (sequence in sequences) {
            val eventsAtSequence = bySequence[sequence].orEmpty()
            val payloadConflictIds = batchPayloadConflicts[sequence].orEmpty()
            if (sequence > expectedSequence) {
                missingSequence = expectedSequence
                deferred += eventsAtSequence.map { it.first.ledgerEventId }
                deferred += payloadConflictIds
                deferred += pendingIdsAfter(sequence)
                break
            }
            if (sequence < expectedSequence) {
                sequenceConflicts += eventsAtSequence.map { it.first.ledgerEventId } + payloadConflictIds
                continue
            }
            if (payloadConflictIds.isNotEmpty() || eventsAtSequence.size != 1) {
                sequenceConflicts += eventsAtSequence.map { it.first.ledgerEventId } + payloadConflictIds
                deferred += pendingIdsAfter(sequence)
                break
            }

            val (event, fingerprint) = eventsAtSequence.single()
            val effectiveAt = maxOf(projectedAt, event.occurredAtEpochMillis)
            when (event) {
                is Attempt -> {
                    val presentationState = presentationProjectionStates.getValue(event.presentationId)
                    val effectiveAttempt = applyRevealCausality(
                        attempt = event,
                        answerRevealSequence = presentationState.answerRevealSequence,
                        causalSequence = event.eventSequence,
                    )
                    applyAttempt(
                        memoryStates,
                        masteryStates,
                        effectiveAttempt,
                        ambiguous,
                        suppressDuplicateRevealMemory = presentationState.answerRevealSequence != null,
                        effectiveAtEpochMillis = effectiveAt,
                        knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                        dispersion = dispersion,
                    )
                    presentationProjectionStates[event.presentationId] = presentationState.copy(
                        asOfLedgerSequence = event.eventSequence,
                        memoryProjectionApplied = true,
                    )
                    attemptRecords[event.attemptId] = AppliedAttemptRecord(
                        attemptId = event.attemptId,
                        canonicalFingerprint = fingerprint,
                        eventSequence = event.eventSequence,
                        presentationId = event.presentationId,
                        responseOrdinal = event.responseOrdinal,
                    )
                    appliedAttempts += event.attemptId
                }
                is AnswerRevealOutcome -> {
                    val presentationState = presentationProjectionStates.getValue(event.presentationId)
                    if (presentationState.answerRevealSequence != null) {
                        sequenceConflicts += event.outcomeId
                        deferred += pendingIdsAfter(sequence)
                        break
                    }
                    if (!presentationState.memoryProjectionApplied) {
                        val previousMemory = memoryStates[event.practiceUnitId]
                        memoryStates[event.practiceUnitId] = projectAnswerReveal(
                            previous = previousMemory,
                            outcome = event,
                            effectiveAtEpochMillis = effectiveAt,
                            dueDateOffsetDays = if (previousMemory == null) {
                                dispersion.nextOffsetDays(
                                    sourceBundleId = event.assessmentSnapshot.sourceBundleId,
                                    studyDayEpochDay = event.studyDay.epochDay,
                                )
                            } else {
                                0
                            },
                        )
                        // 裁决 26（2026-10-01）：独立「看答案」事件除题卡外**也要 lapse 该题
                        // 绑定的知识点记忆卡**（看答案 = 计一次遗忘失败）。与题卡共用同一道
                        // 呈现态门（`memoryProjectionApplied`）：一次呈现若已由作答投影过记忆，
                        // 揭示不再重复写（两条路径不重复计罚）。
                        lapseKnowledgeCardsForReveal(
                            masteryStates = masteryStates,
                            outcome = event,
                            effectiveAtEpochMillis = effectiveAt,
                            knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                        )
                    }
                    revealRecords[event.outcomeId] = AppliedAnswerRevealRecord(
                        outcomeId = event.outcomeId,
                        presentationId = event.presentationId,
                        canonicalFingerprint = fingerprint,
                        eventSequence = event.eventSequence,
                    )
                    presentationProjectionStates[event.presentationId] = presentationState.copy(
                        asOfLedgerSequence = event.eventSequence,
                        memoryProjectionApplied = true,
                        answerRevealSequence = event.eventSequence,
                    )
                    appliedReveals += event.outcomeId
                }
                is TutorAnswerExposureOutcome -> {
                    memoryStates[event.practiceUnitId] = projectTutorAnswerExposure(
                        previous = memoryStates[event.practiceUnitId],
                        outcome = event,
                        effectiveAtEpochMillis = effectiveAt,
                    )
                    tutorExposureRecords[event.outcomeId] = AppliedTutorAnswerExposureRecord(
                        outcomeId = event.outcomeId,
                        exposureId = event.exposureId,
                        canonicalFingerprint = fingerprint,
                        eventSequence = event.eventSequence,
                    )
                    appliedTutorExposures += event.outcomeId
                }
                is ChatEvidenceSubmitted -> {
                    // 解析到当前节点：证据仍挂在原 id 上（历史不可改写），但掌握度记在
                    // 取代它的节点名下——与 applyAttempt 的处理同源。
                    val knowledgeNodeId = knowledgeNodeSuccessors.resolve(event.knowledgeNodeId)
                    masteryStates[knowledgeNodeId] = projectChatEvidence(
                        previous = masteryStates[knowledgeNodeId],
                        knowledgeNodeId = knowledgeNodeId,
                        event = event,
                        effectiveAtEpochMillis = effectiveAt,
                    )
                    appliedChatEvidences += event.evidenceId
                }
            }
            expectedSequence++
            projectedAt = effectiveAt
        }

        val appliedEventCount = appliedAttempts.size +
            appliedReveals.size +
            appliedTutorExposures.size +
            appliedChatEvidences.size
        val lastSequence = previous.checkpoint.lastSequence + appliedEventCount
        val projectionStatus = when {
            sequenceConflicts.isNotEmpty() -> ProjectionStatus.CONFLICTED
            missingSequence != null -> ProjectionStatus.WAITING_FOR_GAP
            appliedEventCount == 0 && previous.projectionStatus == ProjectionStatus.WAITING_FOR_GAP ->
                ProjectionStatus.WAITING_FOR_GAP
            lastSequence < knownLedgerHeadSequence -> ProjectionStatus.CATCHING_UP
            else -> ProjectionStatus.CURRENT
        }
        val checkpoint = if (appliedEventCount == 0) {
            previous.checkpoint
        } else {
            ProjectionCheckpoint(lastSequence, VERSION, projectedAt)
        }
        val snapshot = previous.copy(
            problemMemoryStates = memoryStates,
            knowledgeMasteryStates = masteryStates,
            checkpoint = checkpoint,
            knownLedgerHeadSequence = knownLedgerHeadSequence,
            generatedAtEpochMillis = projectedAt,
            freshness = if (projectionStatus == ProjectionStatus.CURRENT) {
                LearnerSnapshotFreshness.CURRENT
            } else {
                LearnerSnapshotFreshness.STALE
            },
            projectionStatus = projectionStatus,
            appliedAttemptRecords = boundedRecords(
                attemptRecords,
                AppliedAttemptRecord::eventSequence,
                AppliedAttemptRecord::attemptId,
            ),
            appliedAnswerRevealRecords = boundedRecords(
                revealRecords,
                AppliedAnswerRevealRecord::eventSequence,
                AppliedAnswerRevealRecord::outcomeId,
            ),
            appliedTutorAnswerExposureRecords = boundedRecords(
                tutorExposureRecords,
                AppliedTutorAnswerExposureRecord::eventSequence,
                AppliedTutorAnswerExposureRecord::outcomeId,
            ),
        )
        val outputPresentationStates = presentationProjectionStates.mapValues { (_, state) ->
            state.copy(asOfLedgerSequence = checkpoint.lastSequence)
        }
        return LearningProjectionResult(
            snapshot = snapshot,
            appliedAttemptIds = appliedAttempts,
            ignoredAttemptIds = ignored intersect incomingAttemptIds,
            conflictedAttemptIds = sequenceConflicts intersect incomingAttemptIds,
            deferredAttemptIds = deferred intersect incomingAttemptIds,
            missingSequence = missingSequence,
            ambiguousAttemptIds = ambiguous,
            appliedAnswerRevealOutcomeIds = appliedReveals,
            ignoredAnswerRevealOutcomeIds = ignored intersect incomingRevealIds,
            conflictedAnswerRevealOutcomeIds = sequenceConflicts intersect incomingRevealIds,
            deferredAnswerRevealOutcomeIds = deferred intersect incomingRevealIds,
            appliedTutorAnswerExposureOutcomeIds = appliedTutorExposures,
            ignoredTutorAnswerExposureOutcomeIds = ignored intersect incomingTutorExposureIds,
            conflictedTutorAnswerExposureOutcomeIds = sequenceConflicts intersect incomingTutorExposureIds,
            deferredTutorAnswerExposureOutcomeIds = deferred intersect incomingTutorExposureIds,
            presentationProjectionStates = outputPresentationStates,
        )
    }

    /**
     * Corrections require the complete gap-free ledger so their effects can be replayed safely.
     *
     * **版本守卫（内核修复路线图 W0-1/Q2）**：增量投影 `project()` 一直拒绝"在别的投影版本的快照上
     * 继续算"（[requireCompatibleSnapshot]），但重放没有这道门——重放会整份覆盖存储快照，于是
     * 旧版本二进制可以静默把新版本的投影换掉。两份信息补上这道门：
     *
     * - [displacedSnapshot]：本次重放将要替换掉的存储快照（调用方知道，重放本身从空快照起算）。
     *   版本与当前二进制一致或它就是空快照 → 照旧放行；
     * - [displacedSnapshotArchived]：调用方**已经**把 [displacedSnapshot] 落进
     *   `projection_archive`。跨版本替换只有在"被替换的那份已经存档"时才是合法的：
     *   覆盖不可逆，存档才让"改数值"可回退（回退流程见
     *   `docs/research/kernel-projection-rollback.md`）。
     *
     * 这道门挡的是**调用方**：任何新调用点想跨版本覆盖又没有先归档，会在这里当场抛错；
     * 它挡不住"不含这道门的旧二进制"——那属于部署事实，只能靠归档把损失变成"可恢复"
     * （roadmap W0-1 目标②），不能靠运行期检查（见台账 W0-1 记录）。
     */
    fun replay(
        learnerId: String,
        ledger: List<LearningLedgerEvent>,
        /** 与 [project] 同义：合并重定向。重放与增量必须用**同一份**映射，否则两条路会分叉。 */
        knowledgeNodeSuccessors: KnowledgeNodeSuccessors = KnowledgeNodeSuccessors.EMPTY,
        displacedSnapshot: LearnerSnapshot? = null,
        displacedSnapshotArchived: Boolean = false,
        /**
         * S8（重放管道）：调用方**在读边界**（`loadLearningLedger`/`loadProjectionBatch`）已经用
         * SHA-256 校验过的规范指纹，键 = `ledgerEventId`。给了就直接落进 applied 记录，本 pass
         * 不再重算（同一事件整条管道原本算 3-4 次）；缺项/未给时按 id 在本 pass 内记忆化现算一次，
         * 不改变任何既有调用方的行为（默认空映射）。
         *
         * 复用的前提是"值已经在读边界验过"，但信任仍不是无校验：写进 applied 记录的值会在提交侧
         * （`verifyAppliedEventWindows`）与不可变账本行的指纹逐位比对，说错话在那里被 CAS 拒绝。
         */
        canonicalFingerprints: Map<String, String> = emptyMap(),
    ): LearningProjectionResult {
        require(learnerId.isNotBlank()) { "Learner id must not be blank" }
        displacedSnapshot?.let { previous ->
            if (previous.checkpoint.projectorVersion == VERSION) return@let
            require(displacedSnapshotArchived) {
                "Replaying over a snapshot from ${previous.checkpoint.projectorVersion} requires " +
                    "archiving it first (projection_archive): " +
                    "a projector-version change requires replay from an empty snapshot"
            }
        }
        val ordered = ledger.sortedBy(LearningLedgerEvent::eventSequence)
        require(ordered.map(LearningLedgerEvent::eventSequence) == (1L..ordered.size.toLong()).toList()) {
            "Full replay requires a unique, continuous ledger beginning at sequence one"
        }
        require(ordered.map(LearningLedgerEvent::ledgerEventId).distinct().size == ordered.size) {
            "Ledger event ids must be unique"
        }

        val attemptsById = linkedMapOf<String, Attempt>()
        val corrections = linkedMapOf<String, AttemptCorrection>()
        val presentationOrdinals = mutableMapOf<String, Int>()
        val answerRevealSequences = mutableMapOf<String, Long>()
        // S8：一次 pass 内每个事件指纹只解析一次；有读边界验过的值就直接复用。
        val resolvedFingerprints = HashMap<String, String>()
        fun fingerprintOf(event: LearningLedgerEvent): String = resolvedFingerprints.getOrPut(event.ledgerEventId) {
            canonicalFingerprints[event.ledgerEventId] ?: LearningLedgerFingerprint.event(event)
        }
        ordered.forEach { event ->
            when (event) {
                is Attempt -> {
                    require(event.attemptId !in attemptsById) { "Attempt ids must be immutable and unique" }
                    val expectedOrdinal = (presentationOrdinals[event.presentationId] ?: 0) + 1
                    require(event.responseOrdinal == expectedOrdinal) {
                        "Presentation responses must use contiguous ordinals beginning at one"
                    }
                    presentationOrdinals[event.presentationId] = event.responseOrdinal
                    attemptsById[event.attemptId] = event
                }
                is AnswerRevealOutcome -> require(
                    answerRevealSequences.put(event.presentationId, event.eventSequence) == null,
                ) { "A presentation may have only one terminal answer-reveal outcome" }
                is TutorAnswerExposureOutcome -> Unit
                is ChatEvidenceSubmitted -> Unit
                is AttemptCorrection -> {
                    require(event.attemptId in attemptsById) {
                        "A correction must follow the attempt it replaces"
                    }
                    corrections[event.attemptId] = event
                }
            }
        }

        val memoryStates = mutableMapOf<String, ProblemMemoryState>()
        val masteryStates = mutableMapOf<String, KnowledgeMasteryState>()
        val attemptRecords = mutableMapOf<String, AppliedAttemptRecord>()
        val revealRecords = mutableMapOf<String, AppliedAnswerRevealRecord>()
        val tutorExposureRecords = mutableMapOf<String, AppliedTutorAnswerExposureRecord>()
        val correctionRecords = mutableMapOf<String, AppliedCorrectionRecord>()
        val presentationProjectionStates = mutableMapOf<String, PresentationProjectionState>()
        val ambiguous = linkedSetOf<String>()
        var replayAt = 0L
        var correctionWatermark: Long? = null
        val dispersion = SourceBatchDispersion()
        ordered.forEach { event ->
            val effectiveAt = maxOf(replayAt, event.occurredAtEpochMillis)
            when (event) {
                is Attempt -> {
                    val correction = corrections[event.attemptId]
                    val correctedAttempt = if (correction == null) {
                        event
                    } else {
                        event.copy(
                            evidence = correction.replacementEvidence,
                            problemMemoryOutcome = correction.replacementMemoryOutcome,
                        )
                    }
                    val effective = applyRevealCausality(
                        attempt = correctedAttempt,
                        answerRevealSequence = answerRevealSequences[event.presentationId],
                        causalSequence = event.eventSequence,
                    )
                    val presentationState = presentationProjectionStates.getOrPut(event.presentationId) {
                        PresentationProjectionState(
                            event.presentationId,
                            asOfLedgerSequence = 0,
                            memoryProjectionApplied = false,
                        )
                    }
                    applyAttempt(
                        memoryStates,
                        masteryStates,
                        effective,
                        ambiguous,
                        suppressDuplicateRevealMemory = presentationState.answerRevealSequence != null,
                        effectiveAtEpochMillis = effectiveAt,
                        knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                        dispersion = dispersion,
                    )
                    presentationProjectionStates[event.presentationId] = presentationState.copy(
                        asOfLedgerSequence = event.eventSequence,
                        memoryProjectionApplied = true,
                    )
                    attemptRecords[event.attemptId] = AppliedAttemptRecord(
                        attemptId = event.attemptId,
                        canonicalFingerprint = fingerprintOf(event),
                        eventSequence = event.eventSequence,
                        presentationId = event.presentationId,
                        responseOrdinal = event.responseOrdinal,
                    )
                }
                is AnswerRevealOutcome -> {
                    val presentationState = presentationProjectionStates.getOrPut(event.presentationId) {
                        PresentationProjectionState(
                            event.presentationId,
                            asOfLedgerSequence = 0,
                            memoryProjectionApplied = false,
                        )
                    }
                    if (!presentationState.memoryProjectionApplied) {
                        val previousMemory = memoryStates[event.practiceUnitId]
                        memoryStates[event.practiceUnitId] = projectAnswerReveal(
                            previous = previousMemory,
                            outcome = event,
                            effectiveAtEpochMillis = effectiveAt,
                            dueDateOffsetDays = if (previousMemory == null) {
                                dispersion.nextOffsetDays(
                                    sourceBundleId = event.assessmentSnapshot.sourceBundleId,
                                    studyDayEpochDay = event.studyDay.epochDay,
                                )
                            } else {
                                0
                            },
                        )
                        // 裁决 26：重放与增量走同一条 KC 归因路径（否则两条路会分叉）。
                        lapseKnowledgeCardsForReveal(
                            masteryStates = masteryStates,
                            outcome = event,
                            effectiveAtEpochMillis = effectiveAt,
                            knowledgeNodeSuccessors = knowledgeNodeSuccessors,
                        )
                    }
                    revealRecords[event.outcomeId] = AppliedAnswerRevealRecord(
                        outcomeId = event.outcomeId,
                        presentationId = event.presentationId,
                        canonicalFingerprint = fingerprintOf(event),
                        eventSequence = event.eventSequence,
                    )
                    presentationProjectionStates[event.presentationId] = presentationState.copy(
                        asOfLedgerSequence = event.eventSequence,
                        memoryProjectionApplied = true,
                        answerRevealSequence = event.eventSequence,
                    )
                }
                is TutorAnswerExposureOutcome -> {
                    memoryStates[event.practiceUnitId] = projectTutorAnswerExposure(
                        previous = memoryStates[event.practiceUnitId],
                        outcome = event,
                        effectiveAtEpochMillis = effectiveAt,
                    )
                    tutorExposureRecords[event.outcomeId] = AppliedTutorAnswerExposureRecord(
                        outcomeId = event.outcomeId,
                        exposureId = event.exposureId,
                        canonicalFingerprint = fingerprintOf(event),
                        eventSequence = event.eventSequence,
                    )
                }
                is ChatEvidenceSubmitted -> {
                    val knowledgeNodeId = knowledgeNodeSuccessors.resolve(event.knowledgeNodeId)
                    masteryStates[knowledgeNodeId] = projectChatEvidence(
                        previous = masteryStates[knowledgeNodeId],
                        knowledgeNodeId = knowledgeNodeId,
                        event = event,
                        effectiveAtEpochMillis = effectiveAt,
                    )
                }
                is AttemptCorrection -> {
                    correctionRecords[event.correctionId] = AppliedCorrectionRecord(
                        correctionId = event.correctionId,
                        attemptId = event.attemptId,
                        canonicalFingerprint = fingerprintOf(event),
                        eventSequence = event.eventSequence,
                    )
                    correctionWatermark = maxOf(correctionWatermark ?: 0L, effectiveAt)
                }
            }
            replayAt = effectiveAt
        }

        val projectedAt = replayAt
        val lastSequence = ordered.lastOrNull()?.eventSequence ?: 0
        val snapshot = LearnerSnapshot(
            learnerId = learnerId,
            problemMemoryStates = memoryStates,
            knowledgeMasteryStates = masteryStates,
            checkpoint = ProjectionCheckpoint(lastSequence, VERSION, projectedAt),
            knownLedgerHeadSequence = lastSequence,
            generatedAtEpochMillis = projectedAt,
            correctionWatermarkEpochMillis = correctionWatermark,
            freshness = LearnerSnapshotFreshness.CURRENT,
            projectionStatus = ProjectionStatus.CURRENT,
            appliedAttemptRecords = boundedRecords(
                attemptRecords,
                AppliedAttemptRecord::eventSequence,
                AppliedAttemptRecord::attemptId,
            ),
            appliedCorrectionRecords = boundedRecords(
                correctionRecords,
                AppliedCorrectionRecord::eventSequence,
                AppliedCorrectionRecord::correctionId,
            ),
            appliedAnswerRevealRecords = boundedRecords(
                revealRecords,
                AppliedAnswerRevealRecord::eventSequence,
                AppliedAnswerRevealRecord::outcomeId,
            ),
            appliedTutorAnswerExposureRecords = boundedRecords(
                tutorExposureRecords,
                AppliedTutorAnswerExposureRecord::eventSequence,
                AppliedTutorAnswerExposureRecord::outcomeId,
            ),
        )

        val outputPresentationStates = presentationProjectionStates.mapValues { (_, state) ->
            state.copy(asOfLedgerSequence = lastSequence)
        }
        return LearningProjectionResult(
            snapshot = snapshot,
            appliedAttemptIds = attemptsById.keys.toSet(),
            ignoredAttemptIds = emptySet(),
            conflictedAttemptIds = emptySet(),
            ambiguousAttemptIds = ambiguous,
            correctedAttemptIds = corrections.keys.toSet(),
            appliedAnswerRevealOutcomeIds = revealRecords.keys.toSet(),
            appliedTutorAnswerExposureOutcomeIds = tutorExposureRecords.keys.toSet(),
            presentationProjectionStates = outputPresentationStates,
        )
    }

    private fun projectChatEvidence(
        previous: KnowledgeMasteryState?,
        /** **已解析过的**当前节点 id：证据可能来自一个后被合并掉的节点。 */
        knowledgeNodeId: String,
        event: ChatEvidenceSubmitted,
        effectiveAtEpochMillis: Long,
    ): KnowledgeMasteryState {
        val positive = event.direction == LearningEvidenceDirection.POSITIVE
        val weight = event.weight
        // W3-1/KF-09：掌握点估计由 β-二项（s/f 权重累加 + Jeffreys 先验）派生，替换固定增益 EMA。
        val successWeight = (previous?.successWeight ?: 0.0) + if (positive) weight else 0.0
        val failureWeight = (previous?.failureWeight ?: 0.0) + if (positive) 0.0 else weight
        val updatedProbability = MasteryEstimateMath.pointEstimate(successWeight, failureWeight)
        val evidenceMass = (previous?.evidenceMass ?: 0.0) + weight
        val lowerBound = MasteryEstimateMath.lowerBound(successWeight, failureWeight)
        // W3-2/E 判据：status 跟随知识点记忆卡；本通道（聊天自述）不喂卡，只改 s/f 与展示数值。
        val status = when {
            evidenceMass < 1.0 -> MasteryStatus.UNKNOWN
            clearlyMasteredByMemoryCard(
                previous?.memoryStabilityDays,
                previous?.lastAttemptAtEpochMillis,
                effectiveAtEpochMillis,
            ) -> MasteryStatus.MASTERED
            else -> MasteryStatus.LEARNING
        }
        return KnowledgeMasteryState(
            knowledgeNodeId = knowledgeNodeId,
            masteryScore = updatedProbability,
            conservativeMasteryScore = lowerBound,
            evidenceMass = evidenceMass,
            successWeight = successWeight,
            failureWeight = failureWeight,
            // 聊天通道不喂记忆卡（E 判据白名单）：卡字段原样保留。
            memoryStabilityDays = previous?.memoryStabilityDays,
            memoryDifficulty = previous?.memoryDifficulty,
            lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis,
            lastAttemptStudyDayEpochDay = previous?.lastAttemptStudyDayEpochDay,
            independentCorrectObservations = previous?.independentCorrectObservations.orEmpty(),
            lastIndependentErrorAtEpochMillis = previous?.lastIndependentErrorAtEpochMillis,
            lastIndependentErrorSequence = previous?.lastIndependentErrorSequence,
            status = status,
            calibrationSupport = previous?.calibrationSupport ?: CalibrationSupport.UNKNOWN,
            projectorVersion = VERSION,
            checkpointSequence = event.eventSequence,
            // 证据时钟（审计 AUDIT-ALGORITHM-2026-09-09 §3.5）：模型判断与知识点
            // 复习也是这个 KC 的真实证据，必须刷新 lastEvidenceAt，否则
            // KnowledgeMasteryState 的该字段退化为"最后一次 attempt 通道作答时间"
            // （默认值只从 independentCorrectObservations 推导）——只走 chat 通道的
            // KC 恒为 null，被 ReviewPlanner 判 stale，永远留在复习队列。
            // 方向同理：kcMasteryDropPressure 只在 lastEvidenceDirection 为 NEGATIVE
            // 时传导，不写它则 spec §5 的 KC→错题联动律对模型通道完全失效。
            lastEvidenceAtEpochMillis = maxOf(
                previous?.lastEvidenceAtEpochMillis ?: 0L,
                effectiveAtEpochMillis,
            ),
            lastEvidenceReason = CHAT_EVIDENCE_REASON,
            lastEvidenceDirection = event.direction.name,
        )
    }

    private fun requireCompatibleSnapshot(previous: LearnerSnapshot) {
        require(
            previous.checkpoint.projectorVersion == VERSION ||
                (
                    previous.checkpoint.lastSequence == 0L &&
                        previous.problemMemoryStates.isEmpty() &&
                        previous.knowledgeMasteryStates.isEmpty() &&
                        previous.appliedAttemptRecords.isEmpty() &&
                        previous.appliedCorrectionRecords.isEmpty() &&
                        previous.appliedAnswerRevealRecords.isEmpty() &&
                        previous.appliedTutorAnswerExposureRecords.isEmpty()
                    ),
        ) { "A projector-version change requires replay from an empty snapshot" }
    }

    private fun inferPresentationStates(
        previous: LearnerSnapshot,
        events: List<IncrementalLearningEvent>,
    ): Map<String, PresentationProjectionState> = events
        .mapNotNull { event ->
            when (event) {
                is Attempt -> event.presentationId
                is AnswerRevealOutcome -> event.presentationId
                is TutorAnswerExposureOutcome, is ChatEvidenceSubmitted -> null
            }
        }
        .distinct()
        .associateWith { presentationId ->
            val revealSequence = previous.appliedAnswerRevealRecords.values
                .filter { it.presentationId == presentationId }
                .maxOfOrNull(AppliedAnswerRevealRecord::eventSequence)
            PresentationProjectionState(
                presentationId = presentationId,
                asOfLedgerSequence = previous.checkpoint.lastSequence,
                memoryProjectionApplied = revealSequence != null || previous.appliedAttemptRecords.values.any {
                    it.presentationId == presentationId
                },
                answerRevealSequence = revealSequence,
            )
        }

    private fun applyRevealCausality(
        attempt: Attempt,
        answerRevealSequence: Long?,
        causalSequence: Long,
    ): Attempt {
        if (answerRevealSequence == null || answerRevealSequence >= causalSequence) return attempt
        return when (attempt.evidence.direction) {
            LearningEvidenceDirection.POSITIVE -> attempt.copy(
                evidence = LearningEvidence(
                    direction = LearningEvidenceDirection.NONE,
                    weight = 0.0,
                    reason = LearningEvidenceReason.ANSWER_REVEALED,
                ),
                problemMemoryOutcome = ProblemMemoryOutcome.ANSWER_REVEALED,
            )
            LearningEvidenceDirection.NEGATIVE -> attempt.copy(
                evidence = LearningEvidence(
                    direction = LearningEvidenceDirection.NEGATIVE,
                    weight = attempt.evidence.weight,
                    reason = LearningEvidenceReason.INCORRECT_AFTER_REVEAL,
                ),
                problemMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
            )
            LearningEvidenceDirection.NONE -> attempt
        }
    }

    private fun unchangedConflict(
        previous: LearnerSnapshot,
        ignored: Set<String>,
        conflicts: Set<String>,
        deferred: Set<String>,
        incomingAttemptIds: Set<String>,
        incomingRevealIds: Set<String>,
        incomingTutorExposureIds: Set<String>,
        knownLedgerHeadSequence: Long,
        presentationProjectionStates: Map<String, PresentationProjectionState>,
    ): LearningProjectionResult {
        val snapshot = previous.copy(
            freshness = LearnerSnapshotFreshness.STALE,
            projectionStatus = ProjectionStatus.CONFLICTED,
            knownLedgerHeadSequence = knownLedgerHeadSequence,
        )
        return LearningProjectionResult(
            snapshot = snapshot,
            appliedAttemptIds = emptySet(),
            ignoredAttemptIds = ignored intersect incomingAttemptIds,
            conflictedAttemptIds = conflicts intersect incomingAttemptIds,
            deferredAttemptIds = deferred intersect incomingAttemptIds,
            ignoredAnswerRevealOutcomeIds = ignored intersect incomingRevealIds,
            conflictedAnswerRevealOutcomeIds = conflicts intersect incomingRevealIds,
            deferredAnswerRevealOutcomeIds = deferred intersect incomingRevealIds,
            ignoredTutorAnswerExposureOutcomeIds = ignored intersect incomingTutorExposureIds,
            conflictedTutorAnswerExposureOutcomeIds = conflicts intersect incomingTutorExposureIds,
            deferredTutorAnswerExposureOutcomeIds = deferred intersect incomingTutorExposureIds,
            presentationProjectionStates = presentationProjectionStates,
        )
    }

    private fun applyAttempt(
        memoryStates: MutableMap<String, ProblemMemoryState>,
        masteryStates: MutableMap<String, KnowledgeMasteryState>,
        attempt: Attempt,
        ambiguousAttemptIds: MutableSet<String>,
        suppressDuplicateRevealMemory: Boolean = false,
        effectiveAtEpochMillis: Long,
        /**
         * 合并重定向。归因里的节点可能已被合并掉——证据仍挂在原 id 上（历史不可改写），
         * 但掌握度要记到取代它的节点名下，否则那份证据无处可去、存活节点凭空少一块。
         */
        knowledgeNodeSuccessors: KnowledgeNodeSuccessors = KnowledgeNodeSuccessors.EMPTY,
        /** KF-17 错峰序号（仅新建卡消费；见 [SourceBatchDispersion]）。 */
        dispersion: SourceBatchDispersion,
    ) {
        if (!suppressDuplicateRevealMemory) {
            val previousMemory = memoryStates[attempt.practiceUnitId]
            memoryStates[attempt.practiceUnitId] = projectMemory(
                previous = previousMemory,
                practiceUnitId = attempt.practiceUnitId,
                eventId = attempt.attemptId,
                occurredAtEpochMillis = attempt.occurredAtEpochMillis,
                effectiveAtEpochMillis = effectiveAtEpochMillis,
                eventSequence = attempt.eventSequence,
                eventEpochDay = attempt.studyDay.epochDay,
                eventUtcOffsetMinutes = attempt.studyDay.utcOffsetMinutes,
                outcome = attempt.problemMemoryOutcome,
                weight = attempt.evidence.weight,
                evidenceReason = attempt.evidence.reason,
                evidenceDirection = attempt.evidence.direction,
                dueDateOffsetDays = if (previousMemory == null) {
                    dispersion.nextOffsetDays(
                        sourceBundleId = attempt.assessmentSnapshot.sourceBundleId,
                        studyDayEpochDay = attempt.studyDay.epochDay,
                    )
                } else {
                    0
                },
            )
        }
        val attributions = attempt.assessmentSnapshot.attributions
        if (attributions.any { it.certainty == EvidenceAttributionCertainty.AMBIGUOUS }) {
            ambiguousAttemptIds += attempt.attemptId
        }
        if (attempt.evidence.weight == 0.0) return
        attributions
            .filter { it.certainty == EvidenceAttributionCertainty.DIRECT }
            .sortedBy(KnowledgeEvidenceAttribution::bindingId)
            .forEach { attribution ->
                val knowledgeNodeId = knowledgeNodeSuccessors.resolve(attribution.knowledgeNodeId)
                masteryStates[knowledgeNodeId] = projectMastery(
                    previous = masteryStates[knowledgeNodeId],
                    knowledgeNodeId = knowledgeNodeId,
                    attempt = attempt,
                    attribution = attribution,
                    effectiveAtEpochMillis = effectiveAtEpochMillis,
                )
            }
    }

    private fun projectAnswerReveal(
        previous: ProblemMemoryState?,
        outcome: AnswerRevealOutcome,
        effectiveAtEpochMillis: Long,
        /** KF-17：新建卡的错峰天数（同批同 source 的第 2、3… 张分别 +1、+2 天）。 */
        dueDateOffsetDays: Int = 0,
    ): ProblemMemoryState = projectMemory(
        previous = previous,
        practiceUnitId = outcome.practiceUnitId,
        eventId = outcome.outcomeId,
        occurredAtEpochMillis = outcome.occurredAtEpochMillis,
        effectiveAtEpochMillis = effectiveAtEpochMillis,
        eventSequence = outcome.eventSequence,
        eventEpochDay = outcome.studyDay.epochDay,
        eventUtcOffsetMinutes = outcome.studyDay.utcOffsetMinutes,
        outcome = ProblemMemoryOutcome.ANSWER_REVEALED,
        weight = 0.0,
        evidenceReason = LearningEvidenceReason.ANSWER_REVEALED,
        evidenceDirection = LearningEvidenceDirection.NONE,
        dueDateOffsetDays = dueDateOffsetDays,
    )

    /**
     * 裁决 26（2026-10-01）：「看答案」的**知识点归因**——独立 [AnswerRevealOutcome] 事件在
     * 题卡之外，也把该题绑定的（DIRECT）知识点记忆卡按一次遗忘失败（rating = AGAIN）lapse。
     *
     * 消灭的失败：学生看了答案、于是不再作答 → 知识点层面零痕迹（E 判据下等于"看答案 = 该
     * 知识点毫无代价"，正是裁决 1 想消灭、却被判据换轴在知识点层重新打开的洞）。
     *
     * 边界（与既有两条路径的关系）：
     * - **只动已存在的卡**：没有卡（该 KC 从未有作答流）就不建卡——"lapse 记忆卡"以卡存在
     *   为前提，看答案不是作答通道的证据，不改变"卡由作答流创建"的白名单。
     * - **只动记忆卡**（稳定性/难度/上次作答时刻与学习日 + 状态检查点）；s/f、观察列表、
     *   `lastEvidence*`、冲突态不动——裁决 26 原文"本改动只作用于记忆更新"。
     * - 该 lapse 与题卡的 reveal 投影共用同一道呈现态门（调用点），因此一次呈现最多贡献
     *   一次 reveal 记忆更新；其后的作答仍走既有因果路径（答对 → 不计分；答错 →
     *   `INCORRECT_AFTER_REVEAL` 一次），两条路径各自记账、不重复计罚。
     * - kill-switch 的 legacy 模型下没有卡（与 `projectMastery` 同：字段恒 null）。
     * - 归因按 `bindingId` 排序（与 `applyAttempt` 同序），重放/增量逐位一致。
     */
    private fun lapseKnowledgeCardsForReveal(
        masteryStates: MutableMap<String, KnowledgeMasteryState>,
        outcome: AnswerRevealOutcome,
        effectiveAtEpochMillis: Long,
        knowledgeNodeSuccessors: KnowledgeNodeSuccessors,
    ) {
        outcome.assessmentSnapshot.attributions
            .filter { it.certainty == EvidenceAttributionCertainty.DIRECT }
            .sortedBy(KnowledgeEvidenceAttribution::bindingId)
            .forEach { attribution ->
                val knowledgeNodeId = knowledgeNodeSuccessors.resolve(attribution.knowledgeNodeId)
                val previous = masteryStates[knowledgeNodeId]
                // "lapse 记忆卡"以卡存在为前提：没有卡就没有可下调的记忆状态。卡由**作答流**
                // 创建（W3-2 白名单）；看答案不是作答，不新建卡——它只让已存在的卡付代价。
                if (previous?.memoryStabilityDays == null) return@forEach
                masteryStates[knowledgeNodeId] = lapseKnowledgeCard(
                    previous = previous,
                    studyDayEpochDay = outcome.studyDay.epochDay,
                    effectiveAtEpochMillis = effectiveAtEpochMillis,
                    eventSequence = outcome.eventSequence,
                )
            }
    }

    private fun lapseKnowledgeCard(
        previous: KnowledgeMasteryState,
        studyDayEpochDay: Long,
        effectiveAtEpochMillis: Long,
        eventSequence: Long,
    ): KnowledgeMasteryState {
        val memoryCardUpdate = (memoryUpdateModel as? FsrsMemoryUpdateModel)?.nextMemoryState(
            previousStabilityDays = previous.memoryStabilityDays,
            previousDifficulty = previous.memoryDifficulty,
            // 看答案 = 一次遗忘失败（裁决 1 的评级口径，与题卡 reveal 同一映射源）。
            rating = FsrsEvidenceRatingMapper.schedulingRatingFor(
                LearningEvidenceReason.ANSWER_REVEALED,
                weight = 0.0,
            ),
            elapsedCalendarDays = (studyDayEpochDay - (previous.lastAttemptStudyDayEpochDay ?: studyDayEpochDay))
                .toDouble()
                .coerceAtLeast(0.0),
        )
        return previous.copy(
            memoryStabilityDays = memoryCardUpdate?.first ?: previous.memoryStabilityDays,
            memoryDifficulty = memoryCardUpdate?.second ?: previous.memoryDifficulty,
            lastAttemptAtEpochMillis = if (memoryCardUpdate != null) {
                effectiveAtEpochMillis
            } else {
                previous.lastAttemptAtEpochMillis
            },
            lastAttemptStudyDayEpochDay = if (memoryCardUpdate != null) {
                studyDayEpochDay
            } else {
                previous.lastAttemptStudyDayEpochDay
            },
            projectorVersion = VERSION,
            checkpointSequence = eventSequence,
        )
    }

    private fun projectTutorAnswerExposure(
        previous: ProblemMemoryState?,
        outcome: TutorAnswerExposureOutcome,
        effectiveAtEpochMillis: Long,
    ): ProblemMemoryState {
        // Spec §2.5/§2.6 merge: a tutor answer exposure never decays memory a
        // second time. It only refreshes the clock (spec "S'=S 仅刷新时钟"),
        // so an attempt that follows the exposure is measured from the
        // exposure and lands on the conservative same-day branch.
        //
        // 本路径不传 `lastReviewedEpochDay`：`TutorAnswerExposureOutcome` 不带 studyDay
        // （它没有时区信息），该字段对曝光态只是占位，**不参与任何计算**——后续复习的
        // 本地日由 [projectMemory] 从 `lastReviewedAtEpochMillis` + 该事件的 UTC 偏移现算。
        // 见 `LearningProjectorTest.a review after an answer exposure stays on its own
        // learner-local day`（审计 AUDIT-ALGORITHM §3.7）。
        val refreshedAt = maxOf(previous?.lastReviewedAtEpochMillis ?: 0, effectiveAtEpochMillis)
        val stability = previous?.stabilityDays
            ?: FsrsScheduleMath.initialStability(FsrsRating.AGAIN)
        val difficulty = previous?.difficulty
            ?: FsrsScheduleMath.clampDifficulty(FsrsScheduleMath.initialDifficulty(FsrsRating.AGAIN))
        return ProblemMemoryState(
            practiceUnitId = outcome.practiceUnitId,
            stabilityDays = stability,
            difficulty = difficulty,
            lastReviewedAtEpochMillis = refreshedAt,
            nextReviewAtEpochMillis = forgettingCurve.reviewAtTargetRetention(refreshedAt, stability),
            independentCorrectCount = previous?.independentCorrectCount ?: 0,
            assistedCorrectCount = previous?.assistedCorrectCount ?: 0,
            lapseCount = previous?.lapseCount ?: 0,
            // P5（审计 2026-09-28，批次 3 收口）：每条曝光都 +1，与记忆路径的
            // per-reveal 计数（`projectMemory` 的 ANSWER_REVEALED 分支）对称——
            // 此前写成 `+ if (previous == null) 1 else 0`，计数永远冻结在 1。
            answerRevealCount = (previous?.answerRevealCount ?: 0) + 1,
            lastLapseAtEpochMillis = previous?.lastLapseAtEpochMillis,
            clockAnomalyCount = previous?.clockAnomalyCount ?: 0,
            lastClockAnomalyAtEpochMillis = previous?.lastClockAnomalyAtEpochMillis,
            lastAttemptId = outcome.outcomeId,
            projectorVersion = VERSION,
            checkpointSequence = outcome.eventSequence,
            lastEvidenceReason = TUTOR_EXPOSURE_REASON,
            lastEvidenceDirection = LearningEvidenceDirection.NONE.name,
            consecutiveCrossDaySuccess = previous?.consecutiveCrossDaySuccess ?: 0,
            consecutiveCrossDayAgain = previous?.consecutiveCrossDayAgain ?: 0,
        )
    }

    private fun projectMemory(
        previous: ProblemMemoryState?,
        practiceUnitId: String,
        eventId: String,
        occurredAtEpochMillis: Long,
        effectiveAtEpochMillis: Long,
        eventSequence: Long,
        /** Learner-local calendar day (epoch day) of this review event. */
        eventEpochDay: Long,
        /** UTC offset (minutes) of this event's learner-local study day. */
        eventUtcOffsetMinutes: Int,
        outcome: ProblemMemoryOutcome,
        weight: Double,
        evidenceReason: LearningEvidenceReason,
        evidenceDirection: LearningEvidenceDirection,
        /**
         * KF-17（裁决 8）错峰天数：**仅新建卡**由调用方给出（同批同 source 的第 2、3… 张
         * 分别 +1、+2 天），加在到期日上；不动 FSRS 参数、不改稳定性/难度。已有卡的复习
         * 传 0（错峰是一次创建期后处理，第一次复习后回到自身间隔）。
         */
        dueDateOffsetDays: Int = 0,
    ): ProblemMemoryState {
        require(effectiveAtEpochMillis >= occurredAtEpochMillis) {
            "Effective projection time must not precede the raw event time"
        }
        val clockRollback = occurredAtEpochMillis < effectiveAtEpochMillis
        val effectiveAttemptAt = maxOf(previous?.lastReviewedAtEpochMillis ?: 0, effectiveAtEpochMillis)
        val rating = FsrsEvidenceRatingMapper.schedulingRatingFor(evidenceReason, weight)
        // Calendar-day delta (spec §2.1/§2.15): a review crossing the learner-local midnight is a
        // new study day even when it is under 24 wall-clock hours from the previous review.
        //
        // 上一复习的本地日**由它的时间戳现算**，不读 `previous.lastReviewedEpochDay`：那是派生态，
        // 任何一条没有显式写它的通道都会把该字段的默认值（UTC 日序）留在状态里，从而污染
        // 跨日判定——`projectTutorAnswerExposure` 正是如此（审计 AUDIT-ALGORITHM §3.7，
        // `LearningProjectorTest.a review after an answer exposure stays on its own learner-local day`
        // 锁定该失败）。本事件自带 studyDay（含 utcOffsetMinutes），据此换算得到的是确定值：
        // 重放读同一事件，结果逐位相同，不依赖任何写入方是否记得盖章。
        // W1-6/P1：换算函数单源在 `StudyDayMath`（与写路径盖进账本的日序、review_log 的 delta_t
        // 同一实现），本文件不再保留私有副本。
        val previousEpochDay = previous?.let {
            StudyDayMath.localEpochDayOf(it.lastReviewedAtEpochMillis, eventUtcOffsetMinutes)
        } ?: eventEpochDay
        val elapsedCalendarDays = (eventEpochDay - previousEpochDay)
            .toDouble().coerceAtLeast(0.0)
        val update = memoryUpdateModel.updateMemory(
            previous = previous,
            rating = rating,
            outcome = outcome,
            weight = weight,
            occurredAtEpochMillis = occurredAtEpochMillis,
            effectiveAttemptAtEpochMillis = effectiveAttemptAt,
            elapsedCalendarDays = elapsedCalendarDays,
        )
        var stability = update.stabilityDays
        var difficulty = update.difficulty
        val crossDay = previous != null && elapsedCalendarDays >= 1.0
        val nextCrossDaySuccess = when {
            // P5（审计 2026-09-28，批次 3 收口）：首答**不计**跨日连击——"跨日"指跨过
            // 上一复习日，首答没有可跨的上一日。此前首答计 1，使毕业连击实际 2 次真实
            // 跨日成功即触发（`GRADUATION_SUCCESS_STREAK` 语义被悄悄放水）。
            previous == null -> 0
            crossDay && rating != FsrsRating.AGAIN -> previous.consecutiveCrossDaySuccess + 1
            // Spec §2.10: the streak counts *consecutive* cross-day successes —
            // a cross-day lapse breaks the run. Without this reset the stale
            // pre-lapse count would combine with post-lapse successes into a
            // fake "three in a row" graduation trigger.
            crossDay && rating == FsrsRating.AGAIN -> 0
            else -> previous.consecutiveCrossDaySuccess
        }
        val nextCrossDayAgain = when {
            previous == null -> if (rating == FsrsRating.AGAIN) 1 else 0
            crossDay && rating == FsrsRating.AGAIN -> previous.consecutiveCrossDayAgain + 1
            crossDay && rating != FsrsRating.AGAIN -> 0
            else -> previous.consecutiveCrossDayAgain
        }
        if (previous?.isLeeched == true && difficulty > previous.difficulty) {
            // Spec §2.16: while leeched, difficulty must not climb further.
            difficulty = previous.difficulty
        }
        var nextReviewAt = update.nextReviewAtEpochMillis
        val isRetrievalFailure = outcome == ProblemMemoryOutcome.RETRIEVAL_FAILURE ||
            outcome == ProblemMemoryOutcome.ANSWER_REVEALED
        if (
            memoryUpdateModel is FsrsMemoryUpdateModel &&
            nextCrossDaySuccess >= ProblemMemoryState.GRADUATION_SUCCESS_STREAK &&
            !isRetrievalFailure
        ) {
            // Spec §2.10 graduation: once the streak is long enough and the
            // regular interval reaches ninety days, schedule maintenance at a
            // lower target retention instead.
            // W2-1/KF-01：间隔反函数用**当前模型**的 decay（个性化 w20），不再隐式默认。
            val regularIntervalDays = FsrsScheduleMath.intervalDays(
                stability,
                memoryUpdateModel.desiredRetention,
                memoryUpdateModel.decay,
            )
            if (regularIntervalDays >= GRADUATION_MIN_INTERVAL_DAYS) {
                nextReviewAt = forgettingCurve.reviewAtTargetRetention(
                    effectiveAttemptAt,
                    stability,
                    GRADUATION_TARGET_RETENTION,
                )
            }
        }
        if (dueDateOffsetDays > 0) {
            // KF-17：错峰只加在**到期日**上（FSRS 参数、稳定性、难度一字不动）。
            nextReviewAt += dueDateOffsetDays * AlgorithmConstants.DAY_MILLIS
        }

        return ProblemMemoryState(
            practiceUnitId = practiceUnitId,
            stabilityDays = stability,
            difficulty = difficulty,
            lastReviewedAtEpochMillis = effectiveAttemptAt,
            lastReviewedEpochDay = eventEpochDay,
            nextReviewAtEpochMillis = nextReviewAt,
            independentCorrectCount = (previous?.independentCorrectCount ?: 0) +
                if (outcome == ProblemMemoryOutcome.INDEPENDENT_RECALL) 1 else 0,
            assistedCorrectCount = (previous?.assistedCorrectCount ?: 0) +
                if (outcome == ProblemMemoryOutcome.ASSISTED_RECALL) 1 else 0,
            lapseCount = (previous?.lapseCount ?: 0) + if (isRetrievalFailure) 1 else 0,
            answerRevealCount = (previous?.answerRevealCount ?: 0) +
                if (outcome == ProblemMemoryOutcome.ANSWER_REVEALED) 1 else 0,
            lastLapseAtEpochMillis = if (isRetrievalFailure) effectiveAttemptAt else previous?.lastLapseAtEpochMillis,
            clockAnomalyCount = (previous?.clockAnomalyCount ?: 0) + if (clockRollback) 1 else 0,
            lastClockAnomalyAtEpochMillis = if (clockRollback) {
                occurredAtEpochMillis
            } else {
                previous?.lastClockAnomalyAtEpochMillis
            },
            lastAttemptId = eventId,
            projectorVersion = VERSION,
            checkpointSequence = eventSequence,
            lastEvidenceReason = evidenceReason.name,
            lastEvidenceDirection = evidenceDirection.name,
            consecutiveCrossDaySuccess = nextCrossDaySuccess,
            consecutiveCrossDayAgain = nextCrossDayAgain,
        )
    }

    /**
     * KF-17 Disperse siblings（裁决 8，台账 W4-1 勘定后落投影侧）：**同批/source** 的卡不再
     * 全在同一天到期。
     *
     * 口径（"按序 +1 天错峰"，不动 FSRS 参数）：
     * - 键 = (`sourceBundleId`, 学习日)：同一来源、同一学习日的兄弟姐妹归一组；
     * - 组内按事件投影顺序取序号 0、1、2…，作为**新建卡**的到期日 +N 天偏移；
     * - 已有卡的每次复习不重复错峰（错峰是一次创建期后处理），单个题（组内只有它自己）序号 0，
     *   行为逐位不变；
     * - 序号只在**本次投影/重放调用内**累计——"同批"就是一次把这一簇事件一起投影的调用。
     *   跨调用（不同的 drain 批）不继承序号：同一天、同来源的事件若被拆进两次 drain，
     *   第二次的卡回到序号 0。全量重放把整个账本当一次调用，得到的是这一簇的规范分配，
     *   `PROJECTOR` bump 触发的重放即按此安装。
     *
     * 为什么不是持久化字段：错峰只影响创建时的到期日，后续复习从自身间隔重算；把它固化进
     * `ProblemMemoryState`/Room schema 是一次纯为批边界服务的 schema 变更，收益不抵面。
     */
    private class SourceBatchDispersion {
        private val nextOrdinals = mutableMapOf<DispersionKey, Int>()

        /** 该 (来源, 学习日) 组的下一个序号；来源缺失（本地自建题）恒 0，不参与错峰。 */
        fun nextOffsetDays(sourceBundleId: String?, studyDayEpochDay: Long): Int {
            if (sourceBundleId == null) return 0
            val key = DispersionKey(sourceBundleId, studyDayEpochDay)
            val ordinal = nextOrdinals.getOrDefault(key, 0)
            nextOrdinals[key] = ordinal + 1
            return ordinal
        }

        private data class DispersionKey(
            val sourceBundleId: String,
            val studyDayEpochDay: Long,
        )
    }

    private fun projectMastery(
        previous: KnowledgeMasteryState?,
        knowledgeNodeId: String,
        attempt: Attempt,
        attribution: KnowledgeEvidenceAttribution,
        effectiveAtEpochMillis: Long,
    ): KnowledgeMasteryState {
        // Spec §2.13: every bound KC receives the full evidence record
        // (multi-skill all-record). Binding strength/ordering stays in the
        // attribution snapshot for presentation; it no longer splits evidence.
        val weight = attempt.evidence.weight
        val positive = attempt.evidence.signedWeight > 0
        // W3-1/KF-09：β-二项 s/f 权重累加 + Jeffreys 点估计（替换固定增益 EMA）。
        val successWeight = (previous?.successWeight ?: 0.0) + if (positive) weight else 0.0
        val failureWeight = (previous?.failureWeight ?: 0.0) + if (positive) 0.0 else weight
        val updatedProbability = MasteryEstimateMath.pointEstimate(successWeight, failureWeight)
        val evidenceMass = (previous?.evidenceMass ?: 0.0) + weight
        val lowerBound = MasteryEstimateMath.lowerBound(successWeight, failureWeight)
        // 知识点记忆卡（W3-2/E 判据，台账「裁决 13 · 修订」）：与逐题共用同一套 FSRS 更新
        // （`nextMemoryState`），吃该知识点的作答流。白名单 = 作答通道（本函数即作答通道；
        // 聊天自述走 `projectChatEvidence`，不在此列）；评级沿用调度映射（看答案 = 一次遗忘
        // 失败，与裁决 1 一致）。kill-switch 的 legacy 模型下没有卡（字段恒 null）。
        val memoryCardUpdate = (memoryUpdateModel as? FsrsMemoryUpdateModel)?.nextMemoryState(
            previousStabilityDays = previous?.memoryStabilityDays,
            previousDifficulty = previous?.memoryDifficulty,
            rating = FsrsEvidenceRatingMapper.schedulingRatingFor(attempt.evidence.reason, weight),
            elapsedCalendarDays = if (previous?.memoryStabilityDays == null) {
                0.0
            } else {
                (attempt.studyDayEpochDay - (previous.lastAttemptStudyDayEpochDay ?: attempt.studyDayEpochDay))
                    .toDouble()
                    .coerceAtLeast(0.0)
            },
        )
        val nextMemoryStability = memoryCardUpdate?.first ?: previous?.memoryStabilityDays
        val nextMemoryDifficulty = memoryCardUpdate?.second ?: previous?.memoryDifficulty
        val nextLastAttemptAt =
            if (memoryCardUpdate != null) effectiveAtEpochMillis else previous?.lastAttemptAtEpochMillis
        val nextLastAttemptStudyDay =
            if (memoryCardUpdate != null) attempt.studyDayEpochDay else previous?.lastAttemptStudyDayEpochDay
        val independentError = !positive && attempt.evidence.isIndependent
        val lastErrorAt = if (independentError) {
            maxOf(previous?.lastIndependentErrorAtEpochMillis ?: 0, effectiveAtEpochMillis)
        } else {
            previous?.lastIndependentErrorAtEpochMillis
        }
        val lastErrorSequence = if (independentError) {
            maxOf(previous?.lastIndependentErrorSequence ?: 0, attempt.eventSequence)
        } else {
            previous?.lastIndependentErrorSequence
        }
        // E 判据（W3-2）：撤销的门 = "当时是否在掌握态"——状态标记或记忆卡达标（同一函数）。
        // 冲突判据只依赖 previous/本次证据，不依赖观察列表，因此先算出来，供下面单遍聚合使用。
        val startsConflict = independentError && previous?.let {
            it.status == MasteryStatus.MASTERED ||
                clearlyMasteredByMemoryCard(
                    it.memoryStabilityDays,
                    it.lastAttemptAtEpochMillis,
                    effectiveAtEpochMillis,
                )
        } == true
        val conflictSince = when {
            startsConflict -> attempt.eventSequence
            previous?.status == MasteryStatus.CONFLICTED && independentError -> attempt.eventSequence
            previous?.status == MasteryStatus.CONFLICTED -> previous.conflictSinceSequence
                ?: previous.lastIndependentErrorSequence
            else -> null
        }
        val newObservation = if (positive && attempt.evidence.isIndependent) {
            IndependentCorrectObservation(
                itemFamilyId = attempt.itemFamilyId,
                studyDayEpochDay = attempt.studyDayEpochDay,
                occurredAtEpochMillis = effectiveAtEpochMillis,
                eventSequence = attempt.eventSequence,
                bindingId = attribution.bindingId,
                evidenceWeight = weight,
                calibration = attempt.assessmentSnapshot.calibration,
                timeTrust = if (effectiveAtEpochMillis == attempt.occurredAtEpochMillis) {
                    EventTimeTrust.TRUSTED
                } else {
                    // P11：`FUTURE_TIMESTAMP_CLAMPED` 分支已删——不可达可证：
                    // 事件循环里 `effectiveAt = maxOf(projectedAt, occurredAt)`，
                    // effective 永不早于 occurred，`else` 分支在任何输入下取不到。
                    // 枚举值保留为历史持久化 token（core:model 有注明）。
                    EventTimeTrust.CLOCK_ROLLBACK_CLAMPED
                },
            )
        } else {
            null
        }
        // S3（投影热路径）：原实现每作答一次都先全量拷贝观察列表，再对同一列表扫
        // 3–5 遍（supportedRecovery / activeObservations / any UNSUPPORTED）。
        // 这里改为：无新增时**共享原列表**（不再全拷贝），并把"有无受支持观察""有无不受支持
        // 观察""冲突恢复集"合成**一次遍历**同时产出——逐位等价（比较/累加顺序都不变，
        // 浮点加法顺序也相同）。
        val previousObservations = previous?.independentCorrectObservations.orEmpty()
        val observations = if (newObservation == null) {
            previousObservations
        } else {
            previousObservations + newObservation
        }
        var hasSupportedObservation = false
        var hasUnsupportedObservation = false
        var supportedRecoveryWeight = 0.0
        val conflictFloor: Long? = conflictSince
        val supportedRecovery = if (conflictFloor == null) {
            null
        } else {
            mutableListOf<IndependentCorrectObservation>()
        }
        // 仅用于比较的门槛值；`conflictFloor == null` 时该分支取不到（列表恒为 null）。
        val recoveryFloor = conflictFloor ?: 0L
        observations.forEach { candidate ->
            when (candidate.calibrationSupportAt(effectiveAtEpochMillis)) {
                CalibrationSupport.SUPPORTED -> {
                    hasSupportedObservation = true
                    if (supportedRecovery != null &&
                        candidate.eventSequence > recoveryFloor &&
                        candidate.isStudyDayTrusted
                    ) {
                        supportedRecovery += candidate
                        supportedRecoveryWeight += candidate.evidenceWeight
                    }
                }
                CalibrationSupport.UNSUPPORTED -> hasUnsupportedObservation = true
                CalibrationSupport.UNKNOWN -> Unit
            }
        }
        val conflictRecovered = supportedRecovery != null &&
            supportedRecoveryWeight >= ClearlyMasteredForSkipPolicy.EVIDENCE_MASS &&
            ClearlyMasteredForSkipPolicy.hasIndependentBreadth(
                observations = supportedRecovery,
                lastIndependentErrorAtEpochMillis = null,
                lastIndependentErrorSequence = null,
                atEpochMillis = effectiveAtEpochMillis,
            )
        val calibration = when {
            hasSupportedObservation -> CalibrationSupport.SUPPORTED
            hasUnsupportedObservation -> CalibrationSupport.UNSUPPORTED
            else -> CalibrationSupport.UNKNOWN
        }
        val status = when {
            conflictSince != null && !conflictRecovered -> MasteryStatus.CONFLICTED
            evidenceMass < 1.0 -> MasteryStatus.UNKNOWN
            clearlyMasteredByMemoryCard(
                nextMemoryStability,
                nextLastAttemptAt,
                effectiveAtEpochMillis,
            ) -> MasteryStatus.MASTERED
            else -> MasteryStatus.LEARNING
        }
        return KnowledgeMasteryState(
            knowledgeNodeId = knowledgeNodeId,
            masteryScore = updatedProbability,
            conservativeMasteryScore = lowerBound,
            evidenceMass = evidenceMass,
            successWeight = successWeight,
            failureWeight = failureWeight,
            memoryStabilityDays = nextMemoryStability,
            memoryDifficulty = nextMemoryDifficulty,
            lastAttemptAtEpochMillis = nextLastAttemptAt,
            lastAttemptStudyDayEpochDay = nextLastAttemptStudyDay,
            independentCorrectObservations = observations,
            lastIndependentErrorAtEpochMillis = lastErrorAt,
            lastIndependentErrorSequence = lastErrorSequence,
            status = status,
            calibrationSupport = calibration,
            projectorVersion = VERSION,
            checkpointSequence = attempt.eventSequence,
            lastEvidenceAtEpochMillis = maxOf(
                previous?.lastEvidenceAtEpochMillis ?: 0,
                effectiveAtEpochMillis,
            ),
            conflictSinceSequence = if (conflictRecovered) null else conflictSince,
            lastEvidenceReason = attempt.evidence.reason.name,
            lastEvidenceDirection = attempt.evidence.direction.name,
        )
    }

    /**
     * E 判据的门（W3-2，台账「裁决 13 · 修订」）：知识点记忆卡达到耐久门且**当前**召回概率
     * 仍达标。定义只有一处——[ClearlyMasteredForSkipPolicy.meetsMemoryCriterion]（投影与读取共用）。
     */
    private fun clearlyMasteredByMemoryCard(
        memoryStabilityDays: Double?,
        lastAttemptAtEpochMillis: Long?,
        atEpochMillis: Long,
    ): Boolean = ClearlyMasteredForSkipPolicy.meetsMemoryCriterion(
        memoryStabilityDays = memoryStabilityDays,
        lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
        atEpochMillis = atEpochMillis,
        decay = memoryCardDecay(),
    )

    /**
     * 记忆卡判定用的衰减：FSRS 模型取它自己的 `-w20`（个性化参数）；kill-switch 的 legacy
     * 模型下没有卡（字段恒 null → 判据恒 false），取默认只为让公式有定义（随 KF-11 删除）。
     */
    private fun memoryCardDecay(): Double =
        (memoryUpdateModel as? FsrsMemoryUpdateModel)?.decay
            ?: -FsrsScheduleMath.DEFAULT_PARAMETERS[20]

    /**
     * Bounding invariant for the applied-record audit windows, shared by all
     * four record kinds so the window cannot diverge between them: keep the
     * newest [MAX_APPLIED_RECORDS] rows by event sequence and re-order
     * ascending.
     */
    private fun <T> boundedRecords(
        records: Map<String, T>,
        eventSequence: (T) -> Long,
        id: (T) -> String,
    ): Map<String, T> = records.values
        .sortedByDescending(eventSequence)
        .take(MAX_APPLIED_RECORDS)
        .sortedBy(eventSequence)
        .associateBy(id)

    // P11（2026-09-30）：`generatePredictions` 影子预测与特征指纹已删除——它的输出
    // （`LearningProjectionResult.predictions`）在生产与测试里都没有消费方，而"预测审计轨"
    // 的语义修正（批终态 vs 预测时刻）按审计归属 3C 的独立工作线上处理
    // （`RoomPredictionAuditSink` / `StudySchedulingCalibration.readResolvedStudentModelPredictions`
    // 那条链保持原样，不在本波）。

    // W1-6/P1：`localEpochDayOf` 私有副本已删除——日序换算单源在 `StudyDayMath`（同算术、同口径，
    // 原有 KDoc 的理由记录在 `StudyDayMath` 文件头）。删除即编译错，保证没有第三份换算再长出来。

    companion object {
        const val VERSION = LearningCoreVersions.PROJECTION_COMPOSITE

        private const val MAX_APPLIED_RECORDS = 4_096
        /** W0-4：日长单源在 `AlgorithmConstants.DAY_MILLIS`。 */
        private val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS
        private const val GRADUATION_MIN_INTERVAL_DAYS = 90
        const val GRADUATION_TARGET_RETENTION = 0.8
        const val TUTOR_EXPOSURE_REASON = "TUTOR_EXPOSURE"

        /**
         * `last_evidence_reason` 落库值，用于 chat 证据（模型判断 / 知识点复习）。
         *
         * 保持粗粒度是刻意的：精确来源（MODEL_CHAT vs KNOWLEDGE_QUIZ、哪次会话）
         * 由账本行自己的 `source_kind` 与 `conversation_id` 承载，投影层不再复制
         * 一份可能漂移的副本——这里只需要回答"最近一条证据是不是讲题/复习判断"。
         */
        const val CHAT_EVIDENCE_REASON = "CHAT_EVIDENCE"
        const val DEFAULT_DESIRED_RETENTION = FsrsMemoryUpdateModel.DEFAULT_DESIRED_RETENTION
    }
}
