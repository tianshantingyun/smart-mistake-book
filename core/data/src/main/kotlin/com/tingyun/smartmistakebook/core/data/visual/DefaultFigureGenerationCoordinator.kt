package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.ImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.ImageGenerationRequest
import com.tingyun.smartmistakebook.core.data.model.OpenAiImageGenerationChannel
import com.tingyun.smartmistakebook.core.data.model.imageGenerationConfigurationFingerprint
import com.tingyun.smartmistakebook.core.data.model.resolveGuardedEdits
import com.tingyun.smartmistakebook.core.data.model.resolveImageCredential
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.FigureImageDispatchBinding
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchReservation
import com.tingyun.smartmistakebook.core.model.FigureArtifact
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationSourceKind
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationValidation
import com.tingyun.smartmistakebook.core.model.FigureImageOperation
import com.tingyun.smartmistakebook.core.model.figureBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.util.Arrays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 一次图像后端的生命周期句柄：配置指纹 + **惰性**鉴权通道 + 凭证释放。
 *
 * 通道在 [channel] 第一次调用时才创建：协调器可以先向账本预留（只需要非秘密的
 * [configurationFingerprint]），**被拒时连通道/DNS 都不建立**，更不会发 HTTP。
 * 只由 [openGuardedFigureImageBackend] 构造；协调器在一次生成结束（成功/失败/取消）后
 * 必须 [close]，把本次租约的密钥字符清零。
 */
internal class FigureImageBackend(
    val configurationFingerprint: String,
    private val openChannel: suspend () -> ImageGenerationChannel,
    private val closeAction: () -> Unit,
) : AutoCloseable {
    init {
        require(configurationFingerprint.isNotBlank()) {
            "An image backend needs a configuration fingerprint"
        }
    }

    suspend fun channel(): ImageGenerationChannel = openChannel()

    override fun close() = closeAction()
}

/**
 * 4E.1 生产图像后端：凭证门（`resolveImageCredential` 唯一实现）+ SSRF 防护通道。
 *
 * 没配模型 / 本构建不可出网 / 能力测试未确认图像输入 → null（协调器回 UNAVAILABLE，
 * 一个字节都不发）。这里只读取凭证与派生配置指纹（纯本地）；真正的 SSRF 端点解析与通道
 * 创建推迟到 [FigureImageBackend.channel]——即账本授予之后。
 */
internal suspend fun openGuardedFigureImageBackend(
    configurationStore: ModelConfigurationStore,
    networkRequestsAllowed: Boolean,
): FigureImageBackend? {
    val credential = resolveImageCredential(configurationStore, networkRequestsAllowed) ?: return null
    val configuration = credential.configuration
    val keyChars = credential.apiKey.copyChars()
    var closed = false
    val close = {
        if (!closed) {
            closed = true
            Arrays.fill(keyChars, '\u0000')
            credential.apiKey.close()
        }
    }
    val configurationFingerprint = imageGenerationConfigurationFingerprint(
        configuration.baseUrl,
        OpenAiImageGenerationChannel.DEFAULT_MODEL_ID,
    )
    return FigureImageBackend(
        configurationFingerprint = configurationFingerprint,
        openChannel = {
            try {
                val endpoint = resolveGuardedEdits(configuration.baseUrl)
                OpenAiImageGenerationChannel(
                    baseUrl = endpoint.baseUrl,
                    client = endpoint.client,
                    authorization = "Bearer ${String(keyChars)}",
                    // 与预留使用同一份配置指纹（不依赖端点规范化后的字符串）。
                    configurationFingerprint = configurationFingerprint,
                )
            } catch (cancelled: CancellationException) {
                close()
                throw cancelled
            } catch (failure: Exception) {
                close()
                throw failure
            }
        },
        closeAction = close,
    )
}

