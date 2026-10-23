# 批次 18 基线（6C-2 内容选择备份 v3）

> 登记时点：2026-10-23 21:11–21:18（+08:00），共享工作树快照；计数在登记过程中仍在变化（见 §1.2）。
> 只读登记：本文件是本轨（T0）的唯一产出。未运行 Gradle，未安装/启动 App，未改动任何代码，未执行 `git add/commit/stash/checkout/reset/push`。
> 所有命令均在 `D:\smart mistake book` 执行；结论只覆盖下列命令输出与源码行，不代表编译、设备、迁移或真实质量已经通过。
> 依据计划：`.zcode/plans/plan-2026-10-23-batch-18-6c2-backup-selection.md`（下称“计划”）。
> 设计件：`docs/research/2026-10-17-6c2-content-selection-design.md`（下称“设计件”）。

## 1. 版本控制基线

### 1.1 HEAD、origin/main 与跟踪关系

| 项 | 值 | 命令 / 来源 |
| --- | --- | --- |
| HEAD | `9d2096adcd2ebd4072377626754318c6fb327499`（短号 `9d2096ad`） | `git rev-parse HEAD` |
| 本地 `origin/main` | `9d2096adcd2ebd4072377626754318c6fb327499`（短号 `9d2096ad`） | `git rev-parse origin/main` |
| 跟踪分支 | `main` 上游为 `origin/main`，`## main...origin/main` | `git status -sb`、`git branch -vv` |
| 领先 / 落后 | `0	0` | `git rev-list --left-right --count HEAD...origin/main` |
| 最近提交 | `9d2096ad 2026-10-23T00:48:01+08:00 docs(research): 批次17 记录补提交与推送结果（ecb1b3e6）` | `git log -1 --format='%H %ad %s' --date=iso-strict` |
| 真实远端 | **未核**（本轮未执行 `git ls-remote`） | — |

结论：当前 HEAD **是** `9d2096ad`，且本地 `origin/main` 指向同一提交，`0/0` 无领先/落后。计划 `:5` 的登记与实测一致。真实远端本轮未查询，不能据此声称远端一致。

### 1.2 工作树 modified / untracked 计数与主要归属

共享工作树在登记期间持续被其他线写入，计数**不是稳定值**。同一会话内的连续快照：

| 时点（+08:00） | modified | untracked | 总计 | 取数方式 |
| --- | ---: | ---: | ---: | --- |
| 约 21:11 | 126 | 413 | 539 | `git status --porcelain=v1 \| wc -l` + `awk '{print $1}'` |
| 约 21:13 | 127 | 414 | 541 | 同一命令的 Python 解析（`git status --porcelain=v1 -z`） |
| 21:14:45 | 128 | 415 | 543 | 同上 |
| 21:15:52 | **128** | **417** | **545** | 同上（本文件采用的基线快照） |
| 21:17:55（写入本文件后） | 132 | 418 | 550 | 同上；其中新增的 1 个 untracked 是本文件，modified +4 全部来自他线继续写入 |

变化实例（均在本轮登记窗口内观察到）：`feature/tutor/.../TutorLobbyRoute.kt` 从未修改变为 `M`；未跟踪项新增 `core/data/src/test/.../knowledge/dense/DenseUnionTest.kt`、`core/database/src/test/.../DenseRecallUnionOrderingTest.kt`、`core/ui/.../ClaudeLayout.kt`、`app/src/main/assets/`。因此本文件的所有计数都必须带时点阅读；提交/收口时重新取数，不得沿用。

modified（128，21:15:52 快照）按顶层目录：

| 目录 | 数量 | 细分与说明 |
| --- | ---: | --- |
| `feature/` | 45 | `feature/tutor` 19、`feature/library` 10、`feature/review` 8、`feature/capture` 7、`feature/profile` 1 |
| `core/` | 35 | `core/data` 10、`core/database` 9、`core/ui` 7、`core/export` 5、`core/domain` 4 |
| `docs/` | 33 | `docs/research` 9、`docs/current` 7、`docs/history` 3、`docs/superpowers` 2，其余为仓库既有文档 |
| `app/` | 8 | `SecondaryScreens.kt`、`SmartMistakeBookRoot.kt`、`LearningMasteryScreen.kt`、复习提醒相关 3 个文件、`KnowledgeReviewSessionInstrumentedTest.kt`、`SavedMistakeSheetBytesTest.kt` |
| `tools/` | 5 | `dense_build` 3、`kb_build` 1、`tests` 1 |
| `knowledge-production/` | 1 | `wusan/materials.jsonl` |
| 根目录 | 1 | `dynamic_gui_architecture_plan.md` |

