package com.tingyun.smartmistakebook.core.data.knowledge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSourceBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSourceSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialFingerprint
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.port.KnowledgeReadPort
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialDerivationKind
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceContentUsePolicy
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `readBestKnowledgeTeachingMaterialPerNode` 的真 Room 仪器化测试。
 *
 * 这版针对三处已确认的假绿风险做了加固（见 `build/agent-batch24/room-test-design.md`
 * 与评审结论）：
 *
 * 1. **多节点查询按节点各自取最佳**：不是“返回的 id 属于该节点候选集”就算过，而是先按
 *    **该节点自己的绑定角色** + 教学类型秩 + title + material_id 算出唯一期望，再逐节点
 *    比对。另有一条跨节点共享材料夹具：同一材料在 A 节点是 PRIMARY、在 B 节点是
 *    SUPPORTING；若实现把角色按材料全局折叠（单节点测试看不出来），B 节点会错选它，
 *    本用例当场红。
 * 2. **教学优先覆盖全部 7 档**：6 个相邻档位边界各一个真实入库节点（讲解→方法→推导→
 *    例题→表征→误区→完整解答），不是只覆盖第一档。真实随包数据没有这种分布，所以夹具
 *    走生产写口（`importKnowledgeBase` + `importKnowledgeTeachingMaterials`）构造，
 *    仍过全部契约与指纹校验，不直写材料表绕过校验。
 * 3. **无材料归零是构造性断言**：零材料节点由夹具显式创建（在库、0 条材料），不再用
 *    `Assume` 在“随包数据里找不到零材料节点”时跳过——那样 CI 绿而断言从未执行。
 *    同一用例还钉住空集、RETIRED 过滤与科目过滤。
 *
 * 临时具名库与 `@Before` / `@After` 搭法与
 * `BundledKnowledgeBaseInstallerInstrumentedTest.kt:22-40` 逐字一致。
 *
 * 失败信息统一带：节点 id、返回条数/键集、返回材料的 type/role、该节点候选摘要。
 */
