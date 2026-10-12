package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.entity.ContentInstallStateEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSourceEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeTeachingMaterialEntity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus

/**
 * **内容调和**：让库里的内容等于随包内容。
 *
 * 从 [RoomKnowledgeBaseStore] 分出来，因为它是一个独立关注点：那个 store 管的是"怎么读、
 * 怎么导入"，这里管的是"库里这份内容与包里那份差在哪、该怎么对齐"。混在一起会让一个通用
 * 存储类承担内容生命周期决策，也把它推到千行硬线上（拆分前 997 行）。
 *
 * 它消灭的失败：改前安装器只在"库里一行都没有"时导入，否则要求节点/来源/绑定/材料四样
 * 逐行完全相等——于是发布后任何一次内容改动都会让知识库整包停摆（KD-15：80 条材料的
 * 时间戳倒挂，把另外一万条一起挡在门外）。
 *
 * 四条不变量，逐条对应一个已发生的失败：
 * 1. **@Upsert 而非 REPLACE**：REPLACE 先删后插，而 8 张表以 RESTRICT 引用
 *    `knowledge_node` / `knowledge_teaching_material`，删除会被外键直接拒绝。
 * 2. **退役而非物删**：学生错题绑定/掌握度/复习队列还引用着它们；掌握度表是 CASCADE，
 *    物删会静默带走学生数据。
 * 3. **退役必须同时删检索特征行**：自愈逻辑靠"已索引数 ≥ 已审校数"判断补建，
 *    留着特征行会让两个计数永久漂移。
 * 4. **只升不降**：库里已 `USER_CONFIRMED` 的节点不被改回包里的值。
 *
 * 崩溃安全靠**差分的幂等性**而非大事务：每个对象独立判定，任一步崩掉，下次重跑同一份
 * 差分即收敛。进度行由调用方在**最后**推进，所以"版本已推进"⟺"上一次跑完了"。
 *
 * 不做的事：**合并的学生数据重指**。那由账本事件 `KC_MERGED` 在投影时生效，
 * 从而不重写任何历史行、重放仍逐字段可复现。
 */
