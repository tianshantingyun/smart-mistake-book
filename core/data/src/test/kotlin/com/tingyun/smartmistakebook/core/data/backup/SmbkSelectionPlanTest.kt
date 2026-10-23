package com.tingyun.smartmistakebook.core.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * **6C-2 schema 69 机器清单的同源契约**。
 *
 * 三份事实互相钉住：
 * 1. `69.json`（Room 导出物）：86 表 / 2 视图 / 92 条 FK 边；
 * 2. 设计 §9.11 的 `<!-- 6c2-plan-v2:begin -->` 机器块（唯一数据源）；
 * 3. [SmbkSelectionPlan]（实施读的那份数据）。
 *
 * 这里断言的是**结构性同源**：每张表恰好归类一次、裁剪顺序是真实 FK 图上的合法拓扑序、
 * 闭包能从 69 的 FK 边折叠出 5 条、软引用 20 条且不误把硬 FK 当软引用、空库全选 digest
 * 逐字命中 golden、非法 mask（含产品裁定拒绝的 0/8）不静默补齐。
 *
 * 本批产品裁定覆盖机器块里仍标 `OPEN` 的两项：mask=8/0 拒绝；派生索引恒裁。
 * 这与 §9.11 `maskPolicy.openMasks` / `derivedPolicy.decision=OPEN` 的**设计期**状态不同，
 * 是批次 18 已确认的裁定，不是测试自行放宽。
 */
class SmbkSelectionPlanTest {

    // ---- 1. schema 69 的事实 ----

    @Test
    fun `schema 69 exports 86 tables 2 views and 92 foreign keys matching the plan identity`() {
        assertEquals("schema 版本", 69, database.int("version"))
        assertEquals(SmbkSelectionPlan.SCHEMA_VERSION, database.int("version"))
        assertEquals(SmbkSelectionPlan.IDENTITY_HASH, database.string("identityHash"))
        assertEquals("Room entity 数", 86, entities.size)
        assertEquals("视图数", 2, schemaViews.size)
        assertEquals("FK 边数", 92, fkEdges.size)

        assertEquals("视图不参与裁剪但清单必须列出", schemaViews, SmbkSelectionPlan.views)
        assertEquals("86 表 = 类别 72 + ALWAYS 7 + DERIVED 2 + SPLIT 5", 86, SmbkSelectionPlan.allTables.size)
    }

    // ---- 2. 每表恰好归类一次 ----

    @Test
    fun `every schema table is classified exactly once and the plan has no ghost tables`() {
        val userTables = SmbkSelectionPlan.categoryTables.values.flatten()
        val classified = userTables + SmbkSelectionPlan.alwaysTables +
            SmbkSelectionPlan.derivedTables + SmbkSelectionPlan.splitTables

        assertEquals("归类总数", 86, classified.size)
        assertEquals("不允许一张表落在两个桶里", classified.size, classified.toSet().size)
        assertEquals("归类集合必须与 69.json 的表集合相等", schemaTables.toSet(), classified.toSet())
        assertEquals("rowCounts 键集合 = 86 表", schemaTables.toSet(), SmbkSelectionPlan.rowCountKeys)
        assertEquals(86, SmbkSelectionPlan.rowCountKeys.size)

        assertEquals(
            mapOf(
                SmbkSelectionCategory.ISSUE_BOOK to 30,
                SmbkSelectionCategory.MASTERY to 35,
                SmbkSelectionCategory.SESSIONS to 6,
                SmbkSelectionCategory.IMAGES to 1,
            ),
            SmbkSelectionPlan.categoryTables.mapValues { it.value.size },
        )
        assertEquals(7, SmbkSelectionPlan.alwaysTables.size)
        assertEquals(2, SmbkSelectionPlan.derivedTables.size)
        assertEquals(5, SmbkSelectionPlan.splitTables.size)

        schemaTables.forEach { table ->
            assertNotNull("$table 必须能查到归属桶", SmbkSelectionPlan.bucketOf(table))
        }
        assertNull("未知表不能被猜成某个桶", SmbkSelectionPlan.bucketOf("no_such_table"))
        assertTrue("视图不是表，不能混进归类", schemaViews.none { it in classified })
        assertEquals(
            "library_search_fts 归在 ISSUE_BOOK（虚拟表由 rebuild 收尾，不在裁剪顺序里）",
            SmbkSelectionCategory.ISSUE_BOOK,
            SmbkSelectionPlan.categoryOf("library_search_fts"),
        )
        assertNull("split 表没有单一用户类别", SmbkSelectionPlan.categoryOf("figure_generation_operation"))
        assertNull("ALWAYS 表没有用户类别", SmbkSelectionPlan.categoryOf("knowledge_node"))
        assertNull("DERIVED 表没有用户类别", SmbkSelectionPlan.categoryOf("knowledge_search_feature"))
    }

