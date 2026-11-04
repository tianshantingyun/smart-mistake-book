<!-- 体积自查：242 行 / 18,709 B；R4 上限 400 行 / 65536 B -->
# WS4 设备复基线：post-rollback APK、迁移矩阵、备份族、错因账本、PDF、崩溃签名、worker、32 套件

| 字段 | 值 |
|---|---|
| evidence-id | E-43-WS4-01 |
| convention-version | 1 |
| batch | 2026-11-05-batch-43 |
| step | 计划 §2 WS4 item 29–36 |
| gate-or-kd | 29 APK 重建、30 迁移矩阵 1→73、31 备份族、32 错因账本、33 PDF、34 崩溃签名、35 worker/强停、36 全量套件（含 p09 定性） |
| verdict | **PARTIAL（8 项已执行；不是 PASS）**：5 包重建并逐位绑定；matrix 16/16（含 1..73 循环）；backup 34/34、rowcounts 2/2、trimmer 10/10、c4 8/8；errorcause 3/3；pdfaigc 10/10、exportaigc 7/7；崩溃九类 8 类 0、第 9 类 2 次均为 p10 设计负向；worker 无未配对 spawn、无孤儿、强停冷启恢复且 `quick_check=ok`；32 套件复跑 258 声明 / 243 执行 / **241 绿 / 2 红 / 15 跳**。**两红**（p09、selrestore）**使“设备复基线通过”不成立**；isolation 15 例仍零执行。selrestore 已在 05:29 由他线修复（设备轨 `OK (11)`），本轨红早于修复；p09 仍未收敛。**本轨不得记“通过/全绿”**（2026-11-05 修复环按正文事实订正 verdict）。 |
| executed-at | 2026-11-04T03:04–03:52+08:00 |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（工作树 578 脏项；本轨未改任何源码） |
| device-serial | emulator-5584（`kb_test_device`；API 34 / x86_64 / PAGE_SIZE 4096 / boot_id `556a8213…`；起跑 loadavg 1.68） |
| apk-sha256 | app `1aee470aaff2db5320eae676e8155de75efc4c1febf21c09b6c8ea63e29ed3cd`；app.test `cd35452efbc9d125242b515bf84f1cba747bd53bf849f85aa87a49c0ed5ed76d`；data.test `48213049f887930b70a10bcdb611f24a242d411bc2a3ecda7c177b13a808154f`；database.test `671b381573fa1d7d7891670aa7cf8f194546788abb5b8f735fcbc32d0e458fec`；export.test `08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0`（host 与设备 `sha256sum` 逐位一致，跑前/跑后各核一次） |
| exit-code | 逐套件读 `OK (n tests)` / `Tests run: n, Failures: f`；`adb-exit` 每套件记入 `suites/progress.txt`（32 基线全 0） |
| raw-log | `build/ws4-batch43/`：`build-apk.log`、`manifest-5584.txt`、`installed-sha-5584.txt`、`suite-summary.json`、`suites/`（34×log + 34×logcat + progress）、`worker/`、`crash-signatures.json`、`p09-rep{2,3}.log(+logcat)`、`device-5562-incident.txt` |
| commands | 仅 `gradlew`（一次串行构建）+ `adb install -r -t` / `am instrument -w -r` / `am force-stop` / `am start -W` / `logcat` / `run-as sqlite3`；**未运行 `:app:connectedLocalFirstDebugAndroidTest`** |
| sanitized | 是（无密钥/账号/Cookie；仅模拟器 serial 与包名） |

## 0. 本轨不做的事（先划界）

- **不改源码/测试**（计划 §4：本轨为验证轨）。p09 与 selrestore 的红都只定性、不修。
- **不评判 KD-75/KD-76 关闭**：`pdfaigc` 10/10 与 `exportaigc` 7/7 只是设备侧事实，C2 判据是否落在导出链由 WS1 对账。

## 1. APK 重建与绑定（item 29）

### 1.1 构建

```text
./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
  :core:export:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest --console=plain
```

