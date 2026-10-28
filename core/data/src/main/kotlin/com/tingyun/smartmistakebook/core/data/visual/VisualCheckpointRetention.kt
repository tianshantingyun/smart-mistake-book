package com.tingyun.smartmistakebook.core.data.visual

import com.tingyun.smartmistakebook.core.database.TutorVisualCheckpointKind
import com.tingyun.smartmistakebook.core.model.figureUsageUtf8Bytes

/**
 * 一个既有快照行在保留决策中的**最小投影**（不依赖 Room entity，JVM 门可直接构造）。
 *
 * [kind] 的取值来自 `core/database` 的 [TutorVisualCheckpointKind]（`INITIAL`/`LATEST`；
 * `null` = 可回收中间态）——**不在这里另立一套字面量**：写侧（T3 的 D-02 提交）与保留
 * 策略必须共用同一个来源，否则拼错一次就会让"初始基线"退化成可淘汰的中间态。
 *
 * **未知 kind（既非 INITIAL 也非 LATEST 的非空字符串）按 fail-closed 处理**：不淘汰。
 * 宁可让坏数据占着预算并照常拒绝提交，也不把一条含义不明的行当成"可以删"。
 *
 * @param pinned = 被 `tutor_message_visual.current_snapshot_id` 或
 *   `problem_revision_visual.snapshot_id` 引用（导出冻结集属 `core/export` 热区，本批不做，
 *   由 T3 接导出时补入参）。
 * @param storedBytes = [visualCheckpointStoredBytes] 的计量口径（state+scene+draw commands
 *   的 UTF-8 字节和；契约 §8.1）。
 */
internal data class VisualCheckpointRow(
    val snapshotId: String,
    val kind: String?,
    val pinned: Boolean,
    val storedBytes: Long,
) {
    init {
        require(snapshotId.isNotBlank()) { "A checkpoint row needs a snapshot id" }
        require(storedBytes >= 0L) { "Checkpoint stored bytes must not be negative" }
    }
}

/**
 * checkpoint 保留/配额策略（契约 §8.1 的两个数字）。
 *
 * 本批的语义是**提交门**，不是淘汰器：超限先扣除"未固定且可淘汰"的行，仍超限 →
 * [VisualCheckpointPlan.Rejected]（本次不提交并说明），**不通过删用户事实腾空间**。
 * 实际的 DELETE/GC 落点在 T3 的写入事务与既有 orphan GC，见 [VisualCheckpointRetention]。
 */
internal data class VisualCheckpointRetentionPolicy(
    /** 每 program 的可回收派生缓存上限（契约 §8.1：4 MiB）。 */
    val perProgramBudgetBytes: Long = 4L * 1024 * 1024,
    /** 全局可回收视觉缓存上限（契约 §8.1：32 MiB）。 */
    val globalBudgetBytes: Long = 32L * 1024 * 1024,
) {
    init {
        require(perProgramBudgetBytes > 0L) { "The per-program budget must be positive" }
        require(globalBudgetBytes > 0L) { "The global budget must be positive" }
    }

    companion object {
        val CURRENT: VisualCheckpointRetentionPolicy = VisualCheckpointRetentionPolicy()
    }
}

/** 拒绝提交的有码原因（调用方 T3 把它映射成自己的有界拒绝类型/可读说明）。 */
internal enum class VisualCheckpointRejection(val code: String) {
    PER_PROGRAM_BUDGET("VISUAL_CHECKPOINT_PER_PROGRAM_BUDGET"),
    GLOBAL_BUDGET("VISUAL_CHECKPOINT_GLOBAL_BUDGET"),
    DISK_FULL("VISUAL_CHECKPOINT_DISK_FULL"),
}

