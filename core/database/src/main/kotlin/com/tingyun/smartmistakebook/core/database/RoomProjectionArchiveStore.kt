package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.model.LearnerSnapshotJson

/**
 * 投影归档的读回与回退恢复（内核修复路线图 W0-1/Q2 工具面；流程
 * `docs/research/kernel-projection-rollback.md` §4）。
 *
 * **分层落点**：DAO（`ProjectionTransactionDao`）只做原子读写——最近一份归档的读回、
 * 账本头 / 呈现事实的现读、单事务快照重建；全部**拒绝判定**在这里，且在**任何写之前**。
 * 判定放在数据库层而不是 core:data 编排层，理由都是本仓库的既有事实：
 *
 * 1. 期望版本由调用方显式传入——`core:database` 按依赖方向看不到 `core:domain` 的
 *    `LearningProjector.VERSION`（`KernelWave0SchemaContractTest` 已把这条依赖方向写成契约），
 *    而"显式声明期望版本、不给默认值"正是 `ProjectionCommit.expectedProjectorVersion` 的姿态；
 * 2. schema 比对读的是**本库** `sqlite_master` 的现况、decode 用的是 core:model 的
 *    `LearnerSnapshotJson`——两边的事实都在本层，搬到编排层只是多一次跨界，不会更细。
 *
 * **覆盖前归档**：恢复会覆盖当前投影，所以先按 `archiveProjectionSnapshot` 把当前投影
 * 追加进归档表（只增），再重建。顺序即机制：晚一步归档，被换下的那份就只剩归档表里
 * 没有副本——runbook §4 第 2 步要手工做的这件事由本方法承担，调用方**不要**再手工预归档
 * （最新一条归档就是恢复目标，手工插一行会把目标顶掉）。
 *
 * 恢复是维护操作：应在排空静止时执行（本类不做跨进程互斥，App 正常运行时不要调）。
 */