- 结果：**EXIT=0**，`358 actionable tasks: 333 executed, 25 up-to-date`（`build-apk.log` 末两行）。
- 构建源快照：HEAD `0fef9436` + 工作树 578 脏项；本轨落笔前 `source-snapshot.txt` 记了 app/core 侧的 M/?? 清单（含他线 `KnowledgeImportProgress.kt` 等）。
- **post-rollback 判据（实读源码，不是听述）**：`BackupSelection.ALLOWED_MASKS = linkedSetOf(9, 13, 15)`（`core/domain/.../BackupSelection.kt:166`）；`requireCreatable()` 对 mask∉ALLOWED 直接 `require` 失败（`:140-145`）；`isCreatable = isClosed && mask in ALLOWED_MASKS`（`:90`）。即批次 42 §9.2 的撤回在源码里成立，本次 APK 由该源码构建。

### 1.2 设备绑定（source of truth = 设备上 `sha256sum`）

| 包 | host sha | 设备 sha | 结论 |
|---|---|---|---|
| `…localfirst` | `1aee470a…` | `1aee470a…` | 一致 |
| `…localfirst.test` | `cd35452e…` | `cd35452e…` | 一致 |
| `…core.data.test` | `48213049…` | `48213049…` | 一致 |
| `…core.database.test` | `671b3815…` | `671b3815…` | 一致 |
| `…core.export.test` | `08ea2641…` | `08ea2641…` | 一致 |

- 安装后对 app/app.test 又核一次，套件跑完第三次核，sha 全程未变（无中途重装）。
- 起跑前 `pm clear com.tingyun.smartmistakebook.localfirst`，避免批次 42 残留库污染。
- **旧绑定被取代**：5584 上批次 42 的 `35e10339…`（app）/`0e906482…`（app.test）已被本次 5 包覆盖；批量 42 §4.1 的“设备绿绑 `35e10339…`”自此只对旧证据有效。

### 1.3 5562 弃用（基础设施，非产品）

- `adb -s emulator-5562 reboot` 后：`Mem 2534120K total / 124656K free`、`Swap 24320K used`、loadavg 一度 **17.34**、`INSTRUMENTATION_ABORTED: System has crashed.`；`logcat -b crash` 有 zygote `ZygoteCommandBuffer.getCount` 崩栈。（这几个值是事故当刻在本会话读到的；当刻未另存原始文件——`device-5562-incident.txt` 是 03:52 的**事后**状态。）
- 现态（`device-5562-incident.txt`，03:52）：344916K free、loadavg 3.75 / 5min 8.53 —— 已恢复但仍不适合长时间仪表化。
- 处置：改绑 5584（批次 42 WS10 同机同刷机指纹，可比性最好）。**5562 上的任何结论为零**。

## 2. 全量套件（item 36）

命令形状（逐套件一次独立 `am instrument`，套件间 `logcat -c` 清窗；runner 见 `build/ws4-batch43/run-suites.sh`）：

```bash
adb -s emulator-5584 logcat -c
adb -s emulator-5584 shell am instrument -w -r -e class <FQCN> <PKG>/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5584 shell logcat -v threadtime -d > suite-<slug>-logcat.txt
```

运行窗：**03:31:35 → 03:44:33**（同 boot `556a8213…`，无重装、无 reboot）；`progress.txt` 每套件两行（START/DONE + adb-exit）。

