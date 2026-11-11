# KB B35 查询类型路由 v2（第一步先补装置脚本）：预注册（2026-11-06）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-07T22:03:56+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-06` 是批次日。
> **HEAD**：`ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（`git rev-parse HEAD` 实跑）。
> **上游**：`docs/research/2026-11-03-kb-b34-routing-prereg-v2.md`（下称 **b34-routing**：线索表/秩表/匹配率/装置 CLI/断言表的冻结源，本文件原文收编并逐值复核）；`docs/kb-batch34-2026-11-03.md:144-155`（W6 **ABORT**：直接原因 = `tools/kb_coverage/routing_eval_v2.py` 缺失，"静态门不存在，不得跑设备"）；`docs/research/2026-11-03-kb-r10-small-n-eval-stats.md`（单臂单测一次）。
> **本步唯一新增写盘**：本文件。未改代码/内容/脚本；§0 的读数是本会话 `python -I` / `sha256sum` / `git` / `grep` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-07T22:03:56+0800
git rev-parse HEAD                             # ba7b5624ea0c2bc3f09dcee247f5852e4f428122
git status --porcelain | wc -l                 # 593
ls docs/research/2026-11-06-kb-b35-routing-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 593 项脏项属他线/已写面；不触碰、不裹挟、不回滚；**不得**整树 `git checkout`/`stash`/`reset`。
- 触碰面内已有他线 `M`：`core/data/.../study/RoomTutorToolRunner.kt`（写前实读）。§5.1 的 pre-sha 是工作树值。

### 0.2 现状（实读）

| 事实 | 值 | 来源 |
|---|---|---|
| 路由 v2 写面在树中**不存在** | `KnowledgeQueryType` / `KnowledgeMaterialTypeRanking` / `readBestTeachingMaterialPerNodeRanked` 在 main 源 0 命中 | 本会话 grep 实查 |
| **装置脚本不存在**（b34 ABORT 的直接原因） | `tools/kb_coverage/routing_eval_v2.py`：No such file or directory；`tools/kb_coverage/` 无 routing_eval* | 本会话 `ls` 实查 |
| b34 ABORT 原文 | `run.status=ABORT / reason=缺少 tools/kb_coverage/routing_eval_v2.py：静态门不存在，不得跑设备（v2 §5.1）` | `docs/kb-batch34-2026-11-03.md:147-150` |
| 金标 / 主包身份 | golden sha `eabef102…`、主包 sha `10ede1bf…`、manifest `28/ce8edb8f89d91fe7` | 本会话 `sha256sum` / `python -I` 实读 |
| 共面 | `RoomTutorToolRunner.kt` 与渲染臂（`2026-11-06-kb-b35-render-v3-prereg.md` §4.1 #1）同面；`KnowledgePort/RoomKnowledgeBaseStore/RoomStudyDatabase` 与导入臂同面 | 本会话 porcelain 实读 |

### 0.3 冻结值本会话实算（本文件与 b34-routing 的关键差异）

b34-routing 的 §1.3/§2.3 数字是**写作会话离线实算**，执行轮才复现。本文件把同一组数字**在本会话逐值实算复现**（同一金标 sha `eabef102…` + 同一主包 sha `10ede1bf…`，内容代际一致）：

| 冻结值 | 本会话实跑读数 | 判定 |
|---|---|---|
| 线索表哈希（json.dumps ensure_ascii=False separators=(',',':') 的 sha256，20/22/18 词） | `ed21d6d26831fb22b688b93465a75e606a4eed686312e6ccd77f8897522a4c15` | **逐字命中** |
| 分类分布 | `{"UNCLASSIFIED": 43, "CONCEPT": 67, "METHOD": 7, "COMPUTATION": 12}` | **命中** |
| tie（并列）下标 | `[86, 106, 112, 113, 121, 123]`；零命中 37 条 | **命中** |
| 43 条 UNCLASSIFIED 下标哈希（tie+zero 逗号连接的 sha256） | `099d21ab10b6899a06a0dbc22698229a63e59ec650fad54cdafe404957f73e37` | **逐字命中** |
| 材料面（26 侧车绑定展平） | **64,857** 条 binding 记录（2025 64,853 + 2020 4） | 命中（b34 §0.2 同源口径） |
| 静态匹配率（期望节点） | identity **71/86** → routed **86/86**；`ambiguous=0`、`unresolvable=0`（86 条分类查询的 expectedSlug 全部唯一解析） | **命中** |
| 变化下标（0 基） | `[7, 16, 21, 33, 50, 69, 72, 78, 85, 88, 101, 102, 122, 127, 128]`，全部 `CONCEPT_EXPLANATION → METHOD_MODEL` | **命中** |
| 变化下标哈希（15 下标逗号连接的 sha256——格式本会话确认为普通逗号连接） | `ca0acf43e3a2adafb4e60dbee7ab6af45ccd30cad127465549cc54d1a0fb49c4` | **逐字命中** |

- 实算口径与 b34-routing §1.1/§2.3 的语义逐条一致（lowercase、子串包含、每词每查计 1、严格最大、0/并列→UNCLASSIFIED；role 第一键 → 类型秩 → title → material_id，`ROLE: PRIMARY=0/SUPPORTING=1/ELSE=2`；`material_id=slug` 代理键仅作末位 tiebreaker）。
- ⇒ §3.6 的 `--self-check` 门是**已证明可复现**的门：执行轮复现失败 = 词表誊写漂移/金标漂移/实现漂移，ABORT。

### 0.4 与其他 B35 臂的共面协调（写死）

1. **执行顺序**：导入臂（读路径只读化）→ 渲染臂（第二槽）→ 本臂；执行轮若实际顺序不同，必须登记。§5.1 的 pre-sha = 当轮写前实测。
2. **回滚边界**：本臂回滚**不得**回退导入臂/渲染臂的采纳面；共文件按"恢复到本臂写前实测值"处理。
3. 本臂与渲染臂的分工（不得混写）：本臂只改「节点已定、材料未选」的**选材**层（分类 → identity/ranked 二选一）；渲染臂只改「已选材料怎么渲染」（第二槽/预算/截断注）。本臂**不**触碰第二槽与预算；渲染臂**不**触碰选材秩。

---

## 1. 线索表（冻结；本会话哈希实算）

### 1.1 分类器输入与匹配（b34-routing §1.1 逐字收编）

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

- **冻结哈希**：`json.dumps({"CONCEPT":[...],"METHOD":[...],"COMPUTATION":[...]}, ensure_ascii=False, separators=(',',':'))` 的 sha256 = **`ed21d6d2…`（本会话实算逐字命中，见 §0.3）**。
- 轻度测试集泄漏登记（沿用 b34 §1.2）：写表前预览过金标前 30 条题面；冻结后不得再改。

### 1.3 分类分布与冻结下标（本会话实算复现）

| 项 | 值 |
|---|---|
| 分布 | CONCEPT **67** / METHOD **7** / COMPUTATION **12** / UNCLASSIFIED **43** |
| tie（6） | `[86, 106, 112, 113, 121, 123]` |
| 零命中（37） | `[0, 2, 3, 6, 9, 15, 23, 26, 35, 37, 44, 45, 46, 47, 51, 52, 58, 59, 60, 61, 64, 66, 70, 71, 76, 80, 82, 83, 93, 99, 105, 107, 109, 114, 116, 119, 124]` |
| 43 条索引哈希 | `099d21ab…`（本会话实算逐字命中） |

- 分类器测试必须逐条复现该分布与两组下标；任一不符 → ABORT（词表/金标漂移）。

---

## 2. 类型 → 交付层材料类型加权（冻结）

### 2.1 加权边界（b34-routing §2.1 逐字收编）

- 只在「节点已定、材料未选」层生效；节点集合/顺序/top-5/512/召回/融合**零改**。
- `role` 仍是第一键；类型秩只在同角色内重排；`title`、`material_id` 仍是第三、第四键。
- 旧方法 `readBestTeachingMaterialPerNode` **一字不改**；`UNCLASSIFIED` 直接调用旧方法（同一 SQL、同一参数）。
- 分类命中时调用新方法 `readBestTeachingMaterialPerNodeRanked(subject, ids, r0..r6)`；SQL 与旧方法逐字相同，只把 7 个 CASE 字面量换成 7 个绑定参数（现行 CASE 逐字见 `KnowledgeTeachingMaterialDao.kt:99-116`：role `PRIMARY=0/SUPPORTING=1/ELSE=2`；type `CONCEPT_EXPLANATION=0 … COMPLETE_SOLUTION=6, ELSE=7`）。
- **不得**写回 `reTeachPriority`（另一套消费方；DAO KDoc）。

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
- 现包只有 3 种类型：CONCEPT 行与 identity 恒等；METHOD/COMPUTATION 只把 `METHOD_MODEL` 提到 `CONCEPT_EXPLANATION` 之前；`MISCONCEPTION_GUIDE` 在所有类都排第 5/6（F3 不劣的结构性理由）。

### 2.3 交付指标的定义（b34-routing §2.3 逐字收编）与静态期望（本会话实算）

对每个查询/槽位（query × 5 节点）：

1. 查询 `classify` 为 `UNCLASSIFIED` 或节点无材料 ⇒ **不计入**匹配率分母（走 identity 零变化断言）。
2. `preferred_available_type` = 该节点现有材料类型中，在**该查询类的秩表**里秩最小的那一个。
3. `match` = 该槽位**被选中的材料类型** == `preferred_available_type`。
4. 匹配率 = `match 槽位数 / 计入分母的槽位数`；分别报总体、CONCEPT/METHOD/COMPUTATION 三类。
5. 基线 = 同一 run 内 identity 选材的匹配率；routed = 同一 run 内 ranked 选材的匹配率；**Δ = routed − identity**。

**静态期望节点（preflight；本会话实算复现，见 §0.3）**：

| 类 | n | identity 匹配 | routed 匹配 | Δ |
|---|---:|---:|---:|---:|
| CONCEPT | 67 | 67 | 67 | 0 |
| METHOD | 7 | 3 | 7 | **+4** |
| COMPUTATION | 12 | 1 | 12 | **+11** |
| **合计** | **86** | **71（82.56%）** | **86（100%）** | **+15（+17.44 pp）** |

- 15 条变化下标 = `[7,16,21,33,50,69,72,78,85,88,101,102,122,127,128]`（0 基，金标顺序），哈希 `ca0acf43…`（逗号连接格式本会话确认），全部 `CONCEPT_EXPLANATION → METHOD_MODEL`。
- 43 条 UNCLASSIFIED 的 identity/routed 选材必须逐题完全不变（本会话实算 0 变化）。
- 设备 per-slot 的 Δ 可能大于 15（每条查询 5 个槽位）；采纳要求设备 Δ>0 且逐类 routed ≥ identity，静态 15 是 preflight 地板/结构证明。

### 2.4 不改召回/融合（违例判据；b34-routing §2.4 逐字收编）

- 零触碰：召回 SQL/512、`DenseFusion`、`DenseRecallReranker`、`KnowledgeSearchFeatureExtractor`、`KNOWLEDGE_READ_NODE_LIMIT=5`、120 字、2k 预算、内容包/manifest/dense/金标。
- `SCOPE_VIOLATION`：同一 run 的前 5 节点 id 序列与基线逐题相同；路由只换材料，不改节点。若节点序列变了，规则全部 `NOT_RUN`。

---

## 3. 装置脚本 `tools/kb_coverage/routing_eval_v2.py`（**第一步先补**；写死）

### 3.0 先落盘时序（本臂第 0 步；b34 三次设备失败的教训兑现）

1. **第 0 步（任何构建/安装/instrument 之前）**：按本 §3 契约把脚本写到 `tools/kb_coverage/routing_eval_v2.py`（tracked），`sha256sum` 落 `build/agent-batch35/routing/script.sha256`。
2. **门 0（脚本静态门）**：`python -m py_compile tools/kb_coverage/routing_eval_v2.py` exit 0 **且** `python -I tools/kb_coverage/routing_eval_v2.py --self-check` exit 0。任一缺失/失败 ⇒ **ABORT**，`run.status` 逐字写 `reason=缺少 tools/kb_coverage/routing_eval_v2.py：静态门不存在，不得跑设备（b35-routing §3.0）`——**不得现场手敲命令代替脚本**。
3. `--self-check` 必须逐值复现 §0.3/§1.3/§2.3 的全部冻结值（哈希、分布、71/86→86/86、15 条变化）——本会话已证明该组值在同一内容上可复现；不符 = 词表誊写/金标/实现漂移，ABORT。
4. 所有判读只读脚本输出（`static.json` / `device.json` / `report.json`），不接受手写/临时 SQL 的读数。

### 3.1 脚本身份与安全口径（写死）

- 脚本只读仓库内输入与 `device.tsv`；**不依赖** v1 的仓外 `audit_knowledge_read.py`。
- **路径安全（冻结为脚本需求）**：脚本接受的全部 CLI 路径参数（`--out`、`--device` 的 tsv、`--report` 的两输入）必须经"规范化 + `commonpath` 限仓库内"校验，越界即 exit 2 ABORT——本预注册写作时的独立安全扫描对未校验 CLI 路径判高危，此条是对该发现的可执行修复。
- 脚本只出机械断言与计数，**不出采纳判词**；采纳按 §4 由执行轮对照判据落笔。
- 运行目录 = 仓库根（§3.2 的命令均自仓库根执行）。

### 3.2 CLI（b34-routing §5.2 逐字收编；产物目录改 batch35）

```bash
# 0) 先落盘并记录 sha（在任何运行之前）
sha256sum tools/kb_coverage/routing_eval_v2.py | tee build/agent-batch35/routing/script.sha256

