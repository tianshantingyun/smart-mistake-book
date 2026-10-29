package com.tingyun.smartmistakebook.core.model

/**
 * 统一生图的**纯校验策略**（4E.1 波次 1）。
 *
 * 消灭的具体失败：
 * - 旧实现里"生图"由入口各自决定方法，且无 method 的输出从未被拒绝——任何调用点都可以
 *   静默默认图像模型，模型没有选择权。这里 **GENERATE 缺 method 即拒绝**，且 method 必须
 *   在宿主真实声明的 [FigureGenerationCapabilities.availableMethods] 内。
 * - 一个 proposal 同时带 program 与 imagePrompt 时，两个后端都可能执行同一件事（双付费）。
 *   这里按 method 严格正交：JS 只带 program，IMAGE_MODEL 只带 imageOperation/imagePrompt。
 * - 入口把宿主约束丢掉、把 purpose 改成"讲解"冒充"题面重建"时，校验会拒绝——proposal 的
 *   purpose 必须等于宿主冻结的 purpose，宿主约束必须逐条保留（约束未被降级）。
 * - 没有持久父操作/显式已知预算不足时仍出网。这里在预留之前就拒绝，与 core:domain 的
 *   `RemoteModelDispatchLedger` 的 durable 判定形成两道门（策略门 + 账本门）。
 *   预算未预读（`remainingRemoteDispatches == null`）时策略门放行，由账本做真门；
 *   账本拒绝会把真实已派发次数回给诊断（KD-60）。**预算门只对 IMAGE_MODEL 生效**：
 *   JS 在本地引擎执行、不占远程派发，不得被远程余额拒掉。
 *
 * 只做校验，不做路由：它不按 purpose 选 method，也不替入口改选。
 */
object FigureGenerationPolicy {
    /** 与权威提示词文件同名的版本；所有入口共用一个值。 */
    const val POLICY_VERSION: String = FigureGenerationPolicyVersion.VALUE

    const val MAX_CONSTRAINTS: Int = 32
    const val MAX_CONSTRAINT_CHARS: Int = 200
    const val MAX_REASON_CHARS: Int = 500
    const val MAX_IMAGE_PROMPT_UTF8_BYTES: Int = 4 * 1_024
    const val MAX_PROGRAM_UTF8_BYTES: Int = 64 * 1_024

