package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：生图痕迹必须能回答"用了哪种方法、为什么、哪个逻辑操作、结果状态"（T3）。
 *
 * 这些字段只在本地痕迹里（消息行 `tool_trace_json`），不进模型输入、不碰模型输入指纹；
 * 旧痕迹（只有 kind/figureId/model/generatedNow）必须原样读回。
 */
class TutorToolFigureTraceFieldsTest {

    private fun trace(figure: TutorToolFigureTrace): TutorTurnToolTrace = TutorTurnToolTrace(
        entries = listOf(
            TutorToolTraceEntry(
                tool = TutorToolName.GENERATE_FIGURE,
                ok = true,
                resultCount = null,
                figure = figure,
            ),
        ),
    )

    @Test
    fun aNewTraceRoundTripsMethodPurposeOperationAndStatus() {
        val original = trace(
            TutorToolFigureTrace(
                kind = TutorFigureKind.GENERATE_PROCESS,
                figureId = "figure-abc",
                model = "gpt-image-2",
                generatedNow = true,
                method = FigureGenerationMethod.IMAGE_MODEL,
                purpose = FigureGenerationPurpose.EXPLANATION,
                operationId = "figure-op:GENERATE_PROCESS:figure-abc",
                status = FigureGenerationStatus.GENERATED,
            ),
        )

        val decoded = requireNotNull(decodeTutorTurnToolTrace(encodeTutorTurnToolTrace(original)))
        val figure = requireNotNull(decoded.entries.single().figure)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, figure.method)
        assertEquals(FigureGenerationPurpose.EXPLANATION, figure.purpose)
        assertEquals("figure-op:GENERATE_PROCESS:figure-abc", figure.operationId)
        assertEquals(FigureGenerationStatus.GENERATED, figure.status)
    }

    @Test
    fun aLegacyTraceReadsBackWithEmptyNewFields() {
        // 批次 1/2 写下的旧行没有这些键：不能因为新增字段而让历史痕迹读不出来。
        val legacy = """
            {"entries":[{"tool":"GENERATE_FIGURE","ok":true,"figure":{"kind":"REDRAW_PROBLEM",
            "figureId":"figure-legacy","model":"gpt-image-2","generatedNow":true}}]}
        """.trimIndent()

        val decoded = requireNotNull(decodeTutorTurnToolTrace(legacy))
        val figure = requireNotNull(decoded.entries.single().figure)
        assertEquals(TutorFigureKind.REDRAW_PROBLEM, figure.kind)
        assertEquals("figure-legacy", figure.figureId)
        assertNull(figure.method)
        assertNull(figure.purpose)
        assertNull(figure.operationId)
        assertNull(figure.status)
    }

    @Test
    fun anOldShapedTraceEncodesWithoutTheNewKeys() {
        // 空载体不落键：旧形状（没有方法事实）编码后仍是旧字节形状。
        val encoded = requireNotNull(
            encodeTutorTurnToolTrace(
                trace(TutorToolFigureTrace(kind = TutorFigureKind.REDRAW_PROBLEM, figureId = "figure-1")),
            ),
        )

        assertFalse("method 空载体不得落键：$encoded", encoded.contains("\"method\""))
        assertFalse("purpose 空载体不得落键：$encoded", encoded.contains("\"purpose\""))
        assertFalse("operationId 空载体不得落键：$encoded", encoded.contains("\"operationId\""))
        assertFalse("status 空载体不得落键：$encoded", encoded.contains("\"status\""))
        assertTrue(encoded.contains("\"generatedNow\""))
    }

    @Test
    fun jsIsNotAValidTraceMethod() {
        // 痕迹记录的是**实际执行的方法**：JS 未接通，就不可能有一条 JS 执行痕迹。
        val rejected = runCatching {
            TutorToolFigureTrace(
                kind = TutorFigureKind.GENERATE_PROCESS,
                method = FigureGenerationMethod.JS,
            )
        }

        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
    }
}
