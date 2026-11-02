# 批次 40 基线核对（2026-11-02）：5× 任务量收口批 §0/§1 逐项复核

> 基线轨（只读核对：零 Gradle、零设备写、零 git 写）。依据计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md` §0/§1 逐项实跑复核；每项给出命令与 `path:line` 证据。与计划 §0 不符处汇总在 **§8**。
>
> 本文件不声称任何"通过"：所有"存在/不存在/数值"均来自本会话实跑；涉及构建、真机执行、CI 运行结果的面一律记 **UNVERIFIED**。

## 0. 核对方式与环境

| 项 | 实测 | 证据 |
|---|---|---|
| Shell | Git Bash（win32 10.0.26200） | 环境注入 |
| adb | PATH 上**无** `adb`；需用绝对路径 | `adb devices` → `bash: adb: command not found`；`$ANDROID_HOME` = `C:\Android\Sdk`，`/c/Android/Sdk/platform-tools/adb.exe` 可用 |
| 排除面 | 仓内存在 `.worktrees/`（`image-subsystem`、`kb-bench` 等）。凡 `grep -r .` 一律先确认命中不在 `.worktrees`，否则结论作废 | 例：`grep -rn "enum class ModelTaskKind"` 首命中为 `.worktrees/image-subsystem/…`，已弃用该命中，改读主树 |
| 未跑 | Gradle、任何测试套件、任何设备用例、`gh run view`（网络） | 本轨纪律 |

---

## 1. 一 · HEAD / origin/main / 工作树 / DB schema / 请求 schema

| 项 | 实测 | 命令与证据 |
|---|---|---|
| HEAD | `a3f367dfaccd960f951a66468a1cdda2f0b21f47`（2026-11-02 05:18:05 +0800，批次 39 台账更正） | `git rev-parse HEAD`；`git log -1` |
| origin/main（本地引用） | 同为 `a3f367df…`；`HEAD...origin/main` = **0 0**（无领先/落后） | `git rev-parse origin/main`；`git rev-list --left-right --count HEAD...origin/main` |
| 工作树计数 | **548 条** = `M` **127** + `??` **421**；暂存区 0 | `git status --porcelain \| wc -l`；`awk '{print $1}' \| sort \| uniq -c` |
| DB schema | **72**（与题面应值一致）：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:140` `internal const val STUDY_DATABASE_VERSION = 72`；`@Database` `version = STUDY_DATABASE_VERSION` 在 `:244` | `grep -n "STUDY_DATABASE_VERSION"` 于 `core/database/src/main/kotlin` |
| 导出 schema | 目录 `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/` 共 **72 个 json**（`7.json`…`72.json`）；`72.json` 的 `database.version = 72`、`identityHash = aa5e8e8e6536e0c1f3adcda6867814cb` | `ls …/*.json \| wc -l`；python 解析 `72.json` |
| 请求 schema | **18**：`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546` `const val TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`，`:547` `CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION` | 同上文件 `sed -n '486,550p'` |

- 请求 schema 18 的**语义边界**（与 WS4 item 24"18→19"相关）：`ModelTasks.kt:488-546` 是逐版本引入门（`EGRESS=2` … `TUTOR_ATTACHED_QUESTION` … `TUTOR_VISUAL_CONTEXT=18`），bump 到 19 必须同时新增一个 `*_SCHEMA_VERSION = 19` 常量并把 `CURRENT_SCHEMA_VERSION` 指过去；`:421` 的 `require(schemaVersion in MIN..CURRENT)` 与各 `require(schemaVersion >= X)` 是同一条链。改常量一处不会自动放行新字段——新字段必须自己拿到 `require` 里的门，这一点决定了 WS4 不是"把 18 改成 19"这么简单。
- 工作树 548 条的成因（分组见 §2）：127 M 中 `core/data` 47、`core/database` 21、`app` 4、热区 6；421 ?? 中含 **14 个未跟踪 lockfile**、`core/markdown/` 整目录、以及批次 39 尾批新增的 KB L2 文件。

---

## 2. 二 · 热区快照与 core/data、core/database 开工即脏

命令：`for p in feature/ core/ui/ core/export/ core/domain/ core/data/ core/database/ core/model/ app/; do git status --porcelain -- "$p" | …; done`

