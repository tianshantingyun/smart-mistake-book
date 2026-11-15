# 智能体接线补全批（2026-11-16）

## 范围

按上一轮审计列出的接线缺口逐项收口（第三方生图模型腿除外）：

1. `RUN_JAVASCRIPT` / `READ_FIGURE` 广告门：分别按 `visualEngine != null`、像素检查器 + 提供方
   `supportsImageInput` 裁剪；没有执行条件的工具不再出现在模型能力面。
2. 本地动作可用性：`TutorAutonomousActions.availableActions()` 按真实仓储/设置/备份引用过滤，
   不再把未装配的动作广告给模型。
3. 首轮 `APP_CONTEXT.subjects`：应用层注入学习目录里的真实科目（`TutorAgentContextRepository`
   → `TutorAgentContext.subjects`），不再要求先做 `NOTEBOOK_READ`。
4. JS `agent.callAction` 附图上下文：大厅来源图 + 上下文图 + 讲题学生图都进入
   `TutorToolContext.attachedImageAssetIds`，保存动作不再丢图。
5. 同轮 `SELECT_QUESTION` 刷新：`RUN_JAVASCRIPT` 内部选中题目后，后续嵌套调用读写同一个
   mutable context，`APP_CONTEXT` / `KNOWLEDGE_READ` 立即看到新题。
6. 通用 JS state 持久化：`TutorJavascriptStateStore`（domain 端口 + app SharedPreferences 实现，
   16 KiB/JSON 校验、SHA-256 键），跨任务与进程重启读回。
7. `ADD_TO_REVIEW_PLAN` 真实写入：`StudyExperienceRepository.addToReviewPlan` +
   `RoomBackedStudyExperienceRepository` 落今日计划的新 revision（不可变计划行 + 槽位切换），
   幂等、预算、已开始/已完成拒绝；快照构建保留 `planRevision > 1` 的显式修订。
8. `READ_FIGURE` 本地视觉验收：像素摘要上执行空白/内容过少/对比度低/贴边检查，
   输出 `acceptance.status/reasons/advice`；工具文案要求非 PASS 时如实说明、不得声称图片清晰。
9. 文档/集合漂移：`Capabilities.kt` 与动作集合、提示词、确认卡文案、领域/特性测试同步到
   “全部本地动作已接通”。

## 验证

- `:core:model:test :core:domain:test :core:data:testDebugUnitTest :feature:tutor:testDebugUnitTest`
- 新增 `TutorFigureAcceptanceTest`、`addToReviewPlan` 两例（追加 revision/快照保留、已开始计划拒绝）
- 变更测试：能力快照、动作广告对拍、确认卡路由、本地动作准入

## 未做（明确边界）

- **4E.2 原生交互/表单**（`reduce(state,event)`、`controls[]`、`NativeTutorFormV1`、事件驱动
  重渲染）没有伪装实现。它是独立的 4E.2 阶段：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`
  的 69 项准入门、JS 广告门（KD-76）、UI/协议/迁移面都未开启；当前 SceneV1 编译器按契约
  拒绝非空 `controls`/`form`。在没有 UI 宿主与准入门的现在强行放开，只会广告一个用户无法
  操作的能力。`ui_state_json` 列已存在（schema 70 起），是 4E.2 的存储前置。
- 第三方图像模型生成腿（用户明确排除）。
