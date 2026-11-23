package com.tingyun.smartmistakebook

import android.content.Context
import com.tingyun.smartmistakebook.core.domain.TutorToolResultChunk
import com.tingyun.smartmistakebook.core.domain.TutorToolResultStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App-private, deterministic tool-result artifact store.
 *
 * Full results are kept as files; the model only receives a preview + id and pages through
 * `READ_RESULT`. The store is content-addressed by `(scope, tool, payload)`, so a replay or
 * retry of the same tool call reuses the same artifact instead of growing the directory.
 * Values are bounded and pruned by age/count; a missing or malformed file is treated as absent.
 */
internal class SharedPreferencesTutorToolResultStore(context: Context) : TutorToolResultStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "tutor-tool-results")

    override suspend fun write(scopeKey: String, tool: String, payload: String): String =
        withContext(Dispatchers.IO) {
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) {
                "A tool result exceeds the ${MAX_RESULT_BYTES / 1024} KiB artifact budget"
            }
            val resultId = resultId(scopeKey, tool, payload)
            directory.mkdirs()
            val target = File(directory, "$resultId.json")
            if (target.isFile) {
                pruneLocked(System.currentTimeMillis())
                return@withContext resultId
            }
            val temp = File.createTempFile(resultId, ".tmp", directory)
            temp.writeText(payload, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.delete()
                if (!target.isFile) error("Tool result could not be committed")
            }
            pruneLocked(System.currentTimeMillis())
            resultId
        }

    override suspend fun read(resultId: String, offset: Int, limit: Int): TutorToolResultChunk? =
        withContext(Dispatchers.IO) {
            if (!resultId.matches(RESULT_ID)) return@withContext null
            if (offset < 0 || limit !in 1..MAX_CHUNK_CHARS) return@withContext null
            val file = File(directory, "$resultId.json")
            if (!file.isFile) return@withContext null
            val payload = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                ?: return@withContext null
            if (offset > payload.length) return@withContext null
            val end = minOf(payload.length, offset + limit)
            TutorToolResultChunk(
                resultId = resultId,
                text = payload.substring(offset, end),
                offset = offset,
                totalChars = payload.length,
            )
        }

    override suspend fun prune(nowEpochMillis: Long) = withContext(Dispatchers.IO) {
        pruneLocked(nowEpochMillis)
    }

    private fun pruneLocked(nowEpochMillis: Long) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?: return
        files.filter { nowEpochMillis - it.lastModified() > MAX_AGE_MILLIS }
            .forEach(File::delete)
        val remaining = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?: return
        remaining.sortedByDescending(File::lastModified)
            .drop(MAX_RESULTS)
            .forEach(File::delete)
    }

    private fun resultId(scopeKey: String, tool: String, payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$scopeKey\n$tool\n$payload".toByteArray(Charsets.UTF_8))
        return "r-" + digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val MAX_RESULT_BYTES = 256 * 1024
        const val MAX_RESULTS = 64
        const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX_CHUNK_CHARS = 3_500
        val RESULT_ID = Regex("r-[a-f0-9]{64}")
    }
}
