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
        TutorLocalAction.ADD_TO_REVIEW_PLAN -> {
            val result = studyRepository.addToReviewPlan(requireNotNull(entryId), now)
            val ok = result is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.Added ||
                result is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.AlreadyScheduled
            if (ok) runCatching { studyRepository.refresh() }
            val detail = when (result) {
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.Added ->
                    "已经加入今天的复习计划：第 ${result.ordinal + 1} 项，共 ${result.scheduledCount} 项。"
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.AlreadyScheduled ->
                    "这道题已经在今天的复习计划里。"
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.NotAdded -> result.detail
            }
            val artifactId = when (result) {
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.Added -> result.reviewPlanId
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.AlreadyScheduled -> result.reviewPlanId
                is com.tingyun.smartmistakebook.core.domain.AddToReviewPlanResult.NotAdded -> entryId
            }
            tutorApplicationReceipt(ok, detail, artifactId)
        }
        TutorLocalAction.REMOVE_FROM_REVIEW_PLAN -> {
            val result = studyRepository.removeFromReviewPlan(requireNotNull(entryId), now)
            val ok = result is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.Removed ||
                result is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.NotScheduled
            if (ok) runCatching { studyRepository.refresh() }
            val detail = when (result) {
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.Removed ->
                    "已经从今天的复习计划移除；计划里还剩 ${result.scheduledCount} 项。"
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.NotScheduled ->
                    "这道题本来就不在今天的复习计划里。"
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.NotRemoved -> result.detail
            }
            val artifactId = when (result) {
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.Removed -> result.reviewPlanId
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.NotScheduled -> result.reviewPlanId
                is com.tingyun.smartmistakebook.core.domain.RemoveFromReviewPlanResult.NotRemoved -> entryId
            }
            tutorApplicationReceipt(ok, detail, artifactId)
        }
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
        TutorLocalAction.ADD_EXAM -> addTutorExam(parameters, effectKey)
        TutorLocalAction.REMOVE_EXAM -> removeTutorExam(parameters["examEntryId"])
        TutorLocalAction.RECLASSIFY_PROBLEM -> reclassifyTutorProblem(
            requireNotNull(entryId),
            requireNotNull(parameters["knowledgeNodes"]),
        )
        TutorLocalAction.MERGE_PROBLEM -> {
            val duplicateId = parameters["duplicateEntryId"]?.trim().orEmpty()
            if (duplicateId.isEmpty()) {
                tutorApplicationReceipt(false, "没有指定要合并掉的重复条目。")
            } else if (duplicateId == entryId) {
                tutorApplicationReceipt(false, "重复条目不能是当前题本身。")
            } else {
                val merged = mistakeDetailRepository.mergeEntry(
                    duplicateEntryId = duplicateId,
                    canonicalEntryId = requireNotNull(entryId),
                    relationId = "agent-merge-$effectKey",
                    at = now,
                )
                if (merged) runCatching { studyRepository.refresh() }
                tutorApplicationReceipt(
                    merged,
                    if (merged) {
                        "已把重复题合并到当前题：重复条目已归档并留下关联，可从归档恢复。"
                    } else {
                        "未能合并：条目不存在、不是活动状态，或与当前题是同一道。"
                    },
                    requireNotNull(entryId),
                )
            }
        }
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

private suspend fun SmartMistakeBookApplication.addTutorExam(
    parameters: Map<String, String>,
    effectKey: String,
): TutorLobbyLocalActionOutcome {
    val subject = parameters["subject"]?.trim().orEmpty()
    val title = parameters["title"]?.trim().orEmpty()
    val day = parameters["examEpochDay"]?.toLongOrNull()
    if (subject.isEmpty() || title.isEmpty() || day == null || day < 0) {
        return tutorApplicationReceipt(false, "考试信息不完整：需要科目、日期（epoch day）和名称。")
    }
    // 稳定 entryId：同一逻辑操作重放时是同一场考试（store 按 id upsert，不堆重复行）。
    val entryId = "agent-exam-$effectKey"
    val entry = com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry(
        entryId = entryId,
        subject = subject,
        examEpochDay = day,
        title = title,
    )
    schedulingSettingsStore.addExam(entry)
    val stored = schedulingSettingsStore.exams.first().any { it.entryId == entryId }
    if (!stored) return tutorApplicationReceipt(false, "考试没有保存成功。", entryId)
    runCatching { studyRepository.refresh() }
    val date = runCatching { java.time.LocalDate.ofEpochDay(day).toString() }.getOrNull() ?: day.toString()
    return tutorApplicationReceipt(true, "已把「$title」写入 $subject 的考试日历（$date）。", entryId)
}