    // ---- 3. 裁剪顺序 ----

    @Test
    fun `trim order covers exactly 78 tables and is a child before parent topological order`() {
        val order = SmbkSelectionPlan.trimOrder
        assertEquals("78 = 86 − 7 ALWAYS − 1 FTS", 78, order.size)
        assertEquals("裁剪顺序不允许重复", order.size, order.toSet().size)

        val expectedTables = SmbkSelectionPlan.allTables.toSet() -
            SmbkSelectionPlan.alwaysTables.toSet() - SmbkSelectionPlan.rebuildTables.toSet()
        assertEquals("裁剪顺序必须恰好覆盖全部非 ALWAYS 非 FTS 表", expectedTables, order.toSet())
        assertTrue("ALWAYS 表不能出现在裁剪顺序", order.none { it in SmbkSelectionPlan.alwaysTables })
        assertTrue("FTS 虚拟表不在裁剪顺序，由 rebuild 收尾", order.none { it in SmbkSelectionPlan.rebuildTables })
        SmbkSelectionPlan.derivedTables.forEach { derived ->
            assertTrue("派生索引按本批裁定恒裁：$derived", derived in order)
        }

        val position = order.withIndex().associate { (index, table) -> table to index }
        val violations = fkEdges
            .filter { edge -> edge.child != edge.parent }
            .filter { edge -> edge.child in position && edge.parent in position }
            .filter { edge -> position.getValue(edge.child) >= position.getValue(edge.parent) }
        assertTrue(
            "裁剪顺序必须是子表先于父表的拓扑序；违规边（child→parent）：$violations",
            violations.isEmpty(),
        )
    }

    // ---- 4. 闭包 ----

    @Test
    fun `closure folded from the real fk graph equals the five expected edges`() {
        val folded = fkEdges.mapNotNull { edge ->
            val child = SmbkSelectionPlan.categoryOf(edge.child)
            val parent = SmbkSelectionPlan.categoryOf(edge.parent)
            if (child != null && parent != null && child != parent) SmbkClosureEdge(child, parent) else null
        }.toSet()

        val expected = setOf(
            SmbkClosureEdge(SmbkSelectionCategory.ISSUE_BOOK, SmbkSelectionCategory.IMAGES),
            SmbkClosureEdge(SmbkSelectionCategory.MASTERY, SmbkSelectionCategory.ISSUE_BOOK),
            SmbkClosureEdge(SmbkSelectionCategory.MASTERY, SmbkSelectionCategory.SESSIONS),
            SmbkClosureEdge(SmbkSelectionCategory.SESSIONS, SmbkSelectionCategory.ISSUE_BOOK),
            SmbkClosureEdge(SmbkSelectionCategory.SESSIONS, SmbkSelectionCategory.IMAGES),
        )
        assertEquals("从 69.json FK 边折叠出的类别闭包", expected, folded)
        assertEquals("机器块的 5 条边", expected, SmbkSelectionPlan.expectedClosure.toSet())
        assertEquals(5, folded.size)
    }

