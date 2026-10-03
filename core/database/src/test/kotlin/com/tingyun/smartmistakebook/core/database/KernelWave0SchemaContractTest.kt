package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **内核 Wave 0 的 schema 契约**（`docs/research/2026-09-28-kernel-remediation-roadmap.md` W0-1/W0-2）。
 *
 * 三层证据，都是 JVM 上就能炸的：
 * 1. **拒绝降级写**（W0-1 ②）：提交带的快照版本 ≠ 当前二进制期望版本 → 拒绝；
 *    两者一致 → 放行（"版本一致时行为不变"）；
 * 2. **归档表**（W0-1 ③）：`projection_archive` 的迁移 DDL 与导出的 54.json 同形，
 *    六列一列不少——归档少了列，回退时就会少一把钥匙；
 * 3. **指纹列合并**（W0-2）：`review_plan` 的 `input_fingerprint` 在 53.json 里存在、在 54.json 里不存在；
 *    迁移的 INSERT..SELECT **逐列覆盖新表的每一列**（非破坏 = 旧行照搬，而不是"重建后只剩默认值"）。
 *
 * 阶段 4A 批 1 追加一层：**当前 schema 头的一致性**（版本常量 == 导出的最高版本 JSON == 61），
 * 把"版本提了但 schema 没导出"从仪器化矩阵提前到 JVM 上炸。
 *
 * 与仪器测试的分工：这里管"DDL 与 schema 文件一致 + 语句形状"，
 * `FullMigrationMatrixInstrumentedTest` 管"真库迁移后结构与行都还在"。
 */
class KernelWave0SchemaContractTest {

    @Test
    fun `the study database version matches the latest exported schema`() {
        val latestExported = File("schemas")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .max()
        assertEquals(
            "版本常量必须等于导出的最高 schema 版本",
            latestExported,
            STUDY_DATABASE_VERSION,
        )
        assertEquals(
            "阶段 4A 批 4 的 schema 头是 61；下一次 bump 请同步本字面量",
            61,
            STUDY_DATABASE_VERSION,
        )
    }

    @Test
    fun `a commit carrying another projector version is refused`() {
        val failure = runCatching {
            DatabaseContractValidator.validateProjectionCommit(
                commit(snapshotProjectorVersion = "learning-core-v6(projector-v6,evidence-v4)"),
            )
        }

        assertTrue(
            "携带非当前版本快照的提交必须被拒：${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is DatabaseContractViolationException,
        )
    }

    @Test
    fun `a commit carrying the expected projector version is accepted`() {
        // 版本一致 = 行为不变：今天所有提交走的都是这一条。
        DatabaseContractValidator.validateProjectionCommit(
            commit(snapshotProjectorVersion = CURRENT_PROJECTOR_VERSION),
        )
    }

    @Test
    fun `the archive table migration matches the exported 54 schema`() {
        val exported = exportedTable("projection_archive", version = 54)
        val migrated = PROJECTION_ARCHIVE_DDL_53_54.first()

        assertEquals(
            "归档表的列必须与导出的 54.json 逐列同形（含 PK 与 NOT NULL）",
            columnShape(exported.createSql),
            columnShape(migrated.replace("${'$'}{TABLE_NAME}", "projection_archive")),
        )
        assertEquals(
            listOf(
                "archive_id",
                "projection_name",
                "learner_id",
                "archived_at_epoch_millis",
                "snapshot_json",
                "projector_version",
                "schema_ddl",
            ),
            columnShape(exported.createSql),
        )
        assertTrue(
            "归档表的索引名必须与 54.json 一致（回退查询按它排序）",
            exported.indices.any { it.createSql.contains("archived_at_epoch_millis") },
        )
        assertTrue(
            "索引也要由迁移建出来，否则 54.json 与真库会漂开",
            PROJECTION_ARCHIVE_DDL_53_54.any { it.contains("CREATE INDEX") },
        )
    }

