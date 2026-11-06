# WS6 错因端到端（批次 41，items 38–42）

> 范围：`plan-2026-11-03-batch-41.md` §2 WS6（38–42）。本文件只记录**载体与判据**；
> 本会话未运行 Gradle、未上设备、未做 git 写操作（纪律：门禁阶段单独运行）。
> 因此“在树”不等于“编译/执行通过”，执行状态见 §5。

## 0. 落点

| item | 落点 | 类型 |
|---|---|---|
| 38 组合用例 | `core/data/src/test/kotlin/.../study/ErrorCauseChainTest.kt`（6 例） | 新增 |
| 39 设备 DAO 扩展 | `core/database/src/androidTest/kotlin/.../ErrorCauseLedgerInstrumentedTest.kt:155` | 扩展 |
| 40 撤销回滚断言 | 同上 `:268`（测试侧触发器注入） | 扩展 |
| 41 生产装配接缝 | `core/data/src/main/kotlin/.../study/ErrorCauseBookingService.kt` | 新增 |
| 42 文档回写 | 本文件 + 下方 §6 待并入批次 41 记录 | 文档 |

牵动面（改一处先枚举）：

1. `core/data` main：新类只依赖 `ErrorCausePort` 的结果枚举与同包三个既有构件，未改
   extractor/gate/retractor/port/DAO；无生产调用点（`grep ErrorCauseBookingService`
   只命中本类与测试）。
2. `core/data` test：新测试类独立，既有三个单件测试零改动。
3. `core/database` androidTest：新增 2 个 `@Test`，不改生产 DAO/实体/迁移，不改 schema。
4. 热区：`feature/*`、`core/ui`、`core/export`、`core/domain`、`settings.gradle.kts`、
   Application/Root 全部未触碰。
5. 备份/清单：无表、无版本、无 `SmbkSelectionPlan` 改动。
6. 设备：用例依赖既有 `StudyDatabaseFactory.openInMemory` 与 `errorCauseDao()`；
   item 40 的失败注入只在测试事务外建/删触发器，生产代码零注入点。

## 1. 组合用例（item 38）

`ErrorCauseChainTest` 用真实 `ErrorCauseExtractor` 的输出驱动门与落账器，假端口按真 DAO
的判定顺序实现（先查 observation 状态、再查 `active_key` 占用），但不模拟 SQL 事务。

| 行 | 断言 |
|---|---|
| `:49` | 默认关闭接缝：提取器产出 `PROPOSED` 候选；`proposeAndBook` 返回 `GATE_REJECTED`，0 observation / 0 binding / 0 attempt 写 |
| `:77` | 认证门放行：`BOOKED`；observation `PROPOSED→ACCEPTED`；binding `ACTIVE`，`active_key == scope.activeKey()`，`AUTO_GATE` + `d1c-v1`；全表 1 条 ACTIVE |
| `:110` | 提取器自己产出的 E03（CARELESS/REDO）被认证门按硬规则拒绝：`GATE_REJECTED`，0 写入 |
| `:139` | 同 scope 第二条候选：门放行但账本 `CONFLICT`；被拒候选留 `PROPOSED`，无第二条 binding，仍 1 条 ACTIVE |
| `:167` | 用户撤回：`RETRACTED` observation（`supersedes` 指向原证据、`SELF_REPORT`、`USER`）+ binding `VOIDED`、`active_key=NULL`、`ended_at=7000`；读侧为空；binding 行不删除；原 observation 仍 ACCEPTED |
| `:217` | 撤回迟滞：`retractedRecently=true` 时 `GATE_REJECTED` 零写回；新独立负事件（attempt-2/message-2）可 `BOOKED`，仍只有 1 条 ACTIVE |

## 2. 设备 DAO 用例（item 39）

`candidateRejectedAndRetractedStatesLeaveNoActiveLabelBehind`（`ErrorCauseLedgerInstrumentedTest.kt:155`）：

