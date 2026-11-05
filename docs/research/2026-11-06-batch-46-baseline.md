# 批次 46 基线（2026-11-06 行级复核批）

> 交付物：本文件是批次 46 的**只读基线**。未改台账、未改源码/热区、未引依赖、未跑 Gradle。
> 入口：批次 45 终裁 NOT ACCEPTED（`a7cecc63`，`docs/research/2026-11-06-batch-45-record.md` §9）。
> 计划：`.zcode/plans/plan-2026-11-06-batch-46-line-review.md`（§1 WS1–WS5、§2 硬约束、§3 验收）。

## 0. 一句话结论

台账 `887e3ba6…` 的 1200 行已冻结且结构合规（`violations=0`，`trace gaps=[]`），但 **`pass_with_review = 0 / 865`，逐行终裁 JSON 一个都不存在，10 份域级复核 JSON 全部绑在旧台账 `bb25ef23…` 上**；`--require-complete` 当前 exit 1，两条阻断分别是 `NOT_ACCEPTANCE_COMPLETE（UNVERIFIED=144 FAIL=30 BLOCKED=161）` 与 `PASS_WITHOUT_REVIEW（865）`。本批 96 项里 WS1 的 10 个域名任务只覆盖 A–J 的 668 行，**另有 D0/FIX/GATE 共 197 行 PASS 无复核责任人指派**（§8.4）。

## 1. 快照

| 项 | 值 | 来源 |
|---|---|---|
| HEAD | `a7cecc634f89cab5a085ddd7ee52d4481e0458dd`（2026-11-05T15:22:16+08:00，`docs: 批次45 主会话收口…`） | `git rev-parse HEAD`、`git log -1 --format=%H%n%ad%n%s --date=iso-strict` |
| 台账 | `docs/acceptance/ledger.md`，sha256 `887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa`，1303 行 / 512,416 B，**1200 数据行** | `sha256sum docs/acceptance/ledger.md`；`wc -lc` |
| 台账 ID 完整性 | `identity_issues=[]`（13 个域前缀 D0/A–J/FIX/GATE 与各域计数全对，无缺行/多行） | `acceptance_model.parse_ledger` 复算（§10 探针 1；`tools/ci/acceptance_model.py:39-56`） |
| 工作树 | `dirty_paths(uall) = 3758`（报告自报）；`docs/acceptance/` **0 dirty**；`docs/research/evidence/` **4 dirty**（§6.3） | `acceptance_report` 输出、`git status --porcelain -uall` |
| 设备 | `emulator-5562  device`、`emulator-5584  device`（2 台在线） | `C:/Android/Sdk/platform-tools/adb.exe devices` |
| adb 路径 | `adb` **不在 PATH**；实际二进制 `C:\Android\Sdk\platform-tools\adb.exe`（`ANDROID_HOME=C:\Android\Sdk`） | `which adb` 无输出；`ls $ANDROID_HOME/platform-tools/adb.exe` |
| Gradle | **本基线未跑**（本 ask 禁 Gradle；门禁阶段串行另跑） | 不适用 → UNVERIFIED |

已知差异（未核实，只登记）：批次 45 记录 §7.1 写 `-uall=4091`，本次报告自报 `3758`，差 333。批次 45 记录形成于收口提交之前，工作树构成已变，差异来源**未核实**，不作为任何结论依据。另外 10 份域级复核 JSON 的 `head` 字段多为 `87604198…`（F/J 两份为 null），与当前 HEAD 不同——这也是它们只能当「输入」不能当证据的原因之一。

## 2. 计数（复算，非引用）

命令：

```bash
python -I tools/ci/acceptance_report.py
```

输出（关键行，exit=1）：

```text
ledger_sha256: 887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa
head: a7cecc634f89cab5a085ddd7ee52d4481e0458dd   dirty_paths: 3758
total: 1200   counts: PASS 865 / FAIL 30 / BLOCKED 161 / UNVERIFIED 144
state: FAIL   pass_with_two_kinds: 865
pass_with_review: 0   pass_without_review: 865   violations: []   warnings: []
```

