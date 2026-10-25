# KB v21 内容更新：等和线正文派生别名 · 预注册（2026-10-26）

> 出数前冻结；事后 §1–§6 一字不动，只填 §7。依据：`docs/research/2026-10-26-kb-content-derived-alias-rules.md`（`body-alias-v1`，S2/G2/G6/G7/G8）、`knowledge-production/alias-exceptions-candidate-2026-10-26.csv`、批次19 A1 账本与批次21 基线。本预注册只覆盖 **2 条别名** 与 **材料边界登记**；候选表其余 4 行不进入本批写面。

## 1. 更新内容（冻结）

### 1.1 别名轨（2 条，写入）

| # | 别名（写入值） | 材料块 | 原文摘录（材料实读） | 规则 | 内容派生 / 边界 |
|---|---|---|---|---|---|
| 1 | `其和为 k 的等和线` | `ext-mat-91e0c2ace8-058b`（`moe-2025-teaching-support-v2-05.json`，MATH / METHOD_MODEL，PRIMARY → `kb:moe-2025-four-subjects-v1:math:atomic:等和线`，`:61290-61307`） | `**其和为 $k$ 的等和线**：点 $O$、$A$、$B$ 为平面上不共线的三个点` | **S2_STRUCTURAL**：2–24 字加粗短语，非通用章节名；`df_node≥1`；同 section 含 cue | `contentMarkdown` 加粗 span 归一后逐字出现（去 `**`、去 `$`、NFKC、trim）；材料标题为「和为 $k$ 的等和线」，标题不含「其」；候选首字「其」来自正文加粗 span，故**非金标题反推**。归一后 10 字；`df_node=1`；`df_subject=1/14,468`（0.0069%）；同学科无其他节点 claim。 |
| 2 | `k 的取值影响等和线的位置` | 同上 | `**$k$ 的取值影响等和线的位置**` | **S2_STRUCTURAL**（同上） | 归一后逐字出现；归一后 13 字；`df_node=1`；`df_subject=1/14,468`（0.0069%）；正文命题式加粗 span，登记为别名但**单点只报不判**；同学科无其他节点 claim。 |

**冻结说明**

- 两条别名的 `subject/slug/alias` 取 `knowledge-production/alias-exceptions-candidate-2026-10-26.csv` 第 2–3 行；`reason` 保留该表原行（含 `body-alias-v1/S2_STRUCTURAL`、材料块与 cue 证据）。写入目标表头：`subject,slug,alias,reason`（`tools/kb_build/tables/alias_exceptions.csv`）。
- 候选表共 6 行；本预注册只冻结上述 2 行。其余 4 行（`其和为 1 的等和线（三点共线的充要条件）`、`|k| 的几何意义`、两条 S1 重复短句）**不写入、不测量、不判读**，留在候选表等待后续单独预注册。
- 规则来源：`docs/research/2026-10-26-kb-content-derived-alias-rules.md:280-284`（S2）、`:302`（G2 逐字）、`:306`（G6 全局互斥）、`:307`（G7 分词器友好）、`:308`（G8 禁读评测）。
- 本代理实读校验：材料块 `contentMarkdown` 无 Markdown 标题，整段为同一 section；同 section 含 `即/当/若/所以` cue；两条别名均通过 `title_usable` 形状门（`tools/kb_build/rebuild_aliases.py:78-99`；本代理实跑 `title_usable` 返回 `None`）；并按 `:138-179` 的三道门逐项核验主名/同科互斥（全包节点名、节点别名、全包材料标题、`alias_exceptions.csv`、`alias_map.csv` 均 0 命中）。
- ask 对第 2 条原文摘录止于「`**$k$ 的取`」；本代理按 v2-05 材料块实读补全为 `**$k$ 的取值影响等和线的位置**`，其余冻结项不变。

### 1.2 材料轨（登记边界，不写入）

| 检查项 | 实测 / 登记 | 证据 |
|---|---|---|
| 薄料节点唯一材料 | `必备实验仪器的原理及使用` 只有 **1 条**材料 `phy-basic-instruments-principle-use`（`moe-2025-teaching-support-v2-22.json`），role=**PRIMARY**；正文为四仪器总述，多用电表仅 ④ 段；**不是错挂** | 本代理实读 v2-22：该节点绑定 1 条、`title=必备实验仪器的原理与使用要点`、content 含 `④` 与 `多用电表` |
| 全包待执行 REBIND | **0 条待执行**：`改绑 0；跳过（幂等/错行）9575；材料总数 64973；悬空绑定 0；可写回: True`。ask 登记 **30/30 已落包**；本代理全表复跑亦为 0 待执行 | 本代理实跑 `PYTHONPATH=tools python -m kb_build.rebind_materials`（2026-10-26，报告模式，无 `--write`） |
| 唯一“正文讲多用电表” | `phys-hj-189-multimeter-usage` 已改挂 `多用电表的使用方法`（PRIMARY），不再挂 `利用电场线和等势面解决带电粒子运动问题的方法` | 本代理实读 v2-02 绑定；`tools/kb_build/tables/material_rebind.csv` 对应行 |
| 未绑材料 | **0 / 64,973**；全部绑定 role=**PRIMARY**；0 条材料多绑定 | 本代理逐 sidecar 统计（v2-01…v2-25） |
| SUPPORTING 补链通道 | **无现行通道**：64,973 条绑定全部为 PRIMARY，无 SUPPORTING 绑定 | 同上 |
| 缺的“电路元器件识别”半边 | 另见 `电路中的基本概念` 既有节点；本批不补料 | `knowledge-production/coverage-final-review-2026-10-25.md:109-112`（行 23 thinAssessment） |
| 维护态写包 | **零写**；blocked 写「**本批无材料写入**」 | 本预注册与材料轨不写任何 material/sidecar 正文 |

**口径**：材料轨的收益 = **口径完成度**（薄料正确绑定、无待执行 REBIND、无未绑材料、无 SUPPORTING 缺口），不是检索分数。本批唯一的包写入来自别名轨（2 条 `alias_exceptions` → v21）。

