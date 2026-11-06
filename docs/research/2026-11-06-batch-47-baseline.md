# 批次 47 基线（2026-11-06 验收收敛批）

> 交付物：本文件是批次 47 的**只读基线**。未改台账、未改源码/热区、未引依赖、未跑 Gradle、
> 未做任何 git 写（`git diff --cached --name-only | wc -l` = 0）。
> 入口：批次 46 终裁 NOT ACCEPTED（`39d8c75a`，`docs/research/2026-11-06-batch-46-record.md` §9）。
> 计划：`.zcode/plans/plan-2026-11-06-batch-47-acceptance-converge.md`（§1 WS1–WS5 = 78 项、
> §2 硬约束、§3 验收三统计）。

## 0. 一句话结论

台账 `19ea6b57…` 的 1200 行**与批次 46 终态逐格一致、零漂移**：`PASS 888 / FAIL 32 / BLOCKED 280 /
UNVERIFIED 0`，`pass_with_row_specific_review = 575/888`，`REVIEW_CHECKS_SHARED` 24 组覆盖 156 行，
`checks_scope=shared-suite` 318 行。`--require-complete` 仍 **exit 1**，阻断仍是那两条
（`NOT_ACCEPTANCE_COMPLETE`、`REVIEW_CHECKS_SHARED`）。本基线新增三项**开工前必须知道的事实**（均本轮实算）：

1. **工具只阻断 ≥5 行的共用组（156 行），另有 157 行 PASS 落在 2–4 行的共用组里，门看不见**。
   若 WS2 只把 156 行清掉，`--require-complete` 可转绿而「行级判别力」仍未成立（§4.3）。
2. **台账里没有 `blocked_by` 字段**（全文 `blocked_by` 字面命中 **0** 次）。280 条 BLOCKED 的分组
   只能派生；本基线按行 `method` 文本 + `runbook.md` §13 工单表派生为 10 组（§5），其中
   **74 行（26.4%）落在计划的 7 组之外**（缺用例/阈值/工具能力），责任人不是「外部条件」。
3. **49 份批次 46 证据文件全部未入 git**（`git ls-files docs/research/evidence/2026-11-06-batch-46` = **0**），
   其中 41 份被台账引用；**31 条 PASS + 6 条 FAIL** 行依赖**非 `.log`** 的未跟踪证据，从 HEAD 不可复算；
   另有 360 个 `*.log` 因 `.gitignore:31` 被永久排除（876 行 PASS/BLOCKED/FAIL 引用它们）（§6.3）。

## 1. 快照

| 项 | 值 | 本轮命令 / 来源 |
|---|---|---|
| HEAD | `39d8c75aebbf0b5f0acf4053d559fe35cf175145`（2026-11-05T20:19:56+08:00，`docs: 批次46 主会话收口…`） | `git rev-parse HEAD`、`git log -1` |
| 台账 | `docs/acceptance/ledger.md`，sha256 `19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b`，**1231 行 / 563,093 B / 1200 数据行** | `sha256sum`、`wc -lc`、`acceptance_model.parse_ledger_text`（issues=0） |
| 台账 sha 与批次 46 终态 | **完全相同**（批次 46 §0.2 记录的盘上值即 `19ea6b57…`） | 两处 `sha256sum` 比对 |
| 工作树 | `porcelain` **550**；`porcelain -uall` **3788**；暂存 **0** | 三条 git 命令逐条计数 |
| 行级 JSON | `docs/acceptance/verdicts/rows/` **920** 份；`git ls-files` **920**（**已入库**，非批次 46 时的 0） | `ls \| wc -l`、`git ls-files … \| wc -l` |
| 证据根（批次 46） | `docs/research/evidence/2026-11-06-batch-46/` 盘上 **49** 文件 / 已跟踪 **0**；`docs/acceptance/evidence/2026-11-06-batch-46/` 盘上 **18** / 已跟踪 **17** | `find -type f`、`git ls-files` |
| 设备 | `emulator-5562`、`emulator-5584` 均 `device`；两台均 `PAGE_SIZE=4096`、`abi=x86_64`、`sdk=34` | `C:/Android/Sdk/platform-tools/adb.exe devices` + `adb -s … shell getconf/getprop` |
| Gradle | **未跑**（本 ask 禁；门禁阶段串行另跑） | 不适用 → 见 §9 |

