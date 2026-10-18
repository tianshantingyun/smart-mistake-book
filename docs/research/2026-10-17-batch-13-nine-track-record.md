# 九轨批记录：6B-1 docx 导出（重开）+ schema 67（收藏/手动转写）+ KD-51 重建分块 + KD-44 判词自动结算 + KD 低危包 + 6C-2 设计件（批次 13，2026-10-17/18）

计划来源：`.zcode/plans/plan-2026-10-17-batch-13-6b-4c-kd51-kd44.md`（8177 字节；文件标题即「下一批（九轨）：6B 导出半边 + 4C 收藏/手动建题 + KD-51 连接池 + KD-44 自动结算 + 低危包（长工作流）」；
§1 写明「九轨（七实施轨 + 设备轨 + 收尾轨）」，T8=设备轨、T9=收尾（门禁 + 独立核对 + 对抗性复核 + 记录 + 台账同步 + 关账与提交））。
运行脚本：`.zcode/workflow-runs/dwfrun-31ed866d-4a7d-4db5-abf9-0b76479b2c4a.mjs`（49,914 字节；脚本板面与汇总产物自称「十三轨」，即 13 个 actor：七实施轨 + 门禁归因员 + 门禁修复协调 + 设备走查员 + 独立核对员 + 对抗性复核员 + 批次记录员；本记录文件按派单名 `nine-track`。
本记录者只读核对了该脚本里的 `runUnitGate` / 强门禁命令与 `KNOWN_REDS` 常量，§4 引用均出自脚本原文）。

**记录口径**：本记录的事实来自本轮运行给出的批次材料（T1–T7 逐轨材料 + 单测门/强门禁结论 + 设备段 + 独立核对 check 段），并逐处注明来源
（轨自述 / 门禁执笔 / 设备执笔 / 独立核对执笔 / 本记录者只读核对）。**本记录者未运行 Gradle、未接设备、未做独立复核**；凡「通过」均注明由哪一环跑出。材料未覆盖处一律写「未记录」。

**材料完整性声明**：材料是脚本 `JSON.stringify({tracks, unitGateExit, unitGateVerdict, unitGateRounds, androidTestCompileExit, assembleExit, strongVerdict, device, check, review}).slice(0, 60000)` 的前 60,000 字符 →
① `check`（独立核对）段在**第 7 条 claim 的 evidence 中途截断**，其后内容未记录；② `review`（对抗性复核员 findings）**整段未随材料附上** → 未记录（脚本对 findings 的后续汇总与 `check.corrections` 亦未随材料附上）。
逐轨材料为 **T1–T7 共七轨**；T8（设备轨）无逐轨材料，其产出以「device 段 + 独立核对 check 段」两段给出（§3）；T9（收尾轨）的关账/台账同步/提交结果未随材料附上（见 §7）。

**本记录者只读核对（2026-10-18 15:58 前后）**：① 七轨 `files` 并集去重 **111 个路径**在共享工作树**全部存在**（逐轨 30/30/30/5/4/16/1）；
② `git log -1` HEAD=`07382e23`（与 T3 材料「HEAD 仍是 07382e23」一致）、`git status --porcelain` **588** 条、目标文件
`docs/research/2026-10-17-batch-13-nine-track-record.md` 此前**不存在**（本记录即新建）；
③ **67.json 现已存在**（mtime 2026-10-18 00:25:55、348,529 B、version 67、identityHash `9bf16ae8f2e162fac01cc0e0c111b9a8`；`error_book_entry.is_favorite` NOT NULL DEFAULT `'0'`、`problem_draft.source_asset_id` 可空、视图 `library_catalog` 含 `entry.is_favorite AS is_favorite`）——T2 材料「67.json 未产出」已被构建覆盖（§5-B）；该文件在 `git status` 中为 `??`（未跟踪，提交须纳入）；
④ 抽查十余处行号/内容引用：除 2 处行号漂移外均逐字符合（明细见 §6.6）；⑤ 强门禁的落盘佐证新鲜：三模块 androidTest 编译产物 10:42:37–10:42:45、`app-localFirst-debug.apk` = 2026-10-18 10:43:17；⑥ 设备段引用的截图/日志在宿主 Temp 存在（`kd51_after_fastscreen.png`、`aud05_restore_rejected.png`、`aud05_validate_invalid.png`、`kd51_install.log`、`kd51_forced_full.log`、`kd51_coldstart.log`）；⑦ `.artifacts/`（T4 两脚本）与 `.zcode/` 均被 `.gitignore` 忽略，`tools/export/` 为未跟踪目录；⑧ 测试结果目录**已被材料写作后的更晚运行覆盖**（详见 §4.4）。

---

## 1. 九轨批结果总表（材料所附：七实施轨）

设备轨与收尾轨无逐轨材料 → 见 §3 / §4；下表的「实跑证据」只记材料明文写出的那一环。

| 轨 | 文件数 | 结果 | 实跑证据（来源） | 未达 / 未验证 |
|---|---|---|---|---|
| T1 6B-1 docx 导出（重开） | 30 | 审计既有产出后修三处会挂门禁的缺陷（包级 Target、重复 newFile、失败文案）+ 竖长图按内容高封顶 + 结构性断言与 KDoc 如实化；链路（chips→worker→runner→hub）全链读码核对 | 轨内：`python tools/export/verify_mistake_docx.py --selfcheck` → exit 0（stdlib zipfile+ElementTree 结构通过、python-docx 打开 11 段、负向拒无 word/document.xml 包）；独立校验脚本（Temp，非仓库）→ exit 0（正例 10 文本节点逐条对上 + 两条负向）；`git diff --check` → exit 0 无输出 | 零 Gradle/零设备（轨内声明）；设备 docx 保存/分享链未达（模型不可达，§3.5）；「Word 真能打开」未由 Word/设备验证；JPEG 端到端字节未单测；手工 kotlinc 被安全 hook 拒绝 |
| T2 schema 67：C11 收藏 + A8 半边 | 30 | 单一迁移 66→67（收藏列 + 空源草稿 + 视图列）落地；收藏读侧两条链 + 行级写入 + 列表开关全通；A8 校验器按 provenance 分支 + 草稿读取 fail-closed | 轨内：`python tmp/t2_ddl_selfcheck.py` → OK（迁移 DDL 与 66.json 推出的期望逐字节一致）；`python tmp/t2_kotlin_selfcheck.py <30 文件>` → OK；facet 接线计数 grep=6/4（本记录者复跑一致）；设备段：`FavoriteAndManualDraftMigrationInstrumentedTest` **2/0**（§3.3） | A8 **创建入口链未交付**（blocked，§2 T2/§7.1）；手动题全链 instrumented 未写未跑；空源草稿在导出/详情的运行时行为未验；非分页替身路径无收藏数据源；67.json 材料写作时未产出（现已存在，§5-B） |
| T3 题级去重可见回执 | 30 | 去重判据一字未动，把「复用」变成可见回执：新记录 `ProblemDraftCommitOutcome` + 三提交口/两端口/装配回传 + 状态/卡片两态文案 | 轨内：`python …/t3_static_check.py` → 36/36 passed；`python …/t3_sibling_sweep.py` → 112 个已改 .kt 仅两处非破坏命中；设备段：`CaptureCommitReuseVisibilityInstrumentedTest` **3 tests / 1 failed（确定性红）**（§3.3） | 零 Gradle（轨内）；卡片未真机渲染；docs/台账未改（归 T9）；设备红归因/处置材料未记录；`created` 两读法已在用例与 KDoc 钉住（§2 T3） |
| T4 KD-51 重建分块 | 5 | 整科重建改「读相 + 64 块短写事务 + 块间可中断 + 锚点最后写」；删除改按既有 id 集分块删（输出行集逐位不变）；新增每科重建日志 | 轨内：`python .artifacts/kd51-t4/verify_chunked_delete_equivalence.py` → 全 PASS（行集逐行相同、残留 50 id 在删除集、单语句 ≤64×192、别科 660 行未触碰）；静态冒烟 PASS；设备段：`KnowledgeSearchIndexRebuildChunkingInstrumentedTest` **4/0**、强制全量对照 **0 条 `unable to grant`**（§3.2） | 重建占比实测未做（等设备读数）；写饥饿解除的设备口径为**人为构造的强制全量**（非用户路径）；`withReadTransaction` 内部行为未核实；读路径自愈分支（`RoomKnowledgeBaseStore.kt:267-275`）同形态剩余饿死面未纳入本轨 |
| T5 KD-44 判词自动结算 | 4 | 空 KC 集题锚定集换该题 revision 的伪绑定 `pseudo:<科目>`：已物化直接取、未物化复用先例物化；判词照常结算、归属指向伪绑定、队列推进；补「伪桶→改绑→真实节点」重放用例 | 轨内：3 项非 Gradle 自检（EOL 归一、括号平衡+用例名唯一、`git diff` 规模）；零 bump 判据命令输出（三条版本串未动）；设备段：`TutorJudgedReviewSettleInstrumentedTest` **2/0**（§3.3） | 零 Gradle（轨内）；「模型真把判词写在桶代号上」只有结构性依据无设备复现；残留阻塞面（真实候选节点证据仍 NO_VERDICT 等）待 T9 登记另裁；台账折入归 T9 |
| T6 KD 低危包（五件） | 16 | KD-46/49/50 KDoc 如实化并同步同口径引用点；KD-47 只改枚举与 RestoreReceipt KDoc（UI 文案留下）；KD-48 公开只读口 `scanRows()` + debug 屏 + 三态单测；KD-17 读证既有入口已覆盖，只补两条回归 | 轨内：`python .artifacts/t6-selfcheck/check_debug_manifest.py` → OK（debug 清单解析 + 新 Activity 断言 + release-manifest 工具函数采到新组件 + 15 文件括号启发式） | 零 Gradle（轨内）；release-manifest 门未实际跑（只验证其解析函数）；KD-47 用户文案/日志/测试 KDoc 未改（§5-A.7）；T6 两条新回归用例的设备执行结果材料未点名 → 未记录 |
| T7 6C-2 内容勾选设计件 | 1 | 972 行设计件（六节齐全：类别闭包 / 快照行级裁剪 / 恢复校验衔接 / 换机与旧包兼容 / manifest v3 草案 / 待裁定与风险）；闭包由 schema v66 的 90 条 FK 边推导（合法组合只有 5 组） | 轨内：6 个 python 探针/校验全通过（dump_schema、check_selection_plan、dryrun_trim、probe_fts_rebuild、probe_free_pages、dump_table_columns） | 零 Gradle；所有 Android 设备侧事实未实测；67 形态 FK 边待 67.json 导出后重跑校验（现已可跑）；三项待裁定（§7.2） |

（模块计〔本记录者按 §6.1 清单口径核对〕：core/database 28、core/data 14、core/domain 12、core/export 16、core/model 3、feature 27、app 7、tools 1、docs 1、.artifacts 2，合计 111。）

---

## 2. 各轨明细

### T1 6B-1 docx 导出（重开）

**任务**：审计本轨既有产出（6B-1 的 docx 渲染/导出面）后修复三个会挂门禁的缺陷，并补边界与结构性断言。

- **修法一（OPC 包级 Target）**：`MinimalDocxWriter.packageRelsPart` 原把包级关系 Target 写成 `document.xml`；包级关系（`_rels/.rels` 的源就是包本身）Target **相对包根**，必须写完整的 `word/document.xml`——旧写法 Word 打开即报损坏，且 JVM 金样断言
  `Target="word/document.xml"` 必红。落点：`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MinimalDocxWriter.kt:111-127`（`Target="$DOCUMENT_PART"`，KDoc 写明理由）；图片关系仍为 `word/document.xml` 相对的 `media/imageN.ext`（`:130-144`）。
