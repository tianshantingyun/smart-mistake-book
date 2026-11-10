package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.database.PersistTutorChoiceCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorAnswerExposureCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorMoveCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorRevealCommand
import com.tingyun.smartmistakebook.core.database.PersistTutorSessionAnchorCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.TutorAnswerExposureRecord
import com.tingyun.smartmistakebook.core.database.TutorTurnResponseRecord
import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorMoveCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorSolutionExposureCommand
import com.tingyun.smartmistakebook.core.domain.RevealTutorSolutionCommand
import com.tingyun.smartmistakebook.core.domain.TutorAnswerExposureKey
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorSessionProblemAnchor
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.tingyun.smartmistakebook.core.model.TutorConversationIds
import kotlinx.coroutines.withContext

internal class RoomTutorInteractionRepository(
    private val database: StudyDatabasePort,
    private val learnerId: String = "learner:local",
) : TutorInteractionRepository {
    override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> {
        require(sessionId.isNotBlank())
        return combine(database.observeTutorTurnResponses(sessionId),
            database.observeTutorReplyVersions(TutorConversationIds.captured(sessionId))) { records, versions ->
            projectReplyVersionResponses(records.map(TutorTurnResponseRecord::toDomain), versions.map { it.toDomainVersion() })
        }
    }

    override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse =
        withContext(Dispatchers.IO) {
            if (command.replyVersionId != null) {
                return@withContext updateVersionAnswer(command.sessionId, requireNotNull(command.replyVersionId)) { previous ->
                    if (previous?.hasChoicePayload == true) {
                        require(previous.selectedChoiceId == command.selectedChoiceId) { "This version already has an answer" }
                        previous
                    } else TutorTurnResponse(
                        sessionId = command.sessionId, questionDocumentId = command.questionDocumentId,
                        revisionNumber = command.revisionNumber, cycleOrdinal = command.cycleOrdinal,
                        turnOrdinal = command.turnOrdinal, diagnosticStemMarkdown = command.diagnosticStemMarkdown,
                        selectedChoiceId = command.selectedChoiceId, selectedChoiceMarkdown = command.selectedChoiceMarkdown,
                        selectionWasCorrect = command.selectionWasCorrect, feedbackMarkdown = command.feedbackMarkdown,
                        requestedMove = previous?.requestedMove, solutionRevealed = previous?.solutionRevealed == true,
                        submittedAtEpochMillis = previous?.submittedAtEpochMillis ?: command.occurredAtEpochMillis,
                        updatedAtEpochMillis = command.occurredAtEpochMillis, replyVersionId = command.replyVersionId,
                        choiceSubmittedAtEpochMillis = command.occurredAtEpochMillis,
                    )
                }
            }
            database.recordTutorChoice(
                PersistTutorChoiceCommand(
                    sessionId = command.sessionId,
                    questionDocumentId = command.questionDocumentId,
                    revisionNumber = command.revisionNumber,
                    cycleOrdinal = command.cycleOrdinal,
                    turnOrdinal = command.turnOrdinal,
                    diagnosticStemMarkdown = command.diagnosticStemMarkdown,
                    selectedChoiceId = command.selectedChoiceId,
                    selectedChoiceMarkdown = command.selectedChoiceMarkdown,
                    selectionWasCorrect = command.selectionWasCorrect,
                    feedbackMarkdown = command.feedbackMarkdown,
                    choiceSubmittedAtEpochMillis = command.occurredAtEpochMillis,
                ),
            ).toDomain()
        }

    override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
        withContext(Dispatchers.IO) {
            require(command.requestedMove != TutorMoveType.REVEAL_SOLUTION)
            if (command.replyVersionId != null) {
                return@withContext updateVersionAnswer(command.sessionId, requireNotNull(command.replyVersionId)) { previous ->
                    previous?.copy(requestedMove = command.requestedMove, updatedAtEpochMillis = command.occurredAtEpochMillis)
                        ?: TutorTurnResponse(command.sessionId, command.questionDocumentId, command.revisionNumber,
                            command.cycleOrdinal, command.turnOrdinal, null, null, null, null, null,
                            requestedMove = command.requestedMove, submittedAtEpochMillis = command.occurredAtEpochMillis,
                            updatedAtEpochMillis = command.occurredAtEpochMillis, replyVersionId = command.replyVersionId)
                }
            }
            database.recordTutorMove(
                PersistTutorMoveCommand(
                    sessionId = command.sessionId,
                    questionDocumentId = command.questionDocumentId,
                    revisionNumber = command.revisionNumber,
                    cycleOrdinal = command.cycleOrdinal,
                    turnOrdinal = command.turnOrdinal,
                    requestedMove = command.requestedMove.name,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                ),
            ).toDomain()
        }

    override suspend fun revealSolution(command: RevealTutorSolutionCommand): TutorTurnResponse =
        withContext(Dispatchers.IO) {
            if (command.replyVersionId != null) {
                return@withContext updateVersionAnswer(command.sessionId, requireNotNull(command.replyVersionId), allowLockedReveal = true) { previous ->
                    previous?.copy(solutionRevealed = true, updatedAtEpochMillis = command.occurredAtEpochMillis)
                        ?: TutorTurnResponse(command.sessionId, command.questionDocumentId, command.revisionNumber,
                            command.cycleOrdinal, command.turnOrdinal, null, null, null, null, null,
                            solutionRevealed = true, submittedAtEpochMillis = command.occurredAtEpochMillis,
                            updatedAtEpochMillis = command.occurredAtEpochMillis, replyVersionId = command.replyVersionId)
                }
            }
            database.revealTutorSolution(
                PersistTutorRevealCommand(
                    learnerId = learnerId,
                    sessionId = command.sessionId,
                    questionDocumentId = command.questionDocumentId,
                    revisionNumber = command.revisionNumber,
                    cycleOrdinal = command.cycleOrdinal,
                    turnOrdinal = command.turnOrdinal,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                ),
            ).toDomain()
        }

    private suspend fun updateVersionAnswer(
        sessionId: String,
        versionId: String,
        allowLockedReveal: Boolean = false,
        transform: (TutorTurnResponse?) -> TutorTurnResponse,
    ): TutorTurnResponse {
        val version = database.observeTutorReplyVersions(TutorConversationIds.captured(sessionId)).first()
            .single { it.versionId == versionId }
        check(version.selected && (allowLockedReveal || !version.locked)) { "This reply version is read-only" }
        val response = transform(version.responseJson?.let(::decodeReplyVersionResponse))
        val plan = version.toDomainVersion().request?.input as? com.tingyun.smartmistakebook.core.model.TutorPlanInput
        require(plan != null && plan.sessionId == response.sessionId &&
            plan.questionDocument.id == response.questionDocumentId && plan.draftRevisionNumber == response.revisionNumber &&
            plan.cycleOrdinal == response.cycleOrdinal && plan.turnOrdinal == response.turnOrdinal)
        check(database.saveTutorReplyVersionAnswer(versionId, version.responseJson,
            encodeReplyVersionResponse(response), response.updatedAtEpochMillis, allowLockedReveal)) { "Reply version changed before saving" }
        return response.copy(replyVersionId = versionId)
    }

    override suspend fun recordSolutionExposure(command: RecordTutorSolutionExposureCommand) {
        withContext(Dispatchers.IO) {
            database.recordTutorSolutionExposure(
                PersistTutorAnswerExposureCommand(
                    learnerId = learnerId,
                    sessionId = command.sessionId,
                    questionDocumentId = command.questionDocumentId,
                    revisionNumber = command.revisionNumber,
                    cycleOrdinal = command.cycleOrdinal,
                    turnOrdinal = command.turnOrdinal,
                    surfaceKind = command.surfaceKind.name,
                    modelTaskRequestId = command.modelTaskRequestId,
                    responseOrdinal = command.responseOrdinal,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                ),
            )
        }
    }

    override suspend fun hasAnswerExposure(key: TutorAnswerExposureKey): Boolean =
        withContext(Dispatchers.IO) {
            database.readTutorAnswerExposure(
                modelTaskRequestId = key.modelTaskRequestId,
            )?.matchesAnswerExposure(
                expectedLearnerId = learnerId,
                expectedKey = key,
            ) == true
        }

    override suspend fun findRecordedAnswerExposures(
        keys: Set<TutorAnswerExposureKey>,
    ): Set<TutorAnswerExposureKey> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext emptySet()
        val records = database.readTutorAnswerExposures(
            keys.mapTo(linkedSetOf(), TutorAnswerExposureKey::modelTaskRequestId),
        )
        records.matchingAnswerExposureKeys(
            expectedLearnerId = learnerId,
            candidates = keys,
        )
    }

    override suspend fun anchorSession(anchor: TutorSessionProblemAnchor) {
        withContext(Dispatchers.IO) {
            database.bindTutorSessionProblemAnchor(
                PersistTutorSessionAnchorCommand(
                    learnerId = learnerId,
                    sessionId = anchor.sessionId,
                    problemRevisionId = anchor.problemRevisionId,
                    practiceUnitId = anchor.practiceUnitId,
                    source = "SAVED_MISTAKE",
                    anchoredAtEpochMillis = anchor.anchoredAtEpochMillis,
                ),
            )
        }
    }
}

