<!-- 体积自查：74 行 / 5960 B；R4 上限 400 行 / 65536 B -->
# AUD-08 切分金标门 —— 本轮门判定证据（R4 模板精神）

- 生成时刻（UTC）：2026-11-06T18:16:32Z
- 门状态：**FAIL**（exit 1）
- 自动化：门后自动化不启用（裁定 37 / 4C③：门不过不允许自动入库，切残不得自动入库）。本门脚本不切任何产品开关；开启门后自动化须另立产品裁定（4C③）。
- 门阈值：①≥0.9（立项自定）且 ③≤0.05；出处见 GATE.md 与报告 thresholds（改值须先裁定并留旧值对照）

## 1. E-ID 表

| E-ID | 步骤 | 判定 | 证据（本文件节） |
|---|---|---|---|
| E-A08-T1 | 金标冻结校验（validate C1–C6+C7） | PASS（子步骤） | §2 |
| E-A08-T2 | 度量（measure --json） | PASS（子步骤） | §3、§5 |
| E-A08-T3 | 门判定（0.9/0.05 闭边界） | FAIL | §4 |

## 2. 产物身份（sha256）

| 产物 | 路径 | 字节 | sha256 |
|---|---|---|---|
| 金标 split_golden_v2.json | `tools\split_golden\tables\split_golden_v2.json` | — | `03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54` |
| 应用导出 app-split-golden-v2-final.json | `docs\research\evidence\2026-11-07-batch-48\splitter\app-split-golden-v2-final.json` | 15174 | `abb7744fcba2ecd0c07dba30bdebf703693c7ef00d65fba4bdc1dc4965b717f5` |
| 应用导出 source 块 | 有（db sha256 `None`；页名来源 null） | — | — |
| 应用导出输入版本 | app schemaVersion 2；db user_version None；快照身份 `None`（None）；载荷 `None`；生产者 `None`（None） | — | — |

## 3. 命令与退出码（可复跑）

1. `python.exe D:\smart mistake book\tools\split_golden\measure_split_accuracy.py --golden tools\split_golden\tables\split_golden_v2.json --app docs\research\evidence\2026-11-07-batch-48\splitter\app-split-golden-v2-final.json --json` → exit 0

## 4. 指标与门判定

- ① 题数完全一致率 = 10/24 = 0.4167 （Wilson95 0.2447–0.6117，描述性区间非门）；门 0.4167 >= 0.9 → 未过
- ② 边界 IoU≥0.5 题占比 = 32/78 = 0.4103（只度量，不设门）
- ③ 切残率 = 3/78 = 0.0385 （Wilson95 0.0132–0.1071，描述性区间非门）；门 0.0385 <= 0.05 → 过
- 门判定：**FAIL**（① 0.4167 vs 0.9；③ 0.0385 vs 0.05）
- 切残硬红线（不依赖 0.05 阈值）：切残题数 3 / 要求 0 → 未满足（切残不得入库）

## 5. 逐页裁定

| file | 来源 | 金标题数 | 应用框数 | ①一致 | IoU≥0.5 达标 | 切残 |
|---|---|---|---|---|---|---|
| IMG20260810003928.jpg | golden_split_eval_harness | 4 | 4 | 一致 | 2 | 1 |
| IMG20260810004304.jpg | golden_split_eval_harness | 1 | 0 | 一致 | 0 | 0 |
| IMG20260810004353.jpg | golden_split_eval_harness | 2 | 2 | 一致 | 1 | 1 |
| IMG20260810004511.jpg | golden_split_eval_harness | 3 | 0 | 不一致 | 0 | 0 |
| IMG20260810004854.jpg | golden_split_eval_harness | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810004952.jpg | golden_split_eval_harness | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810005144.jpg | golden_split_eval_harness | 7 | 0 | 不一致 | 0 | 0 |
| IMG20260810005301.jpg | golden_split_eval_harness | 3 | 3 | 一致 | 3 | 0 |
| IMG20260810005605.jpg | golden_split_eval_harness | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810005711.jpg | golden_split_eval_harness | 3 | 3 | 一致 | 3 | 0 |
| IMG20260810005855.jpg | golden_split_eval_harness | 3 | 3 | 一致 | 2 | 0 |
| IMG20260810010034.jpg | golden_split_eval_harness | 6 | 5 | 不一致 | 5 | 0 |
| IMG20260810010202.jpg | golden_split_eval_harness | 3 | 3 | 一致 | 2 | 1 |
| IMG20260810010306.jpg | golden_split_eval_harness | 4 | 3 | 不一致 | 3 | 0 |
| IMG20260810010349.jpg | golden_split_eval_harness | 4 | 4 | 一致 | 3 | 0 |
| IMG20260810010633.jpg | golden_split_eval_harness | 3 | 0 | 不一致 | 0 | 0 |
| IMG20260810010859.jpg | golden_split_eval_harness | 4 | 3 | 不一致 | 3 | 0 |
| IMG20260810011017.jpg | golden_split_eval_harness | 3 | 0 | 不一致 | 0 | 0 |
| IMG20260810011126.jpg | golden_split_eval_harness | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810011248.jpg | golden_split_eval_harness | 5 | 4 | 不一致 | 3 | 0 |
| IMG20260810011358.jpg | golden_split_eval_harness | 5 | 0 | 不一致 | 0 | 0 |
| IMG20260810011649.jpg | golden_split_eval_harness | 3 | 2 | 不一致 | 0 | 0 |
| IMG20260810011800.jpg | golden_split_eval_harness | 1 | 0 | 一致 | 0 | 0 |
| IMG20260810012103.jpg | golden_split_eval_harness | 3 | 3 | 一致 | 2 | 0 |

## 6. 未验证 / UNVERIFIED（如实登记）

- 金标证据等级：G1（产物字段可复算（C7 已检查该等级要求的登记字段））；原始 evidenceGrade=`model-labeled, no human expert; single model (GLM-5.3-Flash via ZCode harness) blind labeling of all 24 pages from page images only (no split-algorithm code/output, no v1 boxes read before labeling); same-model 8-page re-pass recorded under agreement as consistency evidence only (not independent per protocol §3.3); no human review; grade capped at G1`。真实人工正确率 **UNVERIFIED**，不得当作人工金标引用
- 应用导出来源块：present（db sha256 `None`；db user_version None；逐页 nameSource 见 §5）
- **适用范围（不可外推）**：本门值（① 10/24=0.4167、③ 3/78=0.0385）只对 金标 sha `03b1eeccbbde…` × 应用导出 sha `abb7744fcba2…` 这份样本成立；**不等于**当前设备库切分准确率，也**不等于**该模型在 155 页数据集上的准确率（证据等级 G1）。
  - 金标 v2 的 24 页是 155 页数据集按步长 6 的系统抽样，不是概率样本；标注为单模型盲标（G1，model-labeled），无人工专家复核，坐标为模型估读（±0.01–0.02）
  - v2 已做 dev/acceptance（16/8）切分：dev 16 页参与了切分修复调参，acceptance 8 页只复算一次；门值不外推为未参与调参页面的准确率
- 门后自动化：始终未启用（仓内无门控开关；开启属 4C③ 产品裁定，不在工具职责内）

