# S9 / S18 量化报告：MASTERY_READ 聚合与 library_catalog 视图（2026-10-05）

> 阶段 3C 后半批 2（`docs/research/2026-10-05-stage3c-part2-plan.md` §3 批 2）。
> 纪律依据：AGENTS §12.2「新增必先指认它消灭的具体失败」→ **量化先行，物化/索引只在实测超预算时才做**。
> 本报告只记录本批实测到的原始数字与 EXPLAIN 原文；结论逐条对应"加了什么/为什么/是否触发物化"。

## 0. 环境、夹具与口径

- **设备**：`test_device` AVD（API 34，google_apis，x86_64，pixel_6，2 核），debug 构建，
  framework SQLite（`AndroidSQLiteDriver`，未 ANALYZE）——与 `.github/workflows/android-check.yml` 同规格。
- **口径**：预热 3 / 样本 24 / p95 = `((n-1)*95)/100`（最近秩）/ `ciSlowRunner` ×4，
  逐字照 `KnowledgeContextRetrievalInstrumentedTest.kt:397-537`；EQP 只断言结构不变量，不比对完整字符串。
- **采集**：`adb logcat`（`System.out`）；命令逐条见 §5。

## 1. S9：`readIndependentErrorAggregates`（聚焦 `MASTERY_READ`）

### 1.1 夹具与测量

`MasteryReadAggregateScaleInstrumentedTest`（core/database androidTest）：200 个知识点 /
200 题-单元-绑定 / **2,000 条负向作答 + 2,000 条正向作答**（方向过滤的噪声），每次作答带
**2 条 attribution**（主 + 相邻知识点；多 KC 归属的常态 → join 行数 8,000），
每题/点/绑定/条目/掌握态/记忆态等 1,600+ 行。聚焦读 = `readSubjectMastery` +
`readMasteryAggregates`（24 个聚焦点，= `RoomTutorToolRunner.MASTERY_FOCUS_RESOLUTION_LIMIT`）。
口径（`COUNT(DISTINCT attempt_id)`，一题一次）**一字未改**。

### 1.2 EQP 原文（当前 schema，`attempt_event` 无 `(learner_id, evidence_direction)` 索引）

```
SEARCH attempt USING INDEX index_attempt_event_learner_id_event_sequence (learner_id=?)
SEARCH attribution USING INDEX index_assessment_evidence_attribution_snapshot_id (snapshot_id=?)
USE TEMP B-TREE FOR GROUP BY
USE TEMP B-TREE FOR count(DISTINCT)
```

两侧都走索引；无 `SCAN attempt_event` / `SCAN assessment_evidence_attribution`。

### 1.3 实测（p95 预算 250ms 本地 / 1000ms CI，锚点 = 知识检索腿同工具读面预算）

```
S9 focused MASTERY_READ benchmark:
samples=[10,10,12,12,10,10,11,11,10,10,10,9,10,8,12,9,10,9,9,9,9,11,10,10] p95=12ms budget=250ms
```

（两次复跑：p95=10ms；schema 63 落地后再跑一次
`samples=[9,11,10,10,10,10,10,9,10,9,9,10,8,9,9,9,8,10,10,11,11,9,9,10] p95=11ms`。）

### 1.4 候选索引评估（EXPLAIN 前后对比）

`CREATE INDEX index_attempt_event_learner_id_evidence_direction ON attempt_event(learner_id, evidence_direction)`
（测试内临时建/删，只作用于临时库）：

```
after:
SEARCH attempt USING INDEX index_attempt_event_learner_id_evidence_direction (learner_id=? AND evidence_direction=?)
SEARCH attribution USING INDEX index_assessment_evidence_attribution_snapshot_id (snapshot_id=?)
USE TEMP B-TREE FOR GROUP BY
USE TEMP B-TREE FOR count(DISTINCT)
```

计时（各 50 样本，两次运行）：**before p95=5ms / after p95=5ms**（第二次：before min 3 / max 6，
after min 3 / max 6）。

### 1.5 结论（S9 · 未触发物化，也未加索引）

- **未超预算**：聚焦 `MASTERY_READ` p95 = 12ms，预算 250ms（CI 1000ms）——离门 20 倍以上。
- **候选索引有结构收益、无实测收益**：计划确实从"只吃 `learner_id` 前缀（4,000 行）"
  收窄到"`learner_id AND evidence_direction`（2,000 行）"，但 50 样本 p95 一字不变
  （5ms→5ms），门的结论不变。按 AGENTS §12.2（新增必须指认它消灭的具体失败）与 §11
  纪律，**不加这条索引**——它消灭不了任何一个实测失败，却要动 schema（写侧多一条索引维护）。
