# KB B33 B3 近重复去重（阈值二选一 + 锚点保护扩三类）：预注册（2026-11-02）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-03T08:08:44+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日。
> **HEAD / origin/main**：`faab17f58b01eab78d5c08a9f843b3af3df2fecf`（`git rev-parse` 实跑，同值）。
> **上游**：`docs/research/2026-11-02-kb-r3-dedupe-summary-quality.md`（下称 **R3**，§1.4 阈值误伤面、§1.5 覆盖面、§2.2 锚点保护、§3.2 A3/A4 臂）；`tools/kb_coverage/dedupe_near_dup_materials.py`（下称 **工具**，当前未跟踪）；批次31C 的 `build/agent-batch31c/plan.json`（0.70+gates 的旧读数：182 退休）。
> **本步唯一新增写盘**：本文件。未改内容包、未跑 Gradle/设备/JVM；§0.3/§2/§3 的读数是本会话用真实工具函数离线实跑（非设备读数）。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-03T08:08:44+0800
git rev-parse HEAD                             # faab17f58b01eab78d5c08a9f843b3af3df2fecf
git rev-parse origin/main                      # faab17f58b01eab78d5c08a9f843b3af3df2fecf  → 同值 ✓
git status --porcelain | wc -l                 # 560
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   440 ??
#    14  D      ← 他线 core/data/.../pdf/*
#   106  M
git diff --cached --name-only | wc -l          # 0
ls docs/research/2026-11-02-kb-b33-b3-dedupe-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- **触碰面在本臂写前已有他线/历史改动，必须逐条区分（不得当成干净基线）**：
  - `tools/kb_coverage/dedupe_near_dup_materials.py`：**未跟踪 `??`**（工具本身尚未入库；pre-sha `f4ac9f72…`）。
  - `tools/kb_build/tables/material_retire.csv`：**未跟踪 `??`**（批次31C 的台账；本臂 **append-only**）。
  - `tools/kb_coverage/tables/material_judgments/{BIOLOGY,CHEMISTRY,MATH,PHYSICS,UNASSIGNED}.csv`：**`M`**（他线/批次遗留；本臂在其工作树内容上编辑）。
  - 11 个 sidecar 与 manifest 写前**干净**（`git status --porcelain -- <§4.2>` 为空）。
- 560 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。

### 0.2 现状与输入（实读 / 实算）

| 事实 | 值 | 来源 |
|---|---:|---|
| 包内材料 / 节点 | **65,021 / 3,280** | 25 sidecar `materials[]` |
| 当前包身份 | `version=26` / `contentVersion=aea886e42b36dcb2` / 主包 `96246c78…` / `.vec` `70329115…` / `vectorCount=44,121` | manifest + `.vec.json` + `sha256sum` |
| release ↔ staging | **30/30 逐字节相同**（diff 0 / missing 0） | 本会话逐文件 `sha256` |
| 工具的硬口径 | char 3-gram、128 perm、MinHash 粗筛 0.60、**精确 Jaccard ≥ 0.70**、只在同节点内、**标题规范化相同**、**正文短实词替换不退休**、不链式退休 | 工具 `:20-38`、`:101-146`、`:184-220`、`:447-504` |
| 现有保护 | 登记锚点 **10** + tools/tests/gates 引用 slug + `alias_map` 唯一支撑 **35,515** | 工具 `load_anchor_slugs` `:317-336`、`load_alias_backed_slugs` `:337-371` |
| 批次31C 旧读数 | 369 对 / 209 簇 / **182 退休** / 26 skip / 0.28% | `build/agent-batch31c/plan.json`（`counts`） |

### 0.3 本会话实算（离线，非设备读数）

**阈值证据（369 对，来自批次31C 的 `dedupe-pairs.csv`）**：

| 阈值 | 对数 | 标题规范化相同 | 标题不同（会被标题门丢弃） |
|---:|---:|---:|---:|
| ≥0.70 | **369** | 247 | **122** |
| ≥0.80 | 180 | 152 | 28 |
| ≥0.90 | **116** | 111 | **5** |
| ≥0.95 | 81 | 80 | 1 |

**扩保护后的计划（本会话用真实 `find_clusters` + `build_plan` 在 v26 包上实跑）**：

| 项 | 基线（旧保护） | 扩三类后 |
|---|---:|---:|
| 退休 | **182** | **168** |
| skip | 26 | 26 |
| 簇 | 209 | 209 |
| 退休比例 | 0.28% | **168 / 65,021 = 0.2584%** |
| 新保护拦下的退休 | — | **14 条** |

**扩保护的面**：

| 保护类 | 覆盖 | 材料数 |
|---|---:|---:|
| 登记锚点（`content_audit_anchors.csv`） | 10 | 10 |
| `alias_map` 唯一支撑（现有） | — | 35,515 |
| **金标 `expectedSlug`**（新增） | 128 个节点（129 查询去重） | **3,847** |
| **THIN 节点 ≤2 材料**（新增） | 432 个节点 | **626** |
| 合并去重后的保护总数 | — | **37,512** |
| 保护 ∩ 退休 | — | **0** |

**退休分布**：CHEMISTRY 72 / PHYSICS 51 / MATH 43 / BIOLOGY 2；影响 **145 个节点**；原因 `near-duplicate` 153 + `near-duplicate of protected anchor` 15。

**节点材料数**：退休后 **0 个节点降到 0 条**；「恰 1 条」的节点 **238 个（前后相同）**——THIN 保护把 ≤2 条的节点全部挡住。

