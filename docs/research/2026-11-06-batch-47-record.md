# 批次 47 记录（2026-11-06）：验收收敛批——32 FAIL 修复、318 行行级判别力、280 BLOCKED runbook（78 项）

- 结论先行：**NOT ACCEPTED（验收不通过）**。`acceptance_report --require-complete` 本轮记录员实跑 **exit 1**
  （`NOT_ACCEPTANCE_COMPLETE` FAIL=23 BLOCKED=280 + `REVIEW_CHECKS_SHARED` 18 组），报告 exit 与终裁一致
  （`docs/acceptance/adjudication.md` §0/§9，批次 47 版）。
- 计划：`.zcode/plans/plan-2026-11-06-batch-47-acceptance-converge.md`（WS1–WS5，78 项）
- 批次基线：`docs/research/2026-11-06-batch-47-baseline.md`（342 行，§3 FAIL 32 逐条行号、§4 shared-suite 双口径、§4.3 两个假绿提醒）
- 上一批：`docs/research/2026-11-06-batch-46-record.md` §9（NOT ACCEPTED；PASS 888 / FAIL 32 / BLOCKED 280）
- 终裁：`docs/acceptance/adjudication.md`（批次 47 报告重生成轮 + 修复环复核轮，NOT ACCEPTED，含签字边界）
- 台账：`docs/acceptance/ledger.md` sha256 `a9912c2a…254737`（1200 行）；域导航 `docs/acceptance/domain-index.md`

## 0. 口径与基线

### 0.1 项数口径

- **计划 78 项** = WS1 逐条 FAIL 处置 32 + 修复模块定向测试 8 + WS2 十域行特有 10 + 自检脚本 10
  + WS3 runbook 分组 10 + 内部单列 1 + 外部单列 1 + WS4 报告/lint/终裁/台账 4 + WS5 判定 2。
- **台账 1200 行** = D0 100 + A–J 各 90（900）+ FIX 100 + GATE 100。本轮逐行解析：
  `rows=1200`、重复 id=0、域分布与上式一致。
- **编号映射说明**：计划 §1 只给编号与分组，未逐条列名；本记录按"台账行序 + 域顺序"
  展开（§1），映射方式可复核，与批次 46 记录同一做法。

### 0.2 基线快照（本轮记录员实测）

| 项 | 值 | 证据（本轮命令/实读） |
|---|---|---|
| HEAD | `39d8c75aebbf0b5f0acf4053d559fe35cf175145` | `git rev-parse HEAD`（与 `acceptance_report` 输出 head 一致） |
| 工作树 `porcelain` | **1493**；`-uall` **4795**；暂存 **0** | `git status --porcelain[-uall] \| wc -l`；`git diff --cached --name-only \| wc -l` |
| 台账（盘上） | sha256 `a9912c2abe7e0eaa9b19eee4c507b608dfad27fe74a455e40cfc1adb56254737`；1231 行 / 566,134 B | `sha256sum` / `wc -l` |
| 台账（HEAD 版） | `19ea6b57…`（批次 46 终态，PASS 888 / FAIL 32 / BLOCKED 280） | `git show HEAD:docs/acceptance/ledger.md` |
| 行级 JSON | `docs/acceptance/verdicts/rows/` **920** 份 = 897 PASS + 23 FAIL | `ls \| wc -l` + 逐份解析 |
| 批次 47 证据根 | `docs/research/evidence/2026-11-06-batch-47/` **103** 文件（c/e/f/h/i/j 六域）；`docs/acceptance/evidence/2026-11-06-batch-47/` 3 清单 + gate 14 件 | `find -type f \| wc -l` |
| 设备 | **本记录员会话 adb 不在 PATH，未跑设备轨**；F/H/J 域设备复跑由修复轨完成并归档（`export-batch47-r1..3.*`、`h-061-red/green`、j-domain 14 件），本轮只做字节与字段复算 | `adb devices` → command not found；归档文件逐一实读 |

### 0.3 台账两代：引用必须带 sha

1. **`19ea6b57…`（HEAD / 批次 46 终态）**：`PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0`。
2. **`a9912c2a…`（批次 47 终态，盘上为准）**：`PASS 897 / FAIL 23 / BLOCKED 280 / UNVERIFIED 0`。
   本轮所有行级 JSON（920/920 `ledger_sha256` 逐份复算绑定此值）、报告、清单、本记录一律绑它。

