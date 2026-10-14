# 选修覆盖单列报告：NOT_COVERED 逐条与计数（2026-10-13）

> 数据源：`knowledge-production/kb-coverage-alignment-2026-v2.json`（本报告撰写时实读；
> schemaVersion 1、alignmentId `kb-coverage-alignment-2026-v2`、reviewer `Qwen3.8-27B（用户指定语义判定通道，2026-09-16 决定）`、reviewedAt `2026-10-14`；
> baseline：2025 课标内容要求（source-register-2025-v1；2026-09-18 九科 PDF 指纹全部逐字节核验——数学/语/英/政/史/地 6 份重下并核验，物/化/生 3 份在盘核验）；targetPack：`moe-2025-four-subjects-v1`；publishedRule：覆盖率 = COVERED / (总数 − UNDECIDED)；分子、分母、UNDECIDED 绝对数同时公布）
>
> **口径**：选修 = `module` 含「选修」且不含「必修」——因此「选择性必修」不计入选修（其名含「必修」）；
> NOT_COVERED = 台账 `status` 原值，逐条列出、不改写。主线 = 其余（必修 + 选择性必修）。
>
> **计数**：选修 NOT_COVERED = **189 条**（逐条见 §3）；主线 NOT_COVERED = **9 条**（逐条见 §4）。
>
> 生成：`build/report-batch10/gen_elective_coverage.py`（读同一 JSON 复算；只读，不改数据）。

## 1. 总览（本报告实读复算）

| 范围 | 总数 | COVERED | PARTIAL | NOT_COVERED | UNDECIDED |
|---|---|---|---|---|---|
| 全部 | 661 | 390 | 73 | 198 | 0 |
| 选修（本报告范围） | 235 | 16 | 30 | 189 | 0 |
| 主线（必修 + 选择性必修） | 426 | 374 | 43 | 9 | 0 |

## 2. 选修 NOT_COVERED 分列摘要（按学科 × module）

| 学科 | 选修 NOT_COVERED | 选修总数 | 涉及 module 数 |
|---|---|---|---|
| MATH | 125 | 152 | 15 |
| PHYSICS | 42 | 51 | 3 |
| CHEMISTRY | 7 | 10 | 3 |
| BIOLOGY | 15 | 22 | 22 |
| **合计** | **189** | **235** | — |

## 3. 选修 NOT_COVERED 逐条（189 条）

> 行格式：短引用 + 条目原文 ｜ 判定依据：台账 evidencePhrase；候选短引用 = 全引用去掉 `candidate:moe:2025:` 前缀。非 NOT_COVERED 的选修条目（COVERED/PARTIAL）不在本表。

### 3.1 MATH（数学，125 条）

**选修A：微积分（16 条）**

- `math:curriculum-text/elective-a-calculus-candidate-001` 通过典型收敛数列的极限过程 (当狀→ ∞ 时,1狀→0, 狀狀+ 1→ 1,狇狀→0(0< |狇|<1) ,犪 1狀 →1(犪>0) ) ,建立并理解数列极限的 ε.犖定义。 ｜ 判定依据：数列极限未设节点（选修A大学先修）
- `math:curriculum-text/elective-a-calculus-candidate-002` 探索并证明基本性质:收敛数列是有界数列。 ｜ 判定依据：数列极限性质未设节点
- `math:curriculum-text/elective-a-calculus-candidate-003` 通过典型单调有界数列1狀{ }, 狀狀+ 1{ } ,{狇狀} (0<狇<1) ,{犪 1狀 } (犪>0) 的收敛过程,理解基本事实:单调有界数列必有极限。 ｜ 判定依据：单调有界收敛准则未设节点
- `math:curriculum-text/elective-a-calculus-candidate-004` 掌握数列极限的四则运算法则 。 ｜ 判定依据：极限四则运算未设节点
- `math:curriculum-text/elective-a-calculus-candidate-005` 通过典型数列的收敛性,理解e的意义。 ｜ 判定依据：e的极限定义未设节点
- `math:curriculum-text/elective-a-calculus-candidate-006` 通过典型函数的极限过程 (当狓→犪时,狓2→犪2;当狓→犪时, s i n狓→s i n犪;当狓→0时,犪狓→1(犪>0,且犪≠1) ) ,理解函数极限的 ε.δ定义。 ｜ 判定依据：函数极限未设节点
- `math:curriculum-text/elective-a-calculus-candidate-007` 掌握基本初等函数极限的四则运算 。 ｜ 判定依据：函数极限运算未设节点
- `math:curriculum-text/elective-a-calculus-candidate-008` 掌握两个重要函数极限:l i m狓→0 s i n狓狓 = 1,l i m狓→0(1 +狓) 1狓 = e,并会 求其简单变形的极限。 ｜ 判定依据：两个重要极限未设节点
- `math:curriculum-text/elective-a-calculus-candidate-009` 理解连续函数的定义。 ｜ 判定依据：连续函数定义未设节点
- `math:curriculum-text/elective-a-calculus-candidate-012` 通过导函数的概念,掌握二阶导数的概念,了解二阶导数的物 理意义与几何意义。 ｜ 判定依据：二阶导数未设节点
- `math:curriculum-text/elective-a-calculus-candidate-014` 理解并证明拉格朗日中值定理 ,并能用其讨论函数的单调性。 ｜ 判定依据：拉格朗日中值定理未设节点
- `math:curriculum-text/elective-a-calculus-candidate-017` 了解微分的概念及其实际意义 ,并会用符号表示。 ｜ 判定依据：微分未设节点
- `math:curriculum-text/elective-a-calculus-candidate-018` 通过等分区间求特殊曲边梯形面积的极限过程 ,理解定积分的 概念及其几何意义与物理意义 。 ｜ 判定依据：定积分未设节点
- `math:curriculum-text/elective-a-calculus-candidate-019` 在单调函数定积分的计算过程中 ,通过微分感悟积分与导数的 关系,理解并掌握微积分基本定理 (牛顿.莱布尼茨公式犳(犫)-犳(犪)= ∫ 犫 犪犳′(狋)d狋) 。 ｜ 判定依据：微积分基本定理未设节点
- `math:curriculum-text/elective-a-calculus-candidate-020` 会利用基本初等函数的导数表和牛顿 .莱布尼茨公式 ,求一些 简单函数的定积分。 ｜ 判定依据：定积分计算未设节点
- `math:curriculum-text/elective-a-calculus-candidate-021` 会利用定积分计算某些封闭图形的面积 ,计算球、圆锥、圆台 和三棱锥、三棱台的体积 ;能利用定积分解决简单的做功问题和重心 问题。 ｜ 判定依据：定积分应用（面积/体积/做功）未设节点

**选修A：空间向量与代数（18 条）**

