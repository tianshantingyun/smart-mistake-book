package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.feature.library.LibraryExportCandidates
import com.tingyun.smartmistakebook.feature.library.MAX_LIBRARY_BATCH_EXPORT_QUESTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 库页导出动作的三态分流（L7）：
 * `Candidates` 入队、**不**产生提示；超限与"没有可导出的题"各自给出如实的说明。
 *
 * 它钉住的失败：改前"超过上限"与"当前没有题"都收敛成空列表，导出页把超限说成
 * "当前没有可导出的错题"——一个会让用户白找筛选器的谎。
 */
class LibraryExportNoticeTest {

    @Test
    fun `candidates are queued without any rejection notice`() {
        assertNull(
            libraryExportRejectionNotice(
                LibraryExportCandidates.Candidates(listOf("entry-1", "entry-2")),
            ),
        )
    }

    @Test
    fun `too many visible names the limit and the filters that still exist`() {
        val notice = libraryExportRejectionNotice(LibraryExportCandidates.TooManyVisible)

        assertTrue("必须点明上限：$notice", notice!!.contains(MAX_LIBRARY_BATCH_EXPORT_QUESTIONS.toString()))
        assertTrue("必须给可执行的出路：$notice", notice.contains("筛选"))
        assertTrue("不许再提已退场的知识点筛选", !notice.contains("知识点"))
        assertTrue(notice.contains("录入时间段"))
    }

    @Test
    fun `nothing visible says exactly that`() {
        assertEquals(
            "当前没有可导出的错题。",
            libraryExportRejectionNotice(LibraryExportCandidates.NothingVisible),
        )
    }
}
