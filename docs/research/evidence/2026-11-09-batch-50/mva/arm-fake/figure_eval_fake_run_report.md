# PRD-09 生图评测 · fake 驱动机制自检报告

- 运行类型：**FAKE_DRIVER_MACHINERY_CHECK**（run_seed=`t5-batch20`）
- 夹具冻结：2026-10-25T00:00:00+08:00（sha256 校验通过）
- **真实 Provider 质量：UNVERIFIED**（无凭据，fake 驱动不出网、零真实派发；
  本报告只证明评测机制可运行，不构成任何真实质量结论——PRD-09 退出门 :137）
- 机制自检：**PASS**（质量门数值在此模式下仅供参考，不作为启用判定）

## 1. 冻结集与分母

- 盲测 120（6 组 × 20）：120
- 开发集另设 24，**不计入 120 分母**（PRD-09 :132）
- 重复同一题只作稳定性观察，不算独立新样本（design §6 :91）
- 评审来源：全部任务评审来源：模型标注，无人工专家（human_expert=false）；分歧未决不计合格（PRD-09 :132）

## 2. 评测预算（六派发总门）

- 每逻辑操作远程派发上限：**6**（docs/current/visual-generation-contract.md:138；core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:61）
- 本次 fake 运行派发合计：204 / 上限 864（fake，零真实外发）
- 费用：unknown（未知费用记未知、不写 0；真实 Provider 无凭据，单价未知：未知费用记未知、不写 0（设计§6 :95）。待 key 配置后按真实返回与配置计。）

## 3. 两后端配对/切换

- 主后端：**IMAGE_MODEL**；后端枚举：IMAGE_MODEL / JS
- 配对子集任务数：24；切换事件：11
- IMAGE_MODEL：FAKE_DRIVER（driver=fake）
- JS：NOT_AVAILABLE（driver=none；reason=未注入 VisualExecutionEngine 适配器：T3 真引擎只在设备轨可执行，本进程无从确认可用）
- 配对子集检验两后端与双向切换，不强制所有题跑两遍（design §6 :93）；JS 状态取自端口探测（NOT_AVAILABLE 时该题不产生 JS 成功记录）
- JS 本地执行不占远程派发（契约 :138）；RENDERED 仅为产物引用，事实核验 UNVERIFIED，不冒充成功
- 分类语义：明确要图却 NO_FIGURE / 降级为文字只算安全退路，不算生图成功（design §6 :93）

## 4. 指标（fake 值，非真实质量；真实值待 key）

| 指标 | IMAGE_MODEL (fake p50) | IMAGE_MODEL (fake p95) | JS (p50/p95) |
|---|---|---|---|
| 首图延迟 ms | 83947 | 143545 | 无样本（NOT_AVAILABLE） |
| 总时长 ms | 47708 | 144275 | 无样本（NOT_AVAILABLE） |
| token 输入/输出 | null（待真实 usage） | null | 不适用（本地执行） |
| 费用 | unknown | — | 不适用（本地执行，金额记 null 不写 0） |

- JS 样本来源：无（引擎未注入/不可用 → NOT_AVAILABLE）

采集点（字段与采集点先落）：
- `first_image_latency_ms`：figure_generation_attempt.started_at_epoch_millis → ended_at_epoch_millis（core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/FigureGenerationEntities.kt 首个 GENERATED attempt）；harness 侧同字段。已知缺口：该表暂无 token/费用/首图延迟专用列（批次 20 零 schema 决定，真实 key 到位后随启用门补列）。
- `total_duration_ms`：任务开始→终态（operation 两时间戳 figure_generation_operation.started/updated）+ harness 侧。
- `tokens`：真实 Provider usage 返回（prompt/completion tokens）；fake 驱动无 usage → null。
- `cost`：按真实返回与配置单价计；未知费用标未知、不写 0（design §6 :95）。
- `dispatches_used`：持久派发账本（RoomFigureDispatchLedger durable 预留/计数）+ harness 侧同口径。

## 5. 质量门（常量按 PRD 原文引用；本节数值为 fake 机制自检输入）

| 门 | 阈值 | fake 值 | 分母 | Wilson95 | 判定（仅供参考） |
|---|---|---|---|---|---|
| 首次合格率 | ≥90% | 48.33% | 120 | [0.396, 0.572] | NOT_MET |
| 预算内最终合格率 | ≥95% | 62.50% | 120 | [0.536, 0.706] | NOT_MET |
| 关键题意错误被接受 | =0 | 0 | — | — | MET |
| 首图 p95（IMAGE_MODEL） | ≤120000 ms | 143545 | — | — | False |
| 首图 p95（JS） | ≤30000 ms | JS 事实核验未接 | — | — | 不可判（如实记 null） |

分组最终合格率（阈值每组 ≥90%，fake 值）：

| 组 | 合格 | 分母 | 值 | 判定（仅供参考） |
|---|---|---|---|---|
| flow_formula | 11 | 20 | 55.00% | NOT_MET |
| handwriting_removal | 11 | 20 | 55.00% | NOT_MET |
| mechanics_phases | 14 | 20 | 70.00% | NOT_MET |
| natural_illustration | 12 | 20 | 60.00% | NOT_MET |
| proportion_coordinates | 14 | 20 | 70.00% | NOT_MET |
| source_rebuild | 13 | 20 | 65.00% | NOT_MET |

## 6. 机制自检

- PASS: ✓
- PASS_WITH_REPAIR: ✓
- DEGRADED_TO_TEXT: ✓
- NO_FIGURE: ✓
- FAILED_any: ✓
- critical_mismatch_observed: ✓
- critical_mismatch_accepted_zero: ✓
- switch_event_recorded: ✓
- js_backend_recorded: ✓
- js_backend_honest_status: ✓
- js_never_claims_quality: ✓
- backends_pairing_complete: ✓
- budget_cap_respected: ✓
- **verdict: PASS**

## 7. 引用

- design_section6: docs/superpowers/specs/2026-10-19-product-readiness-design.md §6（:91/:93/:95）
- prd09: docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:126-140
- contract_six_dispatch: docs/current/visual-generation-contract.md:138
- prompt_policy_budget: docs/current/figure-generation-prompt-policy.md §5
- dispatch_constant: core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:61
