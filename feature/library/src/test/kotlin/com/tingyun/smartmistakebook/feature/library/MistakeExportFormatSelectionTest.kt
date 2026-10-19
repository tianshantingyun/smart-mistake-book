package com.tingyun.smartmistakebook.feature.library

import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 原生导出格式 sheet 的纯逻辑（PRD-04 / T4）。
 *
 * 这个类钉住两件在 JVM 上就能说清的事：
 * 1. 打开 sheet 的默认格式是 PDF；保存状态里缺失/损坏/未知的代号也必须回落到 PDF
 *    （不能因为一次坏状态把学生上一次选的 Word 变成默认）。
 * 2. sheet 确认回调交出的格式，就是入队请求携带的格式——中间不换值、不丢失。
 *    worker 输入/记录/缓存的完整链路由 `ExportPdfWorkerTest`（app JVM）与
 *    `MistakeExportJobRunnerTest`（core:export）继续往下钉。
 */
class MistakeExportFormatSelectionTest {

    @Test
    fun `an absent or unreadable saved selection falls back to pdf`() {
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection(null))
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection(""))
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection("   "))
        // 未来版本/损坏状态里的未知代号：不崩溃、不猜成 Word。
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection("odt"))
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection("PDF"))
    }

    @Test
    fun `both known tokens survive the saved selection`() {
        assertEquals(MistakeExportFormat.PDF, mistakeExportFormatSelection("pdf"))
        assertEquals(MistakeExportFormat.DOCX, mistakeExportFormatSelection("docx"))
    }

    @Test
    fun `the confirmed format is carried into both native export request kinds`() {
        val key = MistakeRevisionKey("entry-1", "problem-1", "revision-3")

        // 请求类型的缺省格式 = PDF（既有原生入口行为不变）。
        assertEquals(
            MistakeExportFormat.PDF,
            MistakeExportJobRequest.Single(exportId = "export:default", key = key).format,
        )

        val singlePdf = MistakeExportJobRequest.Single(
            exportId = "export:single",
            key = key,
            format = mistakeExportFormatSelection(null),
        )
        assertEquals(MistakeExportFormat.PDF, singlePdf.format)

        val batchDocx = MistakeExportJobRequest.Batch(
            exportId = "export:batch",
            entryIds = listOf("entry-1", "entry-2"),
            format = mistakeExportFormatSelection("docx"),
        )
        assertEquals(MistakeExportFormat.DOCX, batchDocx.format)
    }
}