| 区 | M | ?? | 明细（M 逐文件；?? 择要） |
|---|---|---|---|
| `feature/*` | 0 | 6 | 5 个 `gradle.lockfile`（capture/library/profile/review/tutor）+ `feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/nul` |
| `core/ui` | **1** | 1 | `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt`；`core/ui/gradle.lockfile` |
| `core/export` | **3** | 1 | `src/test/…/MistakePdfEligibilityTest.kt`、`MistakePdfPagePlannerTest.kt`、`MistakePdfPlannerGoldenTest.kt`（**全在 test**，无 main 改动）；`core/export/gradle.lockfile` |
| `core/domain` | **2** | 1 | `KnowledgeResearch.kt`、`TutorJudgedReview.kt`；`core/domain/gradle.lockfile` |
| `core/data` | **47** | 1 | 按源集拆：**main 27 / androidTest 11 / test 9**；`core/data/gradle.lockfile` |
| `core/database` | **21** | 7 | 按源集拆：**main 11 / androidTest 4 / test 6**；main 11 = `StudyDatabase.kt`、`KnowledgeBaseImportContract.kt`、`KnowledgeSearchFeature{BatchInsert,Extractor,IndexBuilder}.kt`、`RoomKnowledgeBaseStore.kt`、`RoomKnowledgeContentReconciler.kt`、`dao/{KnowledgeTeachingMaterial,PendingCapture,ProblemOrganization}Dao.kt`、`port/TutorVisualPort.kt` |
| `core/model` | 1 | 1 | `core/model/src/main/kotlin/…/ProblemCatalog.kt`；`core/model/gradle.lockfile` |
| `app` | 4 | 5 | — |
| `settings.gradle.kts` | **0** | 0 | **干净**（见 §6 KD-72） |

**core/database 的 7 个 `??`（WS2 直接受影响）**：

- `core/database/src/main/kotlin/…/KnowledgeSearchMaterialFeatureMigration_71_72.kt`（71→72 迁移本体）
- `core/database/src/main/kotlin/…/entity/KnowledgeSearchMaterialFeatureEntity.kt`
- `core/database/src/androidTest/kotlin/…/KnowledgeSearchMaterialFeatureInstrumentedTest.kt`
- `core/database/src/androidTest/kotlin/…/TutorVisualSavedProblemRetentionInstrumentedTest.kt`
- `core/database/src/test/kotlin/…/MaterialFeatureQuotaTest.kt`、`MaterialFeatureScaleTest.kt`
- `core/database/gradle.lockfile`

**结论（对计划 §2"热区禁改"的实际影响）**：

1. 热区**不是**干净的——开工即有 6 个 M（core/ui 1、core/export 3、core/domain 2）。计划 §2 `:97`"热区禁改"在字面上仍可执行（这些是他线的改动，本批不碰），但**验收时无法用"这些文件变了=本批改了"来归因**；提交前逐路径核对暂存区（§2 `:100`）是唯一可用的门。
2. `core/export` 的 3 个 M 全是 **test 文件**，且 `build.gradle.kts` 已干净（与 §6 KD-72 的改善一致）。
3. 与批次 39 基线（`docs/research/2026-11-01-batch-39-baseline.md` §2：core/domain 4 M、core/export 6 M、core/ui 35 M、settings.gradle.kts 1 M）相比，热区脏面已从 46 条降到 6 条——`a3f367df` 一带的收口提交确已入库。
4. **异常项**：`feature/tutor/src/main/java/…/feature/tutor/nul` 是 **0 字节空文件**（`ls -la` 2026-09-29 09:57），典型 Windows `> nul` 重定向误落。它在热区源码树内、未跟踪，任何 `git add -A` 都会把它带进提交。**建议主会话在提交前显式删除或以 `git add -- <逐路径>` 规避。**

---

## 3. 三 · WS1 落点（PDF /AIGC）

