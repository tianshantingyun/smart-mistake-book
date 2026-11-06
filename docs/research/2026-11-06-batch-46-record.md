# 批次 46 记录（2026-11-05/06）：行级复核、FAIL 重判、UNVERIFIED 收敛与终裁

> **交付边界**：本文件是批次 46 的收口记录，也是本轮记录员的**唯一写入**
> （`docs/research/2026-11-06-batch-46-record.md`；临时命令输出在 gitignored `build/rec46-*`）。
> 本轮**未改台账、未改行级 JSON、未改工具、未改源码/热区、未引依赖、未做 git 写、
> 未跑 Gradle、未跑设备仪表化轮**。台账、920 份行级 JSON、证据、终裁与域索引均已在前序
> 轮次落盘，本轮只做**独立复算 + 记录**。
>
> **本轮亲手实跑（逐字见 §3）**：`git rev-parse/status/ls-files/show`、`sha256sum/stat`、
> `acceptance_init --check`、`acceptance_report`（普通 / `--require-complete`）、
> `acceptance_lint`、`trace_requirements`、`d0_self_acceptance`（all / docs verdicts /
> docs protocol / lint / hash）、`acceptance_hash_manifest --verify` ×3、
> `h_compliance_gate --only` 五条、`j_release_verify` identity/signing/version/aab16k、
> `check_split_gate`、`pytest` 六组（合计 242 例）、`adb getprop` 两台模拟器，
> 以及一个只读脚本：逐份复算 1200 行台账与 920 份 `rows/<ID>.json`。
>
> **本轮未跑**：任何 Gradle 任务、任何 `am instrument` 设备轮、2h soak、arm64/16KB 真机、
> 真实 Provider、Play Console、人工金标、TalkBack/IME 真机走查、第二个人/第二模型的语义复核。
> C/E 域 JVM 与设备结论引用批次 46 归档 XML/JSON，本轮只复算字节与字段（§5.1）。
>
> **状态口径**：完成 = 载体在树且本轮能复算出可指认证据；部分 = 主链在树但主体独立性
> 或语义面不足；UNVERIFIED = 无法判定，**不得写通过**。

- 计划：`.zcode/plans/plan-2026-11-06-batch-46-line-review.md`（§1 96 项、§2 硬约束、§3 验收）
- 上一批：`docs/research/2026-11-06-batch-45-record.md` §9（NOT ACCEPTED）
- 批次基线：`docs/research/2026-11-06-batch-46-baseline.md`
- 终裁：`docs/acceptance/adjudication.md`（**NOT ACCEPTED**，含 §10 修复环）
- 台账：`docs/acceptance/ledger.md`（sha256 `19ea6b57…33e7b`）；域导航 `docs/acceptance/domain-index.md`

## 0. 口径与基线

### 0.1 项数口径

- **计划 96 项** = WS1 冻结与按行复核 60 + WS2 FAIL 重判 15 + WS3 UNVERIFIED 收敛 10
  + WS4 BLOCKED 确认 5 + WS5 报告与终裁 6。
- **台账 1200 行** = D0 100 + A–J 各 90（900）+ FIX 100 + GATE 100。
  本轮逐行复算：`ledger_rows=1200`、`dup ids=0`、域分布与上式一致，
  `acceptance_init --check` **exit 0**（§3 #1）。

### 0.2 基线快照（本轮实测）

| 项 | 值 | 证据（本轮命令/实读） |
|---|---|---|
| HEAD | `a7cecc634f89cab5a085ddd7ee52d4481e0458dd` | `git rev-parse HEAD` |
| 工作树 `porcelain` | **566** | `git status --porcelain \| wc -l` |
| 工作树 `porcelain -uall` | **4739** | `git status --porcelain -uall \| wc -l` |
| 暂存 | **0** | `git diff --cached --name-only \| wc -l` |
| 台账（批前，= HEAD 字节） | `887e3ba649eb17a70c0f6f7af8a657d606398a10925c6116818265387af689aa` | `git show HEAD:docs/acceptance/ledger.md \| sha256sum` |
| 台账（批后，盘上） | `19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b`，1231 行 / 563,093 B | `sha256sum` / `wc -l` / `stat` |
| 行级 JSON | `docs/acceptance/verdicts/rows/` **920** 份 | `ls \| wc -l`；本轮逐份解析 |
| 批次 46 证据根 | `docs/research/evidence/2026-11-06-batch-46/` **49** 文件；`docs/acceptance/evidence/2026-11-06-batch-46/` **18** 文件 | `find -type f \| wc -l` |
| 入库状态 | `docs/acceptance/verdicts/rows` 被跟踪 **0**；`docs/research/evidence/2026-11-06-batch-46` **0**；`docs/acceptance/evidence/2026-11-06-batch-46` **0** | `git ls-files … \| wc -l` |
| 设备 | `emulator-5562`、`emulator-5584` 均 `device`；两机 `PAGE_SIZE=4096`、`abi=x86_64`、`sdk=34` | `adb devices` + `adb -s … shell getconf/getprop` |

> 工作树 4739 条脏路径包含大量历史未提交改动；`docs/current/*`、`core/domain/*`、
> `feature/*` 的改动 mtime 为 2026-11-01～11-04，**批次 46 起始（11-05 15:00）之后
> 热区 0 个文件被写**（本轮按 `git status -uall` 逐路径 stat 复算）。

### 0.3 三代台账：引用必须带 sha

1. **`bb25ef23…`（批次 45 修复环前）**：顶层 10 份 `verdicts/{A..J}-review.json` 绑这一代；
   **不得当行级证据**（§5.3）。
2. **`887e3ba6…`（HEAD / 批次 45 终态）**：`PASS 865 / FAIL 30 / BLOCKED 161 / UNVERIFIED 144`，
   `pass_with_review=0`（批次 46 基线 §2 实跑）。
3. **`19ea6b57…`（批次 46 终态，盘上为准）**：`PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0`，
   `pass_with_review=888`。

本轮所有行级 JSON、报告、清单、本记录一律绑 **`19ea6b57…`**；任何 “865” 只能读作批前快照。

### 0.4 不可变量（只读复核）

