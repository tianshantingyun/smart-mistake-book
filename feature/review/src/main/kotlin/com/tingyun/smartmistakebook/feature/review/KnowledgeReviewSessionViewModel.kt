package com.tingyun.smartmistakebook.feature.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.KnowledgeQuizFeedbackResult
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueEntry
import com.tingyun.smartmistakebook.core.domain.KnowledgeReviewSessionPlan
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 知识点复习：当前知识点的考察项加载状态。 */
internal enum class KnowledgeQuizLoadStatus {
    IDLE,
    LOADING,
    LOADED,
    FAILED,

    /**
     * 知识内容还在后台就位（D-Q3）。与 [FAILED] 分开：这里不需要"再试一次"的技术措辞，
     * 实话是"还在准备，等一下就好"，且进程重建后回到 [IDLE] 自动重取——这条**不**算
     * "恢复时新出题"：那时一道题都还没出过，没有题面可读回。
     */
    KNOWLEDGE_PREPARING,

    /**
     * 这个知识点没有可用的讲解材料（合法空集，区别于失败）：重试不会有不同结果，
     * 出路是跳过它。
     */
    NO_MATERIAL,
}

/** 知识点复习：当前知识点作答的回写状态。 */
internal enum class KnowledgeQuizSubmitStatus {
    IDLE,
    RECORDING,
    RECORDED,
    FAILED,
}

/**
 * 取题走哪条路（纯函数，AUD-02）：恢复态（题面已落库、内存里无题）只**读回**，
 * 只有从未出过题的 [KnowledgeQuizLoadStatus.IDLE] 才现场出题。
 * "恢复不自动新出题、不再出网"就是这条规则，单独成函数是为了它能被单测直接钉住。
 */
internal enum class KnowledgeQuizLoadPath {
    /** 读回本会话已持久化的题面（只读，不出网）。 */
    RESTORE,

    /** 现场出题并落库（首次进入 / 学生明确重试）。 */
    GENERATE,

    /** 无需动作：已有题面、或已作答。 */
    NONE,
}

internal fun knowledgeQuizLoadPath(
    loadStatus: KnowledgeQuizLoadStatus,
    hasItem: Boolean,
    alreadySubmitted: Boolean,
): KnowledgeQuizLoadPath = when {
    alreadySubmitted || hasItem -> KnowledgeQuizLoadPath.NONE
    loadStatus == KnowledgeQuizLoadStatus.LOADED -> KnowledgeQuizLoadPath.RESTORE
    loadStatus == KnowledgeQuizLoadStatus.IDLE -> KnowledgeQuizLoadPath.GENERATE
    else -> KnowledgeQuizLoadPath.NONE
}

/**
 * 知识点复习会话的一个展示单元（spec dual-review-entry §3.3/§3.4）：负责"当前知识点"
 * 从取题到判答回写的状态机。整个有序队列、当前索引与作答判决持久化在 SavedStateHandle，
 * 重建时不把旧作答套到新计划的同一索引。模型取题与掌握度回写都经回调注入（对齐
 * ReviewSessionViewModel/CapturedReviewSessionViewModel），本类不持有任何
 * repository/gateway，纯 JVM 可测。
 *
 * 进程重建语义（AUD-02 起）：题面由 app 层按 `(会话, 知识点)` 落库（复用既有呈现快照写口），
 * 重建后不再"回 IDLE 重出题"——[KnowledgeQuizLoadStatus.LOADED] 的原样恢复意味着
 * **读回原题面**（[restoreCurrentQuiz]，只读不出网）；读不回（缺失/损坏）落
 * [KnowledgeQuizLoadStatus.FAILED]，走既有失败出口。只有从未出过题的 [IDLE]（首次进入、
 * 明确重试）才现场出题。
 */
