# KB R12：标题短式 → 别名的质量准则、外部依据与完整臂预注册要素（2026-11-03）

> **性质**：研究记录 + 可落成代码的判据建议。本文不执行任何内容写盘，不改包、不改别名表、不跑 Gradle/设备。
> **写作时点**：2026-11-03T23:17:49+0800（`date` 实跑）；HEAD `0fef943641f53560b92ce2afec4da22bf52f5e86`（本会话 `git rev-parse HEAD`）。
> **上游**：`docs/research/2026-11-02-kb-b33-b2-title-only-prereg.md`（153 条短式 / 20 节点 / +123 别名；下称 **B2**）；`docs/research/2026-10-26-kb-content-derived-alias-rules.md`（下称 **R-alias**）；`docs/kb-content-conformance-spec.md`（节点命名规范）。
> **本步唯一写盘**：本文件。本文所有本仓读数均为本会话 `python -I` 只读实算，未写任何 CSV/包/向量。

---

## 结论先行

1. **“标题短式自动成为别名”不是一个中性的文案动作**：别名同时进入词面腿（`fromNode` 的 2–3 字 gram，节点侧上限 16 片段 / 192 特征）与 dense 腿（`canonicalName + aliases` 每项一行向量）。任何标题改写，只要通过 `title_usable`，就同时改变两条检索腿和一个向量资产；必须按“索引变更”而非“标题润色”来治理。
2. **153 条 APPLIED 的净效果可独立复现为 20 节点 / +123 条唯一别名**（本会话 `rebuild(pre/post)` 实算）：153 行中 128 行产出了新行，其中 5 行在同节点内重复，集合去重后净增 123 条；另有 11 条本来已是别名、10 条被“题面/指令开头”门拒、2 条含逗号被拒、2 条等于主名。
3. **现有过滤器不足以处理短式标题流**：123 条新别名中 **37 条（30.1%）** 含保守标点白名单外的字符（`|`、`、`、`「」`、`≠`、`∅`、`∪`、`⊆`、`√`、`^`、`{}`、全角 `＝` 等）；已发布别名表本身还有 6 条含换行、76 条 ≤3 字、23 条含 LaTeX 字符。把标题当别名，必须先补“标点/公式/控制字符”门，否则会把公式片段和残片写进两条腿。
4. **外部证据不支持“自动同义词扩展必然提升”**：Voorhees（SIGIR 1994）在 TREC 上的结论是——查询本身较完整时，同义词扩展“little difference”；查询不完整时，**人工挑选**的概念可显著改善；但“自动生成同义词集”会**降低**检索性能。Elasticsearch 官方也明确：索引期同义词会改变词频/IDF 统计，且加同义词建议先做 A/B 或 ranking evaluation 再上线。因此自动标题派生必须预注册、可回滚、有负向门。
5. **标题规范有公开依据，但“24 字”没有公开依据**：百度官方给的是“核心词 + 修饰词（≤3 个）”、简明、唯一、不堆砌；Google 给的是 descriptive/concise、唯一、无硬上限但会截断；GB/T 7713.2–2022 把学术论文题名从“一般不宜超过 20 字”改为“**一般不宜超过 25 字**”（二手转述，待验证）。24 是本仓 `NAME_MAX` 的工程阈值，恰好落在 153 条短式长度的 p95=23 之上；可保留 24，或对齐 25，但都不应单靠长度判质量。
6. **建议把“标题改写”与“别名准入”拆成两个决定**：标题可以为了可读性改写而不进入别名；别名准入应由独立门（形状 + 冲突 + 证据）决定。当前 `rebuild_aliases` 把“绑定材料标题”直接当候选，导致两个决定被耦合；这是 R12 最该先修的接口问题。最小实现：在标题改写 CSV 增加 `alias_admission`（`admit`/`deny`）列，`rebuild_aliases` 只把 `admit` 行当候选；它消灭的具体失败是“标题为了可读性必须改，但改了就必须改索引表面”。

---

## 1. 事实

### 1.1 本仓事实（代码/数据实读）

#### 1.1.1 别名被谁消费（错一个不是文案问题）

| 消费点 | 代码 | 用法 | 后果 |
|---|---|---|---|
| 词面索引 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureExtractor.kt:44-57` | `canonicalName + aliases.sorted() + boundaryMarkdown` 抽成 2–3 字 gram 与整段词；节点侧上限 16 片段 / 192 特征 | 错误别名把错误节点顶上来；超限时按“末段 + 首段 + 均衡中间段”截断，别名可能挤掉主名/boundary 的片段 |
| 生产索引写入 | `.../KnowledgeSearchIndexBuilder.kt:210` | `toSearchFeatures()` 逐节点调用同一抽取器 | 同一份抽取器，无第二套分词 |
| 模型候选菜单 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/KnowledgeContextRetriever.kt:103-117` | 别名重合权重 7、精确短语加 70；主名权重 8、精确短语加 80 | 别名直接影响“模型可以绑定哪些节点”的菜单；70 分的精确短语加成使“短别名 = 查询”具有高杠杆 |
| 错题组织/讲题 | `core/data/.../RoomMistakeOrganizationRepository.kt`、`core/data/.../OpenAiProblemOrganizationProtocol.kt` | 用 `aliases + canonicalName` 匹配学生文字/发给模型挑点 | 别名指向别的概念 = 把组织与讲题引到错误节点 |
| dense 向量集 | `tools/dense_build/dense_asset.py:35,324-355` | 向量集 = 当前包全部 ATOMIC `canonicalName` + 全部 `alias`；每项一行，`kind ∈ {canonical, alias}` | 新增 123 别名 = 新增 123 行 dense 向量；`vectorCount`、`.vec` sha、`packSha256` 全部变化 |

**关键结构事实**：包契约要求 `aliases[0] == canonicalName`（`tools/kb_build/check_pack_contract.py:89-92`），所以“别名”实际是“主名 + 额外检索面”的集合。`validateAliases` 只做逐条 ≤200 字、trim、大小写不敏感唯一；**不设条数上限**（`core/database/.../KnowledgeBaseImportContract.kt:229-256` 的 KDoc 明确说旧的 `<=32` 无依据，真正预算在 16 片段 / 192 特征）。

#### 1.1.2 现有别名生成规则（三条）

`tools/kb_build/rebuild_aliases.py`：

