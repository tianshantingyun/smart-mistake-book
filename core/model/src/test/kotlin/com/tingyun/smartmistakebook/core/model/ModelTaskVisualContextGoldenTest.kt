package com.tingyun.smartmistakebook.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-12 扩测（视觉契约 §8:186 / 准入清单 :200；计划任务 3 第三组）：schema 18
 * `TutorVisualContext` 进请求的**字节级 golden 扩测**。既有 `ModelTaskFingerprintStabilityTest`
 * 42 例与本包 `TutorVisualContextFingerprintTest` 不动；本文件补它们没有的三样：
 *
 * 1. **旧 payload 逐字节往返**：v17 行（visualContext 键不存在）decode → re-encode 必须与
 *    原串逐字节一致——Plan/Respond 两半（指纹稳定测试只钉过 Respond 半边的 v17 行）；
 * 2. **逻辑操作投影的字节级 golden**：`ModelTaskLogicalOperationFingerprint` 的负载 =
 *    kind + "\n" + `logicalOperationJson` 编码 − 既定 strip 链。null context **不出现新键**
 *    由"当前投影 == 手工合成的同配置编码"逐字节钉住（投影链漂移或 null 落键都会破坏它）；
 *    非空 context 是真载体，指纹必须变（防假稳定）；
 * 3. **非空载体的精确键形状**：`"visualContext":{...}` 的键序与值形状按声明序逐字节断言
 *    （含 inner-null 形状——inner 键由 `explicitNulls=true` 落键，是 v18 行读回一致性的
 *    一部分，改动必须是自觉的 schema 决定）。
 *
 * golden 载体说明（如实登记）：仓内没有可导出的**真实旧库行**（无运行库），v17 形状用
 * "当前编码器 + `@EncodeDefault(NEVER)` 不落键"合成——与既有 42 例同一手法；合成行 + 往返
 * 逐字节 + 键缺席三方断言共同钉住"当前代码对旧输入的编码输出与 golden 逐字节一致"。
 */
class ModelTaskVisualContextGoldenTest {

    // ---- 1. 旧 payload（schema 17 行）逐字节往返 ----

    @Test
    fun aSchema17RespondRowReencodesByteForByteAcrossTheContextFieldUpgrade() {
        val v17 = ModelTaskRequest(
            schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
            requestId = "respond:v17-golden",
            input = respondInput(),
            occurredAtEpochMillis = 1_000,
        )
        // v17 时代编码器不认识 visualContext；NEVER 语义下当前编码器对 null context 也不落键
        // ——所以这份编码就是 v17 行的字节形状。
        val legacyJson = ModelTaskCodec.encodeRequest(v17)
        assertFalse("null context 不得落键：$legacyJson", legacyJson.contains("\"visualContext\""))

        val decoded = ModelTaskCodec.decodeRequest(legacyJson)
        val reEncoded = ModelTaskCodec.encodeRequest(decoded)

        assertEquals("v17 行读回再编码必须逐字节一致", legacyJson, reEncoded)
        assertEquals(17, decoded.schemaVersion)
        assertEquals(ModelTaskFingerprint.of(v17), ModelTaskFingerprint.of(decoded))
    }

    @Test
    fun aSchema17PlanRowReencodesByteForByteAcrossTheContextFieldUpgrade() {
        val v17 = ModelTaskRequest(
            schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
            requestId = "plan:v17-golden",
            input = planInput(),
            occurredAtEpochMillis = 1_000,
        )
        val legacyJson = ModelTaskCodec.encodeRequest(v17)
        assertFalse("null context 不得落键（Plan 半边）：$legacyJson", legacyJson.contains("\"visualContext\""))

        val decoded = ModelTaskCodec.decodeRequest(legacyJson)
        val reEncoded = ModelTaskCodec.encodeRequest(decoded)

        assertEquals("v17 Plan 行读回再编码必须逐字节一致", legacyJson, reEncoded)
        assertEquals(ModelTaskFingerprint.of(v17), ModelTaskFingerprint.of(decoded))
        assertEquals(
            "Plan 的 v17 行逻辑指纹也必须与升级后一致",
            ModelTaskLogicalOperationFingerprint.of(planInput()),
            ModelTaskLogicalOperationFingerprint.of(decoded.input),
        )
    }

    // ---- 2. 逻辑操作投影的字节级 golden（null context 不落键） ----

    @Test
    fun theRespondOperationProjectionDropsTheNullContextKeyByteForByte() {
        val input = respondInput()

        val expectedPayload = "TUTOR_RESPOND\n" + respondProjectionWithoutContext()
        assertEquals(
            "Respond 逻辑投影必须逐字节等于不含 visualContext 键的 golden",
            sha256Hex(expectedPayload),
            ModelTaskLogicalOperationFingerprint.of(input),
        )
    }

    @Test
    fun thePlanOperationProjectionDropsTheNullContextKeyByteForByte() {
        val input = planInput()

        val expectedPayload = "TUTOR_PLAN\n" + planProjectionWithoutContext()
        assertEquals(
            "Plan 逻辑投影必须逐字节等于不含 visualContext 键的 golden",
            sha256Hex(expectedPayload),
            ModelTaskLogicalOperationFingerprint.of(input),
        )
    }

    @Test
    fun aRealVisualContextChangesBothFingerprintsAgainstTheNullGolden() {
        val withContext = respondInput(context())

        assertNotEquals(
            "带 context 是另一次逻辑输入（防假稳定）",
            ModelTaskLogicalOperationFingerprint.of(respondInput()),
            ModelTaskLogicalOperationFingerprint.of(withContext),
        )
        assertNotEquals(
            ModelTaskFingerprint.of(
                ModelTaskRequest(requestId = "r1", input = respondInput(), occurredAtEpochMillis = 1_000),
            ),
            ModelTaskFingerprint.of(
                ModelTaskRequest(requestId = "r1", input = withContext, occurredAtEpochMillis = 1_000),
            ),
        )
    }

