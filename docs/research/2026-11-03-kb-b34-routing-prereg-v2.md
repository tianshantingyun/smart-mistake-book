# KB B34 查询类型路由 v2（线索表冻结 + 交付层材料类型匹配率 + 可执行装置）：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§6 写死；执行轮只允许在文末追加 §8，不得回改 §1–§6。
> **写作时点**：2026-11-04T15:18:39+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`26f2327b0f88d3cad7e076b967b62874e36ca07d`（`git rev-parse HEAD` 实跑）。
> **上游**：`2026-11-02-kb-b33-query-routing-prereg.md`（v1：线索表/四张秩表/旧路径保留/F1–F3；下称 **v1**）、`2026-11-03-kb-r10-small-n-eval-stats.md`（R10：单次判读、24 章守卫）、`2026-10-25-kb-final-acceptance-criteria-v1.md`（AC：F1/F2/F3 定义）。
> **v1 失败点（ask 给定）**：上次执行**未按 v1 §5.2/§5.3 落盘并执行装置**，读数不可判读。v2 不再依赖执行者记忆：把装置写成一个**可执行的、先落盘的脚本**，脚本 `--self-check`/`--static` 过门后才允许设备运行（§5）。
> **本步唯一新增写盘**：本文件。未改代码/内容/测量脚本；§0/§1 的读数是本会话 `python -I` / `sha256sum` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T15:18:39+0800
git rev-parse HEAD                             # 26f2327b0f88d3cad7e076b967b62874e36ca07d
git status --porcelain | wc -l                 # 538
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   437 ??
#   101 M
ls docs/research/2026-11-03-kb-b34-routing-prereg-v2.md
#   No such file or directory  → 本次新建，只写一次
```

- 538 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。
- v1 的代码（`KnowledgeQueryType.kt` / `KnowledgeMaterialTypeRanking.kt` / ranked 端口）在当前树**不存在**（v1 只是预注册）；v2 仍按 v1 §5.1/§5.3 的写面落地，并新增 §5 的脚本。v1 的 `retest_usage_r1.py` 存在（sha `b9f7375d…`），但 v2 的装置走**新脚本**，不依赖它及其仓外 `audit_knowledge_read.py`。

### 0.2 交付层在哪（与 v1 §0.2 同一层）

| 事实 | 出处 |
|---|---|
| `KNOWLEDGE_READ` 先取 512 深度候选 → 截前 5 节点 → 每节点 1 条材料 → 渲染 | `core/data/.../study/RoomTutorToolRunner.kt:421-490` |
| 展示条数 5、正文 120 字 | `RoomTutorToolRunner.kt:517-528` |
| 每节点最佳材料：`role → material_type → title → material_id`，相关子查询 `LIMIT 1` | `core/database/.../dao/KnowledgeTeachingMaterialDao.kt:70-126`（CASE `:106-115`） |
| 学生原话输入 | Respond `TutorRespondInput.studentMessage`；Plan `questionDocument.title` + `Paragraph.markdown`（`TutorTasks.kt:418-431,218-234`；`StructuredContent.kt:12-16,22-27`） |
| 另一套顺序（不得混用） | `reTeachPriority`（`RoomTutorTeachingReferenceRepository.kt:52-82`） |

- 本臂只改「节点已定、材料未选」这一层：节点集合/顺序/top-5/512 深度/召回/融合**一个都不动**。

### 0.3 当前内容与静态读数（本会话实算）

| 项 | 值 |
|---|---:|
| 2025 sidecar 材料 / 绑定 | **64,853**（全部有 `bindings`；2020 的 2 条不在本臂静态表） |
| 类型分布 | `CONCEPT_EXPLANATION` **35,486** / `METHOD_MODEL` **25,437** / `MISCONCEPTION_GUIDE` **3,930**；其它类型 **0** |
| 节点 / 多类型节点 | **3,280 / 2,231（68.0%）** |
| 金标分类（§1 词表） | `CONCEPT` **67** / `METHOD` **7** / `COMPUTATION` **12** / `UNCLASSIFIED` **43**（6 tie + 37 零命中） |
| 分类且有期望节点材料 | **86 / 86** |
| 期望节点「类型匹配」identity → routed | **71/86 → 86/86**；变化 **15**（`+15`；+17.44 pp） |
| 逐类 identity → routed | CONCEPT **67/67 → 67/67**；METHOD **3/7 → 7/7**；COMPUTATION **1/12 → 12/12** |

- 15 条变化的下标（0 基，金标顺序）：`[7, 16, 21, 33, 50, 69, 72, 78, 85, 88, 101, 102, 122, 127, 128]`，全部 `CONCEPT_EXPLANATION → METHOD_MODEL`。
- 43 条 UNCLASSIFIED 的下标见 §3.1；其 identity/routed 选材必须**逐题完全不变**。
- **内容代际**：本文件按当前 **v28 / 主包 `10ede1bf…` / 2025 sidecar 64,853 材料** 冻结。若 B2b（标题+别名+dense 重打）或其它内容臂先执行，标题/别名/材料会变；本臂必须在**新内容**上重算 §1.3 的分布、§2.3 的 71/86→86/86 与 15 条变化，并重新冻结；不得把 B2b 的内容变化混进交付指标，也不得跨代拼接 `device.tsv`。

### 0.4 与 v1 的关系（v2 改了什么、没改什么）

| 项 | v1 | v2 |
|---|---|---|
| 线索表 | 20/22/18 词 | **逐词不变，冻结哈希 `ed21d6d2…`**（§1.2） |
| 四张秩表 | 8×4 | **逐值不变**（§2.2） |
| 非命中零变化 | 分类/端口/输出三层 | **升级为逐题断言**：43 条 UNCLASSIFIED 的 5 个槽位选材与渲染逐字相同（§3） |
| 判读 | 主集不劣 + 24 章 + 零漂移 + F1/F2/F3 不变差 | **不劣（无回归）+ 交付指标（材料类型匹配率提升）+ 零漂移**；F1/F2/F3 仍作无回归守卫（§4） |
| 装置 | §5.2/§5.3 的清单 | **一个可执行脚本 + 设备导出**；脚本先落盘/hash，self-check/static 过门后才跑设备（§5） |
| 臂数 | A/B 两臂 | **单臂单测一次**：同一 run 内同一 top-5 节点分别跑 identity 与 routed 选材；不做第二个 APK/第二次设备运行（§4.1） |

---

## 1. 线索表（冻结；三类各列全）

### 1.1 分类器输入与匹配（与 v1 逐字相同）

| 项 | 冻结值 |
|---|---|
| 输入 | **学生原话**：Respond `studentMessage`；Plan `title` + 全部 `Paragraph.markdown`（按块序 `\n` 连接；`Formula` 不参与）。经 `RoomTutorToolRunner.Context.studentQueryText` 传入交付层 |
| 空值 | `null` / 空白 → `UNCLASSIFIED` |
| 归一化 | 仅 `lowercase(Locale.ROOT)`；不做全半角/标点/分词/同义扩展 |
| 匹配 | 每条词做**子串包含**（`text.contains(word)`）；同一词条一次查询只计 1 |
| 计分 | 每类得分 = 命中**distinct 词条数** |
| 词条长度 | 全部 ≥2 字 |
| 判定 | **严格最大**；0 命中或最高分并列（≥2 类同分）→ `UNCLASSIFIED` |
| 输出 | `enum class KnowledgeQueryType { CONCEPT, METHOD, COMPUTATION }`；`classify(text): KnowledgeQueryType?`（null=UNCLASSIFIED） |

### 1.2 三类线索表（逐词列出；冻结后不得增删改）

**CONCEPT（20 词，逐词）**

`为什么`、`是什么`、`什么区别`、`区别`、`概念`、`含义`、`定义`、`原理`、`原因`、`作用`、`属于`、`称为`、`叫作`、`本质`、`判断`、`是否`、`能不能`、`对不对`、`一回事`、`关系`

**METHOD（22 词，逐词）**

`方法`、`步骤`、`怎么写`、`怎么求`、`怎么判断`、`怎么算`、`如何`、`怎样`、`怎么做`、`几种`、`哪几种`、`画法`、`写法`、`证明`、`构造`、`换元`、`配方`、`待定系数`、`归纳`、`分类讨论`、`数形结合`、`解法`

**COMPUTATION（18 词，逐词）**

`计算`、`求值`、`多少`、`质量比`、`物质的量`、`浓度`、`体积`、`面积`、`长度`、`角度`、`数值`、`结果`、`等于`、`算出`、`取值`、`最值`、`概率`、`比例`

**冻结哈希**：`json.dumps({"CONCEPT":[...],"METHOD":[...],"COMPUTATION":[...]}, ensure_ascii=False, separators=(',',':'))` 的 sha256 = **`ed21d6d26831fb22b688b93465a75e606a4eed686312e6ccd77f8897522a4c15`**（20/22/18 词，本会话实算）。

- **轻度测试集泄漏登记**（沿用 v1）：写表前预览过金标前 30 条题面以判断语料风格；写表后只统计分布，未按分布增删词。冻结后不得再改；§7 登记残余风险。

### 1.3 分类分布与冻结下标

| 项 | 值 |
|---|---|
| 分布 | CONCEPT **67** / METHOD **7** / COMPUTATION **12** / UNCLASSIFIED **43** |
| tie（6） | `[86, 106, 112, 113, 121, 123]` |
| 零命中（37） | `[0, 2, 3, 6, 9, 15, 23, 26, 35, 37, 44, 45, 46, 47, 51, 52, 58, 59, 60, 61, 64, 66, 70, 71, 76, 80, 82, 83, 93, 99, 105, 107, 109, 114, 116, 119, 124]` |
| 43 条索引哈希 | `sha256("86,106,112,113,121,123,0,2,...,124")` = **`099d21ab10b6899a06a0dbc22698229a63e59ec650fad54cdafe404957f73e37`** |

- 分类器测试必须逐条复现该分布与两组下标；任一不符 → ABORT（词表/金标漂移）。

---

## 2. 类型 → 交付层材料类型加权（冻结）

### 2.1 加权边界

- 只在「节点已定、材料未选」层生效；节点集合/顺序/top-5/512/召回/融合**零改**。
- `role` 仍是第一键；类型秩只在同角色内重排；`title`、`material_id` 仍是第三、第四键。
- 旧方法 `readBestTeachingMaterialPerNode` **一字不改**；`UNCLASSIFIED` 直接调用旧方法（同一 SQL、同一参数）。
- 分类命中时调用新方法 `readBestTeachingMaterialPerNodeRanked(subject, ids, r0..r6)`；SQL 与旧方法逐字相同，只把 7 个 CASE 字面量换成 7 个绑定参数。
- **不得**写回 `reTeachPriority`（另一套消费方；DAO KDoc `:70-79`）。

### 2.2 四张类型秩表（逐值冻结；数字越小越优先）

| `material_type` | UNCLASSIFIED（identity） | CONCEPT | METHOD | COMPUTATION |
|---|---:|---:|---:|---:|
| `CONCEPT_EXPLANATION` | 0 | 0 | 3 | 4 |
| `METHOD_MODEL` | 1 | 2 | 0 | 1 |
| `DERIVATION` | 2 | 1 | 2 | 2 |
| `WORKED_EXAMPLE` | 3 | 3 | 1 | 0 |
| `REPRESENTATION_GUIDE` | 4 | 4 | 4 | 5 |
| `MISCONCEPTION_GUIDE` | 5 | 5 | 5 | 6 |
| `COMPLETE_SOLUTION` | 6 | 6 | 6 | 3 |
| 未知类型（ELSE） | 7 | 7 | 7 | 7 |

- 四行都是 `0..6` 的排列；UNCLASSIFIED 行与现行 SQL 逐值相同。
- 现包只有 3 种类型：CONCEPT 行与 identity 恒等；METHOD/COMPUTATION 只把 `METHOD_MODEL` 提到 `CONCEPT_EXPLANATION` 之前；`MISCONCEPTION_GUIDE` 在所有类都排第 5/6，**没有类会把误区提到首位**（F3 不劣的结构性理由）。

### 2.3 交付指标的定义（v2 新增，冻结）

对每个查询/槽位（query × 5 节点）：

1. 若查询 `classify` 为 `UNCLASSIFIED` 或节点无材料 ⇒ **不计入**匹配率分母（走 identity 零变化断言）。
2. `preferred_available_type` = 该节点现有材料类型中，在**该查询类的秩表**里秩最小的那一个（不是固定的 rank 0 类型；现包无 `WORKED_EXAMPLE`，所以 COMPUTATION 的 preferred available 是 `METHOD_MODEL`）。
3. `match` = 该槽位**被选中的材料类型** == `preferred_available_type`。
4. 匹配率 = `match 槽位数 / 计入分母的槽位数`；分别报总体、CONCEPT/METHOD/COMPUTATION 三类。
5. 基线 = 同一 run 内 identity 选材的匹配率；routed = 同一 run 内 ranked 选材的匹配率；**Δ = routed − identity**。

**静态期望节点（preflight，非设备读数）**：对每条分类查询的 `expectedSlug` 节点取材料，按同一键选 identity/routed，得到：

| 类 | n | identity 匹配 | routed 匹配 | Δ |
|---|---:|---:|---:|---:|
| CONCEPT | 67 | 67 | 67 | 0 |
| METHOD | 7 | 3 | 7 | **+4** |
| COMPUTATION | 12 | 1 | 12 | **+11** |
| **合计** | **86** | **71（82.56%）** | **86（100%）** | **+15（+17.44 pp）** |

- 15 条变化下标 = `[7,16,21,33,50,69,72,78,85,88,101,102,122,127,128]`，哈希 `ca0acf43e3a2adafb4e60dbee7ab6af45ccd30cad127465549cc54d1a0fb49c4`。
- 这是**结构期望**：脚本的 `--static` 必须逐值复现 71/86 → 86/86、15 条变化；不符 → ABORT，不得跑设备。
- 设备 per-slot 的 Δ 可能大于 15（每条查询有 5 个槽位）；采纳要求设备 Δ>0 且逐类 routed ≥ identity，静态 15 是 preflight 地板/结构证明。

### 2.4 不改召回/融合（违例判据）

- 零触碰：召回 SQL/512、`DenseFusion`、`DenseRecallReranker`、`KnowledgeSearchFeatureExtractor`、`KNOWLEDGE_READ_NODE_LIMIT=5`、120 字、2k 预算、内容包/manifest/dense/金标。
- `SCOPE_VIOLATION`：同一 run 的前 5 节点 id 序列与 v1/基线逐题相同；路由只换材料，不改节点。若节点序列变了，规则全部 `NOT_RUN`。

---

## 3. 非命中查询零变化（逐题断言）

### 3.1 43 条 UNCLASSIFIED（逐题）

- 索引：§1.3 的 6 tie + 37 零命中，共 **43**；哈希 `099d21ab…`。
- **分类层**：`classify(text) == null`（逐条）。
- **端口层**：UNCLASSIFIED 必须调用**旧方法** `readBestKnowledgeTeachingMaterialPerNode`，**不得**调用 `...Ranked`；分类命中时相反（记录调用的 fake 端口测试）。
- **设备逐题**：对 43 条中的每一条、5 个槽位：`identity_material_id == routed_material_id`、`identity_type == routed_type`、`rendered_identity == rendered_routed`（逐字）。**43/43 全等**；任一不等 → 本臂越界/实现错误，规则全部 `NOT_RUN`。
- **静态逐题**：脚本对 43 条期望节点同样断言 identity==routed（选材 id/type 与渲染），0 变化。

### 3.2 86 条分类查询

- 静态期望节点：15 条变化（§2.3）为**预期**；其余 71 条 identity 已匹配，选材应与 routed 相同（title/material_id 同键）。脚本断言 `changed == {15 条}`，多一条/少一条都 ABORT。
- 设备 per-slot：分类查询允许变化（这是交付指标），但必须满足 §2.3 的匹配率提升与 §4 的无回归。

### 3.3 逐题导出（装置要求）

设备测试必须导出 `device.tsv`，每行一个 (query, slot)：

```
query_id  chapter  expectedSlug  slot  node_id  identity_material_id  identity_type  routed_material_id  routed_type  rendered_identity_sha256  rendered_routed_sha256
```

- 没有该导出 ⇒ 43 条零变化无法逐题断言，判读无效。
- 渲染字符串的 sha256 用同一序列化；UNCLASSIFIED 的 identity/routed 渲染必须逐字节相同。

---

## 4. 判读（单臂单测一次；写死）

### 4.1 单臂单测

- **一个 routed APK、一次设备 run**：同一 run 内取同一 top-5 节点，对每个槽位分别调用旧方法（identity）与新方法（routed）；两列来自**同一次**检索，不做 A/B 第二个 APK、不做第二次设备运行。
- 脚本的 `--static` 是 preflight 镜像，不是第二次假设检验；设备 run 是唯一交付读数。
- 只测一次：不得边看边改词表/秩表/匹配率定义；不得重跑挑结果。装置失败（连接/夹具）按 §5.4 记录后重跑，不得把多次运行拼成一个读数。

### 4.2 不劣（无回归）

| # | 项 | 判据 |
|---:|---|---|
| R1 | 检索不劣 | 同一 run 的 top-5 节点序列与基线逐题相同（路由不改召回）；融合 Recall@5/MRR 与基线同值。若序列变了 → `SCOPE_VIOLATION`，不判读 |
| R2 | F1 覆盖 | routed == identity（每个有材料的槽位仍取到 1 条）；任何下降 → 不采纳 |
| R3 | F2 期望节点有材料 | routed == identity（expectedSlug 在 top-5 且拿到材料的查询数不变） |
| R4 | F3 误区槽位 | routed **≤** identity，且 ≤20%；任何上升 → 不采纳 |
| R5 | 43 条 UNCLASSIFIED | §3.1 逐题全等；任一不等 → 不采纳 |

- F1/F2/F3 的 identity 值是**同一 run** 的基线，不用历史 117/129/8 代替；当前内容（v27/v28）的基线以本 run 为准。

### 4.3 交付指标（材料类型匹配率提升）

- 设备 per-slot：`Δ_overall = routed_match_rate − identity_match_rate` **> 0**。
- 逐类：`routed_match_rate_c ≥ identity_match_rate_c`（CONCEPT/METHOD/COMPUTATION 三类都不得下降）；报告每类 n、match 数、率、Δ。
- 静态 preflight：71/86 → 86/86、15 条变化必须逐值命中；否则 ABORT。
- 若 `Δ_overall = 0` 且 R1–R5 全绿 ⇒ **保持现状 + 登记「交付层类型加权无证明收益」结案**（不是“证明无效”）。
- 若 `Δ_overall > 0` 但某一类下降 ⇒ 不采纳（局部回归）。

### 4.4 零漂移

| # | 检查 | 通过条件 |
|---:|---|---|
| Z1 | 静态 ↔ 设备逐槽位 | 脚本 static 与 `device.tsv` 的 identity/routed 选材 id/type **0 mismatch**（129×5 槽位；无材料的槽位按规则一致） |
| Z2 | 设备 ↔ JVM 镜像 | fused 与 lexical 的 MISS query 集合 symmetric difference **0**（沿用 v1/AC 装置；非 0 = 装置缺陷，不判读） |
| Z3 | 同 run 两列 | identity/routed 使用同一 top-5 节点序列；序列逐题相同 |

- Z1 是 v2 的新装置门：它直接证明脚本镜像与 Kotlin 实现同口径；上次失败的“装置未按 §5.2/§5.3 执行”在这里被变成**可执行断言**。
- 任一门非 0 → 不判读；修复后按 §5.4 重跑，不得用单方读数出结论。

### 4.5 24 章守卫（沿用 R10）

- 逐章报 `hits/total`、净变化；**不报逐章 p 值**。
- 任一章损失 **≥2 条** → 阻断采纳；单章贡献 ≥50% 的净增益 → 标“单章驱动”，需 fresh 复现。
- 本臂不改召回，逐章命中应与基线逐章相同；若不同，先查 `SCOPE_VIOLATION`。

---

## 5. 装置脚本（可执行、先落盘、唯一测量路径）

### 5.1 为什么单列

v1 的 §5.2/§5.3 是**清单**（`retest_usage_r1.py` + allocation test + 6 个新文件），执行轮没有先落盘/按序执行，导致读数不可判读（ask 给定）。v2 把全部步骤收进 **一个脚本**，并要求：

1. 脚本先写到 `tools/kb_coverage/routing_eval_v2.py`（tracked），`sha256sum` 落 `build/agent-batch34/routing/script.sha256`；
2. 脚本 `--self-check` / `--static` 过门后，才允许构建/安装/跑设备；
3. 所有判读只读脚本输出（`static.json` / `device.json` / `report.json`），不接受手写/临时 SQL 的读数。

### 5.2 脚本 CLI（冻结）

```bash
# 0) 先落盘并记录 sha（在任何运行之前）
sha256sum tools/kb_coverage/routing_eval_v2.py | tee build/agent-batch34/routing/script.sha256

