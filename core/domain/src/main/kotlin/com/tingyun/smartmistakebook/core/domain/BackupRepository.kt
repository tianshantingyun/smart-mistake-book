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

data class BackupOptions(
    val includeAssets: Boolean = true,
) {
    init {
        require(includeAssets) { "First backup version always includes canonical assets" }
    }
}

data class BackupReceipt(
    val createdAtEpochMillis: Long,
    val databaseSchemaVersion: Int,
    val problemCount: Int,
    val assetCount: Int,
    val fileCount: Int,
    val totalBytes: Long,
) {
    init {
        require(createdAtEpochMillis >= 0L)
        require(databaseSchemaVersion > 0)
        require(problemCount >= 0 && assetCount >= 0 && fileCount > 0)
        require(totalBytes > 0L)
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

    suspend fun create(
        destination: OutputStream,
        options: BackupOptions = BackupOptions(),
    ): BackupReceipt

    suspend fun validate(source: InputStream): BackupValidation

    suspend fun restore(source: InputStream): RestoreReceipt

    suspend fun deleteAllData(): DeleteAllDataReceipt
}
