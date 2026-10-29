# 批次 33 基线核对（2026-10-29）

> **性质**：只读基线。本轨**未运行 Gradle、未 assemble、未 install、未执行任何 JVM/仪器化测试**；
> 设备面只用 `adb devices` / `adb shell getprop`（只读）；**零 git 写**（本次新增的文件是本轨唯一产物）。
> 结论只对**本轮实读/实跑**的现树成立；未实跑的项在 §11 单列 `UNVERIFIED`。
>
> 计划来源：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md` §0（下称"计划 §0"）。

## 0. 结论速览：与计划 §0 不符的地方（先看这一节）

| # | 计划 §0 的说法 | 现树实测 | 判定 |
|---|---|---|---|
| A | 结构门 5 例 1 红：`TutorVisualStructureGatesInstrumentedTest.kt:221` 先断言 `snap-staging-middle` 已删（`:205`），又用 `getValue` 读同一行 | **现树已不是这样**：读侧改读 `snap-staging-keeper`（`:187`、`:230`），`:214` 仍是 `assertNull("snap-staging-middle")`；该文件对 HEAD **零 diff**，修复在 `9d91e6b0`（2026-10-29 **13:05:17** 提交，HEAD 祖先，`git merge-base --is-ancestor` 实测 true） | **不符（计划 §0 过期）**：任务 1 的"改读哪一行"**已落码**，只剩设备重跑 |
| B | stage 路径预算两层口径不一致（plan 按淘汰后净值、事务按全量） | **现树已统一**：`stageTutorVisualSnapshot` 在同一事务内先执行 `evictUnpinnedCheckpoints`（`RoomStudyDatabase.kt:1963-1971`），再按全量重判（`:1975-1982`）；stage 命令携带淘汰集（`TutorVisualCommit.kt:181-198`）、仓储层已传入（`TutorVisualCommitRepository.kt:271-284`） | **不符（计划 §0 过期）**：任务 3 只剩"净值也超 → 拒绝"的负例 |
| C | `sceneSha256` 未校验与内容一致 | **仍成立**：`ensureTutorVisualSnapshot` 只 `require` state/draw 两条（`RoomStudyDatabase.kt:2259-2264`），`scene_sha256` 无内容校验 | 与 §0 一致 |
| D | digest 负向用例只断言快照数不判事务性 | **仍成立**：两条负向只断言 `IllegalArgumentException` + `countSnapshots == 0`（`TutorVisualCommitInstrumentedTest.kt:160-168`、`:202-210`），无 `countPrograms == 0` | 与 §0 一致（同文件 `:112-125` 的 id 复派生用例两条都断言，模式现成） |
| E | N13 未覆盖真正的 1 px 盲集 | **仍成立**：N13 是 **+2 px**（`VisualDrawOpsParityInstrumentedTest.kt:394-397`），注释自写 `bboxDev=5`（`:377`） | 与 §0 一致 |
| F | `:317-318` 代数注释错误 | **仍成立**：原文"该值与基线精度无关（`b² = a² + 2·vx + 1 ⇒ d(+1) = b`）"仍在（`:317-318`），是循环定义不是推导 | 与 §0 一致 |
| G | 设备：仓库 **8 例**、结构门 5 例 4 绿 1 红 | **现树仓库 9 例**（第 9 例 `stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`，`:424`），设备跑的 8 例对应**旧源**；结构门设备结果 `Tests run: 5, Failures: 1` 对应**修复前源** | 计数需在设备轨构建前复读（批次 32 判据 §1.3 规则不变） |
| H | 计划 §1 任务 2（KD-80 C4 补断言） | **仍缺**：`BackupRestoreInstrumentedTest.kt` 对 `tutor_visual` 的引用 **0** 次（`grep -c` 实测；文件里唯一的"visual"是无关的 `insertFigureGenerationAuditFixture` import，`:22`） | 与 §0 一致 |

> 直接后果：**任务 1 若按计划原话再改一次 `:221`，会改成对已存在行的自我矛盾改动**——现树 `:222-225` 读的是 `snap-linked-staging`（在淘汰候选集 `:204` 内，但被 link `NOT EXISTS` 保住）、`:228-230` 读的是 `snap-pinned-staging`/`snap-linked-staging`/`snap-staging-keeper`，三行都应存在。任务 1 实际只剩"设备重跑取 `OK (5 tests)`"。

---

## 1. HEAD / origin/main / 工作树 / schema

### 1.1 提交面

| 项 | 实测 | 命令 |
|---|---|---|
| `HEAD` | `6abd807fa73d9084ef629d54015bd4f725c94784`（`docs(tools): 批次32 AUD-08 WAL 溯源登记…`，2026-10-29 13:08:19 +0800） | `git rev-parse HEAD`、`git log -1 --format="%h %ci %s" HEAD` |
| `origin/main`（本地引用） | `0b4bdbbea918ba6b8c8e1f3b190b8be502ec627a`（`docs(4e.2): 批次31 总路线滚动行 + 决策台账…`，2026-10-29 08:36:23 +0800） | `git rev-parse origin/main`、`git log -1 origin/main` |
| 相对关系 | `origin/main...HEAD` = **0 / 11**（落后 0、领先 11） | `git rev-list --left-right --count origin/main...HEAD` |
| 结构门修复提交在 HEAD 内 | `9d91e6b0` 是 HEAD 祖先 | `git merge-base --is-ancestor 9d91e6b0 HEAD` → true |

即：**HEAD 未推送**，与计划 §0 "推送 443 后台重试"一致（本轨不推送）。

### 1.2 工作树计数

- 总脏条目 **687**：`M` **215** / `??` **472**（`git status --porcelain | wc -l`；`awk '{print $1}' | sort | uniq -c`）。
- 按顶层目录（`git status --porcelain | awk '{print $2}' | cut -d/ -f1 | sort | uniq -c`）：
  `feature` 134、`core` 72、`docs` 61、`app` 28、`tools` 20、`knowledge-production` 12、`settings.gradle.kts` 1、
  `settings-gradle.lockfile` 1，以及若干 `tmp*`/`out`/`nul` 类垃圾条目（各 1）。

### 1.3 DB schema（应 71）—— 三处同值，成立

| 证据点 | 实测 | 命令 |
|---|---|---|
| 生成物 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` 存在，`"identityHash": "714c0ecb85ee5edf74f0013740148138"` | `grep -o '"identityHash": *"[^"]*"' …/71.json`；目录内最高版本文件为 `71.json` |
| 版本常量 | `STUDY_DATABASE_VERSION = 71`（`core/database/src/main/kotlin/.../StudyDatabase.kt:139`，用于 `:242` 的 `version =`） | `grep -rn "STUDY_DATABASE_VERSION" core/database/src/main/kotlin` |
| 备份常量 | `SmbkSelectionPlan.SCHEMA_VERSION = 71`（`:44`）、`IDENTITY_HASH = "714c0ecb85ee5edf74f0013740148138"`（`:51`） | `grep -rn "SCHEMA_VERSION\|IDENTITY_HASH" --include=SmbkSelectionPlan.kt` |
| 生成的 Impl（build 内旁证） | `core/database/build/generated/ksp/debug/kotlin/.../StudyDatabase_Impl.kt` 含同一 hash | `grep -rln "714c…8138" build core/*/build` |

