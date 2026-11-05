<!-- 体量自查：落盘后 wc -lc 实测 484 行 / 45,708 B（≤65,536 B 目标）。 -->

# 批次 45 记录（2026-11-05/06）：验收闭环批——84 项对账、十轨实值、终裁“不通过”

> **来源与边界**：本文件由**批次 45 记录员**落盘，是本次 ask 的**唯一交付写入**
> （`docs/research/2026-11-06-batch-45-record.md`；临时命令输出在 gitignored
> `build/b45-*`、`build/batch45-postfix-*`）。未改台账、未改工具、未改源码/热区、
> 未做 git 写、未跑 Gradle、未跑 adb/设备。
>
> **本轮亲手实跑（逐字见 §3.4）**：`git rev-parse/status/ls-files`、台账 1200 行逐行复算、
> `acceptance_init --check`、`acceptance_report`（普通 / `--require-complete`）、
> `acceptance_lint`（普通 / `--strict-warnings`）、`trace_requirements`、
> `acceptance_hash_manifest --verify` 三份清单、`h_compliance_gate --only` 五条、
> `d0_self_acceptance --group docs --section verdicts|protocol`、`d0_self_acceptance --group all`、
> `check_backup_plan_sync`、`split_golden/check_split_gate`、`fix_relapse_gate --all --prove-red`、
> `git apply --check acceptance_ci.patch`、`sha256sum -c` E 域清单、工具 pytest 44 例（UTF-8 / 非 UTF-8 各一次）、
> 源文件实读（`BackupSelection.kt`、`h_compliance_gate.py`、`SecondaryScreens.kt`、`d0_self_acceptance.py`）。
>
> **本轮未跑**：任何 Gradle（单测门/强门/定向单测均为**转录** `build/` 与 `gate/` 日志）、
> 任何 adb/设备、真实 Provider、Play Console、人工金标、arm64、2h soak。
> §4 设备事实**全部来自证据文件**，不是本轮运行。
>
> **状态口径**：完成 = 载体在树 + 有可指认证据（本轮实跑，或证据文件可指认）；部分 = 主链在树但有明确缺口；
> BLOCKED = 外部条件缺失或纪律禁止；未达成 = 目标被证否；UNVERIFIED = 无法判定，**不得写通过**。

- 计划：`.zcode/plans/plan-2026-11-06-batch-45-acceptance-close.md`（§1 84 项、§2 硬约束、§3 验收）
- 上一批：`docs/research/2026-11-06-batch-44-record.md`（§9.2 验收 FAIL 不签字；§9.5 批次 45 入口）
- 终裁：`docs/acceptance/adjudication.md`（**NOT ACCEPTED**，本记录不替代它，只做对账与独立复核）
- 台账：`docs/acceptance/ledger.md`（`887e3ba6…`）；域导航 `docs/acceptance/domain-index.md`

## 0. 口径与基线

### 0.1 项数口径（1200 = 100 + 900 + 100 + 100）

`python -I tools/ci/acceptance_init.py --check` **exit 0**；本轮另用逐行 `split('|')`、按 ID 前缀归域独立复算：
**1200 行、dup ids 0、bad verdict 0**，域分布 D0 100 + A–J 各 90 + FIX 100 + GATE 100。计划 §1 的 84 项与本批实账无数量缺口。

但“1200 项逐项有 verdict”只等于**枚举完整**：当前非 PASS 共 **335 行**（FAIL 30 + BLOCKED 161 + UNVERIFIED 144）。批次 44 的 532 行非 PASS / 300 行结构性未判定已不复存在——D0/FIX/GATE 300 行**已全部回填**，这是本批最大的进展。

### 0.2 基线事实（本轮实读/实跑）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD | `87604198feeef198d5dba7343b8c4692c5e8f937`（`87604198`） | 本轮 `git rev-parse HEAD` |
| 工作树 | `porcelain` **611**；`-uall` **4091**；暂存 **0** | 本轮 `git status --porcelain` / `-uall` / `git diff --cached --name-only \| wc -l` |
| 台账 | sha256 **`887e3ba6…89aa`**，512,416 B，mtime `2026-11-05 14:18:41 +0800` | 本轮 `sha256sum` / `stat` |
| HEAD 上的台账 | **`02ba5307…e65`**（旧字节） | 本轮 `git show HEAD:docs/acceptance/ledger.md \| sha256sum` |
| 盘上报告 | `docs/acceptance/acceptance-report.json`：`ledger_sha256=887e3ba6…`、`865/30/161/144`、`exit_code=1` | 本轮实读该文件 |
| 报 evidence 旧值 | `gate/acceptance-report.json` 绑 **`bb25ef23…`**、`882/30/144/144` | 本轮实读 `gate/acceptance-counts.txt`、`gate/acceptance-report.txt` |
| 6C-2 行为面 | `ALLOWED_MASKS = linkedSetOf(9, 13, 15)`；`isCreatable = isClosed && mask in ALLOWED_MASKS`；`require(mask in ALLOWED_MASKS)` | `core/domain/.../BackupSelection.kt:166 / :90 / :140`（本轮实读） |
| 6C-2 工作树 | `git diff --stat -- BackupSelection.kt` **空** | `gate/sixc2-source-read.txt:19-21`；本轮实读 |
| 6C-2 测试 | `SixC2RulingInvariantTest` `tests="3" failures="0" errors="0"` | `gate/sixc2-targeted.xml:2`（本轮实读） |
| 证据跟踪态 | 两个 batch-45 证据根 `git ls-files` 均 **0**；`docs/acceptance/verdicts` **2**（仅 README/_template） | 本轮实测 |
| 本批提交 | **0**（共享工作树，记录员不做 git 写） | 本轮 `git diff --cached` = 0 |

### 0.3 三代台账：引用必须带 sha（本批最重要的口径事实）

1. **终态 `887e3ba6…`（本轮实测为准）**：`865 PASS / 30 FAIL / 161 BLOCKED / 144 UNVERIFIED`，report **exit 1**。
2. **修复环前 `bb25ef23…`（gate 证据与本轮 10 份复核 JSON 绑的版本）**：`882 / 30 / 144 / 144`。
   差值来自修复环对 **35 行** 的改判（`F-review.json.ledger_binding_note` 原文）：17 个 FIX“不可修登记”行 PASS→BLOCKED、13 个 BLOCKED 补 runbook 指针、4 个 D 行加脏树披露。17 的位移在计数上完全闭合：`882-17=865`、`144+17=161`。
3. **HEAD `02ba5307…`**：干净 checkout 上的字节，**不是**本批终裁的台账。

**后果**：`gate/acceptance-report.json`（882）与 `docs/acceptance/acceptance-report.json`（865）是**两代字节**，不得混引；任何“882 PASS”只能读作修复环前快照。同样，10 份 `{A..J}-review.json` 绑 `bb25ef23…`，**不是**新台账的行级 review 证据（§2.5）。

### 0.4 不可变清单与热区（本批执行口径）

