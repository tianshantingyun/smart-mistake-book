package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.BindingAuditSnapshotCodec

/**
 * KF-29 聚合口径：错误率按**科 / 模型版本**分组。
 *
 * **它消灭的失败**：抽样落判之后，判定只是散落的行——没有一处能把"哪个学科、哪个模型版本的
 * 绑定错得多"算出来；更没有一处会在快照读不出来时**如实说"有一行没读出来"**。本对象把
 * 口径固定成一处纯函数（JVM 可测、报告与复核屏共用同一份计算），并让不可读的行计数可见，
 * 而不是从分母里静默蒸发。
 *
 * 口径定义（报告与复核屏必须与此一致）：
 * - 只统计 `REVIEWED` 且 verdict 合法的行；`PENDING` 不参与。
 * - `errorRate = WRONG / (CORRECT + WRONG)`；**AMBIGUOUS 不进分子也不进分母**（审阅者无法
 *   判定时不拿它当对或错），但它按组计数可见。
 * - 分母为 0 时 [BindingAuditErrorRate.errorRate] 为 null——不许把"没有可判样本"写成 0。
 * - 快照解码失败或 verdict 缺失/非法 → 计入 [BindingAuditAggregation.unreadableReviewedCount]，
 *   不参与任何分组。
 * - 分组键：`subject` + `modelVersion`（快照里的模型版本；离线用户纠正为 null → `UNSPECIFIED`）。
 */
data class BindingAuditErrorRate(
    val subject: String,
    val modelVersion: String,
    val reviewedCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val ambiguousCount: Int,
) {
    val errorRate: Double?
        get() = (correctCount + wrongCount).takeIf { it > 0 }?.let { denominator ->
            wrongCount.toDouble() / denominator
        }
}

data class BindingAuditAggregation(
    val groups: List<BindingAuditErrorRate>,
    /** REVIEWED 但快照/判定读不出来的行数（聚合必须如实暴露它）。 */
    val unreadableReviewedCount: Int,
)

object BindingAuditAggregator {
    const val UNSPECIFIED_MODEL_VERSION = "UNSPECIFIED"

    fun aggregate(samples: List<BindingAuditSampleRow>): BindingAuditAggregation {
        var unreadable = 0
        val reviewedFacts = samples.mapNotNull { row ->
            if (row.status != BINDING_AUDIT_STATUS_REVIEWED) return@mapNotNull null
            val verdict = row.verdict?.takeIf { it in BINDING_AUDIT_VERDICTS }
            val snapshot = BindingAuditSnapshotCodec.decode(row.bindingSnapshotJson)
            if (verdict == null || snapshot == null) {
                unreadable += 1
                return@mapNotNull null
            }
            ReviewedFact(
                subject = snapshot.subject,
                modelVersion = snapshot.modelVersion?.takeIf(String::isNotBlank)
                    ?: UNSPECIFIED_MODEL_VERSION,
                verdict = verdict,
            )
        }
        val groups = reviewedFacts
            .groupBy { fact -> fact.subject to fact.modelVersion }
            .map { (key, facts) ->
                BindingAuditErrorRate(
                    subject = key.first,
                    modelVersion = key.second,
                    reviewedCount = facts.size,
                    correctCount = facts.count { it.verdict == BINDING_AUDIT_VERDICT_CORRECT },
                    wrongCount = facts.count { it.verdict == BINDING_AUDIT_VERDICT_WRONG },
                    ambiguousCount = facts.count { it.verdict == BINDING_AUDIT_VERDICT_AMBIGUOUS },
                )
            }
            .sortedWith(compareBy({ it.subject }, { it.modelVersion }))
        return BindingAuditAggregation(groups = groups, unreadableReviewedCount = unreadable)
    }

    private data class ReviewedFact(
        val subject: String,
        val modelVersion: String,
        val verdict: String,
    )
}
