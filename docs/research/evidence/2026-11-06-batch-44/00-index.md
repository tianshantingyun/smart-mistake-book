<!-- 体量自查：落盘后 wc -lc 实测见 §11（R4 上限 400 行 / 65536 B）。目录 .md 预算：本文件 + device-track/00-index.md + research-crosscheck.md = 3 个，远低于 16。 -->
# 批次 44 证据总索引（验收替代批；实际落盘目录 `2026-11-06-batch-44`）

- 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（计数 1200 = D0 100 + A–J 900 + FIX 100 + GATE 100，`:80`）
- 首项输入：批次 43 收口 `docs/research/2026-11-05-batch-43-record.md` §9（KD-76 open、isolation 不定案、p09 未收敛、发版不成立）
- 本文件范围：批次 44 **E 文件的元字段整理、编号映射、产物身份、可证性边界与 sha256 清单**。E 文件分布在三个位置：本目录（验收机制/设备复基线/交叉复核）、`docs/research/evidence/2026-11-05-batch-44/`（A–J 域）、`docs/acceptance/`（台账/裁决/证据）。**外目录只登记、不搬动、不改写**。
- 字段口径（本 ask 指定）：`evidence-id / verdict / executed-at / head / device-serial / apk-sha256 / exit-code / raw-log / record-ref / sanitized`
- 整理纪律：本轮**只读核对 + 文本整理**——未运行 Gradle、未跑设备、未改源码/热区、未做 git 写。本轮亲手实跑的只读检查列在 §9；其余判定一律**转录**并注明出处。
- 目录不重叠声明：`2026-11-05-batch-44` 是**十域执行日**目录（实跑 2026-11-04），`2026-11-06-batch-44` 是**验收机制 + 设备复基线**目录（实跑 2026-11-04/05）。两者同属计划批次 44，**不是两次批次**。

## 0. E 文件编号映射（先读这一节）

| E-ID | 文件 | 位置 | verdict（本索引转录） |
|---|---|---|---|
| `E-44-ACC-01` | `acceptance-report.json` + `acceptance/*.json,log,xml` | 本目录 | **NOT ACCEPTANCE COMPLETE**：`acceptance_complete=false`、`state=FAIL`；计数 PASS 670 / FAIL 21 / BLOCKED 73 / UNVERIFIED 436 |
| `E-44-LEDGER-01` | `docs/acceptance/{ledger.md,dod.md,runbook.md,ledger.schema.json,verdicts.md,verdicts/README.md}` | `docs/acceptance/` | **形状成立、不可总体通过**：1200 行 / 13 域计数复算一致；530 non-pass；D0/FIX/GATE 300 行全 UNVERIFIED；review 证据 0 |
| `E-44-DEV-01` | `device-track/**`（121 文件） | 本目录 | **FAIL**：10 个套件 label 中 4 个 FAIL；6 条复现红；TalkBack BLOCKED；15 例 isolation skip 不计通过 |
| `E-44-XCHECK-01` | `research-crosscheck.md` | 本目录 | 计划 §0 七条：A 部分成立须改 / B 半对半错 / C 前提事实错误 / D 结论成立构件名错 / E 成立 / F 来源真实不可采用 / G 日期成立但条款 UNVERIFIED |
| `E-44-A-01` | `a-domain/00-index.md` + `adversarial-attempts.md` | `2026-11-05-batch-44/` | **66 PASS / 23 UNVERIFIED / 1 BLOCKED**（=90） |
| `E-44-B-01` | `b-domain/00-index.md` | 同上 | **88 PASS / 2 UNVERIFIED**（=90）；迁移矩阵 1..73 与备份 18 行在册 |
| `E-44-C-01` | `c-domain/00-index.md` | 同上 | **77 PASS / 1 FAIL / 12 UNVERIFIED**（=90）；FAIL = `C-065` 单例红 |
| `E-44-D-01` | `d-domain/`（34 文件，**无 00-index**） | 同上 | **81 PASS / 9 BLOCKED**（=90，按台账复算）；**E 索引文档缺失** |
| `E-44-E-01` | `e-domain/00-index.md` | 同上 | **80 PASS / 10 UNVERIFIED**（=90）；纯 JVM，无设备外推 |
| `E-44-F-01` | `f-domain/00-index.md` | 同上 | **56 PASS / 2 FAIL / 10 BLOCKED / 22 UNVERIFIED**（=90）；FAIL = F-055/F-057 2h soak 崩溃 |
| `E-44-I-01` | `i-domain/`（94 文件，**无 00-index**） | 同上 | **53 PASS / 5 BLOCKED / 32 UNVERIFIED**（=90，按台账复算）；**E 索引文档缺失**；设备探针与 device-track 是两次不同运行 |
| `E-44-J-01` | `j-domain/00-index.md` | 同上 | **21 PASS / 13 FAIL / 34 BLOCKED / 22 UNVERIFIED**（=90） |
| `E-44-GH-01` | `docs/acceptance/evidence/{G,H}/`（150 文件，**无 00-index**） | `docs/acceptance/evidence/` | G **82/0/2/6**；H **66/5/12/7**（=90 各，按台账复算）；G 有 `hashes.sha256`，H 无目录哈希 |
| `E-44-RESEARCH-01` | `2026-11-06-{batch-44-baseline,metamorphic-relations,release-acceptance,screenshot-baseline,a11y-acceptance}.md` | `docs/research/` | 五份研究件，**均无批次级 PASS**；baseline 判「基线成立 + 三项高危前提」；metamorphic 给 48 条关系；release 判 3 PASS / 1 FAIL / 1 BLOCKED（仅本机可核项） |
| `E-44-GATES-01` | `build/batch44-unit.log` + `build/batch44-strong.log` | `build/`（gitignored） | 双门 **exit 0**：`GRADLE_EXIT_PHASE2=0`；`GATE RESULT: PASS at 2026-11-05 04:36:14`；HEAD `542b9bba`、dirty 575；**日志不可提交** |

