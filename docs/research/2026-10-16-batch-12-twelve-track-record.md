# 十二轨批记录：3E 全库扫描 + 6C 备份扩展 + AUD-09 净化作业 + T9 语义修复（批次 12，2026-10-16/17）

计划来源：`.zcode/plans/plan-2026-10-16-batch-3e-6c-aud09-t9.md`（多条轨的材料引用其 T1/T2/T8 段）。
运行脚本：`.zcode/workflow-drafts/长轮十二轨-3E全库扫描--6C备份扩展--AUD09净化作业--T9语义修复.dwf.ts`
（单测门证据称本批为「十条任务书 BRIEF_T1..T10」；两段强门禁命令读自该脚本 `:361-367`）。

**记录口径**：本记录的事实来自本轮运行给出的批次材料（T1–T10 逐轨材料 + 单测门/强门禁结论 + 设备段），并逐处注明来源
（轨自述 / 门禁执笔 / 设备执笔 / 本记录者只读核对）。**本记录者未运行 Gradle、未接设备、未做独立复核**；凡「通过」
均注明由哪一环跑出。材料未覆盖处一律写「未记录」。

**材料完整性声明**：所附逐轨材料为 **T1–T10 共十轨**；设备段在「3b) feature:library 全量 …」处**截断**（见 §3.7），
其后内容未记录。材料另处提到 **T12**（设备核，见 T4 未验证项）与 **T13**（门禁串行跑 / 记录折入 / 对抗性复核）为本批
其他角色，未附逐轨材料；「十二轨」的完整轨目材料未说明 → 未记录。

**本记录者只读核对（2026-10-17 05:38 前后）**：① 十轨 `files` 并集 **83 个路径**在共享工作树**全部存在**（材料 T7
`files` 里的 `SmkbArchiveCodecTest.kt` 为误拼、实际为 `SmbkArchiveCodecTest.kt`：误拼不存在、后者存在，§6 清单已按实写）；
② `git log -1` HEAD=`9fbb534c`（KB 线批次 12 提交）、暂存区 0 条、`git status --porcelain` 500 条（含他线改动与未跟踪产物）；
③ 目标文件 `docs/research/2026-10-16-batch-12-twelve-track-record.md` 此前不存在（本记录即新建）。

---

## 1. 十轨结果总表

| 轨 | 文件数 | 结果 | 实跑证据（来源） | 未达 / 未验证 |
|---|---|---|---|---|
| T1 切分回执读侧对账（设备段标注 KD-39） | 10 | 读侧对账落地；`markConfirmed` 保留裁定；5 例新 instrumented 已写 | 轨内：SQL 自检（python+sqlite3）、命名/采集点/双件 grep、括号平衡 10/10。设备段实跑 `SplitImportReceiptReconciliationInstrumentedTest` **5/0** | 轨内零 Gradle/零设备（后段设备已跑 5/0）；复核页仍按 UI 快照选题（非原子、未加防抖）；对账查询全表索引扫描未压测 |
| T2 状态页绑定时刻说明（KD-40） | 3 | 生成物头部注明落地（+58/−0） | 轨内实跑 `python -m unittest discover -s tools/tests -p "test_ci_generate_status.py" -t tools` → **16 tests OK**（基线 14）；4 类变异探针各 1 failure；端到端渲染 `status.main()` exit 0、头部含新句 | 未重生成 `docs/status.md`（CI 生成物）；KD-40 未回填（归 T13）；未跑 Gradle/设备（派单禁止） |
| T3 小测损坏行覆盖重写（KD-41） | 7 | 覆盖重写落地（新端口 + `@Update` + 假库镜像）；新 2 例；第 5 例一条断言按「损坏行=不可复用」改写 | 轨内：括号平衡 7/7、room3 jar 注解可用性核对、实现面穷举。设备段实跑 `:core:data:testDebugUnitTest --tests *KnowledgeQuizItemStoreTest*` → **7/0/0** | 轨内零 Gradle（KSP/编译未验证）；真库路径未跑（未加仪器化）；zero bump 为推断；docs 未改（T13 关账） |
| T4 导入中断提示读口（KD-42） | 7 | 读口三块（staging 纯函数 + 域口 + 入口提示）落地；5 例 JVM + 2 个 instrumented 文件落盘 | 轨内：规则复算 16 断言全绿、括号 7/7、grep 单点。设备段：`BatchImportInterruptedStagingInstrumentedTest` **1/0**；feature:library 全量 **49/0**（含新提示类 2 例；材料截断） | 轨内零编译零跑（`where kotlinc` 无命中）；staging 与大厅附图共用、提示实时性边界未设备验证；三个共编文件不能按本线独有提交 |
| T5 3E 全库扫描第二档 | 11 | 第二档落地（域常量/纯函数 + 并集队列 + 单条 SQL 读口 + 两档装配 + spec 改写） | 轨内 python：schema 65.json 真表 SQL 语义（含反面断言 + EXPLAIN）、5000 节点规模 best-of-3 **12.1ms**、发布 SQL 与验证 SQL 逐字一致、括号平衡 10/10 | Kotlin 用例未跑（无材料证据）；「每日 ≤10」口径待 owner 裁定；候选先修图成本未量化；设备 S9/S17 未做 |
| T6 AUD-09 净化作业持久化 | 22 | schema 66 表 + 非破坏迁移 65→66 + 可续跑 runner + 指纹兼容落地 | 单测门：`CleanRedrawTaskRunnerTest` **17/0**、core:database **192/0**（本轮新跑）；设备：迁移矩阵 **13/0**；但 `CleanRedrawTaskInstrumentedTest` **2 例 1 红**（`.kt:68` 确定性，3/3 复现） | 66.json 为构建产物（提交清单须含）；1 例设备红归因/处置未记录；两处口径判断待人工复核；并发窗口与升级边界未验证 |
| T7 6C-1 备份清单 v2 + settings.json | 8 | formatVersion 1→2 + settings.json + 恢复期设置写落地 | 轨内非 Gradle 编译 + JUnit：`COMPILE_STATUS=0`、**OK (20 tests)**（13 既有 + 7 新）；Android 侧类型检查本轨零诊断。设备段实跑 `BackupRestoreInstrumentedTest` **25/0** | Gradle 门禁未由本轨跑；设置写失败 partial 未设备复现；Phase 8 时序未注入验证；内容勾选=6C-2；contentVersion 退化边界未测 |
| T8 判词锚定 + 归属落账 | 4 | 锚定门 + 归属落账 + 快照 id 内容寻址落地；9 例新单测（写就未跑） | 轨内：括号平衡、`git diff --stat`、行尾核对；指纹/投影读核实证（`LearningLedgerFingerprint.kt:147-172`、`LearningProjector.kt:933-944/:1306-1322`） | 零 Gradle/零设备（材料未列其运行）；「驱动证据 id 落账」未达；真库五元组核对未被假库镜像；投影后果与 0.15 上限张力待复核；zero bump 待 T13 |
| T9 AUD-11 两件（冷却限通道 + 可观察性/审计口；KD-43 登记） | 21 | 两件落地；零 bump 依据 §3.21 | 设备段实跑 `KnowledgeQuizCooldownChannelScopeInstrumentedTest` **7/0**、`KnowledgeQuizEvidenceAuditInstrumentedTest` **3/0**（设备段分标 T9/T10，见 §3.3 注）；轨内 python 静态度量（21 文件 delta=0）+ 调用点扫描；独立 kotlinc 编译被安全 hook 拒绝（无编译级证据） | 轨内零 Gradle；「载荷带题面/键版本引用」解释面（推导式 vs 真列）待复核；TutorToolTrace 判定为读码；洪泛面无实测；共享文件整份一致性待门禁/复核 |
| T10 KD-38 残余代理对截断 + 台账 §3.20 | 3 | `takeBudgetChars` 替换 `Progress.of` 的 take；11 例新测（写就）；台账 §3.20（+17/−0） | 轨内 python：22 断言全绿、台账检查通过、`git diff` 两处区段核对、file size gate（nothing to gate） | core:model 用例未跑（材料：其绿非本轮证据）；活截断点未修；KD-38 表述未同步；zero bump 无冻结测试实证 |

---

## 2. 各轨明细

### T1 切分回执读侧对账（KD-39）

按计划 T1 段（`.zcode/plans/plan-2026-10-16-batch-3e-6c-aud09-t9.md:14-18`：修法=读侧对账；顺带裁定 `markConfirmed`；
验收=instrumented「提交后被杀→重启/进复核→追平 SAVED 且续做下一题」）与 `docs/known-defects.md:1841-1857` 的关闭条件落地
（缺陷条同时列出「复核页无放弃出口」这一后果）。

- **修法**：新增一个台账补齐操作——DAO 一条 JOIN 取「草稿 `status=COMMITTED` 且台账 `confirm_state=PENDING`」的题；
  `RoomSplitImportStore.reconcileCommittedQuestionDrafts` 在同一个写事务里置 SAVED + touchJob，幂等、返回笔数。
- **三处读触发点**：`readNextUnsubmittedDraft`（续做下一题不再被送回已录过的题；改前现读 `RoomSplitImportRepository.kt:106-118`）、
  `observeActiveImports`（复核页装配；生产唯一采集点 `feature/library/.../SplitImportReviewRoute.kt:77`，故 feature:library
  一行未改）、`SmartMistakeBookApplication` 启动对账（`SmartMistakeBookApplication.kt:431-453`，不进复核页也能追平）。
- **取舍**：未走退路（回执并入 `confirmAndCommitProblemDraftFromWorkspace` 同一写事务）。理由：读侧补写本身就是一个写事务内的
  批量 UPDATE（无跨事务 check-then-act）、与并发回执写的是同一行同一值（未发现幂等/并发冲突）；且读侧能追平「台账已写坏的
  历史行」与「讲题 `saveTutorSession` 路径丢失的回执」这两类退路覆盖不到的情形（改前现读：`RoomCaptureWorkflowRepository.kt:439-494`，
  `:480` 的 `recordSplitImportCommit` 在提交之后、非同一事务；`RoomSplitImportStore.kt:119-138` 幂等回执；
  `SplitImportReviewRoute.kt:163-217`、`:230-234`「已录入」判定）。
- **裁定**：`markConfirmed` 保留，在 domain 接口与 core:data 实现两处 KDoc 注明「无生产调用方，保留给对账/续做路径」。
- **测试**：新增 core:data instrumented 5 例（`SplitImportReceiptReconciliationInstrumentedTest.kt`，255 行、5 个 @Test：
  提交后被杀→续做/重启/进复核三种追平路径 + 幂等 + 废弃草稿与不在台账草稿两个负向）；既有 core:data 3 例
  （`SplitImportCommitReceiptInstrumentedTest`）与 feature:library 7 例（`SplitImportReviewCommitFlowInstrumentedTest` 3 +
  `SplitReviewEntryInstrumentedTest` 4）未改。
