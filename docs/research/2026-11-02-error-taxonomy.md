# R2 错因 taxonomy：判据、可观测信号、schema 落位与撤销语义

> 批次 40 / WS2 研究线（计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md:26-34`，items 9–16）。
> 取用日期：2026-11-02。只读研究：**零 Gradle、零设备、零 git 写**；本文不声称任何门"通过"。
>
> 证据等级约定：
> - **A**：同行评审论文 / 官方规范 / 可核的原始摘要；
> - **B**：会议或期刊论文、机构教学资源（可核原文）；
> - **C**：单一来源、仅摘要级、或未取到全文（按"待验证"处理，不得当定论）。
>
> 本文的阈值、常数、字段名均为**设计提案**，除注明外一律 `PENDING_CALIBRATION`。落库 DDL 以 KSP 导出的 `73.json` 为最终事实，本文 DDL 只是草案。

---

## 0. 结论摘要（可直接进台账的 8 条）

1. **四分类的判据采用"IRIS 三主类 + careless 旁类"，并把 MISIDENTIFICATION 收窄为"识别/转录层"**。IRIS 原文的三类是 factual / procedural / conceptual，careless 是"另一类"；本项目的 `MISIDENTIFICATION` 应落在 IRIS 的 factual（"misidentifies signs / digits"）语义上，而不是 Elbrink 的算法层同名项（后者归 PROCEDURAL）。依据：IRIS 案例页原文；Andriani et al. 2021 的 Elbrink 三型。
2. **旧 spec §7 的 `FORGOTTEN` 没有对应项，应退役到 FSRS 的 lapse / retrievability，不得硬塞进 CARELESS**。理由：遗忘是"曾经会、现在提取失败"，与"会但执行失误"是两种机制；现有 `learner_problem_memory_state.lapse_count` 已经在表达它，重复表达会让两个事实互相矛盾。依据：`docs/specs/mastery-scheduling-spec.md:247-248`、`core/database/.../entity/LearningEntities.kt:684`。
3. **错因不是一个稳定的潜变量，v1 默认只能产"候选"，不能自动落账**。两种 careless 操作化（contextual slip vs BKFC）在同一批数据上**互不对齐**，且与后测的相关方向相反（arXiv:2503.04737）；细粒度代数错因自动分类的最好准确率约 **80.7%**（加上学生所选动作后 85.9%），且原文明确"没有对学生每一步意图的完美知识，就不可能确定错因"（McNichols et al., AIED 2023）。
4. **现有数据面不足以可靠区分 CONCEPTUAL 与 PROCEDURAL**：`tutor_message` 只有 `role + body_markdown`，没有结构化"学生步骤"；`attempt_event` 只有选项/时长/提示/揭示，没有自由演算。v1 的 CONCEPTUAL/PROCEDURAL 判定必须依赖"学生自己说出来的规则"，否则只能 abstain。依据：`core/database/.../entity/TutorConversationEntities.kt:138-159`、`core/database/.../entity/LearningEntities.kt:283-360`。
5. **schema 73 推荐两张表（追加式 observation + 现行 binding），不是一张；表数是 91→93，不是 92**。一张表会把"从未生效的候选"与"生效过又被撤回的绑定"混在同一状态空间里，读侧漏一个谓词就把被拒候选当成现行标签——这正是既有 `problem_classification_candidate`（schema 65 落库）+ `problem_classification_binding` 分离所消灭过的失败。依据：`core/database/.../entity/ProblemEntities.kt:558,632`（绑定/候选分离）、`docs/research/2026-11-02-batch-40-baseline.md:93,101-104`。
6. **`attempt_event.error_type / error_type_confidence` 三列保持占位，不作权威存储**。它们无写入方（审计 R-09）、无历史、无来源、无撤销语义；把它们当权威会制造第二份真相。依据：`docs/audit-2026-09-12-kernel-readiness.md:1555-1560,1912`；`core/database/.../entity/LearningEntities.kt:355-359`。
7. **D-1 不能直接复用为错因门；应定义 `D-1c`（错因自动落账门）并让它自带下界证据**。D-1 的语义是 KC 语义绑定精确率 ≥0.95，不是错因分类精度；且 D-1 的现状在仓内两份记录里互相矛盾（"95.15% 点估计过门、无下界保证" vs "未通过、只有 89.4% 抽样"）。见 §5.2 与 §8。
8. **AUD-11 的撤销是"证据链"操作（VOID/REPLACE + 重放），错因撤回是"标签链"操作（supersede，无重放）**。把错因撤回实现成 `attempt_correction VOID` 会把一次真实答错从掌握证据里静默抹掉——这是本设计要消灭的头号失败。依据：`core/model/.../LearningState.kt:540-553,668-684`。

---

## 1. 分类判据

### 1.1 外部基线：IRIS 的三主类 + careless 旁类

IRIS Center（Vanderbilt Peabody）的 "Mathematics: Identifying and Addressing Student Errors" 案例页给出的定义（原文引用）：

- **Factual errors**："errors due to a lack of factual information (e.g., digit misidentification, counting errors)"。
- **Procedural errors**："errors due to the incorrect performance of steps in a mathematical process (e.g., not regrouping, decimal misplacement)"。
- **Conceptual errors**："errors due to misconceptions or a faulty understanding of the underlying principles and ideas connected to the mathematical problem (e.g., misunderstanding of place value, incorrectly applying rules to new problems)"。
- **Careless error**（原文明确是"另一种可能"）："The student fails to correctly solve a given mathematical problem despite having the necessary skills or knowledge. This might happen because the student is tired or distracted..."；并且 "Although teachers can note the occurrence of such errors, doing so will do nothing to identify a student's skill deficits."

IRIS 还给了两条对设计直接有用的操作口径：

1. **模式阈值**："Three to five errors on a particular type of problem constitute an error pattern (Howell, Fox, & Morehead, 1993; Radatz, 1979)."
2. **区分困难**："Because conceptual and procedural knowledge often overlap, it is difficult to distinguish conceptual errors from procedural errors (Rittle-Johnson, Siegler, & Alibali, 2001; Riccomini, 2014)."

以及四步流程（Step 1 收集 3–5 道同型题；Step 2 找模式；**Step 3 找原因："by examining the procedures they use to solve problems or by asking them to explain their logic"**；Step 4 对应教学）。Step 3 是本项目"讲题 trace 作为证据"的最直接外部依据，但它同时说明：**证据是"学生解释自己的逻辑"，不是模型的解释**。

来源：<https://iris.peabody.vanderbilt.edu/case-study/mathematics-identifying-and-addressing-student-errors/>（证据级 B；机构教学资源，非同行评审）。

### 1.2 本项目四类的判据（可执行定义）

| 类 | 稳定码 | 判据（必须同时满足） | 明确排除 |
|---|---|---|---|
| **CONCEPTUAL** | `E01` | 学生的做法/解释里出现一条**在一般情况下不成立的规则**（或对概念的错误等价），且该错误在**同 KC 的另一道题**上复现；或已确认前置知识缺口导致本题无法起步 | 只凭"答案错"推断；只凭模型解释推断；一次性的计算失误 |
| **PROCEDURAL** | `E02` | 学生调用的是**正确的一般规则/概念**，但在步骤执行上出错（顺序、进位、符号、通分、小数对齐、算法选择）；错误在**结构同型的题**上复现 | 规则本身错（→ CONCEPTUAL）；看错题面（→ MISIDENTIFICATION）；单次滑手（→ CARELESS） |
| **CARELESS** | `E03` | 学生**具备**该技能（历史独立正确或同 KC 正确证据），本次为**单次**失误；典型组合：用时显著低于个人基线 + 无提示 + 立即独立重做正确 + 不复现 | 重复出现的错误；有提示/揭示；长时挣扎；低掌握度下的错（此时无法归因） |
| **MISIDENTIFICATION** | `E04` | 学生的**方法与规则正确**，但与题面之间发生了**识别/转录层**的错配：看错符号、看错数字、漏看条件、看错所求；纠正读题后立即正确 | 选错算法/用错条件（→ PROCEDURAL 的 `PROC_ALGORITHM_SELECTION`）；"没认真读题"但无任何 trace 佐证（→ 不分类/abstain） |

**MISIDENTIFICATION 的同名冲突（必须写进 KDoc）**：Elbrink 理论里的 "Misidentification" 指"算法执行时找错点/比较错函数值"，是**算法层**错误；Andriani et al. 2021 把它列为 procedural error 的第一型。本项目的 `E04` 是**感知/转录层**，两者不是一回事。若不写清，后续标注者会把 Elbrink 的算法错标成 E04，再把 E02 的样本抽空。

来源：Andriani, Triyanto & Nurhasanah (2021), "Procedural error of XIIth grade high school students in solving algebra problems based on Elbrink's theory", IOP Conf. Ser. 1796 012048，摘要原文列出三型：`Misidentification / Misgeneralization / Repair Theory`（证据级 B，小样本质性研究）。

**为什么把 E04 收窄到"感知/转录层"**：若把"误解题意"也算 E04，它与 E01 的 `CONCEPT_RULE_INVALID` 就无法区分（两者都是"对题意的错误理解"）；Movshovitz-Hadar et al. 1987 的 `misinterpreted language` 类正是这种语义层错误。收窄后 E04 有独立的反证（纠正读题后立即正确），E01 保留"规则不成立"的判据，两者互斥。代价是：纯粹的"题意理解偏差"会落到 E01 或 abstain，而不是 E04——这是有意为之。

### 1.3 判定顺序（决策阶梯，先排除再归类）

对每一次"答错"事件，按顺序问；**任一步不满足就往下走，走到底就 abstain（不写 observation）**：

1. **Q1 有没有学生自己产生的 trace？**（学生消息里的解释/步骤、学生选的选项文本、学生重做时的过程）
   - 没有 → 只能考虑 E03 或 abstain；**禁止**凭结果推 E01/E02/E04。
2. **Q2 trace 里的一般规则是否成立？**
   - 不成立（且是学生主动表述的）→ **E01**。
3. **Q3 规则成立、但某一步执行错？**
   - 是 → **E02**（`detail_code` 记具体步骤）。
4. **Q4 方法与规则都对，只是把题面读/抄错？**
   - 是（且有纠正读题后正确的证据）→ **E04**。
5. **Q5 是否"会而失手"的单次模式？**（历史会 + 快 + 无提示 + 立即重做对 + 不复现）
   - 是 → **E03**；否则 → **abstain**。

这个阶梯的三个性质是有意的：

- **E01/E02 必须有 trace**。这是对 McNichols et al. "没有意图知识就无法确定错因"的直接落地，也是防止"答案错 → 模型编一个错因"的闸门。
- **E03 是唯一可以只靠行为面判的类**，但也只在"会"的前提下成立（BKT 的 slip 定义：`P(incorrect | skill known)`）。
- **abstain 是一等公民**。没有 observation 行不是失败，是诚实。

### 1.4 旧枚举的映射与退役

`docs/specs/mastery-scheduling-spec.md:247` 曾写：`CONCEPT_MISCONCEPTION / PROCEDURAL_SLIP / MISREAD / FORGOTTEN`。它与本批计划的四类不是一对一：

| 旧值 | 新值 | 处置 |
|---|---|---|
| `CONCEPT_MISCONCEPTION` | `E01 CONCEPTUAL` | 直接映射 |
| `PROCEDURAL_SLIP` | `E02 PROCEDURAL` | 直接映射；注意旧名的 "slip" 与 careless 的 slip 不是同一概念，落库时不得混用 |
| `MISREAD` | `E04 MISIDENTIFICATION` | **收窄**：仅"读/抄错"；若是"忽略条件/用错条件"，归 `E02` 的 `PROC_ALGORITHM_SELECTION` 或 `E01` |
| `FORGOTTEN` | **无** | **退役**。遗忘由 FSRS 的 lapse/retrievability 表达；不进入错因层。若未来要恢复，必须以"教学响应不同"为证据新增 `E05`，不能复用旧码 |
| （旧 spec 无） | `E03 CARELESS` | 新增。旧 spec 没有它，是这次补上的旁类 |

**为什么 FORGOTTEN 必须退役（失败指认）**：若保留，同一事实会有两个表示（`lapse_count` 与 `cause=FORGOTTEN`），而两者由不同链路更新（投影 vs 标注），必然出现"lapse_count=3 但错因说不是遗忘"的矛盾；教学侧还会对"遗忘"做重教，而 FSRS 的正确答案是"按间隔复习"，两条处置互相打架。

### 1.5 与产品冻结合同的硬边界

产品合同三处明确：错因**不得**成为可见分类、筛选、分组、卡片标签或必填字段：

- `docs/product-information-architecture.md:287`："错因、来源、题型可以留作内部来源证据或教学候选，但不得成为错题本的可见分类、筛选、分组、卡片标签或必填字段。"
- 同文件 `:408`："错因、来源、题型不显示为标签或筛选项。"
- `docs/m1-exhaustive-product-contract.md:95,259` 同口径。
- `docs/model-first-product-boundaries.md:9`："错因、来源和题型不作为可见分类。"

因此本设计有三条不可越的线：

1. 新表是**内部标签账本**，不建面向用户的 facet/索引，不持久化 `display_name`（展示名只在 UI 映射层）。
2. 详情页现有的"错因 / 备注"自由文本（`feature/library/.../MistakeDetailRoute.kt:692`；裁定见 `docs/agent-first-refactor-decisions-2026-09-23.md:464`）**仍是备注**，不是本 taxonomy 的输入；不得解析、不得当标注、不得出网（"备注原文不出网"见 `docs/REFACTOR-MASTER-LINE.md:191`；`user_note` "转录不落原文"见 `docs/agent-first-refactor-decisions-2026-09-23.md:2680-2690`）。
3. 若产品要提供"这不是我的错因"的纠正入口，它是**内部纠错动作**（写入 observation 的 `decision_source=USER`），不是"错因分类浏览页"。这一点需要产品侧确认，见 §8。

---

## 2. 文献证据（逐条可核）

| # | 来源 | 支撑的结论 | 关键原文/数字 | 级 | 限制 |
|---|---|---|---|---|---|
| S1 | IRIS Center, "Mathematics: Identifying and Addressing Student Errors"（Vanderbilt Peabody）<https://iris.peabody.vanderbilt.edu/case-study/mathematics-identifying-and-addressing-student-errors/> | 三主类 + careless 旁类的定义；四步错误分析；3–5 次成模式；概念/程序难分 | 见 §1.1 引文 | B | 机构教学资源，不是同行评审；引用的是其综述的二手结论 |
| S2 | Radatz, H. (1979). Error Analysis in Mathematics Education. *JRME* 10(3), 163–172. DOI `10.5951/jresematheduc.10.3.0163` | 错因可按信息加工机制分类；其中 "failure of cognitive control" 被**本文映射**为 careless 的上位机制（不是 Radatz 的原词） | "…by incorrect associations or failure of cognitive control, and by the application of irrelevant strategies or rules." | A | 1979 年的综述；分类是因果机制，不是可观测信号 |
| S3 | Movshovitz-Hadar, Zaslavsky & Inbar (1987). An Empirical Classification Model for Errors in High School Mathematics. *JRME* 18(1), 3–14. DOI `10.5951/jresematheduc.18.1.0003` | 六类内容导向分类，且**互斥且穷尽**经过信度检验 | "six error categories: misused data, misinterpreted language, logically invalid inference, distorted theorem or definition, unverifed solution, and technical error… categories are inclusive and mutually exclusive." | A | 分类轴是"内容/解题动作"，与本项目的"心理机制"轴不同；不能直接当四类用，但证明"粗粒度互斥分类"可行 |
| S4 | Andriani, Triyanto & Nurhasanah (2021). Procedural error … based on Elbrink's theory. *IOP Conf. Ser.* 1796 012048. DOI `10.1088/1742-6596/1796/1/012048` | Elbrink 的 procedural 三型：Misidentification / Misgeneralization / Repair Theory | 摘要原文三型 | B | 小样本质性；"Misidentification" 是算法层，与本项目 E04 同名异义 |
| S5 | Mathaba, Bayaga, Tîrnovan & Bossé (2024). Error Analysis in Algebra Learning. *Journal on Mathematics Education*（ERIC EJ1428049） | 代数错因的常见类：conjoining / cancellation / problem-solving；概念理解与程序执行双缺口 | 摘要原文 | B | 学段/课程不同；只提供"类目存在性"，不提供阈值 |
| S6 | arXiv:2412.03765, "A Benchmark for Math Misconceptions: Bridging Gaps in Middle School Algebra with AI-Supported Instruction" | 代数误解基准：55 个误解、220 个诊断例；LLM 的最好 precision/recall 83.9% | "…reaches 83.9% when including educator feedback and restricting it by topic." | B（预印本） | **83.9% 不是"准确率"**，是 precision/recall 的一个组合读数，且依赖教育者反馈 + 按主题限制；不能当作无条件的 84% |
| S7 | McNichols, Zhang & Lan (2023). Algebra Error Classification with Large Language Models. AIED 2023. <https://people.umass.edu/~andrewlan/papers/23aied-error.pdf> | 细粒度代数错因分类的实测上限；类别重叠导致不可判定 | BERT **80.68 ± 1.1**；BERT+Action **85.90 ± 1.86**（24/19 个细标签）；"Without a perfect knowledge of student intent for each step of a response, it is impossible to have certainty about the true cause of student error." | A/B（同行评审会议） | 数据集是 Cognitive Tutor 的 95 名学生、5,744 个 BUG 步骤；不是本项目的题型分布 |
| S8 | Corbett & Anderson (1995). Knowledge tracing: Modeling the acquisition of procedural knowledge. *UMUAI* 5, 253–278. DOI `10.1007/bf01099821` | slip 的标准定义：已知会的情况下仍答错的条件概率；与 guess 对称 | 标准 BKT 四参数 | A | 单技能假设；多技能题上 contextual slip 失效（见 S9） |
| S9 | arXiv:2503.04737, "Carelessness Detection using Performance Factor Analysis"（2025） | careless 的两种操作化**互不对齐**；与后测的相关方向相反 | "careless errors identified by these two approaches did not align"；contextual slip 与后测正相关、BKFC 与后测负相关；"a broader challenge in operationalizing carelessness" | B（预印本） | 初中小数游戏数据；但结论是"构念不稳定"，方向明确 |
| S10 | Chi, Bassok, Lewis, Reimann & Glaser (1989). Self-explanations… *Cognitive Science* 13(2), 145–182. DOI `10.1207/s15516709cog1302_1` | 学生自我解释是理解深度的可观察指标；支持"讲题 trace 作为证据" | 自我解释与理解/迁移相关 | A | 不是"错因分类"研究；只支持信号效度，不支持阈值 |
| S11 | Rittle-Johnson & Siegler, "The relation between conceptual and procedural knowledge in learning mathematics: A review"（DOI `10.4324/9781315784755-6`） | 概念与程序知识的关系是迭代、重叠的（综述主题；IRIS S1 亦转引其 2001 版本） | 只引标题级结论，未读全文 | A（标题级） | 只支持"两类难分"，不提供判据 |
| S12 | Geifman & El-Yaniv (2017). Selective Classification for Deep Neural Networks. arXiv:1705.08500 | "拒绝选项"：用覆盖率换风险，可设定期望风险 | "allows a user to set a desired risk level. At test time, the classifier rejects instances as needed…" | A（NeurIPS） | 面向 DNN；本项目借用的是"门 + abstain"的结构，不是其具体算法 |

### 2.1 这批文献共同指向的三件事

1. **粗粒度、互斥、可审计的分类是可行的**（S3 的信度检验；S1 的三类），但**细粒度自动分类不可靠**（S6/S7 的上限与类别重叠）。
2. **"错因"是推断，不是观测**（S7 原文）。因此系统应输出"候选 + 置信 + 证据引用"，而不是"事实"。
3. **careless 最不稳定**（S9）。它适合做"抑制重教"的短时信号，不适合做持久标签。

---

## 3. 每类可观测信号

### 3.1 现有数据面（本地可读，逐列可核）

| 事实 | 位置 | 能支持什么 | 不能支持什么 |
|---|---|---|---|
| `attempt_event.duration_seconds` | `core/database/.../entity/LearningEntities.kt:341` | 快/慢于个人基线 | 单独不能判 careless（快也可能不会） |
| `attempt_event.hint_count` / `revealed_before_answer` | 同文件 `:350,352` | 是否被提示/看答案；排除"独立正确" | 提示的内容与时机 |
| `attempt_event.submitted_choice_markdown` | 同文件 `:327` | 学生选了什么（可用于 E04 的符号/数字错配比对） | 学生的推导过程 |
| `attempt_event.evidence_direction` / `evidence_weight` | 同文件 `:331` | 这次作答对记忆的方向与权重 | 错因 |
| `attempt_event.low_confidence_correct` | 同文件 `:359` | "蒙对"的低置信正确 | 错因 |
| `review_log.duration_ms` / `scroll_up_count` / `edit_count` / `interruption_count` / `away_millis` | `core/database/.../StudyDatabaseRecords.kt:1318-1321` | 犹豫、中断、离开（careless 的环境线索） | 语义原因 |
| `review_log.source_kind`（`ATTEMPT/SELF_REPORT/VISUAL/MODEL_JUDGED/LOCAL_CHECKED/REVEAL`） | `core/data/.../study/ReviewLogSink.kt:351-380` | 证据来源分级；`LOCAL_CHECKED` 最强（判定噪声≈0） | — |
| `learner_problem_memory_state.stability_days / difficulty / independent_correct_count / assisted_correct_count / lapse_count` | `core/database/.../entity/LearningEntities.kt:673-684` | "学生是否会"的先验（E03 的前提） | 单题层面的原因 |
| `independent_correct_observation` | 同文件 `:778,793-816` | 跨日独立正确（重做证据） | 过程 |
| `tutor_message.role / body_markdown / bound_problem_id / bound_problem_revision_id` | `core/database/.../entity/TutorConversationEntities.kt:138-159` | 学生/助手轮次文本；可做语义提取 | **没有结构化步骤**；模型文本不是学生证据 |
| `tutor_message.tool_trace_json` | `core/model/.../TutorToolTrace.kt` | 工具名/条数/结果码 | **不含语义 rationale**（KDoc 明确"只进 UI，不进模型"） |
| 讲题结算 | `core/data/.../study/TutorJudgedReviewSettler.kt:34-66` | 本地核对题作答 > 模型判词；判词须锚定本题 KC | 错因；它只产对/错与证据 |

**关键缺口（必须如实写进实现 KDoc）**：当前没有任何"学生自由演算步骤"的结构化存储。讲题 trace 是自然语言，且助手侧是模型生成。因此 E01/E02 的自动判定本质上是一次**语义分类**，必须走模型任务边界、受 D-1c 门约束，不能假装是本地确定性规则。

### 3.2 信号矩阵

记号：**S**=强（可单独支撑候选），**M**=中（需与另一条合并），**W**=弱（只能降/升置信，不能单独定类）；**反**=反证（出现则压低或撤回）。

| 类 | 信号（本地可核） | 来源 | 向 | 强度 | 时效 | 反证 |
|---|---|---|---|---|---|---|
| **E01 CONCEPTUAL** | 同 KC、**不同题面**的独立作答再次错（≥2 次） | `attempt_event` + `assessment_evidence_snapshot.attributions` | 支持 | S | 跨日/跨题 | 同题立即重做对；近迁移题对 |
| | 学生在讲题里说出**一般规则**，该规则可被判为不成立 | `tutor_message`（role=student） | 支持 | S | 当轮 | 学生只是复述助手的话（role 归属错） |
| | 该**题**（practice unit）历史 `independent_correct_count=0` 且 `assisted_correct_count>0` | `learner_problem_memory_state` | 支持 | M | 慢 | 首次学习、样本不足 |
| | 前置 KC 掌握度低（prerequisite gap） | 知识图谱 + 掌握投影 | 支持（限定为 `CONCEPT_PREREQUISITE_GAP`） | M | 慢 | 前置已修复 |
| **E02 PROCEDURAL** | 同一**步骤签名**在结构同型题上重复（如都错在进位/符号/通分） | `attempt_event` + 题面结构（需题目标注/模型） | 支持 | S | 跨题 | 只有一次；换结构后对 |
| | 概念题（若有）答对，但计算步骤错 | `review_log(LOCAL_CHECKED)` + 题目类型 | 支持 | M | 当次 | 概念题也错（→ E01） |
| | 学生解释里规则正确、执行描述错 | `tutor_message` | 支持 | S | 当轮 | 解释本身也错（→ E01） |
| | 无提示、用时正常或偏长 | `attempt_event` | 支持（排除 careless） | W | 当次 | 极快（→ E03 候选） |
| **E03 CARELESS** | 用时显著低于**个人基线**（建议 p10 以下；基线需 ≥30 次作答） | `attempt_event.duration_seconds`；门槛依据 `docs/specs/mastery-scheduling-spec.md:290` | 支持 | M | 当次 | 用时正常/偏长 |
| | 无提示、无揭示 | `attempt_event.hint_count/revealed_before_answer` | 支持 | M | 当次 | 有提示（不能算"会而失手"） |
| | **立即独立重做正确**（同题或同结构） | 后续 `attempt_event` / `independent_correct_observation` | 支持 | S | 分钟–天 | 重做仍错（→ E01/E02） |
| | 该题历史独立正确 ≥1，且 `lapse_count` 低 | `learner_problem_memory_state` | 支持 | M | 慢 | 首次学习 |
| | 中断/离开/滚动异常 | `review_log.interruption_count/away_millis/scroll_up_count` | 支持（环境） | W | 当次 | 无异常 |
| **E04 MISIDENTIFICATION** | 学生的选项与正确答案只差**符号/数字/条件**的读取错配，且方法正确 | `attempt_event.submitted_choice_markdown` + 答案键 | 支持 | M | 当次 | 选项差异不是读取型 |
| | 纠正读题后立即正确 | 后续 attempt | 支持 | S | 分钟 | 纠正后仍错 |
| | 学生明说"我看错成…" | `tutor_message` | 支持 | S | 当轮 | 只是事后合理化（需与重做一致） |
| | 题面在作答后被修订（源资产/修订链） | `problem_revision_source_asset` / 修订事件 | **反证/失效**：错可能不在学生 | — | — | — |

### 3.3 聚合与晋级规则

单个信号不足以写 binding。建议的晋级条件（`PENDING_CALIBRATION`）：

| 类 | 生成 observation 的最低条件 | 晋级 binding 的最低条件 |
|---|---|---|
| E01 | 1 条 trace 证据 **或** 1 次同 KC 负迁移 | 2 条独立负事件（不同题面/不同日）**或** 1 条 trace + 1 次负迁移 |
| E02 | 1 条 trace 证据 **或** 2 次同步骤签名 | 2 次同步骤签名（可跨题）**或** trace + 1 次复现 |
| E03 | 快 + 无提示 + 立即重做对（三者齐） | **不晋级**（见下） |
| E04 | 1 条读取型错配 + 纠正后对 **或** 学生明说 | 学生明说 + 纠正后对 |

**CARELESS 不由系统晋级为 binding**，只保留为短时效 observation（建议 TTL 14 天，`PENDING_CALIBRATION`）。唯一例外是用户**显式确认**（`acceptance_source=USER_CONFIRMED`），且该 binding 必须带 90 天复核期，不作为长期属性。理由：S9 表明它的两种操作化互不对齐，且与学习结果的相关方向相反；把 careless 写成持久标签会把"一次手滑"变成"这个学生的属性"，并可能抑制本该发生的重教。

### 3.4 冷启动与个人基线

`docs/specs/mastery-scheduling-spec.md:290` 已经裁定：交互犹豫信号（RT 分位/修改次数/scroll-up）需要"个人基线 ≥30 次作答"，冷启动期一律中性权重。E03 的"快"必须遵守同一条：**基线不足 30 次时，E03 只能由"立即重做对 + 无提示 + 历史会"三条支撑，不得用 RT 阈值**。

### 3.5 明确不可用的信号

- `tutor_message.tool_trace_json`：只有工具名/条数/结果码，没有语义（`core/model/.../TutorToolTrace.kt` KDoc 明说"只进 UI，不进模型"）。拿它推断错因是过度解读。
- `tutor_message.thinking_markdown`：模型思考轨迹，旧行/失败行为 NULL；它不是学生证据，且"不回喂模型"（`TutorConversationEntities.kt:148-150`）。
- 详情页 `user_note`：备注原文不出网；不解析、不分类、不进 observation 的 `rationale_markdown`。
- 模型的自然语言解释本身：它是"教学假设"，不是"学生错因"；只能作为 trace 的上下文，不能单独定类。

### 3.6 三条通道的分工与限制（讲题 trace / 自评 / 重做）

| 通道 | 能支撑 | 不能支撑 | 权重与使用规则 |
|---|---|---|---|
| **讲题 trace** | E01/E02/E04 的**正向**证据（学生自己说出的规则、步骤、读题）；E03 的环境线索（中断、卡点） | 不能只靠助手侧文本；不能把模型的解释当学生证据；不能从 `tool_trace_json` 取语义 | 最高，但**只认学生 role 的轮次**；助手轮次只作上下文。当前无结构化步骤，必须走模型提案 + 引用原始 `message_id` |
| **自评** | "我以为我会"这类**元认知**先验；与结果不一致时可提示 E01（高信心 + 错） | 不能单独定类；学生的因果归因本身不可靠；可能被"少做/逃避重教"激励污染 | 最低，只调置信，不单独立类。注意：现行实现（`TutorJudgedReviewSettler` KDoc："复习作答不再由学生自评"）已把复习作答改为本地核对/模型判词，`review_log.source_kind=SELF_REPORT` 只剩旧行/其他通道 |
| **重做** | E03 的**强**证据（立即独立重做对）；E01/E02 的复现证据（不同题面仍错） | 同题重做会被"记住答案"污染，不能证明概念已会 | 中–高。E03 用同题/近似题 + 立即；E01/E02 必须用**不同题面**（同 KC 或同步骤签名），否则不算复现 |

**聚合优先级**：`讲题 trace（学生侧） > 重做（跨题） > 自评`。任何单通道都不足以把 E01/E02 晋级为 binding；E03 可以只靠"重做 + 行为面"，但按 §3.3 不晋级。

**对"自评"的一句硬话**：自评不是错因的测量，是学生对自己状态的**报告**。把它当测量，会把"学生不想重教"误读成"学生概念没问题"。

---

## 4. 与既有 schema（71/72）的落位设计

### 4.1 现状事实（逐项核对）

| 事实 | 值 | 证据 |
|---|---|---|
| DB schema | **72** | `core/database/.../StudyDatabase.kt:140` `STUDY_DATABASE_VERSION = 72` |
| 表/视图 | **91 表 / 2 视图** | `72.json`；`core/data/.../backup/SmbkSelectionPlan.kt:548-549` |
| 备份类别 | 类别 74（ISSUE_BOOK 31 + MASTERY 35 + SESSIONS 7 + IMAGES 1）+ ALWAYS 7 + DERIVED 3 + SPLIT 7 = 91；`trimOrder` 83 | `SmbkSelectionPlan.kt:550-561` |
| 备份 identity | `IDENTITY_HASH = aa5e8e8e6536e0c1f3adcda6867814cb`（必须等于 `72.json`） | `SmbkSelectionPlan.kt:51` |
| 备份规则版本 | `CLOSURE_VERSION = 4`，digest 前缀 `smbk-selection-v4` | 同文件 `:54,57` |
| 错因占位列 | `attempt_event.error_type` / `error_type_confidence` / `low_confidence_correct` | `LearningEntities.kt:355-359`；迁移 `MasterySchedulingMigration.kt:44-48` |
| 占位列的写入方 | **无**（R-09：既无写入也无读取；唯一构造点只写 hint/reveal） | `docs/audit-2026-09-12-kernel-readiness.md:1555-1560,1912` |
| 已有候选/绑定先例 | `problem_classification_candidate` + `problem_classification_binding`（含 `accepted`/`acceptance_source`/`confidence`/`accepted_confidence`） | `core/database/.../entity/ProblemEntities.kt:558,632`；候选集迁移 `ProblemClassificationCandidateMigration_64_65.kt` |
| 已有"可空唯一活键"先例 | `review_session.active_session_key TEXT NULL` + `Index(unique=true)` | `core/database/.../entity/ReviewEntities.kt:213,223-224` |
| 已有追加式更正先例 | `attempt_correction`（VOID/REPLACE，last-write-wins，不删除） | `LearningState.kt:540-553,668-684` |

### 4.2 方案对比

| 方案 | 形态 | 消灭的失败 | 代价/问题 | 结论 |
|---|---|---|---|---|
| A. 复用 `attempt_event.error_type*` | 在现有占位列上写 | 零新表、零迁移 | 只能表达"一次作答一个值"；无候选/拒绝/撤回历史；无来源/渠道；与 `Attempt` 域模型不一致（模型无字段）；两处真相 | **否** |
| B. 复用 `llm_teaching_advisory` | 把错因写成 advisory | 复用现有表 | advisory 是自由文本 + kind，无状态机、无撤销、无 per-attempt 关联、无候选集；它回答"教什么"，不回答"错在哪" | **否**（advisory 保留为教学假设面） |
| C. 一张表（candidate + binding 合体） | 单表 + `accepted` 标志 + 状态 | 少一张表；表数 91→92，符合计划隐含的"92" | 读侧必须靠谓词过滤；漏一个谓词就把被拒候选当现行标签；撤回与拒绝共享状态空间；审计要扫全表 | **否**（除非硬要凑 92） |
| D. 两张表（observation 追加 + binding 现行） | 与 schema 65 同构 | 候选/拒绝/撤回/替换各有明确行；读侧只有一个来源；审计天然完整 | 表数 91→93；备份/闭包/identity 需同步 | **推荐** |

**对计划数字的更正**：计划 item 12 写"91→N 表"、基线文档说"92 是 73 落地后的预期值"。那是按**一张表**算的。按本批开工时的 72 基线，推荐方案 D 落地后是 **93 表**。

**2026-11-02 复核轮裁定（rebase）**：KB 32B 已按「L2 不可判→回滚」删除 `knowledge_search_material_feature` 与 71→72 迁移，73 改为 **rebase 到 71**：90 + 2 = **92 表**。因此 `SmbkSelectionPlan` 的 `require`、`SmbkSelectionPlanTest`、`rowCountKeys`、`trimOrder`（84 项）、entity/FK（92/110）均按 **92** 落地；本文上面的 93 只是当时 72 基线的推算，不再是当前口径。

### 4.3 枚举与稳定码

```kotlin
// core/model/src/main/kotlin/.../ErrorCause.kt
enum class ErrorCause(val code: String) {
    CONCEPTUAL("E01"),
    PROCEDURAL("E02"),
    CARELESS("E03"),
    MISIDENTIFICATION("E04"),
    ;

