# 批次 19 基线核对（T0，2026-10-24 采集）

计划：`.zcode/plans/plan-2026-10-23-batch-19-6c2-closeout-5b-halves.md` §0。
核对方式：只读命令 + 源码通读，未跑 Gradle、未改任何生产文件。凡未在本次执行的检查均标 UNVERIFIED。

---

## 1. HEAD / 工作树 / schema / 70.json

命令与输出（Git Bash，2026-10-24）：

```
$ git rev-parse HEAD origin/main
311f7de1b49eafdb63616c0e5196e72b2e994404
311f7de1b49eafdb63616c0e5196e72b2e994404

$ git status --porcelain | wc -l
574

$ git status --porcelain | awk '{print substr($0,1,2)}' | sort | uniq -c
    142  M
    432  ??
```

- HEAD = `origin/main` = `311f7de1`：与计划 §0 一致（批次 18 收口提交）。
- **工作树 574 项（142 M + 432 ??）≠ 计划的 569 项（140 M + 429 ??）**：漂移 +2 M / +3 ??，他线继续在飞。热区明细见 §2。

DB schema（`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/` 下最大号 JSON 为 `69.json`；`70.json` 不存在）：

```
$ ls .../69.json 存在；ls .../70.json
ls: cannot access '...70.json': No such file or directory

69.json 内容（python json 读出）：
version: 69
identityHash: 6633b7c2fe223041c4a2927203853f4f
```

- 与计划 §0 一致：DB schema 69、`70.json` 空闲（T5 若需新表可取 70）。

请求 schema（`TutorVisualContext` 所在 schema 常量，`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546-547`）：

```kotlin
const val TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18
const val CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION
```

- 与计划 §0 一致：请求 schema 18。`TutorVisualContext.kt` 本体（`core/model/.../TutorVisualContext.kt:23`）不含版本常量，版本事实在 ModelTasks 伴生对象。

## 2. 他线热区快照（142 M 主源码按模块归组，测试只计数）

```
$ git status --porcelain | grep "^ M" | sed 's/^ M //' | grep "src/main" | sed -E 's#/src/main/.*##' | sort | uniq -c | sort -rn
     17 feature/tutor
     13 feature/library
      9 feature/capture
      9 core/ui
      8 feature/review
      6 core/database
      6 app
      4 core/domain
      2 core/data
      1 feature/profile
      1 core/export
```

