# E-05 备份/恢复核心组

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | 五（备份抽验） |
| gate-or-kd | 备份/恢复设备门 |
| verdict | PASS |
| executed-at | 2026-10-26T16:23:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step05-backup-restore.log`（486 行）、`step05-backup-restore-logcat.txt`（274 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

抽验备份/恢复核心组的完整类：`BackupRestoreInstrumentedTest` 全量，要求 0 失败、0 跳过。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step05-backup-restore-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 34 | 34 |
| failures | 0 | 0 |
| skipped | 0 | 0（本类无 assume 跳过） |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |
| 12 类日志签名命中 | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
OK (34 tests)
INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常

无。本步未覆盖 `BackupSelectionCreate/Restore`、`BackupRestoreLifecycle`、`BackupSnapshotTrimmer` 等其余备份类（属"抽验"范围外，未声称）。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**PASS**：备份/恢复核心类 34/34 通过，无跳过、无崩溃签名。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step05-backup-restore.log`、`step05-backup-restore-logcat.txt`
- 行数：486 行 / 约 25 KiB；logcat 274 行
- 生成命令：见 §2
