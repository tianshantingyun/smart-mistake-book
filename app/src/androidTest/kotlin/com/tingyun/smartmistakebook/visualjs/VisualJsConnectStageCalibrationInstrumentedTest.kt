package com.tingyun.smartmistakebook.visualjs

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualExecutionBudget
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * KD-76 冷启动连接阶段校准的设备用例（批次 39 T4 落码；统计口径照 R2
 * `docs/research/2026-11-01-kd76-stats.md`，seam 设计照
 * `docs/research/2026-11-01-kd76-timing-calibration.md`）。
 *
 * **它消灭的具体失败**（不是"更完备"）：
 * 1. KD-76 的 `READY_TIMEOUT_MILLIS=10 s` 只有服务端口径（AMS `Start proc` →
 *    `warm-up finished`，E-04 `step04-warmup-cost.md` §1 明示"不含宿主 bind/握手排队"），
 *    客户端可感口径（bind → ready）**没有任何数字**；本用例经
 *    [VisualJsClientEngine.connectStageTimestampsForTest] 取 per-bind 四段实测。
 * 2. 该 seam 此前在 `app/src/androidTest` **0 引用**（E-08 §1–§2），即"seam 已落但无消费方"
 *    ——校准数据源存在与否不可分辨；本用例是它的第一个设备消费方，并用**错 generation
 *    负例**钉住过滤（[VisualJsClientEngine.connectStageTimestampsForTest] 传非当前 link 的
 *    generation 必须返回 null；只传对 generation 无法发现"忽略参数恒返回当前记录"的坏实现）。
 * 3. 四段推导缺失时，"首请求慢"无法归因到 bind / warm-up / ready 轮询 / 首字节
 *    （KD-76 ⑤ 与 29.5 s 链的共同缺口）。
 *
 * **预注册（A10，跑之前固定，运行期不得改）**：
 * - 样本量 [CALIBRATION_ROUNDS] = 20 冷启动轮，分两批各 [BATCH_SIZE] = 10 轮；合并原始
 *   样本 n=20 后 `p95` = 第 19/20 个（容忍一个最大值），这是 R2 §2.3 的默认预注册形态。
 *   **单批 n=10 时 nearest-rank `p95` ≡ `max`**（R2 §2.2），因此单批不单独作 G2 门——
 *   那会与 anti-flaky 的"单点离群不红"自相矛盾；单批只出数并接受 G1（零失败）。
 * - 估计量 [ESTIMATOR_NAME]：`sorted[ceil(0.95n)-1]`，与仓库既有 Kotlin 门同式
 *   （`core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt:605-610`）。
 *   **不得**换成线性插值（E-04 的 725 是插值口径，两者不可比，R2 §2.1）。
 * - 门 G2 按 **R2 §3.2:132 的主口径**：`M = S-bind + S-warmup`（bind 请求→ready，**逐轮
 *   先求和再对和取 p95**，不是"把两段的 p95 相加"），`M.p95 ≤ READY_TIMEOUT / 2`。
 *   **S-warmup 单段只是 M 的分量（C2，不含 bind/握手排队），不得单独作门**——只对 warmUpMillis 施门
 *   会把主口径缩窄成 ready−bindConnected，恰好漏掉唯一有 9229 ms 离群的 S-bind 段。
 *   READY_TIMEOUT 是 [VisualJsClientEngine] 的 companion 常量（`VisualJsClientEngine.kt:1325`），
 *   本用例经反射读取生产常量并与 [READY_BUDGET_MILLIS] 比对（见 [assertPreRegistration]）；
 *   门由预算派生、**不追加容差**（A4）。
 * - outlier：不删点、不缩尾、不重跑到绿；失败轮记删失（值 ≥ 对应预算）并走 G1 硬门
 *   （R2 §2.5/§3.1）。
 *
 * **四段**（写点 `VisualJsClientEngine.kt`：`:670` bindRequested、`:1161`
 * bindConnected、`:821`/`:840` ready 两成功出口、`:401` executeAccepted、`:570`
 * firstByte，全部同一 `SystemClock.uptimeMillis()`）：
 * - S-bind = bindConnected − bindRequested（= 契约链 C1）
 * - S-warmup = ready − bindConnected（= C2 分量；**不含** bind/握手排队，客户端可感口径是
 *   M = S-bind + S-warmup，不是本段；100 ms 轮询量化）
 * - S-ready-gap = executeAccepted − ready（⊂ C3）
 * - S-first-byte = firstByte − executeAccepted（⊂ C3）
 *
 * **不声称的**（R2 §4.2/§4.5）：S-ready-gap + S-first-byte 不是 C3 实测；C4（死亡确认）与
 * C3 宿主期限分布本用例不测；四段的独立 p95 **不可相加**成链 p95（主口径 M 是逐轮求和后
 * 再取 p95，与"p95 相加"是两种不同统计量）。29 500 ms 只是预算包络
 * （15 000 + 10 000 + 2 500 + 2 000），本用例只出 S1/S2 的实测分布。
 *
 * **冷启动形状与预注册偏差（必须先读）**：每轮新建 [VisualJsClientEngine]、bind 到一个
 * **新 fork 的** `:visual_js` 进程（上一轮 `terminate` → 进程自杀 → 死亡确认后才进下一轮），
 * 轮内不预热、不先 execute（A11）。但本用例是**单个 instrumentation 进程内 20 轮**，
 * 跨轮与跨套件的 OS 页缓存是热的——口径是"冷进程 + 热页缓存"（与 E-01d 的 4.6–7.5 s
 * 同形），**不是** R2 §3.2 要求的"`adb reboot` 后不沉降即跑"的真实冷启动轮。因此：
 * - 本用例的数字**不满足主口径的预注册执行条件**（无 reboot、两批不是两次独立设备运行），
 *   只能作"冷进程 + 热页缓存"观测；即使 n=20 全绿也不能单独用作关闭 KD-76 的证据。
 * - 设备轨若按主口径执行，必须 `adb reboot` 后**只跑一轮/一次 am instrument**（或每样本
 *   重启），且逐样本落盘；单次运行内 20 轮的分布不能冒充该形状。
 * - 第 1 轮若偶然复用暖进程，段值会偏小——这是已知**乐观偏差**窗口（假绿方向，不掩盖
 *   真失败）；它被限定在 1/20 样本，且热页缓存本身已让全部样本系统性偏小。
 *
 * **anti-flaky 检查表逐条落点**（R2 §5）：
 * - A1 归因先行 → 非 `Rendered` 先记 FAIL（不读任何时长）；只有 `Rendered` 轮才进入
 *   [deriveStages] 的 `started()`/五点单调/窗口内断言，然后才算段值；
 * - A2 单钟 → 每轮用 `SystemClock.uptimeMillis()` 夹住整个轮次，断言 seam 五点都落在
 *   `[roundStart, roundEnd]` 内（换 `elapsedRealtime`/`nanoTime` 会越界）；
 * - A3 无扣除 → 每段只由自己的两个时间戳相减；五个戳都必须 > 0，且 `total == 四段之和`
 *   只作**定义域一致性**检查（该等式代数恒等，抓不到漏写；漏写由 `>0` 与单调断言抓）；
 * - A4 相对门 → `READY_GATE_MILLIS = READY_BUDGET_MILLIS / 2` + 反射读生产常量比对；
 * - A5 失败不丢 → 失败轮记 `Failed(censoredLowerBoundMillis)`，全部跑完后 G1 断言零失败；
 * - A6 两轮独立 → 两批各自完整成形 + 各自 G1 + 同 APK/同指纹；**注意**：两批在同一个
 *   instrumentation 进程内，真正的"两轮独立设备运行"由设备轨两次执行承担，测试不能自证；
 * - A7 不重试洗绿 → 断言 `sample + failure == CALIBRATION_ROUNDS` 且轮次 id 无重复；
 *   没有重试分支可言，这条是**结构约束的记录**，不是可证伪的性能门；
 * - A8 同轮元数据 → 每轮前后 loadavg、每批前后抓 APK sha256/指纹/SDK/ABI/page size；
 * - A9 披露分辨率 → 报告写明 S-warmup 由 100 ms 轮询量化、<100 ms 差异不解读为性能差异
 *   （这是披露规则，不是单轮可证伪的断言；可断言的一半是轮询常量镜像）；
 * - A10 预注册 → [assertPreRegistration] 钉住 n/估计量/门/自检向量；
 * - A11 冷启动不预热 → 新引擎首访 `connectStageTimestampsForTest() == null`、
 *   connect 与 execute 后 `connectionCountForTest() == 1`（轮内未重建）、worker UID 不等于
 *   instrumentation 进程 UID；
 * - A12 屏幕/睡眠可观测 → 每轮与每批边界读 `PowerManager.isInteractive`；不交互按
 *   INVALID 批处理（`uptimeMillis` 在 deep sleep 暂停会让段值偏小）。
 *
 * 设备执行归设备轨；本轨不运行 Gradle（共享纪律）。
 */
