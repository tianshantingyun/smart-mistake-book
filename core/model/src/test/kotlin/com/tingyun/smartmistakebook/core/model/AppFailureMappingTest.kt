package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFailureMappingTest {
    @Test
    fun modelFailureCodesMapToStableUserFacingCodes() {
        assertEquals(
            AppFailureCode.PROVIDER_NOT_CONFIGURED,
            ModelFailureCode.MODEL_NOT_CONFIGURED.toAppFailureCode(),
        )
        assertEquals(
            AppFailureCode.EGRESS_CONSENT_REQUIRED,
            ModelFailureCode.EGRESS_AUTHORIZATION_REQUIRED.toAppFailureCode(),
        )
        assertEquals(
            AppFailureCode.EGRESS_LEASE_EXPIRED,
            ModelFailureCode.EGRESS_AUTHORIZATION_INVALID.toAppFailureCode(),
        )
        assertEquals(
            AppFailureCode.PROVIDER_CAPABILITY_MISMATCH,
            ModelFailureCode.PROVIDER_CAPABILITY_MISSING.toAppFailureCode(),
        )
        assertEquals(
            AppFailureCode.NETWORK_UNAVAILABLE,
            ModelFailureCode.TIMEOUT.toAppFailureCode(),
        )
    }

    @Test
    fun appFailureNeverLeaksRawExceptions() {
        val failure = appFailure(
            code = AppFailureCode.NETWORK_UNAVAILABLE,
            title = "暂时连不上模型",
            message = "这次讲解没有发送成功，题图和问题已保留。",
            dataPreserved = true,
            retryability = Retryability.RETRYABLE,
            primaryAction = ActionType.RETRY,
            secondaryAction = ActionType.CONTINUE,
        )

        assertTrue(failure.dataPreserved)
        assertEquals(ActionType.RETRY, failure.primaryActionKind)
        assertEquals(ActionType.CONTINUE, failure.actions[1].actionType)
        assertFalse(failure.message.contains("Exception"))
        assertFalse(failure.message.contains(" at "))
    }

    @Test
    fun truncatedModelOutputIsRetryableWhileAnUnusableShapeIsNot() {
        // 两者在用户侧同属"这次输出不能用"（同一个 AppFailureCode），但重发判决不同：
        // 截断是这一次的（finish_reason = length / content_filter），重发通常会好；
        // 形状错误重发会逐字复现，不给重试出口。
        val truncated = ModelTaskFailure(
            code = ModelFailureCode.INCOMPLETE_OUTPUT,
            message = "模型这次没写完，可以重试",
            retryable = true,
        ).toAppFailure()
        val invalid = ModelTaskFailure(
            code = ModelFailureCode.INVALID_RESPONSE,
            message = "模型返回的内容无法使用。",
            retryable = false,
        ).toAppFailure()

        assertEquals(AppFailureCode.MODEL_OUTPUT_INVALID, truncated.code)
        assertEquals("模型这次没写完", truncated.title)
        assertEquals(ActionType.RETRY, truncated.primaryActionKind)
        assertTrue(truncated.retryable)
        assertEquals(AppFailureCode.MODEL_OUTPUT_INVALID, invalid.code)
        assertFalse(invalid.retryable)
    }

    @Test
    fun modelTaskFailureMapsRetryabilityToRecoveryAction() {
        val retryable = ModelTaskFailure(
            code = ModelFailureCode.NETWORK_UNAVAILABLE,
            message = "服务暂时不可用，题图已保留。",
            retryable = true,
        ).toAppFailure()
        val permanent = ModelTaskFailure(
            code = ModelFailureCode.INVALID_RESPONSE,
            message = "模型返回的内容无法使用。",
            retryable = false,
        ).toAppFailure()

        assertEquals(AppFailureCode.NETWORK_UNAVAILABLE, retryable.code)
        assertEquals(ActionType.RETRY, retryable.primaryActionKind)
        assertEquals(AppFailureCode.MODEL_OUTPUT_INVALID, permanent.code)
        assertEquals(null, permanent.primaryAction)
        assertTrue(retryable.dataPreserved)
    }
}
