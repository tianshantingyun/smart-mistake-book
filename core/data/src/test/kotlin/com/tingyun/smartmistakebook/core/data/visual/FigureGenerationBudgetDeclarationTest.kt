package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.data.model.FigurePromptAssembler
import com.tingyun.smartmistakebook.core.data.model.sharedOperationFigureBudget
import com.tingyun.smartmistakebook.core.data.study.FakeStudyDatabasePort
import com.tingyun.smartmistakebook.core.database.ModelTaskOperationDispatchReservationResult
import com.tingyun.smartmistakebook.core.database.ReserveModelTaskOperationDispatchCommand
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationCapabilities
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.ModelTaskRemoteDispatchPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KD-60：预算声明不再用常量 6 冒充实余量；图像与工具轮共享的 durable 预算
 * **分列**说明，拒绝时给出真实已派发次数（可读诊断）。
 */
class FigureGenerationBudgetDeclarationTest {

    @Test
    fun `the declared budget carries the shared cap and an unknown real balance`() {
        val budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true)

        assertEquals(ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES, budget.operationDispatchLimit)
        assertNull(
            "6 是上限，不是余量；声明面不得把上限冒充成当前剩余",
            budget.remainingRemoteDispatches,
        )
        assertTrue(budget.sharesOperationBudgetWithToolRounds)
        assertTrue("未预读时放行到账本门（真门）", budget.allowsOneRemoteDispatch())
    }

    @Test
    fun `an explicit zero balance still blocks before the ledger`() {
        val exhausted = sharedOperationFigureBudget(reserveChatFinalAnswer = false)
            .copy(remainingRemoteDispatches = 0)

        assertFalse(exhausted.allowsOneRemoteDispatch())
    }

    @Test
    fun `the prompt separates the shared cap from the durable remaining balance`() {
        val prompt = FigurePromptAssembler.assemble(
            purpose = FigureGenerationPurpose.EXPLANATION,
            capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
            budget = sharedOperationFigureBudget(reserveChatFinalAnswer = true),
        )

        assertTrue("必须说明图像与工具轮共享预算：${prompt.body}", prompt.body.contains("共享"))
        assertTrue("未预读必须如实说：${prompt.body}", prompt.body.contains("未预读"))
        assertFalse(
            "不得再写“剩余远程派发上限：6 次”冒充余额：${prompt.body}",
            prompt.body.contains("剩余远程派发上限：6 次"),
        )
        assertTrue(prompt.body.contains("终答"))
    }

    @Test
    fun `a ledger denial names the real consumed count and the shared budget`() = runBlocking {
        val ledger = RoomFigureDispatchLedger(DenyingPort(), now = { 5 })

        val decision = ledger.reserve(
            RemoteModelDispatchRequest(
                operationId = "figure-op:explanation:figure-1",
                parent = FigureDispatchParent.ModelTask("task-1", reserveChatFinalAnswer = true),
                remoteKind = com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind.IMAGE_GENERATION,
                method = FigureGenerationMethod.IMAGE_MODEL,
                purpose = FigureGenerationPurpose.EXPLANATION,
                policyVersion = FigureGenerationPolicyVersion.VALUE,
                configurationFingerprint = "config-fingerprint",
                occurredAtEpochMillis = 5,
            ),
        )

        val denied = decision as RemoteModelDispatchDecision.Denied
        assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, denied.code)
        assertTrue("必须给出实际已派发次数：${denied.message}", denied.message.contains("已派发 5"))
        assertTrue("必须说明共享预算：${denied.message}", denied.message.contains("共享"))
        assertTrue("讲题必须说明终答保留位：${denied.message}", denied.message.contains("终答"))
        assertTrue("必须如实说未出网：${denied.message}", denied.message.contains("未出网"))
    }

    private class DenyingPort : FakeStudyDatabasePort() {
        override suspend fun reserveModelTaskOperationDispatch(
            command: ReserveModelTaskOperationDispatchCommand,
        ): ModelTaskOperationDispatchReservationResult = ModelTaskOperationDispatchReservationResult(
            applied = false,
            budgetExhausted = true,
            logicalDispatchCount = 5,
            reservationId = null,
        )
    }
}
