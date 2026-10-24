package com.tingyun.smartmistakebook.core.database.dao

/**
 * `canonical_source_asset` 的**五类引用站点**谓词——单一来源（批次 19 T2-5 / I5）。
 *
 * 五类形态（与 `PendingCaptureDao.findUnreferencedCanonicalAssets` 的口径逐位一致）：
 * 1. `problem_revision_source_asset.source_asset_id`（题面修订附图）；
 * 2. `problem_draft_source_asset.source_asset_id`（草稿页束附图）；
 * 3. `problem_draft.source_asset_id`（草稿主图，可空）；
 * 4. `tutor_message_source_asset.source_asset_id`（消息附图）；
 * 5. `figure_generation_attempt.output_asset_id`（图生成尝试产物，不按 status 过滤——
 *    引用是物理的：失败/取消行异常回填的产物同样 RESTRICT，删它会撞约束）。
 *
 * 消灭的失败：清理认领（`findUnreferencedCanonicalAssets`）与备份裁剪器
 * （`BackupSnapshotTrimmer` 的 I5 五站点对拍）此前各写一份 SQL——新增第六类引用形态时
 * 只改了一侧，就会出现「清理不删、备份却漏收」（或反过来）的半截口径。现在两侧都由
 * [UNREFERENCED_PREDICATE_SQL] 这一个常量生成：改这一处，两边同变，测试同改同测。
 *
 * 语义逐位不变：本常量的谓词文本与 6C-2 批次 18 落地时 DAO 查询里的五个
 * `NOT EXISTS` 分支逐字一致（换行空白除外，SQLite 语义等价）。
 */
object CanonicalAssetReferenceSites {

    /**
     * 「未被五类形态引用」谓词，**绑定表别名 `asset`**（= `canonical_source_asset AS asset`）。
     * 返回 0 行 = 被引用（全部 NOT EXISTS 成立 = 未被引用，注意语义方向）。
     */
    const val UNREFERENCED_PREDICATE_SQL: String = """NOT EXISTS (
            SELECT 1 FROM problem_revision_source_asset AS revision_link
            WHERE revision_link.source_asset_id = asset.source_asset_id
        )
          AND NOT EXISTS (
            SELECT 1 FROM problem_draft_source_asset AS draft_link
            WHERE draft_link.source_asset_id = asset.source_asset_id
        )
          AND NOT EXISTS (
            SELECT 1 FROM problem_draft AS draft
            WHERE draft.source_asset_id = asset.source_asset_id
        )
          AND NOT EXISTS (
            SELECT 1 FROM tutor_message_source_asset AS message_link
            WHERE message_link.source_asset_id = asset.source_asset_id
        )
          AND NOT EXISTS (
            SELECT 1 FROM figure_generation_attempt AS attempt_output
            WHERE attempt_output.output_asset_id = asset.source_asset_id
        )"""

    /**
     * `findUnreferencedCanonicalAssets` 的整条查询（Room `@Query` 的值）。列清单与
     * 排序（`created_at ASC, source_asset_id ASC`——清理认领从最老的开删）都固定在这份
     * 单一 SQL 定义里；`WHERE` 子句由 [UNREFERENCED_PREDICATE_SQL] 拼出，不另抄一份。
     */
    const val FIND_UNREFERENCED_CANONICAL_ASSETS_SQL: String =
        "SELECT asset.source_asset_id,\n" +
            "       asset.content_sha256,\n" +
            "       asset.relative_path,\n" +
            "       asset.mime_type,\n" +
            "       asset.byte_size,\n" +
            "       asset.width,\n" +
            "       asset.height,\n" +
            "       asset.source_type,\n" +
            "       asset.created_at_epoch_millis\n" +
            "FROM canonical_source_asset AS asset\n" +
            "WHERE " + UNREFERENCED_PREDICATE_SQL + "\n" +
            "ORDER BY asset.created_at_epoch_millis ASC, asset.source_asset_id ASC"
}
