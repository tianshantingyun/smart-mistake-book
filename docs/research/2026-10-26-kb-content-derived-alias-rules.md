# 从材料正文派生检索别名：先例、可冻结规则与风险

- 日期：2026-10-26
- 范围：KB 包 `moe-2025-four-subjects-v1`（manifest `version=20`，`contentVersion=ebf477816c318704`）
- 本代理实算基线：3,245 个节点全部至少绑定 1 份材料；25 份 teaching-support sidecar 共 64,973 份材料；每节点材料数中位数 13、最多 145；3,152 个节点已有“主名以外”的别名（`version=20` 包 + sidecar 实读）。
- 结论先行：**可规则化落地的是“候选生成 + 硬门 + 确定性排序”，不是有监督/向量化的关键短语抽取。** 对中文正文，最稳的候选源是“标点/标题/加粗切出的短句 + 跨材料重复”，不是词边界发现；对数学记号，最稳的做法是“NFKC 归一编码变体、保留运算符骨架、再要求一条分词器友好的正文转述”。本文给出 `body-alias-v1` 与它的风险边界，规则本身尚未实现、未跑设备/JVM。

---

## 0. 一页结论

1. **外部成熟做法分三族**：
   - 关键短语抽取（RAKE / YAKE / TextRank / TF-IDF）：共同结构是“候选生成 → 排序 → 短语成形”。
   - 知识库别名词表（Wikipedia redirect / DBpedia Lexicalization / Wikidata aliases）：别名面是**受控、可去重、可统计歧义**的，不是自由文本。
   - 文档扩展（doc2query）：把“文档能回答的查询”写回文档表示，直接支撑“能作答”这条判据。
2. **在“无新依赖、规则化、可复算”约束下可落地的**：
   - 用**标点/标题/加粗/数学定界符**做候选边界（RAKE 的 delimiter 思路、YAKE 的 special-character 预处理）；
   - 用**节点内材料频次/跨材料重复**做候选筛选（RAKE 的 degree/frequency、YAKE 的 frequency/dif-sentence 的简化），不用全局 IDF 作为主排序；
   - 用**定义句/能作答 section** 做质量门（教材术语抽取的 defining-sentence 先例）；
   - 用**学科级 df 上限 + 全局互斥**做过宽门（DBpedia 的歧义统计、现有 `rebuild_aliases` 的全局互斥）。
3. **不能落地的**：TextRank 的 POS 过滤（需要中文分词/POS 依赖）、KeyBERT/嵌入式方法（模型依赖）、doc2query 的 seq2seq（模型依赖）、完整 accessor-variety 词发现（需要语料级左右熵统计，且会引入新的词边界判定）。
4. **记号的真实行为（本代理实跑探针）**：
   - NFKC 会把 `＋→+`、`²→2`、`ₓ→x`、`𝑥→x`、`µ→μ`、`Å→Å`，但**不会**统一 `×/·/*`、`−/-/–`、`λ/μ`。
   - 生产查询特征提取器对纯记号查询 `x+y`、`λ+μ`、`x^2+y^2` 产出 **0 个特征**；`x2+y2` 产出 `x2/y2`；`系数和` 产出 `系数和/系数/数和`。
   - 因此“把 `x+y` 直接写成别名”在词面腿上是无效的；必须同时给出正文里的中文转述（如“系数和”“x 与 y 的和”），或者接受它只服务 dense/展示层。
5. **`body-alias-v1` 的骨架**：输入只读节点绑定材料的 `contentMarkdown`；三类候选（重复短句、标题/加粗短语、记号 span+正文转述）；八道门（形状、逐字出现、同 section 能作答、节点相关、学科特异、全局互斥、分词器友好、禁读评测输入）；每节点最多 6 条；参数与包 contentVersion 一起 sha256 冻结；只写候选表，不直接改 `alias_map.csv`。
6. **最大风险不是“抽不到”，而是“抽到通用词/例句”**：本代理用真实包跑朴素 n-gram 原型，4/4 抽样节点的 top-6 被 `直线/函数/Mg/反应/“=k”/“x+”` 这类片段占据；换成“重复短句”原型后质量明显变好，但仍混入 `一般地/特别地/反之也成立` 这类话语标记。规则必须带学科 df 上限与话语标记停用表，否则会把索引越补越噪。

---

## 1. 本地约束与既有证据

### 1.1 别名被谁消费（错一个不是“文案问题”）

| 消费点 | 代码 | 对别名的用法 | 后果 |
|---|---|---|---|
| 词面索引 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt:44-57` | `fromNode` 把 `canonicalName + aliases.sorted() + boundaryMarkdown` 一起抽成 2–3 字 gram 与整段词，节点侧上限 16 片段 / 192 特征 | 别名进入 SQL 词面召回；错误别名把错误节点顶上来 |
| 生产索引写入 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexBuilder.kt:210` | `toSearchFeatures()` 逐节点调用 `fromNode` | 同一份抽取器，无第二套分词 |
| 模型候选菜单 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeContextRetriever.kt:103-117` | 别名重合权重 7、精确短语加 70；主名权重 8、精确短语加 80 | 别名会影响“模型可以绑定哪些节点”的菜单 |
| 错题组织 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt`、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiProblemOrganizationProtocol.kt` | 用 `aliases + canonicalName` 匹配学生文字 / 发给模型挑点 | 别名指向别的概念 = 把组织与讲题引到错误节点 |

别名集合的包契约还规定 `aliases[0]` 必须等于主名（`tools/kb_build/check_pack_contract.py:89-92`），所以“别名”实际是“主名 + 额外检索面”的集合。

### 1.2 现有派生规则：只从材料标题来

`tools/kb_build/rebuild_aliases.py` 的判据是三条（文件头 `:9-28`）：

1. 主名永远保留；
2. 别名必须是**该节点自己绑定材料的标题**，且形状合格：≤24 字、无句末标点/逗号、不是残片、过坏名检测（`title_usable`，`:78-99`；`NAME_MAX=24`、`SENTENCE_PUNCT`、`BAD_PREFIX/BAD_SUFFIX` 见 `:57-63`）；
3. **同学科全局互斥**：一个名字被两个以上节点 claim 时全部丢弃（`:218-240`）。

人工例外表 `tools/kb_build/tables/alias_exceptions.csv` 是定稿层，仍要过形状门、不得等于同学科另一节点主名、同一名字不能登记给两个节点（`_validate_exceptions`，`:138-179`）。

**这意味着**：现有别名是“标题派生 + 人工例外”，正文只作为例外条目的 `reason` 证据，不直接生成别名。`body-alias-v1` 要补的是**正文通道**，不是替代标题通道。

### 1.3 分词器与索引的真实行为

`KnowledgeSearchFeatureExtractor.kt`：

- `NON_SEARCH_CHARACTER = Regex("[^\\p{L}\\p{N}]+")`（`:117`）——所有非字母/数字都是分隔符；
- 每个片段：整段长度 2–48 时保留整段，再补 2–3 字 gram（`:95-102`）；
- 节点侧：最多 16 片段、192 特征，超限时保留“末段 + 首段 + 均衡中间段”（`:7-10`、`:71-84`）。

`KnowledgeContextRetriever.kt:143-161` 的 `SearchText.of` 则先把整串压成 `filter(Char::isLetterOrDigit)` 的 compact 串，再取 2–3 字 gram + 按非字母数字切出的词。

**本代理的逐行翻译探针**（只用于观察算法，未运行 Kotlin/JVM 生产代码）：

| 输入 | `fromQuestion` 特征 | 说明 |
|---|---|---|
| `x+y` | `[]` | 切成 `x`、`y`，都只有 1 字，不产生 2–3 字 gram |
| `λ+μ` | `[]` | 同上 |
| `x^2+y^2` | `[]` | 切成 `x`、`2`、`y`、`2`，全为 1 字 |
| `x2+y2` | `['x2','y2']` | 数字并入片段，反而可检索 |
| `系数和` | `['系数和','系数','数和']` | 中文术语天然有 2–3 字 gram |
| `x+1/x的最小值` | `['x的最小值','x的','的最','最小','小值','x的最','的最小','最小值']` | 记号前缀被丢掉，只剩中文后缀可匹配 |
| `m+n=2p的等比性质` | `['2p的等比性质','2p','p的','的等','等比','比性','性质',…]` | 记号部分只剩 `2p` |

`KnowledgeContextRetriever.SearchText.of` 的 compact 行为更糟：

| 输入 | compact | 特征 |
|---|---|---|
| `x+y` / `x-y` / `x*y` / `xy` | 都是 `xy` | 都是 `['xy']` |
| `λ+μ` / `λ-μ` / `λμ` | 都是 `λμ` | 都是 `['λμ']` |
| `a·b` / `a b` | 都是 `ab` | 都是 `['ab']` |

**结论**：在词面腿里，运算符被丢弃后 `x+y` 与 `x-y` 不可区分；纯记号查询甚至没有特征。别名若只写 `x+y`，既救不了纯记号查询，也无法提供区分度。

### 1.4 已测上界：正文/标题别名是“低收益、可逆”的补充

- 批次16：材料标题/正文派生（T/C）24 条 / 16 节点 → 模拟词面 89→93（**+4**）；题干式人工别名 48 条 → 89→113（+24），但属“用 129 条金标反推索引”，按纪律**不得采用**；全包盲目缩短 >24 字题名 → 净 **−1**（16 片段截断位移）。（`docs/kb-batch16-2026-10-22.md:31`、`docs/kb-direction-2026-10-22.md:15`）
- 批次19：A1 保守别名臂（31 条 T/C-only，21 节点）→ 设备词面 **102/129 = 0.7907**、融合 **117/129 = 0.9070**；翻转分解 17 条中 13 条=词面别名增益。（`docs/kb-batch19-2026-10-23.md:9,47,110`、`docs/research/2026-10-22-d3-round3-a1-prereg.md:7`）
- 31 条候选里 **15 条**的 `reason` 明写“正文含……”（本代理实读 `knowledge-production/alias-exceptions-candidate-2026-10-22.csv`），说明正文证据已经在用，但仍是人工挑条，不是规则化派生。

**这组数字的作用**：它把“正文派生”的预期钉在“低收益、可逆、不能救 absent”这一档；`body-alias-v1` 的价值是**把人工挑条变成可复算的通道**，不是把 0.9070 推到更高。

---

## 2. 外部先例（含交叉验证）

> 阅读方式：优先读原始论文/官方文档；能下 PDF 的用 `pdftotext` 抽取后读原文；不能访问的页面用 OpenAlex 的题录+摘要交叉核对。每个结论至少给两个独立来源；只有单一来源的，在 §6 标“待验证”。

### 2.1 关键短语抽取：候选生成 → 排序 → 短语成形

`A review of keyphrase extraction`（Papagiannopoulou & Tsoumakas, arXiv:1905.05044）把无监督关键短语抽取的步骤写成：

1. 用启发式选候选词单元（排除停用词、限定词性等）；
2. 给候选排序；
3. 由高分词选词或由高分短语成形为关键短语。

这与 RAKE/YAKE/TextRank 的原始描述一致，可作为“候选筛选”而非“别名判定器”的共同框架。

| 方法 | 候选生成 | 排序/筛选 | 依赖 | 在本项目可落地性 |
|---|---|---|---|---|
| **RAKE**（Rose et al. 2010） | 用停用词、短语分隔符、词分隔符把文本切成候选短语；保留停用词内部的短语（通过重复相邻词对识别） | 词频/词度（degree）及其比值，短语分数=组成词分数之和；取 top T | 停用词表 + 分隔符集合 | **可落地**：分隔符换成中文标点/换行/Markdown 标题；词度改成“片段在多少份材料中出现” |
| **YAKE**（Campos et al. 2018） | 按空格/特殊字符切词；滑窗生成 1/2/3-gram 候选 | 5 个局部特征：Casing、Word Positional、Word Frequency、Word Relatedness to Context、Word DifSentence；分数越小越重要 | 无需词典/语料训练/POS | **部分可落地**：Casing 对中文无效；Position/Frequency/DifSentence 可做；Relatedness 需要“词”单位，中文无分词时不可靠 |
| **TextRank**（Mihalcea & Tarau 2004） | 分词 + 词性标注；只保留名词/形容词等开放类词；同窗口（2–10 词）共现建图 | PageRank 迭代；取 top T（T=顶点数 1/3）；后处理把相邻关键词合并成多词短语 | 分词 + POS | **不落地**：中文 POS/分词是新依赖；规则化版本会退化成“共现图”，收益不确定 |
| **TF-IDF**（Salton & Buckley 1988；scikit-learn 实现） | 词/ n-gram | `tf(t,d) × idf(t)`；idf 需要全语料文档频率 | 语料统计 | **部分可落地**：节点内 df 可算；全局 idf 可作为“通用词过滤”，不宜作主排序 |

关键原文/文档：

- RAKE 原章摘要（OpenAlex 题录）：无监督、领域无关、语言无关；比 TextRank 更高效，precision 更高、recall 可比；还提出面向特定领域/语料的停用词表生成方法。
- RAKE 的候选/打分在综述里写得更具体：`Anything between stop words`，权重 `deg(w)freq(w)`（`Keyword Extraction: A Modern Perspective`, SN Computer Science 2023, Table 8）。
- YAKE 原论文（ECIR 2018）摘要：不依赖词典/同义词库、不在任何语料上训练；用 5 个文本统计特征；候选用 1/2/3-gram；分数越小越相关。
- TextRank 原论文 §3.1：分词+POS；最好结果是只保留名词/形容词；共现窗口 2–10；只把单词作为顶点；T 取顶点数 1/3；后处理把相邻关键词合并。
- TF-IDF：scikit-learn 文档给出 `tf-idf(t,d)=tf(t,d)×idf(t)`，默认 `idf(t)=log((1+n)/(1+df(t)))+1`；并明确“计算 idf 需要语料统计”（`n` 与 `df(t)` 在 `fit` 时学到）。
- LIAAD/yake 官方 README：轻量无监督、无需训练/外部语料/词典、语言与领域无关；分数越低越相关。

**对本项目的取舍**：

- 用 RAKE 的“分隔符候选”思想，但分隔符是**中文标点、换行、Markdown 标题/加粗、数学定界符**，不是空格；
- 用 YAKE 的“位置/频次/跨句分布”做节点内排序，不用 Casing/Relatedness；
- 用 TF-IDF 的“文档频率”只做**通用词过滤**（学科级 df 上限），不做全局主排序；
- 不用 TextRank（POS 依赖）和任何嵌入/模型方法。

### 2.2 中文：没有词边界，就不要假装有

- `Chinese text retrieval without using a dictionary`（1997）摘要：中文没有分隔符；该文比较了不用词典的检索，说明 bigram 等字符级索引可以达到可用效果。
- `Comparing representations in Chinese information retrieval`（1997）摘要：比较 unigram、bigram、基于简单分词的 short-word 三种表示，三者检索性能可比。
- `PAT-tree-based keyword extraction for Chinese information retrieval`（1997）摘要：用 PAT-tree 从相关中文文档集中自动抽取关键词，不依赖词典。
- `Accessor Variety Criteria for Chinese Word Extraction`（2004）摘要：用字符串左右相邻字符的多样性（predecessor/successor）判断它是否像一个词。

**对本项目的取舍**：

- 不做词边界发现；候选边界只来自**显式分隔符**（。！？；，、换行、`##`、`**`、`$`）；
- 用“**跨材料重复的短句**”作为中文候选的主要来源（对应 PAT-tree 的重复子串思想，但只用整句/短句，不做任意长度子串）；
- 不实现完整 accessor variety（需要语料级左右熵，且会把“看起来像词”的片段大量放进来；本代理的 term-head 原型已经出现 `勾函数/克定律/学性质/2反应` 这类边界错误）。

