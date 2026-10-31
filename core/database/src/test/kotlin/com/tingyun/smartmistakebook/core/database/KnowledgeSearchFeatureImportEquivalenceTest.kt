package com.tingyun.smartmistakebook.core.database

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import com.tingyun.smartmistakebook.core.database.entity.KnowledgeSearchFeatureEntity
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **F6 JVM 等价门**（性能剖析 2026-10-31）：把「导入后 DB 的检索结果」在真 SQLite 上
 * 逐条对拍。三条臂共用同一份语料（两个随包 JSON 的**全部**节点 + 生产
 * `KnowledgeSearchFeatureExtractor` 抽出的全部特征行）：
 *
 * | 臂 | 写入形态 | pragma | 二级索引 |
 * |---|---|---|---|
 * | A（改前） | 逐行 `INSERT OR IGNORE ... VALUES (?,?,?)` | 默认 | 全程在 |
 * | B（批量 VALUES） | 生产 `insertKnowledgeSearchFeaturesBatched` | 默认 | 全程在 |
 * | C（导入调优 + 索引窗口） | 生产批量写入 | `synchronous=OFF/cache_size` | 写入期移除，写后补建 |
 *
 * 断言（不是质量阈值，是**等价性**）：
 * 1. 三臂的特征表内容指纹逐字节相同（按主键排序后三列 + 分隔符 → SHA-256），行数相同；
 * 2. 冻结金标集 `golden_queries_v2.json` 的**每一条**查询，三臂返回的节点序列逐条相同
 *    （`ProblemOrganizationDao.searchSubjectKnowledgeRecallCandidates` 的 JVM 镜像，
 *    limit=512；`IN` 列表固定 128 个 `?` 占位符，不足 128 时用最后一个特征重复填充——
 *    重复值不改变集合语义，也不影响 `COUNT(DISTINCT ...)`）；
 * 3. C 在「二级索引被移除」窗口内、以及补建索引之后的两次查询结果都与 A 相同；
 * 4. `FEATURE_INDEX_ENSURE_DDL` 与 Room 当前 schema 的索引定义同义（索引名、列序与
 *    `CREATE INDEX IF NOT EXISTS` 全部对齐；索引后置不会把 schema 留在别的形状）；
 * 5. `PRAGMA integrity_check` 三臂均为 `ok`。
 *
 * 未被本测试覆盖的边界（如实登记）：材料表（`knowledge_teaching_material`）与
 * `library_search_fts` 不在本轮改动路径上（改动只碰特征表写入形态、会话 pragma 与特征表
 * 二级索引），本测试不重跑整条 Android 导入。设备侧全量导入不在本门范围内。
 */
class KnowledgeSearchFeatureImportEquivalenceTest {

