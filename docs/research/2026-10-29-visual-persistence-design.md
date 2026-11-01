# 视觉持久化核心设计（批次 30 R1）：四表 / 身份与 CAS / 冷恢复

> **性质**：设计文档（本文件是唯一写盘产物）。**零 Gradle、零 adb/am instrument、零 git 写操作、零源码改动**。
> 本文服务批次 30 的 T2（D-01 四表 + 迁移）、T3（D-02 身份与 CAS / D-05 冷恢复）、T4（证据与设备用例），
> 不是"已实现"的声明；文中所有"现状"均标注读到的 `path:line`，所有外部结论标注检索通道。
>
> **口径**：`已落`= 当前树有代码（本会话实读）；`未落`= 本会话 grep 零命中或仓内无载体；
> `建议`= 本文的设计判断，需 T2/T3 落码时确认或否决。**0 项在本轮转"通过"。**
> **纪律沿用**：JS 不广告（MVA 未过）；一致性门仍是原型门；真实 Provider UNVERIFIED；
> schema 70 的 `StudyDatabase.kt`/`migrations`/`schemas` **只由 T2 轨写**，本文只给设计不给落码。

---

## 0. 结论速览（给 T2/T3/T4 的可执行要点）

| # | 结论 | 消灭的具体失败 |
|---|---|---|
| C1 | 四表 = `tutor_visual_program` / `tutor_visual_snapshot` / `tutor_message_visual` / `problem_revision_visual`，纯非破坏 v69→70（只 CREATE），旧行零改动 | 改前"图只活在消息正文与一张 PNG 里"：重启/换页/导出都只剩像素，图从哪来、按哪版题面、能否重放全部不可考 |
| C2 | `programId = visualId` 由**内容**派生（sourceJs + initial state + basis + api/sdk + role 的 SHA-256），`tutor_visual_program` 行**永不 UPDATE** | 改前"同一 program 被原地改写源码"：已被消息/题目引用的图会静默变成另一张图 |
| C3 | 快照 `STAGING → LIVE` 单向；**首张可见图与 assistant message/link 同一事务**；link 插入用 `INSERT…SELECT…WHERE snapshot.status='LIVE'` 做 SQL 级结构门 | 改前"消息已显示、快照还在半成品"或"快照 LIVE 了但消息没写成"：两条记录互相矛盾，恢复时不知信谁 |
| C4 | `canonical_source_asset` 的引用站点从**五类扩到六类**（新增 `tutor_visual_snapshot.preview_asset_id`），单源常量 `CanonicalAssetReferenceSites` 一处改、清理与备份两侧同变 | 漏加则 `OrphanAssetGc` 会删掉消息正在显示的预览字节——**第 12.1 条最典型的"改了表没闭合引用面"** |
| C5 | 备份机器清单（`SmbkSelectionPlan`）从 86 表/78 裁剪项/20 软引用同步扩到 **90/82/21**，`CLOSURE_VERSION` 2→3 | 机器清单是硬 `require` 门（`:511`/`:520`/`:527`）：不同步会在备份闭合摘要里漏表或直接崩，=`新表没进裁剪清单 → 原样泄露`（`BackupSnapshotTrimmer.kt:1218` 同型失败） |
| C6 | 冷恢复**结构性**不执行 JS：恢复入参类型不含 `sourceJs`，恢复包不依赖引擎，只走 `VisualDrawOpsParser.parse(text, contentIds, null)` → painter | 改前"恢复顺手重跑一遍源码"：冷启动要起隔离进程、要 JS 运行时、还可能因源码版本漂移画出另一张图 |
| C7 | 兼容分三档：program 版本（能否再执行）/ scene+render schema（能否重放）/ 字体清单（能否像素一致）。任一档未知 → 只读（预览或文字），**绝不把未知 JSON 交给 painter** | 改前"未知版本强行解析"：`VisualDrawOpsParser` 已按码拒绝（`CODE_SCHEMA_VERSION`），但若无"未知版本只读"这一层，UI 会反复重试/空白化 |
| C8 | 本批**不需要**请求 schema 19（`TutorVisualContext` 已在 18 携带源码/state，`ModelTasks.kt:546`）；只有把 visual 引用真的塞进模型请求时才 bump | 清单 D-01 写的"请求 schema 19+"是条件句；无条件 bump 会让旧请求指纹与 encodeDefaults 语义无谓变更（契约 §8 末段） |

---

## 1. 现状事实闭包（本会话实读）

### 1.1 数据库与迁移机制

| 事实 | 证据 |
|---|---|
| DB 版本 = **69** | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134` `internal const val STUDY_DATABASE_VERSION = 69` |
| 迁移对象形态：`object : Migration(68, 69)`，全部 DDL 走 `connection.execSQL(...)` | `core/database/.../FigureGenerationMigration_68_69.kt:35-49` |
| **DDL 常量必须与导出 JSON 逐字一致**，差一字符真机升级被 Room schema 校验炸掉 | 同上 `:27-33`；`FIGURE_GENERATION_OPERATION_DDL_68_69` `:52-63` |
| 导出物路径 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json`（本会话 `find` 实测存在） |
| 迁移矩阵测试形态：**手写**，`createDatabaseFromExportedSchema(...)` + `StudyDatabaseFactory.open(...)` 逐版本 1..current | `core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt:43-62` |
| `room3-testing` **不在 classpath**（全仓 `.kts`/`.toml` grep 零命中） | 本会话 grep；Room 版本见 `gradle/libs.versions.toml:12` `room3 = "3.0.0"` |

> **判断（建议）**：不要为 D-01 引入 `androidx.room3:room3-testing`。官方 `MigrationTestHelper` 确实存在
> （`androidx/androidx` 仓 `room3/room3-testing/api/current.txt` 实测：`createDatabase(version)`、
> `runMigrationsAndValidate(version, migrations)`），但**加依赖 = 改构建文件 = 热区**（本批硬约束），
> 且现有手写矩阵已经覆盖"1→current 逐版"。沿用手写形态，成本 0、口径不变。

### 1.2 资产与引用面（D-01 的真正难点）

| 事实 | 证据 |
|---|---|
| 规范资产按**内容寻址**：`sourceAssetId = "asset-${sha256.take(32)}"`、`relativePath = assets/<hash>.<ext>` | `core/data/.../AndroidCanonicalAssetVault.kt:401-403` |
| 落盘形态：临时文件 → 编码 → 算 sha → `renameTo(destination)`，同名已存在时**核对 sha 后拒绝重写**（内容寻址不允许同内容重写） | 同上 `:377-390` |
| 资产引用是**单一来源**常量，五类站点：`problem_revision_source_asset` / `problem_draft_source_asset` / `problem_draft` / `tutor_message_source_asset` / `figure_generation_attempt.output_asset_id` | `core/database/.../dao/CanonicalAssetReferenceSites.kt:28-47` |
| 该常量同时驱动**孤儿清理认领**与**备份裁剪器五站点对拍** | 同上 `:54-66`；`core/data/.../backup/BackupSnapshotTrimmer.kt:1254-1256`；单源钉测试 `core/data/src/test/.../CanonicalAssetReferenceSingleSourceTest.kt:27-34` |
| 备份裁剪器另有两条 pin SQL（全量 / 裁剪后） | `BackupSnapshotTrimmer.kt:1229-1243` |
| 可用空间读口是**注入式**（默认 `parentFile.usableSpace`，测试可注 0 覆盖失败路径） | 同上 `:69-72` |