- **不物化**：审计 S9 的"预聚合/物化"方向在当前规模（2k 作答）与实测下没有触发条件；
  若未来作答量级增长，本门（EQP + p95）是触发信号，`(learner_id, evidence_direction)`
  索引的前后对比已留档（§1.4），可作为届时的第一步。

## 2. S18：`library_catalog` 视图（page / count / facets）

### 2.1 夹具补齐（"消除退化路径"）

`LibraryCatalogScaleFixture.kt`：5 万行目录形态，补齐三块（旧夹具全缺 → 三条相关子查询
全走空连接，mastery 恒 `'unknown'`、标签恒 NULL，筛选与文本拼接都测不到真实成本）：

| 块 | 行数 | 作用 |
|---|---|---|
| problem / revision / unit / entry | 各 50,000 | 目录主体（与 4A 批 1 同形态） |
| knowledge_node / binding | 2,000 / 50,000 | mastery 子查询的连接左端 |
| `learner_problem_memory_state` | 50,000 | **learner_id 的来源**（缺它 mastery 仍恒 unknown） |
| `learner_knowledge_mastery_state` | 2,000 | 四桶状态（learning/mastered/stale/conflicted 各 25%） |
| `problem_classification_binding` | 100,000 | CHAPTER × 20 桶 + KNOWLEDGE × 50 桶 |

`LibraryCatalogPagingInstrumentedTest.fiftyThousandRowsPageSearchAndFacetCountsInSql` 改用该夹具，
原有断言全部保留，另加非退化断言（标签非空、四桶 facet、section/mastery 过滤真的收窄）。
夹具建库成本：**60,149ms**（一次，两类测试共享形态）。

### 2.2 实测（schema 62，夹具补齐后）——**超预算**

| 路径 | 查询 | 实测 | 预算（本地 / CI×4） | 结论 |
|---|---|---|---|---|
| page 首屏 | `LibraryQueryDao.page`（无筛选，limit 20） | p95 **185–191ms** | 500 / 2000ms | 过 |
| count（章节+掌握筛选） | `LibraryQueryDao.count` | p95 **4056ms**（样本 ≈3955–4067） | 500 / 2000ms | **超 8×（CI 也超 2×）** |
| facets（`subjectFacets`） | 单次 | **78,870ms** | 200 / 800ms | **超 ~99×（CI 也超 ~98×）** |
| facets 整条门 | 3 条 facet × 27 次调用 | **31 分钟未跑完第一条**（中止） | — | **不可接受** |

### 2.3 根因（EXPLAIN 原文，schema 62）

同一 `library_catalog` 展开下，**唯一的结构差异**在记忆态连接的访问路径：

- **page**（慢在上限内）：`SEARCH memory USING INDEX index_learner_problem_memory_state_practice_unit_id (practice_unit_id=?) LEFT-JOIN`
- **count / facets**（秒-分钟级）：
  `SEARCH memory USING COVERING INDEX sqlite_autoindex_learner_problem_memory_state_1 (projection_name=?) LEFT-JOIN`

主键是 `(projection_name, learner_id, practice_unit_id)`：`learner_id` 是空档，规划器只能吃
`projection_name` 前缀 → **逐外层行扫全部同 projection 行（5 万）**。无 ANALYZE 统计时，
"覆盖 learner_id"的主键索引被估成比 `practice_unit_id` 索引便宜，于是被选中。page 路径要
`next_review_at_epoch_millis`（主键索引覆盖不了），才落到正确的 `practice_unit_id` 索引上。

### 2.4 候选索引 A/B（EXPLAIN 前后 + 计时）

`CREATE INDEX index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id
ON learner_problem_memory_state(projection_name, practice_unit_id, learner_id)`（测试内临时建，仅临时库）：

```
after（count 计划，其余行逐字不变）:
SEARCH memory USING COVERING INDEX index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id (projection_name=? AND practice_unit_id=?) LEFT-JOIN
```

| 测量 | before | after |
|---|---|---|
| count（章节+掌握筛选，3 样本） | [3970, 3982, 3972] ms | **[44, 44, 67] ms** |
| subjectFacets（单次） | 78,870ms | **69ms** |

### 2.5 结论（S18 · **触发 schema 63，但触发的是索引，不是 mastery/标签物化**）