- **证据（轨内自跑，非 Gradle）**：SQL 自检（python + 标准库 sqlite3 `:memory:`，把 `SplitImportDao.kt` 新查询原文正则抽出执行，
  表按 `entity/SplitImportEntities.kt:43-87`、`entity/CaptureEntities.kt:44-74` 搭）——只命中「PENDING 且草稿 COMMITTED」一行
  （job-1/0），SAVED 行 / EDITING 草稿行 / ABANDONED 草稿行 / `split_draft_id=NULL` 行 / 不在台账的已入库草稿全部不命中，
  末行 OK 与 `assert rows == 单行` 通过；括号自检 10 文件 `ALL BALANCED`；`grep -rn "reconcileCommittedQuestionDrafts" core feature app`
  = 6 处（store/port/`RoomStudyDatabase`/repository/两处测试双件），无旧名残留；接口新增方法的必改双件已同步
  （`SplitImportPortFake.kt:174-178` 带 KDoc「假件没有草稿事实，如实返回 0」、`RoomBackedStudyExperienceRepositoryTest.kt:2707`），
  其余 `StudyDatabasePort by delegate` 包装器无需改；观察者核对——生产采集点只有 `SplitImportReviewRoute.kt:77`，批/拍照切分
  只调 `createSplitJob`/`markReady`（`BatchSplitRecognizer.kt:101,126`、`RoomCaptureWorkflowRepository.kt:386,392`），对账不进这些写路径。
- **零 bump 依据**：本轨 diff 不含 schema/迁移/DDL 改动（无新表列、无 version 常量），不触碰任何 `documentFingerprint`/
  `producerVersion`/内容版本；`algorithm-version-ledger.md` 无该域登记项，故无 bump 义务。
- **未跑（轨内声明）**：任何 Gradle 任务、任何设备/ adb——所以轨内没有编译或 instrumented 的通过证据。后段设备已实跑该
  instrumented **5/0**（§3.3）。
- **边界（轨内声明）**：复核页按钮仍基于界面快照选题（先有 `collectAsStateWithLifecycle` 快照、后有一次点击），对账把
  「必然重开已录过的题」的窗口收窄为一次快照延迟，但不是原子操作；本轨未新增 UI 级防抖（超出范围）。对账查询不带作业状态
  过滤（有意——已入库的题不因任务收尾而改变事实），因此历史题行上是一次全表索引扫描；只在启动 / 进复核 / 续做三个低频道
  触发点各跑一次，未做规模压测。

### T2 状态页绑定时刻说明（KD-40）

- **修法**：`tools/ci/generate_status.py` 新增常量 `RUN_CONTEXT_NOTE`，`run_context()` 把它作为值返回（刻意不按 BOUND/UNBOUND
  分支——头部两种情况都印 sha/branch，定义必须与绑定结果无关）；`.github/workflows/status-template.md` 在 Run context 行正下方
  加一行承载。KD-40（`docs/known-defects.md:1859-1872`，Symptom 指向 `generate_status.py:432-470`，Fix direction 首选「生成物
  头部补一句」）两条修法中选了「生成物头部注明」（CI commit-back 后重生成是自指，不可解）。
- **测试**：既有 14 例（LintSummaryTest 4 + CompileEvidenceTest 4 + RunContextTest 4 + JobAndOverallTest 2）全部原样保留
  （diff 纯新增、0 删除）；3 个 RunContext 用例补文案等值断言钉住字面量；另加 2 例对真模板对账（占位符必须存在且紧贴
  Run context 行、替换后必须真的出现该句而不被 NOT_MEASURED 扫描兜掉），用例名
  `RunContextNoteTemplateTest.test_note_placeholder_sits_directly_under_the_run_context_line` / `test_note_survives_rendering`。
- **实跑（轨内）**：基线 `python -m unittest discover -s tools/tests -p "test_ci_generate_status.py" -t tools` → `Ran 14 tests` / OK；
  改动后同命令 → **`Ran 16 tests` / OK**（`-v` 复跑原 14 个用例名逐条在列）；变异探针 4 类（模板副本删行 / 挪到文件末尾 /
  占位符改名 `{{NOTE_MISSING}}` / `patch RUN_CONTEXT_NOTE`）各 1 failure，未变异副本两条用例均 pass；端到端渲染（`status.OUTPUT`
  指临时文件后调 `status.main()`，CI 同一条路径）exit 0，页面头部逐行为：
  `- **Commit SHA**: \`fa265487d7d4...\``、`- **Branch**: \`main\``、`- **Triggered By**: \`unknown\``、
  `- **Run context**: BOUND · sha \`fa265487d7d4\`（git HEAD） · branch \`main\` · 触发 unknown`、
  `- **Run context note**: 绑定时刻 = 生成时刻，非所描述代码的提交`；`{{RUN_CONTEXT_NOTE}}` 残留=False；该句出现在页面=True。
- **未误伤 CI 生成物**：`docs/status.md` 不在改动列表（本线未重生成、未提交该页面）；本轨三个文件 `git diff --stat` =
  **58 insertions(+), 0 deletions(-)**（.github 模板 +1、generate_status.py +13、测试 +44）。
- **未达**：未重新生成 `docs/status.md`（CI 生成物；本批提交里该页面仍是旧渲染，新文案首现于下次 CI 产出）；未改
  `docs/known-defects.md`（KD-40 仍 open，记录折入归 T13；同文件另有 T9 登记 KD-43，避免并发改同一处）；端到端渲染内部工具
  套件（TOOLS_TESTS_*）计数未回读（只验证 exit 0 且页面正确）。
- **给 T13 的提醒（轨内声明）**：计划 T13 记录的「T2 可变缝 KDoc 注记（保留 + 默认 null 断言）」（plan:76）与上一批 T2 的失败
  诊断通道 `ModelCallFailureDiagnostics.sink` 有关（`docs/research/2026-10-16-verdict-chain-and-nine-track-batch-record.md:90、:383`），
  本轨 T2 无任何可变缝/默认 null；如 T13 按本批编号直解会挂错轨。

### T3 小测损坏行覆盖重写（KD-41）

- **修法**（`docs/known-defects.md:1874-1886` KD-41，Fix direction「损坏行按『不可复用』处理：读到解不开的行即覆盖重写」）：
  `KnowledgeQuizItemStore.save()` 撞 `ImmutablePayloadConflictException` 后「先分辨这行还能不能解」——解不开（损坏行）就
  **原地整行覆盖重写**；解得开的既有行照旧回读、绝不覆盖（「首见即真」语义逐字未动，只在冲突分支里多了一条「既有行解不开」
  的路）。
- **改动点（改后行号，轨内已逐段 diff 复核）**：`KnowledgeQuizItemStore.kt:73-104` save（`:91-102` 冲突分支：
  `existing!=null` 且 `decode(existing)==null` → `overwriteAssessmentItemSnapshot`）；`LearningProjectionPort.kt:50-64`
  新端口方法与契约 KDoc；`LearningDao.kt:8` 加 `import androidx.room3.Update`、`:169-179` 新
  `@Update abstract suspend fun overwriteAssessmentItem(...): Int`；`RoomStudyDatabase.kt:1351-1361` 实现（先
  `validateAssessmentItem` 再 `toEntity` 覆盖）；`RoomBackedStudyExperienceRepositoryTest.kt:2912-2919` 假库镜像
  （`containsKey` 才写，「只覆盖既有行、无行不新增」）；`StudyExperienceRepository.kt:361-372` KDoc 口径同步。
- **为何动 core:database**：唯一写口 `saveAssessmentItemSnapshot` 的插入带 IGNORE + 载荷比对冲突闸，无法覆盖既有行；
  替换而非先删后写，是为避免 `assessment_event` 的 CASCADE 与「行短暂不存在」窗口。
- **调用链（读证）**：`RoomBackedStudyExperienceRepository.kt:731-748` persist/read 仅转发 store；`SmartMistakeBookRoot.kt:613-625`
  生成成功后返回值 `?.let(Ready) ?: Unavailable`（非 null 即「可显示」）；`KnowledgeReviewSessionScreen.kt:134-141`
  FAILED→「重试出题」→ `viewModel.loadCurrentQuiz(loadQuiz)` 再走同一 persist。
- **测试**：`KnowledgeQuizItemStoreTest.kt` @Test 计数 **7**（原 5 + 新 2）；`:160-228` 第 5 例——**写侧一条断言按本轨裁定改写**：
  原「坏行上再落一次 → null」正是 KD-41 本体（死路），改后同处断言「覆盖重写并读回」，读侧 `assertNull` 断言原样保留
  （若保留旧断言则与本轨要求的「第二笔成功且可显示」逻辑上不可兼得，按任务书「损坏行=不可复用」口径写定）；
  `:235-267` `aCorruptedRowIsRewrittenSoRetryCanRecover`（4 种坏行：选项截断、schema v99、答案截断、形状不合法
  `correctChoiceId=C`；每轮断言第二笔非 null 且题干正确、能读回、`kc-other` 旁身份不变、行数仍 2）；`:273-296`
  `aRepairedRowIsImmutableAgain`（修复后第三笔不得覆盖）。
- **证据（轨内自跑）**：括号平衡 7 文件 `ALL_BALANCED`；实现面穷举——全仓直接实现 `StudyDatabasePort` 的只有
  `RoomStudyDatabase.kt:40` 与 `RoomBackedStudyExperienceRepositoryTest.kt:1756`（FakeStudyDatabasePort），其余
  （BackupRestoreInstrumentedTest:400/412、MistakeOrganizationRepositoryInstrumentedTest:240、
  RoomTutorKnowledgeContextLoaderTest:195 等）都是 `by delegate`（委托会转发新抽象方法），全仓无 mockk/Mockito；
  `androidx.room3.Update` 在本地 `room3-common-jvm-3.0.0.jar` 中存在（仅证明注解存在，未编译）；`git diff --stat` 7 文件；
  与他轨 hunk（T1 `reconcileCommittedQuestionDrafts`、T9②/T10 chat evidence 回执与 `lastAcceptedChatEvidenceAtForKc`
  加 `sourceKind`）文本不重叠。
- **零 bump 依据（推断，已核事实面）**：`core/database/schemas/…StudyDatabase` 最新为 65.json、`database.version=65`、83 表
  （python 读 JSON），与 `StudyDatabase.kt:224` `STUDY_DATABASE_VERSION` 同源；本轨只加 DAO 方法与端口方法，未动实体/迁移，
  `@Update` 不产生 DDL。
- **未跑**：轨内零 Gradle（KSP 对新增 `@Update` 的处理未验证；真库/设备级「损坏行 → 覆盖 → 读回」未跑、未新增仪器化）；
  zero bump 未做 schema 导出比对实测。后段设备实跑 JVM `:core:data:testDebugUnitTest --tests *KnowledgeQuizItemStoreTest*`
  → **7/0/0**（§3.5）。
- **未达**：`docs/known-defects.md` KD-41 仍标 open（关账、批次记录、主线台账折入按计划归 T13）；共享文件
  （KnowledgeQuizItemStore.kt、RoomStudyDatabase.kt、RoomBackedStudyExperienceRepositoryTest.kt）另有他轨同批改动，
  若他轨整文件重写本轨 hunk 可能被覆盖（轨内声明）。

### T4 导入中断提示读口（KD-42）

