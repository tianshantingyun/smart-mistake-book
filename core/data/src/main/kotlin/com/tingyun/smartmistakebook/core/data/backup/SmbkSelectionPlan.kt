package com.tingyun.smartmistakebook.core.data.backup

import java.security.MessageDigest

/**
 * 6C-2 内容选择备份的 **schema 70 机器清单**（v3）。
 *
 * 唯一数据源：`docs/research/2026-10-17-6c2-content-selection-design.md` §9.11 的
 * `<!-- 6c2-plan-v3:begin -->` 机器块；表/视图/FK 的事实源是 Room 导出物
 * `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json`
 * （`database.version=70`、`identityHash=11a8a701…`）。本文件不新增规则：类别、闭包、
 * 裁剪顺序、软引用、资产根、kind 谓词全部逐项对应机器块，`SmbkSelectionPlanTest`
 * 反向解析机器块与 `70.json` 断言两处同源。
 *
 * 90 张表的归属（每表恰好一次）：
 * - [SmbkSelectionCategory] 四类：31 + 35 + 7 + 1；
 * - [alwaysTables] 7 张恒随包；
 * - [derivedTables] 2 张派生索引（本批裁定恒裁，恢复完成后按科后台重建）；
 * - [splitTables] 7 张按行筛选（`model_task*` 按 `task_kind`，`figure_generation_*` 按来源类别 +
 *   存活根引用，`tutor_visual_program` / `tutor_visual_snapshot` 按存活 link/题面引用 +
 *   INITIAL 基线，见 [modelTaskKindCategory] / [figureOperationSourceKindCategory]）。
 *
 * 两项视图（[views]）没有行也不参与裁剪；`library_search_fts` 是 FTS4 外部内容虚拟表，
 * 归在 ISSUE_BOOK 类别下，但**不进入** [trimOrder]，由删除后的 `rebuild` 收尾（[rebuildTables]）。
 *
 * 本批已确认的产品裁定（不得回退）：
 * 1. 不提供「仅图片」组合（mask=8）；
 * 2. 禁止空选择（mask=0）；
 * 3. 派生索引恒裁，恢复完成后按科重建。
 *
 * 因此 [allowedMasks] 只有 9（题+图）、13（题+会话+图）、15（全选）；0、8 与
 * 1–7、10–12、14 一律拒绝，**不静默补齐**。全选也走裁剪器，但只裁派生索引。
 *
 * [closureDigest] 是 §9.7 定稿的 v3 算法，只用于同构对拍与第三方重裁的粗筛；权威始终是
 * 包内库实测。
 */
object SmbkSelectionPlan {

    /** 机器块 `schemaVersion`，同时是 `70.json` 的 `database.version`。 */
    const val SCHEMA_VERSION: Int = 70

    /** 机器块 `identityHash`，必须与 `70.json` 的 `database.identityHash` 相等。 */
    const val IDENTITY_HASH: String = "11a8a701bae767a8a3c5bc810e4743a4"

    /** 规则表版本；只在规则表变化时 bump，不是 schemaVersion 的别名。 */
    const val CLOSURE_VERSION: Int = 3

    /** digest 前缀，与 closureVersion=3 绑定。 */
    const val CLOSURE_DIGEST_PREFIX: String = "smbk-selection-v3"

    /** 四项用户类别全选。 */
    const val FULL_MASK: Int = 15

    /** 合法组合：题+图、题+会话+图、全选。0/8 被产品裁定拒绝。 */
    val allowedMasks: Set<Int> = setOf(9, 13, 15)

    /** 其余 13 个 mask 全部拒绝（含闭包自洽但产品裁定的 0/8）。 */
    val rejectedMasks: Set<Int> = (0..FULL_MASK).toSet() - allowedMasks

    /** v3 合法 mask 都含 IMAGES，资产策略只能是 INCLUDED（无图子集不实现）。 */
    const val ASSET_POLICY_INCLUDED: String = "INCLUDED"

    /** 两项视图：无行、不裁剪。 */
    val views: List<String> = listOf(
        "library_catalog",
        "knowledge_question_lattice",
    )