### 1.3 执行链（写死）

1. 追加 2 行到 `tools/kb_build/tables/alias_exceptions.csv`（33 → **35** 数据行）。
2. `PYTHONPATH=tools python -m kb_build.rebuild_aliases --write`：写 staging 包 + `alias_map.csv` / `alias_dropped.csv`。预期净增 2 条、冲突 0、拒绝 0、变更节点 1（`等和线`）；`alias_map.csv` 37,551 → **37,553** 数据行；别名总数 40,796 → **40,798**。以执行轮输出实读为准。
3. `PYTHONPATH=tools python -m kb_build.promote`：**v20 → v21**，刷新 `contentVersion`（新值 §7 登记）；23 道门 + 契约镜像 + round-trip 全绿才落盘（`tools/kb_build/promote.py:19-24`）。任一环红：不写成品、不跑设备、不判读。
4. `tools/dense_build` 全链重打：`pack_dense_asset.py` → `gen_dense_device_fixture.py` / `stage3_device_sim.py` → `gen_device_parity_fixture.py` → `gen_tokenizer_fixture.py`（按批次19 A1 已跑链）；`.vec` 预期 44,041 → **44,043** vectors（3,245 canonical + 40,798 alias），新 sha 与 `.vec.json` 的 `vectorCount/packSha256` 写入 §7。
5. 重钉受影响的钉值（`DenseRecallAssembly.VECTOR_ASSET_SHA256`、测试锚点；见 §5.3），再跑设备/JVM 判官、零漂移、显著性；全部结果写入 §7。

> 预期数字均为**算术预期**，不是实测；§7 必须以落盘产物实读覆盖。若 `rebuild_aliases` 的实际净增不是 2（例如标题派生面同时漂移），按实测登记并停止判读，先查原因。

## 2. 判读口径

### 2.1 主判读与基线

- **主判读**：设备融合臂（真 SQL + LiteRT + `DenseFusion`，B512 召回 + 重排 + top5），与 A1 同口径。
- **基线 A1**：融合 **117/129 = 0.9069767441860465**、MRR **0.7322997416020672**；词面对照 **102/129 = 0.7906976744186046**、MRR **0.6206718346253232**（`build/agent-batch19/a1/device-metrics.txt:3-5,32-35`）。
- **当前基线边界**：批次21 dense-union 并集臂（K=64）**已终裁不采纳并逐字回滚**（`docs/kb-batch21-2026-10-26.md` 收口补记）；当前生产 dense 腿只**重排词面召回集、成员不变**（`DenseRecallAssembly.kt:10-13`）。本批不继承任何并集/K=64 语义。
- **对照**：设备词面臂、JVM 双臂均报告；JVM 仅作零漂移/装置保真，不单独判分。
- **MRR**：报告，不单独作为采纳条件（A1 的判分对象是 Recall@5 主集）。

### 2.2 四条判据（内容更新 = 非劣）

| # | 判据 | 求值口径 |
|---|---|---|
| 1 | **主集非劣** | 设备融合 Recall@5 **≥ 117/129**；低于即不采纳 |
| 2 | **24 章逐章不劣** | 24 章逐章 Recall@5 与 A1 比较；任一章**严格更低**即不采纳；equal/improved 均通过 |
| 3 | **零漂移对称差 0** | 同路 device↔JVM 的 fused 与 lexical 两条 MISS query 集合 symmetric difference 必须 **0**；非 0 = 装置缺陷，本臂**不判读**（不是“FAIL”，也不是“采纳/回滚”结论） |
| 4 | **负向守卫** | 按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；**A = v21、B = A1**，配对差 = A − B；要求 95% CI **上界 ≥ 0**（只排除“显著更差”，不要求显著更高） |

补充：

- **装置预算**：沿用 A1 的 `p50Budget=150ms` / `p95Budget=250ms`；`overBudgetSamples>0` 只作装置健康观测，不单独作为采纳条件。
- **零漂移未计算**：若 JVM 账本缺失/镜像红导致 `NOT_COMPUTABLE`，按“不判读”处理；先修装置或另立预注册，不得用代理账本冒充同路门。
- **显著性缺失**：未跑出 `significance-*` 产物时，判据 4 记 `NOT_RUN`，不得写“无显著差异”。

### 2.3 为什么是“非劣”，不是“严格更高”

- **严格更高**是检索机制臂的门槛（A1 保守别名臂、dense-union 并集臂）：它主张“某个机制带来增益”，所以要求主集严格更高 + 逐章不劣 + 负向守卫。
- **本批是内容更新**：它主张的是内容面/索引面补齐，不是新检索机制。两条别名只服务 `MATH/等和线` 一个节点；冻结金标中 `expectedSlug=等和线` 的题只有 **1/129**。若套用“严格更高”，裁定实际上会被单题翻转支配，等价于面向结果调参，违反 `body-alias-v1` G8（禁读评测）与内容侧纪律。
- 非劣 + 逐章不劣 + 零漂移 + 负向守卫，正好覆盖内容更新真正要防的失败：索引截断、别名互斥、跨章回退、装置漂移；同时不把“没有涨分”误判为“内容更新失败”。
- **不放宽机制臂门槛**：后续若把本批别名当机制臂重测，仍须另立预注册并走“严格更高”三规则；本预注册不构成先例。

### 2.4 单点口径（写死）

- `等和线`（`expectedSlug=等和线`，章节 `数学必修第二册·第六章·平面向量`，该章共 3 题）**只报不判**。
- §7 必须报告该 query 在 fused / lexical / device / JVM 四路的 hit 与名次（或 `absent`），但**不得**以该点翻转作为采纳/回滚依据。
- 不得因为该点翻转而在本批内改别名、改阈值、改参数；若确需调整，另立 `v2` 预注册。
- 本代理按 `KnowledgeSearchFeatureExtractor.fromQuestion` 的等价翻译实算（未运行 Kotlin 生产代码）：两条别名与该 query 的 **2–3 字 gram 交集为 0**（query 特征如 `abc中/ab/bc/在直线/直线/跑到哪儿去了`；别名特征如 `其和为/和为/等和线`、`取值/影响/位置`）。因此词面腿对该题**不可能**因这两条别名直接得分；当前 A1 口径下 dense 腿只重排词面召回集（成员不变），而该题在 A1 fused/lexical 均为 `absent`（`build/agent-batch19/a1/device-fused-misses.txt` / `device-lexical-misses.txt` 均 `rank=absent`），两条别名又与该 query 无特征交集，故 dense 也无从把它带进 top5。**按当前口径，该题没有已知的翻转入径**；这一事实进一步支持“单点只报不判”。

