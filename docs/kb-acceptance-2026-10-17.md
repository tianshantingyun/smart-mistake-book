# 教材目录覆盖验收（F14 双口径：主线 426 条 / 全量 661 条）

> 文书日期：**2026-10-20**（v18 补记 2026-10-21；文件名沿用 2026-10-17 批次验收槽位）
> 验收对象：v18 现包 `build/kb-staging/moe-2025-four-subjects-v1.json`（`moe-2025-update-manifest.json` version=**18**，contentVersion=**d82a05607bdb622a**；主包 sha256 `f6098c41d4901d56bd33106b205e7489fa6bf078d3853413c674976b5b84386c`，425 主题 / 3,245 知识点 / 25 件 sidecar 64,973 材料——本报告实算）
> 验收口径（**主口径**，F14）：**主线课标内容要求 = 必修 + 选择性必修**，分母 **204 + 222 = 426**；**COVERED 374/426 = 87.79%**（PARTIAL 43、NOT_COVERED 9、UNDECIDED 0）。**选修 235 条单列报告**（COVERED 16 / PARTIAL 30 / NOT_COVERED 189），不计入主口径。
> 旧口径（**附录 A** 保留，双口径并列）：全量 661 条 **COVERED 390/661 = 59.00%**（PARTIAL 73、NOT_COVERED 198、UNDECIDED 0）。
> 性质：本验收核的是**教材目录（章/节名与节点位置）↔ 现包主题树**及**课标内容要求 status**；**不是「题库全覆盖」承诺**，也不是教材正文内容验收。

## 1. 结论

1. **主口径（F14：必修 + 选择性必修 = 426 条）**：**COVERED 374/426 = 87.79%**；PARTIAL **43/426 = 10.09%**；NOT_COVERED **9/426 = 2.11%**；UNDECIDED **0**。按已发布规则同时公布分子、分母与 UNDECIDED 绝对数（`knowledge-production/kb-coverage-alignment-2026-v2.json` 的 `publishedRule`）。
2. **主线分科（COVERED/该科主线总数）**：数学 **127/156 = 81.41%**；物理 **78/87 = 89.66%**；化学 **58/63 = 92.06%**；生物学 **111/120 = 92.50%**。
3. **选修 235 条（单列，不进主口径）**：COVERED **16/235 = 6.81%**；PARTIAL **30/235 = 12.77%**；NOT_COVERED **189/235 = 80.43%**；UNDECIDED **0**。189 条逐条见 `docs/research/2026-10-13-elective-coverage-report.md`，本报告只复算计数与构成。
4. **选修 NOT_COVERED 189 的构成**：按学科 MATH **125/189 = 66.14%**、PHYSICS **42/189 = 22.22%**、CHEMISTRY **7/189 = 3.70%**、BIOLOGY **15/189 = 7.94%**；按理由 **未设节点 175/189 = 92.59%**、**非知识点对象 13/189 = 6.88%**、**E 类自主课程 1/189 = 0.53%**。子模块构成见 §3.1。
5. **主线 NOT_COVERED 9 的构成与处置**：MATH 6（必修 3 + 选择性必修 3）、PHYSICS 1（必修）、CHEMISTRY 1（必修）、BIOLOGY 1（选择性必修）；理由 7 条非知识点对象 + 2 条未设节点。**9 条与 `knowledge-production/f14-waivers.csv` 的 9 行逐条同集（本报告实算 `same set=True`）**：F14 要求的「逐条补料或登记豁免」按批次10 处置记录已闭环（47 条：COVERED 32 / 豁免 9 / 补料 6＝SUPPLY_EXISTING 2 + SUPPLY_NEW 4，见 `docs/kb-batch10-2026-10-13.md` §2）。**豁免不等于 COVERED**：9 条仍按 NOT_COVERED 计入分母。
6. **教材目录侧（v16 终态台账；v18 结构计数复核不变）**：官方 **340 节 340/340 有落点**（0 无落点、0 不可解析）；官方 **90 章 90/90 带官方章名**；历史缺失 **13 节 13/13 已补建同名节位**（7 节 0 点/0 材料空壳＝化学 5 + 生物 2、6 节已挂点 18 点/材料 197 条——材料数据逐节台账）。20 行非节单列，不进入 340 分母。
7. **一句话**：**主线（必修+选择性必修）覆盖 374/426（87.79%），9 条缺口全部登记豁免；选修 235 条单列，NOT_COVERED 189 条（80.43%）集中在数学选修 A/B 与物理选修 1/2/3。旧全量口径 390/661（59.00%）保留在附录 A，不得与主口径混用。教材目录结构已 340/340 有落点、90/90 章名，但册位偏差、点级归属与 7 个空壳节仍未闭合。**

## 2. 办法

### 2.1 目录只读对照（不读教材正文、原文不入库）

- **官方节表**：四份 W-7 报告（`docs/kb-textbook-toc-align/{CHEMISTRY,BIOLOGY,PHYSICS,MATH}.md`，2026-09-21）逐册表，共 **21 册 / 90 章 / 340 官方节 + 20 非节行 = 360 行**。W-7 报告自述口径：视觉读人教社官方电子教材目录页，**页图取完即删、未留存、未抄正文**（见 `PHYSICS.md`/`BIOLOGY.md` 文首）。
- **现包**：`build/kb-staging/moe-2025-four-subjects-v1.json`，**v18**（manifest version=18，contentVersion `d82a05607bdb622a`），**425 主题 / 3,245 知识点**；sidecar 25 件共 **64,973 材料**（化学 18,307 / 生物学 13,704 / 物理 18,494 / 数学 14,468）。
- **逐节 join 顺序**：官方节同名主题（名或 slug 末段）→ 同名/含节名点所在主题 → FINAL §3「现包现状」指认覆盖主题 → 官方章容器；同级多落点全部列出。台账自检：落点缺失 0、材料孤儿绑定 0、课标 target 悬空 0（`SECTION-BY-SECTION-LEDGER.md` §1 自检表）。
- **章名判定**：以四份 W-7 报告的官方章名为准。`reference/standard_chapter_names.json` 仍有 5 处与官方不同（生物 2：选必1 第一章缺「人体的」、选必2 第四章旧名「生态环境的保护」；物理 3：必修三第十二章多「与可持续发展」、选必三第二章少顿号、选必三第五章多「和核反应」），已在 `PHYSICS.md`/`BIOLOGY.md` 登记，生物 2 处亦见 FINAL §5；本验收不拿带缺陷的 reference 名反推 90/90。**（2026-10-21 补记：reference 副本的 5 处章名已于 2026-10-20 按 W-7 官方名对齐——生物 2 处、物理 3 处，见 `docs/kb-textbook-toc-align/reference/README.md`「已知待修 / 本轮修订」；本行「仍有 5 处不同」为历史原文，90/90 判定与覆盖数字不变。）**
- **不读正文**：本验收只核章名/节名与节点位置，不核节内知识点表述、不核教材正文、不把教材原文或页图入库；W-7 读目录的页图已删除、未留存。

### 2.2 课标覆盖口径（F14：主线/选修分开实算）

- **台账**：`tools/kb_coverage/tables/curriculum_alignment.csv`，**661 行**（MATH 308 / PHYSICS 138 / CHEMISTRY 73 / BIOLOGY 142）；status：COVERED **390**、PARTIAL **73**、NOT_COVERED **198**、UNDECIDED **0**（全量口径，附录 A）。
- **分组规则（本报告实算，按 `candidate_slug` 前缀）**：`required-*` = 必修（**204** 行）；`selective-*` = 选择性必修（**222** 行）；`elective-*` = 选修（**235** 行）；无其他前缀（unclassified=0）。**主线 = required-* + selective-* = 426 行**；选修 235 行单列，不进入主口径分子/分母。
- **主口径（主线 426）分科实算**：

| 学科 | 主线总数 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED | COVERED/主线 |
|---|---:|---:|---:|---:|---:|---:|
| 数学 MATH | 156 | 127 | 23 | 6 | 0 | 81.41% |
| 物理 PHYSICS | 87 | 78 | 8 | 1 | 0 | 89.66% |
| 化学 CHEMISTRY | 63 | 58 | 4 | 1 | 0 | 92.06% |
| 生物学 BIOLOGY | 120 | 111 | 8 | 1 | 0 | 92.50% |
| **合计** | **426** | **374** | **43** | **9** | **0** | **87.79%** |

- **选修 235 单列实算**：

| 学科 | 选修总数 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED | COVERED/选修 |
|---|---:|---:|---:|---:|---:|---:|
| 数学 MATH | 152 | 11 | 16 | 125 | 0 | 7.24% |
| 物理 PHYSICS | 51 | 3 | 6 | 42 | 0 | 5.88% |
| 化学 CHEMISTRY | 10 | 1 | 2 | 7 | 0 | 10.00% |
| 生物学 BIOLOGY | 22 | 1 | 6 | 15 | 0 | 4.55% |
| **合计** | **235** | **16** | **30** | **189** | **0** | **6.81%** |

- **已发布规则**：`knowledge-production/kb-coverage-alignment-2026-v2.json` 的 `publishedRule` 写明「覆盖率 = COVERED / (总数 − UNDECIDED)；分子、分母、UNDECIDED 绝对数同时公布」。本报告按该规则复算：主口径 **374 / (426 − 0) = 374/426 = 87.79%**；选修单列 **16/235 = 6.81%**；全量旧口径 **390/661 = 59.00%**（附录 A）。三套数字共用同一批 status，不重新判定。
- **判定来源**：alignment JSON `reviewedAt=2026-10-19`、reviewer=`Qwen3.8-27B（用户指定语义判定通道，2026-09-16 决定）`、`reviewState=AI_REVIEWED`。本验收**只复算/复述既有判定，不重新判定**；逐条 statement 与理由见 §3。
- **有 target 的条目**：全量 **463/661**（COVERED + PARTIAL）；主线 **417/426**、选修 **46/235**。NOT_COVERED 无 target；课标 target 悬空 0（逐节台账自检）。
- **NOT_COVERED 理由字段**：CSV 的 `note` 在 **196/198** 行为空，理由实际落在 `evidence_phrase`（去前导标点后读取）；另 2 行 `note` 有值（均为论文型学术活动）。§3 的「理由」列按「note 优先、否则 evidence_phrase」取值，未改写理由。

### 2.3 与本验收分离的检索读数（不作为覆盖证据）

- **D-3 预注册（2026-10-16；其零漂移/判读纪律为批 13 v16 复跑提供参照）**：`docs/research/2026-10-16-d3-round1-prereg.md` 冻结了融合 α 臂的判读规则（A0=α0.5、A1=α0.7、逐章点估计、按章 cluster 配对 bootstrap、零漂移硬门）。该轮结果为 **不采纳、逐字回滚 α=0.5**（同文件「结果（2026-10-16）」节）；批 13 的 v16 复跑按同型零漂移门出数，**不是 α=0.7 采纳轮**。
- **批次 13 的 D-3 产物（v16 装置）实读**：设备融合臂 Recall@5 **0.7751937984496124（100/129）**、MRR **0.6228682170542638**；词面臂 **0.689922480620155（89/129）**；同路零漂移词面 **0/PASS**、融合 **0/PASS**（`zero-drift-b13.out.txt`：`lexical=PASS fused=PASS`；同路 symdiff 均 0，跨路 13、device 两腿 13 只作情报）。逐章 0.80 达标 **14/24**、未达标 10、最小 **0.0000**；主集 0.90 差距 **0.124806**（还差 17 题命中到 117/129 下沿）。
- **边界**：以上 D-3 读数是**检索装置/质量读数**，**不进入** §1 的 X/Y 分子或分母，也不构成「题库全覆盖」。检索资产侧另做只读核验：本验收实跑 `python tools/dense_build/check_asset.py` → **EXIT=0**，`.vec` 旁车 `packSha256=f6098c41…` 与当前主包一致（v18 只改 sidecar 文本，主包 sha 未变）、`layout` 44,010 条逐条一致、`vectorCount=44010`、`DenseRecallAssembly.VECTOR_ASSET_SHA256=8e216ee…` 与旁车 `sha256` 同值；即早前 `build/agent-batch13/e/golden-pack-verify-report.md` 附加发现 1 的「仍为 v15」已被 00:50 的资产重生成覆盖，当前不再成立。D-3 读数仍只作检索参照。**批次14 v18 未跑 D-3（§7.6）；本节读数属于 v16 装置，不得当作 v18 基线。**

