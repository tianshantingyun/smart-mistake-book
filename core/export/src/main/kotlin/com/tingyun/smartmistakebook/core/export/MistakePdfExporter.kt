package com.tingyun.smartmistakebook.core.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal const val PREPARED_PDF_FILE_NAME = "mistake.pdf"
internal const val PREPARED_DIGEST_FILE_NAME = "mistake.pdf.sha256"
internal const val PENDING_DIRECTORY_PREFIX = ".pending-"

object MistakePdfExportLimits {
    const val MAX_SINGLE_PAGES = 24
    const val MAX_BATCH_PAGES = 120
    const val MAX_PAGES = MAX_BATCH_PAGES
    const val MAX_SINGLE_RENDERED_LINES = 1_200
    const val MAX_BATCH_RENDERED_LINES = 6_000
    const val MAX_RENDERED_LINES = MAX_BATCH_RENDERED_LINES
    const val MAX_SINGLE_PDF_BYTES = 8L * 1024L * 1024L
    const val MAX_BATCH_PDF_BYTES = 24L * 1024L * 1024L
    const val MAX_PDF_BYTES = MAX_BATCH_PDF_BYTES
    const val MAX_PREVIEW_EDGE_PX = 4_096
    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
    const val MAX_CACHED_ARTIFACTS = 24
    const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
}

enum class MistakePdfExportFailure {
    PAGE_LIMIT_EXCEEDED,
    PDF_SIZE_LIMIT_EXCEEDED,
    EXISTING_FILE_INTEGRITY_CONFLICT,
    GENERATED_PDF_INVALID,
    FILE_PUBLISH_FAILED,
}

class MistakePdfExportException(
    val failure: MistakePdfExportFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

@ConsistentCopyVisibility
data class PreparedMistakePdf internal constructor(
    val file: File,
    val sha256: String,
    val inputSha256: String,
    val pageCount: Int,
) {
    /** Identifies this exact immutable artifact after UI state is recreated. */
    val recoveryToken: PreparedMistakePdfToken
        get() = PreparedMistakePdfToken(
            inputSha256 = inputSha256,
            pdfSha256 = sha256,
            pageCount = pageCount,
        )

    fun verifyIntegrity(): Boolean =
        file.isFile &&
            file.name == PREPARED_PDF_FILE_NAME &&
            file.parentFile?.name == inputSha256 &&
            file.length() in 1..MistakePdfExportLimits.MAX_PDF_BYTES &&
            sha256.matches(SHA_256) &&
            file.parentFile?.listFiles()?.map(File::getName)?.toSet() == PREPARED_PAIR_NAMES &&
            readRecordedDigest(File(file.parentFile, PREPARED_DIGEST_FILE_NAME)) == sha256 &&
            sha256File(file) == sha256

    private companion object {
        val SHA_256 = Regex("[a-f0-9]{64}")
        val PREPARED_PAIR_NAMES = setOf(PREPARED_PDF_FILE_NAME, PREPARED_DIGEST_FILE_NAME)
    }
}

/** A Bundle-safe reference to one verified prepared PDF. */
data class PreparedMistakePdfToken(
    val inputSha256: String,
    val pdfSha256: String,
    val pageCount: Int,
) {
    fun toPersistedValue(): String = "$inputSha256:$pdfSha256:$pageCount"

    internal fun isWellFormed(): Boolean =
        inputSha256.matches(SHA_256) &&
            pdfSha256.matches(SHA_256) &&
            pageCount in 1..MistakePdfExportLimits.MAX_PAGES

    companion object {
        fun fromPersistedValue(value: String?): PreparedMistakePdfToken? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 3) return null
            val token = PreparedMistakePdfToken(
                inputSha256 = parts[0],
                pdfSha256 = parts[1],
                pageCount = parts[2].toIntOrNull() ?: return null,
            )
            return token.takeIf(PreparedMistakePdfToken::isWellFormed)
        }

        private val SHA_256 = Regex("[a-f0-9]{64}")
    }
}

/** Creates one immutable prepared artifact under cache/mistake_pdf_exports. */
class MistakePdfExporter(context: Context) {
    private val exportDirectory = File(context.applicationContext.cacheDir, EXPORT_DIRECTORY)
    private val store = PreparedPdfStore(exportDirectory)