    @Test
    fun `mask propagation follows the closure and full mastery means everything`() {
        assertEquals("ISSUE_BOOK ⇒ IMAGES", 9, SmbkSelectionPlan.closureOf(1))
        assertEquals("SESSIONS ⇒ ISSUE_BOOK + IMAGES", 13, SmbkSelectionPlan.closureOf(4))
        assertEquals("MASTERY ⇒ SESSIONS ⇒ ISSUE_BOOK ⇒ IMAGES", 15, SmbkSelectionPlan.closureOf(2))
        assertEquals("0 闭包自洽（但被产品裁定拒绝）", 0, SmbkSelectionPlan.closureOf(0))
        assertEquals("8 闭包自洽（但被产品裁定拒绝）", 8, SmbkSelectionPlan.closureOf(8))
        assertEquals(9, SmbkSelectionPlan.closureOf(9))
        assertEquals(13, SmbkSelectionPlan.closureOf(13))
        assertEquals(15, SmbkSelectionPlan.closureOf(15))

        assertEquals(
            linkedSetOf(SmbkSelectionCategory.ISSUE_BOOK, SmbkSelectionCategory.IMAGES),
            SmbkSelectionPlan.categoriesOf(9),
        )
        assertEquals(9, SmbkSelectionPlan.maskOf(SmbkSelectionPlan.categoriesOf(9)))
        assertEquals(13, SmbkSelectionPlan.maskOf(SmbkSelectionPlan.categoriesOf(13)))
        assertEquals(15, SmbkSelectionPlan.maskOf(SmbkSelectionPlan.categoriesOf(15)))
        assertThrows(IllegalArgumentException::class.java) { SmbkSelectionPlan.closureOf(16) }
        assertThrows(IllegalArgumentException::class.java) { SmbkSelectionPlan.categoriesOf(-1) }
    }

    // ---- 5. 软引用 ----

    @Test
    fun `twenty soft references resolve against schema 69 and never shadow a hard foreign key`() {
        val refs = SmbkSelectionPlan.softReferences
        assertEquals("v2 软引用 20 条", 20, refs.size)
        assertEquals("同一 (table,column) 不能列两次", 20, refs.map { it.table to it.column }.toSet().size)
        assertEquals(
            mapOf(
                "no-new-dangling" to 15,
                "none" to 3,
                "informational" to 1,
                "rebuild-consistency" to 1,
            ),
            refs.groupingBy { it.check }.eachCount(),
        )
        assertEquals(1, refs.count { it.virtual })
        assertEquals("library_search_fts", refs.single { it.virtual }.table)
        assertEquals("docid", refs.single { it.virtual }.column)

        val columnsByTable = entities.associate { it.tableName to it.columns }
        val hardForeignKeyColumns = entities.flatMap { entity ->
            entity.foreignKeys.flatMap { fk -> fk.columns.map { entity.tableName to it } }
        }.toSet()

        refs.forEach { ref ->
            assertTrue("软引用源表不存在于 69.json：${ref.table}", ref.table in schemaTables)
            if (!ref.virtual) {
                assertTrue(
                    "软引用源列不存在于 69.json：${ref.table}.${ref.column}",
                    ref.column in columnsByTable.getValue(ref.table),
                )
                assertFalse(
                    "软引用不能是硬 FK 列（否则应由 FK 图而不是手工清单表达）：${ref.table}.${ref.column}",
                    (ref.table to ref.column) in hardForeignKeyColumns,
                )
            }
            if (!ref.targetTable.startsWith("*")) {
                assertTrue("软引用目标表不存在：${ref.targetTable}", ref.targetTable in schemaTables)
            }
            assertTrue("未知 kind：${ref.kind}", ref.kind in SOFT_REFERENCE_KINDS)
            assertTrue("未知 check：${ref.check}", ref.check in SOFT_REFERENCE_CHECKS)
            assertTrue("未知 strength：${ref.strength}", ref.strength in SOFT_REFERENCE_STRENGTHS)
        }
    }

    // ---- 6. 资产根与 split kind 谓词 ----

