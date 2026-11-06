<!-- 体积自查：约 190 行；R4 上限 400 行 / 65536 B -->
# 批次 42 设备轨：WS2/WS3/WS4/WS5/WS6/WS10 实跑证据

| 字段 | 值 |
|---|---|
| evidence-id | E-42-DEVICE-01 |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §2 WS2 18–21、WS3 29/33、WS4 34–40、WS5 45–46、WS6 54、WS10 76–80；计划 §3 设备轨 |
| verdict | **PARTIAL**：WS2 设备判据 PASS（AI 图链）；WS3 n=20/PASS；WS4 UNCONVERGED；WS5 v4 3/3；WS6 1/4（3 例夹具缺陷）；WS10 243 执行 / 241 绿 / 2 红 / 15 跳。**未伪造任何跳过为通过。** |
| executed-at | 2026-11-03T15:18–17:10+08:00 |
| operator | 设备轨（本 ask） |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf`；源快照 `git diff HEAD --binary \| sha256sum` = `d89cd1a4fe3f51c98fa2eb30290f5151e3236e44a93676ff704d0321a43fc876`（§0） |
| device-serial | emulator-5584（KD-76 末轮 boot `01798878…`）与 emulator-5566（全程 boot `8f54d45c…`）；见 `devices` 行 |
| devices | emulator-5584（AVD `kb_test_device`，boot `01798878…` 为 KD-76 末轮）；emulator-5566（AVD `audit_full_ui_forest_20261028`，全程 boot `8f54d45c…`） |
| apk-sha256 | app `ec135844ee879f8d889880aec618545889d00487094c9f52c95ba5f889e19bb5`；app.test `a2532e16d1809e666643bc5f243b410b14ba863375c6ecd6100c1f5da1e9687b`；export.test `0d34532cfe88cb117758308ef039c8b9aafd2804b567e25c6d6576f7d1bbeca0`；data.test `a3d0d4e295efd647558f02955d1842505a6903f702ac112345b1799368538e66`；database.test `a7a443737bb1000fb0e966b845d881cbef06ab3a22447e1e98c7d24882d709c5`；strictOffline app `562717aff63e03c926192ae472c968d44a296b939aecec7cbf815b29d4d39c29` / test `e6b1a9e50917043b087e1fe09e4b97d2d4ddb1efd2c48ea88a497f8e8ad853fa` |
| exit-code | 构建（五包 assemble）`EXIT=0`；设备结果为逐套件 `am instrument` 读数（`OK (n tests)` / `Tests run: …`），未汇总 shell exit code |
| raw-log | `build/device-track-batch42/`：`suites-5566/`（43 份 suite 日志 + 36 份 logcat，含 matrix 变体）、`ws2-pdf/`、`ws4-5566/`、`perf/`、`crash-signatures-5566.json`、`kd76-20r.driver.log`；KD-76 原始：`build/kd76-runs/ws3-batch42-r2/` |
| record-ref | 本文件；WS2 `ws2-kd75-closeout.md`；WS3 `ws3-kd76-20-rounds.md`；WS4 `ws4-isolation-causality.md`；WS5 `ws5-kd83-v4-fixture.md`；WS10 `ws10-device-full.md` |
| sanitized | 是（无密钥/账号/Cookie；仅模拟器 serial） |

> 口径：**实跑**=本 ask 内执行的命令与输出；**实读**=本 ask 内读到的源码/日志；**转录**=引用其他轨道证据。
> 设备绿只对 §0 的 APK sha + 源快照成立；设备重启/APK 被替换后的结果另注。

## 0. 绑定与源快照

- HEAD `faab17f58b01eab78d5c08a9f843b3af3df2fecf`；工作树 **649 条**（`build/device-track-batch42/source-status.txt`）；
  `git diff HEAD --binary | sha256sum` = `d89cd1a4fe3f51c98fa2eb30290f5151e3236e44a93676ff704d0321a43fc876`（`source-diff-sha.txt`）。
- 构建：`./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:export:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **EXIT=0**（`build-androidtest-r2.log`，358 tasks up-to-date）；16:04 追加 strictOffline 两包 → EXIT=0。
- 装机：`adb install -r -t` 五包，设备侧 `sha256sum $(pm path …)` 与宿主逐位一致（`installed-sha-5566.txt`、`installed-sha.txt`）。
- 设备指纹：`google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`；API 34；x86_64；PAGE_SIZE 4096。
- 源快照边界：data.test `a3d0d4e2` 是共享工作树 15:23 的构建产物，本轨 15:33 的 assemble（`build-androidtest-r2.log`）判定 358 tasks **全部 up-to-date**，故与当前源一致；但仍属“共享树快照”，不是独占构建窗口。