**预测 `contentVersion`（删除 168 条后）**：`2cd974dbada7cb2f`（`version` 26→27）。

---

## 1. 阈值：二选一，写死 **B（0.70 + 标题门 + 实体替换门）**

### 1.1 选择

**采用 B**：精确 Jaccard **≥ 0.70**，且**规范化标题必须相同**，且**不存在 ≤6 字实词替换**；不采用 A（单阈值 0.90）。

### 1.2 门的冻结语义（逐条，与工具现有实现一致）

| 门 | 冻结定义 | 出处 |
|---|---|---|
| 分片 | 文本 = `title + "\n" + summaryMarkdown + "\n" + contentMarkdown`；`NFKC + casefold + 去全部 Unicode 空白`；char **3-gram**；64-bit FNV-1a；**128** 置换（固定种子 `20261102`） | 工具 `:132-170`、`:101-104` |
| 候选召回 | MinHash 估计 **≥ 0.60** 才做精确值；**最终只认精确 Jaccard ≥ 0.70** | 工具 `:22`、`:466-475` |
| 范围 | **只在同一 `knowledgeNodeId` 内**比较；多绑定材料跳过 | 工具 `:275-286` |
| 标题门 | `canonical_title` = `NFKC + casefold` → 去版次词（学生版/教师版/学生用书/教师用书/学生用/教师用）→ 去括号内容 → 去非词字符；**两材料必须相等**；空标题不算相等 | 工具 `:138-145`、`:480` |
| 实体替换门 | `difflib` 对比正文，出现 **≤6 字的实词替换**（非标点/数字/圈号/转义残片）⇒ **不退休** | 工具 `:184-220` |
| 不链式 | 每条被退休材料必须与**最终保留者直接**满足「标题相同 + Jaccard ≥ 0.70」；靠中间材料串起来的记 `SKIP` | 工具 `:38`、`:548-573` |

### 1.3 为什么拒绝 A（单阈值 0.90）——用本包数据

1. **0.90 不消除标题不同的假阳性**：≥0.90 的 116 对里仍有 **5 对**规范化标题不同（`canonical_title_equal=false`）。A 没有标题门，这 5 对会被直接判重并退休——正是 R3 §1.4 点名的 `y=Asin(ωx+φ)` / `y=Acos(ωx+φ)`、`a>0` / `a<0`、下凸/上凸这类**互补结论**。
2. **0.90 也不消除实体替换假阳性**：`核糖/脱氧核糖`、`果糖/半乳糖`、`蔗糖/乳糖`、`温度/pH` 这类同模板表格材料，正文只差 ≤6 字实词，Jaccard 通常 >0.95；提高阈值到 0.90 仍会命中，只有实体门能挡。
3. **A 还少做候选**：标题相同且 ≥0.70 的 247 对里，0.90 只保留 111 对；A 会放过 136 对标题相同的候选（其中仍有实体门要筛，但 A 连筛的机会都没有），覆盖率必然更低。
4. **A 需要新机制**：工具的 `JACCARD_THRESHOLD`、`find_clusters`、`build_plan.similar` 全部以 0.70 + 两门写死；改成 0.90 且去掉门 = 新算法，本批没有它的任何读数。B 是已实现、已测（批次31C 182 → 本批扩保护 168）的那条。

**结论**：A 在**精度上并不更安全**（5 对标题不同 + 实体替换类），在**召回上更差**（少 136 对），且没有现成读数。B 胜出。

### 1.4 选 B 消灭的具体失败

| 选择 | 消灭的**具体**失败 |
|---|---|
| 0.70 + 标题门 | `Asin/Acos`、`a>0/a<0`、下凸/上凸被当重复退休（R3:30 实测 122 对） |
| 0.70 + 实体门 | `核糖/脱氧核糖`、`果糖/半乳糖`、`蔗糖/乳糖` 这类同模板换实体材料被退休，丢独立知识点（R3:32-34） |
| 不在 CLI 暴露阈值 | 执行轮「调到刚好退休想退休的」；阈值/门是写死口径，不可网格搜索 |
| 不链式退休 | 连通分量里靠中间材料串起来的成员被误退休（R3:38） |

---

## 2. 锚点保护：扩三类（冻结）

### 2.1 三类扩展（写死）

| # | 类 | 判定 | 保护动作 |
|---:|---|---|---|
| 1 | **被 tools/tests/gates 引用的 slug** | 材料 slug 作为字面量出现在 `tools/tests/**/*.py`、`gate.py`、`promote.py`、`check_pack_contract.py`、`audit_content_bindings.py`、`materialize.py`（工具的 `_scan_files`） | 该材料**永不退休**（现有 `load_anchor_slugs`；冻结其扫描面） |
| 2 | **金标 `expectedSlug`** | `golden_queries_v2.json` 的 129 条 `expectedSlug`（去重 128）→ 该**节点**下的**全部材料** | 该节点的材料**永不退休**（新增 `load_golden_expected_slugs`） |
| 3 | **THIN 节点（≤2 材料）中的材料** | 去重前该节点绑定的材料数 **≤ 2** | 该节点材料**永不退休**（新增 `load_thin_node_slugs`） |

**保留现有两类**（不得削弱）：`content_audit_anchors.csv` 的 10 条登记锚点；`alias_map` 中「该标题在本节点仅此一条材料」的唯一支撑（35,515 条）。

