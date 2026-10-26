package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.InlineActionButton
import com.tingyun.smartmistakebook.core.ui.SectionHeader
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.core.ui.ThinkingCollapsibleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedUiLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextKeepsSectionActionsReachable() {
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SmartMistakeBookTheme {
                    Column(Modifier.width(320.dp)) {
                        SectionHeader("当前学习情况与待完成任务", action = {
                            InlineActionButton("查看全部记录", { clicks++ }, Modifier.testTag("section_action"))
                        })
                        Text("下面的学习内容", Modifier.testTag("next_content"))
                    }
                }
            }
        }
        compose.onNodeWithText("当前学习情况与待完成任务").assertIsDisplayed()
        compose.onNodeWithTag("section_action").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        val action = compose.onNodeWithTag("section_action").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("next_content").fetchSemanticsNode().boundsInRoot
        assertTrue("The following content must not overlap the action", next.top >= action.bottom)
        UiAuditEvidence.capture("shared-ui-large-text")
    }

    @Test fun thinkingDetailsKeepAnAccessibleToggle() {
        compose.setContent {
            SmartMistakeBookTheme { ThinkingCollapsibleCard("先确认题目条件，再选择合适的公式。") }
        }
        compose.onNodeWithText("已思考").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("先确认题目条件，再选择合适的公式。").assertIsDisplayed()
        UiAuditEvidence.capture("thinking-readable")
    }
}
