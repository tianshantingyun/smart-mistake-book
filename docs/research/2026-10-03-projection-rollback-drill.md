# 投影回退演练记录（阶段 3B · 批次 B6）

> 建立于 2026-10-03。上游：`docs/research/2026-10-02-stage3b-plan.md` §3 步骤三第 4 条
> （用户裁定：回退 = 工具化 + 演练）、`docs/research/kernel-projection-rollback.md`（§4 手工流程）。
> 交付物：archive 读回 + `restoreArchivedProjection` 专用恢复路径（代码面，零 schema / 零版本变动）
> + `:core:database` 仪器化演练（`ProjectionRollbackDrillInstrumentedTest`，9 用例全绿；
> B6 独立复核 P1/P2 的守卫用例已并入，见 §2.3）。

## 0. 本批新增的工具面（代码位置）

| 面 | 位置 | 说明 |
|---|---|---|
| archive 读回 | `core/database/.../dao/ProjectionTransactionDao.kt` `readLatestArchivedProjection` | `archived_at_epoch_millis DESC, archive_id DESC LIMIT 1`，返回 `PersistedProjectionArchive`（含 `archiveId` / `snapshotJson` / `projectorVersion` / `schemaDdl`） |
| 端口 | `core/database/.../port/LearningProjectionPort.kt`（`RoomStudyDatabase` 实现） | `readLatestArchivedProjection` + `restoreArchivedProjection` |
| 恢复编排判层 | `core/database/.../RoomProjectionArchiveStore.kt` | 七类拒绝（版本列与版本载荷各判一道，共八道判定：无归档 / 版本列 / 可解码 / 版本载荷 / 恢复时刻单调 / checkpoint 不超头 / 呈现态不超前 / schema 同口径）+ decode + 覆盖前归档当前投影，全部在写之前 |
| 单事务重建 | `ProjectionTransactionDao.restoreProjectionSnapshot` | 头部覆盖 + 7 张投影表重建；`state_version` 递增 |
| DDL 同口径 | `ProjectionTransactionDao.projectionTablesDdl()`（私有单源） | 写入与恢复比对读的是同一个构造（表清单 + 连接符） |
| 拒类型 | `ProjectionRestoreRejectedException`（`reason: ProjectionRestoreRejection`） | 机器可判的拒绝：`NO_ARCHIVE` / `VERSION_MISMATCH` / `MALFORMED_ARCHIVE` / `RESTORED_AT_IN_PAST` / `CHECKPOINT_AHEAD` / `PRESENTATION_AHEAD` / `SCHEMA_MISMATCH` |
| 演练 | `core/database/src/androidTest/.../ProjectionRollbackDrillInstrumentedTest.kt` | 真 Room + 真 drainer（`:core:database` 的 androidTest 依赖 `:core:data`，仅测试类路径） |

零 schema / 零版本变动：无新表 / 新列 / 新迁移，`STUDY_DATABASE_VERSION` 与四个内核版本串不动。

## 1. `kernel-projection-rollback.md` §4 步骤 → 本批工具映射

| §4 步骤 | 现在是什么 | 自动化程度 |
|---|---|---|
| 前置：确认目标行存在、版本一致 | `readLatestArchivedProjection` 读回；版本（列+载荷）、可解码、恢复时刻、checkpoint 不超头、呈现态不超前判定都在 `restoreArchivedProjection` 内部，任一不过即拒 | 自动 |
| 1. 定位目标行（SQL `ORDER BY archived_at DESC, archive_id DESC LIMIT 5`） | 同一排序口径进了 DAO 读口（取 1 条；读口验证用） | 自动 |
| 2. 先给"当前"投影也落一条归档 | `restoreArchivedProjection` 在重建前调 `archiveProjectionSnapshot`（同表、同写入路径、只增） | **自动**（本批裁定，见 §4.1） |
| 3. 校验 schema 兼容（DDL 对比） | `readProjectionTablesDdl()` 与归档行 `schema_ddl` 逐字比对；不一致 → `SCHEMA_MISMATCH`，不写一行 | 自动 |
| 4. 一个事务内重建并写回快照 | `restoreProjectionSnapshot`（@Transaction）：头部覆盖 + 7 表重建；`state_version` 递增；`checkpoint` / `known_ledger_head` / `projector_version` 与归档一致 | 自动 |
| 5. 重新 drain | 既有 `StudyProjectionDrainer`（演练经 `RoomBackedStudyExperienceRepository.refresh()` 触发）；重放前会**再次归档**（恢复出来的那份） | 自动（既有机制） |
| 6. 验证 freshness / projection_status = CURRENT、与账本一致 | 排空后 `readCurrentLearnerSnapshot` 读回断言（机器）；"与账本语义一致"（队列/列表目视） | 半自动（语义对照仍人工） |

