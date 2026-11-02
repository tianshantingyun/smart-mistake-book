# R7 错因 → 反馈与练习策略的教学映射（产品话术与调度建议）

> 批次 41 / 研究线 R7（计划 `.zcode/plans/plan-2026-11-03-batch-41.md:22`）。
> 题面：「conceptual/procedural/careless/misidentification 四类错因对应的反馈与练习策略（文献与可引来源），用于产品话术与调度建议；不得面向评测集调参。」
> 取用日期：2026-11-03。只读研究：**零 Gradle、零设备、零 git 写、零源码改动**；本文不声称任何门"通过"。
> 前置：分类判据、落库与撤销语义见 `docs/research/2026-11-02-error-taxonomy.md`（下称 R2）；本文**不重复**分类判据，只答"判出来之后说什么、练什么、怎么排"。

**证据等级**（沿用 R2 §0 约定）

- **A**：同行评审论文 / 官方规范，本轮核到书目元数据与摘要（Crossref 实查或出版方摘要页实读）。
- **B**：同行评审会议/期刊，但本轮只取到摘要片段或二手转述；结论方向可用，数字须再核。
- **C**：单一来源、机构教学资源、预印本，或经仓内研究件转引而未读原文。按"待验证"处理，不得支撑"可行/有效"的强结论。
- **【裁定】/【提案】**：不是文献结论，是本项目的设计选择；数值一律 `PENDING_CALIBRATION`。

---

**路径缩写约定**：为表格排版，正文部分用 `<文件名>:line` 缩写；每个缩写的完整路径见 §9 仓内清单（第 23–33 条），其中 `ErrorCauseGate.kt` = `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseGate.kt`，`ReviewSessionScreen.kt` / `KnowledgeReviewSessionScreen.kt` = `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/` 下同名文件，`leech-remediation-research.md` = `docs/research/leech-remediation-research.md`。

---

## 0. 结论摘要（可直接进台账的 12 条）

| # | 结论 | 类型 | 证据 |
|---|---|---|---|
| 1 | **四类的差别不在"用哪套词"，而在"反馈指向哪一层、下一步做什么动作"。** 对四类都用同一句"再想想/多练练"，等于没有反馈 | 设计裁定 | Hattie & Timperley 2007 四层（A）；Shute 2008 "specific"（A） |
| 2 | **任何一类的第一步都不是给答案，而是让学生把自己的推理/步骤/读到的题面说出来。** 反馈必须包含对"错误推理过程"的分析，只给正确答案不算纠正性反馈 | 文献结论 | Metcalfe 2017 摘要原文（A）；Metcalfe et al. 2025 课堂分析（经仓内转引，B/C） |
| 3 | **E01 概念错 → 先制造"现有规则不成立"的冲突，再给可理解的替代规则**；材料优先"误解纠正型"，不是"再讲一遍课本" | 文献结论 | Posner et al. 1982 四条件（A）；Tippett 2010 refutation text 综述（B） |
| 4 | **E02 程序错 → 定位到具体步骤，用同型例题示范该步，再渐隐让学生补该步，紧邻一道同型已有题** | 文献结论 | VanLehn 2011 步骤级交互（B）；Atkinson & Renkl 2003 渐隐（A/B）；Margulieux et al. 2012 子目标标签（B） |
| 5 | **E03 粗心 → 不重教、不加量、不贴标签；只给"提交前自查动作"，且只作短时信号** | 文献结论 + 裁定 | careless 两种操作化互不对齐（arXiv:2503.04737，B）；自查/自我监控综述（B）；R2 §3.3 不晋级 |
| 6 | **E04 识别/转录错 → 改"读题动作"与题面呈现，不补知识点**；教"按结构读题"，**不要**教"关键词对应运算" | 文献结论 | Powell & Fuchs 2018 明确反对关键词法（B）；IRIS Step 3 让学生解释自己的逻辑（B） |
| 7 | **时机要分开看**：步骤级提示越早越好（避免把错步骤练熟）；**完整解释/正确答案**延迟到学生尝试之后，保持效果不劣甚至更好 | 文献结论 + 裁定 | Butler, Karpicke & Roediger 2007 "delayed feedback led to superior final test performance"（A）；Metcalfe 2017（A） |
| 8 | **间隔与交错只用于"重排已有题"，不得据此生成新题、同类题或变式题** | 产品合同硬约束 | `docs/model-first-product-boundaries.md:9` 同段"绝不生成新题、同类题、变式题" |
| 9 | **话术不得暴露 E01–E04、不得出现"概念错/粗心/审题不清"这类分类词，不得承诺掌握度变化** | 产品合同硬约束 | `docs/product-information-architecture.md:287,408`；`docs/m1-exhaustive-product-contract.md:95,259` |
| 10 | **无证据（gate 关闭 / abstain / 通道只有自评）时，只能走通用话术**；通用话术不猜测原因，只邀请学生说出思路并给一个可做的下一步 | 设计裁定（与实现一致） | `ErrorCauseGate.kt:38-43,56`（默认关、CARELESS 与 SELF_REPORT 永不放行）；R2 §5.6 |
| 11 | **"粗心"不能写成学生的属性。** 对人评价（"你真粗心/你真聪明"）与任务无关，且能力表扬比过程表扬更伤动机 | 文献结论 | Hattie & Timperley 2007 self 层定义（A）；Mueller & Dweck 1998 摘要（A） |
| 12 | **本文所有阈值都是教学先验，不是实证值；不得用任何评测集的表现来调话术或阈值** | 纪律 | 本文 §5.5；R2 §8.1（本项目错因标注样本 = 0） |

---

## 1. 约束：为什么"类别"不能直接变成"话术"

### 1.1 产品合同的硬边界（逐条实读）

| 约束 | 原文/位置 | 对话术的直接含义 |
|---|---|---|
| 错因不得成为可见分类、筛选、分组、卡片标签或必填字段 | `docs/product-information-architecture.md:287` | 不能有"这是概念错/粗心"的标签、徽章、筛选项 |
| 错因、来源、题型不显示为标签或筛选项 | 同文件 `:408`；`docs/m1-exhaustive-product-contract.md:95,259`；`docs/model-first-product-boundaries.md:9` | 连"错因"这个词都不应出现在面向学生的界面 |
| 不暴露内部算法名 | `docs/product-information-architecture.md:409`（"不暴露 FSRS、BKT、HLR 等算法名"） | 也不能说"按遗忘曲线""按掌握度模型" |
| 没有用户明确要求时，绝不生成新题、同类题、变式题或校准题 | `docs/model-first-product-boundaries.md:9` | "多练几道同类题"这句话在本产品里**不能兑现**，因此不能作为话术承诺 |

**结论**：错因类别只能用于**系统内部选择"给哪种帮助"**；面向学生的话术只说"这一步/这个规则/这个读法"，不说"你属于哪一类"。

### 1.2 证据面：类别是推断，不是观测

