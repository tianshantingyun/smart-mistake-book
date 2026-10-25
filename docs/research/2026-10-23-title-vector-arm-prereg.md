# 标题向量臂（材料 title 入 dense 资产）· 预注册（2026-10-23）

> 出数前冻结；事后 §1–§6 一字不动，只填 §7。立项：批次18 检索循环第三杠杆「材料标题向量入 dense」（`docs/kb-batch18-2026-10-23.md:61,90`，当轮因 `stopStreak=2` 未执行）；实算与冻结：**2026-10-25 读包现算**（python 读主包 + 25 sidecar、`sha256sum` 现算、Gradle 门现跑）。体例参照：`docs/research/2026-10-22-d3-round3-a1-prereg.md`。

## 1. 臂定义（含实算）

**臂 = 把当前包全部教学材料的 `title` 字段作为新向量行入 `bge-small-zh-int8.vec`，其余一切不动。**

- 来源与行 id：25 个 `moe-2025-teaching-support-v2-XX.json`（`-index.json` 序 01→25）的全部 materials；每条材料恰有 1 个 `PRIMARY` 绑定（实测 64,973/64,973）⇒ 行 id = 该绑定的原子节点 `knowledgeNodeId`，与 canonical/alias 行同构（`DenseVectorAsset.subjectKeyOf` 可解析；同节点行必须连续——`DenseVectorAsset.kt:122-127` 的 require 原样适用，不满足即资产不可用）。
- 去重：跳过空标题（实测 0 条，规则仍冻结）；按**（节点, 标题原文）精确去重**，保序（`dict.fromkeys`，与 `dense_asset.py:351` 的 alias 去重同法）；标题行内部序 = sidecar index 序 × 文件内材料序。
- 行序：每节点 `[节点名行(canonical), 别名行…, 标题行…]`——标题行追加在同节点别名行之后；节点分 = 节点全部向量 cosine 的 **max** 语义不变（`DenseVectorAsset.kt:51-65`）。
- 量化与编码口径不变：int8 每向量对称 scale（`scale[r]=max|v_r|/127`，`dense_asset.py:25-31,376-385`）；标题用与 canonical/alias 同一 doc 编码（docPrefix=null、maxLen=128 截断、CLS+L2 归一）。
- **运行时零改动**：`core/data` 的 `DenseVectorAsset.kt` 是权威读实现（格式权威定义在 `tools/dense_build/dense_asset.py`），按行数与连续段泛化，一字不改；唯一随资产变的运行时值 = `VECTOR_ASSET_SHA256` 钉值（`DenseRecallAssembly.kt:107`，重钉 ≠ 改逻辑）。core/database 零改动（dense-union 已回滚，`RoomKnowledgeBaseStore` 现无 union 分支）。本臂**不写包、不晋升版本**（包保持 v20 贯穿全程）。
- 离线侧唯一语义扩展点 = `dense_asset.atomic_layout`（`dense_asset.py:323-359`）增发 `kind="title"` 行；`export_bge_int8.py:487` 的 surfaces 与打包侧条数校验随布局现取，stage3 期望值与五份 dense 夹具随链重出。

**实算（2026-10-25）：**

| 项 | 值 |
|---|---|
| 材料总数（25 sidecar 合计；contentId 跨文件无重复） | **64,973** |
| 非空标题数 | **64,973**（空标题 0） |
| 精确去重后新增行数 | **60,918**（同键重复剔除 4,055——同点多材料同题所致） |
| 资产行数 | 44,041 → **104,959**（= 3,245 canonical + 40,796 alias + 60,918 title；行数比 **2.3832**） |
| 文件字节 | 26,040,207 → **62,062,649**（+36,022,442 = 矩阵 31,190,016 + scales 243,672 + ids 块 4,588,754） |
| 运行时内存增量（matrix+scales，无逐行向量对象） | **+31,433,688 B ≈ +30.0 MiB**（21.7 → 51.6 MiB） |
| 标题长度（字符） | min 1（'功'）/ 中位 17 / max 183 |
| 节点覆盖 | 60,918 行全部落在当前包 3,245 原子节点布局内（越界 0、retired 悬挂 0）；3,245 节点全部获得 ≥1 条标题行（单点最多 143 行：三角函数的图象与性质） |