仍需人工的只剩两件：§4-2 的**外部双保险**（`VACUUM INTO` / `BackupPort.snapshotForBackup` 文件副本，可选）与
§4-6 的语义目视核对。回退主链（定位 → 归档旧值 → 兼容校验 → 重建 → 再排空）全在工具里。

### 1.1 §4-2 的执行方式裁定（演练发现，本批改口径）

runbook 原文给了两个选项：①"用同一张表、同一个流程（把当前 `snapshot_json` 写进去）"；②先 `VACUUM INTO`
一份库文件副本。**选项①与"最近一份"选择器冲突**：restore 取 `ORDER BY archived_at DESC, archive_id DESC`
的第一条，手工把当前投影写进同一张表后，最新一条就变成了它自己——恢复目标被顶掉，restore 变成一次
静默空操作（"回退没生效"的又一来源）。

本批把它收进工具内部（顺序：读定目标 → 归档当前 → 重建），于是：

- 操作者**不要**手工预归档到同一张表；做完 §4-1 的读回后直接调 `restoreArchivedProjection` 即可；
- ②文件副本仍是合法的外部双保险（不动归档表、不影响选择器），照旧人工；
- 被换下的那份因此必然晚于目标（调用方传"恢复时刻"，生产取 now；工具已把这条升级为运行期守卫
  `RESTORED_AT_IN_PAST`，见 §2.3）——这也是连续回退的一步一退语义。

## 2. 演练实测记录

### 2.1 必跑门（JVM，`--rerun-tasks`）

原始命令（仓库根）：

```bash
./gradlew :core:domain:test :core:model:test :core:data:testDebugUnitTest \
  :core:database:testDebugUnitTest --rerun-tasks --console=plain
```

关键输出：`BUILD SUCCESSFUL in 3m 45s`（复核处置后的最终一轮；四个测试任务均执行、无失败用例；
`--rerun-tasks` 保证不是 UP-TO-DATE 复用。早前同口径两轮为 `2m 17s` / `2m 47s`）。

### 2.2 演练（`:core:database` connected，真 Room + 真 drainer）

原始命令：

```bash
./gradlew :core:database:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.ProjectionRollbackDrillInstrumentedTest" \
  --console=plain
```

关键输出（emulator-5554，`test_device(AVD) - 14`）：

```
Starting 9 tests on test_device(AVD) - 14
Finished 9 tests on test_device(AVD) - 14
BUILD SUCCESSFUL in 15s
```

XML 汇总（`core/database/build/outputs/androidTest-results/connected/debug/TEST-*.xml`）：
`tests="9" failures="0" errors="0" skipped="0" time="3.295"`（套件时间；`test-result-exit-code.txt` = 0。
同口径早前一轮 `38s` / `time=3.193`）。

五个原始用例与断言（断言均为逐位相等/显式拒绝，无放宽）：

