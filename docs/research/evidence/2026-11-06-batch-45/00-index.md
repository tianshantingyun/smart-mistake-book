# 批次 45 · D0 自验收索引（WS2 13–24）

> **本轮实跑**：`python -I tools/ci/d0_self_acceptance.py --group <group>`（15 次，
> 每次末行 `EXIT=0`，合计 205 条 CHECK）、各组 `python -I -m pytest … --junitxml`
> （17 份 XML，全 `failures=0 errors=0 skipped=0`）、`acceptance_hash_manifest.py`
> 生成/校验、`acceptance_init.py --check`、`acceptance_report.py`、
> `acceptance_lint.py`、`trace_requirements.py`。
>
> **本轮未跑**：任何 Gradle、任何 adb/设备、真实 Provider、Play Console。截图采集与
> 语义树导出的**执行**证据来自批次 44 已归档设备产物（本文件只复算其侧车/解析）；
> D0-050 的侧车字段与 D0-057 的离线解析因此可判，真机重跑仍属设备轨。
>
> **边界**：本文件是 D0 100 行的**机械自验收**，不是独立复核。
> `acceptance_report` 的 `pass_with_review` 仍为 **0**；D0 PASS 不满足 DoD §1.5 的
> “reviewer ∉ authors”，不得据此签总体通过。

## 1. 十组工具自验收（工具 → 命令 → exit → 证据 → verdict）

| # | 组 | 工具 | 自测命令（逐字） | exit | 证据（仓库相对） | verdict |
|---:|---|---|---|---:|---|---|
| 1 | model | `acceptance_model.py` | `python -I tools/ci/d0_self_acceptance.py --group model` | 0 | `d0-self/model/model.txt` + `model-pytest.xml`（13 例） | PASS |
| 2 | init | `acceptance_init.py` | `python -I tools/ci/d0_self_acceptance.py --group init` | 0 | `d0-self/init/init.txt` + `init-pytest.xml`（6 例） | PASS |
| 3 | report | `acceptance_report.py` | `python -I tools/ci/d0_self_acceptance.py --group report` | 0 | `d0-self/report/report.txt` + `report-pytest.xml`（21 例） | PASS |
| 4 | lint | `acceptance_lint.py` | `python -I tools/ci/d0_self_acceptance.py --group lint` | 0 | `d0-self/lint/lint.txt` + `lint-pytest.xml`（8 例） | PASS |
| 5 | trace | `trace_requirements.py` | `python -I tools/ci/d0_self_acceptance.py --group trace` | 0 | `d0-self/trace/trace.txt` + `trace-pytest.xml`（11 例） | PASS |
| 6 | hash | `acceptance_hash_manifest.py` | `python -I tools/ci/d0_self_acceptance.py --group hash` | 0 | `hash/hash.txt` + `hash-pytest.xml`（4 例） | PASS |
| 7 | metamorphic | `metamorphic_checks.py` | `python -I tools/ci/d0_self_acceptance.py --group metamorphic` | 0 | `d0-self/metamorphic/metamorphic.txt` + `…-pytest.xml`（4 例） | PASS |
| 8 | screenshot | `screenshot_baseline.py` | `python -I tools/ci/d0_self_acceptance.py --group screenshot` | 0 | `d0-self/screenshot/screenshot.txt` + `…-pytest.xml`（5 例） | PASS |
| 9 | a11y | `a11y_audit.py` | `python -I tools/ci/d0_self_acceptance.py --group a11y` | 0 | `d0-self/a11y/a11y.txt` + `a11y-pytest.xml`（11 例） | PASS |
| 10 | h_compliance | `h_compliance_gate.py` | `python -I tools/ci/d0_self_acceptance.py --group h_compliance` | 0 | `d0-self/h_compliance/h_compliance.txt` + `…-pytest.xml`（90 例） | PASS |

> 证据根：`docs/research/evidence/2026-11-06-batch-45/`。10 组的 cmd 证据均为驱动
> 的 `CHECK … EXPECTED=… ACTUAL=… OK` 逐行记录 + 末行 `EXIT=0`；test 证据均为
> pytest JUnit XML。`h_compliance` 无 D0 行，它是 H 域门，仅为 WS2 的十组之一。

## 2. 每组做了什么（自测 / 假绿回放 / 自突变）

