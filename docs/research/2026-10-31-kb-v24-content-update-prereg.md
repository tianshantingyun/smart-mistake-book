# KB v24 内容更新（错挂修正 + 薄料补料 + 等和线词汇）：预注册（2026-10-31）

> **性质**：出数前冻结。§1–§5 写死，执行轮只允许在文末追加 §7；不得回改本文 §1–§5。
> **写作时点**：2026-10-31；HEAD `5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（`git rev-parse HEAD` 实跑）。
> **上游**：`build/agent-batch30/baseline/identity.md` §4（`:85-177`，错挂/薄料/等和线写法空间）、`docs/research/2026-10-26-kb-usage-surface-audit.md` R2（`:83-109`）、`docs/research/2026-10-30-kb-v23-content-update-prereg.md` §2–§3（`:143-198`，非劣与双门口径）。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/判官、未写 staging。

---

## 0. 写前守卫与数字口径

### 0.1 porcelain 守卫（写前实跑）

```bash
git status --porcelain | wc -l                                  # 708
git status --porcelain | awk '{print $1}' | sort | uniq -c      # 484 ?? / 224 M
git rev-parse HEAD                                             # 5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa
git status --porcelain -- <§4.1 的写面>                          # 空（全部 CLEAN）
ls docs/research/2026-10-31-kb-v24-content-update-prereg.md      # No such file
```

- **目标文件不存在 → 本次是新建，只写一次**；执行轮结果按“只追加”写 §7。
- 写面 4 张表 + `core/data/src/main/resources/knowledge/` 全部 CLEAN；708 条脏项属他线（含批次 30 已采纳的渲染 v2 保留改动），本批不触碰、不裹挟、不回滚。

### 0.2 数字口径（本会话实算）

| 事实 | 值 | 方式 |
|---|---:|---|
| v23 manifest | `version=23`、`schemaVersion=2`、`contentVersion=868a177d1de4f97a` | 读 `moe-2025-update-manifest.json` |
| 材料总数 | **65,014**（MATH 14,492 / PHYSICS 18,499 / CHEMISTRY 18,315 / BIOLOGY 13,708） | 遍历 25 sidecar `materials[]` |
| 节点数 | **3,280** | 遍历主包 `knowledgePoints[]` |
| `等和线` 绑定材料 | **32** 条（全 PRIMARY） | 遍历 sidecar `bindings[]` |
| `必备实验仪器的原理及使用` 绑定材料 | **1** 条（`phy-basic-instruments-principle-use`，416 字） | 同上 |
| `多用电表的结构与刻度盘` 绑定材料 | **13** 条 | 同上 |
| `原子结构与波粒二象性` 绑定材料 | **4** 条（含本条错挂） | 同上 |
| `phys-hj3-multimeter-structure` 当前绑定 | `…:physics:atomic:原子结构与波粒二象性`（PRIMARY） | 读 `moe-2025-teaching-support-v2-02.json` |
| materialize 现状（只读 dry-run） | 判定 **135,085** 行：材料 **53,572**、跳过 **81,513**、错误 **0** | `python -I -c "... materialize.main(['--dry-run'])"`，exit 0 |
| 新增两行后的计划（内存模拟） | 材料 **53,574**、错误 **0** | `materialize.plan(rows + 2 new)` |
| dense `.vec` | `bbcae9ec…bb527b`（26,085,873 B） | `sha256sum` |
| 金标 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`（129 条 / 24 章） | `sha256sum tools/kb_coverage/tables/golden_queries_v2.json` |

---

## 1. 内容（冻结）

### 1.1 ① 错挂修正：`phys-hj3-multimeter-structure`