| 问项 | 实测 | 证据 |
|---|---|---|
| `core/data` 是否已有 `pdf` 包 | **没有**。`core/data` 现有包：`backup capture export knowledge library mistake model settings splitimport study tutor visual` | `ls core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/` |
| `PdfDocument` / `PdfRenderer` 使用点（主树 main 源） | `core/export`：`DeterministicMistakePdfRenderer.kt:10,62,65`（`PdfDocument` 建文档/页）、`MistakePdfExporter.kt:4,167`（`PdfRenderer` 验页数）、`MistakePdfDelivery.kt:9,10,33,54,72,226-245`（预览重渲染与逐页重绘）。`core/data`：`capture/PdfBatchImportPageRenderer.kt`、`visual/PdfWriteIntegrity.kt`、`visual/VisualDrawOpsPdfPainter*.kt`（均为**绘制/校验**用，不是文档级字典编辑） | `grep -rln "PdfDocument\|PdfRenderer" core app`（排除 `.worktrees`） |
| 现有 AIGC 载体 | `core/data/src/main/kotlin/…/capture/GeneratedFigureMetadata.kt:145-190`：TC260-PG-20259A 的 **XMP 包**（命名空间 `:154`、`Label/ContentProducer/PropagationId` 三字段 `:177-179`、RDF 片段 `:189`）。即 item 5"/AIGC 字典 schema 按 TC260 实现"的字段表**已有单源**可复用（图片侧），PDF 侧需另起 Info 字典 | 读该文件 |
| core/export 导出接缝（只读判定） | `MistakePdfExporter.prepare()`（`:117-140`）流程为：`DeterministicMistakePdfRenderer.render(input, temporaryPdf)` → 尺寸门 → `validatedPageCount(temporaryPdf)`（`PdfRenderer`）→ `store.writeDigest` → `store.publish`。**渲染后、发布前没有任何可插入字节处理的 public/registered 接缝**；`inputSha256` 在 `:119` 的缓存查找里已前置使用 | 读 `core/export/…/MistakePdfExporter.kt:108-180` |
| `core/data/export` 现状 | 只有一个类 `RoomMistakeExportRepository.kt`（导出**记录**行，不碰产物字节；`observeRecords`/`recordStarted`…） | 读该文件 `:1-60` |
| item 6"仅 AI 图"判据现状 | `core/export/…/AiGeneratedImageCaption.kt:30` `internal fun isAiGeneratedExportImage(role: String?)`：只按 `VisualRole`（`null`/`CLEAN_IMAGE`/`TUTOR_EXPLANATION` → 打标）。调用点：`MistakePdfPagePlanner.kt:148,162,412`、`DocxMistakeRenderer.kt:237,294,329`。**没有任何"模型解析文本"通道** | `grep -rn "isAiGeneratedExportImage"` |
| 输入 DTO 是否已带解析文本 | `MistakePdfExportInput`（`core/export/…/MistakePdfEligibility.kt:318-355`）字段：`title/subject/subtitle/documentTitle/blocks/questionDocumentSha256/inputSha256/max*/cleanImage*/questionImageRole/solutionImages/layout/userNote`。**无"解析文本来源"字段**；且构造函数为 `internal constructor` → 新增字段必然是 core/export（热区）改动 | 读 `:318-360` |

**对计划的影响（不是验收结论，是落点事实）**：

- item 1"落 `core/data` pdf 包"是**纯新增**包与纯计算，无既存调用者；它要真正生效必须在 `prepare()` 里插入调用，而那是热区。⇒ WS1 与"core/export 集成只落接缝与判据"（§2 `:97`）之间存在一处**必须显式裁定的缺口**：接缝要么以"新增 seam 文件 + 一行注册"登记后由主会话应用，要么 item 8 的往返测试只能在注入器自身的纯字节层做，无法证明"导出链真的注入了"。
- item 6 需要 `MistakePdfExportInput` 新增来源标记，与上一条同属 core/export 改动面。

---

## 4. 四 · WS2 落点（schema / 迁移 / 备份清单 / D-1 / AUD-11）

| 问项 | 实测 | 证据 |
|---|---|---|
| schema 72 与 `72.json` | 均在树（见 §1）。`72.json` 有 **91 个 entity**、`version 72`、`identityHash aa5e8e8e…`、2 个 view（`library_catalog`、`knowledge_question_lattice`） | python 解析 `72.json` |
| 迁移注册点 | `core/database/…/StudyDatabase.kt:342-416` `.addMigrations(...)`，末条为 `KNOWLEDGE_SEARCH_MATERIAL_FEATURE_MIGRATION_71_72`（`:415`，注释 `:414`）；定义在 `core/database/…/KnowledgeSearchMaterialFeatureMigration_71_72.kt:22` | 读 `StudyDatabase.kt:371-420` |
| 备份清单"-表数" | **91 表**（不是题面猜的 92）：`categoryTables` 74（ISSUE_BOOK 31 + MASTERY 35 + SESSIONS 7 + IMAGES 1）＋ `alwaysTables` 7 ＋ `derivedTables` **3** ＋ `splitTables` 7 = 91；`trimOrder` **83**；`rowCountKeys` = 91 个键；`rebuildTables` 1；`views` 2。与 `72.json` 的 91 entity **双向零差集**（脚本比对：`missing from plan` = `[]`、`listed but not in 72.json` = `[]`） | python 计数 + 集合比对（文件 `core/data/…/backup/SmbkSelectionPlan.kt`） |
| `SmbkSelectionPlan` 常量 | `SCHEMA_VERSION = 72`（`:44`）、`IDENTITY_HASH = "aa5e8e8e6536e0c1f3adcda6867814cb"`（`:51`，与 `72.json` 同值）、`CLOSURE_VERSION = 4`（`:54`）、`CLOSURE_DIGEST_PREFIX = "smbk-selection-v4"`（`:57`）、`FULL_MASK = 15`（`:60`）、`allowedMasks = {9,13,15}`（`:63`） | 读 `:41-70` |
| 清单文档漂移（WS2 item 12 的直接风险） | 该文件**自身 KDoc 仍写 90/2/82**：`:22`"`derivedTables` 2 张派生索引"、`:180`"90 张表（类别 74 + ALWAYS 7 + DERIVED 2 + SPLIT 7）"、`:182`"90 个键"、`:185`"82 项裁剪顺序…= 90 − 7 − 1"。实际已是 91/3/83。另 `:56` 注释"digest 前缀，与 closureVersion=3 绑定"与 `:54` 的 4 自相矛盾 | 实读 + 计数 |
| D-1 现有载体 | D-1 = 3D **语义绑定精确率 ≥0.95**，"**D-1 过门 = 自动落账许可门**"。载体：`docs/REFACTOR-MASTER-LINE.md:86`（3C 行）、`docs/requirements-fused-final-2026-10-11.md:73`、`:114`。**当前状态=未通过**：`docs/research/2026-10-04-stage5-gate-check.md:15` 记 "❌ 未通过（只有抽样点估计 ≈89.4%…无任何 ≥0.95 结论）" | grep + 读 |
| AUD-11 现有载体 | AUD-11 = 判词/冷却/答案键三节反例报告（`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md`）；其中答案键"撤销/纠正/重放"三样皆无的缺陷已转 **KD-43**，`docs/known-defects.md:1934` 记 **fixed（2026-10-21，批次 16 收尾）** | grep 命中原行 |

