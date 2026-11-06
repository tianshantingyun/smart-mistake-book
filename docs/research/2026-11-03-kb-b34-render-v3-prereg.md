# KB B34 渲染 v3：第二材料槽（异型）+ 2k 逐行预算 + 截断注：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-04T16:25:34+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`26f2327b0f88d3cad7e076b967b62874e36ca07d`（`git rev-parse HEAD` 实跑）。
> **上游**：`2026-11-02-kb-b33-render-v3-prereg.md`（下称 **R3-v1**：第二槽/2k 预算/截断注/广告同步的完整设计；本文件把它收窄为**不依赖 QR** 的异型第二槽）；`2026-10-31-kb-knowledge-read-render-v2-prereg.md`（现行单槽渲染）；`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md`（F1/F3/F5 口径）。
> **本步唯一新增写盘**：本文件。未改代码/内容包/向量；§0.3 的读数是本会话 `python -I` / `sha256sum` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T16:25:34+0800
git rev-parse HEAD                             # 26f2327b0f88d3cad7e076b967b62874e36ca07d
git status --porcelain | wc -l                 # 564
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   463 ??
#   101 M
ls docs/research/2026-11-03-kb-b34-render-v3-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 564 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。
- 本臂触碰的 9 个既有文件写前**干净**（§4.1 逐路径 `git status --porcelain --` 为空）；执行轮先重跑守卫。

### 0.2 现状（源码实读，当前 HEAD）

| 事实 | 出处 |
|---|---|
| 每节点**恰一条**材料：`nodeMaterialRecords` → `readBestKnowledgeTeachingMaterialPerNode`（`LIMIT 1`） | `RoomTutorToolRunner.kt:450-511`；`KnowledgeTeachingMaterialDao.kt:70-126` |
| 渲染：`"$prefix｜材料《${title}》" + excerpt`，节选 ≤**120** 字 | `RoomTutorToolRunner.kt:468-482` |
| 预算：`BudgetedLines(MAX_TOOL_RESULT_CHARS − TRUNCATION_NOTE_RESERVE_CHARS)` = **2,000 − 240 = 1,760** | `RoomTutorToolRunner.kt:456`、`:75` |
| 逐行裁剪：`addWithTrimmableExcerpt` 先缩节选、缩不下才整行丢；两种都计数 | `RoomTutorToolRunner.kt:933-949` |
| 截断注：`已截断：{dropped} 项未显示，{trimmed} 行节选缩短。` | `RoomTutorToolRunner.kt:488-491` |
| 无材料标记：`（该节点无材料）`；`$prefix｜$marker` | `RoomTutorToolRunner.kt:470-472`、`:525-526` |
| 展示节点数 5；节选上限 120 | `RoomTutorToolRunner.kt:520-523` |
| 工具广告（长/短）当前仍是「绑定材料《标题》与正文节选」「材料《标题》」（单数） | `TutorToolDescriptions.kt:21-24`、`:102-103` |
| 广告快照逐字钉住上述两段 | `TutorToolDescriptionsSnapshotTest.kt`（§4.1 #3） |
| 现有测试钉「单查询 5 个节点 → 材料段恰好 5 条」 | `UsageMaterialAllocationTest.kt`（§4.1 #4） |

### 0.3 静态可达性与预算（本会话实算，当前内容）

| 项 | 值 |
|---|---:|
| 节点 / 材料（2025 sidecar） | 3,280 / **64,853** |
| 节点材料数 = 1（只有首选槽） | **238** |
| 节点 ≥2 材料 | **3,042** |
| 节点 ≥2 类型（第二槽可达） | **2,231** |
| ≥2 材料但只有 1 类型（异型规则无第二槽） | **811** |
| 材料标题最大长度 | **183** 字 |
| 正文预算 | 2,000 − 240 = **1,760** |
| 5 节点首选槽地板 | 5 × **61** = **305** 字符 |

- 第二槽只在 **2,231/3,280** 节点上可达；238 个单材料节点与 811 个单类型节点只有首选槽，**不是缺陷**。
- 标题最长 183 字 ⇒ 预算必须允许标题在极端情况下让步（§1.2），否则“每节点节选 >60”在 5 节点最坏组合下不可满足。

### 0.4 与 R3-v1 的差异（本臂**不依赖 QR**）