- **三块**：① 读口加在 `AndroidBatchImportSourceStaging`（`BatchImportSourceStaging.kt:40` 类、`:63` 接口声明、`:194` 实现；
  回收判定抽成纯函数 `:254 orphanedStagingSessions`、`:276 reclaimableStagingSessions`，`reconcileOrphans`（`:197`）改用它；
  uri 形状校验复用 `batchImportProviderAuthority`/`SESSION_ID`/`PAGE_FILE_NAME`（`:19`、`:16-17`、`:211-222 ownedSessionId`）；
  启动调用点 `RoomBatchImportRepository.kt:89-99`（init 里以全部任务页 uri 为引用集））；② 域层读口
  `BatchImportRepository.hasInterruptedBatchImport`（`BatchImportRepository.kt:191`）+ `RoomBatchImportRepository` 实现
  （`:121`，引用集与启动对账同一口径）；③ `BatchImportRoute` 入口顶部按 testTag `batch_import_interrupted` 渲染
  「上次导入被中断，可重导」（`BatchImportRoute.kt:230` 条件渲染、`:336–363` 卡片、`:89–98` 读口只读一次并按最新任务 id
  刷新）；读口为假时该节点不存在。
- **关键取舍**：判据取「staging 里存在未被任何任务认领的会话」而不是「目录非空」——切分未结时在册任务保留的暂存不会被
  误报为中断（这正是 KD-42 现场与正常导入的分界）；60 分钟宽限只决定何时回收、不决定提示是否存在（宽限内仍为真、回收后
  转假）。已写进三处 KDoc（staging 接口 `:35-62`、域接口 `:176-190`、提示卡片 `:326-334`）。
- **证据（轨内自跑，非 Gradle）**：`python …/kd42_staging_rule_check.py` → 16 条 spec 级断言全绿（在真实 Windows 临时目录上
  重放 orphaned/reclaimable 规则与单测断言，含 mtime 毫秒回读）；`kd42_bracket_check.py` → 7/7 文件 `ok`；文案单点
  （`grep "上次导入被中断，可重导"` 唯一渲染点 `BatchImportRoute.kt:351`；两个仪器化用例断言同一字面量
  `BatchImportInterruptedNoticeInstrumentedTest.kt:46`（真）`:71`（假））；testTag 单点（UI `:338`、测试 `:45/:70`）；
  两个接口各加一个抽象方法的实现者全库只有这两个生产实现（无测试替身会被打破）；单轮 128 回收上限而可见性不设限。
- **单测（JVM，写就未由本轨跑）**：`BatchImportStagingOrphansTest.kt` 5 例——未认领会话→可见为真且宽限内不可回收
  `:29-47`；被任务认领→转假 `:40`；回收（删除）后→转假 `:43-46`；宽限边界含 cutoff 本身 `:50-66`；时钟在宽限内/epoch
  时间戳无可回收 `:69-75`；非会话条目与缺失根目录不参与 `:78-89`；单轮 128 上限而可见性不设限 `:92-104`。
- **仪器化（写就未由本轨跑）**：`BatchImportInterruptedStagingInstrumentedTest.kt:33-63`（真 staging + 真 FileProvider uri
  走 ①未认领为真 ②超宽限回收后为假 ③在册任务认领（processing 停掉的 scope、页面文件仍在磁盘上）为假）；
  `BatchImportInterruptedNoticeInstrumentedTest.kt:26-72`（Compose 真/假两态）。后段设备实跑：staging 类 **1/0**；
  feature:library 全量 49/0 含提示类 2 例（§3.3、§3.7；材料两侧用例计数口径未说明）。
- **未达/边界（轨内声明）**：未编译、未类型检查（环境无独立 kotlinc，`where kotlinc` 无命中，只有 JDK21）；staging 目录与
  大厅附图摄取**共用**（`RoomLobbyMessageImageIntake.kt:33`、`:126-130` 走同一个 stage 且不删），一张相册附图被暂存后也会让
  读口为真直到对账回收——提示是「staging 里有没人认领的字节」，不等于「整卷导入被中断」；区分生产者需要 KD-42 里那条带
  schema 的完整修法；提示实时性——读口只在进屏与「最新任务 id 变化」时刷新，旧孤儿会话还在时提示会一直为真直到下次启动
  对账按 60 分钟宽限回收。
- **共享树协调（轨内声明）**：`BatchImportRepository.kt`（别线新增 BatchImportDraftState/savedEntryId 等）、
  `RoomBatchImportRepository.kt`（draft 状态映射）、`BatchImportRoute.kt`（`onOpenSavedEntry` 与页行改写）同时载有**另一条
  在飞线**的改动（另有其新文件 `BatchImportOutcomeInstrumentedTest.kt`、`MistakeDetailRoute.kt` 等）；本轨只做加法、已保留
  对方 hunks，但**提交时不能按「本线独有」处理这三个文件**。

### T5 3E 全库扫描第二档

- **core:domain**：新增 3E 专属常量文件 `KnowledgeReviewFullLibraryScan.kt`（`FULL_LIBRARY_SCAN_DAILY_CAP=10`，KDoc 注明
  出处=本轮裁定）、范围规则纯函数 `fullLibraryScanCandidate`（无绑定题/无材料 ⇒ 不在范围），并把第二档并入
  `selectKnowledgeReviewQueue`（先按分取前 N，再与今日队列并集，同一时间预算双封顶）；重读 diff 时修正 `knowledgeRecallRiskByNode`
  形参名（现为 `nowEpochMillis`，`KnowledgeReviewQueue.kt:99`）两处调用；`selectKnowledgeReviewQueue` 新增两个带默认值参数，
  既有 8 处调用全用命名参数（域单测 + 服务唯一生产调用点）。
- **core:database**：新增单条 SQL 读口 `readFullLibraryScanNodes`（活跃节点 ∧ 在册绑定题(CH=ACTIVE) ∧ 科目一致非退役材料组，
  避免 N+1；`ProblemOrganizationDao.kt` 的 @Query），`RoomStudyDatabase`/`KnowledgePort`/假库接线。
- **core:data**：`currentKnowledgeReviewPlan` 改为两档装配（第二档减今日队列，扫描节点只补解析入选者名字），并删掉
  `planQueue`/`requestedScope`/`queueScope`/`material` 四处早退（「不受今日队列限制」；原 541-542 范围锁死注释改写）。
- **spec**：`docs/specs/2026-09-06-dual-review-entry-design.md` §0/§1/§3.2/§5-P3（另两处一致性）同批改写。
- **关键取舍**：「每日 ≤10」按需求文档「每日增量由 F8 预算 + F17 配额封顶」与 spec 改写文本实现为**只封第二档增量**——
  今日队列不被 10 挤掉，并由时间预算另行封顶；若 owner 本意是「并集整体取前 10」，需把 `take(N)` 移到并集之后（一行改动，
  已在 `selectKnowledgeReviewQueue` 的 KDoc 写明当前口径）。
- **证据（轨内自跑，非 Gradle）**：`python check_full_library_scan_sql.py`（导出的 Room schema 65.json 建真表 + 夹具）→
  4 行正例 + 反面断言（无绑定题 / 唯一题已归档 / 已退役 都不在行集）+ EXPLAIN QUERY PLAN 全走既有索引；`python scale_check.py`
  → `scale rows: 5000 best-of-3 ms: 12.1`（未设性能门；S9/S17 真门在设备侧）；发布 SQL 与已验证 SQL 逐字一致（归一化比对 True）；
  改动文件括号平衡烟雾检查 10 个全 OK。
- **单测/仓库用例（写就未跑）**：`KnowledgeReviewQueueTest` 新增 7 例（`fullLibraryScanCandidate` 范围规则、
  `recoversWeakNodesThatAreNotInTodaysQueue`、`capsTheFullLibraryScanAtTheDailyCap`、`aZeroDailyCapDisablesTheSecondTierEntirely`、
  `timeBudgetDropsMergedTierEntriesAlike`、`todayQueueEntryWinsWhenTheSameNodeIsInBothTiers`、
  `fiveThousandFullLibraryScanCandidatesStayWithinTheDailyCapAndScaleGate`（best-of-3 <1500ms 宽门，未跑））；
  `currentKnowledgeReviewPlanCoversTodaysQueuePlusTheFullLibraryScan`（原断言逐条保留为子集断言 + KF-08 先验夹具自证该题不在
  今日队列 + 第二档节点 `knowledge:chain-rule` 被捞回 + 无材料点与无绑定题点不入队）与
  `currentKnowledgeReviewPlanScansTheLibraryEvenWhenTodaysQueueIsEmpty`（旧实现在此早退）。
- **零 bump 依据（轨内读源）**：`KnowledgeReviewSessionPlan` 全仓无持久化落点（只在 core/data 装配、app 消费），未进任何
  计划指纹（`ReviewPlannerV2.VERSION` / `PLAN_FINGERPRINT_SCHEMA_VERSION` 一行未动）；只加只读查询、无 schema 改动；依据已
  写进 `KnowledgeReviewFullLibraryScan.kt` KDoc（引 ledger §3.12 与 `KnowledgeNodeScorer` 同一口径）。
- **未验证**：Kotlin 编译与两模块 JVM 单测未跑（按纪律）；Room/KSP 对新 @Query 的编译期校验未跑（已加 `COALESCE(...,'')`
  让非空 String 契约成立）；5000 级规模证据只在本地 python；core:data androidTest 编译未跑（新端口方法实现者已 grep 核实
  为 `RoomStudyDatabase` + Fake 两处）；设备侧 S9/S17（夹具 + 量化门 + 退化-红/还原-绿）未做；「每日 ≤10」口径解释待 owner；
  扫描档候选先修图对全部扫描行求（`graphFor` 内 256/400 分块，非逐节点），极端规模下调用量成本未量化。

### T6 AUD-09 净化作业持久化（schema 66）

- **落地**：新增 schema 66 表 `clean_redraw_task`（身份 = `revisionId+sourceAssetId+sourceImageSha256+requestFingerprint`；
  status=PENDING/SUCCEEDED/FAILED/SUPERSEDED；attemptCount/lastFailureCode/时间戳；纯非破坏迁移 65→66 + 迁移矩阵用例）；
  classify 输入带源图字节 sha（`ModelTaskRequest` schema 17，含旧行指纹 strip 兼容：`ModelTasks.kt:867-871`、`:973-983`
  一带两处 strip，`:815` 无条件 `withoutEmptyClassifySourceSha` 避免旧行 verifyIntegrity 判脏）；fire-and-forget 重绘升级为
  可续跑作业 `CleanRedrawTaskRunner`（依赖缝可 JVM 测）：同指纹已成功跳过不重复付费、失败落原因码、启动对账按租约 + 尝试
  上限续跑、付费前核「修订仍是其条目当前题面」否则标 SUPERSEDED 不挂、返回值改为日志留痕。
- **触发范围**：仅 `created==true` 触发（`RoomCaptureWorkflowRepository.kt:509-522`、`:715` 附近两处调用点未改）；
  TEXT_ONLY 不动作（runner 的 `runAttempt` 分支 + 单测 `a text only problem commits the task without redrawing`）；既有三条
  capture 重绘仪器化用例逐条对照控制流（内联 scope=null 与注入 scope 两态）未发现语义破坏。
