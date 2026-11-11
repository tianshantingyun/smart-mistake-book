# 批次 51 · AUD-08 acceptance 一次性运行与判门（2026-11-10）

> 范围：acceptance 8 页**唯一一次**运行 + dev（同提示词两场观测）合并 24 页过
> `tools/split_golden/check_split_gate.py`。阈值（① 0.9 / ③ 0.05）、分母、金标、提示词、
> 产品代码均未改；切题只走模型识别，无 CV/几何 hint。跑前冻结见 `acceptance-protocol.md`
> （写入时间早于运行）。

## 1. 一次性合规（可核）

- 跑前先写 `acceptance-protocol.md`（§1 冻结/§2 承诺/§3 偏差/§4 判门规则/§5 预期）。
- run 目录只有 **1 个**：`run/acceptance-b51-01/`；脚本对已存在 `manifest.json` 的目录直接拒绝
  （`build/batch51-split/run_split_acceptance.sh:18`）。
- 页集硬断言：`pages == split.acceptance`（8 页，逐页顺序）且与 `split.dev` 交集 0
  （`run_split_acceptance.sh:22-31`；manifest 实测 8/8 同名）。
- 未重跑、未换绿、未回调用参；acceptance 8 页未进入任何 dev 调参命令（5 份 dev manifest
  页集均恒等于 `split.dev`，本阶段实读）。

## 2. 冻结配置（实读/实算）

| 项 | 值 |
|---|---|
| 金标 | `split_golden_v2.json` sha256 `03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54`（旁车一致；冻结校验 PASS；G1/model-labeled） |
| acceptance 切片 | 8 页 / 26 题，sha256 `c9138ba284aacee8199596e64a44a4ca8c767be339cd2aeb0da8e468b9aa4bc7`（build 与证据目录逐字节一致） |
| 提示词 | v3.3，2204 字符，sha256 `7f92b456dea4bbdd5d47e4d2f1bff62375b6482298dc6444fe675c54e0a76061`；本阶段从 Kotlin 源重抽 == `prompt-final.txt`；run 内 8/8 页 `promptSha256` 均为该值 |
| 模型/端点 | `deepseek-flash` / `https://api.deepseek.com`，temperature 0.1，`response_format=json_object`；key 只记 sha8 `cd10efbc` |
| 通道 | `GoldenSplitEvalChannelHarness`（真实 `requestBody` / `parseCompletion` / `isUsableSplitRegionSet`）；直连 HTTPS、PIL 重采样、无设备 DB 落库（与 dev 同一通道，差异见 dev-tuning.md §1） |

运行命令（本阶段实跑，Gradle `--no-daemon` 串行；运行前确认 daemon 空闲、CPU 3s 未增长）：

```
bash build/batch51-split/run_split_acceptance.sh acceptance-b51-01   # GRADLE_EXIT=0, MEASURE_EXIT=0
```

## 3. acceptance 单切片读数（8 页 / 26 题，口径 v3）

| 指标 | 读数 | 说明 |
|---|---|---|
| ① 题数一致 | **7/8 = 0.8750** | 未达 0.9（8 页是 8/8 全或无） |
| ② IoU≥0.5 | **17/26 = 0.6538** | 只度量，不设门 |
| ③ 切残 | **1/26 = 0.0385** | ≤ 0.05 |
| 弃答 | 0/8 | RECAPTURE/NEED_MORE_IMAGE = 0 |
| 通道不可评 | 0/8 | 8 页 status 全 OK |
| 旧口径对照 (v1/v2) | 6/8 = 0.7500 | `IMG20260810011800`（金标 1 题、应用 PASS 0 框）在 v3 记一致 |

逐页（按金标页序）：

| 页 | 金标题 | 应用框 | 页类 | ① | 配对 IoU | ③ |
|---|---|---|---|---|---|---|
| IMG20260810003928 | 4 | 4 | ACCEPTED_SPLIT | 一致 | 0.874/0.906/0.668/0.691 | 0 |
| IMG20260810004353 | 2 | 2 | ACCEPTED_SPLIT | 一致 | 0.496/0.000 | 0 |
| IMG20260810004952 | 2 | 3 | ACCEPTED_SPLIT | **不一致（2 vs 3）** | 0.652/0.000 | **1** |
| IMG20260810005711 | 3 | 3 | ACCEPTED_SPLIT | 一致 | 0.890/0.815/0.710 | 0 |
| IMG20260810010034 | 6 | 6 | ACCEPTED_SPLIT | 一致 | 0.731/0.860/0.857/0.634/0.871/0.868 | 0 |
| IMG20260810010202 | 3 | 3 | ACCEPTED_SPLIT | 一致 | 0.872/0.799/0.649 | 0 |
| IMG20260810011358 | 5 | 5 | ACCEPTED_SPLIT | 一致 | 0.110/0.378/0.375/0.123/0.000 | 0 |
| IMG20260810011800 | 1 | 0 | ACCEPTED_NO_SPLIT | 一致（PASS=整页一题） | — | 0 |

失败归因（只据导出与逐页记录，不猜）：

- `004952`：金标 2 题、应用 3 框 → 多切一题；金标第 0 题被 1 个应用框切残（③ 的唯一来源）。
- `004353`、`011358`：题数一致但 ② 为 0/2 与 0/5——框边界偏差（② 不设门，只登记）。
- `011800`：单题页 PASS 被 v3 正确记一致（批次 51 口径修正的直接效果）。

## 4. 合并 dev+acceptance 判门（`check_split_gate.py`，exit 实测）

