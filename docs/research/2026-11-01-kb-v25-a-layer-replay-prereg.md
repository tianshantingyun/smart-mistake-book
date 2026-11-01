# KB v25 A 层定向抽取回放：预注册（2026-11-01）

> **性质**：出数前冻结。§1–§5 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§5。
> **写作时点**：2026-11-01；HEAD `a0641bb7392f380b5716afe95dd3c371dd0c7670`（`git rev-parse HEAD` 实跑），`origin/main` 同值。
> **上游**：`build/agent-batch30/a-layer-extract.md` §4.1（3 条 MATERIAL 六字段全文）、`build/agent-batch30/a-layer-work/a-layer-verdicts.csv`（3 行 12 列）、`build/agent-batch30/a-layer-work/head-merge-rebuild.log`（HEAD 副本 merge 对拍 PASS）、`build/agent-batch30/v25.log`（v25 前臂）、`build/agent-batch30/quota/SUMMARY.md`（配额臂回滚）、`build/agent-batch30/render-final.md`（v24 基线 117/129）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 staging、未写判定表。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
git rev-parse HEAD                                   # a0641bb7392f380b5716afe95dd3c371dd0c7670
git rev-parse origin/main                            # a0641bb7392f380b5716afe95dd3c371dd0c7670  → HEAD==origin/main ✓
git status --porcelain | wc -l                       # 711
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   221  M
#   490 ??
git status --porcelain -- <§4.1 写面>                 # 空（写面全部 CLEAN）
ls docs/research/2026-11-01-kb-v25-a-layer-replay-prereg.md
#   No such file or directory  → 本次是新建，只写一次
```

- **数字口径说明（实算，不沿用旧数）**：ask 记「porcelain 748 项」；本次实跑 **711**（221 `M` + 490 `??`），差 **37**。差值来自 v24 写面已提交（`2bb4582a`）与 v25/配额臂回滚后脏项减少；本文按 **711** 登记，不把 748 当当前事实。
- 711 条脏项属他线（含批次 30 已采纳的渲染 v2、v24 遗留与配额臂残留）；本批不触碰、不裹挟、不回滚他线。

### 0.2 数字口径（本会话实算）

| 事实 | 值 | 方式 |
|---|---:|---|
| A 层子面总数 | **22**（16 条 KEEP PARTIAL 未单设子面 + 3 薄料 + 3 边界；S11 与 T03 同节点不同子面） | `a-layer-extract.md:12` |
| A 层 MATERIAL | **3**（S01、S02、T03） | `a-layer-extract.md:13` |
| A 层 SKIP / NOT_FOUND | **15 / 4** | `a-layer-extract.md:14-15` |
| SKIP→MATERIAL 翻转 | **0**；UNJUDGED→MATERIAL **3** | `a-layer-extract.md:16-17` |
| HEAD 副本 merge 前 | 135,085 行 / MATERIAL 53,572 / SKIP 81,513 | `head-merge-rebuild.log` |
| HEAD 副本 merge 后 | **135,088 行 / MATERIAL 53,575 / SKIP 81,513**（+3；MATH +2、PHYSICS +1） | `head-merge-rebuild.log` |
| merge 问题 / 重复键 / 六字段空值 | **0 / 0 / 0** | `head-merge-rebuild.log` |
| v24 设备融合基线 | **117/129 = 0.9069767441860465**；MRR `0.7239018087855298`；24 章 | `build/agent-batch30/render/device-metrics.txt:3-5` |
| v25 前臂设备融合 | **114/129 = 0.8837209302325582**；MRR `0.719250645994832` | `build/agent-batch30/v25/device-metrics.txt` |
| v25 前臂零漂移 | lexical symdiff **0**；fused symdiff **5**（device 15 / JVM 12）→ FAIL | `build/agent-batch30/v25/zero-drift.json` |
| v25 前臂 24 章 | lower **4** / equal **19** / higher **1** | `v25/significance-fused-v25-vs-v24.json` |
| MISS 账本 | **12 条 = 9 条有 rank + 3 条 `absent`** | `build/agent-batch30/render/device-fused-misses.txt` |

---

## 1. 内容（冻结）

### 1.1 三条合格 MATERIAL（A 层定向抽取）

> 来源：`build/agent-batch30/a-layer-work/a-layer-verdicts.csv`（3 行）与 `build/agent-batch30/a-layer-extract.md` §4.1（六字段全文）。三条均为 `METHOD_MODEL`；`midx` 为空；`note` 保留抽取子面与来源。

#### ① `d9379ea17f-009` → `三角函数的图象与性质`

| 字段 | 值 |
| --- | --- |
| 子面 | **S01**（三角函数的图象与性质·应用建模面：用三角函数刻画周期变化/解决实际问题） |
| chunk_rel | `2026年新高考资料/一轮复习/【1】2026高考数学 一轮复习讲义三角函数专题/高考数学  一轮复习讲义 -  函数y＝A sin (ωx＋φ)的图象与性质.docx` |
| chunk_id | `d9379ea17f-009` |
| action | `MATERIAL` |
| node_slug | `三角函数的图象与性质` |
| type | `METHOD_MODEL` |
| title（15 字） | 三角函数模型实际应用的两条路径 |
| summary | 三角函数模型的实际应用分两类：已知函数模型时用性质求解，或把实际问题抽象建模后求解。 |
| applicability | 遇到简谐运动、潮汐、气温变化等周期现象的实际问题时使用。 |
| content | 三角函数模型在实际应用中体现在两个方面：一是已知函数模型，利用三角函数的有关性质解决问题，关键是准确理解自变量的意义以及自变量与函数之间的对应法则；二是把实际问题抽象转化成数学问题，建立三角函数模型，再利用三角函数的有关知识解决问题，关键是建模。 |
| boundary | 两条路径的前提不同：题目已给出函数模型时不能再自行建模，否则会改动题目给定的解析式；自行建模时自变量的实际意义要先界定清楚，不能默认取全体实数。 |
| note | A 层定向抽取 S01（应用建模面）；来源 2026 新高考资料·一轮·三角函数专题 考点3 实际应用 |
| midx | （空） |

#### ② `6475d53e0f-005` → `平面向量的坐标运算`

| 字段 | 值 |
| --- | --- |
| 子面 | **S02**（平面向量的坐标运算·向量方法解决平面几何/力学/实际问题） |
| chunk_rel | `2026年新高考资料/一轮复习/2026年高三数学一轮复习备考题型归纳讲义/资料/第27讲：平面向量的数量积及平面向量的应用（知识梳理+题型总结）（教师版）.docx` |
| chunk_id | `6475d53e0f-005` |
| action | `MATERIAL` |
| node_slug | `平面向量的坐标运算` |
| type | `METHOD_MODEL` |
| title（13 字） | 向量方法解决几何与物理问题 |
| summary | 用向量解决平面几何问题分三步：向量化、向量运算、结果翻译回几何；物理中速度与力都可视为向量。 |
| applicability | 用向量方法处理平面几何中的长度、夹角、全等与相似问题，或分析速度、力的合成问题时使用。 |
| content | 用向量方法解决平面几何问题的“三部曲”：先建立平面几何与向量的联系，用向量表示问题中涉及的几何元素，把平面几何问题转化为向量问题；再通过向量运算研究几何元素之间的关系，如距离、夹角等；最后把运算结果“翻译”成几何关系。物理中速度、力都是向量，都可以转化为向量问题，力的合成与分解符合平行四边形法则。 |
| boundary | 第三步不能省：只算出向量结果而不翻译回几何关系，等于没有回答几何问题；基底或坐标系选得不合适会使运算量激增，物理情境中的向量还要保留方向，不能当作普通数量处理。 |
| note | A 层定向抽取 S02（向量方法解决几何/力学/实际问题）；来源 2026 新高考资料·一轮·第27讲 平面向量的应用 |
| midx | （空） |

#### ③ `e14aad82a5-233` → `必备实验仪器的原理及使用`

| 字段 | 值 |
| --- | --- |
| 子面 | **T03**（薄料节点：必备实验仪器的原理及使用·多用电表/仪器使用） |
| chunk_rel | `2027版高中《53知识清单》彩色版（物理）/2027高中知识清单物理.pdf` |
| chunk_id | `e14aad82a5-233` |
| action | `MATERIAL` |
| node_slug | `必备实验仪器的原理及使用` |
| type | `METHOD_MODEL` |
| title（11 字） | 用多用电表判断电路故障 |
| summary | 用电压挡逐段测量，由示数为零、等于电源电压或小于电源电压判断断路与短路位置；电阻挡必须断电测量。 |
| applicability | 闭合开关后电表无示数、灯泡不亮，需要定位断路或短路位置时使用。 |
| content | 用多用电表电压挡判断电路故障可分三种情况：若电表读数为零，说明与电表并联的路段之外有断路，或该并联路段完全短路；若电表示数等于电源电压，说明与电表并联的电路间有断路，而该并联路段之外的电路无断路；若电表有示数且小于电源电压，说明与电表并联的电路之外无断路，且该并联路段内正常或不完全短路。用电流挡判断时必须先确认电流最大值不超过量程；用电阻挡检测时必须确认电路无电压，并将待测元件与电路断开，若测得电阻无穷大则元件断路（二极管除外）。 |
| boundary | 电阻挡不能带电测量，否则会损坏电表；电压挡判断的前提是电表并联在被测路段两端，串联或量程选错都会得到错误结论；二极管具有单向导电性，不能用“电阻无穷大即断路”一概而论。 |
| note | A 层定向抽取 T03（薄料节点：必备实验仪器的原理及使用）；来源 53 知识清单·物理 第167-168页 多用电表检测故障 |
| midx | （空） |

**六字段口径**：上表 `node_slug / type / title / summary / applicability / content / boundary` 全非空；`head-merge-rebuild.log` 对三条逐条记「六字段空=[]」。`title` 均 ≤24 字、无 LaTeX；`content` 为自包含讲授段，非题干/答案。

### 1.2 证据一：身份与回滚（写前实测）

| 检查 | 实测 | 判定 |
| --- | --- | --- |
| HEAD == origin/main | 两者均 `a0641bb7…` | **PASS ✓** |
| v24 四项身份全同值 | `version=24`、`schemaVersion=2`、`contentVersion=9664896aeba22e86`、`packId=moe-2025-four-subjects-v1`；四项均与 `v24-final.md` §二登记值相同 | **PASS** |
| 配额臂 7 件回滚 | 当前仅 `KnowledgePort.kt` 为 `M`（1/7 残留）；其余 6 件 CLEAN；`DenseRecallAssembly.kt` = `c654d988…`（v24 已采纳字节） | **1/7 残留**（ask 记 2/7；差额是 `DenseRecallAssembly.kt` 已被 `2bb4582a` 提交进 HEAD） |
| v25 release 回滚 | v2-25 = `d132dd68…`（154 条材料，v24 值）；manifest = `2aa5e5e5…`（version 24）；主包 = `96246c78…`；索引 = `2da0b9c5…` | **PASS** |
| v25 判定表回滚 | MATH 数据行 **44,424**（v25 post 44,426，−2）；PHYSICS **38,086**（post 38,087，−1）；三条 `chunk_id` 在两张表中均 **0 命中**；行集合与 `backup-v25-dense/tables/` pre 备份相同 | **PASS（−3 行）** |
| **staging 残留** | `build/kb-staging/moe-2025-teaching-support-v2-25.json` = `a1400dd1…`（**157 条材料，含 3 条新 slug**）；`build/kb-staging/moe-2025-update-manifest.json` = `02f94acf…`（version **25**） | **未回滚（2 件）** |
| `extraction_state.csv` | 10,487 行；与 `backup-v25-dense/tables/extraction_state.csv` **逐行相同**，与 HEAD blob（`git hash-object` = `8652250…`）相同 | **0 行残留**（ask 记 3 行，本次未复现） |
| porcelain | **711**（221 `M` + 490 `??`） | 不干净（见 §0.1） |

**关键更正（按实测登记，不按 ask 字面）**：v25 的 3 条新材料确实还留在 **staging**（v2-25 157 条 + manifest v25），而不是 `extraction_state.csv`。`extraction_state.csv` 与 pre 备份逐行相同，0 行残留；判定表侧的 3 行已回滚（MATH −2 / PHYSICS −1）。执行轮开始前必须先把 staging 复位到 v24（§4.2），否则 `materialize` 会把 3 条当既有料跳过、`promote` 会直接把 v25 staging 推成 release。

### 1.3 证据二：A 层 3 条 MATERIAL 已抽取（HEAD 副本 merge PASS）

```text
对拍前：行 135085；action {'MATERIAL': 53572, 'SKIP': 81513}；唯一键 135085
merge：输入文件 1；输入行 3；合并行 3；问题 0
apply → 副本：新增 3 行，跳过既有键 0 行，表内共 135088 行
对拍后：行 135088；action {'MATERIAL': 53575, 'SKIP': 81513}；唯一键 135088
增量：行 3；MATERIAL 3；SKIP 0
   MATH: MATERIAL 12417 -> 12419 (+2)
   PHYSICS: MATERIAL 15851 -> 15852 (+1)
