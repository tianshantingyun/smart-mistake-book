package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "problem",
    indices = [Index(value = ["canonical_fingerprint"], unique = true)],
)
internal data class ProblemEntity(
    @PrimaryKey
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "canonical_fingerprint")
    val canonicalFingerprint: String,
    val subject: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "archived_at_epoch_millis")
    val archivedAtEpochMillis: Long? = null,
)

/** Revisions are append-only. No DAO exposes an update or delete operation for this table. */
@Entity(
    tableName = "problem_revision",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["problem_id"],
            childColumns = ["problem_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
        Index(value = ["problem_id", "revision_id"], unique = true),
        Index(value = ["problem_id", "revision_number"], unique = true),
        Index(value = ["problem_id", "content_fingerprint"], unique = true),
    ],
)
internal data class ProblemRevisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "revision_id")
    val revisionId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "revision_number")
    val revisionNumber: Int,
    val title: String,
    @ColumnInfo(name = "problem_markdown")
    val problemMarkdown: String,
    @ColumnInfo(name = "question_document_snapshot")
    val questionDocumentSnapshot: String? = null,
    @ColumnInfo(name = "answer_spec_id")
    val answerSpecId: String?,
    @ColumnInfo(name = "answer_spec_snapshot")
    val answerSpecSnapshot: String?,
    @ColumnInfo(name = "answer_verification_status")
    val answerVerificationStatus: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_reference")
    val sourceReference: String?,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "practice_unit",
    foreignKeys = [
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["problem_id", "problem_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["problem_id"]),
        Index(value = ["problem_revision_id"]),
        Index(value = ["problem_id", "problem_revision_id"]),
        Index(value = ["practice_unit_id", "problem_id"], unique = true),
        Index(value = ["practice_unit_id", "problem_revision_id"], unique = true),
        Index(value = ["problem_revision_id", "unit_key"], unique = true),
    ],
)
internal data class PracticeUnitEntity(
    @PrimaryKey
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "unit_key")
    val unitKey: String,
    @ColumnInfo(name = "unit_kind")
    val unitKind: String,
    val title: String,
    @ColumnInfo(name = "prompt_markdown")
    val promptMarkdown: String,
    @ColumnInfo(name = "estimated_seconds")
    val estimatedSeconds: Int,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "error_book_entry",
    foreignKeys = [
        ForeignKey(
            entity = PracticeUnitEntity::class,
            parentColumns = ["practice_unit_id", "problem_id"],
            childColumns = ["practice_unit_id", "problem_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["problem_id", "current_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["practice_unit_id"], unique = true),
        Index(value = ["practice_unit_id", "problem_id"]),
        Index(value = ["current_revision_id"]),
        Index(value = ["problem_id", "current_revision_id"]),
        Index(value = ["source_key"], unique = true),
        Index(value = ["status", "updated_at_epoch_millis"]),
    ],
)
internal data class ErrorBookEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "current_revision_id")
    val currentRevisionId: String,
    @ColumnInfo(name = "source_key")
    val sourceKey: String?,
    val status: String,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "user_note")
    val userNote: String? = null,
    /**
     * C11 收藏：条目的私有标记（与 [userNote] 同层——可自由改动，不在 append-only 修订链上）。
     * 默认 0 = 未收藏；旧库升级后全员 0（迁移不回填"历史收藏"，那是编造）。
     */
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
)

