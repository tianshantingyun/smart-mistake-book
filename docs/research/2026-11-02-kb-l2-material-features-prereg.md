# KB L2 材料特征索引（title + summaryMarkdown）：预注册（2026-11-02）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-02（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑 `2026-11-02T02:33:47+0800`）；HEAD `70611e611b81aad15b65330310f953c4de563bab`（`git rev-parse HEAD` 实跑），`origin/main` 同值。
> **上游**：`docs/research/2026-11-01-kb-external-research-wave.md:11`（L2 = 材料 title+summary 级索引、节点聚合 max）；`docs/kb-batch32a-2026-11-01.md:137-147`（F18 当前 blocked / 无对象）；`build/agent-batch32a/baseline/identity.md:271-292`（+31% 行、约 +44 s 的代价外推）；当前 v26 release（manifest `version=26` / `contentVersion=aea886e42b36dcb2` / 65,021 材料 / 3,280 原子节点）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/JVM/判官、未写 sidecar/判定表/索引。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'   # 2026-11-02T02:33:47+0800
git rev-parse HEAD             # 70611e611b81aad15b65330310f953c4de563bab
git rev-parse origin/main      # 70611e611b81aad15b65330310f953c4de563bab  → 同值 ✓
git status --porcelain | wc -l # 493
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   420 ??
#    73  M
git diff --cached --name-only | wc -l   # 0（暂存区空）
ls docs/research/2026-11-02-kb-l2-material-features-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 493 项脏项属他线与 v25/v26/v27 已写面；本预注册不触碰、不裹挟、不回滚。**不得**用整树 `git checkout`。
- **L1 遗留孤儿**：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json` 仍在工作树（未跟踪，382,428 B，mtime 2026-11-02 00:26）；L1 的 15 `M` + 5 `A` 源码件在本会话已不可见（`KnowledgeSearchBm25.kt` / `KnowledgeSearchFeatureStatEntity.kt` / `KnowledgeSearchFeatureStatMigration_71_72.kt` 均不存在）。L2 从 **v26 的 schema 71** 起；执行轮必须删除该孤儿 `72.json` 后重新生成 L2 的 `72.json`，不得把 L1 的 72 当基线。
- **数字实算**：本会话用 `python -I` 读主包 + 25 sidecar（§0.2–§0.4），用 `sha256sum` 取 pre-sha（§4/§5）；**未跑** Kotlin/JVM/设备/判官。

### 0.2 v26 身份与规模（本会话实算 / 实读）

| 事实 | 值 | 方式 |
|---|---:|---|
| 节点 | **3,280 原子**（MATH 856 / PHYSICS 891 / CHEMISTRY 1,019 / BIOLOGY 514）；另有 425 主题节点，共 3,705 | 遍历主包 `subjects[].topics[].knowledgePoints[]` |
| 材料 / 绑定 | **65,021 / 65,021**（MATH 14,497 / PHYSICS 18,501 / CHEMISTRY 18,315 / BIOLOGY 13,708） | 遍历 25 sidecar `materials[]` |
| 绑定 role | **65,021 条全部 `PRIMARY`**；unbound **0** / dangling **0** | 遍历 `bindings[].role` / `knowledgeNodeId` |
| 材料状态 | **65,021 条全部 `ACTIVE`**（无 `RETIRED`） | 读 `status`（缺省 `ACTIVE`） |
| 材料 title 长度 | min **1** / 中位 **16** / max **183** | `len(title)` |
| 材料 summary 长度 | min **3** / 中位 **43** / max **326** | `len(summaryMarkdown)` |
| manifest | `version=26` / `schemaVersion=2` / `contentVersion=aea886e42b36dcb2` / sha `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` | 读 manifest + `sha256sum` |
| 主包 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` | `sha256sum` |
| v2-25 侧车 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552`（159 条） | `sha256sum` |
| 25-sidecar 聚合 | `9e7d3e13cba98da236001ea94e3f0f372e962e7661e06ac9f23f3e4b2eab5473` | 按 v2-01…25 顺序连接 basename+sha |
| dense `.vec` / 旁车 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` / **`6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b`** | `sha256sum`；`.vec.json` 实测当前树/`f0512a47`/`70611e61` 均 `6612a28d…`，v26-final 记的 `5cbbcb6f…` 是陈旧记录（§0.2 注） |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`（129 条 / 24 章） | `sha256sum` + 读 JSON |
| DB schema | **71**（`StudyDatabase.kt:139`）；孤儿 `72.json` 见 §0.1 | 读源码 |
| 别名 | **40,841**（沿 v26 基线登记，本会话未重算） | `build/agent-batch31a/v26-final.md:11` |

> **`.vec.json` 记录差**：v26-final `:11` 记 `.vec.json` 为 `5cbbcb6f…`；本会话 `sha256sum` 实测当前树、`git show f0512a47:`、`git show 70611e61:` 三处均为 **`6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b`**（3,631 B）。`.vec` 本体 `70329115…` 三处同值。本预注册以实测 `6612a28d…` 为 `.vec.json` 的零触碰 pre-sha，并把 `5cbbcb6f…` 登记为**上游陈旧记录**；这不改变 `.vec` 的 70329115 与 dense 资产代际。

**现行特征行规模（本会话按 v26 包现算，非 DB 读数）**：

| 口径 | 节点 | 行数 |
|---|---:|---:|
| 原子节点（材料可绑定域） | 3,280 | **541,725** |
| 主题节点（无材料绑定） | 425 | 4,863 |
| 全部被索引节点 | 3,705 | **546,588** |

- 对照：`build/agent-batch32a/baseline/identity.md:271-292` 记 **541,349 行 / 3,683 节点**；差异属节点集/抽取口径，执行轮以 DB `COUNT(*)` 为准。
- 本预注册的**行增量/导入外推以 546,588 为分母**（全节点现算口径）；材料特征只落在 3,280 个原子节点上。

### 0.3 材料特征的静态配额实算（写前，非读数）

按 §1.2 的冻结规则（`title + summaryMarkdown`，16 片段 / 单材料 64 特征，节点材料配额 64 行，行序 `title.length asc, material_id asc`）遍历 v26 包：

| 项 | 读数 |
|---|---:|
| 每节点绑定材料数 | min **1** / p25 **5** / 中位 **13** / p75 **29** / p90 **48** / p99 **96** / max **146** / mean **19.82** |
| 每节点材料特征行（64 行封顶后） | min **6** / p25 **64** / 中位 **64** / max **64** / mean **63.80** |
| 命中 64 行上限的节点 | **3,232 / 3,280（98.5%）** |
| 未满 64 行的节点 | **48**（单材料且 title+summary 特征不足 64） |
| **首条材料单独占满 64 行**的节点 | **1,785 / 3,280（54.4%）** |
| 节点自身特征（现行 192 上限） | min **51** / 中位 **173** / max **192** / mean **165.16**；命中 192 的节点 **1,089** |
| 节点自身特征（本臂 128 切分后） | min **51** / 中位 **128** / max **128** / mean **125.66**；命中 128 的节点 **2,888** |

**行增量与导入外推（三种切分对照；外推用 `identity.md` 的 0.2663 ms/行，不是实测）**：

| 切分 | 材料行 | 合并后总行 | 相对 546,588 增量 | 外推插入增量 |
|---|---:|---:|---:|---:|
| 现行（无材料） | 0 | 546,588 | — | — |
| **own 192 + 材料 64（不切 own）** | 209,250 | 682,673 | **+136,085（+24.90%）** | **+36.24 s** |
| **own 128 + 材料 64（本预注册冻结）** | 209,250 | **560,268** | **+13,680（+2.50%）** | **+3.64 s** |
| own 192 + `min(64, 192−own)`（残余容量，仅对照） | — | 598,910 | +52,322（+9.57%） | +13.93 s |

- **为什么冻结 128/64**：`own 192 + 材料 64`（“不切 own、材料另加”）**违反 192 总上限**，列出只为显示它会把导入推过 60 s；`own 128 + 材料 64` 是唯一既守住 192 又把外推增量压到 +3.64 s 的切分。残余容量变体（own 192 + `min(64,192−own)`）虽守住 192，但让 **1,089 个节点材料行为 0**（own 已满 192），臂在这些节点上等于没接；本预注册不采用。
- **已知代价（必须写进判读）**：own 从 192 降到 128 会删掉 **129,576 条**现行节点特征（541,725 → 412,149，−23.9%）；材料侧新增 **143,256 条**新特征（与 own 128 去重后），净 +13,680。这是“替换 64 个 own 槽位”，不是“纯追加”。主集若回退，第一归因是 own 截断，不是材料特征无效。

### 0.4 12 条 v26 MISS 的静态可达性（写前实算，非读数）

对 `build/agent-batch31a/v26-device/device-fused-misses.txt` 的 12 条 MISS，按 §1.2 冻结规则计算“材料 64 行内是否出现节点自身 **128 行（本臂切分后）** 没有的查询特征”：

| # | expectedSlug | rank | own128 匹配 | 材料匹配 | 材料新增 | 新增特征（示例） |
|---:|---|---:|---:|---:|---:|---|
| 1 | 性状显隐性的判断 | 7 | 5 | 5 | 0 | — |
| 2 | 电解原理易错点 | 13 | 3 | 1 | 0 | — |
| 3 | 同分异构现象和同分异构体 | 12 | 1 | 0 | 0 | — |
| 4 | 利用奇偶性求值… | 8 | 2 | 1 | 0 | — |
| 5 | 函数图象的翻折变换 | 16 | 3 | 2 | **1** | 得到 |
| 6 | 方程组法… | 11 | 3 | 5 | **2** | 已知 / 解析式 |
| 7 | 判别式法… | 8 | 7 | 4 | **1** | 整理 |
| 8 | **等和线** | absent | 0 | 0 | 0 | **配额被最短标题材料占满，含查询特征的材料被排除**（见 §1.7） |
| 9 | 数列的单调性与最值问题 | 8 | 4 | 3 | **1** | 一项 |
| 10 | **活结与死结模型** | absent | 0 | 1 | **1** | 相等 |
| 11 | 力的分解 | absent | 0 | 0 | 0 | — |
| 12 | 共点力的动态平衡 | 51 | 1 | 1 | **1** | 缓慢 |

- 全 129 条中，**70 条**在材料 64 行内有至少 1 个 own128 没有的查询特征；12 条 MISS 中 **6 条**拿到 ≥1 个材料新增特征。
- 这是**结构可达性上限**，不是名次提升承诺；新增特征多为“得到/已知/解析式/整理/一项/相等/缓慢”这类通用词，其他节点也可能同样匹配，未必能把目标节点推入 top-5。

---

## 1. 机制（冻结）

### 1.1 输入与边界

- **输入字段**：每条材料的 **`title` + `summaryMarkdown`**；**`contentMarkdown` 不入**，`applicabilityMarkdown` / `boundaryMarkdown` 也不入。导入期读材料的 DAO 只 SELECT `material_id, knowledge_node_id, title, summary_markdown`，正文列不进入索引路径。
- **材料范围**：`knowledge_teaching_material.status != 'RETIRED'`；v26 全部 `ACTIVE`。绑定按 `knowledge_teaching_material_node_binding` 聚合到 `knowledge_node_id`；v26 全部 `PRIMARY`，本臂**不按 role 过滤**（未来出现 `SUPPORTING` 也计入；若执行轮发现 role 分布变化，先登记再决定，不得静默改）。
- **节点范围**：只给 `readSubjectKnowledgeRecallCandidates` 同域的节点建材料行——`verification_status IN ('CURATED','SOURCE_GROUNDED','USER_CONFIRMED')` 且 `status != 'RETIRED'`、`subject` 匹配；主题节点无材料绑定，不产生材料行。
- **材料表是派生数据**：不进内容备份、不计入材料总数、不写判定表/别名表；恢复后按 §1.3 重建。

### 1.2 抽取与配额（写死）

**材料特征抽取**（沿用 `KnowledgeSearchFeatureExtractor` 的确定性规则）：

1. `texts = [title, summaryMarkdown]`（title 在前，summary 在后）。
2. 全部 `lowercase(Locale.ROOT)`，按 `[^\p{L}\p{N}]+` 切片段，丢弃空片段。
3. 片段数 >16 时，按现有规则保留**末段 + 首段 + 均衡中间段**，共 16 段。
4. 每段产出：整段（2..48 字）+ 2/3-gram；按 `balancedStartIndexes` 的确定性顺序。
5. 多段流 round-robin 取特征，`featureLimit = 64`，结果去重（LinkedHashSet 语义）。

**节点自身特征**：仍用 `KnowledgeSearchFeatureExtractor.fromNode` 的 canonicalName + aliases.sorted() + boundary 与同一套片段规则，但 **`featureLimit` 从 192 降到 128**（`MAX_NODE_FEATURES` 拆分为 `MAX_OWN_NODE_FEATURES = 128` + `MAX_MATERIAL_NODE_FEATURES = 64`）。片段上限 16、单词上限 48、空集回退 canonicalName 均不变。

**每节点材料特征配额 ≤64 行（写死）**：

1. 把该节点绑定的 `ACTIVE` 材料按 **`(title.length asc, material_id asc)`** 排序；`title.length` 用 Kotlin `String.length`（UTF-16 code unit），`material_id` 用 `String.compareTo`（ASCII hex，等价字典序）。
2. 按该顺序逐材料、逐特征（材料内按 §1.2 第 5 步的顺序）走；遇到该节点尚未出现的特征，就记一行，归属**当前材料**；**首个产生该特征的材料拥有它**（同一特征不重复归属）。
3. 累计到 **64 行不同特征**即停；不足 64 行按实际行数落表。
4. 配额计数是**材料特征行**（去重后），不是材料条数；`title.length` 只决定材料顺序，不截断 title 文本。
5. 节点总特征 = own ≤128 + material ≤64 ≤ **192**；own 与 material 分表存储，查询期做 max（§1.4），不做并集求和。

### 1.3 导入期落表

- **新表** `knowledge_search_material_feature`：

  | 列 | 类型 | 说明 |
  |---|---|---|
  | `subject` | TEXT NOT NULL | 与节点同科；查询过滤用 |
  | `material_id` | TEXT NOT NULL | 特征归属材料 |
  | `knowledge_node_id` | TEXT NOT NULL | 绑定节点 |
  | `search_feature` | TEXT NOT NULL | 特征串 |

  主键 `(material_id, knowledge_node_id, search_feature)`；索引 `(subject, search_feature)`（查询 IN 查找）与 `(subject, knowledge_node_id)`（按节点聚合）。外键 `material_id → knowledge_teaching_material(material_id)`、`knowledge_node_id → knowledge_node(knowledge_node_id)`，均 `ON DELETE CASCADE`（派生行随材料/节点物理删除；退役是 UPDATE，见维护点）。
- **schema 71 → 72**：迁移只建空表，不搬内容；`STUDY_DATABASE_VERSION` 71→72（`StudyDatabase.kt:139`）。执行轮必须先删除 §0.1 的 L1 孤儿 `72.json`，再由 Room 导出 L2 的 `72.json`。
- **`INDEX_VERSION` 1 → 2**（`KnowledgeSearchFeatureExtractor.kt:28`）：节点侧抽取规则变了（own 192→128 + 材料行），旧库锚点不等 ⇒ `ensureKnowledgeSearchIndex` 整科换血。**不得**沿用 L1 的锚点/版本语义；L1 未采纳，其 `72.json` 不是本臂基线。
- **`KnowledgeSearchIndexBuilder.rebuildSubject`**（`KnowledgeSearchIndexBuilder.kt:108-191`）：
  1. 读相（同一 `withReadTransaction` 快照）在现有三条查询外，增加一条“该科材料 + 绑定 + title + summary”的查询（新增 DAO 方法，放 `KnowledgeTeachingMaterialDao.kt`）；不读 `content_markdown`。
  2. 抽取（own 128 + material 64）在**写事务外**做，沿用 `extractMs` 口径；日志追加 `materialNodes=… materialFeatures=…`，保留 `tookMs=` 前缀兼容。
  3. 删相/插相仍按 `REBUILD_NODE_CHUNK_SIZE = 64` 节点分块短写事务；材料行随同一节点块插入（每块 ≤64×64 = 4,096 行），**不得**回到一条长写事务（KD-51）。
  4. 锚点最后写；`knowledge_search_index_state` 的 `INDEX_VERSION=2` 是“本次跑完”的锚。
- **维护点（每处都必须与材料/绑定写同事务或显式重建）**：

  | # | 路径 | 动作 |
  |---:|---|---|
  | 1 | `KnowledgeSearchIndexBuilder.rebuildSubject` | 删/插材料行；锚点最后 |
  | 2 | `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex` 只补缺分支 | 补材料行；与 own 行同事务 |
  | 3 | `RoomKnowledgeContentReconciler` 内容安装/退役 | 现有逐科 `rebuildSubject` 已覆盖；确认材料行随科重建 |
  | 4 | `KnowledgeGroundingDao.applyReviewedPack` | 若写入材料/绑定，置该科 `INDEX_VERSION` 失效或直接 `rebuildSubject`；不得只写材料表不更新索引 |
  | 5 | `retireMaterial` / `retireNodes` | 同事务删对应材料行（或置该科失效）；退役材料的行不得留在索引里 |

  - 读路径不新增“材料行是否完整”的独立门：`ensureKnowledgeSearchIndex` 的现有完整性/锚点门已覆盖；若材料行缺失但锚点当前，属于本臂缺陷，执行轮必须用 DB 计数（§7）暴露，不得用“锚点当前”掩盖。

### 1.4 查询期聚合 = max（沿用）

- **own 分**：`ownScore(n) = COUNT(DISTINCT feature.search_feature)`，只数 `knowledge_search_feature` 的 own 行；口径与 v26 逐字相同（本臂**不接 L1 BM25**）。
- **材料分**：`materialScore(n,m) = COUNT(DISTINCT search_feature)`，只数 `knowledge_search_material_feature` 中 `(knowledge_node_id=n, material_id=m)` 的行。
- **节点分**：`nodeScore(n) = max(ownScore(n), max_m materialScore(n,m))`。无材料行的节点 `nodeScore = ownScore`（与 v26 完全一致）；own=0 但材料分>0 的节点可进入候选域。
- **为什么是 max**：沿用 dense 节点分的 max 语义（`DenseVectorAsset.kt:51-65`：节点分 = 节点全部向量 cosine 的 max）与外部研究波 L2 的“节点聚合（max）”（`2026-11-01-kb-external-research-wave.md:11`）。**不是**把材料分加到 own 上：一个节点最多 146 条材料，求和会让“材料多”的节点霸榜，max 只取最强单材料。
- **参考 SQL 形状**（单一来源；Room 若拒 CTE，用 `@RawQuery` 同 SQL，语义不变）：

  ```sql
  WITH matched AS (
    SELECT knowledge_node_id, 'NODE' AS source_key, search_feature
    FROM knowledge_search_feature
    WHERE subject = :subject AND search_feature IN (:searchFeatures)
    UNION ALL
    SELECT knowledge_node_id, 'MAT:' || material_id AS source_key, search_feature
    FROM knowledge_search_material_feature
    WHERE subject = :subject AND search_feature IN (:searchFeatures)
  ),
  per_source AS (
    SELECT knowledge_node_id, source_key, COUNT(DISTINCT search_feature) AS match_count
    FROM matched GROUP BY knowledge_node_id, source_key
  ),
  node_score AS (
    SELECT knowledge_node_id, MAX(match_count) AS matchCount
    FROM per_source GROUP BY knowledge_node_id
  )
  SELECT node.*, node_score.matchCount
  FROM knowledge_node AS node
  INNER JOIN node_score ON node_score.knowledge_node_id = node.knowledge_node_id
  WHERE node.verification_status IN ('CURATED','SOURCE_GROUNDED','USER_CONFIRMED')
    AND node.status != 'RETIRED'
  ORDER BY node_score.matchCount DESC,
           CASE node.granularity WHEN 'ATOMIC' THEN 0 ELSE 1 END,
           node.canonical_name ASC,
           node.knowledge_node_id ASC
  LIMIT :limit
  ```

- **不改的检索常数**：候选深度 **512**（`StudyDatabasePort.kt:43`）、top-5、5 samples（`GoldenRetrievalInstrumentedTest.kt:624-625`）、融合 `α=0.5` / 域内 min-max / 缺腿给 0 / 并列 node_id（`DenseFusion.kt` 零改）。`KnowledgeRecallCandidateRow.matchCount` 仍是 `Int`；dense reranker 只收 `Int` 分，类型不变。
- **查询期不做**：不在 SQL 调 `ln()`/数学函数（API 23 兼容）；不做逐材料全表扫描；不做材料分求和。

### 1.5 每一项新增消灭的具体失败（逐个指认）

| 新增 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| 材料 title+summary 入索引 | 节点 own 表面（canonical/alias/boundary）不含材料标题/摘要里的词汇，查询词只在材料里出现时节点进不了候选域 | 外部研究波 L2；`等和线` 33 条材料中 18 条摘要含“等和线”、21 条含“平行”（本会话实算；**该例在冻结行序下未被覆盖，见 §1.7-2**） |
| 每节点材料 64 行配额 | 65,021 条材料 × 每材料最多 64 特征 = 无界行膨胀，导入必破 60 s | §0.3：材料行总和 209,250，已远大于 own 行 |
| `(title.length, material_id)` 行序 | 同一节点多材料时“哪 64 行”不确定；不同设备/不同 sidecar 顺序会产出不同索引 | 冻结为纯函数顺序；`title.length` + ASCII `material_id` |
| own 128 + material 64 的 192 切分 | own 192 已满时材料行无处可放；或总行超 192 破坏既有上限契约 | `KnowledgeSearchFeatureExtractor.kt:8` 的 192 |
| 独立 `knowledge_search_material_feature` 表 | 改 `knowledge_search_feature` 主键/混入材料行，会让 own 查询与自愈计数口径漂移 | 现有表 PK `(subject, search_feature, knowledge_node_id)`（`KnowledgeSearchFeatureEntity.kt:9-12`） |
| 查询期 `max(own, max_m material)` | 材料分求和让材料多的节点霸榜；也避免材料行与 own 行重复计数相加 | `DenseVectorAsset.kt:51-65` 的 max 语义 |
| `INDEX_VERSION` 1→2 | 旧库锚点=1，抽取规则已变却复用旧索引，材料行永远不建 | `KnowledgeSearchFeatureExtractor.kt:28` 的版本语义 |
| 导入期 `rebuildSubject` 读材料 | 另建离线资产/旁车会引入第二套内容源与代际错配（标题向量臂已实证过装置缺陷） | `2026-10-23-title-vector-arm-prereg.md:89-98` |
| 材料行维护点 | 内容安装/退役后材料行静默过期，查询命中已退役材料 | §1.3 维护点 3–5 |
| 判官冻结脚本（§5） | 判官文件被顺手改掉，臂间读数不可比 | §5 断言 |
| 导入 ≤60 s 硬门（§2.2） | 召回升了但首启安装超预算，仍被采纳 | F6 既有门（`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:145-154`） |
| 静态可达性表（§0.4） | 把“臂没有结构可达面”误判成“材料特征无效” | 本会话 12 MISS 实算 |

### 1.6 明确不改（一条都不许动）

1. **内容**：主包 `96246c78…`、manifest `370b0979…`、25 sidecar（含 v2-25 `93269182…`/159）、材料 65,021、节点 3,280、别名 40,841；不建点、不改 title/summary/content/boundary。
2. **dense**：`.vec` `70329115…`、`.vec.json` `5cbbcb6f…`、模型、查询编码、`DenseRecallAssembly` 开关；不重打资产。
3. **融合**：`DenseFusion.kt`（α=0.5 / min-max / 缺腿 0 / 并列 node_id）零改；`DenseRecallReranker` 的 `lexicalScore: Int` 零改。
4. **检索参数**：候选深度 512、top-5、5 samples、WHERE 过滤、subject 作用域。
5. **判官与金标**：`GoldenRetrievalInstrumentedTest.kt` `9fae203f…`、`GoldenRetrievalJvmTest.kt` `62c232fc…`、`golden_queries_v2.json` `eabef102…` 零改；JVM 镜像 `RetrievalBenchmark.kt` 是本臂可写面（§5.4），不是判官。
6. **own 抽取的片段规则**：16 片段、48 字、`[^\p{L}\p{N}]+` 切分、空集回退不变；只把 `featureLimit` 192→128。
7. **材料正文**：`contentMarkdown` / `applicabilityMarkdown` / `boundaryMarkdown` 不进索引；材料查询不 SELECT 正文列。

### 1.7 冻结规则的静态后果与风险（写前实算，非读数）

1. **配额饱和**：3,232/3,280 节点命中 64 行；1,785 节点（54.4%）由**首条材料单独占满**。行序按 title 长度升序，短标题材料优先，长标题材料可能一行都进不去。
2. **`等和线` 的具体后果**：该节点 33 条材料、18 条摘要含“等和线”、21 条含“平行”；但最短标题材料 `ext-mat-91e0c2ace8-059`（title 6 字）单条产出 64 特征，**占满配额**；含 4 个查询特征的 `ext-mat-1110ca3dd4-006` 排在配额之外。按冻结行序，该节点的材料腿匹配数 = **0**，本臂**不能**靠 `等和线` 兑现外部研究波点名的“词汇缺口”。这是冻结规则的直接后果，不是实现缺陷；若主会话要保留该示例，必须在预注册阶段改行序/配额（例如按材料 round-robin 或先按材料覆盖再取行），本文件不擅自改。
3. **own 截断代价**：own 192→128 删掉 129,576 条现行特征（−23.9%）；材料侧新增 143,256 条（去重后），净 +13,680。主集若回退，先归因 own 截断，再谈材料特征。
4. **静态可达面**：12 条 MISS 有 **6 条**拿到 ≥1 个材料新增特征（翻折 / 方程组 / 判别式 / 数列 / 活结 / 共点力）；全 129 条有 **70 条**至少 1 个材料新增特征。新增特征多为通用词，不能当作名次提升证据。主集严格更高的现实路径仍是“已在候选域内的 9 条排序提升 + 少数 absent 节点入域”，不是“absent 节点必然被救回”。
5. **不承诺 +N**：上述是结构上限，不是预测；臂可能 0 增益或回退。

---

## 2. 判读（写死）

### 2.1 三条规则 + 零漂移 + 显著性

| # | 规则 | 判据 |
|---:|---|---|
| 1 | **主集严格更高** | 设备融合 Recall@5 **严格大于**同代 v26 基线，且 **≥ 118/129 = 0.9147286821705426**（对冻结的 117/129 下限）；`A_fused > B_fused` 与绝对值下限同时成立才算过。equal 不算过。 |
| 2 | **24 章逐章不劣** | 24 章逐章融合 Recall@5 与同代 v26 基线比较；任一章**严格更低**即不采纳；equal/improved 均通过。 |
| 3 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = 本臂、B = 同代 v26**；A−B 的 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高）。 |

- **符号口径（冻结，防写反）**：`tools/kb_coverage/retrieval_significance.py` 输出 `delta_recall_b_minus_a` 与 `ci95_chapter`，是 **B−A**。A−B 的 95% CI = `[−ci_hi, −ci_lo]`；规则 3 通过条件是 **`−ci_lo ≥ 0`，即 `ci_lo ≤ 0`**。§7 必须同时给工具原始字段与换算后的 A−B 区间。
- **显著性**：同一 bootstrap 的 `p_two_sided` 只作报告，不替代规则 1/2；不得用“p 不显著”或“p 显著”改写规则 1 的严格更高。
- **零漂移 = 0（装置门，不参与好坏）**：同路 device↔JVM 的 **fused 与 lexical 两条** MISS query 集合 symmetric difference 必须 **0**。非 0 = 装置缺陷，本臂**不判读**；不得用逐章相等或主集读数绕过。
- **同代性**：同一 emulator 序列/API、同一金标 sha、同一主包 contentVersion、同一 dense `.vec` sha、同一融合常数、同一候选深度；`samegen=MATCH` 才判读。任一不成立 ⇒ 规则 1/2/3 记 `NOT_RUN`。
- **B 臂必须同轮重取**：117/129 是 v26 已落盘读数（`build/agent-batch31a/v26-device/device-metrics.txt:3-5`）；若同轮复测 B ≠ 117/129，规则 1 的严格比较用**复测 B**，117/129 只作绝对下限。**不得**用 L1 的 115/129、v25/v24 旧设备数、JVM 离线数或 stage3 数冒充 B 臂。

### 2.2 导入 ≤60 s 硬门

- **判定点**：`KnowledgeInstall: install finished in … ms (full path: parse + reconcile)`（`BundledKnowledgeBaseInstaller.kt:31,46-70`）；首启**全量冷装**（`pm clear` 或新装），不是快路径 `(fast path: content stamp matches, no parse, no reconcile)`。
- **门槛**：**≤ 60,000 ms**。超门即**不采纳**，即使规则 1/2/3 全过；不得把“召回收益”与“导入预算”分开记成“部分采纳”。
- **同口径**：同一 emulator/API、同一 APK、同一 v26 内容包；至少 3 次冷装，报中位/最大；最大 ≤60,000 ms 才算过。
- **L1 读数不可挪用**：L1 的 47,470 ms（`docs/kb-batch32a-2026-11-01.md:125`）是 L1 臂读数，**不是 L2 的导入证据**；L2 必须自测 v26 基线 + L2 两臂。
- **写前外推（不是实测）**：128/64 切分 +13,680 行，按 0.2663 ms/行外推 **+3.64 s**；192/64 切分 +136,085 行，外推 **+36.24 s**，叠加 51.6 s 基线会超门。外推只用于选切分，不替代 §7 的实测。

### 2.3 基线口径与 24 章（v26 设备读数）

**主集**（`build/agent-batch31a/v26-device/device-metrics.txt:2-5,33-35`）：

| 项 | v26 基线 |
|---|---:|
| 设备融合 Recall@5 | **117/129 = 0.9069767441860465** |
| 设备融合 MRR | **0.7239018087855298** |
| 设备词面 Recall@5 | **101/129 = 0.7829457364341085** |
| 设备词面 MRR | **0.6178294573643413** |
| p50 / p95 / max / overBudget | 49 ms / 78 ms / 190 ms / 0 |

**24 章逐章基线**（同文件 `:6-30`；执行轮按同章重算，不得换章名/合并章）：

| # | chapter | 基线命中/总数 |
|---:|---|---:|
| 1 | 生物学必修1·第五章·细胞呼吸的原理和应用 | 3/3 |
| 2 | 生物学必修2·第一章·遗传的基本规律 | 8/9 |
| 3 | 生物学选择性必修3·第三章·重组DNA技术的基本工具 | 1/1 |
| 4 | 生物学选择性必修3·第三章·基因工程的基本操作程序 | 2/2 |
| 5 | 化学必修第一册·第三章·铁与金属材料 | 10/11 |
| 6 | 化学选择性必修3·第一章·有机化合物的结构特点 | 1/1 |
| 7 | 化学必修第二册·第七章·有机化合物 | 1/2 |
| 8 | 化学必修第二册·第五章·硫氮及其化合物 | 9/9 |
| 9 | 化学选择性必修1·第一章·反应热与能量 | 1/1 |
| 10 | 化学必修第二册·第六章·化学反应与能量 | 8/8 |
| 11 | 化学选择性必修1·第三章·水溶液中的离子平衡 | 3/3 |
| 12 | 数学必修第一册·第三章·函数的概念与性质 | 7/11 |
| 13 | 数学必修第一册·第二章·不等式 | 3/3 |
| 14 | 数学必修第二册·第六章·平面向量 | 2/3 |
| 15 | 数学选择性必修第一册·第二三章·解析几何 | 9/9 |
| 16 | 数学选择性必修第三册·第六章·计数原理 | 3/3 |
| 17 | 数学选择性必修第二册·第四章·数列 | 10/11 |
| 18 | 物理必修第一册·第一章·运动的描述 | 8/8 |
| 19 | 物理必修第一册·第四章·牛顿运动定律 | 1/1 |
| 20 | 物理必修第一册·第三章·相互作用 | 9/12 |
| 21 | 物理必修第三册·第十一十二章·恒定电流 | 9/9 |
| 22 | 物理必修第二册·第八章·机械能与能量守恒 | 3/3 |
| 23 | 物理选择性必修第一册·第一章·动量 | 3/3 |
| 24 | 物理选择性必修第三册·第一至三章·热学 | 3/3 |

- 合计 **117/129**；6 章有 MISS（#2/#5/#7/#12/#14/#17/#20），其余 17 章满分（#12 与 #20 是回退风险最高的两章）。
- **24 章规则只看融合路**；词面路只报读数，不判分。

### 2.4 报告义务（§7 至少包含）

- A/B 两臂：设备融合与词面的 Recall@5、MRR、p50/p95/max、overBudget；24 章逐章 lower/equal/higher。
- 零漂移两路 symdiff；bootstrap 20k/seed0 原始 JSON 与 A−B 换算区间；`p_two_sided`。
- 首启导入：v26 基线 + L2 各 3 次冷装的 `install finished in` 毫秒、中位/最大、是否 ≤60,000。
- DB 计数：`knowledge_search_feature` 行数、`knowledge_search_material_feature` 行数、材料特征节点数、每节点材料行分布（min/中位/max/命中 64 数）、`INDEX_VERSION`、schema 版本。
- §0.3 的 own 192/128 分布、材料配额饱和数、`等和线` 的配额归属（是否仍被首条材料占满）。
- 双门 exit code；§4 逐文件写后 sha；§5 判官断言脚本输出。
- 任何未跑项写 `NOT_RUN` / `UNVERIFIED`，不得留空或写“无显著差异”。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | **exit 0**（一条命令、两个 task、单一 exit code） |
| 2 | `./gradlew :core:data:compileDebugUnitTestKotlin :core:data:assembleDebugAndroidTest --console=plain` | **exit 0**（一条命令、两个 task、单一 exit code） |

- 门 1 覆盖新表/迁移/DAO/配额单测/设备迁移测试；门 2 覆盖 JVM 镜像与设备检索套件。**门数仍为 2，判据仍为 exit 0。**
- 任一门红 ⇒ **不写 release、不跑设备、不判读**；不得先跑设备再补门。
- 两门日志落 `build/agent-batch33/l2-material/gates/`（建议），§7 引用；不得只写“已跑”。
- **当前工作树不是干净基线**：493 项脏、孤儿 `72.json`、L1 源码件已不可见；执行轮必须先删除孤儿 72、确认触碰文件未被其他线覆盖，再跑门。**本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚（触碰清单 + pre-sha）

### 4.1 A. 生产源码（当前工作树 pre-sha；均与 HEAD blob 同值，除注明）

| # | 路径 | 预期动作 | pre-sha256（当前工作树） |
|---:|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt` | own 192→128；材料抽取函数；`INDEX_VERSION` 1→2 | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt` | 读材料；抽材料行；分块插；日志字段 | `db734746f0d3c255af63647febac1ad85867e7f160eee8edebf74b58c45fc416` |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt` | 召回 SQL 改 max(own, material)；行类型不变 | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 读路径透传；只补缺分支补材料行 | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt` | version 71→72；实体入列；迁移挂载 | `7210f974fbfd1f447e8165c5ef2becd05a8105787cf2c4a5a4a91e7f98ef8336` |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 新增按科读材料+绑定的索引查询（只读 title/summary） | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` |
| 7 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeContentReconciler.kt` | 确认/补充材料行失效与重建 | `e6ef7cc01342ca2cd3035b08d395d4ad1e8d61b5d7e72334f6c255bdd64e3142` |
| 8 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeGroundingDao.kt` | 材料/绑定变更后置失效或重建 | `5dd4ec659fcda8b4aabf644ba13809e0935a86579150fe4197fa2f890cc64f6b` |
| 9 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureImportEquivalenceTest.kt` | harness 建材料表；镜像 SQL 改 max | `27e1ef312fa1fcdd881fe1df54fc098e06808ce466c0c26a8cae6afd70f60c17` |
| 10 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt` | JVM 镜像加材料抽取/配额/max（**可写面**，非判官） | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |

### 4.2 B. 新增文件（写前不存在）

| # | 路径 | 动作 | pre-sha256 |
|---:|---|---|---|
| 11 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/KnowledgeSearchMaterialFeatureEntity.kt` | 新 `@Entity` | **不存在** |
| 12 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchMaterialFeatureMigration_71_72.kt` | 建空表 | **不存在** |
| 13 | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json` | Room 导出（生成） | **存在（L1 孤儿，382,428 B）；L2 写前删除并重新生成，回滚时删除，不恢复** |
| 14 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/MaterialFeatureQuotaTest.kt` | 配额/行序/192 切分/去重单测 | **不存在** |
| 15 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchMaterialFeatureInstrumentedTest.kt` | 71→72、材料行、max 查询、退役失效 | **不存在** |
| 16 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/MaterialFeatureMirrorParityTest.kt` | JVM 镜像与手算配额对拍 | **不存在** |

### 4.3 C. 只读 / 零触碰（登记供核验，不得改）

| # | 路径 | 期望 | pre-sha256 |
|---:|---|---|---|
| 17 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | 零改 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 18 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | 零改 | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` |
| 19 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-25.json` | 零改 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552` |
| 20 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 21 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 零改 | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b`（v26-final 记 `5cbbcb6f…` 为陈旧记录；本会话实测 `f0512a47`/`70611e61` 同值 `6612a28d…`） |
| 22 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 23 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` | 零改（设备判官） | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` |
| 24 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt` | 零改（JVM 判官；工作树 `M` 属他线） | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` |
| 25 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseFusion.kt` | 零改 | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 26 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabasePort.kt` | 零改（512 深度） | `dc1ba2e2dc1f65f5d3620e0582e4597e6c7ac919555855e9b3405cf9e78c05b7` |
| 27 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/KnowledgeSearchFeatureEntity.kt` | 零改（own 表 PK 不动） | `9da364a46c629523e8c608172d04e06a0f771c44e0f3a43b1d29eab266791213` |
| 28 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/entity/KnowledgeTeachingMaterialEntities.kt` | 零改 | `97f3277fc425cd0dadcf925bc2b813fe800b8e0976f5b5ca214de1b054e516e9` |

