# D-3 0.90 次轮实验 · 预注册（2026-10-21 立 · 出数前锁定）

> **回答的问题**：在**成品包 v18（version=18 / contentVersion=d82a05607bdb622a）+ 金标 v2（129 条 / 24 章）**上，
> ① 把生产 `KNOWLEDGE_READ` 从裸 B 路 `limit=5` 接到既有的 **B512 召回 + 稠密重排 + top5** 路径（A2），
> ② 把融合口径从 α=0.5 归一化分数融合换成 **RRF(k=60)**（A3），
> 能否使主集 Recall@5 提高且**24 章逐章不倒**？A0 = v18 原样，是本轮的唯一比较基线。
> **性质**：**预注册规则存档 + 判读依据**，不是施工文档。§1–§10 在任何本轮臂数字出现之前写死；
> 出数之后不许改题面、不许改判据、不许改判读对象、不许调阈值。§11 结果节由收尾环节按 §11 填（**只填数，不改 §1–§10**）。
> **上游**：首轮预注册与判读纪律 `docs/research/2026-10-16-d3-round1-prereg.md`（§1 路线 / §5 显著性 / §6 零漂移 / §8 采纳）；
> D12 向量预注册规则的常设目标 **0.90/0.80**（`docs/kb-architecture-refactor-decisions-2026-09-21.md:41`）；
> Stage-2 融合口径先例（`docs/kb-stage2-dense-spec.md:113-119`：α=0.5、**D-rrf-60 = `Σ 1/(60 + rank_leg)`**、min-max 均出数前写死；
> 「融合参数不许在判官上调」）；
> v18 晋升与版本差（`docs/kb-batch14-2026-10-20.md:5-6,82-86`）；金标 chapter-only 迁移（`docs/kb-batch13-2026-10-17.md:169-170,268`）。
> **数字纪律**：本文件在 §11 之外只写**冻结输入/历史旁证**的数字（包/金标/字节 sha/已有 JVM 镜像），
> 不写本轮任何臂的判读读数、不预估结果。

---

## 1. 路线（写死）

**判读路线 = 设备金标融合臂**；设备词面臂为对照；JVM 双路只做装置保真（不判分）：

| 腿 | 装置 | 说明 |
|---|---|---|
| **设备金标融合臂**（主判读） | 真 SQL（Room）+ 端侧 dense（LiteRT int8）+ `DenseRecallAssembly.reranker()` → `OnDeviceDenseRecallReranker.order()` → `DenseFusion.order(...)`；**B512 召回 + 重排 + top5** | 判读数只取这一腿；判分窗口固定前 5（`SCORED_TOP_K=5`，`GoldenRetrievalInstrumentedTest.kt:617`） |
| **设备词面臂**（对照） | 同一 store、同一 `RECALL_DEPTH=512`、同一 `SAMPLES_PER_QUERY=5`，仅 `queryText=null`（纯词面次序） | 只作对照/情报，不判读（`GoldenRetrievalInstrumentedTest.kt:202-238`） |
| **JVM 双路**（装置保真，不判分） | 同一 `OnDeviceDenseRecallReranker` + fixture 编码器（离线原文查询向量）：词面镜像 + 融合镜像 `B-fused-mirror` | 与设备做同路零漂移对拍（§6）；JVM 数不进采纳比较（`GoldenRetrievalJvmTest.kt:100-135`） |
| **A2 生产路径探针**（仅 A2 判读用） | 设备上逐题驱动 `RoomTutorToolRunner` 的 `KNOWLEDGE_READ`（真安装 v18 + 真 SQL + LiteRT），取返回节点序列前 5 | **测量仪器**，不是生产改动；与 A0 共用同一探针；仪器件在首次出数前冻结 sha（见 §4/§9） |

- **设备命令**（原样；脚本文件落 `build/agent-batch15/d3/` 再跑，不用内联一行式）：
  ```
  D:/smart mistake book/gradlew.bat -p D:/smart mistake book :core:data:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.GoldenRetrievalInstrumentedTest --console=plain
  ```
- **A2 生产路径探针命令**（类名在首次出数前随探针件冻结；同一 `connectedDebugAndroidTest` 通道）：
  ```
  D:/smart mistake book/gradlew.bat -p D:/smart mistake book :core:data:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.study.KnowledgeReadProductionPathProbeInstrumentedTest --console=plain
  ```
- **JVM 命令**：`gradlew :core:data:testDebugUnitTest --tests "*GoldenRetrievalJvmTest*" --rerun`。
- **A3 单元测试命令**：`gradlew :core:data:testDebugUnitTest --tests "*DenseFusionTest*" --rerun`。
- **gradle 单通道**：任何时刻只有一个 `gradlew` 在跑；设备/JVM 步骤串行，不与其他 gradle 并行。
- **设备冷启纪律**：每次出数前先验 `adb devices` 有设备、`getprop sys.boot_completed` = 1、
  `/sdcard/Download` 真写探针（写入 → 读回 → 比对）；任一失败先 `-wipe-data` 冷启，再重验，通过后才跑测试。
- **账本落点**（设备）：`/sdcard/Download/golden-fused-misses-<运行戳>.txt`、`golden-lexical-misses-<运行戳>.txt`
  （`MISS_LEDGER_STEM`/`MISS_LEDGER_NAME`，`GoldenRetrievalInstrumentedTest.kt:628-639`）；A2 探针账本
  `knowledge-read-production-misses-<运行戳>.txt`（同 MISS 行格式）。取回 `build/agent-batch15/d3/`。