internal fun TutorAnswerExposureRecord.matchesAnswerExposure(
    expectedLearnerId: String,
    expectedKey: TutorAnswerExposureKey,
): Boolean = learnerId == expectedLearnerId &&
    sessionId == expectedKey.sessionId &&
    questionDocumentId == expectedKey.questionDocumentId &&
    questionRevisionNumber == expectedKey.revisionNumber &&
    cycleOrdinal == expectedKey.cycleOrdinal &&
    turnOrdinal == expectedKey.turnOrdinal &&
    surfaceKind == expectedKey.surfaceKind.name &&
    modelTaskRequestId == expectedKey.modelTaskRequestId &&
    responseOrdinal == expectedKey.responseOrdinal

internal fun List<TutorAnswerExposureRecord>.matchingAnswerExposureKeys(
    expectedLearnerId: String,
    candidates: Set<TutorAnswerExposureKey>,
): Set<TutorAnswerExposureKey> {
    if (isEmpty() || candidates.isEmpty()) return emptySet()
    val candidatesByRequestId = candidates.groupBy(TutorAnswerExposureKey::modelTaskRequestId)
    return buildSet {
        this@matchingAnswerExposureKeys.forEach { record ->
            candidatesByRequestId[record.modelTaskRequestId].orEmpty()
                .filterTo(this) { key ->
                    record.matchesAnswerExposure(expectedLearnerId, key)
                }
        }
    }
}

