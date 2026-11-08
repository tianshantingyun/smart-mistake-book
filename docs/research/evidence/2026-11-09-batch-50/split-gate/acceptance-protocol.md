# 批次 50 · AUD-08 acceptance 一次性运行：冻结与承诺（跑前写入）

> 写入时点：**acceptance 8 页尚未运行**。本文件先冻结配置与判据，再启动那唯一一次运行
> （协议出处：`docs/research/2026-11-09-split-cv-llm.md` §8.5「运行前把 scope 文案抄进记录」）。

## 1. 冻结物

| 项 | 值 | 来源 |
|---|---|---|
| 金标 | `tools/split_golden/tables/split_golden_v2.json` | sha256 `03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54`（与旁车逐字节一致，本会话实读） |
| 金标等级 | **G1**（model-labeled，单模型盲标，无人工专家） | v2 根 `evidenceGradeLevel`；不得称"人工金标" |
| dev / acceptance | 16 页 / 8 页（`split` 块；sha256(name) 升序前 16 / 后 8） | v2 `split` |
| 臂 | **B. LLM+hint**（协议 §8.2：唯一有资格进门的臂） | `layoutHint = LayoutStructure.toPromptBlock()` |
| 生产提示词 | sha256 `31aef2ab0f07b8d3e3337d4f64009e9bf3bdd08d1050d3e784990ac8a9bb496d`（dev 无提示臂 16/16 页同一 sha，本会话实算） | `GoldenSplitEvalHintChannelHarness` 逐页 `promptSha256` |
| LayoutConfig | 生产默认值（`structureDelta=20`、`valleyRatio=0.45`、`structureValleyRatio=0.15`、`minValleyGapPx=3`、`minGutterWidthRatio=0.02`、`mergeGapRatio=0.02`、`minSideMassRatio=0.20`、`minBothSideRowShare=0.45`、`paperRowShare=0.25`、其余为候选/探针参数） | `core/model/.../layout/LayoutStructure.kt:19-56` |
| 模型 | `deepseek-flash`（`api.deepseek.com`），key 仅 sha8 `cd10efbc` | harness 强校验 |
| 图像 | 出网副本按 `EgressImageBudget`（长边 2048 / q88 / q70 回落），逐页 meta 与 sha 落在 run 目录 | `build/batch48-ws2/prepare_egress.py` |
| Gradle | `:core:data:testDebugUnitTest --tests …GoldenSplitEvalHintChannelHarness`（串行；daemon 预停） | `build/batch50-split/run_split_eval_hint.sh` |

## 2. 一次性承诺（跑前写死）

1. acceptance 8 页**只跑这一次**；无论结果如何都**不重跑换绿**、不回调用参。
2. 结果只有三种：`PASS` / `FAIL` / `NOT_EVALUABLE`；未达 0.9/0.05 即如实 FAIL（计划 :53）。
3. 判门用 `check_split_gate.py --golden …v2.json --app <24 页合并导出>`；dev 16 页取
   `dev-hint-01`（同配置：生产提示词 + hint，本批未再改提示词），acceptance 8 页取本次 run。
4. 门脚本 scope（v2）原文抄录：**"v2 已做 dev/acceptance（16/8）切分：dev 16 页参与了切分
   修复调参，acceptance 8 页只复算一次；门值不外推为未参与调参页面的准确率"**
   （`tools/split_golden/check_split_gate.py:202-208`）。

## 3. 必须先说清的口径限制（不得省略）

1. **这份 acceptance 切片在批次 48 已被消费过一次**：`docs/research/evidence/2026-11-07-batch-48/splitter/`
   的 `acceptance-once-01`（8 页，① 5/8=0.625，③ 3/26=0.1154，报告
   `gate-report-v2-01.json`）。那一次的逐页失分模式已写进批次 48 README 并可见于本库，
   因此本轮的 acceptance **不是**首次盲测，held-out 属性**不可恢复**。本轮如实称为
   "同一 8 页在新配置下的**第二次观测**"，门值按批次 50 判据照算，但不宣称"未参与调参页面的准确率"。
2. v2 的 `heldOutUnusedForTuning=true` 是**自证字段**；结合第 1 条，它在 batch 50 语境下
   不成立（GATE.md §2.1 的机检不可项）。
3. acceptance 8 页有 **0 张双栏**（研究 §8.1）→ 栏逻辑在验收集上是盲区。

## 4. 预期（跑前登记，避免事后叙事）

dev 同配置实测 ① 4/16 = 0.2500（`dev-hint-01`）；acceptance 8 页的 ① 是 **8/8 全或无**
（7/8=0.875 < 0.9，研究 §8.6）。据此预先判断：本轮门**大概率 FAIL**；若出现 PASS 才属意外，
届时按真值报告并复核是否有通道异常。