internal class RoomKnowledgeContentReconciler(
    private val database: StudyDatabase,
    /**
     * ④-6（K1 批 1）：内容安装落点的判定缓存失效。与读路径（`RoomKnowledgeBaseStore`）
     * **同一实例**——安装写新节点后，同进程的下一次召回必须重新核验索引完整性。
     */
    private val searchIndexCompleteness: KnowledgeSearchIndexCompleteness =
        KnowledgeSearchIndexCompleteness(),
) {

    /** S21（2026-10-02）：内容安装期就把搜索特征建好，首访问不再触发整科重建。 */
    private val searchIndexBuilder = KnowledgeSearchIndexBuilder(database, searchIndexCompleteness)

    suspend fun applyKnowledgeContentUpdate(
        command: KnowledgeContentUpdateCommand,
    ): KnowledgeContentUpdateResult {
        val skipped = mutableListOf<String>()

        upsertMissingSources(command)

        val existingNodes = database.problemOrganizationDao()
            .readKnowledgeNodesByTaxonomy(command.packId)
        val nodes = planNodes(command.nodes, existingNodes, skipped)
        if (nodes.upserts.isNotEmpty()) {
            database.problemOrganizationDao()
                .upsertKnowledgeNodes(nodes.upserts.map(KnowledgeNodeSeedRecord::toEntity))
        }
        val nodesRetired = retireNodes(
            existingNodes = existingNodes,
            requestedNodeIds = command.nodes.mapTo(hashSetOf(), KnowledgeNodeSeedRecord::knowledgeNodeId),
            retirements = command.nodeRetirements,
        )

        // 被逐条校验挡掉的节点不能作为绑定的目标：写了会撞外键，读也读不到。
        // 其余包内节点都是合法目标——**包括内容没变、本轮不需要写的那些**。
        val retainedNodeIds = command.nodes
            .mapTo(hashSetOf(), KnowledgeNodeSeedRecord::knowledgeNodeId)
            .apply { removeAll(nodes.skippedIds) }

        replaceNodeSourceBindings(command.nodeSourceBindings, retainedNodeIds, command.nodes)
        val relationsWritten = replaceRelations(command.relations, retainedNodeIds, command.nodes)

        val materials = planMaterials(
            command = command,
            acceptedNodeIds = retainedNodeIds,
            acceptedNodes = command.nodes.filter { it.knowledgeNodeId in retainedNodeIds },
            skipped = skipped,
        )
        if (materials.upserts.isNotEmpty()) {
            database.knowledgeTeachingMaterialDao()
                .upsertMaterials(materials.upserts.map(KnowledgeTeachingMaterialRecord::toEntity))
        }
        replaceMaterialBindings(
            bindings = command.materialBindings,
            packMaterialIds = command.materials
                .mapTo(hashSetOf(), KnowledgeTeachingMaterialRecord::materialId),
            acceptedMaterialIds = materials.acceptedIds,
        )
        val materialsRetired = retireMaterials(
            standingIds = materials.standingIds,
            stillInPackIds = materials.stillInPackIds,
        )

        // S21（2026-10-02）：安装期预热搜索特征索引。只重建**本轮节点有增改**的科目——
        // 此前要等首访问的读时自愈触发整科重建，秒级成本落进召回长尾（审计 16.6s max
        // 的最可疑来源）。锚点最后写、崩溃安全与读时自愈共用 KnowledgeSearchIndexBuilder；
        // 纯退役科目不需要（退役路径已同步删除特征行，两个计数同步下降）。
        nodes.upserts
            .mapTo(linkedSetOf(), KnowledgeNodeSeedRecord::subject)
            .forEach { subject -> searchIndexBuilder.rebuildSubject(subject) }

        // ④-6（K1 批 1）：内容安装**落点**的显式失效（计划三处之一）。上面的重建已按科
        // 失效，这里再按"本轮有增改的科"兜一道：本函数往后若加入不经过 rebuildSubject 的
        // 索引相关写入，判定缓存不得带着安装前的"完整"结论放行。公开写入口一处对齐。
        nodes.upserts
            .mapTo(linkedSetOf(), KnowledgeNodeSeedRecord::subject)
            .forEach(searchIndexCompleteness::invalidate)

        return KnowledgeContentUpdateResult(
            nodesInserted = nodes.inserted,
            nodesUpdated = nodes.updated,
            nodesRetired = nodesRetired,
            materialsInserted = materials.inserted,
            materialsUpdated = materials.updated,
            materialsRetired = materialsRetired,
            relationsInserted = relationsWritten,
            relationsDeleted = 0,
            skipped = skipped,
        )
    }

    suspend fun readContentInstallState(packId: String): ContentInstallStateRecord? =
        database.contentInstallStateDao().read(packId)?.let { entity ->
            ContentInstallStateRecord(
                packId = entity.packId,
                contentVersion = entity.contentVersion,
                appliedAtEpochMillis = entity.appliedAtEpochMillis,
                skippedCount = entity.skippedCount,
                skippedDetail = entity.skippedDetail,
            )
        }

    suspend fun recordContentInstallState(record: ContentInstallStateRecord) {
        database.contentInstallStateDao().upsert(
            ContentInstallStateEntity(
                packId = record.packId,
                contentVersion = record.contentVersion,
                appliedAtEpochMillis = record.appliedAtEpochMillis,
                skippedCount = record.skippedCount,
                skippedDetail = record.skippedDetail,
            ),
        )
    }

    // ---- 来源 ----

    /**
     * 只补缺，不改已存在的。
     *
     * 来源是溯源记录不是内容；改它要动 `content_fingerprint`，而那个列有**唯一索引**——
     * 重算后与另一条来源撞索引就会把整批拖垮。
     *
     * 节点来源与材料来源是**两组**（`sources` / `teachingSources`），必须都补：
     * 只补一组会让另一组的使用方全部因"缺来源"被挡掉（实测踩到过，整批材料落不了地）。
     */
    private suspend fun upsertMissingSources(command: KnowledgeContentUpdateCommand) {
        val allSources = command.sources + command.teachingSources
        val dao = database.problemOrganizationDao()
        val existing = dao.readKnowledgeSourcesByIds(
            allSources.mapTo(linkedSetOf(), KnowledgeSourceSeedRecord::sourceId),
        ).mapTo(hashSetOf(), KnowledgeSourceEntity::sourceId)
        val missing = allSources
            .distinctBy(KnowledgeSourceSeedRecord::sourceId)
            .filterNot { it.sourceId in existing }
        if (missing.isNotEmpty()) dao.upsertKnowledgeSources(missing.map { it.toEntity() })
    }

    // ---- 节点 ----

    /** 一个差分计划：要写什么、各是插入还是更新、跳过了谁。 */
    private data class NodePlan(
        val upserts: List<KnowledgeNodeSeedRecord>,
        val inserted: Int,
        val updated: Int,
        /** 被逐条校验挡掉的节点 id。**显式带出来**，不去解析跳过原因的字符串——
         *  节点 id 本身含冒号（`kb:pack:subject:atomic:slug`），按冒号切必然切错。 */
        val skippedIds: Set<String>,
    )

    private fun planNodes(
        nodes: List<KnowledgeNodeSeedRecord>,
        existingNodes: List<KnowledgeNodeEntity>,
        skipped: MutableList<String>,
    ): NodePlan {
        val existingById = existingNodes.associateBy(KnowledgeNodeEntity::knowledgeNodeId)
        val byId = nodes.associateBy(KnowledgeNodeSeedRecord::knowledgeNodeId)
        val accepted = linkedMapOf<String, KnowledgeNodeSeedRecord>()
        val rejected = linkedMapOf<String, String>()

        for (node in nodes) {
            val problem = KnowledgeBaseImportContract.problemWith(node, byId)
            if (problem != null) {
                rejected[node.knowledgeNodeId] = problem
                continue
            }
            val existing = existingById[node.knowledgeNodeId]
            // 只升不降：库里已人工确认过的，调和不得把它改回包里的值。
            accepted[node.knowledgeNodeId] = if (
                existing != null &&
                existing.verificationStatus == KnowledgeNodeVerificationStatus.USER_CONFIRMED.name &&
                node.verificationStatus != KnowledgeNodeVerificationStatus.USER_CONFIRMED.name
            ) {
                node.copy(verificationStatus = KnowledgeNodeVerificationStatus.USER_CONFIRMED.name)
            } else {
                node
            }
        }

        // **级联**：父级写不进去（被校验拒了，或父级本身没通过），子级也写不进去——
        // 写进去会撞 `parent_knowledge_node_id` 的外键，整批崩掉。
        // 这是"逐对象跳过"必须闭合的影响面：单看那一行没毛病，坏在它与父级的关系上。
        // 迭代到不动点，因为跳过一层会牵动下一层。
        var changed = true
        while (changed) {
            changed = false
            for ((id, node) in accepted.toList()) {
                val parentId = node.parentKnowledgeNodeId ?: continue
                if (parentId in existingById || parentId in accepted) continue
                accepted.remove(id)
                rejected[id] = "父级 $parentId 不在库中且本轮未被接受"
                changed = true
            }
        }

        rejected.forEach { (id, reason) -> skipped += "node:$id: $reason" }
        val upserts = mutableListOf<KnowledgeNodeSeedRecord>()
        var inserted = 0
        var updated = 0
        accepted.forEach { (id, node) ->
            val existing = existingById[id]
            when {
                existing == null -> { inserted++; upserts += node }
                existing != node.toEntity() -> { updated++; upserts += node }
            }
        }
        // **按父级先序写出**。`knowledge_node.parent_knowledge_node_id` 是自引用外键（RESTRICT），
        // 而 `@Upsert` 逐行插入、逐行检查——子级排在父级前面就会撞外键、整批崩。
        //
        // 不假定包里 topics 数组的顺序是对的：实测当前包里有 4 个 topic 的父级排在它**后面**
        // （`MATH·综合` 在 `MATH` 之前等），而"数组顺序"既不是包契约的一部分、也没被任何门钉住。
        // 由写入方负责这个顺序，等于让一个排版细节决定安装能否成功。
        return NodePlan(
            upserts = parentFirstOrder(accepted, existingById.keys),
            inserted = inserted,
            updated = updated,
            skippedIds = rejected.keys.toSet(),
        )
    }

    /**
     * 把节点排成"父级一定在子级之前"。
     *
     * 迭代发出可发出者，直到没有进展——父级要么已在库里，要么本轮已发出。契约已排除
     * 父子成环（`validateTopicHierarchyIsAcyclic`），而父级不在集合里的节点在级联阶段
     * 已被剔除，所以循环必然把 accepted 清空。
     */
    private fun parentFirstOrder(
        accepted: Map<String, KnowledgeNodeSeedRecord>,
        existingIds: Set<String>,
    ): List<KnowledgeNodeSeedRecord> {
        val emitted = mutableSetOf<String>()
        val out = mutableListOf<KnowledgeNodeSeedRecord>()
        val pending = accepted.keys.toMutableSet()
        var progressed = true
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            for (id in pending.toList()) {
                val parentId = accepted.getValue(id).parentKnowledgeNodeId
                if (parentId == null || parentId in existingIds || parentId in emitted) {
                    out += accepted.getValue(id)
                    emitted += id
                    pending -= id
                    progressed = true
                }
            }
        }
        check(pending.isEmpty()) {
            "Knowledge nodes are not a forest; ${pending.size} node(s) never became writable"
        }
        return out
    }

    /** 包里有、库里没有 / 库里没有的都要退役；`retirements` 给出 1:1 取代目标（可能为 null）。 */
    private suspend fun retireNodes(
        existingNodes: List<KnowledgeNodeEntity>,
        requestedNodeIds: Set<String>,
        retirements: Map<String, String?>,
    ): Int {
        val standing = existingNodes
            .mapTo(linkedSetOf(), KnowledgeNodeEntity::knowledgeNodeId)
            .apply { removeAll(requestedNodeIds) }
        if (standing.isEmpty()) return 0
        val dao = database.problemOrganizationDao()
        val retired = standing.count { dao.retireKnowledgeNode(it, retirements[it]) > 0 }
        // 与退役同步：自愈逻辑靠"已索引数 ≥ 已审校数"判断是否补建，退役节点若留着
        // 特征行，两个计数会永久漂移。
        standing.chunked(MAX_IN_PARAMETERS).forEach { dao.deleteSearchFeaturesForNodes(it.toSet()) }
        return retired
    }

    // ---- 节点—来源绑定与前置边：纯内容，整体替换 ----

    /**
     * 这两张表没有学生数据引用（与节点/材料不同），所以整体替换比逐条 diff 更简单且等价：
     * 删掉这个包的全部，再按包写入。**只在该包的对象上操作**，不会碰到别的包或用户自建内容。
     */
    private suspend fun replaceNodeSourceBindings(
        bindings: List<KnowledgeNodeSourceBindingSeedRecord>,
        retainedNodeIds: Set<String>,
        nodes: List<KnowledgeNodeSeedRecord>,
    ) {
        val dao = database.problemOrganizationDao()
        val packNodeIds = nodes.mapTo(hashSetOf(), KnowledgeNodeSeedRecord::knowledgeNodeId)
        if (packNodeIds.isEmpty()) return
        packNodeIds.chunked(MAX_IN_PARAMETERS).forEach {
            dao.deleteKnowledgeNodeSourceBindingsForNodes(it.toSet())
        }
        val toInsert = bindings.filter { it.knowledgeNodeId in retainedNodeIds }
        if (toInsert.isNotEmpty()) {
            dao.importKnowledgeNodeSourceBindings(toInsert.map(KnowledgeNodeSourceBindingSeedRecord::toEntity))
        }
    }

    /** 返回实际写入的边数。两端都必须保留——只有一端在包里的边是悬空的，写进去也读不到。 */
    private suspend fun replaceRelations(
        relations: List<KnowledgeNodeRelationRecord>,
        retainedNodeIds: Set<String>,
        nodes: List<KnowledgeNodeSeedRecord>,
    ): Int {
        val dao = database.knowledgeNodeRelationDao()
        val packNodeIds = nodes.mapTo(hashSetOf(), KnowledgeNodeSeedRecord::knowledgeNodeId)
        if (packNodeIds.isEmpty()) return 0
        packNodeIds.chunked(MAX_IN_PARAMETERS).forEach { dao.deleteByDependents(it.toSet()) }
        val toInsert = relations.filter {
            it.dependentKnowledgeNodeId in retainedNodeIds &&
                it.prerequisiteKnowledgeNodeId in retainedNodeIds
        }
        if (toInsert.isNotEmpty()) {
            dao.upsertAll(toInsert.map(KnowledgeNodeRelationRecord::toEntity))
        }
        return toInsert.size
    }

    // ---- 材料 ----

    private data class MaterialPlan(
        val upserts: List<KnowledgeTeachingMaterialRecord>,
        val acceptedIds: Set<String>,
        val inserted: Int,
        val updated: Int,
        /** 库里在、包里已没有的材料 id（退役候选）。 */
        val standingIds: List<String>,
        /** 两边都有的材料 id（"绑定被清零"检查的候选）。 */
        val stillInPackIds: List<String>,
    )

    /**
     * 材料差分：**分页读**库里的行，逐页比对、逐页释放。
     *
     * 消灭的失败（KD-32 设备实测峰值）：改前一次 `readByStableCodePrefix` 把整包 5 万行
     * 材料连同每行正文读回内存（`kb_peak3.hprof`：KnowledgeTeachingMaterialEntity
     * 50,869 个、随行字符串约 +30.5 万个 char[]，基线对比 ≈ +49MB），再叠加
     * `existingById`（50,869 条映射）与 `groupBy`（每材料一张 List + 59,012 条映射）两个
     * 中转结构，与驻留的包材料记录（59,010 条）同时在场，把 192MiB 上限的堆顶到
     * 191.8MB（`0% free, 191MB/192MB`）后 OOM。
     *
     * 逐行判定语义**逐字不变**：每行仍用 `existing != material.toEntity()` 全等比较，
     * 被跳过的理由与计数口径一致，退役集合仍是"库里在、包里没有"的全部 materialId
     * （含前缀头段与尾段——段划分覆盖整个前缀区间，见 [materialScanSegments]）。
     * 改的只是"整包在场"→"一页在场"；库里这一包的 id 仍需收集（退役判定要用），
     * 但正文不再保留。
     */
    private suspend fun planMaterials(
        command: KnowledgeContentUpdateCommand,
        acceptedNodeIds: Set<String>,
        acceptedNodes: List<KnowledgeNodeSeedRecord>,
        skipped: MutableList<String>,
    ): MaterialPlan {
        val dao = database.knowledgeTeachingMaterialDao()
        val packPrefix = "${command.packId}:"
        val packPrefixEnd = stableCodePrefixEnd(packPrefix)
        val packMaterialIds = command.materials
            .mapTo(hashSetOf(), KnowledgeTeachingMaterialRecord::materialId)
        val upserts = mutableListOf<KnowledgeTeachingMaterialRecord>()
        val acceptedIds = mutableSetOf<String>()
        var inserted = 0
        var updated = 0
        val standingIds = mutableListOf<String>()
        val stillInPackIds = mutableListOf<String>()

        // 绑定按 materialId 排序一次（5.9 万引用 ≈ 0.5MB），每条材料二分定位自己的绑定组。
        // 改前 `groupBy { materialId }` 会同时活下 5.9 万张 List、5.9 万个后备 Object[]
        // 与 5.9 万条映射条目（KD-32 直方图：ArrayList 59,543、Object[] +5.7 万、
        // LinkedHashMapEntry +11 万的一部分）。
        val sortedBindings = command.materialBindings.sortedBy(
            KnowledgeTeachingMaterialNodeBindingRecord::materialId,
        )

        val pages = command.materials
            .sortedBy(KnowledgeTeachingMaterialRecord::stableCode)
            .chunked(MATERIAL_READ_PAGE_SIZE)
        val segments = materialScanSegments(
            packPrefix = packPrefix,
            packPrefixEnd = packPrefixEnd,
            pageFirstStableCodes = pages.map { it.first().stableCode },
        )
        for (segment in segments) {
            val rows = dao.readMaterialsInStableCodeRange(
                fromStableCode = segment.fromStableCode,
                throughStableCode = segment.throughStableCode,
            )
            val pageIndex = segment.pageIndex
            if (pageIndex != null) {
                val rowsById = rows.associateBy(KnowledgeTeachingMaterialEntity::materialId)
                for (material in pages[pageIndex]) {
                    val ownBindings = bindingsForMaterial(sortedBindings, material.materialId)
                    if (ownBindings.isEmpty()) {
                        // 无绑定的材料在装载时会被静默剔除、等于白写：显式记账，不无声丢弃。
                        skipped += "material:${material.materialId}: 没有任何知识节点绑定"
                        continue
                    }
                    val problem = KnowledgeTeachingMaterialContract.problemWith(
                        material = material,
                        bindings = ownBindings.filter { it.knowledgeNodeId in acceptedNodeIds },
                        nodes = acceptedNodes,
                        sources = command.teachingSources,
                    )
                    if (problem != null) {
                        skipped += "material:${material.materialId}: $problem"
                        continue
                    }
                    acceptedIds += material.materialId
                    val existing = rowsById[material.materialId]
                    when {
                        existing == null -> { inserted++; upserts += material }
                        existing != material.toEntity() -> { updated++; upserts += material }
                    }
                }
            }
            // 这一段的库里行就地分流：正文随段一起释放，只留下退役判定需要的 id。
            rows.forEach { row ->
                if (row.materialId in packMaterialIds) {
                    stillInPackIds += row.materialId
                } else {
                    standingIds += row.materialId
                }
            }
        }
        return MaterialPlan(upserts, acceptedIds, inserted, updated, standingIds, stillInPackIds)
    }

    private suspend fun replaceMaterialBindings(
        bindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
        packMaterialIds: Set<String>,
        acceptedMaterialIds: Set<String>,
    ) {
        if (packMaterialIds.isEmpty()) return
        val dao = database.knowledgeTeachingMaterialDao()
        // 按**包的**材料集删（不是按绑定集）：一条材料在新版里丢了全部绑定，它的旧绑定
        // 也必须清掉，否则会留下指向已退役节点的悬空行。
        packMaterialIds.chunked(MAX_IN_PARAMETERS).forEach {
            dao.deleteBindingsForMaterials(it.toSet())
        }
        val toInsert = bindings.filter { it.materialId in acceptedMaterialIds }
        if (toInsert.isNotEmpty()) {
            dao.upsertBindings(toInsert.map { it.toEntity() })
        }
    }

    /**
     * 退役两类材料：**移出包的**，以及**此刻已无绑定的**。
     *
     * 后者是影响面闭合里最容易漏的一格：`replaceMaterialBindings` 会把包内材料的绑定**整体替换**，
     * 于是"绑定目标被丢弃"或"校验没过被跳过"的材料会剩 0 条绑定，而检索/讲题参考/复习题
     * 全都要经过绑定表——0 绑定等于不可达。不退役它就会留一条 status=ACTIVE 却谁也到不了的
     * 行，让"库里有多少材料"与"运行时能用到多少"永久对不上。
     *
     * [standingIds] / [stillInPackIds] 由 [planMaterials] 的分页扫描就地分流得到，口径与
     * 改前"整包读回后求差集"逐字一致（`inDatabase - packMaterialIds` /
     * `inDatabase ∩ packMaterialIds`），只是不再把行正文留在内存里。
     */
    private suspend fun retireMaterials(
        standingIds: List<String>,
        stillInPackIds: List<String>,
    ): Int {
        val dao = database.knowledgeTeachingMaterialDao()
        var retired = standingIds.count { dao.retireMaterial(it) > 0 }

        if (stillInPackIds.isNotEmpty()) {
            // 分块与同文件四条批量 DELETE 同口径（W4-4 的分块修复漏了这条 SELECT）：
            // 包规模（v2 教学支持侧车 5 万+ 材料）越过 SQLite 绑定变量上限时，这条
            // `IN (…)` 会抛 `SQLITE_ERROR: too many SQL variables`，整包安装失败。
            // 聚合语义不变：仍是"这些材料里仍有绑定的那些"的并集。
            val stillBound = stillInPackIds
                .chunked(MAX_IN_PARAMETERS)
                .flatMap { dao.readBoundMaterialIds(it.toSet()) }
                .toHashSet()
            retired += (stillInPackIds - stillBound).count { dao.retireMaterial(it) > 0 }
        }
        return retired
    }
}

