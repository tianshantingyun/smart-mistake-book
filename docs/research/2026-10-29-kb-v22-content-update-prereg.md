# KB v22 内容更新：R6 换行形态修复 + S3 记号别名 · 预注册（2026-10-29）

> 出数前冻结；事后 §1–§6 一字不动，只填 §7。依据：`build/agent-batch25/r6-items.json`（24,275 B，sha256 `cbfdfb48d30b5003425e1b0be410c8d63271991d216d9255b9bce6cd13898b2f`）、`docs/research/2026-10-26-kb-content-derived-alias-rules.md`（`body-alias-v1/S3@1`，rule_hash `938e2026c93f0aa6f60e8bb01c6d2079decf1e51bdb0c56b5f97bb462617ae10`）、`build/agent-batch25/{gen_s3_aliases,dryrun_s3,validate_s3_candidates}.out.txt`、`build/agent-batch24/r6-assessment.md`、`build/agent-batch25/baseline/identity.md`、批次19 A1 账本。本预注册覆盖 **R6 29 条文本形态修复** 与 **S3 262 条别名写入**；薄料轨 **blocked，零写**。

---

## 1. 更新内容（冻结）

### 1.1 R6 数据形态修复（29 条，写入）

R6 的唯一产出物是 `build/agent-batch25/r6-items.json`（24,275 B，sha256 `cbfdfb48…`）。生成方式：Python inline heredoc 扫描 25 个 teaching sidecar + 主包 `moe-2025-four-subjects-v1.json`；只读 `materials[].summaryMarkdown`、`materials[].boundaryMarkdown`、`topics[].knowledgePoints[].boundary`，不读金标、misses、用户日志。

**实测计数（本代理直接扫描复算，2026-10-30）**

| 项 | 值 | 说明 |
|---|---:|---|
| sidecars | 25 | `moe-2025-teaching-support-v2-01…25.json` |
| nodes | 3,245 | 主包原子节点 |
| materials | 64,973 | 25 sidecar 合计 |
| `summaryNewline` | **13** | 全部位于 `moe-2025-teaching-support-v2-01.json`；全部为 `\ne` 转义（JSON 把 LaTeX `\ne` 解析成“真实换行 + `e`”） |
| `summaryNewlineWithin60` | **12** | 首换行位置 < 60 |
| `nodeBoundaryNewlineTotal` | **14** | 主包节点 `boundary` 含真实换行 |
| `nodeBoundaryNewlineWithin80` | **6** | 首换行位置 < 80；全部 PHYSICS 结构换行 |
| `materialBoundaryNewline` | **10** | 相邻发现：材料 `boundaryMarkdown` 含换行；9 条 `\ne` + 1 条 PHYSICS 结构换行；全部 < 80 |
| `materialBoundaryNewlineWithin80` | **10** | 同上 |

**口径对齐**：R6 主类命中 = `13 summary + 6 node boundary = 19 条`（本 ask 的“19 条”即此和）；`materialBoundaryNewline` 是批次24 评估时相邻发现的 **另 10 条**，不在原 13+6 内，但已进入 `r6-items.json`，本批一并修。`nodeBoundaryNewlineTotal=14` 是扫描总数，写面只取 `r6-items.json` 列出的 **within80 的 6 条**。

**冻结清单（逐条）**

| 类 | 条数 | 文件 | slug |
|---|---:|---|---|
| `summaryMarkdown` | 13 | `v2-01` | `math-hj-dengbi-sn-fenduan-taolun`, `math-hj-zhouqi-dingyi`, `math-hj-shuangqu-dingyi`, `math-fx-zhengqie-xingzhi`, `math-fx-dengcha-hanshu-shuxing`, `math-fx-dengbi-dingyi-zhongxiang`, `math-fx-dengbi-qiuhc-fenlei`, `math-fx-dengbi-sn-xingzhi`, `math-fx-fushu-gainian-fenlei`, `math-fx-zhouqi-dingyi-qiuzhi`, `math-fx-jiben-chudeng-zhiyu-qingdan`, `math-fx-zhouqi-fuhe-gonggong`, `math-fx-sincos-dingyv-zhiyv-duizhao` |
| node `boundary`（within80） | 6 | 主包 | `功能关系和能量守恒定律`, `光的反射和折射`, `光的干涉-衍射和偏振`, `电源的内阻-电源内部是由导体构成-所以也有电阻-这个电阻叫做电源的内阻`, `电路中的基本定律`, `电磁感应` |
| material `boundaryMarkdown` | 10 | `v2-01`（9）/ `v2-02`（1） | `math-hj-dengbi-shulie-dingyi`, `math-fx-dengbi-sn-xingzhi`, `math-fx-ditui-moxing-sanlei`, `math-fx-hancan-zhixian-guodongdian`, `math-fx-erci-hanshu-fangcheng-lianxi`, `math-fx-jiexishi-sifang-qiu`, `math-fx-duigou-jianjin-jizhi`, `math-fx-dengbi-xiaoyuan-jicheng`, `math-fx-dengbi-panding-sifa`, `phys-hj3-three-rules-causality` |

**修复规则（写死；只改形态，不改语义）**

