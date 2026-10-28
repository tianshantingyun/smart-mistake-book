package com.tingyun.smartmistakebook.visualjs

import android.content.res.Resources
import android.graphics.Paint
import android.os.Build
import androidx.core.os.ConfigurationCompat
import com.tingyun.smartmistakebook.core.domain.VisualContentKind
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureEntry
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.model.MathBoxBuilder
import com.tingyun.smartmistakebook.core.model.MathMetrics
import com.tingyun.smartmistakebook.core.model.figureSha256Hex
import com.tingyun.smartmistakebook.core.model.parseAndBuildBox
import java.nio.charset.StandardCharsets

/**
 * `:visual_js` 服务进程内的真实内容度量（批次 27 T2；设计依据 R2
 * `docs/research/2026-10-27-content-measurement.md`，契约 §3.2 事务第 2 步"原生验证并度量
 * text/math，保留不可变布局 artifact"）。
 *
 * **它消灭的具体失败**（对照批次 22 的 `UnwiredVisualContentMeasurer` fail-closed 桩）：
 * 1. "任何含文本/公式的图都不可用"——桩对非空批次一律拒绝；本实现给出真实宽/高/baseline；
 * 2. "服务侧另起一套公式解析与 UI 渲染不一致"——math 复用 core:model 的
 *    `parseAndBuildBox`/`MathBoxBuilder` 同一条共享 lowering（契约 §5："不得继续增加第三套
 *    公式/坐标绘制逻辑"），解析/预算失败按契约降级为可读文本（度量为原文 payload），
 *    不做占位框；
 * 3. "度量环境漂移无法归因"——环境快照（density/fontScale/字体/locale/sizeSp）参与内容
 *    摘要（契约 §3.2："缓存只在内容摘要和布局环境完全一致时命中"）。
 *
 * 端口形状（`VisualContentMeasurer.measure(batch): VisualContentMeasureResult`）与批次 22
 * 完全兼容、零改动；metrics 进 render 的五字段 JSON 也不变（引擎 `metricsJson`）。断行输出
 * 只落内部 seam（[VisualMeasuredLayout.lines]），不进端口（R2 §4："断行留在内部 artifact
 * seam，未来 painter 复用同一 seam"）。
 *
 * 线程纪律（R2 §1.4/D5）：度量器实例与其 [Paint] 后端**线程独占**——只在 `visual-js-executor`
 * 单线程上构造与使用（warm-up 探针与 `runRequest` 由同一单线程执行器串行化）。官方文档对
 * `Paint` 无线程安全声明，按非线程安全对待；服务单线程使并发共享不成立，不引入 Paint 池。
 *
 * 精度边界（如实登记，不宣称字体级精确）：
 * - API < 33 无文本感知 font metrics，混排（CJK 回退）行高可能低估（R2 §1.5-1 边界 P1；
 *   API 33+ 走 `Paint.getFontMetricsInt(CharSequence, …)`，官方口径"返回足以绘制所有字体的
 *   ascent/descent"）；
 * - math 盒宽是布局盒估算（0.5 em/字符，CJK 低估约一半）；"补为真实字形度量"是共享
 *   lowering 的既定后续工作，**不能只在度量侧做**（否则与绘制侧立刻不一致），本批不做
 *   （R2 §1.5-6 边界 P2、契约 §5 末段）；
 * - 隔离进程内 `resources`/系统字体可用性是 R2 假设 A1 [待验证]：环境构造失败与度量输出
 *   异常一律 fail-closed（[VisualMeasurementEnvironmentException] → 引擎有界拒绝 →
 *   warm-up 阶段先探针暴露，绝不伪造度量）。
 */
