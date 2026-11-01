package com.tingyun.smartmistakebook.visualjs

import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 契约 §64（docs/current/visual-generation-contract.md:64）的客户端帧队列（批次 25 T3，
 * 设计依据 docs/research/2026-10-27-visualjs-warmup-queue.md §3.2/§5）：
 *
 * - **全局一个在飞**：同一时刻至多一个 [runFrame] 会话在跑（[inFlight] 不变量）；
 * - **最多两个待绘**：[pending] 容量 [MAX_PENDING_FRAMES]=2；满时新帧立即
 *   `Rejected(BUSY)`，**在飞与两个待绘帧一概不动**（"满队列回 BUSY 并保留原帧，
 *   不把另一张图误当旧帧丢掉"）；
 * - **latest-wins**：同 programId 的新待绘帧原地顶掉旧待绘帧，旧等待者回
 *   `Rejected(BUSY)`（端口语义 = 背压保留原帧，[VisualExecutionRejection.BUSY]），
 *   **不共享新结果**——结果按 dispatch 指纹记账（RoomFigureDispatchLedger），跨 dispatch
 *   复用会错配台账，且旧 revision 调用方拿新 revision 违反契约 §8"sceneRevision 只可回显"；
 * - **跨 program 按序**：不同 program 按 first-enqueue 的 FIFO 顺序出队（ArrayDeque）。
 *
 * 队列只管排队，绝不跨会话持有锁：所有 `synchronized` 临界区都是纯内存操作，连接、
 * IPC、超时等待都发生在锁外（R1' §5.2-1——这消灭了旧 `executeMutex` 整段串行
 * "一个慢会话阻塞所有入队判定"的根因）。
 *
 * **它消灭的具体失败**（对照批次 24 现状）：
 * 1. 旧实现第 N 个调用方挂在无容量 `Mutex` 上等待无上界、拿不到 BUSY——契约 :72 旧文
 *    已登记该缺口；现在第 3 个待绘位之后的调用方立即得到有界拒绝；
 * 2. 同 program 连发多帧在旧实现里全部串行执行，旧帧结果浪费整个会话预算且无人消费；
 *    现在旧等待者立刻回 BUSY，只有最新 state 会被执行；
 * 3. 死亡时队列在旧实现下不可见（Mutex 排队方只能等锁）；现在 [dropPending] 让排队帧
 *    在 worker 死亡时立即 fail-closed，不放大死亡窗口的不确定性。
 *
 * **保守分支（R1' §3.2 推荐、待主会话确认）**：worker 死亡时 [dropPending] 清空待绘并
 * 回 `ENGINE_CLOSED`（与"不能并发放大进程数"一致）；备选"留队等待重建"未采用。
 * **取消语义（主会话已裁定确认采纳，批次 27 T4 定稿；批次 25 记录 §3.3 ②）**：取消只出队
 * 调用方自己的等待帧（取消已排队帧 = 出队不执行）；取消在飞帧不打断在飞执行——会话照常
 * 在自身预算内跑完、结果被丢弃（不 abandon）——旧实现"调用方取消即 terminate worker"的
 * 行为随会话所有权移交消费者而废止，否则一个被取消的调用方会连坐顶掉全部排队帧。
 *
 * 等待者合同：每个 [Frame.waiter] 恰好被完成一次（正常值或取消）；[runFrame] 的合同是
 * "每个帧恰好完成一次 waiter，或者抛出"——抛出由 [launchSession] 兜底完成，等待者永不悬挂。
 */
