package com.tingyun.smartmistakebook.core.database

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S18 尾批 2 的**结构守卫**（JVM 契约；先例 `LibraryFtsCountPathQueryCopyContractTest`）。
 *
 * 它消灭的具体失败：`LibraryFtsRankingPathInstrumentedTest` 的等价性对照只能用**逐字手抄**
 * 的改前 SQL（room3 prepared-statement 路径拒绝 EXPLAIN；builder 又是运行期拼串，@Query 式的
 * 原文提取不适用），EQP 门只能测 live builder 的 SQL。副本抄错、或改前 SQL 被"顺手修成"
 * 新版形状，等价性门就会对**错的参照物**报绿——守卫缺失本身就是"绿得没有意义"。
 * 这里把两处源码读出来钉住结构：
 *
 * 1. **改前参照物**（`preChangeSearchPageSql`）必须保持改前形状：9 个列级相关子查询
 *    （`SELECT 1 FROM library_search_fts AS ranked` + `ranked.<column> MATCH ?`）+ 3 个整行
 *    探测 + 普通 `JOIN` 驱动（**不得**含 `CROSS JOIN` / `hit_docid`）；
 * 2. **live 形态**（`RoomLibrarySearchStore.buildLibrarySearchRawQuery`）必须是集合级
 *    命中集 join：`CROSS JOIN` 顺序锁 + `LEFT JOIN (SELECT docid AS hit_docid ...)` +
 *    `hit_$column` / `hit_token_$index` 别名，且**不得**再出现改前的 `AS ranked` 相关子查询
 *    （防静默回退）。
 */
class LibraryFtsRankingQueryContractTest {

    @Test
    fun preChangeReferenceKeepsTheCorrelatedShape() {
        val body = extractFunctionBody(
            File(RANKING_TEST_PATH).readText(),
            "private fun preChangeSearchPageSql(",
        )
        assertTrue(
            "改前参照物丢了列级相关子查询（等价性门的旧口径不成立）：\n$body",
            "SELECT 1 FROM library_search_fts AS ranked" in body,
        )
        assertTrue(
            "改前参照物丢了列级 MATCH 约束：\n$body",
            "AND ranked.\$column MATCH ?" in body,
        )
        assertTrue(
            "改前参照物丢了整行探测（额外 token 的 +1）：\n$body",
            "WHERE library_search_fts.docid = content.content_row_id" in body,
        )
        assertTrue(
            "改前参照物丢了 9 列权重表：\n$body",
            "\"stem_text\" to 4" in body && "\"subject\" to 2" in body &&
                "\"formula_tokens\" to 1" in body,
        )
        assertFalse(
            "改前参照物混进了新形态（它必须钉旧口径）：\n$body",
            "hit_docid" in body || "CROSS JOIN" in body || "LEFT JOIN" in body,
        )
        assertTrue(
            "改前参照物丢了普通 JOIN 驱动形态：\n$body",
            "JOIN library_search_content AS content" in body,
        )
    }

    @Test
    fun liveBuilderKeepsTheHitSetJoinShape() {
        val source = File(STORE_PATH).readText()
        assertTrue(
            "live builder 缺 FTS 最左的 CROSS JOIN 顺序锁（驱动顺序会被 planner 重排）",
            "CROSS JOIN library_search_content AS content" in source &&
                "CROSS JOIN library_catalog AS catalog" in source,
        )
        assertTrue(
            "live builder 缺集合级命中集 join（LEFT JOIN (SELECT docid AS hit_docid ...)）",
            "LEFT JOIN (\\n" in source &&
                "    SELECT docid AS hit_docid FROM library_search_fts\\n" in source,
        )
        assertTrue(
            "live builder 缺列级命中集别名（hit_\$column）或额外 token 别名（hit_token_\$index）",
            "\"hit_\$column\"" in source && "\"hit_token_\$index\"" in source,
        )
        assertTrue(
            "live builder 不再逐行乘权重（CASE WHEN <alias>.hit_docid IS NOT NULL）：" +
                "排序语义可能被改写成别的公式",
            "CASE WHEN \$alias.hit_docid IS NOT NULL THEN 1 ELSE 0 END" in source,
        )
        assertFalse(
            "live builder 回退到逐行 FTS 相关子查询（旧形态 `AS ranked`）：" +
                "EQP 门会红，这里先给出可读的失败原因",
            "AS ranked" in source,
        )
    }

    /** 从 [marker] 起截到下一个方法前的 `// ---` 横幅；找不到横幅就截到文件末尾。 */
    private fun extractFunctionBody(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("源里找不到 $marker", start >= 0)
        val bannerIndex = source.indexOf("\n    // ------", start)
        return if (bannerIndex < 0) source.substring(start) else source.substring(start, bannerIndex)
    }

    private companion object {
        const val STORE_PATH =
            "src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomLibrarySearchStore.kt"
        const val RANKING_TEST_PATH =
            "src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/" +
                "LibraryFtsRankingPathInstrumentedTest.kt"
    }
}
