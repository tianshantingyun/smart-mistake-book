package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorRespondOutput
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * K1c 之后的两条号轴，各自单一：
 *
 * 1. **消息序号 = 会话级单调 ordinal**（`nextTutorMessageOrdinals`）：每条消息取会话计数器的
 *    下一位，学生与助手各占一位。旧口径是两套——大厅 `last_turn_ordinal` 步长 2 再把序号整除
 *    推回轮次（`(last/2)+1`），讲题区 `responseOrdinal*2-1`——同一条会话里（讲题会话两条路都写）
 *    两套号会在 `tutor_message` 的唯一键 `(conversation_id, ordinal)` 上互撞。
 * 2. **派发槽位号 = 会话内 RESPOND 任务的最大号 +1**（`nextTutorResponseOrdinal`）：不再按题派生
 *    （换题后 max 从 0 开始会让"第二道题的第一轮"与"第一道题的第一轮"撞 `model_task` 的槽
 *    `(subject_id, task_kind, tutor_response_ordinal)`）。它**不再**参与消息序号
 *    （旧的 `*2-1` 已废弃），只用来占一次派发槽位并进入请求标识。
 *
 * 槽键主语（`subjectId`）是会话 id（`TutorConversationIds.captured`），与 `tutor_message`
 * 的唯一键同一个 id 空间——K1 之前这里是讲题会话 id，两个键空间并存。
 */
class TutorResponseOrdinalTest {

    @Test
    fun `message ordinals advance one per message from the conversation counter`() {
        val first = nextTutorMessageOrdinals(lastTurnOrdinal = 0)
        assertEquals(1, first.student)
        assertEquals(2, first.assistant)

        // 一轮结束后计数器停在助手的号上，下一轮接着往下走（不再有步长 2 的隐含约定）。
        val second = nextTutorMessageOrdinals(lastTurnOrdinal = first.assistant)
        assertEquals(3, second.student)
        assertEquals(4, second.assistant)
    }

    @Test
    fun `a message ordinal is never derived from the dispatch slot ordinal`() {
        // 回归防线：旧口径下学生第 n 轮的消息号是 `responseOrdinal*2-1`（1,3,5…）。
        // 新口径下号只由会话计数器决定，与派发槽位号无关——槽位号 3 的一轮
        // 拿到的消息号取决于会话已经写了多少条消息，而不是 5。
        val ordinals = nextTutorMessageOrdinals(lastTurnOrdinal = 1)
        assertEquals(2, ordinals.student)
        assertEquals(3, ordinals.assistant)
        assertEquals(3, nextTutorResponseOrdinal("session-1", listOf(respondTask("session-1", "question-a", 1), respondTask("session-1", "question-a", 2))))
    }

    @Test
    fun `an empty session starts at the first round`() {
        assertEquals(1, nextTutorResponseOrdinal("session-1", emptyList()))
    }

    @Test
    fun `rounds stay monotonic inside one question`() {
        assertEquals(
            3,
            nextTutorResponseOrdinal(
                "session-1",
                listOf(respondTask("session-1", "question-a", 1), respondTask("session-1", "question-a", 2)),
            ),
        )
    }

    @Test
    fun `a second question in the same session does not restart at one`() {
        // 这就是撞槽的现场：同一会话里已经聊过 question-a 两轮，现在换到 question-b。
        // 按题派生会得到 1；按会话分配必须得到 3。
        val sessionTasks = listOf(
            respondTask("session-1", "question-a", 1),
            respondTask("session-1", "question-a", 2),
        )

        assertEquals(3, nextTutorResponseOrdinal("session-1", sessionTasks))
    }

    @Test
    fun `non-respond tasks and other sessions do not advance the ordinal`() {
        val sessionTasks = listOf(
            respondTask("session-1", "question-a", 1),
            respondTask("session-1", "question-b", 2),
            planTask("session-1"),
            respondTask("session-2", "question-other", 7),
        )

        assertEquals(3, nextTutorResponseOrdinal("session-1", sessionTasks))
    }