internal class KnowledgeReviewSessionViewModel(
    private val savedStateHandle: SavedStateHandle,
    plan: KnowledgeReviewSessionPlan,
) : ViewModel() {
    private val sessionQueue = restoreSessionQueue(plan)

    /** 缺少/损坏身份快照时不能把旧选择、判决配到新队列，也不能自动重新出题。 */
    val sessionRestoreFailed: Boolean = sessionQueue == null

    internal var currentIndex: Int by mutableStateOf(
        restoredCurrentIndex(),
    )
        private set

    /** 当前展示的知识点；队列走完后为 null。 */
    val currentEntry: KnowledgeReviewQueueEntry?
        get() = sessionQueue?.getOrNull(currentIndex)

    val queueSize: Int
        get() = sessionQueue?.size ?: 0

    /**
     * 当前知识点这次的题面（内存态；库内真值由 app 层按 `(会话, 知识点)` 的呈现快照保存，
     * 进程重建后经 [restoreCurrentQuiz] 读回同一道题）。
     */
    var currentItem: TutorAssessmentItem? by mutableStateOf(null)
        private set

    var loadStatus by mutableStateOf(restoredLoadStatus())
        private set

    var selectedChoice: String? by mutableStateOf(savedStateHandle[SELECTED_CHOICE_KEY])
        private set

    var submittedChoice: String? by mutableStateOf(savedStateHandle[SUBMITTED_CHOICE_KEY])
        private set

    var submitStatus by mutableStateOf(restoredSubmitStatus())
        private set

    /** 回写结果；null = 尚未提交或未记录。 */
    var feedbackResult: KnowledgeQuizFeedbackResult? by mutableStateOf(
        savedStateHandle.get<String>(FEEDBACK_RESULT_KEY)?.let(::restoreFeedbackResult),
    )
        private set

    private val sessionStartedAtEpochMillis: Long =
        savedStateHandle.get<Long>(SESSION_STARTED_AT_KEY)
            ?: System.currentTimeMillis().also {
                savedStateHandle[SESSION_STARTED_AT_KEY] = it
            }

    /**
     * 当前节点已回写、可以前进（下一知识点或完成）。末位节点回写后仍为 true——
     * 按钮文案由 [completed] 区分（"完成" vs "下一知识点"）。
     */
    val canContinue: Boolean
        get() = currentEntry != null &&
            submitStatus == KnowledgeQuizSubmitStatus.RECORDED

    /** 末项已回写，或已前进至队尾（包括跳过末项）；跳过不伪造作答判决。 */
    val completed: Boolean
        get() = queueSize > 0 &&
            (currentIndex >= queueSize ||
                submitStatus == KnowledgeQuizSubmitStatus.RECORDED && isLastIndex(currentIndex))

    fun select(choiceId: String) {
        if (submittedChoice != null) return
        val item = currentItem ?: return
        if (item.choices.none { it.id == choiceId }) return
        selectedChoice = choiceId
        savedStateHandle[SELECTED_CHOICE_KEY] = choiceId
    }

    /**
     * 为当前知识点取题。回调返回 null 或抛异常 = 本次取题失败（可重试，不丢节点）。
     * 幂等：同一节点多次调用只派发一次（LOADING 中忽略）。
     *
     * [load] 同时拿到本会话标识：生成物落库的身份是 `(会话, 知识点)`，app 层据此把题面
     * 存进呈现快照表（AUD-02），重试/恢复才有一份库内真值可对。
     */
    fun loadCurrentQuiz(
        load: suspend (KnowledgeReviewQueueEntry, sessionId: Long) -> KnowledgeQuizLoadResult,
    ) {
        val entry = currentEntry ?: return
        if (loadStatus == KnowledgeQuizLoadStatus.LOADING) return
        if (submittedChoice != null) return
        updateLoadStatus(KnowledgeQuizLoadStatus.LOADING)
        runQuizLoad(entry, load)
    }

    /**
     * 恢复路径（AUD-02）：题面已随会话落库，进程重建后把它**读回来**（只读，不出网）。
     *
     * 前置是"本会话已出过题"（[loadStatus] == [KnowledgeQuizLoadStatus.LOADED]，内存里的
     * currentItem 已丢）：只有这种状态才有"原题面"可言。读不回（缺失/损坏）→
     * [KnowledgeQuizLoadStatus.FAILED]——走既有失败出口，不静默重出题、不再出网。
     *
     * 刻意不置 LOADING：local 读不该说成"正在出题"；读成或读不成，状态各自落定。
     */
    fun restoreCurrentQuiz(
        restore: suspend (KnowledgeReviewQueueEntry, sessionId: Long) -> KnowledgeQuizLoadResult,
    ) {
        if (loadStatus != KnowledgeQuizLoadStatus.LOADED) return
        if (currentItem != null) return
        if (submittedChoice != null) return
        val entry = currentEntry ?: return
        viewModelScope.launch {
            try {
                when (val result = restore(entry, sessionStartedAtEpochMillis)) {
                    is KnowledgeQuizLoadResult.Ready -> {
                        val item = result.item
                        check(item.knowledgeNodeIds.contains(entry.knowledgeNodeId)) {
                            "Restored quiz must stay anchored to the reviewed knowledge node"
                        }
                        currentItem = item
                    }
                    // 读不回：缺失或损坏都是"这次恢复没有题面"，走既有失败出口。
                    KnowledgeQuizLoadResult.KnowledgePreparing,
                    KnowledgeQuizLoadResult.NoMaterial,
                    KnowledgeQuizLoadResult.Unavailable,
                    -> updateLoadStatus(KnowledgeQuizLoadStatus.FAILED)
                }
            } catch (cancelled: CancellationException) {
                // 取消不改状态：改回 IDLE 会让下一次重建走"现场出题"（等于悄悄重新出网）。
                throw cancelled
            } catch (_: Exception) {
                updateLoadStatus(KnowledgeQuizLoadStatus.FAILED)
            }
        }
    }

    private fun runQuizLoad(
        entry: KnowledgeReviewQueueEntry,
        load: suspend (KnowledgeReviewQueueEntry, Long) -> KnowledgeQuizLoadResult,
    ) {
        viewModelScope.launch {
            try {
                when (val result = load(entry, sessionStartedAtEpochMillis)) {
                    is KnowledgeQuizLoadResult.Ready -> {
                        val item = result.item
                        check(item.knowledgeNodeIds.contains(entry.knowledgeNodeId)) {
                            "Generated quiz must stay anchored to the reviewed knowledge node"
                        }
                        currentItem = item
                        updateLoadStatus(KnowledgeQuizLoadStatus.LOADED)
                    }
                    // 每种"不能出题"各自成态：学生看到的话与出路都不同（D-Q3）。
                    KnowledgeQuizLoadResult.KnowledgePreparing ->
                        updateLoadStatus(KnowledgeQuizLoadStatus.KNOWLEDGE_PREPARING)
                    KnowledgeQuizLoadResult.NoMaterial ->
                        updateLoadStatus(KnowledgeQuizLoadStatus.NO_MATERIAL)
                    KnowledgeQuizLoadResult.Unavailable ->
                        updateLoadStatus(KnowledgeQuizLoadStatus.FAILED)
                }
            } catch (cancelled: CancellationException) {
                updateLoadStatus(KnowledgeQuizLoadStatus.IDLE)
                throw cancelled
            } catch (_: Exception) {
                updateLoadStatus(KnowledgeQuizLoadStatus.FAILED)
            }
        }
    }

    /**
     * 提交当前选择并回写掌握度。判答与回写由调用方（app 层）注入，签名对齐
     * StudyExperienceRepository.submitKnowledgeQuizFeedback。判答一致性在此校验：
     * 持久化的 isCorrect 必须等于本地 evaluateChoice 的客观对错，防止远端回写越权。
     *
     * 提交身份（requestId）由本类从**会话 + 题面 + 选项**派生（[knowledgeQuizSubmissionId]），
     * 不由界面拼：界面拼串时没有会话分量，同一次重试与隔日第二笔结构上不可区分（AUD-01）。
     */
    fun submitAnswer(
        occurredAtEpochMillis: Long,
        submit: suspend (
            requestId: String,
            knowledgeNodeId: String,
            correctChoiceId: String,
            selectedChoiceId: String,
            occurredAtEpochMillis: Long,
            conversationId: String,
        ) -> KnowledgeQuizFeedbackResult,
    ) {
        if (submitStatus == KnowledgeQuizSubmitStatus.RECORDING) return
        if (submittedChoice != null) return
        val item = currentItem ?: return
        val entry = currentEntry ?: return
        val choiceId = selectedChoice ?: return

        updateSubmitStatus(KnowledgeQuizSubmitStatus.RECORDING)
        viewModelScope.launch {
            try {
                val result = submit(
                    knowledgeQuizSubmissionId(item.id, choiceId),
                    entry.knowledgeNodeId,
                    item.correctChoiceId,
                    choiceId,
                    occurredAtEpochMillis,
                    knowledgeQuizConversationId(),
                )
                check(result.isCorrect == item.evaluateChoice(choiceId).isCorrect) {
                    "Persisted knowledge-quiz verdict disagrees with the locally judged choice"
                }
                submittedChoice = choiceId
                savedStateHandle[SUBMITTED_CHOICE_KEY] = choiceId
                feedbackResult = result
                savedStateHandle[FEEDBACK_RESULT_KEY] = serialize(result)
                updateSubmitStatus(KnowledgeQuizSubmitStatus.RECORDED)
            } catch (cancelled: CancellationException) {
                updateSubmitStatus(KnowledgeQuizSubmitStatus.IDLE)
                throw cancelled
            } catch (_: Exception) {
                updateSubmitStatus(KnowledgeQuizSubmitStatus.FAILED)
            }
        }
    }

    /**
     * 本次知识点复习会话的写入门控标识（审计 2026-09-09 P1）：配额必须按"每次复习会话"
     * 计数，而不是全局固定 id——后者会让每会话 50 条上限变成终身上限，写满后永久拒写。
     * 会话起点持久化在 SavedStateHandle，进程重建后仍是同一次会话。
     */
    private fun knowledgeQuizConversationId(): String =
        "knowledge-quiz-review:$sessionStartedAtEpochMillis"

    /**
     * 本会话的写入标识（`knowledge-quiz-review:<会话起点>`）。答键改正要用它定位**本会话**
     * 那条被拒观察行——题面/选项按会话重新生成，`itemId` 是常量，不能跨会话挑旧观察行。
     */
    internal val conversationId: String
        get() = knowledgeQuizConversationId()

    /**
     * 本次作答的提交身份（AUD-01）：**会话分量 + 题面 + 选项**。
     *
     * - 同一会话、同一题面、同一选项 → 恒同 id：同一次重试是同一笔证据（下游按身份幂等，
     *   不重复落库、不撞 outbox 唯一键）；
     * - 换一场会话（隔日再来）→ 不同 id：隔日同选项各自成笔。
     *
     * 改前身份是 `knowledge-quiz:review:<题面>:<选项>`，没有会话分量：隔日第二笔与同次重试
     * 撞同一个证据 id，被幂等键吞掉（"隔日两笔"结构上不可能）。会话分量取会话起点
     * （SavedStateHandle 持久化，系统回收后仍是同一会话；force-stop 丢态开新会话，见 KDoc）。
     */
    private fun knowledgeQuizSubmissionId(itemId: String, choiceId: String): String =
        "knowledge-quiz:review:$sessionStartedAtEpochMillis:$itemId:$choiceId"

    /** 进入下一知识点；若当前是末位则标记会话完成（Screen 负责返回首页）。 */
    fun continueToNext() {
        if (!canContinue) return
        advance()
    }

    /**
     * 跳过当前知识点（取题持续失败时的出口）：零权重——不写掌握度、节点保持到期，
     * 只在本会话内前进，避免一个节点卡住整场复习。
     */
    fun skipCurrentNode() {
        if (currentEntry == null) return
        advance()
    }

    private fun advance() {
        currentIndex += 1
        savedStateHandle[CURRENT_INDEX_KEY] = currentIndex
        clearNodeState()
    }

    private fun clearNodeState() {
        currentItem = null
        selectedChoice = null
        savedStateHandle.remove<String>(SELECTED_CHOICE_KEY)
        submittedChoice = null
        savedStateHandle.remove<String>(SUBMITTED_CHOICE_KEY)
        feedbackResult = null
        savedStateHandle.remove<String>(FEEDBACK_RESULT_KEY)
        updateLoadStatus(KnowledgeQuizLoadStatus.IDLE)
        updateSubmitStatus(KnowledgeQuizSubmitStatus.IDLE)
    }

    private fun isLastIndex(index: Int): Boolean = index == queueSize - 1

    private fun restoreSessionQueue(plan: KnowledgeReviewSessionPlan): List<KnowledgeReviewQueueEntry>? {
        if (savedStateHandle.contains(SESSION_QUEUE_KEY)) {
            return runCatching {
                val fields = savedStateHandle.get<ArrayList<String>>(SESSION_QUEUE_KEY)
                    ?: error("Missing session queue")
                require(fields.size % QUEUE_ENTRY_FIELD_COUNT == 0)
                KnowledgeReviewSessionPlan(
                    fields.chunked(QUEUE_ENTRY_FIELD_COUNT).map { values ->
                        KnowledgeReviewQueueEntry(
                            knowledgeNodeId = values[0],
                            subject = values[1],
                            displayName = values[2],
                            masteryScore = values[3].takeIf(String::isNotEmpty)?.toDouble(),
                            lastEvidenceAtEpochMillis = values[4].takeIf(String::isNotEmpty)?.toLong(),
                        )
                    },
                ).queue
            }.getOrNull()
        }
        // 旧版只存索引/判决，没有足够信息证明它属于今天新排的哪个知识点。
        if (savedStateHandle.contains(CURRENT_INDEX_KEY) ||
            NODE_STATE_KEYS.any(savedStateHandle::contains)
        ) {
            return null
        }
        savedStateHandle[SESSION_QUEUE_KEY] = ArrayList<String>().apply {
            plan.queue.forEach { entry ->
                add(entry.knowledgeNodeId)
                add(entry.subject)
                add(entry.displayName)
                add(entry.masteryScore?.toString().orEmpty())
                add(entry.lastEvidenceAtEpochMillis?.toString().orEmpty())
            }
        }
        return plan.queue.toList()
    }

    private fun restoredCurrentIndex(): Int {
        val savedIndex = savedStateHandle.get<Int>(CURRENT_INDEX_KEY) ?: 0
        val index = savedIndex.coerceIn(0, queueSize)
        if (sessionRestoreFailed || index != savedIndex || index == queueSize) {
            NODE_STATE_KEYS.forEach { savedStateHandle.remove<Any>(it) }
        }
        savedStateHandle[CURRENT_INDEX_KEY] = index
        return index
    }

    private fun updateLoadStatus(status: KnowledgeQuizLoadStatus) {
        loadStatus = status
        savedStateHandle[LOAD_STATUS_KEY] = status.name
    }

    private fun updateSubmitStatus(status: KnowledgeQuizSubmitStatus) {
        submitStatus = status
        savedStateHandle[SUBMIT_STATUS_KEY] = status.name
    }

    private fun restoredLoadStatus(): KnowledgeQuizLoadStatus {
        // 已提交的节点无需重建题目。
        if (savedStateHandle.get<String>(SUBMITTED_CHOICE_KEY) != null) {
            return KnowledgeQuizLoadStatus.IDLE
        }
        val restored = savedStateHandle.get<String>(LOAD_STATUS_KEY)
            ?.let { runCatching { KnowledgeQuizLoadStatus.valueOf(it) }.getOrNull() }
            ?: KnowledgeQuizLoadStatus.IDLE
        return when (restored) {
            // 重建后 currentItem 只存内存、必然丢失，但题面已随会话落库（AUD-02）：
            // LOADED 原样保留，Screen 据此走**读回**（不重出题、不再出网）；
            // 读不回（缺失/损坏）由恢复路径落 FAILED，走既有失败出口。
            // LOADING 同样按"可能有题面"处理：进程死在生成中途，也可能已经落库——
            // 先读回，读不回走失败出口，而不是自动再来一次出网。
            KnowledgeQuizLoadStatus.LOADING,
            KnowledgeQuizLoadStatus.LOADED,
            -> KnowledgeQuizLoadStatus.LOADED
            // "内容还在准备"回到 IDLE：那时一道题都还没出过，没有题面可读回；
            // 重建时知识内容多半已经就位，自动重取就是 D-Q3 要的"就绪后自动放行"。
            KnowledgeQuizLoadStatus.KNOWLEDGE_PREPARING,
            -> KnowledgeQuizLoadStatus.IDLE
            else -> restored
        }
    }

    private fun restoredSubmitStatus(): KnowledgeQuizSubmitStatus {
        if (savedStateHandle.get<String>(SUBMITTED_CHOICE_KEY) != null) {
            return KnowledgeQuizSubmitStatus.RECORDED
        }
        val restored = savedStateHandle.get<String>(SUBMIT_STATUS_KEY)
            ?.let { runCatching { KnowledgeQuizSubmitStatus.valueOf(it) }.getOrNull() }
            ?: KnowledgeQuizSubmitStatus.IDLE
        return if (restored == KnowledgeQuizSubmitStatus.RECORDING) {
            KnowledgeQuizSubmitStatus.FAILED
        } else {
            restored
        }
    }

    private fun serialize(result: KnowledgeQuizFeedbackResult): String = buildString {
        append(if (result.isCorrect) "1" else "0")
        append(':')
        append(if (result.evidenceRecorded) "1" else "0")
        append(':')
        append(result.rejectedReason.orEmpty())
    }

    private fun restoreFeedbackResult(raw: String): KnowledgeQuizFeedbackResult {
        val parts = raw.split(':', limit = 3)
        return KnowledgeQuizFeedbackResult(
            isCorrect = parts.getOrNull(0) == "1",
            evidenceRecorded = parts.getOrNull(1) == "1",
            rejectedReason = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
        )
    }

    internal companion object {
        const val CURRENT_INDEX_KEY = "kq_current_index"
        const val SESSION_QUEUE_KEY = "kq_session_queue_v1"
        const val SESSION_STARTED_AT_KEY = "kq_session_started_at"
        const val LOAD_STATUS_KEY = "kq_load_status"
        const val SELECTED_CHOICE_KEY = "kq_selected_choice"
        const val SUBMITTED_CHOICE_KEY = "kq_submitted_choice"
        const val SUBMIT_STATUS_KEY = "kq_submit_status"
        const val FEEDBACK_RESULT_KEY = "kq_feedback_result"
        private const val QUEUE_ENTRY_FIELD_COUNT = 5
        private val NODE_STATE_KEYS = listOf(
            LOAD_STATUS_KEY,
            SELECTED_CHOICE_KEY,
            SUBMITTED_CHOICE_KEY,
            SUBMIT_STATUS_KEY,
            FEEDBACK_RESULT_KEY,
        )
    }
}
