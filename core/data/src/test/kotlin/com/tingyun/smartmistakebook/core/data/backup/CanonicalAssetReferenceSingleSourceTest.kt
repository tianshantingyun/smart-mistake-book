package com.tingyun.smartmistakebook.core.data.backup

import com.tingyun.smartmistakebook.core.database.dao.CanonicalAssetReferenceSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 19 T2-5（I5 单源化）的同源钉：清理认领（Room DAO）与备份裁剪器的五站点资产
 * 引用口径必须由**同一个** SQL 定义生成。
 *
 * 消灭的失败：批次 18 里这两侧各写一份 SQL（DAO 五个 `NOT EXISTS`、裁剪器五个
 * `EXISTS`），新增第六类引用形态时只改一侧——清理把仍被引用的图删了（RESTRICT 撞约束
 * 或用户图丢），或备份漏收引用图（恢复后详情页无图）。本测试把「裁剪器那侧必须由共享
 * 谓词生成」钉死：谁再手写一份 EXISTS 析取，这里先红。
 *
 * DAO 侧由构造保证单源：`@Query(CanonicalAssetReferenceSites.FIND_UNREFERENCED_CANONICAL_ASSETS_SQL)`
 * 直接引用常量，不存在第二份文本（Room 编译期校验该常量里的 SQL）。
 */
class CanonicalAssetReferenceSingleSourceTest {

    @Test
    fun `trimmer five site sql is derived from the shared predicate`() {
        assertEquals(
            "裁剪器的五站点 SQL 必须由共享谓词单源生成（NOT (五个 NOT EXISTS) ≡ 五个 EXISTS）",
            "SELECT asset.source_asset_id FROM canonical_source_asset AS asset " +
                "WHERE NOT (" + CanonicalAssetReferenceSites.UNREFERENCED_PREDICATE_SQL + ")",
            BackupSnapshotTrimmer.FIVE_SITE_ASSET_IDS_SQL,
        )
    }

    @Test
    fun `shared predicate names exactly the five reference sites`() {
        val predicate = CanonicalAssetReferenceSites.UNREFERENCED_PREDICATE_SQL
        assertEquals("恰好五个 NOT EXISTS 分支", 5, Regex("NOT EXISTS").findAll(predicate).count())
        listOf(
            "problem_revision_source_asset",
            "problem_draft_source_asset",
            "problem_draft",
            "tutor_message_source_asset",
            "figure_generation_attempt",
        ).forEach { site ->
            assertTrue("五站点谓词必须点名 $site", predicate.contains("$site AS "))
        }
        // DAO 完整查询：WHERE 子句就是共享谓词本身，排序保持清理认领的既有口径。
        val daoQuery = CanonicalAssetReferenceSites.FIND_UNREFERENCED_CANONICAL_ASSETS_SQL
        assertTrue(
            "DAO 查询的 WHERE 必须内联同一份谓词（不另抄第二份）",
            daoQuery.contains("WHERE $predicate"),
        )
        assertTrue(
            "清理认领从最老资产开删的排序不得漂移",
            daoQuery.contains("ORDER BY asset.created_at_epoch_millis ASC, asset.source_asset_id ASC"),
        )
        assertTrue(
            "查询对象仍是 canonical_source_asset 且别名为 asset（谓词绑定的别名）",
            daoQuery.contains("FROM canonical_source_asset AS asset"),
        )
    }
}
