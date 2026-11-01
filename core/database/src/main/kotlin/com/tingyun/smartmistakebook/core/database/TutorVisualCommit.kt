package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.entity.TutorMessageVisualEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualProgramEntity
import com.tingyun.smartmistakebook.core.database.entity.TutorVisualSnapshotEntity

/**
 * 视觉快照状态（`tutor_visual_snapshot.status`，schema 70 / D-01）。
 *
 * `STAGING → LIVE` **单向**：LIVE 是终态（用户看到过的图不能"消失"）。设计 §3.2 明确
 * **没有 `LIVE → *`** 的迁移；对账只删除**从未被引用、且 owner 租约已过期**的 STAGING 行。
 */
object TutorVisualStatus {
    const val STAGING = "STAGING"
    const val LIVE = "LIVE"
}

/** 快照的 checkpoint 角色（`tutor_visual_snapshot.checkpoint_kind`）。 */
object TutorVisualCheckpointKind {
    /** 首个 LIVE 提交写入的重建基线；永不变。 */
    const val INITIAL = "INITIAL"

    /** 每 program 的最新可回收快照；由 CAS 迁移，旧值降为 NULL。 */
    const val LATEST = "LATEST"
}

/**
 * checkpoint 配额的两个数字（契约 §8.1：每 program 4 MiB、全库 32 MiB）。
 *
 * **唯一数值来源**：`core:data` 的 `VisualCheckpointRetentionPolicy` 默认值引用这里，写入
 * 事务内的重判也用同一对常量。改前策略常量只在 `core:data`，事务层若要重判就得再抄一遍
 * 数字——两套口径漂移后，事务内会放行策略层已拒绝的提交（或反过来），"预算门"变成两个
 * 互相矛盾的门。
 */
object TutorVisualCheckpointBudget {
    const val PER_PROGRAM_BYTES: Long = 4L * 1024 * 1024
    const val GLOBAL_BYTES: Long = 32L * 1024 * 1024
}

/** 事务内预算重判失败的两条轴（与 `VisualCheckpointRejection` 的两种预算原因一一对应）。 */
enum class TutorVisualCheckpointBudgetAxis {
    PER_PROGRAM,
    GLOBAL,
}

/**
 * **写入事务内**重读已用字节后判定的预算拒绝。
 *
 * 它消灭的具体失败：预算判定此前只在仓储层、事务**之前**做（`planCheckpoint`）。两个并发
 * 提交可以各自读到"还没超"的旧总量、各自通过判定、然后都写入——超出部分没有任何门拦；
 * 进程内 `Mutex` 只覆盖同一 repository 实例，挡不住第二个实例/第二个进程。把判定放进
 * 写入事务、基于事务内已写入的行重读总量后，第二个事务必然看到第一个事务的结果，因而只能
 * 有一个通过。
 *
 * 抛出它会让 Room 回滚整个写事务（消息/link/program/snapshot 一起不成立），仓储层再把它
 * 翻译成对外的 `TutorVisualCommitRejectedException`（含 `PER_PROGRAM_BUDGET` /
 * `GLOBAL_BUDGET`）。
 */
class TutorVisualCheckpointBudgetExceededException(
    val axis: TutorVisualCheckpointBudgetAxis,
    message: String,
) : IllegalStateException(message)

/**
 * 程序行的一次**不可变**写入载荷（D-02 身份层 → DB 层）。
 *
 * [programId] 由内容派生（`TutorVisualIdentity`），并且**等于对外 visualId**。DB 层在写入时
 * 用 `INSERT OR IGNORE` + 回读逐列比对：同 id 不同载荷 ⇒
 * [ImmutablePayloadConflictException]；**没有任何 UPDATE 路径**。这正是"同一份源码被原地改写，
 * 已显示的图静默变成另一张"这个失败的结构性终结。
 *
 * [createdAtEpochMillis] 是**元数据**，不是身份：重试时首次写入的时间获胜，不参与同 id 比对
 * （否则每次重试都会因为新时间戳被误判为"载荷不同"）。
 */
