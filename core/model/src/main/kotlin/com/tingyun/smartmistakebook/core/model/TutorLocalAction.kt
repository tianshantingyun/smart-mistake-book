package com.tingyun.smartmistakebook.core.model

/**
 * 本地动作白名单第一版（规格 `docs/superpowers/specs/2026-09-25-agent-first-refactor-design.md`
 * §3.2；D-K2e 白名单表已收下）。
 *
 * 模型**只能选不能造**：动作的 ID 与参数形状都由本地定义，模型只从这份名单里挑一个。
 * 名单之外的动作在协议里根本没有可表达的形状——不是"服务端会拒绝"。
 *
 * 这份名单消灭的具体失败：改前"它能给你东西"的出口是 `TutorLocalIntentPanel`，而它的可见性
 * 取决于模型本轮的 `intentDecision`——基础出口被模型输出绑架（事实基线研究报告 §4.1#2）。
 * 动作请求改为模型的**输出**、执行永远等学生点卡（确认卡）之后，名单就是"卡上可能出现什么"
 * 的唯一定点。
 *
 * 权限档在 `core:domain` 的 `TutorPermissionPolicy`：这四个动作全部 **ask（确认卡）**。
 * 重拆提议（`PROPOSE_RECLASSIFY`）**不在本版**：D-K2/K2b 与 D-K3 把它整体推迟到阶段 3B，
 * 它的档位由重拆 hook 判据决定（被拦 → ask），届时按同一机制接入。
 */
enum class TutorLocalAction {
    /** 打开某题（跳错题本详情）。 */
    OPEN_PROBLEM,

    /**
     * 当前题加入错题本 = `NOTEBOOK_WRITE` 的 ask 档（D-K2e 白名单表）：
     * 同一件事的两种协议拼写——工具拼写（模型在工具面里选）与动作拼写（模型在本地动作通道里选）。
     */
    SAVE_TO_NOTEBOOK,

    /** 发起选择导出（检索 → 勾选 → 本地排版 → 导出可打印文件）。插眼 5 点名的高风险流程。 */
    START_EXPORT,

    /** 提出复习计划调整（把某题纳入计划）：模型提出行为，请求学生同意。 */
    ADD_TO_REVIEW_PLAN,
}
