package com.tingyun.smartmistakebook.core.data.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.ContentInstallStateRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeGranularity
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.KnowledgeTeachingMaterialType
import com.tingyun.smartmistakebook.core.model.SubjectKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledKnowledgeBaseInstallerInstrumentedTest {
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

    @Test
    fun installsEverySupportedSubjectWithGroundedAtomsAndIsIdempotent() = runBlocking {
        BundledKnowledgeBaseInstaller.install(database)
        BundledKnowledgeBaseInstaller.install(database)

        SUPPORTED_SUBJECTS.forEach { subject ->
            val nodes = database.readSubjectKnowledgeNodes(subject.name, limit = 256)
            val topics = nodes.filter { it.granularity == KnowledgeNodeGranularity.TOPIC.name }
            val atoms = nodes.filter { it.granularity == KnowledgeNodeGranularity.ATOMIC.name }

            assertTrue("$subject needs a visible topic", topics.isNotEmpty())
            assertTrue("$subject needs grounded atomic abilities", atoms.isNotEmpty())
            assertTrue(atoms.all {
                it.verificationStatus == KnowledgeNodeVerificationStatus.SOURCE_GROUNDED.name
            })
            atoms.forEach { atom ->
                val parent = topics.singleOrNull { it.knowledgeNodeId == atom.parentKnowledgeNodeId }
                assertNotNull("${atom.canonicalName} needs a same-subject topic parent", parent)
            }
            assertEquals(subject.name, nodes.map { it.subject }.distinct().single())
        }

        val coverage = database.observeReviewedKnowledgeCoverage().first()
        assertEquals(SUPPORTED_SUBJECTS.size, coverage.size)
        // 9 科都有已审 topic 与原子知识点（moe-2020 样本 5 科 + moe-2025 四科大包）
        coverage.forEach { record ->
            assertTrue("${record.subject} needs a visible topic", record.topicCount > 0)
            assertTrue(
                "${record.subject} needs grounded atomic abilities",
                record.atomicKnowledgeCount > 0,
            )
            assertTrue("${record.subject} needs a reviewed source", record.reviewedSourceCount > 0)
        }
        assertTrue(coverage.sumOf { it.atomicKnowledgeCount } >= 19)
        val relations = SUPPORTED_SUBJECTS.flatMap { subject ->
            database.readSubjectKnowledgeNodeRelations(subject.name, limit = 64)
        }
        assertTrue(relations.size >= 2)
        assertTrue(relations.all { it.prerequisiteKnowledgeNodeId != it.dependentKnowledgeNodeId })

        val mathNodes = database.readSubjectKnowledgeNodes(SubjectKind.MATH.name, limit = 256)
        val mathNodeIds = mathNodes.mapTo(hashSetOf()) { it.knowledgeNodeId }
        val teachingSupport = database.readKnowledgeTeachingMaterialsForNodes(
            subject = SubjectKind.MATH.name,
            knowledgeNodeIds = mathNodeIds,
            limit = 8,
        )
        assertTrue("math teaching support should be retrievable", teachingSupport.isNotEmpty())
        val tutorReferences = RoomTutorTeachingReferenceRepository(database).referencesFor(
            subject = SubjectKind.MATH.name,
            knowledgeNodeIds = mathNodeIds,
        )
        assertTrue("math tutor references should be retrievable", tutorReferences.isNotEmpty())

        val physicsNodes = database.readSubjectKnowledgeNodes(
            subject = SubjectKind.PHYSICS.name,
            limit = 256,
        )
        assertTrue(
            "physics tutor references should be retrievable after the large-pack import",
            RoomTutorTeachingReferenceRepository(database).referencesFor(
                subject = SubjectKind.PHYSICS.name,
                knowledgeNodeIds = physicsNodes.mapTo(hashSetOf()) { it.knowledgeNodeId },
            ).isNotEmpty(),
        )
    }

    /**
     * 版本不符 → 全量导入路径（设备侧）。
     *
     * 快路径判定（`isInstallUpToDate`）的两态钉在 JVM（`BundledKnowledgeBaseInstallerTest`）与
     * 真实随包内容的快路径（`BundledContentReconciliationInstrumentedTest`），但那条设备用例
     * 只覆盖"戳同 → 不重写"这一态：它先装一次拿到真戳、再装一次，**从来没有让戳与随包不符**。
     * 于是"App 升级后随包 contentVersion 变了，却仍然被当成最新而跳过导入"这类静默失效
     * （字段静默不到设备，契约 §5.2）在设备上没有一条用例会红。
     *
     * 本用例补齐这一态：先在空库上写一条**包 ID 对、版本不对**的进度行（App 旧版本装的旧包），
     * 再跑真实 `install()`，断言它没有信戳跳过——解析了整包、重写了进度戳、内容真的进了库；
     * 最后把戳对回随包版本再装一次，必须回到快路径（这一句是配对，证明差异来自版本而不是
     * 别的东西）。判据用 `appliedAtEpochMillis` 而不是"拿 contentVersion 跟自己比"——后者恒真。
     */
    @Test
    fun contentVersionMismatchTakesTheFullImportPath() = runBlocking {
        val manifest = requireNotNull(BundledKnowledgePackResources.loadManifestOnly()) {
            "随包 update-manifest 必须在 classpath 上，否则版本判定无从谈起"
        }

        // 冷缓存：同进程里先跑过的用例可能已把包解析结果驻留，热缓存会让"解析发生"的断言失真
        // （JVM 侧同款理由见 `BundledKnowledgeBaseInstallerTest.setUp`）。
        BundledKnowledgePackResources.clear()

        // 版本不符的典型形态：库里已有进度行，但它记的是上一个随包版本。
        database.recordContentInstallState(
            ContentInstallStateRecord(
                packId = manifest.packId,
                contentVersion = "stale-${manifest.contentVersion}",
                appliedAtEpochMillis = 1L,
            ),
        )
        val parseCountBefore = BundledKnowledgePackResources.fullParseCount

        BundledKnowledgeBaseInstaller.install(database)

        assertEquals(
            "版本不符必须走全量：整包解析发生恰好一次，而不是信戳跳过",
            parseCountBefore + 1,
            BundledKnowledgePackResources.fullParseCount,
        )
        val reinstalled = requireNotNull(database.readContentInstallState(manifest.packId)) {
            "全量路径跑完后必须有进度行"
        }
        assertEquals(
            "进度戳必须重写为随包 contentVersion",
            manifest.contentVersion,
            reinstalled.contentVersion,
        )
        assertTrue(
            "全量路径必须重写进度行的应用时间（快路径直接返回、不改写）",
            reinstalled.appliedAtEpochMillis > 1L,
        )

        // 导入真的发生了，而不只是戳被改：空库上跑完全量路径后，九科都有可见 topic 与 grounded 原子点。
        val coverage = database.observeReviewedKnowledgeCoverage().first()
        assertEquals("九科都应有覆盖记录", SUPPORTED_SUBJECTS.size, coverage.size)
        coverage.forEach { record ->
            assertTrue("${record.subject} needs a visible topic", record.topicCount > 0)
            assertTrue(
                "${record.subject} needs grounded atomic abilities",
                record.atomicKnowledgeCount > 0,
            )
        }

        // 配对态：戳与随包一致后再装一次，必须回到快路径（零解析、零戳重写）。
        val settledParseCount = BundledKnowledgePackResources.fullParseCount
        val settledAppliedAt = reinstalled.appliedAtEpochMillis
        BundledKnowledgeBaseInstaller.install(database)
        assertEquals(
            "戳已对齐，第二次安装应走快路径、不再解析",
            settledParseCount,
            BundledKnowledgePackResources.fullParseCount,
        )
        val settled = requireNotNull(database.readContentInstallState(manifest.packId)) {
            "第二次安装不得删掉进度行"
        }
        assertEquals(
            "快路径不重写进度戳",
            settledAppliedAt,
            settled.appliedAtEpochMillis,
        )
    }

    private companion object {
        val SUPPORTED_SUBJECTS = SubjectKind.entries - SubjectKind.GENERAL
    }
}