- **证据（轨内自跑，非 Gradle）**：括号平衡 + 不可见字符扫描覆盖 22 个改动/新增文件全清；只读解包 room3 3.0.0 编译器/插件/
  运行时 jar（javap）确认——导出 JSON 的 identityHash 来自 `SchemaIdentityKey`（`DatabaseBundle` 用 `getIdentityHash`）、
  运行时 `BaseRoomConnectionManager.checkIdentity` 会比对 identity_hash 并在不符时抛 `IllegalStateException`、
  `RoomSchemaCopyTask` 把 KSP 写出的 schema 拷回 `$projectDir/schemas`——因此 **66.json 是构建产物，本轨不手写**；
  `KernelWave0SchemaContractTest` 的既有约定（「版本常量 == 导出的最高 schema JSON」，字面量钉 65）已同步为 66。
- **后段实跑**：单测门 core:database **192/0**（本轮新跑，目录 00:15:30）；`CleanRedrawTaskRunnerTest` **17/0**（上轮 T6 新红
  已真修，不是遮绿：生产文件改动 mtime 00:11:50/00:12:29 + 新增回归例 `claiming a failed task clears the stale failure code
  before the new attempt`）；设备 `FullMigrationMatrixInstrumentedTest` **13/0**（1h36m21s，§3.6）。
- **设备红（材料原文，未归因）**：`CleanRedrawTaskInstrumentedTest` **2 tests / 1 failure**——
  `interruptedPendingCleanRedrawTaskIsRetriedOnResumeWithoutPayingTwice` 在 `CleanRedrawTaskInstrumentedTest.kt:68`
  `checkNotNull(database.readModelTask("save-decision:${committed.problemRevisionId}"))` 抛
  `IllegalStateException: Required value was null.`（同类第 2 例通过）；`adb reboot` 后重跑仍在同一行（首跑 + 重跑 + 重启后
  重跑 3/3），**判为确定性红，非模拟器劣化**。
- **未验证（轨内）**：Gradle 单测/androidTest 编译/整包全未跑；66.json 在工作树尚不存在（提交时需纳入本线文件清单）；
  两条新增仪器化未由本轨在设备运行（其中一条即上述红，后段已跑）；两处口径判断需人工复核——①「为该草稿最新」实现为
  「修订仍是其 `error_book_entry.current_revision_id`（书里现在显示的那一版）」，今天的写路径不会移动该列（用例用裸 SQL
  合成该状态验证）；②作业指纹取分类请求的「逻辑操作指纹」（不含时刻/同意位）而非 request 级指纹（带时刻会让每次触发都
  变成新身份）；残留并发窗口——领取用 attempt_count CAS，能拦「旧尝试的迟到终态覆盖新尝试」，但不排除「同一行被两个执行者
  各领一次」；可达路径被两道前提挡住（同一草稿只能提交一次、启动对账有租约），当前构造下不可达，未为此加内存锁；
  升级边界——升级前在途、停在非终态的分类任务行请求快照不含 sha，升级后按同一 requestId 重建会撞模型台账的不可变请求
  校验 → 该轮落 CLASSIFY_FAILED 并由尝试上限收敛（不重复付费，但那张图这次不会变干净），未给模型台账写「旧行补键」迁移；
  迁移矩阵新用例（v65→66）与 `CleanRedrawTaskSchemaContractTest` 的 DDL 逐字比对依赖构建期 Room 生成物完全一致（未在本地
  验证一致性，差一字符会在门禁红——这正是设计意图）。

### T7 6C-1 备份清单 v2 + settings.json

- **清单 formatVersion 1→2**：新增 `appVersionName`/`appVersionCode`/`contentVersion`/`hasSettings`/`settingsDigest`；v1 缺省
  读回 null/false、行为不变；写永远 v2；>2 用既有文案拒（`SmbkArchiveCodec.kt:809-815` 版本门：>2「备份格式版本过新，暂不
  支持恢复」/ <1 过旧；`:32-93` SmbkManifest v2 + SmbkArchiveMetadata；`:208-263` create 写 v2，设置记录进 files、
  `fileCount`/`totalBytes` 含设置）。
- **settings.json**：自带 schemaVersion=1；字段 = desired_retention/exam_calendar/optimized_fsrs_parameters/提醒
  enabled+minutes_after_midnight；**结构上不含**投递历史/API Key/授权租约/sleep journal（`SmbkSettingsDocument.kt` +
  `SmbkSettingsCodec.rejectionReasonFor`）。
- **恢复路径**：设置版本与值域在 Phase 1 unpack 的编解码闸门内「交换前」可读拒绝（`settingsRejection` `:882-922`）；
  交换提交且 journal 清掉之后才写 DataStore（`AndroidBackupRepository.kt:540-546` 在 try 之外）；写失败不回滚已一致的 DB，
  回执用 `RestoreSettingsOutcome.WRITE_FAILED` 标 partial（`BackupRepository.kt:53-83`、manifest 版本戳透出 `:116-127`；
  app 文案 `SecondaryScreens.kt:729-735`「设置未能写入，本机设置保持原样。」）；提醒只写两个偏好键
  （`AndroidBackupRepository.kt:583-603`），系统通知授权与投递历史都不碰。
- **关键取舍**：`contentVersion` 取「随包那个内容包」在 `content_install_state` 的进度行（packId 由
  `BundledKnowledgePackResources.updateManifest` 提供，不另抄 id），且取自快照库、非实时库（`AndroidBackupRepository.kt:179-222`）；
  设置口未装配（仅测试）时写 `hasSettings=false` 而不写空载荷；既有 13 例 codec 单测中 1 例的夹具由「注入 formatVersion」改为
  「替换 formatVersion」（清单现在显式写版本，重复键会让夹具语义不确定），断言仍为 Invalid 并额外断言夹具确实改变了。
- **证据（轨内自跑，非 Gradle）**：`/tmp/t7check/compile_and_run.sh` 用 Gradle 缓存里的 Kotlin 2.3.21 编译器 +
  kotlin-serialization 1.9.0/coroutines 1.11.0（与 `gradle/libs.versions.toml:3/10/11` 一致）编译 core:model + core:domain 源码 +
  codec/settings/test → `COMPILE_STATUS=0`、`OK (20 tests)`（13 既有 + 7 新：v2 round-trip、v1 兼容读、>2 拒、settingsDigest
  校验、设置 schema 版本门、声明有设置却缺载荷、排除项字段名钉子）；`/tmp/t7check/compile_android_repo.py`（同编译器 +
  android.jar API 37 + AGP transforms 缓存 290 个 AAR classes.jar）——**唯一诊断在别线在飞文件
  `BatchImportSourceStaging.kt:211`**，本轨文件零诊断；@Test 计数 20、JUnit 20/20 OK；逐条对任务书 5 项核过。
- **app 接线**：`SmartMistakeBookApplication.kt:401-408` 工厂传 `schedulingSettingsStore` + `reviewReminderRepository`
  （两者 `:245/:294` 先于此处初始化）。
- **未验证**：Gradle 门禁全未跑（core:data 单测、core:data androidTest 编译与仪器化、app 整包）；设置写 DataStore 失败 →
  回执 partial 且数据库不回滚只在代码与用例层面成立（`FailingReviewReminderRepository` 注入写失败用例未运行）；恢复 Phase 8
  时序（journal.clear() 之后、try 之外写设置；取消不触发回滚）经人工复核、未经进程死亡/取消注入验证；内容勾选（行级子集）
  不做，登记为 **6C-2**；contentVersion 戳若随包包 id 变更或多包形态变化会退化为 null（不伪造值），未做专门测试。后段设备
  实跑 `BackupRestoreInstrumentedTest` **25/0**（§3.3）。

### T8 判词锚定 + 归属落账（AUD-11 裁定 ①）

- **锚定门**：模型判词分支只认锚在 `MistakeRecord.knowledgeNodeIds` 上的 accepted 证据（`TutorJudgedReviewSettler.kt:328`；
  门在 `:113`、`:317-330`——accepted 过滤 `rejected_reason==null && weight>0` 后，再按 `knowledge_node_id ∈ 本题 KC 集` 过滤，
  取最新一条）；全外部节点 → NO_VERDICT（`:125`/`:144`，保持阻塞）；空 KC 集 → 该会话不构成判词（KD-36 的多会话回看循环与
  新鲜度门槛未动：`:100-124`）。
- **归属落账**：锚定成立时把驱动证据落成一条归属（驱动节点 + 该题该 revision 的现役绑定 + 驱动证据生效权重，`:360-374`；
  绑定 = `readCurrentPracticeUnitKnowledgeBindings` 里同节点且 `basisRevisionId==mistake.problemRevisionId` 的现役行，
  bindingId 字典序最小者；PRIMARY/DIRECT；`taxonomyVersion` 随归属绑定 `:197`；`attributions = listOfNotNull(attribution)`
  `:203`；`Verdict` 携带 `drivingEvidence` `:391`；解析不出绑定则与「无判词」同一条 NO_VERDICT 出口 `:136-144`）；本地核对/
  未落账分支维持空归属（既有仪器化用例「不动知识点掌握度」不回退）。
- **额外一处必要改动**：归属进入快照载荷后「稳定 id＝稳定内容」不成立，快照 id 改为**内容寻址**（`:174-190`），否则一次
  「判词内容变了」的重试会撞 KD-37 同类的确定性身份死路；本地核对分支的 id 逐字节不变。
- **核实（轨内读码）**：attributions 确实同时进入持久化规范指纹（`LearningLedgerFingerprint.kt:130-155` 调
  `assessmentSnapshot`；`:147-172` 逐字段把 attributions 写入 SHA-256 规范载荷——schema 形状未变，故 fingerprint schema 串
  不动）与投影（`LearningProjector.kt:933-944` DIRECT 归属 → `projectMastery`；`:964-974` 重放按当前绑定重派生；
  `:1306-1322` §2.13 归属幅度不缩放 = 整条 attempt 证据，故该 KC 会收到 0.5 并与 chat 通道 ≤0.15 并存）；但投影器与两条
  归属派生路径一行未动、新内容只出现在新快照上 → 固定账本投影逐位不变，按台账 §3.12/§3.14 先例判**零版本 bump**（依据见下，
  供 T13 折入台账；反向风险：若 bump PROJECTOR/ATTRIBUTION（复合串 learning-core-v13）会强制全量重放，把存量讲题判定
  attempt 按当前绑定重挂 KC——属裁定未要求的追溯性掌握度变化）。
- **实据（归属必须真实绑定行）**：`AttemptTransactionDao.kt:301-304`（save 合同校验）、`:665-676`（每条归属做
  binding/practiceUnit/node/basisRevision/taxonomy 五元组核对，悬挂引用抛 `ImmutablePayloadConflictException`）、
  `:648-662`（KD-37 时间戳容忍比较仍在）；`DatabaseContract.kt:475-487`（归属非空即可放行 + 归属 basisRevision/taxonomy 必须
  与快照逐字一致）；同形状先例 `StudyPracticeUnitFacts.kt:98-127`、`:178-221`。
- **用例（写就未跑，材料所列 9 例）**：`aPrerequisiteKnowledgeVerdictNoLongerFlipsTheWholeQuestion`（报告 §1.2 反例本体）、
  `evidenceOnAnUnrelatedKnowledgeNodeIsNotAVerdict`、`onlyExternalNodeEvidenceLeavesTheItemBlocked`、
  `aQuestionWithoutKnowledgeNodesCanNeverAnchorAVerdict`、`anAnchoredVerdictWithoutAMatchingBindingStaysBlocked`、
  `theSettledSnapshotRecordsTheDrivingEvidenceWithItsBinding`（归属五元组/权重/角色/确定性 + 快照 taxonomy 随绑定）、
  `aNewerSessionWithOnlyExternalEvidenceDoesNotHideTheAnchoredOne`（KD-36 回看 × 锚定门）、
  `replayingTheSameModelVerdictSettlementKeepsOneSnapshotId`、`aChangedVerdictContentYieldsANewSnapshotId`；既有用例
  （本地核对决策链、rejected 不作证据、新鲜度门槛、KD-36 多会话、重放幂等）断言全部保留，仅夹具补 KC 集与绑定行。