    @Test
    fun `asset roots and split kind predicates stay consistent with the classification`() {
        val roots = SmbkSelectionPlan.assetRoots
        assertEquals("四张根引用表", 4, roots.size)
        assertEquals(3, roots.count { it.category == SmbkSelectionCategory.ISSUE_BOOK })
        assertEquals(1, roots.count { it.category == SmbkSelectionCategory.SESSIONS })
        val columnsByTable = entities.associate { it.tableName to it.columns }
        roots.forEach { root ->
            assertTrue("资产根表不存在：${root.table}", root.table in schemaTables)
            assertTrue(
                "资产根列不存在：${root.table}.${root.column}",
                root.column in columnsByTable.getValue(root.table),
            )
            assertEquals("资产根表必须属于它声明的类别", root.category, SmbkSelectionPlan.categoryOf(root.table))
        }

        assertEquals(
            "model_task 家族三表共用同一 kind 谓词",
            setOf("model_task_event", "model_task", "model_task_operation"),
            SmbkSelectionPlan.modelTaskRowFilterTables.toSet(),
        )
        assertEquals(
            "figure 家族两表按行谓词删除，attempt 先于 operation",
            setOf("figure_generation_attempt", "figure_generation_operation"),
            SmbkSelectionPlan.figureRowFilterTables.toSet(),
        )
        assertEquals(
            "两家族的谓词表集合必须覆盖全部 split 表",
            SmbkSelectionPlan.splitTables.toSet(),
            (SmbkSelectionPlan.modelTaskRowFilterTables + SmbkSelectionPlan.figureRowFilterTables).toSet(),
        )

        assertEquals("12 个已知 task_kind", 12, SmbkSelectionPlan.modelTaskKindCategory.size)
        assertEquals("4 个已知 figure source_kind", 4, SmbkSelectionPlan.figureOperationSourceKindCategory.size)
        assertEquals(
            "全选时没有要删的 kind",
            emptySet<String>(),
            SmbkSelectionPlan.deletedModelTaskKinds(SmbkSelectionPlan.FULL_MASK),
        )
        assertEquals(
            "mask=13 只排除 MASTERY 类 kind",
            setOf("REVIEW_RERANK", "KNOWLEDGE_QUIZ"),
            SmbkSelectionPlan.deletedModelTaskKinds(13),
        )
        assertEquals(
            "mask=9 排除 SESSIONS + MASTERY 类 kind",
            setOf(
                "TUTOR_PLAN", "TUTOR_RESPOND", "TUTOR_LOBBY", "TUTOR_EVALUATE", "LEARNING_SUMMARIZE",
                "REVIEW_RERANK", "KNOWLEDGE_QUIZ",
            ),
            SmbkSelectionPlan.deletedModelTaskKinds(9),
        )
        assertEquals(
            "mask=9 只保留 ISSUE_BOOK 类来源",
            setOf("PROBLEM_REVISION", "CLEAN_REDRAW", "CAPTURE_PARSE"),
            SmbkSelectionPlan.figureSourceKindsToKeep(9),
        )
        assertEquals(
            "mask=13 再保留 TUTOR_MESSAGE",
            setOf("TUTOR_MESSAGE", "PROBLEM_REVISION", "CLEAN_REDRAW", "CAPTURE_PARSE"),
            SmbkSelectionPlan.figureSourceKindsToKeep(13),
        )
        assertEquals(
            "全选保留全部已知来源",
            SmbkSelectionPlan.figureOperationSourceKindCategory.keys,
            SmbkSelectionPlan.figureSourceKindsToKeep(15),
        )
    }

    // ---- 7. 设计件 §9.11 机器块同源 ----

