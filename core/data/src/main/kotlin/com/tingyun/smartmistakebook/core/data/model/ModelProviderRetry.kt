package com.tingyun.smartmistakebook.core.data.model

import kotlin.random.Random

/**
 * Provider 级瞬时失败重试（与 OpenAI Agents SDK `run_internal/model_retry.py` 同构）：
 * 429/5xx 与连接类 `IOException` 在**本轮尚未产出任何增量**时按指数退避 + 抖动重发。
 *
 * 它消灭的具体失败：一次 429、一次 503、一次连接被重置，此前直接把整轮落成
 * RETRYABLE_FAILURE，学生必须自己再点一次重试；而这些恰恰是"等半秒再发就好了"的形态。
 *
 * 红线：一旦有增量到达（正文/思考/工具调用），**绝不重发**——重发会把已经流到屏幕上的
 * 半截回答变成重复内容，也会重复计费。这条由 [DeltaPresence] 在执行层保证，本类只给策略。
 */
internal class ModelProviderRetry(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    private val random: Random = Random.Default,
) {
    init {
        require(maxAttempts >= 1) { "A provider retry policy needs at least one attempt" }
        require(baseDelayMillis >= 0) { "A retry base delay cannot be negative" }
        require(maxDelayMillis >= baseDelayMillis) { "A retry cap cannot be below its base delay" }
    }

    fun isRetryableStatus(statusCode: Int): Boolean = statusCode == HTTP_TOO_MANY_REQUESTS || statusCode in 500..599

    /**
     * 第 [attempt] 次失败后的等待。服务端给了 `Retry-After` 就按它（仍受 [maxDelayMillis] 封顶，
     * 防止一条任务被超长退避拖死）；否则指数增长并取等抖动——同刻重发风暴比固定退避更容易
     * 再次撞上同一个限流窗口。
     */
    fun delayMillis(attempt: Int, retryAfterMillis: Long? = null): Long {
        require(attempt >= 1) { "Retry attempt numbers start at 1" }
        if (retryAfterMillis != null) return retryAfterMillis.coerceIn(0L, maxDelayMillis)
        val exponential = (baseDelayMillis shl (attempt - 1).coerceAtMost(20)).coerceAtMost(maxDelayMillis)
        if (exponential <= 0L) return 0L
        val half = exponential / 2
        return half + random.nextLong(exponential - half + 1)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BASE_DELAY_MILLIS = 500L
        const val DEFAULT_MAX_DELAY_MILLIS = 8_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429

        val DEFAULT = ModelProviderRetry()
    }
}

/**
 * 记录"这条流上是否已经有任何内容到达"。重试包装器用它决定还能不能重发：
 * 增量一旦出现过，重发就不再是"重试一次请求"，而是"把同一段回答再说一遍"。
 */
internal class DeltaPresence {
    private val arrived = java.util.concurrent.atomic.AtomicBoolean(false)

    val any: Boolean get() = arrived.get()

    fun wrap(callback: ((String) -> Unit)?): ((String) -> Unit)? =
        callback?.let { outer ->
            { delta ->
                if (delta.isNotEmpty()) arrived.set(true)
                outer(delta)
            }
        }

    fun wrapToolCall(callback: (() -> Unit)?): (() -> Unit)? =
        callback?.let { outer ->
            {
                arrived.set(true)
                outer()
            }
        }
}