- **轨内自跑检查**：python 括号配平自检 4 文件 `balanced`；`git diff --stat`（Settler +145、测试 +256、LearningState/
  TutorJudgedReview 仅 KDoc 行）；行尾一致（Settler/LearningState/TutorJudgedReview 全 CRLF、Test 全 LF，与各自原状一致）。
- **未验证**：零 Gradle/零设备（材料未列其运行；轨内建议最小集 `:core:data:testDebugUnitTest --tests "*TutorJudgedReviewSettlerTest*"`、
  `:feature:tutor:testDebugUnitTest --tests "*TutorChoiceSubmissionFailureTest*"`（本改动未触碰 feature/tutor，预期仅作回归
  哨兵）、`:core:data:connectedDebugAndroidTest` 的 `TutorJudgedReviewSettleInstrumentedTest`（本地核对路径 id/语义逐字节
  未变，预期 2/2））；**「驱动证据 id 落账」子项未达**——`KnowledgeEvidenceAttribution` 无证据 id 字段
  （`LearningState.kt:189-207`），唯一自由槽 bindingId 必须是真实绑定的 id，塞证据 id 必抛冲突；要落下需给归属加列
  （schema+迁移+指纹同批），当前按「节点 + 绑定 + 会话/时刻」回溯（已写进 Settler 类 KDoc:50-54）；真库运行期五元组核对
  只在真 Room 路径生效，JVM 假库（`RoomBackedStudyExperienceRepositoryTest.kt:2921-2924`）不镜像该检查，本轨未新增仪器化
  用例；投影后果（KC 收 0.5、与 chat 通道 ≤0.15 并存、档案错因计数变化 `MasteryOverviewDao.kt:112-124` 的 per-node 负向聚合
  会开始出现模型判错的该条 attempt——「档案仍为空」的现状会改变）为读码所得、无用例实证，与
  `docs/research/model-judged-verdict-pricing.md` §4(ii)「模型判对的 KC 写入上限 0.15」存在张力，属裁定的知情后果，
  请复核点收；并发窗口未验证（读现役绑定 → 写快照之间若改绑/删绑定，会以 `ImmutablePayloadConflictException` 可见失败，
  不损坏数据）；zero bump 为实施者按台账判别准则的结论，未登记进 `algorithm-version-ledger.md`（按计划 §T13 折入）。
- **文档待办（轨内声明，交 T13）**：`docs/REFACTOR-MASTER-LINE.md:56` 的「讲题判定通道不写知识点归属，
  `learner_knowledge_mastery_state=0` 行」在本改动后失效；`docs/known-defects.md:1672-1685`（KD-37 症状段）引用旧快照 id
  派生 `stableId("tutor-judged-snapshot", settlement.requestId)`，现已按归属内容寻址（历史记录未回改）。

### T9 AUD-11 两件（冷却限通道 + 可观察性/审计口）

（材料原文记「AUD-11 两件全部落地（T9+T10 两节）」；设备段把两条用例分别标为 T9/T10，见 §3.3 注。）

- **① 冷却限 `MODEL_CHAT`**：DAO 冷却查询加 `source_kind` 谓词（`ChatEvidenceDao.kt:169-190`；通道由
  `StudyDbValue.ChatEvidenceSourceKind` 单源，写入侧 `KnowledgeEvidenceChannel` 同源引用，`:159-180` 与
  `KnowledgeEvidenceWriter.kt:205-213`）；写入口只对模型通道取冷却年龄（`KnowledgeEvidenceWriter.kt:73-82`——
  `lastSameKcWrite = if (request.channel == MODEL_CHAT) database.lastAcceptedChatEvidenceAtForKc(..., sourceKind = request.channel.sourceKind) else null`），
  小测通道按自身幂等身份防重（会话/滚动窗配额仍通道无关）；`MasteryWriteGate.kt:42-49`、`:294-299` 口径 KDoc（常量 12h 未动、
  门逻辑 `:361-363` 一字未改）；小测屏 `SAME_KC_IN_COOLDOWN` 文案改为「这个知识点上刚有一笔更新的掌握记录，这次先只作为观察」
  （`KnowledgeReviewSessionScreen.kt:323-341`，注明旧句成因与唯一剩余到达路径 = SavedStateHandle 里的旧拒因，ViewModel
  `:285-287` 序列化保存）；讲题侧 `TutorToolTrace.kt:371-374` 复核后不改并落注释说明为什么仍然准确（冷却只约束模型通道后
  该句必然指向本通道更早的 accepted 记录）；`ChatEvidenceGateCalibration` 观测/报告加 source_kind 维（拒因占比按通道摊开、
  各通道 accepted 峰值；`ChatEvidenceGateCalibration.kt:38-64`、`:86-104`、`:127-140`、`:153-164`；DAO 两条 GROUP BY
  `ChatEvidenceDao.kt:206-243`、行类型 `:300-312`；`StudySchedulingCalibration.kt:70-89`），建议文案在有通道维时追加
  「（来路：…）」、无通道维逐字不变。
- **② 可观察性**：`insertAsLedgerEvents` 返回 `ChatEvidenceWriteReceipt`（新落行/正常重放/身份粘性三档，
  `ChatEvidenceDao.kt:81-141` 含 `readByEvidenceIds` 回读既有行区分「既有行是被拒观察行」、类型 `:270-296`），写入口折成
  `KnowledgeEvidenceLedgerStatus` 并对 `BLOCKED_BY_REJECTED_ROW` 落 WARN 日志（`KnowledgeEvidenceWriter.kt:150-174`、
  `:276-294`，默认 LEDGERED 保持既有构造点可编译；端口/实现透传 `ModelTaskDatabasePort.kt:154-196`、
  `RoomStudyDatabase.kt:1503-1521`）——「accepted 被 IGNORE」不再静默；新增 `KnowledgeQuizEvidenceAudit` 审计读口（按
  evidence_id+conversation_id+节点推导题面行 id，返回题面+键版本 item_revision/answer_spec schema，坏行只报键版本不猜题面；
  `KnowledgeQuizEvidenceAudit.kt:41-56`、`:82-97`；`KnowledgeQuizItemStore.kt:41-64`、`:198-214`、`:149-185`）。
- **③ KD-43 已登记** `docs/known-defects.md:1903-1932`（open，含报告 §3 的实测六项、Fix direction、Reopen condition；
  `grep '^## KD-43'` 命中 1 次，无重复）。
- **关键取舍**：③ 的「证据载荷带题面/键版本引用」按「最小改动面」实现为**零 schema 改动、零载荷字段**——题面/键版本引用由
  既有身份分量（会话在 conversation_id、节点在列、requestId 在 evidence_id）推导，不新增 `learner_chat_evidence` 列（会与
  同批 T6 的 schema 65→66 直接撞号），也不动 `ChatEvidenceSubmitted`/指纹；冷却实现取「DAO 过滤 + 写入口跳过」最小形态，
  未给 GateInput 加通道维（门逻辑一字未改，只在 KDoc 记录该维来路）。零 bump 依据写明于 `algorithm-version-ledger.md:278-296`
  （§3.21：全部版本串未 bump 的三条理由 + 诚实登记「放行集合变化」不是 bump 条件 + 测试证据列标注本轨未跑、待门禁）+
  `KnowledgeEvidenceWriter.kt:44-48` 类 KDoc；「投影层不读 source_kind」的源码事实 `LearningProjector.kt:1546-1547`。
- **证据（轨内自跑，非 Gradle）**：python 静态度量——21 个文件分隔符平衡对照 HEAD 基线**全部 delta=0**（整体非零的 3 个文件
  在 HEAD 上同样非零，属脚本对 KDoc/CRLF 的噪声）；符号/调用点扫描——`lastAcceptedAtForKc`/`lastAcceptedChatEvidenceAtForKc`
  的源码级调用点全部 3 参、`recordChatEvidence` 仅 2 处实现；并发线完整性——`overwriteAssessmentItemSnapshot` 仍命中
  （T3 的 KD-41 改动未被本轨编辑覆盖）；试图独立 kotlinc 编译（cached kotlin-compiler-embeddable 2.3.21 + JDK21）被安全 hook
  拒绝（「Bash 直接写源码/安全配置会绕过 Write/Edit 安全扫描」），未重试、未绕过——本轨**无编译级证据**。
- **新增用例（写就未跑）**：`KnowledgeQuizCooldownChannelScopeInstrumentedTest` 7 例（模型→小测放行且两笔落库两笔进账本、
  反向亦然、模型自冷却仍拒且落观察行、严格 < 边界 12h−1ms 拒 / 正好 12h 放行、身份粘性=BLOCKED 且账本零事件且既有拒绝行
  不被改写、accepted 重放=REPLAYED 且账本仍一笔、校准两查询真 SQL 分桶带通道维）；`KnowledgeQuizEvidenceAuditInstrumentedTest`
  3 例（生产写路径下证据行对回题面+键版本+correctChoiceId、坏行只报键版本不猜题面、MODEL_CHAT 行不进本口）；
  `KnowledgeEvidenceLedgerStatusTest` 4 例；`KnowledgeQuizEvidenceAuditTest` 6 例；`ChatEvidenceGateCalibrationTest` +3 例
  （轨内自检出并修正一处字典序期望）。后段设备实跑 cooldown **7/0**、audit **3/0**（§3.3）。
- **未验证**：轨内零 Gradle（新增/改动的 JVM 与 instrumented 用例、core:data/core:domain/core:database 既有用例
  （含 AUD-01/02）是否全绿、能否编译均未验证）；「载荷带引用」解释面待复核（若裁定原意要「载荷里真带一个引用列」则需
  schema 65→66，与 T6 撞号）；审计口当前无生产调用方（internal 读口 + 仪器化 drill 钉住语义，接进 UI/诊断面留 5B）；
  `TutorToolTrace` 讲题侧「无需改动」为读码推断；共享文件（RoomStudyDatabase.kt、StudyDbValue.kt、KnowledgeQuizItemStore.kt、
  RoomBackedStudyExperienceRepositoryTest.kt、KnowledgeReviewSessionScreen.kt、algorithm-version-ledger.md、known-defects.md）
  都含他线 hunk（T3 KD-41 覆盖重写、会话恢复失败屏等），整份文件最终一致性需门禁/复核；洪泛面（冷却收窄是否打开灌量面）
  只有校准读数设计与论证（小测防重仍靠身份 + 两路配额兜底），无实测数据。
- **给 T13**：KD-43 已由本轨登记完成，勿重复追加；本轨改动落在共享文件，提交时按文件整份纳入（勿按 hunk 挑拣）。

### T10 KD-38 残余（代理对截断）+ 台账 §3.20

