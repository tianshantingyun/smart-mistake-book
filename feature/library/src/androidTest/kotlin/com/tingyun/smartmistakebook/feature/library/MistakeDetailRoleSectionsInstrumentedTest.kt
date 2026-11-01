package com.tingyun.smartmistakebook.feature.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import com.tingyun.smartmistakebook.core.domain.MistakeDetail
import com.tingyun.smartmistakebook.core.domain.MistakeDetailIdentity
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionSummary
import com.tingyun.smartmistakebook.core.domain.MistakeSourceAsset
import com.tingyun.smartmistakebook.core.domain.MistakeSourceLocation
import com.tingyun.smartmistakebook.core.domain.MistakeSourceSet
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Direct full-width pictures keep question/explanation roles separate and never write data. */
@RunWith(AndroidJUnit4::class)
class MistakeDetailRoleSectionsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val files = mutableListOf<File>()

    @After fun removeFixtures() {
        ImageReadBudgetProvider.clear()
        files.forEach { it.delete() }
    }

    @Test fun questionSourceAndNoteModulesRemainSeparateWithoutWriting() {
        val repository = ReadOnlyDetailRepository()
        show(readyState(), repository)
        composeRule.onAllNodesWithText("函数最值", useUnmergedTree = true).assertCountEquals(1)
        val question = composeRule.onNodeWithTag("mistake_detail_question_module").getUnclippedBoundsInRoot()
        val source = composeRule.onNodeWithTag("mistake_detail_source_section").getUnclippedBoundsInRoot()
        val note = composeRule.onNodeWithTag("mistake_detail_note").getUnclippedBoundsInRoot()
        assertTrue(question.bottom <= source.top)
        assertTrue(source.bottom < note.top)
        assertEquals(0, repository.writes)
    }

    @Test fun aDistinctDocumentTitleIsKeptBesideTheQuestionIdentity() {
        show(readyState(assets = emptyList(), documentTitle = "附加条件"))
        composeRule.onNodeWithText("函数最值").assertIsDisplayed()
        composeRule.onNodeWithText("附加条件").assertIsDisplayed()
    }

    @Test fun questionAndExplanationPicturesAppearWithoutAnExpansionStepAndStayInSeparateSections() {
        val repository = ReadOnlyDetailRepository()
        show(readyState(), repository)
        composeRule.onNodeWithTag("mistake_detail_source_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_3").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_4").assertDoesNotExist()
        composeRule.onNodeWithText("重绘").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_3").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_source_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_toggle").assertDoesNotExist()
        assertEquals(0, repository.writes)
    }

    @Test fun directSectionsSurviveStateRestoreWithoutAnyWriteOrDispatch() {
        val repository = ReadOnlyDetailRepository()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(state = readyState(), onBack = {}, onExport = {}, repository = repository)
            }
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("mistake_detail_source_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_4").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1").assertExists()
        assertEquals(0, repository.writes)
    }

    @Test fun legacyCleanImageStillRendersFirstAndHasNoExplanationSection() {
        show(readyState(listOf(asset("original", "QUESTION_SOURCE"), asset("clean", VisualRole.CLEAN_IMAGE.name))))
        composeRule.onNodeWithTag("mistake_detail_source_asset_1").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_2").assertExists()
        composeRule.onNodeWithTag("mistake_detail_source_asset_3").assertDoesNotExist()
        composeRule.onNodeWithText("重绘").assertExists()
        composeRule.onNodeWithTag("mistake_detail_explanation_section").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_content").assertDoesNotExist()
    }

    @Test fun aPictureUsesItsFullWidthAndNaturalHeightAndOpensTheViewerDirectly() {
        val picture = picture("full-width", 800, 1600)
        val file = File(java.net.URI((picture.location as MistakeSourceLocation.Available).localUri))
        val originalHash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
        val repository = ReadOnlyDetailRepository()
        show(readyState(listOf(picture)), repository)
        composeRule.onNodeWithTag("mistake_detail_source_asset_1_image").performScrollTo()
        awaitViewAction("mistake_detail_source_asset_1_view")
        val image = composeRule.onNodeWithTag("mistake_detail_source_asset_1_image").getUnclippedBoundsInRoot()
        val module = composeRule.onNodeWithTag("mistake_detail_source_section").getUnclippedBoundsInRoot()
        assertEquals((module.right - module.left).value, (image.right - image.left).value, 1f)
        assertEquals((image.right - image.left).value * 2, (image.bottom - image.top).value, 1f)
        assertTrue("The detail picture must not be capped at thumbnail height", (image.bottom - image.top).value > 520f)
        composeRule.onAllNodesWithText("原图", useUnmergedTree = true).assertCountEquals(1)
        composeRule.captureLibraryQaScreenshot("mistake-detail-full-image.png")
        composeRule.onNodeWithContentDescription("原图", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("image_viewer_viewport").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭图片查看").performClick()
        composeRule.onNodeWithTag("mistake_detail_source_asset_1_image").assertIsDisplayed()
        assertEquals(originalHash, MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList())
        assertEquals(0, repository.writes)
    }

    @Test fun directExplanationPicturesAlsoUseNaturalHeight() {
        show(readyState(listOf(picture("explanation", 800, 1600, VisualRole.TUTOR_EXPLANATION.name))))
        composeRule.onNodeWithTag("mistake_detail_source_section").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_detail_explanation_asset_1_image").performScrollTo()
        awaitViewAction("mistake_detail_explanation_asset_1_view")
        val image = composeRule.onNodeWithTag("mistake_detail_explanation_asset_1_image").getUnclippedBoundsInRoot()
        assertEquals((image.right - image.left).value * 2, (image.bottom - image.top).value, 1f)
        composeRule.onNodeWithTag("mistake_detail_explanation_toggle").assertDoesNotExist()
    }

    @Test fun manyDirectLongPagesOnlyDecodeInTheViewportAndTheLastPageIsReachable() {
        val pages = (1..12).map { page ->
            val source = picture("detail-budget-$page", 100, 3000)
            val file = File(java.net.URI((source.location as MistakeSourceLocation.Available).localUri))
            source.copy(location = MistakeSourceLocation.Available(ImageReadBudgetProvider.register(source.sourceAssetId, file)))
        }
        show(readyState(pages))
        composeRule.onNodeWithTag("mistake_detail_source_asset_1_image").performScrollTo()
        awaitViewAction("mistake_detail_source_asset_1_view")
        composeRule.runOnIdle {
            assertTrue("Offscreen pages were decoded: ${ImageReadBudgetProvider.openedIds()}",
                ImageReadBudgetProvider.openedIds().size <= 3)
        }
        composeRule.onNodeWithTag("mistake_detail_source_asset_12_view").performScrollTo()
        awaitViewAction("mistake_detail_source_asset_12_view")
        composeRule.runOnIdle { assertTrue("detail-budget-12" in ImageReadBudgetProvider.openedIds()) }
    }

    private fun awaitViewAction(tag: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        composeRule.onNodeWithTag(tag).assertIsEnabled()
    }

    private fun show(state: MistakeDetailState.Ready, repository: ReadOnlyDetailRepository = ReadOnlyDetailRepository()) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeDetailContent(state = state, onBack = {}, onExport = {}, repository = repository)
            }
        }
    }

    private fun picture(id: String, width: Int, height: Int, role: String = "QUESTION_SOURCE"): MistakeSourceAsset {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "$id.png")
        files += file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = width * 0.055f }
        canvas.drawText("1. 已知 f(x) = x² - 4x + 5", width * 0.06f, width * 0.12f, paint)
        canvas.drawText("求最小值并写出计算过程。", width * 0.06f, width * 0.22f, paint)
        paint.color = Color.rgb(30, 110, 80)
        paint.strokeWidth = 3f
        canvas.drawLine(width * 0.12f, height * 0.8f, width * 0.88f, height * 0.8f, paint)
        canvas.drawText("完整图片底部", width * 0.3f, height * 0.95f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return asset(id, role).copy(width = width, height = height, byteSize = file.length(),
            location = MistakeSourceLocation.Available(file.toURI().toString()))
    }

    private fun readyState(
        assets: List<MistakeSourceAsset> = defaultAssets(),
        documentTitle: String = "函数最值",
    ) = MistakeDetailState.Ready(
        detail = MistakeDetail(
            identity = MistakeDetailIdentity(
                errorBookEntryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-1",
                revisionNumber = 1,
                title = "函数最值",
                subject = "MATH",
            ),
            fallbackMarkdown = "求函数最值。",
            source = if (assets.isEmpty()) MistakeSourceSet.Missing else MistakeSourceSet.Present(assets),
        ),
        questionDocument = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "question",
                title = documentTitle,
                blocks = listOf(ContentBlock.Paragraph(id = "stem", markdown = "求函数最值。")),
            ),
            blockEvidence = emptyList(),
        ),
    )

    private fun defaultAssets() = listOf(
        asset("original", "QUESTION_SOURCE"),
        asset("clean", VisualRole.CLEAN_IMAGE.name),
        asset("diagram", VisualRole.QUESTION_DIAGRAM.name),
        asset("explain-a", VisualRole.TUTOR_EXPLANATION.name),
        asset("explain-b", VisualRole.TUTOR_EXPLANATION.name),
    )

    private fun asset(id: String, role: String) = MistakeSourceAsset(
        role = role,
        sourceAssetId = id,
        contentSha256 = "a".repeat(64),
        mimeType = "image/png",
        byteSize = 1_024,
        width = 800,
        height = 600,
        sourceType = "CAMERA",
        createdAtEpochMillis = 1_000,
        location = MistakeSourceLocation.Available("file:///nonexistent-$id.png"),
    )

    /** 详情页只读：任何写/派发路径被触发都让用例失败，而不是悄悄记账。 */
    private class ReadOnlyDetailRepository : MistakeDetailRepository {
        var writes = 0
            private set

        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override fun observeExact(key: MistakeRevisionKey): Flow<MistakeDetailState> =
            flowOf(MistakeDetailState.NotFound)

        override suspend fun readExact(key: MistakeRevisionKey): MistakeDetailState =
            MistakeDetailState.NotFound

        override suspend fun reviseProblem(
            command: ReviseMistakeProblemCommand,
        ): ReviseMistakeProblemResult {
            writes += 1
            error("详情页不得发起题目修订")
        }

        override suspend fun updateUserNote(
            entryId: String,
            note: String?,
            updatedAtEpochMillis: Long,
        ): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得写备注")
        }

        override suspend fun setFavorite(
            entryId: String,
            isFavorite: Boolean,
            updatedAtEpochMillis: Long,
        ): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得改收藏")
        }

        override suspend fun archiveEntry(entryId: String, at: Long): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得归档")
        }

        override suspend fun restoreEntry(entryId: String, at: Long): Boolean {
            writes += 1
            error("详情页打开/展开/旋转不得恢复")
        }

        override fun observeRevisionHistory(
            errorBookEntryId: String,
        ): Flow<List<MistakeRevisionSummary>> = flowOf(emptyList())
    }
}