- E-ID 前缀统一为 `E-44-*`（本索引新起的登记号，不改任何原文件）；原文件内的 `evidence-id`（如 `B44-BASELINE-01`、`B44-MR-LIB-01`、`R5-RELACCEPT-01`、`B44-R3-SCREENSHOT-01`）在 §1 原样保留。
- **缺 E 索引文档的三处**：`d-domain`、`i-domain`、`evidence/{G,H}`。它们的原始文件在册、台账有引用，但没有本域导航件 → §6-G3/G4。

## 1. 九字段元数据

### 1.1 判定 / 时间 / 源

| E-ID | verdict（原文口径） | executed-at | head |
|---|---|---|---|
| `E-44-ACC-01` | `state=FAIL`、`ok=true`（审计成功≠验收通过）、`acceptance_complete=false` | `2026-11-04T22:17:48Z`（`generated_at_utc` / run-id 同值） | **未记录**（报告无 HEAD 字段；绑定为 `ledger@e139b03e…`） |
| `E-44-LEDGER-01` | 1200 行形状成立；**不可总体通过**（530 non-pass） | ledger mtime `2026-11-05 03:46:07 +0800`；shape recount 见 `ledger-shape.log` | **未记录** |
| `E-44-DEV-01` | **FAIL**（4 套件 FAIL、2 套件含 skip、6 红） | `2026-11-04 20:43–21:52Z` | `542b9bbac49081aca808c616a5dc538965d1767e`（`source-snapshot.txt:1`） |
| `E-44-XCHECK-01` | 七条结论逐条判定（§0 表） | 2026-11-04（本轮在线抓取同轮） | **未记录**（文内未绑 HEAD） |
| `E-44-A-01` | 66/23/1 | `2026-11-04 08:39–10:29Z` | `26f2327b0f88d3cad7e076b967b62874e36ca07d` |
| `E-44-B-01` | 88/2 | `2026-11-04 19:11–20:46Z` | `26f2327b…` |
| `E-44-C-01` | 77/1/12 | `2026-11-04 13:13–13:35Z` | `26f2327b…` |
| `E-44-D-01` | 81/9（台账复算） | `2026-11-04T14:23:58Z–14:24:05Z`（6 份 cmd log 时间戳） | **未记录** |
| `E-44-E-01` | 80/10 | `2026-11-04 22:40–23:05`（本机） | `7683640dfd772293a5adc2d7d6c5034454a003d0` |
| `E-44-F-01` | 56/2/10/22 | `2026-11-04 15:23–16:38`（本机） | `542b9bba…` |
| `E-44-I-01` | 53/5/32（台账复算） | `2026-11-04T19:01:18Z`（`base-library.device.json` finished_at；TalkBack logcat 设备时间 19:08） | **未记录**（仅 APK/serial/boot 绑定） |
| `E-44-J-01` | 21/13/34/22 | `2026-11-05 03:19–03:40`（本机） | `542b9bba…` |
| `E-44-GH-01` | G 82/0/2/6；H 66/5/12/7 | G `2026-11-05T01:07:35–01:11:57+08:00`；H pytest XML `2026-11-05T01:48:22+08:00` | **未记录** |
| `E-44-RESEARCH-01` | 五份研究件，无批次级 PASS | 2026-11-04（baseline/release/screenshot/metamorphic/a11y 同轮） | baseline `26f2327b`；release `26f2327b`；metamorphic `26f2327b`；screenshot 未绑 HEAD；a11y 未绑 HEAD |
| `E-44-GATES-01` | 双门 exit 0（非 clean single-pass 以外的重试未出现） | unit `2026-11-05 04:30`；strong `04:34:33–04:36:14 +0800` | `542b9bba…`（`batch44-strong.log:2`） |

### 1.2 设备 / APK / exit / raw-log / record-ref / sanitized

