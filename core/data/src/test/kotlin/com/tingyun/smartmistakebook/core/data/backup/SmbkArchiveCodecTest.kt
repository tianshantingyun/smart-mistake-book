package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SmbkArchiveCodecTest {
    @Test
    fun `create and validate round trip reports exact counts and checksums`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()

        val receipt = SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 29,
            problemCount = 3,
            createdAtEpochMillis = 1_000L,
        )

        assertEquals(3, receipt.problemCount)
        assertEquals(1, receipt.assetCount)
        assertEquals(2, receipt.fileCount)
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(archive.toByteArray()),
        )
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(29, validation.manifest.databaseSchemaVersion)
        assertEquals(2, validation.checkedFileCount)
    }

    @Test
    fun `tampered asset fails validation with a business reason`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 29,
            problemCount = 1,
            createdAtEpochMillis = 1_000L,
        )

        val tampered = ByteArrayInputStream(tamperAsset(archive.toByteArray()))
        val validation = SmbkArchiveCodec.validate(tampered)

        assertTrue(validation is BackupValidation.Invalid)
    }

    @Test
    fun `unpack restores database and assets after full verification`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 2,
            createdAtEpochMillis = 2_000L,
        )
        val destination = File.createTempFile("smbk-unpack", "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()

        val validation = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archive.toByteArray()),
            destination,
        )

        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(30, validation.manifest.databaseSchemaVersion)
        assertEquals(
            "database-content",
            File(destination, "database.sqlite").readText(),
        )
        assertEquals(
            "asset-content",
            File(destination, "assets/${asset.name}").readText(),
        )
    }

    @Test
    fun `unpack rejects traversal paths before writing outside staging`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 3_000L,
        )
        val withTraversal = rewritePath(archive.toByteArray(), "../escape", "escape-content")
        val destination = File.createTempFile("smbk-unpack-safe", "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()

        val validation = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(withTraversal),
            destination,
        )

        assertTrue(validation is BackupValidation.Invalid)
        assertTrue(!File(destination.parentFile, "escape").exists())
    }

    @Test
    fun `missing manifest fails validation with a business reason`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 4_000L,
        )

        val withoutManifest = removeEntries(archive.toByteArray(), setOf("manifest.json"))
        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(withoutManifest))

        assertTrue(validation is BackupValidation.Invalid)
        assertTrue((validation as BackupValidation.Invalid).reason.contains("manifest"))
    }

    @Test
    fun `tampered checksum file fails validation`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 5_000L,
        )

        val tampered = rewriteEntry(
            archive.toByteArray(),
            "checksums.sha256",
            "0000000000000000000000000000000000000000000000000000000000000000 *database.sqlite",
        )
        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(tampered))

        assertTrue(validation is BackupValidation.Invalid)
    }

    @Test
    fun `future format version fails validation before any restore`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 6_000L,
            selection = selectionFixture(
                mask = 15,
                rowCounts = emptyRowCounts(),
                trimmedTableCount = 0,
            ),
        )

        val futureManifest = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
            ZipOutputStream(futureManifest).use { output ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "manifest.json") {
                        val manifest = zip.readBytes().decodeToString()
                        // v2 起清单**显式**写出 formatVersion（读侧不靠默认值猜版本），
                        // 所以这里替换那个键，而不是再插一个——插一个会变成重复键，
                        // 夹具对"读到的是哪个版本"就说了不算。
                        val declaredVersion = "\"formatVersion\":${SmbkManifest.FORMAT_VERSION}"
                        assertTrue(
                            "夹具必须真的由当前版本写出 formatVersion",
                            manifest.contains(declaredVersion),
                        )
                        val future = manifest.replace(declaredVersion, "\"formatVersion\":99")
                        assertTrue("夹具必须真的把版本改成了未来版本", future != manifest)
                        output.write(future.toByteArray())
                    } else {
                        output.write(zip.readBytes())
                    }
                    output.closeEntry()
                    zip.closeEntry()
                }
            }
        }
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(futureManifest.toByteArray()),
        )

        assertTrue(validation is BackupValidation.Invalid)
    }

    @Test
    fun `missing listed asset fails validation before any restore`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 4_000L,
        )

        val withMissingAsset = removeEntries(
            archive.toByteArray(),
            setOf("assets/${asset.name}"),
        )
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(withMissingAsset),
        )

        assertTrue(validation is BackupValidation.Invalid)
    }

    @Test
    fun `missing database fails validation before any restore`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 5_000L,
        )

        val withMissingDatabase = removeEntries(
            archive.toByteArray(),
            setOf("database.sqlite"),
        )
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(withMissingDatabase),
        )

        assertTrue(validation is BackupValidation.Invalid)
    }

    /**
     * AUD-05 的成包面：快照库引用的资产必须真的进包。两边漂开时**成包失败**，
     * 而不是写出一份"库引用了包内没有的图"的归档（那正是旧成包顺序留下的形态）。
     */
    @Test
    fun `create refuses a referenced asset that is not part of the package`() {
        val database = tempFile("database.sqlite", "database-content")
        val packaged = tempFile("asset-packaged.png", "packaged-content")
        val referencedOnly = tempFile("asset-referenced.png", "referenced-content")
        val archive = ByteArrayOutputStream()

        val failure = runCatching {
            SmbkArchiveCodec.create(
                archive = archive,
                database = database,
                assets = listOf(packaged),
                referencedAssets = listOf(packaged, referencedOnly),
                databaseSchemaVersion = 30,
                problemCount = 1,
                createdAtEpochMillis = 7_000L,
            )
        }

        assertTrue("被引用的资产不在包内时必须成包失败", failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue("校验失败不得留下半个归档", archive.size() == 0)
    }

    /**
     * AUD-05 的解包面：包自己声明的引用集合里少一张就拒绝——校验/恢复都不必等到
     * 读库那一步才发现图没了。旧包（没有这张清单）不受影响，见 [SmbkManifest]。
     */
    @Test
    fun `archive whose declared referenced asset is missing fails validation`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 7_000L,
        )

        val manifest = manifestText(archive.toByteArray())
        val declaredArray = "[\"assets/${asset.name}\"]"
        assertTrue("夹具必须让 manifest 真的声明了引用集合", manifest.contains(declaredArray))
        val tamperedManifest = manifest.replace(declaredArray, "[\"assets/gone.png\"]")
        assertTrue(tamperedManifest != manifest)
        val tampered = rewriteEntry(archive.toByteArray(), "manifest.json", tamperedManifest)

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(tampered))

        assertTrue(validation is BackupValidation.Invalid)
        assertTrue(
            (validation as BackupValidation.Invalid).reason.contains("题图缺失"),
        )
    }

    /**
     * AUD-05 的计数面：manifest 记的是**快照库**的题数（调用方从快照库里读出来交给编解码层
     * 的那个数），包内资产集合也只有快照库引用的那一份闭包——快照之后才落盘的图不在包里。
     */
    @Test
    fun `create records the snapshot problem count and packages only the referenced closure`() {
        val database = tempFile("database.sqlite", "database-content")
        val referenced = tempFile("asset-referenced.png", "referenced-content")
        val notReferenced = tempFile("asset-orphan.png", "orphan-content")
        val archive = ByteArrayOutputStream()

        val receipt = SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(referenced),
            referencedAssets = listOf(referenced),
            databaseSchemaVersion = 30,
            problemCount = 7,
            createdAtEpochMillis = 7_000L,
        )

        assertEquals(7, receipt.problemCount)
        assertEquals(1, receipt.assetCount)
        assertEquals(2, receipt.fileCount)
        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(archive.toByteArray()),
        )
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals("manifest 的题数取自快照库", 7, validation.manifest.problemCount)
        assertEquals(1, validation.manifest.assetCount)
        assertEquals(2, validation.checkedFileCount)

        val destination = File.createTempFile("smbk-unpack-closure", "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()
        SmbkArchiveCodec.unpack(ByteArrayInputStream(archive.toByteArray()), destination)
        assertEquals(
            "包内资产集合必须正好是传入的那份引用闭包，一个不多一个不少",
            listOf(referenced.name),
            File(destination, "assets").listFiles().orEmpty().map { it.name }.sorted(),
        )
        assertTrue(!File(destination, "assets/${notReferenced.name}").exists())
    }

    @Test
    fun `unpack rejects per-entry zip bombs by compression ratio`() {
        val bomb = ByteArrayOutputStream()
        ZipOutputStream(bomb).use { zip ->
            // 8 MB of zeros deflate to a few KB: ratio far above the 1:100 budget.
            zip.putNextEntry(ZipEntry("database.sqlite"))
            zip.write(ByteArray(8 * 1024 * 1024))
            zip.closeEntry()
        }
        val destination = File.createTempFile("smbk-unpack-bomb", "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()

        val validation = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(bomb.toByteArray()),
            destination,
        )

        assertTrue(validation is BackupValidation.Invalid)
        assertTrue((validation as BackupValidation.Invalid).reason.contains("压缩比"))
    }

    /**
     * KD-53：`database.sqlite` 有自己的预算，不再与单张题图共用同一道单条目门。
     * 用注入的小预算把「数据库 > 单图上限但 < 数据库上限」这条边界钉在小文件上。
     */
    @Test
    fun `database entry uses its own budget while assets keep the single entry budget`() {
        val database = tempBinaryFile("database.sqlite", incompressibleBytes(1_024, seed = 1L))
        val asset = tempBinaryFile("asset-a.png", incompressibleBytes(128, seed = 2L))
        val limits = SmbkResourceLimits(
            maxSingleEntryBytes = 256L,
            maxDatabaseEntryBytes = 4_096L,
            maxTotalDecompressedBytes = 64L * 1_024L,
        )
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 1_000L,
            limits = limits,
        )
        val archiveBytes = archive.toByteArray()

        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(archiveBytes),
            limits = limits,
        )
        assertTrue(
            "数据库条目高于单图上限、低于数据库上限时必须通过：$validation",
            validation is BackupValidation.Valid,
        )

        val destination = unpackDestination("smbk-db-budget")
        val unpacked = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archiveBytes),
            destination,
            limits,
        )
        assertTrue("恢复侧与校验侧同口径，必须同样通过：$unpacked", unpacked is BackupValidation.Valid)
        assertArrayEquals(
            "数据库条目必须逐位解包",
            database.readBytes(),
            File(destination, "database.sqlite").readBytes(),
        )
    }

    /**
     * KD-53 负向：数据库条目超过**自己的**预算时，校验与解包都必须拒绝（同口径）。
     * 单图上限放宽到很大，证明拒因来自数据库预算而不是别的门。
     */
    @Test
    fun `database entry above its own budget is rejected by both validate and unpack`() {
        val database = tempBinaryFile("database.sqlite", incompressibleBytes(2_048, seed = 3L))
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 30,
            problemCount = 1,
            createdAtEpochMillis = 1_000L,
        )
        val archiveBytes = archive.toByteArray()
        val limits = SmbkResourceLimits(
            maxSingleEntryBytes = 1_000_000L,
            maxDatabaseEntryBytes = 1_024L,
            maxTotalDecompressedBytes = 1_000_000L,
        )

        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(archiveBytes),
            limits = limits,
        )
        assertTrue(validation is BackupValidation.Invalid)
        assertTrue(
            "拒因必须指向 database.sqlite：${(validation as BackupValidation.Invalid).reason}",
            validation.reason.contains("database.sqlite"),
        )

        val destination = unpackDestination("smbk-db-over-budget")
        val unpacked = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archiveBytes),
            destination,
            limits,
        )
        assertTrue(unpacked is BackupValidation.Invalid)
        assertTrue(
            "恢复侧拒因必须与校验侧同一道门：${(unpacked as BackupValidation.Invalid).reason}",
            unpacked.reason.contains("database.sqlite"),
        )
        assertTrue(
            "被拒的数据库条目不能留在 staging 里",
            !File(destination, "database.sqlite").exists(),
        )
    }

    /**
     * KD-53 成包面：不在成包时写出一个"自己都验不过"的归档。这条检查必须在写任何
     * 字节之前失败（归档保持为空），而不是先写半包再让校验拒绝。
     */
    @Test
    fun `create refuses a database beyond its budget before writing any bytes`() {
        val database = tempBinaryFile("database.sqlite", incompressibleBytes(1_024, seed = 4L))
        val archive = ByteArrayOutputStream()

        val failure = runCatching {
            SmbkArchiveCodec.create(
                archive = archive,
                database = database,
                assets = emptyList(),
                referencedAssets = emptyList(),
                databaseSchemaVersion = 30,
                problemCount = 0,
                createdAtEpochMillis = 1_000L,
                limits = SmbkResourceLimits(maxDatabaseEntryBytes = 512L),
            )
        }

        assertTrue("超过数据库条目预算时必须成包失败", failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals("校验失败不得留下半个归档", 0, archive.size())
    }

    /**
     * KD-53 成包面的总量门：数据库 + 题图超过总预算时，同样在写任何字节之前失败。
     * 这条与校验侧的 `checkTotalBudget` 同口径（同一 `SmbkResourceLimits`）。
     */
    @Test
    fun `create refuses a payload beyond the total decompression budget before writing any bytes`() {
        val database = tempBinaryFile("database.sqlite", incompressibleBytes(512, seed = 5L))
        val asset = tempBinaryFile("asset-a.png", incompressibleBytes(512, seed = 6L))
        val archive = ByteArrayOutputStream()

        val failure = runCatching {
            SmbkArchiveCodec.create(
                archive = archive,
                database = database,
                assets = listOf(asset),
                referencedAssets = listOf(asset),
                databaseSchemaVersion = 30,
                problemCount = 0,
                createdAtEpochMillis = 1_000L,
                limits = SmbkResourceLimits(maxTotalDecompressedBytes = 700L),
            )
        }

        assertTrue("超过总量预算时必须成包失败", failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals("校验失败不得留下半个归档", 0, archive.size())
    }

    /**
     * KD-53 的真实体积回归：设备实测库 365,133,824 B（批次 13 设备读数）。旧上限
     * （100 MB 单条目）会让这份自建备份"自己都验不过"；这里走完整的
     * create → validate → unpack，并逐位比对解包出来的库。
     *
     * 数据用固定种子的伪随机字节（不可压缩），确保压缩比门不会先于预算门拒掉样本。
     * 磁盘占用约 1.5 GB（源库 + 归档 + 校验 spool + 解包副本），超时给 5 分钟。
     * 可用 `-Dsmbk.largeDbBytes=<n>` 缩小样本（默认就是设备实测体积）。
     */
    @Test(timeout = 300_000L)
    fun `near device size database round trips through create validate and unpack`() {
        val databaseBytes = System.getProperty("smbk.largeDbBytes")?.toLongOrNull()
            ?: 365_133_824L
        val root = File.createTempFile("smbk-large-db", "").apply {
            delete()
            mkdirs()
        }
        root.deleteOnExit()
        try {
            val database = File(root, "database.sqlite")
            writePseudoRandom(database, databaseBytes, seed = 0x5EEDL)
            val asset = File(root, "asset-large.png")
            writePseudoRandom(asset, 64L * 1_024L, seed = 0xA55E7L)
            val archive = File(root, "large.smbk")

            val receipt = BufferedOutputStream(FileOutputStream(archive)).use { output ->
                SmbkArchiveCodec.create(
                    archive = output,
                    database = database,
                    assets = listOf(asset),
                    referencedAssets = listOf(asset),
                    databaseSchemaVersion = 67,
                    problemCount = 1_234,
                    createdAtEpochMillis = 1_000L,
                )
            }
            assertEquals(
                "回执总量必须等于源库 + 题图",
                databaseBytes + asset.length(),
                receipt.totalBytes,
            )

            val scratch = File(root, "validate-scratch").apply { mkdirs() }
            val validation = FileInputStream(archive).use { input ->
                SmbkArchiveCodec.validate(input, scratch)
            }
            assertTrue("大库备份必须能校验：$validation", validation is BackupValidation.Valid)
            validation as BackupValidation.Valid
            assertEquals(2, validation.checkedFileCount)

            val destination = File(root, "unpacked").apply { mkdirs() }
            val unpacked = FileInputStream(archive).use { input ->
                SmbkArchiveCodec.unpack(input, destination)
            }
            assertTrue("大库备份必须能解包/恢复：$unpacked", unpacked is BackupValidation.Valid)
            val restored = File(destination, "database.sqlite")
            assertEquals(databaseBytes, restored.length())
            assertEquals(
                "解包出来的数据库必须与源快照逐位一致",
                sha256Hex(database),
                sha256Hex(restored),
            )
            assertEquals(
                asset.length(),
                File(destination, "assets/asset-large.png").length(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * v2 成包面（6C-1）：版本戳与设置载荷都进包，且校验/解包都读得回来。
     * 摘要（`settingsDigest`）必须与包内 `settings.json` 的字节一致——存在性声明与
     * 载荷本身不是一回事。
     */
    @Test
    fun `v2 round trip carries the version stamps and the settings payload`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val settings = settingsFixture()
        val settingsBytes = SmbkSettingsCodec.encode(settings)
        val archive = ByteArrayOutputStream()

        val receipt = SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 32,
            problemCount = 3,
            createdAtEpochMillis = 8_000L,
            metadata = SmbkArchiveMetadata(
                appVersionName = "0.2.0-localfirst",
                appVersionCode = 2,
                contentVersion = "a73be83c7319c649",
            ),
            settings = settings,
        )

        assertEquals("数据库 + 设置 + 一张图", 3, receipt.fileCount)
        assertEquals(database.length() + asset.length() + settingsBytes.size, receipt.totalBytes)

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(archive.toByteArray()))
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(
            "没有 selection 的写出面必须是 v2（v3 的 selection 块必填）",
            SmbkManifest.LEGACY_FORMAT_VERSION,
            validation.manifest.formatVersion,
        )
        assertEquals("0.2.0-localfirst", validation.manifest.appVersionName)
        assertEquals(2L, validation.manifest.appVersionCode)
        assertEquals("a73be83c7319c649", validation.manifest.contentVersion)
        assertTrue("包内带了 settings.json，hasSettings 必须为真", validation.manifest.hasSettings)
        assertEquals(3, validation.checkedFileCount)

        val destination = unpackDestination("smbk-v2-settings")
        val unpacked = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archive.toByteArray()),
            destination,
        )
        assertTrue(unpacked is BackupValidation.Valid)
        assertEquals(
            "设置载荷必须逐字段往返",
            settings,
            SmbkSettingsCodec.decode(File(destination, "settings.json").readText()),
        )
        assertEquals("database-content", File(destination, "database.sqlite").readText())
        assertEquals("asset-content", File(destination, "assets/${asset.name}").readText())

        // 排除项的结构性钉子：设置文档里不许出现 API Key / 投递历史 / 授权租约 / sleep journal
        // 的字段名——它们要么是设备本地凭据，要么是随时间滚动的观测，搬进包里只会说谎。
        val settingsJson = File(destination, "settings.json").readText()
        listOf("apikey", "delivery", "lease", "sleep").forEach { excluded ->
            assertTrue(
                "设置文档不许带 $excluded 字段：$settingsJson",
                !settingsJson.lowercase().contains(excluded),
            )
        }
    }

    /** 不带设置的 v2 包：`hasSettings=false`，包内没有 `settings.json`，读侧不许凭空造一份。 */
    @Test
    fun `v2 archive without settings declares none and carries none`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 9_000L,
        )

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(archive.toByteArray()))
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(
            "没有 selection 的写出面必须是 v2（v3 的 selection 块必填）",
            SmbkManifest.LEGACY_FORMAT_VERSION,
            validation.manifest.formatVersion,
        )
        assertTrue(!validation.manifest.hasSettings)
        assertNull(
            "v2 包没有 selection 块，必须读作全量而不是空选择",
            validation.manifest.selection,
        )

        val destination = unpackDestination("smbk-v2-no-settings")
        assertTrue(
            SmbkArchiveCodec.unpack(ByteArrayInputStream(archive.toByteArray()), destination)
                is BackupValidation.Valid,
        )
        assertTrue(!File(destination, "settings.json").exists())
    }

    /**
     * v1 旧包（`formatVersion` 1，没有设置与版本戳）在新 app 上照旧可读可解包：
     * 缺省字段读作 null/false，不因此改变旧包的既有行为。
     */
    @Test
    fun `v1 archive without settings stays readable and unpacks without a settings file`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = buildV1Archive(database, listOf(asset))

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(archive))
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(1, validation.manifest.formatVersion)
        assertTrue(!validation.manifest.hasSettings)
        assertEquals(null, validation.manifest.appVersionName)
        assertEquals(null, validation.manifest.appVersionCode)
        assertEquals(null, validation.manifest.contentVersion)
        assertEquals(2, validation.checkedFileCount)
        assertNull("v1 旧包没有 selection 块，读作全量", validation.manifest.selection)

        val destination = unpackDestination("smbk-v1-compat")
        val unpacked = SmbkArchiveCodec.unpack(ByteArrayInputStream(archive), destination)
        assertTrue(unpacked is BackupValidation.Valid)
        assertEquals("database-content", File(destination, "database.sqlite").readText())
        assertEquals("asset-content", File(destination, "assets/${asset.name}").readText())
        assertTrue(
            "v1 旧包没有设置载荷，解包不该凭空造一个",
            !File(destination, "settings.json").exists(),
        )
    }

    /**
     * v3 成包面（6C-2）：`selection` 块逐字段往返，且 `closureDigest` 必须与
     * `SmbkSelectionPlan` 的 v2 算法同源（同输入重算相等）。
     */
    @Test
    fun `v3 round trip carries the selection block and its digest`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = ByteArrayOutputStream()
        val rowCounts = emptyRowCounts().toMutableMap().apply {
            put("problem", 4L)
            put("canonical_source_asset", 1L)
            put("library_search_fts", 4L)
        }
        val selection = selectionFixture(
            mask = 9,
            rowCounts = rowCounts,
            trimmedTableCount = 33,
        )

        val receipt = SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = listOf(asset),
            referencedAssets = listOf(asset),
            databaseSchemaVersion = 32,
            problemCount = 4,
            createdAtEpochMillis = 11_000L,
            selection = selection,
        )

        assertEquals("回执必须如实带出选择", 9, receipt.selectionMask)
        assertEquals(33, receipt.trimmedTableCount)

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(archive.toByteArray()))
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(3, validation.manifest.formatVersion)
        assertEquals(SmbkManifest.FORMAT_VERSION, validation.manifest.formatVersion)
        val summary = validation.manifest.selection
            ?: error("v3 包必须读出 selection")
        assertEquals(9, summary.mask)
        assertEquals(
            setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES),
            summary.categories,
        )
        assertEquals(
            selection.closureDigest,
            SmbkSelectionPlan.closureDigest(
                categories = SmbkSelectionPlan.categoriesOf(9),
                assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                rowCounts = rowCounts,
            ),
        )

        val destination = unpackDestination("smbk-v3-selection")
        val unpacked = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(archive.toByteArray()),
            destination,
        )
        assertTrue(unpacked is BackupValidation.Valid)
        assertEquals(9, (unpacked as BackupValidation.Valid).manifest.selection?.mask)
    }

    /**
     * v3 读侧闸门（T5）：规则版本/mask/categories/资产策略/rowCounts/trimmedTableCount/digest
     * 任何一项不符都在**交换前**拒绝；v2 格式带 selection 同样是自相矛盾的清单。
     *
     * 这里逐项改一份自建 v3 包的 manifest（其余条目逐字节不变）——这正是恢复校验要挡的
     * 形状：清单可以撒谎，读侧不能靠"能解析"就放行。
     */
    @Test
    fun `v3 selection read side rejects unsupported or inconsistent blocks`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        val rowCounts = emptyRowCounts().toMutableMap().apply { put("problem", 1L) }
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 12_000L,
            selection = selectionFixture(mask = 9, rowCounts = rowCounts, trimmedTableCount = 10),
        )
        val archiveBytes = archive.toByteArray()
        val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
        val manifest = json.decodeFromString(SmbkManifest.serializer(), manifestText(archiveBytes))
        val baseSelection = requireNotNull(manifest.selection) { "夹具必须写出 selection" }

        fun validateWith(
            selection: SmbkSelectionRecord = baseSelection,
            formatVersion: Int = manifest.formatVersion,
        ): BackupValidation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(
                    archiveBytes,
                    "manifest.json",
                    json.encodeToString(
                        SmbkManifest.serializer(),
                        manifest.copy(formatVersion = formatVersion, selection = selection),
                    ),
                ),
            ),
        )

        val nine = SmbkSelectionPlan.categoriesOf(9)
        val cases: List<Pair<String, BackupValidation>> = listOf(
            "closureVersion" to validateWith(baseSelection.copy(closureVersion = 1)),
            "schemaVersion" to validateWith(baseSelection.copy(schemaVersion = 1)),
            "mask=8" to validateWith(
                baseSelection.copy(
                    categoryMask = 8,
                    categories = SmbkSelectionPlan.categoriesOf(8).map { it.name },
                    closureDigest = SmbkSelectionPlan.closureDigest(
                        SmbkSelectionPlan.categoriesOf(8),
                        SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                        baseSelection.rowCounts,
                    ),
                ),
            ),
            "categories" to validateWith(
                baseSelection.copy(categories = listOf("IMAGES")),
            ),
            "assetPolicy" to validateWith(
                baseSelection.copy(
                    assetPolicy = "ABSENT",
                    closureDigest = SmbkSelectionPlan.closureDigest(
                        nine,
                        "ABSENT",
                        baseSelection.rowCounts,
                    ),
                ),
            ),
            "rowCounts" to validateWith(
                baseSelection.copy(rowCounts = baseSelection.rowCounts - "problem"),
            ),
            "trimmedTableCount" to validateWith(baseSelection.copy(trimmedTableCount = 79)),
            "closureDigest" to validateWith(
                baseSelection.copy(closureDigest = "0".repeat(64)),
            ),
            "格式版本" to validateWith(formatVersion = 2),
        )

        cases.forEach { (marker, validation) ->
            assertTrue("$marker 必须被拒绝：$validation", validation is BackupValidation.Invalid)
            val reason = (validation as BackupValidation.Invalid).reason
            assertTrue(
                "$marker 的拒因必须可读且指向内容选择：$reason",
                reason.contains("备份内容选择"),
            )
            assertTrue("$marker 的拒因必须点名具体字段：$reason", reason.contains(marker))
        }
    }

    /**
     * v2 包（没有 `selection` 键）照旧可读可解包：缺省读作 null = 整库全量，
     * 不能把它当成「空选择」而让恢复侧拒绝一份合法的旧包。
     */
    @Test
    fun `v2 archive without selection stays readable and reads as the full backup`() {
        val database = tempFile("database.sqlite", "database-content")
        val asset = tempFile("asset-a.png", "asset-content")
        val archive = buildLegacyArchive(database, listOf(asset), formatVersion = 2)

        val validation = SmbkArchiveCodec.validate(ByteArrayInputStream(archive))
        assertTrue(validation is BackupValidation.Valid)
        validation as BackupValidation.Valid
        assertEquals(2, validation.manifest.formatVersion)
        assertNull(
            "v2 包没有 selection 块，必须读作全量而不是空选择",
            validation.manifest.selection,
        )

        val destination = unpackDestination("smbk-v2-compat")
        assertTrue(
            SmbkArchiveCodec.unpack(ByteArrayInputStream(archive), destination)
                is BackupValidation.Valid,
        )
        assertEquals("database-content", File(destination, "database.sqlite").readText())
    }

    /**
     * v3 必须带 selection（设计 §3.2-S1）：被剥掉 selection 的 v3 清单不能被读成"全量"，
     * 否则一份子集/残缺包会按全量恢复并清空本机未包含的类别。`null` 与"键被删掉"两种
     * 形状都要拒绝。
     */
    @Test
    fun `v3 archive without a selection block is rejected`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 14_000L,
            selection = selectionFixture(
                mask = 15,
                rowCounts = emptyRowCounts(),
                trimmedTableCount = 0,
            ),
        )
        val archiveBytes = archive.toByteArray()
        val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
        val manifest = json.decodeFromString(
            SmbkManifest.serializer(),
            manifestText(archiveBytes),
        )
        val strippedManifest = json.encodeToString(
            SmbkManifest.serializer(),
            manifest.copy(formatVersion = SmbkManifest.FORMAT_VERSION, selection = null),
        )
        val withoutKey = strippedManifest.replace(",\"selection\":null", "")
        assertTrue("夹具必须真的删掉 selection 键", withoutKey != strippedManifest)

        listOf("显式 null" to strippedManifest, "缺少键" to withoutKey).forEach { (label, text) ->
            val validation = SmbkArchiveCodec.validate(
                ByteArrayInputStream(rewriteEntry(archiveBytes, "manifest.json", text)),
            )
            assertTrue("$label 必须被拒绝：$validation", validation is BackupValidation.Invalid)
            val reason = (validation as BackupValidation.Invalid).reason
            assertTrue(
                "$label 的拒因必须说明缺内容选择块：$reason",
                reason.contains("内容选择块"),
            )
        }
        // 恢复走 unpack，同一道 finalizeValidation 也必须拒绝。
        val unpacked = SmbkArchiveCodec.unpack(
            ByteArrayInputStream(rewriteEntry(archiveBytes, "manifest.json", withoutKey)),
            unpackDestination("smbk-v3-no-selection"),
        )
        assertTrue("unpack 也必须拒绝 v3 缺 selection：$unpacked", unpacked is BackupValidation.Invalid)
    }

    /**
     * 非法选择在**写任何字节之前**拒绝：0/8、未闭合 mask、categories 与 mask 不符、
     * rowCounts 漏表、digest 不符都抛 `IllegalArgumentException`，`archive` 保持为空。
     * 仓储层还有 `BackupSelection.requireCreatable` 的同一道闸门（先于快照）。
     */
    @Test
    fun `create rejects a non-creatable selection before writing any bytes`() {
        val database = tempFile("database.sqlite", "database-content")
        val validRowCounts = emptyRowCounts()
        val validCategories = SmbkSelectionPlan.categoriesOf(9).map { it.name }
        val validDigest = SmbkSelectionPlan.closureDigest(
            categories = SmbkSelectionPlan.categoriesOf(9),
            assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
            rowCounts = validRowCounts,
        )
        fun record(
            mask: Int,
            categories: List<String> = validCategories,
            rowCounts: Map<String, Long> = validRowCounts,
            digest: String = validDigest,
        ) = SmbkSelectionRecord(
            schemaVersion = SmbkSelectionRecord.CURRENT_SCHEMA_VERSION,
            categoryMask = mask,
            categories = categories,
            assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
            closureVersion = SmbkSelectionPlan.CLOSURE_VERSION,
            closureDigest = digest,
            rowCounts = rowCounts,
            trimmedTableCount = 0,
        )

        val invalid = listOf(
            "空选择 mask=0" to record(mask = 0, categories = emptyList()),
            "仅图片 mask=8" to record(
                mask = 8,
                categories = listOf(BackupContentCategory.IMAGES.name),
            ),
            "未闭合 mask=1" to record(
                mask = 1,
                categories = listOf(BackupContentCategory.ISSUE_BOOK.name),
            ),
            "categories 与 mask 不符" to record(
                mask = 9,
                categories = listOf(
                    BackupContentCategory.MASTERY.name,
                    BackupContentCategory.IMAGES.name,
                ),
            ),
            "rowCounts 漏表" to record(
                mask = 9,
                rowCounts = mapOf("problem" to 1L),
            ),
            "digest 不符" to record(mask = 9, digest = "0".repeat(64)),
        )

        invalid.forEach { (label, selection) ->
            val archive = ByteArrayOutputStream()
            val failure = assertThrows(IllegalArgumentException::class.java) {
                SmbkArchiveCodec.create(
                    archive = archive,
                    database = database,
                    assets = emptyList(),
                    referencedAssets = emptyList(),
                    databaseSchemaVersion = 32,
                    problemCount = 0,
                    createdAtEpochMillis = 12_000L,
                    selection = selection,
                )
            }
            assertEquals("$label 必须不产出任何归档字节", 0, archive.size())
            assertTrue(
                "$label 的拒因必须说明选择非法：${failure.message}",
                failure.message.orEmpty().contains("备份内容选择"),
            )
        }
    }

    /** >3 拒：读不懂的格式版本必须在交换前拒绝，文案沿用既有那句。 */
    @Test
    fun `archive format version above three is rejected with the existing reason`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 10_000L,
            selection = selectionFixture(
                mask = 15,
                rowCounts = emptyRowCounts(),
                trimmedTableCount = 0,
            ),
        )

        val manifest = manifestText(archive.toByteArray())
        val declaredVersion = "\"formatVersion\":${SmbkManifest.FORMAT_VERSION}"
        assertTrue("夹具必须真的由当前版本写出 formatVersion", manifest.contains(declaredVersion))
        val futureManifest = manifest.replace(declaredVersion, "\"formatVersion\":4")
        assertTrue("夹具必须真的把版本改成了未来版本", futureManifest != manifest)

        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(archive.toByteArray(), "manifest.json", futureManifest),
            ),
        )

        assertTrue(validation is BackupValidation.Invalid)
        assertEquals(
            "备份格式版本过新，暂不支持恢复",
            (validation as BackupValidation.Invalid).reason,
        )
    }

    /**
     * settingsDigest 校验：载荷被改（摘要与字节对不上）与清单里的摘要被改（存在性声明与
     * 字节不是一回事）都必须拒绝。只对"有没有设置"不对"字节是不是那份"，改过的设置就能过。
     */
    @Test
    fun `tampered settings payload or declared digest is rejected`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 11_000L,
            settings = settingsFixture(),
        )
        val archiveBytes = archive.toByteArray()

        val tamperedPayload = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(archiveBytes, "settings.json", "{\"schemaVersion\":1}"),
            ),
        )
        assertTrue(tamperedPayload is BackupValidation.Invalid)
        assertTrue(
            "拒因必须指向 settings.json，实际：${(tamperedPayload as BackupValidation.Invalid).reason}",
            tamperedPayload.reason.contains("settings.json"),
        )

        val manifest = manifestText(archiveBytes)
        val digestMarker = "\"settingsDigest\":\""
        val markerStart = manifest.indexOf(digestMarker)
        assertTrue("夹具必须真的写出 settingsDigest", markerStart >= 0)
        val digestStart = markerStart + digestMarker.length
        val digest = manifest.substring(digestStart, manifest.indexOf('"', digestStart))
        assertEquals("settingsDigest 必须是 SHA-256 十六进制", 64, digest.length)
        val tamperedDigest = manifest.replace(
            "$digestMarker$digest\"",
            "$digestMarker${"0".repeat(64)}\"",
        )
        assertTrue("夹具必须真的改掉摘要", tamperedDigest != manifest)

        val digestValidation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(archiveBytes, "manifest.json", tamperedDigest),
            ),
        )
        assertTrue(digestValidation is BackupValidation.Invalid)
        assertEquals(
            "settings.json 校验失败",
            (digestValidation as BackupValidation.Invalid).reason,
        )
    }

    /**
     * 设置文档的 schema 版本门：读不懂的设置必须在**交换前**可读拒绝（与归档 `formatVersion`
     * 同一纪律）。夹具把载荷与清单摘要**一起**换新，否则先撞上摘要门，测的就不是版本门。
     */
    @Test
    fun `settings schema version above one is rejected with a readable reason`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 13_000L,
            settings = settingsFixture(),
        )
        val archiveBytes = archive.toByteArray()
        val manifest = manifestText(archiveBytes)
        val digestMarker = "\"settingsDigest\":\""
        val digestStart = manifest.indexOf(digestMarker) + digestMarker.length
        val declaredDigest = manifest.substring(digestStart, manifest.indexOf('"', digestStart))

        val currentDocument = SmbkSettingsCodec.encode(settingsFixture()).decodeToString()
        val newerDocument = currentDocument.replace(
            "\"schemaVersion\":${SmbkSettingsDocumentV1.SETTINGS_SCHEMA_VERSION}",
            "\"schemaVersion\":${SmbkSettingsDocumentV1.MAX_READABLE_SCHEMA_VERSION + 1}",
        )
        assertTrue("夹具必须真的把设置版本改新", newerDocument != currentDocument)
        val alignedManifest = manifest.replace(
            "$digestMarker$declaredDigest\"",
            "$digestMarker${sha256Hex(newerDocument.toByteArray())}\"",
        )
        assertTrue("夹具必须把摘要与新载荷对准", alignedManifest != manifest)

        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(
                    rewriteEntry(archiveBytes, "settings.json", newerDocument),
                    "manifest.json",
                    alignedManifest,
                ),
            ),
        )

        assertTrue(validation is BackupValidation.Invalid)
        assertEquals(
            "备份设置版本过新，暂不支持恢复",
            (validation as BackupValidation.Invalid).reason,
        )
    }

    /**
     * 声明有设置、包内却没有：不许读成"旧包、没有设置"——那正是"设置丢了"被伪装成
     * 正常恢复的形态。`hasSettings` 与包内条目必须一致。
     */
    @Test
    fun `archive that declares settings but carries none is rejected`() {
        val database = tempFile("database.sqlite", "database-content")
        val archive = ByteArrayOutputStream()
        SmbkArchiveCodec.create(
            archive = archive,
            database = database,
            assets = emptyList(),
            referencedAssets = emptyList(),
            databaseSchemaVersion = 32,
            problemCount = 1,
            createdAtEpochMillis = 12_000L,
        )

        val manifest = manifestText(archive.toByteArray())
        val declaredNoSettings = "\"hasSettings\":false"
        assertTrue("夹具必须真的写出 hasSettings", manifest.contains(declaredNoSettings))
        val lyingManifest = manifest.replace(declaredNoSettings, "\"hasSettings\":true")
        assertTrue(lyingManifest != manifest)

        val validation = SmbkArchiveCodec.validate(
            ByteArrayInputStream(
                rewriteEntry(archive.toByteArray(), "manifest.json", lyingManifest),
            ),
        )

        assertTrue(validation is BackupValidation.Invalid)
        assertEquals(
            "备份声明包含设置但缺少 settings.json",
            (validation as BackupValidation.Invalid).reason,
        )
    }

    private fun settingsFixture(): SmbkSettingsDocumentV1 = SmbkSettingsDocumentV1(
        desiredRetention = 0.9,
        examCalendar = listOf(
            ExamCalendarEntry(
                entryId = "exam-1",
                subject = "MATH",
                examEpochDay = 20_700,
                title = "期末",
            ),
        ),
        optimizedFsrsParameters = List(21) { index -> 0.05 * (index + 1) },
        reviewReminderEnabled = true,
        reviewReminderMinutesAfterMidnight = 18 * 60 + 30,
    )

    /** `rowCounts` 的 86 表全零底稿（manifest 摘要必须覆盖全部表，0 也写）。 */
    private fun emptyRowCounts(): Map<String, Long> =
        SmbkSelectionPlan.rowCountKeys.associateWith { 0L }

    /** v3 `selection` 夹具：digest 用与成包侧同一个 `SmbkSelectionPlan` 算法。 */
    private fun selectionFixture(
        mask: Int,
        rowCounts: Map<String, Long>,
        trimmedTableCount: Int,
    ): SmbkSelectionRecord {
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
            trimmedTableCount = trimmedTableCount,
        )
    }

    /**
     * v1/v2 旧包夹具：清单里**没有** selection、v2 的版本戳与设置字段（那会儿它们还不存在），
     * 摘要/大小仍按旧约定写全，好让兼容读走的是与旧 app 相同的形状。
     */
    private fun buildV1Archive(database: File, assets: List<File>): ByteArray =
        buildLegacyArchive(database, assets, formatVersion = 1)

    private fun buildLegacyArchive(
        database: File,
        assets: List<File>,
        formatVersion: Int,
    ): ByteArray {
        val records = listOf(
            Triple("database.sqlite", sha256Hex(database.readBytes()), database.length()),
        ) + assets.map { asset ->
            Triple("assets/${asset.name}", sha256Hex(asset.readBytes()), asset.length())
        }
        val manifest = buildString {
            append("{")
            append("\"formatVersion\":$formatVersion,")
            append("\"databaseSchemaVersion\":29,")
            append("\"createdAt\":1000,")
            append("\"problemCount\":1,")
            append("\"assetCount\":${assets.size},")
            append("\"hashAlgorithm\":\"SHA-256\",")
            append("\"files\":[")
            records.forEachIndexed { index, record ->
                if (index > 0) append(",")
                append("{\"path\":\"${record.first}\",\"sha256\":\"${record.second}\",")
                append("\"byteSize\":${record.third}}")
            }
            append("]}")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("database.sqlite"))
            zip.write(database.readBytes())
            zip.closeEntry()
            assets.forEach { asset ->
                zip.putNextEntry(ZipEntry("assets/${asset.name}"))
                zip.write(asset.readBytes())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("checksums.sha256"))
            zip.write(
                records.joinToString(separator = "\n") { record ->
                    "${record.second} *${record.first}"
                }.toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun unpackDestination(prefix: String): File {
        val destination = File.createTempFile(prefix, "").apply {
            delete()
            mkdirs()
        }
        destination.deleteOnExit()
        return destination
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    /** KD-53 大库样本的逐位比对：流式算摘要，不把 365 MB 读进内存。 */
    private fun sha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun tempBinaryFile(name: String, bytes: ByteArray): File {
        val file = File.createTempFile(name, ".tmp")
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file
    }

    /** 固定种子的伪随机字节：不可压缩，避免压缩比门先于预算门拒掉边界样本。 */
    private fun incompressibleBytes(size: Int, seed: Long): ByteArray =
        ByteArray(size).also { bytes -> java.util.Random(seed).nextBytes(bytes) }

    private fun writePseudoRandom(file: File, size: Long, seed: Long) {
        val random = java.util.Random(seed)
        val buffer = ByteArray(64 * 1024)
        file.outputStream().buffered(64 * 1024).use { output ->
            var remaining = size
            while (remaining > 0L) {
                random.nextBytes(buffer)
                val chunk = minOf(remaining, buffer.size.toLong()).toInt()
                output.write(buffer, 0, chunk)
                remaining -= chunk
            }
        }
    }

    private fun tamperAsset(archiveBytes: ByteArray): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        entries["assets/asset-a.png"] = "tampered-content".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Reads the manifest entry text out of an archive so one field can be tampered with. */
    private fun manifestText(archiveBytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") return zip.readBytes().decodeToString()
                zip.closeEntry()
            }
        }
        error("archive has no manifest.json entry")
    }

    private fun tempFile(name: String, content: String): File {
        val file = File.createTempFile(name, ".tmp")
        file.writeText(content)
        file.deleteOnExit()
        return file
    }

    private fun rewritePath(archiveBytes: ByteArray, newPath: String, content: String): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        entries[newPath] = content.toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun removeEntries(archiveBytes: ByteArray, names: Set<String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            ZipOutputStream(output).use { zipOutput ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name !in names) {
                        zipOutput.putNextEntry(ZipEntry(entry.name))
                        zipOutput.write(zip.readBytes())
                        zipOutput.closeEntry()
                    }
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun rewriteEntry(
        archiveBytes: ByteArray,
        entryName: String,
        content: String,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            ZipOutputStream(output).use { zipOutput ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    zipOutput.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == entryName) {
                        zipOutput.write(content.toByteArray())
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
}