### 0.4 不可变量（只读复核）

| 项 | 本轮实读/实跑 | 判定 |
|---|---|---|
| 6C-2 mask 集合 | `SmbkSelectionPlan.kt:79` `setOf(9, 13, 15)`；`:593` `require(allowedMasks == setOf(9, 13, 15))`；`git diff --stat` 该文件 **空** | 未漂移 |
| 6C-2 三项行为语义（mask=8 不提供 / mask=0 禁止 / 派生索引恒裁） | 本批未动该文件代码；只复核常量与 `require`。不把常量存在读成三项语义被本轮重新证明 | 未改 |
| KD-76 | `docs/known-defects.md:2916` `## KD-76 (open, 2026-10-26 批次 24 T5 登记…` | **open** |
| isolation | `docs/known-defects.md:2745` E-40-04 verdict=**PARTIAL**、A3 判据口径待主会话裁定 | **不定案** |
| p09 | 沿用批次 45 记录 `:163`/`:228`（三次 2071/1890/1861 ms > 1300 ms）；本批未重跑 | **未收敛** |
| AUD-08 | 本轮实跑 `check_split_gate.py` **exit 1**，`countExactRate=4/7=0.5714 < 0.9`，`fragmentRate=0/16` 过 | **FAIL** |
| 热区 | 禁改清单 `runbook.md:244`（`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root）；脏表中热区路径 17 个，**mtime 在 2026-11-05 15:00 之后的写入 = 0**（逐路径 stat 复算） | 未改 |
| 依赖 | `git diff -- '*.gradle' '*.gradle.kts'` 新增行中命中 `implementation/api/kapt/ksp/classpath` 的仅 **1 行且为注释**（`// （942 组件…）`） | 未引 |

### 0.5 证据边界（先读这一段再看数字）

- 920 份行级 JSON 全部 `review_kind=mechanical-recompute`、`independence.independent_agent=false`
  （920/920 逐份解析）。它证明"证据存在、字节匹配、可判定字段为绿"，**不证明语义充分**，
  更不是第二人/第二模型复核。
- `pass_with_review=897/897` 是机械复算口径（checks 非空 + evidence sha 逐文件匹配 +
  ledger_row 命中当前行）；其中 `pass_with_row_specific_review=728`。
- 三项统计的三个口径见 §4；"BLOCKED runbook 覆盖 280/280"只证明出口条目存在，**不证明条件已取得**。

## 1. 78 项逐项对账

> 映射口径：1–32 = 批次 46 的 32 条 FAIL 按台账行序（F 9 → J 13 → H 3 → GATE 6 → G 1，
> 与基线 §3 一致）；33–40 = 修复相关模块定向测试八项；41–50 = A–J 十域行特有补齐；
> 51–60 = 自检脚本与 shared-suite 四口径；61–70 = runbook 载体 + 七类分组 + 覆盖自检；
> 71–72 = 内部/外部单列；73–76 = WS4 四项；77–78 = WS5 判定。

### 1.1 WS1：32 条 FAIL 逐条处置（1–32）

