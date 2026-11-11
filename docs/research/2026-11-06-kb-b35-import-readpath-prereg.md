# KB B35 导入读路径缺陷修复 + 导入性能归因（读路径只读化 / 三臂定主因 / 20+20）：预注册（2026-11-06）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-07T01:50:05+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-06` 是批次日（沿用 b34 预注册命名先例）。
> **HEAD**：`3007a2bcef719150e1710737fc3f13c7b2938266`（`git rev-parse HEAD` 实跑）。
> **上游**：批次34 W2 残留两条——**读路径写副作用（高）**与**导入硬门未达**（`docs/kb-batch34-2026-11-03.md:299-300,385-391`，§10.8 #1"下一波首项"）；分阶段边界引用 `docs/research/2026-11-02-kb-r4-progressive-import.md`（下称 R4）；b34 导入臂冻结设计引用 `docs/research/2026-11-03-kb-b34-import-perf-prereg.md`（下称 b34-prereg，其臂已回滚，本文只按原文引用其冻结值，不当作现树事实）。
> **本步唯一新增写盘**：本文件。未改代码/内容包、未跑 Gradle/设备/基准；§0 的读数是本会话 `python -I` / `sha256sum` / `git` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-07T01:50:05+0800
git rev-parse HEAD                             # 3007a2bcef719150e1710737fc3f13c7b2938266
git status --porcelain | wc -l                 # 559
ls docs/research/2026-11-06-kb-b35-import-readpath-prereg.md
#   No such file or directory → 本次新建，只写一次
```

- 写前工作树**不干净**（559 项脏项属他线/已写面）。本预注册**不触碰、不裹挟、不回滚**任何他线改动；不得整树 `git checkout`/`stash`/`reset`。
- 触碰面内已有他线 `M`：`core/data/.../BundledKnowledgeBaseInstaller.kt`、`core/data/.../RoomTutorKnowledgeContextLoader.kt`（批次34 §10.1 W9 遥测接线采纳的未提交改动；`git status --porcelain` 实读）。§4.1 的 pre-sha 是**工作树值**，本臂在该值之上继续编辑，不得回退这两件。
- `core/data/gradle.lockfile` 为 `??`（未跟踪，126 行生成物），§4.1 登记工作树 pre-sha。

### 0.2 现状（实读 / 实算；本表全部为本会话实跑，非引用）

| 事实 | 值 | 来源 |
|---|---|---|
| 读路径写自愈调用点 | `RoomKnowledgeBaseStore.kt:183`（`ensureKnowledgeSearchIndex(subject)`）——**全仓 main 源唯一调用点**（grep 实查，其余命中全为 KDoc） | `grep -rn ensureKnowledgeSearchIndex core/database/src/main/kotlin` |
| 自愈体（待迁出） | `:249-285`：缓存未命中 → `readVersion` + 两条 COUNT → 同版本缺 ⇒ `:268` `withWriteTransaction` 补缺；锚点不等 ⇒ `:284` `searchIndexBuilder.rebuildSubject` | 实读 `RoomKnowledgeBaseStore.kt:249-285` |
| 召回口签名 | 返回**裸 `List<KnowledgeNodeSeedRecord>`**，无完整性标记（未就绪 ⇒ 静默空） | 实读 `port/KnowledgePort.kt:42-47` |
| confirm 路径不写特征 | `RoomProblemOrganizationStore.confirm` 落 `USER_CONFIRMED` 节点但**无特征写入**，仅 `:294` `searchIndexCompleteness::invalidate`——K1 判定缓存 KDoc 明说失效④"靠读时自愈补"（`KnowledgeSearchIndexCompleteness.kt:21-23`） | 实读；`grep insertKnowledgeSearchFeatures RoomProblemOrganizationStore.kt` = 0 |
| 生产召回消费方 | `RoomTutorKnowledgeContextLoader.kt:124`（讲题 KNOWLEDGE_READ B 路）；金标/G3 探针走 `GoldenRetrievalInstrumentedTest` 同 API | 实读 |
| 恢复路径已有写侧重建窄端口 | `KnowledgeSearchIndexRebuildPort`（core:model）：`listKnowledgeSubjects()` + `rebuildKnowledgeSearchIndex(subject)`，实现委托同一 `KnowledgeSearchIndexBuilder`（`RoomStudyDatabase.kt:633-639`）——**6C-2 T5 恢复后重建已在读路径之外** | 实读 |
| 材料写入形态 | **单条 `@Upsert(List)`**，64,855 行级一条事务（`RoomKnowledgeContentReconciler.kt:83-86`）；1,500 行分块不在树（b34 §1.2 已回滚） | 实读 |
| JSON 解析形态 | `readText()` + `parseToJsonElement()` 建整棵树（`BundledKnowledgePackResources.kt:161,175`；侧车 codec `:103`）；流式解析不在树（b34 §1.4 已回滚） | grep 实查 |
| 导入维护在位项 | pragma 调优（`synchronous=NORMAL`、`cache_size=-32768`）与特征索引窗口**在位**（`RoomKnowledgeImportMaintenance.kt:78-128`）；材料索引窗口与 `PRAGMA optimize` **不在树** | 实读全文 |
| 内容身份 | manifest `version=28` / `contentVersion=ce8edb8f89d91fe7` | `python -I` 实读 manifest |
| 完成戳写点 | `BundledKnowledgeBaseInstaller.reconcile` **最后**写 `content_install_state`（`:159-167`）；快路径判定 = `isInstallUpToDate(manifest, recorded)`（`:94-98`） | 实读 |
| 分段日志锚点 | `packs fully parsed in …`（`BundledKnowledgePackResources.kt:92`）；`KnowledgeIndexRebuild` 逐科分段计时（readMs/extractMs/insertMs/deleteMs/anchorMs，`KnowledgeSearchIndexBuilder.kt:179-187`）；材料块日志**不存在**（随 b34 §1.7 回滚） | 实读 |
| 基线读数（引用，本文未重测） | b34 W2 G2 **p95 78,695 ms**（N=20，emulator-5566，优化臂上，`docs/kb-batch34-2026-11-03.md:95`）；批次28 当前形态全量 **127,014 ms**；批次29 优化后 47.5–51.8 s（段：解析 20.7–21.2 s、材料/绑定 20.8–25.2 s、索引 5.4–5.9 s；`b34-prereg §0.2` 引用行）。**当前树（优化回滚后）的基线未测**，执行轮先测（§2.1） | 引用，标注来源 |
| F6 口径 | 首启导入 ≤60 s，读数 = `KnowledgeInstall: install finished in <ms> ms (full path: parse + reconcile)`，同一装置、干净安装（`docs/research/2026-10-25-kb-final-acceptance-criteria-v1.md:149`） | 引用 |

### 0.3 健康机与口径冻结

1. **归因与验收装置（唯一有效装置）**：`emulator-5566` / `sdk_gphone64_x86_64` / API 34 / x86_64（F-005 设备身份同源：`docs/acceptance/ledger.md:586`；批次34 §10.2 同机实测记录）。
2. **每轮测前健康门**（b34 §10.2 口径，冻结）：`/proc/loadavg` 1min ≤ 1.0 且 `MemAvailable` ≥ 1 GiB，逐轮通过才开测。
3. **5562=6.60、5584=9.52 未过门被否**：本行数字是任务书（ask）原文给出的两台被拒装置健康门读数（超 ≤1.0 门），本文按任务书材料冻结，**本会话未重测**。旁证：批次34 §10.2 记录 5584 重启后 loadavg 3.88→14.87→9.88→6.57、5562 的 354,727 ms 按裁定记环境受阻不计入。执行轮若复用 5562/5584，必须先过 §0.3-2 的健康门并逐轮落盘读数。
4. **探针与 G2 分轮**（ask 原文"探针轮（G1/G3 与 G2 分轮）"）：b34 实测探针轮把导入拉到 213,894 ms（`docs/kb-batch34-2026-11-03.md:97`）——G2 读数轮**禁止**并发探针；G1/G3 只在独立探针轮测（§3.4）。

### 0.4 边界声明

1. §1 是**缺陷修复**，不是性能臂；它的验收（读路径零写副作用 + G3 静默空=0）是采纳条件（§2.5）。
2. §2 的三臂由**归因裁决**，不是三选一的赌注：主因落哪段，就实施哪臂的反制；多段并立时按占比降序逐臂（§2.1-3）。除共同写面（§2.3）外，未选中臂的冻结设计**不得**在执行轮顺手实施（那是另一个预注册）。
3. G1 p95 ≤5 s 的**机制前提**：首查非空要等首科特征建好。只有臂1（重建后移 Phase B）给它机制保证；臂2/臂3 被选中时 G1 ≈ 解析+首科重建的串行时点，可能红——按 §2.5 如实报，不得为过门偷偷上分阶段设计。
4. 内容包/包哈希/金标/别名表零改动（§4.3）；本批不重判检索指标（B2b/词面线各有归属）。

---

## 1. 缺陷修复：读路径只读化（写死）

### 1.1 读路径摘除写自愈（代码级冻结）

**位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt`。