private suspend fun SmartMistakeBookApplication.removeTutorExam(
    rawEntryId: String?,
): TutorLobbyLocalActionOutcome {
    val examEntryId = rawEntryId?.trim().orEmpty()
    if (examEntryId.isEmpty()) return tutorApplicationReceipt(false, "没有指定要移除的考试条目。")
    val existing = schedulingSettingsStore.exams.first().firstOrNull { it.entryId == examEntryId }
    if (existing == null) return tutorApplicationReceipt(false, "考试日历里没有这个条目，未移除。")
    schedulingSettingsStore.removeExam(examEntryId)
    val removed = schedulingSettingsStore.exams.first().none { it.entryId == examEntryId }
    if (!removed) return tutorApplicationReceipt(false, "考试没有移除成功。", examEntryId)
    runCatching { studyRepository.refresh() }
    return tutorApplicationReceipt(true, "已从考试日历移除「${existing.title}」。", examEntryId)
}

/**
 * 改知识点关联：只接受 `nodeId@taxonomyVersion` 对，仓库再按已审目录重新校验；
 * 板块与手动分类保持原样，替换的是知识点绑定（与原生「修改知识点关联」同一条仓库路径）。
 */
private suspend fun SmartMistakeBookApplication.reclassifyTutorProblem(
    entryId: String,
    rawSelections: String,
): TutorLobbyLocalActionOutcome {
    val selections = rawSelections.split(',')
        .map { part -> part.trim() }
        .filter { it.isNotEmpty() }
        .map { part ->
            val at = part.lastIndexOf('@')
            if (at <= 0 || at == part.lastIndex) {
                return tutorApplicationReceipt(false, "知识点身份格式无效，应为 nodeId@taxonomyVersion。")
            }
            com.tingyun.smartmistakebook.core.model.KnowledgeNodeSelection(
                nodeId = part.substring(0, at),
                taxonomyVersion = part.substring(at + 1),
            )
        }
    if (selections.isEmpty() || selections.size > 8) {
        return tutorApplicationReceipt(false, "一次只能改 1..8 个知识点关联。")
    }
    val detail = mistakeDetailRepository.observe(entryId).first { it != MistakeDetailState.Loading }
        as? MistakeDetailState.Ready
        ?: return tutorApplicationReceipt(false, "这道题没有可用的当前版本，未改分类。")
    val identity = detail.detail.identity
    val result = mistakeOrganizationRepository.correctConfirmedOrganization(
        MistakeRevisionKey(entryId, identity.problemId, identity.problemRevisionId),
        com.tingyun.smartmistakebook.core.domain.ProblemOrganizationSelection(
            classificationIndexes = emptySet(),
            relationIndexes = emptySet(),
            knowledgeSelections = selections.toSet(),
        ),
        System.currentTimeMillis(),
    )
    if (!result.applied) {
        return tutorApplicationReceipt(false, "题目或分类已变化，这次没有改写；请重新读取当前题后再试。", entryId)
    }
    runCatching { studyRepository.refresh() }
    return tutorApplicationReceipt(
        true,
        "已把这道题的知识点关联改为 ${selections.size} 个节点（板块与手动分类保留）。",
        entryId,
    )
}

internal fun tutorApplicationReceipt(
    ok: Boolean, detail: String, artifactId: String? = null,
    decision: String = if (ok) "SUCCEEDED" else "FAILED",
) = TutorLobbyLocalActionOutcome("APPLICATION_ACTION", decision, detail, ok = ok, artifactId = artifactId)
