# S18 前置量化：library FTS `countSearch` 路径在真实 1 万行上超预算（移交批 2）

- 日期：2026-10-05
- 来源：阶段 3C 后半批 1（S17 性能量化）在修复 `PerformanceGateTest.insertTestData`
  空实现后，用真实 1 万行 library 夹具实测发现。
- 状态：**超预算，移交批 2（S18）处理：改查询驱动或按需物化**。
- 裁决（2026-10-05 批 1 实施提问，口径迁移记录）：搜索门改测**用户可见**
  `RoomLibrarySearchStore.searchPage` 路径；`countSearch`/`librarySearchCount` 本批
  **不设门**——原断言指向的 count 路径在真实数据上确实超预算（数字 + EXPLAIN 见下），
  本批改为门住用户可见路径 + 把 count 路径移交 S18。这是口径迁移，不是放宽门。

## 1. 测量环境与夹具

- 设备：`test_device` AVD（API 34，google_apis，x86_64，pixel_6），与
  `.github/workflows/android-check.yml` 的 CI 镜像同规格；debug 构建；framework SQLite
  （`AndroidSQLiteDriver`；未 ANALYZE）。
- 夹具（`PerformanceGateTest.insertTestData`）：problems / revisions / practice_units /
  error_book_entries 各 10,000 行；文本编入 `test query N`、`concurrent test N`、
  `函数方程`、`独特检索词` 等可检索 token。
- 采集命令：`./gradlew :core:database:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.PerformanceGateTest`
  （数字取自 `adb logcat` 的 `System.out`，2026-10-05）。

## 2. 实测数字（本地严格口径；CI 预算 ×4）

| 测量点 | 查询 / 命中 | 结果 |
|---|---|---|
| `LibraryFtsSearchDao.countSearch`（DAO 直测） | `"test" "query" "0"` / 100 命中 | 3615 ms（单次） |
| 同上 | `"函" "数" "方" "程"` / 10000 命中 | 9852 ms（单次） |
| `librarySearchCount`（生产 totalCount 路径，含投影刷新） | 100 命中 | 3623 ms（单次） |
| 既有 PerformanceGateTest 旧测量（countSearch P95） | 100 命中 | **4204 ms**（预算 500ms×CI） |
| 同上（中文，countSearch P95） | 10000 命中 | **9957 ms** |
| 同上（并发，countSearch 均值） | ~1 千命中 × 10 并发 | **24252 ms**（预算 1000ms×CI） |
| 对照：`librarySearchPage`（用户可见路径） | 100 命中 / 首页 20 行 | 171 ms（单次） |
| 对照：`librarySearchPage` 宽命中排序（P95，50 次） | 10000 命中 / 首页 20 行 | **16218 ms** |
| 对照：并发 `librarySearchPage`（10 并发均值） | ~1 千命中 × 10 | **15960 ms** |
| 对照：`libraryQueryDao().count`（空白搜索目录计数） | 10000 行 | 7 ms |
| FTS 首建 bootstrap（一次性） | 10000 revisions | 6041 ms；第二次刷新 3 ms |

并发数字（`concurrent search ... latencies=[12067, 12088, 12159, 20705, 18172, 12134,
18253, 18435, 20695, 18405]`，另一轮复跑 `[11272, 11497, 11382, 11478, 16557, 16754,
18781, 16668, 16737, 18787]`，同量级）来自 2 核模拟器，含 10 路 `refreshProjection()`
写事务串行化，量级仅供参考。

## 3. EXPLAIN 原文（设备原生，API 34；筛选参数全空）

`EXPLAIN QUERY PLAN` 作用于 `LibraryFtsSearchDao.countSearch` 的 SQL 形状
（MATCH 取 `CjkTextTokenizer.matchExpression("test query 0")`；采集点见
`PerformanceGateTest.printCountSearchQueryPlan`，取到后删除）：

