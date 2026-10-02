package com.tingyun.smartmistakebook.core.database.dao

import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.entity.AnswerRevealOutcomeEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedAnswerRevealRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedAttemptRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedCorrectionRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AppliedTutorAnswerExposureRecordEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceAttributionEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentEvidenceSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.AttemptCorrectionEntity
import com.tingyun.smartmistakebook.core.database.entity.AttemptEventEntity
import com.tingyun.smartmistakebook.core.database.entity.BindingChangeEventEntity
import com.tingyun.smartmistakebook.core.database.entity.IndependentCorrectObservationEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerKnowledgeMasteryStateEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerProblemMemoryStateEntity
import com.tingyun.smartmistakebook.core.database.entity.LearnerProjectionSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.PresentationProjectionStateEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionArchiveEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionOutboxEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorAnswerExposureOutcomeEntity
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W4-3 复核阻断项（S5 并发语义）的回归测试。
 *
 * 复核发现：分块版第一实现把尾部完整性判定用的 `last_allocated_sequence` 放到**事务外**单读。
 * 并发写入者真实存在且不与 study 仓库共享互斥（`ChatEvidenceDao` 在单个事务里追加 outbox+序列，
 * 经 tutor 工具环调用），当它在"最后一块读之后、尾部头读之前"提交时，`allocated > expected - 1`
 * 而那一行其实存在——假 GAP 会让 `StudyProjectionDrainer` 抛 `LearningLedgerIntegrityException`。
 * 旧实现（整本行 + 分配头同一事务）观察不到这个中间态。
 *
 * 修复：分配头与块行读自**同一事务快照**（`readLedgerChunk` 内读）。本测试用一个只实现账本读
 * 路径的 DAO 子类，在"块事务结束之后"注入一次追写（即真实写入者的提交窗口），钉住：
 * - 追写落在最后一块之后 → 仍是 COMPLETE（前缀停在旧头），不再假 GAP；
 * - 追写落在块与块之间 → 下一块读到它，前缀补齐；
 * - 真洞（分配头覆盖的序列确实无行）→ 仍判 GAP，且 blockedAtSequence 指第一个缺失序号；
 * - 载荷与指纹不符 → 仍判 CONFLICT。
 *
 * 旧实现在第一条上会返回 GAP（尾部事务外单读会看到前进的分配头）——本文件即是那条断言的守卫。
 */
class LearningLedgerChunkReadTest {

    @Test
    fun `an append committed after the last chunk read is not reported as a gap`() = runBlocking {
        val dao = RacingLedgerDao()
        repeat(3) { dao.appendAttempt(attempt(it + 1L)) }
        // 块事务结束后的提交窗口：行与分配头都前进，但都不在已读的块里。
        dao.afterChunk = { dao.appendAttempt(attempt(4)) }

        val ledger = dao.loadLearningLedger(LEARNER)

        assertEquals(LearningLedgerReadStatus.COMPLETE, ledger.status)
        assertEquals(listOf(1L, 2L, 3L), ledger.validPrefix.map { it.event.eventSequence })
        assertEquals(4L, dao.allocatedSequence)
    }

    @Test
    fun `an append committed between chunks is read by the next chunk instead of becoming a gap`() =
        runBlocking {
            val dao = RacingLedgerDao()
            repeat(900) { dao.appendAttempt(attempt(it + 1L)) }
            dao.afterChunk = {
                dao.afterChunk = null
                dao.appendAttempt(attempt(901))
            }

            val ledger = dao.loadLearningLedger(LEARNER)

            assertEquals(LearningLedgerReadStatus.COMPLETE, ledger.status)
            assertEquals((1L..901L).toList(), ledger.validPrefix.map { it.event.eventSequence })
        }

    @Test
    fun `a real hole is still reported as a gap at the first missing sequence`() = runBlocking {
        val dao = RacingLedgerDao()
        dao.appendAttempt(attempt(1))
        dao.appendAttempt(attempt(2))
        // 真洞：分配头覆盖 1..4，但 3、4 没有账本行。
        dao.advanceAllocatedSequence(4L)

        val ledger = dao.loadLearningLedger(LEARNER)

        assertEquals(LearningLedgerReadStatus.GAP, ledger.status)
        assertEquals(3L, ledger.blockedAtSequence)
        assertEquals(listOf(1L, 2L), ledger.validPrefix.map { it.event.eventSequence })
    }

    @Test
    fun `a payload that no longer matches its fingerprint is still reported as a conflict`() =
        runBlocking {
            val dao = RacingLedgerDao()
            dao.appendAttempt(attempt(1))
            dao.tamperFingerprint("attempt-1")

            val ledger = dao.loadLearningLedger(LEARNER)

            assertEquals(LearningLedgerReadStatus.CONFLICT, ledger.status)
            assertEquals(1L, ledger.blockedAtSequence)
        }

