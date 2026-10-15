package com.tingyun.smartmistakebook.core.domain

/**
 * 切分台账的**提交回执口**（T4 / AUD-03）：一道切分题「入库了没有」只由提交回执说了算。
 *
 * 为什么单独一个**窄接口**、而不是把这两笔并进 [SplitImportRepository]：录入界面
 * （`feature:capture`）只需要问「这道题入库之后还有没有下一题」，不该顺带拿到 `complete` /
 * `abandon` 这类任务级动作，也不该看见 `split_import_question`、Room 这些数据库类型——
 * 按依赖方向它只看得见本模块（以及 `core:model`）的契约类型，先例见 `core:model` 的
 * `ImmutablePayloadConflict` 跨层标记。这条窄口就是这次跨层调用的落点。
 *
 * 它消灭的失败（第 12 条）：AUD-03——复核页在打开任何草稿**之前**就把勾选题全部标成 SAVED，
 * 学生只录了第一题，其余题却已经显示「已录入」；而真的入库之后，SAVED 又没有生产回写方。
 */
interface SplitImportCommitReceipts {

    /**
     * 记下这份草稿真的入库了：按 `split_draft_id` 反查台账里的那道题并置 SAVED（幂等）。
     *
     * 只在提交拿到回执之后调用。提交失败、放弃、只是创建了草稿，都不算回执，不得调用。
     * 返回 true = 台账里确实有这道题（这一笔改到了它，或它本来就是 SAVED）；false =
     * 这份草稿不是切分来的（或已被清理）——回执落在台账之外不是错误，也不是静默丢数据。
     */
    suspend fun recordCommittedDraft(
        splitDraftId: String,
        occurredAtEpochMillis: Long,
    ): Boolean

    /**
     * 这份草稿所属分题任务里**最早的一道未提交题**的草稿 id；不属于任何任务、
     * 或已经没有未提交的题时为 null。
     *
     * 为什么是「最早未提交」而不是「序号 + 1」：学生可能只勾其中几题、也可能中间某一题
     * 先失败再重来；两种情况下的「下一题」都必须是台账里序号最小的那道未提交题。
     * 返回 / 杀进程之后续做走的是同一条规则。
     */
    suspend fun readNextUnsubmittedDraft(splitDraftId: String): String?
}
