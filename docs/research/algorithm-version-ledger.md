# 算法版本清单（Wave 0 / W0-3 · Q1）

> 建立于 2026-09-30（内核修复路线图 Wave 0 的 W0-3）。
> 上游：`docs/research/2026-09-28-kernel-remediation-roadmap.md` §0 执行守则、
> `docs/agent-first-refactor-decisions-2026-09-23.md`「阶段 3A 号段登记」。
> 姊妹文档：`docs/research/kernel-projection-rollback.md`（投影回退流程）。

## 1. 这份清单回答什么

审计 Q1 的原话是"投影版本仅 3 次 bump 历史，无发布清单"——**改了数值之后没人能回答"改了什么、
凭什么改、拿什么证明、能不能回退"**。所以每次 bump 必须在这里留一行，五项缺一不可：

| 项 | 含义 | 不写的后果 |
|---|---|---|
| **版本串** | 被 bump 的常量与它的新值（`LearningCoreVersions.*`、`STUDY_DATABASE_VERSION`） | 版本对不上时无法定位是哪一次改动 |
| **变更公式** | 被改的公式/常数，改前 → 改后（逐字） | "数值变了"与"公式变了"分不清，复核无从下手 |
| **数据来源** | 依据（审计编号 / 裁定编号 / 官方文档 / 实测数据），带 file:line 或链接 | 变成"某次会话觉得该这么改" |
| **测试证据** | 证明这次改动按预期生效的测试名与结果 | 改完没人知道它到底生效了没有 |
| **archive 是否已落** | bump 前旧投影是否已进 `projection_archive`（W0-1 ③） | 出问题时旧投影已被覆盖，回退无门 |

**纪律**（与 roadmap §0 同源）：
1. 动投影公式 → 必 bump `LearningCoreVersions.PROJECTOR`（复合串变 → 版本不匹配 → 自动全量重放）；
   **bump 前**旧投影必须已归档（`projection_archive`，见回退文档）。
2. 动 Room schema → bump `STUDY_DATABASE_VERSION` + 非破坏迁移 + 导出新 JSON。
3. **「待裁」值不得自行取值**：先验/θ/ε/γ/fuzz 之类需要裁定的数值，先过用户（裁决落
   `docs/agent-first-refactor-decisions-2026-09-23.md`），再在这里登记。

## 2. 号段登记（阶段 3A，已占号）

