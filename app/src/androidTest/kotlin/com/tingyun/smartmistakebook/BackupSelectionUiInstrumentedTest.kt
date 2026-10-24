package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tingyun.smartmistakebook.core.domain.BackupActivity
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupManifestSummary
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory.IMAGES
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory.ISSUE_BOOK
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory.MASTERY
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory.SESSIONS
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupRepository
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupSelectionSummary
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.DeleteAllDataReceipt
import com.tingyun.smartmistakebook.core.domain.RestoreReceipt
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.StorageInventory
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 6C-2 设置页内容勾选（设备 UI）。
 *
 * 只渲染 [BackupContentSelectionSection] 与纯函数/假仓储：不初始化生产库，也不入队
 * 孤儿回收（StorageScreen 的副作用不在这条用例里）。
 */
class BackupSelectionUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultSelectionIsEverythingAndCreationReceivesMaskFifteen() {
        var created: BackupSelection? = null
        showPanel { created = it }

        for (category in BackupContentCategory.ALL) {
            composeRule.onNodeWithTag(categoryTag(category)).assertIsOn()
        }
        // 题图与错题本是基础：不能取消，0/8 因此不可达。
        composeRule.onNodeWithTag(categoryTag(ISSUE_BOOK)).assertIsNotEnabled()
        composeRule.onNodeWithTag(categoryTag(IMAGES)).assertIsNotEnabled()
        composeRule.onNodeWithTag("backup_category_lock_ISSUE_BOOK").assertExists()
        composeRule.onNodeWithTag("backup_category_lock_IMAGES").assertExists()
        composeRule.onNodeWithTag("backup_selection_summary")
            .assertTextContains("错题本、掌握库、会话、图片", substring = true)

        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(15, created?.mask)
        assertTrue(created?.isFull == true)
    }

    @Test
    fun uncheckingMasteryLeavesIssueBookSessionsImages() {
        var created: BackupSelection? = null
        showPanel { created = it }

        composeRule.onNodeWithTag(categoryTag(MASTERY)).performClick()

        composeRule.onNodeWithTag(categoryTag(MASTERY)).assertIsOff()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).assertIsOn().assertIsEnabled()
        composeRule.onNodeWithTag(categoryTag(ISSUE_BOOK)).assertIsOn()
        composeRule.onNodeWithTag(categoryTag(IMAGES)).assertIsOn()
        composeRule.onNodeWithTag("backup_selection_summary")
            .assertTextContains("错题本、会话、图片", substring = true)

        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(13, created?.mask)
        assertEquals(linkedSetOf(ISSUE_BOOK, SESSIONS, IMAGES), created?.categories)
    }

    @Test
    fun uncheckingSessionsCascadesMasterySoTheResultIsNine() {
        var created: BackupSelection? = null
        showPanel { created = it }

        // 直接取消会话：依赖会话的掌握库必须一起取消，否则组合未闭合。
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).performClick()

        composeRule.onNodeWithTag(categoryTag(SESSIONS)).assertIsOff()
        composeRule.onNodeWithTag(categoryTag(MASTERY)).assertIsOff()
        composeRule.onNodeWithTag(categoryTag(ISSUE_BOOK)).assertIsOn()
        composeRule.onNodeWithTag(categoryTag(IMAGES)).assertIsOn()
        composeRule.onNodeWithTag("backup_selection_summary")
            .assertTextContains("错题本、图片", substring = true)

        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(9, created?.mask)
        assertEquals(linkedSetOf(ISSUE_BOOK, IMAGES), created?.categories)
    }

    @Test
    fun fromThirteenUncheckingSessionsAlsoLeavesNine() {
        var created: BackupSelection? = null
        showPanel { created = it }

        composeRule.onNodeWithTag(categoryTag(MASTERY)).performClick()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).performClick()

        composeRule.onNodeWithTag(categoryTag(SESSIONS)).assertIsOff()
        composeRule.onNodeWithTag(categoryTag(MASTERY)).assertIsOff()
        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(9, created?.mask)
    }

    @Test
    fun checkingMasteryFromNinePullsSessionsBackIn() {
        var created: BackupSelection? = null
        showPanel { created = it }

        // 先到 9（题+图）。
        composeRule.onNodeWithTag(categoryTag(MASTERY)).performClick()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).performClick()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).assertIsOff()

        // 勾掌握库必须同时补回它依赖的会话，而不是留下未闭合组合。
        composeRule.onNodeWithTag(categoryTag(MASTERY)).performClick()

        composeRule.onNodeWithTag(categoryTag(MASTERY)).assertIsOn()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).assertIsOn()
        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(15, created?.mask)
    }

    @Test
    fun emptyAndImagesOnlySelectionsStayUnreachable() {
        var created: BackupSelection? = null
        showPanel { created = it }

        // 关掉所有允许关的类别后仍停在 9：题/图两项在 9 上被禁用，无法再清空。
        composeRule.onNodeWithTag(categoryTag(MASTERY)).performClick()
        composeRule.onNodeWithTag(categoryTag(SESSIONS)).performClick()
        composeRule.onNodeWithTag(categoryTag(ISSUE_BOOK)).assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithTag(categoryTag(IMAGES)).assertIsOn().assertIsNotEnabled()

        composeRule.onNodeWithTag("storage_create_backup").performClick()
        assertEquals(9, created?.mask)

        // 纯状态机同样拒绝 0/8：取消题或图都是空操作。
        val nine = backupSelectionOfMask(9)
        assertEquals(9, toggleBackupCategory(nine, ISSUE_BOOK, checked = false).mask)
        assertEquals(9, toggleBackupCategory(nine, IMAGES, checked = false).mask)
        val empty = BackupSelection.of(emptySet())
        assertEquals(empty, toggleBackupCategory(empty, IMAGES, checked = true))
    }

    @Test
    fun checkingACategoryPullsInItsDependencies() {
        val fromIssueBook = toggleBackupCategory(
            BackupSelection.of(emptySet()),
            ISSUE_BOOK,
            checked = true,
        )
        assertEquals(9, fromIssueBook.mask)
        assertTrue(IMAGES in fromIssueBook.categories)

        val fromSessions = toggleBackupCategory(
            BackupSelection.of(emptySet()),
            SESSIONS,
            checked = true,
        )
        assertEquals(13, fromSessions.mask)
        assertTrue(ISSUE_BOOK in fromSessions.categories)
        assertTrue(IMAGES in fromSessions.categories)

        val fromMastery = toggleBackupCategory(
            BackupSelection.of(emptySet()),
            MASTERY,
            checked = true,
        )
        assertEquals(15, fromMastery.mask)

        // 13 → 勾掌握库补全会话链 → 15；13 → 取消会话 → 9。
        assertEquals(15, toggleBackupCategory(backupSelectionOfMask(13), MASTERY, true).mask)
        assertEquals(9, toggleBackupCategory(backupSelectionOfMask(13), SESSIONS, false).mask)
    }

    @Test
    fun createPassesTheSelectedCategoriesToTheRepository() {
        val repository = RecordingBackupRepository()

        runBlocking {
            createBackupArchive(repository, ByteArrayOutputStream(), backupSelectionOfMask(13))
        }

        val options = requireNotNull(repository.lastOptions)
        assertEquals(13, options.selection.mask)
        assertEquals(setOf(ISSUE_BOOK, SESSIONS, IMAGES), options.selection.categories)
    }

    @Test
    fun zeroEightAndUnclosedSelectionsNeverReachTheRepository() {
        val repository = RecordingBackupRepository()

        for (mask in listOf(0, 8, 1, 14)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    createBackupArchive(
                        repository,
                        ByteArrayOutputStream(),
                        backupSelectionOfMask(mask),
                    )
                }
            }
        }

        assertNull(repository.lastOptions)
    }

    @Test
    fun restoreReceiptNamesTheSelectedCategories() {
        val subset = storageRestoreCompletionMessage(
            problemCount = 2,
            assetCount = 1,
            settings = RestoreSettingsOutcome.APPLIED,
            restartRequired = false,
            selection = BackupSelectionSummary(
                mask = 9,
                categories = linkedSetOf(ISSUE_BOOK, IMAGES),
            ),
        )
        assertTrue(subset.contains("恢复完成：2 道题、1 张题图"))
        assertTrue(subset.contains("错题本"))
        assertTrue(subset.contains("图片"))
        assertTrue("替换语义下没勾的类别会被清空，回执必须说明", subset.contains("未包含的类别已按替换语义清空"))
        // 批次 19 T2-6：子集回执必须**点名**不含的类别（括注口径），且是"不含"而非声称带上；
        // 同时转述 manifest 的资产策略（assetPolicy=INCLUDED → 题图随包）。
        assertTrue(
            "回执必须点名不含的类别",
            subset.contains("（不含掌握库、会话）"),
        )
        assertTrue("回执必须与创建/确认同口径转述资产策略", subset.contains("题图随包"))
        assertFalse("子集回执不能声称带了全部类别", subset.contains("错题本、掌握库、会话、图片"))

        val full = storageRestoreCompletionMessage(
            problemCount = 2,
            assetCount = 1,
            settings = RestoreSettingsOutcome.NOT_IN_ARCHIVE,
            restartRequired = false,
            selection = BackupSelectionSummary(
                mask = 15,
                categories = BackupContentCategory.ALL,
            ),
        )
        assertTrue(full.contains("错题本、掌握库、会话、图片"))

        val legacy = storageRestoreCompletionMessage(
            problemCount = 0,
            assetCount = 0,
            settings = RestoreSettingsOutcome.NOT_IN_ARCHIVE,
            restartRequired = false,
            selection = null,
        )
        assertTrue("v1/v2 旧包必须按全量说明", legacy.contains("全部"))
    }

    /**
     * 恢复前确认必须把话说完（设计 §3.3）：这份包带了哪些类别、未包含的哪些会在替换
     * 语义下被清空；旧包按全量说明。恢复按钮不再是一按就替换。
     */
    @Test
    fun restoreConfirmationNamesThePackageAndWhatWillBeCleared() {
        val subset = BackupManifestSummary(
            formatVersion = 3,
            databaseSchemaVersion = 69,
            createdAtEpochMillis = 0L,
            problemCount = 1,
            assetCount = 1,
            fileCount = 2,
            selection = BackupSelectionSummary(
                mask = 9,
                categories = linkedSetOf(ISSUE_BOOK, IMAGES),
            ),
        )
        val subsetText = backupRestoreConfirmationText(subset)
        assertTrue(subsetText.contains("错题本"))
        assertTrue(subsetText.contains("图片"))
        assertTrue("未包含的掌握库必须点名", subsetText.contains("掌握库"))
        assertTrue("未包含的会话必须点名", subsetText.contains("会话"))
        assertTrue("必须说明未包含类别会被清空", subsetText.contains("恢复时会被清空"))
        assertTrue("必须说明是替换语义", subsetText.contains("替换当前全部数据"))
        assertFalse("子集不能说成旧版全量", subsetText.contains("旧版备份"))

        val fullText = backupRestoreConfirmationText(
            subset.copy(
                selection = BackupSelectionSummary(
                    mask = 15,
                    categories = BackupContentCategory.ALL,
                ),
            ),
        )
        assertTrue(fullText.contains("错题本、掌握库、会话、图片"))
        assertFalse("全选没有未包含类别，不该说会清空", fullText.contains("会被清空"))

        val legacyText = backupRestoreConfirmationText(subset.copy(selection = null))
        assertTrue("v1/v2 旧包必须按全量说明", legacyText.contains("旧版备份按全量恢复"))
    }

    private fun showPanel(onCreateBackup: (BackupSelection) -> Unit) {
        composeRule.setContent {
            SmartMistakeBookTheme {
                BackupContentSelectionSection(
                    enabled = true,
                    busy = false,
                    onCreateBackup = onCreateBackup,
                )
            }
        }
    }

    private fun categoryTag(category: BackupContentCategory): String =
        "backup_category_${category.name}"
}

