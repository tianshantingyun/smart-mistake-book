package com.tingyun.smartmistakebook.core.model

/**
 * 6B-3 自然语言改卷的**装配接缝**（批次 41 / WS7 项 46）。
 *
 * 为什么需要它：`feature:tutor` 现有的 `TutorPaperEditAssistant` 端口返回的是**原始字符串**
 * （`feature/tutor/.../TutorPaperEditAssistant.kt:25`），实现方可以绕开三件已经成立的事——
 * 有界卷面快照 [PaperEditInput]（图片地址/本地路径结构上不可表达、schema 19 的构造期守卫、
 * 12 000 字快照预算）、网关里**同一把**白名单解析器、以及持久账本的逐逻辑操作派发预算。
 * 一旦 feature 侧直连网关或自己拼请求，这三件都会在装配处被悄悄旁路，而 `PaperEditTasksTest`
 * 与 `PaperEditAdapterTest` 都不会变红。
 *
 * 接口只收/发 core:model 的类型，所以 feature 可以依赖它（架构契约禁止 feature 依赖具体
 * `core:data` 类）；生产实现 `RepositoryPaperEditModelPort` 在 `core:data`，由 app 装配。
 *
 * 实现方必须满足的判据（`PaperEditModelPortTest` 逐条钉住）：
 * 1. 只把**已过白名单解析器**的 [PaperEditOutput] 交回；任务终态失败/取消/未落终态一律抛
 *    [PaperEditModelException]，不得返回空命令冒充"没有可执行修改"。
 * 2. 交给账本的请求必须是 `kind = PAPER_EDIT`、`schemaVersion = 19` 的 [PaperEditInput]；
 *    不得自己构造出网清单（逐次清单路径不适用于改卷）。
 * 3. 学生同意位必须由调用方传**应用层开关实值**（`ModelTaskRequest.agentConsentGranted` 的同一
 *    纪律），实现方不得写死 `true`。
 * 4. 卷面副本 → [PaperEditInput] 的映射属于装配点（`core:export`/`feature` 热区），映射表见
 *    `docs/research/2026-11-02-model-task-layering.md` §10.6；本接口不复制该映射。
 */
interface PaperEditModelPort {
    /**
     * 为 [input] 提交一次改卷模型任务并等待终态。
     *
     * @param agentConsentGranted 应用层"配置了模型 = 同意"开关的实值；false 时实现方仍按同一
     *   请求形状提交，由出网策略拒绝（不得在实现里换成另一套授权形状）。
     * @throws PaperEditModelException 任务失败、被取消或没有落终态。
     */
    suspend fun commandsFor(
        input: PaperEditInput,
        agentConsentGranted: Boolean,
    ): PaperEditOutput
}

/**
 * 改卷任务没有交回可执行命令。[status] 为 null 表示持久任务流结束了却没有落任何终态
 * （不是"没有可执行修改"——那种情况由 `{"commands":[]}` 的 [PaperEditOutput] 表达）。
 */
class PaperEditModelException(
    val status: ModelTaskStatus?,
    val failureCode: ModelFailureCode?,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
