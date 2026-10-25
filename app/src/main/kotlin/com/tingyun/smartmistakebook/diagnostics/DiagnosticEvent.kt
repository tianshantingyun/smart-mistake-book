package com.tingyun.smartmistakebook.diagnostics

/**
 * 诊断事件（T6 beta 反馈闭环）：一行 JSON 一条，落 `filesDir/diagnostics/` 的环形文件账本。
 *
 * 采集面收窄到"定位崩溃/无响应所需的最小字段"：时间、类别、线程、可选消息/堆栈、
 * 本地已知的应用版本三元组。不采集设备标识、logcat、系统设置、IP（零新增隐私面，
 * R2 §5.1）；堆栈里若含用户内容路径，也只会在用户主动导出诊断包时离开设备。
 */
data class DiagnosticEvent(
    val atEpochMillis: Long,
    /** 类别常量见 [DiagnosticCategories]（JSONL 里是自由字符串，这里集中定义生产者）。 */
    val category: String,
    val threadName: String,
    val message: String? = null,
    val stackTrace: String? = null,
    /** 版本读不到就是 null（诊断字段不许假造；同 AndroidBackupRepository 的版本戳纪律）。 */
    val versionName: String? = null,
    val versionCode: Long? = null,
    val flavor: String? = null,
)

/**
 * 诊断事件类别常量。前缀约定：崩溃/协程/看门狗直接命名；上一进程的系统退出记录
 * 以 `EXIT_` 开头（来自 ApplicationExitInfo 对账，不是本进程现场捕获）。
 */
object DiagnosticCategories {
    /** 现场未捕获异常（CrashHandler 落盘，链回系统 handler 之前写）。 */
    const val CRASH = "CRASH"

    /** 应用级协程未捕获异常（CoroutineExceptionHandler backstop 落账）。 */
    const val COROUTINE_FAILURE = "COROUTINE_FAILURE"

    /** 主线程看门狗记录的一次阻塞（API 23–29 的 ANR 缺口补法）。 */
    const val ANR_WATCHDOG = "ANR_WATCHDOG"

    const val EXIT_ANR = "EXIT_ANR"
    const val EXIT_CRASH = "EXIT_CRASH"
    const val EXIT_CRASH_NATIVE = "EXIT_CRASH_NATIVE"
    const val EXIT_SIGNALED = "EXIT_SIGNALED"
    const val EXIT_LOW_MEMORY = "EXIT_LOW_MEMORY"
    const val EXIT_OTHER = "EXIT_OTHER"

    /** 系统退出记录对账写入的类别。 */
    val EXIT_CATEGORIES = setOf(
        EXIT_ANR,
        EXIT_CRASH,
        EXIT_CRASH_NATIVE,
        EXIT_SIGNALED,
        EXIT_LOW_MEMORY,
        EXIT_OTHER,
    )

    /**
     * 会进"上次异常退出"横幅的类别。[COROUTINE_FAILURE] 不在其中：协程失败没有退出
     * 进程，不构成"上次异常退出"。
     */
    val NOTICE_WORTHY = setOf(CRASH, ANR_WATCHDOG) + EXIT_CATEGORIES
}
