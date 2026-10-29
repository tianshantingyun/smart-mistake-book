# 备份/恢复后视觉四表引用校验研究（批次 33 / KD-80 80-C4）

> 本轨只读代码与文档，**未运行 Gradle、未上设备、零 git 写**。下文所有"应绿/应红"都是设计判据，
> 不是执行结果。凡未执行项在 §7 显式登记 `UNVERIFIED`。
>
> 目标：为 KD-80 第四项关闭条件（"备份/恢复后旧行与视觉四表引用关系保持"，
> `docs/known-defects.md:2977` 原文口径）提供可直接落成仪器化用例的不变量集、断言集、
> "本应缺失 vs 缺陷缺失"判定表、设备侧最小用例清单与通过判据，并给出 GitHub 参考评分。

---

## 0. 结论速览

1. **80-C4 不是"恢复侧零校验"**：恢复路径已经在交换前跑 `PRAGMA quick_check` +
   `PRAGMA foreign_key_check`（`AndroidBackupRepository.kt:1121-1135`），并在 Phase 2c.1 把
   manifest 的 `rowCounts` 与包内库 **90 张表逐表对拍**（`:1234-1310`）——四张视觉表在这 90 张里
   （`SmbkSelectionPlan.kt:91,152,187-188`）。缺的是**用已知源数据证明这些数是对的**，以及
   **SQLite 外键表达不了的三条语义**：`link.program_id == snapshot.program_id`、被引用快照必须
   `LIVE`、身份/摘要与内容同源。
2. 四表的不变量分三层：**结构层**（PK/FK/UNIQUE，数据库自己拒绝）、**写入门层**（DAO 的
   `INSERT … SELECT … WHERE status='LIVE'` 与 `require` 派生式，只有走生产写入口才成立）、
   **测试层**（SQLite 无法表达的复合一致性）。恢复只复制字节，所以第三层是否保持，**只能靠用例证明**。
3. 裁剪是**规则性缺失**，不是缺陷：`mask != 15` 时快照保留集 =
   （选中会话的消息指针）∪（选中题本的题面 pin）∪ `checkpoint_kind='INITIAL'`；program 保留集 =
   上述引用 ∪ 被保留快照的 `program_id`（`BackupSnapshotTrimmer.kt:756-789`）。全选（15）**一行视觉
   数据都不裁**：split 表被 `mask != FULL_MASK` 守卫跳过（`:573-594`），类别表因四类全选中也不删。
4. 最容易误判为"缺陷缺失"的三种正常形态：① 旧包（schema 69 及以下）迁移后四表**本来为空**
   （`VisualSnapshotMigration_69_70.kt:15-18` 的非破坏迁移）；② 子集包里**未被引用且非 INITIAL 的
   STAGING 中间态**被裁；③ 子集包里**失去消息引用的 LIVE 非 INITIAL 快照**也被裁（当前 keep 规则
   不含 LIVE，见 §4.5 待裁定项）。
5. 设计采用**两段式判定**：先对"包内库"跑同一套 checker 并与期望集合比对（抓裁剪器/成包缺陷），
   再对"恢复后的库"跑同一套 checker 并与包内库逐行比对（抓恢复缺陷）。期望集合用 Kotlin 从源数据
   计算，不复制 SQL 规则。
6. 设备侧建议**新增独立测试类**（5 例），不要往 `BackupRestoreInstrumentedTest` 里加例：KD-80
   80-C1 的证据口径是 `OK (34 tests)`，加例会改变基线（例如变成 `39/39`），后续台账无法对照。
   旧包用例的"四表为空"断言可**追加到既有 `schema68ArchiveRestoresAndMigratesToCurrentVersion`**
   （只加断言，不改例数）。
7. 读码发现一个**候选缺陷 / 待裁定**（读码推断，未执行验证）：恢复侧不重查"被引用快照必须 LIVE"
   与复合 `program_id` 一致性（§2.3 的写入门）。第三方重裁包把 link 指向 STAGING 快照、或改掉
   `link.program_id` 后，FK 与逐表行数都能过，恢复会接受；之后该图只读降级（不会画错，但用户
   已保存的图不可重放）。建议补 `RestoreVisualReferenceRecheck` 或登记为残留风险（§7）。
8. GitHub 参考的最佳组合：**SQLite 官方 PRAGMA 分层**（`integrity_check` 不含 FK，必须另跑
   `foreign_key_check`）＋ **androidx Room3 `MigrationTestHelper`**（导出 schema + 迁移前后断言行）
   ＋ **litestream**（restore 后按级别跑 `quick_check`/`integrity_check`）＋ **restic**（结构校验与
   数据校验分级）。meshtastic 的 Android DB 备份可借鉴 `user_version`/magic/`integrity_check` 与
   staged restore 形态，但它是 **GPL-3.0**，只借模式、不复制代码。

---

## 1. 事实闭包（本会话实读）

### 1.1 四张表与删除语义

| 表 | 主键 | 关键外键 / 约束 | 语义 |
|---|---|---|---|
| `tutor_visual_program` | `program_id` | 无 status 列；无 UPDATE 路径 | 内容派生不可变身份（`TutorVisualEntities.kt:36-72`） |
| `tutor_visual_snapshot` | `snapshot_id` | `program_id`→program **RESTRICT**；`preview_asset_id`→`canonical_source_asset` **RESTRICT**；`UNIQUE(program_id,state_sha256,draw_commands_sha256,renderer_version,font_manifest_version)` | 一次可重放快照；`status ∈ {STAGING,LIVE}`，LIVE 终态（`:116-211`） |
| `tutor_message_visual` | `(message_id, ordinal)` | `message_id`→`tutor_message` **CASCADE**；`program_id`/`current_snapshot_id` **RESTRICT** | 会话消息上的图引用（`:232-278`） |
| `problem_revision_visual` | `(problem_revision_id, role, ordinal)` | 三列全 **RESTRICT**（题面修订 / program / snapshot） | 保存题自己持有的固定图引用（`:298-339`） |

DDL 与 FK 逐字见 `VisualSnapshotMigration_69_70.kt:87-106`（snapshot）、`:137-149`（message
link）、`:167-178`（revision pin）；设计图的删除语义全图见
`docs/research/2026-10-29-visual-persistence-design.md:216-230`。

状态/角色值源：`TutorVisualStatus`（STAGING/LIVE）与 `TutorVisualCheckpointKind`
（INITIAL/LATEST，NULL=可回收中间态），`TutorVisualCommit.kt:13-25`。

### 1.2 写入侧的结构门（第三层不变量的来源）

- **只允许 LIVE 快照被消息引用**：`insertMessageVisualIfLive` 是
  `INSERT … SELECT … WHERE snapshot_id=? AND status='LIVE' AND program_id=?`
  （`TutorVisualDao.kt:345-367`）。非 LIVE 或跨 program 一行都写不进。