/** 保留决策结果：可提交（附 demote/淘汰清单）或拒绝提交（有界原因）。 */
internal sealed interface VisualCheckpointPlan {
    /**
     * @param incomingKind 新 checkpoint 的 `checkpoint_kind`（首个 = INITIAL，之后 = LATEST）。
     * @param demoteToIntermediate 旧 `LATEST` 行（标记只有一个，新 checkpoint 接手）；被固定的
     *   旧 LATEST 也在内——它只是不再是最新标记，行仍因 pinned 保留。
     * @param evictUnpinnedSnapshotIds 可回收的未固定行（`null` kind 或旧 LATEST）。
     *   **本轨不执行删除**：由 T3 在同一写入事务/既有 GC 里落地，且淘汰集必须在提交时重查
     *   （pin 是并发可变的）。
     * @param retainedBytesAfterEviction 扣除淘汰候选后本 program 仍保留的字节数
     *   （INITIAL + pinned + 未知 kind + 本行）。
     */
    data class Retained(
        val incomingKind: String,
        val demoteToIntermediate: List<String>,
        val evictUnpinnedSnapshotIds: List<String>,
        val retainedBytesAfterEviction: Long,
    ) : VisualCheckpointPlan

    data class Rejected(
        val reason: VisualCheckpointRejection,
        val diagnostic: String,
    ) : VisualCheckpointPlan
}

/**
 * **checkpoint 初始+最新两份 + 配额拒提交**的纯策略（批次 30 T4 / D-03 半场）。
 *
 * **它消灭的具体失败**：见 `VisualCheckpointRetentionTest` 文件头——"保留两份"此前只是散文，
 * 没有可执行口径，导致要么缓存无限长、要么在配额压力下删掉被消息/题目固定的快照。
 *
 * 规则（契约 §8.1 原文的落码）：
 * 1. 新 checkpoint 的 kind：本 program 没有 `INITIAL` 行 → [TutorVisualCheckpointKind.INITIAL]；
 *    否则 [TutorVisualCheckpointKind.LATEST]（`INITIAL` 行永不改写）；
 * 2. **可淘汰 = 未固定且（`kind == null` 或 `LATEST`）**；被固定的事实（消息当前指针、
 *    保存题引用）一律保留；未知 kind fail-closed 保留；
 * 3. 预算按**淘汰后**的保留字节数判定（per-program），全局按"全局总量 − 本 program 淘汰量
 *    + 本行"判定；仍超限 → 拒绝提交；
 * 4. 磁盘可用空间不足（本行字节 > 可用字节）→ 拒绝提交，**不删任何用户事实**。
 *
 * **与写入方的边界（批次 30 复核修复后已接线）**：
 * - 本对象**只判定**，不触 DB、不删行、不改 status；`demote`/`evict` 的 SQL 由
 *   `TutorVisualCommitRepository.planCheckpoint` + `TutorVisualPort.commitTutorTurnWithVisual`
 *   在同一事务里执行（`UPDATE … checkpoint_kind` 与淘汰删除在事务内，且淘汰 SQL 重查 pin/kind）。
 * - 仓储层在**写库前**调用本函数：`stageSnapshot` / `commitAssistantTurnWithVisual` 都用
 *   [nextCheckpointKind] 决定落库 kind，并用 `plan(...)` 做磁盘/配额门；
 *   [VisualCheckpointPlan.Rejected] 映射成 `TutorVisualCommitRejectedException`
 *   （含 `PER_PROGRAM_BUDGET` / `GLOBAL_BUDGET`）。两边字节口径一致
 *   （`utf8Size(state)+utf8Size(scene)+utf8Size(draw)`；SQL 侧 `length(CAST(x AS BLOB))`）。
 * - `pinned` 的"导出冻结集"（E-01）属 `core/export` 热区，本批不做，接导出时补入参；
 * - 对 LIVE 行的删除**不是降级**：契约 §8.1 明确允许"淘汰未固定快照/预览"；"LIVE 不可回退"
 *   约束的是 status 语义（不得 LIVE→STAGING、不得改写内容），两件事分开。
 */
internal object VisualCheckpointRetention {

    /** 拒绝原因的上界（有界诊断，不把内部集合回显）。 */
    const val MAX_DIAGNOSTIC_CHARS: Int = 240

    /** 下一条 checkpoint 的 kind（首个 LIVE 提交 = INITIAL，之后 = LATEST）。 */
    fun nextCheckpointKind(existing: List<VisualCheckpointRow>): String =
        if (existing.any { it.kind == TutorVisualCheckpointKind.INITIAL }) {
            TutorVisualCheckpointKind.LATEST
        } else {
            TutorVisualCheckpointKind.INITIAL
        }

