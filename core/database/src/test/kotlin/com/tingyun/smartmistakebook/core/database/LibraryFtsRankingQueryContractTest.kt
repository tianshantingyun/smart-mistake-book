package com.tingyun.smartmistakebook.core.database

import java.io.File
import org.junit.Assert.assertEquals
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
 * 3. **冻结副本契约（2026-10-07 收口修复轮加强）**：live builder 的 9 个列权重、全部
 *    筛选片段、排序二级键与额外 token 不变量（恰好 3 个、权重 1）必须与 androidTest
 *    冻结副本**空白归一后逐字一致**——"顺手改权重（solution_text 3→5）/少一个筛选片段"
 *    即红，不再只靠抽查三条权重与形状标记。
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

    /**
     * 冻结副本契约（与批 1 `LibraryFtsCountPathQueryCopyContractTest` 同款；2026-10-07
     * 收口修复轮加强）。它消灭的具体失败：结构标记守卫只抽查三条权重、只证形状标记，
     * "顺手改权重（如 solution_text 3→5）""少一个筛选片段"仍会绿。这里把 live builder
     * （[STORE_PATH]）与 androidTest 冻结副本（[RANKING_TEST_PATH] 的
     * `preChangeSearchPageSql`）的排序/筛选相关字面量按**空白归一后逐字比对**：
     *
     * 1. 9 个列权重表（含顺序）——改错任一权重即红；
     * 2. 全部筛选片段（拼成 SQL 文本）——少一段/改一段即红；
     * 3. 排序二级键（`RECENTLY_CREATED` 分支 + `updated_at`/`entry_id` 决胜键）；
     * 4. 额外 token 的不变量（两代形态不同，逐字面限于"恰好 3 个、权重 1"）。
     *
     * 任一处不一致都提示"改实现后必须同步更新测试副本"——否则等价性门会拿旧参照物报绿。
     */
    @Test
    fun rankingAndFilterLiteralsMatchTheFrozenReferenceCopy() {
        val live = File(STORE_PATH).readText()
        val copy = File(RANKING_TEST_PATH).readText()

        // ① 9 个列权重表（含顺序）。
        assertEquals(
            "live builder 的列权重表与 androidTest 冻结副本漂移（solution 3→5 这类改动会改变" +
                "排序语义）：改实现后必须同步更新测试副本",
            normalizeWhitespace(
                sliceBetween(live, "\"stem_text\" to 4,", ").forEach { (column, weight) ->"),
            ),
            normalizeWhitespace(
                sliceBetween(
                    copy,
                    "\"stem_text\" to 4,",
                    ").forEachIndexed { index, (column, weight) ->",
                ),
            ),
        )

        // ② 全部筛选片段（拼成 SQL 文本后比对；两侧代码形态不同、SQL 字面量必须逐字一致）。
        assertEquals(
            "live builder 的筛选片段与 androidTest 冻结副本漂移（少一段/改一段筛选）：" +
                "改实现后必须同步更新测试副本",
            normalizeWhitespace(
                stringLiteralsOf(
                    sliceBetween(
                        live,
                        "if (subjectId != null) {",
                        "val sortClause = when (sort) {",
                    ),
                ),
            ),
            normalizeWhitespace(
                stringLiteralsOf(
                    sliceBetween(
                        copy,
                        "if (filters.subjectId != null) append(",
                        "val ranking = StringBuilder()",
                    ),
                ),
            ),
        )

        // ③ 排序二级键：RECENTLY_CREATED 分支 + updated_at/entry_id 决胜键。
        assertEquals(
            "live builder 的排序二级键分支与 androidTest 冻结副本漂移：" +
                "改实现后必须同步更新测试副本",
            normalizeWhitespace(
                sliceBetween(
                    live,
                    "val sortClause = when (sort) {",
                    "if (limit != null) {",
                ),
            ),
            normalizeWhitespace(
                sliceBetween(
                    copy,
                    "val sortClause = when (sort) {",
                    "return \"SELECT catalog.*,\\n\" +",
                ),
            ),
        )
        listOf("updated_at_epoch_millis DESC", "entry_id ASC").forEach { key ->
            assertEquals(
                "live builder 的排序决胜键字面量（$key）与 androidTest 冻结副本漂移：" +
                    "改实现后必须同步更新测试副本",
                literalContaining(live, key),
                literalContaining(copy, key),
            )
        }

        // ④ 额外 token 的不变量：恰好 3 个、权重恒为 1（两代形态不同，逐字面限于该不变量）。
        val liveExtraTokens = takeCountIn(live)
        assertEquals(
            "live builder 的额外 token 数不是 3（排序公式的常数项个数漂移）",
            "3",
            liveExtraTokens,
        )
        assertEquals(
            "androidTest 冻结副本的 EXTRA_TOKEN_COUNT 与 live builder 的 take(n) 不一致：" +
                "改实现后必须同步更新测试副本",
            liveExtraTokens,
            constValueOf(copy, "EXTRA_TOKEN_COUNT"),
        )
        assertEquals(
            "live builder 的额外 token 权重不是 1（排序公式的常数项变了）",
            "1",
            extraTokenWeightIn(live),
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

    /** 取 [marker] 起、到 [endMarker] 前的源码片段（逐字比对用；两个锚点都必须存在）。 */
    private fun sliceBetween(source: String, marker: String, endMarker: String): String {
        val start = source.indexOf(marker)
        assertTrue("源里找不到锚点：$marker", start >= 0)
        val end = source.indexOf(endMarker, start + marker.length)
        assertTrue("源里找不到锚点 `$marker` 之后的结束锚点：$endMarker", end >= 0)
        return source.substring(start, end)
    }

    /**
     * 空白归一：跨行/缩进差异不算漂移，字面量内容差异才算。字符串字面量里的 `\n`/`\t`
     * 转义序列也按空白处理——测试副本把次级排序键的尾部换行拆在 `LIMIT` 字面量一侧、
     * live 拆在别处（SQL 文本等价），逐字面比对不应把这种拆法差异算成漂移。
     */
    private fun normalizeWhitespace(text: String): String = text
        .replace("\\n", " ")
        .replace("\\t", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * 拼接片段里所有普通字符串字面量的**内容**（跳过 `//` 行注释与 `\` 转义）。
     * live 与副本两侧的 Kotlin 代码形态可以不同（`filters.append(...)` vs `append(...)`），
     * 但 SQL 字面量文本必须逐字一致。
     */
    private fun stringLiteralsOf(region: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < region.length) {
            when {
                region.startsWith("//", index) -> {
                    val lineEnd = region.indexOf('\n', index)
                    index = if (lineEnd < 0) region.length else lineEnd + 1
                }
                region[index] == '"' -> {
                    val content = StringBuilder()
                    var cursor = index + 1
                    while (cursor < region.length && region[cursor] != '"') {
                        if (region[cursor] == '\\' && cursor + 1 < region.length) {
                            content.append(region[cursor]).append(region[cursor + 1])
                            cursor += 2
                        } else {
                            content.append(region[cursor])
                            cursor++
                        }
                    }
                    assertTrue("字符串字面量未闭合：$region", cursor < region.length)
                    out.append(content)
                    index = cursor + 1
                }
                else -> index++
            }
        }
        return out.toString()
    }

    /** 取源里含 [needle] 的字符串字面量内容（空白归一）；找不到即断言失败。 */
    private fun literalContaining(source: String, needle: String): String {
        val pattern = Regex("\"([^\"\\n]*" + Regex.escape(needle) + "[^\"\\n]*)\"")
        val match = pattern.find(source)
        assertTrue("源里找不到含 `$needle` 的字符串字面量", match != null)
        return normalizeWhitespace(match!!.groupValues[1])
    }

    /** live builder 的额外 token 数：`tokens.drop(1).take(3)`。 */
    private fun takeCountIn(source: String): String {
        val match = Regex("""tokens\.drop\(1\)\.take\((\d+)\)""").find(source)
        assertTrue("live builder 里找不到 tokens.drop(1).take(<n>)", match != null)
        return match!!.groupValues[1]
    }

    /** 源里 `const val <name> = <n>` 的字面值。 */
    private fun constValueOf(source: String, name: String): String {
        val match = Regex("""const val $name = (\d+)""").find(source)
        assertTrue("源里找不到 const val $name = <n>", match != null)
        return match!!.groupValues[1]
    }

    /** live builder 的额外 token 命中集权重：`addRankingTerm("hit_token_$index", null, phrase, 1)`。 */
    private fun extraTokenWeightIn(source: String): String {
        val match = Regex(
            """addRankingTerm\("hit_token_\${'$'}index", null, phrase, (\d+)\)""",
        ).find(source)
        assertTrue("live builder 里找不到额外 token 的 addRankingTerm(..., <weight>)", match != null)
        return match!!.groupValues[1]
    }

    private companion object {
        const val STORE_PATH =
            "src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomLibrarySearchStore.kt"
        const val RANKING_TEST_PATH =
            "src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/" +
                "LibraryFtsRankingPathInstrumentedTest.kt"
    }
}
