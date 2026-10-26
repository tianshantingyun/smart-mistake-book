package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.ClaudeAlertDialog
import com.tingyun.smartmistakebook.core.ui.ClaudeSegment
import com.tingyun.smartmistakebook.core.ui.ClaudeSegmentedControl
import com.tingyun.smartmistakebook.core.ui.ClaudeSplitPane
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton
import com.tingyun.smartmistakebook.core.ui.ClaudeTextField
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaudeControlsExpansionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun persistentFieldLabelDoesNotReplaceTheEditableTextOrDispatchChangesOnOpen() {
        val edits = mutableListOf<String>()
        compose.setContent {
            SmartMistakeBookTheme {
                var draft by remember { mutableStateOf("") }
                ClaudeTextField(draft, { draft = it; edits += it },
                    Modifier.fillMaxWidth().testTag("field"), label = { Text("题目标题") })
            }
        }
        compose.runOnIdle { assertTrue(edits.isEmpty()) }
        compose.onNodeWithTag("field").performTextInput("新的草稿🧪")
        compose.onNodeWithText("题目标题").assertIsDisplayed()
        compose.runOnIdle { assertEquals("新的草稿🧪", edits.last()) }
    }

    @Test fun longDialogKeepsItsRealConfirmAndCancelActionsReachable() {
        var confirms = 0
        var cancels = 0
        compose.setContent {
            SmartMistakeBookTheme {
                ClaudeAlertDialog(onDismissRequest = { cancels++ },
                    title = { Text("确认这次操作") },
                    text = { Text((1..80).joinToString("\n") { "第 $it 条需要阅读的内容" }) },
                    confirmButton = { ClaudeTextButton({ confirms++ }, Modifier.testTag("confirm")) { Text("确定") } },
                    dismissButton = { ClaudeTextButton({ cancels++ }, Modifier.testTag("cancel")) { Text("取消") } })
            }
        }
        compose.onNodeWithTag("confirm").assertIsDisplayed().performClick()
        compose.onNodeWithTag("cancel").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, confirms); assertEquals(1, cancels) }
    }

    @Test fun largeTextSegmentsRetainTheirNamesAndOnlyDispatchAChangedChoice() {
        val selections = mutableListOf<String>()
        compose.setContent {
            SmartMistakeBookTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    var selected by remember { mutableStateOf("a") }
                    ClaudeSegmentedControl(listOf(ClaudeSegment("a", "学科", "segment_a"),
                        ClaudeSegment("b", "章节", "segment_b"), ClaudeSegment("c", "掌握", "segment_c"),
                        ClaudeSegment("d", "收藏", "segment_d")), selected,
                        { selected = it; selections += it })
                }
            }
        }
        compose.onNodeWithTag("segment_d").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("segment_d").performClick()
        compose.runOnIdle { assertEquals(listOf("d"), selections) }
    }

    @Test fun accessiblePaneResizeKeepsTheSameDraftAndClampsBothSides() {
        var actualRatio = .5f
        compose.setContent {
            SmartMistakeBookTheme {
                var ratio by remember { mutableFloatStateOf(.5f) }
                ClaudeSplitPane(ratio, { ratio = it; actualRatio = it }, Modifier.fillMaxSize(),
                    left = { Text("原图参考") }, right = {
                        var draft by remember { mutableStateOf("") }
                        ClaudeTextField(draft, { draft = it }, Modifier.testTag("pane_draft"))
                    })
            }
        }
        compose.onNodeWithTag("pane_draft").performTextInput("不能丢的草稿")
        compose.onNodeWithTag("editor_split_handle").performSemanticsAction(SemanticsActions.SetProgress) { it(.9f) }
        compose.onNodeWithText("不能丢的草稿").assertIsDisplayed()
        compose.runOnIdle { assertTrue(actualRatio in .5f.. .9f) }
    }
}