1. `readSubjectKnowledgeRecallCandidates`（`:167-194`）删去 `:183` 的 `ensureKnowledgeSearchIndex(subject)` 调用，改调**只读核验** `verifyKnowledgeSearchIndex(subject)`：
   - 判定口径与现行 ensure 前半**逐条一致**（K1 缓存命中 = 零 DAO；未命中 = `readVersion` + `countReviewedKnowledgeNodesBySubject` + `countIndexedKnowledgeNodesBySubject`，复用 `KnowledgeSearchIndexCompleteness` 同一实例与 `markVerifiedCompleteIfUnchanged` 落缓存——缓存是内存写，不是库写，允许）；
   - **任何分支不得写库**：不开 `withWriteTransaction`、不调 `rebuildSubject`、不调 `insertKnowledgeSearchFeatures`；核验结论"不完整"时**不补、不重建**，查询照常返回（缺特征行的查询天然返回子集），但调用方拿到的标记降级（§1.3）。
2. `ensureKnowledgeSearchIndex`（`:249-285`）的**写自愈体整体迁出读路径**，唯一落点 = 导入路径启动自愈：`BundledKnowledgeBaseInstaller.install()` 在快路径判定**之前**（现 `ensureKnowledgeSearchFeatureIndexes()` 旁，`BundledKnowledgeBaseInstaller.kt:40`）新增 `healKnowledgeSearchIndexes()`：
   - 只读核验**全部科目**（科目集复用 `readKnowledgeSubjects`，与 `RoomStudyDatabase.kt:634` 同源）；
   - 核验不完整（锚点缺失/不等，或同版本下 已索引 < 已审校）⇒ 在 `withKnowledgeImportTuning` 窗口内执行现行 ensure 的两个分支原语义（锚点不等 ⇒ 逐科 `rebuildSubject`；同版本缺 ⇒ 事务内补缺）；
   - **快路径命中但核验不完整 ⇒ 不得跳过**（消灭的具体失败：KD-51 取消窗口的"锚点当前+索引半成品"原来靠读路径补缺门兜底，读路径只读后必须有人在导入窗口接住；`KnowledgeSearchIndexBuilder.kt:45-49` 登记的中间态 ②）；
   - 恢复路径既有窄端口 `KnowledgeSearchIndexRebuildPort.rebuildKnowledgeSearchIndex`（`RoomStudyDatabase.kt:636-639`）**不动**——它本来就是读路径之外的写侧重建，与本迁移同向。
3. 顺带核销的引用面（改 KDoc，不改语义）：`KnowledgeSearchIndexBuilder.kt:18,43`、`KnowledgeSearchFeatureExtractor.kt:15`、`KnowledgeSearchIndexCompleteness.kt:9,20,63`、`ProblemOrganizationDao.kt:401`、`KnowledgeSearchIndexStateEntity.kt:17` 里"读路径自愈"的表述改为"导入路径启动自愈"。

### 1.2 confirm 路径同事务写特征（消灭失效④对读自愈的依赖）