- **只允许 LIVE 快照被题面 pin**：`insertRevisionVisualIfLive` 同形
  （`TutorVisualDao.kt:476-497`）。
- **指针 CAS 也带复合 program 条件**：`repointMessageVisual` 的 `EXISTS` 同时校验
  `snapshot.status='LIVE' AND snapshot.program_id = tutor_message_visual.program_id`
  （`:397-420`）。
- **身份与摘要**：program id / snapshot id 由内容派生（`TutorVisualIdentity.kt:41-64,89-108`），
  写入前 `require` 重新派生并比对；state/draw 摘要必须等于 JSON 的 SHA-256
  （`RoomStudyDatabase.kt:2205-2220,2254-2274`）。**`scene_sha256` 当前尚未做内容绑定**（批次 33
  任务 4 才接线），见 §7。

### 1.3 备份裁剪的视觉规则（"本应缺失"的唯一事实源）

- 四表归属：`tutor_message_visual` ∈ SESSIONS、`problem_revision_visual` ∈ ISSUE_BOOK、
  `tutor_visual_program/snapshot` ∈ SPLIT（`SmbkSelectionPlan.kt:91,152,180-189`）。
- 裁剪顺序（子先父）：`tutor_message_visual` → `problem_revision_visual` →
  `tutor_visual_snapshot` → `tutor_visual_program`（`:205-212`）。
- 视觉行级保留集在**任何 DELETE 之前**预扫（`BackupSnapshotTrimmer.kt:745-789`）：

```text
keptSnapshot = { link.current_snapshot_id | SESSIONS 选中 }
             ∪ { revision_visual.snapshot_id | ISSUE_BOOK 选中 }
             ∪ { snapshot_id | checkpoint_kind = 'INITIAL' }
keptProgram  = { link.program_id | SESSIONS 选中 }
             ∪ { revision_visual.program_id | ISSUE_BOOK 选中 }
             ∪ { snapshot.program_id | snapshot ∈ keptSnapshot }
```

- 全选（15）直接跳过视觉行级预扫与删除（`:215-218,573-594`）。
- 允许的 mask 只有 9 / 13 / 15（`SmbkSelectionPlan.kt:60-66`）。三者都含 ISSUE_BOOK 与 IMAGES，
  所以 **`problem_revision_visual` 恒保留**；`tutor_message_visual` 只在 13/15 保留。
- 预览资产：保留快照的 `preview_asset_id` 进入 TRIMMED 资产 pin 集
  （`BackupSnapshotTrimmer.kt:1328-1334`），字节随包；快照被裁时其预览行仍在
  `canonical_source_asset`（IMAGES 类整表保留），但文件不必然随包。

### 1.4 恢复侧已经存在的校验（决定 80-C4 的真实缺口）

| 阶段 | 校验 | 是否覆盖四表 |
|---|---|---|
| Phase 2b | `PRAGMA quick_check` + `PRAGMA foreign_key_check`（`AndroidBackupRepository.kt:1121-1135`） | **是**（硬 FK；不含复合 program 一致性、不含 LIVE 语义） |
| Phase 2b | Room 生产 builder 打开验证副本、跑全量迁移、版本必须等于本机（`:1138-1165`） | 表存在性 / schema 版本 |
| Phase 2c.1 | manifest `rowCounts` vs 包内库 **90 表逐表**（`:1234-1310`） | **是**，但只是"声明=实测"，不证明声明正确 |
| Phase 2c.3 | `RestoreSoftReferenceRecheck`（`:566-575`；实现 `RestoreSoftReferenceRecheck.kt`） | **否**：只覆盖 `model_task*` / `figure_generation_*`，`grep tutor_visual` = 0 |
| Phase 3 | 对将要晋升的字节跑 `PRAGMA integrity_check`（`:1405-1442`） | 页级/索引/UNIQUE，**不含 FK**（SQLite 官方明确） |
| Phase 5 | 只读 `user_version`（`:1450-1475`） | 不做事后全检（全检已在 Phase 3） |

**因此 80-C4 的真实缺口是**：
(a) 没有任何用例用"已知源数据"证明四表的行数与引用在归档/恢复后仍是**期望的那一组**；
(b) 外键之外的语义（复合 program、LIVE-only、身份/摘要同源）没有恢复侧复核，也没有用例；
(c) 裁剪切面（哪些行**本应**缺席）没有被钉住，容易被误报为缺陷。

---

## 2. 恢复后必须成立的视觉四表不变量

记 `P/S/M/R` 分别为 `tutor_visual_program/snapshot/tutor_message_visual/problem_revision_visual`。
每条给"执行层"：**结构**＝数据库拒绝；**写入门**＝只有生产写入口拒绝；**测试**＝SQLite 表达不了。

### 2.1 行数与集合

| ID | 不变量 | 执行层 | 判据 |
|---|---|---|---|
| **V-R1** | 全选（mask=15）备份→恢复后，四表行数**等于成包前源库** | 测试（产品只查声明=实测） | `COUNT(*)` 四表 + 与源库比较 |
| **V-R2** | 子集（mask=9/13）备份→恢复后，四表行数**等于 §1.3 保留集** | 测试 | 期望 id 集合（Kotlin 计算） |
| **V-R3** | 恢复后四表行数**等于包内库**（逐表、逐 id、逐内容列） | 测试（恢复是字节复制，应恒等） | 归档库 vs 恢复库逐行比较 |
| **V-R4** | 派生表 `knowledge_search_feature` / `knowledge_search_index_state` **恒为 0 行** | 产品（Phase 2c.2，`:1325+`）＋测试 | `COUNT(*)=0`；与视觉四表无关但同批复核时一并断言，避免把"派生被裁"误读 |

> 行数只是必要条件。**必须同时比较 id 集合与内容列**：一增一减可以保持总数不变（§3.3 的期望
> 集合比较就是为此）。

### 2.2 引用完整性（"被引用快照不可缺"）

| ID | 不变量 | 执行层 | 判据 |
|---|---|---|---|
| **V-R5** | 每条 `M.current_snapshot_id` 都能在 `S` 解析到行 | **结构**（FK RESTRICT）＋测试 | `LEFT JOIN … IS NULL` 为空；`foreign_key_check` 空 |
| **V-R6** | 每条 `R.snapshot_id` 都能在 `S` 解析到行 | **结构**（FK RESTRICT）＋测试 | 同上 |
| **V-R7** | 每条 `M.program_id` / `R.program_id` / `S.program_id` 都能在 `P` 解析到行 | **结构**（FK RESTRICT）＋测试 | 同上 |
| **V-R8** | `M.program_id == S(M.current_snapshot_id).program_id` | **写入门**（`TutorVisualDao.kt:345-367,397-420`）→ 测试 | 复合不一致查询为空 |
| **V-R9** | `R.program_id == S(R.snapshot_id).program_id` | **写入门**（`TutorVisualDao.kt:476-497`）→ 测试 | 同上 |
| **V-R10** | `S.preview_asset_id` 非空时能解析到 `canonical_source_asset`；**保留快照的预览文件在恢复后的资产目录里** | 结构（FK）＋产品（`verifyStagedAssetClosure`，`:544-547`）＋测试 | FK 查询；文件存在 + 字节数/SHA 与资产行一致 |
| **V-R11** | 软引用 `P.basis_revision_id → problem_revision` 满足 **no-new-dangling**（裁剪后悬空集合 ⊆ 裁剪前） | 产品（裁剪器 `BackupSnapshotTrimmer.kt:1278-1288`）＋测试 | 悬空集合比较；允许历史悬空原样保留，**不许新增** |