**对计划的影响**：

- 计划 §1 item 11 说"schema 73（72 已被他线 knowledge_search_material_feature 占用）"——**与实测一致**（72 在树、已注册、entity 已导出）。但计划 §2 `:98` 写"**DB schema 71→72 由 WS2 单写者**"，与 §1 item 11/12（73）**直接冲突**：72 已由 KB L2 落定，WS2 只能写 72→73。计划内部需以 §1 为准修正 §2。
- item 12"备份清单同步（91→N 表、CLOSURE 4→5）"的起点值 **91 与 CLOSURE 4 均实测正确**；题面所问的"92"不成立（92 是 73 落地后的预期值）。
- item 15/16 复用 `D-1`、`AUD-11` 这两个**已被占用的审计编号**：D-1 现指 KB 绑定精确率门且**未通过**（其语义是"过门前一切标注只能建议+确认，不能自动落账"），AUD-11 现指 KD-43（已 fixed）。若 WS2 要用同一编号指"错因自动落账门/撤销语义"，必须在台账里写清是**同名新件**还是**续用**，否则后续核对会串号。
- **WS2 的单写者前提已不成立**：迁移注册文件 `core/database/…/StudyDatabase.kt` **本身是 ` M`**（他线在改，见 §2 明细），`KnowledgeSearchMaterialFeatureMigration_71_72.kt` 等 6 个 KB L2 文件仍是 `??`。也就是说 72 落地的整条链**尚未入库**。WS2 在其上再加 72→73，等于把两条迁移叠在同一个脏文件里——按 §2 `:98`"WS2 单写者"的原意，应当先确认 KB L2 那条链的入库时点，否则 WS2 无法单独归因自己的 diff。
- **复核轮更正（2026-11-02，晚于本基线落笔）**：KB 32B 已按「L2 不可判→回滚」删除 L2 的 71→72 迁移/实体；主会话裁定 **rebase 到 71**，schema 73 = 90 + 2 = **92 表**，直连 `MIGRATION_71_73`。本文件里“72 在树/91 表/需先确认 L2 入库”的读数保留为基线时点值，不再代表当前口径；当前口径见 `docs/research/evidence/2026-11-02-batch-40/step02b-schema73-migration-backup.md` §4。

---

## 5. 五 · WS3（复习调度）与 WS4（新任务类型）落点

### 5.1 WS3 现状