- 细粒度错因自动分类的实测上限约 **80.7%**（BERT）/ **85.9%**（加学生所选动作），原文明确"没有对学生每一步意图的完美知识，就不可能确定错因"（McNichols, Zhang & Lan, AIED 2023，转引 R2 S7）。→ 把类别当事实说出口，会把 15–20% 的误判变成对学生的错误断言。
- `ErrorCauseGate` 的 `certified` 默认 `false`，CARELESS 与 SELF_REPORT 通道永不放行（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseGate.kt:38-43,56,69`）。→ **生产默认状态下，系统根本不该"知道"类别**；话术必须能在"不知道"时成立。
- E01/E02/E04 的判定都要求"学生自己产生的 trace"（R2 §1.3 Q1、§3.6）；模型解释不是学生证据。→ 反馈里引用"你当时说……"必须能指回学生的原话/原选项，不能是模型替学生编的理由。

### 1.3 由此得到的三条话术规则（本文其余部分都服从它）

1. **可撤回**：话术只描述"这一次的哪一步/哪一处"，不描述"你这个人/你这门课"。
2. **可执行**：每段反馈必须落到一个学生当场能做的动作（说出规则、补一步、读一遍、自查一项）。
3. **可降级**：拿不到证据时，话术退化为"邀请说出思路 + 一个通用下一步"，且**不出现任何原因判断**。

---

## 2. 跨类基线：反馈的五个结构位

这一节是四类共用的骨架；§3 只填"每类往里放什么"。

### 2.1 三问：任何反馈都要回答的三个问题

Hattie & Timperley (2007) 把有效反馈拆成三问：**Where am I going?（目标）How am I going?（现状与目标的差）Where to next?（下一步）**。三问缺一，反馈就退化成评价。

- 对本产品：目标 = "这道题要你做出的那一步"；现状 = "你做到了哪、断在哪"；下一步 = "现在做这一个动作"。
- 禁止：只回答"对/错"（缺现状与下一步），或只回答"你要更仔细"（缺目标与可执行动作）。

来源：Hattie, J., & Timperley, H. (2007). The Power of Feedback. *Review of Educational Research* 77(1), 81–112. DOI `10.3102/003465430298487`（A；Crossref 摘要全文实读；三问与四层的具体措辞取自 JSTOR 页面在检索摘要中给出的原文片段，本轮未打开 JSTOR 正文）。

### 2.2 四层：反馈指向什么，决定它有没有用

Hattie & Timperley 的四个层级（JSTOR 页面原文片段，经检索摘要；本轮未打开 JSTOR 正文）：

> "These include the level of task performance, the level of process of understanding how to do a task, the regulatory or metacognitive process level, and/or the self or personal level (unrelated to the specifics of the task)."

| 层 | 指向 | 在本产品里的例子 | 用法 |
|---|---|---|---|
| **task** | 这一题/这一步对不对 | "第 3 步的符号反了" | 起点，但不能停在这 |
| **process** | 做这类任务的策略/步骤 | "先把条件列成两行，再选运算" | **四类的主战场** |
| **self-regulation** | 学生自己监控、检查、安排 | "提交前用 30 秒核对符号与单位" | E03/E04 的主战场 |
| **self** | 对学生本人的评价（与任务无关） | "你真粗心""你就是不细心" | **禁用** |

**为什么 self 层必须禁用**：它与任务细节无关，不携带"怎么改"的信息；而且能力表扬比过程表扬更伤动机——Mueller & Dweck (1998) 六项实验的摘要结论是 "praise for intelligence had more negative consequences for students' achievement motivation than praise for effort"（A）。对本产品，"你真粗心"同时踩中两条：人格标签 + 固定归因。

### 2.3 剂量与伤害：反馈不是越多越好

Kluger & DeNisi (1996) 元分析（607 个效应量、23,663 个观测）：反馈干预平均 `d = .41`，但 **超过三分之一的干预降低了表现**（原文 "over 1/3 of the FIs decreased performance"）。Shute (2008) 的综述把合格的形成性反馈概括为四个词：**nonevaluative, supportive, timely, and specific**。

对本产品的三条推论：

1. **不要每错必长文**。一段反馈只解决一个断点；长解释会把"下一步"淹没。
2. **不要带评价色彩**（"这么简单也错"属于 evaluative，直接落在 self 层）。
3. **specific 优先于完整**：说清"哪一步/哪一处"，比给完整解法更有用（也与 §2.4 的"以正确解法为目标更差"一致）。

### 2.4 错误必须被"用上"，而不是被"绕开"

Metcalfe (2017) 摘要原文：

> "Experimental investigations indicate that errorful learning followed by corrective feedback is beneficial to learning. Interestingly, the beneficial effects are particularly salient when individuals strongly believe that their error is correct: Errors committed with high confidence are corrected more readily than low-confidence errors. **Corrective feedback, including analysis of the reasoning leading up to the mistake, is crucial.**"

三个可落地结论：

1. **高置信错误是最高收益目标**（hypercorrection）：学生"很确定却错"的题，值得优先给解释性反馈，而不是只标错。
2. **反馈必须分析"导致错误的推理"**，不是只给正确答案。
3. **低风险场景应允许先错**：摘要明确 "allow and even encourage students to commit and correct errors while they are in low-stakes learning situations"。

仓内已有同一结论的落地件：`docs/research/leech-remediation-research.md:107-133`（§3.1–§3.3），并据此把重教材料排序改成 `MISCONCEPTION_GUIDE` 优先（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:52-75`）。本文与该排序**一致**，不另立一套。

> 转引限制：Metcalfe et al. 2025（*BJEP* 95(1), DOI `10.1111/bjep.12651`，课堂研究："以得到正确解法为目标"劣于"让学生理解自己的错误"）本轮**未读原文**，仅经 `leech-remediation-research.md:127-133` 转引，按 B/C 处理。

### 2.5 时机：即时的是"步骤提示"，延迟的是"完整解释"

Butler, Karpicke & Roediger (2007) 的两项实验：延迟反馈的最终测试表现**优于**即时反馈（原文 "delayed feedback led to superior final test performance relative to immediate feedback"）；反馈格式（给正确答案 vs 答到对为止）**没有差异**（"type of feedback did not matter"）。作者把延迟收益归因于"间隔呈现"（A）。

这与"程序错要尽早纠正"不矛盾，因为两者对象不同：

| 对象 | 时机 | 理由 |
|---|---|---|
| **步骤级提示**（"这一步先别往下，检查进位"） | 越早越好 | 避免把错步骤练成习惯；步骤级交互是 ITS 的典型形态（VanLehn 2011，B） |
| **完整解释 / 正确答案** | 学生尝试并说出思路之后 | 延迟不劣甚至更优（Butler 2007，A）；先给答案会消掉"冲突"与"提取" |

**【裁定】** 本产品采用"两段式"：作答后先给**定位提示**（一句，指向层与步骤），学生说出/补出该步后，再给**完整解释或材料**。这条是设计选择，不是文献直接比较过的产品形态。

### 2.6 表扬：表扬过程，不表扬人

Mueller & Dweck (1998)（A）：能力表扬 > 过程表扬的负面后果，体现在 performance goals 上升、面对困难时坚持下降。落到文案：

