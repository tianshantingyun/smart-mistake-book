# 批次 47 报告重生成终裁（2026-11-06）

> 台账：`docs/acceptance/ledger.md` sha256 `a9912c2abe7e0eaa9b19eee4c507b608dfad27fe74a455e40cfc1adb56254737`（1200 行）
> run-id：批次 47 行级复核（`batch47-f-domain-fail-fix` / `batch47-g089-repair` / `batch47-h-domain-fail-repair` / `batch47-line-review-<域>` / `batch47-e-row-specific-repair(-r2)` / `batch47-a-domain-rowspecific` / `batch47-g-rowspecific-review`）
> 机器报告：`docs/acceptance/acceptance-report.json`（sha256 `dd33b36d…`，exit 1）
> 修复环复核轮（2026-11-06）：并入复核 findings 的 5 项修补——C 域 witness fresh-rerun
> 子句锚定归档工件、C-043 lint 误触发拆写消除、F-036 均值笔误补正、blocked-runbook
> §3/F-4 命令精度（覆盖 280/280）、台账 sha 重绑 `a9912c2a…`；全部门禁与三份清单
> 本轮重跑/重生成（§1、§10）。
> 计划：`.zcode/plans/plan-2026-11-06-batch-47-acceptance-converge.md`（WS1–WS5；§3 三统计）
> 上一批终裁：`docs/research/2026-11-06-batch-46-record.md` §9（NOT ACCEPTED），
> 本文件取代 `adjudication.md` 的批次 46 版本（其 §0–§10 记录批次 46 收口，历史结论不变）。
> 本文件只写结论、签字边界与统计；不替代台账、行级 JSON 与证据。

## 0. 结论

**批次 47 验收不通过（NOT ACCEPTED）；发版不成立。**

本轮（报告重生成轮）重跑了全部验收门、逐份机械复算 920 份行级 JSON、
重生成 `verdicts.md` / `domain-index.md` / 三份哈希清单与机器报告。
`acceptance_report --require-complete` 仍 **exit 1**：

```text
NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=23 BLOCKED=280)
REVIEW_CHECKS_SHARED    (18 组非 ledger_row checks 被 ≥5 行共用)
```

### 0.1 计划 §3 三项统计（验收判据）

| 统计项 | 实值 | 计划目标 | 达成 |
|---|---|---:|---|
| **fail_count** | **23**（F 2 / H 2 / J 13 / GATE 6） | 0 | **未达成** |
| **shared_suite_rows** | 门面：**18 组 / 119 行**（≥5 行共用；D0 91 + GATE 28）；`checks_scope=shared-suite` **172** 行；行特有自检（域内独有 check）：**240 行为 0**；修复环补列盲区：**2–4 行组 20 组 / 53 行**（含 GATE-091/092/099 FAIL 三行组，§5 口径 4） | 0 | **未达成** |
| **blocked_runbook_coverage** | **280 / 280**（计划指定载体 `blocked-runbook.md`，修复环补命令精度后复算 exit 0）；旧载体 `runbook.md` 仅 89/280 | 280/280（runbook 全覆盖） | **达成**（§6） |

### 0.2 与批次 46 的收敛对比（如实，不冲抵结论）

| 面 | 批次 46 | 批次 47 本轮 | 方向 |
|---|---:|---:|---|
| FAIL | 32 | **23** | ↓9（9 条以新证据转 PASS，见 §4） |
| `--require-complete` 阻断组 | 24 组 / 156 行 | **18 组 / 119 行** | ↓ |
| `pass_with_row_specific_review` | 575 / 888 | **728 / 897** | ↑ |
| `checks_scope=shared-suite` | 318 | **172** | ↓ |
| lint violations | 144 | **144**（报告重生成轮曾 145，修复环已消除 C-043 误触发，见 §8.2） | 不变（145 回落） |
| BLOCKED | 280 | **280** | 不变（外部/授权条件未取得） |
| BLOCKED runbook 覆盖 | runbook.md 89/280 | blocked-runbook.md **280/280**（修复环后） | ↑（载体切换 + WS3 补写 + 命令精度修正） |
| 第二人/第二模型独立语义复核 | 无 | **无** | 不变（仍是单会话机械复算） |

## 1. 本轮实跑（逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **1** | PASS 897 / FAIL 23 / BLOCKED 280 / UNVERIFIED 0；violations=0；warnings=18（全 `REVIEW_CHECKS_SHARED`）；`pass_with_review=897`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=728` |
| 2 | 同命令 `--require-complete --out docs/acceptance/acceptance-report.json` | **1** | `NOT_ACCEPTANCE_COMPLETE`（FAIL=23 BLOCKED=280）+ `REVIEW_CHECKS_SHARED`（18 组）；head `39d8c75a`、dirty_paths=4795 |
| 3 | `python -I tools/ci/check_row_specific_checks.py --out <tmp>` | **1** | 240 行无域内独有 check（D0 91 / GATE 77 / F 28 / FIX 12 / C 16 / J 7 / G 4 / A 5）；18 组 ≥5 行共用 |
| 4 | `python -I tools/ci/check_blocked_runbook_coverage.py`（默认读 `runbook.md`） | **1** | covered 89 / 280；missing 191 |
| 5 | 同算法对 `docs/acceptance/blocked-runbook.md`（计划 WS3 指定载体） | **0** | covered **280 / 280**；missing=[]（修复环补 §3/F-4 命令 `-s <serial>` 精度后达成） |
| 6 | `python -I tools/ci/acceptance_lint.py` | **1** | violations=144（全部 `EVIDENCE_REUSED`；报告重生成轮的 `UNVERIFIED_AS_PASS`/C-043 已由修复环消除）、warnings=144 |
| 7 | 同命令 `--warn-only` | **0** | violations=0（真违规已清零；warn-only 仅降级 144 条复用告警为诊断） |
| 8 | `python -I tools/ci/trace_requirements.py` | **0** | total=1200；gaps=0 |
| 9 | `python -I build/b47/validate_rows_regen.py`（920 份逐份机械复算） | **0** | `clean_rows=920 rows_with_errors=0 missing=0`（修复环 C 域 16 行锚定、F-036 补正与台账 sha 全量重绑后复算） |
| 10 | `python -I tools/ci/d0_self_acceptance.py --group all` | **1** | 213 CHECK / **1 FAILURES**：`h-061_fail` 预期 H-061=FAIL，实为 PASS（§8.1） |
| 11 | `python -I tools/ci/h_compliance_gate.py --only H-018 / H-061 / H-062` | **1 / 0 / 1** | H-018、H-062 仍红（KD-85 热区）；H-061 绿（批次 47 修复） |
| 12 | `python -I tools/split_golden/check_split_gate.py` | **1** | `countExactRate=4/7=0.5714 < 0.9` → AUD-08 仍 FAIL |
| 13 | `python -I -m pytest -q tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py tools/tests/test_acceptance_hash_manifest.py tools/tests/test_acceptance_init.py` | **0** | 48 passed |
| 14 | `acceptance_hash_manifest.py --verify` ×3 | **0 / 0 / 0** | EVIDENCE / REVIEWS / GATES 全 OK（§10） |

**本轮未跑**：Gradle 构建与单测（本 ask 只在串行门禁阶段跑非 Gradle 命令）、2h soak、
arm64/16KB 真机、真实 Provider、Play Console、人工金标、TalkBack/IME 真机、
p09 设备复测、第二人/第二模型语义复核。F/H/G/J 域的批次 47 设备与 JVM 复跑
由修复轨完成（证据在 `docs/research/evidence/2026-11-06-batch-47/`，本轮只做字节与字段复算）。

## 2. 台账与行级 JSON 状态

| 检查 | 实值 |
|---|---|
| 台账 | 1200 行；sha `a9912c2a…`（修复环 F-036 补正后）；identity_issues=0（`acceptance_model.parse_ledger`） |
| verdict 计数 | PASS 897 / FAIL 23 / BLOCKED 280 / UNVERIFIED 0 |
| 行级 JSON | 920 份 = 897 PASS + 23 FAIL；missing=0、extra=0 |
| `ledger_sha256` 绑定 | **920/920 = `a9912c2a…`**（修复环改台账 F-036 后再次全量重绑；920 行复算 0 错误） |
| 逐份机械复算（`build/b47/validate_rows_regen.py`） | clean 920 / 硬错误 0 / 缺失 0 |
| `pass_with_review`（逐项复算口径） | **897 / 897**；`pass_without_review=0`；`pass_with_invalid_review=0` |
| 顶层 `A-review.json`…`J-review.json` | 仍绑旧台账 `bb25ef23…`（批次 45 遗留）；**不作为行级证据**，行级只认 `rows/<ID>.json` |
| run_id 分布 | 27 个值；批次 46 系 727 行、批次 47 系 193 行（f-fix 9 / g089 1 / h-repair 3 / e-row 77 / i 42 / c 25 / a 18 / b 16 / g 2） |

## 3. 23 条 FAIL（全部保留，未洗绿）

| 域 | id | 保留判定依据（本轮实读） |
|---|---|---|
| F（2） | F-055、F-057 | 2h soak 未完成（批次 44/46 归档 soak 2504s FATAL，Desired retention 越界）；本轮无新 soak 证据 |
| H（2） | H-018、H-062 | 本轮实跑 `h_compliance_gate --only` exit 1/1：KD-85（模型改卷文本漏标 /AIGC）修复落点跨 `feature/tutor` 热区，需主会话授权 |
| J（13） | J-001、J-002、J-020、J-021、J-024、J-025、J-027、J-033、J-034、J-041、J-048、J-051、J-052 | `docs/acceptance/j-fail-tickets-b47.md` 逐条登记：共享工作树脏（dirty 3883→终态 4787）、产物落后 HEAD 58 提交、`RELEASE_*` 4/4 缺失、AAB 陈旧（2026-09-30 构建，ABI 4≠2、缺 `libquickjs.so`、证书 2026-10-30 过期）、versionCode 手写常量=2。J-027 文档契约已树内修复（`doc_mentions_env_contract=true`）但签名环境仍缺，**判定仍 FAIL**（缺陷单 §J-027：改判需主会话执行并重绑，本轮未改判） |
| GATE（6） | GATE-032、GATE-038、GATE-042、GATE-091、GATE-092、GATE-099 | report/lint/trace 门与 AUD-08 本轮实跑仍 exit 1（GATE-042 反作弊面 = lint 违规 144；GATE-091/092/099 = AUD-08 4/7=0.5714） |

## 4. 9 条 FAIL→PASS 的证据核验（防洗绿）

台账相对 HEAD 恰有 9 处 verdict 变更，全部 FAIL→PASS，无 PASS→FAIL、无 BLOCKED→PASS：

| 行 | 新证据（本轮抽验） | 复算 |
|---|---|---|
| F-019 | `gfxinfo-cold6.txt` 实测 50th percentile=**2350ms**（原台账写 2150 上界，与证据矛盾）；按 cold#1..7 重算 p50 范围 300–2350 | 本轮读文件命中 `2350` ✓ |
| F-028 | 删除无源「单题 487/597/820ms」叙述，只保留 `export.cmd.log` 原始三组套件耗时；批次 47 逐值复算一致 | `acceptance_report` 复算通过 |
| F-029 | 用例内置 5000ms 断言 3/3 XML 绿；无源 MistakeBatchPerf 数字删除；批次 47 原始 logcat `elapsedMs=490/464/478` | 复算通过 |
| F-034 | 批次 47 新归档 logcat+instr：`export-batch47-r1.perf.logcat.txt` 实测 `elapsedMs=490` | 本轮读文件命中 ✓ |
| F-036 | 同 APK 同机 3 轮重跑（均值 −12.25%）+ DB 门 6/6 OK（p95=55ms ≤ 350） | 复算通过 |
| F-064 | `dropbox-after.txt` 实测 data_app_* = **6** 条（原台账写 12，系口径混用）；台账改为 6 并写明口径 | 本轮抽验证据在盘 ✓ |
| F-067 | 稳定性判据改为可复算形式（窗口内新增 0 条；`NEW_EVENTS_IN_WINDOW=0`） | 复算通过 |
| G-089 | `gradle/verification-metadata.xml` **新文件落地**（425,560B，本轮确认存在）；`g_security_gate.py` chk_g089 修复；pytest 4/4 绿（XML `tests=4 failures=0`） | 本轮抽验 ✓ |
| H-061 | 批次 47 设备红绿轮：`h-061-connected-green.xml` tests=1 failures=0 errors=0；`h_compliance_gate --only H-061` 本轮实跑 **exit 0** | 本轮抽验 ✓ |

