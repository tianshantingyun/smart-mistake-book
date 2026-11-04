# WS8 item 60：AUD-08 审计报告（批次 43）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS8-60-AUD08-AUDIT-REPORT-01` |
| batch | 2026-11-05-batch-43 / 计划 §2 WS8 item 60 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86` |
| executed-at | 2026-11-03T21:39Z（本会话 UTC；本机 2026-11-04） |
| verdict | **AUD-08 = FAIL 维持（① 0.5714 < 0.9）**；人工金标 **BLOCKED**；结果面禁令复扫 **0 违规**；发版就绪**不成立** |
| 边界 | 零 git 写、零 Gradle、零设备；只写本 WS 落点（3 份文档 + 脚本产物） |

## 1. 结论（先读）

1. **门值不变**：① 题数完全一致率 **4/7 = 0.5714 < 0.9**；③ 切残率 **0/16 = 0.0000 ≤ 0.05**；
   门判定 **FAIL / exit 1**，门后自动化继续不启用。阈值、口径、分母、金标 v1 零改动。
2. **人工金标仍不存在**：无 ≥2 名独立标注员 + 1 名裁定者，S0–S7 未执行；本轮只交付可执行 runbook，
   不交付任何金标产物。
3. **结果面禁令复扫 0 违规**：泄漏扫描 error 0 / warning 3（既有）；七条禁令逐条通过。
4. **门值覆盖范围不变**：仍只对「金标 `f9e92c47…` × 4C 存量导出 `9b49b0c4…`」成立，
   **不等于当前设备库准确率**；本轮没有新的设备 split 导出。
5. **发版就绪不成立**：与本 WS 无关的三项外部缺口（生产签名、arm64、干净树）没有变化；
   AUD-08 的 FAIL 是其中数据质量项，不因本批有任何放松。

## 2. 逐项对账（计划 §2 WS8 item 57–60）

| # | 项 | 状态 | 证据 |
|---:|---|---|---|
| 57 | 人工金标抽样执行计划（BLOCKED 则出 runbook） | **BLOCKED + runbook 已落盘**；抽样复算 24/24 sha 一致、dev16/acc8 | `ws8-aud08-human-gold-runbook.md` |
| 58 | 门值复算（FAIL 维持） | **完成：FAIL / exit 1**；裁定 `validate_rulings.py` PASS | `step-aud08-gate-recompute-b43.md`、`rulings/20261103T213902Z-c0dc8d30.json` |
| 59 | 结果面向禁令复扫 | **完成：0 error；3 warning 既有未变**；七条禁令逐条通过 | `ws8-aud08-prohibition-rescan.md` |
| 60 | 审计报告 | **完成（本文件）** | 本文 |

## 3. 门值复算：输入、输出与可复现性

### 3.1 输入身份

| 输入 | sha256 | 事实 |
|---|---|---|
| 金标 `split_golden_v1.json` | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` | 7 页 / 16 框；C1–C7 PASS（子步骤）；证据等级 G2 |
| 应用导出 `app-split.json` | `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6` | 1482 B；mtime **2026-10-14 00:02**；`source` 块缺失 |

### 3.2 门值

- ① `4/7 = 0.5714285714285714`，门 `>= 0.9` → **未过**；
- ③ `0/16 = 0.0000`，门 `<= 0.05` → 过；
- `hardRedLines.fragmentedQuestions = {value: 0, required: 0, satisfied: true}`（硬红线满足）；
- `automation.enabled = false`；
- 判定 **FAIL**（闭边界；`check_split_gate.py:310-324`）。

### 3.3 可复现性（本轮实测）

- 新报告 `build/ws8-batch43/gate-report-b43.json` 的 sha256 为
  `944d0951b95ac8f2d3103045a787ec1dd3a03cdac497482fcd0970be2042d18d`；
- 与批次 41 报告 `build/split-eval/gate-report-batch41.json`、批次 42 报告
  `build/ws9-batch42/gate-report-batch42.json` **逐字节相同**（`diff -q` 输出 `IDENTICAL`；
  三份 sha 同为 `944d0951…`）。报告不含时间戳，输入相同即字节相同；
- 证据 md（含生成时刻）与裁定文件含运行身份，**不能**用 sha 相同的标准要求；裁定 `rulingsSha256`
  本轮为 `7359ac1b3f2d2572…`，与批次 42 的 `2180f0dd…` 不同——差异只在每页
  `ruling.decidedAtUtc`（`decidedBy` 两轮同为 `script:check_split_gate.py@sha256:c8a44c28…`）。
  **不得把两轮裁定当同一 run**（同型口径见
  `docs/research/evidence/2026-11-03-batch-41/step-aud08-prohibition-scan.md:63`）。

### 3.4 裁定校验

```bash
python -I tools/split_golden/validate_rulings.py \
  --file docs/research/evidence/2026-11-05-batch-43/rulings/20261103T213902Z-c0dc8d30.json
# 裁定校验 PASS：...
# 页数 7；判词 consistent 4 / fragmented 0 / inconsistentCount 3 / missingInApp 0 / notEvaluable 0
# 门状态 FAIL；rulingsSha256=7359ac1b3f2d2572…
# exit 0
```

