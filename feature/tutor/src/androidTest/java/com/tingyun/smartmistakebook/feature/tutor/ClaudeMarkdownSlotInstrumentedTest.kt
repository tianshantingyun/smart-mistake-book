package com.tingyun.smartmistakebook.feature.tutor

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.ui.AiReplyRichMarkdown
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaudeMarkdownSlotInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun render(markdown: String) {
        compose.setContent { SmartMistakeBookTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { AiReplyRichMarkdown(markdown) }
        } }
    }

    private fun assertBlocks(count: Int) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("claude_code_block").fetchSemanticsNodes().size == count
        }
        compose.onAllNodesWithTag("claude_code_block").assertCountEquals(count)
    }

    private fun clipboard(): String? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString()
    }

    @Test fun fencedCodeShowsItsLanguageAndCopiesTheUntrimmedParserLiteral() {
        render("```kotlin\n  val answer = 42  \n\n```\n")
        assertBlocks(1)
        compose.onNodeWithText("kotlin").assertIsDisplayed()
        compose.onNodeWithText("复制代码").performClick()
        compose.onNodeWithText("已复制").assertIsDisplayed()
        compose.runOnIdle { assertEquals("  val answer = 42  \n\n", clipboard()) }
    }

    @Test fun listAndQuoteCodeUseTheSameToolbarWithoutASecondParser() {
        render("- 列表\n\n  ```js\n  first()\n  ```\n\n> ```python\n> second()\n> ```\n")
        assertBlocks(2)
        compose.onNodeWithText("js").assertIsDisplayed()
        compose.onNodeWithText("python").assertIsDisplayed()
        compose.onAllNodesWithText("复制代码")[0].performClick()
        compose.runOnIdle { assertEquals("first()\n", clipboard()) }
        compose.onAllNodesWithText("复制代码")[0].performClick()
        compose.runOnIdle { assertEquals("second()\n", clipboard()) }
    }

    @Test fun headingsListsAndTableCellsKeepTheOriginalRichTextPath() {
        render("# 标题\n\n- 列表项\n\n| 名称 | 值 |\n| --- | --- |\n| 面积 | 42 |\n\n```text\nplain\n```\n")
        assertBlocks(1)
        compose.onNodeWithText("标题").assertIsDisplayed()
        compose.onNodeWithText("列表项").assertIsDisplayed()
        compose.onNodeWithText("面积").assertIsDisplayed()
    }

    @Test fun htmlAndRemoteImageStillFallBackBeforeTheRichTextAdapter() {
        render("<script>alert(1)</script>\n\n![远程](https://example.invalid/image.png)\n\n```js\nunsafe()\n```\n")
        compose.waitForIdle()
        compose.onAllNodesWithTag("claude_code_block").assertCountEquals(0)
    }
}
