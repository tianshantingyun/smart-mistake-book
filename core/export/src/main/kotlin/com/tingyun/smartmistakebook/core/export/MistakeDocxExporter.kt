package com.tingyun.smartmistakebook.core.export

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

internal const val PREPARED_DOCX_FILE_NAME = "mistake.docx"
internal const val PREPARED_DOCX_DIGEST_FILE_NAME = "mistake.docx.sha256"

/**
 * Word 产物在导出记录 `pageCount` 段的占位值。
 *
 * 它消灭的失败：记录表是 L7 的既有表单（`MistakeExportRecord` 要求成功行携带
 * "artifact identity"），而 6B-1 明确**零 schema**——不加格式列。Word 没有"页"这一交付
 * 概念，而 [PreparedMistakePdfToken] 的三段持久值 `input:artifact:pages` 需要一个正整数；
 * 这里固定写 [MISTAKE_DOCX_RECORD_PAGE_PLACEHOLDER]，成果入口对 Word 记录**不显示页数**
 * （只显示"Word 文档"），页数永远不作为 Word 的交付事实出现。
 */
const val MISTAKE_DOCX_RECORD_PAGE_PLACEHOLDER = 1

/**
 * One prepared immutable Word artifact（与 [PreparedMistakePdf] 同构：同一份完整性口径，
 * 但文件名/扩展名/描述都落在 Word 上）。
 */
@ConsistentCopyVisibility
data class PreparedMistakeDocx internal constructor(
    val file: File,
    val sha256: String,
    val inputSha256: String,
) {
    fun verifyIntegrity(): Boolean =
        file.isFile &&
            file.name == PREPARED_DOCX_FILE_NAME &&
            file.parentFile?.name == inputSha256 &&
            file.length() in 1..MistakePdfExportLimits.MAX_PDF_BYTES &&
            sha256.matches(SHA_256) &&
            file.parentFile?.listFiles()?.map(File::getName)?.toSet() == PREPARED_PAIR_NAMES &&
            readRecordedDigest(File(file.parentFile, PREPARED_DOCX_DIGEST_FILE_NAME)) == sha256 &&
            sha256File(file) == sha256

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
        val PREPARED_PAIR_NAMES = setOf(PREPARED_DOCX_FILE_NAME, PREPARED_DOCX_DIGEST_FILE_NAME)
    }
}

/**
 * Creates one immutable prepared Word artifact under cache/mistake_docx_exports.
 *
 * **为什么与 PDF 的导出器分目录、各自实现完整性口径**：两种格式的产物**同键（inputSha256）
 * 不同字节**，同一键下 PDF 与 docx 必须共存；共用目录会让 PDF 的既有"目录里恰好两个文件"
 * 的完整性判据（`PreparedPdfStore.findExisting`）在两种格式都渲染过之后直接
 * `EXISTING_FILE_INTEGRITY_CONFLICT`——那不是"多一个缓存"，是把 PDF 导出打成永久失败。
 * 因此这里使用独立目录 + 同构的"临时目录 → 摘要 → 发布 → 按 TTL/上限清理"链，
 * 锁与清理口径复用 core:export 既有实现（[ExportRootLock] / [MistakePdfExportLimits]）。
 */
class MistakeDocxExporter(context: Context) {
    private val exportDirectory = File(context.applicationContext.cacheDir, EXPORT_DIRECTORY)
    private val store = DocxArtifactStore(exportDirectory)

    fun prepare(input: MistakePdfExportInput): PreparedMistakeDocx {
        requireDocxExportDirectory(exportDirectory)
        return ExportRootLock.withLock(exportDirectory) {
            store.cleanupTemporaryDirectories()
            val now = System.currentTimeMillis()
            store.cleanupPublishedArtifacts(now)
            store.findExisting(input.inputSha256)?.let { existing ->
                store.markAccessed(existing, now)
                return@withLock existing
            }

            val temporaryDirectory = store.createTemporaryDirectory()
            try {
                val temporaryDocx = File(temporaryDirectory, PREPARED_DOCX_FILE_NAME)
                DocxMistakeRenderer.render(input, temporaryDocx)
                if (temporaryDocx.length() !in 1..input.maxPdfBytes) {
                    throw MistakePdfExportException(
                        MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED,
                    )
                }
                store.writeDigest(temporaryDirectory, sha256File(temporaryDocx))
                store.publish(input.inputSha256, temporaryDirectory).also { prepared ->
                    store.markAccessed(prepared, now)
                    store.cleanupPublishedArtifacts(now)
                }
            } finally {
                store.deleteTemporaryDirectory(temporaryDirectory)
            }
        }
    }