## 3. 装置门

- **门**：`:core:data:compileDebugUnitTestKotlin` 必须为 **exit 0**。
- **写轮实测**：本代理 2026-10-26 实跑 `./gradlew :core:data:compileDebugUnitTestKotlin --console=plain` → **exit=0 / BUILD SUCCESSFUL**（`24 actionable tasks: 24 up-to-date`）。这是门任务的真实结果，不覆盖 `testDebugUnitTest` 全绿，也不覆盖设备测试。
- **红则不写包、不跑设备、不判读**：门红时停止在 §1.3 第 1 步之前；不得先写 staging、不得先跑 dense、不得用旧账本代替本批判读。
- 执行轮须把门日志落盘到 `build/agent-batch22/`（或本批执行目录），并在 §7 引用；不得只写“已跑”。
- 他线在飞文件若导致门红，按 §3 等待或另裁，不得回滚他线文件。

## 4. 冻结输入（实算，2026-10-26）

### 4.1 包与节点

| 项 | 值 |
|---|---|
| 包身份 | `moe-2025-four-subjects-v1`；manifest **version=20**、`contentVersion=ebf477816c318704`；manifest sha256 `0210dc02ef423bf81aaa1d71c023cf3aa169eede60b094d78010fbb272065caf` |
| 主包 | `moe-2025-four-subjects-v1.json` sha256 `298f4c0c41519d5116b8d35340edd6f7b4e073573950b4eb0a13e01b32822150`；3,245 节点 / 64,973 材料 / 25 sidecar |
| 25 sidecar | 聚合 sha256 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`（逐文件见附录 A）；源材料所在 v2-05 sha256 `5d2fc2623a5efc0f8849fc99d3cea967c813263e9ee879a54f6dbf776fe96fb8` |
| 目标节点 | `MATH / 等和线`（`kb:moe-2025-four-subjects-v1:math:atomic:等和线`）；当前 aliases **23 条**（含主名）；绑定材料 **32 条** |
| 目标材料块 | `ext-mat-91e0c2ace8-058b`；title `和为 $k$ 的等和线`；bindings `[{knowledgeNodeId: ...等和线, role: PRIMARY}]`（v2-05 `:61290-61307`） |
| 别名账本 | `alias_exceptions.csv` 33 数据行 / sha256 `43c6b61deeb1d1483ded5bb92b0f27cc18bce249c0aefe02971d3ebf96611d47`；`alias_map.csv` 37,551 数据行 / sha256 `904864690ddb5d7abca35f4dfe4b0581dd791766337a2653d8d4266d60b61778`；`alias_dropped.csv` 22,559 数据行 / sha256 `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf` |
| 别名总量 | **40,796** = 3,245 canonical + 37,551 额外别名（节点 `aliases` 数组的并集）；`.vec.json` `corpus.aliasVectors=40796` 指这 40,796 个 alias-list 向量，另有 3,245 个独立 canonical 向量，故 `vectorCount=44,041` |
| 候选表 | `knowledge-production/alias-exceptions-candidate-2026-10-26.csv`，6 数据行，sha256 `a4d279cf78c80112affd7dfa344dcdbb0fb73412aacee2251b582006cb46a7be`；本批只取第 2–3 行 |

### 4.2 版本与 dense

| 项 | 值 |
|---|---|
| 版本迁移 | **v20 → v21**；`promote` 的单调规则 = `max(staging, 成品) + 1`，当前 staging/成品均为 v20，故下一版为 v21；仓库未发现 `version=21` 的 manifest（grep 无命中） |
| 新 contentVersion | 由 `promote` 在落盘时刷新；**写前不可知**，§7 登记 |
| `.vec` | sha256 `0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a`；`vectorCount=44041` = 3,245 canonical vectors + 40,796 alias-list vectors（`aliasVectors` 含各节点主名，故不等于 37,551 条额外别名） |
| `.vec.json` | sha256 `4c911fc32261412254c9e52e4d340e1ee6412b9be3e532fbefc9b821c7396dcb`；`packSha256=298f4c0c…`、`vectorCount=44041` |
| 重打预期 | `.vec` **44,043** vectors（+2 alias）；新 sha256 **重打后登记**（ask 的 `.vec 0536d0d1→重打新值`）；若实际不是 +2，按实测登记并停止判读 |
| 钉值 | `DenseRecallAssembly.kt:107` `VECTOR_ASSET_SHA256 = "0536d0d1…"`（当前文件 sha256 `6b7f91a95e66717ac3520609de4a1399e7e793d057cb1687ddd42794ae8511e6`） |
| 离线 expectation | `build/stage3-device-expectation.json` sha256 `a5e2d46eb03d97c2f4f50317cd6a7993a3b3c4d93fa28106b5953137127a5632`；`refFusedD1=117/129`、`refLexicalOnlyD1=102/129`、`refLexicalOnlyD1.mrr=0.6206718346253229` |

### 4.3 金标、A1 账本与判官

| 项 | 值 |
|---|---|
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json` sha256 **`eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102`**；129 题 / 24 章；`core/data/src/androidTest/assets/golden/golden_queries_v2.json` 同 sha |
| A1 账本（设备融合） | `build/agent-batch19/a1/device-metrics.txt` sha256 `a332e2aa6051e49f221879fe6cdbfad263a7550f94b3f6d853ccd59895059c0b`；117/129、MRR 0.7322997416020672、p50=38 / p95=51 / max=84ms |
| A1 账本（设备词面） | 同文件 `:32-35`：102/129、MRR 0.6206718346253232 |
| A1 收束 | `build/agent-batch19/a1-final.md` sha256 `ab2f7e75501692f0dc4f15721e6be50a91b2d17d04c3159e643ed02e4ff8b4a1`；stateAfter = v20 + `.vec 0536d0d1…`（44,041） |
| 判官两件 | 设备 `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalInstrumentedTest.kt` sha256 `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786`；JVM `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/GoldenRetrievalJvmTest.kt` sha256 `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` |
| 零漂移镜像（非判官） | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RetrievalBenchmark.kt` sha256 `8c2a2ef05b1c96bb31bbef206e1d432ec615c2ab36f7c06c86076ec3d824e5be` |

> **ULP 两源（沿用 A1 §7.8，不改口径）**：离线 `stage3-device-expectation.json` 的 lexical MRR 为 `0.6206718346253229`，设备/生产钉值为 `0.6206718346253232`；差 ≈3×1e-16。本批不据此改判据、不改锚点、不把两源合并。

## 5. 回滚

### 5.1 触碰清单（写死；执行轮以此为准）

**A. 必触碰（别名链 + 包家族）**

| # | 路径 | 预期动作 | pre-sha256（全值） |
|---|---|---|---|
| 1 | `tools/kb_build/tables/alias_exceptions.csv` | +2 行（33→35） | `43c6b61deeb1d1483ded5bb92b0f27cc18bce249c0aefe02971d3ebf96611d47` |
| 2 | `tools/kb_build/tables/alias_map.csv` | 重建（37,551→预期 37,553） | `904864690ddb5d7abca35f4dfe4b0581dd791766337a2653d8d4266d60b61778` |
| 3 | `tools/kb_build/tables/alias_dropped.csv` | 重建（预期不变） | `84040e23256bfae0e31531d4e8f04cb3d022b31654bf6bedae0cce2b297eafdf` |
| 4 | `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | promote v21 | `298f4c0c41519d5116b8d35340edd6f7b4e073573950b4eb0a13e01b32822150` |
| 5 | `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | version 21 + contentVersion | `0210dc02ef423bf81aaa1d71c023cf3aa169eede60b094d78010fbb272065caf` |
| 6 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-index.json` | promote 重写（预期字节不变） | `2da0b9c5702c708723e4129fba3eda6e34875271ea07422f2aa9fb9981145b92` |
| 7 | `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-01…25.json` | promote 重写（内容预期不变） | 聚合 `ed1c6d4f6d9d331a0982408c2f60a660a8490cbad5c2a74d438604d12057355e`；逐文件见附录 A |
| 8 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec` | 重打 | `0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a` |
| 9 | `core/data/src/main/resources/knowledge/dense/bge-small-zh-int8.vec.json` | 重打旁车 | `4c911fc32261412254c9e52e4d340e1ee6412b9be3e532fbefc9b821c7396dcb` |
| 10 | `tools/dense_build/model-manifest.json` | 可能随重打更新（预期仅计数/指纹） | `7f1f6e8d868428a4cb4f1b45512e433fae8940da652dcada33d0ae9eb72c36f9` |
| 11 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallAssembly.kt` | 重钉 `VECTOR_ASSET_SHA256` | `6b7f91a95e66717ac3520609de4a1399e7e793d057cb1687ddd42794ae8511e6`；常量 `0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a` |
| 12 | `core/data/src/test/resources/dense/dense-device-order-reference.txt` | 重出 | `5e330d9d39313e59d6d901c399712bbdbffe2250a464f9710467d847f631219c` |
| 13 | `core/data/src/test/resources/dense/dense-fusion-reference.txt` | 重出 | `c2ab7b1c321ebcd9c4886f6fde019633f2827d07d12f0901a9942431ae29ec5f` |
| 14 | `core/data/src/test/resources/dense/dense-scan-reference.txt` | 重出 | `84eba62a526a242b2d117bc8ea018694c0e80fea7514ecf9e311421c08209f61` |
| 15 | `core/data/src/test/resources/dense/golden-query-vectors.i8` | 重出（预期数据行不变） | `6411754368c3a769136704320f3e434e300cbdf18a8308f29925a71ff12284c3` |
| 16 | `core/data/src/test/resources/dense/tokenizer-parity-cases.txt` | 重出 | `aaf9642980a0c49b47cda2215241bb08414356a22d7b97ff6169d6918628f41b` |
| 17 | `core/data/src/test/resources/dense/tokenizer-parity-stages.txt` | 重出 | `8a136cfb4813c87450668f71f1b167b53b3660b7898c14dbe30cdd4e91d01875` |
| 18 | `core/data/src/test/resources/dense/tokenizer-parity-summary.json` | 重出 | `129a2020509bb080778779a135de6aebaf4307c326db72f699cfe1d4f0ec2065` |
| 19 | `core/data/src/androidTest/assets/dense/encoder-parity-cases.tsv` | 重出 | `bdea883c0e1273d7d7f8a810c1db0c7afc1b1713a04918239c7f6b04375e8955` |
| 20 | `core/data/src/androidTest/assets/dense/encoder-parity-vectors.f32` | 重出 | `27fb7be89c783958c6ffba7f7c29fc1663815ef6a762cb316aa685784860c913` |
| 21 | `core/data/src/androidTest/assets/dense/encoder-parity.json` | 重出 | `7fea52e85270a815b45165e564cb073e06a137d5e520924cf3a71658ff71ecf9` |
| 22 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ProductionLexicalLegExportTest.kt` | 仅当词面计数变化时重钉 | `aa96d93253fab39e8fd2a32b21dd248897e29862f72f3b70c1c6ddec40b816b6`；当前 `V1_D1_MAIN=0.7906976744186046`、`V1_D1_MRR=0.6206718346253232`、hits=102 |
| 23 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage1LexicalLabTest.kt` | 仅当词面计数变化时重钉 | `1d774ffbda3ec1c70beac906d107bc07442298017ae6243dc1ea4f853a0b067b`；当前 v1 旧生产形 main=`0.5736434108527132`（74/129）、MRR=`0.15943152454780354`、hits=74 |
| 24 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/Stage2LexicalScoresExportTest.kt` | 仅当词面计数变化时重钉 | `19a94984f48eeb377e3172eedd0cde42dd903ea76d4d988636e59a01c11449f6`；当前 `ARM_A_MAIN=0.7829457364341085`、`ARM_A_MRR=0.6246770025839794`、hits=101 |
| 25 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/dense/DenseRecallRerankerTest.kt` | 仅当候选映射变化时重钉 | `680c5e92294c100bda8652dc7cf033d7ddf5777e6af8cacf6b1704aa120ee091`；当前候选总数=1,075 |
| 26 | `build/stage3-device-expectation.json` | 重出（build 产物，备份） | `a5e2d46eb03d97c2f4f50317cd6a7993a3b3c4d93fa28106b5953137127a5632` |

