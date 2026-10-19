package com.tingyun.smartmistakebook.core.data.capture

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.ClaimCleanRedrawTaskAttemptCommand
import com.tingyun.smartmistakebook.core.database.CleanRedrawFailureCode
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskStatus
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.FinishCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.cleanRedrawTaskId
import com.tingyun.smartmistakebook.core.database.port.CleanRedrawTaskPort
import com.tingyun.smartmistakebook.core.domain.CleanImageResult
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ImagePipelineProblemKind
import com.tingyun.smartmistakebook.core.model.ModelTaskLogicalOperationFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import kotlinx.coroutines.CancellationException

/**
 * 一次净化作业最多尝试几次（AUD-09）。先例是 `BatchImportDriver` / `ExportPdfWorker` 的
 * `MAX_ATTEMPTS = 3`：有限重试、不无限占着启动路径，也不让一次坏源图把费用拖成无底洞。
 */
internal const val MAX_CLEAN_REDRAW_ATTEMPTS = 3

/**
 * 一个净化作业的身份（AUD-09 ①）：要挂到哪条题面修订上（[revisionId]）、做的是哪张源图
 * （[sourceAssetId]）、以及分类请求的题目身份（[subjectId]，提交路径传草稿 id）。
 *
 * 源图**字节** sha 不在参数里、由执行器从规范资产行现读（同一张图的"同"必须按字节成立）。
 */
internal data class CleanRedrawTaskIdentity(
    val revisionId: String,
    val sourceAssetId: String,
    val subjectId: String,
) {
    init {
        require(revisionId.isNotBlank()) { "A clean redraw revision id must not be blank" }
        require(sourceAssetId.isNotBlank()) { "A clean redraw source asset id must not be blank" }
        require(subjectId.isNotBlank()) { "A clean redraw subject id must not be blank" }
    }
}

/** 一次净化作业的结局（提交路径拿它记账/打日志，不再把返回值丢掉）。 */
internal enum class CleanRedrawTaskOutcome {
    /** 作业做完（含"分类判定为纯文字题，不需要重绘"——作业本身完成）。 */
    SUCCEEDED,

    /** 同一请求指纹已有成功行（或本行已成功）：**不重复付费**。 */
    ALREADY_SUCCEEDED,

    /** 这一轮失败，失败码已落台账；尝试次数未耗尽时下次启动续跑。 */
    FAILED,

    /** 没有可挂的目标（修订不在/已不是当前题面/源图已不是那一张）或工作已由别行完成。 */
    SUPERSEDED,

    /** 没动作：没配模型（出网门关着）、台账写不进、尝试耗尽、或已被别的执行者领走。 */
    SKIPPED,
}

/**
 * AUD-09（4D）的净化作业执行器：把"分类 → 重绘 → 挂接"从提交路径里那段 fire-and-forget
 * 协程升级成**有持久身份、可续跑、不重复付费**的作业。
 *
 * **它消灭的失败**：
 * - 进程在提交之后、干净图挂上之前被杀 → 作业行停在 `PENDING`，下次启动
 *   [resumePending] 接着做完（改前这条链就没了，题面永远停在原图）；
 * - 失败被 `runCatching` 吞成 false/null → 现在每一环的失败都落 `last_failure_code`
 *   （分类/生成/源图不可读/挂接），"为什么没有干净图"从不可考变成可查；
 * - 同一请求（同一张图、同一草稿身份，见作业指纹的口径）已经成功过再跑一遍 → [ALREADY_SUCCEEDED]，
 *   不再付费；
 * - 晚到结果挂到一个已被取代的修订上 → 付费**之前**先核目标，核不过标 `SUPERSEDED` 不挂；
 * - 续跑把已提交操作重放成新的付费轮 → 分类轮按同一 `requestId`（`save-decision:<修订>`）
 *   复用模型台账里的既有任务行（终态即重放，不出网）；生成轮的重复只可能发生在"领取"之后
 *   且被尝试上限收敛。
 *
 * **依赖缝**（[tasks] / [readSourceAsset] / [readSourceBytes] / [classify] / [generateClean] /
 * [attach]）刻意不直接拿 vault / 网关 / 数据库：它们要么是 Android 类，要么是要花真钱的外部
 * 调用；缝把"决策与记账"和"IO 与付费"分开，JVM 单测就能钉住"同指纹跳过"、"失败落码"、
 * "付费前核目标"这几条（与 `ConfiguredTutorFigureGenerator` 同一手法）。
 *
 * **出网门是 fail-closed 的**（AUD-09 ④ 的"出网门/凭证门照旧"）：[egressAllowed] 关着时一行
 * 也不动（连台账行都不建——没有尝试过就没有欠账）；凭证门在 [generateClean] 的实现里
 * （`ConfiguredCleanImageGenerator` 唯一实现），拒绝即 `REDRAW_FAILED`，不假装成功。
 */
