package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 后台导出的 WorkManager 接线（JVM 可测的部分）。
 *
 * 与 `BatchImportDriverTest` 同口径：唯一名与策略写错的失败是静默的（重名 + KEEP 会互相压制），
 * 请求编解码写错则会让 worker 渲染另一版题或整单丢失，所以这些都在 JVM 上钉住。
 * expedited 请求的构造需要真实 WorkRequest，归仪器化
 * （`MistakeExportBackgroundInstrumentedTest`）覆盖。
 */
class ExportPdfWorkerTest {

    @Test
    fun `a single request round trips through the work input map`() {
        val request = MistakeExportJobRequest.Single(
            exportId = "export:1",
            key = MistakeRevisionKey(
                entryId = "entry-1",
                problemId = "problem-1",
                problemRevisionId = "revision-3",
            ),
        )

        assertEquals(request, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(request)))
    }

    @Test
    fun `a batch request round trips and keeps entry ids in order`() {
        val request = MistakeExportJobRequest.Batch(
            exportId = "export:2",
            entryIds = listOf("entry-a", "entry-b", "entry-c"),
        )

        assertEquals(request, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(request)))
    }

    @Test
    fun `a batch of a hundred ids stays far below the work data limit`() {
        val request = MistakeExportJobRequest.Batch(
            exportId = "export:3",
            entryIds = (1..100).map { "entry:m1:question-$it" },
        )

        val encoded = MistakeExportJobCodec.encode(request)
        val size = encoded.entries.sumOf { (key, value) ->
            key.length + (value?.toString()?.length ?: 0)
        }
        // WorkManager Data 上限 10KB；批量只带 entry id 就是为了留足余量。
        assertTrue("编码后 $size 字节", size < 5 * 1024)
    }

    @Test
    fun `malformed or unknown inputs decode to null instead of a half request`() {
        assertNull(MistakeExportJobCodec.decode(emptyMap()))
        assertNull(
            MistakeExportJobCodec.decode(
                mapOf(
                    MistakeExportJobCodec.EXPORT_ID_KEY to "export:1",
                    MistakeExportJobCodec.KIND_KEY to "single",
                ),
            ),
        )
        assertNull(
            MistakeExportJobCodec.decode(
                mapOf(
                    MistakeExportJobCodec.EXPORT_ID_KEY to "export:1",
                    MistakeExportJobCodec.KIND_KEY to "someday",
                ),
            ),
        )
        assertNull(
            MistakeExportJobCodec.decode(
                mapOf(
                    MistakeExportJobCodec.EXPORT_ID_KEY to " ",
                    MistakeExportJobCodec.KIND_KEY to "batch",
                    MistakeExportJobCodec.ENTRY_IDS_KEY to "entry-a",
                ),
            ),
        )
    }

    @Test
    fun `unique work is named per export so one record cannot stack two jobs`() {
        assertEquals("mistake-export-export:1", MistakeExportJobCodec.uniqueName("export:1"))
        assertNotEquals(
            MistakeExportJobCodec.uniqueName("export:1"),
            MistakeExportJobCodec.uniqueName("export:2"),
        )
        // 与其它后台任务的唯一名不撞（重名 + KEEP 会互相压制）。
        assertNotEquals(BatchImportDriver.UNIQUE_NAME, ExportPdfDriver.TAG)
        assertNotEquals(OrphanAssetGc.UNIQUE_NAME, ExportPdfDriver.TAG)
        // KEEP：同一 export_id 重复入队是重放，不开第二次渲染。expedited 形态本身无法在
        // JVM 上构造 WorkRequest，由 `ExportPdfDriverInstrumentedTest` 走真实 WorkRequest 覆盖。
        assertEquals("KEEP", ExportPdfDriver.existingPolicy.name)
    }

    /**
     * P1 回归：就绪门只挡"库没打开 / 打不开"。`RecoverableFailure`（恢复回滚、知识包安装失败）
     * 下数据库是好的，文案明说"错题和复习可以继续使用"——导出必须照常可跑。改前用
     * `!is Ready` 挡门，worker 重试耗尽后静默失败，记录永远停在 RUNNING（"永久正在整理"）。
     */
    @Test
    fun `the worker runs whenever the book is usable and only waits for a closed database`() {
        assertFalse(StartupState.Initializing.allowsMistakeExportWorker())
        assertTrue(StartupState.Ready.allowsMistakeExportWorker())
        assertTrue(
            StartupState.RecoverableFailure(
                title = "备份恢复没有完成",
                message = "已回到恢复之前的数据，错题记录仍然完整。",
                diagnosticId = "startup:restore-reverted:test",
            ).allowsMistakeExportWorker(),
        )
        assertTrue(
            StartupState.RecoverableFailure(
                title = "本地知识包尚未准备好",
                message = "错题和复习可以继续使用，自动分类会暂缓。",
                diagnosticId = "startup:knowledge:test",
                errorCategory = StartupErrorCategory.KNOWLEDGE_BASE,
            ).allowsMistakeExportWorker(),
        )
        assertFalse(
            StartupState.FatalFailure(
                title = "应用数据无法打开",
                message = "数据库初始化失败，暂时不能安全读写学习记录。",
                diagnosticId = "startup:database:test",
            ).allowsMistakeExportWorker(),
        )
    }

    /**
     * 4B 批 4：版式必须随任务到达 worker——sheet 上学生改过的版式与后台产出必须是同一份
     * （版式也是缓存键的一部分）。
     */
    @Test
    fun `a layout survives the work input round trip for both request kinds`() {
        val layout = MistakePdfLayout(
            templateId = MistakePdfLayout.TEMPLATE_PRACTICE_SHEET,
            marginPt = 60,
            fontScale = 3,
            columnCount = 2,
            blockOrder = listOf("paragraph", "choice_group"),
            imageScale = 0.8f,
            includeAnswer = true,
            includeNote = true,
        )

        val batch = MistakeExportJobRequest.Batch(
            exportId = "export:layout",
            entryIds = listOf("entry-a"),
            layout = layout,
        )
        val single = MistakeExportJobRequest.Single(
            exportId = "export:layout-single",
            key = MistakeRevisionKey("entry-a", "problem-a", "revision-a"),
            layout = layout,
        )

        assertEquals(layout, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(batch))?.layout)
        assertEquals(layout, MistakeExportJobCodec.decode(MistakeExportJobCodec.encode(single))?.layout)
    }

    /** 缺版式键 = 改版前入队的旧任务：按默认版式渲染，不整单丢弃（兼容）。 */
    @Test
    fun `an old request without layout keys decodes to the default layout`() {
        val decoded = MistakeExportJobCodec.decode(
            mapOf(
                MistakeExportJobCodec.EXPORT_ID_KEY to "export:old",
                MistakeExportJobCodec.KIND_KEY to "batch",
                MistakeExportJobCodec.ENTRY_IDS_KEY to "entry-a",
            ),
        )

        assertEquals(MistakePdfLayout.DEFAULT, decoded?.layout)
    }

    /** 版式键在、但读不成合法布局：整条请求拒（不拿默认版式冒充学生改过的版式）。 */
    @Test
    fun `a malformed layout rejects the whole request instead of falling back`() {
        val encoded = MistakeExportJobCodec.encode(
            MistakeExportJobRequest.Batch(exportId = "export:bad", entryIds = listOf("entry-a")),
        )

        assertNull(
            MistakeExportJobCodec.decode(
                encoded + (MistakeExportJobCodec.LAYOUT_FONT_SCALE_KEY to 9),
            ),
        )
        assertNull(
            MistakeExportJobCodec.decode(
                encoded + (MistakeExportJobCodec.LAYOUT_TEMPLATE_ID_KEY to "poster"),
            ),
        )
    }

    /** B3-4：请求了但被 fail-closed 跳过的版式功能，结果通知里如实说（计数不蒸发）。 */
    @Test
    fun `the skipped-feature notice speaks only when something was requested and skipped`() {
        assertNull(mistakeExportSkippedNotice(MistakePdfLayout.DEFAULT))
        assertEquals(
            "解析区暂不可用，已跳过。",
            mistakeExportSkippedNotice(MistakePdfLayout(includeSolution = true)),
        )
    }
}