/**
 * 统一生图协调器的生产实现（WG-01）。
 *
 * 公共状态机（所有入口同一份）：
 * 1. [FigureGenerationPolicy.validate]：policyVersion / method 显式 / 能力 / 目标 / 预算 /
 *    约束未被降级 / 源证据绑定；
 * 2. 打开后端的**凭证/配置句柄**（凭证门在这里，入口不各自建通道；此时不解析端点）；
 * 3. 先向 [RemoteModelDispatchLedger] 预留——被拒即返回，**不建立图像通道、不发 HTTP**；
 * 4. 只有授予后才构造 [FigureImageDispatchBinding]、建立通道并调用带绑定的方法；
 * 5. 统一产物 [FigureArtifact.ImageAsset] 或有界诊断；后端句柄在 finally 释放。
 *
 * `openBackend` 是**装配缝**：生产注入 [openGuardedFigureImageBackend]，测试注入 fake
 * （fake 也必须经过同一预留顺序）。
 */
class DefaultFigureGenerationCoordinator internal constructor(
    private val ledger: RemoteModelDispatchLedger,
    private val openBackend: suspend () -> FigureImageBackend?,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 4E WG-08 T1b：审计台账记录器。默认 [FigureGenerationAuditRecorder.Noop]——
     * 旧调用（测试/未接审计的装配）不产生任何持久写入，结果与改前逐位一致。
     */
    private val audit: FigureGenerationAuditRecorder = FigureGenerationAuditRecorder.Noop,
) : FigureGenerationCoordinator {

    override suspend fun generate(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationOutcome {
        val validation = FigureGenerationPolicy.validate(request, proposal)
        if (validation is FigureGenerationValidation.Rejected) {
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.REJECTED,
                diagnostics = validation.message,
            )
        }
        return when (proposal.decision) {
            FigureGenerationDecision.NO_FIGURE -> outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.NO_FIGURE,
                diagnostics = proposal.reason.ifBlank { null },
            )

            FigureGenerationDecision.NEED_SOURCE -> outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.NEED_SOURCE,
                diagnostics = proposal.reason.ifBlank { null },
            )

            FigureGenerationDecision.UNAVAILABLE -> outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.UNAVAILABLE,
                diagnostics = proposal.reason.ifBlank { null },
            )

            FigureGenerationDecision.GENERATE -> generateImage(request, proposal)
        }
    }

    private suspend fun generateImage(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationOutcome {
        val method = requireNotNull(proposal.method) {
            "policy accepted a GENERATE proposal without a method"
        }
        if (method != FigureGenerationMethod.IMAGE_MODEL) {
            // 本波次只接通图像方法；JS 运行库未过门，不执行、不假成功（也不广告）。
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.UNAVAILABLE,
                diagnostics = "本波次没有可用的 JS 运行库，未执行",
            )
        }
        val backend = try {
            openBackend()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return outcome(
            request = request,
            proposal = proposal,
            status = FigureGenerationStatus.UNAVAILABLE,
            diagnostics = "本机没有可用的图像能力（凭证/能力/网络门未通过）",
        )
        try {
            return generateWithBackend(request, proposal, backend)
        } finally {
            backend.close()
        }
    }

    private suspend fun generateWithBackend(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
        backend: FigureImageBackend,
    ): FigureGenerationOutcome {
        val sourceBytes = request.source?.imageBytes()
        val sourceSha = sourceBytes?.let(::figureSha256Hex)
        // 审计从"这次操作确实拿到后端、准备预留"开始：PENDING 先落，reservation 授予后
        // 才进 RUNNING 并建 attempt。后端不可用（UNAVAILABLE）没有操作，不建行。
        val state = FigureGenerationAuditState(
            operation = request.auditOperation(proposal, sourceSha, now()),
            finalAnswerReserved = request.finalAnswerReserved(),
            audit = audit,
            now = now,
        )
        try {
            state.begin()
            return dispatchWithReservation(
                request = request,
                proposal = proposal,
                backend = backend,
                state = state,
                sourceBytes = sourceBytes,
                sourceSha = sourceSha,
            )
        } catch (cancelled: CancellationException) {
            // 应用被杀/用户取消：账本已扣的预留与已开始的尝试都如实落 CANCELLED。
            // NonCancellable 是必需的——普通 withContext 在一个已取消的协程里会立刻抛，
            // 终态就永远写不下去。
            withContext(NonCancellable) { state.cancel() }
            throw cancelled
        }
    }

    private suspend fun dispatchWithReservation(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
        backend: FigureImageBackend,
        state: FigureGenerationAuditState,
        sourceBytes: ByteArray?,
        sourceSha: String?,
    ): FigureGenerationOutcome {
        val occurredAt = now()
        val reservationDecision = try {
            ledger.reserve(
                RemoteModelDispatchRequest(
                    operationId = request.operationId,
                    parent = requireNotNull(request.dispatchParent) {
                        "policy accepted an unbound figure request"
                    },
                    remoteKind = RemoteModelDispatchKind.IMAGE_GENERATION,
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    purpose = request.purpose,
                    policyVersion = proposal.policyVersion,
                    sourceImageSha256 = sourceSha,
                    configurationFingerprint = backend.configurationFingerprint,
                    occurredAtEpochMillis = occurredAt,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state.finishFailedOperation()
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.FAILED,
                diagnostics = boundedFailure(failure),
            )
        }
        val reservation = when (reservationDecision) {
            is RemoteModelDispatchDecision.Granted -> reservationDecision.reservation
            is RemoteModelDispatchDecision.Denied -> {
                // 没有授予就没有派发：operation 落 FAILED，计数 0/0，attempt 不建。
                state.finishFailedOperation()
                return outcome(
                    request = request,
                    proposal = proposal,
                    status = FigureGenerationStatus.FAILED,
                    diagnostics = reservationDecision.message,
                )
            }
        }
        // 顺序是硬要求：operation PENDING → RUNNING，attempt 必须在 reservation 授予之后
        // 才 RUNNING（"有预留才算开始一次尝试"）。
        state.beginAttempt(reservation)
        // KD-59：绑定只能从账本授予的预留派生（构造器 internal）。
        val dispatch = FigureImageDispatchBinding.fromReservation(reservation)
        val imagePrompt = requireNotNull(proposal.imagePrompt) {
            "policy accepted an IMAGE_MODEL proposal without an image prompt"
        }
        val imageOperation = proposal.imageOperation
        if (imageOperation == null) {
            state.failAttempt("IMAGE_MODEL proposal 缺少图像操作")
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.REJECTED,
                diagnostics = "IMAGE_MODEL proposal 缺少图像操作",
                attemptId = state.attemptId,
            )
        }
        val channel = try {
            backend.channel()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state.failAttempt(boundedFailure(failure))
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.FAILED,
                diagnostics = boundedFailure(failure),
                attemptId = state.attemptId,
            )
        }
        // 从这里开始真的可能在线上花掉一次派发：即使后面失败/取消，used 也如实记 1。
        state.markDispatched()
        val generated = try {
            when (imageOperation) {
                FigureImageOperation.EDIT -> {
                    // EDIT 走带源图的生成请求（通道内部即 `/images/edits`）；这与旧工具链
                    // 的重绘路径同形状，源图字节来自宿主核对后的证据。
                    val bytes = requireNotNull(sourceBytes) {
                        "policy accepted an EDIT without source bytes"
                    }
                    val mimeType = requireNotNull(request.source?.mimeType) {
                        "policy accepted an EDIT without a source mime type"
                    }
                    channel.generate(
                        ImageGenerationRequest(
                            prompt = imagePrompt,
                            sourceImageBytes = bytes,
                            sourceImageMimeType = mimeType,
                        ),
                        dispatch,
                    )
                }

                FigureImageOperation.GENERATE -> channel.generate(
                    ImageGenerationRequest(
                        prompt = imagePrompt,
                        sourceImageBytes = sourceBytes,
                        sourceImageMimeType = request.source?.mimeType,
                    ),
                    dispatch,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val diagnostics = boundedFailure(failure)
            state.failAttempt(diagnostics)
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.FAILED,
                diagnostics = diagnostics,
                attemptId = state.attemptId,
            )
        }
        val artifact = FigureArtifact.ImageAsset(
            method = FigureGenerationMethod.IMAGE_MODEL,
            purpose = request.purpose,
            mimeType = generated.mimeType,
            contentSha256 = figureSha256Hex(generated.imageBytes),
            modelId = generated.modelVersion.ifBlank { null },
            assetId = null,
            imageBytes = generated.imageBytes,
        )
        state.succeed()
        return outcome(
            request = request,
            proposal = proposal,
            status = FigureGenerationStatus.GENERATED,
            artifact = artifact,
            attemptId = state.attemptId,
        )
    }

    /**
     * 一次操作的审计状态（只活在这次调用里）。
     *
     * 所有写入都经 [auditWrite]：recorder 失败/取消失败**不影响**生成结果；取消只由
     * [generateWithBackend] 的 NonCancellable 收尾统一落账。
     */
    private class FigureGenerationAuditState(
        private val operation: FigureGenerationAuditOperation,
        private val finalAnswerReserved: Boolean,
        private val audit: FigureGenerationAuditRecorder,
        private val now: () -> Long,
    ) {
        private var operationRecorded: Boolean = false
        private var attempt: FigureGenerationAuditAttempt? = null
        private var attemptRecorded: Boolean = false
        private var reservedRemoteDispatches: Int = 0
        private var usedRemoteDispatches: Int = 0

        /** 只有真的写进 attempt 行时才回给入口；否则 null（入口据此跳过回填）。 */
        val attemptId: String?
            get() = if (attemptRecorded) attempt?.attemptId else null

        suspend fun begin() {
            operationRecorded = auditWrite { audit.beginOperation(operation) } ?: false
        }

        suspend fun beginAttempt(reservation: RemoteModelDispatchReservation) {
            reservedRemoteDispatches = 1
            auditWrite { audit.markOperationRunning(operation.operationId, now()) }
            val row = FigureGenerationAuditAttempt(
                attemptId = "figure-attempt:${operation.operationId}:${reservation.attemptOrdinal}:" +
                    reservation.method.name,
                operationId = operation.operationId,
                attemptOrdinal = reservation.attemptOrdinal,
                method = reservation.method,
                reservationId = reservation.reservationId,
                inputFingerprint = operation.basisFingerprint,
                sourceSha256 = reservation.sourceImageSha256,
                startedAtEpochMillis = now(),
            )
            attempt = row
            attemptRecorded = auditWrite { audit.beginAttempt(row) } ?: false
        }

        fun markDispatched() {
            usedRemoteDispatches = 1
        }

        suspend fun succeed() {
            finishAttempt(FigureGenerationAuditOutcome.SUCCEEDED, outputAssetId = null, diagnostics = null)
            finishOperation(FigureGenerationAuditOutcome.SUCCEEDED)
        }

        suspend fun failAttempt(diagnostics: String) {
            finishAttempt(FigureGenerationAuditOutcome.FAILED, outputAssetId = null, diagnostics = diagnostics)
            finishOperation(FigureGenerationAuditOutcome.FAILED)
        }

        suspend fun finishFailedOperation() {
            finishOperation(FigureGenerationAuditOutcome.FAILED)
        }

        suspend fun cancel() {
            finishAttempt(
                FigureGenerationAuditOutcome.CANCELLED,
                outputAssetId = null,
                diagnostics = "已取消",
            )
            finishOperation(FigureGenerationAuditOutcome.CANCELLED)
        }

        private suspend fun finishAttempt(
            outcome: FigureGenerationAuditOutcome,
            outputAssetId: String?,
            diagnostics: String?,
        ) {
            val row = attempt ?: return
            if (!attemptRecorded) return
            auditWrite {
                audit.finishAttempt(
                    attemptId = row.attemptId,
                    outcome = outcome,
                    outputAssetId = outputAssetId,
                    diagnostics = diagnostics,
                    endedAtEpochMillis = now(),
                )
            }
        }

        private suspend fun finishOperation(outcome: FigureGenerationAuditOutcome) {
            if (!operationRecorded) return
            auditWrite {
                audit.finishOperation(
                    operationId = operation.operationId,
                    outcome = outcome,
                    reservedRemoteDispatches = reservedRemoteDispatches,
                    usedRemoteDispatches = usedRemoteDispatches,
                    finalAnswerReserved = finalAnswerReserved,
                    updatedAtEpochMillis = now(),
                )
            }
        }
    }

    private fun FigureGenerationRequest.auditOperation(
        proposal: FigureGenerationProposal,
        sourceSha256: String?,
        createdAtEpochMillis: Long,
    ): FigureGenerationAuditOperation {
        val parent = requireNotNull(dispatchParent) {
            "policy accepted an unbound figure request"
        }
        return FigureGenerationAuditOperation(
            operationId = operationId,
            sourceKind = sourceKind ?: when (parent) {
                is FigureDispatchParent.ModelTask -> FigureGenerationSourceKind.TUTOR_MESSAGE
                is FigureDispatchParent.CleanRedrawTask -> FigureGenerationSourceKind.CLEAN_REDRAW
            },
            sourceId = sourceId ?: when (parent) {
                is FigureDispatchParent.ModelTask -> parent.requestId
                is FigureDispatchParent.CleanRedrawTask -> parent.taskId
            },
            basisFingerprint = basisFingerprint
                ?: figureBasisFingerprint(operationId, sourceSha256, proposal.imagePrompt),
            purpose = purpose,
            policyVersion = proposal.policyVersion,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }

    private fun FigureGenerationRequest.finalAnswerReserved(): Boolean =
        (dispatchParent as? FigureDispatchParent.ModelTask)?.reserveChatFinalAnswer ?: false

    private fun outcome(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
        status: FigureGenerationStatus,
        artifact: FigureArtifact? = null,
        diagnostics: String? = null,
        attemptId: String? = null,
    ): FigureGenerationOutcome = FigureGenerationOutcome(
        operationId = request.operationId,
        purpose = request.purpose,
        method = proposal.method,
        status = status,
        artifact = artifact,
        diagnostics = diagnostics?.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS),
        attemptId = attemptId,
    )

    private fun boundedFailure(failure: Exception): String =
        "${failure::class.simpleName}: ${failure.message ?: "无详细信息"}"
            .take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
}

