package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.LearnerSnapshot
import com.tingyun.smartmistakebook.core.model.LearningLedgerEvent
import com.tingyun.smartmistakebook.core.model.ProjectionCheckpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W0-1/Q2 的重放版本守卫（内核修复路线图 Wave 0）。
 *
 * 三个用例对应 roadmap 的"旧版本 snapshot 提交被拒 / 版本一致时行为不变"两条，外加一条
 * **唯一合法的跨版本姿态**：归档之后才允许覆盖（这正是 `StudyProjectionDrainer` 走的路，
 * 归档写入在 `commitFullReplay` 里、调用 replay 之前）。
 *
 * 这里只钉纯函数契约（`LearningProjector` 不碰数据库）；"归档真的先落库"由 core:data 的
 * `ProjectionArchiveDrainerTest` 与仪器化的归档行读到。
 */
class ProjectionVersionGuardTest {
    private val projector = LearningProjector()
    private val emptyLedger = emptyList<LearningLedgerEvent>()

    private fun displacedSnapshot(projectorVersion: String) = LearnerSnapshot(
        learnerId = "learner-1",
        checkpoint = ProjectionCheckpoint(
            lastSequence = 1,
            projectorVersion = projectorVersion,
            projectedAtEpochMillis = 1_000,
        ),
        generatedAtEpochMillis = 1_000,
    )

    @Test
    fun `a foreign-version snapshot is refused unless it was archived first`() {
        val previous = displacedSnapshot("learning-core-v6(projector-v6,evidence-v4)")

        val failure = runCatching {
            projector.replay(
                learnerId = "learner-1",
                ledger = emptyLedger,
                displacedSnapshot = previous,
            )
        }

        assertTrue(
            "跨版本覆盖必须先归档：${failure.exceptionOrNull()}",
            failure.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            "错误信息要点名 archive 与空快照两条出路",
            failure.exceptionOrNull()!!.message!!.contains("projection_archive"),
        )
        // 挡住的是覆盖，不是别的：同版本路径照旧（见下一条用例）。
        assertEquals(
            LearningProjector.VERSION,
            projector.replay("learner-1", emptyLedger).snapshot.checkpoint.projectorVersion,
        )
    }

    @Test
    fun `an archived foreign-version snapshot may be replaced and the result carries the current version`() {
        val previous = displacedSnapshot("learning-core-v6(projector-v6,evidence-v4)")

        val replayed = projector.replay(
            learnerId = "learner-1",
            ledger = emptyLedger,
            displacedSnapshot = previous,
            displacedSnapshotArchived = true,
        ).snapshot

        assertEquals(LearningProjector.VERSION, replayed.checkpoint.projectorVersion)
    }

    @Test
    fun `a snapshot from the current version needs no declaration and replay is unchanged`() {
        val previous = displacedSnapshot(LearningProjector.VERSION)

        val withForeignCheck = projector.replay(
            learnerId = "learner-1",
            ledger = emptyLedger,
            displacedSnapshot = previous,
        ).snapshot
        val withoutDisplacedSnapshot = projector.replay("learner-1", emptyLedger).snapshot

        assertEquals(withoutDisplacedSnapshot, withForeignCheck)
    }
}
