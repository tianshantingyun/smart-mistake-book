# KNOWLEDGE_READ 渲染 v2（材料《标题》＋正文节选）：预注册（2026-10-31）

> **性质**：出数前冻结。§1–§5 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§5。
> **写作时点**：2026-10-31；HEAD `5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD` 实跑）。
> **上游**：`docs/research/2026-10-26-kb-usage-surface-audit.md` R2（`:34`、`:83-109`）、R3（`:113-146`）、R5（`:37`）；R1 的材料取数与 F1–F5 基线 = `docs/research/2026-10-26-kb-usage-r1-prereg.md` §7（`:319-342`）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
git status --porcelain | wc -l                                  # 691
git status --porcelain | awk '{print $1}' | sort | uniq -c      # 479 ?? / 212 M
git rev-parse HEAD                                             # 5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa
git status --porcelain -- <§5.1 的 7 个触碰文件>                # 空（全部 CLEAN）
ls docs/research/2026-10-31-kb-knowledge-read-render-v2-prereg.md  # No such file
```

- **目标文件不存在 → 本次是新建，只写一次**；执行轮结果按“只追加”写 §7。
- 本批 7 个触碰文件当前全部 CLEAN，pre-sha 见 §5.1；整树 691 条脏项属他线，本批不触碰、不裹挟、不回滚。

### 0.2 数字口径

本文件所有计数均为本会话直接对当前工作树实算；引用批次 23 的设备/镜像读数时注明“转引”。

| 事实 | 值 | 实算方式 |
|---|---:|---|
| 教学 sidecar | **25** 个（`moe-2025-teaching-support-v2-01..25.json`） | `glob`，排除 `-index` |
| 材料总数 | **65,014** | 遍历 25 个 sidecar 的 `materials` |
| 材料标题长度 | 中位 **16.0**、均值 21.66、p99 **62**、max **183**；>120 字共 **4** 条 | Python `len()` |
| 材料正文长度 | 中位 **156**、p90 370.7、p99 685、max **2,695** | 同上（`contentMarkdown`） |
| 正文含换行 | **7,849 / 65,014（12.07%）** | `"\n" in contentMarkdown` |
| 标题含换行 | **7 / 65,014** | 同上 |
| 知识点显示名 | 3,280 个；中位 9、p99 22、max **24** | 节点包 `knowledgePoints[].name` |
| 知识库 header 字面量 | **43** 字符 | `RoomTutorToolRunner.kt:454` 的字面量 |

> 与 2026-10-26 审计口径的 `64,973` 差 **41** 条：本会话按当前工作树实算为 65,014；差异原因未核（后续批次内容更新与否不在本预注册判定范围）。标题中位 16.0 与题意一致。

---

## 1. 行格式（冻结）

### 1.1 语法

每个节点**恰好一条物理行**，二选一：

```text
<marker>. <displayName>[：<boundary>]｜材料《<materialTitle>》<bodyExcerpt>
<marker>. <displayName>[：<boundary>]｜（该节点无材料）
```

| 段 | 来源 | 变换/上限 |
|---|---|---|
| `<marker>` | 会话代号 `K1..Kn`（`registry != null`，`RoomTutorToolRunner.kt:439-446`）；否则 `index + 1` | 原文 |
| `<displayName>` | `node.displayName` | 原文，不截断 |
| `：<boundary>` | `node.boundaryMarkdown` | **仅非 null 时出现**；`safeTruncateMarkdown(flatten(boundary), 80)` |
| `｜材料《<materialTitle>》` | `KnowledgeTeachingMaterialRecord.title`（`StudyDatabaseRecords.kt:248`） | **原样**，不截断、不改写 |
| `<bodyExcerpt>` | `KnowledgeTeachingMaterialRecord.contentMarkdown`（`StudyDatabaseRecords.kt:251`） | `safeTruncateMarkdown(flatten(content), 120)` |
| `｜（该节点无材料）` | 常量 `KNOWLEDGE_READ_NO_MATERIAL_MARKER`（`RoomTutorToolRunner.kt:495`） | 原文，**标记文本不变** |

- **`summaryMarkdown` 不再进结果**。这是本批的契约变更（审计 R2 `:87`：此前只给摘要前 60 字，标题与正文 0 字）。材料选择仍走 `readBestKnowledgeTeachingMaterialPerNode`（`KnowledgePort.kt:146`、`RoomKnowledgeBaseStore.kt:384`），**选择语义零改**。
- 材料段与无材料段**互斥**：每行恰好一种尾巴，不得同时出现、不得都不出现。
- `flatten(text)` = 把 `\r\n` / `\r` / `\n` 各归一为**一个半角空格**，其余字符不动；**先归一、再 safeTruncate**。依据：7,849/65,014 条正文含真实换行，不归一则“每节点一行”在 12% 的行上不成立（审计 R6 `:38` 的数据级换行问题，本批只在渲染侧消除，不改 sidecar 数据）。
- `safeTruncateMarkdown` 沿用现有实现（`RoomTutorToolRunner.kt:91-106`）：切口落在公式内退到公式起点，否则退到句号/分号；无安全点回退 `take(limit)`。公式/句界规则对 80 与 120 两个上限同样生效。

### 1.2 示意（真实 sidecar 记录 + 虚构节点名）

材料取自 `moe-2025-teaching-support-v2-01.json` 第 1 条：标题《离散型集合含参问题：列等号→解参数→代回集合检验互异性》（27 字），正文 87 字（flatten 后），整段小于 120 字，故节选为全文：

```text
K1. 集合的运算与含参问题：定位：数学必修第一册 第一章。｜材料《离散型集合含参问题：列等号→解参数→代回集合检验互异性》1. 由集合关系列等号（如 A∩B=B→B⊆A，可得元素对应关系）。 2. 解出参数值（可能多个解）。 3. 逐个代回原集合，检验元素互异性与题给关系是否成立，舍去不满足的。
```

（节点名与 boundary 为示意，仅用于展示拼接顺序；材料标题与正文逐字来自 sidecar。）

### 1.3 行格式不变量（F8/F9 的判定对象）

| # | 不变量 | 判法 |
|---|---|---|
| I1 | 每节点恰好一条物理行，行内 **0 个** `\r` / `\n` | 按 `\n` split 后节点行数 = `resultCount` |
| I2 | 有材料行的标题与 `title` **逐字节相同** | 直接比对 |
| I3 | 节选 ≤ 120 字符 | `length` |
| I4 | boundary ≤ 80 字符 | `length` |
| I5 | 输出中不出现 `summaryMarkdown` 的原文 | 逐行比对夹具摘要 |
| I6 | 原始 `knowledgeNodeId` 不进结果 | 现有断言不变 |
| I7 | 每行恰好一种尾巴（材料段或无材料标记） | 计数 |

> 标题含 CR/LF 的 7 条若进入 top-5：标题按“原样”优先，I1 判红，执行轮按数据缺陷登记并触发 §5.3，**渲染层不得改写标题**。

---

## 2. 预算（冻结）

### 2.1 单位与门限

- **整条 ≤ 2,000 字符**（Kotlin `String.length`，UTF-16 code unit），不是字节。依据：`TutorToolOutcome.MAX_TOOL_RESULT_CHARS = 2_000`（`TutorToolLoop.kt:489`）；当前构造器的硬校验是 6,000（`:477`、`:508`），**2,000 此前只是名义目标**（审计 R5 `:37`）。本批把 2,000 变成 KNOWLEDGE_READ 的**真实渲染门**。
- **UTF-8 字节只作信息报告，不作判据。** 口径理由：v1 渲染 129 条实测 UTF-8 最大 **2,267 B**（`build/agent-batch23/usage-r1/retest-r1.log:18`），若按字节判会把一个既有形态直接判红；本批冻结为字符门，执行轮不得中途换单位。
- `TRUNCATION_NOTE_RESERVE_CHARS = 240`（`RoomTutorToolRunner.kt:73`）→ **正文预算 = 2,000 − 240 = 1,760**。
- 单行：boundary ≤80、节选 ≤120、标题原样（无上限）。材料标题中位 **16 字**（§0.2），典型行远低于单行上限。

### 2.2 逐行裁剪算法（冻结；参照 `RoomTutorToolRunner.kt:789` 的 `BudgetedLines`）

```text
body = BudgetedLines(2_000 - 240)          // 1,760；header 也计入 body（与 :789-804 同形）
body.add(header)

