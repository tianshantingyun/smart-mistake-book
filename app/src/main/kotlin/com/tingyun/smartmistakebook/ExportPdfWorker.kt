package com.tingyun.smartmistakebook

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tingyun.smartmistakebook.core.domain.MistakeExportRepository
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.export.MistakeExportJobOutcome
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import kotlinx.coroutines.CancellationException

/**
 * 后台导出的 WorkManager 接线（阶段 4A 批 4 · L7）。
 *
 * **形态（用户裁定 A / 计划 §2.1）**：expedited 普通 `CoroutineWorker`——渲染是秒级本地任务，
 * 不进长任务前台服务、不占 Android 16 起收紧的 job 配额；配额不足时按
 * [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] 降级为普通任务，宁可慢不可丢。
 *
 * **它消灭的失败**："导出随页面丢"——请求一旦入队就是 WorkManager 的持久行，页面销毁、
 * 进程重建都不取消它（`MistakeExportBackgroundInstrumentedTest` 用"销毁页面后仍完成"钉住）。
 *
 * 每次导出一行记录 + 一个以 `export_id` 命名的唯一任务：同一 id 重复入队是 `KEEP`（重放），
 * 不会开第二次渲染。
 */
internal object ExportPdfDriver {
    const val TAG = "mistake-export"

    val existingPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun request(request: MistakeExportJobRequest): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ExportPdfWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                Data.Builder()
                    .putAll(MistakeExportJobCodec.encode(request))
                    .build(),
            )
            .addTag(TAG)
            .build()

    fun enqueue(context: Context, request: MistakeExportJobRequest) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            MistakeExportJobCodec.uniqueName(request.exportId),
            existingPolicy,
            request(request),
        )
    }
}

/**
 * worker 的就绪门：只挡"库还没打开"（[StartupState.Initializing]）与"库打不开"
 * （[StartupState.FatalFailure]）。
 *
 * **它消灭的失败**：改前以 `!is StartupState.Ready` 挡门，而 restore 回滚与知识包安装失败
 * 都会把状态置为 [StartupState.RecoverableFailure]——其文案明说"错题和复习可以继续使用"
 * （数据库是好的）。导出被一并挡掉后，worker 重试耗尽即静默失败：不写 FAILED、不发通知，
 * 记录永远停在 RUNNING，hub 永远显示"正在整理"。可用的库上导出必须照常可跑。
 */
internal fun StartupState.allowsMistakeExportWorker(): Boolean = when (this) {
    StartupState.Initializing -> false
    StartupState.Ready -> true
    is StartupState.RecoverableFailure -> true
    is StartupState.FatalFailure -> false
}

/**
 * 后台导出任务。
 *
 * 这里只有编排/IO：解出请求 → 就绪门 → 读记录（拿不到就不冒领结果）→ 跑
 * [com.tingyun.smartmistakebook.core.export.MistakeExportJobRunner]（渲染核心，JVM 可测）
 * → 落终态 → 按授权情况发通知（未授权时结果仍在「导出成果」里，见
 * [MistakeExportNotifications]）。
 *
 * **放弃必留痕**：任何走到"最终放弃"的路径（就绪门重试耗尽、预期外异常重试耗尽）都会把记录
 * 写成 FAILED 并视授权情况发失败通知——记录表上不允许出现没有对应任务的"正在整理"。
 */
class ExportPdfWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SmartMistakeBookApplication ?: return Result.failure()
        val request = MistakeExportJobCodec.decode(inputData.keyValueMap) ?: return Result.failure()
        val repository = app.mistakeExportRepository

        if (!app.startupState.value.allowsMistakeExportWorker()) {
            return giveUpOrRetry(
                app = app,
                repository = repository,
                exportId = request.exportId,
                failureMessage = "学习数据还没准备好，这次导出没有开始。请稍后重新导出。",
            )
        }

        val existing = repository.readRecord(request.exportId) ?: return Result.failure()
        // 已有终态 = 这次导出早已落定（进程重建后的重跑）：幂等返回，不写第二份结果、不发第二次通知。
        if (existing.status != MistakeExportStatus.RUNNING) return Result.success()

        return try {
            when (val outcome = app.mistakeExportJobRunner.run(request)) {
                is MistakeExportJobOutcome.Rendered -> {
                    repository.recordSucceeded(
                        exportId = request.exportId,
                        displayName = outcome.displayName,
                        inputSha256 = outcome.prepared.inputSha256,
                        pdfSha256 = outcome.prepared.sha256,
                        pageCount = outcome.prepared.pageCount,
                        atEpochMillis = System.currentTimeMillis(),
                    )
                    app.mistakeExportNotifications.postSucceeded(
                        exportId = request.exportId,
                        displayName = outcome.displayName,
                    )
                }
                is MistakeExportJobOutcome.Blocked -> {
                    repository.recordFailed(
                        exportId = request.exportId,
                        failureMessage = outcome.message,
                        atEpochMillis = System.currentTimeMillis(),
                    )
                    app.mistakeExportNotifications.postFailed(
                        exportId = request.exportId,
                        failureMessage = outcome.message,
                    )
                }
                is MistakeExportJobOutcome.Failed -> {
                    repository.recordFailed(
                        exportId = request.exportId,
                        failureMessage = outcome.message,
                        atEpochMillis = System.currentTimeMillis(),
                    )
                    app.mistakeExportNotifications.postFailed(
                        exportId = request.exportId,
                        failureMessage = outcome.message,
                    )
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            // WorkManager 停了这次执行：记录保持 RUNNING，策略允许时下一次执行继续。
            throw cancelled
        } catch (_: Exception) {
            giveUpOrRetry(
                app = app,
                repository = repository,
                exportId = request.exportId,
                failureMessage = "这次导出意外中断，请重新导出。",
            )
        }
    }

    /**
     * API < 31 的 expedited 任务由 WorkManager 放进前台服务运行，必须给出前台通知
     * （官方《Define work》："any ListenableWorker must implement getForegroundInfo"）；
     * API 31+ 走 expedited job，本方法不会被调用。
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val app = applicationContext as? SmartMistakeBookApplication
            ?: error("The export worker requires SmartMistakeBookApplication")
        return app.mistakeExportNotifications.foregroundInfo()
    }

    /** 有限重试；最终放弃时记录 FAILED + 失败通知（不许静默把记录留在 RUNNING）。 */
    private suspend fun giveUpOrRetry(
        app: SmartMistakeBookApplication,
        repository: MistakeExportRepository,
        exportId: String,
        failureMessage: String,
    ): Result {
        if (runAttemptCount < MAX_ATTEMPTS - 1) return Result.retry()
        runCatching {
            repository.recordFailed(
                exportId = exportId,
                failureMessage = failureMessage,
                atEpochMillis = System.currentTimeMillis(),
            )
        }
        app.mistakeExportNotifications.postFailed(
            exportId = exportId,
            failureMessage = failureMessage,
        )
        return Result.failure()
    }

    internal companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/**
 * 请求与 WorkManager 输入的编解码。
 *
 * 纯映射（没有 android 类型）：批量只带 entry id（单条 id 短、100 条也远小于 WorkManager
 * 10KB 输入上限；三键一组的版本三元组在 100 题时会顶到上限），键在 worker 侧按当前正式版解析。
 */
internal object MistakeExportJobCodec {
    const val EXPORT_ID_KEY = "exportId"
    const val KIND_KEY = "kind"
    const val ENTRY_IDS_KEY = "entryIds"
    const val ENTRY_ID_KEY = "entryId"
    const val PROBLEM_ID_KEY = "problemId"
    const val REVISION_ID_KEY = "revisionId"

    private const val KIND_SINGLE = "single"
    private const val KIND_BATCH = "batch"
    private const val ENTRY_ID_SEPARATOR = "\n"

    fun uniqueName(exportId: String): String {
        require(exportId.isNotBlank()) { "An export id must not be blank" }
        return "mistake-export-$exportId"
    }

    fun encode(request: MistakeExportJobRequest): Map<String, Any?> = when (request) {
        is MistakeExportJobRequest.Single -> mapOf(
            EXPORT_ID_KEY to request.exportId,
            KIND_KEY to KIND_SINGLE,
            ENTRY_ID_KEY to request.key.entryId,
            PROBLEM_ID_KEY to request.key.problemId,
            REVISION_ID_KEY to request.key.problemRevisionId,
        )
        is MistakeExportJobRequest.Batch -> {
            require(request.entryIds.none { it.contains(ENTRY_ID_SEPARATOR) }) {
                "An export entry id must not contain a newline"
            }
            mapOf(
                EXPORT_ID_KEY to request.exportId,
                KIND_KEY to KIND_BATCH,
                ENTRY_IDS_KEY to request.entryIds.joinToString(ENTRY_ID_SEPARATOR),
            )
        }
    }

    fun decode(values: Map<String, Any?>): MistakeExportJobRequest? {
        val exportId = (values[EXPORT_ID_KEY] as? String)?.takeIf { it.isNotBlank() }
            ?: return null
        return when (val kind = values[KIND_KEY] as? String) {
            KIND_SINGLE -> {
                val entryId = (values[ENTRY_ID_KEY] as? String)?.takeIf { it.isNotBlank() }
                val problemId = (values[PROBLEM_ID_KEY] as? String)?.takeIf { it.isNotBlank() }
                val revisionId = (values[REVISION_ID_KEY] as? String)?.takeIf { it.isNotBlank() }
                if (entryId == null || problemId == null || revisionId == null) null
                else MistakeExportJobRequest.Single(
                    exportId = exportId,
                    key = MistakeRevisionKey(
                        entryId = entryId,
                        problemId = problemId,
                        problemRevisionId = revisionId,
                    ),
                )
            }
            KIND_BATCH -> {
                val entryIds = (values[ENTRY_IDS_KEY] as? String)
                    ?.split(ENTRY_ID_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?: return null
                MistakeExportJobRequest.Batch(exportId = exportId, entryIds = entryIds)
            }
            else -> null
        }
    }
}