    @Test
    fun `plan data matches the section 9_11 machine block field for field`() {
        val block = machineBlock
        assertEquals(SmbkSelectionPlan.CLOSURE_VERSION, block.int("closureVersion"))
        assertEquals(SmbkSelectionPlan.SCHEMA_VERSION, block.int("schemaVersion"))
        assertEquals(SmbkSelectionPlan.IDENTITY_HASH, block.string("identityHash"))

        val blockCategories = block.obj("categories")
        assertEquals(
            "类别名集合",
            SmbkSelectionCategory.entries.map { it.name }.toSet(),
            blockCategories.keys,
        )
        blockCategories.forEach { (name, specElement) ->
            val spec = specElement.jsonObject
            val category = SmbkSelectionCategory.valueOf(name)
            assertEquals("$name bit", category.bit, spec.int("bit"))
            assertEquals("$name label", category.label, spec.string("label"))
            assertEquals("$name 表清单", spec.stringList("tables"), SmbkSelectionPlan.categoryTables.getValue(category))
        }

        assertEquals(block.stringList("alwaysTables"), SmbkSelectionPlan.alwaysTables)
        assertEquals(block.stringList("derivedTables"), SmbkSelectionPlan.derivedTables)
        assertEquals(block.stringList("splitTables"), SmbkSelectionPlan.splitTables)
        assertEquals(block.stringList("rebuildTables"), SmbkSelectionPlan.rebuildTables)
        assertEquals(block.stringList("trimOrder"), SmbkSelectionPlan.trimOrder)

        assertEquals(
            block.obj("modelTaskKindCategory").mapValues {
                SmbkSelectionCategory.valueOf(it.value.jsonPrimitive.content)
            },
            SmbkSelectionPlan.modelTaskKindCategory,
        )
        assertEquals(
            block.obj("figureOperationSourceKindCategory").mapValues {
                SmbkSelectionCategory.valueOf(it.value.jsonPrimitive.content)
            },
            SmbkSelectionPlan.figureOperationSourceKindCategory,
        )

        val blockClosure = block.getValue("expectedClosure").jsonArray.map { edge ->
            SmbkClosureEdge(
                SmbkSelectionCategory.valueOf(edge.jsonArray[0].jsonPrimitive.content),
                SmbkSelectionCategory.valueOf(edge.jsonArray[1].jsonPrimitive.content),
            )
        }
        assertEquals(blockClosure.toSet(), SmbkSelectionPlan.expectedClosure.toSet())

        val blockRoots = block.getValue("assetRoots").jsonArray.map { rootElement ->
            val root = rootElement.jsonObject
            SmbkAssetRoot(
                table = root.string("table"),
                column = root.string("column"),
                category = SmbkSelectionCategory.valueOf(root.string("category")),
            )
        }
        assertEquals(blockRoots, SmbkSelectionPlan.assetRoots)

        val blockSoftReferences = block.getValue("softReferences").jsonArray.map { it.jsonObject.toSoftReferenceView() }
        val planSoftReferences = SmbkSelectionPlan.softReferences.map { it.toSoftReferenceView() }
        assertEquals(blockSoftReferences, planSoftReferences)

        assertEquals(
            "机器块的 closedMasks 就是本批可创建集合",
            block.obj("maskPolicy").getValue("closedMasks").jsonArray.map { it.jsonPrimitive.int }.toSet(),
            SmbkSelectionPlan.allowedMasks,
        )
        val openMasks = block.obj("maskPolicy").getValue("openMasks").jsonArray
            .map { it.jsonObject.int("mask") }
            .toSet()
        assertEquals("设计件里仍标 OPEN 的是 0/8", setOf(0, 8), openMasks)
        assertTrue(
            "批次 18 产品裁定：0/8 一律拒绝（覆盖设计期 OPEN，不静默补齐）",
            openMasks.none { SmbkSelectionPlan.isCreatableMask(it) },
        )

        assertEquals(
            "FTS rebuild 只有 library_search_fts",
            listOf("library_search_fts"),
            SmbkSelectionPlan.rebuildTables,
        )
    }

    // ---- 8. digest ----

    @Test
    fun `empty full selection digest matches the published golden`() {
        val rowCounts = SmbkSelectionPlan.rowCountKeys.associateWith { 0L }
        assertEquals("rowCounts 必须覆盖 86 表", 86, rowCounts.size)

        val digest = SmbkSelectionPlan.closureDigest(
            categories = SmbkSelectionCategory.entries.toSet(),
            assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
            rowCounts = rowCounts,
        )
        assertEquals(GOLDEN_EMPTY_FULL_DIGEST, digest)
        assertTrue("必须是 64 位小写 hex：$digest", digest.matches(Regex("[0-9a-f]{64}")))

        val reversed = rowCounts.entries.reversed().associate { it.key to it.value }
        assertEquals(
            "digest 只依赖排序后的键，不依赖 Map 迭代顺序",
            digest,
            SmbkSelectionPlan.closureDigest(
                categories = SmbkSelectionCategory.entries.toList().reversed(),
                assetPolicy = SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                rowCounts = reversed,
            ),
        )
    }

