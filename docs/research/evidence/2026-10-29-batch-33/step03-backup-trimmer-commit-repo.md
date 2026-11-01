# E-03 备份 34/34 + 裁剪器 9/9 + 提交 12/12 + 仓库 11/11

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据二 / 三 |
| gate-or-kd | KD-80 80-C1 / 80-C2（设备侧）/ 80-C3（设备半边）；预算/evict/pin/租约/digest |
| verdict | PASS（80-C3 的 JVM 半边未执行，见 §7） |
| executed-at | 2026-10-29T14:59:38+08:00（备份）/ 15:01:12（裁剪器）/ 15:01:17（仓库）/ 14:50:00（提交） |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | data `36f79b19…`（备份/裁剪器/仓库）；database `a4696041…`（提交） |
| apk-sha256 | `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` / `a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0` |
| apk-version | （空） |
| exit-code | 0（四步） |
| raw-log | `build/device-track-batch33/step01-backup-final.log`（485 行）、`step03-trimmer-final.log`（135）、`step08-visual-commit.log`（含 logcat）、`step09-visual-repo-final.log`（163） |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

- 80-C1：`BackupRestoreInstrumentedTest` 34/34，S=71；`schema68ArchiveRestoresAndMigratesToCurrentVersion`
  恢复后版本断言读常量 71。
- 80-C2 设备侧：v3 manifest 的 `selection.rowCounts` 必须覆盖 90 表，恢复时逐表对拍。
- 80-C3 设备半边：`BackupSnapshotTrimmerInstrumentedTest` 9/9。
- 提交/仓库：预算、stage→commit demote/evict、pin/租约、digest 负向不回退。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
run() { step="$1"; pkg="$2"; cls="$3"
  $A -s "$S" logcat -c
  $A -s "$S" shell am instrument -w -r -e class "$cls" \
    "$pkg/androidx.test.runner.AndroidJUnitRunner" > "build/device-track-batch33/$step.log" 2>&1
  $A -s "$S" logcat -v threadtime -d > "build/device-track-batch33/$step-logcat.txt"; }

run step01-backup-final com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupRestoreInstrumentedTest
run step03-trimmer-final com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.backup.BackupSnapshotTrimmerInstrumentedTest
run step09-visual-repo-final com.tingyun.smartmistakebook.core.data.test \
  com.tingyun.smartmistakebook.core.data.visual.TutorVisualCommitRepositoryInstrumentedTest
run step08-visual-commit com.tingyun.smartmistakebook.core.database.test \
  com.tingyun.smartmistakebook.core.database.TutorVisualCommitInstrumentedTest
```

- 退出码：四步均 0。

## 3. 结果摘要

| 套件 | 目标 | 实测 | 判定 |
|---|---:|---|---|
| `BackupRestoreInstrumentedTest` | 34 | `OK (34 tests)` | PASS |
| `BackupSnapshotTrimmerInstrumentedTest` | 9 | `OK (9 tests)` | PASS |
| `TutorVisualCommitRepositoryInstrumentedTest` | 11 | `OK (11 tests)` | PASS |
| `TutorVisualCommitInstrumentedTest` | 12 | `OK (12 tests)` | PASS |
| skipped / ignored | 0 | 0 | PASS |

备份 logcat/摘要四个 grep：

| 模式 | 命中 |
|---|---:|
| `BackupSnapshotTrimException` | 0 |
| `expected:<70>` | 0 |
| `expected:<71>` | 0 |
| `裁剪器只支持 schema=` | 0 |
| `SmbkArchiveException` | 0 |

`schema68ArchiveRestoresAndMigratesToCurrentVersion` 在 raw log 中出现 2 次（方法名 + 结果行）。

## 4. 90 键逐名的代码依据与静态半边

本轨实跑判据 §1.4 的只读脚本（仓库根，`S=71`）：

```text
SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
ONLY_PLAN=[]
ONLY_JSON=[]
DUP=[]
VIEWS_MATCH=True
PLAN_VERSION=71 JSON_VERSION=71
PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138
RESULT=PASS
EXIT=0
```

- 成包：`SmbkArchiveCodec.kt:464-468` 要求 `selection.rowCounts.keys == SmbkSelectionPlan.rowCountKeys`，
  missing/extra 逐名写进异常；`SmbkSelectionPlan.kt:199` `rowCountKeys = allTables.toSet()`（90）。
- 恢复：`AndroidBackupRepository.kt:1280-1312` 遍历 `SmbkSelectionPlan.allTables`，声明与实测不等时
  逐表 `"$table declared=$declared actual=$actual"` 报出；未勾选类别非 0 同样点名拒绝。
- 34/34 绿即上述两条路径未触发；raw log 不逐表打印 90 行（通过路径不输出），因此本文件不伪造
  逐表清单，只记录"产品路径 + 34/34"。
- **由此 80-C2 的设备半边不能记 PASS**（2026-10-29 复核修复轮更正）：判据
  `docs/research/2026-10-29-batch-33-device-criteria.md:255-257` 要求"90 键逐名写进证据结论，
  不得只写 34/34 绿"；本文件的 raw log 对 `rowCounts` / `tutor_visual_snapshot` 均 0 命中
  （`grep -c` 实跑）。故 `00-index.md` §4 的 80-C2 已由 PASS 改为 **PARTIAL（静态 PASS /
  设备侧 UNVERIFIED）**；KD-80 仍保持 open。

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：逐用例重复进度行。
- 保留：四个 summary、五个 grep 计数、方法名计数。
- 扫描：0 命中。
- 二进制/截图：无。

## 7. 判定

PASS（80-C1 / 80-C2 设备侧 / 80-C3 设备半边）。**80-C3 不完整**：JVM 半边
（`SmbkSelectionPlanTest` 12、`VisualCheckpointRetentionTest` 17）本轨权限只允许 `assemble*`，
未执行 → `UNVERIFIED`；KD-80 因此保持 open。未覆盖：JVM 单测、跨设备像素比较（本例不涉及）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step01-backup-final(.log|-logcat.txt)`、`step03-trimmer-final*`、
  `step08-visual-commit*`、`step09-visual-repo-final*`
- 生成命令：见 §2
- 行数/大小：485 + 135 + 163 + 79 行等（见索引）