## 1. WS2 / KD-75 设备链（计划 18–21）

命令（core:export 的 androidTest 包）：

```bash
adb -s emulator-5584 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner
# OK (5 tests)  Time: 0.976
```

设备 logcat 的 `evidence=` 路径：
`/data/user/0/com.tingyun.smartmistakebook.core.export.test/files/pdf-aigc-export/run-1793690445299-6917/`。
`adb exec-out run-as … cat` 拉回（`MSYS_NO_PATHCONV=1`，否则 Git Bash 把 `/data/…` 改写成 `C:/Program Files/Git/data/…`）：

| 文件 | 字节 | sha256 | pypdf 读回 |
|---|---:|---|---|
| `real-export-labeled.pdf` | 53,271 | `77c6ba9c393394a62fca79733deaab107713f265ec54097c068663c1ead5685a` | 1 页；`/AIGC` 7 键；`Label=1`；`ProduceID=586844f4…f48b`（= 用例断言 `inputSha256`）；`ContentProducer=SmartMistakeBook` |
| `pure-user-unlabeled.pdf` | 40,406 | `f48e046a1714f474b0d7498b5a6887ccafb4ff12404af790fee70d56a95b24b4` | 1 页；**无 `/AIGC`**；info 仅 `/Producer` |

- 独立读者 `pypdf`（非项目 `PdfAigcReader`）：`ws2-pdf/pypdf-readback.txt`。
- 同批 app 回归 `PdfAigcDeviceRoundTripInstrumentedTest`：**OK (10 tests)**（5566 上复跑，`suite-apppdfaigc.log`）。
- 用例本身还断言：注入后字节前缀 == 渲染器原始字节（显式图注逐位不变）、`PdfRenderer` 页数不变、旧无标记同键产物被哨兵清空后重建、整份打印保留 `/AIGC`、**部分页 `writeSubset` 丢失文档级 `/AIGC`**（现状登记）。
- **KD-75 仍不可关闭**：设备判据 C1/C3/C5/C6/C7 对 AI 图链成立；但 (b) `MODEL_DOCUMENT_PARSE` 文本判据仍未实现（`ws2-kd75-closeout.md` §2 C2），C4 用的是代际哨兵而非字面 v3→v4。本轨不把设备绿写成 KD-75 关闭。

## 2. WS3 / KD-76 20 轮主口径（计划 29/33）

```bash
bash tools/ci/run_kd76_rounds.sh --serial emulator-5584 --mode reboot --rounds 20 \
  --first-round 1 --out build/kd76-runs/ws3-batch42-r2 \
  --apk app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk \
  --target-package com.tingyun.smartmistakebook.localfirst \
  --component com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
# [09:06:36Z] done: 20/20 rounds passed
```

`python -I tools/ci/summarize_kd76.py build/kd76-runs/ws3-batch42-r2` → exit 0：

```text
samples: n=20/20 | G1: green
apkSha256: ec135844ee879f8d889880aec618545889d00487094c9f52c95ba5f889e19bb5
bootCount(s): [24..43]（逐轮 +1）
host boot_id(s): n=20 unique=20
mode(s): ['reboot']
M: min=942 p50=1191 p90=1968 p95=1969 max=2218
gate: M.p95 <= 5000 ms (READY_TIMEOUT 10000 / 2; never relaxed)
G2: PASS (M.p95=1969 ms <= 5000 ms)
```

- 逐轮 `KD76_ENV` 均 `bootIdBefore == bootIdAfter`，20 个宿主 boot_id 全不同；`status=INVALID` 0 个、`status=SAMPLE` 20 个；`pidTarget`/`pidWorker` 全空（冷启动形状）；每轮 `KD76_META apkSha256=ec135844…` 与绑定一致。

