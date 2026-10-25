package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.dao.stableSha256

/**
 * 净化作业的状态取值（AUD-09，schema 65→66）。落库形状见
 * [com.tingyun.smartmistakebook.core.database.entity.CleanRedrawTaskEntity]。
 */
object CleanRedrawTaskStatus {
    /** 待做 / 在途：还没有终态。进程被杀留下的就是这一态，启动对账接手。 */
    const val PENDING = "PENDING"

    /**
     * 终态：图已生成并挂上（4E WG-03 起不再覆盖"分类判纯文字"——那是 [NO_FIGURE]）。
     */
    const val SUCCEEDED = "SUCCEEDED"

    /** 终态：最近一次尝试失败（原因见 `last_failure_code`；尝试次数未耗尽时对账仍会接手）。 */
    const val FAILED = "FAILED"

    /** 终态：不再有可挂的目标（修订/源图已不在，或该身份的工作已由另一行完成）——不挂、不再付费。 */
    const val SUPERSEDED = "SUPERSEDED"

    /**
     * 终态（4E WG-03）：分类判定这道题**不需要生成图**——没有产图、没有挂接。
     *
     * **它消灭的失败**：改前 TEXT_ONLY 直接落 `SUCCEEDED`，与"图已产并挂上"不可区分——
     * 读台账的一方（显式重绘的源图选择、对账的"已做完"判据、人工排查）会把"无需生成"
     * 误读成"已产图"。`SUCCEEDED` 从此只表"图真的挂上了"。
     */
    const val NO_FIGURE = "NO_FIGURE"

    /**
     * 待核实（4E WG-03）：这一尝试的**生成请求已发出、结果未知**（进程死在生成轮）。
     *
     * 对领取与对账按终态处理（不可再领、`resumePending` 不接手）：**不盲目补发**——外部
     * 调用不保证恰好一次，Provider 的幂等/查询能力**未经实际验证前不得拿来当重发的依据**
     * （WG-03 边界）。宁可这一行停在待核实（用户看原图），也不赌一次可能重复付费的重发。
     * 结果已知的正常路径不受影响：同一尝试在生成返回后用同一 finish CAS 覆写为
     * `SUCCEEDED`/`FAILED`。
     */
    const val AWAITING_VERIFICATION = "AWAITING_VERIFICATION"

    /** 可被对账接手的两种状态（终态里只有 FAILED 允许再试，且次数上限另行判定）。 */
    val RESUMABLE = listOf(PENDING, FAILED)

    /** 终态集合：写入终态之后的行走不到"再试"（领取 CAS 只认 FAILED 与首领 PENDING）。 */
    val TERMINAL = listOf(SUCCEEDED, FAILED, SUPERSEDED, NO_FIGURE, AWAITING_VERIFICATION)
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
 * 用户显式重绘作业的 `clean_redraw_task.operation_id` 前缀（4E WG-06 T3）。
 *
 * **它消灭的具体失败**：保存期净化作业与用户在详情页发起的显式重绘共用同一张
 * `clean_redraw_task` 表。若没有可区分的身份，启动对账会把显式重绘行当成保存期作业，
 * 走"先分类、TEXT_ONLY 就不动作"的路径——用户明明点了重绘，行却被标成"完成"，题面
 * 永远停在原图。
 *
 * 保存期净化行的 `operation_id` 保持 NULL（旧链逐位不变）；只有显式重绘行带这个前缀。
 */
const val EXPLICIT_REDRAW_OPERATION_ID_PREFIX: String = "explicit-redraw-op:"

/** 一次显式重绘作业的操作 id（确定性：同一个 `task_id` 永远得到同一个操作 id）。 */
fun explicitRedrawOperationId(taskId: String): String {
    require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
    return EXPLICIT_REDRAW_OPERATION_ID_PREFIX + taskId
}

/** 这一行是不是用户显式重绘（而不是保存期自动净化）。NULL/其它前缀都是保存期作业。 */
fun isExplicitRedrawOperation(operationId: String?): Boolean =
    operationId != null && operationId.startsWith(EXPLICIT_REDRAW_OPERATION_ID_PREFIX)

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
    /**
     * 这次净化/重绘作业对应的图生成操作（4E WG-08，schema 69 可空列）。
     * NULL = 保存期净化或历史作业；`explicit-redraw-op:` 前缀 = 用户显式重绘。
     * 它只指向审计台账，**不是**预算：尝试上限仍看 [attemptCount]。
     */
    val operationId: String? = null,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(operationId == null || operationId.isNotBlank()) {
            "A clean redraw operation id must be null or non-blank"
        }
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
    /** 显式重绘传 `explicit-redraw-op:<taskId>`；保存期净化保持 null（旧链逐位不变）。 */
    val operationId: String? = null,
) {
    init {
        require(taskId.isNotBlank()) { "A clean redraw task id must not be blank" }
        require(operationId == null || operationId.isNotBlank()) {
            "A clean redraw operation id must be null or non-blank"
        }
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
    /**
     * 在途租约截止：`PENDING` + 已领取（`attempt_count > 0`）的行只有在
     * `updated_at_epoch_millis <=` 本值时才能再次领取。
     *
     * `PENDING` 同时表示"待做"与"在途"。没有这道租约时，第二个并发请求会读到第一次领取
     * 之后的 `attempt_count = n+1` 并以它为期望值再领取到 n+2——同一作业两个执行者同时
     * 出网、重复付费，终态还可能互相覆盖。首次领取（`attempt_count == 0`）与 `FAILED`
     * 后的重试不受此限；启动对账传"本进程启动时刻"，上一进程留下的在途行仍能被接手。
     *
     * 必填：新请求/`executeTask` 传 0（只领首次或 FAILED 重试），启动对账传进程启动时刻。
     */
    val staleBeforeEpochMillis: Long,
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
        require(staleBeforeEpochMillis >= 0) {
            "A clean redraw claim lease must not be negative"
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