data class TutorVisualProgramWrite(
    val programId: String,
    val learnerId: String,
    /** QUESTION_DIAGRAM / TUTOR_EXPLANATION（宿主派生，进身份哈希）。 */
    val role: String,
    val sourceSha256: String,
    val sourceJs: String,
    val initialStateJson: String,
    val initialStateSha256: String,
    val apiVersion: Int,
    val sdkVersion: String,
    /** 题面修订的软引用（多态来源不建外键）。 */
    val basisRevisionId: String?,
    val basisFingerprint: String,
    /** 原模型请求/来源快照；调用方负责有界（超限写 NULL，不截成半截 JSON）。 */
    val requestSnapshot: String?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(programId.isNotBlank()) { "A visual program id must not be blank" }
        require(learnerId.isNotBlank()) { "A visual program needs a learner id" }
        require(role.isNotBlank()) { "A visual program needs a role" }
        require(sourceSha256.isNotBlank()) { "A visual program needs a source digest" }
        require(sourceJs.isNotEmpty()) { "A visual program needs source" }
        require(initialStateJson.isNotEmpty()) { "A visual program needs its initial state" }
        require(initialStateSha256.isNotBlank()) { "A visual program needs an initial state digest" }
        require(apiVersion >= 1) { "A visual program api version starts at 1" }
        require(sdkVersion.isNotBlank()) { "A visual program needs an sdk version" }
        require(basisFingerprint.isNotBlank()) { "A visual program needs a basis fingerprint" }
        require(createdAtEpochMillis >= 0L) { "A visual program creation time must not be negative" }
    }
}

/**
 * 快照行的一次写入载荷。内容是**回放的唯一输入**，因此每一列都必须由调用方显式给出：
 * 状态/场景/DrawOps/视口/字体缩放/版本/核对状态/预览资产。
 *
 * [snapshotId] 由 `(programId, stateSha256, drawCommandsSha256, rendererVersion,
 * fontManifestVersion)` 内容+版本派生（见 `TutorVisualIdentity.deriveSnapshotId`）：同内容同版本
 * 重复提交落到同一行，是 INV-6 幂等的基础；**版本变化是另一行**（schema 71 / S1）。
 * [checkpointKind] 只表达角色，不参与唯一键（唯一键是 DB 的
 * `UNIQUE(program_id, state_sha256, draw_commands_sha256, renderer_version,
 * font_manifest_version)`）。
 */
data class TutorVisualSnapshotWrite(
    val snapshotId: String,
    val programId: String,
    val stateJson: String,
    val stateSha256: String,
    val sceneSchemaVersion: Int,
    val sceneJson: String,
    val sceneSha256: String,
    val renderSchemaVersion: Int,
    val drawCommandsJson: String,
    val drawCommandsSha256: String,
    val contentIdsJson: String,
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    val fontScaleMilli: Int,
    val theme: String,
    val timeMs: Long,
    val seed: Long,
    val runtimeVersion: String,
    val compilerVersion: String,
    val rendererVersion: String,
    val fontManifestVersion: String,
    /** NOT_CHECKED / VERIFIED / MISMATCH。 */
    val questionVerificationStatus: String,
    val previewAssetId: String?,
    val checkpointKind: String?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(snapshotId.isNotBlank()) { "A visual snapshot id must not be blank" }
        require(programId.isNotBlank()) { "A visual snapshot needs its program id" }
        require(stateJson.isNotEmpty()) { "A visual snapshot needs its state" }
        require(stateSha256.isNotBlank()) { "A visual snapshot needs a state digest" }
        require(sceneSchemaVersion >= 1) { "A scene schema version starts at 1" }
        require(sceneJson.isNotEmpty()) { "A visual snapshot needs its scene" }
        require(sceneSha256.isNotBlank()) { "A visual snapshot needs a scene digest" }
        require(renderSchemaVersion >= 1) { "A render schema version starts at 1" }
        require(drawCommandsJson.isNotEmpty()) { "A replayable snapshot needs draw commands" }
        require(drawCommandsSha256.isNotBlank()) { "A visual snapshot needs a draw digest" }
        require(contentIdsJson.isNotEmpty()) { "A visual snapshot needs its frozen content ids" }
        require(viewportWidthDu >= 1) { "A visual snapshot viewport width must be positive" }
        require(viewportHeightDu >= 1) { "A visual snapshot viewport height must be positive" }
        require(fontScaleMilli >= 1) { "A visual snapshot font scale must be positive" }
        require(theme.isNotBlank()) { "A visual snapshot needs a theme" }
        require(timeMs >= 0L) { "A visual snapshot time must not be negative" }
        require(runtimeVersion.isNotBlank()) { "A visual snapshot needs a runtime version" }
        require(compilerVersion.isNotBlank()) { "A visual snapshot needs a compiler version" }
        require(rendererVersion.isNotBlank()) { "A visual snapshot needs a renderer version" }
        require(fontManifestVersion.isNotBlank()) { "A visual snapshot needs a font manifest version" }
        require(questionVerificationStatus.isNotBlank()) {
            "A visual snapshot needs a question verification status"
        }
        require(createdAtEpochMillis >= 0L) { "A visual snapshot creation time must not be negative" }
    }
}

