package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * **可重放视觉程序**（批次 30 / D-01，schema 69→70）。
 *
 * **它消灭的具体失败**：改前"一张图"只活在消息正文与一张 PNG 里——重启、换页、导出之后
 * 只剩像素，这张图**从哪段源码来、按哪版题面算的、能不能重放**全部不可考；更糟的是同一个
 * 程序可能被原地改写源码，于是消息里已经显示过的图会静默变成另一张图。
 *
 * 因此 [programId] 是**内容派生**的不可变身份（`visual-program-` + sha256(源码 ‖ 初始 state ‖
 * basis ‖ api ‖ sdk ‖ role)，含长度前缀），并且本表**不提供任何 UPDATE/删除路径**：源码、
 * 初始状态、basis、版本、角色的任一变化都产生**另一个** [programId]，旧行原样保留
 * （与 `problem_revision` 的 append-only 同一纪律，`ProblemEntities.kt:26`）。
 * 对外 `visualId` 与本表主键**同一个值**（`VisualExecutionRequest` KDoc 的既有约定）。
 *
 * 列的口径：
 * - `source_js`：原始源码（有界，`VisualExecutionBudget.MAX_SOURCE_JS_BYTES` = 64 KiB；
 *   空间上限由写入方在 S0 预检，本表不重复设 CHECK）。
 * - `initial_state_json` / `initial_state_sha256`：StateV1 初始状态与其字节哈希。
 * - `api_version` / `sdk_version`：**能否再执行**的判据（只服务"用户明确要求重跑"那一档）；
 *   它们不参与矢量重放门（重放不需要运行时）。
 * - `basis_revision_id`：题面修订的**软引用**（多态来源不建外键，理由同
 *   `figure_generation_operation.source_id`，`FigureGenerationEntities.kt:28-29`）。
 * - `request_snapshot`：原模型请求/来源快照，**有界**（约定 ≤2 KiB；超限写 NULL 而不是截断
 *   成半截 JSON）。
 *
 * 纯**非破坏**新增（v69→70）：只建表与索引；存量库升级后本表为空——历史图没有程序可考
 * （"无快照"如实呈现，不编造历史程序）。
 */