/**
 * 材料分页扫描的一段：半开区间 `[fromStableCode, throughStableCode)`。
 *
 * [pageIndex] 指向这一段要处理的包内材料页（`command.materials` 按 stableCode 排序后
 * 每 [MATERIAL_READ_PAGE_SIZE] 条一页）；前缀头段（包内第一条材料之前）与空包为 null。
 */
internal data class MaterialScanSegment(
    val fromStableCode: String,
    val throughStableCode: String,
    val pageIndex: Int?,
)

/**
 * 材料分页扫描的段划分（纯函数，JVM 面可测）。
 *
 * 不变量——**准确性完全靠它们**（漏一段 = 库里在、包里的行被静默留下；重一段 = 同一行
 * 被当两次退役候选）：
 * 1. 第一段从 [packPrefix] 开始，最后一段到 [packPrefixEnd]（不含）结束；
 * 2. 相邻段首尾相接：`segments[i].throughStableCode == segments[i + 1].fromStableCode`；
 * 3. 因此各段合起来**恰好覆盖** `[packPrefix, packPrefixEnd)`，既不重也不漏。
 *
 * [pageFirstStableCodes] 是每页第一条材料的 stableCode（升序），段以它们为边界——所以
 * 第 i 页的材料全部落在 `segments[i + 1]` 里（头段没有材料可处理）；空包只剩一个段，
 * 仍覆盖整个前缀：**残留行照常退役**，不会因为"包里一条材料都没有"而漏掉。
 */