**B. 零触碰（写死）**

- 判官两件（设备 `c2280255…`、JVM `62c232fc…`）与零漂移镜像（`8c2a2ef0…`）：**零改**。
- 金标 `eabef102…`（129/24）：**零改**。
- 25 个 sidecar 的**材料正文**：**零写**；promote 可能重写文件，内容预期不变；回滚以备份的 pre-sha 为准（聚合 `ed1c6d4f…`）。
- 其他节点别名：只允许 `等和线` 变化；`alias_map` 应恰好 +2 行且全部属于 `MATH/等和线`。出现任何其他节点变化 = 缺陷，停止并回滚。
- 模型资产（ONNX 权重、tokenizer vocab）与 `tools/dense_build/dense_asset.py` / `gen_device_parity_fixture.py` 的模型档：**零改**（脚本可原样运行，不改模型配置）。
- 本预注册之外的其他 docs/线（含工作树中数百项他线改动）：**零触碰**。

### 5.2 写前备份与 pre-sha

- 写前对 §5.1 A 表所有 tracked 文件做字节备份到 `build/agent-batch22/backup/`（或本批执行目录），并记录 `sha256sum` 全表；`build/` 产物（`stage3-device-expectation.json`、`production-lexical-leg.tsv`）同样备份。
- 写前运行 `git status --porcelain -- <§5.1 A 表路径>`；本代理 2026-10-26 实测为**空**（全部 CLEAN，git 即备份）。执行轮若发现非空，先登记脏态并单独裁，不得覆盖他线改动。
- 25 sidecar 逐文件 pre-sha 见附录 A；聚合 sha 的构造 = 按 `v2-01…v2-25` 顺序以 `\n` 连接 `"<basename> <sha256>"`（**行间换行、末尾无换行**）后再 sha256（可复算；构造不同会得到不同聚合值）。

