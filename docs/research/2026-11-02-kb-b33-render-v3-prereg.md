# KB B33 渲染 v3：KNOWLEDGE_READ 第二材料槽 + 类型分流 + 2k 逐行裁剪（2026-11-02）

> **性质**：出数前冻结。§1–§3 写死；执行轮只允许在文末追加 §5，不得回改 §1–§3。
> **写作时点**：2026-11-03T09:51:52+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日。
> **HEAD**：`faab17f58b01eab78d5c08a9f843b3af3df2fecf`（`git rev-parse` 实跑）。**写前基线 = B3 去重 + B4 来源修复已落盘**：manifest **28** / `ce8edb8f89d91fe7` / 材料 **64,853** / 主包 `10ede1bf…`（顶层 `sourceUri=null`）。
> **上游**：`docs/research/2026-11-02-kb-b33-query-routing-prereg.md`（下称 **QR**：概念/方法/计算分类器与四张类型秩表——本臂**依赖**它，不重定义）；`core/data/.../RoomTutorToolRunner.kt`（渲染实现）；`core/model/.../TutorToolDescriptions.kt`（工具广告）；`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md`（F5 口径，见 §2.7）。
> **本步唯一新增写盘**：本文件。未改代码/内容包、未跑 Gradle/设备/JVM；§0.3 的读数是本会话用 `python -I` 与源码实读。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-03T09:51:52+0800
git rev-parse HEAD                             # faab17f58b01eab78d5c08a9f843b3af3df2fecf
git status --porcelain | wc -l                 # 611
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   448 ??
#    14  D      ← 他线 core/data/.../pdf/*
#   149  M     ← 含 B3（11 sidecar+manifest+judgments）与 B4（26 sidecar+主包+manifest+dense json）
git diff --cached --name-only | wc -l          # 0
ls docs/research/2026-11-02-kb-b33-render-v3-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 本臂触碰的渲染/文案/测试文件写前**干净**（`git status --porcelain -- <§3.1>` 为空）；工作树整体在他线推进中，执行轮须重跑本守卫。
- 611 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。

### 0.2 现状（源码实读）

| 事实 | 出处 |
|---|---|
| 每节点**恰一条**材料：`nodeMaterialRecords` → `readBestKnowledgeTeachingMaterialPerNode`（`LIMIT 1`） | `RoomTutorToolRunner.kt:497-511`；`KnowledgeTeachingMaterialDao.kt:81-126` |
| 渲染：`skeleton = "$marker. ${displayName}：$boundary｜材料《$title》" + excerpt`，节选 ≤**120** 字 | `RoomTutorToolRunner.kt:461-482` |
| 预算：`BudgetedLines(MAX_TOOL_RESULT_CHARS - TRUNCATION_NOTE_RESERVE_CHARS)` = **2000 − 240 = 1760** | `RoomTutorToolRunner.kt:456`、`:75`；`MAX_TOOL_RESULT_CHARS=2_000`（`UsageMaterialAllocationTest.kt:334` 断言） |
| 逐行裁剪：`addWithTrimmableExcerpt` 先缩节选、缩不下才整行丢弃；两种裁剪都计数 | `RoomTutorToolRunner.kt:933-949` |
| 截断注：`"已截断：${droppedCount} 项未显示，${trimmedCount} 行节选缩短。"` | `RoomTutorToolRunner.kt:483-487` |
| 无材料标记：`（该节点无材料）`，`$prefix｜$marker` | `RoomTutorToolRunner.kt:471-472`、`:527` |
| 展示节点数 = **5**；节选上限 = **120** | `RoomTutorToolRunner.kt:522-525` |
| 工具广告（长/短）写死「一条材料」「正文节选≤120字」 | `TutorToolDescriptions.kt:21-24`、`:102-103` |
| 广告快照测试逐字钉住上述两段 | `TutorToolDescriptionsSnapshotTest.kt:100-102,128-130` |
| 现有测试钉「单查询 5 个节点 → 材料段**恰好 5 条**」 | `UsageMaterialAllocationTest.kt:317` |

### 0.3 本会话实算（离线，非设备读数）

