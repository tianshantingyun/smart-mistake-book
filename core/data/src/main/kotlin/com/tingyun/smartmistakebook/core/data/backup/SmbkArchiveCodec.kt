package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupManifestSummary
import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 归档清单。`formatVersion` 1→2（6C-1）：
 *   - v1：没有 settings、没有版本戳。缺省读回 null/false，旧包行为不变；
 *   - v2：现在唯一写出的形态。新增版本戳（[SmbkArchiveMetadata]）与
 *     `hasSettings`/`settingsDigest`（设置载荷 `settings.json` 的存在性与摘要）。
 *
 * 比 [SmbkManifest.FORMAT_VERSION] 新的归档在 [SmbkArchiveCodec.finalizeValidation] 里可读
 * 拒绝：清单是"这个包该怎么读"的自述，读不懂就不许碰设备上的数据。
 */
@Serializable
internal data class SmbkManifest(
    @SerialName("formatVersion")
    val formatVersion: Int = FORMAT_VERSION,
    @SerialName("databaseSchemaVersion")
    val databaseSchemaVersion: Int,
    @SerialName("createdAt")
    val createdAtEpochMillis: Long,
    @SerialName("problemCount")
    val problemCount: Int,
    @SerialName("assetCount")
    val assetCount: Int,
    @SerialName("hashAlgorithm")
    val hashAlgorithm: String = "SHA-256",
    /** 成包时的应用版本；v1 旧包缺席，读作 null。 */
    @SerialName("appVersionName")
    val appVersionName: String? = null,
    @SerialName("appVersionCode")
    val appVersionCode: Long? = null,
    /**
     * 成包时**那份快照库**的随包内容版本（`content_install_state.content_version`）。
     * 未知时是 null（不是空串）：诊断栏只陈述"当时读到的是什么"。
     */
    @SerialName("contentVersion")
    val contentVersion: String? = null,
    /** 包内是否有 `settings.json`。与条目本身必须一致，否则在交换前拒绝。 */
    @SerialName("hasSettings")
    val hasSettings: Boolean = false,
    /** `settings.json` 的 SHA-256；[hasSettings] 为真时必填。 */
    @SerialName("settingsDigest")
    val settingsDigest: String? = null,
    @SerialName("files")
    val files: List<SmbkFileRecord>,
    /**
     * 快照库引用的资产条目路径（`assets/<文件名>`），AUD-05 的 DB→文件闭合在包里的记录。
     *
     * 默认空表让 formatVersion 1 的旧包继续可读可恢复；新包由成包路径填满，解包/校验时
     * 逐条要求它们在包内。它**不**是闭合的权威判据（清单由包自己带，可以撒谎）——权威判据
     * 是恢复前对归档里那份库的 `readReferencedCanonicalAssets` 反查；这里多一道是为了让
     * "成包时引用集合与包内容漂开"当场失败，并让纯 JVM 面能钉住"缺引用资产 → 拒"。
     */
    @SerialName("referencedAssets")
    val referencedAssets: List<String> = emptyList(),
) {
    companion object {
        /** 写出的格式版本。 */
        const val FORMAT_VERSION = 2

        /** 仍可读的最旧格式版本：v1 旧包没有设置与版本戳，读作缺省值。 */
        const val MIN_READABLE_FORMAT_VERSION = 1
    }
}

/**
 * 归档的版本戳（诊断用）：成包时刻的应用版本与随包内容版本。全部可空——未知就写 null，
 * 不用假值填。恢复不看这些字段（它们只回答"这个包从哪儿来"），所以 v1 旧包缺席也无碍。
 */
internal data class SmbkArchiveMetadata(
    val appVersionName: String? = null,
    val appVersionCode: Long? = null,
    val contentVersion: String? = null,
)

@Serializable
internal data class SmbkFileRecord(
    val path: String,
    @SerialName("sha256")
    val sha256: String,
    @SerialName("byteSize")
    val byteSize: Long,
)

/**
 * Resource budgets enforced while validating untrusted archives. The defaults
 * are the production limits; tests may inject smaller values to exercise the
 * rejection branches without moving hundreds of megabytes.
 */
internal data class SmbkResourceLimits(
    /** Maximum number of entries allowed in a backup archive. */
    val maxEntryCount: Int = 10_000,
    /**
     * Maximum size of a single **non-database** entry (100 MB).
     *
     * 题图逐张有限；数据库条目不能用这一档——KD-53 设备实测库 365,133,824 B，
     * 旧实现把它和单张题图一起卡在 100 MB，自建备份因此"自己都验不过"。
     * `database.sqlite` 用独立的 [maxDatabaseEntryBytes]。
     */
    val maxSingleEntryBytes: Long = 100L * 1024 * 1024,
    /**
     * `database.sqlite` 条目的解压上限（1 GiB）。
     *
     * 库随使用增长（设备实测 365,133,824 B），留出约 2.8× 余量；压缩比门
     * （[maxCompressionRatio]）、总量门（[maxTotalDecompressedBytes]）与压缩归档
     * 体积门（[maxCompressedBytes]）仍然各自生效，抬高的只是"数据库这一类条目"的
     * 单条目解压上限。真实 SQLite 文本/索引数据可压缩（设备样本 365 MB → 63 MB），
     * 因此 1 GiB 的库在 1 GiB 的归档上限内仍可承载。
     */
    val maxDatabaseEntryBytes: Long = 1L * 1024 * 1024 * 1024,
    /**
     * Maximum total decompressed size of all entries (2 GB).
     *
     * 必须覆盖"数据库预算 + 若干题图"，否则抬高 [maxDatabaseEntryBytes] 只是把门
     * 挪到下一道；它仍是所有条目解压 sum 的硬上限。
     */
    val maxTotalDecompressedBytes: Long = 2L * 1024 * 1024 * 1024,
    /** Maximum manifest text length (1 MB). */
    val maxManifestChars: Int = 1_000_000,
    /** Maximum allowed compression ratio (uncompressed / compressed). */
    val maxCompressionRatio: Double = 100.0,
    /** Maximum size of the compressed archive itself (1 GB). */
    val maxCompressedBytes: Long = 1L * 1024 * 1024 * 1024,
) {
    companion object {
        val DEFAULT = SmbkResourceLimits()
    }
}

