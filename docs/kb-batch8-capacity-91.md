# 批次 8 · 91 条容量拦截逐条裁定（落/清/弃）+ 落地与交接

- 日期：2026-10-11 ｜ 输入：`knowledge-production/candidate-merge/capacity_blocked.csv`（91 行）
- 复核刷新（本版）：2026-10-11 12:2x–13:0x 本线第二次实跑——逐条 0/91 复核、34 条落 staging 现状复核、**通道 C 可执行性更正**（原文误判为结构不可用，实为可用、本线未执行）、当前 gate/staging 并发实况。本次脚本与输出在 `build/agent-batch8/capacity91/recheck/`，逐项见 §8。
- 背景：`docs/kb-outstanding-research-2026-10-02.md` §①-5 与 F3（选项②：按粒度清理后落，逐条三选一）
- 口径：本文件里 **落** = 无需任何包内清理动作即可入库；**清** = 需先在包内做一次清理/重定靶/去重动作才能落；**弃** = 不该进包（重复、已被覆盖、靶位不当）
- 证据文件（全部可复跑，只读）放在 `build/agent-batch8/capacity91/`：`capacity91_verdicts.csv`（本表原始数据）、`capacity91_landing_materials.jsonl`（落库载荷）、`probe.json`（逐行断言词回查）、`multimouth.json`（节点多口性/批内重复）、`coverage_probe.json`（逐字长窗口覆盖率）；比对脚本在 `build/agent-batch8/`；**12:4x 复核**在 `build/agent-batch8/capacity91/recheck/`（`_cap91_recheck.py/json/tsv`、`_multimouth_recheck.py/json`、`_cap91_pairs.tsv`）。

## 0. 数字结论

- 裁定分布：**落 34 / 清 14 / 弃 43**（合计 91）
- 判定码：C1 9, C2 2, C3 3, L 34, R1 20, R2 22, R3 1（L=可直接落；C1=节点内跑题/串名需先清；C2=目标节点已不存在需重定靶；C3=包内已有同内容材料但挂在他节点；R1=本批内同题重复；R2=现包已覆盖；R3=靶位不当）
- 已落：**34 条进 `build/kb-staging/`**（本线落盘时点：侧车 v2-19，材料 50,869 → 50,903，cmp- 材料 81 → 115）。**12:4x 复核**：34/34 仍在包（全部在 `moe-2025-teaching-support-v2-19.json`），title/type/summaryMarkdown/contentMarkdown 与绑定节点逐条与载荷相等（`recheck/_cap91_recheck.json`）；`apply_authored_materials … --dry-run` 对当前 staging 复跑 0 error（幂等沿用前轮：added 0 / skipped 34）。前轮落盘后 `PYTHONPATH=tools python tools/kb_build/gate.py` 曾 **23/23 为 0**；**12:4x 复核实况：gate 现 24 项、1 项 FAIL（ghost_aliases=25）——该项出自另一线 11:15–12:33 的在飞工作（其 12:28/12:33 自检同值；幽灵别名＝别名与既有绑定脱节，本线为纯追加式写入，不产生该类缺陷）**
- 未晋升：release 包仍为 version 12 / `627d8d43503e41c1`（mtime 2026-10-10 00:09 未变；19 卷 / 50,869 条 / cmp- 81）。**12:4x 复核：staging 已长到 22 卷 / 59,008 条材料 / cmp- 115**——另一线在持续写入（12:08 resync、12:28 写回 22 卷、12:33 门自检）；`promote.py` 未执行，晋升必须由该 staging 窗口的持有者执行（见 §3 通道 B、§5.1）
- 交接：34 条（落）+ 14 条（清）需要后续接口；43 条弃只需回写账目

## 1. 复核 0/91 在包（逐条比现包）

方法：读 release 包（`core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` + 19 卷侧车，按 `moe-2025-teaching-support-v2-index.json` 清单）建 (科目, 节点 slug) → 材料索引与 slug 索引，再把 `capacity_blocked.csv` 的 91 行逐行对拍。脚本 `build/agent-batch8/analyze91.py`，输出 `build/agent-batch8/capacity91_compare.tsv`。

| 检查项 | 结果 |
|---|---|
| 91 行 slug 出现在现包 | **0 / 91** |
| 目标节点在现包 | 89 / 91 |
| 现包节点材料数 ≥4 | 85 |
| 现包节点材料数 =3 | 4（行 34、35、72、74） |
| 现包节点材料数 =0（节点已不存在） | 2（行 46、48） |
| `existing_materials` 与现包实测相等 | 8 / 91 |

复核实况与 2026-10-02 报告一致（0/91 已入包）；但 `existing_materials`（9-30 生成）与现包实测**只有 8 行相符**——大部分节点在 10 月又长过料，因此本批 evidence 里「节点现有 N 条」的数字与「全量索引 0 命中」的结论**对现包已部分过期**，本裁定一律以现包实测为准（这也是 §2 里 22 行判 R2 的直接原因）。

两行靶节点已不存在，`moe-2025-update-manifest.json` 的 `retired` 有明确记录：

- 行 46 `铁的物理性质`：`MERGE → 铁`，理由「12 条中 11 条为二氧化硫/氯气/钠/氨/醇/氮气/甲烷的物理性质等他物词面误绑，节点无独立内容」（复核 AGREE 2026-10-03）
- 行 48 `铁的氢氧化物的稳定性`：`MERGE → 铁的氢氧化物`，理由「B 边界以两种铁氢氧化物的稳定性与转化为主，与 A 重叠」（复核 AGREE 2026-10-03）

**12:4x 复核（本版刷新；脚本 `build/agent-batch8/capacity91/recheck/_cap91_recheck.py`，输出 `_cap91_recheck.json/tsv`）**：

| 检查项（12:4x 实测） | release（19 卷 / 50,869 条） | staging（22 卷 / 59,008 条） |
|---|---|---|
| 91 slug 出现在包 | **0 / 91** | **34 / 91**（=本线已落清单，逐条字段/绑定相等；其余 57 行 0 命中） |
| 同节点同题（NFKC 标题相等）命中 | 1 行（行 80：包内 `cmp-che-02317` 同名——与弃 R2 裁定一致） | 42 行 = 34 落库自身 + 8 行（2/30/66/78/80/82/83/86，均为 R1 幸存者已落或包内同题，非新增落库） |
| 目标节点在包 | 89 / 91（2 个 retired 见上） | 89 / 91 |
| 表内「现包材料数」列 vs 本版复测 | **89 / 91 逐行相等**（仅行 46/48 节点已删记 0） | 78 行目标节点已增长、9 行持平、2 行减少（行 38/67 的节点 5→4，另一线改绑所致；不影响 release 口径） |

release 自 10-10 00:09 未变，故 §2 表的「现包材料数」列（release 实测）本版仍逐行有效；staging 数字只作落点参考。

## 2. 逐条裁定表（91 行）

「现包材料数」= 本会话对 release 包实测；「裁定/码/证据」见列。