## 2. 计数（逐行复算，非引用）

命令：`python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --out <tmp>/report.json`
（exit **1**；`state=FAIL`；`violations=0`；`warnings=24`）与 `acceptance_model` 逐行解析（issues=0）。

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED |
|---|---:|---:|---:|---:|---:|
| D0 | 100 | 100 | 0 | 0 | 0 |
| A | 90 | 65 | 0 | 25 | 0 |
| B | 90 | 88 | 0 | 2 | 0 |
| C | 90 | 88 | 0 | 2 | 0 |
| D | 90 | 81 | 0 | 9 | 0 |
| E | 90 | 81 | 0 | 9 | 0 |
| F | 90 | 48 | 9 | 33 | 0 |
| G | 90 | 82 | 1 | 7 | 0 |
| H | 90 | 68 | 3 | 19 | 0 |
| I | 90 | 53 | 0 | 37 | 0 |
| J | 90 | 29 | 13 | 48 | 0 |
| FIX | 100 | 15 | 0 | 85 | 0 |
| GATE | 100 | 90 | 6 | 4 | 0 |
| **合计** | **1200** | **888** | **32** | **280** | **0** |

报告字段（实读 JSON）：`pass_with_review=888`、`pass_without_review=0`、
`pass_with_invalid_review=0`、`pass_with_row_specific_review=575`、`pass_with_two_kinds=888`、
`dirty_paths=3788`（报告自报，与 `-uall` 一致）。

> 与批次 46 §2.1 的 13 域表**逐格一致**；与 `docs/acceptance/domain-index.md:11-26` 的表逐格比对亦**一致**
> （13 行；含「行级复核」列 888/888 与「域出口」列文案）。

## 3. FAIL 32（id / 行号 / 域 / scope）

| 域 | n | id（台账行号） | checks_scope |
|---|---:|---|---|
| F | 9 | F-019(600), F-028(609), F-029(610), F-034(615), F-036(617), F-055(636), F-057(638), F-064(645), F-067(648) | 全 row-specific |
| G | 1 | G-089(760) | row-specific |
| H | 3 | H-018(779), H-061(822), H-062(823) | 全 row-specific |
| J | 13 | J-001(942), J-002(943), J-020(961), J-021(962), J-024(965), J-025(966), J-027(968), J-033(974), J-034(975), J-041(982), J-048(989), J-051(992), J-052(993) | 11 row-specific + **J-033/J-041 shared-suite** |
| GATE | 6 | GATE-032(1163), GATE-038(1169), GATE-042(1173), GATE-091(1222), GATE-092(1223), GATE-099(1230) | 3 row-specific + **GATE-091/092/099 shared-suite** |

- **scope 交叉**：32 条中 **27 row-specific / 5 shared-suite**（`J-033, J-041, GATE-091, GATE-092, GATE-099`）。
- **证据可复算性**：**31/32** 条 FAIL 至少引用 1 个**未被 git 跟踪**的证据文件；唯一例外是 G-089
  （只引 `docs/known-defects.md`，其判据是 `gradle/verification-metadata.xml` 缺失）。
  其中 25 条只是 `*.log`（gitignored），6 条依赖非 `.log` 的未跟踪文件
  （GATE-032/038/042/091/092/099 引 `docs/research/evidence/2026-11-06-batch-46/gate/*.txt`）。
- **逐条出口**：`docs/research/evidence/2026-11-06-batch-46/fix/fix-tickets-b46.md`（48 行，**未跟踪**）；
  判词与复算口径见 `docs/acceptance/adjudication.md:159-184`。
