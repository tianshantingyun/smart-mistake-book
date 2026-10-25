package com.tingyun.smartmistakebook.diagnostics

import android.os.Handler

/**
 * 主线程看门狗的判定核（T6）：一个后台线程按固定间隔往主线程投递"回戳"；主线程空闲时
 * 立即执行回戳。两次回戳的间隔超过阈值 = 主线程被卡住的证据；一次阻塞事件期只上报一次，
 * 主线程恢复后重新武装。看门狗**只记录、从不杀进程**。
 *
 * 灵感来源 SalomonBrys/ANR-Watchdog（MIT，https://github.com/SalomonBrys/ANR-Watchdog，
 * 2022-03 后停更）。自写而非引入（R2 §3 裁定）：只取其"主线程回戳"一招，线程管理与
 * 上报面按本项目账本形态裁制，不为此引入停更依赖。补的缺口：API 23–29 无系统退出记录
 * 可读（ApplicationExitInfo 是 API 30+），现场看门狗是该区间的 ANR 记录来源。
 */
class AnrWatchdogMonitor(
    private val blockageThresholdMillis: Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val onBlockage: (blockedForMillis: Long) -> Unit,
) {
    private val lock = Any()
    private var lastTickAtMillis: Long = nowMillis()
    private var episodeOpen: Boolean = false

    /** 仅供 JVM 测试断言间隔计算（生产代码不读）。 */
    fun lastTickAtMillisForTest(): Long = synchronized(lock) { lastTickAtMillis }

    /** 主线程调用：一次正常回戳，关闭阻塞事件期。 */
    fun onTickExecuted() {
        synchronized(lock) {
            lastTickAtMillis = nowMillis()
            episodeOpen = false
        }
    }

    /** 看门狗线程调用：每 tick 检查一次；返回是否上报了阻塞。 */
    fun onCheck(): Boolean {
        val (gap, alreadyOpen) = synchronized(lock) {
            val gapMillis = nowMillis() - lastTickAtMillis
            gapMillis to episodeOpen
        }
        if (gap <= blockageThresholdMillis || alreadyOpen) return false
        synchronized(lock) { episodeOpen = true }
        onBlockage(gap)
        return true
    }

    internal companion object {
        /** 主线程空闲时每 2 秒回戳一次；间隔超过 8 秒视为一次可记录的阻塞。 */
        const val TICK_INTERVAL_MILLIS = 2_000L
        const val BLOCKAGE_THRESHOLD_MILLIS = 8_000L

        /**
         * 生产装配：后台守护线程按间隔给主线程 Handler 投递回戳，然后检查。[stop] 返回
         * 真时退出循环（生产不传 = 跟随进程存活）；[sleep] 是测试缝。任何一环失败都只
         * 降级为"没有 ANR 记录"，绝不上抛。
         */
        fun runWatchdogLoop(
            mainHandler: Handler,
            monitor: AnrWatchdogMonitor,
            stop: () -> Boolean = { false },
            sleep: (Long) -> Unit = { Thread.sleep(it) },
        ) {
            while (!stop()) {
                sleep(TICK_INTERVAL_MILLIS)
                try {
                    mainHandler.post { monitor.onTickExecuted() }
                    monitor.onCheck()
                } catch (_: Throwable) {
                    // 看门狗自身故障只降级为"没有记录"。
                }
            }
        }
    }
}
