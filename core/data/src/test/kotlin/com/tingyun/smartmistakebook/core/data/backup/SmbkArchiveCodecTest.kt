package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupValidation
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
                        output.write(
                            manifest.replace(
                                "\"databaseSchemaVersion\"",
                                "\"formatVersion\":99,\"databaseSchemaVersion\"",
                            ).toByteArray(),
                        )
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
     * 读库那一步才发现图没了。旧包（没有这张清单）不受影响，见 [SmbkManifestV1]。
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
