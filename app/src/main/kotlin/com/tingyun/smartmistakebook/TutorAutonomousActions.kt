package com.tingyun.smartmistakebook

import androidx.work.WorkManager
import com.tingyun.smartmistakebook.core.domain.MistakeExportKind
import com.tingyun.smartmistakebook.core.domain.MistakeExportStatus
import com.tingyun.smartmistakebook.core.domain.PersistentTutorLocalActionExecutor
import com.tingyun.smartmistakebook.core.domain.SaveTutorDraftRequest
import com.tingyun.smartmistakebook.core.domain.SaveTutorDraftToLibraryUseCase
import com.tingyun.smartmistakebook.core.domain.TutorAutonomousActionLandings
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionExecutor
import com.tingyun.smartmistakebook.core.export.MistakeExportJobRequest
import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal fun SmartMistakeBookApplication.autonomousTutorActions(): TutorLocalActionExecutor =
    PersistentTutorLocalActionExecutor(
        requests = agentPendingRequestRepository,
        landings = TutorAutonomousActionLandings(
            saveCaptureDraft = { sessionId ->
                val session = captureRepository.readTutorSession(sessionId)
                if (session == null) {
                    actionReceipt(false, "拍照草稿已经不在本机，未保存。")
                } else {
                    val saved = SaveTutorDraftToLibraryUseCase(captureRepository)(
                        SaveTutorDraftRequest(session.draftId, session.sessionId),
                    )
                    actionReceipt(
                        saved.entryId != null,
                        if (saved.entryId == null) "草稿未能加入错题本。"
                        else if (saved.alreadySaved) "这道题已经在错题本里了。"
                        else "已经加入错题本。",
                        saved.entryId,
                    )
                }
            },
            intakeImages = { assetIds ->
                val intake = tutorAttachedImageIntake.intake(assetIds, System.currentTimeMillis())
                actionReceipt(intake.landed, intake.detail)
            },
            openProblem = { problemId ->
                if (problemId != null) studyRepository.refresh()
                val opened = withContext(Dispatchers.Main.immediate) {
                    tutorActionNavigator?.invoke(problemId) == true
                }
                actionReceipt(
                    opened,
                    if (opened) "已经打开错题本${if (problemId == null) "" else "中的这道题"}。"
                    else "当前没有可用的应用导航窗口，未打开错题本。",
                    problemId.takeIf { opened },
                )
            },
            export = { exportId, attemptId, entryIds, layout, format ->
                exportTutorAction(exportId, attemptId, entryIds, layout, format)
            },
            applicationAction = { command, effectKey, attemptKey ->
                executeTutorApplicationAction(command, effectKey, attemptKey)
            },
            backupReferences = { tutorBackupReferences() },
            availableActions = {
                com.tingyun.smartmistakebook.core.model.TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS
                    .filterTo(linkedSetOf()) { action ->
                        try {
                            actionDependenciesReady(action)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // 单个就绪探针失败只裁掉该动作，不能让整份可用集消失。
                            false
                        }
                    }
            },
        ),
    )

/** Actions whose real landing depends on a repository/store that may not be assembled yet. */
private fun SmartMistakeBookApplication.actionDependenciesReady(
    action: com.tingyun.smartmistakebook.core.model.TutorLocalAction,
): Boolean = when (action) {
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.EDIT_PROBLEM ->
        mistakeEditRepository != null && modelConfigurationStore != null
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.CREATE_BACKUP ->
        runCatching { backupRepository }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.RESTORE_BACKUP ->
        runCatching { backupRepository }.isSuccess &&
            runCatching { tutorBackupReferences().isNotEmpty() }.getOrDefault(false)
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.UPDATE_SCHEDULING ->
        runCatching { schedulingSettingsStore }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.ADD_EXAM,
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.REMOVE_EXAM,
    -> runCatching { schedulingSettingsStore }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.RECLASSIFY_PROBLEM ->
        runCatching { mistakeDetailRepository }.isSuccess &&
            runCatching { mistakeOrganizationRepository }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.SET_FAVORITE,
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.ARCHIVE_PROBLEM,
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.RESTORE_PROBLEM,
    -> runCatching { mistakeDetailRepository }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.OPEN_PROBLEM,
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.ADD_TO_REVIEW_PLAN,
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.REMOVE_FROM_REVIEW_PLAN,
    -> runCatching { studyRepository }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.SAVE_TO_NOTEBOOK ->
        runCatching { tutorAttachedImageIntake }.isSuccess
    com.tingyun.smartmistakebook.core.model.TutorLocalAction.START_EXPORT ->
        runCatching { mistakeExportRepository }.isSuccess
    else -> false
}

private suspend fun SmartMistakeBookApplication.exportTutorAction(
    effectId: String,
    attemptId: String,
    entryIds: List<String>,
    layout: MistakePdfLayout,
    format: com.tingyun.smartmistakebook.core.model.MistakeExportFormat,
): TutorLobbyLocalActionOutcome {
    if (entryIds.isEmpty()) return actionReceipt(false, "本轮没有查到可导出的题，请先查找错题。")
    val existing = mistakeExportRepository.observeRecords().first()
        .filter { it.exportId == effectId || it.exportId.startsWith("$effectId-retry-") }
    val active = existing.firstOrNull { it.status == MistakeExportStatus.SUCCEEDED }
        ?: existing.firstOrNull { it.status == MistakeExportStatus.RUNNING }
    val exportId = active?.exportId ?: if (existing.isEmpty()) effectId else "$effectId-retry-$attemptId"
    val request = MistakeExportJobRequest.Batch(
        exportId = exportId,
        entryIds = entryIds,
        layout = layout,
        format = format,
    )
    val started = mistakeExportRepository.recordStarted(exportId, MistakeExportKind.BATCH, System.currentTimeMillis())
        ?: return actionReceipt(false, "导出记录未能保存，这次未入队。")
    if (started.status == MistakeExportStatus.RUNNING) {
        try {
            runInterruptible(Dispatchers.IO) {
                WorkManager.getInstance(this@exportTutorAction).enqueueUniqueWork(
                    MistakeExportJobCodec.uniqueName(exportId),
                    ExportPdfDriver.existingPolicy,
                    ExportPdfDriver.request(request),
                ).result.get()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mistakeExportRepository.recordFailed(exportId, "这次导出未能排上队。", System.currentTimeMillis())
            return actionReceipt(false, "这次导出未能排上队。", exportId)
        }
    }
    val finished = if (started.status == MistakeExportStatus.RUNNING) {
        withTimeoutOrNull(60_000L) {
            mistakeExportRepository.observeRecords().first { records ->
                records.any { it.exportId == exportId && it.status != MistakeExportStatus.RUNNING }
            }.first { it.exportId == exportId }
        }
    } else started
    return when (finished?.status) {
        MistakeExportStatus.SUCCEEDED -> actionReceipt(true, "导出文件已生成，可在导出成果中打开。", exportId)
        MistakeExportStatus.FAILED -> actionReceipt(false, finished.failureMessage ?: "导出未完成。", exportId)
        else -> TutorLobbyLocalActionOutcome(
            kind = "START_EXPORT", decision = "QUEUED", ok = true,
            detail = "已排入后台导出队列，文件仍在生成，可在导出成果中查看进度。", artifactId = exportId,
        )
    }
}

private fun actionReceipt(ok: Boolean, detail: String, artifactId: String? = null) =
    TutorLobbyLocalActionOutcome(
        kind = "LOCAL_ACTION", decision = if (ok) "SUCCEEDED" else "FAILED",
        detail = detail, ok = ok, artifactId = artifactId,
    )
