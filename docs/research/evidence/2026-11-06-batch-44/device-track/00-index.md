# 设备验收轨证据索引（批次 44 / 2026-11-05 实跑）

> 本文件是导航与口径说明，**不是台账证据**。台账行只引用同目录下的
> `*.xml` / `*.log` / `*.device.json` / `*.png` / `*.json`。
> 所有设备证据绑定 `serial + boot_id + APK sha256`（本地构建 sha 与设备端
> `sha256sum` 逐包相等，见 §3/§7）。

## 1. 身份与源快照

| 项 | 值 | 落点 |
|---|---|---|
| 源快照 HEAD | `542b9bbac49081aca808c616a5dc538965d1767e` | `source-snapshot.txt` |
| 工作树 | `git status --porcelain` = **575** 行；`-uall` = **4294** 行；`git diff` sha256 = `79e4296930c377cbe2920d4cad285222854d1f7fbc68dcdd4abce5bcab1e6b68` | `source-snapshot.txt` |
| 构建 | `./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest :core:export:assembleDebugAndroidTest :feature:capture:assembleDebugAndroidTest :feature:library:assembleDebugAndroidTest :feature:tutor:assembleDebugAndroidTest --offline --continue` → `BUILD SUCCESSFUL in 41s` / `EXIT=0` | `build.log` |
| 设备 | `emulator-5584`，API 34，x86_64，`PAGE_SIZE=4096`，密度 420，`boot_id=831e5d06-7331-40d8-8cf4-406976b8a9c3`（跑前跑后一致） | `witness-device-apk-sha.log`、各 `*.device.json` |
| 安装 | 8 个 APK 全部 `adb install -r -t` → `Success`，`pm list instrumentation` 8 条全部注册 | `install.log` |
| 记录时间 | 2026-11-04 20:43–21:52 UTC（本机 04:43–05:52） | 各 `recorded_at` |

**源漂移声明**：本轨构建/安装的是上述工作树（575 条脏，非 HEAD 逐字节）。所有结论只绑
本目录 APK sha；不外推到 HEAD 或后续树。构建后 `find app/src/main core/*/src/main
feature/*/src/main -type f -newermt "2026-11-05 04:43:42" | wc -l` = **0**，即全部设备
轮次期间产物的上游源码未再变更（`source-drift-after-build.log`）。

## 2. APK 身份（本地 sha256）

| 包 | 文件 | sha256 |
|---|---|---|
| `com.tingyun.smartmistakebook.localfirst` | `app-localFirst-debug.apk` | `f534b0dd8aeabeeeec633bcbb9d24a62efe900eb75fdbcce199b979d58f7f7de` |
| `.localfirst.test` | `app-localFirst-debug-androidTest.apk`（本次重建，mtime 04:43:41） | `2a70fb019eed3aa78b52e8cea7a8a95ed1a5e91f33deed4fbabe4ed98816766e` |
| `.core.data.test` | `data-debug-androidTest.apk` | `611903803bbdcc83936e9ba567db733edbc1726f0cfc7e4fe160fffda27d9454` |
| `.core.database.test` | `database-debug-androidTest.apk` | `32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61` |
| `.core.export.test` | `export-debug-androidTest.apk` | `08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0` |
| `.feature.capture.test` | `capture-debug-androidTest.apk` | `4316d191974f2c39bcf8d419518892f07a610236afc249af497c6721f72b47b9` |
| `.feature.library.test` | `library-debug-androidTest.apk` | `330c97fccc7227c123f3c2f641870752ed34e1acfa74104434bba67f0c6bec65` |
| `.feature.tutor.test` | `tutor-debug-androidTest.apk` | `817a8b4122b5b13a4cf2414bde539df2412ac9d119f8c4135ff0156649253c2a` |

## 3. 运行矩阵（`am instrument -w -r`，recorder 逐例解析）

recorder：`tools/ci/am_instrument_recorder.py`。判定只认 JUnit 逐例状态；`skipped` 一律
不计通过。