> **判断（建议）**：`tutor_visual_snapshot.preview_asset_id` 是**第六类引用形态**。它是本设计里唯一会
> 改变既有语义的单点，必须与四表同批闭合（§2.7、§2.7.1）。

### 1.3 备份机器清单（第二处必改面）

| 事实 | 证据 |
|---|---|
| `SmbkSelectionPlan` 是 **schema 69 的机器清单**：类别 30+35+6+1、ALWAYS 7、DERIVED 2、SPLIT 5 = **86 表** | `core/data/.../backup/SmbkSelectionPlan.kt:15-19`、`:69-172`、`:181-183` |
| 硬门：`require(allTables.size == 86)`、各类别张数、`trimOrder.size == 78`、`softReferences.size == 20`、`splitTables.size == 5` | 同上 `:510-528` |
| `rowCounts` 必须恰好覆盖 86 键（漏/多直接抛） | 同上 `:600-613` |
| 闭合摘要带 `CLOSURE_VERSION = 2` | 同上 `:45`、`:617` |
| 机器块唯一数据源在 `docs/research/2026-10-17-6c2-content-selection-design.md` §9.11 的 `<!-- 6c2-plan-v2:begin -->`，测试反向解析对拍 | 同上 `:6-12` |
| SESSIONS 现有 6 表（会话/消息/消息附图/待决请求/答案暴露×2）、ISSUE_BOOK 30 表 | 同上 `:139-146`、`:70-…` |

> **判断（建议）**：D-01 **不能只加四张表**。`SmbkSelectionPlan` 里 86→90、78→82、softReferences 20→21、
> 类别张数（ISSUE_BOOK 30→31、SESSIONS 6→7），另两张（program/snapshot）走 SPLIT 5→7 必须同批改，机器块同步升 `6c2-plan-v3`，
> `CLOSURE_VERSION` 2→3（否则新旧备份闭合摘要不可区分）。这是"改一处先枚举牵动面"的硬账。

### 1.4 事务 / CAS / 冲突的既有惯用法（直接复用，不新造）

| 惯用法 | 证据 |
|---|---|
| CAS 以 **UPDATE 命中行数**为准，不靠回读判赢（注释明确警告并发下回读会把"没领到"误报成"领到了"） | `core/database/.../dao/CleanRedrawTaskDao.kt:127-148`、`:281-287` |
| `@Transaction` open 方法包 CAS + 回读 | 同上 `:272-289`、`:294-300` |
| 重复提交/幂等冲突用 `ImmutablePayloadConflictException(entityType, entityId)` | `core/database/.../StudyDatabasePort.kt:67`；用法 `TutorConversationDao.kt:252` |
| 消息 + 附图 link 在同一写入方法内落（`insertMessage` + `insertMessageSourceAssetRows`） | `TutorConversationDao.kt:73-77`、`:298-305`、`:418-419` |
| 会话消息对会话是 CASCADE；消息附图对资产是 RESTRICT | `TutorConversationEntities.kt:119-141`、`:233-270` |
| 题面修订 **append-only**，无 update/delete DAO | `ProblemEntities.kt:26` |

### 1.5 可持久化 DTO 边界（批次 29 T2 产物，本设计的落点）

| 事实 | 证据 |
|---|---|
| `Rendered` = `sceneJson` + `sceneSchemaVersion` + `contentIds` + `drawCommandsJson?` + `drawSchemaVersion`，并已做字节预算校验 | `core/domain/.../VisualExecutionEngine.kt:241-262` |
| `VisualExecutionRequest` = `programId`/`sourceJs`/`sceneRevision`/`stateJson?`/`viewport`/`timeMs`/`seed`/`theme`/`explicitUserRetry`，KDoc 明写"programId 与对外 visualId 同一" | 同上 `:56-81` |
| 解析器提供两个入口：`parse(text, frozenContentIds, expectedViewBox?)` 与 `parse(rendered)`；**回放不携 viewport**（回显门在引擎提交时已过） | `core/data/.../visual/VisualDrawOpsParser.kt:17-46`（KDoc）、`:112`（`parse(rendered)`） |
| 版本不符走独立码 `DRAWOPS_SCHEMA_VERSION`（不与其他类型错共用），供调用方按码分流 | 同上 `:51-55`（`CODE_SCHEMA_VERSION`）|
| `FigureArtifactRef.JsSnapshot(programId, snapshotId)` **形状已存在**（无生产构造点） | `core/domain/.../CleanImageGenerator.kt:90-104` |

> **判断（建议）**：四表的 `program_id`/`snapshot_id` 命名必须与 `FigureArtifactRef.JsSnapshot`
> 既有形状逐字对齐（`programId`, `snapshotId`），否则 T3 挂接要再改一遍缝（第 12.1 条）。

---

## 2. D-01：四表设计

### 2.1 不变量（先立规则，再落列）

| ID | 不变量 | 判据载体 |
|---|---|---|
| INV-1 | `program_id` 是**内容派生**的不可变身份；`tutor_visual_program` 无 UPDATE/无 status | DAO 不提供 update；`Fig…` 同型"无 DAO 暴露更新"（`ProblemEntities.kt:26` 同惯用法） |
| INV-2 | 源码/初始 state/basis 任一变化 ⇒ **另一 program_id**，不覆盖旧行 | 派生式 + `require`（§3.1） |
| INV-3 | 快照 `status ∈ {STAGING, LIVE}`，单向；LIVE 是终态 | 迁移无回退；CAS 更新只允许 staging→live |
| INV-4 | 只有 LIVE 快照可被 link 引用 | link 插入走 `INSERT…SELECT…WHERE status='LIVE'`（§3.4） |
| INV-5 | 会话删除不删 program/snapshot；保存题引用固定快照 | message→link CASCADE、link→snapshot RESTRICT、revision_visual→snapshot RESTRICT |
| INV-6 | 同 program 同内容快照只存一份 | `UNIQUE(program_id, state_sha256, draw_commands_sha256)` |
| INV-7 | 旧行零改动、不回填 | 迁移只 CREATE（§2.6） |

### 2.2 `tutor_visual_program`