    @Test
    fun `digest refuses row counts that do not cover all 86 tables`() {
        val complete = SmbkSelectionPlan.rowCountKeys.associateWith { 0L }

        val missing = assertThrows(IllegalArgumentException::class.java) {
            SmbkSelectionPlan.closureDigest(
                SmbkSelectionCategory.entries.toSet(),
                SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                complete - "problem",
            )
        }
        assertTrue("缺键必须报出缺了哪张表：${missing.message}", missing.message!!.contains("missing=[problem]"))

        val extra = assertThrows(IllegalArgumentException::class.java) {
            SmbkSelectionPlan.closureDigest(
                SmbkSelectionCategory.entries.toSet(),
                SmbkSelectionPlan.ASSET_POLICY_INCLUDED,
                complete + ("not_a_table" to 0L),
            )
        }
        assertTrue("多键必须报出多的是哪张表：${extra.message}", extra.message!!.contains("extra=[not_a_table]"))
    }

    // ---- 9. mask 白名单 ----

    @Test
    fun `only masks 9 13 and 15 are creatable and every other mask is rejected`() {
        assertEquals(setOf(9, 13, 15), SmbkSelectionPlan.allowedMasks)
        assertEquals(
            "拒绝集合是 0 到 15 的补集",
            setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 14),
            SmbkSelectionPlan.rejectedMasks,
        )

        (0..SmbkSelectionPlan.FULL_MASK).forEach { mask ->
            val creatable = mask == 9 || mask == 13 || mask == 15
            assertEquals("mask=$mask 的创建合法性", creatable, SmbkSelectionPlan.isCreatableMask(mask))
            if (creatable) {
                assertEquals(mask, SmbkSelectionPlan.requireCreatableMask(mask))
            } else {
                val error = assertThrows("mask=$mask 必须拒绝", IllegalArgumentException::class.java) {
                    SmbkSelectionPlan.requireCreatableMask(mask)
                }
                assertTrue(
                    "拒绝信息必须报出 mask 与允许集合：${error.message}",
                    error.message!!.contains("mask=$mask") &&
                        error.message!!.contains(SmbkSelectionPlan.allowedMasks.joinToString("/")),
                )
            }
        }

        assertTrue("0 闭包自洽", SmbkSelectionPlan.isClosureConsistent(0))
        assertTrue("8 闭包自洽", SmbkSelectionPlan.isClosureConsistent(8))
        assertFalse("0 被产品裁定拒绝", SmbkSelectionPlan.isCreatableMask(0))
        assertFalse("8 被产品裁定拒绝（不提供仅图片）", SmbkSelectionPlan.isCreatableMask(8))

