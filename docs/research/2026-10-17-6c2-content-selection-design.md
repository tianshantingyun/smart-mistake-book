# 6C-2 内容勾选（行级子集）设计件

- 日期：2026-10-17 ｜ 轨道：T7（批次 13）｜ 状态：**设计件，未改任何产品代码**
- 上游：`docs/research/2026-10-11-new-stages-spec.md:127-142`（阶段 6C「备份与恢复」需求/方向/验收门）、
  `.zcode/plans/plan-2026-10-17-batch-13-6b-4c-kd51-kd44.md:44-45`（本轨任务书）。
- 落地形态参照（6C-1，已实现）：`SmbkArchiveCodec.kt`（manifest v2 + 校验/解包）、
  `SmbkSettingsDocument.kt`（`settings.json` v1 与版本/值域闸门）、
  `AndroidBackupRepository.kt`（快照 → 成包 → 恢复的阶段化流程）。
- 事实纪律：本文每条结论都带 `文件:行` 或「机器导出 + 可复跑命令」。**未在 Android 设备上跑过**
  的部分一律标注；本机的 Python/SQLite 探针（3.13.14 / SQLite 3.50.4）只是机制验证，
  Android 内置 SQLite 版本更低，凡受版本影响的点都写进 §6 风险并给了设备复核项。

## 0. 结论摘要（TL;DR）

1. **四个勾选框在 FK 图上的合法组合只有 5 组**：`{}`、`{图片}`、`{错题本+图片}`、
   `{错题本+会话+图片}`、`{全选}`。闭包不是产品口径的拍脑袋，而是从 schema v66 的 90 条外键边
   推导出来的（§1.2，可复跑校验）。
2. 行级子集 = **在既有的 `VACUUM INTO` 快照文件上按拓扑序删整表**（`model_task` 家族按
   `task_kind` 过滤），成包前跑 I1–I7 对拍不变式（§2.5）。裁剪**不动实时库**，也不新增第二套快照机制。
3. **裁剪后必须 VACUUM**：DELETE 只是把页标记为空闲，被裁内容仍留在文件里（已实测：
   同一条删掉的文本不 VACUUM 时仍可在文件字节里搜到）——不 VACUUM 的「不含会话」包会撒谎（§2.4）。
4. 恢复侧**不需要新的回滚机制**：所有新增校验都放在 `journal.writePhase(BACKUP_CURRENT)` 之前，
   失败落进既有的「尚未 swap，直接清临时件」分支；交换、代际回滚、启动恢复、journal 全部复用（§3）。
5. manifest v2 → **v3**：新增 `selection` 块（`categoryMask` / `categories` / `assetPolicy` /
   `closureVersion` / `closureDigest` / `rowCounts`）。v1/v2 旧包读作「全量」，行为逐字不变（§5）。
6. **未决项 8 条**（§6.1），其中 3 条需要人裁（仅图片组合是否提供、空选择是否允许、派生索引是否恒裁），
   另有 1 条（⑧ 裁剪+VACUUM 的耗时与空间）与风险 R7（旧 SQLite 上的 FTS rebuild 行为）只能设备实测定稿。

---

## 1. ① 类别级内容勾选与依赖闭包

### 1.1 四个类别的内容定义

勾选单位是**类别**（不是行）。每个类别 = 一组表的**全部行**；表与类别一一对应，84 张表全部归类
（schema v66 导出 `entities=84`，覆盖校验见 §E）：

| 类别 | 位 | 内容口径（用户看到的话） | 表数 |
| --- | --- | --- | --- |
| `ISSUE_BOOK` 错题本 | 1 | 题目/题面修订/练习单元/收录条目/知识点标签/关系/分类/去重台账、录入中草稿与整卷切分作业、题侧去重与治理台账、题侧模型的幂等台账、库搜索投影 | 30 |
| `MASTERY` 掌握库 | 2 | 学习证据账本（作答/揭示/改错/会话暴露）、投影快照与派生状态、复习计划/队列/会话、复习日志、学生模型预测、教学建议与聊天证据 | 35 |
| `SESSIONS` 会话 | 4 | 讲题会话、消息、消息附图引用、待处理请求、答案暴露与暴露结果 | 6 |
| `IMAGES` 图片 | 8 | `canonical_source_asset` 行 + `source-assets/` 字节 | 1 |
| `ALWAYS` 恒随包 | — | 知识层（节点/来源/出处绑定/关系/教学材料）与 `content_install_state` | 7 |
| `DERIVED` 恒裁 | — | `knowledge_search_feature` + `knowledge_search_index_state`（读时自愈可重建） | 2 |
| `SPLIT` 按 kind | — | `model_task` / `model_task_event` / `model_task_operation`（同一张表里按 `task_kind` 分属不同类别） | 3 |

**为什么知识层恒随包**：`knowledge_node` 等 7 张表是掌握库与绑定的坐标系，且本来就是随包内容
（`BundledKnowledgePackResources.load()` 从 APK classpath 解析，`BundledKnowledgePackResources.kt:82-98`）。
裁掉它们会让下次启动触发一次整包重装（体积 30M+ 字符 JSON，`BundledKnowledgePackResources.kt:66-71`
记录了驻留与解析成本），收益只是几十 KB 的文本行——不划算。**注意**这条决定把「照片库里最大的一坨」
（`knowledge_search_feature`，全库 623,774 行/4,187 节点，见 plan T4 段 `:30`）交给 `DERIVED` 处理，
而不是留给用户勾。

### 1.2 依赖闭包（由 FK 图推导，不是手写）

规则一句话：**外键的子表被选中 ⇒ 其父表必须被选中**（否则删父留子，要么 FK 报错、要么留下悬空引用）。
把 schema v66 的 90 条外键边按「表→类别」折叠，去掉同类边与指向 `ALWAYS` 的边，剩下的就是闭包：

```
ISSUE_BOOK ⇒ IMAGES      （problem_draft → canonical_source_asset；
                           problem_draft_source_asset → canonical_source_asset；
                           problem_revision_source_asset → canonical_source_asset）
SESSIONS   ⇒ ISSUE_BOOK  （tutor_answer_exposure_outcome → practice_unit）
SESSIONS   ⇒ IMAGES      （tutor_message_source_asset → canonical_source_asset）
MASTERY    ⇒ ISSUE_BOOK  （assessment_item_snapshot / assessment_evidence_snapshot /
                           learner_problem_memory_state / review_queue_item /
                           tutor_answer_exposure_outcome → practice_unit；
                           practice_unit → problem_revision；error_book_entry → practice_unit）
MASTERY    ⇒ SESSIONS    （applied_tutor_answer_exposure_record → tutor_answer_exposure_outcome）
```

传递闭包 ⇒ **`MASTERY` 一勾就等于全选**（`MASTERY⇒ISSUE_BOOK⇒IMAGES` 且 `MASTERY⇒SESSIONS`）。
这不是设计缺陷，而是「学习证据 + 投影」本来就长在题与会话暴露事实之上：投影的
`checkpoint_sequence` 是整本账本（含会话暴露）的位置，删掉任一类事件都会让投影状态对不上账本
（排空器遇账本缺口直接抛 `LearningLedgerIntegrityException`，`StudyProjectionDrainer.kt:108-113`）。

三条用户要求逐条对账：
- 「勾学习证据须带依赖题修订/节点」→ `MASTERY ⇒ ISSUE_BOOK`（题修订/练习单元）＋ 知识层恒随包（节点）；
- 「勾图引用须带图」→ `ISSUE_BOOK ⇒ IMAGES` 与 `SESSIONS ⇒ IMAGES`；
- 「不得导出悬空子集」→ 硬 FK 由闭包保证（§2.5 的 I1）＋ 软引用由 §1.3 的清单保证（I3）。

### 1.3 软引用（无外键的引用列）——必须手工维护的第二张依赖表

FK 抓不到这些列，但它们是真实的引用（删了目标就会留下悬空值）。逐条列在附录 A 的
`softReferences`（38 条），按处理方式分三类：

| 类 | 含义 | 处理 | 例子 |
| --- | --- | --- | --- |
| `same-category` | 引用行与被引用行同属一个类别，类别整裁时自然一起消失 | 无需额外规则 | `library_search_content.problem_revision_id`、`clean_redraw_task.revision_id` |
| `cross-category` | 引用行所属类别 ⇒ 被引用行所属类别（**参与闭包**） | 并进 §1.2 判据；闭合校验 SQL 见附录 C | `tutor_conversation.anchor_practice_unit_id → practice_unit`、`review_log.card_id → practice_unit` |
| `always` | 指向恒随包表（知识层） | 无需规则（目标恒在） | `learner_chat_evidence.knowledge_node_id` |

`model_task` 家族是特殊的行级分裂（一张表两种归属），按各 kind 输入的 `subjectId` 定宿主
（**一手证据**，见附录 F）：`CAPTURE_ASSESS`/`CAPTURE_PARSE` = draftId → 错题本；
`PROBLEM_CLASSIFY`/`PROBLEM_RELATE` = problemRevisionId → 错题本；
`IMAGE_PIPELINE_CLASSIFY` = `subjectIdOverride ?: sourceAssetId` → 资产域（生产者是采集/重绘）；
`TUTOR_LOBBY` = conversationId、`TUTOR_PLAN`/`TUTOR_RESPOND` = `tutor-conv:captured:<会话>`、
`LEARNING_SUMMARIZE` = sessionId → 会话；`KNOWLEDGE_QUIZ` = 知识点域 → 掌握库。
`TUTOR_EVALUATE` / `REVIEW_RERANK` 目前**只有枚举与出网映射、没有输入类实现**
（`ModelEgress.kt:100-101`），当前无行；分派表按会话/掌握库预置，实施时复核。

### 1.4 资产闭包（AUD-05 口径的直接延续）

「被引用」的定义在仓库里**只有一处**：`PendingCaptureDao.findUnreferencedCanonicalAssets`
的四表 `NOT EXISTS`（题面修订 / 草稿页束 / 草稿主图 / 学生消息附图，
`PendingCaptureDao.kt:42-57`）；引用集合是它的补集（`RoomStudyDatabase.kt:840-847`），
恢复前的闭合校验按它反查（`AndroidBackupRepository.kt:862-888`）。6C-2 不改这个口径，只要求：

> **只要四张引用表里还有行指向资产行，资产行与其文件就必须一起进包**。

这条正是 `ISSUE_BOOK ⇒ IMAGES` / `SESSIONS ⇒ IMAGES` 的产品含义。三条独立证据说明「无图」不是
无害省流，而是内容损坏：
1. 详情页的题图来自 `problem_revision_source_asset ⋈ canonical_source_asset`
   （`MistakeDetailDao.kt:294-341`），行在而文件不在 = 详情页拿不到图；
2. 行去掉而修订留着 = 原图与重绘干净图（role=`QUESTION_SOURCE` / `CLEAN_IMAGE`，
   `ProblemDraftTransactionDao.kt:793`、`:937`）与题的关联被抹掉，导出/重绘链失去输入；
3. 现有成包路径**本来就 fail-closed**：`SmbkArchiveCodec.create` 要求
   `referencedAssets ⊆ packagedPaths`（`SmbkArchiveCodec.kt:191-199`），且
   `BackupOptions` 的 `require(includeAssets)`（`BackupRepository.kt:26-32`）已经把「只备数据库」这条路堵死。

### 1.5 合法组合表与非法组合的拒绝口径

| mask | 组合 | 人话 | 备注 |
| --- | --- | --- | --- |
| 15 | 错题本+掌握库+会话+图片 | 全部（v2 等价） | 默认 |
| 13 | 错题本+会话+图片 | 不带学习记录 | **最实用的一档**（省掉掌握库的投影与复习计划） |
| 9 | 错题本+图片 | 只要题 | 不带讲题会话与学习记录 |
| 8 | 图片 | 只要题图 | **§6.1-① 建议 UI 不提供**（见下） |
| 0 | 空 | 只要知识层+设置 | **§6.1-② 建议 UI 禁止**（产物是「重置包」） |

非法组合（例如 `{错题本}` 无图、`{会话}` 无题）**一律拒绝**，不静默补齐：静默补齐 = 用户要求
少带走数据、我们多带了（对「不想把会话带出设备」的场景是隐私事故）；静默缩水更糟。拒绝必须是
两层的：
- **UI 层**：勾选传播（勾 `错题本` 自动点亮并锁住 `图片`；取消 `图片` 联动取消依赖它的类别，
  并把原因写在勾选项下方）。传播后的组合永远合法，用户看到的是**自己的选择被展开**；
- **仓储层**：`BackupSelection` 构造期校验闭包，非法即抛（fail-closed），
  **不作为**「自动补齐后继续」（`create()` 不接受一个不自洽的选择）。

`{图片}` 单独这一组要被挡掉的真实理由（不是洁癖）：包内资产行**没有任何引用者**，
恢复后启动时的孤儿回收会按 30 分钟宽限把它们全删掉——`AndroidBackupRepository.cleanupOrphanAssets`
（`:105-114`）经 `OrphanAssetGc` 在**应用启动**时被 enqueue（`SmartMistakeBookApplication.kt:422`，
另见 `SecondaryScreens.kt:644`），宽限期常量 `ORPHAN_ASSET_GRACE_MILLIS`（`:74-80`）按
`created_at_epoch_millis` 过滤，而恢复进来的资产行带着旧设备的创建时间（必然早于 30 分钟）。
所以这一档的产物是「恢复完就蒸发」。

### 1.6 API 草案（域层）

```kotlin
// core/domain/.../BackupRepository.kt
enum class BackupContentCategory(val bit: Int) {
    ISSUE_BOOK(1), MASTERY(2), SESSIONS(4), IMAGES(8);

    companion object { val ALL = setOf(...); const val FULL_MASK = 15 }
}

/** 依赖闭包：子 ⇒ 父（附录 A 的 expectedClosure 是这一张表的机器可读副本）。 */
private val CLOSURE: Map<BackupContentCategory, Set<BackupContentCategory>> = mapOf(
    BackupContentCategory.ISSUE_BOOK to setOf(BackupContentCategory.IMAGES),
    BackupContentCategory.SESSIONS to setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.IMAGES),
    BackupContentCategory.MASTERY to setOf(BackupContentCategory.ISSUE_BOOK, BackupContentCategory.SESSIONS),
)

class BackupSelection private constructor(val categories: Set<BackupContentCategory>) {
    val mask: Int get() = categories.sumOf { it.bit }
    val isFull: Boolean get() = categories.size == BackupContentCategory.entries.size

    /** 缺哪条依赖；空表 = 自洽。UI 用它解释为什么某项被点亮。 */
    fun missingDependencies(): Map<BackupContentCategory, Set<BackupContentCategory>> = ...

    /** UI 传播用：把勾选闭合成自洽集合（不改变用户显式勾选的那几项）。 */
    fun closed(): BackupSelection = ...

    init { /* 不在这里 require：构造允许暂时不自洽，create() 前必须 closed() */ }
    companion object {
        fun of(categories: Set<BackupContentCategory>): BackupSelection
        val FULL: BackupSelection
    }
}

data class BackupOptions(
    val selection: BackupSelection = BackupSelection.FULL,
    // includeAssets 退役：它的语义已由 IMAGES 类别承担，
    // 且原 init 的 require(includeAssets) 与闭包规则重复（BackupRepository.kt:26-32）。
)
```

取舍：**允许构造不自洽的选择、由 `create()` 拒绝**——UI 需要表达「用户此刻勾了什么、还差什么」，
构造期直接抛会让 UI 无法渲染中间态；但 `create()` 必须先要 `closed()` 的实例
（用 `require(selection.isClosed())` 或让 `create` 只接受 `BackupSelection.Closed` 类型）。

---

## 2. ② 整库快照形态下的行级子集实现路径

### 2.1 现状流程（6C-1，逐行）

`AndroidBackupRepository.create`（`:129-170`）今天是这样走的：

1. `readSettingsForBackup()` —— 设置先读，读失败整包失败（`:179-196`）；
2. `database.snapshotForBackup(dbFile, snapshotFile)` —— 一致性快照，优先 `VACUUM INTO`
   （SQLite ≥ 3.27），否则 TRUNCATE checkpoint + 只读字节拷贝
   （`RoomBackupSupportStore.kt:23-73`）；**归档永远从快照文件产生，不碰实时库**；
3. `withSnapshotDatabase` —— 把快照复制进 databases 目录、按名用 Room 打开（因为 Room 只能按库名打开）、
   读完删掉连同 `-wal/-shm`（`:234-254`）；题数与引用集合**都取自这份快照**
   （`problemCount` / `readReferencedCanonicalAssets`，`:146-147`）；
4. `resolveReferencedAssetFiles` —— 逐条按 vault 判据（存在+字节数+SHA-256）核对，
   缺图/被改 ⇒ 成包失败（`:264-276`）；
