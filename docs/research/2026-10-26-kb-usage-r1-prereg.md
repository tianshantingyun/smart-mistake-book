# KB 使用面 R1 修复（KNOWLEDGE_READ 每节点一条教学材料）：预注册（2026-10-26）

> 出数前冻结；§1–§6 事后一字不动，执行轮只在文末追加 §7。依据：`docs/research/2026-10-26-kb-usage-surface-audit.md`（R1：`:27-28`、`:33`、`:50-79`；R2–R5 边界：`:34-37`）。本预注册只覆盖 `KNOWLEDGE_READ` 的材料取数、选择与渲染；不改召回、不改旧 SQL、不改契约、不改材料数据。

---

## 1. 修复面与边界

### 1.1 修复面（只此一处）

| # | 层 | 路径 | 动作 |
|---|---|---|---|
| 1 | DAO | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | **新增** `readBestTeachingMaterialPerNode`（新 `@Query` + 行 POJO）；旧 `readForKnowledgeNodes` 与其 SQL 一字不改 |
| 2 | port | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | **新增** `readBestKnowledgeTeachingMaterialPerNode(subject, knowledgeNodeIds): Map<String, KnowledgeTeachingMaterialRecord>`；旧方法不动 |
| 3 | store | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | **新增**同名实现：调新 DAO，按 `knowledgeNodeId` 映射为 `Map`；旧方法不动 |
| 4 | 数据库委托 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | **新增** override，委托给 `knowledgeBase` |
| 5 | runner | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | `nodeMaterialSummaries` **切换**到新 port；无材料节点渲染显式标记 |
| 6 | 测试 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt` | 扩展：新 port 调用点、每节点一条、无材料标记、2k 字符门 |
| 7 | 测试替身 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepositoryTest.kt` | `FakeStudyDatabasePort` **新增**新 port 的内存实现（§2b 同序） |
| 8 | 测试 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest.kt` | **新增**真 Room 查询测试：§2b 四级排序、每节点恰 1 条、无材料节点不出现、RETIRED/科目过滤 |

**新增方法签名（冻结）**

```kotlin
// DAO（core:database，internal）
internal data class KnowledgeTeachingMaterialForNodeRow(
    @ColumnInfo(name = "knowledge_node_id") val knowledgeNodeId: String,
    @Embedded val material: KnowledgeTeachingMaterialEntity,
)

@Query(/* §1.3 SQL */)
suspend fun readBestTeachingMaterialPerNode(
    subject: String,
    knowledgeNodeIds: Set<String>,
): List<KnowledgeTeachingMaterialForNodeRow>

// port（core:database）
suspend fun readBestKnowledgeTeachingMaterialPerNode(
    subject: String,
    knowledgeNodeIds: Set<String>,
): Map<String, KnowledgeTeachingMaterialRecord>
```

### 1.2 不动面（写死）

- **旧 DAO SQL 一字不改**：`KnowledgeTeachingMaterialDao.kt:24-61` 的 `readForKnowledgeNodes` 注解体（`best_role_rank → material_type CASE → title → material_id`，`LIMIT :limit` 在 `:59`）逐字保留；`:62-66` 方法签名不变。执行轮必须用 `git diff` 证明旧注解体零字节变化；只允许出现新增方法/新增行。
- **旧 port 方法一字不改**：`KnowledgePort.kt:133-137` 的 `readKnowledgeTeachingMaterialsForNodes` 签名与语义不变；`MAX_TEACHING_MATERIAL_CANDIDATES = 1_024`（`:178`）不变。
- **旧 store 方法一字不改**：`RoomKnowledgeBaseStore.kt:365-381` 的 `readKnowledgeTeachingMaterialsForNodes` 保留；新方法另起。
- **旧读口的生产调用点（3 处；本批只切换第 3 处）**：
  1. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:26-30`（讲题参考 / leech 重教，`limit = KnowledgeReadPort.MAX_TEACHING_MATERIAL_CANDIDATES = 1024`）——**不动**。它必须继续走旧 DAO 的 `best_role_rank → material_type CASE → title → material_id`；DAO `:18-22` 明确要求类型 CASE 与 `TutorTeachingReferenceSelector.reTeachPriority`（`:73-81`）逐值一致，本批不碰这两处。
  2. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlannerService.kt:661-664`（复习出题范围，`MAX_KNOWLEDGE_QUIZ_SCOPE_MATERIALS = 64`，`:866`）——**不动**。
  3. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt:388-392`（`KNOWLEDGE_READ` 的旧取数）——这是本批**唯一**被切换的生产旧调用点：旧调用被移除，改为新 port；不是“保留旧调用再加新调用”。
  - **口径澄清**：ask 的“不动补救消费方”= 第 1、2 处；第 3 处是本次要修的使用面，不是补救消费方。三处都走同一个旧 DAO 方法，但本批只把第 3 处切到新方法，旧方法与旧 SQL 对所有未切换调用方保持原行为。
