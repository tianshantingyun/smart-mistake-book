package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.data.backup.BackupRestoreDatabaseClosedException
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRestoreFeedbackTest {
    @Test fun incompleteRollbackDoesNotPromiseThatTheOriginalDataWasRestored() {
        val message = storageRestoreFailureMessage(
            BackupRestoreDatabaseClosedException("恢复失败且回滚不完整，相关数据已隔离"),
        )
        assertTrue(message.contains("回滚不完整"))
        assertTrue(message.contains("已隔离"))
        assertFalse(message.contains("已回到原来的数据"))
        assertTrue(message.contains("关闭应用"))
    }

    @Test fun aClosedDatabaseWithoutDetailsStillRequiresADataCheckAfterRestart() {
        val message = storageRestoreFailureMessage(BackupRestoreDatabaseClosedException(""))
        assertFalse(message.contains("已回到原来的数据"))
        assertTrue(message.contains("核对"))
    }

    /**
     * KD-47：恢复设置是多键顺序写、非原子。写失败时数据库已恢复，但设置可能只写进去一部分，
     * 文案不得承诺“保持原样”。
     */
    @Test fun failedSettingsWriteSaysPartOfTheSettingsMayHaveChanged() {
        val message = storageRestoreCompletionMessage(
            problemCount = 3,
            assetCount = 2,
            settings = RestoreSettingsOutcome.WRITE_FAILED,
            restartRequired = true,
        )
        assertTrue(
            "必须如实说明“未能完整写入、可能有部分设置已变”",
            message.contains("设置未能完整写入，可能有部分设置已变"),
        )
        assertFalse(
            "多键顺序写失败后不能承诺设置仍是恢复前那份",
            message.contains("保持原样"),
        )
        assertTrue(message.contains("请完全退出并重新打开应用"))
    }

    @Test fun appliedSettingsDoNotCarryThePartialWriteWarning() {
        val message = storageRestoreCompletionMessage(
            problemCount = 3,
            assetCount = 2,
            settings = RestoreSettingsOutcome.APPLIED,
            restartRequired = false,
        )
        assertFalse(message.contains("设置未能完整写入"))
        assertFalse(message.contains("请完全退出并重新打开应用"))
        assertTrue(message.contains("恢复完成：3 道题、2 张题图"))
    }
}