- **JVM 账本**：`build/golden-jvm-metrics.txt`（词面镜像）与 `build/agent-batch14/d3/jvm-fused-metrics.txt`
  （融合镜像；路径硬编码在 `GoldenRetrievalJvmTest.kt:175`，是本轮冻结的现状，见 §3 口径注）→ 抽取两路 MISS 到
  `build/agent-batch15/d3/jvm-lexical-misses.txt` / `jvm-fused-misses.txt`。
- **臂执行顺序**：**A0 → A2 → A3**。A0 先出全套基线（设备金标双臂 + JVM 双路 + 生产路径探针）；
  每条臂一次变更、出数、判读、回滚或保留；后一臂的比较对象始终是 **A0**，不是前一臂。
- **包中性**：本轮不写 `build/kb-staging/`、成品目录 pack/sidecar、`.vec`/`.vec.json`；唯一例外见 §4
  （A2 一处生产调用点、A3 一处融合变体 + 其测试/夹具重钉），不采纳即逐字回滚。

## 2. 判读对象（写死）

**判读对象** = 该臂在**设备**上的两个读数（A3 取金标融合臂；A2 取生产路径探针，见 §4/§7 口径注）：

1. **主集 Recall@5**（129 条，命中/129）；
2. **逐章 Recall@5**（24 章，逐章命中/该章题数）。

**命中定义（不动）**：判分窗口 = 返回序列前 **5** 名（`SCORED_TOP_K=5`，`GoldenRetrievalInstrumentedTest.kt:617`），
命中 = 该窗口内存在 `:atomic:<expectedSlug>` 后缀节点（`ATOMIC_SUFFIX=":atomic:"`，`:626`；
判分实现 `:186`、`:228`、`RetrievalBenchmark.kt:324-331`）。

**MISS 诊断口径（不动）**：判分仍 top-5；MISS 名次只在放宽窗口内解释——候选深度 `RECALL_DEPTH=512`
（`:624`），返回序列前 `MISS_PROBE_LIMIT=256`（`:654`；JVM 同值 `RetrievalBenchmark.kt:237`），
`rank=N` = 窗口内第 N 名，`absent` = 该窗口内不存在。名次只作诊断，不进判据。

**24 章与题数（自冻结金标实算；129 条合计）**：

| 章 | 题数 | 章 | 题数 |
|---|---|---|---|
| 化学必修第一册·第三章·铁与金属材料 | 11 | 数学必修第一册·第三章·函数的概念与性质 | 11 |
| 化学必修第二册·第七章·有机化合物 | 2 | 数学必修第一册·第二章·不等式 | 3 |
| 化学必修第二册·第五章·硫氮及其化合物 | 9 | 数学必修第二册·第六章·平面向量 | 3 |
| 化学必修第二册·第六章·化学反应与能量 | 8 | 数学选择性必修第一册·第二三章·解析几何 | 9 |
| 化学选择性必修1·第一章·反应热与能量 | 1 | 数学选择性必修第三册·第六章·计数原理 | 3 |
| 化学选择性必修1·第三章·水溶液中的离子平衡 | 3 | 数学选择性必修第二册·第四章·数列 | 11 |
| 化学选择性必修3·第一章·有机化合物的结构特点 | 1 | 物理必修第一册·第一章·运动的描述 | 8 |
| 生物学必修1·第五章·细胞呼吸的原理和应用 | 3 | 物理必修第一册·第三章·相互作用 | 12 |
| 生物学必修2·第一章·遗传的基本规律 | 9 | 物理必修第一册·第四章·牛顿运动定律 | 1 |
| 生物学选择性必修3·第三章·基因工程的基本操作程序 | 2 | 物理必修第三册·第十一十二章·恒定电流 | 9 |
| 生物学选择性必修3·第三章·重组DNA技术的基本工具 | 1 | 物理必修第二册·第八章·机械能与能量守恒 | 3 |
|  |  | 物理选择性必修第一册·第一章·动量 | 3 |
|  |  | 物理选择性必修第三册·第一至三章·热学 | 3 |

（科分布：CHEMISTRY 35 / MATH 40 / PHYSICS 39 / BIOLOGY 15；逐章题数合计 129。）

**A0（本轮基线）= v18 原样**：不预填分数；A0 在本轮先跑，读数与账本在 §11 登记。
**历史旁证（不是 A0 判读值，不用于任何采纳规则）**：v18 的 JVM 融合镜像已有一份落盘读数
`build/agent-batch14/d3/jvm-fused-metrics.txt`（sha256 `b8199821…`，见 §3）——主集 100/129 = 0.7751937984496124、
29 条 MISS、其中 4 条 `rank=absent`；设备侧 v18 在批 14 因在飞文件阻断编译而**没有**设备读数
（`docs/kb-batch14-2026-10-20.md:188-198`）。本轮 A0 必须重新出设备读数，不得用该 JVM 值替代。

**常设目标与差距（不动）**：主集 **0.90**、逐章 **0.80**（D12，`docs/kb-architecture-refactor-decisions-2026-09-21.md:41`）。
0.90/0.80 的差距在 §11 由 A0 实测值重算；**本轮不承诺 0.90 达标**。

