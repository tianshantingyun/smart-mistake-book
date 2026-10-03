package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import kotlinx.coroutines.CancellationException

/**
 * 一次后台导出的**渲染编排**（阶段 4A 批 4 · L7）。
 *
 * 它消灭的失败：改前"读快照 → 判可导出 → 渲染"这三步只存在于页面里（`produceState` +
 * `rememberCoroutineScope`），离开页面即取消，失败原因也只在屏幕上说一次。这里把三步收成
 * 一个可注入、可在 JVM 上完整演练的单元——worker 只负责"入队身份 / 记录 / 通知"这些 IO 与
 * 编排，不参与排版或判定。
 *
 * 三个依赖都是函数：
 * - [readExact]：按不可变版本读数（单题）；
 * - [readByEntryId]：按条目读当前正式版（批量：入口只带得动 entry id，键在 worker 侧解析）；
 * - [renderPdf]：真实现是 `MistakePdfExporter::prepare`，JVM 用例是假渲染器。
 *
 * 结果三态而不是布尔：**不可导出**（题面自身不满足）与**渲染失败**（内容超预算/文件写入
 * 失败）对学生是两句不同的话，合并成一个"失败"会让"这道题本来就不能导出"看起来像系统故障。
 */
class MistakeExportJobRunner(
    private val readExact: suspend (MistakeRevisionKey) -> MistakeDetailState,
    private val readByEntryId: suspend (String) -> MistakeDetailState,
    private val renderPdf: (MistakePdfExportInput) -> PreparedMistakePdf,
) {

    suspend fun run(request: MistakeExportJobRequest): MistakeExportJobOutcome = try {
        when (request) {
            is MistakeExportJobRequest.Single -> runSingle(request)
            is MistakeExportJobRequest.Batch -> runBatch(request)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: MistakePdfExportException) {
        MistakeExportJobOutcome.Failed(
            when (request) {
                is MistakeExportJobRequest.Single -> exportFailureMessage(failure.failure)
                is MistakeExportJobRequest.Batch -> batchExportFailureMessage(failure.failure)
            },
        )
    } catch (_: Exception) {
        MistakeExportJobOutcome.Failed("这次没有整理出完整的 A4 文件，请稍后重新导出。")
    }

    private suspend fun runSingle(
        request: MistakeExportJobRequest.Single,
    ): MistakeExportJobOutcome {
        val state = readExact(request.key)
        if (!state.matchesExactKey(request.key)) {
            return MistakeExportJobOutcome.Blocked(
                "没能确认这一版题目与当前错题一致，因此没有生成文件。",
            )
        }
        return when (val eligibility = MistakePdfEligibility.check(state, request.layout)) {
            is MistakePdfEligibilityResult.Ineligible ->
                MistakeExportJobOutcome.Blocked(exportBlockedMessage(eligibility.reasons))
            is MistakePdfEligibilityResult.Eligible -> {
                val identity = checkNotNull((state as? MistakeDetailState.Ready)?.detail?.identity) {
                    "Eligible single export must come from a ready detail"
                }
                render(
                    input = eligibility.input,
                    displayName = exportDisplayName(identity.title, identity.revisionNumber),
                )
            }
        }
    }

    private suspend fun runBatch(
        request: MistakeExportJobRequest.Batch,
    ): MistakeExportJobOutcome {
        val entryIds = request.entryIds.distinct()
        if (entryIds.isEmpty()) {
            return MistakeExportJobOutcome.Blocked(
                batchBlockedMessage(MistakePdfBatchIneligibility.EMPTY),
            )
        }
        if (entryIds.size > MistakePdfBatchEligibility.MAX_QUESTIONS) {
            return MistakeExportJobOutcome.Blocked(
                batchBlockedMessage(MistakePdfBatchIneligibility.TOO_MANY_QUESTIONS),
            )
        }
        val states = entryIds.map { entryId -> readByEntryId(entryId) }
        return when (val eligibility = MistakePdfBatchEligibility.check(states, request.layout)) {
            is MistakePdfBatchEligibilityResult.Ineligible ->
                MistakeExportJobOutcome.Blocked(batchBlockedMessage(eligibility.reason))
            is MistakePdfBatchEligibilityResult.Eligible -> render(
                input = eligibility.input,
                displayName = batchExportDisplayName(eligibility.includedCount),
            )
        }
    }

    private fun render(
        input: MistakePdfExportInput,
        displayName: String,
    ): MistakeExportJobOutcome {
        val prepared = renderPdf(input)
        check(prepared.verifyIntegrity()) { "Prepared PDF failed integrity verification" }
        return MistakeExportJobOutcome.Rendered(displayName = displayName, prepared = prepared)
    }
}

/**
 * 后台渲染任务的输入：一次导出要渲染哪一版题目 / 哪一批条目，以及**用哪份版式**。
 *
 * 版式（4B 批 4）：`START_EXPORT` 的导出 sheet 让学生改完版式再开始导出——参数必须随任务
 * 走到渲染，否则 sheet 上看到的版式与后台产出不是同一份（缓存键也会错）。默认
 * [MistakePdfLayout.DEFAULT] = 参数化前的固定版式（旧的入队任务与单题入口不受影响）。
 */
sealed interface MistakeExportJobRequest {
    val exportId: String
    val layout: MistakePdfLayout

    /** 单题：入口给的是不可变版本三元组。 */
    data class Single(
        override val exportId: String,
        val key: MistakeRevisionKey,
        override val layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ) : MistakeExportJobRequest {
        init {
            // 请求构造处就拒非法版式（防御其他调用点）：坏版式进 WorkManager 只会变成
            // "解码失败/渲染失败"的晚失败，而这里能让调用方当场看见。
            require(layout.validate().isEmpty()) { "Invalid export layout: ${layout.validate()}" }
        }
    }

    /** 批量：入口只带得动 entry id（键在 worker 侧解析，避免 WorkManager 输入超限）。 */
    data class Batch(
        override val exportId: String,
        val entryIds: List<String>,
        override val layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
    ) : MistakeExportJobRequest {
        init {
            require(layout.validate().isEmpty()) { "Invalid export layout: ${layout.validate()}" }
        }
    }
}

/** 三态结果：可交付（已通过完整性核对）/ 不可导出 / 渲染失败。 */
sealed interface MistakeExportJobOutcome {
    data class Rendered(
        val displayName: String,
        val prepared: PreparedMistakePdf,
    ) : MistakeExportJobOutcome

    data class Blocked(val message: String) : MistakeExportJobOutcome

    data class Failed(val message: String) : MistakeExportJobOutcome
}

/**
 * 请求与不可变版本是否指向同一版（与页面上"你刚才看到的那一版"同一判定）。
 *
 * `Loading` 不算匹配：worker 的读口不产生 Loading，出现即说明读数路径不对，宁可停下。
 */
internal fun MistakeDetailState.matchesExactKey(key: MistakeRevisionKey): Boolean = when (this) {
    MistakeDetailState.Loading -> false
    MistakeDetailState.NotFound -> true
    is MistakeDetailState.Ready -> detail.identity.matches(key)
    is MistakeDetailState.Legacy -> detail.identity.matches(key)
    is MistakeDetailState.CorruptSnapshot -> identity.matches(key)
}

private fun com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity.matches(
    key: MistakeRevisionKey,
): Boolean = errorBookEntryId == key.entryId &&
    problemId == key.problemId &&
    problemRevisionId == key.problemRevisionId
