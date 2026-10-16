package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
        assertEquals(SmbkManifest.FORMAT_VERSION, validation.manifest.formatVersion)
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
        assertEquals(SmbkManifest.FORMAT_VERSION, validation.manifest.formatVersion)
        assertTrue(!validation.manifest.hasSettings)

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

    /** >2 拒：读不懂的格式版本必须在交换前拒绝，文案沿用既有那句。 */
    @Test
    fun `archive format version above two is rejected with the existing reason`() {
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
        )

        val manifest = manifestText(archive.toByteArray())
        val declaredVersion = "\"formatVersion\":${SmbkManifest.FORMAT_VERSION}"
        assertTrue("夹具必须真的由当前版本写出 formatVersion", manifest.contains(declaredVersion))
        val futureManifest = manifest.replace(declaredVersion, "\"formatVersion\":3")
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

    /**
     * v1 旧包夹具：清单里**没有** v2 的版本戳与设置字段（那会儿它们还不存在），
     * 摘要/大小仍按 v1 的既有约定写全，好让 v1 兼容读走的是与旧 app 相同的形状。
     */
    private fun buildV1Archive(database: File, assets: List<File>): ByteArray {
        val records = listOf(
            Triple("database.sqlite", sha256Hex(database.readBytes()), database.length()),
        ) + assets.map { asset ->
            Triple("assets/${asset.name}", sha256Hex(asset.readBytes()), asset.length())
        }
        val manifest = buildString {
            append("{")
            append("\"formatVersion\":1,")
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