# 1) 输入/词表/分布/静态期望自检（不跑设备）
python -I tools/kb_coverage/routing_eval_v2.py --self-check

# 2) 静态镜像（SQLite 逐字 SQL；输出 static.json）
python -I tools/kb_coverage/routing_eval_v2.py --static \
  --out build/agent-batch35/routing/static.json

# 3) 设备 run（一次）后，读导出（输出 device.json）
python -I tools/kb_coverage/routing_eval_v2.py --device \
  build/agent-batch35/routing/device.tsv \
  --out build/agent-batch35/routing/device.json

# 4) 合并判读（输出 report.json；exit 0=机械门全过，1=FAIL/NOT_JUDGEABLE）
python -I tools/kb_coverage/routing_eval_v2.py --report \
  build/agent-batch35/routing/static.json \
  build/agent-batch35/routing/device.json \
  --out build/agent-batch35/routing/report.json
```

### 3.3 断言表（b34-routing §5.3 逐条收编 + B35 修订；缺一即失败）

| # | 断言 | 失败动作 |
|---:|---|---|
| S1 | 金标 sha `eabef102…`、主包 sha `10ede1bf…`、侧车 26 件（25×v2 + 2020×1）、manifest `28/ce8edb8f89d91fe7` | exit 1，ABORT |
| S2 | 词表哈希 `ed21d6d2…`、20/22/18 词、无重复、全部 ≥2 字 | exit 1 |
| S3 | 分类分布 67/7/12/43；tie/零命中下标与哈希 `099d21ab…` | exit 1 |
| S4 | 静态期望节点：86 条有材料；identity 71/86、routed 86/86；15 条变化且下标哈希 `ca0acf43…`（逗号连接格式） | exit 1 |
| S5 | 43 条 UNCLASSIFIED 静态 identity==routed（id/type） | exit 1 |
| S6 | 设备 `device.tsv` 129 题 × 5 槽全在；query/slot 不重不漏 | exit 1 |
| S7 | Z1 静态↔设备 0 mismatch（定义见 §4.4）；Z2 fused/lexical symdiff 0；Z3 同 run 双列节点序列逐题相同 | 记 `NOT_JUDGEABLE` |
| S8 | R1–R5 无回归；`Δ_overall>0`；逐类不降 | exit 1（不采纳） |
| S9 | F1/F2/F3 的 identity/routed 两列与 24 章表完整输出 | exit 1（缺列） |

- **--static 的镜像口径（写死）**：用 SQLite/同语义逐字执行 identity SQL 与 ranked SQL（秩表 §2.2 作绑定参数）；`material_id` 的包侧代理键 = 侧车 `slug`（仅作同 title 时的末位 tiebreaker，登记）。expectedSlug → node_id 的解析口径：绑定 `knowledgeNodeId` 末段 == slug 且含 `:<subject小写>:` 段；0 命中/多义 → 该查询按"无材料"处理（本会话实算 86 条分类查询全部唯一解析）。
- **--device 的输入契约（写死）**：`device.tsv` 每行一个 (query, slot)，列 = `query_id  chapter  expectedSlug  slot  node_id  identity_material_id  identity_type  routed_material_id  routed_type  rendered_identity_sha256  rendered_routed_sha256`（b34-routing §3.3 原文）；`query_id` = 金标 0 基下标。没有该导出 ⇒ 43 条零变化无法逐题断言，判读无效。
- **--device 的全槽镜像（写死）**：脚本对 device.tsv 的每个 (query, slot) 用包数据 + 导出 `node_id` 独立复算 identity/routed 选材（不预测召回，只镜像选材），与设备两列对拍——Z1 覆盖全部 129×5 槽位。

### 3.4 输出 schema（冻结 = ask 的"输出"）

- `static.json`：`distribution`、`tie`、`zeroHit`、`classified/withMaterial`、`identityMatch/routedMatch`、`changed`、`unclassifiedZeroChange`、`perQuery[]`（每条：`index/subject/chapter/expectedSlug/qtype/nodeId/nCandidates/identity{materialId,type,title}/routed{...}/identityMatch/routedMatch`）——**逐题类型判定**即 `perQuery[].qtype`。
- `device.json`：逐槽镜像结果、mismatch 计数、逐题零变化断言、匹配率（总体/逐类）。
- `report.json`：R1–R5/Z1–Z3/Δ_overall/逐类 Δ/F1/F2/F3 两列/24 章表 + 机械门判词（PASS/FAIL/NOT_JUDGEABLE）。

### 3.5 --device/--report 的机械判读项（写死）

- R2（F1 覆盖）：identity 有材料的槽，routed 也必须有材料（material_id 非空），反之亦然；任何单侧缺失 → 不采纳。
- R4（F3 误区槽位）：选中类型 = `MISCONCEPTION_GUIDE` 的槽位数，routed ≤ identity 且 ≤20%。
- R5（非命中零变化）：43 条 UNCLASSIFIED 的每条 5 槽：`identity_material_id == routed_material_id`、`identity_type == routed_type`、`rendered_identity_sha256 == rendered_routed_sha256`（逐字节）；43/43 全等，任一不等 → 本臂越界/实现错误，规则全部 `NOT_RUN`。
- 交付匹配率：分母 = 分类查询中有材料的槽位（§2.3 规则 1）；Δ_overall 与逐类率按 §2.3 定义计算。

### 3.6 正文落盘纪律（写死）

- 脚本正文由执行轮第 0 步按本 §3 契约编写并落盘（CLI/断言/输出 schema/安全口径/语义逐条冻结）；本预注册**不内联正文**——写作时点登记：正文三次内联生成尝试全部损坏或被独立安全扫描拦截（未校验 CLI 路径判高危），故冻结面收在契约层，可复现性由"门 0 + --self-check 逐值复现已验证数字"承担（§0.3 已证明该组数字在同一内容上可复现）。
- 落盘后立即 `sha256sum` 落账；正文若与契约冲突（如无法复现 S3/S4），**停**，登记后另议，不得改冻结值凑绿。

---

## 4. 判读（单臂单测一次；写死）

### 4.1 单臂单测（b34-routing §4.1 逐字收编）

- **一个 routed APK、一次设备 run**：同一 run 内取同一 top-5 节点，对每个槽位分别调用旧方法（identity）与新方法（routed）；两列来自**同一次**检索，不做 A/B 第二个 APK、不做第二次设备运行。
- 脚本的 `--static` 是 preflight 镜像，不是第二次假设检验；设备 run 是唯一交付读数。
- 只测一次：不得边看边改词表/秩表/匹配率定义；不得重跑挑结果。装置失败（连接/夹具）按记录后重跑，不得把多次运行拼成一个读数。

### 4.2 不劣（无回归；b34-routing §4.2 逐字收编）

| # | 项 | 判据 |
|---:|---|---|
| R1 | 检索不劣 | 同一 run 的 top-5 节点序列与基线逐题相同（路由不改召回）；融合 Recall@5/MRR 与基线同值。若序列变了 → `SCOPE_VIOLATION`，不判读 |
| R2 | F1 覆盖 | routed == identity（每个有材料的槽位仍取到 1 条）；任何下降 → 不采纳 |
| R3 | F2 期望节点有材料 | routed == identity（expectedSlug 在 top-5 且拿到材料的查询数不变） |
| R4 | F3 误区槽位 | routed **≤** identity，且 ≤20%；任何上升 → 不采纳 |
| R5 | 43 条 UNCLASSIFIED | §3.5 逐题逐槽全等；任一不等 → 不采纳 |

- F1/F2/F3 的 identity 值是**同一 run** 的基线，不用历史 117/129/8 代替。

### 4.3 交付指标（材料类型匹配率提升；b34-routing §4.3 逐字收编）

- 设备 per-slot：`Δ_overall = routed_match_rate − identity_match_rate` **> 0**。
- 逐类：`routed_match_rate_c ≥ identity_match_rate_c`（三类都不得下降）；报告每类 n、match 数、率、Δ。
- 静态 preflight：71/86 → 86/86、15 条变化必须逐值命中（本会话已复现，§0.3）；否则 ABORT。
- 若 `Δ_overall = 0` 且 R1–R5 全绿 ⇒ **保持现状 + 登记「交付层类型加权无证明收益」结案**（不是"证明无效"）。
- 若 `Δ_overall > 0` 但某一类下降 ⇒ 不采纳（局部回归）。

### 4.4 零漂移（B35 精确定义；b34-routing §4.4 收编）

| # | 检查 | 通过条件 |
|---:|---|---|
| Z1 | 静态 ↔ 设备逐槽位 | 脚本镜像（包数据 + device.tsv 导出 node_id 逐槽复算）与设备 identity/routed 选材 id/type **0 mismatch**（129×5 槽位；无材料的槽位按规则一致） |
| Z2 | 设备 ↔ JVM 镜像 | fused 与 lexical 的 MISS query 集合 symmetric difference **0**（沿用 v1/AC 装置；非 0 = 装置缺陷，不判读） |
| Z3 | 同 run 两列 | identity/routed 使用同一 top-5 节点序列；序列逐题相同（device.tsv 单 node_id 列结构保证 + 设备用例断言） |

- 任一门非 0 → 不判读；修复后按记录重跑，不得用单方读数出结论。

### 4.5 24 章守卫（b34-routing §4.5 逐字收编）

- 逐章报 `hits/total`、净变化；不报逐章 p 值；任一章损失 ≥2 条 → 阻断采纳；单章贡献 ≥50% 净增益 → 标"单章驱动"，需 fresh 复现。
- 本臂不改召回，逐章命中应与基线逐章相同；若不同，先查 `SCOPE_VIOLATION`。

---

## 5. 回滚（触碰清单 + pre/post sha）

### 5.1 A. 既有代码（pre-sha256 = 写前工作树值，本会话 `sha256sum` 实跑；post 由 §7 落）

| # | 路径 | 预期动作 | pre-sha256（实测） | porcelain | post-sha |
|---:|---|---|---|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/TutorToolContext.kt` | `Context.studentQueryText` | `772a77c56983af29d201439ff6d850de73757dffb4d93f1dd8053d1195b3386f` | 干净 | §7 |
| 2 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | 分类 → 旧/新方法二选一 | `01652c2c3df218c9d4f0c09ef9b9996825f1f9326190dc5d0f73d6fb3333b73d` | **`M`（他线/渲染臂面，不得回退）** | §7 |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 新增 ranked 端口（含导入臂面，不得回退） | `86c4aef6aa9350db3f28f6cd73a6c381e4fc9a1fd261806fd8ea97ccf2f3dbe8` | 干净 | §7 |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 实现 ranked（7 秩参数）（含导入臂面，不得回退） | `e630d54bb9c66c6feead753d4eb2e24963652ad311a8fccf2a2f0a3969b9c145` | 干净 | §7 |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托 ranked（含导入臂面 + 他线 `M`） | `35305f894f95bb5ebcf6728fa199f88781477ffa3a650a497e66bb8cbc65c7e5` | **`M`（他线）** | §7 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 新增 ranked（CASE 7 参数）；旧方法一字不改 | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` | 干净 | §7 |
| 7 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/TeachingMaterialAllocationInstrumentedTest.kt` | 补「旧方法未改」断言 + `device.tsv` 导出 | `f52526d15585250f7410a8306db90990a67ad72a2f53dd4505133be3ffab85a1` | 干净 | §7 |

