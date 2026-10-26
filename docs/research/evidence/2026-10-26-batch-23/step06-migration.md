# E-06 迁移抽验（设备迁移演练 + 迁移矩阵抽样）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | 五（迁移抽验） |
| gate-or-kd | 迁移设备门 |
| verdict | PASS（抽验范围；全矩阵见 §9） |
| executed-at | 2026-10-26T16:26:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/.../data-debug-androidTest.apk` 和 `core/database/.../database-debug-androidTest.apk` |
| apk-sha256 | data `5ef6ff9b…2e75`；database `feb1b360…df4e` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step06*.log`（125/109/38/220/11 行）、`step06*-logcat.txt`（335/188/106/1432/75 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

"备份/迁移核心组抽验"取三块：

1. `DeviceMigrationDrillInstrumentedTest` 全类（真实全量/子集迁移演练）；
2. `FullMigrationMatrixInstrumentedTest` 的两个**带数据**迁移方法（v53→54 计划指纹合并、投影归档 DDL/JSON）；
3. 破坏性 kill-stage 用例按设计需要显式 opt-in（§9 补充）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
# (a) 不带 opt-in：所有用例应 skip（安全默认）
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# (b) 显式 opt-in（只清测试 APK 数据目录）
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e smb_allow_data_reset true \
  -e class com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# (c) 迁移矩阵两个带数据方法
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class 'com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows,com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#projectionArchiveStoresSnapshotJsonAndSchemaDdl' \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

## 3. 结果摘要

| 运行 | tests | 通过 | 跳过 | 失败 | summary |
|---|---|---|---|---|---|
| (a) 无 opt-in | 6 | 0 | 6 | 0 | `OK (6 tests)` — **全部 assume 跳过** |
| (b) 带 opt-in | 6 | 5 | 1 | 0 | `OK (6 tests)` |
| (c) 矩阵抽样 | 2 | 2 | 0 | 0 | `OK (2 tests)` |

- (a) 的跳过原因（每例同一句）：`这会清空测试应用的数据目录（deleteAllData）；只在专用测试设备上传 -e smb_allow_data_reset true 再跑。`
- (b) 唯一跳过：`killProcessHandoffStagesArchiveThenDies`，原因 `真实杀进程阶段只在显式 -e smb_d1_kill_stage true 时执行（预期本次调用随进程死亡非零退出）`。
- 12 类日志签名命中：0（三次运行均 0）。

## 4. 关键输出（有界）

```text
# (a)
Tests run: 6, Failures: 0  ... OK (6 tests)   # 6 个 AssumptionViolatedException
# (b)
restoreFromHandoffAfterProcessDeath                          STATUS_CODE 0
corruptedSelectionStopsBeforeSwapLeavingNoHalfDatabase       STATUS_CODE 0
killProcessHandoffStagesArchiveThenDies                      STATUS_CODE -4 (assume)
fullMaskFifteenMigrationDrillRoundTripsEveryTableAndSettings STATUS_CODE 0
subsetMaskThirteenMigrationDrillKeepsSessionsAndSettings     STATUS_CODE 0
subsetMaskNineMigrationDrillDropsSessionsAndRebuildsBothSubjects STATUS_CODE 0
OK (6 tests)
# (c)
projectionArchiveStoresSnapshotJsonAndSchemaDdl              STATUS_CODE 0
reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows      STATUS_CODE 0
OK (2 tests)
```

## 5. 失败 / 跳过 / 异常

- (a) `OK (6 tests)` 不能当成"迁移已验"——R4 §8.5 点名的陷阱；本文件把 6 个跳过如实列出。
- (b) 的 1 个跳过是设计行为（kill-stage 需另外的参数），不是失败。
- 本步不含 `FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 的全版本矩阵（1→当前逐版本）；该长用例另起一轮，结果见 §9。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**PASS（抽验范围）**：迁移演练在 opt-in 后 5/6 真跑通过（1 个按设计跳过），两个带数据的迁移矩阵方法 2/2 通过。全矩阵长用例见 §9；若 §9 未完成，则全矩阵属 UNVERIFIED，不用抽验结果冒充。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step06-device-migration-drill.log`（125 行）、`step06b-device-migration-drill-optin.log`（109 行）、`step06c-migration-matrix-sample.log`（38 行）、对应 `*-logcat.txt`
- 生成命令：见 §2

## 9. 补充（追加式）

### 9.1 全量迁移矩阵（15 例，含 1→当前逐版本）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

结果（`build/device-track-batch23/step06d-full-migration-matrix.log`，220 行）：

```text
test=everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback  STATUS_CODE 0
test=kf5bClassificationCandidatesTableIsAddedLeavingExistingRowsIntactAndIdempotent STATUS_CODE 0
...
OK (15 tests)
INSTRUMENTATION_CODE: -1
AM_EXIT=0
```

- 15/15 通过；`everyExportedSchemaVersion...` 完成 1→当前逐版本迁移（本步耗时约 14 min，16:28–16:42）。
- 12 类日志签名 0 命中（`step06d-full-migration-matrix-logcat.txt`，1432 行）。
- 说明：本 logcat 是运行结束后回读，若环形缓冲已滚动则只覆盖尾部；判定以 am instrument summary 为准。

### 9.2 kill-stage 设计内进程崩溃（显式 opt-in）

```bash
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e smb_allow_data_reset true -e smb_d1_kill_stage true \
  -e class 'com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#killProcessHandoffStagesArchiveThenDies' \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

```text
INSTRUMENTATION_STATUS: test=killProcessHandoffStagesArchiveThenDies
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_RESULT: shortMsg=Process crashed.
INSTRUMENTATION_CODE: 0
AM_EXIT=0
```

这是**用例设计行为**（kill-stage 预期本次调用随进程死亡退出），不是失败；随后的 `restoreFromHandoffAfterProcessDeath` 已在 (b) 中真跑通过。进程由 native kill 结束，故 logcat 的 Java 崩溃签名为 0 与设计不矛盾。

### 9.3 本节判定

**PASS**：全量迁移矩阵 15/15 + 迁移演练（opt-in）5/6（1 个设计跳过）+ kill-stage 设计内崩溃 + 两个带数据方法抽样 2/2；迁移面在本批设备上已完整执行。