    /**
     * KF-32：新增的 `BINDING_CHANGED` kind 走同一套身份三连 + 载荷指纹校验——载荷行、outbox 行
     * 与规范指纹三者一致时读得回来（重放据此拿到"该改绑过"的凭据）。
     */
    @Test
    fun `a binding change event is read back with its re-derived fingerprint`() = runBlocking {
        val dao = RacingLedgerDao()
        val event = bindingChange(sequence = 1)
        dao.appendBindingChange(event)

        val ledger = dao.loadLearningLedger(LEARNER)

        assertEquals(LearningLedgerReadStatus.COMPLETE, ledger.status)
        assertEquals(event, ledger.validPrefix.single().event)
    }

    /** KF-32：载荷被篡改（指纹不符）→ 仍判 CONFLICT。 */
    @Test
    fun `a tampered binding change payload is still reported as a conflict`() = runBlocking {
        val dao = RacingLedgerDao()
        dao.appendBindingChange(bindingChange(sequence = 1))
        dao.tamperBindingChangeNodes("binding-change-1", before = "kc-old", after = "kc-tampered")

        val ledger = dao.loadLearningLedger(LEARNER)

        assertEquals(LearningLedgerReadStatus.CONFLICT, ledger.status)
        assertEquals(1L, ledger.blockedAtSequence)
    }

    private fun attempt(sequence: Long): Attempt = Attempt(
        attemptId = "attempt-$sequence",
        presentationId = "presentation-$sequence",
        responseOrdinal = 1,
        assessmentSnapshot = snapshot("attempt-$sequence"),
        evidence = LearningEvidence(
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
        ),
        problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + sequence * 1_000L,
        durationSeconds = 45,
        studyDay = StudyDayContext(epochDay = 20_000L, timeZoneId = "Asia/Shanghai", utcOffsetMinutes = 480),
        eventSequence = sequence,
    )

    private fun bindingChange(sequence: Long): BindingChanged = BindingChanged(
        bindingChangeId = "binding-change-$sequence",
        practiceUnitId = "unit-${sequence % 4}",
        previousKnowledgeNodeIds = listOf("kc-old"),
        newKnowledgeNodeIds = listOf("kc-new"),
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + sequence * 1_000L,
        eventSequence = sequence,
    )

    private fun snapshot(id: String): AssessmentEvidenceSnapshot = AssessmentEvidenceSnapshot(        snapshotId = "snapshot-$id",
        assessmentItemId = "assessment-$id",
        practiceUnitId = "unit-${id.length % 4}",
        problemRevisionId = "revision-1",
        answerSpecId = "answer-1",
        itemFamilyId = "family-$id",
        sourceBundleId = "bundle-chunk-read",
        taxonomyVersion = "taxonomy-v1",
        verification = AssessmentSnapshotVerification.VERIFIED,
        calibration = CalibrationSnapshot(
            support = CalibrationSupport.SUPPORTED,
            sourceId = "calibration-source",
            version = "calibration-v1",
            validFromEpochMillis = 0,
            validUntilEpochMillis = Long.MAX_VALUE,
        ),
        attributions = listOf(
            KnowledgeEvidenceAttribution(
                bindingId = "binding-$id",
                knowledgeNodeId = "kc-${id.length % 3}",
                weight = 1.0,
                basisRevisionId = "revision-1",
                taxonomyVersion = "taxonomy-v1",
                role = EvidenceAttributionRole.PRIMARY,
                certainty = EvidenceAttributionCertainty.DIRECT,
            ),
        ),
        capturedAtEpochMillis = 0,
    )

    private companion object {
        const val LEARNER = "learner:ledger-chunk-read"
        const val BASE_EPOCH_MILLIS = 1_767_225_600_000L
    }
}

/**
 * 只实现账本读路径（[ProjectionTransactionDao.loadLearningLedger] 及其块事务）需要的查询；
 * 其余 40 余个成员不属于本测试路径，保持"未实现即失败"以免测试悄悄依赖别的行为。
 */
private abstract class LedgerOnlyProjectionTransactionDao : ProjectionTransactionDao() {
    val outboxRows = mutableListOf<ProjectionOutboxEntity>()
    private val attemptEntities = mutableMapOf<String, AttemptEventEntity>()
    private val snapshotEntities = mutableMapOf<String, AssessmentEvidenceSnapshotEntity>()
    private val attributionEntities = mutableMapOf<String, List<AssessmentEvidenceAttributionEntity>>()
    private val bindingChangeEntities = mutableMapOf<String, BindingChangeEventEntity>()
    var allocatedSequence: Long = 0L
        private set