    /** 类别 → 表（机器块 `categories`，每类内部顺序与机器块一致）。 */
    val categoryTables: Map<SmbkSelectionCategory, List<String>> = linkedMapOf(
        SmbkSelectionCategory.ISSUE_BOOK to listOf(
            "problem",
            "problem_revision",
            "practice_unit",
            "error_book_entry",
            "problem_relation",
            "problem_classification_binding",
            "problem_classification_candidate",
            "problem_organization_receipt",
            "practice_unit_knowledge_binding",
            "problem_revision_source_asset",
            "problem_revision_visual",
            "problem_draft",
            "problem_draft_source_asset",
            "problem_draft_revision",
            "problem_draft_edit_snapshot",
            "problem_draft_commit_receipt",
            "batch_import_job",
            "batch_import_page",
            "split_import_job",
            "split_import_question",
            "clean_redraw_task",
            "library_search_content",
            "library_search_fts",
            "library_search_outbox",
            "knowledge_grounding_request",
            "knowledge_grounding_resolution",
            "knowledge_research_review_bundle",
            "knowledge_research_review_source",
            "mistake_export_record",
            "binding_audit_sample",
            "llm_teaching_advisory",
        ),
        SmbkSelectionCategory.MASTERY to listOf(
            "assessment_item_snapshot",
            "assessment_event",
            "assessment_evidence_snapshot",
            "assessment_evidence_attribution",
            "assessment_presentation",
            "assessment_answer_reveal_event",
            "answer_reveal_outcome",
            "attempt_submission",
            "attempt_event",
            "attempt_correction",
            "binding_change_event",
            "learning_sequence",
            "projection_outbox",
            "learner_projection_snapshot",
            "projection_archive",
            "learner_problem_memory_state",
            "learner_knowledge_mastery_state",
            "independent_correct_observation",
            "applied_attempt_record",
            "applied_correction_record",
            "applied_answer_reveal_record",
            "applied_tutor_answer_exposure_record",
            "presentation_projection_state",
            "review_plan",
            "active_review_plan_slot",
            "review_queue_item",
            "review_queue_knowledge_node",
            "review_queue_reason",
            "review_session",
            "review_session_revision",
            "review_session_advance_receipt",
            "review_log",
            "student_model_prediction",
            "prediction_outcome",
            "learner_chat_evidence",
        ),
        SmbkSelectionCategory.SESSIONS to listOf(
            "tutor_conversation",
            "tutor_message",
            "tutor_message_visual",
            "tutor_message_source_asset",
            "agent_pending_request",
            "tutor_answer_exposure",
            "tutor_answer_exposure_outcome",
        ),
        SmbkSelectionCategory.IMAGES to listOf(
            "canonical_source_asset",
        ),
    )

    /** 7 张恒随包表（知识层 + 内容安装状态）。 */
    val alwaysTables: List<String> = listOf(
        "knowledge_node",
        "knowledge_source",
        "knowledge_node_source_binding",
        "knowledge_node_relation",
        "knowledge_teaching_material",
        "knowledge_teaching_material_node_binding",
        "content_install_state",
    )

    /** 2 张派生索引表：恒裁，恢复完成后按科重建（本批裁定 ③）。 */
    val derivedTables: List<String> = listOf(
        "knowledge_search_feature",
        "knowledge_search_index_state",
    )

    /** 7 张按行筛选的表；具体谓词见两个 kind 映射、`*RowFilterTables` 与视觉引用闭包。 */
    val splitTables: List<String> = listOf(
        "model_task_event",
        "model_task",
        "model_task_operation",
        "figure_generation_attempt",
        "figure_generation_operation",
        "tutor_visual_snapshot",
        "tutor_visual_program",
    )

    /** FTS 外部内容表：不删行，删除结束后 `INSERT INTO …('rebuild')`。 */
    val rebuildTables: List<String> = listOf("library_search_fts")

