<!-- 体积自查：190 行 / 约 16 KiB；R4 上限 400 行 / 65536 B -->
# WS10 设备全量：28+ 套件 / 备份矩阵 / 崩溃签名 / worker / 性能冒烟

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS10-01（旧标识 `WS10-42` 保留为别名） |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | 计划 §2 WS10 item 76–80 |
| gate-or-kd | 76 全量套件、77 跨版本备份、78 崩溃签名、79 worker/强停、80 性能冒烟 |
| verdict | **PARTIAL**：32 套件 258 例 → 执行 243 / 绿 241 / 红 2 / 跳过 15；崩溃九类 0（除 p10 设计性 dnc）；强停恢复与 73→73 / v4→73 通过；isolation 15 例仍全部 `-4`（自证门 `own_cpu_busy`），未执行 |
| executed-at | 2026-11-03T13:28–14:05+08:00 |
| operator | 设备验证轨（WS10） |
| head | **未记录**（本文件只绑四包 sha；§0 明确“验证的是绑定 APK，不是当前工作树源码”——源快照缺口，见索引 §6） |
| device-serial | emulator-5584（API 34 / x86_64 / PAGE_SIZE 4096 / boot_id `2940a887…`） |
| apk-sha256 | app `35e10339c4606dabb17a8210763fba68a5d8acf1be67703793293b82d4bff1f8`；app.test `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca`；data.test `d88ec38f95672a5aa9f4eca6f4ec727e6d885355198303ef30954f3f2d0552b2`；database.test `7b4df3ea451759b9e02025a06dbaf692e3e445957d1f5f77ee23427f4def10d0`（全程未换；跑前/跑后 `sha256sum` 逐位一致） |
| exit-code | 未汇总：逐套件 `am instrument` 读数（`OK (n tests)` / `Tests run: …`）；未记录 shell exit code；本轨零 Gradle |
| raw-log | `build/device-track-batch42/`：`manifest.txt`、`suites/`（32×log + 32×logcat + progress）、`isolation/`、`worker/`、`perf/`、`lowload/`、`crash-signatures.json` |
| commands | 仅 `adb install -r -t` / `am instrument -w -r` / `am force-stop` / `am start -W` / `logcat` / `sqlite3`；**未运行 Gradle、未运行 `:app:connectedLocalFirstDebugAndroidTest`** |
| record-ref | 本文件；批次 40 E-40-05 §2（p09 同方法同报文）；批次 41 E-41-D05（isolation 同形） |
| sanitized | 是（无密钥/账号/Cookie；仅模拟器 serial） |

## 0. APK 绑定与构建边界