| 列 | 类型 | 约束 | 口径 |
|---|---|---|---|
| `program_id` | TEXT | PK | `visual-program-` + sha256(规范串)，**= 对外 visualId** |
| `learner_id` | TEXT | NOT NULL | 与 `figure_generation_operation.learner_id` 同口径 |
| `role` | TEXT | NOT NULL | QUESTION_DIAGRAM / TUTOR_EXPLANATION（宿主派生，进身份哈希） |
| `source_sha256` | TEXT | NOT NULL | sourceJs UTF-8 字节的 SHA-256 |
| `source_js` | TEXT | NOT NULL | 原始源码（≤64 KiB，`VisualExecutionBudget.MAX_SOURCE_JS_BYTES`） |
| `initial_state_json` | TEXT | NOT NULL | StateV1 初始状态 |
| `initial_state_sha256` | TEXT | NOT NULL | 同上字节 SHA-256 |
| `api_version` | INTEGER | NOT NULL | SDK/API 版本（能否再执行的判据之一） |
| `sdk_version` | TEXT | NOT NULL | `visual-sdk-v1` 等版本标识 |
| `basis_revision_id` | TEXT | NULL | 题面 revision **软引用**（多态来源不建外键，理由同 `figure_generation_operation.source_id`，`FigureGenerationEntities.kt:28-29`） |
| `basis_fingerprint` | TEXT | NOT NULL | 来源冻结摘要（题面/源图 sha 组合） |
| `request_snapshot` | TEXT | NULL | 原模型 request/provenance，**有界**（不塞全量 prompt；上限建议 2 KiB，超限截断存 NULL） |
| `created_at_epoch_millis` | INTEGER | NOT NULL | |

**索引**：`UNIQUE` 由 PK 承担；另建

- `index(learner_id, created_at_epoch_millis)`——"这个学员最近有哪些程序"
- `index(basis_revision_id)`——按题面反查（软引用`no-new-dangling` 校验用）
- `index(source_sha256)`——"同一份源码被复用几次"（复用审计 / 熔断指纹同源）

### 2.3 `tutor_visual_snapshot`

| 列 | 类型 | 约束 | 口径 |
|---|---|---|---|
| `snapshot_id` | TEXT | PK | 宿主分配 |
| `program_id` | TEXT | NOT NULL, FK→program **RESTRICT** | |
| `state_json` | TEXT | NOT NULL | 规范 state（StateV1） |
| `state_sha256` | TEXT | NOT NULL | |
| `scene_schema_version` | INTEGER | NOT NULL | |
| `scene_json` | TEXT | NOT NULL | 验证后的场景 |
| `scene_sha256` | TEXT | NOT NULL | |
| `render_schema_version` | INTEGER | NOT NULL | = `Rendered.drawSchemaVersion`（当前 1） |
| `draw_commands_json` | TEXT | NOT NULL | DrawOps V1 文本（回放唯一输入） |
| `draw_commands_sha256` | TEXT | NOT NULL | |
| `content_ids_json` | TEXT | NOT NULL | `Rendered.contentIds` 冻结集合（解析器的 `frozenContentIds` 入参） |
| `viewport_width_du` / `viewport_height_du` | INTEGER | NOT NULL | 回放尺寸（1 du = 1 dp） |
| `font_scale_milli` | INTEGER | NOT NULL | fontScale×1000（整数存储，避免浮点在 schema 里的确定性争议） |
| `theme` | TEXT | NOT NULL | |
| `time_ms` | INTEGER | NOT NULL | |
| `seed` | INTEGER | NOT NULL | |
| `runtime_version` | TEXT | NOT NULL | QuickJS/宿主运行时标识（再执行判据；**回放不需要匹配**） |
| `compiler_version` | TEXT | NOT NULL | 编译器/SDK 版本 |
| `renderer_version` | TEXT | NOT NULL | painter **实现**版本（schema 版本已有 `DRAW_SCHEMA_VERSION=1`；实现版本当前无常量，见 §7 #3） |
| `font_manifest_version` | TEXT | NOT NULL | 可验证字体清单版本（**当前仓内无清单，见 §5**） |
| `question_verification_status` | TEXT | NOT NULL | 题面图核对状态（NOT_CHECKED / VERIFIED / MISMATCH） |
| `preview_asset_id` | TEXT | NULL, FK→`canonical_source_asset` **RESTRICT** | 可空预览（LOCAL_VISUAL_PREVIEW） |
| `status` | TEXT | NOT NULL | STAGING / LIVE |
| `owner_request_id` | TEXT | NULL | STAGING 期活动 owner（对账租约键） |
| `checkpoint_kind` | TEXT | NULL | INITIAL / LATEST / NULL（可回收中间态） |
| `created_at_epoch_millis` | INTEGER | NOT NULL | |
| `promoted_at_epoch_millis` | INTEGER | NULL | NULL = 未提升（与 status=STAGING 同向） |

**索引**

| 索引 | 为什么 |
|---|---|
| `UNIQUE(program_id, state_sha256, draw_commands_sha256)` | INV-6：重复 checkpoint 不产生新行（幂等） |
| `index(program_id, promoted_at_epoch_millis)` | "每 program 最新快照"（LATEST 迁移与恢复取最新） |
| `index(preview_asset_id)` | 第六类资产引用面（孤儿清理/备份按资产反查） |
| `index(status, created_at_epoch_millis)` | 启动对账扫 STAGING 僵尸（与 `figure_generation_operation` 的 status 索引同型） |

### 2.4 `tutor_message_visual`

| 列 | 类型 | 约束 | 口径 |
|---|---|---|---|
| `message_id` | TEXT | NOT NULL, FK→`tutor_message` **CASCADE** | 消息删 → link 删（引用行随宿主走） |
| `ordinal` | INTEGER | NOT NULL | 同一条消息的第 n 张图 |
| `program_id` | TEXT | NOT NULL, FK→program **RESTRICT** | |
| `current_snapshot_id` | TEXT | **NOT NULL**, FK→snapshot **RESTRICT** | 指向当前展示的快照；CAS 换指（§4.3） |
| `revision` | INTEGER | NOT NULL | 指针 CAS 的期望版本，从 1 起 |
| `ui_state_json` | TEXT | NULL | 4E.2 草稿（有界） |
| `form_digest` | TEXT | NULL | 表单摘要 |
| `created_at_epoch_millis` / `updated_at_epoch_millis` | INTEGER | NOT NULL | |

- PK = `(message_id, ordinal)`——**正是契约"messageId+ordinal 唯一"**。
- 索引：`index(program_id)`、`index(current_snapshot_id)`（反向：这张快照被哪些消息引用）。
- **设计判断**：`current_snapshot_id` 取 NOT NULL 而不是可空。可空会允许"link 存在但没有图"的
  中间态，恢复时无法区分"图还没到"与"图丢了"；用 NOT NULL + `INSERT…SELECT…WHERE status='LIVE'`
  把"没有 LIVE 快照就不许建 link"变成 SQL 级结构门。

