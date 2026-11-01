# 快照身份与跨 program 结构门（批次 31 R1，驱动 T3）

> **性质**：设计/研究文档（本文件是本轮**唯一**写盘产物）。
> 本轮**零 Gradle、零 adb / am instrument、零 git 写操作、零源码改动**。
> 文中的 SQLite 行为结论来自**本会话实跑的探针**（Python 3.13 标准库 `sqlite3`，引擎 **3.50.4**，内存库，`PRAGMA foreign_keys = ON`），
> 与官方文档交叉核对；**无一条构建或设备结论**。
>
> **口径**：`已落` = 当前树有代码（本会话实读，引 `path:line`）；`未落` = 本会话 grep 零命中；
> `建议` = 本文判断，T3 落码时确认或否决；`裁定` = 与批次 30 已落结论冲突，需主会话决定。
> **0 项在本轮转"通过"。**
>
> **纪律沿用**：热区禁改；schema 71 只由 T3 轨写；JS 不广告；一致门仍是原型门；真实 Provider UNVERIFIED。

---

## 0. 结论速览

| # | 结论 | 它消灭的具体失败 | 证据等级 |
|---|---|---|---|
| **C1** | 任务一的方案应把 `renderer_version` + `font_manifest_version` **同时**并入**三处**：唯一键、`snapshotId` 派生式、内容查重键。只并入其一都会退化成"两套口径" | 现状：painter 升级后同内容提交**要么**静默复用旧行（旧 renderer 版本，恢复侧 `RENDERER_VERSION_MISMATCH` → **该图永久只读**）**要么**（批次 30 修复前）抛 `ImmutablePayloadConflictException`。两者都是坏结果 | 事实闭包 + 实测 E1/E2/E3 |
| **C2** | 计划 §0:10 的表述"painter 升级后**无法持久化**同内容"与树不符：**能**持久化，只是被**静默吸收**进旧行。结论（版本并入身份）成立，**前提必须改写**；且这是对批次 30 复核修复裁定（`TutorVisualCommit.kt:331-337`）的**升级而非否定** | 若照原表述落码，T3 会以为在修一个不存在的异常，从而漏掉真正的"永久只读"降级 | `TutorVisualCommit.kt:326-338`、`VisualSnapshotRestore.kt:223-228` |
| **C3** | 70→71 迁移**不需要** `ALTER TABLE`：两列在 v70 已存在。只需 `DROP INDEX` + `CREATE UNIQUE INDEX`（新键是旧键的**严格超集**，因此对存量行**不可能失败**） | 若误以为要加列/重建表，会写出不必要的 `ALTER`（Room 校验与 `TutorVisualSchemaContractTest` 双重风险） | 实测 E1/E2 |
| **C4** | **跨 program 漏洞的真实位置**不是 CAS、也不是题面 pin（两者批次 30 复核修复已加谓词），而是**消息 link 的初次插入** `insertMessageVisualIfLive`（`TutorVisualDao.kt:278-298`）：只有 `status='LIVE'`，**没有** `program_id` 比对 | 实测 A1：用该 SQL 原形可写进 `(program_id=P1, current_snapshot_id=S2∈P2)`。后果不是"画错图"，而是**别人 program 的快照被永久 pin**（pin/evict 只按 `snapshot_id` 关联，`TutorVisualDao.kt:99-120`、`:158-176`），既回收不了也解释不了 | 实测 A1/A2 + 源码 |
| **C5** | 三种修法里：**写入侧 EXISTS 必做（1 行谓词）**，**触发器推荐作为 71 的 DB 级backstop**，**复合外键否决** | 复合外键要重建两张子表；而 Room 3 的迁移**在 `BEGIN EXCLUSIVE` 事务里跑**（`RoomConnectionManager.kt:194-207`），SQLite 的 `PRAGMA foreign_keys` **在事务内是 no-op**（实测 E4 + 官方文档），因此无法用标准 12 步法关外键；更致命的是**存量违规行的 `INSERT…SELECT` 拷贝会直接失败**（实测 H3），升级会把库卡住 | 实测 B/C/E/H + 官方文档 |
| **C6** | 题面 link 的 STAGING 门**已在树**（`TutorVisualDao.kt:407-428`，含 `status='LIVE' AND program_id` 双条件）。残余缺陷不是"门没写"，而是：① **零调用者、零用例**；② 失败被折成 `ImmutablePayloadConflictException`，"STAGING 竞态（可稍后重试）"与"永久不匹配"不可区分 | 若按计划"再修一遍"，T3 会重复实现同一 SQL；真正缺的是负向用例 + 有码拒绝 | `TutorVisualDao.kt:399-428`、`:449-482`、grep 调用者=0 |
| **C7** | `snapshotId` 写库前**不复派生**（DB 层），与 `ensureTutorVisualProgram` 的复派生（`:2122-2132`）不对称。补法 = 3 行 `require`，且必须与 C1 的**同一个派生函数**同源 | 传一个与内容不符的 snapshotId 今天会落成"身份与内容脱钩"的行；若 C1 落地后 DB 层仍用旧公式，门会判错行 | `RoomStudyDatabase.kt:2118-2139` vs `:2151-2175` |
| **C8** | 71 会改 `identityHash` → **必须同批改 `SmbkSelectionPlan.SCHEMA_VERSION`/`IDENTITY_HASH`**，否则备份裁剪器 **fail-closed**（`BackupSnapshotTrimmer.kt:268-283`），把刚修的 KD-80 34/34 绿轮再打红 | "改了 schema 没闭合裁剪器"是批次 30 已经出现过一次的同类失败（C5 在旧设计 §0） | `SmbkSelectionPlan.kt:40,43`、`BackupSnapshotTrimmer.kt:268-283` |
| **C9** | 现在**是**改派生式的唯一低风险窗口：`tutor_visual_program`/`snapshot` 在**任何升级库中都是空的**（v69→70 只 CREATE），且写入仓储 `TutorVisualCommitRepository` **在 app/feature 零调用者**（只有 androidTest） | 一旦产品路径开始写行，再改派生式就会产生"同内容两个 id"的 legacy 行，DB 层复派生门将无法对旧行成立 | `VisualSnapshotMigration_69_70.kt:15-20`、grep 调用者=0 |

---

## 1. 现状事实闭包（本会话实读）

### 1.1 身份派生

| 事实 | 证据 |
|---|---|
| `programId = "visual-program-" + sha256(长度前缀×6：sourceJs, initialState, basis, apiVersion, sdkVersion, role)` | `core/database/.../TutorVisualIdentity.kt:46-69`、长度前缀实现 `:89-100` |
| `snapshotId = "visual-snapshot-" + sha256(programId + stateSha256 + drawCommandsSha256)[:32]` —— **只含三者，不含任何版本** | 同上 `:71-81` |
| 注释自述"唯一键 `(program, state, draw)` 同源：同内容重复提交落到同一个 id" | 同上 `:38-43` |

### 1.2 DB 写入路径

| 事实 | 证据 |
|---|---|
| `ensureTutorVisualProgram` **重新派生 programId 并 `require` 相等** | `core/database/.../RoomStudyDatabase.kt:2118-2139`（`require` 在 `:2130-2132`） |
| `ensureTutorVisualSnapshot` **不重派生** `write.snapshotId`：按内容三元组查重 → 无则 `insertSnapshot(write.toEntity(...))`（直接用调用方给的 id） | 同上 `:2151-2175`（插入 `:2166`，回读兜底 `:2167-2172`） |
| 仓储层在构造写入载荷时派生 snapshotId | `core/data/.../visual/TutorVisualCommitRepository.kt:392-403`（`:399` 调 `deriveSnapshotId`） |

