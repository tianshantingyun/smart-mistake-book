# S18 尾批 2 报告：searchPage 宽命中排序量化与条件等价重写（2026-10-07）

- 批次：S18 尾批 2（`docs/research/2026-10-06-s18-fts-search-plan.md` §3 批 2）；
  前置输入：`docs/research/2026-10-05-s18-count-path-prefinding.md`（移交数字与 EXPLAIN）。
- 纪律依据：AGENTS §12.2（量化先行——只有实测超预算才动）＋ 计划 §5（改查询必须逐列对照新旧结果）。
- 本报告只记录**本轮实测**的原始数字、EXPLAIN 原文、候选形状对比与结论；未验证项见 §9。

## 0. 结论摘要

1. **用户真实规模（百级命中）改前就达标、改后更快**：100 命中 `searchPage` P95
   146ms → 85ms（4 轮区间 79–95ms；预算 500ms×ciSlowRunner）。
2. **千级命中改前超预算，触发条件重写**：1k 命中 P95 784ms → **92ms**（区间 85–99ms，≈8.5×）；
   1 万命中 19208ms → **144ms**（区间 139–167ms，≈130×）；
   10 并发 × 1k 命中均值 10574ms → **896ms**（区间 846–949ms，≈12×）。
3. **重写是零 schema 的等价重写，排序语义一字未改**：把 9 个列命中标志 + 3 个额外 token
   标志从 ORDER BY 的**逐行相关子查询**（`EXISTS(SELECT 1 FROM library_search_fts AS ranked
   WHERE ranked.docid = content.content_row_id AND ranked.<column> MATCH ?)`）改为
   **集合级命中集 LEFT JOIN**（每个标志一次 `MATERIALIZE hit_*` + 按 docid 的自动覆盖索引探测）。
   新旧 SQL 在真实 1 万行库上**逐列逐行同序**（100 / 1k / 10k 命中 × 两种排序 × 两个窗口 ×
   筛选档，共 14 组；另加内存小夹具的并列键与列权重序）。
4. **没有改 bm25/rank（也没法改）**：设备 framework SQLite **不支持 FTS5**（功能探针实测
   `fts5=false`，批 1 记录在本报告 §8 引用的测试 KDoc），而本项目索引是 FTS4——换 bm25/rank
   属用户可见排序语义变更且需要换库，登记待裁定（§8），本批不做。

## 1. 环境、口径与夹具

- **设备**：`test_device` AVD（API 34，google_apis，x86_64，2 核），debug 构建，framework
  SQLite（`AndroidSQLiteDriver`），未 ANALYZE——与 `.github/workflows/android-check.yml` 同规格；
  设备 SQLite 3.39.2、FTS4 可用、FTS5 不可用（批 1 实测记录，见 §8）。
- **口径**：P95 = 最近秩 `((n-1)*95)/100`；预热 3 次、≥20 样本；预算 `500ms × ciSlowRunner`
  （与 `PerformanceGateTest.SEARCH_P95_TARGET_MS` 同值同口径）；并发数字含 2 核模拟器与
  `refreshProjection` 写事务串行化，只作量级参考（同 S17 前置文档 §2 的并发口径）。
- **夹具**：`seedLibraryCatalogScale(10_000)`（`LibraryCatalogScaleFixture.kt`：problems /
  revisions / units / entries 各 1 万行 + 分类 + 掌握态齐全）＋ 一次 FTS bootstrap；
  本批在其上注入 1k 档探针：`UPDATE problem_revision SET problem_markdown =
  problem_markdown || ' 千级探针' WHERE revision_id GLOB 'revision-*0'`（每第 10 行、1000 行）
  ＋同一增量刷新。三档探针命中数经计数路径现场校验：**100 / 1000 / 10000**。
- **采集**：`adb logcat`（`System.out`）；命令见 §7。

## 2. 改前基线（2026-10-07，探针类，改动前在生产 builder 上现场量）

### 2.1 三档 P95（生产路径 `RoomLibrarySearchStore.searchPage`，含 `refreshProjection`）

