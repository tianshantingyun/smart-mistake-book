# KB B33 查询类型路由 → 交付层材料类型加权：预注册（2026-11-02）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-03T02:33:16+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日，不是落盘日。
> **HEAD / origin/main**：`62522930bc8bae51be1523c954f39449e054b6af`（`git rev-parse HEAD`、`git rev-parse origin/main` 实跑，同值）。
> **上游**：`docs/research/2026-11-01-kb-external-research-wave.md:12`（下称 **W**，L3「查询类型路由（概念/方法/计算）→ 材料类型加权」）；`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:89-97`（下称 **AC**，F1/F2/F3 定义与冻结读数）；`docs/research/2026-10-26-kb-usage-r1-prereg.md:193-206`（F1–F3 口径）；`docs/research/2026-11-02-kb-l2-material-features-prereg.md`（下称 **L2**，24 章逐章基线表）；`docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md`（下称 **L1**，仅借用其"基线同轮重测 / 零漂移"纪律，不继承其公式）；交付层代码见 §0.2。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/JVM/判官、未改测量脚本。§0.3 的静态复算是本会话用 `python -I` 实跑（非仓内产物）。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                 # 2026-11-03T02:33:16+0800
git rev-parse HEAD                           # 62522930bc8bae51be1523c954f39449e054b6af
git rev-parse origin/main                    # 62522930bc8bae51be1523c954f39449e054b6af  → 同值 ✓
git status --porcelain | wc -l               # 585
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   468 ??
#   117  M
git diff --cached --name-only | wc -l        # 0（暂存区空）
ls docs/research/2026-11-02-kb-b33-query-routing-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 585 项脏项属他线与已写面（含本批上一份 BM25 预注册）；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。
- 触碰面上的他线 `M`（不得 stage、不得回滚）：`ProductionLexicalLegExportTest.kt`、`GoldenRetrievalJvmTest.kt`、`StudyDatabasePort.kt`、`Stage1LexicalLabTest.kt` 等，**均不在本臂写面**。

### 0.2 「交付层」在哪（代码事实，本会话实读）

本臂的「交付层」= **节点已定、材料未选**的那一层，不是召回层、不是融合层：

