package com.tingyun.smartmistakebook.visualjs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连接四阶段时间戳的 JVM 语义单测（批次 39 T4；KD-76 计时 seam 的纯语义面）。
 *
 * [ConnectStageTimestamps] 是随每次 bind 新建 Link 的 per-bind 诊断数据面（R2
 * `docs/research/2026-11-01-kd76-timing-calibration.md` §2.1）；它的写点全在
 * `VisualJsClientEngine` 的连接/会话流程内（需真机 binder 面，四阶段段值归设备轨 T5
 * 校准轮），JVM 可测的是访问器 `connectStageTimestampsForTest` 与设备轨 oracle 共同
 * 依赖的两条纯语义：
 *
 * 1. **0 = 未发生 + started() 门**：全零记录不得被当成有效计时数据——设备轨经访问器
 *    拿到的必须是 null 而不是全零记录。若该门失效，校准轮会把"未发生"当"0 ms 段长"
 *    出数（校准无数据源缺口收口后引入的新数据面，其可靠性由本测试钉住）。
 * 2. **首写点即 started**：bind 失败/attach 超时出口只写了 `bindRequestedAtMillis`
 *    （其余字段留 0），记录必须仍判 started（R2 §2.2 #1"时间戳照写，bindConnectedAt
 *    留 0 即未连接事实"）——若 started() 被改成要求后续字段非 0，bind 失败诊断面会
 *    静默丢失。
 */
class ConnectStageTimestampsTest {

    @Test
    fun `a fresh record is not started and every stage reads zero`() {
        val timestamps = ConnectStageTimestamps()

        assertFalse(timestamps.started())
        assertEquals(0L, timestamps.bindRequestedAtMillis)
        assertEquals(0L, timestamps.bindConnectedAtMillis)
        assertEquals(0L, timestamps.readyAtMillis)
        assertEquals(0L, timestamps.executeAcceptedAtMillis)
        assertEquals(0L, timestamps.firstByteAtMillis)
    }

    @Test
    fun `a bind request alone starts the record and later stages stay zero`() {
        val timestamps = ConnectStageTimestamps()

        timestamps.bindRequestedAtMillis = 1_000L

        assertTrue(timestamps.started())
        assertEquals(0L, timestamps.bindConnectedAtMillis)
        assertEquals(0L, timestamps.readyAtMillis)
        assertEquals(0L, timestamps.executeAcceptedAtMillis)
        assertEquals(0L, timestamps.firstByteAtMillis)
    }
}
