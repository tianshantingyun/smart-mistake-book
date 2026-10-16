package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-42 的入口提示：整卷录入在 job 落库**之前**被杀时，列表里没有任务、也没有失败页，学生看到的
 * 是「什么都没发生」。staging 目录里那批没被任何任务认领的页面还在，入口就要如实说一句
 * 「上次导入被中断，可重导」。
 *
 * tag `batch_import_interrupted` 同时是设备走查的定位点（造 staging 目录 → 进导入页 → 见提示），
 * 提示为假时它必须不存在——否则每次正常导入都会附带一句假的中断声明。
 */
@RunWith(AndroidJUnit4::class)
class BatchImportInterruptedNoticeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anInterruptedPreviousImportIsStatedOnTheEntry() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                BatchImportContent(
                    job = null,
                    message = null,
                    interruptedImport = true,
                    onChoosePhotos = {},
                    onChoosePdf = {},
                    onPause = {},
                    onResume = {},
                    onRetry = { _, _ -> },
                    onSkip = { _, _ -> },
                    onOpenDraft = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("batch_import_interrupted").assertExists()
        composeRule.onNodeWithText("上次导入被中断，可重导").assertExists()
        // 提示只负责说清楚，不当动作：旁边既有的「选择多张照片」照旧在。
        composeRule.onNodeWithTag("batch_import_choose").assertExists()
    }

    @Test
    fun aCleanEntryCarriesNoInterruptedNotice() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                BatchImportContent(
                    job = null,
                    message = null,
                    onChoosePhotos = {},
                    onChoosePdf = {},
                    onPause = {},
                    onResume = {},
                    onRetry = { _, _ -> },
                    onSkip = { _, _ -> },
                    onOpenDraft = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("batch_import_interrupted").assertDoesNotExist()
        composeRule.onNodeWithText("上次导入被中断，可重导").assertDoesNotExist()
    }
}
