package com.tingyun.smartmistakebook.core.data.telemetry

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 知识库安装/首查/查询路径 → R5 指标面的唯一接线点。
 *
 * **默认关闭是代码级事实，不是配置约定**：
 * 1. [Disabled] 不带 recorder，安装与检索照常运行，但不会构造事件、不会碰缓冲目录；
 * 2. 即使调用方显式传入真实 [TelemetryRecorder]，每个 `record` 仍先过
 *    [TelemetryRecorder.isCollectionAllowed]（GRANTED + 构建开关 + 政策哈希 + 年龄门）。
 *    未同意时 lambda 根本不执行（见 `TelemetryRecorder.record`）。
 *
 * 本类只做事件拼装，不做 IO/网络；落盘仍由调用方决定何时 `flush`。
 */
class KnowledgeTelemetry(
    private val recorder: TelemetryRecorder?,
) {
    private val firstRealQueryTaken = AtomicBoolean(false)

    /** 知识库安装完成（快路径或全量路径）。`coldStart` = 本次是否走了全量解析。 */
    fun installFinished(durationMs: Long, coldStart: Boolean) {
        record {
            StageDuration(
                stage = TelemetryStage.INSTALL,
                outcome = TelemetryOutcome.OK,
                coldStart = coldStart,
                subject = null,
                trigger = TelemetryTrigger.INSTALL,
                durationMs = durationMs,
            )
        }
    }

    /** 安装抛错。记录后由调用方原样重抛，行为不变。 */
    fun installFailed(durationMs: Long) {
        record {
            StageDuration(
                stage = TelemetryStage.INSTALL,
                outcome = TelemetryOutcome.ERROR,
                coldStart = true,
                subject = null,
                trigger = TelemetryTrigger.INSTALL,
                durationMs = durationMs,
            )
        }
    }

    /** 单个检索阶段耗时（特征提取 / 召回 / 精排 / 前置跳）。 */
    fun stageFinished(
        stage: TelemetryStage,
        outcome: TelemetryOutcome,
        coldStart: Boolean,
        subject: TelemetrySubject?,
        trigger: TelemetryTrigger,
        durationMs: Long,
    ) {
        record {
            StageDuration(
                stage = stage,
                outcome = outcome,
                coldStart = coldStart,
                subject = subject,
                trigger = trigger,
                durationMs = durationMs,
            )
        }
    }

    /** 进入真实检索（空题面/未就绪不记，避免稀释空结果率分母）。 */
    fun queryStarted(
        subject: TelemetrySubject?,
        trigger: TelemetryTrigger,
        kbReady: Boolean,
    ) {
        if (subject == null) return
        record { QueryStarted(trigger = trigger, subject = subject, kbReady = kbReady) }
    }

    /** 召回非空、精排零命中（回退主题之前）。空结果率的分子。 */
    fun noRelevantHit(subject: TelemetrySubject?, trigger: TelemetryTrigger) {
        if (subject == null) return
        record { NoRelevantHit(subject = subject, trigger = trigger) }
    }

    fun fallbackToTopic(trigger: TelemetryTrigger) {
        record { FallbackToTopic(trigger = trigger) }
    }

    fun emptyContext(reason: TelemetryReason, trigger: TelemetryTrigger) {
        record { EmptyContext(reason = reason, trigger = trigger) }
    }

    fun querySkipped(reason: TelemetryReason, trigger: TelemetryTrigger) {
        record { QuerySkipped(reason = reason, trigger = trigger) }
    }

    fun queryError(stage: TelemetryStage, errorClass: TelemetryErrorClass, retryable: Boolean) {
        record { QueryError(stage = stage, errorClass = errorClass, retryable = retryable) }
    }

    /** 查询级端到端耗时；p50/p95 由 [TelemetryAggregator.percentiles] 从桶分布估计。 */
    fun queryFinished(
        trigger: TelemetryTrigger,
        outcome: TelemetryOutcome,
        durationMs: Long,
    ) {
        record { QueryDuration(trigger = trigger, outcome = outcome, durationMs = durationMs) }
    }

    /** 本进程首次真实检索（每个 recorder 实例只记一次）。 */
    fun firstQueryFinished(
        coldStart: Boolean,
        kbReady: Boolean,
        trigger: TelemetryTrigger,
        durationMs: Long,
    ) {
        record {
            FirstQueryDuration(
                coldStart = coldStart,
                kbReady = kbReady,
                trigger = trigger,
                durationMs = durationMs,
            )
        }
    }

    /**
     * 标记一次“真实检索”开始。返回 true 表示这是本进程第一次；调用方应把该值同时用作
     * `cold_start` 维度，并在结束时写 [firstQueryFinished]。跳过（未就绪/空题面）不调用。
     */
    fun takeFirstRealQuery(): Boolean = firstRealQueryTaken.compareAndSet(false, true)

    private inline fun record(crossinline eventFactory: () -> TelemetryEvent) {
        recorder?.record { eventFactory() }
    }

    companion object {
        /** 生产默认值：不带 recorder，路径正常跑、零事件、零文件。 */
        val Disabled: KnowledgeTelemetry = KnowledgeTelemetry(recorder = null)

        /**
         * 学科代码（`MATH`/`PHYSICS`…）→ 遥测枚举。大小写不敏感；未知学科返回 null
         * （调用方据此跳过需要 subject 维度的指标，而不是编一个错误学科）。
         */
        fun subjectOf(code: String): TelemetrySubject? = when (code.trim().uppercase(Locale.ROOT)) {
            "CHINESE" -> TelemetrySubject.CHINESE
            "MATH" -> TelemetrySubject.MATH
            "ENGLISH" -> TelemetrySubject.ENGLISH
            "PHYSICS" -> TelemetrySubject.PHYSICS
            "CHEMISTRY" -> TelemetrySubject.CHEMISTRY
            "BIOLOGY" -> TelemetrySubject.BIOLOGY
            "HISTORY" -> TelemetrySubject.HISTORY
            "GEOGRAPHY" -> TelemetrySubject.GEOGRAPHY
            "POLITICS" -> TelemetrySubject.POLITICS
            else -> null
        }
    }
}