```
S18 probe baseline tier=100 samples=[133, 142, 128, 125, 122, 120, 126, 126, 132, 127, 118, 121, 122, 166, 139, 134, 125, 146, 118, 137] p95=146ms median=127ms
S18 probe baseline tier=1k  samples=[688, 710, 730, 764, 707, 784, 741, 764, 724, 796, 718, 738, 724, 743, 721, 708, 733, 769, 698, 687] p95=784ms median=730ms
S18 probe baseline tier=10k samples=[19169, 19208, 19434] p95=19208ms median=19208ms
S18 probe concurrent (10x, 1k tier) latencies=[12539, 6069, 12214, 15583, 6253, 12465, 6020, 5967, 15674, 12959] mean=10574ms
```

| 档位 | 改前 P95 | 预算（本地严格） | 结论 |
|---|---|---|---|
| 100 命中（用户真实规模） | 146ms | 500ms | 达标 |
| 1k 命中 | 784ms | 500ms | **超预算 → 触发条件重写** |
| 10k 命中 | 19208ms | 500ms | 病理量级 |
| 10 并发 × 1k（均值） | 10574ms | —（不设门，量级参考） | 病理量级 |

样本口径说明：100/1k 档预热 3 + 20 样本；**改前 10k 档只取 3 样本**（单次 ~19.2s，
20 样本需 6.4 分钟，超出探针预算；改后 10k 档按 ≥20 样本口径复测，见 §5）。
并发档为 10 并发各 1 次的均值。

### 2.2 成本分解微探针（同一夹具、同一改前 SQL 的两种访问形态）

```
S18 probe micro: stem_text set scan=18ms per-row probe x1000=16782ms (docid=1)
```

- **集合级**一次 `SELECT COUNT(*) FROM library_search_fts WHERE stem_text MATCH '"分"'`
  （1 万命中、18ms）——命中集物化的成本。
- **逐行**探测一次 `EXISTS(SELECT 1 FROM library_search_fts AS ranked WHERE ranked.docid = ?
  AND ranked.stem_text MATCH '"分"')` ≈ **16.8ms**；9–12 个标志 × 命中行数就是 1 万档
  19.2s 的来源（16.8ms 是冷/慢样本，量级足以说明问题）。

### 2.3 改前 EXPLAIN 原文（设备，10k 档、筛选全空）

```
MATERIALIZE library_catalog
SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)
SEARCH problem USING INDEX sqlite_autoindex_problem_1 (problem_id=?)
SEARCH unit USING COVERING INDEX sqlite_autoindex_practice_unit_1 (practice_unit_id=?)
SEARCH revision USING INDEX index_problem_revision_problem_id_revision_id (problem_id=? AND revision_id=?)
SEARCH memory USING INDEX index_learner_problem_memory_state_projection_name_practice_unit_id_learner_id (projection_name=? AND practice_unit_id=?) LEFT-JOIN
CORRELATED SCALAR SUBQUERY 15
SEARCH binding USING COVERING INDEX index_practice_unit_knowledge_binding_practice_unit_id_knowledge_node_id_basis_revision_id_taxonomy_version (practice_unit_id=?)
SEARCH mastery USING INDEX sqlite_autoindex_learner_knowledge_mastery_state_1 (projection_name=? AND learner_id=? AND knowledge_node_id=?)
CORRELATED SCALAR SUBQUERY 16
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
CORRELATED SCALAR SUBQUERY 17
SEARCH classification USING INDEX index_problem_classification_binding_problem_id_basis_revision_id_dimension_label_id (problem_id=? AND basis_revision_id=? AND dimension=?)
SCAN library_search_fts VIRTUAL TABLE INDEX 11:
SEARCH content USING INTEGER PRIMARY KEY (rowid=?)
SEARCH catalog USING AUTOMATIC COVERING INDEX (problem_revision_id=?)
CORRELATED SCALAR SUBQUERY 1
SCAN ranked VIRTUAL TABLE INDEX 2:
CORRELATED SCALAR SUBQUERY 2
SCAN ranked VIRTUAL TABLE INDEX 4:
CORRELATED SCALAR SUBQUERY 3
SCAN ranked VIRTUAL TABLE INDEX 7:
CORRELATED SCALAR SUBQUERY 4
SCAN ranked VIRTUAL TABLE INDEX 5:
CORRELATED SCALAR SUBQUERY 5
SCAN ranked VIRTUAL TABLE INDEX 3:
CORRELATED SCALAR SUBQUERY 6
SCAN ranked VIRTUAL TABLE INDEX 6:
CORRELATED SCALAR SUBQUERY 7
SCAN ranked VIRTUAL TABLE INDEX 8:
CORRELATED SCALAR SUBQUERY 8
SCAN ranked VIRTUAL TABLE INDEX 9:
CORRELATED SCALAR SUBQUERY 9
SCAN ranked VIRTUAL TABLE INDEX 10:
CORRELATED SCALAR SUBQUERY 10
SCAN library_search_fts VIRTUAL TABLE INDEX 11:
CORRELATED SCALAR SUBQUERY 11
SCAN library_search_fts VIRTUAL TABLE INDEX 11:
CORRELATED SCALAR SUBQUERY 12
SCAN library_search_fts VIRTUAL TABLE INDEX 11:
USE TEMP B-TREE FOR ORDER BY
```

