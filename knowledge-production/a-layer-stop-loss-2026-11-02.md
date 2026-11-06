# A 层止损登记（2026-11-02）

> 性质：状态登记；不改判定表、不建点、不补源、不重开封层。
> 机器件：`knowledge-production/a-layer-stop-loss-2026-11-02.json`。

## 1. 封层（维持）

| 项 | 值 | 证据 |
|---|---:|---|
| A 层未判块 | **60,512** | `docs/kb-acceptance-2026-10-17.md:452,649` |
| 裁量 | **SEAL**（不重开、不重判、不追加抽样） | 批14 预注册；`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:47,74` |
| point | **1,053.333 < 1,500** | 同上 |
| Wilson lower | **326.901 < 800** | 同上 |
| genuine | **5/5** | 同上 |

## 2. 定向抽取两轮（合计 5 条 MATERIAL → 停）

| 轮 | 批次 | 目标子面 | MATERIAL | SKIP | NOT_FOUND | 证据 |
|---:|---|---:|---:|---:|---:|---|
| 1 | v25 | 22 | 3 | 15 | 4 | `build/agent-batch30/a-layer-extract.md:13-16` |
| 2 | v26 | 39 | 2 | 4 | 33 | `build/agent-batch31a/v26-extract.md:13-15` |

- 合计 **5 条 MATERIAL**（v25 3 + v26 2）；按 C6 止损口径停止，不设第 3 轮。
- 定向抽取是验收后维护性补料，不等于重开 60,512 块封层。

## 3. NOT_FOUND 逐条 reason

口径：**无源** = 池内 0 命中或块池无该子面实质讲授段；**不相关** = 有候选块但逐块读后为题组/解析/课标/填空/异知识点；**重复** = 候选内容已在既有节点落料（本轮重复均落 SKIP，NOT_FOUND 中 0 条）。

计数：**无源 4 / 不相关 33 / 重复 0**，合计 **37**。