| # | id | 处置 | 本轮复核依据（记录员实读/实跑） |
|---:|---|---|---|
| 1 | F-019 | **FAIL→PASS** | `F-019-gfxinfo-recount.{cmd.log,json}` 在盘且含 `2350`（冷#6 实测 p50，台账由 2150 上界改正） |
| 2 | F-028 | **FAIL→PASS** | 无源数字删除，`F-028-export-values-recount.*` 归档；report 复算通过 |
| 3 | F-029 | **FAIL→PASS** | 5000ms 断言 3/3；`export-batch47-r1..3.perf.logcat.txt` 实读 `elapsedMs=490/464/478` |
| 4 | F-034 | **FAIL→PASS** | 同上 r1=490 命中；`F-034-logcat-recount.*` 归档 |
| 5 | F-036 | **FAIL→PASS** | `F-036-regression-recount.json` 实读 `b43_documented_ms=[545,569,518]`（均值 544.0） |
| 6 | F-055 | 保留 FAIL | 2h soak 无新证据；行 JSON 证据仍指批次 44 soak 归档（`current_batch_files=1`） |
| 7 | F-057 | 保留 FAIL | 同上 |
| 8 | F-064 | **FAIL→PASS** | dropbox 口径改正（data_app_* = 6），recount 归档在盘 |
| 9 | F-067 | **FAIL→PASS** | 稳定性判据改为 `NEW_EVENTS_IN_WINDOW=0` 可复算形式 |
| 10–22 | J-001/002/020/021/024/025/027/033/034/041/048/051/052 | 保留 FAIL（13 条） | `docs/acceptance/j-fail-tickets-b47.md` 逐条登记复跑命令与观测 exit（identity/signing/aab16k exit 1；versionCode 三条 exit 0 但判词看值）；J-027 文档契约已树内修复但签名环境仍缺，判定未改 |
| 23 | H-018 | 保留 FAIL | 本轮实跑 `h_compliance_gate --only H-018` **exit 1**（DOCX 无文档级 `/AIGC`；KD-85 落点跨热区） |
| 24 | H-061 | **FAIL→PASS** | 先红后绿：`h-061-red-b47.log` BUILD FAILED + `h-061-connected-green.xml` tests=1 failures=0；本轮实跑 gate `--only H-061` **exit 0** |
| 25 | H-062 | 保留 FAIL | 本轮实跑 gate `--only H-062` **exit 1**（KD-85 热区） |
| 26–31 | GATE-032/038/042/091/092/099 | 保留 FAIL（6 条） | 门本身本轮实跑仍红：report exit 1、lint exit 1（144）、AUD-08 exit 1（4/7）；GATE-091/092/099 三行 checks 逐字节同源（§4） |
| 32 | G-089 | **FAIL→PASS** | `gradle/verification-metadata.xml` 在盘 425,560 B，sha `03efb87f…` 与行 JSON `file_sha256` check 命中；`g-089-red-baseline.log` 先红；junit `tests=4 failures=0` |

- **HEAD 台账 vs 盘上台账 verdict diff = 恰 9 处，全部 FAIL→PASS**；无 PASS→FAIL、无 BLOCKED→PASS
  （本轮逐行比对复算）。无洗绿：9 条各有批次 47 新证据（上表），23 条保留并登记出口。
- F 域 7 条的性质是"台账叙述与归档字节矛盾"，修复方向 = 台账文字向实测值对齐 + 补原始输出
  （基线 §3 声明的唯一合法修复路径）。

### 1.2 修复相关模块定向测试（33–40）

| # | 范围 | 证据与 exit |
|---:|---|---|
| 33 | F 域 recount | `f-domain/F-019…F-067` 7 组 `.cmd.log+.json` + `db-perf-b47-5584` 设备 4 件归档；exit 均落盘（我抽验 3 组内容命中） |
| 34 | H 域设备红绿轮 | red log（BUILD FAILED）+ green XML（1/0/0）+ gate 1/0/1（本轮记录员复跑 3 条） |
| 35 | J 域 13 条复跑 | `j-fail-tickets-b47.md` 逐条命令+exit；`j-domain/` 14 件证据 |
| 36 | G-089 | red baseline + junit 4/0/0 + 文件 sha 命中（本轮复算 sha 一致） |
| 37 | C 域 witness 重锚 | `c-domain/c-node-bench-b47.cmd.log` 修复环实跑 `94 passed, 0 failed … EXIT=0`（归档；本轮记录员未重跑 node） |
| 38 | E 域行级修补 | e-row-repair 54+23=77 行重绑；`gate/regen-validation.json` 920 份复算 0 错误 |
| 39 | GATE 门复跑 | report exit 1 / lint exit 1 / trace exit 0 / AUD-08 exit 1 —— **本轮记录员全部实跑**（§3） |
| 40 | 工具自检 | pytest 4 文件 **48 passed**（exit 0）+ 三清单 `--verify` **0/0/0** —— 本轮记录员实跑 |

### 1.3 WS2：十域行特有补齐（41–50）

批次 47 行级复核 193 行（run_id 分布逐份解析）：E 77（54+23）、I 42、C 25、A 18、B 16、F 9（7 转 PASS + F-055/057 保留）、H 3、G 2 + G-089 1。判定：**部分完成**——