- **`TutorTeachingReferenceSelector` / `reTeachPriority`**：零改。新顺序只用于 `KNOWLEDGE_READ`，不得渗入 `referencesFor` / 复习出题范围。
- **召回路径**：`readSubjectKnowledgeRecallCandidates`、稠密腿、`DenseFusion`、`take(5)`、金标 `golden_queries_v2.json`：零改。
- **材料数据**：25 个 sidecar、材料正文、`alias_*`、`.vec`：零写。
- **契约**：R2/R3/R4/R5 的披露/截断/预算语义：零改（见 §6）。

### 1.3 新取数形状（冻结）

**约束**：`minSdk = 23`（`core/database/build.gradle.kts:14`），SQLite 窗口函数不可用；core 内无 `ROW_NUMBER` / `OVER (` 既有用法（本代理 grep 零命中）。因此新查询用**相关子查询 + `LIMIT 1`**，不用窗口函数。绑定表有 `(knowledge_node_id, role)` 索引（`KnowledgeTeachingMaterialEntities.kt:80-83`），按节点取最佳一条不需要全表排序。

**SQL（冻结；只用 `:subject` / `:knowledgeNodeIds` 参数绑定，不拼接、不 format）**

```sql
SELECT
  binding.knowledge_node_id AS knowledge_node_id,
  material.*
FROM knowledge_teaching_material AS material
INNER JOIN knowledge_teaching_material_node_binding AS binding
  ON binding.material_id = material.material_id
WHERE material.subject = :subject
  AND material.status != 'RETIRED'
  AND binding.knowledge_node_id IN (:knowledgeNodeIds)
  AND material.material_id = (
    SELECT m2.material_id
    FROM knowledge_teaching_material AS m2
    INNER JOIN knowledge_teaching_material_node_binding AS b2
      ON b2.material_id = m2.material_id
    WHERE b2.knowledge_node_id = binding.knowledge_node_id
      AND m2.subject = :subject
      AND m2.status != 'RETIRED'
    ORDER BY
      CASE b2.role
        WHEN 'PRIMARY' THEN 0
        WHEN 'SUPPORTING' THEN 1
        ELSE 2
      END ASC,
      CASE m2.material_type
        WHEN 'CONCEPT_EXPLANATION' THEN 0
        WHEN 'METHOD_MODEL' THEN 1
        WHEN 'DERIVATION' THEN 2
        WHEN 'WORKED_EXAMPLE' THEN 3
        WHEN 'REPRESENTATION_GUIDE' THEN 4
        WHEN 'MISCONCEPTION_GUIDE' THEN 5
        WHEN 'COMPLETE_SOLUTION' THEN 6
        ELSE 7
      END ASC,
      m2.title ASC,
      m2.material_id ASC
    LIMIT 1
  )
ORDER BY binding.knowledge_node_id ASC
```

**形状语义（冻结）**

