# PRD-09 生图质量评测 harness（批次 20 / T5 可先行半边）

状态：**机制就位，fake 驱动端到端可跑；真实 Provider 质量 UNVERIFIED**（无凭据，
禁止真实调用——PRD-09 退出门 `docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:137`）。

## 文件

| 文件 | 作用 |
|---|---|
| `tasks_blind_120.json` | 冻结盲测集：120 任务（6 组 × 20，组名 = 设计§6 :91 原文六组：比例/坐标、力学/阶段、流程/公式、源图重建、去手写、自然插画）。**盲测题不得用于调 prompt**；改 prompt 后的启用判定须换新的未见盲测集。 |
| `tasks_dev_24.json` | 开发集：24 任务（6 组 × 4），另设、**不计入 120 分母**（PRD-09 :132），可用于驱动/prompt 调试。 |
| `freeze_manifest.json` | 两份任务集的 SHA-256 冻结清单。评测门每次运行前校验，不一致即拒绝（防"看到结果后改夹具"）。变更任务集：重跑 `build_frozen_tasks.py` 重新冻结并留审计记录。 |
| `budget.json` | 显式评测预算清单：每操作 6 次远程派发上限（六派发总门，`docs/current/visual-generation-contract.md:138` + `core/model/.../ModelTasks.kt:61`）、终答预留、运行级上限；**费用未知记未知、不写 0**（设计§6 :95）。 |
| `build_frozen_tasks.py` | 冻结任务集生成器（固定种子，重跑逐字节相同）。事实预期由题族参数独立计算，从不来自模型/驱动输出。 |
| `figure_eval_gate.py` | 评测门：夹具完整性校验、fake IMAGE_MODEL 驱动、JS 后端接线（`JsPort` 可注入引擎；不可用如实 NOT_AVAILABLE）、两后端配对/切换记录、指标聚合（首图 p50/p95、总时长、token/费用字段与采集点）、按 PRD 原文引用的质量门常量、JSON/MD 报告输出。 |
| `test_figure_eval_gate.py` | 机制测试（`python -m pytest tools/figure_eval/test_figure_eval_gate.py -q`）。 |
| `reports/` | fake 驱动机制自检报告工件（JSON/MD）。 |

## 运行

```bash
python tools/figure_eval/figure_eval_gate.py                 # fake 机制自检，写 reports/
python tools/figure_eval/figure_eval_gate.py --backend JS    # JS 主后端（无注入引擎→NOT_AVAILABLE）
python -m pytest tools/figure_eval/test_figure_eval_gate.py -q
```

退出码：0 机制自检通过；1 机制失败；2 夹具完整性错误。
`--enforce-quality-gates` 仅供未来真实 Provider 运行；fake 运行禁止开启
（fake 门值是机制自检输入，非质量结论）。

## 关键纪律（测试锁定）

1. **盲测/开发分离先落盘**：分母只含 120 盲测；开发集 24 另设（测试
   `test_blind_and_dev_ids_disjoint_and_dev_excluded_from_denominator`）。
2. **评审来源必填**：每任务 `review_source`；当前如实标"模型标注，无人工专家；
   分歧未决不计合格"。
3. **防面向结果**：冻结 manifest sha 校验 + fake 驱动裁决只由
   `sha256(driver|seed|backend|task_id)` 决定、不读夹具预期值
   （`test_freeze_manifest_rejects_tampered_task_file`、
   `test_driver_outcome_independent_of_expectation_values`）。
4. **独立事实核验**：产出事实对照夹具预期核验，不读模型自评
   （`test_fact_checker_catches_critical_mismatch_and_never_accepts`）；关键
   题意错误（方向/比例/解析值等 `critical: true` 预期）一律不被接受。
5. **门常量不发明**：≥90% / 预算内 ≥95% / 每组 ≥90% / 关键错误=0 /
   首图 p95 JS≤30s、IMAGE_MODEL≤120s，全部带原文引用
   （`test_gate_constants_come_from_prd_original_text`）。