### 2.3 状态与角色

| ID | 不变量 | 执行层 | 判据 |
|---|---|---|---|
| **V-R12** | `status ∈ {STAGING, LIVE}` | 写入门（`toEntity` 固定 STAGING，`promoteSnapshot` 固定 LIVE） | 域外值查询为空 |
| **V-R13** | `status='LIVE' ⇒ promoted_at_epoch_millis IS NOT NULL`；`status='STAGING' ⇒ promoted_at IS NULL` | 写入门（`promoteSnapshot` 同时写两列，`TutorVisualDao.kt:294-309`） | 两个方向各一条查询 |
| **V-R14** | **被 `M` 或 `R` 引用的快照必须 `status='LIVE'`** | 写入门（INV-4）→ 测试 | 引用×快照 join 后 `status<>'LIVE'` 为空 |
| **V-R15** | `checkpoint_kind IS NULL OR ∈ {INITIAL, LATEST}` | 写入门（策略只产出这两个值；`TutorVisualSnapshotWrite` 本身**没有** require） | 域外值查询为空 |
| **V-R16** | `M.revision >= 1` | 写入门（初次 1，CAS +1） | `< 1` 查询为空 |

> V-R14 是"被引用快照不可缺"的语义补充：快照存在但不可重放（STAGING）时，恢复不画错图，但
> `VisualSnapshotRestore.restore` 会以 `RESTORE_SNAPSHOT_NOT_LIVE` 降级为只读
> （`VisualSnapshotRestore.kt:205-210`），用户已保存的图不可重放。**产品恢复侧当前不重查
> V-R14**（§7 候选缺陷）。

### 2.4 身份与内容同源（防"复制过程中换内容"）

| ID | 不变量 | 执行层 | 判据 |
|---|---|---|---|
| **V-R17** | `program_id == deriveProgramId(sourceJs, initialStateJson, basisFingerprint, apiVersion, sdkVersion, role)` | 写入门（`RoomStudyDatabase.kt:2205-2220`） | 逐行重派生比较 |
| **V-R18** | `snapshot_id == deriveSnapshotId(programId, stateSha256, drawCommandsSha256, rendererVersion, fontManifestVersion)` | 写入门（`RoomStudyDatabase.kt:2271-2283`） | 逐行重派生比较 |
| **V-R19** | `state_sha256 == sha256(state_json)` 且 `draw_commands_sha256 == sha256(draw_commands_json)` | 写入门（`RoomStudyDatabase.kt:2258-2264`） | 逐行重算比较 |
| **V-R20** | `scene_sha256 == sha256(scene_json)` | **尚未接线**（批次 33 任务 4） | 接线后纳入；当前记为 `UNVERIFIED`（§7） |
| **V-R21** | 五列唯一键成立、无重复内容行 | **结构**（UNIQUE 索引，`VisualSnapshotMigration_69_70.kt:111-114` / 71 为五列） | 分组计数 `HAVING COUNT(*)>1` 为空；`PRAGMA index_list/index_info` 形状 |

> 恢复是字节复制，V-R17..R21 在源库成立时**理应在恢复后继续成立**；它们的作用是抓"包内被换
> 内容""第三方重裁""恢复路径改行"这类不会触发 FK 的破坏。

---

## 3. 可执行断言集合

### 3.1 两段式运行器（设计，未编译）

建议在 `core/data/src/androidTest/.../backup/` 新增一个纯测试工具（例如
`VisualFourTableChecker.kt`），输入是**一个已打开的 `SQLiteDatabase` 连接**，输出不可变快照 +
违规清单。两个调用点：归档解出的 `database.sqlite`、恢复后重开的库。

```kotlin
internal data class VisualFourTableSnapshot(
    val programIds: Set<String>,
    val snapshotIds: Set<String>,
    val snapshotStatus: Map<String, String>,        // snapshot_id -> status
    val snapshotKinds: Map<String, String?>,        // snapshot_id -> checkpoint_kind
    val messageLinks: Set<Pair<String, Int>>,       // (message_id, ordinal)
    val revisionPins: Set<Triple<String, String, Int>>, // (revision_id, role, ordinal)
    val programRows: Map<String, ProgramContent>,   // 内容列，逐行比较用
    val snapshotRows: Map<String, SnapshotContent>,
    val messageRows: Map<Pair<String, Int>, LinkContent>,
    val revisionRows: Map<Triple<String, String, Int>, RevisionContent>,
    val violations: List<VisualInvariantViolation>,
)

/** 只读；不改库、不建临时表、不依赖生产端口。失败项带 ID 与样例主键。 */
internal fun checkVisualFourTables(db: SQLiteDatabase): VisualFourTableSnapshot
```

设计约束（对齐仓库纪律）：

- **不新增生产 API**。四表读侧已够用（`TutorVisualPort.kt:86-116`），但缺 `problem_revision_visual`
  与批量 join；测试直接用 `SQLiteDatabase.rawQuery`（既有先例：
  `BackupRestoreInstrumentedTest.kt:1522-1532,244-251`），不为了测试给端口加方法。
- **不复制裁剪 SQL**。期望集合在 Kotlin 用 §1.3 的公式从源行计算；SQL 只做"库内自洽"检查。
- **标识符是编译期常量**，值必须参数绑定（仓库安全约束；本 doc 的 SQL 用 `?`）。
- **通过 SQLite 连接读**，不要复制主文件字节做比较：恢复后的库处于 WAL 模式，未 checkpoint 的
  已提交行只在 `-wal` 里（KD-81 / `docs/known-defects.md` KD-81 的受控实验已证明同主库不同 WAL
  会产生不同视图）。复制字节会把 WAL 中的行误判为"缺失"。
- **旧包（schema < 70）的四表可能根本不存在**：checker 必须以 `sqlite_master` 的表存在性为准，
  把"表缺失"与"表存在但 0 行"视为同一期望（0 行）；不能因为 `SELECT` 抛
  `no such table` 就判红（非破坏迁移的正确形态，`VisualSnapshotMigration_69_70.kt:15-18`）。

### 3.2 SQL 断言清单（库内自洽）

**S1 计数（四表 + 派生表）**