| 用例 | 构造（全部真实路径） | 断言 |
|---|---|---|
| `theRealDrainerArchivesTheDisplacedProjectionAndRestoreReturnsItBitForBit` | `recordAttempt` → `refresh()`（真 drainer 增量提交）→ `appendAttemptCorrection` → `refresh()`（真 drainer 先归档、再全量重放）→ `readLatestArchivedProjection` → `restoreArchivedProjection` → 再 `refresh()` | ① 归档行 = 重放前 `readCurrentLearnerSnapshot` 快照**逐位一致**，`schema_ddl` 含 9 张表；② 恢复返回与读口读回**同一份**（`PersistedLearnerSnapshot` 数据类相等）；`state_version = 重放后 + 1`；③ 归档表 0 → 1（drainer）→ 2（restore 追加被换下的当前投影，目标行按 `archive_id` 原样 byte-identical）→ 3（再排空把恢复出来的那份也归档）；④ §4-5 再排空后投影**逐位回到**重放后状态（重放确定性），freshness/status CURRENT |
| `restoringWithoutAnyArchiveIsRejectedAndWritesNothing` | 空库直调 | `ProjectionRestoreRejectedException(NO_ARCHIVE)`；投影仍为 null、归档表 0 行 |
| `restoringACrossVersionArchiveIsRejected` | 真实归档写入口落一行"旧二进制"归档（版本列与 JSON 载荷同为 `learning-core-v11(...)`） | `VERSION_MISMATCH`；不建投影、不追加归档行 |
| `anArchiveWhosePayloadCarriesAnotherVersionIsRejected` | 版本列伪装成当前版、JSON 载荷仍写旧版本 | `VERSION_MISMATCH`（载荷也被判定，防"只信列"） |
| `anArchiveWithDriftedSchemaDdlIsRejected` | 真实归档一行后，外部连接 `UPDATE ... SET schema_ddl = schema_ddl \|\| ' -- drill-drift'` | `SCHEMA_MISMATCH`；当前投影逐位不变、归档表仍 1 行且漂移行原样保留（不"顺手修好"） |

演练过程中修掉的两处（记录，不隐藏）：

1. **恢复时刻的单调性**：首轮主链路在"新追加的归档在目标行之后"断言上红了——drainer 用系统时钟归档，
   测试给 restore 传的固定过去时刻比目标归档更早，"最近一份"原地不动。这是**真实契约**（端口 KDoc 已写明：
   `restoredAtEpochMillis` 应晚于任何既有归档行，生产取 now），不是测试独有；用例改为
   `target.archivedAtEpochMillis + 1` 后绿。该契约现已升级为**运行期守卫**（`RESTORED_AT_IN_PAST`，见 §2.3）。
2. 测试夹具 `ArchiveRow` 首版写成普通 class（身份相等），断言恒不成立；改 data class 后绿。

### 2.3 独立复核补齐的守卫用例（P1/P2，全部真实路径）

| 用例 | 构造 | 断言 |
|---|---|---|
| `aTargetEarlierThanAnExistingPresentationFactIsRejected`（P1） | attempt → `refresh()` → 手工 `archiveProjectionSnapshot` 建 save-point（C=1）→ `recordAnswerReveal` → `refresh()`（呈现行终局 = 2）→ restore(C=1) | `PRESENTATION_AHEAD`；当前投影**逐位不变**、归档表仍 1 行（不追加） |
| `anArchiveWithACheckpointAheadOfTheLedgerIsRejected`（P2-A） | 空账本（头 0）+ 归档一份 checkpoint=1 的"看起来正常"快照 | `CHECKPOINT_AHEAD`；不建投影、不追加归档行 |
| `aRestoreTimeBeforeTheArchiveIsRejected`（P2-B） | 归档时刻 = `ARCHIVED_AT`，restore 传 `ARCHIVED_AT - 1` | `RESTORED_AT_IN_PAST`；不建投影、不追加归档行 |
| `aMalformedArchiveJsonIsRejected`（P2-G） | 归档 JSON 截断（`{"learnerId": ..., "checkpoint": {`） | `MALFORMED_ARCHIVE`（原始解码异常只作 cause）；不建投影、不追加归档行 |

## 3. 裁定登记：archive 保留策略 = 维持只增

- 现状：`projection_archive` 只增（drainer 重放前一行 + 本批 restore 追加被换下的当前投影一行），
  无清理 / 无保留期；单行 = 一份快照 JSON（序列化含默认值，通常几十 KB 量级）+ 9 表 DDL 文本。
- 裁定：**维持只增**（体量小；行数随全量重放次数增长，全量重放由修正事件与版本 bump 触发，频次低）。
  本批不引入清理策略——增加机制必须先指认它消灭的具体失败，而当前"归档行过多"没有任何实测证据。
- 登记复看：与 3A 尾的遗留项同列；若未来真机发布后全量重放频次上量，按"每 learner 保留最近 N 份
  （N=2：当前副本 + 上一份）"评估。**复看触发点**：发布前置的存量压力观测。

## 4. 已知边界（诚实清单）

1. **应用未发布**：真实存量只有开发库；本演练用合成账本（真 Room + 真 drainer + 真 DAO/事务），
   真实存量压力观测随发布前存量补（与计划 §7 同口径）。
