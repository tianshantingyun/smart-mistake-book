# 批次 35 / 设备轨：重建重装 + 新用例 + 15 套件不回退 + R-04 K1–K6（2026-10-30）

> **性质**：设备执行轨命令日志（R4 口径：每步命令、exit、用例数）。**未做任何 git 写操作**（无 add/commit/push；文件移动用文件系统 + Write 工具完成，见 §7）。
> **对照计划**：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` §1 任务 15/§3；**判据**：`docs/research/2026-10-30-batch-35-device-criteria.md`（顺序按其 §四.7：登记→一致门→快照 before→构建→diff→安装 sha→asset 门→dex 门→套件串行→R-04→恢复）。
> **构建窗口纪律**：全部构建为 `assemble*`（未运行 `connectedAndroidTest`）；每窗口源快照 before/after diff 均为空。
> **原始日志目录**：`build/device-track-batch35/`（gitignore 内，不在提交集）。

---

## 0. 环境与设备再登记（判据 §4.7）

- HEAD：`902d7acce117c3e33905b1ec5ba9ea0d61a21f9c`（`git rev-parse HEAD`，构建/绿轮绑定值）；本轨运行期间他线 KB 线落两笔 **docs-only** 提交（`a3b8a534`、`4341f5ad`，仅 `docs/kb-*.md`，02:57）——不触任何 `src`，对 APK 字节与套件绑定零影响；收尾时 HEAD=`4341f5adcabe0ec45736d9e40cc11482ccf1fd1c`。开工工作树 751 项脏/未跟踪（多线在飞，快照含他线内容——按判据不阻塞）。
- 设备：`emulator-5562`（`adb devices -l` 实测在线；5584/5596 未触碰）。五值：
  `ro.build.version.sdk=34`、`ro.product.cpu.abi=x86_64`、`getconf PAGE_SIZE=4096`、
  `ro.build.fingerprint=google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`、release=14。
- 工具：adb `C:/Android/Sdk/platform-tools/adb.exe`；`MSYS_NO_PATHCONV=1`；build-tools 37.0.0 apksigner；cmdline-tools apkanalyzer。
- 构建前源计数复读（构建前实跑，`build/device-track-batch35/test-counts-buildpre.txt`）：
  `34/2/5/9/5/15/4/3/12/12/16/24/16/14/2 + 增量 1/6/27`（隔离 14 = 13 + P-11 wire 例；对账 6 = 3 + 负向 A/B + 终止态例；引擎 27 = 26 + M-04 例）。**P-12③ 设备例未落树**（隔离套件无 `explicitUserRetry` 解熔断例），本轨无该步可跑。

## 1. 一致性门（构建前）

- `V=71 P=71`（StudyDatabase.kt / SmbkSelectionPlan.kt sed 实跑）+ `71.json` 存在（`71_JSON_PRESENT`）。
- S=71 静态比对（批次 34 判据 §1.4 原命令重跑）：`SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90`、
  `ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True`、`PLAN_VERSION=71 JSON_VERSION=71`、
  `PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138`、
  **`RESULT=PASS` / `EXIT=0`**。

## 2. 重建重装硬门（判据 §四）

- 快照 before：`src-sha-before.txt` 1161 文件（`app/src core/model/src core/data/src core/database/src core/domain/src`，判据 §4.1 批次 35 口径）。
- 构建：`./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD_EXIT=0**（BUILD SUCCESSFUL in 43s，327 tasks）。
- 快照 after diff：**空**（`DIFF_EXIT=0`，非 SOURCE CHANGED）。
- 四包 sha256（宿主）：app `bb9485c6…`、app.test `f58cf083…`、data.test `5fa9717d…`、database.test `00dddb3d…`（`four-apk-sha.txt`）；`adb install -r -t` 4× `Success`；设备侧 `pm path` + `sha256sum` 四包**逐一相等**。
- asset 门：data 测试包内 `assets/…StudyDatabase/71.json` 存在，sha256 `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（与 T7 轨台账一致）。
- dex 符号门（`dex-gate.sh`，批次 34 §4.3 清单全保留 + 批次 35 追加）：三包 **`DEX OK` 全过（exit=0）**，含新增
  `localAttemptsUseNegativeOrdinalsDisjointFromTheRemoteLedgerSequence`、对账三新例、
  `aMeasurementFacilityFailureFoldsToEngineClosedWithAStableMarker`、`anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire`、
  `workerIsIsolated…`/`theWatchdog…`。
  - **归因记录 A（1 次）**：首次跑 `MISSING onlyLiveSnapshotsCanBeLinkedAndCasUseChangedRowCounts`——判据文档 §4.6 转抄笔误
    （源码实名 `…CasUsesChangedRowCounts`，`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:102`，该文件自批次 32 85f6b318 后未动、HEAD 版同名）。修正门清单为源码实名后全过；非旧包（D）。

## 3. 套件串行（判据 §三；两轮结果一致）

- 首轮（02:22–02:57）与重绑轮（03:08–03:33）**逐套件同绿**：

| step | 套件 | 设备结果 | 构建前源计数 | 判定 |
|---|---|---|---:|---|
| 01 | 备份 | OK (34 tests) | 34 | ✓ |
| 02 | rowCounts | OK (2 tests) | 2 | ✓ |
| 03 | 结构门 | OK (5 tests) | 5 | ✓ |
| 04 | 裁剪器 | OK (9 tests) | 9 | ✓ |
| 05 | C4 视觉四表 | OK (5 tests) | 5 | ✓ |
| 06 | 迁移矩阵 1→71 | OK (15 tests) | 15 | ✓ |
| 07 | 69→70 | OK (4 tests) | 4 | ✓ |
| 08 | 70→71 | OK (3 tests) | 3 | ✓ |
| 09 | 提交 | OK (12 tests) | 12 | ✓ |
| 10 | 仓库 | OK (12 tests) | 12 | ✓ |
| 11 | 一致门（原型） | OK (16 tests) | 16 | ✓ |
| 12 | painter | OK (24 tests) | 24 | ✓ |
| 13 | PDF | OK (16 tests) | 16 | ✓ |
| 14 | 隔离（P-11 wire 例在内） | OK (14 tests) | 14 | ✓（=构建前计数；≥13 底线满足） |
| 15 | flags | OK (2 tests) | 2 | ✓ |
| 16 | 本地 attempt 负序号 | OK (1 test) | 1 | ✓（`-r` 流含方法名 ×2） |
| 17 | 对账 JS 区分 | OK (6 tests) | 6 | ✓（负向 A/B/终止态例方法名均在 `-r` 流；判据预期 3/5，实现轨落 3 新例→以构建前计数 6 为准，无回退） |
| 18 | 引擎邻居门（M-04 强制复跑） | OK (27 tests) | 27 | ✓（M-04 例 `aMeasurementFacilityFailureFoldsToEngineClosedWithAStableMarker` 在流；折 ENGINE_CLOSED+稳定标记，与 JVM 半边分工） |

- **9 类崩溃签名扫描**（`FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed`）：两轮 **18/18 份 logcat 全 0**。
- **worker 生命周期收口**：step14 内 8 个生命周期例（watchdog 杀+新代无 busy、硬超时+500ms 内放行、死亡重建一次且新 worker 不忙、并发第二请求 BUSY、warm-up 获取拒绝且不被 watchdog 杀、确认放弃至多重建一次、connect/ready 超时不闩锁重试重绑）方法名全部在 `-r` 流中（各 ×2）且套件绿。
- **重绑轮缘由**：R-04 窗口内发生两处源变化（§6.1 本轨移动 1 文件 + §6.2 他线 dense 写 14 文件），恢复 debug 态后按"修复环后必须复跑强门"重跑 18 步并把绿绑定到恢复后四包（设备 sha `1593b25a…/f58cf083…/0b710c5f…/394faa86…`）。重跑窗口快照 diff **空**（`RERUN_DIFF_EXIT=0`）。
- **登记**：两轮共用同一组 `step*.log` 文件名（脚本 `tee` 覆盖），首轮原始日志被重绑轮覆盖；首轮数字以本会话记录为准（上表两轮一致），盘上日志为重绑轮（绑定轮）。

## 4. R-04 K1–K6（判据 §二；独立设备状态舞步）

**步 0 keystore**：`keytool -genkeypair`（RSA-2048/3650d，CN=R04 Local）生成 `build/r04-local.jks`；口令随机生成、仅经环境变量传递（`build/device-track-batch35/.r04env`，600 权限，gitignore 内；**R-04 收口后与 keystore 一并删除**，§4.7）。生产签名维持 UNVERIFIED。

### 4.1 K1 红基线 — PASS

- 构建 1：`./gradlew :app:assembleStrictOfflineRelease :app:assembleStrictOfflineDebugAndroidTest` → **BUILD_EXIT=1**，红因 `validateSigningStrictOfflineRelease: Keystore file 'D:\smart mistake book\app\build\r04-local.jks' not found`——判据步 0 的相对路径 `RELEASE_STORE_FILE=build/r04-local.jks` 被 `app/build.gradle.kts:135` `file(releaseStoreFile)` 按模块目录解析。属判据 §2.1"缺环境变量=基础设施问题，先修步 0"类；改绝对路径后构建 2 → **BUILD_EXIT=0**（env 就位，validateReleaseSigning 过）。
- 安装舞步：debug 签名与 r04local 不同 → 先 `adb uninstall`（offline + offline.test，2× Success）再装（release 主包 + debug 测试包，2× Success）。
- instrument 单例（`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`）→ **exit=1，红因=签名门**：
  `INSTRUMENTATION_STATUS: Error=Permission Denial … package com.tingyun.smartmistakebook.offline.test does not have a signature matching the target com.tingyun.smartmistakebook.offline`（`r04-step1-red.log`，批次 27 E-03 §3.1 形态）；logcat 三类 R8 崩溃签名计数 **0**。非"instrument 绿了"，非"缺环境变量"。

### 4.2 K2 release 测试轨构建 — PASS（首跑红 → 源集移动修复 → 同窗重建）

- 首跑 `./gradlew :app:assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release` → **BUILD_EXIT=1**，
  红因 `:app:compileStrictOfflineReleaseAndroidTestKotlin` 编译错：`DebugModelFailureCaptureInstrumentedTest.kt` 15 处 `Unresolved reference 'DebugModelFailureCapture'/'TestCaptureModelFailuresReceiver'`。
- **牵动面枚举**（全库 grep）：androidTest 源集仅此 1 文件、仅引这 2 个 debug 独有类（均在 `app/src/debug/kotlin/com/tingyun/smartmistakebook/`）；`BindingAuditActivity`/`FormatTestActivity`/`KnowledgeEvidenceAuditActivity`/`TestSeedModelConfigReceiver` 无 androidTest 引用。该测试的被测对象本身即 debug 专属设施（类名与被测类同在 debug 源集），在 release 主包上无运行面。
- **处置**（整文件纳入并注明，§7）：文件**逐字节不变**移动至 `app/src/androidTestDebug/kotlin/com/tingyun/smartmistakebook/`（AGP 标准变体源集；sha256 前后同为 `f55fd14f549d732fc7ca2c2e0f6fb5f6f1315b6441f4503d6738fd3418abcff9`）。断言零改动、debug 轨 APK 内容不变（§5 实测 app debug test APK sha 前后同为 `f58cf083…`）。消灭的失败：release androidTest 变体不可编译（K2 红）。
- 他线并发（§6.2）：重建前快照 delta 显示他线在 R-04 窗口内写 `core/data/src/` 14 文件（含生产 Kotlin `DenseRecallAssembly.kt`）→ 60 秒稳定性探测 `DENSE_SETTLED` → **release 主包+测试包同窗重建**（`assembleStrictOfflineRelease` + `assembleStrictOfflineReleaseAndroidTest -Psmbk.testBuildType=release`）→ **BUILD_EXIT=0**（3m12s）；快照 after diff **空**。
- 产物：`app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk`（155,749,587B）与
  `app/build/outputs/apk/androidTest/strictOffline/release/app-strictOffline-release-androidTest.apk`（1,118,085B）均在（`androidTest/strictOffline/release/` 路径形态符合）。

### 4.3 K3 证书一致 — PASS

`apksigner verify --print-certs`（JAVA_HOME 需 Windows 形式——POSIX 形式使 .bat 报 invalid directory，属环境修正非判据改动）两包 exit=0，**证书 SHA-256 逐字相同**：
`4c5cf033b2faa904878e20ff8c60da950603946cb3f8383cf725f4f73fce8038`（`r04-k3-main.txt` / `r04-k3-test.txt`）。未用 apksigner 重签（AGP 原生继承实测成立）。

### 4.4 K4 关闭混淆 0 命中 + mapping 真实改名 — PASS（脚本权威）

- 已落地门脚本 `python tools/ci/check_r04_keep_rules.py` → 两文件各 `0 hits`、**exit=0**（脚本注释排除口径：R8 不执行注释）。
- 判据原样 grep `grep -cE "dontobfuscate|dontshrink|dontoptimize" app/proguard-rules.pro app/r04-test-keep.pro` → `0` 与 `1`：唯一命中 = `r04-test-keep.pro:17` **注释**（门自身自述文本，R8 不执行）；`-dontshrink`/`-dontoptimize` 两文件均 0。以已落地脚本为准（判据 §二 前言"脚本落地后以脚本为准"），差异如实登记。
- mapping：`app/build/outputs/mapping/strictOfflineRelease/mapping.txt` 非空，`grep -c " -> "` = **796,290**（真实改名行 >0）。

### 4.5 K5 so 双 ABI + 测试类未改名 — PASS

- `unzip -l` release 主包：`lib/arm64-v8a/libquickjs.so`（924,936B）+ `lib/x86_64/libquickjs.so`（911,248B）双 ABI 在包。
- 测试类名在 test dex 未改名：apkanalyzer `dex packages` 计 **275** ≥1（兜底 unzip+grep -a = 35）。keep 规则足量。

### 4.6 K6 设备两例往返 — **RED（D 类形态，按判据阻塞回报）**

- fresh 同窗双包安装（卸 offline.test → 装两包，3× Success；主包同签名覆盖成功）。
- instrument 两例（`workerIsIsolated…` + `theWatchdogKills…`）→ **无 `OK (2 tests)`，`INSTRUMENTATION_RESULT: shortMsg=Process crashed`**（runner 启动即崩，两例未执行）。
- 崩溃签名（logcat 计数 **6**）：
  `NoClassDefFoundError: Failed resolution of: Landroidx/tracing/Trace;`（`ClassNotFoundException: androidx.tracing.Trace`——主包 R8 收缩掉 test 运行时所需类）
  与 `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（test 侧 desugared-library 与主包错位）。
