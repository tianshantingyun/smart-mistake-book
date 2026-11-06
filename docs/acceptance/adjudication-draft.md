<!-- 体量自查：落盘后以 wc -lc 实测为准。 -->

# 批次 44 终裁草案（供主会话逐项签字）

> **本文件是草案，不是签字，也不是验收结论。** 写入范围仅本文件；未改台账、未改工具、未改源码、未做 git 写。
>
> - 计划：`.zcode/plans/plan-2026-11-05-batch-44-acceptance.md`（§1 验收机制 100 / §2 十域 900 / §3 修复 100 / §4 门禁终裁 100）
> - 输入一（报告）：`docs/acceptance/verdicts.md` + `docs/research/evidence/2026-11-06-batch-44/acceptance-report.json`
> - 输入二（复核 findings）：`docs/research/evidence/2026-11-06-batch-44/00-index.md`（G1–G12）、`acceptance/crosscheck.json`（trace 逐条重算）、`research-crosscheck.md`、批次 43 收口 §9
> - 上一批收口：`docs/research/2026-11-05-batch-43-record.md` §9（KD-76 open、isolation 不定案、p09 未收敛、发版不成立）
> - 不可变清单（只读确认，未改）：6C-2 三项——mask=8 不提供、mask=0 禁止、派生索引恒裁

## 0. 本草案的证据边界（我实际跑了什么）

**本轮亲手执行（命令 + 关键输出逐字）**，全部在 `HEAD=542b9bba`、`porcelain=575`、`-uall=4398` 的共享工作树上：

