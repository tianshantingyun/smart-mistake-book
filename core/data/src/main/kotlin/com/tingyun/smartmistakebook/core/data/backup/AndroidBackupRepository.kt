package com.tingyun.smartmistakebook.core.data.backup

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.data.capture.canonicalAssetFileIsIntact
import com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgePackResources
import com.tingyun.smartmistakebook.core.data.settings.MODEL_SECRET_KEY_ALIAS
import com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupReceipt
import com.tingyun.smartmistakebook.core.domain.BackupRepository
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.DeleteAllDataReceipt
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.RestoreReceipt
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.ReviewReminderPreferences
import com.tingyun.smartmistakebook.core.domain.ReviewReminderRepository
import com.tingyun.smartmistakebook.core.domain.SchedulingOptions
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.domain.StorageInventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backup/restore repository implementing the §10.3 target architecture in a
 * deliberately constrained shape:
 *
 *   - Generation isolation: during restore the complete new data set is
 *     materialized as the "next" generation (`<db>.next` + `source-assets.next`)
 *     while the live set, once displaced, becomes the "previous" generation
 *     (`<db>.prev` + `source-assets.prev`).
 *   - Pointer/journal: the durable restore journal records the phase AND the
 *     concrete generation paths, acting as the current-generation pointer.
 *     `BackupRestoreStartupRecovery` reads it on next startup.
 *   - Atomic switch: the live database and the live asset directory are each
 *     replaced by a single atomic rename within the same directory; files and
 *     parent directories are fsynced before and after the renames.
 *   - Deviation from §10.3: the live database stays at its canonical Room
 *     path (`getDatabasePath`) instead of living inside a generation-A/B
 *     directory tree, because every consumer (Room builder, deleteAllData,
 *     backup code) resolves that single path; moving it would ripple through
 *     the Room open path and cannot be verified safely without a device.
 *     The two remaining cross-artifact rename steps are covered by the
 *     journal-driven startup rollback, which is exercised by tests.
 *
 * 6C-1：设置随包走（`settings.json` + 清单里的版本戳）。成包读 [schedulingSettings] /
 * [reviewReminders]（未装配时为 null → 包内没有设置，`hasSettings=false`）；恢复在**交换前**
 * 拒绝读不懂的设置（编解码层的版本与值域闸门），交换成功后才写 DataStore——写失败不回滚
 * 已经一致的数据库，只在回执里如实标 [RestoreSettingsOutcome.WRITE_FAILED]。
 */
