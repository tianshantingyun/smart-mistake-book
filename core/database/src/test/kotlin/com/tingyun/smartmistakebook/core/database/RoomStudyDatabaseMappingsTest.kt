package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.CanonicalSourceAssetRow
import com.tingyun.smartmistakebook.core.database.dao.MistakeRow
import com.tingyun.smartmistakebook.core.database.entity.LearnerProjectionSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorMessageEntity
import com.tingyun.smartmistakebook.core.database.dao.SubjectMasteryRow
import com.tingyun.smartmistakebook.core.database.dao.toMemoryEntities
import com.tingyun.smartmistakebook.core.database.dao.toPersistedSnapshot
import com.tingyun.smartmistakebook.core.database.dao.toRecord
import com.tingyun.smartmistakebook.core.database.dao.ReviewPlanAggregate
import com.tingyun.smartmistakebook.core.database.dao.ReviewQueueAggregate
import com.tingyun.smartmistakebook.core.database.entity.ActiveReviewPlanSlotEntity
import com.tingyun.smartmistakebook.core.database.entity.AssessmentItemSnapshotEntity
import com.tingyun.smartmistakebook.core.database.entity.LlmTeachingAdvisoryEntity
import com.tingyun.smartmistakebook.core.database.entity.ReviewPlanEntity
import com.tingyun.smartmistakebook.core.database.entity.ReviewQueueItemEntity
import com.tingyun.smartmistakebook.core.database.entity.ReviewQueueKnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.ReviewQueueReasonEntity
import com.tingyun.smartmistakebook.core.database.entity.ReviewSessionEntity
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.TeachingAdvisoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field-for-field checks on the shared mappings in RoomStudyDatabaseMappings.kt:
 * a mapping that drops or renames a column must fail here instead of silently
 * nulling data at runtime.
 */
class RoomStudyDatabaseMappingsTest {

    @Test
    fun assessmentItemSnapshotRoundTripsLosslessly() {
        val record = AssessmentItemSnapshotSeedRecord(
            assessmentItemSnapshotId = "item-1",
            itemRevision = 2,
            practiceUnitId = "unit-1",
            problemRevisionId = "rev-1",
            tutorContentSnapshotId = "tutor-1",
            promptMarkdown = "1+1=?",
            optionsSnapshot = "[A,B]",
            answerSpecSnapshot = "{\"answer\":\"2\"}",
            verificationStatus = "VERIFIED",
            assessmentEligibility = "ELIGIBLE",
            scoringMode = "EXACT",
            learnerSnapshotVersion = "v1",
            projectionCheckpoint = 7,
            hintLevelAtPresentation = 0,
            answerRevealState = "HIDDEN",
            createdAtEpochMillis = 1_000L,
        )
        assertEquals(record, record.toEntity().toRecord())
    }

    @Test
    fun reviewSessionRecordDerivesActiveSessionKeyOnlyWhileInProgress() {
        val record = reviewSessionRecord(status = StudyDbValue.ReviewStatus.IN_PROGRESS)
        val entity = record.toEntity()
        assertEquals("plan-1", entity.activeSessionKey)
        assertEquals(record, entity.toRecord())

        val ended = record.copy(status = StudyDbValue.ReviewStatus.COMPLETED)
        assertEquals(null, ended.toEntity().activeSessionKey)
    }

    @Test
    fun reviewQueueChildrenAreSortedByIdentifier() {
        val record = reviewQueueRecord(
            knowledgeNodeIds = setOf("kc-c", "kc-a", "kc-b"),
            reasons = setOf("WEAK_KNOWLEDGE", "AVOIDANCE_SIGNAL", "RECENT_LAPSE"),
        )
        assertEquals(
            listOf("kc-a", "kc-b", "kc-c"),
            record.toKnowledgeNodeEntities().map { it.knowledgeNodeId },
        )
        assertEquals(
            listOf("AVOIDANCE_SIGNAL", "RECENT_LAPSE", "WEAK_KNOWLEDGE"),
            record.toReasonEntities().map { it.reason },
        )
    }