@RunWith(AndroidJUnit4::class)
class VisualJsConnectStageCalibrationInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()

    @After
    fun closeEverything() {
        engines.forEach { it.close() }
        engines.clear()
    }

    @Test
    fun coldStartConnectStagesMeetTheReadyHalfBudgetAcrossTwoBatches() = runBlocking {
        // A10：预注册先于任何计时。
        assertPreRegistration()

        val metadata = captureMetadata()
        assertMetadataComplete(metadata)

        // 证据先行（复核第 2 轮 #6④）：批边界/轮内断言可能在任何 emit 之前中止
        // （典型：T1 形状的 reboot 后设备熄屏 → 批首 `screenIsInteractive()` 断言抛错）。
        // 这里捕获中止并**至少落一份部分报告 + 一行 ABORTED 原因**，避免"连一行样本都不落盘"。
        var batchAResult: BatchOutcome? = null
        var batchBResult: BatchOutcome? = null
        try {
            batchAResult = runBatch("A")
            batchBResult = runBatch("B")
        } catch (aborted: Throwable) {
            val partialA = batchAResult
            val partialB = batchBResult
            if (partialA != null) {
                emit(
                    buildReport(
                        metadata,
                        partialA,
                        partialB ?: BatchOutcome("B", metadata, emptyList(), emptyList()),
                        partialA.samples + (partialB?.samples ?: emptyList()),
                    ),
                )
            }
            emit(
                listOf(
                    "KD-76 calibration ABORTED before a complete report: " +
                        (aborted.message ?: aborted::class.java.name),
                    "device: fingerprint=${metadata.fingerprint} | sdk=${metadata.sdkInt} | " +
                        "apkSha256=${metadata.apkSha256}",
                ),
            )
            throw aborted
        }
        val batchA = checkNotNull(batchAResult) { "batch A did not complete" }
        val batchB = checkNotNull(batchBResult) { "batch B did not complete" }
        val samples = batchA.samples + batchB.samples

        // 证据先行：即使后面判红，四段分布与失败集也必须已经落盘/落日志。
        emit(buildReport(metadata, batchA, batchB, samples))

        // G1（与 G2 合取）：任何 FAIL 都不能被 p95 掩盖（R2 §3.3）。
        val failures = batchA.failures + batchB.failures
        assertTrue(
            "G1 red: ${failures.size} cold-start round(s) failed; each is a censored sample " +
                "(value ≥ budget), not a missing value. Attribution (env/fixture/snapshot/product) " +
                "is required before any p95 reading (R2 §2.5/§3.2): " +
                failures.joinToString("; ") {
                    "${it.roundId}: ${it.reason} (censored ≥ ${it.censoredLowerBoundMillis} ms)"
                },
            failures.isEmpty(),
        )

        // A7/A10：预注册轮数各执行恰一次（无重试、无提前停、无补跑到绿）。
        // 注意：这是**结构约束的记录**——循环上界固定为 BATCH_SIZE，断言本身不能
        // 证伪"存在重试分支"；它能抓到的是失败轮被吞掉（样本+失败 ≠ 20）或轮次 id 重复。
        assertEquals(
            "A7/A10: every pre-registered round must end as exactly one sample or one failure " +
                "($CALIBRATION_ROUNDS rounds, no retry, no optional stopping)",
            CALIBRATION_ROUNDS,
            samples.size + failures.size,
        )
        assertEquals(
            "A7: every pre-registered round id must appear exactly once",
            CALIBRATION_ROUNDS,
            (samples.map { it.roundId } + failures.map { it.roundId }).distinct().size,
        )
        // 批"完整"= 每个预注册轮次都有结局（样本或记录在案的失败），不是"全部成功"。
        assertEquals(
            "A6: each batch must account for all $BATCH_SIZE pre-registered rounds",
            BATCH_SIZE,
            batchA.samples.size + batchA.failures.size,
        )
        assertEquals(
            "A6: each batch must account for all $BATCH_SIZE pre-registered rounds",
            BATCH_SIZE,
            batchB.samples.size + batchB.failures.size,
        )

        // A6/A8：两批合并的前提是同一构建/同一设备条件（R2 §2.3 #3）。
        assertEquals(
            "A8/G1: both batches must run the same APK (sha256)",
            batchA.metadata.apkSha256,
            batchB.metadata.apkSha256,
        )
        assertEquals(
            "A8: both batches must run on the same device fingerprint",
            batchA.metadata.fingerprint,
            batchB.metadata.fingerprint,
        )
        assertEquals(
            "A8: both batches must run on the same page size",
            batchA.metadata.pageSizeBytes,
            batchB.metadata.pageSizeBytes,
        )
        assertBatchMediansAreMergeable(batchA, batchB)

        val mergedMain = samples.map { it.stages.bindMillis + it.stages.warmUpMillis }.stats()
        val mergedWarmUp = samples.map { it.stages.warmUpMillis }.stats()
        val gate = READY_GATE_MILLIS
        // G2：主口径 M = 每轮 (S-bind + S-warmup) —— 先逐轮求和、再对和取 p95（R2 §3.2:132）；
        // 不是"把 S-bind 与 S-warmup 各自的 p95 相加"。S-warmup 单段只作分量报告，不单独作门。
        // 不放宽——不换插值（该换法会把 p95 变小）、不换 p90、不删离群点、不加容差。
        assertTrue(
            "G2 red: main criterion M=S-bind+S-warmup nearest-rank p95=${mergedMain.p95} ms > " +
                "READY_TIMEOUT/2=$gate ms (n=${mergedMain.n}, estimator=$ESTIMATOR_NAME). " +
                "The criterion must not be narrowed to S-warmup alone and must not be relaxed " +
                "(no interpolation, no p90, no point removal, no tolerance): R2 §3.2/§2.3 #4. " +
                "Sorted M=${mergedMain.sorted}; S-warmup component p95=${mergedWarmUp.p95} ms",
            mergedMain.p95 <= gate,
        )
    }

    // ---------------------------------------------------------------- 批次/轮次

    private suspend fun runBatch(name: String): BatchOutcome {
        // A11：不是预热，是"没有活的前任进程"的沉降间隔；旧 worker 已由上一轮的
        // abandon 死亡确认保证终结，这里只吸收调度/AMS 收尾的抖动。
        delay(SETTLE_BETWEEN_ROUNDS_MILLIS)
        // A12：睡前出现在批边界 → 整批 INVALID（uptimeMillis 在 deep sleep 暂停）。
        assertTrue(
            "batch $name INVALID: the screen is not interactive at batch start; uptimeMillis " +
                "pauses in deep sleep, so the segment values would be biased low (R2 A12)",
            screenIsInteractive(),
        )

        val metadataBefore = captureMetadata()
        assertMetadataComplete(metadataBefore)

        val samples = mutableListOf<RoundOutcome.Sample>()
        val failures = mutableListOf<RoundOutcome.Failed>()
        for (offset in 0 until BATCH_SIZE) {
            val roundId = String.format(Locale.US, "%s-%02d", name, offset + 1)
            when (val outcome = runOneRound(roundId)) {
                is RoundOutcome.Sample -> samples.add(outcome)
                is RoundOutcome.Failed -> failures.add(outcome)
            }
        }

        assertTrue(
            "batch $name INVALID: the screen stopped being interactive mid-batch (R2 A12)",
            screenIsInteractive(),
        )
        val metadataAfter = captureMetadata()
        assertEquals(
            "batch $name INVALID: the APK changed mid-batch; the round set is not one snapshot (A8)",
            metadataBefore.apkSha256,
            metadataAfter.apkSha256,
        )
        return BatchOutcome(name, metadataBefore, samples, failures)
    }

    private suspend fun runOneRound(roundId: String): RoundOutcome {
        val engine = VisualJsClientEngine(context).also { engines.add(it) }
        try {
            // A11：新引擎不得带任何已开始的 bind 记录（否则不是冷启动轮）。
            assertNull(
                "$roundId INVALID: a fresh engine already exposes a started bind record " +
                    "(a warm/pre-warmed engine would erase the cold-start cost this case measures)",
                engine.connectStageTimestampsForTest(),
            )
            // A12：逐轮屏幕可观测（不是只在批边界）。
            assertTrue(
                "$roundId INVALID: the screen is not interactive; the segment values would be " +
                    "biased low by uptimeMillis pausing in deep sleep (R2 A12)",
                screenIsInteractive(),
            )
            val loadBefore = readLoadAverage(roundId)

            // A2：同一个 SystemClock.uptimeMillis() 域夹住整轮，用于交叉核对 seam 五点。
            val roundStartUptime = SystemClock.uptimeMillis()
            val connected = engine.connectForTest()
            if (!connected) {
                // A5：失败轮是删失观测，不是缺失值；值 ≥ 首连预算。不重试（A7）。
                delay(SETTLE_BETWEEN_ROUNDS_MILLIS)
                return RoundOutcome.Failed(
                    roundId = roundId,
                    reason = "connectForTest() == false (attach/ready budget expired or the " +
                        "worker died before ready; attribution per R2 §3.2 is required)",
                    censoredLowerBoundMillis = FIRST_CONNECT_BUDGET_MILLIS,
                )
            }

            // G0 / A1：先断言轮次形状，形状不对就不进入计时判读。
            assertEquals(
                "$roundId INVALID: a cold-start round must connect exactly once",
                1,
                engine.connectionCountForTest(),
            )
            assertFalse(
                "$roundId INVALID: a successful cold start must not latch the engine unavailable",
                engine.unavailableForTest(),
            )
            val generation = checkNotNull(engine.generationForTest()) {
                "$roundId INVALID: connected without a link generation"
            }
            // A11/T1 §5.1：校准测的是进程隔离下的冷启动；worker 若与 instrumentation 同 UID，
            // 测到的就不是"新 fork 的 :visual_js 进程"的装载/握手成本。
            val workerUid = checkNotNull(engine.workerUidForTest()) {
                "$roundId INVALID: connected without a worker UID; the isolation premise is gone"
            }
            assertNotEquals(
                "$roundId INVALID: the worker UID equals the instrumentation process UID " +
                    "(${Process.myUid()}); a non-isolated worker invalidates the cold-start cost",
                Process.myUid().toLong(),
                workerUid.toLong(),
            )

            val result = engine.execute(pureGeometryRequest())
            val roundEndUptime = SystemClock.uptimeMillis()

            if (result !is VisualExecutionResult.Rendered) {
                // A5（R2 §2.5）：execute 非 `Rendered` 是产品失败轮——记删失（值 ≥ 宿主期限），
                // 不丢样本、不重试；它不是 INVALID（INVALID 只给 seam/夹具/时间戳形状缺陷），
                // 也不是"0 ms 段长"。时长对失败轮没有意义（A1），这里不读任何段值。
                runCatching { engine.abandonForTest() }
                delay(SETTLE_BETWEEN_ROUNDS_MILLIS)
                return RoundOutcome.Failed(
                    roundId = roundId,
                    reason = "execute produced ${describe(result)} instead of a rendered result",
                    censoredLowerBoundMillis = VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS,
                )
            }
            // A11：测量窗口内不得重建（重建会让 generation/连接账换成另一个进程）。
            assertEquals(
                "$roundId INVALID: the measured window rebuilt the worker " +
                    "(a second connect would make this a warm-start sample)",
                1,
                engine.connectionCountForTest(),
            )

            // generation 过滤：读到的必须是本轮那个 link 的记录，不是重建后的新实例。
            val timestamps = checkNotNull(engine.connectStageTimestampsForTest(generation)) {
                "$roundId INVALID: the seam returned null for generation $generation " +
                    "(the record is gone or belongs to another link)"
            }
            // 负例才钉得住过滤：一个忽略参数、恒返回当前 link 记录的实现也能通过"传对
            // generation"的正例；只有"传错 generation ⇒ null"能区分两者（T1 §5.1）。
            assertNull(
                "$roundId INVALID: the seam returned the current link's record for a foreign " +
                    "generation (${generation + 1}); the generation filter is not pinned",
                engine.connectStageTimestampsForTest(generation + 1),
            )
            val stages = deriveStages(roundId, timestamps, roundStartUptime, roundEndUptime)
            val loadAfter = readLoadAverage(roundId)

            // A11：确认本轮 worker 死亡后才允许下一轮 bind——否则下一轮不是冷启动。
            val deathConfirmed = engine.abandonForTest()
            assertTrue(
                "$roundId INVALID: the worker death was not confirmed within the 2 s budget; " +
                    "the next round could attach to the same live process and stop being a " +
                    "cold start (R2 §2.5 INVALID)",
                deathConfirmed,
            )
            delay(SETTLE_BETWEEN_ROUNDS_MILLIS)

            return RoundOutcome.Sample(
                roundId = roundId,
                generation = generation,
                stages = stages,
                loadBefore = loadBefore,
                loadAfter = loadAfter,
            )
        } finally {
            // 失败路径也保证不留孤儿 binding/进程（terminate + unbind）。
            engine.close()
        }
    }

    /**
     * G0/A1/A2/A3：四段全部由 per-bind 记录的五个时间戳相减得到。
     *
     * 顺序即断言：先形状（started、五点单调、都在本轮 uptime 窗口内），再时长。
     */
    private fun deriveStages(
        roundId: String,
        timestamps: ConnectStageTimestamps,
        roundStartUptime: Long,
        roundEndUptime: Long,
    ): StageDurations {
        assertTrue(
            "$roundId INVALID: the bind record is not started (bindRequestedAt=0)",
            timestamps.started(),
        )
        // 漏写时间戳时单看 total == 四段之和抓不到（0 也满足该代数恒等式）；这里逐个
        // 断言"每个语义写点都真的落过"，与单调性断言一起构成缺段守卫。
        assertTrue(
            "$roundId INVALID: bindConnectedAt=0 — the handshake completion stamp was never written",
            timestamps.bindConnectedAtMillis > 0L,
        )
        assertTrue(
            "$roundId INVALID: readyAt=0 — no ready exit wrote its stamp",
            timestamps.readyAtMillis > 0L,
        )
        assertTrue(
            "$roundId INVALID: executeAcceptedAt=0 — the accepted-execute stamp was never written",
            timestamps.executeAcceptedAtMillis > 0L,
        )
        assertTrue(
            "$roundId INVALID: bindRequestedAt=${timestamps.bindRequestedAtMillis} is outside " +
                "the round window [$roundStartUptime, $roundEndUptime] — the seam clock is not " +
                "SystemClock.uptimeMillis (A2 single clock)",
            timestamps.bindRequestedAtMillis in roundStartUptime..roundEndUptime,
        )
        assertTrue(
            "$roundId INVALID: firstByteAt=${timestamps.firstByteAtMillis} is outside " +
                "the round window [$roundStartUptime, $roundEndUptime] — the seam clock is not " +
                "SystemClock.uptimeMillis (A2 single clock)",
            timestamps.firstByteAtMillis in roundStartUptime..roundEndUptime,
        )
        // 单调性：任何倒序都说明记录被跨实例/跨阶段误写（R2 §2.5 INVALID）。
        assertTrue(
            "$roundId INVALID: bindConnectedAt=${timestamps.bindConnectedAtMillis} < " +
                "bindRequestedAt=${timestamps.bindRequestedAtMillis}",
            timestamps.bindConnectedAtMillis >= timestamps.bindRequestedAtMillis,
        )
        assertTrue(
            "$roundId INVALID: readyAt=${timestamps.readyAtMillis} < " +
                "bindConnectedAt=${timestamps.bindConnectedAtMillis} (the ready stamp must be " +
                "written after the handshake; 0 would mean 'never ready')",
            timestamps.readyAtMillis >= timestamps.bindConnectedAtMillis,
        )
        assertTrue(
            "$roundId INVALID: executeAcceptedAt=${timestamps.executeAcceptedAtMillis} < " +
                "readyAt=${timestamps.readyAtMillis}",
            timestamps.executeAcceptedAtMillis >= timestamps.readyAtMillis,
        )
        assertTrue(
            "$roundId INVALID: firstByteAt=${timestamps.firstByteAtMillis} < " +
                "executeAcceptedAt=${timestamps.executeAcceptedAtMillis} (first byte must follow " +
                "the accepted execute)",
            timestamps.firstByteAtMillis >= timestamps.executeAcceptedAtMillis,
        )
        assertTrue(
            "$roundId INVALID: firstByteAt=0 — the first-byte stamp is only written on the " +
                "success path, and this round rendered, so 0 means the record was reset",
            timestamps.firstByteAtMillis > 0L,
        )

        // A3：每段只由自己的两个时间戳相减，不反推、不扣除任何"传输段"。
        val bindMillis = timestamps.bindConnectedAtMillis - timestamps.bindRequestedAtMillis
        val warmUpMillis = timestamps.readyAtMillis - timestamps.bindConnectedAtMillis
        val readyGapMillis = timestamps.executeAcceptedAtMillis - timestamps.readyAtMillis
        val firstByteMillis = timestamps.firstByteAtMillis - timestamps.executeAcceptedAtMillis
        val totalMillis = timestamps.firstByteAtMillis - timestamps.bindRequestedAtMillis

        // A2/A3：单钟 + 无扣除的**定义域一致性**检查。该等式代数恒等
        // （firstByte − bindRequested ≡ 四段之和），任何段值下都成立；它不能证明"少写了
        // 一段"——缺段由上面的 `>0` 与单调/窗口断言抓。保留它是为了在重构段推导时立刻
        // 暴露"定义被改了口径"（例如引入扣除项或换钟后再相减）。
        assertEquals(
            "$roundId INVALID: total($totalMillis) != bind($bindMillis) + warmUp($warmUpMillis) + " +
                "readyGap($readyGapMillis) + firstByte($firstByteMillis); segments must be the " +
                "five stamps of one record with no deduction/compensation term (A3)",
            totalMillis,
            bindMillis + warmUpMillis + readyGapMillis + firstByteMillis,
        )

        return StageDurations(
            bindMillis = bindMillis,
            warmUpMillis = warmUpMillis,
            readyGapMillis = readyGapMillis,
            firstByteMillis = firstByteMillis,
            totalMillis = totalMillis,
        )
    }

    // ---------------------------------------------------------------- 门/预注册

    private fun assertPreRegistration() {
        // A10：估计量自检（R2 §2.3 #5）——固定向量 0..9：p50=4、p95=9（n=10 时 p95≡max）。
        val fixed = (0L..9L).toList()
        assertEquals(
            "the percentile estimator must be nearest-rank (ceil), same formula as " +
                "GoldenRetrievalInstrumentedTest.kt:605-610 (A10)",
            4L,
            fixed.percentileNearestRank(50),
        )
        assertEquals(
            "with n=10, nearest-rank p95 must equal the max (9); an interpolating estimator " +
                "would return a smaller value and silently relax the gate (R2 §2.2/A10)",
            9L,
            fixed.percentileNearestRank(95),
        )
        assertTrue(
            "the pre-registered sample must be at least 10 cold-start rounds (plan task 4)",
            CALIBRATION_ROUNDS >= 10,
        )
        assertEquals("the pre-registered sample must split into two complete batches (A6)", 2, CALIBRATION_ROUNDS / BATCH_SIZE)
        assertEquals("the pre-registered sample must not leave a partial batch", 0, CALIBRATION_ROUNDS % BATCH_SIZE)
        // A4 + 判据钉住：**读生产类常量本身**，不是只比较本用例自己的两个常量——
        // 只比较本地镜像时，生产侧单独改动（例如把 READY_TIMEOUT 调到 20 s）不会让本用例红，
        // 那组断言就是自证。`const val` 编译为 `VisualJsClientEngine` 上的
        // `public static final long`（本机 javap 实证），反射读取无副作用。
        assertEquals(
            "READY_TIMEOUT_MILLIS must stay 10_000 ms in VisualJsClientEngine (production " +
                "constant, read by reflection); if the budget changes, this mirror and the " +
                "KD-76 criterion must be updated in the same change",
            10_000L,
            productionLongConstant("READY_TIMEOUT_MILLIS"),
        )
        assertEquals(
            "the test mirror READY_BUDGET_MILLIS must equal the production READY_TIMEOUT_MILLIS",
            productionLongConstant("READY_TIMEOUT_MILLIS"),
            READY_BUDGET_MILLIS,
        )
        assertEquals(
            "the p95 gate must be derived as READY_TIMEOUT/2 (A4), never hard-coded wider",
            READY_BUDGET_MILLIS / 2,
            READY_GATE_MILLIS,
        )
        assertEquals(
            "the gate must be exactly 5_000 ms for READY_TIMEOUT=10_000 (KD-76 判据不得放宽)",
            5_000L,
            READY_GATE_MILLIS,
        )
        assertEquals(
            "the ready poll quantization must stay 100 ms in VisualJsClientEngine (production " +
                "constant, read by reflection; A9)",
            100L,
            productionLongConstant("READY_POLL_INTERVAL_MILLIS"),
        )
        assertEquals(
            "the test mirror READY_POLL_INTERVAL_MILLIS must equal the production constant",
            productionLongConstant("READY_POLL_INTERVAL_MILLIS"),
            READY_POLL_INTERVAL_MILLIS,
        )
    }

    /** 读取 `VisualJsClientEngine` 的 companion `const val`（JVM: public static final long）。 */
    private fun productionLongConstant(field: String): Long {
        val declared = try {
            VisualJsClientEngine::class.java.getDeclaredField(field)
        } catch (missing: NoSuchFieldException) {
            throw AssertionError(
                "A4: production constant '$field' not found on VisualJsClientEngine; the budget " +
                    "mirror cannot be validated (renamed/removed → this guard must be updated)",
                missing,
            )
        }
        declared.isAccessible = true
        return declared.getLong(null)
    }

    private fun assertMetadataComplete(metadata: DeviceMetadata) {
        // A8：没有这些，任何离群都无法归因（"数字无法归因、不可复现"）。
        assertEquals(
            "the APK sha256 must be a 64-char hex digest of the installed APK (A8)",
            64,
            metadata.apkSha256.length,
        )
        assertTrue(
            "the APK sha256 must be hex (A8): ${metadata.apkSha256}",
            metadata.apkSha256.all { it in "0123456789abcdef" },
        )
        assertTrue("the device fingerprint must be recorded (A8)", metadata.fingerprint.isNotBlank())
        assertTrue("the SDK level must be recorded (A8)", metadata.sdkInt >= 23)
        assertTrue("the ABI list must be recorded (A8)", metadata.abis.isNotBlank())
        assertTrue("the page size must be readable (A8)", metadata.pageSizeBytes > 0L)
    }

    /** R2 §2.3 #3：两批 S-warmup 中位数相差 >2× 视为条件漂移，不合并。 */
    private fun assertBatchMediansAreMergeable(batchA: BatchOutcome, batchB: BatchOutcome) {
        val medianA = batchA.samples.map { it.stages.warmUpMillis }.percentileNearestRank(50)
        val medianB = batchB.samples.map { it.stages.warmUpMillis }.percentileNearestRank(50)
        // 0 ms 中位数（ready 在 attach 结束前就绪）时比值无定义；此时无法据中位数判漂移，
        // 只在报告里并列两批分布（不构成放宽：G2 仍施于合并样本）。
        if (medianA > 0L && medianB > 0L) {
            val ratio = maxOf(medianA, medianB).toDouble() / minOf(medianA, medianB).toDouble()
            assertTrue(
                "conditional drift: batch medians differ by ${formatDecimal(ratio)}x > 2x " +
                    "(A=${medianA} ms, B=${medianB} ms); the two batches must be reported " +
                    "separately instead of merged (R2 §2.3 #3)",
                ratio <= 2.0,
            )
        }
    }

    // ---------------------------------------------------------------- 报告

    private fun buildReport(
        metadata: DeviceMetadata,
        batchA: BatchOutcome,
        batchB: BatchOutcome,
        samples: List<RoundOutcome.Sample>,
    ): List<String> {
        val lines = mutableListOf<String>()
        lines += "KD-76 cold-start connect-stage calibration (T4)"
        lines += "estimator=$ESTIMATOR_NAME | rounds=$CALIBRATION_ROUNDS (2x$BATCH_SIZE) | " +
            "outlier policy=$OUTLIER_POLICY"
        lines += "budgets: READY_TIMEOUT=$READY_BUDGET_MILLIS ms | G2 gate (main criterion " +
            "M=S-bind+S-warmup) = READY_TIMEOUT/2=$READY_GATE_MILLIS ms | " +
            "first-connect=$FIRST_CONNECT_BUDGET_MILLIS ms"
        lines += "A9 disclosure: S-warmup is quantized by the $READY_POLL_INTERVAL_MILLIS ms ready " +
            "poll; differences below $READY_POLL_INTERVAL_MILLIS ms are not performance differences"
        lines += "device: fingerprint=${metadata.fingerprint} | sdk=${metadata.sdkInt} | " +
            "abis=${metadata.abis} | pageSize=${metadata.pageSizeBytes} | " +
            "versionName=${metadata.versionName} | apkSha256=${metadata.apkSha256}"
        lines += "segment derivation: S-bind=bindConnected-bindRequested (C1) | " +
            "S-warmup=ready-bindConnected (C2) | S-readyGap=executeAccepted-ready (C3 subset) | " +
            "S-firstByte=firstByte-executeAccepted (C3 subset)"

        listOf(batchA, batchB).forEach { batch ->
            lines += "[batch ${batch.name}] rounds=${batch.samples.size + batch.failures.size} " +
                "success=${batch.samples.size} fail=${batch.failures.size} " +
                "apkSha256Prefix=${batch.metadata.apkSha256.take(16)}"
            batch.samples.forEach { sample ->
                val s = sample.stages
                lines += "  ${sample.roundId} gen=${sample.generation} " +
                    "S-bind=${s.bindMillis} S-warmup=${s.warmUpMillis} " +
                    "S-readyGap=${s.readyGapMillis} S-firstByte=${s.firstByteMillis} " +
                    "total=${s.totalMillis} (=${s.bindMillis}+${s.warmUpMillis}+" +
                    "${s.readyGapMillis}+${s.firstByteMillis}) " +
                    "loadBefore=${formatDecimal(sample.loadBefore)} " +
                    "loadAfter=${formatDecimal(sample.loadAfter)}"
            }
            batch.failures.forEach { failure ->
                lines += "  ${failure.roundId} FAIL (censored ≥ ${failure.censoredLowerBoundMillis} ms): " +
                    failure.reason
            }
            lines += "  " + formatStats("S-bind", batch.samples) { it.bindMillis }
            lines += "  " + formatStats("S-warmup", batch.samples) { it.warmUpMillis }
            lines += "  " + formatStats("S-readyGap", batch.samples) { it.readyGapMillis }
            lines += "  " + formatStats("S-firstByte", batch.samples) { it.firstByteMillis }
            lines += "  " + formatStats("S-total", batch.samples) { it.totalMillis }
        }

        lines += "[merged sample n=${samples.size}]"
        lines += "  " + formatStats("M=S-bind+S-warmup (main criterion)", samples) {
            it.bindMillis + it.warmUpMillis
        }
        lines += "  " + formatStats("S-bind", samples) { it.bindMillis }
        lines += "  " + formatStats("S-warmup (C2 component, NOT the gate)", samples) { it.warmUpMillis }
        lines += "  " + formatStats("S-readyGap", samples) { it.readyGapMillis }
        lines += "  " + formatStats("S-firstByte", samples) { it.firstByteMillis }
        lines += "  " + formatStats("S-total", samples) { it.totalMillis }

        // 判词守卫：预注册样本未达成或 G1 已红时**不得打印 PASS/FAIL 判词**——否则报告层
        // 会把 n=9 的 1319 ms 写成"G2 PASS"，下游证据再引用它就成了被放大的阈值。
        val g1Red = batchA.failures.isNotEmpty() || batchB.failures.isNotEmpty()
        val preRegistered = samples.size == CALIBRATION_ROUNDS && !g1Red
        if (samples.isNotEmpty()) {
            val mergedMain = samples.map { it.stages.bindMillis + it.stages.warmUpMillis }.stats()
            val mergedWarmUp = samples.map { it.stages.warmUpMillis }.stats()
            if (preRegistered) {
                lines += "G2 (main criterion M): p95=${mergedMain.p95} ms vs gate=" +
                    "$READY_GATE_MILLIS ms => " +
                    if (mergedMain.p95 <= READY_GATE_MILLIS) "PASS" else "FAIL (must not be relaxed)"
                lines += "G2 context: S-warmup component p95=${mergedWarmUp.p95} ms " +
                    "(component only; not a gate)"
                lines += "G3: M max=${mergedMain.max} ms (round=" +
                    samples.maxByOrNull { it.stages.bindMillis + it.stages.warmUpMillis }?.roundId +
                    "); " +
                    if (mergedMain.max > READY_GATE_MILLIS) {
                        "max > gate: attribution is required and must not be omitted (R2 §3.3 G3)"
                    } else {
                        "max ≤ gate"
                    }
            } else {
                lines += "G2: NOT EVALUABLE — pre-registration unmet: n=${samples.size}/" +
                    "$CALIBRATION_ROUNDS, pre-registered rounds failed=${batchA.failures.size + batchB.failures.size}" +
                    " (G1 red=$g1Red). For information only, M p95=${mergedMain.p95} ms vs " +
                    "gate=$READY_GATE_MILLIS ms, but NO pass/fail verdict may be derived from a " +
                    "sample that lost rounds to failures (R2 §2.5/§3.1)."
            }
        } else {
            lines += "G2/G3: no successful rounds — nothing to report; G1 must be red"
        }

        lines += "chain envelope: sum(budget)=15000+10000+2500+2000=29500 ms is a supremum, " +
            "not a measured value; this case measures only S-bind/C1 and S-warmup/C2 " +
            "(S-readyGap/S-firstByte are C3 subsets; C3/C4 distributions are NOT measured here); " +
            "segment p95s must never be added into a chain p95 (R2 §4.2-§4.5)"
        return lines
    }

    private fun formatStats(
        label: String,
        samples: List<RoundOutcome.Sample>,
        selector: (StageDurations) -> Long,
    ): String {
        val values = samples.map { selector(it.stages) }
        if (values.isEmpty()) return "$label n=0"
        val stats = values.stats()
        return "$label n=${stats.n} min=${stats.min} p50=${stats.p50} p90=${stats.p90} " +
            "p95=${stats.p95} max=${stats.max} sorted=${stats.sorted}"
    }

    private fun emit(lines: List<String>) {
        lines.forEach { line ->
            Log.i(TAG, line)
            println(line)
        }
    }

    // ---------------------------------------------------------------- 统计

    /**
     * nearest-rank（ceil）分位：`sorted[ceil(n*p/100)-1]`，与仓库既有 Kotlin 门同式
     * （`GoldenRetrievalInstrumentedTest.kt:605-610`）。值一定是实际观测值。
     *
     * 不得换成线性插值（E-04 的 725 是插值口径；n=10 时插值 p95 会小于真实 max，等于
     * 悄悄放宽 G2，R2 §2.1/§2.2）。
     */
    private fun List<Long>.percentileNearestRank(percent: Int): Long {
        require(isNotEmpty()) { "a percentile needs at least one sample" }
        val sorted = sorted()
        val index = ((sorted.size * percent + 99) / 100 - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun List<Long>.stats(): SegmentStats {
        require(isNotEmpty()) { "segment stats need at least one sample" }
        val sorted = sorted()
        return SegmentStats(
            n = sorted.size,
            min = sorted.first(),
            p50 = sorted.percentileNearestRank(50),
            p90 = sorted.percentileNearestRank(90),
            p95 = sorted.percentileNearestRank(95),
            max = sorted.last(),
            sorted = sorted,
        )
    }

    // ---------------------------------------------------------------- 同轮元数据（A8）

    private fun captureMetadata(): DeviceMetadata {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return DeviceMetadata(
            fingerprint = Build.FINGERPRINT,
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.joinToString(","),
            pageSizeBytes = Os.sysconf(OsConstants._SC_PAGESIZE),
            apkSha256 = installedApkSha256(),
            versionName = packageInfo.versionName ?: "unknown",
        )
    }

    private fun installedApkSha256(): String {
        val sourceDir = context.packageManager
            .getApplicationInfo(context.packageName, 0)
            .sourceDir
        val digest = MessageDigest.getInstance("SHA-256")
        File(sourceDir).inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readLoadAverage(roundId: String): Double {
        // A8 设备实测修正（批次 39 收尾轮）：Android 14 的 untrusted_app 域读不到
        // `/proc/loadavg`（本机 raw='null'），若按原断言判 INVALID 会使整个用例
        // 在本轨设备上不可运行。这里保留读取尝试并把不可读记为 -1.0，由设备轨在
        // 同一窗口用 `adb shell cat /proc/loadavg` 外部采样补 A8 归因；G1/G2 门与
        // 时间戳测量不受影响，也不把 -1.0 当作"低负载"证据。
        val text = runCatching { File("/proc/loadavg").readText().trim() }.getOrNull()
        val value = text?.split(Regex("\\s+"))?.firstOrNull()?.toDoubleOrNull()
        if (value == null) {
            Log.w(TAG, "$roundId loadavg unreadable in-process (raw='$text'); " +
                "A8 attribution comes from the device-track adb sampler")
            return -1.0
        }
        return value
    }

    private fun screenIsInteractive(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun describe(result: VisualExecutionResult): String = when (result) {
        is VisualExecutionResult.Rendered -> "Rendered"
        is VisualExecutionResult.Rejected -> "Rejected/${result.reason}: ${result.diagnostics}"
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.2f", value)

    // ---------------------------------------------------------------- 夹具/数据

    private fun pureGeometryRequest() = VisualExecutionRequest(
        programId = "program-calibration-geometry",
        sourceJs = """
            const visual = {
              prepare: function () { return []; },
              render: function (input) {
                return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                         viewBox: { x: 0, y: 0,
                                    width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         paths: [], root: { type: 'group', children: [] } };
              },
            };
        """.trimIndent(),
        sceneRevision = 1,
        stateJson = null,
        viewport = VisualViewport(360, 240),
        timeMs = 1_000L,
        seed = 42L,
        theme = "light",
    )

    private data class StageDurations(
        val bindMillis: Long,
        val warmUpMillis: Long,
        val readyGapMillis: Long,
        val firstByteMillis: Long,
        val totalMillis: Long,
    )

    private sealed class RoundOutcome {
        abstract val roundId: String

        data class Sample(
            override val roundId: String,
            val generation: Long,
            val stages: StageDurations,
            val loadBefore: Double,
            val loadAfter: Double,
        ) : RoundOutcome()

        data class Failed(
            override val roundId: String,
            val reason: String,
            val censoredLowerBoundMillis: Long,
        ) : RoundOutcome()
    }

    private data class BatchOutcome(
        val name: String,
        val metadata: DeviceMetadata,
        val samples: List<RoundOutcome.Sample>,
        val failures: List<RoundOutcome.Failed>,
    )

    private data class DeviceMetadata(
        val fingerprint: String,
        val sdkInt: Int,
        val abis: String,
        val pageSizeBytes: Long,
        val apkSha256: String,
        val versionName: String,
    )

    private data class SegmentStats(
        val n: Int,
        val min: Long,
        val p50: Long,
        val p90: Long,
        val p95: Long,
        val max: Long,
        val sorted: List<Long>,
    )

    private companion object {
        const val TAG = "Kd76Calibration"

        /**
         * 估计量名：evidence 必须打印口径（R2 §2.3 #1）；只写"p95=xxx"无法判断是
         * nearest-rank 还是线性插值。
         */
        const val ESTIMATOR_NAME = "nearest-rank (ceil)"

        /** R2 §3.1：不删点、不缩尾、不重跑到绿；失败轮 = 删失样本。 */
        const val OUTLIER_POLICY = "none (no delete/winsorize/retry; FAIL = censored ≥ budget)"

        /**
         * 预注册样本量（R2 §2.3 #2 的默认形态：两批独立冷启动各 10 轮，合并原始样本
         * n=20）。n≥10 是计划下限；n=20 才让 nearest-rank p95 = 第 19/20 个、容忍一个
         * 最大值——n=10 时 p95≡max，那会与"单点离群不红"自相矛盾（R2 §2.2）。
         */
        const val CALIBRATION_ROUNDS = 20
        const val BATCH_SIZE = 10

        /**
         * `VisualJsClientEngine.READY_TIMEOUT_MILLIS`（private companion，`:1325`）的镜像。
         * 断言在 [assertPreRegistration] 里钉住 10_000；预算改动必须同步本用例与判据。
         */
        const val READY_BUDGET_MILLIS = 10_000L

        /**
         * KD-76 主口径判据：`M = S-bind + S-warmup`（bind 请求→ready，逐轮求和后取 p95），
         * `M.p95 ≤ READY_TIMEOUT / 2`（5_000 ms）。**相对形式**（A4）；不追加容差、不换 p90、
         * 不换估计量、**不缩窄为 S-warmup 单段**（R2 §2.3 #4、§3.2:132）。
         */
        const val READY_GATE_MILLIS = READY_BUDGET_MILLIS / 2

        /** `VisualJsClientEngine.READY_POLL_INTERVAL_MILLIS` 的镜像（`:1328`，A9）。 */
        const val READY_POLL_INTERVAL_MILLIS = 100L

        /** `VisualJsClientEngine.FIRST_CONNECT_TIMEOUT_MILLIS` 的镜像（`:1301`），仅作删失下界。 */
        const val FIRST_CONNECT_BUDGET_MILLIS = 15_000L

        /**
         * 轮间/批间沉降：确认死亡后给 AMS/进程收尾留时间，保证下一轮 bind 一定 fork
         * 新进程。它不是预热（不触碰引擎），也不进任何段值。
         */
        const val SETTLE_BETWEEN_ROUNDS_MILLIS = 500L
    }
}