- ✅ "你把条件拆成两行再选运算，这个顺序是对的。"
- ❌ "你真聪明/你数学真好。"
- ❌ "你就是太粗心。"

### 2.7 落到本产品的反馈骨架（五段，可裁剪）

```
1) 确认状态（task 层，一句）：你做到了哪一步 / 哪一处和题面不一致
2) 指向断点（process 层，一句）：断在"规则 / 步骤 / 读题 / 手快"的哪一处（用行为词，不用类别词）
3) 要学生补的证据（一句提问）：说出你当时怎么想的 / 把这一步补上 / 把题面这一行读一遍
4) 给材料（可选）：只给针对该断点的那一种（误解纠正 / 同型例题 / 题面重排 / 自查清单）
5) 下一步（一个动作）：现在只做这一件事；做完再回到整题
```

四类的差别集中在 2) 和 4)；1)、3)、5) 共用。这保证"不暴露类别"也能给出差异化反馈。

---

## 3. 四类映射（核心）

> 每类固定五栏：**先说什么 / 给什么证据 / 下一步动作 / 禁止说什么 / 证据与强度**。
> 判据本身见 R2 §1.2；这里只写"判出来之后"。

### 3.1 E01 CONCEPTUAL（规则不成立 / 前置缺口）

| 栏 | 内容 |
|---|---|
| **先说什么** | 把学生用的规则**显式说出来**，再放到一个反例上："你这一步用的是「……」。我们拿另一个数试同一规则——它还成立吗？"（先制造不满意，不给结论） |
| **给什么证据** | ① 反例/对比案例（同一规则、不同题面）；② 可理解的替代规则（refutation 结构：常见想法 → 为什么不成立 → 更准确的规则）；③ 材料类型按 `RoomTutorTeachingReferenceRepository.kt:73-81` 的优先级取：`MISCONCEPTION_GUIDE`(0) → `WORKED_EXAMPLE`(1)；`CONCEPT_EXPLANATION` 只作兜底（第 4 位，前面还有 `METHOD_MODEL`/`DERIVATION`） |
| **下一步动作** | 让学生**用自己的话**说出新规则（一句），再回到原题改**一步**；随后用**同 KC 的另一道已有题**检验迁移（不生成新题） |
| **禁止说什么** | "这个概念你没掌握"（类别+能力标签）；"你把课本再看一遍"（无针对性）；直接给完整解法（见 §2.4） |
| **证据与强度** | Posner et al. 1982 四条件：dissatisfaction / intelligible / plausible / fruitful（A，SEDL 摘录原文）；refutation text 是"最有效的文本手段之一"（Tippett 2010，B，仅摘要）；概念/程序难分（R2 S1 引 IRIS，B） |

**为什么不是"多讲一遍"**：Posner 的第一条件是"对现有概念不满意"。学生如果仍认为自己的规则成立，再正确的讲解也只会被"同化"进旧规则。refutation 结构之所以有效，是因为它先把旧规则摆出来、指出它在哪里失败。

**为什么不是"多练同类题"**：概念错的复现需要**不同题面**（R2 §3.3 晋级条件），同题重复会被"记住答案"污染；且产品合同不允许生成新题。可用的是学生已保存的同 KC 题。

### 3.2 E02 PROCEDURAL（规则对、步骤执行错）

| 栏 | 内容 |
|---|---|
| **先说什么** | 先肯定规则/思路，再定位步骤："你的思路是对的，断在第 N 步——「……」这一步。"（task→process，不否定整体） |
| **给什么证据** | ① 该步的**同型例题**（步骤可见、只展示到该步）；② 渐隐：下一次只留该步让学生补（"fading"）；③ 子目标标签：给这一步起个名字（如"先通分再相加"），帮助学生把这步当成一个可调用的单元；④ 材料类型优先 `WORKED_EXAMPLE` |
| **下一步动作** | **紧邻**一道结构同型的**已有**题，只练这一步；该步稳定后再回到整题；间隔按正常排期，不额外加量 |
| **禁止说什么** | "计算太差"（能力标签）；"多练几遍就好了"（无定位的加量）；"你太马虎"（把程序错说成粗心） |
| **证据与强度** | 渐隐例题对近迁移可靠、远迁移不可靠，需配 self-explanation prompts（Atkinson & Renkl 2003，A/B，PsycNet 摘要）；子目标标签改善表现与迁移（Margulieux et al. 2012，B，标题级）；步骤级交互是 ITS 的典型形态（VanLehn 2011，B） |

**关键区分**：E02 的错误是"规则对、执行错"，所以反馈**不能**去纠正规则（那会让学生怀疑本来正确的理解），也不能说成"粗心"（那会让学生以为不需要改步骤）。二者都会把可修复的步骤问题变成不可修复的自我怀疑或忽略。

**为什么渐隐而不是"一直看例题"**：持续给完整例题会把"执行"这一环一直外包出去；渐隐把最后一步交还给学生，才是从"看懂"到"会做"的过渡。

### 3.3 E03 CARELESS（会而失手，单次）

| 栏 | 内容 |
|---|---|
| **先说什么** | 把"这次事件"与"他的能力"分开："这次是手快漏了「……」；同类题你之前是做对的。"（只描述这一次的行为） |
| **给什么证据** | **不给新材料**（重教会传递"你不会"的错误信号）；给一个**当场可用的自查清单**：符号 / 单位 / 抄写 / 所求 / 是否漏条件（按本题错点裁成 2–3 项，不要给通用十条） |
| **下一步动作** | **立即原题重做一次**（不是新题）；下一次呈现时先做同一份自查；**不加量、不降间隔、不改 retention** |
| **禁止说什么** | "你太粗心"（人格标签，§2.2 self 层）；"这么简单都错"（evaluative）；"以后要认真"（不可执行）；把 careless 写成长期标签或可见属性 |
| **证据与强度** | careless 的两种操作化在同一批数据上互不对齐、与后测相关方向相反（arXiv:2503.04737，B，预印本）；自我监控/自查在数学上对普通与特殊需要学生"generally benefit"（2024 系统综述，B，仅摘要）；R2 §3.3 已裁定 CARELESS 不晋级 binding、TTL 14 天 |

**为什么"不重教"**：重教的前提是"不会"。对"会而失手"的学生重教，一是浪费预算，二是传递"你其实不会"的错误归因，三是挤掉真正需要重教的卡。仓内 leech 规则 R7 已经写了同一条（`leech-remediation-research.md:291`："仅在掌握度低时注入；已熟练却偶发失误的卡不注入"）。

**为什么"只做短时信号"**：careless 构念本身不稳定；把它写成长标签会把一次手滑变成学生属性，并可能抑制本该发生的重教（R2 §0 第 3 条、§3.3）。话术层同理：这一次可以说"手快漏了"，但不能在下一次、下一题继续提。

### 3.4 E04 MISIDENTIFICATION（方法与规则对，读/抄题面错）

