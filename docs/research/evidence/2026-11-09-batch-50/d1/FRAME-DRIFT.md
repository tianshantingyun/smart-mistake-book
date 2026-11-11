# D-1 抽样框漂移记录（批次 50 修复环）

> 生成时点：2026-11-09（修复环）。数据源：`manifest.json`（抽样时点读数）、
> `judgments.csv`（1,600 条已判定样本）、当前 `build/kb-staging/overfull_nodes.csv`、现行成品包。
> 复算命令与输出：`python -I -X utf8 docs/research/evidence/2026-11-09-batch-50/d1/frame_drift_check.py`
> → `frame-drift.json`（本文件所有数字都来自该 JSON，不是手抄）。

## 1. 事实

D-1 的 overfull 分层框是 `build/kb-staging/overfull_nodes.csv`。抽样时（`manifest.json`）
这份文件是：

| 量 | 抽样时点（manifest） | 当前（实读） | 差 |
|---|---:|---:|---:|
| sha256 | `fce8269c…cedfec` | `ce7d6823…c69d9d` | 变了 |
| CSV 数据行 | 63,102 | 62,944 | −158 |
| unique node_id | 2,482 | 2,481 | −1 |
| overfull 层绑定 | 62,948 | 62,944 | −4 |

时间线（`ls -la` 实读）：`manifest.json` / `sample.csv` mtime **14:30:59**；同一框文件
`build/kb-staging/overfull_nodes.csv` mtime **15:20:55**——即抽样后约 50 分钟被 D-2 的
`PYTHONPATH=tools python -X utf8 -m kb_build.overfull_nodes --write` 原地重写
（`d2/README.md:51`、`d2-overfull-report.txt:1`）。该文件是未跟踪的 `build/` 产物，
旧内容未随证据保存，**不可从仓库或 git 恢复**。

`docs/research/2026-11-09-binding-audit.md:103,506` 在开工前已把"先冻结/入库，否则分层框
不可第三方复现"列为前置条件；本批没有执行这一步，属流程缺陷，不是计算错误。

## 2. 量化影响（本修复环实跑）

`sample_d1.py --check` 的真实结果（原样记录，未改脚本语义）：

```text
$ python -I -X utf8 sample_d1.py --check
{"check": "sample_keys", "match": false, "expected": 1600, "actual": 1600}
EXIT=2
```

即：**样本键对当前框不可复放**。按当前框重抽（同 seed、同规则），1,600 条冻结样本里有
**879 条**不在新样本中。REPORT 旧文写的 `match: true` 是抽样时点的读数，现状已不成立。

这不等于判定样本失效：1,600 条材料/节点仍都在包内，且按当前框逐一复核，
**层归属 0 条不一致**（`sample_layer_mismatches = 0`）。因此：

- 每层 `n`/`k`/`p` 不变；
- 变的是层权重 `N_h/N` 与 FPC `(N_h-n_h)/(N_h-1)`。

按当前框 N 复算（同一段代码在抽样时点 N 上逐位复现 `stats.json`，见下）：

| 量 | 抽样时点（stats.json） | 按当前框 N 复算 |
|---|---:|---:|
| 主判据点估计 | 0.9821665565557136 | 0.9821630298422428 |
| 主判据 SE | 0.0033830036607307647 | 0.0033829014742417537 |
| n_eff | 1530.4368543831492 | 1530.8264930683808 |
| **主判据 Wilson 下界** | **0.975691305961763 → PASS** | **0.975688200072439 → PASS** |
| overfull 护栏（no-FPC） | 0.9770088445338914 通过 | 0.9770088445338914 通过 |
| **normal 护栏（no-FPC）** | **0.8979148347511259 未通过** | **0.8979148347511259 未通过** |

结论不变：**PASS main / FAIL guardrail**（`overall_pass=false`）。框漂移不改变门判。

自检：`frame_drift_check.py` 用同一段代码在"抽样时点 N"上复算，主判据下界与 normal 护栏
与 `stats.json` 逐位一致（容差 1e-12，`self_check_against_stats` 全 true）——说明上表不是
换公式后的另一种算法。

## 3. 顺带暴露：护栏对 FPC 口径敏感（原报告未披露）

预注册 G2 的护栏口径是 **no-FPC** 的每层 Wilson 下界（`docs/research/2026-11-09-binding-audit.md:192-196`），
主判据则含 FPC。两者口径不同，原 REPORT §4.3 只对主判据做了 FPC 敏感性，没有披露护栏会翻转：

| normal 层护栏口径 | n_eff | Wilson 下界 | ≥0.90 |
|---|---:|---:|---|
| 预注册 no-FPC（`n=300, k=278`） | 300 | **0.8979148347511259** | **否（差 0.2085pp）** |
| FPC（抽样时点 N=1,905） | 355.8878 | **0.9005765917383107** | 是 |
| FPC（当前 N=1,909） | 355.7489 | **0.9005708241640439** | 是 |

因此 `overall_pass=false` 是**按预注册口径**成立，对 FPC 口径敏感；不得据此断言
"normal 层真实精确率 <0.90"（REPORT §7.4 已写"证据不足"）。阈值与口径都不改。

## 4. 仍然无效 / 仍不可复放的部分

1. 抽样时点的 overfull 名单（sha `fce8269c…`）**无法重建**：旧 CSV 被原地覆盖，仓库内无副本。
   任何"从当前树复跑 D-1 抽样"的声明都不成立。
2. `sample_d1.py --check` 会持续 exit 2；这是**正确行为**（它检测到了漂移），不得为了让门变绿
   而去改它或替换 sample.csv。
3. 本批的补救边界：只证明"层归属与门结论不受影响"，不证明"抽样框可第三方复放"。

## 5. 后续硬规则（写进下一批 D-1 前置）

1. 抽样前把框文件**复制进证据目录**并记 sha（只记 sha 不够：原文件会被下游工具重写）；
2. 抽样脚本只读该冻结副本，不再读 `build/` 活文件；
3. 抽样后任何写框的工具（如 `overfull_nodes --write`）必须写新路径，禁止原地覆盖；
4. `--check` 的期望输出与真实 exit 必须原样抄进 REPORT，不得抄抽样时点的旧读数。