### 5.2 B. 新增文件（b34-routing §6.3 原表收编；写前均不存在）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/main/kotlin/.../knowledge/KnowledgeQueryType.kt` | 枚举 + §1.2 词表 + `classify` |
| 2 | `core/data/src/main/kotlin/.../knowledge/KnowledgeMaterialTypeRanking.kt` | §2.2 四表 + 比较器 |
| 3 | `core/data/src/test/kotlin/.../knowledge/KnowledgeQueryTypeClassifierTest.kt` | 分类分布/tie/空文本（钉 §1.3） |
| 4 | `core/data/src/test/kotlin/.../knowledge/KnowledgeMaterialTypeRankingTest.kt` | 四表排列/未知 7/键序 |
| 5 | `core/data/src/test/kotlin/.../study/TutorKnowledgeReadQueryRoutingTest.kt` | UNCLASSIFIED 走旧方法且输出逐字 |
| 6 | `core/database/src/androidTest/kotlin/.../TeachingMaterialRankedAllocationInstrumentedTest.kt` | 真库：旧方法 == Ranked(identity) |
| 7 | **`tools/kb_coverage/routing_eval_v2.py`** | **本臂唯一装置脚本（§3；第 0 步落盘）** |
| 8 | `core/data/src/test/kotlin/.../knowledge/KnowledgeMaterialTypeMatchRateTest.kt` | 静态 71/86→86/86、15 条变化、43 条零变化 |

