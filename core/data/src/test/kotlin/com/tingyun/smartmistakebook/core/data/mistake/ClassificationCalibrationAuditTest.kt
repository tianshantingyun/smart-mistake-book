package com.tingyun.smartmistakebook.core.data.mistake

import com.tingyun.smartmistakebook.core.database.ProblemClassificationCandidateRecord
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K5-B：分类置信度**校准审计件**（可运行）——按 confidence 分桶统计「自动接受标签后来被用户
 * 纠正 / 未被纠正」的比例，回答"自报各档置信度的实测正确率"。
 *
 * ## 为什么是 JVM 测试，不是查询脚本
 * - 本模块 JVM 测试类路径**已有** `sqlite-jdbc`（`core/data/build.gradle.kts:197`；先例
 *   `Stage1LexicalLab` 在 JVM 里跑真 SQLite FTS5）——审计直接打开真库、跑真 SQL，不需要新工具链；
 * - 阈值与 margin 语义**只有一份实现**（主源码 `ClassificationCandidateMargin.kt`），审计调用它，
 *   不会出现"脚本里另写一份 0.02"的漂移；
 * - 它随既有命令运行（`:core:data:testDebugUnitTest`），门与证据同一处。
 *
 * ## 口径（必须照录，不是装饰）
 * - **代理金标 = 用户纠正**，不是真金标（研究文档
 *   `docs/research/2026-10-11-r1-r2-label-accuracy-research.md` §1/§2 的证据边界：我们没有金标子集）；
 * - **add-only 语义的局限**：**未被人纠正 ≠ 正确**——用户可能从未复核、也可能从不纠正；
 *   用户设计底线是"不要把这种问题交给用户判断"（裁定 Q2），所以本审计**只出数**，
 *   **不**据此自动改写/删除/降信任何标签（研究 §0：外部无先例支持静默自纠）；
 * - 样本从 schema 65 起积累：v65 之前只存被接受标签，被用户纠正后那些行已被替换删除
 *   （`RoomProblemOrganizationStore.confirm` 先 `deleteClassifications` 再写用户集），
 *   因此**无法**用绑定表回溯"被纠正的自动标签"——候选表（含被接受候选）才是可审计的载体；
 * - 分数被接受门截断 ⇒ **选择偏差**：被拒候选的分数分布单独呈现。
 *
 * ## 怎么对真数据跑
 * 把库文件路径给到环境变量 `SMARTMISTAKE_AUDIT_DATABASE`（或系统属性
 * `smartmistake.audit.database`；env 优先），然后：
 * `./gradlew :core:data:testDebugUnitTest --tests "*ClassificationCalibrationAuditTest*"`
 * 没给路径（或库里还没有候选）时报告 **【无样本】** ——不编造任何比例。
 *
 * 夹具与查询全部走 `PreparedStatement` 参数绑定，执行的 SQL 文本只有编译期字面量（本仓硬约束：
 * 不许把值拼进 SQL 文本）。夹具表的列名/类型由 [assertFixtureMatchesExportedSchema] 对着导出的
 * `65.json` 逐列核对——夹具不许与生产 schema 漂开。
 */
class ClassificationCalibrationAuditTest {

    @Test
    fun `a missing database is a no-sample report and never a fabricated rate`() {
        val report = runAudit(
            databaseFile = File("build/audit/definitely-absent-${System.nanoTime()}.db"),
            threshold = SUSPECT_NORMALIZED_MARGIN_THRESHOLD,
        )

        assertTrue("没有库就是无样本", report.noSamples)
        assertTrue("无样本时不许给出任何桶比例", report.buckets.isEmpty())
        assertTrue("理由必须写清楚（不是静默空报告）", report.notes.any { "找不到库文件" in it })
        println(report.render())
    }

    @Test
    fun `an empty candidate table is a no-sample report`() {
        val database = createFixtureDatabase()
        try {
            val report = runAudit(database, threshold = SUSPECT_NORMALIZED_MARGIN_THRESHOLD)

            assertTrue("库在但没有候选 ⇒ 无样本（真实数据到齐前的常态）", report.noSamples)
            assertTrue(report.buckets.isEmpty())
            assertTrue(report.notes.any { "没有候选行" in it })
            println(report.render())
        } finally {
            database.delete()
        }
    }