internal fun TutorTurnResponseRecord.toDomain() = TutorTurnResponse(
    sessionId = sessionId,
    questionDocumentId = questionDocumentId,
    revisionNumber = revisionNumber,
    cycleOrdinal = cycleOrdinal,
    turnOrdinal = turnOrdinal,
    diagnosticStemMarkdown = diagnosticStemMarkdown,
    selectedChoiceId = selectedChoiceId,
    selectedChoiceMarkdown = selectedChoiceMarkdown,
    selectionWasCorrect = selectionWasCorrect,
    feedbackMarkdown = feedbackMarkdown,
    requestedMove = requestedMove?.let(TutorMoveType::valueOf),
    solutionRevealed = solutionRevealed,
    choiceSubmittedAtEpochMillis = choiceSubmittedAtEpochMillis,
    submittedAtEpochMillis = submittedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

object TutorInteractionRepositoryFactory {
    fun create(database: StudyDatabasePort): TutorInteractionRepository =
        RoomTutorInteractionRepository(database)
}

@kotlinx.serialization.Serializable
private data class ReplyVersionResponsePayload(
    val sessionId: String,
    val questionDocumentId: String,
    val revisionNumber: Int,
    val cycleOrdinal: Int,
    val turnOrdinal: Int,
    val diagnosticStemMarkdown: String?,
    val selectedChoiceId: String?,
    val selectedChoiceMarkdown: String?,
    val selectionWasCorrect: Boolean?,
    val feedbackMarkdown: String?,
    val requestedMove: String?,
    val solutionRevealed: Boolean,
    val submittedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val choiceSubmittedAtEpochMillis: Long?,
)

internal fun encodeReplyVersionResponse(response: TutorTurnResponse): String =
    kotlinx.serialization.json.Json.encodeToString(
        ReplyVersionResponsePayload.serializer(),
        ReplyVersionResponsePayload(
            response.sessionId, response.questionDocumentId, response.revisionNumber,
            response.cycleOrdinal, response.turnOrdinal, response.diagnosticStemMarkdown,
            response.selectedChoiceId, response.selectedChoiceMarkdown, response.selectionWasCorrect,
            response.feedbackMarkdown, response.requestedMove?.name, response.solutionRevealed,
            response.submittedAtEpochMillis, response.updatedAtEpochMillis, response.choiceSubmittedAtEpochMillis,
        ),
    )

internal fun decodeReplyVersionResponse(json: String): TutorTurnResponse {
    val payload = kotlinx.serialization.json.Json.decodeFromString(ReplyVersionResponsePayload.serializer(), json)
    return with(payload) {
        TutorTurnResponse(
            sessionId = sessionId, questionDocumentId = questionDocumentId, revisionNumber = revisionNumber,
            cycleOrdinal = cycleOrdinal, turnOrdinal = turnOrdinal, diagnosticStemMarkdown = diagnosticStemMarkdown,
            selectedChoiceId = selectedChoiceId, selectedChoiceMarkdown = selectedChoiceMarkdown,
            selectionWasCorrect = selectionWasCorrect, feedbackMarkdown = feedbackMarkdown,
            requestedMove = requestedMove?.let(TutorMoveType::valueOf), solutionRevealed = solutionRevealed,
            submittedAtEpochMillis = submittedAtEpochMillis, updatedAtEpochMillis = updatedAtEpochMillis,
            choiceSubmittedAtEpochMillis = choiceSubmittedAtEpochMillis,
        )
    }
}
