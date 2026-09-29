package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一条交互面（C1）的三件策略：A1 空态是能力目录、A3 实时流挂本会话当前活跃任务、
 * A5 输入框永不消失。三件都做成纯函数，所以能在单测里逐个钉住。
 */
class TutorSurfacePolicyTest {

    // ---- A5：输入框永不消失 ----

    @Test
    fun aMissingProviderExplainsItselfAndOffersTheSettingsEntry() {
        val block = TutorComposerAvailability(
            providerReady = false,
            providerLoadFailed = false,
        ).block()

        assertNotNull("模型没配好时必须给出一句人话", block)
        assertTrue(block!!.reason.contains("模型"))
        assertEquals(TutorComposerActionKind.OPEN_SETTINGS, block.action?.kind)
        assertEquals(TUTOR_SURFACE_OPEN_SETTINGS_LABEL, block.action?.label)
        // 只是发不出去：输入框仍然可以打字（A5）。
        assertEquals(false, block.inputDisabled)
    }

    @Test
    fun anUnreadableProviderConfigAlsoOffersTheSettingsEntry() {
        val block = TutorComposerAvailability(
            providerReady = false,
            providerLoadFailed = true,
        ).block()

        assertEquals(TutorComposerActionKind.OPEN_SETTINGS, block?.action?.kind)
        assertEquals(false, block?.inputDisabled)
    }

    @Test
    fun aPlanStillBeingPreparedSaysSoWithoutInventingAnAction() {
        val block = TutorComposerAvailability(
            providerReady = true,
            planReady = false,
            planFailed = false,
        ).block()

        assertNotNull("讲解还没出结果时不能装作可以发", block)
        assertTrue(block!!.reason.contains("正在准备"))
        // 这一轮正在跑，没有"重试"可点：不给一个按下去没反应的按钮。
        assertNull(block.action)
    }

    @Test
    fun aFailedPlanOffersTheUnifiedRetryWording() {
        val block = TutorComposerAvailability(
            providerReady = true,
            planReady = false,
            planFailed = true,
        ).block()

        assertEquals(TutorComposerActionKind.RETRY, block?.action?.kind)
        // 三套文案（重新发送 / 重试 / 重新生成）统一成一套。
        assertEquals("重试", block?.action?.label)
        assertEquals(TUTOR_SURFACE_RETRY_LABEL, block?.action?.label)
    }

    @Test
    fun anEndedConversationKeepsTheBoxButSaysThereIsNothingLeftToSend() {
        val block = TutorComposerAvailability(
            providerReady = true,
            conversationEnded = true,
        ).block()

        assertNotNull("会话结束后输入框也不能消失（A5）", block)
        assertTrue(block!!.reason.contains("已经结束"))
        // 确实没有可以发过去的对象：输入框在，但不假装能输入。
        assertTrue(block.inputDisabled)
        assertNull(block.action)
    }

    @Test
    fun aReadyRoundHasNoBlockAtAll() {
        assertNull(
            TutorComposerAvailability(
                providerReady = true,
                planReady = true,
            ).block(),
        )
    }

    @Test
    fun theEndedConversationOutranksEveryOtherReason() {
        // 顺序就是学生先撞上的那一件：会话都结束了，再说"模型没配好"没有意义。
        val block = TutorComposerAvailability(
            providerReady = false,
            providerLoadFailed = true,
            planReady = false,
            planFailed = true,
            conversationEnded = true,
        ).block()

        assertTrue(block!!.reason.contains("已经结束"))
        assertTrue(block.inputDisabled)
    }

    // ---- A3：实时文本挂"本会话当前活跃任务" ----

    @Test
    fun thePlanPhaseIsAnActiveTurnSoItGetsTheLiveStream() {
        val active = activeTutorLiveTurn(
            conversationKey = "tutor-conv:captured:session-1",
            tasks = listOf(
                planTask("session-1", status = ModelTaskStatus.RUNNING, updatedAt = 200),
            ),
        )

        // 消灭的失败：订阅只挂回应轮，计划阶段全程没有流。
        assertEquals("plan-request", active?.requestId)
        assertEquals("tutor-conv:captured:session-1#plan-request", active?.subscriptionKey)
    }

    @Test
    fun theNewestInFlightTaskWinsAcrossTaskKinds() {
        val active = activeTutorLiveTurn(
            conversationKey = "tutor-conv:captured:session-1",
            tasks = listOf(
                // 已经收场的那一轮（SUCCEEDED 必须有输出，这里用取消态表达"早就结束了"）。
                planTask("session-1", status = ModelTaskStatus.CANCELLED, updatedAt = 100),
                planTask("session-1", status = ModelTaskStatus.RUNNING, updatedAt = 150),
                respondTask("session-1", status = ModelTaskStatus.STREAMING, updatedAt = 300),
                lobbyTask("tutor-conv:captured:session-1", status = ModelTaskStatus.QUEUED, updatedAt = 250),
            ),
        )

        assertEquals("respond-request", active?.requestId)
    }