分域复算（探针 1，与已入库 `docs/acceptance/domain-index.md` 表逐格一致）：

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED |
|---|---:|---:|---:|---:|---:|
| D0 验收基础设施 | 100 | 93 | 2 | 0 | 5 |
| A 功能旅程 | 90 | 65 | 0 | 1 | 24 |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 |
| F 性能与稳定 | 90 | 55 | 2 | 10 | 23 |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 |
| FIX 修复与加固 | 100 | 15 | 0 | 85 | 0 |
| GATE 门禁与终裁 | 100 | 89 | 7 | 3 | 1 |
| **合计** | **1200** | **865** | **30** | **161** | **144** |

**十域（A–J）PASS 合计 = 668 行**；非十域 PASS = D0 93 + FIX 15 + GATE 89 = **197 行**。

## 3. ID 列表（逐行，供 WS1–WS4 派活）

### 3.1 FAIL（30）

| 域 | id |
|---|---|
| D0（2） | D0-030, D0-081 |
| C（1） | C-065 |
| F（2） | F-055, F-057 |
| H（5） | H-018, H-052, H-061, H-062, H-066 |
| J（13） | J-001, J-002, J-020, J-021, J-024, J-025, J-027, J-033, J-034, J-041, J-048, J-051, J-052 |
| GATE（7） | GATE-032, GATE-035, GATE-038, GATE-042, GATE-091, GATE-092, GATE-099 |

计划 §1 WS2 点名重点：**H-052 / H-066 的硬编码行号假通过**（批次 45 记录 §9.2 已记「H-066 曾因硬编码行号出现假通过」）。

### 3.2 UNVERIFIED（144）

| 域 | n | id |
|---|---:|---|
| D0 | 5 | 031–035 |
| A | 24 | 003, 005, 006, 014, 015, 023, 024, 031, 032, 033, 041, 049, 050, 051, 058, 059, 060, 065, 066, 068, 069, 077, 086, 087 |
| B | 2 | 077, 078 |
| C | 12 | 005, 008, 010, 015, 020, 045, 066, 067, 068, 071, 088, 089 |
| E | 10 | 007, 016, 025, 034, 043, 052, 061, 070, 079, 086 |
| F | 23 | 002, 009, 011, 012, 018, 020, 027, 038, 039, 047, 048, 050, 054, 056, 058, 063, 072, 074, 081, 083, 084, 085, 090 |
| G | 6 | 016, 036, 072, 080, 081, 089 |
| H | 7 | 063, 075, 076, 077, 080, 089, 090 |
| I | 32 | 005, 009, 014, 018, 023, 025, 027, 030, 032, 036, 039, 041, 043, 045, 050, 054, 059, 063, 066, 068, 072, 077, 081, 082, 083, 084, 085, 086, 087, 088, 089, 090 |
| J | 22 | 003, 007, 008, 009, 016, 018, 022, 023, 026, 036, 044, 045, 049, 053, 054, 063, 064, 066, 067, 070, 071, 072 |
| GATE | 1 | 098 |

### 3.3 BLOCKED（161）

| 域 | n | id |
|---|---:|---|
| A | 1 | A-010 |
| D | 9 | D-082 … D-090（连续） |
| F | 10 | F-006, F-015, F-024, F-033, F-042, F-051, F-060, F-069, F-078, F-087 |
| G | 2 | G-087, G-088 |
| H | 12 | H-008, H-024, H-025, H-036, H-039, H-040, H-041, H-042, H-043, H-051, H-053, H-081 |
| I | 5 | I-007, I-061, I-070, I-075, I-079 |
| J | 34 | J-006, J-017, J-019, J-035, J-037, J-038, J-039, J-042, J-043, J-055, J-056, J-057, J-061, J-065, J-068, J-069, J-073 … J-090（073–090 连续 18） |
| FIX | 85 | FIX-016 … FIX-100（连续） |
| GATE | 3 | GATE-096, GATE-097, GATE-100 |

