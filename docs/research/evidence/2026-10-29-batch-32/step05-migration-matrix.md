# E-05 迁移矩阵 1→71 `FullMigrationMatrixInstrumentedTest`（PASS 15/15）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §3.1、§6.2） |
| gate-or-kd | 迁移矩阵门 / schema 71 Stage 2 |
| verdict | PASS |
| executed-at | 2026-10-29T11:19:15–11:26:12+08:00（`Time: 416.683`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step03-migration-matrix.log`（219 行）/ `…-logcat.txt`（792 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

- 第 1 例循环 `for (version in 1..STUDY_DATABASE_VERSION)`（`FullMigrationMatrixInstrumentedTest.kt:38`）；常量 71 ⇒ 必须覆盖 1..71 共 71 个版本点。
- 套件目标 `OK (15 tests)`（构建前复读 15）。
- 矩阵用每个版本的导出 `setupQueries` 建库再打开，因此同时是“JSON 哈希 ↔ Impl 哈希”的运行时交叉校验。
- 资产门：APK 内 71 个 schema json 且含 `71.json`。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step03-migration-matrix.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step03-migration-matrix-logcat.txt

V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' core/database/src/main/kotlin/.../StudyDatabase.kt | head -1)  # 71
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l   # 71
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 15 | 期望 15 |
| failures / errors / skipped | 0 / 0 / 0 | 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 15 | 每例一条 |
| `V`（源常量） | 71 | == 71 |
| APK 内 schema json | 71 | == V |
| APK 内含 `71.json` | 是 | 必须 |
| logcat 9 类签名 | 0 | 见 E-12 |
| 耗时 | 416.683 s | 71 个版本点逐版建库 |

## 4. 关键输出（有界）

```text
Time: 416.683
OK (15 tests)
V=71
71
HAS 71.json
```

## 5. 失败 / 跳过 / 异常

无 `Migration didn't properly handle`、无 `Room cannot verify the data integrity`、无 `FileNotFound`。无跳过。

## 6. 脱敏与去噪

- 删除：无（logcat 全量留 `build/`）。
- 保留：summary、逐用例状态码、资产门输出。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

PASS：1..71 全版本点迁移绿；71.json 的 setupQueries 哈希与生成代码同源（每次打开即校验）。**缺口**：`71.json` 当前 `??` 未跟踪，提交时必须纳入（设备轨不做 git 写操作）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step03-migration-matrix.log`（219 行 / 11,797 B）、`…-logcat.txt`（792 行 / 142,612 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