```sql
SELECT 'tutor_visual_program' AS table_name, COUNT(*) AS n FROM tutor_visual_program
UNION ALL SELECT 'tutor_visual_snapshot', COUNT(*) FROM tutor_visual_snapshot
UNION ALL SELECT 'tutor_message_visual', COUNT(*) FROM tutor_message_visual
UNION ALL SELECT 'problem_revision_visual', COUNT(*) FROM problem_revision_visual
UNION ALL SELECT 'knowledge_search_feature', COUNT(*) FROM knowledge_search_feature
UNION ALL SELECT 'knowledge_search_index_state', COUNT(*) FROM knowledge_search_index_state;
```

**S2 外键（V-R5..R7、R10 的粗网）**

```sql
PRAGMA foreign_key_check;
-- 通过：0 行
```

**S3 孤儿引用（点名到主键，FK 之外的第二道；也用于 FK 被关掉的第三方包）**

```sql
-- 消息 link → 快照
SELECT link.message_id, link.ordinal, 'snapshot' AS missing
FROM tutor_message_visual AS link
LEFT JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
WHERE s.snapshot_id IS NULL
UNION ALL
-- 题面 pin → 快照
SELECT r.problem_revision_id, r.ordinal, 'snapshot'
FROM problem_revision_visual AS r
LEFT JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
WHERE s.snapshot_id IS NULL
UNION ALL
-- link / pin / snapshot → program
SELECT link.message_id, link.ordinal, 'program'
FROM tutor_message_visual AS link
LEFT JOIN tutor_visual_program AS p ON p.program_id = link.program_id
WHERE p.program_id IS NULL
UNION ALL
SELECT r.problem_revision_id, r.ordinal, 'program'
FROM problem_revision_visual AS r
LEFT JOIN tutor_visual_program AS p ON p.program_id = r.program_id
WHERE p.program_id IS NULL
UNION ALL
SELECT s.snapshot_id, 0, 'program'
FROM tutor_visual_snapshot AS s
LEFT JOIN tutor_visual_program AS p ON p.program_id = s.program_id
WHERE p.program_id IS NULL;
```

**S4 复合 program 一致性（V-R8/R9；FK 表达不了）**

```sql
SELECT link.message_id, link.ordinal, 'message' AS src
FROM tutor_message_visual AS link
JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
WHERE s.program_id <> link.program_id
UNION ALL
SELECT r.problem_revision_id, r.ordinal, 'revision'
FROM problem_revision_visual AS r
JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
WHERE s.program_id <> r.program_id;
-- 通过：0 行
```

**S5 被引用快照必须是 LIVE（V-R14）**

```sql
SELECT 'message' AS src, link.message_id AS id, link.ordinal AS ord
FROM tutor_message_visual AS link
JOIN tutor_visual_snapshot AS s ON s.snapshot_id = link.current_snapshot_id
WHERE s.status <> 'LIVE'
UNION ALL
SELECT 'revision', r.problem_revision_id, r.ordinal
FROM problem_revision_visual AS r
JOIN tutor_visual_snapshot AS s ON s.snapshot_id = r.snapshot_id
WHERE s.status <> 'LIVE';
-- 通过：0 行；前提是源库满足该不变量（生产写入门保证）
```

**S6 状态/晋升配对（V-R12/R13）**

```sql
SELECT snapshot_id, status, promoted_at_epoch_millis
FROM tutor_visual_snapshot
WHERE status NOT IN ('STAGING','LIVE')
   OR (status = 'LIVE'    AND promoted_at_epoch_millis IS NULL)
   OR (status = 'STAGING' AND promoted_at_epoch_millis IS NOT NULL);
```

**S7 checkpoint 角色域（V-R15）**

```sql
SELECT snapshot_id, checkpoint_kind
FROM tutor_visual_snapshot
WHERE checkpoint_kind IS NOT NULL
  AND checkpoint_kind NOT IN ('INITIAL','LATEST');
```

**S8 link revision 域（V-R16）**

```sql
SELECT message_id, ordinal, revision
FROM tutor_message_visual
WHERE revision < 1;
```

**S9 内容唯一键（V-R21）**

```sql
SELECT program_id, state_sha256, draw_commands_sha256,
       renderer_version, font_manifest_version, COUNT(*) AS n
FROM tutor_visual_snapshot
GROUP BY program_id, state_sha256, draw_commands_sha256,
         renderer_version, font_manifest_version
HAVING n > 1;
-- 通过：0 行
```

**S10 软引用悬空（V-R11）**

```sql
SELECT p.program_id, p.basis_revision_id
FROM tutor_visual_program AS p
WHERE p.basis_revision_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM problem_revision AS r WHERE r.revision_id = p.basis_revision_id
  );
-- 断言：post 悬空集合 ⊆ pre 悬空集合（no-new-dangling）
```

### 3.3 期望集合（Kotlin，mask 9/13/15）

```kotlin
data class SourceVisualRows(
    val programs: Set<String>,
    val snapshots: Map<String, SourceSnapshot>, // id -> (programId, status, kind)
    val messageLinks: Set<MessageLink>,         // (messageId, ordinal, programId, snapshotId)
    val revisionPins: Set<RevisionPin>,         // (revisionId, role, ordinal, programId, snapshotId)
)

fun expectedVisualRows(rows: SourceVisualRows, mask: Int): ExpectedVisualRows {
    require(mask == 9 || mask == 13 || mask == 15)
    val sessions = mask and 4 != 0
    val issueBook = mask and 1 != 0
    if (mask == 15) {
        return ExpectedVisualRows(
            programs = rows.programs,
            snapshots = rows.snapshots.keys,
            messageLinks = rows.messageLinks,
            revisionPins = rows.revisionPins,
        )
    }
    val keptSnapshotIds = buildSet {
        if (sessions) rows.messageLinks.forEach { add(it.snapshotId) }
        if (issueBook) rows.revisionPins.forEach { add(it.snapshotId) }
        rows.snapshots.forEach { (id, s) ->
            if (s.kind == "INITIAL") add(id)
        }
    }
    val keptProgramIds = buildSet {
        if (sessions) rows.messageLinks.forEach { add(it.programId) }
        if (issueBook) rows.revisionPins.forEach { add(it.programId) }
        keptSnapshotIds.forEach { id -> add(rows.snapshots.getValue(id).programId) }
    }
    return ExpectedVisualRows(
        programs = keptProgramIds,
        snapshots = keptSnapshotIds,
        messageLinks = if (sessions) rows.messageLinks else emptySet(),
        revisionPins = if (issueBook) rows.revisionPins else emptySet(),
    )
}
```

> 注意：**全选 15 也走裁剪器**，但视觉四表不在删除范围（§1.3），所以 `expected == source`。
> 派生表永远是 0，不属于视觉期望集合。

### 3.4 判定顺序与归因

对同一份归档依次做：

```text
expected = expectedVisualRows(sourceRows, mask)
A = checkVisualFourTables(archiveDatabase)     // 包内库
B = checkVisualFourTables(restoredDatabase)    // 恢复后的库
```

