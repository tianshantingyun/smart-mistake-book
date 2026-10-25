# 混合检索候选域并集与缺腿归一化：先例、冲突与交叉验证

> 检索/成稿：2026-10-25/26。范围：外部官方文档、GitHub 源码、论文，以及仓库内**既有**实测产物。
> 本报告**不修改代码、不重跑检索臂**；所有本地读数都来自既有产物，外部资料均给链接。
>
> 术语约定：本文用 **K** 表示“每腿候选深度”（本例 64）；RRF 的 **k** 是平滑常数（常见 60/50/2）。
> 二者不是一回事，后文会反复区分。
>
> 标注约定：具体默认值/实现细节若只引一处外部来源，按「单一来源，待验证」处理；核心结论均给
> ≥2 个独立来源，来源冲突在 §3.3 并列呈现。本地代码/产物是“本仓库事实”，不冒充外部先例。

## 0. 结论摘要

1. **并集是主流。** Weaviate 源码明确写 “The two sets are unioned, never intersected”；Qdrant 的融合集合 `D` 定义为各分支结果的并集；Lucene、Elasticsearch、Vespa、OpenSearch、pgvector、Supabase、ParadeDB、sqlite-vec 示例都采用并集（去重由文档/行 id 或查询引擎保证）。
2. **每腿独立 min-max 是主流。** Weaviate `relativeScoreFusion`、OpenSearch 的 per-clause normalization、Bruch 等人的论文都按每条腿各自的分数集归一化；缺腿文档不进入该腿的统计。
3. **缺腿给 0 是主流生产做法。** OpenSearch 明确写：“A document that one clause did not return contributes `0` to the numerator but still counts in the denominator”；Supabase/pgvector/sqlite-vec 用 `COALESCE(..., 0)`；Qdrant/Elasticsearch/Weaviate 的缺腿项不贡献。**Bruch 论文是异类**：它写的是“compute its missing score”，即补真实分数，而不是填 0。
4. **“缺腿=0”有结构代价。** 当 α=0.5 时，dense-only 候选的融合分上限是 **0.5**；词面腿拿满分的候选只要 dense_norm>0，就严格高于它。OpenSearch 文档的 “normalized score halved” 是同一现象的另一种说法。这不是实现 bug，而是口径的数学后果。
5. **本地已有同口径实测，且结论为“无增益”。** 批次18 lever-1 实现的正是“词面 512 ∪ dense-only 64 + α=0.5 + 缺腿 0”，设备/JVM 均为 **100/129、MRR 0.622868**，与 A0 逐位相同，已回滚。探针显示 4 个 absent 目标的**融合后名次**是 26 / absent / 17 / 26（它们的 dense 名次是 1 / 336 / 2 / 7）。
6. **K=64 不是瓶颈。** 3 个目标的 dense 名次 ≤7，K=64 早已覆盖；第 4 个同科 182 / 全科 336，扩到能覆盖它的 K 代价与收益不成比例。失败发生在融合口径，不在候选深度。
7. **α=0.5 可以保留，但不是“最优”的证据。** 等权是多个系统的默认（Supabase、Qdrant、OpenSearch 省略 weights 时），但 Weaviate 服务端默认 0.75，Bruch 的实验在 α∈[0.6,0.8] 持续改善。本地 α=0.7 设备臂因零漂移装置缺陷**不判读**，离线诊断 α=0.6/0.7 反而高于 0.5；因此“0.5 是保守默认”，不是“0.5 已被证明最优”。
8. **建议。** 保留 per-leg min-max 与“缺腿不入域”；把“缺腿=0”、α、K 拆成独立预注册臂；若要救 dense-only 目标，优先评估“补真实词面分 / 独立救援配额 / 提高 α”之一，而不是把同一个 K=64 臂再跑一遍。

## 1. 本地写死口径（对照基线）

本报告要交叉验证的四条口径，在仓库里都有明确落点：

| 口径 | 代码/文档落点 | 事实 |
|---|---|---|
| α=0.5 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseFusion.kt:21` | `const val ALPHA = 0.5` |
| 每腿独立 min-max | `DenseFusion.kt:24-31`；域定义见 `DenseFusion.kt:10-14` | `minMax()` 对传入的每条腿 map 独立做；上界=下界时全体 0 |
| 缺腿给 0 且不入域 | `DenseFusion.kt:52-57` | `denseNorm[nodeId] ?: 0.0` / `lexicalNorm[nodeId] ?: 0.0`；缺项不在该腿 map 中，故不参与该腿 min-max |
| 稠密域 = 该科全部原子节点 | `DenseRecallAssembly.kt:37`；`DenseVectorAsset.kt:45-56` | `nodeScores(query, subjectKey)` 返回该科全部有向量的原子节点，注释明确“返回的键集同时就是融合时稠密腿的 min-max 域” |
| 词面域 = SQL 召回集 | `DenseRecallAssembly.kt:38`；`dao/ProblemOrganizationDao.kt:181-210` | `lexicalScores = candidates.associate { ... }`；候选来自 `COUNT(DISTINCT feature.search_feature)` 的 `GROUP BY`，`LIMIT :limit` |
| K=64 提案 | `docs/research/2026-10-22-dense-union-prereg.md:14` | “返回「词面 top-512 ∪ dense top-K」并集（K 写死，如 64）” |
| 当前端口“只改次序、不改成员” | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DenseRecallReranker.kt:16-21` | 并集臂要改成员，必须显式改契约；既有 `order()` 的排列校验会拒绝新增成员 |

> 关键点：**“缺腿=0”与“缺腿不入域”是两件事**。前者是分数取值，后者是 min-max 统计范围。
> 外部系统大多两者一致（缺腿项既不算分也不进统计），但“常数域/单元素域”的处理并不统一（见 §3.3）。

