package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeSourceBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchFeatureEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSourceEntity
import com.tingyun.smartmistakebook.core.database.entity.PracticeUnitKnowledgeBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationBindingEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemClassificationCandidateEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemOrganizationReceiptEntity
import com.tingyun.smartmistakebook.core.database.entity.ProblemRelationEntity
import kotlinx.coroutines.flow.Flow

internal data class ReviewedKnowledgeCoverageRow(
    val subject: String,
    val topicCount: Int,
    val atomicKnowledgeCount: Int,
    val reviewedSourceCount: Int,
    val latestReviewedAtEpochMillis: Long,
)

/** 召回候选行：节点 + 词面分（`COUNT(DISTINCT feature.search_feature)`）。 */
internal data class KnowledgeRecallCandidateRow(
    @Embedded val node: KnowledgeNodeEntity,
    val matchCount: Int,
)

/**
 * 3E 全库扫描行：活跃知识点 → 在册绑定题（CHAR(31) 连接）+ 可出题材料组。
 *
 * `practiceUnitIds` 用 CHAR(31)（US，Unit Separator）连接而非默认逗号：id 是本系统自己生成的
 * 不透明串（`practice-<sha256 前 16 字节 hex>` 等），分隔符一旦与 id 字符集重叠就会静默切错，
 * 而 CHAR(31) 不在任何 id 的取值范围内——与 `ProblemDao.observeActiveMistakes` 的
 * `GROUP_CONCAT(..., CHAR(31))` 同一约定。
 */
internal data class FullLibraryScanRow(
    @ColumnInfo(name = "knowledge_node_id") val knowledgeNodeId: String,
    val subject: String,
    @ColumnInfo(name = "practice_unit_ids") val practiceUnitIds: String,
    @ColumnInfo(name = "material_group_id") val materialGroupId: String?,
)

@Dao
internal interface ProblemOrganizationDao {
    @Query(
        """
        SELECT
            node.subject AS subject,
            COUNT(DISTINCT CASE
                WHEN node.granularity = 'TOPIC' AND node.verification_status = 'CURATED'
                THEN node.knowledge_node_id
            END) AS topicCount,
            COUNT(DISTINCT CASE
                WHEN node.granularity = 'ATOMIC' AND node.verification_status = 'SOURCE_GROUNDED'
                THEN node.knowledge_node_id
            END) AS atomicKnowledgeCount,
            COUNT(DISTINCT provenance.source_id) AS reviewedSourceCount,
            MAX(provenance.reviewed_at_epoch_millis) AS latestReviewedAtEpochMillis
        FROM knowledge_node AS node
        INNER JOIN knowledge_node_source_binding AS provenance
            ON provenance.knowledge_node_id = node.knowledge_node_id
           AND provenance.reviewed_at_epoch_millis IS NOT NULL
        WHERE node.verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
          AND node.status != 'RETIRED'
        GROUP BY node.subject
        ORDER BY node.subject ASC
        """,
    )
    fun observeReviewedKnowledgeCoverage(): Flow<List<ReviewedKnowledgeCoverageRow>>