### 1.3 三处 link 门（不对称）

| 写入口 | `status='LIVE'` | `program_id` 一致 | 证据 |
|---|---|---|---|
| 消息 link **初次插入** `insertMessageVisualIfLive` | ✅ | ❌ **缺** | `core/database/.../dao/TutorVisualDao.kt:278-298`（`WHERE snapshot_id = :snapshotId AND status = :liveStatus`） |
| 消息 link **指针 CAS** `repointMessageVisual` | ✅ | ✅ | 同上 `:328-351`（`snapshot.program_id = tutor_message_visual.program_id` 在 `:340`） |
| 题面 pin `insertRevisionVisualIfLive` | ✅ | ✅ | 同上 `:407-428`（`:412-417`） |

补充：`linkMessageVisual`/`pinRevisionVisual` 都是 `@Transaction` + 回读判赢（`:359-397`、`:449-482`），命中 0 行 → `ImmutablePayloadConflictException`。

**调用者实测**（`grep -rn "pinRevisionVisual\|insertRevisionVisualIfLive\|insertMessageVisualIfLive\|linkMessageVisual" --include=*.kt . | grep -v /build/`）：
`pinRevisionVisual` / `insertRevisionVisualIfLive` 的**生产调用者 0、测试调用者 0**；`linkMessageVisual` 只有 `RoomStudyDatabase.kt:2014` 与迁移测试 `:580`。

### 1.4 唯一键与载荷比对

| 事实 | 证据 |
|---|---|
| 唯一键 = `UNIQUE(program_id, state_sha256, draw_commands_sha256)` | `core/database/.../entity/TutorVisualEntities.kt:121`；迁移 DDL `VisualSnapshotMigration_69_70.kt:111-114`；导出 `70.json` 同名索引 `unique=true`（本会话 python 实读） |
| 快照载荷比对**排除**四个环境版本列；注释明确"身份 = 唯一键 = 内容三列" | `core/database/.../TutorVisualCommit.kt:326-338`（说明）、`:339-361`（实现，`renderer_version`/`font_manifest_version` 不在其中） |
| program 载荷比对逐列（`created_at` 除外） | 同上 `:309-324` |

### 1.5 恢复侧版本门

| 事实 | 证据 |
|---|---|
| `rendererVersion != policy.rendererVersion` → `ReadOnly(RENDERER_VERSION_MISMATCH)` | `core/data/.../visual/VisualSnapshotRestore.kt:223-228` |
| `fontManifestVersion !in verifiedFontManifests` → `ReadOnly(FONT_MANIFEST_UNVERIFIED)`；清单集合**当前为空集** | 同上 `:229-234`、`:125` |
| `VISUAL_RENDERER_IMPLEMENTATION_VERSION = "visual-renderer-v1"`（painter 实现版本常量） | `core/data/.../visual/VisualRendererVersion.kt:20` |
| 写入侧**没有任何**"同一内容 + 新 renderer 产生第二行"的路径 | §1.2 + §1.4：查重键不含版本、`INSERT OR IGNORE` 命中即复用 |

### 1.6 schema / 导出 / 备份清单三方耦合

| 事实 | 证据 |
|---|---|
| DB 版本 70；迁移注册 | `core/database/.../StudyDatabase.kt:139`、`:409` |
| `70.json`：version=70、identityHash=`11a8a701bae767a8a3c5bc810e4743a4`、90 实体（本会话 python 实读） | `core/database/schemas/…/70.json` |
| `SmbkSelectionPlan`：`SCHEMA_VERSION=70`、`IDENTITY_HASH` 与 70.json 逐字相同、`CLOSURE_VERSION=3`、`require(allTables.size==90)` / `softReferences==21` / `trimOrder==82` | `core/data/.../backup/SmbkSelectionPlan.kt:40,43,46,537-554` |
| 裁剪器**两个 fail-closed 门**：`database.version != SCHEMA_VERSION` → fail；`room_master_table.identity_hash != IDENTITY_HASH` → fail | `core/data/.../backup/BackupSnapshotTrimmer.kt:268-283` |
| JVM 契约测试把"迁移只许 CREATE"与唯一索引三列**逐字钉死** | `core/database/src/test/.../TutorVisualSchemaContractTest.kt:99-124`（禁 `ALTER/DROP/DELETE/INSERT/UPDATE`）、`:238-248`（唯一键 DDL 全串相等） |
| `KernelWave0SchemaContractTest` 把"最高导出 == 版本常量"与字面量 70 钉住；该文件当前 **` M`（工作树已改，未提交）** | `core/database/src/test/.../KernelWave0SchemaContractTest.kt:33-56`；`git status --porcelain` 实测 |

### 1.7 生产调用者 = 0（决定"现在改 vs 以后改"）

| 事实 | 证据 |
|---|---|
| `TutorVisualCommitRepository` 构造点只有定义与 androidTest | `grep -rn "TutorVisualCommitRepository(" --include=*.kt .`（批次 31 基线 §3.5 同结论，本会话复核） |
| 四表在 v69→70 迁移里**只 CREATE，旧行零改动**，故生产库升级后四表**为空** | `VisualSnapshotMigration_69_70.kt:15-20`、`:37-53` |

---

## 2. 任务一：renderer / font 版本并入快照身份

### 2.1 先把失败说准（这一点计划写反了）

**现状（版本不进身份）** 在 painter 或字体实现发生变化后，同一 `(program, state, draw)` 的再提交按以下顺序收敛：

1. `ensureTutorVisualSnapshot` 按内容三元组查到旧行（`RoomStudyDatabase.kt:2156-2164`）；
2. `sameSnapshotPayload` **不比版本**（`TutorVisualCommit.kt:339-361`）→ 通过；
3. `claimStagingOwner` 返回旧行（`RoomStudyDatabase.kt:2163`）；
4. 后续消息 link / 指针 CAS 指向的是**旧 renderer 版本的快照**；
5. 恢复时 `record.rendererVersion != CURRENT` → `RENDERER_VERSION_MISMATCH` → **只读**（`VisualSnapshotRestore.kt:223-228`），且**没有任何写路径能补出一行新版本快照**。

所以真实失败是：**一次 painter 版本 bump 会把全部历史图不可逆地降级为只读**（`VERIFIED_FONT_MANIFESTS` 为空更让字体门恒不过，`VisualSnapshotRestore.kt:125`），而不是"无法持久化同内容"。

批次 30 复核修复把版本从**载荷比对**里移出，理由是"两套口径只能留一套"（`TutorVisualCommit.kt:333-337`）——在"身份 = 唯一键 = 内容三列"这个前提下，它的推理是对的：若只把版本加进载荷比对，就会在升级后抛冲突（更坏）。**但前提本身可以升级**：把版本同时放进**三处**，两套口径仍然只有一套，且两种坏结果都不出现。

### 2.2 四种方案取舍