| 项 | 本轮实读/实跑 | 判定 |
|---|---|---|
| 6C-2 mask 集合 | `core/data/.../backup/SmbkSelectionPlan.kt:79` `setOf(9, 13, 15)`；`:593` `require(allowedMasks == setOf(9, 13, 15))`；`git diff --stat` 该文件 **空** | 未漂移 |
| 6C-2 不变量测试 | `c-domain/TEST-…SixC2RulingInvariantTest.xml` `tests="3" failures="0" errors="0" skipped="0"` | 3/3 |
| KD-76 | `docs/known-defects.md:2916` 标题仍 `KD-76 (open, …)` | **open** |
| isolation | `docs/known-defects.md:2745` 批次 40 段 `E-40-04 verdict = PARTIAL` | **不定案** |
| p09 | 沿用批次 45 §2.3（`2026-11-06-batch-45-record.md:163/:228`）三次 2071/1890/1861 ms > 1300 ms；本轮未重跑 | **未收敛** |
| AUD-08 | 本轮 `check_split_gate.py` **exit 1**，`countExactRate=4/7=0.5714 < 0.9`，`fragmentRate=0/16` | **FAIL** |
| 热区 / 依赖 | 批次 46 起点后热区写入 0；`*.gradle(.kts)` 新增行中无 `implementation/api/kapt/ksp/classpath` 坐标 | 未改 / 未引 |

> “mask=8 不提供、mask=0 禁止、派生索引恒裁”三项行为语义本批未动代码，仅复核常量、
> `require` 与测试计数；不把常量存在读成三项语义全部被本轮重新证明。

### 0.5 证据边界（先读这一段再看数字）

- **920 份行级 JSON 是机械复算**：`review_kind=mechanical-recompute`（920/920），
  `independence.independent_agent=false`（920/920）。它证明“证据存在、字节未变、
  可判定字段为绿”，**不证明**检查项与证据在语义上充分。
- `reviewer` 是角色标签（`batch46-line-reviewer-<域>`），`authors` 是证据产出角色
  （`batch45-evidence-producer-<域>`）；两者字符串不等，但**不是第二个人/第二模型**。
- `checks` 的 `ledger_row` 在每行不同（id/verdict/行号/sha），其余 check 有 **24 组
  被 ≥5 行逐字节共用**（覆盖 156 行），因此“888 有复核”≠“888 有行级判别力”。

## 1. 96 项逐项对账

> **编号映射口径**（计划 §1 只给编号与分组，未逐条列名，本记录按以下可复核方式展开）：
> - 1 = 冻结台账；2–11 = A–J 逐域行级复核；12–51 = A–J 每域四项自检
>   （① 每个 PASS 行有 JSON；② `reviewer∉authors`；③ `ledger_sha256` 匹配；
>   ④ `checks` 非空），按 A→J 顺序每域 4 项；52–60 = 52–58 七条复核矛盾行
>   （F-019/F-028/F-029/F-034/F-036/F-064/F-067）+ 59–60 两条硬编码假通过
>   （H-052/H-066）；61–75 = 批次 45 的 30 条旧 FAIL 按台账行序两两配对；
>   76–85 = UNVERIFIED 按域分组（J 与 GATE 合并为第 85 项）；86–90 = BLOCKED 确认五项；
>   91–96 = 报告/重生成/lint/终裁/台账/记录。
> - 判定用值均来自本轮复算：台账 1200 行逐行解析、920 份 JSON 逐份复算（§5.1）。

### 1.1 WS1 冻结与按行复核（1–60）

| # | 事项 | 实值 / 证据 | 判定 |
|---:|---|---|---|
| 1 | 冻结台账（sha、HEAD、行数） | 批前 `887e3ba6…`/1231 行；批后 `19ea6b57…`/1231 行；HEAD `a7cecc63` | 完成 |
| 2 | A 域行级复核 | 65 PASS，JSON 65，0 缺失 | 部分（机械面） |
| 3 | B 域行级复核 | 88 PASS，JSON 88，0 缺失 | 部分（机械面） |
| 4 | C 域行级复核 | 88 PASS，JSON 88，0 缺失 | 部分（机械面） |
| 5 | D 域行级复核 | 81 PASS，JSON 81，0 缺失 | 部分（机械面） |
| 6 | E 域行级复核 | 81 PASS，JSON 81，0 缺失 | 部分（机械面） |
| 7 | F 域行级复核 | 48 PASS + 9 FAIL，JSON 57，0 缺失 | 部分（机械面） |
| 8 | G 域行级复核 | 82 PASS + 1 FAIL，JSON 83，0 缺失 | 部分（机械面） |
| 9 | H 域行级复核 | 68 PASS + 3 FAIL，JSON 71，0 缺失 | 部分（机械面） |
| 10 | I 域行级复核 | 53 PASS，JSON 53，0 缺失 | 部分（机械面） |
| 11 | J 域行级复核 | 29 PASS + 13 FAIL，JSON 42，0 缺失 | 部分（机械面） |
| 12–15 | A 域四项自检 | 覆盖 65/65；rev_bad 0；sha_bad 0；checks_empty 0 | 完成 ×4 |
| 16–19 | B 域四项自检 | 88/88；0；0；0 | 完成 ×4 |
| 20–23 | C 域四项自检 | 88/88；0；0；0 | 完成 ×4 |
| 24–27 | D 域四项自检 | 81/81；0；0；0 | 完成 ×4 |
| 28–31 | E 域四项自检 | 81/81；0；0；0 | 完成 ×4 |
| 32–35 | F 域四项自检 | 48/48；0；0；0 | 完成 ×4 |
| 36–39 | G 域四项自检 | 82/82；0；0；0 | 完成 ×4 |
| 40–43 | H 域四项自检 | 68/68；0；0；0 | 完成 ×4 |
| 44–47 | I 域四项自检 | 53/53；0；0；0 | 完成 ×4 |
| 48–51 | J 域四项自检 | 29/29；0；0；0 | 完成 ×4 |
| 52 | F-019 矛盾 | 台账 300–2150ms；gfxinfo `50th percentile: 2350ms` | 改 FAIL |
| 53 | F-028 矛盾 | 台账 487/597/820ms；日志 `PRODUCT_TIME_S=7.973` | 改 FAIL |
| 54 | F-029 无源数字 | `MistakeBatchPerf` 在 F 域日志 0 命中 | 改 FAIL |
| 55 | F-034 无源 | `export-batch1.instr.txt` 无 MistakeBatchPerf 行 | 改 FAIL |
| 56 | F-036 无源基线 | 归档产物 0 命中 | 改 FAIL |
| 57 | F-064 计数错误 | `dropbox-after.txt` `data_app_crash` 实测 6，台账 12 | 改 FAIL |
| 58 | F-067 计数错误 | 同上，实测 6 | 改 FAIL |
| 59 | H-052 硬编码行号 | `h_compliance_gate.py:578-582` 改为内容判定；`--only H-052` exit 0 | 修复后 PASS |
| 60 | H-066 硬编码行号 | 原 `splitlines()[825]`（`SecondaryScreens.kt:826` 为 `}`）；现 `:655-679` 内容锚点；exit 0 | 修复后 PASS |

