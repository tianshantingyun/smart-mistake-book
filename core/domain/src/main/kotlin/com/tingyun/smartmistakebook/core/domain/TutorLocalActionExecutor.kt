package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.MistakePdfLayout
import com.tingyun.smartmistakebook.core.model.MistakeExportFormat
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorBackupReference
import com.tingyun.smartmistakebook.core.model.TutorCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

data class TutorLocalActionCommand(
    val logicalOperationId: String,
    val conversationId: String,
    val request: TutorLocalActionRequest,
    val context: TutorLocalActionContext,
    val exportEntryIds: List<String> = emptyList(),
    val roundOrdinal: Int = 0,
    val allowedBackupRefs: List<String> = emptyList(),
)

fun interface TutorLocalActionExecutor {
    suspend fun execute(command: TutorLocalActionCommand): TutorLobbyLocalActionOutcome
    suspend fun backupReferences(): List<TutorBackupReference> = emptyList()
    suspend fun availableActions(): Set<TutorLocalAction> = TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS
}

/** Platform landings report the side effect they actually completed. */
data class TutorAutonomousActionLandings(
    val saveCaptureDraft: suspend (String) -> TutorLobbyLocalActionOutcome,
    val intakeImages: suspend (List<String>) -> TutorLobbyLocalActionOutcome,
    val openProblem: suspend (String?) -> TutorLobbyLocalActionOutcome,
    val export: suspend (String, String, List<String>, MistakePdfLayout, MistakeExportFormat) -> TutorLobbyLocalActionOutcome,
    val applicationAction: suspend (TutorLocalActionCommand, String, String) -> TutorLobbyLocalActionOutcome = { command, _, _ ->
        TutorLobbyLocalActionOutcome(command.request.action.actionId, "FAILED", "这项应用操作尚未接通。", ok = false)
    },
    val backupReferences: suspend () -> List<TutorBackupReference> = { emptyList() },
    val availableActions: suspend () -> Set<TutorLocalAction> = { TutorCapabilitySnapshot.IMPLEMENTED_LOCAL_ACTIONS },
)