- 候选：`appendObservation` 后 `readActiveBinding == null`、`activeBindingTotal == 0`、
  observation 为 `PROPOSED`；
- 落账：`APPLIED` 后读侧返回同一 binding，全表 1 条 ACTIVE；
- 被拒：同 scope 第二条 `acceptObservationAndActivateBinding` 返回 `CONFLICT`；
  `bindingCount("binding-rejected") == 0`、被拒 observation 仍 `PROPOSED`、全表仍 1 条 ACTIVE；
- 撤销：`readActiveBinding == null`、`activeBindingTotal == 0`、binding `VOIDED` 且
  `active_key IS NULL`、撤回行保留、被拒候选不被连带删除/升级；
- 活键释放：撤销后同一被拒候选可落账为 `binding-after-retract`，且仍只有 1 条 ACTIVE。

## 3. 撤销不留半行（item 40）

`aFailingVoidRollsBackTheRetractionRowAndKeepsTheBindingActive`（`:268`）：

1. 先落账一条 ACTIVE；
2. 通过 `RoomDatabase.useWriterConnection { executeSQL(...) }` 建测试触发器
   `fail_error_cause_void`：`BEFORE UPDATE OF state ON error_cause_binding WHEN NEW.state='VOIDED'`
   → `RAISE(ABORT, ...)`；
3. 调 `retractActiveBinding`：第一步 `INSERT` RETRACTED 已执行，第二步 UPDATE 失败；
4. 断言调用抛出（不是静默 `APPLIED`）、RETRACTED 行不存在、binding 仍 `ACTIVE`、
   `active_key` 与 `ended_at` 均回滚、全表 1 条 ACTIVE；
5. `DROP TRIGGER` 后同一撤回 `APPLIED`，读侧为空、0 条 ACTIVE——证明失败只来自注入。

为什么必须设备：JVM 假端口没有事务；既有 batch-40 设备证据是
`ErrorCauseLedgerInstrumentedTest` **1 例 `OK (1 test)`**（`step02b-schema73-migration-backup.md:27`），
该例含**改判**路径的回滚断言，但没有覆盖**撤回**路径的"第一步已写、第二步失败"；本用例补这一缺口。

API 依据：`androidx.room3.RoomDatabaseKt.useWriterConnection` 与
`androidx.room3.TransactorKt.executeSQL(PooledConnection, String)` 均为 public、
无 opt-in 注解（本会话对 `room3-runtime-android:3.0.0` 的 `javap` 实读）。

## 4. 生产装配接缝（item 41）

`ErrorCauseBookingService`（`:34`）只暴露 `proposeAndBook(input, corroborationCount,
retractedRecently, bindingId, decidedAt)`（`:45`）：

- 候选只能由内部 `ErrorCauseExtractor` 从 `ErrorCauseExtractionInput` 产生，调用方
  无法把任意 `ErrorCauseCandidate` 直接送进落账器；
- 门在构造时注入，默认 `ErrorCauseGate()`（`certified=false`）；`isGateClosed` 供装配诊断；
- 先判门只为区分 `GATE_REJECTED` 与账本 `CONFLICT`；落账仍走
  `retractor.bookIfAllowed`（`:57`）在事务边界内再判一次，接缝本身不放宽门；
- 结果四态：`NO_CANDIDATE` / `GATE_REJECTED` / `BOOKED` / `CONFLICT`。

判据（评审用，类 KDoc `:27` 同步）：

- C1 生产自动落账唯一调用点是 `proposeAndBook`；`grep -rn "bookIfAllowed(" -- core app feature`
  只应命中 `ErrorCauseRetractor` 定义、本接缝与测试（本会话实跑命中 5 处：
  `ErrorCauseRetractor.kt:42` 定义、`ErrorCauseBookingService.kt:57`、`ErrorCauseChainTest.kt:170`、
  `ErrorCauseRetractorTest.kt:33/50`；`feature`/`app` 零命中）；
