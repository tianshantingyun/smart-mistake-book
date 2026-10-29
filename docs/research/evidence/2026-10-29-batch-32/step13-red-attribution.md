# E-13 红例归因：`evictionDeletesOnlyUnpinnedStagingIntermediates`（类别 A，未修，未放宽）

| 字段 | 值 |
|---|---|
| evidence-id | E-13 |
| convention-version | 1 |
| batch | 2026-10-29-batch-32 |
| step | 六（判据 §8.2–§8.4） |
| gate-or-kd | 持久化结构门（E-07 的红例） |
| verdict | **FAIL（归因成立，非产品缺陷）**：类别 **A（夹具/门逻辑缺陷）** |
| executed-at | 2026-10-29T11:27:08+08:00（失败采集）；2026-10-29T11:35+08:00（归因） |
| operator | 设备验证轨（subagent，批次 32） |
| head | `0b4bdbbe` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| apk-sha256 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |
| apk-version | （测试包空） |
| exit-code | 宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch32/step05-structure-gates.log`（130 行）/ `…-logcat.txt`（209 行） |
| record-ref | `docs/research/2026-10-29-batch-32-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 失败原样（§8.2 第 1 步：抓原样）

```text
1) evictionDeletesOnlyUnpinnedStagingIntermediates(com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest)
java.util.NoSuchElementException: Key snap-staging-middle is missing in the map.
	at kotlin.collections.MapsKt__MapsKt.getValue(Maps.kt:398)
	at com.tingyun.smartmistakebook.core.database.TutorVisualStructureGatesInstrumentedTest$evictionDeletesOnlyUnpinnedStagingIntermediates$1$1.invokeSuspend(TutorVisualStructureGatesInstrumentedTest.kt:221)
	at ...TutorVisualStructureGatesInstrumentedTest.evictionDeletesOnlyUnpinnedStagingIntermediates(TutorVisualStructureGatesInstrumentedTest.kt:164)

Tests run: 5,  Failures: 1
```

- 第一项目帧：`core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt:221`。
- 失败前该用例已执行到的断言：`deleted == 1`（`:204`）、`findSnapshot("snap-staging-middle") == null`（`:205`）
  都通过——产品路径确实被走到且行为符合预期。

## 2. 四源核对（§8.4）

| 源 | 事实 | 位置 |
|---|---|---|
| 用例种子 | `snap-staging-middle` 是 `STAGING`、`kind=null`、未被 link/pin，且被放进 evict 候选列表 | `TutorVisualStructureGatesInstrumentedTest.kt:170,187-195` |
| 用例自断言 | 候选淘汰数应为 1；`snap-staging-middle` 必须被删 | 同文件 `:204-205` |
| 产品写侧 | `DELETE … WHERE snapshot_id IN (…) AND status=:stagingStatus AND (租约) AND NOT EXISTS(link) AND NOT EXISTS(pin)` | `TutorVisualDao.kt:216-225` |
| 产品读侧 | `checkpointSummaries` 只 `SELECT … FROM tutor_visual_snapshot WHERE program_id=:programId`，**只返回仍存在的行** | `TutorVisualDao.kt:118-141` |

结论：行在 `:204-205` 被断言（且实际）删除后，`checkpointSummaries` 不可能再返回它；
`:221` 的 `summaries.getValue("snap-staging-middle")` 必然抛 `NoSuchElementException`。

## 3. 类别判定（A/B/C/D）

- **A（夹具/门逻辑缺陷）**：成立。失败在测试自身的读侧断言，产品路径（淘汰 SQL）行为正确；两条断言互相矛盾。
- **B（测试期望漂移）**：不成立。没有“权威期望值 vs 实测值”的漂移；被读取的行已按本用例自己的期望删除。
- **C（产品缺陷）**：不成立。`:204`（只删 1 行）、`:205-216`（INITIAL/LATEST/LIVE/pinned/linked 全保留）均通过，
  淘汰语义与 `TutorVisualDao.kt:216-225` 一致。
- **D（设备/旧包）**：排除。APK sha256 为本轮重建后的 `90be7c2b…`，dex 门含本用例；同套件另外 4 例全绿；源 sha 构建前后一致。

## 4. 处置（未执行，归属 T5）

- **不改红例、不删断言、不放宽阈值、不 `@Ignore`**（§8.1）。
- 最小修复建议（供 T5/主会话裁定）：保留 `:221` 的“无引用 = pinned 0”断言，但读取一条**未被列入淘汰候选、
  因而仍存在**的无引用行；或新增一条不入候选集的 STAGING 种子行（如 `snap-staging-keeper`）再读它。
  这样既保留“pin 谓词必须有分辨力”的原意，也不弱化任何现有断言。
- 本轨未修改 `TutorVisualStructureGatesInstrumentedTest.kt`（T5 在飞文件、设备轨零测试源码写入），故无“重建 sha→复跑绿轮”；
  E-07 维持 FAIL。

## 5. 影响

- 结构门本轮 **4/5**；批次 32 验收里“持久化结构门全绿”未成立。
- 不影响 E-02/E-03/E-04/E-05/E-06/E-08/E-09/E-10/E-11：它们各自的包与套件独立执行且全绿。

## 6. 脱敏与去噪

- 删除：无。
- 保留：完整失败行、第一项目帧、四源 `path:line`。
- 扫描：R4 §4.4 → 0 hits。
- 二进制/截图：无。

## 7. 判定

**FAIL + 归因 A**：红例是测试自身“先删后读同一行”的矛盾，不是产品缺陷，也不是环境/旧包。未修复（归属 T5）；未放宽任何断言。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch32/step05-structure-gates.log`（130 行 / 8,728 B）、`…-logcat.txt`（209 行 / 39,299 B）
- 说明：`build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。

## 9. 复核修复轮处置（2026-10-29，非本设备轮）

- **改动**：`TutorVisualStructureGatesInstrumentedTest.kt` 新增一条不入淘汰候选集的 STAGING 种子行
  `snap-staging-keeper`；`:230`（原 `:221`）的“pinned 读侧：无引用 = 0”改读该行。原 `:214`
  对 `snap-staging-middle` 的删除断言、其余 4 例、任何阈值与断言均未改。
- **证据边界**：本次修复**未运行 Gradle/设备**；本文档记录的 `Tests run: 5, Failures: 1` 仍是
  修复前那一轮的读数。修复后的绿轮必须由设备轨重跑 `am instrument -e class …TutorVisualStructureGatesInstrumentedTest`
  产生 `OK (5 tests)`；在重跑前不得把结构门写成 5/5。