### 2.2 实现点（冻结，工具改动）

1. `load_golden_expected_slugs(golden_path=None) -> dict[str, list[str]]`：
   - 读 `tools/kb_coverage/tables/golden_queries_v2.json`，取 `expectedSlug` 去重；
   - 对 `groups` 里 `node_slug_of(node_id) ∈ expectedSlug` 的节点，其全部材料 slug → `["golden:expectedSlug"]`；
   - 金标文件缺失/为空 ⇒ **报错退出**（不得静默退化为「无保护」）。
2. `load_thin_node_slugs(groups, max_materials=2) -> dict[str, list[str]]`：
   - 对 `len(items) <= 2` 的节点，其全部材料 → `["thin:<=2-materials"]`；
   - 计数必须与 plan 的 `thin_nodes` 一致。
3. `main()` 里把两者并入 `extra_protected`（与 `alias_backed` 合并；同一 slug 多来源时全部保留）。
4. `build_plan.counts` 增加 `golden_protected` / `thin_protected` / `golden_nodes` / `thin_nodes`。
5. 写后自检增加：`protected ∩ present = ∅`（现有）、`golden/THIN 集合仍在保护内`、`零节点降到 0 条`。

### 2.3 扩保护的实测面

| 项 | 读数 |
|---|---:|
| 金标 unique expectedSlug / 在包内找到 | 128 / **128** |
| 金标节点材料被保护 | **3,847** |
| THIN 节点（≤2）/ 材料 | **432 / 626** |
| 合并保护总数（含现有 10 + 35,515） | **37,512** |
| 保护 ∩ 退休 | **0** |
| 新保护拦下的退休（对基线 182） | **14** |
| 退休后降到 0 条的节点 | **0** |

### 2.4 每项扩展消灭的具体失败

| 扩展 | 消灭的**具体**失败 | 证据 |
|---|---|---|
| 金标 expectedSlug 保护 | 金标期望节点的材料被当近重复退休 → 该题的材料交付（F1/F2）与后续讲题引用失去依据；金标是**测试集**，其节点材料不得被卫生动作削薄 | 129 条金标是判读基准（R3 §3） |
| THIN ≤2 保护 | 去重把「恰 2 条」的节点降到 1 条、把「恰 1 条」降到 0 条 → 节点从可教变不可教（R3:230 的 D5） | 本会话：0 节点降到 0；238 个 1 条节点前后不变 |
| 冻结 tools/tests/gates 扫描面 | 门失去锚点、`ghost_aliases` 变红、dense 被迫重导（R3:223） | 现有 `load_anchor_slugs`/`load_alias_backed_slugs` |

---

## 3. 判读（写死）

### 3.1 判据一：特征快照 **diff = 0**

去重只**删除材料**，不动节点/别名/边界/关系。三层：

1. **节点表面层**：主包 `moe-2025-four-subjects-v1.json` 的 sha **必须不变**（`96246c78…`）；3,705 个节点的 `(kind, slug, name, sorted aliases, boundary)` 聚合 sha 与写前相同（基线 `c36840819a737891c42731b54fb08a52aa1289f42d6c8fad42baf4a078045524`，与 B1 同口径）。
2. **抽取器层（JVM）**：用真实 `KnowledgeSearchFeatureExtractor.fromNode` 对写前/写后两份包逐节点抽特征，`(node_id, feature)` 排序后的 sha **相等**；期望 diff = 0。
3. **金标装置层（JVM）**：`build/golden-jvm-metrics.txt` 与 `build/production-lexical-leg.tsv` 写前/写后**逐字节相同**（文件不含墙钟；见 B1 §3.1 的同口径）。

**为什么可能为 0**：`apply_plan` 只从 sidecar 的 `materials[]` 移除条目（工具 `:813-822`），不写主包、不写 `alias_map`；`alias_backed` 保护确保没有材料是某别名的唯一支撑（否则会逼出别名/dense 连锁）。非 0 ⇒ 停，先定位未文档化通路。

### 3.2 判据二：vec-ids **diff = 0**

| 检查 | 期望 |
|---|---|
| `bge-small-zh-int8.vec` sha | `70329115…`（不变） |
| `.vec.json` sha / `packSha256` | `6612a28d…` / `96246c78…`（不变，== 主包） |
| `vectorCount` / `format.count` / id 块 sha | `44,121` / `44,121` / `8f177832…`（不变） |

去重不进 dense；别名不变 ⇒ 向量集不变。若 id 集变化 ⇒ 有人重建了别名或 dense，本臂越界。

### 3.3 判据三：锚点零误伤

1. `protected ∩ retire = ∅`（本会话基线 **0**；扩保护后仍必须 0）。
2. 10 条登记锚点在写后 `load_anchor_slugs(present)` 里**仍在位**（工具写后自检）。
3. 金标 128 节点的 3,847 条材料、THIN 432 节点的 626 条材料**一条都没退休**。
4. `tools/tests/gates` 引用的 slug **一条都没退休**。
5. 保护清单（slug → 来源）与 sha256 写进 `plan.json`，且写后重算的保护集合与计划一致（R3:232「保护清单未漂移」）。

### 3.4 判据四：近重复残留

**冻结口径 = 「未解释的残留 = 0」**（R3 §1.5：字面「近重复=0」不可达，因为有设计内保留）：

- 369 对的逐对处置（本会话在扩保护计划上实算）：