- R3-v1 的首选槽按**查询类型**的秩表选（概念/方法/计算）；本文件收窄为：**首选 = 现行 identity 最优（`readBestKnowledgeTeachingMaterialPerNode` 原样）**；**第二槽 = 同节点上类型不同于首选的最优材料**。因此：
  - 不依赖 QR 的分类器/秩表/`studentQueryText`；当前树没有 QR 也能落地。
  - 不改首选材料、不改节点/top-5/512/召回/融合；只**增加**一条异型材料。
  - `UNCLASSIFIED` 与所有查询类型行为一致（无类型分流）。
- 这样做的代价：放弃“概念查询→概念材料”的 query-type 匹配；本臂只证明**材料类型多样性（异型第二槽）**的交付与预算安全。query-type 分流若要做，另立臂。

---

## 1. 渲染 v3（冻结）

### 1.1 第二材料槽（异型各一条）

1. **首选槽（primary）**：该节点按现行 identity 教学序（`role → material_type → title → material_id`）的最优材料；**选择语义零改**（仍走 `readBestKnowledgeTeachingMaterialPerNode`）。
2. **第二槽（secondary）**：同节点上，**类型不同于首选**的最优材料（同 identity 键序）；**同型不补**。
   - 若节点没有异型候选（238 单材料 + 811 单类型）→ 第二槽为空，只渲染首选。
   - 同一材料不得出现两次；两条都必须绑定在该节点（`bindings[].knowledgeNodeId` = 当前节点）。
3. **取数**：新增端口 `readTeachingMaterialCandidatesPerNode(subject, nodeIds)`，返回 `Map<nodeId, List<MaterialRecord>>`（按 identity 键序排序的**全部候选**）；Kotlin 侧选首选（仍用旧方法）与异型次选。
   - 不复用 `LIMIT 1`；也不把类型秩塞进 SQL。
4. **上限/数量**：每节点 ≤ **2** 条；节点仍 **5** 个；材料总数 **5–10**（每节点 0/1/2 条）。
5. **第二槽行形状（冻结）**：`"${marker}·补充《${title}》" + excerpt`——**不**重复节点名称与 boundary（它们已在首选行）；`marker` 与首选行同一代号。
6. **无材料节点**：只输出 `$prefix｜（该节点无材料）`；不渲染第二槽。
7. **有材料但无异型**：只渲染首选行；**不**输出无材料标记。

### 1.2 预算与逐行裁剪（冻结）

- 正文预算 = `MAX_TOOL_RESULT_CHARS(2_000) − TRUNCATION_NOTE_RESERVE_CHARS(240)` = **1,760**。
- **两遍渲染**：先 header + 5 条首选，再 5 条第二槽。首选先占预算，第二槽只用剩余。
- **首选槽地板**：`MIN_PRIMARY_EXCERPT_CHARS = 61`；首选槽节选**不得**被裁到 60 及以下。首选槽**不能**直接用 `addWithTrimmableExcerpt` 的「available ≥1 就缩」下限；必须用 floor-aware 变体：剩余 <61 时进入下面的让步顺序，而不是把首选缩到 1–60。为守住地板，让步顺序写死：
  1. 先**不渲染任何第二槽**（第二槽永远是第二遍）；
  2. 再把首选槽的 boundary 截到 0（boundary 本来已 ≤80）；
  3. 仍不够 → 截短 `《标题》`（保留至少 1 字前缀 + `…`，仍以 `《…》` 包裹，非空）；
  4. 仍不够 → 该首选行整行丢弃（记 `dropped`；§2.1 判据失败——这是臂的失败，不是可接受形态）。
- **第二槽**：可用预算 ≥ 其 skeleton + 1 时渲染，节选按 `safeTruncateMarkdown` 收到剩余长度（≤120）；否则整行丢弃（`dropped`）。第二槽标题**不**参与标题让步；放不下就丢第二槽。
- **逐行成本**：沿用 `line.length + 1`（换行 1 字符）；同一套 `BudgetedLines`，不新写第二套预算器。
- **计数**：`droppedCount` 计整行丢弃（首选或第二槽）；`trimmedCount` 计节选被缩短的行；标题被截短另记 `titleTrimmedCount`。

### 1.3 截断注（冻结）

- 现有模板不变：`已截断：{droppedCount} 项未显示，{trimmedCount} 行节选缩短。`
- **仅当** `titleTrimmedCount > 0` 时追加 `，{titleTrimmedCount} 行标题缩短。`（同一行，紧接原句）。
- 无任何裁剪（dropped/trimmed/titleTrimmed 全 0）→ **不输出**截断注（与现状一致）。

### 1.4 无材料标记（冻结，逐字不变）

