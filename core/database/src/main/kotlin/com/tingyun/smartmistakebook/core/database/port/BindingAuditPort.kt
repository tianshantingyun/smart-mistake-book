package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.BindingAuditSampleRow
import com.tingyun.smartmistakebook.core.database.DEFAULT_BINDING_AUDIT_SAMPLES_PER_SUBJECT_WEEK
import com.tingyun.smartmistakebook.core.database.RecordBindingAuditSampleCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 绑定正确性监测（KF-29）的持久化端口。
 *
 * 默认全部"做不到"（false / null / 空流）：测试替身与无库界面没有这张表。默认值刻意选
 * "做不到"而不是"假装做到了"——写侧据此不会以为"这条绑定已被抽检"，复核屏也不会冒领样本。
 */
interface BindingAuditPort {
    /**
     * 入队一条抽样（PENDING）；配额满或样本已存在时返回 false。
     * 配额默认每科每周 [DEFAULT_BINDING_AUDIT_SAMPLES_PER_SUBJECT_WEEK] 条。
     */
    suspend fun recordBindingAuditSample(
        command: RecordBindingAuditSampleCommand,
        maxPerSubjectWeek: Int = DEFAULT_BINDING_AUDIT_SAMPLES_PER_SUBJECT_WEEK,
    ): Boolean = false

    /** 观察抽样行（[status] 为 null 时全部），按 sample_id 升序。 */
    fun observeBindingAuditSamples(status: String? = null): Flow<List<BindingAuditSampleRow>> =
        flowOf(emptyList())

    /** 读抽样行（[status] 为 null 时全部）。 */
    suspend fun readBindingAuditSamples(status: String? = null): List<BindingAuditSampleRow> = emptyList()

    /** 落判（PENDING→REVIEWED）；返回操作后该行的权威快照（已判定时为第一条判定）。 */
    suspend fun reviewBindingAuditSample(
        sampleId: String,
        verdict: String,
        reviewedAtEpochMillis: Long,
    ): BindingAuditSampleRow? = null
}
