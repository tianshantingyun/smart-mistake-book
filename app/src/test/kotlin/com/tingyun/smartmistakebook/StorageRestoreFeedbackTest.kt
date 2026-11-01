package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.data.backup.BackupRestoreDatabaseClosedException
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupSelectionSummary
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

    /**
     * 批次 19 T2-6：子集备份的文案必须按 mask 显示**实际内容**——点名包含的类别，
     * 并点名**不含**的类别（「题+图（不含掌握库与会话）」口径），不再一律说成完整备份。
     * 创建面板摘要、创建回执、恢复前确认、恢复回执四处同口径。
     */
    @Test fun subsetCopyNamesBothIncludedAndExcludedCategories() {
        val nine = BackupSelectionSummary(
            mask = 9,
            categories = linkedSetOf(
                BackupContentCategory.ISSUE_BOOK,
                BackupContentCategory.IMAGES,
            ),
        )

        val panelSummary = backupSelectionSummaryText(backupSelectionOfMask(9))
        assertTrue(
            "创建面板摘要必须点名不含的类别：$panelSummary",
            panelSummary.contains("错题本、图片（不含学习进度、讲题记录）"),
        )

        val createReceipt = backupSelectionSentence(9)
        assertTrue(
            "创建回执必须与恢复前确认同口径（点名不含类别）：$createReceipt",
            createReceipt.contains("错题本、图片（不含学习进度、讲题记录）"),
        )

        val restoreReceipt = storageRestoreCompletionMessage(
            problemCount = 2,
            assetCount = 1,
            settings = RestoreSettingsOutcome.APPLIED,
            restartRequired = false,
            selection = nine,
        )
        assertTrue(
            "恢复回执必须点名不含的类别：$restoreReceipt",
            restoreReceipt.contains("错题本、图片（不含学习进度、讲题记录）"),
        )
    }

    /** 全选文案不点名排除（没有可排除的），也不带"会被清空"的警告。 */
    @Test fun fullSelectionCopyStaysClean() {
        val full = backupSelectionSentence(15)
        assertTrue(full.contains("错题本、学习进度、讲题记录、图片"))
        assertFalse("全选没有不含类别，不该出现括注", full.contains("不含"))

        val panelSummary = backupSelectionSummaryText(backupSelectionOfMask(15))
        assertFalse("全选面板摘要不带清空警告", panelSummary.contains("清空"))
    }

    /**
     * 批次 19 T2-2：回执/确认文案必须转述 manifest 的资产策略（assetPolicy=INCLUDED
     * →「题图随包」），消费点不再只能沉默。创建回执、恢复回执同口径。
     */
    @Test fun receiptsStateTheAssetPolicyFromTheSelection() {
        val subset = BackupSelectionSummary(
            mask = 13,
            categories = linkedSetOf(
                BackupContentCategory.ISSUE_BOOK,
                BackupContentCategory.SESSIONS,
                BackupContentCategory.IMAGES,
            ),
        )
        val restoreReceipt = storageRestoreCompletionMessage(
            problemCount = 1,
            assetCount = 1,
            settings = RestoreSettingsOutcome.NOT_IN_ARCHIVE,
            restartRequired = false,
            selection = subset,
        )
        assertTrue("恢复回执必须说明题图随包：$restoreReceipt", restoreReceipt.contains("题图随包"))

        val createReceipt = backupSelectionSentence(13)
        assertTrue("创建回执必须说明题图随包：$createReceipt", createReceipt.contains("题图随包"))
    }
}