| round | 宿主 boot_id（轮前=轮后） | bootCount | M=bind+warmup (ms) |
|---:|---|---:|---:|
| 1 | `4ad43ed9-b00b-46f4-8801-85977401464a` | 24 | 1203 |
| 2 | `cc0af1e0-f13a-4059-aa95-aeece324c261` | 25 | 1676 |
| 3 | `413d50a4-ab06-4f72-8c58-dc6baf0fd4c7` | 26 | 1101 |
| 4 | `a2130db0-d340-4dc4-9c28-3f063254ba59` | 27 | 1110 |
| 5 | `123e0664-e6a0-4e5b-92dd-38b7f624e1c1` | 28 | 1968 |
| 6 | `b3e06177-5057-4533-9de0-233a12e4781d` | 29 | 1969 |
| 7 | `72570e66-988d-40ba-8970-4cb5978443f5` | 30 | 2218 |
| 8 | `0b265d78-4432-4959-89ee-3708055ba727` | 31 | 1237 |
| 9 | `a40f2b86-1fbe-4b7f-a435-b7a785909147` | 32 | 1253 |
| 10 | `eb223e5a-aa5c-4cdd-ab9f-3ebe36d7a5c5` | 33 | 1885 |
| 11 | `fa694025-9336-4f83-b96a-f45a76d35f4b` | 34 | 1131 |
| 12 | `954e19ce-20d6-4c9c-a1c8-b7880c8b95ee` | 35 | 1078 |
| 13 | `7963f9ce-2318-4358-876c-ada4f94082ac` | 36 | 1126 |
| 14 | `d1d0ded3-14cb-4ec0-92f0-e2faeea98020` | 37 | 942 |
| 15 | `5ae0dc88-9ba6-485b-9fae-e9cbc41fe565` | 38 | 1191 |
| 16 | `4ccd9e00-5d3f-4161-8d43-e3ee64dde4c7` | 39 | 976 |
| 17 | `7128d734-8652-4a2e-8a11-3620883f9403` | 40 | 1676 |
| 18 | `a6c726f9-07b4-45f5-9488-213658bf81bd` | 41 | 1182 |
| 19 | `214effee-409e-43ea-968a-d3ed9a5de69b` | 42 | 1007 |
| 20 | `01798878-aaea-4017-a33e-d6ece83c25b3` | 43 | 1520 |

- 这是**当前源 APK**（含 F11 legacy `@Ignore` + 硬失败）的 20 轮；WS3 证据的 20 轮绑定的是 `35e10339…`（`ws3-kd76-20-rounds.md` §7.3 已注明）。两次不得相加成 n=40。
- 判词来源：`Kd76ReportVerdict.decide(sampleCount, g1Red, p95Millis)` 现为三参数、阈值常量唯一来源（`Kd76ReportVerdict.kt:25,31,80-102`，本 ask 实读）；`summarize_kd76.py` 与 Kotlin 同口径。
- 29.5 s 链：`kd76_chain_segments.py` → C1 p95=1760 / C2 p95=317 / C3 witness=2000 / C4 **UNVERIFIED**；四段实测不成立。
- 仍 open：R3 A6 的两批独立（本证据单批 n=20）、C4 无实测、arm64/真机未覆盖。

## 3. WS4 / isolation 因果（计划 34–40）

- 工具前置（WS4 轨已实跑，本轨不重复）：F7/F8/F10 修复后的 `pytest 41 passed`（`ws4-isolation-causality.md` §2.1）；F8 同 COMPONENT 物理拒绝 exit=2（§2.2）。
- 本轨在 **emulator-5566** 用 strictOffline 两包各跑 1 臂（`-e smb.lowload.gate PASS -e smb.lowload.reason shell_side_PASS -e smb.t4.arm OFF|ON`）：
- **低负载前门如实**：本轨**未重跑** 5 min 标定/门（WS4 轨已在同一 AVD 实跑：r1 PASS、r2/r3 INVALID，见 `ws4-isolation-causality.md` §3）；为单独验证自证探针，本轨显式传入 `-e smb.lowload.gate PASS`（**合成判词**），因此下表 `window=PASS` 不构成低负载门实测。

| 臂 | 结果 | SKIP | 自证探针 |
|---|---|---:|---|
| OFF | `OK (15 tests)` | **15** | `UNGATED reason=jitter_p95_absolute`（jitter p95=36.7 ms；own_cpu=2060/窗 4888 ms） |
| ON | `OK (15 tests)` | **15** | `UNGATED reason=own_cpu_busy`（own_cpu=2900/窗 3613 ms） |

