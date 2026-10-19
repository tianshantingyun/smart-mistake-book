package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
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
 * 四个依赖都是函数：
 * - [readExact]：按不可变版本读数（单题）；
 * - [readByEntryId]：按条目读当前正式版（批量：入口只带得动 entry id，键在 worker 侧解析）；
 * - [renderPdf]：真实现是 `MistakePdfExporter::prepare`，JVM 用例是假渲染器；
 * - [renderDocx]：真实现是 `MistakeDocxExporter::prepare`（6B-1）。缺省 null 保留旧调用点
 *   的构造形状；DOCX 请求在没有它时**如实失败**，绝不悄悄回退成 PDF（那会拿 PDF 文件冒领
 *   学生选的 Word 格式）。
 *
 * 结果三态而不是布尔：**不可导出**（题面自身不满足）与**渲染失败**（内容超预算/文件写入
 * 失败）对学生是两句不同的话，合并成一个"失败"会让"这道题本来就不能导出"看起来像系统故障。
 */
class MistakeExportJobRunner(
    private val readExact: suspend (MistakeRevisionKey) -> MistakeDetailState,
    private val readByEntryId: suspend (String) -> MistakeDetailState,
    private val renderPdf: (MistakePdfExportInput) -> PreparedMistakePdf,
    private val renderDocx: ((MistakePdfExportInput) -> PreparedMistakeDocx)? = null,
    /**
     * 6B-2：编辑后卷面的草稿读取口（draft id → 已定稿草稿）。缺省 null 保留旧调用点形状；
     * 编辑请求在没有它时**如实失败**，绝不悄悄回退成"按条目重新读一遍"（那会丢掉学生的编辑）。
     */
    private val readPaperDraft: (suspend (String) -> MistakePaperDraft?)? = null,
) {

    suspend fun run(request: MistakeExportJobRequest): MistakeExportJobOutcome = try {
        when (request) {
            is MistakeExportJobRequest.Single -> runSingle(request)
            is MistakeExportJobRequest.Batch -> runBatch(request)
            is MistakeExportJobRequest.Edited -> runEdited(request)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: MistakePdfExportException) {
        MistakeExportJobOutcome.Failed(
            when (request) {
                is MistakeExportJobRequest.Single ->
                    exportFailureMessage(failure.failure, request.format)
                is MistakeExportJobRequest.Batch ->
                    batchExportFailureMessage(failure.failure, request.format)
                is MistakeExportJobRequest.Edited ->
                    editedPaperFailureMessage(failure.failure, request.format)
            },
        )
    } catch (_: Exception) {
        MistakeExportJobOutcome.Failed(
            when (request.format) {
                MistakeExportFormat.PDF ->
                    "这次没有整理出完整的 A4 文件，请稍后重新导出。"
                MistakeExportFormat.DOCX ->
                    "这次没有整理出完整的 Word 文档，请稍后重新导出。"
            },
        )
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
                    request = request,
                    input = eligibility.input,
                    displayName = exportDisplayName(
                        title = identity.title,
                        revisionNumber = identity.revisionNumber,
                        format = request.format,
                    ),
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
                request = request,
                input = eligibility.input,
                displayName = batchExportDisplayName(eligibility.includedCount, request.format),
            )
        }
    }

    /**
     * 6B-2：直接渲染一份已定稿的卷面草稿。
     *
     * **不重读条目、不重跑准入、不派发任何模型任务**：草稿里就是学生在编辑态看到并确认的那份
     * 输入（块序列 + 版式 + 指纹）。草稿缺失/损坏/版式对不上时如实失败——宁可让这次导出
     * 重来，也不拿一份不是学生确认过的卷面交付。
     */
    private suspend fun runEdited(
        request: MistakeExportJobRequest.Edited,
    ): MistakeExportJobOutcome {
        val read = readPaperDraft
            ?: return MistakeExportJobOutcome.Failed(
                "这次导出没有准备好卷面草稿，请重新编辑后再导出。",
            )
        val draft = read(request.draftId)
            ?: return MistakeExportJobOutcome.Failed(
                "这份编辑后的卷面没有保存下来，请重新编辑后再导出。",
            )
        if (draft.input.layout != request.layout) {
            return MistakeExportJobOutcome.Failed(
                "这份卷面的版式已经变了，请重新编辑后再导出。",
            )
        }
        val displayName = if (draft.edited) {
            editedPaperDisplayName(draft.questionCount, request.format)
        } else {
            batchExportDisplayName(draft.questionCount, request.format)
        }
        return render(request = request, input = draft.input, displayName = displayName)
    }

    private fun render(
        request: MistakeExportJobRequest,
        input: MistakePdfExportInput,
        displayName: String,
    ): MistakeExportJobOutcome = when (request.format) {
        MistakeExportFormat.PDF -> {
            val prepared = renderPdf(input)
            check(prepared.verifyIntegrity()) { "Prepared PDF failed integrity verification" }
            MistakeExportJobOutcome.Rendered(displayName = displayName, prepared = prepared)
        }
        MistakeExportFormat.DOCX -> {
            val renderDocx = renderDocx
                ?: return MistakeExportJobOutcome.Failed(
                    "这次导出没有准备好 Word 生成器，请稍后重新导出。",
                )
            val prepared = renderDocx(input)
            check(prepared.verifyIntegrity()) { "Prepared Word artifact failed integrity verification" }
            MistakeExportJobOutcome.RenderedDocx(displayName = displayName, prepared = prepared)
        }
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

    /** 交付格式（6B-1）：缺省 PDF = 旧的入队任务与既有入口的行为不变。 */
    val format: MistakeExportFormat
        get() = MistakeExportFormat.PDF

    /** 单题：入口给的是不可变版本三元组。 */
    data class Single(
        override val exportId: String,
        val key: MistakeRevisionKey,
        override val layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
        override val format: MistakeExportFormat = MistakeExportFormat.PDF,
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
        override val format: MistakeExportFormat = MistakeExportFormat.PDF,
    ) : MistakeExportJobRequest {
        init {
            require(layout.validate().isEmpty()) { "Invalid export layout: ${layout.validate()}" }
        }
    }

    /**
     * 6B-2：**编辑后卷面**的入队请求。
     *
     * 只带草稿 id（短字符串）——卷面块序列在应用私有目录里，不塞进 WorkManager 的 10KB
     * Data；worker 渲染前用 [MistakeExportJobRunner] 的草稿读取口取回。`layout` 与草稿里的
     * 版式必须一致（学生确认的就是这份），不一致时如实失败而不是换一份版式交付。
     */
    data class Edited(
        override val exportId: String,
        val draftId: String,
        override val layout: MistakePdfLayout = MistakePdfLayout.DEFAULT,
        override val format: MistakeExportFormat = MistakeExportFormat.PDF,
    ) : MistakeExportJobRequest {
        init {
            require(layout.validate().isEmpty()) { "Invalid export layout: ${layout.validate()}" }
            require(draftId.isNotBlank()) { "An edited export needs a paper draft id" }
        }
    }
}

/** 三态结果：可交付（已通过完整性核对）/ 不可导出 / 渲染失败。 */
sealed interface MistakeExportJobOutcome {
    data class Rendered(
        val displayName: String,
        val prepared: PreparedMistakePdf,
    ) : MistakeExportJobOutcome

    /** 6B-1：Word 交付的渲染结果（与 [Rendered] 同构，产物类型不同）。 */
    data class RenderedDocx(
        val displayName: String,
        val prepared: PreparedMistakeDocx,
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
