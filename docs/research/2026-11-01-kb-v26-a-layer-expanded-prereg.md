# KB v26 A 层扩展定向抽取：预注册（2026-11-01）

> **性质**：出数前冻结。§1–§4 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§4。
> **写作时点**：2026-11-01；HEAD `a0641bb7392f380b5716afe95dd3c371dd0c7670`（`git rev-parse HEAD` 实跑），`origin/main` 同值。
> **上游**：`build/agent-batch31a/baseline/identity.md` §3（MISS 账本 12 条）、§④（仅 `MISCONCEPTION_GUIDE` 节点 51 个 / top10）、§⑤（薄料 top-20）；`build/agent-batch30/a-layer-extract.md`（A 层抽取方法与落料契约）；`docs/research/2026-11-01-kb-v25-a-layer-replay-prereg.md`（v25 非劣/双门/回滚口径）；当前 v25 release（manifest `version=25` / `contentVersion=7e4102b6dcd8de32`）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 staging、未写判定表。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
git rev-parse HEAD                       # a0641bb7392f380b5716afe95dd3c371dd0c7670
git rev-parse origin/main                # a0641bb7392f380b5716afe95dd3c371dd0c7670  → 同值 ✓
git status --porcelain | wc -l           # 736
ls docs/research/2026-11-01-kb-v26-a-layer-expanded-prereg.md
#   No such file or directory  → 本次是新建，只写一次
git status --porcelain -- <§4.1 写面>
#   5 项 M：MATH.csv / PHYSICS.csv / extraction_state.csv / v2-25 / manifest
#   （这是 v25 已写成的当前态，不是待清理脏项；v26 在其上追加，回滚目标是 v25 而非 HEAD v24）
```

- 736 项脏项属他线与 v25 已写面；本预注册不触碰、不裹挟、不回滚。
- **回滚基线是当前工作树 v25**（5 个 `M`），不是 HEAD 的 v24。执行轮若失败，必须恢复 v25 备份，**不得**用 `git checkout` 把 5 个 `M` 打回 v24。

### 0.2 数字口径（本会话实算，v25 release 为当前基线）

| 事实 | 值 | 方式 |
|---|---:|---|
| 节点 | **3,280**（subject+slug） | 遍历主包 `96246c78…` |
| 材料总数 | **65,019**（MATH 14,495 / PHYSICS 18,501 / CHEMISTRY 18,315 / BIOLOGY 13,708） | 遍历 25 sidecar |
| 类型分布 | `CONCEPT_EXPLANATION` / `METHOD_MODEL` / `MISCONCEPTION_GUIDE` 三值 | 遍历 `materials[].type` |
| 恰 1 条材料的节点 | **238**（MATH 74 / PHYSICS 55 / CHEMISTRY 64 / BIOLOGY 45） | 节点绑定计数 |
| 仅 `MISCONCEPTION_GUIDE` 的节点 | **51**，材料合计 **303** | 类型集合恰为 `{MISCONCEPTION_GUIDE}` |
| v25 manifest | `version=25` / `schemaVersion=2` / `contentVersion=7e4102b6dcd8de32` / `packId=moe-2025-four-subjects-v1`；sha `0deea0c7…` | 读 manifest + `sha256sum` |
| v25 主包 | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f`（未变） | `sha256sum` |
| v2-25 | `89693752c96a80773a4e33a6ca66a0328b97fb1b4962a6506f6d944d55f936cc`，**157 条** | `sha256sum` + 读取 |
| dense `.vec` / 旁车 | `70329115…` / `5cbbcb6f…`（v25 未重打） | `sha256sum` |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | `sha256sum` |
| 25-sidecar 聚合 | `c0ab0e94ab1dbca3375d0dc8726a6911a3d3fa6bb5cbcf6f96527d57837e644a` | 按 v2-01…25 顺序连接 basename+sha |

---

## 1. 内容（冻结）

### 1.1 组①：9 条 MISS 节点的期望子面