1. 主名永远保留；
2. 别名必须是**该节点自己绑定材料的标题**，且过 `title_usable()`：
   - `NAME_MAX = 24`；空标题；含 `。！？；;，,`；以 `例/典例/如/解/答案/解析/已知/下列/判断/计算/求` 开头；以 `的/和/与/或/及/供/为/是` 结尾；括号不配对；含 `$` 或 `\`；过 `gate._is_bad_name()`（题干/答案标记、只有标题冒号、通用名词、高考题干残句、公式被剥离、>24 字整句）。
3. **同学科全局互斥**：一个名字被 ≥2 个节点 claim 时全部丢弃；人工例外表 `tables/alias_exceptions.csv` 优先，但例外仍过形状门与主名互斥/同学科唯一。

**本会话 dry-run 的丢弃分布**（`python -I` 调 `rebuild_aliases.main()`，只读）：

| 丢弃原因 | 条数 | 占 22,471 |
|---|---:|---:|
| 超长（>24 字） | 20,612 | 91.7% |
| 以题面/指令开头 | 818 | 3.6% |
| 被 2 个节点同时 claim（全局互斥） | 574 | 2.6% |
| 以虚词结尾 | 186 | 0.8% |
| 含逗号/句末标点 | 129 | 0.6% |
| 坏名检测 | 44 | 0.2% |
| 括号不配对 | 31 | 0.1% |
| 含 LaTeX 定界符 | 30 | 0.1% |

这组读数说明：标题→别名本来就是**高度选择性**的通道（37,567 保留 vs 22,471 丢弃）；短式标题之所以能新增别名，是因为它们从“超长/带出处括注”变成了“过形状门”的短串，不是规则变宽了。

#### 1.1.3 153 条短式的净效果（本会话独立复现）

输入：`build/agent-batch32b/rewrite/title-shortforms-new.csv`（205 行；`APPLIED` **153** / `NOOP` **49** / `REVERTED` **3**）。

复现方法：`bound_titles()` 取 pre；对 153 条 APPLIED，用材料 slug→绑定节点 `(subject, slug)`，从该节点标题集合中移除 `old_title`、加入 `new_title`，得 post；同一 pack、同一 `alias_exceptions.csv` 跑 `rebuild()`，逐节点取差集。

| 项 | 值 |
|---|---:|
| pre 候选总数 | **37,567**（B2 为 37,566，差 1 属快照漂移） |
| post 候选总数 | **37,690**（B2 为 37,689，同差 1） |
| 净新增 / 删除 | **+123 / −0** |
| 受影响节点 | **20**（MATH 9 / CHEMISTRY 7 / PHYSICS 3 / BIOLOGY 1） |
| 每节点新增 | 1–25（最大为 PHYSICS「共点力的动态平衡」25） |

**153 行的逐行归属**（本会话对 pre/post 集合精确比对）：

| 归属 | 行数 | 说明 |
|---|---:|---|
| 真正新增唯一别名 | **123** | 128 个新增行经同节点集合去重后的唯一数 |
| 新增行中的同节点重复 | 5 | 3 组重复标题；行级 128 = 唯一 123 + 重复 5 |
| 本来已是该节点别名 | 11 | 短式后与既有别名重合，零新增表面 |
| 被“题面/指令开头”门拒 | 10 | 以 `求/判断/解/解决` 开头，如「求数列最大项的方法」「判断数列单调性的三点提醒」 |
| 含逗号/句末标点被拒 | 2 | 「验证机械能守恒定律：不必测质量，阻力是主要误差源」等 |
| 等于主名（冗余） | 2 | 「函数图象的翻折变换」「铝及其化合物的易错点」 |
| **合计** | **153** | 123 + 5 + 11 + 10 + 2 + 2 |

**123 条新别名的形状读数**：

| 项 | 值 |
|---|---:|
| 长度 min / median / max | 8 / 17 / 24 |
| 长度 ≤4 | 0 |
| 含保守白名单外字符 | **37 / 123 = 30.1%** |
| 其中含 `、` / 竖线 / `「」` / 集合或逻辑符号 | 16 / 16 / 10 / 其余（按“含该字符的别名条数”计，可重叠） |
| 等于或包含于主名 | 0（主名相等已在生成期丢弃） |

**本仓历史收益（本地证据，外部文献不能替代）**：批次16 T/C 24 条 → 模拟词面 89→93（**+4**）；批次19 A1 31 条 → 设备词面 **102/129**、融合 **117/129**；全包盲目缩短 >24 字题名 → 净 **−1**（16 片段截断位移）。来源：`docs/kb-batch16-2026-10-22.md:31`、`docs/kb-batch19-2026-10-23.md:9,47,110`、`docs/research/2026-10-26-kb-content-derived-alias-rules.md:90`。这组数字把“标题→别名”的预期钉在“**低收益、可逆、不能救 absent**”一档；R12 不应承诺结构性提升。

#### 1.1.4 已发布别名表的存量质量（`tables/alias_map.csv`）

| 项 | 读数 | 备注 |
|---|---:|---|
| CSV 记录数（`csv.DictReader`） | **37,561** | B2 的“37,568 行”是**物理行数**（含表头 37,569 行）；6 条别名内嵌换行，合计多 7 行 |
| 物理数据行 | **37,568** | 与 B2 口径一致 |
| 含控制字符/换行 | **6** | 如 `动物细胞有丝分裂·不\n同\n点`；`title_usable` 不检查控制字符，故能入表 |
| 长度 ≤3 字 | **76** | 含 `不能作`、`细胞既`、`细胞全`、`写名称`、`想原理`、`方法一` 等残片；也有 `ATP`、`Cl-`、`酵母菌`、`酶活性` 等合法短名 |
| 含 `、` | **3,460** | 9.2%；说明“禁 `、`”不能作为全表硬门，只能作为新别名软门/复核项 |
| 含 `「」/“”` | **325** | 引用式标题 |
| 含 LaTeX 字符（`^`、`_`、`$`、反斜杠） | **23** | LaTeX 残片 |
| 含竖线字符 | **10** | 表格/绝对值混入 |
| 含集合/逻辑符号 | **21** | `∅∪⊆⇔≠√{}` |
| 与当前 `rebuild()` 的差 | map-only **0**；cand-only **6** | 表比当前包少 6 条，说明表有轻微滞后；B2 的 +123 用 `rebuild()`，不受影响 |

**这条漂移对预注册的含义**：当前 `rebuild(pack)` 若写盘会改 6 个节点（`别名：40841 → 40847；变更节点 6`，本会话 dry-run 实读），所以 R12 不能只冻结 `alias_map.csv`；必须同时冻结 pack 文件 sha 与 `rebuild_aliases.py` 版本，并以 `rebuild(pack)` 为唯一真源。否则“+123”的分母会随表/包漂移。

#### 1.1.5 标题长度分布（作为 24 字阈值的本仓依据）

| 集合 | n | median | p90 | p95 | max | ≤24 |
|---|---:|---:|---:|---:|---:|---:|
| 205 条 `old_title` | 205 | 32 | 52 | 57 | 78 | 51 |
| 153 条 `new_title`（APPLIED） | 153 | 15 | 22 | **23** | **24** | 153 |
| 全包材料标题（本会话 25 个 sidecar 实读） | 64,853 | 16 | 42 | 49 | 183 | 44,217（68.18%） |

**读法**：24 是“短式标题的 p95+1”，不是外部标准；它恰好卡住本批最长值（24），因此 153 条全部过长度门。换成 25 不会改变本批结果；换成 20 会砍掉约 5% 的短式（p90=22）。材料数 64,853 是本会话 `sidecar_paths()` 实读；B1/B2 的 65,021 是另一快照，差 168，引用时必须带快照，不能混用。

### 1.2 外部事实（逐条给来源）

> 阅读方式：官方文档优先；能取全文的取全文，取不到全文的只引摘要并标“待验证”。本会话 `search_text` 多次超时/熔断，已用 `WebFetch`/`extract_content` 交叉补取；仍取不到的列在 §5。

| # | 来源 | 关键结论（原文/摘要） | 对本仓的直接含义 |
|---|---|---|---|
| S1 | Elasticsearch 官方《Synonym token filter》<br><https://www.elastic.co/docs/reference/text-analysis/analysis-synonym-tokenfilter> | Solr 格式：等价同义词用逗号分隔（`ipod, i-pod, i pod`）；显式同义词用 `=>`，左侧展开为右侧所有可能。`expand=true` 时 `foo, bar, baz` 展开为 9 条映射；`expand=false` 时**第一个词被当作 canonical**（`bar => foo`、`baz => foo`）。`stop` 过滤器在 synonym **之前**会从规则里删词并可能报错；在**之后**会删掉展开结果，导致规则失效。`lenient=true` 只忽略无法解析的规则，但不保证语义正确。 | 别名词表不是“随便写短语”：逗号、`=>`、停用词都会改变展开语义。本仓用集合而非规则串，但“第一个/主名”语义与 `expand=false` 的 canonical 思路同源；别名的方向性（谁展开到谁）必须显式定义。 |
| S2 | Elasticsearch 官方《Synonym graph token filter》<br><https://www.elastic.co/docs/reference/text-analysis/analysis-synonym-graph-tokenfilter> | `synonym_graph` 生成 graph token stream，能正确处理多词同义词；**只能用于 search analyzer**；多过滤器串联“can produce indeterminate search-time behavior”；默认每 filter 100 个 set、每 set 100,000 条规则。 | 多词别名（中文短语）必须按“图/短语”处理，不能按单 token 替换；本仓是节点级特征集合，不存在 ES 的位置图问题，但“串联/叠加”的歧义风险同样存在（多个别名共享 2–3 字 gram）。 |
| S3 | Elastic 官方博客《The same, but different: Boosting the power of Elasticsearch with synonyms》<br><https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms> | 索引期同义词：索引变大；“Search scoring … might suffer because synonyms are also counted, and the statistics for less common words become skewed”；规则改动需 reindex。搜索期同义词：索引大小、语料统计不变，规则可热更新。同义词“can be leveraged to increase the recall”，但“Adding synonyms can have unexpected side effects on relevance scoring … testing (be it A/B testing or … ranking evaluation) first before directly applying changes in production”。同义词不能替代 stemmer/fuzzy；过度使用是常见错误。 | 本仓别名是**索引期**进入节点特征与 dense 行：加别名会改变节点侧统计/特征预算，且不能热切换；必须整包重建 + 预注册评测。与 ES 推荐的“search-time synonyms”不同，这是架构约束，不是选择。 |
| S4 | Elasticsearch 官方指南《Synonyms: expand or contract》<br><https://www.elastic.co/guide/en/elasticsearch/guide/2.x/synonyms-expand-or-contract.html> | 扩展 = 列出的任一词展开为全部词；收缩 = 一组词映射到右侧单一值。索引期扩展“All synonyms will have the same IDF”；查询期扩展“The IDF for each synonym will be correct”。 | 本仓把别名并入节点特征集，等价于索引期扩展：别名越多的节点，其表面词统计越“平”，IDF 区分度下降。dense 腿不受 IDF 影响，但会增加同节点多行、提高该节点的 max 分概率。 |
| S5 | Algolia 官方《Synonyms》<br><https://www.algolia.com/doc/guides/managing-results/optimize-search-results/adding-synonyms> | 四类：regular（双向互换）、one-way（输入→同义词，反向不成立）、alternative corrections（精确匹配优先）、placeholders。多词短语“must be in the correct order with nothing in between”；“Algolia synonyms don't recognize plurals”；“Synonyms don't cascade”；连字符/下划线需加入 `separatorsToIndex`；“Overusing synonyms can lead to unexpected or unwanted results”。 | 电商搜索的可操作规则是：**方向性**（one-way 用于非对称关系）、**不级联**、**短语顺序敏感**、**不过度**。本仓没有 one-way 机制，所有别名等价双向；对“别名指向更宽概念”的情况（如 `平衡`）会制造歧义，只能靠全局互斥兜底。 |
| S6 | Algolia 支持《Understanding Synonyms: Best Practices and Common Problems》<br><https://support.algolia.com/hc/en-us/articles/44857152474129-Understanding-Synonyms-Best-Practices-and-Common-Problems> | 该页是问题索引，不给数值规范；明确同义词“help improve search relevance”，但与 rules 等特性联用“may occasionally produce unexpected results”；列出 multi-word、compound word、typo、special characters、CSV import 等常见问题。 | “同义词是相关性特性，不是数据清洗”这一判断有官方支持；具体规则要按引擎/语言实测，不能照搬英文规则。 |
| S7 | Voorhees, *Query Expansion using Lexical-Semantic Relations*, SIGIR 1994<br><https://link.springer.com/chapter/10.1007/978-1-4471-2099-5_7> | 摘要原文：扩展“makes little difference in retrieval effectiveness if the original queries are relatively complete descriptions … even when the concepts to be expanded are selected by hand. Less well developed queries can be significantly improved by expansion of hand-chosen concepts. However, an automatic procedure that can approximate the set of hand picked synonym sets has yet to be devised, and expanding by the synonym sets that are automatically generated can degrade retrieval performance.” | 这是“自动标题→别名”最直接的反证：**自动生成**的同义词集可能降低性能；人工挑选才在短/不完整查询上有效。本仓的标题派生是自动生成，必须用负向门和消融来证明不是退化。 |
| S8 | Wikidata 官方《Help:Aliases》<br><https://www.wikidata.org/wiki/Help:Aliases> | “Aliases are alternative names, like nicknames, acronyms, or translations.” 应排除：alternative capitalization、common spelling mistakes、alternative word order、section headers、以及“People, objects, events, or locations that are not individually notable, but are redirects”。同时：“Multiple items can have the same alias, so long as they have different descriptions.” | 别名 = **替代名称**，不是“相关短语/章节标题/子主题”。这一条直接否定“把材料标题（常常是章节/题型描述）无条件当别名”。Wikidata 允许同名是因为有 description 消歧；本仓匹配器没有 description，所以只能用“同学科全局互斥”替代，属于**设计推断**，不是外部规则直搬。 |
| S9 | 百度搜索资源平台《百度搜索网页标题规范》（2018-11-15）<br><https://ziyuan.baidu.com/college/articleinfo?id=2728> | 标题是“对网页内容的准确且简明扼要的描述”；原则：每个页面有标题、同站不同页分别用不同标题、准确概括、简明扼要、避免关键词堆砌、符号正确。优质格式：“**核心词+修饰词**”，修饰词建议**不多于 3 个**。符号规则：间隔符统一为 `-`；括号类（书名号、小括号除外）内 ≤4 字改 `「」`/`[]`，>4 字删除；删除标题末尾无意义标点；删除 emoji；中文句号改空格。 | “对象+限定”的公开依据在这里：核心词（对象）+ 修饰词（限定，≤3）。同时给出“唯一标题”“不堆砌”“符号清理”三条可操作规则。注意：该官方页**没有**给字数上限；“30 字”是第三方转述（§5 待验证）。 |
| S10 | Google Search Central《Influencing your title links》<br><https://developers.google.com/search/docs/appearance/title-link> | 写“descriptive and concise”的标题；避免模糊描述；避免“unnecessarily long or verbose”；避免 keyword stuffing；避免 repeated/boilerplate；每页标题应 distinct；“there's no limit on how long a `<title>` element can be, the title link is truncated … typically to fit the device width”；标题语言/书写系统应与正文一致。 | 与百度互证：**简明、唯一、不堆砌、无硬上限但会截断**。长度应由“展示/截断”与“可读性”决定，不应把它当成语义质量判据。 |
| S11 | 国家标准 GB/T 7713.2–2022《学术论文编写规则》<br>官方元数据：<https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=0B963916637B8F34B295FCF4A51A1BE5><br>修订说明（二手检索摘要）：<https://publishmedia.cbpt.cnki.net/portal/minio/webs/Editor/2023/0602/dhds/11bef1fb-7103-4077-ad3b-306eb41f7f25.pdf> | 官方页确认：标准号 GB/T 7713.2–2022，中文名《学术论文编写规则》，状态“现行”，实施日期 2023-07-01。二手修订说明称：题名字数由“题名一般不宜超过 **20** 字”改为“题名应简明，一般不宜超过 **25** 字”。 | 给“24/25”一个国家标准锚点：25 是现行学术论文题名上限；本仓 24 与之只差 1 字。注意：这是**学术论文**题名，不是教育内容/知识点标题；只能作类比依据。 |
| S12 | Clerk.io 厂商博客《Generative Synonym Tools for Ecommerce Search in 2026》<br><https://www.clerk.io/blog/generative-synonym-tools-ecommerce-search-2026> | “Proposed synonyms are tested against actual search behaviour and only applied if they perform.” “The merchandiser approves or rejects proposed synonyms before they go live.” 输入含 catalogue、search query log、language model；要监控 zero-result rate 与 conversion。 | 电商实践的一致方向：**生成 → 行为验证 → 人工批准 → 监控**。这是厂商单一来源（待验证），但与 S3/S7 的“先测再上”互证。 |

**自动抽取的五类已知噪声，分别有来源**：

| 噪声 | 外部依据 | 本仓对应实测 |
|---|---|---|
| 标点/分隔符 | S1（同义词规则里的逗号/`=>` 会改变展开；stop filter 会删词）；S9（标题符号清理）；S5（多词短语顺序/特殊字符） | 123 条新别名中 37 条含白名单外字符；存量 3,460 条含 `、`、325 条含中文引号、10 条含竖线 |
| 公式/LaTeX | S1/S2（按 token/图处理，符号不是自然语言 token）；R-alias 实测纯记号查询在词面腿产出 0 特征 | 存量 23 条含 `^`、`_`、`$` 或反斜杠；本批 1 条含 `^`、多条含 `∪/⊆/∅/√/≠` |
| 停用词/话语标记 | S1（stop 在 synonym 前会从规则删词，在后会删掉展开结果） | 存量为 `方法一/第二类/总反应` 这类无主语名；R-alias 的通用词/话语标记表 |
| 过长 | S9（简明、不堆砌）；S10（过长会被截断）；S11（题名 ≤25 字） | 存量 0 条 >24（被硬门挡住）；本批 153 条全部 ≤24；但全包 22,471 条候选里 20,612 条因 >24 被丢 |
| 子主题/章节标题被当别名 | S8（Wikidata 明确排除 section headers、非独立关注实体的 redirect） | 本批 10 条“方法/提醒/路线”被现行 `BAD_PREFIX` 误杀；2 条等于主名 |

### 1.3 交叉核对后的三条硬结论

1. **“同义词/别名”与“标题”不是同一类对象**：S8 说别名是替代名称；S9/S10 说标题是内容描述。材料标题常常是“这一节讲什么”的描述（甚至句子），不天然是节点的替代名称。把标题自动当别名，等于把“描述”当“名称”，这是语义类别错误。
2. **自动扩展的风险有实验证据，不是理论担忧**：S7 的结论是“自动生成的同义词集可能降低性能”；S3 说加同义词会意外影响评分；S5 说过度使用会带来不想要的结果。因此 R12 的默认立场应是“**先证伪**”，不是“加了会更好”。
3. **长度没有统一标准，24 是本仓工程值**：S9 官方无字数上限；S10 明确无硬上限但会截断；S11 的 25 是学术论文题名；本仓 24 是 `gate.py` 的硬门 + 153 条短式的 p95+1。用 24 可以，但不能声称“24 是规范”。

---

## 2. 别名生成/过滤规则建议（可直接落成代码判据）

### 2.1 管道（建议冻结为五段）

```
candidate generation
  → normalization
  → shape gates (fail-fast, reason code)
  → conflict resolution (node/subject/global)
  → audit table + counters