## 3. 冻结输入（实算 sha；2026-10-21 本文件撰写时运行）

**实算方法**：逐文件二进制读，`python -c "import hashlib; print(hashlib.sha256(open(p,'rb').read()).hexdigest())"`；
本文件直接抄录该次输出。侧车集聚合值 = 按路径排序、逐文件 sha256 十六进制 + LF 连写后再 sha256。

| 冻结对象 | sha256（实算） | 说明 |
|---|---|---|
| 包 manifest `core/data/src/main/resources/knowledge/moe-2025-update-manifest.json` | `7daf9a2acb026c50a050beeed2e1f8314b2f81de1b7c9e1be4fae7bd65c7a6bc` | 592,254 B；实读 `version=18`、`contentVersion=d82a05607bdb622a`、`packId=moe-2025-four-subjects-v1`、`retired=1256` |
| 主包 `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` | `f6098c41d4901d56bd33106b205e7489fa6bf078d3853413c674976b5b84386c` | 4,509,284 B；= `.vec.json` 的 `packSha256` |
| 教学支持侧车集 `knowledge/moe-2025-teaching-support-v2-*.json`（26 件） | 聚合 `7b0815d1ee9eb4437234ac61ab3d8a4f6fced7cdf6577de829c9c6a2bd2fb1b3` | v18 版本差只来自其中 `-02`（`docs/kb-batch14-2026-10-20.md:5-6`） |
| 侧车 `moe-2025-teaching-support-v2-02.json` | `c28eb2303213a8236503adcd61924b242c4b3c2deb03b6946443b44a92b79dbc` | 4,718,216 B；v17→v18 的 2 条乱码修复承载件 |
| 向量资产 `knowledge/dense/bge-small-zh-int8.vec` | `8e216ee1719b8a18f213a1629572017fbd97d4e7d96a882e9acdf33ca12ad45e` | 26,021,979 B；= `.vec.json.sha256` = `DenseRecallAssembly.VECTOR_ASSET_SHA256`（`DenseRecallAssembly.kt:101`） |
| 向量侧车 `knowledge/dense/bge-small-zh-int8.vec.json` | `887d14dfffd31ba68c0224f8695e4740eb31b5b5d2848ef779b470b6d0982c54` | 3,710 B；记录 `packSha256=f6098c41…`、`sha256=8e216ee1…`；44,010 行（3,245 原子 + 40,765 别名）/ dim 512 / int8 |
| 金标权威 `tools/kb_coverage/tables/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | 34,796 B；129 条 / 24 章；含 batch13 chapter-only 迁移（1 行，见下口径注） |
| 金标副本 `core/data/src/androidTest/assets/golden/golden_queries_v2.json` | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` | 与权威逐字节相同（两份副本同值） |
| 金标旁车（两份）`golden_queries_v2.json.sha256` | `2978eb5299bd1bf1c7e716ffd71b2d9c01e1c5ad9e3ed68afded9a8faa91d756` | 各 66 B；内容 = `eabef102…` + CRLF（实读字节）；两份同值 |
| 判官（设备）`GoldenRetrievalInstrumentedTest.kt` | `c2280255a9b0db2bc77ac1213e977be755a431efe80cfdf5b3c0c6e7bb7a4786` | 45,540 B；与首轮登记值同（本轮不改判官） |
| 判官（JVM）`GoldenRetrievalJvmTest.kt` | `62c232fcbf3ffd11fcdedaaa7a4340981bbc22b8d6a2e0ad39614ef78f5ccd95` | 23,015 B；**工作树现状**，含未提交的 batch14 落点改动（见下口径注）；按现状冻结、不编辑 |
| 显著性工具 `tools/kb_coverage/retrieval_significance.py` | `061f7a8aa3f670d55c0483d3430563f585f95778843ee6352d3ec1be70d3ecbb` | 10,116 B；§5 口径承载件 |
| 零漂移语义载体 `build/agent-batch14/d3/zero-drift-pairs-b14.py` | `83064fad7dc7aa171e9c3a779aef560b9c63d2b07edbea36bd32e8665c9c39de` | 9,334 B；本轮 `zero-drift-pairs-b15.py` 只改路径/输入，语义段逐字沿用；新件在首次出数前记录 sha |
| A2 回滚目标 `RoomTutorToolRunner.kt` | `3f4cdcf75c921369b4c5540b31a896f539980b12a2b0685a78d5cb84ab57541d` | 83,047 B；A2 改动前态 |
| A3 回滚目标 `DenseFusion.kt` | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` | 5,877 B；α=0.5 态 |
| A3 回滚目标 `DenseFusionTest.kt` | `19e184313d2f540146ebae2b5e8795c1eec2da5eefcdd63a13a704e0d5018d01` | 2,880 B；α=0.5 钉态 |
| A3 回滚目标 `dense/dense-fusion-reference.txt` | `c2ab7b1c321ebcd9c4886f6fde019633f2827d07d12f0901a9942431ae29ec5f` | 1,866 B；融合夹具（α=0.5 期望次序） |
| v18 JVM 融合镜像旁证 | `b81998217818003ca6bb75ca439ff36a7d6a8788f70eb6415de04203ad5892c3` | `build/agent-batch14/d3/jvm-fused-metrics.txt`，9,411 B；已在首次出数前复制到 `build/agent-batch15/d3/v18-jvm-fused-metrics-b14.txt`（同 sha），防止 JVM 判官覆盖；仅情报 |

- **金标口径注（重要）**：首轮登记的金标 sha 是 `3dbe9073…`；批 13 的 chapter-only 迁移把 1 条 BIOLOGY
  `限制酶` 的章从「基因工程的基本操作程序」改到「重组DNA技术的基本工具」，两份副本同步变为 `eabef102…`
  （`docs/kb-batch13-2026-10-17.md:169-170,268`）。因此本轮冻结的是 **`eabef102…`（129 条 / 24 章）**，
  不是首轮的 `3dbe9073…`；`expectedSlug` 0 改指。金标内容本轮不动。
- **JVM 判官口径注**：工作树相对 HEAD 的未提交 diff = 3 行改 3 行，全部是**输出落点**：
  `:172` 注释、`:175` 路径、`:197` 文案把 `build/agent-batch13/d3` 改成 `build/agent-batch14/d3`；
  判分逻辑、路线、阈值一字未动（`git diff -- GoldenRetrievalJvmTest.kt` 实读）。按现状冻结为 `62c232fc…`；
  **本轮不编辑判官件**，运行会覆盖 `build/agent-batch14/d3/jvm-fused-metrics.txt`，故旁证副本已先隔离。
- **本文件自身 sha**：落盘后实算，记录于 `build/agent-batch15/prereg-d3-round2.sha256` 与本次回报；
  自引用不写回正文（写回会改变字节，记录即失效）。

## 4. 臂表（一条臂一次变更；写死）

| 臂 | 定义（口径写死） | 变更集 | 变更前 sha256 | 参评 |
|---|---|---|---|---|
| **A0** | **v18 原样**：α=0.5 归一化分数融合（`DenseFusion.kt:21`），生产 `KNOWLEDGE_READ` `limit=5`（`RoomTutorToolRunner.kt:325`），金标判官走 B512 路由（`GoldenRetrievalInstrumentedTest.kt:624`） | 无 | — | ✅ 基线 |
| **A2** | **生产接线**：`KNOWLEDGE_READ` 深度 5→512 + 显式 `.take(5)`；展示条数、材料摘要范围、`resultCount` 仍是 ≤5 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` 一处：`:325` 的 `limit = KNOWLEDGE_READ_NODE_LIMIT` 改为 `limit = MAX_KNOWLEDGE_RECALL_CANDIDATES`（512，`core/database/.../StudyDatabasePort.kt:39`），紧随其后 `.take(KNOWLEDGE_READ_NODE_LIMIT)`（常量仍 = 5，`:403`）再进 `nodeMaterialSummaries`/渲染；SQL 文本/DAO 不动 | `3f4cdcf7…` | ✅（按 §7 口径注在生产路径上判读） |
| **A3** | **融合臂 RRF(k=60)**：`DenseFusion.order(...)` 一处写死变体，`score = Σ 1/(60 + rank_leg)`（Stage-2 冻结定义，`docs/kb-stage2-dense-spec.md:115`）；只重排 `orderDomain`（词面召回集），**成员与长度不变**；两腿名次均按各自分数降序、并列按 node_id 升序；某腿无分 ⇒ 该腿贡献 0 | `core/data/.../knowledge/dense/DenseFusion.kt` 一处（新增 `RRF_K=60` 并让 `order()` 走 RRF；`ALPHA=0.5` 与 `minMax()` 保留但不再参与该路径）+ `DenseFusionTest.kt` 按新行为重钉 + `core/data/src/test/resources/dense/dense-fusion-reference.txt` 的 `[fusion]` 期望次序重钉 | `103cce57…` / `19e18431…` / `c2ab7b1c…` | ✅（设备金标融合臂判读） |
| **A1** | **别名扩展** | **本轮不做** | — | ❌ |