/**
 * 审计写入的 best-effort 包装：**取消除外**，任何持久化失败都不改变生成本身的结论。
 * 单独抽出来是因为 operation/attempt 的每个写口都要同一条语义（写不进 ≠ 没生成）。
 */
private suspend fun <T> auditWrite(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

/**
 * app 装配口：一个共享 coordinator（共享 ledger + 共享凭证门/通道工厂），
 * 注入讲题与净化入口。没配模型时调用方保持 null（各入口维持既有"不可用"行为）。
 *
 * 4E WG-08 T1b 起同时接入审计记录器：操作/尝试行落 schema 69 的两张表。记录器只写审计，
 * 不读预算；数据库端口不可用时（测试替身/旧库）默认实现"做不到"，生成路径不受影响。
 */
object FigureGenerationCoordinationFactory {
    fun create(
        database: StudyDatabasePort,
        configurationStore: ModelConfigurationStore,
        networkRequestsAllowed: Boolean,
    ): FigureGenerationCoordinator = DefaultFigureGenerationCoordinator(
        ledger = RoomFigureDispatchLedger(database),
        openBackend = {
            openGuardedFigureImageBackend(
                configurationStore = configurationStore,
                networkRequestsAllowed = networkRequestsAllowed,
            )
        },
        audit = RoomFigureGenerationAuditRecorder(database),
    )
}