| E-ID | device-serial | apk-sha256 | exit-code | raw-log | record-ref | sanitized |
|---|---|---|---|---|---|---|
| `E-44-ACC-01` | N/A（host） | N/A | report `0`；`--require-complete` **`1`**；lint `0`；trace **`1`**；selftests `0`；replay `1/1/1` | `acceptance/gates-final.log`、`report-require-complete.log`、`trace.stdout.log`、`tool-selftests.log`、`replay-drill.log` | 计划 §1/§7；**批次 44 record 未落盘** | 通过（§8） |
| `E-44-LEDGER-01` | N/A | N/A | `acceptance_init --check` `0`；`acceptance_lint` `0` | `acceptance/init-check.log`、`ledger-shape.log`、`lint.json` | 计划 §1 D0；批次 43 record §9 | 通过（§8） |
| `E-44-DEV-01` | `emulator-5584`（API34/x86_64/PAGE 4096，boot `831e5d06-7331-40d8-8cf4-406976b8a9c3`） | app `f534b0dd8aeabeeeec633bcbb9d24a62efe900eb75fdbcce199b979d58f7f7de`；test `2a70fb01…`；data `61190380…`；database `32235e63…`；export `08ea2641…`；capture `4316d191…`；library `330c97fc…`；tutor `817a8b41…` | build `0`；instrument 由 recorder 逐例判（无单一 exit） | `device-track/**`（121 文件） | **未落盘**（建议写批次 44 record 时回填本行） | 通过（§8） |
| `E-44-XCHECK-01` | 探活 `5562/5566/5570/5584`（不作设备证据） | N/A | N/A（人工复核，无单一 exit） | 文内 §0/§7 表；在线抓取未留原始 HTML | 计划 §0 | 通过（§8） |
| `E-44-A-01` | `5584`（boot `5a34f877…`） | app `84cae864…`；test `9ede778d…`/`aab6af01…`；data.test `4ef6575b…` | 设备 recorder 逐套件；JVM `*.cmd.log` `EXIT=0` | `a-domain/**`（167 文件） | **未落盘** | 通过（§8，1 条 `home` 伪命中） |
| `E-44-B-01` | `5562`（boot `a702449b…`）/ `5566`（boot `d8f39d06…`） | database.test `32235e63…`；data.test `c9943728…`（自测包 target==自身） | 逐套件 recorder；备份门 `b-backup-plan-gate.log` | `b-domain/**`（98 文件） | **未落盘** | 通过（§8） |
| `E-44-C-01` | `5584`（boot `5a34f877…`） | app `84cae864…`；test `aab6af01…`；data.test `c9943728…` | 逐套件；C-065 `FAIL`（1/0/0） | `c-domain/**`（86 文件） | **未落盘** | 通过（§8） |
| `E-44-D-01` | N/A（JVM） | N/A | 6 份 cmd log `EXIT=0` | `d-domain/**`（34 文件） | **未落盘** | 通过（§8） |
| `E-44-E-01` | N/A（JVM） | N/A | `EXIT=0`（JVM 复跑命令见该 index §7） | `e-domain/**`（48 文件） | **未落盘** | 通过（§8） |
| `E-44-F-01` | `5566`（boot `5e1a5d59…`）；`5562` 为降级机（不作基线） | app `b8503f04…`；test `aab6af01…`；export.test `08ea2641…`；data.test `c9943728…`；database.test `32235e63…` | 逐套件；2 FAIL（soak 崩溃） | `f-domain/**`（191 文件） | **未落盘** | 通过（§8，dalvik 路径含 `@` 伪命中） |
| `E-44-I-01` | `5584`（boot `831e5d06…`） | app `b8503f04…`（i-domain 轨；与 device-track 的 `f534b0dd…` **不是同一轨**） | 逐探针 `verdict`（device.json） | `i-domain/**`（94 文件） | **未落盘** | 通过（§8，logcat `@androidx` 伪命中） |
| `E-44-J-01` | `5584`（boot `831e5d06…`）/ `5562`（boot `a702449b…`） | release APK `724ce718…`；AAB `d0734105…`；mapping `c5b2d170…`；debug `97ee82a1…` | 逐日志（`j-aab16k.log` `exit 1`，其余见表） | `j-domain/**`（83 文件） | **未落盘** | 通过（§8） |
| `E-44-GH-01` | G：`5584`；H：N/A | G：app `84cae864…`；H：N/A | G/H 日志 `exit=0`；pytest XML `failures=0` | `docs/acceptance/evidence/{G,H}/**`（150 文件） | **未落盘** | 通过，注：G `.mimosa` hook-state 含 2 条本机 Temp 路径（§8） |
| `E-44-RESEARCH-01` | N/A | N/A | N/A（研究件） | 各文件正文 | 计划 §0/§1 | 通过（§8） |
| `E-44-GATES-01` | N/A | N/A | unit `0`；strong `0` | `build/batch44-{unit,strong}.log` | 计划 §7 | 通过（§8）；**日志被忽略** |

## 2. 环境与源绑定

