package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.ProjectionArchiveRecord
import com.tingyun.smartmistakebook.core.domain.LearningProjector
import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson
import com.tingyun.smartmistakebook.core.model.ProblemMemoryState
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W0-1/Q2 的归档写入时机（内核修复路线图 Wave 0 ③）。
 *
 * 钉的是**顺序**：`commitFullReplay` 必须先把现投影落进 `projection_archive`，再让
 * `LearningProjector.replay` 覆盖它。顺序就是这条机制的全部价值——反过来的话，归档到的会是
 * 新值，"改数值可回退"落空，而失败是静默的（表里有行，只是那行不对）。
 *
 * 归档行的内容也在这一条里钉死：`snapshot_json` 必须能逐位解回被替换的那一份
 * （`LearnerSnapshotJson` 的往返），因为回退流程的起点就是这一列。
 */
class ProjectionArchiveDrainerTest {

    private val previousVersion = "learning-core-v6(projector-v6,evidence-v4)"
    private val fixedClock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val archivedAt = fixedClock.millis()

    @Test
    fun `a version-mismatched projection is archived before the replay replaces it`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val displaced = previousVersionSnapshot()
        database.publishDisplacedProjection(displaced)
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        val drained = requireNotNull(drainer.drain())

        assertEquals(
            "归档必须发生在覆盖之前（archive → commit）：${database.projectionWriteOrder}",
            listOf("archive", "commit"),
            database.projectionWriteOrder,
        )
        val archived = database.archivedProjectionSnapshots.single()
        assertEquals(previousVersion, archived.projectorVersion)
        assertEquals("study-experience-v1", archived.projectionName)
        assertEquals(LEARNER_ID, archived.learnerId)
        assertEquals("归档时刻取注入的时钟，不借用投影时刻", archivedAt, archived.archivedAtEpochMillis)
        assertEquals(
            "归档的必须是被替换的那一份（能逐位解回）：JSON 往返",
            displaced,
            LearnerSnapshotJson.decode(archived.snapshotJson),
        )
        assertEquals(
            "覆盖后的投影带当前二进制版本",
            LearningProjector.VERSION,
            drained.snapshot.checkpoint.projectorVersion,
        )
        assertNotEquals(previousVersion, drained.snapshot.checkpoint.projectorVersion)
    }

    @Test
    fun `an empty ledger with no stored snapshot archives nothing`() = runBlocking {
        val database = FakeStudyDatabasePort()
        val drainer = StudyProjectionDrainer(
            database = database,
            learnerId = LEARNER_ID,
            learningProjector = LearningProjector(),
            clock = fixedClock,
        )

        drainer.drain()

        assertTrue(
            "没有可归档的旧投影时不写归档行（空库不是'被替换'）：${database.projectionWriteOrder}",
            database.archivedProjectionSnapshots.isEmpty(),
        )
        assertTrue(database.projectionWriteOrder.none { it == "archive" })
    }

    private fun previousVersionSnapshot() = LearnerSnapshot(
        learnerId = LEARNER_ID,
        problemMemoryStates = mapOf(
            "unit-previous-projector-version" to ProblemMemoryState(
                practiceUnitId = "unit-previous-projector-version",
                stabilityDays = 2.0,
                difficulty = 9.0,
                lastReviewedAtEpochMillis = 10L * 86_400_000L,
                nextReviewAtEpochMillis = 12L * 86_400_000L,
                projectorVersion = previousVersion,
                checkpointSequence = 1,
            ),
        ),
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = previousVersion,
            projectedAtEpochMillis = 10L * 86_400_000L,
        ),
        knownLedgerHeadSequence = 1,
        generatedAtEpochMillis = 10L * 86_400_000L,
    )

    private companion object {
        const val LEARNER_ID = "learner:local"
    }
}