| 项 | 现状 | 证据 |
|---|---|---|
| FSRS 主体 | **已完整存在**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/FsrsScheduleMath.kt` 头注释为 "FSRS-6 scheduling math … verbatim port of py-fsrs `fsrs/scheduler.py`（source-verified 2026-08-29）"，含 `DEFAULT_PARAMETERS` 21 项（`:20-42`）、`PARAMETER_COUNT = 21`（`:48`）、`STABILITY_MIN/MAX`、`DIFFICULTY_MIN/MAX`、`INITIAL_STABILITY_MAX = 100.0`、`MAXIMUM_INTERVAL_DAYS = 36_500`（`:49-54`）、`RATING_*_WEIGHT`（`:248-251`）、`LOW_CONFIDENCE_CORRECT_CEILING = 0.85`（`:258`） | 读该文件 |
| 存储位 | `learner_problem_memory_state` 已有 `stability_days` / `difficulty` / `next_review_at_epoch_millis` / `lapse_count`（`core/model/…/LearningState.kt:794,818`；亦被 `library_catalog` 视图读取） | 读 `LearningState.kt`；`72.json` 视图 SQL |
| 考试容量爬坡 | **已有**：`core/data/…/study/StudyReviewPlannerService.kt:153` `examCapacityBudgetSeconds(...)`、`:867` `EXAM_RAMP_DAYS = 14`；消费点 `:309`、`:386`；考试窗口判定 `:394` | 读该文件 |
| 每日预算/配额 | `FULL_LIBRARY_SCAN_DAILY_CAP = 10` 在 `core/domain/…/KnowledgeReviewFullLibraryScan.kt:30`（**热区**），被 `StudyReviewPlannerService.kt:13,828` 消费 | grep |
| 欠账追赶 | 只有**错峰**：KF-17（`core/domain/…/LearningProjector.kt:1042,1122,1289` 注释；`core/data/…/StudyPracticeUnitFacts.kt:142`）。全仓 `rebalance` 零命中 → item 20"missed days rebalance"**不存在** | `grep -rin "rebalance"` → 0 |
| 诚实预览 `unschedulableCount` | **已有生产**：`core/domain/…/NewIntroductionPolicy.kt:90`（字段）、`:205`（`ordered.count { it.estimatedDurationSeconds > timeBudgetSeconds }` 产出）、`:241`（`CoveragePreview.unschedulableCount`）；消费侧另有 `StudyExperienceMappers` 与两处 JVM 用例（`StudyExperienceMappersIntakeTest.kt:36,52`、`StudySnapshotIntakeUnschedulableTest.kt`） | grep `unschedulable` |

- 与计划的偏差：item 18"容量爬坡模型（FSRS 三参数简化落 `core/model`）"**已被 FSRS-6 全量实现占位**，且实现在 `core/domain`（热区）而非 `core/model`。照原样做会得到第二套调度数学。item 19"考试截止日每日配额计算"`examCapacityBudgetSeconds` 已在；item 22"数据侧 `unschedulableCount` 生产消费的数据源"模型层已在，KD-68 缺的是 **UI 消费**（`docs/known-defects.md:2652`"`unschedulableCount` 无生产消费者"→ 指 UI 面）。
- F8/F9/F18 现状（`docs/requirements-fused-final-2026-10-11.md:115,117,119`）：F8 "🔌 TimeBudget 在、设置弱"；F9 "🔌 KF-17 错峰在、无开关"；F18 "❌"。
- `docs/current/data-schema.md` 与本批无冲突证据（本轮未逐行核对，标 UNVERIFIED）。

### 5.2 WS4 现状

| 项 | 现状 | 证据 |
|---|---|---|
| `ModelTaskKind` | **12 值**（`core/model/…/ModelTasks.kt:11-24`）：`CAPTURE_ASSESS, CAPTURE_PARSE, PROBLEM_CLASSIFY, PROBLEM_RELATE, TUTOR_PLAN, TUTOR_RESPOND, TUTOR_LOBBY, TUTOR_EVALUATE, REVIEW_RERANK, LEARNING_SUMMARIZE, IMAGE_PIPELINE_CLASSIFY, KNOWLEDGE_QUIZ` | 读主树文件（`.worktrees/kb-bench` 的同名文件是 14 值版本，**不是**本树事实） |
| 请求 schema 版本 | 18（见 §1） | `ModelTasks.kt:546-547` |
| gateway / 解析器 / 提示词落点 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/`：`OpenAiCompatibleModelGateway.kt`、`OpenAiModelTaskAdapters.kt`、`OpenAiModelResponseParsers.kt`、`OpenAiModelResponseParsing.kt`、`OpenAiModelProtocol.kt`、`KnowledgeQuizWire.kt`、`FigurePromptAssembler.kt`、`TutorToolContext.kt`、`wire/` 子包 | `ls core/data/…/model/` |
| 预算/ledger 接入点 | `ModelTaskRemoteDispatchPolicy`（`ModelTasks.kt:58-65`，`MAX_DISPATCHES = 3`）、`MODEL_EXTERNAL_TRANSPORT_REQUEST_LIMIT_BYTES`（`:68`） | 读 `ModelTasks.kt` |

- 计划 item 23 裁定"新任务类型放 core/model、提示词放 core/data model 层"——**与现状分层一致**（枚举在 core/model，提示词在 core/data/model），无冲突。
- item 27"复用 KD-82/M-04 分类面"：`ModelCallFailureDiagnostics.kt` 在 `core/data/…/model/`，是本批可见的失败分类载体（**本轮未读其分类枚举全集**，标 UNVERIFIED）。

---

## 6. 六 · WS5（AUD-08）与 WS6（KD-72 / KD-27）