### 5.3 回滚 / 采纳程序

**不采纳（回滚）**

1. 对 §5.1 A 表 tracked 文件逐字恢复 pre-sha 字节（CLEAN 文件用 `git checkout --`；脏文件用备份）；`build/` 产物用备份覆盖。
2. 核验：manifest `version=20` / `contentVersion=ebf477816c318704`；主包 `298f4c0c…`；25 sidecar 聚合 `ed1c6d4f…`；`alias_exceptions` 33 行 / `43c6b61d…`；`alias_map` 37,551 行 / `90486469…`；`alias_dropped` 22,559 行 / `84040e23…`；`.vec` `0536d0d1…`（44,041）；`.vec.json` `4c911fc3…`；`DenseRecallAssembly.VECTOR_ASSET_SHA256=0536d0d1…`；判官/金标 sha 不变。
3. 核验 `rebind_materials` 报告仍为 `改绑 0；悬空绑定 0`；材料总数 64,973。
4. 复跑装置门 `:core:data:compileDebugUnitTestKotlin` exit=0；若回滚后仍红，登记为他线/基线问题，不归因本批。
5. 在 §7 写明回滚原因与逐文件 sha 核验结果；不得把“已回滚”写成“未采纳但保留改动”。

**采纳**

1. 保留 v21 包、新 `.vec` 与新账本；按实测重钉 `DenseRecallAssembly.VECTOR_ASSET_SHA256`、`.vec.json` `vectorCount/packSha256`、受影响的测试锚点（只按实测重钉，不放宽容差/断言）。
2. 在代码/账本注释里写明新旧值与量源（提交号/日志路径），保留 §4 的 pre-sha 表作为审计链。
3. 在本预注册 §7 登记新 stateAfter；A1 预注册不代改（其 §1–§6 与 §7 均保持原样；如需引用新基线，另写维护记录）。
4. 若采纳后任何后续批次复用本批别名，必须另立预注册；不得用本批的非劣口径替代机制臂的严格更高。

## 6. 预期与边界（不承诺）

