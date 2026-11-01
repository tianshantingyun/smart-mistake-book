package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeNodeEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSourceEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeTeachingMaterialEntity
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeTeachingMaterialNodeBindingEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeCatalogSearchInstrumentedTest {
    private lateinit var store: RoomStudyDatabase

    @Before fun open() {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
    }

    @After fun close() = store.close()

    @Test fun searchesBeyondFirst256AndDoesNotTruncateAliases() = runBlocking {
        val nodes = (0..299).map { index ->
            node("node-$index", "知识点%03d".format(index)).copy(
                aliasesText = if (index == 299) (0..40).joinToString("\u001F") { "别名$it" } + "\u001F尾部目标" else "",
            )
        }
        store.database.problemOrganizationDao().insertKnowledgeNodes(nodes)
        val page = store.readKnowledgeCatalogPage(listOf("尾部目标"), null, 32)
        assertEquals(listOf("node-299"), page.map { it.node.knowledgeNodeId })
    }

    @Test fun emptyQueryPagesWithoutDroppingOrDuplicatingNodes() = runBlocking {
        store.database.problemOrganizationDao().insertKnowledgeNodes((0..72).map { node("id-$it", "名称%03d".format(it)) })
        val first = store.readKnowledgeCatalogPage(emptyList(), null, 32)
        val second = store.readKnowledgeCatalogPage(emptyList(), first.last().position, 32)
        val third = store.readKnowledgeCatalogPage(emptyList(), second.last().position, 32)
        assertEquals(73, (first + second + third).map { it.node.knowledgeNodeId }.distinct().size)
        assertEquals(9, third.size)
    }

    @Test fun duplicateNamesAcrossSubjectsRemainDistinctAndUnreviewedNodesAreExcluded() = runBlocking {
        store.database.problemOrganizationDao().insertKnowledgeNodes(listOf(
            node("math", "共同名称"), node("physics", "共同名称").copy(subject = "PHYSICS"),
            node("retired", "共同名称").copy(status = "RETIRED"),
            node("candidate", "共同名称").copy(verificationStatus = "MODEL_CANDIDATE"),
        ))
        assertEquals(setOf("math", "physics"), store.readKnowledgeCatalogPage(listOf("共同名称"), null, 32)
            .map { it.node.knowledgeNodeId }.toSet())
    }

    @Test fun wildcardInputIsLiteralAndMultipleTermsAreRanked() = runBlocking {
        store.database.problemOrganizationDao().insertKnowledgeNodes(listOf(
            node("literal", "百分比%_\\"), node("both", "函数最值"), node("one", "函数"),
        ))
        assertEquals(listOf("literal"), store.readKnowledgeCatalogPage(listOf("%_\\"), null, 32)
            .map { it.node.knowledgeNodeId })
        val page = store.readKnowledgeCatalogPage(listOf("函数", "最值"), null, 32)
        assertEquals("both", page.first().node.knowledgeNodeId)
        assertTrue(page.first().position.score > page.last().position.score)
    }

    @Test fun completeTeachingParagraphIsSearchableButUnlicensedExcerptAndRetiredMaterialAreNot() = runBlocking {
        val dao = store.database.problemOrganizationDao()
        dao.insertKnowledgeNodes(listOf(node("material-node", "未含查询词的节点")))
        dao.insertKnowledgeSources(listOf(KnowledgeSourceEntity(
            sourceId = "source", subject = "MATH", sourceType = "TEXTBOOK", title = "教材",
            publisher = null, edition = null, sourceUri = null, licenseStatus = "UNKNOWN",
            contentFingerprint = "a".repeat(64), importedAtEpochMillis = 1,
        )))
        val material = KnowledgeTeachingMaterialEntity(
            materialId = "material", stableCode = "material", subject = "MATH", materialType = "CONCEPT_EXPLANATION",
            title = "知识描述", summaryMarkdown = "", applicabilityMarkdown = "", boundaryMarkdown = "",
            contentMarkdown = "普通段落。".repeat(800) + "句中关键词出现在全文尾部。",
            derivationKind = "REVIEWED_SYNTHESIS", sourceId = "source", sourceLocator = "第1节",
            contentFingerprint = "b".repeat(64), reviewedAtEpochMillis = 1,
        )
        val materials = store.database.knowledgeTeachingMaterialDao()
        materials.insertMaterials(listOf(material))
        materials.insertBindings(listOf(KnowledgeTeachingMaterialNodeBindingEntity("material", "material-node", "PRIMARY")))
        val found = store.readKnowledgeCatalogPage(listOf("句中关键词"), null, 32)
        assertEquals("material-node", found.single().node.knowledgeNodeId)
        assertTrue(found.single().snippet.contains("句中关键词"))
        materials.upsertMaterials(listOf(material.copy(derivationKind = "LICENSED_EXCERPT")))
        assertTrue(store.readKnowledgeCatalogPage(listOf("句中关键词"), null, 32).isEmpty())
        materials.upsertMaterials(listOf(material.copy(status = "RETIRED")))
        assertTrue(store.readKnowledgeCatalogPage(listOf("句中关键词"), null, 32).isEmpty())
    }

    @Test fun changingNodeContentInvalidatesCatalogEvenWhenRowCountDoesNotChange() = runBlocking {
        val dao = store.database.problemOrganizationDao()
        val original = node("changing", "修改前")
        dao.insertKnowledgeNodes(listOf(original))
        val subscribed = CompletableDeferred<Unit>()
        val changed = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeKnowledgeCatalogChanges().onEach { subscribed.complete(Unit) }.drop(1).first()
        }
        withTimeout(5_000) { subscribed.await() }
        dao.upsertKnowledgeNodes(listOf(original.copy(canonicalName = "修改后")))
        withTimeout(5_000) { changed.await() }
        assertEquals("changing", store.readKnowledgeCatalogPage(listOf("修改后"), null, 32).single().node.knowledgeNodeId)
    }

    @Test fun fullCatalogScaleStillFindsLastTeachingMaterial() = runBlocking {
        val dao = store.database.problemOrganizationDao()
        (0 until 10_000).chunked(500).forEach { indexes ->
            dao.insertKnowledgeNodes(indexes.map { node("scale-$it", "条目%05d".format(it)) })
        }
        dao.insertKnowledgeSources(listOf(KnowledgeSourceEntity(
            sourceId = "scale-source", subject = "MATH", sourceType = "TEXTBOOK", title = "规模夹具",
            publisher = null, edition = null, sourceUri = null, licenseStatus = "UNKNOWN",
            contentFingerprint = "a".repeat(64), importedAtEpochMillis = 1,
        )))
        val materials = store.database.knowledgeTeachingMaterialDao()
        val body = "经过审核的教学说明。".repeat(80)
        (0 until 50_000).chunked(500).forEach { indexes ->
            materials.insertMaterials(indexes.map { index -> KnowledgeTeachingMaterialEntity(
                materialId = "scale-material-$index", stableCode = "scale-material-$index", subject = "MATH",
                materialType = "CONCEPT_EXPLANATION", title = "教学段落", summaryMarkdown = "",
                applicabilityMarkdown = "", boundaryMarkdown = "",
                contentMarkdown = body + if (index == 49_999) "规模查询尾部目标" else "",
                derivationKind = "REVIEWED_SYNTHESIS", sourceId = "scale-source", sourceLocator = "$index",
                contentFingerprint = index.toString(16).padStart(64, '0'), reviewedAtEpochMillis = 1,
            ) })
            materials.insertBindings(indexes.map { index ->
                KnowledgeTeachingMaterialNodeBindingEntity("scale-material-$index", "scale-${index / 5}", "PRIMARY")
            })
        }
        val started = android.os.SystemClock.elapsedRealtime()
        val found = store.readKnowledgeCatalogPage(listOf("规模查询尾部目标"), null, 32)
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        android.util.Log.i("KnowledgeCatalogQA", "nodes=10000 materials=50000 queryMillis=$elapsed hits=${found.size}")
        assertEquals(listOf("scale-9999"), found.map { it.node.knowledgeNodeId })
    }

    private fun node(id: String, name: String) = KnowledgeNodeEntity(
        knowledgeNodeId = id, stableCode = id, subject = "MATH", displayName = name,
        canonicalName = name, nodeKind = "TOPIC", granularity = "ATOMIC", aliasesText = "",
        boundaryMarkdown = null, verificationStatus = "CURATED", parentKnowledgeNodeId = null,
        taxonomyVersion = "catalog-v1", createdAtEpochMillis = 1,
    )
}