    /**
     * 夹具自检：真 SQLite 库里铺三类题——未被纠正 / 被用户纠正（含被保留的标签）/ 状态不明——
     * 再跑同一份审计代码，钉住：
     * 1. 夹具列形状与导出的 `65.json` 逐列一致（审计 SQL 的列名不许靠猜）；
     * 2. 分桶计数与代理正确率；
     * 3. "被纠正"判定 = 当前绑定集是用户权威 **且** 该标签身份不在其中（被用户保留的不算纠正）；
     * 4. 无绑定的题判**不可判**（不是"未纠正"）；
     * 5. 存疑信号（margin ≤ 0.02，含并列/负 margin）；
     * 6. 被拒候选进入"选择偏差"段；
     * 7. 审计**只读**：跑完行数不变。
     */
    @Test
    fun `the audit pins buckets correction proxy and suspect signals on a real sqlite fixture`() {
        val database = createFixtureDatabase()
        try {
            seedFixture(database)
            val candidatesBefore = countCandidateRows(database)
            val bindingsBefore = countBindingRows(database)

            val report = runAudit(database, threshold = SUSPECT_NORMALIZED_MARGIN_THRESHOLD)

            assertFalse("夹具里必须有样本", report.noSamples)
            val buckets = report.buckets.associateBy { it.label }
            assertEquals(
                "桶边界与计数（gate=0.78 起分档）",
                listOf(4, 2, 1, 0, 0),
                listOf(
                    buckets.getValue("[0.78,0.85)").sampleCount,
                    buckets.getValue("[0.85,0.90)").sampleCount,
                    buckets.getValue("[0.90,0.95)").sampleCount,
                    buckets.getValue("[0.95,1.00]").sampleCount,
                    buckets.getValue("越界(<0.78 或 >1.0)").sampleCount,
                ),
            )
            assertEquals(
                "[0.78,0.85)：0.80 未纠正 + 0.79 被用户保留 + 0.82/0.81 无绑定 ⇒ 不可判",
                listOf(0, 2, 2),
                buckets.getValue("[0.78,0.85)").counts(),
            )
            assertEquals(
                "[0.85,0.90)：0.88/0.87 都被用户纠正",
                listOf(2, 0, 0),
                buckets.getValue("[0.85,0.90)").counts(),
            )
            assertEquals(
                "[0.90,0.95)：0.90 未被纠正",
                listOf(0, 1, 0),
                buckets.getValue("[0.90,0.95)").counts(),
            )
            assertEquals(
                "代理正确率 = 未纠正 / (纠正 + 未纠正)，不可判不参与分母",
                1.0,
                buckets.getValue("[0.78,0.85)").proxyAccuracy!!,
                0.0,
            )
            assertEquals(
                "被用户纠正的两档：代理正确率 0.0（这正是「自报高置信 ≠ 正确」的证据）",
                0.0,
                buckets.getValue("[0.85,0.90)").proxyAccuracy!!,
                0.0,
            )
            assertEquals(
                "被拒候选照样进选择偏差段（其中过门但被挤掉的要单独可数）",
                listOf(3, 1),
                listOf(report.rejectedCandidateCount, report.rejectedAboveGateCount),
            )
            assertEquals(
                "存疑信号：P2 两个知识点 margin 0.01 / -0.01 ⇒ 都存疑",
                listOf("函数单调性", "导数符号"),
                report.suspectSignals.map { it.displayName }.sorted(),
            )
            assertTrue(
                "无对手的已接受标签 margin 不可判（不是 0、也不标存疑）",
                report.unjudgeableSignals.any { it.displayName == "函数" },
            )
            assertEquals(
                "审计只读：候选行一行不多一行不少",
                candidatesBefore,
                countCandidateRows(database),
            )
            assertEquals(
                "审计只读：绑定行一行不多一行不少",
                bindingsBefore,
                countBindingRows(database),
            )
            println(report.render())
        } finally {
            database.delete()
        }
    }

    @Test
    fun `the database path comes from the environment first and the system property second`() {
        assertEquals(
            "env 优先",
            "/tmp/from-env.db",
            resolveAuditDatabasePath(
                environmentValue = "/tmp/from-env.db",
                propertyValue = "/tmp/from-property.db",
            ),
        )
        assertEquals(
            "env 缺席时读系统属性",
            "/tmp/from-property.db",
            resolveAuditDatabasePath(environmentValue = null, propertyValue = "/tmp/from-property.db"),
        )
        assertNull(
            "都没有 ⇒ null（报告无样本，不猜默认路径）",
            resolveAuditDatabasePath(environmentValue = " ", propertyValue = null),
        )
    }