| A vs expected | B vs A | 判定 |
|---|---|---|
| 相等 | 相等 | **PASS**（80-C4 绿） |
| 相等 | 不等 | **恢复缺陷（B 类）**：包对、恢复路径改/丢行 |
| 不等 | 相等 | **裁剪/成包缺陷（A 类）**：恢复忠实复制了错误的包 |
| 不等 | 不等 | 先按 A 归因（裁剪规则/期望公式），修好后再验 B |

再加两条独立门：`A.violations == []` 且 `B.violations == []`（S2–S10），否则无论集合是否相等都红。
失败信息必须带不变量 ID + 样例主键（与仓库既有失败文案风格一致，如
`BackupSnapshotTrimmer.kt:357-362`）。

---

## 4. "备份裁剪后本应缺失" vs "缺陷缺失"

### 4.1 允许 mask 的保留矩阵

| 行类 | mask 9（题+图） | mask 13（题+会话+图） | mask 15（全选） |
|---|---|---|---|
| `tutor_message_visual` | **0 行**（SESSIONS 未选，整表删） | 全部保留 | 全部保留 |
| `problem_revision_visual` | 全部保留（ISSUE_BOOK 恒选） | 全部保留 | 全部保留 |
| `tutor_visual_snapshot` | `keptSnapshot(9)` | `keptSnapshot(13)` | 全部保留 |
| `tutor_visual_program` | `keptProgram(9)` | `keptProgram(13)` | 全部保留 |
| `knowledge_search_feature` | 0 | 0 | 0 |
| `knowledge_search_index_state` | 0 | 0 | 0 |

其中（mask ∈ {9,13}）：

```text
keptSnapshot(9)  = { R.snapshot_id } ∪ { INITIAL }
keptSnapshot(13) = { M.current_snapshot_id } ∪ { R.snapshot_id } ∪ { INITIAL }
keptProgram(m)   = { 对应 keep 的 M/R.program_id } ∪ { keptSnapshot.program_id }
```

### 4.2 本应缺失（不得报缺陷）

1. **未选类别的整表**：mask 9 的 `tutor_message_visual` = 0；任何未选类别表 = 0。
2. **派生索引恒裁**：`knowledge_search_feature` / `knowledge_search_index_state` = 0。
3. **未被引用且非 INITIAL 的 STAGING 中间态**：任何 mask ≠ 15 都被裁（`BackupSnapshotTrimmer.kt:756-789`）。
4. **只被消息引用的快照**：mask 9 下被裁（SESSIONS 未选），mask 13/15 下保留。
5. **只被已裁快照引用的 program**：随快照一起裁。
6. **失去消息引用、且非 INITIAL 的 LIVE 快照**：mask ≠ 15 下被裁（当前 keep 规则不含 LIVE；
   全选保留）。这是**最容易被误报**的一条，见 §4.5。
7. **旧包（schema ≤69）迁移后的四表**：空表是正确形态（非破坏迁移，`VisualSnapshotMigration_69_70.kt:15-18`）。
8. **被裁快照的预览文件**：快照不在，预览不必随包（其 `canonical_source_asset` 行可能仍在，
   但文件缺失是合法的"未引用资产"状态）。

### 4.3 缺陷缺失（必须红）

1. **全选 15 下四表任一行消失**（全选不裁视觉行；`BackupSnapshotTrimmer.kt:573-594` 的守卫）。
2. **期望集合内的行缺失**：kept snapshot/program/link/pin 少一行。
3. **期望集合外的行残留**：未选类别表非 0、派生表非 0、应裁的 STAGING/消息-only 快照仍在。
4. **被引用快照缺失**：link/pin 指向的 snapshot 行不在（FK 会先抓到，但测试要能点名）。
5. **复合 program 不一致**：`M.program_id ≠ S.program_id` 或 `R.program_id ≠ S.program_id`。
6. **被引用快照不是 LIVE**（V-R14）。
7. **内容被换**：行数/集合相同，但 `program_id`/`snapshot_id` 与内容重派生不符、state/draw 摘要
   与 JSON 不符、或归档与恢复的同 id 行内容列不同。
8. **软引用新增悬空**：`P.basis_revision_id` 的悬空集合比成包前更大。

### 4.4 判定速查（回答"这个缺失是裁剪还是缺陷"）

```text
缺失行 ∈ expected?          是 → 缺陷（应保留却缺）
缺失行 ∉ expected?
  ├─ 属于 §4.2 的 1–8 类?   是 → 本应缺失（不是缺陷）
  └─ 不属于                 是 → 缺陷（裁剪规则外丢失）
```

### 4.5 边界与待裁定

- **LIVE 未固定快照在子集包里消失**：当前 keep 规则只认"被引用 ∪ INITIAL"，不认 LIVE
  （`BackupSnapshotTrimmer.kt:768-777`）。如果产品认为"用户看过的图必须随任何备份走"，则应把
  `status='LIVE'` 加入 keep 规则；否则应在文档中明确"子集备份可能丢已见但已失去引用的图"。
  本轨不擅自改规则，登记为**待裁定**（§7）。
- **`basis_revision_id` 历史悬空**：no-new-dangling 允许原样保留，不能断言"必须解析到行"。
- **预览资产**：保留快照的预览必须随包；被裁快照的预览可以只有行没有文件。两者的断言不同。
- **WAL**：断言必须经 SQLite 连接读（§3.1），不要复制主文件字节。

---

## 5. 设备侧最小用例清单与判据

### 5.1 落点建议

- **新增** `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupVisualReferenceInstrumentedTest.kt`
  （5 例）。理由：KD-80 80-C1 的证据口径是 `BackupRestoreInstrumentedTest` 的 `OK (34 tests)`；
  往同一类加例会改变基线与既有台账的对照关系。
- **既有** `BackupRestoreInstrumentedTest.schema68ArchiveRestoresAndMigratesToCurrentVersion`
  （`:488`）只**追加断言**（四表存在且 0 行），不改例数；该文件当前是 ` M`
  （`git status` 实读），若改它必须整文件纳入并在提交说明里注明（批次纪律）。
- 负向重裁包用例可追加到 `BackupSelectionRestoreInstrumentedTest`（干净文件），复用其
  `repackWithModifiedDatabase`（`:762`）/ `rewriteManifest`（`:736`）。

### 5.2 公共夹具（正路径，尽量走生产写入口）

```text
seedStudyFacts(...)                          // problem-backup / revision-backup（:2304-2354）
createTutorConversation(C1)
commitTutorTurnWithVisual(M1, C1, P1, S1)    // P1 首个 checkpoint → S1 = INITIAL, LIVE, link L1
commitTutorTurnWithVisual(M2, C1, P1, S2)    // 同 program 第二次 commit → S2 = LATEST, LIVE, link L2
stageTutorVisualSnapshot(P2, S3)             // P2 首个 stage → S3 = STAGING, kind = INITIAL（无引用）
stageTutorVisualSnapshot(P3, S4)             // S4 = STAGING, kind = null（无引用；显式不设 INITIAL）
raw pin revision-backup/QUESTION_DIAGRAM/0 → (P1, S1)   // 端口无 pin 方法，按结构门测试的 raw 夹具
```

