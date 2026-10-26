# KNOWLEDGE_READ 使用面形态审计：风险清单

- 日期：2026-10-26
- 状态：只读审计完成；本文只登记风险与归属，不改契约、不改代码。
- 审计对象：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt`
  - 工作副本 `sha256=1af63a6be70b9ff75dff26fbdd2a071f080632bae5f46a66cb7d5ed0986a358c`（与任务值一致）
  - `git diff --numstat` = `18 6`；diff 仅 `figure-role` 段，`KNOWLEDGE_READ` 段未改
- 证据口径：每条风险带 `file:line` 或本次实测数字；未运行的检查在 §4 明确标注。
- 复现夹具：`core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json`（3,670 节点 / 3,245 原子节点）、25 个 `moe-2025-teaching-support-v2-*.json` 教学卷（64,973 条材料）、`tools/kb_coverage/tables/golden_queries_v2.json`（129 条金标）。

---

## 1. 结论

KNOWLEDGE_READ 的模型可见面不是「材料正文」，而是 **header + 最多 5 行节点清单**：

```text
知识点候选 5 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）：
K1. 对勾函数：定位：数学必修第一册 第三章。$ab>0$ 才是对勾形状……（80 字处静默切断）
K2. 求函数最值的方法：定位：数学必修第一册 第三章。闭区间与开区间上的最值可能不同……（80 字处静默切断）
K3. 利用导数研究函数的最值：定位：……以确定最大最｜材料：极值点与区间的关系及最值可能取得的点
```

关键事实：

- **材料标题、材料正文、材料 id 都不出现**；只有 `summaryMarkdown` 的前 60 字。
- **不是每节点一条材料摘要**：129 条金标 top-5 中，645 个节点行只有 301 行（46.7%）有材料段，344 行（53.3%）静默缺失；期望节点有摘要仅 65/129（50.4%）。
- **截断全部静默**：boundary `take(80)`、summary `take(60)`，无省略号、无「已截断」提示；实际展示的 301 条摘要中 82 条（27.2%）在第 60 字被切，全库 640/15,114 条长摘要在切口处破坏 LaTeX。
- **2k 预算在 KNOWLEDGE_READ 上没有执行点**：实测 5 节点仅 538–753 chars（中位 627），2k 字符结构上不可达（约 0.9k chars 上限）；硬校验是 6,000，轮级总和 4,000/8,000。

| ID | 风险 | 归属 | 优先级 |
|---|---|---|---|
| R1 | 材料摘要不是每节点一条：全局 `LIMIT=nodes.size` + 重教类型排序，53.3% 节点无材料段，类型偏置 76.1% 误区材料 | 本线可改 | P1 |
| R2 | 材料标题与正文完全缺失：只给 ≤60 字摘要；材料正文 0 字，摘要中位 43 字 vs 正文中位 156 字 | 智能体面契约 | P1 |
| R3 | `take(60)` 静默吃掉步骤/公式：实际展示摘要 27.2% 被切，640 条 LaTeX 在切口处损坏，无任何提示 | 智能体面契约 + 本线可改（摘要生成侧） | P1 |
| R4 | 节点 boundary `take(80)` 吃掉范围/区分句：82.3% 被切，58.7% 范围短语整句落在 80 字后；模型可见面 62.6% 是 boundary | 智能体面契约 + 本线可改（boundary 改写侧） | P2 |
| R5 | 2k 预算是名义值：KNOWLEDGE_READ 无 2k 检查；按 UTF-8 字节算实测最大 2,079 B > 2,000 B；当前 5×80×60 形状字符上不可达 2k | 智能体面契约 | P2 |
| R6 | 数据级换行破坏单行形态与 `\ne`：13 条 summary 含真实换行（12 条在前 60 字内），6 条 boundary 在前 80 字内含换行 | 本线可改（sidecar 数据） | P3 |

优先级口径：P1 = 模型可能据此给出错误事实/错误公式，或关键节点系统性无材料；P2 = 信息显著失真但通常仍可回答；P3 = 低频数据/形态问题，修复成本低。

---

## 2. 风险清单

### R1 材料摘要不是每节点一条：全局 LIMIT + 重教类型排序造成静默缺失与类型偏置

**现象**

`KNOWLEDGE_READ` 对 5 个候选节点只发一次材料查询，`limit = nodes.size`（=5），SQL 的 `LIMIT` 是**跨全部节点合计 5 条**，不是每节点 1 条。排序沿用重教场景的类型优先级（`MISCONCEPTION_GUIDE` 最先、`CONCEPT_EXPLANATION` 靠后）。结果是：某些节点占满多个材料槽，另一些节点（包括金标期望节点）完全没有材料段，且没有占位或说明。

**证据**

- 代码：`RoomTutorToolRunner.kt:388-392`（`limit = nodes.size`）；`KnowledgeTeachingMaterialDao.kt:24-66`（`best_role_rank → material_type CASE → title → material_id`，`LIMIT :limit` 在 `:59`）；渲染侧 `RoomTutorToolRunner.kt:397-405` 只在选中的材料集合里按节点挑 PRIMARY/第一条。
- 实测（129 条金标 top-5，JVM 融合镜像，`Recall@5=117/129=0.907`，与仓库 `build/agent-batch14/d3/jvm-fused-metrics.txt:7` 的 `117/129` 一致）：
  - 645 个节点行：301 行有材料摘要（46.7%），344 行（53.3%）没有材料段。
  - 每查询覆盖 1–4 条：`1:21, 2:56, 3:40, 4:12, 5:0`；均值 2.333；**没有任何一条查询 5 个节点都有摘要**。
  - 期望节点有摘要：65/129（50.4%）；在 top-5 命中 `expectedSlug` 的 117 条里是 65/117（55.6%）。
  - 645 个取回材料槽中 344 个（53.3%）没有产生任何模型可见摘要；301 个被使用的全是该节点的 PRIMARY（fallback 0）。
  - 选中类型：`MISCONCEPTION_GUIDE 491 / METHOD_MODEL 150 / CONCEPT_EXPLANATION 4`；包内对应 `3,950 / 25,475 / 35,548`（`6.1% / 39.2% / 54.7%`）。6.1% 的误区材料占 76.1% 槽位，54.7% 的概念讲解只占 0.6%。
- 具体实例（金标查询「f(x)=x+1/x（x>0）的最小值是多少？它在哪几个区间上单调？」）：
  - K1 对勾函数：36 条材料（27 `CONCEPT_EXPLANATION` + 9 `METHOD_MODEL`）→ **0 摘要**。
  - K2 求函数最值的方法：7 条材料（6 `METHOD_MODEL` + 1 `CONCEPT_EXPLANATION`）→ **0 摘要**。其中有一条高度相关的 PRIMARY「求函数最值的一般思路」；4 个误区材料先占 4 槽，最后 1 个 `METHOD_MODEL` 槽按 title 排序给了 K4（「函数…」<「求…」），这条没进来。
  - K5 函数的极值：34 条材料（含 3 条 `MISCONCEPTION_GUIDE`）→ 占 3 槽，只用 1 条，2 条丢弃。
- 独立复核：把同一份 sidecar 数据装入 SQLite，逐字执行 DAO 的 SQL（`LIMIT 5`）→ 选中 5 条、覆盖 3 个节点、K1/K2 无摘要、2 条未使用，与 Python 镜像一致（`python C:/Users/听云/AppData/Local/Temp/audit_material_sql.py`）。
- 工具描述：`TutorToolDescriptions.kt:12-14, :73` 只说返回「代号+名称+边界+绑定材料摘要」，没有说某些节点会没有、也没有说只截 60 字。模型没有理由把「没有材料段」理解为「材料存在但没被选中」。

**归属**

本线可改（core:database 的 DAO 查询与 core:data 的材料选择）。若要改变「模型可见形态」（例如每节点一条、缺失显式标注），则同时涉及智能体面契约。

**建议优先级**

**P1**。模型可能对学生说这两个知识点「没有讲解材料」，而 K1 实际有 36 条；这是静默的事实错误，不是单纯的信息少。

**建议动作**

- 本线：把 `limit` 从「跨节点合计」改为「每节点至少保证 1 条」的取数形状，或先按节点取 PRIMARY 再全局排序；至少让「无材料」与「材料未被选中」可区分。
- 契约侧：如果保持单次 5 条的预算，结果行需要显式区分「该节点无材料」与「材料未随本次结果返回」，避免模型自行补全为“没有”。

---

### R2 材料标题与正文完全缺失：模型只拿到 ≤60 字摘要，正文 0 字

**现象**

`nodeMaterialSummaries` 只读 `summaryMarkdown`；`KnowledgeTeachingMaterialRecord.title`、`contentMarkdown`、`applicabilityMarkdown`、`boundaryMarkdown` 都不进工具结果。模型看到的是「一句摘要」，不是「材料」；而且摘要还要再 `take(60)`。这不是「只给标题」，而是**标题和正文都不给，只给摘要前 60 字**。

**证据**

- 代码：`RoomTutorToolRunner.kt:405` 只取 `summaryMarkdown`；`:359-361` 只拼接 `summary.take(60)`；`:418` 定义 60。DAO 虽然 `SELECT material.*`（`KnowledgeTeachingMaterialDao.kt:26`），正文/标题读回后被丢弃。
- 实测（2025 教学卷 64,973 条材料）：
  - `summaryMarkdown` 中位 43 字，`p90 78`，`p99 122`，最大 326；`contentMarkdown` 中位 156 字，`p90 371`，`p99 685`，最大 2,695；正文/摘要中位比 **3.43×**（摘要约为正文的 29%）。
  - 模型可见字符构成（129 条 top-5 合计 81,300 chars）：boundary **62.6%**、材料摘要 **17.0%**、名称/代号/表头/换行 20.4%。工具描述写「读取知识点讲解材料」，实际大头是 boundary 定位文本。
  - 实际展示的 301 条摘要：全长中位 47，`p90 78`，最大 123；有材料时平均 45.9 字/条。
- 标题/正文可见性：选中材料的 5 个标题在 chosen 渲染文本中均不出现（`titles visibly present? [False, False, False, False, False]`）。

**归属**

智能体面契约。要不要把正文/标题披露给模型、以什么预算披露，属于工具结果形态与披露边界；本线只能提供更实质的摘要或独立读口。

**建议优先级**

**P1**。材料侧的核心价值（步骤、公式、例题、边界）大量存在于正文；只给 60 字摘要使工具名与能力不匹配，模型容易给出泛泛讲解。

**建议动作**

- 契约侧：决定是否新增「材料全文读取」工具或扩大摘要预算；至少把「摘要 ≠ 材料全文」写进工具描述，避免模型把摘要当全文。
- 本线：若维持摘要形态，优先把「步骤骨架/关键公式」写进 60 字以内，而不是把正文压成一句泛述。

---

### R3 `take(60)` 静默吃掉步骤/公式：27.2% 实际展示摘要被切，切口可破坏 LaTeX

**现象**

摘要截断使用 `String.take(60)`，没有省略号，也没有「已截断」提示。若第 60 字落在公式中间，模型看到的是不完整、甚至语法损坏的公式；它无法区分「摘要到此为止」与「公式就是这样」。步骤同样：正文有编号步骤时，摘要通常不保留步骤标记。

**证据**

- 代码：`RoomTutorToolRunner.kt:359-361`（`it.take(KNOWLEDGE_READ_MATERIAL_DIGEST_CHARS)`）、`:418`（`= 60`）；无 `truncationNote`（对照 MASTERY_READ `:746-749`、ADVISORY_READ `:1304-1307`）。
- 实测（64,973 条材料）：
  - `summary > 60`：15,114 条（23.3%）；其中 14,918 条（98.7%）在第 60 字切断非句末标点；丢弃中位 15 字，最大 266 字。
  - 切口 LaTeX 损伤：640/15,114 条长摘要（632 unclosed `$`、230 mid-command、173 unclosed brace、22 unclosed `\left`；分类可重叠）。
  - 实际展示的 301 条摘要：82/301（27.2%）在第 60 字被切；其中 2 条在切口处破坏 LaTeX；只有 1/301 含步骤标记；36/301（12.0%）含公式标记。
  - 关键步骤：22,714/64,973（35.0%）正文含编号步骤；其中只有 589（2.6%）摘要含步骤标记；全库摘要含步骤标记 600（0.9%）。
  - 公式：14,933/64,973（23.0%）正文含 ≥2 个公式标记；其中 4,420（29.6%）摘要含 ≥1 个；10,427（69.8%）摘要和标题都不含公式；54 条公式只在第 60 字后（被切掉）。
- 真实 top-5 渲染实例（不是构造）：
  1. 「若双曲线的渐近线为 `$y=\pm kx$`（或 `$ax\pm by=0$`），可把双曲线的方程设为 `$k^2x^2-y^2=`」——在 `=` 处切断，右侧 `m` 与闭 `$` 丢失。
  2. 「`$\{a_n\}$`、`$\{b_n\}$` 均为等差数列时，`$\dfrac{a_n}{b_n}=\dfrac{S_{2n-1`」——在 `\dfrac` 参数中切断，公式与闭 `$` 丢失。
  3. 全库示例：「…偶函数 `$\Leftr`」——`\Leftrightarrow` 被切成 `\Leftr`；「…关于点 `$\left(\dfrac{a+b}{2},c\right)`」——丢掉闭 `$`。

**归属**

智能体面契约（截断语义与提示） + 本线可改（摘要生成侧保证 ≤60 字且公式安全）。

**建议优先级**

**P1**。公式损坏不是「少一点信息」，而是把错误的数学表达式交给模型；模型可能照抄或据此推理。

**建议动作**

- 契约侧：截断时加可见标记，或在切口落在公式/命令中时退到安全边界；不要让 `take` 直接切断 `$...$`。
- 本线：把摘要写成 ≤60 字、公式完整、步骤骨架可辨认的形态；对含公式的摘要单独做长度校验。

---

### R4 节点 boundary `take(80)` 吃掉范围/区分句：模型可见面 62.6% 是 boundary，且 58.7% 的范围短语看不到

**现象**

每个节点的 boundary 只取前 80 字。boundary 100% 以「定位：」开头（定位句中位 21 字），真正用于区分知识点、说明“不涉及什么”的范围句经常落在 80 字之后。模型因此看到大量教材位置信息，却看不到最关键的边界区分。

**证据**

- 代码：`RoomTutorToolRunner.kt:358`（`boundaryMarkdown?.take(80)`），无省略号。
- 实测（3,245 个原子节点）：
  - boundary 中位 106 字，`p90 177`，`p99 529`，最大 643；82.3%（2,671/3,245）超过 80。
  - 其中 96.3%（2,571/2,671）在第 80 字切断非句末标点。
  - 100% 以「定位：」开头；定位句中位 21 字（最大 63），占 80 字窗口约 26%。
  - 728 个 boundary 含范围/区分短语（「相区分」「区分」「不涉及」「范围限于」「不包括」「区别于」等）；其中 427 个（58.7%）整句落在 80 字之后，模型看不到。
  - 348 个节点前 80 字含公式；106 个节点的公式只在 80 字后（被切掉）。
- 载荷构成：129 条 top-5 的 81,300 chars 中 boundary 50,910（62.6%），材料摘要 13,803（17.0%）。

**归属**

智能体面契约（每行输出形态与截断） + 本线可改（boundary 文本改写：把范围/区分句前置、压缩位置前缀）。

**建议优先级**

**P2**。多数节点的知识点名称和位置仍然可读，但“边界”这一字段的核心用途（区分易混点）在 58.7% 的节点上失效。

**建议动作**

- 本线：把 boundary 改成「范围/区分在前，教材定位在后」的 80 字可读文本；至少让范围句落在前 80 字。
- 契约侧：若保留 80 字窗口，考虑给 boundary 单独一行或加截断标记，避免它与「｜材料：」粘连成难读的一行。

---

### R5 2k 预算是名义值：KNOWLEDGE_READ 无 2k 检查；按字节算实测最大 2,079 B 已越 2,000 B

**现象**

`MAX_TOOL_RESULT_CHARS = 2_000` 是字符（UTF-16 码元）预算，但 `KNOWLEDGE_READ` 没有任何 2k 检查点；它只受 `TutorToolOutcome` 的 6,000 硬校验和轮级 4,000/8,000 约束。实测 5 节点渲染远低于 2k 字符，但按 UTF-8 字节算最大已超过 2,000 B；当前 5×80×60 的形状又使 2k 字符在结构上不可达，预算失去约束意义。

**证据**

- 代码：`TutorToolLoop.kt:452`（2,000）、`:471`（6,000）、`:437-443`（`requireSafeModelText(... maxChars = MAX_TOOL_RESULT_CHARS_EXTENDED)`）、`:500-508`（轮级 4,000 + 扩展 4,000）；`ModelTasks.kt:1311` 按 `length`（UTF-16 码元）判定。`RoomTutorToolRunner.kt` 中 2k 只出现在 MASTERY_READ `:622-626` 与 ADVISORY_READ `:1288`，KNOWLEDGE_READ 没有引用。
- 实测（129 条金标 top-5）：chars 538–753（中位 627），即 2,000 chars 的 26.9%–37.6%；UTF-8 bytes 1,331–2,079（中位 1,700），即 2,000 B 的 66.6%–**104.0%**。最大 bytes 的一条是 737 chars / 2,079 B；最大 chars 的一条是 753 chars / 2,031 B。
- chosen 查询：607 chars / 1,691 B（30.4% of 2,000 chars；84.6% of 2,000 B）。
- 结构上限：当前包内最长节点名 24 字；5 行 ×（代号 4 + `. ` 2 + 名称 24 + `：` 1 + 80 + `｜材料：` 4 + 60）≈ 875，加 header 43 与换行约 0.9k chars。2k 字符在结构上不可达。
- 测试覆盖：`KnowledgeReadRecallBudgetTest.kt:142-145` 的 `<= MAX_TOOL_RESULT_CHARS` 断言使用无 boundary、无材料的假节点（`FakeStudyDatabasePort.readKnowledgeTeachingMaterialsForNodes` 的 `teachingMaterials` 在测试中为空，`RoomBackedStudyExperienceRepositoryTest.kt:2797`），因此它不能覆盖真实的 boundary/材料截断形态。

**归属**

智能体面契约（预算单位、执行点、字节/字符口径）。本线无法单方面决定 2k 是否要改按字节或改成每调用硬门。

**建议优先级**

**P2**。当前没有 2k 字符超限；风险在于“预算看起来存在、实际不存在”，以及未来改节点数/长度后无人守门；若上游按字节计费，最大 2,079 B 已越 2,000 B。

**建议动作**

- 契约侧：明确 2k 的单位（chars vs bytes）与服务对象；若要保留 2k，给 KNOWLEDGE_READ 加实际检查点；若不加，删掉“2k”表述，避免误以为已守门。
- 测试侧：把 fake 节点换成带真实 boundary/材料的夹具，至少覆盖一条 60 字切口与一条 80 字 boundary。

---

### R6 数据级换行破坏单行形态与 `\ne`：13 条 summary、6 条 boundary

**现象**

教学 sidecar 中有少量文本含真实换行：13 条 `summaryMarkdown` 含换行，其中 12 条在前 60 字内；这些换行大多来自 LaTeX `\ne`（不等于）被 JSON 解析成 `\n` + `e`。被选中时，单行渲染会断成两行，`\ne` 公式也丢失。另有 6 条 boundary 在前 80 字内含换行（PHYSICS 的「一、…\n1. …」结构），同样破坏单行形态。

**证据**

- 数据扫描（25 个 sidecar，64,973 条材料）：13 条 summary 含 `\n`；12 条 `\n` 位置 < 60。示例：`math-hj-zhouqi-dingyi` 的 `summaryMarkdown` 为「存在 `$T\ne 0$` 使 `$f(x+T)=f(x)$`…」，实际第 5 个字符就是换行（`ord=10`），`\ne` 变成「换行 + e」。
- boundary：6/3,245 条在前 80 字内含换行，全部为 PHYSICS 的结构化文本（如「一、功能关系\n1. 内容：…」）。
- 代码侧没有清理：`RoomTutorToolRunner.kt:358-362` 直接拼接 boundary/summary 原文，`requireSafeModelText` 允许 `\n`（`ModelTasks.kt:1320`）。

**归属**

本线可改（sidecar 数据修正：JSON 中 LaTeX 反斜杠正确转义；boundary 换行改空格）。

**建议优先级**

**P3**。频率低（13/64,973、6/3,245），但一旦命中就是公式损坏/行结构破坏，修复成本低。

**建议动作**

- 本线：修正 `math-fx-*` / `math-hj-*` 中含 `\ne` 的 13 条 summary 的 JSON 转义；PHYSICS 的 6 条 boundary 把结构换行改成空格或分号。
- 契约侧：可选，在渲染前把 summary/boundary 中的换行归一为空格，作为防御层。

---

## 3. 实测口径与复现

### 3.1 审计对象校验

```text
$ sha256sum core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
1af63a6be70b9ff75dff26fbdd2a071f080632bae5f46a66cb7d5ed0986a358c *core/.../RoomTutorToolRunner.kt

$ git diff --numstat -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
18  6  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt
```

diff 只涉及 figure-role 段（`attachGeneratedRedrawToRevision` 与 `visualRole`），未触及 `knowledgeRead` / `nodeMaterialSummaries`。

### 3.2 检索 + 渲染镜像

命令：

```text
python C:/Users/听云/AppData/Local/Temp/audit_knowledge_read.py
```

脚本做了：

1. 按 `KnowledgeSearchFeatureExtractor`（v1：16 片段 / 192 节点特征 / 128 查询特征）复刻节点与查询特征；
2. 按 `ProblemOrganizationDao.searchSubjectKnowledgeRecallCandidates` 的 `matchCount DESC → ATOMIC 优先 → canonical_name → node_id` 取 512 域；
3. 按 `DenseFusion`（α=0.5、域内 min-max、缺失腿 0、并列 node_id 升序）与随包 `.vec`、金标查询向量做融合重排；
4. `take(5)` 后按 `nodeMaterialSummaries` 与 `RoomTutorToolRunner.kt:349-373` 逐行渲染。

关键输出：

```text
FUSED mirror Recall@5 117 / 129 = 0.907
RENDER over 129 top5: chars min/median/p90/p99/max 538 627 698 739 753
RENDER over 129 top5: utf8 min/median/p90/p99/max 1331 1700 1871 2060 2079
SUMMARY coverage over 129 top5: {"1":21,"2":56,"3":40,"4":12,"5":0} mean 2.333
expected node got a summary 65 of 129
MATERIAL SLOTS over 129: fetched 645 used 301 unused 344 (53.3%)
USED summaries cut at 60 82 of 301 (27.2%)
COMPOSITION over 129: total 81300 boundary 50910 (62.6%) material summary 13803 (17.0%) other 16587 (20.4%)
```

融合 Recall 与仓库自己的产物一致：

- `build/agent-batch14/d3/jvm-fused-metrics.txt:7`：`Recall@5(主集)=0.9069767441860465 (117/129)`
- `DenseRecallAssembly.kt:98`：`refFusedD1 117/129`

说明 top-5 节点不是手挑的。

### 3.3 材料选择 SQL 复核

命令：

```text
python C:/Users/听云/AppData/Local/Temp/audit_material_sql.py
```

把同一份 sidecar 数据装入 SQLite，逐字执行 `KnowledgeTeachingMaterialDao.readForKnowledgeNodes` 的 SQL（`LIMIT 5`）。输出：

```text
SQL selected 5
binding 极值概念的四个要点… [('函数的极值', 'PRIMARY')]
binding 极值点与导数为零的关系 [('函数的极值', 'PRIMARY')]
binding 求函数最值的关键点 [('利用导数研究函数的最值', 'PRIMARY')]
binding 由 f′(x) 图象判断极值点与最值时的三处误判 [('函数的极值', 'PRIMARY')]
binding 函数单调性的四类应用 [('函数单调性的应用策略', 'PRIMARY')]
nodes with summary 3 of 5
missing ['对勾函数', '求函数最值的方法']
unused selected ['kb-material:beaaa4a6', 'kb-material:bdd30d81']
```

与 Python 镜像一致：5 条选中、3 个节点有摘要、K1/K2 无摘要、2 条未使用。

### 3.4 现有单测

命令：

```text
./gradlew :core:data:testDebugUnitTest --tests com.tingyun.smartmistakebook.core.data.study.KnowledgeReadRecallBudgetTest --console=plain
```

结果：

```text
BUILD SUCCESSFUL in 8s
31 actionable tasks: 1 executed, 30 up-to-date

core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.study.KnowledgeReadRecallBudgetTest.xml
tests=2 failures=0 errors=0 skipped=0
- knowledgeReadQueriesTheFused512DepthButRendersOnlyTheTopFive
- aSmallerCandidateSetIsRenderedAsIsWithoutPadding
```

注意：该测试的假节点没有 boundary/材料，2k 断言未覆盖真实形态（见 R5）。

---

## 4. 未验证与边界

- **未跑设备端 Android 检索**：本文的检索是 JVM 镜像 + 冻结金标查询向量；设备编码器与镜像的 parity 注释见 `GoldenRetrievalJvmTest.kt:103-104`（min cosine 0.99978），不是本次实测。
- **未跑真实 Room 库**：材料选择用 SQLite 重放同一份 sidecar 数据，未在设备 Room 数据库上执行；`status='RETIRED'` 过滤等 DB 状态未覆盖。
- **不是 provider token 计数**：2k/字节数是 UTF-8 字节与 UTF-16 字符；实际模型 token 数未测。
- **摘要“语义保留”只用步骤/公式标记近似**：未做人工语义评分，不能证明摘要与正文语义等价；只能证明步骤标记/公式标记的保留率。
- **本轮没有修改仓库源码**：Python 审计脚本在系统临时目录，Gradle 只写 `build/` 产物；审计对象与 diff 保持原样。

---

## 附录 A：chosen top-5 渲染原文（607 chars / 1,691 UTF-8 bytes）

查询：`f(x)=x+1/x（x>0）的最小值是多少？它在哪几个区间上单调？`（金标 `expectedSlug=对勾函数`）

```text
知识点候选 5 个（代号 K1..Kn 本会话有效，写掌握度时 terms 填代号）：
K1. 对勾函数：定位：数学必修第一册 第三章。$ab>0$ 才是对勾形状，$ab<0$ 时是双撇函数、单调性与最值完全不同，不能套用对勾函数的极值公式；对勾函数在定义域上整体无
K2. 求函数最值的方法：定位：数学必修第一册 第三章。闭区间与开区间上的最值可能不同，端点能否取到要按定义域判断；只求极值而不与端点值比较会漏掉最值；含参数的二次函数必须按对称轴与区间
K3. 利用导数研究函数的最值：定位：数学选择性必修第二册·第五章 一元函数的导数及其应用。讲利用导数求函数在闭区间上最值的方法：先求区间内的极值，再把各极值与两端点处的函数值比较以确定最大最｜材料：极值点与区间的关系及最值可能取得的点
K4. 函数单调性的应用策略：定位：数学必修第一册 第三章。脱去函数符号的前提是函数在相应区间上单调；比较大小前要把自变量化到同一单调区间；由单调性求参数时要单独检验端点与分段点。｜材料：比较函数值大小、解抽象不等式、求最值以及由单调性求参数
K5. 函数的极值：定位：数学选择性必修第二册 第五章·函数与导数。极值是函数在一点附近的局部性质：可导函数取得极值的点处导数为零，且导数在该点两侧变号；"导数为零"只是必要条件，｜材料：极值点取在区间内部而非端点；极值是局部概念；极大值不一定大于极小值；极值点是自变量的值。
```

逐行测量：

| 行 | chars | UTF-8 B | 名称 | boundary 显示/全长 | 材料显示/全长 |
|---|---:|---:|---|---:|---:|
| K1 | 89 | 229 | 对勾函数 | 80 / 135 | 0 / 0（节点有 36 条材料） |
| K2 | 93 | 269 | 求函数最值的方法 | 80 / 90 | 0 / 0（节点有 7 条材料） |
| K3 | 118 | 343 | 利用导数研究函数的最值 | 80 / 133 | 18 / 18 |
| K4 | 121 | 353 | 函数单调性的应用策略 | 75 / 75 | 27 / 27 |
| K5 | 138 | 399 | 函数的极值 | 80 / 116 | 44 / 44 |

K3 的 boundary 在「确定最大最」处静默切断，紧接「｜材料：…」；K1/K2 的 boundary 同样断在句中。三处都没有省略号。

---

## §R1 复测（批次23）

- 复测时点：2026-10-26（+08:00）。**只读复测**：未改源码、未提交。
- 口径：与本文 §R1 完全同口径——同一份金标 129 条 top-5、同一检索/渲染镜像（`audit_knowledge_read.py`：`KnowledgeSearchFeatureExtractor` → 词面 512 → `DenseFusion` α=0.5/域内 min-max/缺失腿 0/并列 `node_id` 升序 → `take(5)` → runner 渲染）、同一 SQLite 逐字复刻 DAO SQL（**不是 DuckDB**）。唯一变量是材料取数：旧「全局 `LIMIT=nodes.size` + 补救类型序」→ 批次23 新「每节点 1 条 + 教学优先序」。
- 新语义代码：`KnowledgeTeachingMaterialDao.kt:96-122`（`readBestTeachingMaterialPerNode`：相关子查询 + `LIMIT 1`，`role → material_type → title → material_id`）；`RoomTutorToolRunner.kt:345-372`（渲染：boundary `take(80)`、summary `take(60)`、无材料显式标记）、`:381-393`（改调新 port）、`:403`（`（该节点无材料）`）。

### 复测命令（本轮实跑，exit=0）

```text
$ python C:/Users/听云/AppData/Local/Temp/audit_knowledge_read.py          # EXIT=0
FUSED mirror Recall@5 117 / 129 = 0.907
SUMMARY coverage over 129 top5: distribution {"0":0,"1":21,"2":56,"3":40,"4":12,"5":0} mean 2.333
expected node got a summary 65 of 129
MATERIAL SLOTS over 129: fetched 645 used 301 unused 344 (53.3%)

$ python C:/Users/听云/AppData/Local/Temp/audit_material_sql.py            # EXIT=0（旧 SQL 基线复核）
SQL selected 5
nodes with summary 3 of 5
missing ['对勾函数', '求函数最值的方法']

$ python C:/Users/听云/AppData/Local/Temp/retest_r1_batch23.py             # EXIT=0（新语义复测）
FUSED mirror Recall@5 117 / 129 = 0.907
BASELINE recalibration: F1 46.67 | F2 65/129 50.39 | F3 491/645 76.12
F1 py 645/645 100.0 | F1_sql 645/645 100.0
F2 py 117/129 90.7 | F2_sql 117/129 90.7 (recall hits 117)
F3 py 8/645 1.24 | F3_sql 8/645 1.24  types {"CONCEPT_EXPLANATION":563,"METHOD_MODEL":74,"MISCONCEPTION_GUIDE":8}
F4 no_material_rows 0 marked 0 fake_material_segments 0
F5 rows/query py {"5":129} | sql {"5":129}
   render chars(UTF-16) min/median/max 660 743 838 (max 41.9% of 2000 chars)
   render utf8 bytes min/median/max 1707 1985 2267 (max 113.3% of 2000 bytes)
SQL vs Python mirror: checked_sql_rows 645 mismatch_queries 0 mismatch_nodes 0
```

`retest_r1_batch23.py` 复用冻结镜像的 `full_recall` / `bindings_by_node` / 材料包，只替换选材与渲染；SQLite 部分把同一份 sidecar 装入内存库，按 Room 展开的五参数逐字执行新 DAO SQL。645 行选材与 Python 镜像 **0 差异**。落盘日志：`build/agent-batch23/usage-r1/retest-r1.log`。

### 与基线对拍

| # | 判据 | 批次22 基线 | 批次23 复测（新语义） | 结论 |
|---|---|---:|---:|---|
| F1 | 有料节点获摘要比例 | 46.7%（301/645） | **100.0%（645/645）** | improved |
| F2 | 期望节点有材料比例 | 50.4%（65/129） | **90.7%（117/129）** | improved |
| F3 | 误区槽位占比 | 76.1%（491/645） | **1.24%（8/645）** | improved |
| F4 | 无材料显式标注 | 无标注（静默省略） | 渲染器固定输出 `｜（该节点无材料）`；金标 0/0 行 | improved（机制） |
| F5 | 单查询材料总数 / 预算 | 5 / 名义 2k | **5/查询（129/129）**；max 838 chars（41.9% of 2,000） | unchanged（条数）；字符预算内 |

- **同口径校准**：同一脚本用旧语义重算得到 F1 46.67%、F2 50.39%、F3 76.12%，与冻结基线逐位一致，说明复测装置未漂移。
- **召回无回归**：融合镜像仍为 117/129；F2 的 90.7% 就是 117/129 的天花板（12 条检索 MISS 的期望节点不在 top-5），117 条命中查询的期望节点全部拿到材料。
- **F3 口径**：分母为 129 查询的 645 个被选材料槽（每查询 5 条，金标节点全部有材料）；分子为 `MISCONCEPTION_GUIDE` 8 条。类型分布从 `491/150/4` 变为 `563/74/8`（概念讲解/方法模型/误区）。

### 边界与未验证

- **F4 在金标上是空真**：645 个节点行全部有绑定材料，所以金标 0 行需要标记，不能据此判“标记已生效”。标记的实证是渲染分支与单测夹具：`RoomTutorToolRunner.kt:357-359`、`:403`；`KnowledgeReadRecallBudgetTest.kt:235`（`kc-1 无材料必须显式标注`）、`:241`（恰好 1 个标记）。**本轮未运行 Gradle 测试**，该两行是源码/断言文本，不是本轮实跑读数。
- **F5 的 2k 单位**：按字符（UTF-16）max 838 ≤ 2,000；按 UTF-8 字节 max 2,267 B > 2,000 B（基线 2,079 B）。这是本文 R5 的契约侧问题在材料增多后的放大，不是本批复测判据，也不写成“2k 字节通过”。
- **只读边界**：未跑设备端 Android 检索、未跑真实 Room 库、未跑 Gradle；检索读数来自 JVM 镜像，材料选择来自 SQLite 重放（`status='ACTIVE'` 全量，未覆盖真实库中的 RETIRED 行）。