- 臂开关生效：`arm=OFF test_side_hooks=false` / `arm=ON test_side_hooks=true`。
- `isolation_pair_report.py` → **REPORT_EXIT=1**，`verdict=UNCONVERGED`、`paired=false`、reasons `window_not_pass, instrumentation_ungated, rounds_short, skipped_or_unknown`、`missing_methods` 两臂均空、15 方法全 `SKIP`（`ws4-5566/pair.json`/`pair.md`）。
- **不写因果**：无生产侧 T4 移除的 OFF 控制臂（WS4 §4.3 BLOCKED）；`OK (15 tests)` 不是 15 例通过。

## 4. WS5 / KD-83 真实 v4 夹具（计划 45–46）

```bash
adb -s emulator-5584 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s emulator-5584 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupV4PackRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# OK (3 tests)  Time: 2.548（16b75158 时代）
# 5566 复跑：OK (3 tests)  Time: 6.075（a3d0d4e2）
```

- 3/3：冻结字节 sha == golden → 恢复到 schema 72 回执 → 下次开库 `readDatabaseVersion=73`、`user_version=73`、新表 0 行、旧行不丢；N1 伪造 rowCounts 拒收；N2 删表有行/翻位包交换前拒收。
- `BackupSelectionRestoreInstrumentedTest.closureFourArchive…` 在设备上仍红（11 例中 1 红，`suite-selrestore.log`）：`no such table: error_cause_observation`，位置 `BackupSelectionRestoreInstrumentedTest.kt:214`。根因同 WS10 W10-2：`StudyDatabaseFactory.open(...).close()` 不触发 Room 懒开库，72→73 未跑；**测试装置缺陷，不是产品 v4 路径**（v4pack 已用 `readDatabaseVersion()` 闭环）。
- KD-83 判据 ⑤（真实 v4 包设备恢复）满足；边界仍是“合成合法 v4 包”。

## 5. WS6 / 6C-2 三裁定（计划 54）

| 套件 | 命令/结果 | 判定 |
|---|---|---|
| `BackupSelectionRulingsInstrumentedTest`（data） | `Tests run: 4, Failures: 3`（`suite-selrulings.log`） | **1/4 绿**；3 红是夹具缺陷（见下） |
| `BackupSelectionUiInstrumentedTest`（app） | `OK (11 tests)`（`suite-selectionui.log`） | 0/8 可达、文案、域闸门、回执断言全绿 |

红例（设备原文）：

1. `imagesOnlyCarriesQuestionSourceEvidenceAndRestoresIt`：`恢复后题面必须在 expected:<1> but was:<0>`（`BackupSelectionRulingsInstrumentedTest.kt:104`）。包已在 :80-:97 断言 `problem=1` 并过读侧闸门，restore 也返回；红的是 `restored.problemCount`。
2. `emptySelectionBackupRestoresSettingsOnlyAndKeepsLocalContent`：`expected:<1> but was:<0>`（`:143`，**seed 后立刻断言**）→ 该用例在 create/restore 之前就退出，**mask=0 恢复未被执行**。
3. `nineMaskStillUsesReplacementSemantics`：`:192` 同一断言 → restore 已执行，仅 `restored.problemCount` 不符。

根因（本 ask 实读）：`StudyDatabasePort.countMistakes()` = `ProblemDao.countActiveMistakes()` = `SELECT COUNT(*) FROM error_book_entry WHERE status='ACTIVE'`（`ProblemDao.kt:318-319`）；而该用例 `StudySeedBundle(... errorBookEntries = emptyList())`（`BackupSelectionRulingsInstrumentedTest.kt:238-239`）只种 problem/revision。`RestoreReceipt.problemCount` 取自 `opened.countMistakes()`（`AndroidBackupRepository.kt:1176`、`:770`），因此恒 0。对照 `BackupSelectionRestoreInstrumentedTest.kt:642` 的可用夹具会种 `practiceUnits + errorBookEntries`。
**裁定**：3 红是断言目标错（把“题面行”断言成“ACTIVE 错题条目数”），不是产品回归；mask=0/mask=9 的恢复语义在设备上仍 **UNVERIFIED**，需 WS6 线补种子后重跑。

## 6. WS10 / 设备全量（计划 76–80）

绑定：5566 全程 boot `8f54d45c…`；app `ec135844`、app.test `a2532e16`、data.test `a3d0d4e2`、database.test `a7a44373`。命令形状：逐套件一次独立 `am instrument -w -r -e class`，禁 `:app:connectedLocalFirstDebugAndroidTest`。

