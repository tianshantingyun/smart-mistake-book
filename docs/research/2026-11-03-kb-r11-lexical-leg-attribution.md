# 词面腿归因（R11）：BM25 两变体 115/129 的失效模式、192 特征截断顺序与下一步

> **回答的问题**：在「词面候选 + dense 节点分、α=0.5、域内 min-max」的融合下，为什么 BM25（Lucene 变体 IDF 与 FTS5 等价 IDF）都只到 115/129，低于基线 117/129；这是词面腿的机制性失效、特征截断顺序问题，还是实现/装置问题；下一步该测哪一条。
> **约束**：本文件只做调研与归因，不改代码、不改金标、不改融合常数。所有仓内读数以本文件 §1 标注的现有产物为准；未复跑的一律标 `NOT_RERUN`。
> **证据等级**：`[官方]` = SQLite/Lucene/Elastic 官方文档；`[评测]` = 有检索评测方法与读数；`[工程]` = 工程实践/实现存在性，不等于效果证据；`[实测]` = 本会话现场计算或实读仓内文件；`[待验证]` = 单一来源、只读到摘要或未取到原文。

---

## 结论先行

1. **115 vs 117 不是「BM25 更差」的证据，只是「没有证明更好」**。2/129 的差异在 129 题上不可分辨：仓内 bootstrap 的融合差值 95% CI **下界就是 0**（`delta_recall_b_minus_a=0.0155`，`ci95_chapter=[0.0, 0.0397]`）；若两臂只在 2 题上不同且方向一致，精确 McNemar 双侧 p=0.50。把它读成「负向结论」会过度解释（§1 F12、§2.0）。
2. **本仓的 BM25 不是教科书 BM25**：`tf≡1`（特征表主键去重）、`|D|=该节点全部特征行数`、`avgdl=147.09`、`df` 跨科全局。公式退化为 `C(len)·ΣIDF`，长度因子在 192 行节点上最多压到 0.889、在 50 行节点上是 1.370（约 1.54×）；而 `df>N/2` 的特征 IDF 为负、被钳到 `1e-6`。这两条与「词面 MRR 升 0.004、Recall 降 1」一致，但是否因果要等 `b=0` 消融（§1 F5/F6、§4 第 1 条）。
3. **192 截断不是「长文档占优」，而是「谁被留下」的问题**。抽取器先选 16 个片段（首段 + 末段 + 均衡中段），再在片段间轮转取特征；别名按字典序排序，所以活下来的别名与查询相关性无关。`192/16≈12`，每个入选片段大约只剩「整片段 + 11 个 n-gram」。仓内 62% 节点的片段数超过 16（§1 F2/F11）。
4. **BM25 的 `b` 惩罚长节点，不奖励长节点**。长节点在本仓恰好是别名多的节点；192 截断先丢掉它们的别名，BM25 再用长度归一压一次。两条机制叠加，方向一致地打击「别名丰富」的正确答案节点（§1 L2/L3、F11）。
5. **两次 115 不是独立复现**：Lucene 变体那次（32A/L1）的判官与镜像已换版，仓内台账明确写「115/129 不可比、不可由当前树复现」；只有批次 33 的 FTS5 等价臂是在现行判官下重跑的（§1 F7）。
6. **下一步不应再换公式**：先做「权重/门控」实验（α=1.0/0.75 对比 0.5；注意 `score = α·dense + (1−α)·lexical`，**降词面权重 = 升 α**）与 `b=0` 归因消融；α 扫描必须按章留出、预注册、只报 out-of-fold 决策数（§3.3）。理由：已记录一条 rank 6 的零漂移差异（它不等于 2 条丢失题之一），词面成员集基本没坏；而换公式已经两次没有收益（§1 F5/F8/F9）。

---

## §1 事实

### 1.1 本仓与两次实验的事实（F1–F12）