**他线 `M` 不裹挟**：`GoldenRetrievalJvmTest.kt`（`62c232fc…`）、`PendingCaptureDao.kt`、`TutorVisualPort.kt` 的工作树改动不在本臂写面；不得 stage、不得回滚。

### 4.4 写前备份 / 回滚 / 采纳

**写前备份（执行轮第 1 步之前）**

1. 把 §4.1 的 10 件复制到 `build/agent-batch33/l2-material/backup/`，落 `sha256sum` 全表。
2. `git status --porcelain -- <§4.1 路径>` 必须为空（本臂写前）；若非空，先归因他线并停止，不得覆盖。**例外**：`GoldenRetrievalJvmTest.kt` 是已知他线 `M`，不属本臂，不备份不恢复。
3. 删除 §4.2 的 L1 孤儿 `72.json`；复核 v26 身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；主包 `96246c78…`；v2-25 `93269182…`/159；dense `70329115…`；金标 `eabef102…`；DB 版本 71。

**回滚（任一规则失败、双门红、零漂移非 0、导入 >60 s）**

1. **不得** `git checkout` 整树（493 项脏项）；只按 §4.1/§4.2 逐文件恢复/删除。
2. 用 `backup/` 覆盖 §4.1 的 10 件；删除 §4.2 的 6 件（含生成的 `72.json`）；删除本臂生成的 `build/agent-batch33/l2-material/` 运行产物（保留备份与回滚记录）。
3. **设备侧必须清库**：Room 不支持 schema 72→71 降级；`adb shell pm clear <appId>`（或卸载重装）后安装 v26 APK，再核 `version=26`。不得直接覆盖安装。
4. 核验：材料表不存在；主包/dense/金标/manifest/v2-25 sha 与 §0.2 一致；材料 65,021；节点 3,280；DB 版本 71；`knowledge_search_feature` 行数回到约 546,588（或 DB 实测 v26 值）。
5. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
6. §7 写明回滚原因与逐文件 sha；不得把“已回滚”写成“未采纳但保留改动”。

