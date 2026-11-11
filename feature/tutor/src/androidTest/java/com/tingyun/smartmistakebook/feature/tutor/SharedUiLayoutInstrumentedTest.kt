package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tingyun.smartmistakebook.core.ui.ClaudeButton
import com.tingyun.smartmistakebook.core.ui.ClaudeSettingsRow
import com.tingyun.smartmistakebook.core.ui.SmartColors
import com.tingyun.smartmistakebook.core.ui.WorkspaceChoiceRow
import com.tingyun.smartmistakebook.core.ui.WorkspacePageHeader
import com.tingyun.smartmistakebook.core.ui.WorkspacePanel
import com.tingyun.smartmistakebook.core.ui.WorkspaceSheet
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
    @Test fun editorialSettingsAndSheetShareTheSameVisualHierarchy() {
        var minutes = "20"
        compose.setContent {
            SmartMistakeBookTheme {
                var open by remember { mutableStateOf(false) }
                var selected by remember { mutableStateOf("20") }
                Column(Modifier.fillMaxSize().background(SmartColors.Paper).padding(horizontal = 20.dp)) {
                    WorkspacePageHeader("我的", subtitle = "学习安排与偏好")
                    SectionHeader("学习设置")
                    Spacer(Modifier.height(12.dp))
                    WorkspacePanel(containerColor = SmartColors.ContentSurface,
                        contentPadding = PaddingValues(vertical = 6.dp)) {
                        ClaudeSettingsRow("复习安排", "每天预计 20 分钟", Icons.Outlined.Schedule,
                            onClick = { open = true }, modifier = Modifier.testTag("schedule"))
                        ClaudeSettingsRow("模型设置", "管理讲题与绘图能力", Icons.Outlined.Tune,
                            onClick = {}, iconContainerColor = SmartColors.LearningSurface,
                            iconTint = SmartColors.LearningAccent, showDivider = false)
                    }
                }
                if (open) WorkspaceSheet("复习安排", onDismiss = { open = false }, icon = Icons.Outlined.Schedule,
                    subtitle = "设置每天预计用于复习的时间。", actions = {
                        ClaudeButton({ minutes = selected; open = false }, Modifier.fillMaxWidth().testTag("save_schedule")) {
                            Text("保存安排")
                        }
                    }) {
                    WorkspaceChoiceRow("20 分钟", "适合日常巩固", selected == "20", { selected = "20" })
                    WorkspaceChoiceRow("30 分钟", "适合集中复习", selected == "30", { selected = "30" },
                        Modifier.testTag("thirty_minutes"))
                }
            }
        }
        compose.onNodeWithTag("schedule").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.waitForIdle()
        UiAuditEvidence.capture("editorial-settings-1111")
        compose.onNodeWithTag("schedule").performClick()
        compose.onNodeWithTag("thirty_minutes").performClick()
        compose.waitForIdle()
        UiAuditEvidence.capture("editorial-settings-sheet-1111")
        compose.onNodeWithTag("save_schedule").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals("30", minutes) }
        compose.onNodeWithTag("schedule").assertIsDisplayed()
    }

}
