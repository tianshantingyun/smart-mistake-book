package com.tingyun.smartmistakebook.core.domain

/**
 * Data-driven calibration channel for the mastery-evidence write gate
 * (research tutor-evidence-gate §4): the gate's constants (confidence
 * threshold, cooldown, quotas, attention floor) are engineering priors until
 * real usage data says otherwise. This reads the collected gate outcomes —
 * rejections by reason, accepted-write pressure per hour — and produces
 * *suggestions only*, mirroring [SourceCalibration]: constants change by
 * human decision after reviewing the report, never automatically.
 *
 * Pure function over the raw statistics; no database access, fully testable.
 */
object ChatEvidenceGateCalibration {

    /**
     * Minimum observations before any suggestion is valid — mirrors
     * [SourceCalibration.MIN_PAIRED_OUTCOMES]: below this the statistics are
     * noise, and small-sample tweaks are exactly the overfitting the gate
     * exists to prevent.
     */
    const val MIN_OBSERVATIONS = 30

    /** Window-pressure share above which the learner-window quota is judged too tight. */
    const val WINDOW_PRESSURE_SUGGEST_THRESHOLD = 0.8

    /** Rejection share above which a specific gate's parameters deserve review. */
    const val REJECTION_SHARE_SUGGEST_THRESHOLD = 0.2

    /** One raw statistic bucket handed in by the persistence layer. */
    data class GateObservation(
        /** Accepted MASTERY_UPDATE writes (gate passed). */
        val acceptedCount: Int,
        /** Rejected writes keyed by [MasteryWriteGate.RejectReason] name. */
        val rejectedByReason: Map<String, Int>,
        /** Accepted writes per epoch-hour bucket (hour = millis / 3_600_000). */
        val acceptedPerHour: Map<Long, Int>,
        /**
         * AUD-11 ①：同一批拒写的**通道维**细分（`source_kind` → 拒因 → 计数）。
         *
         * 消灭的失败：校准面此前只按拒因分桶，读不出"被挡掉的是哪一路"——报告 §2.3.3 的
         * 复核要求正是收窄冷却前后都要能分辨模型写与小测写，否则"该不该再放宽"没有数据。
         * 与 [rejectedByReason] 是**同一批事实**的两种切法：各分桶之和相等，总数口径不变。
         * 空 = 上游没有给通道维（legacy/纯函数调用者），此时报告退回原口径。
         */
        val rejectedBySourceKindAndReason: Map<String, Map<String, Int>> = emptyMap(),
        /**
         * AUD-11 ①：accepted 小时桶的通道维细分（`source_kind` → 小时桶 → 计数）。
         * 扇形读数 = "哪条通道在灌量"（冷却收窄后要看的就是这个洪泛面）；
         * 总压力仍由 [acceptedPerHour] 承担（滚动窗配额本来就是 learner 级、通道无关）。
         */
        val acceptedPerSourceKindPerHour: Map<String, Map<Long, Int>> = emptyMap(),
    ) {
        init {
            require(acceptedCount >= 0) { "Accepted count must not be negative" }
            require(rejectedByReason.values.all { it >= 0 }) { "Rejection counts must not be negative" }
            require(acceptedPerHour.values.all { it >= 0 }) { "Hourly counts must not be negative" }
            require(rejectedBySourceKindAndReason.values.all { buckets -> buckets.values.all { it >= 0 } }) {
                "Rejection counts must not be negative"
            }
            require(acceptedPerSourceKindPerHour.values.all { buckets -> buckets.values.all { it >= 0 } }) {
                "Hourly counts must not be negative"
            }
        }

        val totalObservations: Int
            get() = acceptedCount + rejectedByReason.values.sum()
    }

    data class GateCalibrationReport(
        val totalObservations: Int,
        /** Rejection share per gate reason (0 when nothing was rejected). */
        val rejectionShares: Map<String, Double>,
        /**
         * Peak accepted writes in any single hour divided by the
         * learner-window cap — the direct "is the quota too tight" signal.
         */
        val windowPeakPressure: Double,
        val hasSufficientObservations: Boolean,
        /** Human-review suggestions; empty until [MIN_OBSERVATIONS] is met. */
        val suggestions: List<String>,
        /**
         * AUD-11 ①：拒写占比的**通道维**切法（`source_kind` → 拒因 → 占比）。
         * 空 = 观测没有给通道维。占比的都是同一分母 [totalObservations]——两路并列可比。
         */
        val rejectionSharesBySourceKind: Map<String, Map<String, Double>> = emptyMap(),
        /**
         * AUD-11 ①：各通道的 accepted 单小时峰值（通道 → 峰值笔数）。滚动窗配额是
         * learner 级、通道无关，所以这里给的是"哪条通道在灌量"的读数，不是配额压力。
         */
        val peakAcceptedBySourceKind: Map<String, Int> = emptyMap(),
    )

