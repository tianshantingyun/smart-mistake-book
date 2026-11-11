package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.CancellationException

/**
 * 预览生成输入（D-04 生产接线的公共入参；全部来自"提交时已在手"的数据，零新增查询）。
 *
 * 设计依据 R1 `docs/research/2026-11-01-preview-wiring.md` §2.1/§2.5。帧输入（viewport/
 * fontScale/时间戳）必须与快照行**同一份冻结帧**：预览是"这张快照的样子"的显示缓存，
 * 输入漂移 = 显示缓存与回放语义说谎。
 *
 * @param rendered 引擎产出（`drawCommandsJson` 已过引擎侧 `VisualDrawOpsValidator` 复验，
 *   `core/domain/VisualExecutionEngine.kt:236-239` KDoc）；渲染器仍做恢复级复检（不信任
 *   "自称 validated"）。
 * @param viewportWidthDu / viewportHeightDu 快照冻结视口（du）——[rendered] 不携 viewport
 *   （S4 锁窗口记录），由快照输入携带；预览的 px 定界与 4 MP 准入都以此为准。
 * @param fontScaleMilli 冻结帧 fontScale×1000（与快照行同列同源，整数避免浮点争议）。
 * @param labels 绘制 label 用的内容（contentId → 度量 artifact + 文本载荷）。artifact 是
 *   public 的 [VisualMeasuredLayoutArtifact]（度量侧产物）；artifact→painter DTO 的折算
 *   复用 M-01/replay 批同一行段规则（不造第二套），见 [TutorVisualPreviewRendererImpl]
 *   内的单一折算点。缺某 contentId 的条目 = 绘制时 [PAINTER_UNKNOWN_LABEL]（有码拒绝）。
 * @param measuredEntries 度量条目（4 MP **合计**判的"文字 artifact"半边，通道甲）。
 *   null = 合计退化为"预览独判 ≤ 4 MP"（必要条件仍强制），**合并语义如实登记为待接线**
 *   （R1 §2.5 通道甲：装配点当前传不了度量器条目，接入后改传真值，判据面零改动）。
 * @param createdAtEpochMillis 预览资产行的时间戳；生产装配点传入与仓储 `clock()` 同源的
 *   同一 `now` 读数，避免资产行与快照行时间戳各说各话（R1 §2.2-3）。
 */
public data class TutorVisualPreviewRequest(
    val rendered: VisualExecutionResult.Rendered,
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    val fontScaleMilli: Int,
    val createdAtEpochMillis: Long,
    val labels: List<TutorVisualPreviewLabel> = emptyList(),
    val measuredEntries: List<VisualMeasuredContent>? = null,
    val preferredPxPerDu: Float = 1f,
) {
    init {
        require(viewportWidthDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A preview viewport width must be within the canvas budget, got $viewportWidthDu"
        }
        require(viewportHeightDu in 1..VisualExecutionBudget.MAX_CANVAS_EDGE_DU) {
            "A preview viewport height must be within the canvas budget, got $viewportHeightDu"
        }
        require(fontScaleMilli >= 1) { "A preview font scale must be positive, got $fontScaleMilli" }
        require(preferredPxPerDu.isFinite() && preferredPxPerDu in 1f..3f)
        require(createdAtEpochMillis >= 0L) {
            "A preview timestamp must not be negative, got $createdAtEpochMillis"
        }
        require(labels.size == labels.map { it.contentId }.toSet().size) {
            "A preview request needs distinct label content ids (duplicate keys break the " +
                "contentId → content resolution determinism)"
        }
    }
}

/** 一条预览 label 的绘制内容：冻结 contentId + 文本载荷 + 该内容的度量 artifact。 */
public data class TutorVisualPreviewLabel(
    val contentId: String,
    val payload: String,
    val artifact: VisualMeasuredLayoutArtifact,
) {
    init {
        require(contentId.isNotBlank()) { "A preview label needs a content id" }
        require(payload.isNotEmpty()) { "A preview label needs a payload" }
    }
}

/**
 * 预览生成的**有界结果**（R1 §2.3 统一降级出口的返回形态）：
 * - [Accepted]：预览已落成 canonical 资产并登记行，[previewAssetId] 即
 *   `TutorVisualSnapshotInput.previewAssetId` 的值；
 * - [Refused]：预览缺席（装配点以 `previewAssetId = null` 照常提交快照 + 把 [code]/
 *   [diagnostic] 写进诊断面）。**快照提交本身永不因预览失败而失败**——反例推演见 R1 §2.3：
 *   把显示缓存的存在性问题折成提交失败，是把增强物提升成完整性依赖。
 *
 * [diagnostic] 形如 `"CODE: detail"`（与 `paintingFailure` / 预算门消息同形），有界
 * （≤ [FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS]）；[code] 供程序化分流。
 */
