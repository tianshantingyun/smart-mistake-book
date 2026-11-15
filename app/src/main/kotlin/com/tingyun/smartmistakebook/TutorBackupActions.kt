package com.tingyun.smartmistakebook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tingyun.smartmistakebook.core.domain.BackupContentCategory
import com.tingyun.smartmistakebook.core.domain.BackupOptions
import com.tingyun.smartmistakebook.core.domain.BackupSelection
import com.tingyun.smartmistakebook.core.domain.BackupValidation
import com.tingyun.smartmistakebook.core.domain.RestoreSettingsOutcome
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand
import com.tingyun.smartmistakebook.core.data.tutor.TutorRestoreJournal
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.TutorBackupReference
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import java.io.File
import java.io.FilterOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json

/** Called only after the document picker has granted access or the app has created a backup. */
fun SmartMistakeBookApplication.registerTutorBackupReference(uri: String): String {
    val parsed = Uri.parse(uri)
    require(parsed.scheme in setOf("content", "file"))
    val ref = "backup-" + MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val preferences = getSharedPreferences("tutor-authorized-backups", Context.MODE_PRIVATE)
    check(preferences.edit().putString(ref, uri).commit())
    return ref
}

internal fun SmartMistakeBookApplication.tutorBackupReferences(): List<TutorBackupReference> =
    getSharedPreferences("tutor-authorized-backups", Context.MODE_PRIVATE).all.mapNotNull { (ref, value) ->
        val uri = (value as? String)?.let(Uri::parse) ?: return@mapNotNull null
        try {
            if (uri.scheme == "file") {
                val file = File(requireNotNull(uri.path))
                if (!file.isFile) return@mapNotNull null
                TutorBackupReference(ref, file.name.take(256), file.lastModified(), file.length())
            } else {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val name = cursor.getString(0).orEmpty().ifBlank { "已授权备份" }.take(256)
                    TutorBackupReference(ref, name, 0L, cursor.getLong(1).coerceAtLeast(0))
                }
            }
        } catch (_: Exception) { null }
    }.sortedByDescending { it.createdAtEpochMillis }

internal fun SmartMistakeBookApplication.tutorBackupUri(ref: String): Uri? =
    getSharedPreferences("tutor-authorized-backups", Context.MODE_PRIVATE).getString(ref, null)?.let(Uri::parse)

