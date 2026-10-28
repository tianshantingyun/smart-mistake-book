package com.tingyun.smartmistakebook.feature.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.ReviewSelfReportKey
import com.tingyun.smartmistakebook.core.domain.ReviewStoredAnswer
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionProgress
import com.tingyun.smartmistakebook.core.domain.StudyReviewSessionStatus
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoRequest
import com.tingyun.smartmistakebook.core.domain.StudyReviewUndoResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewResult
import com.tingyun.smartmistakebook.core.domain.StudySelfReportedReviewSubmission
import com.tingyun.smartmistakebook.core.ui.SafeMarkdownText
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * PRD-05 三键自评的界面状态。
 *
 * 三键的语义（裁定 32 + Anki 官方 Hard 警告）：
 * - 对 = Good：顺利回忆；
 * - 卡顿 = Hard：**费力但回忆成功**（不是"没想起来"）；
 * - 错 = Again：没想起来/答错。
 *
 * 没有 Easy。实现不把"卡顿"当失败，也不把"错"塞进 Hard。
 */
internal enum class SelfReportStatus {
    IDLE,
    RECORDING,
    RECORDED,
    FAILED,
}

internal enum class SelfReportUndoStatus {
    IDLE,
    UNDONE,
    RECORDING,
    FAILED,
}

/**
 * undo 的持久回执（correction 行 + outbox 行）。学生撤销后要看得见"撤销真的写进去了"，
 * 而不是只看见状态被清空；重复撤销的回执（created=false）还要如实说明不会再回退一次。
 */
internal data class ReviewUndoReceipt(
    val correctionId: String,
    val created: Boolean,
    val nextPracticeUnitId: String?,
)

/** 已落库的自评结果（旋转/进程重建后由 SavedStateHandle 恢复）。 */
internal data class RecordedSelfReport(
    val attemptId: String,
    val evidenceReason: String,
    /** 学生实际按下的键。Good/Hard 写入同一证据口径，但回执必须能区分这两档。 */
    val key: ReviewSelfReportKey?,
    val objectiveChoiceCorrect: Boolean?,
    val sessionId: String,
    val planId: String,
    val currentOrdinal: Int,
    val queueSize: Int,
    val stateVersion: Long,
    val status: String,
    val nextPracticeUnitId: String?,
) {
    fun progress(): StudyReviewSessionProgress = StudyReviewSessionProgress(
        sessionId = sessionId,
        planId = planId,
        currentOrdinal = currentOrdinal,
        queueSize = queueSize,
        stateVersion = stateVersion,
        status = StudyReviewSessionStatus.valueOf(status),
    )
}

/** 三键文案唯一来源：标签、辅助说明都必须各不相同，Hard 是"想起来"而不是失败。 */
internal data class SelfReportKeyPresentation(
    val label: String,
    val supporting: String,
)

internal fun selfReportKeyPresentation(key: ReviewSelfReportKey): SelfReportKeyPresentation =
    when (key) {
        ReviewSelfReportKey.GOOD -> SelfReportKeyPresentation("对", "顺利回忆")
        ReviewSelfReportKey.HARD -> SelfReportKeyPresentation("卡顿", "费力但想起来了")
        ReviewSelfReportKey.AGAIN -> SelfReportKeyPresentation("错", "没想起来/答错")
    }

/**
 * 自评通道是否完整接线。只有提交 + 撤销 + 持久会话身份都齐了才接管本题；
 * 半接线（例如只有提交）会让学生提交后没有任何撤回手段，必须继续走旧路径。
 */
internal fun selfReportChannelReady(
    hasSubmit: Boolean,
    hasUndo: Boolean,
    sessionId: String?,
    stateVersion: Long?,
): Boolean = hasSubmit &&
    hasUndo &&
    !sessionId.isNullOrBlank() &&
    stateVersion != null &&
    stateVersion >= 0

/**
 * 提交中断（写入成功但响应丢失）后，只保留原判定键可点：换键重试等于制造第二笔
 * 互相矛盾的 attempt，而数据库幂等键只认第一次的 request id。
 */
internal fun selfReportKeyEnabled(
    key: ReviewSelfReportKey,
    selected: ReviewSelfReportKey?,
    enabled: Boolean,
    pendingSubmit: Boolean,
): Boolean = enabled && (!pendingSubmit || key == selected)

/** 已落库回执：明确写出学生按的是哪一档。 */
internal fun selfReportRecordedMessage(
    key: ReviewSelfReportKey?,
    evidenceReason: String?,
): String {
    val copy = key?.let(::selfReportKeyPresentation)
    val judgement = if (copy == null) {
        "本次自评"
    } else {
        "${copy.label}（${copy.supporting}）"
    }
    val evidence = when (evidenceReason) {
        "SELF_REPORTED_RECALL" -> "自评回忆成功"
        "SELF_REPORTED_STUCK" -> "自评没想起来"
        else -> "自评"
    }
    return "已记录：$judgement。$evidence。"
}