| 资源 | Wave 0 前 | 3A 占用 | 用途 | 状态 |
|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` | 53 | **54** | Wave 0：`projection_archive` 新表 + `review_plan` 指纹列合并 | ✅ 已用（本波） |
| `STUDY_DATABASE_VERSION` | 53 | 55 | Wave 2：`review_log.state` | 未用 |
| `STUDY_DATABASE_VERSION` | 53 | 56 | Wave 3：`learner_knowledge_mastery_state` 的 `success_weight`/`failure_weight` | 未用 |
| `LearningCoreVersions.PROJECTOR` | `projector-v7` | `projector-v8` | Wave 3 投影公式变更 | 未用 |
| `LearningCoreVersions.EVIDENCE` | `evidence-v4` | `evidence-v5` | Wave 1 证据定价语义（`persistedAssistance` 链 + 看答案口径） | 未用 |

**Wave 0 不动版本串**：本波只做回退能力、读时校验、常数收敛与清单，**没有任何投影输出变化**，
所以 `PROJECTOR` / `EVIDENCE` / `REVIEW_PLANNER` 一律不动（改公式才 bump）。

## 3. 记录

### 3.1 追溯登记（Wave 0 之前的 bump，凭源码注释与审计回填）

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `projector-v5` → `v6` | 2026-09-11 | `projectChatEvidence` 开始写 `lastEvidenceAt` / `lastEvidenceDirection`（同一条账本事件的投影结果变了，增量不重算已消费事件 → 必须靠版本不匹配触发全量重放） | 审计 `AUDIT-ALGORITHM-2026-09-09` §3.5；`LearningCoreVersions.kt:8-14` | `core:domain` 投影等价用例（`LearningProjectorTest` 等） | ❌ 当时无归档表（archive 到 2026-09-30 才有） |
| `projector-v6` → `v7` | 2026-09-11 | 跨日判定 `delta_t` 改由 `lastReviewedAtEpochMillis` + 事件 UTC 偏移现算，不再读 `ProblemMemoryState.lastReviewedEpochDay`（该字段会被曝光通道写成 UTC 日序默认值） | 审计 §3.7；`LearningCoreVersions.kt:15-21` | 同上 + `LearningProjectorTest` 的日序边界用例 | ❌ 同上 |

### 3.2 Wave 0（本波，2026-09-30）

| 版本串 | 变更 | 公式/口径 | 数据来源 | 测试证据 | archive |
|---|---|---|---|---|---|
| `STUDY_DATABASE_VERSION` 53 → **54** | 加表 `projection_archive`（W0-1 ③）；`review_plan` 去掉 `input_fingerprint`（W0-2，两列恒同值） | **无算法公式变更**：投影输出、计划指纹、计划 id 逐位不变 | roadmap W0-1/W0-2；审计 Q2/Q4/Q5 | `KernelWave0SchemaContractTest`（5 例，绿）；`FullMigrationMatrixInstrumentedTest`（1→54 矩阵 + 合并保行 + 归档落库，绿） | ➖ 本波建立归档能力；`PROJECTOR` 未 bump，因此没有跨版本重放要归档 |
| `PROJECTOR` / `EVIDENCE` / `REVIEW_PLANNER` / `SKIP_POLICY` 等 | **未 bump** | 常数收敛（W0-4）只删同值副本，值一个没改 | roadmap W0-4；审计 Q5 | `core:domain` 496 / `core:database` 93 / `core:data` 563（既有红 5 条 KB 金标）——`ReviewPlannerTest` / `ReviewPlannerV2Test` 全绿（指纹与选序逐位不变） | ➖ |

### 3.3 预留：Wave 2 · L2 罚项 γ（**尚未取值**）

| 项 | 内容 |
|---|---|
| 版本串 | 待实施（`fsrs-optimizer` 侧；若其取值改变排程输出，另需按 §0 规则判断是否 bump `PROJECTOR`/`REVIEW_PLANNER`） |
| 变更公式 | `fsrs-optimizer` 的 **L2 罚项**（权重正则项）：`loss = Σ(预测误差项) + γ · Σ(w_i²)` 一类的罚项系数 γ |
| **取值口径（裁定，不得自行取值）** | **取官方默认值**；实施时**记录所用 `fsrs-optimizer` 的版本号 + 默认值出处（文件与行号）**，写入本表"数据来源"栏。依据：台账裁决 14「L2 γ = 对齐 fsrs-optimizer 官方默认（实施时溯源记台账）」。本波（Wave 0）**只建文件与表格，不取数值** |
| 数据来源 | 台账「3A 前裁决门 · 收口」裁决 14；roadmap 附录 B（fsrs-optimizer 对齐三件） |
| 测试证据 | 待实施（合成数据恢复测试：参数能训回真值——Wave 2 退出门） |
| archive | 待实施（若该波 bump 投影版本，bump 前必须已有 `projection_archive` 行；届时按 `docs/research/kernel-projection-rollback.md` 执行） |

## 4. 谁在什么时候写这一行

- **每次 bump 的同一个提交里**（不是事后补）：改常量/公式的那次改动，连同本表的行一起提交；
  没登记 = 改动不完整。
- 复核入口：`git log --follow docs/research/algorithm-version-ledger.md` 能读出"这个版本串是怎么来的"。
- 与 `projection_archive` 的关系：本表说"为什么改"，archive 存"改之前的旧值"。两者合起来才构成
  "可逆"：只有清单没有归档 = 回不去；只有归档没有清单 = 不知道为什么要回去。
