# E-03 迁移矩阵 1→70：`FullMigrationMatrixInstrumentedTest`（PASS 15/15，覆盖 70 个版本点）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 3；判据 §2.3、§4.3 资产门） |
| gate-or-kd | 迁移矩阵门；"每个导出 schema 都能升到当前版本" |
| verdict | **PASS**：`OK (15 tests)`；第 1 例循环 `1..70` 全过；APK assets 70 个 schema |
| executed-at | 2026-10-29T06:33:04–06:38:15+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `4e54f0de476712add5bc839dec16a419b3927a4fdfc27bf41beca4e290c7da2d`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch31/step03-migration-matrix.log`（219 行 / 11796 B）/ `-logcat.txt`（632 行 / 120561 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.3：目标 `OK (15 tests)`；第 1 例 `for (version in 1..STUDY_DATABASE_VERSION)` ⇒ 当前 70
覆盖 **1..70**；三条硬门：版本常量、APK assets 中 schema 数 == V、`V.json` 存在；
任一版本缺 schema/迁移会在该版本中止 → C 类。**不得删版本或缩小循环**。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step03-migration-matrix.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step03-migration-matrix-logcat.txt

# 版本常量与资产门
V=$(sed -n 's/.*STUDY_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p' \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt | head -1)
echo "V=$V"
unzip -l core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk \
  | grep -E 'StudyDatabase/[0-9]+\.json$' | wc -l
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 15 | 期望 15 |
| failures / errors / skipped | 0 / 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` | 15 | — |
| V（版本常量） | **70** | `StudyDatabase.kt:139` |
| APK assets schema 数 | **70** | 必须 == V |
| `70.json` 在包内 | 是 | 必须存在 |
| 覆盖范围 | 1..70（70 个版本点） | 第 1 例循环 |
| 耗时 | 311.49 s | 记录用 |

## 4. 关键输出（有界）

```text
V=70
schemas_in_apk=70
HAS_70.json=YES

Time: 311.49

OK (15 tests)

INSTRUMENTATION_CODE: -1
```

矩阵例状态：

```text
everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback  codes=['1','0']
```

其余 14 例为专项迁移用例（如 `kf5bClassificationCandidatesTableIsAddedLeavingExistingRowsIntactAndIdempotent`），
全部 `codes=['1','0']`。

## 5. 失败 / 跳过 / 异常

无。**schema 71 分支未触发**：当前 V=70，故未覆盖 1→71；若批次 32 升 71，本套件循环自动扩到 1..71，
届时必须同时满足 `71.json` 在 assets 且被 `git ls-files` 跟踪（判据 §4.3）。

## 6. 脱敏与去噪

- 删除：无。
- 保留：summary、V、assets 计数、矩阵例状态码。
- 扫描：R4 §4.4 → 0 命中。
- 二进制/截图：无。

## 7. 判定

PASS：1..70 每个版本点都在设备上建库并升到 70，无版本中止、无破坏性回退。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step03-migration-matrix.log` / `-logcat.txt`
- 行数/大小：219 行 / 11796 B；logcat 632 行 / 120561 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
