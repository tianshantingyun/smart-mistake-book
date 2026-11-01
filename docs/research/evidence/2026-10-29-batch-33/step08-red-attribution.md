# E-08 红例归因：C4 首轮 2 红（A 类，已修并复跑绿）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据 §6（先 A/B，再 D，最后 C） |
| gate-or-kd | KD-80 80-C4 |
| verdict | A 类（测试夹具缺陷）；修复后同命令复跑 PASS |
| executed-at | 红轮 2026-10-29T14:57±（raw log 被同路径绿轮覆盖）；绿轮 14:58:49+08:00 |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（新增 C4 测试文件） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | 红轮 data APK `c73233a9…`（**仅记录到 sha 前缀**；APK 本体未保留，完整 sha 不可考）；绿轮 `36f79b19…` |
| apk-sha256 | 绿轮 `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` |
| apk-version | （空） |
| exit-code | 红轮 `am instrument` 进程 0（JUnit 失败在结果流）；绿轮 0 |
| raw-log | 绿轮 `build/device-track-batch33/step04-c4-visual.log`（79 行）；红轮 raw log 已被覆盖 |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 红轮原样（工单转录）

```text
INSTRUMENTATION_STATUS: test=subsetNineKeepsRevisionPinsAndInitialBaselinesOnly
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: mask 9：恢复后快照集合必须与归档一致
expected:<[visual-snapshot-4982ae168c7b37311802c5d30de5e7b7,
           visual-snapshot-b202363eb4ef79570e5b7983320e7f09]>
but was:<[visual-snapshot-4982ae168c7b37311802c5d30de5e7b7,
          visual-snapshot-5eb1c5137254f8783c50700e72e53406,
          visual-snapshot-b202363eb4ef79570e5b7983320e7f09,
          visual-snapshot-b7314a77b079ddb97f5b0118389cf08f]>
  at …BackupVisualReferenceInstrumentedTest$subsetNine…(BackupVisualReferenceInstrumentedTest.kt:143)
…
INSTRUMENTATION_STATUS: test=subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: mask 13：恢复后快照集合必须与归档一致
expected:<[4982…, 5eb1…, b202…]> but was:<[4982…, 5eb1…, b202…, b731…]>
  at …BackupVisualReferenceInstrumentedTest$subsetThirteen…(BackupVisualReferenceInstrumentedTest.kt:182)
…
Tests run: 5,  Failures: 2
```

- 归档侧断言（`M=0` / `S={S1,S3}` / mask 13 `M=2` / S4 不在）**全部先绿**；只有"恢复后"三条红。
- 全选（15）与归档/恢复逐行相等、C4 第 5 例拒绝包也绿。

## 2. 四源核对

| 源 | 事实 | 引用 |
|---|---|---|
| 测试夹具 | `setUp` 使用 `"batch33-c4-visual-${System.nanoTime()}.db"`（非默认库） | 红轮时 `BackupVisualReferenceInstrumentedTest.kt` 原 `setUp` |
| 恢复实现 | `doRestore` 固定 `context.getDatabasePath(StudyDatabaseFactory.DEFAULT_DATABASE_NAME)` | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:458` |
| 同类既有用例 | `BackupSelectionRestoreInstrumentedTest` 用 `StudyDatabaseFactory.DEFAULT_DATABASE_NAME` 并通过 | `core/data/src/androidTest/.../BackupSelectionRestoreInstrumentedTest.kt:58` |
| 产品行为 | 恢复换掉默认库；测试读的是未被替换的**另一个**库（源库 4 行），故"恢复后"恒等于源 | 红轮结果：恢复集合 = 源集合 |

## 3. 归因

**A 类（夹具/门逻辑缺陷）**。产品路径未被错误执行：恢复确实换了默认库，只是测试用自定义库名，
导致读侧读到源库。期望值（裁剪后 2/3 行）本身正确（归档侧已证明）。

- 不是 B：测试期望与权威值一致（裁剪公式 = 归档实测）。
- 不是 D：dex 有 C4 符号，APK 由当前树重建，设备 API/ABI/页大小正确。
- 不是 C：产品在默认库上的行为与既有 34 例一致。

## 4. 处置与复跑

- 改动：`BackupVisualReferenceInstrumentedTest.setUp` 改用
  `StudyDatabaseFactory.DEFAULT_DATABASE_NAME`（文件 `:70-76`），并加注释说明恢复路径固定默认库。
- 产品源零改动；未删/未放宽任何断言（期望集合仍由 `expectedRows` 公式计算）。
- 重建：`./gradlew :core:data:assembleDebugAndroidTest` → `BUILD SUCCESSFUL`；
  源快照 before/after 均为 `SOURCE STABLE`；新 data APK sha `36f79b19…`。
- 同命令复跑：`OK (5 tests)`（`step04-c4-visual.log`，14:58:49），logcat 四项 grep
  （`BackupSnapshotTrimException` / `SQLiteException` / `foreign_key_check` / `FATAL|ANR`）均为 0。

## 5. 证据缺口

- 红轮 raw log 被绿轮同路径覆盖，无法给出 `build/...` 行号；本文件 §1 为控制台转录。
  可复跑方式：把 `setUp` 的库名临时改回自定义值、重建并执行同一命令即可复现（不建议在干净树上重放）。

## 6. 脱敏与去噪

- 删除：重复的 5 行短栈（只留首项目帧）。
- 保留：两条失败消息完整、首项目帧、`Tests run`。
- 扫描：0 命中。
- 二进制/截图：无。

## 7. 判定

A 类，已修并复跑绿。C4 的 5/5 判定见 E-04。
