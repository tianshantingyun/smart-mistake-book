# 阶段 3C 后半 · 规模项（S17 量化 + S9/S18 量化与按需物化 + S20 快照合并）实施计划（2026-10-05）

> 依据：`docs/research/2026-09-28-kernel-scale-precision-audit.md` S9/S17/S18/S20（`:87,100,101,103`）
> 与 3C 归属段（`:116`）；3C 前半完成记录（`docs/research/2026-10-04-stage3c-part1-completion-record.md`）；
> 4A 完成记录 §S17（性能未量化 UNVERIFIED，`:46`）。主线 3C 行"S9/S17/S18/S20 规模项"。
> **纪律依据**：AGENTS §12.2「新增必先指认它消灭的失败」→ **量化先行，物化只在实测超预算时做**。
> KF-31 仍不启（数据前置）；3D 硬门仍挡阶段 5（不在本批）。

## 1. 关键事实基线（本轮勘察，file:line 已核）

- **S17**：等价重写已落地（`ProblemDao.kt:85-224` 派生表 + LEFT JOIN；`observeActiveMistakes` `:240`）；
  等价测试 `ProblemDaoAggregationEquivalenceInstrumentedTest`；**但 `PerformanceGateTest.insertTestData`
  是空实现（`:250-253`）→ 三条计时断言在空库上恒真**；唯一 EXPLAIN 用例只针对 library `LIKE '%test%'`
  且断言弱。EQP 先例：`KnowledgeContextRetrievalInstrumentedTest.kt:350-357`、
  `ModelTaskRecentIndexMigrationInstrumentedTest.kt:50-74`（最严格形态）。`ciSlowRunner` 4×
  （`PerformanceGateTest.kt:280-285`；CI 传参 `.github/workflows/android-check.yml:198`）。
- **S9**：`MasteryOverviewDao.kt:109-127`（attempt × attribution JOIN + `COUNT(DISTINCT attempt_id)`，
  口径被 KDoc/测试钉住）；**仅"带词聚焦"的 MASTERY_READ 会调**（`RoomTutorToolRunner.kt:635-638`，
  audit"每次 MASTERY_READ"措辞不准）；无预聚合载体；`attempt_event` 无 `(learner_id, evidence_direction)` 索引。
- **S18**：`LibraryCatalogView.kt:6-71` 视图 3 相关子查询（mastery_id / chapter_labels / knowledge_labels）；
  `LibraryQueryDao` 6 条 instr + 3 条 facet；非空搜索已走 FTS（`RoomLibraryCatalogRepository.kt:19-158`）；
  **50k 夹具（`LibraryCatalogPagingInstrumentedTest`）无 mastery/分类/投影 → 只压到退化路径**。
- **S20**：`RoomBackedStudyExperienceRepository.kt:101/808-814`——每次事件全量重建 `StudyExperienceSnapshot`
  （`onMistakes`/`onPendingDraftCount`/`onLedgerChanged` + 写路径显式 publish）；**无 debounce/conflate**；
  后继映射每 drain 重读 `knowledge_node` 全表（`StudyProjectionDrainer.kt:58-64`；DAO `ProblemOrganizationDao.kt:324-328`）。
- **schema 现状 62**；S9/S18 物化需 **63**（物化载体：新表/新列 + 写侧维护 + 非破坏迁移）。

## 2. 外部对标（本轮收集；来源与待验证如实标注）

- **SQLite 无物化视图**（官方 CREATE VIEW 只读语义；无 MATERIALIZED 语法）；**视图内相关子查询不可展平
  → 物化为无索引临时表 + 全扫**（官方 optoverview §11/12）。物化=真表 + 写时 upsert 或 AFTER 触发器
  （生态实例：Signal 的 FTS5 external-content 表用 3 个 AFTER 触发器维护）。
- **EQP 官方不保证输出格式稳定**（"intended for interactive debugging only… may change"）→ 门禁用
  **结构不变量**（含目标索引名 / 无 `SCAN <大表>` / 无 `TEMP B-TREE FOR ORDER BY`），**不比对完整字符串**；
  社区先例（StreamVault / Project-Phoenix，许可 NOASSERTION，仅参考模式）；固定 SQLite 版本（BundledSQLiteDriver）才可复跑。
- **Facet**：给 facet 列建索引（Datasette）；FTS `MATCH` → rowid → `LIMIT` → 再 join 物化列（Signal 模式）。
- **Flow**：`conflate`/`collectLatest` 稳定、`debounce` 是 `@FlowPreview`；Room 失效是**表级**（官方），
  降噪靠下游 `distinctUntilChanged`；`stateIn(WhileSubscribed)` 持有最近快照（官方样例 NiA 的 mapLatest 管线）。