**口径**：取 v24/v25 融合账本中**有数值 rank** 的 9 条 MISS（`build/agent-batch31a/baseline/identity.md:143-162`；`build/agent-batch30/v24/device-fused-misses.txt` 12 行中 9 条有 rank，3 条 `absent` 属边界单点，不在本组）。每条只针对「题面所问、目标节点现有材料未覆盖」的**一个期望子面**；执行轮在块池中找该子面的实质讲授段，找不到记 `NOT_FOUND`，不把相邻子面硬塞。

| # | 科 | 期望 slug | 现有材料/类型 | MISS 题面（原文） | rank | 冻结期望子面（预注册假设） |
|---:|---|---|---|---|---:|---|
| 1 | BIOLOGY | `性状显隐性的判断` | 38 条；CONCEPT/METHOD/MISCONCEPTION | 两株相对性状的豌豆杂交，只长出4粒种子且全是圆粒，能说圆粒一定是显性吗？ | 7 | 由子代表现型及比例反推显隐性；小样本（4 粒）推断的边界 |
| 2 | CHEMISTRY | `电解原理易错点` | 9 条；CONCEPT/METHOD/MISCONCEPTION | 工业上制金属钠为什么电解熔融氯化钠，而不电解饱和食盐水？ | 13 | 电解熔融 NaCl 与电解饱和食盐水的产物/原理差异 |
| 3 | CHEMISTRY | `同分异构现象和同分异构体` | 31 条；CONCEPT/MISCONCEPTION | 苯的二氯代物有 3 种，那四氯代物有几种？…有没有换元法的巧办法？ | 12 | 二氯代物/四氯代物的换元法计数 |
| 4 | MATH | `利用奇偶性求值-将待求函数值或不等式利用奇偶性转化为已知区间上的函数值求解` | 7 条；METHOD | f(x)是R上的奇函数，当x>0时f(x)=x^2+1，求f(-1)的值。 | 8 | 已知奇偶性与一段解析式，求对称区间的函数值 |
| 5 | MATH | `函数图象的翻折变换` | 15 条；CONCEPT | y=\|f(x)\|的图象是怎么由y=f(x)的图象得到的？那y=f(\|x\|)呢？ | 16 | y=\|f(x)\| 与 y=f(\|x\|) 的翻折变换区别 |
| 6 | MATH | `方程组法-主要解决已知与-的方程-求解析式` | 4 条；METHOD | 已知f(x)+f(-x)=2x+1，怎么求f(x)的解析式？…还差一个方程。 | 11 | 由 f(x)+f(-x)=… 构造方程组消元求解析式 |
| 7 | MATH | `判别式法-主要用于含有二次的分式函数-形如` | 5 条；METHOD | 求f(x)=(x^2+x+1)/(x+1)（x≠-1）的值域，我设了y整理出关于x的一元二次方程，然后呢？ | 8 | 分式函数值域用判别式法的步骤与 Δ≥0 边界 |
| 8 | MATH | `数列的单调性与最值问题` | 51 条；CONCEPT/METHOD/MISCONCEPTION | 已知数列通项a_n=-n²+10n，哪一项最大？ | 8 | 二次型通项的对称轴/整数项最值 |
| 9 | PHYSICS | `共点力的动态平衡` | 117 条；CONCEPT/METHOD | 小球用绳吊着，再用一个水平力慢慢把它推出去，缓慢过程中绳的拉力怎么变？ | 51 | 三力动态平衡的图解法/解析法（缓慢过程绳拉力变化） |

- **3 条 `absent` 不列入本组**：`等和线`、`活结与死结模型`、`力的分解`（边界单点，按 §5 只报不判）。
- 9 条目标节点在当前 v25 包内均存在（本会话逐条核过）；「现有材料/类型」为当前 v25 计数，不是 v24 旧值。
- **冻结期望子面是预注册假设**，由 MISS 题面 + 目标节点名推出；执行轮以块池实际讲授段为准，**不得**为了凑数把题组/解析/课标要求当讲授段。

### 1.2 组②：薄料 top-20 的缺料半边

