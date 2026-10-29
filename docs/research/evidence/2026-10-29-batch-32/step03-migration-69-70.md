# E-03 迁移 69→70→71 `TutorVisualSnapshotMigration_69_70InstrumentedTest`（PASS 4/4）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §3.2） |
| gate-or-kd | schema 71 迁移面 |
| verdict | PASS |
| executed-at | 2026-10-29T11:18:54+08:00（`Time: 6.901`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step02-migration-69-70.log`（65 行）/ `…-logcat.txt`（99 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

v69 库打开后必须连续走 69→70→71：四表建出且为空、旧行一行不丢、再次打开幂等；索引断言与迁移 DDL 常量同源，负控能对错列序/非唯一/不存在索引判不等。目标 `OK (4 tests)`（构建前复读 4）。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotMigration_69_70InstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step02-migration-69-70.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step02-migration-69-70-logcat.txt
```

- `EXIT=0`；`Time: 6.901`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 4 | 期望 4 |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 4 | 每例一条 |
| `INSTRUMENTATION_STATUS_CODE: -2` | 0 | 0 |
| logcat 9 类签名 | 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
Time: 6.901
OK (4 tests)
INSTRUMENTATION_CODE: -1        # 恒 -1，不作判据
```

## 5. 失败 / 跳过 / 异常

无。`INSTRUMENTATION_STATUS: stack=` 计数 0。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary 与逐用例状态码。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：v69→71 连续迁移 4/4；只跑 2 例的旧包风险不成立（实跑 4 例）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step02-migration-69-70.log`（65 行 / 3,372 B）、`…-logcat.txt`（99 行 / 15,259 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