| # | 来源 | 科目 | 目标节点 | 候选 slug | 候选标题 | 现包材料数 | 裁定 | 码 | 证据 |
|---|---|---|---|---|---|---|---|---|---|
| 1 | dir_batch_000.csv#69 | MATH | `求解几何体外接球的半径的思路` | `cmp-mat-00069` | 共斜边的直角三角形模型：斜边中点即外接球球心 | 58 | **落** | L | 现包无「两个直角三角形共斜边→斜边中点即球心」结论：probe 命中项分属 phys-kb-isochronous-circle-model（等时圆）、ext-mat-4b071c22c7-001（三角形的四心），均非本结论；节点 58 条里仅 1 条跑题。 |
| 2 | dir_batch_003.csv#284 | MATH | `求解几何体外接球的半径的思路` | `cmp-mat-00284` | 共斜边的直角三角形模型：斜边中点即外接球球心 | 58 | **弃** | R1 | 与行1 同题同节点（标题逐字相同、正文 Jaccard 0.718），只留首见行。 |
| 3 | dir_batch_003.csv#298 | MATH | `对命题条件探索的三种方法` | `cmp-mat-00298` | 探索性问题的条件探索三条路径与结论探索方法 | 12 | **清** | C1 | 节点 12 条全部是他专题「三种方法」串名（math-restricted-count-three-approaches、math-kb-set-representation-methods、math-hj-s211-zero-count、math-hj-s105-conditional-prob、math-mo-zuotu-sanfa 等），无一涉探索性问题；候选本身同题。 |
| 4 | dir_batch_004.csv#331 | MATH | `直线与平面所成角` | `cmp-mat-00331` | 直线与平面所成角的向量公式与符号处理 | 10 | **弃** | R2 | 同节点已有 math 系材料逐点覆盖：ext-mat-289deb9ecd-015b「直线与平面所成角的向量公式」（sinθ=/cos<u,n>/）、ext-mat-289deb9ecd-904「直线与平面所成角的正弦公式」、ext-mat-19a0d4ddca-005b（向量法 sinθ=/cos⟨a,n⟩/）。 |
| 5 | dir_batch_004.csv#332 | MATH | `二面角` | `cmp-mat-00332` | 二面角的两种向量求法与法向量符号判断 | 21 | **弃** | R2 | 同节点已覆盖：ext-mat-4aeb271a45-003「由法向量方向判断二面角的大小」（进/出半平面定符号）、ext-mat-19a0d4ddca-006「二面角的范围与余弦值符号的判定」。 |
| 6 | dir_batch_004.csv#388 | MATH | `函数的单调区间` | `cmp-mat-00388` | 单调区间的最简原则与端点开闭 | 8 | **弃** | R2 | 四点中端点开闭/书写规范已由同节点 ext-mat-c060a922b5-001b、ext-mat-1b429dbe88-002d 覆盖；「最简原则：应尽可能大」已在 ext-mat-fe6ff7f738-002、ext-mat-5c19ba9896-001（挂节点「函数的单调性」）逐字覆盖。 |
| 7 | dir_batch_005.csv#424 | MATH | `指数函数的常用技巧` | `cmp-mat-00424` | 指数函数的常用技巧：底数分类、图象与底数大小比较、倒数底数图象对称 | 11 | **弃** | R2 | 三点均在同节点有对应：ext-mat-865d6c3591-001c（图象与底数大小比较）、ext-mat-d8ea830d18-006（底数互为倒数/图象越高）、ext-mat-36b898abe1-003（图象关于 y 轴对称与底数增长速度）。 |
| 8 | dir_batch_005.csv#445 | MATH | `对数函数的图象与性质应用` | `cmp-mat-00445` | 对数型函数图象的识别步骤与数形结合应用 | 8 | **清** | C3 | 包内已有同内容材料 ext-mat-9d8541e663-009「对数函数图象的识别与应用方法」（特殊点排除 + 数形结合转化），但挂在节点「函数图象的识别」下；落前需按去重/改绑口径处理。 |
| 9 | dir_batch_007.csv#612 | PHYSICS | `牛顿运动三定律的基本概念和规律` | `cmp-phy-00612` | 牛顿运动三定律的基本内容与相互关系 | 8 | **清** | C1 | 节点 8 条中 7 条跑题：phys-hj2-kaipule-san-da-dinglv（开普勒三定律）、phys-hj3-three-rules-one-law-usage/causality、phys-fx-three-rules-one-law-division（三定则一定律）、phys-mo-l10/l11/l18（牛顿定律综合应用·易混）。 |
| 10 | dir_batch_009.csv#760 | PHYSICS | `动量守恒定律的推导` | `cmp-phy-00760` | 动量守恒定律的推导 | 6 | **落** | L | 全包无「由动量定理＋牛顿第三定律导出 m1v1+m2v2=m1v1′+m2v2′」的推导材料（探针 F1t/推导 类材料 0 条）；节点 6 条只讲特性/条件/实验数据处理。 |
| 11 | dir_batch_010.csv#874 | PHYSICS | `磁场` | `cmp-phy-00874` | 磁性、磁体与磁极的基础概念 | 11 | **清** | C3 | 包内已有同段内容 ext-phy-9d4e47b803-001「磁场的基本概念与性质」（磁性/磁体/磁极成对/不存在磁单极/同名相斥 + 磁场三来源），但挂在节点「磁场的基本概念和性质」下；落前需按去重/改绑口径处理。 |
| 12 | dir_batch_011.csv#936 | PHYSICS | `常见传感器的特点` | `cmp-phy-00936` | 电容式传感器的原理与分析 | 15 | **清** | C3 | 包内已有同内容 ext-phy-e0dafc554c-008「电容式传感器的原理与含传感器电路的分析」（极板正对面积/间距/电介质 → 电容变化）、phys-kb-sensor-conversion-principle（电容式传感器：位移/力/角度→电容），分挂节点「传感器」「工作原理」；落前需处理。 |
| 13 | dir_batch_012.csv#977 | PHYSICS | `单摆的受力特征` | `cmp-phy-00977` | 单摆摆球的两种运动与向心力、回复力的存在条件 | 9 | **落** | L | 节点 9 条同域（回复力/向心力/两特殊位置），无「摆球同时做变速圆周运动与往复运动、两力存在条件」这一表述；探针命中项均在节点内为相关而非同题。 |
| 14 | dir_batch_012.csv#1027 | PHYSICS | `热力学第二定律` | `cmp-phy-01027` | 热力学过程方向性的四类典型实例 | 38 | **弃** | R2 | 同节点已有方向性实例材料：ext-phy-f79c5cbe5c-002「热力学第二定律的实质与方向性表现」（含自发混合）、ext-phy-6f83133765-070「…与方向性实例」。 |
| 15 | dir_batch_012.csv#1029 | PHYSICS | `能量守恒定律` | `cmp-phy-01029` | 能量守恒定律的意义与能量的公共量度作用 | 31 | **弃** | R2 | 「意义」三点已覆盖：ext-phy-e14aad82a5-476「能量守恒定律的内容与理解」③ 意义：找到了各种自然现象的公共量度——能量；第一类永动机由 ext-phy-288e1effad-009b 等覆盖。 |
| 16 | dir_batch_013.csv#1079 | PHYSICS | `近代物理学史-相关粒子的发现和实验` | `cmp-phy-01079` | 伦琴发现X射线 | 7 | **落** | L | 节点内 ext-phy-61eb93e0c1-021 只提「劳厄证实伦琴射线是电磁波」，无 1895 伦琴发现 X 射线（氰亚铂酸钡屏）史实；命题「伦琴」在包内仅此 1 处。 |
| 17 | dir_batch_013.csv#1080 | PHYSICS | `近代物理学史-相关粒子的发现和实验` | `cmp-phy-01080` | 新粒子的发现（正电子、μ子、K介子与π介子、超子、W与Z玻色子） | 7 | **弃** | R2 | 同节点 ext-phy-e14aad82a5-1538「部分新粒子的发现年代」已给正电子/μ子/K介子π介子/W与Z玻色子的发现年代，ext-phy-61eb93e0c1-021 亦给 1932/1937/1947 年代。 |
| 18 | dir_batch_014.csv#1163 | PHYSICS | `电表的改装` | `cmp-phy-01163` | 表头的工作原理、三个参数与满偏关系 | 16 | **弃** | R2 | 同节点已覆盖：ext-phy-e14aad82a5-1340「表头的构造、参数与满偏电流」、ext-phy-e14aad82a5-225 ①「满偏电流 Ig、满偏电压 Ug、表头内阻 Rg，三者满足 Ug=IgRg」。 |
| 19 | dir_batch_015.csv#1236 | PHYSICS | `动量守恒定律的推导` | `cmp-phy-01236` | 由牛顿第二定律与牛顿第三定律推导动量守恒定律 | 6 | **弃** | R1 | 与行10 同题同节点（同一条目「动量守恒定律的推导」，正文 Jaccard 0.422），只留首见行。 |
| 20 | dir_batch_016.csv#1352 | PHYSICS | `带电粒子在交变电场中的运动` | `cmp-phy-01352` | 交变电场中的分段分析与时间极短的匀强电场近似 | 20 | **弃** | R2 | 同节点 ext-phy-41052fd08f-005「带电粒子在交变电场中的偏转」末句已逐字给出「若粒子穿过极板间的时间极短，可以认为粒子穿过时电场不变，按匀强电场处理」。 |
| 21 | dir_batch_018.csv#1444 | PHYSICS | `波动图象的特点` | `cmp-phy-01444` | 波动图象的三个判定要点：波形不变、步调关系与起振方向 | 16 | **弃** | R2 | 同节点 ext-phy-882b26e5cb-003「波的图像的三个特点与振动方向的判定依据」①②③ 与候选三点逐条对应（nT 波形不变/步调相同相反/起振方向与波源相同）。 |
| 22 | dir_batch_018.csv#1453 | PHYSICS | `单摆` | `cmp-phy-01453` | 单摆的双重运动性质与两种受力 | 29 | **弃** | R1 | 与行13 同题（单摆两种运动与两种受力），且节点「单摆」29 条已含 ext-phy-7b30b71329-003、916af4e9e4-009 等受力材料；只留首见行。 |
| 23 | dir_batch_018.csv#1466 | PHYSICS | `机械能守恒定律` | `cmp-phy-01466` | 机械能的概念与三条性质 | 56 | **弃** | R2 | 同节点 ext-phy-e14aad82a5-181b「机械能的定义与机械能守恒的导出」① 已给机械能=动能+重力势能+弹性势能、状态量、相对性、系统性四条。 |
| 24 | dir_batch_018.csv#1472 | PHYSICS | `对机械能守恒条件的理解` | `cmp-phy-01472` | 机械能守恒条件的三点辨误：做功与受力、合力功与合力 | 22 | **弃** | R2 | 同节点 ext-phy-b5ebfaba41-014「对机械能守恒条件的三点辨析」已是同题三点辨析（ext-phy-311ac2aa8a-054 亦含「合力等于零」辨析）。 |
| 25 | dir_batch_018.csv#1476 | PHYSICS | `合外力做功与动能的关系` | `cmp-phy-01476` | 合外力做功与动能变化的正负对应关系 | 8 | **清** | C1 | 节点 8 条中 6 条跑题：phys-only-electric-work-conservation、phys-double-rod-energy-*、phys-max-ke-stopping-voltage、phys-kb-momentum-kinetic-energy-relation、phys-hj2/hj3/fx 动量与动能对比系列。 |
| 26 | dir_batch_018.csv#1485 | PHYSICS | `磁场的基本概念和性质` | `cmp-phy-01485` | 磁性、磁体、磁极与磁场的产生 | 21 | **弃** | R2 | 同节点 ext-phy-9d4e47b803-001「磁场的基本概念与性质」已含候选全部四点（磁性/磁体/磁极成对/不存在磁单极/同名相斥 + 磁场来源）；ext-phy-5592bd185a-027 同。 |
| 27 | dir_batch_018.csv#1519 | PHYSICS | `常见传感器的特点` | `cmp-phy-01519` | 电容式传感器的原理与被测量转换 | 15 | **弃** | R1 | 与行12 同题（电容式传感器原理与转换），只留首见行（行12 判清）。 |
| 28 | dir_batch_019.csv#1530 | PHYSICS | `振荡电路过程中各物理量的变化` | `cmp-phy-01530` | LC振荡充放电过程中各物理量的变化与变化周期 | 12 | **弃** | R2 | 同节点 ext-phy-e14aad82a5-424「LC回路的两组物理量与能量转化周期」③ 已给「电场能、磁场能的变化周期是电磁振荡周期的一半」及各量同步关系。 |
| 29 | dir_batch_019.csv#1586 | PHYSICS | `能量守恒定律` | `cmp-phy-01586` | 能量守恒定律建立的意义 | 31 | **弃** | R1 | 与行15 同题（能量守恒定律的意义），只留首见行（行15 判弃）。 |
| 30 | dir_batch_020.csv#1636 | PHYSICS | `近代物理学史-相关粒子的发现和实验` | `cmp-phy-01636` | 伦琴发现X射线 | 7 | **弃** | R1 | 与行16 同题（伦琴发现X射线），只留首见行。 |
| 31 | dir_batch_020.csv#1637 | PHYSICS | `近代物理学史-相关粒子的发现和实验` | `cmp-phy-01637` | 新粒子的发现历程（正电子、μ子、介子与W、Z玻色子） | 7 | **弃** | R1 | 与行17 同题（新粒子发现史），只留首见行。 |
| 32 | dir_batch_021.csv#1714 | CHEMISTRY | `吸热反应和放热反应` | `cmp-che-01714` | 反应体系的内能及其影响因素 | 25 | **落** | L | 节点 25 条中 0 条跑题，但无「内能的定义 + 由种类/数量/聚集状态/温度/压强共同决定」；命中项 chem-kb-exo-endo-essence 只讲反应内能高低的对比。 |
| 33 | dir_batch_022.csv#1796 | CHEMISTRY | `制取cl2装置的创新` | `cmp-che-01796` | 点滴板微型实验的优点与氯气性质检验的现象判断 | 6 | **落** | L | 「点滴板」全包仅 1 处（ext-che-d28ff8e706-004 顺带提及），节点 6 条讲分离/集气/蒸发/过滤装置创新，无点滴板微型实验；候选同题（装置创新）。 |
| 34 | dir_batch_022.csv#1807 | CHEMISTRY | `fecl3溶液-fe-oh-3胶体-fe-oh-3浊液是三种重要的分散系` | `cmp-che-01807` | 溶液、胶体、浊液三种分散系的比较与鉴别 | 3 | **落** | L | 节点 3 条只覆盖分类依据与本质特征/透过性，未覆盖丁达尔鉴别、吸附性与「三者都是混合物」；节点名「三种分散系的比较」本身规范。 |
| 35 | dir_batch_022.csv#1833 | CHEMISTRY | `n-的关系应用` | `cmp-che-01833` | n 与 N、m、V、c 关系式的应用与标准状况判断 | 3 | **落** | L | 节点 3 条（键长键能/K 与温度/方程式计算流程/关系式法/空气木炭水蒸气制气）全跑题，但候选同题；probe 显示 n＝m/M 类关系式在包内 7 处、本节点 0 处。 |
| 36 | dir_batch_023.csv#1888 | CHEMISTRY | `判断弱酸的三种方法` | `cmp-che-01888` | 导电性对比实验判断弱电解质 | 13 | **落** | L | 节点 13 条同域，无「同浓度同温度导电性对比」的判定表述（probe 导电性对比 全包 2 处、非本节点）。 |
| 37 | dir_batch_023.csv#1905 | CHEMISTRY | `碱金属元素单质及其化合物有广泛用途` | `cmp-che-01905` | 碱金属用途背后的原理：锂电池电解液、氢化钾供氢剂与钠制钾 | 7 | **落** | L | 节点 7 条无「锂电池电解液/氢化钾供氢剂/钠置换熔融氯化钾制钾」；probe 置换熔融 全包 1 处、氢化钾 0 处。 |
| 38 | dir_batch_024.csv#1921 | CHEMISTRY | `金属钠与二氧化碳反应的实验步骤及现象如下表` | `cmp-che-01921` | 钠与二氧化碳反应的产物、检验与化学方程式 | 5 | **清** | C1 | 节点 5 条全部跑题（钠与熔融盐、CO2 的氧化性、钠与水、氯气与金属、氨气与二氧化氮），无钠与 CO₂ 反应本身；节点名为表格残片「…现象如下表」。 |
| 39 | dir_batch_024.csv#1930 | CHEMISTRY | `外界条件对化学平衡的影响` | `cmp-che-01930` | 浓度与压强影响平衡的三个变色探究实验 | 26 | **弃** | R2 | 同节点 ext-che-77309177ec-008「外界条件对化学平衡的影响（实验探究）」已含三组变色体系（Fe³⁺/SCN⁻、Cr₂O₇²⁻/CrO₄²⁻、NO₂/N₂O₄ 压缩）。 |
| 40 | dir_batch_024.csv#1932 | CHEMISTRY | `可逆反应的特点及应用` | `cmp-che-01932` | 可逆反应与可逆过程的区别及平衡浓度范围的极值确定法 | 15 | **落** | L | 节点 15 条无「可逆反应 vs 可逆过程的区别」与「双向极值假设夹出平衡浓度区间」（probe 可逆过程/极值 均非本节点）。 |
| 41 | dir_batch_024.csv#1940 | CHEMISTRY | `化学平衡移动方向和转化率的判断` | `cmp-che-01940` | 恒温恒容再充入气体后转化率与百分含量的变化 | 29 | **弃** | R2 | 同节点 ext-che-1880b6dbb6-042「恒温恒容下再充入单一气体对平衡与转化率的影响」已按 Δn>0/=0/<0 给 PCl5/HI/NO2 三例的 α、φ 变化。 |
| 42 | dir_batch_024.csv#1942 | CHEMISTRY | `化学平衡移动的分析方法` | `cmp-che-01942` | 恒温恒容与恒温恒压的等效思维模式 | 20 | **弃** | R2 | 同节点已覆盖等效分析：ext-che-1880b6dbb6-042（「可从压强增大的角度分析」）、ext-che-77309177ec-023（「等效于压缩容器体积」）、ext-che-5c62da9e03-531（恒温恒容/恒压充惰性气体）。 |
| 43 | dir_batch_024.csv#1953 | CHEMISTRY | `原子结构中微粒的关系及计算` | `cmp-che-01953` | 同位素、同素异形体与同分异构体的辨析及同位素单质的归属 | 23 | **落** | L | 节点 23 条中命中项均为「同位素/核素」相关内容，无三组概念（同位素/同素异形体/同分异构体）按研究对象对照的辨析，也无 HD、HT、DT 归属。 |
| 44 | dir_batch_024.csv#1977 | CHEMISTRY | `可充电电池常考知识-10-点和易错知识-3-点` | `cmp-che-01977` | 充放电时电解质溶液离子浓度与酸碱性的变化 | 6 | **落** | L | 节点 6 条无「充放电时电解质溶液离子浓度与酸碱性变化」的系统表述（probe 离子浓度/酸碱性 均非本节点；铅蓄电池仅 1 处提及）。 |
| 45 | dir_batch_024.csv#1984 | CHEMISTRY | `水的电离平衡移动的影响因素` | `cmp-che-01984` | 弱酸酸式盐对水电离的影响判断 | 23 | **落** | L | 节点 23 条含水的电离影响因素材料，但无「酸式盐按电离/水解程度比较判断促进或抑制」这一判定（候选同题）。 |
| 46 | dir_batch_026.csv#2092 | CHEMISTRY | `铁的物理性质` | `cmp-che-02092` | 铁的颜色、状态与物理性质（含磁性） | 0 | **清** | C2 | 目标节点「铁的物理性质」已不存在：moe-2025-update-manifest.json `retired` 记 MERGE → `铁`（理由：12 条中 11 条为他物词面误绑，节点无独立内容）；落前须重定靶到「铁」。 |
| 47 | dir_batch_026.csv#2105 | CHEMISTRY | `铁与水蒸气反应的注意事项` | `cmp-che-02105` | 铁与水蒸气反应实验的高温条件与氢气验纯 | 16 | **落** | L | 节点 16 条无「需高温故用水蒸气发生装置 + 点燃前验纯」两条注意事项的组合表述。 |
| 48 | dir_batch_026.csv#2109 | CHEMISTRY | `铁的氢氧化物的稳定性` | `cmp-che-02109` | 氢氧化铁受热分解与铁的氢氧化物稳定性比较 | 0 | **清** | C2 | 目标节点「铁的氢氧化物的稳定性」已不存在：manifest `retired` 记 MERGE → `铁的氢氧化物`；落前须重定靶。 |
| 49 | dir_batch_027.csv#2160 | CHEMISTRY | `分子间作用力` | `cmp-che-02160` | 氢键的形成条件、表示方法与两类氢键 | 10 | **落** | L | 节点 10 条含氢键材料（chem-hydrogen-bond-boiling-anomaly 等），但无「X—H…Y 表示法 + 分子间/分子内两类氢键」的成键条件表述；落前建议核对是否与 chem-kb-van-der-waals-hydrogen-bond 重复（复核提示）。 |
| 50 | dir_batch_027.csv#2161 | CHEMISTRY | `键的极性和分子的极性` | `cmp-che-02161` | 键的极性与羧酸酸性的关系 | 18 | **落** | L | 节点 18 条无「键的极性 → 羧基羟基极性 → 酸性强弱」的因果链（probe 羟基的极性 全包 18 处、本节点仅 1 条 ext-che-745c00885b-011）。 |
| 51 | dir_batch_027.csv#2177 | CHEMISTRY | `键参数及其应用` | `cmp-che-02177` | 共价键的形成条件与σ键、π键的判断方法 | 10 | **落** | L | 节点 10 条无「电负性差 >1.7 成离子键 + σ/π 键三种判断方法」的组合表述。 |
| 52 | dir_batch_027.csv#2182 | CHEMISTRY | `v2o5常用作so2转化为so3的催化剂` | `cmp-che-02182` | SO2与SO3的价层电子对数、空间构型与杂化类型 | 5 | **清** | C1 | 节点 5 条全为工艺流程题残片（酸浸/转化/催化剂/净水剂），与节点 boundary（SO2/SO3 价层电子对数、空间构型与杂化）不符；节点名「V2O5常用作SO2转化为SO3的催化剂」为句子残片。 |
| 53 | dir_batch_027.csv#2185 | CHEMISTRY | `配位键和配合物` | `cmp-che-02185` | 配位键稳定性的比较 | 20 | **落** | L | 节点 20 条含配位键/配合物材料，但无「配位原子电负性越小配位键越稳定（[Cu(NH3)4]2+ > [Cu(H2O)4]2+）」这一比较结论。 |
| 54 | dir_batch_028.csv#2257 | CHEMISTRY | `n2o-no和no2等氮氧化物是空气污染物-含有氮氧化物的尾气需处理后才能排放` | `cmp-che-02257` | 氮氧化物尾气处理中的N2O分解与氧化吸收 | 24 | **落** | L | 节点 24 条无「N2O 特种催化剂分解 + NaClO 氧化 NO 提高脱硝率」（probe N2O 83 处、NaClO 172 处、均非本节点同题）。 |
| 55 | dir_batch_030.csv#2401 | CHEMISTRY | `熟记硅及其化合物的特殊性质` | `cmp-che-02401` | 硅及其化合物特殊性质的归纳 | 13 | **落** | L | 节点 13 条无「Si 的还原性与半导体性、SiO2 是酸酐却不溶于水且能与 HF 反应、H2SiO3 难溶、Na2SiO3 通 CO2 析出硅酸」四条的归纳（可复核 ext-che-8cf031c9fe-007 等是否有同题表述）。 |
| 56 | dir_batch_030.csv#2425 | CHEMISTRY | `铜及其化合物的性质` | `cmp-che-02425` | 铜及其化合物的易错性质与Cu2O鉴别 | 29 | **清** | C1 | 节点 29 条中 14 条与「铜及其化合物的性质」不符（probe 显示 Cu2O/歧化/无水 CuSO4 在包内多挂他节点）；候选本身同题。 |
| 57 | dir_batch_030.csv#2430 | CHEMISTRY | `铜及其化合物的易错点` | `cmp-che-02430` | 铜及其化合物易错判断的完整清单 | 13 | **落** | L | 节点 13 条无「铜绿生成条件/Cu(OH)2 溶解范围/CuSO4 只用于检验水/碱式碳酸铜组成/铜盐使蛋白质变性」的完整清单式归纳。 |
| 58 | dir_batch_030.csv#2474 | CHEMISTRY | `胶体的性质及应用` | `cmp-che-02474` | 胶体聚沉与蛋白质变性的变化类型辨析 | 31 | **落** | L | 节点 31 条无「胶体聚沉是物理变化 vs 蛋白质盐析属聚沉、变性属化学变化；只有带电胶粒才电泳」的辨析。 |
| 59 | dir_batch_030.csv#2475 | CHEMISTRY | `fe-oh-3胶体的制备` | `cmp-che-02475` | 书写Fe(OH)3胶体制备方程式的易错点与胶体遇电解质现象 | 28 | **落** | L | 节点 28 条无「制胶体方程式用等号+加热符号、不标↓↑；胶体遇稀硫酸先聚沉后溶解」的易错点组合。 |
| 60 | dir_batch_031.csv#2503 | CHEMISTRY | `离子方程式` | `cmp-che-02503` | 离子方程式书写中特殊情形的处理规范 | 33 | **落** | L | 节点 33 条无「浓硫酸不拆/浓盐酸浓硝酸拆、多元弱酸酸式酸根不拆、氨水按反应物与生成物分别书写、微溶物按澄清或悬浊处理」的完整规范表。 |
| 61 | dir_batch_031.csv#2554 | CHEMISTRY | `氧化还原反应概念的-易错点` | `cmp-che-02554` | 氧化还原反应中同一物质与同种元素的多重角色辨析 | 22 | **落** | L | 节点 22 条无「氧化剂与还原剂/氧化产物与还原产物/被氧化与被还原均可为同一物质或同种元素，且可多元素同时变价」的三重角色辨析。 |
| 62 | dir_batch_032.csv#2592 | CHEMISTRY | `外界条件对化学平衡的影响` | `cmp-che-02592` | 浓度与压强影响平衡的三个变色探究实验 | 26 | **弃** | R1 | 与行39 同题同节点（同一「三个变色探究实验」，正文 Jaccard 0.284），只留首见行（行39 判弃）。 |
| 63 | dir_batch_032.csv#2594 | CHEMISTRY | `可逆反应的特点及应用` | `cmp-che-02594` | 可逆反应与可逆过程的区别及平衡浓度范围的极值法 | 15 | **弃** | R1 | 与行40 同题（可逆反应与可逆过程区别及极值法），只留首见行。 |
| 64 | dir_batch_032.csv#2602 | CHEMISTRY | `化学平衡移动方向和转化率的判断` | `cmp-che-02602` | 单一气体反应物体系再充气后转化率与百分含量的变化 | 29 | **弃** | R1 | 与行41 同题（单一气体反应物再充气后的转化率与百分含量），只留首见行（行41 判弃）。 |
| 65 | dir_batch_032.csv#2604 | CHEMISTRY | `化学平衡移动的分析方法` | `cmp-che-02604` | 恒温恒容与恒温恒压的等效思维模式 | 20 | **弃** | R1 | 与行42 同题（恒温恒容/恒压等效思维），只留首见行（行42 判弃）。 |
| 66 | dir_batch_033.csv#2670 | CHEMISTRY | `碱金属元素单质及其化合物有广泛用途` | `cmp-che-02670` | 碱金属用途背后的原理：锂电池电解液、氢化钾供氢剂与钠制钾 | 7 | **弃** | R1 | 与行37 同题（碱金属用途背后的原理），只留首见行。 |
| 67 | dir_batch_033.csv#2686 | CHEMISTRY | `金属钠与二氧化碳反应的实验步骤及现象如下表` | `cmp-che-02686` | 钠与二氧化碳反应的产物与检验 | 5 | **弃** | R1 | 与行38 同题（钠与二氧化碳反应），只留首见行（行38 判清）。 |
| 68 | dir_batch_034.csv#2785 | CHEMISTRY | `有机物的分类和官能团的识别` | `cmp-che-02785` | 芳香族化合物、芳香烃与苯的同系物的包含关系 | 20 | **清** | C1 | 节点 20 条中 7 条与「有机物的分类和官能团的识别」不符（探针命中项多为他类）；候选本身同题。 |
| 69 | dir_batch_036.csv#2887 | CHEMISTRY | `酯和油脂的结构和性质` | `cmp-che-02887` | 脂肪、油与矿物油的类别区分 | 15 | **清** | C1 | 节点 15 条中 7 条与「酯和油脂的结构和性质」不符；候选的「矿物油」在包内仅 5 处、本节点 0 处，落前先清节点。 |
| 70 | dir_batch_036.csv#2891 | CHEMISTRY | `醇和酚的结构和性质易混易错点` | `cmp-che-02891` | 醇的氢键与脱水反应类型辨析 | 14 | **弃** | R2 | 同节点 ext-che-46e6110aa6-008b「醇与酚的结构性质差异」②③ 已给「醇分子间氢键→沸点高于烷烃」「分子内脱水=消去、分子间脱水=取代」；ext-che-fb8cf14cff-038 同题再述。 |
| 71 | dir_batch_036.csv#2909 | CHEMISTRY | `蛋白质的结构与性质` | `cmp-che-02909` | 酶作为生物催化剂的特点与失活 | 22 | **弃** | R3 | 包内另有节点「酶」，其材料 ext-che-366722558b-006c「酶的来源、本质与催化特点」已含条件温和/高效性/专一性；候选挂「蛋白质的结构与性质」属靶位不当。 |
| 72 | dir_batch_037.csv#2969 | CHEMISTRY | `n-的关系应用` | `cmp-che-02969` | 以物质的量为中心的关系式及其适用条件 | 3 | **弃** | R1 | 与行35 同题（物质的量关系式应用与标准状况判断），只留首见行。 |
| 73 | dir_batch_037.csv#2985 | CHEMISTRY | `误差分析的方法` | `cmp-che-02985` | 配制一定物质的量浓度溶液的误差分析思路 | 8 | **落** | L | 节点 8 条无「配制一定物质的量浓度溶液：先判影响 n 还是 V 再定偏大偏小」的思路（probe 命中项为滴定误差，非本节点）。 |
| 74 | dir_batch_037.csv#2992 | CHEMISTRY | `忽视溶液中水的存在` | `cmp-che-02992` | 溶剂水对溶液微粒数目判断的影响 | 3 | **清** | C1 | 节点 3 条全部无关（铜的冶炼、硫的存在、碳酸钠与甲酸、湿法炼锌）；候选与节点 boundary 同义，落前先清节点。 |
| 75 | dir_batch_037.csv#3004 | CHEMISTRY | `原子结构与元素周期表的关系` | `cmp-che-03004` | 原子结构、周期表分区与族序数的对应关系 | 15 | **落** | L | 节点 15 条无「周期序数=电子层数、主族序数=最外层电子数 + s/p/d/ds/f 五区」的对应关系（probe 分区 本节点仅 1 条 ext-che-6102bf2210-013）。 |
| 76 | dir_batch_037.csv#3017 | CHEMISTRY | `元素周期表与原子结构的规律` | `cmp-che-03017` | 由核外电子排布推断主族、周期与离子位置的规律 | 12 | **落** | L | 节点 12 条无「最外层 3–7 必为主族、最外层多于次外层在第二周期、阴离子最外层与次外层相同在第三周期」的推断规律。 |
| 77 | dir_batch_037.csv#3033 | CHEMISTRY | `强电解质与弱电解质` | `cmp-che-03033` | 强电解质与弱电解质的电离方程式书写规则 | 23 | **落** | L | 节点 23 条无「强电解质等号/弱电解质可逆号 + 多元弱酸分步、多元弱碱一步、酸式盐分水溶液与熔融」的书写规则（probe 等号/可逆号 本节点 2 条为邻题）。 |
| 78 | dir_batch_038.csv#3054 | CHEMISTRY | `判断弱酸的三种方法` | `cmp-che-03054` | 导电性对比实验判断弱电解质 | 13 | **弃** | R1 | 与行36 同题（导电性对比判断弱电解质），只留首见行。 |
| 79 | dir_batch_038.csv#3083 | CHEMISTRY | `混合溶液酸碱性的判断规律` | `cmp-che-03083` | 强酸与强碱恰好完全中和时体积与pH的定量关系 | 17 | **弃** | R2 | 同节点 ext-che-146a413810-015「等体积混合后酸碱性的判断规律」末句已逐字给出 10^(−a)·Va = 10^(b−14)·Vb 与体积比判定。 |
| 80 | dir_batch_038.csv#3108 | CHEMISTRY | `溶液中微粒种类的判断` | `cmp-che-03108` | 溶液中微粒种类的判断方法 | 5 | **弃** | R2 | 同节点已有 cmp-che-02317「溶液中微粒种类的判断方法」（标题逐字相同）与 ext-che-6a488d0c96-008「溶液中微粒种类的判断」；probe 电离方程式/水解方程式/微粒种类 均在本节点命中。 |
| 81 | dir_batch_038.csv#3109 | CHEMISTRY | `离子浓度大小比较的方法` | `cmp-che-03109` | 不同溶液中同一离子浓度的比较方法 | 10 | **落** | L | 节点 10 条无「比较不同溶液中同一离子浓度看其他离子的促进/抑制」的判据（probe 同一离子 本节点仅 1 条 ext-che-1d09ecb78a-037）。 |
| 82 | dir_batch_039.csv#3144 | CHEMISTRY | `吸热反应和放热反应` | `cmp-che-03144` | 反应体系的内能及其影响因素 | 25 | **弃** | R1 | 与行32 同题（反应体系内能及其影响因素），只留首见行。 |
| 83 | dir_batch_040.csv#3200 | CHEMISTRY | `可充电电池常考知识-10-点和易错知识-3-点` | `cmp-che-03200` | 充放电时电解质溶液离子浓度与酸碱性的变化 | 6 | **弃** | R1 | 与行44 同题（充放电时离子浓度与酸碱性变化），只留首见行。 |
| 84 | dir_batch_040.csv#3258 | CHEMISTRY | `制取cl2装置的创新` | `cmp-che-03258` | 点滴板微型实验的优点与氯水性质检验的现象判断 | 6 | **弃** | R1 | 与行33 同题（点滴板微型实验与氯水现象），只留首见行。 |
| 85 | dir_batch_041.csv#3314 | CHEMISTRY | `n2o-no和no2等氮氧化物是空气污染物-含有氮氧化物的尾气需处理后才能排放` | `cmp-che-03314` | 氮氧化物尾气的N2O分解与氧化性吸收剂强化脱硝 | 24 | **弃** | R1 | 与行54 同题（氮氧化物尾气 N2O 分解与氧化吸收），只留首见行。 |
| 86 | dir_batch_041.csv#3336 | CHEMISTRY | `熟记硅及其化合物的特殊性质` | `cmp-che-03336` | 硅及其化合物特殊性质的归纳 | 13 | **弃** | R1 | 与行55 同题（硅及其化合物特殊性质归纳），只留首见行。 |
| 87 | dir_batch_043.csv#3446 | BIOLOGY | `环境因素参与调节植物的生命活动` | `cmp-bio-03446` | 植物激素类易错辨析：实验结论、含量与激素分工 | 48 | **落** | L | 节点 48 条无「生长素促细胞核分裂/细胞分裂素促细胞质分裂的分工」「喷施细胞分裂素延长绿色叶菜保鲜」（probe 细胞分裂素 62 处、叶菜保鲜 0 处，均非本节点）。 |
| 88 | dir_batch_043.csv#3475 | BIOLOGY | `糖类和脂肪的转化` | `cmp-bio-03475` | 糖类与脂肪的供能数值比较及供能顺序 | 19 | **落** | L | 节点内 bio-kb2-03-sugar-fat-conversion 给出供能顺序但无 1 g 糖原≈17 kJ / 1 g 脂肪≈39 kJ 的数值比较（复核提示：与既有材料「供能顺序」部分重叠）。 |
| 89 | dir_batch_043.csv#3513 | BIOLOGY | `细胞呼吸的影响因素及应用` | `cmp-bio-03513` | 果蔬保鲜的适宜储藏条件 | 59 | **落** | L | 节点 59 条无「含水量约 85%、温度 1–3 ℃、O2 体积分数 2–4%、湿度 90–95%」的一组具体储藏数值（probe 含水量 本节点 2 条为原理性表述）。 |
| 90 | dir_batch_044.csv#3527 | BIOLOGY | `技巧-实验数据-与-9-3-3-1及其变式-间的转化方法` | `cmp-bio-03527` | 显性上位 12∶3∶1 的成因与测交后代比例 | 20 | **弃** | R2 | 同节点已有 3 条材料讲到 12∶3∶1 变式：ext-bio-e88d6562c4-003b「和16的分离比模型与数据转化三步法」、ext-bio-dbce67b1b0-012「三步法破解 9∶3∶3∶1 的比例变式」、ext-bio-f30a783d56-056/057。 |
| 91 | dir_batch_044.csv#3528 | BIOLOGY | `个体或配子致死问题的处理-规律方法` | `cmp-bio-03528` | 隐性纯合致死 3∶1 与单显性配子致死 3∶1∶3∶1 | 63 | **弃** | R2 | 同节点 ext-bio-5f510cdc4a-728「个体或配子致死导致AaBb自交后代分离比的改变」已含隐性纯合致死 9∶3 与含 A 或 B 配子致死 3∶1∶3∶1；ext-bio-ca5e821ffe-040 同。 |