    @Test
    fun reviewPlanAggregateToRecordSortsQueueAndResolvesSessionHeads() {
        val plan = reviewPlanEntity()
        val lateSession = reviewSessionEntity(
            status = StudyDbValue.ReviewStatus.COMPLETED,
            lastActiveAtEpochMillis = 9_000L,
            stateVersion = 1,
        )
        val activeSession = reviewSessionEntity(
            status = StudyDbValue.ReviewStatus.IN_PROGRESS,
            lastActiveAtEpochMillis = 1_000L,
            stateVersion = 2,
        )
        val aggregate = ReviewPlanAggregate(
            plan = plan,
            queue = listOf(
                ReviewQueueAggregate(
                    item = reviewQueueItemEntity(ordinal = 2, itemId = "q-2"),
                    knowledgeNodes = listOf(ReviewQueueKnowledgeNodeEntity("q-2", "kc-2")),
                    reasons = listOf(ReviewQueueReasonEntity("q-2", "WEAK_KNOWLEDGE")),
                ),
                ReviewQueueAggregate(
                    item = reviewQueueItemEntity(ordinal = 1, itemId = "q-1"),
                    knowledgeNodes = listOf(ReviewQueueKnowledgeNodeEntity("q-1", "kc-1")),
                    reasons = listOf(ReviewQueueReasonEntity("q-1", "RECENT_LAPSE")),
                ),
            ),
            sessions = listOf(lateSession, activeSession),
            currentSlots = emptyList(),
        )

        val bundle = aggregate.toRecord()
        assertEquals(listOf("q-1", "q-2"), bundle.queue.map { it.reviewQueueItemId })
        assertEquals(false, bundle.isCurrent)
        // activeSession comes from the IN_PROGRESS session, not from currentSlots.
        assertEquals(activeSession.reviewSessionId, bundle.activeSession?.reviewSessionId)
        // latestSessionHead prefers the in-progress session over recency.
        assertEquals(activeSession.reviewSessionId, bundle.latestSession?.reviewSessionId)

        val withoutActive = aggregate.copy(sessions = listOf(lateSession))
        val endedBundle = withoutActive.toRecord()
        assertEquals(null, endedBundle.activeSession)
        assertEquals(lateSession.reviewSessionId, endedBundle.latestSession?.reviewSessionId)

        val withActiveSlot = aggregate.copy(
            currentSlots = listOf(
                ActiveReviewPlanSlotEntity(
                    learnerId = "learner-1",
                    localDayEpochDay = 20_000L,
                    timeZoneId = "Asia/Shanghai",
                    currentReviewPlanId = plan.reviewPlanId,
                    updatedAtEpochMillis = 1_000L,
                ),
            ),
        )
        val activeBundle = withActiveSlot.toRecord()
        assertEquals(true, activeBundle.isCurrent)
        assertEquals(activeSession.reviewSessionId, activeBundle.activeSession?.reviewSessionId)
        // An in-progress session is also the latest head.
        assertEquals(activeSession.reviewSessionId, activeBundle.latestSession?.reviewSessionId)
    }

    @Test
    fun mistakeRowToRecordDecodesTrimsDeduplicatesAndSortsLabels() {
        val row = MistakeRow(
            entryId = "entry-1",
            problemId = "problem-1",
            problemRevisionId = "rev-1",
            practiceUnitId = "unit-1",
            sourceKey = "source-1",
            subject = "MATH",
            title = "题目",
            problemMarkdown = "# 题目",
            status = "ACTIVE",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
            estimatedSeconds = 180,
            nextReviewAtEpochMillis = 3_000L,
            retrievability = 0.9,
            knowledgeNodeIds = "\u001Fkc-c\u001Fkc-a \u001Fkc-a\u001F",
            chapterLabels = null,
            knowledgeLabels = "\u001F函数\u001F",
            captureOccurrenceCount = 0,
        )
        val record = row.toRecord()
        assertEquals(listOf("kc-a", "kc-c"), record.knowledgeNodeIds.toList())
        assertEquals(emptyList<String>(), record.chapterLabels)
        assertEquals(listOf("函数"), record.knowledgeLabels.toList())
        // Zero/negative occurrence counts clamp to a single capture.
        assertEquals(1, record.captureOccurrenceCount)
    }