    companion object {
        const val TAXONOMY_VERSION: String = "error-cause-v1"
        fun fromCode(code: String): ErrorCause? = entries.firstOrNull { it.code == code }
    }
}
```

规则：

- `code` 是**稳定码**，一旦发布永不复用、永不重编号；展示名只在 UI 层映射（不落库，避免"改文案=改语义"）。
- `E05` 预留给 `FACTUAL`（IRIS 的 factual 类），**v1 不启用**。启用条件：E01 的 `detail_code` 中 factual 子类占比 ≥20% 且教学响应与概念错不同（需实测）。
- `detail_code` 是受控词表、可空、按 `taxonomy_version` 解释。它保留 IRIS factual 与 Elbrink procedural 的细粒度，不扩张顶层枚举。

| 顶层 | `detail_code` 建议值 |
|---|---|
| E01 | `CONCEPT_RULE_INVALID`, `CONCEPT_OVERGENERALIZED`, `CONCEPT_PREREQUISITE_GAP`, `FACTUAL_BASIC_FACT`, `FACTUAL_VOCABULARY`, `FACTUAL_FORMULA` |
| E02 | `PROC_STEP_ORDER`, `PROC_REGROUPING`, `PROC_SIGN`, `PROC_DECIMAL_ALIGN`, `PROC_FRACTION_RULE`, `PROC_ALGORITHM_SELECTION`（Elbrink 的 Misidentification 落这里） |
| E03 | `SLIP_FAST_WRONG`, `SLIP_INTERRUPTED`, `SLIP_TRANSCRIPTION` |
| E04 | `READ_SIGN`, `READ_DIGIT`, `READ_CONDITION`, `READ_TARGET` |

### 4.4 两表字段草案

> 命名与类型对齐 Room/SQLite 现有风格；最终以 KSP 的 `73.json` 为准。所有 FK 的 `onDelete` 均 `RESTRICT`（错因行不能被父行静默带走）。

#### 表 1：`error_cause_observation`（追加式，永不删除）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `observation_id` | TEXT PK | 否 | 内容寻址：`sha256(learner, unit, revision, node, attempt, channel, extractor_version, evidence_refs)`；同内容重放幂等 |
| `learner_id` | TEXT | 否 | |
| `practice_unit_id` | TEXT | 否 | FK → `practice_unit` |
| `problem_revision_id` | TEXT | 否 | FK → `problem_revision`；错因绑定精确修订 |
| `knowledge_node_id` | TEXT | 是 | FK → `knowledge_node`；NULL=题级；v1 默认 NULL，仅 `certainty=DIRECT` 时填 |
| `attempt_id` | TEXT | 是 | FK → `attempt_event`；E03/E04 通常有；纯 trace 候选可为 NULL |
| `cause` | TEXT | 否 | `E01`–`E04` |
| `detail_code` | TEXT | 是 | 受控词表 |
| `confidence` | REAL | 否 | 0..1；写入方必须给，不允许默认 0.5 |
| `channel` | TEXT | 否 | `LOCAL_RULE` / `TUTOR_TRACE` / `SELF_REPORT` / `REDO` / `MODEL` |
| `extractor_version` | TEXT | 否 | 规则集/提示词/模型版本的联合标识；换版必须换值 |
| `taxonomy_version` | TEXT | 否 | `error-cause-v1` |
| `rationale_markdown` | TEXT | 否 | **本地**理由；不得含 `user_note` 原文；不保证可出网 |
| `evidence_refs_json` | TEXT | 否 | 类型化引用数组：`{kind:"attempt",id:...}` / `{kind:"tutor_message",id:...}` / `{kind:"review_log",id:...}`；**不建 FK 到 `tutor_message`**（会话级 CASCADE 会连带删/阻断开审计行） |
| `observed_at_epoch_millis` | INTEGER | 否 | |
| `status` | TEXT | 否 | `PROPOSED` / `ACCEPTED` / `REJECTED` / `RETRACTED` / `SUPERSEDED` |
| `decided_at_epoch_millis` | INTEGER | 是 | |
| `decision_source` | TEXT | 是 | `AUTO_GATE` / `USER` / `SYSTEM`（答案键纠正触发的失效） |
| `supersedes_observation_id` | TEXT | 是 | 自 FK；撤回/替换链 |

索引：

- `(learner_id, practice_unit_id, observed_at_epoch_millis)`：读某题历史；
- `(attempt_id)`：按作答找回；
- `(practice_unit_id, cause)`：聚合/审计。

#### 表 2：`error_cause_binding`（现行标签；撤销/替换写状态，不删行）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `binding_id` | TEXT PK | 否 | |
| `learner_id` | TEXT | 否 | |
| `practice_unit_id` | TEXT | 否 | FK → `practice_unit` |
| `problem_revision_id` | TEXT | 否 | FK → `problem_revision` |
| `knowledge_node_id` | TEXT | 是 | FK → `knowledge_node` |
| `cause` | TEXT | 否 | `E01`–`E04` |
| `detail_code` | TEXT | 是 | |
| `confidence` | REAL | 否 | 复制自 source observation（不重算） |
| `acceptance_source` | TEXT | 否 | `AUTO_GATE` / `USER_CONFIRMED` / `USER_CORRECTED` |
| `gate_version` | TEXT | 是 | 自动落账时必填；用户确认时 NULL |
| `source_observation_id` | TEXT | 否 | FK → `error_cause_observation`；必须 `status=ACCEPTED` |
| `accepted_at_epoch_millis` | INTEGER | 否 | |
| `state` | TEXT | 否 | `ACTIVE` / `VOIDED` / `SUPERSEDED` |
| `active_key` | TEXT | 是 | **可空唯一活键**：`learner\u001funit\u001frevision\u001fnode`（NULL=已结束）。照 `review_session.active_session_key` 的先例 |
| `superseded_by_binding_id` | TEXT | 是 | 自 FK |
| `ended_at_epoch_millis` | INTEGER | 是 | |

索引：

- `UNIQUE(active_key)`：同一 (learner, unit, revision, node) 最多一条 ACTIVE；
- `(learner_id, practice_unit_id, state)`：读现行/历史；
- `(problem_revision_id)`：按修订清理/审计；
- `(source_observation_id)`：追溯。

**为什么用 `active_key` 而不是部分唯一索引**：SQLite 支持 `WHERE state='ACTIVE'` 的部分唯一索引，但 Room 的 `@Index` 无法声明谓词；迁移里建了、实体里没有，Room 的 schema 校验会把它当漂移。`active_key` 是仓内已验证的等价做法（`ReviewEntities.kt:213`）。

### 4.5 关联图

```
attempt_event ───────────────┐
tutor_message (软引用, JSON) ─┤
review_log / redo attempt ───┤
                             ▼
              error_cause_observation   (追加；PROPOSED/ACCEPTED/REJECTED/RETRACTED)
                             │ source_observation_id (FK)
                             ▼
              error_cause_binding       (现行；active_key UNIQUE)
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
   教学材料选择 / advisory（内部）      审计 / 备份（只读）
              │
              ✗ 不写 attempt_event / review_log / learner_*_state
