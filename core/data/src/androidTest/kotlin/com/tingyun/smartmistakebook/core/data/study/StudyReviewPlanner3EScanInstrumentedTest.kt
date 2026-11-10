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
import com.tingyun.smartmistakebook.core.database.StudyDbValue
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
import com.tingyun.smartmistakebook.core.model.CalibrationSnapshot
import com.tingyun.smartmistakebook.core.model.CalibrationSupport
import com.tingyun.smartmistakebook.core.model.IndependentCorrectObservation
import com.tingyun.smartmistakebook.core.model.KnowledgeMasteryState
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.MasteryStatus
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
 * 批次 50 / G2（3E 门）：**真 Room 种子库 + 真实写口**直跑 [StudyReviewPlannerService]，
 * 钉 3E「全库薄弱点扫描」的三条门判据：离开今日并集可捞回、F8/F17 双封顶、每日/单计划口径。
 *
 * ## 夹具为什么是现在这个形状（批次 50 修正，先红后绿）
 *
 * 本文件源自批次 20 的 `StudyReviewPlanner3EScanInstrumentedTest`。批次 50 首跑该用例
 * **3/3 红**（`3e/logs/01-instrument-3e-scan-existing.log`），成因全部在夹具对两处
 * **后来才变**的生产口径的假设上，不是实现缺陷：
 *
 * 1. **预算解析口径**：`BudgetResolution`（批次 49 `b6712a42`）引入后，构造参数预算只在
 *    `CONSTRUCTOR_ONLY` 下生效；默认 `SETTINGS_THEN_CONSTRUCTOR` 先读设置存储，真 DataStore
 *    给出默认 1200s。旧用例显式传 120/360 却期望计划预算恰好是 120/360 → 实得 1200。
 * 2. **知识点队列的预算来源**：`ea4348f9`（2026-11-06「预算同源」）把
 *    `timeBudgetSeconds = reviewTimeBudgetSeconds` 改成 `currentPlan.plan.timeBudgetSeconds`
 *    ——知识点队列取**当日已落库计划**的时间预算（含设置派生与考试爬升），不再取知识排程
 *    实例的构造预算。旧用例"今日计划 120s + 知识实例 2700s"的写法因此不再可能成立：预算只有
 *    一个来源。
 *
 * 旧用例的断言意图一条没删，只是把夹具改成**能在单预算来源下成立**的真实场景：
 * 用户错题本里有 126 道在册题，个性化时长桶把每道错题估成 600s（10 分钟的大题量级），
 * 45 分钟（2700s）只装得下 4 道；其余 118 个薄弱知识点真的落在今日队列之外——这才是 3E 要
 * 捞回的那批点。既有断言全部保留并加强：节点数 15→126（> 10 倍）、新增"掌握良好的点不被捞回"
 * 负向与"按检索概率排序（不是平局/先到先得）"。
 *
 * ## 四面验收
 *
 * 1. **第二档全库捞回（离开今日并集）**：120 个弱掌握点（绑定题 5–124 天前复习、稳定度 1 天，
 *    无知识点掌握态）里，当日计划（4 题）只绑定其中 2 个 + 2 道今日题；其余 118 个点只可能由
 *    全库扫描捞回。断言捞回的 10 个**全部**不在今日计划的知识点并集里，且恰是队外最老的 10 个
 *    （检索概率最低 → 打分最高，不是平局/先到先得）。
 * 2. **F17 名额封顶**：队外薄弱点 118 个，第二档每个学习日累计只取
 *    [FULL_LIBRARY_SCAN_DAILY_CAP] = 10 个**新增**（KD-46 批次 51 修复：当日已花费名额从既有
 *    `KNOWLEDGE_QUIZ` 作答账本派生，零 schema）。
 * 3. **F8 时间预算封顶**：预算 1200s 时知识点队列只取 6 个（6×180 = 1080 ≤ 1200 < 7×180），
 *    其中 2 个槽给今日档、只剩 4 个给第二档——名额还没用完，预算先绑住；且预算与当日计划
 *    同一来源（`currentPlan.plan.timeBudgetSeconds`）。
 * 4. **每日累计（KD-46）**：同一天第二次排计划（前 10 个已落当日 `KNOWLEDGE_QUIZ` 证据）**不得**
 *    再取满 10——扫描档只剩回放（同一批 10），当日并集 ≤ 10。旧用例的"当日累计 20 > 10"
 *    断言已按新口径改写（收紧，不是放宽）。
 *
 * 负向对照（真 SQL 范围判定）：只有材料、没有在册绑定题的节点不在扫描行集里；有在册绑定题
 * 但没有可出题材料的节点有扫描行（material 为 NULL）却不进队列；**掌握良好且新鲜**的点
 * 在今日并集之外也不被捞回（扫描只捞薄弱点，不捞"所有未排到的点"）。
 *
 * 预算量级：知识点每个 180s（`LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS`）→ 45 分钟
 * = 15 个知识点槽；错题按个性化桶 600s/题 → 45 分钟 = 4 题、20 分钟 = 2 题。两条都是学生
 * 可设的量级，不是为用例编的数。
 */
