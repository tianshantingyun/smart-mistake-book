package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupManifestSummary
import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupSelectionSummary
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
 * 归档清单。`formatVersion` 1→2（6C-1）→3（6C-2）：
 *   - v1：没有 settings、没有版本戳。缺省读回 null/false，旧包行为不变；
 *   - v2：版本戳（[SmbkArchiveMetadata]）与 `hasSettings`/`settingsDigest`；
 *   - v3：新增 `selection` 块（[SmbkSelectionRecord]）。v1/v2 的包**没有这个键**，读回
 *     null，语义是「整库全量」（与旧包行为逐字一致）；v3 的 `selection` 块**必须存在**
 *     （设计 §3.2-S1）——读侧遇到 `formatVersion>=3` 却没有 selection 的清单直接拒绝，
 *     不把它猜成全量。没有 selection 的写出路径仍按 v2 落盘（[LEGACY_FORMAT_VERSION]），
 *     因此"旧测试/旧调用面"与生产 v3 包在格式上一眼可分。
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
    /**
     * 本包的内容选择（6C-2，formatVersion=3 起）。**null = v1/v2 旧包的全量语义**；
     * `formatVersion>=3` 时它必须非 null（读侧在 [finalizeValidation] 里拒绝缺失）。
     * 读侧的严格闸门（closureVersion、digest、rowCounts 全键、与包内库逐表对拍）在恢复
     * 校验（T5）里，本类负责携带与格式/自洽判定。
     */
    @SerialName("selection")
    val selection: SmbkSelectionRecord? = null,
) {
    companion object {
        /** 写出的格式版本：v3 = v2 + 必填 `selection` 块。 */
        const val FORMAT_VERSION = 3

        /**
         * 不带 selection 的写出格式：v2（6C-1 的版本戳 + 设置）。v3 的 selection 块是
         * 必填的，所以 `create(selection=null)` 只能写成 v2——不能写一份 v3 却缺选择块，
         * 那正是读侧要拒绝的自相矛盾清单。
         */
        const val LEGACY_FORMAT_VERSION = 2

        /** 仍可读的最旧格式版本：v1 旧包没有设置与版本戳，读作缺省值。 */
        const val MIN_READABLE_FORMAT_VERSION = 1
    }
}

/**
 * v3 清单的 `selection` 块（6C-2）：这次备份带了哪几类内容、按哪版闭包规则裁的、裁完每张表
 * 有多少行。
 *
 * 全部字段在**写侧**由 `AndroidBackupRepository.create` + `SmbkSelectionPlan` 生成；本类
 * 故意不做 `init` 约束——读侧遇到未来版本的记录必须能先解析出来，再由恢复校验按
 * 「规则版本不认识 / mask 不合法 / digest 不符」在交换前拒绝，而不是在 JSON 解码时抛一个
 * 无法解释的异常。
 */
