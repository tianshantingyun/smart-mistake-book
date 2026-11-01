# E-06 备份套件 `BackupRestoreInstrumentedTest`（PASS 34/34）

| 字段 | 值 |
|---|---|
| evidence-id | E-06 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §4.1–§4.5） |
| gate-or-kd | KD-80 80-C1；schema 68→71 版本接缝 |
| verdict | PASS |
| executed-at | 2026-10-29T11:26:19–11:26:49+08:00（`Time: 29.74`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step01-backup.log`（485 行）/ `step01-backup-logcat.txt`（238 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- 目标 `OK (34 tests)`（构建前复读 34），0 失败/错误/跳过。
- 批次 30 的 8 条红例逐名必须转绿（`STATUS_CODE: 0`）。
- schema68 快照恢复后读到的库版本必须是 `SmbkSelectionPlan.SCHEMA_VERSION`（现 71），不是字面量 70/69；回执里的版本仍是包自己的 68。
- log 不得出现 `BackupSnapshotTrimException`、`expected:<70>`、`expected:<71>`；logcat 不得出现“裁剪器只支持 schema=”。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step01-backup.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step01-backup-logcat.txt
```

- `EXIT=0`；`Time: 29.74`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 34 | 期望 34 |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 34 | 每例一条 |
| `INSTRUMENTATION_STATUS_CODE: -2` | 0 | 0 |
| `BackupSnapshotTrimException` | 0 | 0 |
| `expected:<70>` / `expected:<71>` | 0 / 0 | 0（期望读常量） |
| logcat “裁剪器只支持 schema=” | 0 | 0 |
| logcat 9 类签名 | 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
Time: 29.74
OK (34 tests)
```

批次 30 的 8 条红例（每条出现 2 次：开始 `STATUS_CODE: 1` + 结束 `STATUS_CODE: 0`）：

```text
fullRestoreLeavesDerivedSearchIndexEmptyAndMarksItForRebuild
backupClosureIncludesGenerationAttemptOutputs
settingsSurviveBackupAndRestoreRoundTrip
duplicateConcurrentRestoreRequestsAreRejectedIdempotently
schema68ArchiveRestoresAndMigratesToCurrentVersion            # BackupRestoreInstrumentedTest.kt:488
backupCreatedWhileACaptureIsRegisteredKeepsTheArchiveReferenceClosed
backupDeleteAndRestoreRoundTripPreservesCatalog
settingsWriteFailureIsReportedAsPartialWithoutRollingBackTheRestoredDatabase
```

## 5. 失败 / 跳过 / 异常

无。`INSTRUMENTATION_STATUS: stack=` 计数 0。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、逐用例状态码、专项 grep 计数。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：34/34、8 条旧红全绿、schema 68→71 接缝成立。
**边界**：34 例仍无 `tutor_visual` 行/引用断言，**KD-80 80-C4 仍 UNVERIFIED**；本文件不据此关闭 80-C4。选择计划/表清单静态门另见 `00-index.md` §5（90 表 / 2 视图 / 82 裁剪项 / 5 闭包 / 21 软引用，`RESULT=PASS`）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step01-backup.log`（485 行 / 25,581 B）、`step01-backup-logcat.txt`（238 行 / 44,000 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
