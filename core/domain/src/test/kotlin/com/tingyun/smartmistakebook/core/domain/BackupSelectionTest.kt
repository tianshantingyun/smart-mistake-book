package com.tingyun.smartmistakebook.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6C-2 内容选择的域规则（批次 18 T1）。
 *
 * 钉住四件事：
 * 1. 四种类别位（1/2/4/8）与 16 个 mask 的往返；只有 0/8/9/13/15 在闭包下自洽；
 * 2. 闭包传播（`ISSUE_BOOK ⇒ IMAGES`、`SESSIONS ⇒ ISSUE_BOOK + IMAGES`、
 *    `MASTERY ⇒ SESSIONS`）与 `missingDependencies` 的如实报告；
 * 3. 产品裁定：**只有 9/13/15 可创建**——0（空选择）与 8（仅图片）虽然闭包自洽，
 *    仍必须被 `requireCreatable()` 拒绝；其余 mask 未闭合即拒绝；任何路径都不静默补齐；
 * 4. 默认值即旧语义：`BackupOptions()` 为全选（15），回执/manifest 摘要缺省为 v1/v2 全量。
 */
class BackupSelectionTest {
    private val issueBook = BackupContentCategory.ISSUE_BOOK
    private val mastery = BackupContentCategory.MASTERY
    private val sessions = BackupContentCategory.SESSIONS
    private val images = BackupContentCategory.IMAGES

    /** 16 个 mask：只有 0/8/9/13/15 闭包自洽。 */
    private val closedMasks = setOf(0, 8, 9, 13, 15)

    /** 产品裁定的可创建组合：9（题+图）、13（题+会话+图）、15（全选）。 */
    private val creatableMasks = setOf(9, 13, 15)

    @Test
    fun `all sixteen masks round trip and only closed masks are self consistent`() {
        for (mask in 0..15) {
            val selection = selectionOfMask(mask)

            assertEquals("mask $mask 必须能无损往返", mask, selection.mask)
            assertEquals("mask $mask 的类别集合", categoriesOfMask(mask), selection.categories)
            assertEquals("mask $mask 的闭包自洽性", mask in closedMasks, selection.isClosed)
            assertEquals("只有全选才算 full", mask == BackupContentCategory.FULL_MASK, selection.isFull)
        }
    }

    @Test
    fun `only nine thirteen and fifteen are creatable`() {
        assertEquals(creatableMasks, BackupSelection.ALLOWED_MASKS)
        for (mask in 0..15) {
            assertEquals("mask $mask 的可创建性", mask in creatableMasks, selectionOfMask(mask).isCreatable)
        }
        assertEquals(BackupContentCategory.FULL_MASK, 15)
    }

    @Test
    fun `empty selection and images only are closed but still rejected before create`() {
        for (mask in listOf(0, 8)) {
            val selection = selectionOfMask(mask)

            assertTrue("mask $mask 在闭包上自洽", selection.isClosed)
            assertFalse("mask $mask 仍未获产品允许", selection.isCreatable)
            val failure = assertThrows(IllegalArgumentException::class.java) {
                selection.requireCreatable()
            }
            assertTrue(
                "拒因必须点名 mask：${failure.message}",
                failure.message.orEmpty().contains("mask=$mask"),
            )
            assertEquals("拒绝不得改写选择", mask, selection.mask)
            assertEquals("拒绝不得静默补齐", categoriesOfMask(mask), selection.categories)
        }
    }

    @Test
    fun `unclosed masks are rejected without silent completion`() {
        val unclosedMasks = (1..7) + (10..12) + 14

        for (mask in unclosedMasks) {
            val selection = selectionOfMask(mask)

            assertFalse("mask $mask 不应被视为自洽", selection.isClosed)
            assertTrue("mask $mask 必须报出缺失依赖", selection.missingDependencies().isNotEmpty())
            val failure = assertThrows(IllegalArgumentException::class.java) {
                selection.requireCreatable()
            }
            assertTrue(
                "拒因必须说明未闭合：${failure.message}",
                failure.message.orEmpty().contains("未闭合"),
            )
            assertEquals("拒绝不得改写选择", mask, selection.mask)
            assertEquals("拒绝不得静默补齐", categoriesOfMask(mask), selection.categories)
        }
    }

    @Test
    fun `closure propagates issue book to images`() {
        val closed = BackupSelection.of(setOf(issueBook)).closed()

        assertEquals(setOf(issueBook, images), closed.categories)
        assertEquals(9, closed.mask)
        assertTrue(closed.isClosed)
        assertTrue(closed.isCreatable)
        assertEquals(9, BackupSelection.of(setOf(issueBook)).closed().summary().mask)
    }

    @Test
    fun `closure propagates sessions to issue book and images`() {
        val closed = BackupSelection.of(setOf(sessions)).closed()

        assertEquals(setOf(sessions, issueBook, images), closed.categories)
        assertEquals(13, closed.mask)
        assertTrue(closed.isClosed)
        assertTrue(closed.isCreatable)
    }

    @Test
    fun `mastery implies full selection`() {
        for (seed in listOf(setOf(mastery), setOf(mastery, issueBook), setOf(mastery, sessions))) {
            val closed = BackupSelection.of(seed).closed()

            assertEquals("掌握库（含传递依赖）等价于全选：$seed", 15, closed.mask)
            assertEquals(BackupContentCategory.ALL, closed.categories)
            assertTrue(closed.isFull)
            assertTrue(closed.isCreatable)
        }
    }

