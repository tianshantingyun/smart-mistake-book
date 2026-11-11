package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.tingyun.smartmistakebook.core.domain.PrerequisiteRemediation
import com.tingyun.smartmistakebook.core.domain.ReTeachOpening
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import com.tingyun.smartmistakebook.core.model.NetworkMode
import com.tingyun.smartmistakebook.core.model.TeachingArtifactVerification
import com.tingyun.smartmistakebook.core.model.TutorAssessmentItem
import com.tingyun.smartmistakebook.core.model.TutorChoice
import com.tingyun.smartmistakebook.core.model.VerifiedTeachingArtifact
import com.tingyun.smartmistakebook.core.ui.AiContentBadge
import com.tingyun.smartmistakebook.core.ui.AttachedImageRenderState
import com.tingyun.smartmistakebook.core.ui.GeneratedFigureCard
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.ReviewSessionScreen
import org.junit.Rule
import org.junit.Test

/**
 * AIGC 显式标识的**界面可见性**（《人工智能生成合成内容标识办法》第四条）。
 *
 * 这些用例在标识落地前是红的（标签节点不存在）：`GeneratedFigureCard` 只写"配图"、
 * 材料卡只有标题——用户在 App 内完全看不出哪段内容是模型产出的。R3 §2.2 把
 * "App 内讲题对话与生成图展示无任何用户可见 AI 标识"列为 P0，这里是它的 UI 面。
 *
 * 只测"标识在屏上且措辞统一"：判据本身（哪些内容算 AI 生成）在数据层/导出层各有单测，
 * 这里不重复，也不放宽。
 */
class AiContentLabelsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stem = "求 f(x)=x^3-3x+1 在闭区间上的最大值与最小值。"

    private val artifact = VerifiedTeachingArtifact(
        id = "teaching:aigc",
        subject = "MATH",
        title = "闭区间上的函数最值",
        problemMarkdown = stem,
        explanationMarkdown = "端点值与驻点值必须同时比较。",
        verification = TeachingArtifactVerification.CURATED_REFERENCE,
        assessmentItems = listOf(
            TutorAssessmentItem(
                id = "assessment:aigc",
                stemMarkdown = stem,
                choices = listOf(
                    TutorChoice(id = "A", markdown = "最大值为 3，最小值为 -1"),
                    TutorChoice(id = "B", markdown = "最大值为 2，最小值为 -2"),
                ),
                correctChoiceId = "A",
                knowledgeNodeIds = setOf("knowledge:m1:closed-interval-extrema"),
            ),
        ),
        knowledgeNodeIds = setOf("knowledge:m1:closed-interval-extrema"),
    )

    private val opening = ReTeachOpening(
        materialId = "material:aigc",
        title = "闭区间最值常见错误",
        materialType = KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE,
        markdown = "只比较驻点而漏掉端点，是最常见的错误。",
    )

    private val remediation = PrerequisiteRemediation(
        prerequisiteName = "从图像读取单调性",
        title = "读图判断单调性常见错误",
        markdown = "单调区间要按定义域分段读。",
    )

    private val capabilities = AppCapabilitySnapshot(
        networkMode = NetworkMode.LOCAL_FIRST,
        cameraCaptureAvailable = false,
        trustedOcrAvailable = false,
        tutorTeachingEnabled = true,
        remoteModelConfigured = false,
    )

    @Test
    fun generatedFigureCarriesTheVisibleAiBadge() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                GeneratedFigureCard(
                    assetId = "asset-aigc",
                    state = AttachedImageRenderState.Ready("file:///nonexistent.png"),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("generated_figure_ai_label_asset-aigc", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("AI 生成", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun reTeachMaterialCarriesTheAiOrganizedBadgeAndNote() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewSessionScreen(
                    onBack = {},
                    capabilities = capabilities,
                    practiceUnitId = "practice:m1:closed-interval-extrema:whole",
                    presentationId = "presentation:aigc-reteach",
                    teachingArtifact = artifact,
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    reTeachOpening = opening,
                    queuePosition = 1,
                    queueSize = 3,
                    onSubmitChoice = { error("labels must not submit") },
                    onRevealAnswer = { error("labels must not reveal") },
                    onContinue = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("review_reteach_ai_label").assertIsDisplayed()
        composeRule.onNodeWithTag("review_reteach_ai_note").assertIsDisplayed()
        composeRule.onNodeWithText("AI 整理").assertIsDisplayed()
        composeRule.onNodeWithText("本材料由 AI 整理，请以教材和老师讲授为准。").assertIsDisplayed()
    }

    @Test
    fun prerequisiteRemediationCarriesTheAiOrganizedBadgeAndNote() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                ReviewSessionScreen(
                    onBack = {},
                    capabilities = capabilities,
                    practiceUnitId = "practice:m1:closed-interval-extrema:whole",
                    presentationId = "presentation:aigc-prereq",
                    teachingArtifact = artifact,
                    profile = StudyProfileOverview(hasLearningEvidence = true),
                    prerequisiteRemediation = remediation,
                    queuePosition = 1,
                    queueSize = 3,
                    onSubmitChoice = { error("labels must not submit") },
                    onRevealAnswer = { error("labels must not reveal") },
                    onContinue = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("review_prereq_ai_label").assertIsDisplayed()
        composeRule.onNodeWithTag("review_prereq_ai_note").assertIsDisplayed()
    }

    @Test
    fun theBadgeItselfRendersTheGivenWording() {
        // 文案来源单源：`AI 生成` / `AI 整理` 是 core:ui 的常量，不在各界面各写一份。
        composeRule.setContent {
            SmartMistakeBookTheme {
                AiContentBadge(text = "AI 生成", modifier = androidx.compose.ui.Modifier)
            }
        }
        composeRule.onNodeWithText("AI 生成").assertIsDisplayed()
    }
}
