# 阶段 3C 后半 · 规模项（S17 / S9 / S18 / S20）完成记录（2026-10-05/06）

> 依据：`docs/research/2026-10-05-stage3c-part2-plan.md`（实施计划；工作树未跟踪，见 §5.9）、
> 量化报告 `docs/research/2026-10-05-s9-s18-quantification-report.md`（随批 2 提交）、
> 前置量化 `docs/research/2026-10-05-s18-count-path-prefinding.md`（随批 1 提交）、
> 前半记录 `docs/research/2026-10-04-stage3c-part1-completion-record.md`、主线 3C 行。
> **范围**：S17 性能量化（批 1）→ S9/S18 量化与按需物化（批 2）→ S20 快照合并 + 后继映射缓存（批 3）；
> 综合门 + R8 冒烟（批 4）。**KF-31 未启**（前置未变，见 §5.1）；**3D 硬门仍挡阶段 5**（见 §5.2，不在本批）。
> **纪律**：量化先行，物化/索引只在实测超预算时做（AGENTS §12.2）；每批「实施 → 正式门（world.run）→
> 独立只读复核 → 修复轮 → 显式清单提交」。R8 冒烟与综合门数字来自终门执行者的运行回报（本记录撰写者未复跑）。

## 1. 交付与提交

| 批 | 提交 | 内容 |
|---|---|---|
| 计划 | 工作树未跟踪（`git ls-files` 为空） | 3C 后半实施计划：批设计（S17 / S9+S18 / S20 / 综合门）、版本与迁移预算（schema 63 条件触发）、纪律与风险 |
| 批 1 | `8d7e24b2` | **S17 性能量化**：`PerformanceGateTest.insertTestData` 由 placeholder 改为真实 1 万行批量夹具（problems / revisions / practice_units / error_book_entries 各 10,000）；修掉第二个空转点（旧 `ftsSearchCount` 直测绕过 `refreshProjection` → FTS 索引从未建立，实测 content=0/indexed=0）；按 2026-10-05 裁决 A，搜索/中文门改测**用户可见** `searchPage` 路径（口径迁移，见 `2026-10-05-s18-count-path-prefinding.md` 头部），首屏/facet 门在真实数据上通过并加非空信号；count 路径数字与 EXPLAIN 原文移交批 2（S18）。新增 S17 专属门：`observeActiveMistakes` 5000 行夹具 + EQP 结构断言（SEARCH 走索引 / 无 `SCAN TABLE problems` / 无整段 `TEMP B-TREE FOR ORDER BY` / 无 `CORRELATED SCALAR SUBQUERY`）+ 计时 backstop（p95=49ms < 1500ms×CI）；退化-红 / 还原-绿已实证 |
| 批 2 | `d78815c3` | **S9 + S18 量化，按需物化**：S9 聚焦 `MASTERY_READ` 负载门（2k 负向作答 × attributions，EQP + p95）**未超预算 → 未物化、未加索引**；S18 5 万行目录夹具补齐 mastery/分类/投影（消除退化路径）→ 实测 count/facet **超预算 → schema 63**（`learner_problem_memory_state(projection_name, practice_unit_id, learner_id)` 复合索引 + 非破坏迁移 `62→63`），**未做 mastery/标签物化**（根因是连接索引，非相关子查询重算）；量化报告 `2026-10-05-s9-s18-quantification-report.md` 逐条留档数字与 EXPLAIN 原文 |
| 批 3 | `85e6b6ae` | **S20 快照合并 + 后继映射缓存**（零 schema）：三条重建型观察路径 `distinctUntilChanged().conflate()`（`StudyExperienceObservationJobs.kt:87`），**写路径显式 publish 未动**；`KnowledgeNodeSuccessorsCache` 后继映射缓存 + `knowledge_node` 表级失效（Room 探针 → 仓库订阅，`RoomBackedStudyExperienceRepository.kt:323`）；P0 修复：缓存失效世代号守卫（`KnowledgeNodeSuccessorsCache.kt:42-43,58-63,72-88`） |

