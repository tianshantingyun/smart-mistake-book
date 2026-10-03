package com.tingyun.smartmistakebook.feature.capture

import com.tingyun.smartmistakebook.core.domain.CaptureEntryOrigin
import com.tingyun.smartmistakebook.core.domain.PendingCaptureItem
import com.tingyun.smartmistakebook.core.domain.PendingCaptureStage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L4 待处理列表的纯策略：8 态的如实文案、打开落点（草稿 / 讲题会话）与分类汇总。
 *
 * 这些用例是从已删除的错题本栏 `PendingCaptureInboxPolicyTest`（随 2f0b98a9 一起删除）
 * 迁到列表新家（录入界面）的同一组断言——列表换个落点，状态语义不许变。
 */
class CapturePendingCapturesPolicyTest {
    @Test
    fun everyPendingStageHasAStudentFacingActionLabel() {
        val labels = PendingCaptureStage.entries.associateWith(::pendingCaptureStageLabel)

        assertEquals(
            mapOf(
                PendingCaptureStage.TUTOR_SESSION_READY to "题目已保存，可以开始讲解",
                PendingCaptureStage.SOURCE_UNAVAILABLE to "原图暂时打不开，请重新拍摄",
                PendingCaptureStage.RECAPTURE_REQUIRED to "关键内容看不清，请重新拍摄",
                PendingCaptureStage.MODEL_WORKING to "正在整理题目，可稍后再来",
                PendingCaptureStage.RETRY_OR_MANUAL to "上次没有完成，点此继续",
                PendingCaptureStage.MANUAL_REVIEW_REQUIRED to "题面还没整理完整，点此继续",
                PendingCaptureStage.READY_TO_REVIEW to "题面已整理，可继续",
                PendingCaptureStage.READY_TO_CONTINUE to "已保存原图，点此继续",
            ),
            labels,
        )
    }

    @Test
    fun temporaryTutorQuestionOpensItsWaitingStateInsteadOfCorrection() {
        val target = pendingCaptureOpenTarget(
            pendingItem(
                stage = PendingCaptureStage.TUTOR_SESSION_READY,
                tutorSessionId = "session-1",
            ),
        )

        assertEquals(PendingCaptureOpenTarget.TutorSession("session-1"), target)
    }

    @Test
    fun ordinaryPendingItemResumesItsExistingDraft() {
        val target = pendingCaptureOpenTarget(
            pendingItem(stage = PendingCaptureStage.READY_TO_REVIEW),
        )

        assertEquals(PendingCaptureOpenTarget.Draft("draft-1"), target)
    }

    @Test
    fun summarySeparatesModelWorkFromStudentActionsAndActualProblems() {
        val summary = summarizePendingCaptures(
            listOf(
                pendingItem(PendingCaptureStage.MODEL_WORKING),
                pendingItem(PendingCaptureStage.READY_TO_REVIEW),
                pendingItem(PendingCaptureStage.TUTOR_SESSION_READY, "session-1"),
                pendingItem(PendingCaptureStage.RECAPTURE_REQUIRED),
            ),
        )

        assertEquals(
            PendingCaptureSummary(
                total = 4,
                modelWorking = 1,
                readyForStudent = 2,
                needsAttention = 1,
            ),
            summary,
        )
    }

    private fun pendingItem(
        stage: PendingCaptureStage,
        tutorSessionId: String? = null,
    ) = PendingCaptureItem(
        draftId = "draft-1",
        origin = CaptureEntryOrigin.TUTOR,
        subject = "数学",
        title = "导数与单调性",
        currentRevisionNumber = 1,
        updatedAtEpochMillis = 1L,
        stage = stage,
        tutorSessionId = tutorSessionId,
    )
}