结论：`71.json` / `StudyDatabase_Impl.kt` / `SmbkSelectionPlan` **三处同值**，计划 §0 该条成立（本轨未构建，Impl 是既有 build 产物旁证）。

### 1.4 请求 schema（模型请求）

- 仓内**无**版本常量（`grep -rn "REQUEST_SCHEMA\|requestSchemaVersion\|TOOL_SCHEMA_VERSION" --include=*.kt core/ app/` → 0 命中）。
- 文档与 KDoc 一致标为 **18**：`core/model/src/main/kotlin/.../TutorTasks.kt:255`（`4E WG-04b（请求 schema 18）`）、
  `core/model/src/main/kotlin/.../TutorVisualContext.kt:7`、`docs/agent-first-refactor-decisions-2026-09-23.md:3020/:3083/:3149`。
- 本批无任何请求 schema 变更计划；基线记 **18（文档口径，无代码常量可钉）**。

---

## 2. 热区快照与开工即脏清单

热区定义取批次 32 判据 §1.5（`docs/research/2026-10-29-batch-32-device-criteria.md:134-137`）。

| 热区/路径 | 脏条目 | 说明 |
|---|---|---|
| `feature/` | 134 | 全部他线在飞（capture/library/review/profile/tutor） |
| `core/ui/` | 35 | 全目录禁改 |
| `core/export/` | 6 | `build.gradle.kts` + runner + 3 个 JVM 测试 |
| `core/domain/` | 5 | 4 个 main 源 |
| `core/markdown/` | 1 | — |
| `docs/current/` | 7 | `release-gates.md`、`data-schema.md` 等 |
| `app/` | 28 | Root/Destinations/SecondaryScreens 等开工即脏面 |
| `settings.gradle.kts` / `.gitignore` | 1 / 1 | 均 `M`（禁改） |
| `tools/kb/` | 0 | 干净 |

### 2.1 `core/data`（14 条）

`M`：`androidTest/.../backup/BackupRestoreInstrumentedTest.kt`、`backup/DeviceMigrationDrillInstrumentedTest.kt`、
`capture/BatchImportRepositoryInstrumentedTest.kt`、`capture/SplitImportCommitReceiptInstrumentedTest.kt`、
`capture/SplitImportReceiptReconciliationInstrumentedTest.kt`、`model/ModelTaskRepositoryInstrumentedTest.kt`、
`study/StudySubmissionWithoutFixtureInstrumentedTest.kt`；`main/.../mistake/RoomMistakeDetailRepository.kt`、
`main/.../study/RoomTutorToolRunner.kt`；`test/.../knowledge/GoldenRetrievalJvmTest.kt`、
`test/.../model/OpenAiNativeToolsProtocolTest.kt`、`test/.../model/TutorToolRequestDualParseTest.kt`、
`test/.../visual/VisualDrawOpsParserTest.kt`；`?? core/data/gradle.lockfile`。

### 2.2 `core/database`（10 条）