public sealed interface TutorVisualPreviewOutcome {
    public data class Accepted(val previewAssetId: String) : TutorVisualPreviewOutcome

    public data class Refused(val code: String, val diagnostic: String) : TutorVisualPreviewOutcome
}

/**
 * 预览渲染端口（D-04 生产接线缝；沿 `FigureGenerationCoordinationFactory` 的
 * public-缝/internal-机先例——装配点只看本接口，internal 机不可达也不需可达）。
 *
 * **它消灭的具体失败**（R1 §2.6）：调点自拼"解析→画→门→压缩→persist→登记"六步——
 * 漏门 = 超尺寸位图在分配点 OOM 变进程死亡；漏登记行 = RESTRICT FK 拒掉整个快照事务
 * 而无可读原因；各点自定 pxPerDu = 同内容跨设备不同字节（内容寻址幂等破）。本端口把
 * 六步收成一次调用，失败语义固定为有界降级（[TutorVisualPreviewOutcome.Refused]）。
 *
 * **写入点与时点**（R1 §2.2）：只在快照 commit（LIVE 首张可见图）路径调用——**不在**
 * `stageSnapshot`（STAGING 是 60 分钟租约的临时中间态，被回收后预览必成孤儿）。
 * **顺序即语义**（`TutorVisualPreviewAssetPersister` KDoc 已钉）：渲染 → persist（字节→
 * 行原子两步）→ 返回 `previewAssetId` → 调用方填进快照输入 → 仓储提交。引用建立时资产行
 * 必然已在。不在仓储/引擎内部做（DB 层保持纯事务语义；JS 事务内不掺宿主位图工作）。
 *
 * **幂等**：内容寻址（同内容重复提交 → 同字节 → 同一行，`INSERT OR IGNORE`）要求渲染
 * 确定性——pxPerDu 策略**不依赖设备 density**（预览自定界，见
 * [TutorVisualPreviewRendererFactory.PREVIEW_EDGE_PX]），同 drawCommands + 同帧输入 +
 * 同策略 + 同 renderer 版本 → 同字节。
 *
 * **失败语义**（R1 §2.3 全表）：门拒/绘制拒/persist 拒一律折 [TutorVisualPreviewOutcome.Refused]
 * （快照照常提交）；已登记未引用的资产行由六站点谓词
 * （`CanonicalAssetReferenceSites.UNREFERENCED_PREDICATE_SQL` 第六分支）GC 回收——降级自愈，
 * 无泄漏。[CancellationException] **重抛**（取消不是超限，吞掉会把取消伪装成"预览不可用"）。
 *
 * **R-04 登记项**（R1 §2.7）：生产 commit 接通之前本端口只被 androidTest 引用 → 主包 R8
 * 可收缩 → **涉及本端口的 release 轨设备例不得进 R-04 清单**，keep 义务与
 * `VisualChainTestSeams` 一并在 `app/r04-test-keep.pro` 登记（两批合并管理，避免门脚本漂移）。
 */
public interface TutorVisualPreviewRenderer {

    public suspend fun renderPreview(request: TutorVisualPreviewRequest): TutorVisualPreviewOutcome
}

/**
 * 预览定界策略常量与生产装配口。
 *
 * @property PREVIEW_EDGE_PX 预览长边（px）策略值。沿仓内消费方既有分辨率上限先例
 *   `BoundedLocalImage.MAX_PREVIEW_EDGE = 1_600`（1600²×4 = 10.24 MB，对 16 MiB 门留
 *   36% 余量；③档显示在手机屏上 1600 px 长边已超屏宽；1600² du = 2.56 MP ≤ 4 MP，
 *   给"文字+预览合计"留余量）。
 * @property MAX_PREVIEW_EDGE_PX 策略值的**不可越线**：2048²×4 = 16,777,216 B = 恰达
 *   16 MiB 门（≤ 放行），2049 px 即越。策略是第一道、门（[VisualLayoutBudget.requireBitmapBytesWithinBudget]）
 *   是第二道——改策略忘了门，门兜底；但每次门触发都是策略缺陷的回归，故策略入口在
 *   [previewPxPerDu] 对常量本身 require 此上界（调大即当场炸出，不是静默永久降级）。
 */