    /**
     * @param existing 本 program 的全部既有快照行（含 STAGING 与 LIVE；`pinned` 由调用方
     *   在同事务内按 link/题面引用计算）。
     * @param availableDiskBytes 注入的可用空间（测试可注 0/边界；同 `BackupSnapshotTrimmer`
     *   的 `usableSpace` 注入口形态）。
     * @param globalStoredBytes 全库视觉快照字节总量，**必须包含** [existing] 的字节和。
     */
    fun plan(
        incomingSnapshotId: String,
        incomingStoredBytes: Long,
        existing: List<VisualCheckpointRow>,
        availableDiskBytes: Long,
        globalStoredBytes: Long,
        policy: VisualCheckpointRetentionPolicy = VisualCheckpointRetentionPolicy.CURRENT,
    ): VisualCheckpointPlan {
        require(incomingSnapshotId.isNotBlank()) { "An incoming checkpoint needs a snapshot id" }
        require(incomingStoredBytes >= 0L) { "Incoming checkpoint bytes must not be negative" }
        require(availableDiskBytes >= 0L) { "Available disk bytes must not be negative" }
        require(globalStoredBytes >= 0L) { "Global stored bytes must not be negative" }
        val existingIds = existing.map { it.snapshotId }
        require(existingIds.size == existingIds.toSet().size) { "Duplicate checkpoint snapshot ids" }
        require(incomingSnapshotId !in existingIds) {
            "Incoming checkpoint ${incomingSnapshotId.take(64)} already exists"
        }
        val existingBytes = existing.sumOf { it.storedBytes }
        require(globalStoredBytes >= existingBytes) {
            "Global stored bytes $globalStoredBytes do not cover this program's $existingBytes"
        }

        val evictable = existing.filter { row ->
            !row.pinned && (row.kind == null || row.kind == TutorVisualCheckpointKind.LATEST)
        }
        val evictableBytes = evictable.sumOf { it.storedBytes }
        val retainedBytes = existingBytes - evictableBytes + incomingStoredBytes

        if (incomingStoredBytes > availableDiskBytes) {
            return rejected(
                VisualCheckpointRejection.DISK_FULL,
                "needs $incomingStoredBytes bytes but only $availableDiskBytes are available",
            )
        }
        if (retainedBytes > policy.perProgramBudgetBytes) {
            return rejected(
                VisualCheckpointRejection.PER_PROGRAM_BUDGET,
                "retained $retainedBytes bytes after evicting unpinned rows exceeds " +
                    "the per-program budget ${policy.perProgramBudgetBytes}",
            )
        }
        val globalAfter = globalStoredBytes - evictableBytes + incomingStoredBytes
        if (globalAfter > policy.globalBudgetBytes) {
            return rejected(
                VisualCheckpointRejection.GLOBAL_BUDGET,
                "global $globalAfter bytes after local eviction exceeds the budget ${policy.globalBudgetBytes}",
            )
        }
        return VisualCheckpointPlan.Retained(
            incomingKind = nextCheckpointKind(existing),
            demoteToIntermediate = existing
                .filter { it.kind == TutorVisualCheckpointKind.LATEST }
                .map { it.snapshotId },
            evictUnpinnedSnapshotIds = evictable.map { it.snapshotId },
            retainedBytesAfterEviction = retainedBytes,
        )
    }

    private fun rejected(reason: VisualCheckpointRejection, detail: String): VisualCheckpointPlan.Rejected =
        VisualCheckpointPlan.Rejected(
            reason = reason,
            diagnostic = "${reason.code}: $detail".take(MAX_DIAGNOSTIC_CHARS),
        )
}

/**
 * checkpoint 的计量口径（契约 §8.1）：`draw_commands_json + state_json + scene_json` 的
 * UTF-8 字节和。**它消灭的具体失败**：各写入方用字符串长度（UTF-16 code units）或平台默认
 * 编码估算，中文/emoji 场景下与真实落盘字节差一截，配额门形同虚设。
 */
internal fun visualCheckpointStoredBytes(
    stateJson: String,
    sceneJson: String,
    drawCommandsJson: String,
): Long =
    figureUsageUtf8Bytes(stateJson).toLong() +
        figureUsageUtf8Bytes(sceneJson).toLong() +
        figureUsageUtf8Bytes(drawCommandsJson).toLong()