批 1 文件：`core/database/src/androidTest/.../PerformanceGateTest.kt`、
`.../ProblemDaoActiveMistakesPerformanceInstrumentedTest.kt`、`docs/research/2026-10-05-s18-count-path-prefinding.md`。
批 2 文件：`schemas/.../63.json`、`LibraryCatalogMemoryIndexMigration_62_63.kt`、`StudyDatabase.kt`（`:127` 版本 63、`:373` 注册迁移）、
`entity/LearningEntities.kt`（新增复合索引）、`LibraryCatalogScaleFixture.kt`、`LibraryCatalogScalePerformanceInstrumentedTest.kt`、
`MasteryReadAggregateScaleInstrumentedTest.kt`、`LibraryCatalogPagingInstrumentedTest.kt`、`FullMigrationMatrixInstrumentedTest.kt`、
`KernelWave0SchemaContractTest.kt`、`LibraryCatalogMemoryIndexSchemaContractTest.kt` 与量化报告。
批 3 文件：`KnowledgeNodeSuccessorsCache.kt`、`RoomBackedStudyExperienceRepository.kt`、`StudyExperienceObservationJobs.kt`、
`StudyProjectionDrainer.kt`、`core/database` 的 `RoomStudyDatabase.kt` / `ProblemOrganizationDao.kt` / `KnowledgePort.kt`
及四个 JVM 用例 + 一个 study 仪器化用例（`KnowledgeNodeChangeSignalInstrumentedTest.kt`）。

## 2. 逐项落证（是否触发物化 + 量化数字 + EXPLAIN 结论）

### S17 · `observeActiveMistakes` 性能量化（批 1，零 schema）

- **空实现修复（本轮真正消灭的失败）**：`PerformanceGateTest.insertTestData` 原为空实现 → 三条计时断言在空库上恒真；
  修复后为真实 1 万行夹具。第二个空转点：旧 `ftsSearchCount` 直测绕过 `refreshProjection`，FTS 索引从未建立
  （实测 content=0/indexed=0）——即旧 FTS 门也是空转。
- **新增专属门**：`observeActiveMistakes` 5000 行夹具 + EQP 结构断言（SEARCH 走索引 / 无 `SCAN TABLE problems` /
  无整段 `TEMP B-TREE FOR ORDER BY` / 无 `CORRELATED SCALAR SUBQUERY`）+ 计时 backstop **p95=49ms < 1500ms×CI**；
  **可证伪已实证**：把查询临时退化成相关子查询形态 → 红；还原 → 绿。
- **口径迁移（如实登记，非放宽）**：搜索/中文门改测用户可见 `searchPage` 路径，实测 **p95=164ms < 500ms 预算**；
  原断言的 count 路径在真实数据上确实超预算，本批改为门住用户可见路径，count 路径移交 S18。
- **移交批 2 的数字（`2026-10-05-s18-count-path-prefinding.md` §2）**：`countSearch` 单次 3615ms（100 命中）/
  9852ms（1 万命中）；`librarySearchCount` 3623ms；旧门 countSearch P95 4204ms / 中文 9957ms / 并发 24252ms；
  对照 `searchPage` 单次 171ms，但宽命中排序 P95 **16218ms**、并发均值 **15960ms**；空白搜索目录 count 7ms；
  FTS 首建 bootstrap 6041ms（一次性）。
- **是否触发物化**：不适用（零 schema 批，无物化载体）；结论 = 量化与门到位，热点路径移交并记录。

### S9 · 聚焦 `MASTERY_READ` 聚合（批 2）——**未触发物化，也未加索引**

- **夹具**：200 知识点 / 200 题-单元-绑定 / 2,000 条负向作答 + 2,000 条正向作答（方向过滤噪声），
  每次作答 2 条 attribution（join 行数 8,000）；聚焦读 = `readSubjectMastery` + `readMasteryAggregates`
  （24 个聚焦点 = `MASTERY_FOCUS_RESOLUTION_LIMIT`）。口径 `COUNT(DISTINCT attempt_id)` **一字未改**。
