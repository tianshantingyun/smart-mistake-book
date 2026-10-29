# E-05 迁移矩阵 1→71（15）+ 69→70（4）+ 70→71（3）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据三（迁移面不回退） |
| gate-or-kd | schema 71 迁移面 |
| verdict | PASS |
| executed-at | 2026-10-29T15:11:14+08:00（矩阵）/ 15:11:24（69→70）/ 15:11:42（70→71） |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0` |
| apk-version | （空） |
| exit-code | 0（三步） |
| raw-log | `step05-migration-matrix.log`（219 行）；`step06-migration-69-70.log`（65）；`step07-migration-70-71.log`（51） |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

证明 71 的迁移面没有回退：每个导出 schema 都能非破坏迁移到当前版本；69→71 的视觉表创建
与 LIVE-only/CAS/索引 DDL 断言仍成立；70→71 的身份迁移仍成立。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
run() { step="$1"; cls="$2"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
    > "build/device-track-batch33/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "build/device-track-batch33/$step-logcat.txt"; }

run step05-migration-matrix com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest
run step06-migration-69-70 com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest
run step07-migration-70-71 com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest
```

- 退出码：三步均 0。

## 3. 结果摘要

| 套件 | 目标 | 实测 | 判定 |
|---|---:|---|---|
| `FullMigrationMatrixInstrumentedTest` | 15 | `OK (15 tests)` | PASS |
| `TutorVisualSnapshotMigration_69_70InstrumentedTest` | 4 | `OK (4 tests)` | PASS |
| `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | 3 | `OK (3 tests)` | PASS |
| skipped / ignored | 0 | 0 | PASS |

## 4. 关键输出（有界）

```text
step05-migration-matrix: OK (15 tests)
step06-migration-69-70:  OK (4 tests)
step07-migration-70-71:  OK (3 tests)
```

无 `Migration didn't properly handle`、无 `FileNotFoundException`/assets 缺失、无
`Room cannot verify the data integrity`（三份 raw log 全量 grep 为 0，见 §6）。

## 5. 失败 / 跳过 / 异常

无。未出现旧包/资产缺失（D 类）形态。

## 6. 脱敏与去噪

- 删除：逐用例重复行。
- 保留：三条 summary。
- 扫描：三份 raw log 中 `Migration didn't properly handle|FileNotFoundException|Room cannot verify` 计数 0；
  证据文件人工通读 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。三套迁移全部绿，且同一 database APK（`a4696041…`）也是结构门与提交套件的执行包。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step05-migration-matrix(.log|-logcat.txt)` 等三对
- 生成命令：见 §2
- 行数/大小：219/65/51 行
