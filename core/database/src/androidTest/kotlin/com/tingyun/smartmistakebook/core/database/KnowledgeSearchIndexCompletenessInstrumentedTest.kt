package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceLicenseStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeSourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ④-6（K1 批 1）：检索索引「已核验完整」判定缓存的端到端门（生产写路径 + 生产召回路径）。
 *
 * 消灭的失败：`ensureKnowledgeSearchIndex` 每次召回白付两条 COUNT（S19 ≈12.6ms/次）。本类钉三件事：
 *
 * 1. **计数断言（不是计时）**：首次（冷）召回真的下发版本读取 + 两条 COUNT（正控制，
 *    防断言空转）；第二次及以后命中缓存，**零判定 SQL**（连版本锚点都不读）。
 *    手段是 internal 计数缝 [KnowledgeSearchIndexCompleteness.issuedVerificationQueries]——
 *    room3 3.0.0 已核实**没有** `setQueryCallback`（`javap androidx.room3.RoomDatabase$Builder`
 *    只有 `setQueryCoroutineContext`），故按计划回退到计数缝。
 * 2. **负向（正确性核心）**：内容安装新节点 / 用户整理确认新 USER_CONFIRMED 节点之后，
 *    同进程的下一次召回**必须立即可见**。整理确认这条路径不建特征行（靠读时自愈补），
 *    是"失效漏接线 → 召回漏新装节点"的唯一现实入口——`missingInvalidationWireCanary…`
 *    用测试开关摘下失效接线，断言同一场景下新节点**不可见**，证明主用例真的对失效敏感。
 * 3. **重建/安装不回退**：换版锚点缺失时整科重建照跑、安装期预热成果保持（不新增
 *    写侧行为，只加判定缓存与失效）。
 *
 * 夹具是每个用例一个全新命名库文件（判定缓存按数据库实例隔离，天然用例隔离）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchIndexCompletenessInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private var store: RoomStudyDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "kb-search-index-completeness-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        store?.close()
        context.deleteDatabase(databaseName)
    }

    /**
     * 用**注入的**缓存实例包一层同一底层库：读路径（召回）与三条写路径（内容安装 /
     * 整科重建 / 整理确认）都从 `RoomStudyDatabase` 拿到同一个实例——注入点即生产装配点。
     */
    private fun open(
        cache: KnowledgeSearchIndexCompleteness = KnowledgeSearchIndexCompleteness(),
    ): RoomStudyDatabase {
        val opened = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
        return RoomStudyDatabase(opened.database, searchIndexCompleteness = cache).also {
            store = it
        }
    }

    private suspend fun recall(store: RoomStudyDatabase, text: String): List<String> =
        store.readSubjectKnowledgeRecallCandidates(
            subject = "MATH",
            searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion(text),
            limit = 64,
            queryText = text,
        ).map(KnowledgeNodeSeedRecord::knowledgeNodeId)

    @Test
    fun firstColdRecallRunsTheGateAndLaterRecallsIssueNoVerificationQueries() = runBlocking {
        val cache = KnowledgeSearchIndexCompleteness()
        val store = open(cache)
        store.applyKnowledgeContentUpdate(contentCommand(nodes = listOf(topic(), atomic("判断函数单调性"))))
        cache.issuedVerificationQueries.clear()

        val cold = recall(store, "判断函数单调性")
        assertTrue("夹具退化：冷召回没有召回已安装节点；ids=$cold", cold.contains(ATOMIC_ID))
        val coldQueries = cache.issuedVerificationQueries.toList()
        assertTrue(
            "正控制失败：冷召回没有下发两条 COUNT（计数缝与判定路径脱节）；实际=$coldQueries",
            coldQueries.contains(SearchIndexVerificationQueries.COUNT_REVIEWED) &&
                coldQueries.contains(SearchIndexVerificationQueries.COUNT_INDEXED),
        )

        cache.issuedVerificationQueries.clear()
        val warm = List(RECALL_REPEAT) { recall(store, "判断函数单调性") }
        warm.forEach { ids ->
            assertEquals("命中缓存后召回结果不得变化", cold, ids)
        }
        assertTrue(
            "第二次及以后的召回必须命中缓存：零判定 SQL（含 countReviewed…/countIndexed…）；" +
                "实际=${cache.issuedVerificationQueries.toList()}",
            cache.issuedVerificationQueries.isEmpty(),
        )
    }

    /**
     * 负向主用例：安装新节点后同进程召回必须立即可见。
     *
     * 注意本用例的敏感面：安装路径自身会（经 `KnowledgeSearchIndexBuilder`）重建索引，
     * 所以它主要钉"安装 → 重建 → 可见"的链路不因缓存/失序而回退；对"失效接线"的敏感
     * 性由 [userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation] 与
     * canary 用例钉住。
     */
    @Test
    fun contentInstallKeepsNewNodesImmediatelyRecallable() = runBlocking {
        val store = open()
        store.applyKnowledgeContentUpdate(contentCommand(nodes = listOf(topic(), atomic("判断函数单调性"))))
        val before = recall(store, "判断函数单调性")
        assertTrue("夹具退化：安装后首次召回没有节点；ids=$before", before.contains(ATOMIC_ID))

        store.applyKnowledgeContentUpdate(
            contentCommand(
                nodes = listOf(topic(), atomic("判断函数单调性"), atomic("函数极值时取法", nodeId = EXTREMUM_ID)),
            ),
        )

        val after = recall(store, "函数极值时取法")
        assertTrue(
            "安装新节点后同进程召回必须立即可见（否则失效接线漏了）；ids=$after",
            after.contains(EXTREMUM_ID),
        )
    }

    /**
     * 负向主用例（对失效接线敏感）：整理确认落 USER_CONFIRMED 知识点后，同进程召回必须立即可见。
     *
     * 现实失败形态：用户确认/纠正知识点 → 节点行落库（USER_CONFIRMED，**无特征行**）→
     * 学生在同一次会话里再讲一道题 → 召回命中判定缓存、跳过只补缺 → 新知识点永远不进
     * 召回索引（复习以知识点为核心展开时它也不存在）。
     */
    @Test
    fun userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation() = runBlocking {
        val store = open()

        val ids = confirmationScenario(store)

        assertTrue(
            "整理确认新知识点后同进程召回必须立即可见（缓存失效漏接线 → 这里会红）；ids=$ids",
            ids.contains(CONFIRMED_BETA_ID),
        )
    }

    /**
     * **canary**：把失效接线摘掉（`invalidationEnabled=false`，测试开关）重跑同一场景——
     * 新确认的节点在召回里**不可见**。它把"主用例的绿色来自失效接线"变成可复跑的断言、
     * 而不是一次手工验证；实现若把失效改成空操作（或接线被摘），canary 先红。
     */
    @Test
    fun missingInvalidationWireCanaryHidesTheNewlyConfirmedNode() = runBlocking {
        val store = open(KnowledgeSearchIndexCompleteness(invalidationEnabled = false))

        val ids = confirmationScenario(store)

        assertFalse(
            "canary 失效：撤掉失效接线后新节点仍可见——主用例的绿色不再能证明接线的存在；ids=$ids",
            ids.contains(CONFIRMED_BETA_ID),
        )
        assertTrue(
            "canary 场景本身退化：连已索引的旧节点都召回不到；ids=$ids",
            ids.contains(CONFIRMED_ALPHA_ID),
        )
    }

    /**
     * 场景（与生产路径逐条同形）：
     * ① 直写题库事实（两道题/两个实践单元）；② 整理确认落 USER_CONFIRMED 知识点 alpha；
     * ③ 两次召回（第一次触发锚点缺失的整科重建，第二次跑判定并落缓存——**缓存此刻起"完整"**）；
     * ④ 第二道题整理确认落新 USER_CONFIRMED 知识点 beta（无特征行）；
     * ⑤ 返回第五步召回的 id 集。
     */
    private suspend fun confirmationScenario(store: RoomStudyDatabase): List<String> {
        store.seedStudyFacts(organizationSeed())

        store.confirmProblemOrganization(
            organizationCommand(
                problemId = PROBLEM_ONE,
                revisionId = REVISION_ONE,
                practiceUnitId = PRACTICE_ONE,
                nodeId = CONFIRMED_ALPHA_ID,
                displayName = "二次函数最值",
                acceptedAtEpochMillis = 2_000,
            ),
        )
        repeat(2) {
            val alpha = recall(store, "二次函数最值")
            assertTrue("夹具退化：alpha 未进召回；ids=$alpha", alpha.contains(CONFIRMED_ALPHA_ID))
        }

        store.confirmProblemOrganization(
            organizationCommand(
                problemId = PROBLEM_TWO,
                revisionId = REVISION_TWO,
                practiceUnitId = PRACTICE_TWO,
                nodeId = CONFIRMED_BETA_ID,
                displayName = "函数极值时取法",
                acceptedAtEpochMillis = 4_000,
            ),
        )
        return recall(store, "函数极值时取法")
    }

    // ------------------------------------------------------------------
    // 内容包夹具（与 KnowledgeSearchIndexInstallInstrumentedTest 同形的最小合法包）
    // ------------------------------------------------------------------

    private fun contentCommand(nodes: List<KnowledgeNodeSeedRecord>) = KnowledgeContentUpdateCommand(
        packId = TAXONOMY_VERSION,
        contentVersion = "test-version",
        nodes = nodes,
        sources = listOf(source()),
        nodeSourceBindings = nodes.map { binding(it.knowledgeNodeId) },
        relations = emptyList(),
        materials = listOf(material()),
        materialBindings = listOf(
            KnowledgeTeachingMaterialNodeBindingRecord(
                materialId = MATERIAL_ID,
                knowledgeNodeId = nodes.last().knowledgeNodeId,
                role = "PRIMARY",
            ),
        ),
        nodeRetirements = emptyMap(),
        teachingSources = listOf(source()),
    )

    private fun source() = KnowledgeSourceSeedRecord(
        sourceId = SOURCE_ID,
        subject = "MATH",
        sourceType = KnowledgeSourceType.MANUAL_RESEARCH.name,
        title = "经审校的函数知识目录",
        publisher = "授权教研机构",
        edition = null,
        sourceUri = "https://example.edu/math/function-index",
        licenseStatus = KnowledgeSourceLicenseStatus.REFERENCE_ONLY.name,
        contentFingerprint = "B".repeat(64),
        importedAtEpochMillis = 1_000,
    )

    private fun topic() = KnowledgeNodeSeedRecord(
        knowledgeNodeId = TOPIC_ID,
        stableCode = "$TAXONOMY_VERSION:math:topic:function",
        subject = "MATH",
        displayName = "函数",
        parentKnowledgeNodeId = null,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = 1_000,
        canonicalName = "函数",
        nodeKind = KnowledgeNodeKind.TOPIC.name,
        granularity = KnowledgeNodeGranularity.TOPIC.name,
        verificationStatus = KnowledgeNodeVerificationStatus.CURATED.name,
    )

    private fun atomic(name: String, nodeId: String = ATOMIC_ID) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = nodeId,
        stableCode = "$TAXONOMY_VERSION:math:atomic:${nodeId.substringAfterLast(':')}",
        subject = "MATH",
        displayName = name,
        parentKnowledgeNodeId = TOPIC_ID,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = 1_000,
        canonicalName = name,
        nodeKind = KnowledgeNodeKind.REASONING.name,
        granularity = KnowledgeNodeGranularity.ATOMIC.name,
        boundaryMarkdown = "只判断给定函数在指定区间上的单调性。",
        verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
    )

    private fun material() = KnowledgeTeachingMaterialRecord(
        materialId = MATERIAL_ID,
        stableCode = "$TAXONOMY_VERSION:math:teaching:$MATERIAL_ID",
        subject = "MATH",
        materialType = "CONCEPT_EXPLANATION",
        title = "单调性的判断",
        summaryMarkdown = "先看定义域，再比较区间内任意两点的函数值。",
        applicabilityMarkdown = "判断给定区间上单调性的题。",
        contentMarkdown = "1. 取定义域内的任意两点。\n2. 比较函数值大小。\n3. 得出单调性。",
        boundaryMarkdown = "只在给定区间上讨论，端点开闭需单独说明。",
        derivationKind = "REVIEWED_SYNTHESIS",
        sourceId = SOURCE_ID,
        sourceLocator = "函数目录·单调性",
        contentFingerprint = "PENDING",
        reviewedAtEpochMillis = 1_000,
    ).let { it.copy(contentFingerprint = KnowledgeTeachingMaterialFingerprint.expected(it)) }

    private fun binding(nodeId: String) = KnowledgeNodeSourceBindingSeedRecord(
        knowledgeNodeId = nodeId,
        sourceId = SOURCE_ID,
        sourceLocator = "函数目录·单调性",
        derivationNote = "人工核对原始材料后拆分。",
        reviewedAtEpochMillis = 1_000,
    )

    // ------------------------------------------------------------------
    // 整理确认夹具（最小合法命令：CHAPTER + KNOWLEDGE 两维分类，共享 USER_CORRECTED 权威）
    // ------------------------------------------------------------------

    private fun organizationSeed() = StudySeedBundle(
        problems = listOf(
            problem(PROBLEM_ONE, "1".repeat(64)),
            problem(PROBLEM_TWO, "2".repeat(64)),
        ),
        revisions = listOf(
            revision(REVISION_ONE, PROBLEM_ONE, "3".repeat(64)),
            revision(REVISION_TWO, PROBLEM_TWO, "4".repeat(64)),
        ),
        practiceUnits = listOf(
            practice(PRACTICE_ONE, PROBLEM_ONE, REVISION_ONE),
            practice(PRACTICE_TWO, PROBLEM_TWO, REVISION_TWO),
        ),
        errorBookEntries = listOf(
            entry("entry-one", PRACTICE_ONE, PROBLEM_ONE, REVISION_ONE),
            entry("entry-two", PRACTICE_TWO, PROBLEM_TWO, REVISION_TWO),
        ),
    )

    private fun organizationCommand(
        problemId: String,
        revisionId: String,
        practiceUnitId: String,
        nodeId: String,
        displayName: String,
        acceptedAtEpochMillis: Long,
    ) = ConfirmProblemOrganizationCommand(
        commandId = "confirm-$problemId",
        payloadFingerprint = "a".repeat(64),
        problemId = problemId,
        problemRevisionId = revisionId,
        practiceUnitId = practiceUnitId,
        knowledgeNodes = listOf(
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = nodeId,
                stableCode = "math:knowledge:$nodeId",
                subject = "MATH",
                displayName = displayName,
                parentKnowledgeNodeId = null,
                taxonomyVersion = "user-corrected-v1",
                createdAtEpochMillis = acceptedAtEpochMillis,
                // 用户纠正的点是用户权威（生产路径同此；MODEL_CANDIDATE 不进已审校计数）。
                verificationStatus = KnowledgeNodeVerificationStatus.USER_CONFIRMED.name,
            ),
        ),
        knowledgeBindings = listOf(
            KnowledgeBindingSeedRecord(
                bindingId = "binding-$problemId",
                practiceUnitId = practiceUnitId,
                knowledgeNodeId = nodeId,
                basisRevisionId = revisionId,
                strength = 1.0,
                sourceType = "USER_CORRECTED",
                taxonomyVersion = "user-corrected-v1",
                acceptedAtEpochMillis = acceptedAtEpochMillis,
            ),
        ),
        classifications = listOf(
            ProblemClassificationBindingRecord(
                bindingId = "classification-chapter-$problemId",
                problemId = problemId,
                basisRevisionId = revisionId,
                dimension = "CHAPTER",
                labelId = "math:chapter:function",
                displayName = "函数",
                taxonomyVersion = "user-corrected-v1",
                acceptanceSource = "USER_CORRECTED",
                acceptedAtEpochMillis = acceptedAtEpochMillis,
            ),
            ProblemClassificationBindingRecord(
                bindingId = "classification-knowledge-$problemId",
                problemId = problemId,
                basisRevisionId = revisionId,
                dimension = "KNOWLEDGE",
                labelId = "math:knowledge:$nodeId",
                displayName = displayName,
                taxonomyVersion = "user-corrected-v1",
                acceptanceSource = "USER_CORRECTED",
                acceptedAtEpochMillis = acceptedAtEpochMillis,
            ),
        ),
        relations = emptyList(),
        acceptedAtEpochMillis = acceptedAtEpochMillis,
    )

    private fun problem(id: String, fingerprint: String) = ProblemSeedRecord(
        problemId = id,
        canonicalFingerprint = fingerprint,
        subject = "MATH",
        createdAtEpochMillis = 1_000,
    )

    private fun revision(id: String, problemId: String, fingerprint: String) = ProblemRevisionSeedRecord(
        revisionId = id,
        problemId = problemId,
        revisionNumber = 1,
        title = "测试题",
        problemMarkdown = "求函数最值。",
        questionDocumentSnapshot = null,
        answerSpecId = null,
        answerSpecSnapshot = null,
        answerVerificationStatus = "UNKNOWN",
        sourceType = "TEST",
        sourceReference = null,
        contentFingerprint = fingerprint,
        createdAtEpochMillis = 1_000,
    )

    private fun practice(id: String, problemId: String, revisionId: String) = PracticeUnitSeedRecord(
        practiceUnitId = id,
        problemId = problemId,
        problemRevisionId = revisionId,
        unitKey = "unit:$id",
        unitKind = "PROBLEM",
        title = "测试题",
        promptMarkdown = "求函数最值。",
        estimatedSeconds = 180,
        createdAtEpochMillis = 1_000,
    )

    private fun entry(id: String, practiceId: String, problemId: String, revisionId: String) =
        ErrorBookEntrySeedRecord(
            entryId = id,
            practiceUnitId = practiceId,
            problemId = problemId,
            currentRevisionId = revisionId,
            sourceKey = null,
            status = StudyDbValue.ErrorBookStatus.ACTIVE,
            acceptedAtEpochMillis = 1_000,
            updatedAtEpochMillis = 1_000,
        )

    private companion object {
        const val RECALL_REPEAT = 3
        const val TAXONOMY_VERSION = "moe-2025-four-subjects-v1"
        const val SOURCE_ID = "kb-source:math:function-index"
        const val TOPIC_ID = "kb-node:math:function"
        const val ATOMIC_ID = "kb-node:math:monotonicity"
        const val EXTREMUM_ID = "kb-node:math:extremum"
        const val MATERIAL_ID = "kb-material:monotonicity"
        const val PROBLEM_ONE = "problem-completeness-one"
        const val REVISION_ONE = "revision-completeness-one"
        const val PRACTICE_ONE = "practice-completeness-one"
        const val PROBLEM_TWO = "problem-completeness-two"
        const val REVISION_TWO = "revision-completeness-two"
        const val PRACTICE_TWO = "practice-completeness-two"
        const val CONFIRMED_ALPHA_ID = "knowledge:user:alpha"
        const val CONFIRMED_BETA_ID = "knowledge:user:beta"
    }
}