    @Test
    fun batchedImportWithTuningAndDeferredIndexesMatchesThePerRowImport() {
        val repoRoot = locateRepoRoot()
            ?: error("找不到仓库根（settings.gradle.kts）")
        val nodes = loadPackNodes(repoRoot)
        val features = nodes.flatMap { node ->
            KnowledgeSearchFeatureExtractor.fromNode(node).map { feature ->
                KnowledgeSearchFeatureEntity(node.subject, feature, node.knowledgeNodeId)
            }
        }
        // 语料规模下界：v23 四科包 546,839 行（2020 九科再加约 1.7k）。低于这个数说明
        // 解析/抽取链断了，等价性就无从谈起。
        assertTrue("特征行只有 ${features.size} 行，远低于随包规模", features.size > 500_000)

        val cases = loadGoldenCases(repoRoot)
        assertTrue("金标集条数异常：${cases.size}", cases.size in 120..140)

        assertFeatureIndexesMatchRoomSchema(repoRoot)

        val workDir = Files.createTempDirectory("kb-f6-equivalence").toFile()
        try {
            val perRow = openHarnessDatabase(File(workDir, "per-row.db"), tuning = false)
            val batched = openHarnessDatabase(File(workDir, "batched.db"), tuning = false)
            val deferred = openHarnessDatabase(File(workDir, "deferred.db"), tuning = true)
            try {
                insertNodes(perRow, nodes)
                insertNodes(batched, nodes)
                insertNodes(deferred, nodes)

                insertPerRow(perRow, features)
                runBlocking {
                    JdbcPooledConnection(batched).insertKnowledgeSearchFeaturesBatched(features)
                }

                // C：模拟导入窗口——移除两条二级索引，写入后再补建。
                dropFeatureIndexes(deferred)
                runBlocking {
                    JdbcPooledConnection(deferred).insertKnowledgeSearchFeaturesBatched(features)
                }

                // 1) 特征行逐字节等价（内容指纹 + 行数）。
                val perRowDigest = featureDigest(perRow)
                println(
                    "f6-equivalence: nodes=${nodes.size} features=${features.size} " +
                        "queries=${cases.size} featureDigest=$perRowDigest",
                )
                assertEquals(perRowDigest, featureDigest(batched))
                assertEquals(perRowDigest, featureDigest(deferred))
                assertEquals(featureCount(perRow), featureCount(batched))
                assertEquals(featureCount(perRow), featureCount(deferred))

                // 2) 金标集每条查询的命中序列逐条相同（B 对 A）。
                val perRowHits = runGoldenQueries(perRow, cases)
                assertEquals(perRowHits, runGoldenQueries(batched, cases))

                // 3) C 在索引窗口内、补建之后都与 A 相同。
                assertEquals(perRowHits, runGoldenQueries(deferred, cases))
                ensureFeatureIndexes(deferred)
                assertEquals(perRowHits, runGoldenQueries(deferred, cases))
                assertEquals(
                    setOf(
                        "index_knowledge_search_feature_knowledge_node_id",
                        "index_knowledge_search_feature_subject_knowledge_node_id",
                    ),
                    deferredFeatureIndexes(deferred),
                )

                // 4) 三臂完整性。
                assertEquals("ok", integrityCheck(perRow))
                assertEquals("ok", integrityCheck(batched))
                assertEquals("ok", integrityCheck(deferred))
            } finally {
                perRow.close()
                batched.close()
                deferred.close()
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // 语料：直接读随包 JSON（与 `BundledKnowledgePackResources` 同一份文件、同一
    // `taxonomyVersion`/`subjectKey`/节点 id 规则），抽取走生产提取器。
    // ------------------------------------------------------------------

    private fun loadPackNodes(repoRoot: File): List<KnowledgeNodeSeedRecord> {
        val resources = File(repoRoot, "core/data/src/main/resources/knowledge")
        return listOf("moe-2020-foundation-v1.json", "moe-2025-four-subjects-v1.json").flatMap { name ->
            val root = Json.parseToJsonElement(File(resources, name).readText()).jsonObject
            val taxonomyVersion = root.getValue("taxonomyVersion").jsonPrimitive.content
            val reviewedAt = root.getValue("reviewedAtEpochMillis").jsonPrimitive.long
            root.getValue("subjects").jsonArray.flatMap { subjectElement ->
                val subject = subjectElement.jsonObject
                val subjectName = subject.getValue("subject").jsonPrimitive.content
                val subjectKey = subjectName.lowercase(Locale.ROOT)
                val topics: List<JsonObject> = subject["topics"]?.jsonArray?.map { it.jsonObject }
                    // 2020 包是「一科一主题」的扁平形态：topicSlug/topicName/knowledgePoints。
                    ?: singleFlatTopic(subject)
                topics.flatMap { topic ->
                    val topicSlug = topic.getValue("slug").jsonPrimitive.content
                    val topicName = topic.getValue("name").jsonPrimitive.content
                    val topicNode = KnowledgeNodeSeedRecord(
                        knowledgeNodeId = "kb:$taxonomyVersion:$subjectKey:topic:$topicSlug",
                        stableCode = "$taxonomyVersion:${subjectKey}:topic:$topicSlug",
                        subject = subjectName,
                        displayName = topicName,
                        parentKnowledgeNodeId = null,
                        taxonomyVersion = taxonomyVersion,
                        createdAtEpochMillis = reviewedAt,
                        canonicalName = topicName,
                        nodeKind = "TOPIC",
                        granularity = "TOPIC",
                        verificationStatus = "CURATED",
                    )
                    val points = topic["knowledgePoints"]?.jsonArray.orEmpty().map { pointElement ->
                        val point = pointElement.jsonObject
                        val slug = point.getValue("slug").jsonPrimitive.content
                        val pointName = point.getValue("name").jsonPrimitive.content
                        KnowledgeNodeSeedRecord(
                            knowledgeNodeId = "kb:$taxonomyVersion:$subjectKey:atomic:$slug",
                            stableCode = "$taxonomyVersion:${subjectKey}:atomic:$slug",
                            subject = subjectName,
                            displayName = pointName,
                            parentKnowledgeNodeId = topicNode.knowledgeNodeId,
                            taxonomyVersion = taxonomyVersion,
                            createdAtEpochMillis = reviewedAt,
                            canonicalName = pointName,
                            nodeKind = point["kind"]?.jsonPrimitive?.contentOrNull ?: "CONCEPT",
                            granularity = "ATOMIC",
                            aliases = point["aliases"]?.jsonArray.orEmpty()
                                .map { it.jsonPrimitive.content }
                                .toSet(),
                            boundaryMarkdown = point["boundary"]?.jsonPrimitive?.contentOrNull,
                            verificationStatus = "SOURCE_GROUNDED",
                        )
                    }
                    listOf(topicNode) + points
                }
            }
        }
    }

    private fun singleFlatTopic(subject: JsonObject): List<JsonObject> = listOf(
        JsonObject(
            mapOf(
                "slug" to subject.getValue("topicSlug"),
                "name" to subject.getValue("topicName"),
                "knowledgePoints" to subject.getValue("knowledgePoints"),
            ),
        ),
    )

    private data class GoldenCase(val subject: String, val query: String)

    private fun loadGoldenCases(repoRoot: File): List<GoldenCase> {
        val file = File(repoRoot, "tools/kb_coverage/tables/golden_queries_v2.json")
        return Json.parseToJsonElement(file.readText()).jsonArray.map { element ->
            val value = element.jsonObject
            GoldenCase(
                subject = value.getValue("subject").jsonPrimitive.content,
                query = value.getValue("query").jsonPrimitive.content,
            )
        }
    }

    // ------------------------------------------------------------------
    // 真 SQLite 夹具（sqlite-jdbc）。A/B 用默认 pragma；C 复刻导入调优值。
    // ------------------------------------------------------------------

    private fun openHarnessDatabase(file: File, tuning: Boolean): Connection {
        val connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        // 先以 autoCommit=true 建库：`PRAGMA synchronous` 不允许在事务内修改
        // （sqlite-jdbc 在 autoCommit=false 时执行第一条语句就会起事务）。
        connection.createStatement().use { it.execute("PRAGMA journal_mode = OFF") }
        connection.createStatement().use { it.execute("PRAGMA synchronous = OFF") }
        if (tuning) {
            connection.createStatement().use { it.execute("PRAGMA cache_size = -32768") }
        } else {
            connection.createStatement().use { it.execute("PRAGMA cache_size = -2000") }
        }
        createSchema(connection)
        if (!tuning) {
            ensureFeatureIndexes(connection)
        }
        connection.autoCommit = false
        return connection
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate("CREATE TABLE `knowledge_node` (`knowledge_node_id` TEXT NOT NULL, `subject` TEXT NOT NULL, `canonical_name` TEXT NOT NULL, `granularity` TEXT NOT NULL, `verification_status` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`knowledge_node_id`))")
        }
        connection.createStatement().use {
            it.executeUpdate("CREATE TABLE `knowledge_search_feature` (`subject` TEXT NOT NULL, `search_feature` TEXT NOT NULL, `knowledge_node_id` TEXT NOT NULL, PRIMARY KEY(`subject`, `search_feature`, `knowledge_node_id`), FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }

    /** 按 Room schema 的原 DDL 补建两条二级索引（与生产 `FEATURE_INDEX_ENSURE_DDL` 同义）。 */
    private fun ensureFeatureIndexes(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate("CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_knowledge_node_id` ON `knowledge_search_feature` (`knowledge_node_id`)")
        }
        connection.createStatement().use {
            it.executeUpdate("CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_subject_knowledge_node_id` ON `knowledge_search_feature` (`subject`, `knowledge_node_id`)")
        }
    }

    /** 移除两条二级索引（C 臂的「索引后置」窗口）。 */
    private fun dropFeatureIndexes(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate("DROP INDEX IF EXISTS `index_knowledge_search_feature_knowledge_node_id`")
        }
        connection.createStatement().use {
            it.executeUpdate("DROP INDEX IF EXISTS `index_knowledge_search_feature_subject_knowledge_node_id`")
        }
    }

    private fun insertNodes(connection: Connection, nodes: List<KnowledgeNodeSeedRecord>) {
        connection.prepareStatement("INSERT INTO `knowledge_node` (`knowledge_node_id`, `subject`, `canonical_name`, `granularity`, `verification_status`, `status`) VALUES (?,?,?,?,?,?)").use { statement ->
            for (node in nodes) {
                statement.setString(1, node.knowledgeNodeId)
                statement.setString(2, node.subject)
                statement.setString(3, node.canonicalName)
                statement.setString(4, node.granularity)
                statement.setString(5, node.verificationStatus)
                statement.setString(6, "ACTIVE")
                statement.executeUpdate()
            }
        }
        connection.commit()
    }

    /** 改前形态：room3 `@Insert` 的逐行 `INSERT OR IGNORE ... VALUES (?,?,?)`。 */
    private fun insertPerRow(connection: Connection, features: List<KnowledgeSearchFeatureEntity>) {
        connection.prepareStatement("INSERT OR IGNORE INTO `knowledge_search_feature` (`subject`, `search_feature`, `knowledge_node_id`) VALUES (?,?,?)").use { statement ->
            for (feature in features) {
                statement.setString(1, feature.subject)
                statement.setString(2, feature.searchFeature)
                statement.setString(3, feature.knowledgeNodeId)
                statement.executeUpdate()
            }
        }
        connection.commit()
    }

    // ------------------------------------------------------------------
    // 检索 SQL：`ProblemOrganizationDao.searchSubjectKnowledgeRecallCandidates` 的镜像。
    // ------------------------------------------------------------------

    private fun runGoldenQueries(
        connection: Connection,
        cases: List<GoldenCase>,
    ): List<List<String>> = cases.map { case ->
        val searchFeatures = KnowledgeSearchFeatureExtractor.fromQuestion(case.query).toList()
        if (searchFeatures.isEmpty()) {
            emptyList()
        } else {
            require(searchFeatures.size <= MAX_QUERY_FEATURES) {
                "查询特征数 ${searchFeatures.size} 超过生产上界 $MAX_QUERY_FEATURES"
            }
            // 固定 128 个占位符；不足时用最后一个特征重复填充。IN 的重复值不改变集合语义，
            // COUNT(DISTINCT feature.search_feature) 也不受影响——与生产查询逐义相同。
            val bound = if (searchFeatures.size == MAX_QUERY_FEATURES) {
                searchFeatures
            } else {
                searchFeatures + List(MAX_QUERY_FEATURES - searchFeatures.size) {
                    searchFeatures.last()
                }
            }
            connection.prepareStatement("SELECT node.`knowledge_node_id` FROM `knowledge_search_feature` AS feature INNER JOIN `knowledge_node` AS node ON node.`knowledge_node_id` = feature.`knowledge_node_id` WHERE feature.`subject` = ? AND feature.`search_feature` IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) AND node.`verification_status` IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED') AND node.`status` != 'RETIRED' GROUP BY node.`knowledge_node_id` ORDER BY COUNT(DISTINCT feature.`search_feature`) DESC, CASE node.`granularity` WHEN 'ATOMIC' THEN 0 ELSE 1 END, node.`canonical_name` ASC, node.`knowledge_node_id` ASC LIMIT 512").use { statement ->
                statement.setString(1, case.subject)
                bound.forEachIndexed { index, feature ->
                    statement.setString(index + 2, feature)
                }
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getString(1))
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 等价性度量
    // ------------------------------------------------------------------

    private fun featureCount(connection: Connection): Long =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM `knowledge_search_feature`").use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }

    /** 按主键排序后，把三列用分隔符串起来做 SHA-256（逐行内容指纹）。 */
    private fun featureDigest(connection: Connection): String {
        val digest = MessageDigest.getInstance("SHA-256")
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT `subject`, `search_feature`, `knowledge_node_id` FROM `knowledge_search_feature` ORDER BY `subject`, `search_feature`, `knowledge_node_id`").use { rows ->
                while (rows.next()) {
                    digest.update(rows.getString(1).toByteArray(Charsets.UTF_8))
                    digest.update(0x1F)
                    digest.update(rows.getString(2).toByteArray(Charsets.UTF_8))
                    digest.update(0x1F)
                    digest.update(rows.getString(3).toByteArray(Charsets.UTF_8))
                    digest.update(0x1E)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deferredFeatureIndexes(connection: Connection): Set<String> =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA index_list(`knowledge_search_feature`)").use { rows ->
                buildSet {
                    while (rows.next()) {
                        val name = rows.getString(2)
                        if (name.startsWith("index_knowledge_search_feature_")) {
                            add(name)
                        }
                    }
                }
            }
        }

    private fun integrityCheck(connection: Connection): String? =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA integrity_check").use { rows ->
                if (rows.next()) rows.getString(1) else null
            }
        }

    /**
     * `FEATURE_INDEX_ENSURE_DDL` 与 Room 当前 schema 的索引定义必须同义：索引名一致，
     * 每条 DDL 都带原表的 `CREATE INDEX IF NOT EXISTS` 前缀，列名按 schema 的列序出现。
     * 不构造/改写 SQL 文本，只做结构比对。
     */
    private fun assertFeatureIndexesMatchRoomSchema(repoRoot: File) {
        val schemaDir = File(
            repoRoot,
            "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase",
        )
        val currentSchema = schemaDir.listFiles { file -> file.extension == "json" }
            ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
            ?: error("找不到 Room schema JSON：$schemaDir")
        val entities = Json.parseToJsonElement(currentSchema.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray
        val featureTable = entities.map { it.jsonObject }
            .single { it.getValue("tableName").jsonPrimitive.content == "knowledge_search_feature" }
        val indices = featureTable.getValue("indices").jsonArray.map { it.jsonObject }
        assertEquals(
            "特征表索引名与 Room schema ${currentSchema.name} 不一致",
            indices.map { it.getValue("name").jsonPrimitive.content }.toSet(),
            setOf(
                "index_knowledge_search_feature_knowledge_node_id",
                "index_knowledge_search_feature_subject_knowledge_node_id",
            ),
        )
        indices.forEach { index ->
            val name = index.getValue("name").jsonPrimitive.content
            val columns = index.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content }
            val ddl = FEATURE_INDEX_ENSURE_DDL.single { it.contains(name) }
            assertTrue("$name 不是 `CREATE INDEX IF NOT EXISTS`", ddl.contains("CREATE INDEX IF NOT EXISTS"))
            assertTrue("$name 未指向 knowledge_search_feature", ddl.contains("`knowledge_search_feature`"))
            var previousPosition = -1
            columns.forEach { column ->
                val position = ddl.indexOf("`$column`")
                assertTrue("$name 缺列 $column 或列序与 schema 不一致", position > previousPosition)
                previousPosition = position
            }
        }
    }

    /** 从工作目录逐级上找仓库根（与 `GoldenRetrievalJvmTest` 同一取法）。 */
    private fun locateRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return dir
    }

    private companion object {
        const val MAX_QUERY_FEATURES = KnowledgeSearchFeatureExtractor.MAX_QUERY_FEATURES
    }
}

/**
 * 把 JDBC 连接包装成 room3 的 [PooledConnection]，让等价门直接执行**生产**的批量写入
 * 循环（而不是测试里另抄一份绑定逻辑）。只实现批量 INSERT 用得到的成员；其余显式失败，
 * 避免静默走错语义。
 */
private class JdbcPooledConnection(
    private val connection: Connection,
) : PooledConnection {
    override suspend fun <R> usePrepared(
        query: String,
        block: suspend (SQLiteStatement) -> R,
    ): R = connection.prepareStatement(query).use { statement ->
        block(JdbcSQLiteStatement(statement))
    }
}

private class JdbcSQLiteStatement(
    private val statement: PreparedStatement,
) : SQLiteStatement {
    override fun bindBlob(index: Int, value: ByteArray) = statement.setBytes(index, value)
    override fun bindDouble(index: Int, value: Double) = statement.setDouble(index, value)
    override fun bindLong(index: Int, value: Long) = statement.setLong(index, value)
    override fun bindText(index: Int, value: String) = statement.setString(index, value)
    override fun bindNull(index: Int) = statement.setNull(index, Types.NULL)
    override fun getBlob(index: Int): ByteArray = error("read path is not used by the batched INSERT")
    override fun getDouble(index: Int): Double = error("read path is not used by the batched INSERT")
    override fun getLong(index: Int): Long = error("read path is not used by the batched INSERT")
    override fun getText(index: Int): String = error("read path is not used by the batched INSERT")
    override fun isNull(index: Int): Boolean = error("read path is not used by the batched INSERT")
    override fun getColumnCount(): Int = error("read path is not used by the batched INSERT")
    override fun getColumnName(index: Int): String = error("read path is not used by the batched INSERT")
    override fun getColumnType(index: Int): Int = error("read path is not used by the batched INSERT")
    override fun step(): Boolean = statement.execute()
    override fun reset() = statement.clearParameters()
    override fun clearBindings() = statement.clearParameters()
    override fun close() = statement.close()
}
