# 批次 50 修复环（高/中项）— 修复记录

> 规则：只修高/中；不改门阈值；AUD-08 不重跑 acceptance；不伪造外部项。
> 红/绿证据：`red-before.txt`（修复前读数）+ 本文件 §3（修复后实跑）。
> 相关 finding 均为"已独立复核"；本环逐条复核并给出处置。

## 1. 逐条处置

| # | finding（中） | 处置 | 落点 |
|---|---|---|---|
| 1 | AUD-08 acceptance 不是首次盲测；"只跑一次"全局口径不成立 | **已修（口径）**：范围文案改为"本批只运行一次 + 同一 8 页已在批次 48 消费、held-out 不可恢复"；原 gate 报告/证据保持原字节，另出取代声明 | `tools/split_golden/check_split_gate.py:202-212`；`split-gate/SCOPE-CORRECTION.md`；`verdicts.md` §0/§3；生成器 `make_verdicts_report.py` |
| 2 | D-1 抽样框被 D-2 原地重写；`--check` match=false，REPORT 却写 match=true | **已修（证据）**：新增漂移量化器与 JSON；REPORT §2/§4.3/§7/§8/§9 改为真实读数；不改 `sample_d1.py`、不重抽、不换样本 | `d1/frame_drift_check.py`；`d1/frame-drift.json`；`d1/FRAME-DRIFT.md`；`d1/REPORT.md` |
| 3 | 同一 finding 的重复条目（抽样框未冻结/旧 CSV 未保存） | **已修（同上）+ 后续硬规则**：抽样前必须把框复制进证据目录；写框工具不得原地覆盖 | `d1/FRAME-DRIFT.md §5` |
| 4 | MVA 不可评且旧门 `critical_error_accepted` 恒真 | **已修（标注）**：`machinery_check` 增 `critical_error_evidence.citable=false` + reason；`:980` 注释"不可引用为门证据"；不改门值/判定 | `tools/figure_eval/figure_eval_gate.py`；`mva/README.md §3` |
| 5 | 3D 主判据 κ NOT RUN 未随记录/终裁携带 | **已修（披露）**：record 3D 节、5c 引用、终裁、verdicts 判词/不可变项、生成器均加"model-labeled 暂定值，按 R2 §4.5 不得用于门" | `record.md`；`verdicts.md`；`make_verdicts_report.py` |
| 6 | Wilson 护栏对 FPC 口径敏感，原报告未披露 | **已修（披露）**：REPORT §4.3/§4.4 并列 no-FPC 0.8979148（FAIL）与 FPC 0.9005766/0.9005708（PASS）；`overall_pass=false` 按预注册口径 | `d1/REPORT.md §4.3`；`d1/frame-drift.json` |
| 7 | 3E 第二档无预留份额，默认配置"能捞回"不成立 | **阻塞**（产品语义裁定） | `repair/BLOCKED.md B-2` |
| 8 | 5c 生产无候选生产者，功能未端到端闭环 | **阻塞**（缺结构化 trace 采集 + 产品裁定） | `repair/BLOCKED.md B-1` |
| 9 | JS 生产装配未完成（visualEngine/能力广告） | **阻塞**（他线在飞 + MVA FAIL/KD-76） | `repair/BLOCKED.md B-3` |
| 10 | AAB/APK 是脏树产物 | **阻塞**（须主会话提交后复跑干净树） | `repair/BLOCKED.md B-4` |
| 11 | soak 的 PASS 只能读作 crash-free | **已修（口径）**：README §6 增"PASS 判据边界" | `device/README.md §6` |

低严重度项（3D 标注如实、release 签名/16KB 真实、5b/5c/6B-3 真接线、端到端边界、D-2 未清零、
D-3 边界、signing 门未断言 signer≠debug）按原记录保留，本环不改；其中 signing 门缺口（低）
已由独立复核记录在案，未在此扩大改动面。

