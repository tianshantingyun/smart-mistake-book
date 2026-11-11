package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.LearningLedgerRead
import com.tingyun.smartmistakebook.core.database.LearningLedgerReadStatus
import com.tingyun.smartmistakebook.core.database.PersistedIncrementalLearningEvent
import com.tingyun.smartmistakebook.core.database.PersistedLearningLedgerEvent
import com.tingyun.smartmistakebook.core.database.ProjectionBatch
import com.tingyun.smartmistakebook.core.database.ProjectionBatchStopReason
import com.tingyun.smartmistakebook.core.database.ProjectionOutboxRecord
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationConsumer
import com.tingyun.smartmistakebook.core.domain.AdaptiveClarificationRequest
import com.tingyun.smartmistakebook.core.domain.AdaptiveDecisionKind
import com.tingyun.smartmistakebook.core.domain.AdaptiveTargetKind
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.StudyDataStatus
import com.tingyun.smartmistakebook.core.model.ChatEvidenceSubmitted
import com.tingyun.smartmistakebook.core.model.IncrementalLearningEvent
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.LearningEvidenceDirection
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 49 P2-13：投影级冲突处置。
 *
 * 钉三件事：
 * 1. 投影排空撞上账本冲突/缺口时，读面必须给出**专用**的 [ProjectionStatus]（不是只有
 *    通用 ERROR）——学生据此看到"投影与账本对不上"，而不是"数据库打不开"；
 * 2. "重新整理"（[RoomBackedStudyExperienceRepository.reprojectAll]）在账本修好后用
 *    全量重放把这版投影重算对，成功即清空专用状态；
 * 3. 重放再失败时专用状态**保留**（降级成通用 ERROR 会让唯一修复动作消失）。
 *
 * 同时钉住"不误报"：普通读失败（IO 错误）不得被说成投影冲突；健康的空库没有专用状态。
 */
class ProjectionConflictReprojectTest {