| 处置 | 对数 | 说明 |
|---|---:|---|
| 标题门丢弃 | **122** | 规范化标题不同（含 Asin/Acos 类） |
| 实体门丢弃 | **22** | 标题相同但正文有 ≤6 字实词替换 |
| 已退休 | **187** | 该对至少一端被退休（与保留者直接相似） |
| 双方受保护 | **36** | 两端都在保护集 |
| 链式跳过 | **2** | 与最终保留者不直接相似（不链式退休） |
| **未解释** | **0** | 无 |

- 两条链式跳过的实际归属已核：`纯净物` 簇（kept `ext-che-025004618b-007`）与 `周期数列的常见形式` 簇（kept `ext-mat-b74b04fd74-002b`，protected）——两者都是“与 kept 不直接相似”的成员，不是漏网。
- 判据：**未解释 = 0**；每条保留都必须在上表有来源。不得用「有保护」一句话概括而不给来源。

### 3.5 判据五：覆盖报告（退休比例）

| 项 | 值 |
|---|---:|
| 退休材料 / 全包 | **168 / 65,021 = 0.2584%** |
| 分科 | CHEMISTRY 72 / PHYSICS 51 / MATH 43 / BIOLOGY 2 |
| 影响节点 | **145** |
| 退休原因 | near-duplicate 153 / near-duplicate of protected anchor 15 |
| skip | 26（链式/实体门） |
| 节点降到 0 条 | **0** |
| 恰 1 条的节点 | **238（前后相同）** |
| 受影响 sidecar | **11**（移除数：v2-01:1、v2-02:4、v2-03:1、v2-05:1、v2-13:3、v2-14:1、v2-16:2、v2-19:39、v2-20:76、v2-21:39、v2-22:1） |
| 预测 `contentVersion` | **`2cd974dbada7cb2f`**（version 27） |

- 覆盖必须**同时报分母、分子、比例、分科、受影响节点**；不得只报「退休 168 条」。
- `0.2584%` 是保守口径（alias-backed 35,515 + 金标 3,847 + THIN 626 保护后的结果），**不得**写成「去重已完成」。

### 3.6 判据六：包身份三方一致

| 身份 | 写后期望 |
|---|---|
| A 内容 | `version=27` / `contentVersion=2cd974dbada7cb2f`（== 写后 `content_version(pack, sidecars)` 复算；≠ 则 `INPUT_DRIFT`/`SERIALIZATION_DRIFT`，停） |
| B 主包 | `96246c78…`（**不变**） |
| C dense | `packSha256 == B` / `vectorCount=44,121` / `.vec` `70329115…` |
| 副本 | release ↔ staging **30/30 逐字节相同** |

### 3.7 范围守卫（只允许这些变化）

1. 11 个 sidecar：只允许 `materials[]` 里 **168 条**被移除；每条的 `(slug, kept_slug, node, subject)` 与 plan 的 `retire` 逐字节一致；其余材料与字段不变；sidecar 数不变。
2. 判定表：只允许被退休的 168 行 action→`SKIP` + note 追加（工具的 `apply_plan` 行为）；其它行不变。
3. `material_retire.csv`：**append-only**，只新增 168 行；已有行不变。
4. manifest：只改 `version` 与 `contentVersion`；`schemaVersion`/`packId`/`retired` 不变。
5. **零改**：主包、`alias_map.csv`、`content_audit_anchors.csv`、dense 两件、金标、`moe-2025-teaching-support-v2-index.json`、2020 两件。

### 3.8 装置门

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `PYTHONPATH=tools python -m kb_coverage.dedupe_near_dup_materials --dry-run` | exit 0；打印 `retire=168`、`protected∩retire=0`、`golden/thin` 计数；落新 `plan.json` |
| 2 | `python -m unittest tools.tests.test_kb_dedupe_anchor_protection -v` | exit 0（新增用例） |
| 3 | `python tools/ci/run_kb_checks.py && python tools/kb_coverage/alignment.py --check` | exit 0（现有门） |
| 4 | `./gradlew :app:assembleDebug --console=plain` | exit 0（资源打包） |

- 门 1 的 dry-run 必须在工具改动**之后**重跑；**不得**沿用批次31C 的 182 条旧 plan。
- 任一门红 ⇒ 不 apply、不跑设备、不采纳。

### 3.9 报告义务（§6 至少包含）