| 项 | 值 |
|---|---|
| 材料 | `phys-hj3-multimeter-structure`；title「多用电表的结构与原理」；`CONCEPT_EXPLANATION` |
| 现绑定 | `kb:moe-2025-four-subjects-v1:physics:atomic:原子结构与波粒二象性`（PRIMARY） |
| 目标节点 | `kb:moe-2025-four-subjects-v1:physics:atomic:多用电表的结构与刻度盘`（存在；PHYSICS / CONCEPT；现有 13 条材料） |
| 证据（正文三段） | ①表盘三行刻度（上行电阻、左 ∞ 右 0、不均匀；中行直流电流/电压/交流电压；下行交流 2.5 V 专用）；②直流电流挡并联/混联电阻改装、直流电压挡串联电阻改装、欧姆挡接不同电源与电阻；③红表笔插「＋」、黑表笔插「－」，测电阻前欧姆调零。**与原子结构/黑体辐射无关。** |
| 写入方式 | 在 `tools/kb_build/tables/material_rebind.csv` **追加 1 行**，再跑 `python -m kb_build.rebind_materials --write`（`rebind_materials.py:50-86` 只改 binding 目标；`:89-121` 无损校验） |
| 冻结行（CSV 四列） | `phys-hj3-multimeter-structure,原子结构与波粒二象性,多用电表的结构与刻度盘,正文三段讲表盘/改装/红黑表笔；与原子结构无关` |

- 内存模拟实跑：`rebind(pack, sidecars, [row])` → `{'rebound': 1, 'skipped': 0, 'bad_to': []}`；改后 binding 为 `…:多用电表的结构与刻度盘`。
- 不变量：材料总数 **65,014 不变**；`原子结构` 4→**3**、`多用电表的结构与刻度盘` 13→**14**；无悬空绑定。
- **不改** sidecar 正文/标题/slug；**不改**判定表（`PHYSICS.csv` 按标题 0 命中，错挂不在判定行上；identity `:107`）。

### 1.2 ② 薄料补料：「多用电表要点」

**现状**：节点 `必备实验仪器的原理及使用`（PHYSICS / EXPERIMENT）只有 1 条 416 字综述（打点计时器 / 游标卡尺 / 螺旋测微器 / 多用电表四段）；9 条判定 MATERIAL 全被更细的兄弟节点吸收（identity `:111-120`）。本批为该聚合节点补 1 条独立的多用电表材料。

**判定行（冻结；追加到 `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` 末尾）**

| 列 | 值 |
|---|---|
| `chunk_rel` | 与 `PHYSICS.csv:3243` 的 `chunk_rel` **逐字相同**（第36讲，安徽专用） |
| `chunk_id` | `d72ac5caec-007` |
| `action` | `MATERIAL` |
| `node_slug` | `必备实验仪器的原理及使用` |
| `type` | `CONCEPT_EXPLANATION` |
| `title` | `多用电表要点`（6 字） |
| `summary` | 表盘三行刻度、红黑表笔插孔、机械调零与欧姆调零的分工，以及电流/电压/电阻测量端的量程对应。 |
| `applicability` | 复习多用电表的结构与使用要点时。 |
| `content` | 表盘有三行刻度：最上行测电阻（左 ∞ 右 0，不均匀，从右向左越来越密），中间行测直流电流、直流电压和交变电压（均匀），最下行是交流 2.5 V 专用（不均匀）；选择开关周围标出测量功能与量程；红表笔插「＋」插孔、黑表笔插「－」插孔；机械调零用指针定位螺丝，使不接入电路时的指针指在左端 0 刻线，欧姆调零在测电阻前进行，两者不能混用；内部由小量程电流表与若干元件组成，电流测量端接 1 为大量程，电压测量端接 6 为大量程，电阻测量端接 4 为大倍率。 |
| `boundary` | 机械零点在左端 0、欧姆零点在右端 0；测电阻前必须欧姆调零，换挡后重新调零。 |
| `note` | v24 薄料补料：必备实验仪器的原理及使用（多用电表要点） |
| `midx` | `b` |

- 内容全部出自 `d72ac5caec-007` 的块文本（表盘三行 / 选择开关 / 插孔与两个调零旋钮 / 内部构造与量程端点）；不引入块外事实。
- materialize 落盘结果（冻结预期）：slug **`ext-phy-d72ac5caec-007b`**；`derivationKind=REVIEWED_SYNTHESIS`；F8 三键 `contentAttribute=AI_SYNTHESIZED`、`contentProvider=moe-2025-four-subjects-v1`、`contentId=ext-phy-d72ac5caec-007b`（`materialize.py:286-301`、`material_provenance.py:81-91`）；绑定 `…:physics:atomic:必备实验仪器的原理及使用`（PRIMARY）。
- **`AI_SYNTHESIZED` 指 `contentAttribute`，不是 `derivationKind`**；后者由 materialize 固定为 `REVIEWED_SYNTHESIS`，不得改写。
- 预期：`必备实验仪器` 1→**2**；PHYSICS 18,499→**18,500**；总数 65,014→**65,015**。