**口径**：当前 v25 包内**恰 1 条材料**的节点 238 个；因 238 节点并列最少，按「每科最少 5 个、按 slug 排序」取前 20（与 `identity.md:307-322` 同口径）。对每个节点，读其唯一材料，冻结**缺料半边**（该材料未覆盖、但节点名要求的那一面）；执行轮只补这一面，不复制唯一材料。

| # | 科 | 节点 | 现有唯一材料（type / title） | 冻结缺料半边（预注册假设） |
|---:|---|---|---|---|
| 1 | MATH | `三射线定理` | CONCEPT / 三射线定理与射影平方和 | 定理适用条件与射影平方和的计算步骤（方法面） |
| 2 | MATH | `三角换元求最值` | METHOD / 三角函数换元求最值的三步骤 | 换元后变量范围、等号条件与失效边界（概念/边界面） |
| 3 | MATH | `上台阶模型` | METHOD / 上台阶模型 | 递推计数原理与适用边界（概念面） |
| 4 | MATH | `下电梯与公交车模型` | METHOD / 下电梯与公交车模型 | 相对运动/参考系选择的概念解释（概念面） |
| 5 | MATH | `不定方程的解的个数` | METHOD / 不定方程整数解的个数（隔板法） | 隔板法对正整数/非负整数/有界解的适用条件（边界/方法面） |
| 6 | PHYSICS | `a-b两物块叠放在转盘上` | METHOD / A、B两物块叠放在转盘上 | 静摩擦提供向心力的临界条件与方向判定（概念/边界面） |
| 7 | PHYSICS | `一般曲线运动的曲率半径` | CONCEPT / 一般曲线运动的曲率半径 | 曲率半径的计算方法（法向加速度/几何法）（方法面） |
| 8 | PHYSICS | `二力不平衡的合成法` | METHOD / 二力不平衡的合成法 | 合力、分力与加速度方向的概念解释（概念面） |
| 9 | PHYSICS | `光学知识体系` | CONCEPT / 光学知识体系总览 | 折射/全反射/干涉的主干判定方法（方法面） |
| 10 | PHYSICS | `减少量与增量的区别` | CONCEPT / 减少量与增量的表达式与符号 | 在热力学/动量过程中的应用与符号约定（方法面） |
| 11 | CHEMISTRY | `0-1-mol-l-1的-nh4-2so4溶液中各粒子浓度的关系` | METHOD / (NH4)2SO4溶液中粒子浓度的关系与三大守恒 | 电荷/物料/质子守恒的推导与适用条件（概念/方法面） |
| 12 | CHEMISTRY | `4-溴甲基-1-环己烯的一种合成路线如下` | METHOD / 有机合成路线的设计思路 | 官能团转化与反应类型选择（方法面） |
| 13 | CHEMISTRY | `v2o5常用作so2转化为so3的催化剂` | CONCEPT / SO2 与 SO3 的分子结构与杂化（V2O5 催化氧化背景） | 催化氧化反应机理与条件（方法面） |
| 14 | CHEMISTRY | `一种2-甲基色酮内酯-y-可通过下列反应合成` | CONCEPT / 黄鸣龙还原法：羰基还原为亚甲基 | 逆合成分析与官能团保护（方法面） |
| 15 | CHEMISTRY | `一种新型煤气化燃烧集成制氢发生的主要反应如下` | METHOD / 多重平衡体系的分析与计算 | 方程式书写、盖斯定律与平衡移动（概念/方法面） |
| 16 | BIOLOGY | `中心体` | CONCEPT / 中心体的分布、结构与功能 | 中心体与纺锤体形成/细胞分裂的关系（过程面） |
| 17 | BIOLOGY | `中性突变学说与间断平衡学说` | CONCEPT / 中性突变学说与间断平衡学说的核心观点 | 两学说与自然选择/进化证据的比较（辨析面） |
| 18 | BIOLOGY | `人胚胎干细胞研究的伦理规范` | CONCEPT / 人胚胎干细胞研究的培养期限与植入限制 | 伦理审查与法律规范的应用（方法面） |
| 19 | BIOLOGY | `体温调定点与发热` | CONCEPT / 体温调定点的调节原理与发热的成因 | 发热/中暑/退烧药的作用机制分析（方法面） |
| 20 | BIOLOGY | `克隆技术与伦理问题` | CONCEPT / 克隆技术与伦理问题：核移植、克隆动物与伦理争议 | 核移植流程与伦理辨析（方法面） |