1. `\ne` 转义（summary 13 条 + material 9 条）：把“真实换行 + `e`”恢复为 LaTeX `\ne`，即把 U+000A 替换为两个字符 `\` + `n`；不增删其余字符。
2. 结构换行（node 6 条 + material 1 条）：把真实换行替换为**空格或分号**；**不得直接删除换行把两段拼接**（删除会合并词片段，可能改变词面特征；批次24 `r6-assessment.md` §0 已把“删除”列为不推荐写法）。
3. 不做任何其他文本清洗；不碰 `contentMarkdown`、标题、绑定、别名（别名只走 §1.2）。

**依据**：`build/agent-batch24/r6-assessment.md` §0/§1.2（计数复现：node 14/within80 6、summary 13/within60 12、material boundary 10/within80 10）；本代理 2026-10-30 直接扫描主包与 25 sidecar 复算同值。

### 1.2 S3 记号别名轨（262 条，写入）

| 项 | 值 | 证据 |
|---|---|---|
| 候选表 | `knowledge-production/alias-exceptions-candidate-2026-10-29.csv`，**262 数据行**，sha256 `58d2f146123b84af2c92e95d3033fea1d75fe655391369154b5eb553401c4aa5` | 本代理 `wc`/`csv` 实读 |
| 规则 | `body-alias-v1/S3@1`，rule_hash `938e2026c93f0aa6f60e8bb01c6d2079decf1e51bdb0c56b5f97bb462617ae10` | `build/agent-batch25/gen_s3_aliases.out.txt:3` |
| 输入包 | staging `build/kb-staging/moe-2025-four-subjects-v1.json`，`version=21`、`contentVersion=9f9e99ceec47cf8c` | 同上 `:2` |
| 候选规模 | **262 rows / 174 nodes**；阈值 **1,500**（本 ask 给定）→ **PASS** | `gen_s3_aliases.out.txt:6`；`validate_s3.out.txt` |
| dry-run 冲突 | **0**（阈值 0）→ **PASS**；`exceptions_applied=297`、`exception_conflicts=0`、`exceptions_rejected=0`、`patch rows applied=262/262`、`nodes_changed=174` | `build/agent-batch25/dryrun_s3.out.txt:7-15` |
| 别名总量 | 40,798 → **41,060**（+262） | 同上 `:15` |
| 每节点上限 | `cap_per_node=3`；max=**3**；hist=`{0:3071, 1:115, 2:30, 3:29}`；零候选节点 **3,071**；violations=**0** | `validate_s3.out.txt`；本代理复算 |
| 记号 span | `spans_total=107,127`（`d1=106,262`、`d2=**865**`）；规则见 `docs/research/2026-10-26-kb-content-derived-alias-rules.md` | `gen_s3_aliases.out.txt:5`；`106,262+865=107,127` |

> **数字校正**：本 ask 写 `d2=86`；产物实读为 `span_forms={'d1': 106262, 'd2': 865}`，且 `106,262+865=107,127` 与 `spans_total` 对齐。故本预注册冻结 **865**，不采用 86。

**逐科规模（rows / nodes）**

| 学科 | rows | nodes |
|---|---:|---:|
| MATH | 88 | 58 |
| PHYSICS | 117 | 73 |
| CHEMISTRY | 45 | 35 |
| BIOLOGY | 12 | 8 |
| 合计 | **262** | **174** |

**写入预期（算术，不是实测）**

- `tools/kb_build/tables/alias_exceptions.csv`：35 → **297** 数据行（+262）。
- `tools/kb_build/tables/alias_map.csv`：37,553 → **37,815** 数据行（+262）。
- 别名总量（canonical + 额外别名）：40,798 → **41,060**。
- 变更节点：**174**；`patch added vs old aliases=262`、`patch already in old aliases=0`。
- dense `.vec`：44,043 → **44,305** vectors（3,245 canonical + 41,060 alias-list）；新 sha256 由重打落盘，§7 登记。
- **整表应用，不做结果导向筛选**：262 行全部写入，不因金标/单点结果删行；候选仍含实例/代换噪声（规则文档 §S3.4-E / V3 已登记），由每节点 3 条上限、互斥门与非劣判据兜底。

### 1.3 薄料轨（blocked，零写）

| 项 | 登记 |
|---|---|
| 薄料行 | 行 23「**必备实验仪器的原理及使用**」（1 条材料 `phy-basic-instruments-principle-use`，`moe-2025-teaching-support-v2-22.json`，PRIMARY） |
| 本轮动作 | **blocked，零写**；维持为维护项，不改材料、不加绑定、不写 sidecar |
| 运行机制 | **支持** SUPPORTING 第二绑定：schema 复合主键 + 契约 `1..16 且恰一 PRIMARY`；读路径 `readBestTeachingMaterialPerNode` 按 PRIMARY→SUPPORTING→其他排序，SUPPORTING 绑定会立即生效 |
| 写入链 | **吃得下**：DAO `upsertBindings` 不校验角色/条数；调和器按包内绑定整组替换 |
| 作者工具链 | **没有产出非 PRIMARY 绑定的操作**：`build.py` 的 `_apply_material_bindings` 只写单元素 PRIMARY；`build.py:583-588` 直接拒绝 `len(bindings)>1`；`tables.py` 的绑定表是一材料一目标；`rebind_materials` 是“改指 + 去重”，不是“追加” |
| 结论 | 需先扩工具链（权威表 + 追加命令），不在本轮薄料动作内；本轮不写盘 |
| 证据 | `build/agent-batch25/baseline/identity.md` §5（逐项代码实读）；`knowledge-production/coverage-final-review-2026-10-25.md:109-111`（行 23 thinAssessment） |

**口径**：材料轨收益 = **口径完成度**（薄料正确登记、无待执行 REBIND、无未绑材料），不是检索分数。本批唯一的包写入来自 **R6 文本形态** 与 **S3 别名**。

### 1.4 执行链（写死）

1. **R6 文本修复**：按 `r6-items.json` 的 29 条逐条替换（summary 13、node boundary 6、material boundary 10），改 staging 主包与 staging sidecar；规则见 §1.1。任何未列条目零改。
2. **别名表追加**：把候选表 262 行追加到 `tools/kb_build/tables/alias_exceptions.csv`（35→297），保持表头 `subject,slug,alias,reason`。
3. **别名重建**：`PYTHONPATH=tools python -m kb_build.rebuild_aliases --write`。预期净增 262、冲突 0、拒绝 0、变更节点 174；`alias_map.csv` 37,815 行；别名总量 41,060。以执行轮输出实读为准；若净增不是 262，按实测登记并停止判读，先查原因。
4. **promote**：`PYTHONPATH=tools python -m kb_build.promote`，**v21 → v22**，刷新 `contentVersion`（新值 §7 登记）；23 道门 + 契约镜像 + round-trip 全绿才落盘。任一环红：**不写成品、不跑设备、不判读**。
5. **dense 重打**：`tools/dense_build` 全链：`pack_dense_asset.py` → `gen_dense_device_fixture.py` / `stage3_device_sim.py` → `gen_device_parity_fixture.py` → `gen_tokenizer_fixture.py`。预期 `.vec` 44,305 vectors；新 `.vec` / `.vec.json` sha 与 `vectorCount/aliasVectors/packSha256` 写入 §7。
6. **重钉与判读**：重钉受影响的钉值（`DenseRecallAssembly.VECTOR_ASSET_SHA256`、dense 夹具、词面/候选计数锚点；见 §5.1），再跑设备/JVM 判官、零漂移、显著性；全部结果写入 §7。

> 预期数字均为**算术预期**，不是实测；§7 必须以落盘产物实读覆盖。R6 文本改动会改变主包 sha 与 sidecar 聚合 sha，进而改变 `contentVersion`；不改戳会被安装器快路径跳过，主包改动还会使 dense 旁车 `packSha256` 失配，故必须走完整 v22 链（`build/agent-batch24/r6-assessment.md` §0）。

---

## 2. 判读口径

### 2.1 主判读与基线

- **主判读**：设备融合臂（真 SQL + LiteRT + `DenseFusion`，B512 召回 + 重排 + top5），与 A1 同口径。
- **基线 A1**：融合 **117/129 = 0.9069767441860465**、MRR **0.7322997416020672**；词面对照 **102/129 = 0.7906976744186046**、MRR **0.6206718346253232**（`build/agent-batch19/a1/device-metrics.txt:3-5,32-35`）。
- **当前生产 dense 腿**：只重排词面召回集、成员不变（`DenseRecallAssembly.kt:10-13`）；本批不继承任何并集/K=64 语义。
- **对照**：设备词面臂、JVM 双臂均报告；JVM 仅作零漂移/装置保真，不单独判分。
- **MRR**：报告，不单独作为采纳条件（A1 的判分对象是 Recall@5 主集）。

### 2.2 四条判据（内容更新 = 非劣）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**；低于即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与 A1 比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读**（不是“FAIL”，也不是“采纳/回滚”结论） |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v22、B = A1**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

补充：

- **装置预算**：沿用 A1 的 `p50Budget=150ms` / `p95Budget=250ms`；`overBudgetSamples>0` 只作装置健康观测，不单独作为采纳条件。
- **零漂移未计算**：若 JVM 账本缺失/镜像红导致 `NOT_COMPUTABLE`，按“不判读”处理；先修装置或另立预注册，不得用代理账本冒充同路门。
- **显著性缺失**：未跑出 `significance-*` 产物时，判据 4 记 `NOT_RUN`，不得写“无显著差异”。

### 2.3 为什么是“非劣”，不是“严格更高”

- **严格更高**是检索机制臂的门槛：它主张“某个机制带来增益”，所以要求主集严格更高 + 逐章不劣 + 负向守卫。
- **本批是内容更新**：R6 是文本形态修复（summary 不进知识召回索引；node boundary 换行→空格/分号是分隔符等价替换），S3 是记号别名补齐（262 条 / 174 节点），二者都不是新检索机制。套用“严格更高”会把裁定交给单题翻转，等价于面向结果调参，违反 `body-alias-v1` G8（禁读评测）。
- 非劣 + 逐章不劣 + 零漂移 + 负向守卫，覆盖内容更新真正要防的失败：索引截断、别名互斥、跨章回退、装置漂移；同时不把“没有涨分”误判为“内容更新失败”。
- **不放宽机制臂门槛**：后续若把本批别名/R6 当机制臂重测，仍须另立预注册并走“严格更高”三规则；本预注册不构成先例。

### 2.4 单点口径（写死）

| 单点 | 学科 / 章节 | 金标 query | A1 四路状态 |
|---|---|---|---|
| `等和线` | MATH / 数学必修第二册·第六章·平面向量 | 在△ABC中 AP=xAB+yAC，x+y=1 时 P 在直线 BC 上，那 x+y=1/2 时 P 跑到哪儿去了？ | device fused / device lexical / JVM fused / JVM lexical 均 `rank=absent` |
| `活结与死结模型` | PHYSICS / 物理必修第一册·第三章·相互作用 | 一幅画用绳子穿过可在横杆上滑动的小环吊着，两边拉力相等吗？若绳直接打结固定在墙上呢？ | 四路均 `rank=absent` |
| `力的分解` | PHYSICS / 物理必修第一册·第三章·相互作用 | 一盏灯被两根绳子吊着，每根绳的拉力都比灯的重力大，为什么？重力该怎么分？ | 四路均 `rank=absent` |

- 三个单点**只报不判**：§7 必须报告各 query 在 fused / lexical / device / JVM 四路的 hit 与名次（或 `absent`），但**不得**以单点翻转作为采纳/回滚依据。
- 不得因为单点翻转而在本批内改别名、改阈值、改参数；若确需调整，另立 `v2` 预注册。
- S3 候选表中这三个节点均为 **0 行**（本代理逐行检索确认），因此单点变化只可能来自 R6/索引侧；这一事实进一步支持“只报不判”。

---

## 3. 装置门

- **双门（写死）**：
  1. `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` → 必须 **exit 0**；
  2. `./gradlew :core:data:assembleDebugAndroidTest --console=plain` → 必须 **exit 0**。
- 本门是**单测编译门 + androidTest 装配门**（与 v21 预注册 §3 同口径）；若执行轮另跑 `testDebugUnitTest`，其结果另记，不替代本门。
- **红则不写包、不跑设备、不判读**：任一门红时停止在 §1.4 第 1 步之前；不得先写 staging、不得先跑 dense、不得用旧账本代替本批判读。
- 执行轮须把两门日志落盘到本批执行目录（建议 `build/agent-batch26/`），并在 §7 引用；不得只写“已跑”。
- 他线在飞文件若导致门红，按本节等待或另裁，不得回滚他线文件。
- **本预注册写作时点未跑 Gradle**：本节是门定义，不是通过声明。

---

## 4. 冻结输入（实算，2026-10-30）

### 4.1 包与版本

| 项 | 值 |
|---|---|
| 包身份 | `moe-2025-four-subjects-v1`；manifest **version=21**、`contentVersion=9f9e99ceec47cf8c`；manifest sha256 `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0` |
| 主包 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` sha256 `a719248448f314d1a6e6bfc74e3a101f07960d08d6985ade3d1c97f3efe6da36`；4,510,607 B；3,245 节点 / 64,973 材料 / 25 sidecar |
| 25 sidecar | 聚合 sha256 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`（逐文件见附录 A） |
| sidecar 索引 | `moe-2025-teaching-support-v2-index.json` sha256 `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 版本迁移 | **v21 → v22**；`promote` 的单调规则 = `max(staging, 成品) + 1`，当前 staging/成品均为 v21，故下一版为 v22 |
| 新 contentVersion | 由 `promote` 在落盘时刷新；**写前不可知**，§7 登记 |