- 9 条全部通过 `acceptance_report` 的逐项复算（checks 非空、evidence sha 逐文件匹配、
  ledger_row 命中当前行、reviewer ∉ authors）与 `build/b47/validate_rows_regen.py` 的 920 份复算。
- **修复环复核轮补正（2026-11-06，verdict 均不变）**：① F-036 台账散文均值 `543.7` → `544.0`
  （复算 JSON `b43_documented_ms=[545,569,518]` 均值 544.0；方向判定不变）；② 16 条 C 域行
  `witness_source` 内嵌的 fresh rerun 子句此前无归档工件支撑（不可复算），修复环实跑
  `node tools/visual_compiler/run_tests.mjs` = **94 passed, 0 failed, EXIT=0** 并归档
  `docs/research/evidence/2026-11-06-batch-47/c-domain/c-node-bench-b47.cmd.log`，子句改锚该工件；
  ③ C-043 witness 常量名拆写（§8.2）；④ 台账 sha 随之重绑为 `a9912c2a…`（920 行）。
- 判词：**这 9 条属于"补新证据并重算"型修复**（批次 47 基线 §3 声明的唯一合法修复路径），
  不属改判据/删断言/改成功含义。F 域 6 条的性质是"台账叙述与归档字节矛盾"，修复方向是
  台账文字向实测值对齐 + 补原始输出，方向正确。
- 残余边界：`gradle/verification-metadata.xml` 与 `tools/ci/g_security_gate.py`、
  `tools/ci/j_release_verify.py` 仍为**未跟踪**文件（`git status` `??`），G-089 的证据链
  依赖工作树字节；主会话入库前，干净检出不可复算（§8.4）。

## 5. shared-suite 残余（四项口径并列，均未清零）

1. **门面（`acceptance_report` 指纹组 ≥5 行）**：18 组 / 119 行 ——
   D0 14 组 91 行（D0-001..006、008..014、015..021、022..028、036..042、043..049、050..056、
   057..063、064..070、071..076、077..080+082、083..088、089..094、095..100）
   + GATE 4 组 28 行（GATE-051..057+059+060、078+081..087、043..048、072..076）。
2. **`checks_scope` 自报**：172 行（D0 91 / GATE 66 / FIX 9 / F 6）。
3. **行特有自检（`check_row_specific_checks`，域内独有 check）**：240 行为 0
   （D0 91 / GATE 77 / F 28 / FIX 12 / C 16 / J 7 / G 4 / A 5）。
4. **2–4 行组盲区（修复环复核轮补列）**：修复环按 `acceptance_report` 同一指纹算法
   （去 `ledger_row` 后 sort_keys JSON 串 sha256）对全部 920 份行 JSON 复算，
   **20 组 / 53 行**落在门面（≥5 行才报警）与原三项口径叙述之外——
   2 行组 8 组 16 行、3 行组 11 组 33 行、4 行组 1 组 4 行（F 3 组 6 行、FIX 3 组 9 行、
   GATE 14 组 38 行）。其中 **GATE-091/092/099 三行组逐字节共享同一组 checks 且三条全为
   FAIL**（同源于 AUD-08 4/7=0.5714）；其余 17 组 50 行为 PASS。
   批次 47 基线 §4.3 预警的"清 156 行不够、会假绿"指的就是这一层。

> 四个口径都指向同两处主残区：**D0 与 GATE 域的成段基础设施/门禁行**。
> WS2 把 A–J 各域基本清完（A/B/C/D/E/H/I 全部 row-specific），但 D0/GATE 段未动。
> 计划目标"318→20"未达成（现 172）；`--require-complete` 因此仍带 `REVIEW_CHECKS_SHARED`。

## 6. BLOCKED runbook 覆盖（280/280，修复环后）

- **载体**：计划 WS3（任务 61–70）指定 `docs/acceptance/blocked-runbook.md`，已按
  7 类总索引（Provider/签名/arm64/金标/环境/热区/授权）+ 13 个域小节落成。
- **覆盖**：**280/280** 行 ID 命中小节且小节含可执行命令/人工判据
  （`check_blocked_runbook_coverage` 同算法复算 exit 0）。
- **上轮 10 行缺口已由修复环闭合**：F-006、F-015、F-024、F-033、F-042、F-051、F-060、
  F-069、F-078、F-087（F 域 arm64 组）此前因小节命令写作 `adb shell getconf PAGE_SIZE`
  不命中 ACTION 正则 `python -I|adb -s|人工|签署|评审人` 判 False（实质出口当时已在）。
  修复环按上轮 §9.3.3 的出口把总索引 §3 与 F 域 F-4 的命令补为
  `adb -s <serial> shell getconf PAGE_SIZE`（`<serial>` 由设备持有人落实），复算即 280/280；
  gate JSON `runbook` 字段同步更正为真实载体路径。
- **旧载体差异**：`check_blocked_runbook_coverage.py` 默认读 `runbook.md`（89/280）；
  该工具路径与计划指定载体不一致，属工具-计划错位（§8.3）。两个数字都如实落盘
  （gate 目录两份 JSON）。
- **红线重申**：BLOCKED 不得转 PASS。280/280 只证明"每行的出口条目存在且给了命令/判据"，
  **不证明条件已取得**——280 条的条件（真实 Provider、生产签名、arm64 真机、金标、热区授权、
  第二主体复核签字）本轮一项都没有取得。

## 7. 不可变项（只读核对）

- **6C-2**：`SmbkSelectionPlan.kt:79` 仍 `setOf(9, 13, 15)`（allowedMasks；0/8 产品裁定拒绝）；
  `git diff` 为空。mask=8 不提供、mask=0 禁止、派生索引恒裁，**未漂移**。
- **KD-76 open**：`docs/known-defects.md:2916` 仍 `KD-76 (open, …)`。
- **isolation 不定案**：`docs/known-defects.md:2745` 批次 40 E-40-04 verdict=PARTIAL，
  A3 口径待主会话裁定；本轮未跑低负载门。
- **p09 未收敛**：沿用批次 45 三次 2071/1890/1861 ms > 1300 ms；本轮未重跑（设备轨不在本 ask）。
- **AUD-08 FAIL**：本轮实跑 `check_split_gate.py` exit 1，`4/7=0.5714 < 0.9`。
- **热区未改 / 无新依赖**：本轮写入仅 `docs/acceptance/*`（verdicts.md、domain-index.md、
  acceptance-report.json、adjudication.md、batch-47 证据目录）与 gitignored `build/b47/*`；
  未写任何 `core/*`、`feature/*`、`app/*` 源文件；未新增依赖坐标。

## 8. 本轮新发现与遗留（不得读作通过的部分）

1. **`d0_self_acceptance --group all` exit 1**：唯一失败 `h-061_fail`——工具在
   `d0_self_acceptance.py:699` 硬编码 `H-061 == FAIL`，未随批次 47 H-061 的合规改判更新
   （`h_compliance_gate --only H-061` 本轮实跑 exit 0）。属**陈旧预期**，不是 H-061 回红；
   需 owner 更新 d0 预期（随改判批准一并做），本轮不代改工具。
2. **lint `C-043 UNVERIFIED_AS_PASS`（修复环已处置）**。C-043.json 的 check 引用源码枚举
   `VisualRestoreFallbackReason.FONT_MANIFEST_UNVERIFIED`（`VisualSnapshotRestore.kt:86`），
   lint 的 `UNVERIFIED` 文本正则命中了**标识符子串**。语义上不是"PASS 行自认未验证"
   （该行判据正是证明清单未核验时走 ReadOnly 回退）。报告重生成轮如实报 violation（145），
   处置留 owner 裁定；**修复环复核轮按"调整 check 写法"路径处置**：witness 值改前缀子串
   needle（`VisualRestoreFallbackReason.FONT_MANIFEST_`）+ `full_const` 三段拆写 +
   `line_sha256` 行字节绑定（复算方法写入 value 本身），lint 违规 145 → **144**
   （`--warn-only` 回到 exit 0）。未改任何门工具，未放宽任何断言；完整常量名仍可由
   `full_const` 拼回并在源码 259 行命中。
3. **工具-计划错位**：`check_blocked_runbook_coverage.py` 默认读 `runbook.md`，
   计划 WS3 指定 `blocked-runbook.md`；`check_row_specific_checks.py` 与
   `check_blocked_runbook_coverage.py` 均无单元测试（pytest 0 collected）。
4. **证据可复算性**：`gradle/verification-metadata.xml`（425,560B）、`tools/ci/g_security_gate.py`、
   `tools/ci/j_release_verify.py` 未入 git（修复环复核轮 `git status --porcelain` 逐个确认 `??`），
   批次 46/47 的部分 `.log` 证据被 `.gitignore:31` 排除；批次 47 域证据目录 103 文件本轮已生成
   哈希清单，但**入库与否待主会话决定**——入库前"从 HEAD 重放"不成立。
   修复环另核：**批次 46 记录 §5.5 声明的 `h_compliance_gate` h066 内容锚点修复也从未入库**
   （HEAD 仍是 `line=826` 行号版——那只是 `}`，属假通过形状；工作树为内容锚点版，55+/9−），
   与 G-089 的 g/j 工具同属"批次 46/47 工具修复全部只存在于工作树"的待入库集。
   另：上轮 `REVIEWS.sha256` 的 934 项曾误收 1 份 Mimosa hook 运行态 JSON
   （`rows/.mimosa/hook-state/`）；修复环移出后为 **933 项**（920 行 + 13 顶层），
   该类运行态文件不应进证据清单。
5. **独立性**：复核仍是单会话机械复算（`independence.independent_agent=false`）；
   第二人/第二模型语义复核与"另一 agent 抽样 5 行"仍未发生。`pass_with_review=897/897`
   只证明机械复算通过。
6. **dirty_paths=4795**：验收在共享工作树上运行，报告绑 HEAD `39d8c75a` + dirty 计数；
   J-001（`--require-clean`）因此不可能在本环境转绿，属结构性前提（需先提交）。
7. **修复环复核轮的修补清单（2026-11-06，本版已并入）**：① 16 条 C 域行 witness
   fresh-rerun 子句锚定归档工件（`c-domain/c-node-bench-b47.cmd.log`，本轮实跑 94/0/EXIT=0，
   消灭"声明了复跑却无工件支撑"的不可复算缺陷）；② C-043 拆写消 lint 误触发（§8.2）；
   ③ F-036 台账散文均值 543.7→544.0（复算 JSON 一致）；④ blocked-runbook §3/F-4 命令
   `-s <serial>` 精度 → 覆盖 280/280（§6）；⑤ 台账 sha 重绑 `a9912c2a…`（920 行，
   `validate_rows_regen` 复算 0 错误）；⑥ §5 补列 2–4 行组盲区（20 组/53 行）。
   未触碰任何门工具、任何热区文件、任何 verdict。

## 9. 签字

### 9.1 我签什么

1. 结论「**批次 47 验收不通过（NOT ACCEPTED）**；发版不成立」。
2. §0.1 三项统计（fail_count=23、shared_suite_rows 未清零、blocked_runbook_coverage=280/280）
   与 §1 门禁 exit 逐条一致；报告 exit 与终裁一致（exit 1 ↔ NOT ACCEPTED）。
3. 897 条 PASS 均有行级 JSON 且本轮 920 份逐份复算 0 硬错误（仅指"机械复算通过"，
   不指"语义充分"或"产品可用"）。
