package com.tingyun.smartmistakebook

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-06 设备用例（真实空库、真实接线，不用假仓库）：
 * 复习首页空态 CTA → 唯一录入入口（错题本来源）→「手动建题」真能落进可编辑的手动草稿。
 *
 * 钉住的三件事：
 * 1. 空态只在快照 READY 且 `savedQuestionCount == 0` 时出现（Root 传数、ReviewRoute 判态）；
 *    加载中/出错传 null，不会把"还不知道"说成"还没有题"。
 * 2. CTA 与错题本栏同指一个入口（`CaptureEntryOrigin.LIBRARY`）：整卷/PDF 与手动建题都在，
 *    不会误进仍要求照片的讲题入口。
 * 3. 手动建题是纯本地路径：进入编辑表单、结束语是"只存入错题本，不会自动开始讲解"，
 *    全程 `model_task` 一行都不写 = 没有新增模型调用。
 *
 * 本用例会清空**已安装应用的真实数据库**以构造"首装空库"，因此默认跳过；只在专用测试
 * 设备上以 `-e smb_allow_data_reset true` 显式开启（见 [DestructiveDeviceTestGuard]）。
 */
@RunWith(AndroidJUnit4::class)
class Prd06EmptyLibraryCaptureCtaInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startFromATrulyEmptyLibrary() {
        // 清的是已安装应用的真实库：没有显式开关就跳过（见 DestructiveDeviceTestGuard）。
        DestructiveDeviceTestGuard.requireExplicitOptIn()
        val application = composeRule.activity.application as SmartMistakeBookApplication
        runBlocking {
            application.studyDatabase.clearAllData()
            application.studyRepository.refresh()
        }
    }

    @Test
    fun emptyLibraryCtaReachesTheLibraryManualEntryWithoutAModelDispatch() {
        waitForTag("root_review")
        // 快照 READY + 题数 0 才会渲染这条 CTA；它存在本身就是"空库判定已接线"的证据。
        waitForTag("review_capture_button")
        composeRule.onNodeWithText("还没有可复习的题").assertExists()
        composeRule.onNodeWithTag("review_start_button").assertExists()

        composeRule.onNodeWithTag("review_capture_button")
            .performScrollTo()
            .performClick()

        waitForTag("capture_screen")
        waitForTag("capture_mode_chooser")
        // 错题本来源的入口特征：整卷/PDF 与手动建题都在（讲题来源两者都不提供）。
        composeRule.onNodeWithTag("capture_mode_files").assertExists()
        composeRule.onNodeWithTag("capture_mode_manual")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("手动建题").assertExists()

        composeRule.onNodeWithTag("capture_mode_manual").performClick()

        // 不是"点得到一个空壳"：草稿水合后，手动编辑表单与提交键都真的在。
        waitForTag("capture_correction_form")
        waitForTag("capture_commit_button")
        waitForText("手动建题")
        composeRule.onNodeWithText("只存入错题本，不会自动开始讲解。").assertExists()
        // 手动建题不经过模型链：不出现"去设置模型"块，也不生成模型任务卡。
        composeRule.onAllNodesWithTag("capture_model_setup_block").assertCountEquals(0)
        composeRule.onAllNodesWithTag("capture_model_task_card").assertCountEquals(0)
        assertEquals(
            "手动建题必须是纯本地路径：这条链不允许新增任何模型调用",
            0,
            countModelTaskRows(),
        )
    }

    /** 只读证据：模型任务表在本用例的整条链走完后仍为空。 */
    private fun countModelTaskRows(): Int {
        val application = composeRule.activity.application as SmartMistakeBookApplication
        val path = application
            .getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            .absolutePath
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM `model_task`", null).use { cursor ->
                check(cursor.moveToFirst()) { "COUNT(*) on model_task returned no row" }
                cursor.getInt(0)
            }
        }
    }

    private fun waitForTag(tag: String) {
        waitUntil { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String) {
        waitUntil {
            composeRule.onAllNodesWithText(text, substring = false)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000, condition = condition)
    }
}