| # | 事实 | 读数 / 机制 | 出处 |
|---|---|---|---|
| **F1** | 生产词面腿的打分 | `COUNT(DISTINCT feature.search_feature)`，无 IDF、无长度归一；`LIMIT :limit`（上限 512）；最终判分窗口 top-5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt:208-228`；`core/database/.../StudyDatabasePort.kt:44`（`MAX_KNOWLEDGE_RECALL_CANDIDATES=512`） |
| **F2** | 特征抽取的截断顺序 | 节点侧 `texts = [canonicalName] + aliases.sorted() + [boundaryMarkdown]`；片段 >16 时保留 `last + first + balanced middle`；随后 16 条片段流**轮转**取特征，直到 192；每条片段先出整片段（2–48 字），再出全部 2-gram、3-gram | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt:44-56, 71-91, 95-102` |
| **F3** | 特征表没有字段来源 | `knowledge_search_feature(subject, search_feature, knowledge_node_id)`，PK 去重 ⇒ 同一 (节点, 特征) 只有一行，`tf≡1`；**无法区分**该特征来自 canonicalName、别名还是 boundary | `core/database/src/main/kotlin/.../entity/KnowledgeSearchFeatureEntity.kt`；`schemas/.../73.json`（`createSql`） |
| **F4** | 金标与查询截断 | 129 条 / 24 章；整串长度 min 16 / median 37 / max 60。按 `NON_SEARCH_CHARACTER` 切分后：**6/129 的片段数 >16**（16 片段截断生效）；**0/129 有单片段 >48 字**（`MAX_WORD_LENGTH=48` 在本题集不咬）；**0/129 的估计特征数 >128**（中位 56，最大 90；128 查询上限不咬） | 本会话 `python -I` 按 `KnowledgeSearchFeatureExtractor` 的切分/计数规则复算；`KnowledgeSearchFeatureExtractor.kt:7-10, 66-70, 95-102`；`tools/kb_coverage/tables/golden_queries_v2.json` |
| **F5** | 基线 vs BM25（批次 33，现行判官） | 融合：B 117/129（Recall 0.90698，MRR 0.72390）vs A 115/129（0.89147，0.71189）；**词面单腿：B 101/129（MRR 0.61783）vs A 100/129（MRR 0.62183）**；A 的 MRR 更高但 Recall 少 1 | `build/agent-batch33/bm25/final.md` §0；`build/agent-batch33/bm25/device/bm25.json`（`armMetrics`/`baselineMetrics`） |
| **F6** | BM25 在本仓退化为「长度因子 × IDF 和」 | `tf=1` ⇒ 分母首项恒为 1，`bm25 = Σ IDF(f) · 2.2 / (1 + 1.2(0.25 + 0.75·len/avgdl))`。`avgdl=546,872/3,718=147.09`；`len=50` 时 C=1.370，`len=192` 时 C=0.889（1.54×）；`df=1` 的 IDF=7.815，`df=1000` 的 IDF=1.000，`df=3000` 的 IDF=-1.429 → 钳到 `1e-6` | 公式冻结见 `docs/research/2026-11-02-kb-b33-bm25-prereg.md` §1.1-§1.2；N/avgdl/行数见同文件 §0.2；本会话 `python -I` 复算 |
| **F7** | 「两次 115」的可比性 | 32A/L1（Lucene 变体 `+1`）读数 115/129、词面 100/129；但其设备判官 post `ce7ef8e2…`、`RetrievalBenchmark` post `8134553a…`，现行已回滚为 `9fae203f…`/`8c2a2ef0…` ⇒ 仓内台账判「**不可比、不可复现**」。批次 33 的 115 是现行判官下的 FTS5 等价臂 | `docs/kb-outstanding-work-2026-09-20.md` 的 32A/32B 结算条目；`docs/kb-batch32b-2026-11-02.md` §7 第 10 条；`docs/kb-batch33-2026-11-02.md` §2.1 |
| **F8** | 装置零漂移失败 | 词面 `symdiff=1`（onlyInDevice：`二次函数在闭区间上的最值问题`，MATH，rank 6）；融合 `symdiff=0`。按预注册 §3.1 非 0 = 装置缺陷 ⇒ 本臂形式上 **NOT JUDGEABLE** | `build/agent-batch33/bm25/final.md` §0；`bm25.json.zeroDrift` |
| **F9** | 去截断（v2）实验是净伤害 | v1 截断 × 裸 B5：金标 49/90；v2 去截断 × 裸 B5：47/90；v2 × B512→A：31/90 且 p95 663/695ms。结论：公共 gram 通胀抬高「大别名量节点」的二值 count，去截断连原路由都没救回来 | `docs/kb-vector-topic-decision.md` §3.2（表 1–3 与结论段） |
| **F10** | 别名扩展与 trigram 的历史读数 | 同一 Stage-1 实验台（90 条，matched-only 口径；Stage-2 后生产判分同窗口）：A（FTS5 BM25）59/90；B（别名词典扩展）**54/90**；T（trigram tokenizer）58/90。别名扩展比 A 低 5 条 | `docs/kb-lexical-stage1-experiments.md` §7 表（第 85–91 行）与 §7.1 口径说明 |
| **F11** | 语料规模与节点长度 | 设备 v27 冷启日志：被索引节点 **3,718**；包内重算 **3,705 = 3,280 原子 + 425 主题**，差 13 未归因；特征行 546,872；avgdl 147.09；**62% 节点片段数 >16**（索引 KDoc 明写）；v2 实验时 `maxFeaturesPerNode=1672` | `docs/research/2026-11-02-kb-b33-bm25-prereg.md` §0.2；`KnowledgeSearchFeatureExtractor.kt:17-22`；`docs/kb-vector-topic-decision.md` §3.2 |
| **F12** | 2/129 的统计量级 | 仓内 bootstrap：融合 `B−A = 0.0155`，`ci95_chapter=[0.0, 0.0397]`（宽约 4pp ≈ 5 题）；词面 `B−A = 0.00775`，`[-0.0241, 0.0472]`。若逐题只有 2 条不一致且同向，McNemar 精确双侧 p=0.50；3:1 → 0.625；4:2 → 0.6875（本会话计算，仅量级说明） | `build/agent-batch33/bm25/final.md` §0 R3；本会话 `python -I` |

**F4 的口径更正（对本任务前提的修正）**：本仓金标不是 2–3 词短查询，而是整串 median 37 字（按 CJK 计 median 29 字，见 `docs/research/2026-11-02-kb-r1-chinese-lexical.md` §0）的自然语言问句；因此经典「短查询 IDF 不稳」的文献不能直接套用。真正对应的是仓内已登记的 D-01 失败类：**自然语言问句 vs 词条式节点名**，公共 2/3-gram 少 → 二值 count 低（`docs/kb-vector-topic-decision.md` §3.2 第 68 行：自由落体例 count=1，裸 B 路 rank 499/546）。

