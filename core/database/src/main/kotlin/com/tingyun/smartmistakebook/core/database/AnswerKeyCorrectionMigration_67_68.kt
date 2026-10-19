package com.tingyun.smartmistakebook.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v67→68（批次 15 T2 / PRD-02 / KD-43）：`learner_chat_evidence` 增加
 * `corrects_evidence_id TEXT NULL` + 索引。
 *
 * 消灭的具体失败（AUD-11 §3.1/§3.3）：错键落下的证据没有任何**明确目标**的更正/撤销事实——
 * 既无法定位"要纠正哪一条"，也无法在重放时把被撤销的证据从有效集合里拿掉。列本身不改变
 * 既有行：`ALTER TABLE ... ADD COLUMN` 是追加，旧行读回 NULL（"不是一条纠正"）。
 *
 * 纯非破坏新增：不改写/删除任何历史证据行，不回填、不猜配纠正关系。迁移只加列 + 一条
 * `corrects_evidence_id` 索引（迟到旧判分校验/回执/审计按目标定位）。
 *
 * 注：`schemas/68.json` 是构建产物（KSP 写出）；本文件里的 DDL 与它逐字一致。
 */
internal val ANSWER_KEY_CORRECTION_MIGRATION_67_68 =
    object : Migration(67, 68) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(LEARNER_CHAT_EVIDENCE_CORRECTS_DDL_67_68)
            connection.execSQL(LEARNER_CHAT_EVIDENCE_CORRECTS_INDEX_DDL_67_68)
        }
    }

/**
 * 与 `schemas/68.json` 里 `learner_chat_evidence.corrects_evidence_id` 列声明逐字一致。
 * 可空：普通证据与"中性预报告"都没有目标；只有账本更正行必须非空（读边界显式校验）。
 */
internal val LEARNER_CHAT_EVIDENCE_CORRECTS_DDL_67_68: String =
    "ALTER TABLE `learner_chat_evidence` ADD COLUMN `corrects_evidence_id` TEXT"

/** 与 `schemas/68.json` 里该列索引的名称逐字一致。 */
internal val LEARNER_CHAT_EVIDENCE_CORRECTS_INDEX_DDL_67_68: String =
    "CREATE INDEX IF NOT EXISTS `index_learner_chat_evidence_corrects_evidence_id` " +
        "ON `learner_chat_evidence` (`corrects_evidence_id`)"