| # | 命令 | 结果 |
|---|---|---|
| 1 | `python -I tools/ci/acceptance_init.py --check` | **exit 0**（静默） |
| 2 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md` | **exit 1**；`ledger_sha256=02ba5307…`；`counts={PASS:668, FAIL:21, BLOCKED:73, UNVERIFIED:438}`；`state=FAIL`；`violations=101`（全 `PLACEHOLDER_CMD`，覆盖 **80 个唯一 id，全部是 E 域 PASS 行**）；`pass_with_review=0` |
| 3 | 同命令加 `--require-complete` | **exit 1**（NOT_ACCEPTANCE_COMPLETE） |
| 4 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **exit 0**；`violations=0`；`warnings=29`（全 `EVIDENCE_REUSED`） |
| 5 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **exit 1**；`gaps=60`（50 `MISSING_EVIDENCE_PATH` + 10 `DEVICE_EVIDENCE_MISSING`）；60 行**全部是 UNVERIFIED** |
| 6 | `python -I -m pytest tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py tools/tests/test_acceptance_init.py tools/tests/test_trace_requirements.py -q` | **44 passed**，exit 0 |
| 7 | `python -I -m pytest tools/tests/test_h_compliance_gate.py -q` | **90 passed**，exit 0 |
| 8 | `python -I tools/ci/h_compliance_gate.py --only H-066` | **PASS / exit 0**（复现不出 FAIL） |
| 9 | `… --only H-052` | **PASS / exit 0**（复现不出 FAIL） |
| 10 | `… --only H-018 / H-061 / H-062` | **FAIL / exit 1 / 1 / 1**（仍可复现） |
| 11 | `python -I tools/ci/metamorphic_checks.py --demo` | exit 0 |
| 12 | `git apply --check tools/ci/acceptance_ci.patch` | exit 0（新文件提案可落地，未应用） |
| 13 | `sha256sum docs/acceptance/ledger.md` | **`02ba5307…`**，与报告的 `e139b03e…` **不同** |
| 14 | 逐行复算台账六列 | 1200 行；逐域计数与报告 `domain_counts` 一致（见 §2） |
| 15 | `git ls-files docs/acceptance \| wc -l`；两证据根 `git ls-files \| wc -l` | **0 / 0 / 0**；`docs/acceptance/verdicts/` 仅 `README.md` + `_template.json`（0 个终裁 JSON） |

**本轮未跑（不得计为通过）**：任何 Gradle、任何 adb/设备、真实 Provider、Play Console、人工金标、arm64。按纪律**未**执行 `:app:connectedLocalFirstDebugAndroidTest`。

**读源码/证据（逐处可指认）**：`core/domain/.../BackupSelection.kt:90,140,166`、`app/build.gradle.kts:33-34,125-128`、`app/.../SchedulingSettingsScreen.kt:45-53,93`、`app/.../SecondaryScreens.kt:826`、`core/export/.../DocxMistakeRenderer.kt`（无 `/AIGC` 命中）、`f-domain/soak-crash-logcat.txt:1-9`、`j-domain/j-signing.log`、`j-apksigner.log`、`j-version-artifacts.log`、`j-aab16k.log`、`c-domain/c-restore-single-rerun.xml`、`H/logs/*.log`、`build/batch44-unit.log`、`build/batch44-strong.log`、`device-track/{source-snapshot,apk-shas}.txt`。

## 1. 结论先行

1. **不可签「批次 44 验收通过」。** 当前态 1200 行里 PASS 668 / FAIL 21 / BLOCKED 73 / UNVERIFIED 438（`#2`，与 `#14` 逐域一致）；非 PASS 共 **532** 行。
2. **报告本身现在不通过**：`acceptance_report.py` 在当前台账上 **exit 1**，而盘上 `verdicts.md` / `acceptance-report.json` 记录的是「exit 0、670/21/73/436、ledger `e139b03e`」。**报告与台账不同源，记录不可复现。**
3. **「PASS」在本批只等于「该行证据列通过 ≥2 类独立证据的机械审计」**：668 行中 `pass_with_review=0`，即 **668/668 没有独立复核**（DoD §1.5 未成立）；再扣掉 E 域 80 行（`PLACEHOLDER_CMD` 违规），**机械可签面至多 588 行，且仍全部缺复核**。
4. **FAIL 集不稳定**：21 条里至少 2 条（`H-052`、`H-066`）在当前树已复跑为 PASS；`F-055/F-057` 的根因已在工作树内修好但**无执行证据**。**不能拿这 21 条原样签字或原样开修复单。**
5. **300 行结构性未判定**：D0 100 / FIX 100 / GATE 100 **全 UNVERIFIED** → 验收机制自身、修复域、终裁域从未回填。
6. **发版就绪：不成立**（§5），且其中一项是**用户可达的产品崩溃**（§3-B3），不是外部阻塞。

## 2. 十三域汇总与域级裁定（草案）

口径：**域裁定**列按「有可复现失败→FAIL；只缺外部条件→BLOCKED；PASS 面不成立→UNVERIFIED；全域 PASS 且有复核→PASS」判定，**无一个域达到 PASS**。

| 域 | 总数 | PASS | FAIL | BLOCKED | UNVERIFIED | 域裁定（草案） | PASS 面覆盖什么 / 为何不可签 |
|---|---:|---:|---:|---:|---:|---|---|
| D0 验收基础设施 | 100 | 0 | 0 | 0 | 100 | **UNVERIFIED** | 15 类工具与台账**已存在且可跑**（`#1/#4/#6/#11/#12` 是我亲手跑的），但 D0 的 100 行**证据列全空**；「机制已验收」不成立 |
| A 功能旅程 | 90 | 65 | 0 | 1 | 24 | **BLOCKED** | 设备旅程在 app `84cae864…` 上跑过；`A-003` 中断恢复缺用例（07:31 被改 UNVERIFIED）；`A-010` 讲题主路径因无 Provider BLOCKED；device-track 另 4 套件 FAIL（§4-F10） |
| B 数据完整性 | 90 | 88 | 0 | 0 | 2 | **UNVERIFIED** | 迁移 1..73 与备份 18 行在册（device-track `database` 319/319、`data-backup-suite` 84/84）；残余：未在 1..72 每版实例化数据行 |
| C 视觉与渲染 | 90 | 77 | 1 | 0 | 12 | **FAIL** | 106 例设备用例 105 绿；`C-065` 恢复回放「不留墨水」**XML `failures=1`**（我读原件）→ 真失败；另：台账 C-001..030 骨架原写错原语（`acceptance_init.py:270-271`），已由域重写 |
| D 模型链路 | 90 | 81 | 0 | 9 | 0 | **BLOCKED** | JVM 金样/schema/解析在册；`D-082…D-090` 9 条真实 Provider 全 BLOCKED；**无 `00-index`**（G3） |
| E 复习与算法 | 90 | 80 | 0 | 0 | 10 | **UNVERIFIED**（PASS 面机械失效） | 80/80 PASS **全部**被 `PLACEHOLDER_CMD` 判违规：`e-domain/*.cmd.log` 的 `CMD:` 行是 `<E-domain classes …>` 这类摘要，**不是可复现命令**（我读 `e-domain-domain-jvm.cmd.log:1`）。XML 存在，但命令溯源不成立 |
| F 性能与稳定 | 90 | 55 | 2 | 10 | 23 | **FAIL** | `F-055/F-057` 2h soak 崩溃（真 FATAL，我读 `soak-crash-logcat.txt:1-9`）；arm64 10 条 BLOCKED；`F-050` 内存归因 07:31 被改 UNVERIFIED |
| G 安全与隐私 | 90 | 82 | 0 | 2 | 6 | **BLOCKED** | 静态/依赖/日志面在册；`G-087` 无离线 advisory、`G-088` 锁文件 NOT_LANDED 放行态 → 不计通过 |
| H 合规与文案 | 90 | 66 | 5 | 12 | 7 | **FAIL** | 5 条 FAIL 中 `H-018/H-061/H-062` 复跑仍 FAIL（真缺口：DOCX 无 `/AIGC`、KD-84、KD-85）；`H-052/H-066` 已复跑 PASS（判词过期）；12 条外部（Play/隐私政策/热区/CAC） |
| I 可用性与无障碍 | 90 | 53 | 0 | 5 | 32 | **BLOCKED** | 语义树/触摸目标/200%/暗色结构 PASS；TalkBack 全路径 BLOCKED；对比度与 9 条语感行 UNVERIFIED；**无 `00-index`**（G4） |
| J 发布工程 | 90 | 21 | 13 | 34 | 22 | **FAIL** | 13 条 FAIL 是发版硬阻塞（§5）；34 条外部依赖（账号/成对产物/arm64）；仅 21 条本机可核项 PASS |
| FIX 修复与加固 | 100 | 0 | 0 | 0 | 100 | **UNVERIFIED** | **计划 §3 在本批未发生**：100 行无「先红后绿」记录；实际有 3 处修复落在工作树却未回填（§3-B3） |
| GATE 门禁与终裁 | 100 | 0 | 0 | 0 | 100 | **UNVERIFIED** | 独立复核/对抗终裁 0 个 JSON；十域独立复核未做 |

## 3. 三条必须先处理的阻断（草案提出，按严重度）

### B1 台账 / 工具 / 报告三者不同源（口径阻断，最高优先）

- 现台账 sha `02ba5307…`；记录报告绑定 `e139b03e…`；`acceptance_report.py` 与 `acceptance_lint.py` 的 mtime 为 `07:32:15 / 07:20:15`，晚于报告生成时刻 `2026-11-04T22:17:48Z`（`verdicts.md:4`）。
- 台账在 `07:31:26` 被改：逐行对比，`A-003`、`F-050` 由 **PASS→UNVERIFIED**（两行的「方法」列已写入 `UNVERIFIED:` 理由）。
- **后果**：任何引用 `verdicts.md` 的 670/21/73/436 都在引用旧字节；当前可复算值是 **668/21/73/438 且 report exit 1**。
- 解除条件：冻结台账 → 重跑 report/lint/trace → 用新 run-id 重生成 `verdicts.md` 与 `acceptance-report.json`；在此之前**不签任何行**。

### B2 E 域 80/80 PASS 违反 `PLACEHOLDER_CMD`（证据阻断）

- 报告 101 条违规全部落在 E 域，覆盖 80 个 PASS 行（E 域 PASS 总数恰为 80）。
- 根因：`e-domain` 的 `.cmd.log` 只记摘要命令（如 `e-domain-domain-jvm.cmd.log:1` 是 `--tests <E-domain classes incl StudyDayMathTest>`），台账「方法」列才是具体命令 → **证据不记录实际执行命令**，不可复现。
- E 域索引自称「台账每行 method 只描述该行断言，不重复整条命令」，这是设计选择，但与 DoD 的 `cmd` 证据定义冲突。**不能靠调低工具规则洗绿**；要么重生成日志写入逐字命令，要么把该证据降级为 `test:` 单一类（则 PASS 不成立）。
- 解除条件：E 域 `.cmd.log` 用逐字命令重跑重录 → report `violations=0` → 复跑 `--require-complete` 看剩余项。

### B3 FAIL 集不稳定 + 修复无执行证据（判词阻断）

| 行 | 报告判词 | 本轮复跑/实读 | 结论 |
|---|---|---|---|
| `H-052` | FAIL | `h_compliance_gate.py --only H-052` → **PASS/exit 0**；`docs/ai-content-disclosure-copy.md:9` 已写「2026-11-05 批次 44 H-052 修正」 | **判词过期** |
| `H-066` | FAIL | `--only H-066` → **PASS/exit 0**；`SecondaryScreens.kt:826` 已是固定文案（`:826` 注释明写「不拼接」） | **判词过期** |
| `F-055/F-057` | FAIL | 崩溃栈真（`SchedulingSettingsScreen.kt:74` → `StudyExperienceRepository.kt:737`）；但 `SchedulingSettingsScreen.kt:45-53` 已加 `retentionFromSlider` 夹回 `[0.7,0.97]`（`git diff` 实读，未提交），配套测试 `SchedulingRetentionSliderValueTest.kt` 存在 | **根因已在树内修，但无执行证据**：单测门日志只含 12 个模块，**不含 `:app:testDebugUnitTest`**（`build/batch44-unit.log` 实读），该测试类无任何 XML |
| `H-018/H-061/H-062` | FAIL | `--only` 复跑仍 exit 1；`DocxMistakeRenderer.kt` 无 `/AIGC` | **真 FAIL，维持** |

- **后果**：21 条 FAIL 不能原样作为「待修清单」或「发版阻断清单」引用；必须先按域重跑再定。
- 附带证据质量问题：`docs/acceptance/evidence/H/logs/*.log` **全部 2 行**（`H-xxx PASS/FAIL 一句话` + `EXIT=`），是自述判词而非门输出；`J-025` 的「证书已过期」在其 cited `j-keytool.log`（2 行：SHA256 + EXIT=0）里**不可见**（该细节只写在 `j-domain/00-index.md:58`）。FAIL 方向可由 `j-signing.log`（`RELEASE_*` 4/4 缺失）与 `j-apksigner.log`（`CN=WS8 Ephemeral Local Only`）独立支持，但**证据链要补齐**。

## 4. 不可签字项清单（逐条可执行）

| 范围 | 数量 | 为什么不能签 | 解除条件（可执行） |
|---|---:|---|---|
| 总体结论 | 1 | 532 行非 PASS；报告自身 exit 1 | 全部门禁绿 + 逐行复核 JSON 存在（现状 0） |
| `D0-001…100` | 100 | 证据列全空；机制可跑但没有回填 | 对每行跑声明命令并回填 `cmd:`/`test:` 证据，report 复算 |
| `FIX-001…100` | 100 | 无「先红后绿」值守记录；B3 的三处修复未入账 | 每项补红相→绿相证据（`test:`+`cmd:`），并把 H-052/H-066/F-055 的修复登记进来 |
| `GATE-001…100` | 100 | 独立复核 0、对抗终裁 0；`docs/acceptance/verdicts/*.json` = 0 | 每域产出 1 份独立复核 JSON + 1 份对抗反例（现状 A/B/C 各 1，共 3/10） |
| `E-001…090` 的 80 条 PASS | 80 | `PLACEHOLDER_CMD` 违规 | 重录逐字命令日志后 report `violations=0` |
| 其余 588 条 PASS | 588 | `pass_with_review=0`：所有 PASS 缺 DoD §1.5 独立复核 | 每行 `review:<path>` 且 reviewer ∉ authors、绑 run-id 与 `ledger_sha256` |
| 21 条 FAIL | 21 | 2 条已不成立、2 条根因已修未验、其余需按域重跑 | 按域复跑后重判；H 域先补真实门输出日志 |
| 73 条 BLOCKED | 73 | 外部条件缺失（Provider 10 / arm64 10 / Play 34 / H 12 / I 5 / G 2） | 按 `docs/acceptance/runbook.md` 拿到条件后复跑改判；不得转 PASS |
| 438 条 UNVERIFIED | 438 | 含 trace 60 条真实缺口（50 证据文件不存在 + 10 设备证据缺失），全为 UNVERIFIED | 产出证据后重判；`MISSING_EVIDENCE_PATH` 全部指向未生成的 verdict JSON |
| 人类语感/审美 | I-082…090、H-075/H-076 等 | 自动化不可替代（DoD §5） | **不由本草案签字**；标人类残余 |
| 证据归档 | 全部 | `docs/acceptance`、两个证据根 `git ls-files = 0`，`.log` 被 `.gitignore:31` 忽略 | 提交前决定证据入库策略；否则干净克隆上 report 会把依赖 `cmd:*.log` 的行判缺失 |

## 5. 发版就绪判定：**不成立**

| # | 硬阻塞 | 证据（本轮实读/实跑） | 级别 |
|---|---|---|---|
| 1 | **用户可达崩溃**：复习安排滑杆端点 → `SchedulingOptions` 越界 → FATAL | `soak-crash-logcat.txt:1-9`（`IllegalArgumentException` @ `StudyExperienceRepository.kt:737`）；树内已修（`SchedulingSettingsScreen.kt:45-53`）但**未跑测试**（单测门不含 `:app`） | P1 |
| 2 | 工作树不干净、产物落后 | `porcelain=575`、`-uall=4398`（本轮）；`J-001` 记录 4248 脏；`J-002` 记录 release APK 落后 HEAD **32 提交** | P0 |
| 3 | 生产签名缺失 | `j-signing.log`：`RELEASE_KEY_ALIAS/KEY_PASSWORD/STORE_FILE/STORE_PASSWORD` **4/4 缺失**、`tracked_keystores=[]`；`app/build.gradle.kts:125-128` 缺任一即静默跳过 | P0 |
| 4 | 签名身份不一致/临时证书 | `j-apksigner.log`：`CN=WS8 Ephemeral Local Only, OU=NotForRelease`；`j-keytool.log` AAB 证书 `A9:E7:6C:AD…` ≠ APK `9D:C0:7E:9F…`（`J-021/J-024`） | P0 |
| 5 | AAB 内容不合格 | `j-aab16k.log`：`abi_set=[arm64-v8a, armeabi-v7a, x86, x86_64]`（`J-033/J-041`）；`libquickjs.so` 缺失（`J-034`），`EXIT=1` | P0 |
| 6 | versionCode 恒 2、无生成机制 | `app/build.gradle.kts:33-34` 手写 `versionCode = 2`；`j-version-artifacts.log` 四份产物全为 2（`J-048/J-051`） | P0 |
| 7 | AIGC 隐式标识未覆盖交付格式 | `H-018` exit 1：DOCX 无 `/AIGC`；`H-061` KD-84；`H-062` KD-85；`DocxMistakeRenderer.kt` 无 `/AIGC` 命中 | P0（合规） |
| 8 | 质量门未绿 | report exit 1（101 违规）、trace exit 1（60 gap）、300 行结构性未判定、独立复核 0 | P0 |
| 9 | 设备面有真红 | `C-065` XML `failures=1`；device-track 4 套件 FAIL；arm64/16KB 真机 BLOCKED（宿主 x86_64） | P1 |
| 10 | Play 侧全未做 | `J-017/J-073…J-090`：账号、素材、data safety、封闭测试轨道、mapping 上传均 BLOCKED | P1 |

**判定依据**：任何一条 P0 成立即不足以发版；本批 P0 共 7 条。**结论：不成立，且不因「600+ PASS」而改变**——PASS 是机械审计口径，不是产品放行口径。

## 6. 残余风险登记

| # | 风险 | 现状 | 影响 |
|---|---|---|---|
| R1 | 四条 app APK 不可合并 | A/C/G `84cae864…`、F/I `b8503f04…`、device-track `f534b0dd…`、J release `724ce718…`（`00-index.md` §3） | 任何「累计通过数/设备全绿」都无效；结论必须带 APK sha |
| R2 | 双门是增量绿 | unit `31 tasks: 1 executed`；strong `226 tasks: 3 executed`（我读两份日志）；且单测门不含 `:app` | 不能证明当前源码全量可测；被掩盖的编译/测试红不可见 |
| R3 | 强门「PASS」非干净单跑 | `build/batch44-strong.log` 末行 `GATE RESULT: PASS at 2026-11-05 04:36:14`，但 223/226 up-to-date | 与批次 43「clean single-pass」问题同类 |
| R4 | 台账骨架曾由生成器猜写 | C-001..030 原写错原语，域内已重写（`c-domain/00-index.md:31-49`） | 其余域是否也有「生成器猜测 → 只部分修正」未逐域复核 |
| R5 | H/J 日志是自述判词 | `H/logs/*.log` 全 2 行；`J-025` 细节不在 cited log | 判词不可独立复算；FAIL 方向可能对，证据链不成立 |
| R6 | 证据未跟踪 | 三个证据根 `git ls-files=0`；`.log` 被忽略 | 干净克隆无法复算 668 行；验收结果随本机磁盘丢失 |
| R7 | 设备抽检依赖模拟器 | 全部结论绑 x86_64 模拟器；arm64/真机/16KB 全 BLOCKED | 真机崩溃/性能/16KB 装载风险未覆盖 |
| R8 | 修复落在工作树、未提交、未跑 | `SchedulingSettingsScreen.kt` 修复 + 测试未进任何门 | 若按当前树发版即带未验证修复；若按 HEAD 发版即带 P1 崩溃 |
| R9 | isolation / KD-76 / p09 沿用批次 43 未决状态 | 本批未重裁 | 不得把批次 44 的绿写成对这些的解决 |
| R10 | 6C-2 只做只读确认 | `BackupSelection.kt:166` `{9,13,15}`、`:90` `isCreatable`、`:140` `require`（我读源码） | 行为未漂移；但本批**未跑**不变量测试（Gradle 门禁阶段才跑） |

## 7. 建议主会话的签字动作（按序）

1. **不签批次级**。先在记录里写明「报告与台账不同源，670 不成立，当前值 668/21/73/438」。
2. **先做 B1**：冻结台账，重跑 report/lint/trace，重生成 `verdicts.md` + JSON，记录新 run-id 与 `ledger_sha256`。
3. **再做 B2**：E 域重录逐字命令日志，直到 `violations=0`；否则 E 域 80 行一律记 UNVERIFIED。
4. **再做 B3**：H 域 5 条 FAIL 重跑（本轮已跑：H-052/H-066 转 PASS，H-018/H-061/H-062 维持 FAIL），F 域 soak 崩溃在门禁阶段跑 `:app:testDebugUnitTest` + 设备复跑，C-065 复跑；按结果改判并把修复登记进 FIX 域。
5. **逐行签字**：只对「report exit 0 + lint exit 0 + 有 `review:` JSON（reviewer ∉ authors，绑 run-id/ledger_sha256）」的单个 id 签 PASS；不签域、不签批次。
6. **发版**：§5 的 7 条 P0 逐条取证后再判；在此之前一律「不就绪」。
7. **人类残余**：I-082…090、H-075/H-076 保持 UNVERIFIED，由人签，不由本草案代签。

## 8. 验收替代的边界（本草案明确不覆盖）

- 屏幕阅读器朗读语感、中文文案语感、视觉审美：无编码判据 → 永久 UNVERIFIED（`00-index.md` G12）。
- TalkBack 全路径、第三方中文 IME、真机传感器旋转/折叠屏、真实 Provider 报文、Play Console 全流程、人工金标：BLOCKED，出口在 `docs/acceptance/runbook.md`。
- 本草案对以上各项**不给 PASS、不给结论**。

## 9. 回链与复跑

- 复跑命令：`python -I tools/ci/acceptance_init.py --check`；`python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md [--require-complete]`；`python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md`；`python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md`；`python -I -m pytest tools/tests/ -q`。
- 本草案引用的报告：`docs/acceptance/verdicts.md`（§0/§1/§3/§4/§5/§6）、`docs/research/evidence/2026-11-06-batch-44/acceptance-report.json`。
- 复核 findings：`docs/research/evidence/2026-11-06-batch-44/00-index.md` §4/§5/§6（G1–G12）、`acceptance/crosscheck.json`（trace 归一 111 → 现工具已收敛为 60，均为 UNVERIFIED）。
- 批次 43 未决项：KD-76 open、isolation 不定案、p09 未收敛、AUD-08 FAIL、发版不成立——本批**未改变**这些结论，不得据本批 PASS 数回填。
