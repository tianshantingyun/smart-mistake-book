# 教材目录覆盖验收（课标内容要求口径）

> 文书日期：**2026-10-20**（文件名沿用 2026-10-17 批次验收槽位；v16 终态在此补记）
> 验收对象：v16 现包 `build/kb-staging/moe-2025-four-subjects-v1.json`（`moe-2025-update-manifest.json` version=**16**，sha256 `f6098c41d4901d56bd33106b205e7489fa6bf078d3853413c674976b5b84386c`）
> 验收口径：**覆盖课标内容要求 390/661（COVERED / (总数 − UNDECIDED) = 59.00%）**；目录只读对照，不读教材正文，原文不入库。
> 性质：本验收核的是**教材目录（章/节名与节点位置）↔ 现包主题树**及**课标内容要求 status**；**不是「题库全覆盖」承诺**，也不是教材正文内容验收。

## 1. 结论

1. **主口径（课标内容要求）**：**COVERED 390 / 661 = 59.00%**。其中 PARTIAL **73/661 = 11.04%**、NOT_COVERED **198/661 = 29.95%**、UNDECIDED **0**。按已发布规则同时公布分子、分母与 UNDECIDED 绝对数（`knowledge-production/kb-coverage-alignment-2026-v2.json` 的 `publishedRule`）。
2. **分科（COVERED/该科总数）**：数学 **138/308 = 44.81%**；物理 **81/138 = 58.70%**；化学 **59/73 = 80.82%**；生物学 **112/142 = 78.87%**。
3. **有 target 的条目**：**463/661**（390 COVERED + 73 PARTIAL）；198 条 NOT_COVERED 无 target。课标 target 悬空 0（逐节台账自检）。
4. **口径分解（不改变主口径）**：必修 **184/204 = 90.20%**；选择性必修 **190/222 = 85.59%**；选修 **16/235 = 6.81%**。NOT_COVERED 的 **189/198 = 95.45%** 落在选修模块——主口径 59.00% 被 235 条选修/活动型要求拉低，引用时必须带分母口径，不能直接当作「必修内容只覆盖六成」。
5. **教材目录侧（v16 实读）**：官方 **340 节 340/340 有落点**（0 无落点、0 不可解析）；官方 **90 章 90/90 带官方章名**；历史缺失 **13 节 13/13 已补建同名节位**（点数为 v16 实读；7 节 0 点/0 材料空壳＝化学 5 + 生物 2、6 节已挂点 18 点/材料 197 条——材料数据逐节台账）。20 行非节单列，不进入 340 分母。
6. **一句话**：**课标内容要求覆盖 390/661（59.00%），缺口集中在选修/活动型要求；教材目录结构已 340/340 有落点、90/90 章名，但册位偏差、点级归属与 7 个空壳节仍未闭合。**

## 2. 办法

### 2.1 目录只读对照（不读教材正文、原文不入库）

- **官方节表**：四份 W-7 报告（`docs/kb-textbook-toc-align/{CHEMISTRY,BIOLOGY,PHYSICS,MATH}.md`，2026-09-21）逐册表，共 **21 册 / 90 章 / 340 官方节 + 20 非节行 = 360 行**。W-7 报告自述口径：视觉读人教社官方电子教材目录页，**页图取完即删、未留存、未抄正文**（见 `PHYSICS.md`/`BIOLOGY.md` 文首）。
- **现包**：`build/kb-staging/moe-2025-four-subjects-v1.json`，v16，**425 主题 / 3,245 知识点**；sidecar 25 件共 **64,973 材料**（化学 18,307 / 生物学 13,704 / 物理 18,494 / 数学 14,468）。
- **逐节 join 顺序**：官方节同名主题（名或 slug 末段）→ 同名/含节名点所在主题 → FINAL §3「现包现状」指认覆盖主题 → 官方章容器；同级多落点全部列出。台账自检：落点缺失 0、材料孤儿绑定 0、课标 target 悬空 0（`SECTION-BY-SECTION-LEDGER.md` §1 自检表）。
- **章名判定**：以四份 W-7 报告的官方章名为准。`reference/standard_chapter_names.json` 仍有 5 处与官方不同（生物 2：选必1 第一章缺「人体的」、选必2 第四章旧名「生态环境的保护」；物理 3：必修三第十二章多「与可持续发展」、选必三第二章少顿号、选必三第五章多「和核反应」），已在 `PHYSICS.md`/`BIOLOGY.md` 登记，生物 2 处亦见 FINAL §5；本验收不拿带缺陷的 reference 名反推 90/90。
- **不读正文**：本验收只核章名/节名与节点位置，不核节内知识点表述、不核教材正文、不把教材原文或页图入库；W-7 读目录的页图已删除、未留存。

