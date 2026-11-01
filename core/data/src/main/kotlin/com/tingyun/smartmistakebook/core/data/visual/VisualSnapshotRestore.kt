package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget

/**
 * 冷恢复的**输入行**（批次 30 T4 / D-05；对应 `tutor_visual_snapshot` 的一行，见
 * `core/database/.../entity/TutorVisualEntities.kt`）。
 *
 * **它消灭的具体失败**：改前"恢复一张图"没有明确的输入边界——调用方可以顺手把 program 行的
 * 源码列也传进来，于是恢复路径迟早被写成"再跑一遍源码"：冷启动要起隔离进程、依赖 JS
 * 运行时、源码版本漂移还可能画出另一张图。本类型**结构上没有**源码字符串，也没有
 * 运行时/编译器/API/SDK 版本字段：
 * - 源码只在"用户明确要求重跑"的①档读取，**不属于恢复 DTO**；
 * - 运行时/编译器不参与矢量重放（设计 §4.2：把它们塞进重放门会让应用升级后旧图全部降级）；
 * - API/SDK 版本只决定"能否再执行"，恢复路径拿不到它们，也就**无法**误走再执行。
 *
 * 这不是运行期 `if (!isColdStart)`（那是可被后来者删掉的一行），而是"改错就编译不过"的
 * 类型层约束。依赖层的另一条（grep 门）：本文件与 [VisualSnapshotRestore] 不 import 任何
 * JS 执行引擎类型，恢复只需 [VisualDrawOpsParser.parse] + painter。
 *
 * 字段口径（与 D-01 表列一一对应）：
 * - [contentIds]：`content_ids_json` 解出的冻结集合；解析器用它复验 label 引用（**重新校验**，
 *   不信任磁盘上自称 validated 的标记，契约 §8）；
 * - [sceneSchemaVersion] / [renderSchemaVersion] / [rendererVersion]：②档三输入；
 * - [fontManifestVersion]：像素一致门。**先说明**：仓内尚无"可验证字体清单"，默认策略下
 *   一律不满足该门 → 只读（保守分支），不承诺"重放像素与当时一致"；
 * - [previewAssetId]：可空预览（`canonical_source_asset` 的 RESTRICT 引用）。是否**可信**
 *   由调用方用 `canonicalAssetFileIsIntact` 逐位核对后经 `previewVerified` 声明——本类型
 *   只携带 id，不自行声称完整；
 * - [live]：只有 LIVE 快照可回放。快照 STAGING 时消息/link 本来也写不进去（D-01 的
 *   `INSERT … SELECT … WHERE status='LIVE'`），这里再拒一次是"恢复不信任来源查询"的兜底。
 */
internal data class VisualSnapshotRestoreRecord(
    val snapshotId: String,
    val programId: String,
    val drawCommandsJson: String,
    val contentIds: Set<String>,
    val sceneSchemaVersion: Int,
    val renderSchemaVersion: Int,
    val rendererVersion: String,
    val fontManifestVersion: String,
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    val fontScaleMilli: Int,
    val theme: String,
    val previewAssetId: String?,
    val live: Boolean,
) {
    init {
        require(snapshotId.isNotBlank()) { "A restore record needs a snapshot id" }
        require(programId.isNotBlank()) { "A restore record needs a program id" }
        require(drawCommandsJson.isNotEmpty()) { "A restore record needs its draw commands" }
        require(sceneSchemaVersion >= 1) { "A scene schema version starts at 1" }
        require(renderSchemaVersion >= 1) { "A render schema version starts at 1" }
        require(rendererVersion.isNotBlank()) { "A restore record needs a renderer version" }
        require(fontManifestVersion.isNotBlank()) { "A restore record needs a font manifest version" }
        require(viewportWidthDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A restore viewport width must be within the canvas budget"
        }
        require(viewportHeightDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A restore viewport height must be within the canvas budget"
        }
        require(fontScaleMilli > 0) { "A restore font scale must be positive" }
        require(theme.isNotBlank()) { "A restore record needs a theme" }
        require(previewAssetId == null || previewAssetId.isNotBlank()) {
            "A restore preview asset id must be null or non-empty"
        }
    }
}

/** ②档降级到③档（只读）的**有码**原因：调用方按码分流（预览/文字/重试），不折成一句"图坏了"。 */
internal enum class VisualRestoreFallbackReason(val code: String) {
    /** 行不是 LIVE（半成品/损坏来源查询）。 */
    SNAPSHOT_NOT_LIVE("RESTORE_SNAPSHOT_NOT_LIVE"),

    /** `scene_schema_version` 未知：不把未知 JSON 交给 painter。 */
    UNKNOWN_SCENE_SCHEMA("RESTORE_UNKNOWN_SCENE_SCHEMA"),

