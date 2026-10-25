package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.knowledge.KnowledgePrerequisiteReader
import com.tingyun.smartmistakebook.core.data.settings.DataStoreSchedulingSettingsStore
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeSourceSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialFingerprint
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialNodeBindingRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeTeachingMaterialRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.ReviewPlanBundle
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.database.port.FullLibraryScanNodeRecord
import com.tingyun.smartmistakebook.core.domain.AlgorithmConstants
import com.tingyun.smartmistakebook.core.domain.ExamCalendarEntry
import com.tingyun.smartmistakebook.core.domain.FULL_LIBRARY_SCAN_DAILY_CAP
import com.tingyun.smartmistakebook.core.domain.ForgettingCurve
import com.tingyun.smartmistakebook.core.domain.FsrsScheduleMath
import com.tingyun.smartmistakebook.core.domain.HLRPredictionAuditService
import com.tingyun.smartmistakebook.core.domain.KnowledgeNodeScorer
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.domain.LogDurationModel
import com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2
import com.tingyun.smartmistakebook.core.domain.SchedulingSettingsStore
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批次 20 / T10（补批次 19 设备侧 UNVERIFIED）：**真 Room 种子库**直跑
 * [StudyReviewPlannerService]，钉 3E 知识点排计划与 G6 考试窗口门的设备侧行为。
 *
 * 批次 19 的 G6/3E 半边只在 JVM（`StudyReviewPlannerExamWindowTest`、
 * `RoomBackedStudyExperienceRepositoryTest`）验证过；本用例把同一批断言搬到
 * **真 SQLite** 上：种子走共享的 [seedStudyFacts]（库文件直写）+ 真写口
 * `importKnowledgeTeachingMaterials`（过材料契约与指纹校验），考试日历走真
 * `DataStoreSchedulingSettingsStore`（隔离 filesDir，规避 DataStore 单文件多实例）。
 *
 * 四面验收（对应批次 19 设备侧 UNVERIFIED 项）：
 * 1. **第二档全库捞回**：弱掌握点（绑定题 90 天前复习、稳定度 1 天，检索概率 ≈ 0.5）
 *    不在今日错题队列里，仍被全库扫描捞回（`currentKnowledgeReviewPlan` 的 scan 档）；
 *    多科（MATH/PHYSICS）同时在队。两个负向对照钉真 SQL 的范围判定：无材料节点
 *    （扫描行 material 为 NULL）不进队列；只有材料、没有在册绑定题的节点根本不在
 *    扫描行集里。
 * 2. **FULL_LIBRARY_SCAN_DAILY_CAP 封顶**：12 个同形弱点的分数全同 → 按节点 id
 *    定序取前 [FULL_LIBRARY_SCAN_DAILY_CAP] 个（KD-46：每次排计划 ≤10，非严格每日
 *    累计）；预算放宽（2700s ≥ 12×180s）使名额先于时间绑住，故恰好 10 个入队、
 *    id 字典序最大的两个（11/12）落选——同时钉了平局定序规则。
 * 3. **时间预算封顶**：知识点队列每项 180s（`LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS`，
 *    不吃时长模型），预算 360s 时全池（12 弱点 + 2 今日）只取 2 个——预算先于名额绑住。
 *    错题计划同受预算封顶（冷启动 60s/题 × 2 = 120 = 预算）。
 * 4. **无考试日与 JVM 基线一致（指纹对照）**：与 JVM 基线
 *    `StudyReviewPlannerExamWindowTest` 的「no exam ≡ out-of-window exam」同款门——
 *    同一固定时钟、同一夹具、两个独立真库，只改考试表（无考试 vs 窗口外 30 天）；
 *    断言计划**指纹**逐位一致（指纹 = 全计划字段的 SHA-256，含队列/理由/分数/预算/时刻，
 *    `ReviewPlannerV2.canonicalPlanFingerprint`）、预算逐位不变、且 `createReviewPlan`
 *    不触发全库扫描读（G6 读门，真 Room 端口上计数）。与 JVM 侧的**字面哈希**对照
 *    需要 JVM 先算出期望常量（本轨不跑 Gradle，不硬编码占位值），两侧以同一夹具形状
 *    + 同一断言集对齐；指纹对输入确定性 ⇒ 同输入在 JVM/设备上取值一致。
 *
 * 为什么两条预算（120s / 2700s）：冷启动时长模型把每道在册题估成 60s
 * （`LogDurationModel.GLOBAL_PRIOR_SECONDS`，`ReviewPlannerV2` 会用它替换种子里的
 * 180s），120s 的错题计划恰好装 2 题——今日题（100 天衰减 + 逾期 90 天）严格高分
 * 胜出，弱点题（90 天衰减）留在队外，第二档的"离开今日并集"因此真实发生；知识点
 * 排程由**另一个**服务实例（2700s）承担——它读的是**已落库**的当日计划（经
 * `saveReviewPlan`，同 `StudySnapshotBuilder.build` 的生产路径），其队列成员不再受
 * 新实例预算影响，只有知识点队列的取队预算随实例走。
 */