```

### 4.6 迁移 72→73 与牵动面（逐项）

**代码层**

1. `core/model/.../ErrorCause.kt`：枚举 + 稳定码 + `TAXONOMY_VERSION`（新文件，他线不占）。
2. `core/model/.../ErrorCauseEvidence.kt`：证据模型（channel / confidence / refs / observedAt），供端口与序列化。
3. `core/database/.../entity/ErrorCauseEntities.kt`：两个 `@Entity`（新文件）。
4. `core/database/.../ErrorCauseMigration_72_73.kt`：`Migration(72, 73)`，只建表 + 索引，不回填（无历史数据）。
5. `StudyDatabase.kt:140`：`STUDY_DATABASE_VERSION = 73`；`:149` 的 `entities` 加两项；`:415` 与 `openInMemory` 的迁移列表各加一条（**两处都要**，否则内存库与真库行为分叉）。
6. `core/database/.../dao/ErrorCauseDao.kt` + `port/ErrorCausePort.kt`：读/写/撤回；所有 SQL 参数绑定，无拼接。
7. `core/data/.../study/ErrorCauseExtractor.kt`：候选提取（确定性特征 + 可选模型提案）。
8. `core/data/.../study/ErrorCauseGate.kt`：`D-1c` 门（§5），默认 `enabled=false`。
9. `core/data/.../study/ErrorCauseRetractor.kt`：撤回/替换事务（§6）。

**构建/产物层**

10. KSP 生成 `core/database/schemas/.../73.json`；**不得手写**（先例：`VisualSnapshotIdentityMigration_70_71.kt:34` 明确 identityHash 只能由带 KSP 的构建产出）。
11. `SmbkSelectionPlan.kt`：`SCHEMA_VERSION 72→73`、`IDENTITY_HASH` 取 `73.json` 新值、`allTables 91→93`、`categoryTables[MASTERY] 35→37`、`trimOrder 83→85`、`rowCountKeys 93`、`CLOSURE_VERSION 4→5`、`CLOSURE_DIGEST_PREFIX v4→v5`；`expectedClosure` 仍是 **5 条边**（新表的 FK 只落在既有 MASTERY→ISSUE_BOOK 边上），`softReferences` 仍是 **21**（新引用都是硬 FK 或 JSON，不新增软引用列）。计划 item 12 写的 "CLOSURE 4→5" 只能指 `CLOSURE_VERSION`——`expectedClosure.size` 当前已经是 5，不可能再 4→5。
12. `SmbkSelectionPlanTest.kt:27,44-56,67,384`：91/102 FK/31/35/7/1/7/3/7/83 等断言全部改；FK 边数 **102→112**（observation 5 条 + binding 5 条）。
13. `BackupRowCountsCoverageInstrumentedTest.kt:95-97`：identityHash 断言随 `73.json` 更新。
14. 迁移矩阵 `FullMigrationMatrixInstrumentedTest`：1→73 全链；旧行保持（本迁移只建空表，旧表零改动）。
15. 备份 34 例：表数/类别/裁剪顺序/digest golden 重算；新增两表的 rowCounts 与恢复对拍。

**冲突面（必须先解决的顺序问题）**

- 基线已记录：`StudyDatabase.kt` 本身是 `M`，KB L2 的 71→72 链（含 `KnowledgeSearchMaterialFeatureMigration_71_72.kt` 等 6 个文件）仍是 `??`，**尚未入库**（`docs/research/2026-11-02-batch-40-baseline.md:104`）。WS2 在其上再叠 72→73，会把两条迁移写进同一个脏文件，无法单独归因 diff。建议：**先让 KB L2 链入库并复跑门，再开 72→73**；否则 WS2 的 item 12 只能记为"阻塞"。
- 计划 §2 `:98` 仍写"DB schema 71→72 由 WS2 单写者"，与 §1 item 11/12（73）冲突；以 §1 与基线为准（`baseline:101`）。这是计划文本错误，不是实现问题。

### 4.7 读写端口的不变量

- **写 observation 只能追加**；`UPDATE` 只允许改 `status/decided_at/decision_source`（状态机迁移），不允许改 `cause/confidence/evidence_refs`。
- **写 binding 只能**：新建 ACTIVE（同事务把 source observation 置 ACCEPTED）；ACTIVE→VOIDED；ACTIVE→SUPERSEDED + 新建 ACTIVE。
- **读 binding 只认 `state='ACTIVE'`**，不得用"最新时间戳"兜底（时钟异常会翻标签；仓内已有 `clock_anomaly_count`）。
- **删除禁止**：两表都不提供 `DELETE` DAO 方法；父行删除是 RESTRICT，不会静默带走。
- **JSON 软引用可悬空**：`evidence_refs_json` 里的 `tutor_message` 等引用不建 FK，会话删除后允许悬空；读侧必须容错并标注"证据已删除"，**不得因悬空而丢掉整条标签**（标签的权威来源是 observation 行，不是被引用的消息）。

---

## 5. D-1 自动落账门（D-1c）

### 5.1 先澄清两个 D-1

| 名称 | 定义 | 现状 | 用途 |
|---|---|---|---|
| **3D D-1** | KC 语义绑定精确率 **≥0.95** | 仓内记录冲突（见 §5.2） | "标注层可否自动落账"的总许可门 |
| **D-1c**（本文提议） | **错因**自动落账门：在 D-1 已过的前提下，对错因分类单独认证 | 未建立 | 决定 observation 能否升为 binding |

**为什么不能直接复用 D-1**：D-1 的分母是"KC 绑定"，不是"错因判定"。一个绑定正确的 KC 上，错因仍可能被判错；反之亦然。拿 D-1 的 0.95 去覆盖错因，是把两个不同测量的精度混为一谈。D-1 只能作为**前置**（P0），D-1c 必须单独测。

### 5.2 现状与源冲突（必须并列呈现，不替任何一方下结论）

- 记录 A（2026-10-04 门核验）：D-1 **未通过**，只有抽样点估计 ≈89.4% 精确率，全量逐条未做，无任何 ≥0.95 结论（`docs/research/2026-10-04-stage5-gate-check.md:15`、§2）。
- 记录 B（2026-10-11 台账修正 / 批次 9）：D-1 **按 95.15% 点估计判过**，但明确"非总体下界保证"（`docs/agent-first-refactor-decisions-2026-09-23.md:2735`；`docs/kb-outstanding-research-2026-10-02.md:1225`；`docs/REFACTOR-MASTER-LINE.md:83`）。
- 记录 C（本批基线）：引用记录 A，写"当前状态=未通过"（`docs/research/2026-11-02-batch-40-baseline.md:96`）。

**本文的处置**：不裁谁对；只按更保守的一条设计——**任何自动落账都必须自带下界证据，不能继承 95.15% 的点估计**。这既满足记录 B 的"无下界保证"限定，也不与记录 A 的"未通过"冲突。若台账要更新，应把"点估计过门/下界未达"写成两栏，而不是一个"通过"。

### 5.3 D-1c 的门结构

| 编号 | 条件 | 判据 | 类型 |
|---|---|---|---|
| **P0** | 前置 | 3D D-1 以**同一 taxonomy_version** 处于"已过且未过期"状态；否则 D-1c 不得开 | 硬 |
| **P1** | 预注册 | 评测协议、样本来源、排除规则、代理金标定义在开测前落盘；测后不得改 | 硬 |
| **P2** | 代理金标 | 只用**用户显式纠正**（"这不是我的错因"）作为正/负标签；未纠正 ≠ 正确（不得把沉默当确认） | 硬 |
| **P3** | 金标质量 | 抽 100 例双人独立标注，Cohen's κ ≥ 0.70；否则金标不可用，门不成立 | 硬 |
| **P4** | 总体精度 | 自动落账决策的 **Wilson 95% 单侧下界 ≥ 0.95**，且样本 n ≥ 200 | 硬 |
| **P5** | 每类下限 | `E01/E02/E04` 每类 Wilson 下界 ≥ 0.90 且 n ≥ 50；`E03` **不参与自动落账** | 硬 |
| **P6** | 置信阈值 | 由 precision–coverage 曲线选阈值；提案 `AUTO_BOOK_MIN_CONFIDENCE = 0.80`，`PENDING_CALIBRATION` | 软 |
| **P7** | 节点级归属 | 仅当 `assessment_evidence_snapshot.attributions` 的 `certainty=DIRECT` 才允许写 `knowledge_node_id`；否则只写题级 | 硬 |
| **P8** | 换版重认证 | 提取器/提示词/模型/枚举任一变更 → `extractor_version` 变 → D-1c 回到"未认证" | 硬 |

**Wilson 下界的具体含义**（n=200，单侧 95%）：

| 错误数 | 点精度 | Wilson 下界 | 是否 ≥0.95 |
|---|---|---|---|
| 0 | 1.000 | 0.981 | 是 |
| 1 | 0.995 | 0.972 | 是 |
| 2 | 0.990 | 0.964 | 是 |
| 3 | 0.985 | **0.957** | **是（边界）** |
| 4 | 0.980 | 0.950 | 否（0.9497） |
| 5 | 0.975 | 0.943 | 否 |

即：**n=200 时最多允许 3 个错误**。这不是"点估计 98.5% 就行"——必须报下界。

### 5.4 决策函数（草案）

```kotlin
fun mayAutoBook(obs: ErrorCauseObservation, gate: D1cGateState, now: Long): Boolean {
    if (!gate.certified) return false
    if (obs.cause == ErrorCause.CARELESS) return false          // P5
    if (obs.confidence < gate.minConfidence) return false        // P6
    if (obs.knowledgeNodeId != null && obs.certainty != DIRECT) return false // P7
    if (retractedRecently(obs.scopeKey, now)) return false       // 迟滞
    if (obs.corroborationCount < minCorroboration(obs.cause)) return false
    return true
}
```

`retractedRecently`：同一 `(learner, unit, revision, node, cause)` 若被用户撤回，**必须有一次晚于撤回时间的新的独立负事件**才允许重新自动落账；否则永久不自动落账（`RETRACTION_COOLOFF` 提案 30 天，`PENDING_CALIBRATION`）。没有这条，系统会在用户每次纠正后自动写回，形成"纠正—重写"拉锯。

### 5.5 kill-switch 与降级

- 滚动窗口（最近 50 条自动落账）内用户撤回率 > **5%** → `gate.paused=true`，自动落账停止，全部退回候选；需人工复核后重新认证。5% 是提案值，`PENDING_CALIBRATION`，不是文献结论。
- 任何 P0–P5 的证据过期（超过 90 天或 taxonomy_version 变更）→ 自动降级。
- 降级期间产品行为：**候选仍然生**（供教学/advisory 内部使用），但不写 binding；用户看不到"错因分类"，符合产品合同。

### 5.6 对计划 item 15 的直接回答

计划写"D-1 自动落账门（阈值 + 置信 + 撤销入口）"。本文的答案是：

1. **门要有，但 v1 应默认关闭**（`enabled=false`）。当前没有任何本项目语料的错因标注样本（0 条），P2–P5 无样本支撑（P1 可先落协议）；此时开自动落账等于无证据放行。
2. 门的三要素分别落在：**阈值** = §5.3 P4/P5；**置信** = §5.3 P6；**撤销** = §6。撤销入口不是门的附属，而是**独立的状态机**（§6）。
3. 若必须给一个"能开"的最短路径：先做候选 + 用户纠正入口（收集 P2 金标）→ 累积 ≥200 条纠正 → 算下界 → 过 P3/P4/P5 → 才开。这个顺序不能倒。

---

## 6. AUD-11 撤销语义边界

### 6.1 AUD-11 是什么（先对齐，不重定义）

AUD-11 是"判词粒度 / 冷却 / 答案键"三节的反例报告（`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md`），三条裁定已实施（`docs/REFACTOR-MASTER-LINE.md:165`）：

1. **判词粒度 = 锚定 + 归属落账**：整题判决要求 ≥1 条 accepted 证据锚在本题 KC 集；外部节点证据 → `NO_VERDICT`（实现见 `TutorJudgedReviewSettler.kt:34-66`）。
2. **冷却限 MODEL_CHAT**。
3. **答案键 = 可观察性先行**；纠正语义转 KD-43，后由 PRD-02 落地：`learner_chat_evidence.corrects_evidence_id` + `EVIDENCE_CORRECTION` 的 **VOID/REPLACE**（schema 68，`AnswerKeyCorrectionMigration_67_68.kt:8-20`）。

VOID/REPLACE 的语义（`LearningState.kt:540-553,668-684`）：

- **VOID**：把目标从有效证据集合**排除**（不是权重置零后继续推进）；投影器看到哨兵 `[void-attempt:v1]` 时在进入投影前丢弃。
- **REPLACE**：按新载荷在原账本位置/时间重算。
- 同一 attempt 的最终有效更正按账本序 **last-write-wins**；VOID 不可再撤销，只能追加 REPLACE。
- **这些都会改变掌握度投影**（重放）。

### 6.2 两条链的对照

| 维度 | AUD-11 证据纠正 | 错因撤回（本文） |
|---|---|---|
| 对象 | 作答/证据行、答案键判词 | 附着在作答/修订上的**原因标签** |
| 触发 | 答案键错、判词错、学生申诉 | 学生说"不是这个原因"、提取器换版、重做反证、题面修订 |
| 操作 | `attempt_correction` 追加 VOID/REPLACE | observation 追加 `RETRACTED/SUPERSEDED` + binding 置 `VOIDED/SUPERSEDED` |
| 是否重放 | **是**（账本→投影重算） | **否**（标签层，不进 `LearningProjector`） |
| 对掌握度 | 会变 | **不变** |
| 对作答事实 | 可排除/替换该次学习效果 | **永不改**（答错仍然是答错） |
| 幂等 | `correction_id` 内容寻址 | `observation_id` 内容寻址 + `active_key` 唯一 |
| 审计 | correction 行 + 审计口 | observation 全历史 + binding 状态 |
| 失败如果混用 | 掌握度被错误改写/证据被静默删 | 标签复活/拉锯/历史丢失 |

### 6.3 不变量（实现必须钉住）

- **I1 单事务**：observation 追加 + binding 状态变更 + `active_key` 置 NULL 必须在同一事务；任一失败全回滚（"不留半行"）。
- **I2 不删除**：两表无 DELETE；撤回是状态，不是消失。
- **I3 唯一 ACTIVE**：`active_key` 唯一索引兜底；同一 scope 不可能两条 ACTIVE。
- **I4 读侧只认 ACTIVE**：不得"取最新一条"；时钟异常不得翻标签。
- **I5 不进投影**：`ErrorCauseBinding` 不是 `IncrementalLearningEvent`；不改 `attempt_event` / `review_log` / `learner_*_state` / `applied_*_record`。
- **I6 来源必须 ACCEPTED**：binding 的 `source_observation_id` 必须 `status=ACCEPTED`；否则读/恢复 fail-closed。
- **I7 修订失效**：`problem_revision_id` 不匹配当前修订 → binding 不可读；新修订只能以"候选"续挂，不得自动继承。
- **I8 用户优先且粘性**：`USER_CORRECTED`/`USER` 撤回后，同一 scope 的自动落账必须等待新证据（§5.4 迟滞）。

### 6.4 依赖方向（单向，不可反）

```
答案键纠正（AUD-11） ──触发失效──▶ 错因撤回（E03/E04 尤其）
错因撤回 ──✗ 不得触发──▶ 答案键纠正 / 作答 VOID / 掌握度重放
```

具体：

- 若答案键被纠正为"学生其实答对了"，该 attempt 上的错因必须**自动 RETRACT**（`decision_source=SYSTEM`），因为"错"这个前提没了。这是唯一允许系统自动撤回的场景。
- 反向不成立：学生说"我不是概念错，是看错了"，只改标签；这次作答仍然是错的，仍然进掌握度。若允许反向，学生会通过"改错因"把答错洗成答对——这是必须堵死的激励漏洞。

### 6.5 "不留半行"的事务边界（逐场景）

| 场景 | 必须在一个事务里完成 | 失败会留下的"半行" |
|---|---|---|
| 自动落账 | 插 observation(ACCEPTED) + 插 binding(ACTIVE, active_key) | 有 observation 无 binding（候选被当成已生效）或反之（binding 指向 PROPOSED） |
| 用户撤回 | 插 observation(RETRACTED, supersedes=old) + old binding→VOIDED + active_key=NULL | binding 已 VOID 但无撤回理由行（审计断链） |
| 用户改判 | 插 observation(ACCEPTED, supersedes=old) + old binding→SUPERSEDED + new binding ACTIVE | 两条 ACTIVE（active_key 冲突）或 old 仍 ACTIVE（读侧翻标签） |
| 答案键纠正失效 | 插 observation(RETRACTED, decision_source=SYSTEM) + binding→VOIDED | 已判对的题上仍挂 ACTIVE 错因 |
| 修订换版 | 旧 binding 保持（仅对旧 `problem_revision_id` 可读）+ 新修订不自动建 binding | 新修订静默继承旧错因（错版标注） |

### 6.6 与备份/恢复的交界

- 两表都进备份（MASTERY 类别）；恢复后 `active_key` 必须保真，否则唯一性失效。
- 恢复侧校验（fail-closed）：每条 ACTIVE binding 的 `source_observation_id` 必须存在且 `status=ACCEPTED`；否则整个恢复拒绝，而不是"尽量修"。
- 撤回状态必须随包走：不能出现"备份前撤回、恢复后复活"。

---

## 7. 对 WS2 八项（items 9–16）的落地裁定建议

| Item | 计划内容 | 建议 | 理由/依赖 |
|---|---|---|---|
| 9 | 四类枚举 + 稳定码（core/model） | **可做** | E01–E04 + 预留 E05；`detail_code` 受控词表；`TAXONOMY_VERSION` |
| 10 | 错因证据模型（讲题 trace/自评/重做，core/model） | **可做，但需加三样** | 必须含 `channel`、`confidence`、`evidence_refs`、`observed_at`；否则无法审计/撤回 |
| 11 | schema 73 DDL（表 + 关联 + 索引） | **改为两表**；表数 91→93 | 见 §4.2；一张表会把候选/现行混在一起 |
| 12 | 迁移 72→73 + KSP `73.json` + 备份清单同步 | **可做，但依赖 KB L2 链先入库** | `StudyDatabase.kt` 已是 `M`，KB L2 六个文件仍 `??`（`baseline:104`）；否则无法单独归因 diff |
| 13 | 迁移矩阵 1→73（设备）+ 旧行保持 | **可做** | 本迁移只建空表，旧表零改动；矩阵用例走 `FullMigrationMatrixInstrumentedTest` |
| 14 | 错因提取器（trace + 自评 → 候选，不自动落账） | **可做，但 v1 只允许"候选"** | 无结构化步骤；E01/E02 必须 abstain 或走模型提案；见 §3.1 |
| 15 | D-1 自动落账门（阈值 + 置信 + 撤销入口） | **设计可做，默认关闭** | 0 条本项目标注样本，P1–P5 不成立；门必须是"拦"不是"放"（`baseline:222`） |
| 16 | AUD-11 撤销语义（不留半行，core/data + DAO） | **可做，但明确是标签链** | 绝不能复用 `attempt_correction VOID`；见 §6.2 |

**一句话**：WS2 的 9/10/11/13/14/16 可以在本设计上开工；12 需要先解决 KB L2 的入库顺序；15 只能落"门 + 默认关"，不能落"开"。

---

## 8. 未验证、单一来源与待裁决

### 8.1 未验证（UNVERIFIED）

| 项 | 状态 | 说明 |
|---|---|---|
| 本项目语料的错因标注 | **0 条** | 本文没有跑任何标注/评测；所有阈值都是提案 |
| D-1c 的任何实测精度 | **无** | 没有样本，P4/P5 不可能成立 |
| Gradle / 单测 / 设备 | **未跑** | 按共享纪律，门禁由脚本串行执行；本文零 Gradle |
| `73.json` 的 identityHash | **`1d086050204a0da1ed6b6e3299dd9733`** | 2026-11-02 13:35 KSP 在 rebased 92 表 schema 上生成；`SmbkSelectionPlan` 已回填同值 |
| 备份 92 表的 digest golden | **已按 rebase 复算** | `5c690755…`（2026-11-02 复核轮按同一 SHA-256 载荷独立复算；旧 93 表 `a8d4de2d…` 同脚本可复现） |
| IRIS 页面的原始研究报告 | **未取到** | 只取到案例页文本；其引用的一手研究（Howell 1993 等）未逐篇核 |

### 8.2 单一来源 / 仅摘要（按"待验证"处理）

| 项 | 来源 | 限制 |
|---|---|---|
| careless 与能力/信心正相关 | "Detecting Carelessness through Contextual Estimation of Slip Probabilities…"（AIED 2011）的检索摘要 | 只取到摘要片段，未读全文；**不得**当定论。本文未用它支撑任何阈值 |
| `FORGOTTEN` 退役 | 本文推论 + FSRS 现有 lapse | 没有直接文献说"错因分类不应包含遗忘"；这是设计裁定，不是文献结论 |
| `RETRACTION_COOLOFF=30d` / kill-switch 5% | 本文提案 | 无文献依据；`PENDING_CALIBRATION` |

### 8.3 源冲突（必须并列，不替任何一方裁）

| 冲突 | 甲 | 乙 | 处置 |
|---|---|---|---|
| D-1 是否过门 | 2026-10-04 门核验：未通过（89.4% 抽样） | 2026-10-11 台账：95.15% 点估计过门（无下界） | 本文取保守读法：自动落账必须自带下界；台账建议两栏并列 |
| schema 起点 | 计划 §2："71→72 由 WS2 单写者" | 计划 §1 + 实测：已是 72，WS2 写 72→73 | 以 §1/实测为准；§2 文本需更正（`baseline:101,213`） |
| 表数 | 计划/基线隐含 92 | 两表方案为 93 | 本文建议 93；若产品/台账坚持 92，需回退到一张表方案 C，并接受 §4.2 的失败 |
| 错因可见性 | 产品合同：不可见分类 | 旧 spec §7："用户详情页可纠正" | 二者可共存，但纠正必须是**内部动作**（"这不是我的错因"），不是分类浏览；需产品确认 |

### 8.4 待用户/台账裁决（3 条）

1. **是否允许一个"这不是我的错因"的纠正入口？** 若允许，它写 `observation.decision_source=USER`，但不展示分类列表、不做筛选/标签；若不允许，P2 的代理金标无从产生，D-1c 永远不能开。
2. **`FACTUAL` 是否要在 v2 升为 E05？** 本文建议先折叠进 E01 的 `detail_code`；若实测占比高且教学响应不同，再升。
3. **错因是否要挂到 KC（`knowledge_node_id`）？** 本文建议 v1 只写题级（NULL），仅 `certainty=DIRECT` 时写节点；否则会把"题级错因"错误分摊到多个 KC。

---

## 9. 来源清单（取用日期 2026-11-02）

**外部**

1. IRIS Center (Vanderbilt Peabody), *Mathematics: Identifying and Addressing Student Errors*. <https://iris.peabody.vanderbilt.edu/case-study/mathematics-identifying-and-addressing-student-errors/>（B）
2. Radatz, H. (1979). Error Analysis in Mathematics Education. *JRME* 10(3), 163–172. <https://doi.org/10.5951/jresematheduc.10.3.0163>（A；摘要经 Crossref 实读）
3. Movshovitz-Hadar, N., Zaslavsky, O., & Inbar, S. (1987). An Empirical Classification Model for Errors in High School Mathematics. *JRME* 18(1), 3–14. <https://doi.org/10.5951/jresematheduc.18.1.0003>（A；摘要经 Crossref 实读）
4. Andriani, S. P., Triyanto, & Nurhasanah, F. (2021). Procedural error of XIIth grade high school students in solving algebra problems based on Elbrink's theory. *IOP Conf. Ser.* 1796 012048. <https://doi.org/10.1088/1742-6596/1796/1/012048>（B；摘要经 Crossref 实读）
5. Mathaba, P. N., Bayaga, A., Tîrnovan, D., & Bossé, M. J. (2024). Error Analysis in Algebra Learning: Exploring Misconceptions and Cognitive Levels. *Journal on Mathematics Education*. ERIC EJ1428049. <https://eric.ed.gov/?id=EJ1428049>（B；ERIC API 实读）
6. *A Benchmark for Math Misconceptions: Bridging Gaps in Middle School Algebra with AI-Supported Instruction*. arXiv:2412.03765. <https://arxiv.org/abs/2412.03765>（B；预印本）
7. McNichols, H., Zhang, M., & Lan, A. (2023). Algebra Error Classification with Large Language Models. AIED 2023. <https://people.umass.edu/~andrewlan/papers/23aied-error.pdf>（A/B；PDF 实读）
8. Corbett, A. T., & Anderson, J. R. (1995). Knowledge tracing: Modeling the acquisition of procedural knowledge. *UMUAI* 5, 253–278. <https://doi.org/10.1007/bf01099821>（A）
9. *Carelessness Detection using Performance Factor Analysis: A New Operationalization with Unexpectedly Different Relationship to Learning*. arXiv:2503.04737. <https://arxiv.org/abs/2503.04737>（B；预印本）
10. Chi, M. T. H., Bassok, M., Lewis, M. W., Reimann, P., & Glaser, R. (1989). Self-explanations: How students study and use examples in learning to solve problems. *Cognitive Science* 13(2), 145–182. <https://doi.org/10.1207/s15516709cog1302_1>（A）
11. Rittle-Johnson, B., & Siegler, R. S. *The relation between conceptual and procedural knowledge in learning mathematics: A review*. <https://doi.org/10.4324/9781315784755-6>（A；只引标题级结论）
12. Geifman, Y., & El-Yaniv, R. (2017). Selective Classification for Deep Neural Networks. arXiv:1705.08500. <https://arxiv.org/abs/1705.08500>（A）

**仓内**

13. `docs/specs/mastery-scheduling-spec.md:196,247-248,290`（旧枚举、分通道、个人基线 ≥30）
14. `docs/requirements-fused-final-2026-10-11.md:73,80,210`（标注层自动完成、D-1 许可门、绑错记错账）
15. `docs/REFACTOR-MASTER-LINE.md:83,86,165,191`（3D/3C/5c、AUD-11 三裁定）
16. `docs/agent-first-refactor-decisions-2026-09-23.md:464,2680-2690,2735`（备注保留、`user_note` 转录不落原文、D-1 点估计）；`docs/REFACTOR-MASTER-LINE.md:191`（备注原文不出网）
17. `docs/research/2026-10-04-stage5-gate-check.md:15,§2`（D-1 未通过）
18. `docs/research/2026-11-02-batch-40-baseline.md:87-104,213-214,222`（schema/备份/D-1/编号复用）
19. `docs/audit-2026-09-12-kernel-readiness.md:1555-1560,1912`（error_type 占位列无写方，R-09）
20. `docs/product-information-architecture.md:287,408`；`docs/m1-exhaustive-product-contract.md:95,259`；`docs/model-first-product-boundaries.md:9`（错因不可见）
21. `core/model/.../LearningState.kt:540-553,668-684,739`（VOID/REPLACE 语义）
22. `core/database/.../AnswerKeyCorrectionMigration_67_68.kt:8-20`（schema 68 纠正列）
23. `core/data/.../study/TutorJudgedReviewSettler.kt:34-66`（判词锚定）
24. `core/database/.../entity/LearningEntities.kt:283-360,637-684,778-816`（attempt_event / memory state / independent correct）
25. `core/database/.../entity/TutorConversationEntities.kt:138-159`（tutor_message 无结构化步骤）
26. `core/database/.../entity/ReviewEntities.kt:213,223-224`（可空唯一活键先例）
27. `core/data/.../backup/SmbkSelectionPlan.kt:44-57,293-299,548-564`（schema/identity/闭包/表数）
28. `core/data/src/test/.../backup/SmbkSelectionPlanTest.kt:27,44-56,67,384`（91/102/83 断言）
29. `core/database/.../StudyDatabase.kt:140,149,415`（版本/实体/迁移注册）
30. `core/data/.../study/ReviewLogSink.kt:351-380`（证据来源分级）

---

## 附录 A：Wilson 95% 单侧下界（自动落账门用）

| n \ 错误数 | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| 100 | .963 | .946 | .930 | .916 | .902 | .888 |
| 150 | .975 | .963 | .953 | .943 | .933 | .924 |
| 200 | .981 | .972 | .964 | **.957** | .950 | .943 |
| 300 | .987 | .981 | .976 | .971 | .966 | .962 |

计算式：Wilson score interval，`z=1.959963984540054`；表中为下界。n=200 / 3 错的精确值 `.9568`（四舍五入 .957），4 错 `.9497`（表中 .950，实际 <0.95，故不通过）。

## 附录 B：DDL 草案（非最终；以 KSP `73.json` 为准）

```sql
CREATE TABLE IF NOT EXISTS `error_cause_observation` (
  `observation_id` TEXT NOT NULL,
  `learner_id` TEXT NOT NULL,
  `practice_unit_id` TEXT NOT NULL,
  `problem_revision_id` TEXT NOT NULL,
  `knowledge_node_id` TEXT,
  `attempt_id` TEXT,
  `cause` TEXT NOT NULL,
  `detail_code` TEXT,
  `confidence` REAL NOT NULL,
  `channel` TEXT NOT NULL,
  `extractor_version` TEXT NOT NULL,
  `taxonomy_version` TEXT NOT NULL,
  `rationale_markdown` TEXT NOT NULL,
  `evidence_refs_json` TEXT NOT NULL,
  `observed_at_epoch_millis` INTEGER NOT NULL,
  `status` TEXT NOT NULL,
  `decided_at_epoch_millis` INTEGER,
  `decision_source` TEXT,
  `supersedes_observation_id` TEXT,
  PRIMARY KEY(`observation_id`),
  FOREIGN KEY(`practice_unit_id`) REFERENCES `practice_unit`(`practice_unit_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`problem_revision_id`) REFERENCES `problem_revision`(`problem_revision_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`attempt_id`) REFERENCES `attempt_event`(`attempt_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`supersedes_observation_id`) REFERENCES `error_cause_observation`(`observation_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS `index_error_cause_observation_learner_id_practice_unit_id_observed_at_epoch_millis`
  ON `error_cause_observation` (`learner_id`, `practice_unit_id`, `observed_at_epoch_millis`);
CREATE INDEX IF NOT EXISTS `index_error_cause_observation_attempt_id`
  ON `error_cause_observation` (`attempt_id`);
CREATE INDEX IF NOT EXISTS `index_error_cause_observation_practice_unit_id_cause`
  ON `error_cause_observation` (`practice_unit_id`, `cause`);

CREATE TABLE IF NOT EXISTS `error_cause_binding` (
  `binding_id` TEXT NOT NULL,
  `learner_id` TEXT NOT NULL,
  `practice_unit_id` TEXT NOT NULL,
  `problem_revision_id` TEXT NOT NULL,
  `knowledge_node_id` TEXT,
  `cause` TEXT NOT NULL,
  `detail_code` TEXT,
  `confidence` REAL NOT NULL,
  `acceptance_source` TEXT NOT NULL,
  `gate_version` TEXT,
  `source_observation_id` TEXT NOT NULL,
  `accepted_at_epoch_millis` INTEGER NOT NULL,
  `state` TEXT NOT NULL,
  `active_key` TEXT,
  `superseded_by_binding_id` TEXT,
  `ended_at_epoch_millis` INTEGER,
  PRIMARY KEY(`binding_id`),
  FOREIGN KEY(`practice_unit_id`) REFERENCES `practice_unit`(`practice_unit_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`problem_revision_id`) REFERENCES `problem_revision`(`problem_revision_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`knowledge_node_id`) REFERENCES `knowledge_node`(`knowledge_node_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`source_observation_id`) REFERENCES `error_cause_observation`(`observation_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT,
  FOREIGN KEY(`superseded_by_binding_id`) REFERENCES `error_cause_binding`(`binding_id`)
    ON UPDATE NO ACTION ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_error_cause_binding_active_key`
  ON `error_cause_binding` (`active_key`);
CREATE INDEX IF NOT EXISTS `index_error_cause_binding_learner_id_practice_unit_id_state`
  ON `error_cause_binding` (`learner_id`, `practice_unit_id`, `state`);
CREATE INDEX IF NOT EXISTS `index_error_cause_binding_problem_revision_id`
  ON `error_cause_binding` (`problem_revision_id`);
CREATE INDEX IF NOT EXISTS `index_error_cause_binding_source_observation_id`
  ON `error_cause_binding` (`source_observation_id`);
```

**索引名长度提醒**：Room 自动生成的索引名有 64 字符上限；上面第一个索引名 **82 字符**，落码时需改用 Room 的短名或 `<table>_<cols-hash>` 约定。这是草案，不是可直接粘进 `@Entity` 的文本。
