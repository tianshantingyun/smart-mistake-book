# KB B34 词面腿消融（α / b）：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-04T14:03:03+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`26f2327b0f88d3cad7e076b967b62874e36ca07d`（`git rev-parse HEAD` 实跑）。
> **上游**：`2026-11-03-kb-r11-lexical-leg-attribution.md`（R11：BM25 115/129、tf≡1、192 截断、W0/W1/D0）、`2026-11-03-kb-r10-small-n-eval-stats.md`（R10：精确 McNemar、单次判读、24 章守卫）、`2026-11-02-kb-b33-bm25-prereg.md`（B33：FTS5 等价 BM25 公式，已回滚）。
> **本步唯一新增写盘**：本文件。未改代码/内容/向量/判官；§0/§3 的读数是本会话 `sha256sum` / `rg` 实读，R11 的 62%/avgdl 标注为未重测。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T14:03:03+0800
git rev-parse HEAD                             # 26f2327b0f88d3cad7e076b967b62874e36ca07d
git status --porcelain | wc -l                 # 534
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   433 ??
#   101 M
ls docs/research/2026-11-03-kb-b34-lexical-ablation-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 534 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。
- 本臂触碰面里已有他线 `M`：`DenseRecallRerankerTest.kt`（`d1f10bcd…`）。其余写面 `ProblemOrganizationDao.kt` / `DenseRecallReranker.kt` / `RoomKnowledgeBaseStore.kt` / `RetrievalBenchmark.kt` / `GoldenRetrievalInstrumentedTest.kt` / `ProductionLexicalLegExportTest.kt` 写前干净（§4）。

### 0.2 本臂研究的对象（先拆清楚，避免把实验腿当生产腿）

| 项 | 冻结口径 |
|---|---|
| **实验腿** | B33 的 **FTS5 等价 BM25**（`tf≡1`、`N/avgdl` 查询期实测、`s=ΣIDF·2.2/(1+k1(1−b+b·len/avgdl))`、`k1=1.2`、`b=0.75`）。B33 在 2026-11-03 判定**不采纳并已回滚**，当前生产树**没有** `KnowledgeSearchBm25.kt`；本臂把它作为**实验臂**重建，不改生产词面腿。 |
| **生产词面腿** | `COUNT(DISTINCT feature.search_feature)`，α=0.5（`DenseFusion.ALPHA`），v27 设备融合 **119/129**（R10/R11 引用）。它**不是**本臂的配对基线；只作参考读数。 |
| **B33 BM25 基线** | 同实验腿、α=0.5、b=0.75，B33 历史读数 **115/129**（融合）、词面 100/129。历史 115 **不直接当基线**：必须在同一轮、同设备、同判官、同内容下**重测 B0**（R11 G1）；历史值只作 sanity check（差 >1 题先解释）。 |
| **本臂目的** | 只回答「BM25 词面分在融合排序上是否有可证明的贡献」与「`b=0` 是否把损失归因到长度归一」；**不**回答「生产 COUNT(DISTINCT) 词面腿是否该改」。 |

### 0.3 装置与身份（冻结）

| 项 | 要求 |
|---|---|
| 设备 | 同一台设备/模拟器；记录 `ro.product.model`、`ro.build.version.sdk`、`ro.product.cpu.abi` |
| APK | 每个臂一个 APK；同一 `versionCode` 族、同一签名；记录每个 APK 的 sha256 |
| 内容 | 同一主包 `10ede1bf…`、同一 `.vec` `70329115…`、同一 manifest `version=28`/`ce8edb8f89d91fe7`、同一金标 `eabef102…` |
| 判官/镜像 | 同一 `GoldenRetrievalInstrumentedTest`（`9fae203f…`）、同一 JVM 镜像（`8c2a2ef0…`）；不得换版 |
| 候选域 | 词面 top-512（`MAX_KNOWLEDGE_RECALL_CANDIDATES=512`）；α=1.0/0.75 只改排序权重，**不扩候选集**；b=0 可能改 BM25 分数与 top-512 成员（这是被消融的机制之一） |

- **内容代际**：本文件按当前 **v28 / 主包 `10ede1bf…` / `.vec 70329115…`** 冻结。若 B2b（标题+别名+dense 重打）先执行，主包/sidecar/`.vec` 都会变；本臂必须在新内容上**重新冻结**同一组 B0/A1/A2/A3（同 APK 族/同设备/同判官），不得把 B2b 的内容增益混进 α/b 消融。两臂的 `(w,l)` 不得跨代拼接。

---

## 1. 三组消融（写死）

### 1.1 组定义