@Entity(
    tableName = "tutor_visual_program",
    indices = [
        Index(value = ["learner_id", "created_at_epoch_millis"]),
        Index(value = ["basis_revision_id"]),
        Index(value = ["source_sha256"]),
    ],
)
internal data class TutorVisualProgramEntity(
    @PrimaryKey
    @ColumnInfo(name = "program_id")
    val programId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    /** QUESTION_DIAGRAM / TUTOR_EXPLANATION（宿主派生，进身份哈希）。 */
    val role: String,
    @ColumnInfo(name = "source_sha256")
    val sourceSha256: String,
    @ColumnInfo(name = "source_js")
    val sourceJs: String,
    @ColumnInfo(name = "initial_state_json")
    val initialStateJson: String,
    @ColumnInfo(name = "initial_state_sha256")
    val initialStateSha256: String,
    @ColumnInfo(name = "api_version")
    val apiVersion: Int,
    @ColumnInfo(name = "sdk_version")
    val sdkVersion: String,
    @ColumnInfo(name = "basis_revision_id")
    val basisRevisionId: String?,
    @ColumnInfo(name = "basis_fingerprint")
    val basisFingerprint: String,
    @ColumnInfo(name = "request_snapshot")
    val requestSnapshot: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

/**
 * 一个程序的**一次可重放快照**（批次 30 / D-01，schema 69→70）。
 *
 * **它消灭的具体失败**：改前"图"与"能重放这张图的数据"是两件事——像素存下来了，画出它的
 * 状态、场景、DrawOps、视口、字体缩放、渲染版本却没地方落，"下次打开重画一遍"只能靠再跑
 * 一次 JS（起隔离进程、依赖源码版本、可能画出另一张图）。本行把 [drawCommandsJson] 与
 * 它的全部输入钉在一起：恢复路径只读这一行 + 解析器 + painter，**不执行 JS**。
 *
 * 状态经 `STAGING → LIVE` 单向迁移（[status]）：
 * - `STAGING`：已算出但尚未被任何消息/题面引用，[ownerRequestId] 记活动 owner 用于对账；
 * - `LIVE`：**终态**（[promotedAtEpochMillis] 非空）。没有 `LIVE → *` 的回退——回退等于
 *   用户看到过的图"消失"。
 *
 * 唯一索引 `(program_id, state_sha256, draw_commands_sha256)` 让**同内容快照只存一份**
 * （重复 checkpoint 是幂等读回，不是新行）。
 *
 * 列的口径：
 * - `scene_schema_version` / `render_schema_version` / `renderer_version`：矢量重放门的三个
 *   输入（**runtime/compiler 不参与重放**——重放不需要运行时；把它们塞进门会让应用升级后
 *   旧图全部降级）。
 * - `font_manifest_version`：像素一致门的输入；仓内尚无"可验证字体清单"，在清单落地前
 *   调用方只能保守走预览路径（设计 §4.2）。
 * - `viewport_width_du` / `viewport_height_du` / `font_scale_milli`：回放尺寸（1 du = 1 dp；
 *   缩放用整数千分比存储，避免浮点在 schema 里的确定性争议）。
 * - `preview_asset_id`：可选预览 PNG（`canonical_source_asset` 的 **RESTRICT** 引用，
 *   即资产引用面的**第六类站点**——漏登记会让孤儿清理删掉正在显示的画面）。
 * - `checkpoint_kind`：INITIAL（重建基线，永不变） / LATEST / NULL（可回收中间态）。
 *
 * 纯**非破坏**新增（v69→70）：只建表与索引；新表为空，旧行零改动。
 */
@Entity(
    tableName = "tutor_visual_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = TutorVisualProgramEntity::class,
            parentColumns = ["program_id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CanonicalSourceAssetEntity::class,
            parentColumns = ["source_asset_id"],
            childColumns = ["preview_asset_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["program_id", "state_sha256", "draw_commands_sha256"], unique = true),
        Index(value = ["program_id", "promoted_at_epoch_millis"]),
        Index(value = ["preview_asset_id"]),
        Index(value = ["status", "created_at_epoch_millis"]),
    ],
)
internal data class TutorVisualSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    @ColumnInfo(name = "program_id")
    val programId: String,
    @ColumnInfo(name = "state_json")
    val stateJson: String,
    @ColumnInfo(name = "state_sha256")
    val stateSha256: String,
    @ColumnInfo(name = "scene_schema_version")
    val sceneSchemaVersion: Int,
    @ColumnInfo(name = "scene_json")
    val sceneJson: String,
    @ColumnInfo(name = "scene_sha256")
    val sceneSha256: String,
    @ColumnInfo(name = "render_schema_version")
    val renderSchemaVersion: Int,
    @ColumnInfo(name = "draw_commands_json")
    val drawCommandsJson: String,
    @ColumnInfo(name = "draw_commands_sha256")
    val drawCommandsSha256: String,
    /** `Rendered.contentIds` 的冻结集合（解析器的 `frozenContentIds` 入参）。 */
    @ColumnInfo(name = "content_ids_json")
    val contentIdsJson: String,
    @ColumnInfo(name = "viewport_width_du")
    val viewportWidthDu: Int,
    @ColumnInfo(name = "viewport_height_du")
    val viewportHeightDu: Int,
    @ColumnInfo(name = "font_scale_milli")
    val fontScaleMilli: Int,
    val theme: String,
    @ColumnInfo(name = "time_ms")
    val timeMs: Long,
    val seed: Long,
    @ColumnInfo(name = "runtime_version")
    val runtimeVersion: String,
    @ColumnInfo(name = "compiler_version")
    val compilerVersion: String,
    @ColumnInfo(name = "renderer_version")
    val rendererVersion: String,
    @ColumnInfo(name = "font_manifest_version")
    val fontManifestVersion: String,
    /** NOT_CHECKED / VERIFIED / MISMATCH（题面图核对状态）。 */
    @ColumnInfo(name = "question_verification_status")
    val questionVerificationStatus: String,
    @ColumnInfo(name = "preview_asset_id")
    val previewAssetId: String?,
    /** STAGING / LIVE（LIVE 是终态）。 */
    val status: String,
    @ColumnInfo(name = "owner_request_id")
    val ownerRequestId: String?,
    /** INITIAL / LATEST / NULL（可回收中间态）。 */
    @ColumnInfo(name = "checkpoint_kind")
    val checkpointKind: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "promoted_at_epoch_millis")
    val promotedAtEpochMillis: Long? = null,
)

