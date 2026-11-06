package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [VisualSnapshotRestoreReadSide] 的 JVM 门（批次 38 V-03 实现缝）。
 *
 * **它消灭的具体失败**：行 → 恢复记录的映射此前没有生产权威侧——V-01 设备链已被迫在测试内
 * 手拼一次（`runChainToRestoredReplay` 自注"测试内映射"）。本套件把映射与恢复门串成一条链钉住：
 *
 * 1. `status → live` 只有 `LIVE` 为真：STAGING 与未知状态一律不可回放（恢复门 1 fail-closed）；
 * 2. `content_ids_json` 严格解析：非数组 / 非字符串元素 / 损坏 JSON 都拒绝，不退成空集
 *    （否则 label 引用复验从"重新校验"退化为"空口无凭"）；
 * 3. 版本列直取不改写：scene/render schema、renderer、字体清单按行原样进恢复门，
 *    映射不得"修好"任何版本；
 * 4. 结构断言：读侧不引用任何 JS 引擎 / HTTP 类型（恢复腿结构上离线，同
 *    `VisualSnapshotRestoreTest` 的反射断言形状）。
 *
 * 与既有 `VisualSnapshotRestoreTest`（12 例，手工构造 record）的差异：本套件的输入是
 * **core/database 的行记录**经映射函数产出——映射本身是被测对象。先红后绿：映射函数先于
 * 本文件落盘（同模块 main），本环境不运行 Gradle，红绿登记为 UNVERIFIED（脚本串行门裁决）。
 */
class VisualSnapshotRestoreReadSideTest {

    private val pathDocument =
        """{"drawSchemaVersion":1,"viewBox":{"x":0.0,"y":0.0,"w":360.0,"h":240.0},""" +
            """"background":null,"ops":[{"op":"path","path":[["M",0.0,0.0],["L",10.0,10.0]],""" +
            """"fill":"#112233","stroke":null,"alpha":1.0}],"hitRegions":[],""" +
            """"stats":{"nodes":1,"pathCommands":2,"labels":0,"ops":1}}"""

    private val labelDocument =
        """{"drawSchemaVersion":1,"viewBox":{"x":0.0,"y":0.0,"w":360.0,"h":240.0},""" +
            """"background":null,"ops":[{"op":"label","contentId":"c1","x":10.0,"y":20.0,""" +
            """"rotationDeg":0.0,"hAlign":"left","vAlign":"top","color":"#111111","alpha":1.0}],""" +
            """"hitRegions":[],"stats":{"nodes":0,"pathCommands":0,"labels":1,"ops":1}}"""

    /** 字体清单落地前，只有显式登记过的清单版本才允许走矢量重放。 */
    private val verifiedFonts = VisualRestorePolicy(verifiedFontManifests = setOf("fonts-v1"))

    private fun snapshotRow(
        status: String = "LIVE",
        sceneSchemaVersion: Int = 1,
        renderSchemaVersion: Int = 1,
        rendererVersion: String = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
        fontManifestVersion: String = "fonts-v1",
        contentIdsJson: String = "[]",
        drawCommandsJson: String = pathDocument,
        previewAssetId: String? = "asset-preview",
    ): TutorVisualSnapshotRecord = TutorVisualSnapshotRecord(
        snapshotId = "snap-read-1",
        programId = "visual-program-read-1",
        stateJson = "{}",
        stateSha256 = "a".repeat(64),
        sceneSchemaVersion = sceneSchemaVersion,
        sceneJson = """{"scene":true}""",
        sceneSha256 = "b".repeat(64),
        renderSchemaVersion = renderSchemaVersion,
        drawCommandsJson = drawCommandsJson,
        drawCommandsSha256 = "c".repeat(64),
        contentIdsJson = contentIdsJson,
        viewportWidthDu = 360,
        viewportHeightDu = 240,
        fontScaleMilli = 1_000,
        theme = "light",
        timeMs = 0,
        seed = 0,
        runtimeVersion = "quickjs-test",
        compilerVersion = "visual-compiler-v1",
        rendererVersion = rendererVersion,
        fontManifestVersion = fontManifestVersion,
        questionVerificationStatus = "NOT_CHECKED",
        previewAssetId = previewAssetId,
        status = status,
        ownerRequestId = null,
        checkpointKind = "LATEST",
        createdAtEpochMillis = 9_000,
        promotedAtEpochMillis = 9_001,
    )

    private fun mapped(row: TutorVisualSnapshotRecord): VisualSnapshotRestoreRecord =
        VisualSnapshotRestoreReadSide.restoreRecordOf(row)

    private fun fallback(outcome: VisualRestoreOutcome): VisualRestoreOutcome.ReadOnly {
        assertTrue("expected a read-only fallback, got $outcome", outcome is VisualRestoreOutcome.ReadOnly)
        return outcome as VisualRestoreOutcome.ReadOnly
    }

    // ------------------------------------------------------------ 映射 → 回放链