- 化学 5 个 slug 是「题干片段被当成节点名」的产物（`identity.md:324` 已登记）；本组仍按节点名补缺料半边，但**不修节点名**（改名属另一预注册）。
- 「现有唯一材料」的 title 逐字取自当前 v25 sidecar（本会话实读）；执行轮若该节点已被其他批次补到 >1 条，按「薄料前提失效」记 `SKIP` 并说明，不硬补。

### 1.3 组③：仅误区材料节点 top-10 的讲解/方法材料

**口径**：节点绑定材料类型集合恰为 `{MISCONCEPTION_GUIDE}` 的 51 个节点；按材料数降序取 top-10（并列按科、slug 排序，与 `identity.md:282-305` 同口径）。这些节点只有「易错/误区」材料，缺的是被误区材料预设的**概念讲解**或**方法步骤**；本组只补 `CONCEPT_EXPLANATION` 或 `METHOD_MODEL`，**不补第三条误区材料**。

| # | 科 | 节点 | 现有误区材料数 | 冻结讲解/方法面（预注册假设） |
|---:|---|---|---:|---|
| 1 | MATH | `忽视-漏-空集致错` | 18 | 空集作为子集、交并运算的完整讨论 |
| 2 | PHYSICS | `摩擦力的常见认识误区` | 15 | 摩擦力方向/大小的判定方法 |
| 3 | CHEMISTRY | `铝及其化合物的易错点` | 14 | 铝及其化合物的两性与转化关系 |
| 4 | PHYSICS | `验证机械能守恒定律的误差分析与注意事项` | 13 | 实验原理与误差来源的系统讲解 |
| 5 | CHEMISTRY | `钠和钠的氧化物易错知识点` | 12 | 钠及氧化物的性质与转化 |
| 6 | CHEMISTRY | `原子结构易混易错知识点` | 11 | 能层/能级/电子排布的概念讲解 |
| 7 | CHEMISTRY | `金属材料和金属的冶炼易错点` | 11 | 金属冶炼方法与材料性质 |
| 8 | MATH | `充分性与必要性位置颠倒理解错误致错` | 11 | 充分/必要条件的定义与判断方法 |
| 9 | MATH | `利用导数研究单调性的易错点` | 11 | 导数与单调性的判定步骤 |
| 10 | MATH | `由求时忽略对-检验致错` | 11 | 求轨迹/参数时检验的完整方法 |

- 本组节点现有材料**全是** `MISCONCEPTION_GUIDE`；补讲解/方法材料后类型集合变为 `{MISCONCEPTION_GUIDE, CONCEPT_EXPLANATION}` 或 `{MISCONCEPTION_GUIDE, METHOD_MODEL}`，不再是「仅误区」节点——这是本组的预期结构变化，不是缺陷。
- 执行轮只补 1 条/节点；若块池找不到该面的实质讲授段，记 `NOT_FOUND`，不把误区材料改写成讲解材料。

### 1.4 合格标准（三条统一，写死）

一条候选块只有同时满足以下四条才可落 `MATERIAL`：