按 adjudication §4 已分档：**151 条外部条件 + 10 条内部热区/授权**；H 域 12 条中 6 条内部热区、6 条外部（`docs/acceptance/adjudication.md:119`）。

### 3.4 PASS（865）的取得方式

PASS 集合 = 每域 `1..N` 全集 − 上述三张列表。已用 `acceptance_model.expected_ids()` 对齐，`identity_issues=[]`，故三表 + 每域 PASS 数（§2）即唯一确定 865 行，无歧义。

## 4. 现有复核 JSON 与台账的绑定状态

命令：探针 1（读 `docs/acceptance/verdicts/*-review.json`），字段取 `ledger_sha256` / `ledger_sha256_at_review`。

**当前台账 sha = `887e3ba6…`；10 份复核 JSON 全部声明 `bb25ef23…` → 10/10 不匹配。**

| 文件 | 声明 ledger_sha256 | 匹配当前 | verdict | reviewer | self_review |
|---|---|---|---|---|---|
| A-review.json | `bb25ef23…` | ✗ | UNVERIFIED | subagent-a-domain-independent-review | false |
| B-review.json | `bb25ef23…` | ✗ | UNVERIFIED | independent-b-reviewer-batch45 | false |
| C-review.json | `bb25ef23…` | ✗ | FAIL | subagent-c-domain-independent-review | false |
| D-review.json | `bb25ef23…` | ✗ | UNVERIFIED | batch45-d-domain-independent-reviewer | false |
| E-review.json | `bb25ef23…` | ✗ | UNVERIFIED | b45-e-independent-reviewer | false |
| F-review.json | `bb25ef23…` | ✗ | UNVERIFIED | batch45-f-domain-independent-reviewer | false |
| G-review.json | `bb25ef23…` | ✗ | BLOCKED | independent-reviewer:g-domain-batch45 | false |
| H-review.json | `bb25ef23…` | ✗ | UNVERIFIED | batch45-h-domain-independent-reviewer | false |
| I-review.json | `bb25ef23…` | ✗ | UNVERIFIED | batch45-i-domain-independent-reviewer | false |
| J-review.json | `bb25ef23…` | ✗ | FAIL | independent-reviewer-j-domain-b45 | false |

每份文件自带 `ledger_binding_note`，逐字写明「本复核绑定修复前台账 `bb25ef236056…`…因此本文件不得作为新台账的行级 review 证据；要转 PASS 必须在新台账上重跑」。即：这 10 份是下一轮重签的**输入**，不是证据。

逐行终裁 JSON 的现状（计划 §1 item 10 要求的 `docs/acceptance/verdicts/rows/<ID>.json`）：

- `docs/acceptance/verdicts/rows/` **目录不存在**（`ls` 报 No such file or directory）；
- `git ls-files docs` 中匹配 `*/rows/*` 或 `/<域>-NNN.json` 的文件数 = **0**；
- 台账 PASS 行中含 `review:` 证据 token 的行 = **0**（探针 1：`pass_with_review_ids = []`）。

## 5. 门禁现状（基线值，冻结前）

| 命令（逐字） | exit | 关键输出 |
|---|---:|---|
| `python -I tools/ci/acceptance_report.py` | **1** | 865/30/161/144；`pass_with_review=0`；`violations=[]`；`state=FAIL` |
| `python -I tools/ci/acceptance_report.py --require-complete` | **1** | violations 两条：`NOT_ACCEPTANCE_COMPLETE`（`UNVERIFIED=144 FAIL=30 BLOCKED=161`）、`PASS_WITHOUT_REVIEW`（`865 个 PASS 行没有 review 证据`） |
| `python -I tools/ci/acceptance_lint.py` | **0** | `violations=0`；`warnings=140`（全为 `EVIDENCE_REUSED`）；`reuse=674`（阈值 5） |
| `python -I tools/ci/acceptance_lint.py --strict-warnings` | **1** | 140 条 `EVIDENCE_REUSED` 被升级为 violations |
| `python -I tools/ci/trace_requirements.py` | **0** | 1200 行、13 域计数全对、`gaps=[]` |