internal class VisualJsFrameQueue(
    private val scope: CoroutineScope,
    private val runFrame: suspend (Frame) -> Unit,
) {

    /**
     * 一个待绘/在飞帧。[envelope] 已在入队前过全部输入校验（字节预算、熔断、编码），
     * 这里只搬运字节，不再解析——队列不暴露任何通用 RPC 面（契约 §64 末句）。
     * 刻意不用 data class：[pending] 的 latest-wins 移除按对象身份，不按字段值。
     */
    internal class Frame(
        val programId: String,
        /** 熔断指纹（源码 SHA-256）； executeFrame 出队时的二次熔断复查用。 */
        val digest: String,
        val envelope: ByteArray,
        val explicitUserRetry: Boolean,
        val waiter: CompletableDeferred<VisualExecutionResult>,
    )

    private val lock = Any()
    private val pending = ArrayDeque<Frame>()

    /** §64"全局一个在飞"：只有 submit 的直跑分支与 [scheduleNext] 的取队头分支置真。 */
    private var inFlight = false

    /** close() 一次性闩；死亡清队（[dropPending]）**不**置位——重建后队列必须继续可用。 */
    @Volatile
    private var closed = false

    /**
     * 提交一帧。永不抛出：满了/被顶掉/已关闭都折成对 [Frame.waiter] 的完成（值不是异常），
     * 调用方只 `waiter.await()`。
     */
    fun submit(frame: Frame) {
        synchronized(lock) {
            if (closed || !scope.isActive) {
                frame.waiter.complete(rejected(VisualExecutionRejection.ENGINE_CLOSED, CLOSED_DIAGNOSTICS))
                return
            }
            // 调用方在 submit 前已取消：不入队、不执行（等待者已是取消态，无法再投递结果）。
            if (frame.waiter.isCompleted) return
            if (!inFlight) {
                inFlight = true
                launchSession(frame)
                return
            }
            val existingSlot = pending.indexOfFirst { it.programId == frame.programId }
            if (existingSlot >= 0) {
                // latest-wins：原地替换保住跨 program 的 FIFO 位置；旧等待者回 BUSY（背压保原帧）。
                val superseded = pending[existingSlot]
                pending[existingSlot] = frame
                superseded.waiter.complete(rejected(VisualExecutionRejection.BUSY, SUPERSEDED_DIAGNOSTICS))
                return
            }
            if (pending.size >= MAX_PENDING_FRAMES) {
                // 满队列：BUSY 且保留原帧——在飞与两个待绘一概不动。
                frame.waiter.complete(rejected(VisualExecutionRejection.BUSY, QUEUE_FULL_DIAGNOSTICS))
                return
            }
            pending.addLast(frame)
        }
        // 注册必须在入队判定之后：completed 的 deferred 会立刻以既有 cause 触发 handler，
        // 从而覆盖"入队后、注册前取消"的窗口；入队判定里的 isCompleted 检查覆盖注册前的取消。
        frame.waiter.invokeOnCompletion { cause ->
            if (cause != null) {
                // 调用方取消：排队帧出队（不执行）；在飞帧由会话跑完（结果被丢弃，无泄漏：
                // 写端关闭、activeCall 清理都在 runSession 的既有 finally）。
                synchronized(lock) { pending.remove(frame) }
            }
        }
    }

    /**
     * worker 死亡 / abandon 的队列处置（R1' §3.2 保守推荐，待主会话确认）：清空待绘并
     * 回 [reason]。**不闩死**——重建成功后新帧照常入队。在飞帧不在此处置：它的 waiter
     * 由其自身会话（死亡确认/超时路径）完成。
     */
    fun dropPending(reason: VisualExecutionRejection, message: String) {
        synchronized(lock) {
            if (closed) return
            drainPendingLocked(reason, message)
        }
    }

    /** close() 一次性闩 + 清队。幂等。在飞帧的 waiter 由引擎既有 markDead 路径完成。 */
    fun close(message: String) {
        synchronized(lock) {
            if (closed) return
            closed = true
            drainPendingLocked(VisualExecutionRejection.ENGINE_CLOSED, message)
        }
    }

    // ---------------------------------------------------------------- 测试 seam

    /** A-9 断言"连续 BUSY 不放大队列"需要观测队列长度；与 *_ForTest 系列同纪律。 */
    internal fun pendingCountForTest(): Int = synchronized(lock) { pending.size }

    internal fun inFlightForTest(): Boolean = synchronized(lock) { inFlight }

    // ---------------------------------------------------------------- 内部

    private fun launchSession(frame: Frame) {
        // ATOMIC：scope 在 dispatch 前被取消（未走 close() 的异常收尾）时协程仍会进入函数体，
        // 由下方 catch 完成 waiter——否则排队等待者永久悬挂。
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                runFrame(frame)
            } catch (failure: Throwable) {
                // runFrame 合同是"恰完成一次 waiter"；这里只兜底实现抛出而未完成的形状
                //（含 scope 取消），等待者永不悬挂。端口契约：execute 不抛库异常。
                if (!frame.waiter.isCompleted) {
                    frame.waiter.complete(
                        rejected(
                            VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
                            "visual_js frame execution failed: ${failure.javaClass.simpleName}",
                        ),
                    )
                }
            } finally {
                scheduleNext()
            }
        }
    }

    private fun scheduleNext() {
        synchronized(lock) {
            inFlight = false
            if (closed || !scope.isActive) {
                drainPendingLocked(VisualExecutionRejection.ENGINE_CLOSED, CLOSED_DIAGNOSTICS)
                return
            }
            val next = pending.removeFirstOrNull() ?: return
            inFlight = true
            // 锁内 launch 只投递调度，不阻塞；新会话若同步完成，其 finally 会短暂等本临界区。
            launchSession(next)
        }
    }

    /** 持锁清队。等待者可能已被取消：complete 返回 false，取消路径自会终结。 */
    private fun drainPendingLocked(
        reason: VisualExecutionRejection,
        message: String,
    ) {
        val dropped = pending.toList()
        pending.clear()
        dropped.forEach { frame -> frame.waiter.complete(rejected(reason, message)) }
    }

    private fun rejected(
        reason: VisualExecutionRejection,
        message: String,
    ): VisualExecutionResult = VisualExecutionResult.Rejected(
        reason,
        message.take(FigureGenerationOutcome.MAX_DIAGNOSTICS_CHARS),
    )

    private companion object {
        /** 契约 §64：最多两个待绘 program。 */
        const val MAX_PENDING_FRAMES: Int = 2

        const val SUPERSEDED_DIAGNOSTICS: String =
            "superseded by a newer state of the same program (visual_js frame queue, contract 64)"

        const val QUEUE_FULL_DIAGNOSTICS: String =
            "visual_js frame queue is full (1 in-flight + 2 pending)"

        const val CLOSED_DIAGNOSTICS: String = "the visual_js client is closed"
    }
}