**位置**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomProblemOrganizationStore.kt` 的 `confirm` 写事务内。

- 本次确认落库的 `USER_CONFIRMED` 节点，**同一写事务**内补写特征行：行集 = 与 `insertKnowledgeNodes` 同集合的 `toSearchFeatures()`（复用现有抽取器，`KnowledgeSearchIndexBuilder.kt:239-246`）；插入复用 `ProblemOrganizationDao.insertKnowledgeSearchFeatures`（与补缺同款）。
- 既有 `searchIndexCompleteness::invalidate`（`:294`）保留不动。
- 回归钉（写死）：`userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation`（`KnowledgeSearchIndexCompletenessInstrumentedTest.kt:146`，经 `confirmationScenario`→`confirmProblemOrganization`）必须保持绿——读路径自愈摘除后，该用例若红即是本项没做对（先红后绿的验证缝已存在，`KnowledgeSearchIndexCompleteness.kt:21-23`）。
- 幂等性：重复确认/已有特征行的节点不得重复写（插入冲突策略与补缺一致）；执行轮若发现冲突策略与幂等语义不匹配，**停**，登记后另议，不得静默改判定。

### 1.3 完整性标记（IMPORTING 明确降级，不静默空；代码级冻结）

1. **端口签名**：`KnowledgeReadPort.readSubjectKnowledgeRecallCandidates`（`port/KnowledgePort.kt:42-47`）返回值由裸 `List` 改为 `KnowledgeRecallResult(candidates: List<KnowledgeNodeSeedRecord>, indexComplete: Boolean)`；`RoomStudyDatabase` 委托同步改形。
2. **合成口径**（冻结）：最终标记 `completeness ∈ {INSTALLED, IMPORTING}` 在 core:data 装配层合成（生产 = `RoomTutorKnowledgeContextLoader.kt:124` 调用点；探针/金标走同一合成值）：
   - `IMPORTING` ⇔ `!isInstallUpToDate(manifest, readContentInstallState(packId))` **或** `!indexComplete`；
   - `INSTALLED` ⇔ 两者都满足。
   - **单一比较源**：`isInstallUpToDate`（`BundledKnowledgeBaseInstaller.kt:94-98`）抽成公共纯函数，installer 快路径与标记合成共用，**不得**复制第二份比较逻辑。
3. **降级语义**：`IMPORTING` 时结果可以不完整/为空，但**必须带标记**——空 + 无标记 = 静默空，是 G3 的硬失败口径（§3.4，与 b34-prereg §2.2 的冻结定义一致）。
4. `IMPORTING` 判"INSTALLED"是本批最不可接受的变绿（§2.6）；标记判据不得放宽。

### 1.4 观测面（共同；b34-prereg §1.7 的日志行集合原样复用）

| 日志行（tag 固定 `KnowledgeImport`，字段顺序固定） | 发出位置 |
|---|---|
| `pack parsed packId=<id> subjects=<n>` | 主包解码后、侧车循环前 |
| `sidecar parsed index=<i>/<n> packId=<id>` | 每个侧车解码后 |
| `sources+nodes written pack=<id> nodes=<n> sources=<n>` | 节点/来源写完后 |
| `node bindings+relations written pack=<id> bindings=<n> relations=<n>` | 绑定/关系写完后 |
| `material chunk=<i>/<n> pack=<id> rows=<n>` | 每个 1,500 行块提交后（2025 为 1/44…44/44，2020 为 1/1） |
| `material bindings written pack=<id> rows=<n>` | `replaceMaterialBindings` 返回后 |
| `material retire pass done pack=<id> retired=<n>` | `retireMaterials` 返回后 |
| `feature indexes dropped phase=window-enter` | 特征索引窗口 DROP 后 |
| `feature index rebuilt subject=<subject>` | `rebuildSubject` 返回后 |
| `feature indexes ensured phase=startup-heal / window-close` | 起点 `healKnowledgeSearchIndexes` 补建 / 窗口 finally 补建 |
| `knowledge search heal issued subjects=<n> incomplete=<k>` | `healKnowledgeSearchIndexes` 核验完成、进入修复前 |

- 只读局部变量，不写库、不改顺序、不参与断言；不含学生数据/PII。
- 臂3专属行（material indexes dropped/ensured、optimize issued/done）只在该臂被归因选中时存在（§2.2）。
- 这些行是 §3.3 的 kill 触发点与 §2.1 的分段证据；**不允许**为日志好看延迟/合并写入。

### 1.5 §1 的验收（写死；全部进采纳条件）

| # | 验收项 | 判据 |
|---:|---|---|
| 1 | 静态守卫测试（JVM，源码扫描——只证明"该文件该函数体不含该字符串"，同 b34 §1.6 先例） | `RoomKnowledgeBaseStore.readSubjectKnowledgeRecallCandidates` 函数体内不含 `withWriteTransaction`、`rebuildSubject`、`insertKnowledgeSearchFeatures`、`ensureKnowledgeSearchIndex` |
| 2 | 仪器化只读探针用例 | 构造"已审校节点缺特征行"的库态，调 `readSubjectKnowledgeRecallCandidates`：特征行计数**不变**、无异常、返回标记 `indexComplete=false`（合成后 `IMPORTING`） |
| 3 | K1 确认后立刻召回用例 | `userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation` 保持绿（confirm 同事务写特征的回归钉） |
| 4 | 导入期写副作用实测 | §3.4 探针轮：G3 静默空 **=0**（每次空结果必带标记） |

---

## 2. 性能：三臂归因定主因（写死）

### 2.1 归因协议

1. **先测当前树基线**（观测日志 §1.4 落地后）：健康机同口径冷装 **N=3**（N<20 只报 median/max，不报 p95），逐轮落分段读数。
2. **分段口径**（冻结）：
   - 解析段 = `packs fully parsed in <ms>`（`BundledKnowledgePackResources.kt:92`）；
   - 特征重建段 = Σ `KnowledgeIndexRebuild: rebuild subject=… tookMs=`（`KnowledgeSearchIndexBuilder.kt:179-187`，现有）；
   - 材料写入段 = Σ `material chunk=…` 块耗时（§1.4 新增；按块间隔墙钟差求和）；
   - 其余段 = G2 总时长 − 三段和（调和对账残差，如实报）。
3. **主因判据**（冻结）：占比最大段 **≥40%** ⇒ 定为单主因；无段 ≥40% ⇒ 无单主因，按占比降序**逐臂**实施反制（每实施一臂重测分段），门不变。归因结论与占比读数落 §6。
4. 旧分段读数（批次29 的 20.7–21.2/20.8–25.2/5.4–5.9 s）是**优化臂上**的历史值，当前树不含那些优化——只作机制参考，**不得**替代本轮实测归因。

### 2.2 三臂反制（按任务书原文冻结映射）

| 臂 | 主因落段 | 反制（冻结） | 冻结设计来源 |
|---:|---|---|---|
| 1 | 特征重建/索引 | **全部延后到 Phase B 后台**：Phase A/B 边界、逐科 READY、"不在 Phase A 写 `content_install_state`"按 R4 §3.1 原文；Phase B 用唯一 `CoroutineWorker`（`enqueueUniqueWork KEEP`）承载（R4 §3.5(e)）。**G2 读数必须覆盖到 Phase B 完成戳写入**——`install finished (full path…)` 行不得在 Phase B 完成前发出；把后台工作挪出测量 = 假绿（§2.6） | R4 §3.1/§3.5 |
| 2 | 解析 | **按需字段流式解析**：主包+侧车改 `InputStream` + `decodeFromStream` + DTO/自定义 `KSerializer`，严格校验逐条保留（`requireOnlyKeys`/必需可空/all-or-none/枚举/trim/跨引用），**28 文件 decode-golden 等价门先于改 codec 生成、28/28 逐行相等** | b34-prereg §1.4/§2.4 原文复用 |
| 3 | 材料写入 | **1,500 行/事务保留并核对是否生效**：`MATERIAL_UPSERT_BATCH_SIZE = 1_500` 冻结值原样（b34 §1.2），"核对生效" = ① `material chunk=` 日志 2025 44 块 + 2020 1 块逐块命中；② 材料写入段占比相对基线下降。**不得**扫参改 1,000/2,000 凑门；材料索引窗口（b34 §1.3）与 `PRAGMA optimize`（b34 §1.5）**不属于本臂**，门未过需加时另立预注册 | b34-prereg §1.2 |

### 2.3 共同写面（不随归因变化；先于归因基线落地）

**材料写入 1,500 行/事务 + 分块日志**是本批**共同写面**（即使臂3未被选中也在 B35 构建内）：

- 它消灭的两个具体失败：① §3.3 的 kill 点"材料分块 1/5/10/20/30/40/44 与绑定阶段"只有分块提交+日志才**机制可达**（b34 实测"只清完成戳"装置下 kill 点 7–14 不可达，`docs/kb-batch34-2026-11-03.md:96`）；② 单条 64,855 行大事务占住唯一写连接整段（R7 §1.2 的原始失败，b34 §1.2 消灭失败原文）。
- 语义不变量照抄 b34 §1.2：行集合/行序/计数/跳过原因/退役集合全不变；崩溃在块 k 后 ⇒ 前 k 块已提交，重跑收敛（完成戳最后写）。
- 改动位置：`RoomKnowledgeContentReconciler.kt:83-86`（chunked(1_500) 逐块 `upsertMaterials`）。

### 2.4 门表（按任务书原文冻结；F6 口径 = §0.2 末行）

| 门 | 冻结值 | 性质 |
|---|---|---|
| G2 全量 | **p95 ≤ 60,000 ms** | **硬门**（F6 口径读数；采纳条件） |
| G2 挑战线 | ≤ 45,000 ms | 挑战（只报不判门） |
| G1 首查 | p95 ≤ 5,000 ms | 达标线（必报；机制前提见 §0.4-3；红不静默） |
| G3 延迟 | p95 ≤ 250 ms | 达标线（必报；红不静默） |
| G3 静默空 | **= 0** | **硬门**（§1 修复的直接度量；采纳条件） |

- p95 = nearest-rank，不插值；N≥20 才允许报 p95。
- G1 定义沿用 b34 §2.1：冷装 T0 → 首次返回非空检索结果（首科 MATH、金标第一条、生产检索 API）；G3 定义沿用 b34 §2.2：四科金标各一条、`T0+500 ms` 起每 250 ms 轮询至 G2 完成，样本=全部轮询点，p95 只统计导入期。

### 2.5 采纳条件（写死）

**采纳 ⇔ 同时满足：**

1. §1.5 的 4 项验收全过（读路径零写副作用 + K1 用例绿）；
2. **G3 静默空 = 0**；
3. **G2 p95 ≤ 60,000 ms**（F6 口径；臂1 采纳时读数覆盖 Phase B 完成）；
4. **崩溃注入 20/20**（§3.3）。

**否则回滚**（任一失败、样本不足、装置/APK/内容包不一致，都按"不采纳"）。

- G1 p95 ≤5,000 ms 与 G3 p95 ≤250 ms 红时：§6 单列归因，报告不得写"达标"；是否因达标线红而回滚由 owner 显式裁定（本预注册默认不回滚，但**不得声称达标**）。挑战线 45 s 只报不判。
- 若臂1 采纳而 G1 仍红：如实报，不得据此悄悄加 Phase A 改动（另立预注册）。

### 2.6 不允许的"变绿"方式

- 把 Phase B 后台工作挪出 `install finished` 测量窗口（臂1 专属红线）。
- `IMPORTING` 判成 `INSTALLED`；删标记判据；把"没有查询发生"当 0 次静默空。
- 只跑 3 次冷装就报 p95；用 `pm clear` 代替 uninstall；同一进程热重启当冷装。
- 改 1,500 冻结值扫参；为 G1/G3 红偷偷上未选臂或 Phase A 分阶段改动。
- 删断言、放宽 `requireOnlyKeys`、开 `ignoreUnknownKeys=true`、把 `synchronous` 改 OFF、关外键（b34 §3.2 全文继续有效）。

---

## 3. 验收（写死）

### 3.1 装置与口径

- 装置：`emulator-5566`（§0.3 唯一有效装置；指纹记录 `ro.product.model`/`ro.build.version.sdk`/`ro.product.cpu.abi`/`/proc/meminfo` 前 3 行/PAGESIZE）；健康门逐轮落盘。
- APK：同一 sha256、同一 versionCode；内容包：manifest `version=28` / `ce8edb8f89d91fe7`（§0.2 实读值），主包/侧车零字节改动（§4.3）。
- 冷装 = `adb uninstall` → `pm path` 为空 → `adb install -t` → `logcat -c` → `am start -W`；T0 = `am start -W` 前的 `date +%s%3N`。
- 计时来源 `System.nanoTime()` / `date +%s%3N`；不得用分段相加推算 G2。

### 3.2 G2 轮（健康机 N=20 冷装）

- **20 次真冷装，无探针**（§0.3-4 分轮红线）；逐次 `install finished in <ms> ms (full path…)`、p50/p95/max、装置/APK/内容包指纹、分段读数全部落盘；同一次安装只允许一行 full-path 完成行。
- 判读：硬门 p95 ≤60,000 ms；挑战线 45,000 ms 只报。

### 3.3 崩溃注入（20/20；kill 点覆盖材料分块 1/5/10/20/30/40/44 与绑定阶段）

**注入方式**：ADB 外部 kill（`am force-stop` 或 `kill -9`）在指定进度标记出现后立即执行；每次只杀一次；20 次指 **20 次有效命中**，未命中重试，额外尝试照实记。

**装置修正（消灭 b34"kill 点 7–14 不可达"的具体失败）**：每次注入流程 =

1. 装 APK → 等**首次**全量导入完成 → 确认内容计数达标；
2. 驱动测试 seed 12 张用户表（表清单与规范化 sha 口径照抄 b34 §2.3：`problem`/`problem_revision`/`practice_unit`/`error_book_entry`/`review_plan`/`review_queue_item`/`review_log`/`learner_problem_memory_state`/`learner_knowledge_mastery_state`/`attempt_submission`/`tutor_conversation`/`tutor_message`）→ 快照（COUNT + 主键序全字段规范化 sha256）；
3. 强制重跑：清 2025 包 `content_install_state` 行 **并删除 2025 包材料面**（先 `knowledge_teaching_material_node_binding` 后 `knowledge_teaching_material`，按外键依赖序、经生产 DAO）——只删材料面就能让 `planMaterials` 全量重写、chunk 标记可达；**干跑验证一次标记可达后**注入轮才有效；
4. 等指定 kill 标记 → kill；重开 → 等 `install()` 结束 → 恢复断言。

**恢复断言（每次注入）**：12/12 用户表计数与 sha 逐表相等；全部非知识表计数零变化；`PRAGMA integrity_check=ok`；知识表计数与 §3.5 表一致；材料/特征索引在位；最终 `content_install_state.contentVersion = ce8edb8f89d91fe7`。

**kill 点池（冻结）**：

| # | 命中标记 | 必命中 |
|---:|---|---|
| 1 | `pack parsed packId=moe-2025-four-subjects-v1` | |
| 2 | `sidecar parsed index=1/25 packId=…` | |
| 3 | `sidecar parsed index=13/25 packId=…` | |
| 4 | `packs fully parsed in <ms>`（现有） | |
| 5 | `sources+nodes written pack=…` | |
| 6 | `node bindings+relations written pack=…` | |
| 7–12 | `material chunk=1/44`、`5/44`、`10/44`、`20/44`、`30/44`、`40/44` | **必命中** |
| 13 | `material chunk=44/44`（2025 材料全写完、绑定未写） | **必命中** |
| 14 | `material bindings written pack=…`（绑定阶段） | **必命中** |
| 15 | `material retire pass done pack=…`（绑定阶段尾） | **必命中** |
| 16 | `feature indexes dropped phase=window-enter` | |
| 17 | `feature index rebuilt subject=…`（第一科） | |
| 18 | `knowledge search heal issued …`（§1.4；验证启动自愈） | |
| 19 | `feature indexes ensured phase=startup-heal` | |
| 20 | `feature indexes ensured phase=window-close` | |

- 臂3被选中时，kill 点池追加 material indexes dropped/ensured 两点（编号顺延），必命中项不变。
- 20 个有效命中从池中取；池中标记在该构建不存在（未选臂）的点自动不占名额。

### 3.4 探针轮（G1/G3；与 G2 轮分开）

- 独立轮次：同样 N≥3（冷装口径）或与 G2 轮同 N=20 由执行轮按预算定，但**必须在 §6 写明轮数与理由**；探针只落探针轮读数，绝不与 G2 轮混报。
- G1：`T0+500 ms` 起每 250 ms 发首科查询直到首次非空；G3：四科金标各一条每 250 ms 轮询至 G2 完成；每次记录墙钟、`results.size`、合成标记。
- 判读：G3 静默空=0（硬门）；G3 p95 ≤250 ms、G1 p95 ≤5,000 ms（达标线，必报）。

### 3.5 恢复内容断言表（引用 b34 §2.3 冻结值；执行轮以基线轮实测校准，不符时以实测为准并在 §6 登记）

| 表 | 期望 |
|---|---:|
| `knowledge_node` | 3,733 |
| `knowledge_source` | 62 |
| `knowledge_node_source_binding` | 3,299 |
| `knowledge_node_relation` | 350 |
| `knowledge_teaching_material` | 64,855 |
| `knowledge_teaching_material_node_binding` | 64,857 |
| 完成戳 | `ce8edb8f89d91fe7`（2025 包） |

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 既有代码（pre-sha256 = 写前工作树值，本会话 `sha256sum` 实跑；post 由 §6 落）

| # | 路径 | 预期动作 | pre-sha256（实测） | porcelain | post-sha |
|---:|---|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 读路径摘除写自愈、只读核验、返回 `indexComplete`（§1.1/§1.3） | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` | 干净 | §6 |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 召回签名改 `KnowledgeRecallResult` | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | 干净 | §6 |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托改形 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | 干净 | §6 |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomProblemOrganizationStore.kt` | confirm 同事务写特征（§1.2） | `7f66821d9f2d79ca33d6c8780c212d3ac0fcd90b06f683c93a9390700178d398` | 干净 | §6 |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeContentReconciler.kt` | 材料 1,500 行分块（§2.3 共同写面） | `e6ef7cc01342ca2cd3035b08d395d4ad1e8d61b5d7e72334f6c255bdd64e3142` | 干净 | §6 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt` | heal 用只读查询接线（如需） | `6ed4e0d3f90e3d60b9581f1b1d4ef4897ea168d56372d37df86607452c239574` | 干净 | §6 |
| 7 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstaller.kt` | 启动自愈 `healKnowledgeSearchIndexes`、进度日志、`isInstallUpToDate` 抽公共纯函数、（臂1 时 Phase B 边界） | `c8a429f64782203e2176295f547720db7a203e9b749ac09ce735a16b435fa585` | **`M`（他线 W9 遥测，不得回退）** | §6 |
| 8 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorKnowledgeContextLoader.kt` | 标记合成与消费（§1.3-2） | `0f6528a880d48257d16761801e8f6bfb3ed45396925821da8fcf60057338809c` | **`M`（他线 W9 遥测，不得回退）** | §6 |
| 9 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt` | 仅臂3：材料索引窗口 + optimize（b34 §1.3/§1.5 原文） | `eacf9b0c80885b5e4a7501cc99712ba73815d59b9778b864df80928cf2ae3bee` | 干净 | §6 |
| 10 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgePackResources.kt` | §1.4 日志；仅臂2：流式解码 | `4525edaa1f65b5457e8fc80116ec700df5dc15cac5868f6526108932598bd7d2` | 干净 | §6 |
| 11 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt` | 仅臂2：流式解码 | `80053e2d7bc458bb05a14a594cae04e20b1e048ba0bc5f6c18dad94e5cb5ecf7` | 干净 | §6 |
| 12 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` | 仅臂1：Phase B Worker 接线；availability 语义随标记核对 | `20bc8de8ce6e7f928c0ad9bc52efbb71f054822524f92198208aba5e67efc219` | 干净 | §6 |
| 13 | `gradle/libs.versions.toml` | 仅臂2：`kotlinx-serialization-json-io` 坐标 | `a3b18a448f6629f2b1df7c7d4842606ca8fd412934f5d23ea891de877a830856` | 干净 | §6 |
| 14 | `core/data/build.gradle.kts` | 仅臂2：一条 implementation | `a63e5beceab8ddb7764adf2a82dd2c069bd944d45efd295dbbf68b3588875ed3` | 干净 | §6 |
| 15 | `core/data/gradle.lockfile` | 仅臂2：Gradle 重生成 | `daca64b930a498f8eede7df3c707ccbb5745d8731374db8957c7e3194358525d` | `??` | §6 |

- 未选中臂的文件**必须保持 pre-sha**（#9 仅臂3、#10/#11 的流式部分仅臂2、#12 仅臂1、#13–#15 仅臂2）；执行轮结束时逐件核对，触碰了未选臂的文件 = 违反 §0.4-2，按未采纳处理。

### 4.2 B. 新增文件（新增面；回滚时删除）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/database/src/test/kotlin/.../KnowledgeSearchReadPathGuardTest.kt` | §1.5-1 静态守卫 + 只读核验 JVM 用例 |
| 2 | `core/data/src/androidTest/kotlin/.../knowledge/KnowledgeRecallMarkerInstrumentedTest.kt` | §1.5-2 只读探针 + 标记断言 |
| 3 | `core/data/src/androidTest/kotlin/.../knowledge/KnowledgeImportCrashDriverInstrumentedTest.kt` | §3.3 装置（seed、清戳+删材料面、等 kill）——b34 §4.2 #4 原样重建 |
| 4 | `core/data/src/androidTest/kotlin/.../knowledge/KnowledgeImportRecoveryInstrumentedTest.kt` | §3.3 恢复断言 |
| 5 | 仅臂2：`core/data/src/test/resources/knowledge/decode-golden.txt` + 等价测试 | 28 文件差分门（改 codec **前**生成） |
| 6 | 仅臂1：Phase B 状态机/Worker 文件（R4 §3.2/§3.5(e) 对应物） | 执行轮按 R4 原文落，逐件进 §6 |
| 7 | `tools/kb_perf/cold_install_20x.sh`、`crash_inject_20x.sh`、`summarize_import_perf.py` | b34 §4.2 #6–8 原样重建 |