### 1.3 ③ 等和线词汇：「等和线的几何意义与平行关系」

**现状**：节点 `等和线`（MATH / PROCEDURE）现有 **32** 条材料；金标查询「在△ABC中 AP=xAB+yAC，x+y=1 时 P 在直线 BC 上，那 x+y=1/2 时 P 跑到哪儿去了？」四路均 `rank=absent`（`build/agent-batch30/render/{device,jvm}-{fused,lexical}-misses.txt:8/13`）。本批补 1 条**含 ASCII `x+y`、无 LaTeX** 的材料，服务词面检索，不承诺单点翻转。

**候选与约束（实算）**

- 候选取自 `build/agent-batch30/baseline/identity.md:166`（“等和线的几何意义与平行关系 / 几何直观（现有无）”）。
- 该 title 实际长度 **13** 字（identity 表记 11，系表内计数笔误；13 ≤ 24，判据不受影响）；无 LaTeX。
- 与现有 32 条 `norm_key`（NFKC + 去空白）不碰撞；内容也不碰撞（本会话实算）。

**判定行（冻结；追加到 `tools/kb_coverage/tables/material_judgments/MATH.csv` 末尾）**

| 列 | 值 |
|---|---|
| `chunk_rel` | 与 `MATH.csv:1252` 的 `chunk_rel` **逐字相同**（27版五三·专题五 平面向量与复数） |
| `chunk_id` | `e99f917373-005` |
| `action` | `MATERIAL` |
| `node_slug` | `等和线` |
| `type` | `CONCEPT_EXPLANATION` |
| `title` | `等和线的几何意义与平行关系`（13 字） |
| `summary` | x+y 为定值时点 P 在一条与 BC 平行的直线上，这条直线就是等和线。 |
| `applicability` | 理解等和线的几何意义、判断 x+y 改变时点 P 所在直线的平移方向时使用。 |
| `content` | 设 AP=xAB+yAC，其中 x、y 是实数。x+y=1 时点 P 在直线 BC 上；x+y=k（k 为定值）时点 P 在一条与 BC 平行的直线上，这条直线就是等和线。x+y 的值改变，等和线沿着从 A 到 BC 的方向平移：x+y=1/2 在 A 与 BC 之间，x+y=2 在 BC 的另一侧。求系数和 x+y 的范围，就是看 P 所在直线平移到哪两条等和线之间。 |
| `boundary` | 必须先把 AP 写成同一组不共线基底 AB、AC 的线性组合才能谈系数和；等和线要与 BC 平行。 |
| `note` | v24 等和线词汇：ASCII x+y 与平行线几何意义 |
| `midx` | `b` |