| 套件 | 结果 | 套件 | 结果 |
|---|---|---|---|
| v01app | OK(4) | backup | OK(34) |
| a09 | OK(3) | rowcounts | OK(2) |
| **p09** | **FAIL 1/1**（同批次 40/41 报文） | trimmer | OK(10) |
| p10 | OK(1) | c4 | OK(8) |
| p0405 | OK(1) | repo | OK(12) |
| flags | OK(2) | parity | OK(16) |
| **isolation** | OK(15) 但 **15×`-4`** | painter | OK(24) |
| nvec | OK(4) | pdf | OK(16) |
| x02 | OK(6) | ordinal | OK(1) |
| x03 | OK(1) | reconciler | OK(6) |
| a10x | OK(1) | engine | OK(27) |
| gates | OK(5) | a10 | OK(3) |
| **matrix** | 13/16 在套件内 + 3 例单跑全绿 | m6970 | OK(4) |
| m7071 | OK(3) | dbcommit | OK(12) |
| v4pack | OK(3) | **selrestore** | **10/11**（§4 夹具红） |
| selcreate | OK(3) | errorcause | OK(3) |

- 合计 **32 套件 / 258 例**：执行 **243**、绿 **241**、红 **2**（p09、selrestore closure-4）、跳过 **15**（isolation）。
- matrix 16/16 方法均绿，但**不是一次套件跑完**：13 例在 5566 套件内；`errorCauseTables…`、`kf5bClassification…` 单跑（25.15 s / 21.71 s）；`everyExportedSchemaVersion…` 在 5566/5562 长 uptime 模拟器上 20+ 分钟不收敛（线程栈在 `ExportedSchemaTestDatabase.kt:27` 的 `nativeExecuteForChangedRowCount`，guest 仅 147 MB free + swap，~40 s/版本），在**刚重启的 5584** 上 `OK (1 test) Time: 201.65`。因此记“16/16 方法通过（分跑）”，不记“单次 full-suite 通过”。
- 崩溃签名九类（`python -I crash_scan.py suites-5566`，36 份 logcat）：除 p10 设计性 `death was not confirmed` ×1 外全 0；`FATAL EXCEPTION`/`ANR in`/`SIGSEGV` 等均 0。worker：spawn 10 / warm-up finished 10 / warm-up failed 0。
- 强停恢复（5584）：`am force-stop` → `am start -W` `LaunchState=COLD`、`TotalTime=3664 ms`；随后设备内 `run-as … sqlite3` 实测 `PRAGMA quick_check=ok`、`user_version=73`、`problem=5`（库 `smart-mistake-book.db`）。
- 导出（真实 UI→`ExportPdfWorker`）：`MistakeExportBackgroundInstrumentedTest#exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable` → `OK (1 test) Time: 22.286`，logcat `Worker result SUCCESS`、`ExportPdfWorker` 启动→SUCCESS 约 **1.30 s**（09:12:32.559→09:12:33.855）。
- 性能冒烟（`am force-stop` + `am start -W`）：5584（刚重启）**3358 / 2972 / 2900 ms**；5566（uptime 1d17h）**6588 / 5662 / 5582 ms**。差异归因 guest 内存/uptime，不作产品回归判据。
- 额外 30 例（WS2/WS6 设备类）：apppdfaigc 10/10、exportaigc 5/5、selectionui 11/11、selrulings 1/4（不计入 258 基线）。

## 7. 干扰与发现（必须读）