internal class AndroidVisualContentMeasurer(
    private val environment: VisualMeasureEnvironment,
    private val backend: VisualTextMeasureBackend,
) : VisualContentMeasurer {

    override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult {
        // 纯几何场景（prepare 返回空批次）保持批次 22 的合法事务行为：空结果不构成失败，
        // 且不触达任何度量后端（契约 §3.2"每事务最多一次 measure batch"由引擎保证）。
        if (batch.entries.isEmpty()) {
            return VisualContentMeasureResult(emptyList())
        }
        return VisualContentMeasureResult(
            batch.entries.mapIndexed { index, entry ->
                val layout = measureContentEntry(entry, environment, backend)
                val digest = contentDigest(layout.kind, entry.payload, environment, layout.degraded)
                VisualMeasuredContent(
                    contentId = CONTENT_ID_PREFIX + index + "-" + digest.take(CONTENT_ID_DIGEST_CHARS),
                    widthDu = layout.widthDu,
                    heightDu = layout.heightDu,
                    baselineDu = layout.baselineDu,
                    // "实际字号" = 实际使用的规范字号（R2 §4.3-5）；MVA 条目协议不携带 per-entry
                    // size（D3），度量为环境单一规范字号。
                    fontSizeSp = environment.sizeSp,
                    digest = digest,
                )
            },
        )
    }

    /**
     * warm-up 探针（R2 D6）：用固定 ASCII 载荷真实走一遍 text 度量路径。
     *
     * **它消灭的失败**：字体子系统**首次初始化**成本原本落在首个含 text 请求的 1500 ms
     * 硬期限窗口内（KD-76 同族"装载成本落预算内被误杀"形状）；探针把该成本移到 ready 之前，
     * 环境性失败（字体/资源不可用，假设 A1）在 warm-up 阶段暴露（服务 killSelf、宿主走
     * 既有死亡→重建 fail-closed 路径），不留到请求期被误归"程序错"。
     */
    fun warmUpProbe() {
        val layout = measureTextIntrinsic(WARMUP_PROBE_PAYLOAD, environment, backend)
        check(
            layout.widthDu.isFinite() && layout.widthDu >= 0f &&
                layout.heightDu.isFinite() && layout.heightDu > 0f &&
                layout.baselineDu.isFinite() && layout.baselineDu >= 0f,
        ) {
            "visual_js measurement probe produced broken metrics: " +
                "width=${layout.widthDu} height=${layout.heightDu} baseline=${layout.baselineDu}"
        }
    }

    private companion object {
        const val CONTENT_ID_PREFIX = "c"
        const val CONTENT_ID_DIGEST_CHARS = 8
        const val WARMUP_PROBE_PAYLOAD = "visual-js-warmup-probe"
    }
}

/** 环境构造或度量后端输出异常（fail-closed）；引擎把它折成有界拒绝，warm-up 探针提前暴露。 */
internal class VisualMeasurementEnvironmentException(message: String) : Exception(message)

/**
 * 度量环境快照（R2 §4.1）：参与内容摘要与"布局环境完全一致"缓存判定（契约 §3.2）。
 *
 * 单位换算单一规则（度量与未来重放共用，契约 §5："1 du=1 dp……字号使用允许字体的 sizeSp，
 * 原生解析成 du；系统 fontScale 另计"）：`px = sp × density × fontScale`；`du = px / density`；
 * 故 `du per sp = fontScale`。
 */