/**
 * S2 + S3（设计 §3.3）：把 program 与 STAGING 快照落成**可见之前的**事实。
 *
 * 单独暴露是因为宿主可能需要在消息完成之前拿到 `(programId, snapshotId)`（例如预览/恢复
 * 占位）。被取消的 STAGING 行由 [reconcileStaleTutorVisualStaging] 回收；LIVE 行永不被它碰。
 */
data class StageTutorVisualSnapshotCommand(
    val program: TutorVisualProgramWrite,
    val snapshot: TutorVisualSnapshotWrite,
    val ownerRequestId: String,
    /**
     * 事务内预算重判前要回收的 checkpoint 候选（批次 32 复核修复）。
     *
     * 它消灭的具体失败：`VisualCheckpointRetention.plan` 在事务前按"淘汰后的净值"判预算
     * （`retainedBytes`/`globalAfter` 已扣除可回收行），而 `stageTutorVisualSnapshot` 的
     * 事务内重判读的是**全量**字节。stage 路径若只写 `incomingKind`、把 plan 的淘汰集丢掉，
     * 就会在"总量超限、净值不超"时预检放行、事务拒绝——与契约 §8.1"超限只淘汰中间态，
     * 淘汰后仍超才不提交"的方向相反（不淘汰就拒）。
     *
     * stage 只带**淘汰**集，不带 demote：新行仍是 STAGING（尚未被用户看到），不能抢走旧
     * LATEST 的角色；淘汰只作用于"未固定、STAGING、kind ∈ {NULL, LATEST}"的缓存行，
     * SQL 仍会重查 pin/kind/status/租约。默认空列表 = 不做回收。
     */
    val evictCheckpointSnapshotIds: List<String> = emptyList(),
    /**
     * STAGING 租约的时间判据（与 `CommitTutorTurnWithVisualCommand` 同名同义）：
     * `created_at_epoch_millis <= 此值` 视为租约过期，才允许回收**其他 owner** 的行。
     * `Long.MIN_VALUE`（默认）= 不按时间淘汰，只允许回收本 owner 的行。
     */
    val stagingLeaseExpiredBeforeEpochMillis: Long = Long.MIN_VALUE,
    /**
     * 本次事务内预算重判的两个上界（默认契约 §8.1 的 4 MiB / 32 MiB）。
     * 仓储层传入自己的策略值；直接调端口的调用方拿到默认值。
     */
    val perProgramBudgetBytes: Long = TutorVisualCheckpointBudget.PER_PROGRAM_BYTES,
    val globalBudgetBytes: Long = TutorVisualCheckpointBudget.GLOBAL_BYTES,
) {
    init {
        require(ownerRequestId.isNotBlank()) { "A staging snapshot needs its owner request id" }
        require(program.programId == snapshot.programId) {
            "A snapshot must belong to the program being staged"
        }
        require(evictCheckpointSnapshotIds.none { it.isBlank() }) {
            "An evicted checkpoint snapshot id must not be blank"
        }
        require(perProgramBudgetBytes > 0L) { "The per-program checkpoint budget must be positive" }
        require(globalBudgetBytes > 0L) { "The global checkpoint budget must be positive" }
    }
}

