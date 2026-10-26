# E-06 迁移抽验（演练 opt-in + 全量迁移矩阵）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 五（迁移抽验） |
| gate-or-kd | 迁移设备门 |
| verdict | PASS（演练 5/6 + 设计跳过 1；全矩阵 15/15） |
| executed-at | 2026-10-26T21:54:20+08:00（演练）/ 2026-10-26T22:22:00+08:00（矩阵结束） |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/.../data-debug-androidTest.apk` 和 `core/database/.../database-debug-androidTest.apk` |
| apk-sha256 | data `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281`；database `1196dd93abdd781f23fb92b42eee1c876283fcb0b852ff1fb5d98a0e3b33dba6` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch24/step05-migration-drill.log`（108 行）、`step05-migration-matrix.log`（219 行）、对应 `*-logcat.txt`（200 / 1,762 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

"备份/迁移核心组抽验"取两块：

1. `DeviceMigrationDrillInstrumentedTest` 全类，带 `-e smb_allow_data_reset true` 显式 opt-in
   （只清测试 APK 自己的数据目录）；要求除设计内的 kill-stage 外全部真跑通过；
2. `FullMigrationMatrixInstrumentedTest` 全类 15 例（含 1→当前逐版本迁移）；要求 0 失败 0 跳过。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# (a) 迁移演练（显式 opt-in，只清 com.tingyun.smartmistakebook.core.data.test 数据）
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e smb_allow_data_reset true \
  -e class com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step05-migration-drill-logcat.txt

# (b) 全量迁移矩阵
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step05-migration-matrix-logcat.txt
```

- 工作目录：仓库根
- 演练耗时：约 16 s（`Time: 14.555`）
- 矩阵耗时：约 1,434 s（`Time: 1,431.852`）

## 3. 结果摘要

| 运行 | tests | 通过 | 跳过 | 失败 | summary |
|---|---|---|---|---|---|
| (a) 演练（opt-in） | 6 | 5 | 1 | 0 | `OK (6 tests)` |
| (b) 迁移矩阵 | 15 | 15 | 0 | 0 | `OK (15 tests)` |

- (a) 唯一跳过：`killProcessHandoffStagesArchiveThenDies`，`INSTRUMENTATION_STATUS_CODE: -4`
  （`AssumptionViolatedException`），原因原文：
  `真实杀进程阶段只在显式 -e smb_d1_kill_stage true 时执行（预期本次调用随进程死亡非零退出）`。
  这是用例设计内的 opt-in，不是失败。
- (b) 15 例全部 `1 → 0`，失败/跳过码计数 0。
- 两次 logcat 的 7 类崩溃签名命中均为 0。

## 4. 关键输出（有界）

```text
# (a)
restoreFromHandoffAfterProcessDeath                             => 1, 0
corruptedSelectionStopsBeforeSwapLeavingNoHalfDatabase          => 1, 0
killProcessHandoffStagesArchiveThenDies                         => 1, -4 (assume)
fullMaskFifteenMigrationDrillRoundTripsEveryTableAndSettings    => 1, 0
subsetMaskThirteenMigrationDrillKeepsSessionsAndSettings        => 1, 0
subsetMaskNineMigrationDrillDropsSessionsAndRebuildsBothSubjects => 1, 0

Time: 14.555
OK (6 tests)
INSTRUMENTATION_CODE: -1
```

```text
# (b)
INSTRUMENTATION_STATUS: numtests=15
...
Time: 1,431.852

OK (15 tests)

INSTRUMENTATION_CODE: -1
```

矩阵 15 个方法：

```text
bindingAuditSampleTableIsCreatedEmptyWithoutTouchingStoredRows
chatEvidenceCorrectionTargetIsAddedToV67RowsWithoutRewritingThem
cleanRedrawTaskTableIsCreatedEmptyWithoutTouchingStoredRows
everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback
figureGenerationLedgerIsCreatedWithoutTouchingV68RowsAndIdempotent
kf30AcceptedConfidenceColumnIsAddedLeavingLegacyRowsNullAndIdempotent
kf32CreatesBindingChangeEventTableWithoutTouchingExistingRows
kf5bClassificationCandidatesTableIsAddedLeavingExistingRowsIntactAndIdempotent
libraryCatalogMemoryIndexIsCreatedWithoutTouchingStoredRows
libraryCatalogRebuildReadsRevisionTitleWithoutTouchingStoredRows
m1DropRemovesLegacyFixtureProjectionTablesAndKeepsRealRows
m5DropKeepsMemoryRowsAndRemovesConsumptionTable
mistakeExportRecordTableIsCreatedEmptyWithoutTouchingStoredRows
projectionArchiveStoresSnapshotJsonAndSchemaDdl
reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows
```

## 5. 失败 / 跳过 / 异常

- 无失败。
- 1 个设计内跳过（§3），已给出 assume 原因；`OK (6 tests)` 不掩盖它。
- 破坏性 kill-stage 本步未执行（需要额外 `-e smb_d1_kill_stage true`）；批次 23 E-06 §9.2 已
  记录其"设计内进程死亡"形状，本批不重复。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：全部逐例状态、assume 原因、summary、crash 签名零计数。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（抽验范围）**：迁移演练在显式 opt-in 后 5/6 真跑通过（1 个按设计跳过）；全量迁移矩阵
15/15 通过，含 1→当前逐版本迁移。

未覆盖：未执行 `smb_d1_kill_stage` 的显式崩溃用例（本批不是该项的关闭证据）；
未做更大规模数据库/低存储迁移；未在 arm64 上重复。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step05-migration-drill.log`（108 行）、`step05-migration-matrix.log`（219 行）、`step05-migration-{drill,matrix}-logcat.txt`（200 / 1,762 行）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