1. **别名增益不确定**：机制假设 = 材料记号与题面词面同域（`k` / `和为` / `等和线` / `系数和`）。但冻结金标中 `expectedSlug=等和线` 的题实际问法是 `x+y=1/2` 时点 P 的轨迹，**不含两条别名串**；本代理按 `fromQuestion` 等价翻译实算其生产查询特征与两条别名的 2–3 字 gram 交集为 **0**（未运行 Kotlin 生产代码）。因此词面腿对该题不可能直接得分；当前 A1 口径下 dense 腿只重排词面召回集（成员不变；批次21 并集臂已回滚），该题在 A1 fused/lexical 均为 `absent`（`device-fused-misses.txt` / `device-lexical-misses.txt` 均 `rank=absent`），故 dense 也无法把它带进 top5。**按当前口径，该题没有已知的翻转入径；不承诺等和线翻转，也不承诺任何分数。**
2. **两条别名的角色不同**：第 1 条 `其和为 k 的等和线` 是正文加粗 span，首字「其」为材料标题所无，是“正文派生、非金标题反推”的干净样本；第 2 条 `k 的取值影响等和线的位置` 是正文命题式加粗 span，语义更宽，是两条里风险更高的一条。两条按 S2 规则均可登记，但第 2 条不是名词性术语；若出现任何章回退/索引截断，**整批回滚**，不做“只留一条”的事后挑选。
3. **材料收益 = 口径完成度**：材料轨零写，收益是薄料节点正确绑定、全包 0 待执行 REBIND、0 未绑材料、无 SUPPORTING 缺口的登记闭环，不是检索分数；缺的“电路元器件识别”半边仍在 `电路中的基本概念`，本批不补。blocked 记「本批无材料写入」。
4. **索引截断风险**：`等和线` 节点已有 23 条别名、边界较长；节点侧特征预算为 16 片段 / 192 特征（`KnowledgeSearchFeatureExtractor.kt:7-16,71-84`），当前 62% 节点片段数超 16，别名只有一部分进入生产索引（KD-24）。新增 2 条别名**可能不增加净特征，反而挤掉既有片段**。这是主集/逐章非劣判据要防的主要失败。
5. **单点边界**：`等和线` 只报不判；即使从 `absent` 变 hit，也不据此采纳；即使仍 `absent`，也不据此否决（除非主集 <117/129 或某章严格更低）。不得因为单点结果改别名/阈值/参数。
6. **不承诺任何具体分数（例如 117→118 或 MRR 提升）**：本批只承诺按 §2 四条判据求值；任何增益只报告，不作为“超出预期”的采纳依据。
7. **装置边界**：设备读数为 emulator，不是真机；JVM 仅作零漂移/保真，不判分；离线 expectation 与设备 MRR 存在已登记 ULP 两源（`…229` vs `…232`），本批不改口径。
8. **无第二评测集**：129 题 / 24 章是唯一冻结金标；不外推泛化，不新增/改写题目。内容更新不改变金标 sha。
9. **版本边界**：v21 只用于本批；promote 任何一环红则保持 v20，不产生半包/半升级。材料轨维护态包零写；别名轨是唯一包写入面。
10. **时间与范围**：本预注册只冻结 2 条别名 + 材料边界登记；候选表其余 4 行、其他节点、其他材料、判官、金标、模型资产均不在本批写面。

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
  build/agent-batch19/a1/device-metrics.txt

# 3) 节点/材料/绑定统计（3,245 节点 / 64,973 材料 / 0 未绑 / 全 PRIMARY）
python - <<'PY'
import json,glob
pack=json.load(open('core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json',encoding='utf-8'))
ms=[]
for p in glob.glob('core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-*.json'):
    if 'index' in p: continue
    ms += json.load(open(p,encoding='utf-8'))['materials']
print('materials',len(ms),'unbound',sum(1 for m in ms if not m.get('bindings')))
print('roles',{r:sum(1 for m in ms for b in m['bindings'] if b['role']==r) for r in ('PRIMARY','SUPPORTING')})
PY

# 4) 无待执行 REBIND（报告模式，不写）
PYTHONPATH=tools python -m kb_build.rebind_materials

# 5) 装置门（写轮实测 exit=0）
./gradlew :core:data:compileDebugUnitTestKotlin --console=plain

# 6) 金标 129/24
python -c "import json,collections;q=json.load(open('tools/kb_coverage/tables/golden_queries_v2.json',encoding='utf-8'));print(len(q),len({x['chapter'] for x in q}))"

# 7) 两条别名的形状门（预期两个 None）
PYTHONPATH=tools python -c "from kb_build.rebuild_aliases import title_usable; print(title_usable('其和为 k 的等和线'), title_usable('k 的取值影响等和线的位置'))"
```

---

## 7. 结果节

> 填写口径（批次22 收束员，2026-10-26）：§1–§6 一字未动。数字三类标注——**执行轮产物实读**（`build/agent-batch22/`、`build/agent-batch22/v21/` 执行段落盘）、**本代理实跑**（收束轮只读复核命令，逐处注明）、**预注册冻结值**（§4）。裁定依据 = `build/agent-batch22/v21-final.md`（v21 收束结果：**adopted**）。

### 7.1 出数时间线与装置门（执行轮产物实读）

- 装置门/构建：`:core:data:compileDebugUnitTestKotlin` 与 `:core:data:assembleDebugAndroidTest` 均 exit=0；APK 内嵌 `.vec` = `79a22270…`，重装后 `POST_INSTALL_SAMEGEN=MATCH`（`v21/step2_build.log` 的 `apk_vec_sha`；`v21/step3_run_device.log:4,13-15`；`v21/post-install-samegen.txt:1-5`）。注：04:52 的 `apk-samegen-check.log` 是**重装前**的同代检查，当时本机/已装 APK 仍为上一代 `0536d0d1…`，其 `SAMEGEN_VERDICT=MISMATCH_LOCAL+MISMATCH_INSTALLED` 是预期前态，不是本轮最终装置结论；最终以 post-install 为准。过程偏离（他线 QuickJS 文件一度致 androidTest 编译红、后自愈；未启用任何排除预案）见 `build/agent-batch22/v21/deviation.md`。
- 设备：`am instrument` → **OK (1 test)**、`am instrument exit=0`、`test_result=OK`；`POST_INSTALL_SAMEGEN=MATCH`，`local_apk_vec_sha = installed_apk_vec_sha = worktree_vec_sha = 79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446`（`v21/step3_run_device.log`、`v21/device-test-result.txt`）。
- JVM：`BUILD SUCCESSFUL in 16s`、`gradle exit=0`（`v21/step5_run_jvm.log`）；dense 单测轮 `BUILD SUCCESSFUL` / `EXIT=0`（`build/agent-batch22/dense-jvm-tests.log`）。
- 零漂移/显著性：`v21/zero-drift.json`、`v21/significance-{fused,lexical}-v21-vs-A1.json`（执行轮落盘；收束轮复跑见 §7.4/§7.5）。

### 7.2 主判读读数（设备融合臂，执行轮产物实读）

- 融合主集 **Recall@5 = 0.9069767441860465（117/129）**、**MRR = 0.7322997416020672**（`v21/device-metrics.txt:3-5`）；与 A1 冻结基线 **117/129、MRR 0.7322997416020672** 逐位持平（`build/agent-batch19/a1/device-metrics.txt:3-5`）。
- 逐章 24 章分布（`v21/device-metrics.txt:7-30`）：**24/24 与 A1 全等**——严格更低 **0 章**、严格更高 **0 章**、相等 **24 章**（收束轮实跑 `diff` 退出码 0；另以 `significance-fused-v21-vs-A1.json` 的 `b.per_chapter` 对 A1 `b.per_chapter` 复算 lower=0 / higher=0 / equal=24）。
- 墙钟：融合 **p50=40ms / p95=57ms / max=164ms**，`overBudgetSamples=0`（预算 150/250ms；`v21/device-metrics.txt:2`；A1 历史读数为 38/51/84ms，代际差为 emulator 波动，未超预算）。

### 7.3 对照臂与 JVM 保真（执行轮产物实读）

- 设备词面臂 **102/129 = 0.7906976744186046**、MRR **0.6206718346253232**、p50=7 / p95=14 / max=20ms（`v21/device-metrics.txt:32-35`）；与 A1 逐位持平。
- JVM 保真（不判分）：融合 117/129、MRR 0.7322997416020672；词面 102/129、MRR 0.6206718346253232（`v21/jvm-fused-metrics.txt`、`v21/jvm-metrics.txt`）。JVM 融合档头 `totalFeatureRows` 540,668→540,633 随 v21 索引变化，指标值未变。

### 7.4 零漂移门（§2 口径：同路对称差必须 0；本代理实跑）

命令：

```bash
python build/agent-batch19/a1/zero_drift.py \
  --device-lexical build/agent-batch22/v21/device-lexical-misses.txt \
  --device-fused   build/agent-batch22/v21/device-fused-misses.txt \
  --jvm-lexical    build/agent-batch22/v21/jvm-lexical-misses.txt \
  --jvm-fused      build/agent-batch22/v21/jvm-fused-misses.txt \
  --json-out       build/agent-batch22/v21/zero-drift-recheck.json