/** 状态行文案（提交中断、撤销失败、撤销回执）。RECORDED/IDLE 由回执行单独承担。 */
internal fun selfReportStatusMessage(
    status: SelfReportStatus,
    undoStatus: SelfReportUndoStatus,
    undoReceipt: ReviewUndoReceipt?,
): String? = when {
    undoStatus == SelfReportUndoStatus.RECORDING -> "正在撤销本次作答…"
    undoStatus == SelfReportUndoStatus.FAILED -> "暂时无法确认撤销结果，请重试。"
    undoStatus == SelfReportUndoStatus.UNDONE -> when {
        undoReceipt == null -> "已撤销，可以重新作答。"
        undoReceipt.created -> "已撤销。" +
            "可以重新作答。"
        else -> "这次作答已撤销，不会重复撤销。" +
            "可以重新作答。"
    }
    status == SelfReportStatus.RECORDING -> "正在记录本次自评…"
    status == SelfReportStatus.FAILED -> "暂时无法确认记录结果，请重试；不会重复记录。"
    else -> null
}

/** 已保存的答案的可用性：null 是"未知"，不能伪装成"没有可信答案"的断言。 */
internal enum class StoredAnswerAvailability {
    TRUSTED,
    UNTRUSTED,
    UNKNOWN,
}

internal fun storedAnswerAvailability(storedAnswer: ReviewStoredAnswer?): StoredAnswerAvailability =
    when {
        storedAnswer == null -> StoredAnswerAvailability.UNKNOWN
        storedAnswer.isTrusted -> StoredAnswerAvailability.TRUSTED
        else -> StoredAnswerAvailability.UNTRUSTED
    }

/**
 * 三键自评控制器：稳定 request id + 持久回执，双击/旋转/提交中断幂等。
 *
 * `sessionId`/`stateVersion` 由本 VM 自己持有并在每次提交/撤销后按返回进度前进，
 * 因此 undo→重评不需要外部重新注入版本（调用方仍传初始值作为首次兜底）。
 */