目标行在副本中命中 3/3
   d9379ea17f-009 [MATH] ... 六字段空=[]
   6475d53e0f-005 [MATH] ... 六字段空=[]
   e14aad82a5-233 [PHYSICS] ... 六字段空=[]
结论： PASS
副本已删除：... head-merge exists=False
```

- 该对拍在 **HEAD `dcfdc5b5` 的临时副本**上完成（`a-layer-extract.md:3,293`），副本用后删除；**未写运行时五片**。
- **当前 HEAD 已变为 `a0641bb7`**，因此执行轮必须在当前 HEAD 上重跑同一 merge 对拍，不能把 dcfdc5b5 的 PASS 直接当本轮通过。

### 1.4 证据三：MISS 账本实为 12 条（9 条有 rank + 3 条 `absent`）

来源：`build/agent-batch30/render/device-fused-misses.txt`（12 个非空行，v24 基线账本；与 `v24-final.md` §一判据 1 的「12 行 MISS ⇒ (129−12)/129」一致）。

| # | 学科 | expectedSlug | 章节 | rank |
| ---: | --- | --- | --- | --- |
| 1 | BIOLOGY | 性状显隐性的判断 | 生物学必修2·第一章 | 7 |
| 2 | CHEMISTRY | 电解原理易错点 | 化学必修第一册·第三章 | 13 |
| 3 | CHEMISTRY | 同分异构现象和同分异构体 | 化学必修第二册·第七章 | 12 |
| 4 | MATH | 利用奇偶性求值… | 数学必修第一册·第三章 | 8 |
| 5 | MATH | 函数图象的翻折变换 | 数学必修第一册·第三章 | 16 |
| 6 | MATH | 方程组法…求解析式 | 数学必修第一册·第三章 | 11 |
| 7 | MATH | 判别式法… | 数学必修第一册·第三章 | 8 |
| 8 | MATH | **等和线** | 数学必修第二册·第六章 | **absent** |
| 9 | MATH | 数列的单调性与最值问题 | 数学选择性必修第二册·第四章 | 8 |
| 10 | PHYSICS | **活结与死结模型** | 物理必修第一册·第三章 | **absent** |
| 11 | PHYSICS | **力的分解** | 物理必修第一册·第三章 | **absent** |
| 12 | PHYSICS | 共点力的动态平衡 | 物理必修第一册·第三章 | 51 |

- **12 = 9 条有数字 rank（7/13/12/8/16/11/8/8/51）+ 3 条 `absent`（等和线、活结与死结模型、力的分解）**。
- 这 3 条 `absent` 是边界单点，按 §5 只报不判；不得以单点翻转作为采纳/回滚依据。

---

## 2. 判读（非劣，写死）

### 2.1 主判读：A 层 3 条 MATERIAL 回放 = 非劣（四条）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**；低于即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与 v24 基线比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读** |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = 本轮回放、B = v24**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

- 基线（v24，转引 + 本轮只读核对）：设备融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**；JVM 融合同为 117/129；`chapters=24`。
- **v25 前臂为什么必须回放**：v25 前臂设备融合 **114/129**（−3 例）、24 章 lower **4** / equal **19** / higher **1**、同路 fused 零漂移 **5**；按本预注册口径已红，故 v25 被回滚。回放不是重读 v25 旧账本，而是从 v24 基线重新落 3 条料、重新出四路账本。
- 零漂移 `NOT_COMPUTABLE`（账本缺失/镜像红）按“不判读”；显著性产物缺失时判据 4 记 `NOT_RUN`，不得写“无显著差异”。
- JVM 只作零漂移/装置保真，不单独判分；设备读数为 emulator，不是真机。

### 2.2 为什么是“非劣”，不是“严格更高”

本批是**内容面定向补料**（3 条 MATERIAL），不是新检索机制。严格更高会把裁定交给单题翻转，等价于面向结果调参；非劣四条覆盖内容更新真正要防的失败：主集回退、跨章回退、装置漂移、显著更差。**不构成先例**：若后续把本批当机制臂重测，须另立预注册并走“严格更高”三规则。

### 2.3 边界单点（只报不判）

| 单点 | 学科 / 章节 | 金标 query | v24 基线（四路） |
|---|---|---|---|
| `等和线` | MATH / 数学必修第二册·第六章 | 在△ABC中 AP=xAB+yAC，x+y=1 时 P 在直线 BC 上，那 x+y=1/2 时 P 跑到哪儿去了？ | device fused / device lexical / JVM fused / JVM lexical 均 `absent` |
| `活结与死结模型` | PHYSICS / 物理必修第一册·第三章 | 一幅画用绳子穿过可在横杆上滑动的小环吊着，两边拉力相等吗？若绳直接打结固定在墙上呢？ | device fused `hit_top5`（v25 前臂）；v24 四路以账本为准 |
| `力的分解` | PHYSICS / 物理必修第一册·第三章 | 一盏灯被两根绳子吊着，每根绳的拉力都比灯的重力大，为什么？重力该怎么分？ | 四路 `absent` |

- 执行轮必须用本轮新包重出四路 hit/名次或 `absent`，**不得**以单点翻转作为采纳/回滚依据，也不得因单点未翻转判本批失败。
- 不得为单点改别名/阈值/参数；确需调整另立预注册。

### 2.4 报告义务

§7 至少报告：设备融合/词面主集与 MRR、24 章逐章 lower/equal/higher 计数、零漂移两路 symdiff、bootstrap 的 delta/CI/p、四路边界的 hit/名次或 `absent`、材料/绑定计数与 sha、双门 exit code。任何一项未跑写 `NOT_RUN` / `UNVERIFIED`，不得留空冒充通过。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 与 v24 预注册 §3（`docs/research/2026-10-31-kb-v24-content-update-prereg.md:245-252`）同口径；若另跑 `testDebugUnitTest`，结果另记，不替代本门。
- **红则不写 release、不跑设备、不判读**：任一门红时停在 §4.1 的 promote 之前；不得先 promote、不得先跑 dense、不得用旧账本代替本批判读。
- 两门日志落执行轮目录（建议 `build/agent-batch31/`；若编号不同，以实际目录为准），§7 引用；不得只写“已跑”。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚

### 4.1 触碰清单 + pre-sha（写前实算）

**A. 判定源与提取台账（tracked；本批直接编辑）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 1 | `tools/kb_coverage/tables/material_judgments/MATH.csv` | +2 行（`d9379ea17f-009`、`6475d53e0f-005`） | `9210553d1bc1aacc8b619b3e0fa1acc9bccdfa7f3c46aa41778b6a0051185a1e`（44,424 数据行） |
| 2 | `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` | +1 行（`e14aad82a5-233`） | `fec3c0b746409b3a83526e24d0fe38c84e85c4aac88232c94f14672f7f43b8ad`（38,086 数据行） |
| 3 | `tools/kb_coverage/tables/extraction_state.csv` | materialize 标记 EXTRACTED + output_ref（整表重写；行数不变） | `43332b59233260e1fd60ca1984bd4fe73f7664a36b00fc46fecc7c201480693d`（10,487 行；git blob `8652250…`） |

**B. 包家族（promote 触碰；release）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 4 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | **预期字节不变**（无节点变更） | `96246c78a1706bfc29e9de9f98e43a927e055e16d44bc4dcd1b94b3b78ca0b4f` |
| 5 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 24→25 + 新 contentVersion | `2aa5e5e5d6ca2d3c426f590d1fcddd63e0241b02be56fc371e4d694fa15cf076` |
| 6 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变，无新卷） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-25.json` | 3 条新材料落 v2-25（154→157 条） | `d132dd684ddda2224704abd93d920a77e2591b77db4e89f61f314bede33937ac` |

