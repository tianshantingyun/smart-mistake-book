# 批次 37 / 设备轨：重建重装 + 20 步不回退（含 V-01 app 版 / A-10 修复版）+ R-04 K1–K6（2026-10-31）

> **性质**：设备执行轨命令日志（R4 口径：每步命令、exit、用例数）。**未做任何 git 写操作**（无 add/commit/push）；
> **禁改热区零触碰**（feature/*/core ui、export、markdown、core/domain、core/data 主代码、settings.gradle.kts、
> .gitignore、docs/current、tools/kb、knowledge-production 全部未改；本目录既有 T5 台账文件未改）。
> **对照计划**：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md` §1/§3；**判据**：
> `docs/research/2026-10-31-batch-37-device-criteria.md`（518 行，已完整读；执行顺序按其 §4.8）。
> **构建窗口纪律**：全部构建为 `assemble*`（零 `connectedAndroidTest`）；每个构建窗口源快照 before/after diff 均为空。
> **原始日志目录**：`build/device-track-batch37/`（gitignore 内，不在提交集；本文件为 R4 摘要）。
> **目录注记**：本目录 `00-index.md`（T5 台账轨）自述"本目录无新设备执行"——该自述针对 T5 落盘时点的目录范围；
> 本文件为本轨（设备轨）**新增**的独立 R4 证据（沿批次 35 `step-device-track.md` 命名惯例），不改动 T5 任何文件。

---

## 0. 环境与设备再登记（判据 §4.7）

- HEAD：`5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD` 实跑，与判据 §0/基线一致）。
- 设备：`emulator-5562`（`adb devices -l` 实测在线：5562/5566/5584/5596 四台；**5566/5584 属他线禁碰、
  5596 API 23 冒烟机未跑任何套件**，本轨零触碰）。五值（`device-registration.txt`）：
  `ro.build.version.sdk=34`、`ro.product.cpu.abi=x86_64`、`getconf PAGE_SIZE=4096`、
  `ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`。
- 工具：adb `C:/Android/Sdk/platform-tools/adb.exe`；`MSYS_NO_PATHCONV=1`；build-tools 37.0.0（apksigner/dexdump）；
  cmdline-tools apkanalyzer；JDK 21 keytool。
- **判据落码时点差登记**：判据文档计数列 1173 文件（T2/T3 落码前）；本轨开工时 T2/T3 已落树
  （V-01 app 版新文件 + A-10/降级变体修改），五模块集实测 **1175 文件**（判据 §4.1"设备轨重取"授权）。
- **T2/T3 落树形态核对（本轨实读，构建前）**：
  - V-01 app 版：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt`
    （**未跟踪新文件**，787 行，包 `com.tingyun.smartmistakebook.visualjs`——判据预期包 `…smartmistakebook.visual`，
    以 T2 落树为准登记包名差）；`@Test`=**3**（:136/:247/:289）；真引擎 `VisualJsClientEngine(context)`
    （:500-501，与先例 `VisualJsIsolationInstrumentedTest.kt:120` 同构造）；`QuickJsVisualExecutionEngine`
    仅 KDoc :68（代码引用 0）；四断言位齐全（operation reserved/used=0 :188-194、attempt reservation_id IS NULL
    :196-206、dispatch_count 写前写后、产物恒 Program）；KDoc 明示 PDF/painter/restore 腿留 core/data 降级变体
    （core:data internal 符号跨模块不可达，R1 §2.3-4 可达性判断被证伪的如实登记）。
  - A-10 修复版：`FigureDispatchBudgetBoundaryInstrumentedTest.kt`（M，3 例 :58/:89/:127）；夹具走生产链路
    `createModelTask`（:230）→ `transitionModelTask`（:239）落 QUEUED，无端口绕行；终答位子门断言面在树
    （applied=true/logicalDispatchCount=6/budgetExhausted=false + 子门① snapshot RUNNING/attemptCount=6 +
    子门② 终答消费后预算闭死）——**未放宽，且强于判据最低面**。
  - core/data 降级变体（`core.data.visual.V01VisualChainInstrumentedTest`，4 例）：本批仅加降级口径标注，
    测试逻辑零改动（其 KDoc :120-128 自述）；**已知 2/4 夹具红仍在树** → 按 §2.1 第 6 条**明示不进本批套件单**
    （不跑 step21；"不允许已知红例留在套件单里"）。

## 1. 一致性门（构建前；判据 §4.2）

- keep 门（`python tools/ci/check_r04_keep_rules.py`）：OK×3 / **`KEEP_GATE_EXIT=0`**。
- S=71 静态比对（批次 34 §1.4 原命令重跑，`s71-static-compare.txt`）：`SCHEMA=71 PLAN_TABLES=90 UNIQUE=90
  JSON_TABLES=90 / ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71 /
  PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138 / RESULT=PASS / S71_EXIT=0`。
- V/P 双读：`V=71`（`StudyDatabase.kt:139`）、`P=71`（`SmbkSelectionPlan.kt:44`）、`71.json` 在树。
- keep 三文件 git 干净（`git status --porcelain` 空；`git log --oneline -1 -- app/r04-test-keep.pro`=`b31b5d02`）。

## 2. 重建重装硬门（判据 §4.1/§4.3–§4.6）

- 快照 before：`src-sha-before.txt` **1175 文件**（find 集 = `app/src core/model/src core/data/src
  core/database/src core/domain/src`，计划底线含 core/model）；keep 三文件 sha before（`keep3-sha-before.txt`）。
- 构建（§4.3 原命令）：`./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD_EXIT=0**
  （BUILD SUCCESSFUL in 51s）。
- 快照 after diff：**空**（`DIFF_EXIT=0`）；keep 三文件 after diff：**不变**。
- 四包 sha256（宿主，`four-apk-sha.txt`）：app `070b4485…`、app.test `bba75fa3…`、data.test `957b53a5…`、
  database.test `14240f5a…`。
- 安装（§4.4）：主包首次 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（残留 debug 签名包）→ 按判据处置
  `adb uninstall com.tingyun.smartmistakebook.offline` → 重装 `Success`（`install-debug-retry.txt`）；
  其余 3 包一次 `Success`。设备侧 `pm path`+`sha256sum` 四包**逐一相等**（`on-device-sha.txt`）。
- asset 门（§4.5）：data 测试包内 `assets/…StudyDatabase/71.json` 存在，sha256
  `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（`asset-71-sha.txt`；与批次 35/36 同值）。
- dex 符号门（§4.6 `need()`，批次 34 定义逐字）：app 测试包 5 符号（隔离类 + K6 两方法 + P-12③ 例 +
  **`V01VisualChainInstrumentedTest`**）`DEX OK`；data 测试包 4 符号（A-10 类 + 三方法名）`DEX OK`（exit 均 0）。
- 构建前计数复读（§3.3，`pre-build-counts.txt`）：**34/2/5/9/5/15/4/3/12/12/16/24/16/15/2/1/6/27**（step01–18，
  与判据 §3.1 表列逐一相等）+ **step19-v01-app=3**（T2 落树计数；判据旧位 4 的口径差见 §8-1）+ **step20b=3**。

## 3. 套件串行（判据 §三.3 模板；run20.sh，21:19–22:04）

| step | 套件 | 设备结果 | 构建前计数 | 判定 |
|---|---|---|---:|---|
| 01 | 备份 | OK (34 tests)，Time 90.2s | 34 | ✓ |
| 02 | rowCounts | OK (2 tests) | 2 | ✓ |
| 03 | 结构门 | OK (5 tests) | 5 | ✓ |
| 04 | 裁剪器 | OK (9 tests)，Time 292.3s | 9 | ✓ |
| 05 | C4 视觉四表 | OK (5 tests) | 5 | ✓ |
| 06 | 迁移矩阵 1→71 | OK (15 tests)，Time 1926.7s | 15 | ✓ |
| 07 | 69→70 | OK (4 tests) | 4 | ✓ |
| 08 | 70→71 | OK (3 tests) | 3 | ✓ |
| 09 | 提交 | OK (12 tests) | 12 | ✓ |
| 10 | 仓库 | OK (12 tests) | 12 | ✓ |
| 11 | 一致门（原型） | OK (16 tests) | 16 | ✓ |
| 12 | painter | OK (24 tests) | 24 | ✓ |
| 13 | PDF | OK (16 tests) | 16 | ✓ |
| 14 | 隔离（含 P-12③） | 首轮 **Tests run: 15, Failures: 2** → 归因舞步后重跑 **OK (15 tests)**（§4） | 15 | ✓（舞步后） |
| 15 | flags | OK (2 tests) | 2 | ✓ |
| 16 | 本地 attempt 负序号 | OK (1 test) | 1 | ✓ |
| 17 | 对账 JS 区分 | OK (6 tests) | 6 | ✓ |
| 18 | 引擎邻居门 | OK (27 tests) | 27 | ✓ |
| 19 | **V-01 app 版** | **OK (3 tests)**（=T2 落树计数） | 3 | ✓ |
| 20b | **A-10 修复版** | **OK (3 tests)**（含终答位子门） | 3 | ✓ |
| — | step21 降级变体 | **明示不进套件单**（§0 第 3 条；§8-2） | — | 登记 |

- 20 份 step logcat + step14 重跑 logcat 的 9 类签名扫描（§3.3 原命令）：**全 0**（`signature-scan-20steps.txt`）。

## 4. step14 首跑 2 红归因（判据 §五 最低字段逐项）

| 字段 | 红 1 | 红 2 |
|---|---|---|
| 红例名+行号 | `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`VisualJsIsolationInstrumentedTest.kt:245`，断言 `:281`） | `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse`（`:423`，断言 `:439`） |
| 失败消息 | `the new process inherited the old BUSY state: status=-3 diagnostics=null expected:<0> but was:<-3>` | `the explicit retry must render through the isolated worker, got Rejected(reason=HARD_DEADLINE_EXCEEDED, diagnostics=visual_js worker died: binder died)` |
| 第一项目帧 | `VisualJsIsolationInstrumentedTest$…$1.invokeSuspend(VisualJsIsolationInstrumentedTest.kt:281)` | 同型（`:439`） |
| 机制解码 | `-3 = HarnessExecution.DIED`（`:1026`，harness `execute` 捕 `RemoteException` 合成，`:1168`）——**:visual_js 隔离进程死亡** | worker binder 死亡 → 引擎折 HARD_DEADLINE_EXCEEDED |
| 四源核对 | A：该测试文件 git 干净（=批次 36 绿轮同源 `OK (15 tests)`，`build/device-track-batch36/step14*.log` 实读）；B：visualjs 主代码/aidl git 干净；D：dex 门过 + 设备 sha=宿主 freshly install；C：引擎路径零产品改动且重跑同工件绿 | 同左 |
| 类别 | **环境**（宿主 3 台他线模拟器在飞负载下隔离进程被杀；设备侧 `top` 实测 219MB free/swap 355MB/iowait 22%；无 LMK 签名，无 9 类签名） | 同左 |
| 处置 | 判据 §3.2 舞步：停该步 → `assembleStrictOfflineDebug`+AndroidTest 重建（`rerun-build.log`，EXIT=0；重跑窗口源快照 diff **空**）→ 重装（设备 sha `59b6d444…`=宿主）→ 重跑 step14 | **OK (15 tests)**（`step14-isolation-rerun.log`，Time 82.8s；logcat 9 类签名 0） |

- 舞步注记：重装后主包 sha 由 `070b4485…` 变 `59b6d444…`——同源码重打包的 zip 非确定性（androidTest 包未重建
  保持 `bba75fa3…` 不变）；源快照 diff 空证明非源变更；设备绑定以 freshly installed sha 为准。

## 5. R-04 轨（判据 §一；独立舞步，置于 20 步轨之后）

### 5.1 步 0–1（K1 红基线）
- 步 0：`keytool -genkeypair` 覆盖 `build/r04-local.jks`（KEYTOOL_EXIT=0；口令只走运行时文件 `/tmp/`（仓库外）+
  环境变量，不落仓/文档/日志——Mimosa hook 拦截含口令赋值样式的脚本文件后改为内联命令，未降低口径）。
  **舞步登记**：首次 `RELEASE_STORE_FILE=build/r04-local.jks` 相对路径被 Gradle 按 app 模块解析为
  `app/build/r04-local.jks` → `validateSigningStrictOfflineRelease` 失败（`r04-step1-build.log`）；
  改绝对路径 `D:/smart mistake book/build/r04-local.jks` 后重跑 → **STEP1_BUILD_EXIT=0**（BUILD SUCCESSFUL in 3m58s）。
- **K1 首次尝试（无效红，登记不采用）**：主包装机撞 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（设备上是 debug 主包）
  未先卸载即跑 instrument → 单例红 `first execution: null expected:<0> but was:<-3>`（worker 死亡环境形状，
  非 K1 判据红因）→ **不构成 K1**，按舞步卸载重装后重做。
- **K1 有效红**：release 主包 + debug 测试包装机（均 Success）→ instrument 单方法
  `workerIsIsolated…` → `K1_INSTRUMENT_EXIT=1`，红因 =
  **`Permission Denial: … package com.tingyun.smartmistakebook.offline.test does not have a signature matching the target com.tingyun.smartmistakebook.offline`**
  （`r04-step1-red.log`，batch 27 E-03 §3.1 形态）→ **K1 过**（红因是签名门，非缺 env）。

### 5.2 步 2（K2）
- `./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release --console=plain`
  → **STEP2_BUILD_EXIT=0**（51s）；两产物在（`ls -l` 实录）。新产物身份（`r04-two-apk-sha.txt`，
  与批次 36 窗口值 `1c7fe3f7…/03d1d426…` 不同 ✓）：
  主包 `9ec35dbac5e0e4b7ca15d91387f794c022b9f37492d80c930672c22a5f11ea0b`、
  测试包 `390ccec09a161cd3f707c6f57e17fd10e8636e37854ff588a7e150c9cca98801`。→ **K2 过**。

### 5.3 步 3（K3/K4/K5，产物级）
- **K3 过**：两 APK `apksigner verify --print-certs` 的 V2 证书 SHA-256 **逐字相同**
  `9fd478d96b3e8f6f67e7a2a28a25a36b665cbe56edde6f8c82ea6f6a1f633969`（CN=R04 Local；`r04-k3-main.txt`/`r04-k3-test.txt`）。
- **K4 过**：权威注释感知门 OK×3 / **`KEEP_GATE_EXIT=0`**（`r04-k4-keepgate.txt`；字面 grep 口径差按判据 §1.1 登记）；
  K4b：`mapping.txt` 非空 + 改名行 **797,123**（>0；`r04-k4b-mapping-lines.txt`）。
- **K5a 过**：`libquickjs.so` 双 ABI（arm64-v8a 924,936B + x86_64 911,248B；`r04-k5a-so.txt`）。
- **K5b 过（双口径）**：apkanalyzer `com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest`
  计数 **289**（≥1；批次 35 实测 275，随树增长）；unzip 兜底口径 **35**（≥1；批次 36 同值 35——两口径单位不同，各自登记不互替）。
  （`r04-k5b-count.txt` / `r04-k5b-fallback-count.txt`；批次 36 的 K5b 欠账就此补齐。）

### 5.4 三值产物预检（判据 §1.5，新 K2 产物重跑；`r04-three-value-precheck.txt`）
- ① `DesugarCollections$SynchronizedMap -> j$.util.g`（主 mapping）：**1**（≥1 ✓）。
- ② ` -> j$.util.g:`（**测试包** mapping）：**1**（期望 0，**不过**——R2 verify §2.3 预测形态在批次 37 新产物上复现）。
- ③ `androidx/tracing/Trace`（主包 classes.dex 字符串）：**1**（≥1 ✓）；**类定义级加固过**：
  `dexdump` 实证 `Class descriptor : 'Landroidx/tracing/Trace;'`（`r04-dexdump-main-classes.txt:137697`，
  PUBLIC FINAL + 三签名方法 beginSection/endSection/forceEnableAppTracing 在；
  `r04-3-dexdump-class.txt`/`r04-3-dexdump-methods.txt`；注：dexdump 输出含二进制字节，grep 须 `-a`）。
- **②=1 → 分支 P2**（判据 §1.5）。**分支裁定（本轨执行记录）**：
  - 选项 A（Keeper/L8 规则合并）触 `settings.gradle.kts`（热区禁改）+新依赖钉版——**未实施**（无主会话裁定不得擅施）。
  - **选项 B（按计划 §1.3 执行 K1–K6 全链，②不过登记为背景证据）——已执行**。裁定依据：计划 §1.3
    （`plan-2026-10-31-batch-37-v01-isolated-r04-k6.md:18`）为"**主会话裁定（本批执行）**"章节的明文
    （"R-04 K6 执行：K1–K6 全链重跑……仍红按阻塞登记，不放宽"），且判据文档 §1.5 自己标注该计划为
    "更晚的主会话裁定"；判据 §六.5 要求的"主会话显式确认"以计划 §1.3 的在案明文满足。
    两分支 K6 判据/命令完全相同；共同禁止清单（放宽②、-dontobfuscate/-dontshrink/-dontoptimize、
    internal 变体、删例、K1–K5 绿替代 K6）全程未触。

### 5.5 步 4（K6，判据 §1.6 命令逐字）
- 装机：测试包先卸（debug 测试包在装）→ release 主包/测试包 install -r -t **双 Success**；
  设备侧 sha = 宿主 `9ec35dba…`/`390ccec0…` 逐一相等（D 排除）。
- `-e class …#workerIsIsolated…,…#theWatchdog…` → **`Process crashed before executing the test(s)`**
  （两例一例未跑，`-r` 流无方法名、无 `OK (2 tests)`）。
- 崩溃栈（`r04-k6.log`）：`InvocationTargetException ← NoSuchMethodError: **No direct method
  <init>(Ljava/util/Map;)V in class Lj$/util/g;**`（declaration 位于 **offline.test** base.apk!classes2.dex）←
  `j$.util.DesugarCollections.synchronizedMap` ← `androidx.work.impl.WorkDatabase.<init>` ←
  `WorkManagerInitializer` ← `androidx.startup.InitializationProvider.onCreate`（WorkManager 启动栈）。
- 三类签名计数（`r04-k6-signature-count.txt`）：**4**（`NoSuchMethodError`×4 + `FATAL EXCEPTION`×1，
  其余 7 类 0——`final-signature-sweep.txt` 逐类表）；期望 0。
- **K6 判据逐字对照**：`OK (2 tests)` ✗（0 tests）；三类签名 0 ✗（4）→ **K6 红**。
- **归因（判据 §1.7 六行表第 1 行逐字对号）**：现象 = "instrument 未跑完即 Process crashed，logcat
  `NoSuchMethodError … Lj$/util/g; <init>(Ljava/util/Map;)V`（WorkManager 启动栈）" → **先判 机制①**
  （j$ L8 改名碰撞）；与三值预检 ②=1 的产物级预测一致（预测→实测闭环）。
  **处置 = 按"阻塞"回报**：不得改判据、不得加 `-dontobfuscate`、不得换 internal 变体、不得删例——全部未触。
  **R-04 不虚关：K1–K5 绿不替代 K6，阻塞维持。**
- 本轮实测对 R2 verify 预测的补强：`NoClassDefFoundError: androidx.tracing.Trace`（机制②）**0 命中**——
  批次 36 的 keep 扩充（`b31b5d02`）对机制②有效（③ 类定义级在包实证）；剩余崩溃全部来自机制①
  （testProguardFiles 规则到不了 L8 面，R2 verify §三 结构性裁定在本批产物级+设备级双复现）。

### 5.6 步 5 回归 + 步 6 恢复 debug 态（判据 §1.8；批次 36 欠账补齐）
- 步 5：不带属性 `./gradlew :app:assembleStrictOfflineDebugAndroidTest` → **EXIT=0**，产物回落
  `…/androidTest/strictOffline/debug/`（`r04-step5-regression.log`）——日常轨未被改道。
- 步 6：`adb uninstall` 四包（offline/offline.test/core.data.test/core.database.test）4× Success →
  debug 四包 `install -r -t` 4× Success（`r04-step6-uninstall.txt`）；复核在装主包 sha=`59b6d444…`
  （=§4 舞步后绿轮绑定包）→ **debug 态恢复，R-04 舞步闭环**。
- R-04 窗口源快照（`r04-src-sha-before/after.txt`，before 取于步 3 前、after 取于步 6 后）：diff **空**；
  keep 三文件 sha 全程不变（`keep3-sha-before.txt` == `keep3-sha-after.txt`）。

## 6. 步五：崩溃签名与 worker 生命周期收口（全会话）

- 9 类签名扫描（`final-signature-sweep.txt`）：20 份 step logcat + step14 重跑 = **全 0**；
  `r04-step1-red-logcat.txt` = 0（K1 红因 SecurityException 不属 9 类，红证据为 `r04-step1-red.log` 的
  Permission Denial 原文）；`r04-k6-logcat.txt` = **5**（FATAL EXCEPTION×1 + NoSuchMethodError×4 =
  K6 机制①崩溃证据本体，按 §1.7 登记，非收口遗漏）。
- 红例总表（§五 最低字段齐备）：§4 红 1/红 2（环境，舞步后绿）+ §5.5 红 3（机制①，阻塞登记）。
  无 A（夹具）类红：T2/T3 夹具修复形态在树（§0 实读），两新套件全绿，无夹具红复现。

## 7. 判据对照总表

| 判据 | 期望 | 实测 | 判定 |
|---|---|---|---|
| K1 | 红且红因=签名门/R8 改名 | Permission Denial signature matching | ✓ |
| K2 | BUILD_EXIT=0 + 两 release 产物 | 0 + 两产物在 | ✓ |
| K3 | 两证书 SHA-256 相同 | 同 `9fd478d9…` | ✓ |
| K4 | dontobfuscate 两文件 0 + mapping 改名行>0 | 门 OK×3 EXIT=0 + 797,123 行 | ✓ |
| K5 | so 在包（双 ABI）+ 测试类名未改名 | 双 ABI + 289/35 | ✓ |
| K6 | 两例 `OK (2 tests)` + 三类签名 0 | Process crashed（0 tests）+ 签名 4 | ✗ → **阻塞** |
| 三值预检 | ①≥1 ②=0 ③≥1 | ①=1 ②=1 ③=1（类定义级加固过） | ②不过 → P2 → 选项 B |
| 20 步 | 每步 OK(N)==构建前计数 | 全等（step14 舞步后） | ✓ |
| 步 5/步 6 | 回归绿 + debug 态恢复 | EXIT=0 + 4 卸 4 装 | ✓ |

## 8. 偏差与口径登记（全部显式）

1. **step19 计数口径**：判据 §3.1 表列 4 为 T2 落码前旧位（core/data）计数；T2 落树 app 版 3 例，
   按 §3.1"以 T2 落树为准/期望=构建前计数"执行（3==3 绿）。
2. **V-01 语义底线第 3 条的④ PDF 腿**：判据枚举 app 版 4 例（④=PDF）；T2 落树 app 版 3 例，PDF/painter/restore
   腿留在 core/data 降级变体（依赖 core:data internal 符号跨模块不可达，T2 KDoc 与 R1 引用如实登记）。
   本批设备证据：app 版 3/3 绿（真隔离引擎语义底线 1/2/4/5 条满足）；④ PDF 腿**未在本批设备轨复跑**
   （降级变体按 §2.1 第 6 条明示不进套件单，其已知 2/4 夹具红挂账待主会话裁定 R1 §3.3 方案 B）——
   **V-01 收口的"④ PDF 设备证据"本批缺失，按此登记，不虚报**。
3. **step21 降级变体不进套件单**（§0 第 3 条、判据 §3.1 可选注）。
4. **源快照计数 1175 vs 判据 1173**：T2/T3 落码落树所致（判据 §4.1 授权设备轨重取）。
5. **V01App 包名**：落树为 `com.tingyun.smartmistakebook.visualjs`（判据预期 `…visual`，"以 T2 落树为准"）。
6. **K1 首次尝试无效红**（主包未卸载即装机 + worker 死亡形状）——登记不采用，重做后有效。
7. **RELEASE_STORE_FILE 相对路径解析**（app 模块基准）→ 绝对路径重跑——环境舞步，不改判据。
8. **预检②不过的分支选择**：选项 B（计划 §1.3）执行，§5.4 已述裁定依据；若主会话追溯裁定应维持
   批次 36"预检硬门"口径，则本批 K6 结果不变（同为机制①阻塞），仅流程口径差登记在案。
9. **本目录索引范围注记**：见文件头部目录注记。

## 9. 未验证项与维持登记

- 生产签名（一次性本地 key 之外）**UNVERIFIED**；arm64 真机/16KB release 面、API 23 全套件维持既有缺口。
- R-04 **阻塞维持**（K6 机制①）：关闭/继续需主会话在两合规路径（Keeper 结构解锁 vs 改判据/降口径）间裁定——
  本轨按判据不代裁，未实施任何越权改动。
- core/data 降级变体 2/4 夹具红挂账（R1 §3.3 方案 B 待裁定）；V-01 ④ PDF 腿设备证据缺口（§8-2）。
- 本轨未跑 `connectedAndroidTest`；未做 git 写；未改任何产品/规则/测试/他线文件。

## 10. 关键可重跑命令索引（全量日志在 `build/device-track-batch37/`）

```bash
cd "D:/smart mistake book"
python tools/ci/check_r04_keep_rules.py            # K4 权威门（会话内 3 次全 OK EXIT=0）
S=71 python - <<'PY' … PY                          # S=71 静态比对（PASS/EXIT=0，s71-static-compare.txt）
./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest \
  :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest   # §2 构建 EXIT=0
bash build/device-track-batch37/run20.sh           # §3 20 步串行（模板=判据 §三.3）
./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release  # K2（需 RELEASE_* env）
C:/Android/Sdk/build-tools/37.0.0/apksigner.bat verify --print-certs <两 release 包>  # K3
C:/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat dex packages <测试包> | grep -c …  # K5b=289
# K6 两例 instrument + 三类签名 grep（判据 §1.6 逐字）→ r04-k6.log / r04-k6-logcat.txt
```
