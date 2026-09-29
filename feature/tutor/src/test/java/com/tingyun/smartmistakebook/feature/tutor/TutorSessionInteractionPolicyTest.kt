package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.domain.TutorSendPhase
import com.tingyun.smartmistakebook.core.domain.TutorSendState
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSessionInteractionPolicyTest {
    @Test
    fun choiceAndMoveAreBlockedWhileBusyOrWithoutAnExecutableProvider() {
        assertFalse(
            tutorChoiceSubmissionCanStart(
                hasPlanOutput = true,
                hasDiagnosticItem = true,
                hasEvaluation = true,
                interactionBusy = true,
            ),
        )
        assertTrue(
            tutorChoiceSubmissionCanStart(
                hasPlanOutput = true,
                hasDiagnosticItem = true,
                hasEvaluation = true,
                interactionBusy = false,
            ),
        )
        assertFalse(
            tutorMoveCanStart(
                interactionBusy = true,
                hasExecutableProvider = true,
            ),
        )
        assertFalse(
            tutorMoveCanStart(
                interactionBusy = false,
                hasExecutableProvider = false,
            ),
        )
        assertTrue(
            tutorMoveCanStart(
                interactionBusy = false,
                hasExecutableProvider = true,
            ),
        )
        assertFalse(
            tutorRestartCanStart(
                hasExecutableProvider = true,
                hasConversationMemory = false,
            ),
        )
        assertTrue(
            tutorRestartCanStart(
                hasExecutableProvider = true,
                hasConversationMemory = true,
            ),
        )
        assertEquals(2, tutorPlanAttemptCount(2))
    }

    @Test
    fun interactionErrorsStayStudentFacing() {
        assertTrue(TUTOR_CHOICE_SAVE_ERROR.isNotBlank())
        assertTrue(TUTOR_MOVE_SAVE_ERROR.isNotBlank())
        assertFalse("Exception" in TUTOR_CHOICE_SAVE_ERROR + TUTOR_MOVE_SAVE_ERROR)
        assertFalse("null" in TUTOR_CHOICE_SAVE_ERROR + TUTOR_MOVE_SAVE_ERROR)
        assertFalse("WorkManager" in TUTOR_CHOICE_SAVE_ERROR + TUTOR_MOVE_SAVE_ERROR)
    }

    @Test
    fun respondCollectRequiresAnExecutableProviderAndGuardsLocalRecovery() {
        val external = provider()
        val local = provider(executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS)

        assertFalse(
            tutorRespondCollectCanStart(
                provider = null,
                requestHasEgressManifest = false,
                allowExternalEnvelopeForLocalRecovery = false,
                chatSubmitPending = false,
            ),
        )
        assertTrue(
            tutorRespondCollectCanStart(
                provider = external,
                requestHasEgressManifest = false,
                allowExternalEnvelopeForLocalRecovery = false,
                chatSubmitPending = false,
            ),
        )
        assertFalse(
            tutorRespondCollectCanStart(
                provider = external,
                requestHasEgressManifest = false,
                allowExternalEnvelopeForLocalRecovery = false,
                chatSubmitPending = true,
            ),
        )
        assertTrue(
            tutorRespondCollectCanStart(
                provider = local,
                requestHasEgressManifest = false,
                allowExternalEnvelopeForLocalRecovery = false,
                chatSubmitPending = false,
            ),
        )
    }

    @Test
    fun respondExecuteIsUnchangedFromTheTextComposerGate() {
        assertFalse(
            tutorRespondExecuteCanStart(
                hasPlanOutput = true,
                providerCanExecute = true,
                messageBlank = true,
                chatSending = false,
            ),
        )
        assertTrue(
            tutorRespondExecuteCanStart(
                hasPlanOutput = true,
                providerCanExecute = true,
                messageBlank = false,
                chatSending = false,
            ),
        )
        assertFalse(
            tutorRespondExecuteCanStart(
                hasPlanOutput = true,
                providerCanExecute = true,
                messageBlank = false,
                chatSending = true,
            ),
        )
    }

    @Test
    fun respondCopyStaysStudentFacing() {
        val copy = listOf(
            TUTOR_RESPOND_VALIDATION_TITLE,
            TUTOR_RESPOND_VALIDATION_MESSAGE,
            TUTOR_RESPOND_NETWORK_TITLE,
            TUTOR_RESPOND_NETWORK_MESSAGE,
        ).joinToString()
        assertFalse("Exception" in copy)
        assertFalse("WorkManager" in copy)
        assertFalse("lease" in copy)
        assertFalse("dispatch" in copy)
        assertFalse("SSE" in copy)
    }

    /**
     * B5：发送推进只有一条路——新学生消息开新回合（Reset + StudentMessagePersisted + Consent），
     * 重试沿用原标识。没有"次数到上限"这一说，界面也不再拿它拒绝发送。
     */
    @Test
    fun respondSendAdvanceOpensANewRoundWithoutAnyDispatchBudget() {
        val opened = tutorRespondSendAdvance(
            sendState = TutorSendState(),
            logicalOperationId = "op-1",
            messageId = "msg-1",
            isRetry = false,
        )
        assertEquals(TutorSendPhase.DISPATCHING, opened.phase)
        assertEquals("op-1", opened.logicalOperationId)
        assertEquals("msg-1", opened.messageId)

        // 上一轮还没回来也照发下一轮：新消息开新回合，旧回合的标识被顶掉。
        val nextRound = tutorRespondSendAdvance(
            sendState = opened,
            logicalOperationId = "op-2",
            messageId = "msg-2",
            isRetry = false,
        )
        assertEquals(TutorSendPhase.DISPATCHING, nextRound.phase)
        assertEquals("op-2", nextRound.logicalOperationId)

        val retried = tutorRespondSendAdvance(
            sendState = nextRound,
            logicalOperationId = "op-2",
            messageId = "msg-2",
            isRetry = true,
        )
        assertEquals(TutorSendPhase.DISPATCHING, retried.phase)
        assertEquals("op-2", retried.logicalOperationId)
        assertEquals("msg-2", retried.messageId)
    }

    @Test
    fun agentGateGovernsPlanDispatchAndContinue() {
        val external = provider()
        val local = provider(executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS)

        assertFalse(
            tutorAgentChatEnabled(
                provider = null,
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertTrue(
            tutorAgentChatEnabled(
                provider = external,
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertTrue(
            tutorAgentChatEnabled(
                provider = local,
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertFalse(
            tutorAgentChatEnabled(
                provider = provider(supportsPlan = false),
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertTrue(tutorContinueAfterMove(hasChoicePayload = true, nextHistorySize = 1))
        assertFalse(tutorContinueAfterMove(hasChoicePayload = false, nextHistorySize = 1))
        assertFalse(tutorContinueAfterMove(hasChoicePayload = true, nextHistorySize = 8))
    }

    @Test
    fun configuredProviderIsTheSingleLiveGateAcrossPlanAndRespond() {
        val externalImage = provider()
        val externalStructuredOnly = provider(supportsImageInput = false)
        val local = provider(executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS)

        listOf(
            ModelTaskKind.TUTOR_PLAN,
            ModelTaskKind.TUTOR_RESPOND,
        ).forEach { kind ->
            assertFalse(tutorAgentChatEnabled(provider = null, kind = kind))
            // A provider the app cannot currently execute against fails closed even though
            // it is configured: unavailability outranks the configured-model admission.
            assertFalse(
                tutorAgentChatEnabled(
                    provider = provider(executionLocation = ModelExecutionLocation.UNAVAILABLE),
                    kind = kind,
                ),
            )
            // A local provider never egresses, so the single gate always admits it.
            assertTrue(tutorAgentChatEnabled(provider = local, kind = kind))
        }
        assertTrue(
            tutorAgentChatEnabled(
                provider = externalImage,
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertTrue(
            tutorAgentChatEnabled(
                provider = externalImage,
                kind = ModelTaskKind.TUTOR_RESPOND,
            ),
        )
        assertTrue(
            tutorAgentChatEnabled(
                provider = externalStructuredOnly,
                kind = ModelTaskKind.TUTOR_PLAN,
            ),
        )
        assertTrue(
            tutorAgentChatEnabled(
                provider = externalStructuredOnly,
                kind = ModelTaskKind.TUTOR_RESPOND,
            ),
        )
    }

    /**
     * A1 竞态：`observedTask == null` 只有在**任务流发过首帧**之后才是"确实没有这一轮"。
     *
     * 首次出现这个判据时它读的是 `collectAsState(initial = emptyList())` —— "还没读到"与
     * "确实没有"被压成同一个值，provider 先到的那一次组合因此把首轮派发两遍
     * （`leavingImmediatelyAfterRevealRequestDoesNotRecordAnUnseenExposure` 现场是
     * `RecordingModelTaskRepository.execute` 被第二次调用直接抛错）。
     */
    @Test
    fun theFirstTurnIsNotAutoStartedBeforeThePlanTaskFlowHasEmittedItsFirstFrame() {
        val external = provider()

        assertFalse(
            "任务流还没发首帧时不许开轮（否则会多发一轮）",
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = true,
                tasksObserved = false,
                hasObservedTask = false,
                provider = external,
            ),
        )
        // 首帧到了、那一帧里确实没有任务：这才是"可以开首轮"。
        assertTrue(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = true,
                tasksObserved = true,
                hasObservedTask = false,
                provider = external,
            ),
        )
        // 首帧里已经有这一轮（例如恢复出来的）：不再开第二轮。
        assertFalse(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = true,
                tasksObserved = true,
                hasObservedTask = true,
                provider = external,
            ),
        )
        // 其余三种既有口径不变：入口不自动开轮 / 会话已结束 / 没有可执行的 plan provider。
        assertFalse(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = false,
                conversationEnabled = true,
                tasksObserved = true,
                hasObservedTask = false,
                provider = external,
            ),
        )
        assertFalse(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = false,
                tasksObserved = true,
                hasObservedTask = false,
                provider = external,
            ),
        )
        assertFalse(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = true,
                tasksObserved = true,
                hasObservedTask = false,
                provider = null,
            ),
        )
        assertFalse(
            tutorAutoStartsFirstTurn(
                autoStartFirstTurn = true,
                conversationEnabled = true,
                tasksObserved = true,
                hasObservedTask = false,
                provider = provider(executionLocation = ModelExecutionLocation.UNAVAILABLE),
            ),
        )
    }

    private fun provider(
        executionLocation: ModelExecutionLocation = ModelExecutionLocation.EXTERNAL_PROVIDER,
        supportsImageInput: Boolean = true,
        supportsPlan: Boolean = true,
    ) = ProviderCapabilitySnapshot(
        providerId = "provider",
        providerDisplayName = "模型",
        modelId = "model",
        supportedTasks = buildSet {
            add(ModelTaskKind.TUTOR_PLAN)
            add(ModelTaskKind.TUTOR_RESPOND)
            add(ModelTaskKind.TUTOR_LOBBY)
            if (!supportsPlan) remove(ModelTaskKind.TUTOR_PLAN)
        },
        supportsImageInput = supportsImageInput,
        supportsStructuredOutput = true,
        supportsStreaming = false,
        executionLocation = executionLocation,
        providerConfigurationVersion = "configuration-v1",
    )
}
