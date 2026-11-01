# KB L1 词面腿 BM25 式重排：预注册（2026-11-01）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7；不得回改本文 §1–§5。
> **写作时点**：2026-11-01（会话跨日，实际落盘 2026-11-02 00:10 +08:00）；HEAD `f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b`（`git rev-parse HEAD` 实跑），`origin/main` 同值。
> **上游**：`docs/research/2026-11-01-kb-external-research-wave.md` §一 L1（BM25 式重排采纳为臂）；当前 v26 release（manifest `version=26` / `contentVersion=aea886e42b36dcb2` / 材料 65,021 / 节点 3,280）；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:211-234`（现行词面 SQL）；`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt:108-191`（导入期整科建索引）；`build/agent-batch31a/v26-final.md` §二（v26 设备基线）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 sidecar、未写判定表。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'        # 2026-11-02T00:10:26+0800
git rev-parse HEAD                 # f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b
git rev-parse origin/main          # f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b  → 同值 ✓
git status --porcelain | wc -l     # 493
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   419 ??
#    74 M
git diff --cached --name-only | wc -l   # 0（暂存区空）
ls docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 493 项脏项属他线与 v25/v26/v27 已写面；本预注册不触碰、不裹挟、不回滚。
- 触碰区已有的他线 `M` 三件：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt`（判官，工作树 sha `62c232fc…`，diff 仅为 batch13→14 输出路径注释）、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/PendingCaptureDao.kt`、`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/TutorVisualPort.kt`。本臂不修改、不 stage 这三件。
- **数字实算**：本会话用 `python -I` 读主包与金标（§0.2），用 `sha256sum` 取 pre-sha（§4）；**未跑** Kotlin/JVM/设备/判官。

### 0.2 数字口径（本会话实算 / 实读）

| 事实 | 值 | 方式 |
|---|---:|---|
| 节点 N | **3,280**（MATH 856 / PHYSICS 891 / CHEMISTRY 1,019 / BIOLOGY 514） | 遍历主包 `subjects[].topics[].knowledgePoints[]`（`python -I`） |
| 金标 | **129 条 / 24 章** | 读 `tools/kb_coverage/tables/golden_queries_v2.json`（`python -I`） |
| v26 设备融合基线 | **117/129 = 0.9069767441860465**；MRR **0.7239018087855298** | `build/agent-batch31a/v26-device/device-metrics.txt:3-5` |
| v26 设备词面基线 | **101/129 = 0.7829457364341085**；MRR **0.6178294573643413** | 同上 `:33-35` |
| 9 条有 rank 的 MISS | rank **7 / 13 / 12 / 8 / 16 / 11 / 8 / 8 / 51** | `build/agent-batch31a/baseline/identity.md:143-162` |
| 特征上限 | 节点侧 16 片段 / 192 特征；查询侧 128 | `KnowledgeSearchFeatureExtractor.kt:7-10` |
| 现行索引规则指纹 | `INDEX_VERSION = 1` | `KnowledgeSearchFeatureExtractor.kt:28` |
| DB schema | **71** | `StudyDatabase.kt:139` |
| 主包 sha256 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` | `sha256sum` |
| manifest sha256 | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` | `sha256sum` |
| dense `.vec` sha256 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` | `sha256sum` |
| v2-25 侧车 sha256 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552` | `sha256sum` |
| 金标 sha256 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | `sha256sum` |
| 特征行规模下界 | **>500,000** | `KnowledgeSearchFeatureImportEquivalenceTest.kt:67` 的既有断言 |

**IDF 取值（本会话按 N=3,280 实算，用于核对公式方向与正值性；不是 df 实测）**：

| df | 1 | 10 | 100 | 1,000 | 2,000 | 3,280 |
|---|---:|---:|---:|---:|---:|---:|
| IDF | 7.6904384249 | 5.7445282758 | 3.4857458055 | 1.1876483789 | 0.4947511047 | 0.0001524042 |

**长度归一因子（k1=1.2、b=0.75，本会话实算）**：`len/avglen = 0.5 ⇒ 1.2571428571`；`= 1 ⇒ 1.0`；`= 2 ⇒ 0.7096774194`。

**定点量化误差上界（本会话实算）**：单条 IDF 量化误差 ≤ `0.5e-9`；单节点最多 128 条匹配特征 ⇒ 绝对分偏差 ≤ `128 × 0.5e-9 = 6.4e-8`。

### 0.3 现行词面腿事实（改动面）

- SQL 分数 = `COUNT(DISTINCT feature.search_feature)`，`ORDER BY count DESC, ATOMIC, canonical_name, node_id`；行类型 `KnowledgeRecallCandidateRow.matchCount: Int`（`ProblemOrganizationDao.kt:31-34, 211-234`）。
- 该分数经 `DenseRecallCandidate.lexicalScore: Int`（`DenseRecallReranker.kt:9-12`）进入融合；融合在**单查询候选域内**做 min-max，`α=0.5`，缺腿给 0，并列按 node_id（`DenseFusion.kt:4-60`）。
- 候选深度 = 512（`MAX_KNOWLEDGE_RECALL_CANDIDATES`，`StudyDatabasePort.kt:43`）；判分窗口 = top-5，5 samples（`GoldenRetrievalInstrumentedTest.kt:163-180`）。
- 本臂**只改分数定义**；WHERE 过滤、subject 作用域、512 深度、top-5、样本数、融合常数、dense 资产均不动。

---

## 1. 机制（冻结）

### 1.1 公式与参数

词面腿分数由 `COUNT(DISTINCT feature)` 改为 BM25 式（**写死**）：

```text
score(n) = Σ_{f ∈ matched(n)} IDF(f) · (k1 + 1) / (1 + k1 · (1 − b + b · len(n) / avglen)) + 1
IDF(f)   = ln( (N − df(f) + 0.5) / (df(f) + 0.5) + 1 )
```

- `N = 3,280` 节点（v26 主包；执行轮以 `corpus_stat.node_count` 复核，≠3,280 即 `CORPUS_MISMATCH`，不判读）。
- `df(f)` = 含该 feature 的节点数；`len(n)` = 该节点在 `knowledge_search_feature` 的**行数**（即该节点特征集大小）；`avglen` = 全部被索引节点的 `len` 均值。
- **`k1 = 1.2`、`b = 0.75` 取文档默认，禁止网格搜索**；不得按结果回调这两个值。
- `tf ≡ 1`：`matched(n)` 是按 `search_feature` 去重后的匹配集（`IN` + 每节点每特征一行，主键 `(subject, search_feature, knowledge_node_id)`），因此每个匹配特征只计一次，长度归一因子对同一节点的所有匹配特征相同。等价写法：

```text
score(n) = (k1 + 1) / (1 + k1 · (1 − b + b · len(n) / avglen)) · Σ_{f ∈ matched(n)} IDF(f) + 1
```

实现必须按上式**先对去重匹配集求 IDF 和，再乘一次节点级因子**；不得把 `len` 当成每个特征各自的长度重复乘。
- `+1` 是公式的一部分（保证非负、空匹配不出现 0 分）；在现行 min-max 融合下它是全候选的常数平移，**不改变归一化值与名次**，不得把它当调参旋钮。

### 1.2 df / avglen / len 的口径

- `df(f)`：在 `knowledge_search_feature` 全表上 `COUNT(DISTINCT knowledge_node_id)`，**跨科全局统计**，不按 subject 截断。同一特征串出现在两科时，两科节点都计入 `df`。
- `len(n)`：该节点在 `knowledge_search_feature` 的全部行数（不是本次查询的匹配数），与查询特征集无关。
- `N`：`COUNT(DISTINCT knowledge_node_id)` over `knowledge_search_feature`，即被索引（`CURATED` / `SOURCE_GROUNDED` / `USER_CONFIRMED` 且非 `RETIRED`）的节点数；v26 预期 3,280。
- `avglen = totalFeatureRows.toDouble() / N`，其中 `totalFeatureRows = COUNT(*)`；由整数除法的一次 Double 转换得出，不走 `AVG()` 的聚合路径。
- 统计域与召回查询的过滤域**逐条一致**（同样的 `verification_status` 集合、同样排 `RETIRED`）；不一致会让 `df/avglen` 与候选域错位。
- 旁车是**派生数据**，不是内容：不写判定表、不参与材料计数、不随内容备份作为“内容”恢复；恢复后缺失时按 §1.4 惰性重建。

### 1.3 数值确定性（零漂移的前提）

- **IDF 用 `java.lang.StrictMath.log` 计算，不用 `Math.log`**。`StrictMath` 按 fdlibm 定义、跨 JVM/Android 逐位可复现；`Math.log` 允许平台 1 ulp 差异，足以在并列边界翻转名次（`DenseFusion.kt:18-20` 已登记 Float/Double 翻转名次的同类风险）。
- IDF 以 **1e-9 定点整数**落库：`idf_nano = floor(idf * 1e9 + 0.5).toLong()`（IDF 恒正，半上舍入定义唯一）。`N=3,280` 时 IDF ≤ 7.6904384249，单节点至多 128 项，`Σ idf_nano ≤ 9.9e11 < 2^53`，Long/Double 转换精确。
- 这样 `Σ idf_nano` 是**整数和**：SQLite 的 `SUM` 与 Kotlin 的 `sumOf` 都精确、与求和顺序无关，消灭“设备 SQL 求和顺序 ≠ JVM 镜像求和顺序”的漂移源。
- 节点级因子与最终分数在两端用**同一棵表达式树**计算（写死，不得改写结合顺序）：

```text
ratio = len(n).toDouble() / avgLen
den   = 1.0 + k1 * (1.0 - b + b * ratio)
score = (sumIdfNano.toDouble() / 1.0e9) * ((k1 + 1.0) / den) + 1.0
```

- 查询期**不在 SQL 里调用 `ln()`/`log()`**：API 23 的 SQLite 不保证数学函数可用；IDF 已在导入期定点化，SQL 只做整数 `SUM` 与四则运算。
- 与实数公式的偏差仅来自定点量化（≤ `6.4e-8` 绝对分，见 §0.2），不改变任何非并列次序；`k1/b/N/avglen` 一个都不许改。

### 1.4 旁车（新表，导入期由 `KnowledgeSearchIndexBuilder` 落表）

新增 4 张派生表（DB schema 71 → **72**，迁移 `71_72` 只建空表，不搬内容）：

| 表 | 列 | 主键 | 用途 |
|---|---|---|---|
| `knowledge_search_feature_df` | `search_feature TEXT NOT NULL`, `df INTEGER NOT NULL` | `search_feature` | 每个特征的全局 df |
| `knowledge_search_df_idf` | `df INTEGER NOT NULL`, `idf_nano INTEGER NOT NULL` | `df` | df→定点 IDF 查找表（N 行，v26 = 3,280 行） |
| `knowledge_search_node_len` | `knowledge_node_id TEXT NOT NULL`, `feature_count INTEGER NOT NULL` | `knowledge_node_id` | 每节点特征行数 `len(n)` |
| `knowledge_search_corpus_stat` | `singleton INTEGER NOT NULL`, `node_count INTEGER NOT NULL`, `total_feature_count INTEGER NOT NULL`, `avg_feature_count REAL NOT NULL`, `stale INTEGER NOT NULL` | `singleton`（恒 0，单行） | N / 总行数 / avglen / 脏标记 |

**刷新入口（冻结名）**：`refreshSearchFeatureStats()`，放在 `KnowledgeSearchIndexBuilder.kt`，在同一写事务内：

1. 删旧行 → `INSERT … SELECT search_feature, COUNT(DISTINCT knowledge_node_id) FROM knowledge_search_feature GROUP BY search_feature` 写 `feature_df`；
2. `INSERT … SELECT knowledge_node_id, COUNT(*) … GROUP BY knowledge_node_id` 写 `node_len`；
3. 以 Kotlin 生成 `df = 1..N` 的 `idf_nano`，写 `df_idf`（v26 = 3,280 行）；
4. 写 `corpus_stat(0, N, total, total/N, 0)`。

Room 允许 `@Query` 执行 `INSERT`（先例 `LibraryFtsSearchDao.kt:240`）；若 Room 对 `INSERT … SELECT` 拒绝编译，改用同事务内的 `@RawQuery`，**列、语义、事务边界不变**。

**不变量（写死）**：`corpus_stat.stale = 0` ⟺ 三张统计表与 `knowledge_search_feature` 逐行一致。

**维护点（每处都必须与特征写/删同事务）**：

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `KnowledgeSearchIndexBuilder.rebuildSubject` | 删/插特征块期间 `stale=1`；锚点事务内 `refreshSearchFeatureStats()` 后写锚点（锚点最后） |
| 2 | `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex` 的只补缺分支 | 插入缺失特征后同事务 `refreshSearchFeatureStats()` |
| 3 | `ProblemOrganizationDao.importKnowledgeBase` | 导入事务内置 `stale=1`（随后安装期重建会把统计校正为 0） |
| 4 | `KnowledgeGroundingDao.applyReviewedPack` | 批内插入节点/特征后置 `stale=1`（O(1) UPDATE，不在此做全量统计） |
| 5 | `RoomKnowledgeContentReconciler.retireNodes` | 退役删特征后置 `stale=1`；删除与置脏包在同一写事务 |

**读路径**：`ensureKnowledgeSearchIndex` 在现有完整性缓存短路**之前**读一次 `corpus_stat.stale`（单行主键读）；`stale=1` 或行缺失 ⇒ 先在同一写事务内 `refreshSearchFeatureStats()`，再走原有完整性判定。这样升级/恢复后无需整科重抽特征，只在首次召回做一次统计构建（>500k 行的 `GROUP BY`，一次性成本，§7 记耗时）。

**不升 `INDEX_VERSION`**：抽取规则（16 片段 / 192 特征）一字未改，特征表内容不变；旁车是从现有特征表派生的。把 `INDEX_VERSION` 从 1 改成 3 会强制一次无意义的 60 万行重抽，且历史实验锚点 2 会造成旧库误判。**`INDEX_VERSION` 保持 1。**

### 1.5 查询形状与排序（冻结）

保持同一 `GROUP BY` / `ORDER BY` 单一来源（沿用 `ProblemOrganizationDao.kt:203-210` 的纪律），SQL 形状冻结为：

```sql
SELECT node.*, <score> AS lexicalScore
FROM knowledge_search_feature AS feature
INNER JOIN knowledge_node AS node ON node.knowledge_node_id = feature.knowledge_node_id
INNER JOIN knowledge_search_feature_df AS fdf ON fdf.search_feature = feature.search_feature
INNER JOIN knowledge_search_df_idf AS didf ON didf.df = fdf.df
INNER JOIN knowledge_search_node_len AS nlen ON nlen.knowledge_node_id = node.knowledge_node_id
CROSS JOIN knowledge_search_corpus_stat AS corpus
WHERE feature.subject = :subject
  AND feature.search_feature IN (:searchFeatures)
  AND node.verification_status IN ('CURATED', 'SOURCE_GROUNDED', 'USER_CONFIRMED')
  AND node.status != 'RETIRED'