- 阈值证据表（369/116 的标题同异分布）；选择的阈值与两道门的口径。
- 保护清单与来源（10 / 35,515 / 3,847 / 626；合并 37,512；sha256）；`protected∩retire`。
- 168 条退休的逐条 `(slug, kept_slug, node, subject, reason)`；11 个 sidecar 的移除数。
- 369 对的处置表 + 未解释 0；两条链式跳过的簇归因。
- 节点材料数分布（0 条 = 0；1 条 = 238）；F1/F2 的旁证（节点不降到 0 ⇒ 结构上不变）。
- 三层特征 diff、dense 三值、三方一致、release↔staging 30/30。
- 装置门 exit code；§4 逐文件写后 sha。

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 工具 / 表 / 台账（工作树，非 HEAD）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `tools/kb_coverage/dedupe_near_dup_materials.py` | 新增 golden/THIN 保护函数；并入 `extra_protected`；counts/自检扩展；**阈值保持 0.70 + 两门** | `f4ac9f72fcae641d8b0c9d1342a76c6c9451a022adaa4fb7013ae7e47fe10d6e`（**未跟踪 `??`**） | §6 |
| 2 | `tools/kb_coverage/tables/material_judgments/BIOLOGY.csv` | 168 行中被退休行 action→SKIP（本文件子集） | `abe5ed5a734780a3d31571b03630b648b5e9752733981456f4e09c081b6fd065`（`M`） | §6 |
| 3 | `.../CHEMISTRY.csv` | 同上 | `ce2416ae1b5620c5d5b1e193a0baed9ac05f25ba34f14c8306a58be97217b209`（`M`） | §6 |
| 4 | `.../MATH.csv` | 同上 | `f80023fe23d4241870ca764104fc4d059f5acd976038bf8ce65870dd4a3cd848`（`M`） | §6 |
| 5 | `.../PHYSICS.csv` | 同上 | `6e4907487ac2008bec2e42d271d77056fe6d6741d1be4baae57918c437d3b7da`（`M`） | §6 |
| 6 | `.../UNASSIGNED.csv` | 同上 | `28ed802b9a11ffefb46c881ea9b47814246d1bef3273d75e7fbaf69935322def`（`M`） | §6 |
| 7 | `tools/kb_build/tables/material_retire.csv` | **append-only** 168 行 | `b9948a1c4ed7377e4f3a32da4216507e753ef4be6454e9251660988f624de360`（**未跟踪 `??`**） | §6 |

- 触碰面已有 `M`/`??`，本臂在**现有工作树内容**上编辑；回滚用备份覆盖（不是 `git checkout`），否则会吞掉他线改动。

### 4.2 B. 内容包（11 sidecar + manifest；写前干净）

| # | 路径 | 移除数 | pre-sha256（写前） | post-sha256 |
|---:|---|---:|---|---|
| 1 | `.../moe-2025-teaching-support-v2-01.json` | 1 | `feddca192e10c26bbc1b1663c6681095bfe7196f2073a5574c8c98a3d73661f3` | §6 |
| 2 | `.../moe-2025-teaching-support-v2-02.json` | 4 | `ba443288dca7e10e598a26934516348b47055eb017f4fd9bcda1f7d9f6ec56e1` | §6 |
| 3 | `.../moe-2025-teaching-support-v2-03.json` | 1 | `23e08d3a63c8ab4a17216cb1ea3e856a01c33a95f15b05eb02ecfa88d540a7ad` | §6 |
| 4 | `.../moe-2025-teaching-support-v2-05.json` | 1 | `5d2fc2623a5efc0f8849fc99d3cea967c813263e9ee879a54f6dbf776fe96fb8` | §6 |
| 5 | `.../moe-2025-teaching-support-v2-13.json` | 3 | `ce38b4d4cf75cfde7a22230f16c37c967eec6397f68e0284ae405ed39ddc5353` | §6 |
| 6 | `.../moe-2025-teaching-support-v2-14.json` | 1 | `1d790d3cc8068d7ed5a36babcab6f1e8209d71706821e685cf0abdd59c895c4f` | §6 |
| 7 | `.../moe-2025-teaching-support-v2-16.json` | 2 | `7612aa625a6097b7b655eab4cae4329762b2ac6125498e96fdd211d0ae7aa86b` | §6 |
| 8 | `.../moe-2025-teaching-support-v2-19.json` | 39 | `3224da13ff1336eef6dc10bc85c71ae628e76f117b3de557733d2aacf98faff8` | §6 |
| 9 | `.../moe-2025-teaching-support-v2-20.json` | 76 | `c0064dead7ef985416051f383ca131f63a24dfb89776ef3dff01f95185c0e2f4` | §6 |
| 10 | `.../moe-2025-teaching-support-v2-21.json` | 39 | `dd9607445f2b699181e57b41ab055e456b77509a4f5f555d9f42966525e859f0` | §6 |
| 11 | `.../moe-2025-teaching-support-v2-22.json` | 1 | `88b393fa3a37f9430b7b3459798d3f78de28d53d2a3811fb785dfc72752caf37` | §6 |
| 12 | `.../moe-2025-update-manifest.json` | — | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` | §6 |

### 4.3 C. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `tools/tests/test_kb_dedupe_anchor_protection.py` | 金标/THIN 保护用例、`protected∩retire=∅`、0→0 节点、3 材料节点可退 |
| 2 | `build/agent-batch33/b3/plan.json` 与 `excluded` 类产物 | 执行轮生成（build/ 不入仓） |

### 4.4 D. 零触碰（登记供核验）

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 2 | `tools/kb_build/tables/alias_map.csv` | `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451` |
| 3 | `tools/kb_build/tables/content_audit_anchors.csv` | `8d1265fc7df1426e414048def038c450bb75d3f36d32ea6c59ca55d0b0e2a51b` |
| 4 | `.../dense/bge-small-zh-int8.vec` | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 5 | `.../dense/bge-small-zh-int8.vec.json` | `6612a28d9dd71e140e91f4300c142f8ca8dae027ca58afde88e0f7d3f749dc4b` |
| 6 | `.../moe-2025-teaching-support-v2-index.json` | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 7 | `.../moe-2020-foundation-v1.json` / `.../moe-2020-teaching-support-v1.json` | `9ceca927…` / `5aea4e60…` |
| 8 | `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 9 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |

### 4.5 写前备份 / 回滚 / 采纳

**写前备份（apply 之前）**