- **不得洗绿**（计划 §2）：7 条 F 域 FAIL 是「证据字节与台账数字矛盾」型（批次 46 §5.5），
  修复路径只能是**补新证据并重算**，不能靠改台账数字或删断言；G-089 是文件缺失，补文件即改判。

## 4. shared-suite 318（口径、分域、与门的关系）

### 4.1 口径定义（两条不同的量，别混用）

| 量 | 值 | 定义 / 出处 |
|---|---:|---|
| `checks_scope=shared-suite`（行 JSON 自报字段） | **318**（313 PASS + 5 FAIL） | 逐份解析 `docs/acceptance/verdicts/rows/*.json` |
| PASS 行中 checks 指纹**非唯一** | **313** | 指纹 = 去掉 `ledger_row` 后 checks 规范化 JSON 串 sha256（`tools/ci/acceptance_report.py:265-271`） |
| 指纹唯一（`pass_with_row_specific_review`） | **575** | 同上；`acceptance_report.py:450-454` |
| **门阻断的**共用组 | **24 组 / 156 行** | 仅统计组内行数 **≥ `REUSE_WARN_THRESHOLD`=5**（`:83`、`:441-442`、`:564-572`） |
| 组内行数 2–4 的共用组（**门不看**） | **66 组 / 157 行** | 本轮复算（组大小分布：2→45 组、3→17 组、4→4 组） |

> 318 与 313 差 5，正好是 5 条 FAIL 行的 `checks_scope=shared-suite`；318 与本文件 §5 的分类标签
> 合计数（也是 318）**纯属数字巧合，无任何关联**。

### 4.2 分域

| 域 | PASS | 声明 shared-suite（含 FAIL） | PASS 非唯一指纹 | 其中 ≥5 行组（门阻断） | 其中 2–4 行组（门不阻断） |
|---|---:|---:|---:|---:|---:|
| A | 65 | 18 | 18 | 0 | 18 |
| B | 88 | 14 | 14 | 8 | 6 |
| C | 88 | 25 | 25 | 7 | 18 |
| D0 | 100 | 91 | 91 | 91 | 0 |
| D | 81 | 0 | 0 | 0 | 0 |
| E | 81 | 54 | 54 | 22 | 32 |
| F | 48 | 6 | 6 | 0 | 6 |
| FIX | 15 | 9 | 9 | 0 | 9 |
| G | 82 | 2 | 2 | 0 | 2 |
| GATE | 90 | 66（63 PASS + 3 FAIL） | 63 | 28 | 35 |
| H | 68 | 0 | 0 | 0 | 0 |
| I | 53 | 24 | 24 | 0 | 24 |
| J | 29 | 9（7 PASS + 2 FAIL） | 7 | 0 | 7 |
| **合计** | **888** | **318** | **313** | **156** | **157** |

### 4.3 对 WS2 的两个硬提醒

1. **清 156 行不够**：门判据只看 ≥5 行组。剩余 157 行（A 18、B 6、C 18、E 32、F 6、FIX 9、G 2、GATE 35、I 24、J 7）
   若不动，`--require-complete` 的 `REVIEW_CHECKS_SHARED` 会**消失**，但「同一组 checks 复制给多行」的事实仍在。
   计划 §1 WS2 的目标写的是「318 行」，与本提醒一致；**按 156 行做会得到假绿**。
2. **门用的是指纹，不是 `checks_scope` 字段**：把字段改成 `row-specific` 不改 checks 内容**不会**改变门的判定
   （`acceptance_report.py:260-271` 只看 checks 内容；`checks_scope` 未被任何工具读取）。
   WS2 必须①改 checks 内容、②同步把字段改成与实际一致——否则字段会变成新的假账。

### 4.4 24 个 ≥5 行共用组（逐组 id，供 WS2 派活）