### 2.2 课标覆盖口径（alignment 台账复算）

- **台账**：`tools/kb_coverage/tables/curriculum_alignment.csv`，**661 行**（MATH 308 / PHYSICS 138 / CHEMISTRY 73 / BIOLOGY 142）；status：COVERED **390**、PARTIAL **73**、NOT_COVERED **198**、UNDECIDED **0**；有 target **463** 行。
- **逐科实算**：

| 学科 | 总数 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED | COVERED/总数 |
|---|---|---|---|---|---|---|
| 数学 MATH | 308 | 138 | 39 | 131 | 0 | 44.81% |
| 物理 PHYSICS | 138 | 81 | 14 | 43 | 0 | 58.70% |
| 化学 CHEMISTRY | 73 | 59 | 6 | 8 | 0 | 80.82% |
| 生物学 BIOLOGY | 142 | 112 | 14 | 16 | 0 | 78.87% |
| **合计** | **661** | **390** | **73** | **198** | **0** | **59.00%** |

- **已发布规则**：`knowledge-production/kb-coverage-alignment-2026-v2.json` 的 `publishedRule` 写明「覆盖率 = COVERED / (总数 − UNDECIDED)；分子、分母、UNDECIDED 绝对数同时公布」。本验收按该规则复算：**390 / (661 − 0) = 390/661 = 59.00%**。
- **判定来源**：alignment JSON `reviewedAt=2026-10-19`、reviewer=`Qwen3.8-27B（用户指定语义判定通道，2026-09-16 决定）`、`reviewState=AI_REVIEWED`。本验收**只复算/复述既有判定，不重新判定**；逐条 statement 与理由见 §3。
- **NOT_COVERED 理由字段**：CSV 的 `note` 在 196/198 行为空，理由实际落在 `evidence_phrase`（去前导标点后读取）；另 2 行 `note` 有值（论文型学术活动）。§3 的「理由」列按「note 优先、否则 evidence_phrase」取值，未改写理由。

### 2.3 与本验收分离的检索读数（不作为覆盖证据）

- **D-3 预注册（2026-10-16；其零漂移/判读纪律为批 13 v16 复跑提供参照）**：`docs/research/2026-10-16-d3-round1-prereg.md` 冻结了融合 α 臂的判读规则（A0=α0.5、A1=α0.7、逐章点估计、按章 cluster 配对 bootstrap、零漂移硬门）。该轮结果为 **不采纳、逐字回滚 α=0.5**（同文件「结果（2026-10-16）」节）；批 13 的 v16 复跑按同型零漂移门出数，**不是 α=0.7 采纳轮**。
- **批次 13 的 D-3 产物（v16 装置）实读**：设备融合臂 Recall@5 **0.7751937984496124（100/129）**、MRR **0.6228682170542638**；词面臂 **0.689922480620155（89/129）**；同路零漂移词面 **0/PASS**、融合 **0/PASS**（`zero-drift-b13.out.txt`：`lexical=PASS fused=PASS`；同路 symdiff 均 0，跨路 13、device 两腿 13 只作情报）。逐章 0.80 达标 **14/24**、未达标 10、最小 **0.0000**；主集 0.90 差距 **0.124806**（还差 17 题命中到 117/129 下沿）。
- **边界**：以上 D-3 读数是**检索装置/质量读数**，**不进入** §1 的 X/Y 分子或分母，也不构成「题库全覆盖」。检索资产侧另做只读核验：本验收实跑 `python tools/dense_build/check_asset.py` → **EXIT=0**，`.vec` 旁车 `packSha256=f6098c41…` 与 v16 包一致、`layout` 44,010 条逐条一致、`vectorCount=44010`、`DenseRecallAssembly.VECTOR_ASSET_SHA256=8e216ee…` 与旁车 `sha256` 同值；即早前 `build/agent-batch13/e/golden-pack-verify-report.md` 附加发现 1 的「仍为 v15」已被 00:50 的资产重生成覆盖，当前不再成立。D-3 读数仍只作检索参照。