- **6C-2 三项**（用户已确认）：mask=8 不提供、mask=0 禁止、派生索引恒裁。本批**只读确认**：源码常量 `{9,13,15}`、`require` 两处、工作树零 diff、定向测试 3/3（§0.2）。行为未漂移。
- **KD-76 open**：`docs/known-defects.md:2916` 标题仍 `KD-76 (open, …)`；A6 探针三处工具各 exit 2 → 维持 open（§2.7）。
- **isolation 不定案**：`docs/known-defects.md:2745` PARTIAL；低负载门 `INVALID`（`la_ratio_max`）→ 维持。
- **p09 未收敛**：三次 2071/1890/1861 ms 全 > 1300 ms 窗 → 维持。
- **AUD-08 FAIL**：本轮实跑 `check_split_gate.py` **exit 1**，`countExactRate 4/7=0.5714 < 0.9`。
- **热区禁改**：`core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、Application/Root。
- **纪律**：Gradle 只在门禁阶段串行；禁 `connectedLocalFirstDebugAndroidTest`；本轮记录员零 Gradle。

## 1. 84 项逐项对账

> 对账口径：计划 §1 的 84 个编号逐条判定，不合并、不跳号。
> 状态合计：**完成 53 / 部分 13 / BLOCKED 17 / 未达成 1**（分项见各表尾）。

### 1.1 WS1 E 域重录（12 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 1–10 | E-001…E-090 逐行把 `<E-domain classes>` 占位换成逐字命令、实跑、落日志、更新 verdict | **完成** | `e-domain-data-jvm.cmd.log:1` 现为 `CMD: ./gradlew --offline -q :core:data:testDebugUnitTest --tests <20 个全限定类名逐字>`，`EXIT=0`；`e-domain-domain-jvm.cmd.log` 同形。report `violations=[]`（批次 44 的 101 条 `PLACEHOLDER_CMD` 已归零） |
| 11 | E 域 90 行无 `<`/`...`/占位 | **完成** | 本轮逐行扫 E 域 90 行 `方法+证据` 两列：`placeholder-ish tokens: 0` |
| 12 | E 域证据 sha 清单重生成 | **完成** | `sha256sum -c docs/research/evidence/2026-11-05-batch-44/e-domain/sha256.txt` → **47/47 OK，exit 0** |

- **口径注**：逐字命令是**模块级**（一条 `--tests` 长列表覆盖一整个模块），不是 90 条独立命令；80 条 E PASS 共用 2 条模块日志，因此 `EVIDENCE_REUSED`（140 条警告中的两大宗：56 行 + 30 行）是结构事实，不是作弊。计划括号允许“`:core:domain:test` 全量”，故判完成。

### 1.2 WS2 D0 自验收（12 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 13–22 | 工具 10 组自验收：工具→自测命令→exit→证据→verdict，含假绿回放/自突变/哈希 verify | **部分** | 归档时 10 组各 `EXIT=0`、17 份 pytest XML 全绿、假绿回放（批次 41/42）仍红（`d0-self/00-index.md` §1–§2）。**但本轮复跑 `--group all` 得 205 CHECK / 5 FAILURES / exit 1**（§5 F3）：断言把修复前的红状态写成了期望值 |
| 23 | D0 100 行 verdict 非空 | **完成** | 本轮复算 D0 `93 PASS / 2 FAIL / 0 BLOCKED / 5 UNVERIFIED`，空 verdict 0 |
| 24 | `acceptance_report`/`lint`/`trace` 在 D0 自身上的 exit 记录 | **完成** | `gate/acceptance-report.txt`、`lint.txt`、`trace.txt` 各有 exit；本轮复跑同值（§3.2） |

### 1.3 WS3 FIX 域（24 项：1 个建单 + 20 单 + 3 个收口）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 25 | 从 FAIL + BLOCKED 生成 20 个修复单（id/严重度/落点/判据） | **完成** | `fix/fix-tickets.md` 20 行表：id、严重度、覆盖源行、落点、关闭条件、本批处置齐全；覆盖 23 FAIL + 73 BLOCKED = 96 源行。**计划写“FAIL 21”，实际开工时 23**（口径差 2，见 §5 F7） |
| 26 (T01) | 滑杆端点越界 | **部分** | 已修复 `587098f1`，单测 4 例绿；**设备 instrumented 复跑未做** |
| 27 (T02) | 失败文案拼接原始异常 | **完成** | 本批真修 6 处 `catch` + `storageRestoreFailureMessage`；红→绿：`fix_relapse_gate --gate FIX-T02` 先红 → 改后绿 → `--prove-red` 注回即转红；定向单测 8 例 0 失败 |
| 28 (T03) | H-052 免责声明文案 | **完成** | 批次 44 已修 `ae1234dc`；本轮 `--only H-052` **exit 0** |
| 29 (T04) | H-018/H-061 KD-84 子集 PDF `/AIGC` | **BLOCKED** | 需 `core/export` 已批准 hunk + 独立读回；runbook §13 |
| 30 (T05) | H-018/H-062 KD-85 模型改卷 | **BLOCKED** | 落点在 `feature/tutor` 热区，需授权 |
| 31 (T06) | C-065 恢复腿夹具 | **BLOCKED** | 夹具已改闭合三角形（工作树未提交）；**设备绿轮未跑** |
| 32 (T07) | DOCX 文档级 `/AIGC` | **BLOCKED** | 需先落 docProps 再做独立读回 |
| 33 (T08) | 真实 Provider（A-010/D-082…090） | **BLOCKED** | 无真实密钥；runbook §9+§13 |
| 34 (T09) | arm64 十项 | **BLOCKED** | 在线设备均 x86_64 |
| 35 (T10) | 生产签名（J-019…027） | **BLOCKED** | `RELEASE_*` 未设 |
| 36 (T11) | versionCode 生成机制 | **BLOCKED** | 现为手写常量 `versionCode = 2`；改版本源属发布裁定 |
| 37 (T12) | AAB ABI/`libquickjs.so`/16KB | **BLOCKED** | AAB 陈旧且无 16KB 设备 |
| 38 (T13) | 干净树/产物对齐 HEAD（J-001/002/006） | **BLOCKED** | 共享工作树 4091 脏路径；验收阶段不跑 Gradle |
| 39 (T14) | Play Console 资产/轨道 | **BLOCKED** | 无账号无素材 |
| 40 (T15) | 成对 vc 升级/降级/回滚 | **BLOCKED** | 无成对包与生产签名 |
| 41 (T16) | 青少年模式/监护同意（H-039…043） | **BLOCKED** | 热区未空出 |
| 42 (T17) | 讲题/配图“AI 生成”角标（H-008） | **BLOCKED** | 热区禁改 |
| 43 (T18) | 隐私政策 URL/备案/data safety（H-024…081） | **BLOCKED** | 外部未产出 |
| 44 (T19) | TalkBack/IME/旋转（I-007…079） | **BLOCKED** | 无对应硬件/驱动 |
| 45 (T20) | 供应链与台账协议（G-087/088 + D0-030/081） | **BLOCKED** | KD-72 未落地；D0 两行属 D0 域 |
| 46 | FIX 100 行填 verdict | **完成** | 本轮复算 `15 PASS / 0 FAIL / 85 BLOCKED / 0 UNVERIFIED`，无空 verdict |
| 47 | 修复涉及模块定向测试 | **完成** | `fix/logs/FIX-unit-targeted.log`：`:app:testLocalFirstDebugUnitTest` **BUILD SUCCESSFUL**、`exit=0`、8+4 例 0 失败（Gradle 由批内执行，本轮只读日志） |
| 48 | 复发门（每个修复一个门） | **完成** | 本轮实跑 `python -I tools/ci/fix_relapse_gate.py --all --prove-red` → `TOTAL: 20 gates, 20 pass, 0 red`、**exit 0** |

### 1.4 WS4 GATE 域（12 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 49–58 | 单测门 / 强门 / 设备 / acceptance / lint / trace / 哈希 / 6C-2 / 备份四方 / AUD-08 各 10 行 | **完成** | `gate/00-gate-index.md` §0 十组总表 + 各行 cmd/test/device 证据；组内红项照实判红（acceptance 3 FAIL、lint 1 FAIL、AUD-08 3 FAIL）。本轮独立复跑的项目见 §3.2/§3.3 |
| 59 | GATE 100 行填 verdict | **完成** | 本轮复算 `89 PASS / 7 FAIL / 3 BLOCKED / 1 UNVERIFIED`，无空 verdict |
| 60 | CI patch 与本地门一致性检查（不应用） | **完成** | 本轮 `git apply --check tools/ci/acceptance_ci.patch` → **exit 0**；`.github/workflows/acceptance.yml` 不存在（未应用） |

### 1.5 WS5 独立复核签字（12 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 61 | 每域 ≥5 行抽样复核（reviewer∉authors），写 verdict JSON | **部分** | 10 份 JSON 均存在，抽样量 A9/B12/C13/D10/E35/F18/G13/H12/I8/J34（均 ≥5），`reviewer` 不在 `authors`。**但 10/10 `independence_verified=false`、10/10 绑旧台账 `bb25ef23…`** |
| 62–70 | A–J 十域复核 JSON（每域 1 文件） | **完成** | `docs/acceptance/verdicts/{A..J}-review.json` 10 份，本轮逐份读 `verdict`/`reviewer`/`ledger_sha256` |
| 71 | `pass_with_review` 统计 >0 | **未达成** | report 实跑 `pass_with_review=0`、`pass_without_review=865`；台账证据列 `review:` token **0**（7 行 `review:` 只在“方法”列作散文） |
| 72 | 复核发现的处理记录 | **部分** | 发现已记录并进入终裁（`adjudication.md` §3/§6；F 域 2 条 `REJECTED`、1 条数值无源），但**未回写台账**（记录员无台账写权限，且缺新版双类证据） |

### 1.6 WS6 报告重生成（8 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 73 | 重生成 `verdicts.md` / `acceptance-report.json` / 域索引 | **完成** | `verdicts.md` 29,810 B（14:31）、`domain-index.md` 2,539 B（14:31）、`acceptance-report.json` 指针绑 `887e3ba6…`；run-id `2026-11-05T06:31:41Z-batch45-acceptance-report` |
| 74 | `acceptance_report` exit 0；若不能，输出 BLOCKED 清单与原因 | **完成（分支 B）** | exit **1** 不可消除；`adjudication.md` §4 给 161 行 BLOCKED 分组清单（真实 Provider 10 / arm64 10 / 供应链 2 / 合规 12 / 无障碍 5 / 发版 34 / FIX 85 / 金标 3），逐组有 runbook 出口 |
| 75 | `trace_requirements` gaps 归零或全部有 BLOCKED 理由 | **完成** | 本轮实跑 **exit 0、`gaps=[]`、`total=1200`** |
| 76 | 证据哈希清单 verify 通过 | **完成** | 三份清单本轮实跑全 **exit 0**：`EVIDENCE.sha256`（281 文件，root `docs/research/evidence/2026-11-06-batch-45`）、`REVIEWS.sha256`（12，root `docs/acceptance/verdicts`）、`GATES.sha256`（23，root `docs/acceptance/evidence/2026-11-06-batch-45`） |
| 77 | `acceptance_lint` 0 违规 | **完成（非严格）** | 本轮 `violations=0`、`warnings=140`（全 `EVIDENCE_REUSED`）；`--strict-warnings` **exit 1**（140 条升级）——严格模式仍红，见 GATE-042 |
| 78 | 终裁文件落 `docs/acceptance/adjudication.md` | **完成** | 29,212 B，标题即 `批次 45 终裁：验收不通过（NOT ACCEPTED）`，§10 有签字区 |
| 79 | 台账最终 sha 固化 | **完成** | `887e3ba6…89aa` 写入 `adjudication.md:4`、`domain-index.md:3`、`acceptance-report.json`。**残余**：HEAD 仍是 `02ba5307…`，见 §5 F8 |
| 80 | 记录最终 1200 行统计 | **完成** | `domain-index.md` 表与本轮独立复算逐格相等（§0.3 / §3.4） |

### 1.7 WS7 剩余计划（4 项）

| # | 计划要求 | 状态 | 依据 |
|---:|---|---|---|
| 81 | KD-76 A6 按预注册 10+10 实跑或明确 UNVERIFIED | **完成（判 UNVERIFIED）** | 三处工具各 **exit 2** 拒绝 10+10（`run_kd76_rounds.sh:115-117`、`a6_plan.py:80,105`、`summarize_kd76.py:503-507`）；最新真实 A6 为 2×20 且 `NOT EVALUABLE`。KD-76 维持 open |
| 82 | isolation 低负载门换机/伸缩裁定或 BLOCKED | **完成（判 BLOCKED）** | 5584→5562 已换机；floor exit 0；30 样本窗 **INVALID**（`la_ratio_max=0.3825 > 0.30`）；套件 15×`-4`、`EXECUTED=0`。维持不定案 |
| 83 | p09 定性（再跑 3 次）或维持未收敛 | **完成（维持未收敛）** | 3/3 绿但 2071/1890/1861 ms 全 > 1300 ms；e-device 新跑三次 = 1 红/2 绿，红在 connect 阶段 |
| 84 | 发版 7 条 P0 runbook 汇总 | **完成（7/7 未闭环）** | `runbook.md` §9.2 + `ws7/00-index.md` §4；P0 逐条复跑值见 §3.3 |

## 2. 各轨事实

> 本节只写“证据能支撑什么、不能支撑什么”。

### 2.1 E 域：占位已灭，但“逐行”是模块级

- 批次 44 的 101 条 `PLACEHOLDER_CMD`（80 个唯一 id）**已归零**：`acceptance_report` 现 `violations=[]`。
- 命令可逐字重放：`e-domain-data-jvm.cmd.log` 首行是完整 `./gradlew --offline -q :core:data:testDebugUnitTest` + 20 个 `--tests` 全限定类名，`EXIT=0`；`e-domain-domain-jvm.cmd.log` 同形。
- 但 80 条 E PASS 只由 **2 条模块级日志** 支撑（56 + 30 行复用），`E-007/E-016/…/E-086` 等 10 条 UNVERIFIED 是并发/DST 缺口。
- E 域证据清单 `sha256.txt` 47/47 OK，**证据本身未被篡改**。

### 2.2 D0：100 行已回填，但自验收套件不可在现树复现（本批最硬的发现）

- 回填结果：`93 PASS / 2 FAIL (D0-030, D0-081) / 5 UNVERIFIED`，无空 verdict。
- **归档时的“205 条 CHECK 全 OK”在现树不成立**。本轮实跑 `python -I tools/ci/d0_self_acceptance.py --group all` → `CHECKS=205 FAILURES=5 EXIT=1`，5 条 BAD：
  - `real_ledger` / `real_ledger_again`：`group_trace` 以 `expected=1` 断言真台账上 `trace_requirements` **必须 exit 1**（`d0_self_acceptance.py:393-394,435-436`）；
  - `real_gaps_visible`：断言 `bool(data.get("gaps"))`，即**必须有 gap**（`:397`）；
  - `no_real_verdict_json`：断言 `len(real) == 0`，即**必须没有真实终裁 JSON**（`:724`）；现为 10；
  - `protocol_conflict_rule_absent`：断言 `present is False`（`:769`）；现因 `runbook.md:257` 出现无关的“冲突”二字（H-052 文案语境）而 `present=True`，属**假阳性**。
- 判读：这 5 条不是“工具坏了”，而是**自验收断言把当时的红状态写成了期望值**（state-coupled），一旦被验对象变绿/变完备就翻红。它们**不构成本批验收通过**，反而是 D0 域应新增的 FAIL 候选；记录员不改台账，只登记。

### 2.3 FIX：20 单 3 修 17 阻塞，复发门 20/20 绿

- `fix_relapse_gate --all --prove-red` 本轮实跑 **exit 0**：`TOTAL: 20 gates, 20 pass, 0 red`，每单都有“删出口 / 抬 PASS”两种自突变能翻红。
- 唯一真修 T02：`SecondaryScreens.kt` 6 处 `catch` + `storageRestoreFailureMessage` 非域内分支；红→绿有过程证据（门先红、改后绿、`--prove-red` 注回转红），定向单测 8 例。
- T01/T03 是批次 44 已修、本批复跑锁定；T06 夹具已改但**设备绿轮未跑**；T04/T05/T16/T17 卡热区/授权，其余卡外部。
- 台账 FIX 100 行 = `15 PASS / 85 BLOCKED`：17 个“不可修登记”行由 PASS 降 BLOCKED（这正是 `882→865` 的 17）。

### 2.4 GATE：十组 100 行，红的就是红的

| 组 | 关键实值（转录/本轮复跑） |
|---|---|
| 单测门 | `--rerun-tasks` **235/235 executed**、`GRADLE_EXIT=0`、3m16s（`build/b45-gate-unit-rerun-raw.log` 转录）；12 模块 535 XML / 4184 tests / 0 fail / 0 err，含 `:app` 156 例 |
| 强门 | 7 个 androidTest Kotlin 逐任务 `--rerun` exit 0、打包 `--rerun` exit 0；`GATE RESULT` 末行 `EXIT=0`。但 `b45-gate-strong-rerun2-raw.log` 是 **1 executed / 240 up-to-date** 的增量绿 |
| 设备 | emulator-5562 / API 34 / x86_64；`device-backup-selection` **11/11**、`device-scheduling` **3/3**，`skipped=0`，APK `c5f5efd1…` |
| acceptance | `violations=0`；`state=FAIL`、`exit=1`；`pass_with_review=0` |
| lint | 本地 0 违规 / 140 警告；CI 严格 `exit=1` |
| trace | `gaps=0 / total=1200 / exit=0` |
| 哈希 | 三份清单 verify exit 0（本轮复跑） |
| 6C-2 | 定向 3/3、`{9,13,15}`、工作树零 diff |
| 备份四方 | 本轮实跑 `check_backup_plan_sync.py` **PASS / exit 0** |
| AUD-08 | 本轮实跑 `check_split_gate.py` **FAIL / exit 1**：`4/7=0.5714 < 0.9`；fragmentRate `0/16=0.0000 ≤ 0.05` 单条过 |

### 2.5 独立复核：10 份 JSON 存在，但“零行级、绑旧账、自述未验证”

- `{A..J}-review.json` 10 份，`reviewer` 均不在 `authors`，`self_review=false`——形式要件在。
- **三处硬伤**（本轮逐份读）：① 10/10 `ledger_sha256=bb25ef23…`，与当前 `887e3ba6…` 全失配；② 10/10 `independence.independence_verified=false`，F 域自述“reviewer 是角色标签，不是具名个人或第二模型”；③ 台账 0 行写入 `review:` 证据 token，故 `pass_with_review=0`。
- 域级结论：A/B/D/E/F/H/I = UNVERIFIED，C/J = FAIL，G = BLOCKED，**无域 PASS**。
- 复核**抓到了真问题**（价值在此，不在“签字”）：F 域 2 条 `REJECTED`（F-064/F-067 台账写 dropbox 12，原始证据 6）、1 条数值无源（F-019 上界 2150 应为 2350）、1 条无源（F-028/029/036 的 516/553/538 与 p95=127 不在原始产物）；H 域 F1 = H-066 门硬编码行号（§5 F4）。

### 2.6 报告与索引：数字对齐，字节两代

- `domain-index.md` 的表与本轮独立复算**逐格相等**：`865/30/161/144`，域级 13 行全对。
- `verdicts.md` 14:31 生成、29,810 B，绑 `887e3ba6…`；`acceptance-report.json` 指针同 sha。
- 但 `gate/acceptance-report.json`（882/30/144/144）、`e-device/00-index.md` §0 第 5 行（882/30/144/144）、10 份 review JSON 仍是**修复环前**数字；引用必须带 sha（§0.3）。

### 2.7 WS7：四条全部“维持原判”，无一条转绿

| 项 | 判词 | 关键实值 |
|---|---|---|
| KD-76 A6 | **UNVERIFIED / open** | 三个工具拒绝 10+10（各 exit 2）；真实 A6 为 2×20 且 `NOT EVALUABLE` |
| isolation | **BLOCKED / 不定案** | 换机已做；`INVALID`（`la_ratio_max 0.3825 > 0.30`）；套件 15×`-4`、`EXECUTED=0` |
| p09 | **未收敛** | 3/3 绿但 2071/1890/1861 ms > 1300 ms；e-device 新跑 1 红/2 绿 |
| 发版 7 条 P0 | **7/7 未闭环** | 见 §3.3 |

## 3. 门禁与实跑退出码

### 3.1 双门（**转录**，本轮未跑 Gradle）

| 门 | 日志 | 结果 |
|---|---|---|
| 单测门 | `build/b45-gate-unit-rerun-raw.log`（2026-11-05 10:11–10:15） | `235 actionable tasks: 235 executed`、`BUILD SUCCESSFUL in 3m 16s`、**`GRADLE_EXIT=0`**（全量 `--rerun-tasks`，非增量） |
| 强门 | `gate/strong-gate.log` + `build/b45-gate-strong-rerun2-raw.log`（10:20–10:22） | 逐任务 `exit: 0`，末行 `EXIT=0`；但 **1 executed / 240 up-to-date** → 增量绿 |
| 定向单测（修复后） | `build/batch45-postfix-app-targeted.log`（14:41） | `183 actionable tasks: 183 executed`、`BUILD SUCCESSFUL in 1m 2s` |
| 定向单测（FIX 归档） | `fix/logs/FIX-unit-targeted.log` | `BUILD SUCCESSFUL in 2s`、`1 executed`、`exit=0` |

**判读边界**：单测门是本批唯一“全量重跑”的 Gradle 证据（235/235 executed），强门是增量绿；两者都**不是本轮运行**，记录员只做转录核对。

### 3.2 验收三件套与哈希（**本轮实跑**）

| # | 命令（逐字） | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_init.py --check` | **0** | 静默通过（1200 行无漂移） |
| 2 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **1** | `ledger_sha256=887e3ba6…`；`PASS 865 / FAIL 30 / BLOCKED 161 / UNVERIFIED 144`；`state=FAIL`；`violations=[]`；`pass_with_two_kinds=865`；`pass_with_review=0`；`dirty_paths=4091` |
| 3 | 同命令 `--require-complete` | **1** | `violations=[NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=144 FAIL=30 BLOCKED=161), PASS_WITHOUT_REVIEW (865 个 PASS 行没有 review 证据)]` |
| 4 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **0** | `violations=0`、`warnings=140`（全 `EVIDENCE_REUSED`；最大 72 行共用 `b-migration-matrix.xml`） |
| 5 | 同命令 `--strict-warnings` | **1** | 140 条复用告警升为违规 |
| 6 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **0** | `gaps=[]`、`total=1200` |
| 7 | `acceptance_hash_manifest.py --root docs/research/evidence/2026-11-06-batch-45 --verify …/EVIDENCE.sha256` | **0** | 281 文件全 OK |
| 8 | `… --root docs/acceptance/verdicts --verify …/REVIEWS.sha256` | **0** | 12 文件全 OK |
| 9 | `… --root docs/acceptance/evidence/2026-11-06-batch-45 --verify …/GATES.sha256` | **0** | 23 文件全 OK |
| 10 | `python -I tools/ci/d0_self_acceptance.py --group all` | **1** | `CHECKS=205 FAILURES=5`（§2.2） |
| 11 | `d0_self_acceptance.py --group docs --section verdicts` | **1** | `no_real_verdict_json EXPECTED=1 ACTUAL=0 BAD json=10` |
| 12 | `d0_self_acceptance.py --group docs --section protocol` | **1** | `conflict_rule_present=True` → `protocol_conflict_rule_absent BAD` |
| 13 | `sha256sum -c docs/research/evidence/2026-11-05-batch-44/e-domain/sha256.txt` | **0** | 47/47 OK |
| 14 | `PYTHONUTF8=1 … pytest test_acceptance_report.py test_acceptance_lint.py test_trace_requirements.py -q` | **0** | `44 passed in 20.29s` |
| 15 | 同上**不设** `PYTHONUTF8` | **0** | `44 passed in 16.46s`（终裁 §9.3 “13 失败”在本会话**未复现**，见 §5 F6） |