| 方案 | 形态 | 升级后同内容再提交 | 旧快照 | 代价 | 判定 |
|---|---|---|---|---|---|
| **S1 并入身份**（推荐） | 唯一键、`snapshotId` 派生、内容查重键**三者同时**含 `renderer_version`+`font_manifest_version` | **新建一行**（新 renderer 的渲染），指针 CAS 换指过去；旧行变未固定 → 可被保留策略淘汰（INITIAL 除外） | 仍可读；旧 renderer 已不存在 → 恢复侧仍只读（**如实**：这张图是旧画法画的，不能假装能重画） | 71 一次迁移（只换唯一索引）；同内容可能短暂并存两行；每 program 预算受多版本影响 | ✅ |
| **S2 版本冲突 → 新建行（键不变）** | 只改 `snapshotId` 派生，键仍三列 | 派生新 id → `INSERT OR IGNORE` 被唯一索引**静默吞掉** → 回读旧行。实测 E3：`changes=0` | 不变 | 无迁移成本，但**什么也没修**；且制造"派生 id ≠ 行 id"的第二套口径 | ❌ 自相矛盾 |
| **S3 版本冲突 → 拒绝** | 键不变，载荷比对含版本 | 抛 `ImmutablePayloadConflictException` | — | 升级后**每一次**同内容提交都失败（用户继续追问同一张图就崩），比现状更坏 | ❌ |
| **S4 维持现状**（HEAD） | 键三列、版本不进身份 | 静默复用旧行 | **永久只读**（§2.1） | 0 成本 | ❌ 这是要被消灭的失败 |

> **S1 为什么在语义上正确**：`draw_commands_json` 是**指令**，不是**像素**。同一段指令被两版 painter 解释即两份不同的渲染产物——身份必须包含解释器。这与"programId 含 api/sdk 版本（能否再执行）"是同一分层：program 身份描述**执行环境**，snapshot 身份描述**重放环境**。
>
> 参考（外部）：Bazel Remote Execution 的 `Action` 把 timeout 也放进身份，理由原文是"否则较短的 timeout 会命中较长 timeout 的执行缓存，从而掩盖 timeout 太短这一事实"（`build/bazel/remote/execution/v2/remote_execution.proto`，`Action` 注释块，本会话 `gh api` 实读）。同一条推理：**凡是能改变结果的环境因素，必须在身份里**，否则缓存会把变更藏起来。

### 2.3 推荐形态（给 T3 的可执行草案）

**① 派生式（`TutorVisualIdentity.kt`）**

```
snapshotId = "visual-snapshot-" + sha256(
    长度前缀(programId) ‖ 长度前缀(stateSha256) ‖ 长度前缀(drawCommandsSha256)
  ‖ 长度前缀(rendererVersion) ‖ 长度前缀(fontManifestVersion)
)[:32]
```

- 五个字段**全部**加长度前缀（与 `deriveProgramId` 的既有加强一致，`:30-32`、`:89-100`），避免 `("a","bc")` 与 `("ab","c")` 撞号。
- 现签名是 3 参（`:71-75`），加两个参数即编译期逼所有调用点（`TutorVisualCommitRepository.kt:399` 一处 + 两个测试）一起改——这正是"改错就编译不过"的约束。

**② 唯一键（`TutorVisualEntities.kt:121`）**

```
Index(value = ["program_id", "state_sha256", "draw_commands_sha256",
               "renderer_version", "font_manifest_version"], unique = true)
```

**③ 查重键必须等于唯一键**（这是 S1 的硬条件）

`findSnapshotByContent(programId, stateSha256, drawCommandsSha256)`（`TutorVisualDao.kt:71-85`）**必须**同样带两个版本参数；否则会查到一个版本不同、而 `sameSnapshotPayload` 又不比版本的旧行，静默复用——正是 S2 的失败。**不变量：派生键 = 唯一键 = 查重键 = 内容比较口径。**

**④ 迁移 70→71（只需一条 DROP + 一条 CREATE）**

```kotlin
internal val VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71 = object : Migration(70, 71) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(DROP_TUTOR_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71)
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71)
    }
}
// DROP INDEX IF EXISTS `index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256`
// CREATE UNIQUE INDEX IF NOT EXISTS `index_tutor_visual_snapshot_program_content_render_identity`
//   ON `tutor_visual_snapshot`
//   (`program_id`,`state_sha256`,`draw_commands_sha256`,`renderer_version`,`font_manifest_version`)
```

- 两列在 v70 **已存在**（`TutorVisualEntities.kt:166-169`），**不需要** `ALTER TABLE`。
- 新键是旧键的**严格超集** ⇒ 存量行不可能冲突 ⇒ **迁移不可能失败**（实测 E1：在含存量行的库上 `DROP`+`CREATE UNIQUE` 成功；E2：随后同内容新版 renderer 插入成功，`COUNT=2`）。
- **不要**用 `CREATE UNIQUE INDEX IF NOT EXISTS` 覆盖同名旧索引——同名不同列会静默保留旧定义，后续写入按三列去重，S1 退化成 S4。必须换名（或先 `DROP`）。
  **实测**：同库先建 `UNIQUE(a,b)`，再执行同名 `CREATE UNIQUE INDEX IF NOT EXISTS idx ON t(a,b,r,f)`，`sqlite_master` 里仍是 `UNIQUE(a,b)`；随后"同 a/b、不同 r/f"的插入 `changes=0`，被旧键吞掉，`COUNT` 仍为 1。
- Room 侧：`@Entity` 的 `indices` 变更 → `identityHash` 变更 → `71.json` 必须重新导出；`KernelWave0SchemaContractTest` 的"最高导出 == 常量"与字面量 70 都要改（`:33-56`）。

**⑤ 旧行处理：不回填、不改 id**

- v70 行的 `snapshot_id` 是按**旧公式**算的；改公式后它们**仍合法**（PK 不变），但不再是"新公式的像"。
- 生产库里这些行**数量为 0**（§1.7：v69→70 只 CREATE；写入仓储无调用者）。因此无需回填，也无需数据迁移。
- 唯一需要写进代码的约束：**DB 层的复派生门只对"正在写入的行"生效**，不能反过来校验历史行。实现上就是"先在 `ensureTutorVisualSnapshot` 入口 `require(write.snapshotId == derive(...))`"，而不是扫描表。
- 若要更保守，可在 `TutorVisualIdentity` 上加一条注释常量 `SNAPSHOT_ID_DERIVATION_EPOCH = 2`，说明"epoch 1（v70）行可能存在于开发机/设备库，id 不满足当前公式，属预期"。

### 2.4 与批次 30 已落裁定的关系（**需主会话裁定**）

`TutorVisualCommit.kt:331-337` 是批次 30 复核修复的**明文裁定**："身份 = 唯一键 = 内容三列；版本差异由恢复侧判定，而不是制造同内容第二行"。

S1 **改的就是这一句**。区别在于：

- 批次 30 面对的二选一是"抛冲突（S3）" vs "静默复用（S4）"，它选了 S4 并写下了"两套口径只能留一套"这条更根本的规则；
- S1 同时满足那条规则（三处同源）并消灭 S4 的"永久只读"。

因此本文的定位是：**S1 = 把批次 30 规则从"三列"推广到"三列 + 重放环境"，不是推翻它**。但代码注释必须同步改写（`:326-338`），否则下一位读者会看到自相矛盾的两段注释。**这一步属裁定范围，需主会话确认后再动 schema 71**；若主会话否决，则本批任务 6 应登记为"不做"，并把 S4 的永久只读降级登记为已知缺陷（KD），不得静默忽略。

### 2.5 牵动面清单（改一处先枚举）