**F5 的一个关键读法**：BM25 让词面腿的 **MRR 上升**（0.6178→0.6218）但 **Recall 下降 1 条**（101→100），融合层再放大为 −2（117→115）。这不是「词面腿整体变差」的单一信号，而是「排序更尖、但把某类只靠公共 gram 命中的正确节点挤出去了」——与 F6 的两条机制一致。

**F6 的算术例子**（不是假设，是冻结公式的代入）：

- 一个干扰节点只命中 1 个稀有别名（`df=1`）：贡献 ≈ `7.815 × C(len)`。
- 一个正确节点只命中 3 个公共 2-gram（`df=1000`）：贡献 ≈ `3 × 1.000 × C(len) = 3.0 × C(len)`。
- 若两者长度相近，**稀有别名干扰项以约 2.6× 压过 3 个公共 gram 的正确项**。这正是 `COUNT(DISTINCT)` 与 BM25 的分歧点：前者把 3 个公共 gram 记 3 分，后者把它们几乎归零。

### 1.2 文献与工程事实（L1–L11）

**L1｜「BM25」不是一套公式，IDF 变体足以翻转名次。** `[官方]`
Lucene `BM25Similarity` 的 IDF 是 `log(1 + (docCount - docFreq + 0.5)/(docFreq + 0.5))`；SQLite FTS5 的 `bm25()` 文档只给 `IDF(q_i)` 与 `N`/`n(q_i)` 的定义，而仓内 r1 对 `fts5_aux.c` 的实读给出 `ln((N−n+0.5)/(n+0.5))`、**没有 `+1`**、`IDF≤0` 钳 `1e-6`、`k1=1.2`/`b=0.75` 硬编码、返回前乘 `-1`。BM25S 论文也明确列出 Lucene/Robertson/ATIRE/BM25L/BM25+ 等变体，并指出分数差异常被归因于分词与实现口径。
来源：<https://lucene.apache.org/core/9_12_1/core/org/apache/lucene/search/similarities/BM25Similarity.html>；<https://sqlite.org/fts5.html#bm25>；<https://arxiv.org/html/2407.03618>；仓内 `docs/research/2026-11-02-kb-r1-chinese-lexical.md` §1（对源码的实读）。

**L2｜`b` 的作用是「惩罚长文档」，不是「奖励长文档」。** `[官方]` `[工程]`
Elastic 的 BM25 讲解明确：`b` 放大长度比，`b=0` 取消长度归一；「all else equal, shorter-than-average documents are favored because their denominator shrinks, while longer documents are penalized」。本仓 `b=0.75`，所以长节点（别名多、特征行多）被系统性压低。
来源：<https://www.elastic.co/blog/practical-bm25-part-2-the-bm25-algorithm-and-its-variables>。

**L3｜BM25 对「非常长」的文档会过度惩罚，这是已知缺陷。** `[评测]`
Lv & Zhai（SIGIR 2011）原话：「the Okapi BM25 retrieval function tends to overly penalize very long documents」，并提出 BM25L 用「shift」修正。本仓 `len` 的分布从几十到 192（v2 时到 1672），正好落在该缺陷的作用区间。
来源：<https://dl.acm.org/doi/10.1145/2009916.2010070>；PDF：<https://lintool.github.io/robust04-analysis-papers/p1103-lv.pdf>。

**L4｜BM25F 的核心是「先按字段加权 tf，再做一次饱和」，不是「各字段各算 BM25 再线性相加」。** `[评测]`（逐字措辞 `[待验证]`）
Robertson/Zaragoza/Taylor（CIKM 2004）摘要：「compute scores for the individual fields ... independently and then combine these scores (typically linearly) ... can lead to poor performance by breaking the carefully constructed non-linear saturation of term frequency」；他们提出「weights term frequencies before the nonlinear term frequency saturation function is applied」。BM25F 教程同样表述为「first combines the term counts of the fields using field weights, and then applies the saturation step once」。
来源：<https://dl.acm.org/doi/10.1145/1031171.1031181>（ACM 正文 403；摘要句取自检索索引与 ResearchGate 摘要，逐字页码待验证）；<https://www.ozercemkelahmet.com/tools/bm25>。

**L5｜查询扩展会带来 query drift，可能伤 precision。** `[评测/待验证]`
Carpineto & Romano 的 QE 综述把 query drift 定义为「the alteration of the focus of a search topic caused by improper expansion」，并指出扩展项与原查询的单个词相关、而非与整个查询相关时，容易匹配到无关概念、伤害 precision。仓内 Stage-1 的 B 臂（别名词典扩展）54/90 < A 59/90，是同一方向的本地证据（F10）。
来源：<https://dl.acm.org/doi/10.1145/2071389.2071390>；综述 PDF：<https://www.iro.umontreal.ca/~nie/IFT6255/carpineto-Survey-QE.pdf>（本会话抓取超时，query drift 引文取自索引摘要与二手导读，逐字措辞待验证）。

**L6｜中文 bigram 是可行的索引表示，但「bigram TF-IDF 优于本仓二值 count」没有直接证据。** `[评测]` `[待验证]`
Chen 等（SIGIR 1997）在约 170MB TREC-5 中文语料、28 条长查询上比较 1-gram / bigram / short-word；IP&M 2004 的中文分词研究也把 bigram 列为三种表示之一，并指出「2-character words 比例更高通常有助于 IR 效果」。这些支持「bigram 可用」，不支持「换权重一定更好」。而且本仓**已经在用** 2/3-gram（F2），所以 (a) 不是新表示，只是重加权。
来源：<https://dl.acm.org/doi/10.1145/258525.258531>；<https://www.sciencedirect.com/science/article/pii/S0306457302000791>（仅摘要级）。