### 2.5 `problem_revision_visual`

| 列 | 类型 | 约束 | 口径 |
|---|---|---|---|
| `problem_revision_id` | TEXT | NOT NULL, FK→`problem_revision` **RESTRICT** | 保存题固定引用 |
| `role` | TEXT | NOT NULL | 与 program.role 同值域 |
| `ordinal` | INTEGER | NOT NULL DEFAULT 0 | 同修订同角色多图顺序（对齐 `problem_revision_source_asset.ordinal`，v69 先例 `CaptureEntities.kt:213-217`） |
| `program_id` | TEXT | NOT NULL, FK→program **RESTRICT** | |
| `snapshot_id` | TEXT | NOT NULL, FK→snapshot **RESTRICT** | |
| `created_at_epoch_millis` | INTEGER | NOT NULL | |

- PK = `(problem_revision_id, role, ordinal)`（**镜像** `problem_revision_source_asset` 的 `(rev, asset, role)`，`CaptureEntities.kt:186-187`）。
- 索引：`index(program_id)`、`index(snapshot_id)`。
- **它消灭的失败**（契约 §8 原文"禁止靠会话还活着维持已保存题的图"）：会话删了，题还在，
  图的引用必须由保存题自己持有；没有这张表，裁剪/GC 只能靠"会话还活着"这个脆弱的间接判据。

### 2.6 外键与删除语义全图

```
tutor_conversation ──CASCADE──> tutor_message ──CASCADE──> tutor_message_visual
                                                              │ RESTRICT
problem ──RESTRICT──> problem_revision ──RESTRICT──> problem_revision_visual
                                                              │ RESTRICT
                                         tutor_visual_snapshot ─┘
                                              │ RESTRICT        │ RESTRICT
                                     tutor_visual_program   canonical_source_asset
                                                              ▲ RESTRICT (preview_asset_id)
```

CASCADE 只出现在"引用行随宿主消息走"这一条边；所有"被固定的事实"（program/snapshot/题面引用/预览资产/保存题）一律 RESTRICT。
这正是"删会话不删保存题引用"的可执行表达（契约 §8；`data-schema.md:18` 同款规则）。

### 2.7 与既有图/资产表的关系（逐项裁定）

| 既有对象 | 关系 | 理由 |
|---|---|---|
| `canonical_source_asset` | 新 **第六类引用站点** `tutor_visual_snapshot.preview_asset_id`；**不**复用 `source_type` 表达业务语义 | 契约 §8"标签只说明存储来源，不定义是否生图/费用"；`AndroidCanonicalAssetVault.kt:401-403` 的 id 规则不变 |
| `figure_generation_operation` | 仅**软引用**（`source_kind='TUTOR_VISUAL'` 之类新增枚举值，或沿用既有 TUTOR_MESSAGE/PROBLEM_REVISION）+ 审计镜像 | 操作台账是"为什么这么做"，四表是"图怎么存"；两者都不该给对方建外键（既有台账明确"不参与预算/不建外键"，`FigureGenerationEntities.kt:18-22`） |
| `figure_generation_attempt.output_asset_id` | 只指向 canonical PNG；**JS 图不走这一列** | 契约 §8"不能把 visualId 冒充旧像素 figureId"；JS 产物身份是 `(program_id, snapshot_id)` |
| `FigureArtifactRef.JsSnapshot(programId, snapshotId)` | 四表是它的持久化载体 | 形状已存在（`CleanImageGenerator.kt:90-104`），T3 只补生产构造点 |
| `tutor_message_source_asset` | **不复用** | 那张表是"学生附图"（PK `(message_id, ordinal)`，`role`/`basis_revision_id` 是图角色），语义是像素证据；JS 图是可以重放的矢量快照，混表会让 `role` 值域与 GC 判据纠缠 |

### 2.7.1 备份机器清单归位（建议；T3 落 `SmbkSelectionPlan` 时照此）

| 表 | 归位 | 行级谓词 / 理由 |
|---|---|---|
| `tutor_message_visual` | **SESSIONS**（类别） | 随消息 CASCADE；会话被裁时 link 必须一起走，否则留下指向已删消息的悬空 link |
| `problem_revision_visual` | **ISSUE_BOOK**（类别） | 保存题的固定引用；会话裁掉后它必须存活（契约 §8"会话删除不删除保存题引用"） |
| `tutor_visual_program` | **SPLIT**（行级） | kept ⟺ 被 kept 的 `tutor_message_visual.program_id` 或 `problem_revision_visual.program_id` 引用（型同 `figure_generation_operation` 的 `O_ref` 反向闭包，`BackupSnapshotTrimmer.kt:678-692`） |
| `tutor_visual_snapshot` | **SPLIT**（行级） | kept ⟺ 被 kept 的 link/题面引用，或 `checkpoint_kind='INITIAL'`（初始快照是重建基线，裁掉则"初始版本"永久丢失） |

`trimOrder` 追加顺序（**子先父**）：`tutor_message_visual` → `problem_revision_visual` → `tutor_visual_snapshot` → `tutor_visual_program`。
新增软引用 1 条：`tutor_visual_program.basis_revision_id → problem_revision`（kind `cross-category`，check `no-new-dangling`，
strength `weak-host-hint`——台账行可以比题面活得久，与 `model_task.subject_id` 同口径，`SmbkSelectionPlan.kt:290-300`）。

> 否决"四表全进 alwaysTables"：它们都是**用户事实**，必须能被"只备份题/只备份会话"选择；
> 恒随包会让"我不想要这些图"变成不可能。

### 2.8 v69→70 迁移草案（T2 落码用；DDL 必须与 `schemas/70.json` 逐字一致）

形态照抄 `FIGURE_GENERATION_MIGRATION_68_69`（`FigureGenerationMigration_68_69.kt:35-49`）：

```kotlin
internal val VISUAL_SNAPSHOT_MIGRATION_69_70 = object : Migration(69, 70) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(TUTOR_VISUAL_PROGRAM_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_LEARNER_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_BASIS_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_PROGRAM_INDEX_SOURCE_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_CONTENT_DDL_69_70)   // UNIQUE
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_LATEST_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_PREVIEW_DDL_69_70)
        connection.execSQL(TUTOR_VISUAL_SNAPSHOT_INDEX_STATUS_DDL_69_70)
        connection.execSQL(TUTOR_MESSAGE_VISUAL_DDL_69_70)
        connection.execSQL(TUTOR_MESSAGE_VISUAL_INDEX_PROGRAM_DDL_69_70)
        connection.execSQL(TUTOR_MESSAGE_VISUAL_INDEX_SNAPSHOT_DDL_69_70)
        connection.execSQL(PROBLEM_REVISION_VISUAL_DDL_69_70)
        connection.execSQL(PROBLEM_REVISION_VISUAL_INDEX_PROGRAM_DDL_69_70)
        connection.execSQL(PROBLEM_REVISION_VISUAL_INDEX_SNAPSHOT_DDL_69_70)
    }
}
```

