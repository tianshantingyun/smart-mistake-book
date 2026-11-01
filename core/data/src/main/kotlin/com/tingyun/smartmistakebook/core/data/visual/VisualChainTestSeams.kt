package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord
import java.io.File

/**
 * V-01 ④ 腿（PDF/restore 真值）的**公开测试缝**（批次 38 T10 方案 A；R1
 * `docs/research/2026-11-01-preview-wiring.md` §4 落地）。
 *
 * **它消灭的具体失败**：V-01 的 app 侧套件（`visualjs/V01VisualChainInstrumentedTest`，
 * 真隔离引擎）拿不到 core:data internal 符号（`VisualSnapshotRestore`/`VisualDrawOpsParser`/
 * `VisualDrawOpsPdfPainter`/`VisualLabelContent` 全家 internal，跨编译单元不可达）——
 * ④ 腿（真引擎产出 DrawOps → restore → PDF 全腿）此前只有 core:data 降级变体（进程内引擎）
 * 能测，真隔离引擎轨 V-01 长期 PARTIAL 3/4。本缝把三个转调点收成两个 opaque 公共操作，
 * app 测试据此补 ④ 腿而不翻转任何 internal 可见性（方案 C 被拒：永久扩 7-10 个领域类型
 * 公开面）。
 *
 * ## 三条硬约束（批次 38 T10 §2.3 + R1 §4.3 补丁，写死于 KDoc）
 *
 * 1. **仅 androidTest 引用**：本对象在 `src/main` 内必须零引用（生产旁路直绘 = restore 门
 *    被绕过）。配套 grep 门 `tools/ci/check_visual_test_seams.py`（A-13 先例同型）断言
 *    `VisualChainTestSeams` 在全部 `src/main` 与 `src/test` 源内零命中——它同时是回滚
 *    净度的机检（只回滚用例不回滚缝时门会红，提醒一起处理）。
 * 2. **R-04 release 轨需 keep**：只被 androidTest 引用的缝与其 internal 转调家族在 release
 *    主包可被 R8 收缩出悬空引用（批次 35 K6 机制②同型）。keep 行与预览渲染器
 *    （`TutorVisualPreviewRenderer`，同为接通前 test-only）在 `app/r04-test-keep.pro`
 *    **一并登记**（R1 §4.2-3，避免两批各自加行造成门脚本漂移）；在该登记落地前，
 *    引用本缝的用例**不得进 R-04 release 轨用例清单**。debug 轨 `minifyEnabled=false`
 *    不受影响。
 * 3. **不透出 internal 类型**（Kotlin 规则：public 签名不得暴露 internal 参数/返回类型）：
 *    `VisualDrawOpsDocument`/`VisualRestoreOutcome`/`VisualDrawOpsPdfPainter` 全部留在缝内；
 *    app ④ 例断言只用本缝的公共返回值 + `PdfRenderer` 公共 API + 像素数组（约束补丁 2）。
 *
 * 不进本缝的面（覆盖差值，如实登记）：屏栅格"恢复文档重绘逐位相等"断言（需 painter 入缝）
 * 留在 core:data 降级变体；app ④ 的独有增量 = 真隔离引擎产出的 DrawOps 走 restore→PDF 全腿。
 */
public object VisualChainTestSeams {

    /**
     * restore 判定：入参用 core:database 公共读侧行（与生产读链同形，夹具零漂移），
     * 行→记录映射走唯一权威侧 [VisualSnapshotRestoreReadSide.restoreRecordOf]（消灭
     * "每个调用方手工拼 record 的口径漂移"），判定走 [VisualSnapshotRestore.restore] 原序
     * （非 LIVE/未知 schema/renderer 版本/字体清单/解析拒绝 → 只读）。
     *
     * @param verifiedFontManifests 测试注入的已登记字体清单（生产 `VERIFIED_FONT_MANIFESTS`
     *   为空集 → 默认恒只读；传快照行的清单版本即验证 ②档回放路径本身——策略 KDoc 明示的
     *   测试用法）。
     * @param previewVerified 调用方对预览字节的完整性声明（语义与
     *   [VisualSnapshotRestore.restore] 同口径：false 时只读结果不暴露预览 id）。
     */
    public fun restoreDecision(
        snapshot: TutorVisualSnapshotRecord,
        verifiedFontManifests: Set<String>,
        previewVerified: Boolean = false,
    ): VisualChainRestoreDecision {
        val outcome = VisualSnapshotRestore.restore(
            record = VisualSnapshotRestoreReadSide.restoreRecordOf(snapshot),
            previewVerified = previewVerified,
            policy = VisualRestorePolicy(verifiedFontManifests = verifiedFontManifests),
        )
        return when (outcome) {
            is VisualRestoreOutcome.Replay -> VisualChainRestoreDecision.Replay(
                snapshotId = outcome.snapshotId,
                programId = outcome.programId,
                viewportWidthDu = outcome.viewportWidthDu,
                viewportHeightDu = outcome.viewportHeightDu,
                fontScaleMilli = outcome.fontScaleMilli,
                theme = outcome.theme,
            )

            is VisualRestoreOutcome.ReadOnly -> VisualChainRestoreDecision.ReadOnly(
                reasonCode = outcome.reason.code,
                diagnostic = outcome.diagnostic,
                previewAssetId = outcome.previewAssetId,
            )
        }
    }

