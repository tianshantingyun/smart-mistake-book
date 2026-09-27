package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.PersistTutorChoiceCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorMoveCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorRevealCommand
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.database.entity.TutorConversationEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorMessageEntity
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 轮次行的角色：本地事件，不是对话消息（不被 [com.tingyun.smartmistakebook.core.domain.TutorContextComposer] 回喂模型）。 */
internal const val TUTOR_ROUND_MESSAGE_ROLE = "LOCAL_EVENT"

/**
 * 讲题轮次事实（原 `tutor_turn_response`）的读写，51→52 之后落在 [TutorMessageEntity] 上。
 *
 * 一轮一行：`(conversation_id, round_cycle_ordinal, round_turn_ordinal)` 定位（唯一索引），
 * `message_id` 由该三元组确定性派生，所以幂等重放拿到的是同一行。
 *
 * **行按需创建**：讲题区一轮可能只发生"学生点了一个选项"，此前没有任何一方为它写过消息行；
 * 会话行（`tutor_conversation`）也在同一事务里按需创建——`tutor_message.conversation_id` 上
 * 有外键，缺行会让整个写入抛 787。建行是"这一轮真的发生了"的记账，不是空会话落库（K1b）。
 */
@Dao
internal abstract class TutorInteractionDao {
    @Query(
        """
        SELECT * FROM tutor_message
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal IS NOT NULL
          AND round_turn_ordinal IS NOT NULL
        ORDER BY round_cycle_ordinal ASC, round_turn_ordinal ASC
        """,
    )
    protected abstract fun observeRoundEntities(
        conversationId: String,
    ): Flow<List<TutorMessageEntity>>

    fun observe(sessionId: String): Flow<List<TutorTurnResponseRecord>> =
        observeRoundEntities(conversationIdOf(sessionId)).map { rows ->
            rows.map { row -> row.toTurnRecord(sessionId) }
        }

    @Query(
        """
        SELECT * FROM tutor_message
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal = :cycleOrdinal
          AND round_turn_ordinal = :turnOrdinal
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRoundEntity(
        conversationId: String,
        cycleOrdinal: Int,
        turnOrdinal: Int,
    ): TutorMessageEntity?

    @Query("SELECT * FROM tutor_conversation WHERE conversation_id = :conversationId LIMIT 1")
    protected abstract suspend fun findConversationEntity(
        conversationId: String,
    ): TutorConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertConversation(entity: TutorConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMessage(entity: TutorMessageEntity): Long

    @Query(
        """
        UPDATE tutor_conversation
        SET last_turn_ordinal = MAX(last_turn_ordinal, :ordinal),
            updated_at_epoch_millis = MAX(updated_at_epoch_millis, :updatedAtEpochMillis)
        WHERE conversation_id = :conversationId
        """,
    )
    protected abstract suspend fun touchConversation(
        conversationId: String,
        ordinal: Int,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE tutor_message
        SET choice_stem_markdown = :diagnosticStemMarkdown,
            choice_selected_id = :selectedChoiceId,
            choice_selected_markdown = :selectedChoiceMarkdown,
            choice_was_correct = :selectionWasCorrect,
            choice_feedback_markdown = :feedbackMarkdown
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal = :cycleOrdinal
          AND round_turn_ordinal = :turnOrdinal
          AND round_question_document_id = :questionDocumentId
          AND round_revision_number = :revisionNumber
          AND choice_stem_markdown IS NULL
          AND choice_selected_id IS NULL
          AND choice_selected_markdown IS NULL
          AND choice_was_correct IS NULL
          AND choice_feedback_markdown IS NULL
        """,
    )
    protected abstract suspend fun updateChoicePayload(
        conversationId: String,
        questionDocumentId: String,
        revisionNumber: Int,
        cycleOrdinal: Int,
        turnOrdinal: Int,
        diagnosticStemMarkdown: String,
        selectedChoiceId: String,
        selectedChoiceMarkdown: String,
        selectionWasCorrect: Boolean,
        feedbackMarkdown: String,
    ): Int