| 事实 | 出处 |
|---|---|
| 工具 `KNOWLEDGE_READ` 先取 512 深度候选 → 截前 5 个节点 → 每节点取 1 条材料 → 渲染 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:421-490`（`take(KNOWLEDGE_READ_NODE_LIMIT)`、`nodeMaterialRecords`） |
| 展示条数 = **5**（`KNOWLEDGE_READ_NODE_LIMIT`），正文节选 120 字 | `RoomTutorToolRunner.kt:517-528` |
| 每节点最佳材料：`role` → `material_type` → `title` → `material_id`，相关子查询 `LIMIT 1`（minSdk 23 无窗口函数） | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt:70-126`（CASE 在 `:106-115`） |
| 现役教学序（identity）：`CONCEPT_EXPLANATION 0 → METHOD_MODEL 1 → DERIVATION 2 → WORKED_EXAMPLE 3 → REPRESENTATION_GUIDE 4 → MISCONCEPTION_GUIDE 5 → COMPLETE_SOLUTION 6 → ELSE 7` | 同上 `:106-115` |
| 端口方法 | `KnowledgeReadPort.readBestKnowledgeTeachingMaterialPerNode`（`port/KnowledgePort.kt:143-150`，接口在 `:27`）；`RoomStudyDatabase.kt:688-694` 委托 `knowledgeBase` |
| 工具上下文 `Context` **当前没有**学生原话字段 | `RoomTutorToolRunner.kt:235-260`；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt:17-49` |
| 学生原话在输入里：`TutorRespondInput.studentMessage`；Plan 侧是 `TutorPlanInput.questionDocument`（`title` + `blocks`，段落 `Paragraph.markdown`） | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorTasks.kt:418-431,218-234`；`StructuredContent.kt:12-16,22-27` |
| 另一套顺序（**不得混用**）：重教优先级 `reTeachPriority`（MISCONCEPTION 0 … COMPLETE_SOLUTION 6） | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:52-82`；DAO KDoc 明写「两套顺序各有消费方，不得互相写回」（`KnowledgeTeachingMaterialDao.kt:70-79`） |

**一句话**：本臂只改 `readBestTeachingMaterialPerNode` 选中的**那一条材料**；节点集合、节点顺序、top-5、512 深度、召回分数、融合分数一个都不动。

### 0.3 本会话实算（离线，非设备读数）

**材料与绑定**（遍历 25 个 sidecar 的 `materials[].bindings[]`，`python -I` 实跑）：

| 项 | 值 |
|---|---:|
| 绑定行 | **65,021**（全部 `role=PRIMARY`） |
| 类型分布 | `CONCEPT_EXPLANATION` **35,579** / `METHOD_MODEL` **25,492** / `MISCONCEPTION_GUIDE` **3,950** |
| 其它类型 | **0**（`DERIVATION` / `WORKED_EXAMPLE` / `REPRESENTATION_GUIDE` / `COMPLETE_SOLUTION` 在当前包内不存在） |
| 节点 | **3,280**；类型数 >1 的节点 **2,231**（68.0%） |
| `material_id` 构法 | `"kb-material:" + sha256("$packId:$subject:teaching:$slug").take(40)`（`ReviewedTeachingMaterialSidecarJsonCodec.kt:167`） |

**129 条金标的分类分布**（用 §1.2 冻结词表；写表后统计，未按分布回调词表）：

| 类 | 条数 | 备注 |
|---|---:|---|
| `CONCEPT` | **67** | |
| `METHOD` | **7** | |
| `COMPUTATION` | **12** | |
| `UNCLASSIFIED` | **43** | 其中 **37** 条零命中、**6** 条并列（tie） |

**静态可达性**（只看每条金标的 `expectedSlug` 节点；不是 top-5 实读，只是结构上限）：

| 类 | 期望节点可分类 | 期望节点选材**会变** | 变化 |
|---|---:|---:|---|
| `CONCEPT` | 67 | **0** | identity 本就先取 `CONCEPT_EXPLANATION`，本类在现包上**恒等** |
| `METHOD` | 7 | **4** | `CONCEPT_EXPLANATION → METHOD_MODEL` |
| `COMPUTATION` | 12 | **11** | `CONCEPT_EXPLANATION → METHOD_MODEL` |
| `UNCLASSIFIED` | 43 | **0**（冻结为恒等） | — |

**零变化基数**：把 65,021 条绑定按 schema 73 的索引列建进 SQLite，**逐字跑** `KnowledgeTeachingMaterialDao.kt:81-122` 的相关子查询 SQL，再与 §2.3 的 identity 比较器逐节点对拍：**3,280/3,280 相同，diff=0**（本会话实跑；不是"同义重写"的自我比较）。

**读法（必须写进判读）**：本臂在现包上的可观测改动面 = **15/129 条**（全部是 `CONCEPT_EXPLANATION → METHOD_MODEL`）；`CONCEPT` 类 67 条与 `UNCLASSIFIED` 43 条行为恒等。这是一条**低风险小臂**，预期对 F1/F2/F3 **没有可测变化**（这三项是覆盖/占比指标，不是相关性指标）——判据只用来证明**没有副作用**，不承诺提升。

### 0.4 与 W L3 的关系（外部依据只到这里）

- W:12 的判断是：中文知识点分类**已验**（atmk；ACL 2002 中文线索词），但「分类 → 材料类型加权」的**收益无外部证据**；因此采纳为**低风险小臂**，要求「非命中查询零变化；线索表与常数预注册」。
- 本文件照做：词表逐词冻结（§1.2）、加权常数冻结（§2.2）、非命中恒等（§2.5）与四条判据（§3）。
- **不主张**：本臂有外部效果证据。W 只支持「可分类」，不支持「这样加权更好」；§3 的判据是**非劣**，不是优效。

---

## 1. 线索表（冻结，逐词列出）

### 1.1 分类器输入与匹配规则（写死）

| 项 | 冻结值 |
|---|---|
| 输入 | **学生原话**，不是模型的检索词。Respond：`TutorRespondInput.studentMessage`；Plan：`questionDocument.title` + 全部 `Paragraph.markdown`（按块序，`\n` 连接；`Formula` 不参与）。该文本经 `RoomTutorToolRunner.Context.studentQueryText` 传入交付层（§5.1 #1/#2） |
| 空值 | `null` / 空白 → **`UNCLASSIFIED`** |
| 归一化 | 仅 `lowercase(Locale.ROOT)`；**不做**全半角转换、不做标点剥离、不做分词、不做同义扩展 |
| 匹配 | 对每个词条做 **子串包含**（`text.contains(word)`），因为中文无空格；同一词条在一次查询里只计 1 次 |
| 计分 | 每类得分 = 该类**命中词条的个数**（distinct 词条数，不是出现次数） |
| 词条长度 | 全部 **≥ 2 字**（不设单字线索，避免"求/和/比"这类噪声） |
| 判定 | **严格最大**获胜；**0 命中**或**最高分并列**（≥2 类同分）→ `UNCLASSIFIED` |

### 1.2 三类线索表（逐词列出；**冻结后不得增删改**）

**CONCEPT（概念，20 词）**

`为什么`、`是什么`、`什么区别`、`区别`、`概念`、`含义`、`定义`、`原理`、`原因`、`作用`、`属于`、`称为`、`叫作`、`本质`、`判断`、`是否`、`能不能`、`对不对`、`一回事`、`关系`

**METHOD（方法，22 词）**

`方法`、`步骤`、`怎么写`、`怎么求`、`怎么判断`、`怎么算`、`如何`、`怎样`、`怎么做`、`几种`、`哪几种`、`画法`、`写法`、`证明`、`构造`、`换元`、`配方`、`待定系数`、`归纳`、`分类讨论`、`数形结合`、`解法`

**COMPUTATION（计算，18 词）**

`计算`、`求值`、`多少`、`质量比`、`物质的量`、`浓度`、`体积`、`面积`、`长度`、`角度`、`数值`、`结果`、`等于`、`算出`、`取值`、`最值`、`概率`、`比例`

**词表来源与泄漏登记（必须如实读）**：词表按通用中文教学问句用语编写；写表**前**本会话预览过金标前 30 条题面（用于判断语料风格），写表**后**只统计分布，**未按分布增删任何一个词**。这仍属轻度「测试集进开发」；冻结后不得再改词表（§6 登记残余风险）。

### 1.3 判定示例（用 §0.3 的真实分布校准，不是挑选）

- 「为什么携带线粒体致病基因却不一定患病？其中原因是什么？」→ `CONCEPT`（命中 `为什么`、`原因`、`是什么` = 3）。
- 「从海水中提取镁要经过哪四个步骤？」→ `METHOD`（命中 `步骤` = 1）。
- 「某铁的氧化物中铁元素与氧元素的质量比为21:8，求它的化学式」→ `COMPUTATION`（命中 `质量比` = 1）。
- 6 条并列的查询 → `UNCLASSIFIED`，走原路径。

### 1.4 分类器输出契约

```kotlin
enum class KnowledgeQueryType { CONCEPT, METHOD, COMPUTATION }