**C. staging（gitignored；写前必须先复位）**

| # | 路径 | 当前实测 | 复位目标 |
|---|---|---|---|
| 8 | `build/kb-staging/moe-2025-teaching-support-v2-25.json` | `a1400dd1…`（**157 条，v25 残留**） | `d132dd68…`（154 条，v24） |
| 9 | `build/kb-staging/moe-2025-update-manifest.json` | `02f94acf…`（version **25**） | `2aa5e5e5…`（version 24） |

**D. 只读/零触碰（登记供核验，不得改）**

| # | 路径 | 期望 | pre-sha256 |
|---|---|---|---|
| 10 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改 | `70329115240fe6beafcb88e6fd85d80585fd64f8b22dbe284730f742b0a2a1a5` |
| 11 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 零改 | `5cbbcb6f1b45e1638a4e6bb7f56f5a86a28d4e5a0a70b5efe83aead8f2d2f089` |
| 12 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

- 第 4 项“主包字节不变”的可证性：v25.log §2 已实测 `merge proof: HEAD 051141e2… + aliases -> staging 96246c78… (bytes equal)`，3 条料只加材料不改节点；本轮执行需再证一次。
- 第 10/11 项：v25.log §8 记 dense 资产与 v24 逐字节相同（`.vec` `70329115…`），故回放**不重打 dense**；若执行轮实测 `.vec` 变化，按装置异常登记，不得静默接受。