外键列必须留索引（Room 会在 `onOpen` 打开 `PRAGMA foreign_keys = ON`，缺索引会让父表删除全表扫；
`FullMigrationMatrixInstrumentedTest.kt:35-41` 注释已记载这条行为）。

**旧行不动**：迁移只 CREATE TABLE/INDEX，零 `ALTER`、零 `UPDATE`、零回填；升级后四表为空
（"历史图没有快照可考"如实呈现，不编造历史快照——与 v68→69 的"无台账如实呈现"同一纪律，
`FigureGenerationMigration_68_69.kt:20-26`）。

**迁移矩阵**：现有 `FullMigrationMatrixInstrumentedTest.everyExportedSchemaVersionMigratesToCurrent…`
（`:44-62`）自动覆盖 1..70 的逐版；另需一条**带数据**用例（照 `FavoriteAndManualDraftMigrationInstrumentedTest`
的分工：空库看不出"旧行还在不在"，`FavoriteAndManualDraftMigrationInstrumentedTest.kt:20-22`）：
种子 v69 库写一条 message/revision → 升 70 → 断言旧行原样、约束生效、再开一次不漂。

---

## 3. D-02：身份不可变 + staging→LIVE

### 3.1 `visualId = programId` 的派生规则（不可变的结构性保证）

```
programId = "visual-program-" + sha256Hex(
    长度前缀(sourceJs UTF-8 字节)
  ‖ 长度前缀(initialStateJson UTF-8 字节)
  ‖ 长度前缀(basisFingerprint)
  ‖ apiVersion ‖ sdkVersion ‖ role
)
```

- 长度前缀不是装饰：`"ab"+"c"` 与 `"a"+"bc"` 必须摘要到不同 id（同型先例
  `cleanRedrawTaskId` 的注释与实现，`CleanRedrawTaskContract.kt:94-111`）。
- **role 进身份哈希**（建议，需 T3 确认）：代价是"同一份源码以两种角色出现"会存两行（各自 ≤64 KiB）；
  收益是 program 行**没有任何可变列**，"role 被改写"这种失败在结构上不存在。
  备选（role 不哈希、建行时 CAS 断言一致）被否决：它把不变性降级成一次运行时检查。
- 一旦算出 id，`INSERT` 用 `INSERT OR IGNORE` + 回读**逐列比对**：同 id 但任一列不同 ⇒
  `ImmutablePayloadConflictException("tutor_visual_program", programId)`（既有冲突类型，
  `StudyDatabasePort.kt:67`）。**不用 `ON CONFLICT DO UPDATE`**——那正是"原位覆盖已被引用的源码"。

### 3.2 状态机

```
                      ┌────────── 进程死亡 / 取消 / 存储失败 ──────────┐
                      ▼                                                │
(无行) ──S2──> program(不可变)                                          │
(无行) ──S3──> snapshot:STAGING(owner_request_id, promoted_at=NULL) ───┤
                      │                                                │
                      │  S4 同一事务：message + link + CAS promote      │ 对账：owner 租约过期
                      ▼                                                ▼ 且无 link/题面引用
              snapshot:LIVE(promoted_at=now)                     snapshot:ABANDONED
                      │                                       （行删除；文件由既有 GC 回收）
                      │  再次 checkpoint（内容不同）
                      ▼
              snapshot2:STAGING ──同一事务 CAS──> LIVE + LATEST
                                                 （旧 LATEST demote → NULL，仅当未被固定）
```

- 迁移集合：`STAGING → LIVE`、`STAGING → ABANDONED(删除)`、`LATEST → NULL(demote)`。
  **没有 `LIVE → *`**：LIVE 是不可回退的事实（回退 = 用户看到过的图"消失"）。
- `ABANDONED` **不写成第三个 status 值**：对账直接删行更诚实（行从未被任何引用指向，
  删掉不制造"曾经存在"的假事实；status 枚举越短，读侧分支越少）。
  若 T3 认为需要留痕，则改为 `status='ABANDONED'` 且 GC 视为可删——两种都可，**不要两者都做**。

### 3.3 每一步的事务边界与失败回滚

| 步 | 做什么 | 事务边界 | 失败与回滚 |
|---|---|---|---|
| S0 | 配额预检：每 program ≤4 MiB、全局 ≤32 MiB、磁盘可用空间 | 只读，无事务 | 超限 → **拒绝提交**并回有界原因；**不删任何用户事实**（契约 §8.1） |
| S1 | 预览字节：临时文件 → 编码 → sha → rename；随后 vault 注册资产行 | 文件系统（无 DB 事务）+ vault 自己的插入 | 写失败/超限 → 无 DB 行、临时文件由 `finally` 删（`AndroidCanonicalAssetVault.kt:411-413` 同型）；资产行已建但后续失败 → 孤儿资产，由 `OrphanAssetGc` 宽限期回收（宽限期来源：`CaptureEntities.kt:11-13` 注释"orphan-cleanup grace window"） |
| S2 | 插入 program 行 | 独立小事务 | 冲突 → `ImmutablePayloadConflictException`；已存在且逐列相同 → 幂等成功（复用） |
| S3 | 插入 snapshot 行 `status=STAGING, owner_request_id=requestId` | 独立小事务 | 唯一索引命中（同内容快照已存在）→ 回读该行；若该行是 STAGING 且 owner 不同 → **CAS 到本 owner**（`UPDATE … WHERE owner_request_id=:old`），0 行命中即视为冲突并放弃本次提交 |
| S4 | **提交首张可见图**：`insert tutor_message` + `insert tutor_message_visual` + CAS `STAGING→LIVE` + checkpoint 标记 | **一个 `@Transaction`** | 任一步抛错 → 整体回滚：消息不存在、link 不存在、快照仍 STAGING（对账接手）；**不写第二次补救事务**（第二次写会制造"消息有、图无"的半成品） |
| S5 | 提交后：无 | — | 无第二次写；`attempt`/审计镜像若需要，也在 S4 同一事务内 |

> **关键设计判断**：S4 必须是"一条事务、一次提交"。契约 §8.1 原文"首张可见图随 assistant
> message/trace/visual link 在同一事务提交"就是这个意思；把它拆成"先写消息、再补图"会在两次写之间
> 制造不可恢复的中间态（进程死在中间 = 用户看到正文但没有图，且没有任何记录说明图本该来）。

### 3.4 link 插入的 SQL 级结构门

