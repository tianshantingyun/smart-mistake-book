# R1/R2 深度研究 · 标签准确性的系统侧自纠 与 未知历史置信度的处置（2026-10-11）

> 背景：KF-30 落地（K3，`docs/research/2026-10-10-k3-completion-record.md`）之后，裁定记录
> `docs/research/2026-10-09-kf30-semantics-rulings.md` 留了两个**必须结合外部高质资料**的研究项：
> - **R1 · 标签准确性自证/自纠**：系统自己发现并清掉「模型看走眼」造成的错标签，不靠 add-only 永久留存、
>   不靠用户手改（用户设计底线：「不要把这种问题交给用户判断，他自己应该准确」）。
> - **R2 · 未知历史置信度的正确处置**：Q2 的 NULL 口径（按门值参与比较）只是 **interim**，正解待定。
>
> 方法（用户硬约束）：外部检索优先 GitHub 高质相似项目 → 官方文档 → 论文/社区经验；**交叉验证**与
> **证据分级**；通道失败只是"这次没取到"，不作"不存在"。**方法论告警照录**：本轮检索期间出现多次引擎熔断/
> 空返回与 WebFetch 解析失败（PDF、403、反爬），凡未取到原文的一律标注"未获取/单一来源待验证"，未以记忆补全。

## 0. 结论摘要（两句）

1. **没有任何外部来源支持"零人工、零金标、纯系统自纠可靠"**——抓到的每个方案都至少需要下列之一：
   交叉验证得到的 out-of-sample 概率 + 独立模型（cleanlab）／良校准置信度 + 足量样本（NannyML）／
   人工核验过的 ground truth 子集（Snorkel Flow）／明确保留人工复核环节（ObjectLab、i2V）；
   并且有两条**反向证据**（自纠会自我强化错误、无外部反馈的自纠可使表现变差）。→ 我们的自纠机制应定位为
   **发现 + 降级/告警（不可判定当一等输出）**，**不做静默自动改写/删除**。
2. **"未知"不该被解释成一个看似精确的值**：缺失数据文献（Altman & Bland 1995、arXiv 2511.01196、
   Steck 2012、Marlin & Zemel 2011）一致支持"缺失 ≠ 低分、更 ≠ 某个具体估计值"；可审计场景（arXiv 2609.26977）
   要求等值/替换规则**显式、确定、可披露**。→ R2 的最小正解 = 把 0.78 从"估计值"**改判为显式策略门**，
   且让"存量未知"这一事实**可区分、可落日志**。

## 1. R1 · 外部证据（按证据等级）