`M`：`androidTest/.../LibraryFtsCountPathInstrumentedTest.kt`、`LibraryFtsCountPathScale50kInstrumentedTest.kt`、
`LibraryFtsRankingPathInstrumentedTest.kt`；`main/.../RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、
`dao/BatchImportDao.kt`、`dao/PendingCaptureDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt`；
`?? core/database/gradle.lockfile`。

> 两个 `gradle.lockfile` 是 KD-72 口径下的未跟踪锁文件，按本批硬约束（锁文件不提交）**不得纳入**。

### 2.3 本批目标文件全部干净（对 HEAD 零 diff）

`git status --porcelain -- <以下 10 个路径>` **输出为空**：

`core/database/.../TutorVisualStructureGatesInstrumentedTest.kt`、`.../TutorVisualCommitInstrumentedTest.kt`、
`core/database/src/main/.../RoomStudyDatabase.kt`、`core/data/.../TutorVisualCommitRepositoryInstrumentedTest.kt`、
`.../VisualDrawOpsParityInstrumentedTest.kt`、`.../VisualDrawOpsParityMetrics.kt`、
`core/data/src/main/.../visual/TutorVisualCommitRepository.kt`、`.../visual/VisualCheckpointRetention.kt`、
`docs/known-defects.md`、`docs/research/2026-10-29-batch-32-device-criteria.md`。

即本批的六处落点**开工即净**，改动可整文件归属本批，不与在飞线混写。

---

## 3. 结构门现树原文（`:190-230`）与 pin 分辨力断言

用例 `evictionDeletesOnlyUnpinnedStagingIntermediates`（`TutorVisualStructureGatesInstrumentedTest.kt:168-232`）现树逐行：

| 行 | 原文要点 |
|---|---|
| `:173-187` | 种子 7 行：`snap-initial`(LIVE,INITIAL)、`snap-latest`(LIVE,LATEST)、`snap-staging-middle`(STAGING,null)、`snap-live-middle`(LIVE,null)、`snap-pinned-staging`(STAGING,null,+题面 pin)、`snap-linked-staging`(STAGING,null,+消息 link)、`snap-staging-keeper`(STAGING,null,无引用) |
| `:189-194` | `demoteLatestCheckpoints(P1, listOf("snap-latest"), "LATEST")` 断言命中 `1`；`findSnapshot("snap-latest")?.checkpointKind == null` |
| `:196-211` | `evictUnpinnedCheckpoints(candidates = [initial, latest, staging-middle, live-middle, pinned-staging, linked-staging], latestKind="LATEST", stagingStatus=STAGING, committingOwnerRequestId="owner-batch31", leaseExpiredBeforeEpochMillis=Long.MIN_VALUE)` |
| `:213-214` | `assertEquals("只有未固定、非 INITIAL、STAGING 的中间态被删", 1, deleted)`；`assertNull("STAGING 中间态被回收", findSnapshot("snap-staging-middle"))` |
| `:215-217` | `assertNotNull`：`snap-initial`（INITIAL 永不淘汰）、`snap-latest`（只降级不删）、`snap-live-middle`（LIVE 不当缓存删） |
| `:218-225` | `assertNotNull`："被题面固定的 STAGING 行保留（pin 谓词必须有分辨力）"=`snap-pinned-staging`；"被消息当前指针引用的 STAGING 行保留（link NOT EXISTS 必须有分辨力）"=`snap-linked-staging` |
| `:227-230` | `checkpointSummaries(P1)` 读侧：`pinned-staging → 1`、`linked-staging → 1`、`staging-keeper → 0` |

**与计划 §0 描述的差异**：计划称 `:221` 用 `getValue` 读 `snap-staging-middle`。现树 `:221` 是 `assertNotNull("…pin 谓词…", findSnapshot("snap-pinned-staging"))` 的收尾括号；
`getValue(...)` 出现在 `:228-230`，键分别是 `snap-pinned-staging` / `snap-linked-staging` / `snap-staging-keeper`，三行都不在删除结果里。
`git log -S 'snap-staging-keeper'` 与 `git log -S 'summaries.getValue("snap-staging-middle")'` 均指向 `9d91e6b0`（后者在 `5fee43c5` 引入、`9d91e6b0` 移除）。

**时点解释（为什么计划 §0 会写成"1 红"）**：设备 E-07（`docs/research/evidence/2026-10-29-batch-32/step07-structure-gates.md` = `Tests run: 5, Failures: 1`）
跑的是 **11:14–11:17 构建的 APK**；修复落在 `3087c8ae`（**13:04:22**）与 `9d91e6b0`（**13:05:17**）两笔提交，HEAD（`6abd807f`，13:08:19）已含两者。
批次 32 记录/判据写于这两笔提交之前，故其"红例在 `:221`"是**历史陈述**，不是现树事实（`known-defects.md:3001` 的"修复后的绿轮尚未重跑"则是现树事实）。

### 3.1 pin 分辨力断言位置

- **DA 层**：`:218-221`（题面 pin 保留）、`:222-225`（link 保留）；读侧 `:228-229`。
- **仓储 plan→command→SQL 层**：`repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned`（`:279-315`），
  两条种子行形状完全相同（STAGING + LATEST + 同一非本次 owner + 租约已过期，`:285-296`），断言在
  **`:306-309`**（"仅因题面 pinned 才保留"）与 **`:310-313`**（"同形状但未被引用的 STAGING 行必须被淘汰（证明 pin 谓词不是恒真）"）。
  这是"不许删的分辨力断言"本体，本批任何修复**不得删/不得放宽**这两条。

---

## 4. 预算的两层口径

### 4.1 事务外（plan，按"淘汰后净值"）

- 可淘汰集：`!pinned && status == STAGING && (kind == null || kind == LATEST)`（`core/data/src/main/.../visual/VisualCheckpointRetention.kt:187-192`）。
- 净值：`retainedBytes = existingBytes - evictableBytes + incomingStoredBytes`（`:194`）；per-program 门 `:202-208`；
  全局门 `globalAfter = globalStoredBytes - evictableBytes + incomingStoredBytes`（`:209-215`）。
- 调用方：`TutorVisualCommitRepository.planCheckpoint`（`:415-473`），已存在且 LIVE → 空 demote/evict（`:429-437`）；
  STAGING 既有行从 `existing` 剔除后重跑（`:449-451`）。

### 4.2 事务内（全量重判）

- **commit**：demote（`:2056-2062`）→ evict（`:2063-2071`）→ `enforceVisualCheckpointBudget`（`:2073-2083`，`snapshotWasAlreadyLive` 为 false 时）；
- **stage**：ensure snapshot（`:1958`）→ evict（`:1963-1971`）→ 全量重判（`:1975-1982`，`status != LIVE` 时）；
- `enforceVisualCheckpointBudget`（`:2175-2197`）读 `programCheckpointStoredBytes(programId)` 与 `totalCheckpointStoredBytes()`，
  **是淘汰后的全量总和，不做净值扣减**。
- 两层咬合点：plan 的净值与事务的全量在**同一份淘汰集被执行后**相等，所以 stage 必须把淘汰集带进事务——见下。

### 4.3 stage 是否丢弃 demote / evict

| 项 | 现树 | 依据 |
|---|---|---|
| evict | **未丢弃**：`stageSnapshot` 传 `evictCheckpointSnapshotIds = checkpointPlan.evictUnpinnedSnapshotIds` | `TutorVisualCommitRepository.kt:279` |
| demote | **不传（按设计）**：`StageTutorVisualSnapshotCommand` 只有 `evictCheckpointSnapshotIds`，KDoc 写明"stage 只带淘汰集，不带 demote：新行仍是 STAGING，不能抢旧 LATEST 的角色" | `TutorVisualCommit.kt:185-198`；`TutorVisualCommitRepository.kt:271-284` |
| commit | 两者都传 | `TutorVisualCommitRepository.kt:329-330` |

> 以上 stage 淘汰集 + 事务内重判的落码在 `3087c8ae`（2026-10-29 **13:04:22**）/ `9d91e6b0`（13:05:17），
> 因此批次 32 的 11:14 设备 APK **不含**这项修复；计划 §0 "stage 两层口径不一致"的描述对应的是该 APK 之前的源。

### 4.4 现有用例覆盖

- **正例（"总量超、净值不超"）已有**：`stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`（`TutorVisualCommitRepositoryInstrumentedTest.kt:424-487`），
  预算 70、三张 29 字节快照，断言"淘汰后净值 58 ≤ 70，本次 stage 必须成功"（`:472-476`）+ 过期 STAGING 被回收（`:477-480`）+ LIVE 基线不动（`:481-485`）。
- **负例（净值也超 → 拒绝、零新增行）**：仪器化侧只有事务内拒绝两例（`perProgramBudgetIsReinforcedInsideTheTransactionAfterAStaleRead` `:302`、`globalBudgetIsReinforcedInsideTheTransactionAfterAStaleRead` `:352`，断言 `PER_PROGRAM_BUDGET`/`GLOBAL_BUDGET` 于 `:337`/`:385`）；**stage 路径"净值超 → S0 拒绝"的仪器化负例不存在**。纯策略层的 `PER_PROGRAM_BUDGET`/`GLOBAL_BUDGET` 拒绝在 JVM `VisualCheckpointRetentionTest.kt:270/:291/:343`。
  → 计划 §1 任务 3 的"补正负例"中，**正例已存在，缺的是 stage 侧负例**（且"总量超、净值不超"与"净值也超"要成对，否则分辨力不足）。
  **复核修复轮补记（2026-10-29）**：已补 `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`
  （`TutorVisualCommitRepositoryInstrumentedTest.kt`，预算 40 < 淘汰后净值 58，断言 plan 在写库前拒绝 +
  program/snapshot 行集合不变 + 无 message/link），与既有 stage 正例成对；同轮还给
  `stagingWhenEvictionIsRefusedByAnInFlightLeaseStillRejectsWithACode` 补了"plan 淘汰集含在飞行行"的
  断言。两处改动后的 12 例复跑 **UNVERIFIED**（本会话不运行 Gradle/设备）。

---

## 5. `sceneSha256` 的消费点与 digest 负向断言集合

### 5.1 `sceneSha256` 全部引用（仓内、非 build、非 worktrees）

| 位置 | 角色 |
|---|---|
| `core/data/src/main/.../visual/TutorVisualCommitRepository.kt:511` | 写入前**计算**：`sceneSha256 = TutorVisualIdentity.sha256Hex(sceneJson)` |
| `core/database/src/main/.../TutorVisualCommit.kt:126` / `:154` | 输入 DTO 字段 + 仅 `require(isNotBlank())` |
| `core/database/src/main/.../TutorVisualCommit.kt:441` | `TutorVisualWriteChecks.sameSnapshotPayload`：`entity.sceneSha256 == write.sceneSha256`（**两个声明值互比**，不比内容） |
| `core/database/src/main/.../TutorVisualCommit.kt:483` / `:542` | DTO → entity 拷贝 |
| `core/database/src/main/.../entity/TutorVisualEntities.kt:166-167` | 列映射 |
| `core/database/src/main/.../VisualSnapshotMigration_69_70.kt:91` | 迁移 DDL |
| 测试：`TutorVisualStructureGatesInstrumentedTest.kt:408/:569`、`TutorVisualCommitInstrumentedTest.kt:612`、两个迁移套件 | 夹具/列名 |

**结论**：`sceneSha256` **没有任何消费方校验它与 `sceneJson` 内容一致**——`ensureTutorVisualSnapshot`（`RoomStudyDatabase.kt:2254-2298`）
只 `require` 了 `stateSha256 == sha256Hex(stateJson)`（`:2259`）与 `drawCommandsSha256 == sha256Hex(drawCommandsJson)`（`:2262`），
然后派生 id（`:2265-2274`）。这与批次 32 记录 §5.2 #10（`docs/research/2026-10-29-batch-32-record.md:270`）一致，计划 §1 任务 4 成立。

### 5.2 digest 负向用例的断言集合（现树）

| 用例 | 位置 | 断言 |
|---|---|---|
| `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite` | `TutorVisualCommitInstrumentedTest.kt:129-170` | ① `failure.exceptionOrNull() is IllegalArgumentException`（`:160-163`）；② `countSnapshots(PROGRAM_ID) == 0`（`:164-168`） |
| `drawCommandsDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite` | `:172-212` | ① 同上（`:202-205`）；② `countSnapshots(PROGRAM_ID) == 0`（`:206-210`） |
| 对照（快照 id 复派生门，`snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite`） | `:96-127` | ① `IllegalArgumentException`（`:112-115`）；② **`countPrograms(PROGRAM_ID) == 0`**（`:116-120`）；③ `countSnapshots == 0`（`:121-125`） |
| 对照（program id 伪造门，`programIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite`） | `:70-94` | `countPrograms(forged.programId) == 0`（`:90`）、`countSnapshots == 0`（`:91`）——该用例的 require 在 `ensureTutorVisualProgram` 内，program 行尚未写，故"0"不证明回滚 |

**缺口确认**：两条 digest 负向**都没有 `countPrograms == 0`**。要点：`stageTutorVisualSnapshot` 事务内先跑 `ensureTutorVisualProgram`（`RoomStudyDatabase.kt:1957`）、
再跑 `ensureTutorVisualSnapshot`（`:1958`，require 在这层抛出），所以 program 行确实被写在 require 之前——只有事务回滚才会消失。
现断言只证明"snapshot 零行"，**不区分"整事务回滚"与"事务提交了 program 行"**。计划 §1 任务 5 成立，且 `countPrograms == 0` 在
`snapshotIdThatIsNotDerivedFromContentIsRejectedBeforeAnyWrite`（`:116-120`）已有同形用法可直接照抄。

---

## 6. 一致门：N13 参数、`:317-318` 代数句、屏侧例数

### 6.1 N13 用例参数（`VisualDrawOpsParityInstrumentedTest.kt:369-449`）

| 参数 | 值 | 位置 |
|---|---|---|
| 平移量 | **+2 px（x 方向，dy=0）**：块 `(60..99, 40..59)` → `(62..101, 40..59)` | `:394-397`；注释 `:394`"块整体 +2 px" |
| 补偿 | 块外 100 px 墨条（列 59..68，行 35..39 与 60..64）+ 块内 10×10 挖空（75..84, 45..54） | `:398-406` |
| 质心/墨量 | 断言 `centroidDeltaPx == 0.0`（容差 1e-9）、`massRatio == 1.0`（容差 1e-9） | `:415-421` |
| bboxDev | 注释写 `bboxDev=5`（`:377`）；断言 `bboxDeviationPx > TEXT_THRESHOLDS.maxBboxDeviationPx` | `:422-425`；阈值 `maxBboxDeviationPx = 1`（`VisualDrawOpsParityMetrics.kt:274`） |
| bestShift | `(bestShiftX,bestShiftY) != (0,0)` 且 `bestShiftIou - iou > MIN_SHIFT_SENSITIVITY_GAIN(0.02)` | `:426-432`；阈值 `:292` |
| 判决 | `F4_GEOMETRY` + `reasons` 中以 `text bboxDev=` 或 `text bestShift=` 开头的一条 | `:433-442` |

`bboxDev=5` 的手算复核（只读代码，未执行）：`maxEdgeDeviationFrom` 取四边最大绝对差（`VisualDrawOpsParityMetrics.kt:78-83`），
`bbox` 在 band `(40,20,120,80)` 内求墨界（`:864-879`）。屏 bbox=(60,40,99,59)，PDF bbox=(59,35,101,64)
⇒ `max(|60-59|, |40-35|, |99-101|, |59-64|) = 5`。**与注释一致**。
注：该用例每次运行都会用真栅格复核该构造（`:415-421` 断言质心 0 与墨量 1.0），故"5"不是纯口算假设；但本轮未执行 → 见 §11。

### 6.2 `:317-318` 的代数句（仍在树）

原文（`VisualDrawOpsParityInstrumentedTest.kt:317-318`）：

> `// - P06：设备实测 d(+1) = 1.2624767812258337（step08-parity-logcat.txt:125）；`
> `//   2 位打印即 1.26。该值与基线精度无关（b² = a² + 2·vx + 1 ⇒ d(+1) = b）。`