### 4.3 C. 零触碰

1. `core/data/src/main/resources/knowledge/**`（主包/26 侧车/manifest/index/向量）——一个字节不改。
2. 学生数据表 / 备份恢复 / 金标 `tools/kb_coverage/tables/golden_queries_v2.json` / alias_map。
3. 他线改动：#7/#8 的 W9 遥测内容、559 项脏项的其余全部——不得回滚、不得裹挟。
4. `KnowledgeSearchIndexCompleteness` 判定口径与缓存失效接线（①②③④）语义不变；#7/#8 之外的端口方法不新增。
5. 恢复路径 `KnowledgeSearchIndexRebuildPort` 现有实现不动。

### 4.4 写前备份 / 回滚 / 采纳

1. 备份：§4.1 全部路径复制到 `build/agent-batch35/readpath/backup/`，落 sha256 全表；核对 pre-sha 与 §4.1 逐件相同。
2. 回滚：不得整树 checkout/stash/reset；用备份覆盖 §4.1 触碰件、删除 §4.2 新增件；不得 `pm clear`/`uninstall` 学生数据（用 `adb install -r` 装回基线 APK）。
3. 回滚后复核：`PRAGMA integrity_check=ok`；§3.5 表计数达标；#7/#8 恢复到**工作树 pre-sha**（含他线 W9 改动，不是 HEAD 值）；定向测试（§1.5 前三项 + `GoldenRetrievalJvmTest`）exit 码落盘。
4. 采纳：只有 §2.5 四条同时满足；§6 登记 post-sha、分段读数、20+20 全量读数与 §1.5 四项证据。