### 4.2 写前复位与回滚程序

**写前复位（执行轮第 1 步之前，必须先做）**

1. 把 §4.1 C 的 staging v2-25 与 manifest 复位到 v24 值：优先从 `build/agent-batch30/backup-v25-dense/staging/` 覆盖；若无该备份，则从 release 复制 `d132dd68…` / `2aa5e5e5…`。
2. 复核 staging：v2-25 材料 **154**、manifest `version=24` / `contentVersion=9664896aeba22e86`；staging 与 release 逐字节同值（v2-25、manifest、主包、索引）。
3. 重跑 `git status --porcelain -- <§4.1 A/B 写面>`；新增脏项先登记归属，不得把“他线脏”当本批改动。

**回滚（任一判据失败或双门红）**

1. tracked：`git checkout --` 第 1–3 项（两张判定表 + extraction_state）+ `core/data/src/main/resources/knowledge/`（release 全部资产）。
2. staging：用写前备份覆盖（含 v2-25、manifest、主包、索引）；不得只回滚 release 而留下 v25 staging。
3. 核验：manifest `version=24` / `contentVersion=9664896aeba22e86`；主包 `96246c78…`；v2-25 `d132dd68…`（154 条）；材料 **65,016**（MATH 14,493 / PHYSICS 18,500）；`必备实验仪器的原理及使用` 绑定 **2**；`等和线` **33**；三条 `chunk_id` 在 MATH/PHYSICS 判定表 **0 命中**；dense `.vec` `70329115…`、金标 `eabef102…` 不变。
4. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
5. §7 写明回滚原因与逐文件 sha 核验；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 release 包家族、两张判定表、extraction_state 与 staging 同步结果；version=25、新 contentVersion 按实测登记。
2. §7 登记 stateAfter（manifest version/contentVersion、主包/侧车/索引/表 sha、材料/绑定计数）。
3. 非劣口径不得替代机制臂的严格更高；后续复用须另立预注册。

