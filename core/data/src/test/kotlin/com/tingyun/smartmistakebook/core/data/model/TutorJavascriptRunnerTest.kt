package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.VisualExecutionEngine
import com.tingyun.smartmistakebook.core.domain.VisualExecutionMode
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.TutorEvidenceDirection
import com.tingyun.smartmistakebook.core.model.TutorLobbyLocalActionOutcome
import com.tingyun.smartmistakebook.core.model.TutorLocalActionRequest
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorJavascriptRunnerTest {
    private class ScriptedEngine(val results: List<VisualExecutionResult>) : VisualExecutionEngine {
        val requests = mutableListOf<VisualExecutionRequest>()
        override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
            requests.add(request)
            return results[requests.lastIndex]
        }
        override fun close() = Unit
    }

    private fun computed(json: String) = VisualExecutionResult.Computed(json)
    private fun objectJson(json: String) = Json.parseToJsonElement(json) as JsonObject
    private fun pending(index: Int, type: String = "tool", name: String = "APP_CONTEXT", args: String = "{}") =
        computed("""{"kind":"yield","index":$index,"key":"call-$index","callType":"$type","name":"$name","args":$args}""")
    private fun completed(consumed: Int = 0, value: String = "42", state: String? = null) =
        computed("""{"kind":"complete","consumed":$consumed,"value":$value,"stateUpdated":${state != null},"state":${state ?: "null"}}""")
    private val noTool: suspend (TutorToolCall) -> TutorToolOutcome = { error("Unexpected tool") }
    private val noAction: suspend (TutorLocalActionRequest) -> TutorLobbyLocalActionOutcome = { error("Unexpected action") }

    @Test
    fun toolAndActionCallbacksEachRunOnceAndReceiptsEnterReplay() = runBlocking {
        val engine = ScriptedEngine(listOf(
            pending(0, name = "NOTEBOOK_READ", args = """{"terms":[],"offset":0,"limit":10}"""),
            pending(1, "action", "OPEN_PROBLEM", """{"entryId":"entry-7"}"""),
            completed(2, """{"opened":true}""", """{"count":1}"""),
        ))
        val tools = mutableListOf<TutorToolCall>()
        val actions = mutableListOf<TutorLocalActionRequest>()
        val result = runTutorJavascript(engine, "return 1;", """{"count":0}""", {
            tools.add(it)
            TutorToolOutcome(it.tool, true, "Found", dataJson = """{"items":[{"entryId":"entry-7"}]}""")
        }, {
            actions.add(it)
            TutorLobbyLocalActionOutcome("OPEN_PROBLEM", "SUCCEEDED", "Opened", ok = true, artifactId = "entry-7")
        })
        assertTrue(result.outcome.ok)
        assertEquals("""{"opened":true}""", result.outcome.dataJson)
        assertEquals("""{"count":1}""", result.stateJson)
        assertEquals(mapOf("offset" to "0", "limit" to "10"), tools.single().parameters)
        assertEquals("entry-7", actions.single().parameters["entryId"])
        assertEquals(3, engine.requests.size)
        assertTrue(engine.requests.all { it.executionMode == VisualExecutionMode.PROGRAM && it.stateJson == """{"count":0}""" })
        assertTrue(engine.requests.last().sourceJs.contains("entry-7"))
        assertTrue(engine.requests.last().sourceJs.contains("SUCCEEDED"))
    }

    @Test
    fun changedReplayIndexStopsBeforeRepeatingTheHostEffect() = runBlocking {
        val engine = ScriptedEngine(listOf(pending(0), pending(0)))
        var calls = 0
        val result = runTutorJavascript(engine, "return 1;", null, {
            calls++
            TutorToolOutcome(it.tool, true, "Done")
        }, noAction)
        assertFalse(result.outcome.ok)
        assertEquals("JAVASCRIPT_NONDETERMINISTIC_REPLAY", result.outcome.errorKind)
        assertEquals(1, calls)
    }

    @Test
    fun twelveHostCallsAreAllowedAndTheThirteenthIsRejected() = runBlocking {
        val engine = ScriptedEngine((0..12).map { pending(it) })
        var calls = 0
        val result = runTutorJavascript(engine, "return 1;", null, {
            calls++
            TutorToolOutcome(it.tool, true, "Done")
        }, noAction)
        assertEquals(12, calls)
        assertEquals("JAVASCRIPT_HOST_CALL_LIMIT", result.outcome.errorKind)
    }

    @Test
    fun recursiveJavascriptIsRejectedBeforeDispatch() = runBlocking {
        val engine = ScriptedEngine(listOf(pending(0, name = "RUN_JAVASCRIPT")))
        val result = runTutorJavascript(engine, "return 1;", null, noTool, noAction)
        assertEquals("JAVASCRIPT_INVALID_HOST_CALL", result.outcome.errorKind)
    }

    @Test
    fun strictWriteParserPreservesSemanticFieldsAndRejectsInvalidEnums() {
        val call = javascriptToolCall(TutorToolName.MASTERY_UPDATE, objectJson("""
            {"terms":["node-1"],"direction":"POSITIVE","understanding":"CONFIDENT","confidence":0.9}
        """.trimIndent()))
        assertEquals(TutorEvidenceDirection.POSITIVE, call.direction)
        assertEquals(0.9, call.confidence, 0.0001)
        assertTrue(runCatching { javascriptToolCall(TutorToolName.MASTERY_UPDATE,
            objectJson("""{"terms":["node-1"],"direction":"SIDEWAYS","understanding":"CONFIDENT"}""")) }.isFailure)
        assertTrue(runCatching { javascriptToolCall(TutorToolName.NOTEBOOK_READ,
            objectJson("""{"limit":"10","parameters":{"limit":"5"}}""")) }.isFailure)
    }

    @Test
    fun failedToolReceiptIsAvailableToJavascriptWithoutInventingSuccess() = runBlocking {
        val engine = ScriptedEngine(listOf(pending(0), completed(1, "false")))
        val result = runTutorJavascript(engine, "return 1;", null, {
            TutorToolOutcome(it.tool, false, "Unavailable", "NOT_AVAILABLE")
        }, noAction)
        assertTrue(result.outcome.ok)
        assertEquals("false", result.outcome.dataJson)
        assertTrue(engine.requests.last().sourceJs.contains("NOT_AVAILABLE"))
    }

    @Test
    fun stateAndOutputBudgetsRejectWithoutCommittingNewState() = runBlocking {
        val original = """{"count":0}"""
        val oversizedState = Json.parseToJsonElement("\"" + "字".repeat(6_000) + "\"").toString()
        val beforeEngine = ScriptedEngine(emptyList())
        val before = runTutorJavascript(beforeEngine, "return 1;", oversizedState, noTool, noAction)
        assertEquals("JAVASCRIPT_STATE_OVER_BUDGET", before.outcome.errorKind)
        assertTrue(beforeEngine.requests.isEmpty())
        val after = runTutorJavascript(ScriptedEngine(listOf(completed(state = oversizedState))),
            "return 1;", original, noTool, noAction)
        assertEquals("JAVASCRIPT_STATE_OVER_BUDGET", after.outcome.errorKind)
        assertEquals(original, after.stateJson)
        val largeOutput = "\"" + "x".repeat(6_001) + "\""
        val output = runTutorJavascript(ScriptedEngine(listOf(completed(value = largeOutput))),
            "return 1;", original, noTool, noAction)
        assertEquals("JAVASCRIPT_OUTPUT_OVER_BUDGET", output.outcome.errorKind)
        assertEquals(original, output.stateJson)
    }

    @Test
    fun runtimeTimeoutIsReturnedAndCancellationPropagates() = runBlocking {
        val rejected = VisualExecutionResult.Rejected(VisualExecutionRejection.PHASE_TIMEOUT, "interrupted")
        val result = runTutorJavascript(ScriptedEngine(listOf(rejected)), "while(true){}", null, noTool, noAction)
        assertEquals("JAVASCRIPT_PHASE_TIMEOUT", result.outcome.errorKind)
        try {
            runTutorJavascript(ScriptedEngine(listOf(pending(0))), "return 1;", null,
                { throw CancellationException("cancel") }, noAction)
            error("Expected cancellation")
        } catch (cancel: CancellationException) {
            assertEquals("cancel", cancel.message)
        }
    }
}