@RunWith(AndroidJUnit4::class)
class TeachingMaterialAllocationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: StudyDatabasePort
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "bundled-knowledge-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = StudyDatabaseFactory.open(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    // ------------------------------------------------------------------
    // ① 教学优先覆盖全部 7 档（6 个相邻边界）
    // ------------------------------------------------------------------

    @Test(timeout = TIMEOUT_MILLIS)
    fun typePriorityCoversEveryTeachingRankBoundary() = runBlocking {
        seedFixture()

        val observedTypes = TYPE_BOUNDARIES
            .flatMap { (node, _, _) -> candidatesFor(node) }
            .mapTo(linkedSetOf()) { it.materialType }
        assertEquals(
            "夹具必须覆盖全部 7 个教学类型档位（DAO 的 CASE 有 7 个 WHEN）；" +
                "实际覆盖=$observedTypes",
            TEACHING_TYPE_ORDER.toSet(),
            observedTypes,
        )

        TYPE_BOUNDARIES.forEach { (node, lowerType, higherType) ->
            val candidates = candidatesFor(node)
            assertEquals(
                "夹具漂移：节点 $node 应恰有 2 条候选，实际 ${candidates.size} 条；" +
                    "候选=${candidates.map { "${it.materialId}:${it.materialType}" }}",
                2,
                candidates.size,
            )
            assertEquals(
                "夹具漂移：节点 $node 的候选类型应恰为低秩/高秩两档；" +
                    "实际=${candidates.map { it.materialType }}",
                setOf(lowerType, higherType),
                candidates.mapTo(linkedSetOf()) { it.materialType },
            )
            assertTrue(
                "夹具漂移：$lowerType 的教学秩必须低于 $higherType；" +
                    "lower=${typeRank(lowerType)}，higher=${typeRank(higherType)}",
                typeRank(lowerType) < typeRank(higherType),
            )

            val expectedId = candidates.single { it.materialType == lowerType }.materialId
            val roles = bindingRolesFor(node, candidates)
            val result = database.readBestKnowledgeTeachingMaterialPerNode(MATH, setOf(node))

            assertEquals(
                "节点 $node 应恰返回 1 条；实际返回 ${result.size} 条，keys=${result.keys}",
                1,
                result.size,
            )
            val returned = result[node] ?: throw AssertionError(
                "节点 $node 有 ${candidates.size} 条候选却未返回结果；" +
                    "返回 keys=${result.keys}；候选=${candidateSummary(candidates, roles)}",
            )
            assertEquals(
                "节点 $node 应选教学秩更低的 $lowerType（$expectedId），" +
                    "实际 ${returned.materialId}（type=${returned.materialType}，" +
                    "role=${roles[returned.materialId]}）；" +
                    "返回 ${result.size} 条，keys=${result.keys}；" +
                    "候选=${candidateSummary(candidates, roles)}",
                expectedId,
                returned.materialId,
            )
        }
    }

    // ------------------------------------------------------------------
    // ② 多节点查询：每个节点都拿到“它自己的”教学最佳
    // ------------------------------------------------------------------

    @Test(timeout = TIMEOUT_MILLIS)
    fun multiNodeQueryReturnsEachNodesOwnBestMaterial() = runBlocking {
        seedFixture()

        val requested = ALL_MATERIAL_NODES
        val candidatesByNode = requested.associateWith { candidatesFor(it) }
        val rolesByNode = requested.associateWith {
            bindingRolesFor(it, candidatesByNode.getValue(it))
        }
        val expectedByNode = requested.associateWith { node ->
            expectedBest(candidatesByNode.getValue(node), rolesByNode.getValue(node))
        }

        val result = database.readBestKnowledgeTeachingMaterialPerNode(
            MATH,
            requested.toSet() + N_ZERO + N_PHYSICS,
        )

        assertEquals(
            "多节点查询应对每个有材料节点各返回 1 条（共 ${requested.size} 条）；" +
                "实际 ${result.size} 条，返回 keys=${result.keys}",
            requested.size,
            result.size,
        )
        assertEquals(
            "多节点查询的键集应恰为有材料节点；" +
                "缺失=${requested.toSet() - result.keys}，多余=${result.keys - requested.toSet()}；" +
                "实际 keys=${result.keys}",
            requested.toSet(),
            result.keys,
        )

        requested.forEach { node ->
            val returned = result[node] ?: throw AssertionError(
                "节点 $node 有材料却未出现在多节点结果中；" +
                    "返回 ${result.size} 条，keys=${result.keys}；" +
                    "候选=${candidateSummary(candidatesByNode.getValue(node), rolesByNode.getValue(node))}",
            )
            assertEquals(
                "节点 $node 返回 ${returned.materialId}（type=${returned.materialType}，" +
                    "role=${rolesByNode.getValue(node)[returned.materialId]}）不是该节点自己的教学最佳 " +
                    "${expectedByNode.getValue(node)}；返回 ${result.size} 条，keys=${result.keys}；" +
                    "候选=${candidateSummary(candidatesByNode.getValue(node), rolesByNode.getValue(node))}",
                expectedByNode.getValue(node),
                returned.materialId,
            )
        }

        // 四级排序的最后两级：先 title，再 material_id。
        // mat-title-z 的 title 更靠前但 material_id 更大；若实现先按 id 或按插入顺序，
        // 会错选 mat-title-a（id 更小/先插入）。
        assertEquals(
            "平局必须先按 title 再按 material_id：节点 $N_TITLE_TIEBREAK 应返回 " +
                "$MATERIAL_TITLE_WINNER（title=A，id=z）；" +
                "实际=${result[N_TITLE_TIEBREAK]?.materialId}；返回 ${result.size} 条，" +
                "keys=${result.keys}；候选=${candidateSummary(candidatesByNode.getValue(N_TITLE_TIEBREAK), rolesByNode.getValue(N_TITLE_TIEBREAK))}",
            MATERIAL_TITLE_WINNER,
            result[N_TITLE_TIEBREAK]?.materialId,
        )
        assertEquals(
            "title 相同时必须按 material_id 升序：节点 $N_ID_TIEBREAK 应返回 " +
                "$MATERIAL_ID_WINNER；实际=${result[N_ID_TIEBREAK]?.materialId}；" +
                "返回 ${result.size} 条，keys=${result.keys}；" +
                "候选=${candidateSummary(candidatesByNode.getValue(N_ID_TIEBREAK), rolesByNode.getValue(N_ID_TIEBREAK))}",
            MATERIAL_ID_WINNER,
            result[N_ID_TIEBREAK]?.materialId,
        )

        // 区分力自证：把“角色按材料全局折叠”（同一材料在所有被请求节点上取 MIN(role)）
        // 作为回归模型跑一遍，它必须在共享材料消费者节点上给出不同答案；否则本夹具
        // 无法证明多节点断言真的钉住了“按节点各自取最佳”。
        val allCandidateIds = requested
            .flatMap { node -> candidatesByNode.getValue(node).map { it.materialId } }
            .toSet()
        val minRoleAcrossRequested = database
            .readKnowledgeTeachingMaterialNodeBindings(allCandidateIds)
            .filter { it.knowledgeNodeId in requested }
            .groupBy { it.materialId }
            .mapValues { (_, rows) -> rows.minOf { roleRank(it.role) } }
        val globalFoldWinner = globalFoldBest(
            candidatesByNode.getValue(N_SHARED_CONSUMER),
            minRoleAcrossRequested,
        )
        assertNotEquals(
            "夹具必须能区分“按节点各自取最佳”与“按材料全局折叠角色”：" +
                "两者在 $N_SHARED_CONSUMER 上必须给出不同结果；" +
                "globalFold=$globalFoldWinner，" +
                "perNode=${expectedByNode.getValue(N_SHARED_CONSUMER)}",
            globalFoldWinner,
            expectedByNode.getValue(N_SHARED_CONSUMER),
        )
    }

    // ------------------------------------------------------------------
    // ③ 角色优先于类型优先（单节点与多节点两种查询形状）
    // ------------------------------------------------------------------

    @Test(timeout = TIMEOUT_MILLIS)
    fun rolePriorityOutranksTypePriorityOnEveryQueryShape() = runBlocking {
        seedFixture()

        listOf(N_ROLE_PRIMARY, N_ROLE_PREREQUISITE).forEach { node ->
            val candidates = candidatesFor(node)
            val roles = bindingRolesFor(node, candidates)
            val primaryIds = roles
                .filterValues { it == KnowledgeMaterialNodeRole.PRIMARY.name }
                .keys
            assertEquals(
                "夹具漂移：节点 $node 必须恰有 1 条 PRIMARY 绑定；实际=$primaryIds",
                1,
                primaryIds.size,
            )
            val nonPrimaryIds = roles
                .filterValues { it != KnowledgeMaterialNodeRole.PRIMARY.name }
                .keys
            assertTrue(
                "夹具漂移：节点 $node 必须有非 PRIMARY 候选；roles=$roles",
                nonPrimaryIds.isNotEmpty(),
            )
            val primaryTypeRank = candidates
                .single { it.materialId in primaryIds }
                .let { typeRank(it.materialType) }
            val nonPrimaryTypeRanks = nonPrimaryIds.map { id ->
                candidates.single { it.materialId == id }.let { typeRank(it.materialType) }
            }
            assertTrue(
                "夹具漂移：节点 $node 的非 PRIMARY 候选类型必须更靠前（否则角色优先没有区分度）；" +
                    "primaryRank=$primaryTypeRank，nonPrimaryRanks=$nonPrimaryTypeRanks；" +
                    "候选=${candidateSummary(candidates, roles)}",
                nonPrimaryTypeRanks.all { it < primaryTypeRank },
            )

            val result = database.readBestKnowledgeTeachingMaterialPerNode(MATH, setOf(node))
            assertEquals(
                "节点 $node 应恰返回 1 条；实际返回 ${result.size} 条，keys=${result.keys}",
                1,
                result.size,
            )
            val returned = result[node] ?: throw AssertionError(
                "节点 $node 有 PRIMARY 候选却未返回结果；" +
                    "返回 keys=${result.keys}；候选=${candidateSummary(candidates, roles)}",
            )
            assertTrue(
                "节点 $node 应优先返回 PRIMARY 绑定材料；" +
                    "实际 ${returned.materialId}（type=${returned.materialType}，" +
                    "role=${roles[returned.materialId]}）；" +
                    "PRIMARY=$primaryIds；返回 ${result.size} 条，keys=${result.keys}；" +
                    "候选=${candidateSummary(candidates, roles)}",
                returned.materialId in primaryIds,
            )
        }

        // 多节点：共享材料在所有者节点是 PRIMARY、在消费者节点是 SUPPORTING。
        // 消费者必须返回自己的 PRIMARY，而不是共享材料（角色不得跨节点折叠）。
        val requested = setOf(N_SHARED_OWNER, N_SHARED_CONSUMER)
        val result = database.readBestKnowledgeTeachingMaterialPerNode(MATH, requested)
        assertEquals(
            "共享材料夹具应返回 2 个节点各 1 条；实际 ${result.size} 条，keys=${result.keys}",
            2,
            result.size,
        )
        assertEquals(
            "共享材料节点 $N_SHARED_OWNER 应返回自己的 PRIMARY 共享材料；" +
                "实际 keys=${result.keys}；" +
                "候选=${candidateSummary(candidatesFor(N_SHARED_OWNER), bindingRolesFor(N_SHARED_OWNER, candidatesFor(N_SHARED_OWNER)))}",
            MATERIAL_SHARED,
            result[N_SHARED_OWNER]?.materialId,
        )
        assertEquals(
            "共享材料在 $N_SHARED_OWNER 的 PRIMARY 绑定不得写回 $N_SHARED_CONSUMER；" +
                "消费者应返回自己的 PRIMARY；实际 ${result[N_SHARED_CONSUMER]?.materialId}" +
                "（type=${result[N_SHARED_CONSUMER]?.materialType}）；" +
                "返回 keys=${result.keys}；" +
                "候选=${candidateSummary(candidatesFor(N_SHARED_CONSUMER), bindingRolesFor(N_SHARED_CONSUMER, candidatesFor(N_SHARED_CONSUMER)))}",
            MATERIAL_CONSUMER_PRIMARY,
            result[N_SHARED_CONSUMER]?.materialId,
        )
    }

    // ------------------------------------------------------------------
    // ④ 无材料归零 + 空集 + RETIRED/科目过滤（全部构造性，无 Assume）
    // ------------------------------------------------------------------

    @Test(timeout = TIMEOUT_MILLIS)
    fun zeroMaterialAndFiltersAreDeterministic() = runBlocking {
        seedFixture()

        // 零材料节点必须真实在库，且 0 条材料——不依赖随包内容里是否恰好存在。
        assertTrue(
            "零材料夹具 $N_ZERO 不在库中（id 写错或节点被跳过）；" +
                "空结果不能证明“无材料节点不出现”",
            database.readKnowledgeNodesByIds(setOf(N_ZERO)).any { it.knowledgeNodeId == N_ZERO },
        )
        val zeroCandidates = candidatesFor(N_ZERO)
        assertEquals(
            "零材料夹具 $N_ZERO 实际有 ${zeroCandidates.size} 条材料，夹具失效；" +
                "候选=${zeroCandidates.map { "${it.materialId}:${it.materialType}" }}",
            0,
            zeroCandidates.size,
        )

        val onlyZero = database.readBestKnowledgeTeachingMaterialPerNode(MATH, setOf(N_ZERO))
        assertTrue(
            "零材料节点不应出现在结果中；实际返回 ${onlyZero.size} 条，keys=${onlyZero.keys}",
            onlyZero.isEmpty(),
        )

        val mixed = database.readBestKnowledgeTeachingMaterialPerNode(
            MATH,
            setOf(N_ZERO, N_TYPE_01),
        )
        assertEquals(
            "零材料 + 有材料混合查询应只返回 1 条；" +
                "实际返回 ${mixed.size} 条，keys=${mixed.keys}",
            1,
            mixed.size,
        )
        assertFalse(
            "混合查询不应给零材料节点 $N_ZERO 造出条目；" +
                "实际返回 ${mixed.size} 条，keys=${mixed.keys}",
            N_ZERO in mixed,
        )
        assertTrue(
            "混合查询应保留有材料节点 $N_TYPE_01；" +
                "实际返回 ${mixed.size} 条，keys=${mixed.keys}",
            N_TYPE_01 in mixed,
        )

        val emptyResult = database.readBestKnowledgeTeachingMaterialPerNode(MATH, emptySet())
        assertTrue(
            "空节点集必须返回空 map；实际返回 ${emptyResult.size} 条，keys=${emptyResult.keys}",
            emptyResult.isEmpty(),
        )

        // RETIRED：退役该节点当前的教学最佳，端口必须跳过它并回落到下一条。
        retireMaterial(MATERIAL_RETIRED_BEST)
        val afterRetireCandidates = candidatesFor(N_RETIRED)
        assertFalse(
            "退役材料 $MATERIAL_RETIRED_BEST 仍出现在候选读口；" +
                "候选=${afterRetireCandidates.map { it.materialId }}",
            afterRetireCandidates.any { it.materialId == MATERIAL_RETIRED_BEST },
        )
        assertEquals(
            "退役后节点 $N_RETIRED 应仍有 1 条活跃候选；" +
                "实际=${afterRetireCandidates.map { it.materialId }}",
            1,
            afterRetireCandidates.size,
        )
        val retiredResult = database.readBestKnowledgeTeachingMaterialPerNode(
            MATH,
            setOf(N_RETIRED),
        )
        assertEquals(
            "RETIRED 材料必须被过滤，节点应回落到下一条最佳；" +
                "实际返回 ${retiredResult.size} 条，keys=${retiredResult.keys}；" +
                "返回=${retiredResult[N_RETIRED]?.materialId}" +
                "（type=${retiredResult[N_RETIRED]?.materialType}）",
            MATERIAL_RETIRED_KEPT,
            retiredResult[N_RETIRED]?.materialId,
        )

        // 科目过滤：请求里带一个 PHYSICS 节点，但 subject=MATH 时不得返回它的材料。
        val acrossSubjects = database.readBestKnowledgeTeachingMaterialPerNode(
            MATH,
            setOf(N_TYPE_01, N_PHYSICS),
        )
        assertEquals(
            "科目过滤：subject=MATH 时物理节点 $N_PHYSICS 不得出现在结果中；" +
                "实际返回 ${acrossSubjects.size} 条，keys=${acrossSubjects.keys}",
            setOf(N_TYPE_01),
            acrossSubjects.keys,
        )
        assertFalse(
            "科目过滤：物理材料不得混入 MATH 查询；" +
                "实际返回 ${acrossSubjects.size} 条，keys=${acrossSubjects.keys}",
            N_PHYSICS in acrossSubjects,
        )
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private suspend fun seedFixture() {
        val sources = SUBJECTS.map(::sourceRecord)
        val nodes = listOf(
            topicNode(TOPIC_MATH, MATH),
            topicNode(TOPIC_PHYSICS, PHYSICS),
        ) + ATOMIC_NODES.map { (subject, nodeId) -> atomicNode(nodeId, subject) }

        val provenanceBindings = nodes.map { node ->
            KnowledgeNodeSourceBindingSeedRecord(
                knowledgeNodeId = node.knowledgeNodeId,
                sourceId = sourceIdFor(node.subject),
                sourceLocator = "locator:${node.knowledgeNodeId}",
                derivationNote = "仪器化夹具的已审校来源",
                reviewedAtEpochMillis = MATERIAL_REVIEWED_AT,
            )
        }

        val materials = MATERIAL_FIXTURES.map(::materialRecord)
        // 同一材料可绑多个节点，但契约要求**恰好一条 PRIMARY**；共享材料因此只在
        // 所有者节点上 PRIMARY，在消费者节点上 SUPPORTING。
        val materialBindings = MATERIAL_BINDINGS.map { binding ->
            KnowledgeTeachingMaterialNodeBindingRecord(
                materialId = binding.materialId,
                knowledgeNodeId = binding.nodeId,
                role = binding.role,
            )
        }

        database.importKnowledgeBase(
            sources = sources,
            nodes = nodes,
            bindings = provenanceBindings,
        )
        database.importKnowledgeTeachingMaterials(
            materials = materials,
            bindings = materialBindings,
            sources = sources,
        )
    }

    private suspend fun candidatesFor(node: String): List<KnowledgeTeachingMaterialRecord> {
        val candidates = database.readKnowledgeTeachingMaterialsForNodes(
            subject = MATH,
            knowledgeNodeIds = setOf(node),
            limit = KnowledgeReadPort.MAX_TEACHING_MATERIAL_CANDIDATES,
        )
        assertTrue(
            "节点 $node 的候选数达到枚举上限 " +
                "${KnowledgeReadPort.MAX_TEACHING_MATERIAL_CANDIDATES}，无法证明最佳材料在候选内",
            candidates.size < KnowledgeReadPort.MAX_TEACHING_MATERIAL_CANDIDATES,
        )
        return candidates
    }

    /** 只取“该节点”的绑定角色；候选 material 可能还绑到别的节点，必须按节点过滤。 */
    private suspend fun bindingRolesFor(
        node: String,
        candidates: List<KnowledgeTeachingMaterialRecord>,
    ): Map<String, String> {
        if (candidates.isEmpty()) return emptyMap()
        val roles = database.readKnowledgeTeachingMaterialNodeBindings(
            candidates.mapTo(hashSetOf()) { it.materialId },
        ).asSequence()
            .filter { it.knowledgeNodeId == node }
            .associate { it.materialId to it.role }
        assertEquals(
            "节点 $node 的候选与绑定行不一一对应；" +
                "候选=${candidates.map { it.materialId }}，绑定=${roles.keys}",
            candidates.mapTo(hashSetOf()) { it.materialId },
            roles.keys,
        )
        return roles
    }

    /** 与 DAO 的 `ORDER BY role → material_type → title → material_id` 逐级一致。 */
    private fun expectedBest(
        candidates: List<KnowledgeTeachingMaterialRecord>,
        roles: Map<String, String>,
    ): String {
        if (candidates.isEmpty()) {
            throw AssertionError("没有候选材料，无法计算该节点的教学最佳")
        }
        return candidates.minWith(
            compareBy<KnowledgeTeachingMaterialRecord> {
                roleRank(roles.getValue(it.materialId))
            }
                .thenBy { typeRank(it.materialType) }
                .thenBy { it.title }
                .thenBy { it.materialId },
        ).materialId
    }

    /**
     * 回归模型：同一材料的角色在所有被请求节点上取 MIN(role)。
     * 只用于自证夹具能区分“按节点各自取最佳”和“按材料全局折叠角色”。
     */
    private fun globalFoldBest(
        candidates: List<KnowledgeTeachingMaterialRecord>,
        minRoleByMaterial: Map<String, Int>,
    ): String {
        if (candidates.isEmpty()) {
            throw AssertionError("没有候选材料，无法计算全局折叠模型的结果")
        }
        return candidates.minWith(
            compareBy<KnowledgeTeachingMaterialRecord> {
                minRoleByMaterial.getValue(it.materialId)
            }
                .thenBy { typeRank(it.materialType) }
                .thenBy { it.title }
                .thenBy { it.materialId },
        ).materialId
    }

    private fun roleRank(role: String): Int = when (role) {
        KnowledgeMaterialNodeRole.PRIMARY.name -> 0
        KnowledgeMaterialNodeRole.SUPPORTING.name -> 1
        else -> 2
    }

    /** 逐值与 `KnowledgeTeachingMaterialDao.readBestTeachingMaterialPerNode` 的 CASE 一致。 */
    private fun typeRank(type: String): Int =
        TEACHING_TYPE_ORDER.indexOf(type).let { if (it >= 0) it else TEACHING_TYPE_ORDER.size }

    private fun candidateSummary(
        candidates: List<KnowledgeTeachingMaterialRecord>,
        roles: Map<String, String>,
    ): String = candidates.take(8).joinToString(
        prefix = "[",
        postfix = if (candidates.size > 8) ", …(${candidates.size})]" else "]",
    ) { "${it.materialId}:${it.materialType}/${roles[it.materialId]}" }

    /**
     * 直接把一条材料标成 RETIRED，用来构造“退役必须被过滤”的确定性夹具。
     *
     * 这是**夹具状态**的构造，不是绕过被测函数：端口读仍走生产 SQL。生产侧退役必须
     * 经内容调和（永不物删），这里没有公开的单行退役写口，直接落库列是唯一不新增生产
     * 机制的办法；仓库内已有同款直写做法（`StudySeedAndroidTestSupport.kt`）。
     */
    private fun retireMaterial(materialId: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.beginTransaction()
            try {
                sqlite.execSQL(
                    "UPDATE knowledge_teaching_material SET status = 'RETIRED' " +
                        "WHERE material_id = ?",
                    arrayOf(materialId),
                )
                sqlite.setTransactionSuccessful()
            } finally {
                sqlite.endTransaction()
            }
        }
    }

    // ------------------------------------------------------------------
    // 记录构造
    // ------------------------------------------------------------------

    private fun sourceRecord(subject: String): KnowledgeSourceSeedRecord =
        KnowledgeSourceSeedRecord(
            sourceId = sourceIdFor(subject),
            subject = subject,
            sourceType = KnowledgeSourceType.AUTHORIZED_EDUCATION_MATERIAL.name,
            title = "教学材料分配夹具来源($subject)",
            publisher = null,
            edition = null,
            sourceUri = "https://example.org/teaching-allocation/${subject.lowercase()}",
            licenseStatus = KnowledgeSourceLicenseStatus.PUBLIC_OFFICIAL.name,
            contentFingerprint = sha256Hex("teaching-allocation-source:$subject"),
            importedAtEpochMillis = SOURCE_IMPORTED_AT,
            contentUsePolicy = KnowledgeSourceContentUsePolicy.REVIEWED_SYNTHESIS_ONLY.name,
        )

    private fun topicNode(nodeId: String, subject: String): KnowledgeNodeSeedRecord =
        KnowledgeNodeSeedRecord(
            knowledgeNodeId = nodeId,
            stableCode = "$TAXONOMY:$subject:$nodeId",
            subject = subject,
            displayName = "主题 $nodeId",
            parentKnowledgeNodeId = null,
            taxonomyVersion = TAXONOMY,
            createdAtEpochMillis = NODE_CREATED_AT,
            nodeKind = KnowledgeNodeKind.TOPIC.name,
            granularity = KnowledgeNodeGranularity.TOPIC.name,
            verificationStatus = KnowledgeNodeVerificationStatus.CURATED.name,
        )

    private fun atomicNode(nodeId: String, subject: String): KnowledgeNodeSeedRecord =
        KnowledgeNodeSeedRecord(
            knowledgeNodeId = nodeId,
            stableCode = "$TAXONOMY:$subject:$nodeId",
            subject = subject,
            displayName = "节点 $nodeId",
            parentKnowledgeNodeId = if (subject == PHYSICS) TOPIC_PHYSICS else TOPIC_MATH,
            taxonomyVersion = TAXONOMY,
            createdAtEpochMillis = NODE_CREATED_AT,
            nodeKind = KnowledgeNodeKind.CONCEPT.name,
            granularity = KnowledgeNodeGranularity.ATOMIC.name,
            boundaryMarkdown = "只覆盖 $nodeId，不涉及其他知识点。",
            verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
        )

    private fun materialRecord(fixture: MaterialFixture): KnowledgeTeachingMaterialRecord {
        val base = KnowledgeTeachingMaterialRecord(
            materialId = fixture.materialId,
            stableCode = "$TAXONOMY:${fixture.subject}:teaching:${fixture.materialId}",
            subject = fixture.subject,
            materialType = fixture.materialType,
            title = fixture.title,
            summaryMarkdown = "${fixture.title} 的摘要。",
            applicabilityMarkdown = "${fixture.title} 的适用范围。",
            contentMarkdown = "${fixture.title} 的正文。",
            boundaryMarkdown = "${fixture.title} 的边界。",
            derivationKind = KnowledgeMaterialDerivationKind.REVIEWED_SYNTHESIS.name,
            sourceId = sourceIdFor(fixture.subject),
            sourceLocator = "locator:${fixture.materialId}",
            contentFingerprint = "",
            reviewedAtEpochMillis = MATERIAL_REVIEWED_AT,
        )
        return base.copy(contentFingerprint = KnowledgeTeachingMaterialFingerprint.expected(base))
    }

    private fun sourceIdFor(subject: String): String = "source-teaching-allocation-$subject"

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    // ------------------------------------------------------------------
    // 夹具数据
    // ------------------------------------------------------------------

    private data class MaterialFixture(
        val materialId: String,
        val materialType: String,
        val title: String,
        val subject: String = MATH,
    )

    private data class BindingFixture(
        val materialId: String,
        val nodeId: String,
        val role: String,
    )

    private companion object {
        /** 挂死护栏，不是性能门：夹具很小，主要成本是建库与两次写口校验。 */
        const val TIMEOUT_MILLIS = 120_000L

        const val MATH = "MATH"
        const val PHYSICS = "PHYSICS"
        const val TAXONOMY = "test-teaching-allocation"
        const val SOURCE_IMPORTED_AT = 1_000L
        const val NODE_CREATED_AT = 1_000L
        const val MATERIAL_REVIEWED_AT = 2_000L

        val SUBJECTS = listOf(MATH, PHYSICS)

        const val TOPIC_MATH = "test:math:topic"
        const val TOPIC_PHYSICS = "test:physics:topic"

        const val N_TYPE_01 = "test:math:atomic:type-0-1"
        const val N_TYPE_12 = "test:math:atomic:type-1-2"
        const val N_TYPE_23 = "test:math:atomic:type-2-3"
        const val N_TYPE_34 = "test:math:atomic:type-3-4"
        const val N_TYPE_45 = "test:math:atomic:type-4-5"
        const val N_TYPE_56 = "test:math:atomic:type-5-6"
        const val N_ROLE_PRIMARY = "test:math:atomic:role-primary"
        const val N_ROLE_PREREQUISITE = "test:math:atomic:role-prerequisite"
        // 契约要求每条材料恰有一条 PRIMARY 绑定；非 PRIMARY 候选也必须另有一个
        // PRIMARY 归属节点（真实侧车里 SUPPORTING/PREREQUISITE 材料正是这样跨节点绑定）。
        const val N_ROLE_SUPPORTING_OWNER = "test:math:atomic:role-supporting-owner"
        const val N_ROLE_PREREQUISITE_OWNER = "test:math:atomic:role-prerequisite-owner"
        const val N_SHARED_OWNER = "test:math:atomic:shared-owner"
        const val N_SHARED_CONSUMER = "test:math:atomic:shared-consumer"
        const val N_TITLE_TIEBREAK = "test:math:atomic:title-tiebreak"
        const val N_ID_TIEBREAK = "test:math:atomic:id-tiebreak"
        const val N_RETIRED = "test:math:atomic:retired-filter"
        const val N_ZERO = "test:math:atomic:zero-material"
        const val N_PHYSICS = "test:physics:atomic:cross-subject"

        val ATOMIC_NODES: List<Pair<String, String>> = listOf(
            MATH to N_TYPE_01,
            MATH to N_TYPE_12,
            MATH to N_TYPE_23,
            MATH to N_TYPE_34,
            MATH to N_TYPE_45,
            MATH to N_TYPE_56,
            MATH to N_ROLE_PRIMARY,
            MATH to N_ROLE_PREREQUISITE,
            MATH to N_ROLE_SUPPORTING_OWNER,
            MATH to N_ROLE_PREREQUISITE_OWNER,
            MATH to N_SHARED_OWNER,
            MATH to N_SHARED_CONSUMER,
            MATH to N_TITLE_TIEBREAK,
            MATH to N_ID_TIEBREAK,
            MATH to N_RETIRED,
            MATH to N_ZERO,
            PHYSICS to N_PHYSICS,
        )

        val ALL_MATERIAL_NODES: List<String> = listOf(
            N_TYPE_01,
            N_TYPE_12,
            N_TYPE_23,
            N_TYPE_34,
            N_TYPE_45,
            N_TYPE_56,
            N_ROLE_PRIMARY,
            N_ROLE_PREREQUISITE,
            N_SHARED_OWNER,
            N_SHARED_CONSUMER,
            N_TITLE_TIEBREAK,
            N_ID_TIEBREAK,
            N_RETIRED,
        )

        /** 6 个相邻边界，合起来覆盖 DAO 教学序的全部 7 档。 */
        val TYPE_BOUNDARIES: List<Triple<String, String, String>> = listOf(
            Triple(
                N_TYPE_01,
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                KnowledgeTeachingMaterialType.METHOD_MODEL.name,
            ),
            Triple(
                N_TYPE_12,
                KnowledgeTeachingMaterialType.METHOD_MODEL.name,
                KnowledgeTeachingMaterialType.DERIVATION.name,
            ),
            Triple(
                N_TYPE_23,
                KnowledgeTeachingMaterialType.DERIVATION.name,
                KnowledgeTeachingMaterialType.WORKED_EXAMPLE.name,
            ),
            Triple(
                N_TYPE_34,
                KnowledgeTeachingMaterialType.WORKED_EXAMPLE.name,
                KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE.name,
            ),
            Triple(
                N_TYPE_45,
                KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE.name,
                KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE.name,
            ),
            Triple(
                N_TYPE_56,
                KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE.name,
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
            ),
        )

        const val MATERIAL_SHARED = "mat-shared"
        const val MATERIAL_CONSUMER_PRIMARY = "mat-consumer-primary"
        const val MATERIAL_TITLE_WINNER = "mat-title-z"
        const val MATERIAL_ID_WINNER = "mat-id-y"
        const val MATERIAL_RETIRED_BEST = "mat-retired-best"
        const val MATERIAL_RETIRED_KEPT = "mat-retired-kept"

        val MATERIAL_FIXTURES: List<MaterialFixture> = listOf(
            // 6 个相邻类型边界：同一节点内两条 PRIMARY 候选，只有类型秩不同。
            MaterialFixture(
                "mat-type-01-lower",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "A 概念讲解 01",
            ),
            MaterialFixture(
                "mat-type-01-higher",
                KnowledgeTeachingMaterialType.METHOD_MODEL.name,
                "B 方法模型 01",
            ),
            MaterialFixture(
                "mat-type-12-lower",
                KnowledgeTeachingMaterialType.METHOD_MODEL.name,
                "A 方法模型 12",
            ),
            MaterialFixture(
                "mat-type-12-higher",
                KnowledgeTeachingMaterialType.DERIVATION.name,
                "B 推导 12",
            ),
            MaterialFixture(
                "mat-type-23-lower",
                KnowledgeTeachingMaterialType.DERIVATION.name,
                "A 推导 23",
            ),
            MaterialFixture(
                "mat-type-23-higher",
                KnowledgeTeachingMaterialType.WORKED_EXAMPLE.name,
                "B 例题 23",
            ),
            MaterialFixture(
                "mat-type-34-lower",
                KnowledgeTeachingMaterialType.WORKED_EXAMPLE.name,
                "A 例题 34",
            ),
            MaterialFixture(
                "mat-type-34-higher",
                KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE.name,
                "B 表征 34",
            ),
            MaterialFixture(
                "mat-type-45-lower",
                KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE.name,
                "A 表征 45",
            ),
            MaterialFixture(
                "mat-type-45-higher",
                KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE.name,
                "B 误区 45",
            ),
            MaterialFixture(
                "mat-type-56-lower",
                KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE.name,
                "A 误区 56",
            ),
            MaterialFixture(
                "mat-type-56-higher",
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
                "B 完整解答 56",
            ),

            // 角色优先：PRIMARY 的类型秩更差，仍必须先于 SUPPORTING / PREREQUISITE。
            MaterialFixture(
                "mat-role-primary",
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
                "A 主绑定完整解答",
            ),
            MaterialFixture(
                "mat-role-supporting",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "B 支撑概念",
            ),
            MaterialFixture(
                "mat-role-prereq-primary",
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
                "A 主绑定完整解答 2",
            ),
            MaterialFixture(
                "mat-role-prerequisite",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "B 前置概念",
            ),

            // 跨节点共享材料：按节点看角色不同，按材料全局折叠会误判。
            MaterialFixture(
                MATERIAL_SHARED,
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "A 共享概念",
            ),
            MaterialFixture(
                MATERIAL_CONSUMER_PRIMARY,
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
                "B 消费者主绑定完整解答",
            ),
            MaterialFixture(
                "mat-owner-other",
                KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
                "B 拥有者完整解答",
            ),

            // 平局：先 title 再 material_id。
            // 插入顺序故意与最终胜者相反（title 胜者 mat-title-z 后插入），
            // 这样“只按插入顺序返回”的实现会被测试抓住。
            MaterialFixture(
                "mat-title-a",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "B 同角色同类型按标题",
            ),
            MaterialFixture(
                "mat-title-z",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "A 同角色同类型按标题",
            ),
            MaterialFixture(
                "mat-id-z",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "同标题按 id",
            ),
            MaterialFixture(
                "mat-id-y",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "同标题按 id",
            ),

            // RETIRED 过滤：退役当前最佳后必须回落到下一条。
            MaterialFixture(
                MATERIAL_RETIRED_BEST,
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "A 将退役",
            ),
            MaterialFixture(
                MATERIAL_RETIRED_KEPT,
                KnowledgeTeachingMaterialType.METHOD_MODEL.name,
                "B 保留",
            ),

            // 科目过滤：物理材料不得在 subject=MATH 的查询里出现。
            MaterialFixture(
                "mat-physics",
                KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
                "物理概念",
                subject = PHYSICS,
            ),
        )

        val MATERIAL_BINDINGS: List<BindingFixture> = listOf(
            BindingFixture("mat-type-01-lower", N_TYPE_01, "PRIMARY"),
            BindingFixture("mat-type-01-higher", N_TYPE_01, "PRIMARY"),
            BindingFixture("mat-type-12-lower", N_TYPE_12, "PRIMARY"),
            BindingFixture("mat-type-12-higher", N_TYPE_12, "PRIMARY"),
            BindingFixture("mat-type-23-lower", N_TYPE_23, "PRIMARY"),
            BindingFixture("mat-type-23-higher", N_TYPE_23, "PRIMARY"),
            BindingFixture("mat-type-34-lower", N_TYPE_34, "PRIMARY"),
            BindingFixture("mat-type-34-higher", N_TYPE_34, "PRIMARY"),
            BindingFixture("mat-type-45-lower", N_TYPE_45, "PRIMARY"),
            BindingFixture("mat-type-45-higher", N_TYPE_45, "PRIMARY"),
            BindingFixture("mat-type-56-lower", N_TYPE_56, "PRIMARY"),
            BindingFixture("mat-type-56-higher", N_TYPE_56, "PRIMARY"),

            BindingFixture("mat-role-primary", N_ROLE_PRIMARY, "PRIMARY"),
            BindingFixture("mat-role-supporting", N_ROLE_PRIMARY, "SUPPORTING"),
            BindingFixture("mat-role-supporting", N_ROLE_SUPPORTING_OWNER, "PRIMARY"),
            BindingFixture("mat-role-prereq-primary", N_ROLE_PREREQUISITE, "PRIMARY"),
            BindingFixture("mat-role-prerequisite", N_ROLE_PREREQUISITE, "PREREQUISITE"),
            BindingFixture("mat-role-prerequisite", N_ROLE_PREREQUISITE_OWNER, "PRIMARY"),

            BindingFixture(MATERIAL_SHARED, N_SHARED_OWNER, "PRIMARY"),
            BindingFixture(MATERIAL_SHARED, N_SHARED_CONSUMER, "SUPPORTING"),
            BindingFixture(MATERIAL_CONSUMER_PRIMARY, N_SHARED_CONSUMER, "PRIMARY"),
            BindingFixture("mat-owner-other", N_SHARED_OWNER, "PRIMARY"),

            BindingFixture("mat-title-a", N_TITLE_TIEBREAK, "PRIMARY"),
            BindingFixture("mat-title-z", N_TITLE_TIEBREAK, "PRIMARY"),
            BindingFixture("mat-id-z", N_ID_TIEBREAK, "PRIMARY"),
            BindingFixture("mat-id-y", N_ID_TIEBREAK, "PRIMARY"),

            BindingFixture(MATERIAL_RETIRED_BEST, N_RETIRED, "PRIMARY"),
            BindingFixture(MATERIAL_RETIRED_KEPT, N_RETIRED, "PRIMARY"),

            BindingFixture("mat-physics", N_PHYSICS, "PRIMARY"),
        )

        /** 与 DAO 的类型 CASE 逐值一致；顺序本身就是被测契约的一部分。 */
        val TEACHING_TYPE_ORDER: List<String> = listOf(
            KnowledgeTeachingMaterialType.CONCEPT_EXPLANATION.name,
            KnowledgeTeachingMaterialType.METHOD_MODEL.name,
            KnowledgeTeachingMaterialType.DERIVATION.name,
            KnowledgeTeachingMaterialType.WORKED_EXAMPLE.name,
            KnowledgeTeachingMaterialType.REPRESENTATION_GUIDE.name,
            KnowledgeTeachingMaterialType.MISCONCEPTION_GUIDE.name,
            KnowledgeTeachingMaterialType.COMPLETE_SOLUTION.name,
        )
    }
}
