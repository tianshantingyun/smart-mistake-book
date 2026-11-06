# WS8 item 59：AUD-08 结果面向禁令复扫

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS8-59-PROHIBITION-RESCAN-01` |
| batch | 2026-11-05-batch-43 / 计划 §2 WS8 item 59 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86` |
| executed-at | 2026-11-03T21:39Z（本会话 UTC） |
| 扫描对象 | AUD-08 结果面：`tools/split_golden/GATE.md`、抽样计划、协议、两批裁定证据、本轮门证据 |
| verdict | **0 违规（0 error）；3 条既有 warning 未变**；静态扫描局限已登记 |
| 本会话实跑 | 泄漏扫描器 + 11 例自测 + 阈值/覆盖行为/文本面复扫 |

## 1. 这一项要消灭的具体失败

批次 41 建立了 `tools/ci/check_eval_label_leakage.py`，但禁令是**面向结果**的：门红时把子步骤
`PASS` 写成门级通过、用旧导出冒充新设备、把 G2 的绿写成"人工金标通过"——这些都不会让任何
脚本变红。本项对**当前这批结果文本与产物**重跑机器扫描 + 七条禁令逐条核对；不复用批次 41
的结论，也不因为上一批干净就跳过。

## 2. 机器扫描（泄漏/调参面）

```bash
python -I tools/ci/check_eval_label_leakage.py
# 扫描 25 个候选脚本；protected 标签读者 8 个
# [warning] THRESHOLD_OVERRIDE_SURFACE retrieval_quality_gate.py:155 — '--recall5-threshold'
# [warning] ...:156 — '--mrr-threshold'
# [warning] ...:157 — '--ndcg5-threshold'
# error 0 / warning 3 → exit 0
```

```bash
python -I -m unittest discover -s tools/tests -t tools -k eval_label_leakage
# Ran 11 tests in 0.277s
# OK
# exit 0
```

- 候选脚本 25（批次 41 为 24）；protected 标签读者仍 8 个，名单与批次 41 逐项一致：
  `dense_build/export_golden_query_vectors.py`、`figure_eval/figure_eval_gate.py`、
  `kb_coverage/apply_rejudged_materials.py`、`kb_coverage/make_golden_slices.py`、
  `kb_coverage/validate_golden.py`、`retrieval_quality_gate.py`、
  `split_golden/measure_split_accuracy.py`、`split_golden/validate_split_golden.py`。
  机器口径 JSON：`build/ws8-batch43/leakage-scan-b43.json`（`summary.errors=0`、
  `summary.warnings=3`、`summary.files=25`）。
- 3 条 warning 是 `tools/retrieval_quality_gate.py:155-157` 的阈值 CLI 覆盖面，属**调参面**、
  不是已调参证据；按“只报不改”未修，与批次 41 一致。
- 0 个 `LABEL_DERIVED_THRESHOLD` / `LABEL_DERIVED_PARAM_OUTPUT`。
- 局限（脚本随报告输出）：静态 AST 看不到动态路径、跨仓库生成、git 历史改值；clean ≠ 正确性证明。

## 3. 七条禁令逐条复扫（`tools/split_golden/GATE.md:234-242`）

### 3.1 禁止 1：不得为让门变绿改阈值/口径/分母/金标