- 超预算成立（count 4056ms / facet 78870ms，CI 预算也超），**修复必须做**。
- 但实测根因是**记忆态连接选了主键前缀计划**，不是"mastery/标签的相关子查询逐行重算"：
  - 物化 mastery/标签列会新增一整套写侧维护面（投影提交/绑定变更/分类变更/条目变更都要
    同步），而实测失败点不在那里；
  - 一条 `(projection_name, practice_unit_id, learner_id)` 复合索引把两个等值约束都给到
    规划器并覆盖 `learner_id`，同一夹具 count 4056ms→47ms、subjectFacets 78,870ms→62ms。
  按"极小形态 + 消灭实测失败"（AGENTS §12.2），**本批做索引（schema 63），不做 mastery/标签物化**。
- 语义不变：索引不改任何行/列/视图定义，目录路径 / FTS 路径 / facet 三处口径的读取 SQL
  一字未动；`LibraryCatalogPagingInstrumentedTest.createdRangeFilterNarrowsCatalogFtsCountsAndFacets`
  （三处口径一致性）保持并通过。

### 2.6 修复后实测（schema 63；修复后跑了两次完整门，数值取最后一次）

| 路径 | p95 | 预算（本地） |
|---|---|---|
| page 首屏（无筛选） | **197ms** | 500ms |
| page 首屏（章节+掌握筛选） | **47ms** | 500ms |
| count（章节+掌握筛选） | **46ms** | 500ms |
| facet SUBJECT | **62ms** | 200ms |
| facet SECTION | **181ms** | 200ms |
| facet MASTERY | **126ms** | 200ms |

夹具建库成本仍为 60,149ms（一次，`@BeforeClass` 共享）；同一批改动下
`LibraryCatalogPagingInstrumentedTest`（补齐夹具 + 一致性用例）从 **447s 降到 60.2s** 并全绿——
索引也把这些用例里的视图查询拉回了毫秒级。

修复后 count 计划（EXPLAIN 原文；与 2.3 的 schema 62 计划相比**只有 memory 一行不同**）：

```
SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)
SEARCH problem USING INDEX sqlite_autoindex_problem_1 (problem_id=?)
SEARCH revision USING INDEX index_problem_revision_problem_id_revision_id (problem_id=? AND revision_id=?)
CORRELATED SCALAR SUBQUERY 1
SEARCH classification USING COVERING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=? AND label_id=?)
CORRELATED SCALAR SUBQUERY 5
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
CORRELATED SCALAR SUBQUERY 6
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
SEARCH unit USING COVERING INDEX sqlite_autoindex_practice_unit_1 (practice_unit_id=?)
SEARCH memory USING COVERING INDEX index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id (projection_name=? AND practice_unit_id=?) LEFT-JOIN
CORRELATED SCALAR SUBQUERY 4
SEARCH binding USING COVERING INDEX index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version (practice_unit_id=?)
SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 (projection_name=? AND learner_id=? AND knowledge_node_id=?)
```

> `SECTION` facet 181ms / 200ms 是本批最紧的一条（90% 预算）；它是唯一必须从
> `problem_classification_binding`（10 万行）驱动的 facet。预算按计划沿用 200ms×CI 未动，
> 该数字如实记录；若后续环境抖动导致红，它会是第一个信号（结构断言仍绿）。

## 3. 本批新增/改动清单（每一项消灭的具体失败）

| 改动 | 类型 | 消灭的具体失败 |
|---|---|---|
| `MasteryReadAggregateScaleInstrumentedTest` | 新测试（零生产改动） | MASTERY_READ 聚合此前**无量化的规模门**（只有语义用例），退化到全表扫/相关子查询时无人报警（S9 审计项无信号） |
| `LibraryCatalogScaleFixture.kt` | 新测试夹具 | 5 万行夹具缺 mastery/分类/投影 → 视图三条相关子查询走空连接，page/count/facet 的筛选与标签路径**量的是退化形态**（本报告 §2.2/2.3 证明：补齐后立刻暴露 4s/79s） |
| `LibraryCatalogPagingInstrumentedTest` 夹具升级 + 非退化断言 | 测试改动（原有断言一字未动） | 同上，且"夹具退化"本身不再无人发现（标签/四桶/筛选命中都有断言） |
| `LibraryCatalogScalePerformanceInstrumentedTest` | 新门（EQP 结构 + p95） | 三条目录路径**无预算门**；且无"记忆态连接必须走复合索引"的结构防线（可证伪：删索引即红，§2.3 的 schema 62 实测就是红态） |
| `LearnerProblemMemoryStateEntity` 新增 `(projection_name, practice_unit_id, learner_id)` 索引 | **schema 63** | count 4056ms（预算 500ms×CI）与 subjectFacets 78,870ms（预算 200ms×CI）——实测超预算 |
| `LIBRARY_CATALOG_MEMORY_INDEX_MIGRATION_62_63` + 版本 63 | 非破坏迁移 | 存量库升级后拿不到该索引（否则新装与升级两条路径行为分叉） |
| `KernelWave0SchemaContractTest` 的 schema 头字面量 62→63 | 既有 JVM 契约同步 | 该用例刻意把"当前 schema 头"钉成字面量（原文："下一次 bump 请同步本字面量"）；不同步 = 全量 JVM 门红（本轮实际发生）。实质断言 `最新导出 schema == STUDY_DATABASE_VERSION` 一字未动 |
| `LibraryCatalogMemoryIndexSchemaContractTest`（JVM） | 新契约测试 | 迁移 DDL 与导出的 63.json 漂移（差一个空格真机升级即炸）；索引列序被改回坏形态 |
| `FullMigrationMatrixInstrumentedTest.libraryCatalogMemoryIndexIsCreatedWithoutTouchingStoredRows` | 新仪器化用例 | 纯新增迁移的"旧行还在 + 索引逐列同形"只有空库矩阵无法证明 |