/**
 * 一条会话消息上的**图引用**（批次 30 / D-01，schema 69→70）。
 *
 * **它消灭的具体失败**：改前"消息里那张图"与"能重放它的快照"没有持久联系——消息正文活着，
 * 图却只能按像素猜；或者反过来，快照 LIVE 了而消息没写成，恢复时不知信谁。
 *
 * 两个结构性约束：
 * 1. 主键 `(message_id, ordinal)` = 契约的"messageId+ordinal 唯一"（同一条消息的第 n 张图）；
 * 2. [currentSnapshotId] **NOT NULL** + 写入走 `INSERT … SELECT … WHERE status='LIVE'`
 *    （[com.tingyun.smartmistakebook.core.database.dao.TutorVisualDao]）：不是靠 code review
 *    保证"只引用 LIVE 快照"，而是这条 INSERT 本身在非 LIVE 时一行也写不进。
 *
 * 删除语义：[messageId] 对 `tutor_message` 是 **CASCADE**（引用行随宿主消息走），
 * [programId] / [currentSnapshotId] 一律 **RESTRICT**（删程序/快照不许抹掉仍在被消息固定的图）。
 * 会话删除只带走 link，不带走 program/snapshot（与"删会话不删保存题引用"同一纪律）。
 *
 * [revision] 是指针 CAS 的期望版本（从 1 起）：迟到 checkpoint 用旧 revision 更新时命中 0 行，
 * 不许覆盖新状态。
 */
@Entity(
    tableName = "tutor_message_visual",
    primaryKeys = ["message_id", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = TutorMessageEntity::class,
            parentColumns = ["message_id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TutorVisualProgramEntity::class,
            parentColumns = ["program_id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TutorVisualSnapshotEntity::class,
            parentColumns = ["snapshot_id"],
            childColumns = ["current_snapshot_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["program_id"]),
        Index(value = ["current_snapshot_id"]),
    ],
)
internal data class TutorMessageVisualEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    val ordinal: Int,
    @ColumnInfo(name = "program_id")
    val programId: String,
    @ColumnInfo(name = "current_snapshot_id")
    val currentSnapshotId: String,
    /** 指针 CAS 的期望版本（从 1 起，换图/重渲染时 +1）。 */
    val revision: Int,
    @ColumnInfo(name = "ui_state_json")
    val uiStateJson: String?,
    @ColumnInfo(name = "form_digest")
    val formDigest: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

/**
 * 已保存题面修订上的**固定图引用**（批次 30 / D-01，schema 69→70）。
 *
 * **它消灭的具体失败**：改前"已保存题的图"只能靠"讲这道题的那个会话还活着"这个脆弱的间接
 * 判据维持——会话被裁掉/关掉，保存题的图就从备份与 GC 的视野里消失（契约 §8 明写"禁止靠
 * 会话还活着维持已保存题的图"）。这张表让**保存题自己持有**引用，会话删除不影响它。
 *
 * 主键 `(problem_revision_id, role, ordinal)` 镜像 `problem_revision_source_asset` 的
 * `(problem_revision_id, source_asset_id, role)`（v69 先例，`CaptureEntities.kt:186-187`）：
 * 同一修订同一角色下可有多张图，[ordinal] 记顺序（DEFAULT 0 = 旧/单图场景的第一张）。
 *
 * [problemRevisionId] 对 `problem_revision` 是 RESTRICT：修订 append-only，删修订本来就是
 * 被禁止的方向；[programId] / [snapshotId] 同样 RESTRICT（被固定的图不许悄悄消失）。
 *
 * 与消息 link 同一条**结构门**（批次 30 复核修复）：写入只走
 * `TutorVisualDao.pinRevisionVisual` 的 `INSERT … SELECT … WHERE status='LIVE'`，STAGING
 * 快照写不进题面引用（否则被取消的 STAGING 会被永久 pin、既回收不了也重放不了）。
 */
@Entity(
    tableName = "problem_revision_visual",
    primaryKeys = ["problem_revision_id", "role", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = ProblemRevisionEntity::class,
            parentColumns = ["revision_id"],
            childColumns = ["problem_revision_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TutorVisualProgramEntity::class,
            parentColumns = ["program_id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TutorVisualSnapshotEntity::class,
            parentColumns = ["snapshot_id"],
            childColumns = ["snapshot_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["program_id"]),
        Index(value = ["snapshot_id"]),
    ],
)
internal data class ProblemRevisionVisualEntity(
    @ColumnInfo(name = "problem_revision_id")
    val problemRevisionId: String,
    /** 与 `tutor_visual_program.role` 同值域。 */
    val role: String,
    @ColumnInfo(name = "ordinal", defaultValue = "0")
    val ordinal: Int,
    @ColumnInfo(name = "program_id")
    val programId: String,
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)