- **处置（判据 K6 失败处置逐字遵守）**：不删例、不换 internal 变体、不引入 `-dontobfuscate`、不改判据——**按"阻塞"回报**：本仓组合下"test dex 套用主包同一混淆"路线在现有 keep 规则集上不成立。扩 keep 规则属实现轨改动（`app/r04-test-keep.pro` 本批已是他线 M，正被实现轨迭代）；本轨不越权改规则。

### 4.7 步 5 回归 + 步 6 恢复 debug 态 — PASS

- 步 5：无属性 `./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest` → **BUILD_EXIT=0**，app 测试产物回落 `androidTest/strictOffline/debug/`（`testBuildType` 回落 debug）。
- 步 6：`adb uninstall` offline + offline.test（2× Success）→ 重装 debug 四包（4× Success）；设备侧 sha 与宿主逐一相等（`1593b25a…`/`f58cf083…`/`0b710c5f…`/`394faa86…`）；随后重跑 §3 重绑轮。debug 态恢复完成。

## 5. 四包 sha 台账（三轮）

| 包 | 重建轮（§2，开工树） | 恢复轮（§4.7，含 §6 两处源变化） |
|---|---|---|
| app 主包 | `bb9485c6b287f9e3…` | `1593b25ae33d10dc…` |
| app androidTest | `f58cf083f35cd538…` | `f58cf083f35cd538…`（**不变**——源集移动不改变 debug 测试 APK 字节） |
| data androidTest | `5fa9717debe75128…` | `0b710c5f808d22a8…`（他线 dense 内容） |
| database androidTest | `00dddb3de28dc5f9…` | `394faa8673e790ef…` |

