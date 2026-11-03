package com.tingyun.smartmistakebook.core.data.telemetry

/**
 * 遥测记录入口（R5 §3.2/§3.8）：
 *
 * 1. **先过门，再构造事件**：`record { ... }` 在门失败时不会调用 lambda，因此不会分配
 *    事件对象、不会拼字符串、不会写文件；
 * 2. **只进内存聚合**：通过门后样本进入 [TelemetryAggregator]，不每条落盘；
 * 3. **批量落盘**：调用方按批调用 [flush]，快照写入 [TelemetryBuffer]（有界环形缓冲）；
 * 4. **撤回即清**：[onConsentChanged] 收到非 GRANTED 状态时清空聚合器并删除缓冲。
 *
 * 这个类**不发起网络请求**，也没有 uploader/Worker；v1 只做本机指标面。
 */
class TelemetryRecorder(
    private val buffer: TelemetryBuffer,
    private val currentPolicy: TelemetryPolicy,
    private val buildConfig: TelemetryBuildConfig = TelemetryBuildConfig(),
    private val aggregator: TelemetryAggregator = TelemetryAggregator(),
) {
    @Volatile
    private var consent: TelemetryConsentSnapshot = TelemetryConsentSnapshot()

    /** 已接受进聚合器的样本数（门关闭时为 0）。 */
    val recordedSamples: Long get() = aggregator.totalRecords

    @Synchronized
    fun onConsentChanged(consent: TelemetryConsentSnapshot) {
        this.consent = consent
        // 只有“明确撤回”或“曾同意但当前门已失效（政策/构建变更）”才清理本地数据。
        // 进程刚启动时缓存还是 UNKNOWN，不能把上一份已同意用户的缓冲误删。
        val shouldPurge = consent.state == TelemetryConsentState.WITHDRAWN ||
            (consent.state == TelemetryConsentState.GRANTED && !isCollectionAllowed())
        if (shouldPurge) {
            purgeLocalData()
        }
    }

    @Synchronized
    fun isCollectionAllowed(): Boolean =
        TelemetryGate.isCollectionAllowed(consent, buildConfig, currentPolicy)

    /** 门在事件构造之前：OFF 时 lambda 不会执行。 */
    @Synchronized
    fun record(eventFactory: () -> TelemetryEvent): Boolean {
        if (!isCollectionAllowed()) return false
        return aggregator.record(eventFactory())
    }

    /** 便捷重载；仍然先过门再接收事件。 */
    @Synchronized
    fun record(event: TelemetryEvent): Boolean {
        if (!isCollectionAllowed()) return false
        return aggregator.record(event)
    }

    /**
     * 把当前聚合结果写成一个有界快照。门关闭、没有样本、或落盘失败时返回 false；
     * 只有成功落盘后才清空聚合器（失败时保留样本，等待下一次批次）。
     */
    @Synchronized
    fun flush(metadata: TelemetryBatchMetadata): Boolean {
        if (!isCollectionAllowed()) return false
        val metrics = aggregator.snapshot()
        if (metrics.isEmpty()) return false
        val policyVersion = consent.policyVersion ?: return false
        val policyHash = consent.policyHash ?: return false
        val batch = TelemetryBatch(
            batchId = metadata.batchId,
            window = TelemetryWindow(metadata.windowStartUtc, metadata.windowEndUtc),
            app = TelemetryApp(
                versionName = metadata.appVersionName,
                versionCode = metadata.appVersionCode,
                flavor = metadata.flavor.wireName,
            ),
            env = TelemetryEnvironment(
                api = metadata.apiLevel,
                ramClass = metadata.ramClass.wireName,
            ),
            consent = TelemetryConsentDescriptor(
                policyVersion = policyVersion,
                policyHash = policyHash,
            ),
            sampling = TelemetrySampling(metadata.samplingRate),
            metrics = metrics,
        )
        if (!buffer.append(TelemetryJson.encode(batch))) return false
        aggregator.clear()
        return true
    }

    @Synchronized
    fun purgeLocalData() {
        aggregator.clear()
        buffer.purge()
    }

    @Synchronized
    fun snapshot(): List<TelemetryMetricSnapshot> = aggregator.snapshot()

    @Synchronized
    fun queryRates(): TelemetryQueryRates = aggregator.queryRates()

    @Synchronized
    fun queryPercentiles(outcome: TelemetryOutcome? = null): TelemetryPercentiles? =
        aggregator.percentiles(TelemetryMetric.QUERY_DURATION_MS, outcome)
}
