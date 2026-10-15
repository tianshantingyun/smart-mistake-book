package com.tingyun.smartmistakebook

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.*
import com.tingyun.smartmistakebook.core.model.*
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.review.CapturedReviewSessionScreen
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test

class CapturedReviewDocumentInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val files = mutableListOf<File>()
    private val entry = StudyCatalogEntry("entry", "problem", "revision", "practice", "CHEMISTRY", "化学装置题",
        "旧文字投影", null, false, emptyList(), emptyList(), MasteryStatus.UNKNOWN, null, null)

    @After fun cleanup() { files.forEach(File::delete) }

    @Test fun formalContentAndEveryOriginalPageCanBeReadWithoutStartingTutor() {
        show(ready(withImages = true))
        compose.onNodeWithText("这里是正式题面").assertIsDisplayed()
        compose.onNodeWithText("旧文字投影").assertDoesNotExist()
        compose.onNodeWithTag("captured_review_source_open").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("复习原图 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("复习原图 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("复习原图 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭原图").performClick()
        compose.onNodeWithTag("captured_review_source_open").assertExists()
    }

    @Test fun aPreviousQuestionsDetailCannotSupplyThisQuestionsImages() {
        val state = ready(withImages = true)
        show(state.copy(detail = state.detail.copy(identity = state.detail.identity.copy(problemRevisionId = "old"))))
        compose.onNodeWithText("旧文字投影").assertIsDisplayed()
        compose.onNodeWithTag("captured_review_source_open").assertDoesNotExist()
    }

    @Test fun unavailableOriginalsAreExplainedWithoutHidingTheFormalQuestion() {
        show(ready(withImages = false))
        compose.onNodeWithText("这里是正式题面").assertIsDisplayed()
        compose.onNodeWithText("这道题的原图暂不可用。").assertExists()
    }

    private fun show(state: MistakeDetailState) {
        compose.setContent { SmartMistakeBookTheme {
            CapturedReviewSessionScreen({}, entry, 1, 1, false, {}, questionState = state)
        } }
    }

    private fun ready(withImages: Boolean): MistakeDetailState.Ready {
        val source = if (!withImages) MistakeSourceSet.Missing else MistakeSourceSet.Present((1..2).map { index ->
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "review-${UUID.randomUUID()}.png").also(files::add)
            val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
            MistakeSourceAsset("CAMERA", "page-$index", "a".repeat(64), "image/png", file.length(),
                80, 60, "CAMERA", 1, MistakeSourceLocation.Available(file.toURI().toString()))
        })
        return MistakeDetailState.Ready(MistakeDetail(MistakeDetailIdentity("entry", "problem", "revision", 1,
            "化学装置题", "CHEMISTRY"), "旧文字投影", source), CapturedQuestionDocument(
            document = QuestionDocument("doc", null, listOf(ContentBlock.Paragraph("stem", "这里是正式题面"))),
            blockEvidence = listOf(QuestionBlockEvidence("stem", "page-1", provenance = QuestionBlockProvenance.USER_CORRECTION,
                reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED)),
        ))
    }
}