> **为什么两次 commit 必须同 program**：`nextCheckpointKind` 只在"本 program 已有 INITIAL"时
> 返回 LATEST（`VisualCheckpointRetention.kt:151-156`；`TutorVisualCommitRepository.kt:253-268`
> 落入 `checkpointKind`）。若第二次 commit 用另一个 program，它仍是该 program 的 INITIAL，
> 测不到 LATEST 行。
> `stageTutorVisualSnapshot` 对空 program 的首个 checkpoint 取 INITIAL，所以 S3 是产品可达的
> "STAGING + INITIAL"（经仓储 `stageSnapshot`，`TutorVisualCommitRepository.kt:253-268`）。
> **S4（STAGING + null）不能走仓储的 `stageSnapshot`**：仓储会用 `plan.incomingKind` 覆盖调用方
> 传入的 kind（同上 `:266-268`），空 program 必然得到 INITIAL。要造可回收中间态，直接调端口
> `StudyDatabasePort.stageTutorVisualSnapshot`，传 `TutorVisualSnapshotWrite(checkpointKind = null)`
> （端口不做 kind 覆盖，`TutorVisualSnapshotWrite` 也不 require kind）；或按结构门测试的
> `rawSnapshot(..., kind = null)` raw SQL 夹具。两条都只用于造"合法但非仓库默认"的行，不放宽生产门。
> `problem_revision_visual` 的写入没有端口方法（`TutorVisualPort.kt` 无 pin），用 raw SQL 且
> 必须满足 FK；这是测试夹具，不是放宽生产门（同 `TutorVisualStructureGatesInstrumentedTest.kt:431-446`）。

源计数：`P=3（P1,P2,P3）, S=4, M=2, R=1`。期望：

| mask | P | S | M | R | 被裁（本应缺失） |
|---|---|---|---|---|---|
| 15 | 3 | 4 | 2 | 1 | 无 |
| 13 | 2（P1,P2） | 3（S1,S2,S3） | 2 | 1 | P3（只被 S4 引用）、S4（STAGING 中间态） |
| 9 | 2（P1,P2） | 2（S1,S3） | **0** | 1 | P3、S2（只被消息引用）、S4；M 整表 |

这张表同时钉住三件事：**保存题图不依赖会话**（mask 9 仍保留 S1/R1）、**INITIAL 基线不因未引用而消失**
（S3 在两个子集里都保留，P2 也随之保留）、**消息-only 快照随会话类别走**（S2 在 mask 9 被裁、13 保留）。

### 5.3 五个正/负向用例

| # | 用例名（建议） | 建包 mask | 核心断言 | 通过判据 |
|---|---|---|---|---|
| 1 | `fullSelectionRoundTripPreservesAllVisualRowsAndReferences` | 15 | 源→归档→恢复三次 `checkVisualFourTables`；四表 id 集合与内容行恒等；S2–S10 全空 | 三次快照相等；`violations` 空 |
| 2 | `subsetNineKeepsRevisionPinsAndInitialBaselinesOnly` | 9 | `M=0`；`R=1`；快照集合 `{S1,S3}`；program 集合 `{P1,P2}`；S1 的 pin 仍解析；S2/S4 不在 | 集合逐项相等；S2–S10 空 |
| 3 | `subsetThirteenKeepsMessageLinksButTrimsUnreferencedStaging` | 13 | `M=2`；快照 `{S1,S2,S3}`；program `{P1,P2}`；S4 不在；被引用快照均 LIVE | 集合逐项相等；S5 空 |
| 4 | `archiveAndRestoredDatabaseAgreeOnVisualRowIdentity` | 15 | 归档库 vs 恢复库：四表按主键排序的内容列逐行相等；`integrity_check` 均 `ok` | 逐行 diff 为空；两库 `integrity_check` = ok |
| 5 | `repackagedArchiveWhoseLinkPointsAtAMissingSnapshotIsRejectedBeforeSwap` | 13（第三方重裁） | 删除被 link 引用的 snapshot（或改其 id）并重写 checksum；恢复必须在交换前拒绝，原库不动 | 抛 `BackupRestoreException`，消息含 `foreign_key_check`；`countMistakes()` 不变 |

用例 5 的可行性已在同类测试里被证明：`repackWithModifiedDatabase` 会改包内库、checkpoint 回主文件、
重写 `rowCounts`/`closureDigest`/`checksums.sha256`（`BackupSelectionRestoreInstrumentedTest.kt:762-800`），
能绕过成包侧 I1–I7 只让恢复侧抓。恢复侧的 FK 检查在 Phase 2b（`:1121-1135`），早于 Phase 2c.1，
所以删行后无需伪造行数也会先被 FK 抓住；断言文案应指向 `foreign_key_check`。

### 5.4 可选第 6 例（LIVE 未固定）

`subsetRoundTripDropsALiveSnapshotThatLostItsMessageByDesign`：为**同一个新 program**连续 commit 两次
（得到该 program 的 INITIAL + LATEST，两者都 LIVE），再删除承载它们的会话（link 级联消失，快照仍在）。
建 mask 13 包，断言：INITIAL 快照**在**包内（INITIAL 规则），LATEST 快照**不在**（当前 keep 规则不含
LIVE）。这一例的价值是**防止未来把"LIVE 消失"误报为缺陷**；如果产品改规则（把 LIVE 加入 keep），
用例期望随之改，不允许放宽。注意：若只 commit 一次，该 program 的快照是 INITIAL，会因 INITIAL 规则
保留，测不到"LIVE 非 INITIAL 被裁"。

### 5.5 设备命令与判据

```bash
A=adb
S=<serial>   # 批次 30/31/32 记录使用 emulator-5562；执行前重新确认
$A -s "$S" logcat -c
$A -s "$S" shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.backup.BackupVisualReferenceInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch33/step-c4-visual.log
```

通过判据：

- `OK (5 tests)`，0 failures / 0 errors / 0 ignored；
- logcat 无 `BackupSnapshotTrimException`、无 `SQLiteException`、无 `foreign_key_check` 相关异常；
- 同轮 `BackupRestoreInstrumentedTest` 仍为 `OK (34 tests)`（80-C1 基线不变）；
- 设备 APK **必须由当前树重建**（批次纪律：先 `assemble*` + `install -r -t`，再 `am instrument`；
  不得引用旧构建的绿轮）。

失败归因：