| label | 包 | tests | evaluated | fail | skip | verdict | 证据 |
|---|---|---:|---:|---:|---:|---|---|
| `smoke-root` | localfirst.test | 10 | 10 | 0 | 0 | PASS | `app/smoke-root.*` |
| `app-suite`（42 类，去 TalkBack） | localfirst.test | 144 | 127 | 2 | 17 | FAIL | `app/app-suite.*` |
| `notif-denied`（`pm revoke` + `-e smb_notifications_denied=true`） | localfirst.test | 2 | 2 | 0 | 0 | PASS | `rerun/notif-denied.*` |
| `database`（69 类） | core.database.test | 319 | 319 | 0 | 0 | PASS | `modules/database.*` |
| `export`（7 类） | core.export.test | 30 | 30 | 0 | 0 | PASS | `modules/export.*` |
| `capture`（14 类，精确前缀） | feature.capture.test | 44 | 44 | 1 | 0 | FAIL | `modules/capture.*` |
| `library`（23 类） | feature.library.test | 109 | 109 | 1 | 0 | FAIL | `modules/library.*` |
| `tutor`（25 类，精确前缀） | feature.tutor.test | 122 | 122 | 2 | 0 | FAIL | `modules/tutor.*` |
| `data`（79 类，去 2 个 365MiB scale） | core.data.test | 94 | 87 | 1 | 6 | FAIL | `modules/data.*` |
| `data-backup-suite`（12 类，`smb_allow_data_reset=true`） | core.data.test | 85 | 84 | 0 | 1 | FAIL（recorder 口径 skipped=1；该 skip 为真实杀进程阶段，由下方两阶段覆盖） | `modules/data-backup-suite.*` |
| `data-migration-drill`（非杀进程 4 例） | core.data.test | 6 | 4 | 0 | 2 | 跳过不计通过 | `rerun/data-migration-drill.*` |
| `data-migration-drill-stageA`（真实杀进程，预期进程死亡） | core.data.test | 1 | — | — | — | 预期 `Process crashed` | `rerun/data-migration-drill-stageA.log` |
| `data-migration-drill-stageB`（新进程恢复对拍） | core.data.test | 1 | 1 | 0 | 0 | PASS `OK (1 test)` | `rerun/data-migration-drill-stageB.log` |

**迁移**：`database` 套件含 `FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`
（1..73 每个源版本建库→Room 迁移→断言无破坏回退）且该例无失败；套件 319/319 通过。
**备份**：`data-backup-suite` 84/84 评估例通过；唯一 skip 是真实杀进程阶段，已由
`stageA`（预期死亡）+ `stageB`（`OK (1 test)`）分两进程覆盖。

## 4. 红项逐条定性（不删断言、不放宽）

### 4.1 已复现红

| # | 用例 | 断言/错误 | 复现 | 定性 |
|---|---|---|---|---|
| R1 | `MistakeExportNotificationsInstrumentedTest#withoutPermissionNothingIsPostedAndNothingThrows` | 缺前置：`拒绝态用例必须在启动前由外部执行 pm revoke，并传 -e smb_notifications_denied=true` | 套件内 1 红；按前置重跑 2/2 PASS | **装置前置缺失**，非产品红 |
| R2 | `VisualJsClientFrameQueueDeviceInstrumentedTest#aFourthDistinctProgramIsRejectedBusy...` | 第 4 个 program 得到 `Rendered` 而非 `Rejected(BUSY)` | 1/1（本轨） | **已知红**（KDoc `VisualJsClientFrameQueueDeviceInstrumentedTest.kt:60-63` 自述批次 40–43 同报文；不得删断言变绿） |
| R3 | `CaptureModelTaskCardInstrumentedTest#incompleteQuestionOnlyOffersAppendWithoutDiscardingSavedPages` | 找不到文本 `已保存的页面不会丢失`（`:195`） | 1/1 | **已知红**（`docs/known-defects.md:1102`；生产文案为 `feature/capture/.../CaptureModelTaskCard.kt:116` 的 `已保存的页面将保留`，测试期望旧文案） |
| R4 | `LocalImageUiInstrumentedTest#aReadyButUndecodableImageShowsItsSummaryWithoutRegenerating` | 找不到 `过程图 · 受力示意 · 未就绪` | 1/1 | **已知红**（`docs/known-defects.md:1102` 的 feature:tutor 5 例之一） |
| R5 | `ReferenceEditorLayoutInstrumentedTest#movingBetweenWideAndNarrowKeepsTheSameEditorDraft` | `editor_source_reference` 不可见 | 1/1 | **已知红**（同 `docs/known-defects.md:1102`） |
| R6 | `LibraryScrollPerformanceInstrumentedTest#longCatalogRecordsNativeFrameStatsDuringTouchScrolling` | `Actual touch scrolling must move the first card offscreen` | 套件 1 红 + 单类复跑 1 红 = **2/2** | **未定性红**：可复现；无既往记录；不宣称产品回归（可能是 `input swipe` 与 Compose 滚动的设备输入问题），登记待裁定 |
| R7 | `BatchImportRepositoryInstrumentedTest`（整类） | 进程崩溃：`FATAL EXCEPTION: DefaultDispatcher-worker` → `android.database.SQLException: Error code: 21, message: connection is closed`，栈顶 `RoomBatchImportRepository$process$2$1` → `settlePendingSplits`（`RoomBatchImportRepository.kt:390,411`） | 套件 1/1 + 单类复跑 1/1（两次崩溃落在不同用例，说明是进程级竞态） | **测试隔离缺陷**：后台 `processingScope` 协程晚于 `@After database.close()` 触库；不是产品 DB 长连接路径结论。设备 crash buffer 原始栈见 `probes/crash-anr.log` |