- **修法二（测试夹具）**：`DocxMistakeRendererTest` 同一用例内二次 `TemporaryFolder.newFile("mistake.docx")` 会抛 IOException → 改为递增文件名的 `File(root, …)`
  （`core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRendererTest.kt:351-357`）。
- **修法三（失败文案断言）**：批量 Word 渲染失败用例断言了该失败档不会出现的「Word 文档」文案（Batch+PDF_SIZE_LIMIT 走「内容较多」文案）→ 改用 `FILE_PUBLISH_FAILED`
  （`core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunnerTest.kt:306-322`；批量超限的可行动文案见 `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt:108-116`）。
- **另补**：竖长图按**内容区高度**封顶（14918 twip = 9472930 EMU）后等比缩宽（与 PDF `imageWidth` 同口径，`DocxMistakeRenderer.kt:115-144`）+ 边界用例 `DocxMistakeRendererTest.kt:217-233`；`DocxImageSource` 的 PNG/JPEG/坏输入 JVM 用例；`w:sectPr` 必须是 `w:body` 末子元素的结构断言（`DocxMistakeRendererTest.kt:70-72`）。
- **KDoc 如实化**：写明 Word 侧降级（页脚、作答空白区、图形只文字、公式写 LaTeX、图片缺失省略）与 6B-2/3（App 内编辑/自然语言改卷）未做。
- **链路（读码核对）**：`TutorExportSheet` 格式 chips → worker Data `format` 键 → runner 按 format 分派 `DocxMistakeExporter` → 记录 `.docx` → hub 按扩展名分流保存/分享（Word 隐藏打印）。
  落点：`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt:327-348`（格式 chips + Word 能力说明）、`app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt:350-361`（format token：缺键=PDF、坏值整条拒）、
  `feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubRoute.kt`（`deliveryFormat` 分流保存/分享）、
  FileProvider/MIME：`core/export/src/main/AndroidManifest.xml:13-21`（`${applicationId}.mistake-docx-exports` + `@xml/mistake_docx_export_paths`）与 `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxDelivery.kt:104-136`（MIME/authority/SAF/SEND+clipData）。
- **共享文件**：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt` 仅导出接线 hunk（`:403-404`、`:475-476`）归本轨；同文件其余 hunk 属他线（databaseRestart/BatchImport/LearningMastery/StorageScreen），未触碰。未碰 `MistakePdfPagePlanner.kt` 及其测试（他线在飞），也未碰其它 35 个他线文件。
- **未跑/未达（轨内声明→见 §5-A.1）**：未跑 Gradle（七模块单测、androidTest 编译三模块、整包编译均未执行）；手工 kotlinc 编译校验被 Bash 守卫拒绝；python-docx 只打开过脚本自产的同构样本（非 Kotlin 渲染器真实字节）；设备保存/分享（SAF + ACTION_SEND + FileProvider URI 授权）未走（归 T8）；`word/media` 的 JPEG 分支只有头部解析单测；「Target 修复后 Word 真能打开」未经 Word/设备验证。

### T2 schema 67：C11 收藏 + A8 手动题（schema 半边）

**任务**：一轨一个迁移（66→67）+ C11 收藏链 + A8 的 schema 半边与校验器 provenance 分支。

- **单一迁移 `FAVORITE_AND_MANUAL_DRAFT_MIGRATION_66_67`**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:130` 版本 67、末位注册〔材料记 :383；本记录者核对为 :384〕）：
  (a) `error_book_entry` 纯追加 `is_favorite INTEGER NOT NULL DEFAULT 0`（实体带 `defaultValue="0"`）；
  (b) `problem_draft` 重建表把 `source_asset_id` 改**可空**（建新表→逐列搬运→DROP→RENAME→重建两索引，沿用 `KernelWave4Migration_56_57` 范式）；
  (c) `library_catalog` 视图 DROP+CREATE 加 `entry.is_favorite AS is_favorite`（与 `@DatabaseView` 逐字一致）。
- **C11 收藏**：`LibraryQuery.favoriteOnly` + `LibraryFacetKind.FAVORITE`（facet 口径=**不带自己这一维过滤**，库内 count 0 也如实返回一行 favorite/已收藏）；
  View/DAO/FTS DAO/Store/Port/`RoomStudyDatabase`/仓储/VM/UI 全链打通；行级写入复用 `MistakeDetailDao` 先例（`setEntryFavorite` 仿 `:115-137` 的 `setUserNote`）经
  `MistakePort.setErrorBookFavorite`→`MistakeDetailRepository.setFavorite`→`LibraryRoute` 列表行的书签开关（写不到就如实 false，UI 用 `library_favorite_notice` 展示失败）。
- **A8**：校验器 `BLANK_SOURCE_ASSET_ID`/`MISSING_SOURCE_REGION` 仅按 `provenance==USER_TRANSCRIPTION` 放行（USER_CORRECTION 及其它来源照旧拒）；
  `ProblemDraftEntity.sourceAssetId` 改可空、`loadDraft`/待处理头部读取对空源行 fail-closed、tutor 确认路径的逐块比对按同一 provenance 放行。
- **关键取舍（blocked 原文要点）**：A8 的**创建入口链**（CaptureScreen/CaptureViewModel 入口、`importDraft`/`initialDraftCommand` 空源分支、`confirmAndCommit` 空 `resolve` 分支、`ResumableCaptureDraft` 空源驼生）本轮未做——要连带改 draft 记录类型/summary/resume 与录入 UI 约 40 处调用点，在无编译器可验证的约束下风险大于收益，已如实留在 blocked。**请裁定**：下一轨接 A8 创建链（带编译器迭代），或按现有门禁反馈先修本轨。
- **证据（轨内自跑，非 Gradle）**：`python tmp/t2_ddl_selfcheck.py` → `OK: 迁移 DDL 与 66.json 推出的 67 期望逐字节一致（3 段、8 句）`（脚本从 66.json 推出期望并解析迁移文件里的 Kotlin 字面量比对）；
  `python tmp/t2_kotlin_selfcheck.py <30 个改动文件>` → `OK: 30 个文件通过浅层语法体检`；facet 接线计数 `grep -c "favoriteOnly = 0 OR catalog.is_favorite = 1"`：`LibraryQueryDao.kt=6`（pagingSource/page/count+三个 facet）、`LibraryFtsSearchDao.kt=4`（countSearch+三个 facet）；
  FK 时序依据：`core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt:753-754` 的 `onOpen` 里 `PRAGMA foreign_keys = ON`（迁移之后才开），与 `KernelWave4Migration_56_57.kt:19-23` 记录的同一依据一致；收藏写入先例：`MistakeDetailDao.kt:115-137`。
  （`tmp/` 为未跟踪目录，脚本可重跑但不进提交；证据里另记 `t2_fts_copy_sync.py`/`t2_ranking_copy_sync.py`/`t2_named_params_selfcheck.py`/`t2_schema_version_selfcheck.py` 等门禁修复环脚本，mtime 10:08–10:39，本记录者核对。）**本记录者复跑核对**：67.json 内容与上述三点一致（§头部③）；`StudyDatabase.kt:130` 现为 67（HEAD 为 66）。
- **契约与仪器化用例（写就未跑，后由设备/门禁跑）**：`FavoriteAndManualDraftSchemaContractTest`（JVM，钉 67.json 的列/索引/视图逐字与迁移 DDL 一致）、`FavoriteAndManualDraftMigrationInstrumentedTest`（v66 带数据迁移到 67 + 视图读 `is_favorite` + 空源草稿 + 幂等 + catalog/FTS 两条链 favoriteOnly 分页/计数/facet + `setErrorBookFavorite` 写后即时生效）、`ManualTranscriptionEvidenceExemptionTest`（USER_TRANSCRIPTION 放行，其它 provenance 与 USER_CORRECTION 仍拒，转写块非法区域仍拒）。
- **既有用例同步（必要改动，非放宽）**：`LibraryCatalogTest` 的 facet 清单断言加入 FAVORITE（“科目/板块/掌握程度”→“科目/板块/掌握程度/收藏”）并新增收藏筛选用例；`LibraryRepositoryInstrumentedTest.kt:106-107` 的 `LibraryCatalogItem` 位置参数补 `false`（新字段插在 `nextReviewAt` 之前）；`RoomBackedStudyExperienceRepositoryTest.kt:2551-2589` 的 `LibraryReadPort` 替身补齐新参数。
- **未验证（轨内声明→§5）**：未运行 Gradle（编译/单测/androidTest/连接设备均未跑）；67.json 当时未产出（KSP 构建产物，现已存在）；手动题全链 `created=true` 且 `provenance=USER_TRANSCRIPTION` 的仪器化未写也未跑；「无照片草稿在既有下游（导出/详情）不崩」未验；非分页替身路径（`catalogRepository == null`）没有收藏数据源（`LibraryMistake.isFavorite` 恒 false，开关只能提示「当前入口不支持收藏」，已写进 KDoc）；详情页收藏开关（`MistakeDetailRoute` 他线在飞）与复习/组卷按收藏筛选未做。

### T3 题级去重可见回执（B5）

**任务**：把题级去重从静默变成可见回执，**判据一个字没动**（仍是 `ProblemDraftTransactionDao.resolveCommittedProblemIds` 里那次 exact-match 查询）。