| 行数 | 指纹前 10 | 域 | id |
|---:|---|---|---|
| 9 | `d03734e927` | GATE | GATE-051..057, GATE-059, GATE-060 |
| 8 | `fa57a55faf` | GATE | GATE-078, GATE-081..087 |
| 8 | `9f5f3c0558` | B | B-074, B-080, B-084..089 |
| 7 | `e886641bef` | C | C-010, C-015, C-020, C-066, C-067, C-068, C-089 |
| 7 | `ce2109e173` | D0 | D0-064..070 |
| 7 | `9df10e570c` | D0 | D0-057..063 |
| 7 | `9c1c13f220` | D0 | D0-008..014 |
| 7 | `827846a4c2` | D0 | D0-050..056 |
| 7 | `71621477e0` | D0 | D0-022..028 |
| 7 | `3e94debb95` | D0 | D0-015..021 |
| 7 | `262c9b2a6e` | D0 | D0-043..049 |
| 7 | `1818575648` | D0 | D0-036..042 |
| 6 | `efadc4d12c` | GATE | GATE-043..048 |
| 6 | `e761b64980` | E | E-074..078, E-080 |
| 6 | `6275ae819a` | D0 | D0-083..088 |
| 6 | `610f4b4dd9` | D0 | D0-071..076 |
| 6 | `582a775f8c` | D0 | D0-001..006 |
| 6 | `355b1f7a3d` | D0 | D0-089..094 |
| 6 | `33c926817d` | D0 | D0-095..100 |
| 6 | `2441d87ce0` | E | E-046..050, E-053 |
| 5 | `adf1098736` | E | E-028..031, E-033 |
| 5 | `7dac470dcf` | GATE | GATE-072..076 |
| 5 | `520bdb0671` | D0 | D0-077..080, D0-082 |
| 5 | `0d6d7df8bf` | E | E-064, E-065, E-066, E-068, E-069 |

## 5. BLOCKED 280 按 blocked_by 分组（派生，非工具字段）

### 5.1 关于 `blocked_by` 的事实

| 检查 | 实值 |
|---|---|
| 台账中 `blocked_by` 字面出现次数 | **0**（`text.count("blocked_by")`） |
| BLOCKED 行 `method` 内含 `runbook` 指针 | **267 / 280** |
| BLOCKED 行 `method` **或**证据单元格含 `runbook` 指针 | **280 / 280** |
| 无 runbook 指针的行 | **0** |

> `docs/acceptance/adjudication.md:193` 写「全部带 `blocked_by`/runbook 指针」。按本轮实读，
> **runbook 指针成立（280/280）**，**`blocked_by` 作为结构化字段不成立**（0 处字面、无对应列）。
> 本文件因此按 §5.2 的规则派生分组；这套规则是本基线的工具，不是台账语义的权威来源。

### 5.2 派生规则（可复算）

- 输入：每条 BLOCKED 行 `method` + `evidence_text` 拼接文本。
- FIX 域 85 行先按文本中的 `FIX-Tnn` 映射到 `runbook.md` §13 工单表（`:250-283`）的 blocked_by 归属
  （T04/T05/T07/T16/T17→热区，T06/T13→环境，T08→Provider，T09/T12→arm64，T10/T11/T15→签名，
  T14/T18→授权，T19→人工，T20→工具能力）。实测 **85/85 行都带工单号，T04–T20 每单恰 5 行**（17×5=85）。
  其中 T04/T07 的落点是 `core/export` 的**已批准 hunk**（runbook §11 `:200`），不是 `feature/*` 热区；
  本基线把它并入 hotzone 组，理由是两者同样需要**内部授权后才可动代码**，责任人同为内部。