- 边界事实：60,918 行中 **38,377 行**与同节点既有 canonical/alias 表面**逐字相同**（别名库本就大量派生自材料标题）——这批是等值重复行，对节点分（max）数值零影响；净新增表面 = **22,541** 行。按本臂冻结定义仍入资产（去重键只有（节点, 标题原文））；是否追加「对既有表面去重」属臂定义变更，不在本预注册内擅自扩大。
- **扫描延迟线性外推（预期，非实测）**：v20 实测融合路由 p50=38ms / p95=51ms（`build/agent-batch19/a1/device-metrics.txt:2`，44,041 行，budget 150/250，overBudgetSamples=0）；词面路由 p50=7ms / p95=13ms（`:32`，不含 dense）。以「融合−词面」作 dense 段上界（p50 ≤31ms / p95 ≤38ms）按行数比 2.3832 线性外推：dense 段 p50 ≤74ms / p95 ≤91ms ⇒ 融合路由外推 **p50 ≈81ms / p95 ≈104ms**；整路直接线性外推（上上限）**p50 ≈91ms / p95 ≈122ms**。两口径均远低于 p95 250ms 预算；外推不替代实测。

## 2. 判读

- 主判读：**设备金标融合臂**（真 SQL + LiteRT + `DenseFusion`，B512 召回 + 重排 + top5）。基线 = **A1（v20，已采纳）= 117/129 = 0.9069767441860465**，MRR 0.7322997416020672（`build/agent-batch19/a1/device-metrics.txt:3-5`；采纳依据 `build/agent-batch19/a1-final.md` 结论 adopted）。
- 对照：设备词面臂（A1 = 102/129 = 0.7907，`device-metrics.txt:33`）；JVM 双路仅作装置保真，不判分。
- **三采纳规则（与 A1 相同）**：①主集严格更高（>117/129，即 ≥118/129）；②24 章逐章不劣（任一章严格更低即不采纳；A1 逐章基线 = `build/agent-batch19/a1/significance-fused-vs-A0.json` 的 `b.per_chapter`，与 `device-metrics.txt:6-30` 同源）；③负向守卫 CI 上界 ≥0。
- **零漂移**：device↔JVM 同路 MISS 题面集合对称差必须 0；非 0 = 装置缺陷，该臂不判读（门定义同批次19 `build/agent-batch19/a1/zero_drift.py`）。
- 显著性：按章 cluster 配对 bootstrap `tools/kb_coverage/retrieval_significance.py --reps 20000 --seed 0`；A=标题臂、B=A1 基线（同 A1 预注册参数）；结果节须显式记差值方向（工具落盘 `delta_recall_b_minus_a` + `ci95_chapter`）。
- **另加预算门**：设备融合路由 **p95 ≤250ms**（p50 ≤150ms 同现有门）且 overBudgetSamples=0；行数外推先验 p95 ≈104–122ms（§1），以实测为准。
- 词面不变式：本臂不动词面腿（`alias_map.csv`/生产 TSV 均不改）⇒ 设备词面臂主集预期与 A1 逐位同（102/129）；词面臂漂移按装置缺陷处理（与零漂移门同精神），该臂不判读。

## 3. 装置门（写死，未过不得启动）