病征：主循环从 FTS MATCH 驱动（这一点本来就对），但 ORDER BY 的 12 个标志各是一个
**相关标量子查询**（`CORRELATED SCALAR SUBQUERY n` + `SCAN ranked VIRTUAL TABLE`）——
对每个命中行重扫一次 FTS 索引。该原文已冻结为
`LibraryFtsRankingPathInstrumentedTest.PRE_CHANGE_RANKING_PLAN`（红样例，行 765 起）。

### 2.4 改前 SQL 原文（冻结副本）

改前 builder（git 59fd8a32 → 批 1 期间的 `buildLibrarySearchRawQuery`）的逐字原文已冻结为
`LibraryFtsRankingPathInstrumentedTest.preChangeSearchPageSql(...)`（行 ~517 起，等价性门的旧口径
参照物）；结构由 JVM 守卫 `LibraryFtsRankingQueryContractTest` 钉住（9 个列级相关子查询、
3 个整行探测、普通 JOIN 驱动、不得混入新形态）。

## 3. 候选形状实测（为何选 LEFT JOIN 命中集）

在改前 SQL 的同一夹具上跑了三种零 schema 候选（raw 执行、单次；顺序对照 = 与改前 SQL 逐行
比较 entryId 序，覆盖 100/1k/10k、两种排序、offset 0/100、subject 筛选）：

| 候选 | 形态 | 1k 单次 | 10k 单次 | 计划形状 | 同序 |
|---|---|---|---|---|---|
| A `LEFT_JOIN` | `LEFT JOIN (SELECT docid AS hit_docid FROM library_search_fts WHERE <column> MATCH ?) ON hit_docid = library_search_fts.docid` ×12 | 96ms | **149ms** | 12×`MATERIALIZE hit_*` + 12×`SEARCH hit_* USING AUTOMATIC COVERING INDEX (hit_docid=?) LEFT-JOIN` | ✅ 全部通过 |
| B `LEFT_JOIN_LIMIT` | A + 子查询 `LIMIT -1`（防展平） | 100ms | 142ms | 与 A 逐行相同 | ✅ |
| C `IN_SET` | `library_search_fts.docid IN (SELECT docid FROM library_search_fts WHERE <column> MATCH ?)` ×12 | 106ms | 120ms | `LIST SUBQUERY n` + `SCAN library_search_fts`（无自动索引） | ❌ `1k/subject=MATH` 档**返回空页**（且无自动索引信号）→ 弃用 |

选择 **A**：计划形状是显式"命中集先物化 + 按 docid 自动覆盖索引探测"，行数不放大
（docid 在 FTS 表里唯一，LEFT JOIN 至多一行），且顺序对照全绿。C 形状虽最快，但筛选档下
结果不成立（本轮实测），不可用。

**顺带的事实（代码阅读，非实测）**：主 MATCH 是全 token 的隐式 AND
（`CjkTextTokenizer.matchExpression`），所以每个命中行必然含全部 token——3 个"额外 token"
标志对所有命中行恒为 +1（常数），实际决定排序的是 9 个列标志。重写保留了这 3 个常数项
（等价性纪律：不改公式）。

## 4. 重写内容（零 schema、语义等价）