- 其余行按关键词表（arm64/16KB/x86_64/PAGE_SIZE/ABI；签名/RELEASE_/AAB/成对/versionCode；Provider/凭据/API_KEY；
  热区；授权/Play Console/账号/备案/域名/复核人签字；金标/标注/人工/语感/视觉/TalkBack；
  语义树不含/逐像素/硬件回传/离线 advisory/故障注入入口；真机/模拟器/历史基线/同设备/soak/门禁阶段/干净树；
  用例/夹具/未落地/接线/未覆盖；阈值/只建基线/未定义/产品裁定/无运行时权限）。
- **一条行可命中多组**（同一行确有多因）；下表是「标签计数」，合计 318 > 280。
- 命中分布：**1 因 242 行 / 2 因 38 行 / 0 因 0 行**。
- 手工补判 3 行：`F-072`（无历史 ANR/crash 基线）、`F-081`（无历史进程死亡恢复基线）、
  `F-090`（无历史后台限制基线）——文本是「无历史 …基线」形态，正则未命中，按语义归 `env`。

### 5.3 十组（行数、域分布、id）

| 组 | 标签数 | 域分布 | id |
|---|---:|---|---|
| **provider**（真实 Provider/上游凭据） | 17 | D 9, FIX 5, A 2, G 1 | A-010, A-015, D-082..D-090, FIX-036..040, G-036 |
| **signing**（生产签名/发版产物/版本源） | 27 | FIX 15, J 11, H 1 | FIX-046..055, FIX-071..075, H-081, J-019, J-055..057, J-061, J-065, J-068..070, J-087, J-088 |
| **arm64**（arm64/16KB/ABI） | 26 | F 10, FIX 10, J 6 | F-006, F-015, F-024, F-033, F-042, F-051, F-060, F-069, F-078, F-087, FIX-041..045, FIX-056..060, J-035, J-037..039, J-042, J-043 |
| **hotzone**（内部热区未空出） | 28 | FIX 25, H 3 | FIX-016..025, FIX-031..035, FIX-076..085, H-008, H-041, H-051 |
| **authz**（账号/授权/备案/独立复核签字） | 65 | J 26, FIX 25, I 9, H 5 | FIX-017, 022, 027, 032, 037, 042, 047, 052, 057, 062, 066..070, 072, 077, 082, 086..090, 092, 097; H-024, H-025, H-036, H-043, H-053; I-009, 018, 027, 036, 045, 054, 063, 072, 081; J-008, 017, 018, 026, 036, 053, 063, 071, 073..090 |
| **gold**（人工金标/人工走查/语感） | 29 | I 17, FIX 5, GATE 4, H 3 | FIX-091..095; GATE-096..098, GATE-100; H-063, H-089, H-090; I-005, 007, 030, 032, 041, 059, 070, 077, 082..090 |
| **env**（真机/基线/门禁阶段流程） | 41 | F 22, FIX 10, J 5, A 2, I 2 | A-023, A-059; F-002, 006, 009, 015, 018, 020, 024, 027, 033, 042, 051, 054, 056, 058, 060, 063, 069, 072, 078, 081, 087, 090; FIX-026..030, FIX-061..065; I-075, I-079; J-003, J-006, J-007, J-022, J-070 |
| **impl-gap**（缺用例/夹具/实现未落地，**内部可执行**） | 42 | A 15, H 9, E 9, G 3, B 2, I 2, C 1, F 1 | A-003, 005, 010, 014, 031, 032, 041, 049, 050, 058, 065, 066, 068, 077, 086; B-077, B-078; C-088; E-007, 016, 025, 034, 043, 052, 070, 079, 086; F-084; G-016, G-036, G-080; H-008, H-039, H-040, H-042, H-051, H-076, H-077, H-080, H-090; I-061, I-066 |
| **toolcap**（门禁/工具能力缺口） | 17 | I 7, FIX 5, G 3, C 1, J 1 | C-008; FIX-096..100; G-072, G-087, G-088; I-014, I-023, I-025, I-030, I-050, I-061, I-068; J-049 |
| **threshold**（产品阈值/口径/裁定未定） | 26 | F 12, A 8, I 2, J 2, G 1, H 1 | A-006, 015, 024, 033, 051, 060, 069, 087; F-011, 012, 038, 039, 047, 048, 050, 056, 058, 074, 083, 085; G-081; H-075; I-039, I-043; J-066, J-067 |

