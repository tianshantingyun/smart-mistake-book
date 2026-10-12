package com.tingyun.smartmistakebook.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiSseTest {
    @Test
    fun concatenatesDeltaContentAndIgnoresDone() {
        val raw = """
            data: {"choices":[{"delta":{"content":"{\"ok\":"}}]}

            data: {"choices":[{"delta":{"content":"true}"}}]}

            data: [DONE]

        """.trimIndent()
        assertEquals(
            "{\"ok\":true}",
            OpenAiSse.eventDataBlocks(raw).mapNotNull(OpenAiSse::deltaContent).joinToString(""),
        )
        val reconstructed = OpenAiSse.reconstructedChatCompletion(raw)
        assertTrue(reconstructed.contains("content"))
        assertTrue(reconstructed.contains("choices"))
    }

    @Test
    fun blankStreamIsRejected() {
        assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion("data: [DONE]\n\n")
        }
    }

    @Test
    fun blankStreamIsStillInvalidResponseNotIncompleteOutput() {
        // 什么都没有的空白流维持既有语义：这不是"上游说了没写完"，而是"这次答复没法用"。
        val thrown = assertThrows(InvalidModelResponseException::class.java) {
            OpenAiSse.reconstructedChatCompletion(
                "data: {\"choices\":[{\"delta\":{\"reasoning\":\"想了很久\"}}]}\n\n" +
                    "data: [DONE]\n\n",
            )
        }

        // 精确到类：截断走的是另一个异常类型（IncompleteModelOutputException）。
        assertEquals(InvalidModelResponseException::class.java, thrown.javaClass)
    }

    @Test
    fun reasoningOnlyStreamCutOffByOutputLimitIsIncompleteOutput() {
        // KD-33 的形态：推理模型把输出预算烧在思考上，答案一字节没吐、终帧说 finish_reason=length。
        // 这是"这次没写完"（可重试），不是"内容不可用"。
        val raw = """
            data: {"choices":[{"delta":{"reasoning":"先看定义域"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"length"}]}

            data: [DONE]

        """.trimIndent()

        assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun contentFilteredStreamWithNoAnswerIsAlsoIncompleteOutput() {
        val raw = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"content_filter\"}]}\n\n" +
            "data: [DONE]\n\n"

        assertThrows(IncompleteModelOutputException::class.java) {
            OpenAiSse.reconstructedChatCompletion(raw)
        }
    }

    @Test
    fun truncatedStreamCarriesItsFinishReasonIntoTheReconstructedBody() {
        // 正文非空也可能被截断：重建体必须原样带上 finish_reason，终态解析才分得出
        // "模型写完了"与"模型没写完"（丢了它，截断又会落回 INVALID_RESPONSE）。
        val raw = """
            data: {"choices":[{"delta":{"content":"{\"ok\":"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"length"}]}

            data: [DONE]

        """.trimIndent()

        val reconstructed = OpenAiSse.reconstructedChatCompletion(raw)

        assertTrue(reconstructed.contains("\"finish_reason\":\"length\""))
    }

    @Test
    fun completedStreamKeepsNoTruncatingFinishReason() {
        val raw = """
            data: {"choices":[{"delta":{"content":"{\"ok\":true}"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()

        val reconstructed = OpenAiSse.reconstructedChatCompletion(raw)

        assertTrue(reconstructed.contains("\"finish_reason\":\"stop\""))
    }

    @Test
    fun reasoningTextSpellingIsRecognisedLikeTheOtherReasoningFields() {
        // opencode 客户端认的三种拼写之一：reasoning / reasoning_content / reasoning_text。
        assertEquals(
            "想一下",
            OpenAiSse.deltaReasoning("""{"choices":[{"delta":{"reasoning_text":"想一下"}}]}"""),
        )
    }
}