// 返回 null = UNCLASSIFIED（零命中 / 并列 / 空文本）
fun classify(studentQueryText: String?): KnowledgeQueryType?
```

- 纯函数、无 IO、无随机、无 Locale 依赖（除 `Locale.ROOT` 的 lowercase）；同输入必同输出。
- 不得读取科目、节点、材料类型等**结果侧**信息——分类只看学生原话（否则就是拿答案反推问题）。

### 1.5 每一项新增消灭的具体失败（逐个指认）

| 新增 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| 三类线索表 + 严格最大/并列→UNCLASSIFIED | 没有分类时，所有查询共用同一条教学序；"怎么算"与"是什么"拿到同一种材料，且无法在预注册里把行为说清 | W:12；§0.3 的 67/7/12/43 |
| 「并列/零命中 → UNCLASSIFIED」 | 模糊查询被硬分到某一类，交付材料悄悄改变且无法解释 | §2.5 的恒等断言 |
| 分类输入 = 学生原话（而非模型检索词） | 模型的 `terms` 是检索词（如「浮力 判断」），几乎不含疑问线索；用它会得到一个生产上永远不命中的分类器 | §0.2 的 `terms.joinToString(" ")` 现形状；§0.3 的词表命中全部来自疑问句式 |
| 只看学生原话、不看结果侧 | 用节点/材料类型反推查询类型 = 循环论证，分类器在任何查询上都会"命中" | §1.4 |

---

## 2. 类型 → 交付层材料类型加权（冻结）

### 2.1 加权边界（写死）

- **只**在「节点已定、材料未选」这一层生效：`RoomTutorToolRunner.knowledgeRead` 取到 5 个节点后，对**每个节点**选 1 条材料时使用类型秩。
- **不**改节点集合、**不**改节点顺序、**不**改 top-5 数量、**不**改 512 深度、**不**改召回 SQL、**不**改融合（`DenseFusion` 零改）。
- 角色（`PRIMARY` / `SUPPORTING` / 其它）仍是**第一键**，类型秩只在同角色内重排；`title`、`material_id` 仍是第三、第四键。这与现行 SQL 的键序逐条相同，只换第二键的取值。
- **不得**把本表写回 `reTeachPriority`（`RoomTutorTeachingReferenceRepository.kt:52-82`）：那是重教通道的顺序，两套顺序各有消费方（DAO KDoc 明写，`KnowledgeTeachingMaterialDao.kt:70-79`）。

### 2.2 四张类型秩表（冻结；数字越小越优先）

| `material_type` | UNCLASSIFIED（identity，现行） | CONCEPT | METHOD | COMPUTATION |
|---|---:|---:|---:|---:|
| `CONCEPT_EXPLANATION` | 0 | 0 | 3 | 4 |
| `METHOD_MODEL` | 1 | 2 | 0 | 1 |
| `DERIVATION` | 2 | 1 | 2 | 2 |
| `WORKED_EXAMPLE` | 3 | 3 | 1 | 0 |
| `REPRESENTATION_GUIDE` | 4 | 4 | 4 | 5 |
| `MISCONCEPTION_GUIDE` | 5 | 5 | 5 | 6 |
| `COMPLETE_SOLUTION` | 6 | 6 | 6 | 3 |
| 未知类型（`ELSE`） | 7 | 7 | 7 | 7 |

- 四行都是 `0..6` 的**排列**（每类恰好一次），未知类型恒 7；`UNCLASSIFIED` 行与现行 SQL 的 CASE **逐值相同**。
- **现包后果**（只有 3 种类型）：`CONCEPT` 行与 identity 行为**恒等**（`CONCEPT_EXPLANATION` 仍第一）；`METHOD` / `COMPUTATION` 行只把 `METHOD_MODEL` 提到 `CONCEPT_EXPLANATION` 之前；四行里 `MISCONCEPTION_GUIDE` 都在第 5 或第 6，**没有任何类会把误区材料提到首位**（F3 的结构性理由，§3.3）。
- 为什么这样排（依据是**教学形态**，不是效果证据）：
  - `CONCEPT`：概念解释第一；推导（解释"为什么"）第二；方法模型第三。
  - `METHOD`：方法模型第一；例题第二（步骤可照做）；推导第三；概念解释第四。
  - `COMPUTATION`：例题第一（算给你看）；方法模型第二；推导第三；完整解答第四（在方法之后）；概念解释第五。
  - 这些是**设计选择**，W 不提供"哪一类排第一更好"的证据；冻结后不得按结果回调。

### 2.3 比较器与 SQL 形状（冻结）

- 比较键（与现行逐条同序，只换第二键）：

```text
roleRank(role) ASC, typeRank(material_type) ASC, title ASC, material_id ASC
roleRank: PRIMARY=0, SUPPORTING=1, 其它=2
typeRank: §2.2 的 4 张表；未知类型=7
```

- **旧路径原样保留**：`KnowledgeTeachingMaterialDao.readBestTeachingMaterialPerNode` 一字不改，`UNCLASSIFIED` 直接调用它（同一方法、同一 SQL、同一参数）——这是「非命中零变化」的**构造性**保证（§2.5）。
- **新路径**：新增 `readBestTeachingMaterialPerNodeRanked(subject, knowledgeNodeIds, r0..r6)`，SQL 与旧方法逐字相同，只把 CASE 的 7 个字面量换成 7 个绑定参数（`:rConcept` … `:rComplete`）。分类结果非 `UNCLASSIFIED` 时调用它。
- 端口新增同名方法（`KnowledgeReadPort` → `RoomStudyDatabase` 委托 → `RoomKnowledgeBaseStore`）；`RoomStudyDatabase` 是唯一实现，测试里的 `StudyDatabasePort by delegate` 假件不需要改（Kotlin 委托自动覆盖新方法）。
- SQL 只读 `knowledge_teaching_material` / `..._node_binding`；不改表、不改索引、不写库。

### 2.4 不改召回/融合（零触碰 + 违例判据）

**零触碰（一条都不许动）**：

1. `ProblemOrganizationDao.searchSubjectKnowledgeRecallCandidates`（召回 SQL）与 512 深度；
2. `DenseFusion.kt`（α=0.5 / min-max / 缺腿 0 / 并列 node_id）、`DenseRecallReranker`、`DenseRecallAssembly`；
3. `KnowledgeSearchFeatureExtractor`（查询/节点特征与 `INDEX_VERSION=1`）；
4. `KNOWLEDGE_READ_NODE_LIMIT = 5`、`KNOWLEDGE_READ_MATERIAL_EXCERPT_CHARS = 120`、2k 工具结果预算；
5. 内容包 / manifest / dense 资产 / 金标。

**违例判据（`SCOPE_VIOLATION`）**：同轮 A/B 两臂，对**每一条**金标，`KNOWLEDGE_READ` 交付的 **top-5 节点 id 序列必须逐位相同**。任一题不同 ⇒ 本臂越界，规则 1/2/3 记 `NOT_RUN`，不得把"召回变了"记成收益或回退。

### 2.5 非命中查询零变化（可断言）

三层断言，缺一不可：

1. **分类型**：`classify(text)` 对 **6 条 tie + 37 条零命中**返回 `null`。这两组按金标文件 0 基下标**冻结在测试里**（本会话实算）：
   - tie（6）：`[86, 106, 112, 113, 121, 123]`
   - 零命中（37）：`[0, 2, 3, 6, 9, 15, 23, 26, 35, 37, 44, 45, 46, 47, 51, 52, 58, 59, 60, 61, 64, 66, 70, 71, 76, 80, 82, 83, 93, 99, 105, 107, 109, 114, 116, 119, 124]`
   - 另断言三类的非空集合恰为其余 86 条（67 + 7 + 12），即分类总数与 §0.3 的分布逐位一致（`KnowledgeQueryTypeClassifierTest`）。
2. **端口调用**：`UNCLASSIFIED` 走的必须是**旧方法** `readBestKnowledgeTeachingMaterialPerNode`，且**不得**调用 `...Ranked`；分类命中时相反（`TutorKnowledgeReadQueryRoutingTest` 用记录调用的 fake `StudyDatabasePort`）。
3. **输出逐字**：`UNCLASSIFIED` 的渲染结果与臂前逐字相同（同一 fixture 的 golden 字符串；`TutorKnowledgeReadQueryRoutingTest`，含无材料标记与预算收缩两种分支）。

数据级旁证（本会话已实跑，§0.3）：对 65,021 条真实绑定的 SQLite 逐字 SQL，identity 秩在 **3,280/3,280** 个节点上复现现行选择；执行轮再用真库断言一次 `旧方法 == Ranked(identity)`（`TeachingMaterialRankedAllocationInstrumentedTest`）。

---

## 3. 判读（写死）

### 3.1 主集不劣（设备真 SQL，129 题 × 5 样本）

| # | 规则 | 判据 |
|---:|---|---|
| 1 | **主集不劣** | 设备 `GoldenRetrievalInstrumentedTest`（真 Room/SQLite）融合 Recall@5，**≥ 同代 B 臂**（`equal` 通过）；129 题 × **5 样本**（`SAMPLES_PER_QUERY = 5`，`GoldenRetrievalInstrumentedTest.kt:625`）。绝对下限 **117/129 = 0.9069767441860465**（v26 冻结读数）；同轮复测 B ≠117 时用复测 B，117 只作下限（L1:228 同款纪律）。 |
| 2 | **24 章不劣** | 24 章逐章融合 Recall@5 与同代 B 比较；任一章**严格更低**即不采纳；`equal` / `improved` 通过。章名/边界沿用 L2 §2.3 的 24 行表，不得换章名/合并章。 |
| 3 | **零漂移 = 0** | 同路 device↔JVM 的 **fused 与 lexical 两条** MISS query 集合 symmetric difference 必须 **0**。非 0 = 装置缺陷，本臂**不判读**（L1:223）。 |
| 4 | **F1 / F2 / F3 不变差** | 见 §3.3；**精确值**判定，不是"大致接近"。 |

- 因本臂不触碰召回，规则 1/2 的**预期关系是逐题相等**，不是"可能更高"；§2.4 的序列不变量同时生效。若 recall 严格变高，不记作收益，先按 `SCOPE_VIOLATION` 查为什么节点序变了。
- **不跑配对 bootstrap**：节点序列不变时 bootstrap 无信息；若序列变了，本臂已越界，不做显著性。**不得**用显著性替代规则 1/2。

### 3.2 基线口径

- **B = 同代 v26 设备四路账本**，同轮同 emulator/API、同金标（`eabef102…`）、同主包 `96246c78…`、同 dense `.vec` `70329115…`；冻结参考融合 **117/129**、MRR **0.7239018087855298**、词面 101/129（`build/agent-batch31a/v26-device/device-metrics.txt:3-5,33-35`）。
- 119/129（v27fix 离线臂）**不得当 B**：`docs/research/2026-11-02-kb-r3-dedupe-summary-quality.md:307`（U1）已登记其身份不可从当前树重建；当前 manifest 仍是 `version=26`。
- `samegen`：emulator 序列/API、金标 sha、主包 contentVersion、dense `.vec` sha、融合常数、候选深度一致；任一不成立 ⇒ 规则 1/2/3 记 `NOT_RUN`。
- 24 章基线：v26 合计 117；低于满分的 **7** 章 = #2 生物学必修2·第一章 8/9、#5 化学必修第一册·第三章 10/11、#7 化学必修第二册·第七章 1/2、#12 数学必修第一册·第三章 7/11、#14 数学必修第二册·第六章 2/3、#17 数学选择性必修第二册·第四章 10/11、#20 物理必修第一册·第三章 9/12；其余 17 章满分（L2 §2.3 的 24 行表；v26 `device-metrics.txt:6-30`）。

### 3.3 F1 / F2 / F3（AC F.1 定义，**不变差**）

| # | 定义（AC:89-91） | 冻结基线 | 本臂判据 |
|---|---|---:|---|
| F1 | 每节点材料覆盖：分母 = 129×5 中**有绑定材料**的节点行；分子 = 其中获得材料（摘要）的节点行 | **100.0%（645/645）** | **必须 = 645/645**；任何下降即不采纳 |
| F2 | 期望节点有材料：分母 = 129；分子 = `expectedSlug` 在 top-5 内**且**获得材料的查询数 | **90.7%（117/129）**（= 召回上限） | **必须 = 117/129**；任何下降即不采纳 |
| F3 | 误区槽位占比：分母 = 645 个被选槽；分子 = `MISCONCEPTION_GUIDE` 槽 | **1.24%（8/645）** | **必须 ≤ 8/645**，且仍 ≤20%；预期 **= 8**（下文理由） |

- **为什么 F1/F2 预期精确不变**：节点集与顺序不变（§2.4），每个有绑定材料的节点仍恰好取到 1 条材料（§2.3 的 `LIMIT 1` 语义不变），分子分母都不动。
- **为什么 F3 预期精确不变**：四张秩表里 `MISCONCEPTION_GUIDE` 都排在第 5 或第 6（§2.2），现包只有三种类型；一个节点若还有任何非误区类型，任一秩表都不会把误区排到首位；若节点**只有**误区材料，identity 与三类都只能选它。因此槽位分布**不可能增加**；现包无 `COMPLETE_SOLUTION`，也不可能因排序把误区挤出而减少。**= 8/645** 是唯一预期。
- **这三项不是相关性指标**：F1 是覆盖、F2 是上限触达、F3 是占比帽。本臂的收益**不**体现在它们上；它们只证明"没有副作用"（与 W:12 的"收益无外部证据"一致，不夸大）。
- 阈值/分母/样本一律**不事后改**（`2026-10-26-kb-usage-r1-prereg.md:206` 同款纪律）；不得因贴近阈值放宽或用别的 top-5 重算。

### 3.4 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |
| 2 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 门 1 覆盖分类器、秩表、Context 字段、工具执行器与 fake 端口测试；门 2 覆盖 DAO 新方法、端口/委托/Store。
- 任一门红 ⇒ 不写 release、不跑设备、不判读；不得先跑设备再补门。日志落 `build/agent-batch33/b33-routing/gates/`（建议）。

### 3.5 报告义务（§7 至少包含）

- 两臂：融合/词面 Recall@5、MRR、p50/p95/max/overBudget；24 章逐章 lower/equal/higher。
- **序列不变量**：129 条逐题的 top-5 节点 id 序列对拍结果（相同数 / 不同数；不同即列题号）。
- 零漂移两路 symdiff；设备判官 / JVM 判官 / 金标 / JVM 镜像四个 sha。
- **F1/F2/F3 两列**（基线 / 本臂）+ 逐类分解（CONCEPT/METHOD/COMPUTATION/UNCLASSIFIED 的槽数与类型直方图）+ 分类分布（必须复现 67/7/12/43）。
- `retest_usage_r1.py` 的两次输出（旧 `TEACH_RANK` 校验 / 新秩表），以及其外部 audit mirror 的 sha256。
- 双门 exit code；§5 逐文件写后 sha。

---

## 4. 测量装置（冻结）

### 4.1 `retest_usage_r1.py` 的改动契约

| 项 | 改成什么 | 不得改什么 |
|---|---|---|
| `TEACH_RANK` | 保留 identity 表作 `RANK_IDENTITY`；新增 §2.2 的三张类表 | identity 的 7 个值 |
| `best_for_node(nid)` | `best_for_node(nid, query_type)`；`query_type` 来自对**金标 `case["query"]`** 调 `classify`（与生产同一规则） | role/title/material_id 键序 |
| `new_render(nodes5)` | 每条查询按 `classify(case["query"])` 选秩表 | 渲染形态、5 条/查询、2,000 字符预算 |
| SQL 交叉核对 | CASE 的 7 个字面量换成 7 个绑定参数；按同一 `query_type` 传秩；**全部 129 条**都要 0 mismatch | 相关子查询结构、`LIMIT 1`、`ORDER BY` 键序 |
| 输出 | 同时打印「基线（identity）」与「本臂（routed）」两列；打印分类分布 | 不挑样本、不合并列 |

### 4.2 不可复现依赖（登记）

`retest_usage_r1.py` 依赖仓库外的 `audit_knowledge_read.py`（`AUDIT` 常量，`retest_usage_r1.py:28`），其头注释记录 sha256 `80b053853b997a56c3eb6db3c892ccadc0d5e383db727c2fc52b0f796744337e`（本会话未复核该文件是否存在/同值）。执行轮必须把**实际使用的**该文件 sha256 写进 §7；若缺失或不等，F1–F3 记 `UNVERIFIED`，不得用旧读数冒充。

### 4.3 为什么不用生产 SQL 直接测 F1–F3

生产 SQL 对每条查询只取 5 个节点、且秩表由 Kotlin 选择；`retest_usage_r1.py` 的作用是**独立复算**（Python 镜像 + SQLite 逐字 SQL）。两者必须对拍 0 mismatch；若对拍不上，先停，不得用任一方单独出数。

---

## 5. 回滚（触碰清单 + pre/post sha）

### 5.1 A. 生产源码（tracked；pre-sha 为本轮工作树实算）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt` | 构造 `Context` 时填 `studentQueryText`：Respond 取 `studentMessage`，Plan 取 `title` + `Paragraph.markdown` | `772a77c56983af29d201439ff6d850de73757dffb4d93f1dd8053d1195b3386f` | §7 |
| 2 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | `Context` 字段；`knowledgeRead` 分类 → 旧/新端口方法二选一 | `2de1e68f71bc0615ce073fa797086dc63178744cd9e7e153c275e26400b37fd4` | §7 |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 新增 `readBestKnowledgeTeachingMaterialPerNodeRanked` | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | §7 |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 实现 ranked 方法（7 个秩参数） | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` | §7 |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托新方法 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | §7 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 新增 `readBestTeachingMaterialPerNodeRanked`（CASE 7 参数）；**旧方法一字不改** | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` | §7 |