    /** `render_schema_version` 未知。 */
    UNKNOWN_RENDER_SCHEMA("RESTORE_UNKNOWN_RENDER_SCHEMA"),

    /** painter 实现版本不符：格式合规但画法变了，不硬画成"另一张图"。 */
    RENDERER_VERSION_MISMATCH("RESTORE_RENDERER_VERSION_MISMATCH"),

    /** 字体清单未验证：不能声称像素一致（契约 §8"不能只存 Typeface 名称便称字体版本精确匹配"）。 */
    FONT_MANIFEST_UNVERIFIED("RESTORE_FONT_MANIFEST_UNVERIFIED"),

    /** 解析器拒绝：diagnostic 内嵌原始 `DRAWOPS_*` 码（版本/缺字段/类型…）。 */
    DRAW_OPS_REJECTED("RESTORE_DRAW_OPS_REJECTED"),
}

/**
 * 恢复判定策略（可注入，默认 [CURRENT]）。
 *
 * **默认策略是保守的**：[VERIFIED_FONT_MANIFESTS] 当前为**空集**——仓内没有"可验证字体资源
 * 清单"（设计 §7 #4：`res/font`/`assets/fonts` 零命中，core/ui 只用 `FontFamily.Monospace`
 * 与系统默认）。因此默认路径**永远走只读**，不会假装"重放像素一致"；清单落地后只需登记
 * 清单版本，②档即自动可达。测试可注入已登记清单来验证解析/回放路径本身。
 */
internal data class VisualRestorePolicy(
    val supportedSceneSchemaVersion: Int = VisualSnapshotRestore.SUPPORTED_SCENE_SCHEMA_VERSION,
    val supportedRenderSchemaVersion: Int = VisualDrawOpsParser.DRAW_SCHEMA_VERSION,
    val rendererVersion: String = VISUAL_RENDERER_IMPLEMENTATION_VERSION,
    val verifiedFontManifests: Set<String> = VERIFIED_FONT_MANIFESTS,
) {
    init {
        require(supportedSceneSchemaVersion >= 1) { "A supported scene schema starts at 1" }
        require(supportedRenderSchemaVersion >= 1) { "A supported render schema starts at 1" }
        require(rendererVersion.isNotBlank()) { "A restore policy needs a renderer version" }
    }

    companion object {
        val CURRENT: VisualRestorePolicy = VisualRestorePolicy()
    }
}

/**
 * 已登记的可验证字体清单版本集合。
 *
 * **当前为空**是如实状态，不是遗漏：仓内没有字体资源清单，任何"字体版本精确匹配"的说法都
 * 没有证据。空集让默认策略**结构上不可能**承诺像素一致（走③档预览/说明），而不是靠调用方
 * 自觉。清单落地时把版本登记到这里（设计与设备判据见 `docs/research/2026-10-29-visual-persistence-design.md`
 * §4.2/§7 #4）。
 */
internal val VERIFIED_FONT_MANIFESTS: Set<String> = emptySet()

/** 恢复结果：可重放的类型化文档，或带码只读降级。 */
internal sealed interface VisualRestoreOutcome {
    /**
     * ②档：DrawOps 已按当前策略重新校验，调用方把 [document] 交给 painter（屏/PDF 同一入口）。
     * 视口/字号/主题随行携带，painter 的 `pxPerDu` 由调用方按目标（屏 density / PDF ptPerDu）算。
     */
    data class Replay(
        val programId: String,
        val snapshotId: String,
        val document: VisualDrawOpsDocument,
        val viewportWidthDu: Int,
        val viewportHeightDu: Int,
        val fontScaleMilli: Int,
        val theme: String,
    ) : VisualRestoreOutcome

    /**
     * ③档：只读。[previewAssetId] 仅在调用方声明 `previewVerified = true`（已用
     * `canonicalAssetFileIsIntact` 逐位核对）时非空；否则为 null，调用方显示文字/公式说明。
     */
    data class ReadOnly(
        val reason: VisualRestoreFallbackReason,
        val diagnostic: String,
        val previewAssetId: String?,
    ) : VisualRestoreOutcome
}