- **量化数字**：p95 = **12ms**（两次复跑 10ms；schema 63 落地后 11ms），预算 250ms 本地 / 1000ms CI（10–20× 余量）。
- **EXPLAIN 结论**（原始 schema，无 `(learner_id, evidence_direction)` 索引）：
  `SEARCH attempt USING INDEX index_attempt_event_learner_id_event_sequence (learner_id=?)`、
  `SEARCH attribution USING INDEX index_assessment_evidence_attribution_snapshot_id (snapshot_id=?)`、
  `USE TEMP B-TREE FOR GROUP BY`、`USE TEMP B-TREE FOR count(DISTINCT)`——两侧都走索引，无 `SCAN attempt_event` /
  `SCAN assessment_evidence_attribution`。
- **候选索引 A/B**：临时建 `(learner_id, evidence_direction)` 索引后计划确实收窄（4,000 行 → 2,000 行），
  但 50 样本 p95 **5ms→5ms 无实测收益** → 按 AGENTS §12.2 不加（消灭不了实测失败，却要多动 schema/写侧维护）。
- **未超预算证据**（未物化依据）：p95 12ms vs 预算 250ms，离门 20 倍以上；报告 §1.5 同时登记"若未来量级增长，
  本门（EQP + p95）是触发信号，候选索引前后对比已留档（§1.4）"。

### S18 · `library_catalog` 视图 page / count / facets（批 2）——**触发 schema 63（索引），未触发 mastery/标签物化**

- **夹具补齐（先修"量化到退化路径"的失败）**：`LibraryCatalogScaleFixture.kt` 5 万行目录形态，
  补 knowledge_node/binding 2,000/50,000、`learner_problem_memory_state` 50,000（learner_id 来源）、
  `learner_knowledge_mastery_state` 2,000（四桶各 25%）、`problem_classification_binding` 100,000（CHAPTER×20 + KNOWLEDGE×50）；
  建库 60,149ms（一次，`@BeforeClass` 共享）。
- **schema 62 实测（补齐后）——超预算**：page 首屏 p95 185–191ms（预算 500/2000ms，过）；
  count（章节+掌握筛选）p95 **4056ms**（样本 ≈3955–4067；预算 500ms，**超 8×，CI 也超 2×**）；
  `subjectFacets` 单次 **78,870ms**（预算 200ms，**超 ~99×**）；facets 整条门（3×27 次调用）**31 分钟未跑完第一条（中止）**。
- **根因 / EXPLAIN 原文**：同一视图展开下唯一结构差异在记忆态连接——page 走
  `SEARCH memory USING INDEX index_learner_problem_memory_state_practice_unit_id`，
  count/facets 走 `SEARCH memory USING COVERING INDEX sqlite_autoindex_learner_problem_memory_state_1 (projection_name=?)`；
  主键是 `(projection_name, learner_id, practice_unit_id)`，`learner_id` 空档 → 逐外层行扫全部同 projection 行（5 万）。
- **候选索引 A/B（实测触发做的唯一改动）**：`(projection_name, practice_unit_id, learner_id)` 复合索引——
  count 3970ms→**44ms**、subjectFacets 78,870ms→**69ms** → 落为 **schema 63**（`LearningEntities.kt` + 非破坏迁移
  `LibraryCatalogMemoryIndexMigration_62_63.kt`；DDL 与 `63.json` 逐字一致由 `LibraryCatalogMemoryIndexSchemaContractTest` 钉住；
  真库"索引建出、旧行原样"由 `FullMigrationMatrixInstrumentedTest` 新用例钉住）。
- **修复后实测（schema 63）**：page 首屏 197ms；筛选 page 47ms；count 46ms；facet SUBJECT 62ms / SECTION **181ms**
  （90% 预算，最紧一条，如实登记）/ MASTERY 126ms；`LibraryCatalogPagingInstrumentedTest` 由 447s 降到 60.2s 并全绿。
- **未物化 mastery/标签列（指认）**：物化会新增投影/绑定/分类/条目变更的整套写侧维护面，而实测失败点不在
  "相关子查询逐行重算"（只差 memory 一行的访问路径）→ 按"极小形态 + 消灭实测失败"只加索引。