untracked（417，21:15:52 快照）按归属：

| 归属 | 数量 | 说明 |
| --- | ---: | --- |
| 根目录 | 355 | 其中 320 个以 `.` 开头；主要是 `.agent_*` 审计截图/CSV、`.tmp_*`、`.wf-manifest*`、`out/`、`tmp/`、`crops/` 等根级临时产物 |
| `feature/` | 17 | `feature/tutor` 13（androidTest、`TutorComposerTextPolicy.kt`、`TutorTailFollowPolicy.kt` 等）、`feature/library` 4（批量导入/UI 测试等） |
| `tools/` | 12 | `tools/kb_coverage` 11、`tools/kb_build` 1 |
| `knowledge-production/` | 11 | 知识生产临时数据/脚本 |
| `docs/` | 10 | `docs/research` 7、`docs/superpowers` 3 |
| `core/` | 9 | `core/ui` 6、`core/data` 2、`core/database` 1（含 dense/KB 与 UI 采纳在飞文件） |
| `app/` | 3 | 2 个 androidTest、`app/src/main/assets/` |

归属判断：这些是计划 `:14` 所述的“他线在飞改动”（批量导入/采集、复习提醒/复习会话、Tutor UI/UI 采纳、KB/dense、导出与文档整合），**不是 T0 产出**。写入本文件后实测 132 M + 418 ?? = 550（21:17:55）：untracked 418 项中含本文件，相对 21:15:52 净增 1，T0 只贡献这 1 项；modified +4 全部来自他线继续写入。共享树仍在变，任何计数都只能带时点引用。

## 2. Schema 基线

### 2.1 Room 数据库 schema 69

| 项 | 当前值 | 证据 |
| --- | --- | --- |
| DB 版本常量 | **69** | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:134`：`internal const val STUDY_DATABASE_VERSION = 69` |
| `@Database` 声明 | `:143` 起；视图 2 个 | 同文件 `:143-145`：`views = [LibraryCatalogView::class, KnowledgeQuestionLatticeView::class]` |
| 实体列表 | 86 个 `...Entity::class` | 同文件 `:145-232`；`grep -c "::class"` 对 `:145-233` 为 86 |
| 69 新增实体 | `FigureGenerationOperationEntity`、`FigureGenerationAttemptEntity` | 同文件 `:230-231` |
| 版本传入 | `version = STUDY_DATABASE_VERSION` | 同文件 `:233` |
| `69.json` | 存在，358,389 字节，2026-10-21 21:43 | `ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json` |
| schema 内容 | **86 表 / 2 视图 / 92 条 FK 边** | 对 `69.json` 的只读 JSON 解析：`entities 86`、`views 2`、`fk_edges 92` |
| identityHash | `6633b7c2fe223041c4a2927203853f4f` | `69.json` `database.identityHash` |

说明：以上是**导出 schema 的静态事实**，不是迁移执行证据；1→69/68→69 的运行时结果本轮未跑（见 §7）。

### 2.2 模型请求 schema

| 项 | 当前值 | 证据 |
| --- | --- | --- |
| 当前请求 schema | **18** | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546`：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`；`:547`：`CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION` |
| 本批是否修改 | **否** | 计划 `:6`“请求 schema 18，本批不改”；计划 `:26` 明确不做“6B-3 新模型任务类型/请求 schema 19” |

## 3. 备份写侧基线（v3 尚未实现）

### 3.1 `SmbkManifest` 字段

`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt:33-75` 当前字段只有：

| 字段 | 类型 / 默认 | 行号 |
| --- | --- | --- |
| `formatVersion` | `Int = FORMAT_VERSION` | `:35` |
| `databaseSchemaVersion` | `Int` | `:37` |
| `createdAt`（`createdAtEpochMillis`） | `Long` | `:39` |
| `problemCount` | `Int` | `:41` |
| `assetCount` | `Int` | `:43` |
| `hashAlgorithm` | `String = "SHA-256"` | `:45` |
| `appVersionName` | `String? = null` | `:48` |
| `appVersionCode` | `Long? = null` | `:50` |
| `contentVersion` | `String? = null` | `:56` |
| `hasSettings` | `Boolean = false` | `:59` |
| `settingsDigest` | `String? = null` | `:62` |
| `files` | `List<SmbkFileRecord>` | `:64` |
| `referencedAssets` | `List<String> = emptyList()` | `:74` |

- `FORMAT_VERSION = 2`（`:78`），`MIN_READABLE_FORMAT_VERSION = 1`（`:81`）。
- **没有** `selection`、`closureDigest`、`rowCounts`、`assetPolicy`、`trimmedTableCount` 字段；v3 的清单结构尚未进入代码。

### 3.2 `AndroidBackupRepository.create` 入口

- 签名：`override suspend fun create(destination: OutputStream, options: BackupOptions): BackupReceipt`，`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:180-183`。
- 流程：先读设置（`:186`）→ 独立连接 `VACUUM INTO` 快照，失败才回退到 Room 连接 + `writeGate`（`:197-214`）→ 在快照上取 `problemCount` 与 `readReferencedCanonicalAssets()`（`:217-218`）→ `resolveReferencedAssetFiles`（`:219`）→ `SmbkArchiveCodec.create`（`:220-236`）。
- 资产是否入包只由 `options.includeAssets` 决定：`:223` `assets = if (options.includeAssets) assetFiles else emptyList()`。
- 没有任何 `selection`、mask、裁剪器或 `rowCounts` 参数；成包直接使用快照库（`:222` `database = snapshotFile`）。

### 3.3 域层现状

- `BackupOptions` 只有 `includeAssets: Boolean = true`，并 `require(includeAssets)`（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt:27-33`）。
- `BackupReceipt`（`:35-49`）、`RestoreReceipt`（`:51-68`）、`BackupManifestSummary`（`:116-130`）均无 `selection` / `selectionMask` / `trimmedTableCount` 字段。
- 源码中不存在 `BackupSelection` / `BackupSnapshotTrimmer` / `SmbkSelectionPlan`：
  `grep -rn "BackupSelection\|BackupSnapshotTrimmer\|SmbkSelectionPlan" --include=*.kt core app feature | grep -v "/build/" | wc -l` → **0**。

