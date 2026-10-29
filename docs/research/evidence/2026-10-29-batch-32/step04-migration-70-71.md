# E-04 70→71 专用迁移 `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest`（PASS 3/3）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §3.3） |
| gate-or-kd | schema 71 身份负向（计划任务 6） |
| verdict | PASS |
| executed-at | 2026-10-29T11:19:08+08:00（`Time: 12.669`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step04-migration-70-71.log`（51 行）/ `…-logcat.txt`（107 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

带数据的 70→71：v70 行保留、旧三列唯一索引消失、新五列唯一索引存在；同内容新 renderer/font 再提交产生**新行**；索引负控（错列序/非唯一/缺失）能判不等。目标 `OK (3 tests)`（构建前复读 3）。

类/方法名以落码实读为准（`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt:41`）：

| 方法 | 行 |
|---|---|
| `v70SnapshotRowSurvivesMigrationAndTheContentKeyBecomesFiveColumns` | `:44` |
| `bumpedRendererVersionCreatesASecondSnapshotInsteadOfReusingTheV70Row` | `:122` |
| `indexShapeAssertionsCanFailOnWrongColumnOrderNonUniqueAndMissingIndex` | `:227` |

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step04-migration-70-71.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step04-migration-70-71-logcat.txt
```

- `EXIT=0`；`Time: 12.669`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 3 | 期望 3 |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 3 | 每例一条 |
| `INSTRUMENTATION_STATUS_CODE: -2` | 0 | 0 |
| `INSTRUMENTATION_STATUS: stack=` | 0 | 0 |

## 4. 关键输出（有界）

```text
Time: 12.669
OK (3 tests)
```

## 5. 失败 / 跳过 / 异常

无。**未声称**触发器 backstop 生效：迁移实现明确本批不加触发器（`VisualSnapshotIdentityMigration_70_71.kt` 文件内裁定），本套件只证 DROP+CREATE 唯一索引与身份负向。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、逐用例状态码。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：70→71 带数据迁移、身份负向（版本变更产生新快照）、索引负控 3/3。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step04-migration-70-71.log`（51 行 / 2,688 B）、`…-logcat.txt`（107 行 / 15,867 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
