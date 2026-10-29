package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.tingyun.smartmistakebook.core.domain.StudyKnowledgeSummary
import com.tingyun.smartmistakebook.core.domain.StudyProfileOverview
import com.tingyun.smartmistakebook.core.model.MasteryStatus
import com.tingyun.smartmistakebook.core.model.SubjectKind
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LearningMasteryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun learningHistoryIsGroupedForStudentsWithoutKnowledgeBaseInternals() {
        val now = 10 * DAY_MILLIS
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(
                    overview = StudyProfileOverview(
                        hasLearningEvidence = true,
                        recordedAttemptCount = 8,
                        weaknesses = listOf(
                            summary(
                                id = "math:monotonicity",
                                name = "函数单调性",
                                subject = SubjectKind.MATH,
                                topic = "函数",
                                status = MasteryStatus.LEARNING,
                                confidence = 0.58,
                                lastEvidenceAtEpochMillis = now - DAY_MILLIS,
                            ),
                        ),
                        strengths = listOf(
                            summary(
                                id = "physics:newton-2",
                                name = "牛顿第二定律",
                                subject = SubjectKind.PHYSICS,
                                topic = "相互作用与运动",
                                status = MasteryStatus.MASTERED,
                                confidence = 0.86,
                                lastEvidenceAtEpochMillis = now,
                            ),
                        ),
                    ),
                    onBack = {},
                    nowEpochMillis = now,
                )
            }
        }

        composeRule.onNodeWithText("学习掌握").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_mastery_distribution").assertExists()
        captureReviewForestScreenshot("mastery-distribution")
        composeRule.onNodeWithTag("learning_mastery_subject:MATH").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("learning_mastery_point:math:monotonicity").assertTextContains("函数单调性")
        composeRule.onNodeWithTag("learning_mastery_subject:PHYSICS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("learning_mastery_point:physics:newton-2").assertTextContains("牛顿第二定律")
        captureReviewForestScreenshot("mastery-subjects")
        composeRule.onNodeWithTag("learning_mastery_recent").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("最近活动").assertExists()
        composeRule.onNodeWithText("最近变化").assertDoesNotExist()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("昨天").assertIsDisplayed()
        // 批次 2 / P8（规格 §1.4 锚 4）：两行各出现自己的粗粒度区间文案
        // （0.58→「约 5–8 成」；0.86→「约 8–10 成」，各只出现一次）。
        composeRule.onNodeWithTag("learning_mastery_point:math:monotonicity").assertTextContains("约 5–8 成")
        composeRule.onNodeWithTag("learning_mastery_point:physics:newton-2").assertTextContains("约 8–10 成")
        FORBIDDEN_STUDENT_TERMS.forEach { term ->
            composeRule.onAllNodesWithText(term, substring = true).assertCountEquals(0)
        }
        saveAuditScreenshot()
    }

    @Test
    fun unknownEvidenceIsKeptSeparateFromMasteredKnowledge() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(
                    overview = StudyProfileOverview(hasLearningEvidence = true,
                        weaknesses = listOf(StudyKnowledgeSummary(
                            knowledgeNodeId = "new", displayName = "新记录知识点",
                            status = MasteryStatus.UNKNOWN, conservativeMasteryScore = 0.0,
                            masteryIntervalUpper = null, subject = SubjectKind.MATH))),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("刚开始记录 1 个", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("掌握较稳 0 个", substring = true).assertExists()
        composeRule.onNodeWithTag("learning_mastery_point:new").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("learning_mastery_point:new").assertTextContains("证据还少")
        composeRule.onNodeWithText("平均掌握估计 · 证据还少").assertExists()
        captureReviewForestScreenshot("mastery-no-evidence")
    }

    @Test
    fun knowledgeStarsShowOnlyConfirmedRelationsAndKeepRadarSeparate() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                    weaknesses = listOf(
                        summary("m", "函数", SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .58, 0L),
                        summary("m2", "函数应用", SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .68, 0L),
                        summary("c", "化学反应", SubjectKind.CHEMISTRY, "反应", MasteryStatus.LEARNING, .48, 0L),
                        summary("c2", "化学计量", SubjectKind.CHEMISTRY, "反应", MasteryStatus.LEARNING, .58, 0L),
                        summary("c3", "反应速率", SubjectKind.CHEMISTRY, "反应", MasteryStatus.LEARNING, .68, 0L),
                    ), strengths = listOf(summary("p", "力学", SubjectKind.PHYSICS, "力学", MasteryStatus.MASTERED, .88, 0L)),
                    knowledgePrerequisites = mapOf("m2" to setOf("m"), "c2" to setOf("c"), "c3" to setOf("c2"))), onBack = {})
            }
        }
        composeRule.onNodeWithTag("knowledge_star_field").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge_star_field").performTouchInput {
            pinch(Offset(width * .4f, height * .5f), Offset(width * .25f, height * .5f),
                Offset(width * .6f, height * .5f), Offset(width * .75f, height * .5f))
        }
        composeRule.onNodeWithText("100%").assertDoesNotExist()
        val beforePan = composeRule.onNodeWithTag("knowledge_star_field").captureToImage().asAndroidBitmap()
        composeRule.onNodeWithTag("knowledge_star_field").performTouchInput {
            pinch(Offset(width * .3f, height * .4f), Offset(width * .4f, height * .5f),
                Offset(width * .6f, height * .4f), Offset(width * .7f, height * .5f))
        }
        val afterPan = composeRule.onNodeWithTag("knowledge_star_field").captureToImage().asAndroidBitmap()
        assertFalse("Two-finger panning must move the rendered stars", beforePan.sameAs(afterPan))
        composeRule.onNodeWithTag("star_reset_view").performScrollTo().performClick()
        composeRule.onNodeWithText("100%").assertExists()
        val ids = listOf("m", "m2", "c", "c2", "c3", "p")
        val stars = constellationStars(ids.map { StudyKnowledgeSummary(it, it, MasteryStatus.UNKNOWN, 0.0) },
            listOf(KnowledgeStarLink("m", "m2"), KnowledgeStarLink("c", "c2"), KnowledgeStarLink("c2", "c3")))
        val target = stars.single { it.knowledge.knowledgeNodeId == "m2" }
        composeRule.onNodeWithTag("knowledge_star_field").performScrollTo().performTouchInput {
            click(Offset(width * target.x, height * target.y))
        }
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
        saveChartScreenshot("knowledge-constellation")
        composeRule.onNodeWithTag("star_select:m2").performScrollTo().performClick()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("已记录的前置知识：函数")
        composeRule.onNodeWithTag("mastery_radar_chart").performScrollTo().assertIsDisplayed()
        saveChartScreenshot("knowledge-radar")
        composeRule.onNodeWithTag("knowledge_constellation").assertExists()
        composeRule.onNodeWithTag("mastery_axis_chart").assertDoesNotExist()
    }

    private fun saveChartScreenshot(name: String) {
        composeRule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = requireNotNull(context.getExternalFilesDir("full-ui-forest"))
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            composeRule.onNodeWithTag("learning_mastery_screen").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun saveAuditScreenshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = requireNotNull(context.getExternalFilesDir("audit"))
        val screenshot = File(directory, "learning-mastery.png")
        FileOutputStream(screenshot).use { output ->
            composeRule.onNodeWithTag("learning_mastery_screen")
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun summary(
        id: String,
        name: String,
        subject: SubjectKind,
        topic: String,
        status: MasteryStatus,
        confidence: Double,
        lastEvidenceAtEpochMillis: Long,
    ) = StudyKnowledgeSummary(
        knowledgeNodeId = id,
        displayName = name,
        status = status,
        conservativeMasteryScore = confidence,
        // 批次 2 / P8（规格 §1.4 锚 4）：区间上界由夹具给出 → 行内出现粗粒度区间文案。
        masteryIntervalUpper = (confidence + 0.2).coerceAtMost(1.0),
        evidenceMass = 4.0,
        independentCorrectObservationCount = 3,
        lastEvidenceAtEpochMillis = lastEvidenceAtEpochMillis,
        subject = subject,
        topicPath = listOf(topic),
    )

    private companion object {
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS

        val FORBIDDEN_STUDENT_TERMS = listOf(
            "原子知识",
            "知识本体",
            "检索召回",
            "学习投影",
            "grounding",
            "taxonomy",
            "embedding",
            "置信度",
            "数据库",
            "数据表",
            "索引",
            "节点",
            "向量",
            "schema",
            "模型候选",
            "前置边",
            "分类依据",
            // 批次 2 / P8（规格 §1.3）：内部术语不得随新区间文案漏到学生面。
            "保守分",
            "下界",
            "Wilson",
        )
    }
}