- 每个有材料的节点**恰好 1 行**；无材料节点**不出现**在结果里（由调用方渲染显式标记）。
- `role` 用**该节点自己的绑定角色**，不是跨节点 `MIN(role)`；`PRIMARY` 优先于 `SUPPORTING`，其余最后。
- 类型顺序是**教学优先**，不是补救顺序：`CONCEPT_EXPLANATION → METHOD_MODEL → DERIVATION → WORKED_EXAMPLE → REPRESENTATION_GUIDE → MISCONCEPTION_GUIDE → COMPLETE_SOLUTION`。
- 并列时 `title ASC` → `material_id ASC`；SQLite 默认 BINARY 排序。当前 64,973 条材料的 `title`/`contentId` 无非 BMP 字符（本代理实跑），故与 Python/JVM 镜像的码点序一致；非 BMP 场景不在本批判据。
- 无 `limit` 参数：结果行数上界 = 节点数（`KNOWLEDGE_READ` 为 5），不是全局 `LIMIT`。
- `status = 'RETIRED'` 的材料不进结果；科目不符的不进结果。

---

## 2. 冻结口径

### 2.1 分配（a）

- **每节点恰好取 1 条**：有材料的节点必得其最佳一条；5 个节点 → 总材料预算 5，与现状同量（旧 `limit = nodes.size` 也是 5，但旧 `LIMIT` 是跨节点合计，见审计 R1 `:50`）。
- **无材料节点显式渲染**：`｜（该节点无材料）`（标记文本 `（该节点无材料）`，`｜` 是既有材料段分隔符）；不得静默省略材料段。
- **有材料节点不得出现“未选中”**：旧实现中 344/645 个节点行（53.3%）有材料但没有摘要段（审计 `:27`、`:56-57`）；新实现必须让这 344 行全部得到材料。
- **“无材料”与“未选中”分开**：本预注册的“无材料”= 该节点绑定材料数为 0；“未选中”= 有材料但旧全局 `LIMIT` 没选到。金标 top-5 的 645 行**全部有材料**（本代理实算），所以金标上“无材料”为 0；“未选中”的 344 行才是本批要消灭的主类。
- **单查询材料总条数**：金标 129 条查询均为 5 个有材料节点 → 5 条/查询；若未来出现无材料节点，材料条数 = 有材料节点数，节点行数仍为 5。

### 2.2 节点内排序（b，教学优先）

| 级 | 键 | 顺序 |
|---:|---|---|
| 1 | `role` | `PRIMARY` 0 / `SUPPORTING` 1 / 其他 2 |
| 2 | `material_type` | `CONCEPT_EXPLANATION` 0 / `METHOD_MODEL` 1 / `DERIVATION` 2 / `WORKED_EXAMPLE` 3 / `REPRESENTATION_GUIDE` 4 / `MISCONCEPTION_GUIDE` 5 / `COMPLETE_SOLUTION` 6 / 其他 7 |
| 3 | `title` | `ASC` |
| 4 | `material_id` | `ASC` |

**依据**：审计 R1 实测槽位偏置——6.1% 的误区材料占了 76.1% 的槽位（`491/645`），54.7% 的概念讲解只占 0.6%（`:60`）。教学场景下先给概念讲解、方法模型、推导，再给例题/表征，最后才是误区与完整解答；这是**使用面**的顺序，不是补救顺序。

**补救旧序不动**：`MISCONCEPTION_GUIDE` 0 / `WORKED_EXAMPLE` 1 / `METHOD_MODEL` 2 / `DERIVATION` 3 / `CONCEPT_EXPLANATION` 4 / `REPRESENTATION_GUIDE` 5 / `COMPLETE_SOLUTION` 6（`RoomTutorTeachingReferenceRepository.kt:73-81`；DAO `:45-58`）。本批不得把 §2b 的顺序写回 `reTeachPriority` 或旧 DAO SQL。

### 2.3 渲染（c）

- `summaryMarkdown.take(60)` 不变（`RoomTutorToolRunner.kt:359-361`、`:418` 的 `KNOWLEDGE_READ_MATERIAL_DIGEST_CHARS = 60`）。
- 节点 boundary `take(80)` 不变（`:358`）。
- header、代号、行结构不变；唯一变化是材料段从“可选”变成“材料或显式标记”。
- 2k **字符**预算不变：`TutorToolOutcome.MAX_TOOL_RESULT_CHARS = 2_000`；现有单测断言 `summaryMarkdown.length <= 2_000`（`KnowledgeReadRecallBudgetTest.kt:142-145`）保留。注意：这是测试侧断言，不是 `KNOWLEDGE_READ` 的运行时 2k 检查点（R5，见 §6）。
- R3/R5 不碰：不加截断标记、不改字符/字节单位、不改运行时硬校验、不改轮级预算。
- 当前包 64,973 条材料 `summaryMarkdown` 无空白（本代理实跑），故不新增“空摘要”特判。