### 4.2 别名账本与候选

| 项 | 值 |
|---|---|
| `alias_exceptions.csv` | 35 数据行；sha256 `1cba77feb7d61389e61674e6ec238ee2c7019c8fe53f051a8b14fe589410db59` |
| `alias_map.csv` | 37,553 数据行；sha256 `3b47ab2da0517a0ff9955ad2536d0d4cf46275bf88d5a750301dbfe350d02d8e` |
| `alias_dropped.csv` | 22,559 数据行；sha256 `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf` |
| 别名总量 | **40,798** = 3,245 canonical + 37,553 额外别名 |
| S3 候选表 | `knowledge-production/alias-exceptions-candidate-2026-10-29.csv`，262 数据行，sha256 `58d2f146123b84af2c92e95d3033fea1d75fe655391369154b5eb553401c4aa5` |
| R6 清单 | `build/agent-batch25/r6-items.json`，24,275 B，sha256 `cbfdfb48d30b5003425e1b0be410c8d63271991d216d9255b9bce6cd13898b2f` |
| batch25 读数件 | `gen_s3_aliases.out.txt` sha `e5b54aa4f6425cdd85cbbc03f6b2b386844f37e2195322a29c0c99bf41276485`；`gen_run2.out.txt` sha `f544e52407ca3c546869435e84698bd801c147947912cf1ff56968d748e472f4`；`dryrun_s3.out.txt` sha `7ac94c72ed8fc3526afdc752c69f775403f1eea24fdf761e84e9b230a9fcd27d`；`validate_s3.out.txt` sha `95f28831a662713345da0cfd36aa29d0ffc71c40861fb2d322534ddc29b86d16` |