`:core:data:compileDebugUnitTestKotlin` 必须为 **0**。2026-10-25 实测 **0**（`./gradlew.bat :core:data:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL，GRADLE_EXIT=0，UP-TO-DATE；日志 `build/agent-batch19/title-prereg-gate.log`。上次出数读数同 0：批次19 A1，`build/agent-batch19/a1-gate.log`）。门未过时：**不写包、不跑设备、不判读**。历史红因均为他线在飞件（批次17 `core/data/.../capture/ExplicitRedrawService.kt:65`；批次19 起跑 `core/domain PaperAnswerKeyChain.kt` 块注释未闭合，见 `build/agent-batch19/a1/judge-files-record.md`）——门红先归因，不越门施工。

## 4. 冻结输入（实算，2026-10-25）

| 项 | 值 |
|---|---|
| 包 | `moe-2025-update-manifest.json` version=**20**、contentVersion=**ebf477816c318704**；主包 sha `298f4c0c41519d5116b8d35340edd6f7b4e073573950b4eb0a13e01b32822150`（A1 已采纳 ⇒ **v20 为基线**；`build/agent-batch19/a1-final.md`；本臂不写包） |
| dense | `.vec` sha **`0536d0d1cda99bd5614bc94d9176628d7ce999938db1c289b3b44600ae13b23a`**（44,041 行 = 3,245 + 40,796）；`DenseRecallAssembly.kt:107` 钉值同值；三方同源门 = `DenseAssetProvenanceTest`（常量↔旁车↔实文件） |
| 金标 | `tools/kb_coverage/tables/golden_queries_v2.json` sha **`eabef10228916d46…`**（129 条 / 24 章；2026-10-25 现算复核 = `dense_asset.py:80` 冻结值一致） |
| 基线账本 | A0（v19）：`build/agent-batch15/d3/a0-device-{fused,lexical}-misses.txt`、`a0-jvm-{fused,lexical}-misses.txt`（100/129、89/129）；**A1（v20，基线）**：`build/agent-batch19/a1/{device,jvm}-{fused,lexical}-misses.txt` + `device-metrics.txt` + `zero-drift.json` + `significance-fused-vs-A0.{json,txt}`（117/129、102/129） |
| 标题来源 | 25 个 `core/data/src/main/resources/knowledge/moe-2025-teaching-support-v2-XX.json` + `-index.json`（64,973 条 / 全部恰 1 个 PRIMARY 绑定 / 空标题 0） |
| 判官两件（现状冻结） | 设备 `core/data/src/androidTest/kotlin/.../knowledge/GoldenRetrievalInstrumentedTest.kt` sha `c2280255a9b0db2b…`（工作树干净）；JVM `core/data/src/test/kotlin/.../knowledge/GoldenRetrievalJvmTest.kt` sha `62c232fcbf3ffd11…`（他线未提交修改态 3+/3−，与批次19 A1 出数时同状态）。臂内不改判官、不改判据；开工时复验两 sha，漂移则停、重录判官状态并核对判据未变后才可出数 |
| 判分/门工具 | `tools/kb_coverage/retrieval_significance.py` sha `061f7a8aa3f670d5…`；零漂移门 `build/agent-batch19/a1/zero_drift.py` |
| 工具链现状 | `dense_asset.py` `2dafbfa994a3c664…`、`pack_dense_asset.py` `50a754af30f2cfe4…`、`export_bge_int8.py` / `gen_device_parity_fixture.py` / `model-manifest.json`（`7f1f6e8d868428a4…`）均为工作树在飞修改态——既有改动属批次13 遗留（他臂），本臂只在其上增量、不代改不回滚 |

## 5. 回滚

- 写盘前记录 pre-sha 清单：`.vec`、`.vec.json`、`DenseRecallAssembly.kt`、`DenseRecallRerankerTest.kt`、`ProductionLexicalLegExportTest.kt`、dense 夹具 9 件（`core/data/src/test/resources/dense/`：dense-scan-reference、dense-device-order-reference、golden-query-vectors.i8、tokenizer-parity-{cases,stages,summary}；`core/data/src/androidTest/assets/dense/`：encoder-parity-{cases.tsv,vectors.f32,json}）；包主件 / 25 sidecar / index / update-manifest 预期**不变**（本臂不写包），仍记录 sha 以便对账。字节备份进本臂自建 `backup/` 目录（同 `build/agent-batch19/a1/backup/` 惯例）。
- 不采纳：`.vec`/`.vec.json`/夹具/钉值逐文件 sha256 对账还原到 `0536d0d1…` 基线。tracked 文件优先 `backup/` 字节还原；`git checkout --` 整文件还原**仅限本臂独占且写前干净**的文件。**排除项**：`tools/dense_build/dense_asset.py`、`tools/dense_build/model-manifest.json` 的既有改动（批次13 遗留，另含同态在飞的 `export_bge_int8.py`、`gen_device_parity_fixture.py`）不属本臂——涉及这四件一律用「本臂写前备份做差量还原」，绝不整文件 checkout。
- 采纳：按实测重钉 `VECTOR_ASSET_SHA256`（代际注释按既有惯例追加「上一代：批次19（v20）44,041 行 / `0536d0d1…`」），旁车与五份夹具随链重出；顺带把 `DenseVectorAsset.kt:15,29` 头注释里的历史行数（41,193 / 3,866）按实测刷新——纯注释，非逻辑改动。

## 6. 预期与边界（不承诺）

- **可达面**：标题行只把 dense 腿的节点分 max 扩大到含标题向量；当前融合形态下 dense 只重排词面候选集（union 已回滚）⇒ 本臂结构上只能影响**已在词面 512 候选域内**的节点的排序。A1 基线 12 条 fused MISS（`build/agent-batch19/a1/device-fused-misses.txt`）= **9 条域内**（rank 7–51）+ **3 条 absent**（等和线、活结与死结模型、力的分解）。9 条域内是本臂理论可达面；3 条 absent 本臂救不了（不在候选域，属 dense-union 候选域扩张或内容侧施工）。**不承诺救回任何一条**——9 条的题面与标题贴近度未离线预评，增量上限 < 9/129。
- **与 dense-union 的关系**：标题向量是**资产侧行扩充**——不改候选域结构、不改 core/database 契约、融合分数口径不变（只是 dense 腿节点分的输入行变多）；**dense-union 才是候选域扩张**（词面 top-512 ∪ dense top-K，跨线改 core/database；批次18 lever-1 已实测一轮 100/129，未采纳并回滚，`docs/kb-batch18-2026-10-23.md` §3）。两臂独立、一条臂一次变更；dense-union 预注册 §3 的「必须先有 A1 之后的 A0」前提已由批次19 A1 采纳满足。
- 38,377 行等值重复行（§1）对节点分零影响 ⇒ 本臂的有效信息增量只有 22,541 条新表面；若采纳评估时读数无变化，先核这一层再谈「臂失效」。
- 代价（实算）：包体 +34.4 MiB、运行时数组 +30.0 MiB（无逐行对象，`DenseVectorAsset` 仍是一个 ByteArray + 一个 FloatArray）；maxLen=128 截断对超窗标题生效（既有窗口口径不改；标题 token 级分布不在本预注册内预跑，随臂执行实测）。收益兑现与否由 §2 判读，本节不预设方向。

## 7. 结果节

> 填写口径（批次19 收口文书员，2026-10-25）：§1–§6 一字未动。数字三类标注——**既有产物实读**（`build/agent-batch19/title*/`、`title-final.md` 等臂执行轮落盘产物）、**本代理实跑**（收口轮命令与脚本，逐处注明）、**执行轮实跑**（臂执行员日志）。**臂结论 = rolled-back（不判读 → 逐字回滚）**，裁定依据 = `build/agent-batch19/title-final.md`。

### 7.1 装置门复跑读数（执行轮实跑）

- 02:02 预注册门实测 **0**（`build/agent-batch19/title-prereg-gate.log`，BUILD SUCCESSFUL）；02:04 复核（`title-gate-recheck.log`）；03:05 出数前预探 **GREEN exit=0**（`title-gate-probe.log`）；03:09 step1 门 + `:core:data:assembleDebugAndroidTest` exit=0（`title/judge-files-record.md` §时间线）。
- APK `data-debug-androidTest.apk` sha `5deb90d7…`、mtime 23:16——**与 A1 轮 APK 逐字节相同**；gradle 对 `:core:data:assembleDebugAndroidTest` 判 up-to-date，**未因 02:46 的 main-resources（资产重导）重打包**（judge-files-record.md §gradle 口径注）。这一条是 7.4 装置缺陷的根因之一。

### 7.2 离线链重出与钉值（臂执行轮，02:29–02:51）

- dry-run 对账 **22 项 vs §1 实算表全部相符**（`title-dryrun.log`）；02:46 重导出：`.vec` **44,041 → 104,959 行**（+60,918 title 行 = 3,245 canonical + 40,796 alias + 60,918 title；其中 38,377 行与既有表面逐字等值、净新表面 22,541——§1 冻结口径随链落盘）、26,040,207 → 62,062,649 B，sha **`9d9bf3f5…`**；旁车 vectorCount 104,959、新字段 titleVectors=60,918、packSha256 `298f4c0c` 不变（`title-export.log`、`title-pack.log`、`title-touched-files.txt`）。
- `VECTOR_ASSET_SHA256` 重钉 `0536d0d1…` → `9d9bf3f5…`（`DenseRecallAssembly.kt`，三方同源实测；`title-touched-files.txt` 第 8 行）；五份 dense 夹具随链重出（encoder-parity 三件 sha `27fb7be8…` → `a5b5ead3…` 等；tokenizer universe 104,959）；`DenseVectorAsset.kt` 运行时**零改动**复证（title-touched-files.txt 记录不改动段）。
- **包零触碰成立**：包主件 / 25 sidecar / index / update-manifest 全程 v20（title-touched-files.txt 记录不改动段；回滚轮 §三-2 逐件复证）。触碰清单 **21 行**（11 改 + 10 夹具重出），PRE sha 全存 `title-sha/pre-sha.txt`；字节备份 `build/agent-batch19/backup/*.pre-title`。

### 7.3 设备·JVM 主集、逐章、MRR、墙钟（**不判读，登记为 intel**）

- 设备融合 **117/129 = 0.9069767441860465、MRR 0.7322997416020672**，逐章分布与 A1 轮逐位同（`title/device-metrics.txt:2-30`）——**逐位复现 A1 轮**：设备两账本 ↔ A1 轮两账本 symdiff 均 = **0**（`title/zero-drift.out.txt:5-6` `device_fused_vs_a1_round` / `device_lexical_vs_a1_round`；同 APK、新 wipe 设备）。根因：APK 内嵌 `.vec` = `0536d0d1…`（23:16 构建代，`unzip -p | sha256sum` 实测）——**标题资产 `9d9bf3f5…` 未进装置**。
- 设备词面 **102/129、MRR 0.6206718346253232**、p50=6ms / p95=12ms / max=15ms（`title/device-metrics.txt:31-33`）。
- JVM 融合镜像 **118/129 = 0.9147286821705426、MRR 0.7257105943152455、11 MISS**（`title/jvm-fused-metrics.txt`）——JVM 03:15 直读 02:46 重导资产（`9d9bf3f5…`），相对 A1 轮 117→118 为**资产代际差**读数；`only_in_jvm` 1 条（数列检验 n=1，rank 6）、`only_in_device` 2 条（`title/judge-files-record.md` §零漂移）。**118/129 不构成臂判读**（判读对象 = 设备主集；同路融合零漂移已 FAIL）。
- JVM 词面镜像 **102/129、MRR 0.6206718346253232** 与 A1 轮同值（`title/jvm-metrics.txt` B-route 段；词面路与 dense 资产无关）。

### 7.4 零漂移对称差（§2 门：同路对称差必须 0）

- 同路词面 device↔JVM symdiff = **0**（PASS）；同路融合 device↔JVM symdiff = **3**（**FAIL**），末行 `verdict: lexical=PASS fused=FAIL(same-route fused drift)`，**exit=2**（`title/zero-drift.out.txt:1-2,11`；本收束轮复跑同值，`title-final.md` §一）。
- 根因（判官记录实测，`title/judge-files-record.md` §零漂移）：**装置两侧喂了不同代的稠密资产**——设备 APK 内嵌 `0536d0d1…`（23:16 构建）vs 工作树 `9d9bf3f5…`（02:46 本臂重导，JVM 直读）；漂移在 JVM 侧不在设备侧（设备账本与 A1 轮 symdiff=0 逐位复现）；词面路与稠密资产无关 ⇒ 词面 symdiff=0 成立。
- 按预注册 §2「非 0 = 装置缺陷，该臂不判读」⇒ **本臂不判读**；按任务书非采纳路径**逐字回滚**。

### 7.5 显著性差值与 CI（不判读，登记）

- 融合 vs A1（设备账本对设备账本）：**B−A = 0.0000，95% CI（按章）[0.0000, 0.0000]，双侧 p = 1.0000**（`title/significance-fused-vs-A1.txt` 末行）——设备腿逐位复现 A1 的平凡结果，无判读意义。
- 词面 vs A1 同型：B−A = 0.0000，CI [0.0000, 0.0000]，p = 1.0000（`title/significance-lexical-vs-A1.txt` 末行）。

### 7.6 预算门实测（p50·p95·overBudget）

- 融合路由 **p50=38ms（≤150）/ p95=53ms（≤250）/ max 125ms / overBudgetSamples=0**，denseLegLive=true（`title/device-metrics.txt:2`）⇒ 预算内；词面 p50=6 / p95=12（`:31`）。
- 边界：该读数是 **APK 内嵌 A1 代资产（44,041 行）的延迟**，不是标题资产（104,959 行）的延迟实测；§1 的行数线性外推（融合路由 p50 ≈81–91 / p95 ≈104–122ms）**仍无实测对证，UNVERIFIED**。

### 7.7 三规则逐条（§2）

| # | 预注册规则 | 求值 |
|---|---|---|
| 1 | 主集严格更高（>117/129，即 ≥118/129） | **未求值**（不判读） |
| 2 | 24 章逐章不劣 | **未求值**（不判读） |
| 3 | 负向守卫 CI 上界 ≥0 | **未求值**（不判读） |
| — | 零漂移一票否决（装置门） | **先行触发**：同路融合 symdiff=3（装置缺陷） |

零漂移门 FAIL ⇒ 本臂不判读 ⇒ 三规则不予求值。**「未求值」不得记成通过或失败**（同批次18 A1 惯例）；JVM intel 118/129 既非判读对象，也不能替代规则①的设备主集判读。

### 7.8 词面不变式核验（§2）

- 设备词面 102/129 与 A1 轮**逐位同**（`device_lexical_vs_a1_round` symdiff=0，`title/zero-drift.out.txt:6`）✓；同路词面零漂移 symdiff=0 ✓；JVM 词面镜像 102/129 与 A1 轮同值 ✓——`alias_map.csv` / 生产 TSV 零触碰成立，**词面腿零漂移**（§2「词面臂漂移按装置缺陷处理」未触发）。

### 7.9 采纳或回滚与 sha 逐文件核验

- **裁定：rolled-back（不采纳）**——非三规则否决，而是零漂移装置缺陷 ⇒ 不判读 ⇒ 按预注册 §5 非采纳路径**逐字回滚**（含 `pack_dense_asset.py` 的本批改动）。回滚执行与核验（`build/agent-batch19/title-final.md`，收束轮实跑）：
  1. **全段终扫 21/21**：文件 sha == title-PRE 工作树字节，python 终扫输出 `SEGMENT: 21 files, ALL OK`；三行「备份≠HEAD blob」疑点经 `git ls-files --eol` + LF 归一化对拍证实为 autocrlf **EOL-ONLY** 差异，无在飞内容改动被回滚。
  2. **资产与钉值回 `0536d0d1…` 代**：`.vec`/`.vec.json`（44,041 行）逐字节还原；`VECTOR_ASSET_SHA256` 回指 `0536d0d1`；五份夹具逐件回 PRE（stage3 期望 `88fde9f8…`、scan `84eba62a…`、order `5e330d9d…` 名次差 0/129、tokenizer 三件 `aaf96429…/8a136cfb…/129a2020…`）。
  3. **APK 旁证 ×5**：23:16 APK 内嵌资产即 A1 POST 态，提取后与 PRE 目标逐位一致。
  4. **链驱动增量消解**：PRE 版生成器在恢复态重出全部命中 PRE（`model-manifest.json` 逐字回 `7f1f6e8d…`、npy 回 A1 代 `3fecbfcf…`/`8d4a1eed…`）；批次13 遗留既有改动原样保留。
  5. **陈旧性门**：恢复版 `check_asset.py` **exit=0，10/10 OK**（`.vec` 0536d0d1、count 44,041、packSha256 298f4c0c、layout 逐条一致）。
  6. **JVM 复跑**：Stage1LexicalLabTest、Stage2LexicalScoresExportTest、DenseRecallRerankerTest（5/5）、DenseAssetProvenanceTest（2/2）**绿**；`ProductionLexicalLegExportTest` **红**（V1_D1_MRR 两源 ULP 级分歧 ≈3×1e-16：A1 Python④ 锚 `…229` vs Kotlin 回读重算 `…232`）——**A1 遗留未竟项的实测暴露，非回滚缺陷**；A1 锚保留不动，登记待裁定（`title-final.md` §四）。
- **stateAfter：v20 + dense `0536d0d1…`（与 A1 采纳态逐位同）仍为基线；标题向量未采纳、非新基线**（`title-final.md` §五）；设备门若复跑，APK（23:16）与工作树资产已同代。
- **留档**：104,959 行标题资产的离线链产物（`title-touched-files.txt`、`backup/*.pre-title`、`title-sha/`）、JVM intel 读数（118/129）与两份装置账本全部保留在 `build/agent-batch19/title*/`；若未来以同代资产 + 同代 APK 重出装置，可按本预注册复评（不自动重开臂）。

### 7.10 结果节证据路径

`build/agent-batch19/title-final.md`（回滚裁定与核验）；`title/{device-metrics.txt,zero-drift.out.txt,zero-drift.json,jvm-fused-metrics.txt,jvm-metrics.txt,significance-fused-vs-A1.txt,significance-lexical-vs-A1.txt,judge-files-record.md,step1_compile_and_assemble.log}`；`build/agent-batch19/{title-dryrun.log,title-export.log,title-pack.log,title-stage3.log,title-touched-files.txt,title-gate-probe.log,title-prereg-gate.log,title-rollback-*.log,backup/*.pre-title,title-sha/}`。

### 7.11 批次20 clean 重测收束（终局裁定：adopted = false，规则②否决；2026-10-25）

> 填写口径（批次20 结案文书员，2026-10-25）：§1–§6 与本 §7.1–§7.10（批次19 轮记录）一字未动；本节按预注册 §4「未来同代装置可按预注册复评」登记批次20 的复评终局。数字标注：**执行轮实读**（`build/agent-batch20/title*/`、`title-final-r20.md` 臂执行轮落盘产物）+ **收口实跑**（结案文书轮复核，逐处注明）。**本节为该预注册的结果终态：臂 settled——判读流程完整走完（与批次19 轮的「不判读」不同，本轮三规则已逐条求值），最终裁定不采纳。**

**复评条件（预注册 §4 前提兑现）**：同代装置——批次20 冷启 wipe 实例（`title/emulator-coldstart.log`，15:05）+ 全链重出（`title-touched-files.txt` 21 行触碰 = 9 NORMAL + 12 PREM；`.vec` 44,041 → **104,959 行**、sha `9d9bf3f5…`、旁车 titleVectors=60,918、`VECTOR_ASSET_SHA256` 重指 `9d9bf3f5`；多件 sha 逐字节命中批次19 标题代快照 `title-sha/reroll-snapshot-*` / `final-shas.json`——确定性可复现）。装置门 GREEN（`title-gatetest.log`、`title-gate-recheck.log`）。

**出数（执行轮实读，`title/device-metrics.txt:2-5`、`zero-drift.out.txt`、`significance-fused-vs-A1.{json,txt}`）**：

- 设备融合主集 **118/129 = 0.9147286821705426**、MRR 0.7257105943152455、p50=48 / p95=60 / max=131ms、overBudgetSamples=0、denseLegLive=true——标题资产此轮**已进装置**（与批次19 轮 APK 内嵌 A1 代资产的本质差别）。
- 设备词面 102/129、MRR 0.6206718346253232（词面不变式成立）；同路零漂移 **lexical symdiff=0 PASS、fused symdiff=0 PASS**（`verdict: lexical=PASS fused=PASS`）——批次19 的 FAIL 根因（装置两侧资产代际错配）已消解；§2 门「非 0 = 装置缺陷」**未触发**。
- 显著性（reps=20000、seed=0，A=A1 基线、B=标题臂）：融合 B−A = **+0.0078**，95% CI（按章）[−0.0208, 0.0441]，双侧 p=0.9247；词面 delta=0.0000、p=1.0000。

**三规则逐条（预注册 §2，本轮已求值；裁定依据 = `build/agent-batch20/title-final-r20.md` §1）**：

| # | 规则 | 求值 | 依据 |
|---|---|---|---|
| 1 | 主集严格更高（≥118/129） | **成立**（118 ≥ 118） | `title/device-metrics.txt:3` |
| 2 | 24 章逐章不劣（任一章严格更低即不采纳） | **否决**：数学选择性必修第二册·第四章·**数列 0.9090909…（10/11）→ 0.8181818…（9/11）严格更低**（json a 行 38 → b 行 84）；仅函数的概念与性质严格更高（0.6364→0.8182），其余 22 章相等 | `significance-fused-vs-A1.json`（收口实跑逐章比对复现） |
| 3 | 负向守卫 CI 上界 ≥0 | **成立**（上界 0.0441 ≥ 0） | `significance-fused-vs-A1.txt` |
| — | 零漂移一票否决 | 未触发（PASS/PASS） | `title/zero-drift.out.txt` |

**裁定与回滚**：**adopted = false（唯一否决点 = 规则②；规则①③成立不改变否决）**。按预注册 §5 非采纳路径逐字回滚（`title-final-r20.md` §2–§3）：`git checkout --`（21 路径 EXIT=0）→ 8 件 EOL 表示差异（autocrlf/.gitattributes）→ 第二遍字节级复原（`.vec.json` 备份直拷、3 件 CRLF 变体、5 件 HEAD blob 原字节）→ 核验 porcelain 21/21 全空（3 次稳定）、`git diff` 空、零暂存、**21/21 sha256 == `title-sha/pre-sha.txt`**、`.vec`/`.vec.json` 与备份 cmp 一致、钉值回指 `0536d0d1`。

**stateAfter：v20 + `0536d0d1…`（A1 基线，44,041 行）＝ HEAD `558185c9` + 资产 `0536d0d1` + 旁车 `4c911fc3…` + 清单 `7f1f6e8d…`**（收口实跑复核 sha256 前 16 全中）。标题向量**未采纳、非新基线**；标题代产物 sha 全录 `title-sha/post-sha-*.txt` 与 `title-rebuild.log`，内嵌 `9d9bf3f5` 的 androidTest APK 留档可回溯。§6 预期的「9 条域内理论可达面」实际兑现为翻转分解（收口实跑：`device-fused-misses.txt` A1↔标题两账本集合运算，symdiff=3 = 修 2 + 新 1）：**修复 2 条**（均在函数的概念与性质章：y=|f(x)| 图象、f(x)+f(−x)=2x+1 求解析式）、**新增 1 条**（数列章「S_n=n²+2n+1 求通项为何单独检验 n=1」——即规则②否决的那一题）；净 +1（117→118）。增量方向存在但按章分布不均匀，逐章不劣门按预注册否决——**不得记成「臂无效」或「臂有效」，只记「不采纳」**。

**证据路径**：`build/agent-batch20/title-final-r20.md`（裁定与回滚核验）；`build/agent-batch20/title/{device-metrics.txt,zero-drift.out.txt,zero-drift.json,significance-fused-vs-A1.{json,txt},significance-lexical-vs-A1.{json,txt},emulator-coldstart.log,step2_run_device.log,step4_run_jvm.log}`；`build/agent-batch20/{title-touched-files.txt,title-rebuild.log,title-pack.log,title-export.log,title-dryrun.log,title-gatetest.log,title-gate-recheck.log,title-checkasset.log,title-dense-tests.log,title-kbchecks.log,title-stage3.log,title-sha/,backup/*.pre-titlerebuild}`。
