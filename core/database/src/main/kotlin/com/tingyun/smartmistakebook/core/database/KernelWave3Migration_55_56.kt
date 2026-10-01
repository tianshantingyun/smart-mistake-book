package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v55→56：**内核 Wave 3** 的掌握表示重做（roadmap W3-1 / KF-09）与知识点记忆卡
 * （W3-2 / E 判据，台账「裁决 13 · 修订」，2026-09-30）。
 *
 * 六列全是**投影产物**：
 * - `success_weight` / `failure_weight`：β-二项 s/f 计数（正向 s += w、负向 f += w；Jeffreys
 *   先验点估计与 Wilson 下界由它们派生，只服务展示层）；
 * - `memory_stability_days` / `memory_difficulty` / `last_attempt_at_epoch_millis` /
 *   `last_attempt_study_day`：**知识点自己的 FSRS 记忆状态**——同一套更新吃该知识点的作答流
 *   （`FsrsMemoryUpdateModel.nextMemoryState`，与逐题共用公式）；判据 = 稳定度 ≥ 21 天
 *   （Anki mature 口径）∧ 当前召回概率 ≥ 0.9。
 *
 * **不回填**（DEFAULT 0 / NULL）：投影表的值由全量重放重建——本波 `PROJECTOR` bump
 * （projector-v9）触发 `commitFullReplay`，`projection_archive` 先归档旧投影再覆盖
 * （W0-1 机制第二次实战）。任何"这里手工回填"的写法都会与重放结果分叉。
 */
internal val KERNEL_WAVE3_MIGRATION_55_56 = object : Migration(55, 56) {
    override suspend fun migrate(connection: SQLiteConnection) {
        MASTERY_MEMORY_CARD_DDL_55_56.forEach(connection::execSQL)
    }
}

internal val MASTERY_MEMORY_CARD_DDL_55_56: List<String> = listOf(
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `success_weight` REAL NOT NULL DEFAULT 0",
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `failure_weight` REAL NOT NULL DEFAULT 0",
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `memory_stability_days` REAL",
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `memory_difficulty` REAL",
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `last_attempt_at_epoch_millis` INTEGER",
    "ALTER TABLE `learner_knowledge_mastery_state` ADD COLUMN `last_attempt_study_day` INTEGER",
)