### 4.3 dense、金标、A1 与判官

| 项 | 值 |
|---|---|
| `.vec` | sha256 `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446`；26,041,347 B；44,043 vectors = 3,245 canonical + 40,798 alias-list |
| `.vec.json` | sha256 `68ed524adbf0c45339c06e98ef95b159651dfd62e1d3ce16f502a023a1c448ce`；`packSha256=a7192484…`、`vectorCount=44043`、`aliasVectors=40798`、`bytes=26041347` |
| 钉值 | `DenseRecallAssembly.kt:108` `VECTOR_ASSET_SHA256 = "79a22270…"`；文件 sha256 `a32fa8e7b702ec52cdb8e5e37dea5b257698a3534bb27c6f65141b6b976f4ba2` |
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json` sha256 **`eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`**；129 题 / 24 章；`core/data/src/androidTest/assets/golden/golden_queries_v2.json` 同 sha |
| A1 账本（设备融合） | `build/agent-batch19/a1/device-metrics.txt` sha256 `a332e2aa6051e49f221879fe6cdbfad263a7550f94b3f6d853ccd59895059c0b`；117/129、MRR 0.7322997416020672、p50=38 / p95=51 / max=84ms |
| A1 账本（设备词面） | 同文件 `:32-35`：102/129、MRR 0.6206718346253232 |
| A1 收束 | `build/agent-batch19/a1-final.md` sha256 `ab2f7e75501692f0dc4f15721e6be50a91b2d17d04c3159e643ed02e4ff8b4a1` |
| 判官两件 | 设备 `GoldenRetrievalInstrumentedTest.kt` sha256 `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786`；JVM `GoldenRetrievalJvmTest.kt` 工作树 sha256 `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` |
| 零漂移镜像（非判官） | `RetrievalBenchmark.kt` sha256 `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |
| R1 语义 | `KnowledgeTeachingMaterialDao.readBestTeachingMaterialPerNode`（SQL `:81-122`，函数 `:123-126`）**不动**：每节点恰好一条、角色序 PRIMARY→SUPPORTING→其他；DAO 文件 sha256 `0a29d8634d76eaa46d7f74c82d48565286b0013bf392a706f9af9c7021e5a047`；调用点 `RoomKnowledgeBaseStore.kt:393` 文件 sha256 `7dc150c8eac95acc8c0bf6b5a15c456d75ee4b52fd4975b152fc3d7781a5af2a` |

