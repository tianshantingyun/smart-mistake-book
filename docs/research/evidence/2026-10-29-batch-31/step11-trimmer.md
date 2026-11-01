# E-11 备份裁剪器 9 例：`BackupSnapshotTrimmerInstrumentedTest`（PASS 9/9）

| 字段 | 值 |
|---|---|
| evidence-id | E-11 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 三（KD-80 80-C3 设备半边；判据 §3.3） |
| gate-or-kd | KD-80 80-C3 |
| verdict | **PASS**：`OK (9 tests)`，0 失败/错误/跳过 |
| executed-at | 2026-10-29T06:42:14–06:43:05+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step12-trimmer.log`（135 行 / 7095 B）/ `-logcat.txt`（116 行 / 16161 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §3.3：KD-80 的"裁剪器/选择计划单测绿"设备半边 = `BackupSnapshotTrimmerInstrumentedTest`
**9 例**（构建前 `grep -c '@Test'` = 9），目标 `OK (9 tests)`；任一红 → KD-80 保持 open。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step12-trimmer.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step12-trimmer-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 9 | 期望 9 |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 9 | — |
| Time | 51.41 s | 记录用 |
| 9 类签名 | 0（E-10） | 期望 0 |

## 4. 关键输出（有界）

```text
Time: 51.41

OK (9 tests)

INSTRUMENTATION_CODE: -1
```

九例（每例出现两次：启动 + 结束，均 `STATUS_CODE 1 → 0`）：

```text
fullSelectionAllowsUnknownTablesWhileSubsetStillRejectsThem
spacePreflightFailureLeavesTheSnapshotUntouched
mask15KeepsAllUserCategoriesButAlwaysTrimsDerivedIndexes
mask9TrimsSessionsAndMasteryButKeepsSharedFigureOperation
subsetTrimVacuumsWhenFreePagesRemainEvenWithNoRowsDeleted
trimmingWithTheSameMaskTwiceIsIdempotentAndDoesNotVacuumAgain
mask13KeepsSessionsAndDeletesOnlyMasteryAndDerivedTables
schemaRoomMasterTableAndSqliteSequenceAreUntouched
invalidMasksAreRejectedBeforeTheSnapshotIsTouched
```

`spacePreflightFailureLeavesTheSnapshotUntouched` 与 `invalidMasksAreRejectedBeforeTheSnapshotIsTouched`
绿 = 裁剪前 fail-closed；`schemaRoomMasterTableAndSqliteSequenceAreUntouched` 绿 = 裁剪不破坏 Room 身份表。

## 5. 失败 / 跳过 / 异常

无。**80-C3 的 JVM 半边未由本轨执行**：`SmbkSelectionPlanTest`（12 例，
`core/data/src/test/.../SmbkSelectionPlanTest.kt`，本轮只读计数 12）属 Gradle 单测任务，
超出本轨 Gradle 允许面（只 assemble*）。门禁轨本轮报"单测门第 1 轮 exit=0"（**工单转录，非本轨 E-file**）。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、九例名、耗时。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS（设备半边）。KD-80 80-C3 的两半：设备 9/9 绿；JVM 12 例 = **UNVERIFIED（本轨）**，
以门禁轨 exit=0 作旁证、不作关闭依据。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step12-trimmer.log` / `-logcat.txt`
- 行数/大小：135 行 / 7095 B；logcat 116 行 / 16161 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