### 6.1 WS5 / `tools/split_golden`

| 项 | 实测 | 证据 |
|---|---|---|
| 目录状态 | `tools/split_golden/`：`GATE.md`(8,075B)、`README.md`(8,049B)、`check_split_gate.py`(24,938B)、`export_split_eval.py`(19,514B)、`measure_split_accuracy.py`(29,347B)、`__pycache__/` | `ls -la tools/split_golden` |
| 门值 | **FAIL 维持**：`GATE.md:17` "当前判定：**门 = FAIL**（① 0.5714 < 0.9，③ 0.0000 ≤ 0.05；数据源为 4C 存量设备导出…）" | 读 `GATE.md` |
| 阈值出处 | ① 完整题命中 `COUNT_GATE` ≥ 0.9（`GATE.md:28`）；③ 切残 `FRAGMENT_GATE` ≤ 0.05（`:39`）；② 只度量无门（`:50`）；漏题/错误合并/重复率 **未立项**（`:60,68,76`） | 同文件 |
| 金标等级 | 冻结金标 v1 = 7 页/16 框，**模型标注代理金标，无人工专家**（`docs/current/visual-generation-contract.md:208`） | 读该行 |
| 逐页裁定现状 | `export_split_eval.py` 的产物**只有** `pages[].{file,nameSource,questionCount}` + `payload.sha256/pages/boxes` + `nameSourceCounts`（`:266-317`）——**没有**"页 id/差异/裁定人"字段 ⇒ item 30 是新增 schema | `grep -n "pages\|ruling" export_split_eval.py` |
| "app-split 生产者" | `tools/` 下**无** `app_split` 命名的工具；生产者面前就 `export_split_eval.py`（导出设备库 → 逐页框数）+ `measure_split_accuracy.py`（比对金标） | `ls tools/ \| grep -i split` |

### 6.2 WS6 / KD-72 六 build 面

`docs/known-defects.md:2758` 起（KD-72）定义的 "W1 六文件" 本轮逐文件实跑：

| 文件 | 现状 |
|---|---|
| `settings.gradle.kts` | **CLEAN** |
| `core/ui/build.gradle.kts` | **CLEAN** |
| `feature/tutor/build.gradle.kts` | **CLEAN** |
| `core/export/build.gradle.kts` | **CLEAN** |
| `core/markdown/build.gradle.kts` | **CLEAN** |
| `.gitignore` | **` M`** |

⇒ **1/6 不干净**（历史各轮记录为 **6/6 不干净**，见 KD-72 批次 30/31/33/34/35/36/39 各 Progress 段）。KD-72 的 W1 条件已实质改善。

锁状态（同轮实跑）：

| 项 | 实测 | 命令 |
|---|---|---|
| 跟踪锁 | **0** | `git ls-files '*lockfile' \| wc -l` |
| git 可见未跟踪锁 | **14**（app、core/{data,database,domain,export,markdown,model,ui}、feature/{capture,library,profile,review,tutor}、`settings-gradle.lockfile`） | `git status --porcelain \| grep -i lockfile` |
| 盘上 `*.lockfile` | **14** | `find . -maxdepth 3 -name "*.lockfile" -not -path "./.git/*" -not -path "./.worktrees/*" \| wc -l` |
| `:benchmark` 锁 | 不存在 | `ls benchmark/gradle.lockfile` → No such file or directory |

- 与历次记录的差异：`core/markdown` 目录已入库（不再折叠为 `?? core/markdown/`），故"git 可见未跟踪"从 **13 升为 14**，与"盘上 14"**首次一致**。KD-72 关闭条件仍缺 W2（树未收口，548 条）、W6（负控从未执行）、W7（Gradle 未跑）。

### 6.3 WS6 / KD-27 CI 配置位置

| 项 | 实测 | 证据 |
|---|---|---|
| 工作流 | `.github/workflows/android-check.yml`：`check` job（`:33`）、`instrumented` job（`:188`）；commit-back 步骤条件 `if: success() && github.event_name == 'push' && github.ref == 'refs/heads/main'`（**`:180`**；KD-27 文内引的 `:145` 已漂移） | `grep -n "if: success()"` |
| 触发面 | `pull_request.paths` 含 `**.kt`/`**.kts`/`app/**`/`core/**`/`feature/**`/`gradle/**`/`**/gradle.lockfile`/`tools/**`/`knowledge/**`/`core/data/src/main/resources/knowledge/**`；`push.branches=[main]` | 读 `:3-30` |
| 另一工作流 | `.github/workflows/schema-export.yml` = **TEMPORARY**（自述 "DELETE this file and SchemaDumpInstrumentedTest.kt once aligned"），只由自身路径/手工触发 | 读 `:1-20` |
| 门脚本 | `tools/ci/`：`check_dependency_pins.py`、`check_lockfiles.py`、`check_file_size_gate.py`、`check_pack_sync.py`、`check_r04_keep_rules.py`、`check_release_manifest.py`、`check_stdlib_compat.py`、`check_visual_test_seams.py`、`check_figure_entry_gates.py`、`generate_status.py`、`run_kb_checks.py` | `ls tools/ci/` |
| CI 当前红因 | **UNVERIFIED**：本轮未跑 `gh run view`（网络/只读纪律）。KD-27 条目内最后记录的复读停在 2026-10-24（`gh run 39818426751`，红因 = HEAD 缺 `core:export` 的 `kotlinx.serialization` 与 `core:database` 的 `RevisionImageSource`）——该两处**现在是否已在树**，需要另跑一次 `gh run list` 才能定，本轮未做 | `docs/known-defects.md:1061-1095` |