> **既有脏态登记（非本批）**：JVM 判官工作树为 `M`（batch13→14 路径注，仅 3 行；sha 仍是本预注册冻结的 `62c232fc…`）；`tools/dense_build/model-manifest.json` 为 `M`（批次22 dense 重打滞留，工作树 sha `8b3a7b24…`）；`tools/dense_build/gen_device_parity_fixture.py` 为 `M`（他线真内容改动，工作树 sha `f65dcd36224e98fdd1155804e04951e94f9de12437341424cad35c56314ded56`）；`knowledge-production/wusan/materials.jsonl` 为 `M`（他线，工作树 sha `7950020ddb6ddfe4b4f31c14d88bdf567abac3ff7402b13726ec7277514d3e6a`）。本批不触碰、不回滚这些文件；A 表回滚以**工作树字节**为准，不以 HEAD 为准。

---

## 5. 回滚

### 5.1 触碰清单（写死；执行轮以此为准）

**A. 必触碰（R6 文本 + 别名链 + 包家族 + dense 夹具）**

| # | 路径 | 预期动作 | pre-sha256（全值） |
|---|---|---|---|
| 1 | `tools/kb_build/tables/alias_exceptions.csv` | +262 行（35→297） | `1cba77feb7d61389e61674e6ec238ee2c7019c8fe53f051a8b14fe589410db59` |
| 2 | `tools/kb_build/tables/alias_map.csv` | 重建（37,553→预期 37,815） | `3b47ab2da0517a0ff9955ad2536d0d4cf46275bf88d5a750301dbfe350d02d8e` |
| 3 | `tools/kb_build/tables/alias_dropped.csv` | 重建（预期不变，22,559） | `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf` |
| 4 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | R6 node boundary 6 条 + S3 别名 262 条（节点 `aliases`）+ promote v22 | `a719248448f314d1a6e6bfc74e3a101f07960d08d6985ade3d1c97f3efe6da36` |
| 5 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 22 + 新 contentVersion | `c4d3875049dab731bae7dfdeb960a3b35473cf706e48d71a1c8b7ff8deced3e0` |
| 6 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-01…25.json` | R6 summary 13 + material boundary 10；promote 重写 | 聚合 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`；逐文件见附录 A |
| 8 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 重打 | `79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446` |
| 9 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 重打旁车 | `68ed524adbf0c45339c06e98ef95b159651dfd62e1d3ce16f502a023a1c448ce` |
| 10 | `tools/dense_build/model-manifest.json` | 可能随重打更新（预期仅计数/指纹） | `8b3a7b2415275c82d2b0f92afe5561cf24acdaf7b9eba8536bac31cc3efea1c5` |
| 11 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt` | 重钉 `VECTOR_ASSET_SHA256` | `a32fa8e7b702ec52cdb8e5e37dea5b257698a3534bb27c6f65141b6b976f4ba2`；常量 `79a22270…` |
| 12 | `core/data/src/test/resources/dense/dense-device-order-reference.txt` | 重出 | `db4391a7a0e10b22bf6855accae4226e9a47b8824abcde8f00641ab22cb92c3d` |
| 13 | `core/data/src/test/resources/dense/dense-fusion-reference.txt` | 重出 | `c2ab7b1c321ebcd9c4886f6fde019633f2827d07d12f0901a9942431ae29ec5f` |
| 14 | `core/data/src/test/resources/dense/dense-scan-reference.txt` | 重出 | `ce50cc66deeb752070f42efe932fa8a1a956e5fa258c3be120071568de3874e2` |
| 15 | `core/data/src/test/resources/dense/golden-query-vectors.i8` | 重出（预期数据行不变） | `6411754368c3a769136704320f3e434e300cbdf18a8308f29925a71ff12284c3` |
| 16 | `core/data/src/test/resources/dense/tokenizer-parity-cases.txt` | 重出 | `e6cb53a367637376ec3809da8ae8e7afdf6839c34c12fa56738ac48fa1ed9f0b` |
| 17 | `core/data/src/test/resources/dense/tokenizer-parity-stages.txt` | 重出 | `8a136cfb4813c87450668f71f1b167b53b3660b7898c14dbe30cdd4e91d01875` |
| 18 | `core/data/src/test/resources/dense/tokenizer-parity-summary.json` | 重出 | `366d575a93375d745778bb51b2dad2b54cc2f4237c8fb4f7fef4e67cb83ed704` |
| 19 | `core/data/src/androidTest/assets/dense/encoder-parity-cases.tsv` | 重出 | `c101c39c33d237a5b463e047d481443c33154883530befd006666a3a3545b584` |
| 20 | `core/data/src/androidTest/assets/dense/encoder-parity-vectors.f32` | 重出 | `b0550446214616284a6578d84517a49be911abd14f54bb14c5ffe9e7fa78458e` |
| 21 | `core/data/src/androidTest/assets/dense/encoder-parity.json` | 重出 | `838f50c886d6ecba33d3fb15985eac0fbbdb29620f33be0bd59e3a79f7a6c953` |
| 22 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallRerankerTest.kt` | 仅当候选映射变化时重钉 | `807d3a506c2fb3c77a8b49860c3a85e0e74c600d925af042c4b68f73de0f5b5e`；当前候选总数=1,074 |
| 23 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ProductionLexicalLegExportTest.kt` | 仅当词面计数变化时重钉 | `aa96d93253fab39e8fd2a32b21dd248897e29862f72f3b70c1c6ddec40b816b6`；当前 `V1_D1_MAIN=0.7906976744186046`、`V1_D1_MRR=0.6206718346253232`、hits=102 |
| 24 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage1LexicalLabTest.kt` | 仅当词面计数变化时重钉 | `1d774ffbda3ec1c70beac906d107bc07442298017ae6243dc1ea4f853a0b067b` |
| 25 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage2LexicalScoresExportTest.kt` | 仅当词面计数变化时重钉 | `19a94984f48eeb377e3172eedd0cde42dd903ea76d4d988636e59a01c11449f6` |
| 26 | `build/stage3-device-expectation.json` | 重出（build 产物，备份） | `24e1d26759691f8afe117264773a61694a9efb1ac2d83cc220045de77e0e65f4` |

**B. 零触碰（写死）**

- 判官两件（设备 `c2280255…`、JVM `62c232fc…`）与零漂移镜像（`8c2a2ef0…`）：**零改**。
- 金标 `eabef102…`（129/24）：**零改**。
- 25 个 sidecar 的 `contentMarkdown`、标题、绑定：**零改**；只改 §1.1 列出的 `summaryMarkdown` / `boundaryMarkdown` 形态。
- 其他节点别名：只允许 S3 候选表列出的 174 个节点变化；`alias_map` 应恰好 +262 行且全部属于这 174 个节点。出现任何其他节点变化 = 缺陷，停止并回滚。
- `readBestTeachingMaterialPerNode` 与 `RoomKnowledgeBaseStore.kt:393` 调用点：**零改**（R1 语义冻结）。
- 模型资产（ONNX 权重、tokenizer vocab）与 `tools/dense_build/dense_asset.py`：**零改**。
- 本预注册之外的其他 docs/线（含工作树中数百项他线改动）：**零触碰**。

### 5.2 写前备份与 pre-sha

- 写前对 §5.1 A 表所有 tracked 文件做字节备份到本批执行目录（建议 `build/agent-batch26/backup/`），并记录 `sha256sum` 全表；`build/` 产物（`stage3-device-expectation.json`）同样备份。
- 写前运行 `git status --porcelain -- <§5.1 A 表路径>`。**本预注册写作时点实测（2026-10-30）**：
  - 核心写面（主包 / 25 sidecar / 索引 / manifest / `.vec` / `.vec.json` / `alias_exceptions` / `alias_map` / `DenseRecallAssembly.kt`）**为空（CLEAN）**；
  - `tools/dense_build/model-manifest.json` 为既存 `M`（批次22 滞留），`tools/dense_build/gen_device_parity_fixture.py` 为既存 `M`（他线），JVM 判官为既存 `M`（路径注）；这些不是本批改动，回滚以工作树字节为准；
  - S3 候选表为 `??`（未跟踪，属输入，不是写面冲突）；
  - 整体工作树非 CLEAN（他线数百项），本批不触碰、不回滚他线文件。
- 25 sidecar 逐文件 pre-sha 见附录 A；聚合 sha 的构造 = 按 `v2-01…v2-25` 顺序以 `\n` 连接 `"<basename> <sha256>"`（**行间换行、末尾无换行**）后再 sha256（可复算；构造不同会得到不同聚合值）。

### 5.3 回滚 / 采纳程序

**不采纳（回滚）**

1. 对 §5.1 A 表 tracked 文件逐字恢复 pre-sha 字节（CLEAN 文件用 `git checkout --`；既存脏文件用写前备份覆盖）；`build/` 产物用备份覆盖。
2. 核验：manifest `version=21` / `contentVersion=9f9e99ceec47cf8c`；主包 `a7192484…`；25 sidecar 聚合 `ed1c6d4f…`；`alias_exceptions` 35 行 / `1cba77fe…`；`alias_map` 37,553 行 / `3b47ab2d…`；`alias_dropped` 22,559 行 / `84040e23…`；`.vec` `79a22270…`（44,043）；`.vec.json` `68ed524a…`；`DenseRecallAssembly.VECTOR_ASSET_SHA256=79a22270…`；判官/金标 sha 不变。
3. 核验 `readBestTeachingMaterialPerNode` 与 `RoomKnowledgeBaseStore.kt:393` 未改；材料 64,973、未绑 0、全 PRIMARY、多绑定 0。
4. 复跑 §3 双门；若回滚后仍红，登记为他线/基线问题，不归因本批。
5. 在 §7 写明回滚原因与逐文件 sha 核验结果；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 v22 包、新 `.vec`、新别名账本与 dense 夹具；按实测重钉 `DenseRecallAssembly.VECTOR_ASSET_SHA256`、`.vec.json` `vectorCount/aliasVectors/packSha256`、受影响的测试锚点（只按实测重钉，不放宽容差/断言）。
2. 在代码/账本注释里写明新旧值与量源（提交号/日志路径），保留 §4 的 pre-sha 表作为审计链。
3. 在本预注册 §7 登记新 stateAfter；A1 预注册不代改（其 §1–§6 与 §7 均保持原样；如需引用新基线，另写维护记录）。
4. 若采纳后任何后续批次复用本批别名/R6，必须另立预注册；不得用本批的非劣口径替代机制臂的严格更高。

---

## 6. 预期与边界（不承诺）

1. **R6 只改形态，不伤检索（静态推导）**：13 条 `summaryMarkdown` **不进任何知识召回索引**（既不是词面特征表，也不是 dense 语料），只用于 `KNOWLEDGE_READ` 展示；6 条 node `boundary` 进词面特征，但“换行→空格/分号”是分隔符等价替换（抽取正则 `[^\p{L}\p{N}]+` 把换行和空格都当分隔符），dense 只吃 `canonicalName + aliases`；10 条 material `boundaryMarkdown` 不进词面特征。**知识召回走 `knowledge_search_feature` 表，不是 FTS；全仓唯一 FTS 是错题本 `library_search_fts`**（`build/agent-batch24/r6-assessment.md:99-109`）。因此“sidecar 文本进 FTS”不是本仓库的准确机制描述；本批按真实机制登记。静态推导仍需 v22 新包重跑 device+JVM+zero-drift 才能成为读数，不能沿用 v21 的 117/129。
2. **R6 发布边界**：summary 在 25 个 sidecar 里，任何 sidecar 正文写入都会改变 `contentVersion`；不改戳时安装器会命中快路径、改动静默不到设备。6 条 node boundary 在主包里，改主包会改变主包 sha，dense 旁车 `packSha256` 随之失配，即使向量不变也必须重新打包 `.vec` 旁车（`r6-assessment.md` §0）。因此必须走 v22 整链。
3. **S3 别名增益不确定**：S3 只服务“短记号就是检索入口”的节点；纯记号查询在词面腿可能仍是 0 特征（规则文档 §1.3 的 `x+y`/`λ+μ` 探针），别名也可能挤掉既有片段（节点侧 16 片段 / 192 特征预算）。262 条 / 174 节点是已报规模，不是增益承诺；主集/逐章非劣判据正是为防索引截断与跨章回退。
4. **S3 候选仍有噪声**：规则文档 §S3.4-E / V3 已登记 `f(1)=2f(1)`、`b=v_0`、`SA=2`、`FE=2`、`C_2^2=1`、`Aat^100`、`AA_2` 这类实例/代换串；本批按冻结规则整表应用，不做结果导向筛选。若出现主集/逐章回退，整批回滚，不做“只留部分行”的事后挑选。
5. **薄料零写**：行 23「必备实验仪器的原理及使用」维持维护项；运行机制支持 SUPPORTING 第二绑定、写入链也吃得下，但作者工具链没有产出非 PRIMARY 绑定的操作，需先扩工具链（权威表 + 命令），不在本轮薄料动作内。材料轨收益 = 口径完成度，不是检索分数。
6. **单点边界**：`等和线` / `活结与死结模型` / `力的分解` 只报不判；即使从 `absent` 变 hit，也不据此采纳；即使仍 `absent`，也不据此否决（除非主集 <117/129 或某章严格更低）。不得因为单点结果改别名/阈值/参数。
7. **不承诺任何具体分数**（例如 117→118 或 MRR 提升）：本批只承诺按 §2 四条判据求值；任何增益只报告，不作为“超出预期”的采纳依据。
8. **装置边界**：设备读数为 emulator，不是真机；JVM 仅作零漂移/保真，不判分；离线 expectation 与设备 MRR 存在已登记 ULP 两源（`…229` vs `…232`），本批不改口径。
9. **无第二评测集**：129 题 / 24 章是唯一冻结金标；不外推泛化，不新增/改写题目。内容更新不改变金标 sha。
10. **版本边界**：v22 只用于本批；promote 任何一环红则保持 v21，不产生半包/半升级。材料轨零写；R6 与 S3 是唯一包写入面。

---

## 附录 A：25 sidecar pre-sha（v2-01…v2-25）

| # | 文件 | sha256 |
|---|---|---|
| 01 | `moe-2025-teaching-support-v2-01.json` | `6719bcc65c9fa025747e9d23052c66714db837716dde9ba1de9f910fc52404e4` |
| 02 | `moe-2025-teaching-support-v2-02.json` | `337d6ecef966654ddff2b90e916fa03677efc3dcfbe976aee41a8131a2a1310b` |
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
| 25 | `moe-2025-teaching-support-v2-25.json` | `a15df33956713db1f65335b006f21eec34f424ae1a9cb9403b3d7900e16b05b9` |

**聚合**：`ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`。

## 附录 B：本预注册的复算命令（只读）

```bash
cd "D:/smart mistake book"