### 5.2 B. 测试 / 测量装置

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `tools/kb_coverage/retest_usage_r1.py` | §4.1 的秩表/分类/SQL 参数化；两列输出 | `b9f7375dd6e1dc38eed0392ea5994198afca5b008605c3db2202d388357f5b03` | §7 |
| 2 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/TeachingMaterialAllocationInstrumentedTest.kt` | 仅在"旧方法未被改动"断言处补一条；**选择语义零改** | `13a1574c24b60c1e1f821c667e925b806e6cab355f18340e0b925904e34745f5` | §7 |

### 5.3 C. 新增文件（写前均不存在）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeQueryType.kt` | 枚举 + §1.2 词表 + `classify` |
| 2 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeMaterialTypeRanking.kt` | §2.2 四张表 + 比较器 |
| 3 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeQueryTypeClassifierTest.kt` | 词表/严格最大/tie/no-hit/空文本；冻结 6 条 tie 与 37 条 no-hit |
| 4 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeMaterialTypeRankingTest.kt` | 四张表是排列；未知类型 7；键序 |
| 5 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/TutorKnowledgeReadQueryRoutingTest.kt` | fake 端口：UNCLASSIFIED 走旧方法且输出逐字；命中走 ranked |
| 6 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TeachingMaterialRankedAllocationInstrumentedTest.kt` | 真库：旧方法 == Ranked(identity)（全节点）；三类秩表的选材 |

### 5.4 D. 零触碰（登记供核验，不得改）

| # | 路径 | 期望 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/database/.../dao/ProblemOrganizationDao.kt` | 零改（召回 SQL） | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 2 | `core/data/.../knowledge/dense/DenseFusion.kt` | 零改 | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 3 | `core/database/.../DenseRecallReranker.kt` | 零改 | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 4 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | 零改（`INDEX_VERSION=1`） | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 5 | `core/database/.../StudyDatabase.kt` | 零改（schema 73） | `b82369a17641c1eb71a005bea51f12f01b6f66bcf6c1cac241ac0b54a4bec308` |
| 6 | `core/data/.../knowledge/RoomTutorTeachingReferenceRepository.kt` | 零改（`reTeachPriority` 是另一套顺序） | `7bfa96df9e93a354b3fac2db2ef710dbe69bd9cc8898ea2ee76d914a854ad992` |
| 7 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改（129 / 24 章） | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 8 | `core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt` | 零改（设备判官） | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` |
| 9 | `core/data/src/test/.../GoldenRetrievalJvmTest.kt` | 零改（JVM 判官；他线 `M`） | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` |
| 10 | `core/data/src/test/.../RetrievalBenchmark.kt` | 零改（JVM 镜像；本臂不改检索） | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |

### 5.5 写前备份 / 回滚 / 采纳

**写前备份（执行轮第 1 步之前）**

1. 把 §5.1 的 6 件、§5.2 的 2 件复制到 `build/agent-batch33/b33-routing/backup/`，落 `sha256sum` 全表。
2. `git status --porcelain -- <§5.1/§5.2 路径>` 必须为空（本臂写前）；若非空，先归因他线并停止，不得覆盖。
3. 复核身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；主包 `96246c78…`；dense `70329115…`；金标 `eabef102…`；DB 73；`INDEX_VERSION=1`。

**回滚（任一规则失败、双门红、零漂移非 0、SCOPE_VIOLATION）**

1. **不得**整树 `git checkout`（585 项脏）；按 §5.1/§5.2/§5.3 逐文件恢复/删除。
2. 用 `backup/` 覆盖 §5.1 的 6 件与 §5.2 的 2 件；删除 §5.3 的 6 件；删除运行产物（保留备份与记录）。
3. **无需清库、无需迁移回退**：本臂零 schema；重装 APK 后核 `version=26`、DB 73、`INDEX_VERSION=1` 即可，**不要** `pm clear`。
4. 核验：`UNCLASSIFIED` 的渲染与臂前逐字相同；`readBestTeachingMaterialPerNode` 的 SQL 与 §5.1 #6 的 pre-sha 一致；设备判官 / JVM 判官 / 金标三个 sha 回到冻结值。
5. 复跑 §3.4 双门；回滚后仍红则登记为他线/基线问题，不归因本臂。

