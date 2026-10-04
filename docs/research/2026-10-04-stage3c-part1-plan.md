# 阶段 3C 前半 · 坐标系可信度监测与自锁打破（KF-29 + KF-30 + KF-07）实施计划（2026-10-04）

> 依据：`docs/research/2026-09-28-algorithm-fix-plan.md` §5 P3 组（KF-29 `:372-385`、KF-30 `:387-392`、KF-07 `:148-156`）、
> `docs/research/2026-09-28-kernel-remediation-roadmap.md` Wave 5、主线 3C 行（`REFACTOR-MASTER-LINE.md:49`）、台账裁决 27/29。
> **为什么是"前半"**：3C 全量 = KF-29 监测 → KF-30/KF-07 → KF-31 自动重审（**数据达标才开**）+ S9/S17/S18/S20 规模项。
> 本轮只做 **KF-29 + KF-30 + KF-07**；**KF-31 按原前置不启**（等 KF-29 有真实错误率数据）；S 项留 3C 后半。
> 触发背景：阶段 5 因 3D 硬门未过挂起（`docs/research/2026-10-04-stage5-gate-check.md`），用户裁定转本批。
> 并行关系：主线 §4 明文允许 3C 与 3D 并行；本批**不碰** `tools/kb_*`、不碰知识包格式（D-0 不在本批）。

## 1. 关键事实基线（本轮勘察）

- **KF-29**：`binding_audit_sample` 全仓零命中（全新表）；后台任务现状只有 `ExportPdfWorker`（WorkManager 单例模式）；debug 专用面现有模式 `app/src/debug/kotlin/.../FormatTestActivity.kt` + `TestSeedModelConfigReceiver.kt`。
- **KF-30**：`mergeAutomaticClassifications`（`core/data/.../mistake/RoomMistakeOrganizationRepository.kt:808`，调用点 `:412`）。
- **KF-07**：兜底全绑分支（fix-plan 给的行号 `:1021-1025` 已漂移，实施前重新定位 `nodeIdsToBind = topicNodes` 形态）；`ensurePseudoKnowledgeBinding` 已在（`StudyReviewPlannerService.kt:241`、`StudyPracticeUnitFacts.kt:203`）。
- **绑定写侧**：组织路径 `RoomMistakeOrganizationRepository`（用户确认 / 自动两路）；抽样 hook 点实施前定位。
- **版本**：schema 当前 **61** → KF-29 建表需 **62**（非破坏迁移 + 矩阵 1→62）；`LearningCoreVersions` 预期不动；若 KF-30 改合并规则影响计划指纹 → bump 并留重放（实施时核）。

## 2. 批设计与验收

### 批 1 · KF-29 绑定正确性监测管线（最重）
- 新表 `binding_audit_sample`：`sample_id` / `practice_unit_id` / `binding_snapshot_json` / `status(PENDING/REVIEWED)` / `verdict(CORRECT/WRONG/AMBIGUOUS)` / `reviewed_at`；schema 62 非破坏迁移。
- 抽样：**每科每周首 N 条（默认 5）新绑定**入队（后台任务；选型实施前定：新增 WorkManager worker 或接既有维护路径，须满足 D2 判据合规）。
- 复核屏：**debug 构建专用**（不进学生面）——显示题面 + 绑定点 + 原文依据，一键 verdict（PENDING→REVIEWED）；照 `FormatTestActivity` 模式。
- 聚合：错误率按**科 / 模型版本**聚合 → 落 `docs/` 报告（可复跑命令 + 口径写清）。
- 门：全量 JVM + DB 仪器化（新表 + 迁移矩阵 1→62）+ debug 面编译/仪器化 + 抽样入队/复核落判/聚合口径用例。

### 批 2 · KF-30 自锁打破 + KF-07 兜底落 pseudo
- **KF-30**：`mergeAutomaticClassifications`——自动接受的标签携带 `source=auto`；后续**更高置信** auto 可覆盖；`USER_CONFIRMED` 语义不可覆盖。
- **KF-07**：删兜底全绑分支 → 返回空绑定 + 由 `ensurePseudoKnowledgeBinding` 落 `pseudo:<SUBJECT>` 占位；自动路径同改。
- 门：全量 JVM + 定向仪器化（组织路径既有用例不得放宽）。

### 综合门 + 收尾
- 全量 JVM + DB 全套 + app 全套 + R8 冒烟。
- 收尾：完成记录独立成文 + 主线 3C 行更新（前半完成、KF-31 未启、S 项留后半）+ 台账折入（若台账仍为他线在飞则先由完成记录承载）。

## 3. 版本与迁移预算
- **schema 62**（KF-29 建表；非破坏；矩阵测试同步）。
- `LearningCoreVersions`：预期零；KF-30 若动合并规则影响计划/指纹 → 按 roadmap §0 bump 并留重放（实施时核，预期不触）。

## 4. 纪律与门
- 每批「实施 → 正式门（全量 JVM + DB 仪器化冷启严格口径 + 定向 app/feature 仪器化）→ 独立只读复核 Agent → 修复轮 → 显式清单提交」；共享工作树不碰他线；R8 只在综合门。
- 本批**不碰**：`tools/kb_*`、`tools/tests/test_kb_*`、`docs/agent-first-refactor-decisions-2026-09-23.md`（他线在飞）、`docs/superpowers/specs/*`、知识包与侧车格式。

## 5. 风险与 UNVERIFIED
- KF-29 抽样 hook 点与后台任务选型需实施前勘察（可能发现需新调度设施）。
- 错误率报告在数据积累前只有"管线可跑"证据——首份报告如实标注样本量，不得表述为"错误率已达标"。
- KF-07 行号漂移，实施前重新定位；删分支后不得破坏正常路径与既有用例。
- 与 KB 线绑定语义口径可能漂移（其 D-1 裁定在飞）——本批只做管线/机制，不改语义判定。
- 本批**不解决** 3D 硬门本身（D-0/D-1/D-2/D-3 仍是他线/协调项）。