internal fun materialScanSegments(
    packPrefix: String,
    packPrefixEnd: String,
    pageFirstStableCodes: List<String>,
): List<MaterialScanSegment> {
    val segments = ArrayList<MaterialScanSegment>(pageFirstStableCodes.size + 1)
    var from = packPrefix
    pageFirstStableCodes.forEachIndexed { index, pageFirstCode ->
        segments += MaterialScanSegment(
            fromStableCode = from,
            throughStableCode = pageFirstCode,
            pageIndex = if (index == 0) null else index - 1,
        )
        from = pageFirstCode
    }
    segments += MaterialScanSegment(
        fromStableCode = from,
        throughStableCode = packPrefixEnd,
        pageIndex = pageFirstStableCodes.indices.lastOrNull(),
    )
    return segments
}

/**
 * `stable_code` 前缀的**开区间上界**：`[prefix, 返回值)` 恰好是所有以 [prefix] 开头的
 * 字符串（含 prefix 本身）。
 *
 * 为什么不是 `LIKE prefix || '%'`：区间比较是字节精确的，能吃到 `stable_code` 的唯一索引
 * 做范围扫描（`LIKE` 用绑定参数时索引常常用不上，且对 ASCII 大小写不敏感、`_`/`%`
 * 还会被当成通配符）；配合 [materialScanSegments] 的段边界，分页才可能是"索引上的一刀切"。
 * [prefix] 的最后一个字符必须有后继（本文件的用法是 `"$packId:"`，':' → ';'）。
 */