public object TutorVisualPreviewRendererFactory {

    public const val PREVIEW_EDGE_PX: Int = 1_600

    public const val MAX_PREVIEW_EDGE_PX: Int = 2_048

    /**
     * 生产装配：字节走 vault 的统一写入面（与拍照、裁剪、生成图同一套解码/尺寸/字节预算），
     * 行登记走数据库端口——`TutorVisualPreviewAssetPersister.forProduction` 的同源组合。
     */
    public fun forProduction(
        context: Context,
        database: StudyDatabasePort,
    ): TutorVisualPreviewRenderer =
        create(TutorVisualPreviewAssetPersister.forProduction(context, database))

    /** 注入式装配（测试/自定义装配面）：预览写侧由调用方提供。 */
    public fun create(persister: TutorVisualPreviewAssetPersister): TutorVisualPreviewRenderer =
        TutorVisualPreviewRendererImpl(persister)
}

/** 残余失败（OOM 兜底、persist 故障、压缩失败）的折叠码：有界诊断携带异常类名可分流。 */
internal const val PREVIEW_RENDER_FAILED = "PREVIEW_RENDER_FAILED"

/**
 * 预览 pxPerDu 的**定界策略**（纯函数，JVM 可测）：`min(1f, PREVIEW_EDGE_PX / max(wDu, hDu))`。
 *
 * **不取设备 density**（R1 §2.4 的根本裁定）：预览是内容寻址的显示缓存，pxPerDu 若随设备
 * density 变化，同内容跨设备不同字节 → 幂等破；基于 du 自定界后，同内容在任何设备上
 * 得到同一 px 尺寸、同一 PNG 字节。4096² du 满幅画布 → 1600² px（10.24 MB），16 MiB 门
 * 必然放行；越线请求由门拒绝（策略不缩采样、不静默降质）。
 */
internal fun previewPxPerDu(viewportWidthDu: Int, viewportHeightDu: Int, preferredPxPerDu: Float = 1f): Float {
    require(preferredPxPerDu.isFinite() && preferredPxPerDu in 1f..3f)
    require(
        TutorVisualPreviewRendererFactory.PREVIEW_EDGE_PX in
            1..TutorVisualPreviewRendererFactory.MAX_PREVIEW_EDGE_PX,
    ) {
        "The preview edge policy ${TutorVisualPreviewRendererFactory.PREVIEW_EDGE_PX} px must stay " +
            "within the ${TutorVisualPreviewRendererFactory.MAX_PREVIEW_EDGE_PX} px hard cap (2048²×4 " +
            "is exactly the 16 MiB gate; beyond it every full-canvas preview degrades silently)"
    }
    val maxEdgeDu = maxOf(viewportWidthDu, viewportHeightDu)
    return min(preferredPxPerDu, TutorVisualPreviewRendererFactory.PREVIEW_EDGE_PX.toFloat() / maxEdgeDu)
}

/**
 * 预览准入门（4 MP **合计**判的预览侧形态；R1 §2.5）：预览 du 面积
 * = `ceil(viewportWidthDu) × ceil(viewportHeightDu)`（预览覆盖整幅画布，du 域不因 px
 * 缩采样而变）+ [measuredEntries] 的文字盒累计（null = 预览独判，通道甲登记的退化语义）。
 *
 * 常量与口径同源 [VisualLayoutBudget]（`boxPixelsDu`/`totalLayoutPixelsDu`/
 * `MAX_TOTAL_LAYOUT_PIXELS_DU`），不另立数字。返回 null = 准入；非 null = 形如
 * `"LAYOUT_PIXEL_BUDGET: …"` 的有界拒绝诊断（与 `transactionBudgetDiagnostic` 同族形状，
 * 可直接进诊断面）。
 *
 * **为什么不在引擎结算处并入**（R1 §2.5 裁定）：引擎结算（`QuickJsVisualExecutionEngine`
 * 度量结算处）越界折 INVALID_PROGRAM = 整张图作废——4096² 合法画布会因"预览放不下"被
 * 连坐杀掉。显示缓存的存在性问题只拒预览，不动摇快照本体。
 */