### 2.1 支撑判据的实测（怎么得出「已覆盖」/「跑题」）

- **批内重复**（12:4x 按 `recheck/_multimouth_recheck.py` 逐字复跑，输出 `recheck/_multimouth_recheck.json`；逐对数据 `recheck/_cap91_pairs.tsv`）：完全重复 1 组（行 55/86，标题与正文全等）；**R1 共 20 行**（＝ 20 对的后见行，逐对全量实测、无其他遗漏对）= **9 组标题逐字相同对**（后见行 2、30、62、65、66、78、82、83、86，对应 1/16/39/42/37/36/32/44/55；正文 Jaccard 0.41–1.00）+ **10 对同节点同题近重复对**（后见行 19、27、29、31、63、64、67、72、84、85，对应 10/12/15/17/40/41/38/35/33/54；正文 Jaccard 0.41–0.81）+ **1 对相邻节点同题对**（行 22/13：`单摆` ↔ `单摆的受力特征`，正文 Jaccard 0.55）。原文「12 对…涉及 20 行」为前轮 dupcheck 汇总口径，本版按逐对实测重列（每对留首见行、弃后见行）。
- **节点多口性**（同上复跑逐字复现）：按「材料标题是否含节点名去通用尾缀后的核心子串」统计跑题率，≥0.5 的 4 个节点是 `n-的关系应用`(1.00)、`波动图象的特点`(0.88)、`技巧-实验数据-与-9-3-3-1及其变式-间的转化方法`(0.55)、`键参数及其应用`(0.50)；该指标对「同后缀串名」（如各种「的特点」）会低估，故 C1 类裁定同时引了各批 evidence 明列的跑题 slug（见上表证据列）。
- **断言回查**（`probe.json` / `gapcheck.py`）：把每行 evidence 里的「关键词/检索词」与人工挑的候选核心断言词拿到**现包全量材料**里回查。22 行判 R2 的依据是某个**同节点**材料已逐点给出候选核心结论（如行 20 `ext-phy-41052fd08f-005` 末句、行 21 `ext-phy-882b26e5cb-003` 三条、行 79 `ext-che-146a413810-015` 末句、行 80 `cmp-che-02317` 同名同题）。
- **跨节点同题**（C3）：`ext-mat-9d8541e663-009`、`ext-phy-9d4e47b803-001`、`ext-phy-e0dafc554c-008`+`phys-kb-sensor-conversion-principle` 已在包内，但挂在与本行目标不同的节点上；直接落会在包内造成同内容两挂，故判「先按去重/改绑口径处理再落」。

