package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q-02（批次 38，2026-11-01 主会话预裁）的 JVM 契约测试：PIXEL_CLEANUP 的
 * purpose→target 映射落 policy。
 *
 * 裁定：语义"保留原像素"的 PIXEL_CLEANUP 一律 `requiresPixelFidelity=true`，policy 无条件
 * 拒绝 JS。语义权威：[FigureGenerationPurpose.PIXEL_CLEANUP] 枚举文（"保留原像素的编辑/去污"，
 * `FigureGeneration.kt`）与 prompt 用途段（"不得把重新绘制称为像素修复"，
 * `FigurePromptAssembler.kt` purposeGuidance）双面钉死；研究（R3 §1.4；准入清单 Q-02 行）
 * 未发现合法"可重绘"语义分支，故不停项回裁。
 *
 * **先红后绿的构造性钉子**：修复前 policy 的两道 pixel 门只看 `request.target` 位，而净化
 * 入口（`ConfiguredCleanImageGenerator`）对 PIXEL_CLEANUP 传的是 `false`——此时一条
 * purpose=PIXEL_CLEANUP、target 位=false 的 JS proposal 会被**放行**（门不存在）。本类
 * 第一条用例钉的就是这个旧漏洞形状：修复后必须 `TARGET_UNSATISFIABLE`。
 *
 * 覆盖映射：
 * - 正例①：PIXEL_CLEANUP + target=false + JS → 拒（映射兜底，不接受宿主声明降级）；
 * - 正例②：PIXEL_CLEANUP + IMAGE_MODEL EDIT + supportsPixelEditing → 放行（生产净化链
 *   不被映射打断；`IMAGE_MODEL_ONLY` 能力位 supportsPixelEditing=true）；
 * - 负例③：PIXEL_CLEANUP + IMAGE_MODEL GENERATE → 拒（清理只能 EDIT，GENERATE 冒充被拒）；
 * - 负控④：SOURCE_RECONSTRUCTION + target=false + JS → 放行（映射由 PIXEL_CLEANUP 用途
 *   驱动，不把所有用途一刀切；同时钉住"这是 target 语义规整，不是用途→方法路由"）；
 * - 负例⑤：SOURCE_RECONSTRUCTION + target=true + JS → 拒（Q-01 原门不被映射改动破坏）。
 */
class FigureGenerationPixelCleanupMappingTest {

    /** JS 已广告、其余位与生产净化请求同构的最小 request。 */
    private fun request(
        purpose: FigureGenerationPurpose,
        target: FigureGenerationTarget,
        capabilities: FigureGenerationCapabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "q02-op-1",
        purpose = purpose,
        target = target,
        capabilities = capabilities,
        budget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
        dispatchParent = FigureDispatchParent.ModelTask("q02-task-1", reserveChatFinalAnswer = false),
        source = FigureSourceEvidence(
            assetId = "asset-1",
            contentSha256 = figureSha256Hex(Q02_SOURCE_BYTES),
            mimeType = "image/png",
            imageBytes = Q02_SOURCE_BYTES,
        ),
    )

    private fun jsAdvertisedCapabilities(): FigureGenerationCapabilities = FigureGenerationCapabilities(
        availableMethods = setOf(FigureGenerationMethod.JS),
        supportsParametricInteraction = false,
    )

    private fun jsProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationProposal(
            decision = FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
            method = FigureGenerationMethod.JS,
            program = "const visual = {};",
        )

    private fun rejectCode(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationRejectionCode {
        val validation = FigureGenerationPolicy.validate(request, proposal)
        assertTrue(
            "expected a rejection, got Accepted: $proposal",
            validation is FigureGenerationValidation.Rejected,
        )
        return (validation as FigureGenerationValidation.Rejected).code
    }

    @Test
    fun `a pixel cleanup request cannot be satisfied by js even when the host target bit is false`() {
        // 旧入口的 target 形状（requiresPixelFidelity=false）+ JS proposal：
        // 修复前 policy 只看 target 位 → 放行（Q-02 登记的入口侧漏洞）；
        // 修复后 purpose 映射兜底 → TARGET_UNSATISFIABLE。
        val request = request(
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = false,
                requiresCompleteLabels = true,
            ),
            capabilities = jsAdvertisedCapabilities(),
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
            rejectCode(request, jsProposal(request)),
        )
    }

    @Test
    fun `a pixel cleanup edit over pixel editing capability still validates`() {
        // 生产净化链形状：IMAGE_MODEL + EDIT + IMAGE_MODEL_ONLY（supportsPixelEditing=true）。
        // 映射生效后该路径必须原样放行——映射封的是 JS，不打断净化。
        val request = request(
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = true,
                requiresCompleteLabels = true,
            ),
        )
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "仅去除手写笔迹，保留印刷题面",
            reason = "测试",
        )

        assertEquals(
            FigureGenerationValidation.Accepted,
            FigureGenerationPolicy.validate(request, proposal),
        )
    }

    @Test
    fun `a pixel cleanup generate operation cannot pose as cleanup`() {
        // 映射生效后 PIXEL_CLEANUP 的 IMAGE 侧同样只能走 EDIT；GENERATE 从零合成不触原像素。
        val request = request(
            purpose = FigureGenerationPurpose.PIXEL_CLEANUP,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = true,
                requiresCompleteLabels = true,
            ),
        )
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.GENERATE,
            imagePrompt = "凭空画一张干净题面",
            reason = "测试",
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun `a non pixel purpose with a false target bit is not caught by the mapping`() {
        // 负控：SOURCE_RECONSTRUCTION + target=false 的 JS proposal 不因映射被拒——
        // 证明拒绝由 PIXEL_CLEANUP 用途驱动，映射不是 method 路由、不外溢到其他用途。
        val request = request(
            purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = false,
                requiresCompleteLabels = true,
            ),
            capabilities = jsAdvertisedCapabilities(),
        )

        assertEquals(
            FigureGenerationValidation.Accepted,
            FigureGenerationPolicy.validate(request, jsProposal(request)),
        )
    }

    @Test
    fun `an explicit pixel fidelity target still rejects js regardless of purpose`() {
        // Q-01 原门回归钉：显式 requiresPixelFidelity=true 的拒绝路径不被映射改动破坏。
        val request = request(
            purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = true,
                requiresCompleteLabels = true,
            ),
            capabilities = jsAdvertisedCapabilities(),
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
            rejectCode(request, jsProposal(request)),
        )
    }

    private companion object {
        val Q02_SOURCE_BYTES = "q02-source-photo".toByteArray()
    }
}