# 1) 包身份 / 版本 / contentVersion
python -c "import json;d=json.load(open('core/data/src/main/resources/knowledge/moe-2025-update-manifest.json',encoding='utf-8'));print(d['version'],d['contentVersion'])"

# 2) 关键 sha（包/25 sidecar/别名表/.vec/金标/判官/A1 账本）
sha256sum core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json \
  core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-*.json \
  core/data/src/main/resources/knowledge/moe-2025-update-manifest.json \
  core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec{,.json} \
  tools/kb_build/tables/alias_{exceptions,map,dropped}.csv \
  tools/kb_coverage/tables/golden_queries_v2.json \
  build/agent-batch19/a1/device-metrics.txt \
  build/agent-batch25/r6-items.json

# 3) R6 计数复算（主包 node boundary + 25 sidecar summary/material boundary）
python - <<'PY'
import json,glob
p=json.load(open('core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json',encoding='utf-8'))
n=w=0
for s in p['subjects']:
    for t in s.get('topics',[]):
        for kp in t.get('knowledgePoints',[]):
            b=kp.get('boundary') or ''
            if '\n' in b:
                n+=1
                if b.index('\n')<80: w+=1
print('node boundary newline',n,'within80',w)
sm=sm60=mat=mat80=0
for f in glob.glob('core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-*.json'):
    if 'index' in f: continue
    d=json.load(open(f,encoding='utf-8'))
    for m in d.get('materials',[]):
        s=m.get('summaryMarkdown') or ''
        if '\n' in s:
            sm+=1
            if s.index('\n')<60: sm60+=1
        b=m.get('boundaryMarkdown') or ''
        if '\n' in b:
            mat+=1
            if b.index('\n')<80: mat80+=1
print('summary',sm,'within60',sm60,'material boundary',mat,'within80',mat80)
PY

# 4) S3 候选规模 / 逐科 / 上限
python - <<'PY'
import csv,collections
rows=list(csv.DictReader(open('knowledge-production/alias-exceptions-candidate-2026-10-29.csv',encoding='utf-8')))
print('rows',len(rows))
print('rows/subject',dict(collections.Counter(r['subject'] for r in rows)))
print('nodes/subject',{k:len({r['slug'] for r in rows if r['subject']==k}) for k in {r['subject'] for r in rows}})
print('per_node_max',max(collections.Counter((r['subject'],r['slug']) for r in rows).values()))
PY

# 5) 装置门（执行轮跑；本预注册未跑）
./gradlew :core:data:compileDebugUnitTestKotlin --console=plain
./gradlew :core:data:assembleDebugAndroidTest --console=plain
```

---

## 7. 结果节

> 留空。执行轮填写；§1–§6 一字不动。