- `math:curriculum-text/elective-a-space-vector-algebra-candidate-004` 掌握向量组的线性相关性,并能加以判断。 ｜ 判定依据：线性相关性未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-005` 掌握向量的线性运算,理解向量空间与子空间的概念 。 ｜ 判定依据：向量空间/子空间未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-006` 通过几何直观引入矩阵概念 ,掌握矩阵的三种基本运算及其 性质。 ｜ 判定依据：矩阵未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-007` 了解正交矩阵及其基本性质,能用代数方法解决几何问题。 ｜ 判定依据：正交矩阵未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-008` 掌握行列式定义与性质,会计算行列式。 ｜ 判定依据：行列式未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-009` 通过实例,探索三元一次方程组的求解过程 ,理解三元一次方 程组的常用解法 (高斯消元法) ,会用矩阵表示三元一次方程组 。 ｜ 判定依据：线性方程组/高斯消元未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-010` 掌握三元齐次线性方程组的解法 ,会表示一般解。 ｜ 判定依据：齐次线性方程组未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-011` 掌握非齐次线性方程组有解的判定 ,建立线性方程组的理论 基础。 ｜ 判定依据：非齐次方程组判定未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-012` 探索三元一次方程组解的结构 ,会表示一般解。 ｜ 判定依据：方程组解的结构未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-013` 理解克拉默 (C r a m e r)法则,会用克拉默法则求解三元一次 方程组。 ｜ 判定依据：克拉默法则未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-014` 通过向量的坐标表示,建立空间平面的方程。 ｜ 判定依据：空间平面方程未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-015` 理解空间直线方程的含义,会用方程表示空间直线。 ｜ 判定依据：空间直线方程未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-017` 了解平面变换的含义,理解平面的等距变换,特别是三种基本 等距变换:直线反射、平移、旋转。 ｜ 判定依据：平面等距变换未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-018` 了解平面对称图形及变换群概念 。 ｜ 判定依据：变换群未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-019` 掌握常见平面等距变换及其矩阵表示 。 ｜ 判定依据：等距变换矩阵表示未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-020` 了解空间变换的含义,理解空间的等距变换,特别是三种常见 等距变换:平面反射、平移、旋转。 ｜ 判定依据：空间等距变换未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-021` 了解空间对称图形及变换群。 ｜ 判定依据：空间变换群未设节点
- `math:curriculum-text/elective-a-space-vector-algebra-candidate-022` 掌握常见空间等距变换及其矩阵表示 。 ｜ 判定依据：空间等距变换矩阵表示未设节点

**选修A：概率与统计（12 条）**

- `math:curriculum-text/elective-a-probability-statistics-candidate-001` 借助具体实例,了解连续型随机变量及其分布 ,体会连续型随 机变量与离散型随机变量的共性与差异 。 ｜ 判定依据：连续型随机变量未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-002` 结合生活中的实例,了解几个重要连续型随机变量的分布 :均 匀分布、正态分布、卡方分布、狋.分布,理解这些分布中参数的意义 , 能进行简单应用。 ｜ 判定依据：连续型分布（均匀/卡方/t）未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-003` 了解连续型随机变量的均值和方差 ,知道均匀分布 、正态分 布、卡方分布、狋.分布的均值和方差及其意义。 ｜ 判定依据：连续型数字特征未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-004` 在学习一维离散型随机变量的基础上 ,通过实例,了解二维离 散型随机变量概念及其分布列 、数字特征 (均值、方差、协方差、相关 系数) ,并能解决简单的实际问题。了解两个随机变量的独立性。 ｜ 判定依据：二维随机变量/协方差未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-005` 在学习一维正态随机变量的基础上 ,通过具体实例,了解二维 正态随机变量及其联合分布,以及联合分布中参数的意义。 ｜ 判定依据：二维正态未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-006` .参数估计 借助对具体实际问题的分析,知道矩估计和极大似然估计这两种参 数估计方法,了解参数估计原理,能解决一些简单的实际问题。 ｜ 判定依据：参数估计未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-007` 了解假设检验的统计思想和基本概念 。 ｜ 判定依据：假设检验未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-008` 借助具体实例,了解正态总体均值和方差检验的方法 ,了解两 个正态总体均值比较的方法。 ｜ 判定依据：均值方差检验未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-009` 结合具体实例,了解总体分布的拟合优度检验 。 ｜ 判定依据：拟合优度检验未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-010` 了解二维正态分布及其参数的统计含义 。 ｜ 判定依据：二维正态统计含义未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-011` 了解二元线性回归模型,会用最小二乘原理对模型中的参数进 行估计。 ｜ 判定依据：二元线性回归未设节点
- `math:curriculum-text/elective-a-probability-statistics-candidate-012` 运用二元线性回归模型解决简单的实际问题 。 犅类课程 ｜ 判定依据：二元回归应用未设节点

**选修B：微积分（14 条）**

- `math:curriculum-text/elective-b-calculus-candidate-001` 通过典型数列 1狀{ }, 狀狀+ 1{ } , {狇狀} (0< |狇| <1) ,了解数列的 极限,掌握极限的符号,了解基本事实:单调有界数列必有极限。 ｜ 判定依据：数列极限（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-002` 通过具体函数 犳(狓)=狓2,犳(狓)=1狓,犳(狓)=槡狓,犳(狓)= 犪狓(犪>0,且犪≠1) ,犳(狓)= c o s狓,了解函数极限l i m狓→狓0犳(狓)=犃 和连续 的概念,掌握极限的符号,了解闭区间上连续函数的性质 。 ｜ 判定依据：函数极限/连续（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-003` 通过导数概念,理解二阶导数的概念,了解二阶导数的物理意 义与几何意义;掌握一些基本初等函数的一阶导数与二阶导数 。 ｜ 判定依据：二阶导数（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-004` 理解拉格朗日中值定理,了解它的几何解释。 ｜ 判定依据：拉格朗日中值定理（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-008` 了解微分的概念及其实际意义 ,会用符号表示。 ｜ 判定依据：微分（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-009` 了解闭区间上连续函数定积分的概念 ,理解其几何意义与物理 意义。 ｜ 判定依据：定积分（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-010` 能用等分区间方法计算特殊的黎曼和 。 ｜ 判定依据：黎曼和未设节点
- `math:curriculum-text/elective-b-calculus-candidate-011` 利用犳(狓)的单调性、等分区间的方法 、拉格朗日中值定理 , 推导牛顿.莱布尼茨公式犳(犫)-犳(犪)=∫ 犫 犪犳′(狋)d狋(微积分基本定理) 。 ｜ 判定依据：牛顿-莱布尼茨公式（B类）未设节点
- `math:curriculum-text/elective-b-calculus-candidate-012` 会利用定积分计算某些封闭平面图形的面积 ,计算球、圆锥、 圆台和三棱锥、三棱台的体积。了解祖/G21原理。 ｜ 判定依据：定积分应用/祖暅原理未设节点
- `math:curriculum-text/elective-b-calculus-candidate-013` 通过简单实例,理解二元函数的背景。 ｜ 判定依据：二元函数未设节点
- `math:curriculum-text/elective-b-calculus-candidate-014` 了解偏导数的定义,能计算一些简单函数的偏导数 。例如,已 知犳(狓)与犵(狔)分别是基本初等函数 ,会求犳(狓)+犵(狔) ,犳(狓) · 犵(狔)的偏导数。 ｜ 判定依据：偏导数未设节点
- `math:curriculum-text/elective-b-calculus-candidate-015` 会求一些简单二元函数的驻点 ,并能求相应实际问题中的 极值。 ｜ 判定依据：驻点未设节点
- `math:curriculum-text/elective-b-calculus-candidate-016` 利用等高线法,会求一次函数犳(狓,狔)=犪 狓+犫狔在闭凸多边 形区域上的最大值和最小值。 ｜ 判定依据：等高线法未设节点
- `math:curriculum-text/elective-b-calculus-candidate-017` 会求闭圆域、闭椭圆域上二元二次函数的最大值和最小值 。 ｜ 判定依据：二元二次函数最值未设节点

