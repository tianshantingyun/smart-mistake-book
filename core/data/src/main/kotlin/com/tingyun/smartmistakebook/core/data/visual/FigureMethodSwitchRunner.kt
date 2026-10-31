package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.domain.FigureGenerationCoordinator
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest
import com.tingyun.smartmistakebook.core.model.FigureGenerationStatus

/**
 * V-02 方法切换的**调用侧编排**（批次 38 计划任务 1；设计 =
 * `docs/research/2026-11-01-method-switching-and-transactions.md` §2.2 候选 B，下称 R1）。
 *
 * **它消灭的具体失败**：契约 §1"允许模型在同一操作内根据失败诊断修复或重选方法，无需新增
 * 逐图确认"——但此前同一 operationId 内没有 JS→IMAGE 的机械半边：JS 可归属失败后，IMAGE
 * 另解只能等模型下一轮显式改选（白烧一次模型回合）。本类把 JS 可归属失败后的 IMAGE 回退
 * 变成**同一次工具调用执行内**的第二次 [FigureGenerationCoordinator.generate]——载荷本地
 * 已有（同一工具调用的 imagePrompt），不必烧一次模型回合。
 *
 * 协调器 / policy / 审计 / 账本 / DAO **零改动**（R1 §2.1 逐行实证）：一次切换 = 同
 * `operationId` 的第二次 `generate`，其审计/预算语义全部来自既有持久结构。
 *
 * ## 逐条对验收行（清单"同 operationId、不新增确认、预算不重置、无双重载荷"）
 *
 * - **同 operationId**：原样复用同一 [request] 实例调用两次；`operationId` 派生不含 method
 *   （`TutorFigureGenerator` 的派生口径），编排层不做任何 id 再派生。
 * - **不新增确认**：切换发生在同一次工具调用执行内，无新 UI 确认、无新授权面（契约 §1 明文）。
 * - **预算不重置**：远程 per-operation 账本计数跨方法累计（同 id 天然不重置；6 上限共用，
 *   `ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES`）；切换后预留被拒 ⇒ 终态 FAILED + 真实
 *   计数诊断（KD-60），**不得**因换过方法绕行。本地 JS 专面预算（布局/源码/墙钟）不迁移、
 *   也不因切换重置；"同一操作最多两份最终图"的门在协调器方法分派**前**
 *   （`DefaultFigureGenerationCoordinator.generateImage` 的 `admitFinalFigure`），第 3 份
 *   成品拒一切方法——切换尝试同样被它拒，编排层不复制这道门。
 * - **explicitUserRetry 原样透传**：该位只被 JS 本地路径消费（熔断解封，恒等物是 program
 *   摘要）；切换产生的 IMAGE 尝试在协调器远程路径结构上不读它——机械切换不借道解除 JS 熔断，
 *   也不产生 policy/预算豁免。
 * - **无双重载荷**：编排层返回**唯一**的最终 outcome；失败的 JS attempt 无产物
 *   （`FigureGenerationOutcome` 类型上只有 GENERATED/CACHED 可携带产物），fallback 提案由
 *   [FigureGenerationPolicy.imageModelProposal] 形状构造（只带 imageOperation/imagePrompt，
 *   program 恒 null）——policy 载荷互斥（`FigureGenerationPolicy` 的 JS/IMAGE 互斥校验）
 *   在第二次 generate 时全量重验。
 *
 * ## 切换判据（fail-closed，R1 §2.4 表）
 *
 * 仅当主尝试是 **JS 方法**时可能切换；[FigureGenerationOutcome] 落到非 JS 腿（IMAGE 主尝试
 * 等）一律原样返回——IMAGE→JS（"图像不佳"）**不做机械回退**：program 只能来自模型，本地无
 * 确定性质量判据，凭感觉回退=把主观判断伪装成策略（R1 §2.4）。该方向登记为**模型回合驱动**：
 * 有界失败诊断经 `TutorVisualContext`（契约 §7 已立，载体在 `core/model`，生产零构造 A-07）
 * 回喂下一次已有预算内模型回合，由模型改选 JS+program，operationId 不变；接线归 X-01。
 *
 * | 主尝试结果（JS 腿） | 切换？ | 依据 |
 * |---|---|---|
 * | FAILED + 诊断以可归属拒绝分类前缀开头（[ATTRIBUTABLE_SWITCH_REJECTIONS]） | **切换** | 程序错=该输入下 JS 兑现不了目标，IMAGE 是合法另解 |
 * | FAILED + `INVALID_PROGRAM: LAYOUT_PIXEL_BUDGET: …`（M-03 排版预算越界折 `INVALID_PROGRAM`，`VisualLayoutBudget.transactionBudgetDiagnostic`） | **切换**（现行为；**M-03×V-02 交互登记**） | 落入 [ATTRIBUTABLE_SWITCH_REJECTIONS]（INVALID_PROGRAM）⇒ IMAGE 另解 + 远程预留（账本 6 上限不重置）。"越界属程序侧确定性超限还是输入侧越量（对照 INPUT_OVER_BUDGET 的'输入侧修输入'排除理由；IMAGE 产物不经 4MP 布局门）"为**开放裁定点**（主会话，X-01 生产接线前裁定）；改裁定须同步 [ATTRIBUTABLE_SWITCH_REJECTIONS] 与 `VisualLayoutBudget` 的映射注记两处 |
 * | FAILED + `[UNATTRIBUTED]` 前缀 | 不切换 | 协调器本已折 UNAVAILABLE；防御位（P-11：不可归属不回喂） |
 * | FAILED + 异常形状诊断（宿主帧/引擎异常，无分类前缀） | 不切换 | 设施类，fail-closed（R1 §2.4 未列入触发面） |
 * | UNAVAILABLE（引擎已关/无引擎/无帧） | 不切换 | IMAGE 走同一凭证门大概率同败，回退会把"本机没接通"掩盖成"方法不行" |
 * | REJECTED + JS 专面本地预算文案 | **切换一次** | JS 专面预算不迁移（墙钟/布局/源码是 JS 专面，IMAGE 由账本另管） |
 * | REJECTED + 其他（policy 校验、最终图上限） | 不切换 | proposal 非法换法无益；最终图门两方向都拒 |
 * | 账本 Denied → FAILED | 不切换 | 预算事实不得绕行——结构上 Denied 只发生在 IMAGE 腿而切换只从 JS 腿发起；防御位=拒绝消息无分类前缀 |
 * | CancellationException | 如实传播 | 取消路径无 catch，不折算成任何终态 |
 *
 * 判据消费 `outcome.status` + 诊断前缀（R1 §2.1：无需新分类器）。前缀识别的锚点：
 * 协调器对 JS 拒绝的有界诊断格式是 `"<VisualExecutionRejection.name>: <原文>"`
 * （`boundedRejection`），枚举名改名会让本文件编译失败（引用枚举常量而非字面量）；
 * JS 专面预算文案锚定 `FigureOperationLocalBudgets` 三条拒绝消息的公共形状——文案变更会让
 * 切换静默停用（fail-closed 方向：少切换、不误切），改文案必须同步本判据。
 *
 * ## 审计口径登记（R1 §2.2 登记项 3/4，实施时必须如实）
 *
 * - 同 operationId 第二次 attempt 的 `input_fingerprint` 按**新方法**派生（IMAGE 用
 *   imagePrompt）；`FigureGenerationDao.insertOperation` 是 `OnConflictStrategy.IGNORE`——
 *   操作行保留**首次**（JS 派生）指纹，per-attempt 的 `input_fingerprint` 才是载荷身份。
 * - 两次调用之间操作行会经 `FAILED(终态) → markOperationRunning → 新终态` 的短暂"复活"；
 *   进程恰死在中间 ⇒ 启动对账按 `FigureGenerationDao.findStaleOperations` 把僵尸 RUNNING
 *   行判待核实，attempt 行各自带真实终态——不产生假成功。
 *
 * ## fallback 工厂契约
 *
 * [imageFallback] 收到主尝试的有界诊断（≤512 字符），返回切换用 proposal；生产调用方应以
 * [FigureGenerationPolicy.imageModelProposal] 构造（载荷来自同一工具调用的 imagePrompt），
 * `reason` 登记切换来源（前一 attempt 有界诊断摘要）——契约 §8"模型所选 method/理由"与
 * §7"不隐藏重试/审查请求"的落点。`reason` 上限 [FigureGenerationPolicy.MAX_REASON_CHARS]
 * 且不得携带控制字符，工厂自行有界/净化。返回 null = 该次调用没有 IMAGE 另解（如工具调用
 * 未携带 imagePrompt），如实回主结果，不硬造。
 *
 * ## 前置依赖（如实登记）
 *
 * JS proposal 的生产接线（工具环 → 协调器 runner）main 暂无调用方（X-01 归位裁定待主会话）；
 * 本类是归位后的自然落点。设备半边用例依赖该接线；JVM 用例（`FigureMethodSwitchTest`）以
 * 真协调器 + fake 引擎/账本/通道/审计先行，替身形状与 `FigureGenerationLocalBudgetBoundaryTest` 同型。
 */