## 4. 发现

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| F1 | 门值仍是 4/7 = 0.5714，三页多题页应用 0 框是唯一来源；切残 0/16 | 本轮报告 §4；`GATE.md:181-211` 逐页与读法 | 高（数据质量） | 维持 FAIL；不得改阈值 |
| F2 | 应用导出是 2026-10-14 的 4C 存量，无 `source` 块；本轮设备产物 `build/ws4-batch43/` 无新 split 导出 | `ls -l build/split-eval/app-split.json`；`ls build/ws4-batch43/`；证据 §6 | 中（覆盖下限） | 当前设备切分准确率 **UNVERIFIED**；需 WS4 新导出后另跑 |
| F3 | 人工金标 S0–S7 未执行，v2/C1–C7/负控不存在 | 抽样计划 §6/§9.2；本轮 runbook §4 | 高（外部阻塞） | BLOCKED；runbook 已就绪 |
| F4 | 结果面禁令 0 违规，但静态扫描不覆盖 Kotlin/动态路径 | `check_eval_label_leakage.py` 输出；rescan §2/§5 | 低（局限） | clean ≠ 正确性证明，登记 |
| F5 | 3 条 warning 仍是 `retrieval_quality_gate.py:155-157` 的阈值 CLI 覆盖面 | 扫描器输出 | 低（既有） | 只报不改，与批次 41 一致 |
| F6 | 报告 JSON 三批逐字节相同，可作为“输入未漂移”的硬证据 | `diff -q` = IDENTICAL；三份 sha `944d0951…` | 无（正面） | 后续复算可继续用字节比对 |

## 5. 口径与边界（不得外推）

- 本门值只对「金标 `f9e92c47…`（7 页/16 题，G2）× 应用导出 `9b49b0c4…`」成立；
  7 页不是 155 页总体的概率样本，24→7 入集准则未落产物，训练/开发/验收切分未做。
- G2 是**代理金标**（模型标注、无人工专家），`recomputable=false`；真实人工正确率 **UNVERIFIED**。
- Wilson 95% 区间（① 0.2505–0.8418）是描述性统计，**不是门**，不得写成总体准确率。
- 门后自动化：`gate.passed && hardRedLines.fragmentedQuestions.satisfied` 且 4C③ 产品裁定；
  当前 `gate.passed=false`，自动化关闭。

## 6. 未验证 / 阻塞

| 项 | 状态 | 原因/解除 |
|---|---|---|
| 人工金标正确率、一致性、裁定质量 | **UNVERIFIED** | 无标注员；runbook §4 B1 |
| 当前设备库切分准确率 | **UNVERIFIED** | 无新设备 split 导出；旧导出无 `source` 块 |
| v2 金标、C1–C7、负控 | **未生成** | 同上 |
| 门后自动化 | **BLOCKED** | 需 4C③ 产品裁定 + 红线段裁定 |
| Kotlin 评分代码的标签泄漏 | **未扫** | 扫描器按 Python AST 设计，不在本项范围 |
| 发版就绪 | **不成立** | 生产签名/arm64/干净树三项外部缺口（非本 WS） |

## 7. 证据清单（本 WS 产物）

| 产物 | 路径 | 行/字节 | sha256 |
|---|---|---|---|
| 门证据（脚本生成） | `docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md` | 4303 B（脚本体积自查 57 行 / 4233 B） | `e74c4d4a727c640dbf66c61905ae004a7f9ac3bd219ed37c7879b3afd78ee46f` |
| 门报告 | `build/ws8-batch43/gate-report-b43.json` | — | `944d0951b95ac8f2d3103045a787ec1dd3a03cdac497482fcd0970be2042d18d` |
| 逐页裁定 | `docs/research/evidence/2026-11-05-batch-43/rulings/20261103T213902Z-c0dc8d30.json` | — | `9f3a1256b4d06c4fe3fd00d3d7137dc72e835bf43670608736651b3b9e4c14ff` |
| 人工金标 runbook | `docs/research/evidence/2026-11-05-batch-43/ws8-aud08-human-gold-runbook.md` | 153 行 / 8096 B | `e0c1f9d725221e108efa82e7dc162e364fc370cf85e416e140477d22b654cad0` |
| 禁令复扫 | `docs/research/evidence/2026-11-05-batch-43/ws8-aud08-prohibition-rescan.md` | 160 行 / 8589 B | `29939352f69e23c54176b46cdc3fc33c1d1e4128ba3b6334197aba82ff413aa4` |
| 审计报告 | 本文件 | — | — |

`build/` 为 gitignored（门报告与泄漏扫描 JSON 不随提交）；证据 md/runbook/rescan/裁定入 `docs/research/evidence/`。

## 8. 本会话实跑命令（可重跑）

```bash
git rev-parse HEAD
python -I -c "...155 张抽样框 + draw 24..."
python -I -c "...24 页 sha256 与抽样计划逐项比对..."
python -I tools/split_golden/check_split_gate.py \
  --report-json build/ws8-batch43/gate-report-b43.json \
  --evidence-out docs/research/evidence/2026-11-05-batch-43/step-aud08-gate-recompute-b43.md \
  --rulings-out docs/research/evidence/2026-11-05-batch-43/rulings
python -I tools/split_golden/validate_rulings.py \
  --file docs/research/evidence/2026-11-05-batch-43/rulings/20261103T213902Z-c0dc8d30.json
python -I tools/ci/check_eval_label_leakage.py
python -I -m unittest discover -s tools/tests -t tools -k eval_label_leakage
git diff --stat -- tools/split_golden/ tools/ci/check_eval_label_leakage.py \
  tools/tests/test_check_eval_label_leakage.py
```

未跑：Gradle、设备、任何标注。

## 9. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:96-100`（WS8 57–60）
- 基线：`docs/research/2026-11-05-batch-43-baseline.md:426`（AUD-08 BLOCKED）
- 计划收尾：`docs/research/2026-11-05-plan-closure.md:301`
- 批次 42 裁定：`docs/research/2026-11-04-batch-42-record.md:545`（FAIL 维持）
- 门规范：`tools/split_golden/GATE.md` §0/§2.1/§3.1/§4/§5.1
- 抽样计划：`docs/research/2026-11-04-aud08-human-gold-sampling-plan.md`
- 协议：`docs/research/2026-11-03-aud08-human-gold-protocol.md`