### 3.4 恢复侧锚点（供 T4/T5 接线参考，不代表行为已验证）

- `restore` 入口：`AndroidBackupRepository.kt:363`；`doRestore`：`:385`。
- `SmbkArchiveCodec.finalizeValidation`：`SmbkArchiveCodec.kt:877`；格式版本闸门在 `:893-896`（大于 `FORMAT_VERSION` 拒绝，小于 `MIN_READABLE_FORMAT_VERSION` 拒绝）。
- 结论：写侧、读侧、域类型都停留在 v2；v3 的 selection/裁剪/校验均**未实现**，与计划 `:7` 一致。

## 4. 6C-2 设计件 §9 与本批裁定

### 4.1 §9 机器清单（schema 69 重算）

| 项 | 值 | 证据 |
| --- | --- | --- |
| schema 69 事实 | 86 表 / 2 视图 / 92 FK；identityHash `6633b7c2fe223041c4a2927203853f4f` | 设计件 `:1054-1057`；`69.json` 解析同值 |
| 类别表数 | ISSUE_BOOK 30、MASTERY 35、SESSIONS 6、IMAGES 1、ALWAYS 7、DERIVED 2、SPLIT 5 | 设计件 `:1085-1093` |
| 类别闭包 | 5 条边：`ISSUE_BOOK⇒IMAGES`、`SESSIONS⇒ISSUE_BOOK`、`SESSIONS⇒IMAGES`、`MASTERY⇒ISSUE_BOOK`、`MASTERY⇒SESSIONS` | 设计件 `:1124-1130`；机器块 `expectedClosure` 5 项 |
| 软引用 | 20 条（含 `no-new-dangling` 口径） | 设计件 `:1027-1031`、`:1154-1164`；机器块 `softReferences` 20 项 |
| 裁剪顺序 | 78 项；`figure_generation_attempt`(48) → `figure_generation_operation`(49) → `canonical_source_asset`(50) | 设计件 `:1348-1361`；机器块 `trimOrder` 78 项 |
| 共享 operation/attempt 行规则 | 按 `source_kind` 取种子 + 产物引用回拉；未知 `source_kind` 在真子集 fail-closed | 设计件 `:1032-1034`、`:1095-1117` |
| `closureVersion` | **2** | 设计件 `:1389-1396`；机器块 `:1589` `"closureVersion": 2` |
| `selection.schemaVersion` | 2 | 设计件 `:1398-1414` |
| 机器块标记 | `<!-- 6c2-plan-v2:begin -->` / `end` | 设计件 `:1586`（begin）、`:2193`（end） |
| 机器块 identityHash | `6633b7c2fe223041c4a2927203853f4f` | 设计件 `:1591` |

