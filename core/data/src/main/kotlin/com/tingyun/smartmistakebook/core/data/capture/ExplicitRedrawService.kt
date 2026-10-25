package com.tingyun.smartmistakebook.core.data.capture

import android.content.Context
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawFailureCode
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.EXPLICIT_REDRAW_OPERATION_ID_PREFIX
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.cleanRedrawTaskId
import com.tingyun.smartmistakebook.core.database.explicitRedrawOperationId
import com.tingyun.smartmistakebook.core.database.isExplicitRedrawOperation
import com.tingyun.smartmistakebook.core.database.port.CleanRedrawTaskPort
import com.tingyun.smartmistakebook.core.database.port.RevisionImageSource
import com.tingyun.smartmistakebook.core.domain.CleanImageGenerator
import com.tingyun.smartmistakebook.core.domain.CleanImageRequest
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawCommand
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawFailureReason
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawPhase
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawRequestResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawState
import com.tingyun.smartmistakebook.core.domain.FigureArtifactRef
import com.tingyun.smartmistakebook.core.model.QUESTION_SOURCE_ROLE
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用户显式重绘的命令标记。它进入作业指纹：保存期净化的指纹来自分类请求，显式重绘的指纹
 * 来自"修订 + 源图 + 用户动作"——两条链永远不会因为指纹相同而互相去重/抢占。
 */
internal const val EXPLICIT_REDRAW_USER_ACTION: String = "USER_REQUESTED"

private const val SHA_256_HEX_CHARS = 64

/** T3 只实现干净题面；其它角色在命令层明确拒绝，不静默改写。 */
internal val EXPLICIT_REDRAW_SUPPORTED_ROLE: VisualRole = VisualRole.CLEAN_IMAGE

/**
 * 显式重绘的作业指纹（64 位小写 SHA-256）。
 *
 * 口径（长度前缀，避免简单拼接的歧义）：`explicit-redraw-request-v1` + `USER_REQUESTED` +
 * 题面修订 + 源图资产 + 源图**字节** sha + 目标角色。
 *
 * - **含 `USER_REQUESTED`**：与保存期净化的分类指纹是两种身份，不能互相命中；
 * - **含修订与源图 sha**：同一修订换了原图就是另一件工作；
 * - **不含理由文字/时刻**：同一修订同一源图的重复点击/重试命中同一行，不重复付费。
 */
internal fun explicitRedrawRequestFingerprint(
    problemRevisionId: String,
    sourceAssetId: String,
    sourceImageSha256: String,
    role: VisualRole,
): String {
    require(problemRevisionId.isNotBlank()) {
        "An explicit redraw revision id must not be blank"
    }
    require(sourceAssetId.isNotBlank()) { "An explicit redraw source asset id must not be blank" }
    require(sourceImageSha256.length == SHA_256_HEX_CHARS) {
        "An explicit redraw source sha must be sha256"
    }
    val canonical = buildString {
        appendField("explicit-redraw-request-v1")
        appendField(EXPLICIT_REDRAW_USER_ACTION)
        appendField(problemRevisionId)
        appendField(sourceAssetId)
        appendField(sourceImageSha256)
        appendField(role.name)
    }
    return figureSha256Hex(canonical.toByteArray(StandardCharsets.UTF_8))
}

/** 来源优先级：原题照片 → 源证据 → 已有干净图。题图/讲解图不是整页题面，不能当重绘源。 */
internal fun pickExplicitRedrawSource(
    sources: List<RevisionImageSource>,
): RevisionImageSource? =
    sources.firstOrNull { it.role == QUESTION_SOURCE_ROLE }
        ?: sources.firstOrNull { it.role == VisualRole.SOURCE_EVIDENCE.name }
        ?: sources.firstOrNull { it.role == VisualRole.CLEAN_IMAGE.name }

/**
 * 显式重绘落盘后的规范资产 id。
 *
 * 与生产挂接实现（`RoomCaptureWorkflowRepository.attachCleanRedrawArtifact`）同一口径：
 * 生成图以 `figureIdentity = "clean-" + sha256(输出字节)` 落盘，资产 id 就是这个幂等键。
 * 只用于把审计 attempt 的 `output_asset_id` 回填；不参与任何预算判断。
 * 4E WG-03：sha 直接取类型化引用携带的 [contentSha256]（构造时已与字节核对），不再重算。
 */
internal fun explicitRedrawAssetId(contentSha256: String): String = "clean-$contentSha256"