- 冻结校验（本会话实算）：`title` 13 字；`title+content` 无 `$` / `\` / `^` / `_`；`content` 含 ASCII `x+y`（**6 处**）；`norm_key` 与 32 条不碰撞。
- materialize 落盘结果（冻结预期）：slug **`ext-mat-e99f917373-005b`**；三键 `AI_SYNTHESIZED`；绑定 `…:math:atomic:等和线`（PRIMARY）。
- 预期：`等和线` 32→**33**；MATH 14,492→**14,493**；总数 65,015→**65,016**。

### 1.4 执行顺序与写面边界（冻结）

```text
1) 追加 3 行：PHYSICS.csv(+1)、MATH.csv(+1)、material_rebind.csv(+1)   [tracked]
2) PYTHONPATH=tools python -m kb_coverage.materialize --dry-run   → 53,574 材料 / 0 错误
3) PYTHONPATH=tools python -m kb_coverage.materialize --write     → staging 选卷（预期 v2-25）
4) PYTHONPATH=tools python -m kb_build.rebind_materials           → rebound=1 / bad_to=0
5) PYTHONPATH=tools python -m kb_build.rebind_materials --write   → staging 全部 sidecar 重写（仅 v2-02 字节变化）
6) PYTHONPATH=tools python -m kb_build.promote --dry-run          → 23 门 + 契约镜像 + roundtrip + manifest 全绿
7) PYTHONPATH=tools python -m kb_build.promote                    → release 写盘；version 23→24
8) §3 双门 → 设备金标 / 零漂移 / bootstrap（§2）
```

- **零改**：节点表/主包结构、别名表、`new_points_manual.csv`、dense `.vec`、金标、判官、Kotlin 代码、schema。
- 选卷是确定性的：materialize `_pick_sidecar`（`materialize.py:208-228`）当前只有 `v2-25` 未超 2.5M 上限（实算 `usable=['moe-2025-teaching-support-v2-25.json']`），故两条新材料预计都落 v2-25。
- 追加行必须保持 12 列、原表头、原行尾（CRLF）；不得整表重写制造十万行 diff。
- **新材料 `content` 一律不写 `\n`**：判定表按字面落盘时 `\n` 会成为两个字符（现有 `ext-*` 材料即此形态，`ext-mat-e99f917373-005` 实测 `literal_bsn=9`、`real_nl=0`），渲染节选会把反斜杠 n 当正文；本批用「；」分句规避，不引入新的文本残迹。

---

## 2. 判读（非劣，写死）

### 2.1 主判读：v24 内容更新 = 非劣（四条）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**；低于即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与批次 30 渲染 v2 基线比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读** |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v24、B = 批次 30 渲染 v2**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

- 基线（转引，执行轮复跑）：设备融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**（`build/agent-batch30/render/device-metrics.txt:3-5`）；JVM 融合同为 117/129（`jvm-fused-metrics.txt:7-8`）；24 章 = `chapters=24`（`jvm-fused-metrics.txt:4`）。
- 预算：`p50Budget=150ms` / `p95Budget=250ms`；`overBudgetSamples` 只作装置健康观测，不单独作为采纳条件。
- 零漂移 `NOT_COMPUTABLE`（账本缺失/镜像红）按“不判读”；显著性产物缺失时判据 4 记 `NOT_RUN`，不得写“无显著差异”。
- JVM 只作零漂移/装置保真，不单独判分；设备读数为 emulator，不是真机。

### 2.2 为什么是“非劣”，不是“严格更高”

本批是**内容面修正**（1 条错挂改绑 + 2 条材料补齐），不是新检索机制。严格更高会把裁定交给单题翻转，等价于面向结果调参；非劣四条覆盖内容更新真正要防的失败：主集回退、跨章回退、装置漂移、显著更差。**不构成先例**：若后续把本批当机制臂重测，须另立预注册并走“严格更高”三规则。

### 2.3 等和线单点（只报不判）

| 单点 | 学科 / 章节 | 金标 query | 批次 30 基线（四路） |
|---|---|---|---|
| `等和线` | MATH / 数学必修第二册·第六章·平面向量 | 在△ABC中 AP=xAB+yAC，x+y=1 时 P 在直线 BC 上，那 x+y=1/2 时 P 跑到哪儿去了？ | device fused / device lexical / JVM fused / JVM lexical 均 `rank=absent`（`build/agent-batch30/render/device-fused-misses.txt:8`、`device-lexical-misses.txt:13`、`jvm-fused-misses.txt:8`、`jvm-lexical-misses.txt:13`） |

- 执行轮必须用 v24 新包重出四路 hit/名次或 `absent`，**不得**以单点翻转作为采纳/回滚依据，也不得因单点未翻转判本批失败。
- 不得为单点改别名/阈值/参数；确需调整另立预注册。

### 2.4 报告义务

§7 至少报告：设备融合/词面主集与 MRR、24 章逐章 lower/equal/higher 计数、零漂移两路 symdiff、bootstrap 的 delta/CI/p、四路等和线单点、材料/绑定计数与 sha、双门 exit code。任何一项未跑写 `NOT_RUN` / `UNVERIFIED`，不得留空冒充通过。

---

## 3. 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---|---|---|
| 1 | `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 与 v23 预注册 §3（`docs/research/2026-10-30-kb-v23-content-update-prereg.md:184-198`）同口径；若另跑 `testDebugUnitTest`，结果另记，不替代本门。
- **红则不写 release、不跑设备、不判读**：任一门红时停在 §1.4 第 7 步之前；不得先 promote、不得先跑 dense、不得用旧账本代替本批判读。
- 两门日志落 `build/agent-batch31/`（执行轮目录；若编号不同，以实际目录为准），§7 引用；不得只写“已跑”。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 回滚

