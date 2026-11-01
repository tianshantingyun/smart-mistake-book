package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-04（批次 38）预览写侧的 JVM 面：钉住两件一旦漂移就是真实事故的事——
 *
 * 1. **source_type 唯一来源**：写侧落库的 `source_type` 必须逐字等于
 *    `StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW`。消灭的失败：调用点各写字符串
 *    字面量后与六站点判定/治理口径漂开（同一语义两份写法）。
 * 2. **登记行与字节同体**：登记口收到的必须是"persist 返回的那一条记录"（同一引用，
 *    字段逐位一致）。消灭的失败：字节落盘、资产行没登记（或登记了改过的副本）——
 *    快照提交被 RESTRICT FK 拒绝，或者引用与行内容互相说谎。
 *
 * 另钉空字节在**任何写入口之前**拒绝。seam 是注入式 fake（本类不触 vault/Context），
 * 与 `TutorVisualCommitRepository` 的注入面同一风格。
 */
class TutorVisualPreviewAssetPersisterTest {

    private val previewPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
    private val now = 1_700_000_000_000L

    @Test
    fun `persist stamps the shared local visual preview source type and registers the same record`() =
        runBlocking {
            val persisted = mutableListOf<CanonicalSourceAssetRecord>()
            val registered = mutableListOf<CanonicalSourceAssetRecord>()
            val persister = TutorVisualPreviewAssetPersister(
                persistPngBytes = { bytes, sourceType, createdAtEpochMillis ->
                    assertArrayEquals("写侧必须收到调用方的字节", previewPng, bytes)
                    persisted += canonicalRecord(sourceType, createdAtEpochMillis)
                    persisted.single()
                },
                registerRow = { record -> registered += record },
            )

            val result = persister.persist(previewPng, now)

            assertEquals(
                "source_type 必须来自共享常量（单一来源）",
                StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW,
                result.sourceType,
            )
            assertEquals("时间戳必须原样转发", now, result.createdAtEpochMillis)
            assertTrue(
                "登记口收到的必须是 persist 返回的同一条记录",
                registered.single() === result,
            )
        }

    @Test
    fun `empty preview bytes are rejected before any write`() = runBlocking {
        var writeAttempts = 0
        val persister = TutorVisualPreviewAssetPersister(
            persistPngBytes = { _, _, _ ->
                writeAttempts += 1
                canonicalRecord(StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW, now)
            },
            registerRow = { },
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { persister.persist(ByteArray(0), now) }
        }

        assertTrue(
            "拒绝原因必须指向空预览字节：${failure.message}",
            failure.message.orEmpty().contains("non-empty"),
        )
        assertEquals("空字节必须在任何写入口之前拒绝", 0, writeAttempts)
    }

    @Test
    fun `register failure propagates - half preview is not a committable snapshot input`() =
        runBlocking {
            val persister = TutorVisualPreviewAssetPersister(
                persistPngBytes = { _, sourceType, createdAtEpochMillis ->
                    canonicalRecord(sourceType, createdAtEpochMillis)
                },
                registerRow = { error("资产行登记失败（模拟 DB 故障）") },
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                runBlocking { persister.persist(previewPng, now) }
            }

            assertTrue(
                "登记失败必须原样穿透（不得吞成假成功）：${failure.message}",
                failure.message.orEmpty().contains("资产行登记失败"),
            )
        }

    private fun canonicalRecord(
        sourceType: String,
        createdAtEpochMillis: Long,
    ): CanonicalSourceAssetRecord = CanonicalSourceAssetRecord(
        sourceAssetId = "asset-d04f3ed7038cd1d34e4b0d3f3d3f2f21",
        contentSha256 = "d04f3ed7038cd1d34e4b0d3f3d3f2f21".repeat(2),
        relativePath = "source-assets/d04f3ed7038cd1d34e4b0d3f3d3f2f21.png",
        mimeType = "image/png",
        byteSize = previewPng.size.toLong(),
        width = 2,
        height = 2,
        sourceType = sourceType,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