---

## 5. 未做 / 未验证（写作时点）

- **未改任何代码/内容包**；§1/§2 是冻结设计。G1/G2/G3、20+20、归因基线全部 `NOT_RUN`。
- **当前树（优化回滚后）的全量导入基线未测**——§2.4 的门相对它判读，相对 b34 W2 的 78,695 ms 只作参考对照。
- **5562=6.60、5584=9.52 未重测**：任务书原文材料冻结（§0.3-3）；健康门在执行轮逐轮实测。
- **标记合成的并发窗口未证明**：查询与标记读取非同一事务，`IMPORTING→INSTALLED` 的竞态只影响标记方向（偏保守），不虚报完整——执行轮用 §1.5-2 用例钉住。
- **删材料面装置的外键可达性未干跑**：§3.3 步骤 3 要求干跑验证后才算有效装置；若 RESTRICT 阻断（有未核对的引用表），如实登记，不得把未命中的 kill 算通过。
- **臂1 的 Phase B 状态机、臂2 的 DTO 严格校验可行性**均未实现未验证——沿用各自来源文档（R4 §3、b34 §1.4/§5）的已知风险登记，执行轮先过各自前置门。
- **G1 在臂2/臂3 被选中时预计红**（§0.4-3），不构成回滚理由，构成如实报告义务。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；备份与回滚核验；§4.1/§4.2 逐件 post-sha；§1.5 四项证据；归因基线（N=3 分段读数 + 主因判读 + 臂选择）；20 次冷装逐次读数与 p50/p95/max；20 次崩溃注入逐次 kill 点/命中标记/恢复路径/integrity_check/知识表计数/12 用户表与非知识表快照比对；探针轮 G1/G3 逐轮询点读数、overBudget、静默空逐次计数；§2.5 的采纳/回滚判词与 G1/G3 达标线的 owner 裁定记录。未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## §6 执行轮记录（2026-11-07，导入实现员会话；本节为追加，未改 §1–§4）