/** 记录 create 收到的选择；不打开数据库、不写文件。 */
private class RecordingBackupRepository : BackupRepository {
    var lastOptions: BackupOptions? = null

    override val activity: StateFlow<BackupActivity> = MutableStateFlow(BackupActivity.Idle)

    override suspend fun inspect(): StorageInventory = StorageInventory(
        databaseBytes = 0L,
        assetBytes = 0L,
        tempBytes = 0L,
        cleanableBytes = 0L,
    )

    override suspend fun cleanupOrphanAssets(): Int = 0

    override suspend fun create(
        destination: OutputStream,
        options: BackupOptions,
    ): BackupReceipt {
        lastOptions = options
        return BackupReceipt(
            createdAtEpochMillis = 0L,
            databaseSchemaVersion = 69,
            problemCount = 0,
            assetCount = 0,
            fileCount = 1,
            totalBytes = 1L,
            selectionMask = options.selection.mask,
        )
    }

    override suspend fun validate(source: InputStream): BackupValidation =
        BackupValidation.Invalid("测试假仓储")

    override suspend fun restore(source: InputStream): RestoreReceipt =
        error("测试假仓储")

    override suspend fun deleteAllData(): DeleteAllDataReceipt =
        DeleteAllDataReceipt(0L, 0L, 0L, 0L)
}