    @Test
    fun `a live row maps into a replayable restore record`() {
        val replay = mapped(snapshotRow(drawCommandsJson = labelDocument, contentIdsJson = """["c1"]"""))
        assertTrue(replay.live)
        assertEquals(setOf("c1"), replay.contentIds)

        val outcome = VisualSnapshotRestore.restore(
            record = replay,
            previewVerified = true,
            policy = verifiedFonts,
        )
        assertTrue("expected replay, got $outcome", outcome is VisualRestoreOutcome.Replay)
        val document = (outcome as VisualRestoreOutcome.Replay)
        assertEquals("visual-program-read-1", document.programId)
        assertEquals("snap-read-1", document.snapshotId)
        assertEquals(1, document.document.stats.labels)
        assertEquals(360, document.viewportWidthDu)
        assertEquals("light", document.theme)
    }

    @Test
    fun `content id sets fold duplicates and keep the frozen labels verifiable`() {
        // 写侧编码来自 Set（排序去重）；读回按集合语义折叠重复元素，label 复验仍然成立。
        val replay = mapped(snapshotRow(drawCommandsJson = labelDocument, contentIdsJson = """["c1","c1"]"""))
        assertEquals(setOf("c1"), replay.contentIds)
        assertTrue(
            VisualSnapshotRestore.restore(replay, previewVerified = false, policy = verifiedFonts)
                is VisualRestoreOutcome.Replay,
        )
    }

    // ------------------------------------------------------------ status → live（fail-closed）

    @Test
    fun `a staging row is never live no matter how valid its payload`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(status = "STAGING")),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.SNAPSHOT_NOT_LIVE, outcome.reason)
    }

    @Test
    fun `an unknown status is never live`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(status = "SOMETHING_ELSE")),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.SNAPSHOT_NOT_LIVE, outcome.reason)
    }

    // ------------------------------------------------------------ 版本列直取（映射不改写）

    @Test
    fun `scene schema travels unmapped into the restore gate`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(sceneSchemaVersion = 2)),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.UNKNOWN_SCENE_SCHEMA, outcome.reason)
    }

    @Test
    fun `render schema travels unmapped into the restore gate`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(renderSchemaVersion = 2)),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.UNKNOWN_RENDER_SCHEMA, outcome.reason)
    }

    @Test
    fun `renderer version travels unmapped into the restore gate`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(rendererVersion = "visual-renderer-v0")),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.RENDERER_VERSION_MISMATCH, outcome.reason)
    }

    @Test
    fun `an unverified font manifest still falls back after mapping`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = mapped(snapshotRow(fontManifestVersion = "fonts-unregistered")),
                // 未核对预览字节（本用例注释与 assertNull 钉的正是"未核对 → 不暴露"；
                // restore KDoc/实现：previewAssetId 仅在 previewVerified=true 时透出）。
                previewVerified = false,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.FONT_MANIFEST_UNVERIFIED, outcome.reason)
        // 映射不核对预览字节：previewVerified=false 时不暴露预览（与 restore KDoc 同口径）。
        assertNull(outcome.previewAssetId)
    }

    // ------------------------------------------------------------ content_ids 严格解析

    @Test
    fun `a corrupt content id list is rejected not silently emptied`() {
        val failure = runCatching { mapped(snapshotRow(contentIdsJson = "not-json")) }
        assertTrue(
            "corrupt JSON must fail the mapping: ${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "the message stays bounded, got ${failure.exceptionOrNull()!!.message?.length}",
            (failure.exceptionOrNull()!!.message ?: "").length <= 200,
        )
    }

    @Test
    fun `a non array content id list is rejected`() {
        assertRejectedContentIds("42")
        assertRejectedContentIds("""{"c1":1}""")
    }

    @Test
    fun `non string elements are rejected`() {
        assertRejectedContentIds("[1]")
        assertRejectedContentIds("""["c1",null]""")
        assertRejectedContentIds("""["c1",{"id":"c2"}]""")
    }

    private fun assertRejectedContentIds(contentIdsJson: String) {
        try {
            mapped(snapshotRow(contentIdsJson = contentIdsJson))
            fail("content ids $contentIdsJson must not map into a restore record")
        } catch (expected: IllegalArgumentException) {
            // 有界拒绝：拒绝消息点名契约，而不是泛化 "error"。
            assertTrue(
                "rejection must name the content id contract, got ${expected.message}",
                (expected.message ?: "").contains("content id list"),
            )
        }
    }

    // ------------------------------------------------------------ 结构断言（恢复腿离线）

    @Test
    fun `the read side references no engine or http type`() {
        val referenced = VisualSnapshotRestoreReadSide::class.java.declaredMethods.flatMap { method ->
            method.parameterTypes.toList() + listOf(method.returnType)
        }.map { it.name }
        assertTrue(
            "restore read side must stay offline: $referenced",
            referenced.none { name ->
                name.contains("QuickJs") || name.contains("VisualExecutionEngine") ||
                    name.contains("OkHttp") || name.contains("HttpClient") || name.contains("Socket")
            },
        )
    }
}