5. `SmbkArchiveCodec.create` —— 写 manifest/checksums/database.sqlite/settings.json/assets/*
   （`SmbkArchiveCodec.kt:174-264`），并在写之前 `require` 引用资产闭合（`:191-199`）。

6C-2 的插入点很清楚：**第 2 步与第 3 步之间**（快照已经是独立文件、还没有任何读口在读它）。

### 2.2 目标流程（v3）

```
1  读设置（不变；失败即失败）
2  database.snapshotForBackup(...)                    ← 不变（VACUUM INTO 快照）
2a 若本次**没有任何删除**（全选且保留派生索引，见 §6.1-③ 的 b 档）→ 跳过 2b–2d（与 v2 完全等价）
2b BackupSnapshotTrimmer.trim(snapshotFile, selection) ← 新增：按 §2.3 顺序单事务删表
2c 对拍校验（附录 D 的 I1–I6）                           ← 新增：不过就不成包
2d VACUUM（快照文件）                                   ← 新增：见 §2.4 的泄露证据
3  withSnapshotDatabase（不变；题数/引用集合此时取自**裁剪后**的快照）
4  resolveReferencedAssetFiles（不变；「引用集合」已随裁剪变小，天然自洽）
5  SmbkArchiveCodec.create(..., selection = ...)        ← 只多传一个参数
finally  删除快照（不变）
```

实现清单（下一批的落点）：

| 件 | 位置 | 职责 |
| --- | --- | --- |
| `SmbkSelectionPlan`（新，object） | `core/data/.../backup/` | 类别→表、裁剪顺序、kind 分派、软引用校验 SQL：附录 A 的**唯一机器可读副本** |
| `BackupSnapshotTrimmer`（新） | 同目录 | 打开快照文件（`SQLiteDatabase.openDatabase(path, null, OPEN_READWRITE)`，按路径打开的先例：`readSchemaVersion` `AndroidBackupRepository.kt:746-754`）→ 事务内删表 → 对拍 → VACUUM → `TrimReport` |
| `BackupSelection` / `BackupContentCategory`（新） | `core/domain/.../BackupRepository.kt` | 闭包校验、mask、UI 传播（§1.6） |
| `SmbkManifest`（改） | `SmbkArchiveCodec.kt:32-83` | v3 的 `selection` 块（§5） |
| `AndroidBackupRepository.create`（改） | `:129-170` | 插 2a–2d 与 receipt 字段 |
| rest 侧三处（改） | `:327-414` | §3.2 |

### 2.3 逐表裁剪顺序（拓扑序，子表先于父表）

顺序的唯一硬约束来自 FK：**删父之前必须先删完所有引用它的子行**。因此顺序不是按类别分组的，
而是整图的一个拓扑序。**字面顺序只在附录 A / 附录 B**；下表只说每个段落存在的理由
（段内互不引用，段间是依赖关系）：

| 段 | 表（代表） | 段间约束 |
| --- | --- | --- |
| A 最深子表 | `tutor_message_source_asset`、`split_import_question`、`review_session_advance_receipt`、`problem_revision_source_asset`、`problem_draft_commit_receipt`、`assessment_event`、`applied_*`… | 这些是纯子节点（同时挂在多张父表上），必须最早删；它们串起了会话链、题侧链与账本链 |
| B 题侧中层 | `problem_draft_revision`/`problem_draft_edit_snapshot` → `problem_draft`；`library_search_content`/`_outbox`；`knowledge_grounding_*` | 草稿链自上而下；搜索投影与题修订同生共死（§2.4-3） |
| C 题侧根 | `practice_unit` → `problem_revision` → `problem` | `practice_unit` 有 FK 指 `problem_revision`，所以**父表反而最后删**（这正是最容易写错的地方：直觉会想先删题） |
| D 会话根 | `agent_pending_request` → `tutor_conversation`（`tutor_message` 更早，在 A 段） | 会话行是链尾 |
| E 账本与投影 | `answer_reveal_outcome` **必须早于** `assessment_answer_reveal_event`（前者 FK 指后者，反向只有软引用 `outcome_id`）；`attempt_event` **必须早于** `assessment_evidence_snapshot`（它是后者的子表）；`learner_projection_snapshot` 晚于所有 `applied_*`（后者 FK CASCADE 指它） | 这四条是账本链里唯一「顺序不是直觉」的地方 |
| F 任务台账 | `model_task_event` → `model_task` → `model_task_operation` | 三级 FK 链，且三段共用同一个 kind 谓词（§2.4-7） |
| G 恒裁派生 | `knowledge_search_index_state` → `knowledge_search_feature` | 锚点先删：索引在而锚点不在会触发自愈重建（§1.1 的 `DERIVED` 口径），锚点在而索引不在则留下「假完整」判定 |
| H 资产 | `canonical_source_asset` | 所有引用表都已删完；`library_search_fts` **不在这张表里**（走收尾 rebuild，见下） |

顺序不是自由发挥：附录 E 的校验脚本会用 schema 的真实 FK 边逐条验证它是合法拓扑序，
并且**实测跑通**（本机 SQLite 干跑，5 组合法组合全部裁净、`foreign_key_check` 为空；
非法组合 `{错题本}` 被 FK 挡下）。

### 2.4 实现要点（每条都对应一个会咬人的失败）

1. **`PRAGMA foreign_keys = ON` 必须在 `BEGIN` 之前显式执行**。SQLite 每个连接的默认值是 OFF，
   而 Room 把它放在 `onOpen`（先例与口径：`RoomBackupSupportStore.clearAllData` 显式
   `foreign_keys = OFF`/`ON` 成对开关，`:107-135`；迁移期「外键是关的」的同一事实见
   `FavoriteAndManualDraftMigration_66_67.kt:18-20`、`KernelWave0Migration_53_54.kt:63-66`）。
   忘了这一条，RESTRICT 全部失效，裁剪会静默产出悬空库——闭合校验（I1）必须**独立于**它成立，
   这也是 I1 要单独跑的原因。
2. **单事务**：所有 DELETE 在一个事务里，配合 `VACUUM` 之外的一步一错即刻回滚。
   `TrimReport` 用 `changes()` 逐表累计删除行数，供 I6 对拍。
3. **FTS 外部内容表**：`library_search_fts` 是 `content=library_search_content` 的 FTS4
   虚拟表（`LibrarySearchFtsMigration.kt:48-60`），查询按 `docid = content_row_id` 联结
   （`LibraryFtsCountPathInstrumentedTest.kt:828-848`）。删除 `library_search_content` 行后，
   索引与内容表会漂开，收尾用一条语句对齐：
   `INSERT INTO library_search_fts(library_search_fts) VALUES('rebuild')`。
   本机实测（SQLite 3.50.4）：rebuild 在显式事务内可用、回滚后两张表都恢复、
   rebuild 后被删行的 `MATCH` 命中消失、孤儿 docid 归零。
   **为什么不用 DELETE FROM fts**：本机上 `DELETE FROM library_search_fts WHERE docid=?` 虽然被接受并生效，
   但外部内容表与索引的一致性按 SQLite FTS3/4 的既定用法应由 rebuild（或专用 `delete` 命令）维护
   ——**这条文档口径本轮未回读原文，标为待验证**；选用 rebuild 的理由是它不依赖那条口径、
   语义自足（从内容表整份重读），代价只是一次重分词。跨版本可用性见风险 R7（设备复核）。
4. **VACUUM 是必须的，不是优化**（实测证据）：DELETE 只释放页，内容仍留在文件里——
   种一行哨兵文本、删除、直接读文件字节，哨兵**仍在**；VACUUM 之后才消失
   （`PRAGMA secure_delete` 默认 0）。不 VACUUM 的话，「不含会话」的包会把会话正文原样带出，
   内容勾选就成了假承诺。
5. **交付单文件**：`VACUUM INTO` 与 byte-copy 两条路产出的快照都是无 WAL 的单文件；裁剪器直接按路径
   打开（不经 Room），改完显式 `PRAGMA wal_checkpoint(TRUNCATE)` 并删除可能出现的
   `-wal`/`-shm`，再 fsync，保证交给 codec 的是单文件、且没有未落盘的帧。
6. **不动 schema、不动 `room_master_table`、不动 `sqlite_sequence`**：前者会让 Room 打开时不认
   （身份哈希校验），后者的高水位必须保留（`library_search_content.content_row_id` 是 AUTOINCREMENT，
   清零高水位会让新行复用旧 rowid，与 FTS docid 撞车）。裁剪只删数据行。
7. **裁剪顺序对 SPLIT 表要「同谓词逐级」**：`model_task_event` 按
   `task_id IN (SELECT task_id FROM model_task WHERE task_kind IN …)` 删，
   `model_task` 与 `model_task_operation` 按 `task_kind IN …` 删，三处共用同一个 kind 集合，
   否则会留下「事件在、任务没了」的孤儿台账。

### 2.5 对拍计数与引用（成包前的 7 条不变式）

跑在**裁剪后的快照**上（此时无写者，参照物是同一份快照的裁剪前计数）。任何一条不过 ⇒ 成包失败，
绝不发半份包：

| # | 不变式 | 具体判定 |
| --- | --- | --- |
| I1 | 硬 FK 闭合 | `PRAGMA foreign_key_check` 返回 0 行 |
| I2 | 剪干净 | 未勾选类别的每张表 `SELECT COUNT(*) = 0`（`ALWAYS` 表除外） |
| I3 | 没误删 | 勾选类别的每张表 `COUNT(*)` 与裁剪前**逐表相等**（同事务里读两次） |
| I4 | 软引用闭合 | 附录 C 的每条检查 SQL 返回 0 行（含 model_task 家族按 kind 的宿主解析） |
| I5 | 资产闭合 | `readReferencedCanonicalAssets(裁剪后库)` 与将要打包的文件集合**双向相等**；`assetPolicy=INCLUDED` 时为 1:1，`ABSENT` 时两侧都必须为空 |
| I6 | 台账守恒 | 对每张表：`pre − post = deleted`（删除数取自 `changes()`），且总和 = I2/I3 的差 |
| I7（恢复侧也跑） | 页面级完整 | `PRAGMA integrity_check = ok`（成包侧早跑一次，把问题暴露在用户设备上而不是恢复时） |

### 2.6 失败处置与空间

- 裁剪任一步失败（SQL 异常/校验不过/VACUUM 失败）⇒ **整个成包失败**，`finally` 删快照，
  实时库与资产目录**一个字节都没动**（它们从头到尾没参与）；
- `VACUUM` 会再写一份完整文件 ⇒ **新增空间预检**（现有 `create()` 没有；恢复侧已有，参考
  `AndroidBackupRepository.kt:337-366` 的算法）：所需 ≈ `快照大小 + 裁剪后大小 + 工作余量`，
  不足时给出「需要 X MB / 可用 Y MB」的明确失败，而不是裁到一半 ENOSPC。

### 2.7 测试缝（按成本从低到高）

1. **JVM 单测**（`core/domain/src/test`）：`BackupSelection` 的闭包/传播/mask；非法组合必抛；
   `CLOSURE` 表与附录 A 的 `expectedClosure` 逐条相等（把附录当数据源读进来，或反向断言两处同源）。
2. **纯 Python 校验**（本设计件已跑，命令见附录 E）：附录 A 与 `66.json` 对拍（覆盖/闭包/顺序/软引用/kind 覆盖）
   + 真实 DDL 内存库干跑 5 组合法组合与 1 组反证。
3. **instrumented**（`core:data`，扩 `BackupRestoreInstrumentedTest`，现有 25 个用例可复用夹具）：
   - 种哨兵：给每类各写一行带唯一标记的数据 ⇒ 裁成 `{错题本+图片}` ⇒ 解包后在包内字节里搜哨兵，
     **会话/掌握库的标记必须搜不到**（这条同时钉住 §2.4-4 的 VACUUM 与 I2）；
   - 往返：裁剪包 → 恢复到干净库 → 题数与引用集合等于 I3/I5 的数；
   - 负向：**FK 闭包守卫测试**——遍历 `PRAGMA foreign_key_list` 构造真实边集，
     断言任一跨类别边都在 `CLOSURE` 里（将来某次迁移新增跨类别 FK 时，这条会先红）；
   - 非法组合：直接构造 `BackupSelection` 不自洽的实例调 `create()` ⇒ 必须抛，且不产出文件。

---

## 3. ③ 恢复侧校验与回滚衔接

### 3.1 现有阶段（复用，不改）

| 阶段 | 位置 | 现状职责 |
| --- | --- | --- |
| Phase 1 VALIDATING | `:327-335` | `SmbkArchiveCodec.unpack` → 结构/CRC/SHA/清单/设置/引用资产声明全套校验 |
| Phase 2 PREFLIGHT | `:337-379` | 磁盘空间、压缩比 |
| Phase 2b | `:381-390` | `inspectStagedDatabase`：Room 全量迁移副本 + `user_version` 对拍 + `quick_check` + `foreign_key_check`（`:780-850`） |
| Phase 2c | `:391-395` | `verifyStagedAssetClosure`：归档库引用集合 ⊆ 解包资产，逐条字节数/SHA（`:862-888`） |
| Phase 2d | `:397-400` | 设置载荷读进内存（交换前失败） |
| Phase 3–7 | `:402-489` | journal 记录代际路径 → 关库 → 两次原子 rename → 验证 → fsync → 清理 |
| 失败路径 | `:501-538` | `swapStarted` 前失败 = 直接清临时件；之后失败 = 代际回滚 + 隔离 |
| 启动恢复 | `RestoreRecoveryCoordinator` | journal 驱动 rollback/cleanup/quarantine |

### 3.2 新增校验（必须全部落在 Phase 3 之前）

放在 **Phase 2b 之后、Phase 3 之前**（即 `journal.writePhase(BACKUP_CURRENT)` 之前，`:403`），
理由只有一句：**这一刻 `swapStarted` 还是 false，任何失败都会落进既有的「尚未 swap，直接清临时件」
分支（`:522-527`）**——原数据、原图都还在原地，不需要新的回滚代码。

| 新增点 | 判定 | 失败语义 |
| --- | --- | --- |
| S1 解析声明 | v3 的 `selection` 块必须存在且自洽（`categoryMask` 与 `categories` 一致、mask 合法、`closureVersion` 可读） | 读不懂的包在**交换前**拒绝（与 6C-1 的版本闸门同一纪律，`SmbkArchiveCodec.kt:809-814`） |
| S2 声明 vs 实测 | 声明的未勾选类别，其表在 staged 库里必须 0 行；声明为 `ABSENT` 的资产策略，包内不得有 `assets/` 条目 | 声明撒谎 = 拒绝（清单是包自己带的，可以撒谎；所以这一条只用来抓「声明的裁剪没做干净」，**权威判据始终是包内库实测**——沿用 `SmbkArchiveCodec.kt:66-72` 的既有口径） |
| S3 软引用闭合 | 在 staged 库上重跑附录 C 的全部检查 = 0 行 | 悬空引用拒绝（外部工具重裁/拼接的包在这里露馅） |
| S4 资产闭合 | 现有 `verifyStagedAssetClosure`（Phase 2c）**不需要改**：引用集合已随裁剪变小，`ABSENT` 时集合为空、天然放行 | — |

### 3.3 回执与文案（「如实」这件事有产品后果）

恢复是**替换**语义（整代交换，不是合并）：恢复一个「只有错题本」的包，本机原来的会话与学习记录
会随之消失。这不是本次发明的行为，是现有 `restore` 的既定语义（`:420-472`），但**部分包会让它的
后果变得更不直观**，所以：

- `RestoreReceipt` 增加 `selection: BackupSelectionSummary?`（mask/categories/assetPolicy，
  v1/v2 旧包为 null = 全量）；
- UI 必须在**恢复前**把话说全：「这份备份只含 错题本 与 图片；恢复会替换本机全部数据，
  未包含的内容（会话、学习记录）会被清空」。恢复后再次如实复述（回执）；
- 6C-1 已有的 `RestoreSettingsOutcome.WRITE_FAILED` 文案保持不动（设置写失败 = 部分恢复）。

### 3.4 为什么不需要新回滚机制

三条都不需要新代码：
1. 新校验全在 swap 前（上面 §3.2 的位置约束是设计约束，实施时必须用测试钉住）；
2. 交换本身的原子性/代际隔离/journal/启动恢复与「包里内容多少」正交——少几类行不会改变
   两次 rename 的成败条件；
3. 恢复后的投影重建沿用既有机制：全量重放由
   `StudyProjectionDrainer.drain()` 的 `FULL_REPLAY_REQUIRED` 分支驱动（`:115-125`），
   它读的是账本 + 当前绑定表（`:208-224`）。**裁剪保持「账本与投影整组同进退」**，
   所以恢复后的库要么有完整账本、要么两者都没有——不会出现「投影记账到 N、账本只剩 0」的破损态。

---

## 4. ④ 换机口径与旧包兼容

### 4.1 随包 / 不随包清单（换机视角）

| 项 | 随包 | 证据 |
| --- | --- | --- |
| 题、题面修订、练习单元、收录条目、草稿、标签、关系 | ✅（`ISSUE_BOOK`） | §1.1 |
| 题图字节 + 资产行 | ✅（`IMAGES` 或随 `ISSUE_BOOK`/`SESSIONS` 强制带上） | §1.4 |
| 学习证据账本、投影、复习计划/队列/会话、复习日志、预测、教学建议、聊天证据 | ✅（`MASTERY`） | §1.1 |
| 讲题会话与消息 | ✅（`SESSIONS`） | §1.1 |
| 知识层（节点/来源/关系/教学材料）+ `content_install_state` | ✅ 恒随包 | §1.1 |
| 设置偏好：保留带、考试日历、FSRS 参数、提醒开关与时刻 | ✅ | `SmbkSettingsDocumentV1`（`SmbkSettingsDocument.kt:26-47`） |
| 通知投递历史（「今天已提醒过」） | ❌ 刻意不装 | 同文件 `:16-21`（搬到另一天恢复会吞掉/重复当天提醒） |
| 模型 API Key / Keystore 别名、授权租约、sleep journal | ❌ 刻意不装 | 同文件 `:16-21`；`deleteAllData` 单独清 Keystore 别名（`AndroidBackupRepository.kt:690-711`） |
| 设备侧凭据与「随时间滚动的观测」 | ❌ | 同上 |
| 系统通知授权 | ❌ 本来就不在偏好里 | `:575-582` 的 KDoc 明说 |

### 4.2 身份、时区、学习日

- **身份不随设备走也不变**：learner 是常量 `DEFAULT_LEARNER_ID = "learner:local"`
  （`RoomBackedStudyExperienceRepository.kt:946`）。换机恢复后账本/投影/序列都在同一个 learner 下，
  不需要身份迁移，也不存在「两个 learner 混进一个库」的风险（单机单用户模型）。
- **时区/学习日**：`review_plan.time_zone_id` + `local_day_epoch_day` 参与主键，
  `active_review_plan_slot` 的主键是 `(learner_id, local_day_epoch_day, time_zone_id)`
  （schema v66）；换时区后「今天」的槽位键不同 ⇒ 自然重规划，旧时区的计划行按历史计划留存。
  这是既有行为，6C-2 不引入新口径，但**恢复回执不应宣称「复习进度完整」**——
  同一天在另一个时区恢复，当天队列会重新规划。
- **资产路径与设备无关**：`relative_path` 是 `source-assets/<sha256>.<ext>` 的扁平约定
  （测试夹具 `BackupRestoreInstrumentedTest.kt:970-992` 与 vault 一致），换机后物理解析不变。

### 4.3 兼容矩阵

| 包版本 | 内容 | 新 App（v3 读侧）行为 | 旧 App（v2 读侧）行为 |
| --- | --- | --- | --- |
| v1 | DB + assets，无 settings/版本戳 | 读作**全量**；设置不动（`NOT_IN_ARCHIVE`） | 同（现状） |
| v2 | + settings + 版本戳 + `referencedAssets` | 读作**全量**；设置按 6C-1 落盘 | 现状 |
| v3 | + `selection` 块（可全选） | 按声明校验（S1/S2）；全选时与 v2 等价 | **拒绝**：`formatVersion > 2` → 「备份格式版本过新，暂不支持恢复」（`SmbkArchiveCodec.kt:809-811`） |

v3 的写侧只写 3；读侧 `MIN_READABLE_FORMAT_VERSION` 保持 1（`:81`）——**旧包永远可读**这条不破。

### 4.4 schema 版本（含本批的 67）

- 恢复侧对 schema 的既有做法：把归档库走一遍生产 Room builder（迁移全跑），再要求
  `user_version` 与实时库相等（`AndroidBackupRepository.kt:824-834`）——**子集包不改变这套**，
  因为裁剪不碰 DDL。
- 本批 T2 的 `MIGRATION_66_67`（`error_book_entry` 加收藏列、`problem_draft.source_asset_id` 改可空、
  `library_catalog` 视图加列；plan `:21-24`）在工作树里已可见（`FavoriteAndManualDraftMigration_66_67.kt`，
  属他线在飞文件，本轨只读不改）。**读码核对结论**：三处都不新增/不删除外键边——
  `error_book_entry` 只 `ADD COLUMN`（`:46-48`）；`problem_draft` 走「建新表→搬行→DROP→RENAME」，
  新表的 `FOREIGN KEY(source_asset_id) REFERENCES canonical_source_asset(source_asset_id) ON DELETE RESTRICT`
  与 66 形态逐字相同（`:54-75`），只是该列不再 NOT NULL；视图不参与闭包（无行）。
  因此 §1.2 的闭包与 §2.3 的顺序**预期不受 67 影响**；`source_asset_id` 变可空后，
  「无源资产草稿」在 6C-2 里的表现与今天一致（草稿仍属 `ISSUE_BOOK`，草稿仍不引用任何资产）。
- **仍未验证的部分**：`67.json` 尚未导出（目录里目前只有到 `66.json`，`:ls` 实测），
  67 形态的最终 FK 边以导出的 schema 为准。**实施时必做**：把附录 E 的校验脚本指向 `67.json`
  重跑一次（脚本默认取目录里版本号最大的 schema），出现差异即按新 FK 边修正闭包与顺序。

---

## 5. ⑤ 与 6C-1 manifest v2 的字段衔接（字段草案）

### 5.1 v3 清单结构

> v2 更新（2026-10-21）：`closureVersion=2`、`selection.schemaVersion=2`、`rowCounts` 覆盖 86 表、
> `trimmedTableCount` 上限 78；现行规则与机器清单见 §9.7 / §9.11。

```jsonc
{
  "formatVersion": 3,                 // 2 → 3；读侧 MIN_READABLE 仍为 1
  "databaseSchemaVersion": 67,
  "createdAt": 1760000000000,
  "problemCount": 128,                // 取自**裁剪后**快照（既有口径不变）
  "assetCount": 219,
  "hashAlgorithm": "SHA-256",
  "appVersionName": "1.4.0",
  "appVersionCode": 42,
  "contentVersion": "moe-2025-four-subjects-v1@2026-09-19",
  "hasSettings": true,
  "settingsDigest": "…",
  "referencedAssets": ["assets/<sha>.jpg", "…"],   // v2 字段；ABSENT 时为空数组
  "selection": {                       // v3 新增；null/缺席 = 全量（v1/v2 语义）
    "schemaVersion": 1,
    "categoryMask": 9,
    "categories": ["ISSUE_BOOK", "IMAGES"],
    "assetPolicy": "INCLUDED",         // INCLUDED | ABSENT
    "closureVersion": 1,
    "closureDigest": "sha256:…",
    "rowCounts": { "problem": 128, "problem_revision": 401, "error_book_entry": 128, "…": 0 },
    "trimmedTableCount": 33
  },
  "files": [ { "path": "database.sqlite", "sha256": "…", "byteSize": 123 }, … ]
}
```

### 5.2 各字段消灭的失败（逐条对应「新增必先指认」）

| 字段 | 消灭的具体失败 |
| --- | --- |
| `categoryMask` + `categories` | 恢复端与用户**无法区分「全量包」与「子集包」**时的误预期：用户以为恢复回了全部，实际只有一部分。有它才能如实回执、才能在 S2 对「未勾选类别必须 0 行」做对拍 |
| `assetPolicy` | `referencedAssets` 为空有两种完全不同的含义（「库里本来没图」vs「这次不带图」）。没有这个字段，读侧只能靠猜；有了它，「声明 ABSENT 却带着 assets/ 条目」当场可判 |
| `closureVersion` | 闭包规则会随 schema 演进（新表、新 FK）。不同版本的包用同一套规则解读会把「合法子集」误判为破损。版本号让读侧能对「规则版本比本机新」的包直接拒绝 |
| `closureDigest` | ① 回归对拍：同输入必得同摘要，「这次备份和上次是否同构」一眼可判；② 第三方重裁/拼接：清单自洽性可验（摘要覆盖 categoryMask + 顺序化行数），改库不改摘要就露馅。**它不是权威判据**——权威仍是包内库实测（同 `SmbkArchiveCodec.kt:66-72` 的既有立场） |
| `rowCounts` | 恢复后可逐表对拍「包内库是不是被换过」；也是 I3/I6 的恢复侧对照物（诊断用，缺失/不符只报不接受） |
| `trimmedTableCount` | 让「这次到底裁了几张表」在诊断面可见（配合 `closureVersion` 排查「规则与实现漂开」） |

`closureDigest` 算法（定稿级）：
```
digest = SHA-256(  "smbk-selection-v1\n"
                 + "closureVersion=1\n"
                 + "categories=" + categories.sorted().joinToString(",") + "\n"
                 + "assetPolicy=" + assetPolicy + "\n"
                 + rowCounts.keys.sorted().joinToString("") { "$it=${rowCounts[it]}\n" } )
```

### 5.3 域类型扩展

| 类型 | 扩展 | 兼容 |
| --- | --- | --- |
| `BackupManifestSummary`（`BackupRepository.kt:112-126`） | `selection: BackupSelectionSummary? = null` | 默认 null = v1/v2 = 全量 |
| `BackupReceipt`（`:34-48`） | `selectionMask: Int = FULL_MASK`、`trimmedTableCount: Int = 0` | 默认值即旧语义 |
| `RestoreReceipt`（`:50-66`） | `selection: BackupSelectionSummary? = null` | 同上 |
| `BackupOptions`（`:26-32`） | `selection: BackupSelection = FULL`；`includeAssets` 退役 | **行为不兼容点**：原 `require(includeAssets)` 使 `false` 不可能通过，退役无实际影响（同文件注释自述「First backup version always includes canonical assets」） |
| `SmbkManifest`（`SmbkArchiveCodec.kt:32-83`） | `selection: SmbkSelectionRecord? = null` + `FORMAT_VERSION = 3` | `MIN_READABLE_FORMAT_VERSION` 保持 1 |

序列化注意：codec 的 Json 配置是 `ignoreUnknownKeys = false` + `encodeDefaults = true`
（`SmbkArchiveCodec.kt:154-161`）。新增可空字段带默认值，v1/v2 包**缺 `selection` 键**读作 null，
不需要放宽容错。

---

## 6. ⑥ 待裁定项与风险

### 6.1 待裁定（3 条需要人裁；其余为实施时自决并登记）

> 2026-10-21 T8：①/②/③ 三项仍未裁定；§9.9 只列影响面，不代签，也不选默认。

| # | 项 | 选项 | 影响 | 建议 |
| --- | --- | --- | --- | --- |
| ① | 「仅图片」组合（mask=8）是否在 UI 提供 | a) 不提供；b) 提供并提示 | a) 少一个无意义入口；b) 用户能导出一包**恢复后会被孤儿回收清掉**的图 | **a**（§1.5 的宽限期证据） |
| ② | 空选择（mask=0）是否允许 | a) UI 禁止（至少勾一项）；b) 允许 | b) 的产物是「重置包」：恢复后本机只剩知识层与设置 | **a** |
| ③ | 派生索引（`knowledge_search_feature` + `knowledge_search_index_state`）是否**恒裁** | a) 恒裁 + 恢复后读时自愈；b) 全选时保留；c) 真做一次后台重建 | a) 包最小，但首次召回某科要现场整科重建（S21 记录过 16.6s 级长尾）；b) 全选包 ≈ v2 大小（623,774 行特征）；c) 恢复回执后异步重建，用户无感 | **a + c**：恒裁，并在恢复完成后（重启后、DB 重开）按科 enqueue 一次 `KnowledgeSearchIndexBuilder.rebuildSubject`；若判定「恢复不该有额外负载」则退 **b** |
| ④ | 类别命名与文案（用户看到的四项） | 「错题本/掌握库/会话/图片」（spec 原词）vs 更直白的「题目与题图/学习记录/讲题记录/图片」 | 用户能否预判「取消它会丢什么」 | 保留 spec 原词做**类别名**，在下行说明里写清「包含/不含什么」 |
| ⑤ | 「无图」子集（拆掉 `ISSUE_BOOK ⇒ IMAGES`）要不要作为未来能力 | a) 不做；b) 做（需 `assetPolicy=ABSENT` + 用户二次确认 + 详情页如实降级） | b) 省大量字节，但题面原图/干净图消失；导出与重绘链缺输入 | **a**（§1.4 三条证据） |
| ⑥ | `llm_teaching_advisory` / `binding_audit_sample` / `knowledge_grounding_*` 的归类边界 | 归 `ISSUE_BOOK`（本设计）vs 归 `MASTERY` | 只影响「不带掌握库时这几张表在不在」，不改闭包 | 本设计取 `ISSUE_BOOK`（它们按题/按绑定存档，见 §1.1）；有异议改附录 A 一行数据 |
| ⑦ | 恢复的替换语义要不要追加「合并恢复」 | a) 保持替换 + 前置文案；b) 做合并 | b) 与代际交换架构正面冲突（合并要跨代 diff），且要定义「同一道题两边都改过」的裁决 | **a** |
| ⑧ | 裁剪 + VACUUM 的耗时/空间在大库上是否可接受 | 直接做 vs 只在有删除时做 vs 评估 `secure_delete` | VACUUM 是第二次全库重写；`secure_delete=ON` 会让每次 DELETE 变慢但免掉 VACUUM | 先按「有删除才 VACUUM」实现，设备实测 100MB 级库耗时后再定 |

### 6.2 风险

| # | 风险 | 触发条件 | 缓解 | 验证 |
| --- | --- | --- | --- | --- |
| R1 | 漏列一条软引用 ⇒ 悬空子集 | 新增引用列而没更新 `softReferences` | 附录 C 的检查清单 + I4；instrumented 全表扫描「引用列」与清单比对（可选的第三条守卫） | 附录 E 脚本 + instrumented |
| R2 | 将来迁移新增跨类别 FK ⇒ 子集静默破损 | `68.json` 之后新增 FK 边 | 闭包校验脚本按最新 schema 重跑（本设计件的脚本默认取最高版本）；instrumented 的 FK 闭包守卫测试 | 每次 bump 重跑 |
| R3 | 裁剪后不 VACUUM ⇒ 被裁内容仍随包外泄 | 实现漏掉 VACUUM 或 VACUUM 失败被吞 | I2/I3 之外加「哨兵文本搜不到」测试（§2.7-3）；VACUUM 失败 = 成包失败 | instrumented 哨兵用例 |
| R4 | FTS 索引与裁剪后内容漂开 ⇒ 搜到幽灵行 | 只删 content 不 rebuild | 裁剪收尾统一 rebuild（§2.4-3）；恢复侧对 `docid` 孤儿数做对拍 | 本机探针已过；设备上按 API 级别复核 rebuild 行为 |
| R5 | 新校验放错位置（swap 之后）⇒ 半恢复 | 实现时把 S1–S3 写到 `BACKUP_CURRENT` 之后 | 设计约束写明（§3.2）；用例钉住「校验失败时**没有** generation 产物、原数据可读」 | instrumented |
| R6 | 用户把「部分包恢复」理解成合并 ⇒ 数据被意外替换 | 文案缺失 | §3.3 的前置与回执文案；`RestoreReceipt.selection` | 设备走查（T8） |
| R7 | Android 内置 SQLite 与探针版本差异 | 老 API 上 FTS4 rebuild 语义、`VACUUM INTO` 可用性 | `VACUUM INTO` 已有回退（`RoomBackupSupportStore.kt:41-73`）；rebuild 是 FTS3/4 早期即有的命令，但仍需设备复核 | 设备：低 API + 高 API 各一次裁剪往返 |
| R8 | `MASTERY` 勾选等于全选，与用户直觉不符 | UI 只给四个复选框 | 勾选传播 + 每项下写「勾它会连带带上哪些」；文案里不承诺「只备份掌握库」 | 设备走查（T8） |
| R9 | 裁剪 + VACUUM 的空间不足导致成包失败（今天没有预检） | 大库 + 低存储设备 | §2.6 的新增空间预检 | 单测（小配额注入）+ 设备 |

---

## 7. 统一生图接入后的闭包补充（2026-10-18）

> 2026-10-21 T8：本节 5 个复选项仍未勾；schema 69 的机器清单重算与用例展开见 §9。
> 其中 JS/预览/未知程序版本仍为前向设计（schema 69 没有对应表），不得写成已覆盖。

本节承接[4E的WG-08](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#wg-08状态预算恢复与备份)与[后续计划](../../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md)。旧schema66的84表/90边/5组合法选择是原探针快照，不代表加入新图表后仍成立；本次不把尚未迁移的表写进附录A机器JSON，也不声称已重跑Android恢复。

- [ ] 实际4E迁移落地后，从最新Room导出重新生成类别/FK/软引用/裁剪顺序及closureVersion/digest，覆盖六个拟新增记录表与旧作业operation关联，不写死版本68或旧84表数量。
- [ ] 题目/消息引用作为保留根计算JS程序、快照、必要预览及canonical图像的可达闭包；同一程序被保留题目和被删除会话共用时仍须保留，不能按整表类别删除。共享operation/attempt按存活宿主与引用分行筛选，扩展现有SPLIT机制，不全部归SESSIONS或IMAGES。
- [ ] 程序和快照作为持久事实、预览/字体版本与图像字节按实际引用进入清单；不可把JS预览当唯一图事实。必要文件缺失/hash不符在交换前拒绝，未固定派生缓存按既有可重建规则处理。
- [ ] 增加混合JS/IMAGE_MODEL、共享图双根、删除会话保留题、不同selection、未知程序版本、缺预览、源revision改变的成包/恢复用例；历史查看/导出0模型派发，恢复ledger不归零，不复活外发授权。
- [ ] 本设计沿用快照库裁剪、不动实时库；其基于VACUUM的路线需先解决KD-53大库上限与KD-54快照写饥饿并设备复验。不能用内容勾选扩展绕过已有备份正确性问题，也不在本节替未决产品选项代签。

## 附录 A · 类别 → 表（机器可读，唯一数据源）

> **历史版本**：本附录是 schema 66 的机器清单（84 表 / 90 边），已被 §9 的 v2（schema 69）取代；
> 保留为研究证据，实施不得据此写代码。当前清单以 §9.11 的 `6c2-plan-v2` 块为准。

> 本块由自检脚本解析（`<!-- 6c2-plan:begin -->` 标记内）。改动只改这里，
> 然后重跑附录 E 的校验——脚本会把「文档」与「Room schema 导出的真实 FK 图」逐条对拍。

<!-- 6c2-plan:begin -->
```json
{
  "closureVersion": 1,
  "note": "6C-2 内容勾选：类别→表清单 + FK 闭包 + 裁剪顺序。表名逐字取自 Room schema 导出（66.json entities）。",
  "categories": {
    "ISSUE_BOOK": {
      "bit": 1,
      "label": "错题本",
      "tables": [
        "problem", "problem_revision", "practice_unit", "error_book_entry",
        "problem_relation", "problem_classification_binding",
        "problem_classification_candidate", "problem_organization_receipt",
        "practice_unit_knowledge_binding", "problem_revision_source_asset",
        "problem_draft", "problem_draft_source_asset", "problem_draft_revision",
        "problem_draft_edit_snapshot", "problem_draft_commit_receipt",
        "batch_import_job", "batch_import_page", "split_import_job",
        "split_import_question", "clean_redraw_task",
        "library_search_content", "library_search_fts", "library_search_outbox",
        "knowledge_grounding_request", "knowledge_grounding_resolution",
        "knowledge_research_review_bundle", "knowledge_research_review_source",
        "mistake_export_record", "binding_audit_sample", "llm_teaching_advisory"
      ]
    },
    "MASTERY": {
      "bit": 2,
      "label": "掌握库",
      "tables": [
        "assessment_item_snapshot", "assessment_event",
        "assessment_evidence_snapshot", "assessment_evidence_attribution",
        "assessment_presentation", "assessment_answer_reveal_event",
        "answer_reveal_outcome", "attempt_submission", "attempt_event",
        "attempt_correction", "binding_change_event", "learning_sequence",
        "projection_outbox", "learner_projection_snapshot", "projection_archive",
        "learner_problem_memory_state", "learner_knowledge_mastery_state",
        "independent_correct_observation", "applied_attempt_record",
        "applied_correction_record", "applied_answer_reveal_record",
        "applied_tutor_answer_exposure_record", "presentation_projection_state",
        "review_plan", "active_review_plan_slot", "review_queue_item",
        "review_queue_knowledge_node", "review_queue_reason", "review_session",
        "review_session_revision", "review_session_advance_receipt",
        "review_log", "student_model_prediction", "prediction_outcome",
        "learner_chat_evidence"
      ]
    },
    "SESSIONS": {
      "bit": 4,
      "label": "会话",
      "tables": [
        "tutor_conversation", "tutor_message", "tutor_message_source_asset",
        "agent_pending_request", "tutor_answer_exposure",
        "tutor_answer_exposure_outcome"
      ]
    },
    "IMAGES": {
      "bit": 8,
      "label": "图片",
      "tables": ["canonical_source_asset"]
    }
  },
  "alwaysTables": [
    "knowledge_node", "knowledge_source", "knowledge_node_source_binding",
    "knowledge_node_relation", "knowledge_teaching_material",
    "knowledge_teaching_material_node_binding", "content_install_state"
  ],
  "derivedTables": ["knowledge_search_feature", "knowledge_search_index_state"],
  "splitTables": ["model_task_event", "model_task", "model_task_operation"],
  "modelTaskKindCategory": {
    "CAPTURE_ASSESS": "ISSUE_BOOK",
    "CAPTURE_PARSE": "ISSUE_BOOK",
    "PROBLEM_CLASSIFY": "ISSUE_BOOK",
    "PROBLEM_RELATE": "ISSUE_BOOK",
    "IMAGE_PIPELINE_CLASSIFY": "ISSUE_BOOK",
    "TUTOR_PLAN": "SESSIONS",
    "TUTOR_RESPOND": "SESSIONS",
    "TUTOR_LOBBY": "SESSIONS",
    "TUTOR_EVALUATE": "SESSIONS",
    "LEARNING_SUMMARIZE": "SESSIONS",
    "REVIEW_RERANK": "MASTERY",
    "KNOWLEDGE_QUIZ": "MASTERY"
  },
  "rebuildTables": ["library_search_fts"],
  "expectedClosure": [
    ["ISSUE_BOOK", "IMAGES"],
    ["MASTERY", "ISSUE_BOOK"],
    ["MASTERY", "SESSIONS"],
    ["SESSIONS", "ISSUE_BOOK"],
    ["SESSIONS", "IMAGES"]
  ],
  "softReferences": [
    {"table": "model_task", "column": "subject_id", "targetTable": "*by-kind*", "kind": "cross-category"},
    {"table": "model_task_operation", "column": "subject_id", "targetTable": "*by-kind*", "kind": "cross-category"},
    {"table": "tutor_conversation", "column": "capture_draft_id", "targetTable": "problem_draft", "kind": "cross-category"},
    {"table": "tutor_conversation", "column": "anchor_practice_unit_id", "targetTable": "practice_unit", "kind": "cross-category"},
    {"table": "tutor_conversation", "column": "anchor_problem_revision_id", "targetTable": "problem_revision", "kind": "cross-category"},
    {"table": "tutor_conversation", "column": "anchor_id", "targetTable": "*by-anchor-kind*", "kind": "cross-category"},
    {"table": "tutor_message", "column": "bound_problem_id", "targetTable": "problem", "kind": "cross-category"},
    {"table": "tutor_message", "column": "bound_problem_revision_id", "targetTable": "problem_revision", "kind": "cross-category"},
    {"table": "tutor_message", "column": "reply_to_message_id", "targetTable": "tutor_message", "kind": "same-category"},
    {"table": "learner_chat_evidence", "column": "conversation_id", "targetTable": "tutor_conversation", "kind": "cross-category"},
    {"table": "learner_chat_evidence", "column": "knowledge_node_id", "targetTable": "knowledge_node", "kind": "always"},
    {"table": "llm_teaching_advisory", "column": "practice_unit_id", "targetTable": "practice_unit", "kind": "same-category"},
    {"table": "llm_teaching_advisory", "column": "knowledge_node_id", "targetTable": "knowledge_node", "kind": "always"},
    {"table": "binding_audit_sample", "column": "practice_unit_id", "targetTable": "practice_unit", "kind": "same-category"},
    {"table": "binding_change_event", "column": "practice_unit_id", "targetTable": "practice_unit", "kind": "cross-category"},
    {"table": "student_model_prediction", "column": "practice_unit_id", "targetTable": "practice_unit", "kind": "cross-category"},
    {"table": "student_model_prediction", "column": "knowledge_node_id", "targetTable": "knowledge_node", "kind": "always"},
    {"table": "review_log", "column": "card_id", "targetTable": "practice_unit", "kind": "cross-category"},
    {"table": "clean_redraw_task", "column": "revision_id", "targetTable": "problem_revision", "kind": "same-category"},
    {"table": "clean_redraw_task", "column": "source_asset_id", "targetTable": "canonical_source_asset", "kind": "same-category"},
    {"table": "knowledge_grounding_request", "column": "problem_id", "targetTable": "problem", "kind": "same-category"},
    {"table": "knowledge_grounding_request", "column": "practice_unit_id", "targetTable": "practice_unit", "kind": "same-category"},
    {"table": "knowledge_grounding_resolution", "column": "knowledge_node_id", "targetTable": "knowledge_node", "kind": "always"},
    {"table": "knowledge_research_review_bundle", "column": "grounding_key", "targetTable": "knowledge_grounding_request", "kind": "same-category"},
    {"table": "library_search_content", "column": "problem_revision_id", "targetTable": "problem_revision", "kind": "same-category"},
    {"table": "library_search_outbox", "column": "revision_id", "targetTable": "problem_revision", "kind": "same-category"},
    {"table": "library_search_fts", "column": "docid", "targetTable": "library_search_content", "kind": "same-category"},
    {"table": "assessment_answer_reveal_event", "column": "outcome_id", "targetTable": "answer_reveal_outcome", "kind": "same-category"},
    {"table": "assessment_answer_reveal_event", "column": "presentation_id", "targetTable": "assessment_presentation", "kind": "same-category"},
    {"table": "tutor_answer_exposure", "column": "session_id", "targetTable": "tutor_conversation", "kind": "same-category"},
    {"table": "tutor_answer_exposure_outcome", "column": "session_id", "targetTable": "tutor_conversation", "kind": "same-category"},
    {"table": "attempt_event", "column": "presentation_id", "targetTable": "assessment_presentation", "kind": "same-category"},
    {"table": "attempt_event", "column": "submission_id", "targetTable": "attempt_submission", "kind": "same-category"},
    {"table": "review_session_advance_receipt", "column": "submission_id", "targetTable": "attempt_submission", "kind": "same-category"},
    {"table": "review_session_advance_receipt", "column": "presentation_id", "targetTable": "assessment_presentation", "kind": "same-category"},
    {"table": "presentation_projection_state", "column": "terminal_outcome_id", "targetTable": "answer_reveal_outcome", "kind": "same-category"},
    {"table": "assessment_evidence_attribution", "column": "knowledge_node_id", "targetTable": "knowledge_node", "kind": "always"},
    {"table": "review_queue_item", "column": "item_family_id", "targetTable": "", "kind": "same-category"}
  ],
  "trimOrder": [
    "tutor_message_source_asset", "tutor_message", "split_import_question", "split_import_job",
    "review_session_advance_receipt", "review_session_revision", "review_session",
    "review_queue_reason", "review_queue_knowledge_node", "review_queue_item",
    "review_log", "projection_outbox", "projection_archive",
    "problem_revision_source_asset", "problem_relation", "problem_organization_receipt",
    "problem_draft_source_asset", "problem_draft_edit_snapshot", "problem_draft_revision",
    "problem_draft_commit_receipt", "problem_classification_candidate",
    "problem_classification_binding", "presentation_projection_state",
    "prediction_outcome", "student_model_prediction", "model_task_event",
    "mistake_export_record", "llm_teaching_advisory", "library_search_outbox",
    "library_search_content", "learning_sequence", "learner_problem_memory_state",
    "learner_chat_evidence", "knowledge_search_index_state", "knowledge_search_feature",
    "knowledge_research_review_source", "knowledge_research_review_bundle",
    "knowledge_grounding_resolution", "knowledge_grounding_request",
    "independent_correct_observation", "learner_knowledge_mastery_state",
    "error_book_entry", "clean_redraw_task", "binding_change_event", "binding_audit_sample",
    "batch_import_page", "problem_draft", "canonical_source_asset", "batch_import_job",
    "assessment_presentation", "assessment_evidence_attribution",
    "practice_unit_knowledge_binding", "assessment_event", "assessment_item_snapshot",
    "applied_tutor_answer_exposure_record", "tutor_answer_exposure_outcome",
    "tutor_answer_exposure", "model_task", "model_task_operation",
    "applied_correction_record", "attempt_correction", "applied_attempt_record",
    "attempt_event", "attempt_submission", "applied_answer_reveal_record",
    "learner_projection_snapshot", "answer_reveal_outcome",
    "assessment_answer_reveal_event", "assessment_evidence_snapshot",
    "practice_unit", "problem_revision", "problem", "agent_pending_request",
    "tutor_conversation", "active_review_plan_slot", "review_plan"
  ]
}
```
<!-- 6c2-plan:end -->

## 附录 B · 裁剪顺序（人读版，逐项）

按附录 A 的 `trimOrder` 展开 = 76 项（可裁表 77 张减去 `library_search_fts`，它由 rebuild 处理）。
分段只是注释，顺序本身不能改（改了就会被附录 E 的拓扑序校验挡住）：

```
    (1)  tutor_message_source_asset
    (2)  tutor_message
    (3)  split_import_question
    (4)  split_import_job
    (5)  review_session_advance_receipt
    (6)  review_session_revision
    (7)  review_session
    (8)  review_queue_reason
    (9)  review_queue_knowledge_node
    (10) review_queue_item
    (11) review_log
    (12) projection_outbox
    (13) projection_archive
    (14) problem_revision_source_asset
    (15) problem_relation
    (16) problem_organization_receipt
    (17) problem_draft_source_asset
    (18) problem_draft_edit_snapshot
    (19) problem_draft_revision
    (20) problem_draft_commit_receipt
    (21) problem_classification_candidate
    (22) problem_classification_binding
    (23) presentation_projection_state
    (24) prediction_outcome
    (25) student_model_prediction
    (26) model_task_event
    (27) mistake_export_record
    (28) llm_teaching_advisory
    (29) library_search_outbox
    (30) library_search_content
    (31) learning_sequence
    (32) learner_problem_memory_state
    (33) learner_chat_evidence
    (34) knowledge_search_index_state
    (35) knowledge_search_feature
    (36) knowledge_research_review_source
    (37) knowledge_research_review_bundle
    (38) knowledge_grounding_resolution
    (39) knowledge_grounding_request
    (40) independent_correct_observation
    (41) learner_knowledge_mastery_state
    (42) error_book_entry
    (43) clean_redraw_task
    (44) binding_change_event
    (45) binding_audit_sample
    (46) batch_import_page
    (47) problem_draft
    (48) canonical_source_asset
    (49) batch_import_job
    (50) assessment_presentation
    (51) assessment_evidence_attribution
    (52) practice_unit_knowledge_binding
    (53) assessment_event
    (54) assessment_item_snapshot
    (55) applied_tutor_answer_exposure_record
    (56) tutor_answer_exposure_outcome
    (57) tutor_answer_exposure
    (58) model_task
    (59) model_task_operation
    (60) applied_correction_record
    (61) attempt_correction
    (62) applied_attempt_record
    (63) attempt_event
    (64) attempt_submission
    (65) applied_answer_reveal_record
    (66) learner_projection_snapshot
    (67) answer_reveal_outcome
    (68) assessment_answer_reveal_event
    (69) assessment_evidence_snapshot
    (70) practice_unit
    (71) problem_revision
    (72) problem
    (73) agent_pending_request
    (74) tutor_conversation
    (75) active_review_plan_slot
    (76) review_plan
收尾  INSERT INTO library_search_fts(library_search_fts) VALUES('rebuild')
```

最容易写错的四处（建议在实现里写成注释）：
`canonical_source_asset`(48) 早于 `problem`(72)/`problem_revision`(71) 是**对的**（引用它的都是 link 表，已先删）；
`answer_reveal_outcome`(67) 必须早于 `assessment_answer_reveal_event`(68)；
`attempt_event`(63) 必须早于 `assessment_evidence_snapshot`(69)；
`model_task` (58) 必须早于 `model_task_operation`(59)（后者是前者的父）。

## 附录 C · 软引用闭合校验 SQL（恢复侧 S3 / 成包侧 I4 共用）

每条都写成「返回 0 行才通过」。kind 集合按附录 A 的 `modelTaskKindCategory` 取（下表内联为字面量，
实施时改成绑定参数）。

**C1 · model_task 家族按宿主解析（分域，见 §1.3）**
```sql
-- 采集类：宿主是草稿
SELECT task_id, task_kind, subject_id FROM model_task
WHERE task_kind IN ('CAPTURE_ASSESS','CAPTURE_PARSE')
  AND NOT EXISTS (SELECT 1 FROM problem_draft d WHERE d.draft_id = subject_id);

-- 整理类：宿主是题面修订
SELECT task_id, task_kind, subject_id FROM model_task
WHERE task_kind IN ('PROBLEM_CLASSIFY','PROBLEM_RELATE')
  AND NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = subject_id);

-- 图像分类类：宿主是源图资产（subjectIdOverride 有值时另说，实施时按真机采样钉死）
SELECT task_id, task_kind, subject_id FROM model_task
WHERE task_kind = 'IMAGE_PIPELINE_CLASSIFY'
  AND NOT EXISTS (SELECT 1 FROM canonical_source_asset a WHERE a.source_asset_id = subject_id)
  AND NOT EXISTS (SELECT 1 FROM problem_draft d WHERE d.draft_id = subject_id);

-- 讲题类：宿主是会话（PLAN/RESPOND 带 'tutor-conv:captured:' 前缀，LOBBY/DEBRIEF 直连会话 id）
SELECT task_id, task_kind, subject_id FROM model_task
WHERE task_kind IN ('TUTOR_PLAN','TUTOR_RESPOND','TUTOR_LOBBY','TUTOR_EVALUATE','LEARNING_SUMMARIZE')
  AND NOT EXISTS (
        SELECT 1 FROM tutor_conversation c
        WHERE c.conversation_id = subject_id
           OR c.conversation_id = substr(subject_id, length('tutor-conv:captured:') + 1));

-- 知识点复习类：宿主是知识点/学习者域（当前实现为自由字段，按非空 + 学科/节点可解析校验）
SELECT task_id FROM model_task
WHERE task_kind IN ('KNOWLEDGE_QUIZ','REVIEW_RERANK')
  AND NOT EXISTS (SELECT 1 FROM knowledge_node n WHERE n.knowledge_node_id = subject_id);
-- 同样三条检查对 model_task_operation.subject_id / task_kind 各跑一遍；
-- model_task_event 无 subject 列，靠 task_id 与 model_task 同谓词删除保证（§2.4-7）。
```

**C2 · 会话侧软引用**
```sql
SELECT conversation_id FROM tutor_conversation
WHERE (capture_draft_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM problem_draft d WHERE d.draft_id = capture_draft_id))
   OR (anchor_practice_unit_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM practice_unit u WHERE u.practice_unit_id = anchor_practice_unit_id))
   OR (anchor_problem_revision_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = anchor_problem_revision_id));

SELECT message_id FROM tutor_message
WHERE (bound_problem_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM problem p WHERE p.problem_id = bound_problem_id))
   OR (bound_problem_revision_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = bound_problem_revision_id));
```

**C3 · 掌握侧软引用**
```sql
SELECT evidence_id FROM learner_chat_evidence
WHERE NOT EXISTS (SELECT 1 FROM tutor_conversation c WHERE c.conversation_id = conversation_id);

SELECT practice_unit_id FROM review_log
WHERE NOT EXISTS (SELECT 1 FROM practice_unit u WHERE u.practice_unit_id = card_id)
  AND card_id NOT LIKE 'pseudo:%';   -- 伪绑定的 pseudo:<SUBJECT> 桶不是 practice_unit 行（StudyPracticeUnitFacts.kt:41）

SELECT practice_unit_id FROM binding_change_event
WHERE NOT EXISTS (SELECT 1 FROM practice_unit u WHERE u.practice_unit_id = practice_unit_id);

SELECT prediction_id FROM student_model_prediction
WHERE NOT EXISTS (SELECT 1 FROM practice_unit u WHERE u.practice_unit_id = practice_unit_id);
```

**C4 · 题侧与索引侧**
```sql
SELECT revision_id FROM clean_redraw_task
WHERE NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = revision_id)
   OR NOT EXISTS (SELECT 1 FROM canonical_source_asset a WHERE a.source_asset_id = source_asset_id);

SELECT revision_id FROM library_search_outbox
WHERE NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = revision_id);

SELECT content_row_id FROM library_search_content
WHERE NOT EXISTS (SELECT 1 FROM problem_revision r WHERE r.revision_id = problem_revision_id);

-- FTS 与内容表对齐（收尾 rebuild 之后的核对）
SELECT COUNT(*) FROM library_search_fts WHERE docid NOT IN (SELECT rowid FROM library_search_content);
```

**C5 · 治理台账**
```sql
SELECT grounding_request_id FROM knowledge_grounding_request
WHERE NOT EXISTS (SELECT 1 FROM problem p WHERE p.problem_id = problem_id)
   OR NOT EXISTS (SELECT 1 FROM practice_unit u WHERE u.practice_unit_id = practice_unit_id);

SELECT bundle_id FROM knowledge_research_review_bundle
WHERE NOT EXISTS (SELECT 1 FROM knowledge_grounding_request q WHERE q.grounding_key = grounding_key);
```

## 附录 D · 对拍不变式（成包侧 I1–I7 的可执行形式）

```sql
-- I1
PRAGMA foreign_key_check;                                  -- 期望 0 行
-- I2（未勾选类别的每张表）
SELECT COUNT(*) FROM "<table>";                            -- 期望 0
-- I3（勾选类别的每张表，与裁剪前同表计数比对；裁剪前计数在事务开始前读一次）
SELECT COUNT(*) FROM "<table>";                            -- 期望 = pre
-- I6（每张表）
--   changes() 累加 = 该表 DELETE 影响行数；要求 pre - post == deleted
-- I7
PRAGMA integrity_check;                                    -- 期望 'ok'
-- I5（资产闭合，双向）
--   引用侧：SELECT source_asset_id, relative_path, byte_size, content_sha256
--           FROM canonical_source_asset a WHERE EXISTS (
--               SELECT 1 FROM problem_revision_source_asset l WHERE l.source_asset_id = a.source_asset_id
--               UNION ALL SELECT 1 FROM problem_draft_source_asset l WHERE l.source_asset_id = a.source_asset_id
--               UNION ALL SELECT 1 FROM problem_draft d WHERE d.source_asset_id = a.source_asset_id
--               UNION ALL SELECT 1 FROM tutor_message_source_asset l WHERE l.source_asset_id = a.source_asset_id);
--   （生产实现直接复用 `readReferencedCanonicalAssets()`，口径只有那一处）
--   包内文件侧：assets/ 条目集合；两侧必须相等（assetPolicy=INCLUDED）；
--   assetPolicy=ABSENT 时两侧都必须为空。
```

## 附录 E · 自检与复跑

本设计件在本轮跑过的三项检查（脚本是本轮的工作副本，位于 gitignore 的 `.zcode/tmp/`；
下一批实施时应把前两项落到 `tools/backup/` 并接进 CI）：

| # | 脚本 | 命令 | 断言 |
| --- | --- | --- | --- |
| 1 | `check_selection_plan.py` | `python .zcode/tmp/check_selection_plan.py --doc docs/research/2026-10-17-6c2-content-selection-design.md --list-selections` | 84 表覆盖唯一；FK 推导的闭包 == `expectedClosure`；`trimOrder` 覆盖 76 张且是合法拓扑序；软引用清单的表/列/目标存在；`model_task` 分派覆盖 12 个 kind；枚举合法组合 |
| 2 | `dryrun_trim.py` | `python .zcode/tmp/dryrun_trim.py` | 用 schema 真实 DDL 建内存库 + 一表一行种子，5 组合法组合全部裁净（`foreign_key_check` 空、勾选表留 1 行、未勾选表 0 行、FTS 无孤儿 docid）；非法组合 `{错题本}` 被 FK 挡下（反证） |
| 3 | `probe_fts_rebuild.py` / `probe_free_pages.py` | 同名命令 | FTS4 外部内容表 `rebuild` 在显式事务内可用/可回滚；DELETE 后哨兵文本仍在文件里、VACUUM 后消失 |

**每次 schema bump（含本批 66→67）后必做**：把 #1 指向新导出的 `<version>.json` 重跑
（脚本默认自动取版本号最大的那份），差异即闭包/顺序需要修正的信号。

## 附录 F · 证据索引（本设计件引用的一手位置）

| 主题 | 位置 |
| --- | --- |
| 6C 需求/方向/验收门 | `docs/research/2026-10-11-new-stages-spec.md:127-142` |
| 本轨任务书 | `.zcode/plans/plan-2026-10-17-batch-13-6b-4c-kd51-kd44.md:44-45` |
| manifest v1→v2 与版本闸门 | `SmbkArchiveCodec.kt:24-83`、`:809-814` |
| 成包（引用资产闭合 require） | `SmbkArchiveCodec.kt:174-264`（`:191-199`） |
| 设置载荷与「不装什么」 | `SmbkSettingsDocument.kt:16-47`、`:64-89` |
| 成包流程（设置→快照→读口→收图） | `AndroidBackupRepository.kt:129-170`、`:179-196`、`:234-254`、`:264-276` |
| 快照机制（VACUUM INTO + 回退） | `RoomBackupSupportStore.kt:23-73`、`port/BackupPort.kt:23` |
| 恢复阶段与失败分支 | `AndroidBackupRepository.kt:327-414`、`:416-489`、`:501-538` |
| 资产闭合/预验证/journal/回执 | `AndroidBackupRepository.kt:862-888`、`:780-850`、`:938-1034`、`:491-500` |
| 设置落盘与部分恢复 | `AndroidBackupRepository.kt:556-603`、`BackupRepository.kt:68-81` |
| `BackupOptions` 现有约束 | `BackupRepository.kt:26-32` |
| 四表资产引用口径 | `PendingCaptureDao.kt:30-61`、`:63-85`；`RoomStudyDatabase.kt:833-847` |
| 详情页题图来源 | `MistakeDetailDao.kt:294-341` |
| 资源角色（原图/干净图） | `ProblemDraftTransactionDao.kt:793`、`:937`；`MistakePdfEligibilityTest.kt:129` |
| FTS 外部内容表与 outbox 触发器 | `LibrarySearchFtsMigration.kt:48-60`、`:117-133`；`RoomLibrarySearchStore.kt:317-359` |
| FTS 查询按 docid 联结 | `LibraryFtsCountPathInstrumentedTest.kt:828-848` |
| 搜索特征读时自愈 | `KnowledgeSearchIndexBuilder.kt:1-60`；`KnowledgeSearchIndexCompleteness.kt:1-40` |
| 随包知识内容来源 | `BundledKnowledgePackResources.kt:43-98`、`:58-71` |
| 全量重放/账本缺口语义 | `StudyProjectionDrainer.kt:108-125`、`:208-224`；`LearningProjectionPort.kt:150-164` |
| learner 身份常量 | `RoomBackedStudyExperienceRepository.kt:946` |
| 孤儿回收与宽限期 | `AndroidBackupRepository.kt:74-114`；`SmartMistakeBookApplication.kt:422`；`SecondaryScreens.kt:644` |
| 备份/恢复 UI 现状 | `SecondaryScreens.kt:623-870`（`:828-830` 文案） |
| 既有设备用例（25 个） | `BackupRestoreInstrumentedTest.kt`（`:121`、`:267`、`:391`、`:1282` …） |
| `model_task` 各 kind 的 subject 域 | `ModelTasks.kt:209-210`、`:277-278`、`:314-315`；`ProblemOrganizationTasks.kt:108-109`；`TutorLobbyTasks.kt:79-80`；`TutorTasks.kt:268-269`、`:487-488`、`:1102-1103`；`TutorConversationIds.kt:9-11`；`KnowledgeQuizTasks.kt:27-30`；`ModelEgress.kt:100-101`（无输入类的两个 kind） |
| 迁移期外键关闭 / Room 在 onOpen 开外键 | `FavoriteAndManualDraftMigration_66_67.kt:18-20`（他线在飞，只读）；`KernelWave0Migration_53_54.kt:63-66`；`RoomBackupSupportStore.kt:107-135` |
| 66→67 的 DDL 差异（不新增/不删 FK 边） | `FavoriteAndManualDraftMigration_66_67.kt:46-48`（加列）、`:54-75`（`problem_draft` 重建，FK 逐字不变、仅 `source_asset_id` 变可空）、`:78-142`（视图加列） |
| schema 事实（84 表 / 2 视图 / 90 条 FK 边） | `core/database/schemas/…/66.json`（机器导出，identityHash `f1cf3cdf…`）；`67.json` 尚未导出（`ls` 实测，只有 6–66 共 66 份） |


## 8. 2026-10-19 产品纠错与备份前置补充

> schema 69 的清单重算、共享 operation/attempt 行规则与 §7 用例展开见 §9。

先完成[PRD-07](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md)的KD-53/54/47，再扩内容勾选。现有VACUUM方案不能长期占Room写连接；旧API fallback必须有真实写者屏障与checkpoint结果核验，不能仅靠“空闲”注释。独立连接/增量备份的支持度以实际驱动/设备证据为准。

- [ ] PRD-02/03新增题/键版本、纠正/撤销事件、源引用和待重放回执后，按实际schema重新枚举FK与软引用。学习记录保留其必要键/题版本/目标证据，不能勾掉会话后把有效证据来源一起丢失。
- [ ] 纠正中/重放中备份恢复要保持持久回执与一致有效视图，恢复后本地续处理但不自动发送外部请求；删会话保题与两方法共享图继续沿第7节闭包规则。
- [ ] 本文旧schema66机器附录/SQL是历史研究证据，不改成新schema已验；实际迁移落地后另出新版清单与往返报告。

## 9. schema 69 机器清单重算（T8，2026-10-21）

> 状态：**只写文档，未实现**。§7 的 5 个复选项仍未勾；本文不声称 6C-2 已落地、已通过门禁或已在设备上验证。
> 上游：计划 `.zcode/plans/plan-2026-10-21-batch-17-wg08-wg06-wg07.md:87-89`、批次 17 基线
> `docs/research/2026-10-22-batch-17-baseline.md:151-156`、T1a 交付的 schema 69。
> 本节取代附录 A 作为**当前**机器清单；附录 A 保留为 schema 66 的历史研究证据，实施不得再按它写代码。

### 9.0 结论摘要

1. schema 69 是 **86 表 / 2 视图 / 92 条 FK 边**（66 与 68 均为 84 / 2 / 90）。新增
   `figure_generation_operation`、`figure_generation_attempt` 两表，新增
   `tutor_message_source_asset.role`、`tutor_message_source_asset.basis_revision_id`、
   `clean_redraw_task.operation_id`、`problem_revision_source_asset.ordinal` 四列。
   证据：`69.json`（version 69，identityHash `6633b7c2fe223041c4a2927203853f4f`）；
   `FigureGenerationMigration_68_69.kt:35-49`。
2. 84 张旧表的类别**不变**（ISSUE_BOOK 30 / MASTERY 35 / SESSIONS 6 / IMAGES 1 / ALWAYS 7 /
   DERIVED 2 / SPLIT 3）。两张新表加入 SPLIT，成为**行级 split 家族**：不新设类别，也不整表归
   SESSIONS 或 IMAGES。86 张表全覆盖、无重复（§9.2、§9.12）。
3. 类别闭包仍是 **5 条边**，未变。新边 `attempt→operation` 是家族内边；新边
   `attempt→canonical_source_asset` 是行级边，且 attempt 的行类别只能落在 ISSUE_BOOK 或 SESSIONS，
   两者本来都蕴含 IMAGES（§9.3）。
4. 软引用清单从 38 条收缩为 **20 条**：删除 4 条“其实已是硬 FK / 空 target”的旧条目，
   新增 3 条 69 新列/新表条目、补列 1 条旧漏项，并把检查口径从“**悬空必须 0 行**”改为
   “**裁剪后悬空集合 ⊆ 裁剪前悬空集合**”。这不是放宽：删会话路径
   `TutorConversationDao.kt:570-580` 只删消息与会话，`model_task`、`learner_chat_evidence` 等
   历史软引用本来就会悬空；零行检查会拒绝合法用户的备份（§9.4）。
5. 共享 operation/attempt 采用**两级行级筛选**：先按宿主类别取种子（`source_kind`），再把
   “产出资产仍被存活根引用”的 operation/attempt 拉回；attempt 的 `output_asset_id` 会钉住资产，
   成为**第 5 个资产引用站点**。全选跳过行筛选；真子集遇未知 `source_kind` fail-closed（§9.5）。
6. 裁剪顺序 **78 项**（旧 76 + 2），在 `problem_draft` 与 `canonical_source_asset` 之间插入
   attempt→operation。旧 76 项在 69 的 FK 图上仍是合法拓扑序（0 违规）；`canonical_source_asset`
   移到第 50 位，仍早于 `problem_revision`(73) / `problem`(74)（§9.6）。
7. `closureVersion` 1→2；`closureDigest` 前缀改为 `smbk-selection-v2`，`rowCounts` 覆盖 86 表；
   给出空库全选 golden `d6ec73ac…`。当前 `SmbkArchiveCodec.FORMAT_VERSION=2`（`:78`），
   当前写侧 `FORMAT_VERSION=2`，v3 选择包只存在于 §5.1 草案、未实现过 v3 写侧，因此不存在需要兼容的
   v1 选择包（§9.7）。
8. §7 的七类用例已展开为 C1–C7，外加 C8 横切检查，全部标注“设计用例，未执行”。其中 JS、
   预览、未知程序版本因 schema 69 没有对应表，只能作为前向设计，不能声称已覆盖（§9.8）。
9. 三项待用户裁定（mask=8 仅图片、mask=0 空选择、派生索引恒裁）**仍未裁定**；机器清单用
   `OPEN` 标记，不选默认、不代签。§6.1 其余 5 项同样未决（§9.9）。
10. 工作树已有 T1b/T5 在飞改动（audit recorder、`sourceKind`、五站点资产引用），但未提交、未跑
    门禁；T2a 保存复制仍需核对。6C-2 启用裁剪前，必须把这些在飞改动与 §9.5 的行规则对齐并跑门禁；
    本清单不把在飞代码当已验收（§9.10）。

### 9.1 schema 69 的事实

| 项 | 66 / 68 | 69 | 证据 |
| --- | --- | --- | --- |
| 表（Room entity） | 84 | 86 | `69.json` `entities` |
| 视图 | 2（`library_catalog`、`knowledge_question_lattice`） | 同左 | `69.json` `views` |
| FK 边 | 90 | 92 | `69.json` 逐表 `foreignKeys` |
| identityHash | 66 `f1cf3cdf…` / 68 `607250e5…` | `6633b7c2fe223041c4a2927203853f4f` | `69.json` |
| 新增表 | — | `figure_generation_operation`、`figure_generation_attempt` | 迁移 `:52-88` |
| 新增列 | — | 上述四列 | 迁移 `:38-41`、`:108-132` |

新增表的约束（DDL 见 `FigureGenerationMigration_68_69.kt:52-102`；T1a 自报与 `69.json` 逐字一致，
本轮未跑 T1a 的契约测试）：

| 表 | 主键 / 唯一 | 外键 | 索引 |
| --- | --- | --- | --- |
| `figure_generation_operation` | `operation_id` PK | 无（`source_kind/source_id` 是多态弱引用，故意不建 FK） | `(source_kind, source_id)`、`(status, updated_at_epoch_millis)` |
| `figure_generation_attempt` | `attempt_id` PK；`(operation_id, attempt_ordinal)` UNIQUE | `operation_id → figure_generation_operation` RESTRICT；`output_asset_id → canonical_source_asset` RESTRICT | `output_asset_id` |

四列的旧行语义（迁移不回填、不猜）：

- `tutor_message_source_asset.role`：NULL = 旧行角色未知；CLEAN_IMAGE / TUTOR_EXPLANATION /
  SOURCE_EVIDENCE 由 T1b/T2a 写。
- `tutor_message_source_asset.basis_revision_id`：NULL = 旧行没有冻结 basis。
- `clean_redraw_task.operation_id`：NULL = 历史净化作业没有生成操作台账。
- `problem_revision_source_asset.ordinal`：NOT NULL DEFAULT 0；旧行 = 单一源图第 0 张。

视图不参与裁剪：它们没有行，DDL 也不动；裁剪后基表仍存在（可能为空），视图继续可查询。
`room_master_table` 与 `sqlite_sequence` 同样不动（沿用 §2.4-6 的 v1 约束）。

### 9.2 类别（v2）

84 张旧表的类别逐字沿用 v1 附录 A（覆盖校验见 §9.12）；新增两张表进 SPLIT。完整表清单见 §9.11
的机器块，这里只给口径与计数：

| 类别 | 位 | 表数 | v2 变化 |
| --- | --- | --- | --- |
| ISSUE_BOOK 错题本 | 1 | 30 | 不变 |
| MASTERY 掌握库 | 2 | 35 | 不变 |
| SESSIONS 会话 | 4 | 6 | 不变 |
| IMAGES 图片 | 8 | 1（`canonical_source_asset`） | 不变；资产**行**策略见 §9.5 |
| ALWAYS 恒随包 | — | 7 | 不变 |
| DERIVED 恒裁/待裁定 | — | 2 | 是否恒裁仍是三项待裁定之一（§9.9-③） |
| SPLIT 按行 | — | 5（旧 3 + 新 2） | 新增 `figure_generation_attempt`、`figure_generation_operation` |

`figure_generation_operation` 的归属按 `source_kind` 决定：

| `source_kind` | 类别 | 当前写入者 / `source_id` 实际值 | 状态 |
| --- | --- | --- | --- |
| `TUTOR_MESSAGE` | SESSIONS | T1b 在飞：`parentOperationId ?: operationId`（`TutorFigureGenerator.kt:375`、`:391-392`）、`parentRequestId`（`AttachedImageGenerator.kt:182`）、`parent.requestId`（`DefaultFigureGenerationCoordinator.kt:504-514` 旧调用回退）。**不保证能反查到 `tutor_message` 行** | 在飞（未提交 / 未跑门禁） |
| `PROBLEM_REVISION` | ISSUE_BOOK | 枚举已存在（`FigureGeneration.kt:67-79`）；**生产无写入者**（只有测试构造）；意图是 revision id | 保留 / 未验证 |
| `CLEAN_REDRAW` | ISSUE_BOOK | T1b 在飞：父为 `CleanRedrawTask` 时 `taskId`；父为 `ModelTask` 时 `parent.requestId`（`ConfiguredCleanImageGenerator.kt:110-113`） | 在飞（未提交 / 未跑门禁） |
| `CAPTURE_PARSE` | ISSUE_BOOK | 枚举已存在；**生产无写入者**；意图是采集解析的草稿/作业 id | 保留 / 未验证 |

为什么不能整表归 SESSIONS 或 IMAGES：一条 `TUTOR_MESSAGE` 来源的 operation 完全可能产出被保存题
revision 引用的干净图；mask=9（错题本+图片，不含会话）删会话时，这条 operation 必须因为
“产物仍被存活题目引用”而保留（§9.5）。整表归 SESSIONS 会把它删掉、丢失来源；整表归 IMAGES 又会
把已删会话的生成历史带进“只要图片”的包。两者都违反“按存活宿主与引用分行”。

这张表的重点是**类别**，不是 `source_id` 的行语义。§9.5 的 O_seed 只看 `source_kind`，**不反查**
`source_id` 指向哪一行；机器块把 `source_id` 的软引用标为 `informational`，原因就在这里。工作树
已有 T1b 在飞改动（新 `FigureGenerationAuditRecorder.kt` / `RoomFigureGenerationAuditRecorder.kt`、
修改 `DefaultFigureGenerationCoordinator.kt`、新增 `FigureGenerationSourceKind`），本轨只读、未跑
门禁，不能把它们当成已验收事实。

`PROBLEM_REVISION` / `CAPTURE_PARSE` 的枚举已存在，但生产入口没有写入者（只有测试构造）；
`ModelEgress.kt:99-103` 同样把 `PROBLEM_RELATE` / `TUTOR_EVALUATE` / `REVIEW_RERANK` 标为无出网映射。
这两项在机器块里标 `RESERVED` / `verified:false`，不得当成已被真实数据验证的映射。

### 9.3 FK 闭包（v2）

推导规则不变：**外键子表被选中 ⇒ 父表类别必须被选中**；同类边、指向 ALWAYS 的边不计。
把 69 的 92 条边按“表→类别”折叠后，去掉同类/ALWAYS 边，剩下的仍是 5 条：

```
ISSUE_BOOK ⇒ IMAGES
SESSIONS   ⇒ ISSUE_BOOK
SESSIONS   ⇒ IMAGES
MASTERY    ⇒ ISSUE_BOOK
MASTERY    ⇒ SESSIONS
```

传递闭包：`MASTERY` 仍等于全选（MASTERY⇒SESSIONS⇒ISSUE_BOOK⇒IMAGES）。新增的两条边不改变它：

| 新边 | 类型 | 为什么不新增类别边 |
| --- | --- | --- |
| `figure_generation_attempt.operation_id → figure_generation_operation.operation_id` | 家族内 RESTRICT | 父子同属一个 split 家族，按同一条行规则同进同退 |
| `figure_generation_attempt.output_asset_id → canonical_source_asset.source_asset_id` | 行级 RESTRICT | attempt 的行类别只能是 ISSUE_BOOK 或 SESSIONS；两者都已蕴含 IMAGES。若未来新增一个不蕴含 IMAGES 的 `source_kind` 类别，闭包校验必须先红 |

这四列都没有新增 FK：`role`/`ordinal` 不是引用；`basis_revision_id`、`operation_id` 是软引用
（§9.4）。因此“新增 FK 边 ⇒ 重算闭包”的规则在 69 上只产生上述两条边，5 条类别边不变。

机器块的 `rowLevelEdges` 还列了 model_task 家族原有的三条 split 边：`model_task_event →
model_task`、`model_task → model_task_operation`、`tutor_answer_exposure → model_task`。它们被类别
折叠排除，但仍是硬 FK；前两条由同一 `task_kind` 谓词三级删除保证，第三条的正常 kind 是
`TUTOR_*`（SESSIONS），与 exposure 同类同删。若出现异常 kind，I1 的 `foreign_key_check` 会兜底，
不会静默产出悬空库。

组合合法性由闭包直接给出（枚举全部 16 个 mask 的校验见 §9.12）：

- 闭包自洽：`0`、`8`、`9`、`13`、`15`。
- 非法（子类别缺父类别）：`1`–`7`、`10`–`12`、`14`；仓储层必须拒绝，不静默补齐。
- `8` 与 `0` 虽然闭包自洽，但属于三项待裁定（§9.9-①/②），机器块标 `OPEN`，不是“已允许”。

### 9.4 软引用（v2）

v1 的 38 条清单有几条已经与 66 之后的 schema 漂开；v2 先做**范围收缩**，再做**口径修正**：

- 只列**跨类别、跨 split 家族、指向资产、指向 ALWAYS、或需要 FTS 对齐**的引用。
- 同类别内部引用（例如 `clean_redraw_task.revision_id → problem_revision`、`review_queue_item →
  review_queue`）随整个类别同删同留，不逐条校验；旧清单里的这类条目只作历史样本。
- `opaqueColumns` 明确列出“名字像引用、其实不是行引用”的列，避免下一个人再把它塞回软引用表。

因此 38→20 **不是** 38−4+3 的算术：同类别内部条目整体退出校验清单，下面 4 条删除与 3 条新增是
**边界条目**的增减；完整 20 条见 §9.11。

#### 9.4.1 删除的旧条目（4 条）

| 旧条目 | 为什么删 | 证据 |
| --- | --- | --- |
| `knowledge_grounding_resolution.knowledge_node_id → knowledge_node` | 69 里已是硬 FK | `69.json` 该表 `foreignKeys` |
| `attempt_event.submission_id → attempt_submission` | 已是硬 FK | 同上 |
| `assessment_evidence_attribution.knowledge_node_id → knowledge_node` | 它是复合 FK 的一列（→ `practice_unit_knowledge_binding`），不是独立软引用 | 同上 |
| `review_queue_item.item_family_id → ""` | 空 target；实际值是 `ITEM_FAMILY_ID_PREFIX + practice_unit_id`，是家族令牌不是行主键 | `StudyPracticeUnitFacts.kt:121` |

`library_search_fts.docid` 保留，但标 `virtual:true`：它是 FTS4 外部内容表的虚拟列，schema 实体
字段里没有它；检查方式是 rebuild 之后 `docid` 孤儿数为 0，而不是一般软引用 SQL。

#### 9.4.2 新增 / 重算的条目（3 条）

| 条目 | 类别 | 强度 | 说明 |
| --- | --- | --- | --- |
| `tutor_message_source_asset.basis_revision_id → problem_revision` | cross-category（SESSIONS⇒ISSUE_BOOK，已闭包） | strong | 69 新列；NULL=旧行未知；非空且源行存活时必须可解析 |
| `clean_redraw_task.operation_id → figure_generation_operation` | split-family | strong | 69 新列；NULL=历史作业；非空且 task 存活时必须可解析（§9.5 的 O_seed 会保住它） |
| `figure_generation_operation.source_id → *source-hint*` | split-family | **informational / weak-host-hint** | 只按 `source_kind` 分类；**不保证 `source_id` 指向宿主行**（T1b 在飞值是父操作/请求 id，§9.2）；共享图保留时来源行可能已删，不能算悬空失败；真子集遇未知 `source_kind` fail-closed |

补列：`clean_redraw_task.subject_id → problem_draft`（旧清单漏列；`CleanRedrawTaskRunner.kt:28` 说明它
是提交路径的草稿 id；same-category / weak-host-hint，草稿提交后允许悬空）。它不随 69 新增，但属于
“名字像引用”的真实列。

另外两条旧的 `model_task.subject_id` / `model_task_operation.subject_id` 从“cross-category”改标
`by-kind`：TUTOR_* 行的目标是 SESSIONS（同类），CAPTURE/PROBLEM 行的目标是 ISSUE_BOOK（同类），
`IMAGE_PIPELINE_CLASSIFY` 的目标可能是资产（IMAGES，ISSUE_BOOK 已蕴含），`KNOWLEDGE_QUIZ` /
`REVIEW_RERANK` 的目标是 ALWAYS。它们不是单一类别边，不能用一个 `cross-category` 标签糊过去。

#### 9.4.3 检查口径：no-new-dangling，而不是 0 行

v1 要求每条检查 SQL 返回 0 行。69 之后这条口径会**拒绝合法备份**，证据是删会话路径：

`TutorConversationDao.deleteConversation`（`:570-580`）只做 `deleteMessages` + `deleteConversationRow`。
`model_task`、`learner_chat_evidence`、`tutor_answer_exposure` 等没有指向 `tutor_conversation` 的
级联外键，删会话后它们的 `subject_id` / `conversation_id` / `session_id` 会留在库里悬空。
用户删过会话再备份，0 行检查会失败——但数据并没有被裁剪破坏，它只是本来就带着历史悬空。

v2 的 I4 因此改为：

1. 在任何 DELETE 之前，对每条 `check=no-new-dangling` 的软引用跑一次，记下悬空的**源行主键集合**
   `D_pre`（按 check id 分组）。
2. 裁剪后再跑一次，得 `D_post`。
3. 断言 `D_post ⊆ D_pre`（只允许减少或不变，不允许新增）。
4. `figure_generation_operation.source_id` 不参与这个断言（`check=informational`）：共享图保留时
   来源行可能已删（且 `source_id` 本来就未必是行主键），这是行级规则的预期结果。
5. `library_search_fts.docid` 走 rebuild 后的孤儿数核对；`always` 引用只作说明（目标恒在）。

这条口径消灭的具体失败：**删过会话的用户无法再创建子集备份**。它仍然抓住真正的裁剪损坏：
如果裁剪把某个选中类别的目标行删掉、导致源行新悬空，`D_post` 会多出 `D_pre` 里没有的键，成包失败。

恢复侧（S3）不能重放 `D_pre`，因此只做**结构性**检查：闭包、硬 FK、以及 §9.5 的 figure 行规则
可完全重算；同类别历史悬空只报诊断，不拒绝。残余风险：第三方重裁如果只新增同类别悬空、且行数
不变，`closureDigest` 抓不到；这是 v2 已知的限制，登记在 §9.10，不用一个未实现的摘要字段假装解决。

### 9.5 共享 operation / attempt 的行级规则

#### 9.5.1 为什么必须按行

一张 `figure_generation_operation` 没有指向宿主的外键，只有 `(source_kind, source_id)` 弱引用。
同一条 operation 的产物资产可能同时被：

- 一条 `tutor_message_source_asset`（SESSIONS，消息附图 / 讲解图）引用；
- 一条 `problem_revision_source_asset`（ISSUE_BOOK，保存后的题面/干净图）引用。

mask=9 删 SESSIONS 时，消息链接被删，但题面 revision 的链接还在。如果按整表 SESSIONS 删
operation/attempt，就会出现“题目还引用着资产，但产出它的操作台账没了”；如果按整表 IMAGES 保留，
又会把已删会话的所有生成历史带进包。所以必须按**存活宿主 + 存活引用**逐行决定。

这里的“宿主存活”按 `source_kind` 的**类别**是否被选中判定，**不按 `source_id` 反查行**：T1b 在飞
写入的 `source_id` 是父操作/请求 id，不保证能反查到消息/草稿行（§9.2）。引用侧才是真正的行级判据：
`output_asset_id` 是否仍被存活根引用（O_ref）。

#### 9.5.2 算法（与 §9.11 的 `figureRowFilter.steps` 同一份事实）

在**同一个快照事务**内、任何 DELETE 之前：

1. **根资产集 R**：按选择读四张根引用表——
   `problem_revision_source_asset`、`problem_draft_source_asset`、`problem_draft.source_asset_id`
   （仅当 ISSUE_BOOK 选中）；`tutor_message_source_asset`（仅当 SESSIONS 选中）。
   `IMAGES` 选中但题/会话未选中时 R 为空。
2. **种子 operation O_seed**：
   - SESSIONS 选中 ⇒ `source_kind='TUTOR_MESSAGE'` 的全部 operation；
   - ISSUE_BOOK 选中 ⇒ `source_kind IN ('PROBLEM_REVISION','CLEAN_REDRAW','CAPTURE_PARSE')` 的全部 operation；
   - 再加存活 `clean_redraw_task.operation_id` 指向的 operation（即使 source_kind 与预期不一致，也保住）。
   - O_seed **只看 `source_kind` 的类别，不反查 `source_id` 的宿主行**：T1b 在飞实现的 `source_id` 是
     `parentOperationId` / `parentRequestId` / `operationId` 一类父操作身份，不是统一的行主键
     （§9.2）。把“宿主行存在”当种子条件会误删合法操作。
3. **被引用 operation O_ref**：`output_asset_id ∈ R` 的 attempt 所属 operation。它们可能来自未选中的
   宿主类别（被删会话/被删草稿），因为图仍被保存题引用。
4. `O_keep = O_seed ∪ O_ref`。
5. `A_keep` = `operation_id ∈ O_keep` 的**全部** attempt，外加 `output_asset_id ∈ R` 的 attempt
   （后者通常已被前者覆盖，显式写出是为了不依赖顺序）。
6. `P = R ∪ A_keep 的非空 output_asset_id`。这是打包资产集，也是第 5 个资产引用站点。
7. 先 `DELETE figure_generation_attempt WHERE attempt_id ∉ A_keep`，再
   `DELETE figure_generation_operation WHERE operation_id ∉ O_keep`。

为什么 `A_keep` 取“O_keep 的全部 attempt”而不是只留被引用的那一条：attempt 行是 operation 的
审计历史；保留一条、删掉同 operation 的其他 attempt 会让 `used_remote_dispatches` 这类审计镜像
与行数对不上。全留的代价是这些 attempt 的 `output_asset_id` 都要进 P——这与 T5 的 GC 口径
（`attempt.output_asset_id` 钉住资产）一致，不是额外扩张。

#### 9.5.3 SQL 形状（参数绑定，不拼串）

```sql
-- 预扫：必须在第 1 条 DELETE 之前执行
CREATE TEMP TABLE kept_asset(asset_id TEXT PRIMARY KEY);
INSERT INTO kept_asset
SELECT source_asset_id FROM problem_revision_source_asset WHERE :issue_book = 1
UNION SELECT source_asset_id FROM problem_draft_source_asset WHERE :issue_book = 1
UNION SELECT source_asset_id FROM problem_draft
      WHERE :issue_book = 1 AND source_asset_id IS NOT NULL
UNION SELECT source_asset_id FROM tutor_message_source_asset WHERE :sessions = 1;

CREATE TEMP TABLE kept_operation(op_id TEXT PRIMARY KEY);
INSERT INTO kept_operation
SELECT operation_id FROM figure_generation_operation
 WHERE (:sessions = 1 AND source_kind = 'TUTOR_MESSAGE')
    OR (:issue_book = 1 AND source_kind IN
        ('PROBLEM_REVISION','CLEAN_REDRAW','CAPTURE_PARSE'));
INSERT OR IGNORE INTO kept_operation
SELECT o.operation_id FROM figure_generation_operation o
 WHERE EXISTS (SELECT 1 FROM figure_generation_attempt a
               JOIN kept_asset k ON k.asset_id = a.output_asset_id
               WHERE a.operation_id = o.operation_id);
INSERT OR IGNORE INTO kept_operation
SELECT operation_id FROM clean_redraw_task
 WHERE :issue_book = 1 AND operation_id IS NOT NULL;

CREATE TEMP TABLE kept_attempt(attempt_id TEXT PRIMARY KEY);
INSERT INTO kept_attempt
SELECT attempt_id FROM figure_generation_attempt
 WHERE operation_id IN (SELECT op_id FROM kept_operation)
    OR output_asset_id IN (SELECT asset_id FROM kept_asset);

-- 真子集：未知 kind 且未被共享引用拉回 → 失败并报出 operation_id（期望 0 行）
SELECT operation_id FROM figure_generation_operation
 WHERE source_kind NOT IN (:k1, :k2, :k3, :k4)
   AND operation_id NOT IN (SELECT op_id FROM kept_operation);

-- 到裁剪位置时
DELETE FROM figure_generation_attempt
 WHERE attempt_id NOT IN (SELECT attempt_id FROM kept_attempt);
DELETE FROM figure_generation_operation
 WHERE operation_id NOT IN (SELECT op_id FROM kept_operation);
```

`attempt_id` / `operation_id` 都是 NOT NULL 主键，`NOT IN` 不会遇到 NULL 语义陷阱。
未知 `source_kind`：真子集在预扫阶段，凡未知 kind 且**不在 O_keep** 的 operation → 直接失败并报出
`operation_id`，不猜、不整表归档；已被共享产物（O_ref）或存活 `clean_redraw_task.operation_id`
拉入 O_keep 的未知 kind → 保留（它是存活图的来源，不需要分类）；全选（mask=15）跳过行筛选，
未知 kind 原样保留。将来新增 kind 并写入映射时必须 bump `closureVersion`。

#### 9.5.4 资产第 5 站点（硬前置）

`figure_generation_attempt.output_asset_id` 是 RESTRICT 外键。只要 attempt 行在包里，资产行与
文件就必须在包里。已提交基线的资产引用口径是四表（`PendingCaptureDao.kt:30-61`）；工作树的 T5
在飞改动已把第五支 `figure_generation_attempt.output_asset_id` 加进
`findUnreferencedCanonicalAssets`（当前 `PendingCaptureDao.kt:31-78`，第五支在 `:71-74`），
`readReferencedCanonicalAssets` 仍取它的补集（`RoomStudyDatabase.kt:874-881`）。本轨只读、未跑测试。
6C-2 必须复用同一口径；若该在飞改动不随批次落地，6C-2 不得启用裁剪，否则会出现：

- 裁剪保留了 attempt（因为宿主或引用），但 `readReferencedCanonicalAssets` 看不到它；
- I5 按四表口径通过，成包成功；
- 恢复后的库里 attempt 指向的资产文件不在包内——图丢了，但没人报错。

机器块把 `assetPinRule.sites` 写成 5，并把“I5 与 readReferencedCanonicalAssets 必须同源”写进
`figureRowFilter.steps`。该在飞改动未随批次落地时，6C-2 不得启用裁剪。

#### 9.5.5 四个走查

| 场景 | R | O_seed | O_ref | 结果 |
| --- | --- | --- | --- | --- |
| 双根共享图：O(TUTOR_MESSAGE)→A，A 同时被消息 M 与 revision Rv 引用；mask=9 | A（revision 链接；消息链接被删前不计） | 不含 O（SESSIONS 未选） | 含 O（A∈R） | A+文件进包；O 及其全部 attempt 保留；消息链接删；revision 链接在；恢复详情有图 |
| 删会话后再备份：M 已删，A 仍被 Rv 引用；mask=9 | A | 不含 O | 含 O | O 保留；`source_id` 可能悬空或本就指向父请求，`check=informational`，不触发 no-new-dangling；A 进包 |
| 只在消息里出现、未保存到题的图；mask=9 | 不含 A | 不含 O | 不含 O | O/attempt 先删；canonical 行按 IMAGES 整表保留，文件不进包；恢复后由孤儿回收处理 |
| 全选 mask=15 | 不适用 | 不适用 | 不适用 | 跳过行筛选，整表保留（含未知 kind / JS 行） |

去重注意：`canonical_source_asset.content_sha256` 有唯一索引（`69.json`），同一资产可能被多次
attempt 产出、被多条根引用指向。O_ref 会把所有产出它的 operation 拉回，不能假设“一条资产只有一个
attempt”。

### 9.6 裁剪顺序（v2）

78 = 86 表 − 7 ALWAYS − 1 `library_search_fts`（虚拟表由 rebuild 收尾）。v2 的 78 项在 69 的 FK 图上
逐边校验 0 违规；旧 76 项是它的子序列、相对顺序未改。v2 只在 `problem_draft`(47) 与
`canonical_source_asset` 之间插入两项：

```
43 clean_redraw_task
47 problem_draft
48 figure_generation_attempt     ← 新增；必须在 operation 与 asset 之前
49 figure_generation_operation   ← 新增；attempt 的父
50 canonical_source_asset        ← 原 48，后移 2
51 batch_import_job              ← 原 49，后移 2
```

被后移的关键位置（旧 → 新）：`attempt_event` 63→65、`answer_reveal_outcome` 67→69、
`assessment_answer_reveal_event` 68→70、`assessment_evidence_snapshot` 69→71、`practice_unit` 70→72、
`problem_revision` 71→73、`problem` 72→74、`model_task` 58→60、`model_task_operation` 59→61。
v1 附录 B 的“最容易写错的四处”按新序号重写：

- `canonical_source_asset`(50) 早于 `problem_revision`(73)/`problem`(74) 是**对的**：引用它的 link 表
  与 draft 已先删；
- `answer_reveal_outcome`(69) 必须早于 `assessment_answer_reveal_event`(70)；
- `attempt_event`(65) 必须早于 `assessment_evidence_snapshot`(71)；
- `model_task`(60) 必须早于 `model_task_operation`(61)（后者是前者的父）。

新增两项的顺序理由：`attempt` 同时 RESTRICT 引用 `operation` 与 `canonical_source_asset`，所以必须
最先删；`operation` 只被 attempt 与 `clean_redraw_task.operation_id` 引用，放在 attempt 之后、asset
之前即可。`clean_redraw_task`(43) 早于 `operation`(49) 不构成悬空：若 task 存活，§9.5 的 O_seed 会把
operation 保住；若 task 被删，软引用随行消失。

预扫（§9.5.3）必须在第 1 条 DELETE（`tutor_message_source_asset`）之前完成，因为四张根引用表在
顺序里很靠前；不能等删到第 48 位再回头找“哪些资产原本被引用”。

`canonical_source_asset`：IMAGES 选中时跳过整表删除（v2 语义）；IMAGES 未选中（当前只有 mask=0，
待裁定）时整表删。`figure_generation_attempt/operation` 是**谓词删除**，不是整表删。
`model_task` 家族仍是按 kind 的同谓词三级删除，三处共用同一个 kind 集合（§2.4-7 不变）。
`library_search_fts` 不在顺序里；`ISSUE_BOOK` 未选中（mask 0/8）导致 `library_search_content` 被删时，
收尾执行 `INSERT INTO library_search_fts(library_search_fts) VALUES('rebuild')`。

### 9.7 closureVersion 与 digest（v2）

`closureVersion` 只在**规则表**变化时 bump，不是 `schemaVersion` 的别名。69 同时改变了表集合、
FK 边、软引用范围与行级规则，所以 1→2 是必须的；未来 schema 70 如果不动规则表，不必跟着 bump。

当前发布面：`SmbkArchiveCodec.FORMAT_VERSION = 2`（`:78`），`MIN_READABLE_FORMAT_VERSION = 1`
（`:81`）。v3 选择包只存在于 §5.1 的草案，写侧从未实现；因此**不存在已落盘的 closureVersion=1 选择包**，
不需要为 v1 保留读侧规则表。若将来读到 `formatVersion=3` 但 `closureVersion` 不是 2，按“规则版本
不认识”在交换前拒绝（与 `SmbkArchiveCodec.kt:893-896` 的格式版本闸门同一纪律）。

manifest 的 `selection` 块 v2：

```jsonc
"selection": {
  "schemaVersion": 2,              // 记录形状；v3 写侧未实现，直接升 2
  "categoryMask": 9,
  "categories": ["ISSUE_BOOK", "IMAGES"],
  "assetPolicy": "INCLUDED",
  "closureVersion": 2,
  "closureDigest": "sha256:…",
  "rowCounts": { "problem": 128, "…": 0 },   // 86 表全量；split 表为行级筛选后的行数
  "trimmedTableCount": 33                    // 删除行数 > 0 的表数；v2 上限 78
}
```

> v2 的合法 mask（9/13/15）都含 IMAGES，因此 `assetPolicy` 只能是 `INCLUDED`；`ABSENT`（无图子集）属
> §6.1-⑤ 未决，不在本清单，也不是 6C-2 的已实现能力。

`closureDigest` 算法（定稿 v2）：

```
digest = SHA-256( "smbk-selection-v2\n"
                + "closureVersion=2\n"
                + "categories=" + categories.sorted().joinToString(",") + "\n"
                + "assetPolicy=" + assetPolicy + "\n"
                + rowCounts.keys.sorted().joinToString("") { "$it=${rowCounts[it]}\n" } )
```

空库、全选、`assetPolicy=INCLUDED` 的 golden（86 表全 0）：

```
categories=IMAGES,ISSUE_BOOK,MASTERY,SESSIONS
SHA-256 = d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6
```

三项待裁定不改变这个 golden：空库里派生表也是 0 行，③ 的 a/b/c 都得到同一 rowCounts；①/② 只决定
mask=8/0 是否可创建，不改变算法。`closureDigest` 不是权威判据，权威始终是包内库实测（沿用
`SmbkArchiveCodec.kt:66-72` 的立场）；它只用于同构对拍与第三方重裁的粗筛。

`rowCounts` 必须覆盖全部 86 表，包括值为 0 的表；漏键会让摘要不可比。split 表（`model_task*`、
`figure_generation_*`）的计数是**行级筛选后**的实际行数，不是类别表总数。

### 9.8 §7 用例（设计用例，未执行）

以下用例全部是**设计**：本轮没有 Gradle、没有设备、没有真实备份往返，状态一律“未执行”。
依赖列写明需要哪条轨先落地；依赖未落地时，用例不可写成“通过”。

#### C1 混合 JS / IMAGE_MODEL

- 前置：T1b 在飞实现已写 operation/attempt（未提交、未跑门禁）。夹具在同一个 operation O
  （`source_kind=TUTOR_MESSAGE`）下放
  `attempt0 method=IMAGE_MODEL output=A1`、`attempt1 method=JS output=NULL`，可选
  `attempt2 method=JS output=A2`。
- 动作：mask=13 与 mask=15 各成包 → 恢复。
- 断言：`method` 不参与筛选，A_keep 含 O 的全部 attempt；P 含 A1（与 A2）；恢复/打开历史/导出
  **0 次模型派发**，不执行 JS；`model_task_operation.dispatch_count` / `clean_redraw_task.attempt_count`
  恢复前后不变；`method=JS` 且 output=NULL 的 attempt 不钉任何资产。
- 边界：schema 69 **没有** JS program/snapshot/preview 表（`docs/research/2026-10-21-4e-wg060708-schema-design.md:37-39`
  仍是预留），本用例只能验证
  `attempt.method` 的行级处理，不能声称 JS 运行、快照保真或未知程序版本已覆盖（见 C5-c）。

#### C2 双根共享图

- 前置：资产 A 由 O（`source_kind=TUTOR_MESSAGE`；`source_id` 不要求等于 M）产出；A 同时被
  `tutor_message_source_asset(M)` 与 `problem_revision_source_asset(Rv, role=CLEAN_IMAGE)` 引用。
- 动作：mask=9（错题本+图片，不含会话）成包 → 恢复到干净库。
- 断言：A 的行与文件都在包内；Rv 的链接在；M 的链接不在；O 及全部 attempt 保留（O_ref）；P 含 O 的
  全部 output；详情页能显示 A；五站点 GC 不删 A；恢复/详情/导出 0 派发。
- 负向：若没有 Rv 链接（legacy 只在消息里），A 不属于 R；mask=9 下 O/attempt 可被裁，包内不含 A，
  恢复后题目无图——这是“题没有拥有这张图”的如实结果，不是裁剪 bug。

#### C3 删会话保题

- 前置：从讲题会话保存题目（T2a 在飞/未验收；同事务把消息 visual link 复制到 revision），随后调用
  `deleteConversation`（只删消息与会话，`TutorConversationDao.kt:570-580`）。
- 动作：mask=9 与 mask=13 各成包 → 恢复。
- 断言：revision 链接与 A 存活；消息/消息链接已删；O 保留；`source_id` 可能悬空或本就指向父请求
  （informational，不参与 no-new-dangling）；mask=9 的包包含题目图；mask=13 的包包含题目图且不含
  已删会话；I4 `D_post ⊆ D_pre` 成立；恢复后详情/导出正常；0 派发。
- 同场断言：`model_task` / `learner_chat_evidence` / `tutor_answer_exposure` 的历史悬空是**删会话路径
  的既有结果**，不得让 I4 0 行检查阻断备份；它们也不因裁剪新增（§9.4.3）。

#### C4 不同 selection

- 闭包合法：mask=15（全选）、13（题+会话+图）、9（题+图）。逐一成包/恢复。
- 待裁定：mask=8（仅图）、0（空）只有在 §9.9-①/② 裁定允许后才可创建；未裁定前 `create()` 必须
  拒绝，不能“先做出来再说”。
- 非法：1–7、10–12、14 在 `BackupSelection` 构造/`create()` 拒绝，不产出文件，不静默补齐。
- 断言：每个 mask 的类别表 0 行/保行符合 §9.2；figure 行规则符合 §9.5；回执如实写出 mask/categories/
  assetPolicy；恢复替换语义的前置文案把“未包含的类别会被清空”说全；`rowCounts` 与裁剪后实测一致，
  `closureDigest` 可复算。
- 派生策略：③ 的 a/b/c 各跑一遍全选，只有 a/c 会裁派生表；b 的全选与 v2 等价（除新表外）。

#### C5 未知版本

- 5a：manifest `selection.closureVersion` ≠ 2、`selection.schemaVersion` 不认识、或 mask 不在允许集合
  → 交换前拒绝；原库可读，没有 generation 产物。
- 5b：真子集里出现未知 `figure_generation_operation.source_kind` → 成包前失败并报 `operation_id`；
  mask=15 全选原样保留，不分类、不删。
- 5c（前向）：未来 JS 程序版本未知时，只用**已验证快照/文字**只读呈现，不执行源码、不联网补图；
  若程序/快照/字体缺失且该预览是唯一渲染事实 → 交换前拒绝；若预览只是可重建缓存 → 丢弃并重建。
  schema 69 没有 program/snapshot 表，本项现在**无法执行**，只是给 4E.2/4E.3 的约束。
- 5d：v1/v2 旧包没有 `selection` 块 → 读作全量，行为逐字不变（`MIN_READABLE_FORMAT_VERSION=1`）。

#### C6 缺预览

- 69 代理用例：被存活链接引用的 canonical 资产文件缺失或 SHA 不符 → 成包/恢复在交换前失败，报出
  缺失的 asset id；绝不产出“成功但少图”的包（复用现有 `resolveReferencedAssetFiles` 与
  `verifyStagedAssetClosure`）。
- 前向 JS 用例：预览若是唯一渲染事实，缺失/哈希不符 = 拒绝；若 program + snapshot + font 齐全、
  预览只是可重建派生缓存，则丢弃并重建，不把“预览缺失”写成完整成功。依据是 4E 计划
  `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:129-134`：预览走 canonical
  vault，必需字节缺失拒绝完整包，未知版本只读已验证快照/文字、不执行源码。
- 断言：没有半份包、没有假成功；恢复后不会用一张错图/旧图冒充。

#### C7 源 revision 改变

- 前置：revision R1 有图 A（`tutor_message_source_asset.basis_revision_id=R1` 或保存复制后的 revision
  链接）；题目编辑到 R2；R1 的晚到结果在 R2 之后返回（T3 的显式重绘/晚到路径）。
- 动作：确认晚到结果被标 `SUPERSEDED` / 不挂到 R2；mask=9 成包 → 恢复。
- 断言：R1 的链接仍以 R1 为 basis；R2 没有旧图；operation 的 `SUPERSEDED` 与 attempt 的终态
  （SUCCEEDED/FAILED/CANCELLED）原样保留；
  恢复/详情/导出 0 派发；账本计数不变；导出/详情按当前 revision 的 role 过滤，不把 R1 的图挂到 R2。
- 负向：若晚到结果覆盖了新 revision，或用 `expectedRevisionId` 失配仍挂接，本用例红（属 T3/WG-06，
  不是 6C-2 单独能修的）。

#### C8 横切：资产 / 账本 / 授权

- 被保留 attempt 的 output 缺文件 → 交换前拒绝；五站点引用集合与包内 `assets/` 双向相等。
- 恢复后 `model_task_operation.dispatch_count`、`clean_redraw_task.attempt_count` 不清零；不复活
  外发授权/租约/sleep journal（这些本来就不随包，见 §4.1）。
- 打开详情、展开、旋转、导出、恢复本身 **0 次模型派发**；真实 Provider 仍 UNVERIFIED。

### 9.9 三项待用户裁定（本轨不代签）

| # | 待裁定 | 选项 | 影响 / 证据 | 机器清单的当前状态 |
| --- | --- | --- | --- | --- |
| ① | 是否提供“仅图片”组合（mask=8） | a) UI 不提供；b) 提供并提示 | b) 的包内资产没有任何引用者；当前 `create()` 只收 `readReferencedCanonicalAssets()`（`AndroidBackupRepository.kt:218-220`），mask=8 下引用集合为空，包内 `assets/` 也会为空——要真正导图必须新增“无引用资产也收”的 assetPolicy；即使收了，恢复后 `cleanupOrphanAssets` 也会按 30 分钟宽限把旧创建时间的资产清掉（`AndroidBackupRepository.kt:123`、`:148-160`；启动 enqueue `SmartMistakeBookApplication.kt:475`）。产物是“恢复完就蒸发” | `maskPolicy.openMasks` 标 `OPEN`；闭包自洽不等于允许；空 assets 策略属 ① 的裁定范围 |
| ② | 是否允许空选择（mask=0） | a) 禁止（至少勾一项）；b) 允许 | b) 的产物是重置包：恢复后本机只剩知识层与设置，其余全部被替换 | 同上，`OPEN` |
| ③ | 派生索引（`knowledge_search_feature` + `knowledge_search_index_state`）是否恒裁 | a) 恒裁 + 恢复后读时自愈；b) 全选时保留；c) 恒裁 + 恢复后后台重建 | a) 包最小，首次召回某科可能现场整科重建（v1 研究记录过 16.6s 级长尾）；b) 全选包接近 v2 大小（旧测 623,774 行特征）；c) 恢复回执后异步重建，用户无感 | `derivedPolicy.decision = OPEN`，三选项全列，不选默认 |

**这三点必须由用户裁定，实现不得默认任一分支**。本节的机器清单只把三种状态表达清楚：
①/② 决定 `create()` 是否接受 mask 8/0；③ 决定全选时是否删两张派生表（从而改变 rowCounts /
trimmedTableCount / digest），但不改变类别、FK、软引用与顺序规则。

§6.1 实际列了 8 条待裁定；按批次 17 基线 `:156`，T8 只点名上述三项，**其余 5 项（类别文案、
无图子集、治理台账归类、合并恢复、VACUUM 耗时）保持未决**，本轨不代签。

### 9.10 未验证与风险

1. **T1b 在飞（工作树，未提交/未跑门禁）**：已见 `FigureGenerationAuditRecorder.kt`、
   `RoomFigureGenerationAuditRecorder.kt`、`DefaultFigureGenerationCoordinator.kt` 修改与
   `FigureGenerationSourceKind`；本轨只读，未验收。recorder 默认是 `Noop`，生产是否真的注入由 I1
   装配决定，未验证。`source_id` 不是统一宿主行主键（§9.2），所以行规则只按 `source_kind` 分类。
   两张新表在迁移后为空，真实行尚未观测；`PROBLEM_REVISION` / `CAPTURE_PARSE` 没有生产写入者，
   标 `RESERVED` / `verified:false`。
2. **T5 在飞（工作树，未提交/未跑测试）**：`PendingCaptureDao.kt` 的
   `findUnreferencedCanonicalAssets` 已加第五支（`:71-74`），`AndroidBackupRepository` 的 KDoc 已改口径；
   本轨只读，未跑测试。若该改动不随批次落地，启用裁剪会漏收被 attempt 钉住的图（§9.5.4）。
3. **T2a 在飞/未验收**（工作树 `RoomTutorToolRunner.kt` 有改动）：保存时复制消息 visual link 到
   revision 必须是同事务规则；没有它，mask=9 会丢掉只存在于消息里的图。旧数据没有复制过的图按
   “题没有拥有它”处理，不伪造。
4. **JS / 预览 / 未知程序版本**：schema 69 没有 program/snapshot/preview 表；C1 只能测 `method` 行，
   C5-c/C6 只能作为前向设计。JS 运行库、4E.2/4E.3、真实质量门仍后置。
5. **删会话的既有悬空**：`TutorConversationDao.kt:570-580` 不清 `model_task`、`learner_chat_evidence`、
   `tutor_answer_exposure` 等；v2 用 no-new-dangling 放行历史悬空。恢复侧无法区分“历史悬空”与
   “第三方重裁新增的同类别悬空”，这是已知限制（§9.4.3）。
6. **T1a KDoc 的 purpose 漂移**：`FigureGenerationEntities.kt:29-31` 列了 `QUESTION_DIAGRAM`，
   `core:model` 的 `FigureGenerationPurpose`（`FigureGeneration.kt:42-57`）没有该值，实际是
   SOURCE_TRANSCRIPTION / SOURCE_RECONSTRUCTION / EXPLANATION / ILLUSTRATION / PIXEL_CLEANUP。
   本清单不按 purpose 分类，故不影响闭包；T1b 落盘前需对齐，别把 KDoc 当枚举。
7. **未跑 Gradle/设备**：本轮没有编译、单测、androidTest、迁移矩阵或真实备份往返；§9.12 只是
   对 `69.json` 与本节 JSON 的静态/脚本一致性检查。schema 69 在真机上的迁移行为以 T1a 的
   `FullMigrationMatrixInstrumentedTest` 与批次 17 的 D1 为准。
8. **IMAGES 行策略**：选中 IMAGES 时 canonical 行整表保留、包内只收 P；未进入 P 的未引用行随包保留
   但不带文件，恢复后由既有孤儿回收处理。这是 v2 语义，不是本次新增的产品选项；仍应设备复核
   “恢复后未引用行不会让详情/导出误判”。
9. **视图**：`library_catalog`、`knowledge_question_lattice` 不参与裁剪；裁剪后基表仍存在（空表也
   存在），视图可继续查询。未在设备上验证视图在裁剪库上的行为。
10. **三项待裁定**：未裁定前，mask 8/0 与派生策略都不能当成默认；任何“先按 a 实现”的代码都违反
    §9.9。

### 9.11 机器可读清单 v2

> 解析标记：`<!-- 6c2-plan-v2:begin -->` / `<!-- 6c2-plan-v2:end -->`。旧的 `6c2-plan` 标记仍是
> schema 66 历史块，当前实现只读 v2。`closureVersion=2` 与本块同源；`identityHash` 必须等于
> `69.json` 的 `database.identityHash`。

<!-- 6c2-plan-v2:begin -->
```json
{
  "closureVersion": 2,
  "schemaVersion": 69,
  "identityHash": "6633b7c2fe223041c4a2927203853f4f",
  "note": "v2（schema 69）：84 张旧表类别不变；新增 figure_generation_operation/attempt 为行级 split 家族；闭包规则见 §9.3，行级规则见 §9.5。",
  "categories": {
    "ISSUE_BOOK": {
      "bit": 1,
      "label": "错题本",
      "tables": [
        "problem",
        "problem_revision",
        "practice_unit",
        "error_book_entry",
        "problem_relation",
        "problem_classification_binding",
        "problem_classification_candidate",
        "problem_organization_receipt",
        "practice_unit_knowledge_binding",
        "problem_revision_source_asset",
        "problem_draft",
        "problem_draft_source_asset",
        "problem_draft_revision",
        "problem_draft_edit_snapshot",
        "problem_draft_commit_receipt",
        "batch_import_job",
        "batch_import_page",
        "split_import_job",
        "split_import_question",
        "clean_redraw_task",
        "library_search_content",
        "library_search_fts",
        "library_search_outbox",
        "knowledge_grounding_request",
        "knowledge_grounding_resolution",
        "knowledge_research_review_bundle",
        "knowledge_research_review_source",
        "mistake_export_record",
        "binding_audit_sample",
        "llm_teaching_advisory"
      ]
    },
    "MASTERY": {
      "bit": 2,
      "label": "掌握库",
      "tables": [
        "assessment_item_snapshot",
        "assessment_event",
        "assessment_evidence_snapshot",
        "assessment_evidence_attribution",
        "assessment_presentation",
        "assessment_answer_reveal_event",
        "answer_reveal_outcome",
        "attempt_submission",
        "attempt_event",
        "attempt_correction",
        "binding_change_event",
        "learning_sequence",
        "projection_outbox",
        "learner_projection_snapshot",
        "projection_archive",
        "learner_problem_memory_state",
        "learner_knowledge_mastery_state",
        "independent_correct_observation",
        "applied_attempt_record",
        "applied_correction_record",
        "applied_answer_reveal_record",
        "applied_tutor_answer_exposure_record",
        "presentation_projection_state",
        "review_plan",
        "active_review_plan_slot",
        "review_queue_item",
        "review_queue_knowledge_node",
        "review_queue_reason",
        "review_session",
        "review_session_revision",
        "review_session_advance_receipt",
        "review_log",
        "student_model_prediction",
        "prediction_outcome",
        "learner_chat_evidence"
      ]
    },
    "SESSIONS": {
      "bit": 4,
      "label": "会话",
      "tables": [
        "tutor_conversation",
        "tutor_message",
        "tutor_message_source_asset",
        "agent_pending_request",
        "tutor_answer_exposure",
        "tutor_answer_exposure_outcome"
      ]
    },
    "IMAGES": {
      "bit": 8,
      "label": "图片",
      "tables": [
        "canonical_source_asset"
      ]
    }
  },
  "alwaysTables": [
    "knowledge_node",
    "knowledge_source",
    "knowledge_node_source_binding",
    "knowledge_node_relation",
    "knowledge_teaching_material",
    "knowledge_teaching_material_node_binding",
    "content_install_state"
  ],
  "derivedTables": [
    "knowledge_search_feature",
    "knowledge_search_index_state"
  ],
  "splitTables": [
    "model_task_event",
    "model_task",
    "model_task_operation",
    "figure_generation_attempt",
    "figure_generation_operation"
  ],
  "modelTaskKindCategory": {
    "CAPTURE_ASSESS": "ISSUE_BOOK",
    "CAPTURE_PARSE": "ISSUE_BOOK",
    "PROBLEM_CLASSIFY": "ISSUE_BOOK",
    "PROBLEM_RELATE": "ISSUE_BOOK",
    "IMAGE_PIPELINE_CLASSIFY": "ISSUE_BOOK",
    "TUTOR_PLAN": "SESSIONS",
    "TUTOR_RESPOND": "SESSIONS",
    "TUTOR_LOBBY": "SESSIONS",
    "TUTOR_EVALUATE": "SESSIONS",
    "LEARNING_SUMMARIZE": "SESSIONS",
    "REVIEW_RERANK": "MASTERY",
    "KNOWLEDGE_QUIZ": "MASTERY"
  },
  "modelTaskRowFilter": {
    "appliesTo": [
      "model_task_event",
      "model_task",
      "model_task_operation"
    ],
    "deletedKinds": "modelTaskKindCategory 中类别未被选中的 kind 集合（全选时为空）",
    "steps": [
      "DELETE model_task_event WHERE task_id IN (SELECT task_id FROM model_task WHERE task_kind IN :deleted_kinds)",
      "DELETE model_task WHERE task_kind IN :deleted_kinds",
      "DELETE model_task_operation WHERE task_kind IN :deleted_kinds"
    ],
    "unknownKind": "真子集遇映射外的 kind → fail-closed；全选保留",
    "note": "三处共用同一 kind 集合；model_task 是 model_task_operation 的子，删除顺序 event→task→operation"
  },
  "figureOperationSourceKindCategory": {
    "TUTOR_MESSAGE": "SESSIONS",
    "PROBLEM_REVISION": "ISSUE_BOOK",
    "CLEAN_REDRAW": "ISSUE_BOOK",
    "CAPTURE_PARSE": "ISSUE_BOOK"
  },
  "figureSourceIdSemantics": {
    "TUTOR_MESSAGE": "T1b 在飞：TutorFigureGenerator 写 parentOperationId ?: operationId；AttachedImageGenerator 写 parentRequestId；coordinator 旧调用回退写 parent.requestId。不是统一的 tutor_message 行主键，不能按 source_id 反查消息行",
    "PROBLEM_REVISION": "保留：意图是 problem_revision.revision_id；生产无写入者，未验证",
    "CLEAN_REDRAW": "T1b 在飞：父为 CleanRedrawTask 时写 taskId；父为 ModelTask 时写 parent.requestId。不是统一的行主键",
    "CAPTURE_PARSE": "保留：意图是采集解析的草稿/作业 id；生产无写入者，未验证"
  },
  "figureRowFilter": {
    "appliesTo": [
      "figure_generation_attempt",
      "figure_generation_operation"
    ],
    "when": "仅真子集（任一类别未勾选）；mask=15 全选时整表保留、不做行级筛选",
    "prepass": "同一事务内、任何 DELETE 之前物化 kept_asset / kept_operation / kept_attempt 三张临时表",
    "steps": [
      "R = 存活根引用表指向的资产（problem_revision_source_asset / problem_draft_source_asset / problem_draft.source_asset_id 当 ISSUE_BOOK 选中；tutor_message_source_asset 当 SESSIONS 选中）",
      "O_seed = 类别被选中的 operation（source_kind 按 figureOperationSourceKindCategory）+ 存活 clean_redraw_task.operation_id 指向的 operation",
      "O_ref = 产出资产属于 R 的 attempt 所属 operation（被删会话/题目域仍因共享图保留）",
      "O_keep = O_seed ∪ O_ref",
      "A_keep = operation_id ∈ O_keep 的全部 attempt ∪ output_asset_id ∈ R 的 attempt",
      "P = R ∪ A_keep 的非空 output_asset_id（第五个资产引用站点；I5 与 readReferencedCanonicalAssets 必须同源）",
      "DELETE figure_generation_attempt WHERE attempt_id ∉ A_keep；再 DELETE figure_generation_operation WHERE operation_id ∉ O_keep"
    ],
    "unknownSourceKind": "真子集：未知 kind 且不在 O_keep → fail-closed；已被共享产物/clean_redraw 引用拉入 O_keep 的 → 保留；全选保留；新增 kind 必须 bump closureVersion",
    "methodPolicy": "method 不参与筛选；JS 行只作为数据保留，恢复/导出/查看均不执行 JS",
    "sharedOutput": "同一资产可被多条根引用与多次 attempt 产出（content_sha256 唯一、去重），不得假设 1:1"
  },
  "rebuildTables": [
    "library_search_fts"
  ],
  "expectedClosure": [
    [
      "ISSUE_BOOK",
      "IMAGES"
    ],
    [
      "MASTERY",
      "ISSUE_BOOK"
    ],
    [
      "MASTERY",
      "SESSIONS"
    ],
    [
      "SESSIONS",
      "ISSUE_BOOK"
    ],
    [
      "SESSIONS",
      "IMAGES"
    ]
  ],
  "rowLevelEdges": [
    {
      "child": "model_task_event",
      "parent": "model_task",
      "column": "task_id",
      "kind": "split-family-intra",
      "onDelete": "RESTRICT",
      "note": "同一 task_kind 谓词三级删除的最深一级"
    },
    {
      "child": "model_task",
      "parent": "model_task_operation",
      "column": "operation_fingerprint",
      "kind": "split-family-intra",
      "onDelete": "RESTRICT",
      "note": "同一 task_kind 谓词；operation 是 task 的父，删除顺序 task→operation"
    },
    {
      "child": "tutor_answer_exposure",
      "parent": "model_task",
      "column": "model_task_request_id",
      "kind": "cross-family-to-split",
      "onDelete": "RESTRICT",
      "note": "SESSIONS 表硬 FK 到 split 的 model_task；正常 kind 为 TUTOR_*（SESSIONS），kind 谓词删同一集合；异常 kind 由 I1 兜底"
    },
    {
      "child": "figure_generation_attempt",
      "parent": "figure_generation_operation",
      "column": "operation_id",
      "kind": "split-family-intra",
      "onDelete": "RESTRICT"
    },
    {
      "child": "figure_generation_attempt",
      "parent": "canonical_source_asset",
      "column": "output_asset_id",
      "kind": "attempt-output-pin",
      "onDelete": "RESTRICT",
      "note": "行级边；attempt 行类别 ∈ {ISSUE_BOOK,SESSIONS}，两者都已蕴含 IMAGES，故不新增类别闭包"
    }
  ],
  "assetRoots": [
    {
      "table": "problem_revision_source_asset",
      "column": "source_asset_id",
      "category": "ISSUE_BOOK"
    },
    {
      "table": "problem_draft_source_asset",
      "column": "source_asset_id",
      "category": "ISSUE_BOOK"
    },
    {
      "table": "problem_draft",
      "column": "source_asset_id",
      "category": "ISSUE_BOOK"
    },
    {
      "table": "tutor_message_source_asset",
      "column": "source_asset_id",
      "category": "SESSIONS"
    }
  ],
  "assetPinRule": {
    "definition": "keptAssets = 四张根引用表在存活行上的资产 ∪ 存活 attempt 的 output_asset_id",
    "sites": 5,
    "requirement": "实现 6C-2 前必须把 attempt.output_asset_id 并入 findUnreferencedCanonicalAssets / readReferencedCanonicalAssets（T5），否则 I5 会漏收被 attempt 钉住的图",
    "canonicalRowPolicy": "IMAGES 选中时 canonical_source_asset 整表保留（v2 语义）；包内文件只含 keptAssets，未引用行随包保留但不带文件，恢复后由既有孤儿回收处理"
  },
  "softReferences": [
    {
      "table": "model_task",
      "column": "subject_id",
      "targetTable": "*by-task-kind*",
      "kind": "by-kind",
      "check": "no-new-dangling",
      "strength": "weak-host-hint",
      "checkDetail": "host existence only for non-terminal statuses; terminal rows may dangle",
      "note": "仅非终态任务要求宿主存在；终态历史任务允许宿主已删"
    },
    {
      "table": "model_task_operation",
      "column": "subject_id",
      "targetTable": "*by-task-kind*",
      "kind": "by-kind",
      "check": "no-new-dangling",
      "strength": "weak-host-hint",
      "checkDetail": "kind known only; host existence not required (ledger outlives host)",
      "note": "预算台账先于宿主存在：只校验 kind 已知，不要求宿主行仍存在"
    },
    {
      "table": "tutor_conversation",
      "column": "capture_draft_id",
      "targetTable": "problem_draft",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "tutor_conversation",
      "column": "anchor_practice_unit_id",
      "targetTable": "practice_unit",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "tutor_conversation",
      "column": "anchor_problem_revision_id",
      "targetTable": "problem_revision",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "tutor_conversation",
      "column": "anchor_id",
      "targetTable": "*by-anchor-kind*",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong",
      "note": "TEXT_ONLY 时为空；EPHEMERAL_DRAFT/PROBLEM_REVISION 按 anchor_kind 解析"
    },
    {
      "table": "tutor_message",
      "column": "bound_problem_id",
      "targetTable": "problem",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "tutor_message",
      "column": "bound_problem_revision_id",
      "targetTable": "problem_revision",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "tutor_message_source_asset",
      "column": "basis_revision_id",
      "targetTable": "problem_revision",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong",
      "note": "schema 69 新增列；NULL=旧行未知，不回填"
    },
    {
      "table": "learner_chat_evidence",
      "column": "conversation_id",
      "targetTable": "tutor_conversation",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong",
      "note": "删会话路径不清证据行：现存悬空只能由 no-new-dangling 放行，不得要求 0 行"
    },
    {
      "table": "binding_change_event",
      "column": "practice_unit_id",
      "targetTable": "practice_unit",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "student_model_prediction",
      "column": "practice_unit_id",
      "targetTable": "practice_unit",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong"
    },
    {
      "table": "review_log",
      "column": "card_id",
      "targetTable": "practice_unit",
      "kind": "cross-category",
      "check": "no-new-dangling",
      "strength": "strong",
      "note": "pseudo:<SUBJECT> 桶不是 practice_unit 行，检查须排除 pseudo: 前缀"
    },
    {
      "table": "clean_redraw_task",
      "column": "subject_id",
      "targetTable": "problem_draft",
      "kind": "same-category",
      "check": "no-new-dangling",
      "strength": "weak-host-hint",
      "note": "提交路径传草稿 id；草稿提交后允许悬空（历史作业）"
    },
    {
      "table": "clean_redraw_task",
      "column": "operation_id",
      "targetTable": "figure_generation_operation",
      "kind": "split-family",
      "check": "no-new-dangling",
      "strength": "strong",
      "note": "schema 69 新增列；NULL=旧行无台账；非空且源行存活时必须可解析"
    },
    {
      "table": "figure_generation_operation",
      "column": "source_id",
      "targetTable": "*by-source-kind*",
      "kind": "split-family",
      "check": "informational",
      "strength": "weak-host-hint",
      "note": "schema 69 新增；按 source_kind 解析宿主；共享图保留时宿主被删是预期结果，不作悬空失败；子集成包遇未知 kind fail-closed"
    },
    {
      "table": "library_search_fts",
      "column": "docid",
      "targetTable": "library_search_content",
      "kind": "same-category",
      "check": "rebuild-consistency",
      "strength": "informational",
      "virtual": true,
      "note": "FTS4 外部内容表虚拟列；以 rebuild 后 docid 孤儿数为核对，不参与一般软引用"
    },
    {
      "table": "learner_chat_evidence",
      "column": "knowledge_node_id",
      "targetTable": "knowledge_node",
      "kind": "always",
      "check": "none",
      "strength": "informational"
    },
    {
      "table": "llm_teaching_advisory",
      "column": "knowledge_node_id",
      "targetTable": "knowledge_node",
      "kind": "always",
      "check": "none",
      "strength": "informational"
    },
    {
      "table": "student_model_prediction",
      "column": "knowledge_node_id",
      "targetTable": "knowledge_node",
      "kind": "always",
      "check": "none",
      "strength": "informational"
    }
  ],
  "opaqueColumns": [
    {
      "table": "figure_generation_attempt",
      "column": "reservation_id",
      "reason": "账本授予句柄（figure-dispatch:<sha> / clean-redraw-dispatch:<task>:<n>），不是行主键；无目标列可引用"
    },
    {
      "table": "review_queue_item",
      "column": "item_family_id",
      "reason": "前缀+practice_unit_id 的家族令牌，不是行引用（旧 v1 清单把它列成空 target，v2 移除）"
    },
    {
      "table": "tutor_conversation",
      "column": "anchor_revision_id",
      "reason": "anchor 键空间的一部分（questionDocumentId:draftRevisionNumber），由 anchor_id/anchor_kind 承载行引用"
    },
    {
      "table": "figure_generation_operation",
      "column": "basis_fingerprint",
      "reason": "冻结输入的哈希，不是行引用"
    },
    {
      "table": "figure_generation_attempt",
      "column": "input_fingerprint",
      "reason": "请求逻辑指纹，不是行引用"
    }
  ],
  "trimOrder": [
    "tutor_message_source_asset",
    "tutor_message",
    "split_import_question",
    "split_import_job",
    "review_session_advance_receipt",
    "review_session_revision",
    "review_session",
    "review_queue_reason",
    "review_queue_knowledge_node",
    "review_queue_item",
    "review_log",
    "projection_outbox",
    "projection_archive",
    "problem_revision_source_asset",
    "problem_relation",
    "problem_organization_receipt",
    "problem_draft_source_asset",
    "problem_draft_edit_snapshot",
    "problem_draft_revision",
    "problem_draft_commit_receipt",
    "problem_classification_candidate",
    "problem_classification_binding",
    "presentation_projection_state",
    "prediction_outcome",
    "student_model_prediction",
    "model_task_event",
    "mistake_export_record",
    "llm_teaching_advisory",
    "library_search_outbox",
    "library_search_content",
    "learning_sequence",
    "learner_problem_memory_state",
    "learner_chat_evidence",
    "knowledge_search_index_state",
    "knowledge_search_feature",
    "knowledge_research_review_source",
    "knowledge_research_review_bundle",
    "knowledge_grounding_resolution",
    "knowledge_grounding_request",
    "independent_correct_observation",
    "learner_knowledge_mastery_state",
    "error_book_entry",
    "clean_redraw_task",
    "binding_change_event",
    "binding_audit_sample",
    "batch_import_page",
    "problem_draft",
    "figure_generation_attempt",
    "figure_generation_operation",
    "canonical_source_asset",
    "batch_import_job",
    "assessment_presentation",
    "assessment_evidence_attribution",
    "practice_unit_knowledge_binding",
    "assessment_event",
    "assessment_item_snapshot",
    "applied_tutor_answer_exposure_record",
    "tutor_answer_exposure_outcome",
    "tutor_answer_exposure",
    "model_task",
    "model_task_operation",
    "applied_correction_record",
    "attempt_correction",
    "applied_attempt_record",
    "attempt_event",
    "attempt_submission",
    "applied_answer_reveal_record",
    "learner_projection_snapshot",
    "answer_reveal_outcome",
    "assessment_answer_reveal_event",
    "assessment_evidence_snapshot",
    "practice_unit",
    "problem_revision",
    "problem",
    "agent_pending_request",
    "tutor_conversation",
    "active_review_plan_slot",
    "review_plan"
  ],
  "maskPolicy": {
    "closedMasks": [
      15,
      13,
      9
    ],
    "openMasks": [
      {
        "mask": 8,
        "decision": "IMAGES_ONLY",
        "state": "OPEN",
        "note": "产物恢复后资产无引用者，孤儿回收会清掉；是否提供由用户裁定"
      },
      {
        "mask": 0,
        "decision": "EMPTY_SELECTION",
        "state": "OPEN",
        "note": "产物是重置包（仅知识层+设置）；是否允许由用户裁定"
      }
    ],
    "unknownMask": "REJECT",
    "closingRule": "子类别隐含父类别；不静默补齐、不静默缩水；create() 只接受自洽选择"
  },
  "derivedPolicy": {
    "decision": "OPEN",
    "options": [
      "ALWAYS_TRIM",
      "KEEP_ON_FULL_SELECTION",
      "TRIM_AND_REBUILD_AFTER_RESTORE"
    ],
    "note": "derivedTables 不属于任何用户类别；三选项只改变全选时是否裁它们，改变 rowCounts/trimmedTableCount/digest，不改变类别/FK/软引用/顺序规则"
  },
  "digest": {
    "algorithm": "sha256( \"smbk-selection-v2\\n\" + \"closureVersion=2\\n\" + \"categories=\" + categories.sorted().joinToString(\",\") + \"\\n\" + \"assetPolicy=\" + assetPolicy + \"\\n\" + rowCounts.keys.sorted().joinToString(\"\"){ \"$it=${rowCounts[it]}\\n\" } )",
    "rowCountsSource": "裁剪后快照的逐表 COUNT(*)；split 表按行级筛选后的行数",
    "trimmedTableCount": "删除行数 > 0 的表数（不含 FTS rebuild）；v2 上限 78",
    "goldenEmptyFull": {
      "mask": 15,
      "assetPolicy": "INCLUDED",
      "sha256": "d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6",
      "note": "69 schema、空库、全选；rowCounts 为全部 86 表=0；三选项下派生表也是 0 行，故该向量不受三项待裁定影响"
    }
  }
}
```
<!-- 6c2-plan-v2:end -->

### 9.12 复跑与校验（本轮实际执行）

本轮用 Python 直接读 `69.json` 与本节 v2 JSON 做静态对拍；没有 Gradle、没有设备、没有备份往返。
执行的是同一份 `69.json`（T1a 交付），不是记忆或旧 schema。

实际断言与结果：

| # | 检查 | 结果 |
| --- | --- | --- |
| 1 | `69.json` version / identityHash / entities / views / FK 边 | 69 / `6633b7c2fe223041c4a2927203853f4f` / 86 / 2 / 92 |
| 2 | 86 表是否恰好被 类别 + ALWAYS + DERIVED + SPLIT 覆盖一次 | 86/86；缺失 0、多余 0；类别计数 30/35/6/1/7/2/5 |
| 3 | 从 69 的真实 FK 边折叠出的类别闭包 vs `expectedClosure` | 5 条边逐条相等 |
| 4 | 78 项 `trimOrder`：覆盖 86−7 ALWAYS−1 FTS；逐边拓扑序 | 78 项、唯一、覆盖完整、0 违规 |
| 5 | 20 条软引用的表/列/目标（或 `*…*` 特殊标记）是否存在；是否误把硬 FK 列当软引用 | 全部存在；无硬 FK 重叠；FTS `docid` 按虚拟列例外；`source_id` 是 `*source-hint*` |
| 6 | 16 个 mask 的闭包自洽性 | 自洽 0/8/9/13/15；其余 11 个非法 |
| 7 | 空库全选 `closureDigest` | `d6ec73ac07e596a11ff97ac292cf01a41e0f962659a2b542a6d8a15c56a5dad6` |
| 8 | 69.json 的新表/新列/索引/外键数量 | 2 表、4 列、4 索引（operation 2 + attempt 2）、2 条新 FK；本轨只读迁移与 69.json，未运行 T1a 契约测试（T1a 自报静态比对通过，UNVERIFIED） |

**没有执行**：Gradle 单测/编译、`FullMigrationMatrixInstrumentedTest`、真实备份创建→恢复、设备迁移
1→69/68→69、真实 Provider。以上一律 UNVERIFIED；不得把本节静态对拍写成门禁通过。

复跑方式（下一批实施时建议落到 `tools/backup/`）：读取 `69.json` 与
`<!-- 6c2-plan-v2:begin -->` 内的 JSON；按 #2 做集合相等，按 #3 从 `foreignKeys` 折叠闭包，按 #4 做
拓扑排序校验，按 #7 复算摘要。任何一项差异都说明“文档规则”与“schema 69”漂开，必须先改清单再写代码。
