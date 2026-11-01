# E-12 批次 39 收尾轮·既有套件不回退（28 套件底册）

| 字段 | 值 |
|---|---|
| evidence-id | E-12 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·步四 |
| gate-or-kd | 28 套件底册不回退（24 既有 + 4 新向量） |
| verdict | **PARTIAL**：24 套件绿（177 例）；4 套件红（backup 8 / c4 8 / rowcounts 2 / isolation 8），前两者归因 B+D，isolation 由 E-11 判。**本窗（01:27–02:57）落在 E-14 判作废的窗 W3**：24 个绿套件只能作运行观测，**不得作为"不回退"的最终证明**（E-14:50） |
| executed-at | 2026-11-02T01:27:44+08:00 – 02:57:24 |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 514 → 510 M/??（窗内他线在飞，见 E-14） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | app `fe9452aa…` / appTest `b4242b7d…`（后换 `3c222fca…` 仅 KD-76 用例）/ data `f7e96147…` / database `9ca01139…` |
| apk-sha256 | 全值见 E-09 |
| exit-code | 每套件 adb-exit=0（runner 逐套件落盘） |
| raw-log | `build/device-track-batch39-tail/suite-<slug>.log` + `-logcat.txt`（26 套件）+ `suites-progress.txt` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是 |

## 1. 命令

`build/device-track-batch39-tail/run-suites.sh`：逐套件 `logcat -c` → `am instrument -w -r -e class <FQCN> <pkg>/AndroidJUnitRunner` → 落 log → `logcat -d` 落盘。x03 复用 E-10 日志、isolation 复用 E-11 日志（按判据不重复判定），其余 26 套件本次串行执行。

## 2. 结果

| slug | 底册 N | 实测 | 判定 |
|---|---|---|---|
| v01app | 4 | `OK (4 tests)` | PASS |
| nvec | 4 | `OK (4 tests)` | PASS |
| x02 | 6 | `OK (6 tests)` | PASS |
| a10x | 1 | `OK (1 test)` | PASS |
| a09 | 3 | `OK (3 tests)` | PASS |
| p09 | 1 | `OK (1 test)` | PASS |
| p10 | 1 | `OK (1 test)` | PASS |
| p0405 | 1 | `OK (1 test)` | PASS |
| flags | 2 | `OK (2 tests)` | PASS |
| backup | 34 | `Tests run: 34, Failures: 8` | **FAIL（B+D）** |
| rowcounts | 2 | `Tests run: 2, Failures: 2` | **FAIL（B+D）** |
| trimmer | 9 | `OK (9 tests)` | PASS |
| c4 | 8 | `Tests run: 8, Failures: 8` | **FAIL（B+D）** |
| repo | 12 | `OK (12 tests)` | PASS |
| parity | 16 | `OK (16 tests)` | PASS |
| painter | 24 | `OK (24 tests)` | PASS |
| pdf | 16 | `OK (16 tests)` | PASS |
| ordinal | 1 | `OK (1 test)` | PASS |
| reconciler | 6 | `OK (6 tests)` | PASS |
| engine | 27 | `OK (27 tests)` | PASS |
| a10 | 3 | `OK (3 tests)` | PASS |
| gates | 5 | `OK (5 tests)` | PASS |
| matrix | 15 | `OK (15 tests)`（`Time: 3959.641`） | PASS |
| m6970 | 4 | `OK (4 tests)` | PASS |
| m7071 | 3 | 首跑「class not found」→ 修正 FQCN 复跑 `OK (3 tests)` | PASS（A 类命令修正） |
| dbcommit | 12 | `OK (12 tests)` | PASS |
| x03 | 1 | `OK (1 test)` | PASS（E-10） |
| isolation | 15 | `Tests run: 15, Failures: 8` | **FAIL（E-11）** |

合计 236 例；**红 26 例；通过 210 例** = 24 个绿套件 177 例 + 4 个红套件内通过的 33 例（backup 34−8=26、isolation 15−8=7；c4/rowcounts 全红）。原写"绿 177 例 / 红 26 例"把"绿套件的用例数"当成"通过数"，漏计红套件内通过的 33 例（177+26=203≠236），本复核轮更正；逐套件底册计数无下降、无上升。

## 3. 红例归因（backup / c4 / rowcounts）