**选修B：空间向量与代数（9 条）**

- `math:curriculum-text/elective-b-space-vector-algebra-candidate-004` 掌握向量组的线性相关性,并能加以判断。 ｜ 判定依据：线性相关性（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-005` 掌握向量的线性运算 ,理解 (低维)向量空间与子空间的 概念。 ｜ 判定依据：向量空间/子空间（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-007` 通过几何直观引入矩阵概念 ,掌握矩阵的三种基本运算及其 性质。 ｜ 判定依据：矩阵（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-008` 掌握行列式定义与性质,会计算行列式。 ｜ 判定依据：行列式（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-009` 通过实例,探索三元一次方程组的求解过程 ,掌握三元一次方 程组的常用解法 (高斯消元法) ,会用矩阵表示三元一次方程组 。 ｜ 判定依据：线性方程组/高斯消元（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-010` 掌握三元齐次线性方程组的解法 ,会表示一般解。 ｜ 判定依据：齐次线性方程组（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-011` 掌握非齐次线性方程组有解的判定 ,建立线性方程组的理论 基础。 ｜ 判定依据：非齐次方程组判定（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-012` 探索三元一次方程组解的结构 ,会表示一般解。 ｜ 判定依据：方程组解的结构（B类）未设节点
- `math:curriculum-text/elective-b-space-vector-algebra-candidate-013` 理解克拉默 (C r a m e r)法则,会用克拉默法则求解三元一次 方程组。 ｜ 判定依据：克拉默法则（B类）未设节点

**选修B：应用统计（16 条）**

- `math:curriculum-text/elective-b-applied-statistics-candidate-001` 借助具体实例,了解连续型随机变量及其分布 ,体会连续型随 机变量与离散型随机变量的共性与差异 。 ｜ 判定依据：连续型随机变量（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-002` 结合生活中的实例,了解几个重要连续型随机变量的分布 :均 匀分布、正态分布、卡方分布、狋.分布,理解这些分布中参数的意义 , 能进行简单应用。 ｜ 判定依据：连续型分布（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-003` 了解连续型随机变量的均值和方差 ,知道均匀分布 、正态分 布、卡方分布、狋.分布的均值和方差及其意义。 ｜ 判定依据：连续型数字特征（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-004` 在学习一维离散型随机变量的基础上 ,通过实例,了解二维离 散型随机变量概念及其分布列 、数字特征 (均值、方差、协方差、相关 系数) ,并能解决简单的实际问题。了解两个随机变量的独立性。 ｜ 判定依据：二维随机变量（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-005` 在学习一维正态随机变量的基础上 ,通过具体实例,了解二维 正态随机变量及其联合分布,以及联合分布中参数的意义。 ｜ 判定依据：二维正态（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-006` .参数估计 借助对具体实际问题的分析,知道矩估计和极大似然估计这两种参 数估计方法,了解参数估计原理,能解决一些简单的实际问题。 ｜ 判定依据：参数估计（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-007` 了解假设检验的统计思想和基本概念 。 ｜ 判定依据：假设检验（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-008` 借助具体实例,了解正态总体均值和方差检验的方法 ,了解两 个正态总体均值比较的方法。 ｜ 判定依据：均值方差检验（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-009` 结合具体实例,了解总体分布的拟合优度检验 。 ｜ 判定依据：拟合优度检验（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-010` 了解二维正态分布及其参数的统计含义 。 ｜ 判定依据：二维正态统计含义（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-011` 了解二元线性回归模型,会用最小二乘原理对模型中的参数进 行估计。 ｜ 判定依据：二元线性回归（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-012` 运用二元线性回归模型解决简单的实际问题 。 ｜ 判定依据：二元回归应用（B类）未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-013` 借助具体实例,了解聚类分析的意义。 ｜ 判定依据：聚类分析未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-014` 借助具体实例,了解几种聚类分析的方法,能解决一些简单的 实际问题。 ｜ 判定依据：聚类方法未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-015` 借助具体实例,了解正交设计原理。 ｜ 判定依据：正交设计未设节点
- `math:curriculum-text/elective-b-applied-statistics-candidate-016` 借助具体实例,了解正交表,能用正交表进行实验设计。 ｜ 判定依据：正交表实验设计未设节点

**选修B：模型（6 条）**

- `math:curriculum-text/elective-b-models-candidate-001` 结合实际问题,了解一维线性模型,理解一次函数与均匀变化 的关系,并能发现生活中均匀变化的实际问题 。 ｜ 判定依据：一维线性模型未设节点
- `math:curriculum-text/elective-b-models-candidate-002` 结合实际问题,了解二维线性模型,探索平面上一些图形的变 化,并能理解一维线性模型与二维线性模型的异同 (例如,系数矩阵是 对角阵) 。 ｜ 判定依据：二维线性模型未设节点
- `math:curriculum-text/elective-b-models-candidate-003` 结合实际问题,了解三维线性模型 ,如经济学上的投入产出 模型。 ｜ 判定依据：三维线性模型/投入产出未设节点
- `math:curriculum-text/elective-b-models-candidate-007` 借助具体实例,理解平面上的参变数模型,如弹道模型。 ｜ 判定依据：参数方程未设节点
- `math:curriculum-text/elective-b-models-candidate-008` 借助具体实例,理解空间中的参变数模型,如螺旋曲线。 ｜ 判定依据：空间参数方程未设节点
- `math:curriculum-text/elective-b-models-candidate-009` 借助一些用参变数方程描述的物理问题与几何问题 ,理解参变 数的意义,掌握参变数变化的范围。 犆类课程 ｜ 判定依据：参变数意义未设节点

**选修C：逻辑推理初步（3 条）**

- `math:curriculum-text/elective-c-logic-candidate-002` .数学推理的前提 理解同一律、矛盾律、排中律的含义,通过实例认识它们在数学推 理中的作用,能在数学推理中认识推理前提的重要性 。能通过实例,区 分排中律与矛盾律,能在推理中正确运用排中律。 ｜ 判定依据：逻辑三律（同一/矛盾/排中）未设节点
- `math:curriculum-text/elective-c-logic-candidate-003` .数学推理的类型 结合学过的数学实例和生活中的实例 ,理解演绎推理、归纳和类比 推理,在这些推理的过程中,认识数学推理的传递性。知道利用推理能 够得到和验证数学的结果。通过数学和生活中的实例,认识或然性推理 和必然性推理的区别。 ｜ 判定依据：推理类型（演绎/归纳/类比）未设节点
- `math:curriculum-text/elective-c-logic-candidate-005` .公理化思想 通过数学史和其他领域的典型事例 ,了解数学公理化的含义,了解 公理体系的独立性、相容性、完备性,了解公理化思想在数学、自然科 学及社会科学中的运用,体会公理化思想的意义和价值 。 ｜ 判定依据：公理化思想未设节点

**选修C：数学模型（5 条）**

- `math:curriculum-text/elective-c-mathematical-models-candidate-001` 存款贷款模型 (指数函数模型) 通过对存款等实际问题的分析 ,抽象出复利模型;通过对住房贷款 等实际问题的分析,抽象出等额本金付款模型。了解这些模型各自的特 点,能用这些模型解决简单的实际问题 。 ｜ 判定依据：存款贷款模型未设节点
- `math:curriculum-text/elective-c-mathematical-models-candidate-002` 投入产出模型 (线性方程组模型) 了解投入产出模型的背景和意义 ,理解如何通过线性方程组中的系 数和解来约束自变量,从而实现组合生产的计划。能用投入产出模型分 析并解决简单的实际问题。 ｜ 判定依据：投入产出模型未设节点
- `math:curriculum-text/elective-c-mathematical-models-candidate-004` 凯恩斯模型 (经济理论模型) 了解如何通过收入、消费和投资之间的关系建立数学模型 ,体会模 型中系数的乘数效应,体会扩大消费与经济发展、增加国民收入之间的 关系,能用模型解释简单的经济现象 。 ｜ 判定依据：凯恩斯模型未设节点
- `math:curriculum-text/elective-c-mathematical-models-candidate-005` 生产函数模型 (对数线性模型) 了解生产理论中柯布.道格拉斯 (C o b b . D o ugl a s)生产函数 ,知道 如何用数学语言表达生产与劳动投入 、资本投入之间的关系,知道如何 把这样的表达转化为对数线性模型 、如何对其中的参数进行估计,能解 决简单的实际问题。 ｜ 判定依据：生产函数/对数线性模型未设节点
- `math:curriculum-text/elective-c-mathematical-models-candidate-008` 信度评价模型 (L ogi s t i c回归模型) 对于银行贷款用户、信用卡用户等涉及信度的问题 ,知道用L ogi s . t i c回归模型进行信度评级的道理 ,知道构造两级 (好、差)或者三级 (好、中、差)进行评价的方法,并会简单应用。 ｜ 判定依据：Logistic回归未设节点

**选修C：社会调查与数据分析（11 条）**

- `math:curriculum-text/elective-c-social-survey-candidate-001` 结合实例,了解社会调查的使用范围、分类和意义。 ｜ 判定依据：社会调查未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-002` 针对具体问题,了解社会调查的基本步骤:项目确定、方案设 计、组织实施、数据分析、形成报告。 ｜ 判定依据：调查步骤未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-003` 结合实例,了解调查方案设计的基本框架 :目的、内容、对 象、项目、方式、方法等。 ｜ 判定依据：调查方案框架未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-004` 结合实例,探索调查方案的可行性评估。 ｜ 判定依据：可行性评估未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-005` 结合实例,了解问卷设计的主要问题 :问卷的结构与常用量 ｜ 判定依据：问卷设计未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-006` 结合实例,掌握社会调查基本方法:文案调查法、观察法、访 谈法、专家调查法 (D e lph iM e t h o d) 、电话法等。 ｜ 判定依据：调查方法未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-007` .抽样设计 在必修课程学习的简单随机抽样 、分层抽样等抽样方法的基础上 , 了解二阶与多阶抽样,能根据具体情境选择合适的抽样方法 。 ｜ 判定依据：多阶抽样未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-008` 结合具体实例,整理调查数据 ,了解常用统计图表 :频数表、 交叉表、直方图、茎叶图、扇形图、雷达图、箱线图,以及常用统计 量:均值、众数、中位数、百分位数,能确定各种抽样方法的样本量 。 ｜ 判定依据：统计图表与统计量未设节点（频数表/茎叶图/箱线图等）
- `math:curriculum-text/elective-c-social-survey-candidate-009` 结合具体实例,了解相关分析、回归分析、多元统计分析。 ｜ 判定依据：多元统计分析未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-010` .社会调查数据报告 掌握社会调查报告的基本要求及基本内容 ,能做出简单的、完整的 社会调查数据报告。 ｜ 判定依据：调查数据报告未设节点
- `math:curriculum-text/elective-c-social-survey-candidate-011` .社会调查案例选讲 通过典型案例的学习,理解社会调查的意义。 犇类课程 ｜ 判定依据：社会调查案例未设节点

**选修D：美与数学（4 条）**

- `math:curriculum-text/elective-d-beauty-math-candidate-001` .美与数学的简洁 数学可以刻画现实世界中的简洁美 。例如,太阳、满月、车轮、井 盖形状等美的共性与圆相关,抛物运动、行星运动轨迹等美的共性与二 次曲线相关,脱氧核糖核酸 (D N A)结构、向日葵花盘 、海螺等美的 共性与特殊曲线相关,家具、日用品、冷却塔、建筑物外形等美的共性 与简单曲面相关,雪花、云彩、群山、海岸线、某些现代设计等美的共 性与分形相关。 ｜ 判定依据：简洁美未设节点
- `math:curriculum-text/elective-d-beauty-math-candidate-002` .美与数学的对称 数学可以刻画现实世界中的对称美 。例如,某些动物形体、飞机造 型、某些建筑物外形等美的共性与空间反射对称相关 ,剪纸、脸谱、风 筝等传统艺术美的共性与轴对称相关 ,晶体等美的共性与中心对称相 关,带饰、面饰等美的共性与平移 、中心对称 、轴对称相关 。循环赛 制、守恒定律也具有对称美。 ｜ 判定依据：对称美未设节点
- `math:curriculum-text/elective-d-beauty-math-candidate-003` .美与数学的周期 数学可以刻画现实世界中的周期美 。例如,昼夜交替、四季循环、 日月星辰运动规律、海洋波浪等美的共性与周期相关 ,乐曲创作、图案 设计中美的共性与周期相关。 ｜ 判定依据：周期美未设节点
- `math:curriculum-text/elective-d-beauty-math-candidate-004` .美与数学的和谐 数学可以刻画现实世界中的和谐美 。例如,人体结构、建筑物、国 旗、绘画、优选法等美的共性与黄金分割相关 ,苗木生长、动物繁殖、 向日葵种子排列规律等美的共性与斐波那契数列相关 。 ｜ 判定依据：和谐美/黄金分割/斐波那契未设节点

**选修D：音乐中的数学（3 条）**

- `math:curriculum-text/elective-d-music-math-candidate-003` .乐曲的节拍与分数 乐曲的小节、拍、拍号与分数相关,套曲的钢琴演奏与最小公倍数 相关。 ｜ 判定依据：节拍与分数未设节点
- `math:curriculum-text/elective-d-music-math-candidate-004` .乐器中的数学 键盘乐器 (如钢琴等) 、弦乐器 (如小提琴、二胡等) 、管乐器 (如 长笛等)的发声、共鸣等,都与数学相关。 ｜ 判定依据：乐器中的数学未设节点
- `math:curriculum-text/elective-d-music-math-candidate-005` .乐曲中的数学 乐曲中的高潮点、乐曲调性的转换点,常与黄金分割相关;乐曲的 创作既与平移、反射、伸缩等变换相关,也与排列、组合相关。 ｜ 判定依据：乐曲中的数学未设节点

**选修D：美术中的数学（4 条）**

- `math:curriculum-text/elective-d-art-math-candidate-001` .绘画与数学 名画中的数学元素,绘画中的平移与对称,绘画中的黄金分割,绘 画中的透视几何。 ｜ 判定依据：绘画与数学未设节点
- `math:curriculum-text/elective-d-art-math-candidate-002` .其他美术作品中的数学 雕塑中的黄金分割,建筑中的对称,工艺品中的对称,邮票中的数 学,书法中的黄金分割。 ｜ 判定依据：美术作品中的数学未设节点
- `math:curriculum-text/elective-d-art-math-candidate-003` .美术与计算机 计算机绘画的发展背景,计算机绘画所需的硬件和软件 ,计算机绘 画实例。 ｜ 判定依据：美术与计算机未设节点
- `math:curriculum-text/elective-d-art-math-candidate-004` .美术家的数学思想 著名美术家的数学思想。 ｜ 判定依据：美术家的数学思想未设节点

**选修D：体育运动中的数学（3 条）**

- `math:curriculum-text/elective-d-sports-math-candidate-002` .运动成绩的数据分析 通过健康指标和运动成绩的数据 ,运用概率与统计知识寻求规律 、 探索合理方案。例如,通过日常运动和健康状况的数据 ,分析运动与健 康的关系。 ｜ 判定依据：运动成绩数据分析未设节点
- `math:curriculum-text/elective-d-sports-math-candidate-003` .运动赛事中的运筹帷幄 知道能借助图论、运筹等数学知识分析体育赛事的规律 ,进行合理 安排,提升教练员的指挥策略,改善运动员赛场上的应对策略 。 ｜ 判定依据：图论/运筹未设节点
- `math:curriculum-text/elective-d-sports-math-candidate-004` .体育用具及设施中的数学知识 知道在大多数体育运动用具和场馆的设计中都运用了数学知识 。例 如,足球、乒乓球的制作,网球拍的构造,标准跑道的规划;通过数学 曲面感悟 “鸟巢”“水立方”等体育设施的设计原理。 犈类课程 ｜ 判定依据：体育用具设施数学未设节点

**选修E：学校自主开发或选用课程（1 条）**

- `math:curriculum-text/elective-e-school-developed-candidate-001` 野、日常生活、地方特色的数学课程,还包括大学数学的先修课程等。 拓展视野的数学课程 例如,机器人与数学、对称与群、球面上的 几何、欧拉公式与闭曲面分类、数列与差分、初等数论初步。 日常生活的数学课程 例如,生活中的数学、家庭理财与数学。 地方特色的数学课程 例如,地方建筑与数学、家乡经济发展的社 会调查与数据分析。 大学数学的先修课程 包括:微积分、解析几何与线性代数、概率 论与数理统计。 ｜ 判定依据：学校自主开发课程（E类）非统一知识点对象

### 3.2 PHYSICS（物理，42 条）

**选修1：物理学与社会发展（14 条）**

- `physics:curriculum-text/elective-physics-1-candidate-001` 了解古希腊的宇宙观与我国古代的宇宙观。了解哥白尼日心 说对宇宙观的冲击。了解开普勒定律对牛顿发现万有引力定律的重要 作用。 ｜ 判定依据：宇宙观史实未设节点
- `physics:curriculum-text/elective-physics-1-candidate-002` 了解伽利略的实验方法和逻辑方法对牛顿力学诞生的贡献。 了解牛顿力学的成就和局限性。 ｜ 判定依据：物理学史未设节点
- `physics:curriculum-text/elective-physics-1-candidate-003` 了解 19 世纪末物理学的发现对于近代物理学革命的意义。 ｜ 判定依据：近代物理学革命史未设节点
- `physics:curriculum-text/elective-physics-1-candidate-005` 了解相对论的时空观,知道质能关系的意义,了解相对论与 量子论对人类认识的影响。 ｜ 判定依据：相对论/质能关系影响未设节点
- `physics:curriculum-text/elective-physics-1-candidate-006` 了解我国物理学家的成果与贡献。 ｜ 判定依据：我国物理学家成果未设节点
- `physics:curriculum-text/elective-physics-1-candidate-007` 了解蒸汽机的发明对第一次工业革命的推动作用。 ｜ 判定依据：工业革命史未设节点
- `physics:curriculum-text/elective-physics-1-candidate-008` 了解电磁学的发展对第二次工业革命的推动作用。 ｜ 判定依据：电磁学发展史未设节点
- `physics:curriculum-text/elective-physics-1-candidate-009` 了解集成电路的进展,了解微电子技术的应用对社会发展的 影响。 ｜ 判定依据：集成电路/微电子未设节点
- `physics:curriculum-text/elective-physics-1-candidate-010` 了解信息技术的应用对社会发展的影响。 ｜ 判定依据：信息技术影响未设节点
- `physics:curriculum-text/elective-physics-1-candidate-011` 了解核技术的应用对社会发展的影响。 ｜ 判定依据：核技术应用影响未设节点
- `physics:curriculum-text/elective-physics-1-candidate-012` 了解可再生能源与不可再生能源在生活中应用的实例。 ｜ 判定依据：能源实例未设节点
- `physics:curriculum-text/elective-physics-1-candidate-013` 能列举微电子技术在生活中的应用实例。了解电视广播技术 的发展对生活的影响。 ｜ 判定依据：微电子技术实例未设节点
- `physics:curriculum-text/elective-physics-1-candidate-014` 了解计算机、手机等发展的大致历程及其对生活的影响。 ｜ 判定依据：计算机/手机历程未设节点
- `physics:curriculum-text/elective-physics-1-candidate-016` 了解航天技术对生活的影响。 ｜ 判定依据：航天技术影响未设节点

**选修2：物理学与技术应用（16 条）**

- `physics:curriculum-text/elective-physics-2-candidate-001` 了解声波的反射在 B 超中的应用。 ｜ 判定依据：B超应用未设节点
- `physics:curriculum-text/elective-physics-2-candidate-003` 了解 X 射线对人体不同组织的穿透性差异。 ｜ 判定依据：X射线医学应用未设节点
- `physics:curriculum-text/elective-physics-2-candidate-004` 了解 CT 扫描的基本原理。 ｜ 判定依据：CT扫描未设节点
- `physics:curriculum-text/elective-physics-2-candidate-005` 了解典型射线的特性及其防护措施,知道这些射线在医学中 的应用及其对人体的影响。 ｜ 判定依据：射线医学防护未设节点
- `physics:curriculum-text/elective-physics-2-candidate-006` 了解可再生能源和不可再生能源的开发与利用,了解新能源 开发的必要性。 ｜ 判定依据：新能源开发未设节点
- `physics:curriculum-text/elective-physics-2-candidate-007` 了解太阳能利用的常见方式及应用前景。 ｜ 判定依据：太阳能利用未设节点
- `physics:curriculum-text/elective-physics-2-candidate-009` 了解地热能、潮汐能及风能等的利用。 ｜ 判定依据：地热/潮汐/风能未设节点
- `physics:curriculum-text/elective-physics-2-candidate-010` 了解物理学在新材料开发中的作用。 ｜ 判定依据：新材料开发未设节点
- `physics:curriculum-text/elective-physics-2-candidate-011` 了解纳米材料的特性及其在生活中的应用。 ｜ 判定依据：纳米材料未设节点
- `physics:curriculum-text/elective-physics-2-candidate-012` 了解超导体的一些特点,了解超导技术的应用。 ｜ 判定依据：超导体未设节点
- `physics:curriculum-text/elective-physics-2-candidate-013` 了解一些功能材料的特性及应用。 ｜ 判定依据：功能材料未设节点
- `physics:curriculum-text/elective-physics-2-candidate-014` 了解物理学对信息技术发展的贡献。 ｜ 判定依据：信息技术贡献未设节点
- `physics:curriculum-text/elective-physics-2-candidate-015` 了解智能手机的一些功能及其对生活的影响。 ｜ 判定依据：智能手机功能未设节点
- `physics:curriculum-text/elective-physics-2-candidate-016` 了解计算机的主要部件及其作用。 ｜ 判定依据：计算机部件未设节点
- `physics:curriculum-text/elective-physics-2-candidate-017` 了解卫星定位系统的发展历程及其在生活中的应用。 ｜ 判定依据：卫星定位系统未设节点
- `physics:curriculum-text/elective-physics-2-candidate-018` 了解物理学对人工智能发展的推动作用。 ｜ 判定依据：人工智能未设节点

**选修3：近代物理学初步（12 条）**

- `physics:curriculum-text/elective-physics-3-candidate-001` 比较对宏观物体与微观粒子运动特点描述的差异。 ｜ 判定依据：宏观微观描述差异未设节点
- `physics:curriculum-text/elective-physics-3-candidate-003` 初步了解海森伯不确定性原理。 ｜ 判定依据：不确定性原理未设节点
- `physics:curriculum-text/elective-physics-3-candidate-006` 知道相对性原理和光速不变原理。 ｜ 判定依据：相对性原理/光速不变未设节点
- `physics:curriculum-text/elective-physics-3-candidate-007` 能利用公式,解释长度收缩效应和时间延缓效应。 ｜ 判定依据：长度收缩/时间延缓未设节点
- `physics:curriculum-text/elective-physics-3-candidate-009` 了解广义相对论的主要思想、结论和观测证据。 ｜ 判定依据：广义相对论未设节点
- `physics:curriculum-text/elective-physics-3-candidate-010` 了解人类探索高速世界的大致历程,知道这种探索将不断 深入。 ｜ 判定依据：高速世界探索史未设节点
- `physics:curriculum-text/elective-physics-3-candidate-011` 初步了解大爆炸宇宙论的理论模型及其观测证据。 ｜ 判定依据：大爆炸宇宙论未设节点
- `physics:curriculum-text/elective-physics-3-candidate-012` 初步了解黑洞及对黑洞的观测方法。 ｜ 判定依据：黑洞未设节点
- `physics:curriculum-text/elective-physics-3-candidate-013` 初步了解暗物质和暗能量。 ｜ 判定依据：暗物质/暗能量未设节点
- `physics:curriculum-text/elective-physics-3-candidate-014` 初步了解物质与反物质。 ｜ 判定依据：反物质未设节点
- `physics:curriculum-text/elective-physics-3-candidate-015` 初步了解宇宙论的一些进展。 ｜ 判定依据：宇宙论进展未设节点
- `physics:curriculum-text/elective-physics-3-candidate-017` 初步了解追求相互作用统一的思想和探索在物理学发展中的 作用。 ｜ 判定依据：相互作用统一思想未设节点

### 3.3 CHEMISTRY（化学，7 条）

**选修：实验化学（1 条）**

- `chemistry:curriculum-text/elective-experimental-chemistry-candidate-004` 综合实验 围绕资源、能源与环境等与可持续发展密切相关的问题开展综合实 验项目。例如,用比色法测定水样中的某项指标,空气中二氧化硫、甲 醛等污染物浓度的检测,电浮选凝聚法等污水处理技术。 围绕材料性质与性能探究,材料开发与生产,以及新型材料设计等 开展综合实验项目。例如,光伏材料的性能研究,液晶显示材料的应用 探究,离子交换膜、反渗透膜等高分子膜的应用探究。… ｜ 判定依据：综合实验项目（水质检测等）未设节点

**选修：化学与社会（3 条）**

- `chemistry:curriculum-text/elective-chemistry-society-candidate-001` 化学与生活 认识到化学与人类的衣、食、住、行密切相关,了解化学在促进人 类健康、提供生活材料和保护环境等方面的重要作用,感受化学对人类 生活的影响,认识化学科学的发展对提高人类生活质量的积极作用,形 成科学的生活态度和生活行为习惯。可从如下方面选取课程内容。 化学与健康。… ｜ 判定依据：化学与生活（STS）未设节点
- `chemistry:curriculum-text/elective-chemistry-society-candidate-002` 化学与技术 了解化学在工农业生产中的具体应用,认识化学工业在国民经济发 展中的地位;认识化学科学发展与技术进步的相互促进作用,强化化学 应用必须考虑化学过程对自然带来的各种影响的意识,提高对“绿色化 学”和“可持续发展”思想的认识。可从如下方面选取课程内容。 化学与资源开发利用。… ｜ 判定依据：化学与技术（STS）未设节点
- `chemistry:curriculum-text/elective-chemistry-society-candidate-003` 综合实践 通过综合实践活动,关注与化学有关的社会热点问题,认识和赞赏 化学对社会发展的重大贡献,发展综合分析、解决问题的能力。供参考 的综合实践活动如下。 调查。… ｜ 判定依据：综合实践活动未设节点

**选修：发展中的化学科学（3 条）**

- `chemistry:curriculum-text/elective-developing-chemical-science-candidate-001` 化学科学研究进展 围绕合成化学、催化化学、界面化学、理论与计算化学、化学测量 与成像,以及化学动态学等现代化学研究的重要领域,选取化学科学研 究成果的典型案例,引导学生认识化学是一门由实验和理论共同支撑的 科学,了解化学理论发展的方向和面临的挑战。供参考的选题如下。… ｜ 判定依据：现代化学研究前沿未设节点
- `chemistry:curriculum-text/elective-developing-chemical-science-candidate-002` 作为交叉学科的化学 围绕材料化学、环境化学、化学生物学、能源化学、大分子及超分 子与纳米化学等具有学科交叉特点的现代化学研究的重要领域,选取反 映学科发展前沿、激发学生学习兴趣的研究案例,引导学生体会这些研 究案例的研究思路与方法,了解化学与材料、资源、能源、生态、环 境、生命、医药、军事国防及信息技术等领域的交叉融合,认识化学作 为一门中心学科在促进科技发展中所发挥的作用。供参考的选题如下。… ｜ 判定依据：交叉学科化学未设节点
- `chemistry:curriculum-text/elective-developing-chemical-science-candidate-003` 化学工程研究进展 现代化学工程是将化学研究转化为生产力的重要途径之一。化学工 程的研究在不断探索如何揭示物质转化过程中传递、分离、过程强化和 反应之间的关系及其对产品组成、结构和性质的影响;实现化工过程的 原子经济性,发展安全高效和节能环保的物质转化工艺和系统。围绕相 关典型研究成果,引导学生了解化学工程前沿研究的问题与思路。供参 考的选题如下。… ｜ 判定依据：化学工程进展未设节点

### 3.4 BIOLOGY（生物学，15 条）

**选修·现实生活应用：健康生活（1 条）**

- `biology:curriculum-text/elective-life-healthy-living-candidate-001` 健康是人类全面发展的基础,关系千家万户的幸福。每个人都有维 护自身和他人健康的责任,健康的生活方式能够维护和促进自身健康。 健康生活是指有益于健康的习惯化的行为方式,具体表现为生活有规 律,没有不良嗜好,讲究个人、环境和饮食卫生,讲科学,不迷信,平 时注意保健,具备基本的医学常识,生病及时就医,积极参加有益于健 康的文体活动和社会活动等。… ｜ 判定依据：健康生活模块,非知识点对象

**选修·现实生活应用：急救措施（1 条）**

- `biology:curriculum-text/elective-life-first-aid-candidate-001` 随着经济的发展,社会的进步,人们的健康意识明显提高。各种急 危重症、意外伤害时有发生,威胁着人们的健康和生命,意外伤害已成 为危害人类健康的全球性公共卫生问题。急救知识的普及,可以使公众 掌握正确的急救知识,一旦发生意外情况能积极有效地开展现场自救互 救,从而为患者赢得宝贵的抢救时机,提高抢救成功率。… ｜ 判定依据：急救措施模块,非知识点对象

**选修·现实生活应用：社会热点中的生物学问题（1 条）**

- `biology:curriculum-text/elective-life-social-issues-candidate-001` 20 世纪下半叶以来,生物学科进入飞速发展的时代,取得无数新 成果,其中有些成了社会大众关注的热点问题。与此同时,人类社会还 面临一些传染病的严峻考验。 本模块选取转基因植物、试管婴儿、克隆哺乳动物、艾滋病、禽流 感、SARS 以及埃博拉疫情等主题加以论述,旨在向广大学生传递正确 的科学知识,有助于他们理性、客观地面对相关的社会性科学议题,为 今后继续深造和走上社会奠定必要的基础。… ｜ 判定依据：社会热点模块,非知识点对象

**选修·现实生活应用：动物福利（1 条）**

- `biology:curriculum-text/elective-life-animal-welfare-candidate-001` 浩瀚宇宙之中,动物和人类共同分享着一个地球,文明、人道地对 待动物本质上也与人类自身的利益息息相关。动物福利指动物处于基本 需求得到满足,痛苦被降至最低的状态。随着社会上一些虐待和残害动 物的事件被陆续曝光,动物福利也成为一个备受公众关注的话题。在生 物学课程体系中进行动物福利教育,对于普及动物福利理念,培养学生 尊重生命、关爱动物的价值观有重要意义,对于发展学生的生物学学科 核心素养,落实立德树人目标有着积极的意义。… ｜ 判定依据：动物福利模块,非知识点对象

**选修·现实生活应用：地方特色动植物研究（1 条）**

- `biology:curriculum-text/elective-life-local-species-candidate-001` 纵观人类历史发展可知,动植物资源是人类社会发展的重要物质资 源,人类的物质生活和精神生活都离不开动植物资源。各地具有地方特 色的野生动植物不仅具有重要的经济、科学、生态、文化及美学等方面 的价值,也大大丰富了人类的文化生活。因此,开展对地方特色动植物 的研究不仅是为了合理配置和利用野生动植物资源,更重要的是通过了 解当地野生动植物资源的现状,培养人与自然和谐共生的意识,充分认 识环境保护与可持续发展、改善人类的物质生活和文化生活的意义。… ｜ 判定依据：地方特色动植物模块,非知识点对象

**选修·职业规划前瞻：生物制药（1 条）**

- `biology:curriculum-text/elective-career-biopharma-candidate-001` 生物制药是指运用微生物学、基因工程、发酵工程、细胞工程和 化学等学科知识和技术,通过生物活体来生产药物的学科。药物学是 指研究药物的来源、炮制、性状和作用,分析、鉴定、调配、生产和 研发新药等的学科。 生物制药的内容非常广泛,本模块侧重于常见药物制备、药物分析 和药物作用三个部分。 本模块应首先介绍药物对机体的作用和药物制备的技术,然后再介 绍各类药物及其临床应用,并介绍药物发现历史及当前最活跃的药物研 发概况。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：海洋生物学（1 条）**

- `biology:curriculum-text/elective-career-marine-biology-candidate-001` 海洋占地球总面积的 70.8%,其中生活着丰富多彩的生物,是生物 多样性的宝库,吸引着我们去探索和认识。海洋生物学就是研究生活在 海洋中的生命有机体的科学。通过对海洋生物形态结构、重要类群、生 理、生化、生态、系统发育和地理分布等知识的学习,可以较为系统全 面地了解海洋生物,为保护以及合理开发利用海洋生物资源奠定基础。 本模块包括海洋生物的重要类群及其生命特征、海洋生态系统的结 构和功能、海洋生物资源的保护和利用 3 个部分。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：食品安全与检疫（1 条）**

- `biology:curriculum-text/elective-career-food-safety-candidate-001` 食品安全指食品符合应有的营养要求,对人及其后代健康不造成任 何急性、亚急性或者慢性危害,在食品加工、存储和销售等过程中确保 食品卫生及食用安全,降低疾病隐患,防范食物中毒。随着人类文明的 进步,人们越来越重视食品的安全性。食品安全关系国民的健康和民族 的永续发展。 本模块包括食品生产安全、食品经营安全和食品安全检疫三个部 分。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：职业病与防控（1 条）**

- `biology:curriculum-text/elective-career-occupational-health-candidate-001` 职业病是指企事业等单位的劳动者在职业活动中,因接触粉尘、放 射性物质或其他有毒、有害因素而出现的疾病。最主要的职业病有肺尘 埃沉着病、职业中毒、职业性皮肤病等。职业病不仅影响劳动者的身体 健康,也影响家庭幸福。我国对职业病的防控非常重视。 本模块包括职业病的形成和防控两个部分。课程内容应以职业病形 成的病因为基础,进而讲述职业病防控的知识和国家有关的法律法规。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：园艺与景观生态学（1 条）**

- `biology:curriculum-text/elective-career-horticulture-candidate-001` 园艺学是研究园艺作物的种质资源、生长发育规律、繁殖、栽培、 育种、储藏、加工、病虫害以及造园等的科学。造园学又叫园林规划设 计学,是研究园林绿地的设计、规划、施工和养护管理的科学。园艺作 物一般指以较小规模集约栽培的具有较高经济价值的作物,主要分为果 树、蔬菜和观赏植物三大类。现代园艺已成为综合应用各种科学技术成 果以促进生产的重要领域,对丰富人类营养和美化、改造人类生存环境 具有重要意义。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：环境友好与经济作物（1 条）**

- `biology:curriculum-text/elective-career-environmental-crops-candidate-001` 经济作物与人类生存和生活密切相关,如棉花、油料作物、果蔬、 茶叶等。人类为了获取高额经济回报往往肆意割除原有植被,不仅大规 模集中种植某种(些)作物,而且在耕作过程中大量使用化肥和农药, 在获得较高产量的同时也对生态环境造成日益严重的影响。 1992 年,在里约热内卢召开的联合国环境与发展大会通过的《 21 世纪议程》中提出了“环境友好”的理念。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·职业规划前瞻：生物资源开发与利用（1 条）**

- `biology:curriculum-text/elective-career-bioresources-candidate-001` 生物资源是自然资源的有机组成部分,是生物圈中一切动物、植物 和微生物组成的生物群落的总和,是对人类具有实际或潜在应用价值的 可再生资源,是人类生活和生产的物质基础。生物资源包括基因、物种 以及生态系统三个层次。 本模块涉及的生物资源可包括野生生物资源、农作物资源(如粮食 作物、油料作物、水果蔬菜等) 、畜牧水产资源(如畜类、禽类、水产 品等) 、微生物资源(如食用菌、酵母菌等) 。… ｜ 判定依据：职业规划前瞻模块,非知识点对象

**选修·学业发展基础：生物信息学与人类基因组（1 条）**

- `biology:curriculum-text/elective-academic-bioinformatics-candidate-001` 生命科学的发展带来了各种生物数据的爆炸性增长,未来还将以 越来越快的速度增长。然而,数据并不等于知识,其增长所带来的挑 战包括海量数据的存储、管理、共享,特别是如何将这些数据变为生 物学知识。 生物信息学是以计算机为工具,用数理及信息科学的理论和方法 研究生命现象,对生物信息进行存储、检索、分析和整合的一门学科。 基因组信息学是生物信息学的重要内容,包括基因组(如人类基因组) 信息的获取、处理、存储、分配和解读。… ｜ 判定依据：生物信息学未设节点

**选修·学业发展基础：干细胞与应用（1 条）**

- `biology:curriculum-text/elective-academic-stem-cells-candidate-001` 干细胞是一类具有自我复制能力并能分化成其他类型功能细胞的多 潜能细胞,因而具有巨大的基础研究价值和临床应用前景。干细胞研究 也是目前全球范围内生物医学领域的热点和焦点。学习和了解干细胞相 关知识是培养新世纪生物学科人才所必需的。 本模块的内容包括干细胞的定义、基本特征、分类、生长和分化机 制及应用。干细胞在特定的条件下,能分化成多种类型的细胞。… ｜ 判定依据：干细胞未设节点

**选修·学业发展基础：校园动植物分类（1 条）**

- `biology:curriculum-text/elective-academic-campus-taxonomy-candidate-001` 当前,校园生态规划建设已经成为人们讨论的热点话题。让生态理 念进入校园,不仅有利于全民生态意识的提高,也丰富了学生的学习实 践内容,有利于学生的全面发展。校园动植物环境是校园生态最基本的 环境元素,是一个半人工生态系统(即由自然生态系统和人造生态系 统共同组成) 。让学生积极参与到校园生态建设和生态保护行动中去, 对于培养学生观察、调查和分类的兴趣,热爱自然环境等都具有十分 重要的意义。… ｜ 判定依据：校园动植物分类模块,非知识点对象

（选修逐条合计：**189 条**）

## 4. 主线 NOT_COVERED 逐条（必修 + 选择性必修，9 条）

### MATH

**必修：函数（2 条）**

- `candidate:moe:2025:math:curriculum-text/required-functions-candidate-007` .函数的形成与发展 收集、阅读函数的形成与发展的历史资料 ,撰写小论文,论述函数 发展的过程、重要结果、主要人物、关键事件及其对人类文明的贡献 。 ｜ 判定依据：函数的形成与发展 收集、阅读函数的形成与发展的历史资料,撰写小论文
- `candidate:moe:2025:math:curriculum-text/required-functions-candidate-015` .收集、阅读对数概念的形成与发展的历史资料 ,撰写小论文 , 论述对数发明的过程以及对数对简化运算的作用 。 ｜ 判定依据：收集、阅读对数概念的形成与发展的历史资料,撰写小论文

**必修：几何与代数（1 条）**

- `candidate:moe:2025:math:curriculum-text/required-geometry-algebra-candidate-028` .几何学的发展 收集、阅读几何学发展的历史资料,撰写小论文,论述几何学发展 的过程、重要结果、主要人物、关键事件及其对人类文明的贡献 。 ｜ 判定依据：论文型学术活动，非知识点对象

**选择性必修：函数（1 条）**

- `candidate:moe:2025:math:curriculum-text/selective-functions-candidate-019` .微积分的创立与发展 收集、阅读对微积分的创立和发展起重大作用的有关资料 ,包括一 些重要历史人物 (如牛顿、莱布尼茨 、柯西、魏尔斯特拉斯等 )和事 件,采取独立完成或者小组合作的方式 ,完成一篇有关微积分创立与发 展的研究报告。 ｜ 判定依据：论文型学术活动，非知识点对象

**选择性必修：几何与代数（1 条）**

- `candidate:moe:2025:math:curriculum-text/selective-geometry-algebra-candidate-027` .平面解析几何的形成与发展 收集、阅读平面解析几何的形成与发展的历史资料 ,撰写小论文 , 论述平面解析几何发展的过程 、重要结果、主要人物、关键事件及其对 人类文明的贡献。 ｜ 判定依据：论文型学术活动，非知识点对象

**选择性必修：数学建模活动与数学探究活动（1 条）**

- `candidate:moe:2025:math:curriculum-text/selective-modeling-inquiry-candidate-001` 数学建模活动与数学探究活动以课题研究的形式开展 。在选择性必 修课程中,要求学生完成一个课题研究 ,可以是数学建模的课题研究 , 也可以是数学探究的课题研究 。课题可以是学生在学习必修课程时已完 成课题的延续,或者是新的课题。… ｜ 判定依据：活动性要求（课题研究）非知识点对象

### PHYSICS

**必修3（1 条）**

- `candidate:moe:2025:physics:curriculum-text/required-physics-3-candidate-022` 认识环境污染的危害,了解科学、技术、社会及环境协调发 展的重要性,具有环境保护的意识和行为。 ｜ 判定依据：环境污染/可持续发展未设节点

### CHEMISTRY

**必修：化学与社会发展（1 条）**

- `candidate:moe:2025:chemistry:curriculum-text/required-chemistry-social-development-candidate-005` 化学应用的安全与规则意识 认识经济发展与环境保护等的关系。树立自觉遵守国家关于化学品 应用、化工生产、环境保护、食品与药品安全等方面的法律法规的意 识。了解总体国家安全观中科技安全、生态安全、资源安全等相关内容 与意义,增强自觉维护国家安全的使命感。 ｜ 判定依据：法规/安全意识未设节点

### BIOLOGY

**选择性必修：生物与环境（1 条）**

- `candidate:moe:2025:biology:curriculum-text/selective-biology-environment-candidate-022` 形成“环境保护需要从我做起”的意识 ｜ 判定依据：环保意识类要求,非知识点对象

## 5. 边界与说明

1. **判定为模型单一来源**：台账 reviewer = `Qwen3.8-27B（用户指定语义判定通道，2026-09-16 决定）`（用户指定语义判定通道），本报告仅做机械过滤与计数，未人工逐条复核。
2. **数据文件时间**：JSON 内 `reviewedAt = 2026-10-14`（本报告文件名沿用批次日期 2026-10-13）。
3. **计数复核**：选修 NOT_COVERED = 189（与任务书预期 189 一致）；全部 NOT_COVERED = 198；主线 NOT_COVERED = 9。
4. **口径即 `module` 文本过滤**：选修 module 名（如「选修A：微积分」「选修1：物理学与社会发展」「选修·学业发展基础：…」）与主线（「必修…」「选择性必修…」）由字符串判定，无第三类。
5. 本报告不改任何数据；后续处置（补节点/豁免/登记）以覆盖验收线（F14/F1）与批报 §2 为准。
