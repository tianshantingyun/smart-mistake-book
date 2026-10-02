# 投影回退流程（Wave 0 / W0-1 ④ · 防降级配套）

> 建立于 2026-09-30（内核修复路线图 W0-1 第 ④ 条）。
> 上游：`docs/research/2026-09-28-kernel-remediation-roadmap.md` W0-1；
> 姊妹文档：`docs/research/algorithm-version-ledger.md`（版本清单）。

## 1. 解决什么

投影（`learner_projection_snapshot` + 8 张子表）是**账本的派生态**：`LearningProjector.replay`
会用新版本整份覆盖它。覆盖一旦发生，旧的那一份就没有第二个副本——"改了数值能不能退回去"
因此不是态度问题，是**有没有副本**的问题。

Wave 0 落了三件事：

1. **覆盖前归档**（表 `projection_archive`）：`StudyProjectionDrainer.commitFullReplay` 在调用
   `replay` **之前**把现投影整份写进归档表（`snapshot_json` + `projector_version` + `schema_ddl`）。
   顺序就是机制——晚一步归档，存下来的就是新值。
2. **防降级写**：`DatabaseContractValidator.validateProjectionCommit` 断言提交里带的快照版本
   等于**当前二进制**的期望版本（`ProjectionCommit.expectedProjectorVersion`，生产调用点是
   `StudyProjectionDrainer` 传 `LearningProjector.VERSION`）；`LearningProjector.replay` 入口
   另外要求"跨版本覆盖必须声明被替换的那份已归档"。两道门合起来：**跨版本替换只能是
   "先归档、再覆盖"这一种姿态**。
3. **本文档**：把"怎么退回去"写成可执行步骤，而不是"应该可以退"。

**本波没有实现回退工具**（诚实说明）：下面第 4 节是**手工流程**，工具化排在后续波次
（roadmap S 组 / 3B 的投影存储口径）。在此之前，回退靠 SQL + 一次性脚本，必须按本流程走。

> 工具化已落地（2026-10-03，3B 批次 B6）：`readLatestArchivedProjection` +
> `restoreArchivedProjection`（端口 `LearningProjectionPort`），本节 §4 每一步的自动化映射与演练实测见
> `docs/research/2026-10-03-projection-rollback-drill.md`；本文手工流程保留为口径参考（§4-2 的执行方式
> 已被工具收编，见演练记录 §1.1）。

## 2. 什么时候用它

- 某次算法 bump 之后数值明显不对（掌握度过低/过高、复习间隔离谱），要回到 bump 前的投影；
- 需要拿"bump 前 / bump 后"的同一条学习记录做对照（例如 `docs/research` 里的算法复核）；
- 判定"这次改动到底改了什么"时，需要旧投影作为对照物。

**不适用**：账本（`projection_outbox` / 事件表）本身坏了——那是账本完整性问题，
投影回退救不了（`loadLearningLedger` 会 fail-closed 报 GAP/CONFLICT）。

## 3. 归档表长什么样

```sql
CREATE TABLE IF NOT EXISTS `projection_archive` (
    `archive_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `projection_name` TEXT NOT NULL,
    `learner_id` TEXT NOT NULL,
    `archived_at_epoch_millis` INTEGER NOT NULL,
    `snapshot_json` TEXT NOT NULL,
    `projector_version` TEXT NOT NULL,
    `schema_ddl` TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS `index_projection_archive_projection_name_learner_id_archived_at_epoch_millis`
ON `projection_archive` (`projection_name`, `learner_id`, `archived_at_epoch_millis`);
```

- `snapshot_json`：`LearnerSnapshotJson`（`core:model`）编码的整份快照——**可解回**
  （`LearnerSnapshotJson.decode`），不是给人看的日志。
- `schema_ddl`：归档时刻投影 9 张表的真 DDL（DAO 在同一事务里从 `sqlite_master` 现读，
  清单见 `PROJECTION_ARCHIVE_TABLES`）——回退时用它判断"这份 JSON 能不能原样写回现在的表"。
- 只增不改：生产代码只 insert，没有删除与保留策略（行数会随全量重放次数增长，记为遗留项）。

## 4. 回退流程（手工）

**前置**：确认目标 archive 行存在；`snapshot_json` 的 `projectorVersion` 与你将要运行的
二进制版本一致（见第 5 节的版本注意）。

1. **定位目标行**（挑该 learner 最近一次、且版本正确的一份）：

   ```sql
   SELECT archive_id, archived_at_epoch_millis, projector_version
   FROM projection_archive
   WHERE projection_name = 'study-experience-v1' AND learner_id = :learnerId
   ORDER BY archived_at_epoch_millis DESC, archive_id DESC
   LIMIT 5;
   ```

2. **先给"当前"投影也落一条归档**：回退会覆盖当前的（新的）投影，如果不先归档它，
   这次回退本身就成了单向操作。用同一张表、同一个流程（把当前 `snapshot_json` 写进去），
   或先 `VACUUM INTO` 一份库文件副本。

3. **校验 schema 兼容**：把该行 `schema_ddl` 与现在的投影表结构对比
   （`sqlite_master` 或 `core/database/schemas/<当前版本>.json`）。DDL 不一致 = 当时的 JSON
   可能带不上现在的列，**停下来**：先决定"补齐还是放弃"（不许悄悄丢列）。

4. **重建并写回快照**（一个事务内）：

   - `LearnerSnapshotJson.decode(row.snapshot_json)` 得到 `LearnerSnapshot`；
   - 删子表 → 写回 `learner_projection_snapshot` 头部（`state_version` 递增，别复用旧值）
     → 写回 `learner_problem_memory_state` / `learner_knowledge_mastery_state` /
     `independent_correct_observation` / `applied_*_record`；
   - `presentation_projection_state` **不在归档 JSON 里**（呈现态不进 `LearnerSnapshot`），
     本步不动它：若目标 checkpoint 早于任何现存终局揭示，增量排空会以未回滚的呈现行为权威，
     所以这类目标必须**拒绝**而不是硬写（工具口径见演练记录 §4-4）；
   - 保持 `checkpoint_sequence` / `known_ledger_head_sequence` / `projector_version` 与归档一致。

5. **重新 drain**：启动应用（或调用排空路径）。排空会读账本、按当前二进制版本判断是否需要
   全量重放（`StudyProjectionDrainer` 的 `requiresReplay`），并把这次重放前的投影**再次归档**。

6. **验证**：确认投影 `freshness` / `projection_status` 为 CURRENT，且复习队列、
   掌握度列表与账本一致（拿改动前后的同一份账本对照）。

## 5. 版本注意（最容易踩的一条）

- 恢复出来的投影带**旧版本串**。如果此时运行的是**新**二进制，排空会立刻判定
  "版本不匹配 → 全量重放"，按新公式重算一遍（并把恢复出来的那份归档掉）——
  **看起来像是回退没生效**。要让回退真的"回到旧算法"，必须让二进制版本与
  `archive.projector_version` 对应（即回退 App 版本本身），或接受"退回的只是数值快照、
  随后仍按当前算法重算"。
- **降级写被拒**：当前二进制拒绝提交"非自己版本"的快照
  （`DatabaseContractValidator.validateProjectionCommit`），所以旧二进制不能借"快速重放"
  把新投影换掉：它要么走本流程（先归档），要么失败。这就是 W0-1 目标①的落点。
- 归档行与版本清单要一起看：`docs/research/algorithm-version-ledger.md` 说"为什么改"，
  本表说"改之前的旧值在哪"。
