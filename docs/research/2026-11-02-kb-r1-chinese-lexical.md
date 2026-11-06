# 中文短查询的词面检索：端侧 SQLite 正确做法（R1，2026-11-02）

> **回答的问题**：在 Android 端侧离线知识库上，中文（短）查询的词面腿应该用什么 token 单元、什么打分公式，是否值得为 df/IDF 改 schema。
> **约束**：本文件只做调研与建议，不改代码、不改金标、不动融合常数。仓内现状以本文件 §0 的实读为准。
> **证据等级标注**：`[官方]` = sqlite.org / Android 官方文档或官方源码；`[实测]` = 本会话在 SQLite 3.50.4 上现场跑的探针（可复跑）；`[评测]` = 有检索评测的方法与读数；`[工程]` = 工程实践/实现存在性，不等于效果证据；`[待验证]` = 单一来源或只读到摘要。

---

## §0 本仓现状（实读，作为决策前提）

| 事实 | 值 | 出处 |
|---|---|---|
| 生产 SQLite 驱动 | `AndroidSQLiteDriver()`（framework SQLite），非 bundled | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:426,438` |
| 设备 framework SQLite 的 FTS5 | **不可用**（设备探针 `fts5=false`，设备 SQLite 3.39.2） | `docs/research/2026-10-06-s18-fts-ranking-report.md` §8 第 1 条 |
| Android 官方对 FTS5 可用性的口径 | "The availability of FTS5 is based on the driver used for the database. For Android specifically the `androidx.sqlite.driver.bundled.BundledSQLiteDriver` supports FTS5."（引文取自搜索索引摘要；原始页面正文本会话未取到，结论另有本仓设备探针独立佐证） | https://developer.android.com/reference/androidx/room3/Fts5 |
| KB 词面腿实现 | **不使用任何 FTS tokenizer**；`knowledge_search_feature` 表 + `search_feature IN (:features)` + `COUNT(DISTINCT search_feature)` 排序 | `core/database/src/main/kotlin/.../dao/ProblemOrganizationDao.kt:203-234` |
| 特征抽取规则（当前 `INDEX_VERSION=1`） | 每个片段产出：**整片段**（2–48 字）+ **全部 2-gram** + **全部 3-gram**；节点侧截断 16 片段 / 192 特征，查询侧 128 特征 | `core/database/src/main/kotlin/.../KnowledgeSearchFeatureExtractor.kt:95-102` |
| 特征表主键/索引 | PK `(subject, search_feature, knowledge_node_id)`；另 `(knowledge_node_id)`、`(subject, knowledge_node_id)` | `core/database/src/main/kotlin/.../entity/KnowledgeSearchFeatureEntity.kt`；`schemas/.../73.json` |
| 融合 | 域内 min-max，`α=0.5`，缺腿给 0，并列 `node_id` 升序 | `core/data/src/main/kotlin/.../dense/DenseFusion.kt` |
| 金标 | 129 条 / 24 章；CJK 长度 min 11 / p25 24 / **median 29** / p75 34 / max 44；**≤6 字的查询 0 条** | 本会话 `python -I` 读 `tools/kb_coverage/tables/golden_queries_v2.json` |
| 融合基线 | 117/129（v26 设备） | `docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md` §0.2 |
| 词面腿单腿基线 | 101/129 | 同上 |
| 另一条 FTS 路径（图书馆检索） | **FTS4** + 应用侧 CJK **单字**切分（`CjkTextTokenizer`），排序为列命中标志加权 | `core/database/src/main/kotlin/.../CjkTextTokenizer.kt`；`.../dao/LibraryFtsSearchDao.kt:24-35` |

**一句话**：KB 词面腿当前是"应用侧 2/3-gram + 整片段"的特征检索，不是 FTS；FTS5 在当前驱动上根本不可用。下面的 tokenizer 讨论必须放在这个前提下读，否则会答错题。

---

## §1 事实（逐条带 URL）

### 1.1 三种 tokenizer 的行为差异

| 项 | unicode61 | ascii | porter | trigram |
|---|---|---|---|---|
| 中文分词 | **不分词**：连续汉字整段成为一个 token | **不分词**：非 ASCII 全部视为 token 字符 | 不是分词器，是英文词干包装器 | 不做"词"切分，做**三字符子串**切分 |
| token 定义 | "Each contiguous run of one or more token characters is considered to be a token." token 字符 = Unicode 6.1 的 `L*`/`N*`/`Co` | 同 unicode61，但码点 >127 一律算 token 字符 | 对底层 tokenizer 的输出套 Porter stemming | "treats each contiguous sequence of three characters as a token" |
| 中文子串查询 | **不能**（整段一个 token） | **不能**（同左） | 不适用 | **能**（≥3 个 Unicode 字符） |
| 大小写/变音符 | Unicode 6.1 规则；默认去拉丁变音符 | 仅 ASCII 折叠；不支持 `remove_diacritics` | 继承底层 | 默认大小写不敏感；`remove_diacritics` 默认 0 |
| 版本 | FTS5 自 3.9.0（2015-10-14）起内置 | 同上 | 同上 | **3.34.0（2020-12-01）**："Enhanced FTS5 to support trigram indexes." |
| 停用词 | **无**停用词选项 | 无 | 无 | 无 |

来源：https://sqlite.org/fts5.html#unicode61_tokenizer 、https://sqlite.org/fts5.html#ascii_tokenizer 、https://sqlite.org/fts5.html#porter_tokenizer 、https://sqlite.org/fts5.html#the_trigram_tokenizer 、https://sqlite.org/releaselog/3_34_0.html （`[官方]`）。

**porter 明确不适用**：官方原话 "The porter stemmer algorithm is designed for use with English language terms only - using it with other languages may or may not improve search utility."（https://sqlite.org/fts5.html#porter_tokenizer ，`[官方]`）。

**unicode61 对中文等于"整段当一个 token"——实测确认**（`[实测]`，SQLite 3.50.4）：

```
docs:
  浮力等于排开液体所受的重力
  物体的浮沉条件取决于浮力与重力的大小关系
  阿基米德原理说明浮力大小
  电场强度是矢量