- **① 修法**：`core/model/ModelTasks.kt` 新增 internal `String.takeBudgetChars(maxChars)`（按字符/UTF-16 码元预算截断，边界
  落在成对代理中间时退一个码元、结果永不越界；`:1315`，正文 `if (maxChars <= 0) return ""` / `if (length <= maxChars) return this` /
  `val boundarySplitsPair = this[maxChars - 1].isHighSurrogate() && this[maxChars].isLowSurrogate()` /
  `return substring(0, if (boundarySplitsPair) maxChars - 1 else maxChars)`），替换 `ModelGatewayEvent.Progress.of` 的原
  `take(MAX_PROGRESS_MESSAGE_CHARS)`（`:686`）——这是该文件里唯一与 `requireSafeModelText` 同预算（2000）的 take；新增
  `core/model` 用例 11 例（emoji 跨边界退格、整对在内用满预算、0/负预算、预算 1、已存在孤立代理原样、0..len 全预算扫描，
  以及「旧 take 确实切出孤立代理」的非恒真钉子）。截断取「截到边界前」（少留一个码元）而非「成对保留」（越界 1），
  因为预算是硬上界（Progress 的 require 与快照 ≤500 都按它判）。
- **② 台账补 §3.20**（`algorithm-version-ledger.md:261..277`）：登记 `tutor-plan-v19-move-label-budget` /
  `tutor-respond-v26-move-label-budget`（出处 KD-38 提交 `038d50c0`；版本串 `ModelEgress.kt:55`、`:67`、`:83`），并记入
  「同批剩余项修复不 bump」的判定；§3.18（`:236`）与 §2 等历史行逐字未动（`+17/−0`）。
- **证据（轨内自跑，非 Gradle）**：`check_take_budget_chars.py` → 22 条断言全绿（含旧 take 在 max−1 处确实留下高代理的反证）、
  `ALL CHECKS PASSED`、EXIT=0；`check_ledger_section.py` → 台账形状 3 行 × 6 列、关键事实（v19/v26、上一版 v18/v25、lobby
  不动、`038d50c0`、KD-38、`ModelEgress.kt:55/:67/:83`）齐全、§3.18 历史行原样、§2 号段未被混入，`ALL LEDGER CHECKS PASSED`；
  `git diff --numstat` = 17 0、无删除行；影响面核对——`Progress.of` 三个生产调用点（`OpenAiCompatibleModelGateway.kt:502/:513/:806`）
  全部先过 `streamingPrefix`（`:525-526`，`MODEL_TASK_STATUS_MESSAGE_MAX_CHARS`=500，`ModelTasks.kt:78`），故 2000 预算的 take
  现状下是防御面；既有断言（`OpenAiCompatibleModelGatewayTest.kt:1829/1868` 用 `<= 500`、`ModelTasksTest.kt:338-365` 全 BMP
  文本）既不放松也不回退；`python tools/ci/check_file_size_gate.py --base HEAD` → `no changed .kt/.java files vs base; nothing
  to gate`（ModelTasks.kt 1257→1280 行、base 已超 1000 硬线 → backlog 告警；新测试 172 行 < 900；仓库无 ktlint/detekt/spotless）。
- **未验证**：`core:model` 单测（含新 11 例）未跑——材料明示 core/model 无新产物「其绿不是本轮证据」；**活截断点未修**
  （`core/data/.../OpenAiModelResponseParsers.kt:353/357/364/408/633/646` 的 take(96/32/64) 与网关 `streamingPrefix` 同为按码元
  take、能真的切出孤立代理；因生产调用点都先经 streamingPrefix（≤500 字符），`Progress.of` 自身的 take 目前不可达——本轨改
  的是该 API 边界的防御面，**不改变线上可见行为**；若任务书本意是修这两个活点，需另开 core/data 轨，本轨按范围纪律未触碰
  core/data 任何文件）；`docs/known-defects.md` KD-38「未达/剩余」中「截断按 UTF-16 码元可能切断代理对」表述未同步（该文件
  本批由 AUD-11 轨登记 KD-43，按共享树纪律本轨未触碰）；台账 v19/v26 行「测试证据」栏引用的是 KD-38 批既有用例，本轨未跑；
  zero bump 未跑任何指纹/冻结测试实证；`ModelTasks.kt` 同时被他轨（AUD-09）写入（同文件、不同区段，~+60 行；本轨两处在
  `:686` 与 `:1315`），提交/复核按区段核。

---

## 3. 设备段（材料原文；执行者为设备轨，本记录者未接设备）

材料声明：设备为 **emulator-5554**（`adb devices -l` 确认；另有 emulator-5556 未使用），所有 adb/gradle 命令显式钉住
emulator-5554；下述全部为设备轨实跑。

### 3.1 前置与装包

- 装包：`cmd /c gradlew.bat :app:installLocalFirstDebug` → BUILD SUCCESSFUL，「Installing APK 'app-localFirst-debug.apk' on
  'test_device(AVD) - 14'」「Installed on 1 device.」。**数据保留已核**：`run-as` 仍见
  `files/datastore/model_configuration.preferences_pb.preferences_pb`（275B）、`files/batch_import_staging/`、`files/source-assets/`。
- 本机环境注（材料原文）：PATH 上的 `cmd` 是 npm shim（`/c/Users/听云/AppData/Roaming/npm/cmd`），`cmd /c` 会报 too many
  arguments；实际用的是 `/c/Windows/System32/cmd.exe`，且需把 MSYS 风格 `JAVA_HOME` 换成 Windows 形式，否则 gradlew 报
  JAVA_HOME invalid（exit 3）。

### 3.2 配额探测（429 阻断）

设备上确有 opencode zen 配置：模型配置 DataStore 读出 `is_configured` / provider=opencode zen / base_url=
`https://opencode.ai/zen/go/v1` / model_id=deepseek-v4.1-flash / protocol=openai-chat-completions-v1（无密钥字段，密钥在
Keystore）。在 app 内「我的 → 大模型设置」点 capability_test（测试图片与讲解能力），UI 回报「服务商暂时没有完成测试，请稍后
重试。」；日志 `W/ModelCapabilityTester: structured probe status=429 bodyBytes=168`。**429 阻断；未重试、未换凭据。**

### 3.3 core:data 仪器化（逐类过滤；同一构建；全部 BUILD SUCCESSFUL）

| 轨标注（设备段） | 类 | 结果 |
|---|---|---|
| T1/KD-39 | `SplitImportReceiptReconciliationInstrumentedTest` | 5 tests / 0 failures |
| T4/KD-42 | `BatchImportInterruptedStagingInstrumentedTest` | 1/0 |
| T9 | `KnowledgeQuizCooldownChannelScopeInstrumentedTest` | 7/0 |
| T10（设备段标注） | `KnowledgeQuizEvidenceAuditInstrumentedTest` | 3/0 |
| T7 | `BackupRestoreInstrumentedTest` | 25/0 |

注：设备段把审计类标为 T10、把冷却类标为 T9；逐轨材料中**两者均列在 T9 的文件清单**（材料内标注不一致，如实照录）。

### 3.4 T6/AUD-09 确定性红（材料原文）

`CleanRedrawTaskInstrumentedTest`：**2 tests / 1 failure**。`interruptedPendingCleanRedrawTaskIsRetriedOnResumeWithoutPayingTwice`
在 `CleanRedrawTaskInstrumentedTest.kt:68` `checkNotNull(database.readModelTask("save-decision:${committed.problemRevisionId}"))`
抛 `IllegalStateException: Required value was null.`（同类的第 2 例 `aLateCleanRedrawResultIsSuperseded…` 通过）。按纪律
`adb reboot` 后重跑仍在同一行（首跑 + 重跑 + 重启后重跑 3/3），**判定为确定性红，非模拟器劣化**。材料未记录该红的归因与处置
（它既不在单测门既有红名单（§4.1），也不在单测门 `newFailures` 里）。

### 3.5 T3 JVM 补跑

T3/KD-41 本批没有仪器化类（新类落在 JVM：`core/data/src/test/.../KnowledgeQuizItemStoreTest.kt`，用例注释即 KD-41）。
设备轨改跑 `:core:data:testDebugUnitTest --tests *KnowledgeQuizItemStoreTest*` → **tests=7 failures=0 errors=0**。

### 3.6 T6 迁移矩阵

`:core:database:connectedDebugAndroidTest` 只跑 `FullMigrationMatrixInstrumentedTest` → **13 tests / 0 failed**，
BUILD SUCCESSFUL，耗时 **1h36m21s**。

### 3.7 feature:library 全量（材料在此截断）

`:feature:library:connectedDebugAndroidTest` 全量 → **49 tests / 0 failed**（BUILD SUCCESSFUL 17m7s），含本批新类
`BatchImportInterruptedNoticeInstrumentedTest` 2、`BatchImportOutcomeInstrumentedTest` 2（后者属他线在飞），及既有
`MistakeNoteAndArchiveInstrumentedTe…`——**材料在此处截断，其后内容（含剩余设备结论）未记录。**

---

## 4. 门禁与跑测口径

**口径**：本批门禁分两段——① 单测门（unitGate，共 **4 轮**，本轮 **exit=1**，因既有红点名）；② 强门禁（两段编译/整包命令，
**exit=0**）。两段均**非**本记录者执行。

### 4.1 单测门（unitGate；本轮 exit=1）

- **执笔结论**：自算 core:data 单测产物（`core/data/build/test-results/testDebugUnitTest` 全部 TEST-*.xml，目录 mtime
  2026-10-17 00:17:19）合计 **tests=834 failures=3**，与输出尾「834 tests completed, 3 failed」逐数对上；判
  `onlyKnownReds=true`、`newFailures=[]`。完整命令行材料未记录（执笔方式为「自算 XML + 读输出尾」，未亲跑 Gradle）。
- **既有红点名（3 条，全部同一根因，照录）**：
  1. `core:data DenseFusionTest > frozen alpha is half`（`DenseFusionTest.kt:21`，expected:<0.5> but was:<0.7>）；
  2. `core:data DenseFusionTest > every fusion case matches the reference order and normalisation`（`DenseFusionTest.kt:38`，
     `two_legs_normal` 次序 expected:<[b, a, c]> but was:<[a, b, c]>）；
  3. `core:data DenseRecallRerankerTest > real asset scan and fusion reproduce the python device order`（`DenseRecallRerankerTest.kt:128`，
     case#0（BIOLOGY）次序与 Python 端侧算法不一致）。
- **判既有红的依据（执笔原文要点）**：唯一能造成这三条的是 `core/data/.../knowledge/dense/DenseFusion.kt`
  `const val ALPHA = 0.5` → `0.7` 的单行改动（`git diff` 仅此一行；文件 mtime 2026-10-16 23:59:43、本轮未再变），而该临时改动
  是 **KB 线自己写死**的——`docs/research/2026-10-16-d3-round1-prereg.md`（mtime 23:49）§4「出数前写死纪律 · α 临时改动与回滚
  （写死）」逐字指定该单行改动 + 记改动前后 sha + 不采纳即逐字回滚；计划 `plan-sess_b4ae5030-….md` §3 B3 明写采纳后「改
  DenseFusion.ALPHA + DenseFusionTest 钉重钉」；两条钉仍是旧态（`DenseFusionTest.kt` mtime 2026-09-24、
  `DenseRecallRerankerTest.kt` 2026-10-16 05:30），即重钉是 KB 线未手；KB 线此刻仍在跑（`build/agent-batch12/b/` 下
  `step1_compile_androidtest.log`、`step2_run_device.start`、`step4_run_jvm.cmd` 等均 2026-10-17 00:00 后被写过）；本批十条
  任务书（BRIEF_T1..T10）无一提到 `core/data/.../knowledge/dense/` 或融合权重 α。