机器块解析结果（只读 Python 解析 `6c2-plan-v2` JSON）：`categories` 计数 30/35/6/1；`alwaysTables` 7；`derivedTables` 2（`knowledge_search_feature`、`knowledge_search_index_state`）；`splitTables` 5；`trimOrder` 78；`softReferences` 20；`expectedClosure` 5；`maskPolicy.closedMasks = [15,13,9]`、`openMasks` 为 8/0 且 `state=OPEN`（`:2148-2157`）；`derivedPolicy.decision = OPEN`（`:2171-2180`）。

### 4.2 digest 算法与 golden

`closureDigest` v2（设计件 `:1416-1424`；机器块 `digest.algorithm`）：

```
SHA-256( "smbk-selection-v2\n"
       + "closureVersion=2\n"
       + "categories=" + categories.sorted().joinToString(",") + "\n"
       + "assetPolicy=" + assetPolicy + "\n"
       + rowCounts.keys.sorted().joinToString("") { "$it=${rowCounts[it]}\n" } )
```

空库、全选（mask=15）、`assetPolicy=INCLUDED` 的 golden（设计件 `:1426-1431`，机器块 `:2184-2190`）：

```
d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6
```

`rowCounts` 必须覆盖全部 86 表（值为 0 也写）；split 表计数是行级筛选后的实际行数（设计件 `:1437-1438`）。

### 4.3 三项裁定：设计件仍标 OPEN，计划记录用户已确认

设计件 §9.9 仍把三项标为 `OPEN`，并写“本轨不代签”（`:1531-1544`）：

| # | 设计件状态 | 本批计划记录的裁定 |
| --- | --- | --- |
| ① 仅图片（mask=8） | `openMasks` 中 `state=OPEN` | **不提供**（计划 `:10`） |
| ② 空选择（mask=0） | `openMasks` 中 `state=OPEN` | **禁止**（计划 `:11`） |
| ③ 派生索引恒裁 | `derivedPolicy.decision = OPEN` | **恒裁**，恢复完成后按科后台重建（计划 `:12`，设计 §6.1-③ 的 a+c） |