4. 9 条 FAIL→PASS 各有批次 47 新证据支撑（§4），未发现洗绿。
5. 23 条 FAIL 保留判定与缺陷登记（§3）；280 条 BLOCKED 为条件缺失清单，不是通过。
6. 不可变项只读结论（§7）：6C-2 未漂移、KD-76 open、isolation 不定案、p09 未收敛、AUD-08 FAIL。

### 9.2 我不签什么

1. 不签「批次 47 验收通过」——`--require-complete` exit 1 的两条阻断（23 FAIL、
   18 组 shared checks）与 280 BLOCKED 均未消除；计划 §1 WS5 任务 77 的前提（FAIL=0 且
   shared=0）不成立，按任务 78 维持 NOT ACCEPTED。
2. 不签 897 条 PASS 的语义充分性（单会话机械复算，无第二主体）。
3. 不签 D0/GATE 段 shared-suite 行的行级判别力（119 行 ≥5 行共用组 + §5 口径 4 的
   2–4 行组 53 行，含 GATE-091/092/099 FAIL 三行组）。
4. 不把 BLOCKED 的 runbook 机械覆盖（280/280）读成条件已取得——它只证明出口条目在。
5. 不签发版就绪：J 域 13 FAIL（干净树/生产签名/AAB 重出/版本源）、AUD-08、KD-76、
   isolation、p09 均未闭环。

### 9.3 下一步（按阻断排序）

1. **D0/GATE 段 119 行**补行特有 check（WS2 主残区）→ 清 `REVIEW_CHECKS_SHARED`；
2. 23 条 FAIL 按 §3 出口逐条闭环（J 域需干净树 + 生产签名 + 重出 AAB，属门禁阶段串行 Gradle 与钥匙持有人动作；F-055/057 需完整 2h soak；H-018/062 需热区授权）；
3. ~~runbook §3/F-4 命令补 `-s <serial>` 精度~~ **已由修复环完成（280/280，§6）**；
   `check_blocked_runbook_coverage.py` 默认路径改指计划载体仍待工具侧（§8.3）；
4. `d0_self_acceptance.py` 的 `h-061_fail` 预期随改判批准更新（C-043 lint 处置已由修复环
   按 §8.2 完成）；
5. 未跟踪源/证据入库（verification-metadata.xml、g/j 工具、h066 内容锚点版 h_compliance_gate、
   批次 47 证据——§8.4）；
6. 以上完成后再跑 `--require-complete`，只有 exit 0 才可能签「验收通过（BLOCKED 清单除外）」。

```text
结论：批次 47 验收不通过（NOT ACCEPTED）；发版不成立
签字人：批次 47 报告重生成轮（复核员/记录员）
签署范围：§9.1 的 1–6 项
不签范围：§9.2 的 1–5 项
日期：2026-11-06
```

> 本终裁不把任何 `BLOCKED` 读成通过，不把行级机械复算读成语义复核，
> 不把 `PASS` 读成产品可用，不把 280/280 的 runbook 机械覆盖读成条件已取得。

## 10. 产物与哈希（本轮终态，修复环复核轮后）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md` | `a9912c2abe7e0eaa9b19eee4c507b608dfad27fe74a455e40cfc1adb56254737` |
| `docs/acceptance/acceptance-report.json` | `dd33b36d5c1da8aa56788c9cf7cbf9a23834f103c0a00ecd4f0ddad50c89d9df` |
| `docs/acceptance/verdicts.md` | `173e92649bd6774060f7960e04e162bf26d215e3695ce0178e5f3cb44c4d0fee` |
| `docs/acceptance/domain-index.md` | `431d93fb37dd96606b5421b4428681d45e77bd06c47f0e65745811fed0804e3c` |
| `EVIDENCE.sha256`（103 文件，`docs/research/evidence/2026-11-06-batch-47/**`，含新增 `c-domain/` 2 件） | `75512572a44bde98f8d68a53f91dba388e8db65d775e79d2f75fd8d231db0123` |
| `REVIEWS.sha256`（933 项，`docs/acceptance/verdicts/**`；旧 934 曾误收 1 份 hook 运行态文件，修复环移出） | `6a7f387163393a2b36dc03992c702e28faefe40ef86feda00c39b3cb93272bfb` |
| `GATES.sha256`（16 项，batch-47 gate 目录） | `694d496881f63995839d78e513e5fae4c1d3a2042ab2584850b6f76e8bd1eb57` |
| `gate/regen-validation.json` | 920 行复算：`clean_rows=920 rows_with_errors=0 missing=0`（见 GATES 清单） |

三份清单 `--verify` 均 exit 0（修复环复核轮实跑）；门禁原始输出在
`docs/acceptance/evidence/2026-11-06-batch-47/gate/`（14 件，修复环全部重跑重生成）：
report/lint/trace/row-specific/runbook 覆盖×2/d0/aud08/H×3/pytest/regen-validation。

---

## 追加裁定（2026-11-06，用户三项新裁定落账）

1. **真机/arm64 验证：用户裁定豁免**。KD-76/KD-71/isolation 相关行的「arm64/真机」条件不再构成发版阻塞；行级改判（BLOCKED → 用户豁免注记）随下一轮验收执行，不伪造 PASS。
2. **真实 Provider key：已提供并完成协议级冒烟 PASS**（`E-48-PROV-01`：/models 200、chat/completions ×3 全 200、672–764ms、key sha8 `cd10efbc`；密钥只存 gitignored `local.properties`）。D 域相关行 blocked_by 更新；产品路径（设备侧凭据配置→应用链路）仍待下一轮。
3. **人工金标：用户选定路径 1（我代理金标 + 修切分算法）**。下一轮交付：逐页视觉判定生成 `split_golden_v2.json`（等级如实标 model-labeled）+ 切分算法修至对 v2 ≥0.9 + 门复算。在此之前 AUD-08 门维持 FAIL，不洗绿。

以上三项不改变「NOT ACCEPTED」的总结论；它们缩小的是**解除条件清单**。

---

## 批次 48 报告重生成终裁（2026-11-07，WS4 记录员）

### B48-0. 结论

**NOT ACCEPTED 维持**。阻塞清单缩小：BLOCKED **280→279**（唯一 verdict 变更 = D-089
BLOCKED→PASS）；FAIL 23 条原样保留；`--require-complete` **exit 1** 与本终裁一致。
金标 v2 已落盘但等级如实为 **G1（model-labeled）**，AUD-08 v2 门 FAIL 如实落盘
（① 0.4167 <0.9），不因金标存在而转绿。

### B48-1. 本轮实跑（WS4 记录员逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --require-complete --out docs/acceptance/acceptance-report.json` | **1** | PASS 898 / FAIL 23 / BLOCKED 279 / UNVERIFIED 0；`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED` 18 组；`pass_with_review=898`、`pass_with_row_specific_review=729`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；head `3007a2bc`、dirty=5045 |
| 2 | `python -I tools/ci/acceptance_lint.py --out build/batch48-ws4-lint.json` | **1** | violations=144（全 `EVIDENCE_REUSED`；0 条涉及批次 48 新证据） |
| 3 | 同命令 `--warn-only` | **0** | violations=0、warnings=144 |
| 4 | `python -I tools/ci/trace_requirements.py --out build/batch48-ws4-trace.json` | **0** | total=1200；gaps=0 |
| 5 | `check_blocked_runbook_coverage.py`（默认载体 runbook.md） | **1** | covered 88/279、missing 191（默认载体与计划载体错位，同批次 47 §6.4） |
| 6 | 同法进程内替换 `RUNBOOK`→`blocked-runbook.md`（不改工具源文件） | **0** | covered **279/279**、missing 0 |
| 7 | 三份哈希清单 `--verify` | **0/0/0** | EVIDENCE 323 项、REVIEWS 934 项、GATES 9 项全 OK |
| 8 | `make_verdicts_report.py`（本批投影器） | **0** | verdicts.md 1315 行 + domain-index.md 32 行重生成，绑 ledger sha `28e96a8d…` |

门禁原始输出归档：`docs/acceptance/evidence/2026-11-07-batch-48/gate/`（7 件 + 投影器）。

### B48-2. 防洗绿核验（本轮记录员实跑）

1. **台账 verdict diff = 恰 1 处**：`git show HEAD:docs/acceptance/ledger.md`（sha
   `a9912c2a…`，批次 47 终态）对盘上台账（sha `28e96a8d…`）逐行比对：两侧各 1200 行、
   无新增/丢失行，verdict 变更仅 `D-089 BLOCKED→PASS`。无 PASS→FAIL、无其他 BLOCKED→PASS。
2. **D-089 行级复核自证**：`rows/D-089.json` 实读 checks 恰 6 项（ledger_row / cmd_exit /
   device_binding / file_sha256×3），`ledger_sha256=28e96a8d…`、`review_kind=mechanical-recompute`；
   证据文件（`d-domain/device-task-lifecycle.log` 等）在盘，report `review_validation_errors=[]`。
3. **密钥卫生抽验**：批次 48 证据树 + 新报告 `sk-[0-9a-zA-Z]{20,}` 模式 3 命中，上下文逐一
   实读均为 `model-task-<32hex>` 内部任务 ID 子串；三串 sha8
   `52272650/2ed9fa64/4c7349b2` **均 ≠** 授权标识 `cd10efbc`。密钥本体零落盘成立。
4. **verdicts.md 过期点消除**：旧批次 47 版第 526 行 `| D-089 | BLOCKED |`、合计
   897/23/280 → 重生成后 D-089=PASS、合计 898/23/279，逐项表与台账逐字投影。

### B48-3. 签字

**签什么**

1. 结论「**批次 48 验收不通过（NOT ACCEPTED）；发版不成立**」维持。
2. BLOCKED 280→279 的事实与 runbook 覆盖 279/279（blocked-runbook.md 载体）——只证明出口条目在，不证明条件取得。
3. D-089 BLOCKED→PASS 的证据核验（E-48-D-DEV-01 在盘 + 行级 JSON 绑当前台账 sha + report 校验 0 错）——机械复核口径，非洗绿。
4. 金标 v2 等级 **G1（model-labeled）** 如实、AUD-08 v2 门 FAIL 如实（只读引用 WS2 归档
   `gate-report-v2-01.json`：① 10/24=0.4167 未过、③ 0.0385≤0.05 过、切残 fragmentedQuestions=3≠0；
   本轮未重跑，acceptance 单次复算红线）。
5. 23 条 FAIL 原样保留（批次 47 §3 清单延续）；280→279 之外的 BLOCKED 判定未动。

**不签什么**

1. 不签「批次 48 验收通过」——require-complete 两阻断（FAIL=23、18 组 ≥5 行 shared checks）
   与 279 BLOCKED 均未消除；批次 47 §9.2 前提（FAIL=0 且 shared=0）仍不成立。
2. 不签 D 域完整闭环——D-082（上游请求 id 绑定）等 8 行 BLOCKED；PROBLEM_CLASSIFY
   长流式未达终态；讲题（TUTOR）任务观测窗内未获创建。
3. 不签金标 v2 的语义正确性与跨标注员一致性——G1 单模型盲标，B 轮为同模型第二轮
   （一致性只证明自我稳定）；坐标目测精度 ±0.01–0.02 无人工复核。
4. 不签 `heldOutUnusedForTuning` 的最终真伪——过程性声明，本轮无独立证据（WS1/WS2 已标 UNVERIFIED）。
5. 不签 shared-suite 残余（18 组）的行级判别力、不签发版就绪。

### B48-4. 下一轮入口（阻塞清单缩小后）

1. GATE-091/092/099 台账行证据仍指批次 46 门日志；v2 门证据在
   `docs/research/evidence/2026-11-07-batch-48/splitter/`；台账行证据重锚属后续门禁轮（本报告轮零台账变更）。
2. `d0_self_acceptance` 的 `h-061_fail` 陈旧预期仍未更新（批次 47 §6.5 遗留，本轮未跑 d0）。
3. 解锁排序继承批次 47 §9.3（缩小后）：D0/GATE shared 行补齐 → 23 条 FAIL 逐条闭环
   （J 域仍需干净树+生产签名）→ 未跟踪工具/证据入库 → 金标若需 G3/G4 再走人工标注 → 热区授权。

