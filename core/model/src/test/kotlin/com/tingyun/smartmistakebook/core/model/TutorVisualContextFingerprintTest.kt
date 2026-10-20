package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：有界 `TutorVisualContext`（T3，请求 schema 18）。
 *
 * 消灭的具体失败：把上一份程序/状态回喂给下一轮时，要么没有载体、要么把整段历史塞进 prompt。
 * 本类型只携带**当前题最近一份**程序源码（64 KiB）、state（16 KiB）与截断诊断（2 KiB）；
 * `@EncodeDefault(NEVER)` 保证 null 不落键——旧 schema 17 行的指纹与编码形状逐位不变，
 * 非空 context 则是另一次逻辑输入（指纹必须变）。
 */
class TutorVisualContextFingerprintTest {

    private fun respondInput(context: TutorVisualContext? = null) = TutorRespondInput(
        sessionId = "session-stable",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-1",
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
        sessionId = "session-plan-stable",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-plan",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        visualContext = context,
    )

    private fun context(
        sourceJs: String? = "const visual = { prepare() { return []; } };",
        stateJson: String? = "{\"step\":1}",
        diagnostics: String? = "上一份程序在 measure 阶段被截断",
    ) = TutorVisualContext(
        capabilityVersion = FigureGenerationPolicyVersion.VALUE,
        sourceJs = sourceJs,
        stateJson = stateJson,
        diagnostics = diagnostics,
    )

    @Test
    fun theRequestSchemaIsEighteen() {
        assertEquals(18, ModelTaskRequest.CURRENT_SCHEMA_VERSION)
        assertEquals(18, ModelTaskRequest.TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION)
    }

    @Test
    fun anEmptyContextKeepsTheEncodingAndTheOperationFingerprintUnchanged() {
        // 旧行（schema 17）编码里没有 visualContext 键；升级后 decode 取默认 null，
        // 两个指纹校验点都必须仍与存库值一致。
        val v17 = ModelTaskRequest(
            schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
            requestId = "respond:v17-empty-context",
            input = respondInput(),
            occurredAtEpochMillis = 1_000,
        )
        val encoded = ModelTaskCodec.encodeRequest(v17)
        assertFalse("null context 不得落键：$encoded", encoded.contains("\"visualContext\""))

        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(17, decoded.schemaVersion)
        assertEquals(ModelTaskFingerprint.of(v17), ModelTaskFingerprint.of(decoded))
        assertEquals(
            ModelTaskLogicalOperationFingerprint.of(v17.input),
            ModelTaskLogicalOperationFingerprint.of(respondInput()),
        )
    }

    @Test
    fun aRealContextChangesBothFingerprints() {
        val withContext = respondInput(context())

        assertNotEquals(
            "带 context 是另一次逻辑输入：重放不能命中旧请求",
            ModelTaskLogicalOperationFingerprint.of(respondInput()),
            ModelTaskLogicalOperationFingerprint.of(withContext),
        )
        assertNotEquals(
            ModelTaskFingerprint.of(
                ModelTaskRequest(
                    requestId = "respond:plain",
                    input = respondInput(),
                    occurredAtEpochMillis = 1_000,
                ),
            ),
            ModelTaskFingerprint.of(
                ModelTaskRequest(
                    requestId = "respond:context",
                    input = withContext,
                    occurredAtEpochMillis = 1_000,
                ),
            ),
        )
    }

    @Test
    fun planAndRespondBothCarryTheContextThroughTheCodec() {
        listOf(
            planInput(context()) to { decoded: ModelTaskInput ->
                (decoded as TutorPlanInput).visualContext
            },
            respondInput(context()) to { decoded: ModelTaskInput ->
                (decoded as TutorRespondInput).visualContext
            },
        ).forEach { (input, read) ->
            val request = ModelTaskRequest(
                requestId = "context-row",
                input = input,
                occurredAtEpochMillis = 1_000,
            )
            val encoded = ModelTaskCodec.encodeRequest(request)

            assertTrue("非空 context 必须落键：$encoded", encoded.contains("\"visualContext\""))
            val decoded = ModelTaskCodec.decodeRequest(encoded)
            assertEquals(context(), read(decoded.input))
            assertEquals(ModelTaskFingerprint.of(request), ModelTaskFingerprint.of(decoded))
            assertEquals(
                ModelTaskLogicalOperationFingerprint.of(request.input),
                ModelTaskLogicalOperationFingerprint.of(decoded.input),
            )
        }
    }

    @Test
    fun aLegacySchemaRowCannotCarryAVisualContext() {
        listOf(
            runCatching {
                ModelTaskRequest(
                    schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
                    requestId = "respond:legacy-with-visual-context",
                    input = respondInput(context()),
                    occurredAtEpochMillis = 1_000,
                )
            },
            runCatching {
                ModelTaskRequest(
                    schemaVersion = ModelTaskRequest.IMAGE_CLASSIFY_SOURCE_SHA_SCHEMA_VERSION,
                    requestId = "plan:legacy-with-visual-context",
                    input = planInput(context()),
                    occurredAtEpochMillis = 1_000,
                )
            },
        ).forEach { rejected ->
            assertTrue(
                "旧 schema 行带着新字段是自相矛盾的形状",
                rejected.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    @Test
    fun theContextBudgetsAreEnforcedInUtf8Bytes() {
        // 恰好到预算可以，超过一个字节就拒绝（不截断、不转义）。
        TutorVisualContext(
            capabilityVersion = "v1",
            sourceJs = "a".repeat(TutorVisualContext.MAX_SOURCE_JS_UTF8_BYTES),
        )
        TutorVisualContext(
            capabilityVersion = "v1",
            stateJson = "s".repeat(TutorVisualContext.MAX_STATE_UTF8_BYTES),
        )
        TutorVisualContext(
            capabilityVersion = "v1",
            diagnostics = "诊".repeat(TutorVisualContext.MAX_DIAGNOSTICS_CHARS),
        )

        listOf(
            runCatching {
                TutorVisualContext(
                    capabilityVersion = "v1",
                    sourceJs = "a".repeat(TutorVisualContext.MAX_SOURCE_JS_UTF8_BYTES + 1),
                )
            },
            runCatching {
                TutorVisualContext(
                    capabilityVersion = "v1",
                    stateJson = "a".repeat(TutorVisualContext.MAX_STATE_UTF8_BYTES + 1),
                )
            },
            runCatching {
                TutorVisualContext(
                    capabilityVersion = "v1",
                    diagnostics = "诊".repeat(TutorVisualContext.MAX_DIAGNOSTICS_CHARS + 1),
                )
            },
        ).forEach { rejected ->
            assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun anEmptyContextObjectIsRejected() {
        val rejected = runCatching { TutorVisualContext(capabilityVersion = "v1") }
        assertTrue("空载体应当传 null，而不是一个空 context", rejected.exceptionOrNull() is IllegalArgumentException)
    }
}