internal class ReviewSelfReportViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var selectedKey: ReviewSelfReportKey? by mutableStateOf(
        savedStateHandle.get<String>(KEY_SELECTED)?.let {
            runCatching { ReviewSelfReportKey.valueOf(it) }.getOrNull()
        },
    )
        private set

    internal var status by mutableStateOf(restoredStatus())
        private set

    internal var undoStatus by mutableStateOf(restoredUndoStatus())
        private set

    var recorded: RecordedSelfReport? by mutableStateOf(restoreRecorded())
        private set

    /** undo 的持久回执；跨旋转保留，重复撤销时如实说明不会再次回退。 */
    var undoReceipt: ReviewUndoReceipt? by mutableStateOf(restoreUndoReceipt())
        private set

    /**
     * 是否有已持久化但还没拿到响应的提交。此时只允许用原判定键重试（同一 request id），
     * 不允许换键——换键会制造与可能已落库的那笔互相矛盾的 attempt。
     */
    val hasPendingSubmit: Boolean
        get() = savedStateHandle.get<String>(KEY_PENDING_REQUEST) != null

    /** 最新的持久会话进度（submit/undo 之后前进；供下一次提交使用）。 */
    var latestProgress: StudyReviewSessionProgress? by mutableStateOf(restoreProgress())
        private set

    private var attemptOrdinal: Int = savedStateHandle.get<Int>(KEY_ATTEMPT_ORDINAL) ?: 1

    fun select(key: ReviewSelfReportKey) {
        if (status == SelfReportStatus.RECORDING) return
        if (recorded != null) return
        // 提交中断的判定已经持久化：换键重试会绕过数据库的幂等身份。
        if (hasPendingSubmit) return
        if (selectedKey == key) return
        selectedKey = key
        savedStateHandle[KEY_SELECTED] = key.name
    }

    /**
     * 提交本次自评。仅当 [status] 不是 RECORDING/RECORDED 时生效；失败后用同一 request id
     * 重试（双击只会打一次）。
     */
    fun requestSubmit(
        presentationId: String,
        practiceUnitId: String,
        fallbackSessionId: String,
        fallbackStateVersion: Long,
        selectedChoiceId: String?,
        durationSeconds: Int,
        occurredAtEpochMillis: Long,
        hintCount: Int = 0,
        scrollUpCount: Int = 0,
        interruptionCount: Int = 0,
        awayMillis: Long = 0,
        submit: suspend (StudySelfReportedReviewSubmission) -> StudySelfReportedReviewResult,
    ) {
        if (status == SelfReportStatus.RECORDING) return
        if (recorded != null) return
        val key = selectedKey ?: return
        val command = pendingCommand(
            presentationId = presentationId,
            practiceUnitId = practiceUnitId,
            fallbackSessionId = fallbackSessionId,
            fallbackStateVersion = fallbackStateVersion,
            key = key,
            selectedChoiceId = selectedChoiceId,
            durationSeconds = durationSeconds,
            occurredAtEpochMillis = occurredAtEpochMillis,
            hintCount = hintCount,
            scrollUpCount = scrollUpCount,
            interruptionCount = interruptionCount,
            awayMillis = awayMillis,
        )
        updateStatus(SelfReportStatus.RECORDING)
        viewModelScope.launch {
            try {
                val result = submit(command)
                val progress = result.progress
                latestProgress = progress
                recorded = RecordedSelfReport(
                    attemptId = result.attemptId,
                    evidenceReason = result.evidenceReason.name,
                    key = key,
                    objectiveChoiceCorrect = result.objectiveChoiceCorrect,
                    sessionId = progress.sessionId,
                    planId = progress.planId,
                    currentOrdinal = progress.currentOrdinal,
                    queueSize = progress.queueSize,
                    stateVersion = progress.stateVersion,
                    status = progress.status.name,
                    nextPracticeUnitId = result.nextPracticeUnitId,
                )
                persistRecorded(requireNotNull(recorded))
                updateStatus(SelfReportStatus.RECORDED)
                clearPendingCommand()
            } catch (cancelled: CancellationException) {
                updateStatus(SelfReportStatus.IDLE)
                throw cancelled
            } catch (_: Exception) {
                updateStatus(SelfReportStatus.FAILED)
            }
        }
    }

    /**
     * 撤销刚记录的 attempt。成功后会话指针回退、清空本次提交状态并让下一次提交使用
     * 新的 request id（attemptOrdinal+1）——presentation 保持不变（重评保留曝光）。
     */
    fun requestUndo(
        presentationId: String,
        practiceUnitId: String,
        occurredAtEpochMillis: Long,
        undo: suspend (StudyReviewUndoRequest) -> StudyReviewUndoResult,
    ) {
        val recorded = recorded ?: return
        if (undoStatus == SelfReportUndoStatus.RECORDING) return
        val request = pendingUndoRequest(
            presentationId = presentationId,
            practiceUnitId = practiceUnitId,
            recorded = recorded,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        undoStatus = SelfReportUndoStatus.RECORDING
        savedStateHandle[KEY_UNDO_STATUS] = SelfReportUndoStatus.RECORDING.name
        viewModelScope.launch {
            try {
                val result = undo(request)
                latestProgress = result.progress
                this@ReviewSelfReportViewModel.recorded = null
                selectedKey = null
                // 清掉持久选择：否则撤销后旋转会把上一轮按下的键恢复成"已选中"。
                savedStateHandle.remove<String>(KEY_SELECTED)
                persistProgress(result.progress)
                clearRecorded()
                clearPendingCommand()
                clearPendingUndoRequest()
                val receipt = ReviewUndoReceipt(
                    correctionId = result.correctionId,
                    created = result.created,
                    nextPracticeUnitId = result.nextPracticeUnitId,
                )
                undoReceipt = receipt
                persistUndoReceipt(receipt)
                attemptOrdinal += 1
                savedStateHandle[KEY_ATTEMPT_ORDINAL] = attemptOrdinal
                updateStatus(SelfReportStatus.IDLE)
                undoStatus = SelfReportUndoStatus.UNDONE
                savedStateHandle[KEY_UNDO_STATUS] = SelfReportUndoStatus.UNDONE.name
            } catch (cancelled: CancellationException) {
                undoStatus = SelfReportUndoStatus.IDLE
                savedStateHandle[KEY_UNDO_STATUS] = SelfReportUndoStatus.IDLE.name
                throw cancelled
            } catch (_: Exception) {
                undoStatus = SelfReportUndoStatus.FAILED
                savedStateHandle[KEY_UNDO_STATUS] = SelfReportUndoStatus.FAILED.name
            }
        }
    }

    /** 撤销后重新选择/重评时，清掉"已撤销"提示。 */
    fun acknowledgeUndo() {
        if (undoStatus != SelfReportUndoStatus.UNDONE) return
        undoStatus = SelfReportUndoStatus.IDLE
        savedStateHandle[KEY_UNDO_STATUS] = SelfReportUndoStatus.IDLE.name
    }

    fun recordedResultOrNull(): StudySelfReportedReviewResult? {
        val recorded = recorded ?: return null
        val progress = recorded.progress()
        return StudySelfReportedReviewResult(
            attemptId = recorded.attemptId,
            created = true,
            evidenceReason = runCatching {
                com.tingyun.smartmistakebook.core.model.LearningEvidenceReason.valueOf(
                    recorded.evidenceReason,
                )
            }.getOrNull() ?: return null,
            objectiveChoiceCorrect = recorded.objectiveChoiceCorrect,
            progress = progress,
            nextPracticeUnitId = recorded.nextPracticeUnitId,
        )
    }

    internal fun currentAttemptOrdinal(): Int = attemptOrdinal

    private fun pendingCommand(
        presentationId: String,
        practiceUnitId: String,
        fallbackSessionId: String,
        fallbackStateVersion: Long,
        key: ReviewSelfReportKey,
        selectedChoiceId: String?,
        durationSeconds: Int,
        occurredAtEpochMillis: Long,
        hintCount: Int,
        scrollUpCount: Int,
        interruptionCount: Int,
        awayMillis: Long,
    ): StudySelfReportedReviewSubmission {
        savedStateHandle.get<String>(KEY_PENDING_REQUEST)?.let { requestId ->
            val pending = StudySelfReportedReviewSubmission(
                requestId = requestId,
                sessionId = requireNotNull(savedStateHandle[KEY_PENDING_SESSION]),
                expectedStateVersion = requireNotNull(savedStateHandle[KEY_PENDING_VERSION]),
                presentationId = requireNotNull(savedStateHandle[KEY_PENDING_PRESENTATION]),
                practiceUnitId = requireNotNull(savedStateHandle[KEY_PENDING_UNIT]),
                key = ReviewSelfReportKey.valueOf(requireNotNull(savedStateHandle[KEY_PENDING_KEY])),
                responseOrdinal = requireNotNull(savedStateHandle[KEY_PENDING_ORDINAL]),
                durationSeconds = requireNotNull(savedStateHandle[KEY_PENDING_DURATION]),
                occurredAtEpochMillis = requireNotNull(savedStateHandle[KEY_PENDING_OCCURRED_AT]),
                selectedChoiceId = savedStateHandle[KEY_PENDING_CHOICE],
                hintCount = requireNotNull(savedStateHandle[KEY_PENDING_HINT]),
                scrollUpCount = requireNotNull(savedStateHandle[KEY_PENDING_SCROLL]),
                interruptionCount = requireNotNull(savedStateHandle[KEY_PENDING_INTERRUPTIONS]),
                awayMillis = requireNotNull(savedStateHandle[KEY_PENDING_AWAY]),
            )
            require(pending.presentationId == presentationId)
            require(pending.practiceUnitId == practiceUnitId)
            require(pending.key == key)
            return pending
        }
        val progress = latestProgress
        val sessionId = progress?.sessionId ?: fallbackSessionId
        val expectedStateVersion = progress?.stateVersion ?: fallbackStateVersion
        val requestId = "request:self-report:$presentationId:$attemptOrdinal"
        return StudySelfReportedReviewSubmission(
            requestId = requestId,
            sessionId = sessionId,
            expectedStateVersion = expectedStateVersion,
            presentationId = presentationId,
            practiceUnitId = practiceUnitId,
            key = key,
            responseOrdinal = attemptOrdinal,
            durationSeconds = durationSeconds,
            occurredAtEpochMillis = occurredAtEpochMillis,
            selectedChoiceId = selectedChoiceId,
            hintCount = hintCount,
            scrollUpCount = scrollUpCount,
            interruptionCount = interruptionCount,
            awayMillis = awayMillis,
        ).also { command ->
            savedStateHandle[KEY_PENDING_REQUEST] = command.requestId
            savedStateHandle[KEY_PENDING_SESSION] = command.sessionId
            savedStateHandle[KEY_PENDING_VERSION] = command.expectedStateVersion
            savedStateHandle[KEY_PENDING_PRESENTATION] = command.presentationId
            savedStateHandle[KEY_PENDING_UNIT] = command.practiceUnitId
            savedStateHandle[KEY_PENDING_KEY] = command.key.name
            savedStateHandle[KEY_PENDING_ORDINAL] = command.responseOrdinal
            savedStateHandle[KEY_PENDING_DURATION] = command.durationSeconds
            savedStateHandle[KEY_PENDING_OCCURRED_AT] = command.occurredAtEpochMillis
            savedStateHandle[KEY_PENDING_CHOICE] = command.selectedChoiceId
            savedStateHandle[KEY_PENDING_HINT] = command.hintCount
            savedStateHandle[KEY_PENDING_SCROLL] = command.scrollUpCount
            savedStateHandle[KEY_PENDING_INTERRUPTIONS] = command.interruptionCount
            savedStateHandle[KEY_PENDING_AWAY] = command.awayMillis
        }
    }

    private fun pendingUndoRequest(
        presentationId: String,
        practiceUnitId: String,
        recorded: RecordedSelfReport,
        occurredAtEpochMillis: Long,
    ): StudyReviewUndoRequest {
        savedStateHandle.get<String>(KEY_PENDING_UNDO_REQUEST)?.let { requestId ->
            return StudyReviewUndoRequest(
                requestId = requestId,
                sessionId = requireNotNull(savedStateHandle[KEY_PENDING_UNDO_SESSION]),
                expectedStateVersion = requireNotNull(savedStateHandle[KEY_PENDING_UNDO_VERSION]),
                presentationId = requireNotNull(savedStateHandle[KEY_PENDING_UNDO_PRESENTATION]),
                practiceUnitId = requireNotNull(savedStateHandle[KEY_PENDING_UNDO_UNIT]),
                attemptId = requireNotNull(savedStateHandle[KEY_PENDING_UNDO_ATTEMPT]),
                occurredAtEpochMillis = requireNotNull(
                    savedStateHandle[KEY_PENDING_UNDO_OCCURRED_AT],
                ),
            )
        }
        val request = StudyReviewUndoRequest(
            requestId = "request:self-report-undo:$presentationId:${recorded.attemptId}",
            sessionId = recorded.sessionId,
            expectedStateVersion = recorded.stateVersion,
            presentationId = presentationId,
            practiceUnitId = practiceUnitId,
            attemptId = recorded.attemptId,
            occurredAtEpochMillis = occurredAtEpochMillis,
        )
        savedStateHandle[KEY_PENDING_UNDO_REQUEST] = request.requestId
        savedStateHandle[KEY_PENDING_UNDO_SESSION] = request.sessionId
        savedStateHandle[KEY_PENDING_UNDO_VERSION] = request.expectedStateVersion
        savedStateHandle[KEY_PENDING_UNDO_PRESENTATION] = request.presentationId
        savedStateHandle[KEY_PENDING_UNDO_UNIT] = request.practiceUnitId
        savedStateHandle[KEY_PENDING_UNDO_ATTEMPT] = request.attemptId
        savedStateHandle[KEY_PENDING_UNDO_OCCURRED_AT] = request.occurredAtEpochMillis
        return request
    }

    private fun updateStatus(next: SelfReportStatus) {
        status = next
        savedStateHandle[KEY_STATUS] = next.name
    }

    private fun restoredStatus(): SelfReportStatus {
        if (savedStateHandle.get<String>(KEY_ATTEMPT_ID) != null) {
            return SelfReportStatus.RECORDED
        }
        val restored = savedStateHandle.get<String>(KEY_STATUS)
            ?.let { runCatching { SelfReportStatus.valueOf(it) }.getOrNull() }
            ?: SelfReportStatus.IDLE
        // 提交中断（进程死亡）后不能永远停在 RECORDING：恢复成 FAILED，用同一 request id 重试。
        return if (restored == SelfReportStatus.RECORDING) SelfReportStatus.FAILED else restored
    }

    private fun restoredUndoStatus(): SelfReportUndoStatus {
        val restored = savedStateHandle.get<String>(KEY_UNDO_STATUS)
            ?.let { runCatching { SelfReportUndoStatus.valueOf(it) }.getOrNull() }
            ?: SelfReportUndoStatus.IDLE
        // 与提交流程同理：撤销进行中被进程杀死不能永远停在 RECORDING（按钮会被永久禁用）。
        // 恢复成 FAILED，保留 recorded 与 pending undo 身份，让学生能重试。
        return if (restored == SelfReportUndoStatus.RECORDING) {
            SelfReportUndoStatus.FAILED
        } else {
            restored
        }
    }

    private fun restoreRecorded(): RecordedSelfReport? {
        val attemptId = savedStateHandle.get<String>(KEY_ATTEMPT_ID) ?: return null
        val evidenceReason = savedStateHandle.get<String>(KEY_ATTEMPT_REASON) ?: return null
        return RecordedSelfReport(
            attemptId = attemptId,
            evidenceReason = evidenceReason,
            key = savedStateHandle.get<String>(KEY_ATTEMPT_KEY)
                ?.let { runCatching { ReviewSelfReportKey.valueOf(it) }.getOrNull() }
                // 旧 SavedStateHandle（本次发布前）没有键名：按证据口径回退，至少不谎称 Good。
                ?: when (evidenceReason) {
                    "SELF_REPORTED_RECALL" -> ReviewSelfReportKey.GOOD
                    "SELF_REPORTED_STUCK" -> ReviewSelfReportKey.AGAIN
                    else -> null
                },
            objectiveChoiceCorrect = savedStateHandle.get<Boolean>(KEY_ATTEMPT_OBJECTIVE),
            sessionId = savedStateHandle.get<String>(KEY_RESULT_SESSION) ?: return null,
            planId = savedStateHandle.get<String>(KEY_RESULT_PLAN) ?: return null,
            currentOrdinal = savedStateHandle.get<Int>(KEY_RESULT_ORDINAL) ?: return null,
            queueSize = savedStateHandle.get<Int>(KEY_RESULT_QUEUE_SIZE) ?: return null,
            stateVersion = savedStateHandle.get<Long>(KEY_RESULT_VERSION) ?: return null,
            status = savedStateHandle.get<String>(KEY_RESULT_STATUS) ?: return null,
            nextPracticeUnitId = savedStateHandle.get<String>(KEY_RESULT_NEXT),
        )
    }

    private fun persistRecorded(recorded: RecordedSelfReport) {
        savedStateHandle[KEY_ATTEMPT_ID] = recorded.attemptId
        savedStateHandle[KEY_ATTEMPT_REASON] = recorded.evidenceReason
        savedStateHandle[KEY_ATTEMPT_KEY] = recorded.key?.name
        savedStateHandle[KEY_ATTEMPT_OBJECTIVE] = recorded.objectiveChoiceCorrect
        savedStateHandle[KEY_RESULT_SESSION] = recorded.sessionId
        savedStateHandle[KEY_RESULT_PLAN] = recorded.planId
        savedStateHandle[KEY_RESULT_ORDINAL] = recorded.currentOrdinal
        savedStateHandle[KEY_RESULT_QUEUE_SIZE] = recorded.queueSize
        savedStateHandle[KEY_RESULT_VERSION] = recorded.stateVersion
        savedStateHandle[KEY_RESULT_STATUS] = recorded.status
        savedStateHandle[KEY_RESULT_NEXT] = recorded.nextPracticeUnitId
    }

    private fun clearRecorded() {
        savedStateHandle.remove<String>(KEY_ATTEMPT_ID)
        savedStateHandle.remove<String>(KEY_ATTEMPT_REASON)
        savedStateHandle.remove<String>(KEY_ATTEMPT_KEY)
        savedStateHandle.remove<Boolean>(KEY_ATTEMPT_OBJECTIVE)
        savedStateHandle.remove<String>(KEY_RESULT_NEXT)
    }

    private fun restoreUndoReceipt(): ReviewUndoReceipt? {
        val correctionId = savedStateHandle.get<String>(KEY_UNDO_RECEIPT_ID) ?: return null
        return ReviewUndoReceipt(
            correctionId = correctionId,
            created = savedStateHandle.get<Boolean>(KEY_UNDO_RECEIPT_CREATED) ?: false,
            nextPracticeUnitId = savedStateHandle.get<String>(KEY_UNDO_RECEIPT_NEXT),
        )
    }

    private fun persistUndoReceipt(receipt: ReviewUndoReceipt) {
        savedStateHandle[KEY_UNDO_RECEIPT_ID] = receipt.correctionId
        savedStateHandle[KEY_UNDO_RECEIPT_CREATED] = receipt.created
        savedStateHandle[KEY_UNDO_RECEIPT_NEXT] = receipt.nextPracticeUnitId
    }

    /** 进度独立于"已记录 attempt"持久化：undo 后 attempt 清空，但回退后的版本仍要供重评。 */
    private fun restoreProgress(): StudyReviewSessionProgress? {
        val sessionId = savedStateHandle.get<String>(KEY_RESULT_SESSION) ?: return null
        val planId = savedStateHandle.get<String>(KEY_RESULT_PLAN) ?: return null
        val currentOrdinal = savedStateHandle.get<Int>(KEY_RESULT_ORDINAL) ?: return null
        val queueSize = savedStateHandle.get<Int>(KEY_RESULT_QUEUE_SIZE) ?: return null
        val stateVersion = savedStateHandle.get<Long>(KEY_RESULT_VERSION) ?: return null
        val status = savedStateHandle.get<String>(KEY_RESULT_STATUS)
            ?.let { runCatching { StudyReviewSessionStatus.valueOf(it) }.getOrNull() }
            ?: return null
        return StudyReviewSessionProgress(
            sessionId = sessionId,
            planId = planId,
            currentOrdinal = currentOrdinal,
            queueSize = queueSize,
            stateVersion = stateVersion,
            status = status,
        )
    }

    private fun persistProgress(progress: StudyReviewSessionProgress) {
        savedStateHandle[KEY_RESULT_SESSION] = progress.sessionId
        savedStateHandle[KEY_RESULT_PLAN] = progress.planId
        savedStateHandle[KEY_RESULT_ORDINAL] = progress.currentOrdinal
        savedStateHandle[KEY_RESULT_QUEUE_SIZE] = progress.queueSize
        savedStateHandle[KEY_RESULT_VERSION] = progress.stateVersion
        savedStateHandle[KEY_RESULT_STATUS] = progress.status.name
    }

    private fun clearPendingCommand() {
        listOf(
            KEY_PENDING_REQUEST,
            KEY_PENDING_SESSION,
            KEY_PENDING_VERSION,
            KEY_PENDING_PRESENTATION,
            KEY_PENDING_UNIT,
            KEY_PENDING_KEY,
            KEY_PENDING_ORDINAL,
            KEY_PENDING_DURATION,
            KEY_PENDING_OCCURRED_AT,
            KEY_PENDING_CHOICE,
            KEY_PENDING_HINT,
            KEY_PENDING_SCROLL,
            KEY_PENDING_INTERRUPTIONS,
            KEY_PENDING_AWAY,
        ).forEach { savedStateHandle.remove<Any?>(it) }
    }

    private fun clearPendingUndoRequest() {
        listOf(
            KEY_PENDING_UNDO_REQUEST,
            KEY_PENDING_UNDO_SESSION,
            KEY_PENDING_UNDO_VERSION,
            KEY_PENDING_UNDO_PRESENTATION,
            KEY_PENDING_UNDO_UNIT,
            KEY_PENDING_UNDO_ATTEMPT,
            KEY_PENDING_UNDO_OCCURRED_AT,
        ).forEach { savedStateHandle.remove<Any?>(it) }
    }

    private companion object {
        const val KEY_SELECTED = "review_self_report_selected"
        const val KEY_STATUS = "review_self_report_status"
        const val KEY_UNDO_STATUS = "review_self_report_undo_status"
        const val KEY_UNDO_RECEIPT_ID = "review_self_report_undo_receipt_id"
        const val KEY_UNDO_RECEIPT_CREATED = "review_self_report_undo_receipt_created"
        const val KEY_UNDO_RECEIPT_NEXT = "review_self_report_undo_receipt_next"
        const val KEY_ATTEMPT_ORDINAL = "review_self_report_attempt_ordinal"
        const val KEY_ATTEMPT_ID = "review_self_report_attempt_id"
        const val KEY_ATTEMPT_REASON = "review_self_report_attempt_reason"
        const val KEY_ATTEMPT_KEY = "review_self_report_attempt_key"
        const val KEY_ATTEMPT_OBJECTIVE = "review_self_report_attempt_objective"
        const val KEY_RESULT_SESSION = "review_self_report_result_session"
        const val KEY_RESULT_PLAN = "review_self_report_result_plan"
        const val KEY_RESULT_ORDINAL = "review_self_report_result_ordinal"
        const val KEY_RESULT_QUEUE_SIZE = "review_self_report_result_queue_size"
        const val KEY_RESULT_VERSION = "review_self_report_result_version"
        const val KEY_RESULT_STATUS = "review_self_report_result_status"
        const val KEY_RESULT_NEXT = "review_self_report_result_next"
        const val KEY_PENDING_REQUEST = "review_self_report_pending_request"
        const val KEY_PENDING_SESSION = "review_self_report_pending_session"
        const val KEY_PENDING_VERSION = "review_self_report_pending_version"
        const val KEY_PENDING_PRESENTATION = "review_self_report_pending_presentation"
        const val KEY_PENDING_UNIT = "review_self_report_pending_unit"
        const val KEY_PENDING_KEY = "review_self_report_pending_key"
        const val KEY_PENDING_ORDINAL = "review_self_report_pending_ordinal"
        const val KEY_PENDING_DURATION = "review_self_report_pending_duration"
        const val KEY_PENDING_OCCURRED_AT = "review_self_report_pending_occurred_at"
        const val KEY_PENDING_CHOICE = "review_self_report_pending_choice"
        const val KEY_PENDING_HINT = "review_self_report_pending_hint"
        const val KEY_PENDING_SCROLL = "review_self_report_pending_scroll"
        const val KEY_PENDING_INTERRUPTIONS = "review_self_report_pending_interruptions"
        const val KEY_PENDING_AWAY = "review_self_report_pending_away"
        const val KEY_PENDING_UNDO_REQUEST = "review_self_report_pending_undo_request"
        const val KEY_PENDING_UNDO_SESSION = "review_self_report_pending_undo_session"
        const val KEY_PENDING_UNDO_VERSION = "review_self_report_pending_undo_version"
        const val KEY_PENDING_UNDO_PRESENTATION = "review_self_report_pending_undo_presentation"
        const val KEY_PENDING_UNDO_UNIT = "review_self_report_pending_undo_unit"
        const val KEY_PENDING_UNDO_ATTEMPT = "review_self_report_pending_undo_attempt"
        const val KEY_PENDING_UNDO_OCCURRED_AT = "review_self_report_pending_undo_occurred_at"
    }
}

