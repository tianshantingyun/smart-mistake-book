# WS8 AUD-08 证据：结果面向禁令自查 + 逐页裁定统计（批次 41）

- 日期：2026-11-03（本会话实跑时刻 2026-11-02T14:33Z 起的 UTC 记录以产物为准）
- 范围：计划 `.zcode/plans/plan-2026-11-03-batch-41.md` §2 WS8 item 48–51。
- 边界：只改 `tools/` 与 `docs/`；无 git 写、无 Gradle、无设备操作、无密钥输出。
- 相关产物：
  - 门判定证据（脚本生成）：`step-aud08-gate-recompute.md`
  - 门报告：`build/split-eval/gate-report-batch41.json`
  - 逐页裁定：`rulings/20261102T143319Z-4c83a01e.json`
  - 人工金标协议：`docs/research/2026-11-03-aud08-human-gold-protocol.md`
  - 禁令自查脚本：`tools/ci/check_eval_label_leakage.py`
  - 禁令自查测试：`tools/tests/test_check_eval_label_leakage.py`

## 1. E-ID 表

| E-ID | 步骤 | 判定 | 证据 |
|---|---|---|---|
| E-A08-41-T1 | 门值复算（冻结校验 → 度量 → 0.9/0.05） | **FAIL（exit 1）** | §2 |
| E-A08-41-T2 | 逐页裁定生成 + R1–R9 校验 | PASS | §2.3 |
| E-A08-41-T3 | 人工金标协议落盘 | 已落盘（协议，未执行） | §3 |
| E-A08-41-T4 | 禁令自查脚本 + 自测 | PASS（11 tests OK；仓库扫描 error 0 / warning 3） | §4 |
| E-A08-41-T5 | 逐页裁定统计与 GATE.md 更新 | 已更新 | §5 |

## 2. 门值复算（item 48）

命令与退出码（仓库根，本会话实跑）：

```bash
python -I tools/split_golden/check_split_gate.py
# 切分金标门判定（AUD-08）：FAIL（exit 1）
# ① countExactRate = 4/7 = 0.5714（门 0.5714 >= 0.9 → 未过）
# ③ fragmentRate = 0/16 = 0.0000（门 0.0000 <= 0.05 → 过）
```

带落盘参数（本次证据来源）：

```bash
python -I tools/split_golden/check_split_gate.py \
  --report-json build/split-eval/gate-report-batch41.json \
  --evidence-out docs/research/evidence/2026-11-03-batch-41/step-aud08-gate-recompute.md \
  --rulings-out docs/research/evidence/2026-11-03-batch-41/rulings
# exit 1；已落盘 report/evidence/rulings 三件
```

输入身份（报告实读）：

| 输入 | sha256 | 说明 |
|---|---|---|
| 金标 `split_golden_v1.json` | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` | 7 页 / 16 框；C1–C7 PASS；等级 G2（冻结常量映射，不可复算） |
| 应用导出 `app-split.json` | `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6` | 1,482 B；4C 存量；`source` 块缺失 → 页名/设备来源 UNVERIFIED |

**阈值零改动**：`COUNT_GATE = 0.9`、`FRAGMENT_GATE = 0.05` 与 `thresholds` 报告块逐字未变；
未改口径/分母/金标。门后自动化 `enabled=false`。

### 2.1 与批次 40 裁决数据的一致性

批次 41 裁定 `20261102T143319Z-4c83a01e.json` 与批次 40 同输入裁定
`20261101T234146Z-2318b8db.json` 比较（本会话脚本比对）：

- `rulings[]` 内容级**一致**：判词、`gold/app.boxesSha256`、`diff` 全部数值相同；
- 差异仅 `ruling.decidedAtUtc` 与 `decidedBy.agentId` 内嵌的工具 sha
  （批次 40 为 `c46caeaa…`，当前为 `c8a44c28…`）——属运行身份，不是内容；
- 因此 `rulingsSha256` 不同（`1d4f9bfe…` vs `97327f3a…`），**不得**把两者当同一 run。

### 2.2 三份裁定汇总（现有裁决数据）

| runId | 应用导出 sha | 输入 | 判词（7 页） |
|---|---|---|---|
| `20261101T234146Z-2318b8db` | `9b49b0c4…` | 4C 存量导出 | CONSISTENT 4 / INCONSISTENT_COUNT 3 |
| `20261101T234207Z-76aba6d0` | `fa0ba8ae…` | emulator-5562，schema 72，split 表 0 行 | MISSING_IN_APP 7 |
| `20261102T143319Z-4c83a01e` | `9b49b0c4…` | 同基线，当前工具 | CONSISTENT 4 / INCONSISTENT_COUNT 3 |

### 2.3 裁定校验

```bash
python -I tools/split_golden/validate_rulings.py \
  --file docs/research/evidence/2026-11-03-batch-41/rulings/20261102T143319Z-4c83a01e.json