# 1) 输入/词表/分布/静态期望自检（不跑设备）
python -I tools/kb_coverage/routing_eval_v2.py --self-check

# 2) 静态镜像（SQLite 逐字 SQL；输出 static.json）
python -I tools/kb_coverage/routing_eval_v2.py --static \
  --out build/agent-batch34/routing/static.json

# 3) 设备 run（一次）后，读导出（输出 device.json）
python -I tools/kb_coverage/routing_eval_v2.py --device \
  build/agent-batch34/routing/device.tsv \
  --out build/agent-batch34/routing/device.json

# 4) 合并判读（输出 report.json；exit 0=PASS，1=FAIL/NOT_JUDGEABLE）
python -I tools/kb_coverage/routing_eval_v2.py --report \
  build/agent-batch34/routing/static.json \
  build/agent-batch34/routing/device.json \
  --out build/agent-batch34/routing/report.json
```

### 5.3 脚本必须做的断言（缺一即失败）

| # | 断言 | 失败动作 |
|---:|---|---|
| S1 | 金标 sha `eabef102…`、主包 sha `10ede1bf…`、sidecar 26 个、manifest `28/ce8edb8f89d91fe7` | exit 1，ABORT |
| S2 | 词表哈希 `ed21d6d2…`、20/22/18 词、无重复、全部 ≥2 字 | exit 1 |
| S3 | 分类分布 67/7/12/43；tie/零命中下标哈希 `099d21ab…` | exit 1 |
| S4 | 静态期望节点：86 条有材料；identity 71/86、routed 86/86；15 条变化且下标哈希 `ca0acf43…` | exit 1 |
| S5 | 43 条 UNCLASSIFIED 静态 identity==routed（id/type/渲染） | exit 1 |
| S6 | 设备 `device.tsv` 129 题 × 5 槽全在；query/slot 不重不漏 | exit 1 |
| S7 | Z1 静态↔设备 0 mismatch；Z2 fused/lexical symdiff 0；Z3 序列相同 | 记 `NOT_JUDGEABLE` |
| S8 | R1–R5 无回归；`Δ_overall>0`；逐类不降 | exit 1（不采纳） |
| S9 | F1/F2/F3 的 identity/routed 两列与 24 章表完整输出 | exit 1（缺列） |

- 脚本只读仓库内输入与 `device.tsv`；**不依赖** v1 的仓外 `audit_knowledge_read.py`。
- 脚本的 `--static` 用 SQLite 逐字执行 v1 §2.3 的 identity SQL 与 ranked SQL；与设备 Kotlin 实现独立，Z1 对拍 0 mismatch 才算同口径。

### 5.4 设备测试与导出

- 设备测试：`core/data/src/androidTest/.../knowledge/TeachingMaterialAllocationInstrumentedTest.kt` 扩展一个 `deliveryRoutingExport` 用例（或同文件内的导出方法），对 129 条跑真实检索/交付路径，输出 §3.3 的 `device.tsv`；**一次 run**。
- 只允许在 S1–S5 通过后构建/安装/运行；构建/安装/运行的命令、APK sha、设备指纹、`sqlite_version()` 写入 §8。
- 设备失败（断言/连接/夹具）不得改脚本的冻结值；按原配置重跑并记录，Z1/S8 只取一次有效 run。

---

## 6. 回滚（触碰清单 + pre/post sha）

### 6.1 A. 生产源码（v1 §5.1 的 6 件；pre-sha 为当前工作树值）

| # | 路径 | 预期动作 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/data/.../model/TutorToolContext.kt` | `Context.studentQueryText` | `772a77c56983af29d201439ff6d850de73757dffb4d93f1dd8053d1195b3386f` |
| 2 | `core/data/.../study/RoomTutorToolRunner.kt` | 分类 → 旧/新方法二选一 | `2de1e68f71bc0615ce073fa797086dc63178744cd9e7e153c275e26400b37fd4` |
| 3 | `core/database/.../port/KnowledgePort.kt` | 新增 ranked 端口 | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` |
| 4 | `core/database/.../RoomKnowledgeBaseStore.kt` | 实现 ranked（7 秩参数） | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` |
| 5 | `core/database/.../RoomStudyDatabase.kt` | 委托 ranked | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` |
| 6 | `core/database/.../dao/KnowledgeTeachingMaterialDao.kt` | 新增 ranked（CASE 7 参数）；旧方法一字不改 | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` |

