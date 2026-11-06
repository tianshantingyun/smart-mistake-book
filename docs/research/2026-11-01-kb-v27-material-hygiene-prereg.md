# KB v27 材料卫生（去重 / 摘要 / 标题 / 别名）：预注册（2026-11-01）

> **性质**：出数前冻结。§1–§4 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§4。
> **写作时点**：2026-11-01；HEAD `f0512a476d09e5b2aa7a7170954530cb9633be8c`（`git rev-parse HEAD` 实跑），`origin/main` 同值。
> **上游**：当前 v26 release（manifest `version=26` / `contentVersion=aea886e42b36dcb2` / 65,021 材料）；`tools/kb_build/rebuild_aliases.py:57-99`（`title_usable` 形状门）；`docs/research/2026-10-26-kb-content-derived-alias-rules.md`（T/C 派生规则）；`docs/research/2026-11-01-kb-v26-a-layer-expanded-prereg.md`（39 节点范围）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 sidecar/判定表/别名表。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
git rev-parse HEAD                       # f0512a476d09e5b2aa7a7170954530cb9633be8c
git rev-parse origin/main                # f0512a476d09e5b2aa7a7170954530cb9633be8c  → 同值 ✓
git status --porcelain | wc -l           # 736
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   234  M
#   502 ??
ls docs/research/2026-11-01-kb-v27-material-hygiene-prereg.md
#   No such file or directory  → 本次是新建，只写一次
```

- 736 项脏项属他线与 v25/v26 已写面；本预注册不触碰、不裹挟、不回滚。
- **当前工作树有未跟踪的批次38视觉文件编译错误**（`TutorVisualPreviewRenderer.kt:254:34`，`String?` 不能赋给 `String`）；§3 双门当前不可跑，执行轮必须先清该他线阻塞（不归因本批）。

### 0.2 数字口径（本会话实算，v26 release 为当前基线）

| 事实 | 值 | 方式 |
|---|---:|---|
| 节点 / 材料 | **3,280 / 65,021**（MATH 14,497 / PHYSICS 18,501 / CHEMISTRY 18,315 / BIOLOGY 13,708） | 遍历主包 + 25 sidecar |
| 绑定 role | **65,021 条全部 `PRIMARY`**（无 SUPPORTING） | 遍历 `materials[].bindings[].role` |
| v26 manifest | `version=26` / `schemaVersion=2` / `contentVersion=aea886e42b36dcb2` / `packId=moe-2025-four-subjects-v1`；sha `370b0979…` | 读 manifest + `sha256sum` |
| 主包 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` | `sha256sum` |
| v2-25 | `932691822c12510e64385093255b91dc6f52ab3e7c44f3dfac01ac466e501552`，**159 条** | `sha256sum` + 读取 |
| 25-sidecar 聚合 | `9e7d3e13cba98da236001ea94e3f0f372e962e7661e06ac9f23f3e4b2eab5473` | 按 v2-01…25 顺序连接 basename+sha |
| dense `.vec` / 旁车 | `70329115…` / `5cbbcb6f…` | `sha256sum` |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | `sha256sum` |
| extraction_state | `19008d07fa6f753b9ef240c4b7c7ce7899f9089448a0bc25d4717c3ff104693f` | `sha256sum` |
| 判定表 MATH / PHYSICS | `634b5e75…`（44,428）/ `41d4ca9c…`（38,087） | `sha256sum` |
| alias_map / exceptions / dropped | `5e8ed8ba…`（37,569 数据行）/ `7a8e13ed…`（36 行）/ `da4543b3…`（22,561 行） | `sha256sum` + 行数 |

---

## 1. 内容（冻结）

### 1.1 ① 近重复去重（归一后 title+summary 完全相同）

**归一**：`NFKC` + `casefold` + 去掉全部 Unicode 空白；不删标点、不做同义改写。按 `(归一 title, 归一 summary)` 分组。

**实测（v26 release，65,021 材料）**：

| 项 | 读数 |
|---|---:|
| 重复组 | **87** |
| 组内材料 | **180** |
| 多余条目（应 RETIRED） | **93** |
| 组大小分布 | **82 组 ×2 / 4 组 ×3 / 1 组 ×4** |
| 跨节点组 | **1**（`电场中的功能关系` ↔ `功能关系`，title「五种常见的功能关系」，2 条） |