**采纳（三条规则全过 + 零漂移 0 + 双门 0 + 导入 ≤60 s）**

1. 保留 §4.1/§4.2 的写面与 72 schema；`INDEX_VERSION=2`；材料表行数按实测登记。
2. **不改内容包/manifest/version**：本臂是代码+派生索引臂，不是内容发布；不得借采纳 bump manifest version 或改 sidecar。
3. §7 登记 stateAfter：manifest/contentVersion（预期不变）、主包/dense/金标 sha、DB version 72、`knowledge_search_feature` 与 `knowledge_search_material_feature` 行数、每节点材料行分布、双门 exit code、两臂四路读数、导入毫秒。

---

## 5. 判官冻结（实现前后脚本断言）

### 5.1 核账基线判官表

| 角色 | 路径 | 基线 sha256 | 当前工作树 | 本臂可写？ |
|---|---|---|---|---|
| 设备判官 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` | 同值（干净） | **否** |
| JVM 判官 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt` | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` | 同值（`M`，他线路径注；HEAD `8b3b6da0…`） | **否** |
| JVM 镜像 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt` | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` | 同值（干净） | **是**（镜像必须跟臂，见 §5.4） |
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`（129 条 / 24 章） | 同值 | **否** |

- **设备判官口径说明（重要）**：v26 收束表 `build/agent-batch31a/v26-final.md:23` 只登记 JVM 判官 `62c232fc…` 与镜像 `8c2a2ef0…`；本会话用 `git show f0512a47:…GoldenRetrievalInstrumentedTest.kt | sha256sum` 实测 **`9fae203f…`**，与当前工作树一致。旧文档里的设备判官 `c2280255…`（如 `2026-10-23-title-vector-arm-prereg.md:55`）**不是当前树值**，是更早的工作树变体；若执行轮看到 `c2280255…`，说明判官不同代，**停**，不得用本预注册的 117/129 直接比较。
- **JVM 判官**：`GoldenRetrievalJvmTest.kt` 工作树 `M` 只含 batch13→14 输出路径注释（`docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md:28`）；本臂不改、不 stage、不回滚。
- **JVM 镜像**：`RetrievalBenchmark.kt` 是装置镜像，不是判官；本臂必须把它改成同一材料抽取/配额/max，否则零漂移必红。改后 sha 记 §7，并跑 `MaterialFeatureMirrorParityTest`。