| 栏 | 内容 |
|---|---|
| **先说什么** | 指出"读到的"与"题面的"差："方法和步骤都对，是题面这一处你读成了「…」，原题是「…」。"（只描述错配，不评价） |
| **给什么证据** | ① **重新呈现题面**：分行、标出条件与所求、放大符号/下标（呈现层修订，材料类型 `REPRESENTATION_GUIDE` 或直接改卡面）；② 教"按结构读题"：已知 / 所求 / 条件 / 限制，各一行；③ **不要**教"看到'一共'就加"这类关键词对应（Powell & Fuchs 2018 明确：关键词法"discourages mathematical reasoning and frequently produces incorrect answers"，B） |
| **下一步动作** | **同一题立即重做**（验证"纠正读题后正确"）；下一次呈现先做"读题三问"；不改知识点掌握、不补课 |
| **禁止说什么** | "审题不清"（泛化指责，等于 self 层）；"你就是不认真"；把 E04 归到"这个知识点不会"去重教 |
| **证据与强度** | Powell & Fuchs 2018（B，ERIC 摘要）；IRIS Step 3 "asking them to explain their logic"（B，R2 S1）；R2 §1.2 已把 E04 收窄为感知/转录层，并要求"纠正读题后立即正确"作为反证 |

**为什么不能归到知识点**：E04 的判据本身要求"方法正确"；把它当知识缺口，会让学生去补已经会的东西，同时掩盖真正要改的读题动作。R2 §1.2 已明确 E04 与 E01 的互斥边界；话术层必须一致。

**为什么"关键词法"要明确禁止**：它不是"另一种读题技巧"，而是被证明会**制造**错误答案的策略。对本产品，学生已经做错的题上再教关键词法，等于把错误策略固化。

### 3.5 四类一张矩阵

| | E01 概念 | E02 程序 | E03 粗心 | E04 识别/转录 |
|---|---|---|---|---|
| **反馈层** | process（规则） | process（步骤） | self-regulation（检查） | task+process（读题） |
| **先说什么** | "你用的规则是…，它在这个反例上还成立吗" | "思路对，断在第 N 步" | "这次手快漏了…，同类题你会" | "读成了…，原题是…" |
| **给什么证据** | 反例 + 替代规则（MISCONCEPTION_GUIDE） | 同型例题 + 渐隐 + 子目标名（WORKED_EXAMPLE） | 无材料；2–3 项自查清单 | 题面重排 + 结构化读题（REPRESENTATION_GUIDE） |
| **下一步** | 说出新规则 → 改一步 → 同 KC 另一题 | 只练该步 → 同型已有题 → 回整题 | 原题立即重做；不加量 | 原题立即重做；下次先读题三问 |
| **调度** | 重教优先；材料后紧邻练习；重教完成前不追求高频重做 | 正常间隔；步骤稳定后再回整题 | 间隔/retention **不变**；只加自查 | 不改排期；改呈现 |
| **晋级条件** | 可晋级（2 条独立负事件） | 可晋级（2 次同步骤签名） | **永不晋级**（TTL 14d） | 需"学生明说 + 纠正后对" |
| **主证据** | Posner 1982 / Tippett 2010 | Atkinson 2003 / Margulieux 2012 | arXiv:2503.04737 / 自查综述 | Powell & Fuchs 2018 / IRIS |

### 3.6 无证据时（abstain / 门关 / 只有自评）

这是**生产的默认状态**（`ErrorCauseGate.isDefaultClosed == true`，`ErrorCauseGate.kt:56`），必须单独写清：

| 情形 | 话术 | 禁止 |
|---|---|---|
| 候选未过门 / abstain | "这次选择还差一步。先说说你当时是怎么想的，我按你的思路找断点。" | 任何原因判断；任何"你应该是…"的猜测 |
| 通道只有 `SELF_REPORT` | 同上；可加"你自己觉得卡在哪一步？" | 把自评当错因（R2 §3.6："自评不是测量，是报告"） |
| 学生明确说"我不是这个原因" | "好，那这条不算。你能说说当时是怎么想的吗？"（撤回标签，不改作答事实） | 继续按旧标签给材料；把撤回当成"学生不愿重教" |
| 答案键被纠正（学生其实答对） | 系统自动撤回错因（R2 §6.4），话术不再提"错" | 保留任何"你错了"的措辞 |

**与现有文案的一致性**：`ReviewSessionScreen.kt:828` 与 `KnowledgeReviewSessionScreen.kt:642` 现在都用"这次选择还差一步"——这正是"无证据时的通用话术"，且**不猜测原因**。本文建议的降级话术是在它后面加一句"邀请说出思路"，不是替换它。

---

## 4. 产品话术模板

> 以下模板是**教学/讲题通道**的内部素材，不等于要新增 UI 元素；任何进入 UI 的文案仍受 §1.1 约束（不暴露类别、不做标签）。

### 4.1 骨架与禁用词

**骨架**（对应 §2.7）：`确认 → 定位 → 要证据 → （可选）材料 → 下一步`

**禁用词 → 替代表**（完整版见附录 A）

| 禁用 | 原因 | 替代 |
|---|---|---|
| 概念错 / 程序错 / 粗心 / 审题不清 | 暴露内部类别（合同） | 这一步的规则 / 这一步的顺序 / 手快漏了 / 读成了 |
| 你真粗心 / 你真聪明 | self 层 + 能力表扬（§2.2/2.6） | 这一次漏了… / 你这个顺序是对的 |
| 这么简单都错 | evaluative（Shute） | 这一步只差一个符号 |
| 多练几遍 | 不可执行 + 可能生成新题（合同） | 现在只练这一步 |
| 你没掌握 / 你会了 | 承诺掌握度（合同：掌握度只由投影写） | 这道题你现在能做到… |
| 按遗忘曲线 / 按掌握模型 | 暴露算法名（`docs/product-information-architecture.md:409`） | 过几天再见面 / 下次先做这道 |

### 4.2 四类模板（各 2 条，可直接改用）

**E01 概念**

1. "你这一步用的是「两边同时乘分母」。换一个分母不同的式子，这条还成立吗？先别改答案，说说你觉得哪里可能不成立。"
2. "很多同学会在这里把「…」当成「…」。这两种说法的差别在：……。用一句话说出你的版本，再回原题改一步。"

**E02 程序**

1. "思路是对的，断在第 3 步：通分之后只加了分子，分母也相加了。先只看这一步——分母应该怎么处理？"
2. "这一步叫「先定符号再合并」。看这道同型题的这一步，然后把最后一步补上。"

**E03 粗心**

1. "这次是手快漏了单位；同类题你之前是做对的。提交前用这三项对一遍：符号、单位、抄写。然后原题再做一次。"
2. "这一步你其实会，是跳过了「先写已知」直接算。下次先写一行已知，再动笔。"

**E04 识别/转录**

1. "方法和步骤都对，是题面这一处你读成了「−3」，原题是「3」。把这题的条件、所求各读一遍，再重做一次。"
2. "这题的问法是「至少」而不是「恰好」。先把「已知/所求/限制」写成三行，再选方法。"

**通用（无证据）**

1. "这次选择还差一步。先说说你当时是怎么想的——我不猜，按你的思路找断点。"
2. "这道题你先做到哪一步了？把卡住的那一步说给我，我们从那里开始。"

