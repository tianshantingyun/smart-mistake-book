package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 一次**图生成操作**的持久身份与审计镜像（4E WG-08，schema 68→69）。
 *
 * **它消灭的具体失败**：改前一次配图/重绘只活在那个协程里——进程被杀后，"这次生成到底
 * 用哪个来源、基于哪一版输入、按哪套策略、派发了几次远程调用、是不是动用了最终答案预算"
 * 全部不可考；同一来源的两次生成（例如题目图与讲解图）在日志里也分不开。这张表把一次
 * 操作的**冻结输入**（`basis_fingerprint`）与**策略版本**（`policy_version`）钉在行上，
 * 让"为什么是这张图 / 为什么没图"从推断变成可查。
 *
 * **它不是预算权威**：`reserved_remote_dispatches` / `used_remote_dispatches` /
 * `final_answer_reserved` 只是审计镜像；真正拦截派发的计数仍只由
 * `model_task_operation.dispatch_count` 与 `clean_redraw_task.attempt_count` 持有。
 * 把预算读成"这两列"会双重计费，所以本表**没有任何指向预算表的外键**，也不参与它们的
 * 状态迁移。
 *
 * 列的口径：
 * - `operation_id`：内容寻址身份（复用现有 `figure-op:` / `clean-redraw-op:` /
 *   `attached-image-op:` 字符串）。**不含 method**：换方法重试是同一次操作的不同尝试。
 * - `source_kind` / `source_id`：来源的软引用（TUTOR_MESSAGE / PROBLEM_REVISION /
 *   CLEAN_REDRAW / CAPTURE_PARSE）。多态来源不建外键——同一条操作记录要能指向不同宿主表。
 * - `purpose`：现有 `FigureGenerationPurpose` 的名字（SOURCE_RECONSTRUCTION / QUESTION_DIAGRAM /
 *   EXPLANATION / ILLUSTRATION / PIXEL_CLEANUP / SOURCE_TRANSCRIPTION）。
 * - `status`：PENDING → RUNNING → SUCCEEDED / FAILED / SUPERSEDED / CANCELLED。
 * - 三个审计计数**只写终态事实**，不做比较交换、不参与预算判定。
 * - `created_at_epoch_millis` / `updated_at_epoch_millis`：建立与最近状态变化时刻
 *   （启动对账按 `(status, updated_at)` 扫僵尸）。
 *
 * 纯**非破坏**新增（v68→69）：只建一张新表与两条索引；存量库升级后该表为空——历史生成
 * 没有台账可考（"无台账"如实呈现，不编造历史操作）。
 */
@Entity(
    tableName = "figure_generation_operation",
    indices = [
        Index(value = ["source_kind", "source_id"]),
        Index(value = ["status", "updated_at_epoch_millis"]),
    ],
)
internal data class FigureGenerationOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "source_kind")
    val sourceKind: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    /** 冻结输入（修订/题面/源图 sha 的组合）；同一操作重放必须命中同一值。 */
    @ColumnInfo(name = "basis_fingerprint")
    val basisFingerprint: String,
    val purpose: String,
    @ColumnInfo(name = "policy_version")
    val policyVersion: String,
    val status: String,
    /** 审计镜像：预定过的远程派发次数（**不是**预算权威）。 */
    @ColumnInfo(name = "reserved_remote_dispatches")
    val reservedRemoteDispatches: Int,
    /** 审计镜像：实际用掉的远程派发次数（**不是**预算权威）。 */
    @ColumnInfo(name = "used_remote_dispatches")
    val usedRemoteDispatches: Int,
    /** 审计镜像：这次操作是否动用过"最终答案"通道（**不是**预算权威）。 */
    @ColumnInfo(name = "final_answer_reserved")
    val finalAnswerReserved: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