@RunWith(AndroidJUnit4::class)
class StudyReviewPlanner3EScanInstrumentedTest {

    // ------------------------------------------------------------------
    // 1. 第二档全库捞回 + F17 名额封顶（真 Room、真材料、真考试表）
    // ------------------------------------------------------------------

    @Test
    fun fullLibraryScanRecallsWeakNodesOutsideTodaysQueueUpToTheDailyCap() = runBlocking {
        withPlannerHarness(addFarExam = true) { database, store, _ ->
            // ---- 夹具自证：真 SQL 读口（一条 JOIN 查询）先钉住行集 ----
            // 126 个节点有在册（ACTIVE 错题本条目）绑定题：120 弱点 + 2 今日 + 3 已掌握 + 1 无材料；
            // "只有材料"的节点不在行集里（INNER JOIN 挡掉）。
            val scanRows = database.readFullLibraryScanNodes()
            val scanRowById = scanRows.associateBy(FullLibraryScanNodeRecord::knowledgeNodeId)
            assertEquals(
                "全库扫描行 = 有在册绑定题的活跃知识点（120 弱 + 2 今日 + 3 已掌握 + 1 无材料）",
                WEAK_NODE_IDS.size + 2 + STRONG_NODE_IDS.size + 1,
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

            // ---- 当日错题计划（预算 2700s，600s/题）：只装得下 4 题，弱点留在队外 ----
            val snapshot = seededSnapshot()
            val planner = plannerService(database, store, budgetSeconds = FULL_BUDGET_SECONDS)
            val mistakes = database.observeMistakes().first()
            assertEquals(
                "真读路径必须带回全部在册错题",
                ALL_UNIT_IDS.size,
                mistakes.size,
            )
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
            val todayScopeNodes = planBundle.queue
                .flatMap { item -> item.knowledgeNodeIds }
                .toSet()
            assertTrue(
                "夹具自证：今日两道题在当日队列里（第一档非空，并集才是真的两档）",
                planBundle.queue.map { it.practiceUnitId }.containsAll(TODAY_UNIT_IDS),
            )
            assertEquals(
                "错题计划时间预算封顶：600s/题 × 4 = 2400 ≤ 2700，第 5 题（3000）装不下",
                4,
                planBundle.queue.size,
            )
            assertTrue(
                "错题计划 Σ 估时 ≤ 2700s",
                planBundle.queue.sumOf { it.estimatedSeconds } <= FULL_BUDGET_SECONDS,
            )
            val weakOutsideToday = WEAK_NODE_IDS.filterNot { it in todayScopeNodes }
            assertTrue(
                "夹具自证：今日计划之外的薄弱点（${weakOutsideToday.size}）必须远多于名额，" +
                    "封顶与捞回才都真的在发生",
                weakOutsideToday.size > FULL_LIBRARY_SCAN_DAILY_CAP * 5,
            )
            // 生产路径落库（同 StudySnapshotBuilder.build：createReviewPlan + saveReviewPlan）。
            database.saveReviewPlan(planBundle)

            // ---- 知识点排计划：预算与当日计划同源（2700s）----
            val knowledgePlan = planner.currentKnowledgeReviewPlan(
                requestId = "3e-device-cap",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            val queueIds = knowledgePlan.queue.map { it.knowledgeNodeId }

            // 第二档全库捞回：队外薄弱点被捞回，且恰好 [FULL_LIBRARY_SCAN_DAILY_CAP] 个。
            val recoveredOutside = queueIds.filter { it in weakOutsideToday }
            assertEquals(
                "第二档每次排计划最多捞回 FULL_LIBRARY_SCAN_DAILY_CAP 个弱掌握点（F17）",
                FULL_LIBRARY_SCAN_DAILY_CAP,
                recoveredOutside.size,
            )
            assertEquals(
                "捞回的必须是队外最老的 10 个（检索概率最低 → 打分最高；不是「先到先得」）",
                expectedOldestOutside(weakOutsideToday, FULL_LIBRARY_SCAN_DAILY_CAP),
                recoveredOutside.toSet(),
            )
            assertTrue(
                "捞回的点必须真的在今日错题并集之外",
                recoveredOutside.none { it in todayScopeNodes },
            )
            // 负向对照：掌握良好且新鲜的点不因"今天没排到"被捞回。
            assertTrue(
                "已掌握且新鲜的知识点不得进入全库扫描档，实际：$queueIds",
                queueIds.none { it in STRONG_NODE_IDS },
            )
            // 第一档（今日队列绑定节点）不被名额挤掉。
            TODAY_NODE_IDS.forEach { nodeId ->
                assertTrue("今日队列绑定的点必须在计划里：$nodeId", nodeId in queueIds)
            }
            // 范围负向：无材料节点两档都不进；只有材料的节点行集里就没有。
            assertFalse("无材料的扫描行不进队列", KC_NEG_NO_MATERIAL in queueIds)
            assertFalse("没有在册绑定题的节点不进队列", KC_ONLY_MATERIAL in queueIds)
            // 多科：捞回的弱点跨至少两科（120 个点按 MATH/PHYSICS/CHEMISTRY 轮转）。
            val subjectById = knowledgePlan.queue.associate { it.knowledgeNodeId to it.subject }
            assertTrue(
                "弱点捞回必须跨科",
                recoveredOutside.map { subjectById.getValue(it) }.toSet().size >= 2,
            )
            assertEquals(
                "知识点队列 = 今日档 + 第二档 10 个（池先被取空，预算 14×180=2520 ≤ 2700 未先绑）",
                todayScopeNodes.size + FULL_LIBRARY_SCAN_DAILY_CAP,
                knowledgePlan.queue.size,
            )
            assertEquals(
                "F17 名额在队列里可见：10 个队外弱点（今日档 ${todayScopeNodes.size} 个）",
                FULL_LIBRARY_SCAN_DAILY_CAP,
                recoveredOutside.size,
            )
            assertTrue(
                "F8：知识点队列 Σ 估时 ≤ 2700s",
                knowledgePlan.queue.size * LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS <=
                    FULL_BUDGET_SECONDS,
            )
        }
    }

    // ------------------------------------------------------------------
    // 2. F8 时间预算先于 F17 名额绑住（预算 1200s = 6×180s < 全池 12 项）
    // ------------------------------------------------------------------

    @Test
    fun knowledgeQueueStopsAtTheTimeBudgetWhenItBindsBeforeTheDailyCap() = runBlocking {
        withPlannerHarness(addFarExam = false) { database, store, _ ->
            // 当日计划用 1200s 落库：知识点队列的预算与它同源（`currentPlan.plan.timeBudgetSeconds`）。
            val planner = plannerService(database, store, budgetSeconds = TIGHT_BUDGET_SECONDS)
            val snapshot = seededSnapshot()
            val mistakes = database.observeMistakes().first()
            val planningContext = planner.planningContext(snapshot)
            val planBundle = planner.createReviewPlan(
                mistakes = mistakes,
                learnerSnapshot = snapshot,
                planningContext = planningContext,
            )
            assertEquals(
                "夹具自证：紧预算当日计划只装 2 题（1200 / 600）",
                2,
                planBundle.queue.size,
            )
            database.saveReviewPlan(planBundle)

            val knowledgePlan = planner.currentKnowledgeReviewPlan(
                requestId = "3e-device-budget",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            assertEquals(
                "预算 1200s / 180s = 6：队列在时间预算处截断（先于每日名额）",
                6,
                knowledgePlan.queue.size,
            )
            assertTrue(
                "F8 精确绑定：6 × 180s = 1080 ≤ 1200 < 7 × 180s = 1260",
                knowledgePlan.queue.size * LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS <=
                    TIGHT_BUDGET_SECONDS &&
                    (knowledgePlan.queue.size + 1) * LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS >
                    TIGHT_BUDGET_SECONDS,
            )
            // 先于名额：6 个槽里 2 个给今日档，只剩 4 个给第二档（< 10）。
            val todayScopeNodes = planBundle.queue.flatMap { it.knowledgeNodeIds }.toSet()
            val weakOutsideToday = WEAK_NODE_IDS.filterNot { it in todayScopeNodes }
            val recoveredOutside = knowledgePlan.queue
                .map { it.knowledgeNodeId }
                .filter { it in weakOutsideToday }
            assertEquals(
                "F8 先于 F17：紧预算下只捞回 4 个队外弱点（名额还剩 6 个没用），" +
                    "queue=${knowledgePlan.queue.map { it.knowledgeNodeId }}",
                4,
                recoveredOutside.size,
            )
            assertEquals(
                "紧预算捞回的必须是队外最老的 4 个",
                expectedOldestOutside(weakOutsideToday, 4),
                recoveredOutside.toSet(),
            )
            assertTrue(
                "夹具自证：队外弱点（${weakOutsideToday.size}）远多于预算能装下的项数",
                weakOutsideToday.size >
                    TIGHT_BUDGET_SECONDS / LogDurationModel.TIER_BASELINE_MEDIUM_SECONDS,
            )
        }
    }

    // ------------------------------------------------------------------
    // 3. 每日/单计划口径（KD-46）：同日第二次排计划捞回下一批 10 个
    // ------------------------------------------------------------------

    /**
     * 每日累计配额（KD-46 批次 51 修复）：同一天第二次排计划**不得**再取满 10。
     *
     * 学生把第一次捞回的 10 个都做了测验：经真实写口落当日 `KNOWLEDGE_QUIZ` 证据行
     * （`recordChatEvidence`，与生产写入口同形状）。第二次排计划时这 10 个已占满当日名额，
     * 扫描档只剩**回放**（同一批 10）——当日并集 ≤ 10，不再出现"下一批 10 个"。
     *
     * 旧用例（`scanQuotaIsPerPlanNotADailyCumulativeTotal`）用 `snapshotAfterMastering`
     * （只改快照、不落证据）模拟"复习完"，并断言"当日累计 20 > 10"——那正是 KD-46 的
     * "每次排计划 ≤10"近似口径。本批把它改写为每日累计口径（**收紧**，不是放宽）。
     */
    @Test
    fun scanQuotaIsDailyCumulativeAcrossPlanCallsInTheSameStudyDay() = runBlocking {
        withPlannerHarness(addFarExam = false) { database, store, _ ->
            val planner = plannerService(database, store, budgetSeconds = FULL_BUDGET_SECONDS)
            val baseSnapshot = seededSnapshot()
            val mistakes = database.observeMistakes().first()
            val planningContext = planner.planningContext(baseSnapshot)
            val planBundle = planner.createReviewPlan(
                mistakes = mistakes,
                learnerSnapshot = baseSnapshot,
                planningContext = planningContext,
            )
            database.saveReviewPlan(planBundle)
            val todayScopeNodes = planBundle.queue.flatMap { it.knowledgeNodeIds }.toSet()
            val weakOutsideToday = WEAK_NODE_IDS.filterNot { it in todayScopeNodes }

            // 第一次排计划：队外最老的 10 个。
            val firstPlan = planner.currentKnowledgeReviewPlan(
                requestId = "3e-device-daily-a",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            val firstRecovered = firstPlan.queue
                .map { it.knowledgeNodeId }
                .filter { it in weakOutsideToday }
            assertEquals(
                "第一次排计划捞回 10 个（F17 当日名额）",
                FULL_LIBRARY_SCAN_DAILY_CAP,
                firstRecovered.size,
            )

            // 学生把这 10 个知识点都做了测验 → 经真实写口落当日 KNOWLEDGE_QUIZ 证据行。
            database.recordChatEvidence(
                firstRecovered.map { nodeId ->
                    knowledgeQuizEvidence(
                        knowledgeNodeId = nodeId,
                        createdAtEpochMillis = PLANNING_AT + 60_000,
                    )
                },
            )

            val secondPlan = planner.currentKnowledgeReviewPlan(
                requestId = "3e-device-daily-b",
                occurredAtEpochMillis = PLANNING_AT,
                mistakes = mistakes,
            )
            val secondRecovered = secondPlan.queue
                .map { it.knowledgeNodeId }
                .filter { it in weakOutsideToday }
            assertTrue(
                "同日第二次排计划不得再取满 10：first=$firstRecovered second=$secondRecovered",
                (firstRecovered + secondRecovered).toSet().size <= FULL_LIBRARY_SCAN_DAILY_CAP,
            )
            assertEquals(
                "当日已复习的扫描点只回放（同一批 10），不取下一批",
                firstRecovered.toSet(),
                secondRecovered.toSet(),
            )
        }
    }

    // ------------------------------------------------------------------
    // 4. 无考试日行为与 JVM 基线一致（指纹对照 + 扫描读门）
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
        assertEquals("预算逐位不变（无考试日）", FINGERPRINT_BUDGET_SECONDS, noExam.bundle.plan.timeBudgetSeconds)
        assertEquals("预算逐位不变（窗口外考试）", FINGERPRINT_BUDGET_SECONDS, farExam.bundle.plan.timeBudgetSeconds)
        assertEquals(
            "两侧计划非空且同队（指纹一致的可见面）",
            noExam.bundle.queue.map { it.practiceUnitId }.sorted(),
            farExam.bundle.queue.map { it.practiceUnitId }.sorted(),
        )
        assertEquals(
            "指纹夹具：预算 120s 只装两道今日题（冷启动 60s/题）",
            2,
            noExam.bundle.queue.size,
        )
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
            val planner = plannerService(database, store, budgetSeconds = FINGERPRINT_BUDGET_SECONDS)
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

    /**
     * 与生产同一构造面（对照 `RoomBackedStudyExperienceRepository` 的接线）。
     *
     * [BudgetResolution.CONSTRUCTOR_ONLY]：显式预算夹具（该类 KDoc 明确只给"按显式预算验证
     * G6/配额数学"的夹具用；生产走 `SETTINGS_THEN_CONSTRUCTOR`，设置存储优先）。不这么做，
     * 真 DataStore 的默认 1200s 会盖掉用例显式传的预算——旧用例就是这么红的。
     *
     * [durationModel]：`ReviewPlannerV2` 会把每道错题的静态估时换成 **learner×subject 桶**的
     * 个性化预测（`ScoredCandidate.withModeledDuration`）。夹具给它 600s/题的真实形状
     * （10 分钟的大题），这样 45 分钟的当日预算只装得下 4 道错题——100+ 个薄弱知识点
     * 才真的落在今日错题并集之外。知识点每项仍按 `TIER_BASELINE_MEDIUM_SECONDS`=180s 计。
     */
    private fun plannerService(
        database: StudyDatabasePort,
        schedulingStore: SchedulingSettingsStore,
        budgetSeconds: Int,
        snapshotProvider: () -> LearnerSnapshot = { seededSnapshot() },
        durationModel: LogDurationModel = seededDurationModel(),
    ): StudyReviewPlannerService = StudyReviewPlannerService(
        database = database,
        learnerId = LEARNER_ID,
        studyZoneId = ZONE,
        clock = CLOCK,
        reviewTimeBudgetSeconds = budgetSeconds,
        planningDecay = DECAY,
        reviewPlannerV2 = ReviewPlannerV2(ForgettingCurve(), durationModel),
        knowledgeNodeScorer = KnowledgeNodeScorer(),
        durationModel = durationModel,
        reviewLogSink = ReviewLogSink(database, LEARNER_ID, CLOCK, ZONE),
        schedulingSettingsStore = schedulingStore,
        predictionAuditService = HLRPredictionAuditService(),
        predictionAuditSink = RoomPredictionAuditSink(database),
        knowledgePrerequisites = KnowledgePrerequisiteReader(database),
        learnerSnapshot = { snapshotProvider() },
        budgetResolution = BudgetResolution.CONSTRUCTOR_ONLY,
    )

    /** 每科一道大题按 600s 计（个性化桶唯一来源：`LogDurationModel.record`）。 */
    private fun seededDurationModel(): LogDurationModel = LogDurationModel().apply {
        SUBJECTS.forEach { subject ->
            record(
                learnerId = LEARNER_ID,
                subjectId = subject,
                itemType = null,
                difficulty = 5.5,
                durationSeconds = MISTAKE_SECONDS.toDouble(),
            )
        }
    }

    /**
     * 确定性学习者快照（同 JVM 基线 `StudyReviewPlannerExamWindowTest` 的形状）：
     * 记忆态按 [PLANNING_AT] 推出。
     *
     * - 弱掌握点 = 绑定题 5–124 天前复习、稳定度 1 天（检索概率随天数单调下降）且无知识点
     *   掌握态（MISSING_KNOWLEDGE_EVIDENCE）——它们的打分**不靠平局**排序，最老的最先捞回。
     *   下次复习时刻 = 复习后 1 天（长期逾期，合法：nextReview ≥ lastReviewed）。
     * - 今日两题 = 400 天前复习（当日计划里分数最高的两道，保证第一档非空）。
     * - 已掌握三点 = 2 天前复习、稳定度 60 天、下次复习在未来 30 天：今日计划排不到它们
     *   （not due），全库扫描也不捞它们（clearly mastered）——"不捞非薄弱点"的负向。
     */
    private fun seededSnapshot(): LearnerSnapshot {
        val memories = buildMap {
            TODAY_UNIT_IDS.forEach { unitId ->
                put(unitId, memoryOf(unitId, lastReviewedDaysAgo = 400))
            }
            WEAK_UNIT_IDS.forEachIndexed { index, unitId ->
                put(unitId, memoryOf(unitId, lastReviewedDaysAgo = weakAgeDays(index)))
            }
            STRONG_UNIT_IDS.forEach { unitId ->
                put(
                    unitId,
                    memoryOf(
                        unitId,
                        lastReviewedDaysAgo = 2,
                        stabilityDays = 60.0,
                        nextReviewInDays = 30,
                    ),
                )
            }
            put(NEG_UNIT_ID, memoryOf(NEG_UNIT_ID, lastReviewedDaysAgo = 60))
        }
        return LearnerSnapshot(
            learnerId = LEARNER_ID,
            problemMemoryStates = memories,
            knowledgeMasteryStates = STRONG_NODE_IDS.associateWith { masteredState(it) },
            checkpoint = ProjectionCheckpoint(
                lastSequence = 0,
                projectorVersion = LearningProjector.VERSION,
                projectedAtEpochMillis = 0,
            ),
            generatedAtEpochMillis = PLANNING_AT,
        )
    }

    /**
     * 一条可复现的记忆态：[lastReviewedDaysAgo] 天前复习、稳定度 [stabilityDays]，
     * 下次复习默认排在复习后 1 天（逾期；`nextReview ≥ lastReviewed` 是模型不变量，
     * 逾期越长 dueRisk 的逾期项越大）。[nextReviewInDays] 为正 = 未来（not due）。
     */
    private fun memoryOf(
        unitId: String,
        lastReviewedDaysAgo: Long,
        stabilityDays: Double = 1.0,
        nextReviewInDays: Long? = null,
    ) = ProblemMemoryState(
        practiceUnitId = unitId,
        stabilityDays = stabilityDays,
        difficulty = 5.5,
        lastReviewedAtEpochMillis = PLANNING_AT - lastReviewedDaysAgo * DAY_MILLIS,
        nextReviewAtEpochMillis = nextReviewInDays?.let { PLANNING_AT + it * DAY_MILLIS }
            ?: (PLANNING_AT - (lastReviewedDaysAgo - 1) * DAY_MILLIS),
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 0,
    )

    /** 已掌握且新鲜的知识点状态（E 判据此刻成立：S=60 ≥ 21、R=1.0 ≥ 0.9，且校准受支持）。 */
    private fun masteredState(nodeId: String) = KnowledgeMasteryState(
        knowledgeNodeId = nodeId,
        masteryScore = 0.95,
        conservativeMasteryScore = 0.9,
        evidenceMass = 2.0,
        successWeight = 2.0,
        memoryStabilityDays = 60.0,
        memoryDifficulty = 5.0,
        lastAttemptAtEpochMillis = PLANNING_AT,
        independentCorrectObservations = listOf(
            IndependentCorrectObservation(
                itemFamilyId = "family-$nodeId",
                studyDayEpochDay = 1,
                occurredAtEpochMillis = PLANNING_AT,
                evidenceWeight = 1.0,
                calibration = CalibrationSnapshot(
                    support = CalibrationSupport.SUPPORTED,
                    sourceId = "3e-fixture-calibration",
                    version = "3e-fixture-v1",
                    validFromEpochMillis = 0,
                    validUntilEpochMillis = PLANNING_AT + DAY_MILLIS,
                ),
            ),
        ),
        status = MasteryStatus.MASTERED,
        calibrationSupport = CalibrationSupport.SUPPORTED,
        projectorVersion = LearningProjector.VERSION,
        checkpointSequence = 0,
        lastEvidenceAtEpochMillis = PLANNING_AT,
    )

    /**
     * 3E 每日扫描配额（KD-46）用例的证据行：知识点测验通道的真实落账形态
     * （`source_kind = KNOWLEDGE_QUIZ`，`created_at` 落在当日学习日窗 `[04:00, +24h)` 内）。
     */
    private fun knowledgeQuizEvidence(
        knowledgeNodeId: String,
        createdAtEpochMillis: Long,
    ) = com.tingyun.smartmistakebook.core.database.entity.LearnerChatEvidenceEntity(
        evidence_id = "knowledge-quiz:3e-device-daily:$knowledgeNodeId",
        learner_id = LEARNER_ID,
        conversation_id = "knowledge-quiz-review:3e-device-daily",
        knowledge_node_id = knowledgeNodeId,
        direction = "NEGATIVE",
        // 与生产写口同值：答错走 MasteryWriteGate.negativeWeight() = 0.35；
        // ChatEvidenceSubmitted 的 MAX_CHAT_EVIDENCE_WEIGHT 也是 0.35（0.5 会被 require 拒绝）。
        weight = 0.35,
        reason_markdown = "知识点测验作答",
        confidence = 1.0,
        source_kind = StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ,
        created_at_epoch_millis = createdAtEpochMillis,
        anchor_class = "CONFIRMED",
    )

    /** 预期捞回集：队外薄弱点里最老的 [count] 个（年龄越大检索概率越低 → 打分越高）。 */
    private fun expectedOldestOutside(
        outsideNodeIds: List<String>,
        count: Int,
    ): Set<String> = outsideNodeIds
        .sortedWith(
            compareByDescending<String> { nodeId -> weakAgeDays(weakIndex(nodeId)) }
                .thenBy { nodeId -> nodeId },
        )
        .take(count)
        .toSet()

    // ------------------------------------------------------------------
    // 种子：多科、绑定题 + 可出题材料、弱掌握点、（真写口）考试日历
    // ------------------------------------------------------------------

    private suspend fun seedPlannerFixture(
        context: Context,
        database: StudyDatabasePort,
        databaseName: String,
    ) {
        val weakUnits = WEAK_NODE_IDS.mapIndexed { index, nodeId ->
            SeededUnit(
                unitId = WEAK_UNIT_IDS[index],
                subject = weakSubject(index),
                nodeId = nodeId,
            )
        }
        val todayUnits = TODAY_NODE_IDS.mapIndexed { index, nodeId ->
            SeededUnit(TODAY_UNIT_IDS[index], TODAY_SUBJECTS[index], nodeId)
        }
        val strongUnits = STRONG_NODE_IDS.mapIndexed { index, nodeId ->
            SeededUnit(STRONG_UNIT_IDS[index], TODAY_SUBJECTS[index], nodeId)
        }
        val negativeUnit = SeededUnit(NEG_UNIT_ID, "MATH", KC_NEG_NO_MATERIAL)
        val allUnits = todayUnits + weakUnits + strongUnits + listOf(negativeUnit)

        // 材料只绑"可出题"节点：今日两点 + 120 弱点 + 3 已掌握 + 只有材料的负向节点。
        // 无材料负向节点刻意**不**绑材料。绑定契约要求节点 ATOMIC + SOURCE_GROUNDED、
        // 材料/来源/节点同科。
        val materialOwners = todayUnits + weakUnits + strongUnits
        val materials = materialOwners.map { unit ->
            materialOf(materialId = "material-${unit.nodeId}", subject = unit.subject, nodeId = unit.nodeId)
        } + materialOf(materialId = "material-$KC_ONLY_MATERIAL", subject = "MATH", nodeId = KC_ONLY_MATERIAL)

        val sources = SUBJECTS.map { subject ->
            KnowledgeSourceSeedRecord(
                sourceId = "source-3e-$subject",
                subject = subject,
                sourceType = "AUTHORIZED_EDUCATION_MATERIAL",
                title = "3E 设备夹具来源($subject)",
                publisher = null,
                edition = null,
                sourceUri = "https://github.com/tianshantingyun/smart-mistake-book/fixtures/3e-${subject.lowercase()}",
                licenseStatus = "PUBLIC_OFFICIAL",
                contentFingerprint = sha256Hex("source-3e-$subject"),
                importedAtEpochMillis = NODE_CREATED_AT,
                // contentUsePolicy 默认 REVIEWED_SYNTHESIS_ONLY，与材料
                // derivationKind=REVIEWED_SYNTHESIS 的许可组合兼容。
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
            bindings = materials.map { material ->
                KnowledgeTeachingMaterialNodeBindingRecord(
                    materialId = material.materialId,
                    knowledgeNodeId = material.materialId.removePrefix("material-"),
                    role = "PRIMARY",
                )
            },
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

        /** 45 分钟（学生可设的量级）：错题 4 题（600s/题）、知识点 15 个（180s/个）。 */
        const val FULL_BUDGET_SECONDS = 2_700

        /** 20 分钟：错题 2 题、知识点 6 个——知识点队列在 F17 名额之前被 F8 截断。 */
        const val TIGHT_BUDGET_SECONDS = 1_200

        /** 指纹对照夹具：20 分钟只装两道今日错题。 */
        const val FINGERPRINT_BUDGET_SECONDS = 1_200

        /** 个性化时长桶的错题估时（10 分钟/题，大题量级）。 */
        const val MISTAKE_SECONDS = 600

        val SUBJECTS = listOf("MATH", "PHYSICS", "CHEMISTRY")

        /** 120 个弱掌握点：按 MATH/PHYSICS/CHEMISTRY 轮转；年龄 5–124 天单调递增。 */
        const val WEAK_NODE_COUNT = 120
        val WEAK_NODE_IDS: List<String> = (1..WEAK_NODE_COUNT).map { index -> "kc-lib-%03d".format(index) }
        val WEAK_UNIT_IDS: List<String> = (1..WEAK_NODE_COUNT).map { index -> "unit-lib-%03d".format(index) }

        val TODAY_NODE_IDS = listOf("kc-today-math", "kc-today-phys")
        val TODAY_UNIT_IDS = listOf("unit-today-math", "unit-today-phys")
        val TODAY_SUBJECTS = listOf("MATH", "PHYSICS", "CHEMISTRY")

        val STRONG_NODE_IDS = listOf("kc-strong-01", "kc-strong-02", "kc-strong-03")
        val STRONG_UNIT_IDS = listOf("unit-strong-01", "unit-strong-02", "unit-strong-03")

        const val KC_NEG_NO_MATERIAL = "kc-neg-no-material"
        const val NEG_UNIT_ID = "unit-neg-no-material"
        const val KC_ONLY_MATERIAL = "kc-only-material"

        val ALL_UNIT_IDS: List<String> =
            TODAY_UNIT_IDS + WEAK_UNIT_IDS + STRONG_UNIT_IDS + NEG_UNIT_ID

        fun weakSubject(index: Int): String = SUBJECTS[index % SUBJECTS.size]

        /** 弱点 index（0 基）的复习天数：5 天起、每个 +1 天（5..124）。 */
        fun weakAgeDays(index: Int): Long = 5L + index

        /** "kc-lib-007" → 6；非弱点 id 不应传入。 */
        fun weakIndex(nodeId: String): Int = nodeId.removePrefix("kc-lib-").toInt() - 1

        val DECAY: Double = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]
    }
}