---

## 3. 判读（使用面，非检索）

### 3.1 判读对象与基线

- **对象**：审计镜像的 129 条金标 top-5（不是重新检索、不是重新排序）。镜像的融合 Recall@5 = **117/129**，与仓库产物一致（`build/agent-batch14/d3/jvm-fused-metrics.txt:7`；审计 `:55`）。
- **基线**（审计 R1 实读，冻结）：

| 指标 | 基线 | 证据 |
|---|---:|---|
| F1 每节点材料覆盖（有料节点中获摘要比例） | **46.7%**（301/645 = 46.67%；645 行全部有材料） | 审计 `:27`、`:56-57`；本代理复算 |
| F2 期望节点有材料 | **50.4%**（65/129 = 50.39%） | 审计 `:28`；本代理复算 |
| F3 误区槽位占比 | **76.1%**（491/645 = 76.12%；`MISCONCEPTION_GUIDE 491 / METHOD_MODEL 150 / CONCEPT_EXPLANATION 4`） | 审计 `:60`；本代理复算 |
| F4 无材料节点显式标注 | **无标注**（旧实现静默省略） | 审计 `:27`、`:78` |
| F5 单查询材料总条数 / 2k | **5 / 名义 2k** | 审计 `:29`、`:37`、`:184-191` |

- **复算命令（本代理实跑，只读，复用审计镜像；不在仓库内）**：

```text
python C:/Users/听云/AppData/Local/Temp/prereg_usage_r1_calc.py
```

关键输出：

```text
BASE rows 645 with_material 645 summary 301 coverage_all 46.67 coverage_with_material 46.67
BASE expected_summary 65 / 129 50.39
BASE no-material rows (need explicit mark) 0
NEW rows 645 with_material 645 summary 645 coverage_all 100.0 coverage_with_material 100.0
NEW expected_summary 117 / 129 90.7
NEW no-material rows marked 0
NEW selected_total 645 types {"CONCEPT_EXPLANATION": 563, "METHOD_MODEL": 74, "MISCONCEPTION_GUIDE": 8} misconception_share 1.24
NEW render chars min/median/max 660 743 838
NEW render utf8 min/median/max 1707 1985 2267
NEW queries 129
```

> 上表的 `NEW` 是**镜像算术预期**，不是执行轮读数；执行轮必须用同一镜像重跑并落盘，§7 以实读覆盖。

### 3.2 五条判据（冻结）

| # | 判据 | 求值口径 | 通过条件 |
|---|---|---|---|
| F1 | 每节点材料覆盖 | 分母 = 129×5 中**有绑定材料**的节点行；分子 = 其中获得材料摘要的节点行 | **≥90%** |
| F2 | 期望节点有材料 | 分母 = 129；分子 = `expectedSlug` 节点在 top-5 内**且**获得材料摘要的查询数 | **≥90%** |
| F3 | 误区槽位占比 | 分母 = 129×5 中被选中的材料槽（金标 = 645）；分子 = 类型为 `MISCONCEPTION_GUIDE` 的槽 | **≤20%** |
| F4 | 无材料节点显式标注 | 金标 top-5 中绑定材料数为 0 的节点行；每行必须出现标记 `（该节点无材料）`（渲染为 `｜（该节点无材料）`），且不得出现 `｜材料：` | **全部标注**；金标 0 行时为空真，**必须**由单测夹具证明标记存在 |
| F5 | 单查询材料总条数 / 2k | 每查询材料行数（金标应 = 5）；渲染文本 `length`（UTF-16 码元）≤ 2,000 | **5 / ≤2,000 chars** |

### 3.3 判读规则（写死）

