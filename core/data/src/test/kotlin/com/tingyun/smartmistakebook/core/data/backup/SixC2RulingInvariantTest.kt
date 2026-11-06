package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 6C-2 防漂移钉子（批次 43 WS9 items 61/62；**只加断言，不改行为**）。
 *
 * 钉住 2026-10-24 用户已确认的三项裁定中的前两项（第三项「派生索引恒裁」由
 * `SmbkSelectionPlanTest` / `BackupSnapshotTrimmerInstrumentedTest` 承担）：
 *
 * 1. 不提供「仅图片」组合（mask=8）；
 * 2. 禁止空选择（mask=0）；
 * 3. 允许集合**恰好**是 {9, 13, 15}。
 *
 * 事故依据：`docs/research/2026-11-04-6c2-rulings.md`（批次 42 WS6 越权把 0/8 改成合法，
 * 已于批次 42 §9.2 全部撤回）。
 *
 * 为什么不重复已有用例：
 * - 域层 `BackupSelectionTest` 与数据层 `SmbkSelectionPlanTest` 已经各自钉住自己那一层；
 * - 写侧 `SmbkArchiveCodecTest.create rejects a non-creatable selection…` 已钉住 mask=0/8
 *   在**写任何字节之前**被拒；
 * - 本文件只补两处**没有任何现存断言**的层，以及一处跨层一致性：
 *   a. `BackupReceipt` 的 `selectionMask` init 闸门（回执/台账入口）——此前无任何用例；
 *   b. 归档**读侧**对 mask=0 的拒绝（现有读侧用例只构造了 mask=8）——恢复前最后一道闸门；
 *   c. 域层与数据层的允许集合必须逐字相同（单点漂移即红，而不是只红某一层自己的用例）。
 */
class SixC2RulingInvariantTest {

    /** 2026-10-24 用户裁定：唯一可成包集合。不是从代码里读出来的常量，是**独立写死的期望值**。 */
    private val rulingAllowedMasks = setOf(9, 13, 15)

    @Test
    fun `allowed mask set is exactly nine thirteen fifteen in domain and data layers`() {
        assertEquals(
            "域层允许集合偏离 2026-10-24 用户裁定",
            rulingAllowedMasks,
            BackupSelection.ALLOWED_MASKS,
        )
        assertEquals(
            "数据层允许集合偏离 2026-10-24 用户裁定",
            rulingAllowedMasks,
            SmbkSelectionPlan.allowedMasks,
        )
        assertEquals(
            "两层允许集合必须逐字相同；只改一层就是漂移（批次 42 WS6 的事故形状）",
            BackupSelection.ALLOWED_MASKS,
            SmbkSelectionPlan.allowedMasks,
        )
        for (mask in listOf(0, 8)) {
            assertFalse(
                "mask=$mask 是用户明确裁定不提供的组合，不得出现在域层允许集合",
                mask in BackupSelection.ALLOWED_MASKS,
            )
            assertFalse(
                "mask=$mask 是用户明确裁定不提供的组合，不得出现在数据层允许集合",
                mask in SmbkSelectionPlan.allowedMasks,
            )
        }
    }