## 2. 修复前后对照（关键数字）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `sample_d1.py --check` | exit 2 / match=false（REPORT 错写 true） | exit 2 / match=false（REPORT 如实） |
| 抽样框 | manifest `fce8269c…` 62,948 绑定；当前 `ce7d6823…` 62,944 | 同左，并量化：层归属 0 错配、重抽 879/1600 键不同 |
| D-1 主判据 | 0.9756913 PASS（stats 口径） | 按当前 N 0.9756882 PASS；自检在抽样 N 上逐位复现 stats |
| normal 护栏 | 0.8979148 FAIL | FAIL（no-FPC 预注册）；FPC 0.9005766 → PASS（差异披露） |
| AUD-08 门 | FAIL（① 7/24、③ 1/78、frag=1） | **不变**（未重跑；`gate-report-b50.json` 原字节保留） |
| MVA 关键错误字段 | 恒真 0，无标记 | 同值 + `citable=false` + reason |
| 门阈值 | 3D 0.95 / 3E 10 / MVA 90/95/90/0 / AUD-08 0.9/0.05/0 | **全部未改**（测试断言） |

## 3. 修复后实跑（命令与读数）

```text
# 1) D-1 漂移量化（exit 0 = 漂移已量化且结论不变）
$ python -I -X utf8 docs/research/evidence/2026-11-09-batch-50/d1/frame_drift_check.py
{"replay_exit": 2, "sample_layer_mismatches": 0, "resample_different_keys": 879,
 "main_lower": 0.975688200072439, "main_pass": true,
 "guardrail_nofpc_lower_normal": 0.8979148347511259, "guardrail_nofpc_pass": false,
 "guardrail_fpc_lower_normal": 0.9005708241640439, "guardrail_fpc_pass": true,
 "sampling_frame_fpc_lower_normal": 0.9005765917383107,
 "self_check_against_stats": {"main_lower_match": true, "normal_guardrail_nofpc_match": true},
 "conclusion_unchanged": true}
EXIT=0

# 2) 原独立复算仍全匹配（未改判定/公式）
$ cd docs/research/evidence/2026-11-09-batch-50/d1 && python -I -X utf8 recompute.py
all_match True   （point/se/n_eff/lower/pass/guardrail/两层正确数 全 true）

# 3) 样本键复放：如实红（正确行为）
$ cd docs/research/evidence/2026-11-09-batch-50/d1 && python -I -X utf8 sample_d1.py --check
{"check": "sample_keys", "match": false, "expected": 1600, "actual": 1600}   EXIT=2

# 4) 修复不变量（新增；修复前状态见 red-before.txt 的命令级读数，测试文件本身在修复后落盘并全绿）
$ python -I -X utf8 -m pytest tools/tests/test_batch50_repair_invariants.py -q
10 passed

# 5) 既有套件（未放宽断言）
$ python -I -X utf8 -m pytest tools/tests/test_batch50_repair_invariants.py tools/tests/test_split_gate.py -q
71 passed
$ python -I -X utf8 -m pytest tools/figure_eval/test_figure_eval_gate.py tools/figure_eval/test_mva_gate.py -q
46 passed
```

未运行：Gradle/设备用例（本环未改 Kotlin 生产码；3E/5c 阻塞项见 BLOCKED）；
AUD-08 acceptance 未第二次消费（只改了范围文案与声明，未重跑门）。

## 4. 本环改动文件

代码/工具：
- `tools/split_golden/check_split_gate.py`（范围文案；无阈值/判定改动）
- `tools/figure_eval/figure_eval_gate.py`（citable 标注 + 注释；verdict 语义未变）
- `tools/tests/test_batch50_repair_invariants.py`（新增 10 例）
- `docs/acceptance/evidence/2026-11-09-batch-50/make_verdicts_report.py`（判词/不可变项文案）

证据/文档：
- `d1/frame_drift_check.py`、`d1/frame-drift.json`、`d1/FRAME-DRIFT.md`、`d1/REPORT.md`
- `split-gate/SCOPE-CORRECTION.md`
- `mva/README.md`、`device/README.md`
- `repair/BLOCKED.md`、`repair/red-before.txt`、`repair/REPAIR-RING.md`
- `docs/acceptance/verdicts.md`、`docs/research/2026-11-09-batch-50-record.md`

未改：`d1/sample_d1.py`、`d1/sample.csv`、`d1/judgments.csv`、`d1/stats.json`、
`split-gate/gate-report-b50.json`、`split-gate/MANIFEST-SHA256.txt`、任何门阈值、任何 KB 包内容。