// 条目类型预算（KD-53）：
//   - `database.sqlite` → SmbkResourceLimits.maxDatabaseEntryBytes（库体积随使用增长）；
//   - 包自身的元数据（`manifest.json` / `checksums.sha256` / `settings.json`）→ 与
//     expandText 相同的文本上限（maxManifestChars * 4 字节），不受题图单条目上限约束：
//     清单大小由文件条目数与字段决定，把题图预算调小不该让包连说明都读不出来；
//   - 其余条目（`assets/` 前缀的题图及未知名字）→ SmbkResourceLimits.maxSingleEntryBytes。
//
// 校验（SmbkArchiveCodec.validate）与恢复（SmbkArchiveCodec.unpack）都走
// SmbkArchiveCodec.expandEntry，因此两侧同口径。
//
// 注意：这里刻意使用行注释而不是 KDoc——KDoc 内出现相邻的 `/` 与 `*`（例如路径通配写法）
// 会开启嵌套块注释（Kotlin 块注释可嵌套），外层注释会一直开到 EOF。
private fun SmbkResourceLimits.budgetFor(entryName: String): Long = when (entryName) {
    SmbkArchiveCodec.DATABASE_ENTRY -> maxDatabaseEntryBytes
    SmbkArchiveCodec.MANIFEST_ENTRY,
    SmbkArchiveCodec.CHECKSUM_ENTRY,
    SmbkArchiveCodec.SETTINGS_ENTRY -> maxManifestChars.toLong() * 4L
    else -> maxSingleEntryBytes
}

/** Structural corruption of an untrusted archive (truncation, bad headers...). */
private class ArchiveIntegrityException(message: String) : Exception(message)

/** One central-directory record cross-checked against its local header. */
private data class SmbkZipRecord(
    val name: String,
    val method: Int,
    val crc: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val dataOffset: Long,
)

internal object SmbkArchiveCodec {
    /** 包内清单条目名；类型预算（元数据上限）与解析都取这一处。 */
    internal const val MANIFEST_ENTRY = "manifest.json"

    /** 包内数据库条目名；类型预算与解包落点都取这一处，不各写一遍字符串。 */
    internal const val DATABASE_ENTRY = "database.sqlite"
    private const val ASSET_PREFIX = "assets/"

    /** 包内校验和条目名；类型预算（元数据上限）取这一处。 */
    internal const val CHECKSUM_ENTRY = "checksums.sha256"

    /** 包内设置载荷的条目名；成包、解包与恢复三处都取这一处，不各写一遍字符串。 */
    internal const val SETTINGS_ENTRY = "settings.json"

    private const val LOCAL_HEADER_SIG = 0x04034b50L
    private const val CENTRAL_HEADER_SIG = 0x02014b50L
    private const val ZIP64_EOCD_LOCATOR_SIG = 0x07064b50L