internal fun previewAdmissionDiagnostic(
    viewportWidthDu: Int,
    viewportHeightDu: Int,
    measuredEntries: List<VisualMeasuredContent>?,
): String? {
    val previewPixels = VisualLayoutBudget.boxPixelsDu(
        viewportWidthDu.toFloat(),
        viewportHeightDu.toFloat(),
    )
    val measuredPixels = measuredEntries?.let(VisualLayoutBudget::totalLayoutPixelsDu) ?: 0.0
    val total = previewPixels + measuredPixels
    if (total <= VisualLayoutBudget.MAX_TOTAL_LAYOUT_PIXELS_DU.toDouble()) {
        return null
    }
    return "${VisualLayoutBudget.LAYOUT_PIXEL_BUDGET}: preview canvas $previewPixels du² plus " +
        "measured content $measuredPixels du² = $total du² exceeds " +
        "${VisualLayoutBudget.MAX_TOTAL_LAYOUT_PIXELS_DU} (the preview is a display cache and is " +
        "refused alone; the snapshot is not affected)"
}

/**
 * 预览渲染机（internal；D-04 生产接线的六步收口实现，R1 §2.1 管线）：
 *
 * 4 MP 准入（纯计算）→ `VisualDrawOpsParser.parse(rendered)` 恢复级复检 → math 盒边界 →
 * artifact→DTO 折算 → pxPerDu 定界 → **16 MiB 门（`createBitmap` 前，M-03 分配点接线）** →
 * 分配/绘制 → PNG 压缩 → `Persister.persist`（字节→行原子）→ [TutorVisualPreviewOutcome.Accepted]。
 *
 * 任何有界失败折 [TutorVisualPreviewOutcome.Refused]；[CancellationException] 重抛；
 * `OutOfMemoryError` 兜底折码（同 `AndroidCanonicalAssetVault.persistCleanImageBytes` 的
 * OOM 处置形态——门在前 + 兜底在后，两层都不让 OOM 变进程死亡）。
 */