| slug | 包 | N | 执行 | 绿 | 红 | 跳 | 原样读数 |
|---|---|---|---|---|---|---|---|
| v01app | APP | 4 | 4 | 4 | 0 | 0 | `OK (4 tests)` |
| a09 | APP | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| **p09** | APP | 1 | 1 | 0 | **1** | 0 | `Tests run: 1, Failures: 1` |
| p10 | APP | 1 | 1 | 1 | 0 | 0 | `OK (1 test)` |
| p0405 | APP | 1 | 1 | 1 | 0 | 0 | `OK (1 test)` |
| flags | APP | 2 | 2 | 2 | 0 | 0 | `OK (2 tests)` |
| **isolation** | APP | 15 | **0** | 0 | 0 | **15** | `OK (15 tests)` + 15×`STATUS_CODE: -4` |
| nvec | DATA | 4 | 4 | 4 | 0 | 0 | `OK (4 tests)` |
| x02 | DATA | 6 | 6 | 6 | 0 | 0 | `OK (6 tests)` |
| x03 | DATA | 1 | 1 | 1 | 0 | 0 | `OK (1 test)` |
| a10x | DATA | 1 | 1 | 1 | 0 | 0 | `OK (1 test)` |
| backup | DATA | 34 | 34 | 34 | 0 | 0 | `OK (34 tests)` |
| rowcounts | DATA | 2 | 2 | 2 | 0 | 0 | `OK (2 tests)` |
| trimmer | DATA | 10 | 10 | 10 | 0 | 0 | `OK (10 tests)` |
| c4 | DATA | 8 | 8 | 8 | 0 | 0 | `OK (8 tests)` |
| repo | DATA | 12 | 12 | 12 | 0 | 0 | `OK (12 tests)` |
| parity | DATA | 16 | 16 | 16 | 0 | 0 | `OK (16 tests)` |
| painter | DATA | 24 | 24 | 24 | 0 | 0 | `OK (24 tests)` |
| pdf | DATA | 16 | 16 | 16 | 0 | 0 | `OK (16 tests)` |
| ordinal | DATA | 1 | 1 | 1 | 0 | 0 | `OK (1 test)` |
| reconciler | DATA | 6 | 6 | 6 | 0 | 0 | `OK (6 tests)` |
| engine | DATA | 27 | 27 | 27 | 0 | 0 | `OK (27 tests)` |
| a10 | DATA | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| gates | DB | 5 | 5 | 5 | 0 | 0 | `OK (5 tests)` |
| **matrix** | DB | 16 | 16 | 16 | 0 | 0 | `OK (16 tests)`（`Time: 420.671`） |
| m6970 | DB | 4 | 4 | 4 | 0 | 0 | `OK (4 tests)` |
| m7071 | DB | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| dbcommit | DB | 12 | 12 | 12 | 0 | 0 | `OK (12 tests)` |
| v4pack | DATA | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| **selrestore** | DATA | 11 | 11 | 10 | **1** | 0 | `Tests run: 11, Failures: 1` |
| selcreate | DATA | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| errorcause | DB | 3 | 3 | 3 | 0 | 0 | `OK (3 tests)` |
| +pdfaigc | APP | 10 | 10 | 10 | 0 | 0 | `OK (10 tests)` |
| +exportaigc | EXPORT | 7 | 7 | 7 | 0 | 0 | `OK (7 tests)` |

- **32 基线合计**：258 声明 / 243 执行 / **241 绿** / **2 红** / **15 跳**。与批次 42 同形（258/243/241/2/15），但换成了 post-rollback 五包。
- **加两项定点**：275 声明 / 260 执行 / 258 绿 / 2 红 / 15 跳。
- 汇总由 `parse_suites.py` 生成（同一脚本先对批次 42 日志复算，得到与批次 42 记录逐项相同的 258/243/241/2/15，证明解析口径没漂移）。

## 3. 迁移矩阵 1→73（item 30）