> 说明：59/60 的“硬编码 → 内容锚点”修复在批次 46 前段完成，本轮只复跑 `--only`
> 并读实现确认不再取固定行号（§3 #11、§5.5）。

### 1.2 WS2 FAIL 重判（61–75，30 条旧 FAIL）

| # | 配对行 | 实算 / 新证据 | 判定 |
|---:|---|---|---|
| 61 | D0-030, D0-081 | `d0-self/verdicts.txt` 10 CHECK/0 FAIL；`protocol.txt` 6 CHECK/0 FAIL | 2 条改 PASS |
| 62 | C-065, F-055 | C-065：`c065-…xml` 1/0/0 设备；F-055：2h soak 2504s FATAL，未完成 2h | C-065 改 PASS；F-055 保留 FAIL |
| 63 | F-057, H-018 | F-057 soak 超限路径仍命中；H-018 `--only` exit 1（DOCX 无 /AIGC） | 均保留 FAIL |
| 64 | H-052, H-061 | H-052 exit 0；H-061 exit 1（KD-84） | H-052 改 PASS；H-061 保留 FAIL |
| 65 | H-062, H-066 | H-062 exit 1（KD-85）；H-066 exit 0（固定文案/拼接 0/取消重抛） | H-066 改 PASS；H-062 保留 FAIL |
| 66 | J-001, J-002 | identity exit 1；dirty 4739；APK revision 落后 HEAD 53 | 保留 FAIL |
| 67 | J-020, J-021 | signing `env_missing` 4/4；`tracked_keystores=[]` | 保留 FAIL |
| 68 | J-024, J-025 | 无生产签名，APK/AAB 身份与有效期无法证明 | 保留 FAIL |
| 69 | J-027, J-033 | `doc_mentions_env_contract=false`；aab16k ABI 4≠2 | 保留 FAIL |
| 70 | J-034, J-041 | aab16k exit 1；缺 `libquickjs.so` | 保留 FAIL |
| 71 | J-048, J-051 | versionCode=2 手写常量；版本源非生成 | 保留 FAIL |
| 72 | J-052, GATE-032 | 版本源非生成；report 门仍 exit 1 | 保留 FAIL |
| 73 | GATE-035, GATE-038 | GATE-035 改 PASS（`pass_with_review=888`）；GATE-038 lint 仍阻断 | 1 改 PASS；1 保留 FAIL |
| 74 | GATE-042, GATE-091 | lint 144 条 `EVIDENCE_REUSED`；AUD-08 4/7 | 均保留 FAIL |
| 75 | GATE-092, GATE-099 | AUD-08 4/7=0.5714；split 门 FAIL | 均保留 FAIL |

> 30 条旧 FAIL 结论：**6 改 PASS**（D0-030/D0-081/C-065/H-052/H-066/GATE-035）、
> **24 保留 FAIL**。另有 **7 条原 PASS 改 FAIL**（§1.1 52–58）、**1 条 UNVERIFIED 改 FAIL**
> （G-089），合计 **32 FAIL**。

### 1.3 WS3 UNVERIFIED 收敛（76–85，144 条）

| # | 域组 | 迁移实值 | 判定 |
|---:|---|---|---|
| 76 | D0 5 | → PASS 5（D0-031…035 行级 JSON 性质断言） | 完成 |
| 77 | A 24 | → BLOCKED 24 | 完成 |
| 78 | B 2 | → BLOCKED 2 | 完成 |
| 79 | C 12 | → PASS 10（JVM XML 0 fail）、BLOCKED 2 | 完成 |
| 80 | E 10 | → PASS 1（E-061 设备 3/3）、BLOCKED 9 | 完成 |
| 81 | F 23 | → BLOCKED 23 | 完成 |
| 82 | G 6 | → BLOCKED 5、FAIL 1（G-089 元数据缺失） | 完成 |
| 83 | H 7 | → BLOCKED 7 | 完成 |
| 84 | I 32 | → BLOCKED 32 | 完成 |
| 85 | J 22 + GATE 1 | → PASS 8、BLOCKED 15 | 完成 |

合计：**24 PASS / 1 FAIL / 119 BLOCKED / 剩余 UNVERIFIED 0**；逐域合计与
批次 45 §6.2 的 D0 5 / A 24 / B 2 / C 12 / E 10 / F 23 / G 6 / H 7 / I 32 / J 22 / GATE 1 完全闭合。

### 1.4 WS4 BLOCKED 确认（86–90）

| # | 事项 | 实值 | 判定 |
|---:|---|---|---|
| 86 | 原 161 条逐条确认 | 161 行仍 BLOCKED（组：Provider 10 / arm64 10 / 供应链 2 / 合规 12 / 无障碍 5 / 发布 34 / FIX 85 / 金标 3） | 完成 |
| 87 | runbook 指针覆盖 | 280/280 行 method 或证据含 `runbook.md` 指针；本轮逐行字符串复算 | 完成 |
| 88 | 内部热区/授权项单列 | 批次 45 §6.1 登记 151 外部 + 10 内部；本批未新增授权 | 完成（维持） |
| 89 | 新增 119 条入账 | 280 = 161 + 119，闭合；新增行均带 `BLOCKED: <原因>；出口见 runbook` | 完成 |
| 90 | `trace_requirements` | exit 0；`total=1200`、`gaps=[]` | 完成 |

