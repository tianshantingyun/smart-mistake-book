package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.data.tutor.TutorPreparedEdit
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import com.tingyun.smartmistakebook.core.domain.MistakeEditRequest
import com.tingyun.smartmistakebook.core.domain.MistakeRevisionKey
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemCommand
import com.tingyun.smartmistakebook.core.domain.ReviseMistakeProblemResult
import com.tingyun.smartmistakebook.core.domain.TutorLocalActionCommand
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.EditBasis
import com.tingyun.smartmistakebook.core.model.MistakeEditJobStatus
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

internal suspend fun SmartMistakeBookApplication.executeTutorApplicationAction(
    command: TutorLocalActionCommand,
    effectKey: String,
    attemptKey: String,
): TutorLobbyLocalActionOutcome {
    val parameters = command.request.parameters
    val entryId = command.context.libraryProblemId
    val now = System.currentTimeMillis()
    return when (command.request.action) {
        TutorLocalAction.SET_FAVORITE -> {
            val favorite = parameters["favorite"]?.toBooleanStrictOrNull()
                ?: return tutorApplicationReceipt(false, "收藏参数无效。")
            val written = mistakeDetailRepository.setFavorite(requireNotNull(entryId), favorite, now)
            tutorApplicationReceipt(written, if (written) {
                if (favorite) "已经收藏这道题。" else "已经取消收藏。"
            } else "题目已不存在，收藏未修改。", entryId)
        }
        TutorLocalAction.ARCHIVE_PROBLEM -> {
            val written = mistakeDetailRepository.archiveEntry(requireNotNull(entryId), now)
            tutorApplicationReceipt(written, if (written) "已经归档这道题。" else "题目未能归档。", entryId)
        }
        TutorLocalAction.RESTORE_PROBLEM -> {
            val written = mistakeDetailRepository.restoreEntry(requireNotNull(entryId), now)
            tutorApplicationReceipt(written, if (written) "已经恢复这道题。" else "题目未能恢复。", entryId)
        }
        TutorLocalAction.EDIT_PROBLEM -> editTutorProblem(requireNotNull(entryId),
            requireNotNull(parameters["instruction"]), effectKey, attemptKey)
        TutorLocalAction.UPDATE_SCHEDULING -> {
            if (parameters.isEmpty()) return tutorApplicationReceipt(false, "没有提供要修改的调度设置。")
            val current = schedulingSettingsStore.options.first()
            val minutes = parameters["dailyReviewMinutes"]?.let {
                it.toIntOrNull() ?: return tutorApplicationReceipt(false, "复习分钟数必须是整数。")
            } ?: current.dailyReviewMinutes
            val retention = parameters["desiredRetention"]?.let {
                it.toDoubleOrNull() ?: return tutorApplicationReceipt(false, "目标保留率必须是数字。")
            } ?: current.desiredRetention
            val next = current.copy(dailyReviewMinutes = minutes, desiredRetention = retention)
            schedulingSettingsStore.setOptions(next)
            val stored = schedulingSettingsStore.options.first()
            tutorApplicationReceipt(stored == next,
                "已保存每日复习 ${stored.dailyReviewMinutes} 分钟，目标保留率 ${stored.desiredRetention}。保留率在下次启动生效。")
        }
        TutorLocalAction.CREATE_BACKUP -> createTutorBackup(effectKey, parameters["selectionMask"]?.toIntOrNull() ?: 15)
        TutorLocalAction.RESTORE_BACKUP -> enqueueTutorBackupRestore(command, effectKey)
        else -> tutorApplicationReceipt(false, "这项操作没有应用执行路径。")
    }
}