for line in 节点行（召回顺序，不重排、不补位）:
    full = build(line)                     // boundary 80 / 节选 120 已先施加
    if body.usedChars + full.length + 1 <= body.budgetChars:
        body.add(full)
    else:
        skeleton = full 去掉 <bodyExcerpt> 后的前缀
        avail = body.budgetChars - body.usedChars - skeleton.length - 1
        if avail >= 1:
            excerpt = safeTruncateMarkdown(fullExcerpt, min(120, avail))
            body.add(skeleton + excerpt)
            trimmedCount += 1
        else:
            droppedCount += 1              // 整行不显示，但必须计数
if trimmedCount + droppedCount > 0:
    render 尾部追加：
    "\n已截断：${droppedCount} 项未显示，${trimmedCount} 行节选缩短。"
```

- **裁剪只动节选**：boundary 的 80、标题原样、marker/名称都不缩。优先保住“这个节点存在、它绑定的是哪份材料”。
- 缩减后的节选**再次走 `safeTruncateMarkdown`**，所以缩到更短上限时仍不会切坏公式/半句。
- **不允许静默丢弃**：每个节点要么显示，要么进入 `droppedCount`；footer 存在与否由 `trimmedCount + droppedCount` 决定。footer 字面量 19 字符（实算），240 的预留足够覆盖 `\n` + footer。
- 与 `:789` 的关系：沿用同一“按行累计 + 计数 + 尾部注明”的机制（`BudgetedLines` 在 `:877-900`），新增的只有“预算不足时先缩节选、缩不下才丢行”。新增消灭的具体失败 = “整行丢弃会让模型把被丢的知识点当成不存在”（审计 R1 的静默缺失同类）。
- 正常路径（§2.3）5 行全放得下 → **footer 不出现**；footer 只在真实裁剪时出现，避免“每批都写已截断”的噪声。

### 2.3 算术校验（本会话实算）

| 项 | 计算 | 结果 |
|---|---|---:|
| header | `RoomTutorToolRunner.kt:454` 字面量 | 43 |
| 最长节点名 | 3,280 节点 max | 24 |
| 最长材料标题 | 65,014 材料 max | 183 |
| 理论最长单行 | `4(marker+. ) + 24 + 1(：) + 80 + 4(｜材料《) + 183 + 1(》) + 120` | **417** |
| 5 行 + header + 5 换行 | `5×417 + 43 + 5` | **2,133 > 2,000** |
| 中位标题下的 5 行整条 | `5×(4+24+1+80+4+16+1+120) + 43 + 5` | **1,298 < 2,000** |

- 结论：裁剪门**结构上必需**——极端标题（max 183）下不裁剪必超 2,000；典型数据（中位标题 16）下 5 行仍宽裕。
- v1 金标渲染 max = **838** 字符（`retest-r1.log:17`，转引）。把每行 60 字摘要尾巴换成“标题 + 120 字节选”：中位标题 +77/行 → 算术估计 **1,223**；p99 标题 +123/行 → **1,453**；max 标题 +244/行 → **2,058**（超门）。这些是**算术估计，不是判据**；执行轮以实读覆盖。

---

## 3. 契约同步

### 3.1 `TutorToolDescriptions` 长/短描述（文本冻结）

| 版本 | 现文本（`TutorToolDescriptions.kt`） | 新文本（冻结） |
|---|---|---|
| 长（`:21-23`） | 读取这道题相关知识点讲解材料（返回**代号**+名称+边界+绑定材料摘要；代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms） | 读取这道题相关知识点讲解材料（返回**代号**+名称+边界（≤80字）+绑定材料《标题》与正文节选（≤120字，公式/句界安全截断）；无材料节点显式标注「（该节点无材料）」；代号本会话有效，可直接作为 MASTERY_UPDATE 的 terms） |
| 短（`:101`） | 读取当前题相关知识点讲解材料（返回代号+名称+边界+材料摘要） | 读取当前题相关知识点讲解材料（返回代号+名称+边界+材料《标题》与正文节选≤120字；无材料显式标注） |

- 长度变化（实算）：长 74 → **124**（+50）；短 31 → **51**（+20）。
- 两段共享前缀 `读取这道题相关知识点讲解材料` / `读取当前题相关知识点讲解材料` 不变 → `core/data/src/test/.../model/TutorToolPromptInjectionTest.kt:69` 的 `contains` 断言不受影响（该文件本批**不触碰**，pre-sha `3de30c32…`，执行轮必须跑绿）。

### 3.2 `TutorToolDescriptionsSnapshotTest`

- `TutorToolDescriptionsSnapshotTest.kt:30-43` 的**全文相等**判法不变（8 工具 × 长/短）。
- 只改 `KNOWLEDGE_READ` 两条期望文本：`:100-102`（长）、`:128-130`（短），逐字替换为 §3.1 的新文本。
- 其余 7 工具快照逐字节不动；`:45-72` 的 `GENERATE_FIGURE` 能力分支测试不动。

### 3.3 这是格式契约变更，不是放宽

- 判法仍是 `assertEquals(完整期望文本, 实际文本)`；**不得**改成 `contains`、不得截短期望文本、不得删除用例。
- 新契约比旧契约**更严**：旧文案没有 80/120 上限、没有标题承诺、没有无材料显式标注；新文案把三者都写死，并新增 F8/F9 两道渲染门。

### 3.4 KDoc 与其它副本

- `RoomTutorToolRunner.kt:373`、`:390`、`:470` 的 KDoc 同步改为“标题 + 正文节选”契约（与 §1 一致）。
- 仓库内 `读取这道题相关知识点讲解材料` / `读取当前题相关知识点讲解材料` 的其它出现只有上述两处描述、快照测试与 `TutorToolPromptInjectionTest.kt:69` 的前缀断言（`grep` 实跑），无第三份生产副本。
- `docs/ops/kb-observability-handoff.md:158` 的行号引用已滞后，但它是运维说明、不是契约副本，本批不触碰。

---

## 4. 判读

### 4.1 冻结基线（转引，执行轮复算）

| 项 | 基线 | 来源 |
|---|---:|---|
| 设备金标融合 Recall@5 | **117/129 = 0.9069767441860465** | `docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:65`；装置 `core/data/src/androidTest/.../knowledge/GoldenRetrievalInstrumentedTest.kt` |
| 设备金标 MRR（信息项） | 0.7322997416020672 | 同上 |
| 同路零漂移 | fused symdiff **0**、lexical symdiff **0** | `docs/research/2026-10-16-d3-round1-prereg.md:124` §6 |
| F1 每节点材料覆盖 | **100.00%（645/645）** | `build/agent-batch23/usage-r1/retest-r1.log:6` |
| F2 期望节点有材料 | **117/129 = 90.70%** | `retest-r1.log:8` |
| F3 误区槽位占比 | **8/645 = 1.24%** | `retest-r1.log:10-11` |
| v1 渲染（信息项） | min/median/max = **660 / 743 / 838** 字符 | `retest-r1.log:17` |

### 4.2 判据表（写死）

| 门 | 判据 | 通过条件 | 证据形态 |
|---|---|---|---|
| **B1 设备金标** | `GoldenRetrievalInstrumentedTest` 的 Recall@5 **不降** | ≥ **117/129**（MRR 只报告，不作门） | 设备 `am instrument` + 账本落盘 |
| **B2 零漂移** | device↔JVM 同路 MISS 集合对称差 | fused = **0** 且 lexical = **0** | 零漂移对拍脚本产出的两个集合 |
| **F1** | 每节点材料覆盖 | **恰等于 100.00%（645/645）** | 同一 129 查询镜像复算 |
| **F2** | 期望节点有材料 | **恰等于 117/129（90.70%）** | 同上 |
| **F3** | 误区槽位占比 | **恰等于 8/645（1.24%）** | 同上 |
| **F8** | 标题必现 + 节选 >60 | §4.3 的违反数 **= 0** | 129 条镜像渲染 + JVM 夹具 |
| **F9** | ≤2k 断言 + 裁剪行为 | §4.4 的违反数 **= 0** | 129 条镜像渲染 + 合成极值夹具 |

- F1/F2/F3 是**选择面**读数，本批只改渲染，因此必须“复算不变”；任一值变化都说明材料选择被意外触碰 → 该轮不判读。
- F1/F2/F3、F8、F9 的前置：镜像必须先复算出与 §4.1 完全相同的 F1/F2/F3，否则其 F8/F9 读数不判读（防止渲染脚本自带选择漂移）。

### 4.3 F8 定义（冻结）

在 129 条金标查询 × top-5 = **645 个节点行**上：

- **F8a（title 必现）**：分母 = 有绑定材料的节点行（基线 645）；分子 = 行内出现 `｜材料《` + 该材料 `title` 原文 + `》` 的行。要求 **分子 = 分母**，且 `title` 0 截断、0 改写。
- **F8b（节选 >60 字）**：分母 = 645 行中“flatten 后正文长度 > 60 **且**未被 §2.2 预算裁剪”的行；分子 = 节选长度 > 60 的行。要求 **分子 = 分母**（0 违反）。
- 报告格式：`F8a title_ok=__/__；F8b excerpt_gt60=__/__；违反明细（query, node, reason）`。
- 分母为 0 的情况不判 PASS：登记为 `NOT_JUDGED` 并说明夹具缺材料。

### 4.4 F9 定义（冻结）

- **F9a（≤2k）**：129 条渲染逐条 `summaryMarkdown.length ≤ 2_000`；报告 min/median/max，max 必须 ≤2,000。
- **F9b（裁剪行为）**：两个合成极值夹具，分别打中“缩节选”与“丢行”两条分支（算术见下）：
  - **b-1 缩节选**：5 节点 × boundary 80 × 标题 **120** × 正文 ≥2,000（正文前 61 字无 `。；`、无公式）→ 结果 ≤2,000；前 4 行全量、第 5 行节选缩到 **61** 字；footer 字面量 `已截断：0 项未显示，1 行节选缩短。`；`displayed=5、trimmed=1、dropped=0`。
  - **b-2 丢行**：5 节点 × boundary 80 × 标题 **183** × 正文 ≥2,000 → 结果 ≤2,000；前 4 行全量、第 5 行 skeleton 放不下 → `displayed=4、dropped=1、trimmed=0`；footer 字面量 `已截断：1 项未显示，0 行节选缩短。`；**5 = displayed + dropped（0 静默丢失）**。
  - 算术：body 预算 1,760、header 成本 44；b-1 单行 354（成本 355），4 行后余 296，skeleton 234 → `avail = 1760−1464−234−1 = 61`；b-2 单行 417（成本 418），4 行后余 44 < skeleton 297 → 丢行。
- **F9c（缩后仍安全）**：合成夹具让“缩减后的节选上限”落在 `$...$` 公式内 → 节选必须退到公式起点，不得出现半个公式（复用 `safeTruncateMarkdown` 语义）。
- **F9d（注与裁剪一一对应）**：footer 出现 **⟺** 该条渲染 `trimmedCount + droppedCount > 0`；未发生裁剪的条目 footer 出现次数 = **0**。129 条全量报告 footer 次数（极端标题导致真实裁剪时允许 >0，但必须与裁剪计数逐条一致；不得出现“没裁剪却写已截断”或“裁剪了却不写”）。
- 报告格式：`F9a max=__ chars；F9b displayed=__ dropped=__ trimmed=__ footer=__；F9c excerpt=__；F9d footers=__ / trimmed+dropped>0 的条数=__（必须相等）`。

### 4.5 决策规则（冻结）

1. **七门全绿 → adopted**；任何一门红 → **不采纳**，按 §5 回滚，并在 §7 登记红因。
2. **NOT_RUN 不是 PASS**：设备不可用、镜像不可复现、判官未跑，均记 `NOT_RUN` / `UNVERIFIED`，整批**不判读、不采纳**。
3. **阈值/分母/样本事后不改**：F1 100%、F2 117/129、F3 1.24%、F8/F9 零违反、B1 ≥117/129 全部冻结；不得因实测贴线而放宽或换样本。
4. **先归因再回滚**：门红时先 `git diff` §5.1 的 7 个文件；若红因可归到他线在飞改动，按环境阻塞登记，不回滚他线文件。
5. **不允许以“提高 2k 上限”换过门**：若 129 条金标在正常数据下就放不下 5 行，属于 §5.3 的结构性回滚条件，而不是调预算的理由。

---

## 5. 回滚

### 5.1 触碰清单 + pre-sha（写前实算，均 CLEAN）

| # | 路径 | pre-sha256 |
|---|---|---|
| 1 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | `f07a98e6e9527a97a652f78e6a23fe0ef5deee9de803077c25254221930c2888` |
| 2 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptions.kt` | `ff53c951f04ecdba9996d5ef7abb7a6aafed1fd35e9552e69464126d355b2c7e` |
| 3 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt` | `7e43d2f7ab8e17ba1fc81bd7abd320f0a77641e91f271b09ab88708258caab8c` |
| 4 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/SafeTruncationTest.kt` | `4d326c9094de132bbb6d706eb5abaee9e692c852881f4c34ee64fc673f2c65db` |
| 5 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/UsageMaterialAllocationTest.kt` | `1d515c5e4375df4202c4c53f1464bc173dcd8923b2a9296a05824c010f9b0d6b` |
| 6 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunnerTest.kt` | `057d38c5abaf63cb1579e1848de03ee23a42fe6ce6ff8d2ffd64181935661e9c` |
| 7 | `core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptionsSnapshotTest.kt` | `5dc7a5cc45edd34220f938bcfcdcf844db482acc2e47d87c7e06168cfe9b3db5` |

