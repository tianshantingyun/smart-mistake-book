package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorRespondInput
import com.tingyun.smartmistakebook.core.model.TutorToolName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E WG-04b：统一生图规则正文注入 **TUTOR_PLAN / TUTOR_RESPOND** 的工具声明段（T3）。
 *
 * 消灭的具体失败：`GENERATE_FIGURE` 的工具描述只有"kind/description"，模型的 method/
 * imageOperation/imagePrompt 选择没有任何规则来源；而修正提示词时又容易复制出第二套
 * 决策规则。这里钉住：
 * - 声明了 GENERATE_FIGURE 时才注入（未声明不得提到它）；
 * - 注入的是 `FigurePromptAssembler` 的唯一共享正文；
 * - 只广告 IMAGE_MODEL；JS/QuickJS/program 不出现在正文里；
 * - 预算按 durable 账本口径声明（上限 + 未预读余量）并为讲题终答留位。
 */
class FigureToolPromptInjectionTest {

    private fun planInput(declarations: List<TutorToolName>) = TutorPlanInput(
        sessionId = "session-plan-1",
        draftRevisionNumber = 1,
        subject = "数学",
        questionDocument = QuestionDocument(
            id = "question-1",
            blocks = listOf(ContentBlock.Paragraph("stem", "求函数的单调区间")),
        ),
        relevantLearningEvidence = emptyList(),
        projectionIsCurrent = true,
        toolDeclarations = declarations,
    )

    private fun respondInput(declarations: List<TutorToolName>) = TutorRespondInput(
        sessionId = "session-1",
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
        toolDeclarations = declarations,
    )

    @Test
    fun planAndRespondCarryTheSameSingleFigureRuleBody() {
        val declarations = listOf(
            TutorToolName.KNOWLEDGE_READ,
            TutorToolName.GENERATE_FIGURE,
        )
        val planPrompt = OpenAiModelTaskAdapters.prompt(planInput(declarations))
        val respondPrompt = OpenAiModelTaskAdapters.prompt(respondInput(declarations))

        listOf(planPrompt, respondPrompt).forEach { prompt ->
            assertTrue(
                "必须注入唯一一份共享规则",
                prompt.contains(FigurePromptAssembler.SHARED_RULES),
            )
            assertTrue(prompt.contains("IMAGE_MODEL"))
            assertTrue(prompt.contains("imageOperation"))
            assertTrue(prompt.contains("imagePrompt"))
            assertTrue(prompt.contains("method"))
            assertTrue("kind 只映射用途：$prompt", prompt.contains("SOURCE_RECONSTRUCTION"))
            assertTrue("kind 只映射用途：$prompt", prompt.contains("EXPLANATION"))
            assertTrue("讲题预算必须为终答留位：$prompt", prompt.contains("终答"))
            assertTrue("余量未预读要如实说：$prompt", prompt.contains("未预读"))
        }
        // 两条提示词的这份规则正文逐字相同（不是各写一份）。
        assertEquals(
            planPrompt.substringAfter(FIGURE_BLOCK_HEADER),
            respondPrompt.substringAfter(FIGURE_BLOCK_HEADER),
        )
    }

    @Test
    fun theFigureRulesNeverAdvertiseJsWhileTheRuntimeIsUnavailable() {
        val prompt = OpenAiModelTaskAdapters.prompt(
            planInput(listOf(TutorToolName.GENERATE_FIGURE)),
        )

        // 只看**注入的生图规则块**。整段 prompt 里有历史兼容文本（attachedImages 的
        // "不得出现图片、SVG、HTML、CSS、JS、代码"），那是在禁止模型输出脚本，与
        // "广告了哪种生图方法"是两件事；扫全段会把禁止句误判成广告。工具声明/
        // availableMethods 的广告面就在这个块里。
        val figureRules = prompt.substringAfter(FIGURE_BLOCK_HEADER, missingDelimiterValue = "")
        assertTrue("必须真的注入了生图规则块：$prompt", figureRules.isNotEmpty())

        assertFalse("不得广告 JS：$figureRules", figureRules.contains("JS"))
        assertFalse("不得提到 QuickJS：$figureRules", figureRules.contains("QuickJS"))
        assertFalse("不得出现 sourceJs：$figureRules", figureRules.contains("sourceJs"))
        assertFalse("不得广告 program 载荷：$figureRules", figureRules.contains("program"))
    }

