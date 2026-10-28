package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.CommitTutorTurnWithVisualCommand
import com.tingyun.smartmistakebook.core.database.RepointTutorMessageVisualCommand
import com.tingyun.smartmistakebook.core.database.StageTutorVisualSnapshotCommand
import com.tingyun.smartmistakebook.core.database.TutorMessageVisualRecord
import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointSummary
import com.tingyun.smartmistakebook.core.database.TutorVisualProgramRecord
import com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord
import com.tingyun.smartmistakebook.core.database.TutorVisualTurnCommitRecord

/**
 * 视觉四表（D-01）之上的**事务级写入口**（D-02）。
 *
 * **它消灭的具体失败**：四表的 DAO 各自能写，但"assistant 正文 + tool trace + link +
 * `STAGING → LIVE`"如果由调用方分四次写，进程死在两次写之间就会留下"消息有、图无且无记录"
 * 的不可恢复中间态；反过来"快照 LIVE 了、消息没写成"则让恢复不知道该信谁。把这条链放进
 * **一个数据库事务**之后，只有"全部成立"与"全部不成立"两种结果。
 *
 * 默认实现抛 [UnsupportedOperationException]：这是**新增能力**，不希望给既有测试替身
 * （`FakeStudyDatabasePort` 等）伪造一个"看起来成功"的空实现——静默成功比编译失败更难查。
 */
interface TutorVisualPort {

    /**
     * S2 + S3：写入不可变 program 与 STAGING 快照（不可见，尚未被任何消息引用）。
     *
     * 失败语义：同 programId 不同载荷 → `ImmutablePayloadConflictException`；
     * 同内容快照已 LIVE → 直接复用（返回 LIVE 行）；同内容快照仍是 STAGING → 按
     * `owner_request_id` 做 CAS 转租约，命中 0 行即冲突。
     */
    suspend fun stageTutorVisualSnapshot(
        command: StageTutorVisualSnapshotCommand,
    ): TutorVisualSnapshotRecord =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual snapshots",
        )

    /**
     * S4：**一个事务**内完成 program/snapshot 落库（或复用）、assistant 消息正文/trace、
     * `STAGING → LIVE` 与 link 插入。任一步抛错 → 整体回滚。
     *
     * 重复提交（同 content、同 message/ordinal）是幂等读回；同 `(message_id, ordinal)`
     * 指向另一张快照 → `ImmutablePayloadConflictException`（换图只能走指针 CAS）。
     */
    suspend fun commitTutorTurnWithVisual(
        command: CommitTutorTurnWithVisualCommand,
    ): TutorVisualTurnCommitRecord =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual turns",
        )

    /** 指针 CAS：返回 false = 期望 revision 已过期或目标快照不是 LIVE（不覆盖新状态）。 */
    suspend fun repointTutorMessageVisual(
        command: RepointTutorMessageVisualCommand,
    ): Boolean =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual pointer changes",
        )

    /**
     * 启动对账：删除**STAGING 且租约过期且无消息/题面引用**的快照；返回删除行数。
     * LIVE 行永不被删除（用户看到过的图不能消失）。
     */
    suspend fun reconcileStaleTutorVisualStaging(staleBeforeEpochMillis: Long): Int =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual reconciliation",
        )

    suspend fun readTutorVisualProgram(programId: String): TutorVisualProgramRecord? =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual reads",
        )

    suspend fun readTutorVisualSnapshot(snapshotId: String): TutorVisualSnapshotRecord? =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual reads",
        )

    suspend fun readTutorMessageVisual(messageId: String, ordinal: Int): TutorMessageVisualRecord? =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual reads",
        )

    /** 测试/审计用：程序行数（回滚与幂等断言不依赖实现内部的 DAO）。 */
    suspend fun countTutorVisualProgramsForTest(programId: String): Int =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual counts",
        )

    /**
     * checkpoint 保留策略的读侧（批次 30 / D-05）：本 program 全部快照行的
     * `(snapshotId, checkpointKind, pinned, storedBytes)`。`storedBytes` 是
     * state+scene+draw 的 UTF-8 字节和。
     */
    suspend fun readVisualCheckpointSummaries(programId: String): List<TutorVisualCheckpointSummary> =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual checkpoints",
        )

    /** 全库视觉快照派生字节总量（与 [readVisualCheckpointSummaries] 同口径）。 */
    suspend fun readGlobalVisualCheckpointStoredBytes(): Long =
        throw UnsupportedOperationException(
            "This StudyDatabasePort implementation does not support visual checkpoints",
        )
}
