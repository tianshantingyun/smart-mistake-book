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

    @Test
    fun advertisingJsIsRefusedInThisWave() {
        val capabilities = FigureGenerationCapabilities(
            availableMethods = setOf(
                FigureGenerationMethod.JS,
                FigureGenerationMethod.IMAGE_MODEL,
            ),
            supportsImageEditing = true,
            supportsImageGeneration = true,
            supportsParametricInteraction = true,
        )

        val result = runCatching {
            FigurePromptAssembler.assemble(
                purpose = FigureGenerationPurpose.EXPLANATION,
                capabilities = capabilities,
                budget = FigureGenerationBudget(),
            )
        }

        assertTrue(
            "本波次没有 JS 运行库契约，装配器必须 fail-closed",
            result.exceptionOrNull() is IllegalArgumentException,
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