- **只报五条，不挑**：F1–F5 全部报告；不得只报上升项。F1/F2/F3 用同一批 129 条 top-5；F4 用单测夹具；F5 用 129 条渲染实测。
- **不重新检索**：top-5 必须与审计/设备融合臂同源（117/129）。若执行轮 top-5 集合或名次变化，先按 §4 判装置缺陷，不进入使用面判读。
- **F2 的算术上限**：金标 129 条中 12 条检索 MISS（117/129），这 12 条的期望节点不在 top-5，结构上不可能有材料；因此 F2 的上限 = **117/129 = 90.70%**。F2 ≥90% 只有 **0.70 个百分点**余量；任何一条命中查询的期望节点丢材料都会跌破 90%。这不是调阈值的理由，而是必须逐条核对的选择正确性门。
- **F4 的空真**：金标 645 行全部有材料（`BASE no-material rows = 0`），所以“金标 F4 = 0/0”**不算通过**；必须由 `KnowledgeReadRecallBudgetTest` 的无材料节点夹具证明 `｜（该节点无材料）` 真的渲染出来。
- **F5 的单位**：2k 是**字符**（UTF-16 码元），不是 UTF-8 字节。镜像预期 max 838 chars（41.9% of 2,000）；UTF-8 max 2,267 B（113.35% of 2,000 B）已越 2,000 B——这是 R5 的契约侧问题（§6），不得写成“2k 字节通过”。
- **阈值不事后改**：F1 90% / F2 90% / F3 20% 冻结；执行轮不得因实测接近阈值而放宽、改分母或换样本。

---

## 4. 无回归

### 4.1 金标检索读数不变

本修复只改 `KNOWLEDGE_READ` 的材料选择/渲染，不动召回路径；因此：

- **设备融合臂 Recall@5 必须仍为 117/129 = 0.9069767441860465**，MRR 0.7322997416020672（`build/agent-batch22/v21/device-metrics.txt:3-5`）。
- **JVM 融合镜像必须仍为 117/129**（`build/agent-batch14/d3/jvm-fused-metrics.txt:7`）。
- **零漂移同路对称差必须为 0**：词面 27/27/common 27、融合 12/12/common 12，`zeroDriftBothRoutes=true`（`build/agent-batch22/v21/zero-drift.json` 的 `same_route_lexical_device_vs_jvm.symmetric_difference_count = 0`、`same_route_fused_device_vs_jvm.symmetric_difference_count = 0`）。

**判据**：上述三项任一变化 = **装置/召回缺陷**，不是本修复的“收益”或“代价”；停止使用面判读，先查原因。不得用新读数替换 §3 的 117/129 基线。

### 4.2 旧消费方无回归

- 旧 DAO SQL 字节不变；旧 port/store 方法不变；`TutorTeachingReferenceSelector` 与 `reTeachPriority` 不变。
- 执行轮必须跑：
  - `./gradlew :core:data:testDebugUnitTest --tests '*RoomTutorTeachingReferenceRepositoryTest' --tests '*TutorTeachingReferenceBudgetTest' --tests '*KnowledgeReadRecallBudgetTest' --console=plain`
  - `./gradlew :core:database:compileDebugKotlin :core:data:compileDebugUnitTestKotlin --console=plain`
  - 新 DAO 真库测试：`KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest`（设备/模拟器）。
- 讲题参考/重教路径的**顺序**必须与改前一致：`PRIMARY → SUPPORTING → 其他`，类型 `MISCONCEPTION_GUIDE → WORKED_EXAMPLE → METHOD_MODEL → DERIVATION → CONCEPT_EXPLANATION → REPRESENTATION_GUIDE → COMPLETE_SOLUTION`，再 `title → material_id`。任何顺序变化 = 回归。

### 4.3 未跑即未通过

本预注册轮**未跑** Gradle、设备 `am instrument`、JVM 判官或零漂移；§4 的三项读数来自已落盘产物（v21 收束），不是本批实测。执行轮若设备不可用，设备读数记 `NOT_RUN`，不得写成“未变”。

---

## 5. 回滚

### 5.1 触碰清单 + pre-sha（写前实测）

**写前 HEAD = `5bc8bdf868e1296049ab901cf1da69e136062006`**（2026-10-26 写前守卫时）。工作副本 sha256 是回滚的权威目标；HEAD blob 仅作审计对照。