@RunWith(AndroidJUnit4::class)
class StudyReviewPlanner3EScanInstrumentedTest {

    // ------------------------------------------------------------------
    // 1. 第二档全库捞回 + 每日名额封顶（真 Room、真材料、真考试表）
    // ------------------------------------------------------------------

    @Test
    fun fullLibraryScanRecallsWeakNodesOutsideTodaysQueueUpToTheDailyCap() = runBlocking {
        withPlannerHarness(addFarExam = true) { database, store, _ ->
            // ---- 夹具自证：真 SQL 读口（一条 JOIN 查询）先钉住行集 ----
            // 15 个节点有在册（ACTIVE 错题本条目）绑定题：2 今日 + 12 弱点 + 1 无材料负向；
            // "只有材料"的节点不在行集里（INNER JOIN 挡掉）。
            val scanRowById = database.readFullLibraryScanNodes()
                .associateBy(FullLibraryScanNodeRecord::knowledgeNodeId)
            assertEquals(
                "全库扫描行 = 有在册绑定题的活跃知识点（2 今日 + 12 弱点 + 1 无材料负向）",
                15,
                scanRowById.size,
            )
            assertFalse(
                "只有材料、没有在册绑定题的节点不在扫描行集里",
                scanRowById.containsKey(KC_ONLY_MATERIAL),
            )
            WEAK_NODE_IDS.forEach { nodeId ->
                assertEquals(
                    "弱点 $nodeId 的扫描行必须带可出题材料组",
                    "material-$nodeId",
                    scanRowById.getValue(nodeId).materialGroupId,
                )
            }
            assertNull(
                "无材料负向节点的扫描行 materialGroupId 必须为 NULL（读口只给事实，范围规则在领域层）",
                scanRowById.getValue(KC_NEG_NO_MATERIAL).materialGroupId,
            )

            // ---- 当日错题计划（预算 120s）：今日两题入选、弱点留在队外 ----
            val planner = plannerService(database, store, budgetSeconds = 120)
            val snapshot = seededSnapshot()
            val mistakes = database.observeMistakes().first()
            assertEquals("真读路径必须带回全部 15 条在册错题", 15, mistakes.size)
            val planningContext = planner.planningContext(snapshot)
            assertEquals(
                "固定时钟下排程时刻必须确定（夹具自证）",
                PLANNING_AT,
                planningContext.planningAtEpochMillis,
            )
            val planBundle = planner.createReviewPlan(
                mistakes = mistakes,
                learnerSnapshot = snapshot,
                planningContext = planningContext,
            )
            assertEquals(
                "夹具自证：今日队列只装两道今日题（弱点的题留在队外，第二档的捞回才是真的）",
                listOf("unit-today-math", "unit-today-phys"),
                planBundle.queue.map { it.practiceUnitId }.sorted(),
            )
            assertEquals(
                "窗口外考试不放大预算（无考试日口径）",
                120,
                planBundle.plan.timeBudgetSeconds,
            )
            assertTrue(
                "错题计划时间预算封顶：冷启动 60s/题 × 2 = 120 ≤ 120",
                planBundle.queue.sumOf { it.estimatedSeconds } <= planBundle.plan.timeBudgetSeconds,
            )
            // 生产路径落库（同 StudySnapshotBuilder.build：createReviewPlan + saveReviewPlan）。
            database.saveReviewPlan(planBundle)

            // ---- 知识点排计划（预算 2700s ≥ 12×180s：名额先于时间绑住）----
            val knowledgePlanner = plannerService(database, store, budgetSeconds = 2_700)
            val knowledgePlan = knowledgePlanner.currentKnowledgeReviewPlan(
                requestId = "3e-device-cap",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            val queueIds = knowledgePlan.queue.map { it.knowledgeNodeId }

            // 第二档全库捞回：弱点（今日并集之外）被捞回，且恰好 [FULL_LIBRARY_SCAN_DAILY_CAP] 个。
            assertTrue(
                "夹具自证：弱掌握点候选（12）必须多于每日名额，封顶才真的在封",
                WEAK_NODE_IDS.size > FULL_LIBRARY_SCAN_DAILY_CAP,
            )
            val weakInQueue = queueIds.filter { it in WEAK_NODE_IDS }
            assertEquals(
                "第二档每次排计划最多捞回 FULL_LIBRARY_SCAN_DAILY_CAP 个弱掌握点",
                FULL_LIBRARY_SCAN_DAILY_CAP,
                weakInQueue.size,
            )
            assertEquals(
                "同形弱点平局按节点 id 定序（领域层 compareByDescending(score).thenBy(id) 的设备侧兑现）",
                (1..FULL_LIBRARY_SCAN_DAILY_CAP).map { index -> "kc-weak-%02d".format(index) },
                weakInQueue.sorted(),
            )
            // 第一档（今日队列绑定节点）不被名额挤掉。
            assertTrue("kc-today-math" in queueIds)
            assertTrue("kc-today-phys" in queueIds)
            // 范围负向：无材料节点两档都不进；只有材料的节点行集里就没有。
            assertFalse("无材料的扫描行不进队列", KC_NEG_NO_MATERIAL in queueIds)
            assertFalse("没有在册绑定题的节点不进队列", KC_ONLY_MATERIAL in queueIds)
            // 多科：捞回的弱点跨 MATH/PHYSICS 两科。
            val subjectById = knowledgePlan.queue.associate { it.knowledgeNodeId to it.subject }
            assertTrue(
                "弱点捞回必须跨科（MATH）",
                weakInQueue.any { subjectById.getValue(it) == "MATH" },
            )
            assertTrue(
                "弱点捞回必须跨科（PHYSICS）",
                weakInQueue.any { subjectById.getValue(it) == "PHYSICS" },
            )
            assertEquals(
                "预算放得下全池（2 今日 + 10 扫描）时名额封顶决定队大小",
                FULL_LIBRARY_SCAN_DAILY_CAP + 2,
                knowledgePlan.queue.size,
            )
            assertTrue(
                "知识点队列时间预算封顶：size × 180s ≤ 2700s",
                knowledgePlan.queue.size * LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS <= 2_700,
            )
        }
    }

    // ------------------------------------------------------------------
    // 2. 时间预算先于名额绑住（预算 360s = 2×180s < 全池 14 项）
    // ------------------------------------------------------------------

    @Test
    fun knowledgeQueueStopsAtTheTimeBudgetWhenItBindsBeforeTheDailyCap() = runBlocking {
        withPlannerHarness(addFarExam = false) { database, store, _ ->
            // 先落一份当日计划（同生产路径），知识点排程才有"今日队列"可读。
            val planner = plannerService(database, store, budgetSeconds = 120)
            val snapshot = seededSnapshot()
            val mistakes = database.observeMistakes().first()
            val planningContext = planner.planningContext(snapshot)
            database.saveReviewPlan(
                planner.createReviewPlan(
                    mistakes = mistakes,
                    learnerSnapshot = snapshot,
                    planningContext = planningContext,
                ),
            )

            val knowledgePlanner = plannerService(database, store, budgetSeconds = 360)
            val knowledgePlan = knowledgePlanner.currentKnowledgeReviewPlan(
                requestId = "3e-device-budget",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            val eligible = WEAK_NODE_IDS.size + 2
            assertTrue(
                "夹具自证：候选池（$eligible）必须多于预算能装下的项数，预算才真的在封",
                eligible > 360 / LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS,
            )
            assertEquals(
                "预算 360s = 恰好 2×180s：队列在时间预算处截断（先于每日名额）",
                2,
                knowledgePlan.queue.size,
            )
            assertTrue(
                "知识点队列时间预算封顶：size × 180s ≤ 360s",
                knowledgePlan.queue.size * LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS <= 360,
            )
        }
    }

    // ------------------------------------------------------------------
    // 3. 无考试日行为与 JVM 基线一致（指纹对照 + 扫描读门）
    // ------------------------------------------------------------------

    @Test
    fun noExamDayPlanIsFingerprintIdenticalToAnOutOfWindowExamPlan() = runBlocking {
        // 两个独立真库、同一夹具、同一固定时钟；唯一差异 = 考试表（无 vs 窗口外 30 天）。
        val noExam = planWithExamGate(databaseSuffix = "no-exam", examDaysUntil = null)
        val farExam = planWithExamGate(databaseSuffix = "far-exam", examDaysUntil = 30)

        assertEquals(
            "无考试日与窗口外考试的指纹必须逐位一致（JVM 基线同款门；" +
                "指纹含队列/理由/分数/预算/时刻，见 ReviewPlannerV2.canonicalPlanFingerprint）",
            noExam.bundle.plan.planFingerprint,
            farExam.bundle.plan.planFingerprint,
        )
        assertEquals("预算逐位不变（无考试日）", 120, noExam.bundle.plan.timeBudgetSeconds)
        assertEquals("预算逐位不变（窗口外考试）", 120, farExam.bundle.plan.timeBudgetSeconds)
        assertEquals(
            "两侧计划非空且同队（指纹一致的可见面）",
            noExam.bundle.queue.map { it.practiceUnitId }.sorted(),
            farExam.bundle.queue.map { it.practiceUnitId }.sorted(),
        )
        assertEquals(2, noExam.bundle.queue.size)
        assertEquals(
            "无考试日不得触发全库扫描读（G6 读门，真 Room 端口上计数）",
            0,
            noExam.scanReadCount,
        )
        assertEquals(
            "窗口外考试不得触发全库扫描读（G6 读门，真 Room 端口上计数）",
            0,
            farExam.scanReadCount,
        )
    }

    // ------------------------------------------------------------------
    // 接线与夹具
    // ------------------------------------------------------------------

    private class PlannedRun(
        val bundle: ReviewPlanBundle,
        val scanReadCount: Int,
    )

    /**
     * 一个独立真库 + 真 DataStore 考试表上按 [examDaysUntil]（null = 无考试）排一份
     * 当日计划。服务包在 [CountingScanPort] 里：`createReviewPlan` 期间的全库扫描读
     * 在真 Room 端口上计数（G6 读门）。
     */
    private suspend fun planWithExamGate(databaseSuffix: String, examDaysUntil: Int?): PlannedRun {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "planner-3e-$databaseSuffix-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        val rawDatabase = StudyDatabaseFactory.open(context, databaseName)
        val database = CountingScanPort(rawDatabase)
        val storeRoot = File(context.cacheDir, "planner-3e-gate-$databaseSuffix-${UUID.randomUUID()}")
        val storeContext = IsolatedStorageContext(context, storeRoot)
        val storeJob = SupervisorJob()
        val store = DataStoreSchedulingSettingsStore(
            context = storeContext,
            scope = CoroutineScope(storeJob + Dispatchers.IO),
        )
        try {
            seedPlannerFixture(context, database, databaseName)
            if (examDaysUntil != null) {
                store.addExam(
                    ExamCalendarEntry(
                        entryId = "exam-gate-$databaseSuffix",
                        subject = "MATH",
                        examEpochDay = LocalDate.of(2026, 1, 2).toEpochDay() + examDaysUntil,
                        title = "窗口外考试",
                    ),
                )
            }
            val planner = plannerService(database, store, budgetSeconds = 120)
            val snapshot = seededSnapshot()
            val mistakes = database.observeMistakes().first()
            val bundle = planner.createReviewPlan(
                mistakes = mistakes,
                learnerSnapshot = snapshot,
                planningContext = planner.planningContext(snapshot),
            )
            return PlannedRun(bundle = bundle, scanReadCount = database.fullLibraryScanReadCount)
        } finally {
            storeJob.cancelAndJoin()
            database.close()
            context.deleteDatabase(databaseName)
            storeRoot.deleteRecursively()
        }
    }

    /**
     * 每个用例一套隔离环境：独立真库 + 隔离 filesDir 上的真 DataStore 考试表
     * （DataStore 对同一文件多实例会抛错；隔离目录沿用
     * `DataStoreReviewReminderRepositoryTest.IsolatedStorageContext` 的既有模式）。
     * [addFarExam] = true 时经**真写口** `addExam` 落一条窗口外（30 天）PHYSICS 考试——
     * 既覆盖"种子库含考试日历设置"，又证明第二档/名额在窗口外考试下逐位不受扰动。
     */
    private suspend fun withPlannerHarness(
        addFarExam: Boolean,
        block: suspend (
            database: StudyDatabasePort,
            schedulingStore: SchedulingSettingsStore,
            context: Context,
        ) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "planner-3e-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        val database = StudyDatabaseFactory.open(context, databaseName)
        val storeRoot = File(context.cacheDir, "planner-3e-store-${UUID.randomUUID()}")
        val storeContext = IsolatedStorageContext(context, storeRoot)
        val storeJob = SupervisorJob()
        val store = DataStoreSchedulingSettingsStore(
            context = storeContext,
            scope = CoroutineScope(storeJob + Dispatchers.IO),
        )
        try {
            seedPlannerFixture(context, database, databaseName)
            if (addFarExam) {
                store.addExam(
                    ExamCalendarEntry(
                        entryId = "exam-far-physics",
                        subject = "PHYSICS",
                        examEpochDay = LocalDate.of(2026, 1, 2).toEpochDay() + 30,
                        title = "窗口外物理考",
                    ),
                )
            }
            block(database, store, context)
        } finally {
            storeJob.cancelAndJoin()
            database.close()
            context.deleteDatabase(databaseName)
            storeRoot.deleteRecursively()
        }
    }

    /** 与生产同一构造面（对照 `RoomBackedStudyExperienceRepository` 的接线）。 */
    private fun plannerService(
        database: StudyDatabasePort,
        schedulingStore: SchedulingSettingsStore,
        budgetSeconds: Int,
    ): StudyReviewPlannerService = StudyReviewPlannerService(
        database = database,
        learnerId = LEARNER_ID,
        studyZoneId = ZONE,
        clock = CLOCK,
        reviewTimeBudgetSeconds = budgetSeconds,
        planningDecay = DECAY,
        reviewPlannerV2 = ReviewPlannerV2(ForgettingCurve()),
        knowledgeNodeScorer = KnowledgeNodeScorer(),
        durationModel = LogDurationModel(),
        reviewLogSink = ReviewLogSink(database, LEARNER_ID, CLOCK, ZONE),
        schedulingSettingsStore = schedulingStore,
        predictionAuditService = HLRPredictionAuditService(),
        predictionAuditSink = RoomPredictionAuditSink(database),
        knowledgePrerequisites = KnowledgePrerequisiteReader(database),
        learnerSnapshot = { seededSnapshot() },
    )

    /**
     * 确定性学习者快照（同 JVM 基线 `StudyReviewPlannerExamWindowTest` 的形状）：
     * 记忆态按 [PLANNING_AT] 推出。弱掌握点 = 绑定题 90 天前复习、稳定度 1 天
     * （检索概率 ≈ 0.5，到期风险高）且无知识点掌握态（MISSING_KNOWLEDGE_EVIDENCE）。
     */
    private fun seededSnapshot(): LearnerSnapshot {
        val memories = buildMap {
            put("unit-today-math", memoryOf("unit-today-math", lastReviewedDaysAgo = 100, overdueDays = 90))
            put("unit-today-phys", memoryOf("unit-today-phys", lastReviewedDaysAgo = 100, overdueDays = 90))
            WEAK_UNIT_IDS.forEach { unitId ->
                put(unitId, memoryOf(unitId, lastReviewedDaysAgo = 90, overdueDays = 80))
            }
            // 无材料负向节点的那道题**没有**记忆态（新题）：NewIntroductionPolicy 的时间片
            // （120s → 40s）装不下 60s 的它，但全超尺寸死锁保护会把它当日引入——无论引入
            // 与否，V2 都会因预算（120s = 2×60s 已被更高分的到期题占满）把它留在队外，
            // 该节点的"不在今日并集"因此不受引入决策影响。
        }
        return LearnerSnapshot(
            learnerId = LEARNER_ID,
            problemMemoryStates = memories,
            knowledgeMasteryStates = emptyMap(),
            checkpoint = ProjectionCheckpoint(
                lastSequence = 0,
                projectorVersion = LearningProjector.VERSION,
                projectedAtEpochMillis = 0,
            ),
            generatedAtEpochMillis = PLANNING_AT,
        )
    }

    private fun memoryOf(
        unitId: String,
        lastReviewedDaysAgo: Long,
        overdueDays: Long,
    ) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = 1.0,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = PLANNING_AT - lastReviewedDaysAgo * DAY_MILLIS,
        nextReviewAtEpochMillis = PLANNING_AT - overdueDays * DAY_MILLIS,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 0,
    )

    // ------------------------------------------------------------------
    // 种子：多科、绑定题 + 可出题材料、弱掌握点、（真写口）考试日历
    // ------------------------------------------------------------------

    private suspend fun seedPlannerFixture(
        context: Context,
        database: StudyDatabasePort,
        databaseName: String,
    ) {
        val todayUnits = listOf(
            SeededUnit("unit-today-math", "MATH", "kc-today-math"),
            SeededUnit("unit-today-phys", "PHYSICS", "kc-today-phys"),
        )
        val weakUnits = WEAK_NODE_IDS.mapIndexed { index, nodeId ->
            SeededUnit(
                unitId = WEAK_UNIT_IDS[index],
                subject = if (index < 6) "MATH" else "PHYSICS",
                nodeId = nodeId,
            )
        }
        val negativeUnits = listOf(
            SeededUnit("unit-neg-no-material", "MATH", KC_NEG_NO_MATERIAL),
        )
        val allUnits = todayUnits + weakUnits + negativeUnits

        // 材料只绑"可出题"节点：今日两点 + 12 弱点 + 只有材料的负向节点。
        // 绑定契约要求节点 ATOMIC + SOURCE_GROUNDED、材料/来源/节点同科。
        val materialSubjectByNode: Map<String, String> =
            mapOf("kc-today-math" to "MATH", "kc-today-phys" to "PHYSICS") +
                WEAK_NODE_IDS.mapIndexed { index, nodeId ->
                    nodeId to if (index < 6) "MATH" else "PHYSICS"
                } +
                mapOf(KC_ONLY_MATERIAL to "MATH")

        val sources = listOf("MATH", "PHYSICS").map { subject ->
            KnowledgeSourceSeedRecord(
                sourceId = "source-3e-$subject",
                subject = subject,
                sourceType = "AUTHORIZED_EDUCATION_MATERIAL",
                title = "3E 设备夹具来源($subject)",
                publisher = null,
                edition = null,
                sourceUri = "https://example.org/3e-${subject.lowercase()}",
                licenseStatus = "PUBLIC_OFFICIAL",
                contentFingerprint = sha256Hex("source-3e-$subject"),
                importedAtEpochMillis = NODE_CREATED_AT,
                // contentUsePolicy 默认 REVIEWED_SYNTHESIS_ONLY，与材料
                // derivationKind=REVIEWED_SYNTHESIS 的许可组合兼容。
            )
        }
        val materials = materialSubjectByNode.map { (nodeId, subject) ->
            materialOf(materialId = "material-$nodeId", subject = subject, nodeId = nodeId)
        }
        val materialBindings = materialSubjectByNode.keys.map { nodeId ->
            KnowledgeTeachingMaterialNodeBindingRecord(
                materialId = "material-$nodeId",
                knowledgeNodeId = nodeId,
                role = "PRIMARY",
            )
        }

        seedStudyFacts(
            context = context,
            database = database,
            databaseName = databaseName,
            bundle = StudySeedBundle(
                problems = allUnits.map { unit ->
                    ProblemSeedRecord(
                        problemId = "problem-${unit.unitId}",
                        canonicalFingerprint = sha256Hex("problem-${unit.unitId}"),
                        subject = unit.subject,
                        createdAtEpochMillis = NODE_CREATED_AT,
                    )
                },
                revisions = allUnits.map { unit ->
                    ProblemRevisionSeedRecord(
                        revisionId = "revision-${unit.unitId}",
                        problemId = "problem-${unit.unitId}",
                        revisionNumber = 1,
                        title = "题 ${unit.unitId}",
                        problemMarkdown = "求 ${unit.unitId} 的解。",
                        questionDocumentSnapshot = null,
                        answerSpecId = null,
                        answerSpecSnapshot = null,
                        answerVerificationStatus = "UNKNOWN",
                        sourceType = "TEST",
                        sourceReference = null,
                        contentFingerprint = sha256Hex("revision-${unit.unitId}"),
                        createdAtEpochMillis = NODE_CREATED_AT,
                    )
                },
                practiceUnits = allUnits.map { unit ->
                    PracticeUnitSeedRecord(
                        practiceUnitId = unit.unitId,
                        problemId = "problem-${unit.unitId}",
                        problemRevisionId = "revision-${unit.unitId}",
                        unitKey = "unit:${unit.unitId}",
                        unitKind = "PROBLEM",
                        title = "题 ${unit.unitId}",
                        promptMarkdown = "prompt ${unit.unitId}",
                        estimatedSeconds = 180,
                        createdAtEpochMillis = NODE_CREATED_AT,
                    )
                },
                errorBookEntries = allUnits.map { unit ->
                    ErrorBookEntrySeedRecord(
                        entryId = "entry-${unit.unitId}",
                        practiceUnitId = unit.unitId,
                        problemId = "problem-${unit.unitId}",
                        currentRevisionId = "revision-${unit.unitId}",
                        sourceKey = null,
                        acceptedAtEpochMillis = NODE_CREATED_AT,
                        updatedAtEpochMillis = NODE_CREATED_AT,
                    )
                },
                knowledgeNodes = allUnits.map { unit -> nodeOf(unit.nodeId, unit.subject) } +
                    nodeOf(KC_ONLY_MATERIAL, "MATH"),
                knowledgeBindings = allUnits.map { unit ->
                    KnowledgeBindingSeedRecord(
                        bindingId = "binding-${unit.unitId}",
                        practiceUnitId = unit.unitId,
                        knowledgeNodeId = unit.nodeId,
                        basisRevisionId = "revision-${unit.unitId}",
                        strength = 1.0,
                        sourceType = "TEST_LOCAL",
                        taxonomyVersion = TAXONOMY_VERSION,
                        acceptedAtEpochMillis = NODE_CREATED_AT,
                    )
                },
            ),
        )
        // 材料走真写口（契约 + 内容指纹校验 + Room 事务），不用库文件直写绕过校验。
        database.importKnowledgeTeachingMaterials(
            materials = materials,
            bindings = materialBindings,
            sources = sources,
        )
    }

    private fun nodeOf(nodeId: String, subject: String) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = nodeId,
        stableCode = "$TAXONOMY_VERSION:$subject:$nodeId",
        subject = subject,
        displayName = "节点 $nodeId",
        parentKnowledgeNodeId = null,
        taxonomyVersion = TAXONOMY_VERSION,
        createdAtEpochMillis = NODE_CREATED_AT,
        // ATOMIC + SOURCE_GROUNDED：材料绑定契约的要求（材料只能绑已审核的细粒度点）。
        granularity = "ATOMIC",
        verificationStatus = "SOURCE_GROUNDED",
    )

