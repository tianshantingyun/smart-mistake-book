package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * 快照行 → 恢复记录的**唯一映射**（批次 38 V-03 实现缝；[VisualSnapshotRestore] KDoc
 * "与 T3 的边界"一段登记的读侧责任）。
 *
 * **它消灭的具体失败**：
 * 1. **映射无权威侧**：此处缺位时，每个恢复调用方都手工拼一次 [VisualSnapshotRestoreRecord]——
 *    V-01 设备链（`V01VisualChainInstrumentedTest` 的 `runChainToRestoredReplay`）已被迫在
 *    测试内手拼过一次并自注"测试内映射"。`status → live` 与 `content_ids_json → Set<String>`
 *    的口径没有生产归属，调用方各自漂移（例如把非 LIVE 状态宽松判成 live=true，恢复门 1
 *    被绕过；或把 contentId 解析成 List 保序、与写侧 Set 语义不一致）。
 * 2. **松散解析把损坏行当空集**：`content_ids_json` 损坏时若退成空集，label 引用复验就从
 *    "重新校验冻结集合"退化为"空口无凭"——恢复可以画出一张引用不可考的图。这里**严格解析**：
 *    非 JSON 数组、含非字符串元素，一律以有界消息抛 [IllegalArgumentException]。写侧
 *    （`TutorVisualCommitRepository.toWrite`）只会写合法的字符串数组，因此异常只可能来自
 *    绕过写侧的库外写入/损坏行——fail-closed 如实上报，不当空集吞掉。
 *
 * 与 [VisualSnapshotRestore] 同一条 grep 门：本文件不 import 任何 JS 引擎/网络类型，恢复
 * 读侧结构上不可能派发或再执行（JVM 结构断言见 `VisualSnapshotRestoreReadSideTest`）。
 *
 * 不做文件 IO、不核对预览字节：`previewVerified` 由调用方用
 * `canonicalAssetFileIsIntact` 逐位核对后传入 [VisualSnapshotRestore.restore]（本类型只
 * 携带 id，与 restore KDoc 同口径）。
 */
internal object VisualSnapshotRestoreReadSide {

    /** 损坏载荷诊断的回显上界（有界原因，不把整段坏 JSON 回显给 UI/日志）。 */
    private const val MAX_CORRUPTION_DETAIL_CHARS: Int = 120

    fun restoreRecordOf(record: TutorVisualSnapshotRecord): VisualSnapshotRestoreRecord =
        VisualSnapshotRestoreRecord(
            snapshotId = record.snapshotId,
            programId = record.programId,
            drawCommandsJson = record.drawCommandsJson,
            contentIds = parseContentIds(record.contentIdsJson),
            sceneSchemaVersion = record.sceneSchemaVersion,
            renderSchemaVersion = record.renderSchemaVersion,
            rendererVersion = record.rendererVersion,
            fontManifestVersion = record.fontManifestVersion,
            viewportWidthDu = record.viewportWidthDu,
            viewportHeightDu = record.viewportHeightDu,
            fontScaleMilli = record.fontScaleMilli,
            theme = record.theme,
            previewAssetId = record.previewAssetId,
            // 只有 LIVE 是终态、可回放；STAGING 与任何未知状态一律按不可回放处理
            // （恢复门 1 fail-closed：恢复不信任来源查询的宽松读法）。
            live = record.status == TutorVisualStatus.LIVE,
        )

    /**
     * `content_ids_json` → 冻结 contentId 集合。写侧编码是"排序后去重的字符串数组"
     * （`buildJsonArray { contentIds.sorted() … }`），这里按同一条契约严格读回：
     * 数组形状、元素全为字符串；重复元素折叠为集合（写侧本就来自 Set）。
     */
    private fun parseContentIds(contentIdsJson: String): Set<String> {
        val element = try {
            Json.parseToJsonElement(contentIdsJson)
        } catch (corrupt: IllegalArgumentException) {
            throw IllegalArgumentException(
                "A visual snapshot content id list is corrupt: " +
                    (corrupt.message ?: "unparseable JSON").take(MAX_CORRUPTION_DETAIL_CHARS),
                corrupt,
            )
        }
        require(element is JsonArray) {
            "A visual snapshot content id list must be a JSON array, " +
                "got ${element::class.simpleName}"
        }
        return buildSet {
            element.forEach { item ->
                require(item is JsonPrimitive && item.isString) {
                    "A visual snapshot content id list must contain strings only, got $item"
                }
                add(item.content)
            }
        }
    }
}
