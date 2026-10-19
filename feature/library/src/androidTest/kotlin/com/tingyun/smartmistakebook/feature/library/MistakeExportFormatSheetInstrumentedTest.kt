package com.tingyun.smartmistakebook.feature.library

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 原生单题/批量共用的格式 sheet（PRD-04 / T4）。
 *
 * 三件事必须在界面上真的成立：默认 PDF、选 Word 后确认交出的就是 DOCX、取消不发起导出。
 * 设备全链（真实保存/分享、无 Key/断网/401/429）由 D1 执行；这里只钉 sheet 的行为与它交给
 * 装配处的参数。
 */
@RunWith(AndroidJUnit4::class)
class MistakeExportFormatSheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSheetDefaultsToPdfAndConfirmsPdfWithoutAnyChoice() {
        var confirmed: MistakeExportFormat? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportFormatSheet(
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_export_format_sheet").assertExists()
        // 默认选中必须看得见：PDF 的 chip 报"已选"，Word 不报。
        composeRule.onNodeWithTag("mistake_export_format_option_pdf")
            .assertContentDescriptionEquals("PDF（已选）")
        composeRule.onNodeWithTag("mistake_export_format_option_docx")
            .assertContentDescriptionEquals("Word 文档")
        // 选 PDF 时不显示 Word 的能力限制说明（不吓唬人，也不假装 Word 与 PDF 一样）。
        composeRule.onNodeWithTag("mistake_export_format_docx_notice").assertDoesNotExist()

        composeRule.onNodeWithTag("mistake_export_format_confirm").performClick()

        composeRule.runOnIdle { assertEquals(MistakeExportFormat.PDF, confirmed) }
    }

    @Test
    fun selectingWordPutsDocxIntoTheExportRequest() {
        var confirmed: MistakeExportFormat? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportFormatSheet(
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_export_format_option_docx").performClick()
        composeRule.onNodeWithTag("mistake_export_format_option_docx")
            .assertContentDescriptionEquals("Word 文档（已选）")
        composeRule.onNodeWithTag("mistake_export_format_option_pdf")
            .assertContentDescriptionEquals("PDF")
        // 选了 Word 才如实说明它与 PDF 的差异（图形以文字说明、无作答区、不能直接打印）。
        composeRule.onNodeWithTag("mistake_export_format_docx_notice").assertExists()

        composeRule.onNodeWithTag("mistake_export_format_confirm").performClick()

        composeRule.runOnIdle {
            assertEquals(MistakeExportFormat.DOCX, confirmed)
            // 与 I1 的入队调用同一参数：格式原样进请求，不在装配处回落成 PDF。
            val request = MistakeExportJobRequest.Batch(
                exportId = "export:selection",
                entryIds = listOf("entry-1"),
                format = requireNotNull(confirmed),
            )
            assertEquals(MistakeExportFormat.DOCX, request.format)
        }
    }

    @Test
    fun cancellingDismissesWithoutStartingAnExport() {
        var dismissed = false
        var confirmed: MistakeExportFormat? = null
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportFormatSheet(
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                )
            }
        }

        composeRule.onNodeWithTag("mistake_export_format_cancel").performClick()

        composeRule.runOnIdle {
            assertTrue("取消必须回调 onDismiss", dismissed)
            assertNull("取消不得入队任何格式", confirmed)
        }
    }

    @Test
    fun aSubtitleNamesWhatIsBeingExported() {
        composeRule.setContent {
            SmartMistakeBookTheme {
                MistakeExportFormatSheet(
                    subtitle = "导出当前筛选的 12 道题",
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithTag("mistake_export_format_subtitle")
            .assertTextEquals("导出当前筛选的 12 道题")
    }
}
