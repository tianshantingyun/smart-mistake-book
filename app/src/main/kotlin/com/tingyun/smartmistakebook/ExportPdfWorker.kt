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
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
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
        val request = MistakeExportJobCodec.decode(inputData.keyValueMap)
        if (request == null) {
            // 解码失败也必须留痕（"放弃必留痕"）：记录行在入队时已落 RUNNING，直接
            // Result.failure() 会让 hub 永远显示"正在整理"（幽灵行）。能读到 exportId 就走与
            // 其它放弃路径同一条重试/落 FAILED 的路；连 id 都没有时没有可标记的行
            // （记录行按 exportId 落库），只能 failure。
            val exportId = (inputData.keyValueMap[MistakeExportJobCodec.EXPORT_ID_KEY] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: return Result.failure()
            return giveUpOrRetry(
                app = app,
                repository = app.mistakeExportRepository,
                exportId = exportId,
                failureMessage = "这次导出的参数无法识别，没有生成文件。请重新发起导出。",
            )
        }
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
                        // B3-4：请求了但被 fail-closed 跳过的版式功能如实说一句——计数不许蒸发
                        // （与分页器的 PdfPlan.skipped、导出 sheet 的"会跳过"提示同一判据）。
                        skippedNotice = mistakeExportSkippedNotice(request.layout),
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
 *
 * 4B 批 4：版式（[MistakePdfLayout]）逐字段进 Data（String/Int/Float/Boolean 都是 Data 支持
 * 的原语，不需要额外序列化依赖）。缺版式键 = 旧的入队任务 → 按 [MistakePdfLayout.DEFAULT]；
 * 版式键**在但读不成合法布局** → 整条请求拒（fail-closed：宁可丢弃一条坏任务，也不拿
 * 默认版式冒充学生改过的版式渲染）。
 */
internal object MistakeExportJobCodec {
    const val EXPORT_ID_KEY = "exportId"
    const val KIND_KEY = "kind"
    const val ENTRY_IDS_KEY = "entryIds"
    const val ENTRY_ID_KEY = "entryId"
    const val PROBLEM_ID_KEY = "problemId"
    const val REVISION_ID_KEY = "revisionId"

    const val LAYOUT_TEMPLATE_ID_KEY = "layoutTemplateId"
    const val LAYOUT_MARGIN_PT_KEY = "layoutMarginPt"
    const val LAYOUT_FONT_SCALE_KEY = "layoutFontScale"
    const val LAYOUT_COLUMN_COUNT_KEY = "layoutColumnCount"
    const val LAYOUT_BLOCK_ORDER_KEY = "layoutBlockOrder"
    const val LAYOUT_IMAGE_SCALE_KEY = "layoutImageScale"
    const val LAYOUT_INCLUDE_ANSWER_KEY = "layoutIncludeAnswer"
    const val LAYOUT_INCLUDE_SOLUTION_KEY = "layoutIncludeSolution"
    const val LAYOUT_INCLUDE_NOTE_KEY = "layoutIncludeNote"

    private val LAYOUT_KEYS = setOf(
        LAYOUT_TEMPLATE_ID_KEY,
        LAYOUT_MARGIN_PT_KEY,
        LAYOUT_FONT_SCALE_KEY,
        LAYOUT_COLUMN_COUNT_KEY,
        LAYOUT_BLOCK_ORDER_KEY,
        LAYOUT_IMAGE_SCALE_KEY,
        LAYOUT_INCLUDE_ANSWER_KEY,
        LAYOUT_INCLUDE_SOLUTION_KEY,
        LAYOUT_INCLUDE_NOTE_KEY,
    )

    private const val KIND_SINGLE = "single"
    private const val KIND_BATCH = "batch"
    private const val ENTRY_ID_SEPARATOR = "\n"
    private const val BLOCK_ORDER_SEPARATOR = ","

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
        ) + layoutEntries(request.layout)
        is MistakeExportJobRequest.Batch -> {
            require(request.entryIds.none { it.contains(ENTRY_ID_SEPARATOR) }) {
                "An export entry id must not contain a newline"
            }
            mapOf(
                EXPORT_ID_KEY to request.exportId,
                KIND_KEY to KIND_BATCH,
                ENTRY_IDS_KEY to request.entryIds.joinToString(ENTRY_ID_SEPARATOR),
            ) + layoutEntries(request.layout)
        }
    }

    fun decode(values: Map<String, Any?>): MistakeExportJobRequest? {
        val exportId = (values[EXPORT_ID_KEY] as? String)?.takeIf { it.isNotBlank() }
            ?: return null
        val layout = layoutFromValues(values) ?: return null
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
                    layout = layout,
                )
            }
            KIND_BATCH -> {
                val entryIds = (values[ENTRY_IDS_KEY] as? String)
                    ?.split(ENTRY_ID_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?: return null
                MistakeExportJobRequest.Batch(exportId = exportId, entryIds = entryIds, layout = layout)
            }
            else -> null
        }
    }

    private fun layoutEntries(layout: MistakePdfLayout): Map<String, Any?> = mapOf(
        LAYOUT_TEMPLATE_ID_KEY to layout.templateId,
        LAYOUT_MARGIN_PT_KEY to layout.marginPt,
        LAYOUT_FONT_SCALE_KEY to layout.fontScale,
        LAYOUT_COLUMN_COUNT_KEY to layout.columnCount,
        LAYOUT_BLOCK_ORDER_KEY to layout.blockOrder.joinToString(BLOCK_ORDER_SEPARATOR),
        LAYOUT_IMAGE_SCALE_KEY to layout.imageScale,
        LAYOUT_INCLUDE_ANSWER_KEY to layout.includeAnswer,
        LAYOUT_INCLUDE_SOLUTION_KEY to layout.includeSolution,
        LAYOUT_INCLUDE_NOTE_KEY to layout.includeNote,
    )

    /** 缺版式键 = 旧任务（默认版式）；键在但读不成合法布局 = null（整条请求拒）。 */
    private fun layoutFromValues(values: Map<String, Any?>): MistakePdfLayout? {
        if (LAYOUT_KEYS.none { key -> key in values }) return MistakePdfLayout.DEFAULT
        val templateId = (values[LAYOUT_TEMPLATE_ID_KEY] as? String)
            ?.takeIf { it.isNotBlank() } ?: return null
        val marginPt = (values[LAYOUT_MARGIN_PT_KEY] as? Number)?.toInt() ?: return null
        val fontScale = (values[LAYOUT_FONT_SCALE_KEY] as? Number)?.toInt() ?: return null
        val columnCount = (values[LAYOUT_COLUMN_COUNT_KEY] as? Number)?.toInt() ?: return null
        val blockOrder = (values[LAYOUT_BLOCK_ORDER_KEY] as? String)
            ?.split(BLOCK_ORDER_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: return null
        val imageScale = (values[LAYOUT_IMAGE_SCALE_KEY] as? Number)?.toFloat() ?: return null
        val includeAnswer = values[LAYOUT_INCLUDE_ANSWER_KEY] as? Boolean ?: return null
        val includeSolution = values[LAYOUT_INCLUDE_SOLUTION_KEY] as? Boolean ?: return null
        val includeNote = values[LAYOUT_INCLUDE_NOTE_KEY] as? Boolean ?: return null
        val layout = MistakePdfLayout(
            templateId = templateId,
            marginPt = marginPt,
            fontScale = fontScale,
            columnCount = columnCount,
            blockOrder = blockOrder,
            imageScale = imageScale,
            includeAnswer = includeAnswer,
            includeSolution = includeSolution,
            includeNote = includeNote,
        )
        return layout.takeIf { it.validate().isEmpty() }
    }
}

/**
 * 请求了但被 fail-closed 跳过的版式功能 → 完成通知里的如实一句（B3-4）；没有跳过时 null。
 *
 * 与 `MistakePdfPagePlanner` 的 `PdfPlan.skipped`、导出 sheet 的"会跳过"提示读同一份判据
 * （[MistakePdfLayout.unsupportedRequestedFeatures]）——三处不会一处说了另一处没说。
 */
internal fun mistakeExportSkippedNotice(layout: MistakePdfLayout): String? {
    val features = layout.unsupportedRequestedFeatures()
    if (features.isEmpty()) return null
    return features.joinToString(separator = "、") { feature -> feature.studentLabel } +
        "暂不可用，已跳过。"
}
