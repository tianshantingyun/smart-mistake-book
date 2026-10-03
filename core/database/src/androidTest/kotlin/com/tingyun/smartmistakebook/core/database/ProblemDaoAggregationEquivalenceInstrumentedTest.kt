package com.tingyun.smartmistakebook.core.database

import android.content.Context
import androidx.room3.executeSQL
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.dao.MistakeRow
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotFreshness
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import com.tingyun.smartmistakebook.core.model.ProjectionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **S17 等价性**（`docs/research/2026-10-03-stage4a-plan.md` §3 批 1）：
 * `observeActiveMistakes` 由"逐行相关子查询 + 嵌套 EXISTS"重写为"派生表 + LEFT JOIN 聚合"后，
 * 结果集必须与旧 SQL **逐列等价**。
 *
 * 证据形态：同一个真库里同一批行，先由**新 DAO 查询**读出，再在关库后用旧 SQL 原文
 * （[PRE_S17_OBSERVE_ACTIVE_MISTAKES_SQL]，取自 S17 之前的 `ProblemDao` 逐字副本）读一遍，
 * 逐行、逐列对照。夹具刻意覆盖三条分支：
 * - 无组织回执（A）：全部绑定与 KNOWLEDGE 分类都是"当前"；
 * - 有回执（B）：只有 accepted_at == MAX 且有同 taxonomy/accepted_at 分类的绑定算当前，
 *   分类侧同理反向要求存在同键绑定；
 * - ARCHIVED 条目（D）：两条查询都必须排除。
 * 同 unit 两条 ACTIVE 条目在 schema 上不可达（`error_book_entry.practice_unit_id` 唯一索引），
 * 因此不构造该形态。
 *
 * `GROUP_CONCAT` 的**顺序**在 SQLite 里没有契约（消费端 `toCatalogLabels()` 会切分、去重、排序），
 * 因此三列标签只对"切分后的集合"断言相等，其余列逐字比对。
 */
@RunWith(AndroidJUnit4::class)
class ProblemDaoAggregationEquivalenceInstrumentedTest {

    @Test
    fun rewrittenAggregationMatchesThePreS17QueryColumnByColumn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "s17-equivalence-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val store = StudyDatabaseFactory.open(context, databaseName) as RoomStudyDatabase
            store.seedStudyFacts(fixture())
            seedReceiptsClassificationsAndArchivedRow(store)
            store.commitProjection(memoryProjection())

            val newRows = store.database.problemDao().observeActiveMistakes().first()
                .map { row -> row.toSnapshot() }
            store.close()

            val oldRows = readWithPreS17Sql(context, databaseName)

            // 先钉住夹具本身（防止两条查询同时返回空而"等价"通过）
            assertEquals(
                listOf("entry-a", "entry-b", "entry-c"),
                newRows.map { it.entryId },
            )
            val a = newRows.single { it.entryId == "entry-a" }
            assertEquals(listOf("kc-a1", "kc-a2"), a.knowledgeNodeIds.tokens())
            assertEquals(listOf("旧知识点A", "知识点A1", "知识点A2"), a.knowledgeLabels.tokens())
            assertEquals(listOf("章节A"), a.chapterLabels.tokens())
            assertEquals(2, a.captureOccurrenceCount)
            assertEquals(REVIEW_AT, a.nextReviewAtEpochMillis)

            val b = newRows.single { it.entryId == "entry-b" }
            assertEquals(listOf("kc-b2", "kc-b3"), b.knowledgeNodeIds.tokens())
            // 知识点B3 有同 taxonomy/accepted_at 的绑定（b3）→ 算当前；
            // 旧知识点B（100）与知识点B4（无同键绑定）都被排除。
            assertEquals(listOf("知识点B", "知识点B3"), b.knowledgeLabels.tokens())
            assertEquals(listOf("章节B"), b.chapterLabels.tokens())
            assertEquals(0, b.captureOccurrenceCount)
            assertNull(b.nextReviewAtEpochMillis)

            val c = newRows.single { it.entryId == "entry-c" }
            // 无回执 → 绑定全部算当前；但 c 只有 CHAPTER 分类，没有 KNOWLEDGE 标签。
            assertEquals(listOf("kc-c1"), c.knowledgeNodeIds.tokens())
            assertNull(c.knowledgeLabels)
            assertEquals(listOf("章节C"), c.chapterLabels.tokens())