### 6.2 B. 测试/装置（v1 §5.2 + v2 扩展）

| # | 路径 | 预期动作 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/data/src/androidTest/.../knowledge/TeachingMaterialAllocationInstrumentedTest.kt` | 补「旧方法未改」断言 + v2 的 `device.tsv` 导出 | `f52526d15585250f7410a8306db90990a67ad72a2f53dd4505133be3ffab85a1` |
| 2 | `tools/kb_coverage/retest_usage_r1.py` | **零改**（v2 用新脚本；v1 文件保留） | `b9f7375dd6e1dc38eed0392ea5994198afca5b008605c3db2202d388357f5b03` |

### 6.3 C. 新增文件（写前均不存在）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/.../knowledge/KnowledgeQueryType.kt` | 枚举 + §1.2 词表 + `classify`（v1 §5.3 #1） |
| 2 | `core/data/.../knowledge/KnowledgeMaterialTypeRanking.kt` | §2.2 四表 + 比较器（v1 §5.3 #2） |
| 3 | `core/data/src/test/.../knowledge/KnowledgeQueryTypeClassifierTest.kt` | 分类分布/下拉/空文本（v1 §5.3 #3） |
| 4 | `core/data/src/test/.../knowledge/KnowledgeMaterialTypeRankingTest.kt` | 四表排列/未知 7/键序（v1 §5.3 #4） |
| 5 | `core/data/src/test/.../study/TutorKnowledgeReadQueryRoutingTest.kt` | UNCLASSIFIED 走旧方法且输出逐字（v1 §5.3 #5） |
| 6 | `core/database/src/androidTest/.../TeachingMaterialRankedAllocationInstrumentedTest.kt` | 真库：旧方法 == Ranked(identity)（v1 §5.3 #6） |
| 7 | **`tools/kb_coverage/routing_eval_v2.py`** | **v2 唯一装置脚本**（§5） |
| 8 | `core/data/src/test/.../knowledge/KnowledgeMaterialTypeMatchRateTest.kt` | 静态 71/86→86/86、15 条变化、43 条零变化 |