    @Query(
        """
        SELECT COUNT(*) FROM practice_unit
        WHERE practice_unit_id = :practiceUnitId
          AND problem_id = :problemId
          AND problem_revision_id = :problemRevisionId
        """,
    )
    suspend fun exactPracticeUnitCount(
        practiceUnitId: String,
        problemId: String,
        problemRevisionId: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKnowledgeNodes(nodes: List<KnowledgeNodeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKnowledgeSources(sources: List<KnowledgeSourceEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKnowledgeNodeSourceBindings(
        bindings: List<KnowledgeNodeSourceBindingEntity>,
    ): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKnowledgeSearchFeatures(features: List<KnowledgeSearchFeatureEntity>): List<Long>

    @Transaction
    suspend fun importKnowledgeBase(
        sources: List<KnowledgeSourceEntity>,
        nodes: List<KnowledgeNodeEntity>,
        bindings: List<KnowledgeNodeSourceBindingEntity>,
        searchFeatures: List<KnowledgeSearchFeatureEntity>,
    ) {
        insertKnowledgeSources(sources)
        insertKnowledgeNodes(nodes)
        insertKnowledgeNodeSourceBindings(bindings)
        insertKnowledgeSearchFeatures(searchFeatures)
    }

    @Query("SELECT * FROM knowledge_node WHERE knowledge_node_id = :id")
    suspend fun readKnowledgeNode(id: String): KnowledgeNodeEntity?

    /**
     * Verification status is the one monotonic attribute of an otherwise
     * immutable knowledge node: once the student confirms the same label, a
     * model candidate becomes user authority and must be reusable as a
     * classification candidate (audit 2026-09-09). Never downgrades.
     */
    @Query(
        "UPDATE knowledge_node SET verification_status = 'USER_CONFIRMED' " +
            "WHERE knowledge_node_id = :id AND verification_status = 'MODEL_CANDIDATE'",
    )
    suspend fun promoteKnowledgeNodeToUserConfirmed(id: String): Int

    @Query(
        """
        SELECT * FROM knowledge_node
        WHERE subject = :subject
          AND status != 'RETIRED'
        ORDER BY
            CASE verification_status
                WHEN 'CURATED' THEN 0
                WHEN 'SOURCE_GROUNDED' THEN 1
                ELSE 2
            END,
            CASE granularity WHEN 'ATOMIC' THEN 0 ELSE 1 END,
            canonical_name ASC,
            knowledge_node_id ASC
        LIMIT :limit
        """,
    )
    suspend fun readSubjectKnowledgeNodes(subject: String, limit: Int): List<KnowledgeNodeEntity>

    /**
     * 库里出现过的知识点科目（去重、稳定排序）。
     *
     * 6C-2 T5 的恢复后重建用它兜底：待重建标记通常已经带了恢复时读到的科目列表，
     * 只有标记为空（旧标记、或恢复时知识点表为空而之后又被导入）才回落到这条查询。
     * 不按 `verification_status` 过滤——整科重建自身会决定哪些节点进索引与分母，
     * 这里只回答"库里有哪些科"。
     */
    @Query("SELECT DISTINCT subject FROM knowledge_node ORDER BY subject ASC")
    suspend fun readKnowledgeSubjects(): List<String>

    // ---- "当前工作"的读路径：一律排除已退役内容 ----
    //
    // 退役的节点从新工作里消失（不再被召回、不再进整理提示词、不再进新复习计划），
    // 但**历史解释路径不过滤**——学生错题上的标签、掌握度列表仍要能解释旧记录。
    // 这条分界是本文件里几个查询带 status 过滤、而 readKnowledgeNodesByIds 不带的原因。

    @Query(
        """
        SELECT * FROM knowledge_node
        WHERE subject = :subject
          AND verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
          AND status != 'RETIRED'
        ORDER BY canonical_name ASC
        LIMIT :limit
        """,
    )
    suspend fun readSubjectKnowledgeRecallCandidates(
        subject: String,
        limit: Int,
    ): List<KnowledgeNodeEntity>

    /**
     * 召回候选 + **该节点的词面分**（`COUNT(DISTINCT feature.search_feature)`）。
     *
     * 词面分随行返回而不是另开一条查询：Stage-3 的融合口径要求"词面腿在本查询候选域内
     * 做 min-max"（spec §2.4），分数与排序出自**同一个** `GROUP BY`/`ORDER BY`；另写一条
     * 同 WHERE 的查询就会成为第二个漂移源（见 `countReviewedKnowledgeNodesBySubject` 的同类注释）。
     * 排序键与旧签名逐条相同（`COUNT(DESC) → ATOMIC 优先 → canonical_name → node_id`）。
     */
    @Query(
        """
        SELECT node.*, COUNT(DISTINCT feature.search_feature) AS matchCount
        FROM knowledge_search_feature AS feature
        INNER JOIN knowledge_node AS node
          ON node.knowledge_node_id = feature.knowledge_node_id
        WHERE feature.subject = :subject
          AND feature.search_feature IN (:searchFeatures)
          AND node.verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
          AND node.status != 'RETIRED'
        GROUP BY node.knowledge_node_id
        ORDER BY
            COUNT(DISTINCT feature.search_feature) DESC,
            CASE node.granularity WHEN 'ATOMIC' THEN 0 ELSE 1 END,
            node.canonical_name ASC,
            node.knowledge_node_id ASC
        LIMIT :limit
        """,
    )
    suspend fun searchSubjectKnowledgeRecallCandidates(
        subject: String,
        searchFeatures: Set<String>,
        limit: Int,
    ): List<KnowledgeRecallCandidateRow>

    /**
     * 检索索引自愈的分母。过滤条件**必须与召回查询逐条一致**（同样的
     * `verification_status`、同样排 `RETIRED`）——否则两个计数会永久漂移，
     * 自愈要么空转要么反复补建。
     */
    @Query(
        """
        SELECT COUNT(*) FROM knowledge_node
        WHERE subject = :subject
          AND verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
          AND status != 'RETIRED'
        """,
    )
    suspend fun countReviewedKnowledgeNodesBySubject(subject: String): Int

    /**
     * 已索引的节点数（自愈的分子）。
     *
     * **必须与分母同筛 `verification_status`（KD-51 补记）**：分母
     * [countReviewedKnowledgeNodesBySubject] 只数 CURATED / SOURCE_GROUNDED / USER_CONFIRMED；
     * 分子若把未审校节点的特征行也算进来，I>R 会让「已索引 ≥ 已审校」的门误判完整——
     * 删块之后取消的重建会被当成跑完，读路径静默少召回且不再自愈。
     *
     * RETIRED 仍不必单独排：退役时 `deleteSearchFeaturesForNodes` 会删掉该节点的特征行
     * （分母也排它）。若哪天真要改这里，必须同时改退役路径——两处必须一起动。
     */
    @Query(
        """
        SELECT COUNT(DISTINCT feature.knowledge_node_id)
        FROM knowledge_search_feature AS feature
        INNER JOIN knowledge_node AS node
          ON node.knowledge_node_id = feature.knowledge_node_id
        WHERE feature.subject = :subject
          AND node.verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
        """,
    )
    suspend fun countIndexedKnowledgeNodesBySubject(subject: String): Int

    /**
     * 这些 id 里**仍然有效**的那些（未退役）。
     *
     * "当前工作"的读路径用它把集合收一道。**刻意不把它塞进 [readKnowledgeNodesByIds]**：
     * 那是历史解释路径（错题标签、掌握度列表要能解释旧记录），在那过滤会让学生的旧记录凭空消失。
     * 某 id 该不该在集合里，由**构造集合的那一方**决定，而不是由名字解析器决定。
     */
    @Query(
        "SELECT knowledge_node_id FROM knowledge_node " +
            "WHERE knowledge_node_id IN (:ids) AND status != 'RETIRED'",
    )
    suspend fun readActiveKnowledgeNodeIds(ids: Set<String>): List<String>

    /**
     * 3E 全库扫描的候选源：**活跃**知识点 → 它的**在册绑定题** + 可出题材料组。
     *
     * 三条判定逐条写死在这里（迁移/换库时只有这一处要改）：
     * - **活跃**：`node.status != 'RETIRED'`（退役节点不进新工作，与
     *   [readActiveKnowledgeNodeIds] 同一口径）。
     * - **在册绑定题**：`practice_unit_knowledge_binding` 的题必须挂在 `error_book_entry`
     *   （`status = 'ACTIVE'`）上——"有绑定题"指的是学生错题本里现在还在的题；同题多版本绑定
     *   行先 `SELECT DISTINCT (node, unit)` 再聚合，去重后一个节点一条。
     *   口径与 `MasteryOverviewDao.readSubjectMastery` 的 `bound_question_count` 一致。
     *   **刻意不比 revision**：扫描是"发现薄弱点"的面，不是证据归属面；归属面"当前绑定"的
     *   revision 口径在 [readCurrentKnowledgeBindingsForPracticeUnit]，两者不同源不互相覆盖。
     * - **可出题材料**：非退役材料（`material.status != 'RETIRED'`）且**材料科目 = 节点科目**
     *   （派发侧 `referencesFor(subject = entry.subject, ...)` 就是按科目取材料的，科目不符等于
     *   排了却出不了题）；组取 `MIN(material_id)`，与
     *   `StudyReviewPlannerService.quizAbleScopeNodes` 的"多份材料取字典序最小者"同义。
     *   没有满足条件的材料 → `material_group_id` 为 NULL（**不在 SQL 里过滤掉这类节点**：
     *   范围规则的单源在 core:domain 的 `fullLibraryScanCandidate`，读口只给事实）。
     *
     * 为什么不是"逐节点查一次"或复用 [readKnowledgeTeachingMaterialsForNodes]：那条读口按
     * 节点集取材料（节点集上限 256、材料条数上限 `MAX_TEACHING_MATERIAL_CANDIDATES`），
     * 全库规模下会先撞上限再截断——被截断的节点会被**误判成"没有材料"**（该排的没排），
     * 而逐节点往返就是 N+1。这里一条查询取全，覆盖判定不做截断。
     *
     * `practice_unit_ids` 套 `COALESCE(..., '')`：INNER JOIN 已保证每组至少一行（实际非空），
     * 显式兜底让"非空 String"这个 Kotlin 契约在 Room 的列可空性分析下也成立；读侧对空串本来就
     * 按"没有绑定题"处理（`fullLibraryScanCandidate` 把空列表判成不在扫描范围）。
     */
    @Query(
        """
        SELECT
            node.knowledge_node_id AS knowledge_node_id,
            node.subject AS subject,
            bound_units.practice_unit_ids AS practice_unit_ids,
            material_group.material_group_id AS material_group_id
        FROM knowledge_node AS node
        INNER JOIN (
            SELECT
                bound.knowledge_node_id AS knowledge_node_id,
                COALESCE(
                    GROUP_CONCAT(bound.practice_unit_id, CHAR(31)),
                    ''
                ) AS practice_unit_ids
            FROM (
                SELECT DISTINCT
                    binding.knowledge_node_id AS knowledge_node_id,
                    binding.practice_unit_id AS practice_unit_id
                FROM practice_unit_knowledge_binding AS binding
                INNER JOIN error_book_entry AS entry
                    ON entry.practice_unit_id = binding.practice_unit_id
                   AND entry.status = 'ACTIVE'
            ) AS bound
            GROUP BY bound.knowledge_node_id
        ) AS bound_units
            ON bound_units.knowledge_node_id = node.knowledge_node_id
        LEFT JOIN (
            SELECT
                material_binding.knowledge_node_id AS knowledge_node_id,
                MIN(material.material_id) AS material_group_id
            FROM knowledge_teaching_material_node_binding AS material_binding
            INNER JOIN knowledge_teaching_material AS material
                ON material.material_id = material_binding.material_id
               AND material.status != 'RETIRED'
            INNER JOIN knowledge_node AS material_node
                ON material_node.knowledge_node_id = material_binding.knowledge_node_id
               AND material_node.subject = material.subject
            GROUP BY material_binding.knowledge_node_id
        ) AS material_group
            ON material_group.knowledge_node_id = node.knowledge_node_id
        WHERE node.status != 'RETIRED'
        ORDER BY node.knowledge_node_id
        """,
    )
    suspend fun readFullLibraryScanNodes(): List<FullLibraryScanRow>

    /**
     * 按 id 读节点。**刻意不过滤 RETIRED**：这是历史解释路径（错题详情、掌握度列表要能
     * 解释旧记录），过滤会让"学生错题上的知识点标签、他的掌握度条目"凭空消失。
     * 新的工作路径请用上面带过滤的那几个查询。
     */
    @Query("SELECT * FROM knowledge_node WHERE knowledge_node_id IN (:ids)")
    suspend fun readKnowledgeNodesByIds(ids: Set<String>): List<KnowledgeNodeEntity>

    // ---- 内容调和用的读/写面（见 BundledKnowledgeBaseInstaller）----

    /**
     * 某个内容包登记在库里的**全部**节点——调和用它算"包里有、库里没有的"和
     * "库里有、包里没有的（该退役了）"。
     *
     * 不能按 subject 读：2020 样例包与 2025 四科包在 MATH 等科目上重叠，按科读会把
     * 另一个包的节点混进来，于是它们会被误判成"包里没有"而遭退役。
     */
    @Query("SELECT * FROM knowledge_node WHERE taxonomy_version = :taxonomyVersion")
    suspend fun readKnowledgeNodesByTaxonomy(taxonomyVersion: String): List<KnowledgeNodeEntity>

    /**
     * 按主键 upsert 节点内容。**必须是 upsert 而不是 REPLACE**：REPLACE 会先删后插，
     * 而 8 张表以 RESTRICT 引用 `knowledge_node`，删除会被外键直接拒绝。
     */
    @Upsert
    suspend fun upsertKnowledgeNodes(nodes: List<KnowledgeNodeEntity>)

    /**
     * 退役一个节点。**永不物删**——学生错题绑定/掌握度/复习队列还引用着它。
     * `status != 'RETIRED'` 的条件让重复调用是空操作（幂等）。
     */
    @Query(
        "UPDATE knowledge_node SET status = 'RETIRED', superseded_by = :supersededBy " +
            "WHERE knowledge_node_id = :id AND status != 'RETIRED'",
    )
    suspend fun retireKnowledgeNode(id: String, supersededBy: String?): Int

    /**
     * 删掉这些节点的检索特征。退役必须同时做这一步：自愈逻辑
     * （`ensureKnowledgeSearchIndex`）靠"已索引数 ≥ 已审校数"判断是否需要补建，
     * 若退役节点仍留着特征行，两个计数会永久漂移，自愈要么空转要么反复补建。
     */
    @Query("DELETE FROM knowledge_search_feature WHERE knowledge_node_id IN (:ids)")
    suspend fun deleteSearchFeaturesForNodes(ids: Set<String>)

    /**
     * 该科**已有特征行的节点 id**（整科重建的分块删除用，KD-51）。
     *
     * 重建必须整科换血——旧规则建的行哪怕节点一个没变也是残缺的（v1 截断了 62% 节点的
     * 大半别名）；但**不能**再用一条 `DELETE ... WHERE subject = ?` 删完：那一条语句会把
     * room3 的唯一写连接占住整段删除（全库 623,774 行量级，同进程其它写只能排队）。
     * 这里先读 id 集，再按 [deleteSearchFeaturesForNodes] 分块删。两段在同一**读**快照内
     * 读出，且 `knowledge_node_id` 非空（外键）——所以 id 集覆盖该科**全部**特征行，
     * 分块删掉的行集与整科 DELETE 逐行相同（等价性由
     * `KnowledgeSearchIndexRebuildChunkingInstrumentedTest` 的行集比对钉住）。
     *
     * 前提：一个节点 id 的特征行只属于一个 subject（[KnowledgeNodeSeedRecord.toSearchFeatures]
     * 把节点的 subject 原样写进特征行）——所以按 id 删不会跨科多删。哪天节点能跨科改属，
     * 这条前提就破了，须改回按科删。
     *
     * 不按"召回候选"读 id：非候选（已退役、未审校）节点的残留行同样在整科换血的范围内。
     */
    @Query("SELECT DISTINCT knowledge_node_id FROM knowledge_search_feature WHERE subject = :subject")
    suspend fun readIndexedKnowledgeNodeIdsForSubject(subject: String): List<String>

    @Upsert
    suspend fun upsertKnowledgeSources(sources: List<KnowledgeSourceEntity>)

    /**
     * 按复合主键删一条节点—来源绑定。绑定是纯内容（没有学生数据引用它），
     * 所以这里可以真删；与节点/材料的"退役而非物删"不同。
     */
    @Query(
        "DELETE FROM knowledge_node_source_binding " +
            "WHERE knowledge_node_id = :nodeId AND source_id = :sourceId " +
            "AND source_locator = :sourceLocator",
    )
    suspend fun deleteKnowledgeNodeSourceBinding(
        nodeId: String,
        sourceId: String,
        sourceLocator: String,
    )

    @Query("DELETE FROM knowledge_node_source_binding WHERE knowledge_node_id IN (:nodeIds)")
    suspend fun deleteKnowledgeNodeSourceBindingsForNodes(nodeIds: Set<String>)

    /** 调和用：节点—来源绑定是纯内容（无学生数据引用），整体替换即可。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importKnowledgeNodeSourceBindings(
        bindings: List<KnowledgeNodeSourceBindingEntity>,
    )

    @Query("SELECT * FROM knowledge_source WHERE source_id IN (:ids)")
    suspend fun readKnowledgeSourcesByIds(ids: Set<String>): List<KnowledgeSourceEntity>

    /**
     * 已退役且指定了取代目标的节点：`退役 id -> 存活 id`。
     *
     * 投影用它把历史证据算到存活节点上（"合并并入"）。链式合并（`X→Y` 之后 `Y→Z`）由读取侧
     * 做传递闭包——这里只给原始边，不折叠。
     */
    @Query(
        "SELECT knowledge_node_id AS retiredId, superseded_by AS successorId " +
            "FROM knowledge_node WHERE superseded_by IS NOT NULL",
    )
    suspend fun readKnowledgeNodeSuccessors(): List<KnowledgeNodeSuccessorRow>

    /**
     * S20：`knowledge_node` 表的**失效探针**（Room 失效是表级的；返回值本身无意义）。
     *
     * 任何经 Room 的节点写入（合并退役 / 导入 / 确认补写）都会让它发射一次，投影的后继映射
     * 缓存据此失效（见 `KnowledgeNodeSuccessorsCache`）。用 `LIMIT 1` 而不是计数：探针不该在
     * 数万节点上做全表扫描，主键命中即返回。
     */
    @Query("SELECT knowledge_node_id FROM knowledge_node LIMIT 1")
    fun observeKnowledgeNodeChangeSignal(): Flow<String?>

    @Query(
        "SELECT * FROM knowledge_node_source_binding WHERE knowledge_node_id IN (:knowledgeNodeIds)",
    )
    suspend fun readKnowledgeNodeSourceBindings(
        knowledgeNodeIds: Set<String>,
    ): List<KnowledgeNodeSourceBindingEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKnowledgeBindings(
        bindings: List<PracticeUnitKnowledgeBindingEntity>,
    ): List<Long>

    /**
     * 把命令携带的绑定刷新到本次确认时间（KF-32 的"当前绑定集合"判定依赖它）。
     *
     * 绑定 id 是内容寻址（`practiceUnitId|nodeId|revisionId|taxonomy`），**不含时间**——同一份
     * 整理被再次确认（新 commandId、新 acceptedAt）时 `insertKnowledgeBindings` 的 IGNORE 会让
     * 行保留**旧** acceptedAt。而"当前绑定集合" = 最近一次确认那一批（`accepted_at == MAX(receipt
     * .accepted_at)`）：旧行不刷新就会在重确认后掉出当前集合——证据归属会退到 pseudo 桶，
     * 界面"当前知识点"也会空掉。
     *
     * 只刷命令自己携带的 id（≤24，命令上界内，无需分块）：被证据引用而保留的旧绑定不在其中，
     * 它的旧 acceptedAt 正是"它不是当前绑定"的依据。
     */
    @Query(
        """
        UPDATE practice_unit_knowledge_binding
        SET accepted_at_epoch_millis = :acceptedAtEpochMillis
        WHERE binding_id IN (:bindingIds)
        """,
    )
    suspend fun touchKnowledgeBindings(
        bindingIds: List<String>,
        acceptedAtEpochMillis: Long,
    )

    @Query("SELECT * FROM practice_unit_knowledge_binding WHERE binding_id = :id")
    suspend fun readKnowledgeBinding(id: String): PracticeUnitKnowledgeBindingEntity?

    @Query(
        """
        SELECT * FROM practice_unit_knowledge_binding
        WHERE practice_unit_id = :practiceUnitId
          AND knowledge_node_id = :knowledgeNodeId
          AND basis_revision_id = :problemRevisionId
          AND taxonomy_version = :taxonomyVersion
        LIMIT 1
        """,
    )
    suspend fun readKnowledgeBindingByIdentity(
        practiceUnitId: String,
        knowledgeNodeId: String,
        problemRevisionId: String,
        taxonomyVersion: String,
    ): PracticeUnitKnowledgeBindingEntity?

    @Query(
        """
        SELECT * FROM practice_unit_knowledge_binding
        WHERE practice_unit_id = :practiceUnitId
        ORDER BY accepted_at_epoch_millis, binding_id
        """,
    )
    suspend fun readKnowledgeBindingsForPracticeUnit(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingEntity>

    /**
     * 一道题的**当前**绑定集合（KF-32 重派生与改绑判定共用）。
     *
     * 规则（与 `observeCurrentKnowledgeNodeIds` 的"当前目录"同一口径）：
     * - 该题**从未被确认过**（`problem_organization_receipt` 无行）→ 全部绑定都算当前
     *   （M1 时代的库内播种/物化绑定没有回执可依据）；
     * - 否则只认**最近一次确认那一批**——`accepted_at_epoch_millis == MAX(receipt.accepted_at)`
     *   （同一命令的所有绑定共享同一个 acceptedAt，见 `validateCommand` 的约束）。
     *
     * 为什么不是"表里的全部行"：`deleteUnreferencedKnowledgeBindings` 会**保留**被证据归因引用的
     * 旧绑定（`assessment_evidence_attribution` 对绑定表是 RESTRICT，物删会直接违反外键；
     * 归因行又是不可改写的历史事实）。于是改绑后表里同时有"新绑定"和"被保留的旧绑定"——
     * 旧绑定是审计遗迹，不是当前目录。把它当当前绑定，重放就会把历史证据同时挂到新旧两个
     * 节点上（旧节点永远清不掉），增量新写的证据也会继续喂旧节点。
     *
     * 与 `observeCurrentKnowledgeNodeIds` 的唯一差别：**不要求匹配 KNOWLEDGE 分类**。那条查询
     * 服务界面"可见 topic"，这里服务证据归属——命令可以只绑 grounded 原子节点
     * （`boundKnowledgeNodeIds.intersect(visibleTopicNodeIds).isEmpty()` 的合法形态），
     * 原子绑定本来就没有分类行，按分类过滤会把它们整批丢掉。
     *
     * **存量库（v58 及以前）的已知缺口（登记不修，见 version ledger §3.13）**：v58 的 `confirm`
     * 没有 touch——重确认同一份绑定身份时 IGNORE 保留旧 `accepted_at`，于是"有回执、但没有任何
     * 行落在 MAX(receipt.accepted_at)"的单元在本口径下读到**空集**（无回退分支）。升级到 v59 后、
     * 该题下一次确认之前：新写证据与重放归属都退到 pseudo 桶（K1 批 2 起重放与写路径同规则；
     * 此前重放会退回写时快照）——无数据损坏，下次确认（touch 落地）即自愈；迁移**不回填**
     * （应用未发布，真实存量仅开发库）。
     */
    @Query(
        """
        SELECT * FROM practice_unit_knowledge_binding
        WHERE practice_unit_id = :practiceUnitId
          AND (
              NOT EXISTS (
                  SELECT 1 FROM problem_organization_receipt AS receipt
                  WHERE receipt.practice_unit_id =
                      practice_unit_knowledge_binding.practice_unit_id
                    AND receipt.problem_revision_id =
                        practice_unit_knowledge_binding.basis_revision_id
              )
              OR accepted_at_epoch_millis = (
                  SELECT MAX(receipt.accepted_at_epoch_millis)
                  FROM problem_organization_receipt AS receipt
                  WHERE receipt.practice_unit_id =
                      practice_unit_knowledge_binding.practice_unit_id
                    AND receipt.problem_revision_id =
                        practice_unit_knowledge_binding.basis_revision_id
              )
          )
        ORDER BY accepted_at_epoch_millis, binding_id
        """,
    )
    suspend fun readCurrentKnowledgeBindingsForPracticeUnit(
        practiceUnitId: String,
    ): List<PracticeUnitKnowledgeBindingEntity>

    @Query(
        """
        SELECT DISTINCT binding.knowledge_node_id
        FROM practice_unit_knowledge_binding AS binding
        INNER JOIN practice_unit AS unit
          ON unit.practice_unit_id = binding.practice_unit_id
         AND unit.problem_revision_id = binding.basis_revision_id
        INNER JOIN knowledge_node AS node
          ON node.knowledge_node_id = binding.knowledge_node_id
        WHERE unit.problem_id = :problemId
          AND unit.problem_revision_id = :problemRevisionId
          AND (
              NOT EXISTS (
                  SELECT 1
                  FROM problem_organization_receipt AS receipt
                  WHERE receipt.problem_id = unit.problem_id
                    AND receipt.problem_revision_id = unit.problem_revision_id
              )
              OR (
                  binding.accepted_at_epoch_millis = (
                      SELECT MAX(receipt.accepted_at_epoch_millis)
                      FROM problem_organization_receipt AS receipt
                      WHERE receipt.problem_id = unit.problem_id
                        AND receipt.problem_revision_id = unit.problem_revision_id
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM problem_classification_binding AS classification
                      WHERE classification.problem_id = unit.problem_id
                        AND classification.basis_revision_id = unit.problem_revision_id
                        AND classification.dimension = 'KNOWLEDGE'
                        AND classification.taxonomy_version = binding.taxonomy_version
                        AND classification.accepted_at_epoch_millis =
                            binding.accepted_at_epoch_millis
                  )
              )
          )
        ORDER BY binding.knowledge_node_id
        """,
    )
    fun observeCurrentKnowledgeNodeIds(
        problemId: String,
        problemRevisionId: String,
    ): Flow<List<String>>

    @Query(
        """
        DELETE FROM practice_unit_knowledge_binding
        WHERE practice_unit_id = :practiceUnitId
          AND basis_revision_id = :problemRevisionId
          AND NOT EXISTS (
              SELECT 1 FROM assessment_evidence_attribution AS attribution
              WHERE attribution.binding_id = practice_unit_knowledge_binding.binding_id
                AND attribution.practice_unit_id = practice_unit_knowledge_binding.practice_unit_id
                AND attribution.knowledge_node_id = practice_unit_knowledge_binding.knowledge_node_id
                AND attribution.basis_revision_id = practice_unit_knowledge_binding.basis_revision_id
                AND attribution.taxonomy_version = practice_unit_knowledge_binding.taxonomy_version
          )
        """,
    )
    suspend fun deleteUnreferencedKnowledgeBindings(
        practiceUnitId: String,
        problemRevisionId: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClassificationBindings(
        bindings: List<ProblemClassificationBindingEntity>,
    ): List<Long>

    @Query("SELECT * FROM problem_classification_binding WHERE binding_id = :id")
    suspend fun readClassificationBinding(id: String): ProblemClassificationBindingEntity?

    /**
     * K5-B（schema 65）：本轮模型候选集（含被拒候选）。`IGNORE` = 同一轮重放不重写
     * （候选 id 按轮内容寻址）；-1 的行由调用方与既有行逐列比对，差异判载荷冲突。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClassificationCandidates(
        candidates: List<ProblemClassificationCandidateEntity>,
    ): List<Long>

    @Query("SELECT * FROM problem_classification_candidate WHERE candidate_id = :id")
    suspend fun readClassificationCandidate(id: String): ProblemClassificationCandidateEntity?

    @Query(
        """
        DELETE FROM problem_classification_binding
        WHERE problem_id = :problemId
          AND basis_revision_id = :problemRevisionId
        """,
    )
    suspend fun deleteClassifications(
        problemId: String,
        problemRevisionId: String,
    ): Int

    @Query(
        """
        SELECT DISTINCT dimension FROM problem_classification_binding
        WHERE problem_id = :problemId
          AND basis_revision_id = :problemRevisionId
        """,
    )
    suspend fun readClassificationDimensions(
        problemId: String,
        problemRevisionId: String,
    ): List<String>

    @Query(
        """
        SELECT DISTINCT acceptance_source FROM problem_classification_binding
        WHERE problem_id = :problemId
          AND basis_revision_id = :problemRevisionId
        """,
    )
    suspend fun readClassificationAcceptanceSources(
        problemId: String,
        problemRevisionId: String,
    ): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelations(relations: List<ProblemRelationEntity>): List<Long>

    @Query("SELECT * FROM problem_relation WHERE relation_id = :id")
    suspend fun readRelation(id: String): ProblemRelationEntity?

    @Query(
        """
        DELETE FROM problem_relation
        WHERE source_problem_id = :problemId
          AND source_basis_revision_id = :problemRevisionId
        """,
    )
    suspend fun deleteOutgoingRelations(
        problemId: String,
        problemRevisionId: String,
    ): Int

    @Query(
        """
        DELETE FROM problem_relation
        WHERE source_problem_id = :problemId
          AND source_basis_revision_id = :problemRevisionId
          AND relation_id IN (:relationIds)
        """,
    )
    suspend fun deleteOutgoingRelationsById(
        problemId: String,
        problemRevisionId: String,
        relationIds: List<String>,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: ProblemOrganizationReceiptEntity): Long

    @Query("SELECT * FROM problem_organization_receipt WHERE command_id = :commandId")
    suspend fun readReceipt(commandId: String): ProblemOrganizationReceiptEntity?

    @Query(
        """
        SELECT * FROM problem_organization_receipt
        WHERE problem_id = :problemId
          AND problem_revision_id = :problemRevisionId
        ORDER BY accepted_at_epoch_millis DESC, command_id DESC
        LIMIT 1
        """,
    )
    suspend fun readLatestReceipt(
        problemId: String,
        problemRevisionId: String,
    ): ProblemOrganizationReceiptEntity?

    @Query(
        """
        SELECT * FROM problem_classification_binding
        WHERE problem_id = :problemId AND basis_revision_id = :problemRevisionId
          AND dimension IN ('SUBJECT', 'CHAPTER', 'KNOWLEDGE')
        ORDER BY dimension ASC, display_name ASC, binding_id ASC
        """,
    )
    fun observeClassifications(
        problemId: String,
        problemRevisionId: String,
    ): Flow<List<ProblemClassificationBindingEntity>>

    @Query(
        """
        SELECT * FROM problem_relation
        WHERE source_problem_id = :problemId
          AND source_basis_revision_id = :problemRevisionId
          AND status = 'ACTIVE'
        ORDER BY relation_type ASC, target_problem_id ASC, relation_id ASC
        """,
    )
    fun observeRelations(
        problemId: String,
        problemRevisionId: String,
    ): Flow<List<ProblemRelationEntity>>
}

/** `readKnowledgeNodeSuccessors` 的行投影：一条合并重定向的原始边。 */
internal data class KnowledgeNodeSuccessorRow(
    val retiredId: String,
    val successorId: String,
)