### 2.3 教育教材：定义句是术语抽取的单位

`An unsupervised linguistic-based model for automatic glossary term extraction from a single PDF textbook`（Education and Information Technologies, 2023）的摘要与方法：

- 输入单本 PDF 教材，输出关键术语；不依赖统计技术或外部知识库，只依赖 pattern recognition、句子切分、POS、chunking；
- 核心单位是 **defining sentences**：把句子分为 is-a（系动词）与 not-is-a，再在条件下筛出四类定义句（ISm、ISx、NISsmv、NIScalled，后者含 called/known as/referred to as/termed 等）；
- 术语从定义句中抽取，因此“术语出现一次也能抽”，不需要词频阈值。

**对本项目的取舍**：

- 采纳“**能作答的 section**”这一单位：候选必须落在含定义/方法/边界/关系/结论 cue 的同一 section，而不是整篇材料任意位置；
- 不采纳 POS/chunking（新依赖）；
- 该文的“术语出现一次也能抽”与我们的“节点内 df≥2”是两种取向：**我们的规则更保守**，优先跨材料重复，df=1 只允许标题/加粗/与主名同句的结构化候选。

### 2.4 知识库/搜索引擎的“别名面”：受控、去重、统计歧义

- **DBpedia Lexicalization Data Set**（LREC 2012, §2.2）：
  - 别名词表来自 **Titles、Redirects、Disambiguation Links** 三类；
  - “Redirects to URIs indicate synonyms or alternative surface forms, including common misspellings and acronyms”；重定向可能指向重定向，因此取**传递闭包**；
  - 规模与歧义：约 **4.4M** 个 surface form 无歧义、**392k** 有歧义；整体平均歧义 **1.22**，仅看有歧义 surface form 时平均 **2.52**；每个资源平均 **2.32** 个别名；
  - 消歧页的标签会成为所有目标资源的 surface form（并去掉尾部括号）。
- **Wikipedia redirects**（Hill & Shaw 2014, `Consider the Redirect` 摘要）：重定向在英文维基百科中占全部条目页的**过半**，但长期被研究忽略；忽略重定向会改变已有研究结论。
- **Elasticsearch synonym token filter**（官方文档）：支持等价集合（`ipod, i-pod, i pod`）和显式映射（`personal computer => pc`）；“左侧的词会展开为右侧所有可能”；过滤器顺序重要，`word_delimiter_graph`/`ngram` 与 synonym 叠加会报错；`updateable` 只用于 search analyzer。
- **doc2query**（Nogueira et al. 2019, arXiv:1904.08375）摘要：用“文档可能被问到的查询”扩写文档表示，再配合重排达到 SOTA；核心观察是“从问答系统视角，扩写内容可以包含文档**能回答**的问题”。

**对本项目的取舍**：

- 别名面必须是**受控词表**：全局互斥、例外表、来源可追溯；这与现有 `rebuild_aliases` 的判据 3 和例外表一致；
- “正文派生”不是自由生成：必须有来源材料、证据 span、去重/歧义门；
- doc2query 的“能回答”判据可采纳，但其 seq2seq 实现不采纳（模型依赖、不可复算）。

### 2.5 数学记号：归一编码变体，保留结构，符号同义靠正文

- **Math-Aware Search Engines: Physics Applications and Overview**（arXiv:1609.03457）：
  - LaTeX 里同一符号有多种写法（`\mathbf{symbol}`、`\pmb{symbol}` 等）；Unicode 里 Å 有 `LATIN CAPITAL LETTER A WITH RING ABOVE`（0xC5）与 `ANGSTROM SIGN`（0x212B）两种编码；数学搜索引擎会处理这些编码差异，但遇到非标准编码仍会出问题（§3.3）。
  - MIaS 区分带下标与不带下标的变量：查询 `x1 + y1` **不会**检索 `x + y`；同时支持子表达式匹配、结构匹配（`x + y/z` 匹配 `a + b/c`）与交换律（`a + 5` 等于 `5 + a`）（§5.7.3）。
  - “presentation-based（句法）”与“meaning-based（语义）”是两条路线；语义路线仍不完美。
- **WebMIaS on Docker**（arXiv:2106.00411）：MIaS 跑在 Lucene/Solr 上；文本做分词/词干化，数学输入（MathML）在索引与查询两侧都做 **canonicalized, ordered, tokenized, and unified**。
- **Efficient Algorithm for Math Formula Semantic Search**（IEICE 2016, §2.1）：数学检索的预处理包括 segmentation、normalization（修复破损 MathML、去属性）、enrichment；近似匹配可用 thesaurus（**给符号加同义词**）、canonical orderings、enumerating variables、replacing symbols with their types、simplification；MIaS 用了 ordering、tokenization、variable unification、constant unification。
- **Math Information Retrieval using a Text Search Engine**（Waterloo thesis, §2.2/§2.3）：数学公式通常先线性化成 LaTeX 再编码为文本，因此可以像文本一样被 Lucene 分词、索引；Lucene 的 analysis 链是 character filtering/normalization → tokenization → token filtering。

**对本项目的取舍**：

- **归一编码变体**：NFKC + 闭集 LaTeX 命令映射；不尝试交换律/结构等价（那是数学搜索引擎的语义层，超出别名范围）；
- **保留运算符骨架**：别名显示形式保留 `+ − × · =` 等；不要为了“分词器友好”把 `x+y` 删成 `xy`（那会与 `x-y` 撞车，见 §1.3）；
- **符号同义靠正文**：MIaS 的 thesaurus 是“给符号加同义词”，我们对应的是“正文里的中文转述”（如 `λ+μ` ↔ “系数和”），用共现/定义模式连接，而不是维护一张符号同义词大表；
- **下标/上标**：NFKC 会把 `²→2`、`ₓ→x`，这会抹平 MIaS 刻意保留的 `x1` vs `x` 区别；本项目词面索引本来就把它们压平，v1 接受这一限制并记录。

### 2.6 归一与分词器的硬事实

- **Unicode UAX #15（NFKC）**：NFKC = Compatibility Decomposition + Canonical Composition；兼容等价可能“有不同视觉表现或行为”，例子包括宽度变体、上下标、带圈/带方框字符、分数、字体变体；UAX #15 明确警告 “Normalization Forms KC and KD must not be blindly applied to arbitrary text”，它们更适合字符集受限的领域。
- **Unicode UAX #29（默认词边界）**：`WB5 AHLetter × AHLetter` 把相邻字母连成一个词；`WB999 Any ÷ Any` 表示其余位置可断。因此 `x+y` 的默认切分是 `x ÷ + ÷ y`，`+` 是独立片段，不属于 `x` 或 `y`。
- **Lucene StandardTokenizer**：实现 UAX #29 的 Word Break 规则；`+` 不会成为词的一部分。
- **Lucene WordDelimiterGraphFilter**：默认把所有非字母数字当作 intra-word delimiter；可 `CATENATE_WORDS`（`wi-fi → wifi`）、`PRESERVE_ORIGINAL`（`500-42 → 500, 42, 500-42`）、控制 `SPLIT_ON_NUMERICS`。
- **Lucene Classic QueryParser**：`+` 是 required operator（`+term` 必须存在），`-` 是 prohibit operator；保留字符还包括 `&& || ! ( ) { } [ ] ^ " ~ * ? : \ /`。若别名被粘进这类查询框，前导 `+/-` 会被当运算符。
- **Elasticsearch standard analyzer**：Standard Tokenizer + lowercase + stop（默认关闭）；`Brown-Foxes → brown, foxes`，标点不输出。

**本代理的 NFKC 探针**（Python `unicodedata.normalize('NFKC')`，仅验证标准行为）：

| 输入 | NFKC 输出 | 说明 |
|---|---|---|
| `＋` U+FF0B | `+` U+002B | 全角→半角 |
| `²` U+00B2 | `2` U+0032 | 上标→数字 |
| `ₓ` U+2093 | `x` U+0078 | 下标→字母 |
| `𝑥` U+1D465 | `x` U+0078 | 数学斜体→ASCII |
| `µ` U+00B5 | `μ` U+03BC | 微符号→希腊 mu |
| `Å` U+212B | `Å` U+00C5 | 埃符号→A-ring |
| `×` U+00D7 | `×` | **不**变 |
| `·` U+00B7 | `·` | **不**变 |
| `−` U+2212 | `−` | **不**变（≠ ASCII `-`） |
| `λ` U+03BB | `λ` | **不**变 |

**两条直接结论**：

1. NFKC 只能解决“同一符号的不同编码”，不能解决“不同运算符的语义差异”；运算符映射必须是**闭集、显式、可审计**的，不能靠 NFKC 自动统一。
2. 任何面向词面检索的别名，都必须先过“生产查询特征提取器”这一关；纯记号串在这一关是 0 特征，必须用正文转述替代。

---

## 3. 可冻结规则 `body-alias-v1`

> 定位：构建期（`tools/kb_build`，Python 标准库）规则；只产出候选表，不直接改包；运行时仍消费冻结后的别名表。规则本身尚未实现；以下是可实现的规格。

### 3.1 输入、输出、边界

**输入（白名单）**：

- 包节点：`subject`、`slug`、`name`、`aliases`（只用于去重，不用于候选生成）；
- 该节点的绑定材料：`slug`、`title`、`type`、`bindings[].role`、`contentMarkdown`；
- 只取 `role ∈ {PRIMARY, SUPPORTING}` 的绑定；`PREREQUISITE` 材料的正文属于别的节点，排除（当前 v20 包全部是 `PRIMARY`，见 §4.1）。

**显式不读**：

- `summaryMarkdown`、`applicabilityMarkdown`、`boundaryMarkdown`（它们是更短、更接近标题的元数据；v1 只做“正文”通道，避免与标题派生重复）；
- 任何评测/查询数据：`golden_queries*`、`*-misses.txt`、`build/agent-*`、用户提问日志、检索失败清单、章节分布；
- 任何网络/模型/外部词典。

**输出**：`body_alias_candidates.csv`，一行一个候选：

```
subject,slug,alias,kind,source_material_slug,evidence_section,evidence_span,df_node,df_subject,rank,rule_version,rule_hash,pack_content_version
```

`kind ∈ {S1_REPEATED_CLAUSE, S2_STRUCTURAL, S3_NOTATION, S3_PARAPHRASE}`。每节点最多 6 行；不足 6 行不补空。规则只写候选表，进包仍走 `alias_exceptions.csv` + `rebuild_aliases --write` + `promote` 的既有链。

### 3.2 定义

