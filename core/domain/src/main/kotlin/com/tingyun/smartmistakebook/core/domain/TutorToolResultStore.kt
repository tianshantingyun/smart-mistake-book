package com.tingyun.smartmistakebook.core.domain

/**
 * 有界分页读取的工具结果工件存储（LangChain `ToolMessage.artifact` / MCP `resource_link`
 * 同构思路：完整结果留在本地，模型只拿到预览与可回读的 id）。
 *
 * 它消灭的具体失败：`TutorToolOutcome.dataJson` 只有 6,000 字符上限，`SELECT_QUESTION`/
 * `APP_CONTEXT`/`NOTEBOOK_READ` 的完整题面或检索结果稍大就只能整条失败；直接截断又会给出
 * 半截 JSON。这里把“完整结果”与“模型可见预览”拆开：预览里有 `resultId`，模型用
 * `READ_RESULT` 分页回读，信息不丢，也不再构造非法 JSON。
 *
 * 实现必须：
 * - 对同一 (scope, tool, payload) 幂等（确定性 id）；
 * - 每个结果有上限（生产 256 KiB）、全局条数/时间淘汰；
 * - `read` 只返回 `[offset, offset+limit)` 的有界文本块，不把整份结果读成模型输入；
 * - 缺失/过期返回 null，不抛异常穿透工具环。
 */
interface TutorToolResultStore {
    suspend fun write(scopeKey: String, tool: String, payload: String): String

    suspend fun read(resultId: String, offset: Int, limit: Int): TutorToolResultChunk?

    suspend fun prune(nowEpochMillis: Long)
}

/** 一次分页回读的结果。 */
data class TutorToolResultChunk(
    val resultId: String,
    val text: String,
    val offset: Int,
    val totalChars: Int,
) {
    init {
        require(resultId.isNotBlank()) { "A tool result chunk needs an id" }
        require(offset >= 0) { "A tool result offset must not be negative" }
        require(totalChars >= 0) { "A tool result length must not be negative" }
    }

    val nextOffset: Int get() = offset + text.length
    val complete: Boolean get() = nextOffset >= totalChars
}
