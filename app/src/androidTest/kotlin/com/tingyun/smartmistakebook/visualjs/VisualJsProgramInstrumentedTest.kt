package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionMode
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises PROGRAM through the production AIDL client and real isolated QuickJS service. */
@RunWith(AndroidJUnit4::class)
class VisualJsProgramInstrumentedTest {
    private val engine = VisualJsClientEngine(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun closeEngine() = engine.close()

    private fun request(source: String, state: String? = null) = VisualExecutionRequest(
        programId = "generic-program",
        sourceJs = source,
        sceneRevision = 1,
        stateJson = state,
        viewport = VisualViewport(1, 1),
        timeMs = 0,
        seed = 42,
        theme = "light",
        executionMode = VisualExecutionMode.PROGRAM,
    )

    @Test
    fun functionsLoopsAndFrozenInputRoundTripInAnIsolatedWorker() = runBlocking {
        val result = engine.execute(request(
            """
            function factorial(n) {
                let answer = 1;
                for (let i = 2; i <= n; i++) answer *= i;
                return answer;
            }
            return { answer: factorial(input.state.number),
                frozen: Object.isFrozen(input) && Object.isFrozen(input.state.nested),
                nested: input.state.nested[0], date: typeof Date,
                fetch: typeof fetch, host: typeof process };
            """.trimIndent(),
            """{"number":6,"nested":[7]}""",
        ))
        assertTrue("$result", result is VisualExecutionResult.Computed)
        val value = JSONObject((result as VisualExecutionResult.Computed).valueJson)
        assertEquals(720, value.getInt("answer"))
        assertTrue(value.getBoolean("frozen"))
        assertEquals(7, value.getInt("nested"))
        assertEquals("undefined", value.getString("date"))
        assertEquals("undefined", value.getString("fetch"))
        assertEquals("undefined", value.getString("host"))
        val workerUid = engine.workerUidForTest()
        assertNotNull(workerUid)
        assertTrue(workerUid != Process.myUid())
    }

    @Test
    fun arbitraryJsonValuesAndFreshContextsAreSupported() = runBlocking {
        for ((source, expected) in listOf(
            "return 42;" to "42",
            "return null;" to "null",
            "return [true, 3, 'answer'];" to "[true,3,\"answer\"]",
            "globalThis.persisted = 1; return 'first';" to "\"first\"",
            "return typeof globalThis.persisted;" to "\"undefined\"",
        )) {
            val result = engine.execute(request(source))
            assertTrue("$result", result is VisualExecutionResult.Computed)
            assertEquals(expected, (result as VisualExecutionResult.Computed).valueJson)
        }
    }

    @Test
    fun invalidProgramsAndUnserializableResultsAreBoundedlyRejected() = runBlocking {
        for (source in listOf(
            "return ( ;",
            "input.state.nested[0] = 8; return input.state;",
            "return undefined;",
            "const value = {}; value.self = value; return value;",
            "return Promise.resolve(1);",
            "return 'x'.repeat(300000);",
        )) {
            val result = engine.execute(request(source, """{"nested":[7]}"""))
            assertTrue("$result", result is VisualExecutionResult.Rejected)
            val rejection = result as VisualExecutionResult.Rejected
            assertEquals(VisualExecutionRejection.INVALID_PROGRAM, rejection.reason)
            assertTrue(rejection.diagnostics.length <= FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)
        }
    }

    @Test
    fun infiniteLoopIsInterruptedAndTheNextProgramExecutes() = runBlocking {
        val result = engine.execute(request("while (true) {}"))
        assertTrue("$result", result is VisualExecutionResult.Rejected)
        assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, (result as VisualExecutionResult.Rejected).reason)
        val next = engine.execute(request("return 21 * 2;"))
        assertEquals(VisualExecutionResult.Computed("42"), next)
    }

    @Test
    fun memoryBudgetRejectsAllocationAndTheWorkerRecovers() = runBlocking {
        val result = engine.execute(request("return new ArrayBuffer(64 * 1024 * 1024);"))
        assertTrue("$result", result is VisualExecutionResult.Rejected)
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, (result as VisualExecutionResult.Rejected).reason)
        assertEquals(VisualExecutionResult.Computed("42"), engine.execute(request("return 42;")))
    }

    @Test
    fun wireDefaultsPreserveScenesAndProgramResultsRoundTrip() {
        val program = request("return 1;")
        assertEquals(program, VisualJsProtocol.decodeRequest(VisualJsProtocol.encodeRequest(program)))
        val scene = program.copy(executionMode = VisualExecutionMode.SCENE)
        val sceneEnvelope = VisualJsProtocol.encodeRequest(scene)
        assertFalse(JSONObject(String(sceneEnvelope, Charsets.UTF_8))
            .getJSONObject("programJson").has("executionMode"))
        assertEquals(scene, VisualJsProtocol.decodeRequest(sceneEnvelope))
        val value = VisualExecutionResult.Computed("""{"answer":[42,null]}""")
        assertEquals(value, VisualJsProtocol.decodeResult(VisualJsWire.RESULT_OK,
            VisualJsProtocol.encodeResult(value), null))
        val unknown = JSONObject(String(sceneEnvelope, Charsets.UTF_8)).apply {
            getJSONObject("programJson").put("executionMode", "UNRECOGNIZED")
        }
        val failure = runCatching { VisualJsProtocol.decodeRequest(unknown.toString().toByteArray()) }.exceptionOrNull()
        assertTrue("$failure", failure is VisualJsProtocolException)
        assertTrue(VisualJsProtocol.encodeResult(value).size < VisualExecutionBudget.MAX_TRANSACTION_JSON_BYTES)
    }
}