问题：`b` 就是 `d(+1)` 本身，`b² = a² + 2·vx + 1 ⇒ b = d(+1)` 是同义反复，推不出"与基线精度无关"。
批次 32 记录 §5.2 #7（`docs/research/2026-10-29-batch-32-record.md:267`）已登记"**未修**"。计划 §1 任务 6 成立。
（同一段的其余句子——`v_P07=(−0.4492, ±0.2582)`、`d(+1)=0.608282`、`0.608 < 0.75`——与 `docs/research/2026-10-29-parity-metric-correction.md` 一致，不动。）

### 6.3 parity 与 metrics 的屏侧例数表述（互相矛盾）

| 文件 | 原文 | 现树实测 |
|---|---|---|
| `VisualDrawOpsParityInstrumentedTest.kt:51-52` | "屏侧本身的正确性由 painter 套件（**设备 19 例 + JVM 10 例**）负责" | painter 设备 `VisualDrawOpsPainterInstrumentedTest.kt` `grep -c '@Test'` = **24** |
| `VisualDrawOpsParityMetrics.kt:14-15` | "屏侧正确性由 painter 套件（**设备 24 例 + JVM 10 例**）负责" | 同上 = 24 → metrics 的 24 正确 |

补充实测（`grep -c '@Test'`）：Painter JVM `VisualDrawOpsPainterPureTest.kt` = **13**、PDF JVM `VisualDrawOpsPdfPainterPureTest.kt` = **6**。
两处都写"JVM 10 例"，与 13（painter 自身）不等；若"JVM 10 例"指 painter+PDF 之外的某个子集，仓内未找到出处 → 该项需主会话裁定口径，本轨只记差异。
结论：计划 §1 任务 6"统一 parity/metrics 的屏侧例数（24）"成立，且**顺带暴露 JVM 侧 10 vs 13 的第二处不一致**。