```

- **候选源**：只允许“该节点绑定材料的 `title`”，不允许正文、summary、boundary（与现行一致；R-alias 已论证正文派生是另一条低收益通道，不应混入本臂）。
- **候选单位**：一条材料标题 = 一个候选；同一节点内按规范化串去重；同一标题绑定多个节点时，每个节点各有一个候选，后续冲突门处理。
- **不生成**：空标题；标题等于主名；标题等于该节点已有别名（no-op，但仍入审计表，标 `already_alias`）。
- **顺序**：候选按 `(subject, slug, normalized_alias)` 排序；**禁止 first-wins**，歧义必须 drop-all（与现行 `claim` 逻辑一致）。

### 2.2 归一化（必须先做，再判形状）

1. Unicode **NFKC**（全角→半角、兼容字符归一；注意 NFKC 不会统一 `×/·/*`、`−/-/–`、`λ/μ`，R-alias 已实测）。
2. `strip()`；内部连续空白折叠为单个半角空格；中文与拉丁/数字之间的空格保留一个（可读性）。
3. 删除零宽字符（`\u200b-\u200f`、`\ufeff`）与 C0/C1 控制字符（`\x00-\x1f`、`\x7f`）。
4. 统一引号/括号：`“”‘’` → `"`/`'` 或保留原样但计入白名单；建议**保留中文引号**用于展示，但**词面别名**在归一化副本中去掉引号，只索引去引号版本。
5. 记录 `raw_title` 与 `normalized_alias` 两列；**冲突判定与唯一性用 normalized + casefold**，展示用 raw。

### 2.3 形状门（建议按顺序 fail-fast；reason code 直接落审计表）

| 门 | 判据（可直接实现） | 拦下的具体失败 | 本仓实测/依据 |
|---|---|---|---|
| **G0 非空** | `t.strip()` 非空 | 空标题 | 现行 |
| **G1 长度** | `2 ≤ len(t) ≤ 24`；纯汉字建议 `4 ≤ len ≤ 24`；混合公式允许 2–24 | 过长句子、单字/双字泛词 | 现行 `NAME_MAX=24`；153 条 p95=23；S11 25 可作替代上限 |
| **G2 控制字符** | 拒绝 `[\x00-\x1f\x7f\u200b-\u200f\u2028\u2029\ufeff]` | 换行/制表/零宽导致的 CSV 破行与显示污染 | 已发布表 6 条含换行；现行 `title_usable` 未查 |
| **G3 句末/逗号** | 拒绝 `。！？；;，,`；拒绝**结尾**的 `：:`、`、`；冒号仅允许“非结尾且有后缀”（教材节名 `实验：探究平抛运动的特点`） | 句子片段、题干、截断残片 | 现行；153 条中 2 条含逗号被拒 |
| **G4 括号** | 中文/英文/书名号/引号必须配对；允许 `（）()【】[]《》“”`；不配对即拒 | 截断残片 | 现行 `_balanced`；153 条 0 不配对 |
| **G5 LaTeX/纯符号** | 拒绝 `$`、`\`、`^`、`_`；拒绝“归一化后无任何汉字且无 ≥2 位字母数字”的纯符号串；允许 `+ - = / · × → ↑ ↓ ≤ ≥ ＜ ＞ % .` 作为公式连接符 | 公式片段、`x+y` 这类词面腿 0 特征的串 | 现行只拒 `$`/`\`，**不拒 `^`/`_`**；已发布表 23 条含 LaTeX 字符；R-alias 实测 `x+y`/`λ+μ` 产出 0 特征 |
| **G6 题面/指令 vs 方法名** | 拒绝真正的题干 artifacts：以 `例`/`典例`/`已知`/`下列`/`答案`/`解析`/`解:` 开头，或以「求…的值」「求…是多少」结尾，或「判断下列说法」；**允许**“动词 + 的 + 方法/策略/步骤/路线/路径/提醒/技巧/思路/要点/关键/类型/模型/条件/范围”的方法/专题名 | 题干被当名称；同时不误杀“求数列最大项的方法”这类方法名 | 现行 `BAD_PREFIX` 一刀切拒 `求/判断`，153 条中误杀 10 条；本会话反事实把该门放宽后净增 8 条唯一别名（+123→+131） |
| **G7 虚词/残片** | 拒绝以 `的/和/与/或/及/供/为/是/也/都/能/就/才/则` 结尾；拒绝以 `不/可/能/要/应/想/写/加/防/总/第` 开头的 3 字残片（需白名单保留 `不能作`? 建议直接拒） | `不能作`、`细胞既`、`写名称` 这类 | 现行只查结尾；已发布表 76 条 ≤3 字中含这类残片 |
| **G8 通用词/话语标记** | 拒绝纯通用词（`性质/方法/概念/反应/计算/应用/综合/专题/知识点/考点/原理/规律/公式`）与话语标记（`一般地/特别地/反之/综上/因此/所以`）；要求至少一个“内容 token”（汉字/字母数字，且不在通用表） | `性质`、`方法一`、`第二类` 这类无主语名 | R-alias 的术语门；已发布表有 `方法一/第二类/总反应` 等 |
| **G9 主名冗余** | `normalized == normalize(canonicalName)` → 拒；`normalized` 是主名的**真子串**且不增加新 gram → 记为 `redundant`（软拒，见 G12） | 153 条中 2 条等于主名 | 现行 discard；本批 2 条 |
| **G10 节点内唯一** | 同节点 `casefold` 后唯一；重复候选保留一条，其余记 `duplicate_within_node` | 153 行中 5 行同节点重复 | 现行集合天然去重；需在审计表显式计数 |
| **G11 跨节点冲突** | 同**学科**内：与任一其他节点的 `canonicalName` 或 `alias` 精确相等 → 拒；被 ≥2 节点 claim → **全部拒**（drop-all）；人工例外表登记者优先，其余让位并记 `exception_conflicts`。跨学科同名**不拒**，但记 `cross_subject_collision` 供复核 | 歧义名（`平衡`、`模型`）；153 条中 0 条触发 | 现行；S8 的“同名靠 description 消歧”在本仓不可用，故用互斥替代（设计推断） |
| **G12 特征增量（软门）** | 计算该别名的 2–3 字 gram + 整段词；若全部已被 `canonicalName + 现有 aliases` 覆盖，记 `no_new_feature`，默认仍准入（因为 `exactPhraseBoost` 仍可能加分），但计入审计；若节点已达 16 片段/192 特征，记 `budget_saturated` | 加了很多别名但词面特征不增；别名挤掉主名/boundary 片段 | `KnowledgeSearchFeatureExtractor` 的上限；本会话未逐节点计算饱和度，列为 §5 未验证 |

**关于“别名是否应与节点名互斥”**（分三层，别混成一句）：

- **额外别名不得等于本节点主名**（G9）。否则只是重复表面，白占 16 片段/192 特征与一行 dense 向量；本批 2 条正是这种冗余。
- **`aliases[0]` 必须是主名**（包契约）。因此“别名集合”包含主名；这是集合表示，不能说成“别名 = 主名”，也不能把主名从集合里删掉。
- **额外别名不得等于同学科另一节点的主名/已有别名**（G11）；跨学科同名允许。理由：本仓对“别名精确短语”给 70 分（主名 80），同名会让同一查询表面同时给两个节点加分，直接制造歧义；本仓没有 Wikidata 式的 description 消歧，只能用互斥替代。

**不建议作为硬门的三项**（本会话实测会误伤存量）：

- **禁 `、`**：已发布表 3,460 条含 `、`（9.2%），一刀切会删掉大量合法枚举式概念名；建议对**新别名**做“枚举 ≥3 个并列概念且无关系头（辨析/关系/区别/比较/综合）”时软拒/复核，不追溯存量。
- **禁中文引号 `「」/“”`**：存量 325 条；新别名中 10 条 `「」`。引号本身不产生词面特征（被分隔符切掉），主要是展示问题；建议**保留但去引号后索引**，不硬拒。
- **禁 `|`**：存量 10 条；新别名 16 条。`|` 常来自绝对值/表格混入，词面腿会切成两段；建议硬拒（它几乎总是抽取噪声），但先确认不是数学记号（如 `|x|` 可转写为 `x的绝对值`）。

### 2.4 冲突解决（冻结优先级）

1. **人工例外表 > 自动标题派生 > 其它**；例外条目仍过 G1–G11，且不能等于同学科另一节点主名。
2. **同一名字被 ≥2 节点 claim（同学科）→ 全部丢弃**，不做“先到先得”，不做“材料多者得”。理由：本仓匹配器没有 description/上下文消歧；drop-all 是唯一不引入隐藏偏置的规则。
3. **新别名与既有别名冲突 → 新别名让位**（不得“抢”已有归属）；记 `yielded_to_existing`。
4. **跨学科同名 → 允许**（物理/化学的“平衡”语义不同），但记 `cross_subject_collision`；如果未来查询路由引入学科先验，再重新评估。
5. **同节点重复 → 保留一条**；重复来源写入审计（本批 5 行）。
6. **例外表自身互斥**：同一名字登记给同学科两个节点 → 两条都拒（现行 `_validate_exceptions`）。

### 2.5 对本次 153 条的实测影响（把规则落到数据上）

| 规则 | 对 153 条的影响 | 证据 |
|---|---|---|
| G1 长度 ≤24 | 153/153 通过 | 本批 max=24 |
| G3 逗号/句末 | 2 条被拒 | 「验证机械能守恒定律：不必测质量，阻力是主要误差源」等 |
| G6 题面/指令 | 现行拒 10 条；放宽到“方法/提醒/路线”后净增 **8** 条唯一别名（+123→+131） | 本会话反事实 `rebuild()` |
| G9 主名冗余 | 2 条被拒 | 「函数图象的翻折变换」「铝及其化合物的易错点」 |
| G10 节点内重复 | 5 行折叠为 1 条/组 | 3 组重复标题 |
| G5 公式/符号 | **37/123（30.1%）** 含保守白名单外字符；若硬拒，本批新增降到约 86 条 | 本会话字符统计 |
| 存量清理（G2/G7/G8） | 6 条换行、76 条 ≤3、23 条 LaTeX、10 条竖线、21 条集合符号 | 若在本臂顺带清理，会额外改变 100+ 个表面，**必须另立臂**，不得混入 |

**结论**：本批 123 条新别名里，**86 条是“干净”的**（汉字/字母数字 + 有限连接符），**37 条是公式/符号型**。建议：
- **方案 A（推荐，最小改动）**：G5 硬拒 37 条 → 本批新增约 86 条；词面腿只收干净表面，dense 也少 37 行。代价是放弃这些标题的检索面。
- **方案 B（两通道）**：`lexical_alias` 走严格 G5；`display_alias` 只进 dense/展示、不进词面特征。需要新字段/新消费点，属于**新增机制**；它消灭的具体失败是“公式型标题在词面腿只产生噪声、但在 dense 腿有语义价值”。若不做，不要用“更完整”当理由硬塞进现有 alias 集合。
- **方案 C（全收）**：123 条全进；必须在预注册里承认 30.1% 是公式型表面，并把“词面腿是否退化”作为主判读之一。

**本研究的建议**：先跑“标题-only（别名不变）”证明标题改写本身不伤检索，再跑“别名-only（86 条干净别名）”证明别名增益；不要直接上 123 条全收。两步都通过后，才讨论是否把 37 条公式型标题走“dense-only 通道”。

### 2.6 可直接落成代码的判定函数（伪代码）

```python
# 返回 (ok, reason_code)；reason_code 进审计表，未知 code 即失败
def alias_verdict(raw, *, node_name, node_aliases,
                  other_names_same_subject, other_aliases_same_subject):
    t = normalize_nfkc(raw)                 # NFKC + strip + 空白折叠
    if not t: return False, "empty"
    if has_control(t): return False, "control_char"
    if not (2 <= len(t) <= 24): return False, "length"
    if any(c in t for c in "。！？；;，,"): return False, "sentence_punct"
    if t[-1] in "：:、": return False, "trailing_punct"
    if not balanced(t): return False, "unbalanced_bracket"
    if any(c in t for c in "$\\^_"): return False, "latex_char"
    if no_content_token(t): return False, "symbol_only"
    if QUESTION_ARTIFACT.match(t): return False, "question_artifact"
    if GENERIC_OR_DISCOURSE(t): return False, "generic"
    if t.endswith(BAD_SUFFIX): return False, "bad_suffix"
    if t == normalize(node_name): return False, "equals_canonical"
    if t in {normalize(a) for a in node_aliases}: return False, "already_alias"
    if t in other_names_same_subject or t in other_aliases_same_subject:
        return False, "ambiguous_same_subject"
    return True, "ok"
```

`QUESTION_ARTIFACT` 的具名常量建议写成（注意 `的值域` 不应命中）：

```python
QUESTION_ARTIFACT = re.compile(
    r"^(?:例|典例|已知|下列|答案|解析|解[:：])"
    r"|^求[^，,。；;]{0,12}(?:的值|是多少)(?:$|[和与或、，,])"
    r"|^判断下列说法"
)
```

配套要求：
- `normalize()` 与冲突判定必须**同一实现**，否则大小写/全半角会绕过互斥。
- `QUESTION_ARTIFACT` 与 `GENERIC_OR_DISCOURSE` 用**具名常量 + 单测**，不要散落正则。
- 审计表列：`subject, slug, material_slug, raw_title, normalized_alias, ok, reason_code, len, feature_delta, dense_row_delta`。
- 任何 `reason_code` 不在枚举内 → 整批失败（fail closed），防止“新规则静默放行”。

---

## 3. 标题规范建议（教育内容“对象+限定”短式）

### 3.1 本仓现行规范（比外部更具体，应作为操作基准）

`docs/kb-content-conformance-spec.md:44-51`：

- 实测：name 长度 min 1 / 中位 8 / p90 16 / **硬上限 24**（`gate.py` >24 判“整句话当名称”）。
- 必须：不以“的”开头；不写“…的定义/的性质”；方法类用**裸方法名**（`配方法` 而不是 `用配方法求最值`）；长度 ≤24，**纯中文 4–8 字最规范**；禁用题干/答案标记、只有标题冒号、通用名词无主语、公式被剥离、含 ★。
- 最规范样例：`函数的单调性`、`函数的奇偶性`、`正弦定理`、`二项式定理`、`牛顿第二定律`、`自由落体运动`、`物质的量`、`氧化还原反应`、`渗透压`、`体温调节`。

这组样例就是“对象（概念）+ 限定（属性/关系）”的典型：`函数` + `的单调性`、`牛顿` + `第二定律`、`自由落体` + `运动`。它同时说明：**限定词通常 0–1 个，不是堆 3 个**。

### 3.2 外部依据（逐条）

| 来源 | 规则 | 与本仓的对应 |
|---|---|---|
| 百度《网页标题规范》S9 | 核心词 + 修饰词；修饰词**建议不多于 3 个**；准确、简明、唯一、不堆砌；符号清理 | 支持“对象 + 限定”，并给出修饰词上限 3；支持“同节点标题应唯一”（本批 5 行重复正是反例） |
| Google《Title links》S10 | descriptive and concise；避免过长/堆砌/模板化；每页 distinct；无硬上限但会截断 | 支持“简明 + 唯一”，但不给具体字数；长度应由展示/可读性决定 |
| GB/T 7713.2–2022 S11 | 题名简明，一般不宜超过 **25 字**（二手转述；旧版 20 字） | 给 25 的国家标准锚点；与本仓 24 只差 1 |
| 本仓规范 §3.1 | 纯中文 4–8 字最规范；≤24；裸方法名；不写“的定义/的性质” | 比外部更严格、更贴合知识点命名；应优先于外部通用规范 |

**没有找到**“教育内容/题库标题必须采用对象+限定”的公开专项标准。上述三条是**类比依据**（网页标题、学术论文题名、本仓知识点命名），不是教育内容标题的强制规范；这一点在 §5 明确标为未验证。

### 3.3 建议的短式语法（可直接做规则/评审表）

允许的五种形态（按优先级）：

| 形态 | 模式 | 例 | 约束 |
|---|---|---|---|
| T1 裸概念 | `[对象]` | `牛顿第二定律`、`氧化还原反应` | 4–24 字；不是通用词 |
| T2 对象+属性 | `[对象]的[属性/关系]` | `函数的单调性`、`种群的年龄结构` | 只允许 **1 个“的”**；属性是名词性 |
| T3 裸方法 | `[方法名]` | `配方法`、`十字相乘法` | 不用“用…求…”；方法名本身是名词 |
| T4 对象+限定 | `[对象]的[限定]` | `匀变速直线运动的位移公式` | 限定 ≤3 个；总长 ≤24 |
| T5 对象+关系 | `[对象]与[对象]的[关系]` | `充分条件与必要条件`、`同位素与同素异形体` | 并列项 ≤3；必须有关系头（关系/区别/比较/辨析） |

禁止的形态：
- 完整主谓句/题干（`已知…求…`、`判断下列说法`、`…，…`）。
- “…”的定义/的性质”这类空壳（本仓规范明令）。
- 以虚词结尾的截断（`…的`、`…和`）。
- 修饰词 >3 个，或“的”链 >1 层。
- 公式主体（`f′(x)>0 是…`、`A∪B=B⇔A⊆B`）——可作正文，不作标题/别名；若要保留，走“公式转写”（`x的绝对值`、`A并B`）。
- 同一节点下两条标题完全相同（本批 5 行重复）。

### 3.4 长度阈值：24 的来源、替代值与建议

| 值 | 来源 | 适用性 |
|---:|---|---|
| **24** | 本仓 `gate.py`/`rebuild_aliases.py` 的 `NAME_MAX=24`；153 条短式 p95=23、max=24 | **操作值**；本批零误伤；无外部标准依据 |
| **25** | GB/T 7713.2–2022 学术论文题名“一般不宜超过 25 字”（二手转述，待验证） | 国家标准锚点；若要对齐标准，改 25 不影响本批 |
| **20** | GB/T 7713 旧版题名“一般不宜超过 20 字” | 过紧；会砍掉本批 p90=22 以上的短式 |
| **30** | 第三方文章称百度中文标题上限 30 字（官方页未给） | **待验证**，不建议作为内容标题硬门 |
| 无硬上限 | Google S10：无限制但会按设备宽度截断 | 说明长度是展示问题，不是语义问题 |

**建议**：保留 **24** 作为当前操作值（连续性好、本批零误伤），并在规范里写明“24 是本仓工程阈值，不是外部标准”；若要向国家标准靠拢，用 **25** 并在同一批重算标题分布。**不要**用 30 作为硬门，也不要用单一长度替代结构规则。

---

## 4. 完整臂（标题+别名+重打向量）的预注册要素清单

> 本节只列“必须冻结什么”，不选具体臂结果。任何一项未冻结，执行轮不得判读。格式对齐 B2 §4.2 的六元组与 R3 §3.1。

### 4.1 五个核心冻结项（缺一不可）

#### A. 别名生成规则（generation）

| 字段 | 必须冻结 |
|---|---|
| 候选源 | 仅“该节点绑定材料的 `title`”；禁止正文/summary/boundary；绑定解析口径（`bindings[].knowledgeNodeId` → `(subject, slug)`） |
| 输入集 | 153 条 APPLIED 的 slug 清单 + 每行 `old_title/new_title` 的 sha256；3 条 REVERTED 与 `^` 行回退规则 |
| 归一化 | NFKC、trim、空白折叠、零宽/控制字符删除、casefold 的唯一实现与版本 |
| 生成器版本 | `tools/kb_build/rebuild_aliases.py` 的 git blob sha；`NAME_MAX`、`SENTENCE_PUNCT`、`BAD_PREFIX/SUFFIX`、`gate._is_bad_name` 的 sha |
| 预期计数 | 20 节点 / +123 唯一 / 11 already / 10 prefix / 2 comma / 2 canonical / 5 duplicate；任一不符 → ABORT |

#### B. 过滤规则（filter）

| 字段 | 必须冻结 |
|---|---|
| 门清单 | G0–G12 的**逐条判据 + reason code**；哪些是硬门、哪些是软门（G12） |
| 标点白名单 | 允许字符集合；`、`、中文引号、竖线、`^`、`_` 的处置（硬拒/软拒/保留） |
| 长度 | 2–24 或 4–24；纯汉字与混合公式是否分口径 |
| 公式策略 | 硬拒 / 两通道（lexical vs display）/ 全收；若两通道，必须定义新字段与消费点（新增机制要指认消灭的失败） |
| 存量清理 | 是否在本臂清理 6 条换行、76 条 ≤3、23 条 LaTeX 等；**建议另立臂**，不混入 |
| 审计 | 每条候选的 `ok/reason_code/feature_delta/dense_row_delta`；未知 code 即失败 |

#### C. 冲突解决（conflict resolution）

| 字段 | 必须冻结 |
|---|---|
| 优先级 | 人工例外 > 自动派生 > 其它；例外仍过形状门 |
| 同学科歧义 | ≥2 节点 claim → **drop-all**；禁止 first-wins/材料数优先 |
| 与既有别名冲突 | 新别名让位，记 `yielded_to_existing` |
| 主名冲突 | 等于主名 → 拒；真子串 → 软拒/记 `redundant` |
| 跨学科同名 | 允许但记 `cross_subject_collision`；不引入学科先验 |
| 同节点重复 | 保留一条；记录重复行 |
| 例外表自身互斥 | 同一名字登记同学科两节点 → 两条都拒 |

#### D. 向量重打范围（dense re-embed）

| 字段 | 必须冻结 |
|---|---|
| 口径 | **整表重打**（推荐）vs delta 追加；若 delta，必须证明行序/组连续/索引重映射与整表逐字节等价 |
| 向量集 | `canonicalName + aliases`（ATOMIC）；**是否包含 title 向量行**必须二选一并写明。建议本臂**不包含** title 向量（title-vector 是另一条臂，2026-10-23 预注册，60,918 行），否则变量不唯一 |
| 行数 | 当前 `44,121 = 3,280 canonical + 40,841 alias`；+123 → **44,244**（整表口径）；若 delta 合并，实测为准，但必须在预注册里给期望值 |
| 编码 | docPrefix=null、maxLen=128、CLS+L2、int8 对称 scale、行序 = 节点序 + 节点内 canonical→alias 序、同节点行连续 |
| 资产身份 | `.vec` sha、`.vec.json` sha、`vectorCount`、`packSha256`、主包 sha、`alias_map.csv` sha；全部写前后对照 |
| 重打触发 | 只在别名集变化时重打；标题文本若同时改，必须说明 title 是否进入向量文本（本仓当前不进） |

#### E. 判读指标（endpoints）

| 层 | 指标 | 冻结口径 |
|---|---|---|
| 主判读 | 设备金标**融合** Recall@5（129 题 / 24 章） | `golden_queries_v2.json` sha `eabef102…`；命中定义 `:atomic:<expectedSlug>`；top-5；候选深度 512；同代判官 |
| 次判读 | 设备**词面** Recall@5；dense Recall@5；MRR | 同一批查询/同一设备/同一构建 |
| 分章 | 24 章逐章 Recall@5 | 逐章点估计；不允许只看总集 |
| 零漂移 | 同路对称差 = 0 | 词面臂与融合臂各自的同路复跑；非零即装置缺陷 |
| 歧义/错误 | top-1 错节点数、新增跨节点冲突数、人工复核 20 节点 | 与 baseline 逐条比对；新增歧义 >0 即拒 |
| 特征预算 | 命中 16 片段/192 特征的节点数；别名挤出主名/boundary 的节点数 | 本会话未测，执行轮必须测 |
| 规模/时延 | `.vec` 字节数、vectorCount、p50/p95 检索时延 | +123 行预期 <0.3%；时延不劣 |
| 标题质量 | **独立**于检索的 153 条标题审计（结构 T1–T5、长度、重复、题干残留） | 129 题**不能**测标题质量；必须另设 30 条分层抽样、双人 rubric |

### 4.2 身份与装置（六元组扩展）

必须写死：

```
pack_sha256
alias_map_sha256 + alias_map 记录数/物理行数
.vec_sha256 + .vec.json_sha256 + vectorCount
golden_queries_sha256（129/24）
代码树 sha256（rebuild_aliases.py / dense_asset.py / KnowledgeSearchFeatureExtractor.kt / 判官）
判官版本 + 设备型号/OS/构建号 + 同路复跑次数
title-shortforms CSV sha256 + 153 行清单 sha256
```

任何一项在执行轮与预注册不一致 → **ABORT**，不得用“差不多”继续。

### 4.3 决策规则（必须预注册，不得事后改）

建议采用 B2 的“三规则”并补两条：

1. **主集不劣**（或严格更高，二选一写死）：融合 Recall@5 不低于同轮 baseline；若写“严格更高”，+1 题即 0.78%，需说明最小可辨差异。
2. **24 章逐章不劣**：∀章 `recall_c(臂) ≥ recall_c(baseline)`；任一章下降即不采纳。
3. **零漂移 = 0**：同路对称差必须为 0；非零按装置缺陷处理，不判读。
4. **负向守卫**：新增 top-1 错节点/新增跨节点冲突 = 0；若 >0，即使主集提升也不采纳。
5. **标题质量审计**：结构违规率不得高于 baseline；153 条中重复标题必须为 0（本批 5 行重复是反例）。
6. **统计**：按 query 配对；按章 cluster bootstrap 给 95% CI；McNemar 精确检验；n=129 功效不足，报告 CI 与逐章，不只报 p。
7. **消融**：至少拆出“标题-only（别名不变）”“别名-only（标题不变）”“标题+别名”三格，否则无法归因；R3/批次32B 的教训是合并效应不可归因。

### 4.4 停止/回滚/证据

- **停止**：输入 sha 漂移；别名 delta ≠ +123（或登记值）；dense 行数 ≠ 预期；任一门红；新增歧义；判官换版未同轮重测。
- **回滚**：预注册列出每个触碰文件的 pre-sha；回滚 = 逐文件核验 sha 复原；不做部分回滚、不 cherry-pick。
- **证据**：命令、退出码、原始日志、逐 query 命中表、逐节点别名 diff、审计表、sha256 清单；缺证据不得判绿。
- **非声明**：本臂不证明“标题更好”“学生更喜欢”“材料级召回更好”；129 题只测节点级检索。标题质量需独立审计。

---

## 5. 未验证项

| # | 项 | 现状 | 处置 |
|---:|---|---|---|
| U1 | 百度“30 字”上限 | 官方页（S9）只给原则/符号规则，**未给字数**；“30 字”来自第三方文章 | 标“待验证”；不作为硬门 |
| U2 | GB/T 7713.2–2022 的“25 字”原文 | 官方页只确认标准号/名称/状态/实施日期；“20→25”来自二手修订说明 PDF 与转述 | 标“待验证”；引用时写“二手转述” |
| U3 | 24 是否为最优阈值 | 无公开来源；本仓仅 `NAME_MAX=24` 与 153 条 p95=23 的经验 | 保留为工程值；换 25 需同批重算 |
| U4 | 37 条公式型别名对词面/融合的实际影响 | 本会话只做字符统计，未跑检索 | 执行轮必须做“严格 G5 vs 全收”消融 |
| U5 | 放宽 G6 后净增 8 条是否真的提升召回 | 只做了反事实重建（+131），未跑评测 | 作为独立臂或消融，不直接采用 |
| U6 | 存量 76 条 ≤3 字 / 6 条换行 / 23 条 LaTeX 的清理收益 | 未做清理臂；清理会改变 100+ 表面 | 另立臂，不与 R12 混跑 |
| U7 | 特征预算饱和度（16 片段/192 特征） | 本会话未逐节点计算 | 执行轮加 `budget_saturated` 计数 |
| U8 | dense +123 行的边际收益 | 未跑设备/JVM；仅按 44,121→44,244 推期望 | 预注册主判读；不得用历史 117/129 代替同轮 baseline |
| U9 | 标题质量审计 rubric 的评分者一致性 | 尚未设计/试评 | 预注册时补 30 条分层样本、双人、κ |
| U10 | 外部来源的跨引擎复核 | 本会话 `search_text` 多次超时/熔断；部分页面 403/超时（Wikipedia Redirect、NN/g 404、Springer 部分重定向） | 已在 §1.2 标注单一来源；不把未取到全文的当定论 |
| U11 | Wikidata “同名可共享”与本仓“全局互斥”的冲突 | Wikidata 靠 description 消歧，本仓无此机制 | 本仓规则是**设计推断**，不是外部规范直搬；若未来引入描述/学科先验，可重评 |
| U12 | `alias_map.csv` 记录数口径 | 37,561 记录 vs 37,568 物理数据行（6 条内嵌换行，+7 行）；B2 的“37,568 行”是物理行 | 预注册同时冻结“记录数”与“物理行数”，避免再次混用 |

---

### 附：本会话可复现命令（只读）

```bash
# 1) 153/20/+123 独立复现（pre/post 差集；只打印，不写盘）
python -I - <<'PY'
import csv, sys
sys.path.insert(0, "tools")
from kb_build import rebuild_aliases as R, pack_io
rows = [r for r in csv.DictReader(open("build/agent-batch32b/rewrite/title-shortforms-new.csv", encoding="utf-8-sig"))
        if r["action"] == "APPLIED"]
mat2node = {}
for sp in pack_io.sidecar_paths():
    for m in pack_io.load_json(sp)["materials"]:
        for b in m.get("bindings") or []:
            p = b["knowledgeNodeId"].split(":")
            mat2node[m["slug"]] = (p[-3].upper(), p[-1])
pre = R.bound_titles(); post = {k: set(v) for k, v in pre.items()}
for r in rows:
    k = mat2node[r["slug"]]; post[k].discard(r["old_title"]); post[k].add(r["new_title"])
pack = pack_io.load_json(pack_io.pack_path()); exc = R.load_exceptions()
a = R.rebuild(pack, titles=pre, exceptions=exc)["candidates"]
b = R.rebuild(pack, titles=post, exceptions=exc)["candidates"]
diff = [k for k in set(a) | set(b) if a.get(k, set()) != b.get(k, set())]
print(len(diff), sum(len(b.get(k, set()) - a.get(k, set())) for k in diff),
      sum(len(a.get(k, set()) - b.get(k, set())) for k in diff))   # 期望：20 123 0
PY

# 2) 现有别名表质量读数
python -I - <<'PY'
import csv
rows=list(csv.DictReader(open('tools/kb_build/tables/alias_map.csv',encoding='utf-8-sig')))
print(len(rows), sum(1 for r in rows if len(r['alias'])<=3),
      sum(1 for r in rows if any(ord(c)<32 for c in r['alias'])))
PY

# 3) 别名重建 dry-run（不写）
python -I -c "import sys; sys.path.insert(0,r'D:/smart mistake book/tools'); from kb_build import rebuild_aliases as r; raise SystemExit(r.main())"
```

> 说明：命令中的绝对路径按本机工作目录写；执行轮请改为仓库相对路径或重新导出 `REPO`。`-I` 会忽略 `PYTHONPATH`，故用 `sys.path.insert` 显式注入 `tools/`。
