package com.tingyun.smartmistakebook.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 4E.2 合并：真实 Room 事务里写关联 + 归档重复条目，且重复/自合并不产生第二条。 */
@RunWith(AndroidJUnit4::class)
class MistakeMergeInstrumentedTest {
    private lateinit var store: StudyDatabasePort

    @Before
    fun setUp() {
        store = StudyDatabaseFactory.openInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        store.close()
    }

    private suspend fun seed() {
        store.seedStudyFacts(
            StudySeedBundle(
                problems = listOf(
                    problem("problem-main", "fp-main"),
                    problem("problem-dup", "fp-dup"),
                ),
                revisions = listOf(
                    revision("revision-main", "problem-main", "主记录", "fp-main-r1"),
                    revision("revision-dup", "problem-dup", "重复记录", "fp-dup-r1"),
                ),
                practiceUnits = listOf(
                    unit("unit-main", "problem-main", "revision-main"),
                    unit("unit-dup", "problem-dup", "revision-dup"),
                ),
                errorBookEntries = listOf(
                    entry("entry-main", "unit-main", "problem-main", "revision-main"),
                    entry("entry-dup", "unit-dup", "problem-dup", "revision-dup"),
                ),
            ),
        )
    }

    @Test
    fun mergeArchivesTheDuplicateAndWritesOneRelation() = runBlocking {
        seed()

        assertTrue(store.mergeMistakeEntry("entry-dup", "entry-main", "merge-rel-1", 2_000))

        val room = store as RoomStudyDatabase
        assertEquals(
            StudyDbValue.ErrorBookStatus.ARCHIVED,
            room.database.mistakeDetailDao().findEntryMergeRow("entry-dup")?.status,
        )
        assertEquals(
            StudyDbValue.ErrorBookStatus.ACTIVE,
            room.database.mistakeDetailDao().findEntryMergeRow("entry-main")?.status,
        )
        var relationCount = 0
        room.database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(
                "SELECT COUNT(*) FROM problem_relation WHERE relation_id = ?",
            ) { statement ->
                statement.bindText(1, "merge-rel-1")
                statement.step()
                relationCount = statement.getInt(0)
            }
        }
        assertEquals(1, relationCount)
    }

    @Test
    fun aSecondMergeOfTheSameArchivedEntryIsRefused() = runBlocking {
        seed()
        assertTrue(store.mergeMistakeEntry("entry-dup", "entry-main", "merge-rel-1", 2_000))
        assertFalse(store.mergeMistakeEntry("entry-dup", "entry-main", "merge-rel-2", 3_000))
        assertTrue(
            runCatching { store.mergeMistakeEntry("entry-main", "entry-main", "merge-rel-3", 3_000) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    private fun problem(id: String, fingerprint: String) = ProblemSeedRecord(
        problemId = id,
        canonicalFingerprint = fingerprint,
        subject = "MATH",
        createdAtEpochMillis = 1_000,
    )

    private fun revision(id: String, problemId: String, title: String, fingerprint: String) =
        ProblemRevisionSeedRecord(
            revisionId = id,
            problemId = problemId,
            revisionNumber = 1,
            title = title,
            problemMarkdown = "题面 $title",
            questionDocumentSnapshot = null,
            answerSpecId = null,
            answerSpecSnapshot = null,
            answerVerificationStatus = "USER_ASSERTED",
            sourceType = "CAPTURE",
            sourceReference = null,
            contentFingerprint = fingerprint,
            createdAtEpochMillis = 1_000,
        )

    private fun unit(id: String, problemId: String, revisionId: String) = PracticeUnitSeedRecord(
        practiceUnitId = id,
        problemId = problemId,
        problemRevisionId = revisionId,
        unitKey = id,
        unitKind = "SINGLE",
        title = "单元 $id",
        promptMarkdown = "题面",
        estimatedSeconds = 60,
        createdAtEpochMillis = 1_000,
    )

    private fun entry(
        id: String,
        unitId: String,
        problemId: String,
        revisionId: String,
    ) = ErrorBookEntrySeedRecord(
        entryId = id,
        practiceUnitId = unitId,
        problemId = problemId,
        currentRevisionId = revisionId,
        sourceKey = null,
        status = StudyDbValue.ErrorBookStatus.ACTIVE,
        acceptedAtEpochMillis = 1_000,
        updatedAtEpochMillis = 1_000,
    )
}