- **性能门方法论**：官方 CI 指南=**趋势而非 pass/fail**；JVM 计时噪声大 → **结构门为主、计时做宽松 backstop**
  （沿用项目 ciSlowRunner 4× 与"环境档"惯例）。
- 待验证：SQLDelight"视图非物化"文档 404；Room 2 无 `@Fts5`（Fts5 在 `androidx.room3`）；未找到官方
  "debounce + Room Flow"指南与 androidx 的 EQP 门先例（先例均为第三方）。

## 3. 批设计与验收

### 批 1 · S17 性能量化（零 schema）
- 实现 `PerformanceGateTest.insertTestData`（真实插入；先覆盖既有门所需数据——library 10k 行），
  让三条既有计时断言**不再是空转**；断言与预算沿用既有（搜索 P95 500ms / 首屏 500ms / facet P95 200ms ×CI）。
- 新增 S17 专属门：`observeActiveMistakes` **5000 行夹具** + ① EQP 结构断言（SEARCH 走索引、无
  `SCAN TABLE problems`、无 `TEMP B-TREE FOR ORDER BY`）② 计时 backstop（宽松、×ciSlowRunner）。
- 验收：空实现修复后既有断言在真实数据上仍过；新门对 S17 有非空信号（**可证伪**：把查询临时退化成
  相关子查询形态应红——做变红证明）。

### 批 2 · S9 + S18 量化，按需物化（schema 63 仅条件触发）
- **S9**：评估并（若有据）加 `attempt_event(learner_id, evidence_direction)` 索引；加**聚焦 MASTERY_READ
  负载门**（2k 负向作答 × attributions，p95 预算 + EQP）；**超预算才物化**（新聚合表、写侧同事务 upsert、
  schema 63）。
- **S18**：50k 夹具**补齐 mastery/分类/投影**（消除退化路径），对 page/count/facets 加 EQP + 计时门；
  **超预算才物化** mastery/标签列（新列/表 + 写侧维护，schema 63）；三处口径一致性测试保持。
- 验收：两项都出**量化报告**（数字 + EXPLAIN 原文 + 结论）；若触发物化则矩阵 1→63 绿、目录/FTS/facet
  口径一致、投影与重放输出不变（按 roadmap §0 判断是否 bump）。

### 批 3 · S20 快照合并 + 后继映射缓存（零 schema）
- 观察侧合并：`conflate`/`collectLatest` + `distinctUntilChanged`（避开 `@FlowPreview` 的 debounce）；
  **写路径显式 publish 保持同步**（`startOrResumeReviewSession`/`revealAnswer` 等时序不得变）。
- 后继映射缓存：缓存 `KnowledgeNodeSuccessors`（保持"一次 drain 内恒定"不变量），失效条件与
  `knowledge_node` 变化/合并退役写路径对齐。
- 验收：突发事件 → **一次重建**（计数用例）；写后立即可见不变；缓存命中/失效用例。

### 批 4 · 综合门 + 收尾
- 全量 JVM + DB 全套 + app 全套 + R8 冒烟；完成记录 + 主线 3C 行更新（后半完成 → ✅ 或如实部分完成）
  + 台账折入（若仍为他线在飞则先由完成记录承载）。

## 4. 版本与迁移预算
- **schema 63**（仅当 S9/S18 物化被实测触发；非破坏迁移 + 矩阵 1→63）。
- 算法版本：物化只改读侧存储与写侧维护，**预期不改投影/计划输出**；若触及输出逐位则按 roadmap §0 bump 并登记。

## 5. 纪律与门
- 每批「实施 → 正式门（全量 JVM + DB 仪器化冷启严格口径 + 定向仪器化）→ 独立只读复核 → 修复轮 → 显式清单提交」。
- **门的 Gradle 与实施者自验的 Gradle 严格串行**（3C 前半实测过一次并发构建污染）。
- 不碰他线在飞文件（`tools/kb_*`、`docs/agent-first-*`、`docs/superpowers/*`、知识包与侧车格式）。

## 6. 风险与 UNVERIFIED
- 量化结论依赖模拟器/CI 环境：沿用 ciSlowRunner 4× 与"环境档"惯例，**不把抖动当回归、不把宽松门当性能背书**。
- S9/S18 若触发物化：新表/列 + 写侧维护面大（事务性/重放/口径一致性），需专门评审轮。
- S20 合并不得改变写后立即可见语义；后继映射缓存的失效传播需覆盖"合并退役"写路径。
- KF-31 仍不启；3D 硬门仍挡阶段 5（D-0 属内核线、与知识包侧车格式相邻，需与 KB 线协调写入窗口）。
