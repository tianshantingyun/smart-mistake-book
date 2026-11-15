package com.tingyun.smartmistakebook.core.data.tutor

import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import kotlinx.serialization.Serializable

/** Durable journal for the autonomous EDIT_PROBLEM action (survives process death). */
@Serializable
data class TutorPreparedEdit(
    val entryId: String,
    val revisionId: String,
    val revisionNumber: Int,
    val operationId: String,
    val document: CapturedQuestionDocument,
)

/** Durable journal for the autonomous RESTORE_BACKUP action. */
@Serializable
data class TutorRestoreJournal(
    val effectKey: String,
    val backupRef: String,
    val modelTaskRequestId: String,
    val status: String = "QUEUED",
    val detail: String = "恢复任务已排队，将在当前对话回合完成后执行。",
)