1. 把 §4.1 的 7 件、§4.2 的 12 件复制到 `build/agent-batch33/b3/backup/`，落 `sha256sum` 全表；另存 `before/` 供语义 diff。
2. `git status --porcelain -- <§4.2 路径>` 必须为空；§4.1 的 `M/??` 按原样备份，**不得**当成干净基线覆盖。
3. 复核：manifest 26/`aea886e42b36dcb2`；主包 `96246c78…`；dense `70329115…`；`release==staging 30/30`；工具阈值/两门与 §1.2 一致。
4. **必须重跑 dry-run 生成新 plan**（`golden/thin` 保护生效）；旧 `build/agent-batch31c/plan.json`（182）只作基线对照，不得 apply。

**回滚（任一判据失败 / 未解释残留非 0 / 范围守卫失败 / 三方不一致）**

1. 不得整树 `git checkout`；用备份覆盖 §4.1 的 7 件与 §4.2 的 12 件；删除 §4.3 的新测试（若已入库则回滚提交）。
2. 设备侧：内容戳从 `2cd974db…` 回到 `aea886e4…`，下次启动全量调和（≈20 s，无数据丢失）；**不需要** `pm clear`、**不需要** DB 迁移。
3. 核验：25 sidecar 聚合 sha 回到 `eac4eff7…`；`material_retire.csv` 回到 `b9948a1c…`（append 的 168 行必须不存在）；判定表回到 §4.1 的 pre-sha；主包/dense/金标/alias_map 与 §4.4 一致。
4. 复跑 §3.8 门 1/3；以 `before/` 为基准，**工具与内容包都回到 §4.1/§4.2 的 pre-sha** 后，dry-run 应报 `retire=182`（旧保护口径）。若报 168，说明工具或包没有真正回滚，停。

**采纳**

保留 §4.1/§4.2 的写面与 §4.3 的测试；§6 登记 168 条退休、11 文件 post-sha、plan sha、三方一致、门 exit code。**不**改主包/别名/dense/金标。

---

## 5. 未做 / 未验证（写作时点）

- **未写任何内容包/工具**：§1–§2 是冻结定义；168 条是本会话**离线计划**读数，不是已落盘。
- **未跑 Gradle/设备/JVM**：§3.1 的抽取器层/金标层、§3.8 的门均 `NOT_RUN`。
- **阈值证据来自批次31C 的 pairs**：369 对是 0.70 候选集；0.90 的 116 对是从该集合重算的，**不是**独立跑一遍 0.90 的 MinHash（0.90 候选集可能包含 0.70 粗筛漏掉的对；但 0.60 粗筛已覆盖，差异未实测）。
- **金标 129→128 unique expectedSlug**：有 1 条重复 expectedSlug，未逐条列出。
- **THIN 定义取「去重前 ≤2」**：去重后新出现的 1 条节点不再补保护（本批实测 0 个节点降到 1，故无影响；若未来规则变化需重判）。
- **`material_retire.csv` 未跟踪**：回滚依赖备份，不是 git。
- **F1/F2/F3 未重算**：本臂不改节点材料可得性（0 节点降到 0），F1/F2 结构上不变；F3 的槽位分布可能因材料删除而变，**未测**，只作报告项。
- **`run_kb_checks`/`alignment` 未跑**：§3.8 是判据，不是通过声明。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1/§4.2 逐文件 post-sha；新 `plan.json` 的 sha 与 `retire=168` 复核；`protected∩retire=0`、golden/thin 计数、10 锚点写后仍在位；369 对处置表复算（未解释必须 0）；168 条逐条清单与 11 sidecar 移除数；节点材料数分布（0 条=0、1 条=238）；三层特征 diff 与 dense 三值；三方一致（27/`2cd974dbada7cb2f`/`96246c78…`/44,121）；release↔staging 30/30；门 1–4 的 exit code；回滚时的 `pm clear` 不需要的说明。未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 6. 执行轮记录（2026-11-03 08:1x–09:0x；§1–§4 未改）

### 6.0 与 §0 的一处偏差（先做，必须先说）

执行轮收到的指令是**从「未去重」基线重算**（不在 32B 的 182 条上叠加），所以本轮的
第 1 步是**恢复未退休标记**，而不是直接跑 dry：

- 事实核对：182 条「已退休」材料**全部仍在包内**（`materials[]` 65,021 条，`slug` 交集 182/182）
  ——内容层早已被 32B 回滚，留下的只有标记。
- 判定表：179 行 `action=SKIP` + note 尾巴 `v27 near-dup retired; kept=<slug>`（`apply_plan` 写的）
  → 恢复为 `action=MATERIAL` 并剥掉尾巴；**只改 `action`/`note` 两列**（实测 changed=179，
  fields={action,note}，行数不变）。另 3 条无判定行（`bio-kb3-f5-s5-2-n11`、
  `chem-kb-periodic-law-essence` 为非 ext- 手写材料；`ext-che-84c9f9347b-011c` 无对应判定行），只动台账。
- 台账 `material_retire.csv`：182 行填 `reverted_at`（该列即为此设计），不删行。
- 工具随之加了一条**necessary** 口径：`apply` 的"已退休"过滤改为只看 `reverted_at` 为空的行
  ——否则回滚后再退休同一材料会被静默跳过，产出 168 条退休却 0 行台账。
- 该步写盘：判定表 4 片（MATH/PHYSICS/CHEMISTRY/BIOLOGY）+ 台账；内容包**零改**。
  脚本 `build/agent-batch33/b3/restore_unretired.py`，读数 `restore-unretired.json`。

### 6.1 写前守卫