        listOf(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 14).forEach { mask ->
            assertFalse("mask=$mask 缺父类别，闭包不自洽", SmbkSelectionPlan.isClosureConsistent(mask))
        }
    }

    // ---- 夹具 ----

    private fun SmbkSoftReference.toSoftReferenceView() = SoftReferenceView(
        table = table,
        column = column,
        targetTable = targetTable,
        kind = kind,
        check = check,
        strength = strength,
        checkDetail = checkDetail,
        virtual = virtual,
    )

    private fun JsonObject.toSoftReferenceView() = SoftReferenceView(
        table = string("table"),
        column = string("column"),
        targetTable = string("targetTable"),
        kind = string("kind"),
        check = string("check"),
        strength = string("strength"),
        checkDetail = this["checkDetail"]?.let { it.jsonPrimitive.contentOrNull },
        virtual = this["virtual"]?.let { it.jsonPrimitive.booleanOrNull } ?: false,
    )

    private data class SoftReferenceView(
        val table: String,
        val column: String,
        val targetTable: String,
        val kind: String,
        val check: String,
        val strength: String,
        val checkDetail: String?,
        val virtual: Boolean,
    )

    private data class SchemaFk(
        val parent: String,
        val columns: List<String>,
    )

    private data class SchemaEntity(
        val tableName: String,
        val columns: Set<String>,
        val foreignKeys: List<SchemaFk>,
    )

    private data class FkEdge(val child: String, val parent: String)

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.stringList(key: String): List<String> =
        getValue(key).jsonArray.map { it.jsonPrimitive.content }

    /**
     * 测试工作目录可能在模块目录（Gradle 单测）或仓库根（IDE）。从当前目录向上找
     * `settings.gradle.kts` 定位仓库根，再解析**仓库内相对路径**：
     * 逐段拒绝上跳段，并用 `normalize` 后校验仍在仓库根之下。
     */
    private fun repositoryRoot(): Path {
        val workingDirectory = Paths.get("").toAbsolutePath().normalize()
        var cursor: Path? = workingDirectory
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve(GRADLE_ROOT_MARKER))) return cursor
            cursor = cursor.parent
        }
        error("找不到 Gradle 根（从 $workingDirectory 向上，标记 $GRADLE_ROOT_MARKER 未命中）")
    }

    private fun repoFile(relativePath: String): Path {
        val segments = relativePath.split('/')
        require(segments.none { it.isEmpty() || it == PARENT_SEGMENT }) {
            "仓库内相对路径不合法（不允许空段或上跳段）：$relativePath"
        }
        val root = repositoryRoot()
        val target = root.resolve(relativePath).normalize()
        require(target.startsWith(root)) { "解析出的路径越出仓库根：$target 不在 $root 之下" }
        require(Files.isRegularFile(target)) { "缺少测试夹具：$target" }
        return target
    }

    private val schemaDatabase: JsonObject by lazy {
        Json.parseToJsonElement(Files.readString(repoFile(SCHEMA_69_PATH)))
            .jsonObject.getValue("database").jsonObject
    }

    private val database: JsonObject get() = schemaDatabase

    private val entities: List<SchemaEntity> by lazy {
        database.getValue("entities").jsonArray.map { element ->
            val entity = element.jsonObject
            SchemaEntity(
                tableName = entity.string("tableName"),
                columns = entity.getValue("fields").jsonArray
                    .map { it.jsonObject.string("columnName") }
                    .toSet(),
                foreignKeys = entity["foreignKeys"]?.jsonArray.orEmpty().map { fkElement ->
                    val fk = fkElement.jsonObject
                    SchemaFk(
                        parent = fk.string("table"),
                        columns = fk.stringList("columns"),
                    )
                },
            )
        }
    }

    private val schemaTables: List<String> by lazy { entities.map { it.tableName } }

    private val schemaViews: List<String> by lazy {
        database.getValue("views").jsonArray.map { it.jsonObject.string("viewName") }
    }

    private val fkEdges: List<FkEdge> by lazy {
        entities.flatMap { entity -> entity.foreignKeys.map { FkEdge(entity.tableName, it.parent) } }
    }

    private val machineBlock: JsonObject by lazy {
        val text = Files.readString(repoFile(DESIGN_PATH))
        val match = MACHINE_BLOCK.findAll(text).singleOrNull()
            ?: error("设计件里必须恰有一个 ${MACHINE_BLOCK.pattern} 块")
        Json.parseToJsonElement(match.groupValues[1]).jsonObject
    }

    private companion object {
        const val GRADLE_ROOT_MARKER = "settings.gradle.kts"
        const val PARENT_SEGMENT = "." + "."
        const val SCHEMA_69_PATH =
            "core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json"
        const val DESIGN_PATH = "docs/research/2026-10-17-6c2-content-selection-design.md"

        /** §9.7 空库、全选、assetPolicy=INCLUDED 的 golden。 */
        const val GOLDEN_EMPTY_FULL_DIGEST =
            "d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6"

        val MACHINE_BLOCK = Regex(
            "<!-- 6c2-plan-v2:begin -->\\s*```json\\s*(\\{.*?})\\s*```\\s*<!-- 6c2-plan-v2:end -->",
            RegexOption.DOT_MATCHES_ALL,
        )

        val SOFT_REFERENCE_KINDS = setOf(
            "by-kind", "cross-category", "same-category", "split-family", "always",
        )
        val SOFT_REFERENCE_CHECKS = setOf(
            "no-new-dangling", "informational", "rebuild-consistency", "none",
        )
        val SOFT_REFERENCE_STRENGTHS = setOf("strong", "weak-host-hint", "informational")
    }
}
