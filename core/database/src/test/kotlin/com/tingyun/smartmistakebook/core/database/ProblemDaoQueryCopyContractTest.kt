package com.tingyun.smartmistakebook.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S17 结构门的**手抄 SQL 漂移守卫**（JVM 契约，复核修复 2026-10-05）。
 *
 * 它消灭的具体失败：`ProblemDaoActiveMistakesPerformanceInstrumentedTest` 的 EXPLAIN 门
 * 只能测一份**逐字手抄**的 SQL 副本（room3 prepared-statement 路径拒绝 EXPLAIN；
 * `@Query` 是 BINARY retention，运行时取不到原文）。DAO 改了查询、副本没同步时，
 * 门会对旧 SQL 报绿——守卫缺失本身就是"绿得没有意义"。这里把两处源码读出来做
 * **空白归一后的逐字比对**，不一致就红并提示"更新测试副本"。
 *
 * 先例：`ExportedSchemaContractTest` 以 `File("schemas")` 读工作目录（Gradle 测试的
 * 工作目录 = 模块目录 `core/database`）。
 */
class ProblemDaoQueryCopyContractTest {

    @Test
    fun observeActiveMistakesSqlCopyMatchesTheDaoQuery() {
        val daoSource = File(DAO_PATH).readText()
        val copySource = File(COPY_PATH).readText()

        val authoritative = extractDaoQuery(daoSource, "observeActiveMistakes")
        val copy = extractAndroidTestRawSqlCopy(copySource, "ACTIVE_MISTAKES_SQL")

        assertEquals(
            "androidTest 的 ACTIVE_MISTAKES_SQL 副本与 ProblemDao.observeActiveMistakes 的 " +
                "@Query 已漂移：DAO 改动后必须同步更新测试副本，否则 EQP 门测的是旧 SQL",
            normalizeWhitespace(authoritative),
            normalizeWhitespace(copy),
        )
    }

    /**
     * 取 `fun <name>(` 之前**最近**的 `@Query(` 的第一个原始字符串字面量。
     * 注解与函数之间隔着 KDoc（见 `ProblemDao.observeActiveMistakes` 的实际排布），
     * 所以按"从函数声明往回找最近的 @Query"而不是按相邻行解析。
     */
    private fun extractDaoQuery(source: String, functionName: String): String {
        val functionIndex = source.indexOf("fun $functionName(")
        assertTrue("ProblemDao 源里找不到 fun $functionName(", functionIndex >= 0)
        val queryIndex = source.lastIndexOf("@Query(", functionIndex)
        assertTrue("@Query( 不在 fun $functionName 之前", queryIndex >= 0)
        val literalStart = source.indexOf("\"\"\"", queryIndex)
        assertTrue(
            "fun $functionName 的 @Query 缺少原始字符串字面量",
            literalStart in (queryIndex + 1) until functionIndex,
        )
        val literalEnd = source.indexOf("\"\"\"", literalStart + 3)
        assertTrue("fun $functionName 的 @Query 原始字符串未闭合", literalEnd > literalStart)
        return source.substring(literalStart + 3, literalEnd)
    }

    /** 取 `val <name>: String = """..."""` 的原始字符串字面量内容。 */
    private fun extractAndroidTestRawSqlCopy(source: String, propertyName: String): String {
        val marker = "val $propertyName: String = \"\"\""
        val markerIndex = source.indexOf(marker)
        assertTrue("androidTest 源里找不到 $propertyName", markerIndex >= 0)
        val literalStart = markerIndex + marker.length
        val literalEnd = source.indexOf("\"\"\"", literalStart)
        assertTrue("$propertyName 的原始字符串未闭合", literalEnd > literalStart)
        return source.substring(literalStart, literalEnd)
    }

    /** 空白（含换行/缩进/CRLF）归一后逐字比对；SQL 文本其余部分必须完全一致。 */
    private fun normalizeWhitespace(sql: String): String = sql.replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val DAO_PATH =
            "src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDao.kt"
        const val COPY_PATH =
            "src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/" +
                "ProblemDaoActiveMistakesPerformanceInstrumentedTest.kt"
    }
}