### 1.5 WS5 报告与终裁（91–96）

| # | 事项 | 实值 | 判定 |
|---:|---|---|---|
| 91 | `acceptance_report --require-complete` | **exit 1**；`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED` | 完成（结论=不通过） |
| 92 | 重生成 verdicts/域索引/哈希清单 | 三份清单 `--verify` 均 exit 0；`verdicts.md`/`domain-index.md` 已重生成 | 完成 |
| 93 | `acceptance_lint` / `trace_requirements` | lint **exit 1**（144）；trace **exit 0**（gaps 0） | 完成（lint 仍红） |
| 94 | 终裁文件 | `docs/acceptance/adjudication.md` 结论 **NOT ACCEPTED**；§10 为修复环 | 完成 |
| 95 | 台账终态 sha 与统计 | `19ea6b57…`；888/32/280/0；`pass_with_review=888` | 完成 |
| 96 | 记录 | 本文件 | 完成 |

### 1.6 汇总

**96 个编号全部有判定：完成 86 / 部分 10 / UNVERIFIED 0。**
“部分”全部落在 2–11（行级复核是单会话机械复算，`independent_agent=false`）。
计划 §1 第 12–51 项文字中另有一句“抽样 5 行人工复算（由另一 agent）”——该要求
**不落在 96 个编号内**，本批未满足，单列 **UNVERIFIED**（§5.3、§6.1）。

## 2. 各轨实值

### 2.1 十域 + D0/FIX/GATE（本轮逐行解析台账 + 920 JSON 复算）

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | PASS 有 JSON | 域出口 |
|---|---:|---:|---:|---:|---:|---:|---|
| D0 | 100 | 100 | 0 | 0 | 0 | 100/100 | d0_self_acceptance 213 CHECK / 0 FAIL |
| A | 90 | 65 | 0 | 25 | 0 | 65/65 | runbook §9；大数据/权限/中断设备缺口 |
| B | 90 | 88 | 0 | 2 | 0 | 88/88 | runbook §9；mask=13 夹具缺口 |
| C | 90 | 88 | 0 | 2 | 0 | 88/88 | runbook §5/§9；C-008/C-088 阻塞 |
| D | 90 | 81 | 0 | 9 | 0 | 81/81 | runbook §9；真实 Provider 密钥 |
| E | 90 | 81 | 0 | 9 | 0 | 81/81 | runbook §9.1；并发/DST |
| F | 90 | 48 | 9 | 33 | 0 | 48/48 | runbook §9.1；arm64 与历史基线 |
| G | 90 | 82 | 1 | 7 | 0 | 82/82 | runbook §9；供应链元数据 |
| H | 90 | 68 | 3 | 19 | 0 | 68/68 | runbook §9/§13；KD-84/85 与热区 |
| I | 90 | 53 | 0 | 37 | 0 | 53/53 | runbook §6/§9；TalkBack/IME |
| J | 90 | 29 | 13 | 48 | 0 | 29/29 | runbook §9.2；签名/arm64/Play |
| FIX | 100 | 15 | 0 | 85 | 0 | 15/15 | runbook §13 FIX-T04–T20 |
| GATE | 100 | 90 | 6 | 4 | 0 | 90/90 | runbook §9；AUD-08 与人工金标 |
| **合计** | **1200** | **888** | **32** | **280** | **0** | **888/888** | 无域级 PASS |

> `docs/acceptance/domain-index.md:14-26` 的表与本轮逐行复算逐格一致。

### 2.2 FAIL 32 条（逐条出口见 `fix/fix-tickets-b46.md`）

- **F 9**：F-019/028/029/034/036/064/067 为复核发现的矛盾或无源数字（§1.1 52–58）；
  F-055/F-057 为 2h soak 未完成且归档 2504s FATAL。
- **J 13**：J-001/002（脏树、产物落后 53）、J-020/021/024/025/027（生产签名缺失）、
  J-033/034/041（ABI 4≠2、缺 `libquickjs.so`）、J-048/051/052（versionCode=2 手写）。
- **H 3**：H-018（DOCX 无文档级 `/AIGC`）、H-061（KD-84）、H-062（KD-85），
  本轮 `--only` 均 exit 1。
- **GATE 6**：GATE-032/038/042（report/lint 门）、GATE-091/092/099（AUD-08 4/7）。
- **G 1**：G-089 `gradle/verification-metadata.xml` 不存在（本轮 `test -f` 确认 missing）。
- 未洗绿：32 条 verdict 全部仍为 FAIL；7 条是原 PASS 因证据矛盾**降级**为 FAIL。

### 2.3 UNVERIFIED 迁移统计

| 迁移 | 数量 | 复核依据 |
|---|---:|---|
| UNVERIFIED → PASS | 24 | C 域 JVM XML 0 fail；D0 性质断言；E-061 设备 3/3；J 域 grep/zipfile/signing/adb 0 fail |
| UNVERIFIED → BLOCKED | 119 | 每行带 runbook 指针；trace gaps 0 |
| UNVERIFIED → FAIL | 1 | G-089 元数据缺失（实读文件系统） |
| 剩余 UNVERIFIED | **0** | `acceptance_report` 实读 |

### 2.4 BLOCKED 280

161（批前）+ 119（UNVERIFIED 转入）= 280；逐行 `runbook.md` 指针 **280/280**（§1.4 #87）。
BLOCKED 的签字含义是**条件缺失**，不是通过：真实 Provider、生产签名、arm64/16KB、
Play Console、人工金标、TalkBack/IME、内部热区授权均未取得。

### 2.5 行级复核的结构面（1200/920 复算）