class AndroidBackupRepository(
    private val context: Context,
    private val database: StudyDatabasePort,
    private val schedulingSettings: SchedulingSettingsStore? = null,
    private val reviewReminders: ReviewReminderRepository? = null,
) : BackupRepository {
    /** Maximum allowed compression ratio (uncompressed / compressed). */
    private companion object {
        const val MAX_COMPRESSION_RATIO = 100.0
        const val ASSET_DIRECTORY = "source-assets"

        /**
         * 孤儿资产回收的宽限期（30 分钟）。资产文件先落盘、引用随后才在另一个事务里
         * 建立（大堂发送附图：登记资产行 → 写入消息引用），这段在途期里它按引用判定
         * 确实是"孤儿"；没有宽限期，清理会把学生正要发送的那张图连行带文件删掉，
         * 随后建立引用时会撞上 tutor_message_source_asset 的 RESTRICT 外键而让发送失败。
         */
        const val ORPHAN_ASSET_GRACE_MILLIS = 30L * 60L * 1_000L

        /** Process-wide guard making concurrent restore requests idempotent-reject. */
        val restoreInFlight = AtomicBoolean(false)
    }

    override suspend fun inspect(): StorageInventory = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val assetRoot = canonicalAssetRoot()
        val tempFiles = listOf(
            databaseFile.parentFile?.listFiles().orEmpty().toList(),
            assetRoot.listFiles().orEmpty().toList(),
        ).flatten().filter { it.name.startsWith(".raw-") || it.name.startsWith(".canonical-") }
        val assetBytes = assetRoot.listFiles().orEmpty()
            .filter { it.isFile }
            .sumOf(File::length)
        val tempBytes = tempFiles.sumOf(File::length)
        StorageInventory(
            databaseBytes = databaseFile.length(),
            assetBytes = assetBytes,
            tempBytes = tempBytes,
            cleanableBytes = tempBytes,
        )
    }

    override suspend fun cleanupOrphanAssets(): Int = withContext(Dispatchers.IO) {
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        // 认领在数据库侧一个写事务内完成，因此认领前提交的任何新引用都会保住它的行；
        // 这里只负责删除被认领行的文件。返回值是真正删掉的图片数，与存储页文案
        //「已清理 N 张…题图」一致（行删掉但文件已不在时不该报成一张）。
        val claimed = database.claimUnreferencedCanonicalAssets(
            createdBeforeEpochMillis = System.currentTimeMillis() - ORPHAN_ASSET_GRACE_MILLIS,
        )
        claimed.count { asset -> runCatching { vault.delete(asset) }.isSuccess }
    }

    /**
     * 成包顺序（AUD-05）：**先快照库**，再按快照库反查引用资产与题数，只收这些资产。
     *
     * 旧顺序（先枚举资产目录、后快照库）里，两步之间"登记资产 + 建立引用"的图会出现在
     * 快照的引用集合里，却不在资产清单里——包内 DB 引用了包内没有的图。反过来，快照之后
     * 才登记的资产不算本次引用（它是下一包的）：它在那份快照里根本没有行，反查不到。
     * 快照里**已引用**的资产缺失/被替换则成包失败（见 [resolveReferencedAssetFiles]），
     * 不静默跳过。
     *
     * 6C-1 追加：版本戳（应用版本、随包内容版本）与设置快照都取"同一个时刻"——
     * 内容版本取自**快照里那份库**（不是实时库），设置读失败则整包失败（静默少一份设置，
     * 就是"备份看起来成功、恢复后设置丢了"）。
     */
    override suspend fun create(
        destination: OutputStream,
        options: BackupOptions,
    ): BackupReceipt = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        // 设置先读：读失败要在做快照之前失败，别留下半个归档的错觉。
        val settings = readSettingsForBackup()
        // Consistency snapshot: VACUUM INTO when the device SQLite supports it,
        // otherwise WAL checkpoint + read-only copy (see BackupPort docs). The
        // archive is always built from the snapshot file, never from the live
        // database file.
        val snapshotDir = File(context.cacheDir, "backup-snapshots").apply { mkdirs() }
        val snapshotFile = File(snapshotDir, "snapshot-${System.nanoTime()}.db")
        try {
            database.snapshotForBackup(databaseFile, snapshotFile)
            withSnapshotDatabase(snapshotFile) { snapshot ->
                // 题数与引用资产都取自**这一份快照**：实时库上的数会在窗口里超前于归档内容。
                val problemCount = snapshot.countMistakes()
                val referencedAssets = snapshot.readReferencedCanonicalAssets()
                val assetFiles = resolveReferencedAssetFiles(referencedAssets)
                SmbkArchiveCodec.create(
                    archive = destination,
                    database = snapshotFile,
                    assets = if (options.includeAssets) assetFiles else emptyList(),
                    // 声明的是快照库的引用集合本身：若将来允许"只备数据库"，收图与声明
                    // 一旦不一致，成包会当场失败，而不是写出 DB 引用了包内没有的图的归档。
                    referencedAssets = assetFiles,
                    databaseSchemaVersion = snapshotFile.readSchemaVersion(),
                    problemCount = problemCount,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    metadata = SmbkArchiveMetadata(
                        appVersionName = packageVersionName(),
                        appVersionCode = packageVersionCode(),
                        contentVersion = snapshot.readBundledContentVersion(),
                    ),
                    settings = settings,
                )
            }
        } finally {
            snapshotFile.delete()
        }
    }

    /**
     * 成包时的设置快照。未装配设置口（只有测试会这样）时返回 null → 包内没有
     * `settings.json`、`hasSettings=false`，读侧不会把"没有设置"误读成"设置是默认值"。
     *
     * 装了设置口却读不出来时**成包失败**：一份声称完整、实际少了设置的包，比一次明确的
     * 失败危险得多（用户会在恢复后才发现设置没了，而那时旧设置已被替换）。
     */
    private suspend fun readSettingsForBackup(): SmbkSettingsDocumentV1? {
        val scheduling = schedulingSettings ?: return null
        val reminders = reviewReminders ?: return null
        return try {
            val reminder = reminders.current()
            SmbkSettingsDocumentV1(
                desiredRetention = scheduling.options.first().desiredRetention,
                examCalendar = scheduling.exams.first(),
                optimizedFsrsParameters = scheduling.optimizedParameters.first()?.toList(),
                reviewReminderEnabled = reminder.enabled,
                reviewReminderMinutesAfterMidnight = reminder.minutesAfterMidnight,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error("设置读取失败，备份未完成：${failure.message.orEmpty()}")
        }
    }

    /** 应用自身的 `versionName`；读不到就是 null（版本戳是诊断字段，不许假造）。 */
    private fun packageVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /** 应用自身的 `versionCode`（API 28 起是 long 版本号，经 PackageInfoCompat 归一）。 */
    private fun packageVersionCode(): Long? = runCatching {
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        )
    }.getOrNull()

    /**
     * 归档里那份库的随包内容版本（`content_install_state.content_version`）。
     *
     * 查的是**随包那个内容包**的进度行（`BundledKnowledgePackResources.updateManifest.packId`
     * ——"哪个包是随包的"只有那一处说了算，不在这里抄一份 id）。解析随包清单失败时返回 null：
     * 版本戳是诊断字段，读不到就写"未知"，不让备份因此失败。
     */
    private suspend fun StudyDatabasePort.readBundledContentVersion(): String? {
        val packId = runCatching { BundledKnowledgePackResources.updateManifest?.packId }
            .getOrNull()
        if (packId.isNullOrEmpty()) return null
        return readContentInstallState(packId)?.contentVersion?.takeIf { it.isNotEmpty() }
    }

    /**
     * 在**快照文件**上开一个读口。Room 只能按数据库名打开，因此先把快照复制进 databases
     * 目录、按名打开，读完连同 -wal/-shm 一起删掉——与恢复路径的
     * [inspectStagedDatabase] 是同一个做法（复制成临时库名再开），不引入第二套打开方式。
     *
     * 消灭的失败：成包的题数与引用集合在旧实现里取自**实时库**（`countMistakes()` 与资产
     * 目录枚举），与归档里那份快照不是同一时刻的数据；并发录入/生图时这个窗口会把包内引用
     * 与包内容撕开（AUD-05）。复制出来读还有一个顺带的好处：被归档的那个文件始终没有写者，
     * 校验与会话期间不会发生变化。
     */
    private suspend fun <T> withSnapshotDatabase(
        snapshotFile: File,
        block: suspend (StudyDatabasePort) -> T,
    ): T {
        val snapshotName = "backup-snapshot-${UUID.randomUUID()}.db"
        val snapshotDatabaseFile = context.getDatabasePath(snapshotName)
        var snapshotDatabase: StudyDatabasePort? = null
        try {
            snapshotFile.copyTo(snapshotDatabaseFile, overwrite = true)
            val opened = StudyDatabaseFactory.open(context, snapshotName)
            snapshotDatabase = opened
            return block(opened)
        } finally {
            runCatching { snapshotDatabase?.close() }
            listOf(
                snapshotDatabaseFile,
                File("${snapshotDatabaseFile.absolutePath}-wal"),
                File("${snapshotDatabaseFile.absolutePath}-shm"),
            ).forEach { it.delete() }
        }
    }

    /**
     * 快照库引用的资产行 → 磁盘文件，逐条按 vault 自己的完整性判据核对（存在 + 字节数 +
     * SHA-256），并顺带挡住越出资产库的路径（`resolve` 的包含性检查）。
     *
     * 用 [AndroidCanonicalAssetVault.resolve] 而不是在这里重写一遍：'文件仍在且逐位一致'
     * 的定义只有 vault 那一处，备份不会与读取面（出网/PDF/预览）漂开。缺文件或被改动时
     * **成包失败**——静默跳过就是 AUD-05 那个"包内 DB 引用了包内没有的图"。
     */
    private fun resolveReferencedAssetFiles(
        referencedAssets: List<CanonicalSourceAssetRecord>,
    ): List<File> {
        val vault = AndroidCanonicalAssetVault(context.applicationContext)
        return referencedAssets.map { record ->
            runCatching { vault.resolve(record) }.getOrElse { failure ->
                error(
                    "快照引用的题图缺失或已损坏，备份未完成：${record.relativePath}" +
                        "（${failure.message.orEmpty()}）",
                )
            }
        }
    }

    override suspend fun validate(source: InputStream): BackupValidation =
        withContext(Dispatchers.IO) {
            // Streaming validation: entries are written to a scratch directory
            // and SHA-256 is computed while streaming; nothing large is kept
            // in memory. The scratch directory is always cleaned up.
            val scratch = File(context.cacheDir, "validate-${UUID.randomUUID()}")
            try {
                SmbkArchiveCodec.validate(source, scratch)
            } finally {
                scratch.deleteRecursively()
            }
        }

    override suspend fun restore(source: InputStream): RestoreReceipt =
        withContext(Dispatchers.IO) {
            // Idempotency: a second, overlapping restore request is rejected
            // instead of racing the in-flight generation switch.
            if (!restoreInFlight.compareAndSet(false, true)) {
                throw BackupRestoreException("恢复已在进行中，重复的恢复请求被拒绝")
            }
            try {
                doRestore(source)
            } finally {
                restoreInFlight.set(false)
            }
        }

    /** suspend：Phase 2b 会用归档库端口读它的题数与引用集合（两条都是挂起读）。 */
    private suspend fun doRestore(source: InputStream): RestoreReceipt {
        val restoreId = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "restore-$restoreId")
        val staging = File(root, "staging").apply { mkdirs() }
        val journal = RestoreJournal(context, restoreId)

        val databaseFile = context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val databasesDir = databaseFile.parentFile ?: context.filesDir
        val assetRoot = canonicalAssetRoot()
        val filesDir = assetRoot.parentFile ?: context.filesDir
        val previousDb = File(databasesDir, "${databaseFile.name}.prev")
        val nextDb = File(databasesDir, "${databaseFile.name}.next")
        val previousAssets = File(filesDir, "$ASSET_DIRECTORY.prev")
        val nextAssets = File(filesDir, "$ASSET_DIRECTORY.next")
        var swapStarted = false
        var databaseClosed = false
        // Phase 2d 读出的设置载荷；Phase 8（交换提交后）才写入，故意放在 try 之外——
        // 写设置的任何失败/取消都不许被下面那段回滚路径接住（那时 journal 已清，
        // 回滚只会去删已经不存在的代，然后把"恢复失败，已回到原来的数据"说成事实）。
        var restoredSettings: RestorableSettings? = null

        val receipt = try {
            // Phase 1: Validate and unpack (counting compressed bytes for zip-bomb ratio)
            journal.writePhase(RestorePhase.VALIDATING)
            val countingSource = CountingInputStream(source)
            val validation = SmbkArchiveCodec.unpack(countingSource, staging)
            val valid = validation as? BackupValidation.Valid
                ?: throw BackupRestoreException(
                    (validation as BackupValidation.Invalid).reason,
                )

            // Phase 2: Free-space preflight
            journal.writePhase(RestorePhase.PREFLIGHT)
            val stagingDb = File(staging, "database.sqlite")
            val currentDbSize = if (databaseFile.exists()) databaseFile.length() else 0L
            val stagedDbSize = if (stagingDb.exists()) stagingDb.length() else 0L
            val currentAssetSize = assetRoot.listFiles().orEmpty()
                .filter { it.isFile }
                .sumOf(File::length)
            val stagedAssetSize = File(staging, "assets").listFiles().orEmpty()
                .filter { it.isFile }
                .sumOf(File::length)
            val freeSpace = context.filesDir.freeSpace

            // Required space: next-generation database + assets copies, the
            // displaced current generation kept for rollback, working space.
            val safetySnapshotSize = currentDbSize + currentAssetSize
            val workingSpace = 10L * 1024 * 1024 // 10MB
            val requiredSpace =
                stagedDbSize + stagedAssetSize + safetySnapshotSize + workingSpace

            if (freeSpace < requiredSpace) {
                val availableMB = freeSpace / 1024 / 1024
                val requiredMB = requiredSpace / 1024 / 1024
                throw BackupRestoreException(
                    "磁盘空间不足：需要 ${requiredMB}MB，可用 ${availableMB}MB。" +
                        "其中新数据 ${stagedDbSize / 1024 / 1024}MB，" +
                        "安全快照 ${safetySnapshotSize / 1024 / 1024}MB，" +
                        "工作空间 ${workingSpace / 1024 / 1024}MB",
                )
            }

            // Verify compression ratio is reasonable (prevent zip bombs).
            // Compare compressed archive bytes against decompressed staging size.
            val compressedBytes = countingSource.count()
            val totalStagedSize = stagedDbSize + stagedAssetSize
            if (compressedBytes > 0 && totalStagedSize > 0) {
                val compressionRatio = totalStagedSize.toDouble() / compressedBytes
                if (compressionRatio > MAX_COMPRESSION_RATIO) {
                    throw BackupRestoreException(
                        "压缩比异常（1:$compressionRatio），可能存在损坏的归档",
                    )
                }
            }

            // Phase 2b: Validate the STAGED database BEFORE any swap so a
            // corrupt or incompatible backup never replaces the live data:
            // Room open + migrations + user_version + quick_check +
            // foreign_key_check.
            val stagedDbInspection = runCatching { inspectStagedDatabase(stagingDb) }
            val stagedDatabase = stagedDbInspection.getOrNull()
                ?: throw BackupRestoreException(
                    "备份数据库预验证失败：" +
                        "${stagedDbInspection.exceptionOrNull()?.message.orEmpty()}",
                )

            // Phase 2c: DB→资产闭合（AUD-05）：归档里那份库引用的每一张图都必须在解包
            // 出来的资产目录里，且与资产行记的字节数/SHA-256 逐位一致。缺一张就在这里
            // 拒绝——此时还没有任何切换发生，原数据、原图都还在原地。
            verifyStagedAssetClosure(staging, stagedDatabase.referencedAssets)

            // Phase 2d: 设置载荷读进内存（6C-1）。兼容性闸门在编解码层（Phase 1 的 unpack
            // 已经按版本/值域拒过），这里只是把载荷映射成领域对象；映射不可能失败，但若
            // 真失败也必须发生在**交换前**——交换后才失败就是"数据库已切换、设置没进来"。
            restoredSettings = readStagedSettings(staging)

            // Phase 3: Prepare generations and record the rollback journal.
            journal.writePhase(RestorePhase.BACKUP_CURRENT)
            deleteGenerationArtifacts(previousDb, nextDb, previousAssets, nextAssets)
            journal.recordGenerationPaths(
                liveDatabasePath = databaseFile,
                previousDatabasePath = previousDb,
                nextDatabasePath = nextDb,
                liveAssetDir = assetRoot,
                previousAssetDir = previousAssets,
                nextAssetDir = nextAssets,
                stagingDir = root,
            )
            journal.recordCurrentState(databaseFile, assetRoot)

            // Phase 4: Close the database and perform the atomic switch.
            journal.writePhase(RestorePhase.SWAPPING)
            database.close()
            databaseClosed = true
            listOf(
                File("${databaseFile.absolutePath}-wal"),
                File("${databaseFile.absolutePath}-shm"),
            ).forEach { it.delete() }

            // Build the next asset generation and make it durable BEFORE any
            // pointer moves.
            nextAssets.mkdirs()
            File(staging, "assets").listFiles().orEmpty()
                .filter { it.isFile }
                .forEach { staged ->
                    val target = File(nextAssets, staged.name)
                    staged.copyTo(target, overwrite = true)
                    fsyncFile(target)
                }
            fsyncDir(nextAssets)

            // Build the next database generation in the databases directory
            // (same filesystem as the live file → rename will be atomic).
            stagingDb.copyTo(nextDb, overwrite = true)
            fsyncFile(nextDb)
            fsyncDir(databasesDir)

            swapStarted = true

            // Atomic switch #1: database. Each rename is atomic within the
            // same directory; the journal (written above) is the pointer that
            // lets startup recovery undo a half-done switch.
            if (databaseFile.exists() && !databaseFile.renameTo(previousDb)) {
                error("Cannot displace live database into previous generation")
            }
            fsyncDir(databasesDir)
            if (!nextDb.renameTo(databaseFile)) {
                // Best-effort in-line repair before escalating to the journal.
                if (previousDb.exists() && previousDb.renameTo(databaseFile)) {
                    swapStarted = false
                }
                error("Cannot promote next database generation")
            }
            fsyncDir(databasesDir)

            // Atomic switch #2: asset directory.
            if (assetRoot.isDirectory && !assetRoot.renameTo(previousAssets)) {
                error("Cannot displace live asset generation")
            }
            fsyncDir(filesDir)
            if (!nextAssets.renameTo(assetRoot)) {
                if (previousAssets.isDirectory && previousAssets.renameTo(assetRoot)) {
                    fsyncDir(filesDir)
                }
                error("Cannot promote next asset generation")
            }
            fsyncDir(filesDir)

            // Phase 5: Verify restored database
            journal.writePhase(RestorePhase.VERIFYING)
            verifyRestoredDatabase(databaseFile)

            // Phase 6: Fsync to ensure durability
            journal.writePhase(RestorePhase.FSYNCING)
            fsyncFile(databaseFile)
            fsyncDir(databasesDir)
            fsyncDir(assetRoot)
            fsyncDir(filesDir)

            // Phase 7: Clean up previous generation + staging + journal.
            journal.writePhase(RestorePhase.COMPLETED)
            deleteGenerationArtifacts(previousDb, nextDb, previousAssets, nextAssets)
            root.deleteRecursively()
            journal.clear()

            // 回执的数取自归档里那份库本身（题数）与刚刚过了闭合校验的引用集合
            // （题图数），不取 manifest 里那张没人核对过的清单（AUD-05）：
            // manifest 是成包那一刻写的，而用户要看到的是"这次恢复进来的是什么"。
            // 设置结果在方法尾部补（Phase 8），此处留缺省值。
            RestoreReceipt(
                databaseSchemaVersion = valid.manifest.databaseSchemaVersion,
                problemCount = stagedDatabase.problemCount,
                assetCount = stagedDatabase.referencedAssets.size,
                fileCount = valid.manifest.fileCount,
            )
        } catch (failure: Exception) {
            runCatching { journal.writePhase(RestorePhase.ROLLING_BACK) }
            val entry = journal.readEntry()
            if (swapStarted && entry != null) {
                val problems = RestoreGenerationSupport.rollback(context, entry)
                if (problems.isNotEmpty()) {
                    // A failed rollback must never silently trust the mixed
                    // state: quarantine the untrusted generation artifacts.
                    RestoreGenerationSupport.quarantine(context, entry)
                    runCatching { journal.writePhase(RestorePhase.ROLLBACK_FAILED) }
                    val quarantined = BackupRestoreException(
                        "恢复失败且回滚不完整，相关数据已隔离：${problems.joinToString("; ")}",
                        failure,
                    )
                    if (databaseClosed) {
                        throw BackupRestoreDatabaseClosedException(quarantined.message.orEmpty(), quarantined)
                    }
                    throw quarantined
                }
                RestoreGenerationSupport.cleanup(context, entry)
                journal.clear()
            } else {
                // Nothing was swapped yet; just remove partial artifacts.
                deleteGenerationArtifacts(previousDb, nextDb, previousAssets, nextAssets)
                root.deleteRecursively()
                journal.clear()
            }
            if (databaseClosed) {
                // 文件已回滚到恢复前状态，但进程内连接已在 swap 前关闭；database
                // 端口是构造注入到全部仓库的，无法热重开——如实告知用户必须重启，
                // 而不是让其带着一个已关闭的连接继续操作。
                throw BackupRestoreDatabaseClosedException(
                    "恢复失败，已回到原来的数据。",
                    failure,
                )
            }
            throw failure
        }

        // Phase 8（6C-1）：交换已提交、journal 已清，这里才写设置。写失败不回滚数据库
        // （数据已经一致），只在回执里如实标 partial；app 层据此把"部分恢复"说出来。
        val settingsOutcome = when (val settings = restoredSettings) {
            null -> RestoreSettingsOutcome.NOT_IN_ARCHIVE
            else -> applyRestoredSettings(settings)
        }
        return receipt.copy(settings = settingsOutcome)
    }

    /**
     * 归档里的设置载荷，解出来备用；包内没有设置（v1 旧包，或成包时没装设置口）返回 null。
     *
     * 版本与值域在 Phase 1 的 unpack 里已经过了编解码层的闸门（读不懂的包到不了这里），
     * 这里只做"文档 → 领域对象"的映射；映射若失败仍必须在交换前抛（[BackupRestoreException]），
     * 因为交换后才失败就是"数据库已切换、设置没进来"的半恢复。
     */
    private fun readStagedSettings(stagingDir: File): RestorableSettings? {
        val settingsFile = File(stagingDir, SmbkArchiveCodec.SETTINGS_ENTRY)
        if (!settingsFile.isFile) return null
        return try {
            val document = SmbkSettingsCodec.decode(settingsFile.readText())
            RestorableSettings(
                scheduling = SchedulingOptions(document.desiredRetention),
                exams = document.examCalendar,
                optimizedParameters = document.optimizedFsrsParameters?.toDoubleArray(),
                reminder = ReviewReminderPreferences(
                    enabled = document.reviewReminderEnabled,
                    minutesAfterMidnight = document.reviewReminderMinutesAfterMidnight,
                ),
            )
        } catch (failure: Exception) {
            throw BackupRestoreException("备份设置无法读取：${failure.message.orEmpty()}", failure)
        }
    }

    /**
     * 把备份里的设置写进本机 DataStore。**只写偏好**：提醒只写 `enabled` 与
     * `minutes_after_midnight` 两个键——系统通知授权不在本机偏好里（这里无法也不应复活它），
     * 投递历史（"今天已经提醒过"）也不在备份载荷里、因此不会被覆盖。
     *
     * 写失败（或设置口未装配）返回 [RestoreSettingsOutcome.WRITE_FAILED]，绝不抛：
     * 此刻数据库已一致，把它拖进回滚路径会把用户的数据再翻一遍。
     */
    private suspend fun applyRestoredSettings(settings: RestorableSettings): RestoreSettingsOutcome {
        val scheduling = schedulingSettings ?: return RestoreSettingsOutcome.WRITE_FAILED
        val reminders = reviewReminders ?: return RestoreSettingsOutcome.WRITE_FAILED
        return try {
            scheduling.setOptions(settings.scheduling)
            scheduling.exams.first().forEach { existing -> scheduling.removeExam(existing.entryId) }
            settings.exams.forEach { exam -> scheduling.addExam(exam) }
            scheduling.setOptimizedParameters(settings.optimizedParameters)
            reminders.setEnabled(settings.reminder.enabled)
            reminders.setReminderTime(settings.reminder.minutesAfterMidnight)
            RestoreSettingsOutcome.APPLIED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w(
                "SmartMistakeBook",
                "设置写入失败；数据库已恢复，设置保持原样：${failure.message.orEmpty()}",
            )
            RestoreSettingsOutcome.WRITE_FAILED
        }
    }

    /**
     * 已映射成领域对象的设置载荷：映射即校验（[SchedulingOptions] / [ReviewReminderPreferences]
     * 的构造器自带值域约束），到 Phase 8 只剩"写"这一个动作。
     */
    private data class RestorableSettings(
        val scheduling: SchedulingOptions,
        val exams: List<ExamCalendarEntry>,
        val optimizedParameters: DoubleArray?,
        val reminder: ReviewReminderPreferences,
    )

    private fun deleteGenerationArtifacts(
        previousDb: File,
        nextDb: File,
        previousAssets: File,
        nextAssets: File,
    ) {
        listOf(
            previousDb,
            File("${previousDb.absolutePath}-wal"),
            File("${previousDb.absolutePath}-shm"),
            nextDb,
            File("${nextDb.absolutePath}-wal"),
            File("${nextDb.absolutePath}-shm"),
        ).forEach { it.delete() }
        previousAssets.deleteRecursively()
        nextAssets.deleteRecursively()
    }

    override suspend fun deleteAllData(): DeleteAllDataReceipt = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)
        val assetRoot = canonicalAssetRoot()
        val databaseBytes = databaseFile.length()
        val assetBytes = assetRoot.listFiles().orEmpty().filter { it.isFile }.sumOf(File::length)
        // SharedPreferences live under the app data root, NOT under filesDir.
        // Context#getDataDir only exists from API 24 while minSdk is 23, so go
        // through ContextCompat: on 23 it resolves the root from ApplicationInfo
        // instead of throwing NoSuchMethodError on a delete-all path.
        val preferenceFiles = ContextCompat.getDataDir(context)
            ?.let { File(it, "shared_prefs") }
            ?.listFiles().orEmpty()
            .filter { it.isFile }
            .toList()
        val preferenceBytes = preferenceFiles.sumOf(File::length)
        val datastoreFiles = File(context.filesDir, "datastore").listFiles().orEmpty()
            .filter { it.isFile }
            .toList()
        val tempFiles = context.cacheDir.listFiles().orEmpty().toList()
        val tempBytes = tempFiles.sumOf(File::length)

        // 1. Cancel all WorkManager tasks
        try {
            androidx.work.WorkManager.getInstance(context.applicationContext).cancelAllWork()
        } catch (_: Exception) {
            // WorkManager may not be initialized; continue cleanup
        }

        // 2. Close and delete database files (including generation artifacts)
        database.close()
        listOf(
            databaseFile,
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm"),
            File("${databaseFile.absolutePath}.prev"),
            File("${databaseFile.absolutePath}.next"),
        ).forEach { it.delete() }

        // 3. Delete canonical assets (including generation artifacts)
        assetRoot.listFiles().orEmpty().forEach { it.deleteRecursively() }
        assetRoot.delete()
        val assetParent = assetRoot.parentFile
        if (assetParent != null) {
            File(assetParent, "$ASSET_DIRECTORY.prev").deleteRecursively()
            File(assetParent, "$ASSET_DIRECTORY.next").deleteRecursively()
        }

        // 4. Delete SharedPreferences
        preferenceFiles.forEach { it.delete() }

        // 5. Delete DataStore files
        datastoreFiles.forEach { it.delete() }

        // 6. Delete temporary/cache files
        tempFiles.forEach { it.deleteRecursively() }

        // 7. Delete secret vault files (API key ciphertext)
        val secretVaultDir = File(context.noBackupFilesDir, "model-secrets")
        val secretVaultBytes = if (secretVaultDir.exists()) {
            val bytes = secretVaultDir.listFiles().orEmpty().sumOf(File::length)
            secretVaultDir.deleteRecursively()
            bytes
        } else {
            0L
        }

        // 8. Clear the Keystore entry that protects the model API key. The
        // alias is authored by the vault, so delete exactly that alias instead
        // of re-deriving its name here — a second copy of the string is how the
        // sweep previously missed the entry and left the key behind.
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MODEL_SECRET_KEY_ALIAS)) {
                keyStore.deleteEntry(MODEL_SECRET_KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Keystore may not be accessible; continue cleanup
        }

        // 9. Delete notifications
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            notificationManager.cancelAll()
        } catch (_: Exception) {
            // Notification manager may not be available
        }

        // 10. Delete restore journals and quarantined restore artifacts.
        context.noBackupFilesDir.listFiles().orEmpty()
            .filter {
                it.name.startsWith("restore-journal-") || it.name == "restore-quarantine"
            }
            .forEach { it.deleteRecursively() }

        DeleteAllDataReceipt(
            deletedDatabaseBytes = databaseBytes,
            deletedAssetBytes = assetBytes,
            deletedPreferenceBytes = preferenceBytes + datastoreFiles.sumOf(File::length),
            deletedTempBytes = tempBytes,
            deletedSecretVaultBytes = secretVaultBytes,
        )
    }

    private fun canonicalAssetRoot(): File {
        val root = File(context.filesDir, ASSET_DIRECTORY)
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            error("Cannot access canonical asset vault")
        }
        return root.canonicalFile
    }

    private fun File.readSchemaVersion(): Int = try {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }
    } catch (_: Exception) {
        -1
    }

    /**
     * 归档库在预验证时一并读出的闭合数据：题数与四表引用口径下仍被引用的资产行。
     * 恢复回执与 [verifyStagedAssetClosure] 都用它，而不是 manifest 里那张清单。
     */
    private data class StagedDatabaseInspection(
        val problemCount: Int,
        val referencedAssets: List<CanonicalSourceAssetRecord>,
    )

    /**
     * Validate the staged database BEFORE it replaces the live one:
     *   1. Room opens a throwaway copy with the full production builder, so
     *      every registered migration runs exactly as it would in production;
     *   2. PRAGMA user_version of the migrated copy must equal the live
     *      database's user_version (a backup from a newer schema is rejected
     *      because Room cannot open it; an older one is accepted only after
     *      it migrates to the same version);
     *   3. quick_check for page-level corruption and foreign_key_check for
     *      referential integrity.
     * Any failure aborts restore before the swap.
     *
     * 迁移后的那份库顺手回答两个只有它能回答的问题（AUD-05）：它自己有多少道题、它引用了
     * 哪些图。恢复侧此前的两个数都直接抄 manifest，既没核对过、也不代表归档里的库。
     */
    private suspend fun inspectStagedDatabase(
        stagedDatabaseFile: File,
    ): StagedDatabaseInspection {
        if (!stagedDatabaseFile.exists()) {
            throw BackupRestoreException("备份中缺少数据库文件")
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(
            stagedDatabaseFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            listOf("PRAGMA quick_check", "PRAGMA foreign_key_check").forEach { pragma ->
                db.rawQuery(pragma, null).use { cursor ->
                    if (pragma == "PRAGMA quick_check") {
                        if (cursor.moveToFirst() && cursor.getString(0) != "ok") {
                            throw BackupRestoreException(
                                "quick_check 失败: ${cursor.getString(0)}",
                            )
                        }
                    } else {
                        if (cursor.moveToFirst()) {
                            throw BackupRestoreException("foreign_key_check 发现引用完整性问题")
                        }
                    }
                }
            }
        }

        // Room-level pre-validation: run the staged database through the
        // production builder (migrations included) on a throwaway copy.
        val validationName = "restore-validate-${UUID.randomUUID()}.db"
        val validationCopy = context.getDatabasePath(validationName)
        var validationDatabase: StudyDatabasePort? = null
        try {
            stagedDatabaseFile.copyTo(validationCopy, overwrite = true)
            val opened = StudyDatabaseFactory.open(context, validationName)
            validationDatabase = opened
            val inspection = StagedDatabaseInspection(
                problemCount = opened.countMistakes(),
                referencedAssets = opened.readReferencedCanonicalAssets(),
            )
            opened.close()
            validationDatabase = null
            val migratedVersion = validationCopy.readSchemaVersion()
            val liveDatabaseFile = context.getDatabasePath(
                StudyDatabaseFactory.DEFAULT_DATABASE_NAME,
            )
            if (liveDatabaseFile.exists()) {
                val liveVersion = liveDatabaseFile.readSchemaVersion()
                if (migratedVersion != liveVersion) {
                    throw BackupRestoreException(
                        "备份数据库迁移后版本($migratedVersion)与当前数据库版本($liveVersion)不一致",
                    )
                }
            }
            return inspection
        } catch (validationFailure: Exception) {
            if (validationFailure is BackupRestoreException) throw validationFailure
            throw BackupRestoreException(
                "备份数据库无法通过 Room 迁移校验：${validationFailure.message.orEmpty()}",
                validationFailure,
            )
        } finally {
            runCatching { validationDatabase?.close() }
            listOf(
                validationCopy,
                File("${validationCopy.absolutePath}-wal"),
                File("${validationCopy.absolutePath}-shm"),
            ).forEach { it.delete() }
        }
    }

    /**
     * Phase 2c 的 DB→资产闭合：归档里那份库引用的每一张图都必须在解包出来的资产目录里，
     * 且与资产行记的字节数/SHA-256 逐位一致（[canonicalAssetFileIsIntact]，与 vault、
     * 出网、PDF 同一条判据）；路径必须正好是 `source-assets/<文件名>`，多一层目录或越出
     * 资产库都在这里拒绝。
     *
     * 为什么必须在**切换前**：切换之后才发现缺图，用户看到的是一次"恢复成功"的缺图数据集，
     * 而原数据已经被挪到上一代。这里抛出走既有的失败路径（尚未 swap，直接清理临时件）。
     * 只要求"引用的都在"（方向性）：旧包把整个资产目录都打进来，多出来的图不影响恢复。
     */
    private fun verifyStagedAssetClosure(
        stagingDir: File,
        referencedAssets: List<CanonicalSourceAssetRecord>,
    ) {
        val stagedAssetDir = File(stagingDir, "assets")
        val problems = referencedAssets.mapNotNull { record ->
            val fileName = record.relativePath.substringAfterLast('/')
            val stagedFile = File(stagedAssetDir, fileName)
            when {
                fileName.isBlank() || record.relativePath != "$ASSET_DIRECTORY/$fileName" ->
                    "${record.relativePath}（资产行路径非法）"
                !stagedFile.isFile -> "${record.relativePath}（包内缺失）"
                !canonicalAssetFileIsIntact(
                    stagedFile,
                    record.byteSize,
                    record.contentSha256,
                ) -> "${record.relativePath}（字节数或 SHA-256 与资产行不一致）"
                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            throw BackupRestoreException(
                "备份数据库引用的题图在包内缺失或已损坏，恢复被拒绝：" +
                    problems.joinToString(separator = "、"),
            )
        }
    }

    /**
     * Verify the restored database is valid and can be opened by Room.
     */
    private fun verifyRestoredDatabase(databaseFile: File) {
        if (!databaseFile.exists()) {
            throw BackupRestoreException("恢复后的数据库文件不存在")
        }

        // Check SQLite magic bytes
        val magic = databaseFile.inputStream().use { stream ->
            val buffer = ByteArray(16)
            stream.read(buffer)
            String(buffer.take(16).toByteArray())
        }
        if (!magic.startsWith("SQLite format 3")) {
            throw BackupRestoreException("恢复后的文件不是有效的 SQLite 数据库")
        }

        // Run integrity check
        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                val cursor = db.rawQuery("PRAGMA integrity_check", null)
                cursor.use {
                    if (it.moveToFirst()) {
                        val result = it.getString(0)
                        if (result != "ok") {
                            throw BackupRestoreException(
                                "数据库完整性检查失败: $result",
                            )
                        }
                    }
                }
            }
        } catch (e: android.database.sqlite.SQLiteException) {
            throw BackupRestoreException("无法打开恢复后的数据库", e)
        }
    }
}

