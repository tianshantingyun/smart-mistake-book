# E-09 core:data 备份组四套件不回退（备份 34 / rowCounts 2 / 裁剪器 9 / C4 8）

| 字段 | 值 |
|---|---|
| evidence-id | E-09 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤三·既有套件（data APK 备份组串行） |
| gate-or-kd | 批次 38 §1 三·备份 34 / rowCounts 2 / 裁剪器 9 / C4 不回退；C4 计数按构建前复读 **8**（判据表 5 + 本批 D-04 新增 3） |
| verdict | PASS（四套件全绿，含 D-04 三新例） |
| executed-at | 2026-11-01T05:0x+08:00（step10/11/12/13，串行） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | data androidTest（窗口 B 构建，E-01 §4） |
| apk-sha256 | `e86fce38d62c59b274b0e46de4e310ed712b2d2e7fb6b923938ce1e1c409bcc0` |
| exit-code | 各步 0 |
| raw-log | `build/device-track-batch38/step{10,11,12,13}-*.log` + 各 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

备份组四套件不回退：设备 `OK (N)` == 构建前计数（34/2/9/**8**）；71.json schema 资产随包（E-01 §6，378961 B）；rowCounts 90 键逐名与 71.json 比对（KD-80 C2 设备侧半边）；C4 的 8 例含**本批 D-04 预览 canonical 资产三新例**（`previewAssetRidesWithProgramAndSnapshotThroughArchiveAndRestore`、`repackedArchiveMissingPreviewAssetBytesIsRejectedBeforeSwap`、`createArchiveFailsWhenReferencedPreviewBytesAreMissing`——"备份含程序/快照/预览、缺必需字节拒绝完整包"，diff +339 行为他轨落点，本轨整文件纳入口径执行并按构建前计数判定）。

## 2. 命令（可复跑，逐步之间 logcat -c）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
PKG=com.tingyun.smartmistakebook.core.data.test; BAK=com.tingyun.smartmistakebook.core.data.backup
$A -s $S shell am instrument -w -r -e class $BAK.BackupRestoreInstrumentedTest           $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $BAK.BackupRowCountsCoverageInstrumentedTest $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $BAK.BackupSnapshotTrimmerInstrumentedTest   $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell am instrument -w -r -e class $BAK.BackupVisualReferenceInstrumentedTest   $PKG/androidx.test.runner.AndroidJUnitRunner
# 每步前 logcat -c、每步后 logcat -v threadtime -d 落盘
```

## 3. 结果摘要

| step | 套件 | 构建前计数 | 设备结果 | Time (s) | 签名命中 |
|---|---|---:|---|---:|---:|
| 10 | BackupRestore | 34 | **OK (34 tests)** | 100.095 | 0 |
| 11 | BackupRowCountsCoverage | 2 | **OK (2 tests)** | 4.680 | 0 |
| 12 | BackupSnapshotTrimmer | 9 | **OK (9 tests)** | 489.972 | 0 |
| 13 | BackupVisualReference（C4） | **8**（5+3 D-04） | **OK (8 tests)** | 25.866 | 0 |

skip/assumption：全部 0。

## 4. 关键输出（有界）

```text
== step10-backup:  OK (34 tests)
== step11-rowcounts: OK (2 tests)
== step12-trimmer:   OK (9 tests)
== step13-c4-visual-ref: OK (8 tests)   # 含 D-04 三新例，全绿
```

## 5. 失败 / 跳过 / 异常

无。C4 计数差（5→8）已在 E-01 §6 登记归因（D-04 落点、同文件他轨 M +339 行）；三新例在设备上真实执行并随套件全绿。

## 6. 脱敏与去噪

- 删除：无；保留：签名计数（0）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：47 例（34+2+9+8）全部在 data APK `e86fce38…` 上执行通过；D-04 预览资产三例取得设备级证据（备份往返含预览、缺字节拒完整包）。
