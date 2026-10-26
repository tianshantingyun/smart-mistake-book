# E-05 备份/恢复核心组

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-26-batch-24 |
| step | 五（备份抽验） |
| gate-or-kd | 备份/恢复设备门 |
| verdict | PASS（34/34） |
| executed-at | 2026-10-26T21:53:30+08:00 |
| operator | 设备验证轨（subagent，批次 24） |
| head | 5bc8bdf868e1296049ab901cf1da69e136062006 |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch24/step05-backup.log`（485 行）、`step05-backup-logcat.txt`（342 行） |
| record-ref | `docs/research/2026-10-26-batch-24-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

抽验备份/恢复核心组的完整类：`BackupRestoreInstrumentedTest` 全量，要求 0 失败、0 跳过。
本步不覆盖 `BackupSelection*`、`BackupRestoreLifecycle`、`BackupSnapshotTrimmer` 等其余备份类
（属"抽验"范围外，未声称）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A -s emulator-5554 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch24/step05-backup-logcat.txt
```

- 工作目录：仓库根
- 运行耗时：约 69 s（`Time: 67.333`）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 34 | 34 |
| failures | 0 | 0 |
| skipped | 0 | 0 |
| `OK (34 tests)` | 是 | 是 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 7 类崩溃签名命中 | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: numtests=34
INSTRUMENTATION_STATUS: test=deleteAllDataClearsThePendingRestoreIndexRebuildMarker
...
Time: 67.333

OK (34 tests)

INSTRUMENTATION_CODE: -1
```

逐例状态：`grep -c "INSTRUMENTATION_STATUS_CODE: 0"` = **34**；失败/跳过码（-1/-2/-4）= **0**。

## 5. 失败 / 跳过 / 异常

无。本类无 `assume` 跳过；34 个用例均返回 0。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：全部逐例状态、summary、crash 签名零计数。
- 扫描：见 `00-index.md` §6（0 命中）。
- 二进制/截图：无。

## 7. 判定

**PASS（34/34）**：备份/恢复核心类在 emulator-5554 / API 34 上全量通过，无跳过、无崩溃签名。

未覆盖：备份选择/生命周期/快照修剪等其余类；真实用户备份包兼容性不在本步。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch24/step05-backup.log`（485 行 / 约 30 KiB）、`step05-backup-logcat.txt`（342 行 / 约 30 KiB）
- 生成命令：见 §2
- 说明：`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
