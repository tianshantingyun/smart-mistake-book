# S19 检索召回诊断（先诊断后修）· 2026-10-02

> 依据：Wave 4 W4-4 批（roadmap Wave 4 的 S19/S21 项）；审计原文
> `docs/research/2026-09-28-kernel-scale-precision-audit.md:102`（「KB 召回未索引谓词（无
> (subject, search_feature) 索引）｜已实测 p95 143ms / max 16.6s｜加复合索引；结果缓存」）。
> 本批按计划要求**先诊断后修，禁止盲加索引**，本文件是诊断的原始记录与结论。

## 1. 方法与口径

- 桌面 SQLite 3.50.4（Python 标准库），按**生产导出的 56.json DDL 逐字建表**（含复合
  PK 与两条显式索引），按 `ProblemOrganizationDao.kt:166-189` 的召回 SQL 逐字复用。
- 数据规模：4 科 × 9,000 节点（合计 3.6 万节点，对齐审计的 3.5 万量级），每节点 24 条
  特征（合计 86.4 万特征行），每科特征词表 4,000（n-gram 共享性近似），查询 128 个特征
  （`MAX_QUERY_FEATURES`），采样 24 次取 p50/p95。
- 口径声明：桌面 SQLite 与 Android 内置可能存在版本差异——本文件的用途是**计划形态与
  量级判断**；最终数字以真机仪器化为准（见 §5）。

## 2. 原始输出（关键项）

### 2.1 召回查询的基线计划（无 ANALYZE）

```
SEARCH feature USING COVERING INDEX sqlite_autoindex_knowledge_search_feature_1 (subject=? AND search_feature=?)
SEARCH node USING INDEX sqlite_autoindex_knowledge_node_1 (knowledge_node_id=?)
USE TEMP B-TREE FOR GROUP BY
USE TEMP B-TREE FOR count(DISTINCT)
USE TEMP B-TREE FOR ORDER BY
```

**结论：审计的「未索引谓词」前提不成立。** `knowledge_search_feature` 的复合主键
`(subject, search_feature, knowledge_node_id)` 生成了 SQLite 内部索引
`sqlite_autoindex_knowledge_search_feature_1`；`IN (expr-list)` 是官方承认的可索引谓词
（sqlite.org 优化器文档：等值 + IN 可用作复合索引前缀、无空档），查询实际以
**COVERING INDEX** 逐值探测——连 `knowledge_node` 的回表都只在 join 侧发生。

### 2.2 耗时分布（p50 / p95，n=24）

| 查询 | 基线 | ANALYZE 后 | 加显式索引 `(subject, search_feature)` 后 |
|---|---|---|---|
| 召回（128 特征） | **6.1 / 6.5 ms** | 6.3 / 6.8 ms | **28.5 / 29.6 ms（变慢 4.7×）** |
| `countReviewedKnowledgeNodesBySubject` | 2.7 ms（计划：`SCAN knowledge_node`） | — | — |
| `countIndexedKnowledgeNodesBySubject` | 9.9 ms（`COVERING INDEX (subject, knowledge_node_id)`） | — | — |

- **显式加索引反而显著变慢**：计划改为 `SEARCH feature USING INDEX probe_explicit (subject=? AND search_feature=?)`
  ——丢掉了 COVERING 属性（该索引不含 `knowledge_node_id`），每次探测要回表取 join 键；
  且它恰好是 PK 内部索引的**前缀**，正撞官方规则「不要让一个索引是另一个的前缀」。
  **本批据此决定：不加任何索引。**
- `ANALYZE` 后计划与耗时均无变化（无统计问题；此桌面场景下不引入 `PRAGMA optimize`）。
- `countIndexed` 的替代写法 `SELECT COUNT(*) FROM (SELECT DISTINCT …)` 实测 **535.4 ms**，
  比现写法差 54×——现形状已是该统计的最优解，不改。

### 2.3 每次召回的固定读数开销（新发现，原审计未点名）

`RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex` 在**每次召回**上都要跑
`readVersion` + `countReviewed`（全表扫描 knowledge_node）+ `countIndexed`（对本科目
~21.6 万特征行做 DISTINCT 计数）——桌面合计 **≈12.6 ms/次**（设备档只会更高）。
自愈本意是"检测索引落后于数据"，但知识库写入只发生在内容安装/退役路径，**写侧完全可以
负责索引健康**（本批 S21 已让安装期重建；退役路径原本就同步删特征行）。把该检查降为
"每进程每科一次"或由写侧失效驱动，是下一个可测的收益点——**本批只登记，不改**
（涉及多个写入口的失效接线，超出本批最小改动范围；数字见上，便于其后单独立项）。

## 3. 结论（S19）

1. **不添加 `(subject, search_feature)` 显式索引**（前提被证伪；实测变慢 4.7×）。
2. **不引入 ANALYZE/PRAGMA optimize**（当前无统计问题，实测零差异）。
3. **不改召回 SQL 形状**（现形状为 covering 探测 + 小结果集聚合，已是最优解之一）。
4. 143ms p95 的构成 = 召回本体（6ms 桌面档）+ 固定读数（12.6ms 桌面档）+ 稠密腿重排/父节点注入等；
   **16.6s max 的最可疑来源是「首访问整科重建」**——锚点缺失时的全科特征重建（3.5 万节点量级
   的提取 + 写入 + 事务提交），与"长尾只出现在首查"的特征吻合。**修复 = S21：把重建挪到内容
   安装期**（见 §4）；真机复测见 §5。

## 4. S21 实现（随本批代码同笔）

- 新 `KnowledgeSearchIndexBuilder`（core:database）：承载「整科重建 + 锚点最后写」的
  唯一实现，读时自愈与安装期预热共用（消灭"两处重建各写一遍"的漂移面）。
- `RoomKnowledgeContentReconciler.applyKnowledgeContentUpdate`：对**本轮节点有增改**的
  科目调用重建（安装期后台完成）；纯退役科目不需要（退役路径已同步删特征行，两个计数同降）。
- `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex`：整科重建分支改为委托同一构建器；
  正常路径下（锚点已由安装期写好）只剩版本读取与"只补缺"检查，语义不变。
- 测试：`KnowledgeSearchIndexInstallInstrumentedTest`（core:database）——安装完成后、**任何
  召回之前**，锚点已在位且两节点特征已建；重命名（upsert）后索引随之重建、锚点保持当前。

## 5. 真机复测（登记，待本批仪器化运行回填）

- 既有 `KnowledgeContextRetrievalInstrumentedTest.largeSubjectRecallRemainsBoundedOnRoom`
  （2 万点种子 + 完整 Room 召回路径 + p95 预算）在本批前后各跑一次取数。
- 新装路径：`KnowledgeSearchIndexInstallInstrumentedTest` 证明"无首访问重建"。
- 口径：首查长尾的因果（重建 vs 设备 GC/冷缓存）在桌面不可复现，真机数字若仍见长尾，
  按"证据定多少写多少"如实记录，不强行归因。