| 落点 | 改什么 | 漏掉的后果 |
|---|---|---|
| `TutorVisualIdentity.kt:71-81` | 派生式加两参 | 编译不过（这是好事：逼所有调用点改） |
| `TutorVisualEntities.kt:120-125` | 唯一索引换列 | Room 校验与导出不一致 → 真机升级炸 |
| `VisualSnapshotMigration_69_70.kt:111-114` | **不动**（历史迁移保持原样） | 改历史迁移 = 重放旧升级时建错索引 |
| 新 `VisualSnapshotIdentityMigration_70_71.kt` | DROP + CREATE UNIQUE | 缺 DROP → 旧索引仍在，去重仍按三列 |
| `StudyDatabase.kt:139`、`:409` | 71 + 注册 | 新库/升级库版本不一致 |
| `RoomStudyDatabase.kt:2156-2174` | 查重键带两版本 + 复派生门（§5） | 查重与唯一键不同源 → S2 |
| `TutorVisualDao.kt:71-85` | `findSnapshotByContent` 加两参 | 同上 |
| `TutorVisualCommit.kt:326-338`、`:339-361` | 注释改写（版本已进键，故载荷比对可比可不可，需说明） | 留下矛盾注释 |
| `TutorVisualCommitRepository.kt:392-431` | `rendererVersion`/`fontManifestVersion` **由仓储盖章**（见 §5.3），不再信任入参 | 调用方给错版本 → 身份记错画法 |
| `SmbkSelectionPlan.kt:40,43` | `SCHEMA_VERSION=71` + 新 `IDENTITY_HASH` | **备份裁剪器 fail-closed**（`BackupSnapshotTrimmer.kt:268-283`），KD-80 绿轮再红 |
| `KernelWave0SchemaContractTest.kt:33-56` | 字面量 71 + 文案 | JVM 必红 |
| `TutorVisualSchemaContractTest.kt:238-248` | v70 的三列唯一键断言（**冻结在 v70 语义**），另加 v71 断言 | 现断言会把 71 的正确索引判错 |
| `docs/current/data-schema.md`、`visual-generation-contract.md` §8 | 补"身份含重放环境" | 文档与实现漂移 |

---

## 3. 任务二：link 指针的跨 program 漏洞

### 3.1 漏洞与可达性（先把话说明白）

**已修的两处**（批次 30 复核修复，本会话实读确认）：

- 指针 CAS：`TutorVisualDao.kt:328-351`，`:340` 有 `snapshot.program_id = tutor_message_visual.program_id`；
- 题面 pin：`:407-428`，`:412-417` 同时有 `status = :liveStatus AND snapshot.program_id = :programId`。

**仍缺的一处**：消息 link 初次插入 `insertMessageVisualIfLive`（`:278-298`）

```sql
INSERT INTO tutor_message_visual (...)
SELECT :messageId, :ordinal, :programId, snapshot_id, 1, ...
FROM tutor_visual_snapshot
WHERE snapshot_id = :snapshotId AND status = :liveStatus     -- ← 没有 program_id 比对
```

`linkMessageVisual`（`:359-397`）随后回读，非空即"成功"，**不报错**——这是静默写坏，不是显式失败。

**可达性如实说明**：本仓当前的 `commitTutorTurnWithVisual` 路径里，`programId` 与 `snapshotId` 同源于一个 command（`TutorVisualCommit.kt:184-186` 只校验两者一致），且 `ensureTutorVisualSnapshot` 按 program 过滤查重，所以**现有产品路径不会**产生跨 program 行。缺的是**DAO 层没有约束表达**：`TutorVisualDao` 是 `internal` 但整个 `core:database` 模块可达，任何新写法/导入/恢复路径都能写出这一行（实测 A1 用该 SQL 原形一键复现）。

**后果为什么严重**（不是"图会画错"这么轻）：

1. pin/evict 只按 `snapshot_id` 关联，不校验 program：`checkpointSummaries`（`TutorVisualDao.kt:99-120`）与 `evictUnpinnedCheckpoints`（`:158-176`）都只 `WHERE link.current_snapshot_id = snapshot.snapshot_id`；
2. 于是 P2 的快照被一行自称 P1 的 link **永久固定**：既进不了淘汰候选，也说不清"是谁在用"；
3. `RESTRICT` 还会拦住 P2 侧的清理（实测 B5 同类：删被引用的父行被拒）。

### 3.2 三种修法对比

| 维度 | O1 写入侧 EXISTS | O2 复合外键 | O3 触发器 |
|---|---|---|---|
| 覆盖 INSERT | ✅（仅被改写的 SQL） | ✅（**任何** INSERT） | ✅（**任何** INSERT） |
| 覆盖 UPDATE | ❌（实测 D3：绕过门的 UPDATE 照写） | ✅（实测 B3） | ✅（需 `BEFORE UPDATE OF`） |
| 能否阻止"未来新写法" | ❌ | ✅ | ✅ |
| 需要 schema 变更 | 否 | 是（父表加 UNIQUE + 两张子表**重建**） | 是（版本 bump，但**无表结构变化**） |
| Room 迁移事务内可完成 | ✅ | ⚠️ 见 3.4 | ✅（实测 F1） |
| 存量违规行的影响 | 无 | **拷贝失败 → 升级中断**（实测 H3） | 无（只拦后续写，实测 H4） |
| 与 `TutorVisualSchemaContractTest` 的关系 | 无 | 改 `@Entity` → 改 `identityHash` → 改 71.json | 触发器**不在** Room 校验范围（`TableInfo.kt:285-299` 只比 name/columns/FKs/indices） |
| 需要 fresh install 额外处理 | 否 | 否（Room 建表自带） | ✅ **是**（Room 不会在 `createAllTables` 里建触发器，必须 `onCreate`/`onOpen` 幂等补建） |

### 3.3 SQLite 实际行为依据（本会话实测，逐条可复现）

| 编号 | 场景 | 结果 |
|---|---|---|
| **A1** | 用 v70 的消息 link SQL 原形写 `(P1 → S2∈P2, LIVE)` | **接受**，行 = `('P1','S2')` |
| **A2** | 事后一致性查询 `link.program_id <> snapshot.program_id` | **命中 1 行**（不变量已被破坏） |
| **A3/A4** | 题面 pin 的守卫 SQL（`status='LIVE' AND program_id`） | `rowcount=0`（跨 program、STAGING 都写不进） |
| **A5** | 无门的普通 `INSERT` | 接受 `(P2, S3=STAGING)` |
| **B1** | 复合外键 `FK(program_id, current_snapshot_id) → snapshot(program_id, snapshot_id)`，跨 program INSERT | `IntegrityError: FOREIGN KEY constraint failed` |
| **B2** | 同 program INSERT | 接受 |
| **B3/B4** | 跨 program **UPDATE** | 拒绝；`program_id/current_snapshot_id` 仍是 `('P1','S1')`（未写坏） |
| **B5** | 删被引用的父快照 | `FOREIGN KEY constraint failed`（RESTRICT 生效） |
| **B6/B6b** | 父表**没有** `UNIQUE(program_id, snapshot_id)` | DDL 接受，但写入时报 `OperationalError: foreign key mismatch`（错误被推迟到 DML） |
| **B7** | `PRAGMA foreign_keys = OFF` | 连 v70 现有的单列外键也**不生效**（跨 program 行可写） |
| **C1/C2/C4** | `BEFORE INSERT` / `BEFORE UPDATE OF current_snapshot_id, program_id` 触发器 + `RAISE(ABORT)` | 跨 program link、STAGING link、跨 program 重指**全部拒绝**，错误文本为自定义码 |
| **C3/C7** | 同 program 且 LIVE | 接受 |
| **C5/C6** | 题面 pin 触发器：STAGING、跨 program | 全部拒绝 |
| **C9/C10** | `RAISE(ABORT)` 发生在显式事务内 | 只回滚**该语句**：`in_transaction=True`，后续语句仍可执行并提交（Kotlin 侧异常向外抛 → Room 回滚整个 `withWriteTransaction`，语义正确） |
| **E4** | `BEGIN` 之后执行 `PRAGMA foreign_keys = OFF` | 读回仍是 **1**（no-op）；`COMMIT` 后再设才变成 0 |
| **F1** | 事务内 `CREATE TRIGGER` | 允许，触发器数 1 |
| **G1/G2** | 悬空 `snapshot_id` / 不存在的 `message_id` | 现有单列外键仍然拒绝（复合外键**不能**替代它们） |
| **H1** | 父表 UNIQUE 索引列序与 FK 引用列序**相反** | 接受（按列集合匹配，不要求同序） |
| **H3** | 在事务内重建子表并 `INSERT…SELECT`，**存量行含跨 program 违规** | `FOREIGN KEY constraint failed` → **整次迁移失败** |
| **H4** | 在**已有**违规行的表上 `CREATE TRIGGER` | 成功；旧行保留；新的违规写入被拒 |