- **做法**：新增 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/ProblemDraftCommitOutcome.kt`（原回执 `commit` + `reusedExistingProblem`）；
  DAO 的三条提交口（`commit`/`replayCommit`/`commitTutorSession`）与 `DraftPort`/`TutorPort` 两个口、`RoomStudyDatabase` 三条 override 一并回传它；
  `CapturedProblemCommitSummary` 加如实字段 `reusedExistingProblem`（无默认值，两条提交链都显式填）；`CaptureWorkflowState` 加 `savedReusedExistingProblem`，与 `savedEntryId` 同一次 reduce 原子写进 `SAVED`；
  录入界面按 `testTag capture_committed_receipt` 的卡片区分「新建入库」与「已存在同一题（已复用）」。
- **口径**：`reusedExistingProblem` = 「这次提交没有写出新的 problem 行」——命中既有题行 → true；同一笔提交的幂等重放 → true；真的新建 → false。
  它和 `created` 回答两个不同问题：`created = true` 的提交既可能新建、也可能命中既有题行（后者不回执复用就会误报成新建）。
- **取舍**：`CommitProblemDraftResult` 定义在在飞文件 `StudyDatabaseRecords.kt`，故用新记录承载指示而不触碰该文件；`created` 语义保持不变（true=这次调用做了写入），未改任何既有消费点。
- **证据（轨内实跑，非 Gradle）**：读计划文件 `.zcode/plans/plan-2026-10-17-batch-13-6b-4c-kd51-kd44.md` 的 T3 小节（:26-27），并按它给的行号核对现状（`resolveCommittedProblemIds`/`findExactCommittedProblem`/`reuseCommittedProblem` 与 `CaptureWorkflowRepository.kt:507-514` 的 `created`）——现状与任务书描述一致；
  静态自检 `python C:/Users/听云/AppData/Local/Temp/t3_static_check.py "D:/smart mistake book"` → **36/36 passed**（覆盖 outcome 形状、DAO 新建枝 flag=false/复用枝 flag=true、三条口与端口两处+TutorPort 回传、装配层三条 override、摘要/状态/动作字段与状态不变量、两条提交链都填指示且不再读旧字段、卡片与文案两态、旧类型无外部引用、全部摘要构造点都带指示）；
  交叉核对 `python C:/Users/听云/AppData/Local/Temp/t3_sibling_sweep.py "D:/smart mistake book"` → 112 个已修改 `.kt` 中仅两处命中且都不是破坏面（`StudyDatabaseRecords.kt` 的**定义**〔本轨未触碰〕与 `CapturePendingCapturesInstrumentedTest.kt:347,355` 的返回类型）；
  未触碰会话开始时 `git status --porcelain` 列表里的 35 个在飞文件；本轨未执行任何 git 写操作（HEAD 仍是 `07382e23`）。
- **新增/改动用例（写就未跑；后由设备跑，见 §3.3）**：`core:data` instrumented 新文件 `CaptureCommitReuseVisibilityInstrumentedTest`（新建→flag=false 且 problem 表 +1；同题面第二份草稿→flag=true、problemId/problemRevisionId 与第一笔相同、problem 表不增；同一笔重放→created=false 且 flag=true、problem 表不增；不同题面→flag=false、problem 表 +1；problem 行数用只读 `SELECT COUNT(*) FROM problem`，先例 `CleanRedrawTaskInstrumentedTest.kt:267-275` 的 `withRawDatabase`）；
  DAO 级既有去重用例 `exactRepeatedCaptureReusesOneMistakeAndKeepsEverySourceImage` 与 `exactCaptureReusesLegacyMistake…` 加三态断言；`core:domain` 状态机加 3 例（SAVED 默认 false / 复用指示随 Reset 消失 / 非 SAVED 不许声明复用→IllegalArgumentException）；
  `feature:capture` 加 `CaptureCommittedReceiptTest` 4 例（两态文案互斥且各自说清新建/复用、库保存写指示、非复用覆盖旧指示、讲题保存不产生入库回执）+ 卡片 Compose 2 例 + `CaptureViewModelTest` 1 例（回执进 SAVED 状态）。
- **未跑/边界（轨内声明→§5）**：所有 Gradle 构建与测试未跑（本轨按纪律不运行 Gradle、不碰设备）；录入 UI 的设备/模拟器走查未跑（卡片文案只在 Compose 组件用例里断言）；
  任务书第 3 条「同题面二次提交 → created=false」的读法说明：`created` 的既有语义是「这次调用做了写入、还是幂等重放」，所以**同一份草稿重放**才是 `created=false`；
  **换一份草稿提交同一题面**是全新一笔提交（`created=true`、新信号为真、problem 行不增）。两种读法都在新用例里钉住，并在测试类 KDoc 写明。docs/台账未改（归 T9）；无 schema 变更（零迁移、零 66/67.json 改动）。

### T4 KD-51 冷启动重建饿死连接池（分块）

**任务**：把整科重建从「一条长写事务（COUNT+整科 DELETE+一次性 INSERT）」改成「读相 + 分块短写事务」，可中断，且**输出行集逐位不变**。

- **新结构**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt:97-161`）：①读相 `withReadTransaction`（计数+候选集+已索引 id 集，同一快照）
  ②删除分块循环（`deleteSearchFeaturesForNodes`，64 个节点 id 一块）③插入分块循环（`flatMap` 抽取在事务外）④锚点最后写；`chunkBoundary()` 在 `:163-171`（`coroutineContext.ensureActive() + yield()`）；
  测试缝枚举 `KnowledgeSearchIndexRebuildWrite`（DELETE/INSERT/ANCHOR）在 `:174-181`；分块上限 `REBUILD_NODE_CHUNK_SIZE=64` 在 `:189-197`；日志行在 `:152-157`（tag `KnowledgeIndexRebuild`，逐科 subject/nodes/features/deleteChunks/insertChunks/tookMs）。