- 绑定的是**设备上已安装并被本批前序轨使用过**的四包（source of truth = 设备 `sha256sum`）：app `35e10339…`（WS3 KD-76 同值）、app.test `0e906482…`（批次 41 01:00 构建）、data.test `d88ec38f…`（09:40 构建，含 WS5 v4 夹具）、database.test `7b4df3ea…`（11:13 构建，本次 `install -r -t`）。
- **已知边界（不掩盖）**：宿主 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` 现为 `0f9fdc72…`（11:14 重建），与设备上的 `35e10339…` 不同；本次未安装该新包，所有结论只绑上表四包。app.test 仍是 01:00 构建，批次 42 对 `PdfAigcDeviceRoundTripInstrumentedTest` / `VisualJsConnectStageCalibrationInstrumentedTest` / `BackupSelectionUiInstrumentedTest` 的工作树改动**不在**该 APK 内。因此本文件验证的是“绑定 APK”，不是当前工作树源码。
- 设备身份：`google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`；`ro.build.version.sdk=34`；`x86_64`；PAGE_SIZE 4096；boot_id `2940a887-9c0b-4898-8961-be5aee1c1e66`（全程同 boot）；起跑 loadavg `0.13 0.08 0.02`。

## 1. 28+ 套件（item 76）

命令形状（逐套件一次独立 `am instrument`，禁 connected）：

```bash
adb -s emulator-5584 logcat -c
adb -s emulator-5584 shell am instrument -w -r -e class <FQCN> <PKG>/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5584 shell logcat -v threadtime -d > suite-<slug>-logcat.txt
```

| slug | 包 | N | 结果 |
|---|---|---|---|
| v01app | APP | 4 | `OK (4 tests)` |
| a09 | APP | 3 | `OK (3 tests)` |
| **p09** | APP | 1 | **`Tests run: 1, Failures: 1`** |
| p10 | APP | 1 | `OK (1 test)` |
| p0405 | APP | 1 | `OK (1 test)` |
| flags | APP | 2 | `OK (2 tests)` |
| **isolation** | APP | 15 | `OK (15 tests)`，但 **15×`STATUS_CODE: -4`（Assume 跳过，未执行）** |
| nvec | DATA | 4 | `OK (4 tests)` |
| x02 | DATA | 6 | `OK (6 tests)` |
| x03 | DATA | 1 | `OK (1 test)` |
| a10x | DATA | 1 | `OK (1 test)` |
| backup | DATA | 34 | `OK (34 tests)` |
| rowcounts | DATA | 2 | `OK (2 tests)` |
| trimmer | DATA | 10 | `OK (10 tests)` |
| c4 | DATA | 8 | `OK (8 tests)` |
| repo | DATA | 12 | `OK (12 tests)` |
| parity | DATA | 16 | `OK (16 tests)` |
| painter | DATA | 24 | `OK (24 tests)` |
| pdf | DATA | 16 | `OK (16 tests)` |
| ordinal | DATA | 1 | `OK (1 test)` |
| reconciler | DATA | 6 | `OK (6 tests)` |
| engine | DATA | 27 | `OK (27 tests)` |
| a10 | DATA | 3 | `OK (3 tests)` |
| gates | DB | 5 | `OK (5 tests)` |
| matrix | DB | 16 | `OK (16 tests)` |
| m6970 | DB | 4 | `OK (4 tests)` |
| m7071 | DB | 3 | `OK (3 tests)` |
| dbcommit | DB | 12 | `OK (12 tests)` |
| +v4pack | DATA | 3 | `OK (3 tests)` |
| +selrestore | DATA | 11 | **`Tests run: 11, Failures: 1`** |
| +selcreate | DATA | 3 | `OK (3 tests)` |
| +errorcause | DB | 3 | `OK (3 tests)` |

- 合计 **32 套件 / 258 例**：执行 **243**，绿 **241**，红 **2**，跳过 **15**（isolation 全套）。按 28 基线口径：28 套件 238 例（较批次 40 的 237 多 1 = trimmer 10 vs 9），**26 套件绿 + p09 1 红 + isolation 未执行**。
- 设备 churn：全程同一 boot、同一四包；`database.test` 仅在起跑前一次性 `install -r -t`（sha 与宿主一致），套件执行期间无重装、无 reboot。

### 1.1 红例 1：p09（已知红复现）

```text
aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight
java.lang.AssertionError: the fourth distinct program must be rejected by the client queue,
got Rendered(sceneJson={"schemaVersion":1,"sceneId":"scene-D",...})
```

- 用例断言：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt:138`。
- 与批次 40 E-40-05 §2 **同方法、同报文**；不是本批新增红，也无 `FATAL/ANR`。归因候选仍是 **T4/D 时序窗**（`abandonForTest()` 制造的重建窗口在 D 到达前已结束），本会话未做归因实验，**不判产品缺陷、不判已修复**。

### 1.2 红例 2：selrestore 的 `closureFourArchiveRestores…`（**测试装置缺陷，非产品路径**）

```text
closureFourArchiveRestoresWithAddedTablesEmptyAndNoRowLoss
android.database.sqlite.SQLiteException: no such table: error_cause_observation
  at BackupSelectionRestoreInstrumentedTest.rowCount(BackupSelectionRestoreInstrumentedTest.kt:758)
  at …$closureFourArchiveRestores…$1.invokeSuspend(…:254)
```

