package com.tingyun.smartmistakebook.core.model

/**
 * 数学内容的**共享 lowering**（批次 38 S-08；设计依据 R2
 * `docs/research/2026-11-01-measure-artifact-and-budget.md` §5，契约 §5 公式段：
 * "现有 `MathBudget.kt` 的 parser/AST/budget 可复用……第一版支持范围须由公式金样限定，
 * 未知 TeX 回可读文本，不宣称完整 LaTeX。不得继续增加第三套公式/坐标绘制逻辑。"）。
 *
 * **它消灭的具体失败**：降级判断（解析/预算失败 → 可读文本）此前只散在 app 度量侧私有
 * 函数里（`measureMathBox`/`measureContentEntry`，`app/.../visualjs/VisualContentMeasuring.kt`），
 * 跨模块（core:data 回放、core:ui、core:export）没有可调用的共享入口——未来 painter 回放
 * 解锁 math 时要么**复制**降级判断（变相第二套降级逻辑 = 违反"不得第三套"语义），要么
 * internal 泄漏。本对象把"parse → AST → budget → box"与"失败 → 原文透传"收敛成唯一 public
 * 纯 Kotlin 入口（零 android 依赖，与 [MathBudget] 同模块同约束），度量侧已改为调用它
 * （app 行为零变化，由 `MathContentLoweringTest` 与既有 app 金样回归钉住）。
 *
 * **降级语义（两段式，缺一即漏；R2 §5.2）**：
 * 1. **预算越界**（真实失败面）：解析器对未知 TeX 极度宽容——未知命令降级为字面 atom，
 *    解析几乎不失败；失败主体是预算（输入 >[MathBudget.MAX_INPUT_LENGTH] 2000 字符、
 *    token/节点/深度/盒尺寸上限）→ [Result.FallbackText] 携带 budget error 字符串；
 * 2. **真解析异常**（防御面，`parseAndBuildBox`/`MathParser.parse` 现树无 throw 点，与
 *    [ReadableMathText.formula] 的 catch 同款保守）：任何异常都折进 [Result.FallbackText]，
 *    绝不向上抛。
 *
 * 两段的 fallback 文本都是**原文透传**（[Result.FallbackText.readable] == 输入，不重建）：
 * 与 [ReadableMathText] 的"原文透传（不重建）"原则同源；度量侧以同一文本走 text 度量路径，
 * 未来回放以同一文本绘制——单一事实来源，度量与回放不可能失配。注意**不要**用
 * [ReadableMathText.formula] 的可读重建文本当 fallback：预算越界的输入往往解析成功，
 * 重建文本 ≠ 原文，而度量侧量的是原文（行为零变化红线）。
 *
 * 支持范围由公式金样限定（`MathBoxGoldenTest` 及其扩集）；金样外的输入不承诺盒形态、
 * 只承诺可读文本——这正是"不宣称完整 LaTeX"的落点。
 */
object MathContentLowering {

    /** lowering 结果：成功 → 布局盒；失败 → 可读文本（原文透传）。 */
    sealed interface Result {

        /**
         * 成功：[MathBoxBuilder.buildBox] 产出的布局盒（width/height/baseline 自顶口径）。
         * [box] 用 [lower] 的 `fontSizePx` 对应的 [MathMetrics] 构建；[parse] 是
         * [parseAndBuildBox] 的完整预算结果（token/node/depth 计数供诊断）。坐标语义：
         * 几何唯一来源是 builder（树内递归布局），绘制侧只消费 box 不重算几何（契约 §5
         * "明确 x/y 的共享 lowering"）。
         */
        data class Box(
            val box: MathBox,
            val node: MathNode,
            val parse: MathParseResult,
        ) : Result

        /**
         * 失败：[readable] 是应显示/度量的文本（**原文透传**，恒等于输入）；[error] 是
         * 失败原因（预算 error 字符串或防御 catch 的异常消息），只进诊断、不进显示。
         */
        data class FallbackText(
            val readable: String,
            val error: String,
        ) : Result
    }

    /**
     * 唯一入口：`parse → AST → budget → box`（成功）或"未知 TeX 回可读文本"（失败）。
     * 永不抛异常（防御 catch 兜底）；[fontSizePx] 非有限/非正时 [MathMetrics.of] 自带
     * 16px 回退（与度量环境门无关的纯函数兜底）。
     */
    fun lower(input: String, fontSizePx: Float): Result {
        val parsed = try {
            parseAndBuildBox(input)
        } catch (failure: Exception) {
            // 防御段（现树无 throw 点）：连"宽容解析"都不成立的输入原文透传，绝不带病降级失败。
            return Result.FallbackText(
                readable = input,
                error = "math lowering parse failed: ${failure.message ?: failure.javaClass.simpleName}",
            )
        }
        if (!parsed.isSuccess) {
            // 预算段（真实失败面）：携带 parseAndBuildBox 的预算 error 字符串。
            return Result.FallbackText(
                readable = input,
                error = parsed.error ?: "math lowering failed without an error message",
            )
        }
        val node = parsed.node
            ?: return Result.FallbackText(
                readable = input,
                error = "math parse reported success without a node",
            )
        val box = MathBoxBuilder.buildBox(node, MathMetrics.of(fontSizePx))
        return Result.Box(box = box, node = node, parse = parsed)
    }
}