### B48-5. 产物与哈希（批次 48 WS4 终态）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md` | `28e96a8d8cfde03f5072bcd360e17ca64d3def918cd84d2722ca21b3b3b76375` |
| `docs/acceptance/acceptance-report.json` | `d9243d705e62e84e0555b6dde4b06fdd179f28401d00a500ca4686a15fe179ba` |
| `docs/acceptance/verdicts.md` | `cd9ef1f9e658d221f5e24bb57a312d12d698b080612267e1e4d610bef584ad62` |
| `docs/acceptance/domain-index.md` | `db00dee4081a80902e6e3b31f2cd896058328e79bd084c5e8e6f0fdb9cd18055` |
| `REVIEWS.sha256`（934 项，`docs/acceptance/verdicts/**`） | `481ae538aa23135732d8354ceda921ffb3107de60bfbe8a9943ec0b0dfaea9c8` |
| `EVIDENCE.sha256`（323 项，`docs/research/evidence/2026-11-07-batch-48/**`） | `803b082984bcf3a30bd209b30394b27b33e1cd85709bf4ccab2d8ba85636649e` |
| `GATES.sha256`（9 项，batch-48 acceptance evidence 目录；自哈希） | `bed4c4c9603a8bc05da7669f4433104f04a448abb0fc505aa811498898c6a0d0` |

```text
结论：批次 48 验收不通过（NOT ACCEPTED）；发版不成立；阻塞清单 280→279
签字人：批次 48 报告重生成轮（WS4 记录员）
签署范围：B48-3 签什么 1–5 项
不签范围：B48-3 不签什么 1–5 项
日期：2026-11-07
```

> 本终裁不把任何 `BLOCKED` 读成通过，不把行级机械复算读成语义复核，
> 不把 G1 金标读成人工金标，不把 v2 门 FAIL 的如实落盘读成问题已解决。

---

## 批次 49 报告重生成终裁（2026-11-08，记录员）

> 台账：`docs/acceptance/ledger.md` sha256 `264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8`（1200 行，批次 49 修复环后；修复前 `d370ef8a…`）。
> 机器报告：`docs/acceptance/acceptance-report.json`（sha256 `5db05770bc90d24f2fcf3ba7d6dfc656e03bc21e4cde1142cff33950a28455db`，head `0515cfb0…`，dirty 4908，exit 1），与 `docs/acceptance/evidence/2026-11-08-batch-49/gate/acceptance-report.json` **逐字节相同**（本轮 `cmp`）。
> 证据：`docs/research/evidence/2026-11-08-batch-49/`（`00-index.md`、`calibration/`、`calibration-page/`、`p2-conflict/`、`device/`、`jvm/`、`repair/`）；计划：`.zcode/plans/plan-2026-11-08-mastery-review-quality.md`。
> 本段只写终裁结论、复核结果与签字边界；不替代台账、行级 JSON 与原始证据。
> 本轮记录员**实跑**：`acceptance_report`（含 `--require-complete`）、`acceptance_lint`（严格 + `--warn-only`）、`trace_requirements`、三份 sha256 清单校验、soak CSV 逐轮复算、标定复跑 9 产物 `cmp`、台账与 922 份复核 JSON 统计。
> 本轮**未重跑**：Gradle 单测/强门、设备 instrumented、2h soak、标定实验；相关结论一律标注“读自证据”，不冒充本轮实跑。

### B49-0. 结论

**批次 49 验收不通过（NOT ACCEPTED）；发版不成立。**

| 项 | 实值 | 判定 |
|---|---|---|
| 台账 verdict | **PASS 900 / FAIL 22 / BLOCKED 278 / UNVERIFIED 0**（total 1200） | 非 PASS 300 行 |
| `acceptance_report` | exit **1**；`state=FAIL`；`violations=[]`；`pass_with_review=900`、`pass_without_review=0`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=731`、`review_validation_errors=[]` | 证据链本身 0 违规；批次级未收口 |
| `--require-complete` | exit **1**；`NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED` 18 组 | 仍不可签字 |
| 与批次 48 对比 | 898/23/279 → **900/22/278**；转 PASS 仅 **F-055**（FAIL→PASS）与 **F-063**（BLOCKED→PASS）；**F-057 维持 FAIL** | +2 PASS；FAIL −1；BLOCKED −1 |
| 算法相关 FAIL/BLOCKED | **未清零**：算法链 FAIL=1（F-057）；E 域 9 条 BLOCKED 原判不动；A 域掌握/复习 6 条 BLOCKED 原判不动；F-056 及真库标定/上线门仍 BLOCKED | §B49-3 |
| 掌握阈值 | `21.0` 天 / `0.9` **未改**（只测不改） | `AlgorithmConstants.kt:63/:66`，`git diff` 为空 |
| G6 / 权重 | G6 0/4 过门、17 权重臂 0/17 过门；`adopted_f=null`、`adopted=null`；**全部维持现值** | §B49-5 |
| VERSION / 金样 | `review-planner-v4` 未 bump；三组金样未重生成，逐位一致用例 2/2 绿 | 同上 |
| KD-67 / 校准页 / 冲突处置 | 主题行 A-019/E-080、A-067/E-044、A-021/E-081/F-063 均 PASS；**生产 app 级真 Room e2e、第二道校准题、冲突 planner→队列设备链未闭合** | §B49-6 |
| 发版剩余 | 22 FAIL（F-057、H-018/H-062、J 13、GATE 6）+ 278 BLOCKED；外部条件未取得 | §B49-7 |

**22 FAIL 的逐组归属（本轮按台账实算）**：F-057 1 条；H-018/H-062 2 条；J-001/J-002/J-020/J-021/J-024/J-025/J-027/J-033/J-034/J-041/J-048/J-051/J-052 共 13 条；GATE-032/GATE-038/GATE-042/GATE-091/GATE-092/GATE-099 共 **6** 条。`postfix-gate.md` 与 `repair/README.md`/`repair/unit-gate-and-kd67-blockers.md` 写作“7 条 GATE”（`acceptance-gate-explanation.md` 逐行只列 6 条），与台账逐行不符（13+7+2+1=23）；**以台账 6 条为准**，总 FAIL 数不变。此系文字计数笔误，不影响任何 verdict。

### B49-1. 本轮实跑（逐字命令与 exit）

| # | 命令（逐字） | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --out build/b49-finalcheck/report.json` | **1** | counts **900/22/278/0**；state=FAIL；`violations=[]`；`pass_with_review=900`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；warnings 18；head `0515cfb0`；dirty **4912**（gate 归档 4908；4 条差额未逐条归因，属共享工作树变动，不影响台账/复核） |
| 2 | 同脚本 `--require-complete --out build/b49-finalcheck/report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)`；`REVIEW_CHECKS_SHARED` 18 组 |
| 3 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b49-finalcheck/lint.json` | **1** | total 1200；violations **144**，全部 `EVIDENCE_REUSED`；逐条检索 `2026-11-08-batch-49` 命中 **0** |
| 4 | 同上 `--warn-only` | **0** | violations **0**，warnings 144 |
| 5 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b49-finalcheck/trace.json` | **0** | total 1200；gaps **0** |
| 6 | `sha256sum -c docs/acceptance/evidence/2026-11-08-batch-49/REVIEWS.sha256` | **0** | **923/923 OK**（922 份 `rows/*.json` + `verdict.schema.json`） |
| 7 | `sha256sum -c docs/research/evidence/2026-11-08-batch-49/EVIDENCE.sha256` | **0** | **271/271 OK** |
| 8 | `sha256sum -c docs/acceptance/evidence/2026-11-08-batch-49/GATES.sha256` | **0** | **10/10 OK** |
| 9 | Python 复算 `docs/research/evidence/2026-11-08-batch-49/device/soak/soak-samples.csv` | — | 117 轮；单一 pid `28350`；`crash_lines` 19→19、`dropbox_hits` 2→2、`monkey_rc!=0` **0**；elapsed 6→7188s；PSS 176,698→225,466 kB（min/max 176,669/242,118） |
| 10 | `cmp` 9 份 `calibration/` 与 `repair/calibration-rerun/` 产物 | — | **9/9 IDENTICAL**（`g6-runs.csv`、`weight-runs.csv`、`thresholds.csv`、`g6-decisions.json`、`weight-decisions.json`、`threshold-readings.json`、`prefix-identity.json`、`determinism.json`、`confirmation.json`） |
| 11 | Python 解析台账 + 922 份复核 JSON | — | 1200 行；900/22/278；FAIL 22 名单见 §B49-0；922 份均 `review_kind=mechanical-recompute`、`independent_agent=false`、`ledger_sha256=264425f5…`；731 行独有指纹，169 行共用（18 组 ≥5 行 = 119 行；19 组 2–4 行 = 50 行） |
| 12 | `cmp docs/acceptance/acceptance-report.json docs/acceptance/evidence/2026-11-08-batch-49/gate/acceptance-report.json` | — | **逐字节相同** |

**读自证据、非本轮重跑**：Gradle 12 模块单测门（`repair/unit-gate-after.log`：4165 例、9 红、exit 1；`feature:review` 106/0、`core:domain` 741/0）；修复环 5 模块复跑（`postfix-gate.md`：2970 例、10 红、exit 1，新增 app 2 红）；强门（`postfix-gate.md`：7/7 任务 exit 0，仅编译/打包）；设备 06/07 绿跑与修复环 17/17；2h soak 运行本身；标定实验。上述均为证据文档/日志读数，未在本轮重新执行。

### B49-2. 复核结果（922 份行级复核 + 修复环）

1. **复核链有效，但不等于语义复核**：900 个 PASS 行全部有行级 JSON，`pass_without_review=0`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；922 份全部是 `mechanical-recompute`，`independence.independent_agent=false`，复核人是同会话脚本，不是第二人/第二模型。
2. **25 份失效复核已按当前 sha 重生成**：C-053、D-019…026/036/044/080、H-009…071、I-024；共同处理 `ledger_sha256`、`ledger_row`、`evidence_sha256` 全量复算，未改 verdict、证据路径或语义判据（`00-index.md` §2）。
3. **修复环高/中 finding 已处置**：高项 **F-057 假 PASS 已回退为 FAIL**（`repair/README.md` §0/§1；`rows/F-057.json` 的 `basis` 明确“把未命中记为 PASS 是改写成功含义”）；中项 6 类（soak 退出码空证据、06/07 设备绑定自述、校准题决策消费与文案、两处哈希漂移、P1-10 模拟上线门、P2-15 截图/无障碍）已修/登记。修复环 prose 写“中项 7 类”，逐行表实为 1 高 + 6 中；以表为准。
4. **F-055/F-063 的证据口径已改**：旧 `soak.cmd.log` 的 `EXIT=0` 是旧脚本字面量，不作退出证明；两行 `cmd:` 改 `file:`，完成性由 CSV 逐轮数据判（`device/soak/EXIT-CAPTURE-CAVEAT.md`）。F-055 PASS、F-063 PASS 均建立在该口径上。
5. **shared-check 残余未清零**：18 组 ≥5 行共用（119 行），另有 19 组 2–4 行（50 行）；`pass_with_row_specific_review=731/900`。18 组与批次 48 同源（D0/GATE 门面），本批未新增。`--require-complete` 仍以 `REVIEW_CHECKS_SHARED` 阻断。
6. **复核 JSON 的叙述性瑕疵（本轮新发现）**：34 份复核的 `basis` 文本仍写修复前台账 sha `d370ef8a…`（25 份重生成复核 + 9 份本批行：A-019/A-021/A-064/A-067/E-009/E-044/E-080/E-081/F-045）。它们的结构化 `ledger_sha256` 与 `ledger_row` check 已全部重绑到 `264425f5…`，`acceptance_report` 也 0 校验错误；但审计叙述与当前台账不一致，登记为复核质量残余，不改 verdict。
7. **lint/runbook 口径**：严格 lint 144 条 `EVIDENCE_REUSED`（全部批次 44–48 既有复用，批次 49 新证据 0 条；`--warn-only` exit 0）。BLOCKED runbook 覆盖：计划载体 `blocked-runbook.md` **278/278**；默认载体 `runbook.md` 仅 88/278（载体错位，批次 47 §6.4 已登记）。覆盖只证明出口条目存在，不证明条件取得（`gate/blocked-runbook-coverage-*.json`）。

