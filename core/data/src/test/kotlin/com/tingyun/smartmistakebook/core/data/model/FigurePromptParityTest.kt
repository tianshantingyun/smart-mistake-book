package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.FigureGenerationBudget
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicy
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E.1 提示词装配的 JVM 对照测试（任务书 `FigurePromptParityTest`）。
 *
 * 消灭的具体失败：讲题/转写/净化各自维护一份"生图方法"提示词时，一处改了正文、另一处
 * 仍按用途写死方法；以及未接 JS 运行库却先把 JS 写进能力上下文（模型产出无法执行的源码）。
 *
 * 断言的是**共享正文只注入一次**与**能力按真实输入注入**，不把提示词当执行证据。
 */
class FigurePromptParityTest {

    @Test
    fun theSameRuleBodyIsInjectedForTutorAndCapturePurposes() {
        val tutor = assemble(FigureGenerationPurpose.EXPLANATION)
        val capture = assemble(FigureGenerationPurpose.PIXEL_CLEANUP)

        assertTrue(
            "讲题正文必须包含唯一一份共享规则",
            tutor.body.contains(FigurePromptAssembler.SHARED_RULES),
        )
        assertTrue(
            "转写/净化正文必须包含同一份共享规则",
            capture.body.contains(FigurePromptAssembler.SHARED_RULES),
        )
        assertEquals(FigureGenerationPolicy.POLICY_VERSION, tutor.policyVersion)
        assertEquals(tutor.policyVersion, capture.policyVersion)
        assertTrue("用途必须注入", tutor.body.contains("EXPLANATION"))
        assertTrue("用途必须注入", capture.body.contains("PIXEL_CLEANUP"))
        assertNotEquals("不同用途的正文不是逐字相同（用途段不同）", tutor.body, capture.body)
    }

    @Test
    fun jsIsNotAdvertisedWhenItIsNotAvailable() {
        val prompt = assemble(FigureGenerationPurpose.EXPLANATION)

        assertEquals(listOf(FigureGenerationMethod.IMAGE_MODEL), prompt.availableMethods)
        assertFalse("未接通 JS 时不得广告 JS：${prompt.body}", prompt.body.contains("JS"))
        assertFalse("不得出现 QuickJS：${prompt.body}", prompt.body.contains("QuickJS"))
        assertFalse("不得出现 sourceJs：${prompt.body}", prompt.body.contains("sourceJs"))
        assertFalse("不得出现 program 载荷说明：${prompt.body}", prompt.body.contains("program"))
        assertTrue("必须声明 IMAGE_MODEL 可用", prompt.body.contains("IMAGE_MODEL"))
    }

    /**
     * A-04（批次 35）**语义翻转登记**（清单判据行明文"去掉 JS fail-closed"）：
     * 原用例 `advertisingJsIsRefusedInThisWave` 断言"能力集合含 JS 即抛"（4E.1 波次 1
     * 没有 JS 运行库契约时的 fail-closed）。A-04 落地后该失败被消灭——JS 显式进入能力
     * 集合时组装成功并注入静态程序契约（形状/SDK 签名/最小样例）。这不是放宽断言：
     * 两条负向**原样保留**——`jsIsNotAdvertisedWhenItIsNotAvailable`（IMAGE_MODEL_ONLY
     * 输入下正文不出现 JS/QuickJS/sourceJs/program，守"未接通时不广告"）与
     * `noAvailableMethodIsRefused`（空方法集合仍拒绝）。JS 校验的保留负向另见
     * [aJavascriptCapabilityReportsTheParametricInteractionBitHonestly]。
     */
    @Test
    fun aJavascriptCapabilityAssemblesAndInjectsTheStaticContract() {
        val prompt = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(
                    FigureGenerationMethod.JS,
                    FigureGenerationMethod.IMAGE_MODEL,
                ),
                supportsImageEditing = true,
                supportsImageGeneration = true,
                supportsParametricInteraction = true,
            ),
            budget = FigureGenerationBudget(),
        )

        assertEquals(
            listOf(FigureGenerationMethod.JS, FigureGenerationMethod.IMAGE_MODEL),
            prompt.availableMethods,
        )
        assertTrue(
            "JS 可用时正文必须注入程序形状：${prompt.body}",
            prompt.body.contains("const visual = { prepare(input), render(input, metrics) }"),
        )
        assertTrue(
            "JS 可用时正文必须注入 SDK 签名：${prompt.body}",
            prompt.body.contains("visualSdk.circle(cx, cy, r)"),
        )
        assertTrue(
            "JS 可用时正文必须注入最小样例（SDK 原语直取）：${prompt.body}",
            prompt.body.contains("visualSdk.circle(160, 84, 26)"),
        )
    }

    /**
     * 保留负向（R1 §2.2）：删掉"含 JS 即抛"的装配期 fail-closed 后，JS 能力行仍必须按
     * [FigureGenerationCapabilities.supportsParametricInteraction] 如实渲染参数交互位——
     * 防"删 require 顺手删掉全部 JS 校验"。
     */
    @Test
    fun aJavascriptCapabilityReportsTheParametricInteractionBitHonestly() {
        val withoutInteraction = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(
                    FigureGenerationMethod.JS,
                    FigureGenerationMethod.IMAGE_MODEL,
                ),
                supportsImageEditing = true,
                supportsImageGeneration = true,
                supportsParametricInteraction = false,
            ),
            budget = FigureGenerationBudget(),
        )
        assertTrue(
            "JS 静态后端的参数交互位必须如实写不支持：${withoutInteraction.body}",
            withoutInteraction.body.contains("JS：本机结构化程序绘图（静态后端：prepare(input) + render(input, metrics)）；参数交互=不支持"),
        )

        val withInteraction = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(
                    FigureGenerationMethod.JS,
                    FigureGenerationMethod.IMAGE_MODEL,
                ),
                supportsImageEditing = true,
                supportsImageGeneration = true,
                supportsParametricInteraction = true,
            ),
            budget = FigureGenerationBudget(),
        )
        assertTrue(
            "宿主声明参数交互可用时 JS 能力行必须如实写支持：${withInteraction.body}",
            withInteraction.body.contains("JS：本机结构化程序绘图（静态后端：prepare(input) + render(input, metrics)）；参数交互=支持"),
        )
    }

    @Test
    fun noAvailableMethodIsRefused() {
        val result = runCatching {
            FigurePromptAssembler.assemble(
                purpose = FigureGenerationPurpose.EXPLANATION,
                capabilities = FigureGenerationCapabilities.NONE,
                budget = FigureGenerationBudget(),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun budgetAndFinalAnswerReservationAreInjected() {
        val prompt = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 3,
                reserveChatFinalAnswer = true,
            ),
        )

        assertTrue("剩余派发数必须注入：${prompt.body}", prompt.body.contains("3 次"))
        assertTrue("讲题必须声明保留终答位：${prompt.body}", prompt.body.contains("终答"))
    }

    @Test
    fun independentOperationsDoNotReserveAChatSlot() {
        val prompt = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 6,
                reserveChatFinalAnswer = false,
            ),
        )

        assertTrue(
            "独立净化不硬留聊天终答位：${prompt.body}",
            prompt.body.contains("不额外保留聊天终答位"),
        )
    }

    private fun assemble(purpose: FigureGenerationPurpose) = FigurePromptAssembler.assemble(
        purpose = purpose,
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(),
    )
}
