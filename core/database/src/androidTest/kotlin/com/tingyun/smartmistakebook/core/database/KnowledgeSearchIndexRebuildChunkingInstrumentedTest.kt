package com.tingyun.smartmistakebook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeKind
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KD-51（2026-10-17）：整科重建的**分块短写事务**。
 *
 * 消灭的失败：改前重建是 `COUNT + 整科 DELETE + 一次性 INSERT 全科特征行` 一条长写事务，
 * 在全库规模（设备台账 623,774 行 / 4,187 节点）下把 room3 的唯一写连接占满整段重建，
 * 同进程别的写排到 270s。分块的**终点状态与不分块逐位相同**，所以它只能靠"提交了几个写块"
 * 来钉——`knowledge_search_feature` / `knowledge_search_index_state` 里没有这个信息。
 * 本类用 [KnowledgeSearchIndexBuilder] 的 `onWriteCommitted` 缝（默认空实现，生产不观察）：
 *
 * 1. [aColdRebuildOfALargeSubjectIsSplitIntoSeveralWriteChunks]：大科被切成 >1 个写块，
 *    锚点是最后一个写块，行集与抽取器直算逐位相同；
 * 2. [aWarmRebuildDeletesAndRewritesInSeveralChunks]：整科换血的**删除**同样分块、重复重建幂等；
 * 3. [cancellingAtAChunkBoundaryLeavesNoAnchorAndARerunConverges]：块间取消 → 已提交的块留下、
 *    **锚点不写**、重跑收敛（"未完成不写锚点"）；
 * 4. [aPartialRebuildUnderACurrentAnchorIsRepairedByTheReadPath]：分块引入的另一种中间态
 *    （锚点本已当前、索引只写了一半）**不会被当成完整**——读路径的「已索引 ≥ 已审校」门
 *    会判不完整并只补缺补回来（这条同时把"读路径自愈一字未改"钉住）。
 *
 * 夹具 130 个节点（> 分块上限 64）：节点数若不大于上限，用例 1 会红——那正是要拦的形态
 * （单块重建就是 KD-51 的改前形态）。直接用 DAO 造节点，不走内容调和链：本类只测重建，
 * 调和链有自己的仪器化用例（`KnowledgeContentReconciliationInstrumentedTest`）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchIndexRebuildChunkingInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private var store: RoomStudyDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "kb-index-rebuild-chunk-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        store?.close()
        context.deleteDatabase(databaseName)
    }

    private fun open(): RoomStudyDatabase =
        (StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase).also { store = it }

    @Test
    fun aColdRebuildOfALargeSubjectIsSplitIntoSeveralWriteChunks() = runBlocking {
        val opened = open()
        opened.database.problemOrganizationDao().insertKnowledgeNodes(fixtureNodes())
        val writes = mutableListOf<KnowledgeSearchIndexRebuildWrite>()
        val builder = KnowledgeSearchIndexBuilder(
            opened.database,
            KnowledgeSearchIndexCompleteness(),
        ) { writes += it }

        builder.rebuildSubject(SUBJECT)

        assertEquals(
            "锚点必须是最后一个写块（锚点当前 ⟺ 这次重建完整跑完）",
            KnowledgeSearchIndexRebuildWrite.ANCHOR,
            writes.last(),
        )
        assertEquals(
            "冷重建时索引本来是空的，不该产生删除块",
            0,
            writes.count { it == KnowledgeSearchIndexRebuildWrite.DELETE },
        )
        assertTrue(
            "$FIXTURE_NODE_COUNT 个节点的科目必须切成 >1 个插入块（一条长事务正是 KD-51 的形态）；" +
                "实际插入块=${writes.count { it == KnowledgeSearchIndexRebuildWrite.INSERT }}",
            writes.count { it == KnowledgeSearchIndexRebuildWrite.INSERT } > 1,
        )
        assertEquals(
            "分块只改事务边界：行集必须与抽取器直算逐位相同",
            expectedFeatureRows(),
            storedFeatureRows(),
        )
        assertEquals(
            "整科重建跑完必须推进锚点",
            KnowledgeSearchFeatureExtractor.INDEX_VERSION,
            anchorVersion(),
        )
    }

    @Test
    fun aWarmRebuildDeletesAndRewritesInSeveralChunks() = runBlocking {
        val opened = open()
        opened.database.problemOrganizationDao().insertKnowledgeNodes(fixtureNodes())
        // 先完整跑一次，把库变成"已有整科索引"的形态——第二次重建才有删除相可走。
        KnowledgeSearchIndexBuilder(opened.database).rebuildSubject(SUBJECT)
        val before = storedFeatureRows()

        val writes = mutableListOf<KnowledgeSearchIndexRebuildWrite>()
        val builder = KnowledgeSearchIndexBuilder(
            opened.database,
            KnowledgeSearchIndexCompleteness(),
        ) { writes += it }
        builder.rebuildSubject(SUBJECT)

        assertTrue(
            "整科换血的删除也要分块（旧实现是一条 DELETE 删完）；实际删除块=" +
                writes.count { it == KnowledgeSearchIndexRebuildWrite.DELETE },
            writes.count { it == KnowledgeSearchIndexRebuildWrite.DELETE } > 1,
        )
        assertTrue(
            "插入同样分块；实际插入块=${writes.count { it == KnowledgeSearchIndexRebuildWrite.INSERT }}",
            writes.count { it == KnowledgeSearchIndexRebuildWrite.INSERT } > 1,
        )
        assertEquals(
            "锚点仍是最后一个写块",
            KnowledgeSearchIndexRebuildWrite.ANCHOR,
            writes.last(),
        )
        assertEquals(
            "同一内容重复重建必须幂等：分块删除 + 重插后行集逐位不变",
            before,
            storedFeatureRows(),
        )
    }

    @Test
    fun cancellingAtAChunkBoundaryLeavesNoAnchorAndARerunConverges() = runBlocking {
        val opened = open()
        opened.database.problemOrganizationDao().insertKnowledgeNodes(fixtureNodes())
        var insertChunksSeen = 0
        val cancelling = KnowledgeSearchIndexBuilder(
            opened.database,
            KnowledgeSearchIndexCompleteness(),
        ) { kind ->
            if (kind == KnowledgeSearchIndexRebuildWrite.INSERT && ++insertChunksSeen == 1) {
                throw CancellationException("kd51-test: 块间取消")
            }
        }

        try {
            cancelling.rebuildSubject(SUBJECT)
            fail("块间取消必须从 rebuildSubject 原样传出（不得被吞掉后继续写完）")
        } catch (cancelled: CancellationException) {
            assertEquals("取消点应是第 1 个插入块提交之后", 1, insertChunksSeen)
        }

        assertNull(
            "没跑完的重建不得写锚点（锚点当前 ⟺ 跑完了，见 KnowledgeSearchIndexBuilder 的 KDoc）",
            anchorVersion(),
        )
        val partial = storedFeatureRows()
        assertTrue(
            "取消前已提交的块必须留在库里（分块提交不回滚）——否则本用例证明不了\"块间\"",
            partial.isNotEmpty(),
        )
        assertTrue(
            "取消在第 1 块：库里不该已经有完整行集；实际 ${partial.size} / ${expectedFeatureRows().size}",
            partial.size < expectedFeatureRows().size,
        )

        KnowledgeSearchIndexBuilder(opened.database, KnowledgeSearchIndexCompleteness())
            .rebuildSubject(SUBJECT)
        assertEquals(
            "重跑必须收敛到与一次跑完相同的行集",
            expectedFeatureRows(),
            storedFeatureRows(),
        )
        assertEquals(
            "重跑跑完才推进锚点",
            KnowledgeSearchFeatureExtractor.INDEX_VERSION,
            anchorVersion(),
        )
    }

    @Test
    fun aPartialRebuildUnderACurrentAnchorIsRepairedByTheReadPath() = runBlocking {
        val opened = open()
        opened.database.problemOrganizationDao().insertKnowledgeNodes(fixtureNodes())
        // ① 先完整跑一次：锚点落成"当前"（安装期换血 / 节点改名后的现实形态）。
        KnowledgeSearchIndexBuilder(opened.database).rebuildSubject(SUBJECT)
        val expected = expectedFeatureRows()

        // ② 再触发一次重建，在第 1 个插入块之后取消：删除相已清掉旧行，锚点仍是"当前"值。
        var insertChunksSeen = 0
        val cancelling = KnowledgeSearchIndexBuilder(
            opened.database,
            KnowledgeSearchIndexCompleteness(),
        ) { kind ->
            if (kind == KnowledgeSearchIndexRebuildWrite.INSERT && ++insertChunksSeen == 1) {
                throw CancellationException("kd51-test: 块间取消")
            }
        }
        try {
            cancelling.rebuildSubject(SUBJECT)
            fail("块间取消必须从 rebuildSubject 原样传出")
        } catch (cancelled: CancellationException) {
            assertEquals(1, insertChunksSeen)
        }

        assertEquals(
            "锚点本已当前，取消不会把它摘掉——这正是分块新引入的中间态",
            KnowledgeSearchFeatureExtractor.INDEX_VERSION,
            anchorVersion(),
        )
        val partial = storedFeatureRows()
        assertTrue(
            "取消在第 1 块：库里的索引此刻是残缺的；实际 ${partial.size} / ${expected.size}",
            partial.size < expected.size,
        )
        val missingNodeId = expected.minus(partial).first().third
        val missingNode = fixtureNodes().single { it.knowledgeNodeId == missingNodeId }
        val queryFeatures = KnowledgeSearchFeatureExtractor
            .fromNode(missingNode.toSeedRecord())
            .take(64)
            .toSet()

        // ③ 读路径（真实召回入口）：锚点当前 + 计数不满足「已索引 ≥ 已审校」⇒ 只补缺。
        val recalled = opened.readSubjectKnowledgeRecallCandidates(
            subject = SUBJECT,
            searchFeatures = queryFeatures,
            limit = FIXTURE_NODE_COUNT,
        )
        assertTrue(
            "补缺后这个节点必须真的可召回（残缺状态下它一条特征行都没有）；node=$missingNodeId",
            recalled.any { it.knowledgeNodeId == missingNodeId },
        )
        assertEquals(
            "读路径必须把它补回来（或下一次整科重建收敛），行集与一次跑完逐位相同",
            expected,
            storedFeatureRows(),
        )
        assertEquals(
            "锚点保持当前（补缺走的是读路径的写事务，不推进锚点）",
            KnowledgeSearchFeatureExtractor.INDEX_VERSION,
            anchorVersion(),
        )
    }

    // ---- 夹具与直读 ----

    private fun fixtureNodes(): List<KnowledgeNodeEntity> = (0 until FIXTURE_NODE_COUNT).map { index ->
        KnowledgeNodeEntity(
            knowledgeNodeId = nodeId(index),
            stableCode = "kb:kd51:math:atomic:$index",
            subject = SUBJECT,
            displayName = "知识点$index",
            canonicalName = "知识点$index",
            nodeKind = KnowledgeNodeKind.REASONING.name,
            granularity = KnowledgeNodeGranularity.ATOMIC.name,
            // 分隔符与 `KnowledgeNodeSeedRecord.toEntity` 成对（CHAR(31)）：别名不参与本例断言，
            // 但要让抽取器真的吐出多特征、行数不是"每节点 1 行"的退化形态。
            aliasesText = "别名${index}甲\u001F别名${index}乙",
            boundaryMarkdown = "只讨论第 $index 个知识点的边界条件，不含题目正文。",
            verificationStatus = KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name,
            parentKnowledgeNodeId = null,
            taxonomyVersion = "kd51-test-v1",
            createdAtEpochMillis = 1_000,
        )
    }

    /** 抽取器直算的行集（subject, feature, node）：分块重建的输出必须与它逐位相同。 */
    private fun expectedFeatureRows(): Set<Triple<String, String, String>> =
        fixtureNodes()
            .flatMap { node ->
                node.toSearchFeatures().map {
                    Triple(it.subject, it.searchFeature, it.knowledgeNodeId)
                }
            }
            .toSet()

    private fun storedFeatureRows(): Set<Triple<String, String, String>> =
        readRaw("SELECT subject, search_feature, knowledge_node_id FROM knowledge_search_feature")
            .map { Triple(it["subject"]!!, it["search_feature"]!!, it["knowledge_node_id"]!!) }
            .toSet()

    private fun anchorVersion(): Int? =
        readRaw("SELECT index_version FROM knowledge_search_index_state WHERE subject = '$SUBJECT'")
            .firstOrNull()
            ?.get("index_version")
            ?.toInt()

    private fun readRaw(sql: String): List<Map<String, String?>> {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            database.rawQuery(sql, null).use { cursor ->
                val rows = mutableListOf<Map<String, String?>>()
                while (cursor.moveToNext()) {
                    val row = mutableMapOf<String, String?>()
                    for (i in 0 until cursor.columnCount) {
                        row[cursor.getColumnName(i)] = cursor.getString(i)
                    }
                    rows += row
                }
                return rows
            }
        }
    }

    private companion object {
        const val SUBJECT = "MATH"

        /** > 分块上限（64）：用例 1/3 靠它证明"被切成多块"。 */
        const val FIXTURE_NODE_COUNT = 130

        fun nodeId(index: Int) = "kb-node:kd51:$index"
    }
}