    /** 90 张表（类别 74 + ALWAYS 7 + DERIVED 2 + SPLIT 7），也是 rowCounts 的键集合。 */
    val allTables: List<String> =
        categoryTables.values.flatten() + alwaysTables + derivedTables + splitTables

    /** `rowCounts` 必须覆盖的 90 个键（值为 0 也写）。 */
    val rowCountKeys: Set<String> = allTables.toSet()

    /**
     * 82 项裁剪顺序（子表先于父表）。= 90 − 7 ALWAYS − 1 `library_search_fts`；
     * 派生表在列（本批恒裁）。逐边拓扑合法性由测试按 `70.json` 的真实 FK 图校验。
     */
    val trimOrder: List<String> = listOf(
        "tutor_message_visual",
        "problem_revision_visual",
        "tutor_visual_snapshot",
        "tutor_visual_program",
        "tutor_message_source_asset",
        "tutor_message",
        "split_import_question",
        "split_import_job",
        "review_session_advance_receipt",
        "review_session_revision",
        "review_session",
        "review_queue_reason",
        "review_queue_knowledge_node",
        "review_queue_item",
        "review_log",
        "projection_outbox",
        "projection_archive",
        "problem_revision_source_asset",
        "problem_relation",
        "problem_organization_receipt",
        "problem_draft_source_asset",
        "problem_draft_edit_snapshot",
        "problem_draft_revision",
        "problem_draft_commit_receipt",
        "problem_classification_candidate",
        "problem_classification_binding",
        "presentation_projection_state",
        "prediction_outcome",
        "student_model_prediction",
        "model_task_event",
        "mistake_export_record",
        "llm_teaching_advisory",
        "library_search_outbox",
        "library_search_content",
        "learning_sequence",
        "learner_problem_memory_state",
        "learner_chat_evidence",
        "knowledge_search_index_state",
        "knowledge_search_feature",
        "knowledge_research_review_source",
        "knowledge_research_review_bundle",
        "knowledge_grounding_resolution",
        "knowledge_grounding_request",
        "independent_correct_observation",
        "learner_knowledge_mastery_state",
        "error_book_entry",
        "clean_redraw_task",
        "binding_change_event",
        "binding_audit_sample",
        "batch_import_page",
        "problem_draft",
        "figure_generation_attempt",
        "figure_generation_operation",
        "canonical_source_asset",
        "batch_import_job",
        "assessment_presentation",
        "assessment_evidence_attribution",
        "practice_unit_knowledge_binding",
        "assessment_event",
        "assessment_item_snapshot",
        "applied_tutor_answer_exposure_record",
        "tutor_answer_exposure_outcome",
        "tutor_answer_exposure",
        "model_task",
        "model_task_operation",
        "applied_correction_record",
        "attempt_correction",
        "applied_attempt_record",
        "attempt_event",
        "attempt_submission",
        "applied_answer_reveal_record",
        "learner_projection_snapshot",
        "answer_reveal_outcome",
        "assessment_answer_reveal_event",
        "assessment_evidence_snapshot",
        "practice_unit",
        "problem_revision",
        "problem",
        "agent_pending_request",
        "tutor_conversation",
        "active_review_plan_slot",
        "review_plan",
    )

    /** 类别闭包（5 条边）：外键子表被选中 ⇒ 父表类别必须被选中。 */
    val expectedClosure: List<SmbkClosureEdge> = listOf(
        SmbkClosureEdge(SmbkSelectionCategory.ISSUE_BOOK, SmbkSelectionCategory.IMAGES),
        SmbkClosureEdge(SmbkSelectionCategory.MASTERY, SmbkSelectionCategory.ISSUE_BOOK),
        SmbkClosureEdge(SmbkSelectionCategory.MASTERY, SmbkSelectionCategory.SESSIONS),
        SmbkClosureEdge(SmbkSelectionCategory.SESSIONS, SmbkSelectionCategory.ISSUE_BOOK),
        SmbkClosureEdge(SmbkSelectionCategory.SESSIONS, SmbkSelectionCategory.IMAGES),
    )