| 检查 | 结果 |
|---|---|
| JSON 文件数 = PASS + FAIL | 920 = 888 + 32；missing 0；extra 0 |
| id / verdict 与台账一致 | 0 mismatch |
| `reviewer∉authors` | 920/920 |
| `ledger_sha256 = 19ea6b57…` | 920/920 |
| `checks` 非空 | 920/920；总 check 数 4713（均值 5.12/行） |
| `evidence_sha256` 与磁盘字节 | 0 mismatch；0 缺失 |
| `run_id` | 618 × `…08:50:11Z-batch46-line-review`、294 × `…11:28:35Z-batch46-repair-line`、7 × `…17:30:00Z-batch46-convergence`、1 × `…09:02:43Z` |
| `checks` 共用 | 24 组 ≥5 行，覆盖 **156** 行；`pass_with_row_specific_review=575/888`（§5.2） |

## 3. 门禁 exit（本轮逐字命令与 exit）

| # | 命令（逐字） | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_init.py --check` | **0** | 1200 行无漂移（无错误输出） |
| 2 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete` | **1** | `PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0`；2 条 violation：`NOT_ACCEPTANCE_COMPLETE`、`REVIEW_CHECKS_SHARED`；`pass_with_review=888`、`pass_without_review=0`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=575` |
| 3 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **1** | `violations=0`、`warnings=24`（全部 `REVIEW_CHECKS_SHARED`）、`state=FAIL` |
| 4 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **1** | total=1200；violations=144、warnings=144（全部 `EVIDENCE_REUSED`） |
| 5 | 同命令 `--warn-only` | **0** | violations=0、warnings=144 |
| 6 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **0** | total=1200；`gaps=[]` |
| 7 | `python -I tools/ci/d0_self_acceptance.py --group all` | **0** | `CHECKS=213 FAILURES=0` |
| 8 | `… --group docs --section verdicts` | **0** | `CHECKS=10 FAILURES=0`；`real_verdict_json=920`、`verdict_schema_files=1` |
| 9 | `… --group docs --section protocol` | **0** | `CHECKS=6 FAILURES=0`；冲突规则锚点命中 |
| 10 | `… --group lint` / `--group hash` | **0 / 0** | 24 CHECK / 0；15 CHECK / 0 |
| 11 | `h_compliance_gate.py --only H-018` | **1** | DOCX 无文档级 `/AIGC`；KD-84/85 部分页与模型改卷 |
| 12 | `… --only H-052` | **0** | `H-052 PASS 文案与 PDF 隐式标识现状一致` |
| 13 | `… --only H-061` | **1** | 部分页 `writeSubset` 丢文档级 `/AIGC`（KD-84 open） |
| 14 | `… --only H-062` | **1** | 模型改卷文本未纳入 `/AIGC`（KD-85 open） |
| 15 | `… --only H-066` | **0** | `固定文案=True 拼接命中=0 取消重抛=True` |
| 16 | `acceptance_hash_manifest.py --root docs/research/evidence/2026-11-06-batch-46 --verify …/EVIDENCE.sha256` | **0** | `OK` |
| 17 | `… --root docs/acceptance/verdicts --verify …/REVIEWS.sha256` | **0** | `OK` |
| 18 | `… --root docs/acceptance/evidence/2026-11-06-batch-46/gate --verify …/GATES.sha256` | **0** | `OK` |
| 19 | `j_release_verify.py identity --root . --apk … --aab … --mapping … --gradle app/build.gradle.kts --metadata … --require-clean --require-head` | **1** | `dirty_paths=4739`；APK revision `faab17f5…`，`commits_behind_head=53` |
| 20 | `j_release_verify.py signing --root .` | **0** | `env_missing` 4/4；`tracked_keystores=[]`；`doc_mentions_env_contract=false` |
| 21 | `j_release_verify.py version --gradle app/build.gradle.kts --metadata …` | **0** | `versionCode=2`、`versionName=0.2.0-offline` |
| 22 | `j_release_verify.py aab16k --aab …/app-strictOffline-release.aab` | **1** | ABI `[arm64-v8a, armeabi-v7a, x86, x86_64] != [arm64-v8a, x86_64]`；缺 `libquickjs.so` |
| 23 | `python -I tools/split_golden/check_split_gate.py` | **1** | `countExactRate=4/7=0.5714 < 0.9`；`fragmentRate=0/16` |
| 24 | `pytest -q tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py` | **0** | 38 passed |
| 25 | `pytest -q tools/tests/test_h_compliance_gate.py` | **0** | 90 passed |
| 26 | `pytest -q tools/tests/test_j_release_verify.py` | **0** | 19 passed |
| 27 | `pytest -q tools/tests/test_d0_self_acceptance.py` | **0** | 13 passed |
| 28 | `pytest -q tools/tests/test_split_gate.py` | **0** | 61 passed |
| 29 | `pytest -q tools/tests/test_acceptance_init.py tools/tests/test_trace_requirements.py tools/tests/test_acceptance_hash_manifest.py` | **0** | 21 passed |
| 30 | `adb -s emulator-5562/5584 shell "getconf PAGE_SIZE; getprop ro.product.cpu.abi; getprop ro.build.version.sdk"` | **0** | 两台均 `4096` / `x86_64` / `34`；不能外推 arm64 或 16KB |
| 31 | 只读复算脚本（`python -I -` 读取台账与 920 JSON；§5.1） | **0** | 1200 行 / 920 JSON / 0 错误 |

**本轮未跑（不得读作通过）**：全部 Gradle 任务（含 `:core:data:testDebugUnitTest`）、
`am instrument` 设备轮、2h soak、arm64/16KB 真机、真实 Provider、Play Console、
人工金标、TalkBack/IME。这些项的结论只来自归档证据字节，本轮未重放执行链。

## 4. 报告统计前后

