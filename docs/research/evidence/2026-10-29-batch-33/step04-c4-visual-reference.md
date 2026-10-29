# E-04 KD-80 80-C4：备份/恢复后视觉四表引用关系（新用例 5/5）

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据二（KD-80 80-C4） |
| gate-or-kd | KD-80 80-C4 |
| verdict | PASS |
| executed-at | 2026-10-29T14:58:49+08:00 |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（新增本测试文件，未跟踪；零主源改动） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` |
| apk-version | （空） |
| exit-code | 0 |
| raw-log | `build/device-track-batch33/step04-c4-visual.log`（79 行）；logcat 208 行 |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

证明备份→归档→恢复之后，视觉四表（`tutor_visual_program/snapshot`、`tutor_message_visual`、
`problem_revision_visual`）的行集合、复合 program 一致性、LIVE-only 引用、状态/角色域、
身份与三条摘要仍成立；并证明第三方重裁包（link 指向被删快照）在交换前被拒。

落点：新增
`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt`
（1181 行，5 例），不改 34 例基线。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s "$S" install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch33/step04-c4-visual.log 2>&1
$A -s "$S" logcat -v threadtime -d > build/device-track-batch33/step04-c4-visual-logcat.txt
```

- 退出码：0
- dex 门：`BackupVisualReferenceInstrumentedTest` + 5 个方法名均在 data 测试包 `classes*.dex`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---:|---|
| `OK (N tests)` | `OK (5 tests)` | 5 |
| Failures / Errors | 0 / 0 | 0 |
| Skipped / Ignored | 0 | 0 |
| 5 个方法逐一出现 | 5/5 | 必须 |
| `BackupSnapshotTrimException` | 0 | 0 |
| `SQLiteException` | 0 | 0 |
| 未被测试捕获的 `foreign_key_check` 异常 | 0（第 5 例的拒绝被 `assertThrows` 捕获） | 0 |
| `FATAL EXCEPTION` / `ANR in ` | 0 | 0 |

## 4. 五例与断言（源码行锚）

| # | 方法 | mask | 核心断言 | 行 |
|---|---|---|---|---|
| 1 | `fullSelectionRoundTripPreservesAllVisualRowsAndReferences` | 15 | 源 = 归档 = 恢复（四表内容逐行相等）；S2–S10 空 | `:87` |
| 2 | `subsetNineKeepsRevisionPinsAndInitialBaselinesOnly` | 9 | `M=0`、`R=1`、`S={S1,S3}`、`P={P1,P2}`、S2/S4 不在 | `:124` |
| 3 | `subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging` | 13 | `M=2`、`S={S1,S2,S3}`、`P={P1,P2}`、S4/P3 不在、被引用快照均 LIVE | `:158` |
| 4 | `archiveAndRestoredDatabaseAgreeOnVisualRowIdentity` | 15 | 归档 vs 恢复按主键内容逐行相等；两库 `integrity_check=ok` | `:197` |
| 5 | `repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap` | 13+重裁 | 抛 `BackupRestoreException` 且消息含 `foreign_key_check`；`countMistakes()` 不变 | `:224` |

检查器覆盖：V-R5/R6/R7 孤儿、V-R8/R9 复合 program、V-R14 被引用快照 LIVE、V-R12/R13 状态与
晋升配对、V-R15 角色域、V-R16 revision≥1、V-R10 预览资产、V-R21 内容唯一键、V-R2 外键、
V-R11 软引用悬空、V-R17 program 身份重派生、V-R18/R19/R20 身份与 state/scene/draw 摘要。
期望集合用 Kotlin 按判据 §1.3 公式从源行计算（`expectedRows`，`:995`），不复制裁剪 SQL。

## 5. 失败 / 跳过 / 异常

无（首轮 2 红已归因并修复；见 `step08-red-attribution.md`）。

## 6. 脱敏与去噪

- 删除：逐用例重复行。
- 保留：5 个方法名、`Tests run`、logcat 四类计数。
- 扫描：0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。80-C4 的 5 个不变量组在设备上成立；与 80-C1 使用**同一份最终 data APK**
`36f79b19…`（备份 34/34 在同包上重跑，见 E-03）。未覆盖：恢复侧是否应新增
`RestoreVisualReferenceRecheck`（研究 §7.2 #1 的待裁定项；本用例只证明检测与拒绝面现状）。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step04-c4-visual.log`、`step04-c4-visual-logcat.txt`
- 生成命令：见 §2
- 行数/大小：79 行 / 208 行