/**
 * **冷恢复判定**（批次 30 T4 / D-05）：重启后从快照行重建一张图，**不执行 JS**。
 *
 * 判定序（设计 §4.3，逐条对应一个具体失败）：
 * 1. 非 LIVE → 只读（半成品行不许被画出来）；
 * 2. scene schema 未知 → 只读（未知 JSON 绝不交 painter）；**先于解析**，坏 DrawOps 不会
 *    盖过"版本未知"这条更有用的原因；
 * 3. render schema 未知 → 只读；
 * 4. renderer 实现版本不符 → 只读（格式合规但画法变了）；
 * 5. 字体清单未验证 → 只读（不承诺像素一致）；
 * 6. 上面全过 → [VisualDrawOpsParser.parse]（与引擎提交门同一套不变量：词表/预算/有限性/
 *    配平/contentId 冻结/stats 复算）→ 通过则 [VisualRestoreOutcome.Replay]，拒绝则只读并
 *    内嵌 `DRAWOPS_*` 码。
 *
 * 恢复**必须重新校验**：解析器重跑全部不变量，不信任磁盘上自称 validated 的标记（契约 §8）；
 * 预览字节的完整性由调用方核对后经 `previewVerified` 传入（本函数不读文件系统）。
 *
 * **与 T3 的边界**：
 * - **行 → 记录的唯一映射**由 T3 的读侧承担（`core/database` 的
 *   `TutorVisualSnapshotRecord` → [VisualSnapshotRestoreRecord]）：`status` 映射为
 *   `live = (status == TutorVisualStatus.LIVE)`，`content_ids_json` 严格解析为
 *   `Set<String>`，预览资产先用 `canonicalAssetFileIsIntact` 核对再传 `previewVerified`。
 *   本类型不接受 Room entity，也不做文件 IO（JVM 门无需 Android）。
 * - ①档（用户明确要求重跑源码）不在此处——它需要 program 行的源码与 API/SDK/运行时版本，
 *   由 T3 的显式再执行入口承担；本类型拿不到这些字段，因此"恢复误执行 JS"在结构上不可表达。
 * - ②档的写侧（`renderer_version` 写 [VISUAL_RENDERER_IMPLEMENTATION_VERSION]）也归 T3。
 */
internal object VisualSnapshotRestore {

    /** 只读诊断上界（有界原因，不把整段坏文档回显给 UI/日志）。 */
    const val MAX_DIAGNOSTIC_CHARS: Int = 240

    /** 当前支持重放的 scene schema（与 JS 引擎产出的场景 schema 同值）。 */
    const val SUPPORTED_SCENE_SCHEMA_VERSION: Int = 1

    /**
     * @param previewVerified 调用方已核对预览资产字节（存在 + 字节数 + sha256 与
     *   `canonical_source_asset.content_sha256` 逐位一致）。false 时只读结果不暴露预览。
     */
    fun restore(
        record: VisualSnapshotRestoreRecord,
        previewVerified: Boolean = false,
        policy: VisualRestorePolicy = VisualRestorePolicy.CURRENT,
    ): VisualRestoreOutcome {
        fun fallback(reason: VisualRestoreFallbackReason, detail: String): VisualRestoreOutcome.ReadOnly =
            VisualRestoreOutcome.ReadOnly(
                reason = reason,
                diagnostic = "${reason.code}: $detail".take(MAX_DIAGNOSTIC_CHARS),
                previewAssetId = record.previewAssetId.takeIf { previewVerified },
            )

        if (!record.live) {
            return fallback(
                VisualRestoreFallbackReason.SNAPSHOT_NOT_LIVE,
                "snapshot ${record.snapshotId.take(64)} is not LIVE",
            )
        }
        if (record.sceneSchemaVersion != policy.supportedSceneSchemaVersion) {
            return fallback(
                VisualRestoreFallbackReason.UNKNOWN_SCENE_SCHEMA,
                "scene schema ${record.sceneSchemaVersion} is not ${policy.supportedSceneSchemaVersion}",
            )
        }
        if (record.renderSchemaVersion != policy.supportedRenderSchemaVersion) {
            return fallback(
                VisualRestoreFallbackReason.UNKNOWN_RENDER_SCHEMA,
                "render schema ${record.renderSchemaVersion} is not ${policy.supportedRenderSchemaVersion}",
            )
        }
        if (record.rendererVersion != policy.rendererVersion) {
            return fallback(
                VisualRestoreFallbackReason.RENDERER_VERSION_MISMATCH,
                "renderer ${record.rendererVersion.take(48)} is not ${policy.rendererVersion.take(48)}",
            )
        }
        if (record.fontManifestVersion !in policy.verifiedFontManifests) {
            return fallback(
                VisualRestoreFallbackReason.FONT_MANIFEST_UNVERIFIED,
                "font manifest ${record.fontManifestVersion.take(48)} is not verified",
            )
        }
        return when (val parsed = VisualDrawOpsParser.parse(record.drawCommandsJson, record.contentIds)) {
            is VisualDrawOpsValidation.Valid -> VisualRestoreOutcome.Replay(
                programId = record.programId,
                snapshotId = record.snapshotId,
                document = parsed.document,
                viewportWidthDu = record.viewportWidthDu,
                viewportHeightDu = record.viewportHeightDu,
                fontScaleMilli = record.fontScaleMilli,
                theme = record.theme,
            )

            is VisualDrawOpsValidation.Invalid -> fallback(
                VisualRestoreFallbackReason.DRAW_OPS_REJECTED,
                "${parsed.code}: ${parsed.message}",
            )
        }
    }
}