---

## 5. 边界（定向抽取 ≠ 重开封层）

1. **只回放 3 条 MATERIAL，不重开 22 个子面的判定层**。A 层抽取的 22 个子面中，**3 MATERIAL / 15 SKIP / 4 NOT_FOUND** 已在 `a-layer-extract.md` §4 冻结；本回放只把 3 条 MATERIAL 落回运行时五片，**不重判** 15 条 SKIP、**不重找** 4 条 NOT_FOUND、**不翻转** 任何 SKIP→MATERIAL。
2. **不重开 16 条 KEEP PARTIAL**。这 16 条是 2026-10-25 终审的「子面挂既有节点」结论；本回放不把它们整体重开，也不把 A 层抽取当成对 16 条的二次终审。A 层抽取只回答「这 22 个子面里有没有未落料的实质讲授段」，答案是 3 条。
3. **不建点、不改别名、不改阈值/参数**。节点数保持 **3,280**；主包保持 `96246c78…`；别名 40,841 不变；dense `.vec` 不重打；判官与金标零改。若执行轮发现必须建点/改别名才能落料，按「超出本预注册」停止，另立预注册。
4. **边界单点只报不判**。`等和线`、`活结与死结模型`、`力的分解` 三条 `absent` 是检索词汇缺口（dense 名次/词面缺口），不是内容缺口；不得以单点翻转作为本批收益证据，也不得为单点调参。
5. **不把 A 层抽取的 4 条 NOT_FOUND 当本批失败**。`圆锥曲线实际背景`、`电路元器件识别`、`磁现象史实/现代技术`、`麦克斯韦电磁说` 在块池内无讲授段，登记为内容缺口；本回放不补、不判。
6. **不把他线脏项裹挟进回放**。711 条 porcelain 脏项中的渲染 v2、v24 遗留、配额臂残留均不在本批写面；执行轮只动 §4.1 A/B/C 列出的文件。