| 域 | 本批动作 | 残余（无域内独有 check 行） |
|---|---|---:|
| A | 18 行 rowspecific | 5 |
| B | 16 行 | **0** |
| C | 25 行 + 修复环重锚 16 行 | 16 |
| D | 批 46 已全独有，本批 0 新行 | **0** |
| E | 77 行 | **0** |
| F | 9 行 | 28 |
| G | 2 行 + G-089 | 4 |
| H | 3 行 | **0** |
| I | 42 行 | **0** |
| J | 0 行新复核（13 FAIL 走缺陷单） | 7 |

（`check_row_specific_checks.py` 本轮实跑输出逐域一致；D0 91 / GATE 77 / FIX 12 不在十域但是残区主体。）

### 1.4 自检脚本与四口径（51–60）

- 51 `check_row_specific_checks.py` 落地：exit **1**，240 行无域内独有 check → 判据未清零，**部分完成**。
- 52 ≥5 行共用检测（`acceptance_report` 内置）：**18 组 / 119 行** → 未清零。
- 53 2–4 行组盲区（门不阻断层，按报告同指纹算法复算）：**20 组 / 53 行**（PASS 50 + FAIL 3）。
- 54 920 份机械复算：`regen-validation.json` `clean_rows=920, errors=0`；本轮记录员独立逐份解析一致。
- 55 三清单 verify ×3：exit **0/0/0**。
- 56 pytest：**48 passed**。
- 57–60 四口径终值：门面 18 组/119 行（D0 91 + GATE 28，全 PASS）；`checks_scope=shared-suite` **172**（D0 91 / GATE 66 / FIX 9 / F 6）；缺域内独有 **240**；盲区 **53** 行——**全部未清零**。

### 1.5 WS3：BLOCKED runbook（61–72）

- 61 载体 `docs/acceptance/blocked-runbook.md`（新增）：七类总索引 + 内部/外部单列 + 13 个域小节 + 追加模板。
- 62–68 七类：§1 Provider 17 / §2 签名 20 / §3 arm64 21 / §4 金标 37 / §5 环境 51 / §6 热区 18（内部）/ §7 授权 116（内部）。
- 69 内部/外部单列：外部 146 + 内部 134 = **280**（与台账 BLOCKED 数一致，本轮加总复核）。
- 70 覆盖自检：默认 `runbook.md` **89/280 exit 1**；计划指定载体 `blocked-runbook.md` **280/280 exit 0**
  （本轮以同一 `coverage()` 算法、把模块常量指到该文件复现，未改任何文件；两份 gate JSON 在
  `docs/acceptance/evidence/2026-11-06-batch-47/gate/`）。
- 71 内部项单列给用户：**载体已列（134 行）**，热区/授权在本批**一项都未取得** → 条件未取得。
- 72 外部项单列（146 行）：**完成**；外部条件（Provider/签名/arm64/金标/环境）本批未取得。
- **红线**：280/280 只证明每行有可执行命令/人工判据的出口条目；**BLOCKED 0 条转 PASS**（本轮 verdict diff 佐证）。

### 1.6 WS4/WS5（73–78）

- 73 `--require-complete`：exit **1**（`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`）。
- 74 lint（exit 1，144 `EVIDENCE_REUSED`；`--warn-only` exit 0）/ trace（exit 0，gaps 0）/ 三清单（0/0/0）。
- 75 `adjudication.md`：批次 47 版已落盘并签字（NOT ACCEPTED，§9.1 可签 1–6 / §9.2 不签 1–5）。
- 76 台账最终 sha `a9912c2a…` + 统计 897/23/280/0（本轮逐行复算一致）。
- 77 前提（FAIL=0 且 shared=0）**不成立** → 不触发签"通过"。
- 78 **维持 NOT ACCEPTED**，不可签字项已在终裁 §9.2 列明 → 完成。

## 2. 各轨实值

### 2.1 域分布（本轮逐行解析，与 `acceptance_report` 输出一致）

| 域 | 行 | PASS | FAIL | BLOCKED | 复核 |
|---|---:|---:|---:|---:|---|
| D0 | 100 | 100 | 0 | 0 | 100/100（shared-suite 残 91 行，§4） |
| A | 90 | 65 | 0 | 25 | 65/65 |
| B | 90 | 88 | 0 | 2 | 88/88 |
| C | 90 | 88 | 0 | 2 | 88/88 |
| D | 90 | 81 | 0 | 9 | 81/81 |
| E | 90 | 81 | 0 | 9 | 81/81 |
| F | 90 | 55 | 2 | 33 | 55/55 |
| G | 90 | 83 | 0 | 7 | 83/83 |
| H | 90 | 69 | 2 | 19 | 69/69 |
| I | 90 | 53 | 0 | 37 | 53/53 |
| J | 90 | 29 | 13 | 48 | 29/29 |
| FIX | 100 | 15 | 0 | 85 | 15/15 |
| GATE | 100 | 90 | 6 | 4 | 90/90 |
| **合计** | **1200** | **897** | **23** | **280** | **897/897** |

