package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4E.1 统一生图策略的 JVM 契约测试。
 *
 * 钉住的核心失败（对应任务书"能力/目标/预算合法；GENERATE 缺 method 拒绝"）：
 * - 无 method 的 GENERATE 不再静默默认图像方法；
 * - 未广告的方法不能执行；
 * - purpose 不能越出宿主范围；
 * - JS / IMAGE_MODEL 载荷严格正交；
 * - EDIT 必须有已授权源图字节，且声明的 sha 必须与实际字节一致；
 * - 讲题预算必须给终答留位；
 * - 没有持久父操作不得出网；
 * - 宿主约束不得被 proposal 丢掉，保真档不得被降低。
 */
class FigureGenerationPolicyTest {

    @Test
    fun generateWithoutMethodIsRejected() {
        val request = request()
        val proposal = FigureGenerationProposal(
            decision = FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
        )

        assertEquals(
            FigureGenerationRejectionCode.GENERATE_WITHOUT_METHOD,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun aMethodThatIsNotAdvertisedIsRejected() {
        val request = request()
        val proposal = FigureGenerationProposal(
            decision = FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
            method = FigureGenerationMethod.JS,
            program = "const visual = {}",
        )

        assertEquals(
            FigureGenerationRejectionCode.METHOD_NOT_AVAILABLE,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun theProposalPurposeMustMatchTheHostPurpose() {
        val request = request(purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION)
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "干净题面",
            reason = "测试",
        ).copy(purpose = FigureGenerationPurpose.EXPLANATION)

        assertEquals(
            FigureGenerationRejectionCode.PURPOSE_MISMATCH,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun imagePayloadAndJsProgramAreOrthogonal() {
        val request = request()

        val imageWithProgram = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "干净题面",
            reason = "测试",
        ).copy(program = "const visual = {}")
        assertEquals(
            FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
            rejectCode(request, imageWithProgram),
        )

        val jsRequest = request(
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(FigureGenerationMethod.JS),
                supportsParametricInteraction = true,
            ),
        )
        val jsWithImage = FigureGenerationProposal(
            decision = FigureGenerationDecision.GENERATE,
            purpose = jsRequest.purpose,
            method = FigureGenerationMethod.JS,
            program = "const visual = {}",
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "不该出现",
        )
        assertEquals(
            FigureGenerationRejectionCode.JS_PAYLOAD_INVALID,
            rejectCode(jsRequest, jsWithImage),
        )
    }

    @Test
    fun anEditWithoutSourceBytesIsRejected() {
        val request = request(source = null)
        val proposal = imageProposal(request)

        assertEquals(
            FigureGenerationRejectionCode.SOURCE_REQUIRED,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun aDeclaredSourceShaThatDoesNotMatchTheBytesIsRejected() {
        val bytes = "photo".toByteArray()
        val request = request(
            source = FigureSourceEvidence(
                contentSha256 = "a".repeat(64),
                mimeType = "image/png",
                imageBytes = bytes,
            ),
        )

        assertEquals(
            FigureGenerationRejectionCode.SOURCE_EVIDENCE_MISMATCH,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun aTutorBudgetMustKeepItsFinalAnswerSlot() {
        val request = request(
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 1,
                reserveChatFinalAnswer = true,
            ),
        )

        assertEquals(
            FigureGenerationRejectionCode.BUDGET_INSUFFICIENT,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun anIndependentOperationMayUseItsLastDispatch() {
        val request = request(
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 1,
                reserveChatFinalAnswer = false,
            ),
        )

        assertTrue(
            FigureGenerationPolicy.validate(request, imageProposal(request))
                is FigureGenerationValidation.Accepted,
        )
    }

    /**
     * KD-60：声明面没有预读 durable 余量时（`remainingRemoteDispatches == null`），
     * 策略门**不得**拿常量 6 冒充余额放行/拒绝——真正的预算门是账本；这里只允许
     * 请求进入预留阶段，由账本决定。
     */
    @Test
    fun anUnknownRemainingBalanceDefersToTheDurableLedger() {
        val request = request(
            budget = FigureGenerationBudget(
                operationDispatchLimit = ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES,
                remainingRemoteDispatches = null,
                reserveChatFinalAnswer = true,
            ),
            parent = FigureDispatchParent.ModelTask("task-1", reserveChatFinalAnswer = true),
        )

        assertTrue(
            FigureGenerationPolicy.validate(request, imageProposal(request))
                is FigureGenerationValidation.Accepted,
        )
    }

    /**
     * JS 在本地引擎执行，不占远程派发：远程余额为 0 时仍必须放行（A-01）。
     * 把同一道预算门压在 JS 上，会让纯本地渲染替远程预算买单。
     */
    @Test
    fun aLocalJavascriptProposalIsNotBlockedByTheRemoteBudget() {
        val request = request(
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(FigureGenerationMethod.JS),
            ),
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 0,
                reserveChatFinalAnswer = true,
            ),
        )
        val proposal = FigureGenerationProposal(
            decision = FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
            method = FigureGenerationMethod.JS,
            program = "const visual = {}",
        )

        assertTrue(
            "本地方法不得因远程余额为 0 被拒：${
                FigureGenerationPolicy.validate(request, proposal)
            }",
            FigureGenerationPolicy.validate(request, proposal) is FigureGenerationValidation.Accepted,
        )
    }

    /** 同一份零余额预算下，IMAGE_MODEL 仍必须被预算门拒掉（对照臂，不是放宽）。 */
    @Test
    fun anImageModelProposalStillNeedsARemoteDispatch() {
        val request = request(
            budget = FigureGenerationBudget(
                remainingRemoteDispatches = 0,
                reserveChatFinalAnswer = false,
            ),
        )

        assertEquals(
            FigureGenerationRejectionCode.BUDGET_INSUFFICIENT,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun aRequestWithoutAPersistentParentCannotGenerate() {
        val request = request(parent = null)

        assertEquals(
            FigureGenerationRejectionCode.DISPATCH_UNBOUND,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun hostConstraintsCannotBeDroppedByTheProposal() {
        val request = request(constraints = listOf("保留全部标注"))
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "干净题面",
            reason = "测试",
            constraints = emptyList(),
        )

        assertEquals(
            FigureGenerationRejectionCode.CONSTRAINT_DROPPED,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun aNonGeneratingDecisionCannotCarryAPayload() {
        val request = request()
        val proposal = FigureGenerationPolicy.noFigureProposal(request, "不需要图")
            .copy(method = FigureGenerationMethod.IMAGE_MODEL)

        assertEquals(
            FigureGenerationRejectionCode.DECISION_WITH_PAYLOAD,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun aValidImageModelProposalIsAccepted() {
        val request = request()

        assertTrue(
            FigureGenerationPolicy.validate(request, imageProposal(request))
                is FigureGenerationValidation.Accepted,
        )
    }

    @Test
    fun aValidNoFigureProposalIsAccepted() {
        val request = request()

        assertTrue(
            FigureGenerationPolicy.validate(
                request,
                FigureGenerationPolicy.noFigureProposal(request, "纯文字已足够"),
            ) is FigureGenerationValidation.Accepted,
        )
    }

    @Test
    fun anImagePromptOverTheUtf8BudgetIsRejected() {
        val request = request()
        val oversized = "a".repeat(FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES + 1)

        assertEquals(
            FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
            rejectCode(request, imageProposal(request, prompt = oversized)),
        )
    }

    @Test
    fun aFidelityDowngradeIsRejected() {
        val request = request(target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL))
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "干净题面",
            reason = "测试",
            fidelity = FigureFidelity.ILLUSTRATIVE,
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_DOWNGRADED,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun pixelFidelityRequiresAnEditCapableMethod() {
        val request = request(
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.FAITHFUL,
                requiresPixelFidelity = true,
            ),
            capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY.copy(
                supportsPixelEditing = false,
            ),
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun parametricInteractionCannotBePromisedByImageModel() {
        val request = request(
            target = FigureGenerationTarget(
                fidelity = FigureFidelity.SEMANTIC,
                requiresParametricInteraction = true,
            ),
        )

        assertEquals(
            FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
            rejectCode(request, imageProposal(request)),
        )
    }

    @Test
    fun aStalePolicyVersionIsRejected() {
        val request = request()
        val proposal = imageProposal(request).copy(policyVersion = "figure-generation-policy-v0")

        assertEquals(
            FigureGenerationRejectionCode.POLICY_VERSION_MISMATCH,
            rejectCode(request, proposal),
        )
    }

    @Test
    fun thePolicyVersionIsTheContractValue() {
        assertEquals("figure-generation-policy-v1", FigureGenerationPolicy.POLICY_VERSION)
        assertEquals(FigureGenerationPolicyVersion.VALUE, FigureGenerationPolicy.POLICY_VERSION)
    }

    // -----------------------------------------------------------------

    private fun rejectCode(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationRejectionCode {
        val validation = FigureGenerationPolicy.validate(request, proposal)
        assertTrue(
            "预期拒绝，实际 $validation",
            validation is FigureGenerationValidation.Rejected,
        )
        return (validation as FigureGenerationValidation.Rejected).code
    }

    private fun imageProposal(
        request: FigureGenerationRequest,
        prompt: String = "基于已授权原图重建干净题面，保留全部标注。",
    ): FigureGenerationProposal = FigureGenerationPolicy.imageModelProposal(
        request = request,
        imageOperation = FigureImageOperation.EDIT,
        imagePrompt = prompt,
        reason = "测试",
    )

    private fun request(
        purpose: FigureGenerationPurpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target: FigureGenerationTarget = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities: FigureGenerationCapabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget: FigureGenerationBudget = FigureGenerationBudget(
            remainingRemoteDispatches = 6,
            reserveChatFinalAnswer = false,
        ),
        parent: FigureDispatchParent? = FigureDispatchParent.ModelTask("task-1", false),
        source: FigureSourceEvidence? = defaultSource(),
        constraints: List<String> = emptyList(),
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = purpose,
        target = target,
        capabilities = capabilities,
        budget = budget,
        dispatchParent = parent,
        source = source,
        constraints = constraints,
    )

    private fun defaultSource(): FigureSourceEvidence {
        val bytes = "photo".toByteArray()
        return FigureSourceEvidence(
            assetId = "asset-1",
            contentSha256 = figureSha256Hex(bytes),
            mimeType = "image/png",
            imageBytes = bytes,
        )
    }
}