- `RoomLibrarySearchStore.buildLibrarySearchRawQuery`（`RoomLibrarySearchStore.kt:94`，改
  `internal` 以便 EQP 门 EXPLAIN 生产原文）：
  - 每个排序标志改为命中集 join：`addRankingTerm`（`:130`）生成
    `LEFT JOIN (SELECT docid AS hit_docid FROM library_search_fts WHERE <column> MATCH ?) AS hit_<column>
    ON hit_<column>.hit_docid = library_search_fts.docid`，额外 token 用 `hit_token_<i>`
    整行 `MATCH ?`（`:158/:161`）；ORDER BY 里改用 `hit_*.hit_docid IS NOT NULL`。
  - 驱动顺序锁：`CROSS JOIN`（FTS 最左，`:203`），与批 1 口径一致（SQLite optoverview
    §7.1.2：planner 不重排 CROSS JOIN）。
  - **WHERE 子句、筛选片段、`snippet()`、次级排序（created/updated/entry_id）逐字未动**；
    只有绑定顺序变了（12 个排名短语在前，因为它们在 SQL 文本里更早出现）。
  - 调用点 `searchPagingSource` / `searchPage` 不再各自算短语，改为传 `tokens`
    （短语推导收进 builder，消除"两处推导漂移"）。
- 零 schema：没有加索引/表/迁移（`grep` 无新 DDL；`LibraryFtsCountPathQueryCopyContractTest`
  等既有守卫不受影响）。

## 5. 改后数字（生产路径，最终门那一轮——冻结源状态上）

```
S18 ranking fixture built: 10000 entries seed≈32–35s marker+bootstrap≈12–15s
S18 ranking benchmark tier=100 samples=[71, 78, 85, 60, 70, 80, 66, 69, 75, 74, 65, 70, 66, 64, 70, 87, 73, 78, 63, 67] p95=85ms budget=500ms
S18 ranking benchmark tier=1k  samples=[81, 69, 78, 77, 90, 86, 92, 83, 72, 82, 78, 76, 82, 81, 88, 87, 70, 125, 82, 81] p95=92ms budget=500ms
S18 ranking benchmark tier=10k (recorded, not gated) samples=[132, 156, 133, 135, 121, 130, 131, 126, 117, 118, 131, 144, 140, 131, 116, 125, 129, 135, 124, 142] p95=144ms
S18 ranking concurrent (10x, 1k tier, recorded not gated): latencies=[580, 760, 535, 957, 507, 931, 1055, 1256, 1166, 1216] mean=896ms
```

（同代码共 4 轮设备运行，全部 5/5 绿（最后一轮 53.9s）；区间：100 档 79–95ms、
1k 档 85–99ms、10k 档 139–167ms、并发均值 846–949ms。其中一轮因**测试期望**两处笔误
判红——100 档 offset=100 越界空页、内存夹具 id 自带 query token 把两行 stem 命中同时翻倍
——已修正，非产品缺陷。）

**并发口径辨析（复核用）**：`PerformanceGateTest.concurrentSearchPerformance` 本轮的
`latencies=[13534, 13143, 13121, 13436, 13145, 13398, 13216, 13555, 13531, 13341]` 与改前
同量级——因为那 10 路并发是**首次搜索**，先抢一个包含 FTS bootstrap（1 万条重分词 + 索引）
的写事务，墙钟由 bootstrap + 写锁串行化决定，不是排序成本；改前同一条探针也是 12–20s
（诊断文档 §2）。本报告 §2/§5 的并发数字用的是**已完成 bootstrap 的共享夹具**上的复测
（两轮同夹具对照），才是排序路径本身的口径。

| 档位 | 改前 P95 | 改后 P95（最终轮） | 变化 |
|---|---|---|---|
| 100（用户真实规模） | 146ms | **85ms** | 1.7× 更快，达标（预算 500ms） |
| 1k | 784ms | **92ms** | 8.5×，回到预算内 |
| 10k | 19208ms | **144ms** | 133× |
| 10 并发 × 1k（均值） | 10574ms | **896ms** | 11.8× |

### 5.1 改后 EXPLAIN（设备，10k 档；以下为原文节选，省略的视图展开行与批 1 相同）