| 指标 | 批前（`887e3ba6…`/HEAD） | 批后（`19ea6b57…`） | Δ |
|---|---:|---:|---:|
| PASS | 865 | **888** | +23 |
| FAIL | 30 | **32** | +2 |
| BLOCKED | 161 | **280** | +119 |
| UNVERIFIED | 144 | **0** | −144 |
| `pass_with_review` | 0 | **888** | +888 |
| `pass_without_review` | 865 | **0** | −865 |
| `pass_with_invalid_review` | —（旧工具无此口径） | **0** | — |
| `pass_with_row_specific_review` | — | **575/888** | — |
| 行级 JSON | 0 | **920** | +920 |
| `acceptance_report --require-complete` | exit 1（`NOT_ACCEPTANCE_COMPLETE` + `PASS_WITHOUT_REVIEW`） | **exit 1**（`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`） | 阻断项换了一条 |
| `acceptance_lint`（默认） | exit 0（144 告警不阻断） | **exit 1**（144 违规） | 反作弊面改为默认阻断 |
| `d0_self_acceptance --group all` | 205 CHECK（批次 45） | **213 CHECK / 0 FAIL** | +8 |

- 批前数据源：`docs/research/2026-11-06-batch-46-baseline.md` §2 的实跑输出
  （`PASS 865 / FAIL 30 / BLOCKED 161 / UNVERIFIED 144`、`pass_with_review=0`）。
- 批后数据源：本轮 §3 #2 的 JSON 输出；盘上报告
  `docs/acceptance/acceptance-report.json` sha256 `a7927de6…c473b`（888/32/280/0、exit 1）。
- 归档副本 `docs/research/evidence/2026-11-06-batch-46/gate/acceptance-report.json`
  sha256 `11703423…2144`（字节不同于盘上报告，但同 sha/同计数/同 exit）。
- 差值的代数闭合：`865+24(UNVERIFIED→PASS)+6(FAIL→PASS)−7(PASS→FAIL)=888`；
  `30−6+7+1=32`；`144−24−1=119` 转入 BLOCKED；`161+119=280`。

## 5. 复核与修复

### 5.1 920 份行级 JSON 的独立复算（本轮）

只读脚本逐份解析（命令见 §3 #31），结果与 `gate/regen-validation.json`
（`clean_rows=920 rows_with_errors=0 missing_rows=0`）一致：

- 920 份文件 = 888 PASS + 32 FAIL；id/verdict 与台账 0 mismatch；
- `reviewer` 全部存在且不在 `authors`；`ledger_sha256` 全部 `19ea6b57…`；
- `checks` 4713 条、无空壳；`evidence_sha256` 逐文件重算 0 mismatch、0 缺失；
- `run_id` 四个批次（618/294/7/1），均带 ISO 时间戳且不早于台账批次。

**这证明的是“证据字节与可判定字段一致”，不是“检查项足以证明该行”。**

### 5.2 checks 判别力（本轮按工具同口径复算）

| 指标 | 实值 |
|---|---|
| 非 `ledger_row` check 指纹组数（≥5 行） | **24** |
| 被共用行数（≥5 组覆盖） | **156** |
| 最大组 | **9 行** |
| `pass_with_row_specific_review` | **575 / 888** |

工具口径见 `tools/ci/acceptance_report.py:271`（指纹 = 非 `ledger_row` checks 的
规范化 JSON 串 sha256）与 `:564-568`（≥5 行即 `REVIEW_CHECKS_SHARED`）。
本轮独立复算得到同样 24 组、同样 575/888，确认该阻断不是脚本噪声。

### 5.3 独立性与“另一 agent 抽样”边界

- 920/920 `independence = {reviewer_role_only: true, independent_agent: false}`。
- 计划要求的“抽样 5 行人工复算（由另一 agent）”**未满足**：本会话没有第二个 agent；
  同会话内另一条脚本复算不构成主体独立。该项记 **UNVERIFIED**，不冒充完成。
- 顶层 `docs/acceptance/verdicts/{A..J}-review.json` 仍绑 `bb25ef23…`，本轮不当行级证据。

### 5.4 修复环（finding 1–6 与本轮复算）

| 修复 | 实值 |
|---|---|
| `pass_with_review` 改为逐项复算 | `tools/ci/acceptance_report.py` 调用 `acceptance_review.validate_review()`；`pass_with_invalid_review=0` |
| 294 份 batch44 `run_id` 重绑 | 重绑前逐份复算通过；现 `run_id=…11:28:35Z-batch46-repair-line-review-*`，旧值存 `rebind_from_run_id` |
| checks 共用检测 | 24 组；报告新增 `review_shared_check_groups` 与 row-specific 计数 |
| 证据新鲜度 | `review_evidence_batches`：批 44=1472、批 45=402、批 46=62、unlabeled=380 |
| 独立性表述 | 920 份 `review_kind=mechanical-recompute`；文档统一改称“机械复算” |
| lint 默认阻断 | `tools/ci/acceptance_lint.py:153-155`：`escalate = strict_warnings or not warn_only` |
| XML 安全 | `tools/ci/acceptance_review.py:48,91-94`：解析前拒绝 `<!DOCTYPE|<!ENTITY`，不使用外部实体 |
| 7 份矛盾 JSON 重建 | D0-030…035、GATE-035，`reviewer=batch46-convergence-reviewer`，checks 改为直接复算 |
| 6 份 FAIL JSON 清理 | GATE-032/038/042/091/092/099，删除历史 sha 重复/假 `evidence_missing`；verdict 未变 |

### 5.5 矛盾行与假通过（本批核心发现）

- **H-066 假通过**：旧实现取 `splitlines()[825]`，而 `SecondaryScreens.kt:826` 是 `}`，
  因此永远 PASS。现 `tools/ci/h_compliance_gate.py:655-679` 以
  `exportDiagnostics` 回调块为内容锚点，断言固定文案存在、无 `failure.message` 拼接、
  `CancellationException` 重抛；测试同步改为同一锚点，`--only H-066` exit 0、pytest 90/90。
- **H-052**：`h_compliance_gate.py:578-582` 以文案/实现内容判定，不再依赖行号；exit 0。
- **F 域 7 条**：原始证据字节与台账数字矛盾（§1.1 52–58），已降级 FAIL，未保留绿判词。
- **C-044**：原复核只引测量侧证据，补入
  `c-visualdrawopspainterinstrumentedtest.xml`（24 例 0 失败，含 pinned flags 用例）后维持 PASS。

### 5.6 从 HEAD 不可重放的 6 行（不改判，列风险）