### 4.1 A2 的口径与判据（写死）

**为什么接 512**：生产另外两个知识召回调用点已经是 `MAX_KNOWLEDGE_RECALL_CANDIDATES`（512）+ `queryText`
（`RoomTutorKnowledgeContextLoader.kt:67-72`、`RoomMistakeOrganizationRepository.kt:227-231`）；
`KNOWLEDGE_READ` 是唯一仍停在 5 的调用点。稠密腿只能重排候选域内成员，域只有 5 条时融合拉不进新节点
（`GoldenRetrievalInstrumentedTest.kt:166-172` 的注释登记了同一事实）。A2 让生产调用点与金标 512 路由同构，
不新增 SQL、不改索引、不改判官。

**A2 的三条判据（同时满足才可采纳；任一不过即不采纳）**：

- **(a) 生产路径与金标 512 路由同构**：在设备上对 129 条金标逐题驱动 `KNOWLEDGE_READ`（subject=金标科目，
  terms=[题干]），从结果摘要按行序取前 5 个展示名；与同一 store、同一题面、`limit=512` + `queryText=题干`
  的返回序列前 5 个展示名**逐项相等，129/129**。
  **前置断言**：v18 主包内同科叶子节点 `name` 无重复（本文件撰写时实算：
  MATH 834/0、PHYSICS 889/0、CHEMISTRY 1011/0、BIOLOGY 511/0，合计 3,245；有重复则该探针改用 id 级 seam，
  不改本判据语义）；
  **反向断言**：探针必须同时记录 `resultCount` ≤5 且摘要行数 = `resultCount`（挡住"把 512 条直接吐给模型"）。