### 4.3 与现有 UI 文案的兼容（只读核对，不改热区）

| 现有面 | 位置 | 现状 | 本文的建议 |
|---|---|---|---|
| 复习作答反馈 | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewSessionScreen.kt:811-838`（`RecordedAttemptFeedback`） | 只显示"选择正确/这次选择还差一步" | 保留；若要加一句，只加"邀请说出思路"，不加类别 |
| 知识点复习判定 | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/KnowledgeReviewSessionScreen.kt:619-660`（`KnowledgeQuizVerdict`） | 同上 + 拒绝原因文案 | 拒绝原因已是"为什么不计数"，与错因无关，保持 |
| 重教开场卡 | `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewSessionScreen.kt:845`（`ReTeachOpeningCard`） | "先看这段讲解，再作答" + 材料 | 材料选型按 §3 优先级；开场句可加"看完用一句话说出你原来的想法错在哪" |
| 自评 vs 本机核对 | `ReviewSessionScreen.kt:606-616` | "本机核对：选择正确/不正确。复习结果仍按你的自评记录。" | 不要在这里叠加错因话术（会把"自评"与"诊断"混在一起） |

> 这些文件都在热区（`feature/*`），本文**只给文案建议，不落码**。

### 4.4 话术的四个"不"

1. **不猜**：没有 trace 就不说原因（§3.6）。
2. **不贴**：不说"你是…型/你属于…"。
3. **不承诺**：不说"你会了/你掌握了/你进步了"（掌握度只由投影写，合同）。
4. **不堆**：一段只解决一个断点；材料只给一种（§2.3）。

---

## 5. 调度与练习建议

> 前置：本产品的调度杠杆只有五个——① FSRS 的 `desiredRetention`；② 复习队列的取题/排序（`ReviewCapacityScheduler` / `ExamQuotaPlanner` / `MissedDayRebalancePolicy`）；③ leech 门与重教注入（`ReviewPlannerV2` / `ReTeachOpening`）；④ 知识点复习入口（概念侧）；⑤ 教学材料选型（`TutorTeachingReferenceSelector`）。**没有"生成新题"这个杠杆**（合同禁止）。

### 5.1 每类的调度动作（v1 提案，全部 `PENDING_CALIBRATION`）

| 类 | 动作 | 不动作 | 依据 | 强度 |
|---|---|---|---|---|
| **E01** | ① 提高该 KC 的材料注入优先级（`MISCONCEPTION_GUIDE` 优先）；② 材料后**紧邻**同 KC 的已有题；③ 重教完成前，该题以"低剂量 + 带材料"呈现，而不是高频重做 | 不提高 `desiredRetention`；不刷同类题；不在未重教时反复出同一题 | Metcalfe 2017（A）；Atkinson 2003（A/B）；leech R5/R8（仓内） | 方向 A，剂量提案 |
| **E02** | ① 正常间隔；② 下次呈现先给该步提示，再让学生补该步；③ 该步稳定后才回到整题 | 不降 retention；不只给完整答案；不把 E02 当 E03（不加"认真点"） | VanLehn 2011（B）；Atkinson 2003（A/B）；Margulieux 2012（B） | 方向 B |
| **E03** | ① 间隔/retention **不变**；② 题面加 2–3 项自查；③ observation 短 TTL（R2 提案 14d），不晋级 | 不重教；不加量；不降 retention；不写长期标签 | arXiv:2503.04737（B）；自查综述（B）；R2 §3.3 | 方向 B，TTL 提案 |
| **E04** | ① 不改排期；② 改题面呈现（分行/标注/放大）；③ 下次呈现先做读题三问；④ 同一题立即重做以验证 | 不补知识点；不降 retention；不加同类题 | Powell & Fuchs 2018（B）；IRIS（B） | 方向 B |

### 5.2 间隔 / 交错 / 检索：能用什么，不能用什么

| 策略 | 证据 | 能用的方式 | 不能用的方式 |
|---|---|---|---|
| **间隔** | Cepeda et al. 2008：最优间隔随测试延迟增长；按比例从"1 周延迟的 20–40%"降到"1 年延迟的 5–10%"（A，摘要原文） | 用它解释"为什么这道题今天不出现"；作为 FSRS 参数的**边界参考**，不替换 FSRS | 不能直接拿"1.4–2.8 天"当所有题的下次间隔：该研究是**事实学习**（1,350+ 人学 facts），不是数学程序 |
| **交错** | Rohrer & Taylor 2007：打散练习（shuffled）优于分块（blocked），标题即结论（A）；一周后 63% vs 20% 的数字为**二手转述**（B，待验证） | 在**已有题**里混合不同题型/知识点；用"这批题里混着两类"制造辨别 | 不能据此生成"同类题"（合同禁止）；不能把交错当"加量" |
| **检索** | Roediger & Karpicke 2006：测试本身增强长期保持（"Taking a memory test not only assesses what one knows, but also enhances later retention"，A） | 优先"先作答再讲解"，而不是"先讲再做"；错题重做本身是检索 | 不能把"重做"变成"看答案后抄一遍"（会污染证据，仓内已有 reveal 语义） |
| **先错后教** | Sinha & Kapur 2021：PS-I 优于 I-PS，g=0.36 [0.20, 0.51]；**小学二至五年级呈相反趋势**（经 `leech-remediation-research.md:145-150` 转引，B/C） | 对中学生：允许先尝试再讲解（与 §2.4 一致） | 对低龄/前置不足者：先讲解/补前置；不能一刀切 |

### 5.3 与现有排期接缝（只读引用，不改）

| 接缝 | 现状（实读） | 本文建议怎么用 |
|---|---|---|
| 日容量 | `ReviewCapacityScheduler.kt:46-47`："当日容量只有一份……绝不允许各拿整份"；`:68-75` 超容量按应取数比例分同一份 `dailyCapacity` | 重教材料/自查动作**不额外占容量**（它们不是新题）；不要把 E01 的重教排成"多取题" |
| 再平衡 | `MissedDayRebalancePolicy` 按 `elapsedFraction`、`lapseCount` 排序（`MissedDayRebalancePolicy.kt:81-84`） | E02 的"同步骤"题可以借这个排序优先补，但**不要**把 E03 的 lapse 计入（CARELESS 不进掌握） |
| leech / 重教 | `RoomBackedStudyExperienceRepository.kt:471-473`：`isLeeched` 才给 `ReTeachOpening`；`ReviewPlannerV2.kt:465` leech 暂停常规排期 | E01 走这条；E02 不走（步骤错不是 leech） |
| 材料选型 | `RoomTutorTeachingReferenceRepository.kt:73-81`：`MISCONCEPTION_GUIDE`(0) → `WORKED_EXAMPLE`(1) → … → `COMPLETE_SOLUTION`(6) | E01 用 0；E02 用 1；E04 用 5 或直接改卡面；E03 不用材料 |
| 知识点复习 | `ReviewRoute.kt:119-131`：知识点复习与错题复习并列，建议先巩固概念 | E01 的"替代规则"可走知识点复习入口；E02/E04 不走 |

