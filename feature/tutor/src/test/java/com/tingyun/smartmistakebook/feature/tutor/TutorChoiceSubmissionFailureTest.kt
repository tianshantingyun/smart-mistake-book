package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.RecordTutorChoiceCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorMoveCommand
import com.tingyun.smartmistakebook.core.domain.RecordTutorSolutionExposureCommand
import com.tingyun.smartmistakebook.core.domain.RevealTutorSolutionCommand
import com.tingyun.smartmistakebook.core.domain.TutorInteractionRepository
import com.tingyun.smartmistakebook.core.domain.TutorTurnResponse
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选择提交的失败路径（KD-36：提交后 UI 锁死且零持久化）。
 *
 * 界面侧的锁（`TutorGeneratedTurn` 的 `pendingChoiceId`）只有两条出路：响应行到达，
 * 或 `interactionError` 非空。此前"写失败"还有两条**没有**出路的路：
 * ①命令的判据在按下之后变了 → 静默 return（没有行、没有错）；②写入永远不回来 →
 * 一直"提交中"。两条都会把选项永久锁死，库里零持久化。
 *
 * 这里钉的就是补上的失败路径：判据变了要报错；有界等待超时算失败（解锁 + 错误可见）；
 * 成功路径保持原语义（无错误、busy 归位）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorChoiceSubmissionFailureTest {
    @Test
    fun aFailedChoiceWriteSurfacesTheErrorAndReleasesTheBusyFlag() = runTest {
        val interactions = RecordingTutorInteractions(
            write = { error("tutor message row could not be written") },
        )
        val harness = choiceSession(interactions = interactions, scope = this)

        harness.commands.submitChoice("choice-2")
        advanceUntilIdle()

        assertEquals(1, interactions.writes)
        assertEquals(TUTOR_CHOICE_SAVE_ERROR, harness.lastError)
        assertFalse(harness.busy)
    }

    @Test
    fun aHungChoiceWriteIsReleasedByTheBoundedWait() = runTest {
        val interactions = RecordingTutorInteractions(write = { awaitCancellation() })
        val harness = choiceSession(interactions = interactions, scope = this)

        harness.commands.submitChoice("choice-2")
        advanceTimeBy(1_000)
        // 写入在途：仍是"提交中"，错误还没有——既有语义（行到达前不重复提交）。
        assertTrue(harness.busy)
        assertNull(harness.lastError)

        advanceTimeBy(TUTOR_CHOICE_SAVE_TIMEOUT_MILLIS + 1)
        advanceUntilIdle()

        // 有界等待到了：按失败处置——解锁（busy 归位 + 错误可见）让学生能再点一次。
        assertEquals(TUTOR_CHOICE_SAVE_ERROR, harness.lastError)
        assertFalse(harness.busy)
    }

    @Test
    fun aChoiceThatIsNoLongerSubmittableIsNotSilentlySwallowed() = runTest {
        val interactions = RecordingTutorInteractions(
            write = { error("a stale choice must never reach the repository") },
        )
        val harness = choiceSession(interactions = interactions, scope = this)

        // 按下与执行之间这一轮不再是当前的（计划输出已失效）：判据拒发。
        // 静默 return 正是"选项锁死且零持久化"里没有错误可等的那条路。
        harness.planOutput = null
        harness.commands.submitChoice("choice-2")
        advanceUntilIdle()

        assertEquals(0, interactions.writes)
        assertEquals(TUTOR_CHOICE_SAVE_ERROR, harness.lastError)
        assertFalse(harness.busy)
    }

    @Test
    fun aSuccessfulChoiceWriteKeepsTheExistingSemantics() = runTest {
        val interactions = RecordingTutorInteractions(
            write = { command -> choiceResponse(command) },
        )
        val harness = choiceSession(interactions = interactions, scope = this)

        harness.commands.submitChoice("choice-2")
        advanceUntilIdle()

        assertEquals(1, interactions.writes)
        assertNull(harness.lastError)
        assertFalse(harness.busy)
    }

    // --- helpers -------------------------------------------------------------

    /** 一条独立的讲题选择会话：命令 + 它读写的三处 UI 状态（计划输出 / busy / 错误）。 */
    private class ChoiceSession(
        interactions: TutorInteractionRepository,
        scope: CoroutineScope,
    ) {
        var planOutput: TutorPlanOutput? = tutorPlanOutput()
        var busy: Boolean = false
        var lastError: String? = null

        val commands: TutorInteractionCommands = TutorInteractionCommands(
            scope = scope,
            sink = TutorInteractionSink(
                currentPlanOutput = { planOutput },
                currentInput = { PLAN_INPUT },
                question = { QUESTION },
                clock = { 10_000L },
                interactionBusy = { busy },
                setInteractionBusy = { value -> busy = value },
                setInteractionError = { message -> lastError = message },
                hasExecutableProvider = { true },
                openModelSettings = {},
                currentCycleResponses = { emptyList() },
                tutorResponses = { emptyList() },
                tutorRespondTasks = { emptyList() },
                answerExposureKeys = { emptySet() },
                currentCycle = { 1 },
                executeTurn = { _, _, _, _ -> },
                interactions = interactions,
            ),
        )
    }

    private fun choiceSession(
        interactions: TutorInteractionRepository,
        scope: CoroutineScope,
    ): ChoiceSession = ChoiceSession(interactions = interactions, scope = scope)

    /**
     * 写入替身：按用例决定"抛错 / 卡住 / 成功"。成功与失败都由命令自己的处置决定，
     * 替身只如实回写命令给它的那一行。
     */
    private class RecordingTutorInteractions(
        private val write: suspend (RecordTutorChoiceCommand) -> TutorTurnResponse,
    ) : TutorInteractionRepository {
        var writes: Int = 0

        override fun observe(sessionId: String): Flow<List<TutorTurnResponse>> = flowOf(emptyList())

        override suspend fun recordChoice(command: RecordTutorChoiceCommand): TutorTurnResponse {
            writes += 1
            return write(command)
        }

        override suspend fun recordMove(command: RecordTutorMoveCommand): TutorTurnResponse =
            error("No move write expected")

        override suspend fun revealSolution(
            command: RevealTutorSolutionCommand,
        ): TutorTurnResponse = error("No reveal write expected")

        override suspend fun recordSolutionExposure(
            command: RecordTutorSolutionExposureCommand,
        ) = Unit
    }

}

