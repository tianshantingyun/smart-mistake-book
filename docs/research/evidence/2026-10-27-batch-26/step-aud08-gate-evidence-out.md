<!-- 体积自查：51 行 / 2926 B；R4 上限 400 行 / 65536 B -->
# AUD-08 切分金标门 —— 本轮门判定证据（R4 模板精神）

- 生成时刻（UTC）：2026-10-26T22:54:09Z
- 门状态：**FAIL**（exit 1）
- 自动化：门后自动化不启用（裁定 37 / 4C③：门不过不允许自动入库，切残不得自动入库）。本门脚本不切任何产品开关；开启门后自动化须另立产品裁定（4C③）。
- 门阈值：①≥0.9（立项自定）且 ③≤0.05；出处见 GATE.md 与报告 thresholds（改值须先裁定并留旧值对照）

## 1. E-ID 表

| E-ID | 步骤 | 判定 | 证据（本文件节） |
|---|---|---|---|
| E-A08-T1 | 金标冻结校验（validate C1–C6） | PASS | §2 |
| E-A08-T2 | 度量（measure --json） | PASS | §3、§5 |
| E-A08-T3 | 门判定（0.9/0.05 闭边界） | FAIL | §4 |

## 2. 产物身份（sha256）

| 产物 | 路径 | 字节 | sha256 |
|---|---|---|---|
| 金标 split_golden_v1.json | `D:\smart mistake book\tools\split_golden\tables\split_golden_v1.json` | — | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` |
| 应用导出 app-split.json | `D:\smart mistake book\build\split-eval\app-split.json` | 1482 | `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6` |

## 3. 命令与退出码（可复跑）

1. `python.exe D:\smart mistake book\tools\split_golden\measure_split_accuracy.py --golden D:\smart mistake book\tools\split_golden\tables\split_golden_v1.json --app D:\smart mistake book\build\split-eval\app-split.json --json` → exit 0

## 4. 指标与门判定

- ① 题数完全一致率 = 4/7 = 0.5714 （Wilson95 0.2505–0.8418，描述性区间非门）；门 0.5714 >= 0.9 → 未过
- ② 边界 IoU≥0.5 题占比 = 7/16 = 0.4375（只度量，不设门）
- ③ 切残率 = 0/16 = 0.0000 （Wilson95 0.0000–0.1936，描述性区间非门）；门 0.0000 <= 0.05 → 过
- 门判定：**FAIL**（① 0.5714 vs 0.9；③ 0.0000 vs 0.05）

## 5. 逐页裁定

| file | 金标题数 | 应用框数 | ①一致 | IoU≥0.5 达标 | 切残 |
|---|---|---|---|---|---|
| IMG20260810003928.jpg | 4 | 4 | 一致 | 4 | 0 |
| IMG20260810004304.jpg | 1 | 0 | 一致 | 0 | 0 |
| IMG20260810004353.jpg | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810005605.jpg | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810010349.jpg | 4 | 4 | 一致 | 3 | 0 |
| IMG20260810011126.jpg | 2 | 0 | 不一致 | 0 | 0 |
| IMG20260810011800.jpg | 1 | 0 | 一致 | 0 | 0 |

## 6. 未验证 / UNVERIFIED（如实登记）

- 金标证据等级：`model-labeled, no human expert`（代理金标，两名标注员一致、无人工专家）——真实人工正确率 **UNVERIFIED**，不得当作人工金标引用
- 应用导出与设备/DB 版本的绑定：以导出文件内 `source` 溯源块为准；缺溯源块的导出，其设备来源 **UNVERIFIED**
- 门后自动化：始终未启用（仓内无门控开关；开启属 4C③ 产品裁定，不在工具职责内）