| 项 | 值 | 依据 |
|---|---|---|
| HEAD（本轮整理时） | `542b9bbac49081aca808c616a5dc538965d1767e` | 本轮 `git rev-parse HEAD` |
| 工作树 | `porcelain=575`；`-uall=4390`；`git diff` sha256 `79e42969…b68` | 本轮实测（§9-1）；device-track 记录时为 `575 / 4294` 且 diff sha **同值** → 已跟踪改动未变，只有未跟踪产物增长 96 条 |
| 台账 | `docs/acceptance/ledger.md` sha256 `e139b03e8b9a005af73d658333f7721907b7e6976718106934c00e096a3eb675`，460,450 B | 本轮 `sha256sum`；与报告 `ledger_sha256` 逐字符相同 |
| 报告 | `acceptance-report.json` sha256 `244cff7b3429e6e014c89fcf41d2e94e2fc71b618f86f1c71e474161704634a0`，233,221 B | 本轮 `sha256sum` |
| 验收门重放 | gates-final.log 时间戳 `2026-11-04T22:17:12Z`；台账 sha 同值 | `gates-final.log:1-2` |
| 双门 | unit `04:30` / strong `04:34–04:36 +0800`，HEAD `542b9bba`、dirty 575 | `build/batch44-strong.log:1-3`；`build/batch44-unit.log` 末行 |
| 证据跟踪态 | 三个证据根 `git ls-files` 均 **0**；`*.log` 被 `.gitignore:31` 忽略；`**/build/` 被 `.gitignore:15` 忽略 | 本轮实测 |
| DB schema / 6C-2 | schema 73；`ALLOWED_MASKS = {9,13,15}` | 转录批次 43 索引 §1（本批未改；本轮未复读源码，标转录） |

## 3. 产物身份（跨轨不可合并；这是本批最容易出假结论的地方）

本批设备证据至少绑 **4 份不同 app APK**。任何「批次 44 设备全绿 / 全红」都必须先指名是哪一份：

| 轨 | app APK sha256 | 设备 | 时点 |
|---|---|---|---|
| A / C / G | `84cae864c42f7e26203d21f3901c06a4d0f02f2dd3b012bb63727ef32e5dab5b` | 5584 | 11-04 08:39–13:35Z；G 11-05 01:07–01:11+08 |
| F / I（i-domain） | `b8503f0405db5b6a1e3f2dd89b9286dc85ee94dcaa261b85278b6a88db8df5ac` | 5566（F）/ 5584（I） | 11-04 15:23–16:38 / 19:01Z |
| **设备复基线（device-track）** | `f534b0dd8aeabeeeec633bcbb9d24a62efe900eb75fdbcce199b979d58f7f7de` | 5584 | 11-04 20:43–21:52Z |
| J（release） | `724ce71834c227db79266149267d52c1b55a23d3cbceb2b93ce3930cba342b44`（AAB `d0734105…`） | 5584 / 5562 | 11-05 03:19–03:40+08 |

- device-track 的设备端 `sha256sum` 与本地 `apk-shas.txt` **逐包相等**（`witness-device-apk-sha.log`），boot_id 全程 `831e5d06-…` 未变 → 该轨「设备上跑的就是这些字节」成立。
- **不可合并**：`84cae864` / `b8503f04` / `f534b0dd` 是三次不同构建；把 A 的 66 PASS 与 F 的 56 PASS 相加成「122 通过」无效。i-domain 与 device-track 都拍同一批页面，但 APK 不同、文件哈希全不同（本轮抽样 5 对全部 DIFF）→ 是两次独立运行。

## 4. 判定汇总与门禁（复算，不引用 summary 当证据）

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED |
|---|---:|---:|---:|---:|---:|
| D0 验收机制 | 100 | 0 | 0 | 0 | **100** |
| A 功能旅程 | 90 | 66 | 0 | 1 | 23 |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 |
| F 性能与稳定 | 90 | 56 | 2 | 10 | 22 |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 |
| FIX 修复加固 | 100 | 0 | 0 | 0 | **100** |
| GATE 门禁终裁 | 100 | 0 | 0 | 0 | **100** |
| **合计** | **1200** | **670** | **21** | **73** | **436** |

- 上表由本轮 `python -I` 直接读 `docs/acceptance/ledger.md` 六列复算（§9-3），与 `acceptance-report.json` 的 `counts`/`domain_counts` **逐域相同**；不是转录 summary。
- **门禁 exit**：`acceptance_init --check`=0、`acceptance_report`=0、`--require-complete`=**1**、`acceptance_lint`=0（violations 0 / warnings 29 全 `EVIDENCE_REUSED`）、`trace_requirements`=**1**（raw gaps 260 → 归一 111 行）、`make_acceptance_report`=0、工具自测 30 passed=0、假绿回放 1/1/1。
- **结构性缺口（不是通过）**：D0/FIX/GATE 共 **300 行全 UNVERIFIED**。即验收机制自己的 100 项、修复域 100 项、终裁域 100 项**从未回填**；PASS 670 只覆盖 A–J 的 670 行。
- **独立复核 = 0**：证据列含 `review:` token 的行 0；方法列要求复核文件的 64 行，`docs/acceptance/verdicts/*.json` 存在 0 个（`verdicts/` 只有 `README.md` 与 `_template.json`）。→ 任何 PASS 都不等于 DoD §1.5 复核通过，GATE 终裁未做。
- **证据归档 = 未达成**：`pass_rows_with_untracked_evidence=670/670`；`tracked_log_files=0`（本轮实测三根目录 `git ls-files` 均 0，且 `*.log` 被 `.gitignore:31` 忽略）。