- 无绑定材料的节点：`$prefix｜（该节点无材料）`，`prefix = "$marker. ${displayName}${boundary?.let { "：$it" } ?: ""}"`；不渲染第二槽。
- 标记串 `（该节点无材料）` 逐字不变；不得改成“暂无材料”“无内容”等。
- 有材料但第二槽为空 → 只渲染首选，**不**输出标记。

### 1.5 广告同步（冻结，逐字）

**长描述**（`purposeDescription`）：

```text
读取这道题相关知识点讲解材料（返回**代号**+名称+边界（≤80字）+**每节点最多两条**绑定材料（首选+补充；各含《标题》与正文节选（≤120字，公式/句界安全截断））；无材料节点显式标注「（该节点无材料）」；代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms）
```

**短描述**（`nativePurposeDescription`）：

```text
读取当前题相关知识点讲解材料（返回代号+名称+边界+每节点最多两条材料《标题》与正文节选≤120字；无材料显式标注）
```

- 两处都必须改；`TutorToolDescriptionsSnapshotTest` 的 `DEFAULT_LONG_DESCRIPTIONS["KNOWLEDGE_READ"]` / `DEFAULT_SHORT_DESCRIPTIONS["KNOWLEDGE_READ"]` 同步为上述逐字文本。
- `RoomTutorToolRunner.kt:383-386` 的 KDoc「绑定材料《标题》与正文节选（≤120 字）」同步为「每节点最多两条（首选+异型补充）」。
- **广告不得**继续出现「一条材料」「绑定材料《标题》」（单数）而代码给两条——这是本臂要消灭的契约漂移。

### 1.6 每项新增消灭的具体失败

| 新增 | 消灭的**具体**失败 |
|---|---|
| 异型第二槽（≤2） | 一个节点只有一条材料可读时，模型只能用同一视角讲；第二视角（概念 vs 方法）拿不到 |
| 异型才补第二槽 | 为凑满两条塞同型材料，制造“两条几乎一样”的噪声 |
| 首选 61 字地板 + 两遍渲染 | 第二槽把首选挤到 <60 字，节点材料退化成“标题+半句” |
| 标题让步（最后手段） | 183 字标题在 5 节点最坏组合下让“每节点 >60”不可满足 |
| 截断注 + 标题计数 | 标题被截短却无任何标注（静默变形） |
| 广告同步 | 工具描述说一条、实际两条；模型按旧契约理解结果 |
| 候选查询替代 `LIMIT 1` | 第二槽需要同节点第 2 条异型材料，而现查询结构上只返回一条 |

---

## 2. 判读（写死）

### 2.1 每节点必含 title，且首选节选 >60

- 对**每个有材料的渲染节点**（5 个中可能有 0 材料的）：至少一条材料行的 `《…》` 非空（标题或其前缀），且该节点**首选槽**节选长度 **≥61**（`MIN_PRIMARY_EXCERPT_CHARS`）。
- **无材料节点**不参与 title/excerpt 判据，只要求 §2.3 的逐字标记；不得为了凑 title/excerpt 给无材料节点编造材料。
- 第二槽不参与本判据（其节选可被裁到 1–120 或整行丢弃）。
- 解析口径：按渲染后的字符串，`《` 与 `》` 之间非空；`｜` 之后、行尾之前的节选按 UTF-16 长度计。
- **任何节点不满足 ⇒ 不采纳**；不得把“第二槽挤掉了首选”解释为可接受的降级。

### 2.2 总长 ≤ 2,000 字符

- `summaryMarkdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS`（2,000，UTF-16 码元）。
- 含 header、截断注、所有材料行；不含工具调用外层 JSON。
- 预算 `line.length + 1`；最终长度 ≤2,000（与 `BudgetedLines(1760)` + 240 reserve 一致）。

### 2.3 无材料标记不变

- 无材料节点逐字渲染 `（该节点无材料）`；有材料节点不得出现该标记。
- 标记串、`prefix` 形状、boundary 截断（≤80）与现状逐字相同。
- 测试：`UsageMaterialAllocationTest` 的标记用例保持绿；新增“有材料但无异型”用例（不得出现标记）。

### 2.4 契约广告同步

- `TutorToolDescriptionsSnapshotTest` 两段逐字等于 §1.5；测试绿。
- 全仓 grep：`KNOWLEDGE_READ` 相关文案不得再出现「一条材料」/「绑定材料《标题》与正文节选」（单数）作为**结果形态**的描述；命中即失败。
- `RoomTutorToolRunner.kt:383-386` KDoc 同步。