    /**
     * `encodeDefaults`：清单必须**显式**写出 `formatVersion` 与 `hasSettings`——
     * 这两项靠读侧默认值去猜，就没人能从包里直接看出"它是第几版、有没有设置"。
     */
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    /**
     * Writes an archive from an already-snapshotted database.
     *
     * [referencedAssets] 是**快照库引用的资产集合**（与 [assets] 同一批文件），
     * [problemCount] 也必须是同一份快照库的数。全部被引用的资产都必须在 [assets] 里：
     * 两边漂开时成包失败，而不是写出一份"库引用了包内没有的图"的归档（AUD-05）。
     *
     * 成包永远写 `formatVersion` 2；[metadata] 是版本戳，[settings] 为 null 时写出
     * `hasSettings=false` 且不带 `settings.json`（只该发生在测试或没装配设置口的装配上——
     * 生产装配两者都在，见 `BackupRepositoryFactory`）。
     *
     * KD-53：[limits] 与校验/恢复是**同一份预算**。成包前先按条目类型核对（数据库条目用
     * [SmbkResourceLimits.maxDatabaseEntryBytes]，其它条目用
     * [SmbkResourceLimits.maxSingleEntryBytes]），并核对条目数、清单长度与总量，不允许
     * 写出一个"自己都验不过"的归档；这些检查在写任何字节之前失败（`archive` 保持为空）。
     */
    fun create(
        archive: OutputStream,
        database: File,
        assets: List<File>,
        referencedAssets: List<File>,
        databaseSchemaVersion: Int,
        problemCount: Int,
        createdAtEpochMillis: Long,
        metadata: SmbkArchiveMetadata = SmbkArchiveMetadata(),
        settings: SmbkSettingsDocumentV1? = null,
        limits: SmbkResourceLimits = SmbkResourceLimits.DEFAULT,
    ): BackupReceipt {
        require(database.isFile) { "Database backup source is missing" }
        require(database.length() <= limits.maxDatabaseEntryBytes) {
            "数据库备份源超过数据库条目预算：${database.length()} > " +
                "${limits.maxDatabaseEntryBytes} 字节"
        }
        assets.forEach { asset ->
            require(asset.isFile && asset.length() > 0L) {
                "Canonical asset backup source is missing"
            }
            require(asset.length() <= limits.maxSingleEntryBytes) {
                "题图备份源超过单条目预算：${asset.name}"
            }
        }
        // 条目数 = manifest + database + checksums +（可选 settings）+ 全部题图。
        val expectedEntryCount = assets.size + 3 + if (settings != null) 1 else 0
        require(expectedEntryCount <= limits.maxEntryCount) {
            "备份条目数量超过预算：$expectedEntryCount > ${limits.maxEntryCount}"
        }
        val packagedPaths = assets.mapTo(HashSet<String>()) { asset -> "$ASSET_PREFIX${asset.name}" }
        referencedAssets.forEach { referenced ->
            require(referenced.isFile && referenced.length() > 0L) {
                "Referenced canonical asset backup source is missing: ${referenced.name}"
            }
            require("$ASSET_PREFIX${referenced.name}" in packagedPaths) {
                "Referenced canonical asset is not part of the backup package: ${referenced.name}"
            }
        }
        val referencedPaths = referencedAssets
            .map { referenced -> "$ASSET_PREFIX${referenced.name}" }
            .sorted()
        val databaseRecord = SmbkFileRecord(
            path = DATABASE_ENTRY,
            sha256 = sha256(database),
            byteSize = database.length(),
        )
        val settingsBytes = settings?.let(SmbkSettingsCodec::encode)
        // 总量门与校验侧同口径：数据库 + 设置 + 全部题图。
        val totalPayloadBytes =
            database.length() + (settingsBytes?.size ?: 0) + assets.sumOf(File::length)
        require(totalPayloadBytes <= limits.maxTotalDecompressedBytes) {
            "备份内容总量超过解压预算：$totalPayloadBytes > " +
                "${limits.maxTotalDecompressedBytes} 字节"
        }
        val settingsRecord = settingsBytes?.let { bytes ->
            SmbkFileRecord(
                path = SETTINGS_ENTRY,
                sha256 = sha256(bytes),
                byteSize = bytes.size.toLong(),
            )
        }
        val assetRecords = assets.map { asset ->
            SmbkFileRecord(
                path = "$ASSET_PREFIX${asset.name}",
                sha256 = sha256(asset),
                byteSize = asset.length(),
            )
        }
        val manifest = SmbkManifest(
            databaseSchemaVersion = databaseSchemaVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            problemCount = problemCount,
            assetCount = assetRecords.size,
            appVersionName = metadata.appVersionName,
            appVersionCode = metadata.appVersionCode,
            contentVersion = metadata.contentVersion,
            hasSettings = settingsRecord != null,
            settingsDigest = settingsRecord?.sha256,
            files = listOfNotNull(databaseRecord, settingsRecord) + assetRecords,
            referencedAssets = referencedPaths,
        )
        val checksumLines = manifest.files.joinToString(separator = "\n") { record ->
            "${record.sha256} *${record.path}"
        }
        // 清单长度门（与 finalizeValidation 的 maxManifestChars 同口径）：成包不写出一份
        // 读侧会因"manifest 内容过长"拒绝的归档。
        val manifestJson = json.encodeToString(SmbkManifest.serializer(), manifest)
        require(manifestJson.length <= limits.maxManifestChars) {
            "备份清单超过长度预算：${manifestJson.length} > ${limits.maxManifestChars}"
        }

        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()

            copyEntry(zip, DATABASE_ENTRY, database)
            settingsBytes?.let { bytes -> copyEntry(zip, SETTINGS_ENTRY, bytes) }
            assets.forEach { asset ->
                copyEntry(zip, "$ASSET_PREFIX${asset.name}", asset)
            }

            zip.putNextEntry(ZipEntry(CHECKSUM_ENTRY))
            zip.write(checksumLines.toByteArray())
            zip.closeEntry()
        }

