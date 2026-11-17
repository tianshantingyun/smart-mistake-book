package com.tingyun.smartmistakebook.core.data.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderRetryTest {
    @Test
    fun onlyRateLimitAndServerErrorsAreRetryableStatuses() {
        val policy = ModelProviderRetry()
        assertTrue(policy.isRetryableStatus(429))
        assertTrue(policy.isRetryableStatus(500))
        assertTrue(policy.isRetryableStatus(503))
        listOf(400, 401, 403, 404, 422).forEach {
            assertFalse("$it must not be retried", policy.isRetryableStatus(it))
        }
    }

    @Test
    fun retryAfterWinsAndIsCappedByThePolicy() {
        val policy = ModelProviderRetry()
        assertEquals(250L, policy.delayMillis(attempt = 1, retryAfterMillis = 250L))
        assertEquals(
            ModelProviderRetry.DEFAULT_MAX_DELAY_MILLIS,
            policy.delayMillis(attempt = 1, retryAfterMillis = 600_000L),
        )
        assertEquals(0L, policy.delayMillis(attempt = 1, retryAfterMillis = 0L))
    }

    @Test
    fun exponentialBackoffGrowsAndJitterStaysWithinHalfToFullDelay() {
        val policy = ModelProviderRetry(random = Random(7))
        val attempt1 = policy.delayMillis(attempt = 1)
        val attempt2 = policy.delayMillis(attempt = 2)
        assertTrue(attempt1 in 250L..500L)
        assertTrue(attempt2 in 500L..1_000L)
        repeat(50) {
            assertTrue(policy.delayMillis(attempt = 1) in 250L..500L)
        }
        assertTrue(
            policy.delayMillis(attempt = 40) in
                (ModelProviderRetry.DEFAULT_MAX_DELAY_MILLIS / 2)..ModelProviderRetry.DEFAULT_MAX_DELAY_MILLIS,
        )
    }

    @Test
    fun aSingleAttemptPolicyDisablesRetrying() {
        assertEquals(1, ModelProviderRetry(maxAttempts = 1).maxAttempts)
    }
}
