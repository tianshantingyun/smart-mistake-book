package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.BeginFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationAttemptCommand
import com.tingyun.smartmistakebook.core.database.port.FinishFigureGenerationOperationCommand
import com.tingyun.smartmistakebook.core.database.port.FigureGenerationRecorderPort
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-08 T1b：审计记录器 → 持久端口命令的映射（JVM）。
 *
 * 这里钉住的是"写到数据库里的那一行到底是什么"：状态词、source kind 的线上值、审计计数
 * （reserved/used/finalAnswerReserved）与 learner 口径。数据库本身的迁移/约束由 T1a 的
 * 契约测试与设备矩阵覆盖；本用例不需要 Android/Room。
 */
class RoomFigureGenerationAuditRecorderTest {

    @Test
    fun mapsOperationAndAttemptLifecycleToPortCommands() = runBlocking {
        val port = RecordingAuditPort()
        val recorder = RoomFigureGenerationAuditRecorder(port)

        assertTrue(
            recorder.beginOperation(
                FigureGenerationAuditOperation(
                    operationId = "op-1",
                    sourceKind = FigureGenerationSourceKind.PROBLEM_REVISION,
                    sourceId = "revision-1",
                    basisFingerprint = "c".repeat(64),
                    purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
                    policyVersion = "figure-generation-policy-v1",
                    createdAtEpochMillis = 10,
                ),
            ),
        )
        assertTrue(recorder.markOperationRunning("op-1", 11))
        assertTrue(
            recorder.beginAttempt(
                FigureGenerationAuditAttempt(
                    attemptId = "figure-attempt:op-1:1:IMAGE_MODEL",
                    operationId = "op-1",
                    attemptOrdinal = 1,
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    reservationId = "reservation-1",
                    inputFingerprint = "d".repeat(64),
                    sourceSha256 = "e".repeat(64),
                    startedAtEpochMillis = 12,
                ),
            ),
        )
        assertTrue(
            recorder.finishAttempt(
                attemptId = "figure-attempt:op-1:1:IMAGE_MODEL",
                outcome = FigureGenerationAuditOutcome.SUCCEEDED,
                outputAssetId = null,
                diagnostics = null,
                endedAtEpochMillis = 13,
            ),
        )
        recorder.attachOutputAsset("figure-attempt:op-1:1:IMAGE_MODEL", "asset-1")
        assertTrue(
            recorder.finishOperation(
                operationId = "op-1",
                outcome = FigureGenerationAuditOutcome.SUCCEEDED,
                reservedRemoteDispatches = 1,
                usedRemoteDispatches = 1,
                finalAnswerReserved = true,
                updatedAtEpochMillis = 14,
            ),
        )

        val beginOperation = requireNotNull(port.beginOperation)
        assertEquals("op-1", beginOperation.operationId)
        assertEquals("learner:local", beginOperation.learnerId)
        assertEquals("PROBLEM_REVISION", beginOperation.sourceKind)
        assertEquals("revision-1", beginOperation.sourceId)
        assertEquals("c".repeat(64), beginOperation.basisFingerprint)
        assertEquals("SOURCE_RECONSTRUCTION", beginOperation.purpose)
        assertEquals("figure-generation-policy-v1", beginOperation.policyVersion)
        assertEquals("PENDING", beginOperation.status)
        assertEquals(10, beginOperation.createdAtEpochMillis)

        assertEquals(listOf("op-1" to 11L), port.runningMarks)

        val beginAttempt = requireNotNull(port.beginAttempt)
        assertEquals("figure-attempt:op-1:1:IMAGE_MODEL", beginAttempt.attemptId)
        assertEquals("op-1", beginAttempt.operationId)
        assertEquals(1, beginAttempt.attemptOrdinal)
        assertEquals("IMAGE_MODEL", beginAttempt.method)
        assertEquals("RUNNING", beginAttempt.status)
        assertEquals("reservation-1", beginAttempt.reservationId)
        assertEquals("d".repeat(64), beginAttempt.inputFingerprint)
        assertEquals("e".repeat(64), beginAttempt.sourceSha256)
        assertEquals(12, beginAttempt.startedAtEpochMillis)

        val finishAttempt = requireNotNull(port.finishAttempt)
        assertEquals("SUCCEEDED", finishAttempt.status)
        assertEquals(null, finishAttempt.outputAssetId)
        assertEquals(13, finishAttempt.endedAtEpochMillis)

        assertEquals(listOf("figure-attempt:op-1:1:IMAGE_MODEL" to "asset-1"), port.outputAttachments)

        val finishOperation = requireNotNull(port.finishOperation)
        assertEquals("SUCCEEDED", finishOperation.status)
        assertEquals(1, finishOperation.reservedRemoteDispatches)
        assertEquals(1, finishOperation.usedRemoteDispatches)
        assertTrue(finishOperation.finalAnswerReserved)
        assertEquals(14, finishOperation.updatedAtEpochMillis)
    }