    /** 模拟一次提交：载荷、outbox 行、分配头一起前进（与写路径同事务的语义一致）。 */
    fun appendAttempt(attempt: Attempt) {
        val fingerprint = LearningLedgerFingerprint.attempt(attempt)
        val snapshot = attempt.assessmentSnapshot
        snapshotEntities[snapshot.snapshotId] = snapshot.toEntity()
        attributionEntities[snapshot.snapshotId] = snapshot.toAttributionEntities()
        attemptEntities[attempt.attemptId] = attempt.toEntity(
            learnerId = LEARNER,
            submissionId = "submission-${attempt.attemptId}",
            canonicalFingerprint = fingerprint,
        )
        outboxRows += ProjectionOutboxEntity(
            outboxId = "outbox-${attempt.attemptId}",
            learnerId = LEARNER,
            outboxSequence = attempt.eventSequence,
            eventKind = EVENT_KIND_ATTEMPT,
            eventId = attempt.attemptId,
            canonicalFingerprint = fingerprint,
            status = StudyDbValue.OutboxStatus.PENDING,
            createdAtEpochMillis = attempt.occurredAtEpochMillis,
        )
        allocatedSequence = maxOf(allocatedSequence, attempt.eventSequence)
    }

    /**
     * KF-32：模拟改绑事件的落库（与 `BindingChangeDao.appendAsLedgerEvent` 同形——载荷行、outbox
     * 行、序列三者一致，指纹按 outbox 序列现算）。
     */
    fun appendBindingChange(event: BindingChanged) {
        val entity = BindingChangeEventEntity(
            bindingChangeId = event.bindingChangeId,
            learnerId = LEARNER,
            practiceUnitId = event.practiceUnitId,
            previousKnowledgeNodeIds = encodeKnowledgeNodeIds(event.previousKnowledgeNodeIds),
            newKnowledgeNodeIds = encodeKnowledgeNodeIds(event.newKnowledgeNodeIds),
            occurredAtEpochMillis = event.occurredAtEpochMillis,
        )
        bindingChangeEntities[event.bindingChangeId] = entity
        outboxRows += ProjectionOutboxEntity(
            outboxId = "outbox-${event.bindingChangeId}",
            learnerId = LEARNER,
            outboxSequence = event.eventSequence,
            eventKind = EVENT_KIND_BINDING_CHANGED,
            eventId = event.bindingChangeId,
            canonicalFingerprint = LearningLedgerFingerprint.bindingChanged(event),
            status = StudyDbValue.OutboxStatus.PENDING,
            createdAtEpochMillis = event.occurredAtEpochMillis,
        )
        allocatedSequence = maxOf(allocatedSequence, event.eventSequence)
    }

    /** 篡改改绑事件的节点集合（载荷不再匹配指纹列）→ 读侧必须判 CONFLICT。 */
    fun tamperBindingChangeNodes(bindingChangeId: String, before: String, after: String) {
        val entity = checkNotNull(bindingChangeEntities[bindingChangeId]) {
            "Unknown binding change $bindingChangeId"
        }
        bindingChangeEntities[bindingChangeId] = entity.copy(
            previousKnowledgeNodeIds = encodeKnowledgeNodeIds(listOf(before)),
            newKnowledgeNodeIds = encodeKnowledgeNodeIds(listOf(after)),
        )
    }

    /** 真洞：只前进分配头，不追加行。 */
    fun advanceAllocatedSequence(sequence: Long) {
        allocatedSequence = maxOf(allocatedSequence, sequence)
    }

    /** 篡改 outbox 行的指纹列：载荷与行不再一致 → 读侧必须判 CONFLICT。 */
    fun tamperFingerprint(attemptId: String) {
        val index = outboxRows.indexOfFirst { it.eventId == attemptId }
        check(index >= 0) { "Unknown attempt $attemptId" }
        outboxRows[index] = outboxRows[index].copy(canonicalFingerprint = "tampered")
    }

    override suspend fun findOutboxAfter(
        learnerId: String,
        afterSequence: Long,
        limit: Int,
    ): List<ProjectionOutboxEntity> = outboxRows
        .filter { it.learnerId == learnerId && it.outboxSequence > afterSequence }
        .sortedBy(ProjectionOutboxEntity::outboxSequence)
        .take(limit)

    override suspend fun lastAllocatedSequence(learnerId: String): Long? = allocatedSequence