- **新增文件**（无 pre-sha；文档只追加不删除）：本文；执行轮记录（路径由执行轮定，必须是新文件）。
- **不触碰面**（写死）：DAO / port / store / entity（`KnowledgeTeachingMaterialDao.kt`、`KnowledgePort.kt:146`、`RoomKnowledgeBaseStore.kt:384-406`、`StudyDatabaseRecords.kt:243-258`）；25 个教学 sidecar 与主包；检索/稠密腿/`DenseFusion`/金标 `golden_queries_v2.json`；schema/manifest；`OpenAiModelProtocol.kt` / `OpenAiModelTaskAdapters.kt` 的广告消费点；`TutorToolPromptInjectionTest.kt`（只跑不改）。
- 为什么触碰 3–7：它们都在断言旧形态（`｜材料：`、`take(60)`、`summaryMarkdown`）。按新契约逐条改写为“标题 + 120 节选 + 2k 裁剪”，**不是**删断言或放宽。

### 5.2 回滚动作

```bash
git checkout -- \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptions.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeReadRecallBudgetTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/SafeTruncationTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/UsageMaterialAllocationTest.kt \
  core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunnerTest.kt \
  core/model/src/test/kotlin/com/tingyun/smartmistakebook/core/model/TutorToolDescriptionsSnapshotTest.kt
sha256sum <上述 7 个路径>   # 必须逐一等于 §5.1
```