**保留规则（冻结）**：组内保留**内容最长**的那条（`contentMarkdown` 字符数最大）；等长时保留 slug 后缀最短（无 `b/c/d` 尾）者；再等长按 slug 字典序最小。理由：65,021 条绑定全部是 `PRIMARY`，没有 role 可区分“主/辅”；用“内容最长”保住信息量，避免按 slug 误删长正文。被保留者记 `kept_slug`，其余 93 条记 RETIRED。

**逐对证据（执行轮产物）**：`build/agent-batch31b/v27-dedupe-pairs.csv`，列：

```text
group_key, group_size, kept_slug, retired_slug, subject, node_slug, title, summary,
kept_content_len, retired_content_len, kept_source_index, retired_source_index,
pre_sidecar_sha256, material_json_sha256, reason
```

**样本（本会话实读，证明分组口径；完整 87 组由执行轮落表）**：

| group_size | kept_slug（按规则） | retired_slug（示例） | node | title |
|---:|---|---|---|---|
| 4 | `bio-kb3-f4-s10-n04`（75 字） | `bio-kb3-f4-s10-n03`（73）/ `-n06`（53）/ `-n07`（55） | `细胞中的糖类` | 细胞中的糖类·单糖 |
| 3 | `bio-fj-05-chromatin-chromosome`（90，等长取字典序最小） | `bio-kb-chromatin-chromosome-same-substance`（90）/ `bio-kb2-05-chromatin-chromosome`（76） | `细胞核的结构和功能` | 染色质与染色体的关系 |
| 3 | `bio-kb3-f4-s10-n08`（93） | `-n09`（88）/ `-n10`（83） | `细胞中的糖类` | 细胞中的糖类·二糖 |
| 3 | `ext-che-7b3c6d5a4e-013`（214） | `ext-che-b0216673fb-011`（149）/ `ext-che-a2ca03a8e1-017`（102） | `元素周期律的内容及实质` | 元素周期律的内容与实质 |
| 2 | `ext-che-84c9f9347b-011`（175，等长取后缀最短） | `ext-che-84c9f9347b-011c`（175） | `碳中和` | 碳中和的含义与实现途径 |
| 2 | `phys-mo-l09-newton-laws-basic-b-2`（119） | `phys-kb-newton-second-law`（80） | `牛顿第二定律` | 牛顿第二定律 |

> 等长示例说明：`ext-che-84c9f9347b-011c` 与 `-011` 内容同为 175 字，按“后缀最短”保留 `-011`；`bio-fj-05…` 与 `bio-kb…` 同为 90 字，按“slug 字典序最小”保留 `bio-fj-05…`。表中 kept 列按**最终规则**执行，完整 87 组以执行轮脚本输出为准。

**RETIRED 机制（冻结）**：当前 pack material 无 `status` 字段（实体默认 `ACTIVE`，`KnowledgeTeachingMaterialEntities.kt:59-60`），因此 RETIRED 采用**可逆台账 + sidecar 移除 + 判定行置 SKIP**：
1. 把被退休材料的**完整 JSON**（含 `slug`、`title`、`summaryMarkdown`、`contentMarkdown`、`bindings`、`sourceIndex`）写入 `tools/kb_build/tables/material_retire.csv`（追加式，见 §4.2）。
2. 从所在 sidecar 的 `materials[]` 按 `sourceIndex` 移除该条。
3. 判定表对应行 `action` 由 `MATERIAL` 改为 `SKIP`，`note` 记 `v27 duplicate retired; kept=<kept_slug>`；不删行（保留可逆证据）。
4. 材料总数预期 65,021 → **64,928**（−93）；节点数 3,280 不变；主包不变。

### 1.2 ② 退化摘要修复（summary ≤8 字或复读 title）

**判据**：`summaryMarkdown.strip()` 的 Unicode 字符数 **≤8**，或归一后与 `title` 完全相同。

**实测**：**127 条**（全部 ≤8 字；其中 **1 条**同时复读 title）。样本：

| slug | 现 summary | title |
|---|---|---|
| `phys-hj-090-variable-force-impulse` | 求变力冲量 | 变力冲量的计算方法 |
| `phys-hj-093-momentum-conservation-forms` | 列动量守恒方程 | 动量守恒定律的表达式 |
| `phys-hj-095-explosion-three-rules` | 分析爆炸类问题 | 爆炸现象的三个规律 |
| `phys-hj-098-elastic-collision-formulas` | 求碰后两球速度 | 一动碰一静弹性碰撞结论 |
| `phys-hj-163-ui-graph-resistance` | 由图像求元件电阻 | U-I 图像中电阻的理解 |