`EVIDENCE_REUSED` 样例（最大者）：`docs/research/evidence/2026-11-05-batch-44/b-domain/b-migration-matrix.xml` 与同名 `.device.json` 各被 **72 行** PASS 共用；`…/e-domain-data-jvm.cmd.log` 56 行；`…/c-node-compiler.log` 31 行。这是历史遗留，不是本批新增；但 WS1 的「行级独立复核」必须正面处理「一行证据服务 72 行」的独立性问题（§8.5）。

## 6. 证据入库状态

### 6.1 是否已入库：**是**

| 根 | 已跟踪文件数 | 命令 |
|---|---:|---|
| `docs/acceptance/evidence/` | **59** | `git ls-files docs/acceptance/evidence \| wc -l` |
| ├ `2026-11-06-batch-45/` | 24 | 含 `EVIDENCE/REVIEWS/GATES.sha256`、`report*.json`、`lint*.json`、`trace.json` |
| ├ `G/`、`H/` | 35 | G: device/hashes/logs/tests；H: acceptance_lint*/acceptance_report*/logs/tests |
| `docs/research/evidence/` | **1051** | `git ls-files docs/research/evidence \| wc -l` |
| ├ `2026-11-05-batch-44/` | 470 | 批次 44 原始证据 |
| ├ `2026-11-06-batch-45/` | 246 | 本批设备/复核/门禁证据 |

`docs/acceptance/` 整体 **0 dirty**（台账、verdicts、evidence 均已提交且干净）→ 台账 sha `887e3ba6…` 与 HEAD 内容自洽，可作为本批复核的绑定基准。

### 6.2 PASS 行证据落点分布（探针 2）

| 落点 | token 数 | 行数 |
|---|---:|---:|
| `docs/research/evidence/2026-11-05-batch-44` | 1484 | **522** |
| `docs/research/evidence/2026-11-06-batch-45` | 402 | 197 |
| `docs/acceptance/evidence/G` | 165 | 82 |
| `docs/acceptance/evidence/H` | 132 | 66 |
| `core/*`（file/hash） | 52 | 46 |
| `app/*`、`docs/*`、`feature/*`、`PRIVACY-DATA-FLOW.md`、`tools/*` | 26 | 22 |

结论：**522 / 865 行（60.3%）的 PASS 证据来自批次 44 的旧树**。行级复核若只是「重读旧日志并抄一遍」不构成对当前 HEAD 的复核；须说明每行证据与当前源码的一致性判据，或标注为不可从 HEAD 重放。

分域证据类（PASS 行 token 计数，`file/hash` 不计独立证据）：A cmd82/device72/test52；B cmd90/device95/test97；C cmd115/device82/test72/file16；D cmd81/test82；D0 cmd93/test93；E cmd101/test119；F cmd58/device64/test15/file2/hash10；FIX cmd15/test15；G cmd74/device14/test78；GATE cmd90/device14/test79/hash4；H cmd66/test66/file75；I cmd56/device50/test21/file6/hash2；J cmd24/device4/test17。

### 6.3 证据根不洁净：4 个已跟踪文件 modified

```text
 M docs/research/evidence/2026-10-28-batch-29/step06-signatures.md
 M docs/research/evidence/2026-11-06-batch-44/00-index.md
 M docs/research/evidence/2026-11-06-batch-44/DIR-SHA256.txt
 M docs/research/evidence/2026-11-06-batch-44/E-FILES-SHA256.txt
```

（`git status --porcelain -uall -- docs/research/evidence`）来源为前批遗留，非本基线产生。后果：**引用这些文件做 `file:`/`hash:` 证据的行，其 sha 复算会与已提交清单不一致**；复核时要么按工作树现值记录，要么显式标注该证据不可从 HEAD 重放。

## 7. 不可变项锚点（只读核对：均存在且未被我触碰）

