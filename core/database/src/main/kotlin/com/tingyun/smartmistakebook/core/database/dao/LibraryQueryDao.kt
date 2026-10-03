package com.tingyun.smartmistakebook.core.database.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.tingyun.smartmistakebook.core.database.entity.LibraryCatalogView

/**
 * 错题本目录的筛选/排序查询（阶段 4A 批 1 · L5）：
 * 一层筛选 = 科目 → 板块；另有掌握程度与「录入时间段」（创建时间闭区间）；
 * 排序只有 `RECENTLY_UPDATED`（默认，落到 `updated_at DESC`）与 `RECENTLY_CREATED`
 * 两值——`NEXT_REVIEW`/`LEAST_MASTERED` 已退场（知识点筛选层同批删除）。
 */
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
@Dao
internal interface LibraryQueryDao {
    @Query(
        """
        SELECT * FROM library_catalog AS catalog
        WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
          AND (
              :sectionId IS NULL OR EXISTS (
                  SELECT 1 FROM problem_classification_binding AS classification
                  WHERE classification.problem_id = catalog.problem_id
                    AND classification.basis_revision_id = catalog.problem_revision_id
                    AND classification.dimension = 'CHAPTER'
                    AND classification.label_id = :sectionId
              )
          )
          AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        ORDER BY
            CASE :sort WHEN 'RECENTLY_CREATED' THEN catalog.created_at_epoch_millis END DESC,
            catalog.updated_at_epoch_millis DESC,
            catalog.entry_id ASC
        """,
    )
    fun pagingSource(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
    ): PagingSource<Int, LibraryCatalogView>

    @Query(
        """
        SELECT * FROM library_catalog AS catalog
        WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
          AND (
              :sectionId IS NULL OR EXISTS (
                  SELECT 1 FROM problem_classification_binding AS classification
                  WHERE classification.problem_id = catalog.problem_id
                    AND classification.basis_revision_id = catalog.problem_revision_id
                    AND classification.dimension = 'CHAPTER'
                    AND classification.label_id = :sectionId
              )
          )
          AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        ORDER BY
            CASE :sort WHEN 'RECENTLY_CREATED' THEN catalog.created_at_epoch_millis END DESC,
            catalog.updated_at_epoch_millis DESC,
            catalog.entry_id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun page(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
        sort: String,
        offset: Int,
        limit: Int,
    ): List<LibraryCatalogView>

    @Query(
        """
        SELECT COUNT(*) FROM library_catalog AS catalog
        WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
          AND (
              :sectionId IS NULL OR EXISTS (
                  SELECT 1 FROM problem_classification_binding AS classification
                  WHERE classification.problem_id = catalog.problem_id
                    AND classification.basis_revision_id = catalog.problem_revision_id
                    AND classification.dimension = 'CHAPTER'
                    AND classification.label_id = :sectionId
              )
          )
          AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        """,
    )
    suspend fun count(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): Int

    @Query(
        """
        SELECT catalog.subject AS id, catalog.subject AS label, COUNT(*) AS count
        FROM library_catalog AS catalog
        WHERE (:sectionId IS NULL OR EXISTS (
                  SELECT 1 FROM problem_classification_binding AS classification
                  WHERE classification.problem_id = catalog.problem_id
                    AND classification.basis_revision_id = catalog.problem_revision_id
                    AND classification.dimension = 'CHAPTER'
                    AND classification.label_id = :sectionId
              ))
          AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        GROUP BY catalog.subject
        ORDER BY COUNT(*) DESC, id ASC
        """,
    )
    suspend fun subjectFacets(
        searchText: String,
        sectionId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): List<LibraryFacetCountRow>

    @Query(
        """
        SELECT classification.label_id AS id, classification.display_name AS label, COUNT(*) AS count
        FROM library_catalog AS catalog
        INNER JOIN problem_classification_binding AS classification
            ON classification.problem_id = catalog.problem_id
           AND classification.basis_revision_id = catalog.problem_revision_id
           AND classification.dimension = 'CHAPTER'
        WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
          AND (:masteryId IS NULL OR catalog.mastery_id = :masteryId)
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        GROUP BY classification.label_id, classification.display_name
        ORDER BY COUNT(*) DESC, id ASC
        """,
    )
    suspend fun sectionFacets(
        searchText: String,
        subjectId: String?,
        masteryId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): List<LibraryFacetCountRow>

    @Query(
        """
        SELECT catalog.mastery_id AS id, catalog.mastery_id AS label, COUNT(*) AS count
        FROM library_catalog AS catalog
        WHERE (:subjectId IS NULL OR catalog.subject = :subjectId)
          AND (:sectionId IS NULL OR EXISTS (
                  SELECT 1 FROM problem_classification_binding AS classification
                  WHERE classification.problem_id = catalog.problem_id
                    AND classification.basis_revision_id = catalog.problem_revision_id
                    AND classification.dimension = 'CHAPTER'
                    AND classification.label_id = :sectionId
              ))
          AND (
              :createdFromEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis >= :createdFromEpochMillis
          )
          AND (
              :createdToEpochMillis IS NULL OR
                  catalog.created_at_epoch_millis <= :createdToEpochMillis
          )
          AND (
              :searchText = '' OR instr(
                  lower(
                      catalog.title || CHAR(10) || catalog.problem_markdown || CHAR(10) ||
                      catalog.subject || CHAR(10) ||
                      COALESCE(catalog.chapter_labels, '') || CHAR(10) ||
                      COALESCE(catalog.knowledge_labels, '')
                  ),
                  lower(:searchText)
              ) > 0
          )
        GROUP BY catalog.mastery_id
        ORDER BY COUNT(*) DESC, id ASC
        """,
    )
    suspend fun masteryFacets(
        searchText: String,
        subjectId: String?,
        sectionId: String?,
        createdFromEpochMillis: Long?,
        createdToEpochMillis: Long?,
    ): List<LibraryFacetCountRow>
}

internal data class LibraryFacetCountRow(
    val id: String,
    val label: String,
    val count: Int,
)