    @Test
    fun aConversationWithNothingInFlightHasNoLiveSubscription() {
        val active = activeTutorLiveTurn(
            conversationKey = "tutor-conv:captured:session-1",
            tasks = listOf(
                planTask("session-1", status = ModelTaskStatus.CANCELLED, updatedAt = 100),
                respondTask("session-1", status = ModelTaskStatus.RETRYABLE_FAILURE, updatedAt = 200),
            ),
        )

        assertNull(active)
    }

    // ---- A1：空态是能力目录 ----

    @Test
    fun theEmptyStateDirectoryListsOnlyCapabilitiesThatActuallyDoSomething() {
        var captures = 0
        var picks = 0
        var notebookOpens = 0
        val capabilities = agentCapabilityDirectory(
            onCapture = { captures += 1 },
            onPickMistake = { picks += 1 },
            onOpenMistakeNotebook = { notebookOpens += 1 },
        )

        assertTrue("空态必须真的是一条目录，而不是一句话", capabilities.size >= 3)
        assertTrue(capabilities.all { capability -> capability.title.isNotBlank() })
        assertTrue(capabilities.all { capability -> capability.detail.isNotBlank() })
        // 没有死按钮：每个带出口的能力都必须同时有文案与动作。
        capabilities.filter { it.actionLabel != null || it.onAction != null }.forEach { capability ->
            assertNotNull(capability.actionLabel)
            assertNotNull(capability.actionTestTag)
            assertNotNull(capability.onAction)
        }
        // 被既有用例与产品口径按住的标签。
        assertEquals(
            setOf("tutor_capture_shortcut", "tutor_choose_existing_button", "tutor_directory_open_notebook"),
            capabilities.mapNotNull { it.actionTestTag }.toSet(),
        )
        capabilities.first { it.id == "capture" }.onAction?.invoke()
        capabilities.first { it.id == "pick-from-notebook" }.onAction?.invoke()
        capabilities.first { it.id == "open-notebook" }.onAction?.invoke()
        assertEquals(1, captures)
        assertEquals(1, picks)
        assertEquals(1, notebookOpens)
        // 「试一句」（S5）：每条能力都带可点示例，点了直接作为首条消息发出——
        // 所以示例必须**每一句都非空、标签唯一**（点了没反应的假示例与重复的标签都是缺陷）。
        assertTrue(capabilities.all { capability -> capability.examples.isNotEmpty() })
        val examples = capabilities.flatMap { capability -> capability.examples }
        assertTrue(examples.all { example -> example.text.isNotBlank() })
        assertEquals(
            examples.size,
            examples.map { example -> example.testTag }.toSet().size,
        )
    }

    // ---- 夹具 ----

    private fun planTask(
        sessionId: String,
        status: ModelTaskStatus,
        updatedAt: Long,
    ): ModelTaskSnapshot = snapshot(
        status = status,
        updatedAt = updatedAt,
        input = TutorPlanInput(
            sessionId = sessionId,
            draftRevisionNumber = 1,
            subject = "数学",
            questionDocument = questionDocument(),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
        ),
    )

    private fun respondTask(
        sessionId: String,
        status: ModelTaskStatus,
        updatedAt: Long,
    ): ModelTaskSnapshot = snapshot(
        status = status,
        updatedAt = updatedAt,
        input = TutorRespondInput(
            sessionId = sessionId,
            draftRevisionNumber = 1,
            subject = "数学",
            questionDocument = questionDocument(),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            responseOrdinal = 1,
            studentMessage = "这一步怎么来的",
        ),
    )

    private fun lobbyTask(
        conversationId: String,
        status: ModelTaskStatus,
        updatedAt: Long,
    ): ModelTaskSnapshot = snapshot(
        status = status,
        updatedAt = updatedAt,
        input = TutorLobbyInput(
            conversationId = conversationId,
            messageOrdinal = 1,
            studentMessage = "问一句",
        ),
    )

    private fun questionDocument() = QuestionDocument(
        id = "question-a",
        blocks = listOf(ContentBlock.Paragraph("stem", "求单调区间")),
    )

    private fun snapshot(
        status: ModelTaskStatus,
        updatedAt: Long,
        input: ModelTaskInput,
    ): ModelTaskSnapshot {
        val request = ModelTaskRequest(
            requestId = when (input) {
                is TutorPlanInput -> "plan-request"
                is TutorRespondInput -> "respond-request"
                else -> "lobby-request"
            },
            input = input,
            occurredAtEpochMillis = updatedAt,
        )
        return ModelTaskSnapshot(
            taskId = "task:${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = status,
            stateVersion = 1,
            stage = ModelTaskStage.PREPARING,
            userMessage = "正在处理",
            attemptCount = 1,
            provider = null,
            output = null,
            failure = if (status == ModelTaskStatus.RETRYABLE_FAILURE ||
                status == ModelTaskStatus.PERMANENT_FAILURE
            ) {
                ModelTaskFailure(
                    code = ModelFailureCode.TIMEOUT,
                    message = "暂时没有完成",
                    retryable = status == ModelTaskStatus.RETRYABLE_FAILURE,
                )
            } else {
                null
            },
            createdAtEpochMillis = updatedAt,
            updatedAtEpochMillis = updatedAt,
        )
    }
}