private suspend fun SmartMistakeBookApplication.editTutorProblem(
    entryId: String, instruction: String, effectKey: String, attemptKey: String,
): TutorLobbyLocalActionOutcome {
    val edits = mistakeEditRepository ?: return tutorApplicationReceipt(false, "题目修改服务尚未就绪。")
    val preparedFile = File(noBackupFilesDir, "tutor-actions/edit-$effectKey.json")
    val preferences = getSharedPreferences("tutor-action-jobs", 0)
    var prepared = if (preparedFile.isFile) Json.decodeFromString(TutorPreparedEdit.serializer(), preparedFile.readText()) else null
    if (prepared == null) {
        val detail = mistakeDetailRepository.observe(entryId).first { it != MistakeDetailState.Loading }
            as? MistakeDetailState.Ready ?: return tutorApplicationReceipt(false, "这道题没有可编辑的完整题面。")
        val identity = detail.detail.identity
        val basis = EditBasis(entryId, identity.problemRevisionId,
            CapturedQuestionDocumentFingerprint.of(detail.questionDocument))
        val key = "edit-$effectKey"
        val existingOperation = preferences.getString(key, null)
        val operation = existingOperation ?: edits.requestEdit(MistakeEditRequest(
            MistakeRevisionKey(entryId, identity.problemId, identity.problemRevisionId),
            detail.questionDocument, detail.questionDocument.document.blocks.map { it.id }.toSet(),
            instruction, editTitle = true,
        ), agentConsentGranted = true).also {
            check(preferences.edit().putString(key, it).commit())
        }
        val existing = edits.observe(operation).first()
        if (existing?.status in setOf(MistakeEditJobStatus.FAILED, MistakeEditJobStatus.UNAVAILABLE) &&
            preferences.getString("$key-attempt", null) != attemptKey) {
            check(preferences.edit().putString("$key-attempt", attemptKey).commit())
            edits.retry(operation, agentConsentGranted = true)
        }
        val job = withTimeoutOrNull(60_000L) {
            edits.observe(operation).first { it != null && it.status !in setOf(
                MistakeEditJobStatus.QUEUED, MistakeEditJobStatus.REQUESTING, MistakeEditJobStatus.GENERATING_FIGURES,
            ) }
        } ?: return tutorApplicationReceipt(true, "修改作业已提交，仍在生成，题目尚未改写。", operation, "QUEUED")
        if (job.status == MistakeEditJobStatus.NO_CHANGE) return tutorApplicationReceipt(true, "已检查，题目内容无需修改。", entryId)
        if (job.status !in setOf(MistakeEditJobStatus.READY, MistakeEditJobStatus.ADOPTED)) {
            return tutorApplicationReceipt(false, job.message ?: "修改未完成，原题尚未改写。", operation)
        }
        val ready = edits.readySuggestion(operation, basis)
            ?: return tutorApplicationReceipt(false, "修改依据已变化，这次未改写题目。", operation)
        prepared = TutorPreparedEdit(entryId, identity.problemRevisionId, identity.revisionNumber,
            operation, requireNotNull(ready.preparedDraft))
        preparedFile.parentFile?.mkdirs()
        writeTutorActionFile(preparedFile, Json.encodeToString(TutorPreparedEdit.serializer(), prepared))
        if (!edits.markAdopted(operation, basis)) return tutorApplicationReceipt(false, "修改结果未能采用。", operation)
    }
    val draft = requireNotNull(prepared)
    val result = mistakeDetailRepository.reviseProblem(ReviseMistakeProblemCommand(
        commandId = "agent-edit-$effectKey", entryId = draft.entryId,
        expectedRevisionId = draft.revisionId, expectedRevisionNumber = draft.revisionNumber,
        questionDocument = draft.document, revisedAtEpochMillis = System.currentTimeMillis(),
        title = draft.document.document.title, adoptedEditOperationIds = setOf(draft.operationId),
    ))
    return when (result) {
        is ReviseMistakeProblemResult.Saved -> tutorApplicationReceipt(true,
            "已经保存修改后的题目，第 ${result.receipt.revisionNumber} 版。", entryId)
        is ReviseMistakeProblemResult.Unchanged -> tutorApplicationReceipt(true, "修改内容与当前版本相同。", entryId)
        else -> tutorApplicationReceipt(false, "保存修改失败，题目版本可能已经变化。", draft.operationId)
    }
}

internal fun tutorApplicationReceipt(
    ok: Boolean, detail: String, artifactId: String? = null,
    decision: String = if (ok) "SUCCEEDED" else "FAILED",
) = TutorLobbyLocalActionOutcome("APPLICATION_ACTION", decision, detail, ok = ok, artifactId = artifactId)