## 5. 仅本批可证项（位绑定）

| 面 | 本批可写 | **只**由本批可证 | 不得外推 / UNVERIFIED |
|---|---|---|---|
| 验收机制可用性 | 假绿回放三例均 exit 1（`PASS_NEEDS_TWO_INDEPENDENT_EVIDENCE` / `SUMMARY_AS_EVIDENCE` / `SKIPPED_GATE_AS_PASS` / `UNVERIFIED_AS_PASS`）；工具自测 30/30；1200 行 13 域形状成立 | `acceptance/replay-drill.log`、`tool-selftests.xml`、`ledger-shape.log` | 机制「已验收」不成立：D0 自身 100 行全 UNVERIFIED |
| 台账可复算性 | 通过：本轮逐行复算 670/21/73/436，与报告逐域一致 | `docs/acceptance/ledger.md` sha `e139b03e…` | 台账 verdict **不等于**产品通过 |
| 设备复基线 | 4 套件 FAIL（app-suite 2 fail/17 skip；capture 1；library 1；tutor 2）+ data 1 fail/6 skip；R1/R3/R4/R5 为已知红，R2 已知红，R6 未定性，R7 测试隔离缺陷 | app `f534b0dd…` + serial 5584 + `device-track/**` | 「设备全绿」不成立；R7 不是产品 DB 结论 |
| 迁移 / 备份 | `database` 套件 319/319（含 1..73 每版迁移）；`data-backup-suite` 84/84 评估例通过；真实杀进程用 stageA/stageB 两进程覆盖 | `modules/database.*`、`modules/data-backup-suite.*`、`rerun/data-migration-drill-stage{B}.log` | 未在 1..72 每版实例化数据行（与 b-domain 同一残余） |
| 无障碍自动化 | 4 根页语义树 STRUCTURAL=PASS、200%/暗色/横屏结构 PASS、焦点 6 标签 PASS；该轨对比度 UNVERIFIED | `probes/base-*`、`fs2-*`、`dark-*`、`landscape-*`、`focus-*` | TalkBack 全路径 BLOCKED；朗读语感 UNVERIFIED |
| 视觉噪声 | 沉浸态两次截图 **MAE=0.0/changed=0**；非沉浸态 0.00216（状态栏时钟超阈值） | `probes/home-immersive-{a,b}.png`、`home-selfdiff.json` | 跨 APK 基线差 0.00366 只登记，不判回归 |
| 计划 §0 研究结论 | 七条逐条有判定与一手来源；A/B/C/D 需改写、F 工具不可采用、G 条款原文未取得 | `research-crosscheck.md:25-31` | 条款原文、官方稳定性页正文、16KB 运行时未取得 → UNVERIFIED |
| 双门 | 单测 exit 0；强门 `GATE RESULT: PASS`（04:36:14），HEAD `542b9bba` | `build/batch44-{unit,strong}.log`（**gitignored**） | 日志不可提交；复跑需重跑，不能引用为归档证据 |

## 6. 缺口、未验证与阻塞（不得填平）

