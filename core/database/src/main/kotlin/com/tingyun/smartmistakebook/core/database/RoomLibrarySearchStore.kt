package com.tingyun.smartmistakebook.core.database

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room3.RoomRawQuery
import androidx.room3.withWriteTransaction
import com.tingyun.smartmistakebook.core.database.dao.LibraryFacetCountRow
import com.tingyun.smartmistakebook.core.database.dao.LibraryFtsSearchDao
import com.tingyun.smartmistakebook.core.database.entity.LibraryCatalogView
import kotlinx.coroutines.CancellationException

/** Library catalog reads and the incremental CJK FTS search projection. */
internal class RoomLibrarySearchStore(
    private val database: StudyDatabase,
) {
    fun pagingSource(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
    ): PagingSource<Int, LibraryCatalogRow> =
        MappingPagingSource(
            delegate = database.libraryQueryDao().pagingSource(
                searchText = searchText,
                subjectId = subjectId,
                sectionId = sectionId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
                sort = sort,
            ),
            transform = LibraryCatalogView::toRow,
        )

    fun searchPagingSource(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
    ): PagingSource<Int, LibraryCatalogRow> {
        require(matchQuery.isNotBlank()) { "FTS search needs a non-blank MATCH expression" }
        require(tokens.isNotEmpty()) { "FTS search needs at least one query token" }
        return RefreshingPagingSource(
            beforeLoad = ::refreshProjection,
            delegate = MappingPagingSource(
                delegate = database.libraryFtsSearchDao().searchPagingSource(
                    buildLibrarySearchRawQuery(
                        matchQuery = matchQuery,
                        subjectId = subjectId,
                        sectionId = sectionId,
                        masteryId = masteryId,
                        createdFromEpochMillis = createdFromEpochMillis,
                        createdToEpochMillis = createdToEpochMillis,
                        sort = sort,
                        tokens = tokens,
                    ),
                ),
                transform = LibraryFtsSearchDao.LibrarySearchHitRow::toCatalogRow,
            ),
        )
    }

    /**
     * Builds the FTS4 search statement for [LibraryFtsSearchDao.searchPagingSource]
     * as a [RoomRawQuery].
     *
     * The weighted ranking sums per-column hit indicators; Room's @Query SQL
     * parser rejects the shape, so the statement runs raw. Every dynamic value
     * is bound positionally through the binding function (never interpolated),
     * and the secondary sort term is chosen from a fixed whitelist, so no
     * caller-controlled text reaches the SQL.
     *
     * Ranking shape (S18 尾批 2): each hit indicator is a **set membership
     * joined by docid** — `LEFT JOIN (SELECT docid FROM library_search_fts
     * WHERE <column> MATCH ?) ON hit_docid = library_search_fts.docid` — so
     * FTS is scanned once per indicator (12 sets), not once per matched row
     * per indicator. The previous correlated `EXISTS(...)` form was exactly
     * that per-row probe: 146ms at 100 hits, 784ms at 1k, 19.2s at 10k on the
     * 1 万行夹具; the set form measures 79–95ms at 100 hits and ~85–100ms at
     * 1k (P95, production path) and keeps the same row order
     * (`LibraryFtsRankingPathInstrumentedTest` compares against a frozen
     * pre-change SQL copy, including tied keys).
     *
     * `internal` (not private) so the androidTest EQP gate can EXPLAIN the
     * exact statement production runs instead of a hand copy
     * ([RoomRawQuery.sql] is public); see `LibraryFtsRankingPathInstrumentedTest`.
     */
    internal fun buildLibrarySearchRawQuery(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        limit: Int? = null,
        offset: Int? = null,
    ): RoomRawQuery {
        require(tokens.isNotEmpty()) { "FTS search needs at least one query token" }
        val primaryPhrase = CjkTextTokenizer.quotedPhrase(tokens.first())
        val neverMatchPhrase = CjkTextTokenizer.quotedPhrase("\uFFFD")
        val extras = tokens.drop(1).take(3).map(CjkTextTokenizer::quotedPhrase)
        val extraTokenPhrases = listOf(
            extras.getOrElse(0) { neverMatchPhrase },
            extras.getOrElse(1) { neverMatchPhrase },
            extras.getOrElse(2) { neverMatchPhrase },
        )
        val bindings = mutableListOf<Any>()
        val filters = StringBuilder()
        val hitSets = StringBuilder()
        val ranking = StringBuilder()
        var rankingTerm = 0

        /**
         * 每个排序标志 = "该 docid 是否在这个列/整行的命中集里"。命中集在 join 里
         * 只扫一次 FTS 索引（`MATERIALIZE hit_*` + 自动覆盖索引 `(hit_docid=?)`），
         * 而不是对每个命中行逐行回探 FTS——后者是 9–12 次/行的相关子查询，1 万命中
         * 实测 19.2s（百级 146ms 尚可、千级 784ms 已超预算，见 S18 尾批 2 报告）。
         * join 的 ON 用主表 `library_search_fts.docid`，与结果行里 content 行的 rowid
         * 恒等（同一个 join 条件），所以标志取值与相关子查询逐行等价；docid 在 FTS
         * 表里唯一，LEFT JOIN 不会放大行数。
         */
        fun addRankingTerm(alias: String, column: String?, phrase: String, weight: Int) {
            val matchConstraint = if (column != null) {
                "library_search_fts.$column MATCH ?"
            } else {
                "library_search_fts MATCH ?"
            }
            hitSets.append(
                "\nLEFT JOIN (\n" +
                    "    SELECT docid AS hit_docid FROM library_search_fts\n" +
                    "    WHERE $matchConstraint\n" +
                    ") AS $alias ON $alias.hit_docid = library_search_fts.docid",
            )
            if (rankingTerm > 0) ranking.append("\n  + ")
            ranking.append("$weight * (CASE WHEN $alias.hit_docid IS NOT NULL THEN 1 ELSE 0 END)")
            rankingTerm++
            bindings += phrase
        }
        listOf(
            "stem_text" to 4,
            "solution_text" to 3,
            "knowledge_points" to 2,
            "subject" to 2,
            "options_text" to 1,
            "chapter" to 1,
            "tags" to 1,
            "error_reason" to 1,
            "formula_tokens" to 1,
        ).forEach { (column, weight) ->
            addRankingTerm("hit_$column", column, primaryPhrase, weight)
        }
        extraTokenPhrases.forEachIndexed { index, phrase ->
            addRankingTerm("hit_token_$index", null, phrase, 1)
        }
        bindings += matchQuery
        if (subjectId != null) {
            filters.append("\n  AND catalog.subject = ?")
            bindings += subjectId
        }
        if (sectionId != null) {
            filters.append(
                "\n  AND EXISTS (\n" +
                    "      SELECT 1 FROM problem_classification_binding AS classification\n" +
                    "      WHERE classification.problem_id = catalog.problem_id\n" +
                    "        AND classification.basis_revision_id = catalog.problem_revision_id\n" +
                    "        AND classification.dimension = 'CHAPTER'\n" +
                    "        AND classification.label_id = ?\n" +
                    "  )",
            )
            bindings += sectionId
        }
        if (masteryId != null) {
            filters.append("\n  AND catalog.mastery_id = ?")
            bindings += masteryId
        }
        if (createdFromEpochMillis != null) {
            filters.append("\n  AND catalog.created_at_epoch_millis >= ?")
            bindings += createdFromEpochMillis
        }
        if (createdToEpochMillis != null) {
            filters.append("\n  AND catalog.created_at_epoch_millis <= ?")
            bindings += createdToEpochMillis
        }
        val sortClause = when (sort) {
            "RECENTLY_CREATED" -> "catalog.created_at_epoch_millis DESC,\n    "
            else -> ""
        }
        if (limit != null) {
            bindings += limit.toLong()
            bindings += offset!!.toLong()
        }
        val sql = "SELECT catalog.*,\n" +
            "       snippet(library_search_fts, '【', '】', '…', -1, 12) AS snippet\n" +
            "FROM library_search_fts\n" +
            "CROSS JOIN library_search_content AS content\n" +
            "    ON content.content_row_id = library_search_fts.docid\n" +
            "CROSS JOIN library_catalog AS catalog\n" +
            "    ON catalog.problem_revision_id = content.problem_revision_id$hitSets\n" +
            "WHERE library_search_fts MATCH ?$filters\n" +
            "ORDER BY (\n" +
            "    $ranking\n" +
            ") DESC,\n" +
            "    $sortClause" +
            "catalog.updated_at_epoch_millis DESC,\n" +
            "    catalog.entry_id ASC" +
            if (limit != null) "\nLIMIT ? OFFSET ?" else ""
        val orderedBindings = bindings.toList()
        return RoomRawQuery(sql) { statement ->
            orderedBindings.forEachIndexed { index, value ->
                when (value) {
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    is Long -> statement.bindLong(index + 1, value)
                    else -> statement.bindText(index + 1, value as String)
                }
            }
        }
    }

    suspend fun searchCount(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): Int {
        require(matchQuery.isNotBlank()) { "FTS search needs a non-blank MATCH expression" }
        refreshProjection()
        return database.libraryFtsSearchDao().countSearch(
            matchQuery = matchQuery,
            subjectId = subjectId,
            sectionId = sectionId,
            masteryId = masteryId,
            createdFromEpochMillis = createdFromEpochMillis,
            createdToEpochMillis = createdToEpochMillis,
        )
    }

    suspend fun searchPage(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        tokens: List<String>,
        offset: Int,
        limit: Int,
    ): List<LibraryCatalogRow> {
        require(matchQuery.isNotBlank()) { "FTS search needs a non-blank MATCH expression" }
        require(tokens.isNotEmpty()) { "FTS search needs at least one query token" }
        refreshProjection()
        return database.libraryFtsSearchDao().searchPage(
            buildLibrarySearchRawQuery(
                matchQuery = matchQuery,
                subjectId = subjectId,
                sectionId = sectionId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
                sort = sort,
                tokens = tokens,
                limit = limit,
                offset = offset,
            ),
        ).map { it.toCatalogRow() }
    }

    suspend fun searchFacets(
        matchQuery: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
    ): List<LibraryFacetCountRecord> {
        require(matchQuery.isNotBlank()) { "FTS search needs a non-blank MATCH expression" }
        refreshProjection()
        val dao = database.libraryFtsSearchDao()
        return when (facet) {
            "SUBJECT" -> dao.searchSubjectFacets(
                matchQuery = matchQuery,
                sectionId = sectionId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            )
            "SECTION" -> dao.searchSectionFacets(
                matchQuery = matchQuery,
                subjectId = subjectId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            )
            "MASTERY" -> dao.searchMasteryFacets(
                matchQuery = matchQuery,
                subjectId = subjectId,
                sectionId = sectionId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            )
            else -> error("Unsupported library facet kind: $facet")
        }.map(LibraryFacetCountRow::toRecord)
    }

    /**
     * Idempotently create FTS content-sync and outbox triggers on the raw
     * connection. Room rejects DDL in @Query, so this runs outside the DAO;
     * IF NOT EXISTS keeps repeat calls cheap.
     */
    private suspend fun ensureSearchTriggers() {
        database.withRawConnection(isReadOnly = false) { connection ->
        listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_library_search_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `library_search_content` BEGIN " +
                "DELETE FROM `library_search_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_library_search_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `library_search_content` BEGIN " +
                "DELETE FROM `library_search_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_library_search_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `library_search_content` BEGIN " +
                "INSERT INTO `library_search_fts`(`docid`, `stem_text`, `options_text`, " +
                "`solution_text`, `subject`, `chapter`, `knowledge_points`, `tags`, " +
                "`error_reason`, `formula_tokens`) VALUES (NEW.`rowid`, NEW.`stem_text`, " +
                "NEW.`options_text`, NEW.`solution_text`, NEW.`subject`, NEW.`chapter`, " +
                "NEW.`knowledge_points`, NEW.`tags`, NEW.`error_reason`, " +
                "NEW.`formula_tokens`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_library_search_fts_AFTER_INSERT " +
                "AFTER INSERT ON `library_search_content` BEGIN " +
                "INSERT INTO `library_search_fts`(`docid`, `stem_text`, `options_text`, " +
                "`solution_text`, `subject`, `chapter`, `knowledge_points`, `tags`, " +
                "`error_reason`, `formula_tokens`) VALUES (NEW.`rowid`, NEW.`stem_text`, " +
                "NEW.`options_text`, NEW.`solution_text`, NEW.`subject`, NEW.`chapter`, " +
                "NEW.`knowledge_points`, NEW.`tags`, NEW.`error_reason`, " +
                "NEW.`formula_tokens`); END",
            "CREATE TRIGGER IF NOT EXISTS library_search_outbox_revision_insert " +
                "AFTER INSERT ON `problem_revision` BEGIN " +
                "INSERT INTO `library_search_outbox` (`revision_id`, `queued_at_epoch_millis`) " +
                "VALUES (NEW.`revision_id`, NEW.`created_at_epoch_millis`); END",
            "CREATE TRIGGER IF NOT EXISTS library_search_outbox_revision_update " +
                "AFTER UPDATE ON `problem_revision` BEGIN " +
                "INSERT INTO `library_search_outbox` (`revision_id`, `queued_at_epoch_millis`) " +
                "VALUES (NEW.`revision_id`, NEW.`created_at_epoch_millis`); END",
        ).forEach { sql ->
            connection.usePrepared(sql) { statement -> statement.step() }
        }
        }
    }

    suspend fun refreshProjection() {
        database.withWriteTransaction {
            val dao = database.libraryFtsSearchDao()
            ensureSearchTriggers()
            if (dao.countIndexed() == 0) {
                // First bootstrap (or repair): re-segment everything we know
                // about - the active library plus every already-materialized
                // row; stale content rows drop out through the projection read.
                val targets = (dao.readActiveLibraryRevisionIds() +
                    dao.readIndexedRevisionIds()).distinct()
                for (revisionId in targets) {
                    drainSearchRevision(dao, revisionId)
                }
            } else {
                // Incremental path: drain the outbox revision by revision;
                // each upsert/delete is mirrored into the FTS index by the
                // room_fts_content_sync triggers.
                while (true) {
                    val batch = dao.readOutboxBatch()
                    if (batch.isEmpty()) break
                    for (revisionId in batch.distinct()) {
                        drainSearchRevision(dao, revisionId)
                    }
                }
            }
        }
    }

    private suspend fun drainSearchRevision(
        dao: LibraryFtsSearchDao,
        revisionId: String,
    ) {
        val row = dao.readRevisionForProjection(revisionId)
        if (row == null) {
            // Dropped from the library: the BEFORE_DELETE sync trigger
            // removes the FTS row alongside the content row.
            dao.deleteContent(revisionId)
        } else {
            val stem = CjkTextTokenizer.segment(row.title + "\n" + row.problemMarkdown)
            val options = CjkTextTokenizer.segment(row.questionDocumentSnapshot.orEmpty())
            val solution = CjkTextTokenizer.segment(row.answerSpecSnapshot.orEmpty())
            val subject = CjkTextTokenizer.segment(row.subject)
            val chapter = CjkTextTokenizer.segment(row.chapter)
            val knowledge = CjkTextTokenizer.segment(row.knowledgePoints)
            val tags = CjkTextTokenizer.segment(row.tags)
            val errorReason = CjkTextTokenizer.segment(row.errorReason)
            val formulaTokens = FormulaSearchProjection.tokensForSnapshot(
                row.questionDocumentSnapshot,
            )
            if (dao.countContentFor(revisionId) > 0) {
                dao.updateContent(
                    revisionId = revisionId,
                    stemText = stem,
                    optionsText = options,
                    solutionText = solution,
                    subject = subject,
                    chapter = chapter,
                    knowledgePoints = knowledge,
                    tags = tags,
                    errorReason = errorReason,
                    formulaTokens = formulaTokens,
                )
            } else {
                dao.insertContent(
                    revisionId = revisionId,
                    stemText = stem,
                    optionsText = options,
                    solutionText = solution,
                    subject = subject,
                    chapter = chapter,
                    knowledgePoints = knowledge,
                    tags = tags,
                    errorReason = errorReason,
                    formulaTokens = formulaTokens,
                )
            }
        }
        dao.clearOutboxFor(revisionId)
    }

    /** Rebuilds one revision's FTS row from its projection (used on restore). */
    internal suspend fun reindexRevision(revisionId: String) {
        drainSearchRevision(database.libraryFtsSearchDao(), revisionId)
    }

    suspend fun catalogPage(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        offset: Int,
        limit: Int,
    ): List<LibraryCatalogRow> {
        return database.libraryQueryDao()
            .page(
                searchText = searchText,
                subjectId = subjectId,
                sectionId = sectionId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
                sort = sort,
                offset = offset,
                limit = limit,
            )
            .map(LibraryCatalogView::toRow)
    }

    suspend fun catalogCount(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): Int {
        return database.libraryQueryDao().count(
            searchText = searchText,
            subjectId = subjectId,
            sectionId = sectionId,
            masteryId = masteryId,
            createdFromEpochMillis = createdFromEpochMillis,
            createdToEpochMillis = createdToEpochMillis,
        )
    }

    suspend fun catalogFacets(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        facet: String,
    ): List<LibraryFacetCountRecord> {
        return when (facet) {
            "SUBJECT" -> database.libraryQueryDao().subjectFacets(
                searchText = searchText,
                sectionId = sectionId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            ).map(LibraryFacetCountRow::toRecord)

            "SECTION" -> database.libraryQueryDao().sectionFacets(
                searchText = searchText,
                subjectId = subjectId,
                masteryId = masteryId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            ).map(LibraryFacetCountRow::toRecord)

            "MASTERY" -> database.libraryQueryDao().masteryFacets(
                searchText = searchText,
                subjectId = subjectId,
                sectionId = sectionId,
                createdFromEpochMillis = createdFromEpochMillis,
                createdToEpochMillis = createdToEpochMillis,
            ).map(LibraryFacetCountRow::toRecord)

            else -> error("Unsupported library facet kind: $facet")
        }
    }
}

