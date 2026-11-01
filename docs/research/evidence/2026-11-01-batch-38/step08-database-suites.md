# E-08 core:database 五套件不回退（结构门 5 / 迁移矩阵 15 / 69→70 4 / 70→71 3 / 提交 12）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤三·既有套件（database APK 五套件串行） |
| gate-or-kd | 批次 38 §1 三·结构门 5 / 迁移 15/4/3 / 提交 12 不回退 |
| verdict | PASS（五套件全绿，计数逐一相符） |
| executed-at | 2026-11-01T04:2x–05:3x+08:00（step03/06/07/08/09，串行） |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | database androidTest |
| apk-sha256 | `7baf8c4624e14ec682aa2c70583cc8f0765da8c29fc78c49d2a40434d13c35c1` |
| exit-code | 各步 0 |
| raw-log | `build/device-track-batch38/step{03,06,07,08,09}-*.log` + 各 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

五套件不回退，设备 `OK (N)` == 构建前 `grep -c '@Test'`（E-01 §6 计数表）：结构门 5、迁移矩阵 15、69→70 4、70→71 3、提交 12；九类签名 0；无跳过。迁移矩阵含 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`（逐导出 schema 版本迁移到 71，无破坏性回退）。

## 2. 命令（可复跑，逐步之间 logcat -c）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
PKG=com.tingyun.smartmistakebook.core.database.test; DB=com.tingyun.smartmistakebook.core.database
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class $DB.TutorVisualStructureGatesInstrumentedTest   $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class $DB.FullMigrationMatrixInstrumentedTest         $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class $DB.TutorVisualSnapshotMigration_69_70InstrumentedTest        $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class $DB.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest $PKG/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class $DB.TutorVisualCommitInstrumentedTest           $PKG/androidx.test.runner.AndroidJUnitRunner
# 每步后 $A -s $S logcat -v threadtime -d 落盘
```

## 3. 结果摘要

| step | 套件 | 构建前计数 | 设备结果 | Time (s) | 签名命中 |
|---|---|---:|---|---:|---:|
| 03 | TutorVisualStructureGates | 5 | **OK (5 tests)** | 9.835 | 0 |
| 06 | FullMigrationMatrix | 15 | **OK (15 tests)** | **2750.714** | 0 |
| 07 | TutorVisualSnapshotMigration_69_70 | 4 | **OK (4 tests)** | 57.529 | 0 |
| 08 | TutorVisualSnapshotIdentityMigration_70_71 | 3 | **OK (3 tests)** | 80.700 | 0 |
| 09 | TutorVisualCommit | 12 | **OK (12 tests)** | 8.767 | 0 |

skip/assumption：五套件全部 0。全部 `INSTRUMENTATION_CODE: -1`（AndroidJUnitRunner 固定值，判定以 summary 为准，规范 §8.1）。

## 4. 关键输出（有界）

```text
== step06-migration-matrix: INSTRUMENTATION_RESULT: stream= OK (15 tests)
Time: 2,750.714 / OK (15 tests) / INSTRUMENTATION_CODE: -1
```

迁移矩阵耗时 2750.7 s 的构成（logcat 证据）：大矩阵用例逐版本开库时 Room 生成物 `StudyDatabase_Impl$createOpenDelegate…onValidateSchemaN` 反复超 JIT 编译阈值（`Method exceeds compiler instruction limit: 19k–25k`，pid 12140 每 ~30–35 s 完成一个版本），与本批改动无关、批次 37 同用例同形态；本批无 schema 变更（S=71 静态门 E-01 §2 PASS）。

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无；保留：Method exceeds compiler instruction limit 行（耗时归因证据）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：五套件 39 例全部在指定设备与 APK（`7baf8c46…`）上执行通过，计数与构建前复读逐一相符。