internal data class VisualMeasureEnvironment(
    /** `resources.displayMetrics.density`。 */
    val density: Float,
    /** `resources.configuration.fontScale`。 */
    val fontScale: Float,
    /** 度量字体的稳定标识（本批仅 [SYSTEM_DEFAULT_TYPEFACE_ID]；字体白名单属后续批次）。 */
    val typefaceId: String,
    /** 环境 locale 标签（CJK 变体选字参与摘要；R2 §1.5-2）。 */
    val localeTag: String,
    /** 规范字号（契约 §4：sizeSp 限 12..48；MVA 用单一规范字号，见 [CANONICAL_SIZE_SP]）。 */
    val sizeSp: Float,
    /**
     * 度量 Paint 的 `flags`（批次 29 T5；KD-78 ④ 收口）。
     *
     * **它消灭的具体失败**：度量 Paint 的 flags 与回放 textPaint 的 flags 各自演化时，
     * 内容摘要不变 → 度量与回放静默失配（advance/字形与冻结布局不同，跨环境恢复尤其
     * 危险）。flags 进环境指纹后，flag 一变 digest 就变、旧快照不再被当命中。
     *
     * 值 0 是**显式钉死的度量/回放共享配置**（AA/locale/subpixel 全关的 MVA 取舍），
     * **不是**平台 `Paint()` 的构造默认——API 34 实测构造默认为 1283
     * （`ANTI_ALIAS|FILTER_BITMAP|DEV_KERN|EMBEDDED_BITMAP`，批次 29 设备证据 R1）。
     * 两侧构造后都必须显式置 0（本侧 [PaintVisualTextMeasureBackend.forEnvironment]、
     * 回放侧 `VisualDrawOpsPainter` 的 `textPaint`）；[forEnvironment] 再校验实际度量
     * Paint 的 flags 与该环境值一致（不一致即 fail-closed，不写"声称"值）。
     */
    val paintFlags: Int = DEFAULT_PAINT_FLAGS,
) {
    init {
        // fail-closed（R2 假设 A1 处置）：隔离进程内环境读数不可信时在此显式拒绝，
        // 绝不带病度量。异常 → warm-up killSelf 或引擎有界拒绝。
        require(density.isFinite() && density > 0f) {
            "A visual measure environment needs a positive density, got $density"
        }
        require(fontScale.isFinite() && fontScale > 0f) {
            "A visual measure environment needs a positive fontScale, got $fontScale"
        }
        require(typefaceId.isNotBlank()) { "A visual measure environment needs a typeface id" }
        require(localeTag.isNotBlank()) { "A visual measure environment needs a locale tag" }
        require(sizeSp.isFinite() && sizeSp in MIN_SIZE_SP..MAX_SIZE_SP) {
            "A visual measure environment sizeSp must be within $MIN_SIZE_SP..$MAX_SIZE_SP, got $sizeSp"
        }
        require(paintFlags >= 0) { "A visual measure environment paintFlags must be non-negative, got $paintFlags" }
    }

    /** 规范字号的像素值：`sizeSp × density × fontScale`。 */
    fun fontSizePx(): Float = sizeSp * density * fontScale

    /**
     * 环境指纹：进入内容摘要（同环境逐字符一致，跨环境不同）。
     *
     * 批次 29 T5（KD-78 ④）起包含 `paintFlags`——度量/回放 Paint 的 flags 差异必须改变
     * digest（否则换 flag 后旧快照仍命中、回放与冻结布局静默失配）。
     */
    fun fingerprint(): String =
        "$typefaceId|sizeSp=$sizeSp|density=$density|fontScale=$fontScale|locale=$localeTag" +
            "|paintFlags=$paintFlags"

    companion object {
        /** 契约 §4：sizeSp 限 12..48。 */
        const val MIN_SIZE_SP: Float = 12f
        const val MAX_SIZE_SP: Float = 48f

        /**
         * 度量 Paint 的 flags 固定值 0（AA/locale/subpixel 全关的 MVA 取舍）。
         *
         * **不是** `Paint()` 的构造默认：平台 `Paint()` 默认 1283（批次 29 设备实测，
         * KD-78 ④），构造后必须**显式** `flags = DEFAULT_PAINT_FLAGS`。[forEnvironment]
         * 以本值装配并校验实际度量 Paint；回放侧 `REPLAY_TEXT_PAINT_FLAGS`
         * （`core/data/.../visual/VisualDrawOpsPainterPure.kt`，同为 0）由
         * `VisualDrawOpsPainter` 的 `textPaint` 显式装配——改一侧必须两侧同改，并由
         * 「环境指纹 `paintFlags=0`」JVM 测与设备侧「度量 Paint flags == 回放 textPaint
         * flags」测各自钉住。
         */
        const val DEFAULT_PAINT_FLAGS: Int = 0

        /** 本批度量字体标识：系统默认字体（字体白名单/字体身份随 painter 批次落地）。 */
        const val SYSTEM_DEFAULT_TYPEFACE_ID: String = "system-default"

        /**
         * MVA 规范字号：16sp（与 core:model `MathMetrics.DEFAULT` 的"16px 文本"金样基线同源，
         * 便于盒式金样与文本金样在同一字号口径下对拍）。per-label sizeSp 是 entry 协议的
         * 未来扩展（R2 D3 契约变更，本批不做）。
         */
        const val CANONICAL_SIZE_SP: Float = 16f
    }
}