**修复规则（冻结）**：
1. 取 `contentMarkdown` 的**首句**（按 `。！？；` 切分，保留句末标点），去掉首尾空白与 Markdown 残片。
2. 若首句仍 ≤8 字或归一后仍等于 title，取**前两句**；仍不合格则记 `NOT_FIXED`，不硬写。
3. 新 summary 必须 **>8 字**且 **≠title**；**正文 `contentMarkdown` 一字不改**；`title/applicability/boundary/bindings/type` 不变。
4. 写判定表 `summary` 列；重新 materialize 该行（或全量 materialize），sidecar `summaryMarkdown` 随之为新值。
5. 逐条证据落 `build/agent-batch31b/v27-summary-repairs.csv`：`slug, old_summary, old_len, first_sentence, new_summary, new_len, status(FIXED/NOT_FIXED), content_sha256_before, content_sha256_after`；`content_sha256_before == after` 是硬断言。

**预期**：127 条中 FIXED 数由执行轮实测；`NOT_FIXED` 逐条登记，不改正文凑数。

### 1.3 ③ title 合规化（>24 字/含标点但语义完整）

**实测（v26 release）**：

| 项 | 读数 |
|---|---:|
| `len(title) > 24` | **20,770** |
| 含标点（中英文句末/逗号/冒号/顿号/括号/书名号/引号/省略号/破折号/间隔号） | **30,679** |
| 并集 | **30,814** |
| 含 LaTeX（`$` / `\` / `^` / `_`） | **307** |
| 可安全短式（本预注册规则，全包） | **20,441**（20,368 个不同 title） |

**范围（冻结）**：本批只处理**绑定到 39 个节点**（9 条 MISS + 20 薄料 top-20 + 10 仅误区 top-10，与 v26 §1 同集合）的 title；该范围内可安全短式候选 **141 条**（141 个不同 title）。全包其余 **20,441 − 141 = 20,300** 条登记为**后续批次 backlog**，v27 不处理。

**安全短式规则（冻结）**：
1. 跳过含 LaTeX（`$`/`\`/`^`/`_`）的 title（307 条中的命中者）——公式片段不硬截。
2. 若含 `：`/`:`/`，`/`,`/`；`/`;`/`。`/`！`/`？`：取**第一个分隔符前**的头部；`6 ≤ len(头部) ≤ 24` 且 `title_usable(头部) is None` → 采用。
3. 否则若 `len(title) > 24`：在 24 字内找最后一个边界（空格/`·`/`、`/`（`/`(`/`—`/`-`）截断；`6 ≤ len ≤ 24` 且 `title_usable(candidate) is None` → 采用；否则退回 24 字截断（同门前置）。
4. 短式必须是原 title 的**前缀**（语义保持；不做同义改写），且不得等于同学科另一节点主名/别名（全局互斥）。
5. **必须调用生产 `tools/kb_build/rebuild_aliases.title_usable()`**（`rebuild_aliases.py:57-99`），不重写门；短式必须返回 `None`。
6. 写判定表 `title` 列；重新 materialize；`rebuild_aliases --write` 使新 title 可被采纳为别名。
7. 逐条证据落 `build/agent-batch31b/v27-title-shortforms.csv`：`slug, node_slug, old_title, old_len, short_title, new_len, cut_reason, title_usable_reason_before, title_usable_reason_after, content_sha256_before/after`。

**不做**：不改 title 的语义（不把“多用电表的结构与原理”改成“多用电表”这种丢信息的短式）；不为凑 24 字截断括号/公式；不处理 39 节点之外的 20,300 条。

### 1.4 ④ T/C 别名批量补齐（仅 9 条 MISS + 弱召回节点）

**范围（冻结）**：39 个节点 = 9 条 MISS（`build/agent-batch31a/baseline/identity.md:143-162` 有数值 rank 的 9 条）+ 20 薄料 top-20 + 10 仅误区 top-10（与 v26 §1 同集合）。节点外一律不做。

**候选规则（沿用 `docs/research/2026-10-26-kb-content-derived-alias-rules.md`）**：
- **T（title 派生）**：该节点绑定材料的 title（经 §1.3 短式后的值）通过 `title_usable`；每节点 ≤6 条。
- **C（content 派生）**：`contentMarkdown` 中**逐字出现**的定义/步骤短语（span），通过 `title_usable`；每节点 ≤6 条；不得是题干/答案/课标要求。
- **全局互斥**：同一 `(subject, alias)` 被 ≥2 节点 claim → 全部丢弃；不得等于同学科另一节点主名；不得与既有 `alias_map.csv`/`alias_exceptions.csv` 冲突。
- **可追溯**：每条候选记录 `raw_span` 与来源材料 slug；写入 `alias_exceptions.csv` 的 `reason` 列。

**预期**：≤ **234** 条（39×6）；实际合格数由 `rebuild_aliases` dry-run 实测，**不是承诺**。写入链：`tools/kb_build/tables/alias_exceptions.csv` → `PYTHONPATH=tools python -m kb_build.rebuild_aliases --write` → `promote`。

**逐条证据**：`build/agent-batch31b/v27-alias-candidates.csv`：`subject, node_slug, kind(T/C), alias, raw_span, source_slug, title_usable_reason, claim_count, accepted/rejected_reason`。

---

## 2. 判读（非劣，写死）

### 2.1 主判读：v27 材料卫生 = 非劣（四条）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**（绝对下限）；若同代 v26 设备基线 ≠117/129，则同时要求 **≥ v26 同代基线**；低于任一即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与**同代 v26 设备基线**比较；任一章严格更低即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读** |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v27、B = 同代 v26**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

### 2.2 基线口径（关键）

- **当前 release 是 v26**（manifest `version=26` / `contentVersion=aea886e42b36dcb2` / 65,021 材料）。
- **v26 设备基线尚未产生**：v26 promote 链在 dense step1 被未跟踪的批次38视觉文件编译错误阻塞（`TutorVisualPreviewRenderer.kt:254`），dense/零漂移未跑；v25 设备基线也未产生。v24 设备基线 `117/129`、MRR `0.7239018087855298` 只作**绝对下限**，不是 v26 同代基线。
- 执行轮必须先在同一 emulator/判官/金标上取 **v26 同代设备四路账本**，再落 v27 并取 v27 四路；两臂同代（`samegen=MATCH`）才判读。v26 同代基线缺失时，判据 1/2/4 记 `NOT_RUN`，**不得**用 v24 或 stage3 代替。
- 零漂移 `NOT_COMPUTABLE` 按“不判读”；显著性产物缺失时判据 4 记 `NOT_RUN`，不得写“无显著差异”。
- JVM 只作零漂移/装置保真，不单独判分；设备读数为 emulator，不是真机。

### 2.3 为什么是“非劣”，不是“严格更高”

本批是**材料卫生**（去重、补摘要、短标题、补别名），不是新检索机制。严格更高会把裁定交给单题翻转，等价于面向结果调参；非劣四条覆盖卫生批真正要防的失败：主集回退、跨章回退、装置漂移、显著更差。**不构成先例**：若后续把本批当机制臂重测，须另立预注册并走“严格更高”三规则。

### 2.4 报告义务

§7 至少报告：v26/v27 两臂设备融合/词面主集与 MRR、24 章逐章 lower/equal/higher、零漂移两路 symdiff、bootstrap delta/CI/p、四组各自计数（① 87 组/93 退休、② 127/已修、③ 141/已短式、④ ≤234/已采纳）、材料/别名计数与 sha、双门 exit code。任何一项未跑写 `NOT_RUN` / `UNVERIFIED`。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 与 v24/v25/v26 预注册 §3 同口径；若另跑 `testDebugUnitTest`，结果另记，不替代本门。
- **红则不写 release、不跑设备、不判读**：任一门红时停在 promote 之前；不得先 promote、不得先跑 dense、不得用旧账本代替本批判读。
- **当前工作树阻塞（如实登记）**：未跟踪的批次38视觉文件 `TutorVisualPreviewRenderer.kt:254:34` 报 `String?` 不能赋给 `String`；`VisualChainTestSeams.kt` 的 KDoc `*/` 已在 v26 轮机械修复。双门在该阻塞清除前**不可跑**；执行轮必须先由该线修好，不得把本批写成“双门已过”。
- 两门日志落执行轮目录（建议 `build/agent-batch31b/`），§7 引用。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚

### 4.1 触碰清单 + pre-sha（写前实算，当前 v26 态）

**A. 包家族（25 sidecar + 主包 + manifest + index）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 1 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | 预期字节不变（无节点变更） | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 2 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 26→27 + 新 contentVersion | `370b0979ec011806258439877d837a33e6f0ea3dcd7db502b201de40d8853608` |
| 3 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 4 | `moe-2025-teaching-support-v2-01…25.json`（25 个） | ① 删 93 条；②/③ 改 summary/title；④ 不改 sidecar | 聚合 `9e7d3e13cba98da236001ea94e3f0f372e962e7661e06ac9f23f3e4b2eab5473`；v2-25 单件 `93269182…`/159 |

**B. 判定源（tracked）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 5 | `tools/kb_coverage/tables/material_judgments/MATH.csv` | ① 改 SKIP / ②③ 改 summary/title | `634b5e75ea3beed25cf3b4c9dce15d8a423e675a207d00414f39f1cb2929a688`（44,428） |
| 6 | `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` | 同上 | `41d4ca9cbcb4fcef9bdbed7d787f9dd0ac21637c390bf4427650c5af29f6fada`（38,087） |
| 7 | `tools/kb_coverage/tables/material_judgments/CHEMISTRY.csv` / `BIOLOGY.csv` / `UNASSIGNED.csv` | 同上（按命中的行） | `0c3660a0…` / `654a94d8…` / `28ed802b…` |
| 8 | `tools/kb_coverage/tables/extraction_state.csv` | materialize 重写 | `19008d07fa6f753b9ef240c4b7c7ce7899f9089448a0bc25d4717c3ff104693f` |

**C. 别名表（tracked）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 9 | `tools/kb_build/tables/alias_exceptions.csv` | ④ 追加 T/C 候选 | `7a8e13edbf5d1e83dddd5f563eede33b710d10c180ae749fcbe0e8dec1a2d01e`（36 行） |
| 10 | `tools/kb_build/tables/alias_map.csv` | `rebuild_aliases --write` 重写 | `5e8ed8baec36b30212fd81cbf4e7e9280d8d3346489f7035a0920c7317682451`（37,569 数据行） |
| 11 | `tools/kb_build/tables/alias_dropped.csv` | `rebuild_aliases --write` 重写 | `da4543b3c3774d42ac4a98e4ddeb2f246b4ae678ae3d52650ae935d8906df7da`（22,561 行） |

**D. 新增台账（本批新建；写前不存在）**

| # | 路径 | 用途 |
|---|---|---|
| 12 | `tools/kb_build/tables/material_retire.csv` | ① 93 条 RETIRED 可逆记录（见 §4.2） |
| 13 | `build/agent-batch31b/v27-dedupe-pairs.csv` | ① 87 组逐对证据 |
| 14 | `build/agent-batch31b/v27-summary-repairs.csv` | ② 127 条逐条证据 |
| 15 | `build/agent-batch31b/v27-title-shortforms.csv` | ③ 141 条逐条证据 |
| 16 | `build/agent-batch31b/v27-alias-candidates.csv` | ④ ≤234 条逐条证据 |

**E. 只读/零触碰**

| # | 路径 | 期望 | pre-sha256 |
|---|---|---|---|
| 17 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改（材料卫生不改 dense 语料） | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 18 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 零改 | `5cbbcb6f1b45e1638a4e6bb7f56f5a86a28d4e5a0a70b5efe83aead8f2d2f089` |
| 19 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

### 4.2 RETIRED 可逆记录（冻结）

`tools/kb_build/tables/material_retire.csv` 列：

```text
slug, kept_slug, subject, node_slug, title, summary,
source_file, source_index, material_json_sha256, pre_sidecar_sha256,
retired_at, reverted_at, reason
```

- `material_json_sha256` = 被退休材料完整 JSON（键排序、UTF-8、无空白差异）的 sha256；完整 JSON 另存 `build/agent-batch31b/v27-retired-json/<slug>.json`。
- `pre_sidecar_sha256` = 移除前该 sidecar 的 sha256；`source_index` = 移除前在 `materials[]` 中的下标。
- **可逆程序**：`python -I -X utf8 build/agent-batch31b/scripts/unretire_v27.py --slug <slug>`：
  1. 读台账与 JSON；校验 `material_json_sha256`；
  2. 校验当前 sidecar 的 `pre_sidecar_sha256`（若已被其他批次改动 → **拒绝**，要求手工 rebase，不得盲插）；
  3. 按 `source_index` 插回；恢复判定行 `action=MATERIAL`；把 `reverted_at` 写入台账（保留行，不删）。
- **回滚（任一判据失败或双门红）**：
  1. 先用 `unretire` 逐条恢复 93 条（或直接用 `backup-v27/` 覆盖 sidecar，效果等价、更快）。
  2. 用 `build/agent-batch31b/backup-v27/` 覆盖 25 sidecar、主包/manifest/index、5 张判定表、`alias_map.csv`/`alias_exceptions.csv`/`alias_dropped.csv`。
  3. 删除本批新增的 4 个证据 CSV 与 `material_retire.csv`（或保留为“已回滚”证据，二选一，执行轮登记）。
  4. 核验 v26 身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；v2-25 `93269182…`/159；25-sidecar 聚合 `9e7d3e13…`；材料 **65,021**；alias_map `5e8ed8ba…`；主包 `96246c78…`；`.vec` `70329115…`；金标 `eabef102…`。
  5. **不得** `git checkout` 打回 HEAD（HEAD 已含 v26；checkout 会把 v27 目标态打回旧态，回滚过头）。
  6. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。

**采纳**：保留 25 sidecar、5 张判定表、别名表与新台账；version=27、新 contentVersion 按实测登记；§7 登记 stateAfter。

---

## 5. 边界（材料卫生 ≠ 重开内容/检索层）

1. **①/② 全包，③/④ 只做 39 节点**：③ 的 20,300 条全包 backlog、④ 的 39 节点之外一律不做；不得把 v27 扩成“全包标题大清洗”或“全包别名重写”。
2. **不建点、不改主名、不改阈值/参数**：节点数 3,280；主包 `96246c78…`；dense `.vec` 不重打；判官/金标零改。④ 只往 `alias_exceptions.csv` 加 T/C 别名，不改节点 `name`。
3. **RETIRED 不是删除**：93 条必须进 `material_retire.csv` + 完整 JSON 备份；不得只从 sidecar 删掉而不留记录；不得改 manifest 的节点级 `retired` 账本（本批不退役节点）。
4. **摘要修复不改正文**：② 只改 `summaryMarkdown`；`contentMarkdown` 的 sha 前后必须相同；不得借机改 content/boundary。
5. **标题短式不丢语义**：③ 短式必须是原 title 前缀；不得为过门把“多用电表的结构与原理”截成“多用电表”这类丢信息的残片；`title_usable` 必须调用生产函数。
6. **别名不面向金标**：④ 的候选只从材料原文派生，不得用 129 条金标反推；同一 `(subject, alias)` 多节点 claim 全丢；不得改单点阈值。
7. **边界单点只报不判**：`等和线`、`活结与死结模型`、`力的分解` 三条 `absent` 不在本批 ④ 的“必救”承诺内；不得以单点翻转作为收益证据。

**一句话边界**：本批是“材料卫生 + 受控别名补齐”，不是“重开 65,021 条材料的内容判定”，也不是“用 129 条金标反推索引”。

---

## 6. 未做 / 未验证（写作时点）

- **未做任何卫生写盘**：87 组去重、127 条摘要、141 条短标题、≤234 条别名均未写；§1 的计数是只读实算，不是执行结果。
- **未跑 materialize/promote/双门/设备/JVM/零漂移/bootstrap**：§2/§3 是判据定义，不是通过声明。
- **v26 同代设备基线未产生**：v26 dense 链被未跟踪的批次38视觉文件编译错误阻塞；执行轮必须先取 v26 设备四路账本，否则判据 1/2/4 记 `NOT_RUN`。
- **双门当前不可跑**：`TutorVisualPreviewRenderer.kt:254:34` 的 `String?`→`String` 属他线未跟踪文件；本预注册不修其语义。
- **③ 的全包 20,300 条 backlog 未处理**：只登记，不承诺。
- **④ 的候选数 ≤234 是上界**：实际合格数由 `rebuild_aliases` dry-run 实测；不得为凑数放宽 `title_usable`/互斥门。

---

## 7. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1 逐文件写后 sha；① 87 组/93 退休逐对表与 `unretire` 往返；② 127 条 FIXED/NOT_FIXED 与 content sha 前后；③ 141 条短式与 `title_usable` 前后；④ ≤234 条 T/C 候选与 `rebuild_aliases` dry/write 输出；材料/别名计数（65,021→实测；alias_map 37,569→实测；节点 3,280；未绑/悬空 0）；双门 exit code；v26/v27 两臂设备四路读数、24 章 lower/equal/higher、零漂移 symdiff、bootstrap delta/CI/p。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