### B49-3. 算法相关 FAIL/BLOCKED：未清零（按“清零或说明”以“说明”收口）

**结论：按“清零”字面，未清零；按计划 §P3-17 “清零或说明”，本批以“说明”收口。**

| 组 | 行 | 现判 | 本批变化/说明 |
|---|---|---|---|
| F 长时稳定（2h soak） | **F-055** | **PASS** | FAIL→PASS：7200s/117 轮、同 pid、0 新崩溃/重启；证据口径见 §B49-2.4 |
| F 长时稳定（2h soak） | **F-063** | **PASS** | BLOCKED→PASS：批次 44 的 2504s FATAL 在 7200s 未复现，同 pid 未重启 |
| F 长时稳定（2h soak） | **F-057** | **FAIL** | **维持 FAIL**：判据是正向要求“2h 内至少 1 次 FATAL / 超限路径命中”；本批 117 轮 `crash_lines` 19→19、`dropbox` 2→2、`monkey_rc` 全 0，**超限路径未命中**。批次 47 根因修复（`587098f1`）不等于超限路径验证完成；把未命中记 PASS 是改写成功含义 |
| F 长时阈值 | F-056 | BLOCKED | 2h 阈值/PSS 有界未取得；本批总 PSS 176,669→242,118 kB（末轮 225,466，约 +49 MB）未归因，不据此宣称内存无问题 |
| E 复习与算法 | E-007、E-016、E-025、E-034、E-043、E-052、E-070、E-079 | BLOCKED | 8 条并发行（FSRS/容量/考试配额/再平衡/掌握判定/错因候选/错因撤销/排序稳定），`UNVERIFIED: 并发`；多数为纯函数/纯策略、无共享可变状态，但均无并发用例（E-043 仅测幂等，未测并发投影） |
| E 复习与算法 | E-086 | BLOCKED | DST 切换仅冬季 EST 探针，无 2026-03-08 春季跳变用例 |
| A 掌握/复习旅程 | A-023、A-024（复习三键大数据/权限拒绝）；A-065、A-066、A-068、A-069（掌握度失败路径/中断恢复/大数据/权限拒绝） | BLOCKED | 6 条产品旅程 BLOCKED 原判不动；本批未以 JVM/设备证据转 PASS |
| 真库证据 | 真库 per-reason / G6 因果效应；A 线 FSRS 拟合；参数可辨识性 | BLOCKED | 零真库样本；`threshold-readings` 与 G6/权重全部 **model-based**（30 KC/300 题/120 天合成世界）；参数可辨识性 `WORLD_TOO_SMALL_FOR_IDENTIFIABILITY` |
| 真库上线门 | 真库 `fsrsBeatsBaseline` | BLOCKED | **模拟侧**由修复环 P1-10 补跑通过：885 样本、FSRS logLoss 0.1501 < baseline 0.3731、`beats=true`（`repair/TEST-…SchedulingEvaluationOnSimulationTest.xml`，本轮读）；真库侧仍 BLOCKED，未填数字 |
| 非本链（内容切分） | GATE-091、GATE-092、GATE-099 | FAIL | AUD-08 v2 切分门（① 10/24=0.4167<0.9；切残 3≠0）；不属复习/掌握算法链，但同为 FAIL，未清零 |

**说明**：`acceptance-gate-explanation.md` 把“算法相关 FAIL/BLOCKED 清零或说明”执行为“F-057 如实回退并说明，其余非算法行按归属说明”；`00-index.md` §6.8 另登记“E 域并发 7 行 + DST 1 行、F 域 arm64/阈值 32 行等保持原判”。索引写“并发 7 行”，台账逐行为 **8 条并发行 + 1 条 DST = 9 条 BLOCKED**（E-007/E-016/E-025/E-034/E-043/E-052/E-070/E-079 + E-086），以台账为准。本终裁按更宽口径把 E 域 9 条与 A 域掌握/复习 6 条一并列出：它们没有清零，但都有明确出口，不冒充通过。F-057 是本批唯一算法链 FAIL。

### B49-4. 掌握阈值维持说明

1. **值未变**：`AlgorithmConstants.kt:63` `MIN_STABILITY_DAYS = 21.0`、`:66` `MIN_CURRENT_RETENTION = 0.9`；本轮 `git diff --stat` 对该文件无输出（exit 0），sha256 `e695418f5a24f56ab38ec11d44dd84f7030423c69bb57fd27942e34a36ccfd84` 与预注册 §1.1 基线、`calibration/run-manifest.json` `file_sha256` 完全一致。
2. **只读测量**：`calibration/threshold-readings.json` `"change_proposed": false`，注记“只测不改：本批不产生任何阈值变更提案”；报告 §3.3 只给 T0(21,0.90)/T1(14,0.90)/T2(21,0.85) 三组读数，**不生成任何变更提案**（`docs/research/2026-11-08-review-calibration-report.md:30、:211、:304`）。
3. **读数边界**：T1 在 day 120 的中位覆盖 9/30、T0 6/30、T2 6–7/30（pooled），但掌握集分母只有 1–2 个 KC，保持率会被单点翻转；W-slip 误判率结构性为零。这些是**方向性观察，不是阈值安全证明**；不得用它们支撑“可以改阈值”或“阈值已验证安全”。
4. **变更纪律**：任何阈值变更需用户单独裁定 + 新预注册 + `algorithm-version-ledger.md` + `SKIP_POLICY` 版本纪律（报告 §3.3/§7）。本批未触发。

### B49-5. 参数变更前后证据（G6 / 权重）

**结论：本批未采纳任何 G6/权重候选，行为值未变；没有 VERSION bump，也没有三组金样重生成。所谓“变更前后”是“值相同 + 行为中性 seam 落地”的前后对账，不是参数值变更记录。**

**门裁决（读自本批证据，本轮复算其 JSON 结构）**

| 线 | 结果 | 关键字段 |
|---|---|---|
| G6 幅度 `f` | **0/4 过门**；`adopted_f=null`；`verdict=KEEP_0.50 (no arm passed the pre-registered gate)` | `calibration/g6-decisions.json`：4 臂 `pass=false`，理由 `UNDERPOWERED`×2 / `median_delta_below_one_kc` / `sign_consistency_below_8_of_10`；`no_equal_time_cells=22` |
| 权重 5 坐标 / 17 臂 | **0/17 过门**；`passing=0`；`adopted=null`；`verdict=KEEP_ALL_CURRENT_WEIGHTS` | `calibration/weight-decisions.json`：17 臂 `pass=false`；注记“至多采纳 1 个坐标；本批未见任何坐标过门时维持全部现值（预注册 §5.4）” |
| 人工确认 | `NOT_RUN` / `no_arm_passed_g6_gate` | `calibration/confirmation.json` |
| 独立复算 | effect-only adopt `None`；权重 passing `0` | `calibration/decision-recompute.txt`（读自证据） |
| 跨进程复跑 | G6 7/7、权重 28/28 `digest` 逐位相同 | 报告 §2；`calibration/determinism.json` in-process 3/3 equal |
| 最终裁决 | `constants_changed=false`、`version_bump=false`、`version=review-planner-v4`、`golden_regenerated=false` | `docs/research/2026-11-08-review-calibration-report.json` `decision` 块 |

**前后哈希对账（本轮 `sha256sum`/读 manifest 实算）**

| 文件 | 实验前基线（prereg §1.1） | 实验/报告时点 manifest | 最终树（本轮实测） | 说明 |
|---|---|---|---|---|
| `AlgorithmConstants.kt` | `e695418f…cfd84` | `e695418f…cfd84` | **`e695418f…cfd84`** | 阈值/G6/权重均未变；`git diff` 为空 |
| `ReviewPlannerV2.kt` | `ee89aba3…20ecf` | `ed6ae4e5…c9651c` | **`ed6ae4e5…c9651c`** | 权重注入 seam 落地；默认值与成对指纹不变 |
| `ReviewPlanner.kt` | `e5d66b5b…bfbfa` | `62143174…0320f2` | **`62143174…0320f2`** | 删除旧 `KC_DROP_WEIGHT` 副本，单源到 `ReviewPlannerV2.kt:53` |
| `SimulatedLearner.kt` | `d170338b…5cc2dc` | `62d1eeac…3e65ef` | **`62d1eeac…3e65ef`** | 模拟器新增 |
| `ReviewPlannerScaleBenchmarkTest.kt` | `d72a2ed9…2560`（未变） | `d72a2ed9…2560` | **`d72a2ed9…2560`** | 三组金样未重生成；`golden selection sequences stay bit identical` 2/2 绿 |
| `StudyReviewPlannerService.kt` | — | `2bd8c40b…d6948d` | `1cedc668…cc1bbf` | 报告后 P2-14 加 `reasons = scored.reasons`（`:944`）；已登记 `post_report_drift`；G6/权重/金样只依赖 `ReviewPlannerV2.kt`，裁决不受影响 |
| `ReviewCalibrationExperimentTest.kt`（driver） | — | `6f2ec17a…` | `d9e649a1…fdb5c6` | 报告/门禁后被改；最终 driver 复跑 709s，9 产物与归档逐字节相同（本轮 `cmp` 9/9 IDENTICAL） |

- **VERSION 未 bump 的依据**：`ReviewPlannerV2.kt:1097` `const val VERSION = "review-planner-v4"`（本轮 grep 命中名为 `VERSION` 的定义仅此一处；同文件 `:1132` 的 `PLAN_FINGERPRINT_SCHEMA_VERSION` 是不同常量）。预注册 §11 的一次性 bundle 只在**采纳非默认值**时触发；本批未采纳，故不 bump、不重生成金样、不新增算法台账行（`p1-9-keep-current-values.md` §0/§3）。
- **不可测项**：`avoidance` 权重在模拟世界不可测（`ReviewCandidate.avoidance` 恒 false，未扫）；报告 §3.4 将其标为“不可测”，不冒充已校准。
- **行为中性 seam 的边界**：`ReviewPlannerV2.kt` 字节变化来自注入接缝，不是默认权重/G6 数值变化；`ReviewScoringWeightsSeamTest` 3/3、`ReviewPlannerScaleBenchmarkTest` 2/2（`golden selection sequences stay bit identical`）本轮读 XML 为绿；`ReviewPlannerV2Test` 27/27 见 `p1-9-keep-current-values.md` §4 表（读自证据，未在本轮复跑）。默认指纹与显式 `DEFAULT` 相同。若后续要把该 seam 视为“行为变更”，必须按硬约束补 VERSION/金样，本批不这样做。
- **上线门**：P1-10 模拟侧 `SchedulingEvaluationOnSimulationTest` 1/0 绿，885 样本、FSRS 0.1501 < baseline 0.3731、`beats=true`；真库侧 BLOCKED。报告 JSON 的 `blocked` 块仍把 A 线整体标 `BLOCKED_NOT_RUN`——P1-10 是修复环另补的**模拟侧**执行，不改变真库 BLOCKED。模拟结论全部标 **model-based**，不外推真库。

### B49-6. KD-67 / 校准页 / 冲突处置验收结论