| 检查 | 命令 | 结果 |
|---|---|---|
| 阈值常量 | `grep -n "COUNT_GATE\|FRAGMENT_GATE" tools/split_golden/check_split_gate.py tools/split_golden/measure_split_accuracy.py` | `COUNT_GATE = 0.9`（`check_split_gate.py:91`）、`FRAGMENT_GATE = 0.05`（`:92`）逐字未变 |
| 工具/金标未改 | `git diff --stat -- tools/split_golden/ tools/ci/check_eval_label_leakage.py tools/tests/test_check_eval_label_leakage.py` | **空输出（exit 0）** |
| 报告阈值 | 读 `build/ws8-batch43/gate-report-b43.json` | `countExactMin >= 0.9`、`fragmentMax <= 0.05` |
| 金标身份 | 报告 `golden.sha256` | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f`（与批次 41/42 同） |

**通过**：阈值、口径、分母、金标零改动。

### 3.2 禁止 2：不得用 `NOT_EVALUABLE` 代替 `FAIL`

- 报告 `gate.state = FAIL`、`gate.passed = false`；证据 `E-A08-T3` 判 **FAIL**（`step-aud08-gate-recompute-b43.md:15`）。
- 全文无 “NOT_EVALUABLE 当作未发现问题” 的表述；证据 §6 明确 `UNVERIFIED` 与 FAIL 分离。

**通过**。

### 3.3 禁止 3：门红时不得打印门级 `PASS`

```bash
grep -n "PASS" docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md
# 13:| E-A08-T1 | 金标冻结校验（validate C1–C6+C7） | PASS（子步骤） | §2 |
# 14:| E-A08-T2 | 度量（measure --json） | PASS（子步骤） | §3、§5 |
grep -n "PASS" ... | grep -v "子步骤"
# (none)
```

**通过**：仅 2 处 `PASS`，均带 `（子步骤）`；门级判词为 FAIL。

### 3.4 禁止 4：不得覆盖既有证据/报告/裁定

```bash
python -I tools/split_golden/check_split_gate.py --report-json build/ws8-batch43/gate-report-b43.json
# 切分金标门判定 FAIL：报告文件已存在，不覆盖：build\ws8-batch43\gate-report-b43.json
# exit 2
sha256sum build/ws8-batch43/gate-report-b43.json
# 944d0951b95ac8f2d3103045a787ec1dd3a03cdac497482fcd0970be2042d18d（与覆盖前一致）
git status --porcelain -- docs/research/evidence/2026-11-04-batch-42/ build/split-eval/
# (空)
```

**通过**：覆盖被脚本拒绝（exit 2），既有批次 41/42 产物在树中无改动。

### 3.5 禁止 5：不得用旧导出门值代表新设备/新模型/新 schema

| 事实 | 值 |
|---|---|
| 应用导出 | `build/split-eval/app-split.json`，1482 B，sha `9b49b0c4…`，mtime **2026-10-14 00:02** |
| 导出 `source` 块 | **缺失** → 页名/设备/DB 版本 `UNVERIFIED`（证据 §6） |
| 本轮设备产物 | `build/ws4-batch43/` 只有 APK/安装 sha/崩溃签名，**没有新的 split 导出** |
| 证据范围句 | 已由脚本写入：“只对 金标 sha `f9e92c47…` × 应用导出 sha `9b49b0c4…` 这份样本成立；不等于当前设备库切分准确率”（`step-aud08-gate-recompute-b43.md:53`） |

**通过（但保留下限）**：本轮没有把旧导出门值写成当前设备结论；代价是**当前设备切分准确率
仍 UNVERIFIED**，需要新的设备导出才能改变。

### 3.6 禁止 6：n≥20 金标未裁定硬红线前不得讨论自动化

- 报告 `automation.enabled = false`；脚本固定输出裁定 37 / 4C③。
- 抽样计划 §7 已预置 AND 红线：`gate.passed && hardRedLines.fragmentedQuestions.satisfied`，
  并声明“本计划不自裁”“阈值 0.9/0.05、分母、金标 v1 均不动”。
- 当前金标题数 n=16（`0/16` 切残），尚未到 n≥20；计划要求的裁定在开标前完成。

**通过**。

### 3.7 禁止 7：不得把 G2 的绿写成“人工金标通过”

```bash
grep -n "人工金标通过\|人工金标已完成\|human gold passed\|门已通过\|自动入库已启用" \
  tools/split_golden/GATE.md docs/research/2026-11-04-aud08-human-gold-sampling-plan.md \
  docs/research/2026-11-03-aud08-human-gold-protocol.md \
  docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md
```

命中全部是**禁令原文或否定句**：

- `GATE.md:242` “不得把 G2 的绿写成‘人工金标通过’”；
- `2026-11-03-aud08-human-gold-protocol.md:159` 同类禁令；
- `2026-11-04-aud08-human-gold-sampling-plan.md:15,216` “不代表‘人工金标已完成’”“本文件不构成…或‘门已通过’”；
- 协议 `:3` “不是‘人工金标已完成’的声明”。

另：`2026-11-02-aud08-annotation.md:580` 有“新增用例全绿”一处，主语是**测试用例**，不是门/金标；
不计为违规。

**通过**。

## 4. 发现汇总

| # | 发现 | 严重度 | 处置 |
|---|---|---|---|
| N1 | 泄漏扫描 error 0，3 条既有 warning 未变 | 无 | 只报不改 |
| N2 | 阈值/工具/金标零改动（`git diff` 空、常量逐字） | 无 | — |
| N3 | 门红证据中 `PASS` 仅出现在带 `（子步骤）` 的行 | 无 | — |
| N4 | 当前应用导出仍是 2026-10-14 的 4C 存量样本，无 `source` 块 | 中（覆盖下限） | 不据此谈当前设备；需 WS4 新导出才能升级 |
| N5 | 文本面未发现把 G2 绿写成人工金标的表述 | 无 | — |

## 5. 未验证 / 局限

- 静态扫描不含 Kotlin 评分代码（脚本按 Python AST 设计），也不覆盖动态路径/跨进程参数文件。
- 文本复扫只覆盖 §3.7 命令列出的 AUD-08 结果文件，不覆盖其他 WS 的结果面。
- 本轮**未**取新设备 split 导出，`source` 块缺失导致逐页来源绑定仍 UNVERIFIED。
- clean 不等于正确性证明；3 条 warning 只证明存在阈值覆盖面，不证明被使用。

## 6. 回链

- 禁令清单：`tools/split_golden/GATE.md:234-242`
- 本轮门证据：`docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md`
- 扫描器：`tools/ci/check_eval_label_leakage.py`；自测：`tools/tests/test_check_eval_label_leakage.py`
- 上一轮同项：`docs/research/evidence/2026-11-03-batch-41/step-aud08-prohibition-scan.md`