/** Shared catalog-label decoding: unit-separated, trimmed, deduplicated, sorted. */
internal fun String?.toCatalogLabels(): List<String> = this
    ?.split("\u001F")
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.distinct()
    ?.sorted()
    .orEmpty()

private fun LibraryCatalogView.toRow() = LibraryCatalogRow(
    entryId = entryId,
    title = title,
    problemMarkdown = problemMarkdown,
    subject = subject,
    chapterLabels = chapterLabels.toCatalogLabels(),
    knowledgeLabels = knowledgeLabels.toCatalogLabels(),
    masteryId = masteryId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    nextReviewAtEpochMillis = nextReviewAtEpochMillis,
    retrievability = retrievability,
)

private fun LibraryFacetCountRow.toRecord() = LibraryFacetCountRecord(
    id = id,
    label = label,
    count = count,
)

private fun LibraryFtsSearchDao.LibrarySearchHitRow.toCatalogRow() = LibraryCatalogRow(
    entryId = entryId,
    title = title,
    problemMarkdown = problemMarkdown,
    subject = subject,
    chapterLabels = chapterLabels.toCatalogLabels(),
    knowledgeLabels = knowledgeLabels.toCatalogLabels(),
    masteryId = masteryId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    nextReviewAtEpochMillis = nextReviewAtEpochMillis,
    retrievability = retrievability,
)

private class RefreshingPagingSource<T : Any>(
    private val beforeLoad: suspend () -> Unit,
    private val delegate: PagingSource<Int, T>,
) : PagingSource<Int, T>() {
    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return delegate.getRefreshKey(state)
    }
    override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, T> {
        try {
            beforeLoad()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Projection refresh is best-effort; delegate load still proceeds.
        }
        return delegate.load(params)
    }
}