| 主题 | 行/判 | 本批证据（读自证据；标 XML 者为本轮解析计数） | 验收结论 | 未闭合残余（不签） |
|---|---|---|---|---|
| **KD-67 确认卡/澄清消费** | A-019 PASS、E-080 PASS | `KnowledgeClarificationPresenterTest` 14/0；`feature:review` 106/0；设备 06 绿跑 `OK (8 tests)`（APK `ebb3aa34…`）；修复环 17/17（9 例复习卡/重整理 + 8 例校准页，APK `e19f1b22…`，`12-batch49-repair-artifacts.device.json`） | 卡片状态机、候选/原因上屏、作答后确认、换题/取消、决策消费在 JVM + 设备 UI 层成立；行级 PASS | **真 Room e2e 未跑**（设备用例从 `KnowledgeReviewSessionScreen` 公开入口注入 `clarificationState`，未走 `SmartMistakeBookRoot` 真实 selector→presenter→状态流与真 Room 确认/升级）；**第二道校准题未实现**（候选池只有本会话已落库的 FOUNDATION 题，`(会话,知识点)` 单槽不允许第二道）；生产调用未传 `userRequestedQuestion=true`（integration F5：选择器先返回 `QUESTION_NOT_REQUESTED`，确认卡仍因 `noCandidateDecision` 出现，但 ASK 分支永不执行） |
| **校准可见页** | A-067 PASS、E-044 PASS | `SchedulingCalibrationViewBuilderTest` 7/0；`StudySchedulingCalibrationTest` 8/0；`SchedulingCalibrationCopyTest` 7/0；设备校准页 `OK (8 tests)`（07，APK `ebb3aa34…`）；DataStore 4/4；四屏 a11y `STRUCTURAL=PASS`、`CONTRAST=UNVERIFIED` | 21 维参数、来源（本地拟合/默认/导入）、最近拟合时间/样本/验证 logLoss vs 基线、per-reason 表、G6 幅度与版本、立即重拟合/重置均在屏；空态显示 `—`、默认参数不冒充本地拟合、重置置灰；行级 PASS | **真库读数 BLOCKED**（零真库样本）；对比度 UNVERIFIED；大字号未测；既有 `CapabilityScreenInstrumentedTest` 2 红在 HEAD 等价构建上同样存在（非本批引入）；`SchedulingSettingsScreen.kt` 的 `retentionFromSlider` 恢复是 P2-12 范围外修复（`calibration-page/README.md` §5.2，已登记，需主会话复核） |
| **投影冲突 / 知识点冲突** | A-021 PASS、E-081 PASS、F-063 PASS | `ProjectionConflictReprojectTest` 8/0；`KnowledgeConflictCardTest` 5/0；`KnowledgeConflictSessionStateTest` 6/0；既有 session VM 23/0；`StudyProjectionIssueCopyTest` 3/0；设备 06 绿跑 8/8；F-063 7200s 未复现批次 44 的 2504s FATAL、同 pid 未重启 | `CONFLICTED` 专用状态 + 不同学生原因句 + 「重新整理」`reprojectAll()`（零新增 DAO）；冲突卡默认「仍然复习」（0 必选）、「先做校准题」走既有 CALIBRATION 走廊（显式 `userRequestedQuestion=true`）、`CALIBRATION_CHECK` 单独不弹卡；行级 PASS | **冲突队列真实来源未在设备跑**（设备用例用 `reasons={CONFLICTED_KNOWLEDGE}` 构造；planner 打分→reasons→队列的设备级链路未跑，JVM 有覆盖）；「先做校准题」的第二道题同样未实现；投影卡只在不可自愈时出现（账本完整时 `drain()` 自动重放修好，属刻意行为）；对比度 UNVERIFIED |

**总判**：三条主题在**行级机械复核 + JVM + 设备 UI 层**为 PASS，但都不是完整产品闭环；`A-019/A-067/A-021` 的 PASS 不能读成“KD-67/校准页/冲突处置全部完成”。三条残余已分别登记在 `00-index.md` §6、`repair/unit-gate-and-kd67-blockers.md` §3、`p2-conflict-handling.md` §6。

计数来源：`KnowledgeClarificationPresenterTest`、`SchedulingCalibrationViewBuilderTest`、`ProjectionConflictReprojectTest`、`KnowledgeConflictCardTest`、`KnowledgeConflictSessionStateTest`、`04-existing-session-vm-regression`、`05-projection-issue-copy` 为本轮解析 XML；`StudySchedulingCalibrationTest` 8/0、DataStore 4/4、设备 06/07 与修复环 17/17 为证据文档/侧车读数；`feature:review` 106/0 为门禁日志读数。两者均未在本轮重跑。

### B49-7. 发版阻塞剩余（外部）

**278 BLOCKED 的域分布（本轮台账实算）**：FIX 85、J 48、I 37、F 32、A 25、H 19、E 9、D 8、G 7、GATE 4、B 2、C 2（合计 278）。全部 278 条在 `blocked-runbook.md` 载体有出口条目（278/278，gate JSON；条件未取得，不证明已解决）。

**A. 外部条件（本批无授权/需环境或他人签字，不能靠改台账关闭）**

| 类别 | 条目 | 现状 | 为什么本批不能关闭 |
|---|---|---|---|
| 发布工程 J（13 FAIL + 48 BLOCKED） | J-001/J-002 干净树与 revision；J-020/J-021/J-024/J-025/J-027 生产钥匙/指纹/AAB 同一身份/证书有效期/文档契约；J-033/J-034/J-041 AAB ABI/quickjs/32-bit `.so`；J-048/J-051/J-052 versionCode 单调/注入机制/历史可比 | 共享脏树（本轮 `-uall` 4912；gate 4908）；无生产签名产物 | 需要干净发布树、生产 keystore 与 AAB 构建；改写这些行等于伪造发布证据（`acceptance-gate-explanation.md`） |
| 真实 Provider D-083 | 真实 401/429/5xx 失败注入与 UI 失败码映射 | key 具备、协议冒烟 PASS，但失败注入未做 | 需要真实凭据与可控失败注入（`ledger.md:484`） |
| 真机 / arm64 | F-006 等 arm64 行、I 域 TalkBack/IME/独立复核、J 域 arm64 | 设备证据仅 x86_64 模拟器 API 34 | 需要真机/arm64 设备与人工无障碍签字；本批不伪造 |
| 人工金标 / 独立语义复核 | GATE-096…098/100（金标 v2 G1 model-labeled）、I-027 独立复核人 | G1 单模型盲标；行级复核是机械复算，`independent_agent=false` | 需要第二人/第二模型语义复核与人工金标 |
| 真库样本 | 真库 per-reason/G6 因果、真库 `fsrsBeatsBaseline`、参数可辨识性 | **BLOCKED**（零真库样本） | 需要真实使用历史（每卡 ≥2 次可拟合行）；模拟数不得替代 |
| 他线合规 | H-018/H-062（AIGC 隐式标识/水印，KD-85 open） | FAIL；属导出/水印链，不在本批 `feature/review`+`app` 热区 | 需他线改导出链；本批不整文件抢占 |
| 内容切分门 | GATE-091/GATE-092/GATE-099（AUD-08 v2） | FAIL；① 10/24=0.4167<0.9、切残 3≠0 | 需切分链修复与重跑；非本批复习算法链 |

**B. 本批/他线未闭合（不属“外部”，但同样阻断签字）**

1. **算法链**：F-057 维持 FAIL；E 域 9 条 BLOCKED、A 域掌握/复习 6 条 BLOCKED、F-056 BLOCKED（§B49-3）。
2. **单测门**：12 模块 4165 例 / 9 红 / exit 1；修复环 5 模块复跑 2970 例 / 10 红 / exit 1（新增 app 2 红）；证据文档逐条归因为 HEAD 既存他线语义（`core:database` 4、`core:data` 4、`feature:tutor` 1、`app` 2；`repair/unit-gate-and-kd67-blockers.md` §1、`postfix-gate.md` §2.3），本批面 `feature:review` 106/0、`core:domain` 741/0。强门 exit 0 只证明编译/打包，不执行测试（`postfix-gate.md` §1）。
3. **KD-67/校准页/冲突**：真 Room e2e、第二道校准题、冲突 planner→队列设备链、对比度均未闭合（§B49-6）。
4. **验收质量**：18 组 ≥5 行 shared checks（119 行）+ 19 组 2–4 行（50 行）；731/900 行级特有；34 份复核 `basis` 叙述引用旧 sha；`d0_self_acceptance --group all` 本批未跑（`verdicts.md` §0）；证据树 22 个 `.log` 被 `.gitignore:31` 忽略，原始日志不入库（integration F10）；`docs/2026-11-08-review-calibration-report.json` 仍是 GBK 非法 UTF-8 旧副本（untracked；本轮实测解码失败），不得提交（integration F1）。
5. **PSS 残余**：2h soak 总 PSS 176,669→242,118 kB（末轮 225,466，约 +49 MB）未归因；F-056 仍 BLOCKED，不据此宣称内存无问题。

### B49-8. 签字

**签什么**

1. 结论「**批次 49 验收不通过（NOT ACCEPTED）；发版不成立**」；终态 **900/22/278/0**、`exit=1`、`violations=[]`、`pass_with_invalid_review=0`；22 FAIL 名单与 6 条 GATE 的逐行计数。
2. 与批次 48 的收敛事实：898/23/279 → 900/22/278；**F-055 FAIL→PASS、F-063 BLOCKED→PASS**；**F-057 维持 FAIL**（超限路径未命中，不是“无崩溃=通过”）。
3. **算法相关 FAIL/BLOCKED 未清零**：算法链 FAIL 1（F-057）；E 域 9 条 BLOCKED、A 域掌握/复习 6 条 BLOCKED、F-056 BLOCKED；真库标定/上线门 BLOCKED；AUD-08 三条 FAIL 另列。按“清零或说明”以“说明”收口，不签“清零”。
4. **掌握阈值 21 天/0.9 维持**：只读测量、无变更提案；T1/T2 只是读数；分母 1–2 KC，不足以支撑阈值安全结论。
5. **G6/权重全部维持现值**：0/4、0/17 过门；`adopted_f=null`、`adopted=null`；`AlgorithmConstants.kt` 哈希与预注册基线一致；VERSION `review-planner-v4` 未 bump；三组金样未重生成且逐位一致 2/2 绿；P1-10 模拟上线门 885 样本/`beats=true`，真库 BLOCKED。
6. **KD-67/校准页/冲突处置的行级 PASS 与其残余**：A-019/E-080、A-067/E-044、A-021/E-081/F-063 的机械复核 + JVM/设备 UI 证据；残余（真 Room e2e、第二道题、冲突设备链、对比度、真库读数）如实登记。
7. **复核链与修复环**：922 份行级 JSON 全部绑当前台账 sha；`REVIEWS.sha256` 923/923、`EVIDENCE.sha256` 271/271、`GATES.sha256` 10/10 校验通过；25 份失效复核按当前 sha 重生成；F-057 假 PASS 已回退；F-055/F-063 的 `cmd:`→`file:` 已更正；calibration 9 产物复跑逐字节一致。

**不签什么**

1. 不签「批次 49 验收通过」或「发版就绪」——`require-complete` 两阻断（FAIL=22、18 组 shared checks）与 278 BLOCKED 均未消除。
2. 不签「算法相关 FAIL/BLOCKED 清零」——F-057 仍 FAIL；E/A 算法 BLOCKED 未取得条件；真库样本与真库上线门 BLOCKED；AUD-08 三条仍 FAIL。
3. 不签「F-057 已通过」——117 轮 soak 未命中超限路径；根因修复不等于超限路径验证；旧 `soak.cmd.log` 的 `EXIT=0` 不作退出证明。
4. 不签「掌握阈值已验证安全」或「可以改阈值」——读数只是 model-based、分母 1–2 KC 的方向性观察；任何变更需用户单独裁定 + 新预注册 + 版本纪律。
5. 不签「G6/权重变更已落地」——本批没有变更；也不签“字节级 seam 改动等于行为变更”或反之——默认值/金样/指纹证明默认行为未变。
6. 不签 KD-67 完整闭环、第二道校准题、冲突 planner→队列设备链、真库校准页读数、无障碍对比度。
7. 不签 shared-suite 残余（18 组 ≥5 行 / 119 行；2–4 行 19 组 / 50 行）的行级判别力；不签 34 份 `basis` 旧 sha 叙述已清理；不签 `docs/2026-11-08-review-calibration-report.json` 可提交（GBK 非法 UTF-8）。
8. 不签外部发布条件：干净树、生产签名、真实 Provider 失败注入、真机/arm64、人工金标/独立语义复核、真库样本、H-018/H-062、AUD-08。