### 5.4 不该做的调度（每条对应一个具体失败）

| 不做 | 消灭的失败 |
|---|---|
| 因 E03 降低 `desiredRetention` 或加量 | 把一次手滑当"没学会"，用更多重复挤掉真正需要的卡；且 careless 构念不稳定，降 retention 没有依据 |
| 因 E04 触发重教/补知识点 | 让学生补已经会的东西，真正要改的读题动作没改；同时把 E04 与 E01 混为一谈（R2 §1.2 明确互斥） |
| 因 E01 生成"同类题"刷量 | 违反合同（不生成新题）；且概念错需要**不同题面**的证据，同型刷题会被"记住答案"污染 |
| 把 E02 的步骤错写成"粗心" | 学生以为只需"认真点"，步骤问题永远不修 |
| 在门关时按候选类别改排期 | 用 15–20% 误判率去改学生的学习路径；门默认关的意义就是"不拿没认证的标签行动"（`ErrorCauseGate.kt:13,56`） |

### 5.5 阈值与调参红线

1. 本文出现的所有数值（TTL 14d、2–3 项自查、材料优先级、间隔比例）**都是教学先验或文献边界**，不是本项目实测值；一律 `PENDING_CALIBRATION`。
2. **不得面向评测集调参**：话术、材料优先级、TTL、间隔策略，都不得以"某个 benchmark/评测集分数"为目标做搜索或 A/B。理由：评测集测的是分类/检索，不是"学生是否修好了这个断点"；用评测集调话术会把"说对话术"与"教好学生"混为一谈。
3. 若未来要做话术 A/B，前置条件与 R2 D-1c 同构：预注册协议、真实学习者结局指标（不是模型分数）、样本量与下界、可撤回。未满足前，只能做**定性**的可用性走查。
4. 换版（话术模板/材料优先级/extractor）必须换版本号，旧证据不继承（与 R2 P8 同口径）。

---

## 6. 与 legacy spec §7 / leech R10 的对账

`docs/specs/mastery-scheduling-spec.md:247-248` 的旧分通道：

> "CONCEPT → 重教（teaching_material 注入 + 本题降权直到重学完成）；SLIP → 低频维护；MISREAD → 归因前置/审题提醒；FORGOTTEN → 正常 FSRS 排期"

`leech-remediation-research.md:294` 的 R10 基本照抄了它，并标注"工程推断（外部未找到对应实证）"。本文的对账：

| 旧口径 | v1 口径 | 处置 | 理由 |
|---|---|---|---|
| CONCEPT → 重教 + 本题降权 | E01 → 重教 + 材料优先 + 材料后紧邻练习；**不**简单"降权" | **保留方向，改动作** | "降权"会让题消失，但概念错需要"带材料的再呈现"；Metcalfe 2025 显示"以正确解法为目标"更差，故不能只降权 |
| SLIP → 低频维护 | E02 → 正常间隔 + 步骤级提示 + 渐隐 | **改** | 旧 "SLIP" 混了程序错与粗心；v1 把程序错（E02）与粗心（E03）分开，二者调度相反 |
| MISREAD → 审题提醒 | E04 → 题面重排 + 结构化读题 + 立即重做 | **保留方向，加证据要求** | "提醒"不可执行；Powell & Fuchs 明确反对关键词式"提醒" |
| FORGOTTEN → 正常 FSRS | **退役**（R2 §1.4） | **删** | 遗忘由 `lapse_count` / retrievability 表达；再设一个 `FORGOTTEN` 会产生两份互相矛盾的真相 |
| （旧无） | E03 → 不改排期、只加自查、短 TTL | **新增** | 旧 spec 没有 careless 旁类；R2 已补 |

**一句话**：旧 §7 的"四通道"方向大致对，但把"程序错"与"粗心"混在 `SLIP` 里，这是 v1 必须拆开的地方——因为两者的调度动作相反（一个要练该步，一个什么量都别加）。

---

## 7. 反模式（每条指认它消灭的具体失败）

| # | 反模式 | 消灭的失败 |
|---|---|---|
| 1 | "你太粗心/你就是不认真" | 把一次行为固化成学生属性；触发 self 层反馈与固定归因（§2.2/2.6），下次出错时学生只会防御，不会改动作 |
| 2 | 答错就立即给完整正确答案 | 消掉"冲突"与"提取"；Metcalfe 2025 的课堂证据显示"以得到正确解法为目标"更差（B/C） |
| 3 | 概念错 → 加练 20 道同类题 | 用数量替代诊断；同型题会被"记住答案"污染，且违反"不生成新题"合同 |
| 4 | 程序错 → 说"再认真点" | 步骤问题不定位，学生以为只需态度；下次同一个步骤继续错 |
| 5 | 识别/转录错 → 去补知识点 | 补已经会的东西；真正的读题动作没改；E04 与 E01 的边界被抹掉 |
| 6 | 把四类做成四个 UI 标签/筛选项 | 直接违反产品合同三处（§1.1）；且把 15–20% 的误判暴露给学生 |
| 7 | 用评测集分数挑话术/阈值 | 把"分类器在评测集上更好"当成"学生学得更好"；本文 §5.5 的红线 |
| 8 | 重教时只放"正确答案 + 材料" | 正是被证明更差的那一类重教形态（`leech-remediation-research.md:133`） |

---

## 8. UNVERIFIED / 单一来源 / 冲突

### 8.1 未验证（不得写"有效/通过"）

| 项 | 状态 | 说明 |
|---|---|---|
| 本项目语料的错因标注 | **0 条** | 本文没有跑任何标注/评测；所有"效果"都是文献结论，不是本项目实测 |
| 任何话术 A/B | **无** | 没有真实学习者结局数据；§4 的模板是设计提案 |
| Cepeda 间隔比例迁移到数学程序 | **未验证** | 原研究是事实学习（paired facts）；数学程序的间隔曲线未测 |
| Rohrer 2007 的 63%/20% | **二手** | 三个二级来源一致，原文摘要未含数字；按 B/待验证 |
| VanLehn 的 d=0.79 | **摘要级** | ERIC 摘要片段；未读全文，不能引其分粒度结论 |
| Tippett 2010 / Powell & Fuchs 2018 / 自查综述 | **仅摘要** | 只读出版方/ERIC 摘要，未读全文 |
| Metcalfe 2025 / Potts & Shanks 2014 / Sinha & Kapur 2021 | **经仓内转引** | 见 `leech-remediation-research.md`；本轮未读原文 |
| 本文件对任何代码的影响 | **零** | 只读研究，未改任何源文件 |

### 8.2 单一来源（按"待验证"处理）

| 结论 | 来源 | 限制 |
|---|---|---|
| refutation text 是"最有效的文本手段之一" | Tippett 2010（一篇综述） | 单一综述；且主要在科学教育，数学概念迁移未验证 |
| 子目标标签改善迁移 | Margulieux et al. 2012（一篇会议论文） | 单一研究；计算教育场景 |
| careless 构念不稳定 | arXiv:2503.04737（一篇预印本） | 预印本；但 R2 已用它支撑"不晋级"，本文沿用同一限制 |
| 自查对数学的普遍收益 | 2024 系统综述（一篇） | 只读摘要；未看效应量与调节变量 |