## 3. 落地通道调查（候选层到底怎么进包）

现包内 `cmp-*` 材料实读 **81 条**（release，分卷 v2-01…v2-19；**staging 现 22 卷、cmp- 115 条 = 81 + 本线 34**）。`sourceId` 四种：`registry-kb-{math,physics,chemistry,biology}-knowledge-list:<科>`（12:4x 实扫核对），与本批同源同通道，说明目录层材料的通道是通的、来源已登记。

### 通道 A（本线已执行）：候选 → 13 键材料载荷 → staging 侧车

```
python tools/kb_build/apply_authored_materials.py --materials <载荷.jsonl> --dry-run   # 校验
python tools/kb_build/apply_authored_materials.py --materials <载荷.jsonl> --write     # 只写 build/kb-staging/
```

必填接口（每行落库都要）：13 键（键序 `new_content.MATERIAL_KEYS`）、`type ∈ 3 值白名单`、`derivationKind=REVIEWED_SYNTHESIS`、`sourceId` 必须是**已登记来源**、`bindings` 恰好 1 条 PRIMARY 且节点 `kb:moe-2025-four-subjects-v1:<科小写>:atomic:<node_slug>` 必须存在、`contentMarkdown` 1–4 行、标题不得是例题。载荷已按此生成并 dry-run 0 error。