- **删除侧等价性**（保住「输出行集逐位不变」）：先读该科已有特征行的节点 id 集（含非候选的残留行）再按 id 分块删；新增 DAO 查询 `readIndexedKnowledgeNodeIdsForSubject`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:370-384`〔本记录者核对函数在 :388〕，KDoc 写明理由），并**删掉失去唯一调用方的 `deleteSearchFeaturesForSubject`**（`git grep` 全仓为空，本记录者复跑 `rc=1` 无命中）。
- **不动**：读路径自愈（`RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex`）、跳过判据与两个调用方一字未改（`RoomKnowledgeContentReconciler.kt:102-104` 与 `RoomKnowledgeBaseStore.kt:283` 仍按 2 参构造，新参均有默认值）。
- **证据（轨内自跑，非 Gradle）**：`python .artifacts/kd51-t4/verify_chunked_delete_equivalence.py` → 输出含 `[data] rows total=157013 subject=MATH=156353 other=CHINESE=660`、`[A 路 改前] 单语句 DELETE：156353 行，154.6 ms`、
  `[B 路 改后] 读 id 集 1097 个 → 18 条 DELETE，共 156353 行；单语句最长 10.4 ms / 中位 9.1 ms / 总 153.5 ms`、`[PASS] 删除后行集逐行相同`、`[PASS] 该科被删掉的 156353 行逐行相同（含残留行）`、`[PASS] 非候选（残留）节点的 50 个 id 都在删除集里`、`[PASS] 单语句删除行数有界：max=9536 ≤ 64×192`、`[PASS] 别科 CHINESE 的 660 行未被触碰`、`[OK]`（桌面 sqlite 口径模拟，同主键+同两索引）；
  `python .artifacts/kd51-t4/check_kd51_static_smoke.py` → `[PASS] 定界符平衡：ProblemOrganizationDao.kt`、`[PASS] 定界符平衡：KnowledgeSearchIndexRebuildChunkingInstrumentedTest.kt`、`[OK] 三处改动定界符平衡；重建分段顺序（读→删→插→锚点）+ 块间可中断点均在位`（脚本自注「静态冒烟：不代表编译通过，也不代表行为正确」）。
- **测试（新增文件；后由设备跑 4/0，§3.3）**：`KnowledgeSearchIndexRebuildChunkingInstrumentedTest.kt:67-104` 冷重建分块数>1+锚点最后+行集等于抽取器直算；`:106-140` 二次重建删除块>1 且幂等；`:142-189` 块间取消后 `assertNull(锚点)`+部分行留存+重跑收敛；`:191-241` 锚点本已当前时的部分索引由读路径「已索引≥已审校」只补缺修复；夹具 130 节点 > 分块上限 64（KDoc:40-42 说明：上限调到 ≥130 时用例 1 会红，那正是要拦的单块形态）。
- **既有用例不回退的审查依据（只读期望，未运行）**：`KnowledgeSearchIndexCompletenessInstrumentedTest.kt:207-216` 断言安装路径失效代号增量 == 3（新代码 invalidate 次数未变）；`KnowledgeSearchIndexInstallInstrumentedTest.kt:64-119` 断言安装后锚点=INDEX_VERSION、两节点有特征行；grep 三个 `KnowledgeSearchIndex*` 仪器化用例只命中注释（无内部 API 依赖）。
- **耗时分解机制（读码+文档）**：既有日志 `BundledKnowledgePackResources.kt:86-92`（packs fully parsed）与 `BundledKnowledgeBaseInstaller.kt:56-59`（install finished）+ 本次新增每科 `KnowledgeIndexRebuild` 日志 → 设备上三段分解 = 解析 + Σ重建 + 其余调和；
  历史可引用读数：`docs/research/2026-10-11-thin-vertical-slice-walkthrough.md:256-263`（`packs fully parsed in 29059 ms (2 packs)`、`install finished in 1133477 ms (full path: parse + reconcile)`、`knowledge_search_feature`=623,774 ／ `knowledge_node`=4,187）⇒ 解析 29,059ms（2.6%）/ 调和 1,104,418ms（97.4%）。
- **改动范围（轨内实跑）**：`git status --short -- <四路径>` 只列出两个 M（KSIB、ProblemOrganizationDao）——`RoomKnowledgeBaseStore.kt` 与 `RoomKnowledgeContentReconciler.kt` 未改。
- **未验证（轨内声明→§5）**：未跑任何 Gradle 编译/单测/仪器化（不跑 Gradle、不碰设备）；Kotlin 编译正确性只有静态冒烟（定界符平衡+分段顺序）；
  设备 before/after（冷启是否还有 270s 级等待）属 T8（§3.2 已由设备跑，含「0 条 unable to grant」）；**重建实测占比未测**（本轨只有解析 29,059ms 与总量 1,133,477ms 两个既有读数）；
  分块删除的等价性证据有两级：桌面 sqlite 模拟（实跑）+ 新增仪器化用例（后由设备跑 4/0）；分块是否真解除写连接饥饿未直接测（桌面读数只是删除相口径，非 Android）；`withReadTransaction` 取自 room3 池中读连接的内部行为未核实（依据是同仓既有用法）；
  按任务书「读路径自愈不动」未改：`RoomKnowledgeBaseStore.kt:267-275` 的「只补缺」分支仍是一条写事务插全科候选特征行（同形态的剩余饿死面，如实登记，未纳入本轨）。

### T5 KD-44 判词空 KC 自动结算

**任务**（按裁定「后台自动解决、零用户参与」）：空 KC 集题的判词自动结算到伪绑定锚，靠既有改绑重放自纠，不加任何面向用户的绑定指引/文案。

- **现状核实（读源码）**：旧 `TutorJudgedReviewSettler.kt:323-330` 的 `.filter { it.knowledge_node_id in questionKnowledgeNodeIds }` + `:125-144` 的 `noVerdict()` 出口，确认空 KC 集题判词被整条丢弃后永久阻塞；
  KC 集为何为空：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDao.kt:122-157` 的 knowledge 子查询要求 `problem_classification_binding` 有 `dimension='KNOWLEDGE'` 行，而伪绑定（`RoomKnowledgeBaseStore.kt:118-128` 的 `sourceType="PSEUDO_FALLBACK"`）没有 → 被滤掉；
  伪绑定行形状与物化先例：`RoomKnowledgeBaseStore.kt:106-138`（bindingId=`pseudo-binding:$practiceUnitId:$problemRevisionId:$taxonomyVersion:pseudo:<SUBJECT>` + 幂等 INSERT IGNORE）、先例 `StudyPracticeUnitFacts.kt:203-220`、`StudyProjectionDrainer.kt:346-363`、`StudyReviewPlannerService.kt:237-253`；
  现实依据（模型确实会写在桶上）：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/RoomModelTaskRepository.kt:948-1012` 每轮 Plan/Respond 都把本科「未分类」桶塞进会话代号表（UNCLASSIFIED_BUCKET）；`RoomTutorToolRunner.kt:804-906` 解析代号后按 `KnowledgeAnchorClass.of`（`core/model/.../TutorKnowledgeCode.kt:47-65`）落 `anchor_class=DISCLOSED`（只减半、不拒写）。
- **修法（落点）**：锚定集 `TutorJudgedReviewSettler.kt:345-358`——先取门控接受证据，空集直接 return（真·无判词不解释锚定、不物化）；KC 集为空才解析伪绑定，失败即空集=fail-closed；
  伪绑定解析 `:378-418` `pseudoAnchorBinding`（已物化直接取→否则 `ensurePseudoKnowledgeBinding` 复用先例；KDoc 写明为何不能用回执口径的「当前绑定」读口，依据 `ProblemOrganizationDao.kt:499-552` 与 `BindingChangedReplayDrainerTest.kt:266-268` 的既有幂等 (b) 分支）；
  归属落账 `:446-455`（现役绑定 + `listOfNotNull(pseudoAnchor)` 同一过滤，节点+revision 都对上才选中）与 `:468-482`（`Verdict.pseudoAnchor` 一并带出，不重复解析）；调用点 `:147-157`。
- **自纠路径（读码核实+补覆盖）**：`core/domain/.../LearningProjector.kt:964-975` `attributionsForAttempt` 在拿到当前绑定事实时从 `derivePracticeUnitBindingAttributions` 重派生，`:933/:938-950` `applyAttempt` 消费派生归属；装配侧 `StudyProjectionDrainer.kt:313-329` 给当前绑定（空则伪绑定事实）；
  既有 KF-32 重放用例只覆盖「真实节点→改绑」与「真实→伪桶」两个方向 → 在 `BindingChangedReplayDrainerTest.kt:133-195` 补一条最小重放用例 `an attempt settled on the pseudo bucket moves to the real node after the unit is bound`（夹具归属=settler 会写下的那条；断言改绑后重放落 kc-new/binding-new、伪桶归零）。
- **SettlerTest 新增 4 例**：`anUnclassifiedQuestionSettlesItsPseudoAnchoredVerdict`（:230-280，RECORDED + 归属=伪绑定行 + 物化请求同规则 + currentOrdinal=1/COMPLETED + MODEL_JUDGED 档）、
  `anAlreadyMaterializedPseudoBindingIsUsedInPlace`（:282-317）、`anUnclassifiedQuestionWithoutAnyVerdictIsStillBlocked`（:319-340，真·无判词不写不推进不物化）、`anUnclassifiedQuestionStaysBlockedWhenThePseudoBindingCannotBeMaterialized`（:342-359，fail-closed）；
  既有 :217-241 用例**语义更新而非删弱**：`aQuestionWithoutKnowledgeNodesCanNeverAnchorAVerdict` → `aRealNodeEvidenceDoesNotAnchorAnUnclassifiedQuestion`（断言原样保留，注释改为「锚定集是本科桶，真实节点证据仍不构成判词」）；`anAnchoredVerdictWithoutAMatchingBindingStaysBlocked`（:361-381，原断言保留+新增「KC 集非空的题不走伪绑定兜底」）。
  测试夹具：`TutorJudgedReviewSettlerTest.kt:594-654` seededDatabase 改为 `PseudoAnchorFakeDatabase` 并播题目评估记录；`:801-855` 本地子类只补三个读口/留痕，不动共享 fake（先例 `BindingChangedReplayDrainerTest` 的 LedgerAwareFakeDatabase）。
- **零 bump 核实（轨内实跑命令）**：`git diff --name-only | grep -Ei 'learningcoreversions|learningprojector'` 无输出；`git diff --name-only -- core/domain/.../LearningCoreVersions.kt core/domain/.../LearningProjector.kt` 无输出 → 三条版本串、投影器、schema、指纹、提示词版本一字未动（命中项 RoomStudyDatabase/StudyDatabase/StudyDatabaseRecords 均属他线/他轨在飞文件）。
- **范围核实**：本轨 4 个改动文件全在 core:data 与 core:domain，`git diff --name-only` 里无 app/feature 文件由本轨改动（UI 文案「本次讲题还没有形成判定」保持只对真 NO_VERDICT）；交叉核实他线在飞改动无交集（`RoomTutorToolRunner.kt +3/-1`（B5）、`StudyReviewPlannerService.kt +4/-2`（KD-46 KDoc）、`RoomBackedStudyExperienceRepository.kt +3/-1`（B5 注释）均不含 pseudo/结算路径）。
- **自检（轨内实跑）**：①EOL 归一（两文件 CRLF→LF，`OK: only CRLF -> LF rewrites, content hashes unchanged`）；②复跑行尾检查（4 文件 `crlf=0`、`git ls-files --eol` 全 `i/lf w/lf`）；③结构自检（`PASS: bracket balance + @Test name uniqueness (NOT a compile)`——Settler 括号平衡、SettlerTest @Test 24 个〔HEAD 20，+4 新例、1 例改名〕、BindingChangedReplayDrainerTest @Test 6 个〔+1〕）；`git diff --numstat`：Settler +102/-13、SettlerTest +246、BindingChangedReplayDrainerTest +107、TutorJudgedReview.kt +4/-2。
- **未验证（轨内声明→§5）**：未跑 Gradle/单测（建议门禁命令 `./gradlew :core:data:testDebugUnitTest --tests "*TutorJudgedReviewSettlerTest" --tests "*BindingChangedReplayDrainerTest"` + 整包编译）；未跑设备/仪器化（读码判断 `TutorJudgedReviewSettleInstrumentedTest` 两例不受影响——后由设备跑 2/0，§3.3）；生产行为未实测（模型是否真把未分类题判词写在桶代号上，只有结构性依据）；
  **残留阻塞面（读码发现的边界，非本轨引入、按裁定字面未扩大范围）**：题**仍未归类**时若模型把证据写在真实检索候选节点上（无确认绑定时预披露给的就是这些，`RoomTutorKnowledgeContextLoader.kt:57-81`），锚定集仍只有 pseudo 桶 → 仍 NO_VERDICT；对称地，KC 集非空的题把证据写到桶上也不结算（AUD-11 ① 原口径）。建议 T9 收尾登记、另行裁定。台账/批次记录登记归 T9；自检证明范围有限（括号平衡≠编译通过）。

### T6 KD 低危包（五件）

**任务**：KD-46/47/48/49/50 + KD-17（单张草稿恢复入口）。

- **KD-46/49/50 KDoc 如实化**：KD-46（cap=**每次排计划 ≤10** 非严格每日）、KD-49（切分对账谓词只有 `confirm_state`→全表扫不加索引）、KD-50（`isCurrentProblemRevision` 只查条目当前指向不校验状态/practice unit），并把同口径的引用点（planner/port/队列函数 KDoc 与测试注释）一并改齐。
  落字位置：`core/domain/.../KnowledgeReviewFullLibraryScan.kt:5-15`、`KnowledgeReviewQueue.kt:131-133` 与 `:162-165`、`StudyExperienceRepository.kt:337-338`、`StudyReviewPlannerService.kt:546-547` 与 `:652-653`、`KnowledgePort.kt:66-69`、`KnowledgeReviewQueueTest.kt:22-25/:353-354/:450`、`RoomBackedStudyExperienceRepositoryTest.kt:433-435`；实测旧措辞已不存在（self-check 断言）。
  KD-49 事实源：`core/database/.../entity/SplitImportEntities.kt:52-56` 与 `SplitImportMigration.kt:55-68` 只有三条索引、`confirm_state` 非前导列 → KDoc 写明全表扫（`SplitImportDao.kt:98-116`）。
- **KD-47 只改枚举与 `RestoreReceipt` KDoc**：`SecondaryScreens.kt` 在飞且目标字符串位于他线新增 hunk（`git show HEAD:…SecondaryScreens.kt | grep -n 设置未能写入` 无输出；`SecondaryScreens.kt:726-737` 的 settingsNote 块是他线未提交新增），按计划回落为「改枚举 KDoc、UI 文案留后续」（`BackupRepository.kt:56-60/:76-84`）。
- **KD-48 可观察性消费面**：`KnowledgeQuizEvidenceAudit.kt` 由 internal 类改为公开类、`scan()` 收回 internal、新增公开 `scanRows()`（:67-76）与公开行 `KnowledgeQuizEvidenceAuditRow`（:90-97）；
  新增 debug-only `app/src/debug/kotlin/com/tingyun/smartmistakebook/KnowledgeEvidenceAuditActivity.kt`（消费 `scanRows`，只读、无写路径），`app/src/debug/AndroidManifest.xml:18-25` 注册（exported=true，与 `BindingAuditActivity` 同一边界）；`KnowledgeQuizEvidenceAuditTest.kt` 新增 `public audit rows keep linked unreadable and unmatched states apart`（对上/坏行/未对上三态）。
- **KD-17 读完登记与现状**：录入入口的待处理列表自 `7f109251`（2026-10-03，L4）起已包含 TUTOR 来源 EDITING 草稿并可走 resume 路由恢复（`PendingCaptureDao.kt:141` `WHERE d.status = 'EDITING'` 不限 origin → `RoomPendingCaptureStore.kt:27`/`RoomCaptureWorkflowRepository.kt:131` → `CaptureScreen.kt:913-925` → `CapturePendingCaptures.kt:66-70` → `SmartMistakeBookRoot.kt:656-657` `Routes.captureResume`），故**不再加重叠入口**，只补数据层与界面层两条回归用例钉住该事实：
  `CaptureWorkflowInstrumentedTest.kt:92-127`（真库、无模型任务、TUTOR 来源 EDITING 草稿在列且可 resume）、`CapturePendingCapturesInstrumentedTest.kt:95-134`（入口态列出并点开）。
- **证据（轨内自跑，非 Gradle）**：`python .artifacts/t6-selfcheck/check_debug_manifest.py` → `OK: debug manifest + KD-46/47/48/49/50/17 静态自检全部通过`（debug 清单 XML 解析 + 新 Activity exported/label 断言、import 并调用仓库自己的 `tools/ci/check_release_manifest.py::debug_only_component_names()` 确认能采到新组件、公开口/文案 marker/新用例名存在性、15 个改动 Kotlin 文件的括号配对启发式；脚本在 gitignore 的 `.artifacts/` 下，不进提交）。
  范围核对：`git status --porcelain` 与逐文件 `git diff` 复核，本轨 15 个 M 文件 + 1 个新文件，所有 hunk 均为本轨内容；`SecondaryScreens`/`MistakePdfPagePlanner`/`RoomBatchImportRepository` 等他线在飞文件未被本轨触碰。
- **未验证（轨内声明→§5）**：未运行任何 Gradle 任务（单测、androidTest 编译与执行、整包、debug APK/manifest 合并均未跑）；release-manifest 门未实际跑（需 assemble 后的 merged manifest；只验证其 `debug_only_component_names()` 解析会采到新 Activity）；
  KD-47 的面向用户文案仍在 `SecondaryScreens.kt:732`（「设置未能写入，本机设置保持原样。」），同缺陷的日志 `AndroidBackupRepository.kt:599` 与测试 KDoc `BackupRestoreInstrumentedTest.kt:601-602` 也未改——按计划留给后续；KD-46/47/49/50 为 KDoc/注释级改动、无行为变更，故无新增用例；device/adb 未使用（按纪律）。

### T7 6C-2 内容勾选设计件（只出文档）

**任务**：产出 `docs/research/2026-10-17-6c2-content-selection-design.md`（**972 行**，只出文档、零产品代码改动）。

- **六节齐全**：①-⑥（类别闭包、整库快照上的行级裁剪路径、恢复侧校验/回滚衔接、换机与旧包兼容、manifest v3 字段草案、待裁定项与风险）。
- **关键取舍**：闭包**不是手写**而是从 Room schema v66 的 90 条外键边推导（合法组合只有 5 组，MASTERY 一勾等于全选）；裁剪落在既有 `VACUUM INTO` 快照文件上、按拓扑序删整表 + `model_task` 家族按 kind 过滤；新增校验全部压在 swap 之前以复用既有失败分支；manifest 加 `selection` 块（categoryMask/assetPolicy/closureVersion/closureDigest/rowCounts）。
- **三条会咬人的事实（写进文档）**：裁剪后必须 `VACUUM`（否则被裁内容仍留在文件字节里，内容勾选会撒谎）、FTS 需收尾 `rebuild`、仅图片组合恢复后会被启动期孤儿回收清掉。
- **证据（轨内自跑，非 Gradle）**：读计划（`.zcode/plans/plan-2026-10-17-batch-13-6b-4c-kd51-kd44.md:44-45`）与 `docs/research/2026-10-11-new-stages-spec.md:127-142`（阶段 6C 需求/方向/验收门）；
  读 6C-1 落地件（`SmbkArchiveCodec.kt:24-83/:174-264/:809-822/:836-922`、`SmbkSettingsDocument.kt:16-47/:64-89`、`AndroidBackupRepository.kt:129-170/:234-254/:327-414/:501-538/:862-888/:780-850/:938-1034`）；
  `python .zcode/tmp/dump_schema.py` → schema v66: entities=84 views=2 FK 边=90，identityHash `f1cf3cdf14f2aabad5284d0e6e13bbd9`；
  `python .zcode/tmp/check_selection_plan.py --doc … --list-selections` → 全部通过（84 表覆盖唯一；FK 推导闭包 == 文档声明 `[ISSUE_BOOK→IMAGES, MASTERY→ISSUE_BOOK, MASTERY→SESSIONS, SESSIONS→ISSUE_BOOK, SESSIONS→IMAGES]`；trimOrder 覆盖 76 张且是合法拓扑序；软引用 38 条在 schema；model_task 分派覆盖全部 12 个 kind；合法组合只有 5 组（mask 0/8/9/13/15））；
  `python .zcode/tmp/dryrun_trim.py --doc …` → 全部通过（schema 真实 DDL 建内存库 + 一表一行种子，5 组合法组合逐表裁净、`foreign_key_check` 空、FTS 无孤儿 docid；反证：非法组合 `{ISSUE_BOOK}` 被 FOREIGN KEY constraint failed 挡下）；
  `python .zcode/tmp/probe_fts_rebuild.py` → FTS4 外部内容表 'rebuild' 在显式事务内可用、ROLLBACK 后两表恢复、rebuild 后被删行 MATCH 命中消失、孤儿 docid=0（SQLite 3.50.4）；
  `python .zcode/tmp/probe_free_pages.py` → DELETE 后哨兵文本仍能在 db 文件字节里搜到（`sentinel still present: True`）、VACUUM 后消失（False）；`PRAGMA secure_delete` 默认 0；
  读码核出四类内容对应表的关键引用口径与 `model_task` 各 kind 的 subject 宿主（`ModelTasks.kt:209-210/:277-278/:314-315`、`ProblemOrganizationTasks.kt:108-109`、`TutorLobbyTasks.kt:79-80`、`TutorTasks.kt:268-269/:487-488/:1102-1103`、`ModelEgress.kt:100-101`）；
  读 66→67 迁移不新增/不删外键边（`FavoriteAndManualDraftMigration_66_67.kt:46-48/:54-75/:78-142`——该文件当时为未跟踪在飞件，本轨只读）；轨内时点 `ls …/67.json` → No such file（待构建导出后再跑一次校验；**现已存在**，本记录者核对）；
  `git check-ignore -v .zcode/plans/…` → `.zcode/` 被 `.gitignore:44` 忽略，故自检脚本不落在提交集内——**已在附录 E 写明下一批应落到 `tools/backup/` 并接 CI**。
- **未验证（轨内声明→§5）**：未跑 Gradle/单测/仪器化/编译；文档 §2.7 提出的 JVM 用例（BackupSelection 闭包）与 instrumented 用例（哨兵不泄漏、往返、PRAGMA foreign_key_list 驱动的 FK 闭包守卫）都只是设计，尚未写成代码；
  所有 Android 设备侧事实未实测（VACUUM 耗时与空间、裁剪往返、低/高 API 上的 FTS4 rebuild 行为）；67 形态的 FK 边未用机器导出验证（67.json 现已存在，可补跑）；`IMAGE_PIPELINE_CLASSIFY` 的 `subjectIdOverride` 实际取值域、`TUTOR_EVALUATE`/`REVIEW_RERANK` 未来投产后的宿主未采样钉死；
  「外部内容 FTS 表直接 DELETE 受限」这条 SQLite 文档口径本轮未回读原文（已在文档 §2.4-3 显式标为待验证）；备份/恢复 UI 的勾选区未做到像素级设计。
- **blocked（非本轨阻塞）**：6C-2 实施前需人裁文档 §6.1 三项（§7.2 照录）。

---

## 3. 设备段（材料原文；执行者为设备走查员，本记录者未接设备）

材料声明：设备为 **emulator-5554**（adb = `C:/Android/Sdk/platform-tools/adb.exe`，命令显式钉住该设备），下述全部为设备轨实跑；独立核对（check 段）对其中若干条做了复跑更正，凡更正处均注明。

### 3.1 前置与装包（数据保留）

- 装包：`MSYS_NO_PATHCONV=1 JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot' ANDROID_SERIAL=emulator-5554 cmd.exe /c "gradlew.bat :app:installLocalFirstDebug"`
  → `Installing APK 'app-localFirst-debug.apk' on 'test_device(AVD) - 14'` / `Installed on 1 device.` / `BUILD SUCCESSFUL in 32s`；208 任务 1 执行 / 207 up-to-date（本次是安装而非重编译）。