@Entity(
    tableName = "knowledge_node",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["parent_knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stable_code"], unique = true),
        Index(value = ["parent_knowledge_node_id"]),
        Index(value = ["subject", "display_name"]),
        Index(value = ["subject", "canonical_name"]),
        Index(value = ["subject", "granularity"]),
    ],
)
internal data class KnowledgeNodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String,
    @ColumnInfo(name = "stable_code")
    val stableCode: String,
    val subject: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "canonical_name", defaultValue = "''")
    val canonicalName: String,
    @ColumnInfo(name = "node_kind", defaultValue = "'TOPIC'")
    val nodeKind: String,
    @ColumnInfo(name = "granularity", defaultValue = "'TOPIC'")
    val granularity: String,
    @ColumnInfo(name = "aliases_text", defaultValue = "''")
    val aliasesText: String,
    @ColumnInfo(name = "boundary_markdown")
    val boundaryMarkdown: String?,
    @ColumnInfo(name = "verification_status", defaultValue = "'MODEL_CANDIDATE'")
    val verificationStatus: String,
    @ColumnInfo(name = "parent_knowledge_node_id")
    val parentKnowledgeNodeId: String?,
    @ColumnInfo(name = "taxonomy_version")
    val taxonomyVersion: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    /**
     * 内容生命周期：`ACTIVE`（现行，参与新工作）或 `RETIRED`（已退役）。
     *
     * **退役永不物删**：外键在 8 张表上是 RESTRICT，而 `learner_knowledge_mastery_state`
     * 之类挂着学生的错题绑定、掌握度、复习队列——物删要么被外键挡住，要么（对 CASCADE 的
     * 表）静默带走学生数据。退役的节点从**新工作**里消失（不再被召回、不再进整理提示词、
     * 不再进新复习计划），但行与 id 保留，学生历史照旧可读。
     */
    @ColumnInfo(name = "status", defaultValue = "'ACTIVE'")
    val status: String = "ACTIVE",
    /**
     * 被谁取代（合并时的 1:1 重定向目标，取 `knowledge_node_id`）。
     *
     * **刻意不建外键**：这是软指针，悬空无害（解析不到就退回显示原节点自己的名字），
     * 而建外键会引入自引用约束、并让"目标也被退役"这类链式情形变得难以迁移。
     * `DELETE` 与 `SPLIT` 没有唯一目标，此列为 null——那两类在历史界面显示旧名。
     */
    @ColumnInfo(name = "superseded_by")
    val supersededBy: String? = null,
)

@Entity(
    tableName = "knowledge_source",
    indices = [
        Index(value = ["content_fingerprint"], unique = true),
        Index(value = ["subject", "source_type"]),
    ],
)
internal data class KnowledgeSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val subject: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    val title: String,
    val publisher: String?,
    val edition: String?,
    @ColumnInfo(name = "source_uri")
    val sourceUri: String?,
    @ColumnInfo(name = "license_status")
    val licenseStatus: String,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String,
    @ColumnInfo(name = "imported_at_epoch_millis")
    val importedAtEpochMillis: Long,
    @ColumnInfo(
        name = "content_use_policy",
        defaultValue = "'REVIEWED_SYNTHESIS_ONLY'",
    )
    val contentUsePolicy: String = "REVIEWED_SYNTHESIS_ONLY",
    @ColumnInfo(name = "license_expression")
    val licenseExpression: String? = null,
    @ColumnInfo(name = "license_uri")
    val licenseUri: String? = null,
    @ColumnInfo(name = "attribution_text")
    val attributionText: String? = null,
)

@Entity(
    tableName = "knowledge_node_source_binding",
    primaryKeys = ["knowledge_node_id", "source_id", "source_locator"],
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["knowledge_node_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KnowledgeSourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["knowledge_node_id"]),
        Index(value = ["source_id"]),
    ],
)
internal data class KnowledgeNodeSourceBindingEntity(
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "source_locator")
    val sourceLocator: String,
    @ColumnInfo(name = "derivation_note")
    val derivationNote: String,
    @ColumnInfo(name = "reviewed_at_epoch_millis")
    val reviewedAtEpochMillis: Long?,
)