## 2. 先例 A：候选域并集、成员扩张与去重

### 2.1 系统对照

| 系统 | 并集/成员扩张方式 | 去重键 | 每腿深度 | 缺腿处理 | 来源 |
|---|---|---|---|---|---|
| **Weaviate** | 稀疏与稠密两路结果**并集，不交集**；融合在 `map[UUID]` 上累加 | 对象 UUID | 两路各自的结果集；融合只看到各自返回的集合 | 缺腿项不贡献（等价该腿 0） | 源码注释 “The two sets are unioned, never intersected” [searcher.go](https://github.com/weaviate/weaviate/blob/main/usecases/traverser/hybrid/searcher.go#L75)；融合实现 [hybrid_fusion.go](https://github.com/weaviate/weaviate/blob/main/usecases/traverser/hybrid/hybrid_fusion.go#L93-L145) |
| **Qdrant** | 每个 prefetch 是一个子查询，各自 `limit`；融合集合 `D` = 各分支结果并集 | point id | prefetch 各自的 `limit`，且必须 ≥ 主查询 `limit + offset` | 缺分支项不加项、不惩罚 | 官方文档 [Hybrid Queries](https://qdrant.tech/documentation/concepts/hybrid-queries/) |
| **Vespa** | `or` 查询显式取并集；`and` 取交集；`rank(...)` 只用第一操作数检索、其余提供特征 | 文档 id（搜索引擎天然唯一） | `targetHits` 是提示，官方示例 `totalCount≈90 > 10+10` | OR 会包含只命中一路的文档；`rank` 只保留第一操作数命中的文档 | 官方教程 [Hybrid Search](https://docs.vespa.ai/en/learn/tutorials/hybrid-search.html) |
| **Lucene** | `TopDocs.rrf(topN, k, TopDocs[])` 把多个结果数组合并 | `(shardIndex, doc)` | 调用方决定每个 `TopDocs` 的大小 | 缺列表项不贡献；只对出现的列表累加 | 源码 [TopDocs.java](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/search/TopDocs.java#L372-L407) |
| **Elasticsearch** | `rrf` retriever 合并多个 child retriever 的结果集 | 文档 id | 每个 child 的 `rank_window_size`，默认等于 `size` | 文档不在某结果集时，该集合的 `if d in result(q)` 不成立，加 0 | 官方文档 [Reciprocal rank fusion](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion) |
| **OpenSearch** | `hybrid` 查询 + normalization processor / score ranker 合并各 clause 结果 | 文档 id | 每个子查询的 `size`；官方建议 100–200（≤10M 文档） | 缺腿项分子 0、分母仍计；且不进该 clause 统计 | 官方文档 [Normalization processor](https://docs.opensearch.org/latest/search-plugins/search-pipelines/normalization-processor/)；[RRF](https://docs.opensearch.org/latest/vector-search/ai-search/hybrid-search/rrf/) |
| **pgvector** | 本身不提供融合；README 明确“与 Postgres 全文检索一起用 hybrid”，并给 RRF/交叉编码器示例 | 行 id | 示例两腿各 `LIMIT 20` | 示例 `COALESCE(..., 0)` | [pgvector README §Hybrid Search](https://github.com/pgvector/pgvector#hybrid-search)；[官方 RRF 示例](https://github.com/pgvector/pgvector-python/blob/master/examples/hybrid_search/rrf.py) |
| **Supabase** | 两个 CTE 各自检索，`FULL OUTER JOIN` 合并 | 行 id | `least(match_count, 30) * 2` | `COALESCE(..., 0)` | [官方文档](https://supabase.com/docs/guides/ai/hybrid-search)；源码 [hybrid-search.mdx](https://github.com/supabase/supabase/blob/master/apps/docs/content/guides/ai/hybrid-search.mdx#L121-L127) |
| **ParadeDB** | 两个分支各自 `ORDER BY ... LIMIT 20`，`UNION ALL` 后 `GROUP BY id` 求和 | 行 id | 两腿各 20，最终 5 | 缺分支项不贡献 | 官方文档 [RRF](https://www.paradedb.com/docs/reference/hybrid/rrf) |
| **sqlite-vec** | 不内置融合；官方 hybrid guide 在访问时是**空文件**；示例 notebook 给了两种写法 | 行 id | 示例两腿各 `k=10` | RRF 写法 `COALESCE(..., 0)` | 空 guide：[hybrid-search.md](https://github.com/asg017/sqlite-vec/blob/main/site/guides/hybrid-search.md)；示例 [3_search.ipynb](https://github.com/asg017/sqlite-vec/blob/main/examples/nbc-headlines/3_search.ipynb) |
| **sqlite-vss** | 不提供 hybrid；README 只讲 KNN，`fts5` 仅作为 API 类比 | — | — | — | [sqlite-vss README](https://github.com/asg017/sqlite-vss) |

### 2.2 并集先例的共性

1. **并集而非交集**：Weaviate 源码注释最直白；Qdrant 的 `D` 定义、Vespa 的 `or`、Supabase 的 `FULL OUTER JOIN`、ParadeDB 的 `UNION ALL` 都是同一语义。
2. **主键去重**：Weaviate `map[UUID]`、Lucene `(shardIndex, doc)`、Qdrant point id、SQL 的 `FULL OUTER JOIN`/`GROUP BY id`。sqlite-vec 的示例还专门警告：**只 `UNION ALL` 不去重会出重复结果，需 `DISTINCT`/`GROUP BY`**（[notebook 原文](https://github.com/asg017/sqlite-vec/blob/main/examples/nbc-headlines/3_search.ipynb)）。
3. **每腿先截断，再合并**：所有系统都先对每条腿取 top-N，再融合；没有系统把“全库所有文档”拿来做并集。差别只在 N 取多大（见 §5 K=64）。
4. **缺腿项在并集中保留**：并集的意义就是让只命中一路的文档也能进入融合；是否给它分数、给多少，是下一节的问题。

## 3. 先例 B：缺腿归一化

### 3.1 三条路线

| 路线 | 代表 | 缺腿处理 | 是否回避分数归一化 |
|---|---|---|---|
| **Rank-based（RRF）** | Cormack 2009；Elasticsearch、OpenSearch、Qdrant、Vespa、Weaviate rankedFusion、Supabase/pgvector/sqlite-vec/ParadeDB 示例 | 缺列表项“不贡献”，不做分数归一化；文档只在一个列表出现时只拿一份 reciprocal rank | 是 |
| **Score-based（min-max 凸组合）** | Weaviate relativeScoreFusion；OpenSearch normalization processor；Bruch 论文 | 缺腿项通常 0 或不贡献；**不入该腿 min-max** | 否；依赖每腿分数分布 |
| **补真实缺腿分** | Bruch 等人的论文（异类） | “When a document ... is not present in one of the top-k sets ... we compute its missing score (i.e., f_o(q,d)) prior to fusion.” | 否；但要求能对任意文档调用该腿的评分函数 |

### 3.2 缺腿处理对照

| 来源 | 缺腿分数 | 是否进该腿 min-max | 常数/单元素域 | 关键原文/代码 |
|---|---|---|---|---|
| **OpenSearch** | 分子 0，分母仍计（等价该腿 0） | **否**：统计只来自该 clause 自己的结果 | **1.0**（全等或单文档时） | “Documents that the clause did not return contribute nothing to its statistics.”；[normalization-processor.md:23](https://github.com/opensearch-project/documentation-website/blob/main/_search-plugins/search-pipelines/normalization-processor.md#L23)；缺腿 halved 见 `:194`；常数域见 `:33` |
| **Supabase** | `COALESCE(..., 0)` | 否（RRF 不做 min-max） | — | [hybrid-search.mdx:126-127](https://github.com/supabase/supabase/blob/master/apps/docs/content/guides/ai/hybrid-search.mdx#L126-L127) |
| **pgvector 官方示例** | `COALESCE(..., 0)` | 否（RRF） | — | [rrf.py:40-43](https://github.com/pgvector/pgvector-python/blob/master/examples/hybrid_search/rrf.py#L40-L43) |
| **sqlite-vec 示例** | `COALESCE(..., 0)` | 否（RRF） | — | [3_search.ipynb RRF cell](https://github.com/asg017/sqlite-vec/blob/main/examples/nbc-headlines/3_search.ipynb) |
| **Qdrant** | 缺分支项不加项 | 否（每分支独立统计） | DBSF 全等/单点 → **0.5** | “$D$ the set of points across all results”；DBSF “If all returned scores are identical (or only one point is returned), DBSF emits 0.5 rather than dividing by zero.”；[Hybrid Queries](https://qdrant.tech/documentation/concepts/hybrid-queries/) |
| **Elasticsearch** | 缺列表项不贡献 | 否（RRF） | — | RRF 公式与 `if d in result(q)`；[RRF 文档](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion) |
| **Weaviate** | 缺腿项不进 `map`，不贡献 | 否（每路结果集独立 min/max） | 全等 → **weight**（常数贡献） | [hybrid_fusion.go:93-145](https://github.com/weaviate/weaviate/blob/main/usecases/traverser/hybrid/hybrid_fusion.go#L93-L145)：“If all scores are identical min and max are the same => just set score to the weight.” |
| **Lucene** | 缺列表项不贡献 | 否（RRF） | — | [TopDocs.java:394-407](https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/search/TopDocs.java#L394-L407) |
| **Bruch 论文** | **补真实分数** `f_o(q,d)` | 不属于“缺腿”情形（它把文档补评后再融合） | — | [arXiv:2210.11934](https://arxiv.org/abs/2210.11934)，§3.1：“we compute its missing score ... prior to fusion” |
| **本仓库** | 0 | **否**（`?: 0.0`，且不在 map） | **0.0**（`high <= low`） | `DenseFusion.kt:52-57`、`:32` |

### 3.3 冲突点（必须并列呈现，不能只选一边）

**冲突 1：缺腿“填 0”还是“补真实分”？**

- 生产系统主流是“0 / 不贡献”：OpenSearch、Supabase、pgvector、sqlite-vec、Qdrant、Elasticsearch、Weaviate 都能落到这个语义。
- Bruch 论文明确写“compute its missing score”，即在融合前对并集中的文档补算该腿的真实分数。这要求该腿的评分函数能对任意文档求值；对词面腿通常可以（BM25/计数），对稠密腿则需要该文档有向量。
- 两者不是“谁对谁错”，而是**信息假设不同**：填 0 假设“没召回≈该腿最差”；补真实分假设“没进 top-k 不等于该腿差，应该按真实分算”。本地词面腿是 `COUNT(DISTINCT feature)`，对 dense-only 候选补真实词面分在数据层可行（同一张 `knowledge_search_feature` 表），但性能与口径影响未验证。**若把补分项写入词面域**，会改变既有成员的归一化；**若只补分不入域**，则要明确“域外分数”是否截断到 [0,1]，否则会破坏 min-max 的边界语义。必须单独预注册。

**冲突 2：常数/单元素域给 0、1、weight 还是 0.5？**

- 本仓库：`high <= low` → 全体 **0.0**（`DenseFusion.kt:32`）。
- OpenSearch：全等或单文档 → 全体 **1.0**（`normalization-processor.md:33`）。
- Weaviate：全等 → 每个文档拿 **weight**（常数贡献，`hybrid_fusion.go:131-134`）。
- Qdrant DBSF：全等/单点 → **0.5**（官方文档）。
- **排序等价性**：如果整条腿对所有文档都是常数，那么 0/1/weight/0.5 只是给所有文档加同一个常数，排序不变；所以这些差异在“整条腿全等”时不影响名次。**但“单元素域 + 其余文档缺腿”不等价**：本仓库把唯一有词面分的文档归一成 0（与缺腿同分），OpenSearch 给它 1.0（明确奖励唯一命中），DBSF 给 0.5。此时会改变名次。详见 §5.2 的算式与本地探针。

**冲突 3：RRF 还是分数凸组合？**

- Weaviate 从 v1.24 起默认 **relativeScoreFusion**（min-max 分数融合），把 rankedFusion（RRF）作为可选；官方文档承认 relativeScoreFusion “retains more information”。
- Elasticsearch 的 hybrid `rrf` retriever 默认就是 RRF；OpenSearch 同时提供 score-based normalization 与 RRF。
- OpenSearch 官方 benchmarks：在六个 BEIR 数据集上，RRF 平均比 score-based hybrid **低 3.86% NDCG@10**，但 RRF 对分数分布/异常值更稳健（[RRF 文档](https://docs.opensearch.org/latest/vector-search/ai-search/hybrid-search/rrf/)）。
- Bruch 论文：凸组合（TM2C2，α=0.8）在 9 个数据集上 NDCG 均优于 RRF；并称 “RRF [is] sensitive to its parameters”。
- 结论：**没有单一赢家**。分数融合在分布可比、可调参时更强；RRF 在分数不可比、分布漂移、异常值多时更稳。本仓库选择 min-max 凸组合是有先例的，但不能把“RRF 更流行”当作反证，也不能把“凸组合更强”当作普适结论。

**冲突 4：RRF 的 k 与候选深度 K 的常数不统一。**

- Elasticsearch/Azure/OpenSearch/ParadeDB 默认 k=60；Supabase 默认 50；Qdrant 默认 2；Cormack 原论文固定 60。
- 这说明“60”不是定理，只是经验默认；本仓库不用 RRF，故不受此影响，但报告读者不应把 RRF 的 k=60 与我们的 K=64 混为一谈。

## 4. 先例 C：域扩张对既有候选分数的影响

### 4.1 外部“踩坑记录”

| 来源 | 现象 | 结论 |
|---|---|---|
| **Elasticsearch RRF** | `rank_window_size` 改变，结果顺序可能改变；“If `rank_window_size` changes, then the order of the results might change as well” | 候选窗口是融合语义的一部分，不能把它当纯性能参数 |
| **Qdrant DBSF** | 归一化用每个分支 prefetch top-k 的均值/标准差；官方建议 “Increase the prefetch limit if you see unstable rankings” | 分支候选集大小会改变归一化，进而改变名次 |
| **Qdrant issue #10640** | 嵌套 fusion 在分片内先融合，各分片只看到自己的候选域；“The merged scores aren't on a common scale”；相同数据在 1/2/4 分片上得到不同分数/不同点 | 融合域必须是全局一致的；分片局部融合会破坏可比性（[issue](https://github.com/qdrant/qdrant/issues/10640)） |
| **Qdrant issue #10611** | DBSF 对两个相同的 Euclidean 检索结果做融合，输出顺序反转；“Fusing two identical ranked inputs should preserve their ranking” | 常数/全等域的归一化必须保持方向与幂等，否则会反转（[issue](https://github.com/qdrant/qdrant/issues/10611)） |
| **OpenSearch** | 每个 clause 只用自己返回的结果做统计；“Documents that the clause did not return contribute nothing to its statistics”；子查询 size 建议 100–200；normalization 依赖当前结果集，流式数据需重校准 | 域 = 该 clause 的返回集；扩大/缩小返回集就会改变分数；RRF 不需要重校准 |
| **Weaviate relativeScoreFusion** | min/max 来自每个结果集；“highest becomes 1, lowest becomes 0” | 结果集边界就是归一化域；改 limit 会改分数 |
| **Bruch 论文** | min-max 在 union set 上做；“minimum score in the retrieved set is replaced with minimum feasible value” 的理论 min-max 更稳；并指出 min-max 是 “de facto method” | 观测 min/max 依赖候选集；用理论下界可减少候选依赖；方差为 0 时可跳过融合 |

### 4.2 对本仓库口径的推导

按 `DenseFusion` 的**字面契约**：

- 稠密腿域 = `nodeScores()` 的键集 = 该科全部原子节点；并集臂只扩大 `orderDomain`，不改变 `denseScores` 的键集。
- 词面腿域 = 传入的 `lexicalScores` 键集 = 词面召回候选；dense-only 候选若**不写入** `lexicalScores`，词面域不变。
- 因此：**既有成员的每腿原始分与归一化域都不变，融合分不变；并集只是插入新成员。**

这不是实测，而是从代码定义推出的性质。批次18 lever-1 的实现注释也明确写了这一点：“两腿的 min-max 域都不变……词面候选之间的相对次序与 Stage-3 逐条相同”（`build/agent-batch18/lever-1/lever-1-diff.patch:72-99`）。

**但当前端口的形状有一个陷阱。** `DenseRecallAssembly.kt:38` 是：

```kotlin
val lexicalScores = candidates.associate { it.knowledgeNodeId to it.lexicalScore.toDouble() }
```

如果并集臂把 dense-only 候选也塞进 `candidates`，并给它们 `lexicalScore = 0`，那么 0 会进入 `lexicalScores` 的键集，**词面 min-max 域就被悄悄扩大了**。这会改变所有既有成员的分，而不只是新增成员。用一个算式说明（本报告实跑 Python 验证）：

- 旧域：词面分 `{z:1, a:5}`，min=1,max=5 → 归一 `{z:0, a:1}`；稠密归一 `{z:1, a:0}`；α=0.5 → 融合 `{z:0.5, a:0.5}`，按 node_id 升序 `a, z`。
- 若把 dense-only 的 `C:0` 写进词面域：min=0,max=5 → 归一 `{z:0.2, a:1, C:0}`；融合 `{z:0.6, a:0.5, C:0}` → 顺序变成 `z, a`。
- **结论：既有成员的分数从 0.5/0.5 变成 0.6/0.5，名次也翻转了。** 这就是“域扩张改变既有分数”的真实机制。

因此，落实“缺腿=0 且不入域”必须有**类型级/结构级**保证，不能只靠注释：

- 让 `DenseRecallCandidate.lexicalScore` 变成可空（`Int?`），或在 reranker 端口把“词面候选”和“dense-only 追加项”分开传；
- `lexicalScores` 只从**非空**词面分的候选构建；
- 增加回归测试：给定固定的两腿 map，向 `orderDomain` 追加 dense-only 成员，**既有成员的融合分与相对次序必须逐位不变**。

### 4.3 K 的语义还有一个本地不一致

预注册写的是“dense top-K”（通常理解为**全局稠密名次前 K**），但批次18 的实现 `DenseFusion.denseOnly()` 是：

```kotlin
.filter { it.key !in excluded }   // 先排除词面候选
.sortedWith(descending dense)
.take(limit)
```

也就是“**排除词面候选后，再取 K 个**”，即 dense-only 追加预算，不是全局 top-K。补丁注释写“先取 top-K 再去重”，与代码顺序相反，注释自相矛盾（`lever-1-diff.patch:23-39`）。这会导致：如果词面集里有很多高稠密分节点，实现会继续向更深的名次取满 K 个，实际全局深度 > K。对本报告的 4 个目标（dense 名次 1/2/7/336）不影响结论，但复跑前应把 K 的语义钉死：**全局名次上限，还是追加项预算**。外部先例（Supabase/Qdrant/Weaviate/pgvector 示例）用的都是“每腿全局 top-N 再合并”，不是“扣掉重叠后再取 N”。

## 5. 对写死口径的交叉验证

### 5.1 逐项判定

| 口径 | 外部支持 | 冲突/风险 | 本地证据 | 判定 |
|---|---|---|---|---|
| **α=0.5** | 等权默认：Supabase `full_text_weight=1, semantic_weight=1`；Qdrant 权重默认 1；OpenSearch 省略 weights 时等权；Azure RRF 各 child 等权 | Weaviate 服务端默认 **0.75**；Bruch 实验 α∈[0.6,0.8] 持续改善；分数融合对 α 敏感 | 本地 α=0.7 设备臂因零漂移装置缺陷不判读；离线诊断 α=0.6→0.7674（99/129）、α=0.7→0.7829（101/129），均高于 α=0.5 的 0.7597（非判读） | **可保留，但存疑**：0.5 是保守默认，不是最优证据 |
| **每腿独立 min-max** | Weaviate relativeScoreFusion；OpenSearch per-clause；Bruch 的 φ_mm/φ_tmm | 若把域定义成 union，域会随成员变；需固定每腿域 | 批次18 实现明确“两腿 min-max 域不变”，词面候选相对次序不变 | **支持**（前提是域固定且缺腿不入域） |
| **缺腿=0 且不入域** | OpenSearch 明确“分子 0、分母仍计、不进统计”；Supabase/pgvector/sqlite-vec `COALESCE 0`；Qdrant/ES/Weaviate 缺腿不贡献 | Bruch 论文补真实分；常数/单元素域 0 vs 1 vs weight vs 0.5；单元素词面域会把唯一命中归一成 0 | 批次18 探针：4 个 absent 目标融合名次 26/absent/17/26，dense 名次 1/336/2/7；dense-only 候选确实进过 top-5（`totalExtrasInTop5=3`，`bestExtraRank=3`），但没救到目标 | **支持机制，效果存疑**：口径是主流且自洽，但对“救 dense-only 目标”有结构上限 |
| **K=64** | 外部无“必须 64”的依据；常见范围：Supabase 2×final（top-5→10）、ParadeDB 20 for final 5、OpenSearch 100–200、Azure text 50/vector k、ES `rank_window_size` 默认=size | K 的语义（全局 top-K vs dense-only 预算）在本地实现里不一致；K 与 RRF k 易混 | 3/4 absent 目标 dense 名次 ≤7；26/29 MISS ≤64；第 4 个同科 182 / 全科 336；按全局语义覆盖它需 K≥182（同科）或 ≥336（全科），按 dense-only 预算语义还要加上被词面占用的名额——代价与收益不成比例 | **保留，但非瓶颈**：64 足以做结构召回；失败在融合口径 |
| **域扩张不改变既有成员分数** | 条件成立：OpenSearch/Weaviate/Qdrant 都按每腿自己的结果集归一；若域固定，新成员不改变旧成员的归一化 | 若实现把 dense-only 的 0 写进词面域，会改变；当前端口 `candidates.associate` 没有类型级防护 | 批次18 实现正确保持了两腿域不变；但聚合指标仍与 A0 逐位相同 | **设计支持，实现需测试锁定** |
| **dense-only 目标能进 top-5** | 并集机制本身支持；Weaviate/ES/OpenSearch 都允许单路文档进入融合 | α=0.5 + 缺腿=0 的结构上限：dense-only 最高 0.5；词面满分且有 dense 分的候选 >0.5 | 批次18 探针：目标融合名次 26/absent/17/26；`extrasInTop5=0`（对 4 个目标） | **不支持“K=64 就能救”的预期**；需改融合策略 |

### 5.2 结构上限的算式（本报告实跑验证）

融合式：

```text
fused = α · dense_norm + (1-α) · lex_norm
```

取 α=0.5，dense-only 候选 `lex_norm=0`：

```text
fused_dense_only = 0.5 · dense_norm ≤ 0.5
```

词面腿拿满分的候选 `lex_norm=1`，只要 `dense_norm>0`：

```text
fused_lex_max = 0.5 · dense_norm + 0.5 > 0.5
```

所以：**dense-only 候选在 α=0.5 下不可能严格超过“词面满分且稠密分非零”的候选**；它通常只能争第 2–5 名，且必须挤掉足够多的双路候选。若词面满分候选的 `dense_norm` 恰好为 0，则 dense-only 最高分与它**并列 0.5**，按 node_id 决胜——这是唯一的“并列第一”窗口。OpenSearch 的 “a document matching only one of the two clauses has its normalized score halved” 是同一事实的另一种表述。

**单元素词面域的例子**（本报告实跑）：

- 只有 A 有词面分，B 没有；A 的 `lex_norm` 在本仓库是 0（`high<=low`），OpenSearch 风格是 1.0。
- A 的 dense_norm=0，B 的 dense_norm=0.6，α=0.5：
  - 本仓库：A=0.0，B=0.3 → **B 赢**；
  - OpenSearch 风格：A=0.5，B=0.3 → **A 赢**。
- 这不是“常数域无所谓”的场景：唯一命中的词面证据被本仓库的 0 归一化抹掉了。外部文档没有裁决“单元素域应该给 0 还是 1”，这是一个需要本地预注册决策的开放项。

**复算记录（本报告实际执行）**：用 Python 对上述两组算式求值，关键输出：

- 域扩张：`old fused {'z': 0.5, 'a': 0.5} order ['a', 'z']`；`new fused {'z': 0.6, 'a': 0.5, 'C': 0.0} order ['z', 'a', 'C']`
- 单元素域：`ours A,B 0.0 0.3 winner B`；`OpenSearch-style A,B 0.5 0.3 winner A`

### 5.3 本地既有实测：批次18 lever-1 就是同一口径

这不是外部先例，但它直接检验了本报告要验证的口径，必须单列。

**实现**（`build/agent-batch18/lever-1/lever-1-diff.patch`）：

- `DENSE_UNION_TOP_K = 64`（`:513`）；
- 词面候选 ∪ dense-only top-K；dense-only 的 `lexicalScore=0`，**不写入** `lexicalScores`，因此词面 min-max 域不变（`:72-99`）；
- 融合仍用 `DenseFusion.order()` 的 α=0.5、per-leg min-max、缺腿 0；
- 返回成员可多于 `limit`，调用方仍 `take(5)`（`:426-466`）；
- 有 `unionPlan`/`merge` 守卫：词面候选必须原样在场、追加项 ≤ K、重复/缺项整体丢弃。

**读数**（既有产物，本报告只读取，未重跑）：

| 项 | 读数 | 来源 |
|---|---|---|
| 设备融合 Recall@5 | **100/129 = 0.775194** | `build/agent-batch18/lever-1/device-metrics.txt:3` |
| 设备融合 MRR | **0.6228682170542638** | 同上 `:5` |
| 词面 Recall@5 | 89/129 = 0.689922 | 同上 `:33` |
| p50 / p95 | 43ms / 55ms（预算 150/250ms） | 同上 `:2` |
| 零漂移 | 词面 0/PASS、融合 0/PASS | `docs/kb-acceptance-2026-10-17.md:510` |
| 与 A0 配对差 | 0.0000（p=1.0000） | 同上 |
| JVM 融合（前/后） | 100/129、MRR 0.622868，前后逐位相同 | `pre-step4-jvm-fused-metrics.txt:7-8` 与 `jvm-fused-metrics.txt:7-8` |
| 探针 | `extraInTop5=1 extraInTop20=30 totalExtrasInTop5=3 bestExtraRank=3` | `build/agent-batch18/lever-1/probe-union-ranks.xml:6` |
| 4 个 absent 目标 | 镁 26；等和线 absent-in-230；活结 17；力的分解 26 | 同上 `:7-10` |
| dense-only 准入日志 | 可见 `denseOnlyAccepted=64`（例：BIOLOGY 查询） | `device-logcat.txt:1328` |

**复算记录（本报告实际执行）**：`diff <(rg "Recall@5|MRR" pre-step4-jvm-fused-metrics.txt) <(rg "Recall@5|MRR" jvm-fused-metrics.txt)` → 无差异（`IDENTICAL_METRICS`），即 pre/post 两版的 Recall/MRR 行逐位相同。

**解释**：

- 并集**确实**插入了 dense-only 成员：`totalExtrasInTop5=3`，最好的一名到过第 3；所以不是“代码没生效”。
- 但 4 个目标没有进 top-5：它们的 dense 名次是 1/2/7/336，融合后却是 26/absent/17/26。**候选深度不是瓶颈；最可能的原因是缺腿=0 的 0.5 上限。**
- 聚合指标与 A0 逐位相同：从聚合读数看，插入的 3 个 extra 没有改变主集命中（可能未命中金标，或与原命中互换），因此 Recall/MRR 不变。这和 §5.2 的算式一致，但不能仅凭聚合读数证明“唯一原因就是 0.5 上限”（没有保存每个候选的 per-leg 分数分解）。

**结论：同一个口径已经被实现、跑过设备/JVM、并以“无增益”回滚。** 在没有改变融合策略（α、缺腿分、救援配额）的前提下，再跑一次 K=64 并集臂，按已有证据不应期待不同结果。

## 6. 建议（研究结论，不是施工指令）

1. **保留 per-leg min-max 与“缺腿不入域”。** 这两条有 Weaviate/OpenSearch/Bruch 的一致支持，也是本地实现已经正确落地的部分；真正的风险在“缺腿=0”与“单元素域=0”的耦合。
2. **把“缺腿=0”当作一个独立可替换策略，而不是不可讨论的常量。** 若并集臂的目标是“救 dense-only 目标”，至少预注册三个候选策略中的一个：
   - **补真实词面分**：对 dense-only 候选在同一 `knowledge_search_feature` 表上补算 `COUNT(DISTINCT feature)`；这接近 Bruch 的 “compute missing score”。若补分项写入词面域，会改变既有成员的归一化；若只补分不入域，需定义域外分数的截断规则。两种都要单独重钉。
   - **独立救援配额**：在最终 top-5 中给 dense-only 保留 1 个名额（类似“探索位”），不改变主融合分；
   - **提高 α**：让 dense-only 的上限从 0.5 提到 0.6/0.7，但要同时接受词面权重下降的全局代价。
3. **α 与缺腿策略必须一起做臂，不能单改一个。** 本地 α=0.7 设备臂的失败是零漂移装置缺陷，不是性能结论；离线 α=0.6/0.7 反而更高。若要做，先修零漂移装置，再按预注册的 α×缺腿策略网格跑，一条臂一次变更。
4. **K=64 可以保留，但不要把它当主要杠杆。** 3/4 目标 dense 名次 ≤7，K 从 64 降到 8 都还能覆盖；第 4 个同科 182 / 全科 336，按全局 top-K 语义覆盖它需 K≥182（同科）或 ≥336（全科），按 dense-only 预算语义还要加上被词面占用的名额——代价与收益不成比例。优先做融合策略，而不是加 K。
5. **把 K 的语义写死。** 明确是“全局稠密 top-K”还是“排除词面后的 dense-only 追加预算”；当前预注册与实现/注释不一致，复跑前必须钉死，否则读数不可比。
6. **加可观测性。** 现有日志只记 `denseOnlyAccepted`，不记 dense-only 候选的融合名次/每腿分数。至少记录：每个 dense-only 候选的 `dense_norm`、`lex_norm`、`fused`、最终名次；否则无法区分“没插入”“插入了但排在 5 名外”“插入了且互换了命中”。
7. **补三类回归测试**：
   - 域不变性：向 `orderDomain` 追加 dense-only，既有成员的融合分与次序逐位不变；
   - 单元素词面域：明确 0/1/skip 的期望，并钉住；
   - 常数稠密域：全等时不改变次序（与 `DenseFusionTest` 现有用例对齐）。
8. **不要把 RRF 当解药。** RRF 同样偏向双路命中的文档（单路只拿一份 reciprocal rank）；它回避的是分数归一化，不是“单路证据少”的结构劣势。是否换 RRF 应作为独立臂，而不是修并集臂的顺手改动。

## 7. 来源清单

### 7.1 官方文档

- Weaviate — Hybrid search concepts: <https://docs.weaviate.io/weaviate/concepts/search/hybrid-search>
- Weaviate — Hybrid search how-to (alpha/fusion default): <https://docs.weaviate.io/weaviate/search/hybrid>
- Qdrant — Hybrid Queries (RRF/DBSF/union/prefetch): <https://qdrant.tech/documentation/concepts/hybrid-queries/>
- Vespa — Hybrid search tutorial (OR union/RRF): <https://docs.vespa.ai/en/learn/tutorials/hybrid-search.html>
- Vespa — Phased ranking (`reciprocal_rank`, default k=60): <https://docs.vespa.ai/en/ranking/phased-ranking.html>
- Elasticsearch — Reciprocal rank fusion: <https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion>
- OpenSearch — Normalization processor: <https://docs.opensearch.org/latest/search-plugins/search-pipelines/normalization-processor/>
- OpenSearch — Reciprocal rank fusion: <https://docs.opensearch.org/latest/vector-search/ai-search/hybrid-search/rrf/>
- Azure AI Search — Hybrid search scoring (RRF): <https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking>
- pgvector — README §Hybrid Search: <https://github.com/pgvector/pgvector#hybrid-search>
- Supabase — Hybrid search: <https://supabase.com/docs/guides/ai/hybrid-search>
- ParadeDB — Reciprocal Rank Fusion: <https://www.paradedb.com/docs/reference/hybrid/rrf>
- ParadeDB — How Hybrid Search Works: <https://www.paradedb.com/docs/concepts/hybrid/overview>

### 7.2 GitHub 源码/示例

- Weaviate fusion: <https://github.com/weaviate/weaviate/blob/main/usecases/traverser/hybrid/hybrid_fusion.go>
- Weaviate searcher（“unioned, never intersected”）: <https://github.com/weaviate/weaviate/blob/main/usecases/traverser/hybrid/searcher.go>
- Weaviate alpha default 0.75: <https://github.com/weaviate/weaviate/blob/main/adapters/handlers/graphql/local/common_filters/hybrid.go#L23>
- Lucene `TopDocs.rrf`: <https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/search/TopDocs.java#L372-L407>
- pgvector RRF 示例: <https://github.com/pgvector/pgvector-python/blob/master/examples/hybrid_search/rrf.py>
- Supabase hybrid 文档源码: <https://github.com/supabase/supabase/blob/master/apps/docs/content/guides/ai/hybrid-search.mdx>
- ParadeDB RRF 源码: <https://github.com/paradedb/paradedb/blob/main/docs/reference/hybrid/rrf.mdx>
- sqlite-vec hybrid guide（访问时为空文件）: <https://github.com/asg017/sqlite-vec/blob/main/site/guides/hybrid-search.md>
- sqlite-vec NBC 示例: <https://github.com/asg017/sqlite-vec/blob/main/examples/nbc-headlines/3_search.ipynb>
- sqlite-vss README: <https://github.com/asg017/sqlite-vss>

### 7.3 论文

- Cormack, Clarke, Buettcher. *Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods.* SIGIR 2009. DOI: <https://dl.acm.org/doi/10.1145/1571941.1572114>；PDF: <http://cormack.uwaterloo.ca/cormack/cormacksigir09-rrf.pdf>
- Bruch, Gai, Ingber. *An Analysis of Fusion Functions for Hybrid Retrieval.* arXiv:2210.11934. <https://arxiv.org/abs/2210.11934>；HTML: <https://arxiv.org/html/2210.11934>

### 7.4 GitHub issues（踩坑记录）

- Qdrant #10640 — Fusion inside a prefetch is computed per shard: <https://github.com/qdrant/qdrant/issues/10640>
- Qdrant #10611 — DBSF reverses identical Euclidean retriever results: <https://github.com/qdrant/qdrant/issues/10611>

### 7.5 本地既有产物（不是外部先例）

- `docs/research/2026-10-22-dense-union-prereg.md:14`（K=64 提案）
- `docs/research/2026-10-23-kb-dense-union-estimate.md:52-86`（3/4 absent 名次 1/2/7/336；结构召回 ≠ 融合 top-5）
- `docs/kb-acceptance-2026-10-17.md:510`（批次18 lever-1 无增益、回滚）
- `build/agent-batch18/lever-1/lever-1-diff.patch:23-39,72-99,426-466,513`（同一口径的实现）
- `build/agent-batch18/lever-1/device-metrics.txt:1-5`（设备 100/129、MRR 0.622868）
- `build/agent-batch18/lever-1/jvm-fused-metrics.txt:7-8` 与 `pre-step4-jvm-fused-metrics.txt:7-8`（前后逐位相同）
- `build/agent-batch18/lever-1/probe-union-ranks.xml:6-10`（extras in top-5；目标融合名次 26/absent/17/26）
- `build/agent-batch18/lever-1/device-logcat.txt:1328`（`denseOnlyAccepted=64`）

## 8. 本报告未验证项

1. **未重跑任何检索/融合臂。** 批次18 lever-1 的读数来自既有产物；本报告只读取，未在本轮执行 Gradle、设备或基准。
2. **未逐条核对 `DenseUnionGoldenProbeTest` 的源码。** 该测试在回滚中已从工作树移除，只保留 XML 产物；探针的统计口径（`extraInTop5`/`totalExtrasInTop5` 的精确定义）未在本轮复核。
3. **未保存每个 dense-only 候选的 per-leg 分数分解。** 因此“零增益的唯一原因是 0.5 结构上限”是**与算式和探针一致的最可能解释**，不是被逐候选日志证明的因果结论。
4. **未验证 K 的两种语义在完整 129 条上的差异。** 预注册的“全局 dense top-K”与批次18 实现的“排除词面后取 K 个”只在代码层核对，未做两版对比实验。
5. **未验证外部系统的具体版本行为。** Weaviate/Qdrant/OpenSearch/Elasticsearch/Vespa 的引用来自 2026-10-25 访问到的 main/latest 文档与源码；版本升级可能改变默认值/公式。
6. **未验证 Bruch 论文“补真实缺失分”的具体实现。** 论文只写 “compute its missing score”，没有给出无法计算时的兜底；本报告把它作为“替代路线”列出，不作为可施工方案。
7. **未验证 sqlite-vec 官方 hybrid guide 的内容。** 访问时该文件是 0 字节；只能引用 NBC 示例 notebook，不能声称 sqlite-vec 官方推荐了某种融合。
8. **未验证 sqlite-vss 是否存在未文档化的 hybrid 支持。** README 未提及；不排除代码/issue 中有实验性做法。
9. **未验证外部 benchmark 对中文 K-12 知识库的可迁移性。** Bruch/OpenSearch 的结论来自英文 IR 数据集；本报告的“支持/存疑”只用于机制判断，不用于预测本地 Recall 数值。
10. **未验证本地 α=0.7 设备臂的零漂移缺陷是否已修复。** 本地 α 结论只按 `docs/research/2026-10-16-d3-round1-prereg.md` 的既有结果与 `docs/kb-acceptance-2026-10-17.md:60` 的登记，不构成“α=0.7 更差/更好”的新结论。
11. **未验证 Qdrant issue #10640/#10611 的最新状态。** 访问时两个 issue 均为 open；修复状态可能变化。
12. **未验证 Weaviate alpha 的客户端默认值。** 服务端源码默认 0.75；官方文档称“If you do not set alpha, the effective weighting depends on your client”，两者不完全一致。

## 9. 附：本地代码/文档对照

| 事实 | 落点 |
|---|---|
| α=0.5 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseFusion.kt:21` |
| min-max 上界=下界 → 0 | 同上 `:32` |
| 缺腿 `?: 0.0` | 同上 `:56` |
| 稠密域 = 全科原子节点 | `DenseRecallAssembly.kt:37`；`DenseVectorAsset.kt:45-56` |
| 词面域 = 候选 map | `DenseRecallAssembly.kt:38` |
| 当前端口“只改次序、不改成员” | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DenseRecallReranker.kt:16-21` |
| 词面分来自 `COUNT(DISTINCT feature)` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:185-210` |
| 召回上限 512 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt:42` |
| K=64 提案 | `docs/research/2026-10-22-dense-union-prereg.md:14` |
| 结构召回估计（3/4 ≤7；第 4 个 336） | `docs/research/2026-10-23-kb-dense-union-estimate.md:52-86` |
| 批次18 lever-1 无增益/回滚 | `docs/kb-acceptance-2026-10-17.md:510` |
