package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.CancellationException

/**
 * 预览注入开关（D-04 生产装配的**显式**开关）。
 *
 * [DISABLED] 是默认态，且必须是 MVA（4E.2 准入）通过前的唯一生产态：
 * 开关关闭时本装配与批次 39 之前**逐位相同**——`previewAssetId` 原样保留（生产恒 null），
 * 不调用渲染器、不分配位图、不写 vault、不登记资产行。
 *
 * [ENABLED] 只由显式实参打开（不得由能力/提示词/远端配置间接触发）：
 * 预览生成成功 → 把资产 id 注入快照输入；预览任一失败 → `previewAssetId = null`
 * 且提交照常进行（R1 `docs/research/2026-11-01-preview-wiring.md` §2.3）。
 *
 * **它消灭的具体失败**：若把开关做成布尔默认值散落在调用点，某处复制粘贴写成
 * `enabled = true` 就会在 MVA 未过时静默打开一条未验收的显示缓存写路径；枚举的两个态
 * 可在编译期被穷举，且默认参数只能是 [DISABLED]（构造签名见 [TutorVisualPreviewAssembly]）。
 */
public enum class TutorVisualPreviewInjection {
    /** 默认态：零渲染、零写入，快照输入原样返回。 */
    DISABLED,

    /** 显式开启：按 R1 §2.1 生成预览并注入；失败有界降级为 `previewAssetId = null`。 */
    ENABLED,
}

/**
 * 装配边界的**有界结果**：调用方拿 [snapshot] 去提交，把 [diagnostic] 写诊断面（不进显示）。
 *
 * [diagnostic] 为 null 有两种合法含义：开关关闭（未尝试）与预览成功（无需诊断）。调用方
 * 不应据此推断"预览可用"——可用性只由 `snapshot.previewAssetId != null` 表达。
 */
public data class TutorVisualPreviewInjectionResult(
    val snapshot: TutorVisualSnapshotInput,
    val diagnostic: String?,
)

/**
 * D-04 预览的**生产注入点**（唯一入口；默认关闭）。
 *
 * **它消灭的具体失败**：渲染器（[TutorVisualPreviewRenderer]）与仓储
 * （[TutorVisualCommitRepository.commitAssistantTurnWithVisual]）之间原本没有任何生产接缝，
 * 每个 commit 调用点若要预览，都得自己重复四件事——判断开关、渲染、在失败时把
 * `previewAssetId` 归 null、把诊断带出去。任一步写错就是两类事故：
 * - 预览失败被当成提交失败 → 一个③档显示缓存（预览）的字节/预算问题**阻断用户拿到
 *   完整图**（正文/program/快照一起不成立）。R1 §2.3 明确这是方向性错误：预览是增强物，
 *   不是完整性依赖；
 * - 预览失败时沿用调用方手里旧的 `previewAssetId` → 快照引用一张与本次内容无关的资产
 *   （显示缓存与回放语义说谎）。
 *
 * 本类把四步收成一个 `suspend` 调用并固定失败语义：**快照永不因预览失败而失败**，
 * 拒绝时 `previewAssetId` 显式置 null（不继承旧值）。
 *
 * **写入点**（R1 §2.2）：只允许在 `commitAssistantTurnWithVisual`（LIVE 首张可见图）路径
 * 调用，**不得**在 `stageSnapshot`（STAGING 是 60 分钟租约的临时中间态，回收后预览必成
 * 孤儿）。本类不代替仓储提交，也不改变仓储语义：调用方拿到 [TutorVisualPreviewInjectionResult.snapshot]
 * 后走既有 `commitAssistantTurnWithVisual`。
 *
 * **顺序即语义**：渲染 → `TutorVisualPreviewAssetPersister.persist`（字节→行原子两步）→
 * 返回 id → 本类把 id 填进快照输入 → 仓储提交。引用建立时资产行必然已在（RESTRICT FK
 * 不会被拒）；persist 成功但快照未提交时留下的未引用行由六站点谓词 GC 自愈。
 *
 * **R-04 登记项**（R1 §2.7）：生产 commit 接通之前本类只被测试引用 → release 轨 keep
 * 义务与 `VisualChainTestSeams` 一并在 `app/r04-test-keep.pro` 管理，不得把涉及本类的
 * release 轨设备例列入 R-04 清单。
 *
 * @param renderer 预览渲染端口。生产装配用
 *   [TutorVisualPreviewRendererFactory.forProduction]；注入式装配（测试/自定义面）用
 *   [TutorVisualPreviewRendererFactory.create]。
 * @param injection 默认 [TutorVisualPreviewInjection.DISABLED]——调用方不显式传 `ENABLED`
 *   时行为与现有生产逐位相同。
 */