fts5vocab(row) 的 term:
  ('浮力等于排开液体所受的重力', 1, 1)
  ('物体的浮沉条件取决于浮力与重力的大小关系', 1, 1)
  ('电场强度是矢量', 1, 1)
  ('阿基米德原理说明浮力大小', 1, 1)
查询 '浮力' -> 0 ; '排开液体' -> 0 ; '重力' -> 0 ; '浮' -> 0
查询 '浮力等于排开液体所受的重力' -> 1
```

这是 unicode61 定义（连续 token 字符 = 一个 token）的直接后果，不是 bug；中文没有空格，整句就是一串 `L*`。

**trigram 能否用于中文子串匹配：能，但最小查询长度是 3 个 Unicode 字符**（`[官方]` + `[实测]`）：

- 官方 Notes 原话："Substrings consisting of fewer than 3 unicode characters do not match any rows when used with a full-text query."（https://sqlite.org/fts5.html#the_trigram_tokenizer ）
- 实测（同一批中文文档，`tokenize='trigram'`）：

```
'浮'    (1 字) -> 0
'浮力'  (2 字) -> 0
'力等'  (2 字) -> 0
'浮力等'(3 字) -> 1
'排开液体'(4 字) -> 1
```

- 关键失败模式：**2 字查询返回 0 行，不报错、不降级**。这是静默空结果，比语法错误更难发现。
- `LIKE '%浮力%'` 仍能命中（官方：trigram 表支持走索引的 LIKE/GLOB；模式里没有 ≥3 连续非通配字符时退化为全表线性扫描），所以"LIKE 能查"不能证明"MATCH 能查"。
- 另一条官方限制：`detail=none` / `detail=column` 时，"full-text queries may not contain any tokens longer than 3 unicode characters"（https://sqlite.org/fts5.html#the_trigram_tokenizer ，`[官方]`）。

**索引体积代价**（`[实测]`，本仓真实语料：3,705 条中文文档，topic 名 + 知识点名 + 别名，平均 172 字；`content=''` 只计索引，`optimize` + `VACUUM` 后测文件字节）：

| tokenization | 索引字节 | KiB | 去重 term 数 | 相对单字 |
|---|---:|---:|---:|---:|
| unicode61 原样（整段 1 token） | 1,310,720 | 1,280 | 未测（≈文档数） | 1.25x |
| unicode61 + 应用侧**单字**切分 | 1,048,576 | 1,024 | 2,016 | 1.00x |
| unicode61 + 应用侧**bigram** 切分 | 1,368,064 | 1,336 | 40,111 | **1.30x** |
| trigram | 2,818,048 | 2,752 | 157,497 | **2.69x** |

读法：相对"单字"这个现实基线，bigram 约 1.3 倍，trigram 约 2.7 倍；相对"整段一个 token"的病态基线，trigram 约 2.15 倍。官方文档**没有**给出 trigram 体积倍数的定量说法（只有"用 `detail=none` 可减小索引"的定性建议），所以倍数只能引用实测。外部单一工程来源（Hermes Agent issue）报告 trigram 表 247 MB、约为普通 FTS 索引 2.6 倍（https://github.com/NousResearch/hermes-agent/issues/22478 ，`[工程]`/`[待验证]`，单一来源）。

### 1.2 FTS5 `bm25()` 的公式与参数

**权威实现**（`[官方源码]`，https://raw.githubusercontent.com/sqlite/sqlite/master/ext/fts5/fts5_aux.c ）：

```c
const double k1 = 1.2;
const double b  = 0.75;
...
/* IDF = log( (N - nHit + 0.5) / (nHit + 0.5) )  */
double idf = log( (nRow - nHit + 0.5) / (nHit + 0.5) );
if( idf<=0.0 ) idf = 1e-6;
...
score += aIDF[i] * ( ( aFreq[i] * (k1 + 1.0) )
                   / ( aFreq[i] + k1 * (1 - b + b * D / pData->avgdl) ) );