internal class RoomProjectionArchiveStore(
    private val database: StudyDatabase,
) {
    private val dao = database.projectionTransactionDao()

    suspend fun readLatestArchivedProjection(
        projectionName: String,
        learnerId: String,
    ): PersistedProjectionArchive? = dao.readLatestArchivedProjection(projectionName, learnerId)

    /**
     * 回退一步：把当前投影换成最近一次被覆盖的那份归档，并把被换下的当前投影追加归档。
     *
     * 拒绝（不写一行）的情形见 [ProjectionRestoreRejection]，判定顺序：
     * 无归档 → 版本（列）→ 可解码 → 版本（载荷）→ 恢复时刻单调 → checkpoint 不超前账本头
     * → 目标不早于既有呈现事实 → schema 同口径。全部通过后按"归档当前 → 单事务重建"两步走；
     * 两步之间崩溃只会多一行归档（只增，无害）。
     */
    suspend fun restoreArchivedProjection(
        projectionName: String,
        learnerId: String,
        expectedProjectorVersion: String,
        restoredAtEpochMillis: Long,
    ): PersistedLearnerSnapshot {
        DatabaseContractValidator.validateProjectionRestoreRequest(
            projectionName = projectionName,
            learnerId = learnerId,
            expectedProjectorVersion = expectedProjectorVersion,
            restoredAtEpochMillis = restoredAtEpochMillis,
        )
        val archive = dao.readLatestArchivedProjection(projectionName, learnerId)
            ?: throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.NO_ARCHIVE,
                "No archived projection for $projectionName/$learnerId",
            )
        if (archive.projectorVersion != expectedProjectorVersion) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.VERSION_MISMATCH,
                "Archived projection carries ${archive.projectorVersion} but the current binary " +
                    "expects $expectedProjectorVersion",
            )
        }
        // 解回影子快照：ignoreUnknownKeys=false + 子类型 init require。坏归档包成机器可判的拒绝
        // （原始异常只作 cause）——调用方要能区分"归档坏了，安全拒了"与"工具自己崩了"。
        val snapshot = try {
            LearnerSnapshotJson.decode(archive.snapshotJson)
        } catch (malformed: IllegalArgumentException) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.MALFORMED_ARCHIVE,
                "Archived snapshot JSON of archive ${archive.archiveId} does not decode: " +
                    (malformed.message ?: malformed::class.java.simpleName),
                malformed,
            )
        }
        // 真正会被写回的是载荷里那份快照，所以版本判定必须也落在载荷上：归档行的版本列
        // 若与载荷不一致（外部改行/未来漂移），只信列会让一份别的版本的状态写进来。
        if (snapshot.checkpoint.projectorVersion != expectedProjectorVersion) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.VERSION_MISMATCH,
                "Archived snapshot payload carries ${snapshot.checkpoint.projectorVersion} but " +
                    "the current binary expects $expectedProjectorVersion",
            )
        }
        // 恢复时刻必须不早于目标归档：被换下那份的 `archived_at` 取它，"最近一份"才是它。
        // 倒挂会让 `readLatestArchivedProjection` 停在原地——连续回退原地打转（且看起来"没生效"）。
        if (restoredAtEpochMillis < archive.archivedAtEpochMillis) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.RESTORED_AT_IN_PAST,
                "Restore time $restoredAtEpochMillis precedes archive ${archive.archiveId} " +
                    "archived at ${archive.archivedAtEpochMillis}",
            )
        }
        // 账本头单调递增，所以在事务外读也安全：现在不超前，将来也不会被"长出来"的事件反超。
        val ledgerHead = dao.readLearnerLedgerHead(learnerId)
        if (snapshot.checkpoint.lastSequence > ledgerHead) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.CHECKPOINT_AHEAD,
                "Archived snapshot checkpoint ${snapshot.checkpoint.lastSequence} is ahead of the " +
                    "current ledger head $ledgerHead; the next drain would silently skip events",
            )
        }
        // 呈现态不在归档 JSON 内（它是账本派生态），恢复不写它；但增量排空把**现存**呈现行
        // 按恢复后的 checkpoint 读成权威——目标早于任何终局揭示时，下一次增量排空会在
        // `PresentationProjectionState` 的"揭示序号 ≤ 水位"前置条件上直接构造失败。没有历史
        // 版本可以精确回滚呈现态，所以这里显式拒绝，并把出路写进 message。
        val presentationTerminal = dao.readMaxPresentationTerminalSequence(projectionName, learnerId)
        if (presentationTerminal != null && presentationTerminal > snapshot.checkpoint.lastSequence) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.PRESENTATION_AHEAD,
                "Restore target checkpoint ${snapshot.checkpoint.lastSequence} precedes existing " +
                    "presentation fact at sequence $presentationTerminal; the incremental drain " +
                    "reads the un-rolled-back presentation rows as authority. Resolve the " +
                    "presentation state first, or take a full-replay path instead",
            )
        }
        val currentDdl = dao.readProjectionTablesDdl()
        if (currentDdl != archive.schemaDdl) {
            throw ProjectionRestoreRejectedException(
                ProjectionRestoreRejection.SCHEMA_MISMATCH,
                "Projection table DDL drifted since archive ${archive.archiveId} was written; " +
                    "refusing to write the archived JSON into a different schema",
            )
        }
        val current = dao.readCurrentSnapshot(projectionName, learnerId)
        if (current != null) {
            dao.archiveProjectionSnapshot(
                ProjectionArchiveRecord(
                    projectionName = projectionName,
                    learnerId = learnerId,
                    snapshotJson = LearnerSnapshotJson.encode(current.snapshot),
                    projectorVersion = current.snapshot.checkpoint.projectorVersion,
                    archivedAtEpochMillis = restoredAtEpochMillis,
                ),
            )
        }
        return dao.restoreProjectionSnapshot(
            projectionName = projectionName,
            learnerId = learnerId,
            snapshot = snapshot,
        )
    }
}