### 2.2 FAIL 23 条（全部保留，未洗绿）

F-055/057（2h soak 未完成）、H-018/062（KD-85 热区，gate exit 1/1 本轮实跑）、
J 13 条（`j-fail-tickets-b47.md`：脏树 dirty 4795、产物落后 HEAD、`RELEASE_*` 缺失、
AAB 陈旧 ABI 4≠2/缺 `libquickjs.so`/证书过期、versionCode 手写=2；J-027 文档契约已修但签名环境缺）、
GATE 6 条（report/lint/AUD-08 本轮实跑 exit 1）。

### 2.3 FAIL→PASS 9 条（防洗绿核验）

见 §1.1 表。交叉验证：HEAD 台账 verdict diff 恰 9 处全 FAIL→PASS；抽样 5 条（F-019/F-034/F-036/G-089/H-061）
证据文件字节与行 JSON check 逐项命中；920 份复算 0 硬错误；`pass_with_review` 复算 897/897、invalid 0。

### 2.4 BLOCKED 280（0 转 PASS）

外部 146（Provider 17/签名 20/arm64 21/金标 37/环境 51）+ 内部 134（热区 18/授权 116）。
runbook 覆盖 280/280（§1.5）；条件取得 **0 项**。

## 3. 门禁 exit（本轮记录员逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **1** | PASS 897 / FAIL 23 / BLOCKED 280 / UNVERIFIED 0；violations=0；warnings=18（全 `REVIEW_CHECKS_SHARED`）；`pass_with_review=897`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=728`；head `39d8c75a`、dirty=4795 |
| 2 | 同命令 `--require-complete --out build/b47-record/report-req.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=23 BLOCKED=280)` + `REVIEW_CHECKS_SHARED`（18 组） |
| 3 | `python -I tools/ci/check_row_specific_checks.py --out …` | **1** | 无域内独有 check 240 行（A5/C16/D0-91/F28/FIX12/G4/GATE77/J7）；`checks_scope=shared-suite` 172 行（D0 91/GATE 66/FIX 9/F 6） |
| 4 | `python -I tools/ci/check_blocked_runbook_coverage.py`（默认 `runbook.md`） | **1** | covered 89/280；missing 191 |
| 5 | 同一 `coverage()` 算法，`RUNBOOK` 指向 `docs/acceptance/blocked-runbook.md`（进程内替换常量，未改文件） | **0** | covered **280/280**；missing 0 |
| 6 | `python -I tools/ci/acceptance_lint.py` | **1** | violations=144（全 `EVIDENCE_REUSED`）、warnings=144 |
| 7 | 同命令 `--warn-only` | **0** | violations=0、warnings=144 |
| 8 | `python -I tools/ci/trace_requirements.py` | **0** | total=1200；gaps=0 |
| 9 | `python -I tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-06-batch-47 --verify …/EVIDENCE.sha256` | **0** | 103 项全 OK |
| 10 | 同工具 `--root docs/acceptance/verdicts --verify …/REVIEWS.sha256` | **0** | 933 项全 OK |
| 11 | 同工具 `--root docs/acceptance/evidence/2026-11-06-batch-47 --verify …/GATES.sha256` | **0** | 16 项全 OK |
| 12 | `python -I tools/split_golden/check_split_gate.py` | **1** | `countExactRate=4/7=0.5714 < 0.9`（AUD-08 维持 FAIL）；`fragmentRate=0/16` 过 |
| 13 | `python -I tools/ci/h_compliance_gate.py --only H-018 / H-061 / H-062` | **1 / 0 / 1** | H-061 绿（批次 47 修复）；H-018/H-062 红（KD-85 热区） |
| 14 | `python -I tools/ci/d0_self_acceptance.py --group all` | **1** | CHECKS=213 FAILURES=1：`h-061_fail EXPECTED=1 ACTUAL=0` —— 工具在 `d0_self_acceptance.py:698-699` 硬编码（"H-018","H-061","H-062" 逐一断言 ==FAIL）H-061=FAIL 的**陈旧预期**，非 H-061 回红（gate #13 实跑 exit 0） |
| 15 | `python -I -m pytest -q tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py tools/tests/test_acceptance_hash_manifest.py tools/tests/test_acceptance_init.py` | **0** | **48 passed** |