R-04 两 release 包：`r04-two-apk-sha.txt`（同窗重建对）。

## 6. 窗口内源变化登记（本轨不改热区；两处均非本轨内容改动）

1. **本轨唯一树改动**（§7 详）：`DebugModelFailureCaptureInstrumentedTest.kt` 逐字节移动 `app/src/androidTest/…` → `app/src/androidTestDebug/…`。
2. **他线并发写**（02:50:15 一批，14 文件）：`core/data/src/androidTest/assets/dense/encoder-parity-{cases.tsv,vectors.f32,json}`、`core/data/src/main/kotlin/…/dense/DenseRecallAssembly.kt`（生产 Kotlin）、`core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec{,.json}`、`core/data/src/main/resources/knowledge/moe-2025-*.json`（4）、`core/data/src/test/resources/dense/*`（5）——全部在"core/data 他线 M 与 knowledge/dense 面"在飞热区。处置：稳定性探测→同窗重建→重绑轮；该面验证归他线自有轨。

## 7. 本轨树改动登记（整文件纳入注明；无 git 写操作）

- `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/DebugModelFailureCaptureInstrumentedTest.kt`：工作树显示 ` D`（原路径删除）。
- `app/src/androidTestDebug/kotlin/com/tingyun/smartmistakebook/DebugModelFailureCaptureInstrumentedTest.kt`：untracked 新路径，内容与原文件**逐字节一致**（sha256 `f55fd14f549d732fc7ca2c2e0f6fb5f6f1315b6441f4503d6738fd3418abcff9` 前后同；162 行 LF-only；Write 工具落盘+哈希比对后删原路径）。**断言/逻辑零改动**；移动 = 全部改动。消灭的失败：R-04 K2 release androidTest 变体编译红。副作用面：debug androidTest 变体从同一类的新旧两个源目录二选一编译，产物字节不变（§5 实测同 sha）；该例不在 18 套件清单内，不涉任何设备绿轮断言。
- 证据产物 `build/device-track-batch35/*`、keystore `build/r04-local.jks`、口令文件 `.r04env`：gitignore 内，不入提交集；口令文件与 keystore 按"口令只走环境变量"纪律于收口删除（重建命令=判据 §2.1，一次性 key 可再生成）。

