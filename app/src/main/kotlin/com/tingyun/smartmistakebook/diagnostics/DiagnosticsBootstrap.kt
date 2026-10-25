package com.tingyun.smartmistakebook.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * ApplicationExitInfo（API 30+）→ [RawProcessExit] 的生产适配。只取 R2 裁定的五类
 * （ANR/CRASH/CRASH_NATIVE/SIGNALED/LOW_MEMORY），其余 reason 不进账本；常量一律用
 * 平台命名常量引用（不硬编码数字）。API<30 恒空：该区间的 ANR 缺口由现场看门狗补，
 * 退出记录缺口如实登记（R2 §6）。
 */
internal object ApplicationExitInfoSource {

    fun create(context: Context): ProcessExitInfoSource = ProcessExitInfoSource {
        if (Build.VERSION.SDK_INT < 30) {
            emptyList()
        } else {
            val activityManager = context.getSystemService(ActivityManager::class.java)
                ?: return@ProcessExitInfoSource emptyList()
            val infos = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_INFOS)
            infos.mapNotNull { info ->
                val exitClass = mapReason(info.reason) ?: return@mapNotNull null
                RawProcessExit(
                    atEpochMillis = info.timestamp,
                    exitClass = exitClass,
                    description = info.description?.toString(),
                )
            }
        }
    }

    private fun mapReason(reason: Int): ExitClass? = when (reason) {
        android.app.ApplicationExitInfo.REASON_ANR -> ExitClass.ANR
        android.app.ApplicationExitInfo.REASON_CRASH -> ExitClass.CRASH
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitClass.CRASH_NATIVE
        android.app.ApplicationExitInfo.REASON_SIGNALED -> ExitClass.SIGNALED
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> ExitClass.LOW_MEMORY
        else -> null
    }

    private const val MAX_EXIT_INFOS = 16
}

/**
 * T6 装配口：Application.onCreate 第一时间调 [install]（越早装上，启动早期的崩溃才有
 * 记录）；存储设置页经 [ledger]/[lastExitNotice] 读状态、[recordCoroutineFailure] 落协程账。
 *
 * 每一样东西消灭的失败（纪律 12.2）：
 * - [install] 装崩溃 handler → 消灭"崩溃后本机无任何记录"（现状 R2 F5/F6：只有 logcat，
 *   重启即丢）；
 * - [install] 起看门狗 → 消灭 API 23–29 的 ANR 记录缺口（R2 §6 缺口 1 的最小补法）；
 * - [reconcileAtStartup] → 消灭"上次异常退出学生无感知"（启动自检，只读横幅不外发）；
 * - [recordCoroutineFailure] → 消灭"协程未捕获异常只进 logcat 重启即丢"（R2 F6）。
 *
 * 全部环节内部吞错：诊断系统绝不能成为第二次崩溃源，也不能拖慢启动（对账由 Application
 * 放在后台协程调）。
 */
object DiagnosticsBootstrap {
    private const val DIRECTORY_NAME = "diagnostics"

    private var installed = false
    private var appContext: Context? = null
    private var ledgerRef: CrashLedger? = null
    private var metaRef: DiagnosticsBundleMeta? = null
    private var previousSessionWatermark: Long? = null

    /** flavor 名（BuildConfig.FLAVOR）；诊断事件与诊断包头共用。 */
    @Volatile
    var flavor: String? = null
        private set

    /** 已装配的账本；未装配（理论上只在单测环境）时 null。 */
    val ledger: CrashLedger? get() = ledgerRef

    /** 启动自检"上次异常退出"的只读横幅文案；null = 没有需要告知的异常退出。 */
    @Volatile
    var lastExitNotice: String? = null
        private set

    fun install(application: Application, flavorName: String?) {
        if (installed) return
        installed = true
        appContext = application.applicationContext
        flavor = flavorName
        val dir = File(application.filesDir, DIRECTORY_NAME)
        if (!dir.isDirectory) {
            runCatching { dir.mkdirs() }
        }
        val ledger = CrashLedger(dir)
        ledgerRef = ledger
        metaRef = DiagnosticsBundleExporter.metaFrom(application, flavorName)

        // 上一会话的水位线先读后写：对账窗口 = [上一会话开始, 本进程启动)。
        previousSessionWatermark = SessionWatermark.readOrNull(dir)
        SessionWatermark.write(dir, System.currentTimeMillis())

        // ① 崩溃 handler：保存并链回 previous——系统杀进程行为不变。
        val versionName = metaRef?.versionName
        val versionCode = metaRef?.versionCode
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                record = { thread, throwable ->
                    ledger.append(
                        CrashHandler.eventFor(thread, throwable, versionName, versionCode, flavorName),
                    )
                },
                previous = previous,
            ),
        )

        // ② 主线程看门狗：后台守护线程 + 主线程 Handler 回戳。MIN_PRIORITY 防抢占。
        val monitor = AnrWatchdogMonitor(
            blockageThresholdMillis = AnrWatchdogMonitor.BLOCKAGE_THRESHOLD_MILLIS,
            onBlockage = { blockedFor ->
                ledger.append(
                    DiagnosticEvent(
                        atEpochMillis = System.currentTimeMillis(),
                        category = DiagnosticCategories.ANR_WATCHDOG,
                        threadName = "main",
                        message = "主线程一次阻塞约 $blockedFor 毫秒",
                        versionName = versionName,
                        versionCode = versionCode,
                        flavor = flavorName,
                    ),
                )
            },
        )
        val watchdog = Thread(
            {
                AnrWatchdogMonitor.runWatchdogLoop(
                    mainHandler = Handler(Looper.getMainLooper()),
                    monitor = monitor,
                )
            },
            "diagnostics-anr-watchdog",
        )
        watchdog.isDaemon = true
        watchdog.priority = Thread.MIN_PRIORITY
        watchdog.start()
    }

    /** 启动对账：Application 在后台协程调用，不阻塞主线程；结果写 [lastExitNotice]。 */
    fun reconcileAtStartup(processStartedAtEpochMillis: Long) {
        val ledger = ledgerRef ?: return
        val context = appContext ?: return
        val report = try {
            StartupExitReconciler(ledger, ApplicationExitInfoSource.create(context)).reconcile(
                processStartedAtEpochMillis = processStartedAtEpochMillis,
                previousSessionStartAtEpochMillis = previousSessionWatermark,
            )
        } catch (_: Throwable) {
            return
        }
        lastExitNotice = report.notice
    }

    /** 应用级协程 backstop 追加落账：现状只进 logcat 的失败，重启后也能从账本看见。 */
    fun recordCoroutineFailure(failure: Throwable) {
        val ledger = ledgerRef ?: return
        val meta = metaRef
        ledger.append(
            DiagnosticEvent(
                atEpochMillis = System.currentTimeMillis(),
                category = DiagnosticCategories.COROUTINE_FAILURE,
                threadName = Thread.currentThread().name,
                message = failure.message,
                stackTrace = failure.stackTraceToString().take(MAX_STACK_CHARS),
                versionName = meta?.versionName,
                versionCode = meta?.versionCode,
                flavor = flavor,
            ),
        )
    }

    private const val MAX_STACK_CHARS = 8 * 1024
}
