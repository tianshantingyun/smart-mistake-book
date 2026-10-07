# S18 尾 · FTS 搜索路径修正实施计划（library 计数 + 宽命中排序）（2026-10-06）

> 依据：`docs/research/2026-10-05-s18-count-path-prefinding.md`（完整诊断 + 移交建议）、
> `docs/research/2026-10-05-stage3c-part2-completion-record.md` §5-8（"留后续批次"）、规模审计 S18。
> 这是 3C 后半唯一"已完整诊断、零 schema 可试、在主线内"的剩余**用户可见**缺陷。
> 纪律依据：AGENTS §12.2（量化先行）+ 项目既有"逐条对照新旧 EXPLAIN，等价或更优才改，不许放宽语义"。

## 1. 关键事实基线（已核 file:line）

- **计数路径（病根）**：`LibraryFtsSearchDao.countSearch`（`core/database/.../dao/LibraryFtsSearchDao.kt`，
  `FROM library_search_fts JOIN library_search_content JOIN library_catalog WHERE MATCH …`）——设备 EXPLAIN：
  planner **先 `MATERIALIZE library_catalog`（视图展开）再逐行 `SCAN library_search_fts VIRTUAL TABLE INDEX 11:`
  回探**，MATCH 未作驱动约束。1 万行库：100 命中 **3.6s**、1 万命中 **9.9s**、并发均值 **24s**；
  生产消费者 = 搜索页"N 道题"与分页窗口（`LibraryViewModel.kt:265/302 → RoomLibraryCatalogRepository.totalCount
  → RoomLibrarySearchStore.searchCount:199-217 → countSearch`）。**FTS 分面三兄弟
  （searchSubjectFacets/searchSectionFacets/searchMasteryFacets）同形状、同病。**
- **排序路径（宽命中）**：`RoomLibrarySearchStore.buildLibrarySearchRawQuery`（`core/database/.../RoomLibrarySearchStore.kt:87-197`）
  的 `ORDER BY (9×(CASE WHEN EXISTS(… ranked.<column> MATCH ?)) + 额外 token 探测)`——**每命中行 9–12 次
  列级 FTS 探测**；1 万命中首屏（LIMIT 20）P95 **16.2s**，并发页均值 **16.0s**（百级命中时 searchPage 仅 171ms）。
- **对照已修**：catalog 侧 facet 已由 schema 63 复合索引修好（78870→69ms）；本批只动 FTS 侧。

## 2. 外部对标（本轮收集，来源已核）

- **`CROSS JOIN` 是官方文档化的"连接顺序锁"**（optoverview §7.1.2："SQLite chooses to never reorder the tables
  in a CROSS JOIN"；lang_select §2.2 同旨）；**内连接可交换**（"INNER JOIN、JOIN、`,` 完全可互换"）→
  强制顺序**语义等价**。生产实证：Signal-Android `SearchTable.kt`（AGPL、29.4k★、活跃）在 FTS5 上正是
  `FROM message_fts CROSS JOIN message …`。
- **FTS5 成本是编译期常量**（fts5_main.c 注释：cost=50000、estimated rows=cost/40、**不随 ORDER BY 变**）
  → 不要指望 ANALYZE；CROSS JOIN 是唯一锁。
- **Room 兼容**：`CROSS JOIN` 在两代 SQLite 语法里都在（无 androidx 测试背书 → **编译期实测**）；
  `WITH` 有官方集成测试背书；**`AS MATERIALIZED` 不在 Room 语法 token 表里**（不可用）。
- **排序路径无"免费等价"**：FTS5 的 `ORDER BY rank` 可被消费并配合 LIMIT 提前终止（官方明说 rank 比 bm25() 快），
  但换 bm25/rank = **改排序语义**（用户可见）；列命中位图无法从 `highlight/snippet` 反推（官方无
  matchinfo/offsets；自定义辅助函数在 framework driver 不可用）。
- 待验证（列入批 1 基线）：设备 `sqlite_version()` 与 `sqlite_compileoption_used('ENABLE_FTS5')`
  （AOSP Android.bp 未见 FTS5 开关，与项目实测矛盾，需实测记录）。

## 3. 批设计与验收

### 批 1 · 计数与分面路径驱动修正（零 schema，语义等价）
- 把 `countSearch` 与三个 FTS 分面查询改为 **`library_search_fts CROSS JOIN library_search_content …
  CROSS JOIN library_catalog …`**（FTS 最左、锁驱动顺序）；`WHERE MATCH` 与全部筛选/绑定逐字不变。
- **等价性**：新旧 SQL 在真实夹具上**结果逐列对照**（计数一致 + 三分面逐行一致；含全部筛选组合的抽样矩阵）。
- **门**：① EQP 结构断言（FTS 出现在**第一个循环**、`MATERIALIZE library_catalog` 消失或后置；不比对完整字符串）
  ② 计时（100 命中 ≤500ms×CI；1 万命中记录进报告）③ 真实 1 万行夹具（沿用 `PerformanceGateTest.insertTestData`）。
