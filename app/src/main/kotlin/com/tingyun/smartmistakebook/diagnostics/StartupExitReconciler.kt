package com.tingyun.smartmistakebook.diagnostics

/**
 * 上一进程退出记录的抽象（T6 测试缝：app 单测无 Robolectric，框架类不能进 JVM 单测，
 * R2 F14）。生产实现包一层 ApplicationExitInfo（API 30+；API<30 返回空列表）。
 */
fun interface ProcessExitInfoSource {
    fun exits(): List<RawProcessExit>
}

/** 平台无关的进程退出记录（映射自 ApplicationExitInfo）。 */
data class RawProcessExit(
    val atEpochMillis: Long,
    val exitClass: ExitClass,
    val description: String? = null,
)

enum class ExitClass { ANR, CRASH, CRASH_NATIVE, SIGNALED, LOW_MEMORY, OTHER }

/** 对账结果：新落了几条系统退出记录、以及"上次异常退出"横幅文案（null = 不打扰）。 */
data class StartupExitReport(
    val recordedExitEvents: Int,
    val notice: String?,
)

/**
 * 启动对账（T6）：进程刚起来时跑一次——
 * 1. API 30+ 把早于本进程的系统退出记录（ANR/崩溃/信号终止/低内存）转写进本机账本，
 *    与账本里已有的退出记录按时间去重（重复启动不重复记同一条）；
 * 2. 汇总账本里"上一会话窗口"内的相关事件，生成只读横幅文案（只存本机、不自动外发）。
 *
 * 时间判据（R2 F8）：早于 `processStartedAtEpochMillis` 的事件才属于上一进程；再往前
 * 追溯到 `previousSessionStartAtEpochMillis`（上一会话的水位线）——更早的事件已经展示过
 * 或早已被轮转，不再打扰。
 */
class StartupExitReconciler(
    private val ledger: CrashLedger,
    private val source: ProcessExitInfoSource,
) {
    fun reconcile(
        processStartedAtEpochMillis: Long,
        previousSessionStartAtEpochMillis: Long?,
    ): StartupExitReport {
        val recorded = recordPreStartExits(processStartedAtEpochMillis)
        val notice = composeNotice(processStartedAtEpochMillis, previousSessionStartAtEpochMillis)
        return StartupExitReport(recorded, notice)
    }

    private fun recordPreStartExits(processStartedAtEpochMillis: Long): Int {
        val raw = try {
            source.exits()
        } catch (_: Throwable) {
            // 读不到系统记录 = 这一项自检空跑，如实降级。
            return 0
        }
        // 对账去重：账本里已有的最新一条退出记录之前（含相等）的系统记录不再重复落账。
        val alreadyRecordedAt = ledger.readAll()
            .asSequence()
            .filter { it.category in DiagnosticCategories.EXIT_CATEGORIES }
            .map { it.atEpochMillis }
            .maxOrNull() ?: Long.MIN_VALUE
        var recorded = 0
        for (exit in raw) {
            if (exit.atEpochMillis >= processStartedAtEpochMillis) continue
            if (exit.atEpochMillis <= alreadyRecordedAt) continue
            ledger.append(
                DiagnosticEvent(
                    atEpochMillis = exit.atEpochMillis,
                    category = exit.exitClass.toCategory(),
                    threadName = PREVIOUS_PROCESS_THREAD,
                    message = exit.description,
                ),
            )
            recorded++
        }
        return recorded
    }

    private fun composeNotice(
        processStartedAtEpochMillis: Long,
        previousSessionStartAtEpochMillis: Long?,
    ): String? {
        val windowStart = previousSessionStartAtEpochMillis ?: Long.MIN_VALUE
        val newest = ledger.readAll()
            .asSequence()
            .filter { it.category in DiagnosticCategories.NOTICE_WORTHY }
            .filter { it.atEpochMillis >= windowStart && it.atEpochMillis < processStartedAtEpochMillis }
            .maxByOrNull { it.atEpochMillis }
            ?: return null
        return "上次异常退出：${describe(newest.category)}。" +
            "诊断信息只存在本机，可在设置的存储页导出诊断包。"
    }

    private fun describe(category: String): String = when (category) {
        DiagnosticCategories.CRASH -> "应用崩溃"
        DiagnosticCategories.ANR_WATCHDOG -> "界面一度无响应"
        DiagnosticCategories.EXIT_ANR -> "系统判定无响应"
        DiagnosticCategories.EXIT_CRASH, DiagnosticCategories.EXIT_CRASH_NATIVE -> "应用崩溃"
        DiagnosticCategories.EXIT_SIGNALED -> "应用被系统终止"
        DiagnosticCategories.EXIT_LOW_MEMORY -> "设备内存不足"
        DiagnosticCategories.EXIT_OTHER -> "异常退出"
        else -> "异常退出"
    }

    private fun ExitClass.toCategory(): String = when (this) {
        ExitClass.ANR -> DiagnosticCategories.EXIT_ANR
        ExitClass.CRASH -> DiagnosticCategories.EXIT_CRASH
        ExitClass.CRASH_NATIVE -> DiagnosticCategories.EXIT_CRASH_NATIVE
        ExitClass.SIGNALED -> DiagnosticCategories.EXIT_SIGNALED
        ExitClass.LOW_MEMORY -> DiagnosticCategories.EXIT_LOW_MEMORY
        ExitClass.OTHER -> DiagnosticCategories.EXIT_OTHER
    }

    private companion object {
        /** 系统退出记录不是本进程现场捕获，线程名如实标注来源。 */
        const val PREVIOUS_PROCESS_THREAD = "(previous process)"
    }
}
