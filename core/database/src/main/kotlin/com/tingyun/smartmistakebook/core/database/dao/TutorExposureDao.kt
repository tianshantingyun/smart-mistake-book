package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.PersistTutorAnswerExposureCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorSessionAnchorCommand
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.TutorAnswerExposureRecord
import com.tingyun.smartmistakebook.core.database.TutorSessionProblemAnchorRecord
import com.tingyun.smartmistakebook.core.database.entity.LearningSequenceEntity
import com.tingyun.smartmistakebook.core.database.entity.ModelTaskEntity
import com.tingyun.smartmistakebook.core.database.entity.ProjectionOutboxEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorAnswerExposureEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorAnswerExposureOutcomeEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorConversationEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorMessageEntity
import com.tingyun.smartmistakebook.core.model.LearningLedgerFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskCodec
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorAnswerExposureOutcome
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import com.tingyun.smartmistakebook.core.model.canExposeSolutionFor
import com.tingyun.smartmistakebook.core.model.requiresRoundQuestionBinding
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal const val EVENT_KIND_TUTOR_ANSWER_EXPOSURE = "TUTOR_ANSWER_EXPOSURE_OUTCOME"

@Dao
internal abstract class TutorExposureDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertConversation(conversation: TutorConversationEntity): Long

    @Query("SELECT * FROM tutor_conversation WHERE conversation_id = :conversationId LIMIT 1")
    protected abstract suspend fun findConversation(conversationId: String): TutorConversationEntity?

    @Query(
        """
        UPDATE tutor_conversation
        SET anchor_learner_id = :learnerId,
            anchor_problem_revision_id = :problemRevisionId,
            anchor_practice_unit_id = :practiceUnitId,
            anchor_source = :source,
            anchored_at_epoch_millis = :anchoredAtEpochMillis,
            updated_at_epoch_millis = MAX(updated_at_epoch_millis, :anchoredAtEpochMillis)
        WHERE conversation_id = :conversationId
          AND (
            anchor_problem_revision_id IS NULL
            OR (
                anchor_problem_revision_id = :problemRevisionId
                AND anchor_practice_unit_id = :practiceUnitId
            )
          )
        """,
    )
    protected abstract suspend fun writeAnchor(
        conversationId: String,
        learnerId: String,
        problemRevisionId: String,
        practiceUnitId: String,
        source: String,
        anchoredAtEpochMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM tutor_conversation
        WHERE conversation_id = :conversationId AND anchor_problem_revision_id IS NOT NULL
        LIMIT 1
        """,
    )
    protected abstract suspend fun findAnchor(conversationId: String): TutorConversationEntity?

    /**
     * 该学习者最近一次锚定到这道题的讲题会话。讲题判定结算靠它把"刚讲完的会话"
     * 与"复习队列当前这一项"对上。
     *
     * 锚是**会话级**事实（D-K1 §5），51→52 之后落在 `tutor_conversation` 的锚块上：
     * 一个会话一个锚，读锚就是读会话行，不再有第二张表，也就不会出现"锚表有、会话行没有"
     * 的分叉。
     */
    @Query(
        """
        SELECT * FROM tutor_conversation
        WHERE anchor_practice_unit_id = :practiceUnitId AND anchor_learner_id = :learnerId
        ORDER BY anchored_at_epoch_millis DESC
        LIMIT 1
        """,
    )
    protected abstract suspend fun findLatestAnchorForPracticeUnit(
        practiceUnitId: String,
        learnerId: String,
    ): TutorConversationEntity?

    /** 结算用的公开读取：把锚定记录映射成端口记录，找不到返回 null。 */
    open suspend fun readLatestAnchorForPracticeUnit(
        practiceUnitId: String,
        learnerId: String,
    ): TutorSessionProblemAnchorRecord? =
        findLatestAnchorForPracticeUnit(practiceUnitId, learnerId)?.toAnchorRecord()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertExposure(exposure: TutorAnswerExposureEntity): Long

    @Query(
        """
        SELECT * FROM tutor_answer_exposure
        WHERE model_task_request_id = :modelTaskRequestId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findExposure(
        modelTaskRequestId: String,
    ): TutorAnswerExposureEntity?

    @Query(
        """
        SELECT * FROM tutor_answer_exposure
        WHERE model_task_request_id IN (:modelTaskRequestIds)
        """,
    )
    protected abstract suspend fun findExposures(
        modelTaskRequestIds: Set<String>,
    ): List<TutorAnswerExposureEntity>

    @Query("SELECT * FROM model_task WHERE request_id = :requestId LIMIT 1")
    protected abstract suspend fun findModelTask(requestId: String): ModelTaskEntity?

    /**
     * 本轮的讲题轮次行（51→52 之后在 `tutor_message` 上，见 [TutorInteractionDao]）。
     * 曝光面校验只多需要"该轮是否已揭示解法"与题面口径——读轮次行，不另存第二份。
     */
    @Query(
        """
        SELECT * FROM tutor_message
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal = :cycleOrdinal
          AND round_turn_ordinal = :turnOrdinal
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRoundRow(
        conversationId: String,
        cycleOrdinal: Int,
        turnOrdinal: Int,
    ): TutorMessageEntity?

    @Query("SELECT * FROM tutor_answer_exposure_outcome WHERE exposure_id = :exposureId LIMIT 1")
    internal abstract suspend fun findOutcomeByExposure(
        exposureId: String,
    ): TutorAnswerExposureOutcomeEntity?

    @Query("SELECT * FROM tutor_answer_exposure_outcome WHERE outcome_id = :outcomeId LIMIT 1")
    internal abstract suspend fun findOutcome(
        outcomeId: String,
    ): TutorAnswerExposureOutcomeEntity?

    @Query(
        """
        SELECT exposure.*
        FROM tutor_answer_exposure AS exposure
        JOIN tutor_conversation AS anchor ON anchor.conversation_id = :conversationId
        LEFT JOIN tutor_answer_exposure_outcome AS outcome ON outcome.exposure_id = exposure.exposure_id
        WHERE exposure.session_id = :sessionId
          AND anchor.anchor_problem_revision_id IS NOT NULL
          AND outcome.exposure_id IS NULL
        ORDER BY exposure.exposed_at_epoch_millis ASC, exposure.exposure_id ASC
        """,
    )
    protected abstract suspend fun findPendingForSession(
        sessionId: String,
        conversationId: String,
    ): List<TutorAnswerExposureEntity>

    @Query(
        """
        SELECT exposure.*
        FROM tutor_answer_exposure AS exposure
        JOIN tutor_conversation AS anchor
          ON anchor.conversation_id = :capturedConversationPrefix || exposure.session_id
        LEFT JOIN tutor_answer_exposure_outcome AS outcome ON outcome.exposure_id = exposure.exposure_id
        WHERE exposure.learner_id = :learnerId
          AND anchor.anchor_problem_revision_id IS NOT NULL
          AND outcome.exposure_id IS NULL
        ORDER BY exposure.exposed_at_epoch_millis ASC, exposure.exposure_id ASC
        LIMIT :limit
        """,
    )
    protected abstract suspend fun findPendingForLearner(
        learnerId: String,
        capturedConversationPrefix: String,
        limit: Int,
    ): List<TutorAnswerExposureEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOutcome(outcome: TutorAnswerExposureOutcomeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOutbox(outbox: ProjectionOutboxEntity)

    @Query(
        """
        SELECT * FROM projection_outbox
        WHERE event_kind = :eventKind AND event_id = :eventId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findOutbox(
        eventKind: String,
        eventId: String,
    ): ProjectionOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun initializeSequence(sequence: LearningSequenceEntity): Long

    @Query("SELECT last_allocated_sequence FROM learning_sequence WHERE learner_id = :learnerId")
    protected abstract suspend fun lastAllocatedSequence(learnerId: String): Long?

    @Query(
        """
        UPDATE learning_sequence SET last_allocated_sequence = :next
        WHERE learner_id = :learnerId AND last_allocated_sequence = :expected
        """,
    )
    protected abstract suspend fun compareAndSetSequence(
        learnerId: String,
        expected: Long,
        next: Long,
    ): Int

    /**
     * 锚定一个会话（原 `tutor_session_problem_anchor` 的写入，51→52 之后落在会话行上）。
     *
     * 幂等：同一个会话 + 同一道题重复锚定是成功的空操作；同一个会话锚到**另一道题**是冲突
     * （锚是全会话唯一的事实，能改就等于历史可以被改写）。
     */
    @Transaction
    open suspend fun bindAnchor(
        command: PersistTutorSessionAnchorCommand,
    ): TutorSessionProblemAnchorRecord {
        val conversationId = conversationIdOf(command.sessionId)
        findConversation(conversationId) ?: run {
            // 会话行通常已由讲题区按需建过；这里兜底建行时把**锚修订**填成这次锚定的题修订
            // （错题讲题的锚就是那道题的修订），会话锚三列随后由 writeAnchor 补齐。
            insertConversation(
                TutorConversationEntity(
                    conversationId = conversationId,
                    conversationArea = TUTOR_CONVERSATION_AREA_AGENT,
                    anchorKind = "EPHEMERAL_DRAFT",
                    anchorId = command.sessionId,
                    anchorRevisionId = command.problemRevisionId,
                    status = "ACTIVE",
                    title = null,
                    createdAtEpochMillis = command.anchoredAtEpochMillis,
                    updatedAtEpochMillis = command.anchoredAtEpochMillis,
                    lastTurnOrdinal = 0,
                    studentDraft = null,
                ),
            )
        }
        writeAnchor(
            conversationId = conversationId,
            learnerId = command.learnerId,
            problemRevisionId = command.problemRevisionId,
            practiceUnitId = command.practiceUnitId,
            source = command.source,
            anchoredAtEpochMillis = command.anchoredAtEpochMillis,
        )
        val stored = checkNotNull(findAnchor(conversationId))
        if (stored.anchorLearnerId != command.learnerId ||
            stored.anchorProblemRevisionId != command.problemRevisionId ||
            stored.anchorPracticeUnitId != command.practiceUnitId
        ) {
            throw ImmutablePayloadConflictException("tutor_conversation_anchor", command.sessionId)
        }
        findPendingForSession(command.sessionId, conversationId).forEach { exposure ->
            materialize(exposure, stored)
        }
        return stored.toAnchorRecord()
    }

    @Transaction
    open suspend fun recordVisibleExposure(
        command: PersistTutorAnswerExposureCommand,
    ): TutorAnswerExposureRecord {
        validateVisibleSurface(command)
        val candidate = command.toExposureEntity()
        insertExposure(candidate)
        val stored = checkNotNull(findExposure(command.modelTaskRequestId))
        if (!stored.sameIdentity(candidate)) {
            throw ImmutablePayloadConflictException(
                "tutor_answer_exposure",
                command.modelTaskRequestId,
            )
        }
        findAnchor(conversationIdOf(command.sessionId))?.let { anchor ->
            materialize(stored, anchor)
        }
        return stored.toRecord(findOutcomeByExposure(stored.exposureId)?.outcomeId)
    }

    @Transaction
    open suspend fun reconcilePending(learnerId: String, limit: Int): Int {
        require(learnerId.isNotBlank())
        require(limit in 1..1_000)
        var reconciled = 0
        while (true) {
            val pending = findPendingForLearner(learnerId, TutorConversationIds.CAPTURED_PREFIX, limit)
            if (pending.isEmpty()) return reconciled
            pending.forEach { exposure ->
                val conversationId = conversationIdOf(exposure.sessionId)
                val anchor = checkNotNull(findAnchor(conversationId)) {
                    "Anchored pending tutor exposure lost its session anchor"
                }
                if (materialize(exposure, anchor)) reconciled++
            }
        }
    }

    @Transaction
    open suspend fun readExposure(
        modelTaskRequestId: String,
    ): TutorAnswerExposureRecord? {
        val exposure = findExposure(modelTaskRequestId) ?: return null
        return exposure.toRecord(findOutcomeByExposure(exposure.exposureId)?.outcomeId)
    }

    open suspend fun readExposures(
        modelTaskRequestIds: Set<String>,
    ): List<TutorAnswerExposureRecord> {
        if (modelTaskRequestIds.isEmpty()) return emptyList()
        require(modelTaskRequestIds.none(String::isBlank))
        return findExposures(modelTaskRequestIds).map { exposure ->
            exposure.toRecord(outcomeId = null)
        }
    }

    private suspend fun validateVisibleSurface(command: PersistTutorAnswerExposureCommand) {
        val task = findModelTask(command.modelTaskRequestId)
            ?: throw ImmutablePayloadConflictException(
                "tutor_answer_exposure_model_task",
                command.modelTaskRequestId,
            )
        val request = ModelTaskCodec.decodeRequest(task.requestSnapshot)
        val output = task.outputSnapshot?.let(ModelTaskCodec::decodeOutput)
        val commonIdentityMatches = task.status == ModelTaskStatus.SUCCEEDED.name &&
            request.requestId == command.modelTaskRequestId &&
            command.occurredAtEpochMillis >= task.updatedAtEpochMillis
        val surfaceMatches = when (command.surfaceKind) {
            "PLAN_SOLUTION" -> {
                val input = request.input as? TutorPlanInput
                val turn = findRoundRow(
                    conversationIdOf(command.sessionId),
                    command.cycleOrdinal,
                    command.turnOrdinal,
                )
                input != null && output is TutorPlanOutput && command.responseOrdinal == null &&
                    input.sessionId == command.sessionId &&
                    input.questionDocument.id == command.questionDocumentId &&
                    input.draftRevisionNumber == command.revisionNumber &&
                    input.cycleOrdinal == command.cycleOrdinal &&
                    input.turnOrdinal == command.turnOrdinal &&
                    turn != null &&
                    turn.roundQuestionDocumentId == command.questionDocumentId &&
                    turn.roundRevisionNumber == command.revisionNumber &&
                    turn.solutionRevealed &&
                    command.occurredAtEpochMillis >=
                    (turn.completedAtEpochMillis ?: turn.createdAtEpochMillis)
            }
            "RESPOND_REPLY" -> {
                val input = request.input as? TutorRespondInput
                val respondOutput = output as? TutorRespondOutput
                input != null &&
                    respondOutput?.canExposeSolutionFor(
                        input,
                        requiresRoundQuestionBinding = request.requiresRoundQuestionBinding,
                    ) == true &&
                    input.sessionId == command.sessionId &&
                    input.questionDocument.id == command.questionDocumentId &&
                    input.draftRevisionNumber == command.revisionNumber &&
                    input.cycleOrdinal == command.cycleOrdinal &&
                    input.turnOrdinal == command.turnOrdinal &&
                    input.responseOrdinal == command.responseOrdinal
            }
            else -> false
        }
        if (!commonIdentityMatches || !surfaceMatches) {
            throw ImmutablePayloadConflictException(
                "tutor_answer_exposure_surface",
                command.modelTaskRequestId,
            )
        }
    }

    /**
     * 把一次已确认的曝光物化成账本行。锚（会话行）提供题目与练习单元，轮次行提供本轮选择题的
     * 本地判对结果——两个都是**快照**：账本行一旦落库就不再随会话/轮次改动而变，所以
     * [TutorAnswerExposureOutcomeEntity.selectionWasCorrect] 与锚来源列不参与
     * [LearningLedgerFingerprint]（它们是随附事实，不是账本身份）。
     */
    private suspend fun materialize(
        exposure: TutorAnswerExposureEntity,
        anchor: TutorConversationEntity,
    ): Boolean {
        val anchorLearnerId = requireNotNull(anchor.anchorLearnerId) {
            "Anchored tutor conversation lost its learner"
        }
        if (exposure.learnerId != anchorLearnerId) {
            throw ImmutablePayloadConflictException("tutor_answer_exposure_learner", exposure.exposureId)
        }
        findOutcomeByExposure(exposure.exposureId)?.let { existing ->
            verifyMaterialized(existing)
            return false
        }
        val sequence = allocateSequence(anchorLearnerId)
        val outcome = TutorAnswerExposureOutcome(
            outcomeId = outcomeId(exposure.exposureId),
            exposureId = exposure.exposureId,
            sessionId = exposure.sessionId,
            questionDocumentId = exposure.questionDocumentId,
            questionRevisionNumber = exposure.questionRevisionNumber,
            cycleOrdinal = exposure.cycleOrdinal,
            turnOrdinal = exposure.turnOrdinal,
            problemRevisionId = requireNotNull(anchor.anchorProblemRevisionId),
            practiceUnitId = requireNotNull(anchor.anchorPracticeUnitId),
            occurredAtEpochMillis = exposure.exposedAtEpochMillis,
            eventSequence = sequence,
        )
        val fingerprint = LearningLedgerFingerprint.tutorAnswerExposure(outcome)
        val round = findRoundRow(
            conversationId = anchor.conversationId,
            cycleOrdinal = exposure.cycleOrdinal,
            turnOrdinal = exposure.turnOrdinal,
        )
        val entity = outcome.toEntity(
            learnerId = anchorLearnerId,
            canonicalFingerprint = fingerprint,
            selectionWasCorrect = round?.choiceWasCorrect,
            anchorSource = anchor.anchorSource,
            anchoredAtEpochMillis = anchor.anchoredAtEpochMillis,
        )
        insertOutcome(entity)
        insertOutbox(entity.toOutbox())
        return true
    }

    private suspend fun verifyMaterialized(entity: TutorAnswerExposureOutcomeEntity) {
        val outcome = entity.toModel()
        if (LearningLedgerFingerprint.tutorAnswerExposure(outcome) != entity.canonicalFingerprint ||
            findOutbox(EVENT_KIND_TUTOR_ANSWER_EXPOSURE, entity.outcomeId) != entity.toOutbox()
        ) {
            throw ImmutablePayloadConflictException("tutor_answer_exposure_outcome", entity.outcomeId)
        }
    }

    private suspend fun allocateSequence(learnerId: String): Long {
        initializeSequence(LearningSequenceEntity(learnerId, 0))
        val current = checkNotNull(lastAllocatedSequence(learnerId))
        check(current < Long.MAX_VALUE) { "Learning sequence exhausted for $learnerId" }
        val next = current + 1
        check(compareAndSetSequence(learnerId, current, next) == 1) {
            "Learning sequence CAS failed inside a serialized Room transaction"
        }
        return next
    }
}

private fun PersistTutorAnswerExposureCommand.toExposureEntity(): TutorAnswerExposureEntity {
    val identity = "$sessionId\n$cycleOrdinal\n$turnOrdinal\n$surfaceKind\n" +
        "$modelTaskRequestId\n${responseOrdinal ?: 0}"
    return TutorAnswerExposureEntity(
        exposureId = "tutor-answer-exposure:${sha256(identity)}",
        learnerId = learnerId,
        sessionId = sessionId,
        questionDocumentId = questionDocumentId,
        questionRevisionNumber = revisionNumber,
        cycleOrdinal = cycleOrdinal,
        turnOrdinal = turnOrdinal,
        surfaceKind = surfaceKind,
        modelTaskRequestId = modelTaskRequestId,
        responseOrdinal = responseOrdinal,
        exposedAtEpochMillis = occurredAtEpochMillis,
    )
}

private fun TutorAnswerExposureEntity.sameIdentity(other: TutorAnswerExposureEntity): Boolean =
    exposureId == other.exposureId && learnerId == other.learnerId && sessionId == other.sessionId &&
        questionDocumentId == other.questionDocumentId &&
        questionRevisionNumber == other.questionRevisionNumber &&
        cycleOrdinal == other.cycleOrdinal && turnOrdinal == other.turnOrdinal &&
        surfaceKind == other.surfaceKind && modelTaskRequestId == other.modelTaskRequestId &&
        responseOrdinal == other.responseOrdinal

private fun TutorConversationEntity.toAnchorRecord() = TutorSessionProblemAnchorRecord(
    learnerId = requireNotNull(anchorLearnerId),
    sessionId = requireNotNull(anchorId),
    problemRevisionId = requireNotNull(anchorProblemRevisionId),
    practiceUnitId = requireNotNull(anchorPracticeUnitId),
    source = requireNotNull(anchorSource),
    anchoredAtEpochMillis = requireNotNull(anchoredAtEpochMillis),
)

private fun TutorAnswerExposureEntity.toRecord(outcomeId: String?) = TutorAnswerExposureRecord(
    exposureId = exposureId,
    learnerId = learnerId,
    sessionId = sessionId,
    questionDocumentId = questionDocumentId,
    questionRevisionNumber = questionRevisionNumber,
    cycleOrdinal = cycleOrdinal,
    turnOrdinal = turnOrdinal,
    surfaceKind = surfaceKind,
    modelTaskRequestId = modelTaskRequestId,
    responseOrdinal = responseOrdinal,
    exposedAtEpochMillis = exposedAtEpochMillis,
    outcomeId = outcomeId,
)

private fun TutorAnswerExposureOutcome.toEntity(
    learnerId: String,
    canonicalFingerprint: String,
    selectionWasCorrect: Boolean?,
    anchorSource: String?,
    anchoredAtEpochMillis: Long?,
) = TutorAnswerExposureOutcomeEntity(
    outcomeId = outcomeId,
    exposureId = exposureId,
    learnerId = learnerId,
    sessionId = sessionId,
    questionDocumentId = questionDocumentId,
    questionRevisionNumber = questionRevisionNumber,
    cycleOrdinal = cycleOrdinal,
    turnOrdinal = turnOrdinal,
    problemRevisionId = problemRevisionId,
    practiceUnitId = practiceUnitId,
    selectionWasCorrect = selectionWasCorrect,
    anchorSource = anchorSource,
    anchoredAtEpochMillis = anchoredAtEpochMillis,
    eventSequence = eventSequence,
    canonicalFingerprint = canonicalFingerprint,
    occurredAtEpochMillis = occurredAtEpochMillis,
)

internal fun TutorAnswerExposureOutcomeEntity.toModel() = TutorAnswerExposureOutcome(
    outcomeId = outcomeId,
    exposureId = exposureId,
    sessionId = sessionId,
    questionDocumentId = questionDocumentId,
    questionRevisionNumber = questionRevisionNumber,
    cycleOrdinal = cycleOrdinal,
    turnOrdinal = turnOrdinal,
    problemRevisionId = problemRevisionId,
    practiceUnitId = practiceUnitId,
    occurredAtEpochMillis = occurredAtEpochMillis,
    eventSequence = eventSequence,
)

internal fun TutorAnswerExposureOutcomeEntity.toOutbox() = ProjectionOutboxEntity(
    outboxId = "learning-outbox:$learnerId:$EVENT_KIND_TUTOR_ANSWER_EXPOSURE:$outcomeId",
    learnerId = learnerId,
    outboxSequence = eventSequence,
    eventKind = EVENT_KIND_TUTOR_ANSWER_EXPOSURE,
    eventId = outcomeId,
    canonicalFingerprint = canonicalFingerprint,
    status = StudyDbValue.OutboxStatus.PENDING,
    createdAtEpochMillis = occurredAtEpochMillis,
)

private fun outcomeId(exposureId: String): String =
    "tutor-answer-exposure-outcome:${sha256(exposureId)}"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