@Entity(
    tableName = "knowledge_node_relation",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["prerequisite_knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["dependent_knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = KnowledgeSourceEntity::class,
            parentColumns = ["source_id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            value = [
                "prerequisite_knowledge_node_id",
                "dependent_knowledge_node_id",
                "relation_type",
            ],
            unique = true,
        ),
        Index(value = ["dependent_knowledge_node_id"]),
        Index(value = ["source_id"]),
        Index(value = ["subject", "relation_type"]),
        Index(value = ["subject", "dependent_knowledge_node_id"]),
    ],
)
internal data class KnowledgeNodeRelationEntity(
    @PrimaryKey
    @ColumnInfo(name = "relation_id")
    val relationId: String,
    val subject: String,
    @ColumnInfo(name = "prerequisite_knowledge_node_id")
    val prerequisiteKnowledgeNodeId: String,
    @ColumnInfo(name = "dependent_knowledge_node_id")
    val dependentKnowledgeNodeId: String,
    @ColumnInfo(name = "relation_type")
    val relationType: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "source_locator")
    val sourceLocator: String,
    @ColumnInfo(name = "reviewed_at_epoch_millis")
    val reviewedAtEpochMillis: Long,
)

@Entity(
    tableName = "knowledge_grounding_request",
    indices = [
        Index(
            value = ["organization_request_id", "request_ordinal"],
            unique = true,
        ),
        Index(value = ["status", "created_at_epoch_millis"]),
        Index(value = ["subject", "status"]),
        Index(value = ["grounding_key", "status"]),
        Index(value = ["problem_revision_id"]),
    ],
)
internal data class KnowledgeGroundingRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "grounding_request_id")
    val groundingRequestId: String,
    @ColumnInfo(name = "grounding_key")
    val groundingKey: String,
    @ColumnInfo(name = "organization_request_id")
    val organizationRequestId: String,
    @ColumnInfo(name = "organization_request_fingerprint")
    val organizationRequestFingerprint: String,
    @ColumnInfo(name = "request_ordinal")
    val requestOrdinal: Int,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    val subject: String,
    val query: String,
    @ColumnInfo(name = "expected_parent_knowledge_display_name")
    val expectedParentKnowledgeDisplayName: String,
    @ColumnInfo(name = "reason_markdown")
    val reasonMarkdown: String,
    val status: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "knowledge_grounding_resolution",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["resolution_id"], unique = true),
        Index(value = ["knowledge_node_id"]),
        Index(value = ["subject", "resolved_at_epoch_millis"]),
    ],
)
internal data class KnowledgeGroundingResolutionEntity(
    @PrimaryKey
    @ColumnInfo(name = "grounding_key")
    val groundingKey: String,
    @ColumnInfo(name = "resolution_id")
    val resolutionId: String,
    val subject: String,
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String,
    @ColumnInfo(name = "taxonomy_version")
    val taxonomyVersion: String,
    @ColumnInfo(name = "resolved_occurrence_count")
    val resolvedOccurrenceCount: Int,
    @ColumnInfo(name = "linked_practice_unit_count")
    val linkedPracticeUnitCount: Int,
    @ColumnInfo(name = "resolved_at_epoch_millis")
    val resolvedAtEpochMillis: Long,
)