因此本批实现的权威口径是**计划 §0/§2**，不是设计件机器块里的 `OPEN`：
- 类别位：`ISSUE_BOOK=1`、`MASTERY=2`、`SESSIONS=4`、`IMAGES=8`；`FULL_MASK=15`（计划 `:34`）。
- 允许的 UI/仓储组合只有 **9、13、15**；0 与 8 必须被 UI 禁止、被仓储拒绝；1–7、10–12、14 按闭包拒绝；不静默补齐（计划 `:35-37`）。
- `BackupSelection` 可为 UI 渲染构造不自洽集合，但 `create()` 只接受 `closed()` 且 mask ∈ {9,13,15}（计划 `:37`）。
- manifest：`formatVersion=3`、`selection.schemaVersion=2`、`closureVersion=2`、`assetPolicy=INCLUDED`；v1/v2 缺 `selection` 读作全量；`MIN_READABLE_FORMAT_VERSION=1` 不变（计划 `:38`）。
- 全选（mask=15）走 2a：**跳过裁剪器**，与 v2 成包等价（仅 manifest 新增 selection/formatVersion）；子集（9/13）走裁剪器（计划 `:41`）。
- 派生两张表恒裁；恢复完成后（重启/DB 重开）按科 `KnowledgeSearchIndexBuilder.rebuildSubject` 重建；重建失败不得回滚已成功的数据库交换，但必须留下待重建事实（计划 `:42`）。`rebuildSubject` 当前存在于 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt:97`。
- 裁剪器只碰快照文件；`PRAGMA foreign_keys=ON` 必须在 `BEGIN` 前；单事务删除；FTS `rebuild`；有删除才 `VACUUM`；不动 schema/`room_master_table`/`sqlite_sequence`（计划 `:43`）。

**文档债（必须在 T1/T2 开工时同步）**：设计件 §9.9 尚未回写这三项裁定，仍会向后来者显示 `OPEN`。本批代码与测试必须按计划 §0/§2 实现；记录员收尾时应把设计件 §9.9 与机器块的 `maskPolicy`/`derivedPolicy` 更新为已裁定状态，避免“文档说未定、代码已定”的漂移。

## 5. 他线热区与共享脏文件

### 5.1 计划点名的共享文件：实际状态

| 文件 | 当前状态 | 指纹 / 说明 |
| --- | --- | --- |
| `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt` | **`M`** | sha256 前缀 `cb0927b4f34c`；mtime `2026-10-22 20:56:34 +0800`；`git diff --stat` 为 4 insertions / 3 deletions；hunk 仅 `@@ -2,0 +3,2 @@`（imports）、`@@ -71 +72,0 @@`、`@@ -91 +92 @@`（`SecondaryHeader` 高度 `height`→`heightIn`）、`@@ -811 +812 @@`（`StorageScreen` 颜色 `ErrorWarm`→`ErrorText`）。本轮三次取样（约 21:12、21:15、21:17）哈希一致，未观察到该文件在变。 |
| `core/data/.../backup/AndroidBackupRepository.kt` | **干净** | sha256 前缀 `b27ad1a234cb`；`git status --porcelain` 无输出 |
| `core/data/.../backup/SmbkArchiveCodec.kt` | **干净** | sha256 前缀 `423284f85e61` |
| `core/database/.../StudyDatabasePort.kt` | **干净** | sha256 前缀 `2014a7199adf`；接口声明 `:102` |
| `core/database/.../RoomStudyDatabase.kt` | **干净** | sha256 前缀 `917674aa1747`；类声明 `:41` |

口径差异：计划 `:103` 把上述五个文件统称“共享脏文件”，但实测只有 `SecondaryScreens.kt` 是 `M`；其余四个在 HEAD 上是干净的。T4/T5 将按分工把它们改脏，**干净不等于可随意改**——仍需按计划串行（T4 只做成包侧、T5 只做恢复侧，计划 `:109`）。

### 5.2 他线热区（本批禁改 / 只读）

| 热区 | 状态（本快照） | 依据 / 说明 |
| --- | --- | --- |
| `feature/tutor/.../components/TutorSessionPanel.kt` | `M` | 计划禁改；`feature/tutor` 整体 19 M / 13 ?? |
| `BatchImport*` / `SplitImport*` | `M` | `BatchImportRoute.kt`、`RoomBatchImportStore.kt`、`BatchImportDao.kt`、`SplitImportReviewRoute.kt` 等均 `M`；计划禁改 |
| `KnowledgeReviewSessionViewModel.kt` 及 Factory/Test | `M` | `feature/review` 8 M；计划禁改 |
| `ReviewReminderCoordinator.kt`、`LearningMasteryScreen.kt` | `M` | 计划禁改 |
| `core/ui/AttachedImageCard.kt`、`BoundedLocalImage.kt` | `M` | 计划禁改；`core/ui` 7 M / 6 ?? |
| `OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt` | **干净** | 计划仍列为禁改；不得因干净而修改 |
| `SmartMistakeBookRoot.kt` | `M` | 集成轨独占，本轨不得改；`SmartMistakeBookApplication.kt` 当前干净 |
| `docs/current/*` | 7 M | 他线文档线在飞，计划禁改 |
| `tools/kb_*`、`knowledge-production/*` | 5 M / 12 ??、1 M / 11 ?? | KB/dense 与知识生产线在飞，计划禁改 |

### 5.3 热区计数（21:15:52 快照）

| 模块 / 区域 | modified | untracked |
| --- | ---: | ---: |
| `feature/tutor` | 19 | 13 |
| `feature/library` | 10 | 4 |
| `feature/review` | 8 | 0 |
| `feature/capture` | 7 | 0 |
| `core/data` | 10 | 2 |
| `core/database` | 9 | 1 |
| `core/ui` | 7 | 6 |
| `core/export` | 5 | 0 |
| `core/domain` | 4 | 0 |
| `docs/current` | 7 | 0 |
| `tools/kb_*` | 5 | 12 |
| `knowledge-production` | 1 | 11 |

**开工纪律**：共享树仍在被写入（§1.2 的 539→550）。T6 在改 `SecondaryScreens.kt` 前必须重新取哈希；若与 `cb0927b4f34c` 不同，先停并报告，不做“合并猜测”。

## 6. 本批轨道入口与明确不做

### 6.1 轨道入口（计划 §3、§4）

| 轨 | 交付入口 | 前置 |
| --- | --- | --- |
| T1 域选择/闭包 | 新 `core/domain/.../BackupSelection.kt`；改 `BackupRepository.kt`（`BackupOptions.selection`、退役 `includeAssets`、回执/manifest summary 扩展）；测试 `BackupSelectionTest.kt` | 无 |
| T2 schema 69 机器清单 | 新 `core/data/.../backup/SmbkSelectionPlan.kt`（86 表/2 视图、78 项顺序、split 谓词、20 条软引用、资产根、`CLOSURE_VERSION=2`、digest、`rowCounts` 键集合）；测试 `SmbkSelectionPlanTest.kt` | 设计件 §9.11 为唯一数据源 |
| T3 快照裁剪器 | 新 `core/data/.../backup/BackupSnapshotTrimmer.kt`；测试 `BackupSnapshotTrimmerInstrumentedTest.kt` | T2 后 |
| T4 manifest v3 + 成包接线 | 改 `SmbkArchiveCodec.kt`（`formatVersion=3`、`selection` 记录、v1/v2 兼容读）；改 `AndroidBackupRepository.create`；测试 codec + `BackupSelectionCreateInstrumentedTest.kt` | T1/T2/T3 后；与 T5 串行，只做成包侧 |
| T5 恢复校验 + 派生索引重建 | 改 `SmbkArchiveCodec.finalizeValidation` / `AndroidBackupRepository.doRestore`；新窄端口 + `StudyDatabasePort`/`RoomStudyDatabase` 实现；`RestoreJournal`/`BackupRestoreStartupRecovery` 待重建标记；测试 `BackupSelectionRestoreInstrumentedTest.kt` | T4 后；只做恢复侧 |
| T6 设置页勾选 UI | 改 `SecondaryScreens.kt`（增量、保留他线 hunk）；测试 `BackupSelectionUiInstrumentedTest.kt` | T1 后；开工前复核该文件哈希 |
| T8 KD-55 | 改 `core/export/.../DocxImageSource.kt`、`DocxMistakeRenderer.kt`；测试 `DocxMistakeRendererTest.kt` | 无 |
| I1 集成 | 改 `SmartMistakeBookApplication.kt` 注入搜索重建端口；Root 仅在编译/路由确需时最小改动 | T5 后 |
| D1 设备 | `emulator-5554`；`assembleDebugAndroidTest` + `adb install -r` + `am instrument`；禁 `connectedAndroidTest`；迁移、子集/恢复、重建、非法包、全选回归、365MB、UI | 各实现轨后 |
| C1 收尾 | 11 模块单测门 + 强门 + 设备；独立核对/对抗复核；记录/KD/台账；分块提交（≤6 路径）；自动 push | 全部后 |

阶段顺序（计划 `:92`）：P0 基线 → P1 并行 T1/T2/T8 → P2 并行 T3/T6 → P3 T4 → P4 T5 → P5 I1 → P6 单测门 → P7 强门 → P8 设备 → P9 独立核对/对抗复核 → P10 修复环 → P11 记录员 → 主会话提交/推送。

### 6.2 明确不做（计划 `:24-30`）

- PRD-09 真实生图质量、4E WG-02/03/09/10、JS/QuickJS、4E.2/3。
- 6B-3 新模型任务类型 / 请求 schema 19。
- 5B/5b/5c、UI-00~11 全量改版、PRD-05 真实数据启用（仍等 3D 完整门）。
- 6C-2 的「仅图片」「空选择」「无图子集」「合并恢复」；本批按裁定不提供。
- KD-61 关闭（只在备份/恢复路径做不扩大旁路的静态复核，不宣称关闭）。
- 真实 Provider 调用；所有模型相关验证仍用 fake/夹具。

T0 本轨额外不做：Gradle、设备安装/启动、`connectedAndroidTest`、真实 Provider 调用、任何代码或测试改动、git 暂存/提交/推送。

### 6.3 门禁口径（计划 §5 `:96-99`）

- 单测：11 模块 `--continue`（`core:model/domain/data/database/export/ui`、`feature:review/library/capture/tutor`、`app:testLocalFirstDebugUnitTest`）。
- 强门：六模块 `compileDebugAndroidTestKotlin` + `:app:assembleLocalFirstDebug`。
- 设备：迁移矩阵、备份子集/恢复/搜索重建、UI、365MB；不使用 `connectedAndroidTest`。
- 失败归因本批 vs 他线；他线导致的门红照录，不改他线文件，不声称全绿。

### 6.4 本批风险登记（计划 §7 `:109-115`）

- **T4/T5 同改 `AndroidBackupRepository.kt`**：必须串行；T4 只做成包侧，T5 只做恢复侧。
- **`SecondaryScreens.kt` 他线在飞**：只做备份区增量；开工时若仍在变，先停/报告（本基线已给出当前哈希）。
- **裁剪器误碰实时库**：只允许按路径打开快照；Room 写口不得进入 trimming 段。
- **VACUUM 空间**：子集包必须预检“快照 + 裁剪后 + 余量”；不足时报明确失败，不产半包。
- **派生索引重建失败**：不回滚已交换的数据库；保留待重建标记，回执如实说明。
- **manifest 兼容**：v1/v2 缺 `selection` 必须读作全量；v3 未知 `closureVersion`/`schemaVersion` 在交换前拒绝。
- **KD-55**：只修 DOCX 读入的复核，不扩大导出语义；真实 Provider 仍 UNVERIFIED。

## 7. 未验证与登记边界

1. **编译/测试 UNVERIFIED**：铁律禁止 Gradle；本基线没有编译、单测、androidTest 编译或强门结果。文中“存在/计数为 N/哈希为 X”均为静态命令输出，不是“通过”。
2. **设备/迁移 UNVERIFIED**：未执行 `adb devices`、未安装 App、未跑 1→69/68→69、未跑备份往返或 365MB 性能；设计件 §9.8 的 C1–C8 也全部标注“设计用例，未执行”。
3. **设计件与计划的口径漂移**：设计件 §9.9 与机器块仍把 mask=8/0、派生策略标为 `OPEN`；计划 `:9-12` 已记录用户裁定。本批以计划为准；收尾必须回写设计件，否则后续读者会得到互相矛盾的授权信号。
4. **工作树计数漂移**：本文件登记窗口内从 539 变到 550；提交/收口必须重新取数。`SecondaryScreens.kt` 的哈希只在三次取样间稳定，不构成长期保证。
5. **“干净”不代表可改**：`AndroidBackupRepository.kt`、`SmbkArchiveCodec.kt`、`StudyDatabasePort.kt`、`RoomStudyDatabase.kt`、`OpenAiModelProtocol.kt`、`RoomModelTaskRepository.kt` 当前无 diff，但仍受计划禁改/串行约束。
6. **schema 计数来源**：86/2/92 来自 `69.json` 的静态解析，未在运行时打开 Room 数据库复算；identityHash 同源。
7. **真实 Provider / PRD-09 质量**：无凭据、无真实调用，整块 UNVERIFIED。
8. **敏感信息**：本文件不包含任何密钥、令牌或凭据；本轮未读取任何凭据文件。

## 附录：本基线使用的只读命令（可复跑）

```bash
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git status -sb
git branch -vv
git log -1 --format='%H %ad %s' --date=iso-strict
git status --porcelain=v1 | wc -l
git status --porcelain=v1 | awk '{print $1}' | sort | uniq -c
python -c "import json;d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json',encoding='utf-8'));db=d['database'];print(db['version'],db['identityHash'],len(db['entities']),len(db.get('views',[])),sum(len(e.get('foreignKeys',[])) for e in db['entities']))"
ls -l core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/69.json
grep -n "STUDY_DATABASE_VERSION\|@Database\|version = STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION\|CURRENT_SCHEMA_VERSION" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
grep -n "FORMAT_VERSION\|MIN_READABLE_FORMAT_VERSION\|data class SmbkManifest" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt
grep -n "override suspend fun create\|readReferencedCanonicalAssets\|includeAssets" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt
grep -n "data class BackupOptions\|data class BackupReceipt\|data class RestoreReceipt\|data class BackupManifestSummary" core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt
grep -rn "BackupSelection\|BackupSnapshotTrimmer\|SmbkSelectionPlan" --include=*.kt core app feature | grep -v "/build/" | wc -l
git status --porcelain=v1 -- app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
git diff --stat -- app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
sha256sum app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
grep -n '"closureVersion"\|"identityHash"\|"openMasks"\|"derivedPolicy"\|"goldenEmptyFull"\|"trimOrder"\|"softReferences"' docs/research/2026-10-17-6c2-content-selection-design.md
```