```
MATERIALIZE library_catalog
SEARCH entry USING INDEX index_error_book_entry_status_updated_at_epoch_millis (status=?)
... （视图展开；其中 3 条 CORRELATED SCALAR SUBQUERY 属视图自身的相关子查询，
      与本批排序表达式无关——判据只拦"相关子查询里的 FTS 虚表访问行"）...
MATERIALIZE hit_stem_text
SCAN library_search_fts VIRTUAL TABLE INDEX 2:
MATERIALIZE hit_solution_text
SCAN library_search_fts VIRTUAL TABLE INDEX 4:
MATERIALIZE hit_knowledge_points
SCAN library_search_fts VIRTUAL TABLE INDEX 7:
MATERIALIZE hit_subject
SCAN library_search_fts VIRTUAL TABLE INDEX 5:
MATERIALIZE hit_options_text
SCAN library_search_fts VIRTUAL TABLE INDEX 3:
MATERIALIZE hit_chapter
SCAN library_search_fts VIRTUAL TABLE INDEX 6:
MATERIALIZE hit_tags
SCAN library_search_fts VIRTUAL TABLE INDEX 8:
MATERIALIZE hit_error_reason
SCAN library_search_fts VIRTUAL TABLE INDEX 9:
MATERIALIZE hit_formula_tokens
SCAN library_search_fts VIRTUAL TABLE INDEX 10:
MATERIALIZE hit_token_0 / hit_token_1 / hit_token_2
SCAN library_search_fts VIRTUAL TABLE INDEX 11:   （×3，额外 token 命中集）
SCAN library_search_fts VIRTUAL TABLE INDEX 11:   （主循环，MATCH 驱动）
SEARCH content USING INTEGER PRIMARY KEY (rowid=?)
SEARCH catalog USING AUTOMATIC COVERING INDEX (problem_revision_id=?)
SEARCH hit_stem_text USING AUTOMATIC COVERING INDEX (hit_docid=?) LEFT-JOIN
... （12 个命中集探测，全自动覆盖索引）...
USE TEMP B-TREE FOR ORDER BY
```

**排序表达式里再也不出现相关子查询**：12 个标志是"物化一次 + 每行 O(log n) 探测"。

## 6. 等价性与门（新增测试类）

新增 `core/database/src/androidTest/.../LibraryFtsRankingPathInstrumentedTest.kt`（5 用例，DEVICE 实测 5/5 绿）：

1. `rankingPlanUsesMaterializedHitSetsNotRowProbes`（行 60）：EQP 结构门——判据
   `rowLevelFtsProbesOf`（行 676）把"紧跟 `CORRELATED SCALAR SUBQUERY` 头的 FTS 虚表访问"
   判为逐行探测；对现场 live 计划必须为空，对**冻结红样例**（改前计划原文，行 782 起）必须
   判失败；另有正向断言"12 个 `MATERIALIZE hit_*` + `SEARCH hit_stem_text USING …
   (hit_docid=?)`"与"驱动角色"断言（FTS 主扫描在、`SCAN catalog` 不在）。
2. `userScaleAndThousandHitTiersStayWithinBudget`（行 111）：100/1k 档 P95 < 500ms×CI
   （门）；10k 档记录（打印）。
3. `concurrentThousandHitPagesAreRecorded`（行 156）：10 并发 × 1k 记录（不设墙钟门，
   2 核模拟器 + refreshProjection 串行化，同既有口径）。
4. `pageOrderMatchesPreChangeSqlAcrossTiersAndSorts`（行 182）：**同序主门**——改前 SQL
   冻结副本 vs 生产 live SQL 的 SQL 原文，在 14 组用例上逐列（全 `catalog.*` + snippet）
   逐行对照；再用生产端口路径复核 entryId 序。覆盖：100/1k 档 × {RECENTLY_UPDATED,
   RECENTLY_CREATED} × offset{0,20/100} × {全空, subject, 组合筛选}，以及 10k 档 offset{0,200}。
5. `tiedKeysAndColumnWeightsKeepTheExactPreChangeOrder`（行 265）：内存小夹具——同 rank 同
   `updated_at` 的并列键（必须 entry_id ASC 决胜）与逐列权重（stem 4 / solution 3 /
   knowledge 2 / subject 2 / options 1 / chapter 1，`updated_at` 与 rank 反序排列，
   排序失效必然给出不同序）的精确期望序；live 与改前 SQL 都必须给出这条序。

配 **JVM 守卫** `core/database/src/test/.../LibraryFtsRankingQueryContractTest.kt`：
钉住"改前参照物保持相关子查询形状（不得被顺手改成新形状）"与"live builder 保持命中集
join + 不得回退到 `AS ranked` 相关子查询"（先例 `LibraryFtsCountPathQueryCopyContractTest`）。

既有锚点（本批自验同时跑，**12/12 绿**，4m21s）：`PerformanceGateTest`（3C 批 1 迁移后的
用户可见搜索门；本轮 `library search page benchmark p95=53ms`，含 100 命中与中文有界命中两档）、
`LibraryCatalogTwoValueSortInstrumentedTest`（FTS 路径两值排序，4/4）、
`LibraryCatalogPagingInstrumentedTest`（FTS 路径筛选/分页，含 5 万行夹具 108.7s，2/2）。
批 1 的 `LibraryFtsCountPathInstrumentedTest` 与三份 @Query 副本守卫未受影响（本批未动 DAO）。

## 7. 采集命令

```
# 改前基线 + 候选形状探针（临时类，跑后删除）
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.S18RankingScratchProbeTest