```sql
INSERT INTO tutor_message_visual
    (message_id, ordinal, program_id, current_snapshot_id, revision,
     ui_state_json, form_digest, created_at_epoch_millis, updated_at_epoch_millis)
SELECT :messageId, :ordinal, :programId, snapshot_id, 1,
       :uiStateJson, :formDigest, :now, :now
FROM tutor_visual_snapshot
WHERE snapshot_id = :snapshotId AND status = 'LIVE';
```

命中 0 行 ⇒ 该快照不是 LIVE（或不存在）⇒ 抛 `ImmutablePayloadConflictException("tutor_message_visual", ...)`。

这样"消息引用了非 LIVE 快照"不是靠 code review 保证，而是**这条 INSERT 本身写不进去**。
与 `CleanRedrawTaskDao.kt:281-287` "以命中行数判赢"同一纪律。

### 3.5 指针 CAS（换图/重渲染）

```sql
UPDATE tutor_message_visual
SET current_snapshot_id = :nextSnapshotId,
    revision = revision + 1,
    updated_at_epoch_millis = :now
WHERE message_id = :messageId AND ordinal = :ordinal
  AND revision = :expectedRevision
  AND EXISTS (SELECT 1 FROM tutor_visual_snapshot AS s
              WHERE s.snapshot_id = :nextSnapshotId AND s.status = 'LIVE');
```

命中 0 行 ⇒ 迟到 checkpoint 不覆盖新状态（正是契约 §8.1 的"迟到 checkpoint 不覆盖新状态"）。
**不要**回读 `revision == expected+1` 判成功（并发下输家也会读到 +1，`CleanRedrawTaskDao.kt:281-283` 已记载同一陷阱）。

### 3.6 T2/T4 的 DAO 仪器化用例矩阵（验收条件 → 用例）

| 用例 | 做法 | 期望 |
|---|---|---|
| 取消 | 起一个在 S3 后取消的协程，随后跑对账 | 快照被删/标记 ABANDONED；无 link 行；资产可由 GC 回收；**不删 LIVE** |
| 存储满 | 注入可用空间 0（照 `BackupSnapshotTrimmer.kt:69-72` 的注入口形态） | S0 拒绝提交；DB 零新增行；返回有界原因（不是 `OutOfMemoryError`/`SQLiteFullException` 穿透） |
| CAS 冲突 | 两个 owner 各自 S3 同一内容快照；两个 message 同 `(message_id, ordinal)` | 前者：一个 owner 赢，另一个按冲突返回；后者：唯一键冲突 → `ImmutablePayloadConflictException` |
| 重复提交 | 同一 program+state+draw 提交两次 | 第二次返回既有 LIVE 快照，行数不变（INV-6） |
| 迟到 checkpoint | 指针已 revision=2，用 expectedRevision=1 提交 | 0 行命中 → 不覆盖；`revision` 仍 2 |
| 消息删除 | 删会话（CASCADE 到消息 → link） | link 删；program/snapshot **在**；被 `problem_revision_visual` 引用的快照**在** |

---

## 4. D-05：冷恢复

### 4.1 "重启不执行 JS"的结构性保证（三层，缺一层都只是口头约定）

| 层 | 保证 | 判据 |
|---|---|---|
| **类型层** | 恢复入参是 `VisualSnapshotRestoreRecord`——**不含 `sourceJs` 字段**（program 行里的源码不进入恢复 DTO） | 恢复函数签名里没有 `String sourceJs`；`source_js` 只在"显式再执行"路径读取 |
| **依赖层** | `core/data` 的恢复包不 import `VisualExecutionEngine` / `QuickJsVisualExecutionEngine`；恢复只用 `VisualDrawOpsParser.parse(text, contentIds, null)` + painter | **grep 门**（A-13 同型）：`VisualSnapshotRestore` 文件里 `VisualExecutionEngine` 0 命中 |
| **观测层（设备）** | 冷启动后打开含图消息：隔离进程 **0 次 spawn**、0 Provider 派发 | 复用 `core/data/src/test/kotlin/.../visual/FigureHttpTransportBypassObservationTest.kt` 的 0 派发观测模式；spawn 计数从隔离服务日志读 |

> 为什么不用"运行时判一个 `if (!isColdStart)`"：那是**可被后来者删掉的一行代码**；
> 类型层与依赖层是**改错就编译不过**的约束。契约 §5 的"持久化的 RenderScene 必须是可序列化 DTO…
> 冷恢复…不再执行 JS"要的正是后者。

### 4.2 三档兼容与匹配门

| 档 | 匹配什么 | 匹配 | 不匹配 |
|---|---|---|---|
| **① 再执行**（用户明确要求重跑源码） | `program.api_version` + `sdk_version` + `runtime_version` | 允许起隔离引擎执行 | 拒绝执行，给"该程序版本过旧"的可读说明；**不自动降级成"试试看"** |
| **② 重放矢量**（默认恢复路径） | `scene_schema_version` + `render_schema_version` + `renderer_version` | `VisualDrawOpsParser.parse(...)` → painter | 降到 ③ |
| **③ 只读** | `preview_asset_id` 存在且资产校验通过 | 显示预览 PNG | 显示文字/公式说明；**未知 JSON 绝不交给 painter** |

**字体匹配是 ② 的附加门，不是 ① 的门**：`font_manifest_version` 不在当前可验证清单内 ⇒ ② 不允许"承诺像素一致"，
按契约 §8 改为"保存的局部栅格/整图预览"路径（③）。理由：契约明写"不能只存 Typeface 名称便称字体版本精确匹配"。

**runtime/compiler 不参与 ②**：重放不需要运行时。把 runtime 版本塞进重放门会让"应用升级后旧图全部降级"
——这是无谓的可用性损失，也不是真实失败。

### 4.3 冷恢复判定序（建议的落码形态）

```
fun restore(record: VisualSnapshotRestoreRecord): RestoreOutcome {
  if (record.sceneSchemaVersion != SUPPORTED_SCENE || 
      record.renderSchemaVersion != VisualDrawOpsParser.DRAW_SCHEMA_VERSION ||
      record.rendererVersion != CURRENT_RENDERER_VERSION) return fallback(record)   // ②→③
  if (record.fontManifestVersion !in VERIFIED_FONT_MANIFESTS) return fallback(record) // 像素门
  val ops = VisualDrawOpsParser.parse(record.drawCommandsJson, record.contentIds, null)
  if (ops is Invalid) return fallback(record)          // 码面分流：DRAWOPS_SCHEMA_VERSION / MISSING_FIELD / TYPE
  return Replay(ops)                                    // 交给 painter，两种目标同入口
}
```

- `parse` 的策略码已按版本/缺字段/类型分流（`VisualDrawOpsParser.kt:51-55`），恢复侧**按码决定降级还是报错**，
  不把三类失败折成同一句"图坏了"（M-04 同型的诊断纪律）。