- `FullMigrationMatrixInstrumentedTest` **16/16**，`Time: 420.671 s`。
- 覆盖证明（读码）：`everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 循环 `for (version in 1..STUDY_DATABASE_VERSION)`，每版 `createDatabaseFromExportedSchema` → `StudyDatabaseFactory.open`（触发 onUpgrade 链）→ 断言 `libraryCatalogCount == 0`（非破坏）→ 删库（`FullMigrationMatrixInstrumentedTest.kt:39-51`）；`STUDY_DATABASE_VERSION = 73`（`core/database/.../StudyDatabase.kt:142`）。
- 其余 15 例为逐版结构钉子（v53 计划指纹、v56 m5、v57 m1、v58 kf32、v59 目录重建、v60 导出记录、v61 绑定审计、v62 记忆索引、v63 kf30、v64 kf5b、v65 重绘任务、v67 证据纠正目标、v68 图生成账本、v72 错因表、以及 projectionArchive 一例）。
- **时长口径**：420.671 s 比批次 42 的 201.65 s 慢一倍（同机不同负载/不同 uptime），不得写成性能回归；两次都通过。

## 4. 备份族（item 31）

| 套件 | 读数 | 说明 |
|---|---|---|
| backup | `OK (34 tests)`（`Time: 29.545`） | 34/34 |
| rowcounts | `OK (2 tests)`（`Time: 2.794`） | `73.json` 92 实体 + 恢复后 `sqlite_master` 逐名 |
| trimmer | `OK (10 tests)`（`Time: 81.169`） | trim 规则未回退 |
| c4 | `OK (8 tests)`（`Time: 9.571`） | closure-4 视觉引用 |
| v4pack | `OK (3 tests)` | 冻结 v4 字节恢复 → `user_version=73` |
| selcreate | `OK (3 tests)` | 73 库成包 |
| **selrestore** | `Tests run: 11, Failures: 1` | 唯一红见 §9.2 |

## 5. 错因账本（item 32）

- `ErrorCauseLedgerInstrumentedTest` **3/3**，`OK (3 tests)`，`Time: 0.786`。

## 6. PDF（item 33）

- `PdfAigcDeviceRoundTripInstrumentedTest` **10/10**，`OK (10 tests)`，`Time: 0.299`。
- 10 个方法名（逐条读日志）：`xrefStreamPdfSurvivesInjectionAndOpensInPlatformRenderer`、`malformedHeaderIsRejectedWithTypedErrorAndNoOutput`、`modelParsedTextForcesAigcLabelling`、`missingStartXrefIsRejectedWithTypedErrorAndNoOutput`、`userOnlyContentStaysUnlabelled`、`encryptedPdfIsRejectedWithTypedErrorAndNoOutput`、`pdfDocumentChineseTwoPagePdfSurvivesInjectionAndOpensInPlatformRenderer`、`secondInjectionWithSameMetadataKeepsOneInfoAndBytePrefix`、`malformedXrefTableIsRejectedWithTypedErrorAndNoOutput`、`classicXrefTablePdfSurvivesInjectionAndOpensInPlatformRenderer`。
- `MistakePdfExporterAigcInstrumentedTest`（core:export）**7/7**，`OK (7 tests)`，`Time: 0.983`（批次 42 记的是 5 例；本 APK 是 7 例——用例已增长，**不得**拿 5 当当前值）。
- **边界**：本轨不读 PDF 字节、不跑 pypdf；`/AIGC` 的实际字节判据归 WS1/WS2；`PdfRenderer` 打开成功 ≠ 外部阅读器一致。

## 7. 崩溃签名九类（item 34）

`python -I build/ws4-batch43/crash_scan.py build/ws4-batch43/suites`（扫 34 份套件 logcat + 1 份 worker logcat）：

| 签名 | 次数 |
|---|---|
| `FATAL EXCEPTION` | 0 |
| `ANR in` | 0 |
| `NoSuchMethodError` | 0 |
| `NoClassDefFoundError` | 0 |
| `UnsatisfiedLinkError` | 0 |
| `SIGSEGV` | 0 |
| `CANNOT LINK` | 0 |
| `failed to map segment` | 0 |
| `death was not confirmed` | **2**（`suite-p10-logcat.txt` 1 + `worker-logcat.txt` 1，均 p10 设计负向） |

- 0 不等于健康：isolation 15 例零执行；34 份套件 logcat 里 **没有** `libquickjs` 装载行、也没有 `UnsatisfiedLinkError`，所以本表 0 只说明“没有 Java 层崩溃与链接失败记录”，**不能**当作原生库健康或 arm64 可用的证明。

## 8. worker 生命周期与强停恢复（item 35）

### 8.1 干净窗 worker 计数（`worker/worker-logcat.txt`，串行 a09 → p0405 → p10 后一次性 `logcat -d`）

| 指标 | 值（本文件精确 grep） |
|---|---|
| `Start proc …:visual_js` | 5 |
| `warm-up finished` | 5 |
| `warm-up failed` | **0** |
| 未配对 spawn（spawn − warm） | **0** |
| `killing :visual_js worker: terminate` | 3 |
| `death was not confirmed` | 1（p10 设计负向） |
| 套件后 `ps -A \| grep visual_js` | **空（无孤儿）** |

- 全量口径（`crash_scan.py` 扫 34 份套件 logcat + 本 worker 窗）：spawn 15 / warm-up 15 / failed 0 / kill 11 / `death was not confirmed` 2 —— 两数不可混用：15 是“全部套件累计”，5 是“worker 干净窗”。

### 8.2 强停恢复（`worker/force-stop-recovery.txt` + `worker/sqlite-check.txt`）

```text
pid_before=8881 → am force-stop → pid_after_force_stop=（空）→ visual_js_after_force_stop=（空）
relaunch: Status ok / LaunchState COLD / TotalTime 3312 ms / pid_after_relaunch=9003
resumed: topResumedActivity=…/MainActivity
run-as databases: smart-mistake-book.db 26,783,744 B（WAL 24,752 B）
sqlite3: quick_check=ok / user_version=73 / problem=0 / error_cause_observation=0
logcat: FATAL EXCEPTION / ANR in = 0
```

- 行数为 0 的原因：起跑前 `pm clear` + 覆盖面用例各用独立库名，主库未被种子写入（批次 42 记到 `problem=5`，其来源本轨未核）。**不得**把 0 行说成“数据丢失”：quick_check ok 与 user_version 73 才是本项判据。
- 边界：脚本级强停，不覆盖 LMK/真实低内存；PRD-08 人工走查仍未自动化。

## 9. 两红定性

### 9.1 p09：**时序抖动，非队列状态机回归**（三次实跑，两个方向都出现）

`aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight`：

| 运行 | 结果 | 重建延迟（kill→warm-up，logcat） |
|---|---|---|
| 套件内 | **红**：`got Rendered(…"sceneId":"scene-D"…)` | 19:31:48.394 → 19:31:49.106 = **712 ms** |
| 复跑 2（`p09-rep2`） | **绿** `OK (1 test)`，`Time: 4.265` | 19:50:50.585 → 19:50:52.631 = **2046 ms** |
| 复跑 3（`p09-rep3`） | **红** `Tests run: 1, Failures: 1` | 19:50:58.305 → 19:50:59.238 = **933 ms** |

机理（代码 + 时间线两证）：

- 用例帧间隔：`FIRST_FRAME_HEAD_START_MILLIS=400`、`FRAME_SPACING_MILLIS=300`，D 在 A 之后 **1300 ms** 提交（`VisualJsClientFrameQueueDeviceInstrumentedTest.kt:270-271,114-124`）。
- 队列只在“在飞 + 两待绘”时拒绝：`if (!inFlight) { inFlight = true; launchSession(frame); return }`、`if (pending.size >= MAX_PENDING_FRAMES) …BUSY`、`MAX_PENDING_FRAMES=2`（`VisualJsFrameQueue.kt:89-106,207`）。
- 重建 **< 1300 ms** 时，A/B2/C 在 D 到达前已排空 → D 走 `!inFlight` 直跑 → `Rendered`（红）；重建 **> 1300 ms** 时窗口成立 → `BUSY`（绿）。三次实测正好跨过该阈值（712/933 红，2046 绿）。
- 结论：**红因是测试构造窗口依赖设备重建速度**，不是 `submit` 语义回归（`VisualJsFrameQueue.submit` 的拒绝条件未变，见上；JVM 状态机用例本轨**未跑**，JVM 结论归门禁轨，不在此引用）。归因口径与批次 40/41/42“同方法同报文”一致；**修法（缩短帧间隔或锁存 in-flight）属 p09 用例落点（WS7 item 54），本轨不改**，也不得靠放宽断言变绿。
- 副作用口径：**p09 是 flaky 例**——任何“p09 已修/仍红”的单次结论都不成立，必须带运行次数。

### 9.2 selrestore `closureFourArchiveRestoresWithAddedTablesEmptyAndNoRowLoss`：测试装置缺陷（未修）

- 报文与批次 42 逐字同型：`SQLiteException: no such table: error_cause_observation`，`BackupSelectionRestoreInstrumentedTest.kt:758`（`rowCount`）。
- 根因（本轮读码复核 `:248-249`）：restore 后只 `StudyDatabaseFactory.open(context, databaseName).close()`——Room 懒开库，`.close()` 之前没有任何 DAO/查询，72→73 `onUpgrade` 不跑；随后 `rowCount` 用原始 SQLite 读表 → 表不存在。真实 v4 字节路径由 `v4pack` 3/3 兜住（本轨复跑仍 3/3）。
- 修复归属：计划 WS7 item 53（S9/S12），**本轨未改**。

## 10. 发现

| # | 发现 | 证据 | 严重度 | 处置 |
|---|---|---|---|---|
| W4-1 | p09 是 flaky：同 APK 三次跑出现 红/绿/红，成败随重建延迟跨 1300 ms 阈值 | §9.1；`suite-p09.log`、`p09-rep2.log`、`p09-rep3.log` | 中（口径） | WS7 item 54 修用例；**不得**写“p09 已修”或“p09 必红” |
| W4-2 | 32 套件不是全绿：2 红（p09 + selrestore）+ 15 跳（isolation） | §2 表；`suite-summary.json` | **高（口径）** | 任何“28/32 套件全绿”表述不成立 |
| W4-3 | isolation 15 例仍 0 执行：`verdict=UNGATED reason=no_shell_side_verdict`、`arm=ABSENT t4_production=compiled_in test_side_hooks=false` | `suite-isolation.log` 的 `-4` 栈、`suite-isolation-logcat.txt` | **高（口径）** | 归 WS3；本轨只报“未执行” |
| W4-4 | 5584 上批次 42 的 app/app.test sha 已被覆盖 | `manifest-5584.txt` vs 批次 42 `00-index.md §2.1` | 低 | 引用旧设备绿时必须注明绑的是 `35e10339…`/`0e906482…` |
| W4-5 | matrix 420.671 s（批次 42：201.65 s） | `suite-matrix.log` | 低 | 记为设备速度差异，不作性能回归 |
| W4-6 | exportaigc 现为 7 例（批次 42：5 例） | `suite-exportaigc.log` | 低 | 引数时用 7；KD-75 结论仍归 WS1 |
| W4-7 | 5562 reboot 后内存只剩 124 MB → instrumentation `System has crashed` | §1.3；`device-5562-incident.txt` | 中（基础设施） | 本轨弃用 5562；后续设备排班避开它，或先关多余包再跑 |

## 11. 未验证 / 边界（不得写成通过）

- **isolation 真实行为、因果、T4**：15 例零执行（WS3）。
- **selrestore closure-4 修复**：未修（WS7 item 53）。
- **KD-75 C2（模型解析文本进导出链）/ C4（字面升版）**：本轨只跑设备套件，不判 KD-75；`modelParsedTextForcesAigcLabelling` 绿只说明该注入用例在 `PdfAigcContentPolicy` 层通过。
- **KD-76 A6 / C4**：本轨未跑 20 轮。
- **arm64 / 16KB / 真机 / LMK**：未覆盖。
- **AI 图 `/AIGC` 字节回读（pypdf）**：未做（WS1/WS2）。
- **跨设备复现**：本轨只用 5584；5562 事故中断，5566 被 WS3 占用。

## 12. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §2 WS4（item 29–36）、§4（设备绿绑源快照 + APK sha；禁 connected）。
- 上一批：`docs/research/2026-11-04-batch-42-record.md` §2.2（三处张力）、§4（设备证据）、§9.2（回滚）、§9.3（isolation 两层）。
- 对照证据：`docs/research/evidence/2026-11-04-batch-42/ws10-device-full.md`（32 套件基线表、崩溃九类、worker/强停）、`device-track-batch42.md`。
- 代码：`core/domain/.../BackupSelection.kt:90,140,166`（回滚后 ALLOWED_MASKS）；`VisualJsFrameQueue.kt:89-106,207`（队列语义）；`VisualJsClientFrameQueueDeviceInstrumentedTest.kt:114-124,270-271`（帧间隔）；`FullMigrationMatrixInstrumentedTest.kt:39-51`；`StudyDatabase.kt:142`。
- 原始日志（gitignored）：`build/ws4-batch43/{build-apk.log,manifest-5584.txt,installed-sha-5584.txt,suite-summary.json,suites/,worker/,crash-signatures.json,p09-rep2*,p09-rep3*,device-5562-incident.txt}`。