**一句话边界**：本回放是「把已经抽取合格的 3 条料落回 v25 并做非劣判读」，不是「重开 KB 判定层」，也不是「用 3 条料去解释/覆盖 15 条 SKIP 与 4 条 NOT_FOUND」。

---

## 6. 未做 / 未验证（写作时点）

- **未跑 Gradle**：§3 双门是定义，不是通过声明。
- **未跑设备/JVM/零漂移/bootstrap**：§2 判据是定义；v25 前臂读数只作「为什么回放」的背景，不作本轮读数。
- **未跑 materialize/promote**：3 条料的 HEAD 副本 merge PASS 来自 `dcfdc5b5`，当前 HEAD 为 `a0641bb7`，执行轮必须重跑。
- **staging 未复位**：v2-25/manifest 仍为 v25（§1.2、§4.1 C）；执行轮第 1 步必须先复位，否则本预注册的「写前 v24」前提不成立。
- **`extraction_state.csv` 的 3 行残留未复现**：实测与 pre 备份逐行相同（0 行）；ask 记 3 行，按实测登记，不臆造。
- **未验证 3 条料在包内可检索**：本预注册只冻结「判定行 + 六字段 + 预期计数」；包内检索与四路读数属执行轮 §7。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD；staging 复位核验；§4.1 逐文件写后 sha；命令与 exit code（merge dry/write、materialize dry/write、promote dry/write、双门）；结构计数（材料 65,019；MATH 14,495；PHYSICS 18,501；v2-25 157；`必备实验仪器` 3；`等和线` 33；节点 3,280；未绑/悬空 0）；四路检索读数与 24 章 lower/equal/higher；零漂移两路 symdiff；bootstrap delta/CI/p；三条边界单点四路状态；未跑项一律 `NOT_RUN` / `UNVERIFIED`。