### 6.4 D. 零触碰（登记供核验）

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/database/.../dao/ProblemOrganizationDao.kt` | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 2 | `core/data/.../dense/DenseFusion.kt` | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 3 | `core/database/.../DenseRecallReranker.kt` | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 4 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 5 | `core/database/.../StudyDatabase.kt` | `b82369a17641c1eb71a005bea51f12f01b6f66bcf6c1cac241ac0b54a4bec308` |
| 6 | `core/data/.../knowledge/RoomTutorTeachingReferenceRepository.kt` | `7bfa96df9e93a354b3fac2db2ef710dbe69bd9cc8898ea2ee76d914a854ad992` |
| 7 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 8 | `core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt` | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` |
| 9 | `core/data/src/test/.../GoldenRetrievalJvmTest.kt` | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95`（`M`） |
| 10 | `core/data/src/test/.../RetrievalBenchmark.kt` | `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |

### 6.5 回滚步骤

1. 不得整树 `git checkout` / `stash` / `reset`；用备份覆盖 §6.1/§6.2，删除 §6.3 的 8 件（若已入库则回滚提交）。
2. 无 schema/迁移/内容变化；`pm clear` 不需要；设备重装默认/基线 APK 即可，学生数据零触碰。
3. 复核：`UNCLASSIFIED` 43 条渲染与臂前逐字相同；旧方法 SQL 与 §6.1 #6 pre-sha 一致；双门（§5.3 S1–S5 的脚本自检 + `compileDebugUnitTestKotlin:assembleDebugAndroidTest`）回到写前形态。
4. 采纳：保留写面；R1–R5 无回归 + Δ_overall>0 + 逐类不降 + Z1–Z3=0；§8 登记两列、逐题零变化、静态 15 条、脚本 sha、双门 exit、逐文件 post-sha。