internal suspend fun SmartMistakeBookApplication.createTutorBackup(effectKey: String, mask: Int): TutorLobbyLocalActionOutcome {
    require(mask in BackupSelection.ALLOWED_MASKS)
    val directory = File(noBackupFilesDir, "tutor-backups").apply { mkdirs() }
    val destination = File(directory, "backup-$effectKey.zip")
    if (!destination.isFile) {
        val options = BackupOptions(BackupSelection.of(BackupContentCategory.entries.filter { it.bit and mask != 0 }.toSet()))
        val atomic = AtomicFile(destination)
        val stream = atomic.startWrite()
        try {
            backupRepository.create(object : FilterOutputStream(stream) {
                override fun close() = flush()
            }, options)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }
    val validation = destination.inputStream().use { backupRepository.validate(it) }
    if (validation !is BackupValidation.Valid) return tutorApplicationReceipt(false, "生成的备份未通过完整性检查。")
    val ref = registerTutorBackupReference(Uri.fromFile(destination).toString())
    return tutorApplicationReceipt(true, "备份文件已生成并验证，可用此备份身份恢复。", ref)
}

internal fun SmartMistakeBookApplication.tutorRestoreJournalFile(effectKey: String): File {
    require(effectKey.matches(Regex("[a-f0-9]{64}")))
    return File(noBackupFilesDir, "tutor-actions/restore-$effectKey.json")
}

internal fun writeTutorActionFile(file: File, text: String) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val output = atomic.startWrite()
    try {
        output.write(text.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(output)
    } catch (failure: Throwable) {
        atomic.failWrite(output)
        throw failure
    }
}

internal suspend fun SmartMistakeBookApplication.enqueueTutorBackupRestore(
    command: TutorLocalActionCommand, effectKey: String,
): TutorLobbyLocalActionOutcome {
    val ref = command.request.parameters["backupRef"]
        ?: return tutorApplicationReceipt(false, "没有指定备份身份。")
    if (ref !in command.allowedBackupRefs || tutorBackupUri(ref) == null) {
        return tutorApplicationReceipt(false, "备份身份没有本机授权，未恢复。")
    }
    val file = tutorRestoreJournalFile(effectKey)
    val journal = if (file.isFile) Json.decodeFromString(TutorRestoreJournal.serializer(), file.readText())
        else TutorRestoreJournal(effectKey, ref, command.logicalOperationId).also {
            writeTutorActionFile(file, Json.encodeToString(TutorRestoreJournal.serializer(), it))
        }
    if (journal.status == "QUEUED") {
        runInterruptible(Dispatchers.IO) {
            WorkManager.getInstance(this@enqueueTutorBackupRestore).enqueueUniqueWork(
                "tutor-restore-$effectKey", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TutorBackupRestoreWorker>()
                    .setInputData(Data.Builder().putString("effectKey", effectKey).build())
                    .setInitialDelay(5, TimeUnit.SECONDS).build(),
            ).result.get()
        }
    }
    return tutorApplicationReceipt(journal.status !in setOf("FAILED", "CANCELLED"), journal.detail,
        "restore-$effectKey", if (journal.status == "SUCCEEDED") "RESTART_REQUIRED" else journal.status)
}

class TutorBackupRestoreWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SmartMistakeBookApplication ?: return Result.failure()
        val effectKey = inputData.getString("effectKey") ?: return Result.failure()
        val file = app.tutorRestoreJournalFile(effectKey)
        if (!file.isFile) return Result.failure()
        val journal = Json.decodeFromString(TutorRestoreJournal.serializer(), file.readText())
        fun settle(status: String, detail: String) {
            writeTutorActionFile(file, Json.encodeToString(TutorRestoreJournal.serializer(), journal.copy(status = status, detail = detail)))
        }
        if (journal.status in setOf("SUCCEEDED", "FAILED", "CANCELLED")) return Result.success()
        if (journal.status == "RESTORING") {
            settle("FAILED", "恢复执行曾被中断，请查看启动恢复结果后重新发起。")
            return Result.failure()
        }
        if (!app.startupState.value.allowsMistakeExportWorker()) return Result.retry()
        val modelStatus = app.tutorModelTaskStatus(journal.modelTaskRequestId) ?: run {
            settle("CANCELLED", "发起恢复的对话任务已不存在，这次没有恢复。")
            return Result.success()
        }
        if (modelStatus == ModelTaskStatus.RETRYABLE_FAILURE) {
            settle("CANCELLED", "发起恢复的对话回合失败，这次没有恢复。")
            return Result.success()
        }
        if (!modelStatus.isTerminal) return Result.retry()
        if (modelStatus != ModelTaskStatus.SUCCEEDED) {
            settle("CANCELLED", "发起恢复的对话回合未完成，这次没有恢复备份。")
            return Result.success()
        }
        val uri = app.tutorBackupUri(journal.backupRef) ?: run {
            settle("FAILED", "备份授权已失效，这次没有恢复。")
            return Result.failure()
        }
        try {
            val validation = requireNotNull(app.contentResolver.openInputStream(uri)).use { app.backupRepository.validate(it) }
            if (validation !is BackupValidation.Valid) {
                settle("FAILED", "备份完整性检查失败，这次没有恢复。")
                return Result.failure()
            }
            settle("RESTORING", "正在恢复已授权备份。")
            val receipt = requireNotNull(app.contentResolver.openInputStream(uri)).use { app.backupRepository.restore(it) }
            val message = if (receipt.settings == RestoreSettingsOutcome.WRITE_FAILED)
                "数据库已恢复，部分设置写入失败，需要重启并检查设置。" else "备份已恢复，需要重启应用。"
            settle("SUCCEEDED", message)
            app.onTutorBackupRestored(message)
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            settle("FAILED", "备份恢复未完成，请查看应用恢复状态。")
            return Result.failure()
        }
    }
}