- 恢复**必须重新校验**（不信任磁盘上自称 validated 的标记）：契约 §8"备份恢复、历史读取均重新校验 DTO/摘要/引用"。
  因此"`preview_asset_id` 指向的资产字节 sha 与 `canonical_source_asset.content_sha256` 逐位一致"要在恢复时核对
  （复用 `canonicalAssetFileIsIntact`，`AndroidCanonicalAssetVault.kt:44-50`）。

### 4.4 checkpoint 与配额（D-03 半场，随 D-05 落）

| 规则 | 实现要点 |
|---|---|
| 每 program 保留**初始 + 最新**两份 | `checkpoint_kind=INITIAL` 在首个 LIVE 提交时写入且永不变；`LATEST` 以 CAS 迁移（旧 LATEST→NULL，仅当它未被消息/题面/导出固定） |
| 被固定的历史快照不得回收 | 固定集合 = `tutor_message_visual.current_snapshot_id` ∪ `problem_revision_visual.snapshot_id` ∪ 导出冻结集（E-01，`core/export` 热区，本批不做） |
| 每 program 可回收派生 ≤4 MiB，全局 ≤32 MiB | 计量口径建议：`draw_commands_json + state_json + scene_json` 的 UTF-8 字节和；**被固定的不算可删缓存**（契约 §8.1 原文） |
| 磁盘不足 ⇒ 拒提交并说明 | 走 S0 预检；不得"删用户事实腾空间" |

> 本批只落"初始+最新两份 + 磁盘不足拒提交"（清单 D-03 的下界，MVA 排序 §2 已裁可裁半）；
> 4 MiB/32 MiB 的实际淘汰随 V-03 批次补（同上）。

---

## 5. GitHub 参考（只借鉴，不引依赖）

通道：`gh api`（本会话实跑）。评分为本文件的主观判断，**只看已验证的元数据 + 读到的原文**。