- **口径一致性**：索引不改行/列/视图定义；目录路径 / FTS 路径 / facet 三处读取 SQL 一字未动；
  `createdRangeFilterNarrowsCatalogFtsCountsAndFacets`（三处口径一致性）保持并通过。
- **未做（移交）**：FTS `countSearch` / 宽命中排序 / 并发页路径（3.6s / 16.2s / 16.0s 量级）本批**未改、未设门**——
  根因在 FTS 驱动顺序（MATCH 未作驱动约束），不是 mastery/标签物化能修的（报告 §4）。
- **是否触发物化**：**否**；触发的是索引，schema 63 = 索引迁移。

### S20 · 快照观察侧合并 + 后继映射缓存（批 3，零 schema）

- **观察侧合并**：三条重建型观察路径改为 `distinctUntilChanged().conflate()`（`StudyExperienceObservationJobs.kt:87`），
  突发事件 → 一次重建（计数用例）；**避开 `@FlowPreview` 的 debounce**；写路径显式 publish 完全不受影响
  （不经过本类），"写后立即可见"时序语义不变。
- **后继映射缓存**：`KnowledgeNodeSuccessorsCache` 缓存 `KnowledgeNodeSuccessors`（保持"一次 drain 内恒定"不变量）；
  失效条件与 `knowledge_node` 变化/合并退役写路径对齐（Room 表级探针 → 仓库订阅 → `invalidateKnowledgeNodeSuccessors()`，
  `RoomBackedStudyExperienceRepository.kt:323`）。
- **P0 修复（修复轮）**：无世代守卫的旧实现会被"加载期间送达的失效"吞掉（写回旧值）——已被回归用例证伪
  （1 failed）；修复 = 加载前记录世代、安装前在同一把锁下复核（`KnowledgeNodeSuccessorsCache.kt:72-88`），
  `invalidate()` 递增世代并清缓存（`:58-63`）；失效风暴（连续失效达到 `MAX_LOAD_ATTEMPTS` 4 次）**不缓存**、
  只交回本次读值（`:72-88,93`）。
- **门数字**：JVM 整模块 **671/0/0**；study 定向仪器化 **8/0/0**（真 Room，含合并退役信号与 KF-32 改绑/合并×改绑 drill）。
- **是否触发物化**：不适用（零 schema）。
- **残余窗口（KDoc 登记，如实）**：Room 失效投递异步到达，写提交与失效投递之间启动的 drain 仍可能读到旧映射
  （毫秒级；生产改 `superseded_by` 只在启动/横幅重试的内容安装期发生）。

## 3. 批级门与综合门

