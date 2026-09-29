package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskSnapshot
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Provider adapter. Implementations cannot access Room or mutate learning facts. */
interface ModelGateway {
    suspend fun capabilities(): ProviderCapabilitySnapshot

    fun execute(execution: ModelGatewayExecution): Flow<ModelGatewayEvent>
}

/**
 * Durable application boundary for model work. Every emitted state has already been persisted, so
 * UI collectors may leave and return without owning the operation's source of truth.
 */
interface ModelTaskRepository {
    suspend fun capabilities(): ProviderCapabilitySnapshot

    fun observe(requestId: String): Flow<ModelTaskSnapshot?>

    /** Durable conversation recovery, bounded to one subject and task kind. */
    fun observeBySubject(
        subjectId: String,
        kind: ModelTaskKind,
    ): Flow<List<ModelTaskSnapshot>> = flowOf(emptyList())

    /** Recent durable recovery, oldest-to-newest, without loading an unbounded conversation. */
    fun observeRecentBySubject(
        subjectId: String,
        kind: ModelTaskKind,
        limit: Int,
    ): Flow<List<ModelTaskSnapshot>> {
        require(limit > 0) { "Recent model-task limit must be positive" }
        return observeBySubject(subjectId, kind).map { snapshots -> snapshots.takeLast(limit) }
    }

    fun execute(request: ModelTaskRequest): Flow<ModelTaskSnapshot>

    /**
     * 学生按下「停止」（A2）：取消这一次派发，并把任务落进既有 [ModelTaskSnapshot] 的
     * `CANCELLED` 终态。
     *
     * 消灭的具体失败：没有用户触发的取消路径——`CANCELLED` 在状态机里存在、在界面文案里存在，
     * 但**没有任何东西会把它推出来**；学生想让一段正在生成的回复停下来时，唯一的做法是离开页面。
     *
     * 语义（实现方必须同时满足）：
     * - **落终态**：取消之后那一行的状态是 `CANCELLED`，不是"悬着"；界面因此不需要靠内存判断
     *   "这一轮结束了没有"，进程死亡后读回同一行也得到同一个结论。
     * - **不消耗派发预算**：取消不是一次派遣，内核账本（逻辑操作派遣上限）不变。
     * - **能立刻重发**：取消返回后，同一个会话可以马上发下一条（新回合）或重发这一条
     *   （同一逻辑操作的下一次尝试）。
     *
     * 默认实现是 no-op：不关心取消的调用方（采集链、批量导入、测试替身）不必实现它。
     */
    suspend fun cancel(requestId: String) = Unit

    /**
     * 生成中的逐 token 实时文本（思考链 / 回答正文 / 工具调用进度），键是一次派发的请求标识。
     *
     * 它**不落库**：进程重启后自然消失，重启后的"进行中 / 可以继续回复"由持久化的任务快照
     * 负责。之所以单独开一条通道，是因为走持久化快照的每一次进度都要写一行状态与审计、且单
     * 任务事件数有上限——那正是实时文本此前只能稀疏回放、正文还被截断的原因。
     *
     * 默认实现返回空流：不关心实时文本的调用方与测试替身不必实现它。
     */
    fun observeLiveText(requestId: String): Flow<ModelLiveText?> = flowOf(null)

    /**
     * 这一轮**查阅了什么**的痕迹（B1），键是一次派发的请求标识；值是已编码并截断好的
     * `TutorTurnToolTrace` JSON（见 `core:model`）；null / 不发射 = 这一轮没有发起过工具调用。
     *
     * 与 [observeLiveText] 的分工：那条通道是"此刻屏幕上显示什么"（逐 token、用完即弃），
     * 这条是"这一轮到底查过什么、有没有被拒"（一轮一组）。消息行的写入方在终态到达时**读一次
     * 它的当前值**，所以实现**不得**在任务落终态时清空痕迹——清空会让刚写完的正文丢掉痕迹
     * （实时文本可以清，痕迹不行）。
     *
     * 默认实现返回空流：不接工具痕迹的调用方与测试替身不必实现它。
     */
    fun observeToolTrace(requestId: String): Flow<String?> = flowOf(null)
}
