package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorMoveCommand
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.domain.toContiguousTutorHistory
import com.tingyun.smartmistakebook.core.domain.toTutorConversationMemory
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.domain.TutorAnswerExposureKey
import com.tingyun.smartmistakebook.core.model.TutorConversationMemory
import com.tingyun.smartmistakebook.core.model.TutorTurnHistoryEntry
import com.tingyun.smartmistakebook.core.model.TutorMoveType
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun tutorContinueAfterMove(
    hasChoicePayload: Boolean,
    nextHistorySize: Int,
): Boolean = hasChoicePayload && nextHistorySize < TutorPlanInput.MAX_TURNS

internal class TutorInteractionCommands(
    private val scope: CoroutineScope,
    private val sink: TutorInteractionSink,
) {
    fun submitChoice(choiceId: String) {
        val output = sink.currentPlanOutput()
        val item = output?.plan?.diagnosticItem
        val evaluation = item?.evaluateChoice(choiceId)
        if (
            !tutorChoiceSubmissionCanStart(
                hasPlanOutput = output != null,
                hasDiagnosticItem = item != null,
                hasEvaluation = evaluation != null,
                interactionBusy = sink.interactionBusy(),
            )
        ) {
            // 界面按下时这一轮还可提交，命令执行时判据已经变了（这一轮不再是当前的 / 已在提交中）。
            // 静默 return 会留下一个永远解不开的 pending 选项：没有任何行会到，也没有错误可等。
            // 如实说"没有保存"，错误本身是那条解开的出口（KD-36 的锁死路径之一）。
            sink.setInteractionError(TUTOR_CHOICE_SAVE_ERROR)
            return
        }
        val submittedItem = requireNotNull(item)
        val submittedEvaluation = requireNotNull(evaluation)
        val question = sink.question()
        val input = sink.currentInput()
        sink.setInteractionError(null)
        sink.setInteractionBusy(true)
        scope.launch {
            try {
                // 有界等待（KD-36）：写入既不返回也不报错时（卡住的 IO / 事务），
                // 不能把选项锁在这里等一个不会到的行。超时按失败处置：解锁 + 错误可见。
                val persisted = withTimeoutOrNull(TUTOR_CHOICE_SAVE_TIMEOUT_MILLIS) {
                    sink.interactions.recordChoice(
                        RecordTutorChoiceCommand(
                            sessionId = question.sessionId,
                            questionDocumentId = question.questionDocument.document.id,
                            revisionNumber = question.revisionNumber,
                            cycleOrdinal = input.cycleOrdinal,
                            turnOrdinal = input.turnOrdinal,
                            diagnosticStemMarkdown = submittedItem.stemMarkdown,
                            selectedChoiceId = submittedEvaluation.choice.id,
                            selectedChoiceMarkdown = submittedEvaluation.choice.markdown,
                            selectionWasCorrect = submittedEvaluation.isCorrect,
                            feedbackMarkdown = requireNotNull(submittedEvaluation.choice.feedbackMarkdown),
                            occurredAtEpochMillis = sink.clock(),
                        ),
                    )
                }
                if (persisted == null) {
                    sink.setInteractionError(TUTOR_CHOICE_SAVE_ERROR)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sink.setInteractionError(TUTOR_CHOICE_SAVE_ERROR)
            } finally {
                sink.setInteractionBusy(false)
            }
        }
    }

    fun continueTurn(requestedMove: TutorMoveType) {
        if (sink.interactionBusy()) return
        if (
            !tutorMoveCanStart(
                interactionBusy = sink.interactionBusy(),
                hasExecutableProvider = sink.hasExecutableProvider(),
            )
        ) {
            sink.openModelSettings()
            return
        }
        val question = sink.question()
        val input = sink.currentInput()
        sink.setInteractionBusy(true)
        scope.launch {
            sink.setInteractionError(null)
            try {
                val movedResponse = sink.interactions.recordMove(
                    RecordTutorMoveCommand(
                        sessionId = question.sessionId,
                        questionDocumentId = question.questionDocument.document.id,
                        revisionNumber = question.revisionNumber,
                        cycleOrdinal = input.cycleOrdinal,
                        turnOrdinal = input.turnOrdinal,
                        requestedMove = requestedMove,
                        occurredAtEpochMillis = sink.clock(),
                    ),
                )
                val nextHistory = sink.currentCycleResponses()
                    .filterNot { it.turnOrdinal == movedResponse.turnOrdinal }
                    .plus(movedResponse)
                    .toContiguousTutorHistory()
                if (tutorContinueAfterMove(movedResponse.hasChoicePayload, nextHistory.size)) {
                    sink.executeTurn(
                        input.cycleOrdinal,
                        input.priorConversationMemory,
                        input.priorCycleStudentMessages,
                        nextHistory,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sink.setInteractionError(TUTOR_MOVE_SAVE_ERROR)
            } finally {
                sink.setInteractionBusy(false)
            }
        }
    }

    fun restartCycle() {
        val memory = sink.tutorResponses().toTutorConversationMemory(sink.answerExposureKeys())
        if (
            !tutorRestartCanStart(
                hasExecutableProvider = sink.hasExecutableProvider(),
                hasConversationMemory = memory != null,
            )
        ) {
            if (!sink.hasExecutableProvider()) sink.openModelSettings()
            return
        }
        sink.executeTurn(
            sink.currentCycle() + 1,
            requireNotNull(memory),
            priorCycleStudentMessages(sink.tutorRespondTasks()),
            emptyList(),
        )
    }
}

internal class TutorInteractionSink(
    val currentPlanOutput: () -> TutorPlanOutput?,
    val currentInput: () -> TutorPlanInput,
    val question: () -> TutorQuestionContext,
    val clock: () -> Long,
    val interactionBusy: () -> Boolean,
    val setInteractionBusy: (Boolean) -> Unit,
    val setInteractionError: (String?) -> Unit,
    val hasExecutableProvider: () -> Boolean,
    val openModelSettings: () -> Unit,
    val currentCycleResponses: () -> List<TutorTurnResponse>,
    val tutorResponses: () -> List<TutorTurnResponse>,
    val tutorRespondTasks: () -> List<ModelTaskSnapshot>,
    val answerExposureKeys: () -> Set<TutorAnswerExposureKey>,
    val currentCycle: () -> Int,
    val executeTurn: (
        Int,
        TutorConversationMemory?,
        List<String>,
        List<TutorTurnHistoryEntry>,
    ) -> Unit,
    val interactions: TutorInteractionRepository,
)