internal class CleanRedrawTaskRunner(
    private val tasks: CleanRedrawTaskPort,
    /** 本构建现在能不能发模型轮（应用层开关的实值）。 */
    private val egressAllowed: () -> Boolean,
    /** 源图规范记录的读取口（台账行里的 id → 当前记录；null = 记录已不在）。 */
    private val readSourceAsset: suspend (String) -> CanonicalSourceAssetRecord?,
    /** 源图字节的核对读取（`vault.resolve` 的逐位校验）；null = 读不出来，不发不可信的字节。 */
    private val readSourceBytes: suspend (CanonicalSourceAssetRecord) -> ByteArray?,
    /** 分类轮；null = 这一轮没有给出判断（取消必须原样抛出，取消不是失败）。 */
    private val classify: suspend (ModelTaskRequest) -> ImagePipelineProblemKind?,
    /**
     * 净化生成（实现里已过凭证门）；null = 没产出（凭证/网络/provider/解码任一环节）。
     *
     * 4E.1：第三个参数是该作业**已领取的持久尝试**——生成实现把它作为统一生图协调器的
     * 派发父操作，图像 HTTP 没有这次持久预留就不会发出（账本/通道双重门）。
     */
    private val generateClean: suspend (
        CanonicalSourceAssetRecord,
        ByteArray,
        FigureDispatchParent.CleanRedrawTask,
    ) -> CleanImageResult?,
    /** 挂接写入（CLEAN_IMAGE 角色）；false = 没有可挂的目标。 */
    private val attach: suspend (String, ByteArray, String) -> Boolean,
    private val maxAttempts: Int = MAX_CLEAN_REDRAW_ATTEMPTS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init {
        require(maxAttempts >= 1) { "A clean redraw attempt limit must be positive" }
    }

    // 已知边界（如实记录，不在本轨修）：升级**前**已经存在、且停在非终态的分类任务行，其请求
    // 快照不含源图 sha；升级后按同一 requestId 重建的请求带着 sha，会撞上模型台账的不可变请求
    // 校验（ImmutablePayloadConflict）→ 这一轮落 CLASSIFY_FAILED 并由尝试上限收敛（不重复付费，
    // 但那一张图这次不会变干净）。要消除它得给模型台账写一条"旧行补键"的迁移——那是台账的读回
    // 口径，超出 AUD-09 的范围。

    /**
     * 提交成功后的触发（AUD-09 ①③⑤）。**只有这一条链会为一次提交建行**：调用方（采集仓库）
     * 只在提交真的落库（`created == true`）时调它，纯文字题在这里被分类轮判定后直接落
     * `SUCCEEDED`（TEXT_ONLY 不动作）。
     */
    suspend fun run(identity: CleanRedrawTaskIdentity): CleanRedrawTaskOutcome {
        if (!egressAllowed()) return CleanRedrawTaskOutcome.SKIPPED
        val source = readSourceAsset(identity.sourceAssetId)
            ?: return CleanRedrawTaskOutcome.SKIPPED
        // 分类请求的时刻 = 作业行的建立时刻：续跑时按行里的同一时刻重建请求，模型台账才认得出
        // "这是同一轮"（终态即重放、不出网）。用"现在"重建会得到另一个快照，撞上台账的
        // 不可变请求校验——一条被杀的作业就再也接不上。
        val requestedAtEpochMillis = now()
        val request = classifyRequest(
            revisionId = identity.revisionId,
            subjectId = identity.subjectId,
            source = source,
            requestedAtEpochMillis = requestedAtEpochMillis,
        )
        // 作业指纹取分类请求的**逻辑操作指纹**（不含时刻/同意位）：同一张图、同一草稿身份的
        // 分类就是同一个操作——这正是"同指纹已成功 → 不再付费"要去重的那个身份，也是模型台账
        // 记派遣预算用的同一个键。用带时刻的请求指纹会让"同一作业重触发一次"变成另一个身份：
        // 既有的成功行认不出来，第二次钱就花出去了。
        val requestFingerprint = ModelTaskLogicalOperationFingerprint.of(request)
        val task = tasks.createCleanRedrawTask(
            CreateCleanRedrawTaskCommand(
                taskId = cleanRedrawTaskId(
                    revisionId = identity.revisionId,
                    sourceAssetId = source.sourceAssetId,
                    sourceImageSha256 = source.contentSha256,
                    requestFingerprint = requestFingerprint,
                ),
                revisionId = identity.revisionId,
                sourceAssetId = source.sourceAssetId,
                sourceImageSha256 = source.contentSha256,
                requestFingerprint = requestFingerprint,
                subjectId = identity.subjectId,
                createdAtEpochMillis = requestedAtEpochMillis,
            ),
        )
        // 台账写不进（默认端口"做不到"）：什么都不做。宁可没有干净图，也不在没有记账的
        // 情况下花一次可能无法追认的钱——重放/续跑都要靠这一行。
        task ?: return CleanRedrawTaskOutcome.SKIPPED
        return runAttempt(row = task, source = source)
    }

    /**
     * 启动对账（AUD-09 ④）：把上一进程留下的、`PENDING`/`FAILED` 且未耗尽尝试次数的行接着
     * 做完，返回本次**实际动过**的行数。
     *
     * [staleBeforeEpochMillis] 是租约判据（应用传"本进程启动时刻"）：本进程刚发起的作业不会被
     * 对账抢走双跑，而上一进程被杀留下的行一定早于它。
     */
    suspend fun resumePending(staleBeforeEpochMillis: Long): Int {
        if (!egressAllowed()) return 0
        val rows = tasks.readResumableCleanRedrawTasks(
            maxAttempts = maxAttempts,
            staleBeforeEpochMillis = staleBeforeEpochMillis,
        )
        var processed = 0
        rows.forEach { row ->
            val outcome = runAttempt(row = row, source = null)
            if (outcome != CleanRedrawTaskOutcome.SKIPPED) processed += 1
        }
        return processed
    }

    private suspend fun runAttempt(
        row: CleanRedrawTaskRecord,
        source: CanonicalSourceAssetRecord?,
    ): CleanRedrawTaskOutcome {
        if (row.status == CleanRedrawTaskStatus.SUCCEEDED) {
            return CleanRedrawTaskOutcome.ALREADY_SUCCEEDED
        }
        if (row.status == CleanRedrawTaskStatus.SUPERSEDED) {
            return CleanRedrawTaskOutcome.SKIPPED
        }
        if (!egressAllowed()) return CleanRedrawTaskOutcome.SKIPPED
        // ② 同指纹已 SUCCEEDED → 跳过不重复付费。同指纹 = 同一个分类请求（同一张图 + 同一草稿
        // 身份 + 同一发送判据）：另一行已经把这份工作做完，这一行不再花第二次钱。
        val sibling = tasks.readCleanRedrawTaskByFingerprint(row.requestFingerprint)
        if (sibling != null && sibling.taskId != row.taskId &&
            sibling.status == CleanRedrawTaskStatus.SUCCEEDED
        ) {
            finish(row, CleanRedrawTaskStatus.SUPERSEDED, failureCode = null)
            return CleanRedrawTaskOutcome.ALREADY_SUCCEEDED
        }
        // 领取这一轮：次数 +1（上限之内、且没有别人先领走）。领不到 = 这次不要再跑。
        val claimed = tasks.claimCleanRedrawTaskAttempt(
            ClaimCleanRedrawTaskAttemptCommand(
                taskId = row.taskId,
                expectedAttemptCount = row.attemptCount,
                maxAttempts = maxAttempts,
                occurredAtEpochMillis = now(),
            ),
        ) ?: return CleanRedrawTaskOutcome.SKIPPED

        val currentSource = source ?: readSourceAsset(claimed.sourceAssetId)
        // 源图不在、或已不是本作业那一张（字节不同）：作业的对象已经变了，不重试、不挂。
        if (currentSource == null || currentSource.contentSha256 != claimed.sourceImageSha256) {
            finish(claimed, CleanRedrawTaskStatus.SUPERSEDED, failureCode = null)
            return CleanRedrawTaskOutcome.SUPERSEDED
        }
        val request = classifyRequest(
            revisionId = claimed.revisionId,
            subjectId = claimed.subjectId,
            source = currentSource,
            requestedAtEpochMillis = claimed.createdAtEpochMillis,
        )

        val kind = try {
            classify(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (kind == null) {
            return fail(claimed, CleanRedrawFailureCode.CLASSIFY_FAILED)
        }
        // TEXT_ONLY：触发范围口径说"不动作"——不生成、不挂接、不占后续尝试。
        if (kind != ImagePipelineProblemKind.WITH_FIGURE) {
            finish(claimed, CleanRedrawTaskStatus.SUCCEEDED, failureCode = null)
            return CleanRedrawTaskOutcome.SUCCEEDED
        }
        // ⑤ 付费之前先核目标：修订不在、或已不是其错题条目当前指向的那一版 → 不生成、标 SUPERSEDED。
        if (!isCurrentProblemRevision(claimed.revisionId)) {
            finish(claimed, CleanRedrawTaskStatus.SUPERSEDED, failureCode = null)
            return CleanRedrawTaskOutcome.SUPERSEDED
        }
        val sourceBytes = try {
            readSourceBytes(currentSource)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return fail(claimed, CleanRedrawFailureCode.SOURCE_UNREADABLE)

        val clean = try {
            generateClean(
                currentSource,
                sourceBytes,
                FigureDispatchParent.CleanRedrawTask(
                    taskId = claimed.taskId,
                    attemptCount = claimed.attemptCount,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return fail(claimed, CleanRedrawFailureCode.REDRAW_FAILED)

        val attached = try {
            attach(claimed.revisionId, clean.bytes, clean.mimeType)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return fail(claimed, CleanRedrawFailureCode.ATTACH_FAILED)
        }
        if (!attached) {
            // 生成之后、挂接之前修订被换走（或引用写入被拒）：不挂、不再试。
            finish(claimed, CleanRedrawTaskStatus.SUPERSEDED, failureCode = null)
            return CleanRedrawTaskOutcome.SUPERSEDED
        }
        finish(claimed, CleanRedrawTaskStatus.SUCCEEDED, failureCode = null)
        return CleanRedrawTaskOutcome.SUCCEEDED
    }

    /** 核"修订仍存在且仍是其错题条目当前指向的那一版"。读口"做不到"（没库）时按不过判。 */
    private suspend fun isCurrentProblemRevision(revisionId: String): Boolean = try {
        tasks.isCurrentProblemRevision(revisionId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun fail(
        row: CleanRedrawTaskRecord,
        failureCode: String,
    ): CleanRedrawTaskOutcome {
        finish(row, CleanRedrawTaskStatus.FAILED, failureCode = failureCode)
        return CleanRedrawTaskOutcome.FAILED
    }

    /**
     * 落终态。写不进（台账写失败）不改判这一轮已经发生的事实：行会留在领取后的 `PENDING`
     * 状态上，由下次启动按尝试上限续跑——不静默重付，但也不假装记下了。
     */
    private suspend fun finish(
        row: CleanRedrawTaskRecord,
        status: String,
        failureCode: String?,
    ) {
        try {
            tasks.finishCleanRedrawTask(
                FinishCleanRedrawTaskCommand(
                    taskId = row.taskId,
                    expectedAttemptCount = row.attemptCount,
                    status = status,
                    lastFailureCode = failureCode,
                    occurredAtEpochMillis = now(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 见方法 KDoc：写不进不是这一轮结果的证据，留下行给下一次对账。
        }
    }

    /**
     * 分类请求（行为 ①：指纹带源图**字节** sha）。`requestId` 按修订稳定、`occurredAtEpochMillis`
     * 取**作业行的建立时刻**——同一作业重跑时模型台账认得出这是同一个任务（终态即重放，不再出网）。
     */
    private fun classifyRequest(
        revisionId: String,
        subjectId: String,
        source: CanonicalSourceAssetRecord,
        requestedAtEpochMillis: Long,
    ): ModelTaskRequest = ModelTaskRequest(
        requestId = "save-decision:$revisionId",
        input = ImagePipelineClassifyInput(
            sourceAssetId = source.sourceAssetId,
            imageWidth = source.width,
            imageHeight = source.height,
            subjectIdOverride = subjectId,
            sourceImageSha256 = source.contentSha256,
        ),
        occurredAtEpochMillis = requestedAtEpochMillis,
        // 「配置模型 = 同意」的发送判据只有一处（core:model 的 agentConsentGranted KDoc）：
        // 这里只回答自己知道的那一半——应用层开关，且取实值。
        agentConsentGranted = egressAllowed(),
    )
}