### B49-9. 产物与哈希（批次 49 终态）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md`（1200 行） | `264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8` |
| `docs/acceptance/acceptance-report.json` | `5db05770bc90d24f2fcf3ba7d6dfc656e03bc21e4cde1142cff33950a28455db` |
| `docs/acceptance/verdicts.md` | `8e3c84cf0420527c67cb47cd4fc395ca693189828f684285d4a32380f49243ec` |
| `docs/acceptance/domain-index.md` | `3d4519766bf877914397f7401a3f261c8e8a6d1219bd871e590400b5bbf82768` |
| `REVIEWS.sha256`（923 项：922 行级 JSON + schema） | `20912e2222d77822196054d45136caccb46b164a12baac38b5b992bfe021a398` |
| `EVIDENCE.sha256`（271 项，`docs/research/evidence/2026-11-08-batch-49/**` 非 `.mimosa`） | `a4893e67030e8c8d60696a8fca2e5fd48f8de15c87cf88e4621a334f3db9f449` |
| `GATES.sha256`（10 项，batch-49 acceptance evidence `gate/`） | `b41a4d6f952bdd711f456a5cbea25aa35ea9717fe09cd84bdcee158183484277` |

**证据口径（必须与结论一起读）**

1. `docs/acceptance/acceptance-report.json` 与 `gate/acceptance-report.json` 逐字节相同；报告头 `head=0515cfb0…`、`dirty=4908`。`00-index.md:5` 仍写“HEAD `ba7b5624` / 门禁 4898”，是该索引头部未随 06:33 重生成更新的**陈旧叙述**；以报告 JSON 与本轮实跑为准（本轮 dirty 4912，4 条差额未逐条归因，属共享工作树变动）。
2. 本轮未重跑 Gradle/设备/soak/标定；单测、强门、设备、标定计数均来自证据 XML/日志，soak 统计由 CSV 复算而非重跑。报告里的 `model-based` 数字不得外推真库或真机。
3. 修复环 `repair/README.md` 的“中项 7 类”与 `postfix-gate.md`/`repair/unit-gate-and-kd67-blockers.md` 的“7 条 GATE”是 prose 计数笔误；逐行表与台账为准（1 高 + 6 中；6 条 GATE）。
4. 本终裁不把任何 `BLOCKED` 读成通过，不把行级机械复算读成语义复核，不把 model-based 模拟读成真库结论，不把行级 PASS 读成完整产品闭环。

```text
结论：批次 49 验收不通过（NOT ACCEPTED）；发版不成立
签字人：批次 49 报告重生成终裁（记录员）
签署范围：B49-8 签什么 1–7 项
不签范围：B49-8 不签什么 1–8 项
日期：2026-11-08
```

---

## 批次 50 报告重生成终裁（2026-11-09，记录员）

> 台账：`docs/acceptance/ledger.md` sha256 `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`（1200 行，批次 50 终态；mtime 01:05）。
> 机器报告：`docs/acceptance/acceptance-report.json` sha256 `59dc796278d933925833ea2e41f3606478f4d28a32a949e3a7b42fd854e49ab5`（head `a87fc70d…`、dirty 5345、exit 1），与 `docs/acceptance/evidence/2026-11-09-batch-50/gate/acceptance-report.json` **逐字节相同**（本轮 `cmp`）。
> 证据：`docs/research/evidence/2026-11-09-batch-50/`（`EVIDENCE.sha256` 673 项）、`docs/acceptance/evidence/2026-11-09-batch-50/`（`REVIEWS.sha256` 947 项、`GATES.sha256` 35 项）；计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md`。
> 本段只写终裁结论、复核结果与签字边界；不替代台账、行级 JSON 与原始证据。
> 本轮记录员**实跑**：`acceptance_report`（含 `--require-complete`）、`acceptance_lint`、`trace_requirements`、`acceptance_init --check`、`REVIEWS/GATES/EVIDENCE` 三份 sha256 清单校验、`cmp` 报告副本、台账逐行 count 复算、runbook 双载体覆盖复算。
> 本轮**未重跑**：Gradle 单测/强门、设备 instrumented、2h soak、真实 Provider、VLM judge、16KB 运行时、AUD-08 切分门（一次性红线）、`connectedLocalFirstDebugAndroidTest`。相关结论一律标注"读自证据"。

### B50-0. 结论

**批次 50 验收不通过（NOT ACCEPTED）；发版不成立。**

| 项 | 实值 | 判定 |
|---|---|---|
| 台账 verdict | **PASS 933 / FAIL 13 / BLOCKED 254 / UNVERIFIED 0**（total 1200） | 非 PASS **267** 行 |
| `acceptance_report` | exit **1**；`state=FAIL`；`violations=[]`；`pass_with_review=933`、`pass_without_review=0`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=655`、`review_validation_errors=[]` | 证据链本身 0 违规；批次级未收口 |
| `--require-complete` | exit **1**；`NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=13 BLOCKED=254)` + `REVIEW_CHECKS_SHARED`（20 组） | 仍不可签字 |
| 与批次 49 对比 | 900/22/278 → **933/13/254/0**；新增 PASS 33（H 5 + FIX 20 + J 8） | +33 PASS；FAIL −9；BLOCKED −24 |
| 四门 | 3D `overall_pass=false`；3E `PASS_WITH_FLAGGED_LIMITATION`；MVA `FAIL/NOT_EVALUABLE`；AUD-08 **FAIL** | 四门均未通过（3E 为条件性，不计无条件 PASS） |
| 产品面 | 5c/5b/6B-3/JS/KD-67 均有落地与测试，但生产闭环残余未消 | 行级/模块级成立，不等于完整产品闭环 |
| 发布工程 | 本地 AAB 可签名、16KB 静态、vc 101160、mapping 成立；干净树/生产签名/Play/备案 BLOCKED | **发版不成立** |

**13 FAIL 逐行（本轮台账复算）**：F-057 1 条；J-001/J-002/J-020/J-021/J-048/J-052 共 **6** 条；GATE-032/GATE-038/GATE-042/GATE-091/GATE-092/GATE-099 共 **6** 条。合计 13。
**254 BLOCKED 域分布（本轮复算）**：A 25、B 2、C 2、D 8、E 9、F 32、G 7、H 16、I 37、J 47、FIX 65、GATE 4。

### B50-1. 本轮实跑（逐字命令与 exit）

| # | 命令（逐字或简） | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `git rev-parse HEAD` / `git status --porcelain \| wc -l` / `-uall` | 0 | `a87fc70d15c8ad726cee17422dbeef0b4977747d`；**1643** / **5356** |
| 2 | `sha256sum docs/acceptance/ledger.md` | 0 | `9044a779…45cb97`；1200 行 |
| 3 | `python -I tools/ci/acceptance_report.py --out build/b50-record/report.json` | **1** | **933/13/254/0**；`state=FAIL`；`violations=[]`；`pass_with_review=933`、invalid=0、without=0、row_specific=**655**；head `a87fc70d`、dirty **5356** |
| 4 | 同脚本 `--require-complete --out build/b50-record/report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (0/13/254)`；`REVIEW_CHECKS_SHARED` 20 组 |
| 5 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b50-record/lint.json` | **1** | total 1200；violations **162**，全部 `EVIDENCE_REUSED`；其中 **22 条**点名 `2026-11-09-batch-50` 证据路径 |
| 6 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b50-record/trace.json` | **0** | total 1200；gaps **0** |
| 7 | `python -I tools/ci/acceptance_init.py --check` | **0** | 无输出 |
| 8 | `sha256sum -c docs/acceptance/evidence/2026-11-09-batch-50/REVIEWS.sha256` | **0** | **947/947 OK**（946 行级 JSON + `verdict.schema.json`） |
| 9 | `cd docs/acceptance/evidence/2026-11-09-batch-50 && sha256sum -c GATES.sha256` | **1** | **34/35 OK**；`make_verdicts_report.py` FAILED（清单 01:06 冻结、文件 02:06 被修复环改） |
| 10 | `sha256sum -c docs/research/evidence/2026-11-09-batch-50/EVIDENCE.sha256` | **1** | **670/673 OK**；`d1/REPORT.md`、`device/README.md`、`mva/README.md` FAILED（均 02:05–02:10 被修复环改） |
| 11 | `cmp docs/acceptance/acceptance-report.json docs/acceptance/evidence/2026-11-09-batch-50/gate/acceptance-report.json` | 0 | **逐字节相同** |
| 12 | runbook 覆盖（进程内换载体，未改工具） | — | `blocked-runbook.md` **254/254**、missing=[]；默认 `runbook.md` **85/254**、missing 169 |
| 13 | 台账逐行复算（Python，按竖线分列） | — | 1200 行；933/13/254；13 FAIL 名单与域计数见 B50-0 |

**读自证据、非本轮重跑**：环后强门（7/7 exit 0，只编译/打包；日志 sha `453584f4…`）；10 模块单测（**4294 例 / 11 红 / 0 error / 3 skip / exit 1**；11 红逐名均为既有）；修复环 pytest **145 passed**；设备矩阵与 2h soak；AIGC/隐私/发布读数；D-1/D-2/D-3 与 AUD-08 门值。上述均未在本轮重新执行。

### B50-2. 复核结果（946 份行级复核 + 修复环）

1. **复核链形式有效，但不是语义复核**：933 个 PASS 行全部有行级 JSON，`pass_without_review=0`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；946 份行级 JSON 全部是 `mechanical-recompute`、`independence.independent_agent=false`（本轮逐文件扫描确认），复核人是脚本，不是第二人/第二模型。`pass_with_row_specific_review=655/933`（70.2%），其余 278 行为 shared checks。
2. **shared checks 未清零**：20 组 ≥5 行共用（**202 行**，最大一组 72 行，指纹 `27b3f6e30432`）；`--require-complete` 仍以 `REVIEW_CHECKS_SHARED` 阻断。组级证据主要来自 B 迁移矩阵、FIX 修复单、AIGC/隐私门面——它们证明"该组检查成立"，不证明"每一行被单独判别"。
3. **复核证据批次**：`review_evidence_batches` 计 50=**392**、49=24、48=3、47=23、46=62、45=402、44=1206、unlabeled=384；本批新增复核与旧证据复用并存。
4. **修复环 7 项已修、4 项阻塞**：AUD-08 范围文案、D-1 抽样框漂移量化、重复 finding 硬规则、MVA `citable=false`、κ 披露、Wilson FPC 披露、soak PASS 口径；阻塞=5c 候选生产者、3E 第二档预留份额、JS 生产装配、干净树 release。详见 `repair/REPAIR-RING.md`。
5. **本轮新发现（登记，不改 verdict）**：
   - **lint 162 条中 22 条点名批次 50 证据**（批 49 为 144 条且新证据 0 条）；典型 `b-migration-matrix.device.json` 被 72 行 PASS 共用。**不能沿用批 49 的"新证据 0 条入告警"结论**。
   - **哈希清单滞后 4 项**：GATES 1（`make_verdicts_report.py`）、EVIDENCE 3（`d1/REPORT.md`、`device/README.md`、`mva/README.md`）；均为修复环在 01:06 冻结后改动（02:05–02:10）。是清单滞后，不是证据链违规。
   - **默认 runbook 载体覆盖 85/254**（计划载体 `blocked-runbook.md` 254/254）；载体错位批次 47 §6.4 已登记，本批未修。
   - **能力屏测试 hook 由整串等值改 `substring=true`**：属匹配放宽（仍要求"测试通过"主句上屏）；本终裁不把它计入"断言零放宽"。
6. **独立 + 对抗复核（计划 #41）未执行**：无第二主体语义复核签字件；修复环 findings 的"已独立复核"无独立产物可核。

### B50-3. 四门防洗绿核验