        return BackupReceipt(
            createdAtEpochMillis = createdAtEpochMillis,
            databaseSchemaVersion = databaseSchemaVersion,
            problemCount = problemCount,
            assetCount = assetRecords.size,
            fileCount = manifest.files.size,
            totalBytes = totalPayloadBytes,
        )
    }

    /**
     * Validates an untrusted archive WITHOUT trusting java.util.zip's lenient
     * streaming parser. ZipInputStream silently accepts truncated archives
     * (an entry whose deflate stream still completes through the shared
     * compression dictionary) and ignores local-header CRC corruption, so
     * hostile fixtures would pass a stream-only check. Instead:
     *   - the whole archive is spooled to a bounded scratch file;
     *   - the end-of-central-directory record must exist and be complete,
     *     which rejects every truncated archive;
     *   - every central-directory record is cross-checked against its local
     *     header (CRC / sizes), which rejects header-level tampering;
     *   - each entry is decompressed while computing CRC-32 and SHA-256 and
     *     must match the central-directory CRC, the manifest size and the
     *     manifest/checksum SHA-256 — data-level tampering is rejected;
     *   - entry count, single-entry size, total decompressed size and the
     *     compression ratio (zip-bomb defence) are enforced from the exact
     *     central-directory sizes. 单条目预算按条目类型取：`database.sqlite`
     *     用独立的数据库上限（KD-53），其余条目仍按单图上限；总量与压缩比门不变。
     */
    fun validate(
        archive: InputStream,
        scratchDir: File = defaultScratchDirectory(),
        limits: SmbkResourceLimits = SmbkResourceLimits.DEFAULT,
    ): BackupValidation {
        require(scratchDir.isDirectory || scratchDir.mkdirs()) {
            "Cannot create backup validation scratch directory"
        }
        val scratchFiles = mutableListOf<File>()
        return try {
            val spooled = spool(archive, File(scratchDir, "archive.smbk"), limits)
            scratchFiles += spooled
            RandomAccessFile(spooled, "r").use { raf ->
                val records = parseZipStructure(raf, limits)
                val digests = mutableMapOf<String, String>()
                val sizes = mutableMapOf<String, Long>()
                var manifestText: String? = null
                var checksumText: String? = null
                var settingsText: String? = null
                var totalDecompressed = 0L
                records.forEach { record ->
                    when (record.name) {
                        MANIFEST_ENTRY -> manifestText = expandText(raf, record, limits)
                        CHECKSUM_ENTRY -> checksumText = expandText(raf, record, limits)
                        else -> {
                            val keepPlain = record.name == SETTINGS_ENTRY
                            if (keepPlain &&
                                record.uncompressedSize > limits.maxManifestChars.toLong() * 4L
                            ) {
                                throw ArchiveIntegrityException("${record.name} 内容过长")
                            }
                            val result = expandEntry(
                                raf,
                                record,
                                target = null,
                                limits = limits,
                                keepPlain = keepPlain,
                            )
                            totalDecompressed = checkTotalBudget(
                                totalDecompressed,
                                result.bytesWritten,
                                limits,
                            )
                            digests[record.name] = result.sha256Hex
                            sizes[record.name] = result.bytesWritten
                            if (keepPlain) {
                                settingsText = String(result.plain ?: ByteArray(0), Charsets.UTF_8)
                            }
                        }
                    }
                }
                finalizeValidation(manifestText, checksumText, settingsText, digests, sizes)
            }
        } catch (failure: ArchiveIntegrityException) {
            BackupValidation.Invalid(failure.message.orEmpty())
        } catch (failure: Exception) {
            BackupValidation.Invalid("备份校验失败：${failure.message.orEmpty()}")
        } finally {
            scratchFiles.forEach { file -> file.delete() }
            // Safety sweep: remove any partial scratch artifact.
            scratchDir.listFiles { file -> file.name.startsWith("archive.") }
                .orEmpty()
                .forEach { file -> file.delete() }
        }
    }

    /**
     * Runs the same structural verification as [validate] and additionally
     * writes the decompressed database and assets into [destinationDir] so a
     * restore can continue from staged files.
     */
    fun unpack(
        archive: InputStream,
        destinationDir: File,
        limits: SmbkResourceLimits = SmbkResourceLimits.DEFAULT,
    ): BackupValidation {
        require(destinationDir.isDirectory || destinationDir.mkdirs()) {
            "Cannot create backup staging directory"
        }
        val staged = mutableListOf<File>()
        fun cleanupStaging() {
            staged.forEach { file -> file.delete() }
            File(destinationDir, "assets").listFiles().orEmpty().forEach { it.delete() }
            File(destinationDir, DATABASE_ENTRY).delete()
            File(destinationDir, SETTINGS_ENTRY).delete()
        }
        return try {
            val spooled = spool(archive, File(destinationDir, ".archive.tmp"), limits)
            staged += spooled
            RandomAccessFile(spooled, "r").use { raf ->
                val records = parseZipStructure(raf, limits)
                val digests = mutableMapOf<String, String>()
                val sizes = mutableMapOf<String, Long>()
                var manifestText: String? = null
                var checksumText: String? = null
                var settingsText: String? = null
                var totalDecompressed = 0L
                records.forEach { record ->
                    when (record.name) {
                        MANIFEST_ENTRY -> manifestText = expandText(raf, record, limits)
                        CHECKSUM_ENTRY -> checksumText = expandText(raf, record, limits)
                        else -> {
                            val target = destinationFile(destinationDir, record.name)
                                ?: throw ArchiveIntegrityException(
                                    "备份包含非法路径 ${record.name}",
                                )
                            val keepPlain = record.name == SETTINGS_ENTRY
                            if (keepPlain &&
                                record.uncompressedSize > limits.maxManifestChars.toLong() * 4L
                            ) {
                                throw ArchiveIntegrityException("${record.name} 内容过长")
                            }
                            val result = expandEntry(
                                raf,
                                record,
                                target,
                                limits,
                                keepPlain = keepPlain,
                            )
                            staged += target
                            totalDecompressed = checkTotalBudget(
                                totalDecompressed,
                                result.bytesWritten,
                                limits,
                            )
                            digests[record.name] = result.sha256Hex
                            sizes[record.name] = result.bytesWritten
                            if (keepPlain) {
                                settingsText = String(result.plain ?: ByteArray(0), Charsets.UTF_8)
                            }
                        }
                    }
                }
                finalizeValidation(manifestText, checksumText, settingsText, digests, sizes)
            }
        } catch (failure: ArchiveIntegrityException) {
            cleanupStaging()
            BackupValidation.Invalid(failure.message.orEmpty())
        } catch (failure: Exception) {
            cleanupStaging()
            BackupValidation.Invalid("备份解包失败：${failure.message.orEmpty()}")
        } finally {
            File(destinationDir, ".archive.tmp").delete()
        }
    }

    // ------------------------------------------------------------------
    // Shared verification pipeline
    // ------------------------------------------------------------------

    /**
     * Scratch directory for a validation that did not name one.
     *
     * java.io rather than java.nio.file: `Files.createTempDirectory` is API 26
     * while minSdk is 23, so it would throw NoSuchMethodError on a supported
     * device. The name stays unique per call so two concurrent validations
     * cannot share a spool file.
     */
    private fun defaultScratchDirectory(): File = File(
        File(System.getProperty("java.io.tmpdir") ?: "."),
        "smbk-validate-${UUID.randomUUID()}",
    ).also(File::deleteOnExit)

    /** Copies the untrusted stream to [target], bounded by [SmbkResourceLimits.maxCompressedBytes]. */
    private fun spool(archive: InputStream, target: File, limits: SmbkResourceLimits): File {
        var written = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = archive.read(buffer)
                if (read < 0) break
                written += read
                if (written > limits.maxCompressedBytes) {
                    throw ArchiveIntegrityException(
                        "压缩归档体积超过上限 (${limits.maxCompressedBytes} 字节)",
                    )
                }
                output.write(buffer, 0, read)
            }
        }
        if (written == 0L) {
            throw ArchiveIntegrityException("备份归档为空")
        }
        return target
    }

    /**
     * Locates the EOCD, walks the central directory and cross-checks every
     * record against its local header. Rejects truncated archives (missing or
     * incomplete EOCD / central directory), zip64 archives, duplicate entries,
     * unsupported compression methods and inconsistent header metadata.
     */
    private fun parseZipStructure(raf: RandomAccessFile, limits: SmbkResourceLimits): List<SmbkZipRecord> {
        val length = raf.length()
        val eocdOffset = findEocd(raf, length)
        raf.seek(eocdOffset + 4)
        val diskNumber = readUShort(raf)
        val cdDisk = readUShort(raf)
        val diskEntries = readUShort(raf)
        val totalEntries = readUShort(raf)
        val cdSize = readUInt(raf)
        val cdOffset = readUInt(raf)
        if (diskNumber != 0 || cdDisk != 0 || diskEntries != totalEntries) {
            throw ArchiveIntegrityException("不支持分卷归档")
        }
        if (
            totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL ||
            totalEntries == 0 || cdOffset + cdSize > eocdOffset
        ) {
            throw ArchiveIntegrityException("归档中央目录不完整或已截断")
        }
        if (totalEntries > limits.maxEntryCount) {
            throw ArchiveIntegrityException("归档条目数量超过上限 (${limits.maxEntryCount})")
        }

        val records = ArrayList<SmbkZipRecord>(totalEntries)
        val seen = HashSet<String>(totalEntries)
        var cursor = cdOffset
        repeat(totalEntries) {
            raf.seek(cursor)
            val sig = readUInt(raf)
            if (sig != CENTRAL_HEADER_SIG) {
                // Some zip producers (notably the JDK's ZipOutputStream)
                // write an extra data descriptor "sig" word into the central
                // directory stream between local headers; treat the canonical
                // PK\x01\x02 signature as authoritative regardless of that
                // trailing byte to stay compatible with locally built archives.
                raf.seek(cursor)
                val sigLow = readUShort(raf)
                if (sigLow != (CENTRAL_HEADER_SIG and 0xFFFFL).toInt()) {
                    throw ArchiveIntegrityException("归档中央目录已损坏")
                }
            }
            // VerifyLocalHeader may move the file pointer when it seeks to
            // the entry's local header; anchor cursor arithmetic to the
            // central directory alone by re-seeking past the signature.
            raf.seek(cursor + 4)
            raf.skipBytes(6) // version made by + version needed + flags
            val method = readUShort(raf)
            raf.skipBytes(4) // mod time/date
            val crc = readUInt(raf)
            val compressedSize = readUInt(raf)
            val uncompressedSize = readUInt(raf)
            val nameLength = readUShort(raf)
            val extraLength = readUShort(raf)
            val commentLength = readUShort(raf)
            raf.skipBytes(4) // disk number + internal attributes
            val externalAttributes = readUInt(raf)
            if (externalAttributes == 0xFFFFFFFFL) {
                throw ArchiveIntegrityException("不支持 ZIP64 归档")
            }
            val localOffset = readUInt(raf)
            if (
                compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL ||
                localOffset == 0xFFFFFFFFL
            ) {
                throw ArchiveIntegrityException("不支持 ZIP64 归档")
            }
            if (nameLength > MAX_ENTRY_NAME_LENGTH) {
                throw ArchiveIntegrityException("归档条目名过长")
            }
            val name = String(raf.readBytes(nameLength), Charsets.UTF_8)
            if (name.isEmpty() || name.contains("..") || name.startsWith("/")) {
                throw ArchiveIntegrityException("非法路径: $name")
            }
            if (!seen.add(name)) {
                throw ArchiveIntegrityException("重复的归档条目: $name")
            }
            if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                throw ArchiveIntegrityException("不支持的压缩方式: $name")
            }
            val dataOffset = verifyLocalHeader(
                raf,
                localOffset,
                name,
                crc,
                compressedSize,
                uncompressedSize,
            )
            if (dataOffset + compressedSize > length) {
                throw ArchiveIntegrityException("归档已截断: $name")
            }
            records += SmbkZipRecord(
                name = name,
                method = method,
                crc = crc,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                dataOffset = dataOffset,
            )
            cursor += 46L + nameLength + extraLength + commentLength
            if (cursor > length) {
                throw ArchiveIntegrityException("归档中央目录已损坏")
            }
        }
        if (cursor != eocdOffset) {
            throw ArchiveIntegrityException("归档中央目录与目录结束记录不一致")
        }
        return records
    }

    /**
     * Verifies the local header of one entry against the central directory.
     * A tampered local header (e.g. a flipped CRC byte) is invisible to
     * ZipInputStream-based validators, so it is rejected here instead.
     * Returns the absolute offset of the compressed data.
     */
    private fun verifyLocalHeader(
        raf: RandomAccessFile,
        localOffset: Long,
        name: String,
        crc: Long,
        compressedSize: Long,
        uncompressedSize: Long,
    ): Long {
        if (localOffset < 0 || localOffset + 30 > raf.length()) {
            throw ArchiveIntegrityException("归档已截断: $name")
        }
        raf.seek(localOffset)
        if (readUInt(raf) != LOCAL_HEADER_SIG) {
            throw ArchiveIntegrityException("归档已截断: $name")
        }
        raf.skipBytes(4) // version + flags
        val method = readUShort(raf)
        raf.skipBytes(4) // mod time/date
        val localCrc = readUInt(raf)
        val localCompressed = readUInt(raf)
        val localUncompressed = readUInt(raf)
        val localNameLength = readUShort(raf)
        val extraLength = readUShort(raf)
        if (localNameLength > MAX_ENTRY_NAME_LENGTH) {
            throw ArchiveIntegrityException("归档条目名过长: $name")
        }
        val localName = String(raf.readBytes(localNameLength), Charsets.UTF_8)
        if (localName != name) {
            throw ArchiveIntegrityException("归档条目名不一致: $name")
        }
        if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
            throw ArchiveIntegrityException("不支持的压缩方式: $name")
        }
        // With a data descriptor (general purpose bit 3) the local header may
        // legitimately carry zeros; any NON-zero value must still match the
        // central directory, otherwise the header was tampered with.
        if (localCrc != 0L && localCrc != crc) {
            throw ArchiveIntegrityException("归档头部校验和不一致: $name")
        }
        if (localCompressed != 0L && localCompressed != compressedSize) {
            throw ArchiveIntegrityException("归档头部大小不一致: $name")
        }
        if (localUncompressed != 0L && localUncompressed != uncompressedSize) {
            throw ArchiveIntegrityException("归档头部大小不一致: $name")
        }
        return localOffset + 30 + localNameLength + extraLength
    }

    /** Scans backwards for a complete EOCD record; rejects truncated archives. */
    private fun findEocd(raf: RandomAccessFile, length: Long): Long {
        if (length < 22) {
            throw ArchiveIntegrityException("归档已截断：缺少中央目录记录")
        }
        val searchLength = minOf(length, 22L + 65_536L).toInt()
        val tail = ByteArray(searchLength)
        raf.seek(length - searchLength)
        raf.readFully(tail)
        for (index in tail.size - 22 downTo 0) {
            if (
                tail[index] == 0x50.toByte() &&
                tail[index + 1] == 0x4B.toByte() &&
                tail[index + 2] == 0x05.toByte() &&
                tail[index + 3] == 0x06.toByte()
            ) {
                val commentLength = (tail[index + 20].toInt() and 0xFF) or
                    ((tail[index + 21].toInt() and 0xFF) shl 8)
                val expectedLength = index + 22 + commentLength
                if (expectedLength == tail.size) {
                    if (index >= 20) {
                        val maybeLocator = (tail[index - 20].toInt() and 0xFF) or
                            ((tail[index - 19].toInt() and 0xFF) shl 8) or
                            ((tail[index - 18].toInt() and 0xFF) shl 16) or
                            ((tail[index - 17].toInt() and 0xFF) shl 24)
                        if (maybeLocator.toLong() == ZIP64_EOCD_LOCATOR_SIG) {
                            throw ArchiveIntegrityException("不支持 ZIP64 归档")
                        }
                    }
                    return length - searchLength + index
                }
            }
        }
        throw ArchiveIntegrityException("归档已截断：缺少中央目录记录")
    }

    private data class ExpandedEntry(
        val bytesWritten: Long,
        val sha256Hex: String,
        val plain: ByteArray? = null,
    )

    private fun checkTotalBudget(current: Long, added: Long, limits: SmbkResourceLimits): Long {
        val total = current + added
        if (total > limits.maxTotalDecompressedBytes) {
            throw ArchiveIntegrityException("解压后总大小超过上限")
        }
        return total
    }

    /** Expands a metadata entry (manifest/checksums) fully into text. */
    private fun expandText(raf: RandomAccessFile, record: SmbkZipRecord, limits: SmbkResourceLimits): String {
        if (record.uncompressedSize > limits.maxManifestChars.toLong() * 4L) {
            throw ArchiveIntegrityException("${record.name} 内容过长")
        }
        val result = expandEntry(raf, record, target = null, limits = limits, keepPlain = true)
        return String(result.plain ?: ByteArray(0), Charsets.UTF_8)
    }

    /**
     * Decompresses one entry, verifying CRC-32, the declared size and the
     * compression-ratio ceiling. Optionally writes the plaintext to [target].
     *
     * 单条目预算按**条目类型**取（KD-53）：`database.sqlite` 用
     * [SmbkResourceLimits.maxDatabaseEntryBytes]，其余条目用
     * [SmbkResourceLimits.maxSingleEntryBytes]。上限检查与实际解压字节都按同一预算，
     * 中央目录声明与实际流各挡一次。
     */
    private fun expandEntry(
        raf: RandomAccessFile,
        record: SmbkZipRecord,
        target: File?,
        limits: SmbkResourceLimits,
        keepPlain: Boolean = false,
    ): ExpandedEntry {
        val entryBudget = limits.budgetFor(record.name)
        if (record.uncompressedSize > entryBudget) {
            throw ArchiveIntegrityException("${record.name} 超过条目解压大小上限")
        }
        if (record.compressedSize > 0 && record.uncompressedSize > 0) {
            val ratio = record.uncompressedSize.toDouble() / record.compressedSize
            if (ratio > limits.maxCompressionRatio) {
                throw ArchiveIntegrityException(
                    "${record.name} 压缩比异常（1:${"%.0f".format(ratio)}），疑似压缩炸弹",
                )
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val plainBuffer = if (keepPlain) ByteArrayOutputStream(record.uncompressedSize.toInt()) else null
        var written = 0L
        raf.seek(record.dataOffset)
        val rawStream = RafInputStream(raf, record.compressedSize)
        val plainStream: InputStream = when (record.method) {
            ZipEntry.STORED -> BufferedInputStream(rawStream)
            else -> InflaterInputStream(BufferedInputStream(rawStream), Inflater(true))
        }
        plainStream.use { input ->
            val output = target?.outputStream()
            try {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) {
                        written += read
                        if (written > record.uncompressedSize ||
                            written > entryBudget
                        ) {
                            throw ArchiveIntegrityException("${record.name} 大小不一致")
                        }
                        digest.update(buffer, 0, read)
                        crc.update(buffer, 0, read)
                        output?.write(buffer, 0, read)
                        plainBuffer?.write(buffer, 0, read)
                    }
                }
            } catch (integrity: ArchiveIntegrityException) {
                throw integrity
            } catch (failure: Exception) {
                throw ArchiveIntegrityException("${record.name} 解压失败：${failure.message.orEmpty()}")
            } finally {
                output?.close()
            }
        }
        if (written != record.uncompressedSize) {
            throw ArchiveIntegrityException("${record.name} 大小不一致")
        }
        if (crc.value != record.crc) {
            throw ArchiveIntegrityException("${record.name} CRC 校验失败")
        }
        return ExpandedEntry(
            bytesWritten = written,
            sha256Hex = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
            plain = plainBuffer?.toByteArray(),
        )
    }

    /** Bounds the read of one entry's compressed data inside the spooled file. */
    private class RafInputStream(
        private val raf: RandomAccessFile,
        private var remaining: Long,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = raf.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val read = raf.read(b, off, toRead)
            if (read > 0) remaining -= read
            return read
        }
    }

    /** Manifest / checksums cross-validation shared by [validate] and [unpack]. */
    private fun finalizeValidation(
        manifestText: String?,
        checksumText: String?,
        settingsText: String?,
        stagedDigests: Map<String, String>,
        stagedSizes: Map<String, Long>,
    ): BackupValidation {
        val text = manifestText ?: return BackupValidation.Invalid("缺少 manifest.json")
        if (text.length > SmbkResourceLimits.DEFAULT.maxManifestChars) {
            return BackupValidation.Invalid("manifest 内容过长")
        }
        val parsed = try {
            json.decodeFromString(SmbkManifest.serializer(), text)
        } catch (failure: Exception) {
            return BackupValidation.Invalid("manifest 解析失败：${failure.message.orEmpty()}")
        }
        if (parsed.formatVersion > SmbkManifest.FORMAT_VERSION) {
            return BackupValidation.Invalid("备份格式版本过新，暂不支持恢复")
        }
        if (parsed.formatVersion < SmbkManifest.MIN_READABLE_FORMAT_VERSION) {
            return BackupValidation.Invalid("备份格式版本过旧，暂不支持恢复")
        }
        if (parsed.hashAlgorithm != "SHA-256") {
            return BackupValidation.Invalid("不支持的校验算法")
        }
        val expectedPaths = parsed.files.map { it.path }.toSet()
        if (expectedPaths != stagedSizes.keys) {
            return BackupValidation.Invalid("备份文件列表与内容不一致")
        }
        val checksums = mutableMapOf<String, String>()
        checksumText.orEmpty().lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val separator = line.indexOf(" *")
            if (separator > 0) {
                checksums[line.substring(separator + 2)] = line.substring(0, separator)
            }
        }
        if (checksumText == null || checksums.isEmpty()) {
            return BackupValidation.Invalid("缺少 checksums.sha256")
        }
        if (checksums != parsed.files.associate { it.path to it.sha256 }) {
            return BackupValidation.Invalid("checksums.sha256 与 manifest 不一致")
        }
        val settingsProblem = settingsRejection(
            declaredHasSettings = parsed.hasSettings,
            declaredDigest = parsed.settingsDigest,
            settingsText = settingsText,
            stagedDigests = stagedDigests,
        )
        if (settingsProblem != null) {
            return BackupValidation.Invalid(settingsProblem)
        }
        // AUD-05：包声明的引用集合必须逐条在包内（缺图在交换前就拒绝，不给"恢复到一半
        // 才发现图没了"的机会）。旧包没有这张清单，默认空表——不因此改变旧包的既有行为。
        val missingReferencedAssets = parsed.referencedAssets
            .filterNot { path -> path in stagedSizes.keys }
        if (missingReferencedAssets.isNotEmpty()) {
            return BackupValidation.Invalid(
                "备份引用的题图缺失：${missingReferencedAssets.joinToString(separator = "、")}",
            )
        }
        parsed.files.forEach { record ->
            val size = stagedSizes[record.path]
                ?: return BackupValidation.Invalid("缺少 ${record.path}")
            if (size != record.byteSize) {
                return BackupValidation.Invalid("${record.path} 大小不一致")
            }
            if (stagedDigests[record.path] != record.sha256) {
                return BackupValidation.Invalid("${record.path} 校验失败")
            }
        }
        return BackupValidation.Valid(
            manifest = BackupManifestSummary(
                formatVersion = parsed.formatVersion,
                databaseSchemaVersion = parsed.databaseSchemaVersion,
                createdAtEpochMillis = parsed.createdAtEpochMillis,
                problemCount = parsed.problemCount,
                assetCount = parsed.assetCount,
                fileCount = parsed.files.size,
                appVersionName = parsed.appVersionName,
                appVersionCode = parsed.appVersionCode,
                contentVersion = parsed.contentVersion,
                hasSettings = parsed.hasSettings,
            ),
            checkedFileCount = parsed.files.size,
            totalBytes = parsed.files.sumOf { it.byteSize },
        )
    }

    /**
     * `settings.json` 的存在性、摘要与兼容判定；返回可读拒因，null 表示通过。
     *
     *   - `hasSettings` 与包内条目必须一致：声明有却没有，就是把"设置丢了"伪装成旧包；
     *     声明没有却带着，就是一份读侧无法解释的载荷——两种都在交换前拒绝；
     *   - 摘要（manifest 的 `settingsDigest`）必须与载荷的 SHA-256 逐位一致：清单里的
     *     存在性声明与字节不是一回事，只对存在性不对字节，改过的载荷还是能过；
     *   - 版本与领域值域用 [SmbkSettingsCodec.rejectionReasonFor]：读不懂或值越界的设置
     *     必须在**交换前**拒绝，否则只能等写 DataStore 时才失败——那是半恢复。
     */
    private fun settingsRejection(
        declaredHasSettings: Boolean,
        declaredDigest: String?,
        settingsText: String?,
        stagedDigests: Map<String, String>,
    ): String? {
        val stagedDigest = stagedDigests[SETTINGS_ENTRY]
        if (!declaredHasSettings) {
            if (stagedDigest != null || declaredDigest != null) {
                return "settings.json 与 manifest 的 hasSettings 不一致"
            }
            return null
        }
        if (stagedDigest == null) {
            return "备份声明包含设置但缺少 settings.json"
        }
        if (declaredDigest.isNullOrEmpty()) {
            return "settings.json 缺少摘要"
        }
        if (declaredDigest != stagedDigest) {
            return "settings.json 校验失败"
        }
        val settingsJson = settingsText
            ?: return "备份声明包含设置但缺少 settings.json"
        val document = try {
            SmbkSettingsCodec.decode(settingsJson)
        } catch (failure: Exception) {
            return "settings.json 解析失败：${failure.message.orEmpty()}"
        }
        return SmbkSettingsCodec.rejectionReasonFor(document)
    }

    // ------------------------------------------------------------------
    // Small binary helpers
    // ------------------------------------------------------------------

    private fun readUShort(raf: RandomAccessFile): Int {
        val buffer = ByteArray(2)
        raf.readFully(buffer)
        return (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt(raf: RandomAccessFile): Long {
        val buffer = ByteArray(4)
        raf.readFully(buffer)
        return (buffer[0].toLong() and 0xFF) or
            ((buffer[1].toLong() and 0xFF) shl 8) or
            ((buffer[2].toLong() and 0xFF) shl 16) or
            ((buffer[3].toLong() and 0xFF) shl 24)
    }

    private fun RandomAccessFile.skipBytes(count: Int) {
        seek(filePointer + count)
    }

    private fun RandomAccessFile.readBytes(count: Int): ByteArray {
        val buffer = ByteArray(count)
        readFully(buffer)
        return buffer
    }

    private fun copyEntry(zip: ZipOutputStream, path: String, file: File) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) zip.write(buffer, 0, read)
            }
        }
        zip.closeEntry()
    }

    private fun copyEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun destinationFile(
        destinationDir: File,
        entryName: String,
    ): File? {
        val normalized = entryName.replace('\\', '/')
        if (normalized == DATABASE_ENTRY) return File(destinationDir, DATABASE_ENTRY)
        if (normalized == SETTINGS_ENTRY) return File(destinationDir, SETTINGS_ENTRY)
        if (!normalized.startsWith(ASSET_PREFIX)) return null
        val assetName = normalized.removePrefix(ASSET_PREFIX)
        if (
            assetName.isBlank() ||
            assetName.contains('/') ||
            assetName == "." ||
            assetName == ".."
        ) return null
        val assetsDir = File(destinationDir, "assets").apply { mkdirs() }
        return File(assetsDir, assetName)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val DEFAULT_BUFFER_SIZE = 8_192
    private const val MAX_ENTRY_NAME_LENGTH = 4_096
}