/** The request row is written before execution. Replays use the same landing key and stored receipt. */
class PersistentTutorLocalActionExecutor(
    private val requests: AgentPendingRequestRepository,
    private val landings: TutorAutonomousActionLandings,
    private val clock: () -> Long = System::currentTimeMillis,
) : TutorLocalActionExecutor {
    override suspend fun backupReferences(): List<TutorBackupReference> = landings.backupReferences()
    override suspend fun availableActions(): Set<TutorLocalAction> = landings.availableActions()
    override suspend fun execute(command: TutorLocalActionCommand): TutorLobbyLocalActionOutcome =
        executionMutex.withLock {
            val request = command.request
            if (request.action !in landings.availableActions()) {
                return@withLock outcome(request, false, "这项应用操作当前未就绪。")
            }
            if (request.action == TutorLocalAction.RESTORE_BACKUP &&
                request.parameters["backupRef"] !in command.allowedBackupRefs) {
                return@withLock outcome(request, false, "这份备份未在本轮授权列表中，未执行恢复。")
            }
            if (request.action in TutorLocalAction.ENTRY_TARGET_ACTIONS &&
                request.action !in setOf(TutorLocalAction.OPEN_PROBLEM, TutorLocalAction.SAVE_TO_NOTEBOOK) &&
                command.context.libraryProblemId == null) {
                return@withLock outcome(request, false, "本轮没有可执行这项操作的题目。")
            }
            val selectedEntryIds = request.parameters["entryIds"]?.split(',')?.map(String::trim)?.distinct()
                ?: command.exportEntryIds.distinct()
            if (request.parameters["entryId"]?.let { it != command.context.libraryProblemId } == true ||
                selectedEntryIds.any { it.isBlank() || it !in command.exportEntryIds }) {
                return@withLock outcome(request, false, "指定的条目没有通过本轮本地目标核对。")
            }
            val admission = tutorLocalActionAdmission(request, command.context)
                ?: return@withLock outcome(request, false, "这次操作没有可用的本地目标或参数无效。")
            // Semantic identity survives provider retries that issue a fresh native call id.
            val identity = listOf(
                command.logicalOperationId, command.conversationId, request.action.actionId,
                admission.payloadJson, selectedEntryIds.joinToString("\u001f"),
                if (request.action in TutorLocalAction.APPLICATION_ACTIONS)
                    request.parameters.toSortedMap().entries.joinToString("\u001f") { "${it.key}=${it.value}" }
                else "",
            ).joinToString("\u001e")
            val key = stableKey(identity)
            val effect = createRow(command, admission, "effect-$key")
                ?: return@withLock outcome(request, false, "操作记录没有保存，这次未执行。")
            if (effect.status.isTerminal) {
                return@withLock decodeAutonomousActionOutcome(effect.resolutionNote)
                    ?.copy(callId = request.callId)
                    ?: outcome(request, false, "先前的操作结果无法读取。")
            }
            val attemptKey = stableKey("$key:${request.callId ?: command.roundOrdinal}")
            val attempt = createRow(command, admission, "attempt-$attemptKey")
                ?: return@withLock outcome(request, false, "操作记录没有保存，这次未执行。")
            val previous = decodeAutonomousActionOutcome(attempt.resolutionNote)
            if (attempt.status.isTerminal && previous?.decision != "QUEUED") {
                return@withLock previous?.copy(callId = request.callId)
                    ?: outcome(request, false, "先前的操作结果无法读取。")
            }
            val receipt = try {
                val landed = if (request.action in TutorLocalAction.APPLICATION_ACTIONS) {
                    landings.applicationAction(command, key, attemptKey)
                } else when (admission.target) {
                    TutorLocalActionTarget.SAVE_CAPTURE_DRAFT ->
                        landings.saveCaptureDraft(requireNotNull(command.context.captureSessionId))
                    TutorLocalActionTarget.INTAKE_ATTACHED_IMAGES ->
                        landings.intakeImages(command.context.attachedImageAssetIds)
                    TutorLocalActionTarget.LIBRARY_PROBLEM_DETAIL ->
                        landings.openProblem(command.context.libraryProblemId)
                    TutorLocalActionTarget.EXPORT_SHEET -> landings.export(
                        "agent-export-$key", attemptKey, selectedEntryIds,
                        requireNotNull(tutorLocalActionExportProposal(admission.payloadJson)),
                        MistakeExportFormat.fromToken(request.parameters["format"])
                            ?: MistakeExportFormat.PDF,
                    )
                    TutorLocalActionTarget.APPLICATION_ACTION ->
                        error("Application actions require their typed landing")
                }
                landed.copy(kind = request.action.actionId, callId = request.callId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                outcome(request, false, "这次操作未完成，可以重新尝试。")
            }
            // A failed attempt leaves the semantic effect retryable. A queued export is queried
            // again on replay until the worker has actually produced a terminal result.
            if (receipt.ok == true && receipt.decision != "QUEUED") settle(effect, receipt)
            if (!attempt.status.isTerminal) settle(attempt, receipt)
            receipt
        }

    private suspend fun createRow(
        command: TutorLocalActionCommand,
        admission: TutorLocalActionAdmission,
        identity: String,
    ) = requests.createRequest(
        AgentPendingRequestDraft(
            conversationArea = AUTONOMOUS_ACTION_AREA,
            conversationId = command.conversationId,
            logicalOperationId = identity,
            messageId = identity,
            kind = admission.kind,
            payloadJson = admission.payloadJson,
        ), clock(),
    )

    private suspend fun settle(row: AgentPendingRequest, receipt: TutorLobbyLocalActionOutcome) {
        requests.resolveRequest(
            requestId = row.requestId,
            decision = AgentPendingRequestDecision.ACCEPT,
            resolutionNote = AUTONOMOUS_OUTCOME_PREFIX + Json.encodeToString(
                TutorLobbyLocalActionOutcome.serializer(), receipt,
            ),
            resolvedAtEpochMillis = clock(),
        ) ?: error("Local action receipt was not persisted")
    }

    private fun stableKey(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun outcome(request: TutorLocalActionRequest, ok: Boolean, detail: String) =
        TutorLobbyLocalActionOutcome(
            kind = request.action.actionId,
            decision = if (ok) "SUCCEEDED" else "FAILED",
            detail = detail,
            callId = request.callId,
            ok = ok,
        )

    private companion object {
        // The application has one process. Share the landing/settlement lock across restored
        // executor instances as well as simultaneous model tasks in that process.
        val executionMutex = Mutex()
    }
}

const val AUTONOMOUS_ACTION_AREA = "autonomous_actions"
private const val AUTONOMOUS_OUTCOME_PREFIX = "action-outcome:"

fun decodeAutonomousActionOutcome(note: String?): TutorLobbyLocalActionOutcome? {
    if (note == null || !note.startsWith(AUTONOMOUS_OUTCOME_PREFIX)) return null
    return runCatching {
        Json.decodeFromString(TutorLobbyLocalActionOutcome.serializer(), note.removePrefix(AUTONOMOUS_OUTCOME_PREFIX))
    }.getOrNull()
}
