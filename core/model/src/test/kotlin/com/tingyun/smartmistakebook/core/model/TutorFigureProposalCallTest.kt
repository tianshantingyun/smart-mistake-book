package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：`GENERATE_FIGURE` 的**统一 proposal 载荷**（T3）。
 *
 * 消灭的具体失败：`TutorToolCall` 此前只有 `kind`/`description`，模型无法表达"用哪种方法、
 * 哪种图像操作、具体 imagePrompt"；适配层只能按 kind 固定后端。现在：
 * - `method` 只能是被真实接通的 `IMAGE_MODEL`（JS 运行库未过门 → 结构性拒绝）；
 * - `IMAGE_MODEL` 必须带 `imageOperation`（EDIT/GENERATE）与具体 `imagePrompt`；
 * - 旧调用（只有 kind/description）仍可用，但适配层会构造一个**显式** IMAGE_MODEL proposal，
 *   不把缺失当成"再默认一个其它方法"。
 */
class TutorFigureProposalCallTest {

    private fun figureCall(
        kind: TutorFigureKind = TutorFigureKind.REDRAW_PROBLEM,
        description: String = "重绘题面去手写",
        method: FigureGenerationMethod? = null,
        program: String? = null,
        operation: FigureImageOperation? = null,
        prompt: String? = null,
    ) = TutorToolCall(
        tool = TutorToolName.GENERATE_FIGURE,
        rationale = "图形是这道题的理解关键",
        figureKind = kind,
        figureDescription = description,
        figureMethod = method,
        figureProgram = program,
        figureImageOperation = operation,
        figureImagePrompt = prompt,
    )

    @Test
    fun imageModelEditIsAcceptedAndMapsTheKindToItsPurpose() {
        val call = figureCall(
            method = FigureGenerationMethod.IMAGE_MODEL,
            operation = FigureImageOperation.EDIT,
            prompt = "保留题干文字、标注和单位，只清除手写。",
        )

        val proposal = requireNotNull(call.toFigureGenerationProposal())
        assertEquals(FigureGenerationDecision.GENERATE, proposal.decision)
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, proposal.method)
        assertEquals(FigureImageOperation.EDIT, proposal.imageOperation)
        assertEquals("保留题干文字、标注和单位，只清除手写。", proposal.imagePrompt)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, proposal.purpose)
        assertNull(proposal.program)
        assertEquals(FigureGenerationPolicyVersion.VALUE, proposal.policyVersion)
    }

    @Test
    fun imageModelGenerateIsAcceptedForAnExplanationFigure() {
        val call = figureCall(
            kind = TutorFigureKind.GENERATE_PROCESS,
            description = "数轴标注导数正负区间",
            method = FigureGenerationMethod.IMAGE_MODEL,
            operation = FigureImageOperation.GENERATE,
            prompt = "画一条数轴，标出导数为正、负的区间。",
        )

        val proposal = requireNotNull(call.toFigureGenerationProposal())
        assertEquals(FigureGenerationMethod.IMAGE_MODEL, proposal.method)
        assertEquals(FigureImageOperation.GENERATE, proposal.imageOperation)
        assertEquals(FigureGenerationPurpose.EXPLANATION, proposal.purpose)
    }

    @Test
    fun aLegacyCallStillBecomesAnExplicitImageModelProposal() {
        // 旧 wire（只有 kind/description）不携带 method；适配层必须显式补上真实方法，
        // 而不是让下游再猜一次后端。
        val proposal = requireNotNull(figureCall().toFigureGenerationProposal())

        assertEquals(FigureGenerationMethod.IMAGE_MODEL, proposal.method)
        assertEquals(FigureImageOperation.EDIT, proposal.imageOperation)
        assertEquals("重绘题面去手写", proposal.imagePrompt)
        assertEquals(FigureGenerationPurpose.SOURCE_RECONSTRUCTION, proposal.purpose)
    }

    @Test
    fun jsIsRejectedBecauseTheRuntimeIsNotAvailable() {
        val rejected = runCatching {
            figureCall(
                method = FigureGenerationMethod.JS,
                operation = FigureImageOperation.GENERATE,
                prompt = "x",
            )
        }

        assertTrue(
            "本波次没有 JS 运行库，JS 方法必须在契约层拒绝：${rejected.exceptionOrNull()}",
            rejected.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun aJsProgramIsRejectedWithOrWithoutADeclaredMethod() {
        listOf<() -> TutorToolCall>(
            { figureCall(program = "const visual = {}") },
            {
                figureCall(
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    operation = FigureImageOperation.EDIT,
                    prompt = "x",
                    program = "const visual = {}",
                )
            },
        ).forEach { call ->
            val rejected = runCatching { call() }
            assertTrue(
                "JS program 不得进入未接通的执行面：${rejected.exceptionOrNull()}",
                rejected.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    @Test
    fun imageModelWithoutAnOperationOrPromptIsRejected() {
        listOf<() -> TutorToolCall>(
            { figureCall(method = FigureGenerationMethod.IMAGE_MODEL) },
            { figureCall(method = FigureGenerationMethod.IMAGE_MODEL, prompt = "画图") },
            {
                figureCall(
                    method = FigureGenerationMethod.IMAGE_MODEL,
                    operation = FigureImageOperation.EDIT,
                )
            },
        ).forEach { call ->
            assertTrue(
                "IMAGE_MODEL 必须同时声明 imageOperation 与 imagePrompt",
                runCatching { call() }.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    @Test
    fun anOperationWithoutAPromptIsRejectedEvenWithoutMethod() {
        val rejected = runCatching {
            figureCall(operation = FigureImageOperation.GENERATE)
        }

        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun anOverBudgetImagePromptIsRejectedByUtf8Bytes() {
        val rejected = runCatching {
            figureCall(
                method = FigureGenerationMethod.IMAGE_MODEL,
                operation = FigureImageOperation.GENERATE,
                prompt = "长".repeat(FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES),
            )
        }

        assertTrue(
            "imagePrompt 的预算按 UTF-8 字节，不按字符：${rejected.exceptionOrNull()}",
            rejected.exceptionOrNull() is IllegalArgumentException,
        )
    }
}