失败原文：

```text
AssertionError: 68 旧包恢复后必须由 Room 迁移到当前版本 expected:<71> but was:<72>
BackupSnapshotTrimException: 快照 schema=72，裁剪器只支持 schema=71
```

- 类别 **B（他线在飞）+ D（构建输入窗口）**：`data-debug-androidTest.apk` 构建于 01:04，内部 `StudyDatabase` 为 72（迁移 `KnowledgeSearchFeatureStatMigration_71_72.kt` 等为他线未跟踪工作），而同包 `SmbkSelectionPlan.SCHEMA_VERSION = 71`（`core/data/.../backup/SmbkSelectionPlan.kt:44`）；两者不属于同一快照。
- 现树（02:59 实读）`StudyDatabase.kt:139` 已回到 `STUDY_DATABASE_VERSION = 71`，他线仍在改动（mtime 01:57:29，02:25→02:59 hash 再变）。
- 处置：**不重跑、不改断言**；待他线 schema-72 工作落定、树静止后，重建 `data-debug-androidTest.apk` 并复跑这三套件。当前结果不构成产品回归证据。
- 崩溃签名：三套件 logcat 9 类全 0（`suite-{backup,c4,rowcounts}-logcat.txt` 逐窗扫描）。

## 4. m7071 首跑 FAIL 的 A 类修正

底册 FQCN 为 `TutorVisualSnapshotMigration_70_71InstrumentedTest`，仓内实际类为 `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest`（`core/database/src/androidTest/.../TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt`）。首跑 `Failed loading specified test class`；改 FQCN 复跑 `OK (3 tests)`。属 runner 命令缺陷，非产品/夹具。

## 5. 崩溃签名（26 套件逐窗 + 聚合）

逐窗扫描 9 类；26 个套件窗聚合：

```text
FATAL EXCEPTION=0  ANR in=0  NoSuchMethodError=0  NoClassDefFoundError=0
UnsatisfiedLinkError=0（26 套件窗）  SIGSEGV=0  CANNOT LINK=0
failed to map segment=0  death was not confirmed=1
```

- 唯一 `death was not confirmed` 出现在 p10 窗，是 p10 用例设计的 fail-closed 日志（`VisualJsClientEngine: … death was not confirmed within 2000 ms; fail-closed`），测试本身 `OK (1 test)`；isolation 窗该串为 0（E-11 判据成立）。
- **判据就地放宽披露（第 2 轮复核补记）**：T1 §7.2 的字面要求是每个既有套件"9 类签名 0"。p10 窗有 **1** 条 `death was not confirmed`（`suite-p10-logcat.txt:666`），严格按字面**不满足**；本表仍判 p10 PASS，依据是"该行是用例设计的 fail-closed 日志、`OK (1 test)`、且已在 §5 披露"——即**判据被就地放宽**（已披露，未隐藏）。是否接受该放宽由主会话裁定；不把 p10 当作"9 类签名 0"的合格证据。
- KD-76 校准窗另有 1 条 `NativeCrashHandlerImpl: UnsatisfiedLinkError: dlopen failed: libnative_crash_handler_jni.so`（测试基础设施库缺失告警）与产品 fail-closed 行，单独在 E-13 登记，不计入 26 套件窗。

## 6. 长套件纪律

matrix 15 例耗时 66 min（01:50:24–02:57:24），未 force-stop、未用字母序 tail 判进度；按 `INSTRUMENTATION_STATUS: current` 计数推进，`OK (15 tests)` 收口。

## 7. 第 2 轮复核修复（2026-11-02；零 Gradle/零设备）

本节由复核修复轮追加，只修口径与计数，不重写原始 raw log：

1. **窗作废注记**：本文件全部运行动作在 E-14 判作废的窗 W3（01:27:44–02:57:24），24 个绿套件只能作运行观测；00-index §11 的 E-12 行已同步补注。
2. **计数更正**：见 §2（总 236 = 红 26 + 通过 210；原"绿 177 / 红 26"漏计红套件内通过的 33 例）。
3. **p10 判据放宽披露**：见 §5。
4. **本文件不声称**："24 套件绿 = 不回退通过"或"236 例中 177+26 已覆盖全部"。

**本节改动文件**：本文件。