    /**
     * KF-32 顺带核实：`CHAT_EVIDENCE_SUBMITTED` 自 v41 起就是一等账本事件（写入/解码/投影/
     * 回执映射四处齐全），但提交许可表漏了它——含 chat 回执的提交会被判 `Unknown ledger
     * event kind`，drainer 在 chat 写入后的下一批必被拒。本测试钉住修复：放行 chat 回执，
     * 同时**不放行** `BINDING_CHANGED`（改绑事件仅全量重放消费）。
     */
    @Test
    fun `chat evidence receipts are permitted while binding changes stay replay-only`() {
        fun commitWith(eventKind: String) = ProjectionCommit(
            projectionName = "study-experience-v1",
            learnerId = "learner-1",
            expectedPreviousCheckpoint = 1,
            expectedPreviousStateVersion = 0,
            mode = ProjectionCommitMode.FULL_REPLAY,
            knownLedgerHeadSequence = 2,
            consumedLedgerEvents = listOf(
                ConsumedLedgerEventReceipt(
                    eventKind = eventKind,
                    eventId = "event-1",
                    eventSequence = 2,
                    canonicalFingerprint = "fingerprint-1",
                ),
            ),
            presentationProjectionStates = emptyMap(),
            snapshot = LearnerSnapshot(
                learnerId = "learner-1",
                checkpoint = ProjectionCheckpoint(
                    lastSequence = 2,
                    projectorVersion = CURRENT_PROJECTOR_VERSION,
                    projectedAtEpochMillis = 1_000,
                ),
                knownLedgerHeadSequence = 2,
                generatedAtEpochMillis = 1_000,
            ),
            expectedProjectorVersion = CURRENT_PROJECTOR_VERSION,
        )

        DatabaseContractValidator.validateProjectionCommit(commitWith("CHAT_EVIDENCE_SUBMITTED"))
        DatabaseContractValidator.validateProjectionCommit(commitWith("BINDING_CHANGED"))
        val boundByTheIncrementalGate = runCatching {
            DatabaseContractValidator.validateProjectionCommit(
                commitWith("BINDING_CHANGED").copy(mode = ProjectionCommitMode.INCREMENTAL),
            )
        }.exceptionOrNull()
        assertTrue(
            "改绑事件不得混进增量提交：$boundByTheIncrementalGate",
            boundByTheIncrementalGate is DatabaseContractViolationException,
        )
    }

    @Test
    fun `the plan fingerprint column is merged away only in 54`() {        val v53 = columnShape(exportedTable("review_plan", version = 53).createSql)
        val v54 = columnShape(exportedTable("review_plan", version = 54).createSql)

        assertTrue("53 里必须还有 input_fingerprint（否则这条迁移没有意义）", "input_fingerprint" in v53)
        assertTrue("input_fingerprint 必须从 54 里消失", "input_fingerprint" !in v54)
        assertTrue("合并后的那一列必须在", "plan_fingerprint" in v54)
        assertTrue(
            "其余列一列不许少（合并只删那一列）",
            v53.toSet() - "input_fingerprint" == v54.toSet(),
        )
    }

