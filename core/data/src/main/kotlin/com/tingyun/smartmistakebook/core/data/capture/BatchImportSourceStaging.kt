package com.tingyun.smartmistakebook.core.data.capture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal const val BATCH_IMPORT_STAGING_DIRECTORY = "batch_import_staging"
private const val BATCH_IMPORT_FILE_PROVIDER_ROOT = "batch_import_staging"
private const val ORPHAN_GRACE_MILLIS = 60L * 60L * 1_000L
private const val MAX_ORPHAN_SESSIONS_PER_RECONCILIATION = 128
private val SESSION_ID = Regex("[a-f0-9-]{36}")
private val PAGE_FILE_NAME = Regex("page-[0-9]{2}\\.source")

internal fun batchImportProviderAuthority(context: Context): String =
    "${context.packageName}.batch-import.fileprovider"

internal data class StagedBatchImportSources(
    val sourceUris: List<String>,
)

internal interface BatchImportSourceStaging {
    fun stage(sourceUris: List<String>): StagedBatchImportSources

    fun stagePdf(sourceUri: String): StagedBatchImportSources

    fun delete(sourceUri: String): Boolean

    fun reconcileOrphans(referencedSourceUris: Set<String>, nowEpochMillis: Long)

    /**
     * True while staging holds a session that no batch job claims — the on-disk trace of an
     * import killed before its durable job was written (KD-42), when the intake list has
     * nothing at all to show. This is the read side of [reconcileOrphans], so the two agree on
     * what "unreclaimed" means: a session whose uri is not among [referencedSourceUris].
     *
     * Interaction with the 60-minute orphan grace period, stated once and relied upon by the
     * intake notice: [reconcileOrphans] only deletes a session on a *later* pass, once it is
     * past [ORPHAN_GRACE_MILLIS] — so a session that is unreclaimed but younger than the grace
     * period still answers true here. That is deliberate: the interruption is known the moment
     * nothing claims the session, and the first hour is exactly when re-importing is still the
     * student's next move. The answer turns false when a job claims the session's pages (a
     * re-import, or the interrupted import resuming) or when reconciliation deletes it. It never
     * depends on a clock reading of its own, and it never creates anything: a missing staging
     * directory answers false.
     *
     * The caller supplies the referenced uris because this layer knows only the filesystem; a
     * session claimed by a live, unfinished job is not an interruption, and reporting it as one
     * would paint the notice over every healthy import whose pages still retain their source.
     *
     * Known boundary: this directory is shared with the lobby image intake, which stages a
     * gallery image through the very same [stage] call and never deletes it — so such a session
     * also answers true until reconciliation reclaims it. A true answer is therefore "staging
     * holds bytes nothing claims", not proof that a batch import was interrupted; the wording it
     * feeds ("上次导入被中断，可重导") stays true-to-action in that case, because re-importing is
     * still what the student can do. Distinguishing the producers needs a record of the
     * attempt, which is the schema-bearing fix KD-42 lists as its fuller direction.
     */
    fun hasOrphanedSessions(referencedSourceUris: Set<String>): Boolean

    fun delete(sources: StagedBatchImportSources) {
        sources.sourceUris.forEach(::delete)
    }
}