- **数据保留已核**：`run-as` 仍见原 DB（`smart-mistake-book.db`，386,727,936 B）、2 个旧草稿（EDITING，2026-10-15 创建）、知识包戳行；真库 `user_version=67`。独立核对复跑 `dumpsys package` 得 versionName=0.2.0-localfirst、lastUpdateTime=2026-10-18 02:50:46（设备钟=UTC，与 install 日志一致）。

### 3.2 KD-51 前后对照（本批重点）

- **对照 A（快路径冷启）**：`am start -W` → `LaunchState: COLD / TotalTime 8344ms`；`Displayed … +8s344ms`；logcat `KnowledgeInstall: install finished in 1075 ms (fast path: content stamp matches, no parse, no reconcile)`；全程 **0 条 `unable to grant`**；截图 `kd51_after_fastscreen.png`。
  **独立核对更正**：`am start -W` 的原始 stdout 在全部 Temp 文件里搜 `LaunchState` 无命中 → 8344ms 属一次性计时、无产物可复核；核对员复跑同命令得 `TotalTime 3133 / WaitTime 3145`、`install finished in 428 ms (fast path…)`、`Displayed +3s133ms`、0 条 grant。
- **对照 B（强制全量安装；方法=把 `content_install_state` 的 moe-2025 戳改旧 + 改 1 个 CHEMISTRY 节点 `display_name` 迫使 upsert→整科重建；属人为放大场景，非用户路径）**：`packs fully parsed in 59487 ms`；
  设备自述 **12 条** `KnowledgeIndexRebuild` 分块重建（独立核对按行计数得 **13 条**，数值与自述逐条一致）：MATH 285.6s/449.3s（14+14 块）、PHYSICS 241.0s/419.9s（17+17）、CHEMISTRY 246.7s/359.8s（18+18）、BIOLOGY 134.1s/199.6s（10+10）、5 小科 0.1~2.3s（CHINESE 2335、ENGLISH 96、POLITICS 301、HISTORY 157、GEOGRAPHY 104 ms）；
  `install finished in 2781112 ms (full path: parse + reconcile)`；**全程 0 条 `SQLiteConnectionPool … unable to grant`**（批 12 记录改前为单条 270.20203s 等待）；期间 1 次 input-dispatch ANR，主线程栈在 Compose 文本排版（`StaticLayoutFactory.<clinit>`，非 SQLite 等待）；
  结束后戳恢复 `d2684b04f412dd0e`（独立核对：该戳与安装器自算值一致——本次冷启得到 fast path 命中），被改节点已复原（独立核对：从 APK 内提取包 JSON 与设备库比对，包内名缺失 0；DB ACTIVE 多出 3 个为另一包 `moe-2020-foundation-v1` 的名称，无可归因于本次改动的残留）。
- **锚点/索引读数**：`knowledge_search_index_state` 9 科 `index_version=1`（表无时间戳列，写入时间以各科重建日志计，锚点=最后写块：CHINESE 03:13:23 … BIOLOGY 03:58:37）；`knowledge_search_feature` 各科行数回到原值（CHEMISTRY 166366、MATH 131102、PHYSICS 148640、BIOLOGY 87004…，与各科重建日志 `features=` 逐一相同）。
- **采集保存不再被饿死（KD-51 用户可见症状面）**：相册选图 →「正在安全保存…」约 15s 内完成并进入「整理题目」（批 12 记录为卡死）。**独立核对更正**：核对员复跑该 UI 链路，新草稿在点选后约 **2s** 落库（`created_at`=设备 05:24:13）、≤17s 到「整理题目」页——只能确认「完成且远快于卡死基线」，未能复现「约 15s」的具体计时口径。

### 3.3 仪器化逐类过滤（同一构建；core:database / core:data）

命令形如 `cmd.exe /c "gradlew.bat :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<类全名>"`（core:data 同形）。结果 XML：

| 轨标注 | 类 | 结果 |
|---|---|---|
| T2 | `FavoriteAndManualDraftMigrationInstrumentedTest` | 2 tests / 0 failures |
| T4 | `KnowledgeSearchIndexRebuildChunkingInstrumentedTest` | 4/0 |
| （草稿/USER_TRANSCRIPTION 链） | `ProblemDraftDatabaseInstrumentedTest` | 17/0 |
| T5 | `TutorJudgedReviewSettleInstrumentedTest` | 2/0 |
| T3 | `CaptureCommitReuseVisibilityInstrumentedTest` | **3 tests / 1 failure** |

- **T3 确定性红（材料原文）**：`sameProblemTextFromASecondDraftReusesTheStoredProblemAndReportsIt` 在 `CaptureCommitReuseVisibilityInstrumentedTest.kt:145` 的 `problemRowCount` 抛
  `SQLiteCantOpenDatabaseException: Cannot open database '.../capture-workflow-*.db' ... doesn't exist`；`adb reboot` 后重跑同点同因（首跑 + 重跑 2/2），**判确定性红，非模拟器劣化**；独立核对复跑该类同样得 `tests=3 failures=1`（同点）。
- 材料未记录该红的归因与处置（它不在单测门 `newFailures` 里——JVM 门不含仪器化；见 §5-A、§7.5）。

### 3.4 AUD-05 恢复拒绝设备核（按序执行）

- 序列：app 内创建完整备份 → UI `备份完成：0 道题、2 张题图。`（63,467,871 B，落 `/sdcard/Download`）→ adb pull 到宿主 → 独立 python zipfile 脚本删掉 1 个被引用资产条目（`entries_before: 6 → entries_after: 5`，`manifest_still_lists_it: True`）→ push 回 → app 内恢复 → **交换前拒绝**：UI `恢复失败，已尝试保留原数据。database.sqlite 超过单条目解压大小上限`；本机数据原样（entries 0/drafts 2/user_version 67；无 `.next`/`.prev` 残件）。截图 `aud05_restore_rejected.png`、`aud05_validate_invalid.png`。
- **兼得的两条新发现（设备实测）**：
  (a) **未篡改的自建备份在 app 内「校验一个备份文件」同样报** `备份无效：database.sqlite 超过单条目解压大小上限`——`SmbkArchiveCodec.kt:113` 单条目解压上限 100 MB，而本设备库 365,133,824 B ⇒ **本设备上该备份不可校验、不可恢复**；
  (b) 备份快照 `VACUUM INTO` 单语句占写连接最长 **601.274s**、40 条 `unable to grant`（与 KD-51 同型、不同语句；KD-51 修的索引重建本身 0 条）——备份期间其它写被饿死。
- 材料注明：两条均**需批次负责线复核**（§7.10）。

### 3.5 T1/T2 走查（未达取证）

- **T2 走查（库空+模型门阻断，均留证）**：错题本 0 条；录入屏只有 拍照/相册/整卷-PDF，**无「手动/无照片建题」入口**；全仓 `MANUAL_TRANSCRIPTION` 只在 model 定义、生产代码无处设置；相册选图后「整理题目」只显示「模型已配置，还差一步能力测试」+「去完成能力测试」，无题面编辑/提交控件（`CaptureScreen.kt:195-208/832-900` 的 `candidateUsable`/`manualWorkspaceReady` 门）⇒ 提交入库不可达；收藏开关+筛选因无条目不可达。
  设备真库确认 T2 迁移落地：`user_version=67`、`error_book_entry` 有 `is_favorite`、`problem_draft.source_asset_id notnull=0`。设备轨创建的测试草稿已用「不再保留」清掉（ABANDONED），设备回到原 2 个旧草稿。
- **T1 走查**：docx 格式选择仅在 `TutorExportSheet`（`:328-341`），该 sheet 由模型 START_EXPORT 卡打开（`TutorPendingRequestCommands.kt:431`）⇒ 模型不可达时 docx 导出→保存(SAF)→分享(ACTION_SEND) 设备链**未达**；未产生设备 `.docx`，宿主 zipfile 校验未执行。

### 3.6 配额探测

