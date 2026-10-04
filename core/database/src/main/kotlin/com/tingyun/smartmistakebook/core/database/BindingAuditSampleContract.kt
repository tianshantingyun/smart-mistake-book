package com.tingyun.smartmistakebook.core.database

/**
 * KF-29 绑定抽样行（阶段 3C 批 1）。字段口径见
 * [com.tingyun.smartmistakebook.core.database.entity.BindingAuditSampleEntity] 的 KDoc。
 */
data class BindingAuditSampleRow(
    val sampleId: String,
    val practiceUnitId: String,
    val bindingSnapshotJson: String,
    val status: String,
    val verdict: String?,
    val reviewedAtEpochMillis: Long?,
)

/**
 * 入队一条绑定抽样：调用方给出内容寻址的 [sampleId]、它的配额前缀 [quotaPrefix]（计数用）与快照。
 *
 * [quotaPrefix] 由 `BindingAuditSampling.quotaPrefix` 生成，必须与 [sampleId] 前缀一致——
 * 这个不变量在 DAO 里强制：否则配额会数到别的科/周去，封顶就形同虚设。
 */
data class RecordBindingAuditSampleCommand(
    val sampleId: String,
    val quotaPrefix: String,
    val practiceUnitId: String,
    val bindingSnapshotJson: String,
)

/** 待复核（唯一非终态）。 */
const val BINDING_AUDIT_STATUS_PENDING = "PENDING"

/** 已落判（终态；再次复核不覆盖第一条判定）。 */
const val BINDING_AUDIT_STATUS_REVIEWED = "REVIEWED"

const val BINDING_AUDIT_VERDICT_CORRECT = "CORRECT"
const val BINDING_AUDIT_VERDICT_WRONG = "WRONG"
const val BINDING_AUDIT_VERDICT_AMBIGUOUS = "AMBIGUOUS"

val BINDING_AUDIT_STATUSES: Set<String> = setOf(
    BINDING_AUDIT_STATUS_PENDING,
    BINDING_AUDIT_STATUS_REVIEWED,
)

val BINDING_AUDIT_VERDICTS: Set<String> = setOf(
    BINDING_AUDIT_VERDICT_CORRECT,
    BINDING_AUDIT_VERDICT_WRONG,
    BINDING_AUDIT_VERDICT_AMBIGUOUS,
)

/** 默认配额：每科每周首 5 条新绑定（KF-29 规格默认值）。 */
const val DEFAULT_BINDING_AUDIT_SAMPLES_PER_SUBJECT_WEEK = 5