    @Test
    fun anUndeclaredFigureToolCarriesNoFigureRules() {
        // 提示词不得提到本轮未声明的工具（TutorToolPromptInjectionTest 的同一不变量）。
        val prompt = OpenAiModelTaskAdapters.prompt(
            respondInput(listOf(TutorToolName.KNOWLEDGE_READ)),
        )

        assertFalse(prompt.contains(FigurePromptAssembler.SHARED_RULES))
        assertFalse(prompt.contains("imageOperation"))
        assertFalse(prompt.contains(FIGURE_BLOCK_HEADER))
    }

    @Test
    fun theTutorToolAssemblyDeclaresTheDurableBudgetAndOnlyImageModel() {
        val prompt = FigurePromptAssembler.assembleForTutorTool(
            budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true),
        )

        assertEquals(listOf(FigureGenerationMethod.IMAGE_MODEL), prompt.availableMethods)
        assertTrue(
            "上限来自 durable 策略：${prompt.body}",
            prompt.body.contains(ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES.toString()),
        )
        assertTrue("未预读余量不得冒充余额：${prompt.body}", prompt.body.contains("未预读"))
        assertTrue("讲题必须保留终答位：${prompt.body}", prompt.body.contains("终答"))
        assertTrue("图像与工具轮共享预算：${prompt.body}", prompt.body.contains("共享"))
        assertTrue(prompt.body.contains("IMAGE_MODEL"))
        assertTrue(prompt.body.contains("EDIT"))
        assertTrue(prompt.body.contains("GENERATE"))
        assertFalse("JS 不在 availableMethods 里：${prompt.body}", prompt.body.contains("JS"))
    }

    /**
     * A-04（批次 35）**语义翻转登记**：原用例 `theToolAssemblyStillRefusesToAdvertiseJs`
     * 断言"工具正文装配遇 JS 能力集合即抛"（4E.1 波次 1 fail-closed）。清单 A-04 判据明文
     * "去掉 JS fail-closed"，该失败被消灭——按判据翻转为正向：JS 显式进入能力集合时工具
     * 正文组装成功并注入 JS 静态程序契约段与按方法的工具参数指令。这不是放宽断言：
     * 负向保留在 [theTutorToolAssemblyDeclaresTheDurableBudgetAndOnlyImageModel]
     * （默认 IMAGE_MODEL_ONLY 正文不出现 JS）与 [theFigureRulesNeverAdvertiseJsWhileTheRuntimeIsUnavailable]
     * （生产路径的注入块不出现 JS/QuickJS/program）。
     */
    @Test
    fun aJavascriptToolCapabilityAssemblesWithTheStaticContract() {
        val prompt = FigurePromptAssembler.assembleForTutorTool(
            budget = FigureGenerationBudget(),
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(
                    FigureGenerationMethod.JS,
                    FigureGenerationMethod.IMAGE_MODEL,
                ),
                supportsImageEditing = true,
                supportsImageGeneration = true,
            ),
        )

        assertEquals(
            listOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL),
            prompt.availableMethods,
        )
        assertTrue(
            "JS 可用时工具正文必须注入静态程序契约段：${prompt.body}",
            prompt.body.contains("## JS 静态程序契约（本机执行）"),
        )
        assertTrue(
            "JS 可用时工具正文必须给出 method=JS 的工具参数指令：${prompt.body}",
            prompt.body.contains("选择 JS 时，在工具参数里给出 method=JS"),
        )
        listOf("reduce", "form", "controls").forEach { token ->
            assertFalse(
                "工具正文不得出现交互面字面量 $token：${prompt.body}",
                prompt.body.contains(token),
            )
        }
    }

    @Test
    fun independentPurposesStillProduceDifferentGuidance() {
        // 反向锚：共享规则不变，但用途段必须真的不同（否则"用途"就只是装饰）。
        val reconstruction = FigurePromptAssembler.assembleForTutorTool(
            budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true),
            purposes = listOf(FigureGenerationPurpose.SOURCE_RECONSTRUCTION),
        )
        val explanation = FigurePromptAssembler.assembleForTutorTool(
            budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true),
            purposes = listOf(FigureGenerationPurpose.EXPLANATION),
        )

        assertNotEquals(reconstruction.body, explanation.body)
    }

    private companion object {
        const val FIGURE_BLOCK_HEADER = "[生图工具的统一判定规则"
    }
}
