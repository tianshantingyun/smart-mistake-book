package com.tingyun.smartmistakebook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull

/**
 * KD-61：恢复/清库关库前对**应用级 collector** 的硬性 quiesce。
 *
 * **它消灭的失败**：`SmartMistakeBookApplication` 在 applicationScope 里启动了一批
 * DB 写 collector（图生成僵尸对账、净化/显式重绘续跑、导出僵尸对账、切分导入双对账、
 * 知识包安装、学习库初始化/FSRS 重拟合……）。恢复的交换段只排空**接了写门**的写者，
 * 这些 collector 大多没接门（部分实现位于本批不可改的热区文件），于是：
 * 1. 关库瞬间它们可能带着在途 SQL——`close()` 与语句执行的原生竞态；
 * 2. 关库后它们再发起写，落进已关闭的 `PassthroughConnectionPool`（code 21）。
 *
 * 做法：取消整个 [scope]（进程此刻已注定重启，取消后 `launch` 变 no-op，杜绝"排空后
 * 又冒出新写"），然后限时 `join` 全部子协程——协程取消是协作式的，在途的 DAO 调用会
 * 把当前 SQL 跑完再退出，join 等"最后一笔归还连接"，这正好把上面两条都关掉。
 *
 * 语义与边界（如实登记到 KD-61，不是这里能修的）：
 * - 超时未 join 完返回 `false`，调用方照常走关库（恢复不能被挂死的 collector 无限拖住）；
 *   该残余仍属 KD-61 的"未排空"。
 * - WorkManager worker 走 `cancelAllWork()`（仓库侧已调），取消不保证 join——worker
 *   在途 SQL 的残余窗口不变。
 * - UI（viewModelScope/Compose）写不在 applicationScope，仍靠重启横幅 + 750ms 宽限。
 * - 返回后 applicationScope 终态：本进程余下生命周期里它不再接受新任务（进程等待重启，
 *   这是刻意的）。
 */
internal suspend fun quiesceApplicationScopeWriters(
    scope: CoroutineScope,
    timeoutMillis: Long,
): Boolean {
    require(timeoutMillis > 0L) { "A background writer quiesce timeout must be positive" }
    val job = scope.coroutineContext[Job] ?: return true
    job.cancel()
    // join 要在取消之后开始数：先 snapshot 当前子协程，再限时等它们全部收尾。
    val children = job.children.toList()
    return withTimeoutOrNull(timeoutMillis) {
        children.joinAll()
        true
    } ?: false
}
