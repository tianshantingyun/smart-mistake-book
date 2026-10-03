package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelLiveText
import com.tingyun.smartmistakebook.core.model.TutorToolTraceEntry
import com.tingyun.smartmistakebook.core.model.TutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.decodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.model.encodeTutorTurnToolTrace
import com.tingyun.smartmistakebook.core.data.study.TutorToolExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * 派发过程中**只活在内存里**的两条界面通道，键都是一次派发的请求标识。
 *
 * 两条通道的生命周期语义**正好相反**，这是把它们放在同一个文件里的理由——差别一眼可见：
 *
 * | 通道 | 是什么 | 终态时 |
 * |---|---|---|
 * | 实时文本 [ModelTaskLiveTextStore] | 此刻屏幕上该显示什么（逐 token） | **清掉**（正文已落库） |
 * | 工具痕迹 [ModelTaskToolTraceStore] | 这一轮到底查过什么（B1） | **不许清**（正文的写入方正是在那一刻读它） |
 *
 * 两条都不落库、都不写审计行：它们是"显示"与"呈现输入"，事实仍在 `tutor_message` 与账本上。
 */
internal class ModelTaskLiveTextStore {
    private val texts = MutableStateFlow<Map<String, ModelLiveText>>(emptyMap())

    fun observe(requestId: String): Flow<ModelLiveText?> =
        texts.map { current -> current[requestId] }.distinctUntilChanged()

    fun publish(requestId: String, text: ModelLiveText) {
        texts.update { current -> current + (requestId to text) }
    }

    fun clear(requestId: String) {
        texts.update { current -> current - requestId }
    }
}

/**
 * 工具痕迹（B1）：一轮里"查阅了什么、拿到几条、有没有被拒"。
 *
 * 值存的是**已经编码并截断好的** `TutorTurnToolTrace` JSON——与落进消息行列里的那一份
 * 逐字相同，所以界面渲染（实时的活单元、重开会话后读回来的那一行）走的是同一个解码与
 * 同一个渲染函数，两处不可能漂成两句话。
 */
internal class ModelTaskToolTraceStore {
    private val traces = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 这一轮的痕迹（null/不发射 = 这一轮还没有发起过工具调用）。 */
    fun observe(requestId: String): Flow<String?> =
        traces.map { current -> current[requestId] }.distinctUntilChanged()

    /** 新一轮派发从空痕迹开始：同一次派发的重试不叠上一轮的结果。 */
    fun clear(requestId: String) {
        traces.update { current -> current - requestId }
    }

    /** 这一轮**此刻**的痕迹（折叠态那行小字用它）；没有痕迹时 null。 */
    fun current(requestId: String): TutorTurnToolTrace? =
        decodeTutorTurnToolTrace(traces.value[requestId])

    /**
     * 把一轮的工具执行并进痕迹。
     *
     * 条数（[TutorToolExecution.resultCount]）来自执行器本地，**不进模型可见字段**：它只用来
     * 让学生那一行小字说出"查到了几条"（模型可见的 outcome 与它的指纹一个字节都没变）。
     *
     * 条目有自然上界（5 轮 × 3 次调用），但键会随会话增长，所以只留最近
     * [MAX_RETAINED_TOOL_TRACES] 条——超过它的都是早就把行写完的老请求。
     */
    fun append(requestId: String, executions: List<TutorToolExecution>) {
        val additions = executions.map { execution ->
            TutorToolTraceEntry(
                tool = execution.outcome.tool,
                resultCount = execution.resultCount,
                ok = execution.outcome.ok,
                // 被拒/失败才有 kind；成功的条目不带（空载体不进存储形状）。
                errorKind = execution.outcome.errorKind,
                // A2：生图条目带事实半（id/kind/模型/是否本次出网）——消息行落库时按它建立
                // 资产引用，界面按它重建配图，工具卡按它说"已计入额度/已保留原图"。
                figure = execution.figure,
            )
        }
        traces.update { current ->
            val existing = decodeTutorTurnToolTrace(current[requestId])
            val merged = TutorTurnToolTrace(
                entries = existing?.entries.orEmpty() + additions,
                omittedCount = existing?.omittedCount ?: 0,
            )
            val encoded = encodeTutorTurnToolTrace(merged)
            val updated = if (encoded == null) current - requestId else current + (requestId to encoded)
            if (updated.size <= MAX_RETAINED_TOOL_TRACES) {
                updated
            } else {
                updated.entries.drop(updated.size - MAX_RETAINED_TOOL_TRACES)
                    .associate { (key, value) -> key to value }
            }
        }
    }

    private companion object {
        /** 内存里保留多少条已完成的痕迹：超过它的都是已经把行写完的老请求。 */
        const val MAX_RETAINED_TOOL_TRACES = 64
    }
}
