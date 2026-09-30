package com.tingyun.smartmistakebook.core.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W1-6/P1 + W2-4/KF-25 的单源学习日契约。
 *
 * 三源统一后，"时间戳 + 偏移 → 学习日"只有一个实现（[StudyDayMath]），本测试钉住四件事：
 * 1. **与 zone 规则推导逐值同构**——学习日 = "本地墙钟减去 day_start(04:00) 偏移"后的日历日，
 *    实现是纯算术，但必须与 zone 规则算出的同一语义逐值相同（统一不能顺带改数值口径）；
 * 2. **日界在本地 04:00 而不是午夜**（W2-4/KF-25，官方 day_start）——本地 00:00–04:00
 *    归前一个学习日，跨午夜的复习不再开启新的一天；
 * 3. **同一天内的两次复习 delta_t = 0**，跨学习日边界才是 1；
 * 4. **偏移是唯一时区输入**：同一时间戳在不同偏移下学习日可以不同。
 */
class StudyDayMathTest {

    @Test
    fun `study day agrees with the zone-rules derivation shifted by day start`() {
        val zone = ZoneId.of("America/New_York")
        val probes = listOf(
            1_772_000_000_000L, // 纽约 2026-02-25 01:13（EST）
            1_771_995_540_000L, // 纽约 2026-02-24 23:59
            1_771_995_660_000L, // 纽约 2026-02-25 00:01（跨午夜但未到 04:00）
            1_772_034_600_000L, // 上海 2026-02-25 23:50（+08:00 侧）
            1_772_036_400_000L, // 上海 2026-02-26 00:20
            0L, // 纪元
            -86_400_000L, // 纪元前 1 天（floorDiv 的负值边界）
        )

        probes.forEach { millis ->
            val local = Instant.ofEpochMilli(millis).atZone(zone)
            val offsetMinutes = local.offset.totalSeconds / 60
            val shiftedToDayStartAxis = Instant.ofEpochMilli(
                millis - StudyDayMath.DAY_START_OFFSET_MILLIS,
            ).atZone(zone)

            assertEquals(
                "时间戳 $millis 的学习日必须等于「本地墙钟 − 04:00」的日历日",
                shiftedToDayStartAxis.toLocalDate().toEpochDay(),
                StudyDayMath.localEpochDayOf(millis, offsetMinutes),
            )
        }
    }

    @Test
    fun `crossing the 4am study day boundary is what starts a new day`() {
        val offset = 480 // Asia/Shanghai +08:00
        val beforeBoundary = 1_772_049_000_000L // 本地 2026-02-26 03:50
        val afterBoundary = 1_772_050_200_000L // 本地 2026-02-26 04:10（相隔 20 分钟）

        assertEquals(
            "跨 04:00 学习日边界 = 1 个日历日（哪怕相隔只有 20 分钟）",
            1L,
            StudyDayMath.localEpochDayOf(afterBoundary, offset) -
                StudyDayMath.localEpochDayOf(beforeBoundary, offset),
        )
    }

    @Test
    fun `late night reviews still belong to the previous study day`() {
        val offset = 480
        val beforeMidnight = 1_772_034_600_000L // 本地 2026-02-25 23:50
        val afterMidnight = 1_772_036_400_000L // 本地 2026-02-26 00:20（相隔 30 分钟）

        assertEquals(
            "跨午夜但未到 04:00：两次复习同属一个学习日（delta_t = 0）",
            0L,
            StudyDayMath.localEpochDayOf(afterMidnight, offset) -
                StudyDayMath.localEpochDayOf(beforeMidnight, offset),
        )
    }

    @Test
    fun `a same study day pair is zero days apart however long it runs`() {
        val offset = 480
        val morning = 1_771_982_400_000L // 本地 2026-02-25 09:20
        val night = 1_772_031_600_000L // 本地 2026-02-25 23:00

        assertEquals(
            "同一学习日内的两次作答 delta_t = 0（时间跨了约 14 小时也一样）",
            0L,
            StudyDayMath.localEpochDayOf(night, offset) -
                StudyDayMath.localEpochDayOf(morning, offset),
        )
    }

    @Test
    fun `the same instant lands on different study days under different offsets`() {
        val instant = 1_772_056_800_000L // UTC 2026-02-25 22:00

        assertEquals("UTC 侧：学习日从 18:00 起算仍是 2026-02-25", 20_509L, StudyDayMath.localEpochDayOf(instant, 0))
        assertEquals(
            "+08:00 侧：本地 06:00 减 04:00 偏移已进入 02-26 的学习日",
            20_510L,
            StudyDayMath.localEpochDayOf(instant, 480),
        )
    }
}
