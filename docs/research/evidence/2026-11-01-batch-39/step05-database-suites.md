# E-05 步骤三·core:database 套件（结构门/迁移/提交 + matrix 复跑）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤三·既有套件不回退（core:database 模块 5 套件） |
| gate-or-kd | 批 38 底册不回退（5/15/4/3/12） |
| verdict | PASS×4（gates 5 / m6970 4 / m7071 3 / dbcommit 12）+ **matrix 见 §3（首跑窗被本轨误停 + 复跑窗判定）** |
| executed-at | 2026-11-01T10:53–13:30+08:00 |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | database androidTest `c5fae932c013859e5bb259c6520a6c788a769f4c4784b19d53dbf485728bc1a4` |
| exit-code | 已完成套件 adb 层均 0 |
| raw-log | `build/device-track-batch39/suite-{gates,m6970,m7071,dbcommit,matrix,matrix-rerun}.log` + 同名 `-logcat.txt`；`matrix-rerun-progress.txt` |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（00-index §6 统一扫描） |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.core.database.<套件名> \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell logcat -v threadtime -d
```

## 2. 结果摘要（4 套件绿）

| 套件 | 结果 | 判据计数 |
|---|---|---|
| `TutorVisualStructureGatesInstrumentedTest` | **OK (5 tests)** | 5 相符 |
| `TutorVisualSnapshotMigration_69_70InstrumentedTest` | **OK (4 tests)** | 4 相符 |
| `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | **OK (3 tests)** | 3 相符 |
| `TutorVisualCommitInstrumentedTest` | **OK (12 tests)** | 12 相符（digest 方法 `onlyLiveSnapshotsCanBeLinkedAndCasUsesChangedRowCounts` 在 dex 门内） |

## 3. `FullMigrationMatrixInstrumentedTest`（15 例）两窗实录

### 3.1 首跑窗（10:54:00 起，无判定——被本轨误停）

- 前序快测逐例 60–70 s 全部完成（TestRunner started/finished 序列完整，无红无跳过）。
- 12:12（宿主）轮到最重例 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`（源码实读：v1..71 每版建库→Room 迁移→断言→删库，`FullMigrationMatrixInstrumentedTest.kt:36-51`）。
- 本轨一度误判该例"v9 卡死"并于 11:52 force-stop 收口——**误判已更正**（探针字母序错觉；实况 v39/71 @11:47 仍在推进，~62 s/版），详见 E-07 §9 更正节。该窗无判定、无签名（E-07 §2）。

### 3.2 复跑窗（11:54:18 起）

- 前序快测逐例完成（60–100 s 量级，含 kf30/kf32/projection/m1-drop/reviewPlan 等），12:12:13 进入 71 版本重例。
- 进度探针（`ls -t databases/ | grep migration-matrix` mtime 序，E-07 §9 教训执行）：v1@12:12 → v14@12:22 → v23@12:31 → v31@12:41 → v39@12:51 → v46@13:00 →（待续至 v71）。
- 判定：**见本文件 §4 追加**（复跑窗结束落笔）。

## 4. 复跑窗判定（追加于收窗时点 13:36+08:00）

- **`OK (15 tests)`**：`Tests run: 15, Failures: 0`；15 例 `INSTRUMENTATION_STATUS_CODE: 0`；adb 层 exit=0；九类崩溃签名 0（E-07 同式扫描）。
- 重例 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 单例 12:12:13–13:36:41（~84 min，71 版本 × ~62–70 s/版，宿主 4 模拟器负载下），进度探针（mtime 序）v1→v71 连续推进，与本轨首跑窗被误停前的真实推进速度一致（v39@53 min，E-07 §9）。
- 判定：**PASS（以复跑窗为准）**——15/15 与判据计数相符，断言面零放宽；首跑窗登记为本轨操作误停（无判定、无签名），教训见 E-07 §9。