    /**
     * Reopens only the cached Word artifact named and fingerprinted by [token].
     *
     * 与 PDF 侧同一条 fail-closed 口径：缺失、被改、形状不对都返回 null。Word 没有页数，
     * token 的 `pageCount` 段在这里不参与核对（它是记录行的占位值，见
     * [MISTAKE_DOCX_RECORD_PAGE_PLACEHOLDER]）；产物身份由 `inputSha256 + artifact sha256` 定。
     */
    fun reopenVerified(token: PreparedMistakePdfToken): PreparedMistakeDocx? {
        if (!token.isWellFormed()) return null
        return try {
            requireDocxExportDirectory(exportDirectory)
            ExportRootLock.withLock(exportDirectory) {
                store.findExact(token) ?: return@withLock null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val EXPORT_DIRECTORY = "mistake_docx_exports"
    }
}

private fun requireDocxExportDirectory(directory: File) {
    if (!directory.exists() && !directory.mkdirs()) {
        throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED)
    }
    if (!directory.isDirectory || directory.name != MistakeDocxExporter.EXPORT_DIRECTORY) {
        throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED)
    }
}

private class DocxArtifactStore(
    private val directory: File,
) {
    fun cleanupTemporaryDirectories() {
        val children = directory.listFiles() ?: publishFailed()
        children
            .filter { it.name.startsWith(PENDING_DIRECTORY_PREFIX) }
            .forEach(::deleteTemporaryDirectory)
    }

    fun cleanupPublishedArtifacts(nowEpochMillis: Long) {
        require(nowEpochMillis >= 0) { "Cache cleanup time must not be negative" }
        publishedArtifacts()
            .filter { nowEpochMillis - it.lastAccessedAtEpochMillis > MistakePdfExportLimits.CACHE_TTL_MILLIS }
            .forEach(::deletePublishedArtifact)

        val retained = publishedArtifacts()
            .sortedWith(
                compareByDescending<PublishedArtifact> { it.lastAccessedAtEpochMillis }
                    .thenBy { it.inputSha256 },
            )
        var retainedCount = retained.size
        var retainedBytes = retained.sumOf(PublishedArtifact::byteSize)
        retained.asReversed().forEach { artifact ->
            if (
                retainedCount > MistakePdfExportLimits.MAX_CACHED_ARTIFACTS ||
                retainedBytes > MistakePdfExportLimits.MAX_CACHE_BYTES
            ) {
                deletePublishedArtifact(artifact)
                retainedCount -= 1
                retainedBytes -= artifact.byteSize
            }
        }
    }

    fun markAccessed(prepared: PreparedMistakeDocx, nowEpochMillis: Long) {
        val artifactDirectory = prepared.file.parentFile ?: integrityConflict()
        requireDirectChild(artifactDirectory, directory)
        if (!artifactDirectory.setLastModified(nowEpochMillis)) publishFailed()
    }

    fun createTemporaryDirectory(): File {
        val temporary = File(directory, "$PENDING_DIRECTORY_PREFIX${UUID.randomUUID()}")
        if (!temporary.mkdir()) publishFailed()
        requireDirectChild(temporary, directory)
        return temporary
    }

    fun deleteTemporaryDirectory(temporary: File) {
        if (!temporary.exists()) return
        require(temporary.name.startsWith(PENDING_DIRECTORY_PREFIX)) {
            "Refusing to remove a non-temporary export directory"
        }
        requireDirectChild(temporary, directory)
        if (temporary.isDirectory) {
            val children = temporary.listFiles() ?: publishFailed()
            children.forEach { child ->
                requireDirectChild(child, temporary)
                if (!child.isFile || !child.delete()) publishFailed()
            }
        }
        if (!temporary.delete()) publishFailed()
    }

    fun writeDigest(temporaryDirectory: File, digest: String) {
        require(INPUT_SHA_256.matches(digest)) { "Word artifact digest is invalid" }
        requireDirectChild(temporaryDirectory, directory)
        val digestFile = File(temporaryDirectory, PREPARED_DOCX_DIGEST_FILE_NAME)
        FileOutputStream(digestFile).use { output ->
            output.write("$digest\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            output.fd.sync()
        }
    }

    fun publish(inputSha256: String, temporaryDirectory: File): PreparedMistakeDocx {
        findExisting(inputSha256)?.let { return it }
        requireDirectChild(temporaryDirectory, directory)
        val temporaryChildren = temporaryDirectory.listFiles() ?: publishFailed()
        if (
            temporaryChildren.map(File::getName).toSet() != PREPARED_PAIR_NAMES ||
            temporaryChildren.size != 2
        ) {
            publishFailed()
        }
        val targetDirectory = targetDirectoryFor(inputSha256)
        if (targetDirectory.exists()) {
            return findExisting(inputSha256) ?: publishFailed()
        }
        if (!temporaryDirectory.renameTo(targetDirectory)) {
            return findExisting(inputSha256) ?: publishFailed()
        }
        return findExisting(inputSha256) ?: publishFailed()
    }

    fun findExisting(inputSha256: String): PreparedMistakeDocx? {
        requireDocxExportDirectory(directory)
        val targetDirectory = targetDirectoryFor(inputSha256)
        if (!targetDirectory.exists()) return null
        if (!targetDirectory.isDirectory) integrityConflict()
        requireDirectChild(targetDirectory, directory)
        return readVerifiedArtifact(targetDirectory, inputSha256)
    }

    fun findExact(token: PreparedMistakePdfToken): PreparedMistakeDocx? {
        return try {
            val targetDirectory = File(directory, token.inputSha256)
            if (!targetDirectory.isDirectory) return null
            requireDirectChild(targetDirectory, directory)
            readVerifiedArtifact(targetDirectory, token.inputSha256)
                ?.takeIf { prepared -> prepared.sha256 == token.pdfSha256 }
        } catch (_: MistakePdfExportException) {
            null
        }
    }

    private fun readVerifiedArtifact(
        targetDirectory: File,
        inputSha256: String,
    ): PreparedMistakeDocx? {
        val children = targetDirectory.listFiles() ?: integrityConflict()
        if (children.map(File::getName).toSet() != PREPARED_PAIR_NAMES || children.size != 2) {
            integrityConflict()
        }
        val targetDocx = File(targetDirectory, PREPARED_DOCX_FILE_NAME)
        val digestFile = File(targetDirectory, PREPARED_DOCX_DIGEST_FILE_NAME)
        if (!targetDocx.isFile || !digestFile.isFile) integrityConflict()
        if (targetDocx.length() !in 1..MistakePdfExportLimits.MAX_PDF_BYTES) integrityConflict()
        val recordedDigest = readRecordedDigest(digestFile) ?: integrityConflict()
        val actualDigest = sha256File(targetDocx)
        if (recordedDigest != actualDigest) integrityConflict()
        return PreparedMistakeDocx(
            file = targetDocx,
            sha256 = actualDigest,
            inputSha256 = inputSha256,
        )
    }

    private fun publishedArtifacts(): List<PublishedArtifact> {
        val children = directory.listFiles() ?: publishFailed()
        return children.mapNotNull { child ->
            if (child.name.startsWith(PENDING_DIRECTORY_PREFIX)) return@mapNotNull null
            if (!child.isDirectory || !INPUT_SHA_256.matches(child.name)) return@mapNotNull null
            requireDirectChild(child, directory)
            val files = child.listFiles() ?: return@mapNotNull null
            if (files.size != 2 || files.map(File::getName).toSet() != PREPARED_PAIR_NAMES) {
                return@mapNotNull null
            }
            if (files.any { !it.isFile }) return@mapNotNull null
            files.forEach { requireDirectChild(it, child) }
            PublishedArtifact(
                inputSha256 = child.name,
                directory = child,
                byteSize = files.sumOf(File::length),
                lastAccessedAtEpochMillis = child.lastModified(),
            )
        }
    }

    private fun deletePublishedArtifact(artifact: PublishedArtifact) {
        require(INPUT_SHA_256.matches(artifact.inputSha256)) {
            "Refusing to remove an invalid export artifact"
        }
        requireDirectChild(artifact.directory, directory)
        val children = artifact.directory.listFiles() ?: publishFailed()
        if (children.size != 2 || children.map(File::getName).toSet() != PREPARED_PAIR_NAMES) {
            publishFailed()
        }
        children.forEach { child ->
            requireDirectChild(child, artifact.directory)
            if (!child.isFile || !child.delete()) publishFailed()
        }
        if (!artifact.directory.delete()) publishFailed()
    }

    private fun targetDirectoryFor(inputSha256: String): File {
        require(INPUT_SHA_256.matches(inputSha256)) { "Input fingerprint is invalid" }
        return File(directory, inputSha256)
    }

    private fun requireDirectChild(child: File, expectedParent: File) {
        val canonicalChild = try {
            child.canonicalFile
        } catch (failure: Exception) {
            throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED, failure)
        }
        val canonicalParent = try {
            expectedParent.canonicalFile
        } catch (failure: Exception) {
            throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED, failure)
        }
        if (canonicalChild.parentFile != canonicalParent) publishFailed()
    }

    private fun integrityConflict(): Nothing = throw MistakePdfExportException(
        MistakePdfExportFailure.EXISTING_FILE_INTEGRITY_CONFLICT,
    )

    private fun publishFailed(): Nothing = throw MistakePdfExportException(
        MistakePdfExportFailure.FILE_PUBLISH_FAILED,
    )

    private companion object {
        val INPUT_SHA_256 = Regex("[a-f0-9]{64}")
        val PREPARED_PAIR_NAMES = setOf(PREPARED_DOCX_FILE_NAME, PREPARED_DOCX_DIGEST_FILE_NAME)
    }

    private data class PublishedArtifact(
        val inputSha256: String,
        val directory: File,
        val byteSize: Long,
        val lastAccessedAtEpochMillis: Long,
    )
}