| 项 | 值 |
|---|---:|
| 节点 / 材料（B3 后） | 3,280 / **64,853** |
| 节点材料数 = 1 | **238**（只有首选槽，无第二槽） |
| 节点 ≥2 材料 | **3,042** |
| 节点 ≥2 类型 | **2,231** |
| **≥2 材料且 ≥2 类型**（第二槽可达） | **2,231** |
| ≥2 材料但只有 1 类型（按“异型”规则无第二槽） | 811 |
| 材料标题最大长度 | **183** 字 |
| 2k 预算 | 2,000 字符；正文 1,760（`TRUNCATION_NOTE_RESERVE_CHARS=240`） |
| 5 节点的首选槽保底 | 5 × **61** = **305** 字符（§1.2 的 `MIN_PRIMARY_EXCERPT`） |

**读法**：第二槽只在 2,231/3,280 节点上可达；238 个单材料节点与 811 个单类型节点**只有首选槽**，不是缺陷。标题最长 183 字 ⇒ 预算必须允许标题在极端情况下让步（§1.2），否则“每节点节选 >60”在 5 节点最坏组合下不可满足。

### 0.4 依赖（冻结）

- **类型分流依赖 QR**：本臂使用 QR §1.2 的三类线索表与 §2.2 的四张类型秩表（含 `UNCLASSIFIED` 的 identity 表），以及 QR §1.1 的 `studentQueryText` 输入。**不重新定义**分类器/秩表。
- 若 QR 未落地：本臂**停止**（没有查询类型就没有“类型分流”；不得临时自造一套分类）。第二槽可以在 `UNCLASSIFIED` 下用 identity 秩 + 异型规则工作，但那是 QR 已落地后的退化路径，不是替代方案。

---

## 1. 渲染 v3（冻结）

### 1.1 第二材料槽与类型分流

1. **首选槽（primary）**：该节点在**本次查询类型**的秩表下、按 `role → typeRank → title → material_id` 的最优材料。
   - `CONCEPT` → `CONCEPT_EXPLANATION` 先；`METHOD` → `METHOD_MODEL` 先；`COMPUTATION` → `WORKED_EXAMPLE` 先（现包无该型 → 落到 `METHOD_MODEL`）。
   - `UNCLASSIFIED` → QR 的 identity 表（即现行教学序）。
2. **第二槽（secondary）**：该节点上**类型与首选不同**的最优材料（同秩表、同键序）；**同型不补**。唯一例外：首选与次选同型时，第二槽为空——不得用同型材料凑满两条。
3. **上限**：每节点 ≤ **2** 条；同一材料不得出现两次；两条都必须绑定在该节点（`bindings[].knowledgeNodeId` = 当前节点）。
4. **数量**：节点仍为 **5** 个；材料总数 **5–10**（每节点 0/1/2 条）。
5. **取数**：新增端口 `readTeachingMaterialCandidatesPerNode(subject, nodeIds)`，返回 `Map<nodeId, List<MaterialRecord>>`（按 `role/type/title/material_id` 排序的**全部候选**）；Kotlin 侧按 §1.1-1/2 选两条。
   - 不复用 `readBestKnowledgeTeachingMaterialPerNode` 的 `LIMIT 1`；也不把类型秩塞进 SQL（`ORDER BY` 不能按查询类型变化）。
   - 与 QR 的 `readBestTeachingMaterialPerNodeRanked` 共享**同一候选查询**；QR 只取第 1 条，本臂取前 2 条（异型）。
6. **第二槽行形状（冻结）**：`"${marker}·补充《${title}》" + excerpt`——**不**重复节点名称与 boundary（它们已在首选行），使第二槽的行成本尽量小；`marker` 与首选行同一代号。

### 1.2 预算与逐行裁剪（冻结）

- 正文预算 = `MAX_TOOL_RESULT_CHARS(2_000) − TRUNCATION_NOTE_RESERVE_CHARS(240)` = **1,760**。
- **两遍渲染**：先 header + 5 条首选，再 5 条第二槽。首选槽先占预算，第二槽只用剩余。
- **首选槽地板**：`MIN_PRIMARY_EXCERPT_CHARS = 61`；首选槽的节选**不得**被裁到 60 及以下。为守住地板，让步顺序写死：
  1. 先**不渲染任何第二槽**（第二槽永远是第二遍）；
  2. 再把首选槽的 boundary 截到 0（boundary 本来已 ≤80）；
  3. 仍不够 → 截短 `《标题》`（保留标题前缀 + `…`，仍以 `《…》` 包裹，非空）；
  4. 仍不够 → 该首选行整行丢弃（记为 `dropped`，且 §2.1 判据失败——这是臂的失败，不是可接受形态）。