**L7｜凸组合（CC）权重是样本高效的，且比 RRF 更稳；但必须在 validation 上调。** `[评测]`
Bruch 等（arXiv:2210.11934）的结论：「learning of a CC fusion is generally agnostic to the choice of score normalization」「CC outperforms RRF in in-domain and out-of-domain settings」「with less than 5% of the training data ... α converges」；实验协议是「tuned α on the validation set of the in-domain datasets」，然后固定 α=0.8 在 test 上评估；他们观察到 `α∈[0.6,0.8]` 一致有增益。注意：这是他们的语料与腿，不是本仓 α=0.5 的正当性证明。
来源：<https://arxiv.org/abs/2210.11934>；全文：<https://arxiv.org/html/2210.11934>。

**L8｜在同一批数据上反复调参，会让误差估计和显著性失效。** `[评测]`
Dwork 等（Science 2015）：「Significance levels of standard procedures do not capture the vast number of analyses one can choose to carry out or to omit」；reusable holdout 的目的是让 holdout 在多次自适应分析后仍保持「fresh data」的保证。Carterette（TOIS 2012）进一步指出，同时检验大量假设会让 p 值「increase by orders of magnitude」。
来源：<https://www.science.org/doi/10.1126/science.aaa9375>；<https://research.google/blog/the-reusable-holdout-preserving-validity-in-adaptive-data-analysis/>；<https://dl.acm.org/doi/10.1145/2094072.2094076>。

**L9｜小 topic 集上的系统比较本身就不稳。** `[评测]`
Zobel（SIGIR 1998）对 TREC 的实测结论是「the measured relative performance of systems appears to be reliable」，但 recall 被高估、不少相关文档未被判定；后续工作（Carterette 的 multiple testing 线）反复说明 topic 数越少，检验之间的一致性越差。129 条不算大，2 条的差异更不够。
来源：<https://dl.acm.org/doi/10.1145/290941.291014>；摘要：<https://people.eng.unimelb.edu.au/ammoffat/sigir98/abstracts/zobel.html>。

**L10｜「先筛后权重」是对的，但「筛」不能是「用评测标签按 IDF 预筛」。** `[评测]`
Ambroise & McLachlan（PNAS 2002）证明：如果用全部样本先做特征选择、再做交叉验证，会产生 selection bias；正确做法是把特征选择放进每一折训练内部。把这个原则搬到本仓：若「按 IDF 预筛 192 特征」的 IDF 是用同一批 129 条金标或同一批评测查询算出来的，选出来的特征会偏向评测集，不能当作泛化证据。
来源：<https://www.pnas.org/doi/10.1073/pnas.102102699>。

**L11｜第一阶段召回是最终召回的上界（recall ceiling）。** `[工程]` `[待验证]`
多阶段检索的标准表述是：第一阶段负责高 recall 的候选生成，第二阶段只做重排；因此第一阶段的候选集是最终结果的上界。另有 2026 年的「recall ceiling」预印本给出形式化上界（`R[NDCG@k] ≤ Recall@|Wπ|`），但那是推荐系统语料，不是本仓 IR 评测，只能作类比。
来源：<https://www.twig.so/dev/the-essential-rag-book/multi-stage-retrieval>；<https://arxiv.org/abs/2609.27953>（`[待验证]`，领域不同）。

---

## §2 失效模式排查清单（可执行）

### 2.0 判定门槛：什么时候能说「这条腿无效」，什么时候只能说「实现/装置有问题」

**先说结论**：以本仓当前证据，最多能说「BM25 在这个集合上没有证明优于基线」，**不能说「词面腿无效」**，更不能说「BM25 机制被证伪」。理由：R4 `symdiff=1` 已使该臂形式 NOT JUDGEABLE；差异只有 2 题；两次 115 中只有一次是现行判官；且没有做 `b=0` 消融把「长度归一」与「IDF」分开。

| 结论强度 | 需要同时满足的证据 | 本仓现状 |
|---|---|---|
| **A. 实现/装置有 bug** | 同判官/同镜像/同内容 sha；`symdiff=0`；同一候选集；A/A′ 对拍容差 0；逐题可复现 | **不满足**：F8 `symdiff=1`；F7 有一次判官换版 |
| **B. 这条腿在本集合上没有可证明的收益** | 装置 A 满足；配对逐题差异的 CI 含 0 或 McNemar p>0.05；逐章无一致方向；效应量小于预注册 MDE | **基本满足**：F12 融合 B−A 的 CI 下界 0（含 0）；24 章 2 lower / 22 equal / 0 higher；2 题 < MDE |
| **C. 这条腿在本集合上无效（机制性）** | B + 机制可指认（如长度归一压掉某类节点）+ 消融能复现/消除该损失 + 在至少一个独立集合上同向 | **不满足**：机制只是从公式推导（F6），未做消融；无独立集合 |
| **D. 换公式/换权重有收益** | 预注册；按章留出；out-of-fold 增益 > MDE；逐章不劣；成本门通过 | **未做**：α 扫描、`b=0` 消融都未跑 |

