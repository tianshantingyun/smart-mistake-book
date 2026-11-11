package com.tingyun.smartmistakebook.core.ui

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Qualitative mastery/retention bands shared by every screen that presents
 * learning state (audit section 6.4): precise probabilities require a
 * calibrated model, so student-facing copy uses bands only.
 *
 * P8（批次 2 规格 §1）：切点是**单源常量**——此前本文件与掌握库页面各写一份 `0.7/0.4`，
 * 改一处不会让另一处变红（审计 R-03：把一档调到 0.75，同一张卡会同时显示「较稳」与
 * 「遗忘风险 高」）。[MASTERY_STRONG_THRESHOLD] = 0.85 是台账「裁决 13 · 修订」保留的
 * **展示带边界**（θ=0.85 不再是判据，「掌握/没掌握」只由状态列（E 判据）表达）；
 * [MASTERY_FAIR_THRESHOLD] = 0.4 为现状值。
 */
const val MASTERY_STRONG_THRESHOLD = 0.85
const val MASTERY_FAIR_THRESHOLD = 0.4

fun masteryBandLabel(conservativeMasteryScore: Double): String = when {
    conservativeMasteryScore >= MASTERY_STRONG_THRESHOLD -> "较稳"
    conservativeMasteryScore >= MASTERY_FAIR_THRESHOLD -> "一般"
    else -> "薄弱"
}

fun retentionBandLabel(retrievabilityAtSnapshot: Double): String = when {
    retrievabilityAtSnapshot >= MASTERY_STRONG_THRESHOLD -> "记忆较稳"
    retrievabilityAtSnapshot >= MASTERY_FAIR_THRESHOLD -> "记忆减弱"
    else -> "记忆模糊"
}

/**
 * 粗粒度区间文案（学生语言；无小数、无内部术语）——批次 2 规格 §1.3 的**唯一**区间文案
 * 函数，三个掌握展示面只准用它（不许各自拼字）。
 *
 * 规则（规格 §1.3）：无上界或区间宽 ≥ [INTERVAL_WIDE_SPAN] → 「证据还少」（宽区间 = 证据少，
 * 报数会被读成确信）；否则取整到 10 个百分点 →「约 X 成」/「约 X–Y 成」。
 *
 * @param lower Wilson 下界（= `conservativeMasteryScore`）
 * @param upper Wilson 上界；null = 无证据（映射侧对 s+f<=0 传 null）
 */
fun masteryIntervalLabel(lower: Double, upper: Double?): String {
    if (upper == null) return "证据还少"
    val lo = lower.coerceIn(0.0, 1.0)
    val hi = upper.coerceIn(0.0, 1.0)
    if (hi - lo >= INTERVAL_WIDE_SPAN) return "证据还少"
    // 1e-9 容差抵消浮点表示误差（0.7*10 = 6.999…），否则恰好落在十分位上的值会被
    // floor/ceil 拆成两个邻近档（0.7/0.7 → 「6–7 成」）。
    val loTenths = floor(lo * 10.0 + TENTH_EPSILON).toInt().coerceIn(0, 10)
    val hiTenths = ceil(hi * 10.0 - TENTH_EPSILON).toInt().coerceIn(0, 10)
    return if (loTenths == hiTenths) "约 $loTenths 成" else "约 $loTenths–$hiTenths 成"
}

/** 区间宽到该幅度即「证据还少」（规格 §1.3 规则 2）。 */
private const val INTERVAL_WIDE_SPAN = 0.5

/** 十分位取整的浮点容差。 */
private const val TENTH_EPSILON = 1e-9