- **第二槽**：可用预算 ≥ 其 skeleton + 1 时渲染，节选按 `safeTruncateMarkdown` 收到剩余长度（≤120）；否则整行丢弃（`dropped`）。
- **逐行成本**：沿用 `line.length + 1`（换行 1 字符）；同一套 `BudgetedLines`，不新写第二套预算器。
- **计数**：`droppedCount` 计整行丢弃（首选或第二槽）；`trimmedCount` 计节选被缩短的行；标题被截短另记 `titleTrimmedCount`（§1.3）。

### 1.3 截断注（冻结）

- 现有模板不变：`已截断：{droppedCount} 项未显示，{trimmedCount} 行节选缩短。`
- **仅当** `titleTrimmedCount > 0` 时追加 `，{titleTrimmedCount} 行标题缩短。`（同一行，紧接原句）。
- 无任何裁剪（dropped/trimmed/titleTrimmed 全 0）→ **不输出**截断注（与现状一致）。

### 1.4 无材料标记（冻结，逐字不变）

- 无绑定材料的节点：`$prefix｜（该节点无材料）`，`prefix = "$marker. ${displayName}${boundary?.let { "：$it" } ?: ""}"`；**不**渲染第二槽。
- 标记串 `（该节点无材料）` 逐字不变；不得改成“暂无材料”“无内容”等。
- 有材料但第二槽为空（同型/无候选）→ 只渲染首选槽，**不**输出标记。

### 1.5 契约广告同步（冻结，逐字）

**长描述**（`TutorToolName.KNOWLEDGE_READ.purposeDescription`）：

```text
读取这道题相关知识点讲解材料（返回**代号**+名称+边界（≤80字）+**每节点最多两条**绑定材料（首选+补充；各含《标题》与正文节选（≤120字，公式/句界安全截断））；无材料节点显式标注「（该节点无材料）」；代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms）
```

**短描述**（`nativePurposeDescription`）：

```text
读取当前题相关知识点讲解材料（返回代号+名称+边界+每节点最多两条材料《标题》与正文节选≤120字；无材料显式标注）
```

- 两处都必须改；`TutorToolDescriptionsSnapshotTest` 的 `DEFAULT_LONG_DESCRIPTIONS["KNOWLEDGE_READ"]` / `DEFAULT_SHORT_DESCRIPTIONS["KNOWLEDGE_READ"]` 同步为上述逐字文本。
- KDoc `RoomTutorToolRunner.kt:385` 的「《标题》与正文节选（≤120 字）」同步为「每节点 ≤2 条」。
- **广告不得**继续出现「一条材料」「绑定材料《标题》」（单数）而代码给两条——这是本臂要消灭的契约漂移。

### 1.6 每项新增消灭的具体失败

| 新增 | 消灭的**具体**失败 |
|---|---|
| 第二材料槽（≤2） | 一个节点只有一条材料可读时，模型只能用同一视角讲；第二视角（概念 vs 方法）拿不到 |
| 类型分流 | 概念查询拿到方法材料、计算查询拿到概念材料——材料与问法错配 |
| 异型才补第二槽 | 为凑满两条塞同型材料，制造“两条几乎一样”的噪声（与去重要消灭的失败同源） |
| 首选 61 字地板 + 两遍渲染 | 第二槽把首选挤到 <60 字，节点材料退化成“标题+半句” |
| 标题让步（最后手段） | 183 字标题在 5 节点最坏组合下让“每节点 >60”不可满足 |
| 截断注 + 标题计数 | 标题被截短却无任何标注（静默变形） |
| 广告同步 | 工具描述说一条、实际两条；模型按旧契约理解结果 |
| 候选查询替代 `LIMIT 1` | 第二槽需要同节点第 2 条材料，而现查询结构上只返回一条 |

---

## 2. 判读（写死）

### 2.1 判据一：每节点必含 title 且节选 >60 字

- 对**每个渲染节点**（5 个）：至少一条材料行的 `《…》` 非空（标题或其前缀），且该行**首选槽节选长度 ≥ 61**（`MIN_PRIMARY_EXCERPT_CHARS`）。
- 第二槽不参与本判据（其节选可被裁到 1–120 或整行丢弃）。
- 判定口径：按渲染后的字符串解析（`《` 与 `》` 之间非空；`｜` 之后、行尾之前的节选按 UTF-16 长度计）。
- **任何节点不满足 ⇒ 不采纳**；不得把“第二槽挤掉了首选”解释为可接受的降级。

