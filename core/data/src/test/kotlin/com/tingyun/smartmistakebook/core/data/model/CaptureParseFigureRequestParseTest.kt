package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureParseInput
import com.tingyun.smartmistakebook.core.model.CaptureParseOutput
import com.tingyun.smartmistakebook.core.model.CaptureSourceAssetRef
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationDecision
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-02（4E）：CAPTURE_PARSE 需要图时返回**同型 proposal**，parser 接受并严格校验。
 *
 * 覆盖：转写选 IMAGE_MODEL、转写选 JS（仅记录不执行）、NO_FIGURE、NEED_SOURCE/UNAVAILABLE、
 * 绑定源页/裁切范围/草稿修订、不把源码塞正文/图片 URI、严格拒收（未知键/缺 method/
 * purpose 越界/缺 generation/裁切越界/超量）、既有 figure(schema) 路径回归。
 */
class CaptureParseFigureRequestParseTest {

    private val sourceSha = "b".repeat(64)

    private fun input(
        selectedRegion: NormalizedSourceRegion? = null,
    ): CaptureParseInput = CaptureParseInput(
        draftId = "draft-1",
        origin = CaptureAssessmentOrigin.LIBRARY,
        basisRevisionNumber = 2,
        sourceAssets = listOf(
            CaptureSourceAssetRef(
                assetId = "asset-1",
                sha256 = sourceSha,
                width = 1000,
                height = 1400,
                pageIndex = 0,
                selectedRegion = selectedRegion,
            ),
        ),
        assessmentRequestId = "req-1",
    )

    private fun parse(payload: String, input: CaptureParseInput = input()): CaptureParseOutput =
        OpenAiModelTaskAdapters.parse(Json.parseToJsonElement(payload).jsonObject, input, "model-1")
            as CaptureParseOutput

    @Test
    fun `transcription picks image model and the reference binds page crop and revision`() {
        val output = parse(
            """
            {"title":"函数题","blocks":[
              {"type":"paragraph","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":0.4},
               "writingLayer":"PRINTED","markdown":"已知函数 f(x)。"},
              {"type":"figure_request","pageIndex":0,"region":{"left":0.1,"top":0.4,"right":0.9,"bottom":0.9},
               "writingLayer":"DIAGRAM","confidence":0.72,"alternativeText":"函数 f(x) 的图像",
               "generation":{"decision":"GENERATE","method":"IMAGE_MODEL","reason":"原图函数曲线无法结构化",
                 "fidelity":"FAITHFUL","imageOperation":"EDIT","imagePrompt":"重绘该区域内的函数图像与坐标轴"}}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, output.capturedDocument.figureReferences.size)
        val reference = output.capturedDocument.figureReferences.single()
        // 绑定源页/裁切范围/草稿修订：全部取自请求侧事实，不取信模型回填。
        assertEquals("asset-1", reference.sourceAssetId)
        assertEquals(0, reference.sourcePageIndex)
        assertEquals(NormalizedSourceRegion(0.1, 0.4, 0.9, 0.9), reference.sourceRegion)
        assertEquals(sourceSha, reference.sourceImageSha256)
        assertEquals(2, reference.basisRevisionNumber)
        assertEquals(FigureGenerationDecision.GENERATE, reference.proposal.decision)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, reference.proposal.method)
        assertEquals("EDIT", reference.proposal.imageOperation?.name)
        assertEquals("重绘该区域内的函数图像与坐标轴", reference.proposal.imagePrompt)
        // 图形通过类型化引用进题目文档：正文只落占位说明，不落源码/提示词/URI。
        val placeholder = output.capturedDocument.document.blocks.last() as ContentBlock.Paragraph
        assertEquals("图示：函数 f(x) 的图像", placeholder.markdown)
        assertFalse(placeholder.markdown.contains("重绘该区域内的函数图像与坐标轴"))
        assertFalse(placeholder.markdown.contains("data:"))
        assertFalse(placeholder.markdown.contains("http"))
    }

    @Test
    fun `transcription picking js is recorded but never executed`() {
        val output = parse(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"受力分析图",
               "generation":{"decision":"GENERATE","method":"JS","reason":"可程序化重建",
                 "program":"renderScene({kind:'force'});"}}
            ]}
            """.trimIndent(),
        )

        val reference = output.capturedDocument.figureReferences.single()
        assertEquals(FigureGenerationMethod.JS, reference.proposal.method)
        assertEquals("renderScene({kind:'force'});", reference.proposal.program)
        // 仅记录：源码只在类型化引用里，不进正文。
        val placeholder = output.capturedDocument.document.blocks.single() as ContentBlock.Paragraph
        assertFalse(placeholder.markdown.contains("renderScene"))
    }