    @Test
    fun `another session's rounds start from its own first round`() {
        // 分配按会话隔离：别人的号不算进本会话，本会话也不会把别人的号推高。
        val sessionTasks = listOf(
            respondTask("session-1", "question-a", 1),
            respondTask("session-2", "question-other", 9),
        )

        assertEquals(2, nextTutorResponseOrdinal("session-1", sessionTasks))
        assertEquals(10, nextTutorResponseOrdinal("session-2", sessionTasks))
    }

    @Test
    fun `the dispatch slot subject is the conversation id`() {
        // K1c：槽键主语 = 会话 id（会话行身份），不是讲题会话 id。
        val respond = TutorRespondInput(
            sessionId = "session-1",
            draftRevisionNumber = 1,
            subject = "数学",
            questionDocument = QuestionDocument(
                id = "question-a",
                blocks = listOf(ContentBlock.Paragraph("stem", "求单调区间")),
            ),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            responseOrdinal = 1,
            studentMessage = "这一步怎么来的",
        )
        assertEquals("tutor-conv:captured:session-1", respond.subjectId)
        assertEquals(
            "tutor-conv:captured:session-1",
            com.tingyun.smartmistakebook.core.model.TutorPlanInput(
                sessionId = "session-1",
                draftRevisionNumber = 1,
                subject = "数学",
                questionDocument = respond.questionDocument,
                relevantLearningEvidence = emptyList(),
                projectionIsCurrent = true,
            ).subjectId,
        )
    }

    private fun respondTask(
        sessionId: String,
        questionId: String,
        responseOrdinal: Int,
    ): ModelTaskSnapshot = snapshot(
        input = TutorRespondInput(
            sessionId = sessionId,
            draftRevisionNumber = 1,
            subject = "数学",
            questionDocument = QuestionDocument(
                id = questionId,
                blocks = listOf(ContentBlock.Paragraph("stem", "求单调区间")),
            ),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
            responseOrdinal = responseOrdinal,
            studentMessage = "这一步怎么来的",
        ),
    )

    /** 计划任务不是 RESPOND：它以非终态出现，因为 SUCCEEDED 的模型任务必须带输出。 */
    private fun planTask(sessionId: String): ModelTaskSnapshot = snapshot(
        status = ModelTaskStatus.RUNNING,
        input = com.tingyun.smartmistakebook.core.model.TutorPlanInput(
            sessionId = sessionId,
            draftRevisionNumber = 1,
            subject = "数学",
            questionDocument = QuestionDocument(
                id = "question-a",
                blocks = listOf(ContentBlock.Paragraph("stem", "求单调区间")),
            ),
            relevantLearningEvidence = emptyList(),
            projectionIsCurrent = true,
        ),
    )

    private fun snapshot(
        status: ModelTaskStatus = ModelTaskStatus.SUCCEEDED,
        input: com.tingyun.smartmistakebook.core.model.ModelTaskInput,
    ): ModelTaskSnapshot {
        val request = ModelTaskRequest(
            requestId = "request:${input.kind}:${input.subjectId}:${(input as? TutorRespondInput)?.responseOrdinal}",
            input = input,
            occurredAtEpochMillis = 100,
        )
        return ModelTaskSnapshot(
            taskId = "task:${request.requestId}",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = status,
            stateVersion = 1,
            stage = ModelTaskStage.COMPLETE,
            userMessage = "完成",
            attemptCount = 1,
            provider = provider,
            output = if (input is TutorRespondInput) {
                TutorRespondOutput(
                    sessionId = input.sessionId,
                    draftRevisionNumber = input.draftRevisionNumber,
                    questionDocumentId = input.questionDocument.id,
                    responseOrdinal = input.responseOrdinal,
                    messageMarkdown = "先看临界点两侧的符号。",
                    modelVersion = "model-v1",
                )
            } else {
                null
            },
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 100,
        )
    }

    private companion object {
        val provider = ProviderCapabilitySnapshot(
            providerId = "configured-provider",
            providerDisplayName = "已配置模型",
            modelId = "tutor-model-v1",
            supportedTasks = setOf(ModelTaskKind.TUTOR_PLAN, ModelTaskKind.TUTOR_RESPOND),
            supportsImageInput = false,
            supportsStructuredOutput = true,
            supportsStreaming = true,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
        )
    }
}
