package com.tingyun.smartmistakebook.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-57：分享授权的**有效期**与"过期理由"的 JVM 断言。
 *
 * 这些用例钉住两件事：
 * - 过期由"收下分享的时刻 + 上限"派生，恢复出来的非空请求不会永不过期；
 * - 授权过期是独立理由，不能与 UNREADABLE / IMPORT_FAILED 混成一句。
 */
class CaptureShareAuthorizationPolicyTest {
    private val authorizedAt = 1_700_000_000_000L

    private fun expired(
        nowEpochMillis: Long,
        authorizedAtEpochMillis: Long = authorizedAt,
        timeoutMillis: Long,
    ): Boolean = captureShareAuthorizationExpired(
        nowEpochMillis = nowEpochMillis,
        authorizedAtEpochMillis = authorizedAtEpochMillis,
        timeoutMillis = timeoutMillis,
    )

    @Test
    fun `authorization is live inside the window and at the last millisecond`() {
        val timeout = 60_000L
        assertFalse(expired(nowEpochMillis = authorizedAt, timeoutMillis = timeout))
        assertFalse(
            expired(
                nowEpochMillis = authorizedAt + timeout - 1,
                timeoutMillis = timeout,
            ),
        )
    }

    @Test
    fun `restored share expires at the timeout instead of living forever`() {
        val timeout = CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS
        // 进程重建：rememberSaveable 把请求与 authorizedAt 一起恢复成非空；
        // 时间继续走，到点必须判过期。
        assertTrue(
            expired(
                nowEpochMillis = authorizedAt + timeout,
                timeoutMillis = timeout,
            ),
        )
        assertTrue(
            expired(
                nowEpochMillis = authorizedAt + timeout + 1,
                timeoutMillis = timeout,
            ),
        )
    }

    @Test
    fun `clock rollback does not expire an otherwise live share`() {
        assertFalse(
            expired(
                nowEpochMillis = authorizedAt - 5_000L,
                timeoutMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `non positive timeout expires immediately`() {
        assertTrue(expired(nowEpochMillis = authorizedAt, timeoutMillis = 0L))
        assertTrue(expired(nowEpochMillis = authorizedAt, timeoutMillis = -1L))
    }

    @Test
    fun `expired failure is named apart from unreadable and import failures`() {
        val expiredMessage = captureShareRejectionMessage(
            CaptureShareRejection.AUTHORIZATION_EXPIRED,
        )
        assertEquals(CAPTURE_SHARE_EXPIRED_MESSAGE, expiredMessage)
        assertNotEquals(
            expiredMessage,
            captureShareRejectionMessage(CaptureShareRejection.UNREADABLE),
        )
        assertNotEquals(
            expiredMessage,
            captureShareRejectionMessage(CaptureShareRejection.IMPORT_FAILED),
        )
        assertTrue(expiredMessage.contains("失效"))
    }

    @Test
    fun `authorization expiry is not retryable`() {
        assertFalse(
            captureShareRejectionIsRetryable(CaptureShareRejection.AUTHORIZATION_EXPIRED),
        )
    }
}