### 2.2 判据二：总长 ≤ 2,000 字符

- `summaryMarkdown.length <= TutorToolOutcome.MAX_TOOL_RESULT_CHARS`（2,000，UTF-16 码元）。
- 含 header、截断注、所有材料行；不含工具调用外层 JSON。
- 预算计算用 `line.length + 1`；`render()` 的最终长度必须 ≤2,000（与 `BudgetedLines(1760)` + 240 reserve 一致）。

### 2.3 判据三：无材料标记不变

- 无材料节点逐字渲染 `（该节点无材料）`；有材料节点不得出现该标记。
- 标记串、`prefix` 形状、boundary 截断（≤80）与现状逐字相同。
- 测试：`UsageMaterialAllocationTest` 的标记用例保持绿；新增用例覆盖“有材料但第二槽为空”（不得出现标记）。

### 2.4 判据四：契约广告同步

- `TutorToolDescriptionsSnapshotTest` 两段逐字等于 §1.5；测试绿。
- 全仓 grep：`KNOWLEDGE_READ` 相关文案不得再出现「一条材料」/「绑定材料《标题》与正文节选」（单数）作为**结果形态**的描述；命中即失败。
- `RoomTutorToolRunner.kt:385` KDoc 同步。

### 2.5 判据五：槽位合法性

- 每节点材料数 ∈ {0,1,2}；总数 ∈ [5,10]（5 节点）。
- 同一节点内无重复材料；两条都绑定该节点。
- 第二槽材料的 `type` ≠ 首选槽的 `type`；同型不得补位。
- 首选槽的 `type` 符合本次查询类型的秩表（QR）；`UNCLASSIFIED` 用 identity。

### 2.6 判据六：类型分流（QR 依赖）

- 对同一组 5 节点、同一候选集：`CONCEPT` / `METHOD` / `COMPUTATION` 三种查询类型下，首选槽的 `type` 分别服从对应秩表；第二槽为异型。
- 测试夹具必须覆盖：三种类型各一 + `UNCLASSIFIED` 一；至少一个节点只有同型材料（第二槽为空）+ 一个节点只有 1 材料（只有首选）。

### 2.7 口径变更登记（**必须写进批报**，不得静默）

- AC v1.1 的 **F5** 冻结「材料条数/查询 = 5」；本臂把它改为「节点 5 个，材料 **5–10** 条（每节点 ≤2）」。
- 连带：**F1/F3 的分母**（645 个被选槽）不再是固定值——上限变为 1,290（129×5×2），实际槽数取决于第二槽可达性。因此：
  - 本臂**不承诺** F1/F2/F3 的旧读数可比；
  - 批报必须写「F5 口径被 render v3 取代；F1/F3 分母待重定」；
  - 更新 AC 文本是**独立记录步**，不在本臂写面（本臂只登记）。
- `UsageMaterialAllocationTest.kt:317` 的「恰好 5 条」必须改为「5–10 条（每节点 ≤2）」，否则本臂自相矛盾。