| 项 | 锚点 | 本次核对 |
|---|---|---|
| 6C-2 三项不可变 | ①mask=8 不提供 / mask=0 禁止：`core/domain/.../BackupSelection.kt:90`（`isCreatable = isClosed && mask in ALLOWED_MASKS`）、`:140`（`require(mask in ALLOWED_MASKS)`，报错文案列举 9/13/15）、`:166`（`ALLOWED_MASKS = linkedSetOf(9, 13, 15)`）。②派生索引恒裁：`core/data/.../backup/AndroidBackupRepository.kt:1350-1377`（`resetDerivedSearchIndex` 对 `SmbkSelectionPlan.derivedTables` 无条件 `DELETE FROM`，并按 `trimOrder` 拓扑序裁剪）。③只读实读记录：`docs/research/evidence/2026-11-06-batch-45/gate/sixc2-source-read.txt` | 两个源文件 `git status` 均**无改动**；锚点行逐字存在（`:140` 报错文案、`:166` 常量、`:1357/1374` derivedTables）。**本基线只核对锚点存在与文件洁净，未重判三项语义** |
| KD-76 open | `docs/known-defects.md:2916` | 行存在，标题仍为 `KD-76 (open, 2026-10-26 批次 24 T5 登记…)` |
| isolation 不定案 | `docs/known-defects.md:2745`（KD-66 批次 40 E-40-04 = PARTIAL，「待主会话裁定 A3 判据口径」） | 行存在；本轮不改其结论 |
| p09 未收敛 | `docs/research/evidence/2026-11-06-batch-45/e-device/verify-p09-run1..run3.txt`（三轮） | 三份原始记录在库；本批不重判收敛 |
| AUD-08 FAIL | `docs/research/evidence/2026-11-06-batch-45/gate/aud08-split-gate.txt`、`aud08-params.txt` | 证据在库；本批维持 FAIL |

热区定义（`docs/acceptance/runbook.md:198` 6C-2 一项、`:199-200` 热区禁改行，逐字）：6C-2 三项不可变，只可加不变量测试；热区禁改 = `core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root`，`core/export` 只做已批准 hunk；不引新依赖；共享工作树不做 git 写。

当前热区/邻近文件的脏状态（前批遗留，本批不得再加改动）：

```text
 M app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
 M app/src/main/kotlin/com/tingyun/smartmistakebook/ReviewReminderCoordinator.kt
```

## 8. WS1 的五个结构性约束（读源码得出，非猜测）

1. **`pass_with_review` 只数台账 evidence token，不数文件存在性。** `tools/ci/acceptance_report.py:349-356` 用 `any(e.kind == "review" …)` 求和。⇒ 只新建 `docs/acceptance/verdicts/rows/<ID>.json` **不会**把 0 变成 865；必须给每条 PASS 行的证据单元格追加 `review:docs/acceptance/verdicts/rows/<ID>.json`。
2. **复核必须绑「最终」台账 sha，且存在顺序死结的解。** `acceptance_report.py:188-191` 对 `declared != actual` 报 `REVIEW_LEDGER_SHA_MISMATCH`。⇒ 可解顺序：①先把 865 行的 review token 定稿写入台账 → ②`sha256sum` 得 S → ③写 865 份 JSON，`ledger_sha256=S` → ④跑报告。**此后台账任何一处改动都会让 865 份复核同时作废。**
3. **判词枚举封闭。** `acceptance_report.py:181` 只接受 `PASS/FAIL/BLOCKED/UNVERIFIED`；自造判词（`SAMPLED_REVIEW`、`PASS_WITH_REVIEW` 等）报 `REVIEW_VERDICT_INVALID`。细则写进 `verdict_detail`/`scope`/`notes`。
4. **计划覆盖缺口：197 行 PASS 无责任人。** 计划 §1 items 2–11 只派 A–J 十域（668 行），但 §3 验收要求 `pass_with_review == 865`，差集 = D0 93 + FIX 15 + GATE 89 = **197 行**。需要主会话在开工前指派，否则本批必然卡在 `PASS_WITHOUT_REVIEW 197`。
5. **独立性不只是「reviewer≠authors」。** 现有台账已有 140 条 `EVIDENCE_REUSED`（单文件最多服务 72 行）。行级复核对这类行必须给出逐行可复算的差异（该行断言的具体数值/行为），否则 72 份 JSON 会共用同一份证据签名 —— 形式上合规、实质上是批量盖章，属于本 ask 明令禁止的「空 checks / summary 当证据」变体。