| 组 | 正向 | 自突变（必须翻红） |
|---|---|---|
| model | 真台账 1200 行、ID/域计数/列数/verdict/证据 token | 坏 verdict、重号、5 列、域不匹配、坏 kind、空路径 |
| init | 生成 1200 行、二次写字节相同、已裁定行不被覆盖、漂移检测 | 删行 → `MISSING_ROW`；改 verdict → `BAD_VERDICT` |
| report | 双类证据 exit 0、`ledger_sha256` 绑定、计数稳定 | 单类证据、缺路径、占位 CMD、exit≠0、XML 失败、summary、自证复核、缺字段、设备零评估、`--require-complete`、完整 1200 行口径的缺行/重号/坏 verdict |
| lint | 真台账 0 违规 | summary、跳过轮、exit≠0、自证、空证据、重号、`UNVERIFIED` 标 PASS、复用 >10 行 strict 升违规；批次 41/42 假绿回放均 exit 1 |
| trace | 真台账 1200 行、D0 100 行全部可定位、干净夹具 0 gap、JSON 稳定 | 缺测试路径、占位、无 test/device 落点、设备方法缺设备证据、PASS 证据路径缺失 |
| hash | 生成/校验、自身 sha256（当前 `0c7c189c…e372c`）、POSIX 路径、不含秘密 | 改字节 → `HASH_MISMATCH`；删文件 → `MISSING_FILE`；加文件 → `UNLISTED_FILE` |
| metamorphic | `--demo` 12 条关系全成立 | 破坏 `append_item` 前缀 → 反例 + reason；缺夹具/未知关系 → exit 2 |
| screenshot | 同图 0、变化 1、阈值必填、报告含双哈希、MAE/比例、RGB8 解码、尺寸不一致 | 超阈值、缺阈值参数、尺寸不一致；采集侧车读批次 44 归档并复算 PNG 哈希 |
| a11y | 干净树 PASS、节点/可点击计数、dump 哈希、缺颜色整体不判 PASS | 20px 触摸目标、无标签、低对比度、坏 dump exit 2；批次 44 语义树离线解析 13 节点 |
| h_compliance | `--all` 90 条、H-018/H-061/H-062 FAIL、H-052/H-066 PASS、单条复跑一致 | 单条 FAIL 仍 exit 1（`--only H-018`）；断言与 `--all` 判词不漂移 |

## 3. D0 100 行回填结果

| verdict | 行数 | 行号 |
|---|---:|---|
| PASS | 93 | 001–029、036–080、082–100（除下列 7 行） |
| FAIL | 2 | **D0-030**（668+ 个 PASS 对应 0 个终裁 JSON）、**D0-081**（协议未写“冲突时保留双方判词”） |
| UNVERIFIED | 5 | D0-031、032、033、034（无终裁 JSON 实例可查）；D0-035（无 verdict schema 文件） |

- D0-030 的观察值：`OBSERVED real_verdict_json=0`（`d0-self/docs/verdicts.txt`）。
- D0-035 的观察值：`OBSERVED verdict_schema_files=0`（同上）。
- D0-081 的观察值：`OBSERVED conflict_rule_present=False`（`d0-self/docs/protocol.txt`）。
- 这 7 行**没有**被写成 PASS；它们正是本批“验收不通过”的 D0 侧证据。

文档/协议/回放家族（D0-008–014、029–035、064–070、071–076、077–088、095–100）
同样有驱动日志 + pytest XML 两类证据；命令为
`python -I tools/ci/d0_self_acceptance.py --group docs --section <dod|verdicts|patch|runbook|protocol|forgery|replay>`，
断言在 `tools/tests/test_d0_docs.py`（38 例，全绿）。

## 4. 门禁实值（本轮实跑，HEAD `87604198`）

| 门 | 命令 | 结果 |
|---|---|---|
| 台账结构 | `python -I tools/ci/acceptance_init.py --check` | **exit 0** |
| 报告 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **exit 1**；`counts={PASS:865, FAIL:30, BLOCKED:161, UNVERIFIED:144}`；`violations=0`；`state=FAIL` |
| lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **exit 0**；`violations=0`、`warnings=140`（`EVIDENCE_REUSED` 阈值从 `>10` 收紧为 `>=5`；新增 `reuse` 全量计数） |
| trace | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **exit 0**；`gaps=0`（新增 `BLOCKED_WITHOUT_RUNBOOK` 检查，13 行补齐 runbook 后才归零） |
| 证据哈希 | `acceptance_hash_manifest.py --root …/d0-self --verify …/hash/MANIFEST.sha256` | **exit 0**（48 文件） |

> **计数口径**：上表是修复环（台账 `887e3ba6…`）后的**最终值**。本文件 §1–§3 的
> D0 自验收内容仍是当时运行；旧值 `761/23/73/343`、`gaps=60`、`warnings=29`
> 是过程快照，不得再引。最终域计数以 `docs/acceptance/domain-index.md` 为准。

- report 的 exit 1 来自 **30 条 FAIL 行**，**不是** D0 证据违规：D0 的 93 条 PASS
  全部通过 `PASS_EMPTY_EVIDENCE` / `PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE` /
  `PLACEHOLDER_CMD` / `CMD_EXIT_*` 等规则。
- trace 的 60 gap 已在修复环归零；新增的 `BLOCKED_WITHOUT_RUNBOOK` 检查证明
  旧 13 行“无出口阻塞”确实存在，补齐后才有 0 gap。
- `pass_with_review=0`：独立复核（WS5）只有域级抽样，**没有行级 `review:` token**，
  DoD §1.5 仍不满足。

## 5. 结论（D0 口径）

1. **十组工具自验收成立**：205 条 CHECK 全 OK、17 份 pytest XML 全绿、假绿回放
   仍红、每组的自突变都翻红、哈希清单 verify 通过。
2. **D0 100 行不再结构性未判定**：93 PASS / 2 FAIL / 5 UNVERIFIED，无空 verdict。
3. **D0 不能单独签字**：`pass_with_review=0`，且 D0-030/D0-081 是真失败；
   `acceptance_report` 仍 exit 1。整体验收结论不由本文件给出。
4. 证据落地：`docs/research/evidence/2026-11-06-batch-45/d0-self/`（48 文件，已被
   `hash/MANIFEST.sha256` 覆盖）；验证命令见 §4。