### 5.4 计划 7 组覆盖不到的 74 行（WS3 必须先补分类再写 runbook）

| 口径 | 值 |
|---|---:|
| 至少命中计划 7 组之一的行 | **206 / 280** |
| **只落在额外 3 组**（impl-gap / toolcap / threshold）的行 | **74**（A 21、F 11、I 9、E 9、H 7、G 6、FIX 4、J 3、B 2、C 2） |
| 其中 impl-gap 独占 | 37 行 |
| 其中 threshold 独占 | 23 行 |
| 其中 toolcap 独占 | 14 行 |

`docs/acceptance/runbook.md` §9（`:158-167`）只给了 Provider / 生产签名 / arm64-16KB / 性能真机基线 /
人工金标 / 屏幕阅读器语感 六条出口；**「缺用例/夹具」「产品阈值未定义」「a11y 工具能力不足」三类没有任何出口段落**。
`blocked-runbook.md` 若要 100% 覆盖 280 行，至少需新增这三类，并明确责任人是工程/产品而非外部供应商。

## 6. 既有 rows JSON 覆盖

### 6.1 覆盖面（920/920，零缺口）

| 检查 | 结果 |
|---|---|
| 文件数 = PASS+FAIL | **920 = 888 + 32**；`missing=[]`、`extra=[]` |
| `verdict` 与台账一致 | **0 mismatch** |
| `ledger_sha256` = 当前台账 | **920/920**（全部 `19ea6b57…`） |
| `checks` 非空 | **920/920** |
| `reviewer ∈ authors` | **0 / 920** |
| `review_kind` | 全部 `mechanical-recompute`（920/920） |
| `independence` | 全部 `{independent_agent: false, reviewer_role_only: true}`（920/920） |
| `evidence_sha256` 条目数 | **2384**（本轮**未重算**字节，见 §9） |
| `run_id` | 18 个值，全为 `2026-11-05T…-batch46-*`：line-review 619、repair-line 294、convergence 7 |
| 入库状态 | `git ls-files docs/acceptance/verdicts/rows` = **920**（全部已跟踪） |

### 6.2 `checks_scope` 分布

`row-specific` **602** = 575 PASS + 27 FAIL；`shared-suite` **318** = 313 PASS + 5 FAIL。

### 6.3 证据可复算性（本轮新增，重要）

`.gitignore:31` 是 `*.log`，因此**所有 `.log` 证据永远不进 git**；再加上批次 46 的
`docs/research/evidence/2026-11-06-batch-46/` 整个目录未提交，形成下面这张表：

| 台账行 | 全部证据已跟踪 | 仅 `.log` 未跟踪 | 含非 `.log` 未跟踪 |
|---|---:|---:|---:|
| PASS（888） | **170** | 687 | **31** |
| FAIL（32） | **1**（G-089） | 25 | **6** |
| BLOCKED（280） | **153** | 127 | 0 |

- 台账证据 token 总计 **1018** 次引用落在 **381** 个未跟踪文件上（其中 360 个 `.log`、
  11 个 `.xml`、6 个 `.txt`、3 个 `.json`、1 个 `.py`）。
- 非 `.log` 的 21 个未跟踪目标里，**20 个在 `docs/research/evidence/2026-11-06-batch-46/`**
  （该目录 49 盘上文件 / 0 已跟踪），另 1 个是 `tools/ci/check_telemetry_compliance.py`（H-033 引用）。
