package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

data class StorageInventory(
    val databaseBytes: Long,
    val assetBytes: Long,
    val tempBytes: Long,
    val cleanableBytes: Long,
    val recentBackupAtEpochMillis: Long? = null,
) {
    init {
        require(databaseBytes >= 0L && assetBytes >= 0L && tempBytes >= 0L) {
            "Storage inventory bytes must not be negative"
        }
        require(cleanableBytes >= 0L && cleanableBytes <= tempBytes) {
            "Cleanable storage must be within temporary storage"
        }
        require(recentBackupAtEpochMillis == null || recentBackupAtEpochMillis >= 0L) {
            "Backup timestamp must not be negative"
        }
    }
}

/**
 * 成包选项（6C-2）。
 *
 * 内容选择默认全选（[BackupSelection.FULL]，mask=15），与 v2 的「整库 + 全部资产」在
 * 用户数据上等价（派生搜索索引恒裁、恢复后按科重建）。
 * 合法性**不在这里**判定：界面需要构造不自洽的中间态，而 `create()` 必须先用
 * [BackupSelection.requireCreatable] 拒绝 0/8 与未闭合组合（不静默补齐）。
 */
data class BackupOptions(
    val selection: BackupSelection = BackupSelection.FULL,
) {
    /**
     * 兼容属性，已退役：图片是否随包由 [BackupSelection] 的 `IMAGES` 类别承载。
     *
     * 旧成包路径（批次 18 T4 前）仍读这个属性；保留只读镜像可避免跨模块编译中断。
     * 新代码不得使用它，也不要据此判断闭包——闭包只认 [BackupSelection]。
     */
    @Deprecated(
        message = "includeAssets 已退役：图片由 BackupSelection 的 IMAGES 类别承载",
    )
    val includeAssets: Boolean
        get() = BackupContentCategory.IMAGES in selection.categories
}

data class BackupReceipt(
    val createdAtEpochMillis: Long,
    val databaseSchemaVersion: Int,
    val problemCount: Int,
    val assetCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
    /** 这次成包的内容选择（mask 9/13/15）；默认 15 即 v2 旧语义。 */
    val selectionMask: Int = BackupContentCategory.FULL_MASK,
    /** 这次裁剪删掉了数据行的表数（全选且无删除时为 0）。 */
    val trimmedTableCount: Int = 0,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(databaseSchemaVersion > 0)
        require(problemCount >= 0 && assetCount >= 0 && fileCount > 0)
        require(totalBytes > 0L)
        require(selectionMask in BackupSelection.ALLOWED_MASKS) {
            "Backup receipt selection mask must be one of ${BackupSelection.ALLOWED_MASKS}"
        }
        require(trimmedTableCount >= 0)
    }
}

data class RestoreReceipt(
    val databaseSchemaVersion: Int,
    val problemCount: Int,
    val assetCount: Int,
    val fileCount: Int,
    val restartRequired: Boolean = true,
    /**
     * 设置载荷这次恢复落到本机的结果（6C-1）。[RestoreSettingsOutcome.WRITE_FAILED]
     * 表示**部分恢复**：数据库已经切换到备份数据且保持一致；设置是多个键顺序写入的
     * （非原子），失败时可能只写进去一部分，不保证仍是恢复前那份（KD-47）。
     */
    val settings: RestoreSettingsOutcome = RestoreSettingsOutcome.NOT_IN_ARCHIVE,
    /**
     * 这份备份声明的内容选择（6C-2）。`null` = v1/v2 旧包，读作**全量**；
     * 非 null 时回执与界面必须如实说明「未包含的类别已随替换语义清空」。
     */
    val selection: BackupSelectionSummary? = null,
) {
    init {
        require(databaseSchemaVersion > 0)
        require(problemCount >= 0 && assetCount >= 0 && fileCount > 0)
    }
}

/** 恢复时本机设置的处置结果。 */
enum class RestoreSettingsOutcome {
    /** 备份里没有设置（v1 旧包，或成包时没装设置口）：本机设置原样不动。 */
    NOT_IN_ARCHIVE,

    /** 备份里的设置已写入本机 DataStore。 */
    APPLIED,