    @Test
    fun canonicalSourceAssetRowToRecordCarriesEveryField() {
        val row = CanonicalSourceAssetRow(
            sourceAssetId = "asset-1",
            contentSha256 = "abc",
            relativePath = "assets/asset-1",
            mimeType = "image/jpeg",
            byteSize = 10L,
            width = 100,
            height = 200,
            sourceType = "CAMERA",
            createdAtEpochMillis = 1_000L,
        )
        val record = row.toRecord()
        assertEquals("asset-1", record.sourceAssetId)
        assertEquals("abc", record.contentSha256)
        assertEquals("assets/asset-1", record.relativePath)
        assertEquals("image/jpeg", record.mimeType)
        assertEquals(10L, record.byteSize)
        assertEquals(100, record.width)
        assertEquals(200, record.height)
        assertEquals("CAMERA", record.sourceType)
        assertEquals(1_000L, record.createdAtEpochMillis)
    }

    @Test
    fun subjectMasteryRowToRecordCarriesEveryFieldIncludingTheIntervalInputs() {
        // 批次 2 / KF-20（规格 §2.4 锚 2）：新四列逐字段直拷——漏拷时区间与记忆行
        // 会在工具面静默缺失（读面从端口拿原料）。
        val row = SubjectMasteryRow(
            knowledgeNodeId = "kc-a",
            displayName = "函数单调性",
            granularity = "ATOMIC",
            nodeKind = "CONCEPT",
            probabilityIndependentCorrect = 0.93,
            lowerBoundIndependentCorrect = 0.61,
            evidenceMass = 8.0,
            status = "LEARNING",
            lastEvidenceAtEpochMillis = 1_000L,
            lastEvidenceDirection = "POSITIVE",
            lastIndependentErrorAtEpochMillis = 500L,
            boundQuestionCount = 2,
            successWeight = 8.0,
            failureWeight = 0.0,
            memoryStabilityDays = 12.3,
            lastAttemptAtEpochMillis = 900L,
        )

        val record = row.toRecord()

        assertEquals("kc-a", record.knowledgeNodeId)
        assertEquals(0.93, record.probabilityIndependentCorrect, 0.0)
        assertEquals(0.61, record.lowerBoundIndependentCorrect, 0.0)
        assertEquals(8.0, record.evidenceMass, 0.0)
        assertEquals(2, record.boundQuestionCount)
        assertEquals(8.0, record.successWeight, 0.0)
        assertEquals(0.0, record.failureWeight, 0.0)
        assertEquals(12.3, requireNotNull(record.memoryStabilityDays), 0.0)
        assertEquals(900L, requireNotNull(record.lastAttemptAtEpochMillis))
    }

    /**
     * 阶段 3B 步骤一 · D-M M5：`learner_problem_memory_state` 的两列死面（`last_reviewed_epoch_day` /
     * `last_attempt_id`）删除后，实体 ↔ 模型映射必须**逐字段存活**——少映一列（或把死列映射
     * 回来）时，读面拿到的记忆行会在离改动很远的地方静默失真。
     */
    @Test
    fun problemMemoryStateRoundTripsThroughTheEntityWithoutTheDeletedColumns() {
        val state = ProblemMemoryState(
            practiceUnitId = "unit-1",
            stabilityDays = 12.5,
            difficulty = 7.0,
            lastReviewedAtEpochMillis = 1_700_000_000_000L,
            nextReviewAtEpochMillis = 1_700_100_000_000L,
            independentCorrectCount = 3,
            assistedCorrectCount = 1,
            lapseCount = 2,
            answerRevealCount = 1,
            lastLapseAtEpochMillis = 1_699_000_000_000L,
            clockAnomalyCount = 1,
            lastClockAnomalyAtEpochMillis = 1_699_500_000_000L,
            projectorVersion = "projector-v11",
            checkpointSequence = 5,
            lastEvidenceReason = "INDEPENDENT_CORRECT",
            lastEvidenceDirection = "POSITIVE",
            consecutiveCrossDaySuccess = 2,
            consecutiveCrossDayAgain = 1,
        )
        val snapshot = LearnerSnapshot(
            learnerId = "learner-1",
            problemMemoryStates = mapOf(state.practiceUnitId to state),
            checkpoint = ProjectionCheckpoint(
                lastSequence = 5,
                projectorVersion = "projector-v11",
                projectedAtEpochMillis = 1_700_000_000_000L,
            ),
            knownLedgerHeadSequence = 5,
            generatedAtEpochMillis = 1_700_000_000_000L,
        )

        val entity = snapshot.toMemoryEntities("study-experience-v1").single()
        val restored = snapshotEntity().toPersistedSnapshot(
            memoryStates = listOf(entity),
            masteryStates = emptyList(),
            observations = emptyList(),
            appliedAttempts = emptyList(),
            appliedCorrections = emptyList(),
            appliedAnswerReveals = emptyList(),
            appliedTutorAnswerExposures = emptyList(),
        )

        assertEquals(mapOf(state.practiceUnitId to state), restored.snapshot.problemMemoryStates)
    }