| # | 路径 | 动作 | porcelain | 工作副本 sha256（pre） | HEAD blob（SHA-1） |
|---:|---|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/KnowledgeTeachingMaterialDao.kt` | 追加新方法 + 行 POJO | CLEAN | `7bcd697d8ee1b3a95c52c1e8ad99526f832fca7b83459c6111c741c5f3712616` | `c20eb5aef4503063adf094cf58c70bf6e4077e48` |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 新增 port 方法 | CLEAN | `f813e8d9fea28c59cac2a745fb8a357a4c7e71e05074e40ff31563837153bceb` | `5e635b5400de4f37dca01481a023cbc43f2bfa4c` |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 新增 store 实现 | CLEAN | `0977a6767a07d1efaede490556c1e2acfda9693c438ec29d4b80d2f487cf620e` | `986a5523823e189380bf7ff94e3d223501127324` |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 新增 override | CLEAN | `b0fa9e301f05c61ebf5274539611525469c7cea2d1e4ffcebbf55128a74d0138` | `9fde9101a659ab15ed392732df48a2f7b74b8721` |
| 5 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | 切换 `nodeMaterialSummaries` + 渲染标记 | **M（18/6）** | `1af63a6be70b9ff75dff26fbdd2a071f080632bae5f46a66cb7d5ed0986a358c` | `5b85b7c5cae245cf4319be13c2cb268e6f5a059d` |
| 6 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt` | 扩展断言 | CLEAN | `c227217e34832654d8a6928dfe66953045a27909f5a32b4b7f6721397ff80910` | `941bff301cf066b493443b9e8a72758b95aaf37b` |
| 7 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomBackedStudyExperienceRepositoryTest.kt` | fake 新增 port 实现 | CLEAN | `94516cf1c702141a501ef34cbdfbbe9a8f317d190dc115d762fadc9851c10b78` | `6d9d648f5de169aa462a42a8e473728ef81fca64` |
| 8 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest.kt` | 新增真库测试 | **新增** | N/A（写前不存在） | N/A |
| 9 | `docs/research/2026-10-26-kb-usage-r1-prereg.md` | 本预注册 | **新增** | N/A（写前不存在） | N/A |

**注**：写前守卫在 HEAD `f0a145f8` 时曾见 `RoomStudyDatabase.kt`（+25）与 `RoomBackedStudyExperienceRepositoryTest.kt`（+28）为脏；随后并行线提交 `5bc8bdf8` 将这两处收走。**最终 pre-sha 以本表为准**（即提交后的工作副本字节）；执行轮不得用旧守卫快照覆盖。

### 5.2 写前 porcelain 守卫（实测）

命令：

```text
git status --porcelain -- <§5.1 的 7 个 tracked 文件>
```

实测（HEAD `5bc8bdf8`）：

```text
 M core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
```

- 7 个 tracked 文件中 **1 个脏（runner，`18 6`）**，6 个 CLEAN。
- runner 的脏 diff 是**他线 figure-role 改动**（审计 `:241-248`：`sha256=1af63a6…`、`git diff --numstat = 18 6`、diff 仅 `figure-role` 段，`KNOWLEDGE_READ` 段未改）。本批只改 `nodeMaterialSummaries` 与材料渲染行；改后 runner 的 `git diff --numstat` 必须仍是“figure-role 18/6 + 本批新增”，且 figure-role 段逐字未动。
- 若执行轮发现任何文件出现**新的**非本批脏态：先登记、另裁，不得覆盖他线改动。
- **所有 7 个 tracked 文件写前都做字节备份**到本批执行目录（建议 `build/agent-batch23/usage-r1/backup/`），并记录 `sha256sum`；回滚一律从备份恢复，不依赖 `git checkout`（HEAD 可能在执行期间移动）。

### 5.3 回滚程序

