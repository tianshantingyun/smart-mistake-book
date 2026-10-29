# E-01 备份套件 34 例复跑：`BackupRestoreInstrumentedTest`（PASS 34/34）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 1；判据 §2.1、§3.1） |
| gate-or-kd | KD-80 80-C1；批次 30 的 8 红修复后绿轮 |
| verdict | **PASS**：`OK (34 tests)`，0 失败/错误/跳过；批次 30 的 8 条红例逐条 `STATUS_CODE=0` |
| executed-at | 2026-10-29T06:31:05–06:31:31+0800（设备 UTC 22:31:05–22:31:31） |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（**非判据**；判据 = summary 行） |
| raw-log | `build/device-track-batch31/step01-backup.log`（485 行）/ `step01-backup-logcat.txt`（226 行） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.1：目标 `OK (34 tests)`；批次 30 的 8 条红例（7 条 `BackupSnapshotTrimException: 快照 schema=70，
裁剪器只支持 schema=69` + 1 条期望仍写 69）必须全部转绿；log 中不得出现
`BackupSnapshotTrimException` 与 `expected:<69>`；logcat 不得出现"裁剪器只支持 schema="。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step01-backup.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step01-backup-logcat.txt
```

- 工作目录：仓库根；退出码 `EXIT=0`；耗时 `Time: 24.853`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 34 | 期望 34（构建前复读 34） |
| failures | 0 | 期望 0 |
| errors | 0 | 期望 0 |
| skipped | 0（log 无 `skipped` 行） | 必须给原因；无跳过 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 34 | 每例一条通过状态 |
| `BackupSnapshotTrimException` | 0 | 期望 0 |
| `expected:<69>` | 0 | 期望 0 |
| logcat"裁剪器只支持 schema=" | 0 | 期望 0 |
| 9 类崩溃签名 | 0（见 E-11） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 24.853

OK (34 tests)

INSTRUMENTATION_CODE: -1        # 恒 -1，不作判据
```

批次 30 的 8 条红例（`grep` 逐名 + 取其后的 `INSTRUMENTATION_STATUS_CODE`）：

```text
fullRestoreLeavesDerivedSearchIndexEmptyAndMarksItForRebuild                occurrences=2 codes=['1','0']
backupClosureIncludesGenerationAttemptOutputs                              occurrences=2 codes=['1','0']
settingsSurviveBackupAndRestoreRoundTrip                                   occurrences=2 codes=['1','0']
duplicateConcurrentRestoreRequestsAreRejectedIdempotently                  occurrences=2 codes=['1','0']
schema68ArchiveRestoresAndMigratesToCurrentVersion                         occurrences=2 codes=['1','0']
backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed        occurrences=2 codes=['1','0']
backupDeleteAndRestoreRoundTripPreservesCatalog                            occurrences=2 codes=['1','0']
settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase occurrences=2 codes=['1','0']
```

（`codes=['1','0']` = 开始时 `STATUS_CODE: 1`、结束时 `STATUS_CODE: 0`；失败会是 `-2` 且带 `stack=` 行。
本 log `INSTRUMENTATION_STATUS: stack=` 计数 = 0。）

## 5. 失败 / 跳过 / 异常

无。无跳过、无假设、无崩溃、无 `ComparisonFailure`。

## 6. 脱敏与去噪

- 删除：无（未过滤任何行；本文件只摘 summary 与逐例状态）。
- 保留：`OK (34 tests)`、逐例 `STATUS_CODE` 计数；全量在 `raw-log`。
- 扫描：R4 §4.4 四条正则 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。34/34 且批次 30 的 8 条红例逐条通过；`SmbkSelectionPlan.SCHEMA_VERSION = 70` 已在新包生效
（E-12 §3 逐名比对 `RESULT=PASS`）。

**不得据此单条关闭 KD-80**：80-C4（备份/恢复后视觉四表引用关系）在 34 例中仍是零断言，
见 E-12 §5。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step01-backup.log` / `step01-backup-logcat.txt`
- 生成命令：见 §2
- 行数/大小：485 行 / 42389 B；logcat 226 行 / 25582 B
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
