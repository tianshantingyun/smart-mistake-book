package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.port.PracticeUnitAssessmentRecord
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
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
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

    /**
     * K1（3D 余件批 2）· KF-32 空绑定分支：一道「诚实未分类」（当前绑定为空）的题，重放后历史
     * 证据必须改挂 `pseudo:<科目>` 兜底桶、旧知识点归零、证据不丢（旧投影整份进归档）。
     *
     * 消灭的失败：v12 口径下重放派生为空后退回写时快照，证据仍留在旧知识点上——复习以知识点为
     * 核心扫描时会把本不属于它的证据算进掌握度。本用例同时钉住物化请求与写路径逐条同规则
     * （taxonomy 同常量、revision 取该题当前 revision、科目取 problem.subject、时间取 revision
     * 创建时刻）。
     */
    @Test
    fun `empty bindings replay the historical evidence onto the pseudo bucket and reset the old node`() =
        runBlocking {
            val database = LedgerAwareFakeDatabase()
            val attempt = attempt()
            database.projectionLedger = listOf<LearningLedgerEvent>(attempt, bindingChanged()).map {
                PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
            }
            database.publishLedgerHead(2)
            // 重放前的投影 = 旧口径（写时快照归属）：证据留在旧知识点 kc-old 上——本批要修的状态。
            val displaced = LearningProjector().replay(LEARNER_ID, listOf(attempt)).snapshot
            assertEquals(
                "夹具前提：旧投影的证据确实挂在旧知识点上",
                listOf("kc-old"),
                displaced.knowledgeMasteryStates.keys.toList(),
            )
            database.publishDisplacedProjection(displaced)
            // 该题当前绑定为空（诚实未分类），但题目有评估记录——pseudo 物化的科目/revision 来源。
            database.bindingsByPracticeUnit[attempt.practiceUnitId] = emptyList()
            database.assessmentsByPracticeUnit[attempt.practiceUnitId] = practiceUnitAssessment()
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )

            val drained = requireNotNull(drainer.drain())

            assertEquals(
                "空绑定题的历史证据改挂 pseudo:<科目> 兜底桶；旧知识点归零",
                listOf("pseudo:MATH"),
                drained.snapshot.knowledgeMasteryStates.keys.toList(),
            )
            val request = database.pseudoBindingRequests.single()
            assertEquals(
                "物化请求与写路径同规则：taxonomy 用同一常量",
                StudyPracticeUnitFacts.PSEUDO_ATTRIBUTION_TAXONOMY_VERSION,
                request.taxonomyVersion,
            )
            assertEquals("revision 取该题当前 revision", "revision-1", request.problemRevisionId)
            assertEquals("科目取 problem.subject", "math", request.subject)
            assertEquals(
                "时间取 revision 创建时刻（确定性 ⇒ 重放幂等）",
                BASE_EPOCH_MILLIS,
                request.acceptedAtEpochMillis,
            )
            val observation = drained.snapshot.knowledgeMasteryStates.getValue("pseudo:MATH")
                .independentCorrectObservations.single()
            assertEquals(
                "证据不丢：独立答对仍记在伪桶上，bindingId = 确定性伪绑定 id",
                "pseudo-binding:$UNIT_ID:revision-1:pseudo-evidence-v1:pseudo:MATH",
                observation.bindingId,
            )
            assertEquals(
                "归属权重与写路径同规则（单绑定 = 1.0）",
                1.0,
                observation.evidenceWeight,
                1e-9,
            )
            // 旧节点的证据不是被删掉：被替换的旧投影整份进归档（W0-1 ③），仍可回退查看。
            val archived = LearnerSnapshotJson.decode(
                database.archivedProjectionSnapshots.single().snapshotJson,
            )
            assertEquals(
                "旧投影（含旧节点证据）在覆盖前已归档",
                setOf("kc-old"),
                archived.knowledgeMasteryStates.keys,
            )
            assertEquals(
                "当前绑定表按题读一次（整本重放共用）",
                listOf(UNIT_ID),
                database.currentBindingReads,
            )
        }

    /**
     * K1：重放幂等——同一份账本在"(a) 伪绑定已可见"与"(b) 伪绑定不在当前集合（已确认零绑定的题
     * 在真库按回执读不到它）"两种库状态下重放，掌握度/题卡逐位相同，且 bindingId 不变。
     */
    @Test
    fun `replaying an empty-binding unit again is idempotent across both database states`() =
        runBlocking {
            val database = LedgerAwareFakeDatabase()
            val attempt = attempt()
            database.projectionLedger = listOf<LearningLedgerEvent>(attempt, bindingChanged()).map {
                PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
            }
            database.publishLedgerHead(2)
            database.bindingsByPracticeUnit[attempt.practiceUnitId] = emptyList()
            database.assessmentsByPracticeUnit[attempt.practiceUnitId] = practiceUnitAssessment()
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )
            val first = requireNotNull(drainer.drain())
            assertEquals(1, database.pseudoBindingRequests.size)

            // 第二次重放：(a) 首次物化的伪绑定已落库并成为"当前"（从未确认过的题 → 全部算当前）。
            val secondChange = bindingChanged(sequence = 3, bindingChangeId = "binding-change-2")
            database.projectionLedger = database.projectionLedger +
                PersistedLearningLedgerEvent(secondChange, LearningLedgerFingerprint.event(secondChange))
            database.publishLedgerHead(3)

            val second = requireNotNull(drainer.drain())

            assertEquals(
                "幂等 (a)：伪绑定已可见时重放不再物化（复用同一行）",
                1,
                database.pseudoBindingRequests.size,
            )
            assertEquals(
                "幂等 (a)：掌握度逐位相同",
                first.snapshot.knowledgeMasteryStates,
                second.snapshot.knowledgeMasteryStates,
            )
            assertEquals(
                "幂等 (a)：题卡记忆逐位相同",
                first.snapshot.problemMemoryStates,
                second.snapshot.problemMemoryStates,
            )

            // 第三次重放：(b) 伪绑定不在当前集合（真库"最近一次确认那一批"读不到它）→ 重新物化，
            // 结果仍逐位相同（bindingId 内容寻址 + INSERT IGNORE ⇒ 同一条）。
            database.bindingsByPracticeUnit[attempt.practiceUnitId] = emptyList()
            val thirdChange = bindingChanged(sequence = 4, bindingChangeId = "binding-change-3")
            database.projectionLedger = database.projectionLedger +
                PersistedLearningLedgerEvent(thirdChange, LearningLedgerFingerprint.event(thirdChange))
            database.publishLedgerHead(4)

            val third = requireNotNull(drainer.drain())

            assertEquals(
                "幂等 (b)：伪绑定不可见时重新物化（确定性同一条）",
                2,
                database.pseudoBindingRequests.size,
            )
            assertEquals(
                "幂等 (b)：掌握度仍逐位相同",
                first.snapshot.knowledgeMasteryStates,
                third.snapshot.knowledgeMasteryStates,
            )
            assertEquals(
                "幂等 (b)：题卡记忆仍逐位相同",
                first.snapshot.problemMemoryStates,
                third.snapshot.problemMemoryStates,
            )
        }

    /**
     * K1 触发边界（负向）：只被曝光事件引用的题没有归属消费方（`LearningProjector.replay` 只对
     * attempt/reveal 查当前绑定映射，曝光分支不读归属），重放**不得**为它物化伪绑定——重放不制造
     * 没有消费方的行。单位被读入映射这一步仍然发生（下一条断言即证）。
     */
    @Test
    fun `an exposure-only unit without bindings does not materialize the pseudo bucket`() =
        runBlocking {
            val database = LedgerAwareFakeDatabase()
            val exposure = tutorExposure(sequence = 1)
            database.projectionLedger = listOf<LearningLedgerEvent>(exposure).map {
                PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
            }
            database.publishLedgerHead(1)
            database.assessmentsByPracticeUnit[UNIT_ID] = practiceUnitAssessment()
            // 版本不匹配触发重放（存量库形态；无改绑事件也要走同一条 commitFullReplay）。
            database.publishDisplacedProjection(previousVersionSnapshot(attempt()))
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )

            val drained = requireNotNull(drainer.drain())

            assertTrue("版本不匹配确实触发了重放（先归档）", database.archivedProjectionSnapshots.isNotEmpty())
            assertEquals("该题被读入绑定映射（不是没读）", listOf(UNIT_ID), database.currentBindingReads)
            assertTrue(
                "曝光事件没有归属消费方 → 不物化伪绑定",
                database.pseudoBindingRequests.isEmpty(),
            )
            assertTrue(
                "曝光不改掌握度（不因空绑定而多出伪桶）",
                drained.snapshot.knowledgeMasteryStates.isEmpty(),
            )
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

    private fun bindingChanged(
        sequence: Long = 2,
        bindingChangeId: String = "binding-change-1",
    ): BindingChanged = BindingChanged(
        bindingChangeId = bindingChangeId,
        practiceUnitId = UNIT_ID,
        previousKnowledgeNodeIds = listOf("kc-old"),
        newKnowledgeNodeIds = listOf("kc-new"),
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + 120_000L,
        eventSequence = sequence,
    )

    /**
     * K1：pseudo 物化的输入来源（与写路径读的同一行：`practice_unit.problem_revision_id` +
     * `problem.subject` + `problem_revision.created_at`）。
     */
    private fun practiceUnitAssessment(): PracticeUnitAssessmentRecord = PracticeUnitAssessmentRecord(
        practiceUnitId = UNIT_ID,
        problemId = "problem-1",
        problemRevisionId = "revision-1",
        subject = "math",
        unitTitle = "Title",
        promptMarkdown = "Prompt",
        questionDocumentSnapshot = null,
        answerSpecId = null,
        answerSpecSnapshot = null,
        answerVerificationStatus = "UNKNOWN",
        sourceType = "CAPTURED",
        sourceReference = null,
        revisionCreatedAtEpochMillis = BASE_EPOCH_MILLIS,
    )

    private fun tutorExposure(sequence: Long): TutorAnswerExposureOutcome = TutorAnswerExposureOutcome(
        outcomeId = "exposure-outcome-$sequence",
        exposureId = "exposure-$sequence",
        sessionId = "session-1",
        questionDocumentId = "question-document-1",
        questionRevisionNumber = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        problemRevisionId = "revision-1",
        practiceUnitId = UNIT_ID,
        occurredAtEpochMillis = BASE_EPOCH_MILLIS + 60_000L,
        eventSequence = sequence,
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
    /** K1：题目评估记录（pseudo 物化的科目/当前 revision/创建时刻来源）。 */
    val assessmentsByPracticeUnit = mutableMapOf<String, PracticeUnitAssessmentRecord>()
    /** K1：重放侧 pseudo 物化的请求参数（与写路径逐条同规则的断言面）。 */
    val pseudoBindingRequests = mutableListOf<PseudoBindingRequest>()

    override suspend fun readPracticeUnitAssessment(
        practiceUnitId: String,
    ): PracticeUnitAssessmentRecord? = assessmentsByPracticeUnit[practiceUnitId]

    /**
     * K1：在既有 fake 的物化实现之上只加两件事——记录请求参数、把结果并入绑定表
     * （真库是 INSERT IGNORE 的持久化行：首次物化后，"从未确认过"的题下次读它就是"当前"）。
     */
    override suspend fun ensurePseudoKnowledgeBinding(
        practiceUnitId: String,
        problemRevisionId: String,
        taxonomyVersion: String,
        subject: String,
        acceptedAtEpochMillis: Long,
    ): PracticeUnitKnowledgeBindingRecord? {
        pseudoBindingRequests += PseudoBindingRequest(
            practiceUnitId = practiceUnitId,
            problemRevisionId = problemRevisionId,
            taxonomyVersion = taxonomyVersion,
            subject = subject,
            acceptedAtEpochMillis = acceptedAtEpochMillis,
        )
        val record = super.ensurePseudoKnowledgeBinding(
            practiceUnitId = practiceUnitId,
            problemRevisionId = problemRevisionId,
            taxonomyVersion = taxonomyVersion,
            subject = subject,
            acceptedAtEpochMillis = acceptedAtEpochMillis,
        ) ?: return null
        val existing = bindingsByPracticeUnit[practiceUnitId].orEmpty()
        bindingsByPracticeUnit[practiceUnitId] =
            existing.filterNot { it.bindingId == record.bindingId } + record
        return record
    }

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

/** K1：一次 pseudo 物化请求的原始参数（与写路径逐条同规则的断言载体）。 */
private data class PseudoBindingRequest(
    val practiceUnitId: String,
    val problemRevisionId: String,
    val taxonomyVersion: String,
    val subject: String,
    val acceptedAtEpochMillis: Long,
)
