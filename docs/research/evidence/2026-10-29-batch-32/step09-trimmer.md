# E-09 裁剪器 `BackupSnapshotTrimmerInstrumentedTest`（PASS 9/9）

| 字段 | 值 |
|---|---|
| evidence-id | E-09 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §4.6） |
| gate-or-kd | KD-80 80-C3（设备半边） |
| verdict | PASS（设备面；JVM `SmbkSelectionPlanTest` 12 例本轨未跑） |
| executed-at | 2026-10-29T11:27:17–11:28:29+08:00（`Time: 71.717`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step09-trimmer.log`（135 行）/ `…-logcat.txt`（151 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

目标 `OK (9 tests)`（构建前复读 9）；验证 71 裁剪器在版本门下的 fail-closed 行为，旧（≤70）快照不可裁剪是既有设计（U9），不得为此放宽版本门。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step09-trimmer.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step09-trimmer-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 9 | 期望 9 |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 9 | 每例一条 |
| `INSTRUMENTATION_STATUS_CODE: -2` | 0 | 0 |
| logcat 9 类签名 | 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
Time: 71.717
OK (9 tests)
```

## 5. 失败 / 跳过 / 异常

无。无 `BackupSnapshotTrimException` 泄漏成失败（正向 fail-closed 用例按断言捕获）。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、逐用例状态码。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS（设备 9/9）。JVM 半边 `SmbkSelectionPlanTest` 12 例未执行，**UNVERIFIED**；80-C3 只能写 PARTIAL。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step09-trimmer.log`（135 行 / 7,096 B）、`…-logcat.txt`（151 行 / 20,866 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
