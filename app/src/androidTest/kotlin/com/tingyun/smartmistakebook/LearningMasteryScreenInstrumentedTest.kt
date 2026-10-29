package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import com.tingyun.smartmistakebook.core.ui.LocalClaudeMotionEnabled
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
        composeRule.onNodeWithTag("star_local_mode").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("知识星图，1 个知识点，0 条已确认的前置关系。", substring = true).assertExists()
        composeRule.onNodeWithText("该知识暂无已记录的直接关联，可在知识总览查看其他知识。").assertExists()
        composeRule.onNodeWithTag("knowledge_star_observatory").performScrollTo()
        saveChartScreenshot("knowledge-local-no-relations")
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
        composeRule.onNodeWithTag("knowledge_star_observatory").performScrollTo()
        saveChartScreenshot("knowledge-constellation")
        saveStarFieldScreenshot("knowledge-star-field")
        selectKnowledge("m2")
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
        composeRule.onNodeWithTag("star_relation:before:m").assertExists()
        composeRule.onNodeWithTag("star_relation:before:m").performScrollTo().performClick()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数")
        composeRule.onNodeWithTag("star_relation:after:m2").assertExists()
        composeRule.onNodeWithTag("mastery_radar_chart").performScrollTo().assertIsDisplayed()
        saveChartScreenshot("knowledge-radar")
        composeRule.onNodeWithTag("knowledge_constellation").assertExists()
        composeRule.onNodeWithTag("mastery_axis_chart").assertDoesNotExist()
    }

    @Test
    fun relationSelectionRevealsHiddenGroupsAndSubjectFilteringResetsTheCamera() {
        val records = (0..20).map { i -> summary("m${i.toString().padStart(2, '0')}", "函数知识$i",
            SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .58, 0L) }
        val physics = summary("p", "牛顿第二定律", SubjectKind.PHYSICS, "力学", MasteryStatus.MASTERED, .88, 0L)
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                    weaknesses = records, strengths = listOf(physics),
                    knowledgePrerequisites = mapOf("m20" to setOf("m00"), "p" to setOf("m20"))), onBack = {})
            }
        }
        composeRule.onNodeWithTag("star_relation:after:m20").performScrollTo().performClick()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数知识20")
        composeRule.onNodeWithText("2 / 2").assertExists()
        selectSubject("MATH")
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数知识0")
        composeRule.onNodeWithTag("star_relation:after:m20").performScrollTo().performClick()
        composeRule.onNodeWithTag("star_relation:after:p").performScrollTo().performClick()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("牛顿第二定律")
        composeRule.onNodeWithTag("star_next_page").assertDoesNotExist()
        composeRule.onNodeWithTag("star_zoom_in").performScrollTo().performClick()
        composeRule.onNodeWithText("125%").assertExists()
        selectSubject("MATH")
        composeRule.onNodeWithText("100%").assertExists()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数知识0")
        composeRule.onNodeWithTag("star_next_page").performScrollTo().performClick()
        selectKnowledge("m20")
        composeRule.onNodeWithTag("knowledge_star_observatory").performScrollTo()
        saveChartScreenshot("knowledge-star-second-group")
    }

    @Test
    fun denseStarsRemainUsableWithLargeTextAndReducedMotion() {
        val records = (0..17).map { i -> summary("k${i.toString().padStart(2, '0')}", "函数与方程的综合应用知识$i",
            SubjectKind.MATH, "函数", MasteryStatus.entries[i % MasteryStatus.entries.size], .58, 0L) }
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f),
                LocalClaudeMotionEnabled provides false) {
                SmartMistakeBookTheme {
                    LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                        weaknesses = records, knowledgePrerequisites = (1..17).associate {
                            "k${it.toString().padStart(2, '0')}" to setOf("k${(it - 1).toString().padStart(2, '0')}") }), onBack = {})
                }
            }
        }
        composeRule.onNodeWithTag("knowledge_star_observatory").performScrollTo()
        saveChartScreenshot("knowledge-star-large-text")
        saveStarFieldScreenshot("knowledge-star-dense-field")
        composeRule.onNodeWithTag("star_zoom_in").performScrollTo().performClick()
        composeRule.onNodeWithText("125%").assertExists()
        composeRule.onNodeWithTag("star_zoom_out").performClick()
        composeRule.onNodeWithText("100%").assertExists()
        selectKnowledge("k17")
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数与方程的综合应用知识17")
        composeRule.onNodeWithTag("star_relation:before:k16").performScrollTo().performClick()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数与方程的综合应用知识16")
    }

    @Test
    fun starNameLabelsCanBeTappedBeyondTheSmallStarTouchCircle() {
        val records = listOf(summary("a", "函数", SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .58, 0L),
            summary("b", "函数应用", SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .68, 0L))
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                    weaknesses = records), onBack = {})
            }
        }
        val target = constellationStars(records, emptyList()).single { it.knowledge.knowledgeNodeId == "b" }
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        composeRule.onNodeWithTag("knowledge_star_field").performScrollTo().assertIsDisplayed().performTouchInput {
            click(Offset(width * target.x, height * target.y + 28f * density))
        }
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
    }

    @Test
    fun localGraphRevealsCrossGroupNeighborsAndRecentersOnlyWhenRequested() {
        val records = (0..20).map { i -> summary("m${i.toString().padStart(2, '0')}",
            when (i) { 0 -> "函数"; 20 -> "函数应用"; else -> "函数知识$i" },
            SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .58, 0L) }
        val physics = summary("p", "位移与速度关系", SubjectKind.PHYSICS, "运动", MasteryStatus.MASTERED, .88, 0L)
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                    weaknesses = records, strengths = listOf(physics),
                    knowledgePrerequisites = mapOf("m20" to setOf("m00"), "p" to setOf("m20"))), onBack = {})
            }
        }
        composeRule.onNodeWithTag("star_view_relations").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("star_local_anchor").assertTextContains("围绕：函数")
        composeRule.onNodeWithContentDescription("知识星图，2 个知识点，1 条已确认的前置关系。", substring = true).assertExists()
        composeRule.onNodeWithTag("star_next_page").assertDoesNotExist()
        selectKnowledge("m20")
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
        composeRule.onNodeWithTag("star_local_anchor").assertTextContains("围绕：函数")
        composeRule.onNodeWithTag("star_view_relations").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("star_local_anchor").assertTextContains("围绕：函数应用")
        composeRule.onNodeWithContentDescription("知识星图，3 个知识点，2 条已确认的前置关系。", substring = true).assertExists()
        composeRule.onNodeWithTag("star_subject_selector").assertDoesNotExist()
        composeRule.onNodeWithTag("knowledge_star_observatory").performScrollTo()
        saveChartScreenshot("knowledge-local-relations")
        saveStarFieldScreenshot("knowledge-local-field")
        composeRule.onNodeWithTag("star_overview_mode").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("star_local_anchor").assertDoesNotExist()
        composeRule.onNodeWithText("2 / 2").assertExists()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数应用")
    }

    @Test
    fun localGraphPaginationAlwaysKeepsTheCenterKnowledgeVisible() {
        val records = (0..20).map { i -> summary("m${i.toString().padStart(2, '0')}", "函数知识$i",
            SubjectKind.MATH, "函数", MasteryStatus.LEARNING, .58, 0L) }
        composeRule.setContent {
            SmartMistakeBookTheme {
                LearningMasteryScreen(overview = StudyProfileOverview(hasLearningEvidence = true,
                    weaknesses = records, knowledgePrerequisites = (0..19).associate {
                        "m${it.toString().padStart(2, '0')}" to setOf("m20") }), onBack = {})
            }
        }
        composeRule.onNodeWithTag("star_relation:before:m20").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("star_local_mode").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("知识星图，18 个知识点，17 条已确认的前置关系。", substring = true).assertExists()
        composeRule.onNodeWithTag("star_next_page").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("知识星图，4 个知识点，3 条已确认的前置关系。", substring = true).assertExists()
        composeRule.onNodeWithTag("star_select:m20").assertExists()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数知识20")
        composeRule.onNodeWithTag("star_overview_mode").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("2 / 2").assertExists()
        composeRule.onNodeWithTag("knowledge_star_details").assertTextContains("函数知识20")
    }

    private fun selectSubject(name: String) {
        composeRule.onNodeWithTag("star_subject_selector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("star_subject:$name").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun selectKnowledge(id: String) {
        composeRule.onNodeWithTag("star_knowledge_selector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("star_select:$id").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    private fun saveStarFieldScreenshot(name: String) {
        composeRule.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = requireNotNull(context.getExternalFilesDir("full-ui-forest"))
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            composeRule.onNodeWithTag("knowledge_star_field").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
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