- `B(N)`：节点 N 的 `role ∈ {PRIMARY, SUPPORTING}` 材料按 `slug` 排序后的 `contentMarkdown` 序列。
- `section(m)`：按 Markdown 标题 `^#{1,6}\s+` 切出的 `(heading, body)`；标题前的正文 `heading=""`。候选必须能定位到唯一 section。
- `clause`：对 section body 按 `。！？；;，,、\n` 切分、去 Markdown 标记（`* _ ` > #`）、NFKC 归一后的片段；保留数学定界符内容。
- `term-like`：归一后 2–24 字；不含句末标点/逗号；不含 `$` 或未转义的 `\`；括号配对；不在通用停用表；不是纯数字/纯运算符；不是单字母；不是 `^[A-Za-z]\s*=\s*[0-9]` 这类赋值；不是纯话语标记。
- `answer-bearing section`：同一 section 内至少出现一个 cue：
  - 定义：`是指 / 称为 / 叫做 / 定义为 / 表示 / 记作 / 即 / 记为`
  - 方法：`步骤 / 先 / 再 / 然后 / 最后 / 方法 / 判断 / 求解 / 转化为`
  - 边界：`注意 / 易错 / 不能 / 必须 / 区分 / 当 / 若`
  - 关系：`等于 / 大于 / 小于 / 成正比 / 成反比 / 取值范围`
  - 结论：`因此 / 所以 / 可得 / 结论`
- `node-related`：`df_node(candidate) ≥ 2`；若节点只有 1 份材料，则要求候选在该材料的 ≥2 个不同 section 中出现；或候选出现在该节点某材料的标题/加粗 span；或候选所在 clause 同时含 `name`/现有别名。
- `specific`：`df_subject(candidate) < 2% × 该学科材料数`，或 `df_subject(candidate) < 25`（小科室地板）。`df_subject` 只数该学科全部材料的 `contentMarkdown`，不数标题。
- `tokenizer-safe`：把候选串送进生产 `fromQuestion` 的同一算法（`KnowledgeSearchFeatureExtractor` 的查询侧规则），返回非空特征集；纯 `x+y/λ+μ` 为 false，`x2+y2/系数和` 为 true。
- `global unique`：同学科内，候选不得等于另一节点主名，也不得被另一节点从标题/正文派生；多节点 claim 时全部丢弃，除非例外表登记归属（复用 `rebuild_aliases` 的 claim 逻辑）。

### 3.3 候选来源（只这三类）

**S1 重复短句（主来源）**

- 对 `B(N)` 的每个 clause，若 `term-like` 且长度 2–24，进入计数；
- 保留 `df_node ≥ 2`（在 ≥2 份不同绑定材料中出现）的 clause；
- **单材料节点回退**：若节点只有 1 份材料，允许“同一 clause 在该材料的 ≥2 个不同 section 中出现”作为 `df_node=2` 的等价证据；这条只救 205 个单材料节点，不改变多材料节点的口径；
- 同一材料内重复只计 1 次 df，但计入 `tf`；
- 不做任意长度子串扩展（本代理原型显示，2–8 字 n-gram 会把 `直线/函数/Mg/反应/=k/x+` 这类片段顶上来；见 §4.3）。

**S2 标题/加粗短语（结构化来源）**

- `section.heading` 与 `**...**` span 中 2–24 字、`term-like`、且不是通用章节名（`方法步骤/易错规避/典型例题/注意事项/知识梳理/考点/…`）；
- 只要求 `df_node ≥ 1`，但必须在同一 section 有 cue；
- 这条路主要救“正文只出现一次、但被作者加粗/做小标题”的术语。

**S3 记号 span（同域记号来源）**

- 抽取 `$...$`、`$$...$$`、`\(...\)`、`\[...\]` 内的内容，以及正文中含数学符号的连续串；
- 归一：NFKC → 去 `$`/`\(`/`\)`/`\[`/`\]` → 去 `\left/\right/\,/\;/\!/\ ` → 闭集 LaTeX 命令映射（`\lambda→λ`、`\mu→μ`、`\times→×`、`\cdot→·`、`\le→≤`、`\ge→≥`、`\neq→≠`、`\in→∈`、`\sum→∑`、`\sqrt→√`、`\to→→`、`\Rightarrow→⇒` 等）→ 合并空白；
- **不删除运算符**；**不**把 `−/–/—` 自动并成 `-`（只在显示层做可选折叠，证据保留原文）；**不**把 `×/·/*` 互转；**不**把 `λ` 映射成 `x`；
- 若归一后的记号 `tokenizer-safe`（如 `x2+y2`），作为候选；
- 若 `tokenizer-safe` 为 false（如 `x+y`、`λ+μ`），在**同一 section、记号出现位置 ±50 字**内找正文转述 `T`：
  - `记号（即 T）`、`记号，即 T`、`记号（称为|叫做|记为|记作|表示为）T`、`T（称为|叫做|记为|记作|表示为）记号`、`T（为|是|表示|对应）记号`；
  - `T` 必须 `term-like`、过所有门；通过则产出 `S3_PARAPHRASE`（`T` 是别名，记号进 `evidence_span`）；
  - 找不到 `T`：只登记 `notation_only`（不进候选表，留给 dense/展示层），**不产出词面别名**。

### 3.4 八道门（全部满足才保留）

| 门 | 规则 | 消灭的失败 |
|---|---|---|
| G1 形状 | 复用 `title_usable`（≤24、无标点/逗号、无 BAD_PREFIX/SUFFIX、括号配对、无 `$`/`\`、坏名检测）+ 正文停用表 + 首尾虚词门（不以 `反/所以/因此/但/而/若/当/则/即/也/都/可/能/要/应/注意/记住` 开头；不以 `时/后/前/中/上/下/内/外/的/和/与/或/及/为/也/都/能/就/才/则` 结尾） | 句子片段、残片、纯话语标记 |
| G2 逐字 | 候选（归一后）必须在 `B(N)` 中逐字出现；记录来源材料 slug、section、span | 模型臆造、改写失真 |
| G3 能作答 | 候选所在 section 含 §3.2 的 cue 之一；题面式 section（只有“已知/求/下列/判断/计算”）不算 | 把题干/无解释材料当别名 |
| G4 节点相关 | `df_node≥2`，或标题/加粗，或与主名/现有别名同句 | 从例题里捞到与节点无关的词 |
| G5 学科特异 | `df_subject < 2%` 或 `<25` | 通用词（函数/反应/细胞/直线/弹力） |
| G6 全局互斥 | 复用 `rebuild_aliases` 的 claim 逻辑；同学科一个名字只归一个节点；例外表登记优先 | 一词多节点，检索歧义 |
| G7 分词器友好 | 词面别名必须 `tokenizer-safe`；记号不友好时必须由 `S3_PARAPHRASE` 替代 | 写进去但查询侧 0 特征 |
| G8 禁读评测 | 构建脚本只打开 §3.1 白名单路径；出现 `golden_queries`/`misses`/`build/agent-` 路径即失败 | 题面反推/金标拟合 |

### 3.5 排序与截断

排序键（字典序，从高到低）：

1. `kind`：`S2_STRUCTURAL` > `S1_REPEATED_CLAUSE` > `S3_PARAPHRASE`；`S3_NOTATION` 只在 `tokenizer-safe` 时参与，排在 `S3_PARAPHRASE` 后；
2. `df_node` 降序；
3. 与 `name`/现有别名的包含关系（含者优先）——让“对勾函数的最小值”排在“最小值”前；
4. 长度升序（同分时更短、更可能被学生输入）；
5. Unicode 码点升序（稳定 tiebreak）。

截断：每节点最多 6 条；其中 `S1` 中“含空格/运算符的陈述句”最多 2 条（防止 6 条全是“当 x1<x2 时都有…”这类句子）。不足 6 条不补。同一 `alias` 从多个来源命中时，合并 evidence，`kind` 取排序最高的一类，不重复占额度。

### 3.6 冻结与复算

- `RULE_VERSION = body-alias-v1`；
- 参数表（分隔符、停用表、cue 表、LaTeX 映射、长度/df/比例、排序键）序列化为**键排序、ASCII、无空白**的 JSON，取 sha256 作为 `rule_hash`；
- 输出头写 `rule_hash` + `pack_content_version`（当前 `ebf477816c318704`）；
- 输入排序：节点按 `(subject, slug)`，材料按 `slug`，section 按出现顺序，候选按最终排序键；无随机、无网络、无模型；
- 复算命令（建议实现）：`PYTHONPATH=tools python -m kb_build.derive_body_aliases --pack <pack> --sidecars <dir> --out <csv>`；同一输入重复运行，输出文件 sha256 必须相同；
- **先冻结、后测量**：任何看过设备/JVM 结果后改参数的行为都算 `body-alias-v2`，不能拿 v1 的名义复用。

### 3.7 伪代码

```text
for node in sorted(nodes):
    mats = [m for m in bound_materials(node) if m.role in (PRIMARY, SUPPORTING)]
    if not mats: continue
    cand = {}                      # alias -> evidence list
    for m in sorted(mats, key=slug):
        for section in split_sections(m.contentMarkdown):
            for clause in split_clauses(section.body):
                c = normalize(clause)          # NFKC + markdown strip
                if term_like(c) and 2 <= len(c) <= 24:
                    cand[c].add((m.slug, section.heading, clause))
            for span in headings_and_bold(section):
                if term_like(span) and not generic_heading(span):
                    cand[span].add((m.slug, section.heading, span))
            for math in math_spans(section.body):
                n = normalize_notation(math)
                if tokenizer_safe(n):
                    cand[n].add((m.slug, section.heading, math))
                else:
                    t = find_paraphrase(section, math, window=50)
                    if t and term_like(t):
                        cand[t].add((m.slug, section.heading, math))
                    else:
                        log_notation_only(node, n, m.slug)
    keep = []
    for alias, ev in cand.items():
        if alias == node.name or alias in node.aliases: continue
        if not shape_ok(alias): continue
        if not answer_bearing(ev): continue
        if not node_related(alias, ev, node): continue
        if not specific(alias, subject): continue       # df_subject < 2% or < 25
        if not tokenizer_safe(alias) and not is_paraphrase(ev): continue
        if not globally_unique(alias, node): continue
        keep.append((alias, evidence_of(ev)))
    keep.sort(key=rank_key)
    emit(keep[:6])
```

---

## 4. 本代理实跑探针（只证明现象，不证明规则效果）

### 4.1 基线读数（实读包 + sidecar）

**实跑命令**（本代理，2026-10-26）：

```text
python - <<'PY'  # 读 moe-2025-four-subjects-v1.json + 25 份 v2 sidecar，统计节点/材料/别名
...
PY
```

**关键输出**：

```text
nodes 3245 nodes_with_material 3245 materials_bound 64973
nodes_with_alias 3152
materials_per_node median 13 max 145
nodes with 1 material: 205   with 2: 193   with >=2: 3040
roles Counter({'PRIMARY': 64973})
types Counter({'CONCEPT_EXPLANATION': 35548, 'METHOD_MODEL': 25475, 'MISCONCEPTION_GUIDE': 3950})
材料数：MATH 14468 / CHEMISTRY 18307 / PHYSICS 18494 / BIOLOGY 13704
```

### 4.2 分词器/NFKC 探针

见 §1.3 与 §2.6 的两张表；脚本是对 `KnowledgeSearchFeatureExtractor` 的逐行翻译（未运行 Kotlin/JVM 生产代码），NFKC 用 Python `unicodedata`。这些读数证明：

- `x+y`/`λ+μ`/`x^2+y^2` 在查询侧无特征；
- `x+y`/`x-y`/`x*y`/`xy` 在 `SearchText.of` 下 compact 成同一个 `xy`；
- NFKC 能统一编码变体，不能统一运算符/希腊字母。

### 4.3 朴素 n-gram 原型：过宽，不可直接采用

做法：对每个节点，把绑定材料正文按标点/换行切 clause，生成 2–8 字 n-gram，保留 `df≥2` 或 `tf≥3`，做“最长包含”过滤，再按 `df/tf/长度` 取 top-6；只用于观察候选形状，不是 `body-alias-v1`。

抽样 4 个节点的 top-6（`python -u body_alias_proto.py`，本代理实跑）：

| 节点 | top-6（节选） |
|---|---|
| MATH 等和线 | `\n`（字面反斜杠+n，LaTeX `\neq` 的 2 字片段）(25)、`直线`(24)、`AB`(19)、`直线 `(19)、`=1`(19)、`点 `(18) |
| MATH 对勾函数 | `函数`(25)、`x+`(25)、`\n`（同上）(23)、`单调`(23)、`y=`(22)、`递增`(19) |
| CHEMISTRY 镁的化学性质 | `Mg`(21)、`CO2`(20)、`MgO`(18)、`OH`(17)、`Mg3N2`(17)、`反应`(16) |
| PHYSICS 胡克定律 | `弹簧`(31)、`形变`(31)、`\n`（同上）(30)、`弹力`(30)、`=k`(29)、`伸长`(28) |

**注意**：`\n` 是 LaTeX `\neq` 被 n-gram 切出的伪候选（原型在形状检查里先删了反斜杠，导致 `n` 通过）。这本身是一条规则约束：**G1 必须在删除反斜杠之前拒绝含 `\` 的候选**，否则 LaTeX 命令碎片会进别名表。

结论：**任意 n-gram + 频次**会把通用词、化学式、公式片段顶到最前；如果直接写进别名表，会把词面索引变成“通用 gram 通胀”。这正是批次16 “全包盲目缩短 → 净 −1”的同类风险。

### 4.4 Term-head 原型：中文词边界仍会错

做法：用一个小型“术语头后缀表”（函数/性质/定理/公式/定律/反应/系数/关系/方法/模型/范围/最值/条件/…），在 clause 里找后缀，向前回退 1–8 字，再做形状门。抽样 top：

| 节点 | top（节选） | 边界错误 |
|---|---|---|
| 等和线 | `直线`、`定值`、`动点`、`共线`、` 在直线`、`行的直线` | 片段/通用词 |
| 对勾函数 | `最值`、`勾函数`、`小值`、`函数`、`最小值`、`近线` | `勾函数` 丢了“对” |
| 镁的化学性质 | `水反应`、`熔点`、`学性质`、`化学性质`、`2反应`、`O2反应` | `学性质` 丢了“化”，`2反应` 是化学式片段 |
| 胡克定律 | `弹力`、`变量`、`形变量`、`度系数`、`劲度系数`、`克定律` | `克定律` 丢了“胡” |

结论：**没有分词器/Dictionary 时，中文词边界不可靠**；term-head 只能当“候选过滤器”，不能当“边界生成器”。`body-alias-v1` 因此不用它。

### 4.5 重复短句原型：质量可用，但仍需门

做法：对每个节点，把材料正文按标点/换行切 clause，只保留 2–24 字、过 `title_usable` 的整句，保留 `df≥2`。抽样：

| 节点 | 重复短句（节选） |
|---|---|
| 等和线 | `则 λ+μ 为定值 k`(2)、`则 λ+μ=k(定值)`(2)、`等和线过 O 点时 k=0`(2)、`直线 AB 及与它平行的直线称为等和线`(2) |
| 对勾函数 | `是奇函数`(2)、`关于原点对称`(2)、`注意取得最值时等号成立的条件`(2) |
| 镁的化学性质 | `熔点较低`(4)、`难溶于水`(2)、`密度、硬度较小`(2)、`与CO2反应2Mg+CO2=2MgO+C`(2) |
| 胡克定律 | `F=kx`(4)、`弹簧发生弹性形变时`(6)、`单位为 N/m`(3)、`x 是弹簧的形变量`(3) |

限量统计（前 300 个节点，均为 MATH）：**163/300 节点至少有 1 条重复短句，共 1,220 条 pre-gate 候选**。

结论：重复短句比任意 n-gram 更接近“学生可能输入的一句话”，但仍然混入 `反之也成立`、`一般地`、`特别地` 这类话语标记，以及 `熔点较低` 这类非名字陈述。**必须加话语标记停用表、首尾虚词门、学科 df 上限，并把陈述句限制在 ≤2 条/节点。**

### 4.6 学科 df 校准：2% 是一条可解释的线

对候选串在**同学科全部材料正文**中的文档频率（本代理实跑）：

| 学科 | 材料数 | 通用词/高 df 例 | 具体词/低 df 例 |
|---|---|---|---|
| MATH | 14,468 | `函数` 30.72%、`直线` 17.24%、`取值范围` 4.51%、`最小值` 4.29%、`渐近线` 1.02% | `系数和` 0.48%、`对勾函数` 0.22%、`等和线` 0.23% |
| CHEMISTRY | 18,307 | `反应` 49.14%、`化学性质` 2.80%、`难溶于水` 1.37%、`镁` 1.29% | `熔点较低` 0.087%、`灭火器` 0.25% |
| PHYSICS | 18,494 | `弹力` 5.80%、`弹簧` 4.86%、`形变量` 1.32%、`劲度系数` 0.75% | `胡克定律` 0.44%、`弹性限度` 0.67% |
| BIOLOGY | 13,704 | `细胞` 44.45%、`蛋白质` 13.43%、`细胞膜` 6.52% | `细胞学说` 0.36% |

**2% 上限**能把 `函数/反应/细胞/直线/弹力/弹簧/最小值/取值范围/细胞膜/蛋白质` 挡掉；`化学性质`（2.80%）也会被挡掉——它的语义已包含在主名“镁的化学性质”里，丢掉不损失；同时保留 `系数和/等和线/对勾函数/胡克定律/细胞学说/劲度系数/形变量/难溶于水`。这是一条**当前包上的经验阈值**，不是普适常数；换包/换学科要重算并重新冻结。

### 4.7 保守 S1 门的 yield（前 300 个 MATH 节点）

在重复短句原型上再加：`name-like` 首尾门、同 section cue 门、学科 df 2%/25 地板门。结果：

- pre-gate 1,118 条 → post-gate **1,008 条**，**153/300 节点**至少 1 条；
- 抽样仍能看到 `一般地`(df_subject=172、未过 2% 因为 MATH 2%=289)、`特别地`(132)、`记作 y=f(x)`、`当 x1<x2 时都有 f(x1)<f(x2)` 等；
- 这说明：**2% 是必要不充分**；还需要话语标记停用表（`一般地/特别地/反之/综上所述/由此可知/显然/同理/类似地`）和“陈述句 ≤2 条/节点”的上限；`body-alias-v1` 已把这两条写进 G1/G 排序。

### 4.8 实跑命令与关键输出（可复核）

| 探针 | 命令（本代理实际执行） | 关键输出 |
|---|---|---|
| 分词器/NFKC | `python -u C:/Users/听云/AppData/Local/Temp/probe_fromquestion.py`；`python - <<'PY' ... unicodedata.normalize('NFKC', ch) ... PY` | `'x+y' -> []`、`'λ+μ' -> []`、`'x^2+y^2' -> []`、`'x2+y2' -> ['x2','y2']`、`'系数和' -> ['系数和','系数','数和']`；NFKC：`＋→+`、`²→2`、`ₓ→x`、`𝑥→x`、`µ→μ`、`Å→Å`，`×/·/−/λ` 不变 |
| 朴素 n-gram | `python -u C:/Users/听云/AppData/Local/Temp/body_alias_proto.py` | 4 个抽样节点 top-6 见 §4.3；有界 yield：`nodes 300 with_candidates 268 total 1574`（说明 n-gram 几乎“人人有份”，进一步证明过宽） |
| Term-head | `python -u C:/Users/听云/AppData/Local/Temp/body_alias_probe2.py` | 抽样 top 见 §4.4；边界错误 `勾函数/克定律/学性质/2反应` |
| 重复短句 | `python -u C:/Users/听云/AppData/Local/Temp/body_alias_probe3.py` | `nodes 300 with_repeated_short_clause 163 candidates 1220` |
| 学科 df | `python -u C:/Users/听云/AppData/Local/Temp/body_alias_probe4.py` | `MATH materials 14468`；`'系数和' df=70 ratio=0.4838%`、`'函数' df=4445 ratio=30.7230%`；`CHEMISTRY '反应' df=8996 ratio=49.1397%`；`PHYSICS '弹力' df=1073 ratio=5.8019%`；`BIOLOGY '细胞' df=6092 ratio=44.4542%` |
| 保守 S1 门 | `python -u C:/Users/听云/AppData/Local/Temp/body_alias_probe5.py` | `sample MATH nodes 300`；`pre_gate_candidates 1118 post_gate_candidates 1008 nodes_with_post_gate 153` |

这些脚本位于系统临时目录，不属于仓库；报告保留命令与输出是为了可复核。**它们都不是 `body-alias-v1` 的生产实现。**

---

## 5. 风险与边界

### 5.1 过宽（把索引补成噪声）

| 风险 | 证据 | 缓解 |
|---|---|---|
| 通用词/学科高频词 | 函数 30.7%、反应 49.1%、细胞 44.5%、直线 17.2%（§4.6） | G5 学科 df <2%；全局互斥 |
| 任意 n-gram 片段 | `直线/函数/Mg/反应/=k/x+` 占据 4/4 抽样 top-6（§4.3） | 不做任意 n-gram；只取整句/标题/加粗/记号 |
| 话语标记/连接词 | `一般地`(172)、`特别地`(132)、`反之也成立`(10) 仍过 2% | 话语标记停用表；首字门 |
| 陈述句过多 | 6 条全是“当…时都有…”会挤掉真正的术语 | 陈述句 ≤2 条/节点；排序 term-like 优先 |
| 例句/数值 | `2反应`、`4Mg+2H+===…` 这类化学式片段 | 纯数字/赋值门；`BAD_PREFIX`；G3 cue 门 |
| 近义重复 | `对勾函数的最小值` / `对勾函数的最值` 同时入选 | 6 条上限；包含关系排序；人工复核 |

### 5.2 过窄（规则跑不出东西）

- 205/3,245 节点只有 1 份材料，`S1` 的 `df≥2` 无法满足；§3.3 的“同一材料 ≥2 section”回退只能部分缓解，这些节点仍主要靠 `S2`（标题/加粗）或 `S3`（记号转述）。
- 材料正文多为长句；2–24 字的整句重复不是常态。前 300 个 MATH 节点 post-gate 1,008 条、153 个节点有候选，**平均每节点约 6.6 条 pre-quality-cap**，经“陈述句 ≤2 + 6 条上限 + 人工复核”后会显著下降；**最终 yield 未测**。
- 历史实测上界：T/C 24 条 → 词面 +4；A1 31 条 → 词面 +13、融合 117/129。正文派生是**低收益补充**，不要用它承诺结构性提升。
- `S3` 要求“记号 + 正文转述”同 section、±50 字、且转述过所有门；很多材料只有记号没有转述，会落到 `notation_only`，词面别名数为 0。

### 5.3 与“题面反推”的界线

**允许**：

- 读节点自己的材料正文；
- 用正文内部的重复、标题/加粗、定义/方法/边界 cue 选候选；
- 用材料自身的来源/绑定作为 provenance。

**禁止**：

- 读 `golden_queries_v2.json`、`*-misses.txt`、`build/agent-*`、用户提问日志、检索失败清单、章节分布；
- 用“这道题期望命中哪个节点”来挑别名；
- 用 129 条金标反复调阈值/停用表/长度上限；
- 把“题干式别名”（Q 类）写进候选。批次16 已明确：Q 类 +24 属“用金标反推索引”，按「评测禁止面向结果」纪律不得采用（`docs/kb-direction-2026-10-22.md:15`、§4）。

**边界情形**：

- 材料正文里可能引用某道题/某个题干；这是允许的来源文本，但候选必须过 G3（同 section 能作答）与 G1（`BAD_PREFIX`：`已知/下列/求/判断/计算/解/答案`）——题干本身不会被选为别名。
- 如果某条候选只在“金标题面”里出现，而不在材料正文里，G2 会直接拒绝；规则不读金标，也不存在“从金标反推”的入口。

**审计建议**：

- 先发布 `rule_hash`，再跑设备/JVM；
- 在**另一套包/另一批材料**上复算一次，检查候选是否稳定；
- 输出表保留 `source_material_slug + evidence_span`，任何别名都能回到原文一行；
- 任何“看结果后调参”都记为新版本，不覆盖 v1 的 hash。

### 5.4 记号归一的风险

| 风险 | 具体表现 | 缓解 |
|---|---|---|
| 过度归一 | NFKC 把 `²→2`、`ₓ→x`、`µ→μ`；再删运算符会把 `x+y/x-y/x*y/xy` 压成同一个 `xy` | 保留运算符；不删运算符；NFKC 只做编码变体 |
| 归一不足 | `−`(U+2212) ≠ `-`(ASCII)；`×/·/*` 不统一；`λ` 不映射 | 闭集运算符映射（显式、可审计）；不做语义等价 |
| 下标语义 | MIaS 刻意区分 `x1` 与 `x`；NFKC 把 `x₁→x1`、`ₓ→x` | v1 接受词面压平；在边界文档里记录；若未来做公式检索，另立规则 |
| 查询解析器保留字 | Lucene Classic QueryParser 把前导 `+` 当 required、`-` 当 prohibit | 别名显示层避免前导 `+/-`；如导出到外部查询框，先转义 |
| 纯记号无效 | `x+y`/`λ+μ` 在 `fromQuestion` 下 0 特征 | G7：必须 `tokenizer-safe`，否则用正文转述替代 |
| 语义等价 | `x+y` vs `y+x`、`x+5` vs `5+x`、`x1+y1` vs `x+y` | 不做；这是数学搜索引擎的结构/语义层，超出别名范围 |

### 5.5 索引截断

- 节点侧 16 片段 / 192 特征，超限保留“末段+首段+均衡中间段”（`KnowledgeSearchFeatureExtractor.kt:36-56,71-84`）；
- 新增 6 条别名会改变片段顺序/数量，可能把原有主名/标题特征挤出 16 片段；
- 批次16 的“全包盲目缩短 >24 字题名 → 净 −1”就是同一机制的负向证据（`docs/kb-batch16-2026-10-22.md:31`）。
- 缓解：每节点 ≤6 条；按“结构/短句/记号转述”排序，不让长陈述挤掉主名；上线前逐节点比较 `fromNode` 特征集合的 symdiff，确认主名/现有高价值别名仍在索引里。

### 5.6 其他

- **全局互斥过严**：两个节点都合理的共享术语会被同时丢弃；例外表是唯一的人工出口。规则应输出 `claim_conflict` 计数，供人复核，而不是静默丢。
- **跨学科歧义**：互斥只在同学科内；同一别名可同时属于 MATH 与 PHYSICS。若未来做跨学科搜索，需要新的消歧策略。
- **包更新漂移**：材料正文改变会导致候选改变；输出必须带 `pack_content_version` + `source_material_slug` + `evidence_span`，否则无法审计。
- **规则复杂度**：S3 的“转述链接”是最容易过拟合的部分（±50 字、copula 模式）；建议先只开 S1+S2，S3 作为独立开关，单独预注册、单独测量。

---

## 6. 来源清单与交叉验证

> 标注：**原始** = 论文/官方规范/官方文档；**综述** = 第三方综述；**实现** = 官方实现文档/代码；**题录** = 仅读到 OpenAlex/ACM 题录与摘要，未读全文。每条结论至少两个独立来源；单一来源在“交叉验证”列标“待验证”。

| # | 来源 | 类型 | 支持什么 | 交叉验证 |
|---|---|---|---|---|
| S1 | Rose, Engel, Cramer, Cowley (2010), *Automatic Keyword Extraction from Individual Documents*, DOI [10.1002/9780470689646.ch1](https://doi.org/10.1002/9780470689646.ch1)；OpenAlex 题录摘要：<https://api.openalex.org/works/https://doi.org/10.1002/9780470689646.ch1> | 原始（题录+摘要） | RAKE 无监督、领域/语言无关；比 TextRank 高效、precision 更高；停用词表生成 | 与 S2（综述 Table 8）、S4（YAKE 论文的相关工作）互证 |
| S2 | Papagiannopoulou & Tsoumakas, *A review of keyphrase extraction*, arXiv:1905.05044，<https://arxiv.org/pdf/1905.05044> | 综述 | 无监督 KPE 三步：候选选择→排序→短语成形；RAKE/TextRank/YAKE 的机制 | 与 S1/S3/S4 原始描述一致 |
| S3 | Mihalcea & Tarau (2004), *TextRank: Bringing Order into Texts*, <https://aclanthology.org/W04-3252.pdf> | 原始 | 分词+POS、名词/形容词过滤、共现窗口 2–10、T=顶点数 1/3、相邻关键词合并 | 与 S2 的 TextRank 描述一致 |
| S4 | Campos et al. (2018), *YAKE! Collection-Independent Automatic Keyword Extractor*, ECIR 2018，PDF：<https://repositorio.inesctec.pt/server/api/core/bitstreams/ef121a01-a0a6-4be8-945d-3324a58fc944/content> | 原始 | 不依赖词典/同义词库/训练；5 个局部特征；1–3 gram；分数越小越相关 | 与 S2、S5（官方 README）互证 |
| S5 | LIAAD/yake 官方 README，<https://github.com/LIAAD/yake> | 实现 | 轻量无监督、无需训练/外部语料/词典；语言/领域无关；分数越低越相关 | 与 S4 一致 |
| S6 | *Keyword Extraction: A Modern Perspective*, SN Computer Science 2023，PDF：<https://link.springer.com/content/pdf/10.1007/s42979-022-01481-7.pdf> | 综述 | Table 8：RAKE=`Anything between stop words` + `deg(w)freq(w)`；YAKE=n-grams；TFIDF=n-grams | 与 S1/S4 互证 |
| S7 | scikit-learn 文档，*Feature extraction*，<https://scikit-learn.org/stable/modules/feature_extraction.html> | 官方文档 | TF-IDF 公式；`smooth_idf`；idf 需要语料统计 | 与 S6 的 TFIDF 行、S8 的原始文献互证 |
| S8 | Salton & Buckley (1988), *Term-weighting approaches in automatic text retrieval*, DOI [10.1016/0306-4573(88)90021-0](https://doi.org/10.1016/0306-4573(88)90021-0) | 原始（题录） | TF-IDF 的经典出处 | 仅题录+引用；具体公式以 S7 为准（**待验证**：未读原文公式） |
| S9 | *Chinese text retrieval without using a dictionary* (1997), DOI [10.1145/258525.258532](https://doi.org/10.1145/258525.258532)，OpenAlex 题录摘要 | 原始（题录+摘要） | 中文无分隔符；不用词典的字符级检索可用 | 与 S10/S11 互证 |
| S10 | *Comparing representations in Chinese information retrieval* (1997), DOI [10.1145/278459.258531](https://doi.org/10.1145/278459.258531)，OpenAlex 题录摘要 | 原始（题录+摘要） | unigram/bigram/short-word 三种表示性能可比 | 与 S9 一致 |
| S11 | Chien (1997), *PAT-tree-based keyword extraction for Chinese information retrieval*, DOI [10.1145/278459.258534](https://doi.org/10.1145/278459.258534)，OpenAlex 题录摘要 | 原始（题录+摘要） | PAT-tree 从中文文档集自动抽关键词，不依赖词典 | 与 S12 的“无词典词发现”思路互证 |
| S12 | Feng et al. (2004), *Accessor Variety Criteria for Chinese Word Extraction*, DOI [10.1162/089120104773633394](https://doi.org/10.1162/089120104773633394)，OpenAlex 题录摘要 | 原始（题录+摘要） | 用左右相邻字符多样性（predecessor/successor）判断词 | 与 S11 互证“字符级/无词典”路线 |
| S13 | Soliman (2023), *An unsupervised linguistic-based model for automatic glossary term extraction from a single PDF textbook*, Education and Information Technologies，PDF：<https://link.springer.com/content/pdf/10.1007/s10639-023-11818-1.pdf> | 原始 | 定义句（is-a / called）是术语抽取单位；不依赖统计阈值；依赖 POS/chunking | 与 S14/S15 的“定义句/能作答”思路互证 |
| S14 | DBpedia LREC 2012, *DBpedia: A Multilingual Cross-domain Knowledge Base*, §2.2，PDF：<http://www.lrec-conf.org/proceedings/lrec2012/pdf/570_Paper.pdf> | 原始 | 别名词表来自 Titles/Redirects/Disambiguation；redirects=同义词/拼写变体/缩写；传递闭包；4.4M 无歧义 / 392k 有歧义；平均 1.22 歧义；2.32 别名/资源 | 与 S15（重定向研究）互证“别名面需去重/歧义统计” |
| S15 | Hill & Shaw (2014), *Consider the Redirect*, DOI [10.1145/2641580.2641616](https://doi.org/10.1145/2641580.2641616)，OpenAlex 题录摘要 | 原始（题录+摘要） | 英文维基重定向占全部条目页过半，长期被忽略 | 与 S14 一致 |
| S16 | Elasticsearch 官方文档，*Synonym token filter*，<https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-synonym-tokenfilter.html> | 官方文档 | 显式映射 `a => b`、等价集合；过滤器顺序；多 token filter 冲突 | 与 S17（Lucene WordDelimiterGraphFilter）互证“别名/同义词是受控映射” |
| S17 | Lucene 9.9 `WordDelimiterGraphFilter` Javadoc，<https://lucene.apache.org/core/9_9_0/analysis/common/org/apache/lucene/analysis/miscellaneous/WordDelimiterGraphFilter.html> | 官方文档 | 默认按非字母数字切；`CATENATE_WORDS`/`PRESERVE_ORIGINAL`/`SPLIT_ON_NUMERICS` | 与 S18/S21 的分词事实互证 |
| S18 | Nogueira et al. (2019), *Document Expansion by Query Prediction*, arXiv:1904.08375，<https://arxiv.org/abs/1904.08375> | 原始（摘要） | 用“文档能回答的查询”扩写文档；模型方法，非规则 | 与 S13 的“能作答”判据互证；实现不采纳 |
| S19 | *Math-Aware Search Engines: Physics Applications and Overview*, arXiv:1609.03457，<https://arxiv.org/pdf/1609.03457> | 综述 | LaTeX/Unicode 多编码；MIaS 区分 x1/x；交换律；presentation vs meaning | 与 S20/S21 互证 |
| S20 | Lupták et al., *WebMIaS on Docker*, arXiv:2106.00411，<https://arxiv.org/pdf/2106.00411> | 原始 | MathML 在索引/查询两侧 canonicalized, ordered, tokenized, unified；Lucene/Solr | 与 S19/S21 一致 |
| S21 | Ohashi et al. (2016), *Efficient Algorithm for Math Formula Semantic Search*, IEICE 2016，PDF：<https://www.jstage.jst.go.jp/article/transinf/E99.D/4/E99.D_2015DAP0023/_pdf> | 原始 | 归一化、符号同义词、canonical ordering、variable/constant unification；MIaS 的 ordering/tokenization/unification | 与 S19/S20 一致 |
| S22 | *Math Information Retrieval using a Text Search Engine*（Waterloo thesis），<https://hdl.handle.net/10012/13329> | 原始（学位论文） | 数学公式线性化为 LaTeX 后可被 Lucene 分词/索引；analysis 链 | 与 S19/S20 一致 |
| S23 | Unicode UAX #15, *Unicode Normalization Forms*，<https://unicode.org/reports/tr15/> | 官方规范 | NFKC 定义；兼容等价；警告“KC/KD 不可盲目用于任意文本” | 与 S24/S25 的分词事实互证 |
| S24 | Unicode UAX #29, *Unicode Text Segmentation*，<https://unicode.org/reports/tr29/> | 官方规范 | 默认词边界；`WB5`/`WB999`；`x+y → x ÷ + ÷ y` | 与 S25/S26 一致 |
| S25 | Lucene 9.9 `StandardTokenizer` Javadoc，<https://lucene.apache.org/core/9_9_0/core/org/apache/lucene/analysis/standard/StandardTokenizer.html> | 官方文档 | 实现 UAX #29 Word Break | 与 S24 一致 |
| S26 | Lucene 9.9 Classic QueryParser 语法，<https://lucene.apache.org/core/9_9_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html> | 官方文档 | `+` required、`-` prohibit；保留字符表 | 与 S23/S24 的“符号需显式处理”互证 |
| S27 | Elasticsearch 官方文档，*Standard analyzer*，<https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-standard-analyzer.html> | 官方文档 | Standard Tokenizer + lowercase；`Brown-Foxes → brown, foxes` | 与 S25 一致 |

**访问性说明**：本环境无法访问 `en.wikipedia.org`、`www.wikidata.org`、`developers.google.com`（连接超时）。因此 Wikipedia/Wikidata/Google 的官方政策页没有直接读到；相关结论改用 S14/S15 的论文摘要与 DBpedia 原文，已在表中标注。若需要“官方政策原文”级别的引用，属于**未验证项**。

---

## 7. 未验证项

1. **规则未实现**：`body-alias-v1` 只有规格和伪代码，没有生产实现；`body_alias_candidates.csv` 不存在。
2. **未跑设备/JVM**：没有测量正文别名对词面/融合 Recall@5、MRR、p50/p95 的影响；所有“+4/+13/117/129”都是引用批次16/19的历史读数，不是本次新测。
3. **最终 yield 未测**：§4.7 的 1,008 条是“重复短句 + 形状 + cue + 2% df”的 pre-quality-cap 读数；加上“陈述句 ≤2、6 条上限、全局互斥、人工复核”后，最终每节点条数未知。
4. **2% 阈值只在当前包校准**：MATH/CHEM/PHYS/BIO 的材料数分别为 14,468/18,307/18,494/13,704；换包、换学科、材料增删后必须重算并重新冻结。
5. **S3 转述链接未实测**：±50 字窗口、copula 模式、同 section 约束都是设计选择；没有在真实材料上统计命中率/误链率。
6. **NFKC 探针是 Python 标准库行为**：与 Kotlin/Android 的 `java.text.Normalizer` 在 Unicode 版本上可能有差异；生产实现应使用项目现有 JVM 归一化路径并加一致性测试（未做）。
7. **分词器探针是逐行翻译**：没有运行 Kotlin/JVM 生产代码；`fromQuestion` 的空特征结论来自代码阅读 + 等价翻译，生产二进制未执行。`DenseTokenizer`/dense 腿对纯记号查询的行为未测。
8. **Wikipedia/Wikidata/Google 官方页面不可达**：重定向/别名政策原文未直接读取；S15 只有摘要，S14 是 DBpedia 论文，不是 Wikipedia 官方政策。
9. **DBpedia/Wikipedia 的歧义统计**是英文维基/DBpedia 的总体数字，不是中文教材 KB 的分布；不能直接外推“2.32 别名/资源”到本项目。
10. **没有人工审计**：没有对候选表做双人标注/一致性检验；规则“能作答”的 cue 表是启发式，可能被套话（每份材料都有的“易错规避”）触发。

---

## 附录 A：探针的可复算片段

### A.1 生产查询特征提取器的等价翻译（仅探针）

```python
import re
NON_SEARCH = re.compile(r'[^\w]', re.UNICODE)

def fragment_features(fragment):
    out = []
    if 2 <= len(fragment) <= 48:
        out.append(fragment)
    for width in range(2, min(3, len(fragment)) + 1):
        for start in range(0, len(fragment) - width + 1):
            out.append(fragment[start:start + width])
    return out

def from_question(text, feature_limit=128):
    fragments = [f for f in NON_SEARCH.split(text.lower()) if f]
    result, seen = [], set()
    streams = [iter(fragment_features(f)) for f in fragments]
    while len(result) < feature_limit and streams:
        it = streams.pop(0)
        try:
            result.append(next(it))
        except StopIteration:
            continue
        streams.append(it)
    out = []
    for x in result:
        if x not in seen:
            seen.add(x); out.append(x)
    return out
```

对应生产代码：`KnowledgeSearchFeatureExtractor.kt:30-34,59-102,117`。注意这是等价翻译，**不是** Kotlin 生产执行。

### A.2 学科 df 统计

```python
# 对每个候选 c、学科 s：
df_subject = sum(1 for m in materials_of(s) if c in m['contentMarkdown'])
ratio = df_subject / len(materials_of(s))
keep = (df_subject < 25) or (ratio <= 0.02)
```

对应 §4.6 的读数；材料数：MATH 14,468、CHEMISTRY 18,307、PHYSICS 18,494、BIOLOGY 13,704。

### A.3 必跑但本次未跑的检查

```text
# 规则实现后（未实现，未运行）：
PYTHONPATH=tools python -m kb_build.derive_body_aliases --pack ... --dry-run
# 输出 sha256 两次一致；再跑：
./gradlew :core:data:testDebugUnitTest --tests '*GoldenRetrievalJvmTest*'
# 设备：
adb shell am instrument -w -e class ...GoldenRetrievalInstrumentedTest ...
```

以上均未执行；本报告不把任何“未跑”写成“通过”。

---

## 附录 B：一句话给实现者

**不要把“正文里出现过的词”直接当别名。** 先把正文切成“能作答的 section”，只收“重复出现的短句 / 作者加粗的标题 / 能被中文转述的记号”，再用“学科 df <2% + 全局互斥 + 分词器友好”三道硬门筛一遍；每节点最多 6 条，先冻结 hash 再测。正文派生是低收益补充，不是 0.90 的结构杠杆。

---

## S3 冻结（批次25）：数学记号 span 别名 `body-alias-v1/S3@1`

> **口径**：本节只把父文档 §3.3 的 **S3 记号 span** 这一半冻结成可复算规则（产 `kind=S3_NOTATION`）；父文档 §3.3 的“记号 ±50 字正文转述”（`S3_PARAPHRASE`）**不在本次冻结范围**，按父文档 §5.6「S3 作为独立开关、单独预注册、单独测量」继续留空。本节不修改上文任何一条，只做追加与收紧。
>
> **读数来源**：本代理 2026-10-29 在 `moe-2025-four-subjects-v1`（`moe-2025-update-manifest.json`：`packId=moe-2025-four-subjects-v1`、`version=21`、`contentVersion=9f9e99ceec47cf8c`；25 份 `moe-2025-teaching-support-v2-*.json`、64,973 份材料、3,245 个节点、绑定 64,973/64,973 全为 `PRIMARY`）上实跑的 10 个探针脚本，命令与关键输出见 §S3.4。**未跑 Gradle/JVM/设备，未生成 `body_alias_candidates.csv`，未改 `alias_exceptions.csv`、未 promote、未重打 dense**；凡“通常/合理”的判断均按未验证处理，列在 §S3.7。

### S3.0 一句话与它消灭的三个失败

记号别名成立的理由不是“记号常见”，而是：学生把 `F=kx`、`ΔG=ΔH-TΔS`、`CuSO_4` 这类串直接敲进搜索框时，节点现有别名（标题派生的中文短语）一个也对不上。S3 只做一件事：**从节点自己绑定材料的正文里，抽出“能进词面索引的记号串”作候选**。

它必须同时防住三个可指名的失败：

| # | 失败 | 具体形状 | 本节对应的门 |
|---|---|---|---|
| F1 | **进不去**：记号的词面形态被分词器吃掉 | `x+y`/`λ+μ`/`x^2+y^2` 在查询侧产 0 特征（父文档 §1.3；生产实现 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt:95-102,117`） | S3-I2 词面友好门：不友好 → 不产别名 |
| F2 | **进得去但不是名字**：几何标签/元素符号/参数下标/实例值通胀 | 实跑（§S3.4）中 `MB`、`MO`、`Ni`、`Pb`、`Sn`、`x_0`、`2p'`、`1aabb`、`_24Cr`、`^14_7N` 都能过“长度+字母+重复”的朴素门 | S3-S1…S3-S5 形状门（长度/首字符/运算符/小数点/通用标签） |
| F3 | **写不进包**：归一不彻底导致例外条目被生产写入链直接拒 | `rebuild_aliases.title_usable` 明确拒 `$` 与 `\`（`tools/kb_build/rebuild_aliases.py:93-94`）、拒逗号与句末标点（`:61,85`）、拒括号不配对（`:91`）、限 24 字（`:57,83`）；例外条目同样要过这把门（`:157`） | S3-N1…S3-N4 + S3-I1：归一后必须无定界符、无残 `\`/`{}` |

### S3.1 输入白名单（与父文档 §3.1 一致，S3 更窄）

**只读**：

- 节点：`subject`、`slug`、`name`、`aliases`（仅用于去重，不参与候选生成）；
- 该节点绑定材料：`slug`、`title`（仅 provenance）、`type`、`bindings[].role`、**`contentMarkdown`**；
- 只取 `role ∈ {PRIMARY, SUPPORTING}`；`PREREQUISITE` 材料的正文属于别的节点，排除（当前包该通道为空）。

**显式不读**：`summaryMarkdown`/`applicabilityMarkdown`/`boundaryMarkdown`；`golden_queries*`、`*-misses.txt`、`build/agent-*`、用户提问日志、检索失败清单、章节分布；任何网络/模型/外部词典。S3 的**唯一**候选来源是 `contentMarkdown` 里的数学定界符 span。

### S3.2 冻结参数表

参数序列化与 hash 见 §S3.6。下表每一行都给出“它消掉的哪一次实测剔除”，便于审计时逐条对账。

#### A. span 形态

| ID | 参数 | 冻结值 | 实测依据 / 消灭的失败 |
|---|---|---|---|
| **S3-P1** | span 形态（闭集，仅这四种） | `$...$`（行内，排除 `$$`）、`$$...$$`（行间）、`\(...\)`、`\[...\]` | 全包实测：`$...$` **106,256**、`$$...$$` **868**、`\(...\)` **0**、`\[...\]` **0**。后两种写进规则是为了闭集完整，不是当前包有数据 |
| **S3-P2** | 纯文本记号串 | **不接受**（正文里没有定界符的 `F=kx`、`Fe3+`、`mol·L` 一律不抽） | 实测：把 `$...$` 掩掉后按“字母数字+运算符”扫连续串，得 **21,556** 个不同串 / **77,875** 次出现，其中最高频是 **`n-` 8,534 次**（Markdown 列表符 `\n- ` 被当成记号）、`mol·L` 351、`v-t` 349、`1/2` 240。不做切分上下文可信的边界，纯文本通道会把列表符、单位、题号一起收进来 |
| **S3-P3** | 跨行 span | 允许（`$$` 常跨行），但归一后仍要过全部形状门 | 抽样的 8 条 `$$` span 全部是 `\dfrac`/`\text{}` 长公式或超 12 字（`\Pi_n^m`、`b=\text{价电子总数}-\text{…}`、`K_x=\dfrac{…}` 等）。**未对 868 条逐条统计过门率**，这是抽样结论，登记在 §S3.7-V11 |

#### B. 归一化

| ID | 参数 | 冻结值 | 实测依据 / 消灭的失败 |
|---|---|---|---|
| **S3-N1** | Unicode 归一 | `NFKC`（`unicodedata.normalize('NFKC')`；生产侧须用同一套 JVM 归一，见 §S3.7-V6） | 父文档 §2.6：NFKC 统一 `＋→+`、`²→2`、`ₓ→x`、`𝑥→x`、`µ→μ`、`Å→Å`，但**不**统一 `×/·/*`、`−/-/–`、`λ/μ` |
| **S3-N2** | 定界符与排版命令 | 去 `$`、`\(`、`\)`、`\[`、`\]`；去 `\left`、`\right`、`\,`、`\;`、`\!`、`\quad`、`\qquad` | 无定界符残留是 S3-I1 的前提（`rebuild_aliases.py:93-94` 直接拒 `$`/`\`） |
| **S3-N3** | LaTeX 命令映射（**闭集，33 条**） | `\lambda→λ`、`\mu→μ`、`\times→×`、`\cdot→·`、`\le/\leq→≤`、`\ge/\geq→≥`、`\neq/\ne→≠`、`\in→∈`、`\sum→∑`、`\sqrt→√`、`\to→→`、`\Rightarrow→⇒`、`\Leftrightarrow→⇔`、`\pm→±`、`\infty→∞`、`\subset→⊂`、`\subseteq→⊆`、`\cup→∪`、`\cap→∩`、`\Delta→Δ`、`\alpha→α`、`\beta→β`、`\theta→θ`、`\pi→π`、`\omega→ω`、`\angle→∠`、`\perp→⊥`、`\parallel→∥`、`\cong→≅`、`\approx→≈`。**映射后仍含 `\`、`{`、`}` 即整条丢弃** | 实测：残 `\` 丢 **31,824** 条、残花括号丢 **2,303** 条（`\dfrac`、`\text{}`、`\mathrm{}` 等闭集外命令）。不做“尽量还原”：`\mathrm{O}_2` 曾归一成 `rm{O}_2` 这种既不是 LaTeX 也不是名字的残片 |
| **S3-N4** | 上下标花括号 | `_{X}`/`^{X}`，`X` 为 1–3 个 `[A-Za-z0-9+-]` → `_X`/`^X`；其它花括号按 S3-N3 丢弃 | 让 `Aat^{100}→Aat^100`、`Fe^{3+}→Fe^3+` 可读；不展开多字符上标（`x^{n+1}` 保留 `{n+1}`，随后被 S3-N3 整条丢弃，属保守取舍） |
| **S3-N5** | 空白 | **去掉全部空白**（空格、换行、制表） | 实测 **16,783** 个 span 含空格（`x + y`、`F = kx`）。生产匹配层只把连续空白压成一个空格（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt:1694-1695`），不删空格，所以 `x + y` 与 `x+y` 在组织面并不等价；别名取无空白形态是唯一稳定写法 |
| **S3-N6** | 大小写 | **不折叠**（保留原材料大小写） | 实测 **23,486** 个 span 不是全小写。理由三条：① 词面索引对全部文本 `lowercase(Locale.ROOT)`（`KnowledgeSearchFeatureExtractor.kt:67`），折叠对召回无增量；② 组织面同样先小写（`RoomMistakeOrganizationRepository.kt:1694-1695`，用在 `:1711` 的别名包含判断）；③ 折叠会抹掉 `Fe`/`fe`、`N`(牛顿)/`n`、`F`/`f` 的区别，让证据 span 不可回读。外部支持：Unicode UAX #31「Case folding」——`UAX31-R5` 要求实现显式声明折叠形式，且“the folding of distinctions (using case mapping or NFKC) **must be delayed until after parsing has located the identifiers**”，即折叠属于匹配层、不是存储层（本节获取，来源见 §S3.6） |

#### C. 形状过滤

| ID | 参数 | 冻结值 | 实测依据 / 消灭的失败 |
|---|---|---|---|
| **S3-S1** | 长度 | 归一后 **2–12** 字符（含） | 实测长度直方（过前序归一、未过本组门）：2 字 3,538 / 3 字 13,239 / 4 字 5,827 / 5 字 4,630 / 6 字 4,340 / 7 字 4,423 / 8 字 2,130 / 9 字 2,009 / 10 字 1,410 / 11 字 1,361 / 12 字 894；**>12 丢 6,361**。抽样看 >12 的多为带 `\text{}` 的长公式或整段等式（“把一道题的解答当名字”）。与父文档 S1 的 2–24 不同：记号串信息密度高，12 字已能容纳 `ΔG=ΔH-TΔS`(11)、`x_1<x_0<x_2`(11)、`a_n+1-a_n>0`(12) |
| **S3-S2** | 至少一个字母 | 必须有 `\p{L}`；纯数字、纯运算符、纯单位一律丢 | 丢 `no_letter` **1,374**（如 `1+2`、`=0`）；纯单位另用闭集正则丢 **6**（`N/m`、`m/s`、`kg·m/s^2` 这类，规则：`^(?:[kMGmunpμ]?(?:m\|g\|s\|A\|K\|mol\|cd\|Hz\|N\|J\|W\|V\|F\|C\|Pa\|Ω\|L)(?:\^\d\|\d)?)(?:/[A-Za-z°]+)?$`）。单位不是概念名，且学科内高频，写进别名只会和主名抢片段 |
| **S3-S3** | 首字符 | 必须是字母 | 丢 **3,934** 条：`_24Cr`、`^14_7N`、`1aabb`、`0≤a≤13`、`2p'`、`1/2`。下标/同位素/系数开头的串是“某个量的实例”，不是名字；这一条同时挡住归一未覆盖的核素写法 |
| **S3-S4** | 必须含运算符 | 至少一个字符 ∈ `= + - − × · ÷ / < > ≤ ≥ ≠ ∈ ∉ ⊂ ⊆ ∪ ∩ → ⇒ ⇔ ^ _ ± ∞ √` | 丢 **6,441** 条：`MB`、`MO`、`AB`、`Ni`、`Pb`、`Sn`、`HD`、`AAbb`。这些是几何线段标签或单个元素符号——词面能进，但没有区分度，且极易被多节点同时 claim。注意集合里**含 `_` 与 `^`**，所以 `CuSO_4`、`a_n`、`3s^23p^3` 不被误杀 |
| **S3-S5** | 小数点 | 含 `.` 即丢 | 丢 **174** 条：`v_1=7.9`、`n(H_2)=0.6`、`n(H_2O)=0.2`。带小数的等式是**算例数值**，不是记号定义 |
| **S3-S5b** | 通用标签 | 正则命中即丢：`^[A-Za-z]$`、`^[A-Za-z][_^][A-Za-z0-9]{1,2}$`、`^[0-9]+[A-Za-z]$` | 丢 **6,567** 条：`x_0`、`a_n`、`F_1`、`v_0`、`2a`、`b_n`。这类“单字母+下标”在任何节点都成立，等于给索引加通用 gram（与父文档 §4.6 的 `函数/反应/细胞` 同型失败，但不靠 df 也能量化拦下） |
| **S3-S6** | CJK | 归一后含 CJK/全角标点即丢 | 丢 **1,337** 条：`\text{价电子总数}-…`、`同化量=呼吸作用散失的能量+…`。数学模式里的中文是正文转述，属于 `S3_PARAPHRASE` 面（本次不冻结）；放进词面别名会把一句话当记号 |

#### D. 与生产链的互操作门

| ID | 参数 | 冻结值 | 实测依据 / 消灭的失败 |
|---|---|---|---|
| **S3-I1** | 形状门镜像 | 必须通过 **生产** `tools/kb_build/rebuild_aliases.title_usable()` 本体（不是重写一份） | 按 S3@1 的门序（结构门在前）实测剔除 **453** 条；换成“结构门关掉、门序不同”的对照（`s3probe5.py`）同一函数剔除 **641** 条，其中含逗号/句末标点 **566**、括号不配对 **75**。数字不同是因为两个口径的门序不同，不是函数行为不同。这一步保证“候选表里的每一行都能原样写进 `alias_exceptions.csv`”，否则会在 `:157` 被拒成 `例外条目形状不合格` |
| **S3-I2** | 词面友好（父文档 G7） | 照抄查询侧算法：`lowercase` → 按 `[^\p{L}\p{N}]+` 切 → 任一片段长度 2–48 即算友好（`KnowledgeSearchFeatureExtractor.kt:30-34,59-70,95-102,117`） | 丢 **10,619** 条：`x+y`、`λ+μ`、`a-b`、`p=q` 这类“单字母 + 运算符 + 单字母”。这正是 F1：写进别名也不会产生任何查询特征。**注意**：不友好 = 不产别名（登记 `notation_only` 供 dense/展示层），**不**改写成去运算符形态（那会把 `x+y`/`x-y` 压成同一个 `xy`，父文档 §1.3 已实测） |

#### E. 证据门、互斥与配额

| ID | 参数 | 冻结值 | 实测依据 / 消灭的失败 |
|---|---|---|---|
| **S3-E1** | 节点内证据 | `df_node ≥ 2`（在 ≥2 份**不同**绑定材料里出现该记号）**或** `tf_node ≥ 3`（该节点材料里原始 span 共出现 ≥3 次） | `df_node` 分布（**结构门关闭的朴素口径**，`s3probe2.py`，截到 5）：1→11,628、2→1,765、3→665、4→345、5+→548；S3@1 口径下的分布未单独统计。`tf≥3` 兜底贡献 **21** 条（占过证据门候选 428 条的 4.9%）。**它救不了薄节点**：205 个单材料节点里 S3 候选数 **0**（§S3.4），所以 `tf≥3` 的实际作用是“同一份材料里反复出现的公式”（如 `CuSO_4` 8 次），不是“单材料回退” |
| **S3-U1** | 同学科互斥（父文档 G6） | ① 不得等于同学科另一节点主名；② 同一 `(subject, alias)` 被 ≥2 个节点 claim → **全部丢弃**。**`claim_scope = pre_evidence`**：claim 在“过形状门、未过证据门（df/tf）”的每节点候选集上统计（取保守口径：只要某节点的材料里出现过该记号，同科其它节点就不能独占它）。口径与 `rebuild_aliases._validate_exceptions`（`:166-179`）一致，因为例外表只能把同一个名字登记给一个节点 | 实测漏斗：过形状门 **8,369** → 过证据门 **2,019** → claim 互斥后 **428**（砍掉 1,591，是全链最大的一刀）→ 主名碰撞 0 → 截断后 **349**。被砍的典型是 `Δv`、`x_0`、`AB` 这类跨节点共享量。**这一道同时替代了父文档 G5**：实测记号候选的学科 df 最高只有 MATH `AB` 0.968%、PHYSICS `Δt` 0.681%、CHEMISTRY `OH^-`/`CO_2` 0.688%、BIOLOGY `CO_2` 0.124%，**全部 < 2%**，2% 上限对 S3 是空门；加一条不删任何东西的机制属于 §12.2 的“多余机制”，故 S3 明确不设学科 df 门 |
| **S3-C1** | 配额 | 每节点 **≤3** 条（父文档是 ≤6，S3 取一半，因 S3 在父文档 §3.5 排序里最低） | 实测：428 条候选 / 220 个节点 → 截到 3 条后 **349** 条（砍 79 条）；**26 个节点**超 3 条（4 条 11 个、≥5 条 15 个），另有 17 个节点正好 3 条、43 个正好 2 条、134 个 1 条 |
| **S3-C2** | 排序键（字典序） | ① `df_node` 降序 ② `tf_node` 降序 ③ 长度升序 ④ Unicode 码点升序 | 无随机项；实测两次运行输出 sha256 相同（§S3.4） |
| **S3-C3** | 配额之外 | 不补空；`alias` 重复命中多来源时合并证据，只占一个额度；`S3` 不挤占父文档 §3.5 的跨类 ≤6 总额 | 索引侧只有 16 片段/192 特征（`KnowledgeSearchFeatureExtractor.kt:10,36-56`；KD-24），多写一条就可能把主名挤出索引 |

### S3.3 算法（可执行规格）

```text
输入：节点集合 N（subject, slug, name, aliases）；每节点的绑定材料集合 M(n)（role ∈ {PRIMARY, SUPPORTING}）
输出：candidates[(n, alias)] = {df_node, tf_node, source_material_slug, evidence_span(raw), rank}

for n in sorted(N, key=(subject, slug)):
    acc = {}                                   # alias -> {materials:set, tf:int, raw_spans:set}
    for m in sorted(M(n), key=slug):
        for raw in find_spans(m.contentMarkdown):      # S3-P1 四种定界符，闭集
            a, why = normalize(raw)                     # S3-N1..N5；失败即计入 drop_reason
            if why: continue
            if not shape_ok(a): continue                # S3-S1..S3-S6
            if title_usable(a): continue                # S3-I1（生产函数本体）
            if not tokenizer_safe(a): log_notation_only(n, a, m.slug); continue   # S3-I2
            rec = acc.setdefault(a, {materials:set(), tf:0, raws:set()})
            rec.materials.add(m.slug); rec.tf += 1; rec.raws.add(raw)
    shape_passing[n] = acc                         # 供全局 claim 统计（claim_scope=pre_evidence）

# 全局阶段（一次遍历全部节点后，按这个顺序；顺序即 §S3-U1 的“pre_evidence”口径）
claim = Counter((n.subject, a) for n in N for a in shape_passing[n])   # 不看 df/tf
for n in N:
    # S3-E1 证据门
    keep = {a: rec for a, rec in shape_passing[n].items()
            if len(rec.materials) >= 2 or rec.tf >= 3}
    # 去重：等于本节点主名/现有别名 → 丢
    keep = {a: rec for a, rec in keep.items() if a != n.name and a not in n.aliases}
    # S3-U1：同学科一名字只归一个节点
    keep = {a: rec for a, rec in keep.items() if claim[(n.subject, a)] == 1}
    keep = {a: rec for a, rec in keep.items() if a not in names_of_other_nodes(n.subject)}
    ordered = sort(keep, key=(-df_node, -tf_node, len(alias), codepoint(alias)))   # S3-C2
    emit_candidates(n, ordered[:3])                # S3-C1

normalize(raw):
    t = NFKC(raw)
    t = strip_delims(t)                       # $ \( \) \[ \]
    t = strip_layout(t)                       # \left \right \, \; \! \quad \qquad
    t = apply_closed_latex_map(t)             # S3-N3 的 33 条
    t = expand_subsup_1_3(t)                  # S3-N4
    t = remove_all_whitespace(t)              # S3-N5
    if "\\" in t or "{" in t or "}" in t: return t, "residual_tex"
    if has_cjk(t): return t, "cjk"
    if not (2 <= len(t) <= 12): return t, "length"
    if not any(isalpha(ch) for ch in t): return t, "no_letter"
    return t, None
```

**证据锚定（G2 的 S3 变体）**：S3 的别名**不是** `contentMarkdown` 的逐字子串（`\le→≤`、`_{2}→_2` 都会改变字面）。所以 S3 的逐字门写成两条、都要成立：① `evidence_span`（原始 span，含定界符）必须逐字出现在 `contentMarkdown`；② `alias == normalize(evidence_span)` 必须可复算。§S3.4 的 6 项断言里，这两条对 349 行全部成立。

**候选表列**：沿用父文档 §3.1 的 13 列，`kind=S3_NOTATION`；追加 `tf_node`、`raw_span` 两列（`evidence_span` 存原始 span）。写入包仍走批次22 的既有链：`alias_exceptions.csv`（`:157` 门）→ `rebuild_aliases --write` → `promote` → dense 重打；S3 **不直接改** `alias_map.csv`。

### S3.4 实跑读数（本代理 2026-10-29，可复算）

**命令**（脚本在系统临时目录，不入仓库；每个脚本都只读包与 sidecar）：

```text
python -u C:/Users/听云/AppData/Local/Temp/s3probe1.py   # span 形态计数
python -u C:/Users/听云/AppData/Local/Temp/s3probe2.py   # 归一/长度/大小写/df 分布
python -u C:/Users/听云/AppData/Local/Temp/s3probe3.py   # 学科 df 校准（证明 2% 门对 S3 是空门）
python -u C:/Users/听云/AppData/Local/Temp/s3probe4.py   # 纯文本记号串（S3-P2 的反证）
python -u C:/Users/听云/AppData/Local/Temp/s3probe5.py   # 生产 title_usable 直调 + 641 拆分口径
python -u C:/Users/听云/AppData/Local/Temp/s3probe6.py   # 结构门开/关对比
python -u C:/Users/听云/AppData/Local/Temp/s3probe7.py   # 全链 + 确定性 sha
python -u C:/Users/听云/AppData/Local/Temp/s3probe8.py   # 6 项断言 + 证据回读
python -u C:/Users/听云/AppData/Local/Temp/s3probe9.py   # 标题/section 覆盖（用于 G3 裁定）
python -u C:/Users/听云/AppData/Local/Temp/s3funnel.py   # 漏斗逐级归属（8,369→2,019→428→349）
```

**A. 规模与形态**

```text
nodes 3245  sidecars 25  materials 64973  roles {'PRIMARY': 64973}
materials_per_node median 13 max 145  single_material_nodes 205
span_counts {'d1': 106256, 'd2': 868}          # d1=$...$, d2=$$...$$；\( 与 \[ 各 0
spans_total 107124  distinct_normalized 10144
space_spans 16783  case_change_spans 23486
```

**B. 逐门剔除（全包 107,124 个 span，最终 15,136 条过门 → 428 条候选 → 349 条写入）**

| 门 | 剔除条数 | 门 | 剔除条数 |
|---|---|---|---|
| 残 `\`（S3-N3） | 31,824 | 首字符非字母（S3-S3） | 3,934 |
| 长度 <2（S3-S1） | 20,595 | 残花括号（S3-N3） | 2,303 |
| 词面不友好（S3-I2） | 10,619 | 无字母（S3-S2） | 1,374 |
| 通用标签（S3-S5b） | 6,567 | 含 CJK（S3-S6） | 1,337 |
| 无运算符（S3-S4） | 6,441 | `title_usable`（S3-I1） | 453 |
| 长度 >12（S3-S1） | 6,361 | 含小数点（S3-S5） | 174 |
| 纯单位（S3-S2） | 6 | **过门** | **15,136** |

合计校验：31,824+20,595+10,619+6,567+6,441+6,361+3,934+2,303+1,374+1,337+453+174+6+15,136 = **107,124**，与 span 总数逐条对齐（无遗漏、无双计）。“过门 15,136”是 **node-agnostic 的 span 条数**；同一个归一别名在多个节点重复出现时会各算一次，节点内还会按 `(node, alias)` 去重，所以它远大于 §S3.4-C 的 8,369 个 `(node, alias)` 对。

**C. 产出与覆盖**

```text
after_shape_gates 8369   (node, alias) 对
after_E1(df>=2 or tf>=3) 2019
after_claim(pre_evidence) 428
after_dedup(name/aliases) 428   after_name_collision 428
pre_cap_aliases 428   nodes 220   post_cap_aliases 349   nodes_truncated 26
per_node(截到 5) {1:134, 2:43, 3:17, 4:11, 5+:15}
nodes_by_subject {'math': 80, 'physics': 92, 'chemistry': 39, 'biology': 9}
final_nodes_by_material_count {3:3, 4:2, 5+:215}       # 全部 ≥3 份材料
tf_fallback_aliases(df=1,tf>=3) 21   单材料节点候选数 0
```

**两道门在这份包上是空门（如实登记）**：`name/aliases` 去重从 2,019→2,019（删 0 条）、主名碰撞从 428→428（删 0 条）。它们没有产生任何剔除，但必须保留——`rebuild_aliases` 在 `:162-171` 会对例外条目做同样的判定，缺了它们，候选表会出现“写进去必然被拒”的行；保留是互操作要求，不是增值机制。

覆盖率 220/3,245 = **6.8%**（math 80/834=9.6%、physics 92/889=10.3%、chemistry 39/1,011=3.9%、biology 9/511=1.8%）。这是一个**保守**的产出面：S3 不负责覆盖全库，只给“记号本身就是检索入口”的节点补一条通道。

**D. 抽样（结构门开启后的真实候选，格式：别名(df/tf)）**

| 学科 | 节点 | 前 3 条 |
|---|---|---|
| MATH | 极值点偏移问题 | `x_1<x_0<x_2`(13/13)、`x_1+x_2>2`(4/9)、`f(2x_0-x)`(4/4) |
| MATH | 导数构造法解函数不等式 | `g(x)=xf(x)`(2/2)、`h(x)=xf(x)`(2/2)、`F(x)=f(x)-kx`(2/2) |
| MATH | 数列的单调性与最值问题 | `a_n+1-a_n>0`(3/4)、`a_n≤a_n+1`(3/3)、`a_n≤a_n-1`(3/3) |
| PHYSICS | 弹性碰撞 | `v_1'=0`(10/10)、`v_2'=v_1`(10/10)、`v_1'<0`(7/7) |
| PHYSICS | 板块模型 | `a_1=μ_1g`(4/5)、`μ_2`(3/3)、`μ_1<μ_2`(3/3) |
| CHEMISTRY | 自由能与化学反应的方向 | `ΔS>0`(5/10)、`ΔG<0`(4/7)、`ΔG=ΔH-TΔS`(4/5) |
| CHEMISTRY | 气体摩尔体积 | `n=V/V_m`(4/4)、`V_m=V/n`(2/2)、`L·mol^-1`(1/4) |
| CHEMISTRY | 配位化合物 | `Ni(CO)_4`(3/4)、`Fe(CO)_5`(2/3) |
| BIOLOGY | 实验-检测生物组织中的糖类-脂肪和蛋白质 | `CuSO_4`(5/8)、`Cu(OH)_2`(5/8) |
| BIOLOGY | 自由组合定律中的特殊分离比问题分析 | `AAbb×aaBB`(3/5)、`AABB×aabb`(3/3) |

**E. 结构门的增量证据（开/关对比，同一输入）**

| 口径 | 节点数 | 候选数（≤3 后） | 典型噪声 |
|---|---|---|---|
| 除 S3-S3/S3-S4/S3-S5 之外全开（P/N/I/E/U/C 全按本节） | 267 | 467 | `MB`、`MO`、`Ni`、`Pb`、`Sn`、`HD`、`3p_0`、`2p'`、`1aabb`、`_24Cr`、`^14_7N`、`0≤a≤13` |
| 再加 S3-S3/S3-S4/S3-S5（= 本节冻结口径） | 220 | 349 | 上述全部消失；仍留 `f(1)=2f(1)`、`b=v_0`、`SA=2`、`FE=2`、`C_2^2=1`、`Aat^100`、`AA_2` |

代价是 −47 个节点、−118 条候选（−25%）。这也是本节唯一一次“用同一份数据比较两个口径”，两个口径都在看到任何检索读数之前跑出，未用金标。

**F. 6 项断言（对 349 行逐行检查，全部 0 违反）与大表 sha**

```text
rows 349
violations: dollar_or_backslash 0  punct 0  len 0  tokenizer 0  verbatim 0  recompute 0
sha256 ff25f6727bedee297ab4a2875ad28967e2efaa7337482c6aa4b0a23289782910   # 8 列（subject|slug|alias|df|tf|rank|src|raw），两次运行相同
```

6 项断言 = ① 别名不含 `$`/`\`；② 不含 `。！？；;，,`；③ 长度 2–12；④ 词面友好；⑤ 原始 span 在来源材料 `contentMarkdown` 中逐字存在；⑥ `normalize(raw_span) == alias`。

> 第一次跑 `s3probe8.py` 有 40 行报 `verbatim`，原因是探针把“含任意 raw 的第一份材料”当来源、却用 `raws[0]` 去核对（探针 bug，不是规则违反）；改成“取含 `raws[0]` 的材料”后归零。这一偏差保留在此，供复核者识别。

### S3.5 与“题干反推”的界线（S3 专属声明）

**允许（S3 的全部输入）**

- 节点自己的 `contentMarkdown` 里的定界符 span；
- 记号在节点材料内的重复（`df_node`）与频次（`tf_node`）、材料 slug 作 provenance；
- 用主名/现有别名做**排除**（去重），不用它们做候选生成。

**禁止（任一条出现即视为该条候选作废、整轮复算作废）**

- 读 `golden_queries_v2.json`、`*-misses.txt`、`build/agent-*`、用户提问日志、检索失败清单、章节分布；
- 用“这道题期望命中哪个节点”挑记号，或按某条 MISS 给节点补记号（父文档 §5.3；批次16 Q 类 +24 已按此纪律判为不可采用）；
- 用金标反复调 2–12 长度、`tf≥3`、运算符闭集、`rule_hash` 覆盖的任何参数；
- 在**看过设备/JVM 检索结果之后**改上述任一参数仍沿用 `S3@1` 的名字（父文档 §3.6「先冻结、后测量」）。

**S3 特有的三条边界情形**

1. **CJK 门已挡住“把题干文字当别名”**：S3-S6 使任何含中文的 span 出局，因此 S3 在结构上不可能产出中文题干别名（父文档 G3 要防的那一类）。**正因如此，S3@1 不设 G3“同 section 含 cue”门**：实测 62,536/64,973（96.3%）材料没有任何 Markdown 标题（`materials_with_headings 2437`、`heading_counts {0: 62536, 1: 2437}`），`section` 退化成整份材料，cue 门会变成“材料里有没有 `方法/注意/因此`”这种 61% 材料都过的空门（`sections_with_cue 41352`）。这是与父文档 G3 的**显式偏离**，登记在 §S3.7-V4。
2. **例题里的公式可以进来，但不是因为“题”**：`f(1)=2f(1)`、`SA=2` 这类确实活到了候选表。它们靠的是“在本节点材料里重复”，不是任何题干线索；候选表不自动进包，由例外表人工定稿层复核（父文档 §3.1 的链）。
3. **不做“符号同义”**：`x+y` 与 `y+x`、`x+5` 与 `5+x`、`x_1` 与 `x` 都不互认（父文档 §5.4）；S3 只做编码变体与闭集命令映射。

**审计建议**：先发布 `rule_hash`（§S3.6），再在任何设备臂上测；候选表保留 `raw_span + source_material_slug`，任一条能回到原文一行；换包/换学科必须在**未看过结果**的前提下复算一次。

### S3.6 冻结与复算

- **版本**：`RULE_VERSION = body-alias-v1/S3@1`；任何参数变化即 `@2`，不得复用本节的 hash 与读数。
- **排序/确定性**：节点按 `(subject, slug)`、材料按 `slug`、span 按出现顺序、候选按 S3-C2；无随机、无网络、无模型、无时间戳。
- **rule_hash**：把下表参数序列化后取 sha256。

```python
import json, hashlib
blob = json.dumps(params, sort_keys=True, separators=(',', ':'), ensure_ascii=True)
rule_hash = hashlib.sha256(blob.encode('utf-8')).hexdigest()
```

本节的参数块（`params`，即上表 A–E 的可机读形态；**这就是被 hash 的原文**）：

```json
{"bare_text_notation":false,"cap_per_node":3,"claim_scope":"pre_evidence","df_min_materials":2,"df_or_tf":true,"drop_cmds":["\\left","\\right","\\,","\\;","\\!","\\quad","\\qquad"],"generic_label_patterns":["^[A-Za-z]$","^[A-Za-z][_^][A-Za-z0-9]{1,2}$","^[0-9]+[A-Za-z]$"],"latex_map":{"\\Delta":"\u0394","\\Leftrightarrow":"\u21d4","\\Rightarrow":"\u21d2","\\alpha":"\u03b1","\\angle":"\u2220","\\approx":"\u2248","\\beta":"\u03b2","\\cap":"\u2229","\\cdot":"\u00b7","\\cong":"\u2245","\\cup":"\u222a","\\ge":"\u2265","\\geq":"\u2265","\\in":"\u2208","\\infty":"\u221e","\\lambda":"\u03bb","\\le":"\u2264","\\leq":"\u2264","\\mu":"\u03bc","\\ne":"\u2260","\\neq":"\u2260","\\omega":"\u03c9","\\parallel":"\u2225","\\perp":"\u22a5","\\pi":"\u03c0","\\pm":"\u00b1","\\sqrt":"\u221a","\\subset":"\u2282","\\subseteq":"\u2286","\\sum":"\u2211","\\theta":"\u03b8","\\times":"\u00d7","\\to":"\u2192"},"len_max":12,"len_min":2,"lowercase":false,"nfkc":true,"operator_chars":"=+-\u2212\u00d7\u00b7\u00f7/<>\u2264\u2265\u2260\u2208\u2209\u2282\u2286\u222a\u2229\u2192\u21d2\u21d4^_\u00b1\u221e\u221a","order":["df_node_desc","tf_node_desc","len_asc","codepoint_asc"],"reject_cjk":true,"reject_decimal_point":true,"reject_residual_backslash":true,"reject_residual_brace":true,"require_letter":true,"require_letter_initial":true,"require_operator":true,"reuse_title_usable":true,"span_forms":["dollar_inline","dollar_display","paren_inline","bracket_display"],"strip_whitespace":true,"subject_unique":true,"subsup_expand":{"pattern":"([_^])\\{([A-Za-z0-9+\\-]{1,3})\\}","replacement":"\\1\\2"},"tf_min_occurrences":3,"tokenizer_safe":"any(lowercase(s).split(non_letter_or_digit) fragment with 2<=len<=48)","unit_pattern":"^(?:[kMGmunp\u03bc]?(?:m|g|s|A|K|mol|cd|Hz|N|J|W|V|F|C|Pa|\u03a9|L)(?:\\^\\d|\\d)?)(?:/[A-Za-z\u00b0]+)?$","version":"body-alias-v1/S3@1"}
```

**`rule_hash = 938e2026c93f0aa6f60e8bb01c6d2079decf1e51bdb0c56b5f97bb462617ae10`**（本代理 2026-10-29 实算）。复算方式：把上面这行 JSON 原样喂给 `sha256` 即可；任何键/值/空格改动都会改变结果。

- **复算命令（建议实现，未实现）**：`PYTHONPATH=tools python -m kb_build.derive_body_aliases --pack <pack> --sidecars <dir> --kind S3 --out <csv>`；同一输入两次运行，输出文件 sha256 必须相同（本节的等价探针已做到两次同 sha）。
- **输出头**：`rule_version=body-alias-v1/S3@1`、`rule_hash=938e2026…`、`pack_content_version=9f9e99ceec47cf8c`。
- **本节新增的来源（1 条）**：Unicode UAX #31《Unicode Identifier and Pattern Syntax》（<https://www.unicode.org/reports/tr31/>，本代理 2026-10-29 获取）——`UAX31-R5` 要求实现显式声明 simple/full case folding，且“the folding of distinctions (using case mapping or NFKC) must be delayed until after parsing has located the identifiers”；该条支撑 S3-N6「折叠属于匹配层、存储层保留原形」。其余外部依据沿用父文档 §2.6/§6（UAX #15、UAX #29、S19–S22 的数学检索归一）。**InChI/IUPAC 关于元素符号大小写敏感（`Co` vs `CO`）的原文未取到**（inchi-trust.org 连接超时、IUPAC Red Book PDF 返回 403），该论点在本节只由本地实测支撑（23,486 个非全小写 span），按未验证处理。

### S3.7 风险与未验证项

| # | 项 | 状态 / 证据 | 影响 |
|---|---|---|---|
| V1 | **规则未实现** | 只有规格 + 等价探针；`body_alias_candidates.csv` 不存在，`tools/kb_build` 下无 `derive_body_aliases` | 后续批次必须先落地实现，再谈测量 |
| V2 | **未接设备/JVM** | 零 Gradle、零 `adb`、零 `am instrument`；349 条对词面/融合 Recall@5、MRR、p50/p95 的影响**完全未知** | 不得把本节的产出写成“会涨分”；父文档 §1.4 的历史上界（T/C +4、A1 +13）仍是唯一量级参考 |
| V3 | **候选仍有噪声** | 仍留 `f(1)=2f(1)`、`b=v_0`、`SA=2`、`FE=2`、`C_2^2=1`、`Aat^100`、`AA_2` 这类“实例/代换”串（§S3.4-E） | 每节点 3 条上限 + 例外表人工定稿 + 互斥门是唯一缓冲；不得自动批量写入 |
| V4 | **偏离父文档 G3** | 96.3% 材料无标题、cue 门实测为空门（§S3.5-1） | 若未来材料补标题，应重开 G3 并重算 `@2`；本偏离必须在批次记录里显式登记 |
| V5 | **薄节点无覆盖** | 205 个单材料节点 S3 候选 **0**；`tf≥3` 只在多材料节点生效（21 条） | S3 不能当“补薄料节点”的手段；父文档 §5.2 的判断未被推翻 |
| V6 | **NFKC 实现差异** | 探针用 Python `unicodedata`（Unicode 版本随解释器）；生产是 JVM `java.text.Normalizer` | 落地时必须用 JVM 路径并加一致性用例；否则同一包在两套归一化下可产出不同别名 |
| V7 | **下标/上标语义丢失** | ① S3-N4 只展开 1–3 字符的 `_{}`/`^{}`，`x^{n+1}` 因此保留花括号、再被 S3-N3 整条丢弃；② 12 字上限使较长的表达式类记号（如 `f(x_1)<f(x_2)`，13 字）系统性缺席；③ `Fe^{3+}` 保留为 `Fe^3+`，但下标/上标语义（MIaS 刻意区分的 `x1` vs `x`）在词面层已被压平 | 保守取舍，不是 bug：S3 只服务“短记号就是检索入口”的节点；表达式检索属另一条规则 |
| V8 | **组织面空白敏感** | 存储 `F=kx`，学生输入 `F = kx` 在 `RoomMistakeOrganizationRepository.kt:1694-1695` 归一后仍不等（只压空格、不删） | 属于既有消费面的行为，S3 不改；若要提升组织面命中，需另立消费者侧改造 |
| V9 | **学科 df 门被移除** | 实测最大 0.968% < 2%（§S3.2-E/S3-U1） | 若换包后记号变得跨节点高频，互斥门（S3-U1）先兜住；届时应重算并考虑恢复 |
| V10 | **未做人工审计** | 349 条没有双人标注/一致性检验；`title_usable` 在本节是**被调用**（`s3probe5/6/7/8` 直接 import 生产函数），但 `rebuild_aliases --write` 未跑 | 候选表质量只到“机械门全过”，未到“语义正确” |
| V11 | **`$$` 只做了抽样** | 868 条 `$$...$$` 只抽读 8 条（全是 `\dfrac`/`\text{}` 长公式），**没有逐条统计过门率** | §S3-P3 的结论是抽样级的；若要断言“`$$` 通道几乎无产出”，必须先跑全量统计 |
