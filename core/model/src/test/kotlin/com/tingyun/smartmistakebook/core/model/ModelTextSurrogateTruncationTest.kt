package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent.Progress

/**
 * KD-38 剩余项：**按字符预算截断时不切代理对**。
 *
 * 背景（`docs/known-defects.md` §KD-38「未达 / 剩余」）：`String.take(n)` 以 UTF-16 码元计数，
 * 边界落在一个补充平面字符（emoji 等成对代理）中间时会留下孤立高代理；而 `requireSafeModelText`
 * 只拒 ISO 控制字符与双向控制符，**不拒孤立代理**，所以半个字符会一路进到快照/流式事件里，
 * 显示为「�」。同批 `DebugModelFailureCapture.truncateToUtf8Bytes` 为同一风险写了字节语义的保护，
 * 这里补的是字符语义那一侧（`String.takeBudgetChars`）。
 *
 * 用例口径：**旧行为必须先被证明有问题**（`take` 确实留下孤立代理），新行为才算修好——
 * 否则断言可能恒真（见 `theOldTakeBehaviourWouldHaveProducedALoneSurrogate`）。
 */
class ModelTextSurrogateTruncationTest {

    /** U+1F600 GRINNING FACE：`\uD83D` 高代理 + `\uDE00` 低代理。 */
    private val emoji = "\uD83D\uDE00"

    @Test
    fun aBudgetBoundaryInsideAnEmojiDropsTheWholePairInsteadOfLeavingHalfACharacter() {
        // 索引 9 = 高代理、索引 10 = 低代理：预算 10 的边界正落在这对中间。
        val text = "a".repeat(9) + emoji + "b"

        val truncated = text.takeBudgetChars(10)

        assertEquals(9, truncated.length)
        assertEquals("a".repeat(9), truncated)
        assertNoLoneSurrogate(truncated)
    }

    @Test
    fun aPairEndingExactlyAtTheBudgetBoundaryIsKeptWhole() {
        // 索引 9/10 是成对代理，最后一个保留的码元（索引 10）是低代理：整对都在预算里，不缩。
        val text = "a".repeat(9) + emoji + "bcd"

        val truncated = text.takeBudgetChars(11)

        assertEquals(11, truncated.length)
        assertEquals("a".repeat(9) + emoji, truncated)
        assertNoLoneSurrogate(truncated)
    }

    @Test
    fun aBoundaryJustBeforeAnEmojiUsesTheFullBudget() {
        // 索引 10 起才是 emoji：边界正好在它之前，没有可切的对，预算应被用满。
        val text = "a".repeat(10) + emoji + "b"

        val truncated = text.takeBudgetChars(10)

        assertEquals("a".repeat(10), truncated)
        assertEquals(10, truncated.length)
    }

    @Test
    fun textWithinTheBudgetIsReturnedUnchanged() {
        val short = "abc" + emoji
        assertEquals(short, short.takeBudgetChars(100))

        // 输入本身已带孤立代理时也原样返回：这一层只保证**不制造**孤立代理，不做修复
        // （修复会掩盖上游切坏的事实，且没有可判定的正确补法）。
        val alreadyBroken = "abc\uD83D"
        assertEquals(alreadyBroken, alreadyBroken.takeBudgetChars(100))
    }

    @Test
    fun anAlreadyLoneSurrogateAtTheBoundaryIsLeftAsIs() {
        // 索引 9 已是孤立高代理（后面是 'x' 而不是低代理）——它不是被这次截断切出来的，
        // 保持与 `take(10)` 相同的输出，不追加"删半个字符"的判断。
        val alreadyBroken = "a".repeat(9) + "\uD83D" + "xyz"

        val truncated = alreadyBroken.takeBudgetChars(10)

        assertEquals(alreadyBroken.take(10), truncated)
        assertEquals(10, truncated.length)
    }

    @Test
    fun zeroOrNegativeBudgetYieldsEmptyText() {
        val text = emoji + "abc"
        assertEquals("", text.takeBudgetChars(0))
        assertEquals("", text.takeBudgetChars(-1))
    }