- 无 schema、无数据迁移、无 manifest 变更 → 回滚是**纯文件级**；不触碰他线脏文件。
- 本预注册与执行记录属历史文书，回滚时不删除。

### 5.3 回滚触发（任一即触发）

1. §4.2 任一红门，且一轮归因后仍指向本批 7 个文件。
2. 实际描述文本与 §3.1 冻结文本不逐字相同（契约漂移）。
3. 129 条金标在**正常数据**下无法在 2,000 字符内放下 5 行（结构性放不下）——回滚，而不是抬预算。
4. F8/F9 连续两轮修不绿：停下并升级，不得弱化判据（不删断言、不换分母、不把 `contains` 当相等）。

---

## 附录 A：执行轮 §7 必报字段（只追加，不改变 §1–§5 判据）

1. 写前/写后 porcelain 与 HEAD；7 个文件的 `sha256`（写后）。
2. 命令与落盘：JVM 测试（`core:model` 与 `core:data` 的相关测试类）、设备 `am instrument`（`GoldenRetrievalInstrumentedTest`）、零漂移对拍、129 条渲染镜像。
3. B1（Recall@5、MRR）、B2（两路 symdiff）。
4. F1/F2/F3 复算值（必须与 §4.1 完全相同）。
5. F8：`title_ok/分母`、`excerpt_gt60/分母`、违反明细。
6. F9：`max chars`、`displayed/dropped/trimmed/footer` 计数、F9c 的公式回退结果、F9d 的 footer 次数。
7. 渲染 min/median/max（字符）与 UTF-8 字节（信息项）。
8. `git diff --stat` 与 7 个文件之外的零改动证明。
9. 未跑项一律写 `NOT_RUN` / `UNVERIFIED`，不得写成 PASS。