    /**
     * 21 条软引用（无外键的引用列）。v3 的检查口径是 **no-new-dangling**：
     * 只要求裁剪后悬空集合 ⊆ 裁剪前悬空集合；`check=informational` 不参与该断言，
     * `check=rebuild-consistency` 以 FTS rebuild 后的 docid 孤儿数为核对。
     *
     * 说明性字段（为什么删旧条目、为什么允许悬空）保留在设计 §9.4/§9.11，不在这里复制。
     */
    val softReferences: List<SmbkSoftReference> = listOf(
        SmbkSoftReference(
            table = "model_task",
            column = "subject_id",
            targetTable = "*by-task-kind*",
            kind = "by-kind",
            check = "no-new-dangling",
            strength = "weak-host-hint",
            checkDetail = "host existence only for non-terminal statuses; terminal rows may dangle",
        ),
        SmbkSoftReference(
            table = "model_task_operation",
            column = "subject_id",
            targetTable = "*by-task-kind*",
            kind = "by-kind",
            check = "no-new-dangling",
            strength = "weak-host-hint",
            checkDetail = "kind known only; host existence not required (ledger outlives host)",
        ),
        SmbkSoftReference(
            table = "tutor_conversation",
            column = "capture_draft_id",
            targetTable = "problem_draft",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_conversation",
            column = "anchor_practice_unit_id",
            targetTable = "practice_unit",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_conversation",
            column = "anchor_problem_revision_id",
            targetTable = "problem_revision",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_conversation",
            column = "anchor_id",
            targetTable = "*by-anchor-kind*",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_message",
            column = "bound_problem_id",
            targetTable = "problem",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_message",
            column = "bound_problem_revision_id",
            targetTable = "problem_revision",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "tutor_message_source_asset",
            column = "basis_revision_id",
            targetTable = "problem_revision",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "learner_chat_evidence",
            column = "conversation_id",
            targetTable = "tutor_conversation",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "binding_change_event",
            column = "practice_unit_id",
            targetTable = "practice_unit",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "student_model_prediction",
            column = "practice_unit_id",
            targetTable = "practice_unit",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "review_log",
            column = "card_id",
            targetTable = "practice_unit",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "clean_redraw_task",
            column = "subject_id",
            targetTable = "problem_draft",
            kind = "same-category",
            check = "no-new-dangling",
            strength = "weak-host-hint",
        ),
        SmbkSoftReference(
            table = "clean_redraw_task",
            column = "operation_id",
            targetTable = "figure_generation_operation",
            kind = "split-family",
            check = "no-new-dangling",
            strength = "strong",
        ),
        SmbkSoftReference(
            table = "figure_generation_operation",
            column = "source_id",
            targetTable = "*by-source-kind*",
            kind = "split-family",
            check = "informational",
            strength = "weak-host-hint",
        ),
        SmbkSoftReference(
            table = "library_search_fts",
            column = "docid",
            targetTable = "library_search_content",
            kind = "same-category",
            check = "rebuild-consistency",
            strength = "informational",
            virtual = true,
        ),
        SmbkSoftReference(
            table = "learner_chat_evidence",
            column = "knowledge_node_id",
            targetTable = "knowledge_node",
            kind = "always",
            check = "none",
            strength = "informational",
        ),
        SmbkSoftReference(
            table = "llm_teaching_advisory",
            column = "knowledge_node_id",
            targetTable = "knowledge_node",
            kind = "always",
            check = "none",
            strength = "informational",
        ),
        SmbkSoftReference(
            table = "student_model_prediction",
            column = "knowledge_node_id",
            targetTable = "knowledge_node",
            kind = "always",
            check = "none",
            strength = "informational",
        ),
        SmbkSoftReference(
            table = "tutor_visual_program",
            column = "basis_revision_id",
            targetTable = "problem_revision",
            kind = "cross-category",
            check = "no-new-dangling",
            strength = "strong",
        ),
    )

