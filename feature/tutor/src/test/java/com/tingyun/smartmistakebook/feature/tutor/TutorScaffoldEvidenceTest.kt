package com.tingyun.smartmistakebook.feature.tutor

import com.tingyun.smartmistakebook.core.model.ModelFailureCode
import com.tingyun.smartmistakebook.core.model.ModelTaskFailure
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskInput
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卡点证据只从**本地可观察的事实**来（D-Q9 的本地一半）：连续失败轮数与最长一轮耗时。
 *
 * 判别格是"哪些事实算卡点"：末尾连续失败才计数（中间成功过就清零）、耗时取最长的一轮
 * （不是最后一轮）、以及没有任何任务时两个字段都是 0/null（不猜）。
 */
class TutorScaffoldEvidenceTest {
    @Test
    fun noTasksMeansNoEvidence() {
        val evidence = tutorLobbyStallEvidence(emptyList())

        assertEquals(0, evidence.consecutiveFailedRounds)
        assertEquals(null, evidence.lastRoundDurationMillis)
    }

    @Test
    fun onlyTheTrailingFailuresCount() {
        val evidence = tutorLobbyStallEvidence(
            listOf(
                task(ordinal = 1, status = ModelTaskStatus.RETRYABLE_FAILURE, startedAt = 100, updatedAt = 200),
                task(ordinal = 2, status = ModelTaskStatus.SUCCEEDED, startedAt = 300, updatedAt = 400),
                task(ordinal = 3, status = ModelTaskStatus.PERMANENT_FAILURE, startedAt = 500, updatedAt = 600),
                task(ordinal = 4, status = ModelTaskStatus.CANCELLED, startedAt = 700, updatedAt = 800),
            ),
        )

        // 末尾连续两轮没拿到可用回复；第 1 轮那次失败不算（中间成功过）。
        assertEquals(2, evidence.consecutiveFailedRounds)
    }

    @Test
    fun theDurationIsTheLongestSucceededRound() {
        val evidence = tutorLobbyStallEvidence(
            listOf(
                task(ordinal = 1, status = ModelTaskStatus.SUCCEEDED, startedAt = 0, updatedAt = 5_000),
                task(ordinal = 2, status = ModelTaskStatus.SUCCEEDED, startedAt = 0, updatedAt = 200_000),
                task(ordinal = 3, status = ModelTaskStatus.RETRYABLE_FAILURE, startedAt = 0, updatedAt = 900_000),
            ),
        )

        // 取最长的那一轮成功（最后一轮是失败，它的时长不代表"学生在自己试"）。
        assertEquals(200_000L, evidence.lastRoundDurationMillis)
        assertEquals(1, evidence.consecutiveFailedRounds)
    }

    @Test
    fun aSuccessfulRoundKeepsTheEvidenceCalm() {
        val evidence = tutorLobbyStallEvidence(
            listOf(task(ordinal = 1, status = ModelTaskStatus.SUCCEEDED, startedAt = 0, updatedAt = 1_000)),
        )

        assertEquals(0, evidence.consecutiveFailedRounds)
        assertEquals(1_000L, evidence.lastRoundDurationMillis)
    }

    private fun task(
        ordinal: Int,
        status: ModelTaskStatus,
        startedAt: Long,
        updatedAt: Long,
    ): ModelTaskSnapshot {
        val input: ModelTaskInput = TutorLobbyInput(
            conversationId = "conversation-1",
            messageOrdinal = ordinal,
            studentMessage = "第 $ordinal 轮",
        )
        val request = ModelTaskRequest(
            requestId = "lobby-request-$ordinal",
            input = input,
            occurredAtEpochMillis = startedAt,
        )
        return ModelTaskSnapshot(
            taskId = "task:$ordinal",
            request = request,
            requestFingerprint = ModelTaskFingerprint.of(request),
            status = status,
            stateVersion = 1,
            stage = ModelTaskStage.PREPARING,
            userMessage = "正在处理",
            attemptCount = 1,
            provider = null,
            output = if (status == ModelTaskStatus.SUCCEEDED) {
                TutorLobbyOutput(
                    conversationId = "conversation-1",
                    messageOrdinal = ordinal,
                    messageMarkdown = "这是第 $ordinal 轮的答复。",
                    modelVersion = "model-1",
                )
            } else {
                null
            },
            failure = if (
                status == ModelTaskStatus.RETRYABLE_FAILURE ||
                status == ModelTaskStatus.PERMANENT_FAILURE
            ) {
                ModelTaskFailure(
                    code = ModelFailureCode.TIMEOUT,
                    message = "暂时没有完成",
                    retryable = true,
                )
            } else {
                null
            },
            createdAtEpochMillis = startedAt,
            updatedAtEpochMillis = updatedAt,
        )
    }
}