### 2.5 槽位合法性

- 每节点材料行数 ∈ {0,1,2}；**5 个节点行始终存在**（材料行或 §2.3 标记）。材料行总数 ∈ [0,10]；若 5 个节点都有材料，则为 [5,10]（F5 的“5–10”只在这一条件下成立）。
- 同一节点内无重复材料；两条都绑定该节点。
- 第二槽材料的 `type` ≠ 首选槽的 `type`；同型不得补位。
- 首选槽仍是 identity 最优（旧方法逐字不变）；第二槽是异型最优。

### 2.6 首查 p95 不劣

- **G-FIRST**：冷装（`uninstall → install → logcat -c → am start -W`）后，T0 = `am start -W` 调用前；探针发第一次 `KNOWLEDGE_READ` 知识查询，直到返回非空；记录 `T0 → 首次非空` 的毫秒数。
- N ≥ **20** 次冷装；p95 = nearest-rank；`p95(post) ≤ p95(baseline)`（同设备、同内容、同查询、pre-arm APK），且 `max ≤ 10,000 ms`；若 baseline p95 不可得，用 **p95 ≤5,000 ms** 作为兜底目标。
- **G-READ（诊断）**：首次 `KNOWLEDGE_READ` 工具调用 `invoke → result` 的 p95（N≥20）≤ baseline+10%，`max ≤1,000 ms`；只报告，不单独作采纳门。
- 另报检索 p95（AC B3，融合 ≤250ms / p50 ≤150ms）——本臂不改召回，预期不变；若检索 p95 红了，先查装置，不归因渲染。

### 2.7 口径变更登记（必须写进批报）

- AC v1.1 的 **F5** 冻结「材料条数/查询 = 5」；本臂改为「节点 5 个，材料 **5–10** 条（每节点 ≤2）」。
- 连带：F1/F3 的分母（645 个被选槽）不再是固定值——上限 1,290（129×5×2），实际取决于第二槽可达性。本臂**不承诺** F1/F2/F3 旧读数可比；批报必须写「F5 口径被 render v3 取代；F1/F3 分母待重定」。
- `UsageMaterialAllocationTest` 的「恰好 5 条」必须改为「5–10 条（每节点 ≤2）」，否则本臂自相矛盾。
- 更新 AC 文本是独立记录步，不在本臂写面。

### 2.8 装置门

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:model:testDebugUnitTest --tests "*TutorToolDescriptionsSnapshotTest*" --console=plain` | exit 0 |
| 2 | `./gradlew :core:data:testDebugUnitTest --tests "*UsageMaterialAllocationTest*" --tests "*KnowledgeReadRecallBudgetTest*" --tests "*KnowledgeReadSecondSlotTest*" --console=plain` | exit 0 |
| 3 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | exit 0（新 DAO/端口） |
| 4 | `./gradlew :app:assembleDebug --console=plain` | exit 0 |
| 5 | `tools/kb_perf/first_query_20x.sh <apk> <baseline-apk>`（新脚本，§2.6） | p95 不劣；max ≤10s |

- 门 1 钉广告；门 2 钉渲染/预算/第二槽；门 3 钉候选查询编译；门 4 钉装配；门 5 钉首查。
- 任一门红 ⇒ 不采纳、不跑设备。

### 2.9 报告义务（§6 至少包含）

- 5 节点 × 2 槽的实际渲染文本（逐节点：标题、节选长度、类型、是否第二槽）。
- 首选节选 min/p50/max；总长；`dropped/trimmed/titleTrimmed` 三个计数。
- 第二槽可达性（2,231 节点静态；本次 5 节点实际条数）。
- 广告两段逐字 + snapshot 结果；F5 口径变更登记。
- G-FIRST/G-READ 的 N、p50/p95/max、baseline 与 post；检索 p95。
- 门 1–5 的 exit code；§4 逐文件 post-sha。

---

## 3. 回滚（触碰清单 + pre/post sha）

### 3.1 A. 生产/文案/测试（写前干净）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/data/.../study/RoomTutorToolRunner.kt` | 两遍渲染、异型第二槽、61 地板、标题让步、计数、KDoc | `2de1e68f71bc0615ce073fa797086dc63178744cd9e7e153c275e26400b37fd4` | §6 |
| 2 | `core/model/.../model/TutorToolDescriptions.kt` | 长/短广告两段 | `0aa0b5dd4286de2380c3a378b0da2577a1a870e6b3b430d286533d59a83e9226` | §6 |
| 3 | `core/model/src/test/.../model/TutorToolDescriptionsSnapshotTest.kt` | 两段期望值 | `47c660cfcc291fd691c96ba5d6a09343d2f41e537dfc98ae2ee4a3aed081ccbe` | §6 |
| 4 | `core/data/src/test/.../study/UsageMaterialAllocationTest.kt` | 「恰好 5 条」→「5–10 条」；第二槽/标记用例 | `1d96c68304e665702d40bd064a4554eeca73b60f2ec725af4913066370234612` | §6 |
| 5 | `core/data/src/test/.../study/KnowledgeReadRecallBudgetTest.kt` | 预算/第一节点用例按两槽重算 | `2dddbf8899db631ab7baccb4f1f6e4c013b68b690f785e0b816c24ecd695783b` | §6 |
| 6 | `core/database/.../dao/KnowledgeTeachingMaterialDao.kt` | 新增候选查询（每节点全部候选，identity 排序） | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` | §6 |
| 7 | `core/database/.../RoomKnowledgeBaseStore.kt` | 候选读取；保留旧方法 | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` | §6 |
| 8 | `core/database/.../port/KnowledgePort.kt` | `readTeachingMaterialCandidatesPerNode` | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | §6 |
| 9 | `core/database/.../RoomStudyDatabase.kt` | 委托新方法 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | §6 |