1. **G1 300 行无判定**：D0/FIX/GATE 全 UNVERIFIED；FIX 域 100 项没有任何「先红后绿」的值班记录 → 计划 §3「修复与加固」在本批**未发生**（C-065、F-055/F-057、H 5 条等仍在 FIX 队列）。
2. **G2 独立复核/终裁缺口**：0 个 verdict JSON、0 条 review token；`GATE-001…100` 全 UNVERIFIED → 主会话只能逐行签字，不能签「批次 44 验收通过」。
3. **G3 `d-domain` 无 00-index**：34 份 JVM XML/日志在册、台账 D 域 164 处引用，但没有本域导航件；D 域 9 条 BLOCKED 的原因只能从台账行读。
4. **G4 `i-domain` 与 `evidence/{G,H}` 无 00-index**：i-domain 94 文件（app `b8503f04`）；G/H 150 文件（G 有 `hashes.sha256`，H 无目录哈希）。
5. **G5 日志不可提交**：`*.log` 被 `.gitignore:31` 忽略、`build/` 被 `.gitignore:15` 忽略 → gates-final.log、设备套件 log、双门 log 都无法随仓库提交；`acceptance_hash_manifest.py` 的清单能证明字节，但不能改变「未跟踪」事实。
6. **G6 批次 record 未落盘**：`docs/research/2026-11-06-batch-44-record.md` 不存在 → 所有 `record-ref` 目前只能指向计划与批次 43 record §9。
7. **G7 跨轨不可合并**：4 份 app APK（§3）；A/B/C 与 F/I 与 device-track 的时间窗不同，任何「累计通过数」无效。
8. **G8 trace 门未过**：raw 260 → 归一 111 行（44 行 PASS 方法列缺 `device:` 落点；57+10 行 UNVERIFIED 缺证据）；三个解析器缺陷已在 `trace.json.tool_defects` 登记。
9. **G9 外部阻塞（BLOCKED）**：真实 Provider（无密钥）、生产签名/AAB（`RELEASE_*` 4/4 缺失；现有证书 `CN=WS8 Ephemeral Local Only` 有效期至 2026-11-05 11:03）、arm64 真机与 16KB 设备（宿主 x86_64）、人工金标（无标注员）、TalkBack 全路径（缺宿主硬件驱动）。
10. **G10 未跑**：365MiB 备份 scale 两例、2h soak 之外的长稳/ monkey、迁移逐版本数据行保持、真机性能。
11. **G11 H 域旧时点不可混引**：`docs/acceptance/evidence/H/acceptance_report.json` 绑定旧台账 `d787ab4f…` 并记录 596/8/52/544；最终台账是 `e139b03e…` 的 670/21/73/436。引用必须带台账 sha。
12. **G12 审美/语感**：屏幕阅读器朗读、中文文案语感、视觉审美无编码判据 → 永久 UNVERIFIED，不由本索引签字。

## 7. 全部 E 文件 sha256 清单

机器清单文件：`E-FILES-SHA256.txt`（本节表的同内容，`sha256sum` 格式；sha256 `c2305e415fdbde4eb9eeda87a118a0acf1cebbcf0ba0222d23a803f1389bcf5e`，`sha256sum -c` 35/35 OK）；目录全量清单：`DIR-SHA256.txt`（144 文件，由 `tools/ci/acceptance_hash_manifest.py --root docs/research/evidence/2026-11-06-batch-44 --out … --self-hash` 生成，`--verify` 复核 **OK**，命令见 §9-6）。

