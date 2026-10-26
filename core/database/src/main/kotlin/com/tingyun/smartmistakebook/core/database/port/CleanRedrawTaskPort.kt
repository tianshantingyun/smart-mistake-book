package com.tingyun.smartmistakebook.core.database.port

import com.tingyun.smartmistakebook.core.database.ClaimCleanRedrawTaskAttemptCommand
import com.tingyun.smartmistakebook.core.database.CleanRedrawTaskRecord
import com.tingyun.smartmistakebook.core.database.CreateCleanRedrawTaskCommand
import com.tingyun.smartmistakebook.core.database.FinishCleanRedrawTaskCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 净化作业台账（AUD-09，schema 65→66）的持久化端口。
 *
 * 默认全部"做不到"（null / 空表）：测试替身与无库装配没有这张表。默认值刻意选"做不到"而不是
 * "假装做到了"——台账的行才是"这次净化作业存在、待续跑"的凭据；把读不到当成"没有作业"是安全的，
 * 把写不进当成"已登记"则会让一次作业既没做也没人记得（正是本表要消灭的失败）。
 */
interface CleanRedrawTaskPort {
    /**
     * 建立一行作业（幂等：同一 `taskId` 已存在时返回既有行，绝不覆盖它的状态与尝试次数）。
     */
    suspend fun createCleanRedrawTask(
        command: CreateCleanRedrawTaskCommand,
    ): CleanRedrawTaskRecord? = null

    suspend fun readCleanRedrawTask(taskId: String): CleanRedrawTaskRecord? = null

    /**
     * 按分类请求指纹读一行（去重判据："同指纹已成功 → 这次不再付费"）。同一指纹存在多行时
     * 返回最早建立的那一行。
     */
    suspend fun readCleanRedrawTaskByFingerprint(
        requestFingerprint: String,
    ): CleanRedrawTaskRecord? = null

    /**
     * 启动对账的读口：`PENDING`/`FAILED` 且尝试次数未达 [maxAttempts]、且最后更新**不晚于**
     * [staleBeforeEpochMillis] 的行（按建立时刻升序）。
     *
     * 时间判据是**租约**：应用传"本进程启动时刻"，于是本进程刚发起的作业不会被对账抢走双跑
     * （那会重复付费），而上一进程被杀留下的行一定早于它。
     */
    suspend fun readResumableCleanRedrawTasks(
        maxAttempts: Int,
        staleBeforeEpochMillis: Long,
    ): List<CleanRedrawTaskRecord> = emptyList()

    /**
     * 领取一次尝试（比较交换，见 DAO KDoc）。返回领取后的行；领不到返回 null
     * （别人领走了 / 次数耗尽 / 已是终态）——调用方据此**不要再跑**。
     *
     * 领取**同时清空 `last_failure_code`**：那一列只对"最近一次已结束的尝试 + `FAILED`"成立
     * （`CleanRedrawTaskRecord` 的契约是有码 ⟺ `FAILED`），领取后这一行回到在途——不清的话，
     * 续跑一条 FAILED 行读回的就是"PENDING + 旧失败码"这种不合法形状，直接抛
     * `IllegalArgumentException`（首轮真实缺陷：失败行的续跑必断）。
     */
    suspend fun claimCleanRedrawTaskAttempt(
        command: ClaimCleanRedrawTaskAttemptCommand,
    ): CleanRedrawTaskRecord? = null

    /**
     * 落一次尝试的终态（比较交换：只对"领到的那一版"生效）。返回写成功后的行；写不进返回 null。
     */
    suspend fun finishCleanRedrawTask(
        command: FinishCleanRedrawTaskCommand,
    ): CleanRedrawTaskRecord? = null

    /**
     * 该题修订是否仍是其错题条目**当前指向**的修订（= 该记录的最新题面）。
     *
     * 晚到的净化结果只许挂到"书里现在显示的这一版"上：修订已不在、或条目已指向别处时返回
     * false，调用方标 `SUPERSEDED` 不挂（见 `attachCleanRedrawAsset` 的同一判据——
     * 挂接写入本身就按它拦，这里的读口供调用方在付费之前先判）。
     */
    suspend fun isCurrentProblemRevision(revisionId: String): Boolean = false

    /**
     * 一条题面修订上最近一次净化/显式重绘作业（按建立时刻倒序）。
     *
     * [operationIdPrefix] 非空时只匹配带此前缀的 `operation_id`（显式重绘用
     * `explicit-redraw-op:`，见 `explicitRedrawOperationId`）；null = 不筛操作类型。
     * 默认"没有"（null）：测试替身与无库装配不会假装看过台账。
     */
    suspend fun readLatestCleanRedrawTask(
        revisionId: String,
        operationIdPrefix: String? = null,
    ): CleanRedrawTaskRecord? = null

    /**
     * 与 [readLatestCleanRedrawTask] 同口径的观察口（详情页"进行中/失败/可重试"要用它，
     * 而不是轮询）。默认空流 = 没有台账可看。
     */
    fun observeLatestCleanRedrawTask(
        revisionId: String,
        operationIdPrefix: String? = null,
    ): Flow<CleanRedrawTaskRecord?> = flowOf(null)

    /**
     * 一条题面修订上已登记的视觉资产（角色 + 规范资产 id + 内容 sha），显式重绘据此选源图。
     *
     * 只返回引用存在的资产；修订不存在/没有视觉引用都返回空表（调用方按"没有可用源图"处理，
     * 不把空表读成一次成功）。默认空表：测试替身与无库装配不会伪造源图。
     */
    suspend fun readRevisionImageSources(problemRevisionId: String): List<RevisionImageSource> =
        emptyList()
}

/** 题面修订上的一条视觉引用（显式重绘选源图用；不携带文件路径，读字节仍走 vault）。 */
data class RevisionImageSource(
    val role: String,
    val sourceAssetId: String,
    val contentSha256: String,
) {
    init {
        require(role.isNotBlank()) { "A revision image source role must not be blank" }
        require(sourceAssetId.isNotBlank()) {
            "A revision image source asset id must not be blank"
        }
        require(contentSha256.length == 64) {
            "A revision image source sha must be sha256"
        }
    }
}