**一句话判据**：**「无效」需要机制 + 消融 + 独立复现；「没证明更好」只需要一个可信的零结果。** 本仓现在只有后者。

### 2.1 检查清单（按执行顺序；前一项红，后一项的结论不成立）

| # | 检查 | 命令 / 读法 | 通过判据 | 若失败，归因到 |
|---|---|---|---|---|
| **G0** | 装置可比性 | 对比臂与基线的判官/镜像/资产/内容版本 sha；读 `bm25.json.zeroDrift`；确认 `symdiff=0` | 全部同 sha；`fused`/`lexical` symdiff 均为 0 | 装置缺陷，不是算法结论（F8 就是此例） |
| **G1** | 基线是否同轮重测 | 同一会话、同一设备、同一 `pm clear` 后重跑 B 与 A；不要拿历史 117 当 B | B 的同轮读数与历史差 ≤1 题；否则先解释漂移 | 内容/索引/判官漂移 |
| **G2** | 候选截断：正确答案是否在词面候选集内 | 读 `build/production-lexical-leg.tsv`（final.md §7 记录存在）或导出每题的 top-512 候选 ID；查 expected slug 是否在集合内、rank 是多少 | 目标节点在候选集内；若不在，先修召回，分数公式无用 | 词面 gate 失败（不是排序失败） |
| **G3** | 候选截断：top-5 边界 | 对每题记录 rank 5 / 6 的节点与分数差；统计「rank 6 且差 <5%」的题数 | rank 6 的题数应可解释；本臂已知 1 题 rank 6（F8） | 排序边界，优先调权重/融合，不是换公式 |
| **G4** | `df` 的域 | `SELECT COUNT(*) FROM knowledge_node WHERE verification_status IN ('CURATED','SOURCE_GROUNDED','USER_CONFIRMED') AND status!='RETIRED'`；`SELECT COUNT(*) FROM knowledge_search_feature`；逐特征 `COUNT(DISTINCT knowledge_node_id)` | N=3,718（不是 3,280）；df 的分母与召回 WHERE 完全一致；`df=0` 不参与；`df>N/2` 的钳位可复算 | 统计域错误（IDF 系统性偏大/偏小） |
| **G5** | 长度归一 | 对 expected 节点与 rank 5 竞争节点，取 `len`（该节点全部特征行数）与 `C(len)=2.2/(1+1.2(0.25+0.75·len/147.09))`；比较 `C` 与 `ΣIDF` | 目标节点的 `C` 不应系统性低于竞争节点；若低 >20%，记录为长度惩罚 | 长度归一（做 `b=0` 消融确认） |
| **G6** | 分词/特征口径 | 检查 `NON_SEARCH_CHARACTER`、`lowercase(Locale.ROOT)`、`MAX_WORD_LENGTH=48`、2/3-gram 重叠；查全角/半角、繁简、上下标、LaTeX 是否未归一；对 129 题统计「切分后片段数」与「单片段 >48 字」的题数 | 查询与索引使用同一函数；无未归一字符导致的零命中；单片段 >48 的题单列（本题集为 0/129） | 分词/归一化（最容易被误判成「IDF 无效」） |
| **G7** | 停用词/高 df 泛称 | `SELECT search_feature, COUNT(DISTINCT knowledge_node_id) c FROM knowledge_search_feature GROUP BY 1 ORDER BY c DESC LIMIT 50`；看 top-df 是否被公共 gram/泛称占据 | 高 df 特征不应是唯一命中路径；若正确节点只靠高 df 特征命中，BM25 的 `1e-6` 会杀掉它 | 高 df 特征（等价于「没有停用词表」） |
| **G8** | 查询侧截断 | 对 129 题调 `fromQuestion`，记录**片段数**与特征数；片段数 >16 的题单列（本题集 6/129）；特征数撞 128 的题单列（本题集 0/129） | 片段数 ≤16 或截断后仍覆盖目标别名；特征数 ≤128 | 查询侧 16 片段截断（F4），不是 48 字上限 |
| **G9** | 符号/归一化 | 确认进入 `DenseFusion` 的是 `s=−bm25≥0`，不是 `bm25` 负值；确认 min-max 的域是「有该腿分的节点」 | 词面腿最差候选归一为 0、最好为 1；缺失腿给 0；名次与 `s` 降序一致 | 符号写反（会把词面腿整体反相） |
| **G10** | 融合域 | 确认 `orderDomain = candidates.map(knowledgeNodeId)`（词面召回集），dense 只重排、不扩集 | 若预期是「dense 能补救词面漏召」，当前实现做不到；需显式改域 | 架构选择，不是 BM25 的错（F1/F10 注释） |
| **G11** | 逐章与配对统计 | 读 `bm25.json.chapters`；按章统计 lower/equal/higher；对逐题命中做配对 bootstrap 或 McNemar | 逐章无明显反向；CI 含 0 时不得声称优劣 | 统计过度解释（F12） |
| **G12** | 多重比较 | 数本轮一共试了几个臂/几个 α；若 >1，用 Holm/Bonferroni 或预注册唯一主臂 | 主臂预注册；次要臂只作探索 | 选择偏差（L8/L10） |