    fun calibrate(observation: GateObservation): GateCalibrationReport {
        val total = observation.totalObservations
        val rejectionShares = observation.rejectedByReason.mapValues { (_, count) ->
            if (total == 0) 0.0 else count.toDouble() / total
        }
        val rejectionSharesBySourceKind = observation.rejectedBySourceKindAndReason
            .mapValues { (_, byReason) ->
                byReason.mapValues { (_, count) -> if (total == 0) 0.0 else count.toDouble() / total }
            }
        val peakAcceptedBySourceKind = observation.acceptedPerSourceKindPerHour
            .mapValues { (_, byHour) -> byHour.values.maxOrNull() ?: 0 }
        val peakHour = observation.acceptedPerHour.values.maxOrNull() ?: 0
        val windowPeakPressure = if (MasteryWriteGate.MAX_WRITES_PER_LEARNER_WINDOW == 0) {
            0.0
        } else {
            peakHour.toDouble() / MasteryWriteGate.MAX_WRITES_PER_LEARNER_WINDOW
        }
        val suggestions = buildList {
            if (total < MIN_OBSERVATIONS) return@buildList
            // 1. Window-quota pressure: a legitimate batch burst repeatedly
            // touching the cap means the constant is too tight for the real
            // intake shape.
            if (windowPeakPressure >= WINDOW_PRESSURE_SUGGEST_THRESHOLD) {
                add(
                    "窗口配额压力 ${"%.0f".format(windowPeakPressure * 100)}%（单小时峰值 $peakHour / " +
                        "上限 ${MasteryWriteGate.MAX_WRITES_PER_LEARNER_WINDOW}）：批量录入场景可能被误拦，" +
                        "建议人工评估放宽 MAX_WRITES_PER_LEARNER_WINDOW。",
                )
            }
            // 2. Per-gate rejection composition: any single gate rejecting
            // more than its share of ALL writes deserves a look — either the
            // model is misbehaving (anchoring/semantics gates) or the
            // parameter is mis-tuned (cooldown/quota gates).
            rejectionShares.forEach { (reason, share) ->
                if (share >= REJECTION_SHARE_SUGGEST_THRESHOLD) {
                    val advice = if (reason == "SAME_KC_IN_COOLDOWN" || reason.contains("QUOTA")) {
                        "节流类拒写占比过高，检查冷却/配额是否与真实学习节奏冲突。"
                    } else {
                        "质量类拒写占比过高，检查模型是否在批量臆测知识点或空判方向。"
                    }
                    add(
                        "拒写「$reason」占比 ${"%.0f".format(share * 100)}%：" +
                            advice +
                            sourceKindSplit(observation, reason),
                    )
                }
            }
        }
        return GateCalibrationReport(
            totalObservations = total,
            rejectionShares = rejectionShares,
            windowPeakPressure = windowPeakPressure,
            hasSufficientObservations = total >= MIN_OBSERVATIONS,
            suggestions = suggestions,
            rejectionSharesBySourceKind = rejectionSharesBySourceKind,
            peakAcceptedBySourceKind = peakAcceptedBySourceKind,
        )
    }

    /**
     * AUD-11 ①：把一条被点名的拒因按通道摊开（"被挡掉的是哪一路"），没有通道维时返回空串
     * ——建议文案与旧口径逐字相同。
     */
    private fun sourceKindSplit(observation: GateObservation, reason: String): String {
        val split = observation.rejectedBySourceKindAndReason
            .mapNotNull { (sourceKind, byReason) ->
                byReason[reason]?.takeIf { it > 0 }?.let { "$sourceKind $it" }
            }
            .sorted()
        return if (split.isEmpty()) "" else "（来路：${split.joinToString(" / ")}）"
    }
}