### 5.2 断言脚本（实现前、实现后各跑一次；任一不等即停）

```bash
set -euo pipefail
check() {
  actual=$(sha256sum "$1" | cut -d' ' -f1)
  if [ "$actual" != "$2" ]; then
    echo "JUDGE_DRIFT path=$1 expected=$2 actual=$actual" >&2
    exit 2
  fi
}
check core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt \
  9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e
check core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt \
  62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95
check tools/kb_coverage/tables/golden_queries_v2.json \
  eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102
echo "JUDGE_FREEZE_OK"
```

- 脚本只覆盖**设备判官 / JVM 判官 / 金标**三件；JVM 镜像不在冻结脚本里（它是臂写面）。
- 实现前跑一次（确认基线未漂）；实现后、出数前再跑一次（确认臂没有顺手改判官）。两次都 `JUDGE_FREEZE_OK` 才可进入 §3 门。
- 本会话已用 `sha256sum` 实测三件与表中值一致（§0.2/§5.1）；**未执行**脚本本身（它是执行轮的门，不是写作时的通过声明）。

### 5.3 判官/金标零改的边界

- 本臂不改判分口径、不改 129 条金标、不改 24 章、不改 top-5/5 samples、不改融合常数。
- `ProductionLexicalLegExportTest.kt`（`bbc21014…`）不是判官；本臂不重钉其锚点，除非采纳后另立“重钉”步骤（§7 登记旧值/新值）。
- 任何判官 sha 漂移 ⇒ 本臂**不判读**，先重录判官状态并核对判据未变；不得用“判官只是注释变化”自行放行（`62c232fc…` 的 `M` 是已登记的例外，不是新例外）。

