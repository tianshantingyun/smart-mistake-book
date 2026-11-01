# E-02 结构门 5/5（pin 分辨力断言仍有效）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-29-batch-33 |
| step | 判据一 |
| gate-or-kd | 结构门（批次 32 红例修复复跑） |
| verdict | PASS |
| executed-at | 2026-10-29T14:47:24+08:00 |
| operator | 设备验证轨（子代理） |
| head | `6abd807fa73d9084ef629d54015bd4f725c94784` |
| worktree | dirty（仅记录；结构门文件有 3 行 KDoc diff，非本轨改动） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `<repo>/core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0` |
| apk-version | （空） |
| exit-code | 0 |
| raw-log | `build/device-track-batch33/step02-structure-gates.log`（79 行）；logcat 176 行 |
| record-ref | `docs/research/2026-10-29-batch-33-record.md` §4 |
| sanitized | 是（§6） |

## 1. 目的与判据

证明批次 32 的结构门红例（`:221` 读取一条已在 `:215` 断言删除的行）已修，且**未删/未放宽**
pin 分辨力断言后，5 个方法在真机上全部通过。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
$A -s "$S" logcat -v threadtime -d > build/device-track-batch33/step02-structure-gates-logcat.txt
```

- 退出码：0

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---:|---|
| `OK (N tests)` | `OK (5 tests)` | 5 |
| Failures | 0 | 0 |
| Errors | 0 | 0 |
| Skipped / Ignored | 0 | 0 |
| 5 个方法逐一出现 `INSTRUMENTATION_STATUS: test=` | 5/5 | 必须 |
| `NoSuchElementException` | 0 | 0 |
| `SQLiteException` | 0 | 0 |
| `FOREIGN KEY constraint failed` | 0 | 0 |
| `FATAL EXCEPTION` / `ANR in ` | 0 | 0 |
| `INSTRUMENTATION_CODE` | -1 | 非判据（runner 固定 RESULT_OK） |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=messageLinkRejectsForeignKeyProgramAndStagingSnapshots
INSTRUMENTATION_STATUS: test=revisionPinRejectsStagingAndForeignKeyProgramButAcceptsSameProgramLive
INSTRUMENTATION_STATUS: test=evictionDeletesOnlyUnpinnedStagingIntermediates
INSTRUMENTATION_STATUS: test=commitTransactionExecutesDemoteAndEvict
INSTRUMENTATION_STATUS: test=repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned
Tests run: 5,  Failures: 0
OK (5 tests)
```

源码侧 pin 分辨力（构建前逐条 grep，全部 `OK`）：

```text
OK  rawSnapshot(context, databaseName, "snap-staging-keeper"
OK  summaries.getValue("snap-staging-keeper").pinned
OK  summaries.getValue("snap-pinned-staging").pinned
OK  summaries.getValue("snap-linked-staging").pinned
OK  snap-abandoned-staging
OK  repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned
forbidden weakenings (assertTrue(... >= 0) / @Ignore / Assume.): none
```

行锚（`TutorVisualStructureGatesInstrumentedTest.kt`）：5 个 `@Test` 在 `:60/:135/:170/:236/:281`；
`snap-staging-middle` 先断言删除（`:201`、`:215`）；pinned/linked 仍存在（`:221`、`:225`）；
读侧 pinned 1/1/0（`:229-231`）；仓储层负向 `snap-abandoned-staging`（`:287`、`:313`）。

## 5. 失败 / 跳过 / 异常

无。

**整文件纳入登记**：该文件对 HEAD 有 3 行 KDoc diff（`git diff HEAD --stat` = `2 insertions(+), 1 deletion(-)`，
内容为批次 33 T6 的"设备轨尚未执行"回改），属他线改动；本轨未修改该文件。提交时须整文件纳入并注明。
**复核修复轮补记（2026-10-29）**：复核修复员为更正第 5 例的分辨力表述又改了该文件 KDoc
（明确"端到端黑盒不能单独证明 plan 层 pin 过滤"），**未改任何断言**；因此对 HEAD 的 diff 行数已变，
上面 3 行的数字只对设备轨时点成立。5 例绿轮仍对应同一批断言（KDoc 不进编译产物）。

## 6. 脱敏与去噪

- 删除：逐方法重复的输出行（只保留方法名与 summary）。
- 保留：5 个方法名、`Tests run`、logcat 四个 grep 计数。
- 扫描：人工通读，0 命中。
- 二进制/截图：无。

## 7. 判定

PASS。`OK (5 tests)`，0 failures/0 errors/0 skipped；pin 分辨力 6 条源码锚全部存在，
无任何放宽断言的形态；logcat 四类失败签名全 0。本绿轮来自当前树重建的 database 测试包。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch33/step02-structure-gates.log`、`step02-structure-gates-logcat.txt`
- 生成命令：见 §2
- 行数/大小：79 行 + 176 行

## 9. 更正（2026-10-29，落盘复核）

- 原 §4 末段写"5 个 `@Test` 在 `:60/:135/:170/:236/:281`"。复核实测：`@Test` **注解行**是
  `:59/:134/:169/:235/:280`，`:60/:135/:170/:236/:281` 是紧随其后的**方法声明行**（`    fun …`）。
  原句想表达的 5 个用例位置成立，只是注解行与方法行混称；判定不变、断言未改、未删任何 pin 断言。
- 复核命令：`grep -n '@Test'` 与 `grep -n '    fun ' `（工作树版本）。其余行锚
  （`:201/:215/:221/:225/:229-231/:287/:313`）逐条复核与原文一致。