**采纳**：保留写面；F1/F2/F3 三项精确值达标；在 §7 登记两列读数、分类分布、序列对拍、双门 exit code、逐文件 post-sha。**不**改 AC 的 F1–F5 判据文本（`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md` 零触碰）。

---

## 6. 未做 / 未验证（写作时点）

- **未写任何代码/测试**：§1–§2 的分类器、秩表、SQL、端口、Context 字段均为冻结定义。
- **未跑 Gradle/设备/JVM/判官**：§3 是判据与门定义，不是通过声明。
- **F1/F2/F3 未重测**：645/645、117/129、8/645 是 AC 的冻结读数（`build/agent-batch23/usage-r1/retest-r1.log`），不是本臂读数；本臂的 F1–F3 仍为 `NOT_RUN`。
- **静态可达性不是效果**：15/129 是"期望节点上的选择会变"的结构计算；真实 top-5 槽位里会变多少条**未测**。
- **外部 audit mirror 未复核**：`retest_usage_r1.py:28` 指向仓外文件；本会话未验证其存在与 sha（§4.2）。
- **线索表有轻度测试集泄漏**：写表前预览过金标前 30 条；未按分布回调词表（§1.2）。
- **生产输入面未验证**：`studentQueryText` 由 Respond 的 `studentMessage` / Plan 的题面构造；真实模型工具环里 `KNOWLEDGE_READ` 的调用轮是否都带得到该文本，未在设备上验证。
- **F3 的精确不变是结构论证**：依赖"现包只有 3 种类型、误区恒在 5/6 位"；若执行轮内容包新增 `COMPLETE_SOLUTION` 等类型，先停并重判（§3.3）。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§5 逐文件写后 sha；双门 exit code；两臂融合/词面 Recall@5/MRR/p50/p95/max/overBudget 与 24 章 lower/equal/higher；**129 题 top-5 序列对拍**（相同/不同数与题号）；零漂移两路 symdiff；F1/F2/F3 基线与本臂两列 + 逐类分解 + 分类分布（须复现 67/7/12/43）；`retest_usage_r1.py` 两次输出与外部 audit mirror sha256；`TeachingMaterialAllocationInstrumentedTest` 的补充断言结果。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