    /**
     * 校验 request + proposal 的合法性与能力/目标/预算一致性。
     *
     * 返回 [FigureGenerationValidation.Rejected] 时，协调器**不得**预留派发、不得调用后端。
     */
    fun validate(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationValidation {
        if (proposal.policyVersion != POLICY_VERSION) {
            return reject(
                FigureGenerationRejectionCode.POLICY_VERSION_MISMATCH,
                "生图 proposal 的策略版本不一致：${proposal.policyVersion}",
            )
        }
        request.prompt?.let { prompt ->
            if (prompt.policyVersion != POLICY_VERSION) {
                return reject(
                    FigureGenerationRejectionCode.POLICY_VERSION_MISMATCH,
                    "生图判定上下文的策略版本不一致：${prompt.policyVersion}",
                )
            }
        }
        if (proposal.purpose != null && proposal.purpose != request.purpose) {
            return reject(
                FigureGenerationRejectionCode.PURPOSE_MISMATCH,
                "proposal 的用途超出了宿主目标（请求 ${request.purpose}，proposal ${proposal.purpose}）",
            )
        }
        return when (proposal.decision) {
            FigureGenerationDecision.GENERATE -> validateGenerate(request, proposal)
            FigureGenerationDecision.NO_FIGURE,
            FigureGenerationDecision.NEED_SOURCE,
            FigureGenerationDecision.UNAVAILABLE,
            -> validateNonGenerating(proposal)
        }
    }

    /** 入口适配层构造 IMAGE_MODEL proposal 的单一形状（purpose 来自宿主 request）。 */
    fun imageModelProposal(
        request: FigureGenerationRequest,
        imageOperation: FigureImageOperation,
        imagePrompt: String,
        reason: String,
        fidelity: FigureFidelity = request.target.fidelity,
        constraints: List<String> = request.constraints,
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = POLICY_VERSION,
        decision = FigureGenerationDecision.GENERATE,
        purpose = request.purpose,
        method = FigureGenerationMethod.IMAGE_MODEL,
        reason = reason,
        fidelity = fidelity,
        constraints = constraints,
        imageOperation = imageOperation,
        imagePrompt = imagePrompt,
    )

    /** 入口适配层构造"确实不需要图"的 proposal。 */
    fun noFigureProposal(
        request: FigureGenerationRequest,
        reason: String,
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = POLICY_VERSION,
        decision = FigureGenerationDecision.NO_FIGURE,
        purpose = request.purpose,
        reason = reason,
    )

    /** 入口适配层构造"源证据不足"的 proposal。 */
    fun needSourceProposal(
        request: FigureGenerationRequest,
        reason: String,
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = POLICY_VERSION,
        decision = FigureGenerationDecision.NEED_SOURCE,
        purpose = request.purpose,
        reason = reason,
    )

    /** 入口适配层构造"没有可用方法"的 proposal。 */
    fun unavailableProposal(
        request: FigureGenerationRequest,
        reason: String,
    ): FigureGenerationProposal = FigureGenerationProposal(
        policyVersion = POLICY_VERSION,
        decision = FigureGenerationDecision.UNAVAILABLE,
        purpose = request.purpose,
        reason = reason,
    )

    private fun validateGenerate(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationValidation {
        val method = proposal.method
            ?: return reject(
                FigureGenerationRejectionCode.GENERATE_WITHOUT_METHOD,
                "GENERATE 必须显式选择 method，不能静默默认图像方法",
            )
        if (method !in request.capabilities.availableMethods) {
            return reject(
                FigureGenerationRejectionCode.METHOD_NOT_AVAILABLE,
                "本机未接通 ${method.name} 方法，不得假装可用",
            )
        }
        if (proposal.purpose == null) {
            return reject(
                FigureGenerationRejectionCode.PURPOSE_MISMATCH,
                "GENERATE 必须回显宿主用途",
            )
        }
        // 预算门只约束**远程**派发：JS 在本地引擎执行，不占远程预算（视觉契约"JS 本地执行
        // 不占远程派发"）。把同一道门也压在 JS 上，会让零余额的纯本地渲染被
        // BUDGET_INSUFFICIENT 拒掉——本地方法替远程预算买单，正是契约禁止的错配。
        if (
            method == FigureGenerationMethod.IMAGE_MODEL &&
            !request.budget.allowsOneRemoteDispatch()
        ) {
            return reject(
                FigureGenerationRejectionCode.BUDGET_INSUFFICIENT,
                "剩余远程派发预算不足以再执行一次生图" +
                    (if (request.budget.reserveChatFinalAnswer) "（须保留终答位）" else ""),
            )
        }
        if (request.dispatchParent == null) {
            return reject(
                FigureGenerationRejectionCode.DISPATCH_UNBOUND,
                "这次生图没有绑定宿主的持久派发操作，不能出网",
            )
        }
        if (!constraintsPreserved(request.constraints, proposal.constraints)) {
            return reject(
                FigureGenerationRejectionCode.CONSTRAINT_DROPPED,
                "proposal 丢失了宿主约束（约束不得降级）",
            )
        }
        val proposalConstraints = proposal.constraints
        if (
            proposalConstraints.size > MAX_CONSTRAINTS ||
            proposalConstraints.any { it.isBlank() || it.length > MAX_CONSTRAINT_CHARS }
        ) {
            return reject(
                FigureGenerationRejectionCode.CONSTRAINT_BUDGET_EXCEEDED,
                "宿主的生图约束超出条数/长度预算",
            )
        }
        val proposedFidelity = proposal.fidelity
        if (proposedFidelity != null && !fidelityAtLeast(proposedFidelity, request.target.fidelity)) {
            return reject(
                FigureGenerationRejectionCode.TARGET_DOWNGRADED,
                "proposal 的保真档低于宿主目标（$proposedFidelity < ${request.target.fidelity}）",
            )
        }
        return when (method) {
            FigureGenerationMethod.JS -> validateJsProposal(request, proposal)
            FigureGenerationMethod.IMAGE_MODEL -> validateImageModelProposal(request, proposal)
        }
    }

    private fun validateJsProposal(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationValidation {
        if (proposal.imageOperation != null || proposal.imagePrompt != null) {
            return reject(
                FigureGenerationRejectionCode.JS_PAYLOAD_INVALID,
                "JS 方法不得携带图像操作或 imagePrompt",
            )
        }
        val program = proposal.program
            ?: return reject(
                FigureGenerationRejectionCode.JS_PAYLOAD_INVALID,
                "JS 方法必须携带完整 program",
            )
        if (program.isBlank()) {
            return reject(
                FigureGenerationRejectionCode.JS_PAYLOAD_INVALID,
                "JS program 不得为空",
            )
        }
        if (figureUsageUtf8Bytes(program) > MAX_PROGRAM_UTF8_BYTES) {
            return reject(
                FigureGenerationRejectionCode.JS_PAYLOAD_INVALID,
                "JS program 超过 $MAX_PROGRAM_UTF8_BYTES 字节预算",
            )
        }
        if (request.target.requiresParametricInteraction && !request.capabilities.supportsParametricInteraction) {
            return reject(
                FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
                "目标要求参数交互，但本机 JS 运行库不支持交互",
            )
        }
        return FigureGenerationValidation.Accepted
    }

    private fun validateImageModelProposal(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationValidation {
        if (proposal.program != null) {
            return reject(
                FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
                "IMAGE_MODEL 方法不得携带 JS program",
            )
        }
        val operation = proposal.imageOperation
            ?: return reject(
                FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
                "IMAGE_MODEL 方法必须声明 EDIT 或 GENERATE",
            )
        val imagePrompt = proposal.imagePrompt
            ?: return reject(
                FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
                "IMAGE_MODEL 方法必须携带具体 imagePrompt",
            )
        if (imagePrompt.isBlank()) {
            return reject(
                FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
                "imagePrompt 不得为空",
            )
        }
        if (figureUsageUtf8Bytes(imagePrompt) > MAX_IMAGE_PROMPT_UTF8_BYTES) {
            return reject(
                FigureGenerationRejectionCode.IMAGE_PAYLOAD_INVALID,
                "imagePrompt 超过 $MAX_IMAGE_PROMPT_UTF8_BYTES 字节预算",
            )
        }
        val sourceBytes = request.source?.imageBytes()
        if (sourceBytes != null && sourceBytes.isEmpty()) {
            return reject(
                FigureGenerationRejectionCode.SOURCE_REQUIRED,
                "源图证据不得为空",
            )
        }
        when (operation) {
            FigureImageOperation.EDIT -> {
                if (!request.capabilities.supportsImageEditing) {
                    return reject(
                        FigureGenerationRejectionCode.IMAGE_OPERATION_UNAVAILABLE,
                        "本机没有已验证的图像编辑能力",
                    )
                }
                if (sourceBytes == null) {
                    return reject(
                        FigureGenerationRejectionCode.SOURCE_REQUIRED,
                        "图像编辑必须带已授权的源图字节",
                    )
                }
                val declaredSha = request.source?.contentSha256
                if (declaredSha != null && declaredSha != figureSha256Hex(sourceBytes)) {
                    return reject(
                        FigureGenerationRejectionCode.SOURCE_EVIDENCE_MISMATCH,
                        "源图证据 sha 与实际字节不一致",
                    )
                }
            }

            FigureImageOperation.GENERATE -> {
                if (!request.capabilities.supportsImageGeneration) {
                    return reject(
                        FigureGenerationRejectionCode.IMAGE_OPERATION_UNAVAILABLE,
                        "本机没有已验证的图像生成能力",
                    )
                }
            }
        }
        if (request.target.requiresParametricInteraction) {
            return reject(
                FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
                "目标要求参数交互，图像模型方法不能冒充完成",
            )
        }
        if (
            request.target.requiresPixelFidelity &&
            (operation != FigureImageOperation.EDIT || !request.capabilities.supportsPixelEditing)
        ) {
            return reject(
                FigureGenerationRejectionCode.TARGET_UNSATISFIABLE,
                "目标要求原像素保真，只能用支持该能力的图像编辑",
            )
        }
        return FigureGenerationValidation.Accepted
    }

    private fun validateNonGenerating(
        proposal: FigureGenerationProposal,
    ): FigureGenerationValidation {
        if (
            proposal.method != null ||
            proposal.program != null ||
            proposal.imageOperation != null ||
            proposal.imagePrompt != null
        ) {
            return reject(
                FigureGenerationRejectionCode.DECISION_WITH_PAYLOAD,
                "非 GENERATE 决策不得携带方法或载荷",
            )
        }
        return FigureGenerationValidation.Accepted
    }

    /** 宿主约束必须逐条出现在 proposal 中（不允许删减/改写）。 */
    private fun constraintsPreserved(
        requested: List<String>,
        proposed: List<String>,
    ): Boolean = requested.all { it in proposed }

    /** FAITHFUL > SEMANTIC > ILLUSTRATIVE。 */
    private fun fidelityAtLeast(
        candidate: FigureFidelity,
        required: FigureFidelity,
    ): Boolean = candidate.ordinal <= required.ordinal

    private fun reject(
        code: FigureGenerationRejectionCode,
        message: String,
    ): FigureGenerationValidation = FigureGenerationValidation.Rejected(code, message)
}

/** policy 校验结论。[Accepted] 仍不代表预算/预留已落实——那是 ledger 的门。 */
sealed interface FigureGenerationValidation {
    data object Accepted : FigureGenerationValidation

    data class Rejected(
        val code: FigureGenerationRejectionCode,
        val message: String,
    ) : FigureGenerationValidation
}

/** 拒绝码（有界、可审计；不把原始 proposal 文本回灌到日志）。 */
enum class FigureGenerationRejectionCode {
    POLICY_VERSION_MISMATCH,
    GENERATE_WITHOUT_METHOD,
    METHOD_NOT_AVAILABLE,
    PURPOSE_MISMATCH,
    DECISION_WITH_PAYLOAD,
    JS_PAYLOAD_INVALID,
    IMAGE_PAYLOAD_INVALID,
    IMAGE_OPERATION_UNAVAILABLE,
    SOURCE_REQUIRED,
    SOURCE_EVIDENCE_MISMATCH,
    TARGET_UNSATISFIABLE,
    TARGET_DOWNGRADED,
    CONSTRAINT_DROPPED,
    CONSTRAINT_BUDGET_EXCEEDED,
    BUDGET_INSUFFICIENT,
    DISPATCH_UNBOUND,
}
