package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawFailureReason
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawPhase
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawRequestResult
import com.tingyun.smartmistakebook.core.domain.ExplicitRedrawState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-06 T3 详情页重绘区的**纯文案/状态映射**（JVM）。
 *
 * 这些函数是 UI 的唯一判定点：什么状态显示什么、失败原因怎么读、命令结果要不要额外提示。
 * Compose 用例只验证这些文字/标签确实渲染出来。
 */
class ExplicitRedrawUiPolicyTest {

    @Test
    fun idleHasNoStatusRow() {
        assertNull(explicitRedrawStatusText(ExplicitRedrawState.Idle))
    }

    @Test
    fun queuedAndRunningStatesTellTheTruth() {
        assertEquals(
            "已提交重绘，等待开始…",
            explicitRedrawStatusText(running(attemptCount = 0)),
        )
        val running = explicitRedrawStatusText(running(attemptCount = 2)).orEmpty()
        assertTrue(running.contains("第 2 次尝试"))
        assertFalse(running.contains("失败"))
    }

    @Test
    fun failureShowsItsReasonAndOnlyOffersRetryWithinTheBudget() {
        val retryable = explicitRedrawStatusText(
            failed(reason = ExplicitRedrawFailureReason.SOURCE_UNREADABLE, canRetry = true),
        ).orEmpty()
        assertTrue(retryable.contains("原图读不出来"))
        assertFalse(retryable.contains("重试次数已用完"))

        val exhausted = explicitRedrawStatusText(
            failed(reason = ExplicitRedrawFailureReason.REDRAW_FAILED, canRetry = false),
        ).orEmpty()
        assertTrue(exhausted.contains("生成没有成功"))
        assertTrue(exhausted.contains("重试次数已用完"))
    }

    @Test
    fun successAndSupersededAreDistinctOutcomes() {
        assertEquals(
            "已生成新的干净题面。",
            explicitRedrawStatusText(succeeded()),
        )
        assertEquals(
            "题面已更新，这次重绘没有执行。",
            explicitRedrawStatusText(
                ExplicitRedrawState(
                    phase = ExplicitRedrawPhase.SUPERSEDED,
                    taskId = "task-1",
                    attemptCount = 1,
                ),
            ),
        )
    }

    @Test
    fun everyFailureReasonHasReadableText() {
        assertEquals("原图读不出来", explicitRedrawFailureText(ExplicitRedrawFailureReason.SOURCE_UNREADABLE))
        assertEquals("生成没有成功", explicitRedrawFailureText(ExplicitRedrawFailureReason.REDRAW_FAILED))
        assertEquals("新图没能保存到这道题", explicitRedrawFailureText(ExplicitRedrawFailureReason.ATTACH_FAILED))
        assertEquals("未知原因", explicitRedrawFailureText(ExplicitRedrawFailureReason.UNKNOWN))
        assertEquals("未知原因", explicitRedrawFailureText(null))
    }

    @Test
    fun acceptedAndAlreadySettledNeedNoExtraNotice() {
        assertNull(explicitRedrawResultMessage(ExplicitRedrawRequestResult.Accepted(ExplicitRedrawState.Idle)))
        assertNull(
            explicitRedrawResultMessage(
                ExplicitRedrawRequestResult.AlreadySettled(ExplicitRedrawState.Idle),
            ),
        )
        assertNull(explicitRedrawResultMessage(ExplicitRedrawRequestResult.NotSupported))
    }

    @Test
    fun rejectionReasonsAreTruthfulAndDistinct() {
        assertTrue(
            explicitRedrawResultMessage(ExplicitRedrawRequestResult.NotCurrentRevision)
                .orEmpty()
                .contains("题面已更新"),
        )
        assertTrue(
            explicitRedrawResultMessage(ExplicitRedrawRequestResult.NoSource)
                .orEmpty()
                .contains("没有可用的原图"),
        )
        assertTrue(
            explicitRedrawResultMessage(ExplicitRedrawRequestResult.Unavailable)
                .orEmpty()
                .contains("模型设置"),
        )
        assertTrue(
            explicitRedrawResultMessage(ExplicitRedrawRequestResult.Rejected("UNSUPPORTED_ROLE"))
                .orEmpty()
                .contains("UNSUPPORTED_ROLE"),
        )
    }

    private fun running(attemptCount: Int) = ExplicitRedrawState(
        phase = ExplicitRedrawPhase.RUNNING,
        taskId = "task-1",
        attemptCount = attemptCount,
        updatedAtEpochMillis = 2_000,
    )

    private fun failed(
        reason: ExplicitRedrawFailureReason,
        canRetry: Boolean,
    ) = ExplicitRedrawState(
        phase = ExplicitRedrawPhase.FAILED,
        taskId = "task-1",
        attemptCount = 1,
        failureReason = reason,
        canRetry = canRetry,
        updatedAtEpochMillis = 2_000,
    )

    private fun succeeded() = ExplicitRedrawState(
        phase = ExplicitRedrawPhase.SUCCEEDED,
        taskId = "task-1",
        attemptCount = 1,
        updatedAtEpochMillis = 2_000,
    )
}