internal class TutorVisualPreviewRendererImpl(
    private val persister: TutorVisualPreviewAssetPersister,
) : TutorVisualPreviewRenderer {

    override suspend fun renderPreview(request: TutorVisualPreviewRequest): TutorVisualPreviewOutcome =
        renderPreview(request, pxPerDuOverride = null)

    /**
     * 同模块测试入口（N6 设备向量"注入 pxPerDu 越过策略"形态，R1 §3.2）：非 null 时跳过
     * 定界策略直接以注入比例定 px 尺寸。向量对象是**门不是策略**——生产策略上限
     * （1600 px）不可能自然产生越门请求，只有注入能让"16 MiB 门接在分配点**前**"这条
     * 接线本身获得设备执行面。公开端口不暴露该入口（零公共面增量）。
     */
    internal suspend fun renderPreview(
        request: TutorVisualPreviewRequest,
        pxPerDuOverride: Float?,
    ): TutorVisualPreviewOutcome = try {
        renderBounded(request, pxPerDuOverride)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (painting: VisualDrawOpsPaintingException) {
        TutorVisualPreviewOutcome.Refused(
            code = painting.code,
            diagnostic = bounded(painting.message ?: "无详细信息"),
        )
    } catch (failure: Exception) {
        TutorVisualPreviewOutcome.Refused(
            code = PREVIEW_RENDER_FAILED,
            diagnostic = bounded("${failure::class.simpleName}: ${failure.message ?: "无详细信息"}"),
        )
    } catch (outOfMemory: OutOfMemoryError) {
        TutorVisualPreviewOutcome.Refused(
            code = PREVIEW_RENDER_FAILED,
            diagnostic = bounded(
                "$PREVIEW_RENDER_FAILED: OutOfMemoryError while rendering the preview " +
                    "(the 16 MiB pre-allocation gate is the first line; this is its backstop)",
            ),
        )
    }

    private suspend fun renderBounded(
        request: TutorVisualPreviewRequest,
        pxPerDuOverride: Float?,
    ): TutorVisualPreviewOutcome {
        // 1) 4 MP 准入（纯计算，du 域）——最廉价的拒绝点，先于解析与任何分配。
        previewAdmissionDiagnostic(
            request.viewportWidthDu,
            request.viewportHeightDu,
            request.measuredEntries,
        )?.let { printable ->
            return TutorVisualPreviewOutcome.Refused(
                code = VisualLayoutBudget.LAYOUT_PIXEL_BUDGET,
                diagnostic = bounded(printable),
            )
        }

        // 2) 恢复级复检：与快照回放（VisualDrawOpsPainterRendered.drawRendered）同一解析
        //    入口、同一拒绝码，不信任"自称 validated"的引擎产物。
        val document = when (val parsed = VisualDrawOpsParser.parse(request.rendered)) {
            is VisualDrawOpsValidation.Valid -> parsed.document
            is VisualDrawOpsValidation.Invalid -> return TutorVisualPreviewOutcome.Refused(
                code = PAINTER_DRAW_COMMANDS,
                diagnostic = bounded("$PAINTER_DRAW_COMMANDS: ${parsed.code}: ${parsed.message}"),
            )
        }

        // 3) math 盒边界：预览继承 painter 的 fail-closed（PAINTER_LABEL_MATH），不做预览
        //    特例。折算前置判定（而非把 Math DTO 交给 painter 再炸）是因为 artifact 不携带
        //    MathBox、无法构造该 DTO——语义不变：math 内容 = 整张预览有界拒绝。
        val mathLabel = request.labels.firstOrNull { it.artifact.lines == null }
        if (mathLabel != null) {
            return TutorVisualPreviewOutcome.Refused(
                code = PAINTER_LABEL_MATH,
                diagnostic = bounded(
                    "$PAINTER_LABEL_MATH: preview inherits the math fail-closed boundary; " +
                        "contentId \"${mathLabel.contentId.take(32)}\" measured as a math box and " +
                        "has no paintable lowering this batch",
                ),
            )
        }

        // 4) artifact→DTO 单一折算点：与 M-01/replay 批同一行段规则（lines→(start,end)、
        //    heightDu、sizeSp、fontScale），不为预览造第二套（契约"不得继续增加第三套
        //    公式/坐标绘制逻辑"的同族纪律）。
        val labelContents: Map<String, VisualLabelContent> = request.labels.associate { label ->
            val lines = requireNotNull(label.artifact.lines) { "math labels are refused above" }
            label.contentId to VisualLabelContent.Text(
                payload = label.payload,
                sizeSp = label.artifact.fontSizeSp,
                fontScale = request.fontScaleMilli / 1_000f,
                heightDu = label.artifact.heightDu,
                lines = lines.map { VisualLabelLine(start = it.start, endExclusive = it.endExclusive) },
            )
        }

        // 5) 定界策略 + **先门后分配**（M-03 接线点，VisualLayoutBudget.kt:108-128 的约定：
        //    "分配不发生"）。
        val pxPerDu = pxPerDuOverride ?: previewPxPerDu(request.viewportWidthDu, request.viewportHeightDu, request.preferredPxPerDu)
        val widthPx = ceil(request.viewportWidthDu * pxPerDu).toInt()
        val heightPx = ceil(request.viewportHeightDu * pxPerDu).toInt()
        try {
            VisualLayoutBudget.requireBitmapBytesWithinBudget(
                widthPx,
                heightPx,
                VisualLayoutBudget.ARGB_8888_BYTES_PER_PIXEL,
            )
        } catch (budget: IllegalArgumentException) {
            return TutorVisualPreviewOutcome.Refused(
                code = VisualLayoutBudget.BITMAP_BYTE_BUDGET,
                diagnostic = bounded(budget.message ?: "bitmap byte budget refused"),
            )
        }

        // 6) 分配 + 绘制 + 压缩。painter 是唯一绘制入口（与屏/PDF 回放同一状态机）；
        //    实例每渲染新建（线程独占纪律最简守形）。
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val pngBytes = try {
            VisualDrawOpsPainter().draw(
                document,
                Canvas(bitmap),
                pxPerDu,
                labelContents::get,
            )
            val sink = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, sink)
            if (!compressed) {
                return TutorVisualPreviewOutcome.Refused(
                    code = PREVIEW_RENDER_FAILED,
                    diagnostic = bounded(
                        "$PREVIEW_RENDER_FAILED: bitmap.compress reported failure for a " +
                            "${widthPx}x$heightPx preview",
                    ),
                )
            }
            sink.toByteArray()
        } finally {
            bitmap.recycle()
        }

        // 7) persist（字节→行原子两步，KDoc 钉的顺序即语义）；失败原样折降级——半截状态
        //    （字节在行不在）不会传给快照，未引用行由六站点谓词 GC 自愈。
        val record = persister.persist(pngBytes, request.createdAtEpochMillis)
        return TutorVisualPreviewOutcome.Accepted(previewAssetId = record.sourceAssetId)
    }

    private fun bounded(message: String): String =
        message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS)

    private companion object {
        /** PNG 是无损格式，quality 参数无效果；传 100 表达"不降质"的意图。 */
        const val PNG_QUALITY = 100
    }
}