- 根因（读码判定，未做单点探针）：该用例在 restore 后只执行 `StudyDatabaseFactory.open(context, databaseName).close()`（`BackupSelectionRestoreInstrumentedTest.kt:248`），**Room 懒开库**——`.close()` 前没有任何 DAO/查询，72→73 `onUpgrade` 根本不跑，文件仍停 72，故 `error_cause_observation` 不存在。
- 同一语义的**真实冻结 v4 字节**用例 `BackupV4PackRestoreInstrumentedTest.frozenV4PackRestoresToSchema73…` 在 `reopened.readDatabaseVersion()` / `countMistakes()` 强制开库后通过（该类 3/3 绿；该 v4 恢复方法 1/1）：`:72` 回执 72、`:93` `user_version=73`、`:100-105` 新表 0 行。
- 处置：`BackupSelectionRestoreInstrumentedTest` 属备份线落点，**本轨不改**；建议该线把 `open().close()` 改为强制一次 DAO/`readDatabaseVersion()` 再断言（与 v4 夹具用例同形）。

### 1.3 isolation：15 例全部未执行（**不得记绿**）

- 默认运行（无门参数）：15×`-4`，原因 `no_shell_side_verdict;run_tools/ci/run_isolation_pair.sh`（`LowLoadProbe.kt:219-221`）。
- 追加真实 shell 侧门后重跑：`lowload_gate.py gate`（floor = `build/device-track-batch41/lowload/floor.json`，同为 5584）判 **PASS**（30 样本；`busy_p95=0.886%`、`wakeup_p95=23.61 ms`、snapshot `test_processes=0/orphan_workers=0`），传入 `-e smb.lowload.gate PASS -e smb.t4.arm OFF` 后仍 **15×`-4`**：
  `SMB_LOWLOAD_GATE: verdict=UNGATED processors=4 jitter_p95_ms=7.4 own_cpu_ms=3260.0 probe_window_ms=3004 reason=own_cpu_busy`（`LowLoadProbe.kt:130-133`，阈值 `ownCpuMs > 0.80 × window`）。
- 与批次 41 E-41-D05 同形（own_cpu≈窗长）。**isolation 未执行、未通过**；`-4` 不是 PASS。
- 第一次 shell 门尝试判 `INVALID reason=process_snapshot`（设备上仍有 `com.tingyun.smartmistakebook.localfirst.test` PID 4916 未退出）；`am force-stop` 应用与 test 包后第二次 PASS——说明该门对残留 test 进程敏感，属门卫生问题，已留原始两份 JSON。

## 2. 跨版本备份矩阵（item 77）

| 路径 | 证据（设备实跑） | 判定 |
|---|---|---|
| **73→73** | `selcreate` 3/3（生产 Room 73 库→成包，`SmbkSelectionPlan.CLOSURE_VERSION` 与 selection schema 断言）；`selrestore` 的 subset9/subset13/full 三例（73 库成包→恢复→重开）；`rowcounts` 2/2（`73.json`=92 entities、`database.version==SCHEMA_VERSION`、identityHash、包内/恢复后 `sqlite_master` 逐名 92 表） | **PASS** |
| **v4→73** | `v4pack` 3/3：冻结字节 sha == golden → 恢复 → 回执 `databaseSchemaVersion=72` → 重开 `readDatabaseVersion()=73`、`user_version=73`、`error_cause_observation/binding=0`、`problem=1`/`attempt_submission=2`/`knowledge_node=1`/`content_install_state=1` 不丢；另 2 例负向（自洽伪造 rowCounts 拒收、删表有行/载荷翻转在交换前拒收） | **PASS** |
| **v5→73** | `selrestore` 现行 closure-5 包用例（subset/full/legacyV2/非法包矩阵等 10/11）全绿 | **PASS（10/11）**；唯一红为 §1.2 的合成 v4 用例装置缺陷，不落在 v5 路径 |

