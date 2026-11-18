package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TutorGeneralAgentWireTest {
    private val input = TutorLobbyInput("conversation", 1, "查看错题", toolDeclarations = TutorToolName.entries)

    @Test fun bothRoutesPreserveJavascriptScriptAndState() {
        val arguments = buildJsonObject {
            put("rationale", "根据应用实际结果处理")
            put("terms", buildJsonArray {})
            put("script", "const context = agent.callTool('APP_CONTEXT', {}); return context.data;")
            put("stateJson", "{\"offset\":0}")
        }
        val native = buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject {
                put("message", buildJsonObject {
                    put("content", JsonNull)
                    put("tool_calls", buildJsonArray { add(buildJsonObject {
                        put("id", "call-js"); put("type", "function")
                        put("function", buildJsonObject { put("name", "RUN_JAVASCRIPT"); put("arguments", arguments.toString()) })
                    }) })
                })
            }) })
        }
        val nativeOutput = OpenAiModelProtocol.parseResponse(native.toString(), input, "model") as TutorToolRequestsOutput
        val envelopeArguments = JsonObject(arguments + ("tool" to JsonPrimitive("RUN_JAVASCRIPT")))
        val envelope = buildJsonObject {
            put("intentDecision", parseObject("""{"intent":"MISTAKE_NOTEBOOK_LOOKUP","confidence":0.9,"explicitActionRequest":true,"memoryPreference":"UNCHANGED","requestedLocalCapability":"NONE","lookupTerms":[]}"""))
            put("toolRequests", buildJsonArray { add(envelopeArguments) })
        }
        val envelopeOutput = OpenAiModelTaskAdapters.parse(envelope, input, "model") as TutorToolRequestsOutput
        assertEquals(nativeOutput.calls, envelopeOutput.calls)
        assertEquals(arguments["script"]!!.jsonPrimitive.content, nativeOutput.calls.single().script)
        assertEquals("{\"offset\":0}", nativeOutput.calls.single().stateJson)
    }

    @Test fun nullableOptionalParametersBecomeAbsentAndUnknownParametersAreRejected() {
        val args = parseObject("""{"rationale":"翻页查看错题","terms":[],"parameters":{"subject":null,"offset":"30","limit":"10"}}""")
        val call = args.toTutorToolCall(TutorToolName.NOTEBOOK_READ)
        assertEquals(mapOf("offset" to "30", "limit" to "10"), call.parameters)
        assertTrue(runCatching { parseObject("""{"rationale":"查看","parameters":{"unknown":null}}""").toTutorToolCall(TutorToolName.APP_CONTEXT) }.isFailure)
    }

    @Test fun nativeSchemasAreStrictAndIncludeTheSharedParameterCarriers() {
        val body = parseObject(OpenAiModelProtocol.requestBody("model", input, emptyList(), enableNativeTools = true))
        val functions = body["tools"]!!.jsonArray.map { it.jsonObject["function"]!!.jsonObject }
        functions.forEach { assertStrict(it["parameters"]!!.jsonObject) }
        val js = functions.single { it["name"]!!.jsonPrimitive.content == "RUN_JAVASCRIPT" }["parameters"]!!.jsonObject["properties"]!!.jsonObject
        assertTrue(js.containsKey("script"))
        assertEquals(
            setOf("string", "null"),
            js["stateJson"]!!.jsonObject["anyOf"]!!.jsonArray
                .map { it.jsonObject.getValue("type").jsonPrimitive.content }.toSet(),
        )
        val notebook = functions.single { it["name"]!!.jsonPrimitive.content == "NOTEBOOK_READ" }["parameters"]!!.jsonObject["properties"]!!.jsonObject
        val parameters = notebook["parameters"]!!.jsonObject["properties"]!!.jsonObject
        assertEquals(TutorToolName.NOTEBOOK_READ.parameterNames(), parameters.keys)
    }

    private fun assertStrict(schema: JsonObject) {
        val properties = schema["properties"]!!.jsonObject
        assertEquals(properties.keys, schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(false, schema["additionalProperties"]!!.jsonPrimitive.boolean)
        properties.values.filter { it.jsonObject["type"] == JsonPrimitive("object") }.forEach { assertStrict(it.jsonObject) }
    }
}
