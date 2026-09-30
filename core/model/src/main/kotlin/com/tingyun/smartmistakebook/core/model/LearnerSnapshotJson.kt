package com.tingyun.smartmistakebook.core.model

import kotlinx.serialization.json.Json

/**
 * 学习投影快照的归档 JSON 编解码（内核修复路线图 W0-1/Q2）。
 *
 * **它存在的理由**：投影是账本的派生态，`LearningProjector.replay` 会用新版本整份覆盖旧投影。
 * 覆盖之前必须先把被替换的那一份原样留下（`projection_archive.snapshot_json`），否则"改数值"
 * 这件事不可逆——这正是审计 Q2「无投影版本回退/防降级」的一半。归档行还记 `projector_version`
 * 与 `schema_ddl`，读回时才知道这份 JSON 属于哪个投影版本、要写回哪些表；
 * 恢复流程见 `docs/research/kernel-projection-rollback.md`。
 *
 * 三条编解码口径都是刻意的：
 * - `encodeDefaults = true`：归档要逐字段完整。省略"等于默认值"的字段会让读回时**分不清**
 *   "当年就是默认值"与"这一版还没有这个字段"，而这两种情况在回退时的处置完全不同。
 * - `ignoreUnknownKeys = false`：形状不认识就抛，不静默丢字段。归档的价值在于"原样"，不在"尽力"。
 * - `decode` 会走 `LearnerSnapshot`/子类型的 `init { require(...) }`：坏归档当场失败，不会写回半截状态。
 */
object LearnerSnapshotJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    fun encode(snapshot: LearnerSnapshot): String = json.encodeToString(snapshot)

    fun decode(value: String): LearnerSnapshot = json.decodeFromString(value)
}