/**
 * S4（设计 §3.3）：**首张可见图与 assistant message/正文/tool trace/link 同一个事务**。
 *
 * 这是 D-02 的核心合同：没有"先写消息、再补图"的第二次写——两次写之间进程死亡会留下
 * "正文在、图不在、且没有任何记录说明图本该来"的不可恢复中间态。
 */
data class CommitTutorTurnWithVisualCommand(
    val message: AppendTutorAssistantMessageDatabaseCommand,
    val program: TutorVisualProgramWrite,
    val snapshot: TutorVisualSnapshotWrite,
    val ownerRequestId: String,
    /** 同一条消息的第 n 张图（`(message_id, ordinal)` 主键）。 */
    val visualOrdinal: Int = 0,
    val uiStateJson: String? = null,
    val formDigest: String? = null,
    /**
     * checkpoint 保留策略的**同事务**落点（批次 30 / D-05）：旧 `LATEST` 降为 NULL（行仍在，
     * 被固定的旧最新快照不删）。空列表 = 本次没有要降级的行。
     */
    val demoteCheckpointSnapshotIds: List<String> = emptyList(),
    /**
     * checkpoint 保留策略的**同事务**淘汰集：只删"仍未固定、kind ∈ {NULL, LATEST}、仍为
     * STAGING、且租约已过期或为本 owner 的"行。SQL 会重查 pin/kind/status/租约（plan 在事务
     * 外算，引用、可见性与 owner 都可能并发变化），INITIAL 与 LIVE 永不淘汰。
     */
    val evictCheckpointSnapshotIds: List<String> = emptyList(),
    /**
     * STAGING 租约的时间判据：`created_at_epoch_millis <= 此值` 视为租约过期。
     *
     * 它消灭的具体失败：淘汰 SQL 只看 pin/kind/status 时，会删掉**另一个仍在飞的请求刚
     * stage 的** STAGING 行（owner 是别人、created_at 是刚刚）。被删方提交时只能重建行或
     * 失败，已发出的 `(programId, snapshotId)` 短暂失效。现在不同 owner 的 STAGING 行只有在
     * 租约过期后才可删；`Long.MIN_VALUE`（默认）= 不按时间淘汰，只允许本 owner 的行。
     */
    val stagingLeaseExpiredBeforeEpochMillis: Long = Long.MIN_VALUE,
    /**
     * 事务内预算重判的两个上界（默认契约 §8.1 的 4 MiB / 32 MiB）。仓储层传入自己的策略值。
     */
    val perProgramBudgetBytes: Long = TutorVisualCheckpointBudget.PER_PROGRAM_BYTES,
    val globalBudgetBytes: Long = TutorVisualCheckpointBudget.GLOBAL_BYTES,
) {
    init {
        require(ownerRequestId.isNotBlank()) { "A visual commit needs its owner request id" }
        require(visualOrdinal >= 0) { "A visual ordinal must not be negative" }
        require(program.programId == snapshot.programId) {
            "A snapshot must belong to the program being committed"
        }
        require(demoteCheckpointSnapshotIds.none { it.isBlank() }) {
            "A demoted checkpoint snapshot id must not be blank"
        }
        require(evictCheckpointSnapshotIds.none { it.isBlank() }) {
            "An evicted checkpoint snapshot id must not be blank"
        }
        require(perProgramBudgetBytes > 0L) { "The per-program checkpoint budget must be positive" }
        require(globalBudgetBytes > 0L) { "The global checkpoint budget must be positive" }
        // 刻意**不**要求 demote 与 evict 互斥：保留策略对"旧 LATEST 且可淘汰"的行同时产出
        // 两个清单——先清标记、再删行是幂等且正确的；反过来禁止重叠会让 S3→S4 常规路径在
        // 构造命令时抛 IllegalArgumentException（`VisualCheckpointRetentionTest` 的
        // `a new checkpoint demotes the previous latest...` 与 plan→command 端到端用例都依赖
        // 这个重叠）。demote 只改角色列，对随后被删除的行没有副作用。
    }
}