---

## 7. KD 原文与"未上设备"类反向句

### 7.1 KD 原文（`docs/known-defects.md`）

| 条目 | 原文标题（逐字摘录） | 关闭条件行 |
|---|---|---|
| **KD-71**（`:2727`） | `## KD-71 (open, 2026-10-26 登记（批次 21 T3 复核发现 #22；低）) · QuickJsVisualExecutionEngine close/execute 竞态：closed 检查与 currentTransaction.set 之间的窗口` | `:2744` `Reopen condition.` — 新增并发用例在设备上证明 close 后不再有成功执行、且 in-flight 事务被强弃后关闭（含 32 轮压测全绿、日志落盘）后关闭 |
| **KD-72**（`:2754`） | `## KD-72 (open, 2026-10-26 复核返修 #5/#10 登记；中) · dependency locking 已配置但仓库无已跟踪锁文件：DEFAULT 模式下锁门未生效，且已生成的锁与 HEAD/提交面不一致` | `:2776` — 每个 subproject 都有 `gradle.lockfile` + 一次负控（人为抬版）在解析期判红 |
| **KD-73**（`:2782`） | `## KD-73 (open, 2026-10-26 复核返修 #11 登记；中) · 运行时 kotlin-stdlib 强制降回 2.3.21：字节码/API 兼容证据只覆盖 quickjs AAR，不是全解析图` | `:2792` — 完整解析图扫描 unresolved=0 且设备/R8 冒烟归因崩溃，或升 2.4.10 的构建/设备证据落盘 |
| **KD-77**（`:2883`） | `## KD-77 (open, 2026-10-27 登记（API 23 冒烟评估；中）；2026-10-29 批次 32 T6 标题回改：android-23 镜像已装盘，旧包冒烟 logcat 未落仓、修复后 APK 未跑) · API 23 运行时未验证：修复后 APK 未在 API 23 完成装机/冷启动/最小冒烟` | `:2893` — API 23 AVD/真机上 v21 APK 完成装机+冷启动+最小冒烟，证据落盘且 logcat 无四类崩溃 |
| **KD-80**（`:2969`） | `## KD-80 (open, 2026-10-29 批次 30 登记；高) · schema 70 落地后备份机器清单一度仍停在 69：v70 库备份/恢复 fail-closed（设备实测 34 例 8 红；批次 31 复跑 34/34 绿，80-C1/C2 核销；80-C3 JVM 半边 UNVERIFIED、80-C4 零断言 → 保持 open）` | `:2987` — `80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4`；80-C4 = "备份/恢复后旧行与视觉四表引用关系保持"（`:2977`） |