    // ---- 审计执行体（测试内，不新增生产面） ----

    private fun runAudit(databaseFile: File, threshold: Double): CalibrationAuditReport {
        if (!databaseFile.isFile) {
            return CalibrationAuditReport.noSamples(
                source = databaseFile.absolutePath,
                reason = "找不到库文件（把真库路径给 SMARTMISTAKE_AUDIT_DATABASE 再跑）",
            )
        }
        openAuditConnection(databaseFile).use { connection ->
            if (!connection.tableExists(CANDIDATE_TABLE)) {
                return CalibrationAuditReport.noSamples(
                    source = databaseFile.absolutePath,
                    reason = "库里没有 $CANDIDATE_TABLE 表（schema < 65：候选集从 65 起积累）",
                )
            }
            val candidates = connection.readCandidates()
            if (candidates.isEmpty()) {
                return CalibrationAuditReport.noSamples(
                    source = databaseFile.absolutePath,
                    reason = "$CANDIDATE_TABLE 没有候选行（尚未有 v65 之后的整理确认）",
                )
            }
            return buildReport(
                source = databaseFile.absolutePath,
                candidates = candidates,
                bindings = connection.readBindings(),
                threshold = threshold,
            )
        }
    }

    /**
     * 报告口径（写死在这里，改动必须同时改 KDoc 与断言）：
     * - 样本 = `accepted = true` **且** `acceptance_source = LOCAL_POLICY_ACCEPTED` 的候选
     *   （"自动接受标签"；用户勾选接受的可能低于接受门，混进来就不是同一口径）；
     * - 代理金标 = 用户纠正：同题同修订的**当前**绑定集里出现用户权威来源时，该标签身份
     *   （dimension + label_id）不在其中 ⇒ 被纠正；在 ⇒ 未被纠正（用户保留/重选）；
     * - 当前绑定集为空（整题没有任何绑定）⇒ **不可判**，不记入"未纠正"；
     * - 桶边界：`[0.78,0.85)`、`[0.85,0.90)`、`[0.90,0.95)`、`[0.95,1.00]`、越界。
     */
    private fun buildReport(
        source: String,
        candidates: List<ProblemClassificationCandidateRecord>,
        bindings: List<BindingFact>,
        threshold: Double,
    ): CalibrationAuditReport {
        val signals = acceptedLabelMarginSignals(candidates, threshold)
        val currentBindings = bindings.groupBy { it.problemId to it.basisRevisionId }
        val acceptedAuto = candidates.filter { candidate ->
            candidate.accepted && candidate.acceptanceSource == "LOCAL_POLICY_ACCEPTED"
        }
        val observations = acceptedAuto.map { candidate ->
            CalibrationObservation(
                confidence = candidate.confidence,
                outcome = correctionOutcome(
                    candidate,
                    currentBindings[candidate.problemId to candidate.basisRevisionId],
                ),
            )
        }
        val rejected = candidates.filterNot { it.accepted }
        return CalibrationAuditReport(
            source = source,
            noSamples = observations.isEmpty(),
            buckets = calibrationBuckets(observations),
            rejectedCandidateCount = rejected.size,
            rejectedAboveGateCount = rejected.count {
                it.confidence >= CLASSIFICATION_ACCEPTANCE_CONFIDENCE
            },
            suspectSignals = signals.filter { it.suspect },
            unjudgeableSignals = signals.filter { it.margin == null },
            notes = buildList {
                add(PROXY_GOLD_NOTE)
                add(ADD_ONLY_LIMITATION_NOTE)
                add(SAMPLE_WINDOW_NOTE)
                add(SELECTION_BIAS_NOTE)
                if (observations.isEmpty()) {
                    add("样本为空：没有 accepted=1 且 LOCAL_POLICY_ACCEPTED 的候选行")
                }
            },
        )
    }