### 3.3 领域门（**本轮实跑**）

| 门 | 命令 | exit | 结果 |
|---|---|---:|---|
| H 合规逐条 | `python -I tools/ci/h_compliance_gate.py --only H-018` | **1** | DOCX 无文档级 `/AIGC`；KD-84/KD-85 开放 |
| | `--only H-052` | **0** | `PASS 文案与 PDF 隐式标识现状一致` |
| | `--only H-061` | **1** | `writeSubset` 逐页重绘丢 `/AIGC` |
| | `--only H-062` | **1** | 模型改卷文本未纳入判据 |
| | `--only H-066` | **0** | **假通过**：门读硬编码第 826 行（§5 F4） |
| 备份四方 | `python -I tools/ci/check_backup_plan_sync.py` | **0** | `PASS 四方一致（code / §9.11 / golden / 73.json）` |
| AUD-08 | `python -I tools/split_golden/check_split_gate.py` | **1** | `4/7=0.5714 < 0.9`；`门判定：FAIL` |
| 复发门 | `python -I tools/ci/fix_relapse_gate.py --all --prove-red` | **0** | `20 gates, 20 pass, 0 red` |
| CI 提案 | `git apply --check tools/ci/acceptance_ci.patch` | **0** | 可落地，未应用 |

### 3.4 本轮只读检查的其它逐字输出