### 6.0 写前/写后守卫

- 写前：`date` = 2026-11-07T02:03:13+0800；HEAD = `3007a2bcef719150e1710737fc3f13c7b2938266`；`git status --porcelain | wc -l` = 560（与本预注册 §0.1 的 559 基本一致，属他线在途，未回滚未裹挟）。
- 写后（本节追加时）：HEAD 不变（未提交）；porcelain = 594（+35 = 本轮触碰面 25 M + 3 新增源文件 + 本节与 evidence 文件）。
- 备份（§4.4-1）：§4.1 全部 15 件已复制到 `build/agent-batch35/readpath/backup/`，`backup-pre-sha256.txt` 记录 15/15 OK，**与 §4.1 pre-sha 逐件相同**（实测核对）。未整树 checkout/stash/reset；5562/5584 未动；学生数据未 uninstall（性能轮的 uninstall 只作用于应用包 `com.tingyun.smartmistakebook.offline` 自身）。
- post-sha：`build/agent-batch35/readpath/post-sha256.txt`。**逐件核对：CHANGED 10 件 = #1–#5、#7、#8、#9（仅 §1.4 共同日志两行：window-enter/window-close；臂3 的材料索引窗口与 optimize 未做）、#10、#11；UNCHANGED 5 件 = #6（heal 无需新 DAO 接线）、#12（仅臂1）、#13/#14/#15（见 6.4 偏差 ①）**。