官方文档交叉核对（与实测一致，两个独立来源）：

- 父键必须是 PK 或"collectively subject to a UNIQUE constraint or have a UNIQUE index"；两张表之间的 FK 配置错误表现为 DML 期的 **"foreign key mismatch"**（`https://www.sqlite.org/foreignkeys.html` §3、§4.1）；
- `PRAGMA foreign_keys` "**is a no-op within a transaction**; foreign key constraint enforcement may only be enabled or disabled when there is no pending BEGIN or SAVEPOINT"（`https://www.sqlite.org/pragma.html#pragma_foreign_keys`）。

### 3.4 为什么否决复合外键（O2）

1. **Room 的迁移在事务里跑，关不掉外键**：`room3` 的 `configureDatabase` 用 `BEGIN EXCLUSIVE TRANSACTION` 包住迁移，成功后 `END`、失败 `ROLLBACK`（`androidx/androidx` · `room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt:184-207`，`:286-296` 的 `onMigrate`）。而 `PRAGMA foreign_keys` 在事务内是 no-op（实测 E4 + 官方文档）。SQLite 官方 12 步改表流程要求"先关外键"，本仓**在迁移上下文里做不到**。
2. **存量违规行会让升级直接失败**（实测 H3）。当前树允许写坏的行（A1），所以"库里有违规行"不是一个假设，而是本次修复要处理的对象；复合外键会把"存在坏行"翻译成"升级失败"，把可用性问题变成阻塞问题。
3. **要重建两张子表**（message link + revision pin），并让 `@Entity` 的外键集合与重建后的 DDL **逐字对齐**，否则 `onValidateSchema` 报 "Migration didn't properly handle"。风险面比收益大。
4. 复合外键**不能替代**现有的单列外键（G1/G2：悬空 id 仍需单列 FK 拒绝），只能在之上叠加 → 外键数量翻倍。

> 唯一值得记下的优点：复合外键是**声明式**的，且 fresh install 自动具备（Room 建表自带），不需要额外 onOpen 补建。若将来某次全表重建顺路（例如另一个任务已经要重建这两张表），可以顺手换成复合外键；单独为它做重建，不划算。

### 3.5 推荐落地（O1 + O3）

**必做（O1，1 行谓词，无 schema 变更）**：

```sql
FROM tutor_visual_snapshot
WHERE snapshot_id = :snapshotId
  AND status = :liveStatus
  AND program_id = :programId          -- ← 补这一行，与 repointMessageVisual 同形
```

- 零风险；`linkMessageVisual` 回读为空即抛冲突，语义与现有 `pinRevisionVisual` 一致。
- 必须同时补**负向用例**（§3.6）：跨 program 的 `linkMessageVisual` 抛 `ImmutablePayloadConflictException` 且 `tutor_message_visual` 零新增行。

**推荐（O3，schema 71 内）**：两条 `BEFORE INSERT` + 两条 `BEFORE UPDATE OF` 触发器，把"link 只能指向同 program 且 LIVE 的快照"变成 DB 不变量：

```sql
CREATE TRIGGER IF NOT EXISTS tutor_message_visual_guard_insert
BEFORE INSERT ON tutor_message_visual
FOR EACH ROW WHEN NOT EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = NEW.current_snapshot_id
      AND s.program_id   = NEW.program_id
      AND s.status       = 'LIVE')
BEGIN SELECT RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM'); END;
```

- **它消灭的具体失败**：不是"当前某条 SQL 写错了"（那条由 O1 修），而是"**任何未写进 DAO 的新写法/导入/恢复路径**都能重新引入同一漏洞，而 DAO 没有约束表达"。批次 30 已经在这个类上踩过两次（CAS、题面 pin 各补一次谓词），第三次仍靠 code review 是已知不收敛的模式。
- **必须同时补 fresh install 路径**：Room 的 `createAllTables` 不建触发器（`TableInfo` 只比 name/columns/FKs/indices，`TableInfo.kt:285-299`），所以触发器要在**迁移**里建一次、并在 `onOpen`/`onCreate` 幂等补建一次。仓内已有同型先例：`RoomLibrarySearchStore.kt:342-380`（注释原文 "**Room rejects DDL in @Query**, so this runs outside the DAO; `IF NOT EXISTS` keeps repeat calls cheap"）与迁移侧 `LibrarySearchFtsMigration.kt:117-133`（注释原文 "Outbox triggers must exist from the moment the migration lands" + "Byte-identical to the lazy `ensureSearchTriggers()` statements"）。**沿用它，不新造机制。**
- 另一条硬约束：Room 把 `CREATE TRIGGER` 归为 **UNKNOWN query type** 而拒绝在 `@Query` 里接受它（`LibraryFtsSearchDao.kt:246-250`，注释原文 "Room classifies CREATE TRIGGER DDL as an UNKNOWN query type and rejects it in @Query"），所以触发器只能出现在 `Migration.migrate` 的裸 `execSQL` 里或 `withRawConnection` 里。
- 触发器**不进** schema JSON ⇒ `identityHash` 不变 ⇒ `SmbkSelectionPlan.IDENTITY_HASH` 无需因触发器而改（但 §2 的唯一索引变更会改它，两者同批时以索引为准）。
- 错误码是自定义的 `RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM')`：`SQLiteConstraintException` 的 message 里带它，便于按码分流（与 `ImmutablePayloadConflictException` 的"有界原因"纪律同向）。

### 3.6 用例矩阵（先红后绿，全属新增）