```text
git rev-parse HEAD                      -> 87604198feeef198d5dba7343b8c4692c5e8f937
git status --porcelain | wc -l          -> 611
git status --porcelain -uall | wc -l    -> 4091
git diff --cached --name-only | wc -l   -> 0
sha256sum docs/acceptance/ledger.md     -> 887e3ba6…89aa（512,416 B）
git show HEAD:docs/acceptance/ledger.md | sha256sum -> 02ba5307…e65
git ls-files docs/acceptance/evidence/2026-11-06-batch-45 | wc -l -> 0
git ls-files docs/research/evidence/2026-11-06-batch-45 | wc -l  -> 0
git ls-files docs/acceptance/verdicts | wc -l                    -> 2
```

- 台账复算（逐行 6 列解析、按 ID 前缀归域）：`rows=1200`、`dup ids 0`、域分布与 §0.3 一致。
- 复核 JSON 绑定：10/10 `ledger_sha256=bb25ef239…`（当前 `887e3ba6…`）；10/10 `independence_verified=false`。
- `h_compliance_gate.py:653-656`：`def h066(): line = 826; text = read(SECONDARY).splitlines()[line-1]`；`SecondaryScreens.kt:826` 是 `}`，真实固定文案在 `:830-831`。
- `d0_self_acceptance.py:393-397,435-436,724,765-769`：state-coupled 断言原文（§2.2）。