    override suspend fun findAttemptsByIds(attemptIds: List<String>): List<AttemptEventEntity> =
        attemptIds.mapNotNull(attemptEntities::get)

    override suspend fun findEvidenceSnapshotsByIds(
        snapshotIds: List<String>,
    ): List<AssessmentEvidenceSnapshotEntity> = snapshotIds.mapNotNull(snapshotEntities::get)

    override suspend fun findAttributionsBySnapshotIds(
        snapshotIds: List<String>,
    ): List<AssessmentEvidenceAttributionEntity> = snapshotIds
        .flatMap { attributionEntities[it].orEmpty() }
        .sortedWith(compareBy({ it.snapshotId }, { it.bindingId }))

    override suspend fun findHeader(
        projectionName: String,
        learnerId: String,
    ): LearnerProjectionSnapshotEntity? = notExercised()

    override suspend fun findOutboxRange(
        learnerId: String,
        afterSequence: Long,
        throughSequence: Long,
    ): List<ProjectionOutboxEntity> = notExercised()

    override suspend fun findAttempt(attemptId: String): AttemptEventEntity? = notExercised()

    override suspend fun findCorrection(correctionId: String): AttemptCorrectionEntity? = notExercised()

    override suspend fun findProjectionAnswerReveal(outcomeId: String): AnswerRevealOutcomeEntity? =
        notExercised()

    override suspend fun findProjectionTutorExposure(
        outcomeId: String,
    ): TutorAnswerExposureOutcomeEntity? = notExercised()

    override suspend fun findChatEvidence(evidenceId: String): LearnerChatEvidenceEntity? =
        notExercised()

    override suspend fun findEvidenceSnapshot(
        snapshotId: String,
    ): AssessmentEvidenceSnapshotEntity? = notExercised()

    override suspend fun findAttributions(
        snapshotId: String,
    ): List<AssessmentEvidenceAttributionEntity> = notExercised()

    override suspend fun findCorrectionsByIds(
        correctionIds: List<String>,
    ): List<AttemptCorrectionEntity> = notExercised()

    override suspend fun findAnswerRevealsByIds(
        outcomeIds: List<String>,
    ): List<AnswerRevealOutcomeEntity> = notExercised()

    override suspend fun findTutorExposuresByIds(
        outcomeIds: List<String>,
    ): List<TutorAnswerExposureOutcomeEntity> = notExercised()

    override suspend fun findChatEvidencesByIds(
        evidenceIds: List<String>,
    ): List<LearnerChatEvidenceEntity> = notExercised()

    override suspend fun findBindingChange(
        bindingChangeId: String,
    ): BindingChangeEventEntity = notExercised()

    override suspend fun findBindingChangesByIds(
        bindingChangeIds: List<String>,
    ): List<BindingChangeEventEntity> = bindingChangeIds.mapNotNull(bindingChangeEntities::get)

    override suspend fun findMemoryStates(
        projectionName: String,
        learnerId: String,
    ): List<LearnerProblemMemoryStateEntity> = notExercised()

    override suspend fun findMasteryStates(
        projectionName: String,
        learnerId: String,
    ): List<LearnerKnowledgeMasteryStateEntity> = notExercised()

    override suspend fun findObservations(
        projectionName: String,
        learnerId: String,
    ): List<IndependentCorrectObservationEntity> = notExercised()

    override suspend fun findAppliedAttempts(
        projectionName: String,
        learnerId: String,
    ): List<AppliedAttemptRecordEntity> = notExercised()

    override suspend fun findAppliedCorrections(
        projectionName: String,
        learnerId: String,
    ): List<AppliedCorrectionRecordEntity> = notExercised()

    override suspend fun findAppliedAnswerReveals(
        projectionName: String,
        learnerId: String,
    ): List<AppliedAnswerRevealRecordEntity> = notExercised()

    override suspend fun findAppliedTutorAnswerExposures(
        projectionName: String,
        learnerId: String,
    ): List<AppliedTutorAnswerExposureRecordEntity> = notExercised()

    override suspend fun findPresentationProjectionStates(
        projectionName: String,
        learnerId: String,
    ): List<PresentationProjectionStateEntity> = notExercised()

    override suspend fun findPresentationProjectionStatesForIds(
        projectionName: String,
        learnerId: String,
        presentationIds: List<String>,
    ): List<PresentationProjectionStateEntity> = notExercised()

    override suspend fun findPresentationProjectionState(
        projectionName: String,
        learnerId: String,
        presentationId: String,
    ): PresentationProjectionStateEntity? = notExercised()

    override suspend fun insertHeader(header: LearnerProjectionSnapshotEntity): Unit = notExercised()