| 用例 | 做法 | 期望（红灯时） |
|---|---|---|
| 跨 program 初次 link | 造 P1/P2 各自 LIVE 快照；`linkMessageVisual(M1, 0, programId=P1, snapshotId=S2∈P2)` | 抛冲突；`tutor_message_visual` 行数 0 |
| 跨 program 指针 CAS | 已有 P1 的 link，`repointMessageVisual(..., nextSnapshotId=S2∈P2)` | 返回 false；`current_snapshot_id` 与 `revision` 均不变（**已有实现，缺用例**） |
| 题面 pin STAGING | `pinRevisionVisual(..., snapshotId=S3@STAGING)` | 抛冲突；`problem_revision_visual` 行数 0（**已有实现，零用例**——`pinRevisionVisual` 在本仓无任何调用点） |
| 题面 pin 跨 program | 同上但 S2∈P2 且 LIVE | 抛冲突；零行 |
| 触发器直写（设备/仪器化） | 走裸 `connection.execSQL` 做上面四件事 | 全部 `SQLiteConstraintException`，message 含 `TUTOR_VISUAL_*` |
| 幂等重放 | 同 `(messageId, ordinal)` 同 program/snapshot 再 link | 读回既有行，行数不变（既有语义，勿回退） |

---

## 4. 任务三：题面 link 的 STAGING 门

### 4.1 现状：门已在树（不是待修项）

`TutorVisualDao.insertRevisionVisualIfLive`（`TutorVisualDao.kt:407-428`）已是：

```sql
INSERT INTO problem_revision_visual (...)
SELECT :problemRevisionId, :role, :ordinal, :programId, :snapshotId, :createdAt
WHERE EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = :snapshotId
      AND s.status      = :liveStatus        -- ← STAGING 写不进
      AND s.program_id  = :programId)        -- ← 跨 program 写不进
```

KDoc `:399-406` 明写"批次 30 复核修复前这里是普通 `@Insert(ABORT)`"，并解释了它消灭的失败：被取消的 STAGING 快照一旦被题面 pin，对账 SQL 又排除"被题面引用"的行 → **既回收不了也重放不了，永久只读 + 永久占位**。这段推理与本文 §3 同源，结论正确。

### 4.2 残余缺陷（这才是本轮该做的）

| # | 缺陷 | 证据 | 修法 |
|---|---|---|---|
| R1 | **零调用者**：`pinRevisionVisual` / `insertRevisionVisualIfLive` 在主代码与测试中调用点均为 0 | 本会话 grep（§1.3） | 本轮不接线（接线属装配/热区），但**必须**补一条 DAO 级负向用例，否则这条门随时可能被删掉而无人发现 |
| R2 | **失败原因折叠**：门命中 0 行 → `ImmutablePayloadConflictException("problem_revision_visual", ...)`（`:477-481`） | 同上 | "快照还是 STAGING（稍后可重试）"与"永久不匹配（program 不符）"应给**不同有码原因**，参照 `TutorVisualCommitRejection`（`TutorVisualCommitRepository.kt:113-122`）的既有形态；至少让调用方能分流 |
| R3 | **无 DB 级 backstop**：直接写的 `INSERT` 仍可 pin STAGING（实测 A5） | 实测 A5 | 已由 §3.5 的 `revision_guard_insert` 覆盖（实测 C5/C6 拒绝，C7 通过） |
| R4 | 幂等分支只比 `programId`/`snapshotId`（`:458-466`），不比 `role`/`ordinal` | PK 已含 role/ordinal，无实际漏洞 | 无需改；记录以免下一位读者误判 |

**结论**：任务三不是"写门"，是"**用例 + 有码拒绝 + 触发器 backstop**"。

---

## 5. 任务四：snapshotId 写库前复派生

### 5.1 现状不对称

| 层 | program | snapshot |
|---|---|---|
| 仓储（`core:data`） | `programIdFor` 派生（`TutorVisualCommitRepository.kt:159-167`） | `deriveSnapshotId` 派生（`:169-176`、`:399-403`） |
| DB（`core:database`） | **重新派生 + `require` 相等**（`RoomStudyDatabase.kt:2122-2132`） | **只按内容查重/插入**，不校验 id（`:2151-2175`） |

后果：仓库层之外（例如未来某个直接调 `TutorVisualPort` 的写者）可以传一个与内容不符的 `snapshotId`，落成"身份与内容脱钩"的行；而 program 层同样的输入会被立刻拒绝。**同一模块的两条身份规则不一致**，本身就是缺陷。

### 5.2 补法（与 §2 同源）

```kotlin
private suspend fun ensureTutorVisualSnapshot(dao, write, ownerRequestId): TutorVisualSnapshotEntity {
    val derivedSnapshotId = TutorVisualIdentity.deriveSnapshotId(
        programId = write.programId,
        stateSha256 = write.stateSha256,
        drawCommandsSha256 = write.drawCommandsSha256,
        rendererVersion = write.rendererVersion,          // §2 落地后
        fontManifestVersion = write.fontManifestVersion,  // §2 落地后
    )
    require(write.snapshotId == derivedSnapshotId) {
        "A visual snapshot id must be derived from its content and replay environment"
    }
    ...
}
```

- 放在**查重之前**（与 program 的 `require` 同位置），这样"错 id"在触库前就被拒，不会先命中既有行再悄悄返回。
- 用 `require` 而**不是** `ImmutablePayloadConflictException`：program 层同位置用的就是 `require`（`:2130-2132`），保持"调用方构造错误 = `IllegalArgumentException`，并发/重复 = 冲突异常"的既有分流。
- 必须复用**同一个** `deriveSnapshotId`（不能就地重算），否则 §2 改公式时会漏改一处。
- 副作用（有益）：`TutorVisualCommitRepository.snapshotIdFor`（`:169-176`）与 DB 层因此互为**交叉校验**，测试里可用"故意错的 id"验证门的存在（新用例）。

### 5.3 顺带必须闭合的一处：环境版本不是调用方事实

`TutorVisualSnapshotInput.rendererVersion` / `fontManifestVersion` 是**必填入参**（`TutorVisualCommitRepository.kt:76-79`），由调用方提供。一旦版本进身份（§2），"调用方给了旧版本号"就会让身份记错画法，恢复侧永远判 mismatch。

**建议**：由仓储**盖章**，不接受入参覆盖：

```kotlin
rendererVersion    = VISUAL_RENDERER_IMPLEMENTATION_VERSION,  // core/data/.../VisualRendererVersion.kt:20
fontManifestVersion = VISUAL_FONT_MANIFEST_VERSION,           // 本仓尚无此常量，需新增（见 §7 U3）
```

理由与 `programId` 字段刻意不放进 `TutorVisualProgramInput`（`TutorVisualCommitRepository.kt:20-27` 的 KDoc）完全同源：**凡是"实现的事实"，都不该由调用方声明**。测试仍可通过直接构造 `VisualSnapshotRestoreRecord` 注入不匹配来覆盖恢复门（既有 `VisualSnapshotRestoreTest.kt:147-152` 已经这么做），不需要保留入参口。

---

## 6. GitHub 参考（内容寻址 + 唯一约束 + CAS）

> 通道：`gh api repos/<owner>/<repo>`（license/stars/pushed_at 均为**本会话实测**）。评分为主观判断，**只看已验证元数据 + 读到的原文**。
> 全部**只借鉴形态**，不引任何依赖（本仓已有等价实现：vault 内容寻址、命中行数 CAS、两段式裁剪）。