    @Test
    fun `the plan table rebuild copies every column of the new shape onto the existing rows`() {
        val v54 = exportedTable("review_plan", version = 54)
        val columns = columnShape(v54.createSql)
        val rebuild = REVIEW_PLAN_FINGERPRINT_MERGE_DDL_53_54
        val insertSelect = rebuild.single { it.startsWith("INSERT INTO") }
        val selectList = insertSelect.substringAfter("SELECT").substringBefore("FROM")
            .replace("`", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        assertEquals("INSERT..SELECT 必须逐列搬运新表的每一列（非破坏）", columns, selectList)
        assertEquals(
            "重建后的表名与列必须与 54.json 同形",
            columnShape(v54.createSql),
            columnShape(
                rebuild.first { it.startsWith("CREATE TABLE") }
                    .replace("review_plan_v54", "review_plan")
                    .replace("${'$'}{TABLE_NAME}", "review_plan"),
            ),
        )
        assertTrue(
            "父键唯一索引必须重建：子表的复合外键指着它",
            rebuild.any {
                it.contains(
                    "index_review_plan_learner_id_local_day_epoch_day_time_zone_id_review_plan_id",
                )
            },
        )
        assertTrue(
            "迁移里不许留第二份指纹值（合并 = 旧值原样留下，不编造）",
            rebuild.none { it.contains(REVIEW_PLAN_MERGED_AWAY_COLUMN_53_54) },
        )
    }

    // ---- fixtures ----

    private fun commit(snapshotProjectorVersion: String) = ProjectionCommit(
        projectionName = "study-experience-v1",
        learnerId = "learner-1",
        expectedPreviousCheckpoint = 1,
        expectedPreviousStateVersion = 0,
        mode = ProjectionCommitMode.FULL_REPLAY,
        knownLedgerHeadSequence = 1,
        consumedLedgerEvents = emptyList(),
        presentationProjectionStates = emptyMap(),
        snapshot = LearnerSnapshot(
            learnerId = "learner-1",
            checkpoint = ProjectionCheckpoint(
                lastSequence = 1,
                projectorVersion = snapshotProjectorVersion,
                projectedAtEpochMillis = 1_000,
            ),
            knownLedgerHeadSequence = 1,
            generatedAtEpochMillis = 1_000,
        ),
        expectedProjectorVersion = CURRENT_PROJECTOR_VERSION,
    )

    /** 列名，按 DDL 里的出现顺序。 */
    private fun columnShape(createSql: String): List<String> =
        COLUMN_PATTERN.findAll(createSql).map { it.groupValues[1] }.toList()

    private fun exportedTable(tableName: String, version: Int): Wave0SchemaEntity =
        exportedDatabase(version).entities.single { it.tableName == tableName }

    private fun exportedDatabase(version: Int): Wave0SchemaDatabase {
        val schemaFile = File("schemas")
            .walkTopDown()
            .first { it.isFile && it.name == "$version.json" }
        return json.decodeFromString<Wave0SchemaFile>(schemaFile.readText()).database
    }

    private companion object {
        /**
         * 测试内自洽的"当前版本"字面量（提交与期望同用一个常量，validator 只比对两者相等）。
         * 2026-10-01 订正：此前停在 `learning-core-v8(projector-v8,evidence-v4)`（Wave 2 时代），
         * 与实际值脱节——"名字说当前、值却过时"。此处不可引用 `core:domain` 的
         * `LearningCoreVersions.PROJECTION_COMPOSITE`（依赖方向不允许），修改内核版本串时
         * 请同步本字面量。
         *
         * 2026-10-02（W4-2 投影批）：同步 `projector-v11` / `evidence-v5` / `attribution-v3`。
         * 2026-10-02（3B 批次 B4/KF-32）：同步 `projector-v12` / `attribution-v4` / `ledger-v3`。
         */
        const val CURRENT_PROJECTOR_VERSION =
            "learning-core-v12(projector-v12,evidence-v5,curve-v3,skip-v4,attribution-v4,ledger-v3)"

        val json = Json { ignoreUnknownKeys = true }
        /** 只认真正的列定义（反引号列名 + 类型），不认 PRIMARY KEY / FOREIGN KEY 子句里的列名。 */
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
    }
}

@Serializable
private data class Wave0SchemaFile(@SerialName("database") val database: Wave0SchemaDatabase)

@Serializable
private data class Wave0SchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<Wave0SchemaEntity>,
)

@Serializable
private data class Wave0SchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("createSql") val createSql: String,
    @SerialName("indices") val indices: List<Wave0SchemaIndex> = emptyList(),
)

@Serializable
private data class Wave0SchemaIndex(
    @SerialName("createSql") val createSql: String,
)