### 3.2 B. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/test/.../study/KnowledgeReadSecondSlotTest.kt` | 两槽/异型/同型不补/单材料/截断注/61 地板 |
| 2 | `core/database/src/androidTest/.../TeachingMaterialCandidatesPerNodeInstrumentedTest.kt` | 真 Room 候选排序与每节点 ≥2 |
| 3 | `tools/kb_perf/first_query_20x.sh` | G-FIRST/G-READ 冷装 20 次探针（§2.6） |

### 3.3 C. 零触碰

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 2 | `core/data/.../knowledge/dense/DenseFusion.kt` | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 3 | `core/database/.../DenseRecallReranker.kt` | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 4 | `core/database/.../dao/ProblemOrganizationDao.kt` | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` |
| 5 | 内容包 / manifest / dense 资产 / 金标 | 当前 `28/ce8edb8f…/10ede1bf…/70329115…/f0c94795…/eabef102…`；本臂零改 |
| 6 | QR 的分类器/秩表（若已存在） | 本臂**不依赖**，零改；若缺也不阻塞本臂 |

### 3.4 写前备份 / 回滚 / 采纳

1. 备份 §3.1 的 9 件到 `build/agent-batch34/render-v3-backup/`；`git status --porcelain -- <§3.1>` 必须为空。
2. 回滚：按备份覆盖 + 删除 §3.2；**无需**清库/迁移（渲染层，无 schema、无内容改动）。
3. 核验：`UsageMaterialAllocationTest` 回到“恰好 5 条”口径、快照回到旧两段、`KnowledgeReadSecondSlotTest` 不存在；门 1–5 复跑；首查 p95 回到基线。
4. 采纳：保留写面；§6 登记实际渲染文本、计数、第二槽可达性、广告两段、F5 口径变更、G-FIRST/G-READ。

---

## 4. 未做 / 未验证（写作时点）

- **未写任何代码/测试**：§1 是冻结定义；2,231 可达性是静态计算，不是渲染读数。
- **未跑 Gradle/JVM/设备**：§2.8 门 1–5 均 `NOT_RUN`；G-FIRST/G-READ 未测。
- **预算最坏组合未实测**：183 字标题 × 5 节点的实际裁剪路径（标题让步触发次数）未测；执行轮按 §2.9 报。
- **首查基线未测**：`p95(baseline)` 需要在 pre-arm APK 上同设备测 N≥20；若不可得，用 p95≤5s 兜底。当前无本臂读数。
- **F5/F1/F3 口径变更未更新 AC**：本臂只登记；AC 文本更新另立记录步。
- **异型第二槽对生成质量的影响未测**：本臂只证明渲染形态/预算/交付安全，不证明“模型讲得更好”。

---

## 5. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；§3.1 逐文件 post-sha；5 节点 × 2 槽的实际渲染文本、首选节选 min/p50/max、总长、dropped/trimmed/titleTrimmed；第二槽可达性（静态 2,231；本次实际）；广告两段逐字 + snapshot；F5 口径变更；G-FIRST/G-READ 的 N/p50/p95/max 与 baseline/post；检索 p95；门 1–5 exit code；采纳/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