| # | 标准 | 判据 |
|---:|---|---|
| 1 | **实质讲授** | 块内有定义/步骤/方法/结论段，学生问该子面能据此作答；纯题组、纯解析、纯课标要求、纯标题碎片 → 不合格 |
| 2 | **六字段齐** | `node_slug / type / title / summary / applicability / content / boundary` 全非空；`content` 为自包含讲授段（非题干/答案）；`node_slug` 必须是当前 v25 包内既有节点 |
| 3 | **type 三值白名单** | 只取 `CONCEPT_EXPLANATION` / `METHOD_MODEL` / `MISCONCEPTION_GUIDE`；组③只取前两值 |
| 4 | **title ≤24 且无 LaTeX** | `len(title)` 按 Unicode 字符 ≤ **24**；不得含 `$` / `\` / `^` / `_`；`summary/applicability/boundary` 非空 |

附加不重复约束（与 A 层同口径）：同一子面已由更细的专门节点承载时记 `SKIP` 并注明承载节点；每条新材料恰 1 条 `PRIMARY` 绑定；`norm_key`/正文与现有材料不碰撞；不建点、不改别名、不改阈值。

**预期产出（不是承诺）**：三组共 **39 个目标子面**（9 + 20 + 10）；合格 MATERIAL 数由执行轮实测，未合格记 `SKIP`/`NOT_FOUND`。若 39/39 全合格，上限为材料 65,019→**65,058**、v2-25 157→**196**；实际以执行轮为准，**不得**为凑上限放宽合格标准。节点数保持 3,280；主包 `96246c78…`、`.vec` `70329115…`、别名、金标零改。

---

## 2. 判读（非劣，写死）

### 2.1 主判读：v26 A 层扩展 = 非劣（四条）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**（绝对下限）；若同代 v25 设备基线 ≠117/129，则同时要求 **≥ v25 同代基线**；低于任一即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与**同代 v25 设备基线**比较；任一章严格更低即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读** |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v26、B = 同代 v25**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

### 2.2 基线口径（关键，防止拿旧读数冒充）

- **当前 release 是 v25**（manifest `version=25` / `contentVersion=7e4102b6dcd8de32` / pack `96246c78…` / v2-25 157 条 / 材料 65,019）。
- v25 的**设备**融合读数**尚未在本轮 promote 链产生**：promote 链只落了 stage3 离线融合 `117/129`、MRR `0.7202842377260982`（`build/agent-batch31a/v25.log`）。**stage3 不是设备读数**，不得当判据 1/2/4 的 B 臂。
- v24 设备基线 `117/129`、MRR `0.7239018087855298`（`build/agent-batch30/v24/device-metrics.txt`）只作**绝对下限**；它不是 v25 同代基线。
- 执行轮必须先在同一 emulator/判官/金标上取 **v25 同代设备四路账本**，再落 v26 并取 v26 四路；两臂同代（`samegen=MATCH`）才判读。v25 同代基线缺失时，判据 1/2/4 记 `NOT_RUN`，**不得**用 v24 或 stage3 代替。
- 零漂移 `NOT_COMPUTABLE`（账本缺失/镜像红）按“不判读”；显著性产物缺失时判据 4 记 `NOT_RUN`，不得写“无显著差异”。
- JVM 只作零漂移/装置保真，不单独判分；设备读数为 emulator，不是真机。

### 2.3 为什么是“非劣”，不是“严格更高”

本批是**内容面定向补料**（39 个目标子面），不是新检索机制。严格更高会把裁定交给单题翻转，等价于面向结果调参；非劣四条覆盖内容更新真正要防的失败：主集回退、跨章回退、装置漂移、显著更差。**不构成先例**：若后续把本批当机制臂重测，须另立预注册并走“严格更高”三规则。

### 2.4 报告义务

§7 至少报告：v25/v26 两臂设备融合/词面主集与 MRR、24 章逐章 lower/equal/higher、零漂移两路 symdiff、bootstrap delta/CI/p、三组各自 MATERIAL/SKIP/NOT_FOUND 计数、材料/绑定计数与 sha、双门 exit code。任何一项未跑写 `NOT_RUN` / `UNVERIFIED`，不得留空冒充通过。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 与 v24/v25 预注册 §3 同口径；若另跑 `testDebugUnitTest`，结果另记，不替代本门。
- **红则不写 release、不跑设备、不判读**：任一门红时停在 §4.1 的 promote 之前；不得先 promote、不得先跑 dense、不得用旧账本代替本批判读。
- 两门日志落执行轮目录（建议 `build/agent-batch31b/`；若编号不同，以实际目录为准），§7 引用；不得只写“已跑”。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚

### 4.1 触碰清单 + pre-sha（写前实算，当前 v25 态）

**A. 判定源与提取台账（tracked；v25 已写成的 5 个 `M`）**

| # | 路径 | 预期动作 | pre-sha256（当前 v25） |
|---|---|---|---|
| 1 | `tools/kb_coverage/tables/material_judgments/MATH.csv` | 追加组①/②/③ 的 MATH 合格行 | `4e0102a44b0ea14bec791e73fe0b90653dee4311a84acfc7741806da9562ee8e`（44,426 数据行） |
| 2 | `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` | 追加 PHYSICS 合格行 | `41d4ca9cbcb4fcef9bdbed7d787f9dd0ac21637c390bf4427650c5af29f6fada`（38,087 数据行） |
| 3 | `tools/kb_coverage/tables/extraction_state.csv` | materialize 标记 EXTRACTED + output_ref（行数不变） | `db141dd7aece5b0983c1c13bc316724b0b45c1eb05d07c0662c4822b9115392b` |

**B. 包家族（promote 触碰；release）**

| # | 路径 | 预期动作 | pre-sha256（当前 v25） |
|---|---|---|---|
| 4 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | **预期字节不变**（无节点变更） | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 5 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 25→26 + 新 contentVersion | `0deea0c7f650803f7a41520ca81528e2f6a1ea54cab9f7c19e2c5aeea32c8e12` |
| 6 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变，无新卷） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-25.json` | 新材料落 v2-25（157→≤196 条；当前 299,656 B，远低于 2.5M 滚动阈值） | `89693752c96a80773a4e33a6ca66a0328b97fb1b4962a6506f6d944d55f936cc` |
| 8 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-26.json` | **预期不新建**（v2-25 未满）；若 materialize 滚动，则新建并重写 index | **不存在**（新建） |

**C. staging（gitignored；当前 == release v25）**

| # | 路径 | pre-sha256 |
|---|---|---|
| 9 | `build/kb-staging/moe-2025-teaching-support-v2-25.json` | `89693752c96a80773a4e33a6ca66a0328b97fb1b4962a6506f6d944d55f936cc` |
| 10 | `build/kb-staging/moe-2025-update-manifest.json` | `0deea0c7f650803f7a41520ca81528e2f6a1ea54cab9f7c19e2c5aeea32c8e12` |

**D. 只读/零触碰（登记供核验，不得改）**

| # | 路径 | 期望 | pre-sha256 |
|---|---|---|---|
| 11 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 12 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 零改 | `5cbbcb6f1b45e1638a4e6bb7f56f5a86a28d4e5a0a70b5efe83aead8f2d2f089` |
| 13 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

### 4.2 写前备份与回滚 / 采纳程序

**写前备份（执行轮第 1 步之前）**

1. 把 §4.1 A（3 张表）、B（主包/manifest/index/v2-25）、C（staging 2 件）复制到 `build/agent-batch31b/backup-v26/`，落 `sha256sum` 全表。
2. 记录 `git status --porcelain -- <§4.1 A/B 写面>` 的当前 5 个 `M`；这 5 个 `M` 是 v25 已写态，**不是待清理脏项**。
3. 复核 v25 身份：manifest `version=25` / `contentVersion=7e4102b6dcd8de32`；v2-25 `89693752…`/157 条；材料 65,019；主包 `96246c78…`；`.vec` `70329115…`。

**回滚（任一判据失败或双门红）**

1. **不得**对 §4.1 A/B 直接 `git checkout`：HEAD 是 v24，checkout 会把 v25 打成 v24，回滚过头。
2. 用 `backup-v26/` 覆盖 §4.1 A（3 表）、B（主包/manifest/index/v2-25；若新建了 v2-26，删除 v2-26 并恢复 index）、C（staging 2 件）。
3. 核验：manifest `version=25` / `contentVersion=7e4102b6dcd8de32`；v2-25 `89693752…`/157 条；材料 **65,019**（MATH 14,495 / PHYSICS 18,501 / CHEMISTRY 18,315 / BIOLOGY 13,708）；主包 `96246c78…`；`.vec` `70329115…`；金标 `eabef102…`；三组目标行在 MATH/PHYSICS 判定表 **0 命中**（回到 v26 写入前）。
4. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
5. §7 写明回滚原因与逐文件 sha 核验；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 release 包家族、判定表、extraction_state 与 staging 同步结果；version=26、新 contentVersion 按实测登记。
2. §7 登记 stateAfter（manifest version/contentVersion、主包/侧车/索引/表 sha、材料/绑定计数）。
3. 非劣口径不得替代机制臂的严格更高；后续复用须另立预注册。

---

## 5. 边界（定向抽取 ≠ 重开封层）

1. **只做 39 个目标子面**：组① 9 + 组② 20 + 组③ 10。**不重开** 238 个薄料节点的全量、**不重判** 51 个仅误区节点、**不重开** v24/v25 的 22 个 A 层子面或 16 条 KEEP PARTIAL。
2. **不建点、不改别名、不改阈值/参数**：节点数保持 3,280；主包 `96246c78…`；别名 40,841；dense `.vec` 不重打；判官与金标零改。若必须建点/改别名才能落料，按「超出本预注册」停止，另立预注册。
3. **组②的 top-20 不是唯一排名**：238 节点并列恰 1 条材料，本组按「每科 5 个、slug 排序」取前 20；执行轮不得把「未入 top-20 的薄料节点」也算进本批，也不得因某节点被其他批次补料后 >1 条而硬补。
4. **组③只补讲解/方法，不补第三条误区**：补后节点不再「仅误区」，这是预期结构变化；不得把误区材料改写成讲解材料来凑数。
5. **边界单点只报不判**：`等和线`、`活结与死结模型`、`力的分解` 三条 `absent` 不在本批 39 个子面内；不得以单点翻转作为本批收益证据，也不得为单点调参。
6. **不把他线脏项裹挟进 v26**：736 项 porcelain 脏项中的 v25 已写面、渲染 v2、v24 遗留、配额臂残留均不在本批新增写面；执行轮只动 §4.1 列出的文件。

**一句话边界**：本批是「对 39 个已冻结的缺料子面做定向补料并做非劣判读」，不是「重开 KB 判定层」，也不是「用 39 条料去覆盖 238 薄料 / 51 仅误区节点的全部问题」。

---

## 6. 未做 / 未验证（写作时点）

- **未做抽取**：三组 39 个子面的候选检索、逐块读原文、MATERIAL/SKIP/NOT_FOUND 判定均未执行；§1 的「冻结期望子面/缺料半边/讲解方法面」是预注册假设，不是已找到的块。
- **未跑 materialize/promote/双门/设备/JVM/零漂移/bootstrap**：§2/§3 是判据定义，不是通过声明。
- **v25 同代设备基线未产生**：v25 promote 链只落 stage3 117/129；执行轮必须先取 v25 设备四路账本，否则判据 1/2/4 记 `NOT_RUN`。
- **v2-26 是否存在未定**：当前 v2-25 仅 299,656 B，预期不滚动；执行轮以 `materialize` 的 `_pick_sidecar` 实测为准，若新建 v2-26 必须同步登记。
- **组②化学 5 个题干片段节点名未修**：本批只补料，不改名；改名属另一预注册。

---

## 7. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；备份与回滚核验；§4.1 逐文件写后 sha；三组各自 目标数/MATERIAL/SKIP/NOT_FOUND；合格标准逐条实测（实质讲授、六字段空值 0、type 白名单、title ≤24 无 LaTeX）；材料/绑定计数（65,019→实测；v2-25 157→实测；节点 3,280；未绑/悬空 0）；双门 exit code；v25/v26 两臂设备四路读数、24 章 lower/equal/higher、零漂移 symdiff、bootstrap delta/CI/p。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
