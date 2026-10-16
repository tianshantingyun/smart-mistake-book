package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

/**
 * 一次「分类 → 净化（重绘）→ 挂接」作业的**持久身份**（AUD-09，schema 65→66）。
 *
 * **它消灭的失败**：改前净化重绘是提交路径里一段 fire-and-forget 的协程——进程被杀，这次
 * 重绘就**没了**（题面永远停在原图，没有任何地方记得"还欠一次作业"）；失败被 `runCatching`
 * 吞成 false/null（"为什么没干净图"不可考）；晚到的结果照样往修订上挂（可能挂到一个已经被
 * 新题面取代的修订上）；续跑若靠重放已提交操作，会**静默再付一次费**。
 *
 * 列的口径：
 * - `task_id`：作业身份 = 四个身份列的合成摘要（见 `cleanRedrawTaskId`）：
 *   `revision_id`（挂接目标：题面修订）+ `source_asset_id` + `source_image_sha256`（源图**字节**
 *   ——分类指纹带它才成立）+ `request_fingerprint`（分类请求的**逻辑操作指纹**：内容/草稿身份/
 *   源图 sha，不含时刻与同意位——同一操作的两次触发必须命中同一行，去重才有意义）。
 * - `subject_id`：重建同一轮分类请求所需的题目身份（提交路径传草稿 id）。**不在身份摘要里**：
 *   它是请求的输入而不是作业的身份；但少了它就无法在续跑时复算出同一个请求指纹，
 *   于是分类轮会变成一次新的付费派遣。
 * - `status`：`PENDING`（待做/在途）→ `SUCCEEDED` / `FAILED` / `SUPERSEDED`（终态）。
 *   `SUCCEEDED` 也覆盖"分类判定为纯文字题，不需要重绘"——作业本身的终态，不是"挂了图"。
 * - `attempt_count`：**已经开始过**的尝试次数。领取一次加一（见 DAO 的 CAS）；达到
 *   `MAX_CLEAN_REDRAW_ATTEMPTS` 后对账不再接手——费用因此有上界。
 * - `last_failure_code`：最近一次失败的原因码（`CleanRedrawFailureCode`）；成功时清空。
 *   失败**不再被吞**：原因码留在这一列上，"为什么没有干净图"从不可考变成可查。
 * - `created_at_epoch_millis` / `updated_at_epoch_millis`：建立时刻与最近一次状态变化时刻
 *   （领取、落终态都更新它；启动对账按它判"这一行是不是本进程之外留下的"）。
 *
 * 纯**非破坏**新增（v65→66）：只建一张新表与两条索引，既有表一列不删、一行不动、不回填——
 * 存量库升级后该表为空：历史重绘没有台账可考，不编造。
 */
@Entity(
    tableName = "clean_redraw_task",
    primaryKeys = ["task_id"],
    indices = [
        Index(value = ["request_fingerprint"]),
        Index(value = ["status"]),
    ],
)
internal data class CleanRedrawTaskEntity(
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "revision_id")
    val revisionId: String,
    @ColumnInfo(name = "source_asset_id")
    val sourceAssetId: String,
    @ColumnInfo(name = "source_image_sha256")
    val sourceImageSha256: String,
    @ColumnInfo(name = "request_fingerprint")
    val requestFingerprint: String,
    @ColumnInfo(name = "subject_id")
    val subjectId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "last_failure_code")
    val lastFailureCode: String?,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
