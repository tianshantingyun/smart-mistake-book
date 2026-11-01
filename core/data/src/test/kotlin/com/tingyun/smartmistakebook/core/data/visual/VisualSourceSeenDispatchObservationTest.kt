package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawRequest
import com.tingyun.smartmistakebook.core.data.model.ImageRedrawResult
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureFidelity
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationTarget
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.FigureSourceEvidence
import com.tingyun.smartmistakebook.core.model.VisualSourceBasis
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X-02 "已发送"记录（`sourceSeenDispatchId`）的派发观测 JVM 测试。
 *
 * 契约口径（visual-generation-contract.md :39）："下一预算内回合实际发送后记录
 * sourceSeenDispatchId/字节摘要；同批准备不冒充已发送。"
 *
 * 钉住的失败：
 * - 源图字节真的随派发出网后，`FigureGenerationOutcome.sourceSeen` 才存在，且其
 *   `sourceSeenDispatchId` 与 attempt 行（持久孪生）一一对应；
 * - 没带源字节的派发（纯文生图）不得冒充"模型已看图"——attemptId 存在而 sourceSeen 为 null；
 * - 授权不足（基准未授权披露原图）在预留之前就 REJECTED：账本 0 次、通道 0 次、
 *   无 attempt 行、无 seen 记录——"同批准备"永远写不出"已发送"证据；
 * - 发送后失败（通道抛出）如实保留 seen 记录：字节已出网是事实，与产物成败无关；
 * - 旧调用（basis = null）路径形状不变，且同样按真实出网字节产出 seen 记录。
 */
class VisualSourceSeenDispatchObservationTest {

