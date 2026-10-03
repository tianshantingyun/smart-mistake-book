package com.tingyun.smartmistakebook

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OutOfQuotaPolicy
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **expedited 形态的真断言**（阶段 4A 批 4 · L7；复核 P2：此前只有读码，没有断言）。
 *
 * `WorkInfo` 不暴露 expedited，所以这里直接检查**已构造 WorkRequest 的可观测形态**：
 * `WorkRequest.workSpec` 的 `expedited` 与 `outOfQuotaPolicy` 字段（WorkManager 2.10.5 的
 * 实际存储位置；反射取，库升级后字段消失会在这里炸，而不是静默退回普通任务）。
 *
 * 形态来源：计划 §2.1（秒级本地渲染用 expedited 普通 Worker，配额不足降级而不是丢单）。
 */
@RunWith(AndroidJUnit4::class)
class ExportPdfDriverInstrumentedTest {

    @Test
    fun theBuiltRequestIsExpeditedAndDegradesInsteadOfDropping() {
        val request = ExportPdfDriver.request(
            MistakeExportJobRequest.Single(
                exportId = "export:expedited-shape",
                key = MistakeRevisionKey(
                    entryId = "entry-shape",
                    problemId = "problem-shape",
                    problemRevisionId = "revision-shape",
                ),
            ),
        )

        val workSpec = request.javaClass.getMethod("getWorkSpec").invoke(request)
        val expedited = workSpec.javaClass.getField("expedited").getBoolean(workSpec)
        assertTrue(
            "expedited 必须真的落在 WorkRequest 上（否则只是注释里的形态）",
            expedited,
        )
        assertEquals(
            "配额不足时降级为普通任务，不取消（丢单）",
            OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            workSpec.javaClass.getField("outOfQuotaPolicy").get(workSpec),
        )
        assertTrue(
            "任务必须带本功能的 tag（诊断/取消按它过滤）",
            ExportPdfDriver.TAG in request.tags,
        )
        assertEquals(
            "唯一名按 export_id 命名",
            MistakeExportJobCodec.uniqueName("export:expedited-shape"),
            "mistake-export-export:expedited-shape",
        )
    }
}