### 4.1 触碰清单 + pre-sha（写前实算，均 CLEAN）

**A. 判定源与改绑表（tracked；本批直接编辑）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 1 | `tools/kb_coverage/tables/material_judgments/PHYSICS.csv` | +1 行（多用电表要点，midx=b） | `861ecd5b6c6fa1a7918324a97f10314c2c39132d15a6b76d9b686dc0475c6f79` |
| 2 | `tools/kb_coverage/tables/material_judgments/MATH.csv` | +1 行（等和线词汇，midx=b） | `96d313636d1e712893e7d588fad6578c7b352992e71ae6e389ff99172921f662` |
| 3 | `tools/kb_build/tables/material_rebind.csv` | +1 行（多用电表改绑） | `9985d624d2cc302f4a015e3f491d864966740ca8aeb8ad8a3760b7c0c4d8ad1d` |
| 4 | `tools/kb_coverage/tables/extraction_state.csv` | materialize 标记 EXTRACTED + output_ref（整表重写） | `c2a09d75e4879710695f9b2aff68e4825880ef911c8b5a2d6606a3e785d85a19` |

**B. 包家族（promote 触碰；release）**

| # | 路径 | 预期动作 | pre-sha256 |
|---|---|---|---|
| 5 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | **预期字节不变**（无节点变更） | `051141e2a05cecc821931bba139cd919b3a205cabfb99439bebc0be0867b1be7` |
| 6 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 23→24 + 新 contentVersion | `14fe7b3af9c9fef2c48a629bb836d1d3715316dfd62b5ff9fc791340057308a7` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变，无新卷） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 8 | `moe-2025-teaching-support-v2-01…25.json`（25 个） | ② 新材料落 v2-25；① 改 v2-02；promote 重写全部 | 聚合 `1b9d2d80e04e5ab28f23c215cb01aeb5f177ab30959287d18a205d780e369496`；逐文件见 §4.2 |

- 第 5 项“主包字节不变”本会话已有可证性：`load_json(pack)` 后 `serialize()` 与当前文件字节相等（`data == ser` True，sha 同为 `051141e2…`）；无节点变更时 promote 重写不会改变主包字节。

**C. 只读/零触碰（登记供核验，不得改）**

| # | 路径 | 期望 | pre-sha256 |
|---|---|---|---|
| 9 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 零改 | `bbcae9ecaedc5d6da868447e793b3ef7b6f16070478288aaba59626443bb527b` |
| 10 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 零改 | `77ac8c92f89ed0ca703179bbc5522bebe78b47c617b3959333185115eea2a737` |
| 11 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |

### 4.2 25 sidecar pre-sha（逐文件，写前实算）

> 聚合构造与 v23 §5.5 相同：按 `v2-01…v2-25` 顺序以 `\n` 连接 `"<basename> <sha256>"`（末尾无换行）后再 sha256；当前聚合 = `1b9d2d80…`。