@Entity(
    tableName = "practice_unit_knowledge_binding",
    foreignKeys = [
        ForeignKey(
            entity = PracticeUnitEntity::class,
            parentColumns = ["practice_unit_id", "problem_revision_id"],
            childColumns = ["practice_unit_id", "basis_revision_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KnowledgeNodeEntity::class,
            parentColumns = ["knowledge_node_id"],
            childColumns = ["knowledge_node_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["practice_unit_id"]),
        Index(value = ["basis_revision_id"]),
        Index(value = ["practice_unit_id", "basis_revision_id"]),
        Index(value = ["knowledge_node_id"]),
        Index(
            value = [
                "binding_id",
                "practice_unit_id",
                "knowledge_node_id",
                "basis_revision_id",
                "taxonomy_version",
            ],
            unique = true,
        ),
        Index(
            value = [
                "practice_unit_id",
                "knowledge_node_id",
                "basis_revision_id",
                "taxonomy_version",
            ],
            unique = true,
        ),
    ],
)
internal data class PracticeUnitKnowledgeBindingEntity(
    @PrimaryKey
    @ColumnInfo(name = "binding_id")
    val bindingId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "knowledge_node_id")
    val knowledgeNodeId: String,
    @ColumnInfo(name = "basis_revision_id")
    val basisRevisionId: String,
    val strength: Double,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "taxonomy_version")
    val taxonomyVersion: String,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
)

@Entity(
    tableName = "problem_relation",
    foreignKeys = [
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["source_problem_id", "source_basis_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["target_problem_id", "target_basis_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["source_problem_id"]),
        Index(value = ["target_problem_id"]),
        Index(value = ["source_basis_revision_id"]),
        Index(value = ["target_basis_revision_id"]),
        Index(value = ["source_problem_id", "source_basis_revision_id"]),
        Index(value = ["target_problem_id", "target_basis_revision_id"]),
        Index(
            value = [
                "source_problem_id",
                "target_problem_id",
                "relation_type",
                "source_basis_revision_id",
                "target_basis_revision_id",
            ],
            unique = true,
        ),
    ],
)
internal data class ProblemRelationEntity(
    @PrimaryKey
    @ColumnInfo(name = "relation_id")
    val relationId: String,
    @ColumnInfo(name = "source_problem_id")
    val sourceProblemId: String,
    @ColumnInfo(name = "target_problem_id")
    val targetProblemId: String,
    @ColumnInfo(name = "relation_type")
    val relationType: String,
    val status: String,
    @ColumnInfo(name = "source_basis_revision_id")
    val sourceBasisRevisionId: String,
    @ColumnInfo(name = "target_basis_revision_id")
    val targetBasisRevisionId: String,
    val confidence: Double,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "problem_classification_binding",
    foreignKeys = [
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["problem_id", "basis_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["problem_id", "basis_revision_id"]),
        Index(value = ["dimension", "label_id"]),
        Index(
            value = ["problem_id", "basis_revision_id", "dimension", "label_id"],
            unique = true,
        ),
    ],
)
internal data class ProblemClassificationBindingEntity(
    @PrimaryKey
    @ColumnInfo(name = "binding_id")
    val bindingId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "basis_revision_id")
    val basisRevisionId: String,
    val dimension: String,
    @ColumnInfo(name = "label_id")
    val labelId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "taxonomy_version")
    val taxonomyVersion: String,
    @ColumnInfo(name = "acceptance_source")
    val acceptanceSource: String,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
    /**
     * KF-30：该标签被接受时的**真实**置信度（模型对本标签的接受分数）。
     *
     * **语义**：`NULL` = 未知——v63 及更早写入的旧行（升级时刻真值已不可考，迁移不回填），
     * 或该行来自用户确认/离线纠正路径（人的选择没有模型分数，权威性由 [acceptanceSource]
     * 表达）。合并（`mergeAutomaticClassifications`）读本列参与替换排序：NULL 按接受门
     * `CLASSIFICATION_ACCEPTANCE_CONFIDENCE`（0.78）参与比较——任何**严格更高且 ≥ 门值**
     * 的新提议都可以替换它（KF-30 Q2 interim，未知**不冻结**），此后写回新提议的真值。
     * 绝不许把"未知"伪造成门值：那正是 KF-30 (a)(b) 的根因。
     */
    @ColumnInfo(name = "accepted_confidence")
    val acceptedConfidence: Double? = null,
)