```

- 同路词面 **symdiff=0**（27/27/common 27）；同路融合 **symdiff=0**（12/12/common 12）；末行 **`verdict: lexical=PASS fused=PASS`**、`zeroDriftBothRoutes=true`，exit=0。
- 复跑 JSON 与执行轮 `v21/zero-drift.json` **解析后逐字段相同**；intel-only 边（cross-route 15、device legs 15、vs A0 13/17）与执行轮一致，非门对象。

### 7.5 显著性（§2 参数：按章 cluster 配对 bootstrap `--reps 20000 --seed 0`；本代理实跑）

命令（A=v21、B=A1；双路各跑一次，输出至 `v21/significance-*-recheck.json`）：

```bash
python tools/kb_coverage/retrieval_significance.py \
  --golden tools/kb_coverage/tables/golden_queries_v2.json \
  --misses build/agent-batch22/v21/device-fused-misses.txt \
  --misses-b build/agent-batch19/a1/device-fused-misses.txt \
  --reps 20000 --seed 0 --json
# lexical 同式（device-lexical-misses.txt ↔ a1/device-lexical-misses.txt）
```

- 融合：`paired.delta_recall_b_minus_a = 0.0`、`ci95_chapter = [0.0, 0.0]`、`p_two_sided = 1.0`；A/B recall 均 0.9069767441860465。
- 词面：同为零差、CI [0.0, 0.0]、p=1.0；A/B recall 均 0.7906976744186046。
- 方向说明：落盘字段是 **B−A**；本命令按预注册传 A=v21、B=A1，故 0.0 同时等于 A−B 与 B−A。判据 4 只要求 **95% CI 上界 ≥ 0**，实测上界 **0.0 ≥ 0**。
- 复跑 JSON 与执行轮 `v21/significance-{fused,lexical}-v21-vs-A1.json` **解析后逐字段相同**。两臂 MISS 账本逐字节相同（`diff -q` 退出码 0），零差是预期结果，不主张增益。

### 7.6 四条判据逐条（§2.2）

| # | 规则 | 求值 | 依据 |
|---|---|---|---|
| 1 | 主集非劣：≥117/129 | **成立**：117/129 = 0.9069767441860465，与 A1 持平 | `v21/device-metrics.txt:3-5`；A1 `a1/device-metrics.txt:3-5` |
| 2 | 24 章逐章不劣 | **成立**：0 章严格更低；24/24 全等 | `v21/device-metrics.txt:7-30` vs `a1/device-metrics.txt:7-30`（收束轮 diff 退出码 0）；`significance-fused-v21-vs-A1.json` `b.per_chapter` 复算 |
| 3 | 零漂移同路对称差 0 | **成立**：lexical 0（27/27/27）、fused 0（12/12/12）；PASS/PASS | `v21/zero-drift.json` + 收束轮 `zero_drift.py` 复跑 |
| 4 | 负向守卫 CI 上界 ≥0 | **成立**：融合/词面均 delta 0.0、CI [0.0, 0.0]、p=1.0；上界 0.0 ≥ 0 | `v21/significance-{fused,lexical}-v21-vs-A1.json` + 收束轮复跑 |

### 7.7 采纳执行与 sha 核验（执行轮产物实读 + 本代理实跑）

- 别名写入：应用 35、冲突 0、拒绝 0、别名 40,796→40,798、变更节点 1（`build/agent-batch22/content-write.out.txt`）；exceptions 相对 pre 备份 **+2 行**、alias_map **+2 行**，全部为 `MATH,等和线`（收束轮 `diff` 实跑）。
- promote：28 文件逐行 `OK`、gates/consistency/roundtrip/manifest 全绿、`version → 21（压平 0 条链）`（`build/agent-batch22/promote-run.log:30-37`）。
- dense 重打：`.vec` **44,043 行**（3,245 canonical + 40,798 alias）、**26,041,347 B**、sha **`79a22270b54180f6ba5346a4f56d87eb16e5f335107ac9877cbb142fd4515446`**；旁车 `68ed524a…`、`vectorCount=44,043`、`aliasVectors=40,798`、`packSha256=a7192484…`、`bytes=26,041,347`（`build/agent-batch22/dense.log:223-224`；收束轮 `sha256sum`/JSON 读取复核）。
- 钉值与锚：`DenseRecallAssembly.kt:108` = `79a22270…`（文件 sha `a32fa8e7…`）；`DenseRecallRerankerTest.kt:116-122` 候选 `50→190`、总数 `1,074`（文件 sha `807d3a50…`）；`model-manifest.json` sha `8b3a7b24…`；容差/断言未放宽。
- 零改：金标 `eabef102…`；设备判官 `c2280255…`；JVM 判官 `62c232fc…`；零漂移镜像 `8c2a2ef0…`；25 sidecar 聚合 `ed1c6d4f…`（与 §4 pre 值相同）；材料 64,973 / 未绑 0 / 全 PRIMARY / 多绑定 0。
- 触碰清单：`build/agent-batch22/content-touched-files.txt` 46 条 `NORMAL` 行，收束轮逐条核验 **46/46** 命中记录 post-sha（32 全值 + 14 缩写前缀），0 失配。

### 7.8 单点与边界

- `等和线` 单点**仍为 absent**：`v21/device-fused-misses.txt:8`、`v21/device-lexical-misses.txt:13`、`v21/jvm-fused-misses.txt:8`、`v21/jvm-lexical-misses.txt:13`。按 §2.4 **只报不判**；未据此改别名、阈值或参数。
- 本批为内容更新非劣裁定，**不承诺任何分数提升**；24 章全等不是增益证据。后续若把本批别名当机制臂，仍须另立预注册并走“严格更高”三规则（§5.3 第 4 条）。
- 设备读数为 emulator，非真机；JVM 仅作零漂移/保真；离线 expectation 的 lexical MRR 两源 ULP 差异（`…229` vs `…232`）沿用 A1 口径。
- 收束轮**未重跑** Gradle 全量、设备 `am instrument`、JVM 判官；上述设备/JVM 读数为执行轮产物实读。收束轮实跑仅限只读复核：`zero_drift.py`、`retrieval_significance.py`、`sha256sum`、Python 统计与 `diff`。
- 工作树仍有其他线在飞的 **661** 条 porcelain 项（收束末次检查 `git status --porcelain | wc -l` = 661，HEAD `062e18f0`）；本批未触碰、未回滚其他线文件。收束窗口内其他线提交后，v21 关键 sha 已重新核验仍与 §7.7 一致。

### 7.9 收口结论与 stateAfter

- **裁定：adopted=true**。四条判据全部通过；未执行任何回滚，v21 包、新 `.vec`、新别名账本、dense 夹具与重钉锚全部保留。
- **stateAfter：v21（contentVersion `9f9e99ceec47cf8c`，主包 sha `a7192484…`）+ 新 `.vec` `79a22270…`（44,043 vectors；旁车 `68ed524a…`）**。即本预注册采纳程序第 3 条所要求的新基线登记。
- 收束结果详表：`build/agent-batch22/v21-final.md`（本行落盘于收束轮）；执行轮物证目录：`build/agent-batch22/v21/`、`build/agent-batch22/`。

### 7.10 文书轮只读复核（2026-10-26；§1–§6 与 §7.1–§7.9 未改）

本节只追加复核记录，不改变 §7.1–§7.9 的收束结论。复核命令均为只读；新增 JSON 只落在 `build/agent-batch22/v21/*-doc-recheck.json`，未覆盖执行轮原文件。

| 复核项 | 命令（摘要） | 结果 |
|---|---|---|
| 基线身份 | `python` 读 manifest / `.vec.json` / csv / 25 sidecar；`sha256sum` | `version=21`、`contentVersion=9f9e99ceec47cf8c`；主包 `a7192484…`；`.vec` `79a22270…`（26,041,347 B）；旁车 `68ed524a…`；`alias_exceptions=35`、`alias_map=37,553`、`alias_dropped=22,559` 数据行；25 sidecar 聚合 `ed1c6d4f…` |
| 判官/金标 | `sha256sum` 设备判官 / JVM 判官 / 镜像 / 金标 | `c2280255…` / `62c232fc…` / `8c2a2ef0…` / `eabef102…`（两件金标同 sha） |
| 逐章 24 行 | `diff <(sed -n '7,30p' v21/device-metrics.txt \| sed 's/^.*System.out: //') <(同 A1)` | `rc=0`，24/24 行逐字相同 |
| 四路 MISS | `diff -q`（device/JVM × fused/lexical vs A1） | 四个 `rc=0`；`device-fused/lexical-misses.txt` 与 A1 同 sha |
| 零漂移 | `python build/agent-batch19/a1/zero_drift.py ... --json-out build/agent-batch22/v21/zero-drift-doc-recheck.json` | lexical **27/27/common 27、symdiff 0**；fused **12/12/common 12、symdiff 0**；`lexical=PASS fused=PASS`、`EXIT=0`；复核 JSON 与执行轮 `zero-drift.json` **解析后完全相同** |
| 显著性 | `python tools/kb_coverage/retrieval_significance.py ... --reps 20000 --seed 0 --json` ×2（A=v21、B=A1） | 两次 `EXIT=0`；融合/词面均 `delta_recall_b_minus_a=0.0`、`ci95_chapter=[0.0,0.0]`、`p_two_sided=1.0`；复核 JSON 与执行轮 `significance-*-v21-vs-A1.json` **解析后完全相同** |
| 46 条写面 | 逐条 `NORMAL` post-sha 复核脚本 | **46/46 命中、0 失配** |
| 材料统计 | `python` 逐 sidecar | 64,973 / 未绑 0 / PRIMARY 64,973 / SUPPORTING 0 / 多绑定 0 |
| 工作树 | `git status --porcelain \| wc -l`；`git rev-parse HEAD` | 文书轮开始 **672** 项，落盘后复查 **674**（含他线波动）；HEAD `062e18f0…` == origin/main |

**未在文书轮重跑**：Gradle 任何任务、设备 `am instrument`、JVM 判官、dense 链脚本；这些读数仍为执行轮产物实读。**裁定不变：adopted=true；未回滚。**