    /**
     * 备份里有设置但写入失败：数据库已切到备份数据；**设置不保证仍是恢复前那份**（KD-47）。
     * 恢复设置是多个键的顺序写（选项、考试、优化参数、开关、提醒时间），非原子——中途失败时
     * 前面已经成功的键会保留，本机设置可能半新半旧。不回滚数据库——数据库一致性已经达成，
     * 回滚只会把用户的数据再翻一遍；也不为设置做失败回滚（需要旧值快照，超出本批范围）。
     */
    WRITE_FAILED,
}

data class DeleteAllDataReceipt(
    val deletedDatabaseBytes: Long,
    val deletedAssetBytes: Long,
    val deletedPreferenceBytes: Long,
    val deletedTempBytes: Long,
    val deletedSecretVaultBytes: Long = 0L,
) {
    init {
        require(
            deletedDatabaseBytes >= 0L && deletedAssetBytes >= 0L &&
                deletedPreferenceBytes >= 0L && deletedTempBytes >= 0L &&
                deletedSecretVaultBytes >= 0L,
        ) { "Deleted data byte counts must not be negative" }
    }
}

sealed interface BackupValidation {
    data class Valid(
        val manifest: BackupManifestSummary,
        val checkedFileCount: Int,
        val totalBytes: Long,
    ) : BackupValidation

    data class Invalid(
        val reason: String,
        val manifest: BackupManifestSummary? = null,
    ) : BackupValidation
}

data class BackupManifestSummary(
    val formatVersion: Int,
    val databaseSchemaVersion: Int,
    val createdAtEpochMillis: Long,
    val problemCount: Int,
    val assetCount: Int,
    val fileCount: Int,
    /** 成包时的应用版本（v2 起的版本戳）；v1 旧包缺席，为 null。 */
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    /** 成包时那份库的随包内容版本；未知为 null。 */
    val contentVersion: String? = null,
    /** 包内是否带设置（`settings.json`）；v1 旧包为 false。 */
    val hasSettings: Boolean = false,
    /**
     * 包声明的**内容选择**（6C-2，formatVersion=3 起）。`null` = v1/v2 旧包，
     * 读作全量；非 null 时恢复侧必须在交换前校验它与包内库实测一致。
     */
    val selection: BackupSelectionSummary? = null,
    /**
     * 包声明的逐表行数（v3 selection 的 `rowCounts`，86 表）。恢复侧用它和**包内库**
     * 逐表 `COUNT(*)` 对拍（mask=15 也照样对拍，防止"子集包伪装全量"或载荷/清单漂开）；
     * v1/v2 旧包没有这个声明，为 null。
     */
    val selectionRowCounts: Map<String, Long>? = null,
)

interface BackupRepository {
    /**
     * 备份活动的显式状态（KD-54）：[BackupActivity.Idle] 之外表示备份正在排空在途写
     * 或生成快照。写入口按同一道 [BackupWriteGate] 短暂排队；界面用它说明"备份期间
     * 保存会稍后继续"，而不是让用户等分钟级连接池超时。
     */
    val activity: StateFlow<BackupActivity>

    suspend fun inspect(): StorageInventory

    /**
     * Deletes canonical assets that no problem revision, draft or tutor message
     * references, and returns how many vault files were actually removed.
     *
     * Deciding and deleting the rows happens inside one write transaction, so a
     * reference committed before the claim always survives. Assets younger than
     * the 30-minute grace window are left for a later run: a freshly captured
     * image stays unreferenced until the send that will reference it commits.
     */
    suspend fun cleanupOrphanAssets(): Int

    /**
     * 生成备份包。
     *
     * 实现必须在写出任何字节前用 [BackupOptions.selection] 的
     * [BackupSelection.requireCreatable] 做闸门：只接受闭包自洽且 mask ∈ {9,13,15}
     * 的选择；0（空选择）、8（仅图片）与未闭合组合一律拒绝，**不静默补齐**
     * （批次 18 产品裁定，见 [BackupSelection]）。
     */
    suspend fun create(
        destination: OutputStream,
        options: BackupOptions = BackupOptions(),
    ): BackupReceipt

    suspend fun validate(source: InputStream): BackupValidation

    suspend fun restore(source: InputStream): RestoreReceipt

    suspend fun deleteAllData(): DeleteAllDataReceipt
}