- app 内「测试图片与讲解能力」→ UI 回报「暂时无法连接服务商，请检查网络后重试。」（**非 429、非成功**）。
- 模拟器 DNS 全面不可用（`ping: unknown host opencode.ai` / `www.baidu.com`；ICMP 8.8.8.8 通；guest DNS=10.0.2.3；宿主 `nslookup` 正常）；重启 emulator 进程两次（含 `-dns-server 8.8.8.8` 冷启）仍未恢复；未重试、未换凭据。
- **quotaState（材料原文）**：**未能判定**——既未取得 429 也未取得成功；批 12 记录为 429 阻断，本次因环境无法复现该读数。

### 3.7 独立核对段（check；材料截断）

独立核对员对设备自述逐条复核（claim/holds/evidence）。**材料在此段第 7 条 claim 的 evidence 中途截断**，可见 7 条全部 `holds=true`，含以下更正/补强（照录要点）：

1. 装包与数据保留 → holds；核对员独立复核（`dumpsys package`/`run-as`/`sqlite3`）。
2. 快路径冷启 → holds，但 **8344ms 无产物可复核**（属一次性计时；复跑 3133ms）。
3. 强制全量 → holds，**13 条（非 12）重建**；ANR 轨迹逐帧解析：main 段 152 条 java 帧全在 android.text/Compose 文本排版，SQLite 帧 0。
4. 节点复原 → holds（包 JSON ↔ 设备库比对：缺失 0、多出 3 个属另一包）。
5. 锚点/索引读数 → holds（表无时间戳列、feature 计数与日志逐一同值）。
6. 采集保存不再被饿死 → holds（复跑 ≈2s 落库、≤17s 到整理页；未能复现「约 15s」口径）。
7. 仪器化逐类过滤 → holds（复跑 `CaptureCommitReuseVisibilityInstrumentedTest` 得 3/1，同点）——**材料在此外截断，第 7 条之后的 claims、以及 `check.corrections` 均未记录。**

### 3.8 环境注（同批 12）

- PATH 上的 `cmd` 是 npm shim，须用 `/c/Windows/system32/cmd.exe`；`JAVA_HOME` 须换 Windows 形式，否则 gradlew 直接失败。

### 3.9 设备段未验证项（照录 device.unverified）

1. KD-37 UI 端到端（失败→重新判定→成功）、KD-38 前后对照、AUD-16 剩余（第二次复习/异常恢复/导出）：均需模型可达，模拟器网络/DNS 不可用 → **未达**。
2. T1 docx 设备链（导出→保存(SAF)→分享(ACTION_SEND)）与宿主 python zipfile 校验：格式选择只存在于模型 START_EXPORT 打开的 sheet → 模型不可达，设备未产生 `.docx`，宿主核对未执行。
3. T2 收藏开关+筛选 UI 走查与手动建题全链（提交→入库可见）：库 0 条 + app 内造题被模型能力门挡住 → **未达**；仅 instrumented（收藏筛选 2/0〔迁移类〕、草稿链 17/0）覆盖。
4. AUD-05 的「缺引用资产」专用门（manifest.referencedAssets / 归档库反查闭合）未在设备上被触发——恢复在更早的「单条目解压上限」门就被拒（结果仍是交换前拒绝）；另：自建备份在本设备上不可校验/不可恢复（100MB 单条目上限 vs 365MB 库），需批次负责线复核。
5. 采集「提交入库」未在设备上完成（模型门）；仅保存阶段（「正在安全保存…」→「整理题目」）实测通过。
6. KD-51 的「改前 270.2s」取自批 12/known-defects 记录，本次未重测改前基线；本次改后对照是在人为构造的强制全量安装下取得（0 条连接饥饿），与真实升级场景的差异如实标注。

---

## 4. 门禁与跑测口径

**口径**：本批门禁分两段——① 单测门（七模块，材料 `unitGateExit=0`、`unitGateRounds=2`）；② 强门禁（androidTest 编译三模块 + 整包编译，`androidTestCompileExit=0`、`assembleExit=0`）。
两段均由脚本串行执行 + 门禁归因员归因；**本记录者未跑 Gradle**，命令原文读自运行脚本（只读核对），结果数字照录材料。

### 4.1 单测门（七模块；3 次执行后收敛 exit=0）

- **命令原文（读自脚本 `runUnitGate`，与门禁执笔所引一致）**：
  `cmd /c gradlew.bat :core:model:test :core:domain:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:export:testDebugUnitTest :feature:review:testDebugUnitTest :feature:library:testDebugUnitTest --continue`（timeout 3600000）。
- **轮次口径**：材料字段 `unitGateRounds=2`；脚本赋值 `unitGateRounds = gateRound + finalRound`（修复环轮数）→ 单测共执行 **3 次**（首跑 + 2 轮修复）；执笔证据写「**第 3 轮**用的仍是…命令」，脚本转述 exit=0、stderr 尾部为空。
- **逐模块 XML 聚合（门禁执笔；python 扫 `build/test-results/*/*.xml` 的 tests/skipped/failures/errors + mtime）**：

| 模块 | 文件/用例/失败 | 产物时间（执笔） |
|---|---|---|
| core:database | 43 文件 / 196 用例 / 0 失败 | 全部 10:41:03 重写 |
| feature:library | 8 / 41 / 0 | 全部 10:41:04 重写 |
| core:model | 42 / 441 / 0 | 00:25（UP-TO-DATE） |
| core:domain | 75 / 578 / 0 | 00:25（UP-TO-DATE） |
| core:data | 107 / 840 / 0 | 01:28（UP-TO-DATE） |
| core:export | 12 / 100 / 0 | 01:26（UP-TO-DATE） |
| feature:review | 7 / 48 / 0 | 00:25（UP-TO-DATE） |

- **「UP-TO-DATE 不掩盖失败」的执笔论证（照录要点）**：扫七个模块源码树里晚于 01:30 的文件，只命中上述 5 个（全在重跑的两个模块内），`core/database/src/main` 无任何晚于 01:30 的改动 → 依赖方 classpath 未变，且 exit=0 不存在「依赖失败而跳过」的任务，故五个模块的绿结果有效（非靠跳过掩盖）。
- **上两轮红点的转绿逐个点名（照录）**：① `KernelWave0SchemaContractTest` 新 10:41:03，tests=7 failures=0（含 `the study database version matches the latest exported schema`）；② `LibraryFtsCountPathQueryCopyContractTest` 新 10:41:01，tests=4 failures=0（含 `crossJoinSqlCopiesMatchTheDaoQueries` 与 scale50k 两例）；③ `LibraryFtsRankingQueryContractTest` 新 10:41:01，tests=3 failures=0（`rankingAndFilterLiteralsMatchTheFrozenReferenceCopy` 在位、`<failure` 计数 0）；整个 core:database 模块扫 `<failure` 得空列表；④ `feature/library LibraryCatalogTest` 新 10:41:04，tests=13 failures=0（该模块上轮 40 例的旧结果停在 10-17 21:04，本轮 41 例才把 00:14 改的测试文件真正跑上）。
- **修复落点核对（门禁执笔）**：`feature/library/.../LibraryViewModel.kt:8-9` 已是两行独立 import（粘连修复）；`KernelWave0SchemaContractTest.kt:50` 字面量已是 67；androidTest 两份冻结副本已含 `is_favorite = 1`（`LibraryFtsCountPathInstrumentedTest.kt` 4 处、`LibraryFtsRankingPathInstrumentedTest.kt` 1 处）。
  **本记录者只读核对**：上述三处与 mtime（`LibraryViewModel.kt` 01:45、`KernelWave0SchemaContractTest.kt` 10:07、两份副本 10:28/10:35/10:38）与执笔一致。
- **判红口径（脚本 `KNOWN_REDS` 照录）**：开工基线=批次 12 收尾后 core:model/core:data/core:database 三模块实测 exit 0，故此后出现的失败默认按新增红处理；三类例外可判既有红：
  ① core:data 的 GoldenRetrieval 系列（KB 线在飞）；② core:database 的 SECTION facet 边际门（环境红）；③ 失败可归因到他线 35 个在飞未提交文件（BatchImportRoute/MistakeDetailRoute/RoomBatchImportRepository/OpenAiModelProtocol/TutorSessionPanel/MistakePdfPagePlanner 等）。判既有红必须在证据里写明它与本批 diff 无关的理由。
- **门禁判定字段（照录）**：`knownRedsSeen=[]`、`newFailures=[]`、`onlyKnownReds=true`（单测门与强门禁同）。

### 4.2 强门禁（两段；exit=0）

- **命令原文（读自脚本 world-read#2/#3）**：
  - `cmd /c gradlew.bat :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin :feature:library:compileDebugAndroidTestKotlin` → **exit=0**（stderr 尾部为空）；
  - `cmd /c gradlew.bat :app:assembleLocalFirstDebug` → **exit=0**（stderr 尾部为空）。
- **落盘佐证（执笔；本记录者复核一致）**：三个模块 androidTest 编译产物新鲜（`classes` 10:42:37–10:42:45、`classes.dex` 10:43），`app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` = **2026-10-18 10:43:17**（其余 APK 均为 10-17 21:0x 及更早）。
- **执笔声明**：未亲跑 Gradle；结论建立在脚本 exit code + 空 stderr 尾部 + 磁盘产物时间戳；**这两道门只证明「能编译/能打包」，不代表 androidTest 用例已在设备上跑过**（那是设备轨的事）。

### 4.3 既有红点名

- **本轮**：**无**（单测门与强门禁 `knownRedsSeen=[]`、`newFailures=[]`）；GoldRetrieval 系列（①）与 SECTION facet 边际门（②）都不在这两道门范围内或本轮未触发；③他线在飞可归因失败本轮无。
- **设备面另有 1 条红**（不在门禁范围）：`CaptureCommitReuseVisibilityInstrumentedTest` 3/1（§3.3）——归因/处置材料未记录，提交前需点名（修或登记）。

### 4.4 本批未跑（口径如实）与核对时点磁盘现状

- 除单测门七模块与强门禁两段外，本批**未跑**：整包 release、`tools/tests` 全量 Python（T1/T2/T4/T6/T7 只跑各自单脚本）；仪器化只在设备上按类过滤跑了 5 个类（§3.3），其余新仪器化（T1 `MistakeExportHubInstrumentedTest`、T3 卡片类、T6 两条回归、`ProblemDraftEditWorkspaceDatabaseInstrumentedTest`）材料未记录其执行结果。
- **本记录者只读核对（2026-10-18 15:58 前后）——test-results 已被更晚运行覆盖**：现盘 `core:database` 仅 1 文件 / 4 例、`core:data` 2 文件 / 30 例、`core:model` 1 文件 / 5 例（最新 mtime 15:31–15:36），`core:export` 12 文件 / 100 例（15:35）；
  从文件名看是**过滤运行**（`FavoriteAndManualDraftSchemaContractTest`、`BindingChangedReplayDrainerTest` + `TutorJudgedReviewSettlerTest`、`ManualTranscriptionEvidenceExemptionTest`），全部 0 失败。
  这些运行**不在材料里、发起者未记录**；材料 §4.1 的逐模块聚合（43 文件/196 例等）在当前磁盘上**无法复现**（`feature:library` 8/41 与 `core:domain` 75/578 两处仍与材料一致）。

---

## 5. 未验证项总表

### A. 仍成立（至材料末仍未验证 / 未达）