## 3. NOT_COVERED 逐条理由（主线 9 + 选修 189；全量 198）

### 3.1 总览（双口径，本报告实算）

**3.1a 主线 NOT_COVERED 9 条**（计入主口径）

| 学科 | NOT_COVERED | 必修 | 选择性必修 | 未设节点 | 非知识点对象 |
|---|---:|---:|---:|---:|---:|
| MATH | 6 | 3 | 3 | 0 | 6 |
| PHYSICS | 1 | 1 | 0 | 1 | 0 |
| CHEMISTRY | 1 | 1 | 0 | 1 | 0 |
| BIOLOGY | 1 | 0 | 1 | 0 | 1 |
| **合计** | **9** | **5** | **4** | **2** | **7** |

> 9 条与 `knowledge-production/f14-waivers.csv` 逐条同集（本报告实算 `same set=True`）：7 条非知识点对象（论文/课题/态度型）、2 条未设节点（物理环境污染、化学法规/安全意识）。**豁免登记不等于覆盖**，9 条仍按 NOT_COVERED 计入 426 分母。

**3.1b 选修 NOT_COVERED 189 条**（单列，不计入主口径）

| 学科 | NOT_COVERED | 未设节点 | 非知识点对象 | E 类自主课程 | 子模块构成（条数） |
|---|---:|---:|---:|---:|---|
| MATH | 125 | 124 | 0 | 1 | 选修A 46（微积分16/空间向量18/概率统计12）；选修B 45（微积分14/应用统计16/空间向量9/模型6）；选修C 19（逻辑3/数学模型5/社会调查11）；选修D 14（美与数学4/音乐3/美术4/体育3）；选修E 1 |
| PHYSICS | 42 | 42 | 0 | 0 | 选修1 14；选修2 16；选修3 12 |
| CHEMISTRY | 7 | 7 | 0 | 0 | 化学与社会 3；实验化学 1；发展中的化学科学 3 |
| BIOLOGY | 15 | 2 | 13 | 0 | 现实生活应用 5；职业规划前瞻 7；学业发展基础 3 |
| **合计** | **189** | **175** | **13** | **1** | — |

> 读法：选修 189 条中 175 条「未设节点」、13 条「非知识点对象」、1 条「学校自主开发 E 类课程」；按学科数学 125（66.14%）、物理 42（22.22%）、化学 7（3.70%）、生物 15（7.94%）。逐条见 `docs/research/2026-10-13-elective-coverage-report.md`。

**3.1c 全量 198 对照（旧 661 口径；与 3.1a/3.1b 同源，不重复判定）**

| 学科 | NOT_COVERED | 必修 | 选择性必修 | 选修 | 未设节点 | 非知识点对象 | 其他 |
|---|---:|---:|---:|---:|---:|---:|---:|
| MATH | 131 | 3 | 3 | 125 | 124 | 6 | 1 |
| PHYSICS | 43 | 1 | 0 | 42 | 43 | 0 | 0 |
| CHEMISTRY | 8 | 1 | 0 | 7 | 8 | 0 | 0 |
| BIOLOGY | 16 | 0 | 1 | 15 | 2 | 14 | 0 |
| **合计** | **198** | **5** | **4** | **189** | **177** | **20** | **1** |

> 读法：**「未设节点」= 现包没有对应节点**；**「非知识点对象」= 论文/课题研究/职业规划等非知识点型要求**；「其他」= 学校自主开发 E 类课程（非统一知识点对象）。198 条中 189 条属选修模块，必修仅 5 条、选择性必修仅 4 条。

### 3.2 逐条表（按学科分列；课标要求为 statement 摘要，完整原文见 CSV/alignment JSON）

> 本表保留**全量 198 条**逐条明细（旧 661 口径的完整底账，便于与附录 A 对账）；主口径只取其中**主线 9 条**，选修 189 条的单列报告见 `docs/research/2026-10-13-elective-coverage-report.md`。本表不改写任何理由。

#### MATH（NOT_COVERED 131 条）