### 3.5 门禁判读边界

- `report` exit 1 **不是工具故障**：`violations=[]` 说明 865 条 PASS 的证据形态全部合规；exit 1 唯一来源是 30 条 FAIL 行与 335 条非 PASS。
- `lint` exit 0 只说明“没抓到作弊形态”；140 条复用告警说明 PASS 的证据独立性有折损，严格模式 exit 1 是 GATE-042 的实值。
- `trace` gaps=0 只说明“每行可定位”，`UNVERIFIED`/`BLOCKED` 依然是“没有达到判定口”，不等于已判定通过。
- 三份哈希清单 verify 通过只证明**当前工作树**字节自洽；取证根未入库（§5 F8），干净克隆无法复算。

## 4. 设备证据（仅证据文件可证；本轮未跑 adb）

### 4.1 产物身份

| 轨 | APK sha256 | 设备 / boot_id | 时点 |
|---|---|---|---|
| GATE 设备组（备份/复习安排） | `c5f5efd177825c67779ba230336095453cdc359db7a9735c7cbdc6c3f62b5ca9` | `emulator-5562`，`boot_id=2d913fc3-97a5-4e63-a987-0628192a4f27` | 2026-11-05 10:22:51 +08 |
| test APK | `8ad0ff903a39eaaf79e5d23ad0645359c41ff6012db82d8a3b8dd396033355be` | 同上 | 同上 |
| e-device / WS7 | 同一 `c5f5efd1…`（`kd76_identity.py` 拉包复算，与本地产物逐字节一致） | 5562，`boot_count=53` | 13:05–13:25 +08 |

- `gate/device-install.txt`：`INSTALL_APP_EXIT=0`；设备 `34` / `x86_64` / boot 与摘要一致。
- `gate/apk-sha.txt`：两个 sha 与 `app/build/outputs/apk/{localFirst,androidTest/localFirst}/debug/*.apk` 逐字节相等，`EXIT=0`。
- **口径**：本批设备证据全部来自 `emulator-5562`（API 34、x86_64）；**无 arm64、无 16KB、无真机**。批次 44 的 `84cae864…` / `b8503f04…` / `f534b0dd…` 与本批 `c5f5efd1…` 是不同构建，**不可合并计数**。

### 4.2 设备结果（转录 `*.summary.txt` / `*.xml`）

| label | tests | evaluated | fail | skip | verdict |
|---|---:|---:|---:|---:|---|
| device-backup-selection | 11 | 11 | 0 | 0 | PASS |
| device-scheduling | 3 | 3 | 0 | 0 | PASS |
| e061-errorcause-ledger | 3 | 3 | 0 | 0 | PASS（顺序 `runBlocking`，**不覆盖并发**） |
| e061-control-no-such-class | — | 0 | 0 | — | 负控（recorder 不空转） |