- 后果（对批次 47 直接生效）：**52 行（30 PASS + 22 FAIL / 其中 6 FAIL 依赖非 `.log`）的判据证据
  只存在于当前工作树**，干净检出后不可复算；WS1 处理这 6 条 FAIL、WS2 为 C/D0/GATE 行补行特有
  checks 时，都必须先决定「是否把这批证据提交入库」，否则「可复算」只能停留在口头。

## 7. 门禁现状（本轮实跑，逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **1** | `counts PASS 888/FAIL 32/BLOCKED 280/UNVERIFIED 0`；`violations=0`；`warnings=24`（全 `REVIEW_CHECKS_SHARED`）；`state=FAIL` |
| 2 | `… --require-complete` | **1** | 2 条 violation：`NOT_ACCEPTANCE_COMPLETE`（`UNVERIFIED=0 FAIL=32 BLOCKED=280`）、`REVIEW_CHECKS_SHARED`（24 组 ≥5 行） |
| 3 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **1** | total=1200；violations=**144**、warnings=**144**（全 `EVIDENCE_REUSED`） |
| 4 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **0** | total=1200；`gaps=0` |
| 5 | `python -I tools/split_golden/check_split_gate.py` | **1** | `countExactRate=4/7=0.5714 < 0.9`；`fragmentRate=0/16`；判定 `FAIL` |

> #5 的 exit 单独捕获（`> out 2>&1; echo $?`）为 **1**；不要读成管道里 `tail` 的 0。

## 8. 不可变量（只读核对）

| 项 | 本轮实读 | 判定 |
|---|---|---|
| 6C-2 mask 集合 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:79` `setOf(9, 13, 15)`；`:593` `require(allowedMasks == setOf(9, 13, 15))`；该文件 `git diff --stat` **空** | 未漂移 |
| 6C-2 不变量测试 | `docs/research/evidence/2026-11-06-batch-46/c-domain/TEST-…SixC2RulingInvariantTest.xml`：`tests="3" skipped="0" failures="0" errors="0"` | 3/3（**文件未跟踪**，见 §6.3） |
| KD-76 | `docs/known-defects.md:2916` 标题仍 `KD-76 (open, …)` | **open** |
| isolation | `docs/known-defects.md:2745`：批次 40 `E-40-04 verdict = PARTIAL`，待主会话裁定 A3 口径 | **不定案** |
| p09 | 沿用批次 45 §2.3（`2026-11-06-batch-45-record.md:163/:228`）三次 2071/1890/1861 ms > 1300 ms；证据 `docs/research/evidence/2026-11-06-batch-45/e-device/verify-p09-{run1..3,summary}.txt` 在盘；**本轮未重跑** | **未收敛** |
| AUD-08 | 本轮实跑 `check_split_gate.py` **exit 1**，4/7=0.5714 < 0.9 | **FAIL** |
| 热区 / 依赖 | 本轮只读：未写任何源文件；未新增 `*.gradle(.kts)` 依赖坐标 | 未改 / 未引 |

## 9. 本基线未做 / 不能做（不得读作通过）

| 项 | 状态 | 说明 |
|---|---|---|
| Gradle 全量（含 `:core:data:testDebugUnitTest`）与设备仪表化轮 | **未跑** | 本 ask 禁 Gradle；属门禁阶段串行任务 |
| 2384 条 `evidence_sha256` 的字节重算 | **未跑** | 本 ask 只要基线；批次 46 §5.1 曾复算为 0 mismatch，本文件**不继承该结论为已验**，标 UNVERIFIED |
| 32 条 FAIL 的逐条修复/重判 | **未做** | 属 WS1；本文件只冻结 id、行号、scope、证据可跟踪性 |
| 156/157 行共用 checks 的细化 | **未做** | 属 WS2；本文件只给口径与 id 清单 |
| 280 条 BLOCKED 分组的产品/外部责任人确认 | **未做** | 分组是本基线**派生**（§5.2），不是既成事实；7 组以外的 74 行尤其需要主会话裁定归属 |
| 「另一 agent 抽样 5 行」与第二人/第二模型语义复核 | **仍未做** | 与批次 46 相同：本会话只有一个 agent（§6.1 `independent_agent=false` 920/920） |
| 49 份未入库批次 46 证据（其中 41 份被台账引用）的处置（提交/重建） | **未做** | 需主会话决定；影响 §6.3 的 6 条 FAIL 与 31 条 PASS 的可复算性 |

## 10. 复现命令（本文件所有数字均可重跑）

```bash
# 快照
git rev-parse HEAD
git status --porcelain | wc -l
git status --porcelain -uall | wc -l
git diff --cached --name-only | wc -l
sha256sum docs/acceptance/ledger.md && wc -lc docs/acceptance/ledger.md
ls docs/acceptance/verdicts/rows | wc -l
git ls-files docs/acceptance/verdicts/rows | wc -l
find docs/research/evidence/2026-11-06-batch-46 -type f | wc -l
git ls-files docs/research/evidence/2026-11-06-batch-46 | wc -l
git check-ignore -v docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-data-jvm.cmd.log

