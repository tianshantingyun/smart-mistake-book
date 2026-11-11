package com.tingyun.smartmistakebook.core.data.knowledge

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingyun.smartmistakebook.core.data.knowledge.dense.DenseRecallAssembly
import com.tingyun.smartmistakebook.core.data.study.RoomTutorToolRunner
import com.tingyun.smartmistakebook.core.data.study.TutorKnowledgeCodeRegistry
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G-FIRST / G-READ 首查探针仪器（b35-render §3 契约第 6 件；b34 W7 的缺失件）。
 *
 * **契约（冻结；b35-render §3）**：
 * - 装配走生产路径：[StudyDatabaseFactory.open] + [DenseRecallAssembly.reranker] +
 *   [BundledKnowledgeBaseInstaller.install]，**不得**用渲染替身/假 port
 *   （装配先例 = `GoldenRetrievalInstrumentedTest.kt:106-114`）；
 * - `launchT0` 经 instrumentation arg 传入（宿主 `date +%s%3N` 毫秒，T0 = `am start -W`
 *   调用前）；G-FIRST 轮询周期 **250 ms**；输出走 logcat 固定 tag。
 *
 * **实现细节（执行轮落地，已登记）**：
 * - 「首次非空」的判定：`KNOWLEDGE_READ` 的 outcome `ok=true` 且 summaryMarkdown 含
 *   「知识点候选」——排除「还在准备中」（D-Q3 占位）与「没有匹配」两种**空形态**，
 *   非空 = 真的渲染出了节点行。
 * - `FIRST_QUERY_MS` = 完成时刻 `System.currentTimeMillis()`（设备墙钟）− `launchT0`
 *   （宿主墙钟）。口径边界（b35-render §2.6 已冻结登记）：探针读数含仪器内装配开销
 *   （install + 索引构建），两臂同探针同装配 ⇒ 差分可比；绝对值不外推产品进程首查。
 *   交叉时钟前提（如实登记）：设备与宿主墙钟 NTP 对齐；偏差进入两臂读数各一个同号
 *   常量，差分时抵消。
 * - `G_READ_MS` = 那次**成功的** `runTraced` 调用的 invoke → result 耗时
 *   （`SystemClock.elapsedRealtime`，设备单调钟，G-READ 只报告不设门）。
 * - 轮询每 250 ms 发一次 `KNOWLEDGE_READ`；总上限 120 s（实现细节），超限 = 本测试
 *   失败 → 编排脚本按「probe emitted no metrics」ABORT，不会静默出数。
 * - 探针常量（冻结实现选择）：科目 `MATH`、terms `二次函数`（与生产 KNOWLEDGE_READ
 *   执行先例 `RoomModelTaskToolLoopInstrumentedTest.kt:545-623` 同源）。
 * - 度量行同时走 `println`（进 `am instrument -w` stdout，供
 *   `tools/kb_perf/first_query_20x.sh` 逐轮 grep）与 `Log.i`（固定 tag，§3 契约）。
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeFirstQueryProbeInstrumentedTest {

    @Test
    fun firstKnowledgeReadFromColdInstallEmitsProbeMetrics() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val launchT0 = requireNotNull(args.getString(ARG_LAUNCH_T0)?.toLongOrNull()) {
            "missing instrumentation arg $ARG_LAUNCH_T0 (host date +%s%3N ms; first_query_20x.sh always supplies it)"
        }
        val round = args.getString(ARG_ROUND) ?: "0"

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "kb-first-query-probe-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        // 生产装配（§3 契约）：稠密腿在设备上装配（可能为 null = 生产同型回退，如实记日志），
        // 真 installer、真库、真索引——不用任何渲染替身。
        val denseRerank = DenseRecallAssembly.reranker(context)
        Log.i(
            TAG,
            "round=$round denseRerank=${if (denseRerank != null) "assembled" else "absent(lexical fallback)"}",
        )
        val store = StudyDatabaseFactory.open(context, databaseName, denseRerank = denseRerank)
        try {
            BundledKnowledgeBaseInstaller.install(store)

            // 生产 KNOWLEDGE_READ 执行面：真 runner（core:data internal，同模块 androidTest
            // 可达——先例 RoomModelTaskToolLoopInstrumentedTest.kt:589 已触 internal toolRunner）。
            val runner = RoomTutorToolRunner(
                port = store,
                knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Ready),
            )
            val registry = TutorKnowledgeCodeRegistry()
            val toolContext = RoomTutorToolRunner.Context(
                subject = PROBE_SUBJECT,
                knowledgeCodeRegistry = registry,
            )
            val call = TutorToolCall(
                tool = TutorToolName.KNOWLEDGE_READ,
                rationale = "首查探针：测量冷装后第一次知识查询的返回时间",
                terms = listOf(PROBE_QUERY),
            )

            val deadline = android.os.SystemClock.elapsedRealtime() + POLL_BUDGET_MILLIS
            var firstNonEmptyMs = -1L
            var gReadInvokeMs = -1L
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                val invokeStartedAt = android.os.SystemClock.elapsedRealtime()
                val execution = runner.runTraced(call, toolContext)
                val invokeMs = android.os.SystemClock.elapsedRealtime() - invokeStartedAt
                val summary = execution.outcome.summaryMarkdown
                Log.d(TAG, "round=$round attempt ok=${execution.outcome.ok} len=${summary.length}")
                if (execution.outcome.ok && summary.contains(NON_EMPTY_SENTINEL)) {
                    // G-FIRST：T0（宿主 am start -W 前）→ 首次非空（设备墙钟）；G-READ：该次 invoke 时长。
                    firstNonEmptyMs = System.currentTimeMillis() - launchT0
                    gReadInvokeMs = invokeMs
                    break
                }
                delay(POLL_PERIOD_MILLIS)
            }
            assertTrue(
                "probe produced no non-empty KNOWLEDGE_READ within ${POLL_BUDGET_MILLIS} ms " +
                    "(subject=$PROBE_SUBJECT terms=$PROBE_QUERY)",
                firstNonEmptyMs >= 0,
            )
            // stdout 半边：first_query_20x.sh 在 am instrument 输出上逐轮 grep 这两行。
            println("FIRST_QUERY_MS=$firstNonEmptyMs")
            println("G_READ_MS=$gReadInvokeMs")
            // logcat 半边（§3 契约的固定 tag）。
            Log.i(
                TAG,
                "round=$round FIRST_QUERY_MS=$firstNonEmptyMs G_READ_MS=$gReadInvokeMs",
            )
            // 探针自证：这次非空结果真的带节点行（防止把占位句误当命中）。
            val features = KnowledgeSearchFeatureExtractor.fromQuestion(PROBE_QUERY)
            val renderedNodes = store.readSubjectKnowledgeRecallCandidates(
                subject = PROBE_SUBJECT,
                searchFeatures = features,
                limit = 5,
                queryText = PROBE_QUERY,
            ).candidates.size
            assertTrue("recall probe expected rendered nodes", renderedNodes > 0)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }

    private companion object {
        const val TAG = "KnowledgeFirstQueryProbe"
        const val ARG_LAUNCH_T0 = "launchT0"
        const val ARG_ROUND = "round"
        const val NON_EMPTY_SENTINEL = "知识点候选"
        const val PROBE_SUBJECT = "MATH"
        const val PROBE_QUERY = "二次函数"
        const val POLL_PERIOD_MILLIS = 250L
        const val POLL_BUDGET_MILLIS = 120_000L
    }
}