2. **跨版本恢复一律拒（版本陷阱）**：归档 JSON 是旧二进制算出来的快照；用新二进制恢复它，排空会立刻
   "版本不匹配 → 全量重放"并把它再次归档，看起来像回退没生效。真实灾难恢复要回到旧算法必须**同时回滚
   应用版本**；本工具只在同版本内回退（端口 KDoc 已写明，`kernel-projection-rollback.md` §5 同源）。
3. **恢复不改归档**：不删、不回写；`SCHEMA_MISMATCH` 被拒后漂移行也原样保留（用例已钉）。
4. **`presentation_projection_state` 不在归档 JSON 内**（呈现态刻意不进 `LearnerSnapshot`，见其 KDoc）：
   恢复事务不触碰它，表集合 = 头部 `learner_projection_snapshot` + `applyProjectionTables` 的 7 张
   （`learner_problem_memory_state`、`learner_knowledge_mastery_state`、`independent_correct_observation`、
   `applied_attempt_record`、`applied_correction_record`、`applied_answer_reveal_record`、
   `applied_tutor_answer_exposure_record`）。呈现态**按账本重派生只发生在全量重放路径**（§4-5 的
   "再排空"之所以无冲突，是因为待处理事件里有 correction，排空走了全量重放）；**增量路径不重派生**
   ——`ProjectionTransactionDao.batchStop` 把现存 `presentation_projection_state` 行按恢复后的
   checkpoint 读成权威（`LearningDaoModels.toModel` → `PresentationProjectionState` 的"揭示序号 ≤
   水位"前置条件）。因此恢复目标早于任何现存终局揭示（`terminal_event_sequence > checkpoint`）时，
   `restoreArchivedProjection` **在任何写之前显式拒绝**（`PRESENTATION_AHEAD`，用例
   `aTargetEarlierThanAnExistingPresentationFactIsRejected`，见 §2.3）；出路是先处理呈现态，或走
   全量重放路径。**为什么不是静默回滚呈现态**：归档 JSON 从不含呈现态，没有历史版本，无法精确退到
   checkpoint 当时的呈现行——静默写一个"猜"出来的呈现态会把一次不确定变成一次伪成功。
   （与计划原文的一处口径差：计划 §3 步骤三第 4 条写"单事务重建 9 表"，实现是 8 张——第 9 张
   `presentation_projection_state` 不在可恢复载荷内，故只能"拒绝早于它的目标"而不能"回退它"。）
5. **只能回退到"最近一份"，精确选目标未工具化**：`readLatestArchivedProjection` 只给最近一份，
   不能枚举/选定更早的行；恢复前若手工删掉目标归档行，工具会取到更早一份（或 `NO_ARCHIVE`）。
   需要精确选定某一历史行时仍在工具外人工处理（读口暴露 `archiveId` 仅供人工核对，不构成选择器）。
   ——与计划原文的第二处口径差：计划写"latest by learner/version"，实现是"最近一份 + 版本不符即拒"，
   属于实现细化（不按版本过滤行，避免"跳过版本不符的最近行去够更早的同版本行"这种隐藏回退）。
   两处口径差（9→8 表、latest by learner/version→最近一份+版本拒）属实现细化，**本记录为准**
   （计划原文不改，按复核指示）。
6. **归档载荷引用的 practice_unit / knowledge_node 若已删**：重建事务会被 RESTRICT 外键整体回滚，绝不会
   写出半截投影；副作用是"覆盖前归档"的那一行因在独立事务里而留下（只增，无害）。该路径未构造用例
   （**UNVERIFIED**）。
7. **`schema_ddl` 只覆盖 9 张表的建表 SQL**（写侧从 `sqlite_master` 现读表 DDL，不含索引/触发器）：
   索引漂移不在比对范围；恢复不依赖索引正确性（索引是派生设施），但这是一条明确的覆盖边界。
8. **恢复是维护操作**：应在排空静止时执行；本类不做跨进程互斥（无 App 运行态调用点）。
9. 工具化归属：本批只交端口 + 演练，没有 UI / 命令行入口（3B 范围）；未来工具调用点必须显式传
   `expectedProjectorVersion`（生产即 `LearningProjector.VERSION`，与 `ProjectionCommit.expectedProjectorVersion`
   同姿态——不给默认值，防判定被无声跳过）。