### 8.3 冲突与处置

| 冲突 | 甲 | 乙 | 处置 |
|---|---|---|---|
| 即时 vs 延迟反馈 | 程序错要尽早纠正（VanLehn 步骤级） | Butler 2007：延迟优于即时 | **对象不同**：即时的是"步骤提示"，延迟的是"完整解释"；§2.5 两段式 |
| 间隔比例 vs FSRS | Cepeda：1 周延迟→间隔 1.4–2.8 天 | 现有 FSRS 按 R/S/D 排期 | **不替换**；Cepeda 只作边界参考，且材料类型不同（事实 vs 程序） |
| 旧 spec §7 vs R2/v1 | CONCEPT/SLIP/MISREAD/FORGOTTEN 四通道 | E01–E04 四类（FORGOTTEN 退役、CARELESS 新增） | 以 R2 + 本文 §6 为准；旧 spec 是历史口径，不回写 |
| "先错后教" vs 低龄反转 | Sinha & Kapur：PS-I 优于 I-PS | 同文：小学二至五年级相反 | 按学段分流；本产品用户学段未定，**不得**一刀切 |

---

## 9. 来源清单（取用日期 2026-11-03）

**外部（采纳）**

1. Hattie, J., & Timperley, H. (2007). The Power of Feedback. *Review of Educational Research* 77(1), 81–112. DOI `10.3102/003465430298487`；JSTOR 页面 <https://www.jstor.org/stable/4624888>；PDF 镜像 <https://assess.ucr.edu/sites/default/files/2019-02/hattietimperley_2007.pdf>。**A**。采纳：三问 + 四层。核验：Crossref 摘要全文实查；四层原文取自 JSTOR 页面在检索摘要中的片段（**未打开 JSTOR 正文**，PDF 直取被内容类型拦截）。
2. Shute, V. J. (2008). Focus on Formative Feedback. *Review of Educational Research* 78(1), 153–189. DOI `10.3102/0034654307313795`。**A**。采纳：nonevaluative/supportive/timely/specific 四词。核验：Crossref 摘要实读。
3. Kluger, A. N., & DeNisi, A. (1996). The effects of feedback interventions on performance. *Psychological Bulletin* 119(2), 254–284. DOI `10.1037/0033-2909.119.2.254`。**A**。采纳：d=.41、>1/3 负效应。核验：Crossref 元数据实查；两句数字取自 PsycNet 页面在检索摘要中的片段。
4. Metcalfe, J. (2017). Learning from Errors. *Annual Review of Psychology* 68, 465–489. DOI `10.1146/annurev-psych-010416-044022`。**A**。采纳：纠错需分析推理、hypercorrection、低风险允许先错。核验：Crossref 摘要全文实读。
5. Butler, A. C., Karpicke, J. D., & Roediger, H. L. (2007). The effect of type and timing of feedback on learning from multiple-choice tests. *JEP: Applied* 13(4), 273–281. DOI `10.1037/1076-898X.13.4.273`。**A**。采纳：延迟优于即时；两种格式无差异。核验：Ovid 摘要页实读 + Crossref 元数据。
6. Cepeda, N. J., Vul, E., Rohrer, D., Wixted, J. T., & Pashler, H. (2008). Spacing Effects in Learning: A Temporal Ridgeline of Optimal Retention. *Psychological Science* 19(11), 1095–1102. DOI `10.1111/j.1467-9280.2008.02209.x`。**A**。采纳：最优间隔随延迟增长；20–40%（1 周）→5–10%（1 年）。核验：Crossref 摘要全文实读。
7. Rohrer, D., & Taylor, K. (2007). The shuffling of mathematics problems improves learning. *Instructional Science* 35, 481–498. DOI `10.1007/s11251-007-9015-8`。**A（标题级）**。采纳：交错优于分块。限制：63%/20% 数字为二手（B），Crossref 无摘要、本轮未读全文。核验：Crossref 元数据实查 + 出版方摘要片段。
8. Roediger, H. L., & Karpicke, J. D. (2006). Test-Enhanced Learning. *Psychological Science* 17(3), 249–255. DOI `10.1111/j.1467-9280.2006.01693.x`。**A**。采纳：测试本身增强长期保持。核验：Crossref 元数据实查 + 出版方摘要片段。
9. Posner, G. J., Strike, K. A., Hewson, P. W., & Gertzog, W. A. (1982). Accommodation of a scientific conception. *Science Education* 66(2), 211–227. DOI `10.1002/sce.3730660207`。**A**。采纳：概念改变四条件。核验：Crossref 元数据实查；四条件原文取自 SEDL 书目页在检索摘要中的片段。
10. Tippett, C. D. (2010). Refutation Text in Science Education: A Review of Two Decades of Research. *IJSM Ed* 8, 951–970. DOI `10.1007/s10763-010-9203-x`。**B**（单一综述，仅摘要）。采纳：refutation 结构有效。限制：科学教育为主。
11. Atkinson, R. K., Renkl, A., & Merrill, M. M. (2003). Transitioning From Studying Examples to Solving Problems. *JEP* 95(4), 774–783. DOI `10.1037/0022-0663.95.4.774`。**A/B**。采纳：渐隐对近迁移可靠、远迁移需 self-explanation。核验：Crossref 元数据实查 + PsycNet 页面检索摘要片段（Crossref 无摘要）。
12. Margulieux, L. E., Guzdial, M., & Catrambone, R. (2012). Subgoal-labeled instructional material improves performance and transfer. *ICER '12*. DOI `10.1145/2361276.2361291`。**B**（单一研究，标题级）。采纳：子目标标签。核验：Crossref 元数据实查（无摘要，未读全文）。
13. Powell, S. R., & Fuchs, L. S. (2018). Effective Word-Problem Instruction: Using Schemas to Facilitate Mathematical Reasoning. *TEACHING Exceptional Children* 51(1), 31–42. DOI `10.1177/0040059918777250`。**B**。采纳：按结构读题；反对关键词法。核验：Crossref 元数据实查 + ERIC 页面检索摘要片段（`https://eric.ed.gov/?id=EJ1190453`；ERIC 正文页本轮超时未取）。
14. 自查系统综述 (2024). *Exceptional Children* 90(3)。DOI `10.1177/00144029241230322`。**B**（仅摘要）。采纳：自查在数学上对普通/特殊需要学生普遍有益。限制：未看效应量与调节变量。
15. Mueller, C. M., & Dweck, C. S. (1998). Praise for intelligence can undermine children's motivation and performance. *JPSP* 75(1), 33–52. DOI `10.1037/0022-3514.75.1.33`。**A**。采纳：过程表扬优于能力表扬。核验：Crossref 元数据实查 + PsycNet 页面检索摘要片段。
16. VanLehn, K. (2011). The Relative Effectiveness of Human Tutoring, Intelligent Tutoring Systems, and Other Tutoring Systems. *Educational Psychologist* 46(4), 197–221. DOI `10.1080/00461520.2011.611369`。**B**（摘要级）。采纳：人辅导 d=0.79（远低于流传的 2.0）；ITS 多用步骤级交互。限制：Crossref 无摘要、ERIC 页面本轮超时；仅取到检索摘要片段，不引其分粒度结论。
17. arXiv:2503.04737, "Carelessness Detection using Performance Factor Analysis"（2025）。**B（预印本）**。采纳：careless 操作化不稳定。限制：单一预印本。经 R2 S9 转引。
18. IRIS Center (Vanderbilt Peabody), *Mathematics: Identifying and Addressing Student Errors*。**B**（机构教学资源）。采纳：三主类 + careless 旁类；3–5 次成模式；Step 3 让学生解释逻辑。经 R2 §1.1 引。
19. McNichols, H., Zhang, M., & Lan, A. (2023). Algebra Error Classification with Large Language Models. AIED 2023。**A/B**。采纳：细粒度自动分类上限 ~80.7%/85.9%。经 R2 S7 引。

