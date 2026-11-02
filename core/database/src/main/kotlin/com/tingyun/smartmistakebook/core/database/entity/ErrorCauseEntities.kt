package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 批次 40 / WS2（schema 73）：错因**追加式 observation**。
 *
 * **为什么新建表，而不是复用 `problem_classification_candidate`/`binding` 或
 * `attempt_event.error_type`**（对账 R2 的高危复核）：
 *
 * 1. `problem_classification_*` 的作用域是 `(problem_id, basis_revision_id)`，没有
 *    `learner_id`、`attempt_id`，也没有撤回状态机；同一道题对不同学习者可以有不同的错因，
 *    把它塞进题级分类表会把两个人/两次作答的原因压成一条。它还会持久化 `display_name`，
 *    与产品合同"错因不得成为可见标签/必填字段"直接冲突。
 * 2. `attempt_event.error_type` 只能表达"一次作答一个值"，没有候选/拒绝/撤回历史、没有来源
 *    与证据引用；R-09 已确认该列无写入方。把它当权威会制造第二份真相。
 * 3. 既有 `ClassificationDimension.ERROR_CAUSE` 继续保留给"用户手填备注型分类"；当前生产
 *    组织任务只接受 CHAPTER/KNOWLEDGE，`ProblemOrganizationTasksTest.
 *    organizationOnlyAcceptsContentHierarchyClassifications` 钉住 ERROR_CAUSE 会被
 *    `ProblemClassificationSuggestion` 拒绝。本表是**内部标签账本**，不是分类浏览面；
 *    两套词表的边界写在这里，避免后续把 E01–E04 写进 classification 表。
 *
 * 语义不变量（R2 §4.7 / §6.3）：
 * - 只追加；`UPDATE` 只允许改 `status/decided_at/decision_source`；
 * - 不提供 DELETE DAO；父行删除是 RESTRICT；
 * - `evidence_refs_json` 是软引用，允许悬空；
 * - `taxonomy_version` 与 `extractor_version` 是换版重新认证的键，不得复用旧值。
 */
@Entity(
    tableName = "error_cause_observation",
    foreignKeys = [
        ForeignKey(
            entity = PracticeUnitEntity::class,
            parentColumns = ["practice_unit_id"],
            childColumns = ["practice_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["problem_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AttemptEventEntity::class,
            parentColumns = ["attempt_id"],
            childColumns = ["attempt_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ErrorCauseObservationEntity::class,
            parentColumns = ["observation_id"],
            childColumns = ["supersedes_observation_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            name = "index_error_cause_observation_learner_unit_observed",
            value = ["learner_id", "practice_unit_id", "observed_at_epoch_millis"],
        ),
        Index(
            name = "index_error_cause_observation_attempt_id",
            value = ["attempt_id"],
        ),
        Index(
            name = "index_error_cause_observation_unit_cause",
            value = ["practice_unit_id", "cause"],
        ),
    ],
)
internal data class ErrorCauseObservationEntity(
    @PrimaryKey
    @ColumnInfo(name = "observation_id")
    val observationId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String?,
    @ColumnInfo(name = "attempt_id")
    val attemptId: String?,
    val cause: String,
    @ColumnInfo(name = "detail_code")
    val detailCode: String?,
    val confidence: Double,
    val channel: String,
    @ColumnInfo(name = "extractor_version")
    val extractorVersion: String,
    @ColumnInfo(name = "taxonomy_version")
    val taxonomyVersion: String,
    @ColumnInfo(name = "rationale_markdown")
    val rationaleMarkdown: String,
    @ColumnInfo(name = "evidence_refs_json")
    val evidenceRefsJson: String,
    @ColumnInfo(name = "observed_at_epoch_millis")
    val observedAtEpochMillis: Long,
    val status: String,
    @ColumnInfo(name = "decided_at_epoch_millis")
    val decidedAtEpochMillis: Long?,
    @ColumnInfo(name = "decision_source")
    val decisionSource: String?,
    @ColumnInfo(name = "supersedes_observation_id")
    val supersedesObservationId: String?,
)

/**
 * 批次 40 / WS2（schema 73）：错因**现行标签**（每作用域最多一条 ACTIVE）。
 *
 * 撤销/替换是状态迁移，不是删除：`VOIDED`/`SUPERSEDED` 行永远留着，审计能回答"曾经标过
 * 什么、谁撤的、为什么"。`active_key` 唯一索引兜底"同一 scope 不可能两条 ACTIVE"；
 * 读侧只认 `state='ACTIVE'`，不得用"最新时间戳"兜底（时钟异常会翻标签）。
 *
 * `source_observation_id` 必须指向 `status='ACCEPTED'` 的 observation；FK 只能保证存在，
 * 状态由 DAO 的事务函数与读侧 JOIN 共同保证。
 */
@Entity(
    tableName = "error_cause_binding",
    foreignKeys = [
        ForeignKey(
            entity = PracticeUnitEntity::class,
            parentColumns = ["practice_unit_id"],
            childColumns = ["practice_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["problem_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ErrorCauseObservationEntity::class,
            parentColumns = ["observation_id"],
            childColumns = ["source_observation_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ErrorCauseBindingEntity::class,
            parentColumns = ["binding_id"],
            childColumns = ["superseded_by_binding_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            name = "index_error_cause_binding_active_key",
            value = ["active_key"],
            unique = true,
        ),
        Index(
            name = "index_error_cause_binding_learner_unit_state",
            value = ["learner_id", "practice_unit_id", "state"],
        ),
        Index(
            name = "index_error_cause_binding_problem_revision_id",
            value = ["problem_revision_id"],
        ),
        Index(
            name = "index_error_cause_binding_source_observation_id",
            value = ["source_observation_id"],
        ),
    ],
)
internal data class ErrorCauseBindingEntity(
    @PrimaryKey
    @ColumnInfo(name = "binding_id")
    val bindingId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String?,
    val cause: String,
    @ColumnInfo(name = "detail_code")
    val detailCode: String?,
    val confidence: Double,
    @ColumnInfo(name = "acceptance_source")
    val acceptanceSource: String,
    @ColumnInfo(name = "gate_version")
    val gateVersion: String?,
    @ColumnInfo(name = "source_observation_id")
    val sourceObservationId: String,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
    val state: String,
    @ColumnInfo(name = "active_key")
    val activeKey: String?,
    @ColumnInfo(name = "superseded_by_binding_id")
    val supersededByBindingId: String?,
    @ColumnInfo(name = "ended_at_epoch_millis")
    val endedAtEpochMillis: Long?,
)
