package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AnswerRevealOutcome
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.AttemptCorrection
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
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
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * W4-3/S8：全量重放经 `StudyProjectionDrainer` 的读（loadLearningLedger）→ 算（replay）→
 * 提交三段后，投影输出与指纹值必须与"投影器自行重算指纹"的旧口径**逐位一致**。
 *
 * 两条断言各钉一半：
 * 1. 等价——drainer 排空后的快照等于 `LearningProjector().replay(ledger)`（重算路径）的快照；
 * 2. 转发——读边界给的指纹值原样落进 applied 记录（真 DAO 的提交侧
 *    `verifyAppliedEventWindows` 仍会与账本行比对，所以"信任"不是"免检"；本 fake 只验证转发）。
 *
 * K2 批 1 增补第三条（存量库升级演练）：旧版本 displaced 快照 + **非平凡账本**（12 事件，
 * 五类齐、含修正）走同一条 drain → ①归档版本=旧版 ②写入顺序 archive→commit ③重放输出
 * 与投影器自行重算逐位一致 ④归档 JSON 往返保真。既有三条各自缺的一角见该用例的 KDoc。
 */
class ReplayFingerprintDrainerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `drainer full replay matches projector recomputation bit for bit`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val events = mixedLedger()
        database.projectionLedger = events.map {
            PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
        }
        database.publishDisplacedProjection(previousVersionSnapshot())
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        val drained = requireNotNull(drainer.drain())

        assertEquals(
            "重放管道（含指纹复用）的输出必须与投影器自行重算逐位相同",
            LearningProjector().replay(LEARNER_ID, events).snapshot,
            drained.snapshot,
        )
        drained.snapshot.appliedAttemptRecords.values.forEach { record ->
            assertEquals(
                LearningLedgerFingerprint.attempt(
                    events.single { it.ledgerEventId == record.attemptId } as Attempt,
                ),
                record.canonicalFingerprint,
            )
        }
    }

    @Test
    fun `drainer forwards the read-boundary fingerprint instead of recomputing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val attempt = attempt(sequence = 1)
        val readBoundaryValue = "read-boundary-fingerprint"
        database.projectionLedger = listOf(PersistedLearningLedgerEvent(attempt, readBoundaryValue))
        database.publishDisplacedProjection(previousVersionSnapshot())
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        val drained = requireNotNull(drainer.drain())

        val record = drained.snapshot.appliedAttemptRecords.getValue(attempt.attemptId)
        assertEquals(readBoundaryValue, record.canonicalFingerprint)
        assertNotEquals(
            "重算会得到另一个值——这条断言证明 drainer 确实在转发读边界的值",
            LearningLedgerFingerprint.attempt(attempt),
            record.canonicalFingerprint,
        )
    }

    /**
     * K2 批 1：**存量库升级（v13 首开 = 先归档再重放）**的 JVM 演练。
     *
     * 钉住的是"旧版本投影 + 非平凡账本"这一组合——既有三条用例各自缺一角：
     * 1. `ProjectionVersionGuardTest`（core:domain）：纯守卫契约，**账本恒空**（不经过 drainer）；
     * 2. `ProjectionArchiveDrainerTest`：真 drainer + 顺序断言，但**账本恒空**（覆盖不到重放输出）；
     * 3. 本类既有两条：旧版本 + 真账本，但**仅 6 事件的最小样例**且只钉指纹等价/转发。
     *
     * 本用例把三块拼起来：v11 时代 displaced 快照（记忆卡齐、checkpoint 追平账本头）+
     * 12 事件账本（五类齐、含修正——正是只有全量重放才消费得动的事件）→ 断言：
     * ① 归档行的 projectorVersion = 旧版本；② 写入顺序必须 `archive` 早于 `commit`；
     * ③ drainer 的重放输出与 `LearningProjector().replay(同一账本)` 逐位相同；
     * ④ 归档 JSON 往返保真，且归档的是被替换的那一份（不是重放后的新值）。
     */
    @Test
    fun `a v11-era projection is archived before a nontrivial ledger is replayed bit for bit`() =
        runBlocking {
            val database = FakeStudyDatabasePort()
            val events = upgradeLedger(cycles = 2)
            assertEquals(
                "夹具必须非平凡：序列 1..N 连续",
                (1L..events.size.toLong()).toList(),
                events.map { it.eventSequence },
            )
            assertEquals(
                "夹具必须真的覆盖五类账本事件（含修正）",
                5,
                events.map { it::class }.toSet().size,
            )
            database.projectionLedger = events.map {
                PersistedLearningLedgerEvent(it, LearningLedgerFingerprint.event(it))
            }
            val displaced = displacedV11Snapshot(lastSequence = events.size.toLong())
            database.publishDisplacedProjection(displaced)
            val drainer = StudyProjectionDrainer(
                database = database,
                learnerId = LEARNER_ID,
                learningProjector = LearningProjector(),
                clock = fixedClock,
            )

            val drained = requireNotNull(drainer.drain())

            assertEquals(
                "顺序即机制：归档必须发生在覆盖之前（archive → commit）：${database.projectionWriteOrder}",
                listOf("archive", "commit"),
                database.projectionWriteOrder,
            )
            val archived = database.archivedProjectionSnapshots.single()
            assertEquals(
                "归档行必须点名被替换快照的旧版本",
                LEGACY_V11_VERSION,
                archived.projectorVersion,
            )
            assertEquals(
                "归档的必须就是被替换的那一份（JSON 往返逐位一致）",
                displaced,
                LearnerSnapshotJson.decode(archived.snapshotJson),
            )
            assertNotEquals(
                "归档的不是重放后的新值（否则'改数值可回退'落空）",
                drained.snapshot,
                LearnerSnapshotJson.decode(archived.snapshotJson),
            )
            assertEquals(
                "重放结果带当前二进制版本",
                LearningProjector.VERSION,
                drained.snapshot.checkpoint.projectorVersion,
            )
            assertEquals(
                "非平凡账本上，重放管道输出必须与投影器自行重算逐位相同",
                LearningProjector().replay(LEARNER_ID, events).snapshot,
                drained.snapshot,
            )
        }

    private fun previousVersionSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-previous-projector-version" to ProblemMemoryState(
                practiceUnitId = "unit-previous-projector-version",
                stabilityDays = 2.0,
                difficulty = 9.0,
                lastReviewedAtEpochMillis = 10L * 86_400_000L,
                nextReviewAtEpochMillis = 12L * 86_400_000L,
                projectorVersion = PREVIOUS_VERSION,
                checkpointSequence = 1,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = PREVIOUS_VERSION,
            projectedAtEpochMillis = 10L * 86_400_000L,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = 10L * 86_400_000L,
    )

    /**
     * 一台 v11 时代二进制留下的投影：两张记忆卡（v11 口径）挂在账本头序列上。
     * 旧值都带 v11 版本串——这正是 v13 首开时必须先归档、后重放的原因。
     */
    private fun displacedV11Snapshot(lastSequence: Long) = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-0" to ProblemMemoryState(
                practiceUnitId = "unit-0",
                stabilityDays = 6.5,
                difficulty = 5.0,
                lastReviewedAtEpochMillis = occurredAt(2),
                nextReviewAtEpochMillis = occurredAt(2) + 3L * 86_400_000L,
                independentCorrectCount = 2,
                projectorVersion = LEGACY_V11_VERSION,
                checkpointSequence = 2,
                lastEvidenceReason = LearningEvidenceReason.INDEPENDENT_CORRECT.name,
                lastEvidenceDirection = LearningEvidenceDirection.POSITIVE.name,
            ),
            "unit-3" to ProblemMemoryState(
                practiceUnitId = "unit-3",
                stabilityDays = 2.0,
                difficulty = 8.0,
                lastReviewedAtEpochMillis = occurredAt(4),
                nextReviewAtEpochMillis = occurredAt(4) + 86_400_000L,
                projectorVersion = LEGACY_V11_VERSION,
                checkpointSequence = 4,
                lastEvidenceReason = LearningEvidenceReason.INDEPENDENT_CORRECT.name,
                lastEvidenceDirection = LearningEvidenceDirection.POSITIVE.name,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = lastSequence,
            projectorVersion = LEGACY_V11_VERSION,
            projectedAtEpochMillis = occurredAt(lastSequence),
        ),
        knownLedgerHeadSequence = lastSequence,
        generatedAtEpochMillis = occurredAt(lastSequence),
    )

    /**
     * 非平凡升级账本：[cycles] 遍"六事件形态"（attempt → reveal → chat → exposure →
     * attempt + correction），序列 1..6·cycles 连续、id 全局唯一——复用既有夹具的形态，
     * 只是把事件数抬到覆盖门要求的量级（修正只有全量重放消费得动，正是升级路径的目标事件）。
     */
    private fun upgradeLedger(cycles: Int): List<LearningLedgerEvent> {
        val events = mutableListOf<LearningLedgerEvent>()
        var sequence = 1L
        repeat(cycles) {
            events += attempt(sequence)
            events += answerReveal(sequence + 1)
            events += chatEvidence(sequence + 2)
            events += tutorExposure(sequence + 3)
            val corrected = attempt(sequence + 4)
            events += corrected
            events += correction(sequence + 5, corrected)
            sequence += 6
        }
        return events
    }

    /** 五类事件各至少一条、序列 1..N 连续的全量重放账本。 */
    private fun mixedLedger(): List<LearningLedgerEvent> {
        val correctedAttempt = attempt(sequence = 5)
        return listOf(
            attempt(sequence = 1),
            answerReveal(sequence = 2),
            chatEvidence(sequence = 3),
            tutorExposure(sequence = 4),
            correctedAttempt,
            correction(sequence = 6, attempt = correctedAttempt),
        )
    }

    private fun attempt(sequence: Long): Attempt = Attempt(
        attemptId = "attempt-$sequence",
        presentationId = "presentation-$sequence",
        responseOrdinal = 1,
        assessmentSnapshot = snapshot("attempt-$sequence", "unit-${sequence % 4}"),
        evidence = LearningEvidence(
            direction = LearningEvidenceDirection.POSITIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_CORRECT,
        ),
        problemMemoryOutcome = ProblemMemoryOutcome.INDEPENDENT_RECALL,
        occurredAtEpochMillis = occurredAt(sequence),
        durationSeconds = 45,
        studyDay = studyDay(sequence),
        eventSequence = sequence,
    )

    private fun answerReveal(sequence: Long): AnswerRevealOutcome = AnswerRevealOutcome(
        outcomeId = "reveal-$sequence",
        presentationId = "presentation-reveal-$sequence",
        assessmentSnapshot = snapshot("reveal-$sequence", "unit-${sequence % 4}"),
        occurredAtEpochMillis = occurredAt(sequence),
        studyDay = studyDay(sequence),
        eventSequence = sequence,
    )

    private fun chatEvidence(sequence: Long): ChatEvidenceSubmitted = ChatEvidenceSubmitted(
        evidenceId = "chat-$sequence",
        conversationId = "conversation-replay",
        knowledgeNodeId = "kc-${sequence % 3}",
        direction = LearningEvidenceDirection.POSITIVE,
        weight = ChatEvidenceSubmitted.POSITIVE_WEIGHT,
        reasonMarkdown = "模型判断。",
        confidence = 0.9,
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun tutorExposure(sequence: Long): TutorAnswerExposureOutcome = TutorAnswerExposureOutcome(
        outcomeId = "exposure-$sequence",
        exposureId = "exposure-fact-$sequence",
        sessionId = "session-$sequence",
        questionDocumentId = "question-document-$sequence",
        questionRevisionNumber = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        problemRevisionId = "revision-1",
        practiceUnitId = "unit-${sequence % 4}",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun correction(sequence: Long, attempt: Attempt): AttemptCorrection = AttemptCorrection(
        correctionId = "correction-$sequence",
        attemptId = attempt.attemptId,
        replacementEvidence = LearningEvidence(
            direction = LearningEvidenceDirection.NEGATIVE,
            weight = 1.0,
            reason = LearningEvidenceReason.INDEPENDENT_INCORRECT,
        ),
        replacementMemoryOutcome = ProblemMemoryOutcome.RETRIEVAL_FAILURE,
        reasonMarkdown = "修正为独立答错。",
        occurredAtEpochMillis = occurredAt(sequence),
        eventSequence = sequence,
    )

    private fun snapshot(id: String, practiceUnitId: String): AssessmentEvidenceSnapshot =
        AssessmentEvidenceSnapshot(
            snapshotId = "snapshot-$id",
            assessmentItemId = "assessment-$id",
            practiceUnitId = practiceUnitId,
            problemRevisionId = "revision-1",
            answerSpecId = "answer-1",
            itemFamilyId = "family-$id",
            sourceBundleId = "bundle-replay",
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

    private fun occurredAt(sequence: Long): Long = BASE_EPOCH_MILLIS + sequence * 60_000L

    private fun studyDay(sequence: Long): StudyDayContext = StudyDayContext(
        epochDay = 20_000L + sequence / 1_440L,
        timeZoneId = "Asia/Shanghai",
        utcOffsetMinutes = 480,
    )

    private companion object {
        const val LEARNER_ID = "learner:replay-drainer"
        const val PREVIOUS_VERSION = "learning-core-v6(projector-v6,evidence-v4)"

        /**
         * v11 时代真实复合串（与 `ProjectionRollbackDrillInstrumentedTest` 的
         * `LEGACY_PROJECTOR_VERSION` 同一串）：v12/KF-32 前的存量库就长这样。
         */
        const val LEGACY_V11_VERSION =
            "learning-core-v11(projector-v11,evidence-v5,curve-v3,skip-v4,attribution-v3,ledger-v2)"
        const val BASE_EPOCH_MILLIS = 1_767_225_600_000L
    }
}