    private fun materialOf(materialId: String, subject: String, nodeId: String) =
        KnowledgeTeachingMaterialRecord(
            materialId = materialId,
            stableCode = "$TAXONOMY_VERSION:$subject:teaching:$nodeId",
            subject = subject,
            materialType = "CONCEPT_EXPLANATION",
            title = "讲解 $nodeId",
            summaryMarkdown = "$nodeId 的要点评述。",
            applicabilityMarkdown = "适用于 $nodeId 相关题目。",
            contentMarkdown = "$nodeId 的概念讲解与示例。",
            boundaryMarkdown = "只覆盖 $nodeId，不涉及其他知识点。",
            derivationKind = "REVIEWED_SYNTHESIS",
            sourceId = "source-3e-$subject",
            sourceLocator = "locate-$nodeId",
            contentFingerprint = "",
            reviewedAtEpochMillis = MATERIAL_REVIEWED_AT,
        ).let { material ->
            // 契约要求 contentFingerprint 恰为载荷的 SHA-256（本记录无构造期校验，回填后落库）。
            material.copy(contentFingerprint = KnowledgeTeachingMaterialFingerprint.expected(material))
        }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    /**
     * KF-32 同款开口（JVM 基线 `CountingScanPort` 的设备版）：只加计数、不改行为——
     * 在**真 Room 端口**上证明 G6 的扫描读门（无考试/窗口外 = 0 次）。
     */
    private class CountingScanPort(
        private val delegate: StudyDatabasePort,
    ) : StudyDatabasePort by delegate {
        var fullLibraryScanReadCount: Int = 0
            private set

        override suspend fun readFullLibraryScanNodes(): List<FullLibraryScanNodeRecord> {
            fullLibraryScanReadCount += 1
            return delegate.readFullLibraryScanNodes()
        }
    }