| 现象 | 归因 |
|---|---|
| 用例 1 红、其余绿 | 全选路径裁掉了视觉行 → **裁剪器 A 类**（`mask != FULL` 守卫被破坏） |
| 用例 2/3 的红与期望表不符，但归档与恢复一致 | **期望公式/裁剪规则 A 类**（先核对 §1.3 与实现） |
| 归档绿、恢复红 | **恢复 B 类**（复制/迁移/交换） |
| 用例 5 未拒绝 | 恢复侧 FK 门失效 → **恢复 B 类**，KD-80 复现 |
| 设备数与源码不符 | 环境/构建不同源，先重建重装 |

---

## 6. GitHub 参考评分（备份恢复一致性测试）

### 6.1 检索方法与通道说明

- `gh search repos "backup restore consistency test sqlite"` 与
  `gh search repos "database backup restore integrity test"` 返回 `[]`（该多词组合未命中；
  不据此断言"不存在"）；换单词查询 `gh search repos "sqlite backup"`、
  `gh search repos "sqlite integrity check backup"` 有结果。
- `gh search code "foreign_key_check language:kotlin"` → 1144 条，surface 出
  `signalapp/Signal-Android`、`gkd-kit/gkd`、`meshtastic/Meshtastic-Android` 等；
  `gh search code "backup restore integrity_check language:kotlin"` → 299 条，surface 出 meshtastic。
- `developer.android.com` 的 WebFetch 超时（连接超时），换通道改读 androidx 源码原文件；
  SQLite 官方文档 WebFetch 成功。
- 元数据（stars / license / pushed_at）全部用
  `gh api repos/<owner>/<repo> --jq '{...}'` 实取，不是记忆。

### 6.2 评分表（5 维，各 0–5；满分 25）

| 参考 | 类型 / 许可 / 最近推送 / stars | 相关 | 可执行 | 维护 | 许可 | 权威 | 总分 | 借用点 / 不采用 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| **SQLite 官方 PRAGMA 文档** `sqlite.org/pragma.html`、`lang_vacuum.html` | 官方文档 / Public Domain / 文档稳定 | 5 | 5 | 5 | 5 | 5 | **25** | `integrity_check` **不含 FK**，必须另跑 `foreign_key_check`；`quick_check` 跳过 UNIQUE 与索引内容；`VACUUM INTO` 是事务性一致快照。直接采用为门的分层依据。 |
| **androidx Room3 `MigrationTestHelper`** `androidx/androidx` | 官方测试库 / Apache-2.0 / 2026-10-29 / 6.1k | 4 | 5 | 5 | 5 | 5 | **24** | 导出 schema + 迁移前后"插行→迁移→断言行仍在"的官方范式。本仓已有**同型**的"导出 schema 与源码逐字对拍"门（`SmbkSelectionPlanTest` 对机器块、`TutorVisualSchemaContractTest` 对 DDL），但没有用该 helper 做"行仍在"断言。不采用：它只验 schema 身份与迁移，不验引用语义。 |
| **litestream** `benbjohnson/litestream` | SQLite 复制/恢复 / Apache-2.0 / 2026-10-28 / 14.5k | 5 | 4 | 5 | 5 | 4 | **23** | restore 提供 `-integrity-check none\|quick\|full`（`cmd/litestream/restore.go:78-89`），实现直接用 `PRAGMA quick_check/integrity_check` 并在非 `ok` 时失败（`replica.go:1343-1365`）；soak 用例周期性 stop→restore→`integrity_check`（`tests/integration/soak_replicate_restore_test.go`）。不采用：复制单位是 LTX 页，没有关系语义。 |
| **restic** `restic/restic` | 备份仓库 / BSD-2-Clause / 2026-10-18 / 36.9k | 3 | 4 | 5 | 5 | 4 | **21** | `restic check` 默认只验结构一致性，`--read-data` 才验真实数据（`doc/man/restic-check.1`）；有清晰退出码。借用"结构校验 vs 数据校验分级"的思路；不采用其仓库模型。 |
| **meshtastic/Meshtastic-Android** `core/database/DatabaseBackup.kt` | Android DB 备份 / **GPL-3.0** / 2026-10-29 / 1.9k | 4 | 4 | 5 | 1 | 2 | **16** | `user_version` + SQLite magic 头校验、`PRAGMA integrity_check`、`VACUUM INTO` 单文件、staged `.restore`/`.bak` 原子替换；jvmTest 断言"导出含 WAL 行 + `countRows`"（`DatabaseBackupJvmTest.kt`）。**只借模式，不复制代码**（GPL-3.0 与本仓许可不相容的风险）。 |

### 6.3 对本仓的直接采纳结论

1. **门的顺序**照 SQLite 官方与既有产品实现：`foreign_key_check`（引用）+ `integrity_check`（页/索引/
   UNIQUE）各自独立，不能互相替代；`quick_check` 只能做交换前快速门。
2. **测试结构**照 androidx：导出/已知 schema + 已知行 → 迁移/恢复 → 断言行与内容；但补上它不覆盖的
   引用语义（S3–S10）。
3. **验证分级**照 litestream/restic：结构（集合/FK）→ 内容（逐行/摘要）→ 页级（integrity_check），
   三层都跑；不要用一层冒充三层。
4. **Android 形态**只借 meshtastic 的模式（单文件快照、头/版本门、staged restore），代码不引入。

---

## 7. 未验证项、残留风险与待裁定

### 7.1 本轨未执行（`UNVERIFIED`）

- 未运行 Gradle（本轨纪律：门禁由脚本串行执行），因此 §3 的 checker 与 §5 的用例**均未编译、未执行**。
- 未上设备；§5.5 的 `OK (5 tests)` / `OK (34 tests)` 是**通过判据**，不是本轮结果。
- 未修改任何源文件；本 doc 是唯一产物。

### 7.2 读码发现的残留风险（未执行验证）

1. **恢复侧不重查 V-R14 / V-R8/R9**：`inspectStagedDatabase` 只跑 FK；`verifyDeclaredSelection…`
   只比行数；`RestoreSoftReferenceRecheck` 不覆盖视觉表（`grep tutor_visual` = 0）。第三方重裁包把
   link 指向 STAGING 快照、或改 `link.program_id`，**按读码推断**会被接受；之后恢复路径以
   `RESTORE_SNAPSHOT_NOT_LIVE` 只读降级（不会画错图，但不可重放）。建议二选一：
   (a) 新增 `RestoreVisualReferenceRecheck`（结构可重算的两条：复合 program 与 LIVE-only），
   在 Phase 2c.3 同位置拒绝；(b) 在 KD 台账登记为已知残留，并在 80-C4 用例里把该形态标为
   "产品不拒绝，测试只做检测"。
2. **`scene_sha256` 未与内容绑定**（`RoomStudyDatabase.kt:2258-2264` 只绑 state/draw；批次 33 任务 4
   才加 scene）。在接线前，V-R20 不能作为合并门；接线后可纳入。
3. **`checkpoint_kind` 无域校验**：`TutorVisualSnapshotWrite` 不 require，schema 无 CHECK；V-R15 只能
   靠测试发现。恢复侧也不重查。