| # | 文件 | sha256 |
|---|---|---|
| 01 | `moe-2025-teaching-support-v2-01.json` | `feddca192e10c26bbc1b1663c6681095bfe7196f2073a5574c8c98a3d73661f3` |
| 02 | `moe-2025-teaching-support-v2-02.json` | `45fdc91376edc24741d19224165ee5f78d258f7a45111045f67021b73a14ef21` |
| 03 | `moe-2025-teaching-support-v2-03.json` | `23e08d3a63c8ab4a17216cb1ea3e856a01c33a95f15b05eb02ecfa88d540a7ad` |
| 04 | `moe-2025-teaching-support-v2-04.json` | `fa7378a77683f684e62075e630315d74c46ff06dda77151e6408ee92432adb71` |
| 05 | `moe-2025-teaching-support-v2-05.json` | `5d2fc2623a5efc0f8849fc99d3cea967c813263e9ee879a54f6dbf776fe96fb8` |
| 06 | `moe-2025-teaching-support-v2-06.json` | `83169541da5228889bd418093adf2e078a86acd77c543e359dbb00cf4220860e` |
| 07 | `moe-2025-teaching-support-v2-07.json` | `b028f97be1a581008636160a63ba1f4fd0040ccec2befae65c19f3a7261176ae` |
| 08 | `moe-2025-teaching-support-v2-08.json` | `0a18acc8ed221db21af277fc6b70b1a902b47d3284254e78a812c490a0d776fe` |
| 09 | `moe-2025-teaching-support-v2-09.json` | `6d430b34b379aaab201a2038a89d9006807dc033582b347019a7cb496c124cf1` |
| 10 | `moe-2025-teaching-support-v2-10.json` | `dbe1bef32e875b2366880afae7ef6ce96e06dafca95f2f77a9576be82a19d748` |
| 11 | `moe-2025-teaching-support-v2-11.json` | `de218526103a840b595b03071e9d4196798c9e490f147b123b3beb9ae9c98da1` |
| 12 | `moe-2025-teaching-support-v2-12.json` | `12dc6a55f835232c42369149b9c921b5714eb18e981d0eb9f439d4c5d00ff95f` |
| 13 | `moe-2025-teaching-support-v2-13.json` | `ce38b4d4cf75cfde7a22230f16c37c967eec6397f68e0284ae405ed39ddc5353` |
| 14 | `moe-2025-teaching-support-v2-14.json` | `1d790d3cc8068d7ed5a36babcab6f1e8209d71706821e685cf0abdd59c895c4f` |
| 15 | `moe-2025-teaching-support-v2-15.json` | `e30a257f08ac94ac8721bda66f40d10ebac10ed0d5780106360c891d04ad43af` |
| 16 | `moe-2025-teaching-support-v2-16.json` | `7612aa625a6097b7b655eab4cae4329762b2ac6125498e96fdd211d0ae7aa86b` |
| 17 | `moe-2025-teaching-support-v2-17.json` | `98a76e355c167a5b9758c09cac8010adb2504fb835e0767cf09b25b71f5246c8` |
| 18 | `moe-2025-teaching-support-v2-18.json` | `8ae8b5183dd083f4a047b0abe96f1b72bfb73852c63eb3f00ddf84b0adfcc897` |
| 19 | `moe-2025-teaching-support-v2-19.json` | `3224da13ff1336eef6dc10bc85c71ae628e76f117b3de557733d2aacf98faff8` |
| 20 | `moe-2025-teaching-support-v2-20.json` | `c0064dead7ef985416051f383ca131f63a24dfb89776ef3dff01f95185c0e2f4` |
| 21 | `moe-2025-teaching-support-v2-21.json` | `dd9607445f2b699181e57b41ab055e456b77509a4f5f555d9f42966525e859f0` |
| 22 | `moe-2025-teaching-support-v2-22.json` | `88b393fa3a37f9430b7b3459798d3f78de28d53d2a3811fb785dfc72752caf37` |
| 23 | `moe-2025-teaching-support-v2-23.json` | `bfe6ddb100c89ab743f5d61a053dbe06247a8db0669e7cae6f8e497502f16703` |
| 24 | `moe-2025-teaching-support-v2-24.json` | `d5c685414c7110a7bf22161663217dab7d38bb37eeb017882d23dea844f2e064` |
| 25 | `moe-2025-teaching-support-v2-25.json` | `26b93acfdd28af14cb2138c4813234065ae39fff6f7f74ab6242109fe16c290b` |

- staging 当前与 release 逐字节同值（`build/kb-staging/` 的 v2-02 / v2-25 / 主包 / manifest 与上表同 sha，本会话 `sha256sum` 实跑）；staging 是 gitignored 工作区，回滚用写前备份覆盖，不用 `git checkout`。

### 4.3 写前备份与回滚 / 采纳程序

**写前备份（执行轮第 1 步之前）**

1. 把 §4.1 A（4 张表）与 §4.2 的 25 个 staging sidecar、staging 主包/manifest/index 复制到 `build/agent-batch31/backup-v24/`，落 `sha256sum` 全表。
2. 重跑 `git status --porcelain -- <§4.1 写面>`；新增脏项先登记归属，不得把“他线脏”当本批改动。

**回滚（任一判据失败或双门红）**