- 设备基线：`sqlite_version()` + FTS5 编译选项实测记录。

### 批 2 · 宽命中排序量化与（条件）等价重写
- 先量化：searchPage 在 **100 / 1k / 10k 命中**三档的 P95（含并发档复测）；给出"用户真实规模（百级）是否达标"的结论。
- 若千级即超预算 → 试**零 schema 等价重写**（把 9 个列命中标志从 ORDER BY 相关子查询里移出：
  CROSS JOIN 锁 + 命中集先物化/集合级列命中），**必须同序**（新旧结果逐行对照）——做不到同序就停。
- 若唯一出路是换 `bm25`/`rank` 排序（**改用户可见排序语义**）→ **不擅自改**：把数据与备选写进报告，登记待用户裁定。
- **门**：同批 1（EQP + 计时 + 等价对照）。

### 批 3 · 综合门 + 收尾
- 全量 JVM + DB 全套 + app 全套（**注意：DB 全套与 app 全套不得背靠背连跑**——3C 后半实测的锁争用；
  中间必须设备清场）+ R8 冒烟；完成记录 + 主线 3C 行余量更新 + 台账折入待办。

## 4. 版本与迁移预算
- **预期零 schema**（纯查询改写）；若发现必须加索引/表才能达标，停下报告（索引可考虑，物化不擅自做）。

## 5. 纪律与门
- 每批「实施 → 正式门 → 独立只读复核 → 修复轮 → 显式清单提交」；**门的 Gradle 与实施者自验严格串行**；
  不碰他线在飞文件。
- 执行方式：沿用**动态工作流**（每相位直挂 Agent/脚本、门显式分支 exit code——上轮教训）。

## 6. 风险与 UNVERIFIED
- `CROSS JOIN` 在 framework SQLite 3.39.2 与视图组合下的实际计划以**设备 EXPLAIN 为准**（官方无 FTS+视图组合示例）。
- Room `@Query` 对 `CROSS JOIN` 的解析需编译期实测（语法在，但无 androidx 测试背书）；若解析失败，
  退回 `@RawQuery`（本项目已有先例）。
- 宽命中排序可能无等价的零 schema 修法 → 按批 2 的"登记待裁定"路径走，不硬改语义。
- 并发数字含 2 核模拟器与 `refreshProjection` 串行化效应，只作量级参考。

## 7. 实施期口径裁定与登记（2026-10-06，协调方）

1. **EQP 判据口径迁移（字面 → 意图；批 1 实施者升级提问后裁定）**：计划 §3 批 1 原文写"`MATERIALIZE library_catalog`
   消失或后置"——设备 framework SQLite **3.39.2 不展平该视图**（3.50.4 才展平），字面项不可能成立。
   **门要拦的失败**是"catalog 当驱动 + 逐行回探 FTS"。裁定改按三合取判定：① FTS 扫描是**主程序第一个循环**
   （`MATERIALIZE` 作为一次性子程序列在最前不判失败——100 命中与 1 万命中计时同价，证明是一次性物化）
   ② 计划中**无 `SCAN catalog` 驱动** ③ catalog 只以 `SEARCH catalog USING … INDEX (problem_revision_id=?)`
   形态被探测；并把**改前旧计划钉为固定红样例**（新判据对旧计划仍红）。**这是口径迁移而非放宽门**；
   3.39/3.50 差异与红样例均在 `LibraryFtsCountPathInstrumentedTest` 的 KDoc/用例内留档。
2. **夹具来源变更**：计划写"沿用 `PerformanceGateTest.insertTestData`"，实装用
   `seedLibraryCatalogScale(10_000)`（1 万行齐全夹具，含 mastery/分类）——登记为实施期等价替换。
3. **设备基线实测（批 1 探针，仓内留档）**：设备 framework SQLite **无 FTS5 模块**（`fts5=false` 探针在
   `LibraryFtsCountPathInstrumentedTest.kt:59-68,371-373`）→ 本项目走 **FTS4**。因此"换 `bm25`/`rank`"
   （FTS5 专有）属**换库 + 用户可见排序语义变更**，批 2 **未擅动**，登记待用户裁定。
4. **批 2 实施期结论（登记）**：三档量化确认千级超预算 → 零 schema 等价重写落地（集合级命中集 LEFT JOIN
   取代逐行相关 EXISTS；排序语义一字未改）；改后 100=85ms / 1k=92ms / 10k=144ms / 并发均值=896ms。
   换 bm25/rank 见上条。改前基线的临时探针类已删除（报告自述），复现性受限——登记。
5. **复核登记（低危，处置：随收口修复轮或登记）**：测试 KDoc 数字对齐、DAO KDoc 漂移、排序守卫钉权重、
   计数门 EQP 补"非 0 索引"合取、10k 档补宽松计时门（以上进修复轮）；50k 规模未覆盖、设备原始证据未留仓、
   排序 EQP 门版本脆弱性、P95 两套口径并存（以上登记）。