    @Test
    fun `no figure decision is accepted without payload`() {
        val output = parse(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"疑似配图，实际不承载信息",
               "generation":{"decision":"NO_FIGURE","reason":"该题不依赖图"}}
            ]}
            """.trimIndent(),
        )

        val reference = output.capturedDocument.figureReferences.single()
        assertEquals(FigureGenerationDecision.NO_FIGURE, reference.proposal.decision)
        assertEquals(null, reference.proposal.method)
        assertEquals(null, reference.proposal.imagePrompt)
    }

    @Test
    fun `need source decision is accepted without payload`() {
        val output = parse(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"被遮挡的电路图",
               "generation":{"decision":"NEED_SOURCE","reason":"源图被遮挡，先补拍"}}
            ]}
            """.trimIndent(),
        )

        assertEquals(
            FigureGenerationDecision.NEED_SOURCE,
            output.capturedDocument.figureReferences.single().proposal.decision,
        )
    }

    @Test
    fun `unknown key in generation object is rejected`() {
        assertInvalid(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"图",
               "generation":{"decision":"GENERATE","method":"IMAGE_MODEL","policyVersion":"x",
                 "imageOperation":"EDIT","imagePrompt":"p"}}
            ]}
            """.trimIndent(),
        )
    }

    @Test
    fun `generate without method is rejected`() {
        assertInvalid(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"图",
               "generation":{"decision":"GENERATE","imageOperation":"EDIT","imagePrompt":"p"}}
            ]}
            """.trimIndent(),
        )
    }

    @Test
    fun `purpose outside transcription is rejected`() {
        assertInvalid(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"图",
               "generation":{"decision":"GENERATE","purpose":"EXPLANATION","method":"IMAGE_MODEL",
                 "imageOperation":"EDIT","imagePrompt":"p"}}
            ]}
            """.trimIndent(),
        )
    }

    @Test
    fun `figure request without generation object is rejected`() {
        assertInvalid(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"图"}
            ]}
            """.trimIndent(),
        )
    }

    @Test
    fun `region outside the requested crop is rejected`() {
        val crop = NormalizedSourceRegion(0.0, 0.0, 1.0, 0.5)
        assertInvalid(
            """
            {"title":"t","blocks":[
              {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.6,"right":1.0,"bottom":0.9},
               "writingLayer":"DIAGRAM","alternativeText":"图",
               "generation":{"decision":"GENERATE","method":"IMAGE_MODEL",
                 "imageOperation":"EDIT","imagePrompt":"p"}}
            ]}
            """.trimIndent(),
            input = input(selectedRegion = crop),
        )
    }

    @Test
    fun `more figure references than the document budget are rejected`() {
        val block = """
            {"type":"figure_request","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":1.0},
             "writingLayer":"DIAGRAM","alternativeText":"图",
             "generation":{"decision":"GENERATE","method":"IMAGE_MODEL",
               "imageOperation":"EDIT","imagePrompt":"p"}}
        """.trimIndent().replace("\n", "")
        assertInvalid("""{"title":"t","blocks":[${List(9) { block }.joinToString(",")}]}""")
    }

    @Test
    fun `reconstructable figure with schema still parses without references`() {
        val output = parse(
            """
            {"title":"t","blocks":[
              {"type":"paragraph","pageIndex":0,"region":{"left":0.0,"top":0.0,"right":1.0,"bottom":0.5},
               "writingLayer":"PRINTED","markdown":"题干"},
              {"type":"figure","pageIndex":0,"region":{"left":0.0,"top":0.5,"right":1.0,"bottom":1.0},
               "writingLayer":"DIAGRAM","alternativeText":"一次函数图像",
               "schema":{"type":"cartesian",
                 "xAxis":{"minimum":-2,"maximum":2,"label":"x"},
                 "yAxis":{"minimum":-1,"maximum":3,"label":"y"},
                 "polylines":[{"points":[{"x":-1,"y":0},{"x":1,"y":2}]}]}}
            ]}
            """.trimIndent(),
        )

        assertTrue(output.capturedDocument.figureReferences.isEmpty())
        assertTrue(
            output.capturedDocument.document.blocks.last() is ContentBlock.Figure,
        )
    }

    private fun assertInvalid(payload: String, input: CaptureParseInput = input()) {
        try {
            parse(payload, input)
        } catch (expected: InvalidModelResponseException) {
            return
        }
        throw AssertionError("expected InvalidModelResponseException but the payload was accepted")
    }
}
