package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-3 召回路径的范围闭合（批次 50）：召回结果「matched 优先、父 topic 随后」**不得**把
 * 已退役（RETIRED）或未审校（MODEL_CANDIDATE）的父节点重新带回来。
 *
 * 修复前的失败形态：父节点用 `readKnowledgeNodesByIds` 取行，该查询**刻意不过滤**
 * RETIRED/verification（它是历史解释路径——学生的旧标签、旧掌握度要能解释，见
 * `ProblemOrganizationDao.kt` 该函数 KDoc 与「当前工作 vs 历史解释」分界注释）。
 * 召回是**当前工作**路径，其 matched 查询本身带
 * `subject + 可信 + status != 'RETIRED'` 三道过滤；父节点却绕过这三道，于是退役主题 /
 * 模型候选主题会随 active 子节点一起被披露给上层（提示词候选菜单、上下文）。
 *
 * 本类钉两条：① 正常父节点仍随子节点返回（修复不能把功能删掉）；② 退役父节点与
 * 模型候选父节点**不得**出现在召回结果里。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeRecallParentScopeInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private var store: RoomStudyDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "kb-recall-parent-scope-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        store?.close()
        context.deleteDatabase(databaseName)
    }

    private fun open(): RoomStudyDatabase {
        val opened = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
        return RoomStudyDatabase(opened.database).also { store = it }
    }

    @Test
    fun retiredAndModelCandidateParentsAreNotDisclosedByRecall() = runBlocking {
        val store = open()
        val dao = store.database.problemOrganizationDao()

        val activeTopic = node(
            id = ACTIVE_TOPIC_ID,
            name = "函数性质（正常主题）",
            granularity = KnowledgeNodeGranularity.TOPIC,
            verification = KnowledgeNodeVerificationStatus.CURATED,
            status = "ACTIVE",
        )
        val retiredTopic = node(
            id = RETIRED_TOPIC_ID,
            name = "函数性质（已退役主题）",
            granularity = KnowledgeNodeGranularity.TOPIC,
            verification = KnowledgeNodeVerificationStatus.CURATED,
            status = "RETIRED",
        )
        val candidateTopic = node(
            id = CANDIDATE_TOPIC_ID,
            name = "函数性质（模型候选主题）",
            granularity = KnowledgeNodeGranularity.TOPIC,
            verification = KnowledgeNodeVerificationStatus.MODEL_CANDIDATE,
            status = "ACTIVE",
        )
        val activeChild = node(
            id = ACTIVE_CHILD_ID,
            name = "判断函数单调性（正常主题下）",
            granularity = KnowledgeNodeGranularity.ATOMIC,
            verification = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
            status = "ACTIVE",
            parentId = activeTopic.knowledgeNodeId,
        )
        val retiredChild = node(
            id = RETIRED_CHILD_ID,
            name = "判断函数单调性（退役主题下）",
            granularity = KnowledgeNodeGranularity.ATOMIC,
            verification = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
            status = "ACTIVE",
            parentId = retiredTopic.knowledgeNodeId,
        )
        val candidateChild = node(
            id = CANDIDATE_CHILD_ID,
            name = "判断函数单调性（候选主题下）",
            granularity = KnowledgeNodeGranularity.ATOMIC,
            verification = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED,
            status = "ACTIVE",
            parentId = candidateTopic.knowledgeNodeId,
        )

        val nodes = listOf(
            activeTopic, retiredTopic, candidateTopic,
            activeChild, retiredChild, candidateChild,
        )
        dao.insertKnowledgeNodes(nodes)
        dao.insertKnowledgeSearchFeatures(nodes.flatMap(KnowledgeNodeEntity::toSearchFeatures))

        val recalled = store.readSubjectKnowledgeRecallCandidates(
            subject = "MATH",
            searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion("判断函数单调性"),
            limit = 64,
            queryText = null,
        ).candidates.map(KnowledgeNodeSeedRecord::knowledgeNodeId)

        // 夹具退化保护：三个 active 子节点都必须被 matched 召回（否则下面的「父节点不在」
        // 断言会因为子节点根本没命中而空转）。
        assertTrue("夹具退化：正常主题下的子节点未被召回；ids=$recalled", recalled.contains(ACTIVE_CHILD_ID))
        assertTrue("夹具退化：退役主题下的子节点未被召回；ids=$recalled", recalled.contains(RETIRED_CHILD_ID))
        assertTrue("夹具退化：候选主题下的子节点未被召回；ids=$recalled", recalled.contains(CANDIDATE_CHILD_ID))

        // 正常父节点仍随子节点返回（修复不得把「父 topic 随后」整条删掉）。
        assertTrue(
            "正常父节点应随 active 子节点返回；ids=$recalled",
            recalled.contains(ACTIVE_TOPIC_ID),
        )
        assertFalse(
            "召回是当前工作路径：已退役父节点不得随 active 子节点被披露；ids=$recalled",
            recalled.contains(RETIRED_TOPIC_ID),
        )
        assertFalse(
            "召回是当前工作路径：MODEL_CANDIDATE 父节点不得被披露；ids=$recalled",
            recalled.contains(CANDIDATE_TOPIC_ID),
        )
    }

    private fun node(
        id: String,
        name: String,
        granularity: KnowledgeNodeGranularity,
        verification: KnowledgeNodeVerificationStatus,
        status: String,
        parentId: String? = null,
    ) = KnowledgeNodeEntity(
        knowledgeNodeId = id,
        stableCode = "d3-recall-scope:math:${id.substringAfterLast(':')}",
        subject = "MATH",
        displayName = name,
        canonicalName = name,
        nodeKind = if (granularity == KnowledgeNodeGranularity.TOPIC) {
            KnowledgeNodeKind.TOPIC.name
        } else {
            KnowledgeNodeKind.REASONING.name
        },
        granularity = granularity.name,
        aliasesText = "",
        boundaryMarkdown = "只判断给定函数在指定区间上的单调性。",
        verificationStatus = verification.name,
        parentKnowledgeNodeId = parentId,
        taxonomyVersion = "d3-recall-scope-test-v1",
        createdAtEpochMillis = 1_000,
        status = status,
    )

    private companion object {
        const val ACTIVE_TOPIC_ID = "topic:active"
        const val RETIRED_TOPIC_ID = "topic:retired"
        const val CANDIDATE_TOPIC_ID = "topic:candidate"
        const val ACTIVE_CHILD_ID = "atomic:active-child"
        const val RETIRED_CHILD_ID = "atomic:retired-child"
        const val CANDIDATE_CHILD_ID = "atomic:candidate-child"
    }
}