GROUP BY node.knowledge_node_id
ORDER BY lexicalScore DESC,
         CASE node.granularity WHEN 'ATOMIC' THEN 0 ELSE 1 END,
         node.canonical_name ASC,
         node.knowledge_node_id ASC
LIMIT :limit
```

`<score>` = `(SUM(didf.idf_nano) / 1000000000.0) * ((1.2 + 1.0) / (1.0 + 1.2 * (1.0 - 0.75 + 0.75 * (nlen.feature_count / corpus.avg_feature_count)))) + 1.0`。

- 行类型 `KnowledgeRecallCandidateRow.lexicalScore: Double`；`DenseRecallCandidate.lexicalScore: Double`；融合侧 `DenseFusion.order` 已收 `Map<String, Double>`，**不改融合代码**。
- WHERE、subject 作用域、`LIMIT :limit` 与现行逐字相同；但**512 深度是按分数截断的，分数变了成员集可以变**——这是机制的一部分，不是漂移；判读看 Recall@5/MRR，不承诺成员不变。
- 并列键（ATOMIC → canonical_name → node_id）保持不变，保证全序。

### 1.6 每一项新增消灭的具体失败（逐个指认）

| 新增 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| IDF 加权 | “特征多就霸榜”：匹配大量常见 2/3-gram 的泛节点靠计数压过匹配少量稀有特征的精确节点 | 外部研究波 L1；现行 SQL 只数 `COUNT(DISTINCT)` |
| 长度归一 `len/avglen` | 别名多的节点因最多 192 条特征行天然占优，掩盖内容相关性 | `KnowledgeSearchFeatureExtractor.kt:44-57`（别名+边界全进特征；单节点上限 192） |
| `feature_df` 旁车 | 每个查询对 >50 万行特征表做按特征的 `COUNT(DISTINCT)` 全扫 | `KnowledgeSearchFeatureImportEquivalenceTest.kt:67` 的行数下界 |
| `df_idf` 查找表 | 查询期需要 `ln()`；API 23 SQLite 不保证数学函数，且逐查询现算 IDF 无法保证 device↔JVM 同值 | 现行最低 API 23；§1.3 |
| `idf_nano` 整数和 | SQL `SUM(REAL)` 与 Kotlin 求和顺序不同 ⇒ 1 ulp 差异在并列处翻转名次 | `DenseFusion.kt:18-20` 已登记同类翻转风险 |
| `node_len` 旁车 | 每个候选做相关子查询数行；且若直接用查询过滤后的 `COUNT(*)`，会把 `len` 错算成匹配数 | §1.1 的 `tf≡1` 与 `len` 定义 |
| `corpus_stat.stale` | 运行期 grounding/退役改特征后统计静默过期，df 偏大/偏小且无人发现 | §1.4 维护点 4/5 |
| `StrictMath.log` | JVM 与 Android 的 `Math.log` 1 ulp 差异进入 IDF，破坏零漂移 | §1.3 |
| 不升 `INDEX_VERSION` | 升级时无谓重抽 60 万行特征；复用历史锚点 2 导致旧实验库被误判为当前 | `KnowledgeSearchFeatureExtractor.kt:28` 的版本语义 |

### 1.7 明确不改（一条都不许动）

1. 融合：`α=0.5`、域内 min-max、缺腿给 0、并列按 `node_id` 升序（`DenseFusion.kt` 零改）。
2. dense：资产 `70329115…`、模型、查询编码、`DenseRecallAssembly` 的装配与开关。
3. 内容：主包 `96246c78…`、manifest `370b0979…`、v2-25 `93269182…`、材料 65,021、节点 3,280、别名。
4. 判官与金标：`golden_queries_v2.json` `eabef102…`；`GoldenRetrievalJvmTest` 不改判分口径（其工作树 `M` 属他线）。
5. 检索参数：候选深度 512、top-5、5 samples、WHERE 过滤、subject 作用域。
6. 公式参数：`k1=1.2`、`b=0.75`、`N=3,280`、`avglen` 定义、`+1`；**禁止网格搜索**。
7. 特征抽取规则与 `INDEX_VERSION`（保持 1）。

---

## 2. 判读（写死）

### 2.1 三条规则（全部按**融合路**求解）

| # | 规则 | 判据 |
|---:|---|---|
| 1 | **主集严格更高** | 设备融合 Recall@5 **严格大于**同代 v26 基线，且 **≥ 118/129 = 0.9147286821705426**（对冻结的 117/129 下限）；`A_fused > B_fused` 与绝对值下限同时成立才算过。equal 不算过。 |
| 2 | **24 章逐章不劣** | 24 章逐章融合 Recall@5 与同代 v26 基线比较；任一章**严格更低**即不采纳；equal/improved 均通过。 |
| 3 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = 本臂、B = 同代 v26**；A−B 的 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高）。 |

**符号口径（冻结，防写反）**：`tools/kb_coverage/retrieval_significance.py` 输出字段是 `delta_recall_b_minus_a`，其 `ci95_chapter` 是 **B−A** 的区间。因此 A−B 的 95% CI = `[−ci_hi, −ci_lo]`，规则 3 的通过条件是 **`−ci_lo ≥ 0`，即 `ci_lo ≤ 0`**。§7 必须同时给出工具原始字段与换算后的 A−B 区间。本会话用 `--reps 200 --seed 0` 只核对了字段名与符号（v26 vs v25 输出 `delta_recall_b_minus_a=0.0`、`ci95_chapter=[0,0]`、`p=1.0`），**200 次不是判读参数**；判读固定 20,000。

### 2.2 零漂移 = 0（装置门，不参与“好坏”）

- 同路 device↔JVM 的 **fused 与 lexical 两条** MISS query 集合 symmetric difference 必须 **0**。非 0 = 装置缺陷，本臂**不判读**（不得用逐章相等或主集读数绕过）。
- 零漂移是 §1.3 数值确定性的直接验收：若 `StrictMath`/整数 IDF/表达式树任一处不一致，这里会先红。

### 2.3 基线口径（防止拿旧读数/离线数冒充）

- **B 臂 = 同代 v26 设备四路账本**，必须在本轮同一 emulator/API、同一金标、同一主包、同一 dense 资产上重新取；冻结参考值是融合 **117/129**、MRR **0.7239018087855298**、词面 **101/129**（`v26-device/device-metrics.txt:3-5,33-35`）。
- 若同代复测的 B 不等于 117/129，则规则 1 的严格比较用**复测 B**，117/129 只作绝对下限；不得用历史 117 冒充同代基线。
- `samegen` 判据：emulator 序列/API、金标 sha、主包 contentVersion、dense 资产 sha、融合常数、候选深度一致（`dual-identity.json`）。任一 MATCH 不成立 ⇒ 规则 1/2/3 记 `NOT_RUN`。
- **stage3 离线融合数、JVM 离线数、v25/v24 旧设备数一律不得当 B 臂**；它们不是同代 v26 设备读数。
- 本臂自身会改判官镜像与设备测试的代码（词面分从 Int 变 Double、镜像是 BM25），这是“臂”的定义；同代性定义在**内容输入 + 判官输入（金标/主包/dense）**上，不要求两臂的镜像源码相同。

### 2.4 报告义务（§7 至少包含）

- A/B 两臂：设备融合与词面的 Recall@5、MRR、p50/p95/max、overBudget；24 章逐章 lower/equal/higher。
- 两臂四条 MISS 账本 sha256；零漂移两路 symdiff；bootstrap 原始 JSON（20k/seed0）与 A−B 换算区间。
- 9 条目标节点的**臂前/臂后名次**（rank 7–51 那 9 条，逐条）。
- `corpus_stat.node_count`（须 3,280）、`total_feature_count`、`avg_feature_count`、`df_idf` 行数、`feature_df` 行数。
- 双门 exit code；§4 逐文件写后 sha。
- 任何未跑项写 `NOT_RUN` / `UNVERIFIED`，不得留空或写“无显著差异”。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | **exit 0**（一条命令、两个 task、单一 exit code） |
| 2 | `./gradlew :core:data:compileDebugUnitTestKotlin :core:data:assembleDebugAndroidTest --console=plain` | **exit 0**（一条命令、两个 task、单一 exit code） |

- 与 v26/v27 的差异：本臂新增 `:core:database` 的实体/DAO/迁移 71→72 与公式单测，门 1 由该模块承担（主源码 + unit test + androidTest，含迁移测试）；门 2 覆盖 JVM 判官镜像（`RetrievalBenchmark` / `GoldenRetrievalJvmTest` / `Bm25MirrorParityTest`）与设备检索套件。**门数仍为 2，判据仍为 exit 0。**
- 任一门红 ⇒ **不写 release、不跑设备、不判读**；不得先跑设备再补门。
- 两门日志落执行轮目录（建议 `build/agent-batch32/l1-bm25/`），§7 引用；不得只写“已跑”。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚（触碰清单 + pre-sha）

### 4.1 A. 生产源码（tracked；写前均为 clean，pre-sha 为当前工作树实算）

| # | 路径 | 预期动作 | pre-sha256（64 hex） |
|---:|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt` | 词面查询改 BM25；行类型改 `Double`；统计 DAO 方法 | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt` | version 71→72；实体入列；迁移挂载 | `7210f974fbfd1f447e8165c5ef2becd05a8105787cf2c4a5a4a91e7f98ef8336` |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt` | `refreshSearchFeatureStats()`；锚点事务接线；`markStale` | `db734746f0d3c255af63647febac1ad85867e7f160eee8edebf74b58c45fc416` |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 读路径 stale 检查；只补缺刷新；`Double` 透传 | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/DenseRecallReranker.kt` | `lexicalScore: Int` → `Double` | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeContentReconciler.kt` | 退役删特征后置 `stale=1`（同写事务） | `72cb7f0b49106ac8d50d820eaa7f802017b7e1d5f6624019d0183844c028e233` |
| 7 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeGroundingDao.kt` | `applyReviewedPack` 插入特征后置 `stale=1` | `5dd4ec659fcda8b4aabf644ba13809e0935a86579150fe4197fa2f890cc64f6b` |
| 8 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt` | `BRouteMirrorIndex` 改 BM25（同 df/idf_nano/表达式树），返回 `Double` | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |
| 9 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ProductionLexicalLegExportTest.kt` | 第三列改 BM25 `Double.toString()`；**采纳后**才重钉 `V1_D1_MAIN`/`V1_D1_MRR`（旧值 0.7829457364341085 / 0.6178294573643413 保留在 §7） | `bbc210144ed2581fb083727aa4410b5697d13134124ceb6a66d922173c531f37` |
| 10 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` | 探针 `DenseRecallCandidate(..., 1)` → `1.0`；其它口径不动 | `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786` |
| 11 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureImportEquivalenceTest.kt` | harness 建 4 张统计表并填数；镜像 SQL 改 BM25 | `27e1ef312fa1fcdd881fe1df54fc098e06808ce466c0c26a8cae6afd70f60c17` |

### 4.2 B. 新增文件（写前不存在）

| # | 路径 | 动作 | pre-sha256 |
|---:|---|---|---|
| 12 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/KnowledgeSearchFeatureStatEntity.kt` | 4 个 `@Entity` | **不存在** |
| 13 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureStatMigration_71_72.kt` | 建 4 表（空） | **不存在** |
| 14 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json` | Room 导出（生成） | **不存在** |
| 15 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/Bm25LexicalScoreTest.kt` | 公式/定点/边界单测 | **不存在** |
| 16 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureStatMigrationInstrumentedTest.kt` | 71→72、惰性统计、查询正确性 | **不存在** |
| 17 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Bm25MirrorParityTest.kt` | JVM 镜像与手算 BM25 对拍 | **不存在** |

### 4.3 C. 只读 / 零触碰（登记供核验，不得改）

| # | 路径 | 期望 | pre-sha256 |
|---:|---|---|---|
| 18 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseFusion.kt` | 零改（α=0.5 / min-max） | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 19 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt` | 零改（`.toDouble()` 对 Double 仍编译） | `c654d988f9a8e730168c435b788e196c858691e9a3af50b2494c07ed57512683` |
| 20 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt` | 零改（`INDEX_VERSION` 保持 1） | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 21 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/KnowledgeSearchFeatureEntity.kt` | 零改 | `9da364a46c629523e8c608172d04e06a0f771c44e0f3a43b1d29eab266791213` |
| 22 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | 零改 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 23 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 24 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | 零改 | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` |
| 25 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-25.json` | 零改 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552` |
| 26 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 27 | `tools/dense_build/stage3_expectation.py` | 零改（算法只读 `float(parts[2])`，注释里的 v1 口径不影响解析） | `4fd0dc67bae670fbc02eb73c6a561f2e09645845132b3a6864bd5f9765273f6a` |
| 28 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` | 零改（历史 schema 对照） | `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94` |

**他线 `M` 不裹挟**：`GoldenRetrievalJvmTest.kt`（`62c232fc…`）、`PendingCaptureDao.kt`、`TutorVisualPort.kt` 的工作树改动不在本臂写面；不得 stage、不得回滚。

### 4.4 写前备份与回滚 / 采纳

**写前备份（执行轮第 1 步之前）**

1. 把 §4.1 的 11 件复制到 `build/agent-batch32/l1-bm25/backup/`，落 `sha256sum` 全表。
2. `git status --porcelain -- <§4.1 路径>` 必须为空（写前）；若非空，先归因他线并停止，不得覆盖。
3. 复核 v26 身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；主包 `96246c78…`；dense `70329115…`；金标 `eabef102…`；DB 版本 71。

**回滚（任一规则失败、双门红、零漂移非 0）**

1. **不得** `git checkout` 整树（493 项脏项）；只按 §4.1/§4.2 逐文件恢复/删除。
2. 用 `backup/` 覆盖 §4.1 的 11 件；删除 §4.2 的 6 件（含生成的 `72.json`）；删除本臂生成的 `build/production-lexical-leg.tsv`、dense fusion fixture 与 `build/agent-batch32/l1-bm25/` 运行产物（保留备份与回滚记录）。
3. **设备侧必须清库**：Room 不支持 schema 72→71 降级；`adb shell pm clear <appId>`（或卸载重装）后安装 v26 APK，再核 `version=26`。不得直接覆盖安装。
4. 核验：`corpus_stat` 等 4 表不存在；主包/dense/金标/manifest/v2-25 sha 与 §0.2 一致；`ProductionLexicalLegExportTest` 锚点回到 0.7829457364341085 / 0.6178294573643413。
5. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
6. §7 写明回滚原因与逐文件 sha；不得把“已回滚”写成“未采纳但保留改动”。

**采纳（三条规则全过 + 零漂移 0 + 双门 0）**

1. 保留 §4.1/§4.2 的写面与 72 schema；`stale=0`、`node_count=3,280`。
2. **只在采纳后**重钉 `ProductionLexicalLegExportTest` 的 `V1_D1_MAIN` / `V1_D1_MRR` 为新臂实测值；§7 同时列旧值/新值，结构断言（129 条、24 章、金标 sha、同科/可信过滤）一条不放宽。
3. §7 登记 stateAfter：manifest/contentVersion（预期不变）、主包/dense/金标 sha、DB version 72、统计表行数、双门 exit code、两臂四路读数。

---

## 5. 预期（对准 9 条域内排序，不承诺 +N）

### 5.1 对准对象：9 条有数值 rank 的 MISS（rank 7–51）

| # | 科 | 期望 slug | chapter | rank |
|---:|---|---|---|---:|
| 1 | BIOLOGY | `性状显隐性的判断` | 生物学必修2·第一章·遗传的基本规律 | 7 |
| 2 | CHEMISTRY | `电解原理易错点` | 化学必修第一册·第三章·铁与金属材料 | 13 |
| 3 | CHEMISTRY | `同分异构现象和同分异构体` | 化学必修第二册·第七章·有机化合物 | 12 |
| 4 | MATH | `利用奇偶性求值-将待求函数值或不等式利用奇偶性转化为已知区间上的函数值求解` | 数学必修第一册·第三章·函数的概念与性质 | 8 |
| 5 | MATH | `函数图象的翻折变换` | 数学必修第一册·第三章·函数的概念与性质 | 16 |
| 6 | MATH | `方程组法-主要解决已知与-的方程-求解析式` | 数学必修第一册·第三章·函数的概念与性质 | 11 |
| 7 | MATH | `判别式法-主要用于含有二次的分式函数-形如` | 数学必修第一册·第三章·函数的概念与性质 | 8 |
| 8 | MATH | `数列的单调性与最值问题` | 数学选择性必修第二册·第四章·数列 | 8 |
| 9 | PHYSICS | `共点力的动态平衡` | 物理必修第一册·第三章·相互作用 | 51 |

- 这 9 条都在 512 候选深度内（rank ≤ 512），所以融合腿有机会把它们排进 top-5；3 条 `absent`（`等和线`、`活结与死结模型`、`力的分解`）不在本组，只报不判。
- **预期是定性的**：IDF 压低常见 gram、长度归一压低别名膨胀节点，理论上有利于“材料少但特征精确”的节点；不承诺任何一条一定进 top-5，也不承诺 `+N`。

### 5.2 不承诺 / 边界

1. **不承诺 +N**：主集严格更高是采纳门槛，不是预测；可能只 +1 条，也可能 0 条或回退。9 条只作“对准”，不作成功条件。
2. **不改融合、不改 dense**：`DenseFusion`、dense 资产、查询编码、`α=0.5` 零改；若融合或 dense 也被改，本臂作废，另立预注册。
3. **词面腿自身可能变差**：本臂的采纳判据在融合路。若融合升、词面腿自身的 Recall@5 下降，§7 必须明确写“机制归因不成立；采纳只按融合判据，不宣称 BM25 提升词面腿”，不得把融合收益记成 BM25 的功劳。
4. **测试集不是开发集**：129 条金标只判读一次；不得看结果后调 `k1/b`、改 `N`、换 `avglen` 定义或重跑挑最好的一次。
5. **候选成员可漂移**：512 深度按分数截断，分数变化会改变窗口成员；这是机制的一部分，§7 需报告两臂的候选集/账本差异，不得当成装置故障（装置故障只看同路 device↔JVM 的 symdiff）。
6. **不修节点、不改内容**：节点 3,280、材料 65,021、别名、判定表零改；本臂只改检索分数。

---

## 6. 未做 / 未验证（写作时点）

- **未跑 Gradle/设备/JVM/判官**：§2/§3 是判据与门定义，不是通过声明。
- **未计算实际 df/avglen**：本预注册只冻结公式、口径与表示；`df/avglen/len` 是导入期派生量，执行轮由 `refreshSearchFeatureStats()` 落表并读回。§0.2 的 IDF 表是**按 N=3,280 的公式取值**，不是语料 df 实测。
- **特征行精确数未测**：只有 >500,000 的既有断言下界；`total_feature_count`、`avglen`、`df_idf` 行数留 §7 实测。
- **迁移 71→72 未跑**：建表、Room schema 校验、惰性统计、旧库升级后的首次召回均未执行。
- **JVM 镜像/harness 未改**：`RetrievalBenchmark`、`ProductionLexicalLegExportTest`、`KnowledgeSearchFeatureImportEquivalenceTest` 的 BM25 版本尚未写；本文件不声称它们已对齐。
- **B 臂同代基线未复测**：117/129 是 v26 已落盘读数（`v26-device/`，emulator-5584、金标 `eabef102…`、判官 `62c232fc…`）；执行轮若换 emulator 必须重测，否则 `samegen` 不成立。
- **显著性只核对了符号**：本会话用 `--reps 200` 读字段（`delta_recall_b_minus_a` / `ci95_chapter` / `p_two_sided`）；20,000 次判读未跑。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1/§4.2 逐文件写后 sha；`corpus_stat`（node_count 须 3,280 / total_feature_count / avg_feature_count / df_idf 行数 / feature_df 行数）；双门 exit code；v26/v26-B 两臂设备融合与词面 Recall@5/MRR、24 章 lower/equal/higher；四条 MISS 账本 sha 与零漂移 symdiff；bootstrap 20k/seed0 原始 JSON 与 A−B 换算区间；9 条目标节点臂前/臂后名次；`stale` 惰性刷新耗时与安装期 `statsMs`。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