### 4.2 跳过（BLOCKED / 不计通过）

- `VisualJsIsolationInstrumentedTest` 15 例：低负载门未传/INVALID 即 `Assume` 跳过。
  批次 43 §9.5 已裁定本机地板 > 绝对上限、不能判 PASS；本轨不重开。
- `VisualJsConnectStageCalibrationInstrumentedTest` 2 例：需 KD-76 轮次参数。
- `DeviceMigrationDrillInstrumentedTest` 2 例：真实杀进程阶段，已用两阶段命令覆盖。
- `TalkBackRuntimeInstrumentedTest`：见 §6。

## 5. 设备探针（非 instrument 证据）

| 项 | 命令/工具 | 结果 | 证据 |
|---|---|---|---|
| 语义树 × 4 根页 | `a11y_probe.py capture --root-tag root_* --nav nav_*` | 4/4 `STRUCTURAL=PASS`、`VIOLATIONS=[]`；对比度 `UNVERIFIED`（未给颜色） | `probes/base-{library,profile,review,tutor}.*` |
| 200% 字号 / 暗色 / 横屏 | 同上 `--font-scale 2.0` / `--night yes` / `--rotation 1` | 3/3 PASS（结构） | `probes/{fs2,dark,landscape}-library.*` |
| 键盘焦点 | `a11y_probe.py focus --presses 6` | PASS，6 个不同焦点标签 | `probes/focus-library.*` |
| 截图自一致（同状态同像素） | `screenshot_baseline.py compare` | 沉浸态两次采集 **MAE=0.0 / changed=0 / sha 相同**；非沉浸态 self-diff `changed_ratio=0.00216`（状态栏时钟），超 0.001 阈值 | `probes/home-immersive-{a,b}.png`、`home-immersive-selfdiff.json`、`home-selfdiff.json` |
| 截图跨 APK 基线 | 对 `2026-11-05-batch-44/c-domain/c-home-light.png` | `MAE=0.408`、`changed_ratio=0.00366`（超阈值 0.001）；跨 APK/跨 boot，只登记不判回归 | `probes/home-vs-prior-baseline.json` |
| 冷启动 | `am start -W` ×5（force-stop 后） | `LaunchState=COLD`；TotalTime **4596 / 2778 / 2824 / 2752 / 2751 ms**，p50 2752 ms；`TOTAL PSS=172851 KB` | `probes/perf-coldstart.log` |
| 崩溃/ANR | `logcat -b crash` + `dumpsys dropbox --print data_app_crash` | 仅 §4.1 R7 的 instrumentation 进程崩溃（21:01:41、21:02:18）；无 app ANR 记录 | `probes/crash-anr.log` |
| 发布包安装/启动 | `adb install -r -t` strictOffline release；`am start -W` | 安装 `Success`；`versionCode=2 / 0.2.0-offline`；冷启 `TotalTime=945 ms`；签名 = `CN=WS8 Ephemeral Local Only, OU=NotForRelease` | `probes/release-install.log` |
| TalkBack | 启用 `TalkBackService` → `dumpsys accessibility` → opt-in 跑例 | 服务绑定、`touchExplorationEnabled=true`；用例在 `hardware()` 第 1 次握手失败：`The opt-in emulator hardware driver did not acknowledge 1`；已恢复 `accessibility_enabled=0`、`enabled_accessibility_services=null` | `probes/talkback-enable.log`、`probes/talkback-runtime.*`、`probes/talkback-service.txt`、`probes/talkback-start.png`、`probes/talkback-hardware-request.json` |