---

## 6. 未做 / 未验证（写作时点）

- **未写任何代码/迁移/测试**：§1 的抽取、配额、max SQL、材料表、维护点均为冻结定义，不是实现。
- **未跑 Gradle/设备/JVM/判官**：§2/§3/§5 是判据与门定义，不是通过声明。
- **未取 L2 读数**：没有 L2 的设备/JVM/零漂移/导入实测；117/129 是 v26 基线，不是 L2 读数。
- **导入外推不是实测**：+3.64 s / +36.24 s 是按 0.2663 ms/行外推；v26 基线与 L2 的 3 次冷装均未跑，60 s 门 UNVERIFIED。
- **own 截断代价未实测**：own 192→128 删 129,576 条特征（−23.9%）是静态计算；对召回的实际影响未测。
- **`等和线` 的冻结后果已登记**：按冻结行序，其 64 行配额被最短标题材料占满，材料腿匹配=0；本文件不擅自改规则，若主会话要改，须另立预注册。
- **工作树不是干净基线**：493 项脏、孤儿 `72.json`、L1 源码件已不可见；执行轮必须先清孤儿并确认触碰面。
- **设备判官历史值不一致**：旧文档 `c2280255…` 与当前树 `9fae203f…` 不同；本预注册以 `9fae203f…` 为核账基线，执行轮若见前者即停。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1/§4.2 逐文件写后 sha；DB 计数（`knowledge_search_feature` / `knowledge_search_material_feature` / 每节点材料行分布 / `INDEX_VERSION=2` / schema 72）；双门 exit code；§5 断言脚本两次输出；v26/L2 两臂设备融合与词面 Recall@5/MRR、24 章 lower/equal/higher；零漂移两路 symdiff；bootstrap 20k/seed0 原始 JSON 与 A−B 换算区间；首启导入 3 次 `install finished in` 毫秒与 ≤60 s 判定；`等和线` 的配额归属复核；未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 7. 执行记录（批次 32B / L2 实现，2026-11-02T04:08:47+0800）