    @Test
    fun aPortThatCannotWriteIsReportedAsNotRecordedWithoutThrowing() = runBlocking {
        val recorder = RoomFigureGenerationAuditRecorder(UnavailableAuditPort())

        assertFalse(
            recorder.beginOperation(
                FigureGenerationAuditOperation(
                    operationId = "op-2",
                    sourceKind = FigureGenerationSourceKind.TUTOR_MESSAGE,
                    sourceId = "message-2",
                    basisFingerprint = "f".repeat(64),
                    purpose = FigureGenerationPurpose.EXPLANATION,
                    policyVersion = "figure-generation-policy-v1",
                    createdAtEpochMillis = 20,
                ),
            ),
        )
        assertFalse(recorder.markOperationRunning("op-2", 21))
        assertFalse(
            recorder.finishOperation(
                operationId = "op-2",
                outcome = FigureGenerationAuditOutcome.FAILED,
                reservedRemoteDispatches = 0,
                usedRemoteDispatches = 0,
                finalAnswerReserved = false,
                updatedAtEpochMillis = 22,
            ),
        )
        // 回填是 best-effort：端口的"做不到"不能把已经成功的生成改判为失败。
        recorder.attachOutputAsset("attempt-missing", "asset-missing")
    }

    private class RecordingAuditPort : FigureGenerationRecorderPort {
        var beginOperation: BeginFigureGenerationOperationCommand? = null
        val runningMarks = mutableListOf<Pair<String, Long>>()
        var beginAttempt: BeginFigureGenerationAttemptCommand? = null
        var finishAttempt: FinishFigureGenerationAttemptCommand? = null
        var finishOperation: FinishFigureGenerationOperationCommand? = null
        val outputAttachments = mutableListOf<Pair<String, String>>()

        override suspend fun beginFigureGenerationOperation(
            command: BeginFigureGenerationOperationCommand,
        ): Boolean {
            beginOperation = command
            return true
        }

        override suspend fun markFigureGenerationOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean {
            runningMarks += operationId to updatedAtEpochMillis
            return true
        }

        override suspend fun beginFigureGenerationAttempt(
            command: BeginFigureGenerationAttemptCommand,
        ): Boolean {
            beginAttempt = command
            return true
        }

        override suspend fun finishFigureGenerationAttempt(
            command: FinishFigureGenerationAttemptCommand,
        ): Boolean {
            finishAttempt = command
            return true
        }

        override suspend fun finishFigureGenerationOperation(
            command: FinishFigureGenerationOperationCommand,
        ): Boolean {
            finishOperation = command
            return true
        }

        override suspend fun attachFigureGenerationAttemptOutput(
            attemptId: String,
            outputAssetId: String,
        ): Boolean {
            outputAttachments += attemptId to outputAssetId
            return true
        }
    }

    private class UnavailableAuditPort : FigureGenerationRecorderPort
}