- **口注（执笔照录供 owner 核）**：任务书点名写的是「core:data 的 GoldenRetrieval 系列（KB 线在飞，4 条）」，而字面
  `GoldenRetrievalJvmTest` 本轮是绿的（2/0；同检索家族绿的还有 `FourSubjectRetrievalBenchmarkTest` 1/0、
  `KnowledgeRetrievalBenchmarkTest` 6/0、`StagingPackRetrievalBenchmarkTest` 1/0、`DenseAssetProvenanceTest` 2/0、
  `DenseEncoderThreadsTest` 4/0、`DenseTokenizerParityTest` 7/0、`DenseVectorAssetTest` 5/0）；实测到的是**同根因**
  （KB 线 α=0.7 在飞）的上述 3 条，按**根因**而非套名归入既有红（若把这三条当新增红派回，会要求别人去动 KB 预注册里
  写死「不许在判官上调参」的常量，故照录不改绿）。
- **其他模块**：core:database 本轮新跑 **192 例 0 失败**（目录 00:15:30）；core/model（22:19:40）、core/domain（22:20:15）、
  core/export（23:19:07）、feature/review（22:20:06）、feature/library（22:20:06）**无新产物，其绿不是本轮证据**（执笔原文）。
- **上轮 T6 新红已真修（不是遮绿）**：生产文件改了（`CleanRedrawTaskDao.kt` mtime 00:11:50、`CleanRedrawTaskContract.kt`
  00:12:29）且测试新增回归例 `claiming a failed task clears the stale failure code before the new attempt`；
  `CleanRedrawTaskRunnerTest` 现 **tests=17 failures=0**（上轮失败那条 `a fresh attempt within the budget is allowed` 已绿）。
- **设备侧另有一条红不在本门**：T6 `CleanRedrawTaskInstrumentedTest` 1 例确定性红（§3.4）——单测门的 `newFailures=[]` 只覆盖
  JVM 产物，该红需另行点名处置。

### 4.2 强门禁（两段；exit=0）

- **命令原文（读自运行脚本 `:361-367`）**：
  - `gradlew.bat :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin :feature:library:compileDebugAndroidTestKotlin`
    → **exit=0**（stderr 尾部为空）；
  - `gradlew.bat :app:assembleLocalFirstDebug` → **exit=0**（stderr 尾部为空）。