- KD-27 的"可修项落码"因此只能从**只读静态面**起步：`if: success()` 位于 `:180`，是本条最确定的静态事实。

---

## 7. 七 · 设备在线状态（只读）

命令：`/c/Android/Sdk/platform-tools/adb.exe devices`；`… -s <serial> shell getprop ro.build.version.sdk`

| 序列号 | 状态 | API |
|---|---|---|
| `emulator-5562` | `device` | 34 |
| `emulator-5566` | `device` | 34 |
| `emulator-5584` | `device` | 34 |
| `emulator-5596` | `device` | 23（最低兼容面） |

- 4 台在线，其中 API 34 三台、API 23 一台。本轮**未安装 APK、未跑任何套件**，因此"设备可用"仅指 adb 可见；设备上当前装的构建版本、库状态一律 UNVERIFIED。
- 与计划 §1 item 45-51 / WS8 的关系：设备轨有 4 台可选，串行执行有余量；KD-76 校准（item 49）要求"n=20 真冷启动（reboot 每样本）"，单台 reboot 20 次即可满足，无需多台并行（并行会引入负载门污染）。

---

## 8. 与计划 §0/§1 不符处（汇总，按严重度）

| # | 项 | 计划写的 | 实测 | 影响 |
|---|---|---|---|---|
| 1 | **§0 深度研究"本轮已完成，见 §1 研究线落盘"** | §0 `:6` 断言研究结论已进 §1；§1 R1 注 "（已起，落 `docs/research/2026-11-02-pdf-incremental-aigc.md`）" | **§1 点名的 8 份研究文书全部不存在**：`2026-11-02-pdf-incremental-aigc.md`、`-error-taxonomy.md`、`-fsrs-capacity.md`、`-model-task-layering.md`、`-aud08-annotation.md`、`-keeper-l8.md`、`-core-visual-module.md`、`-kd71-race-review.md` 逐个 `test -f` → MISSING。`git ls-files` 中 `2026-11-02` 命中 **0**；`docs/research/` 下唯一 11-02 文件是**未跟踪**的 `2026-11-02-kb-l2-material-features-prereg.md` | §0 的"已完成"没有任何落盘凭据；R1–R8 是**未交付**项，不是已起项。§0 引用的 pyHanko/pdf.js/PdfPig/utilvo 结论本轮无法证实（未联网），按未验证处理 |
| 2 | **§2 `:98` "DB schema 71→72 由 WS2 单写者"** | 71→72 | DB 已是 **72**（`StudyDatabase.kt:140`），`72.json` 在树，`71→72` 迁移**已注册**（`:415`），entity/迁移/测试文件为**未跟踪新文件**；而注册文件 `StudyDatabase.kt` 本身是 ` M` | 与 §1 item 11"schema 73（72 已被…占用）"**自相矛盾**。以 §1 为准：WS2 写 72→73，§2 该行需更正。"单写者"前提要求 KB L2 那条链先入库 |
| 3 | **§1 item 12 "91→N 表" / 题面 "92"** | 91→N；题面问"当前 92?" | 当前 **91**（74+7+3+7，与 `72.json` 91 entity 零差集）；`trimOrder` **83**；`CLOSURE_VERSION = 4`、前缀 `v4` | 起点值 91/4 正确；"92"是 73 落地后的目标值，不是现值。**另**：`SmbkSelectionPlan.kt` 自身 KDoc 仍写 90/2/82（`:22,:180,:182,:185`）、`:56` 注释仍说 `closureVersion=3` —— 这些漂移会在 item 12 同步时被一起改动，需先定"KDoc 是否属于本批范围" |
| 4 | **§1 WS8 计数** | 标题 "WS8 设备（**5 项**）"；正文编号 `45,46,47,48,49,50,51` = **7 项**；总额 "80 = 49 实施 + 8 研究 + 23 流程" | 逐节数：WS1 8 + WS2 8 + WS3 6 + WS4 6 + WS5 5 + WS6 5 + WS7 6 + WS8 **7** = **51** 编号项 | 51 ≠ 49；51+8+23 = **82** ≠ 80。§2 `:93` 自己也退让为"49+8+11=68"。计数口径需一次澄清，否则验收矩阵无法逐项勾稽 |
| 5 | **§1 item 18 "FSRS 三参数简化落 core/model"** | 待建 | FSRS-6 **已全量存在**于 `core/domain/…/FsrsScheduleMath.kt`（21 参数、source-verified、含 rating 权重与低置信上限） | item 18 若照写会产生第二套调度数学；且落点 `core/model` 与现实现 `core/domain` 不一致（后者是热区）。item 19/20（exam quota / rebalance）：exam quota **已有**（`examCapacityBudgetSeconds`、`EXAM_RAMP_DAYS=14`），rebalance **不存在**（零命中） |
| 6 | **§1 item 22 "unschedulableCount 生产消费的数据源"** | 待建 | 模型层与产出**已在**：`core/domain/…/NewIntroductionPolicy.kt:90,205,241`；两处 JVM 用例已在用 | KD-68 缺的确实是 **UI 消费**（`known-defects.md:2652`）；item 22 实际范围应收窄为"接 UI + 数据源判据"，不是新建数据源 |
| 7 | **§2 `:97` 热区禁改 vs 现状** | 视为干净基线 | 热区**开工即脏 6 处**（core/ui 1、core/export 3 test、core/domain 2） | 无法用"文件是否变"归因；必须逐路径核对暂存区。core/export 的 3 处全在 test，对 WS1 item 8 的"导出链往返"判据有直接影响（同一测试目录已有他线改动） |
| 8 | **WS1 item 8 的注入点** | 视为存在 | `MistakePdfExporter.prepare()`（`core/export/…/MistakePdfExporter.kt:117-140`）在 `render` → `publish` 之间**无接缝**；`core/data/export` 只有记录行 | "只落接缝与判据"需要先定义"接缝是什么文件、由谁应用"。否则 item 1 的注入器在导出链上无消费者，item 8 只能退化为纯字节层自测 |
| 9 | **KD-72 六 build 面** | 隐含 6/6 脏（历次记录口径） | **1/6 脏**（仅 `.gitignore`）；未跟踪锁 **14**、跟踪 0、`:benchmark` 无锁 | KD-72 的 W1 已不构成阻碍；关闭还差 W2（548 条树未收口）、W6（负控）、W7（Gradle） |
| 10 | **KD-27 的 CI 行号** | KD-27 文内引 `android-check.yml:145` | 实际 `if: success()` 在 **`:180`** | 台账行号漂移；CI 当前红因本轮未取（未跑 `gh`），UNVERIFIED |
| 11 | **D-1 / AUD-11 编号复用** | §1 item 15/16 用作"错因自动落账门 / 撤销语义" | `D-1` 已是 3D 绑定精确率 ≥0.95 门（**未通过**，`stage5-gate-check.md:15`）；`AUD-11` 已指向 KD-43（fixed） | 同名异指，台账会串号。且 D-1 未过门的既有裁定是"自动落账不许开"——item 15 的"门"必须是**拦**不是**放** |
| 12 | 计划 §1 item 29 "app-split 生产者" | 视为已有工具 | `tools/` 无 `app_split` 命名工具；生产者实为 `tools/split_golden/export_split_eval.py` + `measure_split_accuracy.py` | 命名需对齐，否则找不到落点 |

---

## 9. 本轮未验证项（明确列出，不当作已过）

| 面 | 状态 | 原因 |
|---|---|---|
| 任何编译 / 单测 / 仪器化 / 强门 | UNVERIFIED | 基线轨零 Gradle |
| 设备上现有构建版本、库状态、套件结论 | UNVERIFIED | 只做了 `adb devices` + `getprop` |
| CI 当前红/绿 | UNVERIFIED | 未跑 `gh run list`（网络） |
| §0 PDF 增量更新结论（"`PdfDocument` 无 Info 字典 API"、pyHanko/pdf.js/PdfPig 参考） | **未验证**（转述计划原文） | 未联网、未查官方文档；不得当事实引用 |
| TC260 `/AIGC` 字段表与 `GeneratedFigureMetadata.kt` 的 XMP 三字段是否同构 | 未验证 | 未逐字比对 `docs/research/2026-10-25-ai-content-labeling-compliance.md`（文件存在，本轮未读） |
| `ModelCallFailureDiagnostics` 的失败分类全集 | 未验证 | 未读该文件 |
| `docs/current/data-schema.md` 是否已同步 72 | 未验证 | 未读 |
