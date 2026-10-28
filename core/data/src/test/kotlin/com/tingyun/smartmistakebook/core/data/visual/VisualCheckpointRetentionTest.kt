package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VisualCheckpointRetention] 的 JVM 门（批次 30 T4 / D-03 半场：checkpoint 初始+最新两份、
 * 配额不足拒提交）。
 *
 * **它消灭的具体失败**：改前"每 program 只保留初始+最新两个 checkpoint"只是契约散文——
 * 没有任何可执行的口径说明"哪些行能淘汰、淘汰后还算不算超限、超限时是删数据还是拒绝"。
 * 结果是二选一的两头错：要么永不淘汰（缓存无限长），要么在配额压力下删掉被消息/题目固定的
 * 快照（用户看到过的图消失）。本门把三件事钉成策略函数：
 *
 * 1. **INITIAL 永不动**（首个 LIVE 提交写入的重建基线）；
 * 2. **只有"未被固定且可淘汰"的行才进淘汰集**；被 `tutor_message_visual.current_snapshot_id`
 *    或 `problem_revision_visual.snapshot_id` 固定（`pinned = true`）的行一律保留；未知
 *    `checkpoint_kind` fail-closed 保留；
 * 3. **配额只做提交门，不做静默删除**：淘汰候选从预算里扣除后仍超限 → 返回
 *    [VisualCheckpointPlan.Rejected]（有界原因），**不删任何用户事实**（契约 §8.1）。
 *
 * 语义裁定（登记给 T3）：
 * - `LATEST` 是"当前最新 checkpoint"的**可变标记**，同一 program 至多一个；新 checkpoint
 *   到来时旧标记行一律 demote 为 `NULL`（无论是否被固定——被固定的旧行仍因 pinned 保留）；
 * - "LIVE 不可回退"约束的是 **status 语义**（不得把 LIVE 改回 STAGING、不得改写内容），
 *   **不是"LIVE 行不可删除"**；契约 §8.1 原文允许"淘汰未固定快照/预览"。两者是不同轴。
 *
 * 设备/集成面（本环境不运行 Gradle/adb）：demote/淘汰的 SQL 落点是 T3 的 D-02 写入事务
 * （`UPDATE … checkpoint_kind` + 删除未固定行），本轨只给判定；设备用例见 T3 的 DAO 仪器化
 * 矩阵（设计 §3.6"重复提交/取消"两行）。本文件执行结果登记为 UNVERIFIED。
 */
class VisualCheckpointRetentionTest {

    private fun row(
        id: String,
        kind: String? = null,
        pinned: Boolean = false,
        bytes: Long = 100L,
    ): VisualCheckpointRow = VisualCheckpointRow(
        snapshotId = id,
        kind = kind,
        pinned = pinned,
        storedBytes = bytes,
    )

    private fun retained(plan: VisualCheckpointPlan): VisualCheckpointPlan.Retained {
        assertTrue("expected a retained plan, got $plan", plan is VisualCheckpointPlan.Retained)
        return plan as VisualCheckpointPlan.Retained
    }

    private fun rejected(plan: VisualCheckpointPlan): VisualCheckpointPlan.Rejected {
        assertTrue("expected a rejected plan, got $plan", plan is VisualCheckpointPlan.Rejected)
        return plan as VisualCheckpointPlan.Rejected
    }

    // ------------------------------------------------------------ 初始 + 最新

    @Test
    fun `the first checkpoint is the initial one`() {
        assertEquals(
            TutorVisualCheckpointKind.INITIAL,
            VisualCheckpointRetention.nextCheckpointKind(emptyList()),
        )
    }

    @Test
    fun `later checkpoints are the latest one`() {
        val existing = listOf(row("snap-initial", TutorVisualCheckpointKind.INITIAL))
        assertEquals(
            TutorVisualCheckpointKind.LATEST,
            VisualCheckpointRetention.nextCheckpointKind(existing),
        )
    }