# 裁定校验 PASS：…；页数 7；门状态 FAIL；rulingsSha256=97327f3a40a3fb71…
# exit 0
```

## 3. 人工金标协议（item 49）

落盘文件：`docs/research/2026-11-03-aud08-human-gold-protocol.md`。

覆盖点（逐条可 grep）：

- **标注等级字段**：G0–G5 的根级必填登记块 + C7 入口行号（`validate_split_golden.py:395-426`）；
  字段表区分"C7 已机检"与"协议附加、尚未机检"。
- **模型标注显式**：`labelers[].kind="model"` + 五元组；模型不得写成 `kind="human"`；
  G2 独立性按 `(modelId,modelVersion,promptSha256)` 不同判定（`:407-413`）。
- **双人复核流程**：S0 指南冻结 → S1 抽样/切分 → S2 独立标注 → S3 一致性报告 →
  S4 分歧清单 → S5 第三人裁定 → S6 冻结 v2 → S7 验收集单次运行；含降级触发器。
- **未执行声明**：无标注员参与；人工金标正确率、G3/G4/G5 产物均 **UNVERIFIED**；
  `heldOutUnusedForTuning` 是自证字段、不可机检。

## 4. 禁令自查脚本（item 50）

落盘文件：`tools/ci/check_eval_label_leakage.py`；测试：
`tools/tests/test_check_eval_label_leakage.py`。

### 4.1 它消灭的具体失败

禁令（spec `:165`；`GATE.md` §0/§5.7）此前**没有机器检查**：

1. 阈值由金标度量值算出 → 与"立项自定 + 出处"的常量在 diff 里无法区分；
2. 金标派生值写进参数文件，调参发生在两次运行之间；
3. 读 protected 标签的门同时开放阈值 CLI/env 覆盖，事后改阈值不留痕。

### 4.2 检查项与严重度

| id | 严重度 | 判据 |
|---|---|---|
| `LABEL_DERIVED_THRESHOLD` | error | 阈值/门限命名的变量或阈值字典项由 protected 标签数据派生（同文件内 AST 污点） |
| `LABEL_DERIVED_PARAM_OUTPUT` | error | 把 protected 标签派生值写进 threshold/param/tuning/budget/tolerance/calib/config 文件 |
| `THRESHOLD_OVERRIDE_SURFACE` | warning | 读 protected 标签的脚本开放阈值 CLI/env 覆盖（调参面，不等于已调参） |
| `SCAN_UNPARSABLE` | error | 候选脚本解析失败——无法检查不得当作干净 |

protected 集 = 路径含 `golden/blind/acceptance/held-out/label/annotation/eval-set/judge`；
`dev/train/tuning` 是可调参集，不算 protected。

### 4.3 先红后绿（本会话实跑）

红（脚本未实现时）：

```bash
python -I -m unittest discover -s tools/tests -t tools -k eval_label_leakage
# ImportError: cannot import name 'check_eval_label_leakage' from 'ci'
# FAILED (errors=1)；exit 1
```

绿（实现后）：

```bash
python -I -m unittest discover -s tools/tests -t tools -k eval_label_leakage
# Ran 11 tests in 0.211s；OK；exit 0
```

测试覆盖：硬编码阈值 + 读金标 → 干净；阈值由金标派生 → error；金标派生写参数文件 → error；
阈值 CLI 覆盖 → warning 且 exit 0；dev 集调参 → 不报；正则常量里的 `golden` → 不计为标签读者；
裸文件名常量（`tasks_blind_120.json`）→ 计为标签读者；扫描前后字节不变（只报不改）；
有 error 时 exit 1；同输入两次报告 JSON 逐字节一致；真实仓库扫描可运行。

### 4.4 仓库扫描结果（本会话实跑）

```bash
python -I tools/ci/check_eval_label_leakage.py
# 扫描 24 个候选脚本；protected 标签读者 8 个
# [warning] THRESHOLD_OVERRIDE_SURFACE retrieval_quality_gate.py:155 — '--recall5-threshold'
# [warning] THRESHOLD_OVERRIDE_SURFACE retrieval_quality_gate.py:156 — '--mrr-threshold'
# [warning] THRESHOLD_OVERRIDE_SURFACE retrieval_quality_gate.py:157 — '--ndcg5-threshold'
# error 0 / warning 3 → exit 0
```

protected 标签读者（报告实读 8 个）：`dense_build/export_golden_query_vectors.py`、
`figure_eval/figure_eval_gate.py`、`kb_coverage/apply_rejudged_materials.py`、
`kb_coverage/make_golden_slices.py`、`kb_coverage/validate_golden.py`、
`retrieval_quality_gate.py`、`split_golden/measure_split_accuracy.py`、
`split_golden/validate_split_golden.py`。扫描器自身不计入（正则常量里的 `golden`
不是文件路径；`_looks_like_path` 已把它排除——否则 protected 读者计数被自我污染）。

解读：

- **0 个 `LABEL_DERIVED_THRESHOLD` / `LABEL_DERIVED_PARAM_OUTPUT`**：在静态可见范围内，
  没有评分/门脚本把 protected 标签数据用于阈值或参数文件；
- 3 条 warning 是 `tools/retrieval_quality_gate.py:155-157` 的阈值 CLI 覆盖面
  （默认值硬编码、允许调用方传入）。它是**调参面**，不是已调参证据——按"只报不改"不修；
- `figure_eval_gate.py` 的 `--enforce-quality-gates` 是布尔开关，已从阈值覆盖面词表中排除
  （按 `-`/`_` 分词后整词匹配，避免 "gates" 假阳性）；
- 脚本**不修改任何被扫文件**（测试断言扫描前后字节一致）；不自动修复、不切产品开关。

### 4.5 UNVERIFIED / 局限（脚本随报告输出）

- 静态 AST 看不到动态拼接路径、跨仓库/跨进程生成的参数文件、git 历史里的改值；
  clean ≠ 正确性证明。
- `THRESHOLD_OVERRIDE_SURFACE` 只证明存在覆盖面，不证明被使用。
- 不检查数据集划分本身是否存在——v1 未做 train/dev/acceptance（`GATE.md` §2）。

### 4.6 开源实现评估（GitHub 优先）

本会话用 `gh api repos/semgrep/semgrep` 实读元数据（2026-11-03）：

| 项 | 值 |
|---|---|
| 项目 | `semgrep/semgrep` |
| license | **LGPL-2.1** |
| stars | 17,408 |
| pushed | 2026-11-02T00:03:04Z（仍在维护，未 archived） |
| 本地可用性 | `semgrep --version` → **command not found**（本环境未安装） |

结论：semgrep 的 Python taint 模式可以表达"标签 → 阈值/参数"的跨文件污点规则，是比自研
AST 扫描更成熟的候选；本批**未采用**，理由是三条工程约束同时成立：

1. 环境与 CI 都没有 semgrep；引入它要新增一个运行时依赖与 CI 安装步骤（含 LGPL 合规复核），
   超出本项"只报不改"的改动面；
2. 规则仍是项目特定的（protected 标签词表、阈值/参数 sink、dev 集例外），semgrep 不能零配置复用；
3. 现有门工具链是 stdlib-only、离线可跑（`tools/ci/check_dependency_pins.py` 等同风格），
   自研脚本保持零依赖，可直接进现有 discover。

因此本批保留 stdlib 扫描器，并把"接 semgrep 做跨文件污点"登记为后续升级方向（不是本批交付）。
license/维护度/版本兼容已核；未做 semgrep 规则原型（**UNVERIFIED**：未验证该规则集在真实仓库的
误报率）。

## 5. 逐页裁定统计与文档更新（item 51）

- `tools/split_golden/GATE.md` 新增：
  - §0 "2026-11-03 批次 41 复算（阈值零改动）"段（FAIL、输入 sha、三件产物、与批次 40 一致性）；
  - §2.1 人工金标协议链接（协议落盘、无标注员执行）；
  - §3.1 "逐页裁定统计（2026-11-03 批次 41）"：三份裁定汇总表 + 批次 41 逐页表 + 读法口径
    （单题页 0 框记一致、题数一致 ≠ 边界合格、0 页导出 ≠ 设备能力、7 页不可外推）。
- 逐页数字与裁定文件逐项一致（本会话从 `rulings.json` 读取，不是转抄）。

## 6. 未做 / UNVERIFIED

- **无 Gradle**：本 WS 不触发任何 Gradle 编译/单测门；只跑了 `tools/tests` 的 Python 自测
  （split_gate 61 例 + eval_label_leakage 11 例）。
- **无设备**：设备导出的新快照未取；`fa0ba8ae…` 沿用批次 40 产物。
- **人工金标未执行**：协议只是规则；无标注员、无 G3/G4/G5 产物。
- **真实模型/Provider 质量**：不在本 WS 范围，未验证。
- **禁令扫描**：只覆盖 `tools/` 下文件名匹配的 24 个脚本；app/core/feature 的 Kotlin
  评分代码未扫（脚本按 Python AST 设计，Kotlin 不在本项范围）。
