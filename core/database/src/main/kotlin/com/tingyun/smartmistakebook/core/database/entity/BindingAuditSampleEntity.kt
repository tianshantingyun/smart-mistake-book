package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * KF-29 绑定正确性监测：一条待/已复核的绑定抽样（阶段 3C 批 1，schema 61→62）。
 *
 * **它消灭的失败**：绑定写入之后没有任何"事后抽检"的位置——错了也没人看得见（见
 * [com.tingyun.smartmistakebook.core.model.BindingAuditSnapshot] 的 KDoc）。本表是抽样队列的
 * 持久身份：写入侧按"每科每周首 N 条新绑定"入队，debug 复核屏逐条落判，聚合按科/模型版本
 * 算错误率。表只承载**审计辅助**，不是学习事实；删除它不影响任何学习行为。
 *
 * 列的口径：
 * - `sample_id`：内容寻址的样本身份（`binding-audit:<科>:<周键>:<摘要>`，见
 *   `BindingAuditSampling.sampleId`）。主键 + INSERT IGNORE：同一次绑定确认重放不会产生第二条样本。
 * - `practice_unit_id`：被抽检的题（绑定挂在这一题上）。
 * - `binding_snapshot_json`：写入那一刻的绑定快照（`BindingAuditSnapshotCodec` 编码）。
 * - `status`：`PENDING` → `REVIEWED`（只前进；复核落判是 CAS，见 `BindingAuditSampleDao.review`）。
 * - `verdict`：`CORRECT` / `WRONG` / `AMBIGUOUS`；PENDING 时为 NULL（没有判定不许先写一个）。
 * - `reviewed_at`：落判时刻；PENDING 时为 NULL（与 verdict 同进同出）。
 */
@Entity(tableName = "binding_audit_sample")
internal data class BindingAuditSampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "sample_id")
    val sampleId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "binding_snapshot_json")
    val bindingSnapshotJson: String,
    val status: String,
    val verdict: String? = null,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAtEpochMillis: Long? = null,
)
