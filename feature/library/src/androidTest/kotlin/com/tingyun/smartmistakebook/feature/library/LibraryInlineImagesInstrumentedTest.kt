package com.tingyun.smartmistakebook.feature.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.domain.*
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.WritingLayer
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryInlineImagesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val files = mutableListOf<File>()

    @Test fun theCardIdentifiesTheQuestionBeforeThePictureAndKeepsThePictureActionBesideItsLabel() {
        show(PictureRepository(detail(listOf(asset("heading-order")))))
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        val title = compose.onNodeWithText("函数最值", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val origin = compose.onNodeWithText("原图 · 数学", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val zoom = compose.onNodeWithContentDescription("放大$PICTURE").fetchSemanticsNode().boundsInRoot
        assertTrue("The title must identify the card before the picture", title.bottom <= image.top)
        assertTrue("The source label must sit above its picture", origin.bottom <= image.top)
        assertTrue("The source and its action belong to the same row", origin.top < zoom.bottom && zoom.top < origin.bottom)
        compose.onNodeWithText("求函数最值。", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun anAuthoritativeEmptyCountDoesNotOfferExportOrSuggestClearingANonexistentFilter() {
        var captures = 0
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(listOf(entry()), catalogRepository = SinglePageCatalog(reportedTotal = 0),
                    onCapture = { captures++ }, onExportVisible = {}, onOpenExportResults = {}, onOpenItem = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("暂无错题").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("library_export_visible").assertDoesNotExist()
        compose.onNodeWithTag("library_clear_filters").assertDoesNotExist()
        compose.onNodeWithText("当前筛选下没有错题").assertDoesNotExist()
        compose.onNodeWithTag("library_capture_button").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") == "true") {
            compose.captureLibraryQaScreenshot("library-empty.png")
        }
        compose.onNodeWithTag("library_capture_button").performClick()
        compose.runOnIdle { assertEquals(1, captures) }
    }

    @Test fun aFailedFirstPageShowsAnErrorRatherThanAnEmptyLibraryAndRetryLoadsTheQuestion() {
        val catalog = SinglePageCatalog(failFirstLoad = true)
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(listOf(entry()), catalogRepository = catalog,
                    onCapture = {}, onExportVisible = {}, onOpenExportResults = {}, onOpenItem = {})
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("错题加载失败").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("暂无错题").assertDoesNotExist()
        compose.onNodeWithText("当前筛选下没有错题").assertDoesNotExist()
        compose.onNodeWithText("test-private-error").assertDoesNotExist()
        compose.onNodeWithTag("page_state_action").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("library_item_entry")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library_item_entry").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, catalog.loadAttempts) }
    }

    @Test fun loadingPicturesIsNotAnImageFailureAndAConfirmedTextQuestionRemovesThePlaceholder() {
        val state = kotlinx.coroutines.flow.MutableStateFlow<MistakeDetailState>(MistakeDetailState.Loading)
        val repository = object : MistakeDetailRepository by PictureRepository(detail(emptyList())) {
            override fun observe(errorBookEntryId: String) = state
        }
        show(repository)
        compose.onNodeWithText("正在加载题图…", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("求函数最值。", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("题图暂时无法显示").assertDoesNotExist()
        compose.runOnIdle { state.value = detail(emptyList()) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("正在加载题图…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("求函数最值。", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("题图暂时无法显示").assertDoesNotExist()
    }

    @After fun removeFixtures() {
        ImageReadBudgetProvider.clear()
        files.forEach { it.delete() }
    }

    @Test fun oneQuestionWithTwelveLongPagesOnlyDecodesVisiblePagesAndCanReachTheLast() {
        val pages = (1..12).map { page ->
            val source = asset("budget-$page", width = 100, height = 3000)
            val file = File(java.net.URI((source.location as MistakeSourceLocation.Available).localUri))
            source.copy(location = MistakeSourceLocation.Available(ImageReadBudgetProvider.register(source.sourceAssetId, file)))
        }
        show(PictureRepository(detail(pages)))
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).performScrollTo()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("放大$PICTURE"))
                .fetchSemanticsNodes().singleOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.runOnIdle {
            assertTrue("Offscreen pages were decoded: ${ImageReadBudgetProvider.openedIds()}",
                ImageReadBudgetProvider.openedIds().size <= 3)
        }
        val last = "函数最值，第12张题图"
        compose.onNodeWithContentDescription(last, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("放大$last").performScrollTo()
        compose.waitUntil(10_000) { "budget-12" in ImageReadBudgetProvider.openedIds() }
        compose.onNodeWithContentDescription("放大$last").assertIsEnabled().performClick()
        compose.onNodeWithText("原图 · $last").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭图片查看").performClick()
    }

    @Test fun zoomActionDoesNotOpenTheQuestionOrToggleItsFavorite() {
        val repository = PictureRepository(detail(listOf(asset("zoom"))))
        val opened = mutableListOf<String>()
        show(repository, onOpen = opened::add)
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        compose.onNodeWithContentDescription("放大$PICTURE").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("image_viewer_resolution")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("原图 · $PICTURE").assertIsDisplayed()
        compose.onNodeWithContentDescription("放大图片").performClick()
        compose.onNodeWithTag("image_viewer_scale").assertTextContains("125%")
        compose.onNodeWithContentDescription("关闭图片查看").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), opened); assertEquals(0, repository.favorites) }
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("entry"), opened); assertEquals(1, repository.reads) }
    }

    @Test fun zoomingARedrawnQuestionKeepsItsActualSourceRole() {
        show(PictureRepository(detail(listOf(asset("redrawn-zoom", role = CLEAN_IMAGE_SOURCE_ROLE)))))
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        compose.onNodeWithContentDescription("放大$PICTURE").performClick()
        compose.onNodeWithText("重绘 · $PICTURE").assertIsDisplayed()
        compose.onNodeWithText("原图 · $PICTURE").assertDoesNotExist()
    }

    @Test fun listShowsTheSavedPictureWithoutOpeningDetailAndFavoriteRemainsIndependent() {
        val repository = PictureRepository(detail(listOf(asset("photo"))))
        val opened = mutableListOf<String>()
        show(repository, onOpen = opened::add)
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(emptyList<String>(), opened)
        assertEquals(1, repository.reads)
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") == "true") {
            compose.captureLibraryQaScreenshot("library-inline-picture.png")
        }
        compose.onNodeWithTag("library_favorite_entry").performClick()
        compose.waitUntil { repository.favorites == 1 }
        assertEquals(emptyList<String>(), opened)
        assertEquals(1, repository.reads)
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).performClick()
        assertEquals(listOf("entry"), opened)
    }

    @Test fun pagedLibraryAlsoShowsTheSavedPictureDirectly() {
        show(PictureRepository(detail(listOf(asset("paged")), legacy = true)), paging = true)
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("library_item_entry"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun multipagePicturesAreAvailableInOrderWithoutOpeningTheQuestion() {
        val opened = mutableListOf<String>()
        val first = asset("page-1", role = "QUESTION_SOURCE", color = Color.RED)
        val second = asset("page-2", role = "QUESTION_SOURCE", color = Color.GREEN)
        show(PictureRepository(detail(listOf(second, first), readingOrder = listOf(first, second))), onOpen = opened::add)
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        val firstPixels = compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).captureToImage().toPixelMap()
        assertEquals(androidx.compose.ui.graphics.Color.Red, firstPixels[firstPixels.width / 2, firstPixels.height / 2])
        compose.onNodeWithContentDescription("函数最值，第2张题图", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        assertEquals(emptyList<String>(), opened)
    }

    @Test fun aNewRevisionOfTheSameEntryRefreshesThePicture() {
        val repository = PictureRepository(detail(listOf(asset("old", color = Color.RED))))
        val entry = mutableStateOf(entry())
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(listOf(entry.value), mistakeDetailRepository = repository,
                    onCapture = {}, onExportVisible = {}, onOpenExportResults = {}, onOpenItem = {})
            }
        }
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        awaitPicture()
        compose.runOnIdle {
            repository.state = detail(listOf(asset("new", color = Color.GREEN)), revision = "revision-2")
            entry.value = entry.value.copy(problemRevisionId = "revision-2")
        }
        compose.waitUntil(5_000) { repository.reads == 2 }
        compose.waitUntil(5_000) {
            val nodes = compose.onAllNodes(hasContentDescription(PICTURE), useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.size != 1) false else {
                val pixels = compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).captureToImage().toPixelMap()
                pixels[pixels.width / 2, pixels.height / 2] == androidx.compose.ui.graphics.Color.Green
            }
        }
    }

    @Test fun unavailablePictureKeepsTheQuestionTextAndAnHonestMessage() {
        val image = asset("broken").also { File(java.net.URI((it.location as MistakeSourceLocation.Available).localUri)).writeText("not an image") }
        show(PictureRepository(detail(listOf(image))))
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("题图暂时无法显示").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("题图暂时无法显示", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("求函数最值。", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aManualQuestionWithoutPicturesKeepsItsTextWithoutAFalseImageError() {
        show(PictureRepository(detail(emptyList())))
        compose.onNodeWithTag("library_item_entry").performScrollTo()
        compose.onNodeWithText("求函数最值。", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("题图暂时无法显示").assertDoesNotExist()
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun adjacentPicturesAndAManualQuestionKeepTheirOwnNavigationAndContent() {
        val second = entry().copy(entryId = "parabola", problemId = "problem-parabola", problemRevisionId = "revision-parabola",
            practiceUnitId = "practice-parabola", title = "抛物线的交点与对称轴", problemMarkdown = "求抛物线与坐标轴的交点及对称轴。",
            chapterLabels = listOf("二次函数"))
        val manual = entry().copy(entryId = "manual", problemId = "problem-manual", problemRevisionId = "revision-manual",
            practiceUnitId = "practice-manual", title = "配方法整理", problemMarkdown = "用配方法说明二次函数的最小值与顶点坐标的关系。")
        val items = listOf(entry(), second, manual)
        val states = mapOf(
            "entry" to detail(listOf(asset("gallery-first"))),
            second.entryId to detail(listOf(asset("gallery-second", lines = listOf(
                "2. 已知抛物线 y = x² - 2x - 3，", "求其与 x 轴的交点坐标，", "并写出对称轴方程。"))), revision = second.problemRevisionId, item = second),
            manual.entryId to detail(emptyList(), revision = manual.problemRevisionId, item = manual),
        )
        val repository = object : MistakeDetailRepository by PictureRepository(states.getValue("entry")) {
            override fun observe(errorBookEntryId: String) = flowOf(states.getValue(errorBookEntryId))
        }
        val opened = mutableListOf<String>()
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(items, mistakeDetailRepository = repository, onCapture = {}, onExportVisible = {},
                    onOpenExportResults = {}, onOpenItem = opened::add)
            }
        }
        awaitPicture()
        compose.onNodeWithContentDescription(PICTURE, useUnmergedTree = true).assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("uiEvidence") == "true") {
            compose.captureLibraryQaScreenshot("library-gallery.png")
        }
        compose.onNodeWithContentDescription("${second.title}，第1张题图", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(second.entryId), opened)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("library_item_manual"))
        compose.onNodeWithTag("library_item_manual").assertIsDisplayed()
        compose.onNodeWithText(manual.problemMarkdown, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun show(repository: MistakeDetailRepository, paging: Boolean = false, onOpen: (String) -> Unit = {}) {
        compose.setContent {
            SmartMistakeBookTheme {
                LibraryRoute(listOf(entry()), catalogRepository = if (paging) SinglePageCatalog() else null,
                    mistakeDetailRepository = repository, onCapture = {}, onExportVisible = {},
                    onOpenExportResults = {}, onOpenItem = onOpen)
            }
        }
    }

    private fun awaitPicture() = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription(PICTURE), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun asset(id: String, role: String = "PRIMARY", color: Int = Color.WHITE,
                      width: Int = 800, height: Int = 360,
                      lines: List<String> = listOf("1. 已知函数 f(x) = x² - 4x + 5，", "求函数的最小值及对应的 x。", "请写出计算过程。")): MistakeSourceAsset {
        val file = File.createTempFile("inline-$id-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        if (color == Color.WHITE) {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK; textSize = 30f }
            lines.forEachIndexed { index, text -> canvas.drawText(text, 32f, 64f + index * 60f, paint) }
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return MistakeSourceAsset(role, id, sha, "image/png", file.length(), width, height, "CAMERA", 1L,
            MistakeSourceLocation.Available(file.toURI().toASCIIString()))
    }

    private fun entry() = StudyCatalogEntry("entry", "problem", "revision-1", "practice", "MATH", "函数最值",
        "求函数最值。", null, false, emptyList(), emptyList(), nextReviewAtEpochMillis = null, retrievability = null)

    private fun detail(assets: List<MistakeSourceAsset>, revision: String = "revision-1",
                       legacy: Boolean = false, readingOrder: List<MistakeSourceAsset> = assets,
                       item: StudyCatalogEntry = entry()): MistakeDetailState {
        val detail = MistakeDetail(MistakeDetailIdentity(item.entryId, item.problemId, revision, 1, item.title, item.subject),
            item.problemMarkdown, if (assets.isEmpty()) MistakeSourceSet.Missing else MistakeSourceSet.Present(assets))
        if (legacy) return MistakeDetailState.Legacy(detail)
        val sources = readingOrder.map<MistakeSourceAsset, MistakeSourceAsset?> { it }.ifEmpty { listOf(null) }
        return MistakeDetailState.Ready(detail, CapturedQuestionDocument(
            document = QuestionDocument("question-$revision", blocks = sources.indices.map {
                ContentBlock.Paragraph("stem-$it", item.problemMarkdown)
            }),
            blockEvidence = sources.mapIndexed { index, asset ->
                QuestionBlockEvidence("stem-$index", asset?.sourceAssetId.orEmpty(),
                    sourceRegion = asset?.let { NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0) },
                    writingLayer = WritingLayer.PRINTED,
                    provenance = if (asset == null) QuestionBlockProvenance.USER_TRANSCRIPTION else QuestionBlockProvenance.USER_CORRECTION,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED)
            }))
    }

    private class PictureRepository(var state: MistakeDetailState) : MistakeDetailRepository {
        var reads = 0
        var favorites = 0
        override fun observe(errorBookEntryId: String): Flow<MistakeDetailState> {
            require(errorBookEntryId == "entry"); reads++; return flowOf(state)
        }
        override fun observeExact(key: MistakeRevisionKey) = flowOf(state)
        override suspend fun readExact(key: MistakeRevisionKey) = state
        override suspend fun setFavorite(entryId: String, isFavorite: Boolean, updatedAtEpochMillis: Long): Boolean {
            favorites++; return true
        }
    }

    private class SinglePageCatalog(private val failFirstLoad: Boolean = false, private val reportedTotal: Int = 1) : LibraryCatalogRepository {
        var loadAttempts = 0
            private set
        private val item = LibraryCatalogItem("entry", "函数最值", "求函数最值。", "MATH", emptyList(), emptyList(),
            "learning", 1L, 1L, nextReviewAtEpochMillis = null, retrievability = null)
        override fun pagingSource(query: LibraryQuery) = object : PagingSource<Int, LibraryCatalogItem>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LibraryCatalogItem> {
                loadAttempts += 1
                return if (failFirstLoad && loadAttempts == 1) LoadResult.Error(IllegalStateException("test-private-error"))
                    else LoadResult.Page(listOf(item), null, null)
            }
            override fun getRefreshKey(state: PagingState<Int, LibraryCatalogItem>): Int? = null
        }
        override suspend fun totalCount(query: LibraryQuery) = reportedTotal
        override suspend fun query(query: LibraryQuery, offset: Int, limit: Int) = LibraryCatalogPage(listOf(item).drop(offset).take(limit), 1, offset, limit)
        override suspend fun facets(query: LibraryQuery, facet: LibraryFacetKind) = emptyList<LibraryFacetCount>()
    }

    companion object { private const val PICTURE = "函数最值，第1张题图" }
}