**G2/G3 的特别说明**：本仓当前 `orderDomain` **就是词面候选集**（`DenseRecallAssembly.kt:38-43`），所以词面腿既是「打分腿」又是「门」。如果 G2 失败（目标不在候选集），任何 α、任何 BM25 变体都救不了；如果 G2 通过而 G3 失败（rank 6），那是排序/融合问题，优先调权重。F8 的那条 `二次函数在闭区间上的最值问题` 是 rank 6；它至少说明该题的名次对装置/分数敏感。它是否为 2 条 Recall 损失之一、以及是否在候选集内，均未验证（见 §4 第 4 条），所以不能把它直接当作 G3 的已证实例。

**G4/G7 的特别说明**：本仓没有停用词表（`NON_SEARCH_CHARACTER` 只按非字母数字切分），所以「停用词」在本仓的等价物是「高 df 的公共 2/3-gram」。BM25 对 `df>N/2` 的特征钳 `1e-6`，等于给它们做了硬停用；这对「只靠公共 gram 命中」的题是致命的。排查时不要去找 `stopwords.txt`，要找 top-df 特征和「唯一命中路径」。

**G6 的特别说明**：本仓只做 `lowercase(Locale.ROOT)`，没有 NFKC/繁简归一；全角括号、上下标、LaTeX 命令会被 `NON_SEARCH_CHARACTER` 切碎或原样保留，查询侧与索引侧虽用同一函数，但**用户输入与知识库文本的字符形态差异**仍会造成零命中。这是「实现 bug」与「机制无效」最容易被混淆的一类。

**G9 的特别说明**：BM25 预注册 §1.2 明确警告「不得把 `bm25FTS5` 的负值直接交给 `DenseFusion`：min-max 在取负下变成 `1−norm`，词面腿的优劣会整体反相」。如果这条没查，任何「BM25 更差」都可能只是符号错误。

### 2.2 192 截断的三种顺序：可执行对比

| 顺序 | 定义 / 现状 | 机制后果 | 文献 / 本地证据 | 判定 |
|---|---|---|---|---|
| **插入序（现状）** | `canonicalName` → `aliases.sorted()` → `boundaryMarkdown`；片段 >16 时取 `last + first + balanced middle`；片段间轮转 | 谁进索引由**字典序**决定，与查询无关；别名列表的第 17 个以后可能完全不可见；192 上限再按轮转切掉每个片段的 n-gram | F2/F11：62% 节点片段数 >16；F9：v2 去截断 47/90 < v1 49/90 | 不是最优，但「取消上限」已被本地实验证伪；应先换**选择准则**，不是取消上限 |
| **按长度** | 保留最长别名/片段 | 长别名通常更具体，但产生更多 n-gram，会挤掉短而精确的别名；「长度=重要性」没有检索文献支持 | 无直接证据；只能作对照臂 | 不建议直接上；可作为 A/B 对照，检验「长别名是否真的更可召回」 |
| **按 IDF 预筛** | 用全局 df/IDF 选每节点 top-192 | 保留稀有特征（可能是噪声、一次性 boundary 短语）；丢掉共享领域词；若 IDF 用金标/评测查询算 = selection bias；且会改变 `df/N/avgdl`，必须重算统计 | L10（选择必须放进每折）；L4（BM25F 是**权重**，不是选择） | 不建议；若做，必须把选择放进每折训练内部，并在保留集上重算 `N/df/avgdl` |
| **按来源优先级（建议）** | `canonicalName` > 精确别名 > 别名 n-gram > `boundary` | 与「节点名是查询目标」的先验一致；可解释、可版本化；与 BM25F 的字段权重同源 | L4；F3（当前无字段列，需加列或 query-time 重排） | **推荐的「先筛」准则**；先做离线模拟（不改 schema），再决定是否持久化 |

**「先筛后权重」的推荐做法**：**是**，但「筛」应按**来源/结构**（字段、精确表面 vs n-gram），不是按长度或 IDF。顺序应是：① 固定字段优先级与每字段配额；② 在固定后的特征集上计算 `N/df/avgdl`；③ 再做 IDF 加权（BM25/BM25F）。如果反过来「先用 IDF 筛、再算 IDF」，筛选和统计共用同一批数据，既会产生 L10 的选择偏差，也会让 `avgdl` 失去意义。BM25F 的 TFA（先按字段加权 tf、再一次饱和）是「先合并、后饱和」的正面例子；它不是「先按 IDF 选特征」的授权。

---

## §3 替代方案对比与建议

### 3.1 四个方案的事实与证据对照