internal class AndroidBatchImportSourceStaging(
    context: Context,
    private val pdfRenderer: PdfBatchImportPageRenderer = AndroidPdfBatchImportPageRenderer(
        context.applicationContext.contentResolver,
    ),
) : BatchImportSourceStaging {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val authority = batchImportProviderAuthority(appContext)
    private val stagingRoot = File(appContext.filesDir, BATCH_IMPORT_STAGING_DIRECTORY)
        .canonicalFile

    init {
        check(stagingRoot.parentFile == appContext.filesDir.canonicalFile)
    }

    override fun stage(sourceUris: List<String>): StagedBatchImportSources {
        require(sourceUris.isNotEmpty())
        require(sourceUris.all(String::isNotBlank))
        ensureDirectory(stagingRoot)
        val session = File(stagingRoot, UUID.randomUUID().toString()).canonicalFile
        check(session.parentFile == stagingRoot)
        ensureDirectory(session)
        val stagedUris = ArrayList<String>(sourceUris.size)
        var batchBytes = 0L
        try {
            sourceUris.forEachIndexed { index, sourceUri ->
                val source = Uri.parse(sourceUri)
                require(source.scheme == ContentResolver.SCHEME_CONTENT) {
                    "Batch import only accepts local content URIs"
                }
                val destination = File(session, "page-${index.toString().padStart(2, '0')}.source")
                    .canonicalFile
                check(destination.parentFile == session)
                val temporary = File.createTempFile("copy-", ".partial", session).canonicalFile
                check(temporary.parentFile == session)
                val copied = try {
                    val input = resolver.openInputStream(source)
                        ?: throw IOException("Batch import source could not be opened")
                    input.use { sourceStream ->
                        FileOutputStream(temporary).use { destinationStream ->
                            copyBoundedSource(
                                input = sourceStream,
                                output = destinationStream,
                                sourceLimit = MAX_CANONICAL_SOURCE_INPUT_BYTES,
                                batchLimit = MAX_BATCH_IMPORT_STAGED_BYTES - batchBytes,
                            )
                        }
                    }
                } catch (failure: Exception) {
                    temporary.delete()
                    throw failure
                }
                if (copied == 0L) {
                    temporary.delete()
                    throw IOException("Batch import source was empty")
                }
                check(temporary.renameTo(destination)) {
                    "Batch import source could not be committed to private staging"
                }
                batchBytes += copied
                stagedUris += FileProvider.getUriForFile(
                    appContext,
                    authority,
                    destination,
                ).toString()
            }
        } catch (failure: Exception) {
            deleteSession(session)
            throw failure
        }
        return StagedBatchImportSources(stagedUris)
    }

    override fun stagePdf(sourceUri: String): StagedBatchImportSources {
        require(sourceUri.isNotBlank())
        val source = Uri.parse(sourceUri)
        require(source.scheme == ContentResolver.SCHEME_CONTENT) {
            "PDF import only accepts a local content URI"
        }
        ensureDirectory(stagingRoot)
        val session = File(stagingRoot, UUID.randomUUID().toString()).canonicalFile
        check(session.parentFile == stagingRoot)
        ensureDirectory(session)
        try {
            val stagedUris = pdfRenderer.render(source, session).map { renderedPage ->
                val ownedPage = renderedPage.canonicalFile
                check(
                    ownedPage.parentFile == session &&
                        ownedPage.isFile &&
                        PAGE_FILE_NAME.matches(ownedPage.name),
                ) { "PDF renderer returned an unsafe page" }
                FileProvider.getUriForFile(appContext, authority, ownedPage).toString()
            }
            return StagedBatchImportSources(stagedUris)
        } catch (failure: Exception) {
            deleteSession(session)
            throw failure
        }
    }

    override fun delete(sourceUri: String): Boolean {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return false
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != authority) return false
        val segments = uri.pathSegments
        if (
            segments.size != 3 ||
            segments[0] != BATCH_IMPORT_FILE_PROVIDER_ROOT ||
            !SESSION_ID.matches(segments[1]) ||
            !PAGE_FILE_NAME.matches(segments[2])
        ) {
            return false
        }
        val session = File(stagingRoot, segments[1]).canonicalFile
        if (session.parentFile != stagingRoot) return false
        val source = File(session, segments[2]).canonicalFile
        if (source.parentFile != session) return false
        val deleted = !source.exists() || source.isFile && source.delete()
        if (deleted && session.isDirectory && session.listFiles().orEmpty().isEmpty()) {
            session.delete()
        }
        return deleted
    }

    override fun hasOrphanedSessions(referencedSourceUris: Set<String>): Boolean =
        orphanedStagingSessions(stagingRoot, referencedSessionIds(referencedSourceUris)).isNotEmpty()

    override fun reconcileOrphans(
        referencedSourceUris: Set<String>,
        nowEpochMillis: Long,
    ) {
        require(nowEpochMillis >= 0)
        if (!stagingRoot.isDirectory) return
        reclaimableStagingSessions(
            stagingRoot = stagingRoot,
            referencedSessionIds = referencedSessionIds(referencedSourceUris),
            nowEpochMillis = nowEpochMillis,
        ).forEach(::deleteSession)
    }

    // `mapNotNullTo` 是「目标集合 + transform」两个参数一起给；写成 `mapNotNullTo(set)(::f)`
    // 是柯里化写法，Kotlin 不支持（门禁已在 :211 报 Cannot infer type / No value passed for
    // parameter 'transform'）。目标集合的元素类型写明，避免它在「返回类型 + lambda」两边
    // 来回推。
    private fun referencedSessionIds(sourceUris: Set<String>): Set<String> =
        sourceUris.mapNotNullTo(mutableSetOf<String>()) { sourceUri -> ownedSessionId(sourceUri) }

    private fun ensureDirectory(directory: File) {
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
            "Batch import staging directory could not be created"
        }
    }

    private fun ownedSessionId(sourceUri: String): String? {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return null
        val segments = uri.pathSegments
        return segments.getOrNull(1)?.takeIf { sessionId ->
            uri.scheme == ContentResolver.SCHEME_CONTENT &&
                uri.authority == authority &&
                segments.size == 3 &&
                segments[0] == BATCH_IMPORT_FILE_PROVIDER_ROOT &&
                SESSION_ID.matches(sessionId) &&
                PAGE_FILE_NAME.matches(segments[2])
        }
    }

    private fun deleteSession(session: File) {
        if (session.parentFile != stagingRoot || !session.isDirectory) return
        session.listFiles().orEmpty().forEach { child ->
            val owned = child.canonicalFile
            if (owned.parentFile == session && owned.isFile) owned.delete()
        }
        if (session.listFiles().orEmpty().isEmpty()) session.delete()
    }
}