    @Test
    fun budgetOfOneRefusesToKeepHalfAPair() {
        // 1 个码元装不下成对代理：截到边界前 = 空串（宁可少留，也不留半个字符）。
        assertEquals("", emoji.takeBudgetChars(1))
        assertEquals("a", "a$emoji".takeBudgetChars(1))
        assertEquals(emoji, emoji.takeBudgetChars(2))
    }

    @Test
    fun everyBudgetAcrossMixedTextStaysWithinBudgetAndIsAPrefix() {
        val text = buildString {
            repeat(6) {
                append("a")
                append(emoji)
                append("b中")
                append(emoji)
            }
        }

        (0..text.length).forEach { budget ->
            val truncated = text.takeBudgetChars(budget)
            assertTrue("budget=$budget：结果超过预算", truncated.length <= budget)
            assertTrue("budget=$budget：结果不是原串前缀", text.startsWith(truncated))
            assertNoLoneSurrogate(truncated, "budget=$budget")
        }
    }

    @Test
    fun theOldTakeBehaviourWouldHaveProducedALoneSurrogate() {
        // 这条是**测试的测试**：证明下面的断言检查器不恒真，且旧行为（take）确实会切出孤立代理。
        val text = "答".repeat(MAX_PROGRESS_MESSAGE_CHARS - 1) + emoji + "尾"

        val oldBehaviour = text.take(MAX_PROGRESS_MESSAGE_CHARS)
        assertEquals(MAX_PROGRESS_MESSAGE_CHARS, oldBehaviour.length)
        assertTrue("旧行为应在末尾留下孤立高代理", oldBehaviour.last().isHighSurrogate())
        assertThrows(AssertionError::class.java) { assertNoLoneSurrogate(oldBehaviour) }
    }

    @Test
    fun aStreamingPreviewThatWouldHaveSplitAnEmojiStaysWhole() {
        val text = "答".repeat(MAX_PROGRESS_MESSAGE_CHARS - 1) + emoji + "尾"

        val event = Progress.of(text)

        // 退一个码元（少留），不越界：2000 的硬上界仍然成立，且没有半个字符。
        assertEquals(MAX_PROGRESS_MESSAGE_CHARS - 1, event.userMessage.length)
        assertNoLoneSurrogate(event.userMessage)
        assertEquals(text.substring(0, MAX_PROGRESS_MESSAGE_CHARS - 1), event.userMessage)
    }

    @Test
    fun aPreviewWhoseBoundaryLandsBeforeAnEmojiStillFillsTheBudgetExactly() {
        // 反向钉子：不该"一律少留一个码元"——边界没落在对中间时必须用满预算（BMP 文本照旧）。
        val text = "答".repeat(MAX_PROGRESS_MESSAGE_CHARS) + emoji + "尾"

        val event = Progress.of(text)

        assertEquals(MAX_PROGRESS_MESSAGE_CHARS, event.userMessage.length)
        assertEquals(text.take(MAX_PROGRESS_MESSAGE_CHARS), event.userMessage)
        assertNoLoneSurrogate(event.userMessage)
    }

    /**
     * 断言 [value] 里没有任何孤立代理：每个高代理后面必须是低代理，每个低代理前面必须是高代理。
     * 这条检查器本身由 [theOldTakeBehaviourWouldHaveProducedALoneSurrogate] 钉住非恒真。
     */
    private fun assertNoLoneSurrogate(value: String, label: String = "截断结果") {
        value.forEachIndexed { index, char ->
            if (char.isHighSurrogate()) {
                assertTrue(
                    "$label：索引 $index 的孤立高代理后面不是低代理",
                    index + 1 < value.length && value[index + 1].isLowSurrogate(),
                )
            } else if (char.isLowSurrogate()) {
                assertTrue(
                    "$label：索引 $index 的孤立低代理前面不是高代理",
                    index > 0 && value[index - 1].isHighSurrogate(),
                )
            }
        }
    }
}