测试文件（M 状态、src/test 与 src/androidTest）共 21 个（`grep -c "src/test\|src/androidTest"` 实测 21）。
非源码 M：`.gitignore`、`core/export/build.gradle.kts`、docs/** 约 40 个、`tools/dense_build/` 3 个、`tools/kb_build/` 1+1、`knowledge-production/wusan/materials.jsonl`。

逐模块对照计划 §0 热区清单：

| 模块 | 计划 §0 热区 | 本次实测 | 判定 |
| --- | --- | --- | --- |
| feature/tutor | 全模块 | 17 个 M 主源码 | 一致（在计划禁改区） |
| feature/review | 全模块 | 8 个 M 主源码 | 一致（在计划禁改区） |
| feature/library | 仅 `MistakePaperEditRoute.kt` | **13 个** M 主源码 | **热区扩张**（新增 BatchImportRoute、LibraryQuestionCard、LibraryRoute、LibraryToolbar、MistakeDetailRoute、MistakeExportHubRoute、MistakeOrganizationCorrectionEditor、MistakeOrganizationSection、MistakeOrganizationTreePicker、MistakeRevisionEditRoute、MistakeRevisionEditViewModel、SplitImportReviewRoute） |
| app | LearningMasteryScreen、ReviewReminderCoordinator/Screen、SchedulingSettingsScreen、StartupStateBanner | 同左 5 个 + **`SmartMistakeBookRoot.kt`** | **新增冲突**：Root 在计划 §0"干净区"名单里且是 I1 目标，现已 M（计划 §6 已预置"若开工时变脏：整文件纳入并注明"的处置） |
| feature/capture | 未列（`CaptureScreen.kt` 在计划 §0 干净区名单） | **9 个** M 主源码，含 `CaptureScreen.kt` | **新增冲突**：干净区文件已变脏；feature/capture 全模块成为事实热区 |
| core/ui | AiReplyRichMarkdown、AttachedImageCard、BoundedLocalImage、Components（+17 未跟踪） | **9 个** M 主源码（另加 PageStateFrame、Theme、ThinkingCollapsibleCard、TutorMarkdownTokens、TutorToolTraceLine）；未跟踪 core/ui 实测 **19** 个（16 main + 3 test） | **热区扩张** |
| feature/profile | 未列 | `ProfileRoute.kt` M | **新增**（计划未提） |
| core/database | CleanRedrawTaskContract/Port、RoomBatchImportStore、TutorConversationDatabaseContract/Dao、BatchImportDao | 6 个 M 全部落在计划清单内 | 一致 |
| core/domain | BatchImportRepository、CleanImageGenerator、MistakeDetailRepository、TutorJudgedReview | 4 个 M 全部落在计划清单内 | 一致 |
| core/data | RoomMistakeDetailRepository、RoomTutorToolRunner | 2 个 M 全部落在计划清单内 | 一致 |
| core/export | build.gradle.kts、MistakeExportJobRunner | 同左 | 一致（T5 对 runner 只读消费或后置登记，计划 §3 已预置） |
| docs/** | 仅说 docs/research 下他线 8 个未跟踪 | docs/research 未跟踪仍 8 个；另有约 40 个 M 文档 | M 文档为计划未逐项列出的他线文档工作 |

## 3. KD open 清单（`docs/known-defects.md`，62 条目，grep 实测 open 共 6 条）

```
$ grep -n "^## KD-" docs/known-defects.md | grep -vE "fixed|resolved|closed"   →  6 条
```

- **KD-24 (open)**：去截断实验（v2）净伤害，词面路由已回滚；宽路 p95 663ms 超预算；别名截断缺陷改由 dense 兜底结构性消灭（docs/known-defects.md:875）。
- **KD-27 (open)**：CI `check` job 长期红 ⇒ status.md 的 commit-back 被 `if: success()` 永久冻结（:1061）。
- **KD-28 (open，接受边界)**：`kb_build` 9 科白名单抄两份（`KnowledgeBaseImportContract` 与 `SubjectKind`）（:1100）。
- **KD-29 (open)**：AI 生成合成内容标识未落，材料与展示层没有"AI 整理"标识（:1112）。
- **KD-32 (open)**：192MB 堆上限下知识包安装与批量导入 OOM 的内存天花板（两处峰值已修，剩余见条目内）（:1174）。
- **KD-61 (open, low)**：恢复关库前 750ms 宽限是启发式，未接入写门的 DAO 直写/应用级 collector 仍可能命中已关闭连接池（:2439）。KD-62 ③（写门只盖两仓库）同归本条（KD-62 头部明记"③ 保留 KD-61"）。

与计划 §0 一致（KD-61 open + KD-62③ 同归；其余 open 项 T0 重列完成）。

## 4. 6C-2 批次 18 遗留五项 + KD-61 的 file:line 定位（全部代码级证实）

### 4.1 写侧不拒负 rowCounts —— 证实

`SmbkArchiveCodec.requireWritableSelection`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt:435-482`）校验 mask（:436）、schemaVersion（:440）、closureVersion（:444）、assetPolicy（:448）、categories 一致（:452-463）、**rowCounts 只查键覆盖**（:464-468，"必须覆盖 86 表"）、trimmedTableCount（:469）、closureDigest 重算（:473-481）——**无一处拒负值**。读侧已有负数检查：`SmbkArchiveCodec.kt:1197-1198` `if (record.rowCounts.values.any { count -> count < 0L }) return SelectionRead.Invalid("备份内容选择的 rowCounts 含负数")`。写侧缺同规检查，遗留成立。

### 4.2 `BackupSelectionSummary` 缺 `assetPolicy` —— 证实

`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupSelection.kt:177-191`：字段仅 `mask` + `categories`（+派生 `isFull`），无 `assetPolicy`。manifest 侧的 `SmbkSelectionRecord` 有 `assetPolicy`（SmbkArchiveCodec.kt:448 消费），摘要未携带。

### 4.3 `deleteAllData` 不清 `restore-index-rebuild-pending.json` —— 证实

- 全清入口：`AndroidBackupRepository.deleteAllData()`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt:883-951`），端口声明 `core/domain/.../BackupRepository.kt:210`，UI 调用 `app/.../SecondaryScreens.kt:805`。
- 标记文件名：`RestoreIndexRebuildMarker.FILE_NAME = "restore-index-rebuild-pending.json"`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/RestoreIndexRebuild.kt:29`），位于 `noBackupFilesDir`（:33）。
- 清扫逻辑：`deleteAllNonDatabaseFiles` 第 10 步（AndroidBackupRepository.kt:996-1001）对 `noBackupFilesDir` 只过滤 `restore-journal-*` 前缀与 `restore-quarantine`——**该标记不在删除集合内**，遗留成立。

### 4.4 恢复侧 S3 软引用检查不存在 —— 证实

设计件 `docs/research/2026-10-17-6c2-content-selection-design.md` §3.2（:361 行表）要求"在 staged 库上重跑附录 C（:826 起）的全部检查 = 0 行"，落在 Phase 2b 之后、Phase 3 之前。实测：

```
$ grep -n "softReference|soft_reference|dangling" AndroidBackupRepository.kt SmbkArchiveCodec.kt   →  0 命中
```

恢复侧两文件均无软引用闭合检查；软引用实现只存在于成包侧（`BackupSnapshotTrimmer.kt`（I4，no-new-dangling 口径）与 `SmbkSelectionPlan.kt:283`"20 条软引用"）。设计件 §9.9/§9.11 与机器块仍标 OPEN（grep "OPEN" 命中 :1045/:1152/:1535-1537/:2158/:2164/:2172），文档债同计划所述。

### 4.5 I5 五处 SQL 与 `findUnreferencedCanonicalAssets` 双源 —— 证实

- 权威单源：`PendingCaptureDao.findUnreferencedCanonicalAssets`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt:78`），五类 NOT EXISTS：`problem_revision_source_asset`、`problem_draft_source_asset`、`problem_draft`、`tutor_message_source_asset`、`figure_generation_attempt(output_asset_id)`。
- 复制侧：`BackupSnapshotTrimmer.FIVE_SITE_ASSET_IDS_SQL`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt:1247` 起，定义；消费点 :775/:800/:834），同一五站点写成 EXISTS 补集，注释自认"生产 `findUnreferencedCanonicalAssets` 的五站点补集"（:1243-1244）。两份 SQL 手工同步、无共享常量，双源成立。

### 4.6 KD-61 写门现状（T3 输入）

KD-62 ③ 原文与 KD-61 批次 17 再核（docs/known-defects.md:2439-2463）。本次实测生产代码写门接入仍只有两处：

```
$ grep -rn "withWriteAdmission" --include="*.kt" core app feature（排除测试与 .worktrees）
core/data/.../backup/AndroidBackupRepository.kt:155:  withWriteAdmission("清理孤立题图")
core/data/.../capture/RoomCaptureWorkflowRepository.kt:166:  writeGate.withWriteAdmission(entry, block)
```

门本体 `BackupWriteGate.withWriteAdmission`（`core/domain/.../BackupWriteGate.kt:234`）。与 KD-61 批次 17 记录一致：覆盖面未扩大，旁路集合（图生成审计、启动僵尸对账、复习调度/知识写入/掌握度/导出/提醒等 DAO 直写）仍在门外。

## 5. G6 现状复核（T4 输入）

- **常量表**：`AlgorithmConstants.ReviewScoring`（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/AlgorithmConstants.kt:99-133`）。关键值：`EASY_DIFFICULTY_CEILING=4.0`（:101）、`WEAKNESS_THRESHOLD=0.35`（:105）、**`EARLY_REVIEW_MAX_RETRIEVABILITY=0.8`（:111）**、`WEAKNESS_WEIGHT=3.0`（:115）、**`EXAM_WEIGHT=2.0`（:119）**、`WAITING_GRACE_DAYS=7.0`（:131）等。KDoc 明示"手调值，不改任何一个数"（:95-97）。
- **消费点行号**（`core/domain/.../ReviewPlannerV2.kt`）：
  - `EARLY_REVIEW_MAX_RETRIEVABILITY`：**:647**（考试提前闸门，R < 0.8 才允许提前；私有别名 :1077-1078）。
  - `EXAM_WEIGHT * candidate.examPriority`：**:666**（打分项；私有别名 :1086）。
  - 与计划 §0 一致：加权与"提前"已接线。
- **考试日历**：`ExamCalendarEntry`（`core/domain/.../StudyExperienceRepository.kt:719`）；`DataStoreSchedulingSettingsStore`（`core/data/.../settings/DataStoreSchedulingSettingsStore.kt:58` exams Flow、:65 addExam）；屏内申报 `SchedulingSettingsScreen.kt:39` 注释明示"`declareExam`/`removeExam` 只有接口与实现，学生改不了任何排程参数"（只读边界成立），:103-105 `ExamCalendarEditor`。注意 `SchedulingSettingsScreen.kt` 为 M（他线在飞，计划 §1 禁改）。
- **3E 全库扫描分数是否已被排程消费**：分两层——
  - 知识点复习侧**已消费**：`StudyReviewPlannerService.currentKnowledgeReviewPlan`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:526-659`）：`database.readFullLibraryScanNodes()`（:600）→ `fullLibraryScanCandidate`（:611）→ `selectKnowledgeReviewQueue(fullLibraryScanCandidates=…, fullLibraryScanDailyCap=FULL_LIBRARY_SCAN_DAILY_CAP)`（:648-649）。
  - 错题排程侧（`ReviewPlannerV2`）**未消费**：`grep fullLibraryScan ReviewPlannerV2.kt` 0 命中；其弱点项 `WEAKNESS_WEIGHT * weakness`（:661）的输入是快照掌握度（`weakestSmoothedMastery`，:547-551/:589），与 3E 扫描分数无接线。计划 §0"若排程未消费"的待核项，本次核实为**未消费**（T4-3 成立，需接线）。
- **每日预算/新引入配额常量**：时间预算 `DEFAULT_REVIEW_TIME_BUDGET_SECONDS = 20*60`（`core/data/.../study/RoomBackedStudyExperienceRepository.kt:1108`，构造参数 :97 注入 `StudyReviewPlannerService:58`）；名额 `FULL_LIBRARY_SCAN_DAILY_CAP = 10`（`core/domain/.../KnowledgeReviewFullLibraryScan.kt:30`，`KnowledgeReviewQueue.kt:166` 默认参数）。`ReviewPlannerV2` 自身无常量化的每日新引入上限（伴生对象常量表 :1055-1095 无此项）。
- **扩容爬升**：`ReviewPlannerV2`/`AlgorithmConstants` 未见"预算随考试临近放大"类常量或逻辑（grep MAX_/CAP/dailyLimit 于 ReviewPlannerV2 仅命中会话内约束 `MAX_PER_KNOWLEDGE_NODE_PER_SESSION=2`（:1064）、`MAX_SAME_SUBJECT_RUN=3`（:1092）、`MAX_WEAKNESS_ONLY_SHARE=0.25`（:1079））。**不存在**，T4-2 需新建。

## 6. G8 现状复核（T5 输入）

- **`MistakeExportRecord` 字族无答案键/题映射**——证实：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeExportRecord.kt:51-90`，字段全集 = exportId/kind/displayName/status/inputSha256/pdfSha256/pageCount/failureMessage/createdAtEpochMillis/finishedAtEpochMillis；成功态只要求产物身份（:70-72），无 answerKey、无题映射链。同族 DB 侧 `MistakeExportRecordRow`/`MistakeExportRecordEntity`（core/database）同样无（导出记录契约 `MistakeExportRecordContract.kt:7`）。
- **回灌通道签名与语义**（`core/domain/.../StudyExperienceRepository.kt`）：
  - `submitReviewChoice(sessionId, expectedStateVersion, submission: StudyChoiceSubmission): StudyReviewChoiceSubmissionResult`（**:449-453**）——"Atomically records one canonical answer and advances its persisted review queue item"。
  - `submitReviewSelfReport(submission: StudySelfReportedReviewSubmission): StudySelfReportedReviewResult`（**:464-467**）——PRD-05 三键自评，写 `SELF_REPORTED_RECALL`/`SELF_REPORTED_STUCK`（刻意不是 LOCAL_CHECKED/MODEL_JUDGED），同事务推进队列，requestId 幂等；默认实现抛 UnsupportedOperationException。
  - 同族还有 `undoReviewSelfReport`（:476-479，VOID 回退幂等）、`storedReviewAnswer`（:485，PRD-05 无模型路径，`isTrusted=false` = 无可信键）、`settleTutorJudgedReview`（:492-494，讲题判定结算幂等）。
- **PRD-02 答案键来源/纠错规则落点类型**（均枚举/常量 + 账本列，非表）：
  - 来源枚举 `AnswerKeyVerification`（VERIFIED/USER_ASSERTED/UNKNOWN，`core/model/.../LearningState.kt:195-199`）+ 判定纯函数载体 `AnswerKeyTrustDecision`（:208-217，`mayWriteNumericEvidence` 只有 VERIFIED/USER_ASSERTED 才 true）。
  - 纠错规则：账本列 `learner_chat_evidence.corrects_evidence_id`（schema 68，`AnswerKeyCorrectionMigration_67_68.kt`）+ 账本事件 `EvidenceCorrectionKind`（VOID/REPLACE，`LearningState.kt:552-553`，语义 :540-541）。
  - 两轴拆分：`scoringMode = AUTO_VERIFIED` 只声明"本地机械可判"，不蕴含键已验证（`core/data/.../study/KnowledgeQuizItemStore.kt:171-178`、:261）。
  - 历史处置见 KD-43（fixed，docs/known-defects.md:1934-2004）。

## 7. T2/T3/T4/T5 目标文件 clean 状态（逐个 `git status --porcelain -- <path>`）

```
CLEAN  core/data/.../backup/SmbkArchiveCodec.kt                  （T2-1）
CLEAN  core/domain/.../BackupSelection.kt                        （T2-2）
CLEAN  core/data/.../backup/AndroidBackupRepository.kt           （T2-3/T2-4/T3）
CLEAN  core/data/.../backup/BackupSnapshotTrimmer.kt             （T2-5）
CLEAN  core/database/.../dao/PendingCaptureDao.kt                （T2-5）
CLEAN  app/.../SecondaryScreens.kt                               （T2-6）
CLEAN  core/domain/.../BackupWriteGate.kt                        （T3）
CLEAN  core/data/.../capture/RoomCaptureWorkflowRepository.kt    （T3 核查对象）
CLEAN  core/data/.../study/StudyReviewPlannerService.kt          （T4）
CLEAN  core/domain/.../ReviewPlannerV2.kt                        （T4）
CLEAN  core/domain/.../AlgorithmConstants.kt                     （T4）
CLEAN  core/data/.../settings/DataStoreSchedulingSettingsStore.kt（T4 只读）
CLEAN  core/domain/.../KnowledgeReviewQueue.kt                   （T4 关联）
CLEAN  core/domain/.../KnowledgeReviewFullLibraryScan.kt         （T4 关联）
DIRTY(M) app/.../SmartMistakeBookRoot.kt                         （I1 目标；计划 §0 曾标 clean，已漂移）
CLEAN  app/.../SmartMistakeBookApplication.kt                    （I1 目标）
CLEAN  core/domain/.../MistakeExportRecord.kt                    （T5 只读消费）
CLEAN  core/domain/.../StudyExperienceRepository.kt              （T5 回灌通道）
DIRTY(M) core/export/.../MistakeExportJobRunner.kt               （T5 关联，计划 §3 已预置"只读消费或后置登记"）
```

T2/T3/T4 全部目标文件 clean；T5 的两个关联脏文件均在计划预置的处置口径内（Root 按计划 §6"整文件纳入并注明"处理；ExportJobRunner 按 §3 后置登记）。

---

## 与计划 §0 不符处汇总

1. **工作树计数漂移**：574（142 M + 432 ??）vs 计划 569（140 M + 429 ??）。
2. **`SmartMistakeBookRoot.kt` 已 M**：计划 §0 干净区含它（I1 目标）；现脏，触发计划 §6 的预置处置（整文件纳入并注明）。
3. **`CaptureScreen.kt` 及 feature/capture 全模块（9 文件）已 M**：计划 §0 干净区含 `CaptureScreen.kt`；现整个模块成为事实热区（本批无轨直接以它为目标，风险等级低于 Root）。
4. **feature/library 热区扩张**：计划仅列 `MistakePaperEditRoute.kt`，现 13 个 M 主源码。
5. **core/ui 热区扩张**：M 从 4 → 9，未跟踪从计划"+17"实测 19。
6. **feature/profile/ProfileRoute.kt 新增 M**：计划任何清单均未提及。
7. **G6"排程是否已消费 3E 分数"核实为未消费**（错题侧 ReviewPlannerV2 无接线；仅知识点复习侧已消费）——与计划 §0 的待核表述相符，非矛盾，T4-3 需接线。
8. docs/** 约 40 个 M 文档不在计划热区逐项清单中（他线文档工作，本批不动）。

其余各项（HEAD/origin、schema 69 / 请求 schema 18 / 70.json 空闲、KD open 六条、6C-2 五项遗留的代码级存在性、KD-61 写门两处覆盖、G6 加权/提前接线行号、G8 无答案键字族与回灌通道、PRD-02 落点）均与计划 §0 一致。