### 通道 B（本线未执行，需窗口持有者）：staging → 成品包

`python tools/kb_build/promote.py`（唯一写成品目录的通道，跑 23 门 + 契约 + roundtrip 后落盘并推进 version）。
本线不执行：成品包是全仓发布面，且 `build/kb-staging/` 上另有会话在写——`WRITE-WINDOW.md` 第 1/8 步规定 staging 是**单写者窗口**，本会话实读：staging 侧车 mtime 2026-10-11 06:55–06:59（本线写入前），且比 `.staging-freeze.json`（2026-10-02）多出 v2-13…v2-19 七卷，说明另有线在推进同一 staging。**12:4x 复核：该并发仍在——12:08 另一线跑判定表 resync（表内 103,813 行）、12:28 写回 22 卷、12:28/12:33 跑门自检；staging 现 22 卷（v2-20…v2-22 为另一线新开）、59,008 条材料。** 本线只做**追加**（不改任何既有材料、不删卷），写入前已把涉及文件备份到 `build/agent-batch8/backup/capacity91_staging/`（21 个文件 91,427,268 B ＝ 91.4 MB；12:4x 复测一致）。

### 通道 C（结构上可用，本线未执行）：候选作为判定行 → `material_judgments.csv` → `materialize --write`

**更正（12:4x 复核）**：原文（前轮）写「块池没有『知识清单（学生版）』的块 → 通道 C 天然不可用」，**该结论不成立**。逐条实测：91 行的 **40/40 个源 docx 全部**在块池 `tools/kb_coverage/tables/extracted_chunks.rekeyed.jsonl`（186,635 块）中命中（NFKC 规范化后按文件名逐名命中，无模糊匹配；族：`2026年新高考资料*/一轮复习/2026年高考*一轮复习知识清单（全国通用）`；例：行 1 源文档 `专题01 立体几何初步（知识清单）（学生版）.docx` 有 23 块，承载候选原文的块 id `ba6dd21d28-004` 内可读到「两个面为直角三角形…斜边的中点为该三棱锥外接球的球心」原文）。用**真实块键**实跑探针（只读）：