**外部（拒绝/不采纳其用法）**

20. 二手博客/学习技巧站对 Rohrer 2007 的"63% vs 20%"转述：**不单独作为事实**，只作 B 级待验证数字；不作为阈值依据。
21. 关键词法（"看到'一共'就加"）类教学建议：**拒绝**。Powell & Fuchs 2018 明确其"discourages mathematical reasoning and frequently produces incorrect answers"。

**GitHub（检查后不采纳）**

22. `CAHLR/OATutor`（MIT，273 stars，pushed 2026-11-01，JavaScript/React+Firebase，BKT）。检查结论：它是一个完整的 Web ITS；`gh api search/code` 在仓内命中 81 个 `hint` 引用（**未逐文件阅读**），但未发现"错因类别 → 反馈/调度"的映射，且技术栈与本仓 Android/Kotlin 不兼容。**不采纳为代码依赖**；仅作"步骤级提示"形态的旁证。核验：`gh repo view` + `gh api search/code`。检索"intelligent tutoring system feedback"/"error analysis math education"返回空集——按纪律，**空集不等于不存在**，只说明本次检索未命中可直接复用的实现。

**仓内（约束与既有结论）**

23. `docs/research/2026-11-02-error-taxonomy.md`：§1.2 判据、§1.3 决策阶梯、§3.3 晋级规则、§3.6 三通道、§5 门、§6 撤销（本文的分类前置）。
24. `docs/product-information-architecture.md:287,408,409`；`docs/m1-exhaustive-product-contract.md:95,259`；`docs/model-first-product-boundaries.md:9`（错因不可见、不生成新题、不暴露算法名）。
25. `docs/specs/mastery-scheduling-spec.md:247-248`（旧四通道，本文 §6 对账）。
26. `docs/research/leech-remediation-research.md:107-133,145-150,279-309`（纠错、重教形态、productive failure、R1–R14）。
27. `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ErrorCause.kt:25-44,55-89`（E01–E04 与细码）。
28. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ErrorCauseGate.kt:38-43,56,69`（默认关、CARELESS/SELF_REPORT 永不放行）。
29. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorTeachingReferenceRepository.kt:52-81`（材料优先级）。
30. `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ProblemCatalog.kt:177-185`（材料类型全集）。
31. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/ReviewCapacityScheduler.kt:46-47,68-75`（单份日容量）。
32. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/MissedDayRebalancePolicy.kt:81-84`（再平衡排序）。
33. `feature/review/src/main/kotlin/com/tingyun/smartmistakebook/feature/review/ReviewSessionScreen.kt:606-616,811-838,845`；`KnowledgeReviewSessionScreen.kt:619-660`（现有反馈/重教文案，热区，只读）。

---

## 10. 本文的消费方与"不做什么"

### 10.1 谁可以用它

| 消费方 | 用哪一节 | 用法 |
|---|---|---|
| 产品文案批（未来 UI） | §4、附录 A | 取模板与禁用词；不新增分类 UI |
| 调度批（WS3 后续） | §5.1–5.4 | 按类动作改材料/提示/排期；阈值须另行标定 |
| 错因端到端（WS6） | §3.6、§5.3 | 门关时的降级话术；撤回后的文案 |
| 讲题/重教（leech R4–R8） | §3.1–3.2、§6 | 材料类型与"紧邻练习"的对应 |
| 记录/台账 | §0、§8 | 结论与未验证项 |

### 10.2 本文不做

- 不改任何源码、schema、构建配置；不碰热区（`feature/*`、`core/ui`、`core/export`、`core/domain`、settings、Application/Root）。
- 不新增错因类别、不改 `ErrorCause` 枚举、不改 R2 的 D-1c 门。
- 不给出"本项目有效"的任何结论：没有标注样本，没有 A/B。
- 不面向任何评测集调参（§5.5）。

---

## 附录 A：禁用词 → 替代表（完整）

| 禁用 | 层级 | 为什么 | 替代 |
|---|---|---|---|
| 你真粗心 / 你太马虎 | self | 人格标签；careless 构念不稳定 | 这一次手快漏了「…」 |
| 你真聪明 / 你数学好 | self | 能力表扬伤动机（Mueller & Dweck） | 你这个顺序/这个拆法是对的 |
| 这么简单都错 | self/evaluative | 评价而非信息 | 这一步只差一个符号 |
| 概念错 / 程序错 / 审题错 | 类别 | 违反"错因不可见" | 这一步的规则 / 这一步的顺序 / 读成了… |
| 你没掌握 / 你已经会了 | 掌握承诺 | 掌握度只由投影写 | 这道题你现在能做到… |
| 多练几遍 / 多做同类题 | 不可执行+合同 | 不能生成新题 | 现在只练这一步 |
| 按遗忘曲线 / 按掌握模型 | 算法名 | 合同禁止暴露 | 过几天再见面 / 下次先做这道 |
| 你要更认真 | 不可执行 | 无具体动作 | 提交前对这三项：符号、单位、抄写 |
| 你怎么又错了 | self/历史 | 把单次变成模式 | 这次和上次断的不是同一处 |

## 附录 B：四类 × 证据强度速查

| 类 | 反馈方向 | 练习/调度方向 | 证据最强的一条 | 最弱的一环 |
|---|---|---|---|---|
| E01 | 冲突 + 替代规则 | 重教 → 紧邻同 KC 已有题 | Posner 1982（A）/ Metcalfe 2017（A） | refutation 在数学的迁移（B） |
| E02 | 定位步骤 + 例题渐隐 | 只练该步 → 同型已有题 | Atkinson 2003（A/B） | 子目标标签（B，单一研究） |
| E03 | 事件与能力分开 + 自查 | 不加量、不改间隔 | arXiv:2503.04737（B，构念不稳） | 自查对"数学准确性"的效应量（仅摘要） |
| E04 | 题面重排 + 结构化读题 | 立即重做、不改排期 | Powell & Fuchs 2018（B） | 数学读题干预的直接实验（未找到） |