public class TutorVisualPreviewAssembly(
    private val renderer: TutorVisualPreviewRenderer,
    private val injection: TutorVisualPreviewInjection = TutorVisualPreviewInjection.DISABLED,
) {

    /** 开关是否处于显式开启态（装配诊断用；MVA 通过前生产必须为 false）。 */
    public val isEnabled: Boolean get() = injection == TutorVisualPreviewInjection.ENABLED

    /**
     * 按开关处理一次快照输入的预览注入。
     *
     * 关闭（默认）：立即返回**同一个** [snapshot]（含调用方可能已有的 `previewAssetId`），
     * `diagnostic = null`；不触碰 [renderer]。
     *
     * 开启：[request] 的冻结帧必须与 [snapshot] 同源（视口宽高与 fontScale 逐值相等），
     * 否则抛 [IllegalArgumentException]——预览是"这张快照的样子"的显示缓存，输入漂移
     * = 显示缓存与回放语义说谎（R1 §2.1）。随后：
     * - `Accepted` → `snapshot.copy(previewAssetId = 资产 id)`；
     * - `Refused` / 渲染器抛异常 / `OutOfMemoryError` → `snapshot.copy(previewAssetId = null)`
     *   + 有界诊断（≤ [FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS]）。**不重试、不缩放、
     *   不静默降级为"正确图"**（R1 §2.3）；
     * - [CancellationException] 原样重抛（取消不是超限，吞掉会把取消伪装成"预览不可用"）。
     */
    public suspend fun inject(
        request: TutorVisualPreviewRequest,
        snapshot: TutorVisualSnapshotInput,
    ): TutorVisualPreviewInjectionResult {
        if (!isEnabled) {
            return TutorVisualPreviewInjectionResult(snapshot = snapshot, diagnostic = null)
        }
        require(
            request.viewportWidthDu == snapshot.viewportWidthDu &&
                request.viewportHeightDu == snapshot.viewportHeightDu,
        ) {
            "The preview request viewport ${request.viewportWidthDu}x${request.viewportHeightDu} du " +
                "must equal the snapshot viewport ${snapshot.viewportWidthDu}x${snapshot.viewportHeightDu} du " +
                "(the preview is this snapshot's display cache; a drifted frame makes it lie)"
        }
        require(request.fontScaleMilli == snapshot.fontScaleMilli) {
            "The preview request font scale ${request.fontScaleMilli} must equal the snapshot " +
                "font scale ${snapshot.fontScaleMilli} (same frozen frame or the cache is wrong)"
        }

        val outcome = try {
            renderer.renderPreview(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return refused(
                snapshot = snapshot,
                code = PREVIEW_INJECTION_FAILED,
                detail = "${failure::class.simpleName}: ${failure.message ?: "无详细信息"}",
            )
        } catch (outOfMemory: OutOfMemoryError) {
            return refused(
                snapshot = snapshot,
                code = PREVIEW_INJECTION_FAILED,
                detail = "OutOfMemoryError while rendering the preview " +
                    "(the renderer's 16 MiB pre-allocation gate is the first line; this is the seam backstop)",
            )
        }

        return when (outcome) {
            is TutorVisualPreviewOutcome.Accepted -> TutorVisualPreviewInjectionResult(
                snapshot = snapshot.copy(previewAssetId = outcome.previewAssetId),
                diagnostic = null,
            )
            is TutorVisualPreviewOutcome.Refused -> refused(
                snapshot = snapshot,
                code = outcome.code,
                detail = outcome.diagnostic,
            )
        }
    }

    private fun refused(
        snapshot: TutorVisualSnapshotInput,
        code: String,
        detail: String,
    ): TutorVisualPreviewInjectionResult {
        val message = if (detail.startsWith(code)) detail else "$code: $detail"
        return TutorVisualPreviewInjectionResult(
            snapshot = snapshot.copy(previewAssetId = null),
            diagnostic = message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS),
        )
    }
}

/**
 * 装配边界兜底失败的折叠码（渲染器实现自身已把门拒/绘制拒/persist 拒折
 * [TutorVisualPreviewOutcome.Refused]；本码只覆盖"注入进来的端口直接抛异常"与 OOM 兜底）。
 */
internal const val PREVIEW_INJECTION_FAILED = "PREVIEW_INJECTION_FAILED"
