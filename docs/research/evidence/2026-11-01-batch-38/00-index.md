# 批次 38 设备证据索引（2026-11-01）

- 记录：`docs/research/2026-11-01-batch-38-record.md`（记录员落笔位；本索引由设备轨提交）
- 计划：`.zcode/plans/plan-2026-11-01-batch-38-triple.md`
- 工作树 HEAD：`dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a`（本批改动全部未提交，工作树 786 条）
- 证据目录：`docs/research/evidence/2026-11-01-batch-38/`
- 本目录体积：**15** 个 `.md` / 内容 **71.6 KiB**（73367 B 终测，含 §8 复核节；`du -sk` = 96 KiB；首测 70094 B/68.4 KiB）（§3.2 命令实测，记录员落盘时复测；上限 16 个/512 KiB）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1

## 1. 环境

见 `step00-environment.md`。设备 emulator-5562（AVD test_device / API 34 / x86_64 / PAGE_SIZE 4096），5566/5584 他线禁碰、5596 API 23 未跑本轨。

## 2. 产物身份

| 产物 | 本机路径（`<repo>`=仓库根） | sha256 | versionName/变体 |
|---|---|---|---|
| app 主包 | `<repo>/app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` | `94c82daab45da2fc0fe11f788c68915b84f18400f8ad027a1ae018b4764828c9` | strictOffline debug（Gradle up-to-date，02:46 产物，输入集合=现树） |
| app androidTest | `<repo>/app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk` | `33e1d6a24c5b785af7752e585d9408847dc7991dc1962b85349a0297d75161fa` | strictOffline debug androidTest |
| database androidTest | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` | `7baf8c4624e14ec682aa2c70583cc8f0765da8c29fc78c49d2a40434d13c35c1` | debug androidTest |
| data androidTest | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` | `e86fce38d62c59b274b0e46de4e310ed712b2d2e7fb6b923938ce1e1c409bcc0` | debug androidTest（窗口 B，含授权修复） |

四包设备端 sha256 逐字节核对一致（E-01 §3/§4）；mapping 不涉（debug 变体，无 R8 结论）。

## 3. 步骤与结论

| E-ID | 步骤 | 门/KD | 设备 | 判定 | 证据（本批目录内相对路径:行） |
|---|---|---|---|---|---|
| E-00 | 环境与设备身份 | 信息性 | 5562 | PASS | `step00-environment.md:1` |
| E-01 | 重建重装 + 源快照门（含 core/model）+ dex 符号门 + asset 门 + 授权修复登记 | 批次 38 设备硬门 | 5562 | PASS | `step01-rebuild-install-gates.md:1` |
| E-02 | A-09 注入式 3 例（首跑 1 红 → 复跑 3/3 绿） | A-09（清单 `:197`） | 5562 | PASS（以复跑轮为准） | `step02-a09-ads.md:1` |
| E-03 | P-09 队列背压真进程负向 1 例 | P-09（清单 `:132`） | 5562 | PASS | `step03-p09-queue.md:1` |
| E-04 | P-10 死亡未确认 fail-closed 1 例 | P-10（清单 `:133`） | 5562 | PASS | `step04-p10-death-unconfirmed.md:1` |
| E-05 | P-04/P-05 重建预算注入形 1 例 | P-04/P-05（清单 `:127-128`） | 5562 | PASS | `step05-p0405-reconnect.md:1` |
| E-06 | 隔离 15 例（首跑 2 红 → 复跑 15/15 绿，含 K6 两方法 + P-12③） | 隔离 15 不回退 | 5562 | PASS（以复跑轮为准） | `step06-isolation.md:1` |
| E-07 | flags 2 + V-01 app 3 | flags/V-01 不回退 | 5562 | PASS | `step07-flags-v01app.md:1` |
| E-08 | database 五套件 39 例（结构 5/矩阵 15/69-70 4/70-71 3/提交 12） | 不回退 | 5562 | PASS | `step08-database-suites.md:1` |
| E-09 | 备份组 47 例（备份 34/rowCounts 2/裁剪器 9/C4 8 含 D-04 三新例） | 不回退 + D-04 | 5562 | PASS | `step09-backup-suites.md:1` |
| E-10 | 视觉组 102 例（仓库 12/parity 16/painter 24/PDF 16/本地序号 1/对账 6/引擎 27） | 不回退 | 5562 | PASS | `step10-visual-data-suites.md:1` |
| E-11 | A-10 预算边界 3 例（含跨 method 计数不重置位 + 终答位子门） | A-10（清单 `:198`）/V-02 配套 | 5562 | PASS | `step11-a10-budget.md:1` |
| E-12 | 崩溃签名 26 窗全 0 + worker 生命周期对账 + 红例逐条归因（3 红全 D 环境、全被同 APK 绿复跑取代） | 步骤五 | 5562 | PASS | `step12-crash-signatures-lifecycle.md:1` |
| E-13 | KD-76 校准（判据未就绪：计时 seam 0 命中，未执行） | KD-76 | — | UNVERIFIED（条件不成立未执行） | `step13-kd76-calibration.md:1` |

