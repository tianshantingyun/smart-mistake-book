package com.tingyun.smartmistakebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tingyun.smartmistakebook.core.data.study.ErrorCauseReviewService
import com.tingyun.smartmistakebook.core.data.study.ErrorCauseReviewWriteResult
import com.tingyun.smartmistakebook.core.data.study.RoomBackedStudyExperienceRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.model.ErrorCauseScope
import com.tingyun.smartmistakebook.feature.library.ErrorCauseActiveTag
import com.tingyun.smartmistakebook.feature.library.ErrorCauseCandidateItem
import com.tingyun.smartmistakebook.feature.library.ErrorCausePanelState
import com.tingyun.smartmistakebook.feature.library.MistakeErrorCausePanel
import kotlinx.coroutines.launch

/**
 * 批次 50 / 5c：错题详情页的错因候选面板装配（app 层）。
 *
 * **它消灭的具体失败**：UI 直接拼 `ErrorCauseScope` 时把历史版本当当前版本——详情页可以切到
 * 旧修订只读，若面板仍用"上一版"的 revision id 去查，学生确认的候选会挂到看不见的版本上。
 * 这里的作用域只由**当前展示的** `identity` 重新构造，并随 `identity` 变化重读。
 *
 * **写动作只有两条**：确认（人的选择 → `USER_CONFIRMED`）与撤销（撤回行 + VOIDED）。
 * 模型自动落账不经本面板；门是否关闭由数据层 `autoBookingClosed` 如实带出。
 */
@Composable
internal fun MistakeErrorCausePanelHost(
    service: ErrorCauseReviewService,
    identity: MistakeDetailIdentity,
    modifier: Modifier = Modifier,
) {
    val scope = remember(identity) {
        ErrorCauseScope(
            learnerId = RoomBackedStudyExperienceRepository.DEFAULT_LEARNER_ID,
            practiceUnitId = identity.practiceUnitId,
            problemRevisionId = identity.problemRevisionId,
        )
    }
    var state by remember(scope) { mutableStateOf(ErrorCausePanelState()) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun reload(message: String? = null) {
        state = try {
            val snapshot = service.read(scope)
            ErrorCausePanelState(
                active = snapshot.active?.let {
                    ErrorCauseActiveTag(
                        bindingId = it.bindingId,
                        cause = it.cause,
                        detail = it.detail,
                        acceptanceSource = it.acceptanceSource,
                        confidence = it.confidence,
                    )
                },
                candidates = snapshot.candidates.map {
                    ErrorCauseCandidateItem(
                        observationId = it.observationId,
                        cause = it.cause,
                        detail = it.detail,
                        confidence = it.confidence,
                        channel = it.channel,
                        rationaleMarkdown = it.rationaleMarkdown,
                        observedAtEpochMillis = it.observedAtEpochMillis,
                        evidenceCount = it.evidenceRefs.size,
                    )
                },
                autoBookingClosed = snapshot.autoBookingClosed,
                message = message,
            )
        } catch (_: RuntimeException) {
            // 读不出来 ≠ 没有候选：保留 unavailable 态，不落到"还没有候选"。
            ErrorCausePanelState(unavailable = true)
        }
    }

    LaunchedEffect(scope) { reload() }

    MistakeErrorCausePanel(
        state = state,
        onConfirm = { observationId ->
            coroutineScope.launch {
                state = state.copy(busy = true, message = null)
                val result = runCatching {
                    service.confirmCandidate(
                        scope = scope,
                        observationId = observationId,
                        decidedAtEpochMillis = System.currentTimeMillis(),
                    )
                }.getOrNull()
                reload(message = errorCauseWriteMessage(result, ErrorCauseAction.CONFIRM))
            }
        },
        onUndo = { bindingId ->
            coroutineScope.launch {
                state = state.copy(busy = true, message = null)
                val now = System.currentTimeMillis()
                val result = runCatching {
                    service.undoActiveTag(
                        scope = scope,
                        bindingId = bindingId,
                        observedAtEpochMillis = now,
                        decidedAtEpochMillis = now,
                    )
                }.getOrNull()
                reload(message = errorCauseWriteMessage(result, ErrorCauseAction.UNDO))
            }
        },
        modifier = modifier,
    )
}

/** 面板上的两个写动作；提示文案必须区分，否则撤销后会显示"已记下"。 */
internal enum class ErrorCauseAction {
    CONFIRM,
    UNDO,
}

/**
 * 写结果的人员可读提示；`null` 表示"写入失败但不是账本拒绝"，用统一的一句。
 *
 * **为什么要区分动作**：设备走查发现撤销成功后仍显示"已记下"——同一个 APPLIED 文案套在
 * 两个相反动作上，学生会以为自己刚确认了一条。
 */
internal fun errorCauseWriteMessage(
    result: ErrorCauseReviewWriteResult?,
    action: ErrorCauseAction,
): String = when (result) {
    ErrorCauseReviewWriteResult.APPLIED -> when (action) {
        ErrorCauseAction.CONFIRM -> "已记下。它不会改变这次作答的对错，也不影响掌握度。"
        ErrorCauseAction.UNDO -> "已撤销这条候选。历史记录仍然保留，这次作答的对错不变。"
    }
    ErrorCauseReviewWriteResult.CONFLICT -> "这次没有写入：这道题已经有别的错因记录。列表已按当前记录刷新。"
    ErrorCauseReviewWriteResult.CANDIDATE_NOT_FOUND -> when (action) {
        ErrorCauseAction.CONFIRM -> "这条候选已经不在列表里了，列表已刷新。"
        ErrorCauseAction.UNDO -> "这条候选已经不存在或已被撤销。"
    }
    ErrorCauseReviewWriteResult.NOT_FOUND -> "这条记录已经不存在或已被撤销。"
    null -> "这次没有写入。可以稍后再试一次。"
}