## 6. TalkBack 边界（明确 BLOCKED，不冒充通过）

- 装置侧：`TalkBackRuntimeInstrumentedTest.kt:25` 需 `-e talkback_audit=true`；
  `:35-37` 断言服务与 `touchExplorationEnabled`；`:46-58` 与**宿主侧硬件输入驱动**做
  `hardware-input-request.json` / `hardware-input-response.txt` 文件握手。
- 本轨实跑：服务已真实绑定（`dumpsys accessibility` 见 `probes/talkback-enable.log`），
  用例停在“驱动未确认第 1 次输入”。仓内**没有该宿主驱动**（本轮 `grep -rln
  hardware-input-request` 只命中 `docs/research/2026-11-06-a11y-acceptance.md`）。
- 结论：TalkBack **可达/激活/顺序**未取得证据 → **BLOCKED**（缺宿主驱动与独占窗口），
  朗读语感按计划 §6 保持 **UNVERIFIED**。出口见 `docs/acceptance/runbook.md`。

## 7. Witness（强绑定）

`witness-device-apk-sha.log`：对 8 个包执行 `pm path` + 设备端 `sha256sum`，结果与 §2
本地 sha **逐字节相等**；`boot_id` 全程 `831e5d06-…` 未变。因此“设备上跑的就是 §2 的
字节”成立，不靠 recorder 入参自证。

## 8. 未跑 / 未验证（逐条写原因）

| 项 | 状态 | 原因 |
|---|---|---|
| 365MiB 备份 scale 两例（`BackupSelectionScale`、`BackupLargeDatabaseScale`） | 未跑 | 默认 365 MiB；批次 44 a-domain 同类 1800 s 超时。不以缩小 scale 冒充同项 |
| VisualJs 15 例隔离 witness | BLOCKED | 低负载门本机不可判（批次 43 §9.5） |
| ConnectStageCalibration 2 例 | BLOCKED | 需 KD-76 轮次参数 |
| TalkBack 全路径走查 | BLOCKED | 缺宿主硬件驱动（§6） |
| arm64 / 16KB 页大小设备安装 | BLOCKED | 宿主 x86_64，`ps16k_arm64` 不可起（批次 44 基线 §4.2） |
| 生产签名 / AAB / 封闭测试 | BLOCKED | 无生产密钥；现有 release 包为 `NotForRelease` 临时证书 |
| 真实 Provider 调用 | BLOCKED | 无密钥 |
| 2h soak / monkey 长稳 | 未跑 | 本轨只做短程冷启/内存/崩溃缓冲；长稳证据在 f-domain（绑定其 APK，不可合并） |
| 迁移逐版本数据行保持 | 未跑 | 只跑矩阵与 5 个定向迁移例，未在 1..72 每版实例化数据行（与 b-domain 同一残余） |

## 9. 复跑（最短路径）

```bash
# 构建 + 安装
./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest --offline
adb -s emulator-5584 install -r -t app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk
adb -s emulator-5584 install -r -t app/build/outputs/apk/androidTest/localFirst/debug/app-localFirst-debug-androidTest.apk

# 单域 instrument（example）
python -I tools/ci/am_instrument_recorder.py --serial emulator-5584 \
  --package com.tingyun.smartmistakebook.core.database.test \
  --class "$(tr '\n' ',' < build/acceptance/device-track/database-classes.txt)" \
  --apk-sha <app_sha> --test-apk-sha <test_sha> \
  --label database --out-dir <out>

# 探针
python -I tools/ci/a11y_probe.py --serial emulator-5584 capture \
  --label base-library --root-tag root_library --nav nav_library --out-dir <out>
python -I tools/ci/screenshot_baseline.py capture --serial emulator-5584 \
  --out <out>/home.png --apk-sha <app_sha>
```

设备状态恢复：`accessibility_enabled=0`、`enabled_accessibility_services=null`、
`policy_control=null`、字号 1.0、夜间 no、旋转 0（探针自带恢复；TalkBack 已在 §5 恢复；
快照 `probes/device-state-restored.log`）。发布包 `com.tingyun.smartmistakebook.offline`
在取证后已 `adb uninstall`（安装/启动日志保留在 `probes/release-install.log`）。