```
>>> row = {'chunk_rel': '2026年新高考资料/一轮复习/2026年高考数学一轮复习知识清单（全国通用）/2026版/专题01 立体几何初步（知识清单）（学生版）.docx',
...        'chunk_id': 'ba6dd21d28-004', 'action': 'MATERIAL', 'node_slug': '求解几何体外接球的半径的思路', 'type': 'METHOD_MODEL', …}
>>> mz.plan([row])['errors']
[]          # 通过；write() 将派生 slug ext-mat-ba6dd21d28-004
```

即：把候选按判定表形态（列 `chunk_rel,chunk_id,action,node_slug,type,title,summary,applicability,content,boundary,note,midx`）喂进 `tools/kb_coverage/tables/material_judgments.csv`，再 `materialize --write`，**结构上可落**。本线未执行，原因是实况而非结构：

1. **写入窗口不在本线**：`material_judgments.csv` 是另一线的在飞表（12:08 其 resync 实跑：「表内共 103813 行」，mtime 12:08），且 `materialize --write` 写的是 staging 侧车——staging 单写者窗口现由另一线持有（12:28 写回 22 卷、12:28/12:33 门自检）。并发写入违反 `WRITE-WINDOW.md` 的单写者原则与第 1/3 步纪律，且与其「写回全部卷」互为读-改-写竞态（互相覆盖）。
2. **即便能写也是重复**：34 条「落」行已走通道 A 落在 staging；同内容再经 C 落会给同节点造 `ext-*` 第二条同题材料。14「清」/43「弃」按裁定本就不该现在落。
3. **语义要求**（若窗口持有者后续选此通道）：`materialize` 的 `sourceLocator` 会写「桌面资料 {top_dir} / {chunk_rel}（块 {chunk_id}）」，故引用的块应是**真正承载该条目原文的块**（本次 40 个源文档都可定位，但逐行定位＝每行一次块匹配）。

### 通道 D（本线未执行，需要动在飞文件）：`merge_candidate_verdicts.py --write` 重跑

`tools/kb_build/merge_candidate_verdicts.py` 与其测试 `tools/tests/test_kb_merge_candidate_verdicts.py` **是另一会话的在飞文件**（`git status` 实读为 `M`），本线不碰、也不重跑 `--write`：它按 `MAX_VISIBLE=4`（`:165`、`:209-216`）现场重算 counts，当前 staging 的节点材料数已远大于 9-30 生成时的值，重跑会把**当年通过的行也一起拦掉**并覆盖 `capacity_blocked.csv` 等他会话产物。

## 4. 已落清单（34 条，落 staging；release 未动）

前轮落盘（本线，07:5x）：

```
python tools/kb_build/apply_authored_materials.py --materials build/agent-batch8/capacity91/capacity91_landing_materials.jsonl --write
  → {'added': 34, 'skipped': 0, 'sidecars': {'moe-2025-teaching-support-v2-19.json': 828}}
python tools/kb_build/apply_authored_materials.py --materials … --write      # 幂等复跑
  → {'added': 0, 'skipped': 34, 'sidecars': {}}
PYTHONPATH=tools python tools/kb_build/gate.py → 全部通过：缺陷归零（23 项全 0）
```

12:4x 复核（本版；staging 已经在另一线写入之后）：

```
python tools/kb_build/apply_authored_materials.py --materials … --dry-run
  → 材料 34 条；错误 0                     # 对当前 staging 仍校验通过
只读直扫 staging 22 卷（recheck/_cap91_recheck.py → _cap91_recheck.json）：
  → 34/34 slug 在包；34/34 绑定节点 == 载荷绑定节点；title/type/summaryMarkdown/contentMarkdown 34/34 逐条相等
  → 全部位于 moe-2025-teaching-support-v2-19.json；分散在 34 个不同节点
  → staging 现 22 卷 / 59,008 条 / cmp- 115（release 19 卷 / 50,869 / cmp- 81）
```

科目分布 {'MATH': 1, 'PHYSICS': 3, 'CHEMISTRY': 27, 'BIOLOGY': 3}；落在 34 个不同节点。

