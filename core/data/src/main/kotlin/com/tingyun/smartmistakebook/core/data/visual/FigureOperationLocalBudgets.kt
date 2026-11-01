package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.model.FigureGenerationLocalJsBudget
import java.nio.charset.StandardCharsets

/**
 * 4E.2 A-10：同一逻辑操作的**本地 JS / 最终图**预算累计器（协调器持有的进程内状态）。
 *
 * **它消灭的具体失败**（常量语义见 [FigureGenerationLocalJsBudget]，这里只补执行面）：
 * - 远程派发账本只挡 IMAGE_MODEL；JS 本地执行零付费、零预留，同一 operationId 的失败
 *   尝试此前可以无限重试——每次 ≤ 64 KiB 但累计无界、每次布局尝试无界、跨尝试挂死-重建
 *   可以无限烧墙钟（引擎只有单事务 1500 ms 期限，没有跨尝试累计）；
 * - 最终图（GENERATED 产物）没有"同一操作最多两份"的门：JS 路径产出第 3 份最终图
 *   此前没有任何东西拒绝它。
 *
 * 执行点与口径（与 A-10 验收用例逐条对应）：
 * - **累计源码 / 布局尝试**：`admitJsAttempt` 在策略校验之后、引擎执行之前调用——通过即
 *   记账（这次尝试计入，无论随后成功/失败/取消；含失败尝试正是行义），拒绝则这次尝试
 *   **不执行、不进引擎、不建 attempt 行**（与 policy 拒绝同形的 REJECTED）；
 * - **墙钟**：`recordJsWallClock` 在每次 `engine.execute` 返回（含异常路径）后记账；
 *   下一次尝试的 `admitJsWallClock` 按已累计墙钟拒绝——恰达上限后的下一次拒，在途的一次
 *   由引擎自身的单事务期限兜底；
 * - **最终图**：`admitFinalFigure` 在真正生成（打开后端/进引擎）之前拒绝；`recordFinalFigure`
 *   只在产物真正 GENERATED 时记账（REJECTED/FAILED 不占名额，幂等 CACHED 不经协调器）。
 *
 * 状态边界（如实登记，不冒充持久账本）：
 * - 进程内状态，进程重启即清零——远程派发的持久预算在 [com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchLedger]，
 *   这里的三类是账本（schema 71 冻结、无新列可落）**挡不住的无界面**的进程内下界，不是
 *   它的替代；
 * - 同一操作的调用在生产与测试中都是串行的（工具轮逐轮驱动）；跨操作并发各自独立键，
 *   内部以同一把锁串行化读改写；`admitFinalFigure`（生成前检查）与 `recordFinalFigure`
 *   （成功后记账）之间没有预留位——同一操作并发生成理论上可以都过检查，生产路径不存在
 *   该形状，如实登记为边界；
 * - 键窗口有上界 [MAX_TRACKED_OPERATIONS]（淘汰最旧）：它消灭的是"进程级单例上的无界
 *   map"这一内存失败，不是预算豁免——被淘汰的是早已完结的旧操作。
 */
internal class FigureOperationLocalBudgets {

    private class Entry {
        var layoutAttempts: Int = 0
        var sourceBytes: Int = 0
        var wallClockMillis: Long = 0
        var finalFigures: Int = 0
    }