    /**
     * PDF 全腿：`drawCommandsJson + contentIds` 解析（回放语义：结构/值域/冻结引用/配平/
     * stats 复算，不重复也不放宽引擎门）→ [VisualDrawOpsPdfPainter.writeFile]（D1∧D2∧D3
     * 完整性由其内部强制，失败保证不发布半成品）。
     *
     * 失败按既有有码异常族抛（消息带 `PAINTER_*` 码前缀）：解析拒绝 = `PAINTER_DRAW_COMMANDS`
     * （消息内嵌原始 `DRAWOPS_*` 码），页几何/IO = `PAINTER_PDF_*`，label 不可达 =
     * `PAINTER_UNKNOWN_LABEL`。类型为 internal 异常族——app 测试按 `RuntimeException` 接住
     * 并以消息前缀断言（公共签名不透出 internal 类型，约束 3）。
     *
     * @param label 单 label 内容（测试内现算，R1 §3.2-B"引擎回传 contentIds + 测试内 Paint
     *   现算"路径）；null = 无 label 内容，任何 label op 在绘制时报 `PAINTER_UNKNOWN_LABEL`。
     *   只支持 text（math 本批 fail-closed，[PAINTER_LABEL_MATH]）——预览缝与 PDF 腿继承同一
     *    边界，不做特例。
     */
    public fun writeSnapshotPdf(
        drawCommandsJson: String,
        contentIds: Set<String>,
        label: VisualChainTestSeamLabel?,
        ptPerDu: Float,
        target: File,
    ) {
        val document = when (val parsed = VisualDrawOpsParser.parse(drawCommandsJson, contentIds)) {
            is VisualDrawOpsValidation.Valid -> parsed.document
            is VisualDrawOpsValidation.Invalid ->
                throw paintingFailure(PAINTER_DRAW_COMMANDS, "${parsed.code}: ${parsed.message}")
        }
        val labelContent: (String) -> VisualLabelContent? = when (label) {
            null -> { _ -> null }
            else -> { contentId ->
                if (contentId == label.contentId) {
                    VisualLabelContent.Text(
                        payload = label.payload,
                        sizeSp = label.sizeSp,
                        fontScale = label.fontScale,
                        heightDu = label.heightDu,
                        lines = label.lines.map { VisualLabelLine(it.start, it.endExclusive) },
                    )
                } else {
                    null
                }
            }
        }
        VisualDrawOpsPdfPainter().writeFile(
            document = document,
            target = target,
            ptPerDu = ptPerDu,
            labelContent = labelContent,
        )
    }
}

/** restore 判定的公共投影（internal [VisualRestoreOutcome] 的 opaque 形态，只带公共值）。 */
public sealed interface VisualChainRestoreDecision {

    /** ②档：DrawOps 已按注入策略重新校验通过，可回放（app ④ 例据此写 PDF）。 */
    public data class Replay(
        val snapshotId: String,
        val programId: String,
        val viewportWidthDu: Int,
        val viewportHeightDu: Int,
        val fontScaleMilli: Int,
        val theme: String,
    ) : VisualChainRestoreDecision

    /**
     * ③档：只读。[reasonCode] 为 `RESTORE_*` 族（六门逐门一码）；[previewAssetId] 仅在
     * 调用方声明 `previewVerified = true` 时非空——③档显示缓存可达性的断言位。
     */
    public data class ReadOnly(
        val reasonCode: String,
        val diagnostic: String,
        val previewAssetId: String?,
    ) : VisualChainRestoreDecision
}

/**
 * 单 label 内容的公共值类型（app 测试内现算的载荷形状；与 internal `VisualLabelContent.Text`
 * 同字段语义——payload + 冻结字号/字重比/总高 + 显式段区间）。lines 指向 [payload] 的
 * 字符区间，段数与 [VisualChainTestSeamLabel.heightDu] 的不变式
 * （heightDu = 段数 × 行高）由提供方保证，缝内不复制校验（折算语义单点在 painter 侧）。
 */
public data class VisualChainTestSeamLabel(
    val contentId: String,
    val payload: String,
    val sizeSp: Float,
    val fontScale: Float,
    val heightDu: Float,
    val lines: List<Line>,
) {
    init {
        require(contentId.isNotBlank()) { "A seam label needs a content id" }
        require(payload.isNotEmpty()) { "A seam label needs a payload" }
        require(sizeSp.isFinite() && sizeSp > 0f) { "A seam label needs a positive sizeSp, got $sizeSp" }
        require(fontScale.isFinite() && fontScale > 0f) {
            "A seam label needs a positive fontScale, got $fontScale"
        }
        require(heightDu.isFinite() && heightDu >= 0f) {
            "A seam label needs a non-negative heightDu, got $heightDu"
        }
        require(lines.isNotEmpty()) { "A seam label needs at least one line" }
        require(lines.size <= MAX_LINES) { "A seam label exceeds $MAX_LINES lines" }
    }

    /** 一行显式段：`[start, endExclusive)` 指向 [VisualChainTestSeamLabel.payload]。 */
    public data class Line(val start: Int, val endExclusive: Int) {
        init {
            require(start >= 0 && start <= endExclusive) {
                "A seam label line range must satisfy 0 ≤ start ≤ endExclusive, got [$start, $endExclusive)"
            }
        }
    }

    private companion object {
        /** 与度量端口条目数同量级的防呆上界（坏段列表在缝入口显式炸出，不静默画错）。 */
        const val MAX_LINES: Int = 128
    }
}