    fun prepare(input: MistakePdfExportInput): PreparedMistakePdf {
        requireExportDirectory(exportDirectory)
        return ExportRootLock.withLock(exportDirectory) {
            store.cleanupTemporaryDirectories()
            val now = System.currentTimeMillis()
            store.cleanupPublishedArtifacts(now, protectedInputSha256 = "")
            store.findExisting(input)?.let { existing ->
                store.markAccessed(existing, now)
                return@withLock existing.copy(pageCount = validatedPageCount(existing.file))
            }

            val temporaryDirectory = store.createTemporaryDirectory()
            try {
                val temporaryPdf = File(temporaryDirectory, PREPARED_PDF_FILE_NAME)
                DeterministicMistakePdfRenderer.render(input, temporaryPdf)
                if (temporaryPdf.length() !in 1..input.maxPdfBytes) {
                    throw MistakePdfExportException(
                        MistakePdfExportFailure.PDF_SIZE_LIMIT_EXCEEDED,
                    )
                }
                val pageCount = validatedPageCount(temporaryPdf)
                store.writeDigest(temporaryDirectory, sha256File(temporaryPdf))
                store.publish(input, temporaryDirectory, pageCount).also { prepared ->
                    store.markAccessed(prepared, now)
                    store.cleanupPublishedArtifacts(now, input.inputSha256)
                }
            } finally {
                store.deleteTemporaryDirectory(temporaryDirectory)
            }
        }
    }

