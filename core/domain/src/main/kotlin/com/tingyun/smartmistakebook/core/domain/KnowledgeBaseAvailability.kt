package com.tingyun.smartmistakebook.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 知识能力的就绪位（决策台账 D-Q3：首装后台化；规格 §6）。
 *
 * **与 [KnowledgeReadiness] 是两件事**：那个是"某个知识点本身学没学到位"（掌握度判定，
 * 排程降权与会话补救共用）；这个是"本机知识内容准备好了没有"（进程级可用性）。
 * 命名刻意区分，避免把"库没装好"读成"学生没掌握"。
 *
 * 它消灭的失败（改前六个消费点各自为政）：
 * - 错题归类在自己的调用里顺手触发一次全量解析，首装 16.7 秒卡在学生操作的关键路径上；
 * - 教学参考、知识点复习取题、掌握总览、归类检索在"还没准备好"时**静默**降级成
 *   零命中 / 无材料 / 记录为零——学生看到的是假的"没有"，不是真的"还没好"；
 * - 归类准备失败还会被上层统一 catch 成"provider 未配置"，把人指向模型设置页。
 *
 * [Ready] 之前依赖知识库的能力必须**如实说"准备中"**，不得回退到上述任何一种假答案，
 * 也不得自行触发安装（"顺手装一次"掩盖未就绪是本条要消灭的病）。
 */
sealed interface KnowledgeBaseAvailability {
    /** 内容尚在后台就位（首装/内容变更后的全量解析，或安装失败后尚未重试）。 */
    data object Preparing : KnowledgeBaseAvailability

    /** 内容已落库，依赖知识库的能力可以全速工作。 */
    data object Ready : KnowledgeBaseAvailability

    /**
     * 上一次安装失败。[diagnosticId] 与启动横幅同一编号，便于把学生看到的提示
     * 与日志里的失败对上。
     */
    data class Unavailable(val diagnosticId: String) : KnowledgeBaseAvailability
}

/** 只有 [KnowledgeBaseAvailability.Ready] 才允许产出依赖知识库的结果。 */
val KnowledgeBaseAvailability.isReady: Boolean
    get() = this == KnowledgeBaseAvailability.Ready

/**
 * 就绪位的写入方（唯一生产写入方是 app 层的启动编排）：安装开始置 [Preparing]，
 * 成功置 [Ready]，失败置 [Unavailable]；重试再次经过同一序列。
 *
 * 消费方只拿到 [state]（只读 `StateFlow`），所以"就绪前怎么办"是消费方的策略，
 * 而不是它们各自的实现——这正是六个消费点此前口径不一的原因。
 */
class KnowledgeBaseAvailabilityTracker(
    initial: KnowledgeBaseAvailability = KnowledgeBaseAvailability.Preparing,
) {
    private val mutable = MutableStateFlow(initial)

    val state: StateFlow<KnowledgeBaseAvailability> = mutable.asStateFlow()

    fun markPreparing() {
        mutable.value = KnowledgeBaseAvailability.Preparing
    }

    fun markReady() {
        mutable.value = KnowledgeBaseAvailability.Ready
    }

    fun markUnavailable(diagnosticId: String) {
        mutable.value = KnowledgeBaseAvailability.Unavailable(diagnosticId)
    }
}

/**
 * 依赖知识库的操作在内容就位前被调用（D-Q3）。
 *
 * 它是**诚实等待**的兜底：正常路径上消费方先看就绪位、显示"准备中"并在就绪后自动放行；
 * 万一就绪位与调用之间发生竞态（或新消费点忘了先看），这里把"没准备好"变成一个可被
 * 调用方识别的类型，而不是一个"零命中"的假结果、也不是"provider 未配置"的误报。
 *
 * [availability] 让调用方区分"等一会就好"（[KnowledgeBaseAvailability.Preparing]）
 * 与"这次真的没好"（[KnowledgeBaseAvailability.Unavailable]，出路是横幅上的重试）。
 */
class KnowledgeBaseNotReadyException(
    val availability: KnowledgeBaseAvailability,
) : IllegalStateException("Knowledge base is not ready: $availability")