1. **T1**：零 Gradle（七模块单测、androidTest 编译、整包均未由本轨跑）；设备 docx 链未达（模型不可达，§3.5）；「Target 修复后 Word 真能打开」未经 Word/设备验证；`word/media` JPEG 端到端未单测；python-docx 只打开过脚本自产样本；手工 kotlinc 被安全 hook 拒绝。
2. **T2（blocked）**：A8 **创建入口链未交付**（录入界面手动建题入口、`importDraft`/`initialDraftCommand` 空源分支、`confirmAndCommit` 空 resolve 分支、tutor 完整空源会话、`ResumableCaptureDraft` 空源驼生）；手动题全链 instrumented（`created=true` 且 `provenance=USER_TRANSCRIPTION`）未写未跑；「无照片草稿在既有下游（导出/详情）不崩」未验；非分页替身路径无收藏数据源（开关只能提示不支持）；详情页收藏开关与复习/组卷按收藏筛选未做。
3. **T2**：67.json 在材料写作时未产出（**现已由构建产出**，见 B；提交须纳入）。
4. **T3**：所有 Gradle 构建与测试未由本轨跑（后由设备跑，见 B 但带 1 红）；卡片未真机渲染；docs/台账未改（归 T9）；`created` 两读法已钉住仍待复核（任务书字面 vs 既有语义）。
5. **T4**：重建实测占比未测（等设备读数）；写饥饿解除的设备口径为**人为构造的强制全量**；`withReadTransaction` 内部行为未核实；`RoomKnowledgeBaseStore.kt:267-275` 读路径自愈的「只补缺」分支仍是单写事务插全科特征行（同形态剩余饿死面，未纳入本轨）。
6. **T5**：零 Gradle（轨内）；生产行为（模型是否真把未分类题判词写在桶代号上）只有结构性依据；**残留阻塞面**（真实检索候选节点上的证据仍 NO_VERDICT；KC 集非空题写桶不结算）待 T9 登记另裁；零 bump 待折入台账；自检证明范围有限。
7. **T6**：release-manifest 门未实际跑（只验证其解析函数采到新组件）；KD-47 用户文案（`SecondaryScreens.kt:732`）、日志（`AndroidBackupRepository.kt:599`）、测试 KDoc（`BackupRestoreInstrumentedTest.kt:601-602`）未改；KD-46/47/49/50 为 KDoc 级、无新增用例；T6 两条新回归（`CaptureWorkflowInstrumentedTest`/`CapturePendingCapturesInstrumentedTest`）的设备执行结果材料未点名 → 未记录；device/adb 未由本轨使用。
8. **T7**：设计件未实施；所有 Android 设备侧事实未实测；67 形态 FK 边未用机器导出验证（现已可补跑）；`subjectIdOverride` 取值域与 TUTOR_EVALUATE/REVIEW_RERANK 宿主未采样；「外部内容 FTS 表直接 DELETE 受限」未回读 SQLite 原文；UI 勾选区未到像素级。
9. **设备段**：模型轨全部未达（DNS 不可用，quotaState=未能判定）；T1/T2 设备走查未达（§3.5）；AUD-05 专用门未触发（更早的门就拒了）；采集「提交入库」未在设备完成；KD-51 改前基线未重测。
10. **设备红**：`CaptureCommitReuseVisibilityInstrumentedTest` 3/1（`CaptureCommitReuseVisibilityInstrumentedTest.kt:145`，重启后同点同因）——归因与处置材料未记录。
11. **门禁口径**：单测门 3 次执行的逐轮失败清单未随材料附上（只给了「上两轮红点转绿」点名）；强门禁只证「能编译/能打包」；当前磁盘 test-results 已被更晚运行覆盖（§4.4），材料聚合数不可磁盘复现。
12. **材料截断**：`check` 段第 7 条 claim 之后未记录；`review`（对抗性复核 findings）整段未附；`check.corrections` 未附 → 对抗性复核的 findings 与更正清单未记录。
13. **AUD-05 两条新发现**：100MB 单条目上限 vs 365MB 库（自建备份不可校验/不可恢复）；`VACUUM INTO` 601.274s 持写连接 + 40 条 `unable to grant`——均需批次负责线复核（§7.10）。

### B. 已被批次后段证据覆盖（轨内写作时点「未跑/未产出」，后由门禁/设备段实跑）

1. T2 迁移/收藏/空源仪器化 → 设备 **2/0**（§3.3）。
2. T4 分块重建仪器化 → 设备 **4/0**（§3.3）；KD-51 设备对照（强制全量）**0 条 `unable to grant`**（§3.2）。
3. T5 结算仪器化 → 设备 **2/0**（§3.3）。
4. T3 复用可见性仪器化 → 设备 **3/1**（确定性红，见 A.10）。
5. T2 的 67.json → **已产出**（本记录者核对：2026-10-18 00:25:55、348,529 B、version 67、identityHash `9bf16ae8f2e162fac01cc0e0c111b9a8`，含 `is_favorite`/可空源列/视图列；`??` 未跟踪，提交须纳入）。
6. 门禁面：七模块单测 exit=0（3 次执行、2 轮修复）、androidTest 编译三模块 exit=0、整包 `assembleLocalFirstDebug` exit=0（材料 + 脚本执笔；本记录者核对落盘时间戳一致）。
7. T1 的轨内自检（verify_mistake_docx.py --selfcheck、独立校验脚本、`git diff --check`）已由轨内实跑（材料）。
8. 设备段 KD-51 对照 A/B、锚点读数、采集保存、AUD-05、配额探测、T1/T2 走查均由设备轨实跑并留证（§3）。
9. **核对时点观察（材料未记录）**：2026-10-18 15:31–15:36 有过滤重跑落盘（core:export 12/100、Settler+BindingChanged 2 文件/30 例、`FavoriteAndManualDraftSchemaContractTest` 4 例、`ManualTranscriptionEvidenceExemptionTest` 5 例），全部 0 失败；发起者未记录，仅作磁盘事实登记。

---

## 6. 共享树说明（提交者必读）

### 6.1 本线文件清单（七轨 `files` 并集去重，共 **111** 个路径）

本记录者只读核对（2026-10-18 15:58 前后）：111 个路径在共享工作树**全部存在**；其中 3 个文件被多轨共同列出（§6.2）；`.artifacts/` 下 2 个脚本被 `.gitignore:3` 忽略（**不进提交**）；`tools/export/verify_mistake_docx.py` 位于未跟踪目录 `tools/export/`（提交须 `git add` 整个目录）。逐轨归属见 §1/§2。

```
.artifacts/kd51-t4/check_kd51_static_smoke.py
.artifacts/kd51-t4/verify_chunked_delete_equivalence.py
app/src/debug/AndroidManifest.xml
app/src/debug/kotlin/com/tingyun/smartmistakebook/KnowledgeEvidenceAuditActivity.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/ExportPdfWorker.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/MistakeExportNotifications.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt
app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt
app/src/test/kotlin/com/tingyun/smartmistakebook/ExportPdfWorkerTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureCommitReuseVisibilityInstrumentedTest.kt
core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/capture/CaptureWorkflowInstrumentedTest.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/RoomCaptureWorkflowRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/library/RoomLibraryCatalogRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeDetailRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAudit.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepository.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt
core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettler.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/BindingChangedReplayDrainerTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizEvidenceAuditTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepositoryTest.kt
core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorJudgedReviewSettlerTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/FavoriteAndManualDraftMigrationInstrumentedTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexRebuildChunkingInstrumentedTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/ProblemDraftDatabaseInstrumentedTest.kt
core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/ProblemDraftEditWorkspaceDatabaseInstrumentedTest.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/FavoriteAndManualDraftMigration_66_67.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/ProblemDraftCommitOutcome.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomLibrarySearchStore.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomPendingCaptureStore.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabaseRecords.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/LibraryFtsSearchDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/LibraryQueryDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/MistakeDetailDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemDraftTransactionDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/SplitImportDao.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/CaptureEntities.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/LibraryCatalogView.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/ProblemEntities.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/DraftPort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/LibraryPort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/MistakePort.kt
core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/TutorPort.kt
core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/FavoriteAndManualDraftSchemaContractTest.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowStateMachine.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewFullLibraryScan.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueue.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/LibraryCatalogRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeDetailRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/StudyExperienceRepository.kt
core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowStateMachineTest.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueueTest.kt
core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/SaveTutorDraftToLibraryUseCaseTest.kt
core/export/src/main/AndroidManifest.xml
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxImageSource.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRenderer.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MinimalDocxWriter.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxDelivery.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxExporter.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeDocxFileProvider.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportFormatRouting.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunner.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExportCopy.kt
core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfExporter.kt
core/export/src/main/res/xml/mistake_docx_export_paths.xml
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DocxImageSourceTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/DocxMistakeRendererTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportFormatTest.kt
core/export/src/test/kotlin/com/tingyun/smartmistakebook/core/export/MistakeExportJobRunnerTest.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/CapturedQuestionDocument.kt
core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MistakeExportFormat.kt
core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/ManualTranscriptionEvidenceExemptionTest.kt
docs/research/2026-10-17-6c2-content-selection-design.md
feature/capture/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureCommittedReceiptCardInstrumentedTest.kt
feature/capture/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/capture/CapturePendingCapturesInstrumentedTest.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureDraftStateCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureResumeCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureScreen.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureScreenPolicy.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureScreenState.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureViewModel.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/CaptureWorkflowEventCommands.kt
feature/capture/src/main/java/com/tingyun/smartmistakebook/feature/capture/components/CaptureScreenComponents.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureCommittedReceiptTest.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureDraftStateCommandsTest.kt
feature/capture/src/test/kotlin/com/tingyun/smartmistakebook/feature/capture/CaptureViewModelTest.kt
feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRepositoryInstrumentedTest.kt
feature/library/src/androidTest/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubInstrumentedTest.kt
feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryModels.kt
feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryRoute.kt
feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryViewModel.kt
feature/library/src/main/kotlin/com/tingyun/smartmistakebook/feature/library/MistakeExportHubRoute.kt
feature/library/src/test/kotlin/com/tingyun/smartmistakebook/feature/library/LibraryCatalogTest.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorConversationViewModel.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheet.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorLobbyRoute.kt
feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorRoute.kt
feature/tutor/src/test/java/com/tingyun/smartmistakebook/feature/tutor/TutorCapturedDraftLandingTest.kt
feature/tutor/src/test/java/com/tingyun/smartmistakebook/feature/tutor/TutorExportSheetTest.kt
feature/tutor/src/test/kotlin/com/tingyun/smartmistakebook/feature/tutor/TutorSessionViewModelTest.kt
tools/export/verify_mistake_docx.py
```

（顶层计：core 73、feature 27、app 7、docs 1、tools 1、.artifacts 2。本记录者核对的工作树状态：其中 85 个 `M`、24 个未跟踪（`??`，含 `tools/export/` 整个目录）、2 个被 gitignore。）

### 6.2 同批内多轨共编文件（评审/提交勿按单轨 hunk 挑拣）

- `core/database/.../RoomStudyDatabase.kt`：**T2/T3**（迁移注册与回执 override 等处）。
- `core/database/.../dao/ProblemDraftTransactionDao.kt`：**T2/T3/T6** 三轨（T2 空源/校验器链、T3 三条提交口回传、T6 KD-50 KDoc）。
- `core/data/src/test/.../study/RoomBackedStudyExperienceRepositoryTest.kt`：**T2/T3/T6** 三轨。
- `core/database/.../StudyDatabaseRecords.kt`：T2 改动；T3 明确**未触碰**（新记录内嵌 `CommitProblemDraftResult` 而非改它）。
- `core/data/.../study/StudyReviewPlannerService.kt`：T6 改动（KD-46 KDoc）；T5 只读核对其 "+4/-2" 不含结算路径。
- `core/data/.../study/RoomTutorToolRunner.kt`、`RoomBackedStudyExperienceRepository.kt`：T3 改动（B5）；T5 只读交叉核对未碰。
- `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt`：T1 只有导出接线两个 hunk（`:403-404`、`:475-476`）；同文件其余 hunk 属他线（databaseRestart/BatchImport/LearningMastery/StorageScreen）。
- 另：T7 材料把 `FavoriteAndManualDraftMigration_66_67.kt` 描述为「他线在飞（untracked）、本轨只读」——**该文件实为本批 T2 的文件**（同批共享树中当时未提交），提交归属按 T2 计，勿当外部他线处理。