    @Test
    fun `closing is idempotent and never shrinks the explicit choice`() {
        for (mask in 0..15) {
            val explicit = selectionOfMask(mask)
            val closed = explicit.closed()

            assertEquals("关闭必须幂等：mask $mask", closed, closed.closed())
            assertTrue("关闭只能增加依赖：mask $mask", closed.categories.containsAll(explicit.categories))
            assertTrue("关闭后的集合必须自洽：mask $mask", closed.isClosed)
        }
    }

    @Test
    fun `missing dependencies report the direct parent of each selected category`() {
        assertTrue(BackupSelection.of(emptySet()).missingDependencies().isEmpty())
        assertEquals(
            mapOf(issueBook to setOf(images)),
            BackupSelection.of(setOf(issueBook)).missingDependencies(),
        )
        assertEquals(
            mapOf(sessions to setOf(issueBook, images)),
            BackupSelection.of(setOf(sessions)).missingDependencies(),
        )
        assertEquals(
            mapOf(mastery to setOf(issueBook, sessions)),
            BackupSelection.of(setOf(mastery)).missingDependencies(),
        )
        assertEquals(
            mapOf(mastery to setOf(sessions), issueBook to setOf(images)),
            BackupSelection.of(setOf(mastery, issueBook)).missingDependencies(),
        )
        assertTrue(BackupSelection.FULL.missingDependencies().isEmpty())
    }

    @Test
    fun `selections copy their input set so later mutation cannot change the mask`() {
        val mutable = mutableSetOf(issueBook)
        val selection = BackupSelection.of(mutable)

        mutable.add(images)

        assertEquals(1, selection.mask)
        assertEquals(setOf(issueBook), selection.categories)
    }

    @Test
    fun `full is the default option and carries every category`() {
        val options = BackupOptions()

        assertEquals(BackupSelection.FULL, options.selection)
        assertEquals(15, options.selection.mask)
        assertEquals(BackupContentCategory.ALL, options.selection.categories)
        assertTrue(options.selection.isFull)
        assertTrue(options.selection.isCreatable)
        assertThrows(IllegalArgumentException::class.java) {
            BackupOptions(selection = BackupSelection.of(emptySet())).selection.requireCreatable()
        }
    }

    @Test
    fun `summary reports mask and categories and rejects inconsistent pairs`() {
        val summary = BackupSelection.of(setOf(issueBook, images)).summary()

        assertEquals(9, summary.mask)
        assertEquals(setOf(issueBook, images), summary.categories)
        assertFalse(summary.isFull)
        assertEquals(BackupContentCategory.FULL_MASK, BackupSelection.FULL.summary().mask)
        assertTrue(BackupSelection.FULL.summary().isFull)

        assertThrows(IllegalArgumentException::class.java) {
            BackupSelectionSummary(mask = 9, categories = setOf(issueBook))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSelectionSummary(mask = 16, categories = BackupContentCategory.ALL)
        }
    }

    @Test
    fun `receipts and manifest summaries default to legacy full semantics`() {
        val backup = BackupReceipt(
            createdAtEpochMillis = 1L,
            databaseSchemaVersion = 69,
            problemCount = 1,
            assetCount = 1,
            fileCount = 1,
            totalBytes = 1L,
        )
        assertEquals(BackupContentCategory.FULL_MASK, backup.selectionMask)
        assertEquals(0, backup.trimmedTableCount)
        assertEquals(13, backup.copy(selectionMask = 13, trimmedTableCount = 42).selectionMask)
        assertEquals(42, backup.copy(selectionMask = 13, trimmedTableCount = 42).trimmedTableCount)

        val restore = RestoreReceipt(
            databaseSchemaVersion = 69,
            problemCount = 1,
            assetCount = 1,
            fileCount = 1,
        )
        assertNull("v1/v2 恢复回执缺省为全量", restore.selection)
        assertEquals(
            BackupSelection.of(setOf(issueBook, images)).summary(),
            restore.copy(selection = BackupSelection.of(setOf(issueBook, images)).summary()).selection,
        )

        val manifest = BackupManifestSummary(
            formatVersion = 2,
            databaseSchemaVersion = 69,
            createdAtEpochMillis = 1L,
            problemCount = 1,
            assetCount = 1,
            fileCount = 1,
        )
        assertNull("v1/v2 manifest 缺 selection 读作全量", manifest.selection)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `retired include assets flag mirrors the images category`() {
        assertTrue("全选包含图片", BackupOptions().includeAssets)
        assertTrue(
            "9（题+图）包含图片",
            BackupOptions(selection = BackupSelection.of(setOf(issueBook, images))).includeAssets,
        )
        assertFalse(
            "没有图片类别的选择不得声称带图",
            BackupOptions(selection = BackupSelection.of(setOf(issueBook))).includeAssets,
        )
    }

    private fun selectionOfMask(mask: Int): BackupSelection = BackupSelection.of(categoriesOfMask(mask))

    private fun categoriesOfMask(mask: Int): Set<BackupContentCategory> =
        BackupContentCategory.entries.filterTo(LinkedHashSet<BackupContentCategory>()) {
            (mask and it.bit) != 0
        }
}