## 3. NOT_COVERED 逐条理由（198 条）

### 3.1 总览

| 学科 | NOT_COVERED | 必修 | 选择性必修 | 选修 | 未设节点 | 非知识点对象 | 其他 |
|---|---|---|---|---|---|---|---|
| MATH | 131 | 3 | 3 | 125 | 124 | 6 | 1 |
| PHYSICS | 43 | 1 | 0 | 42 | 43 | 0 | 0 |
| CHEMISTRY | 8 | 1 | 0 | 7 | 8 | 0 | 0 |
| BIOLOGY | 16 | 0 | 1 | 15 | 2 | 14 | 0 |
| **合计** | **198** | **5** | **4** | **189** | **177** | **20** | **1** |

> 读法：**「未设节点」= 现包没有对应节点**；**「非知识点对象」= 论文/课题研究/职业规划等非知识点型要求**；「其他」= 学校自主开发 E 类课程（非统一知识点对象）。198 条中 189 条属选修模块，必修仅 5 条、选择性必修仅 4 条。

### 3.2 逐条表（按学科分列；课标要求为 statement 摘要，完整原文见 CSV/alignment JSON）

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
| FINAL 覆盖对照表（v16 终态） | `docs/kb-textbook-toc-align/FINAL-COVERAGE-TABLE.md` | 头部 v16 实读、§1a 终态表、90/90 章名、13 缺节终态；历史 §2–§11 未改 |
| 逐节终验台账 | `docs/kb-textbook-toc-align/SECTION-BY-SECTION-LEDGER.md` | 340 官方节 + 20 非节；落点/材料/课标条目/批11处置；自检 360/360 ✅ |
| 课标对齐 CSV | `tools/kb_coverage/tables/curriculum_alignment.csv` | 661 行 → 390/73/198/0；463 行有 target；198 条 NOT_COVERED 逐条理由 |
| 课标对齐 JSON | `knowledge-production/kb-coverage-alignment-2026-v2.json` | `publishedRule`、`total`（390/73/198/0、分母 661）、逐科 counts、661 条 statement、`reviewedAt=2026-10-19` |
| W-7 报告（4 份） | `docs/kb-textbook-toc-align/{MATH,PHYSICS,CHEMISTRY,BIOLOGY}.md` | 21 册 / 90 章 / 340 官方节 + 20 非节；官方章名；目录只读口径 |
| 章名基准件 | `docs/kb-textbook-toc-align/reference/standard_chapter_names.json` | 21 册 / 90 章；5 处已知差异（生物 2、物理 3）只作基准、不反推 90/90 |
| v16 包与 manifest | `build/kb-staging/moe-2025-four-subjects-v1.json`、`moe-2025-update-manifest.json` | 425 主题 / 3,245 点；version=16；sha256 `f6098c41…`；25 件 sidecar 64,973 材料 |
| D-3 预注册（2026-10-16；零漂移/判读纪律为批 13 v16 复跑参照） | `docs/research/2026-10-16-d3-round1-prereg.md` | 冻结规则 + 2026-10-16 结果（不采纳、回滚 α=0.5）；本验收只引规则与结论，不把 D-3 当覆盖证据 |
| 批次 13 D-3 读数 | `build/agent-batch13/d3/{zero-drift-b13.out.txt,zero-drift-b13.json,device-metrics.txt,chapter-gap-b13.out.txt}` | 产物实读：同路零漂移 0/0 PASS/PASS；融合 100/129=0.7752、词面 89/129=0.6899；14/24 章 ≥0.80、最小 0.0000 |
| v16 金标/包核验 | `build/agent-batch13/e/golden-pack-verify-report.md`、`build/agent-batch13/golden-migration-v16-chapter.json` | 报告实读：金标 129/129 命中 v16/v15；金标 chapter-only 迁移 1 条（idx=12 限制酶，slug/题面/条数未动）；dense `.vec` 本验收实跑 `check_asset.py` EXIT=0，当前与 v16 包一致（早前报告 red 已被重生成覆盖） |