> 本轨对 KD-80 只加一条**现树实测**：`BackupRestoreInstrumentedTest.kt` 的 `tutor_visual` 命中数 = **0**（`grep -c`），
> 即 80-C4 至今**零断言**，计划 §1 任务 2 成立。

### 7.2 "未上设备 / 未执行"类反向句位置

**（a）台账 `docs/known-defects.md`**

| 位置 | 原文要点 | 现状判定 |
|---|---|---|
| `:2996`（计划点名的行） | "S=71 的备份 34/34、1→71 迁移矩阵、`SmbkSelectionPlanTest`/`BackupSnapshotTrimmerInstrumentedTest` 与设备结构门均**未在本批执行 → UNVERIFIED**" | **已被 `:2998-3006` 取代**（设备轨 11:19–11:35 实跑；`:3000` 明写"不得再把这几项写成'未在本批执行'"）。计划 §1 任务 7 要改的就是这一句 |
| `:3002` | "JVM `SmbkSelectionPlanTest`（12 例）、`VisualCheckpointRetentionTest`（17 例）等纯函数门在本轮**未执行**" | `:3006` 已补 build/ 旁证（12/0、17/0，mtime 12:36–12:38），仍记 80-C3 PARTIAL |
| `:3001` | "复核修复轮已把读侧改读未列入淘汰候选的 STAGING 行（`snap-staging-keeper`），**修复后的绿轮尚未重跑**" | **准确**，与本轨 §3 的现树一致；不得写成"结构门全绿" |