            // 逐行、逐列对照新旧两条 SQL
            assertEquals("行数必须一致", oldRows.size, newRows.size)
            assertEquals("排序必须一致", oldRows.map { it.entryId }, newRows.map { it.entryId })
            newRows.forEachIndexed { index, newRow ->
                assertSameShape(index, oldRows[index], newRow)
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    // ---- comparison ----

    private data class CatalogRowSnapshot(
        val entryId: String,
        val problemId: String,
        val problemRevisionId: String,
        val practiceUnitId: String,
        val sourceKey: String?,
        val subject: String,
        val title: String,
        val problemMarkdown: String,
        val status: String,
        val createdAtEpochMillis: Long,
        val updatedAtEpochMillis: Long,
        val estimatedSeconds: Int,
        val nextReviewAtEpochMillis: Long?,
        val retrievability: Double?,
        val knowledgeNodeIds: String?,
        val chapterLabels: String?,
        val knowledgeLabels: String?,
        val captureOccurrenceCount: Int,
    )

    /** 旧查询走 raw driver；列序与新查询的 SELECT 列表完全一致。 */
    private fun readWithPreS17Sql(context: Context, databaseName: String): List<CatalogRowSnapshot> {
        val connection = AndroidSQLiteDriver()
            .open(context.getDatabasePath(databaseName).absolutePath)
        try {
            val statement = connection.prepare(PRE_S17_OBSERVE_ACTIVE_MISTAKES_SQL)
            try {
                val rows = mutableListOf<CatalogRowSnapshot>()
                while (statement.step()) rows += statement.toSnapshot()
                return rows
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun SQLiteStatement.toSnapshot() = CatalogRowSnapshot(
        entryId = getText(0)!!,
        problemId = getText(1)!!,
        problemRevisionId = getText(2)!!,
        practiceUnitId = getText(3)!!,
        sourceKey = textOrNull(4),
        subject = getText(5)!!,
        title = getText(6)!!,
        problemMarkdown = getText(7)!!,
        status = getText(8)!!,
        createdAtEpochMillis = getLong(9),
        updatedAtEpochMillis = getLong(10),
        estimatedSeconds = getInt(11),
        nextReviewAtEpochMillis = if (isNull(12)) null else getLong(12),
        retrievability = if (isNull(13)) null else getDouble(13),
        captureOccurrenceCount = getInt(14),
        knowledgeNodeIds = textOrNull(15),
        chapterLabels = textOrNull(16),
        knowledgeLabels = textOrNull(17),
    )

    private fun SQLiteStatement.textOrNull(index: Int): String? =
        if (isNull(index)) null else getText(index)

    private fun MistakeRow.toSnapshot() =
        CatalogRowSnapshot(
            entryId = entryId,
            problemId = problemId,
            problemRevisionId = problemRevisionId,
            practiceUnitId = practiceUnitId,
            sourceKey = sourceKey,
            subject = subject,
            title = title,
            problemMarkdown = problemMarkdown,
            status = status,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            estimatedSeconds = estimatedSeconds,
            nextReviewAtEpochMillis = nextReviewAtEpochMillis,
            retrievability = retrievability,
            knowledgeNodeIds = knowledgeNodeIds,
            chapterLabels = chapterLabels,
            knowledgeLabels = knowledgeLabels,
            captureOccurrenceCount = captureOccurrenceCount,
        )

    private fun assertSameShape(
        index: Int,
        expected: CatalogRowSnapshot,
        actual: CatalogRowSnapshot,
    ) {
        val mismatches = linkedMapOf<String, String>()
        fun check(name: String, before: Any?, after: Any?) {
            if (before != after) mismatches[name] = "old=$before new=$after"
        }
        check("entryId", expected.entryId, actual.entryId)
        check("problemId", expected.problemId, actual.problemId)
        check("problemRevisionId", expected.problemRevisionId, actual.problemRevisionId)
        check("practiceUnitId", expected.practiceUnitId, actual.practiceUnitId)
        check("sourceKey", expected.sourceKey, actual.sourceKey)
        check("subject", expected.subject, actual.subject)
        check("title", expected.title, actual.title)
        check("problemMarkdown", expected.problemMarkdown, actual.problemMarkdown)
        check("status", expected.status, actual.status)
        check("createdAtEpochMillis", expected.createdAtEpochMillis, actual.createdAtEpochMillis)
        check("updatedAtEpochMillis", expected.updatedAtEpochMillis, actual.updatedAtEpochMillis)
        check("estimatedSeconds", expected.estimatedSeconds, actual.estimatedSeconds)
        check(
            "nextReviewAtEpochMillis",
            expected.nextReviewAtEpochMillis,
            actual.nextReviewAtEpochMillis,
        )
        check("retrievability", expected.retrievability, actual.retrievability)
        check("captureOccurrenceCount", expected.captureOccurrenceCount, actual.captureOccurrenceCount)
        // GROUP_CONCAT 顺序无契约：比对切分后的集合（消费端 toCatalogLabels 同口径）。
        check("knowledgeNodeIds", expected.knowledgeNodeIds.tokens(), actual.knowledgeNodeIds.tokens())
        check("chapterLabels", expected.chapterLabels.tokens(), actual.chapterLabels.tokens())
        check("knowledgeLabels", expected.knowledgeLabels.tokens(), actual.knowledgeLabels.tokens())
        assertEquals(
            "row[$index](${expected.entryId}) 新旧查询列不一致：$mismatches",
            emptyMap<String, String>(),
            mismatches,
        )
    }

    private fun String?.tokens(): List<String> = this
        ?.split('\u001F')
        ?.filter(String::isNotEmpty)
        ?.sorted()
        .orEmpty()

    // ---- fixtures ----

    private suspend fun seedReceiptsClassificationsAndArchivedRow(store: RoomStudyDatabase) {
        store.database.withWriteTransaction {
            // unit-a 的两条提交回执：capture_occurrence_count = 2（同 unit 两条 ACTIVE 条目都该读到 2）。
            // 回执对 problem_draft / canonical_source_asset 有 FK，连同父行一起补。
            listOf("a1", "a2").forEach { suffix ->
                executeSQL(
                    """
                    INSERT INTO canonical_source_asset (
                        source_asset_id, content_sha256, relative_path, mime_type,
                        byte_size, width, height, source_type, created_at_epoch_millis
                    ) VALUES (
                        'asset-commit-$suffix', '${"c".repeat(62)}$suffix',
                        'source-assets/$suffix.jpg', 'image/jpeg',
                        4096, 1200, 1600, 'PHOTO_PICKER', 1000
                    )
                    """.trimIndent(),
                )
                executeSQL(
                    """
                    INSERT INTO problem_draft (
                        draft_id, source_asset_id, origin, status,
                        current_revision_number, created_at_epoch_millis,
                        updated_at_epoch_millis, request_fingerprint
                    ) VALUES (
                        'draft-commit-$suffix', 'asset-commit-$suffix', 'LIBRARY', 'COMMITTED',
                        2, 1000, 4000, NULL
                    )
                    """.trimIndent(),
                )
                executeSQL(
                    """
                    INSERT INTO problem_draft_commit_receipt (
                        command_id, payload_fingerprint, draft_id, draft_revision_number,
                        problem_id, problem_revision_id, practice_unit_id,
                        error_book_entry_id, committed_at_epoch_millis
                    ) VALUES (
                        'commit-$suffix', 'fp-commit-$suffix', 'draft-commit-$suffix', 2,
                        'problem-a', 'revision-a', 'unit-a',
                        'entry-a', 4000
                    )
                    """.trimIndent(),
                )
            }
            // B 有一条组织回执（MAX accepted_at = 300）：只有 T/300 的绑定算当前
            executeSQL(
                """
                INSERT INTO problem_organization_receipt (
                    command_id, payload_fingerprint, problem_id, problem_revision_id,
                    practice_unit_id, classification_count, relation_count,
                    accepted_at_epoch_millis
                ) VALUES (
                    'receipt-b', 'fp-receipt-b', 'problem-b', 'revision-b',
                    'unit-b', 2, 0, 300
                )
                """.trimIndent(),
            )
            // A 行无回执：全部 KNOWLEDGE 分类都算当前（含 T3/50 这条"老"分类）
            listOf(
                ClassificationFixture("class-a-ch", "problem-a", "revision-a", "CHAPTER", "ch-a", "章节A", "tax-a", 100),
                ClassificationFixture("class-a-k1", "problem-a", "revision-a", "KNOWLEDGE", "k-a1", "知识点A1", "tax-a", 100),
                ClassificationFixture("class-a-k2", "problem-a", "revision-a", "KNOWLEDGE", "k-a2", "知识点A2", "tax-a", 200),
                ClassificationFixture("class-a-k3", "problem-a", "revision-a", "KNOWLEDGE", "k-a3", "旧知识点A", "tax-old", 50),
                ClassificationFixture("class-b-ch", "problem-b", "revision-b", "CHAPTER", "ch-b", "章节B", "tax-b", 100),
                ClassificationFixture("class-b-k", "problem-b", "revision-b", "KNOWLEDGE", "k-b", "知识点B", "tax-b", 300),
                ClassificationFixture("class-b-k3", "problem-b", "revision-b", "KNOWLEDGE", "k-b3", "知识点B3", "tax-b3", 300),
                ClassificationFixture("class-b-k4", "problem-b", "revision-b", "KNOWLEDGE", "k-b4", "知识点B4", "tax-b4", 300),
                ClassificationFixture("class-b-old", "problem-b", "revision-b", "KNOWLEDGE", "k-b-old", "旧知识点B", "tax-b", 100),
                ClassificationFixture("class-c-ch", "problem-c", "revision-c", "CHAPTER", "ch-c", "章节C", "tax-c", 100),
            ).forEach { fixture ->
                executeSQL(
                    """
                    INSERT INTO problem_classification_binding (
                        binding_id, problem_id, basis_revision_id, dimension, label_id,
                        display_name, taxonomy_version, acceptance_source,
                        accepted_at_epoch_millis
                    ) VALUES (
                        '${fixture.bindingId}', '${fixture.problemId}', '${fixture.revisionId}',
                        '${fixture.dimension}', '${fixture.labelId}', '${fixture.displayName}',
                        '${fixture.taxonomyVersion}', 'LOCAL_POLICY_ACCEPTED',
                        ${fixture.acceptedAt}
                    )
                    """.trimIndent(),
                )
            }
            executeSQL("UPDATE error_book_entry SET status = 'ARCHIVED' WHERE entry_id = 'entry-archived'")
        }
    }

    private data class ClassificationFixture(
        val bindingId: String,
        val problemId: String,
        val revisionId: String,
        val dimension: String,
        val labelId: String,
        val displayName: String,
        val taxonomyVersion: String,
        val acceptedAt: Long,
    )

    private fun fixture() = StudySeedBundle(
        problems = listOf(
            ProblemSeedRecord("problem-a", "fp-problem-a", "MATH", 1_000),
            ProblemSeedRecord("problem-b", "fp-problem-b", "MATH", 1_000),
            ProblemSeedRecord("problem-c", "fp-problem-c", "MATH", 1_000),
            ProblemSeedRecord("problem-archived", "fp-problem-archived", "MATH", 1_000),
        ),
        revisions = listOf(
            RevisionFixture("revision-a", "problem-a", "题 A"),
            RevisionFixture("revision-b", "problem-b", "题 B"),
            RevisionFixture("revision-c", "problem-c", "题 C"),
            RevisionFixture("revision-archived", "problem-archived", "题 D"),
        ),
        practiceUnits = listOf(
            UnitFixture("unit-a", "problem-a", "revision-a", "题 A"),
            UnitFixture("unit-b", "problem-b", "revision-b", "题 B"),
            UnitFixture("unit-c", "problem-c", "revision-c", "题 C"),
            UnitFixture("unit-archived", "problem-archived", "revision-archived", "题 D"),
        ),
        errorBookEntries = listOf(
            ErrorBookEntrySeedRecord(
                entryId = "entry-a",
                practiceUnitId = "unit-a",
                problemId = "problem-a",
                currentRevisionId = "revision-a",
                sourceKey = "capture:a",
                acceptedAtEpochMillis = 4_000,
                updatedAtEpochMillis = 4_000,
            ),
            ErrorBookEntrySeedRecord(
                entryId = "entry-b",
                practiceUnitId = "unit-b",
                problemId = "problem-b",
                currentRevisionId = "revision-b",
                sourceKey = "capture:b",
                acceptedAtEpochMillis = 3_000,
                updatedAtEpochMillis = 3_000,
            ),
            ErrorBookEntrySeedRecord(
                entryId = "entry-c",
                practiceUnitId = "unit-c",
                problemId = "problem-c",
                currentRevisionId = "revision-c",
                sourceKey = "capture:c",
                acceptedAtEpochMillis = 2_000,
                updatedAtEpochMillis = 2_000,
            ),
            ErrorBookEntrySeedRecord(
                entryId = "entry-archived",
                practiceUnitId = "unit-archived",
                problemId = "problem-archived",
                currentRevisionId = "revision-archived",
                sourceKey = "capture:archived",
                acceptedAtEpochMillis = 9_000,
                updatedAtEpochMillis = 9_000,
            ),
        ),
        knowledgeNodes = listOf(
            "kc-a1", "kc-a2", "kc-b1", "kc-b2", "kc-b3", "kc-c1",
        ).map { node ->
            KnowledgeNodeSeedRecord(
                knowledgeNodeId = node,
                stableCode = "math.test.$node",
                subject = "MATH",
                displayName = node,
                parentKnowledgeNodeId = null,
                taxonomyVersion = "taxonomy-s17",
                createdAtEpochMillis = 1_000,
            )
        },
        knowledgeBindings = listOf(
            KnowledgeBindingSeedRecord("binding-a1", "unit-a", "kc-a1", "revision-a", 1.0, "VERIFIED", "tax-a", 100),
            KnowledgeBindingSeedRecord("binding-a2", "unit-a", "kc-a2", "revision-a", 1.0, "VERIFIED", "tax-a", 200),
            KnowledgeBindingSeedRecord("binding-b1", "unit-b", "kc-b1", "revision-b", 1.0, "VERIFIED", "tax-b", 100),
            KnowledgeBindingSeedRecord("binding-b2", "unit-b", "kc-b2", "revision-b", 1.0, "VERIFIED", "tax-b", 300),
            KnowledgeBindingSeedRecord("binding-b3", "unit-b", "kc-b3", "revision-b", 1.0, "VERIFIED", "tax-b3", 300),
            KnowledgeBindingSeedRecord("binding-c1", "unit-c", "kc-c1", "revision-c", 1.0, "VERIFIED", "tax-c", 100),
        ),
    )

    private fun RevisionFixture(revisionId: String, problemId: String, title: String) =
        ProblemRevisionSeedRecord(
            revisionId = revisionId,
            problemId = problemId,
            revisionNumber = 1,
            title = title,
            problemMarkdown = "$title 的题面",
            answerSpecId = null,
            answerSpecSnapshot = null,
            answerVerificationStatus = StudyDbValue.VerificationStatus.UNKNOWN,
            sourceType = "CAPTURE_CONFIRMED",
            sourceReference = null,
            contentFingerprint = "f".repeat(64),
            createdAtEpochMillis = 1_000,
        )

    private fun UnitFixture(
        unitId: String,
        problemId: String,
        revisionId: String,
        title: String,
    ) = PracticeUnitSeedRecord(
        practiceUnitId = unitId,
        problemId = problemId,
        problemRevisionId = revisionId,
        unitKey = "whole-problem",
        unitKind = "WHOLE_PROBLEM",
        title = title,
        promptMarkdown = "$title 的题面",
        estimatedSeconds = 180,
        createdAtEpochMillis = 1_000,
    )

    /** Memory 行走真实投影提交（`observeActiveMistakes` 的 memory join 必须不动）。 */
    private fun memoryProjection() = ProjectionCommit(
        projectionName = "study-experience-v1",
        learnerId = "learner-s17",
        expectedPreviousCheckpoint = 0,
        expectedPreviousStateVersion = 0,
        mode = ProjectionCommitMode.FULL_REPLAY,
        knownLedgerHeadSequence = 0,
        consumedLedgerEvents = emptyList(),
        presentationProjectionStates = emptyMap(),
        expectedProjectorVersion = LearningProjector.VERSION,
        snapshot = LearnerSnapshot(
            learnerId = "learner-s17",
            problemMemoryStates = mapOf(
                "unit-a" to ProblemMemoryState(
                    practiceUnitId = "unit-a",
                    stabilityDays = 3.0,
                    difficulty = 5.0,
                    lastReviewedAtEpochMillis = REVIEW_AT - 1_000,
                    nextReviewAtEpochMillis = REVIEW_AT,
                    projectorVersion = LearningProjector.VERSION,
                    checkpointSequence = 0,
                ),
            ),
            checkpoint = ProjectionCheckpoint(
                lastSequence = 0,
                projectorVersion = LearningProjector.VERSION,
                projectedAtEpochMillis = REVIEW_AT,
            ),
            generatedAtEpochMillis = REVIEW_AT,
            freshness = LearnerSnapshotFreshness.CURRENT,
            projectionStatus = ProjectionStatus.CURRENT,
        ),
    )

    private companion object {
        const val REVIEW_AT = 1_700_086_400_000L

        /**
         * S17 之前 `ProblemDao.observeActiveMistakes` 的 SQL 原文（git `af530add` 的逐字副本）。
         * 这是"旧口径"的对照物，不得随实现漂移。
         */
        val PRE_S17_OBSERVE_ACTIVE_MISTAKES_SQL: String = """
        SELECT
            entry.entry_id,
            problem.problem_id,
            revision.revision_id AS problem_revision_id,
            unit.practice_unit_id,
            entry.source_key,
            problem.subject,
            unit.title,
            revision.problem_markdown,
            entry.status,
            entry.accepted_at_epoch_millis AS created_at_epoch_millis,
            entry.updated_at_epoch_millis AS updated_at_epoch_millis,
            unit.estimated_seconds,
            memory.next_review_at_epoch_millis,
            NULL AS retrievability,
            (
                SELECT COUNT(*)
                FROM problem_draft_commit_receipt AS receipt
                WHERE receipt.practice_unit_id = unit.practice_unit_id
            ) AS capture_occurrence_count,
            (
                SELECT GROUP_CONCAT(binding.knowledge_node_id, CHAR(31))
                FROM practice_unit_knowledge_binding AS binding
                INNER JOIN knowledge_node AS node
                    ON node.knowledge_node_id = binding.knowledge_node_id
                WHERE binding.practice_unit_id = unit.practice_unit_id
                  AND binding.basis_revision_id = revision.revision_id
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM problem_organization_receipt AS receipt
                          WHERE receipt.problem_id = problem.problem_id
                            AND receipt.problem_revision_id = revision.revision_id
                      )
                      OR (
                          binding.accepted_at_epoch_millis = (
                              SELECT MAX(receipt.accepted_at_epoch_millis)
                              FROM problem_organization_receipt AS receipt
                              WHERE receipt.problem_id = problem.problem_id
                                AND receipt.problem_revision_id = revision.revision_id
                          )
                          AND EXISTS (
                              SELECT 1
                              FROM problem_classification_binding AS classification
                              WHERE classification.problem_id = problem.problem_id
                                AND classification.basis_revision_id = revision.revision_id
                                AND classification.dimension = 'KNOWLEDGE'
                                AND classification.taxonomy_version =
                                    binding.taxonomy_version
                                AND classification.accepted_at_epoch_millis =
                                    binding.accepted_at_epoch_millis
                          )
                      )
                  )
            ) AS knowledge_node_ids,
            (
                SELECT GROUP_CONCAT(classification.display_name, CHAR(31))
                FROM problem_classification_binding AS classification
                WHERE classification.problem_id = problem.problem_id
                  AND classification.basis_revision_id = revision.revision_id
                  AND classification.dimension = 'CHAPTER'
            ) AS chapter_labels,
            (
                SELECT GROUP_CONCAT(classification.display_name, CHAR(31))
                FROM problem_classification_binding AS classification
                WHERE classification.problem_id = problem.problem_id
                  AND classification.basis_revision_id = revision.revision_id
                  AND classification.dimension = 'KNOWLEDGE'
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM problem_organization_receipt AS receipt
                          WHERE receipt.problem_id = problem.problem_id
                            AND receipt.problem_revision_id = revision.revision_id
                      )
                      OR (
                          classification.accepted_at_epoch_millis = (
                              SELECT MAX(receipt.accepted_at_epoch_millis)
                              FROM problem_organization_receipt AS receipt
                              WHERE receipt.problem_id = problem.problem_id
                                AND receipt.problem_revision_id = revision.revision_id
                          )
                          AND EXISTS (
                              SELECT 1
                              FROM knowledge_node AS node
                              INNER JOIN practice_unit_knowledge_binding AS binding
                                  ON binding.knowledge_node_id = node.knowledge_node_id
                                 AND binding.practice_unit_id = unit.practice_unit_id
                                 AND binding.basis_revision_id = revision.revision_id
                              WHERE binding.taxonomy_version =
                                  classification.taxonomy_version
                                AND binding.accepted_at_epoch_millis =
                                    classification.accepted_at_epoch_millis
                          )
                      )
                  )
            ) AS knowledge_labels
        FROM error_book_entry AS entry
        JOIN practice_unit AS unit
            ON unit.practice_unit_id = entry.practice_unit_id
        JOIN problem AS problem
            ON problem.problem_id = unit.problem_id
        JOIN problem_revision AS revision
            ON revision.revision_id = entry.current_revision_id
        LEFT JOIN learner_problem_memory_state AS memory
            ON memory.practice_unit_id = unit.practice_unit_id
           AND memory.projection_name = 'study-experience-v1'
        WHERE entry.status = 'ACTIVE'
        ORDER BY entry.updated_at_epoch_millis DESC, entry.entry_id ASC
        
        """.trimIndent()
    }
}