    /** DataStore 单文件单实例：把 filesDir 指到每用例独立目录（沿用提醒测试的既有模式）。 */
    private class IsolatedStorageContext(
        base: Context,
        private val root: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = File(root, "files").also { directory ->
            check(directory.isDirectory || directory.mkdirs())
        }
    }

    private data class SeededUnit(
        val unitId: String,
        val subject: String,
        val nodeId: String,
    )

    private companion object {
        const val LEARNER_ID = "learner:3e-device"
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZONE)

        /** 固定时钟下的排程时刻（本地 16:00 > 04:00 日界 ⇒ planningAt = clock）。 */
        val PLANNING_AT: Long = Instant.parse("2026-01-02T08:00:00Z").toEpochMilli()
        const val DAY_MILLIS = AlgorithmConstants.DAY_MILLIS

        /** 节点创建/来源导入时刻；材料审核时刻在其后（契约要求 reviewedAt ≥ 两者）。 */
        const val NODE_CREATED_AT = 1_733_000_000_000L
        const val MATERIAL_REVIEWED_AT = NODE_CREATED_AT + 1_000L
        const val TAXONOMY_VERSION = "3e-device-v1"

        /** 12 个弱掌握点：01–06 MATH、07–12 PHYSICS（> FULL_LIBRARY_SCAN_DAILY_CAP）。 */
        val WEAK_NODE_IDS: List<String> = (1..12).map { index -> "kc-weak-%02d".format(index) }
        val WEAK_UNIT_IDS: List<String> = (1..12).map { index -> "unit-weak-%02d".format(index) }
        const val KC_NEG_NO_MATERIAL = "kc-neg-no-material"
        const val KC_ONLY_MATERIAL = "kc-only-material"

        val DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]
    }
}