| sha256 | E 文件 |
|---|---|
| `244cff7b3429e6e014c89fcf41d2e94e2fc71b618f86f1c71e474161704634a0` | `docs/research/evidence/2026-11-06-batch-44/acceptance-report.json` |
| `130358529e6555180e97483345055f0ecbde193e773e0905c90fe442f7bb0972` | `docs/research/evidence/2026-11-06-batch-44/acceptance/gates-final.log` |
| `bf86d867f0230ac5e552e0b177808b6c99a38bdc8e2ddf4b62e31cb8c6ef9fe2` | `docs/research/evidence/2026-11-06-batch-44/acceptance/report.json` |
| `fb1c4787509fb02c4aa767418da35bf026bea58a70a4af93ff10f1cf5138547d` | `docs/research/evidence/2026-11-06-batch-44/acceptance/lint.json` |
| `e26e529365b49a47ba81c1b057a4a1425de48c540f1e646cea02421306e3309c` | `docs/research/evidence/2026-11-06-batch-44/acceptance/trace.json` |
| `8ba1f672dbc0a67ebd7b5044f4e98e7233dccf31fdd5c3adb468051312cac680` | `docs/research/evidence/2026-11-06-batch-44/acceptance/crosscheck.json` |
| `78bdac6d47fd19431eaac756b4dc2dba63f79c19c6f2c1ee3ffdd68dbc0bee43` | `docs/research/evidence/2026-11-06-batch-44/acceptance/tool-selftests.xml` |
| `a523510295e4df7bdbbc0a062f932cf9198be9ab07c5a3fde24f102841f61adb` | `docs/research/evidence/2026-11-06-batch-44/acceptance/replay-drill.log` |
| `418a5c17f33c70e99b0cc0a07fce69191489cfedc94164bfa903785777c5bd4b` | `docs/research/evidence/2026-11-06-batch-44/acceptance/init-check.log` |
| `59950b941167346966503a64ab1580cf35b1e4c8b5e2308a857d4c4e0bb0633c` | `docs/research/evidence/2026-11-06-batch-44/acceptance/ledger-shape.log` |
| `e139b03e8b9a005af73d658333f7721907b7e6976718106934c00e096a3eb675` | `docs/acceptance/ledger.md` |
| `749c794b0d6b64f7afc261faa7433165d6a41bcc865f18ae79c0df767a15b0d8` | `docs/acceptance/verdicts.md` |
| `03df1e31b7015f393527ecc51515ab13b673c502e9197b3285e3fd69364aebc8` | `docs/acceptance/dod.md` |
| `f9f0998538c7f3fb9269ae7797b7f88d9219a441fb34fcb5f861847ccc3647df` | `docs/acceptance/runbook.md` |
| `3c653bd3c55824ba0284892757cf5df7ef602b86f8a7068d79e7d09ce1918840` | `docs/acceptance/ledger.schema.json` |
| `02fd732993c128de8ae4ad628314033e8d5580347f917e612c5be9d08254c2df` | `docs/acceptance/verdicts/README.md` |
| `172d17ef0cffbfa9fab8543144663d7a5a784175104b4db7e65be65b40f91328` | `docs/research/evidence/2026-11-06-batch-44/device-track/00-index.md` |
| `70614e00aab0c92877f24924591e575ba458fb5b18491f0cc8b4a2305f930f90` | `docs/research/evidence/2026-11-06-batch-44/device-track/source-snapshot.txt` |
| `469fae33092773f3c92483015ff10057ea0e1cb97882ccd81477a71ab5eef790` | `docs/research/evidence/2026-11-06-batch-44/device-track/apk-shas.txt` |
| `303c69e4f5ee09814e09724799c8947d86a54a74c10cef888c576b6ba4f03f27` | `docs/research/evidence/2026-11-06-batch-44/research-crosscheck.md` |
| `455b1a5b8f19b4b7ee4f9d8a527f55dfbeabb7d7fed7edebcab477c384888921` | `docs/research/evidence/2026-11-05-batch-44/a-domain/00-index.md` |
| `940cc589bcba86270ec61b1cc84654f966c50827ed0011707b4ee222f6e25953` | `docs/research/evidence/2026-11-05-batch-44/a-domain/adversarial-attempts.md` |
| `5ddbf3b93a64c0f65c7a94c02ed215ab6c520d0003ec9936ee6bd282b2134d8a` | `docs/research/evidence/2026-11-05-batch-44/b-domain/00-index.md` |
| `721345089457c2527d533d0305209c964005ec517931ed5ec8f18914f78bb47e` | `docs/research/evidence/2026-11-05-batch-44/c-domain/00-index.md` |
| `bf312be605b8cfef5b0334d64779a90327376d0f8b82698407ee97d730b78955` | `docs/research/evidence/2026-11-05-batch-44/e-domain/00-index.md` |
| `8982306415a5600be81ef7b0ed998d77f3b7363447afab46a631e98255e50a96` | `docs/research/evidence/2026-11-05-batch-44/f-domain/00-index.md` |
| `526b3b1002820bc73722a710b31921a1921f5a5bbd994bfc6d463d910d3e035d` | `docs/research/evidence/2026-11-05-batch-44/j-domain/00-index.md` |
| `14f33886823680817d2da8a3f213c25459a932fe7c68135c50d73caf7018d721` | `docs/acceptance/evidence/G/hashes.sha256` |
| `44530bcde468ff915475a8316ef19dfacb27a19cbff811f079a57bb0b69fbcbf` | `docs/research/2026-11-06-batch-44-baseline.md` |
| `0f2c2c4091b1c1944bda7c2c5d2b220faa3bc157e37a3cdee0a8a59f078c5eb7` | `docs/research/2026-11-06-metamorphic-relations.md` |
| `f3574a6639db83ffa55eb391eca840614e3f6edf94b7e12ea88c1a240368c3dd` | `docs/research/2026-11-06-release-acceptance.md` |
| `167cc9514a2a6178355ac4c7ad6c70b0a77bcb477cbd35bdcf1bd3171b81b5a9` | `docs/research/2026-11-06-screenshot-baseline.md` |
| `ee593e0d1c26129337bf1ea8e4736feb494be769a49dfef75391a4a4f51f7e32` | `docs/research/2026-11-06-a11y-acceptance.md` |
| `b5b6adc64bbafc2282ada5a6b3315b84231554664690f1b41be0e96be2132794` | `build/batch44-unit.log`（gitignored，仅登记） |
| `bb70eee3b4d01fa67c6930a767aefc328dd2595f7b2fcee3027a9fc639d6e6f0` | `build/batch44-strong.log`（gitignored，仅登记） |

- 以上 35 条均为本轮 `sha256sum` 实算（§9-2）；**清单只覆盖 E 文档**，不覆盖各域原始 XML/日志/PNG 与 G/H 设备 JSON；本目录全量覆盖见 `DIR-SHA256.txt`（144 文件，含本索引）。
- 哈希只绑定「本轮读取时刻的字节」；共享工作树仍在被并发写入（§2），复算不一致时先比 mtime/内容，不直接判文件被改。

## 8. 脱敏扫描（本目录 + 被索引的外目录，本轮实跑）