    @Test
    fun teachingAdvisoryRoundTripsLosslessly() {
        val record = TeachingAdvisoryRecord(
            advisoryId = "adv-1",
            learnerId = "learner-1",
            practiceUnitId = "unit-1",
            knowledgeNodeId = "kc-1",
            advisoryKind = "HINT",
            payloadMarkdown = "提示",
            confidence = 0.8,
            sourceId = "source-1",
            createdAtEpochMillis = 1_000L,
        )
        val entity: LlmTeachingAdvisoryEntity = record.toEntity()
        assertEquals(record, entity.toRecord())
    }

    @Test
    fun expectedWorkspaceToConsumeCommandCarriesExactIdentity() {
        val expected = ExpectedProblemDraftEditWorkspace(
            draftId = "draft-1",
            basisRevisionNumber = 3,
            workspaceVersion = 5,
            workspaceFingerprint = "fp-1",
            finalRequestId = "request-1",
            finalOccurredAtEpochMillis = 7_000L,
        )
        val command = expected.toConsumeCommand()
        assertEquals("draft-1", command.draftId)
        assertEquals(3, command.basisRevisionNumber)
        assertEquals(5, command.expectedWorkspaceVersion)
        assertEquals("fp-1", command.expectedWorkspaceFingerprint)
    }

    private fun snapshotEntity() = LearnerProjectionSnapshotEntity(
        projectionName = "study-experience-v1",
        learnerId = "learner-1",
        stateVersion = 1,
        checkpointSequence = 5,
        knownLedgerHeadSequence = 5,
        projectorVersion = "projector-v11",
        projectedAtEpochMillis = 1_700_000_000_000L,
        generatedAtEpochMillis = 1_700_000_000_000L,
        correctionWatermarkEpochMillis = null,
        freshness = "CURRENT",
        projectionStatus = "CURRENT",
    )

    private fun reviewSessionRecord(
        status: String,
    ) = ReviewSessionRecord(
        reviewSessionId = "session-1",
        reviewPlanId = "plan-1",
        status = status,
        startedAtEpochMillis = 1_000L,
        lastActiveAtEpochMillis = 2_000L,
        completedAtEpochMillis = null,
        currentOrdinal = 3,
        timeBudgetSeconds = 600,
        projectionCheckpoint = 7L,
        stateVersion = 4,
    )

    private fun reviewSessionEntity(
        status: String,
        lastActiveAtEpochMillis: Long,
        stateVersion: Long,
    ) = ReviewSessionEntity(
        reviewSessionId = if (status == StudyDbValue.ReviewStatus.IN_PROGRESS) "session-active" else "session-late",
        reviewPlanId = "plan-1",
        status = status,
        activeSessionKey = null,
        startedAtEpochMillis = 1_000L,
        lastActiveAtEpochMillis = lastActiveAtEpochMillis,
        completedAtEpochMillis = null,
        currentOrdinal = 1,
        timeBudgetSeconds = 600,
        projectionCheckpoint = 7L,
        stateVersion = stateVersion,
    )

    private fun reviewPlanEntity() = ReviewPlanEntity(
        reviewPlanId = "plan-1",
        learnerId = "learner-1",
        localDate = "2026-09-06",
        localDayEpochDay = 20_000L,
        timeZoneId = "Asia/Shanghai",
        timeBudgetSeconds = 600,
        planningAtEpochMillis = 1_000L,
        status = "ACTIVE",
        plannerVersion = "v2",
        projectionCheckpoint = 7L,
        planFingerprint = "plan-fp",
        planRevision = 1,
        createdAtEpochMillis = 1_000L,
    )