internal fun stableCodePrefixEnd(prefix: String): String {
    require(prefix.isNotEmpty()) { "Stable-code prefix must not be empty" }
    val last = prefix.last()
    check(last < Char.MAX_VALUE) { "Stable-code prefix has no successor character" }
    return prefix.dropLast(1) + (last + 1)
}

/**
 * 取某条材料的绑定组：`sortedBindings` 必须已按 materialId 升序（调用方排序一次），
 * 二分定位后向两侧展开同 id 的邻居——同一 materialId 的绑定保持原有相对顺序。
 *
 * 消灭的失败（KD-32 峰值）：`groupBy` 在 5.9 万材料规模上同时建 5.9 万张 List 与
 * 5.9 万条映射（ArrayList 59,543 / Object[] +5.7 万 / LinkedHashMapEntry +11 万的一部分，
 * 约 12MB 浅尺寸）；这里只留一份排序副本（引用，无新字符串）。
 */
internal fun bindingsForMaterial(
    sortedBindings: List<KnowledgeTeachingMaterialNodeBindingRecord>,
    materialId: String,
): List<KnowledgeTeachingMaterialNodeBindingRecord> {
    var index = sortedBindings.binarySearch { it.materialId.compareTo(materialId) }
    if (index < 0) return emptyList()
    var start = index
    while (start > 0 && sortedBindings[start - 1].materialId == materialId) start--
    var end = index + 1
    while (end < sortedBindings.size && sortedBindings[end].materialId == materialId) end++
    return sortedBindings.subList(start, end)
}

/**
 * 材料分页扫描的页大小：每段只在内存里存在一页实体（含正文）。
 *
 * 5 万行整包读回 ≈ +49MB char[]（KD-32）；500 行是它的 1% 量级，同时让
 * `KnowledgeContentReconciliationInstrumentedTest` 的 1,200 材料用例跨满 3 页。
 * 与 `MAX_IN_PARAMETERS`（SQLite 绑定变量口径）无关：本查询没有 `IN` 列表。
 */
private const val MATERIAL_READ_PAGE_SIZE = 500

/**
 * 批量 `… IN (…)` 删除的分块上限。SQLite 绑定变量上限**旧平台为 999**
 * （`SQLITE_MAX_VARIABLE_NUMBER`，minSdk 23 即适用），取 900 留余量；
 * 新平台虽为 32766，包规模（v2 教学支持侧车已达 5 万材料）仍会越过。
 *
 * 消灭的失败（2026-10-02 实测）：整包安装在本文件的四条批量 DELETE 上直接抛
 * `SQLITE_ERROR: too many SQL variables`，安装失败、主页横幅常驻
 * （"本地知识包尚未准备好"），检索端到端仪器化用例同样在此崩。
 */
private const val MAX_IN_PARAMETERS = 900