    @Test
    fun `a new checkpoint demotes the previous latest and keeps the initial`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-latest-old", TutorVisualCheckpointKind.LATEST, bytes = 200),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-latest-new",
                incomingStoredBytes = 300,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 300,
            ),
        )
        assertEquals(TutorVisualCheckpointKind.LATEST, plan.incomingKind)
        assertEquals(listOf("snap-latest-old"), plan.demoteToIntermediate)
        assertEquals(listOf("snap-latest-old"), plan.evictUnpinnedSnapshotIds)
        assertEquals(400L, plan.retainedBytesAfterEviction) // initial + incoming
    }

    @Test
    fun `an unpinned intermediate is evicted but the initial never is`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-middle", kind = null, pinned = false, bytes = 800),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 900,
            ),
        )
        assertEquals(listOf("snap-middle"), plan.evictUnpinnedSnapshotIds)
        assertEquals(300L, plan.retainedBytesAfterEviction)
    }

    @Test
    fun `a pinned intermediate is retained and not evicted`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-pinned", kind = null, pinned = true, bytes = 800),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 900,
            ),
        )
        assertTrue(plan.evictUnpinnedSnapshotIds.isEmpty())
        assertEquals(1100L, plan.retainedBytesAfterEviction)
    }

    @Test
    fun `a pinned previous latest loses the marker but is not evicted`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-latest-old", TutorVisualCheckpointKind.LATEST, pinned = true, bytes = 200),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-latest-new",
                incomingStoredBytes = 300,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 300,
            ),
        )
        // 标记只有一个：旧 LATEST 让位；但它是被固定的事实，行保留。
        assertEquals(listOf("snap-latest-old"), plan.demoteToIntermediate)
        assertTrue(plan.evictUnpinnedSnapshotIds.isEmpty())
        assertEquals(600L, plan.retainedBytesAfterEviction) // initial + pinned + incoming
    }

    @Test
    fun `an unknown checkpoint kind is retained fail closed`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-weird", kind = "ABANDONED", pinned = false, bytes = 800),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 900,
            ),
        )
        assertTrue(
            "an unknown kind must not be treated as an evictable intermediate",
            plan.evictUnpinnedSnapshotIds.isEmpty(),
        )
        assertEquals(1100L, plan.retainedBytesAfterEviction)
    }

    // ------------------------------------------------------------ 配额门

    @Test
    fun `the per-program budget rejects when fixed bytes alone exceed it`() {
        val plan = rejected(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = listOf(row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 900)),
                availableDiskBytes = 10_000,
                globalStoredBytes = 900,
                policy = VisualCheckpointRetentionPolicy(perProgramBudgetBytes = 1_000),
            ),
        )
        assertEquals(VisualCheckpointRejection.PER_PROGRAM_BUDGET, plan.reason)
        assertTrue(plan.diagnostic.isNotBlank())
    }

    @Test
    fun `the per-program budget counts bytes after evicting unpinned rows`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-middle", kind = null, pinned = false, bytes = 800),
        )
        val plan = retained(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 900,
                policy = VisualCheckpointRetentionPolicy(perProgramBudgetBytes = 1_000),
            ),
        )
        assertEquals(300L, plan.retainedBytesAfterEviction)
    }

    @Test
    fun `the global budget rejects only after program-local eviction`() {
        val existing = listOf(
            row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100),
            row("snap-middle", kind = null, pinned = false, bytes = 800),
        )
        val relaxed = VisualCheckpointRetention.plan(
            incomingSnapshotId = "snap-new",
            incomingStoredBytes = 200,
            existing = existing,
            availableDiskBytes = 10_000,
            globalStoredBytes = 1_100,
            policy = VisualCheckpointRetentionPolicy(globalBudgetBytes = 1_000),
        )
        assertTrue("global budget should count the post-eviction total", relaxed is VisualCheckpointPlan.Retained)

        val tight = rejected(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 10_000,
                globalStoredBytes = 1_100,
                policy = VisualCheckpointRetentionPolicy(globalBudgetBytes = 400),
            ),
        )
        assertEquals(VisualCheckpointRejection.GLOBAL_BUDGET, tight.reason)
    }

    @Test
    fun `insufficient disk rejects the commit and never proposes deleting a fact`() {
        val existing = listOf(row("snap-initial", TutorVisualCheckpointKind.INITIAL, bytes = 100))
        val plan = rejected(
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 200,
                existing = existing,
                availableDiskBytes = 199,
                globalStoredBytes = 100,
            ),
        )
        assertEquals(VisualCheckpointRejection.DISK_FULL, plan.reason)
        assertTrue(plan.diagnostic.length <= VisualCheckpointRetention.MAX_DIAGNOSTIC_CHARS)

        // 边界：可用空间恰好等于待写字节时允许提交。
        val boundary = VisualCheckpointRetention.plan(
            incomingSnapshotId = "snap-new",
            incomingStoredBytes = 200,
            existing = existing,
            availableDiskBytes = 200,
            globalStoredBytes = 100,
        )
        assertTrue(boundary is VisualCheckpointPlan.Retained)
    }

    // ------------------------------------------------------------ 参数与计量

    @Test
    fun `negative or duplicated rows are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = -1,
                existing = emptyList(),
                availableDiskBytes = 1,
                globalStoredBytes = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 1,
                existing = listOf(row("snap-a"), row("snap-a")),
                availableDiskBytes = 1,
                globalStoredBytes = 200,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-a",
                incomingStoredBytes = 1,
                existing = listOf(row("snap-a")),
                availableDiskBytes = 1,
                globalStoredBytes = 100,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            // 全局总量必须覆盖本 program 的既有行（否则"扣除淘汰"会算出负数）。
            VisualCheckpointRetention.plan(
                incomingSnapshotId = "snap-new",
                incomingStoredBytes = 1,
                existing = listOf(row("snap-a", bytes = 100)),
                availableDiskBytes = 1,
                globalStoredBytes = 50,
            )
        }
    }

    @Test
    fun `stored bytes are the utf8 sizes of state scene and draw commands`() {
        assertEquals(3L, visualCheckpointStoredBytes(stateJson = "中", sceneJson = "", drawCommandsJson = ""))
        assertEquals(9L, visualCheckpointStoredBytes(stateJson = "中", sceneJson = "文", drawCommandsJson = "字"))
        assertEquals(0L, visualCheckpointStoredBytes("", "", ""))
    }
}