/**
 * 一次图生成操作的**单次尝试**（4E WG-08，schema 68→69）。
 *
 * **它消灭的具体失败**：改前"这次失败到底断在方法选择、Provider、还是产物落盘"只存在于
 * 一段被 `runCatching` 吞掉的异常里；同一个操作重试几次也无处可数——晚到的旧尝试甚至可能
 * 覆盖新尝试的结论。一行一次尝试（`(operation_id, attempt_ordinal)` 唯一），终态 attempt
 * 不许复活；`output_asset_id` 让"这次尝试真的产出了哪张图"与资产表形成 RESTRICT 引用
 * （删资产前必须先处理台账，不许让证据指向一个已不存在的文件）。
 *
 * 列的口径：
 * - `attempt_id`：含 method 与 ordinal 的内容寻址身份；同一操作换方法/重试是不同 attempt。
 * - `operation_id`：父操作（RESTRICT：删父行不许抹掉尝试证据）。
 * - `attempt_ordinal`：同一操作内唯一；领取一次占一个序号。**两条序列互不相交**：
 *   远程方法（IMAGE_MODEL）的正整数来自账本 `dispatchCount`/`attemptCount`；本地方法
 *   （JS）用负数区间（-1、-2、…），避免"本地先占 1、远程拿到 1"撞唯一索引后丢审计行。
 * - `method`：IMAGE_MODEL / JS（JS 为本地引擎执行，不占远程派发）。
 * - `status`：RUNNING → SUCCEEDED / FAILED / CANCELLED（终态不再变）。
 * - `provider_snapshot` / `model_snapshot`：这次实际用的 Provider/模型快照，未知时 NULL。
 * - `source_sha256`：输入源图的字节哈希；纯文字生成（无源图）时为 NULL。
 * - `input_fingerprint`：本次请求的逻辑指纹（重放/去重与"输入是否变过"的判据）。
 * - `output_asset_id`：成功产物（可空：失败没有产物；RESTRICT 引用规范资产）。
 * - `reservation_id`：预算预留句柄（可空：失败可能发生在预留之前；它只指向账本，
 *   不在这里重复记账）。
 * - `diagnostics`：有界失败/取消原因（成功时 NULL）。
 * - `started_at_epoch_millis` / `ended_at_epoch_millis`：开始与结束时刻；未结束时后者 NULL，
 *   启动对账据此识别僵尸 RUNNING。
 *
 * 纯**非破坏**新增（v68→69）：只建一张新表、一条唯一索引与一条查询索引；既有表一列不删、
 * 一行不动、不回填。
 */
@Entity(
    tableName = "figure_generation_attempt",
    foreignKeys = [
        ForeignKey(
            entity = FigureGenerationOperationEntity::class,
            parentColumns = ["operation_id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CanonicalSourceAssetEntity::class,
            parentColumns = ["source_asset_id"],
            childColumns = ["output_asset_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["operation_id", "attempt_ordinal"], unique = true),
        Index(value = ["output_asset_id"]),
    ],
)
internal data class FigureGenerationAttemptEntity(
    @PrimaryKey
    @ColumnInfo(name = "attempt_id")
    val attemptId: String,
    @ColumnInfo(name = "operation_id")
    val operationId: String,
    @ColumnInfo(name = "attempt_ordinal")
    val attemptOrdinal: Int,
    val method: String,
    val status: String,
    @ColumnInfo(name = "provider_snapshot")
    val providerSnapshot: String? = null,
    @ColumnInfo(name = "model_snapshot")
    val modelSnapshot: String? = null,
    @ColumnInfo(name = "source_sha256")
    val sourceSha256: String? = null,
    @ColumnInfo(name = "input_fingerprint")
    val inputFingerprint: String,
    @ColumnInfo(name = "output_asset_id")
    val outputAssetId: String? = null,
    @ColumnInfo(name = "reservation_id")
    val reservationId: String? = null,
    val diagnostics: String? = null,
    @ColumnInfo(name = "started_at_epoch_millis")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis")
    val endedAtEpochMillis: Long? = null,
)