/**
 * 4E WG-06 T3：详情页的用户显式重绘服务。
 *
 * **它消灭的具体失败**：
 * - 详情页只有"自动净化"一条链——学生拍到一张分类器判成 TEXT_ONLY（或自动净化失败）的图，
 *   就再也没有办法让这张题面变干净；现在用户动作可以强制重绘；
 * - 重绘请求没有持久身份：进程被杀后没人记得"用户点过重绘"；现在先落
 *   `clean_redraw_task`（确定性 taskId），启动对账按尝试上限续跑；
 * - 晚到的结果挂到新题面：付费前与挂接时都核 current-revision，旧修订只落 `SUPERSEDED`；
 * - 重复点击/重试重复付费：同一修订 + 同一源图 + `USER_REQUESTED` 命中同一行，成功即
 *   `ALREADY_SUCCEEDED`；
 * - 打开/展开/旋转详情页触发派发：本服务没有任何"观察即派发"的路径，只有 [request]
 *   （用户动作）会建行并启动后台执行。
 *
 * **复用**：尝试上限、预算父操作（[com.tingyun.smartmistakebook.core.model.FigureDispatchParent.CleanRedrawTask]）、
 * 付费前核目标、失败码全部沿用 `CleanRedrawTaskRunner` 的状态机；唯一差别是强制路径不做
 * 分类轮，所以 TEXT_ONLY 不会把用户的重绘动作短路成"不需要图"。
 */