/**
 * 三键按钮（无 Easy）。[enabled] 为 false 时全部禁用（提交中/已记录）。
 * 标签刻意把"卡顿"解释成"费力但想起来了"，消灭 Hard 误用。
 */
@Composable
internal fun ReviewSelfReportKeys(
    selected: ReviewSelfReportKey?,
    enabled: Boolean,
    onSelect: (ReviewSelfReportKey) -> Unit,
    modifier: Modifier = Modifier,
    /** 有已持久化但未拿到回执的提交：只有原判定键保持可点（用同一 request id 重试）。 */
    pendingSubmit: Boolean = false,
) {
    WorkspacePanel(modifier = modifier.testTag("review_self_report_keys")) {
        Column {
            Text(
                text = "这次回忆得怎么样？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartColors.Ink,
            )
            Spacer(Modifier.height(8.dp))
            val renderKey: @Composable (ReviewSelfReportKey, Modifier) -> Unit = { key, keyModifier ->
                val copy = selfReportKeyPresentation(key)
                SelfReportKeyButton(
                    label = copy.label,
                    supporting = copy.supporting,
                    tag = when (key) {
                        ReviewSelfReportKey.GOOD -> "review_key_good"
                        ReviewSelfReportKey.HARD -> "review_key_hard"
                        ReviewSelfReportKey.AGAIN -> "review_key_again"
                    },
                    selected = selected == key,
                    enabled = selfReportKeyEnabled(
                        key = key,
                        selected = selected,
                        enabled = enabled,
                        pendingSubmit = pendingSubmit,
                    ),
                    onClick = { onSelect(key) },
                    modifier = keyModifier,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Preserve readable explanations at large fonts instead of making three tall text columns.
                val needsFullRows = maxWidth < 80.dp * LocalDensity.current.fontScale * 3 + 16.dp
                if (needsFullRows) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewSelfReportKey.entries.forEach { renderKey(it, Modifier.fillMaxWidth()) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewSelfReportKey.entries.forEach { renderKey(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "按实际回忆情况选择。「卡顿」表示想起来了，但较费力。",
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
            )
        }
    }
}

/** 已落库的自评回执：明确写出学生按下的档位与写入的证据口径。 */
@Composable
internal fun ReviewSelfReportReceipt(
    recorded: RecordedSelfReport?,
    modifier: Modifier = Modifier,
) {
    val result = recorded ?: return
    Surface(color = SmartColors.JadeSoft, shape = RoundedCornerShape(16.dp)) {
        Text(
            text = selfReportRecordedMessage(key = result.key, evidenceReason = result.evidenceReason),
            style = MaterialTheme.typography.bodyMedium,
            color = SmartColors.JadeDark,
            modifier = modifier.fillMaxWidth().padding(14.dp).testTag("review_self_report_receipt"),
        )
    }
}

@Composable
private fun SelfReportKeyButton(
    label: String,
    supporting: String,
    tag: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 64.dp)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) SmartColors.JadeSoft else SmartColors.SubtleSurface,
            contentColor = SmartColors.Ink,
            disabledContainerColor = SmartColors.SubtleSurface,
            disabledContentColor = SmartColors.InkMuted,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SmartColors.JadeDark else SmartColors.Divider,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) SmartColors.JadeDark else SmartColors.Ink,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = SmartColors.InkSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 已保存答案/解析面板（PRD-05 无模型路径）。
 *
 * 只有 [visible]（已自评或已按既有 reveal 展开）时才显示内容；[storedAnswer] 为 null 表示
 * 调用方未接线（不显示任何东西）。不可信时如实说明，不伪造答案。
 */
@Composable
internal fun ReviewStoredAnswerPanel(
    storedAnswer: ReviewStoredAnswer?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    revealLabel: String = "查看已保存的答案与解析",
    onRequestReveal: (() -> Unit)? = null,
) {
    if (storedAnswer == null) return
    if (!storedAnswer.isTrusted) {
        WorkspacePanel {
            Column {
                Text(
                    text = "暂无已确认的答案。你仍可自评、跳过，或到讲题页寻求讲解。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartColors.InkSecondary,
                    modifier = modifier.testTag("review_stored_answer_unavailable"),
                )
                // 没有答案键不等于没有已存解析：有解析就如实展示，并标明它不是答案键。
                storedAnswer.explanationMarkdown
                    ?.takeIf { it.isNotBlank() }
                    ?.let { explanation ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "已保存的解析（答案尚未确认）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = SmartColors.InkSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        SafeMarkdownText(
                            markdown = explanation,
                            color = SmartColors.Ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
            }
        }
        return
    }
    if (!visible) {
        if (onRequestReveal != null) {
            TextButton(
                onClick = onRequestReveal,
                modifier = modifier.testTag("review_stored_answer_open"),
            ) {
                Text(revealLabel)
            }
        }
        return
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_stored_answer"),
        color = SmartColors.ContentSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "已保存的答案",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SmartColors.JadeDark,
            )
            Spacer(Modifier.height(6.dp))
            SafeMarkdownText(
                markdown = storedAnswer.answerMarkdown.orEmpty(),
                color = SmartColors.Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            storedAnswer.explanationMarkdown?.takeIf { it.isNotBlank() }?.let { explanation ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "解析",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartColors.InkSecondary,
                )
                Spacer(Modifier.height(4.dp))
                SafeMarkdownText(
                    markdown = explanation,
                    color = SmartColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 自评/撤销的状态行（失败可重试；已撤销给出明确提示）。 */
@Composable
internal fun ReviewSelfReportStatusLine(
    status: SelfReportStatus,
    undoStatus: SelfReportUndoStatus,
    modifier: Modifier = Modifier,
    undoReceipt: ReviewUndoReceipt? = null,
) {
    val text = selfReportStatusMessage(status, undoStatus, undoReceipt) ?: return
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status == SelfReportStatus.FAILED ||
            undoStatus == SelfReportUndoStatus.FAILED
        ) {
            SmartColors.ErrorText
        } else {
            SmartColors.InkSecondary
        },
        modifier = modifier.testTag("review_self_report_status"),
    )
}