- `gate/00-gate-index.md` §2 记一次真实事故：首次 `am instrument` 触发冷启动 ANR（`ActivityManager: ANR in …localfirst` → `Killing 26713`），解析 0 例；处置是 `am start -W` 预热后**同 APK 重跑**，得 11/11。**该失败未被改名成通过**，但原始输出被同 label 重跑覆盖，故**无独立归档**——本记录只作口径转述，不据此判 PASS。
- `ws7`：isolation 套件 15 例全部 `-4`（`EXECUTED=0`）；p09 三次 2071/1890/1861 ms；e-device 三次 = 1 红 / 2 绿（红在 connect 阶段）。

### 4.3 设备结论边界

- “设备全绿”不成立：本批只覆盖 2 个窄套件（14 例）+ 1 个 E 域 3 例；**没有** app-suite、没有 TalkBack、没有 arm64/16KB、没有 2h soak。
- F-055/F-057 的 2h soak 崩溃证据仍是批次 44 的 `soak.cmd.log`（`CRASHED=1`、首崩 2504s、2723s 人工停）；本批**未重跑长时**。
- C-065 的 `failures=1` 仍是批次 44 的 `c-restore-single-rerun.xml`；夹具已改但**未在设备上复跑**。
- 因此：设备轨不能支撑任何 F/C 域的转绿，只能支撑 GATE 设备组 10 行的“命令可跑、APK 可绑定”。

## 5. 复核发现与修复

### 5.1 本轮发现（S1–S12）

| # | 发现 | 证据（本轮实读/实跑） | 严重度 | 处置（本记录） |
|---|---|---|---|---|
| F1 | **报告不可签字**：30 FAIL + 335 非 PASS，`state=FAIL` | report 实跑 exit 1 | 高（结论） | 维持“验收不通过”；输出 BLOCKED 清单 |
| F2 | **零行级复核**：`pass_with_review=0`；10 份 JSON 绑旧账 `bb25ef23…`、`independence_verified=false` | report 实跑；逐份读 JSON | 高（DoD 1.5） | 不签 865 PASS；须在新台账上重做行级 review |
| F3 | **D0 自验收不可复现**：`--group all` 205/5 FAIL；断言把旧红状态写成期望（trace 必须 exit 1、gaps 必须非空、verdict JSON 必须为 0、冲突规则必须缺失） | `build/b45-d0all.txt:81,83,97,158,181`；`d0_self_acceptance.py:393-397,724,769` | 高（工具） | 登记为新 D0 FAIL 候选；不改台账 |
| F4 | **H-052/H-066 判词过期；H-066 门是假通过** | 台账两支 FAIL；本轮 `--only` 得 0/0；`h_compliance_gate.py:653-656` 硬编码 826，`SecondaryScreens.kt:826` 是 `}`、`:830` 才是文案 | 高（判词） | H-052 待补新版双类证据；H-066 先修门再重录，**不得改判** |
| F5 | **报告/复核/域索引三代字节并存**：`gate` 882、`e-device` 882、review JSON 绑 `bb25ef23`，终态 865/`887e3ba6` | `gate/acceptance-counts.txt`；`e-device/00-index.md` §0；`F-review.json.ledger_binding_note` | 高（口径） | 一切引用带 sha；17 行 FIX 降级是唯一合法差值来源 |
| F6 | 终裁称“不设 UTF-8 时 13 例失败”，本轮**两种环境都 44 passed** | §3.2 #14/#15 | 中（可复现性） | 标“本会话未复现”，不据此判工具脆弱 |
| F7 | 计划口径/文档内不一致：计划写 FAIL 21，开工实为 23；`fix/00-index.md` §3 写 15 PASS、§5 写“FIX 32 条 PASS” | `fix/fix-tickets.md:3`；`fix/00-index.md` §3/§5 | 低（文档） | 以台账 15 PASS 为准；32 是降级前旧值 |
| F8 | **不可从 HEAD 重放**：两个证据根 `git ls-files=0`，`HEAD:ledger.md` 仍是 `02ba5307…` | §3.4 | 高（归档） | 提交台账 + 两个证据根 + 10 份 JSON 后才可重放 |
| F9 | **PASS 行存在与证据矛盾的陈述**（未被改判）：F-064/F-067 dropbox 12 vs 6；F-019 上界 2150 vs 2350；F-028/029/036 数值无源 | `F-review.json` findings/sample_rows | 中（质量） | 保留为未处置矛盾，交 FIX 域 |
| F10 | 严格 lint 红：140 条 `EVIDENCE_REUSED` 升违规 | 本轮 `--strict-warnings` exit 1 | 中（门禁） | GATE-042 实值；不放宽阈值换绿 |
| F11 | 强门是增量绿（1/241 executed），只有单测门全量（235/235） | §3.1 | 中（门禁） | 发版前需干净全量 |
| F12 | C-065 夹具已改，设备未复跑；F-055/F-057 soak 未复跑 | `fix/fix-tickets.md` T06/T01；批次 44 soak 日志 | 高（P0） | 维持 FAIL，不因“已修”转绿 |

### 5.2 本批真实落地的修复（3 项）

| 修复 | 落点 | 消灭的具体失败 | 证据 |
|---|---|---|---|
| 失败文案不拼接原始异常 message | `app/src/main/kotlin/.../SecondaryScreens.kt` 6 处 `catch` + `storageRestoreFailureMessage` | 学生可见文案泄露 `content://` URI / 文件路径 / 实现细节 | `fix/logs/FIX-T02.log` 红→绿 + `--prove-red` 转红；`TEST-…StorageRestoreFeedbackTest.xml` 8 例 0 失败 |
| 滑杆端点夹回（批次 44 修，本批锁定） | `SchedulingSettingsScreen.kt` `retentionFromSlider` | Float→Double 端点越出 `0.7..0.97` → `SchedulingOptions` `require` FATAL（soak 2504s） | 单测 4 例 0 失败；`fix_relapse_gate --gate FIX-T01` 绿 |
| H-052 文案对齐（批次 44 修，本批复跑） | `docs/ai-content-disclosure-copy.md` | 文案称 PDF 无隐式标识，与已注入 `/AIGC` 冲突 | `--only H-052` exit 0 |

- 三项都**没有**在设备上重跑：T01 缺 instrumented 复跑、T02 缺端到端、H-052 缺 PDF 读回。因此它们是“JVM/门层已修”，不是“用户面已验证”。

### 5.3 与批次 44 的 S1–S15 对账

