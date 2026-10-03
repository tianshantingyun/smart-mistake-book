package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「导出成果」入口（L7）的界面：三态如实、动作只在成功行出现、文件被清理时失败要关闭而非静默。
 *
 * 真实产物的分享/保存走既有交付链的仪器化覆盖在 app 侧（`MistakeExportDeliveryInstrumentedTest`，
 * 用真 exporter 产出的 PDF 断言 SEND / CREATE_DOCUMENT 意图），这里用假记录钉界面分流。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportHubInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runningRecordShowsProgressWithoutDeliveryActions() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(
                    records = listOf(runningRecord()),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("正在整理 A4 版式…").assertExists()
        composeRule.onNodeWithTag("export_hub_record_status_running-1").assertExists()
        composeRule.onNodeWithTag("export_hub_save_running-1").assertDoesNotExist()
        composeRule.onNodeWithTag("export_hub_share_running-1").assertDoesNotExist()
        composeRule.onNodeWithTag("export_hub_print_running-1").assertDoesNotExist()
    }

    @Test
    fun succeededRecordOffersDeliveryWhileFailedRecordStatesItsReason() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(
                    records = listOf(succeededRecord(), failedRecord()),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("错题-函数单调区间-第3版.pdf").assertExists()
        composeRule.onNodeWithText("已完成 · 共 2 页", substring = true).assertExists()
        composeRule.onNodeWithTag("export_hub_save_done-1").assertExists()
        composeRule.onNodeWithTag("export_hub_share_done-1").assertExists()
        composeRule.onNodeWithTag("export_hub_print_done-1").assertExists()

        composeRule.onNodeWithText("这版题面有空白内容，暂时无法生成完整的练习页。").assertExists()
        composeRule.onNodeWithTag("export_hub_save_failed-1").assertDoesNotExist()
    }

    @Test
    fun aCleanedUpArtifactFailsClosedWithAnHonestMessageOnSave() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(
                    records = listOf(succeededRecord(exportId = "cleaned-1")),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("export_hub_save_cleaned-1").performClick()

        composeRule.onNodeWithText("文件已清理，无法保存。请重新导出这一份。").assertExists()
    }

    @Test
    fun anEmptyHubExplainsWhereResultsComeFrom() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(records = emptyList(), onBack = {})
            }
        }

        composeRule.onNodeWithTag("mistake_export_hub_empty").assertExists()
    }

    @Test
    fun aNoticeIsShownAndCanBeDismissed() {
        var dismissed = false
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportHubRoute(
                    records = emptyList(),
                    notice = "当前结果超过 100 道。",
                    onDismissNotice = { dismissed = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("mistake_export_hub_notice").assertExists()
        composeRule.onNodeWithText("当前结果超过 100 道。").assertExists()
        composeRule.onNodeWithTag("mistake_export_hub_notice_dismiss").performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    // ---- fixtures ----

    private fun runningRecord() = MistakeExportRecord(
        exportId = "running-1",
        kind = MistakeExportKind.SINGLE,
        displayName = null,
        status = MistakeExportStatus.RUNNING,
        inputSha256 = null,
        pdfSha256 = null,
        pageCount = null,
        failureMessage = null,
        createdAtEpochMillis = 1_772_000_000_000,
        finishedAtEpochMillis = null,
    )

    private fun succeededRecord(
        exportId: String = "done-1",
        inputSha256: String = "a".repeat(64),
        pdfSha256: String = "b".repeat(64),
    ) = MistakeExportRecord(
        exportId = exportId,
        kind = MistakeExportKind.SINGLE,
        displayName = "错题-函数单调区间-第3版.pdf",
        status = MistakeExportStatus.SUCCEEDED,
        inputSha256 = inputSha256,
        pdfSha256 = pdfSha256,
        pageCount = 2,
        failureMessage = null,
        createdAtEpochMillis = 1_772_000_000_000,
        finishedAtEpochMillis = 1_772_000_001_000,
    )

    private fun failedRecord() = MistakeExportRecord(
        exportId = "failed-1",
        kind = MistakeExportKind.SINGLE,
        displayName = null,
        status = MistakeExportStatus.FAILED,
        inputSha256 = null,
        pdfSha256 = null,
        pageCount = null,
        failureMessage = "这版题面有空白内容，暂时无法生成完整的练习页。",
        createdAtEpochMillis = 1_772_000_000_000,
        finishedAtEpochMillis = 1_772_000_001_000,
    )
}
