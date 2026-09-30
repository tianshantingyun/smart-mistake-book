package com.tingyun.smartmistakebook.core.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W1-6/P1 的单源日序契约（内核修复路线图 Wave 1）。
 *
 * 三源统一后，"时间戳 + 偏移 → 日序"只有一个实现（[StudyDayMath]），本测试钉住三件事：
 * 1. **与写路径原来那份 zone 规则推导逐值相同**——统一不能顺带改数值（老库里已落的日序、
 *    已写下的 delta_t 都是按旧口径来的，只换实现不换算术）；
 * 2. **跨本地午夜才算新的一天**（同一天内的两次作答 delta_t = 0，跨午夜 = 1）；
 * 3. **偏移是唯一时区输入**：同一时间戳在不同偏移下日序可以不同。
 *
 * 探针时刻都是算好的（本地午夜前后 1 分钟、纪元、纪元前一天），不是随手取的整数。
 */
class StudyDayMathTest {

    @Test
    fun `offset arithmetic agrees with the zone-rules derivation at every probed instant`() {
        val zone = ZoneId.of("America/New_York")
        val probes = listOf(
            1_772_000_000_000L, // 纽约 2026-02-25 01:13（EST，普通一天的中段）
            1_771_995_540_000L, // 纽约 2026-02-24 23:59（本地午夜前 1 分钟）
            1_771_995_660_000L, // 纽约 2026-02-25 00:01（本地午夜后 1 分钟）
            1_772_034_600_000L, // 上海 2026-02-25 23:50（跨时区取样：+08:00 侧）
            1_772_036_400_000L, // 上海 2026-02-26 00:20
            0L, // 纪元
            -86_400_000L, // 纪元前 1 天（floorDiv 的负值边界）
        )

        probes.forEach { millis ->
            val local = Instant.ofEpochMilli(millis).atZone(zone)
            val offsetMinutes = local.offset.totalSeconds / 60

            assertEquals(
                "时间戳 $millis 在 $zone 的日序必须与 zone 规则推导一致",
                local.toLocalDate().toEpochDay(),
                StudyDayMath.localEpochDayOf(millis, offsetMinutes),
            )
        }
    }

    @Test
    fun `a cross-midnight pair is one calendar day apart under the same offset`() {
        val offset = 480 // Asia/Shanghai +08:00
        val beforeMidnight = 1_772_034_600_000L // 本地 2026-02-25 23:50
        val afterMidnight = 1_772_036_400_000L // 本地 2026-02-26 00:20（相隔 30 分钟）

        assertEquals(
            "跨本地午夜 = 1 个日历日（哪怕相隔只有 30 分钟）",
            1L,
            StudyDayMath.calendarDaysBetween(beforeMidnight, afterMidnight, offset),
        )
    }

    @Test
    fun `a same-day pair is zero calendar days apart however long it runs`() {
        val offset = 480
        val morning = 1_771_982_400_000L // 本地 2026-02-25 09:20
        val night = 1_772_031_600_000L // 本地 2026-02-25 23:00

        assertEquals(
            "同一天内的两次作答 delta_t = 0（时间跨了约 14 小时也一样）",
            0L,
            StudyDayMath.calendarDaysBetween(morning, night, offset),
        )
    }

    @Test
    fun `the same instant lands on different days under different offsets`() {
        val instant = 1_772_056_800_000L // UTC 2026-02-25 22:00

        assertEquals("UTC 侧仍是本日", 20_509L, StudyDayMath.localEpochDayOf(instant, 0))
        assertEquals("+08:00 侧已是次日", 20_510L, StudyDayMath.localEpochDayOf(instant, 480))
    }
}