    /** 资产根（4 张根引用表）：选中对应类别时，其存活行指向的资产进入 R。 */
    val assetRoots: List<SmbkAssetRoot> = listOf(
        SmbkAssetRoot("problem_revision_source_asset", "source_asset_id", SmbkSelectionCategory.ISSUE_BOOK),
        SmbkAssetRoot("problem_draft_source_asset", "source_asset_id", SmbkSelectionCategory.ISSUE_BOOK),
        SmbkAssetRoot("problem_draft", "source_asset_id", SmbkSelectionCategory.ISSUE_BOOK),
        SmbkAssetRoot("tutor_message_source_asset", "source_asset_id", SmbkSelectionCategory.SESSIONS),
    )

    /** `model_task` 家族的三张表共用同一个 `task_kind` 谓词集合；删除顺序 event→task→operation。 */
    val modelTaskRowFilterTables: List<String> = listOf(
        "model_task_event",
        "model_task",
        "model_task_operation",
    )

    /** `figure_generation_attempt` 必须先于 `figure_generation_operation` 删除（RESTRICT）。 */
    val figureRowFilterTables: List<String> = listOf(
        "figure_generation_attempt",
        "figure_generation_operation",
    )

    /**
     * 两张视觉表的行级规则（批次 30 / D-01）：`tutor_visual_snapshot` 先于 `tutor_visual_program`
     * 删除（snapshot.program_id → program 的 RESTRICT）。保留集合由裁剪器的存活 link/题面引用
     * 与 `checkpoint_kind='INITIAL'` 基线共同决定（`BackupSnapshotTrimmer`）。
     */
    val visualRowFilterTables: List<String> = listOf(
        "tutor_visual_snapshot",
        "tutor_visual_program",
    )

    /** `task_kind` → 类别；未在映射内的 kind 在真子集必须 fail-closed。 */
    val modelTaskKindCategory: Map<String, SmbkSelectionCategory> = linkedMapOf(
        "CAPTURE_ASSESS" to SmbkSelectionCategory.ISSUE_BOOK,
        "CAPTURE_PARSE" to SmbkSelectionCategory.ISSUE_BOOK,
        "PROBLEM_CLASSIFY" to SmbkSelectionCategory.ISSUE_BOOK,
        "PROBLEM_RELATE" to SmbkSelectionCategory.ISSUE_BOOK,
        "IMAGE_PIPELINE_CLASSIFY" to SmbkSelectionCategory.ISSUE_BOOK,
        "TUTOR_PLAN" to SmbkSelectionCategory.SESSIONS,
        "TUTOR_RESPOND" to SmbkSelectionCategory.SESSIONS,
        "TUTOR_LOBBY" to SmbkSelectionCategory.SESSIONS,
        "TUTOR_EVALUATE" to SmbkSelectionCategory.SESSIONS,
        "LEARNING_SUMMARIZE" to SmbkSelectionCategory.SESSIONS,
        "REVIEW_RERANK" to SmbkSelectionCategory.MASTERY,
        "KNOWLEDGE_QUIZ" to SmbkSelectionCategory.MASTERY,
    )

    /** `figure_generation_operation.source_kind` → 类别；`source_id` 不反查宿主行。 */
    val figureOperationSourceKindCategory: Map<String, SmbkSelectionCategory> = linkedMapOf(
        "TUTOR_MESSAGE" to SmbkSelectionCategory.SESSIONS,
        "PROBLEM_REVISION" to SmbkSelectionCategory.ISSUE_BOOK,
        "CLEAN_REDRAW" to SmbkSelectionCategory.ISSUE_BOOK,
        "CAPTURE_PARSE" to SmbkSelectionCategory.ISSUE_BOOK,
    )

    private val tableBucketIndex: Map<String, SmbkTableBucket> = buildMap {
        categoryTables.forEach { (category, tables) ->
            tables.forEach { put(it, SmbkTableBucket.UserCategory(category)) }
        }
        alwaysTables.forEach { put(it, SmbkTableBucket.ALWAYS) }
        derivedTables.forEach { put(it, SmbkTableBucket.DERIVED) }
        splitTables.forEach { put(it, SmbkTableBucket.SPLIT) }
    }

