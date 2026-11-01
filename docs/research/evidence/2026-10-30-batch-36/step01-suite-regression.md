# E-01 18 套件回归（设备 5562，源计数逐套件对齐）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | 步 1–18（20 步中的前 18 步） |
| gate-or-kd | 设备功能门（无回退） |
| verdict | PASS |
| executed-at | 2026-10-30T08:22–08:44+08:00（宿主文件 mtime；logcat 内时间戳为设备时钟，见 §6） |
| operator | 设备轨（原始日志执行者）；本文件由证据落盘员摘录 |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4`（工作树同 E-00 §3） |
| worktree | 706 项（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | 未采集 |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk` 等 debug 四包（E-00 §3 快照绑定） |
| apk-sha256 | app `4e964e59…` / app.test `1c35a969…` / data.test `269e2a9a…` / database.test `6af00fa1…`（`build/device-track-batch36/four-apk-sha.txt`） |
| apk-version | —（`versionName` 本会话未读） |
| exit-code | 未在日志内固化（runner 的 per-step `exit` 只回显到控制台；见 §5） |
| raw-log | `build/device-track-batch36/step01-backup.log` … `step18-engine-neighbor.log`（各 1 份 + 同名 `-logcat.txt`） |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7） |

## 1. 目的与判据

证明批次 36 新增/修改不使既有 18 个设备套件回退，且每套件的设备执行数等于该套件源内 `@Test` 数（防"绿但漏跑"）。判据来源：计划 `.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md:44`（"新增用例必须重跑"）、批次 35 判据的 18 套件口径。

## 2. 命令（可复跑）

设备轨用 `build/device-track-batch36/run-steps.sh` 的三元组形式，串行执行：

```bash
export MSYS_NO_PATHCONV=1
A="C:/Android/Sdk/platform-tools/adb.exe"; S=emulator-5562; D=build/device-track-batch36
"$A" -s "$S" logcat -c
"$A" -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner | tee "$D/step01-backup.log"
"$A" -s "$S" logcat -v threadtime -d > "$D/step01-backup-logcat.txt"
```

- 工作目录：仓库根
- 构建输入：四包 debug 变体（`build-debug.log:422` `BUILD SUCCESSFUL in 38s`；`four-apk-sha.txt` 四行）
- asset 门：data 测试包内 `71.json` sha256 `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（`asset-71-sha.txt`）
- dex 符号门：三测试包 `DEX OK` + `DEX GATE ALL PASS`（`dex-gate-result.txt:1-4`）

## 3. 结果摘要

`OK (N tests)` 行号取自各 `.log`；`源 @Test` 为本会话对现树源文件 `grep -c '@Test'` 的计数（路径见 §4）。

| step | 套件类 | 设备结果 | 源 @Test | 判定 |
|---|---|---|---:|---|
| 01 | `core.data.backup.BackupRestoreInstrumentedTest` | OK (34 tests)（`:482`） | 34 | ✓ |
| 02 | `core.data.backup.BackupRowCountsCoverageInstrumentedTest` | OK (2 tests)（`:34`） | 2 | ✓ |
| 03 | `core.database.TutorVisualStructureGatesInstrumentedTest` | OK (5 tests)（`:76`） | 5 | ✓ |
| 04 | `core.data.backup.BackupSnapshotTrimmerInstrumentedTest` | OK (9 tests)（`:132`） | 9 | ✓ |
| 05 | `core.data.backup.BackupVisualReferenceInstrumentedTest` | OK (5 tests)（`:76`） | 5 | ✓ |
| 06 | `core.database.FullMigrationMatrixInstrumentedTest` | OK (15 tests)（`:216`） | 15 | ✓ |
| 07 | `core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest` | OK (4 tests)（`:62`） | 4 | ✓ |
| 08 | `core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | OK (3 tests)（`:48`） | 3 | ✓ |
| 09 | `core.database.TutorVisualCommitInstrumentedTest` | OK (12 tests)（`:174`） | 12 | ✓ |
| 10 | `core.data.visual.TutorVisualCommitRepositoryInstrumentedTest` | OK (12 tests)（`:174`） | 12 | ✓ |
| 11 | `core.data.visual.VisualDrawOpsParityInstrumentedTest` | OK (16 tests)（`:230`） | 16 | ✓ |
| 12 | `core.data.visual.VisualDrawOpsPainterInstrumentedTest` | OK (24 tests)（`:342`） | 24 | ✓ |
| 13 | `core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest` | OK (16 tests)（`:230`） | 16 | ✓ |
| 14 | `visualjs.VisualJsIsolationInstrumentedTest` | OK (15 tests)（`:216`） | 15 | ✓（14 → 15：P-12③ 设备例 `VisualJsIsolationInstrumentedTest.kt:423`） |
| 15 | `visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest` | OK (2 tests)（`:34`） | 2 | ✓ |
| 16 | `core.data.visual.FigureGenerationLocalAttemptOrdinalInstrumentedTest` | OK (1 test)（`:20`） | 1 | ✓ |
| 17 | `core.data.visual.FigureGenerationReconcilerInstrumentedTest` | OK (6 tests)（`:90`） | 6 | ✓ |
| 18 | `core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest` | OK (27 tests)（`:384`） | 27 | ✓ |