    private val entries = object : LinkedHashMap<String, Entry>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_TRACKED_OPERATIONS
    }

    /**
     * 累计 JS 墙钟的准入检查（执行前）。返回 null = 准入；非 null = 有界拒绝原因。
     *
     * 恰达上限后的下一次拒；在途的一次由引擎自身的单事务期限兜底（这里管不了"正在烧"）。
     */
    fun admitJsWallClock(operationId: String): String? = synchronized(this) {
        val entry = entryFor(operationId)
        if (entry.wallClockMillis >= FigureGenerationLocalJsBudget.MAX_CUMULATIVE_WALL_CLOCK_MILLIS) {
            return@synchronized "本逻辑操作累计 JS 墙钟已达 ${entry.wallClockMillis} ms" +
                "（上限 ${FigureGenerationLocalJsBudget.MAX_CUMULATIVE_WALL_CLOCK_MILLIS} ms），未执行"
        }
        null
    }

    /**
     * JS 布局尝试的准入 + 记账。返回 null = 准入（这次尝试已计入：布局 +1、源码 +program
     * 字节）；返回非 null = 有界拒绝原因（这次尝试**未执行、未记账**）。
     */
    fun admitJsAttempt(operationId: String, program: String): String? = synchronized(this) {
        val entry = entryFor(operationId)
        if (entry.layoutAttempts >= FigureGenerationLocalJsBudget.MAX_LAYOUT_ATTEMPTS) {
            return@synchronized "本逻辑操作的 JS 布局尝试已达 " +
                "${FigureGenerationLocalJsBudget.MAX_LAYOUT_ATTEMPTS} 次上限" +
                "（已执行 ${entry.layoutAttempts} 次），未执行"
        }
        val programBytes = program.toByteArray(StandardCharsets.UTF_8).size
        val projected = entry.sourceBytes.toLong() + programBytes.toLong()
        if (projected > FigureGenerationLocalJsBudget.MAX_CUMULATIVE_SOURCE_UTF8_BYTES) {
            return@synchronized "本逻辑操作累计 JS 源码 ${entry.sourceBytes} 字节，" +
                "这次再收 $programBytes 字节将超过 " +
                "${FigureGenerationLocalJsBudget.MAX_CUMULATIVE_SOURCE_UTF8_BYTES} 字节预算，未执行"
        }
        entry.layoutAttempts += 1
        entry.sourceBytes += programBytes
        null
    }

    /** 记账一次真实进引擎的执行耗时（宿主侧实测前后 `now` 读数差；成功/失败都算）。 */
    fun recordJsWallClock(operationId: String, elapsedMillis: Long) = synchronized(this) {
        require(elapsedMillis >= 0) { "A wall clock reading must not be negative" }
        entryFor(operationId).wallClockMillis += elapsedMillis
    }

    /** 已累计的 JS 墙钟（诊断与测试读口）。 */
    fun wallClockMillis(operationId: String): Long = synchronized(this) {
        entries[operationId]?.wallClockMillis ?: 0
    }

    /** 最终图准入检查（生成前）。返回 null = 准入；非 null = 有界拒绝原因。 */
    fun admitFinalFigure(operationId: String): String? = synchronized(this) {
        val entry = entryFor(operationId)
        if (entry.finalFigures >= FigureGenerationLocalJsBudget.MAX_FINAL_FIGURES_PER_OPERATION) {
            return@synchronized "本逻辑操作已产出 ${entry.finalFigures} 份最终图" +
                "（上限 ${FigureGenerationLocalJsBudget.MAX_FINAL_FIGURES_PER_OPERATION}），未执行"
        }
        null
    }

    /** 记账一份真正 GENERATED 的最终图（REJECTED/FAILED 不调用）。 */
    fun recordFinalFigure(operationId: String) = synchronized(this) {
        entryFor(operationId).finalFigures += 1
    }

    /** 已产出的最终图份数（诊断与测试读口）。 */
    fun finalFigures(operationId: String): Long = synchronized(this) {
        entries[operationId]?.finalFigures?.toLong() ?: 0
    }

    /** 没有则建；建/取都经它发生，淘汰由 [entries] 的 eldest 规则统一处理。 */
    private fun entryFor(operationId: String): Entry {
        require(operationId.isNotBlank()) { "A local budget entry needs an operation id" }
        return entries.getOrPut(operationId) { Entry() }
    }

    private companion object {
        /**
         * 键窗口上界。它消灭的具体失败：协调器是进程级单例，per-operation 状态若无界，
         * 长生命周期的进程会随操作数无限增长。淘汰最旧——正常逻辑操作的生命周期
         * （一次工具轮内的重试环）远短于这个窗口，淘汰只会发生在早已完结的操作上。
         */
        const val MAX_TRACKED_OPERATIONS: Int = 256
    }
}