### 6.1 §1.5 四项验收

| # | 判据 | 结果 | 证据 |
|---:|---|---|---|
| 1 | 静态守卫（JVM 源码扫描）：`readSubjectKnowledgeRecallCandidates` 函数体不含 `withWriteTransaction`/`rebuildSubject`/`insertKnowledgeSearchFeatures`/`ensureKnowledgeSearchIndex`；另加 `verifyKnowledgeSearchIndex` 只读断言与 heal 写体存活断言 | **PASS** | `KnowledgeSearchReadPathGuardTest` 3/3 绿（core:database JVM，`./gradlew :core:database:testDebugUnitTest` BUILD SUCCESSFUL） |
| 2 | 仪器化只读探针：构造"已审校节点缺特征行"，召回 ⇒ 特征行计数不变、无异常、`indexComplete=false` | **PASS** | `KnowledgeRecallMarkerInstrumentedTest` **OK (2 tests)**（emulator-5566 实跑；含"空结果必带标记"断言 + 合成标记 INSTALLED/IMPORTING 两向 + 自愈收敛） |
| 3 | K1 回归钉 `userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation` 保持绿 | **PASS** | `KnowledgeSearchIndexCompletenessInstrumentedTest` **OK (7 tests)**（emulator-5566 实跑；确认路径同事务特征写入后主钉绿） |
| 4 | G3 静默空 = 0（探针轮） | **NOT_RUN** | 属 §3.4 探针轮 + 20/20 验收（本会话预算外；标记机制已由 #2 的"空结果必带标记"用例覆盖） |

- 测试适配（机制变更连带，逐件登记）：KD-51 chunking 类第 4 用例与 K1 类 `unreviewedFeatureRowsCannotInflateTheIndexedCountPastTheReviewedCount` 原以"读路径补缺"为修复通道——按 §1.1 改为"读路径判不完整（带标记、不写）→ `healKnowledgeSearchIndex` 收敛"，两用例在设备实跑绿；K1 canary 的前提（确认不建特征行）被 §1.2 消灭，改为钉"摘掉失效接线新确认节点仍可见（可见性由同事务特征写入承载）"，设备实跑绿。**这三处是断言改写，不是删断言/放宽判据**；原机制被 §1.2/§1.1 有意消灭（预注册原文的采纳条件）。
- 单测全绿（ask ③）：`:core:database:testDebugUnitTest` 51 套 251 用例 0 失败；`:core:data:testDebugUnitTest` 196 套 1,650 用例 0 失败（含 decode-golden 28/28 等价门、installer 快路径、telemetry 接线）；`:app:testStrictOfflineDebugUnitTest` BUILD SUCCESSFUL。androidTest 编译：core:database/core:data/feature:library/app 四处 `compile…AndroidTestKotlin` BUILD SUCCESSFUL。
- 解码等价门（臂2 前置硬门，b34 §2.4）：golden 于**改 codec 前**由未改实现生成（`KB_DECODE_GOLDEN_GENERATE=true` 实跑，28 行落 `core/data/src/test/resources/knowledge/decode-golden.txt`），改后同一测试比对 **28/28 逐行相等**。

### 6.2 归因基线与臂选择（引用 w1/attribution.json，本轮未重测基线）

- 分段（N=3 中位）：解析 24,621 ms（39.6%/40.4%/39.8%，中位 40.4%）；材料（pack1+pack2 括号）≈20.1 s（33.0%）；重建+索引 ≈14.4 s（23.8%）。
- 主因判读：解析段为最大段且中位 ≥40% ⇒ **单主因 = 解析段 → 臂2**（§2.1-3）。执行记录：归因轮以派生括号测材料/索引段（无生产日志锚点，attribution.json caveats#1），本执行轮已把 §1.4 观测日志落地，后续轮次材料/索引段可直读。

### 6.3 性能第二轮（臂2 落地 + §2.3 共同写面后的重测；健康机 emulator-5566，N=3 冷装）

