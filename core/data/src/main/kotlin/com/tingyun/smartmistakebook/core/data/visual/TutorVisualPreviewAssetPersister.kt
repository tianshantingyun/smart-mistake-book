package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue

/**
 * 视觉快照预览 PNG 的 canonical 资产写侧（D-04，批次 38）。
 *
 * **它消灭的具体失败**：`tutor_visual_snapshot.preview_asset_id` 自 schema 70 起就是
 * canonical 引用面的第六类站点（RESTRICT FK + 孤儿清理/备份成包/恢复闭合三侧判定，
 * 见 `CanonicalAssetReferenceSites`），但生产里**没有任何写入方**——全仓
 * `TutorVisualSnapshotInput.previewAssetId` 只有默认 null。视觉链装配时若没有这条唯一
 * 写口，每个调用点都得自己拼"落字节 → 登记资产行 → 传 id"三步，漏一步就是两类事故：
 * - **漏登记行**：字节在盘上、资产行不存在 → 快照提交被 RESTRICT FK 当场拒绝（整事务
 *   回滚，消息/快照一起不成立），而原因（少了哪一步）没有任何可读出口；
 * - **类型漂移**：source_type 各处写字符串字面量，与六站点判定/治理口径对不上行。
 *
 * 这里把三步收成一步：预览 PNG 字节按 [StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW]
 * 落成 canonical 资产（走 vault 的统一预算/校验面），登记资产行，返回记录——
 * 其 [CanonicalSourceAssetRecord.sourceAssetId] 即 `TutorVisualSnapshotInput.previewAssetId`
 * 的值。**顺序即语义**：先落字节、再登记行，调用方最后才把 id 写进快照——引用建立时
 * 资产行必然已在。
 *
 * 幂等：预览是本地确定性渲染的产物，资产 id 与文件名由**内容 sha** 派生（内容寻址，
 * 与拍照/裁剪同一条）。同一快照重复提交 → 同字节 → 同一行（`INSERT OR IGNORE`），
 * 不重复登记、不产生第二份文件。
 */
class TutorVisualPreviewAssetPersister(
    private val persistPngBytes: suspend (
        pngBytes: ByteArray,
        sourceType: String,
        createdAtEpochMillis: Long,
    ) -> CanonicalSourceAssetRecord,
    private val registerRow: suspend (CanonicalSourceAssetRecord) -> Unit,
) {

    /**
     * 把预览 PNG 落成 canonical 资产并登记资产行；返回记录的
     * [CanonicalSourceAssetRecord.sourceAssetId] 即快照输入 `previewAssetId` 的值。
     *
     * 空字节在这里拒绝（不浪费一次 vault 写入）；字节预算/可解码性/尺寸预算由 vault 的
     * 统一写入面执行，不在这里复制第二份。取消一律向上传播；
     * `persistPngBytes` / `registerRow` 的失败原样抛出——半截预览（字节在、行不在，或
     * 反过来）不构成可提交的快照输入。
     */
    suspend fun persist(
        pngBytes: ByteArray,
        createdAtEpochMillis: Long,
    ): CanonicalSourceAssetRecord {
        require(pngBytes.isNotEmpty()) { "A visual preview needs non-empty PNG bytes" }
        val record = persistPngBytes(
            pngBytes,
            StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW,
            createdAtEpochMillis,
        )
        registerRow(record)
        return record
    }

    companion object {
        /**
         * 生产装配：字节走 vault 的统一写入面（与拍照、裁剪、生成图同一套解码/尺寸/字节
         * 预算与惰性目录创建），行登记走数据库端口。
         */
        fun forProduction(
            context: Context,
            database: StudyDatabasePort,
        ): TutorVisualPreviewAssetPersister {
            val vault = AndroidCanonicalAssetVault(context.applicationContext)
            return TutorVisualPreviewAssetPersister(
                persistPngBytes = { bytes, sourceType, createdAtEpochMillis ->
                    vault.persistCleanImageBytes(
                        bytes = bytes,
                        mimeType = "image/png",
                        sourceType = sourceType,
                        createdAtEpochMillis = createdAtEpochMillis,
                    )
                },
                registerRow = { record -> database.registerCanonicalSourceAsset(record) },
            )
        }
    }
}