### 6.3 他线在飞（不属于本批九轨，勿混提 / 勿覆盖）

- **35 个他线在飞文件**（计划 §2 与脚本 `KNOWN_REDS` 点名：BatchImportRoute / MistakeDetailRoute / RoomBatchImportRepository / OpenAiModelProtocol / TutorSessionPanel / MistakePdfPagePlanner 等）。
- 本批**避开**其热区：不碰 `MistakePdfPagePlanner.kt`（他线 hunk 在 ChoiceGroup 分支 `:178-191`）；C11 详情开关留下一批（避开 `MistakeDetailRoute` 备注卡 `:431-505`）；A8/6B 对 `SmartMistakeBookRoot.kt` 的编辑限在导出/采集接线区（他线 hunk 在 `:161`、`:591-627`、`:693`、`:866-948`）——若需整份提交该文件，按 `463a5dea` 先例注明含他线小区段。
- T6 核实未触碰 `SecondaryScreens.kt`/`MistakePdfPagePlanner`/`RoomBatchImportRepository` 等他线在飞文件；T2 的 blocked 亦因 `MistakeDetailRoute` 在飞而把详情开关留后。
- 工作树另有大量未跟踪产物与工具改动（`tmp/`、`tools/kb_*` 等），本批未触碰、未记录归属。

### 6.4 提交口径要点（各轨/材料声明汇总）

- **67.json 必须纳入提交**：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/67.json`（构建产物、当前 `??`；契约测试在它缺失时直接报错并指明这一点）。
- **`.artifacts/kd51-t4/*.py` 不进提交**（`.gitignore:3`）；T6 自检脚本同（`.artifacts/t6-selfcheck/`）。
- **`tools/export/verify_mistake_docx.py` 是 T1 的新工具文件**（未跟踪目录 `tools/export/`，提交须显式 `git add`）。
- **T2 的 `tmp/t2_*.py` 自检脚本可重跑但不建议纳入**（`tmp/` 未跟踪；材料未声明提交口径 → 未记录）。
- **T5**：零 bump 依据与「pseudo 结算属语义变更」需按批 12 T8 先例折入 `docs/research/algorithm-version-ledger.md`（归 T9）。
- **T6**：KD-46~50 与 KD-17 的关账（改缺陷条目状态）归 T9；本轨未改 `docs/known-defects.md`。
- **T3/T5/T6 的 docs 改动均为零**（各自声明）；本批产出的 docs 文件只有 T7 的设计件（`docs/research/2026-10-17-6c2-content-selection-design.md`）与本记录文件。

### 6.5 提交状态（本记录者只读核对，2026-10-18 15:58 前后）

- HEAD=`07382e23`（批次 12 收尾提交）；暂存区 0 条；`git status --porcelain` **588** 条（含他线改动与未跟踪产物）。
- 本记录者未见本批九轨的任何提交；九轨改动均在共享工作树中（含未跟踪的新文件）。

### 6.6 本记录者抽查记录（行号漂移与现状，供提交者对照）

- 逐字符合：`MinimalDocxWriter.kt:111-127`（`Target="$DOCUMENT_PART"`）、`DocxMistakeRenderer.kt:115-144`（内容高封顶）、`StudyDatabase.kt:130`（=67；HEAD 为 66）、`KernelWave0SchemaContractTest.kt:50`（字面量 67）、`LibraryViewModel.kt:8-9`（两行独立 import）、facet 计数 6/4、`TutorJudgedReviewSettler.kt` 的 `pseudoAnchor` 区（:351/:447/:480）、`app/src/debug/AndroidManifest.xml:18-25`（KD-48 Activity 注册）、`SplitImportDao.kt` KD-49 KDoc、`git grep deleteSearchFeaturesForSubject` 无命中。
- **行号漂移 2 处**（内容均在，行号以工作树为准）：`StudyDatabase.kt` 末位注册材料记 `:383`、实测 `:384`；`ProblemOrganizationDao.kt` 新增读口材料记 `:370-384`、实测查询在 `:388`（KDoc 起于 370 一带）。材料写作后工作树仍有编辑活动，引用行号时以本核对为准更稳。

---

## 7. 待裁定 / 建议登记

1. **T2 请裁定（本轨 blocked 原文）**：A8 创建链未交付——「下一轨接 A8 创建链（带编译器迭代），或按现有门禁反馈先修本轨」。
2. **T7 6C-2 实施前三项待人裁**（文档 §6.1）：①「仅图片」组合是否在 UI 提供（建议不提供：恢复后会被启动期孤儿回收清掉）；②空选择是否允许（建议 UI 禁止）；③派生索引 `knowledge_search_feature`/`index_state` 是否恒裁（建议恒裁 + 恢复后后台重建，备选：全选时保留）。
3. **T5 零 bump 与语义变更登记**：按批 12 T8 先例折入台账；pseudo 上开始出现判定权重属**语义变更**（按裁定实施），需一并登记。
4. **T5 残留阻塞面登记另裁**：未归类题的真实检索候选节点证据仍 NO_VERDICT；KC 集非空题把证据写到桶上也不结算（AUD-11 ① 原口径）——建议 T9 收尾登记。
5. **设备红处置**：`CaptureCommitReuseVisibilityInstrumentedTest` 3/1（`CaptureCommitReuseVisibilityInstrumentedTest.kt:145`，重启后同点同因）——材料未归因、未处置；提交前需点名（修或登记）。**建议优先排查**：同类链路（草稿库文件被提前关闭/删除）可能影响同批其它 capture 仪器化。
6. **T6 KD-47 剩余面**：`SecondaryScreens.kt:732` 的 UI 文案、`AndroidBackupRepository.kt:599` 的日志、`BackupRestoreInstrumentedTest.kt:601-602` 的测试 KDoc 未改（按计划留后续）。
7. **T4 剩余饿死面**：`RoomKnowledgeBaseStore.kt:267-275` 的读路径自愈「只补缺」分支仍是单写事务插全科特征行（同形态），如实登记未纳入。
8. **T3 去重口径复核**：`created` 的两种读法（同一份草稿重放 vs 换草稿提交同一题面）已在新用例与 KDoc 钉住；`reusedExistingProblem` 与 `created` 的组合语义请复核点收。
9. **KD 关账状态**：按计划 T9 负责 KD-17/46~50 关账与 KD-44 登记；**本记录者核对时点** `docs/known-defects.md` 里 KD-17、KD-44、KD-46~51 仍全部标 `open`（KD-45 已 fixed）——关账动作未见落盘。
10. **AUD-05 两条新发现需批次负责线复核**（材料原文）：① 100MB 单条目上限 vs 本设备 365MB 库 → 自建备份不可校验、不可恢复；② `VACUUM INTO` 单语句 601.274s 持写连接 + 40 条 `unable to grant` → 备份期间其它写饿死。（二者同属恢复/备份栈，可能需要新 KD 或并入既有条目。）
11. **材料截断补齐**：`check` 段第 7 条 claim 之后与 `review`（对抗性复核 findings）整段未随材料附上——如要评估复核发现，需向运行方索取完整材料（或读运行台账）。
12. **T1 设备链未达**：docx 保存(SAF)/分享(ACTION_SEND) 与宿主 zipfile 校验未执行（模型不可达）；「Word 真能打开」仍待设备/人工验证。
13. **T6 回归用例执行结果未记录**：`CaptureWorkflowInstrumentedTest`（KD-17 数据层）与 `CapturePendingCapturesInstrumentedTest`（入口层）的设备执行结果材料未点名——建议设备轨补跑或明确记为未达。

---

## 8. 收尾补记（主会话，2026-10-18）

**提交（2 笔，已推送至 `5162a2ff`）**：
- `8335613f` —— 七轨全部（记录 §6.1 的 111 路径剔除 `.artifacts/` 两脚本 + 补入 `67.json`，共 **110 文件**）。
  `SmartMistakeBookRoot.kt` 按 §6.3 整份纳入（T1 导出接线两 hunk + 他线小区段，先例 `463a5dea`）。
- `5162a2ff` —— docs（本收尾补记 + KD 登记 + 主线/台账 §3.23/决策台账折入；主线与决策台账另载他线同日
  **4E 统一生图与 JS 后端**路线修订段——整份文件纳入，如实注明）。
- **推送结果（2026-10-18）**：`07382e23..5162a2ff`（fast-forward，无分叉）；`git ls-remote` 直查远端 = `5162a2ff`。

**收尾修复（主会话，均已过门）**：
- **T3 仪器化确定性红的根因**（§3.3）：不是产品面——`CaptureCommitReuseVisibilityInstrumentedTest` 第一条断言先碰
  裸连接，而 **Room 惰性打开**、库文件尚未落盘 → `SQLiteCantOpenDatabaseException`。修法：先走一次读口
  （`database.countMistakes()`）让库落盘再数 problem 行。**设备复验 3/3 绿**（跑在 `emulator-5556` /
  AVD `audit_user_visible_20261016`——本批设备段的 5554 已不在线，如实标注设备不同）。
- **T3 重放路径回执文案**（对抗性复核 finding 11）：原句「错题本里已有同一道题，这次直接复用了它」对
  「学生自己重试回放」是误报（题其实是刚建出来的）。改句只声称两种情形都为真的事实：
  「没有重复建题：同一题面的题在错题本里只留一条，这次复用了它。」（`CaptureScreenPolicy.kt:88-101`）。
- **docx CRLF**（finding 4）：`MinimalDocxWriter` 段落文本先 `\r\n`/`\r` 归一成 `\n` 再切行；新用例
  `carriage returns become real line breaks instead of leaking into text runs`（`core:export` 13/13 绿，
  含 XML 无 CR 与两处 `w:br` 断言）。
- **worker 坏 format 不重试**（finding 6）：`ExportPdfWorker` 解码失败改走新 `giveUpNow`（直落 FAILED + 通知，
  不再 3 次重试；`giveUpOrRetry` 抽出共用尾段）。`app` 单测 + `:core:data:compileDebugAndroidTestKotlin` 绿。

**登记（主会话）**：**关闭** KD-17（事实核清：入口自 L4 起已存在，补两条回归钉住）/ KD-44（pseudo 自动结算，
零用户参与）/ KD-46 / KD-48 / KD-49 / KD-50 / KD-51（含 indexed>=reviewed 补记）；**部分落地** KD-47
（枚举/回执 KDoc 已如实化，UI 文案在他线在飞文件内留后续）；**新增** KD-52（A8 只有 schema 半边、无生产者，
medium；AUD-28）/ KD-53（100MB 单条目上限 vs 365MB 库 → 自建备份不可校验/恢复，medium；AUD-29）/
KD-54（`VACUUM INTO` 601s 饿死写，medium；AUD-30）/ KD-55（docx 图片 TOCTOU，low）。

**§7 未决项处置**：item 8 点收（T3 口径钉住 + 文案已改）；item 9 关账已做（本补记）；item 10 两条新发现
已登记 KD-53/54；item 12（T1 docx 设备链）保持未达；item 13 已补跑：`CaptureWorkflowInstrumentedTest`
（core:data，KD-17 数据层）**17/0**、`CapturePendingCapturesInstrumentedTest`（feature:capture，入口层）
**6/0**（设备 emulator-5556，2026-10-18 主会话实跑）。

**仍未做 / 未达（照录）**：设备模型轨（KD-37/38 复验、AUD-16 剩余）——模拟器 DNS 全面不可用（非 429、
非成功；重启两次含 `-dns-server` 未恢复）；T1 docx 设备链（保存/分享/宿主 zipfile 校验）与 T2 收藏/手动建题
走查未达（库空 + 模型门；「手动建题」入口不存在 = KD-52）；A8 全链提交验收未做（KD-52）；6B 编辑半边与
自然语言改卷（6B-2/3）；6C-2 只出设计件（实施前需人裁设计件 §6.1 三项）。