| 门 | 结果 |
|---|---|
| 批 1 门 | 全量 JVM `--rerun-tasks` + DB 仪器化 **全绿**（提交信息口径；具体计数未随提交附上 → UNVERIFIED，见 §5.4） |
| 批 2 门 | 全量 JVM 首轮红：`KernelWave0SchemaContractTest` 的 schema 头字面量停 62（该用例刻意钉住"下一次 bump 请同步本字面量"）→ 同步 63 后 `:core:database:testDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL、全量 JVM（`test` + `testLocalFirstDebugUnitTest` + `testStrictOfflineDebugUnitTest`）BUILD SUCCESSFUL；DB 仪器化 **全绿**。定向：S9 `1/0`（p95=11ms）、S18 `3/0`（197/47/46ms + facet 62/181/126ms）、分页一致性类绿（447s→60.2s）、矩阵 `10/0/0`（1→63 逐版本升级） |
| 批 3 门 | 全量 JVM `--rerun-tasks` + `core:data` 仪器化 **全绿**；JVM 整模块 `671/0/0`、study 定向 `8/0/0` |
| 综合门 · 全量 JVM | **exit=0**（通过） |
| 综合门 · DB 全套仪器化 | **exit=0**（通过） |
| 综合门 · app 全套仪器化 | **首轮 exit=1 → 定案为环境锁争用（非回归）**：首轮 53 例 3 失败，全部为 `CapabilityScreenInstrumentedTest` 的 `SQLiteDatabaseLockedException (SQLITE_BUSY)`（紧随 30 分钟 DB 全套在同一模拟器背靠背连跑）；**单独重跑 53/0/0**（2026-10-06T01:47:27Z，3m58s） |
| 综合门 · R8 冒烟 | **净**：构建 `:app:assembleLocalFirstRelease` BUILD SUCCESSFUL in 3m30s（376 任务 / 42 executed），`:app:minifyLocalFirstReleaseWithR8`、`:app:packageLocalFirstRelease`、`:app:assembleLocalFirstRelease` 均 executed（非 UP-TO-DATE）；APK 170,568,267 B / sha256 `5715236f…802e`（构建前基线 170,566,288 B / sha256 `02a298ea…`，确认为新产物）；`apksigner verify` V2 Signer `C=US, O=Android, CN=Android Debug`；装机 `Success`（全新安装）→ monkey `Events injected: 1` → PID **19503**（90s 后复测同 PID）→ `topResumedActivity=MainActivity`；logcat 全文 107 行、`FATAL|ClassNotFound|NoSuchMethod|NoClassDefFound` 计数 **0**（两次复测）；截图 1080×2400、81,515 B，主界面正常渲染（无崩溃弹窗/黑屏）。环境：`ANDROID_HOME=C:\Android\Sdk`、adb 1.0.41、emulator-5554（sdk_gphone64_x86_64 / API 34，已唤醒）；日志中唯一 E 级行 `Not starting debugger since process cannot load the jdwp agent`（release 非 debuggable 正常提示，不在四类 token 内） |

## 4. 独立复核登记（逐批；新 Agent、只读、对抗性）

- **批 1 / 批 2 / 批 3 结论：均 approve-with-notes**（来源：三份提交信息末尾「独立复核：approve-with-notes」；
  批 1 的 note 明细未随提交留档 → UNVERIFIED）。
- **批 2 修复轮**：全量 JVM 门红归因**本批自己的漏改**（schema 63 bump 后未同步 `KernelWave0SchemaContractTest.kt:44-48`
  的钉住字面量）→ 同步为 63，**未放宽任何断言**（实质断言"最新导出 schema == STUDY_DATABASE_VERSION"未动）后全绿。
- **批 3 修复轮**：P0（缓存失效被旧值吞掉）修复 + 回归用例证伪旧实现（1 failed）后全绿（见 §2 S20）。
- 三条批级复核的完整 note 条目（尤其批 1）未随提交/文档留档，本条只登记可得结论。
- **协调方裁定（批 1 口径迁移的仓内授权记录，2026-10-05）**：批 1 实施者升级提问「真实数据下既有搜索门过不去（`countSearch` P95 3.6s vs 预算 500ms）」；协调方裁决 **A**——① 搜索门改测**用户可见路径**（`RoomLibrarySearchStore.searchPage`，实测 p95 164ms）；② `countSearch` 本批**不设门**，数字 + EXPLAIN 写入 `docs/research/2026-10-05-s18-count-path-prefinding.md` **移交 S18**；③ 并发断言改到用户可见路径；④ **不许静默删断言**（口径迁移必须在报告写明）。与审计 N-17「预算判定权在用户、不得挑一个能变绿的预算」的关系：**预算数值未动**（`TARGET_MS`/CI 系数一字未改），变的是**被测路径**（count→page），且 count 路径的数字被完整移交而非丢弃——属**口径迁移**而非放宽门；本条即其仓内授权记录。

## 5. 遗留与 UNVERIFIED

1. **KF-31 未启**（前置未变：KF-29 需先积累真实错误率数据；3C 前半样本量 0，不得表述为"错误率达标"）。
2. **3D 硬门仍挡阶段 5**（`docs/research/2026-10-04-stage5-gate-check.md`：D-0 未实现 / D-1 无 ≥0.95 结论 / D-2·D-3 未做）；
   D-0 属内核线、与知识包侧车格式相邻，需与 KB 线协调写入窗口。
3. **综合门 app 全套 exit=1 已定案（环境锁争用，非回归）**：首轮 53 例 3 失败 = `CapabilityScreenInstrumentedTest` 的 `SQLITE_BUSY`（紧随 30 分钟 DB 全套背靠背连跑）；单独重跑 **53/0/0**（01:47Z）。环境边界登记：**DB 全套与 app 全套不要在同一模拟器背靠背连跑**——前序套件的连接未释放会造成锁争用（本轮实测一次）。
4. **批级门的计数不全**：批 1/批 2 的全量 JVM 与 DB 仪器化只有"全绿"结论（提交信息口径），具体计数未留档；
   批 3 有 671/0/0 与 8/0/0。
5. **量化门的环境口径**：数字全部来自模拟器 `test_device`（API 34、google_apis、x86_64、2 核）debug 构建、
   framework SQLite、**未 ANALYZE**、`ciSlowRunner ×4`；真机 / release / 带统计信息环境未测——**不把宽松门当性能背书**，
   也不把抖动当回归。
6. **S9 未物化依据**：p95 12ms vs 预算 250ms（CI 1000ms），且候选索引前后 5ms→5ms 无实测收益（§2 S9）；
   未来量级增长时 EQP + p95 门是触发信号。
7. **S18 物化口径**：触发的是 **schema 63 索引**（非 mastery/标签物化）；目录 / FTS / facet 三处读取 SQL 一字未动，
   三处口径一致性用例通过；SECTION facet 181ms/200ms 是本批最紧的一条（90% 预算），环境抖动时会第一个红。
8. **FTS `countSearch` / 宽命中排序 / 并发页路径**（3.6s / 16.2s / 16.0s 量级，`2026-10-05-s18-count-path-prefinding.md` §2）
   本批未改、未设门——根因在 FTS 驱动顺序，留后续批次。
9. **计划文档**：`docs/research/2026-10-05-stage3c-part2-plan.md` 已随本记录的 app 定案更新一并纳入提交（此前收尾提交遗漏）。
10. **台账折入待办**：`docs/agent-first-refactor-decisions-2026-09-23.md` 在撰写本记录时仍为**另一条会话的在飞文件**
    （工作树未提交改动，含"修订批（2026-10-01）"等内容）——**未改动**。待折入两条：**3C 前半**（KF-29/KF-30/KF-07 完成
    + KF-31 未启 + schema 62，来源 `docs/research/2026-10-04-stage3c-part1-completion-record.md`）与 **3C 后半**
    （S17/S9/S18/S20 完成 + schema 63 + 本记录，提交 `8d7e24b2` / `d78815c3` / `85e6b6ae`）。
11. **S20 残余窗口**（失效异步到达的毫秒级窗口；KDoc 已如实登记，见 §2 S20 末条）。
12. **3C 前半遗留仍有效**（KF-30 残余语义 (a)(b)(c)、KF-07 空绑定下游依赖等）——见前半记录 §4，不在本批范围。
13. **Mimosa 扫描长期 inconclusive**——不声称任何安全结论（前半记录同款登记）。

## 6. 剩余风险

- **FTS 路径仍是秒级**：本批门只覆盖 `searchPage` 首屏与 `library_catalog` 三条路径；宽命中搜索 / 并发页
  （1 万行 16.2s / 16.0s）在真实数据上仍可能让用户可感卡顿——存在被误读为"性能已整体收口"的风险。
- **SECTION facet 90% 预算**：环境抖动时它是第一条变红的门（结构断言仍绿）；门的结论绑定设备 SQLite 规划器行为
  （API 34 framework driver；其他 API/驱动未逐一验证）。
- ~~综合门 app 全套 exit=1 未定位~~ → **已定案**：环境锁争用（首轮 3 例 `SQLITE_BUSY`、单独重跑 53/0/0）；综合门最终口径 = JVM/DB/R8 同轮全绿 + app 由单独重跑补证全绿；环境边界（DB 全套与 app 全套不得背靠背连跑）已登记。
- **后继映射缓存窗口**：异步失效投递的毫秒级窗口 + 生产改绑只在内容安装期发生，风险低但非零。
- **量化结论的环境依赖**：模拟器 2 核 debug 数字只代表该环境，不能外推真机/release。
- **合集门纪律**：本轮再次实证"门的 Gradle 与实施者自验不得并发"（批 2 JVM 红为自改漏同步、非污染；
  前半记录的并发污染教训仍适用）。