- **(b) 墙钟**：生产路径全段（`runTraced(KNOWLEDGE_READ)` 起止）p50 ≤ **150ms**、p95 < **250ms**；
  采样与百分位定义照判官件（每题 5 次、`List<Long>.percentile`，`GoldenRetrievalInstrumentedTest.kt:604-611`），
  CI 系数照 `FUSED_P50_BUDGET_MILLIS`/`FUSED_P95_BUDGET_MILLIS` 的同一规则（`:707-708`；本机无 CI 参数时系数 1）。
  另报 store-only 分项（512 召回 + 重排）作为情报，不进判据。
- **(c) 金标分不变**：A2 下设备融合/词面 + JVM 融合/词面四份 MISS 题面集合，相对 A0 的对称差**全 0**
  （即金标判官读数逐题不变）。A2 按构造不经过判官的 store 调用（`GoldenRetrievalInstrumentedTest.kt:163-174`
  直接调 store），本项是"改动没有误伤判官"的守卫。

### 4.2 A3 的口径与判据（写死）

**RRF 定义（逐字写死，避免"两套 RRF"）**：对 `orderDomain` 中每个节点：

1. `rankD` = 按 dense 原始分降序、并列 node_id 升序的 1 起始名次；无 dense 分的节点无 `rankD`；
2. `rankL` = 按 lexical 原始分降序、并列 node_id 升序的 1 起始名次；无 lexical 分的节点无 `rankL`；
3. `score = 1/(60 + rankD) + 1/(60 + rankL)`；缺一腿 = 该项 0；
4. 按 `score` 降序、并列 node_id 升序输出；输出成员与长度 = 输入 `orderDomain`。

**为什么只在 `orderDomain` 内排名**：store 契约是"稠密腿只改次序、候选域不变"
（`RoomKnowledgeBaseStore.kt:150-190`；`DenseFusion.kt:14-19`）。把 dense 腿全科名次并入会引入词面域外节点，
违反该契约，也会改变返回长度语义；本轮不这样做。Stage-2 的 D-rrf-60 是离线全候选域对照臂
（`docs/kb-stage2-dense-spec.md:115`），端侧移植的差异仅此一处，登记为口径注。

**A3 的变更边界**：只改 `DenseFusion.kt` 的 `order()` 路径与 `RRF_K`；`ALPHA`、`minMax` 保留
（其他调用与 `DenseFusionTest` 的 min-max 断言不删）；不重打包、不重打 dense、不改查询编码器、不改 SQL。
`DenseFusionTest` 重钉时：
- `frozen alpha is half`（`DenseFusionTest.kt:20-22`）**保留**（ALPHA 仍是 0.5，RRF 路径不使用它）；
- `[fusion]` 用例的**期望次序**由独立 RRF 参考实现现算（脚本 `build/agent-batch15/d3/rrf_reference.py`，
  首次出数前记录 sha；**不得**用 `DenseFusion.order()` 自己的输出回填期望值）；
- 重钉处写因果注释：旧夹具是 α=0.5 归一化融合的期望，RRF 臂采用 `Σ 1/(60+rank)`，旧值不是本臂的证据。

### 4.3 A1 别名扩展：本轮不做的证据（写死）

**证据**：v18 JVM 融合镜像的 29 条 MISS 中，只有 **4 条**是"前 256 窗口不存在"（`rank=absent`）：
CHEMISTRY「镁条着火时为什么不能用二氧化碳灭火器去灭？」、MATH「在△ABC中 AP=xAB+yAC…」、
PHYSICS「一幅画用绳子穿过可在横杆上滑动的小环吊着…」、PHYSICS「一盏灯被两根绳子吊着…」；
其余 **25 条**都在前 256 窗口内（`rank=N`；N 的取值集合 = 6/7/8/10/11/12/13/14/15/16/18/20/27/34/35/43/61/73/75/104）。
来源：`build/agent-batch14/d3/jvm-fused-metrics.txt`（sha256 `b8199821…`；29 条 MISS、4 条 `rank=absent`，
本文件撰写时逐行实读/计数）。**成本**：A1 要改别名 → 改主包/侧车字节 → 重打 `.vec`/`.vec.json`
（行数、`sha256`、`packSha256`）→ 重钉 `DenseRecallAssembly.VECTOR_ASSET_SHA256`（`:101`）与
`DenseAssetProvenanceTest` 的三方一致性钉 → 全链复跑。**结论**：本轮不做；登记到下一轮或按用户裁定。
（该证据是 JVM 镜像；设备 A0 出数后若 MISS 集合与它不同，A1 的取舍仍按本条成本/收益结构复核，**不因 A0 结果改本轮臂表**。）

## 5. 显著性口径（写死）