    /**
     * Reopens only the cached PDF named and fingerprinted by [token].
     *
     * This is deliberately fail-closed: a missing, altered, or malformed artifact is unavailable.
     */
    fun reopenVerified(token: PreparedMistakePdfToken): PreparedMistakePdf? {
        if (!token.isWellFormed()) return null
        return try {
            requireExportDirectory(exportDirectory)
            ExportRootLock.withLock(exportDirectory) {
                val prepared = store.findExact(token) ?: return@withLock null
                val pageCount = validatedPageCount(prepared.file)
                prepared.takeIf { pageCount == token.pageCount }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun validatedPageCount(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount.takeIf { it in 1..MistakePdfExportLimits.MAX_PAGES }
                    ?: throw MistakePdfExportException(
                        MistakePdfExportFailure.GENERATED_PDF_INVALID,
                    )
            }
        }
    } catch (known: MistakePdfExportException) {
        throw known
    } catch (failure: Exception) {
        throw MistakePdfExportException(
            MistakePdfExportFailure.GENERATED_PDF_INVALID,
            failure,
        )
    }

    companion object {
        const val EXPORT_DIRECTORY = "mistake_pdf_exports"
    }
}

private class PreparedPdfStore(
    private val directory: File,
) {
    fun cleanupTemporaryDirectories() {
        val children = directory.listFiles() ?: publishFailed()
        children
            .filter { it.name.startsWith(PENDING_DIRECTORY_PREFIX) }
            .forEach(::deleteTemporaryDirectory)
    }

    fun cleanupPublishedArtifacts(nowEpochMillis: Long, protectedInputSha256: String) {
        require(nowEpochMillis >= 0) { "Cache cleanup time must not be negative" }
        val artifacts = publishedArtifacts()
        artifacts
            .filter { nowEpochMillis - it.lastAccessedAtEpochMillis > MistakePdfExportLimits.CACHE_TTL_MILLIS }
            .filterNot { it.inputSha256 == protectedInputSha256 }
            .forEach(::deletePublishedArtifact)

        val retained = publishedArtifacts()
            .sortedWith(
                compareByDescending<PublishedArtifact> { it.inputSha256 == protectedInputSha256 }
                    .thenByDescending { it.lastAccessedAtEpochMillis }
                    .thenBy { it.inputSha256 },
            )
        var retainedCount = retained.size
        var retainedBytes = retained.sumOf(PublishedArtifact::byteSize)
        retained.asReversed().forEach { artifact ->
            if (
                artifact.inputSha256 != protectedInputSha256 &&
                (retainedCount > MistakePdfExportLimits.MAX_CACHED_ARTIFACTS ||
                    retainedBytes > MistakePdfExportLimits.MAX_CACHE_BYTES)
            ) {
                deletePublishedArtifact(artifact)
                retainedCount -= 1
                retainedBytes -= artifact.byteSize
            }
        }
    }

    fun markAccessed(prepared: PreparedMistakePdf, nowEpochMillis: Long) {
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
            if (files.any { !it.isFile }) {
                return@mapNotNull null
            }
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

    fun findExisting(input: MistakePdfExportInput): PreparedMistakePdf? {
        requireExportDirectory(directory)
        val targetDirectory = targetDirectoryFor(input)
        if (!targetDirectory.exists()) return null
        if (!targetDirectory.isDirectory) integrityConflict()
        requireDirectChild(targetDirectory, directory)
        val children = targetDirectory.listFiles() ?: integrityConflict()
        if (children.map(File::getName).toSet() != PREPARED_PAIR_NAMES || children.size != 2) {
            integrityConflict()
        }
        val targetPdf = File(targetDirectory, PREPARED_PDF_FILE_NAME)
        val digestFile = File(targetDirectory, PREPARED_DIGEST_FILE_NAME)
        if (!targetPdf.isFile || !digestFile.isFile) integrityConflict()
        if (targetPdf.length() !in 1..MistakePdfExportLimits.MAX_PDF_BYTES) integrityConflict()
        val recordedDigest = readRecordedDigest(digestFile) ?: integrityConflict()
        val actualDigest = sha256File(targetPdf)
        if (recordedDigest != actualDigest) integrityConflict()
        return PreparedMistakePdf(
            file = targetPdf,
            sha256 = actualDigest,
            inputSha256 = input.inputSha256,
            pageCount = 0,
        )
    }

    fun findExact(token: PreparedMistakePdfToken): PreparedMistakePdf? {
        return try {
            val targetDirectory = File(directory, token.inputSha256)
            if (!targetDirectory.isDirectory) return null
            requireDirectChild(targetDirectory, directory)
            val children = targetDirectory.listFiles() ?: return null
            if (children.map(File::getName).toSet() != PREPARED_PAIR_NAMES || children.size != 2) {
                return null
            }
            val targetPdf = File(targetDirectory, PREPARED_PDF_FILE_NAME)
            val digestFile = File(targetDirectory, PREPARED_DIGEST_FILE_NAME)
            if (!targetPdf.isFile || !digestFile.isFile) return null
            if (targetPdf.length() !in 1..MistakePdfExportLimits.MAX_PDF_BYTES) return null
            val recordedDigest = readRecordedDigest(digestFile) ?: return null
            val actualDigest = sha256File(targetPdf)
            if (recordedDigest != token.pdfSha256 || actualDigest != token.pdfSha256) return null
            PreparedMistakePdf(
                file = targetPdf,
                sha256 = actualDigest,
                inputSha256 = token.inputSha256,
                pageCount = token.pageCount,
            ).takeIf(PreparedMistakePdf::verifyIntegrity)
        } catch (_: MistakePdfExportException) {
            null
        }
    }

    fun writeDigest(temporaryDirectory: File, digest: String) {
        require(INPUT_SHA_256.matches(digest)) { "PDF digest is invalid" }
        requireDirectChild(temporaryDirectory, directory)
        val digestFile = File(temporaryDirectory, PREPARED_DIGEST_FILE_NAME)
        FileOutputStream(digestFile).use { output ->
            output.write("$digest\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            output.fd.sync()
        }
    }

    fun publish(
        input: MistakePdfExportInput,
        temporaryDirectory: File,
        pageCount: Int,
    ): PreparedMistakePdf {
        findExisting(input)?.let { return it.copy(pageCount = pageCount) }
        requireDirectChild(temporaryDirectory, directory)
        val temporaryChildren = temporaryDirectory.listFiles() ?: publishFailed()
        if (
            temporaryChildren.map(File::getName).toSet() != PREPARED_PAIR_NAMES ||
            temporaryChildren.size != 2
        ) {
            publishFailed()
        }
        val targetDirectory = targetDirectoryFor(input)
        if (targetDirectory.exists()) {
            return findExisting(input)?.copy(pageCount = pageCount) ?: publishFailed()
        }
        if (!temporaryDirectory.renameTo(targetDirectory)) {
            return findExisting(input)?.copy(pageCount = pageCount) ?: publishFailed()
        }
        return findExisting(input)?.copy(pageCount = pageCount) ?: publishFailed()
    }

    private fun targetDirectoryFor(input: MistakePdfExportInput): File {
        require(INPUT_SHA_256.matches(input.inputSha256)) { "Input fingerprint is invalid" }
        return File(directory, input.inputSha256)
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
        val PREPARED_PAIR_NAMES = setOf(PREPARED_PDF_FILE_NAME, PREPARED_DIGEST_FILE_NAME)
    }

    private data class PublishedArtifact(
        val inputSha256: String,
        val directory: File,
        val byteSize: Long,
        val lastAccessedAtEpochMillis: Long,
    )
}

private object ExportRootLock {
    @Synchronized
    fun <T> withLock(exportDirectory: File, block: () -> T): T {
        val lockFile = File(exportDirectory.parentFile, ".mistake_pdf_exports.lock")
        return try {
            RandomAccessFile(lockFile, "rw").use { randomAccessFile ->
                randomAccessFile.channel.use { channel ->
                    channel.lock().use { block() }
                }
            }
        } catch (known: MistakePdfExportException) {
            throw known
        } catch (failure: Exception) {
            throw MistakePdfExportException(
                MistakePdfExportFailure.FILE_PUBLISH_FAILED,
                failure,
            )
        }
    }
}


private fun requireExportDirectory(directory: File) {
    if (!directory.exists() && !directory.mkdirs()) {
        throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED)
    }
    if (!directory.isDirectory || directory.name != MistakePdfExporter.EXPORT_DIRECTORY) {
        throw MistakePdfExportException(MistakePdfExportFailure.FILE_PUBLISH_FAILED)
    }
}

internal fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    BufferedInputStream(FileInputStream(file)).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

private fun readRecordedDigest(file: File): String? {
    if (!file.isFile || file.length() !in 65L..66L) return null
    val value = file.readText(StandardCharsets.US_ASCII).trim()
    return value.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
}