    private fun correctionOutcome(
        candidate: ProblemClassificationCandidateRecord,
        current: List<BindingFact>?,
    ): ProxyGoldOutcome {
        if (current.isNullOrEmpty()) return ProxyGoldOutcome.INDETERMINATE
        val userOwned = current.any { it.acceptanceSource in USER_OWNED_SOURCES }
        if (!userOwned) return ProxyGoldOutcome.NOT_CORRECTED
        val kept = current.any {
            it.dimension == candidate.dimension && it.labelId == candidate.labelId
        }
        return if (kept) ProxyGoldOutcome.NOT_CORRECTED else ProxyGoldOutcome.CORRECTED
    }

    private fun calibrationBuckets(
        observations: List<CalibrationObservation>,
    ): List<CalibrationBucket> = BUCKET_BOUNDS.map { (label, lowerInclusive, upperExclusive) ->
        val inBucket = observations.filter { observation ->
            observation.confidence >= lowerInclusive && observation.confidence < upperExclusive
        }
        CalibrationBucket(
            label = label,
            correctedCount = inBucket.count { it.outcome == ProxyGoldOutcome.CORRECTED },
            notCorrectedCount = inBucket.count { it.outcome == ProxyGoldOutcome.NOT_CORRECTED },
            indeterminateCount = inBucket.count { it.outcome == ProxyGoldOutcome.INDETERMINATE },
        )
    }

    /** 打开审计连接：路径来自本机（开发者显式给出的库文件），JDBC URL 不接受参数绑定。 */
    private fun openAuditConnection(databaseFile: File): Connection =
        DriverManager.getConnection(SQLITE_URL_PREFIX + databaseFile.absolutePath)

    // ---- SQLite 夹具（JVM 真库；列形状对着导出的 65.json 逐列核对） ----