- **工具与命令**（原样；A = 本轮臂，B = A0；`--reps 20000 --seed 0`）：
  ```
  python tools/kb_coverage/retrieval_significance.py \
    --golden tools/kb_coverage/tables/golden_queries_v2.json \
    --misses <本轮臂 MISS 账本> \
    --misses-b <A0 MISS 账本> \
    --reps 20000 --seed 0 --json > build/agent-batch15/d3/<臂>-significance.json
  ```
  - A3：`<本轮臂 MISS 账本>` = `a3-device-fused-misses.txt`；`<A0 MISS 账本>` = `a0-device-fused-misses.txt`。
  - A2：`<本轮臂 MISS 账本>` = `a2-production-misses.txt`；`<A0 MISS 账本>` = `a0-production-misses.txt`
    （生产路径探针账本，见 §4.1/§7 口径注）。
- **机制**：按**章** cluster 配对 bootstrap——以章为重采样单位、A/B 两侧取同一批章、每次复制算差值
  （`retrieval_significance.py:125-142`）；输出配对差点估计 + 95% CI（`ci95_chapter`）+ 双侧 p
  （符号翻转比例 +1 平滑，`:191-194`）。两侧各自的 `ci95_query`/`ci95_chapter`/逐章点估计一并落盘。
- **方向口径（写死）**：命令输出的配对差是 **B−A = A0 − 本轮臂**。正文与 §11 一律带方向前缀
  （「本轮臂−A0」或「A0−本轮臂」），不写裸「配对差」。§7(iii) 要求 **(本轮臂−A0) 的 95% CI 上界 ≥ 0**
  ⇔ 命令输出的 95% CI **下界 ≤ 0**。
- **报告项**：配对差点估计、95% CI、双侧 p、两侧逐章表、逐章最小。
- **正向不显著不阻断采纳**（照实登记）；**显著负向**（(本轮臂−A0) 的 CI 上界 < 0）按 §7(iii) 不采纳。

## 6. 零漂移硬门槛（写死）

- **判据**：**同路** device↔JVM 的 MISS **题面集合对称差必须 0**，每条臂各判一次、两路都判：
  ① 词面路 `device-lexical ↔ jvm-lexical`；② 融合路 `device-fused ↔ jvm-fused`。
  任一非 0 = **装置缺陷**（不是成绩）⇒ 该臂**不判读**：先修装置、重出数，再按 §7 判。
- **对比机制**（照 `build/agent-batch14/d3/zero-drift-pairs-b14.py`，sha256 `83064fad…`）：
  以**题面**为键；逐行格式校验（`MISS [科目] 题面 | expectedSlug=… | chapter=… | rank=…`，认不出的行报错、
  不静默跳过）；重复题面直接报错；**名次不进判据**（只报 rank 情报）。本轮脚本 `zero-drift-pairs-b15.py`
  只改路径/基线输入，语义段逐字沿用；首次出数前记录新件 sha。
- **只作情报、不作门槛**：跨路差（`device-fused ↔ jvm-lexical`）、device 内两腿（`lexical ↔ fused`）、
  本轮臂↔A0 的四路差——值随包/臂换代变动，只登记，不阻断。
- **A2 的补充门**：A2 生产路径探针是设备独有（JVM 无 `RoomTutorToolRunner` 同构装置），
  不参与 device↔JVM 对拍；它的装置保真由 §4.1(a)「生产路径 vs 金标 512 路由 129/129 同构」承担。
  A2 的金标四份账本「不变」门（§4.1(c)）与零漂移是两道独立的门，任一非 0 都不判读/不采纳。
- **A3 的零漂移**：device 与 JVM 共用同一 `DenseFusion.order()` 实现，RRF 变更同时作用于两侧；
  同路对称差必须 0；非 0 按装置缺陷处理，不得解释为 RRF 效果。

## 7. 采纳三规则（写死）

**候选集 = {A0, A2, A3}；A1 不参评；无诊断臂参评。**

### 7.1 通用三规则

- **(i) 主集严格更高**：本轮臂主集点估计 **>** A0 主集点估计；**相等 = 无收益 → 不采纳**
  （本预注册写死的保守口径；不引用未核实的旧条目作依据）。
- **(ii) 24 章逐章不劣**：对 **24 章全量**逐章比较点估计，**∀章 c：recall_c(本轮臂) ≥ recall_c(A0)**；
  等值通过；**任一章严格更低即不采纳**；不设小样本量豁免（含 1–2 题的章）。
- **(iii) 负向守卫**：§5 的按章 cluster 配对 bootstrap（`--reps 20000 --seed 0`）中，
  **(本轮臂 − A0) 的 95% CI 上界 ≥ 0**；显著负向（上界 < 0）即不采纳。
  映射到 §5 命令输出：其「B−A = A0 − 本轮臂」的 95% CI **下界 ≤ 0** ⇔ 本条通过。
- **正向不显著不阻断采纳**（照实登记进 §11）。

### 7.2 接线臂（A2）的口径注（本预注册写死）

A2 按构造不改设备金标融合臂的 512 路由（判官直接调 store，不经过 `RoomTutorToolRunner`；
`GoldenRetrievalInstrumentedTest.kt:163-174`），金标臂在 A2 下按预期**不变**。若把 (i) 的"主集严格更高"
套在金标臂上，接线臂在数学上永远不可能满足。故 **A2 的 (i)(ii)(iii) 一律以生产 `KNOWLEDGE_READ` 路径的
129 条读数为比较对象**：A = A2 生产路径，B = A0 生产路径（同一探针、同一 v18 装置、同一题面）。
§4.1(c) 的"金标分不变"是**独立守卫**（设备/JVM 四份 MISS 集合对称差全 0），不替代 (i)-(iii)。

