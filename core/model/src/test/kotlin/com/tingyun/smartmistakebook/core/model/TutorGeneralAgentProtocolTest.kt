package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TutorGeneralAgentProtocolTest {
    @Test fun notebookCanReadAllAndPageWithoutInventedLookupTerms() {
        val call = TutorToolCall(TutorToolName.NOTEBOOK_READ, "查看全部错题", parameters = mapOf("offset" to "30", "limit" to "30", "subject" to "数学"))
        assertTrue(call.terms.isEmpty())
        assertEquals("30", call.parameters["offset"])
        assertThrows(IllegalArgumentException::class.java) { call.copy(parameters = mapOf("limit" to "31")) }
        assertThrows(IllegalArgumentException::class.java) { call.copy(parameters = mapOf("offset" to "-1")) }
    }

    @Test fun genericJavascriptHasItsOwnScriptAndJsonStateCarriers() {
        val call = TutorToolCall(TutorToolName.RUN_JAVASCRIPT, "读取实际应用结果", script = "return agent.callTool('APP_CONTEXT', {});", stateJson = "{\"offset\":0}")
        assertEquals("{\"offset\":0}", call.stateJson)
        assertThrows(IllegalArgumentException::class.java) { call.copy(tool = TutorToolName.APP_CONTEXT) }
        assertThrows(IllegalArgumentException::class.java) { call.copy(stateJson = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { TutorToolCall(TutorToolName.SELECT_QUESTION, "选择题目") }
    }

    @Test fun defaultFieldsPreserveHistoricalToolEncoding() {
        val json = Json { encodeDefaults = true }
        val call = json.encodeToString(TutorToolCall.serializer(), TutorToolCall(TutorToolName.MASTERY_READ, "查看掌握情况"))
        assertFalse("\"parameters\"", call.contains("\"parameters\""))
        assertFalse("\"script\"", call.contains("\"script\""))
        assertFalse("\"stateJson\"", call.contains("\"stateJson\""))
        val outcome = json.encodeToString(TutorToolOutcome.serializer(), TutorToolOutcome(TutorToolName.MASTERY_READ, true, "done"))
        assertFalse("\"dataJson\"", outcome.contains("\"dataJson\""))
    }

    @Test fun structuredResultsConsumeTheRoundBudgetAndStayValidJson() {
        val outcome = TutorToolOutcome(TutorToolName.NOTEBOOK_READ, true, "items", dataJson = "{\"items\":[1,2,3]}")
        val kept = budgetTutorToolOutcomes(listOf(outcome), 100).single()
        assertEquals(Json.parseToJsonElement(outcome.dataJson!!), Json.parseToJsonElement(kept.dataJson!!))
        val large = outcome.copy(dataJson = "{\"text\":\"${"x".repeat(200)}\"}")
        val dropped = budgetTutorToolOutcomes(listOf(large), 100).single()
        assertNull(dropped.dataJson)
        assertTrue(dropped.summaryMarkdown.contains("缩小查询范围"))
        assertThrows(IllegalArgumentException::class.java) { outcome.copy(dataJson = "{") }
    }
}