| 仓 | license（实测） | stars | 最近 push（实测） | 借哪一点（原文/位置） | 在本仓落地 | 评分（license / 维护 / 可复用） |
|---|---|---|---|---|---|---|
| [sqlite/sqlite](https://github.com/sqlite/sqlite) | GitHub 返回 `NOASSERTION`（无法自动识别）；官方声明 **public domain**："All of the code and documentation in SQLite has been dedicated to the public domain by the authors"（`https://www.sqlite.org/copyright.html`，本会话 WebFetch 实读） | 10,849 | 2026-10-28 | 复合外键的父键唯一性要求、"foreign key mismatch" 的 DML 期报错、`PRAGMA foreign_keys` 事务内 no-op、`RAISE(ABORT)` 的行级语义 | §3 三种修法的判定依据；触发器的错误码写法 | 5 / 5 / 5 |
| [androidx/androidx](https://github.com/androidx/androidx) | Apache-2.0 | 6,121 | 2026-10-28 | Room 3 的 `configureDatabase` 用 `BEGIN EXCLUSIVE … END/ROLLBACK` 包迁移（`room3-runtime/.../RoomConnectionManager.kt:184-207`）；schema 校验只比 name/columns/FKs/indices（`util/TableInfo.kt:285-299`） | §3.4 否决复合外键的**决定性依据**；§3.5"触发器必须在 onOpen 补建"的依据 | 5 / 5 / 2 |
| [bazelbuild/remote-apis](https://github.com/bazelbuild/remote-apis) | Apache-2.0 | 459 | 2026-10-20 | `Action` 把 timeout 并入身份，注释原文："two `Actions` with different timeouts are different, even if they are otherwise identical… a cache hit from an execution run with a longer timeout, hiding the fact that the timeout is too short" | §2.2 S1 的"凡改变结果者必须进身份"论据 | 5 / 4 / 4 |
| [distribution/distribution](https://github.com/distribution/distribution) | Apache-2.0 | 10,666 | 2026-10-28 | 两段式上传："All layer uploads use two steps…"（`docs/content/spec/api.md`，"Pushing a Layer"），外加 `HEAD` 存在性检查与 digest 校验 | 对应 `STAGING → LIVE` + 内容幂等（既有设计，本批不动） | 5 / 5 / 4 |
| [restic/restic](https://github.com/restic/restic) | BSD-2-Clause | 36,862 | 2026-10-18 | `forget` → `prune` 两段："two commands need to be called in sequence: `forget` to remove snapshots, and `prune` to remove the remaining data that was referenced only by the removed snapshots"（`doc/060_forget.rst`） | 对应"先摘引用、再删仅被已删快照引用的数据"；**跨 program pin 会让 prune 判据失效**（§3.1 后果 2） | 5 / 5 / 4 |
| [NixOS/nix](https://github.com/NixOS/nix) | LGPL-2.1 | 17,926 | 2026-10-28 | input-addressed 存储路径的身份由"masked derivation 哈希"给出——把输入派生（含 builder）一起纳入、仅屏蔽输出（`src/libstore/derivation/masked.cc`，`derivation::masked` 命名空间注释块） | §2.2 的"解释器进身份"类比（**单源：仅源码实读，非官方手册表述，按"待验证"处理**） | 5 / 5 / 3 |

**取舍**：只借形态，不引依赖。理由不是"不信任库"，而是本仓已有等价实现，且两个关键结论（Room 迁移在事务内、SQLite pragma 在事务内 no-op）**恰好说明引入第三方库也解决不了**这个具体迁移约束。

---

## 7. 未验证项与边界（如实）

| # | 项 | 状态 |
|---|---|---|
| U1 | 本轮**零 Gradle、零仪器化、零设备**：所有"已落"都来自源码实读 + `git status` + `python json`，"通过/绿"一律未发生 | UNVERIFIED（无一条构建/测试结论） |
| U2 | SQLite 探针用的是**桌面 Python 3.13 / SQLite 3.50.4**，不是设备上的 Android SQLite。行为（复合外键、pragma、触发器、索引超集）是标准 SQLite 语义，但**设备实测未做** | 待设备轮复核（建议 T3 在 69→70 同型仪器化用例里加 70→71 一节） |
| U3 | `VISUAL_FONT_MANIFEST_VERSION` **常量不存在**（本会话 grep 零命中）；`VERIFIED_FONT_MANIFESTS` 为空集（`VisualSnapshotRestore.kt:125`）。§5.3 的"盖章"需要先定义该常量（建议值 `"visual-font-unverified-v1"`，语义=「未验证」而非某个字体版本） | 未落，需 T3 新增 |
| U4 | painter 的**可观察行为变更是否已 bump** `VISUAL_RENDERER_IMPLEMENTATION_VERSION`（`VisualRendererVersion.kt:12-16` 的纪律）无自动化门；本会话未审 painter 历史 | UNVERIFIED |
| U5 | 71 的 `identityHash` **无法在本轮预知**（必须由带 KSP 的构建导出 71.json）；§2.5 里"改 `SmbkSelectionPlan.IDENTITY_HASH`"是**顺序要求**，不是具体值 | 未获取 |
| U6 | 设备上 `PRAGMA foreign_keys` 是否在 Room 连接上始终为 ON 本会话未实测；`VisualSnapshotMigration_69_70.kt:19-20` 与 `FullMigrationMatrixInstrumentedTest` 的注释是既有记载（"Room 在 `onOpen` 打开 `PRAGMA foreign_keys = ON`"），非本轮证据 | 依既有记载 |
| U7 | 计划 §0:10 的"checkpoint 保留策略零生产调用"与"LIVE LATEST 可能进淘汰候选"不属本轮问题集；本文只用到"pin 只按 snapshot_id 关联"（`TutorVisualDao.kt:99-120`）这一点 | 不展开 |
| U8 | Nix 的"input-addressed 身份含 builder"是**单源**（源码实读），官方手册页（`nix.dev/.../store-path.html`）只写"digest 计算方式取决于 store path 类型、digest 不透明"，**没有**直接支持该表述 ⇒ 按纪律标"待验证" | 待验证 |
| U9 | §2 的方案 S1 与批次 30 的明文裁定（`TutorVisualCommit.kt:331-337`）冲突，**需主会话裁定**；本文给的是建议与代价，不是已批准的变更 | 待裁定 |

---

## 8. 来源

**仓内（本会话实读，均为 `path:line`）**

- 身份：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualIdentity.kt:30-32,38-43,46-69,71-81,89-100`
- DB 写入：`.../RoomStudyDatabase.kt:1953-2057,2112-2175,2177-2199`
- DAO：`.../dao/TutorVisualDao.kt:71-85,99-120,134-176,204-229,272-298,318-351,353-397,399-428,445-482`
- 载荷/常量：`.../TutorVisualCommit.kt:13-25,80-134,309-361,364-470`
- 实体：`.../entity/TutorVisualEntities.kt:36-72,74-186,188-253,255-314`
- 迁移：`core/database/src/main/kotlin/.../VisualSnapshotMigration_69_70.kt:15-33,35-54,108-159,161-188`
- 库版本：`core/database/src/main/kotlin/.../StudyDatabase.kt:139,237-240,409`
- 导出：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json`（version=70 / identityHash=`11a8a701…` / 90 实体，python 实读）
- 恢复：`core/data/src/main/kotlin/.../visual/VisualSnapshotRestore.kt:72-90,100-125,193-251`；`.../visual/VisualRendererVersion.kt:1-20`
- 仓储：`core/data/src/main/kotlin/.../visual/TutorVisualCommitRepository.kt:20-27,113-135,152-176,182-256,279-373,375-431,433-447`
- 备份耦合：`core/data/src/main/kotlin/.../backup/SmbkSelectionPlan.kt:40,43,46,537-554`；`.../backup/BackupSnapshotTrimmer.kt:268-283`
- 测试：`core/database/src/test/.../TutorVisualSchemaContractTest.kt:99-124,180-262,264-323`；`.../KernelWave0SchemaContractTest.kt:33-56`；`core/database/src/androidTest/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:37-191,195-303,305-458`
- 先例：`core/database/src/main/kotlin/.../RoomLibrarySearchStore.kt:342-380`（onOpen 幂等建触发器；注释说明 Room 拒绝 `@Query` 里的 DDL）、`.../LibrarySearchFtsMigration.kt:117-133`（迁移内建触发器，与 lazy 版本逐字节相同）、`.../dao/LibraryFtsSearchDao.kt:246-250`（`CREATE TRIGGER` 属 UNKNOWN query type，被 `@Query` 拒绝）、`.../dao/CleanRedrawTaskDao.kt:134-152,272-290`（命中行数 CAS 与"不许回读判赢"的注释）
- 基线/前置研究：`docs/research/2026-10-29-batch-31-baseline.md` §3.5/§3.6/§3.7/§3.8/§8-D5；`docs/research/2026-10-29-visual-persistence-design.md` §2.3/§3.1/§3.4/§3.5/§4.2；计划 `.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md` §0:10、§1 任务 2-6

**外部（本会话 `gh api` / `WebFetch` 实跑）**

- `https://www.sqlite.org/foreignkeys.html` §3（父键唯一性、foreign key mismatch）、§4.1（复合外键）
- `https://www.sqlite.org/pragma.html#pragma_foreign_keys`（事务内 no-op）
- `https://www.sqlite.org/copyright.html`（"SQLite Is Public Domain"）
- `androidx/androidx` · `room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt:184-207,286-296`；`.../util/TableInfo.kt:285-299`
- `bazelbuild/remote-apis` · `build/bazel/remote/execution/v2/remote_execution.proto`（`message Action` 注释块）
- `distribution/distribution` · `docs/content/spec/api.md`（"Pushing a Layer" / "Existing Layers"）
- `restic/restic` · `doc/060_forget.rst`（`forget` → `prune`）
- `NixOS/nix` · `src/libstore/derivation/masked.cc`（"Derivation masked hash" 注释块）—— 单源，待验证
- 元数据：`gh api repos/{restic/restic, distribution/distribution, bazelbuild/remote-apis, NixOS/nix, androidx/androidx, sqlite/sqlite}`（本会话实测 license/stars/pushed_at）
- `https://nix.dev/manual/nix/stable/store/store-path.html`（由 `nixos.org` 302 跳转；只写 digest 不透明，未支持"含 builder"的具体表述）

**本轮实跑命令（可重跑）**

```
python - <<'PY'   # Python 3.13 / sqlite3 3.50.4 / :memory: / PRAGMA foreign_keys=ON
# A/B/C/D/E/F/G/H 八组探针，逐条结果见 §3.3；关键 SQL 已逐条抄在 §3.5 / §2.3 / §4.1
PY
gh api repos/<owner>/<repo> --jq '{license: .license.spdx_id, stars: .stargazers_count, pushed_at}'
git status --porcelain -- <cited paths>      # 目标文件洁净度（仅 KernelWave0SchemaContractTest 为 M）
```

> 探针脚本本身**未落仓**：`build/` 是 `.gitignore` 忽略目录，且本会话直接写入被安全钩子拦截（Mimosa 对含 `execute("…"+var)` 形态的脚本按 SQL 注入拦截，尽管本探针全部是参数绑定）。因此本文只保留每条探针的语义与结果；T3 重跑时按 §3.3 的场景逐条构造即可，结论不依赖脚本文件存在。

---

## 9. 批次 31 裁定：schema 71 推迟（T3 落码后追加）

> 本节是**批次 31 的裁定记录**，不是设计变更。§2 的方案 S1 仍然有效，作为批次 32 的输入。

### 9.1 裁定

**本批不做 schema 71。** 任务 6（renderer/font 并入快照身份）整体推迟到批次 32；
`STUDY_DATABASE_VERSION` 保持 70，不新增 70→71 迁移，不写 `71.json`，不动
`SmbkSelectionPlan` / `BackupSnapshotTrimmer` 的任何常量。

### 9.2 原因（可复核）

1. **`identityHash` 只能由 KSP 生成，不能在源码轨重算。** 它是 Room 3
   `Database.getIdentityHash()` 的输出（`RoomOpenDelegate` 的第一个哈希参数；
   `70.json` 的 `11a8a701…` 与 `core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt:264`
   逐字一致）。本轨实测：用 room3-compiler 3.0.0 字节码反推的
   `SchemaIdentityKey` 公式，在 `65.json`（已知 `dd72ec89…`）与 `70.json` 两个独立样本上
   **都**算不出同一值；`legacyIdentityHash` 同样对不上。导出 JSON 不是能可靠重算该哈希的
   → 输入集。结论：**不跑带 KSP 的构建就没有可信的 71 identityHash**。
2. **本批没有回填环节。** 构建门排在设备轨之前，且没有任何步骤会把 KSP 生成的值写回
   `SmbkSelectionPlan.IDENTITY_HASH`。于是"先提版本、等构建生成"只会让备份裁剪器在任何
   真机升级库上 **fail-closed**（`SmbkSelectionPlan.kt:40,43` 与
   `BackupSnapshotTrimmer.kt:268-283` 是 version + `room_master_table` 双门）。
3. **伪造比不做更糟。** 一个估算/占位的哈希会让 KD-80 刚拿到的备份 34/34 绿轮重新变红，
   并把失败伪装成"备份坏了"而不是"schema 没导出"。

### 9.3 批次 32 的执行顺序（硬要求）

1. 先跑**一次**带 KSP 的构建（`assembleDebug` 或等价模块编译），让 KSP 写出
   `schemas/.../71.json` 并得到生成值；
2. **用生成值**回填 `SmbkSelectionPlan.SCHEMA_VERSION = 71` 与
   `IDENTITY_HASH = <71.json 的 database.identityHash>`（同批，不得分两次提交）；
3. 跑备份 34/34 与迁移矩阵 1→71（`FullMigrationMatrixInstrumentedTest` +
   70→71 专用用例）并记录绿轮；
4. 最后再跑设备轨。

### 9.4 红线

- **不得以估算值、占位哈希或"从 70.json 推导"替代 KSP 生成值。**
- 不得手写/伪造 `71.json`；它只能由 KSP 导出。
- 70→71 迁移必须是 `DROP INDEX`（旧三列唯一索引）+ `CREATE UNIQUE INDEX`（新五列键），
  新键是旧键的严格超集；不得用同名 `CREATE ... IF NOT EXISTS` 覆盖旧索引（SQLite 会静默
  保留旧定义，S1 退化成 S4）。
- `findSnapshotByContent`、`deriveSnapshotId`、唯一键三处必须**同批**同源（§2.3 ③）。

### 9.5 本批已落的无 schema 变更部分（不依赖 71）

- 消息 link 初次插入补 `program_id = :programId` 谓词（消灭跨 program 静默 pin）；
- 题面 pin 的 STAGING / 跨 program 负向用例 + 指针 CAS 跨 program 负向；
- `ensureTutorVisualSnapshot` 写库前用 `TutorVisualIdentity` 复派生并 `require` 相等；
- checkpoint 保留策略补 `status` 轴：LIVE 不进淘汰候选，淘汰 SQL 只删 STAGING。

这些落点与 §2 的身份变更**互不依赖**，批次 32 改身份时不需要重做。