    init {
        require(allTables.size == allTables.toSet().size) { "表被重复归类：$allTables" }
        require(allTables.size == 90) { "schema 70 应有 90 张表，实际 ${allTables.size}" }
        require(views.size == 2) { "schema 70 应有 2 个视图，实际 ${views.size}" }
        require(categoryTables[SmbkSelectionCategory.ISSUE_BOOK]?.size == 31) { "ISSUE_BOOK 应为 31 张表" }
        require(categoryTables[SmbkSelectionCategory.MASTERY]?.size == 35) { "MASTERY 应为 35 张表" }
        require(categoryTables[SmbkSelectionCategory.SESSIONS]?.size == 7) { "SESSIONS 应为 7 张表" }
        require(categoryTables[SmbkSelectionCategory.IMAGES]?.size == 1) { "IMAGES 应为 1 张表" }
        require(alwaysTables.size == 7) { "ALWAYS 应为 7 张表" }
        require(derivedTables.size == 2) { "DERIVED 应为 2 张表" }
        require(splitTables.size == 7) { "SPLIT 应为 7 张表" }
        require(trimOrder.size == 82) { "裁剪顺序应为 82 项，实际 ${trimOrder.size}" }
        require(trimOrder.size == trimOrder.toSet().size) { "裁剪顺序有重复项" }
        require(
            trimOrder.toSet() == allTables.toSet() - alwaysTables.toSet() - rebuildTables.toSet(),
        ) { "裁剪顺序必须恰好覆盖 90 表 − ALWAYS − FTS" }
        require(derivedTables.all { it in trimOrder }) { "派生表按本批裁定必须恒裁" }
        require(expectedClosure.size == 5) { "类别闭包应为 5 条边" }
        require(softReferences.size == 21) { "软引用应为 21 条" }
        require(
            splitTables.toSet() ==
                (modelTaskRowFilterTables + figureRowFilterTables + visualRowFilterTables).toSet(),
        ) { "split 表与 kind/视觉谓词表集合必须一致" }
        require(allowedMasks == setOf(9, 13, 15)) { "本批只允许 9/13/15" }
    }

    /** 表 → 归属桶；未知表返回 null（调用方对 `70.json` 漂移 fail-closed）。 */
    fun bucketOf(table: String): SmbkTableBucket? = tableBucketIndex[table]

    /** 用户类别表的类别；ALWAYS/DERIVED/SPLIT 或未知表返回 null。 */
    fun categoryOf(table: String): SmbkSelectionCategory? =
        (bucketOf(table) as? SmbkTableBucket.UserCategory)?.category

    /** mask 内的类别集合（按枚举顺序）；越界 mask 直接拒绝。 */
    fun categoriesOf(mask: Int): Set<SmbkSelectionCategory> {
        require(mask in 0..FULL_MASK) { "mask 超出 0..15：$mask" }
        return SmbkSelectionCategory.entries.filterTo(linkedSetOf()) { mask and it.bit != 0 }
    }

    /** 类别集合 → mask。 */
    fun maskOf(categories: Iterable<SmbkSelectionCategory>): Int =
        categories.fold(0) { mask, category -> mask or category.bit }

    /** 按 5 条闭包边传播到不动点（不静默补齐：调用方仍需自行拒绝非法 mask）。 */
    fun closureOf(mask: Int): Int {
        require(mask in 0..FULL_MASK) { "mask 超出 0..15：$mask" }
        var current = mask
        var changed = true
        while (changed) {
            changed = false
            for (edge in expectedClosure) {
                if (current and edge.child.bit != 0 && current and edge.parent.bit == 0) {
                    current = current or edge.parent.bit
                    changed = true
                }
            }
        }
        return current
    }

    /** mask 是否自洽（闭合后不变）。0/8 自洽但被产品裁定拒绝，见 [isCreatableMask]。 */
    fun isClosureConsistent(mask: Int): Boolean = closureOf(mask) == mask

    /** 本批可创建的选择：只有 9/13/15。全选也只裁派生索引，用户类别一行不动。 */
    fun isCreatableMask(mask: Int): Boolean = mask in allowedMasks