@Serializable
internal data class SmbkSelectionRecord(
    /** 记录形状版本；本批写出 2。 */
    @SerialName("schemaVersion")
    val schemaVersion: Int,
    /** 四个类别位的按位或（9/13/15）。 */
    @SerialName("categoryMask")
    val categoryMask: Int,
    /** 与 [categoryMask] 一致的类别名（`BackupContentCategory.name`）。 */
    @SerialName("categories")
    val categories: List<String>,
    /** 资产策略；本批唯一写出值 `INCLUDED`（无图子集不实现）。 */
    @SerialName("assetPolicy")
    val assetPolicy: String,
    /** 闭包规则表版本；本批写出 [SmbkSelectionPlan.CLOSURE_VERSION]。 */
    @SerialName("closureVersion")
    val closureVersion: Int,
    /** `SmbkSelectionPlan.closureDigest` 的 v2 摘要（原始小写 hex，无前缀）。 */
    @SerialName("closureDigest")
    val closureDigest: String,
    /** 裁剪后快照的逐表行数；必须覆盖 86 张表（值为 0 也写）。 */
    @SerialName("rowCounts")
    val rowCounts: Map<String, Long>,
    /** 实际删除过数据行的表数（不含 FTS rebuild）；全选只裁派生索引时通常 ≤2，无删除为 0。 */
    @SerialName("trimmedTableCount")
    val trimmedTableCount: Int,
) {
    companion object {
        /** 本批写出的记录形状版本。 */
        const val CURRENT_SCHEMA_VERSION = 2

        /** `selection` 块首次出现的清单格式版本；比它旧的格式不定义这个键。 */
        const val INTRODUCED_FORMAT_VERSION = 3
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
     * 成包写出的格式版本由 [selection] 决定：带选择块写 [SmbkManifest.FORMAT_VERSION]=3；
     * [selection]=null 表示旧「整库全量」调用面，写出 [SmbkManifest.LEGACY_FORMAT_VERSION]=2
     * （绝不写一份缺 selection 的 v3）。[metadata] 是版本戳，[settings] 为 null 时写出
     * `hasSettings=false` 且不带 `settings.json`（只该发生在测试或没装配设置口的装配上——
     * 生产装配两者都在，见 `BackupRepositoryFactory`）。
     *
     * [selection] 是 v3 的内容选择块（6C-2）。**在写任何字节之前**校验：mask 只能是
     * 9/13/15、`categories` 必须与 mask 一致、`rowCounts` 必须覆盖 86 表、digest 必须与
     * 同输入重算的一致。非法选择在这里抛 `IllegalArgumentException`，`archive` 保持为空
     * ——绝不写出半包。
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
        selection: SmbkSelectionRecord? = null,
    ): BackupReceipt {
        selection?.let(::requireWritableSelection)
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
            // v3 的 selection 块必填：没有选择块的写出面只能是 v2（见 create KDoc）。
            formatVersion = if (selection == null) {
                SmbkManifest.LEGACY_FORMAT_VERSION
            } else {
                SmbkManifest.FORMAT_VERSION
            },
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
            selection = selection,
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
            selectionMask = selection?.categoryMask ?: BackupContentCategory.FULL_MASK,
            trimmedTableCount = selection?.trimmedTableCount ?: 0,
        )
    }

    /**
     * 写侧闸门：非法选择在写任何字节之前失败。
     *
     * 与 `BackupSelection.requireCreatable` 同一口径的**第二道**（仓储已先拒一次）：
     * 9/13/15 之外（含 0/8 与未闭合组合）一律抛；`categories` 必须与 mask 位和一致；
     * `rowCounts` 必须恰好覆盖 86 表；`closureDigest` 必须能由同输入重算出来。
     * 不做任何补齐或缩水。
     */
    private fun requireWritableSelection(selection: SmbkSelectionRecord) {
        require(selection.categoryMask in SmbkSelectionPlan.allowedMasks) {
            "备份内容选择非法：mask=${selection.categoryMask}；" +
                "只允许 ${SmbkSelectionPlan.allowedMasks.joinToString("/")}"
        }
        require(selection.schemaVersion == SmbkSelectionRecord.CURRENT_SCHEMA_VERSION) {
            "备份内容选择 schemaVersion=${selection.schemaVersion}，" +
                "本批写出 ${SmbkSelectionRecord.CURRENT_SCHEMA_VERSION}"
        }
        require(selection.closureVersion == SmbkSelectionPlan.CLOSURE_VERSION) {
            "备份内容选择 closureVersion=${selection.closureVersion}，" +
                "本批写出 ${SmbkSelectionPlan.CLOSURE_VERSION}"
        }
        require(selection.assetPolicy == SmbkSelectionPlan.ASSET_POLICY_INCLUDED) {
            "备份内容选择 assetPolicy=${selection.assetPolicy}，" +
                "本批只支持 ${SmbkSelectionPlan.ASSET_POLICY_INCLUDED}"
        }
        val categories = selection.categories.map { name ->
            SmbkSelectionCategory.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("备份内容选择含未知类别：$name")
        }
        require(categories.toSet().size == categories.size) {
            "备份内容选择含重复类别：${selection.categories}"
        }
        val expectedCategories = SmbkSelectionPlan.categoriesOf(selection.categoryMask)
        require(categories.toSet() == expectedCategories) {
            "备份内容选择 categories=${selection.categories} 与 " +
                "mask=${selection.categoryMask}（$expectedCategories）不一致"
        }
        require(selection.rowCounts.keys == SmbkSelectionPlan.rowCountKeys) {
            val missing = (SmbkSelectionPlan.rowCountKeys - selection.rowCounts.keys).sorted()
            val extra = (selection.rowCounts.keys - SmbkSelectionPlan.rowCountKeys).sorted()
            "备份内容选择的 rowCounts 必须覆盖 86 表：missing=$missing extra=$extra"
        }
        require(selection.trimmedTableCount in 0..SmbkSelectionPlan.trimOrder.size) {
            "备份内容选择 trimmedTableCount=${selection.trimmedTableCount} 越界 " +
                "(0..${SmbkSelectionPlan.trimOrder.size})"
        }
        val recomputed = SmbkSelectionPlan.closureDigest(
            categories = categories,
            assetPolicy = selection.assetPolicy,
            rowCounts = selection.rowCounts,
        )
        require(selection.closureDigest == recomputed) {
            "备份内容选择的 closureDigest 与重算不一致：declared=${selection.closureDigest} " +
                "recomputed=$recomputed"
        }
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
        // 6C-2（T5）：selection 块的**严格读侧闸门**。v1/v2 包没有这个键，读作 null = 全量
        // （旧语义）；formatVersion>=3 的包**必须**带 selection（设计 §3.2-S1），缺失时
        // 不能猜成全量——那会把一份被剥掉选择块的子集/残缺包按全量恢复并清空本机数据。
        // 只要清单带了 selection，它就必须与本机规则表版本完全自洽。这里在解包/恢复的
        // Phase 1 就拒绝——仍在交换之前，原库一个字节都不动。
        // 声明 vs 包内库实测（未勾选类别 0 行、86 表逐表行数）在恢复的 Phase 2b 之后另行核对。
        val declaredSelection = parsed.selection
        val selectionSummary = if (declaredSelection == null) {
            if (parsed.formatVersion >= SmbkSelectionRecord.INTRODUCED_FORMAT_VERSION) {
                return BackupValidation.Invalid(
                    "备份格式版本=${parsed.formatVersion} 缺少内容选择块（selection）：" +
                        "v3 起必须声明备份内容，无法确认这份包带了哪些类别，恢复被拒绝",
                )
            }
            null
        } else {
            when (val checked = readableSelection(declaredSelection, parsed.formatVersion)) {
                is SelectionRead.Valid -> checked.summary
                is SelectionRead.Invalid -> return BackupValidation.Invalid(checked.reason)
            }
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
                selection = selectionSummary,
                // 恢复侧 Phase 2c.1 用它和包内库逐表 COUNT 对拍（mask=15 也要对）。
                // v1/v2 没有 selection 块 → null（全量语义，不做行数对拍）。
                selectionRowCounts = declaredSelection?.rowCounts,
            ),
            checkedFileCount = parsed.files.size,
            totalBytes = parsed.files.sumOf { it.byteSize },
        )
    }

    /**
     * 读侧的 selection 判定（T5）。**所有**字段都在交换前判定：格式版本、记录版本、
     * 闭包规则版本、mask、categories、资产策略、86 表 rowCounts、trimmedTableCount 与
     * closureDigest。任何一项不符都返回可读拒因，调用方把它变成
     * [BackupValidation.Invalid]（`validate` 与 `unpack` 共用同一道门）。
     *
     * 为什么不在 JSON 解码时就抛：读侧必须能先解析出**未来版本**的记录，再按「规则版本
     * 不认识」给出可解释的拒绝（与格式版本闸门同一纪律）；解码异常只能得到一句
     * "manifest 解析失败"，说不清是哪条规则不认识。
     */
    private fun readableSelection(
        record: SmbkSelectionRecord,
        formatVersion: Int,
    ): SelectionRead {
        if (formatVersion < SmbkSelectionRecord.INTRODUCED_FORMAT_VERSION) {
            return SelectionRead.Invalid(
                "备份内容选择块与格式版本不一致：formatVersion=$formatVersion 不定义 selection",
            )
        }
        if (record.schemaVersion != SmbkSelectionRecord.CURRENT_SCHEMA_VERSION) {
            return SelectionRead.Invalid(
                "备份内容选择的 schemaVersion=${record.schemaVersion} 不受支持，" +
                    "本机只读 ${SmbkSelectionRecord.CURRENT_SCHEMA_VERSION}",
            )
        }
        if (record.closureVersion != SmbkSelectionPlan.CLOSURE_VERSION) {
            return SelectionRead.Invalid(
                "备份内容选择的 closureVersion=${record.closureVersion} 不受支持，" +
                    "本机规则表为 ${SmbkSelectionPlan.CLOSURE_VERSION}",
            )
        }
        if (record.categoryMask !in SmbkSelectionPlan.allowedMasks) {
            return SelectionRead.Invalid(
                "备份内容选择的 mask=${record.categoryMask} 不被允许；" +
                    "只接受 ${SmbkSelectionPlan.allowedMasks.joinToString("/")}",
            )
        }
        val declared = record.categories.map { name ->
            BackupContentCategory.entries.firstOrNull { it.name == name }
                ?: return SelectionRead.Invalid("备份内容选择包含未知类别：$name")
        }
        if (declared.toSet().size != declared.size) {
            return SelectionRead.Invalid("备份内容选择包含重复类别：${record.categories}")
        }
        val fromMask = BackupContentCategory.entries
            .filterTo(LinkedHashSet()) { category -> record.categoryMask and category.bit != 0 }
        if (declared.toSet() != fromMask) {
            return SelectionRead.Invalid(
                "备份内容选择的 categories=${record.categories} 与 " +
                    "mask=${record.categoryMask} 不一致",
            )
        }
        if (record.assetPolicy != SmbkSelectionPlan.ASSET_POLICY_INCLUDED) {
            return SelectionRead.Invalid(
                "备份内容选择的 assetPolicy=${record.assetPolicy} 不受支持，" +
                    "本机只读 ${SmbkSelectionPlan.ASSET_POLICY_INCLUDED}",
            )
        }
        if (record.rowCounts.keys != SmbkSelectionPlan.rowCountKeys) {
            val missing = (SmbkSelectionPlan.rowCountKeys - record.rowCounts.keys).sorted()
            val extra = (record.rowCounts.keys - SmbkSelectionPlan.rowCountKeys).sorted()
            return SelectionRead.Invalid(
                "备份内容选择的 rowCounts 必须覆盖 86 表：missing=$missing extra=$extra",
            )
        }
        if (record.rowCounts.values.any { count -> count < 0L }) {
            return SelectionRead.Invalid("备份内容选择的 rowCounts 含负数")
        }
        if (record.trimmedTableCount !in 0..SmbkSelectionPlan.trimOrder.size) {
            return SelectionRead.Invalid(
                "备份内容选择的 trimmedTableCount=${record.trimmedTableCount} 越界 " +
                    "(0..${SmbkSelectionPlan.trimOrder.size})",
            )
        }
        val recomputed = runCatching {
            SmbkSelectionPlan.closureDigest(
                categories = SmbkSelectionPlan.categoriesOf(record.categoryMask),
                assetPolicy = record.assetPolicy,
                rowCounts = record.rowCounts,
            )
        }.getOrElse { failure ->
            return SelectionRead.Invalid(
                "备份内容选择无法重算摘要：${failure.message.orEmpty()}",
            )
        }
        if (record.closureDigest != recomputed) {
            return SelectionRead.Invalid(
                "备份内容选择的 closureDigest 与重算不一致：" +
                    "declared=${record.closureDigest} recomputed=$recomputed",
            )
        }
        return SelectionRead.Valid(
            BackupSelectionSummary(mask = record.categoryMask, categories = fromMask),
        )
    }

    /** [readableSelection] 的判定结果；[Invalid] 携带可读拒因。 */
    private sealed interface SelectionRead {
        data class Valid(val summary: BackupSelectionSummary) : SelectionRead

        data class Invalid(val reason: String) : SelectionRead
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
