package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.TutorLocalAction
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentTutorLocalActionExecutorTest {
    private val rows = MemoryRequests()
    private var opens = 0
    private var exports = 0
    private var succeeds = true
    private var queued = false
    private var cancel = false
    private val exportKeys = mutableListOf<String>()
    private val exportSelections = mutableListOf<List<String>>()
    private val exportFormats = mutableListOf<com.tingyun.smartmistakebook.core.model.MistakeExportFormat>()
    private val applicationCalls = mutableListOf<Pair<TutorLocalActionCommand, String>>()
    private val landings = TutorAutonomousActionLandings(
        saveCaptureDraft = { receipt(true, "已保存", "entry-1") },
        intakeImages = { receipt(true, "已进入录入队列") },
        openProblem = {
            opens++
            delay(1)
            if (cancel) throw CancellationException("stopped")
            receipt(succeeds, if (succeeds) "已打开" else "窗口不可用")
        },
        export = { key, _, selectedIds, _, format ->
            exports++
            exportKeys += key
            exportSelections += selectedIds
            exportFormats += format
            receipt(true, "导出结果", key).copy(decision = if (queued) "QUEUED" else "SUCCEEDED")
        },
        applicationAction = { command, effect, _ ->
            applicationCalls += command to effect
            receipt(true, "应用写入完成", command.context.libraryProblemId)
        },
    )

    private fun command(callId: String = "call-1") = TutorLocalActionCommand(
        logicalOperationId = "operation-1", conversationId = "conversation-1",
        request = TutorLocalActionRequest(TutorLocalAction.OPEN_PROBLEM, callId = callId),
        context = TutorLocalActionContext(libraryProblemId = "entry-1"),
    )

    @Test fun successSurvivesExecutorRecreationAndFreshNativeCallIds() = runBlocking {
        val first = PersistentTutorLocalActionExecutor(rows, landings).execute(command())
        val replay = PersistentTutorLocalActionExecutor(rows, landings).execute(command("call-2"))
        assertTrue(first.ok == true)
        assertEquals("call-2", replay.callId)
        assertEquals(1, opens)
    }

    @Test fun freshCallCanRetryFailureWhileSameCallReturnsItsReceipt() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        succeeds = false
        assertFalse(executor.execute(command()).ok == true)
        succeeds = true
        assertFalse(executor.execute(command()).ok == true)
        assertTrue(executor.execute(command("call-2")).ok == true)
        assertEquals(2, opens)
    }

    @Test fun simultaneousExecutorInstancesPerformOneSuccessfulLanding() = runBlocking {
        val results = (1..2).map {
            async { PersistentTutorLocalActionExecutor(rows, landings).execute(command()) }
        }.awaitAll()
        assertTrue(results.all { it.ok == true })
        assertEquals(1, opens)
    }

    @Test fun queuedExportRechecksWorkerAndRetainsItsStableIdentity() = runBlocking {
        val command = command().copy(
            request = TutorLocalActionRequest(
                TutorLocalAction.START_EXPORT,
                parameters = mapOf("templateId" to "practice_sheet", "format" to "docx"), callId = "export-call",
            ), exportEntryIds = listOf("entry-1"),
        )
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        queued = true
        assertEquals("QUEUED", executor.execute(command).decision)
        queued = false
        assertEquals("SUCCEEDED", executor.execute(command).decision)
        assertEquals("SUCCEEDED", executor.execute(command).decision)
        assertEquals(2, exports)
        assertEquals(1, exportKeys.distinct().size)
        assertEquals(
            com.tingyun.smartmistakebook.core.model.MistakeExportFormat.DOCX,
            exportFormats.first(),
        )
    }

    @Test fun equivalentExportSpacingAndExplicitDefaultsReuseTheCompletedEffect() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        val command = command().copy(
            request = TutorLocalActionRequest(TutorLocalAction.START_EXPORT,
                mapOf("templateId" to "practice_sheet", "entryIds" to "entry-1,entry-2"),
                callId = "first-export"),
            exportEntryIds = listOf("entry-1", "entry-2"),
        )
        val first = executor.execute(command)
        val defaults = com.tingyun.smartmistakebook.core.model.MistakePdfLayout.DEFAULT
        val equivalent = command.copy(request = command.request.copy(
            parameters = command.request.parameters + mapOf(
                "entryIds" to "entry-1, entry-2",
                "columnCount" to defaults.columnCount.toString(),
                "fontScale" to defaults.fontScale.toString(),
                "includeAnswer" to defaults.includeAnswer.toString(),
            ), callId = "second-export",
        ))
        val replay = executor.execute(equivalent)
        assertEquals(first.artifactId, replay.artifactId)
        assertEquals("second-export", replay.callId)
        assertEquals(1, exports)
        assertEquals(1, exportKeys.distinct().size)
    }

    @Test fun cancelledLandingRemainsReplayable() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        cancel = true
        val failed = runCatching { executor.execute(command()) }.exceptionOrNull()
        assertTrue(failed is CancellationException)
        assertTrue(rows.rows.values.all { !it.status.isTerminal })
        cancel = false
        assertTrue(executor.execute(command()).ok == true)
    }

    @Test fun receiptWriteFailureReplaysTheSameExportEffectKey() = runBlocking {
        val command = command().copy(
            request = TutorLocalActionRequest(TutorLocalAction.START_EXPORT,
                mapOf("templateId" to "practice_sheet"), callId = "export-call"),
            exportEntryIds = listOf("entry-1"),
        )
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        rows.failNextResolution = true
        assertTrue(runCatching { executor.execute(command) }.isFailure)
        assertEquals("SUCCEEDED", executor.execute(command).decision)
        assertEquals(2, exports)
        assertEquals(1, exportKeys.distinct().size)
    }

    @Test fun explicitUnknownEntryCannotReachThePlatformLanding() = runBlocking {
        val result = PersistentTutorLocalActionExecutor(rows, landings).execute(command().copy(
            request = TutorLocalActionRequest(TutorLocalAction.OPEN_PROBLEM,
                mapOf("entryId" to "invented-entry")),
        ))
        assertEquals(false, result.ok)
        assertEquals(0, opens)
    }

    @Test fun exportCanSelectOnlyValidatedLocalEntries() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        val command = command().copy(
            request = TutorLocalActionRequest(TutorLocalAction.START_EXPORT,
                mapOf("templateId" to "practice_sheet", "entryIds" to "entry-2")),
            exportEntryIds = listOf("entry-2", "entry-1"),
        )
        assertTrue(executor.execute(command).ok == true)
        assertEquals(1, exports)
        assertEquals(listOf(listOf("entry-2")), exportSelections)
        val invalid = command.copy(request = command.request.copy(
            parameters = command.request.parameters + ("entryIds" to "entry-2,invented-entry"),
        ))
        assertEquals(false, executor.execute(invalid).ok)
        assertEquals(1, exports)
        val malformed = command.copy(request = command.request.copy(
            parameters = command.request.parameters + ("entryIds" to "entry-2,"),
        ))
        assertEquals(false, executor.execute(malformed).ok)
        assertEquals(1, exports)
    }

    @Test fun missingTargetDoesNotClaimSaveSuccess() = runBlocking {
        val result = PersistentTutorLocalActionExecutor(rows, landings).execute(command().copy(
            request = TutorLocalActionRequest(TutorLocalAction.SAVE_TO_NOTEBOOK),
            context = TutorLocalActionContext(),
        ))
        assertEquals(false, result.ok)
        assertTrue(rows.rows.isEmpty())
    }

    @Test fun oppositeFavoriteValuesHaveIndependentEffectsAndLegalHistoricalLedgerPayloads() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        val favorite = command().copy(request = TutorLocalActionRequest(
            TutorLocalAction.SET_FAVORITE, mapOf("entryId" to "entry-1", "favorite" to "true"), callId = "favorite",
        ))
        assertEquals(true, executor.execute(favorite).ok)
        val unfavorite = favorite.copy(request = favorite.request.copy(
            parameters = favorite.request.parameters + ("favorite" to "false"), callId = "unfavorite",
        ))
        assertEquals(true, executor.execute(unfavorite).ok)
        assertEquals(true, executor.execute(unfavorite).ok)
        assertEquals(2, applicationCalls.size)
        assertEquals(2, applicationCalls.map { it.second }.distinct().size)
        rows.rows.values.forEach { requireAgentPendingRequestPayload(it.kind, it.payloadJson) }
    }

    @Test fun restoreOnlyUsesTheCurrentRoundsAuthorizedBackupReferences() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        val restore = command().copy(request = TutorLocalActionRequest(
            TutorLocalAction.RESTORE_BACKUP, mapOf("backupRef" to "backup-approved"), callId = "restore",
        ), context = TutorLocalActionContext())
        assertEquals(false, executor.execute(restore).ok)
        assertEquals(0, applicationCalls.size)
        assertEquals(true, executor.execute(restore.copy(allowedBackupRefs = listOf("backup-approved"))).ok)
        assertEquals(1, applicationCalls.size)
    }

    @Test fun entryMutationWithoutALocalEntryCannotReachTheApplicationRepository() = runBlocking {
        val executor = PersistentTutorLocalActionExecutor(rows, landings)
        val edit = command().copy(request = TutorLocalActionRequest(
            TutorLocalAction.EDIT_PROBLEM, mapOf("instruction" to "修正题目的单位"),
        ), context = TutorLocalActionContext())
        assertEquals(false, executor.execute(edit).ok)
        assertEquals(0, applicationCalls.size)
    }

    private fun receipt(ok: Boolean, detail: String, id: String? = null) = TutorLobbyLocalActionOutcome(
        kind = "test", decision = if (ok) "SUCCEEDED" else "FAILED", detail = detail,
        ok = ok, artifactId = id,
    )

    private class MemoryRequests : AgentPendingRequestRepository {
        val rows = linkedMapOf<String, AgentPendingRequest>()
        var failNextResolution = false
        override suspend fun createRequest(draft: AgentPendingRequestDraft, createdAtEpochMillis: Long) =
            rows.getOrPut(draft.requestId()) {
                AgentPendingRequest(
                    draft.requestId(), draft.conversationArea, draft.conversationId,
                    draft.logicalOperationId, draft.messageId, draft.kind, draft.payloadJson,
                    AgentPendingRequestStatus.PENDING, createdAtEpochMillis,
                )
            }

        override suspend fun resolveRequest(
            requestId: String, decision: AgentPendingRequestDecision,
            resolutionNote: String?, resolvedAtEpochMillis: Long,
        ): AgentPendingRequest {
            if (failNextResolution) {
                failNextResolution = false
                error("Simulated persistence failure after landing")
            }
            return requireNotNull(rows[requestId]).copy(
                status = decision.terminalStatus, resolutionNote = resolutionNote,
                resolvedAtEpochMillis = resolvedAtEpochMillis,
            ).also { rows[requestId] = it }
        }
    }
}
