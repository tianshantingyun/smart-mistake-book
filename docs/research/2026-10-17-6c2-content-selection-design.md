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

本节承接[4E的WG-08](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#wg-08状态预算恢复与备份)与[后续计划](../../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md)。旧schema66的84表/90边/5组合法选择是原探针快照，不代表加入新图表后仍成立；本次不把尚未迁移的表写进附录A机器JSON，也不声称已重跑Android恢复。

- [ ] 实际4E迁移落地后，从最新Room导出重新生成类别/FK/软引用/裁剪顺序及closureVersion/digest，覆盖六个拟新增记录表与旧作业operation关联，不写死版本68或旧84表数量。
- [ ] 题目/消息引用作为保留根计算JS程序、快照、必要预览及canonical图像的可达闭包；同一程序被保留题目和被删除会话共用时仍须保留，不能按整表类别删除。共享operation/attempt按存活宿主与引用分行筛选，扩展现有SPLIT机制，不全部归SESSIONS或IMAGES。
- [ ] 程序和快照作为持久事实、预览/字体版本与图像字节按实际引用进入清单；不可把JS预览当唯一图事实。必要文件缺失/hash不符在交换前拒绝，未固定派生缓存按既有可重建规则处理。
- [ ] 增加混合JS/IMAGE_MODEL、共享图双根、删除会话保留题、不同selection、未知程序版本、缺预览、源revision改变的成包/恢复用例；历史查看/导出0模型派发，恢复ledger不归零，不复活外发授权。
- [ ] 本设计沿用快照库裁剪、不动实时库；其基于VACUUM的路线需先解决KD-53大库上限与KD-54快照写饥饿并设备复验。不能用内容勾选扩展绕过已有备份正确性问题，也不在本节替未决产品选项代签。

## 附录 A · 类别 → 表（机器可读，唯一数据源）

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

先完成[PRD-07](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md)的KD-53/54/47，再扩内容勾选。现有VACUUM方案不能长期占Room写连接；旧API fallback必须有真实写者屏障与checkpoint结果核验，不能仅靠“空闲”注释。独立连接/增量备份的支持度以实际驱动/设备证据为准。

- [ ] PRD-02/03新增题/键版本、纠正/撤销事件、源引用和待重放回执后，按实际schema重新枚举FK与软引用。学习记录保留其必要键/题版本/目标证据，不能勾掉会话后把有效证据来源一起丢失。
- [ ] 纠正中/重放中备份恢复要保持持久回执与一致有效视图，恢复后本地续处理但不自动发送外部请求；删会话保题与两方法共享图继续沿第7节闭包规则。
- [ ] 本文旧schema66机器附录/SQL是历史研究证据，不改成新schema已验；实际迁移落地后另出新版清单与往返报告。