/**
 * K5-B（schema 65）：一次整理确认里**模型给出的分类候选集与自报分数**（含被拒候选）。
 *
 * 消灭的失败（R1 机制 ② 的前置，研究文档
 * `docs/research/2026-10-11-r1-r2-label-accuracy-research.md` §1.3）：改前只存**被接受**的标签
 * （`problem_classification_binding`），模型给过什么、给了多少分、哪些没过接受门或被预算挤掉
 * ——**全部不可考**。于是既算不出候选竞争关系（cleanlab `normalized_margin` 需要的
 * `p(选中) − max(p(其它候选))`），也看不出接受门的**选择偏差**（分数被门截断）。
 *
 * 语义（唯一口径，写在列上）：
 * - **一行 = 本轮（同一 `problem_id + basis_revision_id + accepted_at_epoch_millis`）该模型候选**；
 *   候选身份 = `dimension + label_id`（与绑定表同一 labelId 口径），同轮同身份只有一行；
 * - [accepted] = 该候选在本轮确认后成为确认集里的标签身份（同时被本轮接受策略放行）——
 *   被接受门拒掉、被预算挤掉、被更高置信存量顶掉的候选一律 `false`（**被拒候选也要落**）；
 * - [confidence] = 模型**自报**分数（未校准；校准审计见 `ClassificationCalibrationAuditTest`）；
 * - [acceptanceSource] = **本轮确认的接受权威**（与绑定表同域）——`accepted = true` 时它说明
 *   这个标签是**自动放行**的还是**用户勾选**的；`accepted = false` 的行同样带本轮权威
 *   （"自动整理里没过门"与"用户确认时没勾"是两种不同的被拒）。校准审计必须按它筛出
 *   `LOCAL_POLICY_ACCEPTED`，否则会把用户勾选（可低于接受门）混进"自动接受"的实测正确率；
 * - **只写不改、只派生不纠**：本表是历史证据，派生出的 margin/「存疑」信号**不得**回写绑定表、
 *   不得删除或降信任何已接受标签（研究 §0/§1.2：外部无先例支持静默自纠）。
 */
@Entity(
    tableName = "problem_classification_candidate",
    foreignKeys = [
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["problem_id", "revision_id"],
            childColumns = ["problem_id", "basis_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["problem_id", "basis_revision_id"]),
        Index(value = ["dimension", "label_id"]),
        // 同一轮的候选按 accepted_at 分组（margin 的"同维度对手集"= 同轮同维度的全部候选）。
        Index(value = ["problem_id", "basis_revision_id", "accepted_at_epoch_millis"]),
    ],
)
internal data class ProblemClassificationCandidateEntity(
    @PrimaryKey
    @ColumnInfo(name = "candidate_id")
    val candidateId: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "basis_revision_id")
    val basisRevisionId: String,
    val dimension: String,
    @ColumnInfo(name = "label_id")
    val labelId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    /** 模型自报置信度（0.0..1.0，未校准）。 */
    val confidence: Double,
    /** 本轮确认后该候选身份是否在确认集内（被拒候选为 `false`）。 */
    val accepted: Boolean,
    /** 本轮确认的接受权威（`BindingAcceptanceSource` 同域）：自动放行 or 用户勾选。 */
    @ColumnInfo(name = "acceptance_source")
    val acceptanceSource: String,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
)

@Entity(
    tableName = "problem_organization_receipt",
    foreignKeys = [
        ForeignKey(
            entity = PracticeUnitEntity::class,
            parentColumns = ["practice_unit_id", "problem_revision_id"],
            childColumns = ["practice_unit_id", "problem_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["problem_id", "problem_revision_id"]),
        Index(value = ["practice_unit_id", "problem_revision_id"]),
    ],
)
internal data class ProblemOrganizationReceiptEntity(
    @PrimaryKey
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "payload_fingerprint")
    val payloadFingerprint: String,
    @ColumnInfo(name = "problem_id")
    val problemId: String,
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    @ColumnInfo(name = "practice_unit_id")
    val practiceUnitId: String,
    @ColumnInfo(name = "classification_count")
    val classificationCount: Int,
    @ColumnInfo(name = "relation_count")
    val relationCount: Int,
    @ColumnInfo(name = "accepted_at_epoch_millis")
    val acceptedAtEpochMillis: Long,
)