    override suspend fun compareAndSetHeader(
        projectionName: String,
        learnerId: String,
        expectedCheckpoint: Long,
        expectedStateVersion: Long,
        newStateVersion: Long,
        checkpointSequence: Long,
        knownLedgerHeadSequence: Long,
        projectorVersion: String,
        projectedAtEpochMillis: Long,
        generatedAtEpochMillis: Long,
        correctionWatermarkEpochMillis: Long?,
        freshness: String,
        projectionStatus: String,
    ): Int = notExercised()

    override suspend fun deleteMemoryStatesByIds(
        projectionName: String,
        learnerId: String,
        practiceUnitIds: List<String>,
    ): Unit = notExercised()

    override suspend fun deleteObservation(
        projectionName: String,
        learnerId: String,
        knowledgeNodeId: String,
        ordinal: Int,
    ): Unit = notExercised()

    override suspend fun deleteMasteryStatesByIds(
        projectionName: String,
        learnerId: String,
        knowledgeNodeIds: List<String>,
    ): Unit = notExercised()

    override suspend fun deleteAppliedAttemptsByIds(
        projectionName: String,
        learnerId: String,
        attemptIds: List<String>,
    ): Unit = notExercised()

    override suspend fun deleteAppliedCorrectionsByIds(
        projectionName: String,
        learnerId: String,
        correctionIds: List<String>,
    ): Unit = notExercised()

    override suspend fun deleteAppliedAnswerRevealsByIds(
        projectionName: String,
        learnerId: String,
        outcomeIds: List<String>,
    ): Unit = notExercised()

    override suspend fun deleteAppliedTutorAnswerExposuresByIds(
        projectionName: String,
        learnerId: String,
        outcomeIds: List<String>,
    ): Unit = notExercised()

    override suspend fun upsertMemoryStates(states: List<LearnerProblemMemoryStateEntity>): Unit =
        notExercised()

    override suspend fun upsertMasteryStates(states: List<LearnerKnowledgeMasteryStateEntity>): Unit =
        notExercised()

    override suspend fun upsertObservations(
        observations: List<IndependentCorrectObservationEntity>,
    ): Unit = notExercised()

    override suspend fun upsertAppliedAttempts(records: List<AppliedAttemptRecordEntity>): Unit =
        notExercised()

    override suspend fun upsertAppliedCorrections(records: List<AppliedCorrectionRecordEntity>): Unit =
        notExercised()

    override suspend fun upsertAppliedAnswerReveals(
        records: List<AppliedAnswerRevealRecordEntity>,
    ): Unit = notExercised()

    override suspend fun upsertAppliedTutorAnswerExposures(
        records: List<AppliedTutorAnswerExposureRecordEntity>,
    ): Unit = notExercised()

    override suspend fun insertPresentationProjectionState(
        state: PresentationProjectionStateEntity,
    ): Unit = notExercised()

    override suspend fun compareAndSetPresentationProjectionState(
        projectionName: String,
        learnerId: String,
        presentationId: String,
        expectedStateVersion: Long,
        terminalOutcomeId: String?,
        terminalOutcome: String?,
        terminalEventSequence: Long?,
        memoryProjected: Boolean,
        memoryProjectionSequence: Long?,
        lastResponseOrdinal: Int,
        nextStateVersion: Long,
    ): Int = notExercised()

    override suspend fun insertArchive(rows: List<ProjectionArchiveEntity>): Unit = notExercised()

    override suspend fun findTableDdl(tableNames: List<String>): List<String> = notExercised()

    override suspend fun findLatestArchive(
        projectionName: String,
        learnerId: String,
    ): ProjectionArchiveEntity? = notExercised()

    override suspend fun findMaxPresentationTerminalSequence(
        projectionName: String,
        learnerId: String,
    ): Long? = notExercised()

    private fun notExercised(): Nothing =
        error("LearningLedgerChunkReadTest only exercises the ledger read path")

    private companion object {
        const val LEARNER = "learner:ledger-chunk-read"
    }
}

/**
 * 在"块事务结束之后"注入一次追写：真实写入者（chat 证据等）正是在这个窗口提交。
 * 旧实现在这里读到前进的分配头（尾部事务外单读）→ 假 GAP。
 */
private class RacingLedgerDao : LedgerOnlyProjectionTransactionDao() {
    var afterChunk: (() -> Unit)? = null

    override suspend fun readLedgerChunk(
        learnerId: String,
        afterSequence: Long,
        limit: Int,
    ): LedgerChunk {
        val chunk = super.readLedgerChunk(learnerId, afterSequence, limit)
        afterChunk?.invoke()
        return chunk
    }
}