sqlite3_result_double(pCtx, -1.0 * score);
```

即：

```
bm25(D, Q) = - Σ_i IDF(q_i) · f(q_i,D)·(k1+1) / ( f(q_i,D) + k1·(1 - b + b·|D|/avgdl) )
IDF(q_i)   = ln( (N - n(q_i) + 0.5) / (n(q_i) + 0.5) )      ，若 ≤ 0 则取 1e-6
k1 = 1.2 ，b = 0.75（硬编码，SQL 层不可调）
N = FTS 表总行数；avgdl = 全表 token 总数 / 行数；|D| = 当前行 token 数
f(q_i,D) = 该 phrase 在行内的实例数（列权重默认 1.0）
```

**四个容易写错的细节**：

1. **SQLite 的 IDF 没有 `+1`**。它是 `ln((N-df+0.5)/(df+0.5))`，并在 ≤0 时钳到 `1e-6`；Lucene 的写法是 `ln(1 + (N-df+0.5)/(df+0.5))`。单看一个词，两者只差一个单调变换；但**多词求和时的相对权重不同**，足以翻转名次（见下面实测反例）。
2. **负 IDF 被钳位**：词出现在超过半数文档时，贡献≈0（`1e-6`）。这不是"平滑"，是硬钳。
3. **`N`/`avgdl`/`n(q_i)` 是整张 FTS 表上的量**，与 `WHERE` 过滤无关（`xRowCount` / `xColumnTotalSize` / `xQueryPhrase` 计数，见源码）。
4. **`bm25()` 必须用表名，不能用别名**：`bm25(x)` 报 `no such column: x`，`bm25(f)` 在 `FROM f AS x` 下仍可用（`[实测]`，3.50.4）。仓内 `docs/kb-fts5-production-plan.md:175` 已记录同一坑。

**能否在 SQL 外自算等价 IDF + 长度归一：能，但必须逐字复刻钳位与"无 +1"**。`[实测]` 在 3.50.4 上按上式复算，与 `bm25()` 返回值逐位一致（示例：`-2.960907` / `-0.231287`，12 位小数相同）。仓内已有先例：Stage-1 臂 A' 用自写 df+BM25 SQL 与 FTS5 `bm25()` 对拍，容差 0（`docs/kb-fts5-production-plan.md:32`，内部证据）。

**自算用错 IDF 变体会翻转名次——实测反例**（`[实测]`，N=100，两个候选各匹配 2 个特征）：

| 候选 | 匹配特征的 df | SQLite IDF 和 | Lucene(+1) IDF 和 |
|---|---|---:|---:|
| A | 1, 10 | 6.349 | 6.473 |
| B | 2, 6 | **6.351** | **6.442** |

SQLite 口径下 B 略高，Lucene 口径下 A 更高——**排序翻转**。所以"我按 BM25 公式自算"不等于"与 FTS5 `bm25()` 等价"；若要跟 Stage-1 的 FTS5 读数对比，必须用钳位版、无 `+1` 版。

**与 `COUNT(DISTINCT feature)` 排序相比，短查询上的典型差异**（`[实测]`，N=20，两个候选都匹配 2 个特征）：

| 文档 | 长度(token) | 匹配特征数（现行排序键） | FTS5 bm25 |
|---|---:|---:|---:|
| row 1 `rare common` | 2 | 2 | **-2.960907** |
| row 3 `common filler×9` | 10 | 2 | **-0.231287** |

`COUNT(DISTINCT)` 把 row 1–8 全部判成并列（都是 2），只能靠粒度/名字/id 兜底；bm25 把"短且含稀有词"的 row 1 排到前面，分差约 12.8 倍。机制上就两条：**IDF 奖励稀有匹配、长度归一惩罚长文档**；短查询（匹配特征少）时，这两个信号几乎就是全部排序信息，而 `COUNT` 只剩"命中几个"这一位信息。仓内 Stage-1 的 +11.1pp（0.6556 vs 0.5444，90 题）与 MRR 3.7× 是这条机制在真实金标上的内部读数（`docs/kb-fts5-production-plan.md` §1.2），不是外部证据。

### 1.3 中文不分词时，char bigram 作为检索单元的已知做法与效果证据

| 来源 | 类型 | 语料/查询 | 结论 | 局限 |
|---|---|---|---|---|
| Lucene `CJKAnalyzer` | `[工程]`+`[官方]` | — | "Index bigrams (overlapping groups of two adjacent Chinese characters) as tokens." 同包另有 `ChineseAnalyzer`（unigram）与 `SmartChineseAnalyzer`（分词） | 只说做法，不给效果 |
| Kwok 1997, *Comparing representations in Chinese information retrieval* | `[评测]` | TREC-5 中文新闻 ~170 MB；**28 条长查询** | 摘要（OpenAlex 倒排索引重建）：1-gram "good but not sufficiently competitive"；bigram "works surprisingly well"；bigram 的索引 term 空间约为 1-gram 的 **3 倍**，但精度更好，"about 5% better relevants retrieved"；最佳非插值平均精度 0.45，比 1-gram 高 **17%** | **查询是长查询**，不能直接外推到 2–4 字短查询；PDF 403，只读到摘要 |
| Zhou, Hawking & Thomas 2015, *Text segmentation and Chinese site search* | `[评测]` | 465,000 页站点抓取；503 个 judged pair；56 名学生标注 | 站点搜索（**短查询**场景）；检索摘要结论："We find no performance differences overall, but our case studies demonstrate where and when segmentation or bigrams are advantageous." | 结论句来自摘要聚合页（scispace），PDF 不可达，`[待验证]`；"no overall difference" 不等于"bigram 更好" |
| `Ray0907/fts5-cjk`、`hakazv/sqlite-fts5-bigram` | `[工程]` | — | 存在专门的 FTS5 中文/Unicode bigram tokenizer 实现（一个 C 文件、无词典） | 只证明"有人这么做"，不证明效果 |

URL：https://lucene.apache.org/core/api/analysis/common/org/apache/lucene/analysis/cjk/package-summary.html ；https://doi.org/10.1145/278459.258531 （OpenAlex 记录 https://api.openalex.org/works/doi:10.1145/278459.258531 ）；https://doi.org/10.1145/2838931.2838940 ；https://github.com/Ray0907/fts5-cjk ；https://github.com/hakazv/sqlite-fts5-bigram 。

**可下的结论与不可下的结论**：

- 可下：bigram 是中文 IR 里有长期评测支撑的"无词典"基线；相对 unigram 通常更准但索引更大（约 3 倍 term 空间，Kwok 1997）。工程上 Lucene 直接把它做成内置 analyzer。
- 可下：站点搜索（短查询）上，bigram 与分词**总体没有显著差异**（Zhou/Hawking 2015，`[待验证]`）。
- **不可下**：没有找到"2–4 字中文查询 + BM25 + 端侧 SQLite"这一具体组合的对照评测。任何"短查询上 bigram 比 trigram 好 N 个百分点"的说法目前都**没有可溯源证据**。

### 1.4 端侧 SQLite 预计算 df/IDF 的常见做法与迁移成本

**做法 A：直接用 FTS5 的 `bm25()`，不建统计表。** FTS5 在查询期对每个 phrase 调 `xQueryPhrase` 数命中行（`fts5CountCb`），成本是 O(df) 的 doclist 扫描，不是全表扫描；`avgdl` 来自 `xColumnTotalSize`。来源：`fts5_aux.c`（同上，`[官方源码]`）。

**做法 B：用 `fts5vocab` 读 df。** `fts5vocab(t,'row')` 的列是 `(term, doc, cnt)`：`doc` = 含该 term 至少一次的 FTS 行数（即 document frequency），`cnt` = 实例总数；`col` 模式按列拆。源码注释原文："The value of $doc is set to the number of fts5 rows that contain at least one instance of term $term."（https://raw.githubusercontent.com/sqlite/sqlite/master/ext/fts5/fts5_vocab.c ，`[官方源码]`）。`term = ?` 走索引（`FTS5_VOCAB_TERM_EQ`），所以按查询词取 df 是 128 次点查，不是全表。

**做法 C：导入期物化 df/IDF 表（本仓 L1 预注册的设计）。** `docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md` §1.4 冻结了 4 张派生表：`feature_df`、`df_idf`（1e-9 定点）、`node_len`、`corpus_stat`（含 `stale` 脏标记），schema 71→72，5 个写路径维护点，锚点最后写。这是"查询期零统计成本"的方案，代价是派生数据一致性风险。

**做法 D：查询期现算 df（不改 schema）。** 用现有 PK 前缀 `(subject, search_feature, ...)` 对每个查询特征做 `COUNT(DISTINCT knowledge_node_id)`。

**本会话实测的成本量级**（`[实测]`，桌面 x86、SQLite 3.50.4；语料 = 本仓 3,280 节点按 `KnowledgeSearchFeatureExtractor` 规则重抽，192 特征封顶，得 373,882 行特征、152,340 个不同特征）：

| 项 | 读数 |
|---|---:|
| 导入期 `CREATE TABLE df AS SELECT search_feature, COUNT(DISTINCT node_id) … GROUP BY` | **0.18 s** |
| df 表体积 | **3,348 KiB**（特征库 22,620 KiB 的 **+15%**） |
| 查询期 df，128 特征，单科（PK 前缀） | **3.0 ms** |
| 查询期 df，128 特征，跨 4 科全局 | **4.3 ms** |

注意：这是桌面读数，设备/模拟器会放大（倍数未测，见 §4）；且合成特征行数（373,882）低于仓内既有断言下界 `>500,000`（`KnowledgeSearchFeatureImportEquivalenceTest.kt:67`），所以绝对成本只能当量级参考。

**迁移成本/风险（做法 C）**：

- schema 现值是 **73**（`StudyDatabase.kt:142`），L1 冻结的 71→72 需要 rebase 到 73→74；Room 不支持降级，回滚必须清库重装（L1 §4.4 已写）。
- 新增 4 表 + 实体 + 迁移 + `refreshSearchFeatureStats()` + 5 个写路径同事务维护 + `stale` 读路径短路；每处漏掉都会让 df 静默偏大/偏小。
- 派生数据与备份/恢复的关系要单独定义（L1 §1.2 已声明"不随内容备份作为内容恢复"）。
- 收益是省掉每次查询 3–4 ms（桌面）的统计成本；风险是**静默过期**，且一旦过期，排序仍然"看起来正常"。

### 1.5 已知坑（全部有实测或官方原文）

1. **unicode61 对中文 = 整段一个 token**。见 §1.1 实测。任何"用默认 tokenizer 做中文检索"的方案在索引侧就已经错了。
2. **查询串里的标点/公式会让 `MATCH` 直接抛语法错误**（`[实测]`，3.50.4）：

```
'x=1'      -> ERROR: fts5: syntax error near "="
'f(x)'     -> ERROR: fts5: syntax error near "f"
'a-b'      -> ERROR: no such column: b        （'-' 被当成列过滤前缀）
'v=x/t'    -> ERROR: fts5: syntax error near "="
```

   官方规则：bareword 只允许"非 ASCII 字符 / 52 个 ASCII 字母 / 10 个数字 / 下划线 / U+001A"，其它字符必须加引号（https://sqlite.org/fts5.html#fts5_strings ，`[官方]`）。用户题面含 `=`、`(`、`-`、`/` 是常态（本仓题面/公式场景），**绝不能把原文直接塞进 `MATCH`**；必须切 token 后逐个加双引号（仓内 `CjkTextTokenizer.matchExpression` 已是这个形状，`.../CjkTextTokenizer.kt:63-73`）。
3. **`AND`/`OR`/`NOT` 是大写敏感的操作符**，`NEAR(` 是语法结构；用户查询里出现这些大写词会改变语义或报错。逐 token 加引号可消除。
4. **中文标点不报错但会被当分隔符**：中文逗号/句号是非 ASCII，允许出现在 bareword 里，但 tokenizer 视其为 separator，于是 `浮力，重力` 变成隐式 AND（`[实测]`：返回 0，因为文档是整段 token；在已切分的索引上会变成两个 token 的 AND）。全角/半角混排还会产生不同 token。
5. **数字/公式记号（如 `x+y`）语义随 tokenizer 变**（`[实测]`）：

```
默认 unicode61：'x+y' -> 命中（解析为 phrase x 后接 y）；vocab 里有 x, y 两个 term
tokenchars '+' ：'x+y' -> 0（bareword 变成一个 token 'x+y'）；'"x+y"' -> 命中
```

   即：加 `tokenchars '+'` 会把 `x+y` 变成单一 token，查询侧也必须用同样的引号形状，否则召回从"有"变"无"。这是双向一致性要求，不是单侧配置。
6. **没有停用词机制**：unicode61 的选项只有 `remove_diacritics` / `categories` / `tokenchars` / `separators`（https://sqlite.org/fts5.html#unicode61_tokenizer ），**没有 stoplist**。实测单字切分后 `的` 被索引且可命中（`'的' -> 1`）。中文高频虚词（的/了/是/在）会进入索引、抬高 df、压低 IDF；要处理只能在应用侧过滤。
7. **trigram 的 2 字静默空结果**（见 §1.1）。对"浮力""电场""数列"这类两字学科词是硬伤。
8. **IDF 钳位让超高频词几乎无信号**：`df > N/2` 时 IDF = `1e-6`（源码）。在小语料/小分科上，常见 2-gram 可能触发钳位，bm25 退化成近似"无排序"。
9. **`bm25()` 只认表名不认别名**（实测；仓内 `kb-fts5-production-plan.md:175` 同记）。
10. **FTS5 token 上限 32768 字节，超长静默截断**（https://sqlite.org/fts5.html#maximum_token_length ，`[官方]`）。中文整段 token 在长材料上可能触碰，截断后不同文本会变得不可区分。

---

## §2 可选方案对比表

> 评价维度按本仓真实约束：framework SQLite 无 FTS5、KB 语料 3,280 节点、金标查询 median 29 字、融合 α=0.5 min-max、schema 已到 73。

| # | 方案 | 中文子串/2 字查询 | 索引体积 | 需要换驱动 | 排序公式 | schema 变更 | 主要风险 |
|---|---|---|---|---|---|---|---|
| A | FTS5 `unicode61`（默认） | 不能 / 不能 | 最小（整段 1 token，倒排几乎无词） | 是（framework 无 FTS5） | 可用 `bm25()`，但**没有可匹配的 token** | 建 FTS5 虚表 | 方案本身对中文无效 |
| B | FTS5 `trigram` | 能（≥3 字）/ **不能（2 字静默 0 行）** | 实测 **2.69x** 单字基线（term 数 157k vs 2k） | 是 | 内置 `bm25()` | 建 FTS5 虚表 | 2 字查询静默空；体积最大；`detail=none` 还限制查询 token ≤3 字 |
| C | FTS5 `unicode61` + 应用侧 **bigram** 预切分 | 能 / 能 | 实测 **1.30x** 单字基线（term 40k） | 是 | 内置 `bm25()` | 建 FTS5 虚表 + 改写索引/查询两侧文本 | 切分规则必须双侧逐字一致；查询侧拼 phrase 的语义要设计 |
| D | **不用 FTS**：现有特征表（整片段 + 2-gram + 3-gram）+ 自算 BM25 | 能 / 能（2-gram 本来就是索引单元） | 现有特征库 22.6 MiB；df 表 +15% | **否**（framework SQLite 即可） | 自算 BM25（须复刻钳位/无 +1） | 可选：df 统计表；或零 schema（查询期 df） | 自算公式漂移；df 统计过期；SQL 无 `ln()` |
| E | 现状：特征表 + `COUNT(DISTINCT feature)` | 能 / 能 | 最小 | 否 | 无 IDF、无长度归一 | 无 | 长文档/多别名节点霸榜（已登记 D-01） |

补充：

- 方案 C 在 FTS5 生态里是成熟做法（Lucene `CJKAnalyzer` 就是 bigram；SQLite 社区有 `fts5-cjk` / `sqlite-fts5-bigram` 专门 tokenizer）。但本仓方案 D 的特征集**已经是 C 的超集**（还多了整片段与 3-gram），换成 C 反而是能力回退。
- 方案 B 的唯一优势是"不用写切分代码"，代价是 2 字查询不可用 + 索引最大。对"用户可能输入两字词"的产品面（图书馆检索、未来的搜索框），B 是错误选择。
- 方案 A 只适合英文/数字混排；对中文是"能建表、查不到"。

---

## §3 对本仓的决策建议

### 3.1 词面腿该用哪种 token：继续用应用侧 2/3-gram + 整片段，不引入 FTS tokenizer

理由（按权重）：

1. **FTS5 在当前驱动上不可用**（`[实测]` 设备 `fts5=false`；`[官方]` Android 文档说明只有 bundled driver 支持）。引入任何 FTS5 方案都要先做驱动切换，而驱动切换动的是全库 SQL 引擎（迁移矩阵、`VACUUM INTO` 备份、schema dump、既有 FTS4 图书馆检索），风险面远大于收益面（`docs/kb-fts5-production-plan.md` §4.2 已逐条列出）。
2. **现有特征集已经覆盖 bigram/trigram 的全部能力**：`fragmentFeatures` 产出整片段（2–48 字）+ 全部 2-gram + 全部 3-gram（`KnowledgeSearchFeatureExtractor.kt:95-102`）。这正是 §1.3 里"bigram 作为检索单元"的做法，且额外保留了整片段用于精确名匹配。
3. **金标查询不是短查询**（median 29 字，0 条 ≤6 字），所以 trigram 的 3 字下限对金标不构成威胁——但这也说明"用 trigram 修金标"没有靶子；真正的 2 字场景在图书馆检索（用户输入"浮力"），那里 trigram 会静默返回 0。
4. **图书馆检索是 FTS4**，连 `bm25()` 都没有；它的 CJK 单字切分（`CjkTextTokenizer`）若要提高精度，正确的升级是**单字→bigram**（有 §1.3 的评测支撑），不是换 trigram。这一步零 schema 变更，只需索引与查询两侧同步改切分并重建索引。

**明确不建议**：为 KB 词面腿新建 `tokenize='trigram'` 的 FTS5 表。它在 2 字查询上是静默失败，索引还是最大的。

### 3.2 公式：BM25，但必须选对 IDF 变体并写死口径

建议采用**与 SQLite FTS5 `bm25()` 逐字等价**的形式（这样任何与 Stage-1 臂 A 的对比都成立）：

```
score(n) = Σ_{f ∈ matched(n)} IDF(f) · (k1+1) / (1 + k1·(1 − b + b·len(n)/avgdl)) + 1
IDF(f)   = ln( (N − df(f) + 0.5) / (df(f) + 0.5) )   ，若 ≤ 0 取 1e-6
k1 = 1.2，b = 0.75，tf ≡ 1（特征按 (node, feature) 去重）
N = 被索引节点数（当前 3,280），df 为全局 df，len = 节点特征行数，avgdl = 总特征行数 / N
```

三个必须写进预注册的口径点：

- **IDF 不带 `+1`、带 1e-6 钳位**。仓内 L1 预注册写的是 `ln((N−df+0.5)/(df+0.5) + 1)`（`docs/research/2026-11-01-kb-l1-bm25-lexical-prereg.md` §1.1），这是 Lucene 变体，**与 FTS5 `bm25()` 不等价**，且 §1.2 的实测反例证明它能翻转名次。若 L1 照原样执行，其读数不能与 Stage-1 的 FTS5 读数直接比较，也不能声称"复刻了 bm25"；若目标是 FTS5 等价，必须在执行前改这一处（属 §1–§5 冻结面，需要重开预注册，不能在执行轮偷偷改）。
- **末尾 `+1` 无害**：它是全候选常数平移，min-max 归一化后完全抵消（`DenseFusion.minMax` 是线性变换），保留即可，别当调参旋钮。
- **`df`/`avgdl` 的域必须与召回域一致**（同样的 `verification_status` 集合、排 `RETIRED`），否则 IDF 与候选集错位（L1 §1.2 已冻结）。

### 3.3 是否值得改 schema：先跑"零 schema 的查询期 df"，只有 p95 红才落统计表

这是本文件与 L1 预注册唯一实质不同的建议，理由是 §1.4 的实测：**查询期 df 只要 3–4 ms（桌面，128 特征）**，而统计表方案要 4 张表、schema 73→74、5 个写路径维护点、一个会静默过期的 `stale` 标记，以及一次不可降级的迁移。

建议按顺序：

1. **第一步（零 schema）**：在现有召回 SQL 之外加一条 df 查询（128 个特征，`WHERE subject IN (...) AND search_feature IN (...)`，`GROUP BY search_feature`），IDF 在 Kotlin 用 `StrictMath.log` 算（避开 SQL `ln()` 的可用性问题；`ln()` 自 SQLite 3.35.0 起才需 `SQLITE_ENABLE_MATH_FUNCTIONS` 编译开关，https://sqlite.org/releaselog/3_35_0.html ，Android framework 构建是否开启未验证）。
   - 用现有 PK 前缀即可，不需要新索引；跨科全局 df 就是 4 次前缀探测（实测 4.3 ms）。
   - 若实测发现某几个高频特征把 df 查询拖慢，再考虑**只加一个索引**（`search_feature` 单列），仍然不建表。
2. **第二步（仅当 p95 超预算）**：落 L1 的 4 张统计表。此时收益是可量化的（省掉每查询 3–4 ms 桌面量级），风险用 `stale` + 同事务刷新 + 启动期惰性重建兜住。
3. **不建议**为了"看起来完备"直接上 4 张表：按 §12.2 的自检，它消灭的具体失败是"每查询一次 df 统计"，而这个失败在 3–4 ms 量级上尚未被证明存在。

**关于 L1 已冻结这件事**：L1 是预注册，不能在执行轮改公式或改表结构。若决定走第一步，正确做法是**新开一份预注册**（零 schema、FTS5 等价 IDF），把 L1 标为 superseded；不要在两份口径之间混读读数。

### 3.4 预期收益与风险

**收益（定性，不承诺 +N）**：

- 机制上对准的是已登记缺陷 D-01：现行 `COUNT(DISTINCT)` 无 IDF、无长度归一，长文档/多别名节点天然占优。BM25 的两个信号正好压这两类。
- 内部历史读数：Stage-1 在 90 题上 FTS5 bm25 vs 裸 count 为 0.6556 vs 0.5444（+11.1pp）、MRR 3.7×（`docs/kb-fts5-production-plan.md` §1.2）。但那是**词面单腿、90 题、旧口径**，不能直接搬到 129 题融合路。
- 当前融合基线 117/129，天花板只剩 12 题；且判据含"24 章逐章不劣"，收益必须均匀，不能靠一两章换来。**预期更可能是小幅或中性，甚至因候选成员漂移而局部回退。**

**风险（按严重度）**：

1. **候选集漂移**：512 深度按分数截断，分数定义一变，进融合的成员就变；`DenseFusion` 的注释写"成员与长度不变"，但 L1 §1.5 已承认"分数变了成员集可以变"。这是机制的一部分，不是 bug，但会让"同一 query 的融合结果"不可逐题对照，只能看聚合指标。
2. **IDF 变体不一致**：若执行轮沿用 L1 的 `+1` 版，却拿 Stage-1 的 FTS5 数当参照，会得出错误的归因。见 §3.2。
3. **静默统计过期**（若落表）：df 偏大/偏小不会报错，只会让排序慢慢变差。
4. **整片段特征的极端 IDF**：整片段（最长 48 字）大多 df=1，`IDF = ln((N-0.5)/1.5) ≈ ln N - 0.4`（N=3,280 时 7.69，见 L1 §0.2 的表），会主导分数。对"查询恰好等于节点名"是好事，对"查询是自然语言长句"则可能让某个偶然整段匹配压过语义 grams。这是需要在 §7 报告里单列的观察项，不是预先可判的好坏。
5. **设备↔JVM 漂移**：IDF 必须用 `StrictMath.log` + 定点整数和（L1 §1.3 已设计），否则 1 ulp 差异会在并列处翻转名次。
6. **回滚成本**：若落统计表，Room 不能降级，回滚要清库重装；零 schema 方案的回滚只是删一条查询，成本低一个量级。

### 3.5 图书馆检索（FTS4）的独立建议

- 现状是**单字切分**（`CjkTextTokenizer.segment` 在每个汉字间插空格），优点是 1 字查询也能召回，缺点是单字 df 极高、精度差。
- 若要提升，正确方向是**单字 → bigram**（相邻二字，双侧同规则），依据是 §1.3 的 Kwok 1997 与 Lucene `CJKAnalyzer`；索引体积实测约 1.3 倍（§1.1），可接受。
- **不要**为此换 trigram：2 字查询静默失败，而图书馆搜索框最常见的输入就是 2 字词。
- 这一步不涉及 KB 词面腿，也不改融合；应作为独立批次，用图书馆自己的检索门（`PerformanceGateTest.ftsSearchCount`）验收。

---

## §4 未验证项

1. **trigram 体积倍数在设备/真实长文档上的值**：本会话只测了 3,705 条短文档（平均 172 字、`content=''`、桌面 x86）。长材料、含 `content` 存储、设备页大小下的倍数未测。
2. **Kwok 1997 的精确读数**：PDF 返回 403，本文件用的是 OpenAlex `abstract_inverted_index` 重建的摘要；"5% better relevants / 17% better than 1-gram / 3x term space" 的具体实验条件（是否同一分词、是否同一相似度）未读到正文。
3. **Zhou/Hawking 2015 的结论句**：来自 scispace 摘要聚合页，PDF 不可达；"no performance differences overall" 的统计口径（显著性检验？样本 503 对？）未核实。
4. **设备上的查询期 df 延迟**：3.0/4.3 ms 是桌面读数；ARM 设备/模拟器的放大倍数未测，也未在真实 `>500,000` 行特征库上测。
5. **Android framework SQLite 是否有 `ln()`**：`ln()` 需要 3.35.0+ 且 `SQLITE_ENABLE_MATH_FUNCTIONS`；设备 SQLite 3.39.2 的编译开关未探测。本文件因此建议 IDF 在 Kotlin 算。
6. **真实特征表的 df 分布**：本会话用的是按规则重抽的合成特征（373,882 行），未读生产 `knowledge_search_feature` 的真实 df 分布，因此"哪些 2-gram 会触发 IDF 钳位（df > N/2）"未知。
7. **L1 臂的实际结果**：L1 预注册未执行（无设备/JVM 读数）；本文件对收益的判断是机制推演，不是实测。
8. **bundled SQLite 3.50.1 的 trigram 运行时可用性**：仓内只证到 `.so` 含 `trigram` 字符串（`docs/kb-fts5-production-plan.md` §2.1），运行时建表/查询未探针。
9. **`tokenchars '+'` 在 bundled 驱动上的行为**：本会话只在桌面 3.50.4 上验证，未在 3.50.1 bundled 上复跑。
10. **bigram vs trigram 在 129 条金标上的直接 A/B**：没有做过；且金标查询 median 29 字，本来就不是"短查询"，这个 A/B 对回答"短查询"问题的信息量有限。
11. **"2–4 字中文查询"的公开对照评测**：未找到可溯源的研究或工程报告；§1.3 的两篇评测分别是长查询与站点搜索，均不能直接外推。这是本方向最大的证据缺口。