## 8. 结论与未验证项

**绿**：重建重装硬门全过；18 套件两轮全绿且计数=构建前源计数（15 套件不回退成立，含 P-11 wire 例 14、M-04 引擎例 27、对账 6、本地序号 1）；9 类崩溃签名两轮 18/18 份全 0；worker 生命周期 8 例全绿；R-04 K1/K2/K3/K4/K5 PASS。
**红/阻塞**：R-04 **K6 红**（`androidx.tracing.Trace` NoClassDefFoundError + `j$` desugar 错位，D 类形态）——release androidTest 设备往返在本仓现 keep 规则组合下不成立，按判据以"阻塞"移交实现轨/主会话（扩 `r04-test-keep.pro` 或改路线，均非本轨权限）；R-04 不因此判"通过"也不放宽。
**未验证/边界**：
1. K6 两例未在 release 轨上执行过（启动即崩）——R-04 收口状态=K1–K5 绿 + K6 阻塞。
2. 生产签名 UNVERIFIED（一次性本地 key）；arm64 真机面未测（x86_64 模拟器）。
3. P-12③ 设备例未落树未跑（隔离套件 14 = 13 + P-11）；Q-01 设备面按判据不存在（JVM 归主会话）。
4. `PROGRAM_FUSED` 基线断言（隔离套件）本批在 `OK (14 tests)` 内绿，未单独核对其行号现值。
5. 他线 dense 14 文件内容变化的功能正确性未验证（归 dense 轨）；本轨仅保证构建窗口源稳定与套件绿绑定当前包 sha。
6. feature/*、core/ui 他线在飞内容不在判据 find 集（判据口径），其正确性归各线。

## 9. 来源

- 判据/顺序/归因：`docs/research/2026-10-30-batch-35-device-criteria.md` §0–§五
- 计划：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md:23-26,35-45,47-57`
- R-04 配置：`app/build.gradle.kts:12,65,87-98,124-158`；`app/r04-test-keep.pro`；门脚本 `tools/ci/check_r04_keep_rules.py`
- 本轮实跑命令与原始输出：`build/device-track-batch35/`（build-four.log、run-steps-rerun-console.log、r04-step1-red.log、r04-k6.log、r04-k6-logcat.txt、r04-k3-*.txt、dex-gate-result.txt、src-sha-*.txt、test-counts-buildpre.txt、four-apk-sha*.txt 等）