## 4. 覆盖对照（任务项 → 结论）

| 任务项 | 结果 | 备注 |
|---|---|---|
| 一·重建重装 + dex 符号门 + 源快照门（含 core/model） | ✅ E-01 | 源快照 1220 文件两窗口 diff 全 0；dex 门 app 19 + database 21 + data 30 符号全 OK |
| 二·A-09 注入式 | ✅ E-02 | 3/3 绿（复跑轮）；四断言位 + 两负控机制各自如实 |
| 二·P-09/P-10/P-04/P-05 负向 | ✅ E-03/E-04/E-05 | 预算三元组注入形成套（首连半边=隔离套件既有例 E-06 复跑绿） |
| 二·A-10 跨 method | ✅ E-11 | 3/3 绿含跨 method 计数不重置位与终答位子门 |
| 二·V-01 降级变体若修复 | ⛔ 未满足条件不跑 | 已知 2/4 夹具红未修复（`singleOrNull` 形态仍在 `core/data/…/V01VisualChainInstrumentedTest.kt:238/:563/:595`）；按判据"不允许已知红例进套件单"登记（E-01 §6） |
| 三·既有 20+ 套件不回退 | ✅ E-06–E-10 | 24 套件 190 例全绿；唯一计数差 C4 5→8 为本批 D-04 三新例（构建前复读为准，E-09 §5） |
| 四·KD-76 校准 | ⛔ UNVERIFIED | 判据未就绪（E-13）；KD-76 维持 open |
| 五·崩溃签名与生命周期收口；红例归因 | ✅ E-12 | 26 窗九类签名 0；3 首跑红全 D 环境归因、同 APK 绿复跑取代 |

**设备总账**：24 套件 / **223 例**全绿（6 新例 + 217 既有例；以各套件复跑/末轮为准；3+1+1+1+15+2+3+5+15+4+3+12+34+2+9+8+12+16+24+16+1+6+27+3），0 跳过，0 崩溃签名，0 无因 worker 死亡。

## 5. 缺口与未落盘

1. **V-03/V-04 设备套件未跑（非本轨套件单）**：同批 V-03/V-04 轨的三个新套件（`TutorVisualCommitRaceInstrumentedTest` 3 例、`TutorVisualRebuildRestoreInstrumentedTest` 3 例、core:database `TutorVisualSavedProblemRetentionInstrumentedTest`）已随两窗口构建进 dex（编译经由本轨授权的两处机械修复解锁，E-01 §2），但其运行归 V-03/V-04 轨的判据与套件单，本轨未执行、不计入本索引判定。
2. **V-01 降级变体**：条件不成立未跑（E-01 §6）；3 行级夹具修复仍待主会话裁定（批次 37 遗留口径不变）。
3. **KD-76 校准**：判据未就绪（E-13）；seam 落码 + 低 RAM 档位为前置。
4. **A-10 协调器级"IMAGE+JS 混合合计第 3 份拒绝"向量**：仍无直接协调器级测试（清单登记口径维持）；JVM `FigureMethodSwitchTest`（V-02 轨件）+ 本批 E-11 设备位共同守卫，混合合计向量扩例不在本轨。
5. **JVM 单测门状态（继承，非本轨门）**：V-02 轨交接时"单测门第 3 轮 exit=1；强门未跑"；`FigureMethodSwitchTest.kt` 在交接后 03:42 仍有改动——本轨未运行任何 JVM 测试，该门现状 **UNVERIFIED（本轨未跑）**，不以本索引任何设备绿替代。
6. **arm64/16KB/真机口径**：维持既有缺口（本批全部数字为 x86_64/API 34 模拟器口径）。

## 6. 脱敏扫描与回链检查（§4.4 命令实测）

```text
0 hits: secrets / 0 hits: credential assignments / 0 hits: emails / 0 hits: home paths
git check-ignore -v step00-environment.md → 无输出（可跟踪）
```

- [x] 每个 E-file 有 `sanitized` 字段且扫描 0 命中
- [x] 全部证据为 `.md` 且在 `docs/research/evidence/` 下；`build/` 路径只出现在 raw-log 字段
- [x] 无"工单转录"行被用作判定；每个数字可回链 E-ID + path:line
- [x] step 文件只追加不改历史（本批首写；如需更正走 §7.6 更正节）

## 7. 回链检查

