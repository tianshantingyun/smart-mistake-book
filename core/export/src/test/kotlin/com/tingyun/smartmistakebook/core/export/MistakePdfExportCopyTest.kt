package com.tingyun.smartmistakebook.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出文案与文件名的 JVM 契约（L7 从 feature/library 的导出页迁来）。
 *
 * 迁移说明：改前这些断言在 `MistakeExportUiPolicyTest` 里（导出页是唯一消费者）；
 * 现在后台 worker（写失败原因 / 通知）与成果入口共用这一份，断言跟着实现走，
 * 一列不丢。
 */
class MistakePdfExportCopyTest {

    @Test
    fun `every blocked reason has plain student facing copy`() {
        MistakePdfIneligibility.entries.forEach { reason ->
            assertTrue(exportBlockedMessage(setOf(reason)).isNotBlank())
        }

        val figureMessage = exportBlockedMessage(
            setOf(
                MistakePdfIneligibility.UNSUPPORTED_BLOCK,
                MistakePdfIneligibility.UNCONFIRMED_OR_INVALID_DOCUMENT,
            ),
        )
        assertTrue(figureMessage.contains("图形"))
        assertTrue(figureMessage.contains("避免"))
    }

    @Test
    fun `every render failure and batch reason has copy`() {
        MistakePdfExportFailure.entries.forEach { failure ->
            assertTrue(exportFailureMessage(failure).isNotBlank())
        }
        MistakePdfBatchIneligibility.entries.forEach { reason ->
            assertTrue(batchBlockedMessage(reason).isNotBlank())
        }
        MistakePdfExportFailure.entries.forEach { failure ->
            assertTrue(batchExportFailureMessage(failure).isNotBlank())
        }
    }

    @Test
    fun `the batch copy no longer promises the retired knowledge filter`() {
        // L5 删掉了"知识点"筛选层：文案若还让用户去点一个不存在的筛选，就是假路标。
        val tooMany = batchBlockedMessage(MistakePdfBatchIneligibility.TOO_MANY_QUESTIONS)
        assertFalse(tooMany.contains("知识点"))
        assertTrue(tooMany.contains("录入时间段"))
    }

    @Test
    fun `displayNameRemovesUnsafePathCharactersAndPinsRevision`() {
        val displayName = exportDisplayName("  函数/A:B?  \n", 7)

        assertEquals("错题-函数 A B-第7版.pdf", displayName)
        assertFalse(displayName.contains('/'))
        assertTrue(displayName.endsWith(".pdf"))
        assertEquals("错题-未命名错题-第1版.pdf", exportDisplayName("   ", 1))
    }

    @Test
    fun `batch display name carries the included count`() {
        assertEquals("错题练习-2道.pdf", batchExportDisplayName(2))
    }
}