| 方案 | 机制 | 证据 | 本仓适配成本 | 预期 | 裁定 |
|---|---|---|---|---|---|
| **(a) 字符 bigram TF-IDF** | 用 bigram 的 TF-IDF 替代二值 count | 中文 IR 文献支持 bigram 可用（L6，`[待验证]`）；但本仓**已经在索引 2/3-gram**（F2），所以这不是新表示，只是去掉长度归一/换权重 | **低**：不需要 schema；等价于 `b=0` 的 IDF 和 | 中低：能验证「IDF 是否有用」，但不能解决「别名没进索引」 | **作为消融，不作为独立臂**（见 3.3 D0） |
| **(b) BM25F（canonical > alias > boundary）** | 字段加权 tf 后**一次**饱和；不是各字段分别算分再相加 | 文献支持最强（L4）；但本仓特征表**没有字段来源**，且 PK 去重使 `tf≡1`（F3），要做真正的 BM25F 需要 `(node, feature, field)` 行或字段计数 | **高**：抽取器 + schema + 重建索引 + 迁移；若只做 query-time 重排（用 seed record 的 canonical/alias 匹配），可不改 schema，但要多载入 40,841 个别名 | 中：只在「boundary 的 n-gram 淹没了 canonical/alias」被证实后才有高预期 | **先不做**；等 G5/G6 证明是字段混淆再上 |
| **(c) 查询扩展：别名反查** | 用别名词典把查询词扩成同组表面形式 | 仓内已测：B 臂 54/90 < A 59/90（F10）；文献的 query drift 风险（L5） | **低**：Stage-1 已有实现与判分 | 低：本地已经负向；再跑一次大概率重复 | **不重复**；最多保留「精确别名→canonical」的**门**，不做自由扩展 |
| **(d) 词面只当「召回存在性」，不参与排序** | 词面负责候选成员，dense 负责排序（α=1.0 或 gate-only） | 本仓**已经是**「词面定成员、dense 重排」（F1/F10 注释）；未测的是 α=0.75/1.0；Bruch 等支持 CC 权重样本高效（L7）；recall ceiling 说明第一阶段是上界（L11） | **最低**：一个常数/一次重排，无 schema | 中高：若词面分是噪声，α=1.0 会立刻变成「dense 在词面候选内排序」；若词面分有信号，α=1.0 会掉，说明应保留但降权 | **下一步首选**（见 3.2） |

**关于 (d) 的证据强度**：本会话没有找到「gate-only vs score-fusion」的直接 IR 对照实验；它的支持来自本仓架构（词面已定成员）与多阶段检索的第一阶段上界（L11），属于 `[工程]` 而非 `[评测]`。因此 (d) 的采纳必须靠本仓 W0/W1 的读数，不能引用外部结论当定论。

### 3.2 本仓下一步该测哪一条，为什么

**建议：先测 (d) 的「降权/门控」，同时用 `b=0` 做 (a) 的归因消融；不要先上 (b) BM25F，也不要重跑 (c) 别名扩展。**

理由按证据强弱排序：

1. **失败形态更像排序边界，而不是成员缺失。** 词面单腿从 101 掉到 100，融合从 117 掉到 115；已记录的零漂移差异是一条 rank 6（F8，是否为 2 条损失之一未验证）。G2/G3 的区分告诉我们：如果目标仍在候选集内，换公式的空间很小，调权重更直接。
2. **词面腿并非明显「质量低」，更可能是「权重过高」。** 词面单腿 101/129、融合 117/129，dense 在词面候选内净多命中 16 题；α=0.5 给词面 50% 权重，是否合理从未被检验。Bruch 等证明 CC 的 α 只需很少的 validation 数据就能收敛（L7），这正是本仓能承受的实验。
3. **换公式已经两次没有收益。** FTS5 等价（B33）与 Lucene 变体（L1/32A）都是 115；其中一次还不可比（F7）。在没有 `b=0` 消融之前，继续换 IDF 变体是「在同一维度上加倍下注」。
4. **BM25F 的成本/证据比最差。** 特征表没有字段来源、PK 去重（F3）；要做对，得改 schema + 重建 + 迁移。而「字段混淆」目前只是机制候选，没有被 G5/G6 证实。按 §12.2 的标准：**指认不出它消灭的具体失败，就不加这个机制**。
5. **别名扩展已经被本地实验否决。** 54/90 vs 59/90（F10），且文献的 query drift 风险明确（L5）；重跑只会增加多重比较负担。

**具体实验设计（最小、可预注册）**：

- **W0（主臂）**：`alpha=1.0`（词面只定成员，dense 排序），其余不变。
- **W1（次臂）**：`alpha=0.75`（词面 25%）。
- **W2（对照）**：`alpha=0.5`（现行基线，同轮重测）。
- **D0（归因，不是采纳臂）**：BM25 的 `b=0`（等价于「IDF 和、无长度归一」），`alpha=0.5`。它只回答「损失是不是长度归一造成的」，不参与采纳。

> **方向提醒**：`score = α·dense_norm + (1−α)·lexical_norm`。本仓 α=0.5 是 50/50；要「降低词面权重」必须把 α 调向 1.0，不是调向 0。W0/W1 就是 1.0/0.75 两个方向点。

预期读法：
- 若 W0 ≥ 117 且逐章不劣 → 词面分在排序上无净贡献，保留为 gate；后续不再给词面分投研究预算。
- 若 W0 < 117 且 W1 ≈ 117 → 词面分有信号但被高估，降权即可；不要再改公式。
- 若 W0、W1 都 < 117，且 D0 明显高于 BM25 → 损失主要来自长度归一，下一步才考虑 BM25F/字段权重或 `b` 的替代（BM25L/BM25+）。
- 若 D0 也 < 117 → IDF 本身在本语料没有帮助（公共 gram 主导），应考虑「词面只做 gate」并把研究预算转向 dense/候选扩集。

### 3.3 α 扫描的正确做法（预注册、避免过拟合 129 条）

**不要做的事**：在全部 129 条上跑 `α∈{0,0.05,...,1.0}`，挑最高的那个，然后把该点的 Recall 当结论。这会让选择本身吃掉数据（L8），而且 129 条的置信区间宽度约 ±4pp（F12），细网格的「最优点」几乎全是噪声。

**推荐协议**：