| 组 | 配置 | 角色 | 说明 |
|---|---|---|---|
| **B0** | BM25，**α=0.5，b=0.75** | **配对基线**（同轮重测） | `score = 0.5·dense_norm + 0.5·lex_norm`；BM25 长度归一开着 |
| **A1** | BM25，**α=1.0，b=0.75** | **唯一 confirmatory / 可采纳** | 纯 dense：词面只定成员（top-512），不参与排序；检验“词面分在排序上是否有贡献” |
| **A2** | BM25，**α=0.75，b=0.75** | 诊断（exploratory） | 词面 25%；剂量-反应；**不直接采纳** |
| **A3** | BM25，**α=0.5，b=0** | 诊断（exploratory，机制归因） | 去掉长度惩罚；`s=ΣIDF`（分母 `1+k1=2.2` 与分子 `k1+1=2.2` 抵消）；**不直接采纳** |

- **方向**：`score = α·dense_norm + (1−α)·lex_norm`；降词面权重 = **升 α**。α=1.0 不是“去掉词面腿”，词面腿仍作为候选门；它只把词面分从排序里拿掉。
- **只测一次**：A1/A2/A3 各自在 129 条上**只跑一次、只读一次**；不得边看边调 α/b，不得“m=6 看一眼不显著再加到 129”。B0 的重测与装置零漂移检查是**同轮装置**，不是对 A1/A2/A3 的重复假设检验。
- **多重比较控制**：只把 **A1** 当 confirmatory（α=.05）；A2/A3 是 diagnostic，报告精确 McNemar 与效应量，但**不据其直接采纳**；若 A2/A3 过门，只能写“promising，需另立 confirmatory 预注册”。理由：3 个未校正比较的 FWER≈14.3%（R10 §1.3），而 n=129 的 +6 已贴着显著线；先减 family 是 R11 §3.3 的明确建议。

### 1.2 每组的运行与导出（缺一无效）

1. 每个臂单独构建 APK（B0/A1/A2/A3），同设备冷装/清库后跑 `GoldenRetrievalInstrumentedTest` 的 129 条；**每臂只跑一次**。
2. 每臂必须导出 **`perQuery`**：`query_id, chapter, expectedSlug, top5_node_ids, hit(0/1)`。B33 的 `bm25.json` 没有逐题表（R11 §4.3），本臂不重复这个缺口；没有 `perQuery` 的运行**无效**，不得用聚合 115/129 反推 McNemar。
3. 每臂记录：融合 Recall@5、词面 Recall@5、MRR、逐章 `hits/total`、候选集成员率、p50/p95 时延、APK sha、`sqlite_version()`。
4. **G0 装置零漂移（先于 A1/A2/A3）**：B0 同一 APK 连跑两次，`fused` 与 `lexical` 的 top-5 逐题对称差必须都 **0**。B33 的 lexical symdiff=1（rank 6）曾使该臂 NOT JUDGEABLE（R11 F8）；本臂不修装置就不判读。G0 是装置检查，不是第二假设检验；A1/A2/A3 仍各只跑一次。**McNemar 只取第一次 B0 的 `perQuery`**；第二次只用于 symdiff；symdiff=0 时两份逐题命中逐条相同。

---

## 2. 判定（精确 McNemar；写死）

### 2.1 记号

- 对每个臂 vs B0，同一 129 条、top-5 二值命中：
  - `w` = B0 未命中、臂命中（**改善/win**）；
  - `l` = B0 命中、臂未命中（**回归/loss**）；
  - `m = w + l`；**净增 = w − l**。
- 精确双侧 McNemar：

```text
p_exact = min(1, 2 * Σ_{k=0}^{min(w,l)} C(m,k) / 2^m)
```

- 只有 A1 的 p 进入采纳裁定；A2/A3 只报告，不采纳（§1.1）。

### 2.2 精确 p 的算术（先写死，防“+2 就显著”的误读）

| (w,l) | m | 净增 | p_exact |
|---|---:|---:|---:|
| (0,0) | 0 | 0 | 1.0000 |
| (1,0) | 1 | +1 | 1.0000 |
| (2,0) | 2 | +2 | **0.5000** |
| (3,0) | 3 | +3 | **0.2500** |
| (4,0) | 4 | +4 | 0.1250 |
| (5,0) | 5 | +5 | 0.0625 |
| (6,0) | 6 | +6 | **0.03125** |
| (7,0) | 7 | +7 | **0.015625** |
| (8,0) | 8 | +8 | **0.0078125** |
| (3,1) | 4 | +2 | 0.6250 |
| (4,1) | 5 | +3 | 0.3750 |
| (5,1) | 6 | +4 | 0.21875 |
| (6,1) | 7 | +5 | 0.1250 |
| (7,1) | 8 | +6 | 0.0703125 |