    /** 仓储/UI 共用的拒绝点：非法 mask 直接抛错，不补齐、不缩水。 */
    fun requireCreatableMask(mask: Int): Int {
        require(mask in allowedMasks) {
            "非法备份选择 mask=$mask；本批只允许 ${allowedMasks.joinToString("/")}（0/8 与不自洽组合一律拒绝）"
        }
        return mask
    }

    /** 真子集要删除的 `task_kind` 集合；全选（15）为空。映射外 kind 不在此列，由裁剪器 fail-closed。 */
    fun deletedModelTaskKinds(mask: Int): Set<String> {
        require(mask in 0..FULL_MASK) { "mask 超出 0..15：$mask" }
        return modelTaskKindCategory.filterValues { mask and it.bit == 0 }.keys
    }

    /** 真子集要作为 O_seed 保留的 `source_kind` 集合；全选（15）为全部 4 个。 */
    fun figureSourceKindsToKeep(mask: Int): Set<String> {
        require(mask in 0..FULL_MASK) { "mask 超出 0..15：$mask" }
        return figureOperationSourceKindCategory.filterValues { mask and it.bit != 0 }.keys
    }

    /**
     * §9.7 的 v3 digest：
     * ```
     * sha256("smbk-selection-v3\n" + "closureVersion=3\n"
     *      + "categories=" + sorted.joinToString(",") + "\n"
     *      + "assetPolicy=" + assetPolicy + "\n"
     *      + rowCounts.keys.sorted().joinToString("") { "$it=${rowCounts[it]}\n" })
     * ```
     * [rowCounts] 必须恰好覆盖 90 个键（漏键/多键会抛出，避免摘要不可比）。
     */
    fun closureDigest(
        categories: Collection<SmbkSelectionCategory>,
        assetPolicy: String,
        rowCounts: Map<String, Long>,
    ): String {
        require(rowCounts.keys == rowCountKeys) {
            val missing = (rowCountKeys - rowCounts.keys).sorted()
            val extra = (rowCounts.keys - rowCountKeys).sorted()
            "rowCounts 必须覆盖 90 张表：missing=$missing extra=$extra"
        }
        val categoryNames = categories.map { it.name }.distinct().sorted()
        val payload = buildString {
            append(CLOSURE_DIGEST_PREFIX).append('\n')
            append("closureVersion=").append(CLOSURE_VERSION).append('\n')
            append("categories=").append(categoryNames.joinToString(",")).append('\n')
            append("assetPolicy=").append(assetPolicy).append('\n')
            rowCounts.keys.sorted().forEach { table ->
                append(table).append('=').append(rowCounts.getValue(table)).append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

/** 四项用户类别；bit 与机器块一致，`FULL_MASK = 15`。 */
enum class SmbkSelectionCategory(val bit: Int, val label: String) {
    ISSUE_BOOK(1, "错题本"),
    MASTERY(2, "掌握库"),
    SESSIONS(4, "会话"),
    IMAGES(8, "图片"),
}

/** 类别闭包边：选中 [child] ⇒ 必须选中 [parent]。 */
data class SmbkClosureEdge(
    val child: SmbkSelectionCategory,
    val parent: SmbkSelectionCategory,
)

/** 一条软引用（无外键的引用列）。 */
data class SmbkSoftReference(
    val table: String,
    val column: String,
    val targetTable: String,
    val kind: String,
    val check: String,
    val strength: String,
    val checkDetail: String? = null,
    val virtual: Boolean = false,
)

/** 一张资产根引用表：类别被选中时，其存活行指向的资产进入 R。 */
data class SmbkAssetRoot(
    val table: String,
    val column: String,
    val category: SmbkSelectionCategory,
)

/** 表的归属桶；`UserCategory` 携带具体类别。 */
sealed interface SmbkTableBucket {
    data class UserCategory(val category: SmbkSelectionCategory) : SmbkTableBucket

    data object ALWAYS : SmbkTableBucket

    data object DERIVED : SmbkTableBucket

    data object SPLIT : SmbkTableBucket
}