| # | candidate_slug | 模块 | 课标要求（摘要） | NOT_COVERED 理由 |
|---|---|---|---|---|
| 1 | `required-functions-candidate-007` | 必修：函数 | 函数的形成与发展 收集、阅读函数的形成与发展的历史资料 ,撰写小论文,论述函数 发展的过程、重要结果、主要人物、… | 论文型学术活动，非知识点对象 |
| 2 | `required-functions-candidate-015` | 必修：函数 | 收集、阅读对数概念的形成与发展的历史资料 ,撰写小论文 , 论述对数发明的过程以及对数对简化运算的作用 。 | 论文型学术活动，非知识点对象 |
| 3 | `required-geometry-algebra-candidate-028` | 必修：几何与代数 | 几何学的发展 收集、阅读几何学发展的历史资料,撰写小论文,论述几何学发展 的过程、重要结果、主要人物、关键事件及… | 论文型学术活动，非知识点对象 |
| 4 | `selective-functions-candidate-019` | 选择性必修：函数 | 微积分的创立与发展 收集、阅读对微积分的创立和发展起重大作用的有关资料 ,包括一 些重要历史人物 (如牛顿、莱布… | 论文型学术活动，非知识点对象 |
| 5 | `selective-geometry-algebra-candidate-027` | 选择性必修：几何与代数 | 平面解析几何的形成与发展 收集、阅读平面解析几何的形成与发展的历史资料 ,撰写小论文 , 论述平面解析几何发展的… | 论文型学术活动，非知识点对象 |
| 6 | `selective-modeling-inquiry-candidate-001` | 选择性必修：数学建模活动与数学探究活动 | 数学建模活动与数学探究活动以课题研究的形式开展 。在选择性必 修课程中,要求学生完成一个课题研究 ,可以是数学建… | 活动性要求（课题研究）非知识点对象 |
| 7 | `elective-a-calculus-candidate-001` | 选修A：微积分 | 通过典型收敛数列的极限过程 (当狀→ ∞ 时,1狀→0, 狀狀+ 1→ 1,狇狀→0(0< \|狇\|<1) ,犪 … | 数列极限未设节点（选修A大学先修） |
| 8 | `elective-a-calculus-candidate-002` | 选修A：微积分 | 探索并证明基本性质:收敛数列是有界数列。 | 数列极限性质未设节点 |
| 9 | `elective-a-calculus-candidate-003` | 选修A：微积分 | 通过典型单调有界数列1狀{ }, 狀狀+ 1{ } ,{狇狀} (0<狇<1) ,{犪 1狀 } (犪>0) 的… | 单调有界收敛准则未设节点 |
| 10 | `elective-a-calculus-candidate-004` | 选修A：微积分 | 掌握数列极限的四则运算法则 。 | 极限四则运算未设节点 |
| 11 | `elective-a-calculus-candidate-005` | 选修A：微积分 | 通过典型数列的收敛性,理解e的意义。 | e的极限定义未设节点 |
| 12 | `elective-a-calculus-candidate-006` | 选修A：微积分 | 通过典型函数的极限过程 (当狓→犪时,狓2→犪2;当狓→犪时, s i n狓→s i n犪;当狓→0时,犪狓→1… | 函数极限未设节点 |
| 13 | `elective-a-calculus-candidate-007` | 选修A：微积分 | 掌握基本初等函数极限的四则运算 。 | 函数极限运算未设节点 |
| 14 | `elective-a-calculus-candidate-008` | 选修A：微积分 | 掌握两个重要函数极限:l i m狓→0 s i n狓狓 = 1,l i m狓→0(1 +狓) 1狓 = e,并会… | 两个重要极限未设节点 |
| 15 | `elective-a-calculus-candidate-009` | 选修A：微积分 | 理解连续函数的定义。 | 连续函数定义未设节点 |
| 16 | `elective-a-calculus-candidate-012` | 选修A：微积分 | 通过导函数的概念,掌握二阶导数的概念,了解二阶导数的物 理意义与几何意义。 | 二阶导数未设节点 |
| 17 | `elective-a-calculus-candidate-014` | 选修A：微积分 | 理解并证明拉格朗日中值定理 ,并能用其讨论函数的单调性。 | 拉格朗日中值定理未设节点 |
| 18 | `elective-a-calculus-candidate-017` | 选修A：微积分 | 了解微分的概念及其实际意义 ,并会用符号表示。 | 微分未设节点 |
| 19 | `elective-a-calculus-candidate-018` | 选修A：微积分 | 通过等分区间求特殊曲边梯形面积的极限过程 ,理解定积分的 概念及其几何意义与物理意义 。 | 定积分未设节点 |
| 20 | `elective-a-calculus-candidate-019` | 选修A：微积分 | 在单调函数定积分的计算过程中 ,通过微分感悟积分与导数的 关系,理解并掌握微积分基本定理 (牛顿.莱布尼茨公式犳… | 微积分基本定理未设节点 |
| 21 | `elective-a-calculus-candidate-020` | 选修A：微积分 | 会利用基本初等函数的导数表和牛顿 .莱布尼茨公式 ,求一些 简单函数的定积分。 | 定积分计算未设节点 |
| 22 | `elective-a-calculus-candidate-021` | 选修A：微积分 | 会利用定积分计算某些封闭图形的面积 ,计算球、圆锥、圆台 和三棱锥、三棱台的体积 ;能利用定积分解决简单的做功问… | 定积分应用（面积/体积/做功）未设节点 |
| 23 | `elective-a-space-vector-algebra-candidate-004` | 选修A：空间向量与代数 | 掌握向量组的线性相关性,并能加以判断。 | 线性相关性未设节点 |
| 24 | `elective-a-space-vector-algebra-candidate-005` | 选修A：空间向量与代数 | 掌握向量的线性运算,理解向量空间与子空间的概念 。 | 向量空间/子空间未设节点 |
| 25 | `elective-a-space-vector-algebra-candidate-006` | 选修A：空间向量与代数 | 通过几何直观引入矩阵概念 ,掌握矩阵的三种基本运算及其 性质。 | 矩阵未设节点 |
| 26 | `elective-a-space-vector-algebra-candidate-007` | 选修A：空间向量与代数 | 了解正交矩阵及其基本性质,能用代数方法解决几何问题。 | 正交矩阵未设节点 |
| 27 | `elective-a-space-vector-algebra-candidate-008` | 选修A：空间向量与代数 | 掌握行列式定义与性质,会计算行列式。 | 行列式未设节点 |
| 28 | `elective-a-space-vector-algebra-candidate-009` | 选修A：空间向量与代数 | 通过实例,探索三元一次方程组的求解过程 ,理解三元一次方 程组的常用解法 (高斯消元法) ,会用矩阵表示三元一次… | 线性方程组/高斯消元未设节点 |
| 29 | `elective-a-space-vector-algebra-candidate-010` | 选修A：空间向量与代数 | 掌握三元齐次线性方程组的解法 ,会表示一般解。 | 齐次线性方程组未设节点 |
| 30 | `elective-a-space-vector-algebra-candidate-011` | 选修A：空间向量与代数 | 掌握非齐次线性方程组有解的判定 ,建立线性方程组的理论 基础。 | 非齐次方程组判定未设节点 |
| 31 | `elective-a-space-vector-algebra-candidate-012` | 选修A：空间向量与代数 | 探索三元一次方程组解的结构 ,会表示一般解。 | 方程组解的结构未设节点 |
| 32 | `elective-a-space-vector-algebra-candidate-013` | 选修A：空间向量与代数 | 理解克拉默 (C r a m e r)法则,会用克拉默法则求解三元一次 方程组。 | 克拉默法则未设节点 |
| 33 | `elective-a-space-vector-algebra-candidate-014` | 选修A：空间向量与代数 | 通过向量的坐标表示,建立空间平面的方程。 | 空间平面方程未设节点 |
| 34 | `elective-a-space-vector-algebra-candidate-015` | 选修A：空间向量与代数 | 理解空间直线方程的含义,会用方程表示空间直线。 | 空间直线方程未设节点 |
| 35 | `elective-a-space-vector-algebra-candidate-017` | 选修A：空间向量与代数 | 了解平面变换的含义,理解平面的等距变换,特别是三种基本 等距变换:直线反射、平移、旋转。 | 平面等距变换未设节点 |
| 36 | `elective-a-space-vector-algebra-candidate-018` | 选修A：空间向量与代数 | 了解平面对称图形及变换群概念 。 | 变换群未设节点 |
| 37 | `elective-a-space-vector-algebra-candidate-019` | 选修A：空间向量与代数 | 掌握常见平面等距变换及其矩阵表示 。 | 等距变换矩阵表示未设节点 |
| 38 | `elective-a-space-vector-algebra-candidate-020` | 选修A：空间向量与代数 | 了解空间变换的含义,理解空间的等距变换,特别是三种常见 等距变换:平面反射、平移、旋转。 | 空间等距变换未设节点 |
| 39 | `elective-a-space-vector-algebra-candidate-021` | 选修A：空间向量与代数 | 了解空间对称图形及变换群。 | 空间变换群未设节点 |
| 40 | `elective-a-space-vector-algebra-candidate-022` | 选修A：空间向量与代数 | 掌握常见空间等距变换及其矩阵表示 。 | 空间等距变换矩阵表示未设节点 |
| 41 | `elective-a-probability-statistics-candidate-001` | 选修A：概率与统计 | 借助具体实例,了解连续型随机变量及其分布 ,体会连续型随 机变量与离散型随机变量的共性与差异 。 | 连续型随机变量未设节点 |
| 42 | `elective-a-probability-statistics-candidate-002` | 选修A：概率与统计 | 结合生活中的实例,了解几个重要连续型随机变量的分布 :均 匀分布、正态分布、卡方分布、狋.分布,理解这些分布中参… | 连续型分布（均匀/卡方/t）未设节点 |
| 43 | `elective-a-probability-statistics-candidate-003` | 选修A：概率与统计 | 了解连续型随机变量的均值和方差 ,知道均匀分布 、正态分 布、卡方分布、狋.分布的均值和方差及其意义。 | 连续型数字特征未设节点 |
| 44 | `elective-a-probability-statistics-candidate-004` | 选修A：概率与统计 | 在学习一维离散型随机变量的基础上 ,通过实例,了解二维离 散型随机变量概念及其分布列 、数字特征 (均值、方差、… | 二维随机变量/协方差未设节点 |
| 45 | `elective-a-probability-statistics-candidate-005` | 选修A：概率与统计 | 在学习一维正态随机变量的基础上 ,通过具体实例,了解二维 正态随机变量及其联合分布,以及联合分布中参数的意义。 | 二维正态未设节点 |
| 46 | `elective-a-probability-statistics-candidate-006` | 选修A：概率与统计 | 参数估计 借助对具体实际问题的分析,知道矩估计和极大似然估计这两种参 数估计方法,了解参数估计原理,能解决一些简… | 参数估计未设节点 |
| 47 | `elective-a-probability-statistics-candidate-007` | 选修A：概率与统计 | 了解假设检验的统计思想和基本概念 。 | 假设检验未设节点 |
| 48 | `elective-a-probability-statistics-candidate-008` | 选修A：概率与统计 | 借助具体实例,了解正态总体均值和方差检验的方法 ,了解两 个正态总体均值比较的方法。 | 均值方差检验未设节点 |
| 49 | `elective-a-probability-statistics-candidate-009` | 选修A：概率与统计 | 结合具体实例,了解总体分布的拟合优度检验 。 | 拟合优度检验未设节点 |
| 50 | `elective-a-probability-statistics-candidate-010` | 选修A：概率与统计 | 了解二维正态分布及其参数的统计含义 。 | 二维正态统计含义未设节点 |
| 51 | `elective-a-probability-statistics-candidate-011` | 选修A：概率与统计 | 了解二元线性回归模型,会用最小二乘原理对模型中的参数进 行估计。 | 二元线性回归未设节点 |
| 52 | `elective-a-probability-statistics-candidate-012` | 选修A：概率与统计 | 运用二元线性回归模型解决简单的实际问题 。 犅类课程 | 二元回归应用未设节点 |
| 53 | `elective-b-calculus-candidate-001` | 选修B：微积分 | 通过典型数列 1狀{ }, 狀狀+ 1{ } , {狇狀} (0< \|狇\| <1) ,了解数列的 极限,掌握极限… | 数列极限（B类）未设节点 |
| 54 | `elective-b-calculus-candidate-002` | 选修B：微积分 | 通过具体函数 犳(狓)=狓2,犳(狓)=1狓,犳(狓)=槡狓,犳(狓)= 犪狓(犪>0,且犪≠1) ,犳(狓)=… | 函数极限/连续（B类）未设节点 |
| 55 | `elective-b-calculus-candidate-003` | 选修B：微积分 | 通过导数概念,理解二阶导数的概念,了解二阶导数的物理意 义与几何意义;掌握一些基本初等函数的一阶导数与二阶导数 … | 二阶导数（B类）未设节点 |
| 56 | `elective-b-calculus-candidate-004` | 选修B：微积分 | 理解拉格朗日中值定理,了解它的几何解释。 | 拉格朗日中值定理（B类）未设节点 |
| 57 | `elective-b-calculus-candidate-008` | 选修B：微积分 | 了解微分的概念及其实际意义 ,会用符号表示。 | 微分（B类）未设节点 |
| 58 | `elective-b-calculus-candidate-009` | 选修B：微积分 | 了解闭区间上连续函数定积分的概念 ,理解其几何意义与物理 意义。 | 定积分（B类）未设节点 |
| 59 | `elective-b-calculus-candidate-010` | 选修B：微积分 | 能用等分区间方法计算特殊的黎曼和 。 | 黎曼和未设节点 |
| 60 | `elective-b-calculus-candidate-011` | 选修B：微积分 | 利用犳(狓)的单调性、等分区间的方法 、拉格朗日中值定理 , 推导牛顿.莱布尼茨公式犳(犫)-犳(犪)=∫ 犫 … | 牛顿-莱布尼茨公式（B类）未设节点 |
| 61 | `elective-b-calculus-candidate-012` | 选修B：微积分 | 会利用定积分计算某些封闭平面图形的面积 ,计算球、圆锥、 圆台和三棱锥、三棱台的体积。了解祖/G21原理。 | 定积分应用/祖暅原理未设节点 |
| 62 | `elective-b-calculus-candidate-013` | 选修B：微积分 | 通过简单实例,理解二元函数的背景。 | 二元函数未设节点 |
| 63 | `elective-b-calculus-candidate-014` | 选修B：微积分 | 了解偏导数的定义,能计算一些简单函数的偏导数 。例如,已 知犳(狓)与犵(狔)分别是基本初等函数 ,会求犳(狓)… | 偏导数未设节点 |
| 64 | `elective-b-calculus-candidate-015` | 选修B：微积分 | 会求一些简单二元函数的驻点 ,并能求相应实际问题中的 极值。 | 驻点未设节点 |
| 65 | `elective-b-calculus-candidate-016` | 选修B：微积分 | 利用等高线法,会求一次函数犳(狓,狔)=犪 狓+犫狔在闭凸多边 形区域上的最大值和最小值。 | 等高线法未设节点 |
| 66 | `elective-b-calculus-candidate-017` | 选修B：微积分 | 会求闭圆域、闭椭圆域上二元二次函数的最大值和最小值 。 | 二元二次函数最值未设节点 |
| 67 | `elective-b-space-vector-algebra-candidate-004` | 选修B：空间向量与代数 | 掌握向量组的线性相关性,并能加以判断。 | 线性相关性（B类）未设节点 |
| 68 | `elective-b-space-vector-algebra-candidate-005` | 选修B：空间向量与代数 | 掌握向量的线性运算 ,理解 (低维)向量空间与子空间的 概念。 | 向量空间/子空间（B类）未设节点 |
| 69 | `elective-b-space-vector-algebra-candidate-007` | 选修B：空间向量与代数 | 通过几何直观引入矩阵概念 ,掌握矩阵的三种基本运算及其 性质。 | 矩阵（B类）未设节点 |
| 70 | `elective-b-space-vector-algebra-candidate-008` | 选修B：空间向量与代数 | 掌握行列式定义与性质,会计算行列式。 | 行列式（B类）未设节点 |
| 71 | `elective-b-space-vector-algebra-candidate-009` | 选修B：空间向量与代数 | 通过实例,探索三元一次方程组的求解过程 ,掌握三元一次方 程组的常用解法 (高斯消元法) ,会用矩阵表示三元一次… | 线性方程组/高斯消元（B类）未设节点 |
| 72 | `elective-b-space-vector-algebra-candidate-010` | 选修B：空间向量与代数 | 掌握三元齐次线性方程组的解法 ,会表示一般解。 | 齐次线性方程组（B类）未设节点 |
| 73 | `elective-b-space-vector-algebra-candidate-011` | 选修B：空间向量与代数 | 掌握非齐次线性方程组有解的判定 ,建立线性方程组的理论 基础。 | 非齐次方程组判定（B类）未设节点 |
| 74 | `elective-b-space-vector-algebra-candidate-012` | 选修B：空间向量与代数 | 探索三元一次方程组解的结构 ,会表示一般解。 | 方程组解的结构（B类）未设节点 |
| 75 | `elective-b-space-vector-algebra-candidate-013` | 选修B：空间向量与代数 | 理解克拉默 (C r a m e r)法则,会用克拉默法则求解三元一次 方程组。 | 克拉默法则（B类）未设节点 |
| 76 | `elective-b-applied-statistics-candidate-001` | 选修B：应用统计 | 借助具体实例,了解连续型随机变量及其分布 ,体会连续型随 机变量与离散型随机变量的共性与差异 。 | 连续型随机变量（B类）未设节点 |
| 77 | `elective-b-applied-statistics-candidate-002` | 选修B：应用统计 | 结合生活中的实例,了解几个重要连续型随机变量的分布 :均 匀分布、正态分布、卡方分布、狋.分布,理解这些分布中参… | 连续型分布（B类）未设节点 |
| 78 | `elective-b-applied-statistics-candidate-003` | 选修B：应用统计 | 了解连续型随机变量的均值和方差 ,知道均匀分布 、正态分 布、卡方分布、狋.分布的均值和方差及其意义。 | 连续型数字特征（B类）未设节点 |
| 79 | `elective-b-applied-statistics-candidate-004` | 选修B：应用统计 | 在学习一维离散型随机变量的基础上 ,通过实例,了解二维离 散型随机变量概念及其分布列 、数字特征 (均值、方差、… | 二维随机变量（B类）未设节点 |
| 80 | `elective-b-applied-statistics-candidate-005` | 选修B：应用统计 | 在学习一维正态随机变量的基础上 ,通过具体实例,了解二维 正态随机变量及其联合分布,以及联合分布中参数的意义。 | 二维正态（B类）未设节点 |
| 81 | `elective-b-applied-statistics-candidate-006` | 选修B：应用统计 | 参数估计 借助对具体实际问题的分析,知道矩估计和极大似然估计这两种参 数估计方法,了解参数估计原理,能解决一些简… | 参数估计（B类）未设节点 |
| 82 | `elective-b-applied-statistics-candidate-007` | 选修B：应用统计 | 了解假设检验的统计思想和基本概念 。 | 假设检验（B类）未设节点 |
| 83 | `elective-b-applied-statistics-candidate-008` | 选修B：应用统计 | 借助具体实例,了解正态总体均值和方差检验的方法 ,了解两 个正态总体均值比较的方法。 | 均值方差检验（B类）未设节点 |
| 84 | `elective-b-applied-statistics-candidate-009` | 选修B：应用统计 | 结合具体实例,了解总体分布的拟合优度检验 。 | 拟合优度检验（B类）未设节点 |
| 85 | `elective-b-applied-statistics-candidate-010` | 选修B：应用统计 | 了解二维正态分布及其参数的统计含义 。 | 二维正态统计含义（B类）未设节点 |
| 86 | `elective-b-applied-statistics-candidate-011` | 选修B：应用统计 | 了解二元线性回归模型,会用最小二乘原理对模型中的参数进 行估计。 | 二元线性回归（B类）未设节点 |
| 87 | `elective-b-applied-statistics-candidate-012` | 选修B：应用统计 | 运用二元线性回归模型解决简单的实际问题 。 | 二元回归应用（B类）未设节点 |
| 88 | `elective-b-applied-statistics-candidate-013` | 选修B：应用统计 | 借助具体实例,了解聚类分析的意义。 | 聚类分析未设节点 |
| 89 | `elective-b-applied-statistics-candidate-014` | 选修B：应用统计 | 借助具体实例,了解几种聚类分析的方法,能解决一些简单的 实际问题。 | 聚类方法未设节点 |
| 90 | `elective-b-applied-statistics-candidate-015` | 选修B：应用统计 | 借助具体实例,了解正交设计原理。 | 正交设计未设节点 |
| 91 | `elective-b-applied-statistics-candidate-016` | 选修B：应用统计 | 借助具体实例,了解正交表,能用正交表进行实验设计。 | 正交表实验设计未设节点 |
| 92 | `elective-b-models-candidate-001` | 选修B：模型 | 结合实际问题,了解一维线性模型,理解一次函数与均匀变化 的关系,并能发现生活中均匀变化的实际问题 。 | 一维线性模型未设节点 |
| 93 | `elective-b-models-candidate-002` | 选修B：模型 | 结合实际问题,了解二维线性模型,探索平面上一些图形的变 化,并能理解一维线性模型与二维线性模型的异同 (例如,系… | 二维线性模型未设节点 |
| 94 | `elective-b-models-candidate-003` | 选修B：模型 | 结合实际问题,了解三维线性模型 ,如经济学上的投入产出 模型。 | 三维线性模型/投入产出未设节点 |
| 95 | `elective-b-models-candidate-007` | 选修B：模型 | 借助具体实例,理解平面上的参变数模型,如弹道模型。 | 参数方程未设节点 |
| 96 | `elective-b-models-candidate-008` | 选修B：模型 | 借助具体实例,理解空间中的参变数模型,如螺旋曲线。 | 空间参数方程未设节点 |
| 97 | `elective-b-models-candidate-009` | 选修B：模型 | 借助一些用参变数方程描述的物理问题与几何问题 ,理解参变 数的意义,掌握参变数变化的范围。 犆类课程 | 参变数意义未设节点 |
| 98 | `elective-c-logic-candidate-002` | 选修C：逻辑推理初步 | 数学推理的前提 理解同一律、矛盾律、排中律的含义,通过实例认识它们在数学推 理中的作用,能在数学推理中认识推理前… | 逻辑三律（同一/矛盾/排中）未设节点 |
| 99 | `elective-c-logic-candidate-003` | 选修C：逻辑推理初步 | 数学推理的类型 结合学过的数学实例和生活中的实例 ,理解演绎推理、归纳和类比 推理,在这些推理的过程中,认识数学… | 推理类型（演绎/归纳/类比）未设节点 |
| 100 | `elective-c-logic-candidate-005` | 选修C：逻辑推理初步 | 公理化思想 通过数学史和其他领域的典型事例 ,了解数学公理化的含义,了解 公理体系的独立性、相容性、完备性,了解… | 公理化思想未设节点 |
| 101 | `elective-c-mathematical-models-candidate-001` | 选修C：数学模型 | 存款贷款模型 (指数函数模型) 通过对存款等实际问题的分析 ,抽象出复利模型;通过对住房贷款 等实际问题的分析,… | 存款贷款模型未设节点 |
| 102 | `elective-c-mathematical-models-candidate-002` | 选修C：数学模型 | 投入产出模型 (线性方程组模型) 了解投入产出模型的背景和意义 ,理解如何通过线性方程组中的系 数和解来约束自变… | 投入产出模型未设节点 |
| 103 | `elective-c-mathematical-models-candidate-004` | 选修C：数学模型 | 凯恩斯模型 (经济理论模型) 了解如何通过收入、消费和投资之间的关系建立数学模型 ,体会模 型中系数的乘数效应,… | 凯恩斯模型未设节点 |
| 104 | `elective-c-mathematical-models-candidate-005` | 选修C：数学模型 | 生产函数模型 (对数线性模型) 了解生产理论中柯布.道格拉斯 (C o b b . D o ugl a s)生产… | 生产函数/对数线性模型未设节点 |
| 105 | `elective-c-mathematical-models-candidate-008` | 选修C：数学模型 | 信度评价模型 (L ogi s t i c回归模型) 对于银行贷款用户、信用卡用户等涉及信度的问题 ,知道用L … | Logistic回归未设节点 |
| 106 | `elective-c-social-survey-candidate-001` | 选修C：社会调查与数据分析 | 结合实例,了解社会调查的使用范围、分类和意义。 | 社会调查未设节点 |
| 107 | `elective-c-social-survey-candidate-002` | 选修C：社会调查与数据分析 | 针对具体问题,了解社会调查的基本步骤:项目确定、方案设 计、组织实施、数据分析、形成报告。 | 调查步骤未设节点 |
| 108 | `elective-c-social-survey-candidate-003` | 选修C：社会调查与数据分析 | 结合实例,了解调查方案设计的基本框架 :目的、内容、对 象、项目、方式、方法等。 | 调查方案框架未设节点 |
| 109 | `elective-c-social-survey-candidate-004` | 选修C：社会调查与数据分析 | 结合实例,探索调查方案的可行性评估。 | 可行性评估未设节点 |
| 110 | `elective-c-social-survey-candidate-005` | 选修C：社会调查与数据分析 | 结合实例,了解问卷设计的主要问题 :问卷的结构与常用量 | 问卷设计未设节点 |
| 111 | `elective-c-social-survey-candidate-006` | 选修C：社会调查与数据分析 | 结合实例,掌握社会调查基本方法:文案调查法、观察法、访 谈法、专家调查法 (D e lph iM e t h o… | 调查方法未设节点 |
| 112 | `elective-c-social-survey-candidate-007` | 选修C：社会调查与数据分析 | 抽样设计 在必修课程学习的简单随机抽样 、分层抽样等抽样方法的基础上 , 了解二阶与多阶抽样,能根据具体情境选择… | 多阶抽样未设节点 |
| 113 | `elective-c-social-survey-candidate-008` | 选修C：社会调查与数据分析 | 结合具体实例,整理调查数据 ,了解常用统计图表 :频数表、 交叉表、直方图、茎叶图、扇形图、雷达图、箱线图,以及… | 统计图表与统计量未设节点（频数表/茎叶图/箱线图等） |
| 114 | `elective-c-social-survey-candidate-009` | 选修C：社会调查与数据分析 | 结合具体实例,了解相关分析、回归分析、多元统计分析。 | 多元统计分析未设节点 |
| 115 | `elective-c-social-survey-candidate-010` | 选修C：社会调查与数据分析 | 社会调查数据报告 掌握社会调查报告的基本要求及基本内容 ,能做出简单的、完整的 社会调查数据报告。 | 调查数据报告未设节点 |
| 116 | `elective-c-social-survey-candidate-011` | 选修C：社会调查与数据分析 | 社会调查案例选讲 通过典型案例的学习,理解社会调查的意义。 犇类课程 | 社会调查案例未设节点 |
| 117 | `elective-d-beauty-math-candidate-001` | 选修D：美与数学 | 美与数学的简洁 数学可以刻画现实世界中的简洁美 。例如,太阳、满月、车轮、井 盖形状等美的共性与圆相关,抛物运动… | 简洁美未设节点 |
| 118 | `elective-d-beauty-math-candidate-002` | 选修D：美与数学 | 美与数学的对称 数学可以刻画现实世界中的对称美 。例如,某些动物形体、飞机造 型、某些建筑物外形等美的共性与空间… | 对称美未设节点 |
| 119 | `elective-d-beauty-math-candidate-003` | 选修D：美与数学 | 美与数学的周期 数学可以刻画现实世界中的周期美 。例如,昼夜交替、四季循环、 日月星辰运动规律、海洋波浪等美的共… | 周期美未设节点 |
| 120 | `elective-d-beauty-math-candidate-004` | 选修D：美与数学 | 美与数学的和谐 数学可以刻画现实世界中的和谐美 。例如,人体结构、建筑物、国 旗、绘画、优选法等美的共性与黄金分… | 和谐美/黄金分割/斐波那契未设节点 |
| 121 | `elective-d-music-math-candidate-003` | 选修D：音乐中的数学 | 乐曲的节拍与分数 乐曲的小节、拍、拍号与分数相关,套曲的钢琴演奏与最小公倍数 相关。 | 节拍与分数未设节点 |
| 122 | `elective-d-music-math-candidate-004` | 选修D：音乐中的数学 | 乐器中的数学 键盘乐器 (如钢琴等) 、弦乐器 (如小提琴、二胡等) 、管乐器 (如 长笛等)的发声、共鸣等,都… | 乐器中的数学未设节点 |
| 123 | `elective-d-music-math-candidate-005` | 选修D：音乐中的数学 | 乐曲中的数学 乐曲中的高潮点、乐曲调性的转换点,常与黄金分割相关;乐曲的 创作既与平移、反射、伸缩等变换相关,也… | 乐曲中的数学未设节点 |
| 124 | `elective-d-art-math-candidate-001` | 选修D：美术中的数学 | 绘画与数学 名画中的数学元素,绘画中的平移与对称,绘画中的黄金分割,绘 画中的透视几何。 | 绘画与数学未设节点 |
| 125 | `elective-d-art-math-candidate-002` | 选修D：美术中的数学 | 其他美术作品中的数学 雕塑中的黄金分割,建筑中的对称,工艺品中的对称,邮票中的数 学,书法中的黄金分割。 | 美术作品中的数学未设节点 |
| 126 | `elective-d-art-math-candidate-003` | 选修D：美术中的数学 | 美术与计算机 计算机绘画的发展背景,计算机绘画所需的硬件和软件 ,计算机绘 画实例。 | 美术与计算机未设节点 |
| 127 | `elective-d-art-math-candidate-004` | 选修D：美术中的数学 | 美术家的数学思想 著名美术家的数学思想。 | 美术家的数学思想未设节点 |
| 128 | `elective-d-sports-math-candidate-002` | 选修D：体育运动中的数学 | 运动成绩的数据分析 通过健康指标和运动成绩的数据 ,运用概率与统计知识寻求规律 、 探索合理方案。例如,通过日常… | 运动成绩数据分析未设节点 |
| 129 | `elective-d-sports-math-candidate-003` | 选修D：体育运动中的数学 | 运动赛事中的运筹帷幄 知道能借助图论、运筹等数学知识分析体育赛事的规律 ,进行合理 安排,提升教练员的指挥策略,… | 图论/运筹未设节点 |
| 130 | `elective-d-sports-math-candidate-004` | 选修D：体育运动中的数学 | 体育用具及设施中的数学知识 知道在大多数体育运动用具和场馆的设计中都运用了数学知识 。例 如,足球、乒乓球的制作… | 体育用具设施数学未设节点 |
| 131 | `elective-e-school-developed-candidate-001` | 选修E：学校自主开发或选用课程 | 野、日常生活、地方特色的数学课程,还包括大学数学的先修课程等。 拓展视野的数学课程 例如,机器人与数学、对称与群… | 学校自主开发课程（E类）非统一知识点对象 |

#### PHYSICS（NOT_COVERED 43 条）

| # | candidate_slug | 模块 | 课标要求（摘要） | NOT_COVERED 理由 |
|---|---|---|---|---|
| 1 | `required-physics-3-candidate-022` | 必修3 | 认识环境污染的危害,了解科学、技术、社会及环境协调发 展的重要性,具有环境保护的意识和行为。 | 环境污染/可持续发展未设节点 |
| 2 | `elective-physics-1-candidate-001` | 选修1：物理学与社会发展 | 了解古希腊的宇宙观与我国古代的宇宙观。了解哥白尼日心 说对宇宙观的冲击。了解开普勒定律对牛顿发现万有引力定律的重… | 宇宙观史实未设节点 |
| 3 | `elective-physics-1-candidate-002` | 选修1：物理学与社会发展 | 了解伽利略的实验方法和逻辑方法对牛顿力学诞生的贡献。 了解牛顿力学的成就和局限性。 | 物理学史未设节点 |
| 4 | `elective-physics-1-candidate-003` | 选修1：物理学与社会发展 | 了解 19 世纪末物理学的发现对于近代物理学革命的意义。 | 近代物理学革命史未设节点 |
| 5 | `elective-physics-1-candidate-005` | 选修1：物理学与社会发展 | 了解相对论的时空观,知道质能关系的意义,了解相对论与 量子论对人类认识的影响。 | 相对论/质能关系影响未设节点 |
| 6 | `elective-physics-1-candidate-006` | 选修1：物理学与社会发展 | 了解我国物理学家的成果与贡献。 | 我国物理学家成果未设节点 |
| 7 | `elective-physics-1-candidate-007` | 选修1：物理学与社会发展 | 了解蒸汽机的发明对第一次工业革命的推动作用。 | 工业革命史未设节点 |
| 8 | `elective-physics-1-candidate-008` | 选修1：物理学与社会发展 | 了解电磁学的发展对第二次工业革命的推动作用。 | 电磁学发展史未设节点 |
| 9 | `elective-physics-1-candidate-009` | 选修1：物理学与社会发展 | 了解集成电路的进展,了解微电子技术的应用对社会发展的 影响。 | 集成电路/微电子未设节点 |
| 10 | `elective-physics-1-candidate-010` | 选修1：物理学与社会发展 | 了解信息技术的应用对社会发展的影响。 | 信息技术影响未设节点 |
| 11 | `elective-physics-1-candidate-011` | 选修1：物理学与社会发展 | 了解核技术的应用对社会发展的影响。 | 核技术应用影响未设节点 |
| 12 | `elective-physics-1-candidate-012` | 选修1：物理学与社会发展 | 了解可再生能源与不可再生能源在生活中应用的实例。 | 能源实例未设节点 |
| 13 | `elective-physics-1-candidate-013` | 选修1：物理学与社会发展 | 能列举微电子技术在生活中的应用实例。了解电视广播技术 的发展对生活的影响。 | 微电子技术实例未设节点 |
| 14 | `elective-physics-1-candidate-014` | 选修1：物理学与社会发展 | 了解计算机、手机等发展的大致历程及其对生活的影响。 | 计算机/手机历程未设节点 |
| 15 | `elective-physics-1-candidate-016` | 选修1：物理学与社会发展 | 了解航天技术对生活的影响。 | 航天技术影响未设节点 |
| 16 | `elective-physics-2-candidate-001` | 选修2：物理学与技术应用 | 了解声波的反射在 B 超中的应用。 | B超应用未设节点 |
| 17 | `elective-physics-2-candidate-003` | 选修2：物理学与技术应用 | 了解 X 射线对人体不同组织的穿透性差异。 | X射线医学应用未设节点 |
| 18 | `elective-physics-2-candidate-004` | 选修2：物理学与技术应用 | 了解 CT 扫描的基本原理。 | CT扫描未设节点 |
| 19 | `elective-physics-2-candidate-005` | 选修2：物理学与技术应用 | 了解典型射线的特性及其防护措施,知道这些射线在医学中 的应用及其对人体的影响。 | 射线医学防护未设节点 |
| 20 | `elective-physics-2-candidate-006` | 选修2：物理学与技术应用 | 了解可再生能源和不可再生能源的开发与利用,了解新能源 开发的必要性。 | 新能源开发未设节点 |
| 21 | `elective-physics-2-candidate-007` | 选修2：物理学与技术应用 | 了解太阳能利用的常见方式及应用前景。 | 太阳能利用未设节点 |
| 22 | `elective-physics-2-candidate-009` | 选修2：物理学与技术应用 | 了解地热能、潮汐能及风能等的利用。 | 地热/潮汐/风能未设节点 |
| 23 | `elective-physics-2-candidate-010` | 选修2：物理学与技术应用 | 了解物理学在新材料开发中的作用。 | 新材料开发未设节点 |
| 24 | `elective-physics-2-candidate-011` | 选修2：物理学与技术应用 | 了解纳米材料的特性及其在生活中的应用。 | 纳米材料未设节点 |
| 25 | `elective-physics-2-candidate-012` | 选修2：物理学与技术应用 | 了解超导体的一些特点,了解超导技术的应用。 | 超导体未设节点 |
| 26 | `elective-physics-2-candidate-013` | 选修2：物理学与技术应用 | 了解一些功能材料的特性及应用。 | 功能材料未设节点 |
| 27 | `elective-physics-2-candidate-014` | 选修2：物理学与技术应用 | 了解物理学对信息技术发展的贡献。 | 信息技术贡献未设节点 |
| 28 | `elective-physics-2-candidate-015` | 选修2：物理学与技术应用 | 了解智能手机的一些功能及其对生活的影响。 | 智能手机功能未设节点 |
| 29 | `elective-physics-2-candidate-016` | 选修2：物理学与技术应用 | 了解计算机的主要部件及其作用。 | 计算机部件未设节点 |
| 30 | `elective-physics-2-candidate-017` | 选修2：物理学与技术应用 | 了解卫星定位系统的发展历程及其在生活中的应用。 | 卫星定位系统未设节点 |
| 31 | `elective-physics-2-candidate-018` | 选修2：物理学与技术应用 | 了解物理学对人工智能发展的推动作用。 | 人工智能未设节点 |
| 32 | `elective-physics-3-candidate-001` | 选修3：近代物理学初步 | 比较对宏观物体与微观粒子运动特点描述的差异。 | 宏观微观描述差异未设节点 |
| 33 | `elective-physics-3-candidate-003` | 选修3：近代物理学初步 | 初步了解海森伯不确定性原理。 | 不确定性原理未设节点 |
| 34 | `elective-physics-3-candidate-006` | 选修3：近代物理学初步 | 知道相对性原理和光速不变原理。 | 相对性原理/光速不变未设节点 |
| 35 | `elective-physics-3-candidate-007` | 选修3：近代物理学初步 | 能利用公式,解释长度收缩效应和时间延缓效应。 | 长度收缩/时间延缓未设节点 |
| 36 | `elective-physics-3-candidate-009` | 选修3：近代物理学初步 | 了解广义相对论的主要思想、结论和观测证据。 | 广义相对论未设节点 |
| 37 | `elective-physics-3-candidate-010` | 选修3：近代物理学初步 | 了解人类探索高速世界的大致历程,知道这种探索将不断 深入。 | 高速世界探索史未设节点 |
| 38 | `elective-physics-3-candidate-011` | 选修3：近代物理学初步 | 初步了解大爆炸宇宙论的理论模型及其观测证据。 | 大爆炸宇宙论未设节点 |
| 39 | `elective-physics-3-candidate-012` | 选修3：近代物理学初步 | 初步了解黑洞及对黑洞的观测方法。 | 黑洞未设节点 |
| 40 | `elective-physics-3-candidate-013` | 选修3：近代物理学初步 | 初步了解暗物质和暗能量。 | 暗物质/暗能量未设节点 |
| 41 | `elective-physics-3-candidate-014` | 选修3：近代物理学初步 | 初步了解物质与反物质。 | 反物质未设节点 |
| 42 | `elective-physics-3-candidate-015` | 选修3：近代物理学初步 | 初步了解宇宙论的一些进展。 | 宇宙论进展未设节点 |
| 43 | `elective-physics-3-candidate-017` | 选修3：近代物理学初步 | 初步了解追求相互作用统一的思想和探索在物理学发展中的 作用。 | 相互作用统一思想未设节点 |

#### CHEMISTRY（NOT_COVERED 8 条）

| # | candidate_slug | 模块 | 课标要求（摘要） | NOT_COVERED 理由 |
|---|---|---|---|---|
| 1 | `required-chemistry-social-development-candidate-005` | 必修：化学与社会发展 | 化学应用的安全与规则意识 认识经济发展与环境保护等的关系。树立自觉遵守国家关于化学品 应用、化工生产、环境保护、… | 法规/安全意识未设节点 |
| 2 | `elective-experimental-chemistry-candidate-004` | 选修：实验化学 | 综合实验 围绕资源、能源与环境等与可持续发展密切相关的问题开展综合实 验项目。例如,用比色法测定水样中的某项指标… | 综合实验项目（水质检测等）未设节点 |
| 3 | `elective-chemistry-society-candidate-001` | 选修：化学与社会 | 化学与生活 认识到化学与人类的衣、食、住、行密切相关,了解化学在促进人 类健康、提供生活材料和保护环境等方面的重… | 化学与生活（STS）未设节点 |
| 4 | `elective-chemistry-society-candidate-002` | 选修：化学与社会 | 化学与技术 了解化学在工农业生产中的具体应用,认识化学工业在国民经济发 展中的地位;认识化学科学发展与技术进步的… | 化学与技术（STS）未设节点 |
| 5 | `elective-chemistry-society-candidate-003` | 选修：化学与社会 | 综合实践 通过综合实践活动,关注与化学有关的社会热点问题,认识和赞赏 化学对社会发展的重大贡献,发展综合分析、解… | 综合实践活动未设节点 |
| 6 | `elective-developing-chemical-science-candidate-001` | 选修：发展中的化学科学 | 化学科学研究进展 围绕合成化学、催化化学、界面化学、理论与计算化学、化学测量 与成像,以及化学动态学等现代化学研… | 现代化学研究前沿未设节点 |
| 7 | `elective-developing-chemical-science-candidate-002` | 选修：发展中的化学科学 | 作为交叉学科的化学 围绕材料化学、环境化学、化学生物学、能源化学、大分子及超分 子与纳米化学等具有学科交叉特点的… | 交叉学科化学未设节点 |
| 8 | `elective-developing-chemical-science-candidate-003` | 选修：发展中的化学科学 | 化学工程研究进展 现代化学工程是将化学研究转化为生产力的重要途径之一。化学工 程的研究在不断探索如何揭示物质转化… | 化学工程进展未设节点 |

#### BIOLOGY（NOT_COVERED 16 条）

| # | candidate_slug | 模块 | 课标要求（摘要） | NOT_COVERED 理由 |
|---|---|---|---|---|
| 1 | `selective-biology-environment-candidate-022` | 选择性必修：生物与环境 | 形成“环境保护需要从我做起”的意识 | 环保意识类要求,非知识点对象 |
| 2 | `elective-life-healthy-living-candidate-001` | 选修·现实生活应用：健康生活 | 健康是人类全面发展的基础,关系千家万户的幸福。每个人都有维 护自身和他人健康的责任,健康的生活方式能够维护和促进… | 健康生活模块,非知识点对象 |
| 3 | `elective-life-first-aid-candidate-001` | 选修·现实生活应用：急救措施 | 随着经济的发展,社会的进步,人们的健康意识明显提高。各种急 危重症、意外伤害时有发生,威胁着人们的健康和生命,意… | 急救措施模块,非知识点对象 |
| 4 | `elective-life-social-issues-candidate-001` | 选修·现实生活应用：社会热点中的生物学问题 | 20 世纪下半叶以来,生物学科进入飞速发展的时代,取得无数新 成果,其中有些成了社会大众关注的热点问题。与此同时… | 社会热点模块,非知识点对象 |
| 5 | `elective-life-animal-welfare-candidate-001` | 选修·现实生活应用：动物福利 | 浩瀚宇宙之中,动物和人类共同分享着一个地球,文明、人道地对 待动物本质上也与人类自身的利益息息相关。动物福利指动… | 动物福利模块,非知识点对象 |
| 6 | `elective-life-local-species-candidate-001` | 选修·现实生活应用：地方特色动植物研究 | 纵观人类历史发展可知,动植物资源是人类社会发展的重要物质资 源,人类的物质生活和精神生活都离不开动植物资源。各地… | 地方特色动植物模块,非知识点对象 |
| 7 | `elective-career-biopharma-candidate-001` | 选修·职业规划前瞻：生物制药 | 生物制药是指运用微生物学、基因工程、发酵工程、细胞工程和 化学等学科知识和技术,通过生物活体来生产药物的学科。药… | 职业规划前瞻模块,非知识点对象 |
| 8 | `elective-career-marine-biology-candidate-001` | 选修·职业规划前瞻：海洋生物学 | 海洋占地球总面积的 70.8%,其中生活着丰富多彩的生物,是生物 多样性的宝库,吸引着我们去探索和认识。海洋生物… | 职业规划前瞻模块,非知识点对象 |
| 9 | `elective-career-food-safety-candidate-001` | 选修·职业规划前瞻：食品安全与检疫 | 食品安全指食品符合应有的营养要求,对人及其后代健康不造成任 何急性、亚急性或者慢性危害,在食品加工、存储和销售等… | 职业规划前瞻模块,非知识点对象 |
| 10 | `elective-career-occupational-health-candidate-001` | 选修·职业规划前瞻：职业病与防控 | 职业病是指企事业等单位的劳动者在职业活动中,因接触粉尘、放 射性物质或其他有毒、有害因素而出现的疾病。最主要的职… | 职业规划前瞻模块,非知识点对象 |
| 11 | `elective-career-horticulture-candidate-001` | 选修·职业规划前瞻：园艺与景观生态学 | 园艺学是研究园艺作物的种质资源、生长发育规律、繁殖、栽培、 育种、储藏、加工、病虫害以及造园等的科学。造园学又叫… | 职业规划前瞻模块,非知识点对象 |
| 12 | `elective-career-environmental-crops-candidate-001` | 选修·职业规划前瞻：环境友好与经济作物 | 经济作物与人类生存和生活密切相关,如棉花、油料作物、果蔬、 茶叶等。人类为了获取高额经济回报往往肆意割除原有植被… | 职业规划前瞻模块,非知识点对象 |
| 13 | `elective-career-bioresources-candidate-001` | 选修·职业规划前瞻：生物资源开发与利用 | 生物资源是自然资源的有机组成部分,是生物圈中一切动物、植物 和微生物组成的生物群落的总和,是对人类具有实际或潜在… | 职业规划前瞻模块,非知识点对象 |
| 14 | `elective-academic-bioinformatics-candidate-001` | 选修·学业发展基础：生物信息学与人类基因组 | 生命科学的发展带来了各种生物数据的爆炸性增长,未来还将以 越来越快的速度增长。然而,数据并不等于知识,其增长所带… | 生物信息学未设节点 |
| 15 | `elective-academic-stem-cells-candidate-001` | 选修·学业发展基础：干细胞与应用 | 干细胞是一类具有自我复制能力并能分化成其他类型功能细胞的多 潜能细胞,因而具有巨大的基础研究价值和临床应用前景。… | 干细胞未设节点 |
| 16 | `elective-academic-campus-taxonomy-candidate-001` | 选修·学业发展基础：校园动植物分类 | 当前,校园生态规划建设已经成为人们讨论的热点话题。让生态理 念进入校园,不仅有利于全民生态意识的提高,也丰富了学… | 校园动植物分类模块,非知识点对象 |

## 4. 依据件清单

| 依据件 | 位置 | 本验收读取/复算内容 |
|---|---|---|
| FINAL 覆盖对照表（v16 终态） | `docs/kb-textbook-toc-align/FINAL-COVERAGE-TABLE.md` | 头部 v16 实读、§1a 终态表、90/90 章名、13 缺节终态；历史 §2–§11 未改；本报告按 v18 包复核计数不变 |
| 逐节终验台账 | `docs/kb-textbook-toc-align/SECTION-BY-SECTION-LEDGER.md` | 340 官方节 + 20 非节；落点/材料/课标条目/批11处置；自检 360/360 ✅（台账版本标注仍为 v16，v18 结构计数不变） |
| 课标对齐 CSV | `tools/kb_coverage/tables/curriculum_alignment.csv` | 661 行 → 全量 390/73/198/0；前缀分组 → 主线 426（374/43/9/0）+ 选修 235（16/30/189/0）；463 行有 target；198 条 NOT_COVERED 逐条理由 |
| 课标对齐 JSON | `knowledge-production/kb-coverage-alignment-2026-v2.json` | `publishedRule`、`total`（390/73/198/0、分母 661）、逐科 counts、661 条 statement、`reviewedAt=2026-10-19` |
| 选修单列报告 | `docs/research/2026-10-13-elective-coverage-report.md` | 选修 235 → 16/30/189/0；189 条逐条与 9 条主线逐条；本报告复算计数一致 |
| F14 豁免登记 | `knowledge-production/f14-waivers.csv` | 9 行 + 表头；本报告实算与主线 NOT_COVERED 9 条逐条同集（`same set=True`） |
| W-7 报告（4 份） | `docs/kb-textbook-toc-align/{MATH,PHYSICS,CHEMISTRY,BIOLOGY}.md` | 21 册 / 90 章 / 340 官方节 + 20 非节；官方章名；目录只读口径 |
| 章名基准件 | `docs/kb-textbook-toc-align/reference/standard_chapter_names.json` | 21 册 / 90 章；5 处已知差异（生物 2、物理 3）只作基准、不反推 90/90；**2026-10-21 补记：5 处已按 W-7 官方名对齐（`reference/README.md` 记修订后 sha256 `8491642f…`），本格为历史读数** |
| v18 包与 manifest | `build/kb-staging/moe-2025-four-subjects-v1.json`、`moe-2025-update-manifest.json` | 425 主题 / 3,245 点；version=18；contentVersion `d82a05607bdb622a`；主包 sha256 `f6098c41…`；25 件 sidecar 64,973 材料 |
| 批次14 乱码修复记录 | `build/agent-batch14/fix-record.md`、`build/agent-batch14/verify.txt` | 2 条 mojibake 的四层锁步、逐字节 diff 契约、全库复扫 C1=0 |
| 批次14 门读数 | `tools/kb_build/gate.py`、`build/agent-batch14/print_gate_readings.py`、`build/agent-batch14/a/judgment-drift-baseline.csv` | 控制字符门与 Kotlin 契约同源（0）；判定源↔包文本漂移 377（content 194 + boundary 183，基线 INFO） |
| 批次14 空点/选修处置 | `build/agent-batch14/ww/w1_zero_material_adjudication.md` | 4 TWIN + 3 选修共 7 候选，0 个干净切分，全部不建点/不改绑；幂等读数改绑 0、材料 64,973、悬空 0 |
| 批次14 A 层裁量 | `build/agent-batch14/a/reopen-decision.md`、`build/agent-batch14/a/_stats_a_recheck.py` | 60,512 块 / 420 键；point 1,053.333、Wilson lower 326.901、genuine 5/5 → SEAL |
| D-3 预注册（2026-10-16；零漂移/判读纪律为批 13 v16 复跑参照） | `docs/research/2026-10-16-d3-round1-prereg.md` | 冻结规则 + 2026-10-16 结果（不采纳、回滚 α=0.5）；本验收只引规则与结论，不把 D-3 当覆盖证据 |
| 批次 13 D-3 读数（v16，不是 v18） | `build/agent-batch13/d3/{zero-drift-b13.out.txt,zero-drift-b13.json,device-metrics.txt,chapter-gap-b13.out.txt}` | 产物实读：同路零漂移 0/0 PASS/PASS；融合 100/129=0.7752、词面 89/129=0.6899；14/24 章 ≥0.80、最小 0.0000；**v18 未跑 D-3（§7.6）** |
| v16 金标/包核验 | `build/agent-batch13/e/golden-pack-verify-report.md`、`build/agent-batch13/golden-migration-v16-chapter.json` | 报告实读：金标 129/129 命中 v16/v15；金标 chapter-only 迁移 1 条（idx=12 限制酶，slug/题面/条数未动）；dense `.vec` 本验收实跑 `check_asset.py` EXIT=0，当前与主包一致（早前报告 red 已被重生成覆盖） |

## 5. 边界与未覆盖声明

1. **不是「题库全覆盖」**：主口径 374/426 是课标内容要求 status 口径，旧口径 390/661 是同一批 status 的全量口径；两者都不表示所有可能考题、题型、难度或解法都已入库。
2. **不是教材正文覆盖**：只核章/节名与节点位置，不核正文表述、例题、实验、插图；W-7 目录页图已删除、未留存，教材原文不入库。
3. **NOT_COVERED 是明确声明，不是隐藏缺口**：主口径 **9 条**（必修 5 + 选择性必修 4，全部登记豁免：未设节点 2、非知识点对象 7）；选修单列 **189 条**（未设节点 175、非知识点对象 13、E 类自主课程 1）。明细见 §3，未用 PARTIAL 或估算值回填。
4. **PARTIAL 不计入分子**：主口径 PARTIAL **43/426 = 10.09%**；旧全量 PARTIAL **73/661 = 11.04%**；本验收不采用「(COVERED+PARTIAL)/总数」作为发布口径。
5. **340/340 有落点 ≠ 册位/点级全对**：同名节 221 条中含少量册位仍错者（如化学必修一「原子结构与元素周期表」落在选必2）；册位偏差以 FINAL §4.3 历史节与逐节台账「现包落点」列为准，本验收不重判、不掩盖。
6. **13 缺节「已补建」≠ 内容完成**：13/13 同名节位在册（点数 v16 实读、材料数据逐节台账），但 7 节仍是 0 点/0 材料空壳（化学 5 + 生物 2）；6 节已挂点 18 点、材料 197 条。空壳节和材料缺口明确列为未完成内容，不计入「内容已覆盖」。
7. **材料 64,973 条是 sidecar 绑定数，不是教材原文**：材料为结构化总结/授权教辅绑定，计数只证明绑定存在，不证明每条材料正确、充分或适合学生；本验收不核材料质量。
8. **课标判定为 AI 复核，不含逐条人工审校**：alignment JSON 的 661 条均为 `AI_REVIEWED`（Qwen3.8-27B，2026-10-19），本验收只复算其 status，不重新裁决；若要对外发布「覆盖率」，应同时披露该判定通道。
9. **双口径不得混用**：主口径分母 **426**（必修 + 选择性必修），选修 **235** 单列；旧口径分母 **661**（含 235 选修）。F14 裁定见 `docs/agent-first-refactor-decisions-2026-09-23.md:2510`；F14 理由页同时要求「保留全量口径并列公布」（`docs/kb-outstanding-research-2026-10-02.md:681-687`）。引用时必须写明口径，不能把 87.79% 与 59.00% 互相替代。
10. **检索读数与覆盖结论分离**：D-3 的 0.7752/0.6899/零漂移读数只证明检索装置与金标在 **v16** 上的可复现性，不是覆盖证据；**v18 未跑 D-3（§7.6），不得把 v16 读数当作 v18 基线**。当前 dense `.vec` 已与主包一致（`check_asset.py` EXIT=0），但检索读数仍不能替代覆盖结论。
11. **reference 件不是无缺陷权威**：`standard_chapter_names.json` 仍有 5 处与 W-7 官方名不同（生物 2、物理 3）；本验收以 W-7 官方名为准，reference 只作册/章数基准。**（2026-10-21 补记：上述 5 处已按 W-7 官方名对齐，见 `reference/README.md`；本项按历史原文保留，不据此重判。）**
12. **未验证项**：本验收未运行 Gradle/设备/仪器化；未读取教材正文；未对 64,973 条材料做逐条质量复核；未对 198 条 NOT_COVERED 做二次人工语义复核；**D-3 v18 未跑**；A 层 60,512 块未判（§7.5）。dense `.vec` 与当前主包的一致性已由 `python tools/dense_build/check_asset.py`（EXIT=0）核验，不再列为未验证。**（2026-10-21 补记：批次14 D-3 的编译阻塞已解除（原 `OpenAiModelResponseParsers.kt` 未解析引用已修复并入库）；`build/agent-batch14/d3/` 目录已存在，但仍无完整 v18 D-3 读数，故「D-3 v18 未跑」结论不变，详见 §7.6。）**
13. **豁免 ≠ 覆盖**：9 条主线 NOT_COVERED 全部在 `knowledge-production/f14-waivers.csv` 登记豁免；这是「不设节点」的处置记录，不是「已覆盖」的证据。F14 的「47 条主线缺口」在批次10 已处置（COVERED 32 / 豁免 9 / 补料 6＝SUPPLY_EXISTING 2 + SUPPLY_NEW 4，`docs/kb-batch10-2026-10-13.md` §2），本版台账只剩这 9 条。
14. **v18 文本修复不改变覆盖数字**：v18 只修 2 条 sidecar 乱码，主题/点/材料数与 v16/v17 相同；覆盖率和材料数不能因版本号变化而重述。
15. **A 层未判池不在本验收**：**61,170 块未判**（A 层 60,512 + 广告 627 + 版权 25 + 空白 4 + 目录 2）；本验收只覆盖已判池 **125,465 块**；420 样本是估计证据，不是验收计数（§7.5）。

## 6. 复现与读法（本报告实算）

```
# 主口径/选修单列：661 行 → 前缀分组 426 + 235；状态 374/43/9/0 与 16/30/189/0
PYTHONIOENCODING=utf-8 python build/agent-batch14/acceptance_recount.py

# 主线 NOT_COVERED 9 与 f14-waivers.csv 同集校验
PYTHONIOENCODING=utf-8 python build/agent-batch14/acceptance_verify_waivers.py

# v18 包身份与结构统计（425 主题 / 3,245 点 / 64,973 材料）
PYTHONIOENCODING=utf-8 python build/agent-batch14/acceptance_pack_stats.py

# 门读数：control_chars=0；judgment_pack_drift=377（等于基线，INFO）
PYTHONIOENCODING=utf-8 python build/agent-batch14/print_gate_readings.py

# 旧口径（附录 A）：661 行 → 390/73/198/0
python - <<'PY'
import csv, collections
rows=list(csv.DictReader(open("tools/kb_coverage/tables/curriculum_alignment.csv", encoding="utf-8-sig")))
print(len(rows), collections.Counter(r["status"] for r in rows))
PY
```

> 结论引用格式（主口径，必须带分母）：**「主线（必修+选择性必修）覆盖 374/426 = 87.79%（COVERED/(426−UNDECIDED)；PARTIAL 43，NOT_COVERED 9，UNDECIDED 0；9 条 NOT_COVERED 均已登记豁免）」**。选修单列引用格式：**「选修 235 条：COVERED 16/235 = 6.81%，PARTIAL 30，NOT_COVERED 189」**。旧全量口径引用格式：**「全量 661 条：COVERED 390/661 = 59.00%，PARTIAL 73，NOT_COVERED 198，UNDECIDED 0」**。不得省略分母，不得把 87.79% 与 59.00% 混用，不得写成「题库全覆盖」。

## 附录 A：旧口径（全量 661，保留并列，不删）

> F14 裁定要求「主线为分母」的同时**保留全量口径并列公布**（`docs/kb-outstanding-research-2026-10-02.md:681-687`）。本附录保留改写前的 390/661 口径与分科表，供历史对比；**不得与主口径混用**。

- **全量 661**：COVERED **390**、PARTIAL **73**、NOT_COVERED **198**、UNDECIDED **0**；**COVERED/(661−0) = 390/661 = 59.00%**；PARTIAL **73/661 = 11.04%**；NOT_COVERED **198/661 = 29.95%**。
- **分科（COVERED/该科总数）**：

| 学科 | 总数 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED | COVERED/总数 |
|---|---:|---:|---:|---:|---:|---:|
| 数学 MATH | 308 | 138 | 39 | 131 | 0 | 44.81% |
| 物理 PHYSICS | 138 | 81 | 14 | 43 | 0 | 58.70% |
| 化学 CHEMISTRY | 73 | 59 | 6 | 8 | 0 | 80.82% |
| 生物学 BIOLOGY | 142 | 112 | 14 | 16 | 0 | 78.87% |
| **合计** | **661** | **390** | **73** | **198** | **0** | **59.00%** |

- **旧口径分解**：必修 **184/204 = 90.20%**；选择性必修 **190/222 = 85.59%**；选修 **16/235 = 6.81%**。NOT_COVERED 的 **189/198 = 95.45%** 落在选修模块——这正是 F14 改口径的原因：59.00% 会被 235 条选修/活动型要求拉低，不能直接当作「必修内容只覆盖六成」。
- **旧口径复算**：见 §6 最后一段（661 行 → 390/73/198/0）。

> **历史补记（2026-10-20，v17）**：批次13 晋升后的收口补提交把成品包版本由 v16 修正为 **v17**（`contentVersion` 由过期记录值刷新为 `ecce9989aa81bc63`，经 promote 正规路径写入；v17 与 v16 的内容差仅 6 字节乱码修复文本，点位与材料数不变）。本验收的全部覆盖数字（390/661 = 59.00%）与材料数（64,973）在 v17 上不变；D-3 检索读数（0.7752 / 0.6899）在同一内容上测得。v18 已接续 v17（§7.1），版本引用以 v18 为准。

## 7. 批次14（2026-10-20）v18

> 本节记录批次14 对发布口径有影响的事项；覆盖数字仍以 §1/§2.2 的**本报告实算**为准。所有数字均标出来源命令或实读文件；v18 包身份见 §7.1。

### 7.1 v18 包身份（本报告实算）

- manifest：version=**18**，contentVersion=**d82a05607bdb622a**；主包 sha256 `f6098c41d4901d56bd33106b205e7489fa6bf078d3853413c674976b5b84386c`（与 v16/v17 相同：v18 只改 sidecar 文本）。
- 结构：**425 主题 / 3,245 知识点**（MATH 834 / PHYSICS 889 / CHEMISTRY 1,011 / BIOLOGY 511）；25 件 sidecar **64,973 材料**（MATH 14,468 / PHYSICS 18,494 / CHEMISTRY 18,307 / BIOLOGY 13,704）；绑定节点 3,245、悬空 0（`build/agent-batch14/ww/w4_manifest-readback.log` 实读）。
- 晋升：`build/agent-batch14/ww/real-promote-v18.log` 实读——28 个文件，gates/consistency/roundtrip/manifest 四门 OK，version→18，压平 0 条，contentVersion→`d82a05607bdb622a`；`build/agent-batch14/ww/w4_manifest-readback.log` 实读——成品/staging 均为 version=18、contentVersion=`d82a05607bdb622a`，复算 contentVersion 记录==复算 True，成品/staging 28/28、不同 0。
- **覆盖数字不变**：v18 与 v16/v17 的主题/点/材料数相同，§1/§2.2 的 374/426、16/235、390/661 均不变。
- 复算命令：`PYTHONIOENCODING=utf-8 python build/agent-batch14/acceptance_pack_stats.py`（本报告实跑：pack sha256、version、contentVersion、425/3,245/64,973 一次输出）。

### 7.2 乱码锁步（四层）

- 对象：v17 遗留 2 条随包乱码，均在 `moe-2025-teaching-support-v2-02.json`：
  - `ext-che-e1f44db337-030` 的 `contentMarkdown`：`$ï¼5.6ã0ï¼$` → `$（5.6、0）$`；判定表完整字节 `ef bc 88 35 2e 36 e3 80 81 30 ef bc 89` 按 latin-1→UTF-8 解码为 `（5.6、0）`。
  - `ext-che-1ff1c78002-001c` 的 `boundaryMarkdown`：`â少â指的是…` → `“少”指的是…`；判定表完整字节 `e2 80 9c`/`e2 80 9d` 解码为 `“`/`”`。
- 四层锁步：成品 sidecar / staging sidecar / 判定表 `tools/kb_coverage/tables/material_judgments/CHEMISTRY.csv` / `knowledge-production/judgment-verdicts/`。前三层修复后逐字节一致；verdicts 无同键行，未改。
- 指纹（本报告实跑 `sha256sum` 复核当前值）：成品/staging JSON `a58cbb4f…` → **`c28eb2303213a8236503adcd61924b242c4b3c2deb03b6946443b44a92b79dbc`**（4,718,215→4,718,216 B，+1）；CHEMISTRY.csv `544ac425…` → **`7f14c7e8aaea21088cac9af94884fc2c1b380f2fdfbc7a3a06d90e1cc6f5be07`**（25,256,202→25,256,187 B，−15）。
- 逐字节 diff 契约：`fixed == backup.replace(old_utf8, new_utf8)` 每条 PASS；仅目标字段变化；EOL/BOM 不变；C0/U+FFFD 修复前后均 0（`build/agent-batch14/verify.txt`）。
- 全库复扫（本报告实跑 `build/agent-batch14/scan_control_chars_all_json.py`）：成品 32 个 JSON、staging 40 个 JSON，新判据命中 **无**；`build/agent-batch14/fix-record.md` 记录成品 25 卷 + staging 25 卷 + 5 张判定表 + 1 个 verdict CSV 的 C1 合计 **0**，23,023 个 JSON/CSV/JSONL 文件中活跃三层无命中（命中只在历史备份/沙箱/scratch）。`â` 在 05/11/18/21 与 MATH.csv 的 `b̂/â` 是回归记号，不是乱码。
- 边界：本次只修文本，不改点/材料数、不改 contentId、不重生成索引；未跑 Android 构建/单测（纯资源文本修复）。

### 7.3 门同源（判据与产物同源）

- **控制字符门与 Kotlin 契约同源**：`tools/kb_build/gate.py` 的 `_CONTROL_CHARS` 改为 `KnowledgeTeachingMaterialContract.text()` 的同一判据——C0（除 `\n\r\t`）+ DEL + C1；扫描字段从 5 个扩到 7 个（含 `subject`、`sourceLocator`）。本报告实跑 `build/agent-batch14/print_gate_readings.py`：`control_chars: value=0 ok=True`。
- **判定源↔包文本漂移门与 materialize 同源**：`judgment_pack_drift` 用 `judgments.material_slug()`（与 `materialize.write` 同一公式）把判定表 MATERIAL 行与包内同 slug 材料的 `contentMarkdown`/`boundaryMarkdown` 逐字对拍；`value = content 差异 + boundary 差异 + 判定行有/包内无 + 无法定位`。本报告实跑 `PYTHONPATH=tools python -m kb_coverage.judgment_drift`：判定表 MATERIAL 行 **53,531**、唯一 slug **53,531**、包内材料 **64,973**、同 slug 命中 **53,531**；**content 差异 194 + boundary 差异 183 + 判定行有/包内无 0 + 无法定位 0 = 377**。涉及 195 个唯一 slug，逐条归档 `build/agent-batch14/a/judgment-drift-baseline.csv`（377 行 + 表头）。
- **门位置与基线语义**：`judgment_pack_drift` 不放进 `gate.evaluate()`（否则 promote 在历史漂移清账前整批不可用），只挂在 `tools/ci/run_kb_checks.py` 的 `judgment_drift` 节；`value == 377` 报 INFO，**超过基线才 FAIL**，基线只许下调。本报告实跑 `print_gate_readings.py`：`judgment_pack_drift: value=377 ok=True`（等于已登记基线）。
- **旧日志说明**：`build/agent-batch14/run_kb_checks.log`（21:56）早于基线登记（`gate.py` 22:24），其 `FAIL judgment_drift: 377 处` 是历史读数；当前实跑为 `value=377 ok=True`（等于基线，INFO）。`real-promote-v18.log` 的 gates/consistency/roundtrip/manifest 四门 OK；`run_kb_checks.log` 的 gates/consistency/roundtrip/manifest/dense 均 OK。
- 边界：377 是**已登记历史双通道分歧**（判定表旧判词 vs 包内后批作者改写版），不是新漂移；本次没有清零，也没有放宽判据——只把「等于基线」从 FAIL 改为 INFO，超过基线仍 FAIL。

### 7.4 空点/选修处置（窗口①）

- `build/agent-batch14/ww/w1_zero_material_adjudication.md` 实读：4 个「建后零材料」TWIN 点 + 3 条选修「已建但 0 材料」点，共 7 个候选；判定口径为「材料正文按内容**唯一**归属新点、且与孪生/现存节点边界不重叠」。
- 结论：**0 个干净切分 → 全部不建点、不改绑、不删并**；未写 staging，未改 `new_points_manual.csv` / `material_rebind.csv` / `point_delete.csv` / `point_merge.csv`。
- 逐项：BIOLOGY Cre-LoxP（在包，1 条材料属其不收项，0 条唯一归属）；CHEMISTRY 自偶电离（merge:812 已并）；PHYSICS 万有引力成就（merge:811 已并）；PHYSICS 对称法（merge:810 已并）；MATH 偏导数（delete:214 已撤）；MATH 数列有界性（delete:209 已撤）；MATH 随机化回答法（delete:224 已撤）。
- 幂等读数（该文件 §4 实读；本报告实跑 `PYTHONPATH=tools python -m kb_build.rebind_materials` 复核）：`改绑 0；跳过（幂等/错行）9575；材料总数 64973；悬空绑定 0；可写回 True`；`new_points_manual.csv` 1,940 数据行、`material_rebind.csv` 9,575 数据行不变（`wc -l` 1,941 / 9,576 含表头）。
- 边界：该窗口只处理「空点/选修有料可配」名单，不扩展到名单外；「不建点」是**无干净材料**的判定，不是「这些选修内容已覆盖」。其中「偏导数」对应 `elective-b-calculus-candidate-014`，在 CSV 中仍为 NOT_COVERED（属选修 189 条）；「数列的有界性及其判定」「敏感性问题调查的随机化回答法」是点级候选，未在 alignment 台账中单列，其上级选修要求的状态不由本次建点决定。本次处置**不改变** §1/§2.2 的任何覆盖率。

### 7.5 A 层裁量结论与边界

- `build/agent-batch14/a/reopen-decision.md`（2026-10-21）实读 + 本报告实跑 `build/agent-batch14/a/_stats_a_recheck.py`：A 层未判池 `kit-573..1833` = 1,261 件 / **60,512 块**；14 层 × 30 键 = **420 键**（426 行，14 个 MATERIAL 键 / 20 行）。
- 预注册合取门：point **1,053.333 < 1,500 FAIL**；Wilson 合成 lower **326.901 < 800 FAIL**；genuine **5/5（严口径 4/5）PASS** → **SEAL（封层，不重开）**。
- 稳健性：bootstrap lower **396.713**、FPC lower **273.022**，三种口径下界均 <800；按 MATERIAL 行数（20）点估计 **1,426.7** 仍 <1,500。
- SEAL 含义：A 层 60,512 块保持未判；不追加抽样；420 样本的 14 个 MATERIAL 键 / 20 行**不晋升、不入库、不进队列**；KB/判定表/既有提交不变。
- 边界：**61,170 块未判（A 层 60,512 + 广告 627 + 版权 25 + 空白 4 + 目录 2）；本批验收口径只覆盖已判池 125,465 块**；420 样本是估计证据，不是验收计数。单模型判定、块级抽样、形态由路径判定、选项为块内判定——预注册局限不改变 SEAL。
- 复算命令：`PYTHONIOENCODING=utf-8 python build/agent-batch14/a/_stats_a_recheck.py`（本报告实跑：426 行/420 键、14 MATERIAL 键/20 行、POINT 1053.333333、bootstrap/FPC 见上）；`PYTHONIOENCODING=utf-8 python build/agent-batch14/a/_verify_decision_numbers.py`（本报告实跑：RESULT OK）。

### 7.6 D-3 v18 基线

- **未跑**。本报告实查：`build/` 下只有 `agent-batch10/d3`、`agent-batch11/d3`、`agent-batch13/d3`，**无 `agent-batch14/d3` 或任何 v18 D-3 产物**；`find build -maxdepth 4 -newermt 2026-10-20` 的 D-3/zero-drift/device-metrics/chapter-gap 命中仅 batch13 文件。因此本节**不填 v18 新读数**，也不把 v16 读数改标为 v18。
- 最近基线仍是批次13 v16（`build/agent-batch13/d3/`）：融合 Recall@5 **100/129 = 0.775194**、词面 **89/129 = 0.689922**、同路零漂移 **0/PASS/PASS**；逐章 ≥0.80 **14/24**、最小 **0.0000**。该读数只适用于 v16 装置，**不是 v18 基线**。
- dense 资产：`python tools/dense_build/check_asset.py` 本报告实跑 **EXIT=0**；`.vec` 旁车 `packSha256=f6098c41…` 与当前主包一致，`vectorCount=44010`、layout 44,010 条一致。v18 只改 sidecar 文本、主包 sha 未变，故无需重打 dense；但「资产一致」不等于「v18 检索已复测」。
- 结论：**D-3 v18 基线待跑**；在跑之前，任何 v18 检索数字都不得引用 v16 读数替代。
- **（2026-10-21 批次15 补记）v18 D-3 已跑**：设备融合 Recall@5 **100/129 = 0.775194**、词面 **89/129 = 0.689922**、同路零漂移 **词面 PASS / 融合 PASS**（与 v16 的对称差 0）、MRR 0.622868、p95 56ms / p50 43ms、`denseLegLive=true`；JVM 融合镜像与设备逐位一致；vs v16 配对差 **0.0000（p=1.0）**；逐章 ≥0.80 仍 **14/24**、最小 0.0000。证据 `build/agent-batch15/d3/`，叙述与预注册见 `docs/research/2026-10-21-d3-round2-prereg.md` 与 `docs/kb-batch15-2026-10-21.md`。上节「未跑」为批次14 时点记录，保留原文。

### 7.7 本节复算命令汇总

```
# v18 包身份/结构
PYTHONIOENCODING=utf-8 python build/agent-batch14/acceptance_pack_stats.py
# 乱码修复后当前指纹（成品/staging sidecar 02、判定表）
sha256sum core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-02.json \
  build/kb-staging/moe-2025-teaching-support-v2-02.json \
  tools/kb_coverage/tables/material_judgments/CHEMISTRY.csv
# 乱码修复后全库控制字符复扫（只读）
PYTHONIOENCODING=utf-8 python build/agent-batch14/scan_control_chars_all_json.py
# 门读数（control_chars / judgment_pack_drift）
PYTHONIOENCODING=utf-8 python build/agent-batch14/print_gate_readings.py
# 判定源↔包文本漂移明细
PYTHONPATH=tools PYTHONIOENCODING=utf-8 python -m kb_coverage.judgment_drift
# 空点/选修处置幂等读数（只读，无 --write）
PYTHONPATH=tools PYTHONIOENCODING=utf-8 python -m kb_build.rebind_materials
wc -l tools/kb_build/tables/new_points_manual.csv tools/kb_build/tables/material_rebind.csv
# A 层裁量复算
PYTHONIOENCODING=utf-8 python build/agent-batch14/a/_stats_a_recheck.py
PYTHONIOENCODING=utf-8 python build/agent-batch14/a/_verify_decision_numbers.py
# dense 资产一致性
python tools/dense_build/check_asset.py
```