> 本节只追加，§1–§5 未改。**本节不是采纳声明**：只完成了 ①builder 材料来源、②本地时延验证、③单测、④判官 sha 自验；设备判读/24 章/零漂移/导入门均未跑。

### 7.1 写前守卫、备份与写后状态

| 项 | 值 | 方式 |
|---|---|---|
| HEAD | `70611e611b81aad15b65330310f953c4de563bab` | `git rev-parse HEAD` |
| 写前整树脏项 | **494** | `git status --porcelain \| wc -l` |
| §4.1 目标文件写前 porcelain | **0** | `git status --porcelain -- <10 paths>` |
| 写后整树脏项 | **517** | `git status --porcelain \| wc -l`（含本臂 17 件与他线未跟踪产物） |
| 备份 | `build/agent-batch33/l2-material/backup/pre-write.tar`（§4.1 10 件）+ `extra/`（3 件额外改动，取自 HEAD） | 本会话实跑 |
| L1 孤儿 72.json | 已删除，由 Room KSP 重新导出 L2 的 72.json | `rm` + `:core:database:compileDebugKotlin` |

**与预注册 §4.1/§4.2 的差异（如实登记）**：

1. **多改 3 件**：`KnowledgeSearchFeatureBatchInsert.kt`（材料行批量 VALUES，导入时延需要）、`KernelWave0SchemaContractTest.kt`（schema 头 71→72 的字面量）、`KnowledgeSearchFeatureExtractorTest.kt`（own 128 断言）。三者写后 sha 见 `build/agent-batch32b/l2-post-sha256.txt`。
2. **多增 2 个测试/资源**：`MaterialFeatureScaleTest.kt`（§2.2 的本地时延/行预算验证）、`KnowledgeSearchMaterialFeatureInstrumentedTest.kt`（§4.2 #15 的真库召回/迁移表验收）。`MaterialFeatureMirrorParityTest.kt` **未新增**。
3. **`KnowledgeGroundingDao.kt` 零改**：`applyReviewedPack` 只写节点/own 特征，不写材料/绑定，§1.3 维护点 #4 不适用。
4. **`retireMaterial/retireNodes` 未加显式 `withWriteTransaction`**：按调和器既有"差分幂等、不是大事务"的纪律，退役后立刻按候选 id 幂等删材料行（`deleteMaterialSearchFeaturesForMaterials/Nodes`）；崩溃后下一次安装会重跑删除并收敛。

