package com.tingyun.smartmistakebook.diagnostics

/**
 * 未捕获异常处理器（T6）：把崩溃写成一条账本事件，然后**无条件链回** previous
 * handler——系统杀进程的原有行为不变（R2 §3：同步落盘后链回）。记录自身失败绝不阻止
 * 链回：诊断系统不能成为第二次崩溃源。
 */
class CrashHandler(
    private val record: (Thread, Throwable) -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            record(thread, throwable)
        } catch (_: Throwable) {
            // 记录失败只能吞：链回必须发生（进程照常被系统终止）。
        }
        previous?.uncaughtException(thread, throwable)
    }

    companion object {
        /** 堆栈截断上限：单文件 64KB 预算下仍能容纳数条事件。 */
        private const val MAX_STACK_CHARS = 8 * 1024

        /**
         * 从未捕获的 [Throwable] 构造账本事件。类别固定 CRASH；消息取 throwable 的
         * message，堆栈截断。版本三元组由装配方传入（读不到为 null，不假造）。
         */
        fun eventFor(
            thread: Thread,
            throwable: Throwable,
            versionName: String?,
            versionCode: Long?,
            flavor: String?,
        ): DiagnosticEvent = DiagnosticEvent(
            atEpochMillis = System.currentTimeMillis(),
            category = DiagnosticCategories.CRASH,
            threadName = thread.name,
            message = throwable.message,
            stackTrace = throwable.stackTraceToString().take(MAX_STACK_CHARS),
            versionName = versionName,
            versionCode = versionCode,
            flavor = flavor,
        )
    }
}