# 门禁（全部只读）
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --out build/b47/baseline-report.json
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete --out build/b47/baseline-rc.json
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b47/baseline-lint.json
python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b47/baseline-trace.json
python -I tools/split_golden/check_split_gate.py; echo "aud08 exit=$?"

# 设备（只读）
C:/Android/Sdk/platform-tools/adb.exe devices
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell "getconf PAGE_SIZE; getprop ro.product.cpu.abi; getprop ro.build.version.sdk"
C:/Android/Sdk/platform-tools/adb.exe -s emulator-5584 shell "getconf PAGE_SIZE; getprop ro.product.cpu.abi; getprop ro.build.version.sdk"
```

本轮复算全部以 `python -I -c "…"` **内联只读**执行（工作目录 = 仓库根；只 import `tools/ci/acceptance_model.py`
与逐份解析 920 份 JSON），**未在仓库内写任何文件**；中间产物只落在仓外临时目录：
`blocked_assign.json`（280 行的分组标签）、`report.json` / `rc.json` / `lint.json` / `trace.json` / `aud08.txt`（门禁原始输出）。

> 注意：`python -I` 会忽略 `PYTHON*` 环境变量，重定向 stdout 时中文可能按 GBK 落盘；本基线统一用
> `--out <file>`（工具内以 UTF-8 写）取 JSON，避免编码噪声。

## 11. 回链

- 计划：`.zcode/plans/plan-2026-11-06-batch-47-acceptance-converge.md`（§1 WS1–WS5、§2 硬约束、§3 三统计）。
- 上一批终裁：`docs/research/2026-11-06-batch-46-record.md` §9（NOT ACCEPTED、五条阻断、§9.5 批次 47 出口）、
  §2.1（13 域表）、§2.5（920 份复算）、§5.2（24 组/156 行/575）。
- 台账与协议：`docs/acceptance/ledger.md`、`dod.md`、`runbook.md`（§9 `:158-167`、§9.1 `:169-177`、
  §9.2 `:234`、§11 边界 `:196-204`、§13 工单 `:250-283`）、`adjudication.md`（§4 `:110-158`、§5 `:159-184`、§6 `:185-196`）。
- 投影：`docs/acceptance/domain-index.md`、`verdicts.md`、`acceptance-report.json`
  （sha `a7927de6…`、`c71f498a…`、`c3a598a2…`，均为批次 46 产物，本基线未改）。
- 工具判据：`tools/ci/acceptance_report.py:83`（阈值 5）、`:260-271`（指纹）、`:435-454`（共用组/行特有）、
  `:538-572`（`--require-complete` 三条 violation）；`tools/ci/acceptance_lint.py`（`EVIDENCE_REUSED`）。
- 派生分组数据：§5.3 十组 + §4.4 二十四组；原始标签落在仓外 `blocked_assign.json`（280 行）。