- **落盘佐证（执笔只读核对）**：三个目标模块在本轮窗口内有 androidTest 构建活动——`find <module>/build -type f -newermt
  '2026-10-17 00:18'` 对 core/database 回 495、feature/library 回 56、core/data 回 4，且各模块 build 下存在 debugAndroidTest
  相关目录；整包产物 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` 与 output-metadata.json 均为
  **2026-10-17 00:22:05**、APK **207,756,702 字节**；本轮窗口内**没有任何新的测试报告目录**（`find . -type d -name reports
  -newermt '2026-10-17 00:18'` 回空）——即这两个门禁只做编译、不跑测试，与口径一致。
- **执笔方法声明**：未亲跑 Gradle；编译/整包的成功结论建立在脚本给出的 exit=0 上，其自身只做了产物存在性与时效的落盘核对；
  上一步单测门的 3 条既有红（§4.1）仍原样在盘，属上一步口径，不影响本次两个编译门禁的结论。

### 4.3 本批未跑（口径如实）

- 除 core:data（834 例重跑）与 core:database（192 例新跑）外，其余模块本轮**未重跑**（无新产物，其既有绿不属本轮证据——
  材料原文）；整包 release 未跑；`tools/tests` 全量 Python 未跑（T2 只跑单文件 16 例）；零散测试面与设备侧未跑项见 §5。

---

## 5. 未验证项总表

### A. 仍成立（至材料末仍未验证 / 未达）

1. **T6**：`CleanRedrawTaskInstrumentedTest` 1 例确定性红（`CleanRedrawTaskInstrumentedTest.kt:68`，3/3 复现）——归因与处置
   材料未记录。
2. **T8**：全部用例未跑（材料未列其运行）；「驱动证据 id 落账」未达（需给归属加列：schema+迁移+指纹同批）；真库五元组核对
   未被 JVM 假库镜像（无仪器化）；投影后果（KC 收 0.5、与 0.15 上限张力、档案错因计数变化）为读码所得；并发窗口未验证；
   zero bump 待 T13 折入台账。
3. **T8**：两处文档待办未改（`REFACTOR-MASTER-LINE.md:56`、`known-defects.md:1672-1685`，归 T13）。
4. **T5**：Kotlin 用例全部未跑（无材料证据）；「每日 ≤10」口径解释待 owner；候选先修图在极端规模下的调用量成本未量化；
   设备侧 S9/S17 未做。
5. **T10**：`core:model` 新 11 例未跑（材料：core/model 无新产物，其绿非本轮证据）；活截断点
   （`OpenAiModelResponseParsers.kt:353/357/364/408/633/646`、`OpenAiCompatibleModelGateway.kt:525-526`）未修；
   `known-defects.md` KD-38 表述未同步；台账「测试证据」栏所引既有用例本轨未跑；zero bump 无冻结测试实证。
6. **T9**：「载荷带题面/键版本引用」的解释面（推导式 vs 真列；若真列需 schema 65→66 与 T6 撞号）待复核；TutorToolTrace
   讲题侧「无需改动」为读码推断；审计口无生产调用方（接 UI/诊断面留 5B）；洪泛面无实测数据；共享文件整份一致性待门禁/复核。
7. **T2**：`docs/status.md` 未重生成（新文案首现于下次 CI 产出）；KD-40 未回填（归 T13）；端到端渲染内部工具套件计数未回读。
8. **T1**：复核页按钮仍基于界面快照选题（一次快照延迟非原子，未加 UI 防抖）；对账查询在历史题行上是全表索引扫描
   （有意不过滤作业状态），未做规模压测。
9. **T4**：staging 目录与大厅附图摄取共用（一张相册附图暂存也会让读口为真直到回收）；提示实时性（旧孤儿会话在时提示一直
   为真到下次启动对账回收）；staging 类含三条场景但设备段记 1 例（材料两侧计数口径未说明）。
10. **T3**：KSP 对 `@Update` 的处理未编译验证；真库「损坏行 → 覆盖 → 读回」路径未跑（未新增仪器化）；zero bump 未做 schema
    导出比对实测；docs 未改（KD-41 仍标 open，T13 关账）。
11. **T6**：两处口径判断（「为其草稿最新」= `current_revision_id`；作业指纹=逻辑操作指纹）待人工复核；残留并发窗口
    （同 CAS 两执行者各领一次的可能，当前构造不可达，未加锁）；升级边界（在途旧行 → CLASSIFY_FAILED 收敛，不补旧行键）；
    迁移矩阵 DDL 逐字比对依赖构建产物。
12. **T7**：Gradle 门禁未由本轨跑；设置写失败 partial 未设备复现（`FailingReviewReminderRepository` 用例未运行）；Phase 8
    时序未做进程死亡/取消注入；内容勾选=6C-2；contentVersion 退化边界（包 id 变更/多包 → null）未测。
13. **门禁**：除 core:data/core:database 外的模块本轮未重跑；整包 release、`tools/tests` 全量未跑；单测门完整命令行未记录。
14. **交叉疑点（T7 报告、材料未说明）**：`/tmp/t7check/compile_android_repo.py` 对**他轨在飞**的 `BatchImportSourceStaging.kt:211`
    报 `cannot infer type / no value passed for parameter 'transform'` 诊断，而 core:data 单测门（00:17）与两编译门
    （00:18–00:22）均过——差异原因材料未记录（可能为共享树中间态或后续修复），未验证。
15. **材料截断**：设备段 3b) 之后的设备内容未记录。
16. **材料层面**：「十二轨」的完整轨目未给出；材料另处提及的 T12（设备核）与 T13（门禁/折入）亦未附逐轨材料。

### B. 已被批次后段证据覆盖（轨内写作时点「未跑 / 未编译」，后在门禁/设备段实跑）

1. T1 新增 5 例 instrumented → 设备 **5/0**。
2. T4 staging 仪器化 → 设备 **1/0**；提示类 2 例 → feature:library 全量 **49/0**。
3. T3 `KnowledgeQuizItemStoreTest` → 设备 JVM **7/0/0**。
4. T6 `CleanRedrawTaskRunnerTest` → 单测门 **17/0**（含新回归例）；迁移矩阵 → 设备 **13/0**（T6 另有一条仪器化红，见 A.1）。
5. T7 `BackupRestoreInstrumentedTest` → 设备 **25/0**。
6. T9 两条仪器化 → 设备 **7/0**、**3/0**。
7. 编译面：core:data/core:database 的 androidTest 编译 + `:app:assembleLocalFirstDebug` → **exit=0**（材料层面；未逐轨声明
   覆盖了哪些轨的文件）。
8. T2：16 例单测、变异探针、端到端渲染已由轨内实跑（非 Gradle，见 §2 T2）。

---

## 6. 共享树说明（提交者必读）

### 6.1 本线文件清单（十轨 `files` 并集去重，共 **83** 个路径）

本记录者只读核对（2026-10-17 05:38 前后）：83 个路径在共享工作树**全部存在**；材料 T7 `files` 中
`core/data/src/test/.../backup/SmkbArchiveCodecTest.kt` 为**误拼**（不存在），实际是 `SmbkArchiveCodecTest.kt`（存在），
下方清单已按实写。逐轨归属见 §1/§2。

```
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/SplitImportDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomSplitImportStore.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/SplitImportPort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt
core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/SplitImportPortFake.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/LearningProjectionPort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/LearningDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/CleanRedrawTaskContract.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/CleanRedrawTaskMigration_65_66.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CleanRedrawTaskEntity.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/CleanRedrawTaskDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/CleanRedrawTaskPort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDraftTransactionDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/DraftPort.kt
core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/CleanRedrawTaskSchemaContractTest.kt
core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KernelWave0SchemaContractTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FullMigrationMatrixInstrumentedTest.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDbValue.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ChatEvidenceDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/ModelTaskDatabasePort.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/splitimport/RoomSplitImportRepository.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepositoryTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/SplitImportReceiptReconciliationInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemStore.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemStoreTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportSourceStaging.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomBatchImportRepository.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportStagingOrphansTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/BatchImportInterruptedStagingInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunner.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskRunnerTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CleanRedrawTaskInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSettingsDocument.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodecTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettlerTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeEvidenceWriter.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAudit.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudySchedulingCalibration.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeEvidenceLedgerStatusTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAuditTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizCooldownChannelScopeInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAuditInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceIdentityInstrumentedTest.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/SplitImportRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BatchImportRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueue.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueueTest.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MasteryWriteGate.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/ChatEvidenceGateCalibration.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/ChatEvidenceGateCalibrationTest.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ModelTaskFingerprintStabilityTest.kt
core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ImagePipelineClassifySourceShaTest.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/LearningState.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolTrace.kt
core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ModelTextSurrogateTruncationTest.kt
feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/BatchImportRoute.kt
feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/BatchImportInterruptedNoticeInstrumentedTest.kt
feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt
tools/ci/generate_status.py
tools/tests/test_ci_generate_status.py
.github/workflows/status-template.md
docs/specs/2026-09-06-dual-review-entry-design.md
docs/known-defects.md
docs/research/algorithm-version-ledger.md
```

（模块计：core/database 25、core/data 29、core/domain 12、core/model 6、feature 3、app 2、tools 2、.github 1、docs 3。）

### 6.2 同批内多轨共编文件（评审/提交勿按单轨 hunk 挑拣）

- `RoomStudyDatabase.kt`：T1/T3/T5/T6/T9 五轨；`RoomBackedStudyExperienceRepositoryTest.kt`：T1/T3/T5/T9 四轨。
- `KnowledgeQuizItemStore.kt`：T3/T9（T9 声明只改其读口/解码分层、未动 T3 的覆盖重写逻辑）。
- `ModelTasks.kt`：T6/T10（T10 两处在 `:686` 与 `:1315`；T6 约 +60 行在其它区段；T10 提醒勿引用位移行号）。
- `SmartMistakeBookApplication.kt`：T1/T6/T7（另载他线 restart-reason 块）。
- `StudyExperienceRepository.kt`：T3/T5；`algorithm-version-ledger.md`：T9/T10；`known-defects.md`：T9（T2/T3/T10 声明未动
  该文件，避免并发改同处）。
- 各轨声明 hunk 均在不同区域、已逐段 diff 核对不重叠；**整份文件的最终一致性需门禁/复核确认**（T1/T3/T5/T9 均如此声明）。

### 6.3 他线在飞（不属于本批十轨，勿混提 / 勿覆盖）

- **KB 线（批次 12）**：`core/data/.../knowledge/dense/DenseFusion.kt` 的 α=0.7 临时改动（预注册写死、见 §4.1）、
  `docs/research/2026-10-16-d3-round1-prereg.md`、`plan-sess_b4ae5030-….md`、`build/agent-batch12/`（00:00 后仍在写）；
  HEAD `9fbb534c` 即 KB 线提交。
- **另一条在飞线（与 T4 共编）**：`BatchImportRepository.kt`、`RoomBatchImportRepository.kt`、`BatchImportRoute.kt` 三个文件载其改动
  （`BatchImportDraftState`/`savedEntryId`、draft 状态映射、`onOpenSavedEntry` 与页行改写；其新文件如
  `BatchImportOutcomeInstrumentedTest.kt`、`MistakeDetailRoute.kt`）——**T4 材料明示：提交时不能按「本线独有」处理这三个文件**。
- `SecondaryScreens.kt`：T7 只落消息构造段（WRITE_FAILED 文案），同区段另有他线 `StorageOperationsViewModel` 改造。
- T1/T3/T9 的共享文件另有他轨 hunk（restart-reason 块、KD-41 覆盖重写、会话恢复失败屏等）。
- T9 声明：其改动按**文件整份**纳入提交（勿按 hunk 挑拣）；KD-43 已登记，T13 勿重复追加。

### 6.4 提交口径要点（各轨声明汇总）

- **T6**：`core/database/schemas/.../66.json` 是构建产物（轨内写作时点尚未生成），提交时需一并纳入本线文件清单。
- **T2**：`docs/status.md` 未由本线重生成（CI 生成物），不在本线改动列表。
- **T10**：台账 §3.18（`:236`）等历史行与 §2 号段勿动；文件行号引用勿用位移值。
- **T4**：三个共编文件按「含他线改动」处理。
- **T1**：`markConfirmed` 保留（无生产调用方，KDoc 已注明）——评审勿当死代码删。

### 6.5 提交状态（本记录者只读核对，2026-10-17 05:38 前后）

- HEAD=`9fbb534c`（KB 线批次 12 提交）；暂存区 **0** 条；`git status --porcelain` **500** 条（含他线改动与未跟踪产物）。
- 本记录者未见本批十轨的任何提交（HEAD 为 KB 线提交）；十轨改动均在共享工作树中。

---

## 7. 待裁定 / 建议登记

1. **T5「每日 ≤10」口径**：现实现为只封第二档增量（今日队列不被挤掉）；若 owner 本意「并集整体取前 10」→ 把 `take(N)`
   移到并集之后（一行改动，KDoc 已写明当前口径）。
2. **T8 零 bump 折入**：请 T13 把 zero bump 依据（§2 T8）折入 `algorithm-version-ledger.md`；若 owner 判应 bump
   （PROJECTOR/ATTRIBUTION，复合串 learning-core-v13），预期全量重放会**追溯改写存量讲题判定 attempt 的掌握度归属**——
   属裁定未要求的追溯性变化，需一并决定。
3. **T8 投影后果点收**：KC 收到 0.5（整条 attempt 证据）并与 chat 通道 ≤0.15 并存；`MasteryOverviewDao.kt:112-124` 的
   per-node 负向聚合会让知识点档案的错因计数开始变化——与 `docs/research/model-judged-verdict-pricing.md` §4(ii)
   「模型判对的 KC 写入上限 0.15」存在张力，请复核。
4. **T6 两处口径判断人工复核**（「最新」= `error_book_entry.current_revision_id`；作业指纹=逻辑操作指纹）；**T6 残留并发窗口**
   （两执行者各领一次的可能）列为 T13 对抗性复核关注点。
5. **T6 设备红处置**：`CleanRedrawTaskInstrumentedTest.kt:68` 确定性红（3/3）——材料未归因、未处置；提交前需点名（修或登记）。
6. **T9「载荷带引用」解释面**：推导式（零 schema 改动）vs 真列（需 schema 65→66，与 T6 撞号）——请复核确认裁定原意。
7. **T10 活截断点**：`OpenAiModelResponseParsers.kt` 6 处 + 网关 `streamingPrefix` 未修；若任务书本意是修这两处「活点」，
   需另开 core/data 轨（本轨按范围纪律未触碰）。
8. **T8/T9 文档与附带影响**：T8 两处文档待办（`REFACTOR-MASTER-LINE.md:56`、`known-defects.md:1672-1685`）归 T13；
   T8 附带影响（档案错因计数变化）请复核点收。
9. **KD 关账状态**：KD-39（T1）/KD-40（T2）/KD-41（T3）/KD-42（T4）待 T13 关账与折入；KD-43 已由 T9 登记（勿重复）；
   材料明说 KD-40、KD-41 仍 open，其余条目当前状态材料未记录。
10. **单测门口注（照录供 owner 核）**：任务书点名红（「GoldenRetrieval 系列，4 条」）与实测 3 条同根因红（KB 线 α=0.7）
    的口径差异；KB 预注册的 α 临时改动按纪律「采纳或逐字回滚」，本材料未见最终处置。
11. **T4 共享三文件提交口径**（`BatchImportRepository.kt` / `RoomBatchImportRepository.kt` / `BatchImportRoute.kt` 载他线改动）。
12. **T2 给 T13 的编号歧义提醒**：计划 T13 里「T2 可变缝 KDoc 注记」属上一批 T2，勿按本批编号直解。
13. **T6 66.json 纳入提交清单**（构建产物，轨内写作时点不存在）。
14. **T7 内容勾选**已登记为 6C-2（轨内声明，无需重复）。

---

## 8. 收尾补记（主会话，2026-10-17）

**提交（2 笔，未推送）**：
- `463a5dea` —— 十轨全部（83 文件清单剔除四个共编文件 + `66.json`，共 80 路径）。剔除的四个共编文件
  （`SecondaryScreens.kt`、`BatchImportRoute.kt`、`core/domain/.../BatchImportRepository.kt`、
  `core/data/.../RoomBatchImportRepository.kt`）载他线在飞改动，**留给该线按清单提交**——T7 的消息构造段与
  T4 的路由横幅段随其进入历史（`BatchImportSourceStaging` 读口与本线测试已在 `463a5dea`）。
  `SmartMistakeBookApplication.kt` 按记录 §6.3 整份纳入（含他线 restart-reason 块，自足、无未跟踪依赖）。
- `472394ea` —— 收尾修复（2 文件）：`OpenAiModelResponseParsers.kt` 6 处解析期截断 + 网关 `streamingPrefix`
  改用 `takeBudgetChars`（KD-38 剩余项补齐；§3.20 末段的「不在本次修复面内」已被本批推翻，见 ledger §3.22）。

**收尾修复（主会话，均已过门）**：
- T6 领取 CAS 误报（对抗性复核 finding 3）：以 UPDATE 命中行数为准（`CleanRedrawTaskDao.kt`）→ `463a5dea`。
- T6 仪器化用例竞态（设备 3/3 确定性红）：`awaitClassifySucceeded` 等分类行落成 SUCCEEDED（领取早于分类）
  → `463a5dea`；**设备复验 `CleanRedrawTaskInstrumentedTest` 2/2 绿**（emulator-5554，connected 实跑）。
- T5 第二档减法改 `todayCandidateIds`（对抗性复核 finding 4：材料查询 64 条截断会让「两档都漏」）→ `463a5dea`。
- 单测门在收尾修复后重跑：`:core:model:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest`
  BUILD SUCCESSFUL（exit 0）——§4.1 的 3 条 KB 线 α=0.7 红已随 KB 线**回滚**（`DenseFusion.ALPHA` 现为 0.5）消失；
  `:core:data:compileDebugAndroidTestKotlin` 同步 BUILD SUCCESSFUL。
- T8 投影后果复核点收：`attributionsForAttempt` 的 upcast 派生（KF-32 起）在「题有当前绑定（含 pseudo）」时
  本就从绑定事实派生归属，题级 0.5 进 KC 的流向**先于本批存在**；T8 只在「绑定为空/派生为空」的回退路径上改变
  行为（该路径下新快照的归属会被用上）。定价张力（题级 0.5 vs 定价文 §4(ii) 模型 KC 上限 0.15）属**既有**待裁定项，
  不构成 T8 引入的新语义（详见 ledger §3.22）。

**登记（主会话）**：KD-39/40/41/42 关闭（`463a5dea`，KD-42 设备核按独立核对纠正——提示实际已显示）；
新增 KD-44（判词锚定「空 KC 集」永久阻塞 + 文案误导，medium）/ KD-45（领取 CAS 误报，已修）/ KD-46（3E
「每日 10」实为每次 ≤10，low）/ KD-47（设置恢复非原子，low）/ KD-48（可观察性无消费面，low）/ KD-49（切分
对账索引前导列，low）/ KD-50（`isCurrentProblemRevision` 判据宽，low）/ KD-51（冷启动 KB 搜索特性重建饿死
连接池 → ANR + 采集卡死，medium）。

**仍未做 / 未达（照录）**：设备模型轨全部未达（429，不重试不换凭据）；AUD-05 恢复拒绝设备核未做（DB 饥饿 +
SAF 前置）；AUD-09 设备核未达（同上）；设备走查多条自述被独立核对更正（见 §3.5，均已按更正记录）；6C 内容
勾选 = 6C-2；对抗性复核 findings 未设备复现；T7 的 UI 消息段与 T4 的路由横幅段随他线提交。
