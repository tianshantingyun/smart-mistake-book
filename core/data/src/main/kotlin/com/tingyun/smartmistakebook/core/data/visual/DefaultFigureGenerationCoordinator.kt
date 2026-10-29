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
import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
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
import com.tingyun.smartmistakebook.core.model.figureJsBasisFingerprint
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.nio.charset.StandardCharsets
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
 *
 * **双后端（批次 34 / A-01）**：上面 2–5 步是 IMAGE_MODEL 的路径。JS 走
 * [generateLocalJs]——不打开图像后端、不向 [RemoteModelDispatchLedger] 预留，只在本地
 * [VisualExecutionEngine] 上执行；引擎与宿主帧输入默认不注入（生产仍回 UNAVAILABLE）。
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
    /**
     * 4E 双后端装配（A-01）：JS 的本地执行引擎。**默认 null = 生产现状**——没有引擎时
     * JS 仍回 UNAVAILABLE，能力快照/广告面一字不动。本参数消灭的具体失败：policy 已能校验
     * JS，协调器却永远回 UNAVAILABLE，于是"本地方法可执行"无法达成，也无法用 JVM 用例
     * 证明"JS 不占远程派发"。
     *
     * 引擎生命周期归装配方：协调器**不**调用 [VisualExecutionEngine.close]（同一引擎可被
     * 多次生成复用，关闭它属于宿主退出/重建环境）。
     */
    private val visualEngine: VisualExecutionEngine? = null,
    /**
     * JS 执行所需的宿主帧输入（viewport/theme/seed/timeMs）。默认 null——与 [visualEngine]
     * 成对注入；没有它协调器不自己编帧，如实回 UNAVAILABLE。
     */
    private val visualFrameInputs: VisualFrameInputs? = null,
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
        return when (method) {
            // 图像方法：原来的一行不动（预留 → 通道 → 产物）。
            FigureGenerationMethod.IMAGE_MODEL -> generateImageViaModel(request, proposal)
            // 本地方法：新分支，**绝不**经过账本预留。
            FigureGenerationMethod.JS -> generateLocalJs(request, proposal)
        }
    }

    private suspend fun generateImageViaModel(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationOutcome {
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

    /**
     * JS 本地执行（A-01）。顺序与红线：
     * 1. 没有引擎/没有帧 → 现状 UNAVAILABLE（**不建 attempt、不碰账本**，与"未接通"逐位一致）；
     * 2. 审计：operation PENDING → RUNNING，本地 attempt（`reservation_id=NULL`，序号由持久层
     *    在写事务里分配）→ 引擎执行；
     * 3. 结果映射：Rendered → GENERATED + [FigureArtifact.Program]；其余 Rejected / 异常 →
     *    有界诊断 FAILED。两类例外折设施类 UNAVAILABLE（不是程序错、不回喂修 program）：
     *    ENGINE_CLOSED（本机没有引擎）与 INVALID_PROGRAM + `[UNATTRIBUTED]` 前缀（引擎失败
     *    原因不可归属，P-11 消费点）；
     * 4. **不调用 `ledger.reserve`**：JS 没有端点/模型配置指纹，伪造一个并给
     *    `dispatch_count` +1 正是契约禁止的"本地执行占远程派发"。
     */
    private suspend fun generateLocalJs(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationOutcome {
        val engine = visualEngine ?: return outcome(
            request = request,
            proposal = proposal,
            status = FigureGenerationStatus.UNAVAILABLE,
            diagnostics = "本波次没有可用的 JS 运行库，未执行",
        )
        val frame = try {
            visualFrameInputs?.forGeneration(request, proposal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.FAILED,
                diagnostics = boundedFailure(failure),
            )
        }
        if (frame == null) {
            return outcome(
                request = request,
                proposal = proposal,
                status = FigureGenerationStatus.UNAVAILABLE,
                diagnostics = "本机没有冻结的宿主帧输入，未执行本地 JS",
            )
        }
        val program = requireNotNull(proposal.program) {
            "policy accepted a JS proposal without a program"
        }
        val sourceSha = request.source?.imageBytes()?.let(::figureSha256Hex)
        val state = FigureGenerationAuditState(
            operation = request.auditOperation(proposal, sourceSha, now()),
            finalAnswerReserved = request.finalAnswerReserved(),
            audit = audit,
            now = now,
        )
        try {
            state.begin()
            state.beginLocalAttempt(FigureGenerationMethod.JS, sourceSha)
            val programId = visualProgramId(request.operationId, program)
            val execution = try {
                engine.execute(
                    VisualExecutionRequest(
                        programId = programId,
                        sourceJs = program,
                        sceneRevision = VISUAL_SCENE_REVISION,
                        stateJson = null,
                        viewport = frame.viewport,
                        timeMs = frame.timeMs,
                        seed = frame.seed,
                        theme = frame.theme,
                        // P-12②（批次 35）：显式重试位透传给本地引擎。生产 true 调用点=
                        // [FigureGenerationRequest.explicitUserRetry] 的 KDoc 契约；JS 熔断
                        // 的恒等物是 program 摘要，只有这里能解除。远程路径不消费该位
                        // （IMAGE_MODEL 的重试预算由账本管理），dispatchWithReservation 不读它。
                        explicitUserRetry = request.explicitUserRetry,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // 端口承诺不抛；防御性兜底，仍按"这次执行失败"记账。
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
            return when (execution) {
                is VisualExecutionResult.Rendered -> {
                    state.succeed()
                    outcome(
                        request = request,
                        proposal = proposal,
                        status = FigureGenerationStatus.GENERATED,
                        artifact = FigureArtifact.Program(
                            purpose = request.purpose,
                            programId = programId,
                            sourceJs = program,
                        ),
                        attemptId = state.attemptId,
                    )
                }

                is VisualExecutionResult.Rejected -> {
                    val diagnostics = boundedRejection(execution)
                    state.failAttempt(diagnostics)
                    outcome(
                        request = request,
                        proposal = proposal,
                        status = when {
                            // 引擎已关 = 本机没有可用引擎，与"未接通"同一语义。
                            execution.reason == VisualExecutionRejection.ENGINE_CLOSED ->
                                FigureGenerationStatus.UNAVAILABLE
                            // P-11 消费点（批次 35）：引擎失败但原因不可归属（分类器"丢失哨兵
                            // + 低堆"兜底，诊断以 [UNATTRIBUTED] 开头）。它不是"程序错"——
                            // 折 FAILED 会把设施类失败回喂成"去修 program"（R3 §6.6 退化
                            // 口径从登记变生效）。折设施类 UNAVAILABLE（与 ENGINE_CLOSED
                            // 同出口）；诊断原文带前缀逐字保留，审计/trace 仍可回看真实原因，
                            // 两类设施故障凭诊断前缀（ENGINE_CLOSED: vs INVALID_PROGRAM:
                            // [UNATTRIBUTED]）可区分。
                            execution.reason == VisualExecutionRejection.INVALID_PROGRAM &&
                                execution.diagnostics.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX) ->
                                FigureGenerationStatus.UNAVAILABLE

                            else -> FigureGenerationStatus.FAILED
                        },
                        diagnostics = diagnostics,
                        attemptId = state.attemptId,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            // 与图像路径同一收尾：账本没动，attempt/operation 如实落 CANCELLED。
            withContext(NonCancellable) { state.cancel() }
            throw cancelled
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

        /**
         * 本地方法（JS）尝试：没有预留，`reservedRemoteDispatches` 保持 0——本地执行不占
         * 远程派发，终态 operation 写 0/0 就是"0 远程派发"的正向证据。
         *
         * 序号不在这里决定：持久层在同一个写事务里分配（负数区间）。若台账写不进
         * （Noop/无库/写失败），`attemptRecorded=false` → `attemptId=null`，生成照常继续。
         */
        suspend fun beginLocalAttempt(
            method: FigureGenerationMethod,
            sourceSha256: String?,
        ) {
            auditWrite { audit.markOperationRunning(operation.operationId, now()) }
            val row: FigureGenerationAuditAttempt? = auditWrite {
                audit.beginLocalAttempt(
                    operationId = operation.operationId,
                    method = method,
                    inputFingerprint = operation.basisFingerprint,
                    sourceSha256 = sourceSha256,
                    startedAtEpochMillis = now(),
                )
            }
            attempt = row
            attemptRecorded = row != null
        }

        fun markDispatched() {
            usedRemoteDispatches = 1
        }

        /**
         * 生成成功即落 SUCCEEDED，`output_asset_id` 留给入口落盘后回填（coordinator 不知道
         * 资产 id）。若入口 persist/attach 失败，这一行会短暂地"成功但无产物"；启动对账
         * （`FigureGenerationReconciler`）会把结束时刻早于进程启动、仍无 output 的 SUCCEEDED
         * 行纠正为 FAILED 待核实，因此不会永久留下假成功。
         *
         * **本地 JS 成功是例外**：它的产物是 `FigureArtifact.Program`（内存画布指令），
         * 按设计没有 `canonical_source_asset` 可回填；对账第二段因此不把
         * `method = 'JS' AND reservation_id IS NULL`（本地写径的唯一形状）当假成功候选，
         * 不会把一条真实的 JS 成功改判为"失败待核实"。
         */
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
            basisFingerprint = basisFingerprint ?: when (proposal.method) {
                // A-08：JS 的 imagePrompt 恒为 null，旧回退会让两个不同 program 得到同一
                // input_fingerprint；JS 一律用 program 参与规范化（源码即输入）。
                FigureGenerationMethod.JS -> figureJsBasisFingerprint(
                    operationId = operationId,
                    sourceSha256 = sourceSha256,
                    program = requireNotNull(proposal.program) {
                        "policy accepted a JS proposal without a program"
                    },
                )

                FigureGenerationMethod.IMAGE_MODEL,
                null,
                -> figureBasisFingerprint(operationId, sourceSha256, proposal.imagePrompt)
            },
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

    /**
     * 引擎拒绝 → 有界诊断。保留 reject 分类名：宿主据此区分"该改输入"（INVALID_PROGRAM 等）
     * 与"该稍后重排"（BUSY/HARD_DEADLINE_EXCEEDED）；不截断成一句"生成失败"。
     */
    private fun boundedRejection(rejection: VisualExecutionResult.Rejected): String =
        "${rejection.reason.name}: ${rejection.diagnostics}"
            .take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)

    /**
     * 程序身份：`visual:{operationId}:{sha256(program) 前 16 位}`。契约 §8"源码改变即新 program"，
     * 不需要读库就能稳定复算；同一个 program 的 programId 与 [figureJsBasisFingerprint] 同源。
     */
    private fun visualProgramId(operationId: String, program: String): String {
        val digest = figureSha256Hex(program.toByteArray(StandardCharsets.UTF_8))
        return "visual:$operationId:${digest.take(VISUAL_PROGRAM_ID_DIGEST_CHARS)}"
    }

    private companion object {
        /** 契约 §8：宿主分配的场景修订号从 1 起；本批不恢复历史场景，首次执行恒为 1。 */
        const val VISUAL_SCENE_REVISION: Int = 1

        const val VISUAL_PROGRAM_ID_DIGEST_CHARS: Int = 16
    }
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
    /**
     * 生产装配口。**JS 的两个参数默认 null**：生产继续走 UNAVAILABLE，能力快照/广告面
     * 一字不动；测试或后续批次可显式注入引擎与帧输入（不经过任何可变全局开关）。
     */
    fun create(
        database: StudyDatabasePort,
        configurationStore: ModelConfigurationStore,
        networkRequestsAllowed: Boolean,
        visualEngine: VisualExecutionEngine? = null,
        visualFrameInputs: VisualFrameInputs? = null,
    ): FigureGenerationCoordinator = DefaultFigureGenerationCoordinator(
        ledger = RoomFigureDispatchLedger(database),
        openBackend = {
            openGuardedFigureImageBackend(
                configurationStore = configurationStore,
                networkRequestsAllowed = networkRequestsAllowed,
            )
        },
        audit = RoomFigureGenerationAuditRecorder(database),
        visualEngine = visualEngine,
        visualFrameInputs = visualFrameInputs,
    )
}
