package com.tingyun.smartmistakebook.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S18 尾批 1 的**手抄 SQL 漂移守卫**（JVM 契约；先例
 * `ProblemDaoQueryCopyContractTest` 与 `ProblemDaoAggregationEquivalenceInstrumentedTest`）。
 *
 * 它消灭的具体失败：[LibraryFtsCountPathInstrumentedTest] 的等价性对照与 EQP 结构门都只能
 * 测**逐字手抄**的 SQL 副本（room3 prepared-statement 路径拒绝 EXPLAIN；`@Query` 是 BINARY
 * retention，运行时取不到原文）。DAO 改了查询、副本没同步时，门会对旧 SQL 报绿——守卫
 * 缺失本身就是"绿得没有意义"。这里把两处源码读出来做**空白归一后的逐字比对**。
 *
 * 副本分两代：
 * - `CROSS_JOIN_*`：改后形态，必须与 DAO 的 `@Query` 逐字一致，且必须带 FTS 最左的
 *   `CROSS JOIN` 驱动前缀；
 * - `PRE_CHANGE_*`：改前（git 59fd8a32）原文，是等价性对照的"旧口径"参照物，必须保持
 *   改前 JOIN 形态（不得含 `CROSS JOIN`），不得随实现漂移。
 */
class LibraryFtsCountPathQueryCopyContractTest {

    @Test
    fun crossJoinSqlCopiesMatchTheDaoQueries() {
        val daoSource = File(DAO_PATH).readText()
        val copySource = File(COPY_PATH).readText()
        DAO_FUNCTION_TO_COPY.forEach { (function, copyProperty) ->
            assertEquals(
                "androidTest 的 $copyProperty 副本与 LibraryFtsSearchDao.$function 的 @Query " +
                    "已漂移：DAO 改动后必须同步更新测试副本，否则等价性/EQP 门测的是旧 SQL",
                normalizeWhitespace(extractDaoQuery(daoSource, function)),
                normalizeWhitespace(extractRawStringLiteral(copySource, copyProperty)),
            )
        }
    }

    @Test
    fun crossJoinCopiesLockTheFtsFirstDriveOrder() {
        val copySource = File(COPY_PATH).readText()
        CROSS_JOIN_COPIES.forEach { property ->
            val sql = normalizeWhitespace(extractRawStringLiteral(copySource, property))
            assertTrue(
                "$property 缺少 FTS 最左的 CROSS JOIN 驱动前缀（驱动修正被改回去了？）",
                FTS_FIRST_DRIVE_PREFIX in sql,
            )
        }
    }

    @Test
    fun preChangeCopiesKeepThePreFixJoinOrder() {
        val copySource = File(COPY_PATH).readText()
        PRE_CHANGE_COPIES.forEach { property ->
            val sql = normalizeWhitespace(extractRawStringLiteral(copySource, property))
            assertFalse(
                "$property 是改前（旧口径）参照物，不得含 CROSS JOIN",
                "CROSS JOIN" in sql,
            )
            assertTrue(
                "$property 不是改前形态：应仍以 library_catalog 视图（或 FTS 表尾随 JOIN）驱动",
                "FROM library_catalog AS catalog" in sql ||
                    "FROM library_search_fts JOIN library_search_content AS content" in sql,
            )
        }
    }

    /**
     * 取 `fun <name>(` 之前**最近**的 `@Query(` 的参数文本：从 `(` 起做括号配平扫描，
     * 跳过 `"..."` 与 `"""..."""` 字面量，把两代字面量里的 SQL 按出现顺序拼接。
     * （DAO 里 countSearch 用原始字符串、三个分面用 `+` 连接的普通字符串。）
     */
    private fun extractDaoQuery(source: String, functionName: String): String {
        val functionIndex = source.indexOf("fun $functionName(")
        assertTrue("LibraryFtsSearchDao 源里找不到 fun $functionName(", functionIndex >= 0)
        val queryIndex = source.lastIndexOf("@Query(", functionIndex)
        assertTrue("@Query( 不在 fun $functionName 之前", queryIndex >= 0)
        val argumentStart = queryIndex + "@Query(".length - 1
        val builder = StringBuilder()
        var depth = 0
        var index = argumentStart
        var inRawString = false
        var inString = false
        while (index < source.length) {
            when {
                inRawString -> {
                    if (source.startsWith("\"\"\"", index)) {
                        inRawString = false
                        index += 3
                        continue
                    }
                    builder.append(source[index])
                }
                inString -> {
                    if (source[index] == '"') {
                        inString = false
                    } else {
                        builder.append(source[index])
                    }
                }
                source.startsWith("\"\"\"", index) -> {
                    inRawString = true
                    index += 3
                    continue
                }
                source[index] == '"' -> inString = true
                source[index] == '(' -> depth++
                source[index] == ')' -> {
                    depth--
                    if (depth == 0) return builder.toString()
                }
            }
            index++
        }
        throw AssertionError("fun $functionName 的 @Query 参数未闭合")
    }

    /** 取 `val <name>: String = """..."""` 的原始字符串内容。 */
    private fun extractRawStringLiteral(source: String, propertyName: String): String {
        val marker = "val $propertyName: String = \"\"\""
        val markerIndex = source.indexOf(marker)
        assertTrue("androidTest 源里找不到 $propertyName", markerIndex >= 0)
        val literalStart = markerIndex + marker.length
        val literalEnd = source.indexOf("\"\"\"", literalStart)
        assertTrue("$propertyName 的原始字符串未闭合", literalEnd > literalStart)
        return source.substring(literalStart, literalEnd)
    }

    private fun normalizeWhitespace(sql: String): String = sql.replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val DAO_PATH =
            "src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/LibraryFtsSearchDao.kt"
        const val COPY_PATH =
            "src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/" +
                "LibraryFtsCountPathInstrumentedTest.kt"

        const val FTS_FIRST_DRIVE_PREFIX =
            "FROM library_search_fts CROSS JOIN library_search_content AS content " +
                "ON content.content_row_id = library_search_fts.docid " +
                "CROSS JOIN library_catalog AS catalog " +
                "ON catalog.problem_revision_id = content.problem_revision_id"

        val DAO_FUNCTION_TO_COPY: Map<String, String> = mapOf(
            "countSearch" to "CROSS_JOIN_COUNT_SEARCH_SQL",
            "searchSubjectFacets" to "CROSS_JOIN_SUBJECT_FACETS_SQL",
            "searchSectionFacets" to "CROSS_JOIN_SECTION_FACETS_SQL",
            "searchMasteryFacets" to "CROSS_JOIN_MASTERY_FACETS_SQL",
        )

        val CROSS_JOIN_COPIES: List<String> = DAO_FUNCTION_TO_COPY.values.toList()

        val PRE_CHANGE_COPIES: List<String> = listOf(
            "PRE_CHANGE_COUNT_SEARCH_SQL",
            "PRE_CHANGE_SUBJECT_FACETS_SQL",
            "PRE_CHANGE_SECTION_FACETS_SQL",
            "PRE_CHANGE_MASTERY_FACETS_SQL",
        )
    }
}