    private val fixedClock = Clock.fixed(
        Instant.parse("2026-01-02T08:00:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun conflictedLedgerSurfacesADedicatedProjectionIssue() = runBlocking {
        val database = LedgerStatusFake().apply {
            ledgerStatus = LearningLedgerReadStatus.CONFLICT
            publishDisplacedProjection(conflictedProjection())
        }
        val repository = repository(database)

        try {
            val failure = runCatching { repository.initialize() }
            assertTrue("初始化在该投影上必须失败：$failure", failure.isFailure)

            val failed = repository.snapshot.value
            assertEquals(StudyDataStatus.ERROR, failed.status)
            assertEquals(
                "账本冲突必须落成投影专用状态，而不是只剩通用 ERROR",
                ProjectionStatus.CONFLICTED,
                failed.projectionIssue?.status,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun ledgerGapSurfacesTheWaitingForGapProjectionIssue() = runBlocking {
        val database = LedgerStatusFake().apply {
            ledgerStatus = LearningLedgerReadStatus.GAP
            publishDisplacedProjection(conflictedProjection())
        }
        val repository = repository(database)

        try {
            assertTrue(runCatching { repository.initialize() }.isFailure)

            assertEquals(
                "缺口与冲突是两种不同的账本事实，不得合并成同一个状态",
                ProjectionStatus.WAITING_FOR_GAP,
                repository.snapshot.value.projectionIssue?.status,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun reprojectAllReplaysTheWholeLedgerAndClearsTheProjectionIssue() = runBlocking {
        val database = LedgerStatusFake().apply {
            ledgerStatus = LearningLedgerReadStatus.CONFLICT
            publishDisplacedProjection(conflictedProjection())
        }
        database.projectionWriteOrder.clear()
        val repository = repository(database)

        try {
            assertTrue(runCatching { repository.initialize() }.isFailure)
            assertEquals(ProjectionStatus.CONFLICTED, repository.snapshot.value.projectionIssue?.status)

            // 账本修好（冲突消失、有一笔可重放证据）之后，学生点"重新整理"。
            database.ledgerStatus = LearningLedgerReadStatus.COMPLETE
            database.projectionLedger = listOf(persistedEvidence(sequence = 1))
            repository.reprojectAll()

            val recovered = repository.snapshot.value
            assertEquals(StudyDataStatus.READY, recovered.status)
            assertNull("重放成功后不得再挂着投影专用状态", recovered.projectionIssue)
            assertEquals(
                "被替换的旧投影必须先归档再覆盖（archive → commit）：${database.projectionWriteOrder}",
                listOf("archive", "commit"),
                database.projectionWriteOrder,
            )
            val persisted = database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID)
            assertEquals(ProjectionStatus.CURRENT, persisted?.snapshot?.projectionStatus)
            assertEquals(1L, persisted?.snapshot?.checkpoint?.lastSequence)
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun aFailedReprojectKeepsTheDedicatedIssueInsteadOfFallingBackToGenericError() = runBlocking {
        val database = LedgerStatusFake().apply {
            ledgerStatus = LearningLedgerReadStatus.GAP
            publishDisplacedProjection(conflictedProjection())
        }
        val repository = repository(database)

        try {
            assertTrue(runCatching { repository.initialize() }.isFailure)

            val reproject = runCatching { repository.reprojectAll() }
            assertTrue("账本仍有缺口时重放必须如实失败：$reproject", reproject.isFailure)

            val failed = repository.snapshot.value
            assertEquals(StudyDataStatus.ERROR, failed.status)
            assertEquals(
                "重放失败后唯一的修复动作不能消失（状态必须仍是投影专用）",
                ProjectionStatus.WAITING_FOR_GAP,
                failed.projectionIssue?.status,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun aGenericProjectionReadFailureDoesNotClaimAConflict() = runBlocking {
        val database = FakeStudyDatabasePort()
        val repository = repository(database)

        try {
            repository.initialize()
            database.failProjectionReads = true

            assertTrue(runCatching { repository.refresh() }.isFailure)

            val failed = repository.snapshot.value
            assertEquals(StudyDataStatus.ERROR, failed.status)
            assertNull(
                "普通读失败不是投影冲突；误报会让学生去点一个修不好问题的按钮",
                failed.projectionIssue,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun aHealthyEmptyDatabaseHasNoProjectionIssue() = runBlocking {
        val database = FakeStudyDatabasePort()
        val repository = repository(database)

        try {
            repository.initialize()

            assertEquals(StudyDataStatus.READY, repository.snapshot.value.status)
            assertNull(repository.snapshot.value.projectionIssue)
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun anIncrementalBatchRejectedByTheProjectorSurfacesTheConflictIssue() = runBlocking {
        val database = IncrementalConflictFake().apply {
            publishDisplacedProjection(conflictedProjection())
        }
        val repository = repository(database)

        try {
            assertTrue(runCatching { repository.initialize() }.isFailure)

            val failed = repository.snapshot.value
            assertEquals(StudyDataStatus.ERROR, failed.status)
            assertEquals(
                "增量投影拒绝一个数据库校验过的前缀，本质也是投影冲突——不得退化成通用 ERROR",
                ProjectionStatus.CONFLICTED,
                failed.projectionIssue?.status,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    @Test
    fun calibrationIntentRoutesThroughTheCalibrationCorridor() = runBlocking {
        val database = FakeStudyDatabasePort()
        val repository = repository(database)
        val consumer = object : AdaptiveClarificationConsumer {
            override suspend fun onClarificationRequired(request: AdaptiveClarificationRequest) {
                // 空候选池不会产生澄清载荷；若出现说明选择路径与夹具不符。
                error("Unexpected clarification request: $request")
            }
        }

        try {
            repository.initialize()

            val calibration = requireNotNull(
                repository.selectKnowledgeReviewQuestion(
                    sessionId = 1L,
                    knowledgeNodeId = "kc-a",
                    clarificationConsumer = consumer,
                    requestedTargetKind = AdaptiveTargetKind.CALIBRATION,
                    userRequestedQuestion = true,
                ),
            )
            assertEquals(AdaptiveDecisionKind.NO_SAFE_CANDIDATE, calibration.kind)
            assertTrue(
                "校准请求必须走选择器已有的校准走廊：${calibration.reasonCodes}",
                "CALIBRATION_REQUESTED" in calibration.reasonCodes,
            )

            val foundation = requireNotNull(
                repository.selectKnowledgeReviewQuestion(
                    sessionId = 1L,
                    knowledgeNodeId = "kc-a",
                    clarificationConsumer = consumer,
                ),
            )
            assertTrue(
                "默认（正常复习）不得带上校准意图：${foundation.reasonCodes}",
                "CALIBRATION_REQUESTED" !in foundation.reasonCodes,
            )
        } finally {
            repository.close()
            applicationScope.cancel()
        }
    }

    private fun repository(database: FakeStudyDatabasePort) =
        RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
            clock = fixedClock,
            studyZoneId = ZoneId.of("Asia/Shanghai"),
        )

    /** 与生产同版本的**冲突态**投影：状态自报 CONFLICTED、新鲜度为 STALE。 */
    private fun conflictedProjection() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        checkpoint = ProjectionCheckpoint(
            lastSequence = 0,
            projectorVersion = LearningProjector.VERSION,
            projectedAtEpochMillis = 0,
        ),
        knownLedgerHeadSequence = 0,
        generatedAtEpochMillis = 0,
        freshness = LearnerSnapshotFreshness.STALE,
        projectionStatus = ProjectionStatus.CONFLICTED,
    )

    /**
     * 增量批次带一笔事件、而上一版投影自报 CONFLICTED：投影器会走
     * `unchangedConflict`（拒绝继续算），排空必须把这判成投影冲突而不是通用错误。
     */
    private class IncrementalConflictFake : FakeStudyDatabasePort() {
        override suspend fun loadProjectionBatch(
            projectionName: String,
            learnerId: String,
            limit: Int,
        ): ProjectionBatch {
            val events = listOf(persistedEvidence(sequence = 1))
            return ProjectionBatch(
                projectionName = projectionName,
                learnerId = learnerId,
                previousCheckpoint = 0L,
                ledgerHeadSequence = 1,
                events = events.map { persisted ->
                    PersistedIncrementalLearningEvent(
                        event = persisted.event as IncrementalLearningEvent,
                        canonicalFingerprint = persisted.canonicalFingerprint,
                        outbox = ProjectionOutboxRecord(
                            outboxId = "outbox-1",
                            learnerId = learnerId,
                            outboxSequence = 1,
                            eventKind = "CHAT_EVIDENCE_SUBMITTED",
                            eventId = persisted.event.ledgerEventId,
                            canonicalFingerprint = persisted.canonicalFingerprint,
                            status = "PENDING",
                            createdAtEpochMillis = 1_000L,
                        ),
                    )
                },
                authoritativePresentationStates = emptyMap(),
                stopReason = ProjectionBatchStopReason.END_OF_LEDGER,
            )
        }
    }

    /**
     * 账本读边界可拨动的假库：`loadLearningLedger` 的状态就是本次要演练的账本事实
     * （COMPLETE / GAP / CONFLICT）。不修改共享假库，避免影响既有用例。
     */
    private class LedgerStatusFake : FakeStudyDatabasePort() {
        var ledgerStatus: LearningLedgerReadStatus = LearningLedgerReadStatus.COMPLETE

        override suspend fun loadLearningLedger(learnerId: String): LearningLedgerRead =
            LearningLedgerRead(
                learnerId = learnerId,
                validPrefix = projectionLedger,
                status = ledgerStatus,
                blockedAtSequence = if (ledgerStatus == LearningLedgerReadStatus.COMPLETE) null else 1L,
                detail = if (ledgerStatus == LearningLedgerReadStatus.COMPLETE) {
                    null
                } else {
                    "Learning ledger is $ledgerStatus at sequence 1"
                },
            )
    }

    private companion object {
        const val LEARNER_ID = "learner:local"

        fun persistedEvidence(sequence: Long): PersistedLearningLedgerEvent {
            val event = ChatEvidenceSubmitted(
                evidenceId = "evidence-$sequence",
                conversationId = "conversation",
                knowledgeNodeId = "kc-a",
                direction = LearningEvidenceDirection.POSITIVE,
                weight = ChatEvidenceSubmitted.POSITIVE_WEIGHT,
                reasonMarkdown = "explains the step",
                confidence = 0.9,
                occurredAtEpochMillis = 1_000L + sequence,
                eventSequence = sequence,
            )
            return PersistedLearningLedgerEvent(event, LearningLedgerFingerprint.event(event))
        }
    }
}