// 文件级夹具（不在 companion 里：嵌套类构造命令时按简单名读取，作用域没有歧义）。
// 声明顺序即初始化顺序：PLAN_INPUT 读 QUESTION 的题面，所以 QUESTION 在前。
private val QUESTION: TutorQuestionContext = TutorQuestionContext(
            sessionId = "session-1",
            revisionNumber = 2,
            subject = "MATH",
            title = "求函数的单调区间",
            questionDocument = CapturedQuestionDocument(
                document = QuestionDocument(
                    id = "document-1",
                    blocks = listOf(
                        ContentBlock.Paragraph(
                            id = "stem",
                            markdown = "已知 f(x)=x³-3x，求它的单调区间。",
                        ),
                    ),
                ),
                blockEvidence = listOf(
                    QuestionBlockEvidence(
                        blockId = "stem",
                        sourceAssetId = "asset-1",
                        provenance = QuestionBlockProvenance.USER_TRANSCRIPTION,
                        reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                    ),
                ),
            ),
        )

private val PLAN_INPUT: TutorPlanInput = TutorPlanInput(
    sessionId = "session-1",
    draftRevisionNumber = 2,
    subject = "MATH",
    questionDocument = QUESTION.questionDocument.document,
    relevantLearningEvidence = emptyList(),
    projectionIsCurrent = true,
)

private fun tutorPlanOutput(): TutorPlanOutput = TutorPlanOutput(
            sessionId = "session-1",
            draftRevisionNumber = 2,
            questionDocumentId = "document-1",
            plan = TutorTurnPlan(
                openingMarkdown = "先判断导数的正负变化。",
                diagnosticItem = TutorAssessmentItem(
                    id = "diagnostic-1",
                    stemMarkdown = "导数先正后负时，原函数怎样变化？",
                    choices = listOf(
                        TutorChoice("choice-1", "先增后减", "这个对应关系是正确的。"),
                        TutorChoice("choice-2", "先减后增", "你把正负关系反过来了。"),
                    ),
                    correctChoiceId = "choice-1",
                ),
                solutionMarkdown = "完整主解法内容",
                alternateMethodMarkdown = "符号表替代解法内容",
                difficultyReasonMarkdown = "用于区分符号对应和变号遗漏。",
                targetedEvidenceLabels = emptyList(),
                inferredKnowledgeLabels = listOf("导数"),
            ),
            modelVersion = "model-v1",
        )

private fun choiceResponse(command: RecordTutorChoiceCommand) = TutorTurnResponse(
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
    requestedMove = null,
    solutionRevealed = false,
    submittedAtEpochMillis = command.occurredAtEpochMillis,
    updatedAtEpochMillis = command.occurredAtEpochMillis,
)
