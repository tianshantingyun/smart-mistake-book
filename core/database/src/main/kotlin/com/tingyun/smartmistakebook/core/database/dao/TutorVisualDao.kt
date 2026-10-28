package com.tingyun.smartmistakebook.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.tingyun.smartmistakebook.core.database.ImmutablePayloadConflictException
import com.tingyun.smartmistakebook.core.database.TutorVisualStatus
import com.tingyun.smartmistakebook.core.database.entity.ProblemRevisionVisualEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorMessageVisualEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualProgramEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualSnapshotEntity

/**
 * 快照状态值（`tutor_visual_snapshot.status`，schema 70）。
 *
 * 单源在 [TutorVisualStatus]（跨层可见）；本别名只为保住 D-01 DAO 测试的既有 import 形态。
 */
internal object TutorVisualSnapshotStatus {
    const val STAGING = TutorVisualStatus.STAGING
    const val LIVE = TutorVisualStatus.LIVE
}

/** [TutorVisualDao.checkpointSummaries] 的 Room 投影（列名与查询别名逐字一致）。 */
internal data class TutorVisualCheckpointProjection(
    val snapshotId: String,
    val checkpointKind: String?,
    /** 0/1：被消息当前指针或保存题引用即为 1。 */
    val pinned: Int,
    val storedBytes: Long,
)

/**
 * 视觉四表（批次 30 / D-01）的**原始读写口**。这里只做"把行写进去 / 读出来 / 用命中行数判赢"，
 * 不含程序身份的派生（那在 D-02 的身份层）、不含配额预检、不含 UX 决策。
 *
 * 三条结构性门（都是 SQL 本身写不进去，不是运行期再判一次）：
 * 1. **程序不可变**：[insertProgram] 用 `IGNORE`；同 id 不同载荷由调用方回读逐列比对后抛
 *    [ImmutablePayloadConflictException]——**没有** `ON CONFLICT DO UPDATE`，那正是"原位
 *    覆盖已被引用的源码"。
 * 2. **只有 LIVE 快照可被消息引用**：[insertMessageVisualIfLive] 是
 *    `INSERT … SELECT … WHERE status = 'LIVE'`。非 LIVE（或不存在）时 SQL 一行也不写，
 *    随后回读为空 ⇒ 抛冲突。消灭"消息引用了半成品快照"。
 * 3. **指针 CAS 以命中行数为准**：[repointMessageVisual] 命中 0 行 = 期望版本已过期或目标
 *    不是 LIVE。**不**靠回读 `revision == expected + 1` 判成功——并发下输家也会读到 +1
 *    （`CleanRedrawTaskDao` 已记载同一陷阱，`CleanRedrawTaskDao.kt:281-287`）。
 *
 * 重复提交的幂等靠**快照唯一索引** `(program_id, state_sha256, draw_commands_sha256)`：
 * 同内容 checkpoint 的 `INSERT OR IGNORE` 不动行数，调用方回读已有行。
 */