1. 用 §5.2 的备份逐字节恢复 7 个 tracked 文件；`sha256sum` 必须逐项等于 §5.1 的 pre 值。
2. 删除新增的 `KnowledgeTeachingMaterialPerNodeQueryInstrumentedTest.kt`。
3. 核验旧面：
   - `KnowledgeTeachingMaterialDao.kt` 的 `:24-61` 旧 SQL 与 `:62-66` 方法签名逐字等于 pre；
   - `KnowledgePort.kt:133-137`、`RoomKnowledgeBaseStore.kt:365-381` 逐字等于 pre；
   - `RoomTutorTeachingReferenceRepository.kt` / `StudyReviewPlannerService.kt` / `TutorTeachingReferenceSelector` 未被本批触碰；
   - runner 的 `git diff --numstat` 恢复为 `18 6`（仅 figure-role），`sha256 = 1af63a6…`。
4. 复跑 §4.2 的编译与单测；若仍红，登记为他线/基线问题，不归因本批。
5. 在 §7 写明回滚原因与逐文件 sha 核验结果；不得把“已回滚”写成“未采纳但保留改动”。
6. 本预注册文件本身不随代码回滚；它保留为审计记录。

---

## 6. 预期与边界（不承诺）

1. **预期 F1/F2 大幅上升**：镜像算术预期 F1 = **100%**（645/645）、F2 = **90.70%**（117/129）、F3 = **1.24%**（8/645）、F5 = **5/查询**、渲染 max **838 chars**。这些是镜像预期，不是执行轮读数；执行轮以实读覆盖。
2. **F2 的硬上限**：117/129 = 90.70% 是检索召回本身的天花板（12 条 MISS 的期望节点不在 top-5）；本修复不可能把 F2 做到 100%。F2 ≥90% 只有 0.70 个百分点余量，必须逐条核对 117 条命中查询的期望节点都拿到材料。
3. **不承诺检索分数变化**：本修复不动召回路径；117/129、MRR 0.7322997416020672、零漂移 0 是**无回归门**，不是可提升指标。不得声称“修复后检索更高/更低”，也不得用检索读数作为采纳/回滚依据。
4. **F4 在金标上为空真**：645/645 行有材料，金标没有“无材料节点”；`｜（该节点无材料）` 必须由单测夹具证明。只报“金标 0/0”不算通过 F4。
5. **R2/R3/R4/R5 属契约侧交接，本批不碰**：
   - **R2**：材料标题/正文完全缺失，只给 ≤60 字摘要（审计 `:34`）；
   - **R3**：`take(60)` 静默截断、27.2% 展示摘要被切、640 条 LaTeX 在切口损坏（`:35`）；
   - **R4**：boundary `take(80)`、82.3% 被切、58.7% 范围句看不到（`:36`）；
   - **R5**：2k 是名义值、`KNOWLEDGE_READ` 无 2k 运行时检查点、UTF-8 字节已越 2,000 B（`:37`、`:180-205`）。
   本批只修 R1 的分配/排序/显式标注；上述四项登记为契约侧后续交接，不在本批写面。
6. **字节边界**：新实现把材料段从 301 条增加到 645 条，镜像 UTF-8 max 从 2,079 B 升到 **2,267 B**（+188 B），仍 > 2,000 B。这是 R5 的可见化，不是本批新增的字节门；2k 在本批仍按**字符**判（max 838 chars）。
7. **排序不等于语义相关**：新顺序只保证“按冻结的角色/类型/标题/id 规则取最佳一条”，不主张该材料在语义上最相关，也不主张摘要质量提升。审计的 53.3% 静默缺失是**选择伪影**，不是检索失败；本修复不改变召回。
8. **数据边界**：当前包 64,973 条材料、645 个金标 top-5 节点行全部有材料；`title`/`contentId` 无非 BMP 字符（本代理实跑）。若未来包出现无材料节点或非 BMP 标题，F4 与 SQLite BINARY/镜像一致性需另立预注册，不沿用本批结论。
9. **装置边界**：设备读数是 emulator，不是真机；JVM 镜像只作零漂移/保真，不单独判分。设备不可用时记 `NOT_RUN`，不得用 JVM 读数冒充设备读数。
10. **冻结边界**：§1–§6 一字不动；执行轮只在文末追加 §7。任何偏离（改阈值、改排序、改旧 SQL、改召回、改分母）都必须另立预注册，不得在本批内“边跑边调”。

---

> 预注册结束。执行轮只追加 §7 结果节，不改 §1–§6。