| 批次 44 遗留 | 批次 45 状态 |
|---|---|
| S1 报告与台账不同步 | **已解**（同 sha `887e3ba6`）；但产生新的一代错位（F5） |
| S2 E 域占位 | **已解**（violations=0、0 占位、sha 47/47） |
| S3 FAIL 不稳定（H-052/H-066 已绿） | **未解**：台账仍 FAIL；且发现 H-066 门假通过（F4） |
| S4 滑杆崩溃修复未测 | **部分**：JVM 单测绿；设备/长时未跑 |
| S5 C-065 夹具零面积 | **部分**：夹具已改；设备绿轮未跑 |
| S6 300 行未判定 | **已解**：D0/FIX/GATE 300 行全有 verdict |
| S7 独立复核=0 | **部分**：10 份域级 JSON 存在；行级仍 0（F2） |
| S8 证据哈希清单过期 | **已解**：三份清单 verify exit 0 |
| S9 域索引计数过期 | **已解**：`domain-index.md` 与复算逐格相等 |
| S10 双门增量、不含 `:app` | **部分**：单测门已含 `:app` 且全量 235/235；强门仍增量 |
| S11 4 份 APK 不可合并 | **维持**：本批换 `c5f5efd1…`，所有结论带 sha |
| S12 计划 §0 前提错误 | 本批计划已重写（84 项版），无 §0 研究前提 |
| S13 6C-2 未加新不变量 | **维持**：3/3 沿用；行为零 diff |
| S14 发版不成立 | **维持**：7/7 P0 未闭环 |
| S15 证据未跟踪 | **未解**：两个根仍 `git ls-files=0`（F8） |

### 5.4 复发门（“若再出现如何被门抓住”）

| 失败形态 | 抓住它的门 | 强度 |
|---|---|---|
| 占位命令冒充可复现 | `acceptance_report.py` `PLACEHOLDER_CMD` | 强（本轮 E 域 0 命中即证明规则在跑） |
| summary / 跳过 / UNVERIFIED 标 PASS | `acceptance_lint.py` + 批次 41/42 假绿回放 | 强 |
| PASS 只有一类证据 | `PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE`（865/865 两类） | 强 |
| 证据路径缺失/设备未绑定 | `trace_requirements.py`（本轮 gaps=0，且新增 `BLOCKED_WITHOUT_RUNBOOK`） | 强 |
| 终裁自证 / 绑旧账 | `_check_review`：reviewer∉authors、`REVIEW_LEDGER_SHA_MISMATCH` | 强（正因如此 10 份旧 JSON 才没被算成 review） |
| 证据字节被替换 | `acceptance_hash_manifest --verify`（三份 exit 0） | 中 |
| “修了就算绿” | `fix_relapse_gate --prove-red`（20/20，注回即红） | 强（本轮实跑） |
| 门硬编码行号假通过 | **当前没有门抓**（H-066 即反例） | **无**（F4） |
| 自验收断言写死旧红状态 | **当前没有门抓**（§2.2 5 条 BAD 是它自己翻红，但被读成“工具坏了”） | **无**（F3） |

## 6. 遗留、阻塞与 UNVERIFIED

### 6.1 阻塞（161 行 BLOCKED，逐组；签字含义 = 条件缺失，不是通过）

| 组 | 行数 | 缺失条件 | 出口 |
|---|---:|---|---|
| 真实 Provider | 10 | 无 API key / 授权账户 | `runbook.md` §9；环境变量注入，密钥不落源码 |
| arm64 / 16KB（F） | 10 | 无 arm64 真机或镜像 | §9 / §9.1 |
| 供应链（G） | 2 | 无离线 advisory；锁文件 `NOT_LANDED` | §9 |
| 合规外部 / 热区（H） | 12 | 6 外部 + 6 内部热区 | §9 / §13 |
| 无障碍真机（I） | 5 | 无 TalkBack 驱动 / IME / 传感器真机 | §6 / §9 |
| 发布工程（J） | 34 | 干净树 / 生产签名 / arm64 / 成对产物 / Play | §9.2 |
| 修复单（FIX） | 85 | 17 单落点/外部产物/独立读回未满足（含 17 个不可修登记） | §13 FIX-T04–T20 |
| 人工金标（GATE） | 3 | 无标注员/裁定者 | §9 |
| **合计** | **161** | 151 外部 + **10 内部热区/授权** | 全部有 runbook 指针（trace gaps=0） |

### 6.2 UNVERIFIED（144 行，不得写通过）

D0 5 / A 24 / B 2 / C 12 / E 10 / F 23 / G 6 / H 7 / I 32 / J 22 / GATE 1。主要类别：设备用例缺口（A/I）、并发与 DST（E）、真机目标与历史基线（F）、门禁阶段构建与成对产物（J）、人类语感/审美（H/I/GATE）。

### 6.3 明确不做的裁定

- 不签“批次 45 验收通过”，不签任何域 PASS，不把 865 PASS 当产品放行。
- 不把 H-052/H-066 的 live exit 0 写回台账（缺新版双类证据 + 行级复核；H-066 门失效）。
- 不把 C-065 判成“产品恢复链坏了”，不把 F-055/F-057 判成“已修”。
- 不把 KD-76 / isolation / p09 / AUD-08 的任何本批动作读成“已解决”。
- 不改 `acceptance_report` 规则使其 exit 0（那正是本批要防的洗绿）。

### 6.4 下一批入口（可执行、可验收）

1. **修门先于修判词**：H-066 改内容锚点（`grep -n "diagnosticsMessage ="` 或断言固定措辞本身），重录 `--only H-066` 的 cmd+test。
2. **D0 自验收去 state-coupled**：把 `real_ledger(expect 1)`、`real_gaps_visible(必须非空)`、`no_real_verdict_json`、`protocol_conflict_rule_absent` 改为**性质断言**（例如“trace 对真台账的 exit ∈{0,1} 且与 gaps 一致”“若有 verdict JSON 则必须被某 PASS 行引用”“协议若含冲突规则则文本锚点命中”），并重跑 205 条。
3. **行级复核**：对拟签 PASS 行写 `review:` 证据（reviewer∉authors、绑冻结后 sha），使 `pass_with_review>0`。
4. **冻结—重生成**：台账冻结 → 重跑双门（干净全量）→ 重生成 `verdicts.md`/`acceptance-report.json`/`domain-index.md` → 重算三份 sha256 → 新 run-id。
5. **证据入库**：提交台账 `887e3ba6…`（或更新）、两个证据根、10 份 review JSON；处理 `.gitignore:31` 的 `*.log` 白名单。
6. **P0 六项**：生产签名同身份 AAB、ABI/`libquickjs.so`、versionCode 生成、AIGC 交付格式（DOCX + 子集 + 模型改卷）、soak 崩溃后 2h 复跑、干净树全量构建。

## 7. 提交清单建议（每块 ≤6 路径；排除热区/锁文件/build）

> **本记录员未做任何 git 写**；以下只是建议块。**不得提交**：`build/*`（含全部 Gradle 日志）、`gradle.lockfile`、`.mimosa/`、热区文件（`core/domain/*`、`core/ui/*`、`docs/current/*`、`feature/tutor/*`）。