### 2.8 装置门

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:model:testDebugUnitTest --tests "*TutorToolDescriptionsSnapshotTest*" --console=plain` | exit 0 |
| 2 | `./gradlew :core:data:testDebugUnitTest --tests "*UsageMaterialAllocationTest*" --tests "*KnowledgeReadRecallBudgetTest*" --tests "*KnowledgeReadSecondSlotTest*" --console=plain` | exit 0 |
| 3 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | exit 0（新 DAO/端口） |
| 4 | `./gradlew :app:assembleDebug --console=plain` | exit 0 |

- 门 1 钉广告；门 2 钉渲染/预算/第二槽；门 3 钉候选查询编译；门 4 钉装配。
- 任一门红 ⇒ 不采纳、不跑设备。

### 2.9 报告义务（§5 至少包含）

- 5 节点 × 2 槽的实际渲染文本（逐节点：标题、节选长度、类型、是否第二槽）。
- 首选节选长度的 min/p50/max；总长；`dropped/trimmed/titleTrimmed` 三个计数。
- 类型分流用例的四种查询类型 × 5 节点的首选/次选 `type` 表。
- 第二槽可达性（2,231 节点静态；本次 5 节点的实际条数）。
- 广告两段逐字 + snapshot 结果；F5 口径变更登记。
- 门 1–4 的 exit code；§3 逐文件 post-sha。

---

## 3. 回滚（触碰清单 + pre/post sha）

### 3.1 A. 生产/文案/测试（写前干净）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | 两遍渲染、第二槽、61 地板、标题让步、计数、KDoc | `2de1e68f71bc0615ce073fa797086dc63178744cd9e7e153c275e26400b37fd4` | §5 |
| 2 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptions.kt` | 长/短广告两段 | `0aa0b5dd4286de2380c3a378b0da2577a1a870e6b3b430d286533d59a83e9226` | §5 |
| 3 | `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptionsSnapshotTest.kt` | 两段期望值 | `47c660cfcc291fd691c96ba5d6a09343d2f41e537dfc98ae2ee4a3aed081ccbe` | §5 |
| 4 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/UsageMaterialAllocationTest.kt` | 「恰好 5 条」→「5–10 条」；第二槽/标记用例 | `1d96c68304e665702d40bd064a4554eeca73b60f2ec725af4913066370234612` | §5 |
| 5 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt` | 预算/第一节点用例按两槽重算 | `2dddbf8899db631ab7baccb4f1f6e4c013b68b690f785e0b816c24ecd695783b` | §5 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 新增候选查询（每节点全部候选，排序同现行） | `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047` | §5 |
| 7 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 候选读取；保留旧 `readBest...` | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` | §5 |
| 8 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | `readTeachingMaterialCandidatesPerNode` | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | §5 |
| 9 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托新方法 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | §5 |
| 10 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` | 仅当 QR 未接线时补 `studentQueryText`（QR 已落则零改） | `20bc8de8ce6e7f928c0ad9bc52efbb71f054822524f92198208aba5e67efc219` | §5 |

### 3.2 B. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/test/kotlin/.../study/KnowledgeReadSecondSlotTest.kt` | 两槽/异型/同型不补/单材料/截断注/61 地板 |
| 2 | `core/database/src/androidTest/kotlin/.../TeachingMaterialCandidatesPerNodeInstrumentedTest.kt` | 真 Room 候选排序与每节点 ≥2 |

### 3.3 C. 零触碰

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 2 | `core/data/.../knowledge/dense/DenseFusion.kt` | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 3 | 内容包 / manifest / dense 资产 / 金标 | 当前 `28/ce8edb8f…/10ede1bf…/70329115…`；本臂零改 |
| 4 | QR 的分类器/秩表文件 | 依赖，不改；若缺 ⇒ 停 |

### 3.4 写前备份 / 回滚 / 采纳

1. 备份 §3.1 的 10 件到 `build/agent-batch33/render-v3-backup/`；`git status --porcelain -- <§3.1>` 必须为空。
2. 回滚：按备份覆盖 + 删除 §3.2；**无需**清库/迁移（渲染层，无 schema、无内容改动）。
3. 核验：`UsageMaterialAllocationTest` 回到“恰好 5 条”口径、快照回到旧两段、`KnowledgeReadSecondSlotTest` 不存在；门 1–4 复跑。
4. 采纳：保留写面；§5 登记实际渲染文本、计数、类型表、F5 口径变更。

---

## 4. 未做 / 未验证（写作时点）

- **未写任何代码/测试**：§1 是冻结定义；`2,231` 节点可达性是静态计算，不是渲染读数。
- **未跑 Gradle/JVM/设备**：§2.8 四门均 `NOT_RUN`。
- **预算最坏组合未实测**：183 字标题 × 5 节点的实际裁剪路径（标题让步触发次数）未测；执行轮按 §2.9 报。
- **QR 未落地**：分类器/秩表是依赖；若 QR 的 `studentQueryText` 未接线，本臂停（§0.4）。
- **第二槽的“异型”在现包的实际覆盖**：2,231 节点静态可达；本次 5 个召回节点里实际几条可补，未测。
- **F1/F2/F3 未重算**：F5 口径变更后分母变化（§2.7），本臂不承诺旧读数可比。
- **`MAX_TOOL_RESULT_CHARS_EXTENDED` 路径**：本臂只按默认 2k 渲染；extended 分支若存在，未纳入本判据。

---

## 5. 执行轮追加位（只追加，不改 §1–§3）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；§3.1/§3.2 逐文件 post-sha；5 节点渲染文本（标题/节选长度/类型/第二槽）；首选节选 min/p50/max 与总长；`dropped/trimmed/titleTrimmed`；四类查询类型 × 5 节点的类型表；第二槽实际条数；广告两段逐字与 snapshot 结果；F5 口径变更登记；门 1–4 exit code。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