- C2 生产构造不得显式传 `certified = true`；认证状态只能来自 P0–P5 证据（当前不存在）；
- C3 读侧只认 `readActiveBinding`（`ACTIVE` 且来源 observation `ACCEPTED`）；
- C4 撤回后同 scope `active_key` 为空，重新落账须等新独立负事件；
- C5 接缝依赖闭包无 attempt/review/projection 写口（结构性，见类 KDoc `:23`）。

未做（本项边界）：未在 `app`/`feature` 接线，未新增设置/持久化门状态——均在热区，
需主会话裁定。

## 5. 执行状态（本会话）

| 检查 | 命令 | 结果 |
|---|---|---|
| 热区改动 | `git status --porcelain -- feature/ core/ui/ core/export/ core/domain/ settings.gradle.kts` | 本会话未改热区：输出的 5 `M`（`core/domain/.../TutorJudgedReview.kt`、`core/export` 3 测试、`core/ui/.../Theme.kt`）与 9 `??`（5 个 feature 锁、`feature/tutor/.../nul`、3 个 core 锁）均为批次 40 基线既有、非本会话产物 |
| 接缝调用面 | `grep -rn "bookIfAllowed(" --include=*.kt core app feature` | 仅 retractor 定义 + seam + 测试（输出见 §4） |
| 空白/冲突标记 | `git diff --check -- core/database/.../ErrorCauseLedgerInstrumentedTest.kt` | exit=0 |
| `useWriterConnection`/`executeSQL` API | `javap` room3-runtime 3.0.0 | public、无 opt-in |
| JVM 单测 | 未运行 | **UNVERIFIED**（纪律：门禁阶段单独跑） |
| androidTest 编译 | 未运行 | **UNVERIFIED** |
| 设备执行 | 未运行 | **UNVERIFIED**（无 APK sha/源快照绑定） |

门禁阶段建议命令（由门禁轨执行并落原始日志）：

```bash
./gradlew --console=plain :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.study.ErrorCause*"
./gradlew --console=plain :core:database:assembleDebugAndroidTest
# 设备（单包，禁止 :app:connectedLocalFirstDebugAndroidTest）：
# APK 路径以 assembleDebugAndroidTest 实际产物为准（模块无 flavor）。
adb install -r -t core/database/build/outputs/apk/androidTest/debug/core-database-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.database.ErrorCauseLedgerInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

设备绿必须绑定：执行时源快照（sha256 清单）+ 实际安装 APK 的 sha256 + 设备序列号。

## 6. 文档回写要点（item 42）

并入 `2026-11-03-batch-41-record.md` WS6 行：

- 38：`ErrorCauseChainTest` 6 例（组合：候选/默认拒绝/放行/CARELESS 硬拒/唯一活键/撤销/迟滞）。
- 39：设备 DAO 用例 1→3 例；新增用例名与断言见 §2。
- 40：撤回路径回滚用例；测试触发器注入；需设备执行才有结论。
- 41：接缝类 + C1–C5 判据；未接 `app`/`feature`（热区）。
- 42：无新增 KD；KD-75/76/isolation 不受影响；WS6 无 schema/备份改动。
- 未验证：全部 JVM/设备执行（本会话零 Gradle/零设备）。

## 7. 风险与边界

- item 38 的假端口不模拟事务，唯一 ACTIVE 由假端口的 `active_key` 判定模拟；事务性由
  item 39/40 的真库用例承担。
- item 40 依赖 SQLite 触发器在 Room 事务第二步抛错；若门禁阶段实测 `RAISE(ABORT)`
  被 Room 吞掉/转换，需改为 `ROLLBACK` 或换注入点，并在记录中如实登记。
- 接缝未接线，生产仍无自动落账路径（门默认关闭）；这是当前产品口径，不是缺口回退。