- v4 夹具真实字节：`closure4-schema72-mask15.smbk`，设备内 sha 与 golden 逐位一致（用例内断言，见 `BackupV4PackRestoreInstrumentedTest.kt:57-67`）。
- 负向 fail-closed：`invalidDigestRowCountsAndClosureVersionAreRejectedBeforeSwapAndLiveDataStays`、`declaredSubsetThatStillContainsUnselectedRowsIsRejected…`、`fullSelectionWithRowCountsThatDisagree…` 均绿（`selrestore` 内）。

## 3. 崩溃签名九类（item 78）

聚合 45 份日志（32 套件 logcat + isolation/worker 专项 + perf 日志），命令：`python -I build/device-track-batch42/crash_scan.py`（脚本在树，可重跑）。

| 签名 | 次数 | 归属 |
|---|---|---|
| `FATAL EXCEPTION` | 0 | — |
| `ANR in` | 0 | — |
| `NoSuchMethodError` | 0 | — |
| `NoClassDefFoundError` | 0 | — |
| `UnsatisfiedLinkError` | 0 | — |
| `SIGSEGV` | 0 | — |
| `CANNOT LINK` | 0 | — |
| `failed to map segment` | 0 | — |
| `death was not confirmed` | **1/次 p10 执行** | `suite-p10-logcat.txt` 与 `worker/worker-logcat.txt` 各 1，均为 `VisualJsDeathUnconfirmedFailClosedInstrumentedTest` 的设计负向路径（非崩溃） |

- 0 不等于健康：isolation 未执行（§1.3）；`libquickjs.so` 只在 a09/p0405/p10 的 worker 路径加载；未覆盖 arm64/真机。

## 4. worker 生命周期与强停恢复（item 79）

### 4.1 干净窗 worker 计数（`worker/`，1 次独立 `logcat -c` 后跑 a09+p0405+p10+storage）

| 指标 | 值 |
|---|---|
| `Start proc .*:visual_js` | 5 |
| `warm-up finished` | 5 |
| `warm-up failed` | **0** |
| 未配对 spawn（spawn − warm） | **0** |
| `death was not confirmed` | 1（p10 设计） |
| 套件后 `ps -A \| grep visual_js` | **none（NO_ORPHAN）** |
| `StorageOperationLifecycleInstrumentedTest` | `OK (2 tests)`：中断进程不恢复幽灵 busy 操作；运行中操作经 Activity 重建只执行一次 |

### 4.2 强停恢复（`perf/force-stop-recovery.txt`）

```text
pid_before=4247 → am force-stop → pid_after_force_stop=（空）→ visual_js_after_force_stop=（空）
relaunch: Status ok / LaunchState COLD / TotalTime 3036 ms / pid_after_relaunch=4370
resumed: topResumedActivity=…/MainActivity
run-as databases: smart-mistake-book.db 12,816,384 B（WAL 24,752 B）
sqlite3: PRAGMA quick_check=ok；user_version=73；problem=5；error_cause_observation=0
logcat: FATAL EXCEPTION / ANR in = 0
```

结论：强停清空应用与 worker 进程；冷启恢复到前台；用户库完整（quick_check ok、schema 73、`problem` 表 5 行可读）；无崩溃签名。**边界**：这是脚本级强停，不覆盖系统内存回收（LMK）与真实低内存；PRD-08 的“强停/系统回收”人工走查仍未被自动化；设备为测试机，库内行由用例种子写入，不代表真实用户数据。

## 5. 性能冒烟（item 80）