1. tracked：`git checkout --` 4 张表 + `core/data/src/main/resources/knowledge/`（release 全部资产）。
2. staging：用 `backup-v24/` 覆盖（含 25 sidecar、主包、manifest、index）。
3. 核验：manifest `version=23` / `contentVersion=868a177d1de4f97a`；主包 `051141e2…`；25 sidecar 聚合 `1b9d2d80…`；材料 65,014；`等和线` 32、`必备实验仪器` 1、`多用电表的结构与刻度盘` 13、`原子结构` 4；`phys-hj3-multimeter-structure` 绑回 `原子结构与波粒二象性`；dense `.vec` `bbcae9ec…`、金标 `eabef102…` 不变。
4. 复跑 §3 双门；回滚后仍红则登记为他线/基线问题，不归因本批。
5. §7 写明回滚原因与逐文件 sha 核验；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 release 包家族、4 张表与 staging 同步结果；version=24、新 contentVersion 按实测登记。
2. §7 登记 stateAfter（manifest version/contentVersion、主包/侧车/索引/表 sha、材料/绑定计数）。
3. 非劣口径不得替代机制臂的严格更高；后续复用须另立预注册。

---

## 5. 预期（内容面修正，不承诺分数）

| 指标 | 写前 | 写后（预期） | 性质 |
|---|---:|---:|---|
| 材料总数 | 65,014 | **65,016（+2）** | 结构性 |
| PHYSICS 材料 | 18,499 | **18,500（+1）** | 结构性 |
| MATH 材料 | 14,492 | **14,493（+1）** | 结构性 |
| `必备实验仪器的原理及使用` 绑定 | 1 | **2** | 结构性 |
| `等和线` 绑定 | 32 | **33** | 结构性 |
| `多用电表的结构与刻度盘` 绑定 | 13 | **14** | 结构性 |
| `原子结构与波粒二象性` 绑定 | 4 | **3** | 结构性 |
| 节点数 | 3,280 | **3,280（不变）** | 零改 |
| 主包 sha | `051141e2…` | **预期不变** | 零改 |
| dense `.vec` sha | `bbcae9ec…` | **预期不变**（不重打） | 零改 |
| 金标 sha | `eabef102…` | **不变** | 零改 |
| manifest version | 23 | **24** | 版本 |
| contentVersion | `868a177d1de4f97a` | **执行轮实测** | 版本 |

- **不承诺分数**：本批是错挂修正与内容补齐；主集/24 章/零漂移/负向守卫只用于证明“没有变差”，不主张涨分，也不把等和线单点翻转当作收益证据。
- **已知风险**：新增材料会改变词面索引（别名/标题/正文进入检索面），可能让个别查询名次上升或下降；非劣四条是唯一采纳口径，单点波动不单独裁定。
- **内容重复风险**：改绑后的 `phys-hj3-multimeter-structure` 与目标节点已有 `ext-phy-d72ac5caec-007` 同源但不同文；本批不做语义去重，执行轮若 `gate.py` 的 `judgment_pack_drift` / 控制字符 / LaTeX 残迹门判红则按 §4 回滚。
- **薄料边界**：新增材料只补多用电表要点，不把 9 条兄弟节点材料上移，也不改 `boundary` 截断缺陷（identity `:115` 的 28 字截断是独立项）。

---

## 附录 A：执行轮 §7 必报字段（只追加，不改变 §1–§5 判据）

1. 写前/写后 porcelain 与 HEAD；§4.1/§4.2 逐文件写后 sha。
2. 命令与 exit code：`materialize --dry-run`（材料数/错误数）、`materialize --write`（落卷与新增 slug）、`rebind_materials`（rebound/skipped/bad_to）、`promote --dry-run`（23 门/契约/roundtrip/manifest）、`promote`（version/contentVersion/packSha）、§3 双门。
3. 结构计数：材料 65,016；PHYSICS 18,500；MATH 14,493；`等和线` 33；`必备实验仪器` 2；`多用电表的结构与刻度盘` 14；`原子结构` 3；未绑材料 0。
4. 检索判读：设备融合/词面主集与 MRR；24 章逐章 lower/equal/higher；零漂移 lexical/fused symdiff；bootstrap delta/CI/p。
5. 等和线单点四路（device fused / device lexical / JVM fused / JVM lexical）hit/名次或 `absent`，只报不判。
6. 未跑项一律 `NOT_RUN` / `UNVERIFIED`，不得写成 PASS。
