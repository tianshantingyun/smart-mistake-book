package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.MistakeRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeCoverageOverview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The four database observation loops that keep the study snapshot fresh:
 * mistakes, pending drafts, ledger head and knowledge coverage. Every emission
 * is serialized under the repository's mutex and handed to the injected
 * handler, so the repository keeps ownership of its state while this class owns
 * the subscription lifecycle (one place to cancel on close).
 *
 * S20（3C 后半批 3）：三条**重建型**观察路径（错题、待批改草稿数、账本头）先
 * [dedupeAndCoalesce] 再进互斥——每次发射都会触发整份快照重建（drain 投影 + 重排计划 +
 * 全目录），而 Room 的失效是表级的，一次写入常常引发成串重复发射。知识覆盖走的是
 * 增量字段更新（不是整份重建），保持原样。
 */
internal class StudyExperienceObservationJobs(
    private val database: StudyDatabasePort,
    private val learnerId: String,
    private val mutex: Mutex,
    scope: CoroutineScope,
    private val coverageFlow: Flow<StudyKnowledgeCoverageOverview>,
    private val onMistakes: suspend (List<MistakeRecord>) -> Unit,
    private val onPendingDraftCount: suspend (Int) -> Unit,
    private val onLedgerChanged: suspend () -> Unit,
    private val onCoverage: suspend (StudyKnowledgeCoverageOverview) -> Unit,
    private val onFailure: suspend (Throwable) -> Unit,
) {
    private val jobs: List<Job> = listOf(
        scope.launch { collect(database.observeMistakes().dedupeAndCoalesce(), onMistakes) },
        scope.launch {
            collect(
                database.observePendingProblemDraftCount().dedupeAndCoalesce(),
                onPendingDraftCount,
            )
        },
        scope.launch {
            collect(
                database.observeLearningLedgerHead(learnerId).dedupeAndCoalesce(),
            ) {
                onLedgerChanged()
            }
        },
        scope.launch { collect(coverageFlow, onCoverage) },
    )

    fun cancel() {
        jobs.forEach(Job::cancel)
    }

    private suspend fun <T> collect(flow: Flow<T>, handler: suspend (T) -> Unit) {
        try {
            flow.collect { value -> mutex.withLock { handler(value) } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutex.withLock { onFailure(failure) }
        }
    }
}

/**
 * S20：观察侧合并——先按值去重（[distinctUntilChanged]），再合并突发（[conflate]）。
 *
 * **它消灭的失败**：Room 的失效是**表级**的（一次写入让所有引用该表的观察流重新发射），
 * 而这里的每次发射都会在互斥下重算整份快照（drain 投影 + 重排计划 + 全目录重建）。
 * 高频写入（作答、结算、投影提交）会让发射成串，快照被反复重算——学生看到的是界面
 * 卡顿与重复重建，而不是"最终一致"。去重砍掉内容相同的重复发射，合并把"处理器忙碌期间"
 * 到达的一串发射折叠成一次重建。
 *
 * **为什么不用 `debounce`**：它是 `@FlowPreview`，且语义是"等一个静默窗口"——写后可见性
 * 会被拖到窗口之后。合并只丢**中间**值、保证**最后一个**值必达，因此写路径之外的
 * "最终状态可见"语义不变；写路径自身的显式 publish 完全不受影响（不经过本类）。
 */
private fun <T> Flow<T>.dedupeAndCoalesce(): Flow<T> =
    distinctUntilChanged().conflate()