- **必须写明的边界**：ask 的“净增 ≥+2 且 p<0.05（等价 (2,0)/(3,0) 等）”中，`(2,0)` 的 p=0.50、`(3,0)` 的 p=0.25，**都不满足 p<0.05**；在零回归线上，**第一档满足 p<0.05 的是 `(6,0)`**。本预注册按 p<0.05 执行；(2,0)/(3,0) 只有在**另立**“实用净增”规则时才可采纳，不得在跑完后把 p<0.05 改掉。

### 2.3 采纳 / 结案 / 回滚（A1 主臂）

| A1 观测 | 判定 | 动作 |
|---|---|---|
| **净增 ≥+2 且 p<0.05** | **采纳 A1（α=1.0，纯 dense 排序）** | 在**BM25 实验臂**内采用 α=1.0；报告必须写明“仅 BM25 实验腿；生产 COUNT(DISTINCT) 词面腿未测试”。 |
| **净增 = 0 且 `l=0`（即 `w=l=0`）** | **保持现状 + 结案** | 保留 α=0.5/b=0.75；登记「BM25 词面分在融合排序上无证明收益（词面腿作为候选门保留）」；这不是“证明无效”，是“无证明收益”。 |
| 净增 = 0 但 `l>0`（w=l>0，churn） | **无净收益、且有回归** | 不采纳；保持默认；登记“no net benefit with churn”，**不**登记“无证明收益结案”（因为存在回归）。 |
| 净增 = +1（如 (1,0)） | 低于 +2 门槛 | 不采纳；保持默认；登记“below practical threshold”。 |
| 净增 ≥+2 但 p≥0.05（如 (3,1)） | 统计不显著 | 不采纳；保持默认；“无证明收益”。 |
| 净增 <0 | 退化 | 不采纳；保持默认；“no proven benefit”。 |

- **“保持现状”** = 生产与实验臂都回到默认 α=0.5/b=0.75；不需要改任何生产常量。
- **“采纳 A1”** 的作用域仅限 BM25 实验臂；**不得**据此改生产 `DenseFusion.ALPHA` 或 `COUNT(DISTINCT)` 词面腿——生产腿未被本臂测试。若要把“词面只做 gate”上生产，需另立预注册（用生产腿做 α 消融）。
- A2/A3 的判定只写“diagnostic”：若自身 `净增 ≥+2 且 p<0.05`，写“promising（未采纳，需 confirmatory 复现）”；若净增 0 且 l=0，写“该诊断点无变化”；其余写“no proven benefit”。**A2/A3 不触发生产/实验臂采纳。**

### 2.4 24 章逐章（只作守卫）

- 只报每章 `hits/total`、净变化、是否单章驱动；**不报逐章 p 值**，不用 24 个星号做总体结论（R10 §2.6：14/24 章 ≤3 条，逐章精确检验结构性不可达）。
- **守卫 1（硬）**：A1 任一章损失 **≥2 条** → **阻断采纳**，转人工复核；即使主集 p<0.05 也不采纳。
- **守卫 2（标记）**：A1 净增益中单一章贡献 **≥50%** → 标“单章驱动”，需 fresh-query/预注册 holdout 复现；无 fresh 集时只能 `provisional`。
- **守卫 3**：cluster bootstrap 只作敏感性；若逐题精确 A1 通过而 cluster CI 跨 0，标 `cluster-sensitive`，不直接采纳。

---

## 3. 机制记录（tf≡1 / len / 192 截断；写死并披露）

### 3.1 本仓 BM25 的实际形状