| # | 候选 slug | 科目 | 标题 | 落点节点 |
|---|---|---|---|---|
| 1 | `cmp-mat-00069` | MATH | 共斜边的直角三角形模型：斜边中点即外接球球心 | `求解几何体外接球的半径的思路` |
| 10 | `cmp-phy-00760` | PHYSICS | 动量守恒定律的推导 | `动量守恒定律的推导` |
| 13 | `cmp-phy-00977` | PHYSICS | 单摆摆球的两种运动与向心力、回复力的存在条件 | `单摆的受力特征` |
| 16 | `cmp-phy-01079` | PHYSICS | 伦琴发现X射线 | `近代物理学史-相关粒子的发现和实验` |
| 32 | `cmp-che-01714` | CHEMISTRY | 反应体系的内能及其影响因素 | `吸热反应和放热反应` |
| 33 | `cmp-che-01796` | CHEMISTRY | 点滴板微型实验的优点与氯气性质检验的现象判断 | `制取cl2装置的创新` |
| 34 | `cmp-che-01807` | CHEMISTRY | 溶液、胶体、浊液三种分散系的比较与鉴别 | `fecl3溶液-fe-oh-3胶体-fe-oh-3浊液是三种重要的分散系` |
| 35 | `cmp-che-01833` | CHEMISTRY | n 与 N、m、V、c 关系式的应用与标准状况判断 | `n-的关系应用` |
| 36 | `cmp-che-01888` | CHEMISTRY | 导电性对比实验判断弱电解质 | `判断弱酸的三种方法` |
| 37 | `cmp-che-01905` | CHEMISTRY | 碱金属用途背后的原理：锂电池电解液、氢化钾供氢剂与钠制钾 | `碱金属元素单质及其化合物有广泛用途` |
| 40 | `cmp-che-01932` | CHEMISTRY | 可逆反应与可逆过程的区别及平衡浓度范围的极值确定法 | `可逆反应的特点及应用` |
| 43 | `cmp-che-01953` | CHEMISTRY | 同位素、同素异形体与同分异构体的辨析及同位素单质的归属 | `原子结构中微粒的关系及计算` |
| 44 | `cmp-che-01977` | CHEMISTRY | 充放电时电解质溶液离子浓度与酸碱性的变化 | `可充电电池常考知识-10-点和易错知识-3-点` |
| 45 | `cmp-che-01984` | CHEMISTRY | 弱酸酸式盐对水电离的影响判断 | `水的电离平衡移动的影响因素` |
| 47 | `cmp-che-02105` | CHEMISTRY | 铁与水蒸气反应实验的高温条件与氢气验纯 | `铁与水蒸气反应的注意事项` |
| 49 | `cmp-che-02160` | CHEMISTRY | 氢键的形成条件、表示方法与两类氢键 | `分子间作用力` |
| 50 | `cmp-che-02161` | CHEMISTRY | 键的极性与羧酸酸性的关系 | `键的极性和分子的极性` |
| 51 | `cmp-che-02177` | CHEMISTRY | 共价键的形成条件与σ键、π键的判断方法 | `键参数及其应用` |
| 53 | `cmp-che-02185` | CHEMISTRY | 配位键稳定性的比较 | `配位键和配合物` |
| 54 | `cmp-che-02257` | CHEMISTRY | 氮氧化物尾气处理中的N2O分解与氧化吸收 | `n2o-no和no2等氮氧化物是空气污染物-含有氮氧化物的尾气需处理后才能排放` |
| 55 | `cmp-che-02401` | CHEMISTRY | 硅及其化合物特殊性质的归纳 | `熟记硅及其化合物的特殊性质` |
| 57 | `cmp-che-02430` | CHEMISTRY | 铜及其化合物易错判断的完整清单 | `铜及其化合物的易错点` |
| 58 | `cmp-che-02474` | CHEMISTRY | 胶体聚沉与蛋白质变性的变化类型辨析 | `胶体的性质及应用` |
| 59 | `cmp-che-02475` | CHEMISTRY | 书写Fe(OH)3胶体制备方程式的易错点与胶体遇电解质现象 | `fe-oh-3胶体的制备` |
| 60 | `cmp-che-02503` | CHEMISTRY | 离子方程式书写中特殊情形的处理规范 | `离子方程式` |
| 61 | `cmp-che-02554` | CHEMISTRY | 氧化还原反应中同一物质与同种元素的多重角色辨析 | `氧化还原反应概念的-易错点` |
| 73 | `cmp-che-02985` | CHEMISTRY | 配制一定物质的量浓度溶液的误差分析思路 | `误差分析的方法` |
| 75 | `cmp-che-03004` | CHEMISTRY | 原子结构、周期表分区与族序数的对应关系 | `原子结构与元素周期表的关系` |
| 76 | `cmp-che-03017` | CHEMISTRY | 由核外电子排布推断主族、周期与离子位置的规律 | `元素周期表与原子结构的规律` |
| 77 | `cmp-che-03033` | CHEMISTRY | 强电解质与弱电解质的电离方程式书写规则 | `强电解质与弱电解质` |
| 81 | `cmp-che-03109` | CHEMISTRY | 不同溶液中同一离子浓度的比较方法 | `离子浓度大小比较的方法` |
| 87 | `cmp-bio-03446` | BIOLOGY | 植物激素类易错辨析：实验结论、含量与激素分工 | `环境因素参与调节植物的生命活动` |
| 88 | `cmp-bio-03475` | BIOLOGY | 糖类与脂肪的供能数值比较及供能顺序 | `糖类和脂肪的转化` |
| 89 | `cmp-bio-03513` | BIOLOGY | 果蔬保鲜的适宜储藏条件 | `细胞呼吸的影响因素及应用` |

## 5. 交接单（每行落库所需接口）

### 5.1 落 34 条 —— 只需「晋升」一步（载荷已校验并已落 staging）

| 步骤 | 命令/接口 | 谁 |
|---|---|---|
| 1. 确认 staging 无并发写入 | `python tools/kb_build/staging_freeze.py --snapshot` 与 `--compare`（窗口纪律见 `WRITE-WINDOW.md`）。**12:4x 实况：另一线仍在写（12:28 写回 22 卷、12:33 门自检），须等其收窗** | 窗口持有者 |
| 2. 晋升成品包 | `python tools/kb_build/promote.py` 后 `python tools/kb_build/promote.py --dry-run` 复核；落点=release 13 个文件 + version 单调。**前置：当前 staging 门非全绿（ghost_aliases=25，另一线在飞项）——须先由其修绿或具名豁免** | 窗口持有者 |
| 3. 复核数字 | 落盘时点：材料数 50,869 → 50,903；cmp- 材料 81 → 115；节点材料数 +1 的节点 34 个（12:4x 现值 22 卷 / 59,008 条 / cmp- 115，含另一线写入；以 slug 对拍为准，见 §4） | 窗口持有者 |
| 4. 全量测试 | `python -m unittest discover -s tools/tests -t tools`（本线未跑，见 §6） | 窗口持有者 |

**晋升前必读**：`build/kb-staging/` 不是本线独占——本线写入前它已含另一会话的未晋升改动（v2-13…v2-19 七卷、别名与绑定表改动）；**12:4x 复核**：staging 已是 22 卷 / 59,008 条，另一线仍在写（12:08 resync、12:28 写回、12:28/12:33 门自检），晋升窗口须由持有者发起并与另一线对齐。`promote.py` 发布的是**整个 staging**，会把那些改动一并带进成品包；因此晋升必须由该窗口的持有者执行，并在晋升前把「哪些改动属谁」对齐。本线已落 staging 的 34 条在 `moe-2025-teaching-support-v2-19.json`（12:4x 复扫 34/34 仍在原位），可按 slug `cmp-*` 逐条辨认。

### 5.2 清 14 条 —— 先做一次包内清理动作，再走 5.1

| # | 类型 | 目标 | 所需接口 |
|---|---|---|---|
| 3 | C1 | `对命题条件探索的三种方法`（现 12 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 8 | C3 | `对数函数的图象与性质应用`（现 8 条） | 包内已有同内容材料挂在他节点：二选一——把既有材料改绑/加挂到本节点，或按去重口径判本行为冗余；之后再落 |
| 9 | C1 | `牛顿运动三定律的基本概念和规律`（现 8 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 11 | C3 | `磁场`（现 11 条） | 包内已有同内容材料挂在他节点：二选一——把既有材料改绑/加挂到本节点，或按去重口径判本行为冗余；之后再落 |
| 12 | C3 | `常见传感器的特点`（现 15 条） | 包内已有同内容材料挂在他节点：二选一——把既有材料改绑/加挂到本节点，或按去重口径判本行为冗余；之后再落 |
| 25 | C1 | `合外力做功与动能的关系`（现 8 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 38 | C1 | `金属钠与二氧化碳反应的实验步骤及现象如下表`（现 5 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 46 | C2 | `铁的物理性质`（现 0 条） | 目标节点已 MERGE → 重定靶：把载荷里的绑定节点改为 `铁`（行 46）/ `铁的氢氧化物`（行 48）后落 |
| 48 | C2 | `铁的氢氧化物的稳定性`（现 0 条） | 目标节点已 MERGE → 重定靶：把载荷里的绑定节点改为 `铁`（行 46）/ `铁的氢氧化物`（行 48）后落 |
| 52 | C1 | `v2o5常用作so2转化为so3的催化剂`（现 5 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 56 | C1 | `铜及其化合物的性质`（现 29 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 68 | C1 | `有机物的分类和官能团的识别`（现 20 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 69 | C1 | `酯和油脂的结构和性质`（现 15 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |
| 74 | C1 | `忽视溶液中水的存在`（现 3 条） | 把节点内跑题/串名材料改绑到正确节点（`tools/kb_build/tables/material_rebind.csv` 链，执行器 `apply_rebind_conflicts.py`/`sync_rebind_decisions.py`）或按 F3「删冗余」删料（`drop_materials.py`）；清完再落本行 |

C1 明细（节点内需处理的材料，来自本批 evidence 明列 + 本会话 probe）：

- 行 3 `对命题条件探索的三种方法`：节点 12 条全部是他专题「三种方法」串名（math-restricted-count-three-approaches、math-kb-set-representation-methods、math-hj-s211-zero-count、math-hj-s105-conditional-prob、math-mo-zuotu-sanfa 等），无一涉探索性问题；候选本身同题。
- 行 9 `牛顿运动三定律的基本概念和规律`：节点 8 条中 7 条跑题：phys-hj2-kaipule-san-da-dinglv（开普勒三定律）、phys-hj3-three-rules-one-law-usage/causality、phys-fx-three-rules-one-law-division（三定则一定律）、phys-mo-l10/l11/l18（牛顿定律综合应用·易混）。
- 行 25 `合外力做功与动能的关系`：节点 8 条中 6 条跑题：phys-only-electric-work-conservation、phys-double-rod-energy-*、phys-max-ke-stopping-voltage、phys-kb-momentum-kinetic-energy-relation、phys-hj2/hj3/fx 动量与动能对比系列。
- 行 38 `金属钠与二氧化碳反应的实验步骤及现象如下表`：节点 5 条全部跑题（钠与熔融盐、CO2 的氧化性、钠与水、氯气与金属、氨气与二氧化氮），无钠与 CO₂ 反应本身；节点名为表格残片「…现象如下表」。
- 行 52 `v2o5常用作so2转化为so3的催化剂`：节点 5 条全为工艺流程题残片（酸浸/转化/催化剂/净水剂），与节点 boundary（SO2/SO3 价层电子对数、空间构型与杂化）不符；节点名「V2O5常用作SO2转化为SO3的催化剂」为句子残片。
- 行 56 `铜及其化合物的性质`：节点 29 条中 14 条与「铜及其化合物的性质」不符（probe 显示 Cu2O/歧化/无水 CuSO4 在包内多挂他节点）；候选本身同题。
- 行 68 `有机物的分类和官能团的识别`：节点 20 条中 7 条与「有机物的分类和官能团的识别」不符（探针命中项多为他类）；候选本身同题。
- 行 69 `酯和油脂的结构和性质`：节点 15 条中 7 条与「酯和油脂的结构和性质」不符；候选的「矿物油」在包内仅 5 处、本节点 0 处，落前先清节点。
- 行 74 `忽视溶液中水的存在`：节点 3 条全部无关（铜的冶炼、硫的存在、碳酸钠与甲酸、湿法炼锌）；候选与节点 boundary 同义，落前先清节点。

