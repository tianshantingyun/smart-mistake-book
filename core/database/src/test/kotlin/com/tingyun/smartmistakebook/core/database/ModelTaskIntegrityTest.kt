package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.toSnapshot
import com.tingyun.smartmistakebook.core.database.dao.verifyIntegrity
import com.tingyun.smartmistakebook.core.database.entity.ModelTaskEntity
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.ModelTaskCodec
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S7（W4-2 投影热路径）的正直性边界：**读路径懒校验、显式入口保留重算**。
 *
 * 旧边界是"读到即重算"——`observe`/`read` 每次重新计算操作指纹 SHA-256 并逐列核对；
 * 新边界是读路径只解码、[verifyIntegrity]（写入路径与迁移/测试入口）才做完整校验。
 * 本用例钉住的语义一条不少：**篡改仍被 verifyIntegrity 捕获**，只是不再在渲染路径上抛。
 */
class ModelTaskIntegrityTest {

    @Test
    fun `a valid row verifies and the lazy read snapshot is identical`() {
        val entity = entity(request())

        assertEquals(entity.verifyIntegrity(), entity.toSnapshot())
    }

    @Test
    fun `a tampered operation fingerprint is caught by verifyIntegrity but not by the lazy read`() {
        val request = request()
        val tampered = entity(request).copy(operationFingerprint = "f".repeat(64))

        // 读路径：不再重算指纹（S7 的收益点），快照照常可构造。
        assertEquals(request, tampered.toSnapshot().request)

        // 显式入口（写入路径在用的那一个）：篡改仍然当场被拒。
        val failure = runCatching { tampered.verifyIntegrity() }.exceptionOrNull()
        assertTrue(
            "篡改的操作指纹必须被 verifyIntegrity 捕获：$failure",
            failure is LearningLedgerIntegrityException,
        )
    }

    @Test
    fun `a tampered request column is caught by verifyIntegrity but not by the lazy read`() {
        val tampered = entity(request()).copy(subjectId = "some-other-subject")

        assertEquals(request(), tampered.toSnapshot().request)
        val failure = runCatching { tampered.verifyIntegrity() }.exceptionOrNull()
        assertTrue(
            "列与快照不一致必须被 verifyIntegrity 捕获：$failure",
            failure is LearningLedgerIntegrityException,
        )
    }

    @Test
    fun `incomplete failure columns are rejected on both paths`() {
        val tampered = entity(request()).copy(failureCode = "NETWORK")

        assertTrue(
            runCatching { tampered.toSnapshot() }.exceptionOrNull() is LearningLedgerIntegrityException,
        )
        assertTrue(
            runCatching { tampered.verifyIntegrity() }.exceptionOrNull() is LearningLedgerIntegrityException,
        )
    }

    private fun entity(request: ModelTaskRequest) = ModelTaskEntity(
        taskId = "task-${SEQUENCE.incrementAndGet()}",
        requestId = request.requestId,
        requestFingerprint = ModelTaskFingerprint.of(request),
        operationFingerprint = ModelTaskLogicalOperationFingerprint.of(request),
        requestSnapshot = ModelTaskCodec.encodeRequest(request),
        taskKind = request.input.kind.name,
        subjectId = request.input.subjectId,
        tutorResponseOrdinal = null,
        status = ModelTaskStatus.WAITING_FOR_MODEL.name,
        stateVersion = 0,
        stage = ModelTaskStage.WAITING.name,
        userMessage = "任务已保存，等待模型处理",
        attemptCount = 0,
        providerSnapshot = null,
        outputSnapshot = null,
        failureCode = null,
        failureMessage = null,
        failureRetryable = null,
        createdAtEpochMillis = 100,
        updatedAtEpochMillis = 100,
    )

    private fun request() = ModelTaskRequest(
        requestId = "capture-assess:request-1",
        input = CaptureAssessmentInput(
            draftId = "draft-1",
            sourceAssetId = "asset-1",
            origin = CaptureAssessmentOrigin.LIBRARY,
            imageWidth = 1080,
            imageHeight = 1440,
        ),
        occurredAtEpochMillis = 100,
    )

    private companion object {
        val SEQUENCE = AtomicInteger()
    }
}
