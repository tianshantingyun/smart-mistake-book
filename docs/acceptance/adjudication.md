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