    private fun reviewQueueItemEntity(
        ordinal: Int,
        itemId: String,
    ) = ReviewQueueItemEntity(
        reviewQueueItemId = itemId,
        reviewPlanId = "plan-1",
        practiceUnitId = "unit-$ordinal",
        itemFamilyId = "",
        sourceBundleId = null,
        ordinal = ordinal,
        priorityScore = 0.5,
        difficultyBand = "",
        dueAtEpochMillis = 2_000L,
        estimatedSeconds = 180,
        reasonSnapshot = "",
        status = "PENDING",
    )

    /**
     * 轮次事实（51→52 由 `tutor_turn_response` 并入 `tutor_message`）在行映射上逐列存活：
     * 少映一列就等于"这一轮学生选了什么 / 揭示了没有"读不回来，而写入侧是好的——症状会
     * 出现在离改动很远的地方（时间线的选项反馈、曝光面校验）。
     */
    @Test
    fun tutorMessageRoundColumnsSurviveTheRowMapping() {
        val entity = TutorMessageEntity(
            messageId = "tutor-round:session-1:1:1",
            conversationId = "tutor-conv:captured:session-1",
            ordinal = 2,
            role = "LOCAL_EVENT",
            bodyMarkdown = "",
            roundCycleOrdinal = 1,
            roundTurnOrdinal = 1,
            roundQuestionDocumentId = "question-1",
            roundRevisionNumber = 2,
            choiceStemMarkdown = "关键一步是什么？",
            choiceSelectedId = "choice-2",
            choiceSelectedMarkdown = "先判断符号",
            choiceWasCorrect = true,
            choiceFeedbackMarkdown = "这个判断是对的。",
            solutionRevealed = true,
            requestedMove = null,
            status = "PERSISTED",
            logicalOperationId = null,
            replyToMessageId = null,
            createdAtEpochMillis = 5,
            completedAtEpochMillis = 6,
            errorCode = null,
        )

        val record = entity.toRecord()

        assertNotNull(record.toTurnRecordOrNull("session-1"))
        assertEquals(1, record.roundCycleOrdinal)
        assertEquals(1, record.roundTurnOrdinal)
        assertEquals("question-1", record.roundQuestionDocumentId)
        assertEquals(2, record.roundRevisionNumber)
        assertEquals("关键一步是什么？", record.choiceStemMarkdown)
        assertEquals("choice-2", record.choiceSelectedId)
        assertEquals("先判断符号", record.choiceSelectedMarkdown)
        assertEquals(true, record.choiceWasCorrect)
        assertEquals("这个判断是对的。", record.choiceFeedbackMarkdown)
        assertEquals(true, record.solutionRevealed)
        assertNull(record.requestedMove)
        assertTrue(record.isTutorRoundRow)
    }

    /** 普通的对话消息不是轮次行：它不进 turns（此前它会被当成轮次行喂给域模型而抛不变式）。 */
    @Test
    fun aPlainConversationMessageIsNotARoundRow() {
        val entity = TutorMessageEntity(
            messageId = "tutor-message:1",
            conversationId = "tutor-conv:captured:session-1",
            ordinal = 1,
            role = "STUDENT",
            bodyMarkdown = "这题怎么做",
            status = "PERSISTED",
            logicalOperationId = "op-1",
            replyToMessageId = null,
            createdAtEpochMillis = 1,
            completedAtEpochMillis = 1,
            errorCode = null,
        )

        val record = entity.toRecord()

        assertNull(record.toTurnRecordOrNull("session-1"))
        assertFalse(record.isTutorRoundRow)
    }


    private fun reviewQueueRecord(
        knowledgeNodeIds: Set<String>,
        reasons: Set<String>,
    ) = ReviewQueueItemRecord(
        reviewQueueItemId = "q-1",
        reviewPlanId = "plan-1",
        practiceUnitId = "unit-1",
        knowledgeNodeIds = knowledgeNodeIds,
        itemFamilyId = "",
        sourceBundleId = null,
        reasons = reasons,
        ordinal = 1,
        priorityScore = 0.5,
        difficultyBand = "",
        dueAtEpochMillis = 2_000L,
        estimatedSeconds = 180,
        reasonSnapshot = "",
        status = "PENDING",
    )
}