    private fun createFixtureDatabase(): File {
        assertFixtureMatchesExportedSchema()
        val file = File.createTempFile("classification-calibration-audit-", ".db")
        file.delete()
        openAuditConnection(file).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(CANDIDATE_TABLE_DDL)
                statement.execute(BINDING_TABLE_DDL)
            }
        }
        return file
    }

    /**
     * 夹具的表必须与导出的 `65.json` **逐列同序同名同类型**——审计 SQL 读的就是这些列，
     * 漂开会让审计在真库上静默读错列。整张 DDL（含 PK/FK/索引）与迁移的逐字一致性由
     * core:database 的 `ProblemClassificationCandidateSchemaContractTest` 钉住；
     * 这里只需钉住审计依赖的列形状（夹具是只读审计的靶子，不建约束）。
     */
    private fun assertFixtureMatchesExportedSchema() {
        listOf(CANDIDATE_TABLE to CANDIDATE_TABLE_DDL, BINDING_TABLE to BINDING_TABLE_DDL)
            .forEach { (table, fixtureDdl) ->
                val exported = exportedEntity(table)
                assertEquals(
                    "夹具表 $table 的列必须与导出的 $SCHEMA_VERSION.json 同序同名同类型",
                    exported.fields.map { it.columnName to it.affinity },
                    COLUMN_PATTERN.findAll(fixtureDdl)
                        .map { it.groupValues[1] to it.groupValues[2] }
                        .toList(),
                )
            }
    }

    private fun exportedEntity(table: String): ExportedSchemaEntity {
        val schemaFile = SCHEMA_FILE_CANDIDATES
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("导出的 $SCHEMA_VERSION.json 不在预期路径（$SCHEMA_FILE_CANDIDATES）")
        return json.decodeFromString<ExportedSchemaFile>(schemaFile.readText())
            .database.entities.single { it.tableName == table }
    }

    private fun seedFixture(database: File) {
        openAuditConnection(database).use { connection ->
            // P1：自动接受后**未被纠正**；被拒候选 0.40（没过门）。
            connection.insertCandidate("p1", "r1", "KNOWLEDGE", "二次函数最值", 0.90, accepted = true)
            connection.insertCandidate("p1", "r1", "KNOWLEDGE", "导数符号", 0.40, accepted = false)
            connection.insertCandidate("p1", "r1", "CHAPTER", "函数", 0.80, accepted = true)
            connection.insertBinding("p1", "r1", "KNOWLEDGE", "二次函数最值", "LOCAL_POLICY_ACCEPTED")
            connection.insertBinding("p1", "r1", "CHAPTER", "函数", "LOCAL_POLICY_ACCEPTED")
            // P2：被用户纠正——两个知识点被删掉，章节"函数"被用户保留；另有被拒 0.85（过门但被挤掉）。
            connection.insertCandidate("p2", "r2", "KNOWLEDGE", "导数符号", 0.88, accepted = true)
            connection.insertCandidate("p2", "r2", "KNOWLEDGE", "函数单调性", 0.87, accepted = true)
            connection.insertCandidate("p2", "r2", "KNOWLEDGE", "集合运算", 0.85, accepted = false)
            connection.insertCandidate("p2", "r2", "CHAPTER", "函数", 0.79, accepted = true)
            connection.insertBinding("p2", "r2", "CHAPTER", "函数", "USER_CORRECTED")
            // P3：当前绑定集为空 ⇒ 状态不明（不记"未纠正"）。
            connection.insertCandidate("p3", "r3", "KNOWLEDGE", "集合运算", 0.82, accepted = true)
            connection.insertCandidate("p3", "r3", "KNOWLEDGE", "集合", 0.60, accepted = false)
            connection.insertCandidate("p3", "r3", "CHAPTER", "集合", 0.81, accepted = true)
        }
    }

    private fun Connection.insertCandidate(
        problemId: String,
        revisionId: String,
        dimension: String,
        displayName: String,
        confidence: Double,
        accepted: Boolean,
    ) {
        prepareStatement(
            "INSERT INTO `problem_classification_candidate` (`candidate_id`, `problem_id`, " +
                "`basis_revision_id`, `dimension`, `label_id`, `display_name`, `confidence`, " +
                "`accepted`, `acceptance_source`, `accepted_at_epoch_millis`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'LOCAL_POLICY_ACCEPTED', 100)",
        ).use { statement ->
            statement.setString(1, "$dimension:$displayName@$revisionId")
            statement.setString(2, problemId)
            statement.setString(3, revisionId)
            statement.setString(4, dimension)
            statement.setString(5, "$dimension:$displayName")
            statement.setString(6, displayName)
            statement.setDouble(7, confidence)
            statement.setInt(8, if (accepted) 1 else 0)
            statement.execute()
        }
    }

    private fun Connection.insertBinding(
        problemId: String,
        revisionId: String,
        dimension: String,
        displayName: String,
        acceptanceSource: String,
    ) {
        prepareStatement(
            "INSERT INTO `problem_classification_binding` (`binding_id`, `problem_id`, " +
                "`basis_revision_id`, `dimension`, `label_id`, `display_name`, `taxonomy_version`, " +
                "`acceptance_source`, `accepted_at_epoch_millis`) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'local-policy-v1', ?, 200)",
        ).use { statement ->
            statement.setString(1, "binding:$dimension:$displayName@$revisionId")
            statement.setString(2, problemId)
            statement.setString(3, revisionId)
            statement.setString(4, dimension)
            statement.setString(5, "$dimension:$displayName")
            statement.setString(6, displayName)
            statement.setString(7, acceptanceSource)
            statement.execute()
        }
    }

    private fun countCandidateRows(database: File): Int =
        openAuditConnection(database).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM `problem_classification_candidate`",
                ).use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            }
        }

    private fun countBindingRows(database: File): Int =
        openAuditConnection(database).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT COUNT(*) FROM `problem_classification_binding`",
                ).use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            }
        }

    private fun Connection.tableExists(table: String): Boolean =
        prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")
            .use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { result -> result.next() }
            }

    private fun Connection.readCandidates(): List<ProblemClassificationCandidateRecord> =
        prepareStatement(
            "SELECT `candidate_id`, `problem_id`, `basis_revision_id`, `dimension`, `label_id`, " +
                "`display_name`, `confidence`, `accepted`, `acceptance_source`, " +
                "`accepted_at_epoch_millis` FROM `problem_classification_candidate`",
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            ProblemClassificationCandidateRecord(
                                candidateId = result.getString(1),
                                problemId = result.getString(2),
                                basisRevisionId = result.getString(3),
                                dimension = result.getString(4),
                                labelId = result.getString(5),
                                displayName = result.getString(6),
                                confidence = result.getDouble(7),
                                accepted = result.getInt(8) != 0,
                                acceptanceSource = result.getString(9),
                                acceptedAtEpochMillis = result.getLong(10),
                            ),
                        )
                    }
                }
            }
        }

    private fun Connection.readBindings(): List<BindingFact> =
        prepareStatement(
            "SELECT `problem_id`, `basis_revision_id`, `dimension`, `label_id`, " +
                "`acceptance_source` FROM `problem_classification_binding`",
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            BindingFact(
                                problemId = result.getString(1),
                                basisRevisionId = result.getString(2),
                                dimension = result.getString(3),
                                labelId = result.getString(4),
                                acceptanceSource = result.getString(5),
                            ),
                        )
                    }
                }
            }
        }

    private companion object {
        const val CANDIDATE_TABLE = "problem_classification_candidate"
        const val BINDING_TABLE = "problem_classification_binding"
        const val SQLITE_URL_PREFIX = "jdbc:sqlite:"

        /** 夹具表（只读审计的靶子）：列名/类型与导出 schema 逐列核对，不建约束与索引。 */
        const val CANDIDATE_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS `problem_classification_candidate` (" +
                "`candidate_id` TEXT NOT NULL, `problem_id` TEXT NOT NULL, " +
                "`basis_revision_id` TEXT NOT NULL, `dimension` TEXT NOT NULL, " +
                "`label_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
                "`confidence` REAL NOT NULL, `accepted` INTEGER NOT NULL, " +
                "`acceptance_source` TEXT NOT NULL, " +
                "`accepted_at_epoch_millis` INTEGER NOT NULL)"
        const val BINDING_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS `problem_classification_binding` (" +
                "`binding_id` TEXT NOT NULL, `problem_id` TEXT NOT NULL, " +
                "`basis_revision_id` TEXT NOT NULL, `dimension` TEXT NOT NULL, " +
                "`label_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, " +
                "`taxonomy_version` TEXT NOT NULL, `acceptance_source` TEXT NOT NULL, " +
                "`accepted_at_epoch_millis` INTEGER NOT NULL, `accepted_confidence` REAL)"
        val COLUMN_PATTERN = Regex("`([a-z_]+)` (TEXT|INTEGER|REAL|BLOB)")
        val USER_OWNED_SOURCES = setOf("USER_CORRECTED", "USER_CONFIRMED")
        const val PROXY_GOLD_NOTE =
            "金标口径：**代理金标 = 用户纠正**，不是真金标（研究文档 §1/§2 证据边界）——" +
                "把代理当金标读会高估/低估真实正确率"
        const val ADD_ONLY_LIMITATION_NOTE =
            "局限：未被人纠正 ≠ 正确（add-only 语义；用户可能从未复核/从不纠正；" +
                "用户设计底线是「不要把这种问题交给用户判断」）——本审计只出数，不自动改写任何标签"
        const val SAMPLE_WINDOW_NOTE =
            "样本窗口：从 schema 65（候选集落库）起积累；被纠正的自动标签行在绑定表里已被替换删除，" +
                "只有候选表能回溯"
        const val SELECTION_BIAS_NOTE =
            "选择偏差：分数被接受门截断——被拒候选的分数分布见「选择偏差」段，" +
                "桶内正确率不代表「该分数段模型都这么准」"
        const val SCHEMA_VERSION = 65
        const val SCHEMA_DIR = "com.tingyun.smartmistakebook.core.database.StudyDatabase"
        val SCHEMA_FILE_CANDIDATES = listOf(
            "../database/schemas/$SCHEMA_DIR/$SCHEMA_VERSION.json",
            "schemas/$SCHEMA_DIR/$SCHEMA_VERSION.json",
        )
        val json = Json { ignoreUnknownKeys = true }

        /** 桶边界（写死在这里；改边界必须同时改断言与 KDoc）。 */
        val BUCKET_BOUNDS = listOf(
            Triple("[0.78,0.85)", 0.78, 0.85),
            Triple("[0.85,0.90)", 0.85, 0.90),
            Triple("[0.90,0.95)", 0.90, 0.95),
            Triple("[0.95,1.00]", 0.95, 1.01),
            Triple("越界(<0.78 或 >1.0)", -1.0, 0.78),
        )
    }
}

/** 审计输入：一条**当前**绑定事实（只读，用于代理金标判定）。 */
private data class BindingFact(
    val problemId: String,
    val basisRevisionId: String,
    val dimension: String,
    val labelId: String,
    val acceptanceSource: String,
)

/** 代理金标判定：被用户纠正 / 未被纠正 / 状态不明（不可判是一等输出）。 */
private enum class ProxyGoldOutcome { CORRECTED, NOT_CORRECTED, INDETERMINATE }

private data class CalibrationObservation(
    val confidence: Double,
    val outcome: ProxyGoldOutcome,
)

private data class CalibrationBucket(
    val label: String,
    val correctedCount: Int,
    val notCorrectedCount: Int,
    val indeterminateCount: Int,
) {
    val sampleCount: Int get() = correctedCount + notCorrectedCount + indeterminateCount

    /** 代理正确率 = 未纠正 /（纠正 + 未纠正）；不可判不进分母（不可判不是"正确"）。 */
    val proxyAccuracy: Double?
        get() = (correctedCount + notCorrectedCount)
            .takeIf { it > 0 }
            ?.let { notCorrectedCount.toDouble() / it }

    fun counts(): List<Int> = listOf(correctedCount, notCorrectedCount, indeterminateCount)
}

private data class CalibrationAuditReport(
    val source: String,
    val noSamples: Boolean,
    val buckets: List<CalibrationBucket>,
    val rejectedCandidateCount: Int,
    val rejectedAboveGateCount: Int,
    val suspectSignals: List<AcceptedLabelMarginSignal>,
    val unjudgeableSignals: List<AcceptedLabelMarginSignal>,
    val notes: List<String>,
) {
    fun render(): String = buildString {
        appendLine("==== K5-B 分类置信度校准审计（代理金标） ====")
        appendLine("数据源：$source（只读）")
        notes.forEach { appendLine("· $it") }
        if (noSamples) {
            appendLine("【无样本】没有任何可审计的自动接受候选——不给出比例（不编造）。")
        } else {
            appendLine("桶                样本  被纠正  未纠正  不可判  代理正确率")
            buckets.forEach { bucket ->
                appendLine(
                    "%-16s %5d %6d %7d %7d  %s".format(
                        bucket.label,
                        bucket.sampleCount,
                        bucket.correctedCount,
                        bucket.notCorrectedCount,
                        bucket.indeterminateCount,
                        bucket.proxyAccuracy?.let { "%.3f".format(it) } ?: "—",
                    ),
                )
            }
            appendLine(
                "选择偏差：被拒候选 ${rejectedCandidateCount} 条（其中过门被挤掉 $rejectedAboveGateCount 条）",
            )
            appendLine("【存疑】极小 margin 的已接受标签（只呈现，不改写）：${suspectSignals.size} 条")
            suspectSignals.forEach { signal ->
                appendLine(
                    "  - ${signal.dimension} ${signal.displayName} " +
                        "confidence=${signal.confidence} margin=${signal.margin}",
                )
            }
            appendLine("【不可判】同维度无对手：${unjudgeableSignals.size} 条（不是「已验证正确」）")
        }
    }

    companion object {
        fun noSamples(source: String, reason: String) = CalibrationAuditReport(
            source = source,
            noSamples = true,
            buckets = emptyList(),
            rejectedCandidateCount = 0,
            rejectedAboveGateCount = 0,
            suspectSignals = emptyList(),
            unjudgeableSignals = emptyList(),
            notes = listOf(
                "【无样本】$reason",
                "金标口径：代理金标 = 用户纠正，不是真金标；未被人纠正 ≠ 正确（add-only 局限）",
            ),
        )
    }
}

/**
 * 真库路径解析：env `SMARTMISTAKE_AUDIT_DATABASE` 优先，其次系统属性
 * `smartmistake.audit.database`；都没有 ⇒ `null`（报告无样本，不猜默认路径——猜错会把
 * 另一台设备的库当成"样本"，比无样本更坏）。
 */
private fun resolveAuditDatabasePath(
    environmentValue: String?,
    propertyValue: String?,
): String? = listOfNotNull(environmentValue, propertyValue)
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)

@Serializable
private data class ExportedSchemaFile(
    @SerialName("database") val database: ExportedSchemaDatabase,
)

@Serializable
private data class ExportedSchemaDatabase(
    @SerialName("version") val version: Int,
    @SerialName("entities") val entities: List<ExportedSchemaEntity> = emptyList(),
)

@Serializable
private data class ExportedSchemaEntity(
    @SerialName("tableName") val tableName: String,
    @SerialName("fields") val fields: List<ExportedSchemaField> = emptyList(),
)

@Serializable
private data class ExportedSchemaField(
    @SerialName("columnName") val columnName: String,
    @SerialName("affinity") val affinity: String,
)