    // ---- 3. 非空载体的精确键形状（声明序 + inner-null 形状） ----

    @Test
    fun aNonNullContextLandsItsKeyWithTheExactDeclaredShape() {
        val request = ModelTaskRequest(
            requestId = "respond:v18-golden",
            input = respondInput(context()),
            occurredAtEpochMillis = 1_000,
        )
        val encoded = ModelTaskCodec.encodeRequest(request)

        // TutorVisualContext 字段声明序：capabilityVersion / sourceJs / stateJson / diagnostics。
        val expectedFragment = "\"visualContext\":{\"capabilityVersion\":\"" +
            FigureGenerationPolicyVersion.VALUE + "\"" +
            ",\"sourceJs\":\"const visual = {};\"" +
            ",\"stateJson\":\"{\\\"step\\\":1}\"" +
            ",\"diagnostics\":\"上一份程序在 measure 阶段被截断\"}"
        assertTrue(
            "非空 context 的键形状必须逐字符合 golden：$encoded",
            encoded.contains(expectedFragment),
        )
        // 往返与两条指纹同步稳定。
        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(request.input, decoded.input)
        assertEquals(ModelTaskFingerprint.of(request), ModelTaskFingerprint.of(decoded))
        assertEquals(
            ModelTaskLogicalOperationFingerprint.of(request.input),
            ModelTaskLogicalOperationFingerprint.of(decoded.input),
        )
    }

    @Test
    fun anInnerNullContextFieldKeepsItsExactShapeAndBothFingerprintsStable() {
        // inner 字段（sourceJs/diagnostics）为 null 时按 explicitNulls=true 落 null 键：
        // 这是 v18 行读回一致性的形状，改动它 = 另一次 schema 决定（golden 在这里立碑）。
        val context = TutorVisualContext(
            capabilityVersion = FigureGenerationPolicyVersion.VALUE,
            sourceJs = null,
            stateJson = "{\"step\":1}",
            diagnostics = null,
        )
        val request = ModelTaskRequest(
            requestId = "respond:v18-inner-null",
            input = respondInput(context),
            occurredAtEpochMillis = 1_000,
        )
        val encoded = ModelTaskCodec.encodeRequest(request)

        val expectedFragment = "\"visualContext\":{\"capabilityVersion\":\"" +
            FigureGenerationPolicyVersion.VALUE + "\"" +
            ",\"sourceJs\":null" +
            ",\"stateJson\":\"{\\\"step\\\":1}\"" +
            ",\"diagnostics\":null}"
        assertTrue(
            "inner-null 形状必须逐字符合 golden：$encoded",
            encoded.contains(expectedFragment),
        )
        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(request.input, decoded.input)
        assertEquals(ModelTaskFingerprint.of(request), ModelTaskFingerprint.of(decoded))
        assertEquals(
            ModelTaskLogicalOperationFingerprint.of(request.input),
            ModelTaskLogicalOperationFingerprint.of(decoded.input),
        )
    }

    // ---- 夹具与 golden 合成 ----

    private fun respondInput(context: TutorVisualContext? = null) = TutorRespondInput(
        sessionId = "session-golden",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-golden",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        responseOrdinal = 1,
        cycleOrdinal = 1,
        turnOrdinal = 1,
        studentMessage = "这一步怎么来的？",
        priorMessages = emptyList(),
        requestedMove = null,
        visualContext = context,
    )

    private fun planInput(context: TutorVisualContext? = null) = TutorPlanInput(
        sessionId = "session-plan-golden",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-plan-golden",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        visualContext = context,
    )

    private fun context() = TutorVisualContext(
        capabilityVersion = FigureGenerationPolicyVersion.VALUE,
        sourceJs = "const visual = {};",
        stateJson = "{\"step\":1}",
        diagnostics = "上一份程序在 measure 阶段被截断",
    )

    /**
     * 与 `ModelTasks.kt` 的 `logicalOperationJson` 同配置的编码器 + operationPayload 对
     * Respond 输入的全部生效 strip——手工合成"null context 应有的那份负载"。
     * 注意 `studentImageAssetRefs` **不在** strip 链（v5 期键，两边都落），合成就不能抹它。
     */
    private fun respondProjectionWithoutContext(): String = projectionJson
        .encodeToString(ModelTaskInput.serializer(), respondInput())
        .replace(",\"toolDeclarations\":[]", "")
        .replace(",\"toolRoundResults\":[]", "")
        .replace(",\"priorDigest\":null", "")
        .replace(",\"boundQuestionCandidates\":[]", "")
        .replace(",\"knownRoundQuestion\":null", "")
        .replace(",\"knowledgeCodes\":[]", "")
        .replace(",\"attachedQuestion\":null", "")

    /** Plan 输入的生效 strip：`withoutEmptyKnowledgeCodes` + `withoutEmptyPlanToolCarrier` 三键。 */
    private fun planProjectionWithoutContext(): String = projectionJson
        .encodeToString(ModelTaskInput.serializer(), planInput())
        .replace(",\"knowledgeCodes\":[]", "")
        .replace(",\"toolDeclarations\":[]", "")
        .replace(",\"toolRoundResults\":[]", "")
        .replace(",\"teachingReferencesLoadFailed\":false", "")

    private val projectionJson = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
