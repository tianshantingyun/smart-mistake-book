package com.tingyun.smartmistakebook.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * 一次 A4 导出（单题 / 当前筛选）的持久记录（阶段 4A 批 4 · L7，schema 60→61）。
 *
 * **它消灭的失败**：改前导出是页面生命周期内的前台任务——学生一离开页面，渲染就被取消，
 * 没有任何地方留下"这次导出做没做完、文件叫什么、在不在"。这一行让导出成为**后台任务**的
 * 持久身份：worker 在进程外完成渲染后把结果写回这里，通知与「导出成果」入口都读它。
 *
 * 列的口径：
 * - `export_id`：一次导出的唯一身份（入队时生成）。worker 输入只带它 + 渲染所需的键，
 *   结果落回同一行——重放（进程重建、work 重跑）不会产生第二份记录。
 * - `kind`：`SINGLE` / `BATCH`，只用于展示与失败文案分流。
 * - `display_name`：**成功前为 NULL**——文件名要到 worker 读到不可变快照（标题/版次/纳入题数）
 *   才能确定，提前编一个名字写在这里就是撒谎。
 * - `status`：`RUNNING` → `SUCCEEDED` / `FAILED`，终态不回头（重跑同一 `export_id` 只在
 *   RUNNING 行上写结果，不覆盖已有终态）。
 * - `input_sha256` / `pdf_sha256` / `page_count`：成功后重开这一份产物的钥匙
 *   （与 `MistakePdfExporter.reopenVerified` 的 token 同形）；失败时三列都是 NULL。
 * - `failure_message`：失败原因的学生可读文案；成功时 NULL。与终态同进同出。
 * - `created_at_epoch_millis` / `finished_at_epoch_millis`：入队时刻与终态时刻；
 *   进行中的行第二列是 NULL。
 *
 * 不存渲染请求的键：worker 的 WorkManager 输入（同样的键 + `export_id`）才是请求的权威载体，
 * 记录表只承载"结果"。将来若要"重新导出"，应从触发点重新收集当时的筛选条件，而不是重放旧键
 * （旧键指向的版本可能已经不是学生看到的那一版）。
 */
@Entity(tableName = "mistake_export_record")
internal data class MistakeExportRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "export_id")
    val exportId: String,
    val kind: String,
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,
    val status: String,
    @ColumnInfo(name = "input_sha256")
    val inputSha256: String? = null,
    @ColumnInfo(name = "pdf_sha256")
    val pdfSha256: String? = null,
    @ColumnInfo(name = "page_count")
    val pageCount: Int? = null,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "finished_at_epoch_millis")
    val finishedAtEpochMillis: Long? = null,
)