## 5. 边界与未覆盖声明

1. **不是「题库全覆盖」**：390/661 是课标内容要求 status 口径，不表示所有可能考题、题型、难度或解法都已入库；本验收没有、也不能据此承诺题库全覆盖。
2. **不是教材正文覆盖**：只核章/节名与节点位置，不核正文表述、例题、实验、插图；W-7 目录页图已删除、未留存，教材原文不入库。
3. **NOT_COVERED 198 条是明确声明，不是隐藏缺口**：177 条「未设节点」、20 条「非知识点对象」、1 条「学校自主开发 E 类课程」；按模块 5 必修 / 4 选择性必修 / 189 选修。明细见 §3，未用 PARTIAL 或估算值回填。
4. **PARTIAL 73 条不计入分子**：部分覆盖（73/661=11.04%）单列；若把 PARTIAL 并入分子，会得到 463/661=70.05%，但这不是已发布规则，本验收不采用。
5. **340/340 有落点 ≠ 册位/点级全对**：同名节 221 条中含少量册位仍错者（如化学必修一「原子结构与元素周期表」落在选必2）；册位偏差以 FINAL §4.3 历史节与逐节台账「现包落点」列为准，本验收不重判、不掩盖。
6. **13 缺节「已补建」≠ 内容完成**：13/13 同名节位在册（点数 v16 实读、材料数据逐节台账），但 7 节仍是 0 点/0 材料空壳（化学 5 + 生物 2）；6 节已挂点 18 点、材料 197 条。空壳节和材料缺口明确列为未完成内容，不计入「内容已覆盖」。
7. **材料 64,973 条是 sidecar 绑定数，不是教材原文**：材料为结构化总结/授权教辅绑定，计数只证明绑定存在，不证明每条材料正确、充分或适合学生；本验收不核材料质量。
8. **课标判定为 AI 复核，不含逐条人工审校**：alignment JSON 的 661 条均为 `AI_REVIEWED`（Qwen3.8-27B，2026-10-19），本验收只复算其 status，不重新裁决；若要对外发布「覆盖率」，应同时披露该判定通道。
9. **分母含 235 条选修/活动型要求**：59.00% 是与 661 条全量分母对应的口径；必修 90.20%、选择性必修 85.59% 是同一批判定的分解，不能脱离分母单独引用。
10. **检索读数与覆盖结论分离**：D-3 的 0.7752/0.6899/零漂移读数只证明检索装置与金标在 v16 上的可复现性，不是覆盖证据；当前 dense `.vec` 已与 v16 包一致（`check_asset.py` EXIT=0），但检索读数仍不能替代覆盖结论。
11. **reference 件不是无缺陷权威**：`standard_chapter_names.json` 仍有 5 处与 W-7 官方名不同（生物 2、物理 3）；本验收以 W-7 官方名为准，reference 只作册/章数基准。
12. **未验证项**：本验收未运行 Gradle/设备/仪器化；未读取教材正文；未对 64,973 条材料做逐条质量复核；未对 198 条 NOT_COVERED 做二次人工语义复核。dense `.vec` 与 v16 包的一致性已由 `python tools/dense_build/check_asset.py`（EXIT=0）核验，不再列为未验证。

## 6. 复现与读法

```
# 包身份
sha256sum build/kb-staging/moe-2025-four-subjects-v1.json
# 课标 status 复算（661 行 → 390/73/198/0）
python - <<'PY'
import csv, collections
rows=list(csv.DictReader(open("tools/kb_coverage/tables/curriculum_alignment.csv", encoding="utf-8-sig")))
print(len(rows), collections.Counter(r["status"] for r in rows))
PY
```

> 结论引用格式（必须带口径）：**「覆盖课标内容要求 390/661（59.00%，COVERED/(总数−UNDECIDED)；PARTIAL 73，NOT_COVERED 198，UNDECIDED 0）」**。不得简写为「覆盖率 59%」而不带分母，不得写成「题库全覆盖」。