| # | 发现 | 证据 | 处置 |
|---|---|---|---|
| D1 | **共享设备被并发占用**：首批 5584 跑批期间，事件日志 07:26:16–07:26:21 连续出现 5 次 `am_kill … due to start instr`（另一条 workflow 在同机 `am instrument`/重装），随后 15:31 设备重启（boot `2940a887`→`15c7009c`），data.test 由 `16b75158` 变为 `a3d0d4e2` | `suites-r2/` 首批 backup/rowcounts/trimmer “Process crashed”；`logcat -b events`；`installed-sha.txt` vs 现装 | 该批三套件**不判产品红**；改到 5566 用绑定 sha 复跑：backup 34/34、rowcounts 2/2、trimmer 10/10 |
| D2 | WS6 rulings 3 红是断言目标错（§5） | `ProblemDao.kt:318-319`；`BackupSelectionRulingsInstrumentedTest.kt:143/192/104,238-239`；`AndroidBackupRepository.kt:1176` | 移交 WS6/备份线：种 `practiceUnits + errorBookEntries` 后重跑；mask=0 设备语义 UNVERIFIED |
| D3 | `closureFourArchive…` 设备红是 Room 懒开库 | `BackupSelectionRestoreInstrumentedTest.kt:239,214`；对照 `BackupV4PackRestoreInstrumentedTest` | 已知 W10-2，移交备份线强制 `readDatabaseVersion()` |
| D4 | p09 第四 program 被判 `Rendered` 而非 `BUSY` | `suite-p09.log`；`VisualJsClientFrameQueueDeviceInstrumentedTest.kt:227` | 同批次 40/41 已知红；本轨不定性 |
| D5 | isolation 15 例仍全 `-4`（自证门） | §3；`LowLoadProbe.kt` | UNCONVERGED；禁写 T4 排除 |
| D6 | 长 uptime 模拟器上迁移矩阵 I/O 饥饿 | `matrix-trace_00.txt`（native SQLite DDL）；guest `free` 147 MB + swap | 设备卫生建议：跑迁移矩阵前重启模拟器 |
| D7 | 5584 曾 `adb root` 读取 ANR trace，随后 `unroot`；m7071 被该操作打断一次 | `matrix-trace_00.txt`；`suite-m7071.log` adb-exit=255 | m7071 已单独复跑 3/3；root 模式未留下文件属主污染（后续 5566 套件全绿） |

## 8. 未验证 / 阻塞

- **WS6 mask=0 恢复**、mask=9 替换语义：用例在 seed 断言处退出，设备未执行 → UNVERIFIED。
- **KD-75 C2**（模型解析文本）与 C4 字面版本：产品缺口，非设备可补。
- **WS4 因果**：OFF 控制臂（生产侧 T4 移除）不存在 → BLOCKED；本轨只复现 UNCONVERGED。
- **KD-76 两批独立 / C4 实测 / arm64 真机**：未覆盖；本证据单批 n=20。
- **5566/5562 的冷启与迁移矩阵**：受 guest 内存/uptime 影响，数值不作产品回归判据。
- **arm64 / 16KB / 真机 / LMK**：本轨只跑 x86_64 API 34 PAGE_SIZE 4096。
- **其他 workflow 的并发写入**：源快照与 APK 绑定只对 §0 的 sha 成立；共享树在跑批期间有他线变更（649→651 脏路径）。

## 9. 复现命令

```bash
export ADB=C:/Android/Sdk/platform-tools/adb.exe
./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
  :core:export:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest
$ADB -s <S> install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
$ADB -s <S> install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk
$ADB -s <S> install -r -t core/export/build/outputs/apk/androidTest/debug/export-debug-androidTest.apk
$ADB -s <S> install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$ADB -s <S> install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
$ADB -s <S> shell sha256sum "$($ADB -s <S> shell pm path <pkg> | sed 's/^package://' | tr -d '\r')"
bash build/device-track-batch42/run-suites-5566.sh          # 32 套件 + 4 设备类
python -I tools/ci/summarize_kd76.py build/kd76-runs/ws3-batch42-r2
python -I tools/ci/kd76_chain_segments.py build/kd76-runs/ws3-batch42-r2 --c3-witness-ms 2000
python -I tools/ci/isolation_pair_report.py --run … --off-arm-production-t4 test-side-only --out …/pair.json
python -I -c "import pypdf,sys;r=pypdf.PdfReader(sys.argv[1]);print(r.metadata)" build/device-track-batch42/ws2-pdf/real-export-labeled.pdf
```

## 10. 回链

- 计划 `.zcode/plans/plan-2026-11-04-batch-42.md` §2 WS2/3/4/5/6/10、§4 硬约束。
- 批次 41 记录 §9.3（F7/F8/F10/F11/F12）；本批修复证据：`run_kd76_rounds.sh` 的 boot_id 门、`isolation_pair_report.py` 的 UNKNOWN 物化、`Kd76ReportVerdict.decide` 三参数签名。
- 本文件不替代主会话裁定：KD-75 维持 open（C2/C4）；KD-76 维持 open（两批/C4）；KD-83 读侧设备判据满足；isolation **UNCONVERGED**；WS6 设备 1/4 且有 3 个夹具红待重跑。