`D-010/015/016/017/018/027` 的 `checks[1].sha256_equals_head_blob=false`、
`git_status_clean=false`——证据源文件是工作树 `M`/`??` 的测试源码。其 XML/CMD 证据为绿，
但不能从 HEAD 重放；**保留 PASS 并列为遗留风险**（§6.5）。

## 6. 遗留与风险

1. **语义复核缺口（UNVERIFIED）**：920 份是单会话机械复算；计划要求的“另一 agent 抽样
   5 行人工复算”与“第二人/第二模型语义复核”均未完成。888 条 PASS 的**语义充分性不签**。
2. **行级判别力缺口**：24 组 checks 被 ≥5 行共用（156 行），575/888 有唯一指纹；
   `REVIEW_CHECKS_SHARED` 是 `--require-complete` 的第二条阻断。
3. **32 条 FAIL**：P0 集中在 J 域发版工程（签名/干净树/ABI/vc）、H 域 AIGC 标识
   （DOCX 文档级 `/AIGC`、部分页打印、模型改卷）、F 域 2h soak；出口见
   `fix/fix-tickets-b46.md` 的 32 行判据。**没有一条被洗绿。**
4. **280 条 BLOCKED**：真实 Provider、生产签名、arm64/16KB、Play Console、人工金标、
   TalkBack/IME、内部热区授权未取得；runbook §6/§9/§9.1/§9.2/§13。
5. **6 条 PASS 绑未提交源码**（§5.6）：从 HEAD 不可重放。
6. **证据新鲜度**：本轮引用的证据绝大多数是归档字节（批 44=1472、批 45=402、批 46=62、
   unlabeled=380）；`868/920` 行的复核只重算旧字节，不是本轮重跑。
7. **未跑项**：Gradle 全量、设备仪表化轮、2h soak、arm64/16KB 真机、真实 Provider、
   Play Console、人工金标、TalkBack/IME。JVM/设备数字来自归档 XML，不能读成本轮复跑。
8. **不可变量**：KD-76 仍 open；isolation 仍 PARTIAL 不定案；p09 未收敛（沿用批次 45
   三次 2071/1890/1861 ms）；AUD-08 仍 FAIL（4/7）。发版不成立。
9. **GATE-035 的证据是“读报告字段”而非独立判据**：本轮实读
   `gate/pass-with-review-check.log`，其命令是对 `acceptance-report.json` 断言
   `pass_with_review==counts.PASS and pass_without_review==0`，输出
   `PASS 888 pass_with_review 888 pass_without_review 0`、EXIT=0。该 log 只证明
   “报告字段自洽”，不独立证明 888 份复核内容成立；台账 verdict 未改，低 severity 遗留。

## 7. 提交清单（建议块，≤6 路径/块）

> 本轮记录员**未做任何 git 写**（`git diff --cached` = 0）。以下只是建议块；
> **不得提交**：`build/*`（含 `build/b46*`、`build/rec46-*`）、`gradle.lockfile`、
> `.mimosa/`；**热区另裁**：`core/domain/*`、`core/ui/*`、`feature/*`、`docs/current/*`、
> `app/src/main/kotlin/.../SecondaryScreens.kt`。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| C1 | `docs/acceptance/ledger.md`、`verdicts.md`、`domain-index.md`、`acceptance-report.json` | 1200 行终态（563KB，需确认入库策略） |
| C2 | `docs/acceptance/adjudication.md`、`runbook.md`、`verdicts/verdict.schema.json`、`ledger.schema.json` | 终裁 + 协议 + JSON schema |
| C3 | `docs/acceptance/verdicts/rows/`（A–E 段） | 行级复核 1（约 400 份） |
| C4 | `docs/acceptance/verdicts/rows/`（F–J 段） | 行级复核 2 |
| C5 | `docs/acceptance/verdicts/rows/`（D0/FIX/GATE 段） | 行级复核 3（含 FAIL 行） |
| C6 | `docs/acceptance/evidence/2026-11-06-batch-46/{EVIDENCE,REVIEWS,GATES}.sha256`、`gate/regen-validation.json` | 哈希与复算清单（18 文件） |
| C7 | `docs/research/evidence/2026-11-06-batch-46/00-index.md`、`d0-self/`、`gate/` | D0 与门禁证据 |
| C8 | `docs/research/evidence/2026-11-06-batch-46/{c,e,h,j}-domain/` | JVM/设备/合规/发布证据 |
| C9 | `docs/research/evidence/2026-11-06-batch-46/fix/fix-tickets-b46.md` | 32 条 FAIL 出口 |
| C10 | `docs/research/2026-11-06-batch-46-record.md`、`2026-11-06-batch-46-baseline.md` | 本记录 + 基线 |

> `docs/acceptance/verdicts/rows` 当前被 git 跟踪 0 份；不提交则无法从 HEAD 重放
> 行级复核。热区历史改动（`core/domain/TutorJudgedReview.kt`、`docs/current/*` 等，mtime
> 2026-11-01～11-04）不属于本批，**不得混入本批提交块**。

## 8. 最终判词（与终裁一致）

**批次 46 验收不通过（NOT ACCEPTED）；发版不成立。**

- `acceptance_report --require-complete` **exit 1**：`NOT_ACCEPTANCE_COMPLETE`
  （32 FAIL + 280 BLOCKED）与 `REVIEW_CHECKS_SHARED`（24 组）同时阻断。
- 888 条 PASS 的**机械面**已闭环（920 JSON、reviewer∉authors、ledger sha、evidence sha、
  0 无效复核）；**语义面与主体独立性**未闭环。
- 32 条 FAIL 未洗绿；280 条 BLOCKED 是条件缺失；UNVERIFIED 已归零但其中 119 条只是
  转成了带出口的 BLOCKED。
- 6C-2 未漂移；KD-76 open；isolation 不定案；p09 未收敛；AUD-08 FAIL。

## 9. 修复环附录（审计 findings 1–6；承接 `adjudication.md` §10 的指针）

> `docs/acceptance/adjudication.md` §10 写“逐项细节见本记录 §9”。本节保留该锚点，
> 内容为本轮**逐项复算**后的实值；与 §5.4 重叠处以本节为详版。