| 块 | 路径（≤6） | 备注 |
|---|---|---|
| C1 | `docs/acceptance/ledger.md`、`verdicts.md`、`domain-index.md`、`acceptance-report.json` | 1200 行终态（512KB，需确认入库策略） |
| C2 | `docs/acceptance/adjudication.md`、`runbook.md`、`dod.md`、`ledger.schema.json` | 终裁 + 协议（含 BLOCKED 出口与冲突规则待补） |
| C3 | `docs/acceptance/verdicts/{A,B,C,D,E}-review.json` | 复核 JSON 1 |
| C4 | `docs/acceptance/verdicts/{F,G,H,I,J}-review.json` | 复核 JSON 2（含 F 域 REJECTED 发现） |
| C5 | `docs/acceptance/evidence/2026-11-06-batch-45/{EVIDENCE,REVIEWS,GATES}.sha256`、`report.json`、`lint.json`、`trace.json` | 门禁证据 + 哈希 |
| C6 | `docs/research/evidence/2026-11-06-batch-45/00-index.md`、`d0-self/`（48 文件，需拆块）、`gate/00-gate-index.md` | D0 + GATE 索引 |
| C7 | `docs/research/evidence/2026-11-06-batch-45/fix/00-index.md`、`fix/fix-tickets.md`、`fix/logs/`（23）、`fix/xml/` | FIX 域证据 |
| C8 | `docs/research/evidence/2026-11-06-batch-45/{e-device,e-review,ws7,hash}/00-index.md` + `ws7/MANIFEST.sha256` | 设备复核 + WS7 + 哈希 |
| C9 | `docs/research/2026-11-06-batch-45-record.md` | 本记录 |
| — | **热区，另裁**：`app/.../SecondaryScreens.kt`（T02 真修，需授权）、`app/.../SchedulingSettingsScreen.kt`、`core/data/src/androidTest/.../TutorVisualRebuildRestoreInstrumentedTest.kt`（T06 夹具，未提交） | |
| — | **明确不入**：`build/b45-*`、`build/batch45-postfix-*`（gitignored）；`docs/acceptance/evidence/H/acceptance_report.json`（旧账 `d787ab4f…`，不得混引） | |

### 7.1 发版就绪判定：**不成立**

七条 P0（任一成立即不就绪），逐条均有本批/批次 44 证据：

1. **用户可达崩溃**：2h soak 2504s FATAL（滑杆端点 → `SchedulingOptions`）；JVM 已修，设备/长时未复跑。
2. **工作树不干净 / 产物落后**：`-uall=4091`；release APK revision 落后 HEAD。
3. **生产签名缺失**：`RELEASE_*` 4/4 缺失、`tracked_keystores=[]`。
4. **签名身份不一致 / 临时证书**：APK `CN=WS8 Ephemeral Local Only`；AAB `CN=ZCode Release Smoke`（已过期）。
5. **AAB 内容不合格**：ABI 4≠2、缺 `libquickjs.so`。
6. **versionCode 恒 2、无生成机制**。
7. **AIGC 隐式标识未覆盖交付格式**：DOCX 无文档级 `/AIGC`、部分页打印丢标、模型改卷文本漏标（H-018/H-061/H-062 本轮 exit 1）。
8. **质量门未绿**：report exit 1、strict lint exit 1、AUD-08 FAIL、0 行级复核。

## 8. 回链

- 计划：`.zcode/plans/plan-2026-11-06-batch-45-acceptance-close.md`（§0 基线、§1 84 项、§2 硬约束、§3 验收）。
- 上一批：`docs/research/2026-11-06-batch-44-record.md`（§9.2 FAIL 不签字、§9.3 S1–S15、§9.5 入口）。
- 终裁：`docs/acceptance/adjudication.md`（§0 结论、§3 FAIL 30、§4 BLOCKED 161、§5 UNVERIFIED 144、§6 复核、§9 签字条件）。
- 台账/DoD/runbook：`docs/acceptance/ledger.md`（`887e3ba6…`）、`dod.md`（§1.5 独立复核）、`runbook.md`（§9/§9.2/§13）。
- 逐项投影/导航/报告：`docs/acceptance/verdicts.md`、`docs/acceptance/domain-index.md`、`docs/acceptance/acceptance-report.json`。
- 门禁证据：`docs/acceptance/evidence/2026-11-06-batch-45/`（`EVIDENCE/REVIEWS/GATES.sha256`、`report*.json`、`lint.json`、`trace.json`）。
- 域证据：`docs/research/evidence/2026-11-06-batch-45/`（`d0-self/`、`d-review/`、`e-device/`、`e-review/`、`fix/`、`gate/`、`hash/`、`ws7/`）。
- 复核 JSON：`docs/acceptance/verdicts/{A..J}-review.json`（10 份，均绑 `bb25ef23…`）。
- 6C-2：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:90,140,166`；`gate/sixc2-source-read.txt`、`gate/sixc2-targeted.xml`。
- 不可变项：`docs/known-defects.md:2916`（KD-76）、`:2745`（isolation）；`e-device/verify-p09-summary.txt`；`gate/aud08-split-gate.txt`。
- **本记录不替代主会话裁定**：当前可写结论只有——**1200 项已枚举并逐项有 verdict（865/30/161/144）；E 域占位已灭、trace 已归零、哈希可验；但 report exit 1、0 行级复核、证据不可从 HEAD 重放——批次 45 验收不通过，发版不成立。**

---

<!-- 体量自查：wc -lc = 484 行 / 45,708 B（≤65,536 B）。 -->

## 9. 主会话收口（2026-11-05）

### 9.1 主会话实跑验证

| 项 | 结果 |
|---|---|
| `python -I tools/ci/acceptance_report.py` | **exit=1**；total 1200；PASS **865** / FAIL **30** / BLOCKED **161** / UNVERIFIED **144**；`violations=0`（E 域占位已清零） |
| `pass_with_review` | **0 / 865**（域级复核 JSON 绑定旧台账 `bb25ef23…`，且自述 `independence_verified=false`） |
| 单测门 12 模块 | `GRADLE_EXIT=0` |
| 强门（逐任务 `--rerun`） | `GRADLE_EXIT=0` |
| 环后强门 | `GRADLE_EXIT=0` |

### 9.2 终裁：**验收不通过（NOT ACCEPTED）**

我确认 `docs/acceptance/adjudication.md` 的结论，并只对其中两项签字：

1. 结论「验收不通过」；
2. 161 行 BLOCKED 清单（表示“条件/授权缺失，不判 PASS”）。

**不签字**：865 条 PASS（无行级独立复核；F 域复核还发现 2 条 PASS 与证据矛盾）、30 条 FAIL、144 条 UNVERIFIED、发版就绪、“批次 45 验收通过”。

五条阻断与记录 §0 一致：报告 exit 1；865 PASS 零行级复核；30 FAIL 中 28 条有当前证据、2 条判词过期且 H-066 曾因硬编码行号出现假通过；161 BLOCKED 中 10 条属内部热区/授权；证据此前不可从 HEAD 重放（本批已把证据入库，但 HEAD 台账仍是旧字节）。

### 9.3 本批实得

- E 域 80 个占位 id 全部换成逐字命令并实跑：domain 15 类/161 例、data 20 类/150 例 0 fail、探针 298,277 断言 OK、`--mutate` 红相 VIOLATED；`PLACEHOLDER_CMD` 101→0。
- D0 100 行自验收落码；FIX 20 个修复单与 GATE 100 行落表；十域复核 JSON 落盘（虽然绑旧台账，作为下一轮重签的输入）。
- KD-76 A6 仍 NOT EVALUABLE；isolation 窗仍 INVALID；p09 三次复跑仍不一致（未收敛）；发版 7 条 P0 维持。

### 9.4 批次 46 的唯一出口

1. 台账/证据冻结提交后，按**当前 sha** 逐行生成行级独立复核（reviewer≠作者），使 `pass_with_review == PASS 数`；
2. 30 条 FAIL 逐条重判（修或保留 FAIL，不得洗绿）；
3. 161 条 BLOCKED 逐条确认 runbook（外部/内部）；
4. 重跑 `acceptance_report --require-complete`；若仍非 0，维持不通过并给出不可签字项清单。