| 门 | 门定义 | 实测 | 结论 | 不得读成 |
|---|---|---|---|---|
| **3D D-1** | 分层加权绑定精确率单侧 95% Wilson 下界 ≥0.95；每层护栏 ≥0.90 | 主判据 **0.9756913**（抽样时点）/ **0.9756882**（当前框）PASS；normal 护栏 **0.8979148** FAIL（差 0.2085pp；FPC 0.9005766 PASS）；`overall_pass=false`；κ **NOT RUN**；`labeler_kind=model`、`human_expert=false` | **未过门** | 不读作"3D 通过"；不把 0.9757 当可用门值（κ 前置未过，只是 model-labeled 暂定值）；不把 FPC 口径读成护栏通过 |
| **3D D-2** | 近重复/超配/容量/历史 type 清零或登记 | 近重复节点级 **0/0**；2 组候选裁决保留/退休（**退休未执行**）；超配 **2,481 节点 / 62,944 条 / 超出前 4 = 53,020**，`RETAIN_NO_CAP` + 2,481 行台账；容量 91 条逐行登记（22/12/44/13）；type 三值外 **0** | 清零/登记 | 不读作"超配已治理"；只读作"保留策略 + 台账"；容量生产器补丁未应用 |
| **3D D-3** | 五红清零、召回保持 117/129 | 五类 12 例 `--rerun` **12/0/0/0**；dense 11/11；融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**、词面 101/129、p50/p95 38/58ms、overBudget 0、24 章逐章 diff rc=0 | 达成（保持） | 不读作"召回提升"；12 条 MISS 同集、absent 边界不变；MRR 0.7239 是当前值（0.7323 为批 19 旧时点） |
| **3E** | 队外捞回、F8/F17 封顶、每日/单计划口径 | 设备 **OK(4)**、JVM **23/0**；45 分钟捞回 10（全在今日并集外）；1200s→6 知识槽、第二档 4；口径=每次排计划 ≤10（KD-46）；`PASS_WITH_FLAGGED_LIMITATION` | **条件性通过** | 不读作无条件 PASS：第二档无预留份额，默认 20 分钟可被今日档占满，第二档贡献可为 0 |
| **MVA** | 120 盲测、首次/预算内/分组/关键错误 | 冻结/隔离成立（120/24 交集 0、288 事实 PASS）；IMG/JS 两臂 **0 可评样本**；确定性检查 `NOT_RUN`；`critical_errors_accepted` 0 但 **`citable=false`**；40 条 critical 全 UNVERIFIED；`evaluable=false` | **FAIL / NOT_EVALUABLE** | 不读作"0% 命中"或"质量通过"；120 题质量结论不存在 |
| **AUD-08** | ① 题数一致 ≥0.9；③ 切残 ≤0.05；硬红线切残 0 | ① **7/24=0.2917**；② 27/78=0.3462；③ **1/78=0.0128**；硬红线 `fragmentedQuestions=1`；`check_split_gate.py` exit **1**；自动化不启用 | **FAIL** | 不读作"acceptance 只跑一次=held-out 成立"：本批唯一 run，但同一 8 页已在批次 48 消费，held-out 不可恢复（第二次观测）；不把 ③ 过线读成门过 |

**四门汇总口径**：3D 未过（护栏）；3E 条件性通过（有 flagged limitation）；MVA 不可评；AUD-08 失败。**四门没有任何一门可签"通过"。**

### B50-4. 发版判定逐项

| 项 | 实测 | 判定 |
|---|---|---|
| **可签名 AAB** | 双 flavor AAB `jarsigner -verify` exit 0；APK v1+v2 true、v3/v4 false；AAB 证书 sha256 `2c8415b2…5eba` = APK 证书；signing 门 exit 0 | **PASS（本地测试 key）**；**生产签名 FAIL/BLOCKED**（J-020/J-021 仍 FAIL，DN `OU=NOT-PLAY-PRODUCTION`） |
| **16KB** | `zipalign -P 16` Verification successful；apk16k 14/14 `.so` STORED+`offset%16384=0`、`elf_errors: []`；aab16k alignment 2、ABI `{arm64-v8a, x86_64}`、7 库全在 | **静态 PASS**；**运行时 UNVERIFIED**（设备 4096 页/x86_64、API 34；无 arm64 真机） |
| **versionCode** | `101160 = 100000 + 1160`；合并 manifest / `aapt2 badging` / AAB proto manifest 三处一致；`validateVersionCode` 3 负控红 + 101160 绿；verifier `--expected-code` 双 flavor exit 0 | **PASS（注入与一致性）**；J-048/J-052 仍 FAIL（跨构建单调/历史 AAB 可比未做）；下次必须 >101160 |
| **mapping** | 140,341,720 B / 968,647 行 / R8 9.3.16；与 AAB 内 `proguard.map` 逐字节同 sha `ce595aa0…277e`；r8 门 exit 0（classes 10661 / renamed 10461 / removed 1199 / negative_control_hits 0） | **PASS** |
| **干净树** | `identity --require-clean` **exit 1**、`dirty_paths: 4315`（构建时）；当前 `-uall` **5356**；AAB 由脏树构建 | **BLOCKED**（须提交后复跑；不得把本轮包当 HEAD 字节复现） |
| **AIGC** | 五项（UI 角标 / DOCX `docProps/custom.xml` / KD-85 改卷文本 / KD-84 部分页 / KD-29 材料整理）；`h_compliance_gate --all`：H-052/H-061/H-062 **PASS**、H-053 **门红（占位符/外部未取得；台账行 BLOCKED）**；DOCX/PDF 有独立读回 | **PASS（五项有证据）**；**H-053 外部 BLOCKED** |
| **隐私** | 9 章政策全文页；未成年人专章补悬空引用 → H-025 **PASS**；data safety 清单落盘；UI dump 扫 ICP/8 位连续数字零命中 | **PARTIAL**：**H-024 公开 URL BLOCKED、H-036 Play 表单 BLOCKED**；备案号/主体/联系方式/模型备案 6 项 BLOCKED |
| **Play 账号 / 备案号** | 无 Play 账号、无生产 keystore、无备案号；本地 key 明示非 Play | **BLOCKED（外部）** |
| **商店素材/上架清单** | 证据目录未发现产物 | **BLOCKED（外部）/未产出** |

**总判**：可签名的本地 AAB、16KB 静态、versionCode、mapping 成立；**干净树、生产签名、Play 账号、备案号、公开隐私 URL、H-053 回填未取得**。**发版不成立**。

### B50-5. 签字

**签什么**

1. 结论「**批次 50 验收不通过（NOT ACCEPTED）；发版不成立**」；终态 **933/13/254/0**、`exit=1`、`violations=[]`、`pass_with_invalid_review=0`；13 FAIL 名单与域计数。
2. 与批次 49 的收敛事实：900/22/278 → 933/13/254；新增 PASS 33（H 5 + FIX 20 + J 8）；**四门无一通过**。
3. **3D**：D-1 `overall_pass=false`（normal 护栏 0.8979148<0.90；主判据 0.9757 的 κ NOT RUN，只是 model-labeled 暂定值）；抽样框已覆盖、`sample_d1.py --check` match=false/exit 2；D-2 近重复 0/0、超配保留策略 + 2,481 行台账、容量 91 条登记、type 三值外 0；D-3 五红 12/12 绿、117/129 保持。
4. **3E**：`PASS_WITH_FLAGGED_LIMITATION`（第二档无预留份额）；**不计无条件 PASS**。
5. **MVA**：`FAIL/NOT_EVALUABLE`（零生图/零真实 Provider/零 VLM judge/零点确定性检查）；`critical_errors_accepted=0` 不可引用；JS 不广告。
6. **AUD-08**：FAIL（① 7/24=0.2917、③ 1/78=0.0128、硬红线 frag=1）；本批 acceptance 只运行一次，但同一 8 页已在批次 48 消费，held-out 不可恢复。
7. **产品面**：5c/5b/6B-3/JS/KD-67 的落地与门禁读数；各自残余（生产候选生产者、rebalance 恒 false、真实 Provider 回执、visualEngine 未注入、第二道题 UI 入口）如实登记。
8. **复核链与修复环**：933/933 有效复核；947/947 REVIEWS；修复环 7 修 4 阻塞；环后强门 7/7 exit 0（只编译/打包）；10 模块 4294/11 红/exit 1（11 红均为既有）。
9. **发布工程**：本地 AAB 可签名、16KB 静态、vc 101160、mapping 与 AAB 内嵌逐字节一致；本地 key 非 Play 生产身份。

**不签什么**

1. 不签「批次 50 验收通过」或「发版就绪」——`require-complete` 两阻断（FAIL=13、20 组 shared checks）与 254 BLOCKED 未消除。
2. 不签「四门通过」——3D 护栏 FAIL、3E 条件性、MVA 不可评、AUD-08 FAIL；不把 3E 计无条件 PASS。
3. 不签「3D D-1 通过」或「0.9757 是可用门值」——κ NOT RUN，只能读作 model-labeled 暂定值；不签「normal 层确定不达标」——护栏差 0.2085pp（1 条判定宽度），但阈值不放宽。
4. 不签「AUD-08 acceptance 是首次盲测」——同一 8 页已在批次 48 消费；不签「重跑换绿」或第二观测当 held-out。
5. 不签「MVA 质量通过/0% 命中」——120 题无样本；不签 `critical_errors_accepted=0` 可引用。
6. 不签「干净树产物」或「HEAD 字节复现」——构建时 4315 脏路径、当前 5356；不签「生产签名/Play 上架/备案号已取得」。
7. 不签「H-053 已回填」「H-024/H-036 已完成」——外部条件未取得，代码/文档写"尚未取得"。
8. 不签「行级复核是语义复核」或「shared checks 有行级判别力」——`independent_agent=false`、20 组 202 行共用；不签「独立/对抗复核已完成」（计划 #41 未执行）。
9. 不签「哈希清单全部通过」——GATES 34/35、EVIDENCE 670/673（4 项滞后）；不签「断言零放宽」——能力屏 hook 由整串等值改 `substring=true`。
10. 不签「AIGC 导出 background 2 例通过」——2 例 FAIL，该 claim UNVERIFIED。

### B50-6. 产物与哈希（批次 50 终态）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md`（1200 行） | `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97` |
| `docs/acceptance/acceptance-report.json`（= gate 副本，逐字节相同） | `59dc796278d933925833ea2e41f3606478f4d28a32a949e3a7b42fd854e49ab5` |
| `docs/acceptance/verdicts.md` | `42f58ee159c9b4759998bbac62ceb481a443ecf18f19218a0fee617234f3f718` |
| `docs/acceptance/domain-index.md` | `9f1a0da37aef531e509c8839e0bbc905ee13ecf0289ef742bd3acc004c30b58c` |
| `REVIEWS.sha256`（947 项） | `0881a9610fb3cd50fd65a84bd18d9a126372432efb788a87e7c2b319416ef4b1` |
| `GATES.sha256`（35 项） | `73ad608ae2f46b2b338ef7154148858801b1436bd7a2ce55ba55a97c9818627e` |
| `EVIDENCE.sha256`（673 项） | `953f1ebbed5a9aea1cbdb9ac4bd910ec6372683983fda530b2bb59a06748537d` |

**证据口径（必须与结论一起读）**

1. `acceptance-report.json` 与 `gate/acceptance-report.json` 逐字节相同；报告头 `head=a87fc70d…`、`dirty=5345` 是生成时点；本轮实跑 `dirty=5356`，差额属共享工作树变动，不影响台账/复核。
2. 本轮未重跑 Gradle/设备/soak/Provider/VLM/16KB 运行时/切分门；相关计数均来自证据 XML/日志/JSON。
3. `GATES.sha256` 34/35、`EVIDENCE.sha256` 670/673 的 4 项不匹配是**清单冻结后被修复环改动**（01:06 → 02:05–02:10）；不是内容被篡改，但复现者按旧清单会 exit 1。
4. 本终裁不把任何 `BLOCKED` 读成通过，不把行级机械复算读成语义复核，不把 model-labeled 读成人工结论，不把脏树产物读成干净树产物，不把本地测试 key 读成 Play 生产身份。

```text
结论：批次 50 验收不通过（NOT ACCEPTED）；发版不成立
签字人：批次 50 报告重生成终裁（记录员）
签署范围：B50-5 签什么 1–9 项
不签范围：B50-5 不签什么 1–10 项
日期：2026-11-09
```