6. **成功语义**：明确要图却 NO_FIGURE / 降级为文字只算安全退路，不算生图成功
   （设计§6 :93）——harness 分类与合格计数同口径。

## JS 后端接线（批次 21 T4）

`figure_eval_gate.JsPort` 经 `JsEnginePort` 端口接 `core:domain.VisualExecutionEngine`
（T3 真实现 `QuickJsVisualExecutionEngine`）：

- **注入点**：`JsPort(engine=...)`。适配器只实现 `availability()` 与 `run(frame)`
  两个方法（结果形状取端口 `Rendered`/`Rejected` 两态）。真实引擎适配器在设备轨
  （QuickJS 原生库按 ABI 装载、隔离进程/硬终止行为归
  `QuickJsVisualExecutionEngineInstrumentedTest`），本 harness 不装载 QuickJS。
- **产物引用**：`RENDER_SCENE_PROGRAM_JS` 是一个符合契约 §3.2 的 RenderScene 探针
  程序（prepare 恰一批、render 只引用本事务 contentId）。RENDERED 回传
  `scene_sha256`/`scene_bytes`/`content_ids`，分类固定 `JS_RENDERED_UNVERIFIED`：
  harness 没有 RenderScene 事实提取器，`fact_verification=UNVERIFIED`、
  `quality_accepted=False`，**不构成题意合格**。
- **不可用如实降级**：未注入引擎、探测失败、执行中失联 → `status=NOT_AVAILABLE`、
  `classification=JS_NOT_AVAILABLE`，无产物/无延迟。默认 CLI 就是这一态。
- **后端切换**：`run_fake_evaluation(backend="JS")`（CLI `--backend JS`）把 JS 设为
  主后端；每条任务记录的 `backends` 同时含 `IMAGE_MODEL` 与 `JS` 两条记录，切换事件
  双向（IMAGE_MODEL↔JS，同一 operation、预算不重置）。JS 主运行时质量门
  `evaluable=False`（事实核验未接），不输出假门值。
- JS 本地执行不占远程派发（契约 :138），`dispatches_used=0`；token/费用不适用，
  金额记 null、不写 0。
- JS 在产品侧仍是 UNAVAILABLE，不广告（批次 20 已裁边界）。

## 已知缺口（如实）

- `figure_generation_operation/attempt` 表**暂无 token/费用/首图延迟专用列**
  （`core/database/.../entity/FigureGenerationEntities.kt`；批次 20 零 schema
  决定，见 `docs/research/2026-10-25-batch-20-baseline.md` §4）。本 harness 先落
  字段与采集点（`METRIC_COLLECTION_POINTS`）；真实 key 到位后随启用门补列。
- 真实 Provider 成功率/延迟/费用：UNVERIFIED。fake 报告中的门值（如
  首次合格 48.33%）是机制自检输入，**不是**模型质量。
- JS 路径事实核验未接：RenderScene 产物到 `fact_expectations` 的提取/核验器不存在，
  所以 RENDERED 不判合格、JS 主运行质量门不可评。真实设备引擎适配器（`JsEnginePort`
  实现）也尚未落树，默认运行只能如实得到 NOT_AVAILABLE。
- 评审来源当前只有"模型标注，无人工专家"；启用判定前需按 PRD-09 :132 补
  人工专家复核或明确证据限制下的降级口径。

## 引用

- 设计 §6：`docs/superpowers/specs/2026-10-19-product-readiness-design.md:81-97`
- PRD-09：`docs/superpowers/plans/2026-10-19-product-readiness-followup-plan.md:126-140`
- 生图契约（六派发总门）：`docs/current/visual-generation-contract.md:138`
- 统一提示词（预算/切换）：`docs/current/figure-generation-prompt-policy.md` §5
- 六类样例设计表：`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:154-164`
- 桌面 33 场景研究夹具：`.jez/artifacts/flexible-js-drawing-2026-10-17/output/`
  （programs.json：work/cork/waves，经任务 `research_fixture_ref` 引用）
- 既有同类工具先例：`tools/retrieval_quality_gate.py`（离线评测门模式）
