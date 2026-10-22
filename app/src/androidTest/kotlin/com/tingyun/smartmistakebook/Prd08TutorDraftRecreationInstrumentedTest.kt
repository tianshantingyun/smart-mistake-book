package com.tingyun.smartmistakebook

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.TutorConversationAreas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-08 设备用例：`ActivityScenario.recreate()` 之后，学生还没发出的草稿还在，且**不会被
 * 自动发出去、也不会顺手写回会话行**。
 *
 * 恢复链的事实来源（读代码核对过）：
 * - 草稿与本地编辑版本经 `SavedStateHandle` 保留（`TutorConversationSavedState.kt`）；
 * - 会话行为空时不建行、不发请求（`TutorConversationViewModel.onDraftChange` 在
 *   `conversationId` 为空时只更新状态）；
 * - 恢复过程不派发回合，发送只由学生按发送键触发。
 *
 * 边界（与计划口径一致）：本用例覆盖的是 **Activity 重建**（配置变更 + 实例状态保存/恢复），
 * 不冒充进程强杀。force-stop / 系统回收后的表现仍按人工走查记录，且产品文案不承诺"所有未
 * 落盘按键在强杀后必恢复"。
 *
 * 本用例会清空**已安装应用的真实数据库**以构造一个没有历史会话的起点，因此默认跳过；
 * 只在专用测试设备上以 `-e smb_allow_data_reset true` 显式开启（见 [DestructiveDeviceTestGuard]）。
 */
@RunWith(AndroidJUnit4::class)
class Prd08TutorDraftRecreationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startWithoutPersistedConversations() {
        // 清的是已安装应用的真实库：没有显式开关就跳过（见 DestructiveDeviceTestGuard）。
        DestructiveDeviceTestGuard.requireExplicitOptIn()
        val application = targetApplication()
        runBlocking {
            application.studyDatabase.clearAllData()
            application.studyRepository.refresh()
        }
    }

    @Test
    fun recreationRestoresTheDraftWithoutSendingOrWritingItBack() {
        waitForTag("nav_tutor")
        composeRule.onNodeWithTag("nav_tutor").performClick()
        waitForTag("root_tutor")
        waitForTag("tutor_chat_composer")

        composeRule.onNodeWithTag("tutor_chat_composer").performTextInput(DRAFT)
        composeRule.onNodeWithTag("tutor_chat_composer").assertTextContains(DRAFT)

        // 起点是"还没发出任何一条消息"：打字本身不建会话行、不派发模型任务。
        assertEquals(0, conversationCount())
        assertEquals(0, countModelTaskRows())

        composeRule.activityRule.scenario.recreate()

        waitForTag("root_tutor")
        waitForTag("tutor_chat_composer")
        // 1) 草稿恢复：屏幕上的字就是重建前那句，不依赖会话行（会话行根本不存在）。
        composeRule.onNodeWithTag("tutor_chat_composer").assertTextContains(DRAFT)
        // 2) 不自动外发：空态仍在（没有凭空多出一轮消息）。
        composeRule.onNodeWithTag("tutor_empty_state").assertExists()
        // 3) 不回写：恢复没有替学生建会话行，也没有落任何模型任务。
        assertEquals(0, conversationCount())
        assertEquals(0, countModelTaskRows())
    }

    private fun conversationCount(): Int = runBlocking {
        targetApplication().tutorConversationRepository
            .observeRecent(limit = 20, area = TutorConversationAreas.AGENT)
            .first()
            .size
    }

    private fun countModelTaskRows(): Int {
        val path = targetApplication()
            .getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
            .absolutePath
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM `model_task`", null).use { cursor ->
                check(cursor.moveToFirst()) { "COUNT(*) on model_task returned no row" }
                cursor.getInt(0)
            }
        }
    }

    private fun targetApplication(): SmartMistakeBookApplication =
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext as SmartMistakeBookApplication

    private fun waitForTag(tag: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val DRAFT = "还没发出的草稿：recreate 之后这句话应该还在"
    }
}