/**
 * 文本度量后端 seam：把 `android.graphics.Paint` 隔离在唯一实现
 * （[PaintVisualTextMeasureBackend]）之后。
 *
 * **它消灭的具体失败**：app 模块 JVM 单测没有 Robolectric（`testImplementation` 仅 JUnit），
 * `Paint` 构造即抛 "not mocked"——没有这个 seam，度量核心（断行/降级/摘要/单位换算）只能
 * 留给设备轨验证，JVM 门覆盖不了 R2 §6-2/3/4 的红绿判据。
 *
 * 坐标口径：全部返回**像素**（px）；du 换算在度量核心统一做（`px / density`）。
 */
internal interface VisualTextMeasureBackend {
    /** [payload] 的 [start, endExclusive) 区段 advance 宽（`Paint.measureText` 口径）。 */
    fun advancePx(payload: String, start: Int, endExclusive: Int): Float

    /**
     * [payload] 的行 metrics：`ascentPx ≤ 0 ≤ descentPx`（字体坐标约定）。
     * 实现应传整段载荷（含 `\n`/`\r`）：API 33+ 文本感知口径对区间内所有用到的字体返回
     * "足以绘制所有字体"的 metrics；分隔符走默认字体，不改变结果集。
     */
    fun lineMetricsPx(payload: String): VisualTextLineMetricsPx
}

/** 行 metrics（px）：`ascentPx ≤ 0`，`descentPx ≥ 0`（字体坐标约定）。 */
internal data class VisualTextLineMetricsPx(
    val ascentPx: Float,
    val descentPx: Float,
)

/** 一行显式段（`\n` 切分并做 `\r` 归一后的段）：字符区间与该段 advance 宽（du）。 */
internal data class VisualMeasuredLine(
    val start: Int,
    val endExclusive: Int,
    val widthDu: Float,
)

/**
 * 内部完整布局 artifact（R2 §4.2 seam）：端口五字段之上的行结构，供未来 painter/lowering
 * 复用（同一 seam、同一数字）。本批不进端口、不序列化（契约 §5 持久化段："冻结的行范围与
 * run 位置"随 painter/PDF 回放批次经同一 seam 重导出）。
 *
 * 形状对 R2 §4.2 草图的一处收敛（登记）：草图为 sealed interface，本实现收敛为单一 data
 * class——草图两个子类型的成员集完全相同，唯一差异（text 有 lines、math 无）已被降级路径
 * 打破（降级 math 携带文本段列表，保证降级 artifact 可复现），sealed 划不出合法态边界。
 */
internal data class VisualMeasuredLayout(
    val kind: VisualContentKind,
    /** 内蕴宽（du）：各段 advance 最大值（text）或盒宽（math）。 */
    val widthDu: Float,
    /** 高（du）：段数 × 行高（text）或盒高（math）。 */
    val heightDu: Float,
    /** baseline 自盒顶（du）：与 `Layout.getLineBaseline`/MathBox 的"自顶偏移"约定一致。 */
    val baselineDu: Float,
    /** text = 显式段列表；math = null（盒树自带布局）；降级 math = 降级文本的段列表。 */
    val lines: List<VisualMeasuredLine>?,
    /** math 解析/预算失败按契约降级为可读文本时 true（参与摘要，R2 §6-2）。 */
    val degraded: Boolean,
)

/**
 * TEXT 内蕴度量（R2 §4.2）：MVA 只按显式 `\n` 分段、无自动换行（D3：自动换行需要 entry
 * 协议携带可用宽度，属契约变更；能力上下文不广告 wrap）。确定性归一：段尾单个 `\r` 去除。
 * 内蕴宽 = 各段 advance 最大值；高 = 段数 × 行高（行 metrics 对整段载荷取一次）；
 * baseline = 首行自顶偏移（`-ascent`）。
 */