| 仓 | license（verified） | stars | last push（verified） | 借哪一点 | 本仓落地 | 评分（license/维护度/可复用） |
|---|---|---|---|---|---|---|
| [androidx/androidx](https://github.com/androidx/androidx)（`room3/room3-testing`） | Apache-2.0 | 6,121 | 2026-10-28 | `MigrationTestHelper.createDatabase(version)` / `runMigrationsAndValidate(version, migrations)` 的**版本驱动矩阵**形态（API surface 由 `room3/room3-testing/api/current.txt` 实读确认） | 形态已由手写矩阵覆盖；**不引依赖**（加依赖 = 改热区构建文件） | 5 / 5 / 3 |
| [distribution/distribution](https://github.com/distribution/distribution) | Apache-2.0 | 10,666 | 2026-10-28 | **两段式上传**：POST 开会话 → PUT `?digest=` 提交，服务端校验摘要才落盘；`HEAD /blobs/<digest>` 已存在即免传；并发同层"第一个完成的赢"（`docs/content/spec/api.md` 实读：:496-560、:84-94） | = 本设计的 `STAGING → LIVE` + 内容幂等 + 并发只让一个赢（§3.2/§3.3） | 5 / 5 / 5 |
| [restic/restic](https://github.com/restic/restic) | BSD-2-Clause | 36,856 | 2026-10-18 | **`forget` 与 `prune` 两段**：先摘引用、再只删"仅被已删快照引用"的数据；被引用的数据在 prune 中存活（`doc/060_forget.rst` 实读：:17-23、:75-95） | = 本仓既有"trimOrder 删行 + OrphanAssetGc 删文件"两段；四表必须在**两段**都登记（§2.7/§1.3） | 5 / 5 / 4 |
| [bazelbuild/remote-apis](https://github.com/bazelbuild/remote-apis) | Apache-2.0 | 459 | 2026-10-20 | **内容寻址存储 + 语义标识到 Digest 的映射分离**（`README.md` 实读：CAS + Remote Asset API） | = `programId` 由内容派生，而"这条消息第几张图"是宿主语义键（`(message_id, ordinal)`），两者分离 | 5 / 4 / 4 |
| [square/okio](https://github.com/square/okio) | Apache-2.0 | 9,054 | 2026-10-26 | `atomicMove` 契约：**目标文件绝不会是源的半个前缀**；无法原子替换的文件系统上宁抛 `IOException` 也不做半成品；跨卷必须 copy+delete 并自行设计恢复（`FileSystem.kt:315-339` KDoc 实读） | = vault 的 temp→rename + rename 后复核 sha（`AndroidCanonicalAssetVault.kt:385-388` 已经这么做）；本设计**不引入新文件语义** | 5 / 5 / 3 |

**结论**：五个参考全部**仅借鉴形态**，本设计不新增任何第三方依赖。理由不是"不信任库"，而是
本仓已有等价实现（vault 内容寻址、CAS 命中行数、两段式裁剪）——按规则"项目内已有可复用代码优先于外部方案"。

---

## 6. 牵动面清单（改一处先闭合影响面）

| 落点 | 改什么 | 谁写 | 漏掉的后果 |
|---|---|---|---|
| `StudyDatabase.kt:134` | 69→70 | T2 | 新表不建 |
| 新 migration 文件 + 15 条 DDL 常量 | §2.8 | T2 | 迁移缺索引 → 父表删除全表扫；DDL 与 JSON 差一字符 → 真机升级炸（`:27-33`） |
| `schemas/…/70.json` | 构建导出，不手改 | T2（构建） | 矩阵测试读不到 70 |
| `@Entity` 四个 + `@Database(entities=[…])` | 四表入册 | T2 | Room 编译报 schema 不一致 |
| **`CanonicalAssetReferenceSites.kt:28-47`** | 加第六个 `NOT EXISTS`（preview） | T2 | `OrphanAssetGc` 删掉在显示的预览 |
| **`CanonicalAssetReferenceSingleSourceTest.kt`** | 五站点→六站点对拍 | T4 | 单源钉测试失效 |
| **`BackupSnapshotTrimmer.FULL_ASSET_PINS_SQL`/`TRIMMED_ASSET_PINS_SQL`（`:1229-1243`）** | 两条 SQL 各加 `UNION SELECT preview_asset_id …` | T3 | 拆分备份丢预览字节，恢复后图空 |
| **`SmbkSelectionPlan`** | 86→90 表、78→82 裁剪项、ISSUE_BOOK 30→31、SESSIONS 6→7、SPLIT 5→7、软引用 20→21、`CLOSURE_VERSION` 2→3（`:45`/`:181`/`:189`/`:510-528`） | T3 | 硬 `require` 直接崩；或漏表 → 裁剪器"未知表拒绝"（`BackupSnapshotTrimmer.kt:1217-1219`） |
| `docs/research/2026-10-17-6c2-content-selection-design.md` §9.11 机器块 | 同步升 v3 | T3 | `SmbkSelectionPlanTest` 反向解析对拍失败 |
| `docs/current/data-schema.md:24-32` | "Planned visual records (not migrated)" → 改为 70 已落 | T4 | 文档继续写"未迁移"，漂移 |
| `docs/current/visual-generation-contract.md` §8 | 不改（已是目标态）；若身份哈希含 role 需补一句 | T4 | 实现与契约散文不一致 |
| 请求 schema 18 | **本批不动**（§0 C8） | — | 无谓改指纹与编码 |

---

## 7. 未验证项与边界（如实）

1. **本会话零 Gradle、零 adb/am instrument、零 git 写**。所有"已落/未落"来自实读源码与 `gh api`；**无一条设备或构建结论**。
2. **`schemas/70.json` 不存在**：§2.8 的 DDL 是草案，T2 落码后必须与导出 JSON 逐字对拍（`:27-33` 的纪律）；本轮无法验证逐字一致。
3. **`renderer_version` 只有半个载体**：painter 已有 `DRAW_SCHEMA_VERSION = 1`（`VisualDrawOpsPainter.kt:330`，schema 版本）与错误码 `PAINTER_SCHEMA_VERSION`（`VisualDrawOpsPainterPure.kt:30`），但**没有"实现版本"**——painter 改绘制行为而不改 schema 时，旧快照仍会被判"匹配"却可能画出不同的图。T3/T4 需在 painter 侧新增一个实现版本常量（落点 `core/data/visual`，非热区），否则 ② 档的 renderer 匹配门等于不存在。**登记为 T3 前置项。**
4. **`font_manifest_version` 无载体**：仓内没有 `res/font`/`assets/fonts` 目录（`find` 零命中），`core/ui` 只用 `FontFamily.Monospace` 与系统默认（`AiReplyRichMarkdown.kt:74`、`ClaudeCodeBlock.kt:45`），契约要求的"可验证资源清单"尚不存在。在清单落地前，字体门只能保守地走 ③（预览/文字）或明确标注"未承诺像素一致"。**登记为 T3/T4 前置项。**
5. **`LOCAL_VISUAL_PREVIEW` 常量零命中**（本会话 grep）：`preview_asset_id` 的 `source_type` 取值待 D-04 定义；本设计只用"可空外键 + canonical 资产"表达，不预设标签字符串。
6. **Room 官方迁移指南未取到**：`developer.android.com` 两次连接超时（同一通道两次失败，按纪律换到 `gh api` 读 `androidx/androidx` 源码与 API surface）。因此"官方文档建议"类表述**未采纳为事实**；本文关于 `MigrationTestHelper` 的陈述只依据仓内 `api/current.txt` 实读。
7. **`SmbkSelectionPlan` 的机器块升级粒度（v3 vs 原地改 v2）未经主会话裁定**：本文给"升 v3 + CLOSURE_VERSION 2→3"的建议，依据是闭合摘要会被新表改变、新旧摘要必须可区分；最终形态归 T3/主会话。
8. **备份 34 例不回退、迁移矩阵 1→70 全绿、冷恢复设备用例**：全部**未跑**（本会话不执行构建/设备）。批次 30 验收里的这三项由脚本串行门与设备轨裁决。
9. **AR 计费/费用口径**：本文不涉及；四表不参与预算（与 `figure_generation_operation` 的镜像计数同一纪律）。
10. **GitHub 评分是主观判断**（亲和度/可复用性列），只有 license/stars/last push 是 `gh api` 实测值；引用原文的章节行号来自本会话 `gh api` 拉取的 raw 内容。

---

## 8. 来源

**仓内（本会话实读）**
- 契约：`docs/current/visual-generation-contract.md`（§8 `:167-209`、§8.1 `:186`）
- 数据 schema 规则：`docs/current/data-schema.md`（`:6-22` 规则、`:24-32` "Planned visual records"）
- MVA 剩余排序：`docs/research/2026-10-28-mva-remaining-order.md`（§1.2 B1-B4 `:51-55`、§2 批次 30 `:120-129`）
- 准入清单 D 组：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（§3.4 `:164-170`、§3.4 批次 30 注记）
- 计划：`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md`（§1 任务 3-5 `:19-22`、§3 验收 `:43-47`）
- DB：`core/database/.../StudyDatabase.kt:134`；`FigureGenerationMigration_68_69.kt:20-49`；`FigureGenerationEntities.kt:13-120`；`CleanRedrawTaskContract.kt:94-111`；`dao/CleanRedrawTaskDao.kt:127-148,272-300`；`dao/CanonicalAssetReferenceSites.kt:28-66`；`entity/CaptureEntities.kt:17-38,186-217`；`entity/TutorConversationEntities.kt:119-141,233-270`；`entity/ProblemEntities.kt:26,40-58`；`StudyDatabasePort.kt:67`
- 测试形态：`core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt:35-62`
- 备份：`core/data/.../backup/SmbkSelectionPlan.kt:6-19,45,69-183,510-528,600-613`；`BackupSnapshotTrimmer.kt:63-72,629-639,1214-1256`
- 资产：`core/data/.../capture/AndroidCanonicalAssetVault.kt:44-50,344-413`
- 绘制：`core/data/.../visual/VisualDrawOpsPainter.kt:330`（`DRAW_SCHEMA_VERSION`）、`VisualDrawOpsPainterPure.kt:30`（错误码 `PAINTER_SCHEMA_VERSION`）、`core/ui/.../AiReplyRichMarkdown.kt:74`（字体面现状）
- DTO：`core/domain/.../VisualExecutionEngine.kt:56-81,241-262`；`core/domain/.../CleanImageGenerator.kt:44-104`；`core/data/.../visual/VisualDrawOpsParser.kt:17-55,83,112`；`core/model/.../ModelTasks.kt:478,546`
- 构建：`gradle/libs.versions.toml:12,77-79`

**外部（`gh api` 实跑，均未引依赖）**
- `androidx/androidx`：Apache-2.0 / 6,121 stars / pushed 2026-10-28；`room3/room3-testing/api/current.txt`（`MigrationTestHelper` API surface）
- `distribution/distribution`：Apache-2.0 / 10,666 / 2026-10-28；`docs/content/spec/api.md`（两段式上传 :496-560、层去重 :84-94）
- `restic/restic`：BSD-2-Clause / 36,856 / 2026-10-18；`doc/060_forget.rst`（forget→prune :17-23、:75-95）
- `bazelbuild/remote-apis`：Apache-2.0 / 459 / 2026-10-20；`README.md`（CAS + Remote Asset API）
- `square/okio`：Apache-2.0 / 9,054 / 2026-10-26；`okio/src/commonMain/kotlin/okio/FileSystem.kt:315-339`（`atomicMove` 契约）