internal class FigureMethodSwitchRunner(
    private val coordinator: FigureGenerationCoordinator,
) {

    /**
     * 一次"主尝试 + 可选方法切换"的生成。主尝试失败且满足切换判据时，用 [imageFallback]
     * 构造的 IMAGE proposal 以**同一 request** 再调一次协调器；否则原样返回主结果。
     * 两次调用串行（R1 §2.1：同操作的调用在生产与测试中都是串行的）。
     */
    suspend fun generateWithMethodFallback(
        request: FigureGenerationRequest,
        primary: FigureGenerationProposal,
        imageFallback: (primaryFailure: String) -> FigureGenerationProposal?,
    ): FigureGenerationOutcome {
        val primaryOutcome = coordinator.generate(request, primary)
        if (primary.method != FigureGenerationMethod.JS || !shouldSwitchToImage(primaryOutcome)) {
            return primaryOutcome
        }
        val fallbackProposal = imageFallback(primaryOutcome.diagnostics.orEmpty()) ?: return primaryOutcome
        // 同一 request = 同 operationId、同授权/目标/预算约束：第二次 generate 让 policy 与
        // 账本对 fallback 全量重验，本类不做任何旁路。
        return coordinator.generate(request, fallbackProposal)
    }

    /** 切换判据（见类 KDoc 表）。取消与异常不进这里：协调器抛出即如实传播。 */
    private fun shouldSwitchToImage(primary: FigureGenerationOutcome): Boolean = when (primary.status) {
        FigureGenerationStatus.FAILED -> isAttributableJsFailure(primary.diagnostics)
        FigureGenerationStatus.REJECTED -> isJsLocalBudgetRejection(primary.diagnostics)
        else -> false
    }

    /**
     * FAILED 且诊断以可归属拒绝分类前缀开头（程序侧五类 + 硬期限，R1 §2.4 行 1；排除面与
     * 理由见类 KDoc 表）。`[UNATTRIBUTED]` 前缀防御位：协调器已把该形状折 UNAVAILABLE，
     * 这里再挡一次（前缀常量同源复用，不另造字面量）。
     */
    private fun isAttributableJsFailure(diagnostics: String?): Boolean {
        val text = diagnostics ?: return false
        if (text.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX)) return false
        // 主会话裁定（批次 38 收口）：M-03 的 4MP 排版预算越界是**设备资源侧限制**，
        // 不是程序缺陷——自动切 IMAGE 会烧一次付费派发去绕本地资源门（静默付费升级），
        // 与排除面里 INPUT_OVER_BUDGET 的"输入/资源侧不切换"理由同族。fail-closed：
        // 原样落 FAILED 带码诊断，切不切 IMAGE 交产品决策（类 KDoc 表 M-03×V-02 行已裁）。
        if (text.contains(LAYOUT_PIXEL_BUDGET_MARKER)) return false
        return ATTRIBUTABLE_SWITCH_REJECTIONS.any { text.startsWith("${it.name}: ") }
    }

    /**
     * REJECTED 且为 JS 专面本地预算拒绝（R1 §2.4 行 4 的"允许转 IMAGE 一次"半边）。
     * 三条 JS 专面消息（墙钟/布局/源码）公共形状 = "本逻辑操作… JS …"前缀；最终图上限
     * （"本逻辑操作已产出 N 份最终图…"）与 policy 拒绝文案均不含 JS 令牌——不切换。
     */
    private fun isJsLocalBudgetRejection(diagnostics: String?): Boolean {
        val text = diagnostics ?: return false
        return text.startsWith(JS_LOCAL_BUDGET_REJECTION_PREFIX) && text.contains(JS_LOCAL_BUDGET_TOKEN)
    }

    private companion object {
        /**
         * 可归属切换的拒绝分类（R1 §2.4 行 1：程序侧五类 + 硬期限）。排除：INPUT_OVER_BUDGET
         * （输入侧，调用方修输入）、PROGRAM_FUSED/BUSY/ENGINE_CLOSED（编排侧/设施类）——
         * 这些换 IMAGE 不是"合法另解"或属设施失败；未列入的新枚举值 fail-closed 不切换。
         * M-03 排版预算越界（`LAYOUT_PIXEL_BUDGET`）经引擎折 `INVALID_PROGRAM`，但**裁定
         * 不切换**（2026-10-31 主会话，批次 38 收口）：资源侧限制不烧付费派发，排除见
         * isAttributableJsFailure 内注释与 [LAYOUT_PIXEL_BUDGET_MARKER]；类 KDoc 表
         * （M-03×V-02 行）已裁。
         */
        val ATTRIBUTABLE_SWITCH_REJECTIONS: Set<VisualExecutionRejection> = setOf(
            VisualExecutionRejection.INVALID_PROGRAM,
            VisualExecutionRejection.SANDBOX_VIOLATION,
            VisualExecutionRejection.MEMORY_LIMIT,
            VisualExecutionRejection.PHASE_TIMEOUT,
            VisualExecutionRejection.INVALID_SCENE,
            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
        )

        /**
         * M-03 排版预算越界诊断令牌（VisualLayoutBudget.kt:64/:102；经引擎折
         * `INVALID_PROGRAM: LAYOUT_PIXEL_BUDGET: …` 有界诊断）。裁定点见上——含此令牌的
         * FAILED 一律不触发 JS→IMAGE 切换。
         */
        const val LAYOUT_PIXEL_BUDGET_MARKER: String = " LAYOUT_PIXEL_BUDGET: "

        /** `FigureOperationLocalBudgets` 三条 JS 专面拒绝消息的公共前缀（实读 :59/:72/:79）。 */
        const val JS_LOCAL_BUDGET_REJECTION_PREFIX: String = "本逻辑操作"

        /** JS 令牌（最终图上限消息不含它——跨方法门不得据此切换）。 */
        const val JS_LOCAL_BUDGET_TOKEN: String = " JS "
    }
}