/** 指针 CAS（设计 §3.5）：换图/重渲染。命中 0 行 = 期望版本过期或目标不是 LIVE。 */
data class RepointTutorMessageVisualCommand(
    val messageId: String,
    val ordinal: Int,
    val expectedRevision: Int,
    val nextSnapshotId: String,
    val nowEpochMillis: Long,
) {
    init {
        require(messageId.isNotBlank()) { "A visual pointer needs its message id" }
        require(ordinal >= 0) { "A visual ordinal must not be negative" }
        require(expectedRevision >= 1) { "A visual pointer revision starts at 1" }
        require(nextSnapshotId.isNotBlank()) { "A visual pointer needs its next snapshot id" }
        require(nowEpochMillis >= 0L) { "A visual pointer update time must not be negative" }
    }
}

/** 程序行的读侧形态（测试/恢复/审计用；不含任何可变状态——Program 行本来就没有）。 */
data class TutorVisualProgramRecord(
    val programId: String,
    val learnerId: String,
    val role: String,
    val sourceSha256: String,
    val sourceJs: String,
    val initialStateJson: String,
    val initialStateSha256: String,
    val apiVersion: Int,
    val sdkVersion: String,
    val basisRevisionId: String?,
    val basisFingerprint: String,
    val requestSnapshot: String?,
    val createdAtEpochMillis: Long,
)

/** 快照行的读侧形态。 */
data class TutorVisualSnapshotRecord(
    val snapshotId: String,
    val programId: String,
    val stateJson: String,
    val stateSha256: String,
    val sceneSchemaVersion: Int,
    val sceneJson: String,
    val sceneSha256: String,
    val renderSchemaVersion: Int,
    val drawCommandsJson: String,
    val drawCommandsSha256: String,
    val contentIdsJson: String,
    val viewportWidthDu: Int,
    val viewportHeightDu: Int,
    val fontScaleMilli: Int,
    val theme: String,
    val timeMs: Long,
    val seed: Long,
    val runtimeVersion: String,
    val compilerVersion: String,
    val rendererVersion: String,
    val fontManifestVersion: String,
    val questionVerificationStatus: String,
    val previewAssetId: String?,
    val status: String,
    val ownerRequestId: String?,
    val checkpointKind: String?,
    val createdAtEpochMillis: Long,
    val promotedAtEpochMillis: Long?,
)