@Dao
internal abstract class TutorVisualDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProgram(entity: TutorVisualProgramEntity): Long

    @Query("SELECT * FROM tutor_visual_program WHERE program_id = :programId LIMIT 1")
    abstract suspend fun findProgram(programId: String): TutorVisualProgramEntity?

    /** 幂等/回滚断言用：同一内容身份只应有一行。 */
    @Query("SELECT COUNT(*) FROM tutor_visual_program WHERE program_id = :programId")
    abstract suspend fun countPrograms(programId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSnapshot(entity: TutorVisualSnapshotEntity): Long

    @Query("SELECT * FROM tutor_visual_snapshot WHERE snapshot_id = :snapshotId LIMIT 1")
    abstract suspend fun findSnapshot(snapshotId: String): TutorVisualSnapshotEntity?

    /** 内容幂等键（`UNIQUE(program_id, state_sha256, draw_commands_sha256)`）的回读口。 */
    @Query(
        """
        SELECT * FROM tutor_visual_snapshot
        WHERE program_id = :programId
          AND state_sha256 = :stateSha256
          AND draw_commands_sha256 = :drawCommandsSha256
        LIMIT 1
        """,
    )
    abstract suspend fun findSnapshotByContent(
        programId: String,
        stateSha256: String,
        drawCommandsSha256: String,
    ): TutorVisualSnapshotEntity?

    /** 幂等/回滚断言用：一个 program 下应只有内容唯一的快照行。 */
    @Query("SELECT COUNT(*) FROM tutor_visual_snapshot WHERE program_id = :programId")
    abstract suspend fun countSnapshots(programId: String): Int

    /**
     * checkpoint 保留策略的读侧（批次 30 / D-05）：本 program 全部快照行的
     * `(snapshot_id, checkpoint_kind, pinned, stored_bytes)`。
     *
     * `stored_bytes` 与 Kotlin 侧 `visualCheckpointStoredBytes` 同口径：三列 JSON 的 **UTF-8
     * 字节和**，所以用 `length(CAST(x AS BLOB))` 而不是 `length(x)`（后者数的是字符）。
     * `pinned` 与删除 SQL 用同一组引用（消息当前指针 + 保存题引用）。
     */
    @Query(
        """
        SELECT snapshot_id AS snapshotId,
               checkpoint_kind AS checkpointKind,
               CASE WHEN EXISTS (
                        SELECT 1 FROM tutor_message_visual AS link
                        WHERE link.current_snapshot_id = tutor_visual_snapshot.snapshot_id
                    )
                    OR EXISTS (
                        SELECT 1 FROM problem_revision_visual AS revision
                        WHERE revision.snapshot_id = tutor_visual_snapshot.snapshot_id
                    )
                    THEN 1 ELSE 0 END AS pinned,
               (length(CAST(state_json AS BLOB))
                 + length(CAST(scene_json AS BLOB))
                 + length(CAST(draw_commands_json AS BLOB))) AS storedBytes
        FROM tutor_visual_snapshot
        WHERE program_id = :programId
        ORDER BY snapshot_id ASC
        """,
    )
    abstract suspend fun checkpointSummaries(programId: String): List<TutorVisualCheckpointProjection>

    /** 全库视觉快照的派生字节总量（与 [checkpointSummaries] 同口径）。 */
    @Query(
        """
        SELECT COALESCE(SUM(
                   length(CAST(state_json AS BLOB))
                 + length(CAST(scene_json AS BLOB))
                 + length(CAST(draw_commands_json AS BLOB))), 0)
        FROM tutor_visual_snapshot
        """,
    )
    abstract suspend fun totalCheckpointStoredBytes(): Long

    /**
     * 旧 `LATEST` 降为可回收中间态（NULL）。**只改角色列**，不动内容、状态与引用；
     * `INITIAL` 永不降级（WHERE 已把 `LATEST` 写死，且调用方传的集合来自策略的 demote 列表）。
     */
    @Query(
        """
        UPDATE tutor_visual_snapshot
        SET checkpoint_kind = NULL
        WHERE program_id = :programId
          AND snapshot_id IN (:snapshotIds)
          AND checkpoint_kind = :latestKind
        """,
    )
    abstract suspend fun demoteLatestCheckpoints(
        programId: String,
        snapshotIds: List<String>,
        latestKind: String,
    ): Int

    /**
     * 淘汰未固定、非 INITIAL 的派生缓存（批次 30 / D-05）。**SQL 侧重查** pin 与 kind：
     * 策略在事务外算出候选集，pin 可能并发变化；这里再按"仍未被消息/题面引用且 kind ∈
     * {NULL, LATEST}"过滤一次。`INITIAL` 与任何被固定行一行都不删。
     */
    @Query(
        """
        DELETE FROM tutor_visual_snapshot
        WHERE snapshot_id IN (:snapshotIds)
          AND (checkpoint_kind IS NULL OR checkpoint_kind = :latestKind)
          AND NOT EXISTS (
              SELECT 1 FROM tutor_message_visual AS link
              WHERE link.current_snapshot_id = tutor_visual_snapshot.snapshot_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM problem_revision_visual AS revision
              WHERE revision.snapshot_id = tutor_visual_snapshot.snapshot_id
          )
        """,
    )
    abstract suspend fun evictUnpinnedCheckpoints(
        snapshotIds: List<String>,
        latestKind: String,
    ): Int

    /**
     * 启动对账（设计 §3.2 / §3.3）：只删**仍为 STAGING、租约已过期、且不被消息/题面引用**的
     * 快照。LIVE 是终态，本查询在 `status = STAGING` 上就把它排除；`NOT EXISTS` 是第二道门
     * （消息 link 与题面 pin 的 INSERT 都只认 LIVE；这两个 NOT EXISTS 是结构门的纵深防御，
     * 防止历史库或绕过 DAO 的写入留下"STAGING 被引用"的僵尸行）。
     */
    @Query(
        """
        DELETE FROM tutor_visual_snapshot
        WHERE status = :stagingStatus
          AND created_at_epoch_millis <= :staleBeforeEpochMillis
          AND NOT EXISTS (
              SELECT 1 FROM tutor_message_visual AS link
              WHERE link.current_snapshot_id = tutor_visual_snapshot.snapshot_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM problem_revision_visual AS revision
              WHERE revision.snapshot_id = tutor_visual_snapshot.snapshot_id
          )
        """,
    )
    abstract suspend fun deleteStaleStagingSnapshots(
        stagingStatus: String,
        staleBeforeEpochMillis: Long,
    ): Int

    /** 冷恢复/对账取"这个程序最新的 LIVE 快照"。 */
    @Query(
        """
        SELECT * FROM tutor_visual_snapshot
        WHERE program_id = :programId AND status = :liveStatus
        ORDER BY promoted_at_epoch_millis DESC, snapshot_id DESC
        LIMIT 1
        """,
    )
    abstract suspend fun findLatestLiveSnapshot(
        programId: String,
        liveStatus: String,
    ): TutorVisualSnapshotEntity?

    /** 启动对账：某状态且建立早于阈值的快照（STAGING 僵尸）。 */
    @Query(
        """
        SELECT * FROM tutor_visual_snapshot
        WHERE status = :status AND created_at_epoch_millis <= :staleBeforeEpochMillis
        ORDER BY created_at_epoch_millis ASC, snapshot_id ASC
        """,
    )
    abstract suspend fun findStaleSnapshots(
        status: String,
        staleBeforeEpochMillis: Long,
    ): List<TutorVisualSnapshotEntity>

    /**
     * `STAGING → LIVE` 的比较交换。返回命中行数：0 = 行不存在或已不是 STAGING
     * （重复提升/并发提升由赢家决定）。**没有** `LIVE → *` 的反向更新。
     */
    @Query(
        """
        UPDATE tutor_visual_snapshot
        SET status = :liveStatus,
            promoted_at_epoch_millis = :promotedAtEpochMillis,
            owner_request_id = NULL
        WHERE snapshot_id = :snapshotId
          AND status = :stagingStatus
        """,
    )
    abstract suspend fun promoteSnapshot(
        snapshotId: String,
        stagingStatus: String,
        liveStatus: String,
        promotedAtEpochMillis: Long,
    ): Int

    /**
     * 把快照的 STAGING 租约转给本 owner；命中 0 行 = 别人已持有/已 LIVE。
     * 只改 `owner_request_id`，不动任何内容列。
     */
    @Query(
        """
        UPDATE tutor_visual_snapshot
        SET owner_request_id = :nextOwnerRequestId
        WHERE snapshot_id = :snapshotId
          AND status = :stagingStatus
          AND owner_request_id IS :expectedOwnerRequestId
        """,
    )
    abstract suspend fun reassignStagingOwner(
        snapshotId: String,
        stagingStatus: String,
        expectedOwnerRequestId: String?,
        nextOwnerRequestId: String,
    ): Int

    /**
     * **SQL 级结构门**：只有 LIVE 快照能被消息引用。
     *
     * `INSERT … SELECT … WHERE status = 'LIVE'` —— 非 LIVE 时一行也写不进；命中与否由
     * [linkMessageVisual] 回读判定。`revision` 从 1 起（指针 CAS 的期望版本）。
     */
    @Query(
        """
        INSERT INTO tutor_message_visual
            (message_id, ordinal, program_id, current_snapshot_id, revision,
             ui_state_json, form_digest, created_at_epoch_millis, updated_at_epoch_millis)
        SELECT :messageId, :ordinal, :programId, snapshot_id, 1,
               :uiStateJson, :formDigest, :nowEpochMillis, :nowEpochMillis
        FROM tutor_visual_snapshot
        WHERE snapshot_id = :snapshotId AND status = :liveStatus
        """,
    )
    abstract suspend fun insertMessageVisualIfLive(
        messageId: String,
        ordinal: Int,
        programId: String,
        snapshotId: String,
        uiStateJson: String?,
        formDigest: String?,
        nowEpochMillis: Long,
        liveStatus: String,
    )

    @Query(
        """
        SELECT * FROM tutor_message_visual
        WHERE message_id = :messageId
        ORDER BY ordinal ASC
        """,
    )
    abstract suspend fun findMessageVisuals(messageId: String): List<TutorMessageVisualEntity>

    @Query(
        """
        SELECT * FROM tutor_message_visual
        WHERE message_id = :messageId AND ordinal = :ordinal
        LIMIT 1
        """,
    )
    abstract suspend fun findMessageVisual(messageId: String, ordinal: Int): TutorMessageVisualEntity?

    /**
     * 指针 CAS（换图/重渲染）；命中 0 行 = 期望版本过期、目标不是 LIVE，或目标快照属于
     * **另一个 program**。
     *
     * 第三道门（批次 30 复核修复）：`tutor_message_visual` 对 `current_snapshot_id` 只有单列
     * 外键，SQLite 无法表达 `(program_id, snapshot_id)` 复合外键；若不显式比对
     * `snapshot.program_id = tutor_message_visual.program_id`，就能把消息的图指针重指到另一个
     * program 的 LIVE 快照，行内 `program_id` 与快照 `program_id` 静默不一致，恢复/导出会按
     * 两个不同 program 取事实。这里把该一致性变成 CAS 的结构性条件。
     */
    @Query(
        """
        UPDATE tutor_message_visual
        SET current_snapshot_id = :nextSnapshotId,
            revision = revision + 1,
            updated_at_epoch_millis = :nowEpochMillis
        WHERE message_id = :messageId AND ordinal = :ordinal
          AND revision = :expectedRevision
          AND EXISTS (
              SELECT 1 FROM tutor_visual_snapshot AS snapshot
              WHERE snapshot.snapshot_id = :nextSnapshotId
                AND snapshot.status = :liveStatus
                AND snapshot.program_id = tutor_message_visual.program_id
          )
        """,
    )
    abstract suspend fun repointMessageVisual(
        messageId: String,
        ordinal: Int,
        expectedRevision: Int,
        nextSnapshotId: String,
        nowEpochMillis: Long,
        liveStatus: String,
    ): Int

    /**
     * 首张可见图的提交：link 与"只引用 LIVE"的门在**同一条 SQL** 里；回读为空即冲突。
     *
     * 为什么不加第二次补救写：两次写之间进程死亡会留下"消息有、图无且无任何记录说明图本该来"
     * 的中间态。调用方（D-02）把消息/正文/trace/link 放进同一个 `@Transaction`。
     */
    @Transaction
    open suspend fun linkMessageVisual(
        messageId: String,
        ordinal: Int,
        programId: String,
        snapshotId: String,
        uiStateJson: String?,
        formDigest: String?,
        nowEpochMillis: Long,
    ): TutorMessageVisualEntity {
        // 重复提交（同一 (message_id, ordinal) + 同一 program/snapshot）是**幂等读回**，
        // 不是主键冲突：调用方重放一条已经提交成功的助手轮次时，link 已经在了。
        // 指向另一张快照则不是重放——换图只能走指针 CAS，提交路径不许静默改写历史。
        val existing = findMessageVisual(messageId, ordinal)
        if (existing != null) {
            if (existing.programId != programId || existing.currentSnapshotId != snapshotId) {
                throw ImmutablePayloadConflictException(
                    "tutor_message_visual",
                    "$messageId#$ordinal",
                )
            }
            return existing
        }
        insertMessageVisualIfLive(
            messageId = messageId,
            ordinal = ordinal,
            programId = programId,
            snapshotId = snapshotId,
            uiStateJson = uiStateJson,
            formDigest = formDigest,
            nowEpochMillis = nowEpochMillis,
            liveStatus = TutorVisualSnapshotStatus.LIVE,
        )
        return findMessageVisual(messageId, ordinal)
            ?: throw ImmutablePayloadConflictException(
                "tutor_message_visual",
                "$messageId#$ordinal",
            )
    }

    /**
     * **SQL 级结构门（题面固定引用）**：只有 LIVE 快照能被保存题 pin。
     *
     * 与 [insertMessageVisualIfLive] 同形。批次 30 复核修复前这里是普通 `@Insert(ABORT)`：
     * 被取消/中断的 STAGING 快照一旦被题面 pin，对账 SQL 又显式排除"被题面引用"的行，于是它
     * 永远不会被回收，也无法被恢复重放（restore 拒非 LIVE）——永久只读 + 永久占位。
     * 现在非 LIVE（或快照不属于同一 program）时 SQL 一行也写不进，回读为空即冲突。
     */
    @Query(
        """
        INSERT INTO problem_revision_visual
            (problem_revision_id, role, ordinal, program_id, snapshot_id, created_at_epoch_millis)
        SELECT :problemRevisionId, :role, :ordinal, :programId, :snapshotId, :createdAtEpochMillis
        WHERE EXISTS (
            SELECT 1 FROM tutor_visual_snapshot AS snapshot
            WHERE snapshot.snapshot_id = :snapshotId
              AND snapshot.status = :liveStatus
              AND snapshot.program_id = :programId
        )
        """,
    )
    abstract suspend fun insertRevisionVisualIfLive(
        problemRevisionId: String,
        role: String,
        ordinal: Int,
        programId: String,
        snapshotId: String,
        createdAtEpochMillis: Long,
        liveStatus: String,
    )

    @Query(
        """
        SELECT * FROM problem_revision_visual
        WHERE problem_revision_id = :problemRevisionId
          AND role = :role
          AND ordinal = :ordinal
        LIMIT 1
        """,
    )
    abstract suspend fun findRevisionVisual(
        problemRevisionId: String,
        role: String,
        ordinal: Int,
    ): ProblemRevisionVisualEntity?

    /**
     * 保存题固定一张 LIVE 快照（幂等）：同一 `(problem_revision_id, role, ordinal)` 重放同一
     * program/snapshot 是读回；指向另一张则冲突（换图不得静默改写历史）。
     */
    @Transaction
    open suspend fun pinRevisionVisual(
        problemRevisionId: String,
        role: String,
        ordinal: Int,
        programId: String,
        snapshotId: String,
        createdAtEpochMillis: Long,
    ): ProblemRevisionVisualEntity {
        val existing = findRevisionVisual(problemRevisionId, role, ordinal)
        if (existing != null) {
            if (existing.programId != programId || existing.snapshotId != snapshotId) {
                throw ImmutablePayloadConflictException(
                    "problem_revision_visual",
                    "$problemRevisionId#$role#$ordinal",
                )
            }
            return existing
        }
        insertRevisionVisualIfLive(
            problemRevisionId = problemRevisionId,
            role = role,
            ordinal = ordinal,
            programId = programId,
            snapshotId = snapshotId,
            createdAtEpochMillis = createdAtEpochMillis,
            liveStatus = TutorVisualSnapshotStatus.LIVE,
        )
        return findRevisionVisual(problemRevisionId, role, ordinal)
            ?: throw ImmutablePayloadConflictException(
                "problem_revision_visual",
                "$problemRevisionId#$role#$ordinal",
            )
    }

    @Query(
        """
        SELECT * FROM problem_revision_visual
        WHERE problem_revision_id = :problemRevisionId
        ORDER BY role ASC, ordinal ASC
        """,
    )
    abstract suspend fun findRevisionVisuals(problemRevisionId: String): List<ProblemRevisionVisualEntity>
}