    @Test
    fun aSourceCarryingDispatchProducesTheSeenRecord() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = authorizedBasis())

        val outcome = coordinator.generate(request, generateProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        // org.junit.Assert.assertNotNull 返回 void（不回传值，与 kotlin.test 不同）——
        // 先绑定再断言，用 !! 收窄供后续字段读取；断言语义与消息一字未改。
        val seen = outcome.sourceSeen
        assertNotNull("源图字节出网后 seen 记录必须存在", seen)
        assertEquals("seen 记录指向真实的 attempt 行", outcome.attemptId, seen!!.sourceSeenDispatchId)
        assertEquals(
            "字节摘要 = 实际出网源图的 sha",
            figureSha256Hex(SOURCE_BYTES),
            seen.contentSha256,
        )
        assertEquals(1, channel.generateCalls)
        assertEquals(1, ledger.requests.size)
    }

    @Test
    fun aDispatchWithoutSourceBytesNeverImpersonatesASentSource() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = factsOnlyBasis(), source = null)

        val outcome = coordinator.generate(request, generateProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        assertNotNull("派发真实发生（attempt 行存在）", outcome.attemptId)
        assertNull(
            "没带源字节的派发不得冒充'模型已看图'",
            outcome.sourceSeen,
        )
    }

    @Test
    fun anUnauthorizedBasisIsRejectedBeforeAnyReservation() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = factsOnlyBasis())

        val outcome = coordinator.generate(request, editProposal(request))

        assertEquals(FigureGenerationStatus.REJECTED, outcome.status)
        assertTrue(
            "授权拒绝须在预留之前：账本 0 次调用",
            ledger.requests.isEmpty(),
        )
        assertEquals(0, channel.generateCalls)
        assertNull("拒绝没有 attempt 行", outcome.attemptId)
        assertNull("'同批准备'永远写不出'已发送'证据", outcome.sourceSeen)
    }

    @Test
    fun changedSourceBytesAgainstTheFrozenShaAreRejectedBeforeAnyReservation() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(
            basis = authorizedBasis(sha = figureSha256Hex("其它字节".toByteArray())),
        )

        val outcome = coordinator.generate(request, editProposal(request))

        assertEquals(FigureGenerationStatus.REJECTED, outcome.status)
        assertTrue(ledger.requests.isEmpty())
        assertEquals(0, channel.generateCalls)
        assertNull(outcome.attemptId)
        assertNull(outcome.sourceSeen)
    }

    @Test
    fun aFailedSendStillRecordsTheSentSource() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events, fail = true)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = authorizedBasis())

        val outcome = coordinator.generate(request, editProposal(request))

        assertEquals(FigureGenerationStatus.FAILED, outcome.status)
        val seen = outcome.sourceSeen
        assertNotNull("发送后失败仍保留 seen 记录", seen)
        assertEquals(outcome.attemptId, seen!!.sourceSeenDispatchId)
        assertEquals(figureSha256Hex(SOURCE_BYTES), seen.contentSha256)
        assertEquals(1, ledger.requests.size)
        assertEquals(1, channel.generateCalls)
    }

    @Test
    fun needSourceCarriesNeitherAttemptNorSeenRecord() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = factsOnlyBasis())
        val proposal = FigureGenerationPolicy.needSourceProposal(request, "缺少已授权原图")

        val outcome = coordinator.generate(request, proposal)

        assertEquals(FigureGenerationStatus.NEED_SOURCE, outcome.status)
        assertNull(outcome.attemptId)
        assertNull(outcome.sourceSeen)
        assertTrue(
            "证据准备不预留、不派发",
            ledger.requests.isEmpty(),
        )
        assertEquals(0, channel.generateCalls)
    }

    @Test
    fun aLegacyRequestWithoutBasisStillRecordsActualOutboundBytes() = runBlocking {
        val ledger = RecordingLedger()
        val channel = RecordingChannel(ledger.events)
        val coordinator = coordinator(ledger, channel)
        val request = request(basis = null)

        val outcome = coordinator.generate(request, editProposal(request))

        assertEquals(FigureGenerationStatus.GENERATED, outcome.status)
        val seen = outcome.sourceSeen
        assertNotNull("旧调用也按真实出网字节产出 seen 记录", seen)
        assertEquals(outcome.attemptId, seen!!.sourceSeenDispatchId)
        assertEquals(figureSha256Hex(SOURCE_BYTES), seen.contentSha256)
    }

    // -----------------------------------------------------------------

    private fun coordinator(
        ledger: RemoteModelDispatchLedger,
        channel: RecordingChannel,
    ) = DefaultFigureGenerationCoordinator(
        ledger = ledger,
        openBackend = {
            FigureImageBackend(
                configurationFingerprint = "config-1",
                openChannel = { channel },
                closeAction = {},
            )
        },
        // 默认 Noop 会让 attempt 永远"没写进台账"（attemptId=null）——seen 记录因此不存在。
        // 注入真写台账的替身，钉住的是"attempt 行存在 ⇒ seen 记录指向它"的绑定。
        audit = RecordingAuditRecorder(),
    )

    private class RecordingAuditRecorder : FigureGenerationAuditRecorder {
        override suspend fun beginOperation(operation: FigureGenerationAuditOperation): Boolean = true

        override suspend fun markOperationRunning(
            operationId: String,
            updatedAtEpochMillis: Long,
        ): Boolean = true

        override suspend fun beginAttempt(attempt: FigureGenerationAuditAttempt): Boolean = true

        override suspend fun finishAttempt(
            attemptId: String,
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
            endedAtEpochMillis: Long,
        ): Boolean = true

        override suspend fun finishOperation(
            operationId: String,
            outcome: FigureGenerationAuditOutcome,
            reservedRemoteDispatches: Int,
            usedRemoteDispatches: Int,
            finalAnswerReserved: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean = true

        override suspend fun attachOutputAsset(attemptId: String, outputAssetId: String) = Unit
    }

    private fun request(
        basis: VisualSourceBasis?,
        source: FigureSourceEvidence? = defaultSource(),
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(remainingRemoteDispatches = 6, reserveChatFinalAnswer = false),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = source,
        visualSourceBasis = basis,
    )

    private fun editProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "基于已授权原图重建干净题面。",
            reason = "测试",
        )

    private fun generateProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.GENERATE,
            imagePrompt = "基于结构化源事实示意构图。",
            reason = "测试",
        )

    private fun authorizedBasis(
        sha: String = figureSha256Hex(SOURCE_BYTES),
    ): VisualSourceBasis = VisualSourceBasis(
        subjectId = "session-1",
        revision = 1,
        canonicalAssetId = "asset-1",
        contentSha256 = sha,
        allowsImageDisclosure = true,
    )

    private fun factsOnlyBasis(): VisualSourceBasis = VisualSourceBasis(
        subjectId = "session-1",
        revision = 1,
        canonicalAssetId = null,
        contentSha256 = null,
        structuredFacts = "三角形 ABC，AB=5，∠B=90°。",
        allowsStructuredFactsDisclosure = true,
    )

    private fun defaultSource(): FigureSourceEvidence = FigureSourceEvidence(
        assetId = "asset-1",
        contentSha256 = figureSha256Hex(SOURCE_BYTES),
        mimeType = "image/png",
        imageBytes = SOURCE_BYTES,
    )

    private class RecordingLedger(
        private val granted: Boolean = true,
        private val attemptOrdinal: Int = 1,
        val events: MutableList<String> = mutableListOf(),
    ) : RemoteModelDispatchLedger {
        val requests = mutableListOf<RemoteModelDispatchRequest>()

        override suspend fun reserve(
            request: RemoteModelDispatchRequest,
        ): RemoteModelDispatchDecision {
            requests += request
            events += "reserve"
            if (!granted) {
                return RemoteModelDispatchDecision.Denied(
                    code = RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED,
                    message = "逻辑操作的远程派发预算已用尽，未出网",
                )
            }
            return RemoteModelDispatchDecision.Granted(
                RemoteModelDispatchReservation(
                    reservationId = "reservation-${request.operationId}-$attemptOrdinal",
                    operationId = request.operationId,
                    parent = request.parent,
                    remoteKind = request.remoteKind,
                    method = request.method,
                    purpose = request.purpose,
                    policyVersion = request.policyVersion,
                    sourceImageSha256 = request.sourceImageSha256,
                    configurationFingerprint = request.configurationFingerprint,
                    attemptOrdinal = attemptOrdinal,
                    reservedAtEpochMillis = request.occurredAtEpochMillis,
                ),
            )
        }
    }

    private class RecordingChannel(
        private val events: MutableList<String>,
        private val fail: Boolean = false,
    ) : ImageGenerationChannel {
        val resultBytes: ByteArray = byteArrayOf(1, 2, 3, 4)
        var generateCalls: Int = 0
            private set

        override suspend fun generate(
            request: ImageGenerationRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult {
            generateCalls += 1
            events += "post"
            if (fail) throw IllegalStateException("provider down")
            return ImageRedrawResult(
                imageBytes = resultBytes,
                mimeType = "image/png",
                modelVersion = "test-model",
            )
        }

        override suspend fun redrawClean(
            request: ImageRedrawRequest,
            dispatch: FigureImageDispatchBinding,
        ): ImageRedrawResult = throw UnsupportedOperationException("not used in this test")
    }

    private companion object {
        val SOURCE_BYTES = "source-photo".toByteArray()
    }
}
