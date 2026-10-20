package com.tingyun.smartmistakebook.core.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-04a 的降级门：协议不能把 SSE 工具增量聚合成终态时，声明了工具的回合必须显式
 * stream=false（非流式响应天然是完整信封），不能继续流式并把 tool_calls 静静丢掉。
 */
class ModelStreamSelectionTest {

    @Test
    fun aNativeToolRoundFallsBackToNonStreamingWhenTheProtocolCannotAggregateToolCalls() {
        assertFalse(
            shouldStreamModelResponse(
                supportsStreaming = true,
                usesSseSurface = true,
                nativeToolRound = true,
                protocolSupportsStreamedToolCalls = false,
            ),
        )
    }

    @Test
    fun aNativeToolRoundKeepsStreamingWhenTheProtocolAggregatesToolCalls() {
        assertTrue(
            shouldStreamModelResponse(
                supportsStreaming = true,
                usesSseSurface = true,
                nativeToolRound = true,
                protocolSupportsStreamedToolCalls = true,
            ),
        )
    }

    @Test
    fun nonToolRoundsKeepTheirExistingStreamingPolicy() {
        assertTrue(
            shouldStreamModelResponse(
                supportsStreaming = true,
                usesSseSurface = true,
                nativeToolRound = false,
                protocolSupportsStreamedToolCalls = false,
            ),
        )
    }

    @Test
    fun streamingStillRequiresTheProviderAndTheSseSurface() {
        assertFalse(
            shouldStreamModelResponse(
                supportsStreaming = false,
                usesSseSurface = true,
                nativeToolRound = false,
                protocolSupportsStreamedToolCalls = true,
            ),
        )
        assertFalse(
            shouldStreamModelResponse(
                supportsStreaming = true,
                usesSseSurface = false,
                nativeToolRound = false,
                protocolSupportsStreamedToolCalls = true,
            ),
        )
    }
}