**（b）批次 32 判据 `docs/research/2026-10-29-batch-32-device-criteria.md`**

| 位置 | 原文 | 判定 |
|---|---|---|
| `:371-377`（§5.3） | 预算行"设备轨后记…现树已落码"；evict 行"复核修复轮已改读…绿轮待重跑"；pin 行"JVM 例本批未执行" | 与现树一致，但 `:375` 的"E-08 实跑 `OK (11 tests)`/`OK (8 tests)`"里仓库现树已是 **9 例** |
| `:379-388`（§5.4） | 第 1 项"触发器 backstop 未落"仍成立；第 2 项"预算门未入事务/evict 租约未加"已划除并标"已被取代" | 诚实处理，无新增反向句；但 `:384-388` 仍以 `OK (11)`/`OK (8)` 收尾 |
| `:850-868`（§9） | `:857-859` "UNVERIFIED（仍未执行）：JVM 单测…；结构门 E-07 仍 4/5（红例已修、绿轮未重跑）" | 结构门口径**准确**；JVM 单测部分与 `:3006` 的 build/ 旁证存在口径差（仓内命令日志 vs build XML） |
| `:345-357`（§5.1） | 结构门目标写 `OK (4 tests)`、列 4 条方法 | **计数过期**：现树 5 例（`:279-315` 第 5 例） |
| `:361-369`（§5.2） | "`TutorVisualCommitInstrumentedTest` **10**、`TutorVisualCommitRepositoryInstrumentedTest` **5**" | **计数过期**：现树 **11** / **9**（本轨 `grep -c '@Test'` 实测） |

**（c）三处测试 KDoc**

| 位置 | 原文 | 判定 |
|---|---|---|
| `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt:34-36` | "复核修复轮新增第 **9** 例…该例**尚未上设备**——不得把 8 例绿读成 9 例绿" | **唯一逐字"未上设备"**，且与设备证据（E-08 `OK (8 tests)`）一致 → 本批重跑 9 例后须改成 9/9 |
| `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt:49-51` | "修复后的绿轮仍需设备轨重跑，重跑前本套件不得写成 5/5" | 等价"未重跑"句，**准确** |
| `core/data/src/test/.../visual/VisualCheckpointRetentionTest.kt:35-37` | "设备/集成面（本环境不运行 Gradle/adb）… 本文件执行结果登记为 UNVERIFIED" | 第三处"待验证"句；与 `known-defects:3006` 的 build/ XML 旁证（17/0）存在口径差 |

> 计口径说明：计划 §1 任务 7 说"三个测试 KDoc 的'尚未上设备'"，现树**逐字只命中 1 处**（repository `:36`）。
> 另两处（结构门 `:50`、retention `:37`）是同类"绿轮未出/结果 UNVERIFIED"句。若任务 7 按字面找三处"未上设备"，第三处**找不到**——需按上述三项认账。

---

## 8. 设备在线状态（只读）

`C:/Android/Sdk/platform-tools/adb.exe devices -l`：