### 7.3 逐臂落地

| 臂 | (i)(ii)(iii) 的判读对象 | 附加条件 | 采纳动作 | 不采纳动作 |
|---|---|---|---|---|
| **A2** | **生产路径** 129 条主集 + 24 章 | §4.1(a) 同构 129/129、§4.1(b) p50/p95、§4.1(c) 金标分不变；与 (i)-(iii) 全部通过 | 保留 `RoomTutorToolRunner.kt` 改动；**无钉值重钉**（现有单测不钉 `limit`，实读 `RoomTutorToolRunnerTest.kt` 无该断言） | 按 §9 逐字回滚 `RoomTutorToolRunner.kt`，核验 sha |
| **A3** | **设备金标融合臂** 129 条主集 + 24 章 | §6 同路零漂移 0 | 保留 RRF 变体；重钉 `DenseFusionTest`、`dense-fusion-reference.txt` 的 `[fusion]` 期望次序与 `RRF_K=60` 常量（按实测值）；跑 `DenseFusionTest` 绿 | 按 §9 逐字回滚三件，核验 sha |

- 两条臂的采纳互相独立：A2 过而 A3 不过 → 保留 A2、回滚 A3；A3 过而 A2 不过 → 回滚 A2、保留 A3
  （A3 的判读不依赖 A2，因金标判官不经过 runner）。
- **0.90 达标不在本轮承诺**：本轮只承诺「不降（§8）+ 两臂裁决」；0.90/0.80 的缺口归属留给内容侧轮次。

## 8. 止损线（写死）

1. **任一臂主集点估计 < A0 同名对象的主集点估计 ⇒ 中止该臂**（不再继续该臂的任何后续出数），
   如实登记，不解释为噪声。A2 的"主集"= 生产路径；A3 的"主集"= 设备金标融合臂。
2. 主集点估计 **= A0** ⇒ 无收益（不触发"中止"，但按 §7(i) 不采纳、保留现状）。
3. **连续两臂不改善 ⇒ 停轮并转内容侧**：本轮可执行臂顺序为 A2 → A3；若两臂在其判读对象上都 ≤ A0，
   计数 **2** → 停轮、转内容侧。臂因装置缺陷（§6 非 0、设备不可用、编译被阻断）未出数时**不计入**
   "不改善"计数；先修装置/解除阻断，再按同一预注册重出。
4. **不许重开 D12 旧结论**：词面栈/去截断/宽召回统一路由等已锁结论（KD-24 等）不因本轮结果翻案。
5. **止损线优先于一切**：触发时先在当场记录读数与账本 sha，再按 §7/§9 处置。

## 9. 回滚与重钉（写死）

1. **通用纪律**：每条臂一次变更；出数结束立即裁。不采纳 → 把该臂变更集**逐字回滚**，并核验
   **回滚后字节 sha256 == §3 记录的变更前 sha**；核验输出（`sha256sum`/Python 实算 + `git diff --stat` 为空）
   落 `build/agent-batch15/d3/`。采纳 → 保留变更，按 §7.3 重钉对应钉值。
2. **写盘前备份**：任何编辑前先把原文件复制到 `build/agent-batch15/backup/`（保留原文件名 + `.pre-a2`/`.pre-a3`），
   并记录备份的 sha256；回滚从备份逐字恢复，不从记忆重写。
3. **A2**：
   - 回滚目标 = `RoomTutorToolRunner.kt` `3f4cdcf7…`；回滚后核验三件事：文件 sha == 目标、`git diff` 该文件为空、
     `KNOWLEDGE_READ` 恢复 `limit = KNOWLEDGE_READ_NODE_LIMIT`（5）且无 `.take(...)` 包装。
   - 采纳 = 无钉值重钉（A2 不改任何测试钉）；生产路径探针是测量仪器，不属于 A2 变更集，不因回滚删除。
4. **A3**：
   - 回滚目标三件：`DenseFusion.kt` `103cce57…`、`DenseFusionTest.kt` `19e18431…`、
     `dense-fusion-reference.txt` `c2ab7b1c…`；三件逐一核验 sha == 目标，且 `git diff` 三件为空。
   - 采纳 = 重钉 `DenseFusionTest` 的 `[fusion]` 期望次序与 `RRF_K=60` 常量、`dense-fusion-reference.txt`
     的 `[fusion]` 行（按独立 RRF 参考脚本现算值），并在测试注释记录因果；跑
     `gradlew :core:data:testDebugUnitTest --tests "*DenseFusionTest*" --rerun` 必须绿。
5. **A0 不动**：A0 无变更集，无回滚目标；A0 账本只读，后续臂不得覆盖（文件名带 A0 前缀）。
6. **禁止项**：不用 `--no-verify`、不切分支、不改写既有提交、不为了让门变绿而删断言/放宽 required/削门。

## 10. 本轮不做（边界）

- **不改判官/判据常量**：金标集（129 条 / 24 章）、命中定义 `:atomic:<expectedSlug>`、top-5、候选深度 512、
  MISS 探针窗口 256、回归地板 0.64/0.56、融合墙钟门 p50 150ms / p95 250ms（及 CI 系数规则）一律不动。
  A2 只是让生产调用点**使用**既有 512 常量并显式 `.take(5)`，不改常量本身。
