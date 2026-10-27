# E-05 备份/恢复核心组抽验

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-27-batch-26 |
| step | 五（备份抽验） |
| gate-or-kd | 备份/恢复设备门 |
| verdict | PASS（34/34） |
| executed-at | 2026-10-27T07:31:00+08:00 |
| operator | 设备验证轨（subagent，批次 26） |
| head | 405c83739cfa5385aa7652ef61f3488f953c66ce |
| worktree | dirty（他线在飞） |
| device-serial | emulator-5554（E-04 §6 恢复后的 debug 态） |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `94df5c3931db09170dc8ac52db3a074c281bc2b8869bdba3ced724e08dea8590`（与批次 25 产物逐字节一致；up-to-date 未重编） |
| apk-version | — |
| exit-code | 0（adb 进程退出码；判据是 summary） |
| raw-log | `build/device-track-batch26/step05-backup.log`（485 行）、`step05-backup-logcat.txt`（302 行） |
| record-ref | 批次 26 记录（由记录员后写） |
| sanitized | 是（§6） |

## 1. 目的与判据

抽验备份/恢复核心组完整类：`BackupRestoreInstrumentedTest` 全量（`OK (34 tests)`，
`INSTRUMENTATION_STATUS_CODE: 0` 计数 34 实测一致），要求 0 失败、0 跳过——与批次 24 E-05 /
批次 25 E-05 同判据（不回退）。本步不覆盖 `BackupSelection*`、`BackupRestoreLifecycle`、
`BackupSnapshotTrimmer` 等其余备份类（"抽验"范围外，不声称）。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
# 设备此时为 E-04 §6 的恢复态（debug 主包 + 两测试包已重装，均 Success）
$A -s emulator-5554 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch26/step05-backup-logcat.txt
```

- 工作目录：仓库根；安装 `Success`；运行耗时 `Time: 24.026`（批次 25 同类 20.739 s；差异为
  E-03 冷启动后首跑状态差异，非判据）

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 34 | 34 |
| failures | 0 | 0 |
| skipped | 0 | 0 |
| `OK (34 tests)` | 是 | 是 |
| `INSTRUMENTATION_STATUS_CODE: 0` 计数 | 34 | 34 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（adb 侧 exit=0） |
| 5 类签名命中 | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 24.026

OK (34 tests)

INSTRUMENTATION_CODE: -1
```

签名扫描（对 `step05-backup-logcat.txt`）：

```bash
for s in "FATAL EXCEPTION" "ANR in" "NoClassDefFoundError" "UnsatisfiedLinkError" "SIGSEGV"; do
  printf '%-22s %s\n' "$s" "$(grep -c "$s" build/device-track-batch26/step05-backup-logcat.txt)"
done
# 全部 0
```

## 5. 失败 / 跳过 / 异常

无。34 个用例均 `STATUS_CODE: 0`，无 `assume` 跳过，无崩溃签名。

## 6. 脱敏与去噪

- 删除：adb 安装进度行。
- 保留：summary、逐例码计数、崩溃签名零计数。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PASS（34/34）**：备份/恢复核心类在 emulator-5554 / API 34（批次 26 源 debug 树构建件、
E-04 恢复态）上全量通过，相对批次 24/25 E-05 不回退。

未覆盖：备份选择/生命周期/快照修剪等其余类；真实用户备份包兼容性不在本步。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch26/step05-backup.log`（485 行）、`step05-backup-logcat.txt`（302 行）
- 生成命令：见 §2。`build/` 被 `.gitignore` 忽略；关闭判据只认本文件。