| 项 | 方法 | 实测 |
|---|---|---|
| 首帧（冷启） | 每次 `am force-stop` 后 `am start -W`，3 次 | `TotalTime` **2901 / 3205 / 2876 ms**；logcat `Displayed … +2s901/+3s205/+2s876 ms`；wall 3639/3800/4609 ms |
| warm-up（KD-76 单轮） | `am instrument -e smb.kd76.round 1 …#readRoundFromInstrumentationArg`（先 force-stop） | `KD76_SAMPLE round=1 status=SAMPLE bind=616 warmup=109 readyGap=9 firstByte=204 total=938 ms`；`KD76_META` 自报 apkSha256=`35e10339…`（与绑定值一致）；`Time: 2.551`；`OK (1 test)` |
| 导出 | `MistakeExportBackgroundInstrumentedTest#exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable`（真实 UI→`ExportPdfWorker`） | `Worker result SUCCESS`；`Time: 7.31`；wall 10,683 ms；用例 `OK (1 test)` |

- 边界：KD-76 单轮 `bootCount=22`、**未 reboot**，是 warm-boot 冒烟，**不是主口径**（主口径 n=20/p95 见 WS3 证据）；导出计时含 UI 导航与 WorkManager 调度，不是纯渲染微基准；`/AIGC` 注入未在该 01:00 app.test APK 的导出链上验证（KD-75 归 WS2）。

## 6. 发现与移交

| # | 发现 | 位置/证据 | 严重度 | 处置 |
|---|---|---|---|---|
| W10-1 | isolation 套件在绑定 APK 上 **15/15 `-4`**（自证门 `own_cpu_busy`，`own_cpu=3260 ms / window=3004 ms`），`OK (15 tests)` 是假象 | `isolation/isolation-run.log`、`isolation/isolation-logcat.txt`、`LowLoadProbe.kt:130-133,203-210` | **高（口径）** | 本轨不改测试；任何“isolation 通过/28 套件全绿”表述不成立。修复方向：自证探针不计本进程并行线程 CPU 或改墙钟窗口径（属 WS4 落点） |
| W10-2 | `BackupSelectionRestoreInstrumentedTest.closureFourArchive…` 设备红：`open().close()` 不触发 Room 懒开库，72→73 未跑 | `BackupSelectionRestoreInstrumentedTest.kt:248,254`；对照 `BackupV4PackRestoreInstrumentedTest.kt:75-78` | 中（测试装置） | 移交备份线：改为强制 `readDatabaseVersion()`/DAO 读后再断言；产品 v4 路径由 v4pack 3/3 覆盖 |
| W10-3 | p09 第四 program 被判 `Rendered` 而非 `BUSY` | `VisualJsClientFrameQueueDeviceInstrumentedTest.kt:138`；同批次 40 E-40-05 §2 | 中 | 已知红复现，归因候选 T4/D；不由本轨定性 |
| W10-4 | 宿主 app APK（`0f9fdc72…`，11:14）≠ 设备绑定 app（`35e10339…`）；app.test 仍是 01:00 构建 | `manifest.txt`；`app/build/outputs` | 中（可复现边界） | 本结论只绑四包 sha；批次 42 app 侧测试改动未在本轮执行 |
| W10-5 | 第一次 shell 低负载门因残留 test 进程判 INVALID | `lowload/gate-isolation.json` vs `-r2.json` | 低（门卫生） | 跑前 `am force-stop` 应用与 test 包；已留两份原始 JSON |

## 7. 未验证 / 阻塞

- 未运行 Gradle：任何编译/单测门结论不在本文件；APK 为既得产物。
- 未重跑批次 42 工作树新改的 app.test 用例（PdfAigc / VisualJs 单轮 / BackupSelectionUi）——绑定 APK 不含它们。
- isolation 因果、OFF/ON 配对、T4 控制轮：**未执行**（本项只做套件与门；配对归 WS4）。
- 真机 / arm64 / 16KB：本机 x86_64 / PAGE_SIZE 4096，未覆盖。
- 系统内存回收（LMK）与真实低内存下的强停恢复：未覆盖。
- 导出 `/AIGC` 注入、打印部分页：不在本轮导出冒烟内（归 WS2）。