internal fun measureTextIntrinsic(
    payload: String,
    environment: VisualMeasureEnvironment,
    backend: VisualTextMeasureBackend,
): VisualMeasuredLayout {
    val segments = explicitLineRanges(payload)
    val lineMetrics = backend.lineMetricsPx(payload)
    val lineHeightPx = lineMetrics.descentPx - lineMetrics.ascentPx
    if (!lineHeightPx.isFinite() || lineHeightPx < 0f) {
        throw VisualMeasurementEnvironmentException(
            "text line metrics are broken: ascent=${lineMetrics.ascentPx} descent=${lineMetrics.descentPx}",
        )
    }
    var maxWidthPx = 0f
    val lines = segments.map { range ->
        val widthPx = backend.advancePx(payload, range.start, range.endExclusive)
        if (!widthPx.isFinite() || widthPx < 0f) {
            throw VisualMeasurementEnvironmentException(
                "text advance is broken: [${range.start},${range.endExclusive}) -> $widthPx",
            )
        }
        if (widthPx > maxWidthPx) maxWidthPx = widthPx
        VisualMeasuredLine(range.start, range.endExclusive, widthPx / environment.density)
    }
    return VisualMeasuredLayout(
        kind = VisualContentKind.TEXT,
        widthDu = maxWidthPx / environment.density,
        heightDu = segments.size * lineHeightPx / environment.density,
        baselineDu = -lineMetrics.ascentPx / environment.density,
        lines = lines,
        degraded = false,
    )
}

/**
 * MATH 度量（R2 案A）：复用 core:model 纯 Kotlin 盒布局链——`parseAndBuildBox`（预算内建）
 * + `MathBoxBuilder.buildBox(node, MathMetrics.of(fontSizePx))`；返回 null = 解析/预算失败
 * （调用方按契约降级为可读文本，见 [measureContentEntry]）。纯 Kotlin，JVM 可测
 * （R2 §6-1 公式金样）。
 *
 * 预算行为如实登记：`parseAndBuildBox` 的解析器对未知 TeX 极度宽容（未知命令降级为字面
 * atom），真实失败面是**预算越界**（输入 >2000 字符、token/节点/深度/盒尺寸上限）——降级
 * 路径因此主要覆盖超预算与超长公式（契约"第一版支持范围须由公式金样限定"）。
 */
internal fun measureMathBox(
    payload: String,
    environment: VisualMeasureEnvironment,
): VisualMeasuredLayout? {
    val parsed = parseAndBuildBox(payload)
    if (!parsed.isSuccess) return null
    val node = parsed.node ?: return null
    val box = MathBoxBuilder.buildBox(node, MathMetrics.of(environment.fontSizePx()))
    if (
        !box.width.isFinite() || box.width < 0f ||
        !box.height.isFinite() || box.height < 0f ||
        !box.baseline.isFinite() || box.baseline < 0f
    ) {
        throw VisualMeasurementEnvironmentException(
            "math box metrics are broken: ${box.width}x${box.height} baseline=${box.baseline}",
        )
    }
    return VisualMeasuredLayout(
        kind = VisualContentKind.MATH,
        widthDu = box.width / environment.density,
        heightDu = box.height / environment.density,
        baselineDu = box.baseline / environment.density,
        lines = null,
        degraded = false,
    )
}

/**
 * 单条内容度量（案A 组合，R2 §4.3 流程）：TEXT → 内蕴度量；MATH → 盒度量，失败 →
 * **契约降级**：用 text 路径度量**原文 payload**，`degraded=true`（与 UI 侧
 * `fallbackText ?: formula` 同语义，即契约 §5"未知 TeX 回可读文本"，不做占位框）。
 * 降级 math 保留 MATH kind（摘要按 kind 区分）与降级文本段列表（artifact 可复现）。
 */