| 事实 | 值 / 机制 | 出处 |
|---|---|---|
| `tf ≡ 1` | 特征表主键 `(subject, search_feature, knowledge_node_id)` ⇒ 同一 (节点, 特征) 只有一行；BM25 的 `f(q,D)` 恒为 1，公式退化为 `ΣIDF·(k1+1)/(1+k1(1−b+b·len/avgdl))` | schema `73.json` 的 `createSql`；`KnowledgeSearchFeatureEntity.kt`；B33 §1.1 |
| `len(n)` | 该节点在 `knowledge_search_feature` 的**全部特征行数**（不是匹配数）；受 `MAX_NODE_FEATURES=192` 封顶 | `KnowledgeSearchFeatureExtractor.kt:7-10,28`；B33 §1.1 |
| `avgdl` | 全语料特征行数 / 被索引节点数；B33 实测 546,872 / 3,718 = **147.09**（R11 引用，**本会话未重测**） | R11 F6/F11；B33 §0.2 |
| 长度因子 | `C(len)=2.2/(1+1.2(1−0.75+0.75·len/147.09))`；`len=50` → 1.370，`len=192` → 0.889（约 1.54×） | R11 F6；B33 §1.1 |
| `b=0` | `C=1`；`s=ΣIDF`（分子 `k1+1=2.2` 与分母 `1+k1=2.2` 抵消）；**只**去掉长度归一，不改变 `tf≡1`/截断/IDF | 本会话公式推导；B33 §1.1 |
| 192 截断 | `texts = [canonicalName] + aliases.sorted() + [boundaryMarkdown]`；片段 >16 时保留 `last + first + balanced middle`；16 条片段流**轮转**取特征到 192；每片段先整片段（2–48 字）再 2/3-gram | `KnowledgeSearchFeatureExtractor.kt:44-56,71-91,95-102`；R11 F2 |
| 62% | 包内 **62% 节点片段数 >16**（KD-24 已登记）；别名按**字典序**排序，谁活下来与查询相关性无关 | `KnowledgeSearchFeatureExtractor.kt:17-22`；R11 F11/F2 |

### 3.2 必须披露的解释边界（写进批报）

1. **`b=0` 只去掉长度因子**：它**不能**恢复被 16 片段/192 特征截掉的别名，也不能改变 `aliases.sorted()` 的字典序选择。因此：
   - 若 A3（b=0）有增益 → 只能说“在本实现下，长度归一贡献了这部分损失”；
   - 若 A3 无增益 → **不能**推出“BM25 的长度归一无用”或“BM25 算法无效”，因为截断/`tf≡1`/字典序仍在。
2. **`tf≡1` 是表结构的结果**，不是 BM25 的标准输入；本仓的 BM25 不是教科书 BM25，任何结论都要带“本仓实现”限定。
3. **62% 与 avgdl 是 R11/B33 的既有读数**，本会话未重测；执行轮若复算，记录实测值与 `NOT_RERUN`/实测标记。
4. **α=1.0 不是“证明词面腿无用”**：词面腿仍是候选门；它只证明“在本实现/本语料/129 条上，词面分对排序没有可证明的净贡献”。生产 COUNT(DISTINCT) 腿不在本臂结论内。

---

## 4. 回滚（参数复位 + 触碰清单 + pre/post sha）

### 4.1 参数默认值（必须可复位）

| 参数 | 默认值 | 本臂取值 | 复位动作 |
|---|---:|---|---|
| `DenseFusion.ALPHA`（生产） | **0.5** | 不改生产；实验臂 B0/A1/A2/A3 分别 0.5/1.0/0.75/0.5 | 生产常量保持 0.5；实验 APK 丢弃即复位 |
| BM25 `k1` | **1.2** | 1.2（固定，不扫） | 不变 |
| BM25 `b` | **0.75** | B0/A1/A2=0.75；A3=0.0 | 实验代码恢复 `b=0.75`；生产无 BM25 |
| `MAX_SEARCH_FRAGMENTS` / `MAX_NODE_FEATURES` | **16 / 192** | 不变（本臂不消融截断） | 不变 |

- 参数必须由**单一常量/构造参数**控制，禁止散落在多处；每个实验 APK 只改这一处。
- 回滚 = 恢复 §4.2 的 pre-sha + 删除 §4.3 的新增实验文件；生产 `DenseFusion.ALPHA` 从未改变，无需“改回”。

### 4.2 既有文件（写前 pre-sha；`M` 为他线，不得回滚）

| # | 路径 | 预期动作 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/database/.../dao/ProblemOrganizationDao.kt` | BM25 查询路径（实验） | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 2 | `core/database/.../DenseRecallReranker.kt` | 实验 alpha/b 注入 | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 3 | `core/database/.../RoomKnowledgeBaseStore.kt` | 词面候选/分数接线 | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` |
| 4 | `core/data/src/test/.../RetrievalBenchmark.kt` | 实验参数/逐题导出 | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |
| 5 | `core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt` | 逐题 `perQuery` 导出（判官） | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` |
| 6 | `core/data/src/test/.../ProductionLexicalLegExportTest.kt` | 词面腿导出/锚（仅实验需要时） | `efefae1a29baf616570ccc3acea147e3f2604f2e384bb6dcf69e69ada66a745c` |
| 7 | `core/data/src/test/.../DenseRecallRerankerTest.kt` | 候选/分数断言（他线 `M`，在其上追加） | `d1f10bcd9a69a81c5d2f1d7e4c35ff99009203c473fbdf8bd9b559d38e7f9095`（`M`） |

### 4.3 新增实验文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/database/.../KnowledgeSearchBm25.kt` | 重建 B33 BM25 实验实现；`b`/`k1` 可注入（默认 0.75/1.2） |
| 2 | `core/database/src/test/.../Bm25LexicalScoreTest.kt` | 公式/钳位/排序单测 |
| 3 | `core/data/src/test/.../Bm25MirrorParityTest.kt` | 设备/JVM 镜像逐题对拍 |

