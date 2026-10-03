package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L6 导航级证据：错题本与智能体的录入入口**同指唯一录入入口**，方式选择可达；
 * 整卷/PDF 是录入流内部的一步，不再是错题本栏的并列入口。
 *
 * 覆盖的入口：
 * 1. 错题本栏主按钮（有题态，本类第一条用例）
 * 2. 错题本空态按钮（`RootTutorFailClosedInstrumentedTest` 的空态用例，同一条入口）
 * 3. 智能体大厅能力目录（本类第二条用例）
 * 第四类"历史文字会话"的能力目录只在**会话快照载入前的空窗帧**可达（
 * `TutorLobbyRoute` 的 `emptyStateVisible = messages.isEmpty() && tasks.isEmpty()`；历史
 * 列表又只列 `messageCount > 0` 的会话，快照到位后空态即让位），这一帧不稳定、未做仪器化；
 * 它的接线仍走同一个入口（`SmartMistakeBookRoot` 里两处 `onCapture` 都指向
 * `Routes.capture(TUTOR)`）。
 */
@RunWith(AndroidJUnit4::class)
class CaptureEntryNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedCuratedFixture() {
        runBlocking {
            val application = composeRule.activity.application as SmartMistakeBookApplication
            seedAppStudyFacts(
                context = application,
                database = application.studyDatabase,
                databaseName = StudyDatabaseFactory.DEFAULT_DATABASE_NAME,
                bundle = AppCuratedStudyFixture.bundle(includeTutorMistake = true),
            )
            application.studyRepository.refresh()
        }
    }

    @Test
    fun libraryEntryOpensTheSingleCaptureEntryAndTheFileStep() {
        waitForTag("root_review")
        composeRule.onNodeWithTag("nav_library").performClick()
        waitForTag("root_library")

        // 改前的两个并列入口（拍照按钮 + 批量导入行）只剩一个「录入」。
        composeRule.onAllNodesWithTag("library_batch_import").assertCountEquals(0)
        composeRule.onNodeWithTag("library_capture_button")
            .performScrollTo()
            .performClick()
        waitForTag("capture_screen")
        assertModeChooser(withFiles = true)
        // 整卷/PDF = 录入内部的一步（改前它是错题本栏并列的"批量导入"入口）。
        composeRule.onNodeWithTag("capture_mode_files").performClick()
        waitForTag("batch_import_root")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForTag("capture_screen")
        composeRule.onNodeWithTag("capture_back_button").performClick()
        waitForTag("root_library")
        composeRule.onAllNodesWithTag("library_batch_import").assertCountEquals(0)
    }

    @Test
    fun tutorEntryOpensTheSameCaptureEntryWithItsOwnModes() {
        waitForTag("root_review")
        composeRule.onNodeWithTag("nav_tutor").performClick()
        waitForTag("root_tutor")

        composeRule.onNodeWithTag("tutor_capture_shortcut")
            .performScrollTo()
            .performClick()
        waitForTag("capture_screen")
        // 同一个录入屏与方式选择；讲题来源不提供整卷/PDF（终点是讲解，整卷管线落点是
        // 错题本入库——能力说明见 CaptureScreen 的 onOpenFileImport KDoc）。
        assertModeChooser(withFiles = false)
        composeRule.onNodeWithTag("capture_back_button").performClick()
        waitForTag("root_tutor")
    }

    private fun assertModeChooser(withFiles: Boolean) {
        composeRule.onNodeWithTag("capture_mode_chooser").assertExists()
        composeRule.onNodeWithTag("capture_take_picture_button").assertExists()
        composeRule.onNodeWithTag("capture_pick_photo_button").assertExists()
        if (withFiles) {
            composeRule.onNodeWithTag("capture_mode_files").assertExists()
        } else {
            composeRule.onAllNodesWithTag("capture_mode_files").assertCountEquals(0)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