```
MATERIALIZE library_catalog
SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)
SEARCH problem USING INDEX sqlite_autoindex_problem_1 (problem_id=?)
SEARCH unit USING COVERING INDEX sqlite_autoindex_practice_unit_1 (practice_unit_id=?)
SEARCH revision USING INDEX index_problem_revision_problem_id_revision_id (problem_id=? AND revision_id=?)
SEARCH memory USING INDEX index_learner_problem_memory_state_practice_unit_id (practice_unit_id=?) LEFT-JOIN
CORRELATED SCALAR SUBQUERY 4
SEARCH binding USING COVERING INDEX index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version (practice_unit_id=?)
SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 (projection_name=? AND learner_id=? AND knowledge_node_id=?)
CORRELATED SCALAR SUBQUERY 4
SEARCH binding USING COVERING INDEX index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version (practice_unit_id=?)
SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 (projection_name=? AND learner_id=? AND knowledge_node_id=?)
CORRELATED SCALAR SUBQUERY 5
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
CORRELATED SCALAR SUBQUERY 6
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
SCAN catalog
CORRELATED SCALAR SUBQUERY 1
SEARCH classification USING COVERING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=? AND label_id=?)
SEARCH content USING COVERING INDEX index_library_search_content_problem_revision_id (problem_revision_id=?)
SCAN library_search_fts VIRTUAL TABLE INDEX 11:
```

本地 SQLite 3.50.4 用同一 schema/数据复核，计划同形（驱动端 `SCAN entry` +
循环内 `SCAN library_search_fts VIRTUAL TABLE INDEX 11:`）。

## 4. 根因

- `countSearch` 的 join 形状（`library_search_fts JOIN library_search_content JOIN
  library_catalog`）让 planner 先 **MATERIALIZE library_catalog**（视图展开：
  entry→problem→unit→revision + memory + 相关子查询），再对每条 catalog 行
  **SCAN library_search_fts VIRTUAL TABLE INDEX 11:** 做 FTS 探测——"目录全量物化 +
  逐行回探"，而不是从 FTS MATCH 驱动。`MATCH` 没有被用作驱动约束。
- 对照 `searchPage`（raw 排序查询）从 FTS MATCH 驱动，100 命中时快一个数量级（171ms）；
  但它是对**全部命中行**逐行算加权 rank，命中上万时同样放大到秒级（16218ms）。
- 生产消费者链：`LibraryViewModel.kt:265/302` → `RoomLibraryCatalogRepository.totalCount`
  （`:54-76`）→ `StudyDatabasePort.librarySearchCount` → `RoomLibrarySearchStore.searchCount`
  （`:199-217`，每次 `refreshProjection()`）→ `LibraryFtsSearchDao.countSearch`。搜索页的
  "N 道题"与 `query()` 分页窗口都走这条。
- 4A 批 1 的 5 万行夹具（`LibraryCatalogPagingInstrumentedTest`）只压 `searchPage`/`page`
  路径，从未调用 `countSearch`；`PerformanceGateTest` 当时是空实现 → 该热点一直不可见。

## 5. 移交建议（批 2/S18；"超预算才物化"）

1. 先试**零 schema 的查询驱动修正**（例如把 `MATCH` 放进子查询先物化 FTS 命中，再 join
   content/catalog；或改写 join 顺序），按 4A 的等价性方法（新旧 SQL 逐列对照）复核；
2. 若驱动修正后仍超预算，再按 S18 计划物化 mastery/标签列（schema 63，条件触发）；
3. 对 page/count/facets 加 EQP + 计时门（3C 后半计划 §3 批 2），并把**宽命中排序**
   （1 万命中 / 16.2s）与**并发页路径**（均值 16.0s）一并纳入预算评估；
4. `LibraryFtsSearchDao.countSearch` 与 `RoomLibrarySearchStore.searchPage` 的 EQP 均应
   作为结构门（不比对完整字符串）。

## 6. UNVERIFIED

- 模拟器 debug 构建；真机 / release 构建的数量级未测（CI 预算 ×4 后 count 路径仍超）。
- bootstrap 6041ms 为一次性成本（首次搜索触发）；是否可接受未裁。
- 计划形态依赖设备 SQLite 版本（API 34 / framework driver）；其他 API 的差异未逐一验证。
- 并发数字含 2 核模拟器与写事务串行化效应，不代表真机多核表现。