    @Query(
        """
        UPDATE tutor_message
        SET requested_move = :requestedMove,
            completed_at_epoch_millis = MAX(
                COALESCE(completed_at_epoch_millis, :occurredAtEpochMillis),
                :occurredAtEpochMillis
            )
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal = :cycleOrdinal
          AND round_turn_ordinal = :turnOrdinal
          AND requested_move IS NULL
        """,
    )
    protected abstract suspend fun updateMove(
        conversationId: String,
        cycleOrdinal: Int,
        turnOrdinal: Int,
        requestedMove: String,
        occurredAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE tutor_message
        SET solution_revealed = 1,
            completed_at_epoch_millis = MAX(
                COALESCE(completed_at_epoch_millis, :occurredAtEpochMillis),
                :occurredAtEpochMillis
            )
        WHERE conversation_id = :conversationId
          AND round_cycle_ordinal = :cycleOrdinal
          AND round_turn_ordinal = :turnOrdinal
          AND solution_revealed = 0
        """,
    )
    protected abstract suspend fun markSolutionRevealed(
        conversationId: String,
        cycleOrdinal: Int,
        turnOrdinal: Int,
        occurredAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun recordChoice(command: PersistTutorChoiceCommand): TutorTurnResponseRecord {
        val row = ensureRoundRow(
            sessionId = command.sessionId,
            questionDocumentId = command.questionDocumentId,
            revisionNumber = command.revisionNumber,
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            occurredAtEpochMillis = command.choiceSubmittedAtEpochMillis,
        )
        row.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
        if (row.hasSameChoicePayload(command)) return row.toTurnRecord(command.sessionId)
        if (row.hasChoicePayload) {
            throw ImmutablePayloadConflictException(
                "tutor_turn_response",
                "${command.sessionId}:${command.cycleOrdinal}:${command.turnOrdinal}",
            )
        }
        updateChoicePayload(
            conversationId = conversationIdOf(command.sessionId),
            questionDocumentId = command.questionDocumentId,
            revisionNumber = command.revisionNumber,
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            diagnosticStemMarkdown = command.diagnosticStemMarkdown,
            selectedChoiceId = command.selectedChoiceId,
            selectedChoiceMarkdown = command.selectedChoiceMarkdown,
            selectionWasCorrect = command.selectionWasCorrect,
            feedbackMarkdown = command.feedbackMarkdown,
        )
        val stored = readRound(
            command.sessionId,
            command.cycleOrdinal,
            command.turnOrdinal,
        )
        stored.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
        if (!stored.hasSameChoicePayload(command)) {
            throw ImmutablePayloadConflictException(
                "tutor_turn_response",
                "${command.sessionId}:${command.cycleOrdinal}:${command.turnOrdinal}",
            )
        }
        return stored.toTurnRecord(command.sessionId)
    }

    @Transaction
    open suspend fun recordMove(command: PersistTutorMoveCommand): TutorTurnResponseRecord {
        val row = ensureRoundRow(
            sessionId = command.sessionId,
            questionDocumentId = command.questionDocumentId,
            revisionNumber = command.revisionNumber,
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
        )
        row.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
        if (row.hasChoicePayload) {
            require(command.occurredAtEpochMillis >= row.createdAtEpochMillis)
        }
        row.requestedMove?.let { storedMove ->
            if (storedMove != command.requestedMove) {
                throw ImmutablePayloadConflictException(
                    "tutor_requested_move",
                    "${command.sessionId}:${command.cycleOrdinal}:${command.turnOrdinal}",
                )
            }
            return row.toTurnRecord(command.sessionId)
        }
        val updated = updateMove(
            conversationId = conversationIdOf(command.sessionId),
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            requestedMove = command.requestedMove,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
        )
        if (updated != 1) {
            val raced = readRound(
                command.sessionId,
                command.cycleOrdinal,
                command.turnOrdinal,
            )
            raced.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
            if (raced.requestedMove == command.requestedMove) return raced.toTurnRecord(command.sessionId)
            throw ImmutablePayloadConflictException(
                "tutor_requested_move",
                "${command.sessionId}:${command.cycleOrdinal}:${command.turnOrdinal}",
            )
        }
        return readRound(command.sessionId, command.cycleOrdinal, command.turnOrdinal)
            .toTurnRecord(command.sessionId)
    }

    @Transaction
    open suspend fun revealSolution(command: PersistTutorRevealCommand): TutorTurnResponseRecord {
        val row = ensureRoundRow(
            sessionId = command.sessionId,
            questionDocumentId = command.questionDocumentId,
            revisionNumber = command.revisionNumber,
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
        )
        row.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
        if (row.hasChoicePayload) {
            require(command.occurredAtEpochMillis >= row.createdAtEpochMillis)
        }
        if (row.solutionRevealed) return row.toTurnRecord(command.sessionId)
        val updated = markSolutionRevealed(
            conversationId = conversationIdOf(command.sessionId),
            cycleOrdinal = command.cycleOrdinal,
            turnOrdinal = command.turnOrdinal,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
        )
        if (updated != 1) {
            val raced = readRound(
                command.sessionId,
                command.cycleOrdinal,
                command.turnOrdinal,
            )
            raced.requireSameQuestion(command.questionDocumentId, command.revisionNumber)
            check(raced.solutionRevealed) { "Tutor solution reveal was not persisted" }
            return raced.toTurnRecord(command.sessionId)
        }
        return readRound(command.sessionId, command.cycleOrdinal, command.turnOrdinal)
            .toTurnRecord(command.sessionId)
    }

    private suspend fun readRound(
        sessionId: String,
        cycleOrdinal: Int,
        turnOrdinal: Int,
    ): TutorMessageEntity = checkNotNull(
        findRoundEntity(conversationIdOf(sessionId), cycleOrdinal, turnOrdinal),
    ) {
        "Tutor round row was not readable"
    }

    /**
     * 拿到（必要时创建）本轮的行。会话行缺失时一并建：
     * 讲题会话此前没有任何一方建过会话行（`tutor_message` 的外键会让每次写入都抛 787）。
     * 会话锚按与界面同一口径派生（拍照会话 = 会话 id；题面修订 = `questionDocumentId:revision`），
     * 已存在时**原样复用**，不校验、不改写（会话锚的主人是创建方）。
     */
    private suspend fun ensureRoundRow(
        sessionId: String,
        questionDocumentId: String,
        revisionNumber: Int,
        cycleOrdinal: Int,
        turnOrdinal: Int,
        occurredAtEpochMillis: Long,
    ): TutorMessageEntity {
        val conversationId = conversationIdOf(sessionId)
        val conversation = findConversationEntity(conversationId) ?: run {
            insertConversation(
                TutorConversationEntity(
                    conversationId = conversationId,
                    conversationArea = TUTOR_CONVERSATION_AREA_AGENT,
                    anchorKind = "EPHEMERAL_DRAFT",
                    anchorId = sessionId,
                    anchorRevisionId = "$questionDocumentId:$revisionNumber",
                    status = "ACTIVE",
                    title = null,
                    createdAtEpochMillis = occurredAtEpochMillis,
                    updatedAtEpochMillis = occurredAtEpochMillis,
                    lastTurnOrdinal = 0,
                    studentDraft = null,
                ),
            )
            checkNotNull(findConversationEntity(conversationId)) {
                "Tutor conversation row was not readable"
            }
        }
        findRoundEntity(conversationId, cycleOrdinal, turnOrdinal)?.let { return it }
        val ordinal = conversation.lastTurnOrdinal + 1
        insertMessage(
            TutorMessageEntity(
                messageId = tutorRoundMessageId(sessionId, cycleOrdinal, turnOrdinal),
                conversationId = conversationId,
                ordinal = ordinal,
                role = TUTOR_ROUND_MESSAGE_ROLE,
                bodyMarkdown = "",
                roundCycleOrdinal = cycleOrdinal,
                roundTurnOrdinal = turnOrdinal,
                roundQuestionDocumentId = questionDocumentId,
                roundRevisionNumber = revisionNumber,
                status = "PERSISTED",
                logicalOperationId = null,
                replyToMessageId = null,
                createdAtEpochMillis = occurredAtEpochMillis,
                completedAtEpochMillis = occurredAtEpochMillis,
                errorCode = null,
            ),
        )
        touchConversation(
            conversationId = conversationId,
            ordinal = ordinal,
            updatedAtEpochMillis = occurredAtEpochMillis,
        )
        return readRound(sessionId, cycleOrdinal, turnOrdinal)
    }

}

/** 会话区（K1）默认值：智能体栏。复习栏两个入口由创建方显式给出 REVIEW_*。 */
internal const val TUTOR_CONVERSATION_AREA_AGENT = "AGENT"

internal fun conversationIdOf(sessionId: String): String = TutorConversationIds.captured(sessionId)

/**
 * 轮次行的主键由三元组确定性派生：同一次转发（恢复重放同一轮）拿到同一行，
 * 不会因为重试而多出一行。
 */
internal fun tutorRoundMessageId(
    sessionId: String,
    cycleOrdinal: Int,
    turnOrdinal: Int,
): String = "tutor-round:$sessionId:$cycleOrdinal:$turnOrdinal"

private fun TutorMessageEntity.hasSameChoicePayload(
    command: PersistTutorChoiceCommand,
): Boolean = choiceStemMarkdown == command.diagnosticStemMarkdown &&
    choiceSelectedId == command.selectedChoiceId &&
    choiceSelectedMarkdown == command.selectedChoiceMarkdown &&
    choiceWasCorrect == command.selectionWasCorrect &&
    choiceFeedbackMarkdown == command.feedbackMarkdown

private val TutorMessageEntity.hasChoicePayload: Boolean
    get() = choiceStemMarkdown != null

private fun TutorMessageEntity.requireSameQuestion(
    expectedQuestionDocumentId: String,
    expectedRevisionNumber: Int,
) {
    if (
        roundQuestionDocumentId != expectedQuestionDocumentId ||
        roundRevisionNumber != expectedRevisionNumber
    ) {
        throw ImmutablePayloadConflictException(
            "tutor_turn_response",
            "$conversationId:$roundCycleOrdinal:$roundTurnOrdinal",
        )
    }
}

/**
 * 轮次行 → 轮次记录。
 *
 * 时间口径：`created_at` = 本轮第一次动作的时刻（学生提交选择 / 选择动作），
 * `completed_at` = 后续动作推进到的时刻；记录里的 `choiceSubmittedAt` 只在确实有选择载荷时给出，
 * 与旧表 `choice_submitted_at_epoch_millis` 同义。
 */
internal fun TutorMessageEntity.toTurnRecord(sessionId: String) = TutorTurnResponseRecord(
    sessionId = sessionId,
    questionDocumentId = requireNotNull(roundQuestionDocumentId),
    revisionNumber = requireNotNull(roundRevisionNumber),
    cycleOrdinal = requireNotNull(roundCycleOrdinal),
    turnOrdinal = requireNotNull(roundTurnOrdinal),
    diagnosticStemMarkdown = choiceStemMarkdown,
    selectedChoiceId = choiceSelectedId,
    selectedChoiceMarkdown = choiceSelectedMarkdown,
    selectionWasCorrect = choiceWasCorrect,
    feedbackMarkdown = choiceFeedbackMarkdown,
    requestedMove = requestedMove,
    solutionRevealed = solutionRevealed,
    choiceSubmittedAtEpochMillis = createdAtEpochMillis.takeIf { hasChoicePayload },
    submittedAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = completedAtEpochMillis ?: createdAtEpochMillis,
)