### 7.2 判官冻结（实现前 / 实现后，脚本 §5.2）

两次均输出 `JUDGE_FREEZE_OK`（本会话实跑）：

| 角色 | sha256 | 结果 |
|---|---|---|
| 设备判官 `GoldenRetrievalInstrumentedTest.kt` | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` | OK |
| JVM 判官 `GoldenRetrievalJvmTest.kt` | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` | OK |
| 金标 `golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | OK |
| JVM 镜像 `RetrievalBenchmark.kt`（本批**只读**约束） | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` | OK |

> **口径冲突（必须由主会话裁定）**：本批 ask 要求 `RetrievalBenchmark.kt` 与基线一致，故镜像**未**按 §5.4 改成 own 128 + 材料 64 + max。因此**零漂移在本批不可能通过**，本文件 §2.1 的零漂移门保持 `NOT_RUN`，不得用本批读数判读。

### 7.3 装置门（§3 双门，本会话实跑）

| 门 | 命令 | exit | 日志 |
|---:|---|---:|---|
| 1 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | **0** | `build/agent-batch33/l2-material/gates/gate1-final.log` |
| 2 | `./gradlew :core:data:compileDebugUnitTestKotlin :core:data:assembleDebugAndroidTest --console=plain` | **0** | `build/agent-batch33/l2-material/gates/gate2-final2.log` |

### 7.4 JVM 单测与本地时延（无设备）

- **`:core:database:testDebugUnitTest` 全量：241 tests / 0 failures / 0 errors**（`unit-all-final.log`）。
- L2 专项：
  - `MaterialFeatureQuotaTest`：7 tests（配额 64、行序 `(title.length, material_id)`、首材料拥有共享特征、无材料节点零行、摘要词入特征、own+material ≤192、**迁移 DDL 与 72.json createSql 同义**）。
  - `KnowledgeSearchFeatureExtractorTest`：7 tests（新增 own ≤128）。
  - `KnowledgeSearchFeatureImportEquivalenceTest`：1 test / 0 fail（15.5 s；own 192→128 后语料 412k 行下界已按实测调整，三臂行集/金标命中序列仍逐条相同）。
  - `MaterialFeatureScaleTest`：**v26 全量侧车** 65,021 材料 / 65,021 绑定 / 3,280 材料节点；**材料行 209,236**；命中 64 行节点 **3,232**；首条材料单独占满 **1,774**；材料抽取+配额归属 **1,424 ms**（`msPer1kRows=6.806`）。
- **与 §0.3 静态估算的差**：209,250 → 209,236（−14 行）；首材料饱和 1,785 → 1,774（−11 节点）。差异归因于静态脚本对 `\p{L}\p{N}` 的近似切分（非 ASCII 字母/数字），不是配额/行序规则；§2.4 已写死"执行轮以 DB `COUNT(*)` 为准"。
- **插入耗时未单独实测**：`1,424 ms` 只是抽取+归属的 CPU 部分；材料行的 SQLite 批量写（200 行/语句）在 JVM 等价门里随 own 行一起跑，但没有单独计时。设备 60 s 门 `UNVERIFIED`。

### 7.5 设备侧（emulator-5562，Android 14）

| 套件 | 结果 | 证据 |
|---|---|---|
| `KnowledgeSearchMaterialFeatureInstrumentedTest` | **1 test / 0 fail / 7.255 s**（首次因夹具特征写错失败，已修正后重跑通过） | `build/agent-batch33/l2-material/gates/instrumented-l2b.log` |
| `FullMigrationMatrixInstrumentedTest` | **13/15 通过 / 0 失败** 后**手动停止**（第 14 个 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback` 的 1→72 循环在模拟器上跑 >40 min 未完成） | `gates/migration-matrix.log` |

- 真库用例覆盖：`rebuildSubject` 落材料行、`INDEX_VERSION=2` 锚点、摘要词经材料腿召回、无材料节点 own 分不变、`max(own, best material)` 不是求和。
- **71→72 真迁移：`NOT_RUN`**（矩阵未跑到版本 72）。补偿：JVM 测试把迁移 CREATE TABLE/两条索引与 `72.json` 的 `createSql`/`indices` 做字符串同义比对并通过；这不替代真机 `Migration didn't properly handle` 验证。
- **DB 计数（设备真库）：`NOT_RUN`**；上面的 209,236/3,232/1,774 是随包静态复算，不是 DB 读数。

### 7.6 明确未跑（`NOT_RUN` / `UNVERIFIED`）

- 设备判读：v26/L2 两臂融合与词面 Recall@5/MRR、24 章 lower/equal/higher、p50/p95/overBudget — `NOT_RUN`。
- 零漂移 device↔JVM symdiff、bootstrap 20k/seed0、A−B 区间 — `NOT_RUN`（且镜像未改，按 §2.1 本批不可判读）。
- 首启 3 次冷装 `install finished in` 与 ≤60 s 门 — `UNVERIFIED`。
- `等和线` 的配额归属复核（是否仍被首条材料占满）— `NOT_RUN`；按冻结行序的静态结论仍见 §1.7-2。
- 退役材料/节点后材料行消失的真库用例 — **未单独覆盖**（代码路径已改；矩阵/召回用例未覆盖退役分支）。
- `INDEX_VERSION=2` 与 2026-09-22 去截断实验锚点 2 的**版本号碰撞**：若某设备保留过实验锚点 2，本版不会触发换血。按 §1.3 冻结值 2 执行，未改；登记为残余风险。

### 7.7 写后逐文件 sha

见 `build/agent-batch32b/l2-post-sha256.txt`（17 件）；触碰清单见 `build/agent-batch32b/l2-touched-files.txt`。