| # | 来源 | 类型 | 机制/要点（摘录） | 等级 |
|---|---|---|---|---|
| 1 | [cleanlab `filter` 官方文档](https://docs.cleanlab.ai/stable/cleanlab/filter.html) | 官方文档 | `find_label_issues` 输出**排序过的疑似标签问题**；排序分 `normalized_margin`（`p(label=k) − max(p(label≠k))`）/`self_confidence`/`confidence_weighted_entropy`；"most accurate when computed based on **out-of-sample** pred_probs" | 官方 |
| 2 | [cleanlab FAQ](https://docs.cleanlab.ai/stable/tutorials/faq.html) | 官方文档 | "**We caution against just blindly taking the predicted label**…many of these suggestions may be wrong!"；自动改只对训练集"fair game"，评估/测试集应"manually fix…via careful review"；`min_examples_per_class` 保护稀有类 | 官方 |
| 3 | [Northcutt et al., Confident Learning (JAIR'21)](https://arxiv.org/abs/1911.00068) | 论文 | 三原理：pruning noisy data / probabilistic-threshold counting / ranking；"directly estimate the joint distribution between noisy and uncorrupted labels"，"not coupled to a specific data modality or model" | 论文（同行评议） |
| 4 | [作者博客（confident joint 阈值）](https://l7.curtisnorthcutt.com/confident-learning) | 博客（同源） | "thresholds…average predicted probability of examples in that class"；"does not require any true labels" | 同源补充 |
| 5 | [Pervasive Label Errors (NeurIPS'21 D&B)](https://arxiv.org/abs/2103.14749) | 论文 | 10 个数据集平均 ≥3.3% 错误；ImageNet 测试集 6% | 论文 |
| 6 | [Snorkel LabelModel 官方 API](https://snorkel.readthedocs.io/en/v0.9.3/packages/_autosummary/labeling/snorkel.labeling.LabelModel.html) | 官方文档 | 学习 `P(lf | Y)` 重加权多弱标注源；`Y_dev`（金标 dev 集）**可选但强烈建议** | 官方 |
| 7 | [Snorkel Flow 博客](https://snorkel.ai/blog/how-does-the-snorkel-flow-label-model-work/) | 厂商博客 | LF 投票/弃权建模；"score higher precision on our **ground truth** data…get a stronger vote" | 单一来源待验证 |
| 8 | [Co-teaching (NeurIPS'18)](https://arxiv.org/abs/1804.06872) | 论文 | "small-loss instances are more likely to be correctly labeled"；双网络互换选择 | 论文 |
| 9 | [Arazo et al. (IJCNN'20)](https://arxiv.org/abs/1908.02983) | 论文 | "naive pseudo-labeling **overfits to incorrect pseudo-labels** due to confirmation bias"；需 mixup + 每 batch 保底真标 | 论文 |
| 10 | [Huang et al., LLMs Cannot Self-Correct Reasoning Yet (ICLR'24)](https://arxiv.org/abs/2310.01798) | 论文 | "**LLMs struggle to self-correct their responses without external feedback**"；"performance even **degrades** after self-correction" | 论文 |
| 11 | [Chen et al., Self-training Ensembles (NeurIPS'21)](https://arxiv.org/abs/2106.15728v4) | 论文 | 无标注同时做**准确率估计**与**单条错误识别**；有可证保证；需**模型集成** | 论文 |
| 12 | [Ensemble Disagreement as Proxy (ACL GEM'23)](https://aclanthology.org/2023.gem-1.5/) | 论文 | "ensemble disagreement scores work well as a **proxy for human labeling**"，比 silver labels 平均好 13.8% | 论文 |
| 13 | [NannyML CBPE 官方文档](https://nannyml.readthedocs.io/en/v0.4.1/how_it_works/performance_estimation.html) | 开源项目文档 | 用**校准过的**置信度反推性能；假设清单明写"**there is no way to identify concept drift without any ground truth data**" | 官方 |
| 14 | [Guo et al., On Calibration (ICML'17)](https://arxiv.org/abs/1706.04599) | 论文 | "modern neural networks…are **poorly calibrated**"；temperature scaling 有效 | 论文 |
| 15 | [i2V 生产级伪标签纠正流水线](https://medium.com/@i2vsys/recursive-clustering-based-pseudo-label-correction-pipeline-ec700d5643fe) | 工程博客（生产） | 混合簇"**deferred processing**"；只把歧义项交人工；15 万图规模减人工 80–85% | 单一来源（工程） |
| 16 | [ObjectLab (ICML W'23)](https://arxiv.org/abs/2309.00832) | 论文 | 用已有模型给每条**已有标签打分**并排序，"prioritized for label review/correction" | 论文 |
| 17 | [远监督 NER 噪声纠正 (EMNLP'21)](https://aclanthology.org/2021.emnlp-main.810/) | 论文 | 远监督标签"incomplete and noisy"；做法 = loss 调整 + **noisy label removal step** + 自训练 | 论文 |
| 18 | [噪声标签方法综述 (TNNLS'22)](https://arxiv.org/abs/2007.08199) | 综述 | 62 种方法分 5 族 | 论文 |

### 1.1 R1 的交叉验证结论

| 结论 | 来源 | 判定 |
|---|---|---|
| 不依赖金标即可**发现单条坏标签**（用模型概率 + 阈值计数 + 排序） | 1–5（同源）＋ 16（独立、异模态）＋ 11 ＋ 13 | 方向一致；CL 算法细节**同源**，非跨主体独立验证 |
| **置信度不能直接当正确概率**，须先校准 | 14 ＋ 13 | **一致（≥2 独立）** |
| **多视角/集成不一致度**可作质量或错误的代理信号 | 11 ＋ 12 ＋ 13 | **一致（≥2 独立）** |
| **盲目自动纠正危险**：应"排序 → 定向复核"，"defer/保留歧义" | 2 ＋ 15 ＋ 16 ＋ 7/6 | 实践**一致（≥2 独立）**；量化实验只有 2 一家 |
| **自纠会自我强化错误**；无外部反馈的自纠可使表现变差 | 9 ＋ 10 | **一致（≥2 独立，跨领域）** |
| 样本选择＋双模型互教是成熟的噪声处理族 | 8 ＋ 18 ＋ 9 | **一致**（方法族成立） |
| 弱监督标签模型需要**多标注源**、可选金标 dev 集 | 6 ＋ 7 | 同源（Snorkel 一家）＝ **单一来源待验证** |

### 1.2 R1 的**最重要负面结论**（照录）

- **没有与本场景同构的先例**：抓到的所有方案都是「**离线数据集级清洗**」；**没有任何来源**讨论
  「**增量写入的单条标签账本 + add-only 合并语义**导致错误标签长期驻留」。我们缺陷②（"本轮没提 ≠ 该删"）
  在外部资料中**零命中**——**属我们自身的设计语义问题，必须自己建模**。
- **没有任何"零人工、零金标、纯系统自纠可靠"的实现或论文**（多方独立口径同向）。

### 1.3 R1 候选机制（按可行性排序，全部定位为"发现+降级/告警"）

1. **多轮重跑一致性/不一致告警**：同一条记录不同种子/温度重跑 N 次，比对标签分布；**非对称**策略——
   不一致 ⇒ 降级为"存疑"并进待复核；一致 ⇒ **只更新"最后验证时间"、不提升信任度**（否则落入 confirmation bias）。
   风险：系统性错误不可检出（共识≠正确）；需固定推理配置并先做空转标定。
2. **候选竞争关系（top1/top2 margin、并列、候选集大小）+ 置信度校准审计**：可直接沿用 cleanlab 的
   `normalized_margin` / `confidence_weighted_entropy` 语义与 NannyML 的"置信度→错误概率"框架。
   风险：分数被接受门截断（选择偏差）、LLM 自报分未校准（须先分桶统计实测正确率）、稀有类保护。
   **前置**：需要把"候选集与分数"（含被拒候选）落库——当前未存。
3. **下游使用情况 + 漂移审计（只作触发器）**：标签是否被复习/检索用到、条目数是否长期为 1、时间窗分布漂移。
   弱信号、易混淆（"没被用"≠"错"）；NannyML 明示无金标时漂移不可识别 → **只提示"值得看一眼"，不作判定**。

## 2. R2 · 外部证据（按证据等级）

| # | 来源 | 类型 | 要点（摘录） | 等级 |
|---|---|---|---|---|
| 20 | [Altman & Bland 1995 (BMJ, Wayback)](https://web.archive.org/web/2020/https://www.bmj.com/content/311/7003/485) | 经典注记 | "**absence of evidence means simply that there is no information at all**"；不显著 ≠ 无差异 | 经典 |
| 21 | [缺失插补综述 (arXiv 2511.01196)](https://arxiv.org/html/2511.01196v1) | 综述 | 单值插补"doesn't reflect the uncertainty…**overly confident**"；零填充"only viable when the proportion of missing data is sufficiently small"；MNAR 定义 | 论文 |
| 22 | [Steck, CF and the MAR Assumption (arXiv 1206.5267)](https://arxiv.org/abs/1206.5267) | 论文 | "未评分按随机缺失处理"的默认假设**不成立**（缺失与未观测值相关），显式建模缺失机制可显著改善 | 论文 |
| 23 | [Marlin & Zemel (IJCAI'11)](https://www.ijcai.org/Abstract/11/447) | 论文 | 高比例缺失与有效估计假设之间存在"**basic incompatibility**" | 论文 |
| 24 | [openEBGM / GPS（R Journal，作者 FDA）](https://journal.r-project.org/articles/RJ-2017-063/) | 同行评议期刊 | 经验贝叶斯把少样本比例**向先验收缩**；"**Low percentiles (e.g. 5th or 10th) can be used as conservative scores**"；"single counts are typically not considered signals"；承认"convergence is not guaranteed" | 期刊 |
| 25 | [openEBGM 官方文档 (CRAN)](https://search.r-project.org/CRAN/refmans/openEBGM/html/openEBGM-package.html) | 官方文档 | 超参数由边缘分布（负二项）似然估计（梯度/EM）；维护者 `@fda.hhs.gov` | 官方（与 24 同一来源链） |
| 26 | [Empirical Bayes](https://en.wikipedia.org/wiki/Empirical_Bayes_method) / [Beta-binomial](https://en.wikipedia.org/wiki/Beta-binomial_distribution) / [Bayesian average](https://en.wikipedia.org/wiki/Bayesian_average) | 百科 | 先验**从数据估**；无观测时排序分退化为总体均值 `(Cm+Σx)/(C+n)`；矩估计可能为负 | 百科（辅助） |
| 27 | [TrueSkill (Wikipedia)](https://en.wikipedia.org/wiki/TrueSkill) | 百科（生产系统） | 未知选手 μ0=25、σ0=25/3；对外展示取 **1% 下分位 ≈ μ−3σ** ⇒ 初始展示分 0（**悲观呈现**） | 百科（生产实据） |
| 28 | [Evan Miller, How Not To Sort (Wayback)](https://web.archive.org/web/2023/https://www.evanmiller.org/how-not-to-sort-by-average-rating.html) | 业界经典 | 少票平均分不可靠，按 **Wilson 下界**排序 | 经典 |
| 29 | [Sutton & Barto §2.7 乐观初始化 (Wayback)](https://web.archive.org/web/2020/http://incompleteideas.net/book/ebook/node21.html) | 教材 | 乐观初始化是**探索**手段（目标导向，非对错） | 教材 |
| 30 | [Jump Start or False Start? (arXiv 2604.02527)](https://arxiv.org/abs/2604.02527) | 预印本 | **热启动/先验有害**：损坏 >40% 后失去优势、>50% 更差；系统性错位时"LLM 先验导致更高 regret" | 预印本（单一来源待验证） |
| 31 | [Tie Handling Is Part of the Evaluation Protocol (arXiv 2609.26977)](https://arxiv.org/html/2609.26977/v1) | 预印本+工件 | "**tie-breaking rule becomes part of the ranking**"；须用披露种子+ID 的确定性哈希破平并**披露**；仅换破平规则 NDCG@10 从 0.8474 → 0.1702 | 预印本（附复现工件） |
| 32 | [Cold start (Wikipedia)](https://en.wikipedia.org/wiki/Cold_start_(recommender_systems)) / [LightFM](https://github.com/lyst/lightfm) | 百科/开源 | 冷条目靠**特征**给出表示（而非拍常数） | 百科/开源 |

### 2.1 R2 的交叉验证结论

| 结论 | 来源 | 判定 |
|---|---|---|
| 未知/缺失**不能用某个确定值顶替**；单值顶替低估不确定性、过度自信 | 21 ＋ 24 ＋ 28 ＋ 20 | **一致（4 独立）** |
| "没有观测" ≠ "低分/不存在" | 20 ＋ 21 ＋ 22 ＋ 23 | **一致（4 独立）** |
| 未知/少样本的合理默认是**向总体先验收缩**，且先验可**从数据估计** | 24 ＋ 25 ＋ 26 | **一致**（EB 与 openEBGM 独立；Bayesian average 为工程简化） |
| 展示/排序可用**保守下界** | 27 ＋ 28 ＋ 24 | **一致（4 独立、跨领域）** |
| 乐观 vs 悲观初始化是**目标选择**；错位先验可能比无先验更差 | 29 ＋ 27 ＋ 30 | **方向性一致** |
| 并入/等值决策必须**显式、确定、可披露、可复算** | 31 ＋（行业博客） | **一致（2 独立）** |
| "未知**存量行**在竞争性替换中如何处置"的**专门文献** | — | **未找到**（映射属本次推断） |

## 3. 与本仓现状的关系（K3 已落 vs 两项研究）

- K3 已落：`accepted_confidence` 列（NULL=未知）、写侧真值/NULL、合并侧 `record.acceptedConfidence ?: CLASSIFICATION_ACCEPTANCE_CONFIDENCE`
  ——即 **NULL 现在被隐式读成门值 0.78**，与"恰好 0.78 的已知值"**不可区分**。
- R2 的落地缺口（据外部证据）：**0.78 应被声明为"策略门"而非"估计值"**；"存量未知"应**可区分、可落理由**
  （外部口径：31 的"显式/确定/可披露" ＋ 20/21 的"缺失 ≠ 精确值"）。
- R1 的落地缺口：我们**目前没有可用于自纠的外部信号**（无金标、无集成、无 out-of-sample 概率、候选分未落库）；
  外部共识是"**先有信号，再谈自纠**"，且只有非对称（只降级、不提升）的自纠才不自我强化。

## 4. 落地方案（K5 三片；本轮先研究，实施按批准的计划执行）

- **K5-A（最小片，低风险）· R2 口径 1**：命名策略常量（`UNKNOWN_INCUMBENT_COMPARISON = GATE`）+ 合并 KDoc
  声明"策略而非估计"；判定按「已知/未知」**分支**并带 `reason`（`incumbent_known` / `incumbent_unknown`）；
  新增断言把"按策略替换"与"按分数替换"分开；零 schema、零算法 bump。
- **K5-B（需信号面扩展）· R1 机制 ②**：候选集与分数（含被拒候选）落库（**schema 65**）→ 按 cleanlab
  `normalized_margin` 语义派生 margin，标记极小 margin 的已接受标签为"存疑"；并做**校准审计**
  （分桶统计"自报各档置信度的实测正确率"——**我们的可用 ground truth = 用户纠正**，须在口径里写明这是代理金标）。
- **K5-C（最重）· R1 机制 ①**：多轮重跑一致性 + **非对称**降级（不一致 ⇒ 存疑；一致 ⇒ 只更新"最后验证时间"）。
  依赖 K5-B 的校准结论。
- **一律不做**：静默自动改写/删除标签（无外部支持；9/10 两条反向证据）。

## 5. 未获取 / UNVERIFIED（照录两份调研的"未找到"清单）

- 未获取：cleanlab 论文正文的 confident joint 阈值公式原文；conformal prediction 的"空集/并列集=不确定"表述；
  selective classification / learning-to-abstain 来源（检索被熔断）；Reddit confidence sort 一手说明；
  Netflix 冷启动博客原文（403）；Duke EB 讲义正文（PDF 不可解析）；Glicko 的"R−2RD 展示公式"（正文无此式）；
  SR 11-7 原文（PDF）；OCC Bulletin 2026-13 与 SR 11-7 的替代关系（仅核到 OCC 页面可读部分）。
- 单一来源待验证：Snorkel"金标 dev 集提升投票权重"；arXiv 2604.02527 的量化阈值；γ–Poisson 先验参数解读。
- **零命中（≠不存在）**：与本场景同构的"增量单条标签账本 + add-only"研究；"未知存量行在竞争性替换中的处置"
  专门文献；"auditable threshold replacement"/"monotonic replacement rule" 术语。