1. **先定主臂与 MDE**：只预注册 1 个主臂（建议 W0 或 W1），把其余当探索；最小可检测效应（MDE）按 5 题（≈3.9pp）设定——低于它的增益不采纳。
2. **按章留出，不按题随机**：129 条分布在 24 章，同章题目相关。用 leave-one-chapter-out（24 折）或 5 折**按章分组**；每折在训练章上选 α，在留出章上评估。
3. **粗网格**：`α∈{0.5, 0.75, 1.0}` 三个点足够（方向是降词面权重）。Bruch 等的结论是「存在一段 α 区间」而非一个点（L7）；粗网格 + 区间报告比细网格更稳。若还要检验「升词面权重」的相反假设，另立探索臂 `α=0.25`，不要和主臂混在一起挑。 
4. **决策指标用 out-of-fold**：报告 out-of-fold Recall@5、逐章最小值、以及配对 bootstrap CI；全量 129 的读数只作描述，不作选择依据。
5. **多重比较**：若同时跑 W0/W1/D0，用 Holm 校正，或明确「只有 W0 是 confirmatory，其余 exploratory」。
6. **固定装置**：同判官、同镜像、同内容 sha、同 `pm clear`；先过 G0/G1，再谈 α。

**为什么按章而不是按题**：金标是按章组织的（24 章），同章的查询共享知识点与措辞；按题随机会把同章近邻分到训练和测试，导致 α 在「见过的章」上过拟合。按章留出更接近「新章」的泛化问题。这一点与 L10 的「特征选择放进每一折」同源。

### 3.4 如果一定要动公式：最小改动顺序

若 W/D0 的结果指向公式，按以下顺序，不要跳步：

1. **`b=0`（或 `b=0.3`）**：只关长度归一，验证 L2/L3 的机制；成本最低。
2. **BM25L/BM25+**：如果确认是长文档过罚（L3），这两个是文献给出的直接修正；仍然不需要 schema。
3. **字段加权（BM25F-lite）**：先做 query-time 重排（用 seed record 的 canonical/alias 匹配给特征加权），不改 schema；只有证明有效才考虑 `(node, feature, field)` 的持久化。
4. **真正 BM25F**：最后才做 schema + 重建 + 迁移；此时必须有「字段混淆」的实测证据（G6/G7），否则就是 §12.2 说的多余机制。

---

## §4 未验证项

1. **`b=0` 消融未跑**。F6 的「长度归一压掉 1 条」是从公式与 `len` 分布推导的机制假设，不是实测；没有 D0，就不能把 115 的损失归因到长度归一。
2. **两次 115 不是独立复现**。32A/L1 的 115 在仓内台账标「不可比、不可复现」（F7）；只有 B33 的 115 是现行判官下的读数。任何「两个 IDF 变体都 115，所以是机制问题」的推论都过强。
3. **逐题配对表未取到**。F12 的 McNemar 数字是按「2:0 / 3:1 / 4:2」假设算的量级，不是实际 discordant 对数；需要 `bm25.json` 的逐题 `perQuery` 或重新导出才能做正式检验。
4. **两条丢失题的完整 rank 轨迹未取到**。只知一条 `二次函数在闭区间上的最值问题` 是 rank 6（F8），但它是 `zeroDrift.onlyInDevice` 的差异项，**不必然是 2 条 Recall 损失之一**；另一条未知。因此「是 G2 还是 G3」目前只对一条有线索。
5. **词面候选集的 top-512 成员未逐题核对**。`build/production-lexical-leg.tsv` 存在（final.md §7），但本文件未读其列结构与 129 题的 expected slug 成员率；G2 的通过/失败率是未测项。
6. **节点特征长度分布未按金标 expected 节点统计**。「长节点被惩罚」目前是全局 `avgdl=147` 与 `len∈[16,192]` 的推断，不是「129 题的 expected 节点恰好偏长」的实测。
7. **BM25F 的收益未在本仓验证**。L4 是文献结论（且 ACM 正文 403，摘要句来自检索索引/ResearchGate，标 `[待验证]`）；本仓没有字段来源，无法直接套用。
8. **中文 bigram 的比较结论只到摘要级**。L6 的「bigram 可用」有文献支持，但「bigram TF-IDF 优于本仓二值 count」没有直接读数；而且本仓已经在用 2/3-gram，不能把 (a) 当成新表示。
9. **α 扫描未跑**。3.3 是协议建议，不是本仓读数；W0/W1/D0 的预期是假设。
10. **`recall ceiling` 论文是推荐系统语料**（L11），不是 IR/本仓证据；只作「第一阶段是上界」的类比，不当作定量依据。
11. **停用词/泛称的 top-df 列表未取**。G7 的 SQL 未在本轮执行；「公共 gram 主导」目前只有仓内 v2 实验的定性描述（F9），没有当前索引的 top-50 df 表。
12. **装置零漂移未修复**。G0 的 `symdiff=1` 仍在（F8）；在修复前，任何新臂都只能算 exploratory，不能算 confirmatory。

---

## 附：一句话给下一步执行者

**先把 G0/G1 做绿（同判官、同镜像、`symdiff=0`、B 同轮重测），再跑 W0/W1/D0 三个点；用按章留出的 out-of-fold Recall 决策，MDE=5 题。不要在全量 129 上挑 α，不要在没有 `b=0` 消融前再换 IDF 变体，不要重跑已经被 54/90 否决的别名扩展。**
