package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.stableSha256

/**
 * 净化作业的状态取值（AUD-09，schema 65→66）。落库形状见
 * [com.tingyun.smartmistakebook.core.database.entity.CleanRedrawTaskEntity]。
 */
object CleanRedrawTaskStatus {
    /** 待做 / 在途：还没有终态。进程被杀留下的就是这一态，启动对账接手。 */
    const val PENDING = "PENDING"

    /** 终态：作业做完了（含"分类判定为纯文字题，不需要重绘"——作业本身完成，不是挂了图）。 */
    const val SUCCEEDED = "SUCCEEDED"

    /** 终态：最近一次尝试失败（原因见 `last_failure_code`；尝试次数未耗尽时对账仍会接手）。 */
    const val FAILED = "FAILED"

    /** 终态：不再有可挂的目标（修订/源图已不在，或该身份的工作已由另一行完成）——不挂、不再付费。 */
    const val SUPERSEDED = "SUPERSEDED"

    /** 可被对账接手的两种状态（终态里只有 FAILED 允许再试，且次数上限另行判定）。 */
    val RESUMABLE = listOf(PENDING, FAILED)

    /** 终态集合：写入终态之后的行走不到"再试"。 */
    val TERMINAL = listOf(SUCCEEDED, FAILED, SUPERSEDED)
}

/**
 * 净化作业的失败原因码（`last_failure_code`）。
 *
 * 这些码存在的唯一目的：失败**不再被吞**。改前分类轮/生成轮/挂接轮的失败在提交路径里被
 * 折成 false/null，学生看不到干净图、开发也不知道是哪一环断的。码只记"哪一环"，不记
 * 网络细节（那是模型台账与日志的事）。
 */
object CleanRedrawFailureCode {
    /** 分类轮没有给出判断（模型轮失败/拿到非分类产物/请求与旧行冲突）。 */
    const val CLASSIFY_FAILED = "CLASSIFY_FAILED"

    /** 净化生成没有产出（凭证门拒绝/网络失败/provider 失败/解码失败）。 */
    const val REDRAW_FAILED = "REDRAW_FAILED"

    /** 源图读不出来（文件缺失/被替换/字节核对不过）——不把不可信的字节发出去。 */
    const val SOURCE_UNREADABLE = "SOURCE_UNREADABLE"

    /** 挂接写入失败（引用写入抛错；修订是否还在由 SUPERSEDED 那一支判）。 */
    const val ATTACH_FAILED = "ATTACH_FAILED"
}

/**
 * 作业身份 → 任务 id：四个身份列（修订 / 源图资产 / 源图**字节** sha / 分类请求指纹）的
 * 合成摘要。同一身份重放得到同一 id（幂等行）；任一项不同就是另一行作业。
 *
 * 用长度前缀的规范串（不是简单拼接）：`ab`+`c` 与 `a`+`bc` 必须摘要到不同的 id。
 */
fun cleanRedrawTaskId(
    revisionId: String,
    sourceAssetId: String,
    sourceImageSha256: String,
    requestFingerprint: String,
): String = "clean-redraw-" + stableSha256(
    revisionId,
    sourceAssetId,
    sourceImageSha256,
    requestFingerprint,
)

/** 一次净化作业的持久行。字段口径见实体 KDoc。 */
data class CleanRedrawTaskRecord(
    val taskId: String,
    val revisionId: String,
    val sourceAssetId: String,
    val sourceImageSha256: String,
    val requestFingerprint: String,
    val subjectId: String,
    val status: String,
    val attemptCount: Int,
    val lastFailureCode: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(revisionId.isNotBlank()) { "A clean redraw revision id must not be blank" }
        require(sourceAssetId.isNotBlank()) { "A clean redraw source asset id must not be blank" }
        require(sourceImageSha256.length == 64) { "A clean redraw source sha must be sha256" }
        require(requestFingerprint.length == 64) {
            "A clean redraw request fingerprint must be sha256"
        }
        require(subjectId.isNotBlank()) { "A clean redraw subject id must not be blank" }
        require(status in CleanRedrawTaskStatus.TERMINAL + CleanRedrawTaskStatus.PENDING) {
            "Unknown clean redraw status $status"
        }
        require(attemptCount >= 0) { "A clean redraw attempt count must not be negative" }
        require(
            status != CleanRedrawTaskStatus.FAILED || lastFailureCode != null,
        ) { "A failed clean redraw task must carry its failure code" }
        require(
            status == CleanRedrawTaskStatus.FAILED || lastFailureCode == null,
        ) { "Only a failed clean redraw task may carry a failure code" }
        require(createdAtEpochMillis >= 0) {
            "A clean redraw creation time must not be negative"
        }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "A clean redraw update cannot precede its creation"
        }
    }
}