/**
 * Durable restore journal that tracks the restore phase AND the concrete
 * generation paths. On process death, the journal is read on next startup
 * (BackupRestoreStartupRecovery) to decide resume/rollback/quarantine.
 */
internal class RestoreJournal(
    private val context: Context,
    private val restoreId: String,
) {
    private val journalFile: File
        get() = File(context.noBackupFilesDir, "restore-journal-$restoreId.json")

    private var entry = JournalEntry(
        restoreId = restoreId,
        phase = RestorePhase.VALIDATING.name,
        timestamp = System.currentTimeMillis(),
    )

    fun exists(): Boolean = journalFile.exists()

    fun writePhase(phase: RestorePhase) {
        entry = entry.copy(phase = phase.name, timestamp = System.currentTimeMillis())
        persist()
    }

    /** Records the generation layout so startup recovery can execute rollback. */
    fun recordGenerationPaths(
        liveDatabasePath: File,
        previousDatabasePath: File,
        nextDatabasePath: File,
        liveAssetDir: File,
        previousAssetDir: File,
        nextAssetDir: File,
        stagingDir: File,
    ) {
        entry = entry.copy(
            liveDatabasePath = liveDatabasePath.absolutePath,
            previousDatabasePath = previousDatabasePath.absolutePath,
            nextDatabasePath = nextDatabasePath.absolutePath,
            liveAssetDir = liveAssetDir.absolutePath,
            previousAssetDir = previousAssetDir.absolutePath,
            nextAssetDir = nextAssetDir.absolutePath,
            stagingDir = stagingDir.absolutePath,
        )
        persist()
    }

    fun recordCurrentState(databaseFile: File, assetRoot: File) {
        entry = entry.copy(
            timestamp = System.currentTimeMillis(),
            currentState = CurrentState(
                databaseExists = databaseFile.exists(),
                databaseSize = if (databaseFile.exists()) databaseFile.length() else 0,
                assetCount = assetRoot.listFiles()?.size ?: 0,
                freeSpace = context.filesDir.freeSpace,
            ),
        )
        persist()
    }

    fun clear() {
        if (journalFile.exists()) {
            journalFile.delete()
        }
        fsyncDir(context.noBackupFilesDir)
    }

    fun readPhase(): RestorePhase? = readEntry()?.let { entry ->
        runCatching { RestorePhase.valueOf(entry.phase) }.getOrNull()
    }

    fun readEntry(): JournalEntry? {
        if (!journalFile.exists()) return null
        return try {
            kotlinx.serialization.json.Json.decodeFromString(
                JournalEntry.serializer(),
                journalFile.readText(),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Atomic journal write: temp file + fsync + rename over the journal. */
    private fun persist() {
        val text = kotlinx.serialization.json.Json.encodeToString(
            JournalEntry.serializer(),
            entry,
        )
        val parent = journalFile.parentFile ?: return
        val temp = File(parent, "${journalFile.name}.tmp")
        temp.outputStream().use { output ->
            output.write(text.toByteArray())
            output.flush()
            runCatching { output.fd.sync() }
        }
        if (!temp.renameTo(journalFile)) {
            temp.delete()
        }
        fsyncDir(parent)
    }
}

internal enum class RestorePhase {
    VALIDATING,
    PREFLIGHT,
    BACKUP_CURRENT,
    SWAPPING,
    VERIFYING,
    FSYNCING,
    COMPLETED,
    ROLLING_BACK,
    ROLLBACK_FAILED,
}

@kotlinx.serialization.Serializable
internal data class JournalEntry(
    val restoreId: String,
    val phase: String,
    val timestamp: Long,
    val currentState: CurrentState? = null,
    /** Live database file path (the current-generation pointer target). */
    val liveDatabasePath: String? = null,
    /** Previous-generation database copy created before the switch. */
    val previousDatabasePath: String? = null,
    /** Next-generation database staged for promotion. */
    val nextDatabasePath: String? = null,
    /** Live canonical asset directory. */
    val liveAssetDir: String? = null,
    /** Previous-generation asset directory created before the switch. */
    val previousAssetDir: String? = null,
    /** Next-generation asset directory staged for promotion. */
    val nextAssetDir: String? = null,
    /** Restore staging root (cacheDir/restore-<id>). */
    val stagingDir: String? = null,
)

@kotlinx.serialization.Serializable
internal data class CurrentState(
    val databaseExists: Boolean,
    val databaseSize: Long,
    val assetCount: Int,
    val freeSpace: Long,
)

class BackupRestoreException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * 恢复失败且进程内数据库连接已被关闭（swap 阶段之后失败、文件已回滚）。
 * database 端口是构造注入到全部仓库的，进程内无法热重开——唯一的出路是
 * 让用户完全退出并重新打开应用，因此单独成类型让 UI 给出可操作的提示。
 */
class BackupRestoreDatabaseClosedException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Counts bytes read from the wrapped stream, used to detect zip bombs. */
internal class CountingInputStream(
    delegate: InputStream,
) : java.io.FilterInputStream(delegate) {
    private var byteCount: Long = 0L

    override fun read(): Int {
        val value = super.read()
        if (value != -1) byteCount++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if (read > 0) byteCount += read
        return read
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) byteCount += skipped
        return skipped
    }

    fun count(): Long = byteCount
}

object BackupRepositoryFactory {
    /**
     * [schedulingSettings] / [reviewReminders] 是设置随包走的读口与写口（6C-1）：装配方传
     * 生产用的 DataStore 实例，备份把它们的偏好快照进 `settings.json`，恢复在交换成功后
     * 写回。不传（测试装配）时包内没有设置——`hasSettings=false`，不是"设置是默认值"。
     */
    fun create(
        context: Context,
        database: StudyDatabasePort,
        schedulingSettings: SchedulingSettingsStore? = null,
        reviewReminders: ReviewReminderRepository? = null,
    ): BackupRepository = AndroidBackupRepository(
        context = context,
        database = database,
        schedulingSettings = schedulingSettings,
        reviewReminders = reviewReminders,
    )
}
