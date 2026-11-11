package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import com.tingyun.smartmistakebook.core.model.TutorTurnPlan
import org.junit.Rule
import org.junit.Test

/**
 * 讲题回复的**显式** AI 标识（《办法》第四条；R3 §2.2 的 P0 差距）。
 *
 * 回复正文是模型生成的文本；此前界面上没有任何"AI 生成"提示（R3 §5.3 grep 实测）。
 * 这条用例钉住两件事：有正文就必须有一个用户可见的标识；没有正文（没有消息行）时不出现
 * 一个无对象可指的标识（不为"更安全"把界面标成 AI 生成——那同样违反第十条）。
 */
class TutorAiGeneratedLabelInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aModelAuthoredReplyShowsTheAiBadge() {
        composeRule.setContent {
            MaterialTheme {
                TutorTurnContent(
                    output = output(),
                    openingMarkdown = output().plan.openingMarkdown,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutor_ai_generated_label").assertIsDisplayed()
        composeRule.onNodeWithText("AI 生成").assertIsDisplayed()
    }

    @Test
    fun aTurnWithoutAReplyBodyShowsNoBadge() {
        composeRule.setContent {
            MaterialTheme {
                TutorTurnContent(output = output(), openingMarkdown = null)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutor_ai_generated_label").assertDoesNotExist()
    }

    private fun output() = TutorPlanOutput(
        sessionId = "session-aigc",
        draftRevisionNumber = 1,
        questionDocumentId = "document-aigc",
        plan = TutorTurnPlan(
            openingMarkdown = "先判断导数的正负变化。",
            solutionMarkdown = "完整主解法内容",
            alternateMethodMarkdown = "符号表替代解法",
            difficultyReasonMarkdown = "用于区分符号对应和变号遗漏。",
            targetedEvidenceLabels = emptyList(),
            inferredKnowledgeLabels = listOf("导数", "函数单调性"),
        ),
        modelVersion = "model-v1",
    )
}