- [x] 记录（`docs/research/2026-11-01-batch-38-record.md`，记录员落笔）应引用本索引 E-ID；本索引为映射权威
- [x] `docs/known-defects.md` 的 KD Progress 由主会话按本索引回填（KD-76 维持 open，见 E-13）

## 8. 记录员复核（2026-11-01 落盘时点实跑，与设备轨执行相互独立）

以下命令由证据落盘员在落盘时点**本轮实跑**；结果与上述各 step 判定一致，未发现不实登记。

| # | 复核项（命令） | 结果 |
|---|---|---|
| 1 | 体量（§3.2：`find -printf` + `wc -l` + `du -sk`） | 15 个 `.md`；单文件最大 106 行（本索引，含复核节）；终测内容 73367 B（71.6 KiB）/ du 96 KiB——上限（400 行/64 KiB/16 个/512 KiB）全未超 |
| 2 | §4.4 四条脱敏扫描 | 0 hits × 4（secrets / credential assignments / emails / home paths） |
| 3 | `git check-ignore -v <dir>/*.md` | 无输出（全部可跟踪，C5） |
| 4 | `git rev-parse HEAD`；`git status --porcelain \| wc -l` | `dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a`（与各 step 头部一致）；落盘时 789 条（开工 786 条为他线在飞漂移，仅记录） |
| 5 | `python tools/ci/check_r04_keep_rules.py` | OK×3 / **KEEP_GATE_EXIT=0**（与 E-01 §2 一致） |
| 6 | S=71 静态脚本（batch-34 判据 §1.4 原文，`python -I` 重跑） | `SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / VIEWS_MATCH=True / PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138 / RESULT=PASS / EXIT=0`（与 E-01 §2 一致） |
| 7 | raw-log 在位核对 | `build/device-track-batch38/` 67 文件；step 引用的 build{,2,3}.log、src-sha-*、src-snapshot-windowB.diff、dex-gate-*、test-counts-before.txt、step14/14b、step21/21b、step22–25 等全部存在 |
| 8 | 首构建错误行复读 | `build.log:381-382` = `TutorVisualCommitRace…:284:9 Unresolved reference 'TutorVisualIdentity'` + `TutorVisualRebuildRestore…:229:51 Suspension functions can only be called within coroutine body`（与 E-01 §2 原文一致） |
| 9 | 快照 diff 复算（`diff` 三组） | 窗口 A before/after = 0 行；窗口 B before/after = 0 行；A-after vs B-after 恰 2 文件，sha 过渡 `8c90702c…→6a6ec7be…`、`aecf847f…→fc9f45fa…` 与 E-01 §2 登记一致 |
| 10 | dex 门复核 | dex-gate-{app,database,data}.txt 各末行 `DEX OK`、`MISSING=0` |
| 11 | APK sha256 现盘复算（`sha256sum` 四包） | 与本索引 §2 表逐字节一致（`94c82daa…`/`33e1d6a2…`/`7baf8c46…`/`e86fce38…`） |
| 12 | 源码断言位复核（grep） | `singleOrNull()` 仍存于 core/data `V01VisualChainInstrumentedTest.kt:238/:563/:595`（降级变体确未修复，任务条件不成立）；`grep -rn connectStageTimestampsForTest`（引擎 + app/data 两侧 androidTest）= 0 命中（E-13 依据①）；C4 `@Test` 计数 = 8；两处授权机械修复在位（race 文件 import `:11`、restore 文件 `suspend fun restoreByPortRead` `:228`） |
| 13 | 计数账复算（`test-counts-before.txt` 求和） | 25 文件合计 227 = 24 套件 223 例 + core/data V-01 降级变体 4 例（不进套件单）；6 新例（A-09 3 + P-09/P-10/P-04-05 各 1）+ 217 既有例 = §4 设备总账一致 |

- 记录文件 `docs/research/2026-11-01-batch-38-record.md` 落盘时点尚未写就（记录员落笔位），本索引为映射权威。
- **落盘进行中 HEAD 前移登记**：落盘后半段主线外 KB 线提交 `2bb4582a`（批次 30，feat(kb)），`git rev-parse HEAD` 由 `dcfdc5b5…` 变为 `2bb4582a…`。该提交只把开工前已是 M 态的工作树文件（dense/knowledge/RoomTutorToolRunner/SafeTruncation 面、core/model 快照面）入史，**不改工作树字节**，故 E-01 源快照窗口绑定（窗口内 diff=0 + 快照 sha 存 raw-log）不受影响；证据目录 15 个 `.md` 全部仍未跟踪（`git ls-files` = 0），按纪律归主会话提交。
- 本轮复核零 git 写操作（仅 `status/rev-parse/check-ignore/ls-files/log` 只读）；未运行 Gradle（复核仅静态脚本与文件读）。
