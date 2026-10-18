package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeExportRecord
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交付格式的持久/分流口径（6B-1）：记录行 → 格式（零 schema 的扩展名判据）、文件名扩展名、
 * 失败文案分叉与 token 占位。
 */
class MistakeExportFormatTest {

    @Test
    fun `the delivery format is read from the persisted file name extension`() {
        assertEquals(MistakeExportFormat.DOCX, succeededRecord("错题-函数-第3版.docx").deliveryFormat())
        assertEquals(MistakeExportFormat.PDF, succeededRecord("错题-函数-第3版.pdf").deliveryFormat())
        // 旧记录/异常名/进行中的行（displayName 为 null）都按 PDF——旧的交付动作不会换链。
        assertEquals(MistakeExportFormat.PDF, succeededRecord("错题-函数-第3版").deliveryFormat())
        assertEquals(MistakeExportFormat.PDF, succeededRecord(null).deliveryFormat())
        // 大小写不敏感（文件名可能被系统改写扩展名大小写）。
        assertEquals(MistakeExportFormat.DOCX, succeededRecord("错题-函数-第3版.DOCX").deliveryFormat())
    }

    @Test
    fun `format tokens are stable, unknown tokens are refused and missing ones default to pdf`() {
        assertEquals(MistakeExportFormat.DOCX, MistakeExportFormat.fromToken("docx"))
        assertEquals(MistakeExportFormat.PDF, MistakeExportFormat.fromToken("pdf"))
        // 坏值返回 null：调用方（codec）据此整条拒，不许静默当 PDF。
        assertNull(MistakeExportFormat.fromToken("doc"))
        assertNull(MistakeExportFormat.fromToken(""))
        assertNull(MistakeExportFormat.fromToken(null))
        // token 不跟枚举名走：改名/改大小写都不会换持久值。
        assertEquals("pdf", MistakeExportFormat.PDF.token)
        assertEquals("docx", MistakeExportFormat.DOCX.token)
    }

    @Test
    fun `display names carry the extension of the chosen format and keep the pdf default`() {
        assertEquals("错题-函数单调区间-第3版.pdf", exportDisplayName("函数单调区间", 3))
        assertEquals("错题-函数单调区间-第3版.docx", exportDisplayName("函数单调区间", 3, MistakeExportFormat.DOCX))
        assertEquals("错题练习-2道.pdf", batchExportDisplayName(2))
        assertEquals("错题练习-2道.docx", batchExportDisplayName(2, MistakeExportFormat.DOCX))
        // 非法路径字符的清洗与格式无关（Word 与 PDF 同一口径）。
        assertEquals("错题-a b-第1版.docx", exportDisplayName("a/b", 1, MistakeExportFormat.DOCX))
    }

    @Test
    fun `failure copy speaks about the artifact the student actually asked for`() {
        assertEquals(
            "这道题的内容超出了单题 A4 导出的范围，题目仍会原样保留。",
            exportFailureMessage(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED),
        )
        assertEquals(
            "这道题的内容超出了单题 Word 文档的范围，题目仍会原样保留。",
            exportFailureMessage(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED, MistakeExportFormat.DOCX),
        )
        assertEquals(
            "这次没有整理出完整的 A4 文件，请稍后重新导出。",
            exportFailureMessage(MistakePdfExportFailure.FILE_PUBLISH_FAILED),
        )
        assertEquals(
            "这次没有整理出完整的 Word 文档，请稍后重新导出。",
            exportFailureMessage(MistakePdfExportFailure.FILE_PUBLISH_FAILED, MistakeExportFormat.DOCX),
        )
        assertEquals(
            "当前结果的页数较多。缩小筛选范围后再导出，错题内容不会改变。",
            batchExportFailureMessage(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED),
        )
        assertEquals(
            "当前结果的内容较多。缩小筛选范围后再导出，错题内容不会改变。",
            batchExportFailureMessage(MistakePdfExportFailure.PAGE_LIMIT_EXCEEDED, MistakeExportFormat.DOCX),
        )
    }

    /** Word 记录的 pageCount 是占位值；它必须能过 token 的三段持久化（成果入口按它取回产物）。 */
    @Test
    fun `the docx record page placeholder keeps the persisted token well formed`() {
        assertEquals(1, MISTAKE_DOCX_RECORD_PAGE_PLACEHOLDER)
        val token = PreparedMistakePdfToken(
            inputSha256 = "a".repeat(64),
            pdfSha256 = "b".repeat(64),
            pageCount = MISTAKE_DOCX_RECORD_PAGE_PLACEHOLDER,
        )
        assertEquals(
            token,
            PreparedMistakePdfToken.fromPersistedValue(token.toPersistedValue()),
        )
        assertTrue(token.isWellFormed())
    }

    private fun succeededRecord(displayName: String?): MistakeExportRecord = MistakeExportRecord(
        exportId = "done-1",
        kind = MistakeExportKind.SINGLE,
        displayName = displayName,
        status = if (displayName == null) MistakeExportStatus.FAILED else MistakeExportStatus.SUCCEEDED,
        inputSha256 = if (displayName == null) null else "a".repeat(64),
        pdfSha256 = if (displayName == null) null else "b".repeat(64),
        pageCount = if (displayName == null) null else 2,
        failureMessage = if (displayName == null) "失败" else null,
        createdAtEpochMillis = 1,
        finishedAtEpochMillis = 2,
    )
}
