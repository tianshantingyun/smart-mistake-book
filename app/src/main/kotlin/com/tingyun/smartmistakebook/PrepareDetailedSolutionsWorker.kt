package com.tingyun.smartmistakebook

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The queued payload is one durable manifest identity; question/answer bytes stay in the database. */
class PrepareDetailedSolutionsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SmartMistakeBookApplication ?: return Result.failure()
        val id = inputData.getString(MANIFEST_ID)?.takeIf { it.startsWith("answer-manifest:") && it.length <= 100 } ?: return Result.failure()
        val startup = withTimeoutOrNull(30_000) { app.startupState.first { it != StartupState.Initializing } }
        if (startup == null) return if (runAttemptCount < 3) Result.retry() else Result.failure()
        if (!startup.allowsMistakeExportWorker()) return Result.failure()
        val repository = app.detailedSolutionRepository ?: return Result.failure()
        return try {
            repository.prepare(id, app.capabilities.networkRequestsAllowed)
            if (repository.read(id)?.complete == true) Result.success() else Result.failure()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure() }
    }

    companion object {
        const val MANIFEST_ID = "answer_manifest_id"
        fun enqueue(context: Context, manifestId: String) {
            val request = OneTimeWorkRequestBuilder<PrepareDetailedSolutionsWorker>()
                .setInputData(Data.Builder().putString(MANIFEST_ID, manifestId).build())
                .addTag("detailed-solutions").build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("detailed-solutions:$manifestId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
