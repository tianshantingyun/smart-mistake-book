# G0 设备复跑命令（批次 50；迁移 1..75 / 备份 34 / trimmer / rowCounts / drill）

> 状态：**命令已准备，未执行**。本文件只给可复跑命令与判据；没有任何设备结果被写成本轮通过。
> 设备事实（2026-11-08 本会话 `adb` 实测）：`emulator-5590` online，API 34，`PAGE_SIZE=4096`，
> ABI `x86_64`。设备池只有这一台；与 3E/MVA/AUD-08 串行使用。

## 0. 硬约束

- **禁止** `connectedLocalFirstDebugAndroidTest` / 任何 `connected*AndroidTest` 任务；只用
  `assemble*AndroidTest` + `adb install` + `adb shell am instrument`。
- Gradle 只在串行阶段跑；本组不与其他轨并行。
- `adb` 的进程退出码不是判据；判据是 `am instrument` 输出里的 `OK (N tests)` /
  `FAILURES!!!`，以及每个用例的 `STATUS_CODE`。跳过（assumption）必须逐条列出，不能算通过。
- 每组只认「本轮 APK sha + 本轮源快照 + 本轮设备」；旧日志/旧 PASS 不得复用。
- 证据文件命名：`g0/device-<group>.log`、`g0/device-<group>.device.json`（若用
  `tools/ci/am_instrument_recorder.py`）；原始 log 不入仓时在 `00-index.md` 写路径。

## 1. 构建与安装（串行窗口内）

```bash
# 仓库根；Windows Git Bash
A=C:/Android/Sdk/platform-tools/adb.exe
S=emulator-5590

# 1) 只 assemble，不 connected
./gradlew :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest --offline

# 2) 安装两个测试 APK（覆盖安装）
MSYS_NO_PATHCONV=1 $A -s $S install -r core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
MSYS_NO_PATHCONV=1 $A -s $S install -r core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk

# 3) 绑定证据：APK sha + 设备 + 源快照
sha256sum core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk \
          core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
git rev-parse HEAD; git status --porcelain | wc -l
$A -s $S shell getprop ro.build.version.sdk
$A -s $S shell getconf PAGE_SIZE
```

测试包名（库模块自测）：

- `com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner`
- `com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner`

## 2. 迁移 1..75（矩阵）

`FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`
在 `FullMigrationMatrixInstrumentedTest.kt:145` 用 `for (version in 1..STUDY_DATABASE_VERSION)`，
`StudyDatabase.kt:142` 的 `STUDY_DATABASE_VERSION = 75`，即逐版本 1→75。

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class 'com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback' \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-migration-1-75.log
# 判据：OK (1 test)；失败会打印具体 version + 破坏性回退断言
```

同类的两个「带数据」迁移方法（与本组同批跑，避免空库矩阵掩盖行丢失）：

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class 'com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#reviewPlanFingerprintMergeKeepsExistingPlanAndQueueRows,com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#projectionArchiveStoresSnapshotJsonAndSchemaDdl,com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#legacyAndCompleteV73LayoutsReachCurrentWithoutReset,com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest#partialV73LayoutIsRejectedWithoutAdvancingVersion' \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-migration-data.log
```

## 3. 备份 34（`BackupRestoreInstrumentedTest`，34 个 @Test）

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-backup-34.log
# 判据：OK (34 tests)；任何 skipped 必须列出并解释
```

## 4. trimmer（`BackupSnapshotTrimmerInstrumentedTest`，10 个 @Test）

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-trimmer.log
```

## 5. rowCounts（`BackupRowCountsCoverageInstrumentedTest`，2 个 @Test）

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRowCountsCoverageInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-rowcounts.log
# 静态前置已升到 75.json / 99 表（本批 G0 改动）；未升的旧 APK 会以 73≠75 / 92≠99 直红
```

## 6. drill（`DeviceMigrationDrillInstrumentedTest`，6 个 @Test）

```bash
# 6a) 显式 opt-in（只清测试 APK 数据目录）；无 opt-in 时 6 例全 assume-skip，不算通过
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e smb_allow_data_reset true \
  -e class com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-drill-optin.log
# 期望：OK (6 tests)，5 例 STATUS_CODE 0，killProcessHandoffStagesArchiveThenDies 按设计
# assume-skip（需要 -e smb_d1_kill_stage true；该例预期随进程死亡非零退出，单独一轮取证）

# 6b) kill-stage 单独一轮（预期非零退出，不是失败）
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e smb_allow_data_reset true -e smb_d1_kill_stage true \
  -e class 'com.tingyun.smartmistakebook.core.data.backup.DeviceMigrationDrillInstrumentedTest#killProcessHandoffStagesArchiveThenDies' \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-drill-killstage.log
```

## 7. 同批建议（备份语义完整面；不属于本 ask 的五个必跑组）

```bash
MSYS_NO_PATHCONV=1 $A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSelectionRestoreInstrumentedTest,com.tingyun.smartmistakebook.core.data.backup.BackupV4PackRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee docs/research/evidence/2026-11-09-batch-50/g0/device-selection-v4-restore.log
```

## 8. 本批为让命令可跑而修的静态前置

| 文件 | 改动 | 原因 |
|---|---|---|
| `BackupRowCountsCoverageInstrumentedTest.kt` | `73.json`→`75.json`；`92`→`99`（表数、键数、文案） | 原断言 73≠75 / 92≠99，静态必红 |
| `BackupSelectionRestoreInstrumentedTest.kt` | 恢复后版本断言 `73`→`SmbkSelectionPlan.SCHEMA_VERSION` | v4 包恢复后迁移到当前 75，不再停在 73 |
| `BackupV4PackRestoreInstrumentedTest.kt` | `readDatabaseVersion()/user_version` 断言 73→当前 schema | 同上 |

以上均为测试断言随 KSP `75.json` 同步，**未放宽任何断言**（等值关系不变，只换版本/计数目标）。

## 9. 未执行声明

本文件所有命令在本轮 **NOT RUN**。原因：本 ask 只要求「准备设备复跑命令」；Gradle 串行窗口与
设备档期由主会话统一调度。任何引用本文件的结果都必须先有对应的 `g0/device-*.log` 与
APK/源快照绑定。