### 5.3 C. 零触碰（写前实测的行标 ✓；其余沿用 b34-routing §6.4 原表 + 执行轮复核义务）

| # | 路径 | pre-sha256 | 本会话复核 |
|---:|---|---|---|
| 1 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | ✓（sha256sum 实跑） |
| 2 | `tools/kb_coverage/retest_usage_r1.py` | `b9f7375dd6e1dc38eed0392ea5994198afca5b008605c3db2202d388357f5b03` | ✓（sha256sum 实跑） |
| 3 | `core/database/.../dao/ProblemOrganizationDao.kt` | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` | 本会话早前实测 ✓ |
| 4 | `core/data/.../dense/DenseFusion.kt` | b34-routing §6.4 #2 原值 | 执行轮复核 |
| 5 | `core/database/.../DenseRecallReranker.kt` | b34-routing §6.4 #3 原值 | 执行轮复核 |
| 6 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | b34-routing §6.4 #4 原值 | 执行轮复核 |
| 7 | `core/database/.../StudyDatabase.kt` | b34-routing §6.4 #5 原值 | 执行轮复核 |
| 8 | `core/data/.../knowledge/RoomTutorTeachingReferenceRepository.kt` | b34-routing §6.4 #6 原值 | 执行轮复核 |
| 9 | `core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt` | b34-routing §6.4 #8 原值 | 执行轮复核 |
| 10 | `core/data/src/test/.../GoldenRetrievalJvmTest.kt` | b34-routing §6.4 #9 原值（`M`） | 执行轮复核 |
| 11 | 内容包 / manifest / dense 资产 | `28/ce8edb8f…/10ede1bf…/70329115…/f0c94795…` | 主包/manifest 本会话 ✓ |

### 5.4 写前备份 / 回滚 / 采纳

1. 备份 §5.1 的 7 件到 `build/agent-batch35/routing/backup/`，落 sha256 全表；执行轮先重跑 §0.1 守卫并登记当轮 pre-sha（与 §5.1 逐件核对；不一致 = 工作树又前进，停并登记）。
2. 回滚：不得整树 checkout/stash/reset；用备份覆盖 §5.1 的 7 件、删除 §5.2 的 8 件（`routing_eval_v2.py` 若已过门0 且臂未采纳，保留脚本但登记"脚本在、臂未采纳"）；无 schema/迁移/内容变化；设备重装基线 APK 即可，学生数据零触碰。
3. 复核：43 条 UNCLASSIFIED 渲染与臂前逐字相同；旧方法 SQL 与 §5.1 #6 pre-sha 一致；`--self-check` 仍 exit 0。
4. 采纳：R1–R5 无回归 + Δ_overall>0 + 逐类不降 + Z1–Z3=0 + 门 0 双 exit 0；§7 登记两列、逐题零变化、静态 15 条、脚本 sha、双门 exit、逐文件 post-sha。

---

## 6. 未做 / 未验证（写作时点）

- **未改代码/未跑 Gradle/设备/判官**：§1–§5 是冻结设计；8 个新增文件与脚本均未落盘；门 0/`--static`/`--device`/`--report` 全 `NOT_RUN`。
- **脚本正文未内联**（如实登记）：本会话三次内联正文生成尝试损坏或被独立安全扫描拦截（未校验 CLI 路径判高危），冻结面收在 §3 契约层；正文可复现性由"门 0 双 exit + --self-check 逐值复现 §0.3 已验证数字"承担。
- **静态 71/86→86/86 与分布是本会话离线实算**（§0.3，同一金标/主包 sha）：执行轮必须用脚本逐值复现；设备 per-slot 的 Δ 未测（15 是 expectedSlug 节点的结构变化数，top-5 其余槽位可能更大或更小）。
- **F1/F2/F3 的当前同轮基线未测**：用同 run identity 列作基线，不继承历史值。
- **Z2 的 JVM 镜像未跑**；本臂不改召回，预期 0。
- **线索表轻度泄漏登记**（沿用 b34 §1.2）：写表前预览过金标前 30 条；冻结后不得改。
- **`--static` 的 SQLite 逐字对拍**：与设备 Kotlin 的 0 mismatch 只在 Z1 执行后成立，本会话未跑。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；§5 逐文件 pre/post sha；第 0 步脚本落盘 sha（`script.sha256`）与门 0 双 exit（py_compile / --self-check）；static.json 的 67/7/12/43、71/86→86/86、15 条变化、43 条零变化；设备 APK sha、设备指纹、`sqlite_version()`、一次 run 的 `device.tsv`/device.json；R1–R5、Δ_overall/逐类、F1/F2/F3 两列、24 章表；Z1/Z2/Z3 的 symdiff/mismatch；`--report` exit；采纳/结案/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