class ExplicitRedrawService internal constructor(
    private val tasks: CleanRedrawTaskPort,
    private val egressAllowed: () -> Boolean,
    /** 修订上的视觉引用（角色 + 资产 id + sha）；空表 = 没有可用源图。 */
    private val readRevisionSources: suspend (String) -> List<RevisionImageSource>,
    private val readSourceAsset: suspend (String) -> CanonicalSourceAssetRecord?,
    /** 源图字节（出网前逐位核对）；null = 读不出来，不发不可信的字节。 */
    private val readSourceBytes: suspend (CanonicalSourceAssetRecord) -> ByteArray?,
    /** 生产实现是 `ConfiguredCleanImageGenerator`；null = 当前构建没有可用图像能力。 */
    private val cleanImageGenerator: CleanImageGenerator?,
    /** 挂接（生产是 `CaptureWorkflowRepository.attachCleanRedrawArtifact`，类型化引用）；false = 目标已不在。 */
    private val attach: suspend (String, FigureArtifactRef) -> Boolean,
    /** 审计回填（best-effort）；默认不写。 */
    private val recordOutputAsset: suspend (attemptId: String, assetId: String) -> Unit =
        { _, _ -> },
    /** 后台执行作用域（生产是应用作用域）。 */
    private val scope: CoroutineScope,
    private val maxAttempts: Int = MAX_CLEAN_REDRAW_ATTEMPTS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init {
        require(maxAttempts >= 1) { "An explicit redraw attempt limit must be positive" }
    }

    private val runner = CleanRedrawTaskRunner(
        tasks = tasks,
        egressAllowed = egressAllowed,
        readSourceAsset = readSourceAsset,
        readSourceBytes = readSourceBytes,
        // 强制路径永远不调用分类轮；给一个不会成功的实现，防止未来误接线时静默分类。
        classify = { null },
        generateClean = { record, bytes, parent ->
            cleanImageGenerator?.generateClean(
                CleanImageRequest(
                    originalBytes = bytes,
                    mimeType = record.mimeType,
                    parent = parent,
                    // 逻辑操作身份绑定持久作业（不含 method）；重试仍是同一操作。
                    operationId = explicitRedrawOperationId(parent.taskId),
                ),
            )
        },
        attach = attach,
        maxAttempts = maxAttempts,
        now = now,
        onAttached = { clean -> recordCleanOutput(clean) },
    )

    /**
     * 用户动作入口：先核 current-revision CAS，再建立/复用持久作业身份，最后才交给后台执行。
     *
     * 顺序是硬要求：**任何派发之前**先确认"这一版仍是书里显示的那一版"；命令携带的
     * `expectedRevisionId` 与目标修订不同，说明详情页看到的是旧版，直接拒绝（不建行、不付费）。
     */
    suspend fun request(command: ExplicitRedrawCommand): ExplicitRedrawRequestResult {
        if (command.role != EXPLICIT_REDRAW_SUPPORTED_ROLE) {
            return ExplicitRedrawRequestResult.Rejected("UNSUPPORTED_ROLE")
        }
        if (command.problemRevisionId != command.expectedRevisionId) {
            return ExplicitRedrawRequestResult.NotCurrentRevision
        }
        val revisionId = command.problemRevisionId
        val currentRevision = try {
            tasks.isCurrentProblemRevision(revisionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!currentRevision) return ExplicitRedrawRequestResult.NotCurrentRevision
        // 没有可出网的能力/凭证：不建行（没有尝试过就没有欠账），UI 如实说"先配置模型"。
        // generator 非 null 只说明装配存在；真正的凭证/能力门在这里先问一次，否则任务行会
        // 先建立、尝试次数先被领取，然后才在生成器内部失败（用户看到"重绘失败"而不是
        // "先配置模型"，并白耗一次尝试额度）。
        if (!egressAllowed() || cleanImageGenerator == null || !imageCapabilityAvailable()) {
            return ExplicitRedrawRequestResult.Unavailable
        }
        val sourceRef = try {
            pickExplicitRedrawSource(readRevisionSources(revisionId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return ExplicitRedrawRequestResult.NoSource
        val source = try {
            readSourceAsset(sourceRef.sourceAssetId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return ExplicitRedrawRequestResult.NoSource
        // 源图记录在两次读之间被换掉：不拿旧 sha 建身份（否则这一轮注定挂不上）。
        if (source.contentSha256 != sourceRef.contentSha256) {
            return ExplicitRedrawRequestResult.NoSource
        }

        val fingerprint = explicitRedrawRequestFingerprint(
            problemRevisionId = revisionId,
            sourceAssetId = source.sourceAssetId,
            sourceImageSha256 = source.contentSha256,
            role = command.role,
        )
        val createdAtEpochMillis = now()
        val taskId = cleanRedrawTaskId(
            revisionId = revisionId,
            sourceAssetId = source.sourceAssetId,
            sourceImageSha256 = source.contentSha256,
            requestFingerprint = fingerprint,
        )
        val row = try {
            tasks.createCleanRedrawTask(
                CreateCleanRedrawTaskCommand(
                    taskId = taskId,
                    revisionId = revisionId,
                    sourceAssetId = source.sourceAssetId,
                    sourceImageSha256 = source.contentSha256,
                    requestFingerprint = fingerprint,
                    // 强制路径不重建分类请求；这个值只作为持久诊断身份。
                    subjectId = "explicit-redraw:$revisionId",
                    createdAtEpochMillis = createdAtEpochMillis,
                    operationId = explicitRedrawOperationId(taskId),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return ExplicitRedrawRequestResult.Rejected("TASK_LEDGER_UNAVAILABLE")

        val state = row.toExplicitRedrawState()
        // 同一请求已有终态：成功过的不再付费；SUPERSEDED 不再试。
        if (row.status == CleanRedrawTaskStatus.SUCCEEDED ||
            row.status == CleanRedrawTaskStatus.SUPERSEDED
        ) {
            return ExplicitRedrawRequestResult.AlreadySettled(state)
        }
        // 待核实行（生成请求已发出、结果未知）分两种，必须区分：
        // - **本进程执行者还在跑**（request() 启动的后台执行未结束）：与"在途 PENDING"同一口径，
        //   如实返回 Accepted"进行中"，不再起第二个执行者（领取 CAS 也拒绝该态）。
        // - **执行者不在了**（进程死在生成轮/协程被取消）：runAttempt 对该态显式短路 SKIPPED
        //   （不盲目补发，4E WG-03——外部调用不保证恰好一次），启动对账也不接手（RESUMABLE 只有
        //   PENDING/FAILED）——没有任何东西会再动这一行，走 launchExecution 再返回 Accepted 是
        //   "已受理、实际什么都不会发生"的谎报。如实拒绝并保留行：UI 至少给出"重绘请求未被接受
        //   （AWAITING_VERIFICATION）"的可见反馈，行等显式的"重新核实/放弃"路径。该路径与独立
        //   UI 态需要 core/domain 与 feature/library 的类型/文案改动（他线热区），登记为遗留。
        if (row.status == CleanRedrawTaskStatus.AWAITING_VERIFICATION) {
            if (taskId in inFlightExecutions) {
                return ExplicitRedrawRequestResult.Accepted(state)
            }
            return ExplicitRedrawRequestResult.Rejected("AWAITING_VERIFICATION")
        }
        // 在途（PENDING 且已领取）：另一个执行者正在跑。不再 launch 第二个执行者——
        // 即使启动，领取租约也会拒绝它；这里直接如实返回"进行中"，省掉一次空转。
        if (row.status == CleanRedrawTaskStatus.PENDING && row.attemptCount > 0) {
            return ExplicitRedrawRequestResult.Accepted(state)
        }
        // 尝试额度耗尽（FAILED）：不再领取，UI 显示"已达上限"。
        if (row.attemptCount >= maxAttempts) {
            return ExplicitRedrawRequestResult.AlreadySettled(state)
        }
        launchExecution(taskId)
        return ExplicitRedrawRequestResult.Accepted(state)
    }

    /**
     * 详情页观察口：最近一次显式重绘的状态。只读——观察本身不建行、不派发。
     *
     * 只有带 `explicit-redraw-op:` 前缀的行会被看到；保存期自动净化不冒充"用户请求的重绘"。
     */
    fun observe(problemRevisionId: String): Flow<ExplicitRedrawState> {
        require(problemRevisionId.isNotBlank()) {
            "An explicit redraw revision id must not be blank"
        }
        return tasks.observeLatestCleanRedrawTask(
            revisionId = problemRevisionId,
            operationIdPrefix = EXPLICIT_REDRAW_OPERATION_ID_PREFIX,
        ).map { row -> row?.toExplicitRedrawState() ?: ExplicitRedrawState.Idle }
    }

    /**
     * 启动对账（4E WG-06 T3）：把上一进程留下的显式重绘行（`PENDING`/`FAILED` 且未耗尽
     * 尝试次数）接着做完，返回本次实际动过的行数。
     *
     * - 出网门关着时一行也不动（fail-closed），留待下次启动；
     * - 只接手带 `explicit-redraw-op:` 前缀的行；保存期净化仍由 capture 仓库的对账负责；
     * - 接手的是**用户已经点过重绘**的行——启动本身不会新建任何作业。
     */
    suspend fun resumePending(staleBeforeEpochMillis: Long): Int {
        require(staleBeforeEpochMillis >= 0) {
            "An explicit redraw resume cutoff must not be negative"
        }
        // 对账也不能在没有凭证时"续跑"：那会把上一进程留下的行逐个领取、逐个记失败，
        // 白白消耗用户的尝试额度。没有能力就原地不动，等模型配置恢复后再接手。
        if (!egressAllowed() || cleanImageGenerator == null || !imageCapabilityAvailable()) return 0
        val rows = try {
            tasks.readResumableCleanRedrawTasks(
                maxAttempts = maxAttempts,
                staleBeforeEpochMillis = staleBeforeEpochMillis,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return 0
        }.filter { isExplicitRedrawOperation(it.operationId) }
        var processed = 0
        rows.forEach { row ->
            val outcome = try {
                runner.runExplicit(row, staleBeforeEpochMillis = staleBeforeEpochMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CleanRedrawTaskOutcome.SKIPPED
            }
            if (outcome != CleanRedrawTaskOutcome.SKIPPED) processed += 1
        }
        return processed
    }

    /** 跑一条已存在的显式重绘作业（对账与测试用；不建行）。 */
    internal suspend fun executeTask(taskId: String): CleanRedrawTaskOutcome {
        require(taskId.isNotBlank()) { "An explicit redraw task id must not be blank" }
        val row = tasks.readCleanRedrawTask(taskId) ?: return CleanRedrawTaskOutcome.SKIPPED
        return runner.runExplicit(row)
    }

    /** 凭证/能力门（fail-closed）：生成器自己回答，异常按"没有能力"处理。 */
    private suspend fun imageCapabilityAvailable(): Boolean {
        val generator = cleanImageGenerator ?: return false
        return try {
            generator.isAvailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 本进程仍在跑的显式重绘执行者（taskId）。
     *
     * 它消灭的具体失败：`AWAITING_VERIFICATION` 行无法从行本身区分"生成轮进行中"与
     * "进程死在生成轮的孤儿行"（两种形态在台账里逐字节相同——T3 在发出付费请求**之前**
     * 落该标记，正是为了让崩溃留下"结果未知"的诚实行）。request() 靠它区分：执行者活着
     * → Accepted"进行中"；没有执行者 → 如实 Rejected（没有任何东西会再接手这一行）。
     * 只覆盖 [launchExecution]（request() 的派发路径）；启动对账不接 AWAITING 行
     * （RESUMABLE 只有 PENDING/FAILED），不存在它漏登记的执行者。
     * 进程边界即生命周期：进程死则集合清空，孤儿行如实暴露。
     */
    private val inFlightExecutions: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun launchExecution(taskId: String) {
        inFlightExecutions.add(taskId)
        scope.launch {
            try {
                executeTask(taskId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 行留在 PENDING；启动对账会在尝试额度内接手（不静默重付，也不假装做完了）。
            } finally {
                inFlightExecutions.remove(taskId)
            }
        }
    }

    /**
     * 生成并挂接成功后的审计回填。canonical 图片按内容 sha 回填产物资产 id（与挂接实现的
     * 幂等键同一口径）；JS 快照（4E.2 前）没有 canonical 资产，如实不回填。`attemptId` 为
     * null（没有审计台账/coordinator 未接）时不动作；回填失败由 runner 的 best-effort 包装
     * 吞掉，不改判这一轮。
     */
    private suspend fun recordCleanOutput(artifact: FigureArtifactRef) {
        val attemptId = artifact.attemptId ?: return
        when (artifact) {
            is FigureArtifactRef.CanonicalImage ->
                recordOutputAsset(attemptId, explicitRedrawAssetId(artifact.contentSha256))

            is FigureArtifactRef.JsSnapshot -> Unit
        }
    }

    private fun CleanRedrawTaskRecord.toExplicitRedrawState(): ExplicitRedrawState =
        when (status) {
            CleanRedrawTaskStatus.PENDING -> ExplicitRedrawState(
                phase = ExplicitRedrawPhase.RUNNING,
                taskId = taskId,
                attemptCount = attemptCount,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            CleanRedrawTaskStatus.FAILED -> ExplicitRedrawState(
                phase = ExplicitRedrawPhase.FAILED,
                taskId = taskId,
                attemptCount = attemptCount,
                failureReason = lastFailureCode.toExplicitRedrawFailureReason(),
                canRetry = attemptCount < maxAttempts,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            CleanRedrawTaskStatus.SUCCEEDED -> ExplicitRedrawState(
                phase = ExplicitRedrawPhase.SUCCEEDED,
                taskId = taskId,
                attemptCount = attemptCount,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            CleanRedrawTaskStatus.SUPERSEDED -> ExplicitRedrawState(
                phase = ExplicitRedrawPhase.SUPERSEDED,
                taskId = taskId,
                attemptCount = attemptCount,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            else -> ExplicitRedrawState.Idle
        }

}

/** 显式重绘的失败码 → 详情页可展示的原因（未知码不猜测）。 */
internal fun String?.toExplicitRedrawFailureReason(): ExplicitRedrawFailureReason? = when (this) {
    null -> null
    CleanRedrawFailureCode.SOURCE_UNREADABLE -> ExplicitRedrawFailureReason.SOURCE_UNREADABLE
    CleanRedrawFailureCode.REDRAW_FAILED -> ExplicitRedrawFailureReason.REDRAW_FAILED
    CleanRedrawFailureCode.ATTACH_FAILED -> ExplicitRedrawFailureReason.ATTACH_FAILED
    // CLASSIFY_FAILED 只可能来自保存期路径；显式重绘不做分类轮，真出现就如实归入未知。
    else -> ExplicitRedrawFailureReason.UNKNOWN
}

private fun StringBuilder.appendField(value: String) {
    append(value.length).append(':').append(value).append('|')
}

/**
 * 生产装配口（I1 在 Application 中接线）：
 *
 * - [database] 同时是显式重绘台账、规范资产读口与审计回填口；
 * - [cleanImageGenerator] 是共享 coordinator 下的凭证门实现；null = 没配模型（UI 仍显示入口，
 *   点击后如实回"先配置模型"，不建行）；
 * - [attach] 直接复用 `CaptureWorkflowRepository.attachCleanRedrawArtifact`（同一份落盘 +
 *   CLEAN_IMAGE 引用 + current-revision 判据，不复制第二份实现）；
 * - [scope] 是应用作用域：用户点击后执行，进程被杀由启动对账接手。
 */
object ExplicitRedrawServiceFactory {
    fun create(
        context: Context,
        database: StudyDatabasePort,
        cleanImageGenerator: CleanImageGenerator?,
        attach: suspend (String, FigureArtifactRef) -> Boolean,
        egressAllowed: () -> Boolean,
        scope: CoroutineScope,
    ): ExplicitRedrawService {
        val assetVault = AndroidCanonicalAssetVault(context.applicationContext)
        return ExplicitRedrawService(
            tasks = database,
            egressAllowed = egressAllowed,
            readRevisionSources = { revisionId ->
                database.readRevisionImageSources(revisionId)
            },
            readSourceAsset = { assetId -> database.readCanonicalSourceAsset(assetId) },
            // 文件 IO 必须离开主线程；核对失败即返回 null（调用方落 SOURCE_UNREADABLE）。
            readSourceBytes = { record ->
                withContext(Dispatchers.IO) {
                    try {
                        assetVault.resolve(record).readBytes()
                    } catch (_: Exception) {
                        null
                    }
                }
            },
            cleanImageGenerator = cleanImageGenerator,
            attach = attach,
            recordOutputAsset = { attemptId, assetId ->
                database.attachFigureGenerationAttemptOutput(attemptId, assetId)
            },
            scope = scope,
        )
    }
}
