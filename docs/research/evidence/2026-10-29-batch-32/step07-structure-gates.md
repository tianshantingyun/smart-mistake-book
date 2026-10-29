# E-07 持久化结构门 `TutorVisualStructureGatesInstrumentedTest`（FAIL 1/5）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 三（判据 §5.1；红例归因 §8） |
| gate-or-kd | 持久化结构门（pin / link / evict / 事务） |
| verdict | **FAIL**：`Tests run: 5, Failures: 1` |
| executed-at | 2026-10-29T11:27:05–11:27:08+08:00（`Time: 1.927`） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据；判据 = summary） |
| raw-log | `build/device-track-batch32/step05-structure-gates.log`（130 行）/ `…-logcat.txt`（209 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

构建前复读 **5** 例（批次 32 T5 新增“仅因 pinned 才保留”的有分辨力用例；判据 §5.1 的 “4” 是 09:58 快照，按 §1.3 以复读为准）：

| 方法 | 结果 |
|---|---|
| `messageLinkRejectsForeignKeyProgramAndStagingSnapshots` | PASS |
| `revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive` | PASS |
| `evictionDeletesOnlyUnpinnedStagingIntermediates`（`:164`） | **FAIL** |
| `commitTransactionExecutesDemoteAndEvict`（`:226`） | PASS |
| `repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned`（`:271`，T5 新增） | PASS |

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch32/step05-structure-gates.log
$A -s $S logcat -v threadtime -d > build/device-track-batch32/step05-structure-gates-logcat.txt
```

- `EXIT=0`；`Time: 1.927`。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 期望 5（构建前复读） |
| failures | 1 | 期望 0 |
| `INSTRUMENTATION_STATUS_CODE: 0` | 4 | 4 例通过 |
| `INSTRUMENTATION_STATUS_CODE: -2` | 1 | 失败 1 例 |
| `INSTRUMENTATION_STATUS: stack=` | 1 | 仅失败例 |
| logcat 9 类签名 | 0 | 见 E-12 |

## 4. 关键输出（有界）

```text
There was 1 failure:
1) evictionDeletesOnlyUnpinnedStagingIntermediates(com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest)
java.util.NoSuchElementException: Key snap-staging-middle is missing in the map.
	at kotlin.collections.MapsKt__MapsKt.getValue(Maps.kt:398)
	at com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest$evictionDeletesOnlyUnpinnedStagingIntermediates$1$1.invokeSuspend(TutorVisualStructureGatesInstrumentedTest.kt:221)
	at ...TutorVisualStructureGatesInstrumentedTest.evictionDeletesOnlyUnpinnedStagingIntermediates(TutorVisualStructureGatesInstrumentedTest.kt:164)

Tests run: 5,  Failures: 1
```

## 5. 失败 / 跳过 / 异常

1 例失败（非崩溃、非跳过）：`evictionDeletesOnlyUnpinnedStagingIntermediates`。归因与处置见 E-13（类别 **A**：用例自身前后矛盾，产品路径未判错）。**本轨未改该测试文件**（归属 T5；设备轨零测试源码写入），因此本轮结构门仍为 FAIL。

## 6. 脱敏与去噪

- 删除：无。
- 保留：完整失败行、项目帧、`Tests run:` 行。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

FAIL：5 例中 4 绿 1 红。红例为用例内部矛盾（先断言行已删除，再读取该行的 pinned 投影），不是产品缺陷；未放宽任何断言、未 `@Ignore`、未改红例。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step05-structure-gates.log`（130 行 / 8,728 B）、`…-logcat.txt`（209 行 / 39,299 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