4. **子集备份丢 LIVE 未固定快照**（§4.5）：是当前 keep 规则的直接后果，需产品确认是否接受。

### 7.3 待裁定（需运行 owner / 产品）

- 子集（9/13）是否必须保留"已见但已失去引用的 LIVE 快照"？若要，改
  `prepareVisualFilter` 的 keep 规则（加 `status='LIVE'`），并同步 §4.1/§5.2 的期望表。
- 是否为恢复侧补 `RestoreVisualReferenceRecheck`（§7.2 #1）。这会改变恢复的拒绝面，属于产品行为变更。
- 80-C4 的证据形式：新增独立类（推荐）还是并入 34 例基线。

### 7.4 热区与台账注意

- `BackupRestoreInstrumentedTest.kt` 当前 ` M`（`git status` 实读）；若实现轨改它，按批次纪律
  **整文件纳入并注明**，或只新增独立文件。
- 本轨不改 `core/data/src/main`、不改 schema（仍 71）、不碰 `core:database` 迁移。
- 台账回改时只写本 doc 的**设计结论**，不得把 `OK (5 tests)` 写成已跑；80-C4 在设备绿轮前仍是
  `UNVERIFIED`。

---

## 8. 来源

### 8.1 仓内代码/文档（本会话实读）

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/TutorVisualEntities.kt:36-339`（四表列/FK/索引/语义）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/VisualSnapshotMigration_69_70.kt:15-188`（DDL 与 FK；非破坏迁移说明）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorVisualDao.kt:294-420,476-551`（LIVE-only 结构门、指针 CAS、pin）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualCommit.kt:13-25,75-173,398-456`（状态/角色、写入载荷、内容比对）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualIdentity.kt:41-108`（program/snapshot 身份派生）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt:2205-2283`（写入前重派生、state/draw 摘要绑定）
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/TutorVisualPort.kt:1-116`（端口读侧；无 pin 方法）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt:44-51,60-66,91,152,180-212,501-509,470-478`（schema 71、mask、四表归属、裁剪顺序、软引用）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt:205-264,350-479,745-789,1278-1334`（I1–I7、视觉预扫、资产 pin）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:194-280,479-575,1095-1170,1234-1310,1400-1475`（成包、恢复阶段、quick/FK、逐表行数、integrity）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreSoftReferenceRecheck.kt`（只覆盖 model_task/figure，不含视觉）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualSnapshotRestore.kt:92-251`（LIVE/版本/字体门与只读降级）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualCheckpointRetention.kt:145-231`（INITIAL/LATEST、LIVE 不淘汰）
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt:163-212,488-620,1522-1581,2299-2354`（往返、旧包、解包闭包、seed）
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSelectionRestoreInstrumentedTest.kt:409-520,736-800`（第三方重裁/拼接夹具）
- `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualStructureGatesInstrumentedTest.kt:56-131,317-476`（四表 raw 夹具与结构门用例形态）
- `docs/research/2026-10-29-visual-persistence-design.md:102-260`（INV-1..7、四表设计、备份归位、删除语义）
- `docs/research/2026-10-29-batch-31-device-criteria.md:368-480`（KD-80 C1–C4 判据与 §3.4 缺口）
- `docs/known-defects.md:2969-3006`（KD-80 台账与关闭条件原文）
- `.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md:11-31`（本批任务与验收）

### 8.2 外部参考（本会话实取）

- SQLite, `PRAGMA foreign_key_check` / `integrity_check` / `quick_check`：
  <https://www.sqlite.org/pragma.html#pragma_foreign_key_check>
  - 原文：`integrity_check` "does not find FOREIGN KEY errors. Use the PRAGMA foreign_key_check"；
    `quick_check` "does not verify UNIQUE constraints and does not verify that index content matches
    table content"。
- SQLite, `VACUUM INTO`：<https://www.sqlite.org/lang_vacuum.html>
  - 原文：`VACUUM INTO` "generated output database is a consistent snapshot of the original database"，
    原库不变、新文件经过 vacuum。
- androidx Room3 `MigrationTestHelper`（Apache-2.0；默认分支 `androidx-main`）：
  <https://github.com/androidx/androidx/blob/androidx-main/room3/room3-testing/src/commonMain/kotlin/androidx/room3/testing/MigrationTestHelper.kt>
  - KDoc 示例：旧版本建库插行 → `runMigrationsAndValidate` → "Validates data is preserved between
    migrations"；依赖导出 schema（`exportSchema`）。
- litestream（Apache-2.0；`main`；14,549 stars；pushed 2026-10-28）：
  - restore `-integrity-check` 选项：<https://github.com/benbjohnson/litestream/blob/main/cmd/litestream/restore.go>
  - `quick_check`/`integrity_check` 实现：<https://github.com/benbjohnson/litestream/blob/main/replica.go>
  - 复制→恢复 soak：<https://github.com/benbjohnson/litestream/blob/main/tests/integration/soak_replicate_restore_test.go>
- restic（BSD-2-Clause；`master`；36,868 stars；pushed 2026-10-18）：
  - `restic check` 手册：<https://github.com/restic/restic/blob/master/doc/man/restic-check.1>
  - 恢复文档：<https://github.com/restic/restic/blob/master/doc/050_restore.rst>
- meshtastic/Meshtastic-Android（GPL-3.0；`main`；1,893 stars；pushed 2026-10-29）：
  - `DatabaseBackup.kt`：<https://github.com/meshtastic/Meshtastic-Android/blob/main/core/database/src/commonMain/kotlin/org/meshtastic/core/database/DatabaseBackup.kt>
  - `DatabaseBackupJvmTest.kt`：<https://github.com/meshtastic/Meshtastic-Android/blob/main/core/database/src/jvmTest/kotlin/org/meshtastic/core/database/DatabaseBackupJvmTest.kt>

### 8.3 检索命令（可复跑）

```bash
gh search repos "sqlite backup" --limit 10
gh search repos "sqlite integrity check backup" --limit 10
gh api "search/code?q=foreign_key_check+language:kotlin" --jq '.total_count, (.items[:8][] | .repository.full_name + " :: " + .path)'
gh api "search/code?q=backup+restore+integrity_check+language:kotlin" --jq '.total_count, (.items[:8][] | .repository.full_name + " :: " + .path)'
gh api repos/benbjohnson/litestream --jq '{full_name, stargazers_count, license: .license.spdx_id, pushed_at, archived}'
gh api repos/restic/restic --jq '{full_name, stargazers_count, license: .license.spdx_id, pushed_at, archived}'
gh api repos/meshtastic/Meshtastic-Android --jq '{full_name, stargazers_count, license: .license.spdx_id, pushed_at, archived}'
gh api repos/androidx/androidx --jq '{full_name, stargazers_count, license: .license.spdx_id, pushed_at, archived}'
```