跑前冻结规则（`acceptance-protocol.md` §4）：主门用 `dev-v33-02`（冻结提示词的**最后一场** dev 观测，
不按分数挑），`dev-v33-01` 作同数据敏感性读数。

| 门 | 合并导出 sha256 | ①（≥0.9） | ③（≤0.05） | fragmented（硬红线=0） | exit |
|---|---|---|---|---|---|
| **主门**（dev-v33-02 + acceptance-b51-01） | `e8ea2e464c67d368c7b8e884476639ea7a57ebc24b1c6f7ecb379118f03b431c` | **19/24 = 0.7917 未过** | **4/78 = 0.0513 未过** | **4（004511/004854/004952/005855）未满足** | **1（FAIL）** |
| 敏感性（dev-v33-01 + acceptance-b51-01） | `eaf17fa57f14b12ede7aa50d3df4f237b3b8bbe8873bf7ab1a49093d523b9070` | **21/24 = 0.8750 未过** | 3/78 = 0.0385 过 | **3（004511/004952/011017）未满足** | **1（FAIL）** |

主门逐页不一致：`004511`、`004952`、`005144`、`005301`、`011017`（前 4 项中 004511/005144/005301/011017
为 dev 已知失败：金标坐标帧转置与跨版照语义；004952 为本轮 acceptance 新失分）。
② 主门 48/78 = 0.6154；敏感性 53/78 = 0.6795（只度量）。

**门判定：FAIL**（①<0.9；主门 ③ 亦 >0.05；`fragmentedQuestions` 3–4 > 0）。
`gate.passed=false`、`hardRedLines.satisfied=false`；门后自动化不启用（裁定 37 / 4C③）。

## 5. 偏差与限制（不得当作已通过/不得引用为 citable PASS）

1. **没有「从未看过的新 acceptance 切片」**：唯一冻结金标 v2 只有 24 页（dev 16 + acceptance 8），
   无 v3/未用页池。同一 8 页已先后在批次 48（`acceptance-once-01`，① 5/8、③ 3/26）与批次 50
   （`acceptance-b50-01`，合并 ① 7/24、③ 1/78）各跑一次 ⇒ **本轮是第三次观测**，held-out 不可恢复。
   即使读数达标也不得记 citable PASS；本轮读数未达标，结论更不受影响。
   **因此计划项 23「新 acceptance 切片（从未看过）只跑一次」未完成**：本批只做到「同一 8 页的
   第三次观测、本批唯一一次 run」；要完成该项需新标注一套未用页金标（仓内没有）。
2. `check_split_gate.py` 生成的 scope 文案写「本批是第二次观测」——那是批次 50 的语境；按本文件第 1 条，
   实际为**第三次**。本轮不改门脚本（他线 M 文件、且文本非阈值项），在此取代声明。
3. 金标 v2 是 155 页数据集按步长 6 的**系统抽样**、单模型盲标（G1），不是概率样本、无人工复核。
4. acceptance 8 页 **0 张双栏**（批次 50 协议 §3.3）；双栏逻辑在验收集上是盲区。
5. 单臂、单模型、temperature 0.1、单轮采样；dev 两场 ① 差 2 页（14→12）已说明点估计抖动，
   本轮 acceptance 只有一次采样，无置信区间。
6. 通道差异同 dev（直连传输 / PIL 重采样 / 无设备 DB 落库）；门值不外推为设备产品路径准确率。
7. 结构限制沿用 dev 登记：004511/011649 金标坐标帧转置（固定伪切残）、011017 金标框高 0.030 < 0.04。

## 6. 未跑 / 未验证

- 未改门阈值、金标、提示词、产品代码、6C-2、JS/UI/KB 他线文件。
- 未做设备路径复验；未做跨模型/跨温度复验；未做人工复核。
- `bundleDebugAndroidTest`/全量单测门未在本阶段跑（本轮只跑
  `:core:data:testDebugUnitTest --tests …GoldenSplitEvalChannelHarness`）。

## 7. 产物索引（sha256 见同名 `.sha256` 或下表）

```
docs/research/evidence/2026-11-10-batch-51/split/acceptance/
├─ acceptance-protocol.md                          # 跑前冻结（写于运行之前）
├─ golden-v2-acceptance-slice.json(.sha256)        # 8 页/26 题切片
├─ run/acceptance-b51-01/
│  ├─ IMG*.json                                    # 逐页记录（含 responseRaw 全文、promptSha256）
│  ├─ manifest.json                                # 8 页、runId、keySha8
│  ├─ export.json(.sha256)                         # 40ffa7c7365e…；8 页/26 框
│  ├─ metrics.txt / metrics.json                   # ① 7/8、② 17/26、③ 1/26
│  └─ gradle.log
└─ gate/
   ├─ merged-24-primary.json(.sha256)              # dev-v33-02 + acceptance
   ├─ merged-24-alt-dev-v33-01.json(.sha256)       # dev-v33-01 + acceptance（敏感性）
   ├─ gate-report-b51-primary.json                 # FAIL；① 19/24、③ 4/78、frag 4
   ├─ gate-report-b51-alt-dev-v33-01.json          # FAIL；① 21/24、③ 3/78、frag 3
   ├─ gate-evidence-b51-primary.md / …-alt….md     # 证据（逐页/阈值出处/scope）
   ├─ gate-primary.stdout.txt / gate-alt.stdout.txt# exit 与 stdout
   └─ rulings-primary/…json / rulings-alt/…json     # 逐页裁定（各 1 份）
```

关键 sha256：`manifest.json` `33219a45…`；`metrics.json` `e7b0d8bb…`；
`gate-report-primary` `485cb62e…`；`gate-report-alt` `3008cbd0…`。