- 装置：`emulator-5566` / sdk_gphone64_x86_64 / API 34 / x86_64；APK sha256 = `3f19f5962f19c5e6600f0a9b4fd4c632285f29c8021fabe444cdb6024f38fdae`（strictOffline debug，versionCode 2；内容包四元组与 baseline identity 一致——零字节未改）。健康门逐轮实测：run-1 pre 0.45/1.15GiB、run-2 pre 0.97/1.14GiB、run-3 pre 0.84–0.92/1.20GiB，全部过门；post-gate drift 已记录（informational）。
- 冷装逐次（`install finished in <ms> ms (full path: parse + reconcile)` 直读）：**67,524 / 66,321 / 66,101 ms**（中位 66,321，max 67,524；N<20 不报 p95）。
- 分段逐次（ms，直读/派生同 attribution 口径）：

| 段 | run-1 | run-2 | run-3 | 中位 | 归因基线中位 | Δ |
|---|---:|---:|---:|---:|---:|---|
| 解析（直读 packs fully parsed） | 24,264 | 23,827 | 24,356 | 24,264 | 24,621 | **−1.4%（噪声级，无改善）** |
| 材料括号 pack2（[pack1 末 rebuild end, pack2 首 rebuild start]） | 28,963 | 28,786 | 28,408 | 28,786 | 20,047 | **+43.6%** |
| 重建 pack2（逐科 tookMs 和） | 13,719 | 13,918 | 13,495 | 13,719 | 13,009 | +5.5% |
| 索引尾（派生括号） | 2,341 | 1,731 | 1,521 | 1,731 | 1,416 | +22% |
| 材料 44 块提交跨度（§1.4 直读，本轮新增） | 12,464 | 12,058 | 12,252 | 12,252 | （基线无直读） | — |
| 启动自愈（heal issued） | subjects=0 incomplete=0（全新库无科目可核验） | 同 | 同 | — | — | — |

- **判读（如实）**：① 臂2 流式解析对解析段**无改善**（中位 −1.4%，噪声级）——"建整棵 JsonElement 树是解析段主成本"的前提在本装置/本内容上**被证伪**：解析段的主成本是逐材料指纹 SHA-256（64,855 × ~2 KB）+ 记录构造与严格校验的固有 CPU/内存工作（机制推断，标注为推断；流式后输出对象图 64,855 条材料仍必须驻留，删去的只是中间树）。② 全量中位 66,321 vs 基线中位 60,905（**+5,416 ms / +8.9%**；基线 max 66,530 与本轮 max 67,524 同量级）——不能归因到单一因素：本轮构建同时含共同写面（1,500 行分块 = 45 个短事务）与流式；基线括号内无子段日志，材料括号 +43.6% 无法在基线上分解（如实登记为 UNVERIFIED）。③ 按本轮分段，材料括号占比 28,786/66,321 = 43.4% ≥40% 成为最大段——但它的反制（臂3 材料 1,500 行分块）已是本批共同写面且未见降低该段；进一步动作（材料索引窗口/optimize、扫参）按 §0.4-2 **另立预注册**，本执行轮不做。
- **§2.5 采纳判词：不满足**。四条件中 §1.5-1/2/3 过、G3 静默空 NOT_RUN、G2 p95 ≤60,000 未达（N=3 中位已 66,321 > 60,000，且 N<20 不报 p95）、崩溃注入 20/20 NOT_RUN。**按 §2.5"否则回滚"的字面，本轮改动处于"未采纳"状态**；是否回滚由 owner 裁定（§2.5 默认不回滚的条款仅覆盖 G1/G3 达标线红，不覆盖 G2 红）。如实陈述：§1 缺陷修复部分（读路径只读化 + 标记 + confirm 同事务写特征 + 启动自愈）独立于性能门，其 §1.5 前三项验收全绿；性能臂（臂2 + 共同写面）未达到 G2 门。
- 轮数与理由（§3.4 要求）：本轮 N=3，与归因基线同 N，目的是**分段对照**而非门判定；G2 的 20 次冷装与探针轮/崩溃注入 20/20 留给 20+20 验收轮（本会话预算外）。本轮**不含**金标首查仪表化步骤（它是 §3.4 探针轮读数，按分轮红线不与冷装轮混跑）。

### 6.4 偏差与登记

1. **臂2 未新增 `kotlinx-serialization-json-io` 依赖（#13/#14/#15 保持 pre-sha）**：`Json.decodeFromStream(InputStream)` 已在现有 `kotlinx-serialization-json-jvm:1.9.0`（`JvmStreamsKt`，jar 内核实）——b34 §1.4-3 冻结的坐标新增是为引入该 API，API 已在场则新增为死重；流式入口/严格校验/等价门等实质冻结项全部照做。**"不得在导入路径新增 Json{ignoreUnknownKeys=true}"照守**（两 codec 的 Json 配置逐字未动）。
2. **未知键错误文案**：根/对象级未知键由 kotlinx 非宽松解码器拒绝（"Encountered an unknown key '<键>'"），非旧 `requireOnlyKeys` 的 "contains unknown keys" 文案——按 b34 §1.4-4"允许保留语义、改文案"，涉及断言 6 处已同步改为 `contains("unknown key")`；键名点名保留。schema 相关键集错配（v1 带 coverage、v1–v3 带 provenance/verdict 三键等）由实现方自产文案，仍为 "contains unknown keys" 原词汇。
3. **端口面新增**：`KnowledgeWritePort.verifyKnowledgeSearchIndexCompleteness()` 与 `healKnowledgeSearchIndex(subject)`（均带 no-op 默认，测试替零改动）——§1.1-2 的 heal 落点在 #7（installer）所需的最小端口面，§4.3-4 "#7/#8 之外的端口方法不新增"的边界内（#7 所需）。
4. **androidTest 调用点机械适配**：召回口签名改形连带 19 处 `.candidates`（10 文件）+ 5 个 codec 测试文件的流入口包装；逐件列于 `build/agent-batch35/w1/touched-files.txt`。`RoomMistakeOrganizationRepository`/`RoomTutorToolRunner` 为召回口消费方（.candidates 解包，语义不变）。
5. **未做/未验证**：G2 20 次冷装、G1/G3 探针轮、崩溃注入 20/20、PRAGMA optimize、材料索引窗口——均 NOT_RUN/未实施；本轮全量 +8.9% 的成因分解 UNVERIFIED；`IMPORTING→INSTALLED` 竞态窗口仅按 §5 的登记保持"偏保守"论证，未加新证明。