---

## 7. 未做 / 未验证（写作时点）

- **未改代码/未跑 Gradle/设备/判官**：§1–§6 是冻结设计；v1 的 6 个新文件与 v2 的脚本均未落盘；所有设备读数 `NOT_RUN`。
- **v1 装置失败未复盘到具体命令**：ask 只给定“未按 §5.2/§5.3 执行”；本文件据此把装置收成脚本，未对上次执行日志逐行归因。
- **静态 71/86→86/86 是本会话离线实算**：基于当前主包 `10ede1bf…`、2025 sidecar 64,853 材料、§1 词表与 §2.2 秩表；执行轮必须用脚本逐值复现。
- **设备 per-slot 的 Δ 未测**：15 是 expectedSlug 节点的结构变化数；top-5 其余槽位可能带来更大 Δ，也可能因期望节点不在 top-5 而小于 15。采纳只要求设备 Δ>0 且逐类不降；静态 15 是 preflight 地板。
- **F1/F2/F3 的当前同轮基线未测**：v1 的 645/645、117/129、8/645 是 v26；v2 用同 run identity 列作基线，不继承历史值。
- **Z2 的 JVM 镜像**：沿用 v1/AC 的 fused/lexical MISS symdiff；本臂不改召回，预期 0；未跑。
- **线索表轻度泄漏**：写表前预览过金标前 30 条；未按分布回调；残余风险登记。
- **脚本的 SQL 逐字对拍**：`--static` 用 SQLite 逐字 SQL；与设备 Kotlin 的 0 mismatch 只在 Z1 执行后成立，本会话未跑。
- **`TeachingMaterialAllocationInstrumentedTest` 的当前 pre-sha 是 `f52526d1…`**：不是 v1 的 `13a1574c…`（该文件在 v1 之后被其他线改过）；回滚以当前 pre-sha 为准。

---

## 8. 执行轮追加位（只追加，不改 §1–§6）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；§6 逐文件 pre/post sha；`routing_eval_v2.py` 的落盘 sha（`script.sha256`）与 `--self-check`/`--static` exit；static.json 的 67/7/12/43、71/86→86/86、15 条变化、43 条零变化；设备 APK sha、设备指纹、`sqlite_version()`、一次 run 的 `device.tsv`/device.json；R1–R5、Δ_overall/逐类、F1/F2/F3 两列、24 章表；Z1/Z2/Z3 的 symdiff/mismatch；`--report` exit；双门 exit；采纳/结案/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
