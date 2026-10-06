package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.AssessmentEvidenceSnapshot
import com.tingyun.smartmistakebook.core.model.AssessmentSnapshotVerification
import com.tingyun.smartmistakebook.core.model.Attempt
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionCertainty
import com.tingyun.smartmistakebook.core.model.EvidenceAttributionRole
import com.tingyun.smartmistakebook.core.model.KnowledgeEvidenceAttribution
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningEvidence
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningEvidenceReason
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProblemMemoryOutcome
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.StudyDayContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S20：drainer 侧的后继映射缓存——跨 drain 命中、失效后重读，且**重读的映射真的进投影**。
 *
 * 缓存消灭的失败：每次 drain 重读 `knowledge_node` 全表。失效依据是 `knowledge_node` 的
 * 表级失效信号（仓库订阅，见 `RoomBackedStudyExperienceRepositorySuccessorsInvalidationTest`）；
 * 这里直接调 drainer 的失效入口，证明"失效 → 重读 → 合并重定向生效"这一半。
 */
class ProjectionDrainerSuccessorsCacheTest {

    @Test
    fun `drains reuse one successors read and re-read only after invalidation`() = runBlocking {
        val database = SuccessorsAwareFakeDatabase()
        val drainer = drainer(database)

        drainer.drain()
        drainer.drain()
        assertEquals(
            "S20：映射跨 drain 复用缓存（两次 drain 只读一次 knowledge_node）",
            1,
            database.successorReads,
        )

        drainer.invalidateKnowledgeNodeSuccessors()
        drainer.drain()
        assertEquals("失效后下一次 drain 必须重读", 2, database.successorReads)
    }

    @Test
    fun `a merge written after the first drain lands on the successor once the cache is invalidated`() =
        runBlocking {
            val database = SuccessorsAwareFakeDatabase()
            val attempt = attempt(sequence = 1)
            database.projectionLedger = listOf(
                PersistedLearningLedgerEvent(attempt, LearningLedgerFingerprint.attempt(attempt)),
            )
            database.publishDisplacedProjection(previousVersionSnapshot())

            val drainer = drainer(database)
            val before = requireNotNull(drainer.drain())
            assertEquals(
                "合并前：历史证据在 kc-old",
                setOf("kc-old"),
                before.snapshot.knowledgeMasteryStates.keys,
            )

            // 内容调和把 kc-old 退役并指向 kc-final（生产写路径：Room DAO → 表级失效 → 仓库清缓存）。
            database.successors = mapOf("kc-old" to "kc-final")
            drainer.invalidateKnowledgeNodeSuccessors()
            database.publishDisplacedProjection(previousVersionSnapshot())

            val after = requireNotNull(drainer.drain())
            assertEquals(
                "失效后重读映射：历史证据并入 successor",
                setOf("kc-final"),
                after.snapshot.knowledgeMasteryStates.keys,
            )
            assertEquals(
                "重读真的带上了新映射（不是把旧值又用了一遍）",
                mapOf("kc-old" to "kc-final"),
                database.successorsRead.last(),
            )
        }

    private fun drainer(database: StudyDatabasePort) = StudyProjectionDrainer(
        database = database,
        learnerId = LEARNER_ID,
        learningProjector = LearningProjector(),
        clock = fixedClock,
        computeDispatcher = Dispatchers.Unconfined,
        databaseDispatcher = Dispatchers.Unconfined,
    )

    private fun attempt(sequence: Long) = Attempt(
        attemptId = "attempt-$sequence",
        presentationId = "presentation-$sequence",
        responseOrdinal = 1,
        assessmentSnapshot = AssessmentEvidenceSnapshot(
            snapshotId = "snapshot-$sequence",
            assessmentItemId = "assessment-$sequence",
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
        studyDay = StudyDayContext(
            epochDay = 20_000L,
            timeZoneId = "Asia/Shanghai",
            utcOffsetMinutes = 480,
        ),
        eventSequence = sequence,
    )

    private fun previousVersionSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = PREVIOUS_PROJECTOR_VERSION,
            projectedAtEpochMillis = BASE_EPOCH_MILLIS,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = BASE_EPOCH_MILLIS,
    )

    private companion object {
        const val LEARNER_ID = "learner:successors-cache"
        const val UNIT_ID = "unit-successors-cache"
        const val BASE_EPOCH_MILLIS = 1_767_225_600_000L
        const val PREVIOUS_PROJECTOR_VERSION = "learning-core-v11(projector-v11)"
        val fixedClock: Clock =
            Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))
    }
}

/**
 * 在既有 [FakeStudyDatabasePort] 上只改一处读口：后继映射（可变更 + 计数）。
 *
 * 两条用例都经**全量重放**路径（`publishDisplacedProjection` 造出旧版本投影触发），
 * 因此不需要增量批事件——重放读的是 `projectionLedger`。
 */
private class SuccessorsAwareFakeDatabase : FakeStudyDatabasePort() {
    var successors: Map<String, String> = emptyMap()
    var successorReads: Int = 0
        private set

    /** 每次读返回的映射，供"重读带上新值"断言。 */
    val successorsRead = mutableListOf<Map<String, String>>()

    override suspend fun readKnowledgeNodeSuccessors(): Map<String, String> {
        successorReads++
        successorsRead += successors
        return successors
    }
}
