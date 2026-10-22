package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.PendingCaptureDraftRecord
import kotlinx.coroutines.flow.Flow

/**
 * Read-only port for capture operations.
 */
interface CaptureReadPort {
    fun observePendingCaptureDrafts(): Flow<List<PendingCaptureDraftRecord>>
    suspend fun readPendingCaptureDraft(draftId: String): PendingCaptureDraftRecord?
    suspend fun readCanonicalSourceAsset(sourceAssetId: String): CanonicalSourceAssetRecord?
    suspend fun readUnreferencedCanonicalAssets(): List<CanonicalSourceAssetRecord>

    /**
     * [readUnreferencedCanonicalAssets] 的**补集**：五类引用口径下仍被任一引用形态
     * （题面修订、草稿页束、草稿主图、学生消息附图、图生成尝试产物）引用的规范资产行。
     *
     * 备份成包与恢复前闭合都按它反查"必须进包的题图"（AUD-05）。做成补集而不是第二份
     * 五类 EXISTS："被引用"的定义仍只有 [readUnreferencedCanonicalAssets] 那一处，
     * 清理与备份不会各自漂开；同时它天然包含"将来新增的引用形态"，不漏收。
     *
     * 默认实现抛异常而不是返回空表：空表在调用方语义里是"没有任何图被引用"，
     * 会把一次未实现静默变成一次"包内没有图"的成功成包。
     */
    suspend fun readReferencedCanonicalAssets(): List<CanonicalSourceAssetRecord> {
        throw UnsupportedOperationException(
            "This database does not list referenced canonical assets",
        )
    }
}

/**
 * Write port for capture operations.
 */
interface CaptureWritePort {
    /**
     * 废弃一份仍处于 EDITING 的待处理草稿（L4：录入界面待处理列表的"废弃"出路）。
     * 按修订号 CAS：已提交/已废弃/已被替换或期间又改过时返回 false——调用方据此
     * 如实报失败，不假成功。
     */
    suspend fun abandonPendingProblemDraft(
        draftId: String,
        expectedRevisionNumber: Int,
        abandonedAtEpochMillis: Long,
    ): Boolean

    /**
     * 原子认领早于 [createdBeforeEpochMillis] 且此刻仍无任何引用的规范资产行：
     * 判定与删除在同一写事务内完成，返回被删的行供调用方清理其磁盘文件。
     *
     * 两点都是必需的：同事务让"判定之后才提交的新引用"不会误删；宽限期让
     * "已落盘但引用尚未建立"的在途资产（大堂发送路径里登记资产行到写入消息引用之间）
     * 免于回收。
     */
    suspend fun claimUnreferencedCanonicalAssets(
        createdBeforeEpochMillis: Long,
    ): List<CanonicalSourceAssetRecord>

    suspend fun insertOrphanCanonicalAssetForTest(asset: CanonicalSourceAssetRecord)

    /**
     * 登记一个已落盘（vault 校验通过）的规范资产行，供消息附图等新引用形态使用；
     * 引用建立后由孤儿清理按引用判定保留。
     */
    suspend fun registerCanonicalSourceAsset(asset: CanonicalSourceAssetRecord) {
        throw UnsupportedOperationException("This database does not register canonical assets")
    }
}