- **跳过余量**：20 份 `step*.log`（含 step19/20）中 `ignored|skipped|assumption|AssumptionViolated` grep **0 命中**；且每套件 `N` 与源 `@Test` 数逐一相等 → 无被 `OK` 掩盖的跳过。
- **崩溃签名扫描**（20 份 `step*-logcat.txt`，模式 `FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed`）：**20/20 全 0**（本会话实跑逐文件计数）。
- step19/step20 不在本 E-ID：分别见 E-02、E-03（红/部分红，不并入本 PASS）。

## 4. 关键输出（有界）

```text
step01-backup.log:482  OK (34 tests)
step13-pdf.log:230     OK (16 tests)
step14-isolation.log:216  OK (15 tests)
step18-engine-neighbor.log:384  OK (27 tests)
INSTRUMENTATION_CODE: -1（各文件末行；与批次 35 全绿轮同型）
```

源计数依据（本会话 `grep -c '@Test'`）：`core/data/.../BackupRestoreInstrumentedTest.kt`=34、`core/database/.../FullMigrationMatrixInstrumentedTest.kt`=15、`app/.../visualjs/VisualJsIsolationInstrumentedTest.kt`=15、`core/data/.../QuickJsVisualExecutionEngineInstrumentedTest.kt`=27（其余 14 个类同法计得，逐项等于上表）。

## 5. 失败 / 跳过 / 异常

无失败、无跳过。**口径说明**：`run-steps.sh` 的 `echo "=== $step END exit=$?"` 写在 `tee` 管道之后，`$?` 是 `tee` 的退出码且只进控制台、不进 `$step.log`；因此本文件**不以 exit code** 作判定，判定只依据 `OK (N tests)` / `FAILURES!!!` 摘要行与 logcat 签名计数（R4 §7.7 反模式：不得只写 `OK`）。

## 6. 脱敏与去噪

- 删除：无（未贴全量 logcat；只摘 summary 行）。
- 保留：`OK (N tests)`、`INSTRUMENTATION_CODE`、崩溃签名计数（含 0）。
- 时间口径：logcat 用设备时钟，较宿主约 −8h（`step01-backup-logcat.txt:2` 显示 `10-30 00:22:48`，宿主文件 mtime 08:22）；本文件只写宿主窗。
- 扫描：见 `00-index.md` §7；二进制/截图：无。

## 7. 判定

PASS：18/18 套件在扩充后的 keep 规则与批次 36 源树上返回 `OK`，计数与源 `@Test` 数逐套件相等，20 份 logcat 九类签名全 0。未覆盖：`exit-code` 未固化（§5）；AVD 名未采集。
