# E-04 迁移矩阵 15 / 69→70 4 / 70→71 3 / 提交 12 / 仓库 12

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-29-batch-34 |
| step | 四（迁移 + 提交 12 + 仓库 12） |
| gate-or-kd | 迁移面（schema 71）/ 提交 12 / 仓库 12（含第 12 例与 plan 淘汰集断言） |
| verdict | PASS |
| executed-at | 2026-10-29T18:48:40+08:00 → 19:00:49+08:00 |
| operator | 设备验证轨（子代理） |
| head | `db792fa873f4bc150f415b67d1c2e3b1bab6c995` |
| worktree | 721 条（241 `M` / 480 `??`） |
| device-serial | `emulator-5562` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `database-debug-androidTest.apk` = `f75d6fc8…27b116`；`data-debug-androidTest.apk` = `53e69f48…46397caf` |
| exit-code | 0（五条 `am instrument`） |
| raw-log | `build/device-track-batch34/step06…step10*.log`（本机，不入仓） |
| record-ref | `docs/research/2026-10-29-batch-34-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- 迁移面：`FullMigrationMatrixInstrumentedTest` = `OK (15 tests)`、`TutorVisualSnapshotMigration_69_70InstrumentedTest`
  = `OK (4 tests)`、`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` = `OK (3 tests)`。
- 提交 12：`TutorVisualCommitInstrumentedTest` = `OK (12 tests)`，含 scene digest 例
  `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`。
- 仓库 12：`TutorVisualCommitRepositoryInstrumentedTest` = `OK (12 tests)`。批次 33 只跑过 11 例；
  本步必须跑到第 12 例 `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`
  与第 10 例的"预检淘汰集必须包含在飞行 owner 的行"断言。

## 2. 命令（可复跑）

```bash
cd "D:/smart mistake book"; export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562; D=build/device-track-batch34
run() { step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" > "$D/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "$D/$step-logcat.txt" 2>&1; }

run step06-migration-matrix com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest
run step07-migration-69-70 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest
run step08-migration-70-71 com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest
run step09-visual-commit com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest
run step10-visual-repo com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest
```

## 3. 结果摘要

| 套件 | 构建前 `@Test` | 实际 summary | Time | failures/errors/skipped |
|---|---:|---|---:|---:|
| 迁移矩阵 1→71 | 15 | `OK (15 tests)` | 683.937 | 0 / 0 / 0 |
| 69→70 | 4 | `OK (4 tests)` | 11.05 | 0 / 0 / 0 |
| 70→71 | 3 | `OK (3 tests)` | 21.241 | 0 / 0 / 0 |
| 提交 | 12 | `OK (12 tests)` | 4.004 | 0 / 0 / 0 |
| 仓库 | 12 | `OK (12 tests)` | 3.976 | 0 / 0 / 0 |

设备报告数逐项等于构建前源计数（E-01 前复读：15 / 4 / 3 / 12 / 12），排除"旧包少跑一例"。

## 4. 关键输出（有界）

```text
Time: 683.937
OK (15 tests)

Time: 11.05
OK (4 tests)

Time: 21.241
OK (3 tests)

Time: 4.004
OK (12 tests)

Time: 3.976
OK (12 tests)
```

提交/仓库两套件的具体方法名与中文断言字符串已在 E-01 §4 的 dex 门中逐条确认位于
`data` / `database` 测试包内（含 `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`、
`stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`、`预检淘汰集必须包含在飞行 owner 的行`）。

## 5. 失败 / 跳过 / 异常

无。五条套件均 0 失败 / 0 错误 / 0 跳过。

## 6. 脱敏与去噪

- 删除：逐用例进度点与 `am instrument` 冗余状态行。
- 保留：Time、`OK (N tests)`、`INSTRUMENTATION_CODE`、计数对照。
- 扫描：`00-index.md` §6 → 0 命中。

## 7. 判定

**PASS**。迁移 15/4/3、提交 12、仓库 12 全部在重建后的测试包上全绿；仓库第 12 例与 plan 淘汰集断言
已首次上设备并随套件绿（设备报告 12 = 源计数 12）。