/** 消息图引用的读侧形态。 */
data class TutorMessageVisualRecord(
    val messageId: String,
    val ordinal: Int,
    val programId: String,
    val currentSnapshotId: String,
    val revision: Int,
    val uiStateJson: String?,
    val formDigest: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** 一次"正文 + trace + 图"提交的完整结果（三者在同一个事务里成立或一起不成立）。 */
data class TutorVisualTurnCommitRecord(
    val message: TutorMessageRecord,
    val snapshot: TutorVisualSnapshotRecord,
    val link: TutorMessageVisualRecord,
)

/**
 * 一个 program 的既有快照在 checkpoint 保留策略中的最小投影（批次 30 / D-05；批次 31 补
 * `status`）。
 *
 * 跨层原因：保留策略（`core:data` 的 `VisualCheckpointRetention`）需要"本 program 有哪些
 * 快照、哪些被消息/题面固定、哪些是用户已见（LIVE）、各占多少字节"，而读取只能走
 * `core:database` 的 SQL。这个 DTO 只承载策略需要的五列；`storedBytes` 与
 * `visualCheckpointStoredBytes` 同口径（state+scene+draw 的 UTF-8 字节和，SQL 侧用
 * `length(CAST(x AS BLOB))`）。
 *
 * [status] 缺位时 LIVE 的未固定行会被当作"可回收缓存"删掉（用户已见的图消失）；它不参与
 * kind 判断（kind 只表达 INITIAL/LATEST 角色），两者是独立的轴。
 */
data class TutorVisualCheckpointSummary(
    val snapshotId: String,
    /** `checkpoint_kind`；NULL = 可回收中间态。 */
    val checkpointKind: String?,
    /** `status`：STAGING / LIVE。LIVE 永不进淘汰候选。 */
    val status: String,
    /** 被 `tutor_message_visual.current_snapshot_id` 或 `problem_revision_visual.snapshot_id` 引用。 */
    val pinned: Boolean,
    val storedBytes: Long,
)

internal object TutorVisualWriteChecks {
    /**
     * 程序行的**不可变载荷**比对。`created_at` 是元数据（首次写入获胜），不参与比较；
     * 其余每一列都在身份哈希或可执行判据里，任何差异都必须冲突而不是覆盖。
     */
    fun sameProgramPayload(
        entity: com.tingyun.smartmistakebook.core.database.entity.TutorVisualProgramEntity,
        write: TutorVisualProgramWrite,
    ): Boolean =
        entity.programId == write.programId &&
            entity.learnerId == write.learnerId &&
            entity.role == write.role &&
            entity.sourceSha256 == write.sourceSha256 &&
            entity.sourceJs == write.sourceJs &&
            entity.initialStateJson == write.initialStateJson &&
            entity.initialStateSha256 == write.initialStateSha256 &&
            entity.apiVersion == write.apiVersion &&
            entity.sdkVersion == write.sdkVersion &&
            entity.basisRevisionId == write.basisRevisionId &&
            entity.basisFingerprint == write.basisFingerprint &&
            entity.requestSnapshot == write.requestSnapshot

    /**
     * 快照的内容载荷比对。唯一键是 `(program, state, draw, renderer, font)`；若 scene/内容/
     * 视口/预览不同却被当作"同内容"静默复用，恢复时就会画出与初次显示不一致的图——所以这些
     * **内容列**必须比对。状态/租约/checkpoint/时间不参与比较（它们是角色与生命周期，不是内容）。
     *
     * **环境版本列参与比较**（schema 71 / S1 后翻转）：`renderer_version` /
     * `font_manifest_version` 自 v71 起进入唯一键与派生 id，"同键不同版本"在结构上已不可能；
     * 把它们留在比对之外会让**比对口径与身份口径再次分叉**——将来若唯一键/查重被改回三列，
     * 这里不会红，S1 就会静默复发。`runtime_version` / `compiler_version` 不进身份：它们只是
     * "能否再执行"的判据，不参与矢量重放（重放不需要运行时），所以仍不参与比较。
     */
    fun sameSnapshotPayload(
        entity: com.tingyun.smartmistakebook.core.database.entity.TutorVisualSnapshotEntity,
        write: TutorVisualSnapshotWrite,
    ): Boolean =
        // snapshot_id 是宿主标识，不是内容：同内容同版本不同 id 由唯一索引收敛到既有行。
        entity.programId == write.programId &&
            entity.stateJson == write.stateJson &&
            entity.stateSha256 == write.stateSha256 &&
            entity.sceneSchemaVersion == write.sceneSchemaVersion &&
            entity.sceneJson == write.sceneJson &&
            entity.sceneSha256 == write.sceneSha256 &&
            entity.renderSchemaVersion == write.renderSchemaVersion &&
            entity.drawCommandsJson == write.drawCommandsJson &&
            entity.drawCommandsSha256 == write.drawCommandsSha256 &&
            entity.contentIdsJson == write.contentIdsJson &&
            entity.viewportWidthDu == write.viewportWidthDu &&
            entity.viewportHeightDu == write.viewportHeightDu &&
            entity.fontScaleMilli == write.fontScaleMilli &&
            entity.theme == write.theme &&
            entity.timeMs == write.timeMs &&
            entity.seed == write.seed &&
            entity.rendererVersion == write.rendererVersion &&
            entity.fontManifestVersion == write.fontManifestVersion &&
            entity.questionVerificationStatus == write.questionVerificationStatus &&
            entity.previewAssetId == write.previewAssetId
}

// ---- 行 → 记录 / 写入载荷 → 行（D-02 的映射集中在这里，漏一列就是编译错误） ----

internal fun TutorVisualProgramEntity.toRecord() = TutorVisualProgramRecord(
    programId = programId,
    learnerId = learnerId,
    role = role,
    sourceSha256 = sourceSha256,
    sourceJs = sourceJs,
    initialStateJson = initialStateJson,
    initialStateSha256 = initialStateSha256,
    apiVersion = apiVersion,
    sdkVersion = sdkVersion,
    basisRevisionId = basisRevisionId,
    basisFingerprint = basisFingerprint,
    requestSnapshot = requestSnapshot,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun TutorVisualSnapshotEntity.toRecord() = TutorVisualSnapshotRecord(
    snapshotId = snapshotId,
    programId = programId,
    stateJson = stateJson,
    stateSha256 = stateSha256,
    sceneSchemaVersion = sceneSchemaVersion,
    sceneJson = sceneJson,
    sceneSha256 = sceneSha256,
    renderSchemaVersion = renderSchemaVersion,
    drawCommandsJson = drawCommandsJson,
    drawCommandsSha256 = drawCommandsSha256,
    contentIdsJson = contentIdsJson,
    viewportWidthDu = viewportWidthDu,
    viewportHeightDu = viewportHeightDu,
    fontScaleMilli = fontScaleMilli,
    theme = theme,
    timeMs = timeMs,
    seed = seed,
    runtimeVersion = runtimeVersion,
    compilerVersion = compilerVersion,
    rendererVersion = rendererVersion,
    fontManifestVersion = fontManifestVersion,
    questionVerificationStatus = questionVerificationStatus,
    previewAssetId = previewAssetId,
    status = status,
    ownerRequestId = ownerRequestId,
    checkpointKind = checkpointKind,
    createdAtEpochMillis = createdAtEpochMillis,
    promotedAtEpochMillis = promotedAtEpochMillis,
)

internal fun TutorMessageVisualEntity.toRecord() = TutorMessageVisualRecord(
    messageId = messageId,
    ordinal = ordinal,
    programId = programId,
    currentSnapshotId = currentSnapshotId,
    revision = revision,
    uiStateJson = uiStateJson,
    formDigest = formDigest,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun TutorVisualProgramWrite.toEntity() = TutorVisualProgramEntity(
    programId = programId,
    learnerId = learnerId,
    role = role,
    sourceSha256 = sourceSha256,
    sourceJs = sourceJs,
    initialStateJson = initialStateJson,
    initialStateSha256 = initialStateSha256,
    apiVersion = apiVersion,
    sdkVersion = sdkVersion,
    basisRevisionId = basisRevisionId,
    basisFingerprint = basisFingerprint,
    requestSnapshot = requestSnapshot,
    createdAtEpochMillis = createdAtEpochMillis,
)

internal fun TutorVisualSnapshotWrite.toEntity(ownerRequestId: String) = TutorVisualSnapshotEntity(
    snapshotId = snapshotId,
    programId = programId,
    stateJson = stateJson,
    stateSha256 = stateSha256,
    sceneSchemaVersion = sceneSchemaVersion,
    sceneJson = sceneJson,
    sceneSha256 = sceneSha256,
    renderSchemaVersion = renderSchemaVersion,
    drawCommandsJson = drawCommandsJson,
    drawCommandsSha256 = drawCommandsSha256,
    contentIdsJson = contentIdsJson,
    viewportWidthDu = viewportWidthDu,
    viewportHeightDu = viewportHeightDu,
    fontScaleMilli = fontScaleMilli,
    theme = theme,
    timeMs = timeMs,
    seed = seed,
    runtimeVersion = runtimeVersion,
    compilerVersion = compilerVersion,
    rendererVersion = rendererVersion,
    fontManifestVersion = fontManifestVersion,
    questionVerificationStatus = questionVerificationStatus,
    previewAssetId = previewAssetId,
    status = TutorVisualStatus.STAGING,
    ownerRequestId = ownerRequestId,
    checkpointKind = checkpointKind,
    createdAtEpochMillis = createdAtEpochMillis,
    promotedAtEpochMillis = null,
)