### 5.3 弃 43 条 —— 无落库接口，只需回写账目

- R1（本批内重复）20 条：把首见行标为 survivor，其余在账目里记为「同批重复，已由行 X 覆盖」；R2（现包已覆盖）22 条：证据列已给覆盖它的材料 slug；R3（靶位不当）1 条：见行 71。
- 本批账目接口（建议）：`knowledge-production/candidate-merge/capacity_blocked.csv` 的替代品 = `capacity91_verdicts.csv`（91 行逐条有裁定 + 证据），并让 `summary.json` 的 `capacity_blocked` 不再由已废止的 `MAX_VISIBLE=4` 规则产生——**该改动在 `merge_candidate_verdicts.py`（另一会话在飞文件）内，本线不执行**，登记为交接。

## 6. 未验证 / 风险（如实记录）

- **未晋升**：34 条只在 staging，学生侧不可见；release 仍 version 12。晋升需窗口持有者执行（§5.1）。
- **未跑全量测试**：前轮跑了 `PYTHONPATH=tools python tools/kb_build/gate.py`（当时 23/23 为 0）、`python tools/ci/run_kb_checks.py`（gates/consistency/roundtrip/manifest/dense **5 节全 OK**；INFO 报 `material_bindings` 生效 517/欠账 3、三值外 type 0）、`python -m unittest discover -s tools/tests -t tools -p "test_kb_path_guard.py"`（5 tests OK）与 `apply_authored_materials` 的校验/幂等。**12:4x 复核新增实跑**：`gate.py`（现 24 项、1 FAIL＝ghost_aliases=25，另一线在飞项）、`materialize.plan` 真实块键探针（通道 C，errors=[]）、`apply_authored_materials --dry-run`（34 条 / 0 error）、逐条只读对拍（`recheck/_cap91_recheck.py`）与 multimouth 复跑（`recheck/_multimouth_recheck.py`）。**`python -m unittest discover -s tools/tests -t tools` 全量套件仍未跑**（`WRITE-WINDOW.md` 第 4 步要求的是窗口内全量，属晋升侧动作）。
- **evidence 过期风险已实测**：本批 91 行 evidence 写于 2026-09-30，现包节点普遍长过料；因此 R2 的 22 行是**重新实测**得出的，不是照抄 evidence。其余行的「未被覆盖」结论用的是本会话 probe（断言词回查现包全量材料），对**改写型**同义表述仍可能漏判——标为复核提示的 3 行（49、55、88）建议在晋升前抽读一眼。
- **跨节点同题 3 行（C3）**：`ext-mat-9d8541e663-009` / `ext-phy-9d4e47b803-001` / `ext-phy-e0dafc554c-008`+`phys-kb-sensor-conversion-principle` 的存在说明包内已有同内容两挂的隐患，本线只登记，未改绑（改绑属 F3 的施工面）。
- **staging 并发（12:4x 更新）**：本线写入前 staging 已被另一线写过（mtime 06:55–06:59，比 10-02 冻结清单多 7 卷）；12:4x 复核时另一线仍在写（12:08 判定表 resync、12:28 写回 22 卷、12:28/12:33 门自检；现 22 卷 / 59,008 条）。本线是**追加式**写入且已备份（`build/agent-batch8/backup/capacity91_staging/`，21 个文件 91,427,268 B），12:4x 起未再向 staging 写入。若另一线用 10-02 冻结清单做 `--compare`，会看到包括本线在内的差异——请以 commit 级 `git status`/卷 mtime 归因。
- **通道 C 更正**：§3 通道 C 原文「结构上不可用」经 12:4x 复核推翻（块池含 40/40 源文档的块；真实块键探针 errors=[]）——更正为「结构上可用、本线未执行」，理由见 §3。原误判来自一次用**虚构块键**（`chunk_rel='知识清单（学生版）/…'`、`chunk_id='cmp-mat-00069'`）的探针：报「块不存在」只说明该键不存在，不能推出整族无块。
- **本版复核范围（12:4x）**：逐条 0/91 与 34 条落库对拍为**全量**；R2/C3 覆盖证据为**抽验**——实读了 13 条被引材料（行 6、8、11、12、20、21、71、79、80、88、91 的覆盖 slug 及其绑定/标题）、4 个断言短语（如「若粒子穿过极板间的时间极短」仅 1 处命中＝行 20 覆盖材料）、行 91 覆盖材料的比值写法（正文用「9比3」「3比1比3比1」文本形态，非 ∶ 符号）、retired 清单 2 行、重定靶节点 2 个（`铁`、`铁的氢氧化物` 均在包）——均与表内证据一致；未逐行重读全部 91 行的语义（前轮已做，本版未推翻任何一条裁定）。

## 7. 复跑命令（全部本会话实跑过）

```
python build/agent-batch8/analyze91.py                              # 0/91 在包 + 节点现材料数
python build/agent-batch8/dump91.py                                 # 91 行来源裁决行 + 现包同节点材料
python build/agent-batch8/dupcheck91.py                             # 候选 vs 同节点既有材料 相似度
python build/agent-batch8/capacity91/multimouth.py                   # 节点多口性 + 批内重复
python build/agent-batch8/capacity91/coverage.py                     # 逐字长窗口覆盖率
python build/agent-batch8/capacity91/gapcheck.py                     # evidence 关键词回查现包
python build/agent-batch8/capacity91/probe.py                        # 候选核心断言词回查现包
python build/agent-batch8/capacity91/verdicts.py                     # 生成裁定表 + 落库载荷
python tools/kb_build/apply_authored_materials.py --materials build/agent-batch8/capacity91/capacity91_landing_materials.jsonl --dry-run
PYTHONPATH=tools python tools/kb_build/gate.py
python tools/ci/run_kb_checks.py
python -m unittest discover -s tools/tests -t tools -p "test_kb_path_guard.py"
```

12:4x 复核（本版新增；均只读，输出在 `build/agent-batch8/capacity91/recheck/`）：

```
python build/agent-batch8/capacity91/recheck/_cap91_recheck.py       # 91 行 × release/staging：slug/节点/计数/同题命中 + 34 条载荷对拍 → _cap91_recheck.json/tsv
python build/agent-batch8/capacity91/recheck/_multimouth_recheck.py  # 批内重复 + 节点多口性（复核副本，不覆盖原 multimouth.json）→ _multimouth_recheck.json
python tools/kb_build/apply_authored_materials.py --materials build/agent-batch8/capacity91/capacity91_landing_materials.jsonl --dry-run
PYTHONPATH=tools python tools/kb_build/gate.py                        # 现 24 项；1 FAIL = ghost_aliases 25（另一线在飞）
PYTHONPATH=tools python - <<'PY'                                      # 通道 C 探针（真实块键 → plan errors=[]）
import sys; sys.path.insert(0, "tools")
from kb_coverage import materialize as mz
row = {"chunk_rel": "2026年新高考资料/一轮复习/2026年高考数学一轮复习知识清单（全国通用）/2026版/专题01 立体几何初步（知识清单）（学生版）.docx",
       "chunk_id": "ba6dd21d28-004", "action": "MATERIAL", "node_slug": "求解几何体外接球的半径的思路",
       "type": "METHOD_MODEL", "title": "共斜边的直角三角形模型：斜边中点即外接球球心",
       "summary": "…", "applicability": "…", "content": "…", "boundary": "…", "note": "probe", "midx": ""}
print(mz.plan([row])["errors"])
PY
```

## 8. 复核刷新记录（2026-10-11 12:2x–13:0x，本版新增）

| # | 复核项 | 命令（均只读） | 结果 |
|---|---|---|---|
| 1 | 91 行逐条 0/91 | `python build/agent-batch8/capacity91/recheck/_cap91_recheck.py` | release **0/91** slug 命中；staging 34/91（=已落清单）；89/91 节点在包；表内材料数列复测 **89/91 逐行相等** |
| 2 | 34 条已落现状 | 同上（对拍载荷 `capacity91_landing_materials.jsonl`） | 34/34 在包、绑定正确、四字段相等；全部在 `moe-2025-teaching-support-v2-19.json` |
| 3 | 落库载荷仍可校验 | `apply_authored_materials … --dry-run` | 材料 34 条；错误 0 |
| 4 | 批内重复 / 多口性 | `python build/agent-batch8/capacity91/recheck/_multimouth_recheck.py` | 1 组完全重复（55/86）；9 组标题逐字相同（18 行）；≥0.5 跑题节点 4 个（同 §2.1） |
| 5 | R1 逐对实测 | 见 `recheck/_cap91_pairs.tsv` | 20 对全量给出 title_eq / title_jac / content_jac |
| 6 | 覆盖证据抽验 | 13 条被引材料 + 4 个短语 + 比值写法 + retired 2 行 + 重定靶节点 2 个 | 全部与表内证据一致（范围见 §6） |
| 7 | 通道 C 可执行性 | 40 源文档 × 块池匹配 + `mz.plan` 真实块键探针 | 40/40 命中；errors=[]——**更正**原文「结构不可用」 |
| 8 | 当前 gate / staging 并发 | `PYTHONPATH=tools python tools/kb_build/gate.py` 等 | 24 项 / 1 FAIL（ghost_aliases=25，另一线在飞）；staging 22 卷 / 59,008 条 / cmp- 115 |

本版未改 91 行裁定（34 落 / 14 清 / 43 弃）中任何一条；未向包（release/staging）、判定表或任何在飞文件写入；对本文档的改动前已备份到 `build/agent-batch8/backup/docs_kb-batch8-capacity-91.md.pre-refresh-20261011`。