| 轮 | id | 科 | 目标 | reason | 依据（原文） | 证据 |
|---:|---|---|---|---|---|---|
| 1 | S09 | MATH | 圆锥曲线的实际背景与现实作用 | 无源 | 全池检索「实际背景」「现实世界」「刻画现实世界」均 0 命中；候选全为定值/最值题组，无一处讲授圆锥曲线的实际背景（与 finalReview 判定一致） | `build/agent-batch30/a-layer-extract.md:222` |
| 1 | S11 | PHYSICS | 电路元器件识别及其作用 | 无源 | 「电路元器件」「常见的电路元件」0 命中；电阻器/电容器/二极管命中块全为电路题或实验题，无一处按符号与作用讲授元器件识别（与 finalReview 0 命中一致）；注记所指 `电路中的基本概念`（3 条）只讲电源/电动势/电阻定律 | `build/agent-batch30/a-layer-extract.md:224` |
| 1 | S12 | PHYSICS | 磁现象生产生活应用/古代成果/现代技术 | 无源 | 「磁记录」0 命中；「司南」「指南针」命中块均为化学史/四大发明题；「磁悬浮」为磁悬浮电梯题；无一处讲授磁现象的应用、古代成果或现代技术（与 finalReview 一致） | `build/agent-batch30/a-layer-extract.md:225` |
| 1 | T02 | PHYSICS | 薄料：麦克斯韦电磁说 | 不相关 | 节点 2 条材料是「红外线与可见光」「电磁波特性」，不讲授麦克斯韦说；池内「麦克斯韦」命中块为麦克斯韦妖/电磁波选择题，`b378391aba-059` 仅题干前提句「变化的磁场产生电场，变化的电场产生磁场」，非讲授段 → 薄料缺口仍在 | `build/agent-batch30/a-layer-extract.md:231` |
| 2 | M01 | BIOLOGY | 性状显隐性的判断 | 不相关 | 前 5 全为题组/解析（`8f6ba48539-067` 为「鉴别基因型」选择题，`439c46561a-065`、`acffd40782-006` 等为真题/变式题）；无「由子代表现型反推显隐性、小样本推断边界」的讲授段。节点已有 38 条材料。 | `build/agent-batch31a/v26-extract.md:31` |
| 2 | M02 | CHEMISTRY | 电解原理易错点 | 不相关 | 前 5 为电解/工艺流程题（`174d23d8bc-027` 铅精矿电解、`ed70f0554f-025` LiOH 电解、`3fa53123a2-027` Mg-CO2 电池）；无「熔融 NaCl vs 饱和食盐水」的产物/原理讲授段。节点已有 9 条。 | `build/agent-batch31a/v26-extract.md:32` |
| 2 | M03 | CHEMISTRY | 同分异构现象和同分异构体 | 不相关 | 前 5 为同分异构判断题（`7cf262af6a-022`、`7cf262af6a-054`、`8179029ae7-068` 等）；无二氯/四氯代物换元法计数的讲授段。节点已有 31 条。 | `build/agent-batch31a/v26-extract.md:33` |
| 2 | M06 | MATH | 方程组法-…求解析式 | 不相关 | 前 5 为题组/无关块（`0911ad2eb1-001` 导数题、`d98256b8b2-001` 三角函数题等）；无「由 f(x)+f(-x)=… 构造方程组消元」的讲授段。节点已有 4 条 METHOD。 | `build/agent-batch31a/v26-extract.md:36` |
| 2 | M07 | MATH | 判别式法-… | 不相关 | 前 5 为二次方程根的分布/圆的判别式题（`fcc8a57b01-004`、`c2c1bcb660-004`、`289deb9ecd-1004` 等）；无「分式函数值域用判别式法、Δ≥0 边界」的讲授段。节点已有 5 条 METHOD。 | `build/agent-batch31a/v26-extract.md:37` |
| 2 | M08 | MATH | 数列的单调性与最值问题 | 不相关 | 前 5 为题组/解析（`4a73f291d8-021` 递推数列选择题、`dc17268a83-003` 仅一句「作差比较法」）；无「二次型通项对称轴/整数项最值」的完整讲授段。节点已有 51 条。 | `build/agent-batch31a/v26-extract.md:38` |
| 2 | T01 | MATH | 三射线定理 | 不相关 | 候选 `851625fa58-004` 讲「三余弦定理 cosθ=cosθ1·cosθ2」，不是三射线定理/射影平方和；其余为立体几何题组。 | `build/agent-batch31a/v26-extract.md:51` |
| 2 | T02 | MATH | 三角换元求最值 | 不相关 | 候选 `T02-1`（`slide e30`）讲多元最值的整体换元/代入消元，不是三角换元；其余为题组。 | `build/agent-batch31a/v26-extract.md:52` |
| 2 | T03 | MATH | 上台阶模型 | 不相关 | 候选 `f80078ac75-011` 讲数列累加/累乘，`46cbfcfd4e-004` 讲计数原理综合，均非上台阶递推；其余为题组。 | `build/agent-batch31a/v26-extract.md:53` |
| 2 | T04 | MATH | 下电梯与公交车模型 | 不相关 | 28 候选，前 5 无相对运动/参考系选择的讲授段；均为运动学题组。 | `build/agent-batch31a/v26-extract.md:54` |
| 2 | T05 | MATH | 不定方程的解的个数 | 不相关 | 前 5 为题组（`096426d70c-067` 等），无隔板法对正整数/非负整数/有界解适用条件的讲授段。 | `build/agent-batch31a/v26-extract.md:55` |
| 2 | T06 | PHYSICS | a-b两物块叠放在转盘上 | 不相关 | 前 5 为圆周运动/向心加速度题（`T06-3`、`T06-5` 等），无静摩擦临界条件与方向判定的讲授段。 | `build/agent-batch31a/v26-extract.md:56` |
| 2 | T07 | PHYSICS | 一般曲线运动的曲率半径 | 不相关 | 候选 `b5ebfaba41-030` 只有一句定义（且 action=SKIP），节点唯一材料已覆盖概念；无法向加速度/几何法计算段。 | `build/agent-batch31a/v26-extract.md:57` |
| 2 | T08 | PHYSICS | 二力不平衡的合成法 | 不相关 | 前 5 为实验题/合力题（`T08-1`、`T08-2` 等），无二力不平衡合成法的概念解释。 | `build/agent-batch31a/v26-extract.md:58` |
| 2 | T09 | PHYSICS | 光学知识体系 | 不相关 | 前 5 为折射/全反射/干涉题（`bbd1812d79-005`、`b378391aba-039` 等），无主干判定方法的讲授段。 | `build/agent-batch31a/v26-extract.md:59` |
| 2 | T10 | PHYSICS | 减少量与增量的区别 | 不相关 | 前 5 为热学/理想气体题（`T10-1` 等），无符号约定与应用方法的讲授段。 | `build/agent-batch31a/v26-extract.md:60` |
| 2 | T11 | CHEMISTRY | 0-1-mol-l-1的-nh4-2so4溶液中各粒子浓度的关系 | 不相关 | 无 q==0 的讲授候选；前 5 为离子浓度/守恒题组。 | `build/agent-batch31a/v26-extract.md:61` |
| 2 | T12 | CHEMISTRY | 4-溴甲基-1-环己烯的一种合成路线如下 | 不相关 | 前 5 为有机合成/官能团判断题（`T12-2` 等），无逆合成/官能团转化选择讲授段。 | `build/agent-batch31a/v26-extract.md:62` |
| 2 | T13 | CHEMISTRY | v2o5常用作so2转化为so3的催化剂 | 不相关 | 前 5 为 SO2/SO3 性质题，无催化氧化机理与条件的讲授段。 | `build/agent-batch31a/v26-extract.md:63` |
| 2 | T14 | CHEMISTRY | 一种2-甲基色酮内酯-y-可通过下列反应合成 | 不相关 | 前 5 为有机合成题，无逆合成分析与官能团保护的讲授段。 | `build/agent-batch31a/v26-extract.md:64` |
| 2 | T15 | CHEMISTRY | 一种新型煤气化燃烧集成制氢发生的主要反应如下 | 不相关 | 前 5 为反应热/平衡题（`T15-3` 等），无方程式书写/盖斯定律/平衡移动的讲授段。 | `build/agent-batch31a/v26-extract.md:65` |
| 2 | T16 | BIOLOGY | 中心体 | 不相关 | 前 5 为中心体/细胞器题，无中心体与纺锤体形成/细胞分裂关系的讲授段。 | `build/agent-batch31a/v26-extract.md:66` |
| 2 | T17 | BIOLOGY | 中性突变学说与间断平衡学说 | 无源 | 知识清单/精讲来源 0 命中；前 5 为进化选择题，无两学说比较的讲授段。 | `build/agent-batch31a/v26-extract.md:67` |
| 2 | T18 | BIOLOGY | 人胚胎干细胞研究的伦理规范 | 不相关 | 前 5 为伦理判断题，无伦理审查/法律规范应用的讲授段。 | `build/agent-batch31a/v26-extract.md:68` |
| 2 | T19 | BIOLOGY | 体温调定点与发热 | 不相关 | 候选 `ec2683c9fd-004` 为实验题/答案，无调定点机制与发热分析的讲授段。 | `build/agent-batch31a/v26-extract.md:69` |
| 2 | T20 | BIOLOGY | 克隆技术与伦理问题 | 不相关 | 前 5 为克隆/伦理题，无核移植流程与伦理辨析的讲授段。 | `build/agent-batch31a/v26-extract.md:70` |
| 2 | R02 | PHYSICS | 摩擦力的常见认识误区 | 不相关 | 前 5 为摩擦力题（`934e377dcb-014` 等），无摩擦力方向/大小判定的讲授段。 | `build/agent-batch31a/v26-extract.md:83` |
| 2 | R03 | CHEMISTRY | 铝及其化合物的易错点 | 不相关 | 前 5 为氢氧化铝/铝题（`947add6b4a-012`、`2a6b2482d9-010` 等），无两性转化的讲授段。 | `build/agent-batch31a/v26-extract.md:84` |
| 2 | R04 | PHYSICS | 验证机械能守恒定律的误差分析与注意事项 | 不相关 | 候选 `R04-1` 为油膜法实验，非机械能守恒；其余为实验题组。 | `build/agent-batch31a/v26-extract.md:85` |
| 2 | R05 | CHEMISTRY | 钠和钠的氧化物易错知识点 | 不相关 | `f6a9a8356d-010` 只有「三大规律」标题后接训练题，无规则正文；`132fa7a532-022` 为误区判断清单，非讲解段。 | `build/agent-batch31a/v26-extract.md:86` |
| 2 | R06 | CHEMISTRY | 原子结构易混易错知识点 | 不相关 | 候选 `516cbdd1d5-005` 为填空知识清单（电子排布式），非自包含讲授段。 | `build/agent-batch31a/v26-extract.md:87` |
| 2 | R07 | CHEMISTRY | 金属材料和金属的冶炼易错点 | 不相关 | 前 5 为冶炼题/工艺流程题，无金属冶炼方法的讲授段。 | `build/agent-batch31a/v26-extract.md:88` |
| 2 | R10 | MATH | 由求时忽略对-检验致错 | 不相关 | 候选 `b118f109d1-003` 讲轨迹方程的直接/定义/相关点法，未讲「检验」；其余为题组。 | `build/agent-batch31a/v26-extract.md:91` |

## 4. 边界与未验证

- 不建点（节点 3,280 不变）、不改别名、不改阈值/参数、不重开 A 层。
- 本登记只汇总既有抽取报告中的 NOT_FOUND 与理由；未重新读块池复核这 37 条。
- `docs/kb-batch31a-2026-11-01.md` 的 v25/v26 材料数与当前工作树（manifest version 31）不同代；本登记只记录当时的 3+2 条 MATERIAL 事实，不外推到当前包。