| 项 | 值 |
|---|---|
| HEAD / origin/main | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` / 同值 |
| porcelain | 561 |
| §4.1 七件 + §4.4 九件 pre-sha | **15/15 命中**（一次 `sha256sum` 逐条比对，mismatch 0） |
| release ↔ staging | 30/30 逐字节相同 |
| manifest | `26` / `aea886e42b36dcb2` / `retired=1222` |
| 备份 | 37 件 → `build/agent-batch33/b3/backup/`（1 工具 + 5 判定片 + 1 台账 + 30 包 JSON） |

`judgments.load_all() → write_all()` 的**往返逐字节自检**先跑过（五片全部 identical），
所以恢复写盘没有行尾/表头噪音。

### 6.2 门 1：dry-run（工具改动后重跑，未沿用 31C 的 182）

`PYTHONPATH=tools python -m kb_coverage.dedupe_near_dup_materials --dry-run --out-dir build/agent-batch33/b3`
→ **exit 0**，输出与 §0.3 **逐项相同**：

```
材料 65021 / 节点 3280；候选对 369；簇 209；拟退休 168；SKIP 26；受保护锚点 10；
标题别名唯一支撑保护 35515；金标节点 128（3847 条材料）；THIN 节点 432（626 条材料）；
保护合计 37512；标题告警 0（63.0s）
```

`plan.json` sha `ca0a20eb7163f5fec1b64c8ada4fad24f4faf25ff603193626e6df15efc7b850`；
含 `protected`（37,508 条 slug→来源）与 `protected_sha256=37e9afe0d2c4…`。
`protected ∩ retire = 0`（实测）。分科 72/51/43/2、原因 153+15、影响 145 节点均与 §3.5 一致；
预测内容戳 `2cd974dbada7cb2f` 与 §0.3 一致（内存重放实测）。

### 6.3 20 簇抽检（人工）

`build/agent-batch33/b3/manual-review-20.csv`（20 行）+ `review-20-raw.txt`（逐成员标题/正文片段）。
抽样规则：四科各按簇大小降序、轮转取，直到 20 条（CHEMISTRY 6 / PHYSICS 6 / MATH 6 / BIOLOGY 2）。
**20/20 判定 KEEP（同意退休）**，无假阳性。值得记的两条：

- `功能关系` / `三角平方差` 的保留者是 **alias 唯一支撑**（受保护），因此"保留正文更长"的规则被保护覆盖（§2 设计如此）；
- `纯净物` 簇 8 成员里 3 条是**链式跳过**（与最终 kept 不直接相似），不是漏网。

### 6.4 执行 RETIRED：**一次写入，自检脚本在我自己的新增代码里崩了（已修）**

`--apply build/agent-batch33/b3/plan.json --out-dir build/agent-batch33/b3`：
写入阶段全部完成（判定表 / 11 sidecar / 台账 / 168 个 `retired-json`），随后**我新加的
写后自检**抛 `NameError: name 'groups' is not defined`（`apply_plan` 里引用了 `main` 的局部变量），
进程 exit 1。修复：改用 `nodes_before`（从已加载的 sidecar 现算），见 6.9。
**该崩溃没有造成半写状态**：写入顺序是"判定表 → 侧车 → 台账"，崩在最后的自检；
随后用独立脚本 `verify_b3.py` 把 §3.3/§3.4/§3.5/§3.7 的全部判据重跑了一遍（两处：staging、release，均 ok=True）。

### 6.5 范围守卫（§3.7，`verify_b3.py` 实测）

| 判据 | 读数 |
|---|---|
| 11 个 sidecar 的移除数 | 1 / 4 / 1 / 1 / 3 / 1 / 2 / 39 / 76 / 39 / 1（=168，与 §3.5 逐项相同） |
| 逐条 `(slug, kept_slug, node, subject)` 对 plan | 0 处不符 |
| 新增/改动的材料 | **0**（`no_added_or_edited_materials` ok） |
| 判定表 | 165 行 action→SKIP + note（168 中 3 条无判定行）；其余行 **0** 变化 |
| 台账 | 新增 168 行（`reverted_at` 空）；182 行已回滚；合计 350 |
| manifest | `27` / `2cd974dbada7cb2f`；`schemaVersion`/`packId`/`retired(1222)` 不变 |
| 零触碰 | 主包 `96246c78…`、index `2da0b9c5…`、alias_map `5e8ed8ba…`、anchors `8d1265fc…`、金标 `eabef102…`、`.vec` `70329115…`、`.vec.json` `6612a28d…` 全部不变 |

### 6.6 锚点零误伤（§3.3）

`protected∩retire = 0`；10 条登记锚点写后仍在包内（`lost=[]`）；金标 3,847 条材料
`lost=0`；THIN 626 条材料 `lost=0`；写后按新包重算的保护清单与 plan **逐条相同**
（37,508 / 37,508，`only_plan=[] only_now=[]`）。节点材料数：**0 个降到 0 条**；
恰 1 条的节点 **238 → 238**。

### 6.7 369 对处置（§3.4）

| 处置 | 本会话实测 | 预注册 |
|---|---:|---:|
| 标题门丢弃 | **122** | 122 |
| 实体门丢弃 | **22** | 22 |
| 已退休 | **187** | 187 |
| 双方受保护 | **36** | 36 |
| 链式跳过 | **2** | 2 |
| **未解释** | **0** | 0 |

2 条链式跳过的归属实测 = `纯净物`（`ext-che-2728b2ed9a-016` × `ext-che-4f82967fc2-009`，0.798）
与 `周期数列的常见形式`（`ext-mat-dc17268a83-002` × `ext-mat-dde1187192-002c`，0.841），与 §3.4 的点名一致。

### 6.8 判据一/二：特征与 vec

- `python -I build/agent-batch33/content/export_invariants.py --diff-against build/agent-batch33/content`
  → `features-baseline.txt` **IDENTICAL**（`f460819a…`，3,280 节点 / 541,252 特征）、
  `vec-ids-baseline.txt` **IDENTICAL**（`dedc652d…`，44,121 行 / 3,280 唯一 id）。
- 主包 `96246c78…` 逐字节不变 ⇒ 3,705 节点表面零改。
- `materials-sample-20.txt` **有差异，但只在两行头注**（`corpus=65021→64853`、逐格计数）；
  20 条样本的正文**逐字节相同**（未选中被退休材料）。这是材料侧交付面的预期变化，不是特征/向量变化。
- dense 三值不变：`.vec` `70329115…` / id 块 `8f177832…`（3,320,586 B）/ `.vec.json` `6612a28d…`；
  `vectorCount=44,121`、`format.count=44,121`、`packSha256=96246c78…`。
- **JVM 层（§3.1-2/3）**：`./gradlew :core:data:testDebugUnitTest --tests '*GoldenRetrievalJvmTest*'
  --tests '*ProductionLexicalLegExportTest*'` → `GoldenRetrievalJvmTest` **2/2 通过**；
  `build/golden-jvm-metrics.txt` 与 `build/production-lexical-leg.tsv` 与写前**逐字节相同**
  （`5126f187…` / `690402f8…`）。`ProductionLexicalLegExportTest` 仍红，原因与本批无关：
  它 pin 的是工作区（他线 32B）的 v32 值 `0.6130490956072351`，实测 `0.6178294573643413`
  = **HEAD 的 pin**；该测试文件本批未改。
- 预注册 §3.1-1 的 `c36840819a…` 仍**不复现**（聚合口径未落盘，B1/B2 已两次记录）；
  替代证据是主包字节级不变（更强）。

### 6.9 工具改动（§4.1-1，post-sha `3d23069e770c7a0e650d08f4182d447f0b3d0c0e7f27000faf46d96da4c58644`）

1. `_golden_expected_slugs` / `load_golden_expected_slugs`：金标 129 条 → 128 唯一；
   文件缺失或全空 ⇒ **抛错**。
2. `load_thin_node_slugs`：去重前 ≤2 材料的节点。
3. `merge_protections`：同一 slug 多来源全保留。
4. `counts` 增 `golden_protected/thin_protected/golden_nodes/thin_nodes/protected_total`。
5. `plan.json` 增 `protected` + `protected_sha256`。
6. 写后自检增：受保护材料被退休 / THIN 被移除 / 节点降到 0 条（三处硬失败）。
7. 台账"已退休"过滤只看 `reverted_at` 为空的行（见 6.0）。
8. `--out-dir`：新一批产物不覆盖 31C 基线。

### 6.10 门

| # | 命令 | 结果 |
|---:|---|---|
| 1 | `--dry-run --out-dir build/agent-batch33/b3` | **exit 0**（6.2） |
| 2 | `python -m unittest tools.tests.test_kb_dedupe_anchor_protection -v` | **exit 0**（10/10 通过；新增文件 §4.3-1） |
| 2b | `python -m unittest tools.tests.test_kb_dedupe_near_dup_materials -v` | **exit 0**（7/7；台账 350 行仍无锚点） |
| 3 | `python tools/ci/run_kb_checks.py` + `PYTHONPATH=tools python tools/kb_coverage/alignment.py --check` | **exit 0 / exit 0** |
| 4 | `./gradlew :app:assembleDebug --console=plain` | **exit 0**（BUILD SUCCESSFUL in 12s） |

### 6.11 执行轮发现的问题（登记，不在本批修）

1. **`material_bindings.csv` 出现 1 行悬空引用**：`bio-kb3-f5-s5-2-n11`（= 被判退休的 ATP 那条）
   在权威表里仍有一行（`point_slug=atp的合成和利用`）。`run_kb_checks` 的 INFO 因此从
   `材料缺 0` 变 `材料缺 1`；F9 门只约束"节点缺/空/欠账"，故仍绿。§3.7-5 的零触碰清单
   不含该表，所以不算越界；但下次重建该表时这行会消失，需在批报登记。
2. **B1（摘要-only）仍未落地**：本批写前树是 v26（32B 后 B1 被外部回滚，见 B2 §7.7）。
   本批 promote 后是 `27 / 2cd974dbada7cb2f`，**不含** 127 条摘要改写。
3. `apply_plan` 的自检崩溃（6.4）是我本轮新增代码的缺陷，已修；工具现 post-sha 为修后版本，
   而 `plan.json` 的 `file_sha256` 记录的是**崩溃前**的工具 sha——故 `--apply` 重跑会被 sha 守卫拒写，
   这是正确行为（不重跑，状态已核验）。

### 6.12 回滚说明

设备侧不需要 `pm clear`：内容戳 `2cd974dbada7cb2f` → `aea886e42b36dcb2`，下次启动全量调和（≈20 s）。
内容回滚用 `build/agent-batch33/b3/backup/`（37 件）；判定表/台账回滚后把 182 行
`reverted_at` 清空即可恢复 31C 台账语义。