### 4.4 零触碰（本臂不改内容/向量/截断）

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 2 | `.../bge-small-zh-int8.vec.json` | `f0c947950554a7f368fcdfbe3e94e3352d31cfa4653d6ff83da9b082012070e8`（`M`，layoutSha 臂） |
| 3 | `.../moe-2025-four-subjects-v1.json` | `10ede1bf449de38ff21a53d1a13468a82b765dd0d0852dde4af770a1b8ef6670` |
| 4 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 5 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 6 | `core/database/.../entity/KnowledgeSearchFeatureEntity.kt` | `9da364a46c629523e8c608172d04e06a0f771c44e0f3a43b1d29eab266791213` |
| 7 | `core/database/schemas/.../73.json` | `c7e7586a4eaab6d7309e91f2b715a74229369d95bc11966cfe96a94755f39f1e` |
| 8 | `core/data/.../dense/DenseFusion.kt` | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71`（`order(alpha)` 已有；生产 ALPHA 0.5 不变） |

### 4.5 回滚步骤

1. 不得整树 `git checkout` / `stash` / `reset`；用备份覆盖 §4.2 的 7 件，删除 §4.3 的 3 件；`DenseRecallRerankerTest.kt` 按其 `M` 的 pre-sha 恢复（不回到 HEAD）。
2. 参数复位：实验代码 `b=0.75`、`k1=1.2`；生产 `DenseFusion.ALPHA=0.5`；无 schema/迁移/内容变化。
3. 设备：`adb install -r` 默认参数 APK（或直接卸载实验 APK 重装基线）；**不 `pm clear`**，学生数据零触碰。
4. 复核：`.vec` `70329115…`、`.vec.json` `f0c94795…`、主包 `10ede1bf…`、金标 `eabef102…` 未变；`check_asset` 11/11；`DenseAssetProvenanceTest` / `GoldenRetrievalJvmTest` 按基线复核。

---

## 5. 未做 / 未验证（写作时点）

- **未改代码/未跑 Gradle/设备/判官**：§1–§4 是冻结设计；B0/A1/A2/A3 与 G0 全部 `NOT_RUN`。
- **BM25 实验腿当前不在生产树**：B33 已回滚；§4.3 的 `KnowledgeSearchBm25.kt` 是实现目标，不是现有文件。执行轮必须先按 B33 §1.1 重建并对拍（`Bm25MirrorParityTest`），否则不得跑三组。
- **115/129 是 B33 历史读数**：不同轮、不同装置；本臂必须同轮重测 B0，历史 115 只作 sanity check。
- **62% / avgdl=147.09 / 546,872 行 / 3,718 节点**：R11/B33 的既有读数，本会话未重测；执行轮按 DB 实测复算并记录。
- **`(2,0)/(3,0)` 的 p 值**：0.50/0.25，**不满足 p<0.05**；ask 的“等价”括注在精确 McNemar 下不成立。若 owner 要 +2/+3 也可采纳，必须另立“实用净增”规则；本预注册按 p<0.05 执行。
- **A2/A3 是否也应可采纳**：本预注册只把 A1 当 confirmatory；A2/A3 是 diagnostic。若 owner 要三组都可采纳，必须改用 Holm（m=3，第一阈值 0.0167；+6 的 0.03125 也过不了第一关）或另立预注册。
- **单章驱动/fresh 复现**：本仓只有一套 129 条金标；单章 ≥50% 时只能 `provisional`，除非另立 fresh 集。
- **生产 COUNT(DISTINCT) 腿**：本臂不测试；不得把 A1 的结论外推到生产 α/词面腿。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；§4.2/§4.3 逐文件 pre/post sha；B0 同轮重测与 G0 两次 fused/lexical symdiff；A1/A2/A3 各一次运行的 APK sha、`perQuery` 路径、融合/词面 Recall、MRR、逐章表、候选成员率、p50/p95；每组 `(w,l)`、精确 p、净增、A1 的采纳/结案/回滚判词；A2/A3 的 diagnostic 标记；24 章守卫（任一章损失 ≥2、单章 ≥50%）；参数复位与零触碰 sha。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