| 扫描 | 本目录（`2026-11-06-batch-44`） | 外目录（`2026-11-05-batch-44` / `docs/acceptance/evidence`） | 处置 |
|---|---|---|---|
| 强特征令牌（`sk-…`/`AIza…`/`ghp_…`/`Bearer …`） | **0 文件** | **0 文件** | — |
| 凭据赋值（apiKey/password/secret/token/storePassword/keyPassword） | 0 文件（首轮 160KB 输出全是 uiautomator `password="false"` 属性，属伪命中） | 0 文件 | — |
| 邮箱 | 0 文件 | 3 文件均**伪命中**：`f-domain/dropbox-after.txt`、`soak-crash-dropbox.txt`（dalvik 路径 `product@priv-app@…`）、`i-domain/talkback-review.logcat.txt`（`@androidx.work…`） | 不改动 |
| 本机用户路径 | 0 文件；仅有 `C:/Android/Sdk/...` 与 `e:/data/app/...` 设备路径（非用户目录） | `a-domain/00-index.md:111` 是 `rotate/home/kill` 伪命中；`G/tests/.mimosa/*` 2 文件含 `C:\Users\<user>\AppData\Local\Temp\g-acceptance\...` | `.mimosa` hook-state 是工具状态文件、**无凭据**；建议随证据提交时排除，不阻断本批定案 |

- 人工通读：本目录无学习者内容、无凭据、无真机序列号（仅模拟器 serial 与包名）。

## 9. 本次整理实跑的只读检查（可重跑）

```bash
# 1) 源绑定
git rev-parse HEAD                        # 542b9bbac49081aca808c616a5dc538965d1767e
git status --porcelain | wc -l            # 575
git status --porcelain -uall | wc -l      # 4390
git diff | sha256sum                      # 79e42969…b68（与 device-track snapshot 同值）

# 2) E 文件哈希（35 条，见 §7 / E-FILES-SHA256.txt）
sha256sum docs/acceptance/ledger.md docs/research/evidence/2026-11-06-batch-44/acceptance-report.json …
# → e139b03e… / 244cff7b… / …

# 3) 台账 verdict 逐行复算（不读 report summary）
python -I -c "逐行 split('|') 统计 domain×verdict"
# → rows 1200 / PASS 670 / FAIL 21 / BLOCKED 73 / UNVERIFIED 436；逐域与报告一致

# 4) 报告与门禁原文
python -I -c "json.load(acceptance-report.json)['counts']"     # 同上四值
grep -n '^## \|^EXIT=' docs/research/evidence/2026-11-06-batch-44/acceptance/gates-final.log
# → init 0 / report 0 / require-complete 1 / lint 0 / trace 1 / make-report 0

# 5) 跟踪态与忽略规则
git ls-files docs/research/evidence/2026-11-06-batch-44 | wc -l   # 0
git ls-files docs/research/evidence/2026-11-05-batch-44 | wc -l   # 0
git ls-files docs/acceptance | wc -l                              # 0
git check-ignore -v docs/research/evidence/2026-11-06-batch-44/acceptance/gates-final.log
# → .gitignore:31:*.log

# 6) 目录全量哈希清单（先生成、再校验）
python -I tools/ci/acceptance_hash_manifest.py \
  --root docs/research/evidence/2026-11-06-batch-44 \
  --out docs/research/evidence/2026-11-06-batch-44/DIR-SHA256.txt --self-hash
python -I tools/ci/acceptance_hash_manifest.py \
  --root docs/research/evidence/2026-11-06-batch-44 \
  --verify docs/research/evidence/2026-11-06-batch-44/DIR-SHA256.txt

# 7) 脱敏扫描（§8）
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza…|ghp_…|Bearer …' <各证据根>
```

- 上述 1–7 均由本索引整理者在本轮执行并读到输出；**未跑 Gradle、未跑设备、未复跑验收工具**，D0/十域判定全部为转录（出处见 §1/§4）。

## 10. 回链与维护

- 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md` §0/§1/§2/§3/§4/§6/§7。
- 上一批：`docs/research/2026-11-05-batch-43-record.md` §9（KD-76 open、isolation 不定案、p09 未收敛、发版不成立）；证据台账 `docs/research/evidence/2026-11-05-batch-43/00-index.md`。
- 研究件：`docs/research/2026-11-06-{batch-44-baseline,metamorphic-relations,release-acceptance,screenshot-baseline,a11y-acceptance}.md`；交叉复核 `research-crosscheck.md`。
- 本索引**不替代主会话裁定**，也不改任何行 verdict。当前可写的结论只有：**1200 项已枚举并逐项有 verdict（无空项），但验收不可签字**——670 PASS 未经独立复核、300 行结构性未判定、530 non-pass、证据日志未归档。
- 批次 44 record 落盘时，请回填 §1.2 的 `record-ref` 与本索引的 `E-44-*` 编号，不要另起一套 E-ID。

## 11. 体量自查

- 落盘后实测：`wc -lc docs/research/evidence/2026-11-06-batch-44/00-index.md` → **266 行 / 30,999 B**（含本行；均低于 R4 上限 400 行 / 65,536 B）；         清单见 §7 与 E-FILES-SHA256.txt。
- 目录：本文件 + `device-track/00-index.md` + `research-crosscheck.md` = **3 个 `.md`**；另有 `E-FILES-SHA256.txt`、`DIR-SHA256.txt` 两个清单。