/**
 * The sessions under [stagingRoot] that no batch job claims, oldest first.
 *
 * This is the visibility rule behind KD-42's "上次导入被中断，可重导": an import that dies while
 * staging leaves a session here and nothing else — no job row, no failure entry — so this
 * directory listing is the only honest evidence the student started something. A session that a
 * live job references is *not* an interruption, which is why the referenced ids are an input.
 *
 * A plain function over a directory on purpose: the Android implementation needs a Context (and
 * a FileProvider authority) to stage and to parse uris, so the rule itself is pinned by a JVM
 * unit test rather than left to the instrumented run.
 */
internal fun orphanedStagingSessions(
    stagingRoot: File,
    referencedSessionIds: Set<String>,
): List<File> = stagingRoot.listFiles().orEmpty().asSequence()
    .filter { session ->
        session.isDirectory &&
            SESSION_ID.matches(session.name) &&
            session.name !in referencedSessionIds
    }
    .sortedBy(File::lastModified)
    .toList()

/**
 * The subset of [orphanedStagingSessions] that [AndroidBatchImportSourceStaging.reconcileOrphans]
 * deletes: past [ORPHAN_GRACE_MILLIS], within the per-pass budget, and untouched since before the
 * cutoff. A session younger than the grace period is reclaimable by neither this function nor the
 * reconciliation — it stays orphaned (and so keeps the intake notice visible) until the next pass
 * that runs after the grace has elapsed.
 *
 * The `1..cutoff` lower bound is the pre-existing guard against a filesystem reporting epoch 0;
 * [nowEpochMillis] before the grace period yields a cutoff of 0, which reclaims nothing.
 */
internal fun reclaimableStagingSessions(
    stagingRoot: File,
    referencedSessionIds: Set<String>,
    nowEpochMillis: Long,
): List<File> {
    require(nowEpochMillis >= 0)
    val cutoff = if (nowEpochMillis > ORPHAN_GRACE_MILLIS) {
        nowEpochMillis - ORPHAN_GRACE_MILLIS
    } else {
        0L
    }
    return orphanedStagingSessions(stagingRoot, referencedSessionIds)
        .take(MAX_ORPHAN_SESSIONS_PER_RECONCILIATION)
        .filter { session -> session.lastModified() in 1..cutoff }
}