**本轮未跑**：Gradle 构建与单测（约束：门禁阶段只串行非 Gradle 命令）、设备轨（记录员会话 adb 不在
PATH；设备证据为修复轨归档字节，我未重跑）、2h soak、arm64/16KB 真机、真实 Provider、Play Console、
人工金标、TalkBack/IME 真机、p09 设备复测、第二人/第二模型语义复核、node 工具重跑（C 域 94/0 为修复环归档）。

## 4. 三项统计（计划 §3 判据）

| 统计项 | 实值 | 计划目标 | 达成 |
|---|---|---|---|
| **fail_count** | **23**（F 2 / H 2 / J 13 / GATE 6） | 0 | **未达成**（自 32 收敛 9 条，全部有新证据） |
| **shared_suite_rows** | 四口径并列：门面（≥5 行指纹组）**18 组 / 119 行**（D0 91 + GATE 28，全 PASS）；`checks_scope=shared-suite` **172** 行；缺域内独有 check **240** 行；2–4 行组盲区 **20 组 / 53 行**（PASS 50 + FAIL 3，含 GATE-091/092/099 FAIL 三行组逐字节同 checks） | 计划 WS2 写"318 行 → 20"（终裁表写 0） | **未达成**（318→172；门面 24 组/156 行 → 18 组/119 行） |
| **blocked_runbook_coverage** | **280/280**（计划载体 `blocked-runbook.md`，同算法复现 exit 0）；默认 `runbook.md` 89/280 exit 1（工具-计划错位如实并报） | 280/280 | **达成**（机械覆盖口径；不证明条件取得） |

交叉一致性：`pass_with_row_specific_review=728` ⇒ 897−728=169 行 PASS 非唯一指纹 = 门面 119 + 盲区 50，逐行复算吻合。

## 5. 复核与修复（本轮记录员独立复算摘要）

1. **920 份行级 JSON 逐份复算**：897 PASS + 23 FAIL；`ledger_sha256` 920/920 = `a9912c2a…`；
   `review_kind=mechanical-recompute` 920/920；`independent_agent=false` 920/920；空 checks 0。
   run_id 去重 **24 个值**（终裁 §2 写 27，差异为文本口径——193 行批次 47 系计数两边一致；
   见 §6.1）；批次 47 系 193 行 = e77/a18/f9/g089-1/g-2/b16/h3/c25/i42，与终裁一致。
2. **指纹分组复算**（按 `acceptance_report.py:265-271` 同算法，去 `ledger_row` 命名 check 后
   sort_keys JSON 串 sha256）：组大小分布 1×748、2×8、3×11、4×1、5×2、6×6、7×8、8×1、9×1；
   ≥5 行 18 组/119 行、2–4 行 20 组/53 行（含 GATE-091/092/099）。
3. **产物哈希复算**：`acceptance-report.json` = `dd33b36d…`、`verdicts.md` = `173e9264…`、
   `domain-index.md` = `431d93fb…`、三清单自哈希 = `75512572…/6a7f3871…/694d4968…`——
   与终裁 §10 逐项一致。
4. **修复环复核轮修补**（verdict 均不变，终裁 §8.7）：C 域 16 行 witness 重锚归档工件、
   C-043 常量拆写消 lint 误触发（145→144）、F-036 均值 543.7→544.0、blocked-runbook §3/F-4
   命令补 `-s <serial>`（覆盖 280/280）、台账 sha 重绑 920 行。本轮抽验：c-node-bench 归档
   `94 passed/EXIT=0`、lint 现 144、F-036 JSON 实读一致、coverage 复现 280/280。
5. **洗绿检查**：无。9 条 FAIL→PASS 全部以新证据转（§1.1）；23 条 FAIL 与 280 条 BLOCKED
   原样保留；`--require-complete` 仍红。

## 6. 遗留与风险