internal fun measureContentEntry(
    entry: VisualContentMeasureEntry,
    environment: VisualMeasureEnvironment,
    backend: VisualTextMeasureBackend,
): VisualMeasuredLayout = when (entry.kind) {
    VisualContentKind.TEXT -> measureTextIntrinsic(entry.payload, environment, backend)
    VisualContentKind.MATH -> {
        val box = measureMathBox(entry.payload, environment)
        if (box != null) {
            box
        } else {
            val fallback = measureTextIntrinsic(entry.payload, environment, backend)
            fallback.copy(kind = VisualContentKind.MATH, degraded = true)
        }
    }
}

/**
 * 内容摘要（R2 §4.3-4）：`sha256(kind + payload + 环境指纹 + 降级态)`，带版本前缀。
 * 环境/降级参与摘要 = 契约"缓存只在内容摘要和布局环境完全一致时命中" +
 * R2 §6-2"同 payload 不同降级态摘要不同"。payload 放末段（前段字段数与词表固定，
 * 不回解析，拼接歧义不影响摘要唯一性）。
 */
internal fun contentDigest(
    kind: VisualContentKind,
    payload: String,
    environment: VisualMeasureEnvironment,
    degraded: Boolean,
): String = figureSha256Hex(
    buildString {
        append(DIGEST_VERSION)
        append("|kind=").append(kind.name)
        append("|degraded=").append(if (degraded) "1" else "0")
        append('|').append(environment.fingerprint())
        append("|payload=").append(payload)
    }.toByteArray(StandardCharsets.UTF_8),
)

private const val DIGEST_VERSION = "visual-content-measure-v1"

/**
 * `\n` 切分的显式段字符区间（闭开区间 [start, endExclusive)）；段尾单个 `\r` 归一去除
 * （`"a\r\nb"` → `[0,1)`,`[3,4)`：a=0、\r=1、\n=2、b=3，区间指向原始载荷的实际段字符，
 * 不含分隔符）。段数 = `\n` 个数 + 1（尾 `\n` 产生末尾空段，advance 为 0、
 * 参与行数计数——确定性）。不变式：`0 ≤ start ≤ endExclusive ≤ payload.length`（空段
 * start == endExclusive， advance 调用仍成立——`measureText` 对 start==end 返回 0）。
 */
private class ExplicitLineRange(val start: Int, val endExclusive: Int)

private fun explicitLineRanges(payload: String): List<ExplicitLineRange> {
    val ranges = mutableListOf<ExplicitLineRange>()
    var start = 0
    for (index in payload.indices) {
        if (payload[index] == '\n') {
            var end = index
            if (end > start && payload[end - 1] == '\r') end--
            ranges.add(ExplicitLineRange(start, end))
            start = index + 1
        }
    }
    var end = payload.length
    if (end > start && payload[end - 1] == '\r') end--
    ranges.add(ExplicitLineRange(start, end))
    return ranges
}

// ---------------------------------------------------------------- Android 实现

/**
 * [VisualTextMeasureBackend] 的设备实现（`Paint` 线程独占，R2 §1.4/D5）。
 * 仅由 [visualContentMeasurerForService] 在 `visual-js-executor` 线程上构造；
 * JVM 单测注入 fake backend，不触达本类。
 */
