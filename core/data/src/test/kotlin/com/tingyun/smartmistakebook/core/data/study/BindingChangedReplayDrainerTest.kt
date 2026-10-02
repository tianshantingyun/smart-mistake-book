package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitKnowledgeBindingRecord
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.BindingChanged
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KF-32（3B 步骤三）经 **drainer 全管道**的回归：改绑事件 → 全量重放 → 历史证据挂当前绑定节点。
 *
 * 领域侧单测（`LearningProjectorReplayFingerprintTest`）只证明"给了当前绑定就能重派生"；本测试
 * 证明 **drainer 真的把当前绑定表读进来并传给投影器**（接线断了这里会红）。
 *
 * 另钉住两个负向：无改绑事件且版本一致时**不**走全量重放；无改绑时也不读绑定表。
 */
class BindingChangedReplayDrainerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `drainer replays after a binding change and lands historical evidence on the current binding`() =
        runBlocking {
            val database = LedgerAwareFakeDatabase()
            val attempt = attempt()
            val ledger = listOf<LearningLedgerEvent>(attempt, bindingChanged())
            database.projectionLedger = ledger.map {
                PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
            }
            database.publishLedgerHead(2)
            // 重放前的投影是"上一个二进制"的口径（真实场景：本批 bump 后存量库的第一次排空）。
            database.publishDisplacedProjection(previousVersionSnapshot(attempt))
            database.bindingsByPracticeUnit[attempt.practiceUnitId] = listOf(
                binding(bindingId = "binding-new", knowledgeNodeId = "kc-new"),
            )
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )

            val drained = requireNotNull(drainer.drain())

            assertEquals(
                "重放后：历史证据按当前绑定落在新节点上",
                listOf("kc-new"),
                drained.snapshot.knowledgeMasteryStates.keys.toList(),
            )
            assertEquals(
                "重派生归属使用当前绑定 id",
                "binding-new",
                drained.snapshot.knowledgeMasteryStates.getValue("kc-new")
                    .independentCorrectObservations.single().bindingId,
            )
            assertTrue(
                "重放前必须先把被替换的投影整份归档（W0-1）",
                database.archivedProjectionSnapshots.isNotEmpty(),
            )
            assertEquals(
                "当前绑定表按题读一次（账本里出现过的题），整本重放共用同一份",
                listOf(attempt.practiceUnitId),
                database.currentBindingReads,
            )
            assertTrue(
                "重放必须读**当前**绑定集合（不是表里全部行）——改绑后被保留的旧绑定是审计遗迹",
                database.rawBindingReads.isEmpty(),
            )
        }

    @Test
    fun `a drain without a binding change does not replay and does not read the binding table`() =
        runBlocking {
            val database = LedgerAwareFakeDatabase()
            val attempt = attempt()
            database.projectionLedger = listOf(
                PersistedLearningLedgerEvent(attempt, LearningLedgerFingerprint.attempt(attempt)),
            )
            database.publishLedgerHead(1)
            // 版本一致 + 批量读没有待消费事件 → 不触发重放。
            database.publishDisplacedProjection(
                LearnerSnapshot.empty(learnerId = LEARNER_ID, projectorVersion = LearningProjector.VERSION),
            )
            database.publishLedgerHead(1)

            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )

            val drained = requireNotNull(drainer.drain())

            assertTrue(
                "无改绑、版本一致时不得走全量重放",
                database.archivedProjectionSnapshots.isEmpty(),
            )
            assertTrue("该路径不读绑定表", database.currentBindingReads.isEmpty())
            assertTrue("该路径不读绑定表", database.rawBindingReads.isEmpty())
            assertEquals(LearningProjector.VERSION, drained.snapshot.checkpoint.projectorVersion)
        }

    private fun attempt(): Attempt = Attempt(
        attemptId = "attempt-1",
        presentationId = "presentation-1",
        responseOrdinal = 1,
        assessmentSnapshot = AssessmentEvidenceSnapshot(
            snapshotId = "snapshot-1",
            assessmentItemId = "assessment-1",
            practiceUnitId = UNIT_ID,
            problemRevisionId = "revision-1",
            answerSpecId = "answer-1",
            itemFamilyId = "family-1",
            sourceBundleId = "bundle-1",
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
                    bindingId = "binding-old",
                    knowledgeNodeId = "kc-old",
                    weight = 1.0,
                    basisRevisionId = "revision-1",
                    taxonomyVersion = "taxonomy-v1",
                    role = EvidenceAttributionRole.PRIMARY,
                    certainty = EvidenceAttributionCertainty.DIRECT,
                ),
            ),
            capturedAtEpochMillis = BASE_EPOCH_MILLIS,
        ),
        evidence = LearningEvidence(
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
        ),
        problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + 60_000L,
        durationSeconds = 45,
        studyDay = StudyDayContext(epochDay = 20_000L, timeZoneId = "Asia/Shanghai", utcOffsetMinutes = 480),
        eventSequence = 1,
    )

    private fun bindingChanged(): BindingChanged = BindingChanged(
        bindingChangeId = "binding-change-1",
        practiceUnitId = UNIT_ID,
        previousKnowledgeNodeIds = listOf("kc-old"),
        newKnowledgeNodeIds = listOf("kc-new"),
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + 120_000L,
        eventSequence = 2,
    )

    private fun binding(bindingId: String, knowledgeNodeId: String) = PracticeUnitKnowledgeBindingRecord(
        bindingId = bindingId,
        practiceUnitId = UNIT_ID,
        knowledgeNodeId = knowledgeNodeId,
        basisRevisionId = "revision-1",
        taxonomyVersion = "taxonomy-v1",
        acceptedAtEpochMillis = BASE_EPOCH_MILLIS,
    )

    private fun previousVersionSnapshot(attempt: Attempt) = LearnerSnapshot(
        learnerId = LEARNER_ID,
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = PREVIOUS_PROJECTOR_VERSION,
            projectedAtEpochMillis = attempt.occurredAtEpochMillis,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = attempt.occurredAtEpochMillis,
    )

    private companion object {
        const val LEARNER_ID = "learner:binding-replay"
        const val UNIT_ID = "unit-binding-replay"
        const val BASE_EPOCH_MILLIS = 1_767_225_600_000L
        const val PREVIOUS_PROJECTOR_VERSION = "learning-core-v11(projector-v11)"
    }
}