## 4. 未触发/未做（如实）

- **未物化 mastery/标签列**：触发条件（超预算）成立，但实测根因是连接索引，索引即为极小修复；
  物化属"更大机制、无额外实测收益"，按纪律不做（§2.5）。若未来出现"索引仍在但子查询成本"
  的新超预算，物化是下一步候选。
- **未加 `attempt_event(learner_id, evidence_direction)` 索引**：S9 未超预算且前后计时无差
  （§1.4/1.5）。
- **FTS `countSearch` / 宽命中排序 / 并发页路径**：批 1 已把其数字与 EXPLAIN 移交 S18
  （`docs/research/2026-10-05-s18-count-path-prefinding.md`），本批**未改、未设门**——
  它们的根因在 FTS 驱动顺序（MATCH 未作驱动约束），不是 mastery/标签物化能修的；
  本批范围按计划锁定在 `library_catalog` 三条目录路径。这批数字仍是 3.6s（1 万行、count）/
  16.2s（宽命中排序）/ 16.0s（并发）量级，待后续批次处理。
- 模拟器 debug 构建、未 ANALYZE：真机 / release / 带统计信息的环境未测（数量级预期更小）。

## 5. 复跑命令与本批自验结果（2026-10-05，模拟器 `test_device`）

```bash
# S9 聚焦 MASTERY_READ 门（EQP + p95 + 候选索引前后对比）——绿
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.MasteryReadAggregateScaleInstrumentedTest
#   1 tests, 0 failed；focused p95=11ms（预算 250ms）

# S18 目录三条路径门（夹具 5 万行 + EQP + p95）——绿
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.LibraryCatalogScalePerformanceInstrumentedTest
#   3 tests, 0 failed；page 197ms / filtered page 47ms / count 46ms /
#   facet SUBJECT 62ms / SECTION 181ms / MASTERY 126ms

# S18 夹具补齐后的分页/一致性用例——绿（447s → 60.2s）
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.LibraryCatalogPagingInstrumentedTest

# v62→63 迁移：整类矩阵（含 1..63 逐版本升级 + 新索引用例）——绿
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest
#   10 tests, 0 failed；everyExportedSchemaVersion…=179.8s，
#   libraryCatalogMemoryIndexIsCreatedWithoutTouchingStoredRows=5.4s

# JVM schema 契约——绿
./gradlew :core:database:testDebugUnitTest \
  --tests "*LibraryCatalogMemoryIndexSchemaContractTest" --tests "*ExportedSchemaContractTest"

# 全量 JVM 门（与 CI `.github/workflows/android-check.yml:72` 同命令 + 根聚合 test）
./gradlew test testLocalFirstDebugUnitTest testStrictOfflineDebugUnitTest
#   第 1 轮红：KernelWave0SchemaContractTest > the study database version matches the latest
#   exported schema（schema 头字面量停在 62）→ 同步为 63 后
#   :core:database:testDebugUnitTest --rerun-tasks = BUILD SUCCESSFUL（53s），
#   根聚合 = BUILD SUCCESSFUL
```

数字与 EXPLAIN 原文来自上述命令的 `adb logcat`（`System.out`）；EQP 断言的变红条件即 §2.3 的
schema 62 实测（同一语句同一夹具，计划退回主键前缀、计时回到秒级）与 S9 的整表扫假设。