internal class PaintVisualTextMeasureBackend(
    private val paint: Paint,
) : VisualTextMeasureBackend {

    override fun advancePx(payload: String, start: Int, endExclusive: Int): Float =
        paint.measureText(payload, start, endExclusive)

    /**
     * 实际度量 Paint 的 flags（批次 29 T5；KD-78 ④ 跨侧钉的观测面）。
     *
     * **它消灭的具体失败**：后端把 Paint 藏在私有字段里，跨侧测试只能验指纹字面——
     * 真实度量 Paint 被改成另一套 flags 时无人发现。本访问器让设备侧测试直接读实际
     * Paint 的 flags，与 `VisualMeasureEnvironment.paintFlags` 和回放侧显式装配的
     * `REPLAY_TEXT_PAINT_FLAGS`（0）比对。
     */
    internal fun paintFlagsForTest(): Int = paint.flags

    override fun lineMetricsPx(payload: String): VisualTextLineMetricsPx {
        val metrics = Paint.FontMetricsInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+：文本感知 metrics——多字体（CJK 回退等）渲染时返回"足以绘制所有字体"
            // 的 ascent/descent（官方口径，R2 §1.5-1）。区间即整段载荷；isRtl=false 与
            // measureText 的 LTR advance 口径一致。
            paint.getFontMetricsInt(payload, 0, payload.length, 0, payload.length, false, metrics)
        } else {
            // API < 33 精度边界 P1（R2 §1.5-1）：主字体 metrics，混排行高可能低估。
            paint.getFontMetricsInt(metrics)
        }
        return VisualTextLineMetricsPx(
            ascentPx = metrics.ascent.toFloat(),
            descentPx = metrics.descent.toFloat(),
        )
    }

    companion object {
        /**
         * 构造度量 Paint：`flags = DEFAULT_PAINT_FLAGS`（显式 0；平台 `Paint()` 默认
         * 1283 不是本仓语义），textSize = 规范字号的像素值。**不**显式设 locale/subpixel
         * 等可变 flag——度量与回放必须用同一常量配置（R2 §1.5-4）。批次 29 T5（KD-78 ④）
         * 起 flags 已进环境指纹（`VisualMeasureEnvironment.paintFlags`），本函数校验实际
         * Paint 的 flags 与指纹声明值一致；改 flags 必须同时改指纹与回放侧
         * `REPLAY_TEXT_PAINT_FLAGS`。
         */
        fun forEnvironment(environment: VisualMeasureEnvironment): PaintVisualTextMeasureBackend {
            val paint = Paint()
            // 平台 Paint() 默认 flags = 1283（AA|FILTER_BITMAP|DEV_KERN|EMBEDDED_BITMAP），
            // 不是 0（批次 29 设备证据 R1）。显式置为钉死值：这既是"AA 关"的 MVA 取舍，
            // 也是指纹 paintFlags=0 的真实来源。必须置**常量**而非 environment.paintFlags
            // ——否则下面的校验恒真，坏指纹再也拦不住。
            paint.flags = VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS
            paint.textSize = environment.fontSizePx()
            // KD-78 ④（批次 29 T5）：环境指纹声明了 paintFlags，实际度量 Paint 必须与之一致
            // ——否则 digest 记的是"声称值"、真实度量用的是另一套 flags，指纹形同虚设。
            // 不一致时 fail-closed（环境异常族），由 warm-up 探针/引擎有界拒绝暴露。
            if (paint.flags != environment.paintFlags) {
                throw VisualMeasurementEnvironmentException(
                    "measure paint flags ${paint.flags} do not match the environment fingerprint " +
                        "paintFlags=${environment.paintFlags}",
                )
            }
            return PaintVisualTextMeasureBackend(paint)
        }
    }
}

/**
 * 服务装配入口：从本进程 `resources` 构造环境快照并绑定 Paint 后端。
 * 在 warm-up（jsExecutor 单线程）调用一次；失败（含 A1 假设不成立：隔离进程资源/字体
 * 不可用）向上抛 → 服务 killSelf（fail-closed，不伪造度量）。
 */
internal fun visualContentMeasurerForService(
    resources: Resources,
): AndroidVisualContentMeasurer {
    val configuration = resources.configuration
    val environment = VisualMeasureEnvironment(
        density = resources.displayMetrics.density,
        fontScale = configuration.fontScale,
        // ConfigurationCompat：minSdk 23 上 configuration.getLocales() 需要 API 24，
        // 兼容路径由 androidx.core 承担；localeTag 记录的是配置派生的 locale（Paint
        // 未显式设置 locale，用进程默认——同一来源，见 PaintVisualTextMeasureBackend）。
        localeTag = ConfigurationCompat.getLocales(configuration).toLanguageTags(),
        typefaceId = VisualMeasureEnvironment.SYSTEM_DEFAULT_TYPEFACE_ID,
        sizeSp = VisualMeasureEnvironment.CANONICAL_SIZE_SP,
    )
    return AndroidVisualContentMeasurer(
        environment = environment,
        backend = PaintVisualTextMeasureBackend.forEnvironment(environment),
    )
}