/**
 * 建立一行作业（幂等）：同一 `taskId` 已存在时不覆盖（既有行的状态/尝试次数才是权威）。
 */
data class CreateCleanRedrawTaskCommand(
    val taskId: String,
    val revisionId: String,
    val sourceAssetId: String,
    val sourceImageSha256: String,
    val requestFingerprint: String,
    val subjectId: String,
    val createdAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(revisionId.isNotBlank()) { "A clean redraw revision id must not be blank" }
        require(sourceAssetId.isNotBlank()) { "A clean redraw source asset id must not be blank" }
        require(sourceImageSha256.length == 64) { "A clean redraw source sha must be sha256" }
        require(requestFingerprint.length == 64) {
            "A clean redraw request fingerprint must be sha256"
        }
        require(subjectId.isNotBlank()) { "A clean redraw subject id must not be blank" }
        require(createdAtEpochMillis >= 0) {
            "A clean redraw creation time must not be negative"
        }
    }
}

/**
 * 领取一次尝试（比较交换）：只有"状态可试 + 尝试次数仍是读到的那一版 + 未达上限"时，
 * 次数才 +1 并把状态拉回 `PENDING`（在途）。
 *
 * **领取会清空 `last_failure_code`**：那一列记的是"最近一次已结束尝试失败在哪一环"，而领取之后
 * 这一行又回到在途——[CleanRedrawTaskRecord] 的契约是有码 ⟺ `FAILED`，不清就会让续跑一条
 * FAILED 行读回"PENDING + 旧失败码"这种不合法形状（首轮真实缺陷：失败行的续跑必断）。
 *
 * 领取失败 = 别人先领走了或次数已耗尽——两种都意味着**这次不要再跑**（费用上界与并发防线都在
 * 这一处）。
 */
data class ClaimCleanRedrawTaskAttemptCommand(
    val taskId: String,
    val expectedAttemptCount: Int,
    val maxAttempts: Int,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(expectedAttemptCount >= 0) {
            "An expected clean redraw attempt count must not be negative"
        }
        require(maxAttempts >= 1) { "A clean redraw attempt limit must be positive" }
        require(occurredAtEpochMillis >= 0) {
            "A clean redraw claim time must not be negative"
        }
    }
}

/**
 * 落一次尝试的终态（比较交换：只有"领到的仍是当前那一版"才写得进去——旧尝试的迟到结果
 * 不许覆盖新一轮的判定）。
 */
data class FinishCleanRedrawTaskCommand(
    val taskId: String,
    val expectedAttemptCount: Int,
    val status: String,
    val lastFailureCode: String?,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(expectedAttemptCount >= 0) {
            "An expected clean redraw attempt count must not be negative"
        }
        require(status != CleanRedrawTaskStatus.PENDING) {
            "A clean redraw finish must be a terminal status"
        }
        require(status in CleanRedrawTaskStatus.TERMINAL) {
            "Unknown clean redraw status $status"
        }
        require(status != CleanRedrawTaskStatus.FAILED || lastFailureCode != null) {
            "A failed clean redraw task must carry its failure code"
        }
        require(status == CleanRedrawTaskStatus.FAILED || lastFailureCode == null) {
            "Only a failed clean redraw task may carry a failure code"
        }
        require(occurredAtEpochMillis >= 0) {
            "A clean redraw finish time must not be negative"
        }
    }
}