## 9. 本基线未做 / 无法做的

| 项 | 状态 | 说明 |
|---|---|---|
| Gradle（单测门、强门、环后强门） | **未跑** | 本 ask 禁 Gradle；属门禁阶段串行任务 → 本文件不给任何 Gradle 结论 |
| 30 条 FAIL 重判（WS2） | **未做** | 属后续任务；本文件只冻结 30 个 id |
| 144 条 UNVERIFIED 收敛（WS3） | **未做** | 同上；本文件只冻结 144 个 id |
| 161 条 BLOCKED 逐条确认（WS4） | **未做** | 同上；本文件只冻结 161 个 id 与既有分档 |
| 865 份行级 JSON（WS1） | **未做** | 本 ask 仅要求基线；目录尚不存在，0/865 |
| `dirty_paths` 3758 vs 记录 4091 | **未核实** | 前批记录写于收口提交前，工作树构成不同；差异来源不明，不作为结论 |
| 4 个 modified 证据文件的来源 | **未核实** | 仅记录 dirty 事实与其对 sha 复算的影响 |

## 10. 复现命令（本文件所有数字均可重跑）

```bash
# 计数 / 门禁
python -I tools/ci/acceptance_report.py
python -I tools/ci/acceptance_report.py --require-complete
python -I tools/ci/acceptance_lint.py
python -I tools/ci/acceptance_lint.py --strict-warnings
python -I tools/ci/trace_requirements.py

# 台账 sha 与规模
sha256sum docs/acceptance/ledger.md && wc -lc docs/acceptance/ledger.md

# 分域计数 / ID 列表 / 复核绑定（探针 1，只读；脚本在仓外临时目录）
python -I <tmp>/probe.py "<repo>"      # 输出：counts、by_domain、ids、pass_with_review_ids、reviews[*].ledger_sha256
python -I <tmp>/probe2.py "<repo>"     # 输出：per_domain_evidence_kinds、pass 证据落点分布

# 环境
C:/Android/Sdk/platform-tools/adb.exe devices
git ls-files docs/acceptance/evidence | wc -l
git ls-files docs/research/evidence | wc -l
```

探针 1/2 只 import `tools/ci/acceptance_model.py` 并打印 JSON，不写仓库内任何文件（本次输出落在仓外临时目录）。

## 11. 回链

- 计划：`.zcode/plans/plan-2026-11-06-batch-46-line-review.md`（§0 目标、§1 WS1–WS5、§2 硬约束、§3 验收）。
- 上一批：`docs/research/2026-11-06-batch-45-record.md` §9（NOT ACCEPTED、五条阻断、§9.4 批次 46 唯一出口）。
- 终裁/台账/协议：`docs/acceptance/adjudication.md`（§0/§3/§4/§5/§6/§9）、`ledger.md`、`dod.md`、`runbook.md`（§11 边界与纪律 `:196`、`:198`、`:199-200`）。
- 投影：`docs/acceptance/domain-index.md`（13 域计数与本基线逐格一致）、`verdicts.md`、`acceptance-report.json`、`verdicts/README.md`（逐行 JSON 格式约定，含 `ledger_sha256`/`reviewer`/`authors`/`evidence_sha256`）。
- 工具判据：`tools/ci/acceptance_report.py:1-26`（9 条 PASS 判据）、`:161-199`（review 校验）、`:349-356`（两计数）；`tools/ci/acceptance_lint.py:1-27`（7 条反作弊规则）。
- 证据：`docs/acceptance/evidence/2026-11-06-batch-45/`（24）、`docs/acceptance/evidence/{G,H}/`（35）、`docs/research/evidence/2026-11-06-batch-45/`（246）。