/**
 * KF-32 专用 fake：在既有 [FakeStudyDatabasePort] 上只改两处读口——
 * 批量读如实按"账本里有无待消费的改绑事件"报告停止原因（与真 DAO 的判定同义），账本读暴露夹具。
 * 其余行为（快照读/提交/归档/绑定读）全部复用既有实现，避免另造一个百来方法的端口桩。
 */
private class LedgerAwareFakeDatabase : FakeStudyDatabasePort() {
    val bindingsByPracticeUnit = mutableMapOf<String, List<PracticeUnitKnowledgeBindingRecord>>()
    /** KF-32 的"当前绑定集合"读口（重放必须走它）。 */
    val currentBindingReads = mutableListOf<String>()
    /** 表里全部行的读口——重放不该碰它（被保留的旧绑定是审计遗迹）。 */
    val rawBindingReads = mutableListOf<String>()

    override suspend fun loadProjectionBatch(
        projectionName: String,
        learnerId: String,
        limit: Int,
    ): ProjectionBatch {
        val snapshot = super.readCurrentLearnerSnapshot(projectionName, learnerId)
        val checkpoint = snapshot?.snapshot?.checkpoint?.lastSequence ?: 0L
        val pending = projectionLedger.filter { it.event.eventSequence > checkpoint }
        val requiresFullReplay = pending.any { it.event is BindingChanged }
        return ProjectionBatch(
            projectionName = projectionName,
            learnerId = learnerId,
            previousCheckpoint = checkpoint,
            ledgerHeadSequence = projectionLedger.maxOfOrNull { it.event.eventSequence } ?: 0L,
            // 真 DAO 在改绑行**之前**就停下（`loadProjectionBatch` 的 early return），因此这里
            // 报告"没有可增量消费的事件"是如实口径：重放会把整本账本重新消化。
            events = emptyList(),
            authoritativePresentationStates = emptyMap(),
            stopReason = if (requiresFullReplay) {
                ProjectionBatchStopReason.FULL_REPLAY_REQUIRED
            } else {
                ProjectionBatchStopReason.END_OF_LEDGER
            },
            blockedAtSequence = if (requiresFullReplay) checkpoint + 1 else null,
            detail = if (requiresFullReplay) "Binding change requires a full ledger replay" else null,
        )
    }

    override suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead =
        LearningLedgerRead(
            learnerId = learnerId,
            validPrefix = projectionLedger,
            status = com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus.COMPLETE,
        )

    override suspend fun readPracticeUnitKnowledgeBindings(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingRecord> {
        rawBindingReads += practiceUnitId
        return bindingsByPracticeUnit[practiceUnitId].orEmpty()
    }

    override suspend fun readCurrentPracticeUnitKnowledgeBindings(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingRecord> {
        currentBindingReads += practiceUnitId
        return bindingsByPracticeUnit[practiceUnitId].orEmpty()
    }
}