    /**
     * 回执入口：`BackupReceipt` 是成包回执/台账/UI 的共同载体。若它接受 0/8，
     * 上层就能"如实"描述一份产品禁止的备份，且现有域/数据层用例都不会发现。
     */
    @Test
    fun `backup receipts refuse the empty and images only masks`() {
        fun receipt(mask: Int) = BackupReceipt(
            createdAtEpochMillis = 1L,
            databaseSchemaVersion = 73,
            problemCount = 0,
            assetCount = 0,
            fileCount = 1,
            totalBytes = 1L,
            selectionMask = mask,
        )

        // 正向对照：允许集合内的三种 mask 必须能构造，证明下面的拒绝不是"构造永远失败"。
        for (mask in rulingAllowedMasks) {
            assertEquals(mask, receipt(mask).selectionMask)
        }
        for (mask in listOf(0, 8)) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                receipt(mask)
            }
            assertTrue(
                "回执拒因必须指向 selection mask：${failure.message}",
                failure.message.orEmpty().contains("selection mask"),
            )
        }
    }

    /**
     * 读侧入口：恢复前 `SmbkArchiveCodec.validate` 必须拒绝一份自称 mask=0 或 mask=8 的清单。
     *
     * 这正是批次 42 WS6 想打开的语义（空包只应用设置 / 仅图片包）——清单可以撒谎，
     * 只要读侧放行，恢复就会按被禁止的选择执行。写侧用例已钉住同一对 mask；这里把
     * 同一向量钉在**读侧**，两层都关死。
     */
    @Test
    fun `an archive declaring the empty or images only selection is rejected before restore`() {
        val database = tempFile("database.sqlite", "database-content")
        val legalSelection = selectionRecord(mask = 9, rowCounts = emptyRowCounts())
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 0,
            createdAtEpochMillis = 12_000L,
            selection = legalSelection,
        )
        val archiveBytes = archive.toByteArray()
        assertTrue(
            "正向对照：合法 mask=9 包必须可读，否则下面的拒绝没有意义",
            SmbkArchiveCodec.validate(ByteArrayInputStream(archiveBytes)) is BackupValidation.Valid,
        )

        for (mask in listOf(0, 8)) {
            val categories = SmbkSelectionPlan.categoriesOf(mask)
            val tampered = rewriteSelection(archiveBytes) { base ->
                base.copy(
                    categoryMask = mask,
                    categories = categories.map { it.name },
                    closureDigest = SmbkSelectionPlan.closureDigest(
                        categories = categories,
                        assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                        rowCounts = base.rowCounts,
                    ),
                )
            }
            val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(tampered))
            assertTrue("mask=$mask 的清单必须在交换前被拒：$validation", validation is BackupValidation.Invalid)
            val reason = (validation as BackupValidation.Invalid).reason
            assertTrue("mask=$mask 拒因必须指向内容选择：$reason", reason.contains("备份内容选择"))
            assertTrue("mask=$mask 拒因必须点名 mask：$reason", reason.contains("mask=$mask"))
        }
    }

    // ---- 夹具 ----

    private fun emptyRowCounts(): Map<String, Long> =
        SmbkSelectionPlan.rowCountKeys.associateWith { 0L }

    private fun selectionRecord(mask: Int, rowCounts: Map<String, Long>): SmbkSelectionRecord {
        val categories = SmbkSelectionPlan.categoriesOf(mask)
        return SmbkSelectionRecord(
            schemaVersion = SmbkSelectionRecord.CURRENT_SCHEMA_VERSION,
            categoryMask = mask,
            categories = categories.map { it.name },
            assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
            closureVersion = SmbkSelectionPlan.CLOSURE_VERSION,
            closureDigest = SmbkSelectionPlan.closureDigest(
                categories = categories,
                assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                rowCounts = rowCounts,
            ),
            rowCounts = rowCounts,
            trimmedTableCount = 0,
        )
    }

    /** 只改 `manifest.json` 的 `selection` 块，其余条目逐字节不变（清单可以撒谎的形状）。 */
    private fun rewriteSelection(
        archiveBytes: ByteArray,
        transform: (SmbkSelectionRecord) -> SmbkSelectionRecord,
    ): ByteArray {
        val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
        val manifest = json.decodeFromString(
            SmbkManifest.serializer(),
            entryText(archiveBytes, "manifest.json"),
        )
        val base = requireNotNull(manifest.selection) { "夹具必须写出 selection" }
        return replaceEntry(
            archiveBytes = archiveBytes,
            entryName = "manifest.json",
            content = json.encodeToString(
                SmbkManifest.serializer(),
                manifest.copy(selection = transform(base)),
            ),
        )
    }

    private fun entryText(archiveBytes: ByteArray, entryName: String): String {
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == entryName) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        throw AssertionError("归档缺少条目 $entryName")
    }

    private fun replaceEntry(archiveBytes: ByteArray, entryName: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            ZipOutputStream(output).use { zipOutput ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    zipOutput.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == entryName) {
                        zipOutput.write(content.toByteArray(Charsets.UTF_8))
                    } else {
                        zipOutput.write(zip.readBytes())
                    }
                    zipOutput.closeEntry()
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun tempFile(name: String, content: String): File {
        val file = File.createTempFile("sixc2-invariant-", "-$name")
        file.deleteOnExit()
        file.outputStream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return file
    }
}