# 本批正式门（新增类）
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.LibraryFtsRankingPathInstrumentedTest

# 既有锚点
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.PerformanceGateTest,com.tingyun.smartmistakebook.core.database.LibraryCatalogTwoValueSortInstrumentedTest,com.tingyun.smartmistakebook.core.database.LibraryCatalogPagingInstrumentedTest

# JVM 守卫
./gradlew :core:database:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.database.LibraryFtsRankingQueryContractTest"
```

数字取自 `adb logcat`（`System.out`）；设备为 `test_device` AVD（API 34）。

## 8. 待裁定项（登记，本批不擅自改）

1. **换 bm25/rank（改用户可见排序语义）——平台上不可用**：本项目索引是 **FTS4**
   （`FtsLibrarySearchContentEntity` 的 `@Fts4`），FTS4 没有 `bm25()/rank`。批 1 的设备基线
   功能探针实测该设备 **FTS5 不可用**（`fts5=false`，见
   `LibraryFtsCountPathInstrumentedTest.deviceSqliteBaselineIsRecorded` 的 KDoc 与
   `ftsModuleProbe("fts5")`），因此"换 bm25/rank"在当前 framework SQLite 上不是改一行 SQL，
   而是**换 SQLite 实现**（打包自定义 SQLite / 改用其他引擎）——超出本批章程，登记待裁定。
2. **排序语义的其它备选（都不等价，未做）**：
   - 去掉 9–12 个列标志，退回 `updated_at DESC`（最简单，但丢弃"题干命中优先"的现有体验）；
   - 只保留 stem 命中（1 个标志）；
   - 若将来引入 FTS5：bm25 与现有"按列加权命中"公式**不等价**（bm25 含词频/长度归一），
     切换是用户可见行为变更，需单独裁定与产品验收。
3. **count/分面与目录侧**：批 1 已修 FTS 侧；目录侧 facet 由 schema 63 复合索引修好。
   本批不需要物化任何新列（§4 零 schema），计划 §4 的"物化 mastery/标签列"条件未触发。

## 9. UNVERIFIED / 边界

- 只有 debug 构建 + 模拟器（2 核）读数；真机 / release 的数量级未测。CI 预算 ×4 后各档均有余量。
- 10k 档计时只记录未设门（用户真实规模是百级；避免把模拟器抖动变成 CI 红灯）；若协调方
  要求把 10k 也纳入门，可用同一测试类再开一条断言（4 轮实测 P95=139–167ms，余量 3×+）。
- 并发数字含 `refreshProjection()` 写事务串行化与 2 核调度效应，只作量级参考。
- 候选形状只在设备 SQLite 3.39.2 上验证；其它 API 版本的自动覆盖索引行为未逐一验证
  （结构门会在没有自动索引时通过正向断言"`SEARCH hit_stem_text USING`"暴露形态漂移，
  计时门兜底性能）。
- `IN_SET` 候选在筛选档下的"空结果"是本轮实测现象，未做根因定论（已弃用，不影响交付）。