| serial | 状态 | API | ABI | model |
|---|---|---|---|---|
| `emulator-5562` | device | 34 | x86_64 | sdk_gphone64_x86_64 |
| `emulator-5566` | device | 34 | x86_64 | sdk_gphone64_x86_64 |
| `emulator-5584` | device | 34 | x86_64 | sdk_gphone64_x86_64 |

- 三台均在线，与批次 32 记录一致；批次 32 判据 `:867` 写明 "`-5566/-5584` 属他线，禁碰"。
- 本轨**未**执行 install/instrument/卸载；未读 `getprop ro.build.version.page_size`（批次 32 判据要求 4096，本轨未复测 → UNVERIFIED）。
- `adb` 不在 bash `PATH`（`adb: command not found`），需用绝对路径 `C:/Android/Sdk/platform-tools/adb.exe`（`local.properties` 的 `sdk.dir=C:\Android\Sdk` 与 `ANDROID_HOME=C:\Android\Sdk` 一致）。

---

## 9. 对计划 §1 九项任务的直接含义（只写基线能判定的部分）

| 任务 | 基线结论 |
|---|---|
| 1 结构门红例修复 | **已落码**（§0-A、§3）。不要再改行号；本项只剩"重建重装 → 设备 `OK (5 tests)`" |
| 2 KD-80 补 80-C4 | 需要做（§7.1 末注：`tutor_visual` 0 命中） |
| 3 stage 预算口径统一 | 口径**已统一**（§4.1/§4.2/§4.3）。剩：stage 侧负例（净值也超 → 拒绝）与正例成对（§4.4） |
| 4 `sceneSha256` 内容绑定 | 需要做（§5.1） |
| 5 digest 负向补事务性断言 | 需要做（§5.2，`countPrograms == 0`；同形断言见 `:116-120`） |
| 6 一致门盲集诚实化 | 需要做：N13 是 2 px（§6.1）；`:317-318` 代数句仍在（§6.2）；屏侧 19 vs 24（§6.3），另发现 JVM 10 vs 13 |
| 7 文档回改 | 需改：`known-defects:2996`（§7.2a）、批次 32 判据 §5.1/§5.2 计数与 §9 口径（§7.2b）、repository KDoc `:36`（§7.2c）；结构门 `:50` 与 retention `:37` 可一并按证据改写 |
| 8 KD 状态裁决 | 基线只提供原文与关闭条件（§7.1）；**裁决需设备证据**，本轨不裁 |
| 9 热区条件轨 | 热区仍大量在飞（§2）：`feature` 134、`core/ui` 35、`app` 28 等 → 九项排队是否清空**本轨无法从计数判定**（计数≠九项），维持 pending |

---

## 10. 本轮实跑命令（可复跑）

```bash
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git log -1 --format="%h %ci %s" HEAD
git log -1 --format="%h %ci %s" origin/main
git status --porcelain | wc -l
git status --porcelain | awk '{print $1}' | sort | uniq -c
git status --porcelain -- core/data
git status --porcelain -- core/database
git merge-base --is-ancestor 9d91e6b0 HEAD
git log --oneline -S "snap-staging-keeper" -- core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt
git log --oneline -S 'summaries.getValue("snap-staging-middle")' -- core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt
git log --oneline -3 -L 1953,1984:core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt
grep -o '"identityHash": *"[^"]*"' core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json
grep -rn "STUDY_DATABASE_VERSION" --include=*.kt core/database/src/main/kotlin
grep -rln "714c0ecb85ee5edf74f0013740148138" . build core/*/build
grep -c '@Test' <每个套件文件>          # 计数见 §2/§6/§7
grep -rn "sceneSha256\|scene_sha256" --include=*.kt --include=*.md .
grep -rn "countPrograms" --include=*.kt core/database/src
grep -c "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt
C:/Android/Sdk/platform-tools/adb.exe devices -l
C:/Android/Sdk/platform-tools/adb.exe -s <serial> shell getprop ro.build.version.sdk
```

**未运行**：任何 `gradlew`、任何 `adb install/am instrument`、任何 `git add/commit/push`、任何文件删除。

---

## 11. UNVERIFIED（本轮未执行，不得当成已通过）

1. **所有测试结果**：本轨零 Gradle、零仪器化。结构门"修复后是否 5/5 绿"、仓库第 9 例、N13 的 `bboxDev` 运行时读数、digest 负向的实际异常类型——全部是**读码结论**，不是执行证据。
2. **当前树下设备绿轮**：批次 32 的 `OK (16/4/3/15/34/11/8/9/24/16/13/2)` 对应 11:14–11:17 的 APK；现树此后有改动（`known-defects:3006`）。计划 §2 的"必须先重建重装"仍必须执行。
3. **`71.json` 的生成新鲜度**：本轨只读既有产物，未跑 `kspDebugKotlin`；生成物与现树实体是否一致未复验。
4. **设备 16 KB 页**：未读 `getprop ro.build.version.page_size`。
5. **`JVM 10 例`口径**：13（painter pure）+ 6（pdf pure）与文档"10"不一致，本轨未找到"10"的出处与统计口径 → 待裁定。
6. **KD-71 的 JVM 用例执行**：`build/` 下是否有其结果 XML 本轨未核（与 KD 裁决相关，非本轨任务）。
