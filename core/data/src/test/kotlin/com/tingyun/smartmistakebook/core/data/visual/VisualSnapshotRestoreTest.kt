package com.tingyun.smartmistakebook.core.data.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisualSnapshotRestore] 的 JVM 门（批次 30 T4 / D-05 冷恢复）。
 *
 * **不变量**：本文件不实例化 QuickJS、不读 JS 资产、不触 android 符号——恢复判定只吃
 * [VisualSnapshotRestoreRecord]（**类型里没有 `sourceJs`**）、策略常量与既有的纯解析器。
 * 全部用例在无原生库的 JVM 门执行。
 *
 * 覆盖映射（任务书"重启不执行 JS / 字体与 renderer 匹配才重建 / 未知版本只读"）：
 *
 * 1. **结构性不执行 JS**：`VisualSnapshotRestoreRecord` 的字段集里没有源码/运行时/编译器/
 *    api/sdk 版本（反射断言）；恢复入口的方法签名不引用 `VisualExecutionEngine` /
 *    `QuickJs*`（反射断言）。**这两条是"改错就编译不过 / 结构上不存在"的约束**，
 *    不是运行期 `if (!isColdStart)`。
 * 2. **三档兼容**：未知 scene/render schema、renderer 版本不符、字体清单未验证 → 只读
 *    （可带预览资产，但**只有调用方已复核字节完整性**才暴露）；三档全部匹配 → 解析
 *    DrawOps 并交回放计划。
 * 3. **顺序与重新校验**：版本门先于解析（未知版本时坏 DrawOps 不会变成 `DRAWOPS_*` 噪音）；
 *    冻结 contentId 由快照携带并在解析时**重新校验**（不是信任"磁盘上自称 validated"）；
 *    STAGING 行永不被回放。
 *
 * 设备判据（本环境不运行 Gradle/adb，执行归设备轨）：
 * - 冷启动 → 打开一条含图消息 → 隔离进程 **0 次 spawn**、0 Provider 派发（spawn 计数取
 *   隔离服务日志，派发观测复用 `FigureHttpTransportBypassObservationTest` 的 0 派发模式）；
 * - 显示内容 = 保存的预览 PNG（预览资产 sha 与 `canonical_source_asset.content_sha256`
 *   逐位一致）；字体清单未落地时不得承诺"重放像素与当时一致"。
 *
 * 先红后绿：本文件先于 [VisualSnapshotRestore] 落盘；实现缺失时整文件编译不过。本环境
 * **不运行 Gradle**，执行结果登记为 UNVERIFIED（由脚本串行门裁决）。
 */
class VisualSnapshotRestoreTest {

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

    private fun record(
        snapshotId: String = "snap-1",
        live: Boolean = true,
        drawCommandsJson: String = pathDocument,
        contentIds: Set<String> = emptySet(),
        sceneSchemaVersion: Int = 1,
        renderSchemaVersion: Int = 1,
        rendererVersion: String = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
        fontManifestVersion: String = "fonts-v1",
        previewAssetId: String? = "asset-preview",
    ): VisualSnapshotRestoreRecord = VisualSnapshotRestoreRecord(
        snapshotId = snapshotId,
        programId = "visual-program-1",
        drawCommandsJson = drawCommandsJson,
        contentIds = contentIds,
        sceneSchemaVersion = sceneSchemaVersion,
        renderSchemaVersion = renderSchemaVersion,
        rendererVersion = rendererVersion,
        fontManifestVersion = fontManifestVersion,
        viewportWidthDu = 360,
        viewportHeightDu = 240,
        fontScaleMilli = 1000,
        theme = "light",
        previewAssetId = previewAssetId,
        live = live,
    )

    private fun fallback(outcome: VisualRestoreOutcome): VisualRestoreOutcome.ReadOnly {
        assertTrue("expected a read-only fallback, got $outcome", outcome is VisualRestoreOutcome.ReadOnly)
        return outcome as VisualRestoreOutcome.ReadOnly
    }

    // ------------------------------------------------------------ 结构性断言

    @Test
    fun `restore record carries no source runtime or compiler facts`() {
        val fieldNames = VisualSnapshotRestoreRecord::class.java.declaredFields
            .map { it.name.lowercase() }
        assertFalse(
            "restore record must not carry anything that could re-execute JS: $fieldNames",
            fieldNames.any { name ->
                "source" in name || "runtime" in name || "compiler" in name ||
                    "apiversion" in name || "sdkversion" in name
            },
        )
    }