- **不改 SQL 形状**：`searchSubjectKnowledgeRecallCandidates` 的 SQL/DAO/索引表一字不动；`limit` 是参数，不是 SQL 形状。
- **不改 `INDEX_VERSION`、不换 FTS5**、不改 `KnowledgeSearchFeatureExtractor` 的片段/特征截断。
- **不上大档**：不换模型、不换向量维度（512）、不换 int8 资产、不重打 dense、不重打包/侧车。
- **不写内容绑定**：不写 `knowledge-production/`、不写判定表、不动 `core/database`、不动 `build/kb-staging/`。
- **本轮不做别名臂（A1）**；不做 α 变体、z-score、第二融合参数等诊断臂；任何临时诊断数一律标注「非判读数」，
  不进采纳比较、不据其调参。
- **不编辑判官件**：`GoldenRetrievalInstrumentedTest.kt` / `GoldenRetrievalJvmTest.kt` 按 §3 现状冻结；
  JVM 判官的 batch14 落点改动已存在于工作树，本轮不修改、不回退。
- **不 push、不切分支、不改写既有提交**；不编辑他线在飞文件（清单见全局纪律）。
- **不把 0.90/0.80 达标当本轮承诺**。

## 11. 结果节（出数前为空；收尾只填数，不改 §1–§10）

> **填写格式（固定）**：A0 实测（设备金标融合/词面 + JVM 双路 + 生产路径探针）→ A2 实测（生产路径主集/逐章/
> 墙钟/同构 129/129/金标不变）→ A3 实测（设备金标融合臂主集/命中/MRR/逐章最小/逐章全表）→ 零漂移两路对称差
> （每臂）→ 显著性（配对差 **本轮臂−A0** 点估计 + 95% CI + 双侧 p）→ §7 三规则逐条求值 →
> 采纳/不采纳结论与落地读数（回滚后 sha 或重钉测试结果）→ 0.90 差距更新。
> **纪律**：填入时若发现 §1–§10 需要改任何一个字，先停、按「文档缺陷」上报，不许边填边改。

### 11.1 A0（v18 原样）

- 设备金标融合臂：主集 Recall@5 = ____（__/129）；MRR = ____；逐章最小 = ____；逐章全表 = ____。
- 设备词面臂（对照）：主集 = ____（__/129）；MRR = ____。
- JVM 双路：词面 = ____（__/129）；融合 = ____（__/129）；两路 MISS 账本 sha = ____。
- 生产路径探针 A0：主集 = ____（__/129）；24 章逐章 = ____；p50 = ____ms / p95 = ____ms。
- A0 五份账本 sha：device-fused ____；device-lexical ____；jvm-fused ____；jvm-lexical ____；production ____。

### 11.2 A2（生产接线）

- 同构断言：____/129（通过/不通过）；反向前置（`resultCount`≤5、行数=结果数、同科 name 无重复）____。
- 墙钟：p50 = ____ms（门 ≤150）；p95 = ____ms（门 <250）；store-only 分项 p50/p95 = ____/____ms。
- 金标分不变：device-fused Δ____；device-lexical Δ____；jvm-fused Δ____；jvm-lexical Δ____（对称差）。
- 生产路径：主集 = ____（__/129）；MRR = ____；逐章最小 = ____；逐章全表 = ____。
- 零漂移：词面路对称差 ____；融合路对称差 ____。
- 显著性：配对差（A2−A0）= ____；95% CI = [____, ____]；双侧 p = ____（命令口径 A0−A2 = ____，CI [____, ____]）。
- §7 求值：(i) ____；(ii) ____（严格更低章：____）；(iii) ____；附加门 ____。
- 裁决：采纳 / 不采纳；落地读数（保留 diff 或回滚后 sha）____。

### 11.3 A3（融合臂 RRF k=60）

- 设备金标融合臂：主集 Recall@5 = ____（__/129）；MRR = ____；逐章最小 = ____；逐章全表 = ____。
- JVM 融合镜像：主集 = ____（__/129）；MRR = ____；MISS 账本 sha = ____。
- 零漂移：词面路对称差 ____；融合路对称差 ____。
- 显著性：配对差（A3−A0）= ____；95% CI = [____, ____]；双侧 p = ____（命令口径 A0−A3 = ____，CI [____, ____]）。
- §7 求值：(i) ____；(ii) ____（严格更低章：____）；(iii) ____。
- 裁决：采纳 / 不采纳；落地读数（重钉后 `DenseFusionTest` tests/failures/errors = ____；或回滚后三件 sha）____。

### 11.4 0.90 差距更新

- 采纳后生产口径：主集 ____ 距 0.90 差 ____（还差 ____ 题命中）；逐章 0.80 达标 __/24、未达标 __、最小 ____。
- 若 A2 采纳：生产口径取 A2 生产路径读数；若未采纳：生产维持 A0（`limit=5`）。
- 若 A3 采纳：生产融合口径为 RRF(k=60)；若未采纳：维持 α=0.5 归一化融合。

（依据件：`build/agent-batch15/d3/` 的账本、`*-significance.json`、`zero-drift-b15.json`、`chapter-compare-b15.json`、
`sha-*.txt`、`prereg-d3-round2.sha256`。）