| finding | 严重度 | 处置 | 本轮复算 |
|---|---|---|---|
| 1. 294 份复核 `run_id` 仍是 batch44，却声明 batch46 台账 sha | 高 | 逐份重新执行 `validate_review()` 后重绑为 `…11:28:35Z-batch46-repair-line-review-*`；旧值存 `rebind_from_run_id` | `run_id` 前缀计数 **294**；台账 sha 仍 `19ea6b57…`；0 份复算失败 |
| 2. `pass_with_review` 只数 review token 存在，不读 `checks[]` | 高 | 新增 `tools/ci/acceptance_review.py`；报告逐项复算 `checks[]` 与 `evidence_sha256`，拒绝空 checks | `pass_with_review=888`、`pass_with_invalid_review=0` |
| 3. 同组多行 checks 逐字节相同，无行级判别力 | 中 | 报告新增非 `ledger_row` 指纹；≥5 行共用触发 `REVIEW_CHECKS_SHARED` | **24 组 / 156 行**；`pass_with_row_specific_review=575/888`；最大组 9 |
| 4. 868/920 行只复算旧归档字节 | 中 | 每份 JSON 增 `evidence_freshness`；报告增 `review_evidence_batches` | 批 44=1472、批 45=402、批 46=62、unlabeled=380 |
| 5. 单会话批量生成、reviewer 只是角色标签 | 中 | 920 份增 `review_kind=mechanical-recompute`、`independence.independent_agent=false`；文档统一改称“机械复算” | 920/920 两项字段一致 |
| 6. `acceptance_lint` 非 strict 模式 exit 0 掩盖 144 条 `EVIDENCE_REUSED` | 中 | 默认把复用告警升级为违规（`acceptance_lint.py:153-155`）；`--warn-only` 仅诊断 | 默认 **exit 1 / 144**；`--warn-only` exit 0 / 144 |

**修复环内的行级修补（verdict 未变）**

- 7 份 PASS JSON 重建：D0-030、D0-031、D0-032、D0-033、D0-034、D0-035、GATE-035；
  `reviewer=batch46-convergence-reviewer`、`run_id=…17:30:00Z-batch46-convergence-repair`、
  各 4 条 checks；本轮复算 evidence sha 0 mismatch、checks 非空。
- 6 份 FAIL JSON 清理：GATE-032/038/042/091/092/099，删除历史 sha 重复与
  `evidence_missing exists=False` 假检查；`regenerated_by` 均在，verdict 仍 FAIL。
- XML 解析安全：`tools/ci/acceptance_review.py:48` 定义 `DOCTYPE_RE`，`:91-94`
  在 `ElementTree.fromstring` 前拒绝 `<!DOCTYPE|<!ENTITY`，不使用外部实体。

**修复环的诚实边界**

- 修复后的 `--require-complete` 仍是 exit 1：`NOT_ACCEPTANCE_COMPLETE`（32 FAIL +
  280 BLOCKED）与 `REVIEW_CHECKS_SHARED`（24 组）同时阻断。
- “888 有复核”有复算支撑，但只有 575 行具备唯一 checks 指纹；
  313 行 `checks_scope=shared-suite`（两份记录中的 318 为旧计数误差，以本轮
  `575+313=888` 的复算为准）。
- 复核仍不是独立主体复核（`independent_agent=false`）；“另一 agent 抽样 5 行”与
  “第二人/第二模型语义复核”仍是 **UNVERIFIED**。

## 9. 主会话收口（2026-11-05）

### 9.1 终态（主会话实跑）

| 项 | 实值 |
|---|---|
| 台账 | 1200 行；sha `19ea6b57…`（本批后） |
| 计数 | **PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0** |
| `pass_with_review` | **888 / 888**；`pass_without_review=0` |
| 行级 JSON | **920 份**（888 PASS + 32 FAIL），0 缺失、0 硬错误 |
| `acceptance_report` | exit=1（violations 0；state=FAIL 因 32 FAIL） |
| `--require-complete` | exit=1（`NOT_ACCEPTANCE_COMPLETE` UNVERIFIED=0 FAIL=32 BLOCKED=280；`REVIEW_CHECKS_SHARED` 24 组 checks 被 ≥5 行共用，318 行 `checks_scope=shared-suite`） |
| `acceptance_lint` | exit=1（144 违规/144 警告，反作弊面阻断 GATE-042） |

### 9.2 终裁：**NOT ACCEPTED（验收不通过）**

我确认 `docs/acceptance/adjudication.md` 结论，并签字范围与批次 45 相同：

- **可签字**：结论「验收不通过」；280 行 BLOCKED 清单（条件缺失，不判 PASS）；888 条 PASS 已有行级机械复核这一事实（仅指“复算通过”，不指“功能已验收”）。
- **不签字**：32 条 FAIL；318 行共享 checks 的行级判别力；发版就绪；任何“批次验收通过”。

### 9.3 五条阻断的更新

1. 零复核已消除：`pass_with_review=888/888`。
2. UNVERIFIED 已归零（144→0）：可跑的都跑了，其余转 BLOCKED。
3. 新阻断：`REVIEW_CHECKS_SHARED`（24 组 checks 被 ≥5 行共用；318 行 `checks_scope=shared-suite`）——行级判别力不足，需按行细化 checks。
4. 32 条 FAIL 有当前证据：需逐条修或保留；不允许洗绿。
5. 280 条 BLOCKED：外部条件（Provider/签名/arm64/金标/热区）与内部授权缺一不可。

### 9.4 提交（5 笔，已推送）

工具（行级复算/共享 checks 检测/lint 阻断）→ 台账/报告/终裁/域索引 → 920 份行级 JSON + schema → 基线/记录/证据。

### 9.5 若继续（批次 47）

1. 32 条 FAIL 逐条修（先红后绿）或保留 FAIL 并写缺陷；
2. 318 行 shared-suite checks 细化到行（每行至少 1 项行特有复算）；
3. 280 条 BLOCKED 逐条确认 runbook，外部项列给用户/他线；
4. 重跑 `--require-complete`；只有 FAIL=0 且 shared checks 清零，才可能签“通过”。