    @Test
    fun `restore entry references no execution engine type`() {
        val referenced = VisualSnapshotRestore::class.java.declaredMethods.flatMap { method ->
            method.parameterTypes.toList() + listOf(method.returnType)
        }.map { it.name }
        assertTrue(
            "restore entry must not reference the JS engine: $referenced",
            referenced.none { it.contains("VisualExecutionEngine") || it.contains("QuickJs") },
        )
    }

    // ------------------------------------------------------------ 匹配门

    @Test
    fun `unknown scene schema falls back before parsing`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(sceneSchemaVersion = 2, drawCommandsJson = "not json at all"),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.UNKNOWN_SCENE_SCHEMA, outcome.reason)
        // 版本门先于解析：坏 DrawOps 不应盖过"版本未知"这条更有用的原因。
        assertFalse(outcome.diagnostic.contains("DRAWOPS_"))
        assertEquals("asset-preview", outcome.previewAssetId)
    }

    @Test
    fun `unknown render schema falls back`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(renderSchemaVersion = 2),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.UNKNOWN_RENDER_SCHEMA, outcome.reason)
        assertEquals("asset-preview", outcome.previewAssetId)
    }

    @Test
    fun `renderer version mismatch falls back`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(rendererVersion = "visual-renderer-v0"),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.RENDERER_VERSION_MISMATCH, outcome.reason)
    }

    @Test
    fun `unverified font manifest never replays pixels`() {
        // 默认策略没有可验证字体清单：即使 schema/renderer 全部匹配也只能走只读。
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(),
                previewVerified = true,
                policy = VisualRestorePolicy.CURRENT,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.FONT_MANIFEST_UNVERIFIED, outcome.reason)
        assertEquals("asset-preview", outcome.previewAssetId)
    }

    @Test
    fun `verified gates replay the parsed document`() {
        val outcome = VisualSnapshotRestore.restore(
            record = record(),
            previewVerified = true,
            policy = verifiedFonts,
        )
        assertTrue("expected replay, got $outcome", outcome is VisualRestoreOutcome.Replay)
        val replay = outcome as VisualRestoreOutcome.Replay
        assertEquals("visual-program-1", replay.programId)
        assertEquals("snap-1", replay.snapshotId)
        assertEquals(1, replay.document.stats.ops)
        assertEquals(2, replay.document.stats.pathCommands)
        assertEquals(360, replay.viewportWidthDu)
        assertEquals(240, replay.viewportHeightDu)
    }

    // ------------------------------------------------------------ 重新校验与只读面

    @Test
    fun `invalid draw ops fall back with the parser code`() {
        val badVersion = pathDocument.replace("\"drawSchemaVersion\":1", "\"drawSchemaVersion\":2")
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(drawCommandsJson = badVersion),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.DRAW_OPS_REJECTED, outcome.reason)
        assertTrue(
            "diagnostic should keep the parser code, got ${outcome.diagnostic}",
            outcome.diagnostic.contains("DRAWOPS_SCHEMA_VERSION"),
        )
    }

    @Test
    fun `frozen content ids are revalidated from the snapshot`() {
        val accepted = VisualSnapshotRestore.restore(
            record = record(drawCommandsJson = labelDocument, contentIds = setOf("c1")),
            previewVerified = true,
            policy = verifiedFonts,
        )
        assertTrue("expected replay, got $accepted", accepted is VisualRestoreOutcome.Replay)

        val rejected = fallback(
            VisualSnapshotRestore.restore(
                record = record(drawCommandsJson = labelDocument, contentIds = emptySet()),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.DRAW_OPS_REJECTED, rejected.reason)
        assertTrue(rejected.diagnostic.contains("DRAWOPS_REF"))
    }

    @Test
    fun `unverified preview bytes are never exposed`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(sceneSchemaVersion = 2),
                previewVerified = false,
                policy = verifiedFonts,
            ),
        )
        assertNull(
            "a preview must not be offered unless its bytes were verified by the caller",
            outcome.previewAssetId,
        )
    }

    @Test
    fun `a staging snapshot is never replayed`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(live = false),
                previewVerified = true,
                policy = verifiedFonts,
            ),
        )
        assertEquals(VisualRestoreFallbackReason.SNAPSHOT_NOT_LIVE, outcome.reason)
    }

    @Test
    fun `diagnostics stay bounded`() {
        val outcome = fallback(
            VisualSnapshotRestore.restore(
                record = record(drawCommandsJson = "x".repeat(4096)),
                previewVerified = false,
                policy = verifiedFonts,
            ),
        )
        assertTrue(
            "diagnostics must be bounded, got ${outcome.diagnostic.length} chars",
            outcome.diagnostic.length <= VisualSnapshotRestore.MAX_DIAGNOSTIC_CHARS,
        )
    }
}