1. **数字口径两处出入（文本级，无门影响）**：① run_id 去重 24（我方逐份解析）vs 终裁 §2 "27 个值"；
   ② 计划 WS2 目标原文"318 行 → 20"，终裁 §0.1 表写"计划目标 0"。两项实值判定不变（均未达成）。
2. **行 JSON schema 缺口**：F-055/F-057 两份 JSON 缺 `domain` 字段（逐份解析为 null），
   不影响 verdict/门，建议补齐。
3. **shared-suite 主残区 = D0/GATE 段基础设施行**：A–J 十域基本清完（B/D/E/H/I 为 0），
   D0 91 + GATE 77（label 66）+ FIX 12 + F 28 + C 16 + J 7 + A 5 + G 4；2–4 行组门不阻断
   （基线 §4.3.1 预警的假绿层已如实并列在 §4）。
4. **门工具错位**：`check_blocked_runbook_coverage.py:39` 默认 `runbook.md`（89/280），
   与计划载体不一致；`check_row_specific_checks.py` 与该工具无单元测试（pytest 仅覆盖
   acceptance 四件 48 项）。
5. **d0 陈旧预期**：`d0_self_acceptance.py:698-699` 硬编码 `h-061_fail`，随 H-061 改判需 owner
   更新（本轮不代改工具）。
6. **证据链未入库**：`gradle/verification-metadata.xml`（425,560 B）与
   `tools/ci/{check_row_specific_checks,check_blocked_runbook_coverage,g_security_gate,j_release_verify,acceptance_review}.py`
   均 `??` 未跟踪（`git ls-files` 实测）；`h_compliance_gate.py` 为 M（内容锚点版未提交）；
   批次 47 两处证据目录未跟踪。入库前"从 HEAD 重放"不成立。
7. **独立性与前提**：复核仍为单会话机械复算（`independent_agent=false` 920/920）；
   验收在共享工作树（dirty 4795）上运行，J-001 结构性不可能在本环境转绿——需先提交。
8. **设备/外部条件**：本记录员未跑设备轨（adb 不在 PATH）；2h soak、生产签名、arm64 真机、
   真实 Provider、金标、热区授权、第二主体复核——一项都未取得。
9. **终裁**：`NOT ACCEPTED`（报告 exit 1 ↔ 终裁一致；WS5 任务 78 路径）。下一批入口 =
   终裁 §9.3：D0/GATE 119 行行特有补齐 → 23 条 FAIL 逐条闭环 → 未跟踪工具/证据入库 →
   再跑 `--require-complete`，exit 0 才可能签"验收通过（BLOCKED 清单除外）"。

## 7. 提交清单（建议块，≤6 路径/块）

1. `tools/ci/check_row_specific_checks.py` + `tools/ci/check_blocked_runbook_coverage.py`
   + `tools/ci/g_security_gate.py` + `tools/ci/j_release_verify.py` + `tools/ci/h_compliance_gate.py`
   + `tools/tests/test_h_compliance_gate.py`（批次 46/47 工具修复入库；G-089 证据链前提）。
2. `gradle/verification-metadata.xml`（G-089 实体文件，425,560 B）。
3. `docs/acceptance/ledger.md` + `acceptance-report.json` + `adjudication.md` + `domain-index.md`
   + `verdicts.md` + `runbook.md`（台账/报告/终裁/导航，sha 见 §0.3/§5.3）。
4. `docs/acceptance/blocked-runbook.md` + `docs/acceptance/j-fail-tickets-b47.md`（WS3 载体 + J 缺陷单）。
5. `docs/acceptance/verdicts/rows/`（批次 47 变更行，193 份重绑 + 9 转 PASS）
   + `docs/acceptance/evidence/2026-11-06-batch-47/`（3 清单 + gate 14 件）。
6. `docs/research/2026-11-06-batch-47-baseline.md` + `docs/research/2026-11-06-batch-47-record.md`（本文件）
   + `docs/research/evidence/2026-11-06-batch-47/`（103 件）。

> 注：`.gitignore:31` 排除部分 `*.log` 证据——入库时需主会话裁定显式 `-f` 或调整 ignore 规则
> （基线 §6.3：31/32 条 FAIL 引用未跟踪证据；批次 47 同样存在，G-089 之外 J/F 域证据多为 `.log`）。
> 提交后 `dirty_paths` 才可能回落，J-001 的 `--require-clean` 前提才可能解除。
