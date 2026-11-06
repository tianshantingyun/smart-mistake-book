# KB B33 首启渐进导入（两阶段 / 状态机 / 降级查询 / `synchronous` 修复）：预注册（2026-11-02）

> **性质**：出数前冻结。§1–§5 写死；执行轮只允许在文末追加 §7，不得回改 §1–§5。
> **写作时点**：2026-11-03T04:04:46+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-02` 是批次日，不是落盘日。
> **HEAD / origin/main**：`364ee02570c267d761c2bcb9e9d3cc5075f8199d` / `62522930bc8bae51be1523c954f39449e054b6af`（`git rev-parse` 实跑；**本会话期间 HEAD 由 `62522930` 前进到 `364ee025`（他线提交）**，本文件不裹挟这些提交）。
> **上游**：`docs/research/2026-11-02-kb-r4-progressive-import.md`（下称 **R4**，本文件逐节引用）；`docs/kb-acceptance-2026-10-17.md:824,829`（F6 后导入分段）；`docs/kb-batch29-2026-10-31.md:46-64`；源码锚点见 §0.2。
> **本步唯一新增写盘**：本文件。未改源码、未跑 Gradle/设备/JVM/判官；§0.3 的计数是本会话用 `python -I`/`sha256sum`/`grep` 实读，非设备读数。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                  # 2026-11-03T04:04:46+0800
git rev-parse HEAD                            # 364ee02570c267d761c2bcb9e9d3cc5075f8199d
git rev-parse origin/main                     # 62522930bc8bae51be1523c954f39449e054b6af（本会话 HEAD 已前进，见上）
git status --porcelain | wc -l                # 563
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   463 ??
#   100  M
git diff --cached --name-only | wc -l         # 0（暂存区空）
git status --porcelain -- <§5.1/§5.2 触碰面>   # 空（本臂触碰面写前干净）
ls docs/research/2026-11-02-kb-b33-progressive-import-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 563 项脏项属他线与已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`。
- **工作树在本会话内被他线推进过**（HEAD 从 `62522930` → `364ee025`，脏项 585 → 563）：执行轮开始前必须重新实跑本守卫并把新 HEAD/脏项写进 §7；本文件的 pre-sha 是 **04:04:46 时点**的工作树值。

### 0.2 现状（源码实读，逐条）

| 事实 | 出处 |
|---|---|
| 导入入口：一次 `install()` 跑完解析 + 调和 + 每科索引 + 完成戳 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstaller.kt:30-71` |
| 快路径：`manifest.contentVersion == recorded.contentVersion` → 直接返回 | 同上 `:44-55`；`isInstallUpToDate` `:81-85` |
| 解析：**一次 `load()` 把 2 个主包 + 26 个侧车全部读进内存** | `BundledKnowledgePackResources.kt:82-98`（`resourceNames` 2 个，`:47-50`；侧车经索引文件 `:58-65`） |
| 写序（单次调和）：sources → nodes → node-source bindings → relations → **materials** → material bindings → retire → **每科 rebuildSubject** | `RoomKnowledgeContentReconciler.kt:53-104`（材料在 `:77-96`，索引在 `:102-104`） |
| 完成戳：**每包 reconcile 之后**才写 `content_install_state` | `BundledKnowledgeBaseInstaller.kt:146-154` |
| 就绪位：**全局** `Preparing → Ready`，`install()` 返回后才 `markReady()` | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:756-760`（重试路径同形 `:1024-1027`） |
| 复习页整门：`!knowledgeBaseAvailability.isReady` → 「知识点资料还在准备中」 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookRoot.kt:808` |
| 自动整理整门：未就绪抛 `KnowledgeBaseNotReadyException` | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt:177-180` |
| 索引分块：64 节点/块、锚点最后写 | `KnowledgeSearchIndexBuilder.kt`（`REBUILD_NODE_CHUNK_SIZE`；R4:250 同记） |
| 每科索引锚点 | `knowledge_search_index_state(subject, index_version)`（`entity/KnowledgeSearchIndexStateEntity.kt:25-31`） |
| 内容完成戳 | `content_install_state(pack_id, content_version, applied_at_epoch_millis, skipped_count, skipped_detail)`（`entity/ContentInstallStateEntity.kt:21-40`；DDL 见 KSP 生成的 `StudyDatabase_Impl.kt:577`） |
| 导入期 `synchronous = OFF` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt:52` |
| DB schema | **73**（`StudyDatabase.kt:142`） |
| 未就绪时的召回回退（目录序、上限 256） | `RoomKnowledgeBaseStore.kt:180-182`（`readSubjectKnowledgeNodes(subject, limit.coerceAtMost(256))`） |

**R4 的基线读数（本文件沿用，不重测）**：首启全量导入 **47,504 / 51,775 / 51,646 ms**（中位 51,646，最大 51,775）；四科索引 5,436–5,928 ms；JSON 解析 20.7–21.2 s；其余调和 20.8–25.2 s；冷启 `TotalTime` 2,970/2,829/3,014 ms（R4:4、R4:256-264；`docs/kb-acceptance-2026-10-17.md:824,829`）。

### 0.3 本会话实读 / 实算（非设备读数）

| 项 | 值 | 方式 |
|---|---:|---|
| 2025 侧车卷数 / 总大小 | **25 卷 / 114.31 MB**（min 0.29 / max 9.55 MB） | 读 `moe-2025-teaching-support-v2-index.json` + `os.path.getsize` |
| 2025 主包 | **4.33 MB** | 同上 |
| 2020 主包 / 侧车 | 11.5 KB / 1 卷 | `resourceNames` + `teachingSidecarsByPack`（`BundledKnowledgePackResources.kt:47-55`） |
| 预置库体积 | **407,326,720 B ≈ 407 MB**（R4:223 实测） | R4 引用；本会话未复核 |
| 材料 / 节点 | 65,021 / 3,280 | R4:4；本会话未重算 |

**未跑**：Gradle、设备冷装、导入压测、故障注入、判官。§3 的全部门是**判据定义**，不是通过声明。

### 0.4 与 R4 的关系（逐条）

| R4 的结论 | 出处 | 本文件落点 |
|---|---|---|
| 两阶段边界：Phase A 主包→节点/关系→按科 READY；Phase B 逐卷写材料→完成才写完成戳 | R4:19、R4:316-340 | §1 |
| 状态字段与「进度必须与数据同事务」 | R4:342-376 | §2.1 / §2.3 |
| 降级矩阵与「只有 FULL 的空结果才能说没有找到」 | R4:398-442 | §2.4 / §2.5 |
| 验收数字：首查 p95 ≤5 s（硬 10 s）、全量 p95 ≤45 s（硬 60 s）、导入期查询 p95 ≤250 ms、无静默空 | R4:20、R4:486-505 | §3 |
| `synchronous=OFF` 在 OS 崩溃/断电时可能损坏整库；WAL+NORMAL 不腐蚀但可能回滚最近提交 | R4:17、R4:138-158、R4:470-475 | §4 |
| 最小落地顺序：先修 `synchronous`，再分离主包/侧车，再状态机，再逐卷，再降级查询 | R4:517-523 | §1.6 / §4.5 / §5.4 |

**不继承**：R4 的 Phase C（材料 title/summary 特征）与 WorkManager 承载 Phase B 属于可选/后续项，本预注册**不做**（§1.5）。R4:482 的 expedited 配额仍是 `待验证`。

---

## 1. 两阶段导入（冻结）

### 1.1 Phase A：主包 → 节点/关系 → 按科 READY

**目标**：首查可用。只解析主包，不解析任何 teaching 侧车。

1. **解析**：`BundledKnowledgePackResources.loadBasePacks()` 只读 `knowledge/moe-2025-four-subjects-v1.json`（4.33 MB）与 `knowledge/moe-2020-foundation-v1.json`（11.5 KB）；**不**读 25 个 2025 侧车与 1 个 2020 侧车。
2. **写结构**（每包）：`sources` → `knowledge_node` → `node_source_binding` → `node_relation` → 节点退役。**不写材料、不写材料绑定。**
3. **分块**：结构写入按 **500–1,000 行/块**的短事务（节点总量 3,280，可一条事务，但为与材料统一形态仍分块）；块内顺序固定为「先节点/来源/绑定，后关系」，**不得**出现「绑定已提交、节点未提交」的窗口（R4:187-189）。
4. **按科建索引**：对 4 个包内科目逐个判断——**本科有节点且 `status != READY`** 时调用 `KnowledgeSearchIndexBuilder.rebuildSubject(subject)`（64 节点/块、锚点最后写，**不改**）；每科完成后**立刻**写 `knowledge_search_index_progress(subject, status=READY)`。**第一科 READY 时**把 `knowledge_install_progress.state=READY`、`first_subject_ready_at_ms` 落库，并回调编排侧 `markReady()`。
5. **不写完成戳**：Phase A **绝不**写 `content_install_state`。它的语义是「整包完成」；提前写会让下次启动命中快路径、跳过 Phase B（R4:339 同款）。

### 1.2 Phase B：逐卷写侧车材料 → 完成才写完成戳

1. **逐卷解析**：包顺序写死为 **先 `moe-2025-four-subjects-v1`（25 卷）→ 后 `moe-2020-foundation-v1`（1 卷）**；包内按 `moe-2025-teaching-support-v2-index.json` 的 `sidecars` 顺序，**一次只 `loadSidecar(i)` 一卷**（0.29–9.55 MB），解析完立即释放该卷 JSON 对象（不得保留对象图）。
2. **逐卷写材料**：该卷的 `materials` + `materialBindings` 按 **500–1,000 行/块**（或 50–150 ms/块）的短事务写入；**同一事务内**把 `materials_written` 增加本块行数。
3. **卷边界推进**：该卷**最后一块**提交时，**同一事务**把 `next_sidecar_index` 推进到 `i+1`。**不得**在第 i 卷中途推进——否则崩溃后会跳过本卷未写完的部分（R4:328 同款）。
4. **包完成**：2025 包的 25 卷全部写完 → 写 `knowledge_install_progress(state=COMPLETE, materials_ready_at_ms)`；2020 包的 1 卷写完 → 同理。**只有在该包全部材料与绑定写完后**才写该包的 `content_install_state`（沿用现有 `recordContentInstallState`），顺序固定为「数据 → 进度 COMPLETE → 完成戳」。
5. **首查不被 Phase B 阻塞**：Phase B 在 Phase A 的回调之后继续跑；首查在 `state=READY` 时已可用（§3.1 的「首查可用时刻」）。

### 1.3 快路径与续跑（冻结）

- **快路径**（不变）：`isInstallUpToDate(manifest, recorded)` 为真 → 直接返回，不解析（`BundledKnowledgeBaseInstaller.kt:44-55`）。
- **续跑**：快路径不成立时，先读 `knowledge_install_progress`：
  - 无行 / `content_version` ≠ manifest → 从 Phase A 开始，**覆盖**旧进度行（新内容）；
  - `state=READY` 且 `content_version` 相同 → **跳过 Phase A**，从 `next_sidecar_index` 继续 Phase B；
  - `state=COMPLETE` 但完成戳缺失（崩溃在两步之间）→ 只补写完成戳，不重跑解析。
- **包级完成戳各自独立**：2025 与 2020 各写自己的 `content_install_state`；一个包的 Phase B 失败不影响另一个包已写的戳。

### 1.4 编排形状（冻结）

`install()` 改成「A → 回调 → B」的同一条协程、同一把 `installMutex`（不引入 WorkManager）：

```kotlin
suspend fun install(database: StudyDatabasePort, onStructureReady: suspend () -> Unit = {}) {
    // 快路径 → 续跑判定 → Phase A → onStructureReady() → Phase B
}
```

- `onStructureReady` 只在 `state=READY` 落库之后调用；app 侧在回调里 `markReady()`（替代现在 `install()` 返回后的 `markReady`）。
- **失败语义**：Phase A 失败 → 不回调、`markUnavailable`（现状）；**Phase B 失败 → 保持全局 `Ready`**，只置 `last_error`/`attempt`，横幅提示「讲解材料未全部就绪」，**不得**把已可用的知识库打成 `Unavailable`。
- **对象图释放**：`releaseResidentPacks()` 只能在 **Phase B 结束（成功或失败）之后**调用；Phase A 返回时不得释放（否则 Phase B 无包可读）。现 `finally` 块（`SmartMistakeBookApplication.kt:784`、`:1040`）随之移到整条 A+B 的 `finally`。
- **重试**：重试走同一条 `install()`；已有 `state=READY` 时从 Phase B 续跑，不重解析主包。

### 1.5 明确不改（一条都不许动）

1. `KnowledgeSearchIndexBuilder`（64 节点/块、锚点最后写、`INDEX_VERSION=1`）——只由 installer 在每科完成时调用，不改内部。
2. 单条 `content_install_state` 的字段与快路径判据；`knowledge_search_index_state(subject, index_version)` 表原样保留（科级进度**另建表**，不改它）。
3. 材料模型、绑定、别名、内容包、金标、dense 资产。
4. 不引入 WorkManager / 预置库 / Phase C（材料 title+summary 特征）：R4 的 `expedited` 配额与 PAD 体积仍为 `待验证`，不进入本臂。
5. 不把 26 个侧车一次性 `load()` 进内存再写（现 `load()` 的形态在 Phase B 被逐卷替代；`load()` 只保留给测试/旧路径，生产安装不再调用）。

### 1.6 每一项新增消灭的具体失败（逐个指认）

| 新增 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| Phase A 只解析主包 | 解析 20.7–21.2 s 期间库里一行可查内容都没有（R4:18、R4:247） | R4:256-264 |
| Phase A 按科 READY + 回调 `markReady` | 第一科索引完成后仍要等其余三科 + 65k 材料才能查（R4:19） | R4:251（每科已有锚点） |
| Phase B 逐卷 `loadSidecar` | 26 卷一次性 load 的内存峰值与解析时间（R4:340） | `BundledKnowledgePackResources.kt:82-98` |
| 进度与数据同事务 | 「进度 100%、数据缺一块」的假完成（R4:361） | 现有完成戳「最后写」的同一失败形态 |
| `next_sidecar_index` 只在卷尾推进 | 崩溃后跳过未写完的卷 / 从头重跑 26 卷（R4:353） | R4:328 |
| 完成戳只在 Phase B 后写 | 提前写戳 → 下次启动命中快路径 → Phase B 永远不跑 | `BundledKnowledgeBaseInstaller.kt:44-55` |
| Phase B 失败不降级全局 Ready | 材料没写完把已可用的查询打成 `Unavailable` | §1.4 |
| 对象图释放移到 A+B 之后 | Phase A 返回即释放 → Phase B 读不到包 | `SmartMistakeBookApplication.kt:784` |

---

## 2. 状态机与降级查询（冻结）

### 2.1 状态机字段

**新增表 `knowledge_install_progress`（每包一行）**：

| 字段 | 类型 | 语义 | 消灭的失败 |
|---|---|---|---|
| `pack_id` | TEXT PK | 内容包 id | 多包进度互串 |
| `content_version` | TEXT | 与 manifest 对齐 | 旧版本进度被当新版本续跑 |
| `state` | TEXT | **`IMPORTING` / `READY` / `COMPLETE`**（见下） | 查询不知道「现在能查到什么程度」 |
| `phase` | TEXT | `A` / `B` / `DONE` | `READY` 时不知道 Phase B 是否在跑 |
| `structure_ready_at_ms` | INTEGER | Phase A 结构写完的时刻（T0 起） | 无法归因首查等待 |
| `first_subject_ready_at_ms` | INTEGER | **第一科索引 READY** 的时刻（T0 起） | 首查可用时刻不可测 |
| `materials_ready_at_ms` | INTEGER | 全量材料写完的时刻（T0 起） | 全量完成时刻不可测 |
| `materials_expected` / `materials_written` | INTEGER | 材料计数；`expected` 由清单/侧车元数据给出，缺失时填 0 并以 `next_sidecar_index / sidecars_total` 为进度 | 进度不可见 |
| `next_sidecar_index` | INTEGER | 下一卷序号；**仅整卷最后一块提交后推进** | 崩溃续跑跳过未写完的卷 |
| `sidecars_total` | INTEGER | 总卷数（2025=25，2020=1） | 进度分母 |
| `last_error` / `attempt` | TEXT / INTEGER | 最近失败与重试次数 | 只看到「准备中」，看不到原因 |
| `updated_at_ms` | INTEGER | 心跳 | 卡死不可诊断 |

**状态语义（写死，三态，不新增第四态）**：

| `state` | 判定 | 查询含义 |
|---|---|---|
| `IMPORTING` | Phase A 尚未写完结构 / 尚无任何一科 READY | 结构未就绪：查询返回 `PREPARING` + 进度，**不查特征表** |
| `READY` | 结构写完 **且** 至少一科索引 READY；Phase B 可能在跑或失败 | 首查可用；按科给 `FULL` / `PARTIAL_INDEX` / `PARTIAL_MATERIALS` |
| `COMPLETE` | 该包全部侧车写完 + 完成戳已写 | 全量可用；空结果才可报「没有找到」 |

- **失败不新增状态**：Phase A 失败时 `state` 保持 `IMPORTING`；Phase B 失败时 `state` 保持 `READY`；两种情况都写 `last_error` + `attempt++`。这样「三态」始终表示**已确认的可达程度**，不是「正在尝试什么」。
- **科级可用性（新增表 `knowledge_search_index_progress`）**：

| 字段 | 类型 | 语义 |
|---|---|---|
| `subject` | TEXT PK | 科目 |
| `status` | TEXT | `PENDING` / `BUILDING` / `READY` / `FAILED` |
| `nodes_expected` / `nodes_indexed` | INTEGER | 本科应索引/已索引节点数 |
| `updated_at_ms` | INTEGER | 最后推进时间 |

- **科级 `status` 只是 UI/调度提示，不是正确性判据**（R4:374）：查询正确性仍以「锚点当前 + 已索引 ≥ 已审校」为准（`KnowledgeSearchIndexCompleteness` 的口径）；`status` 写丢最多让 UI 少一次提前提示，**不得**让未完成索引被当完整。

### 2.2 状态转移（冻结）

```text
（无行）
  → IMPORTING（phase=A）        开始解析主包
  → READY（phase=B）            第一科索引 READY；回调 markReady()
  → COMPLETE（phase=DONE）      全部侧车写完 → 写 COMPLETE → 写完成戳（两者都落库才算 settled）
任何失败：state 停在已达状态，last_error/attempt 更新；重试从该状态续跑
```

- `READY → COMPLETE` 是**每包各自**的转移：2025 包在其 25 卷全部写完时写自己的 `state=COMPLETE`；2020 包在其 1 卷写完时写自己的。G2 的「全量完成时刻」读 **2025 包**的 `materialsCompleteMs`；「两包都 COMPLETE」只用于状态一致性核对，不是 G2 的读数。
- `COMPLETE` 之后若 `content_version` 变化 → 新行覆盖为 `IMPORTING` 并重跑。

### 2.3 写入纪律（写死）

- 进度行与数据块**同一事务**提交；任何「先写进度、后写数据」的实现都算违反（R4:361）。
- 卷边界推进（`next_sidecar_index`）与**该卷最后一块**的数据写入同一事务。
- 完成戳（`content_install_state`）在 `state=COMPLETE` 落库**之后**、单独的短事务写。
- 导入期间不持有跨阶段长读事务（R4:459）；查询用短读事务，读完即放。
- 写连接仍只有一个写者；不把 26 卷材料放进一条事务（R4:455-460）。

### 2.4 降级查询语义（冻结：索引不全时不返回静默空）

**返回类型**（领域层；名字可调整，字段冻结）：

```kotlin
enum class RecallCompleteness {
    PREPARING,          // 结构未 READY：不查特征表
    STRUCTURE_ONLY,     // 结构 READY，本科无任何特征行
    PARTIAL_INDEX,      // 结构 READY，本科索引未 READY（结果来自已提交块 + 目录兜底）
    PARTIAL_MATERIALS,  // 召回 FULL，但该包材料未全
    STALE,              // contentVersion 不匹配 / 上次安装失败
    FULL                // 结构 READY + 本科索引 READY + 材料不要求
}

data class KnowledgeRecallResult(
    val nodes: List<KnowledgeNodeSeedRecord>,
    val completeness: RecallCompleteness,
    val indexedSubjects: Set<String>,
    val pendingSubjects: Set<String>,
)
```

**降级矩阵（写死）**：

| 状态 | 查询行为 | 标注 |
|---|---|---|
| `state=IMPORTING`（结构未 READY） | **不查** `knowledge_search_feature`；返回 `PREPARING` + 进度 | `PREPARING` |
| 结构 READY，本科 `status != READY` | 先查**已提交**特征行（允许为空）；再 `readSubjectKnowledgeNodes(subject, 256)` 目录兜底；按 id 去重 | `PARTIAL_INDEX` |
| 结构 READY，本科无任何特征行 | 只走目录兜底 | `STRUCTURE_ONLY` |
| 本科索引 READY，包材料未 COMPLETE | 正常召回；材料引用为空时不编造 | `PARTIAL_MATERIALS` |
| `contentVersion` 不匹配 / 安装失败 | 用库里最后一份完整数据 | `STALE` |
| 全 READY 且无命中 | 返回空 | `FULL` |

**铁律（可断言）**：

1. **只有 `completeness == FULL` 的空结果才能说「没有找到」**；其余空结果必须带 `PREPARING`/`PARTIAL_*`/`STALE` 之一（R4:436）。
2. **导入期间查询不得触发整科重建**：`ensureKnowledgeSearchIndex` 在 `state=IMPORTING` 时**不得**被调用（否则查询会内联跑一次整科 rebuild，墙钟直接爆掉）。本科 `status=BUILDING` 时按 `PARTIAL_INDEX` 返回；重建只由 installer 驱动。
3. **不静默空**：用户可见的召回路径必须走 `KnowledgeRecallResult`——本臂迁移面**固定为三处生产调用点**：`RoomTutorToolRunner.kt:431`（`KNOWLEDGE_READ`）、`RoomTutorKnowledgeContextLoader.kt:67`（知识上下文）、`RoomMistakeOrganizationRepository.kt:227`（自动整理）。**旧方法 `readSubjectKnowledgeRecallCandidates(...): List<…>` 保留原签名**（判官 `GoldenRetrievalInstrumentedTest` 与测试继续用它），但在 `src/main` 内除其自身声明外**不得再有调用**（源码守卫测试断言）。任何仍把空当「没有」的调用点都算未迁移。
4. **目录兜底不是相关性排序**：兜底结果必须带 `partial=true`（由 `completeness != FULL` 表达），UI 不得把它与正常结果混在同一个「最相关」列表里而不标注（R4:440-442）。

### 2.5 就绪位与门（冻结）

- **全局 `KnowledgeBaseAvailability.Ready` 的触发点**改为「Phase A 第一科 READY」，不再是 `install()` 返回后（现状 `SmartMistakeBookApplication.kt:756-760`）。`Preparing`/`Unavailable` 的语义不变。
- **复习页整门不变**（`SmartMistakeBookRoot.kt:808`）：它在 Phase A 后放行；未就绪的科由查询结果的 `completeness` 标注，不再整页挡。
- **自动整理门收窄到科目**：`RoomMistakeOrganizationRepository.prepare()` 在全局 Ready 之后，**再查目标科目的 `status`**；非 READY 抛 `KnowledgeBaseNotReadyException(Preparing)`（写路径需要完整候选菜单，不能用部分索引猜分类）。这是对 `RoomMistakeOrganizationRepository.kt:177-180` 的最小扩展。
- **`KnowledgeBaseNotReadyException` 保留**：只用于「必须完整知识库才能做」的写操作；普通查询不再用它表达「部分可用」。

### 2.6 每一项新增消灭的具体失败（逐个指认）

| 新增 | 消灭的**具体**失败 | 证据/先例 |
|---|---|---|
| 三态 `state` + 科级 `status` | 查询只知道「全局 Ready/未 Ready」，不知道「本科索引是否完整」 | R4:344-376 |
| `completeness` 返回类型 | 索引未完成时的空结果被当成「没有这个知识点」——R4:189 点名的假答案 | R4:398-436；`KnowledgeBaseAvailability` KDoc 的同一原则 |
| 目录兜底（`readSubjectKnowledgeNodes`，上限 256） | 结构已写但索引未建时，用户看到空列表 | `RoomKnowledgeBaseStore.kt:180-182` 已有回退先例 |
| 导入期禁止 inline rebuild | 查询触发 `ensureKnowledgeSearchIndex` 整科重建，导入期 p95 爆表 | §2.4 铁律 2 |
| 科目级 `prepare()` 门 | 用部分索引给错题做自动分类，写出错误绑定 | `RoomMistakeOrganizationRepository.kt:175-180` |
| 进度与数据同事务 | 假完成（进度 100%、数据缺块） | R4:361 |

---

## 3. 验收（写死）

### 3.1 两个读数（同一 T0）

**T0** = `Application.onCreate` 第一行取的 `SystemClock.elapsedRealtime()`（进程起点）。两条读数都以 T0 为零点：

| 读数 | 定义 | 日志字段（冻结） |
|---|---|---|
| **首查可用时刻** | `knowledge_install_progress.state=READY` 落库、且第一科 `status=READY` 的那一瞬（= 回调 `markReady()` 之前） | `install.firstSubjectReadyMs` |
| **全量完成时刻** | **2025 包**最后一卷侧车写完、`state=COMPLETE` 落库、`content_install_state` 写完的那一瞬（2020 包单独记，不参与 G2 读数） | `install.materialsCompleteMs` |

- 两个读数必须由**同一次冷装**产生；不得用「解析耗时 + 索引耗时」相加推算。
- 日志 append 一次、不可覆盖；同一次安装只允许一行 `install.firstSubjectReadyMs` 与一行 `install.materialsCompleteMs`。

### 3.2 三条门（全部按设备真 SQL/真 Room）

| # | 门 | 判据 | 硬上限 |
|---:|---|---:|---:|
| G1 | **首查可用** | `install.firstSubjectReadyMs` **p95 ≤ 5,000 ms** | **max ≤ 10,000 ms** |
| G2 | **全量材料完成** | `install.materialsCompleteMs` **p95 ≤ 45,000 ms** | **max ≤ 60,000 ms** |
| G3 | **导入期查询** | 固定探针查询 **p95 ≤ 250 ms**、`overBudget = 0` | **max ≤ 1,000 ms**；**静默空 = 0** |

- **G2 的硬上限 60 s 沿用现有 F6 门**（R4:494）；本臂不承诺 30 s，也不放宽到 60 s 以上。
- **G3 的探针**：每个科目取金标文件里**该科第一条**查询（MATH/PHYSICS/CHEMISTRY/BIOLOGY 各一条，共 4 条，按文件顺序冻结），从 `T0+500 ms` 起每 250 ms 轮询一次，直到 `state=COMPLETE`；每次记录墙钟、`nodes.size`、`completeness`。样本 = 全部轮询点，不是 4 条查询各一次。
- **静默空 = 0**：在 `state != COMPLETE` 期间，任何一次探针返回 `nodes.isEmpty() && completeness == FULL` 都记为一次静默空；**必须为 0**。同时，`PREPARING`/`PARTIAL_*` 的空结果**不**计入静默空（它们有标注）。
- **G3 的 p95 不含 Phase B 的完成时刻**：只统计 `state=IMPORTING/READY` 期间的查询。

### 3.3 支持门（崩溃收敛，登记但不替代 G1–G3）

- 按**固定顺序取 20 个边界**：Phase A 的前 4 个结构写块边界 → 4 科索引完成之后 → Phase B 的前 12 卷边界（4+4+12=20）；每次 kill 后重启，必须：`state` 从已达状态续跑、不重复写已完成卷、最终 `COMPLETE`；`PRAGMA integrity_check` = `ok`；`content_install_state` 与 `knowledge_install_progress` 一致（R4:497 G8）。
- 20 次里**任何一次**出现「完成戳已写但材料缺失」或「`next_sidecar_index` 跳过未写完的卷」⇒ 本臂不采纳。

### 3.4 测量协议（写死，否则数字不可比）

1. **样本**：≥ **20 次真冷装**（`pm uninstall` → `adb install` → `logcat -c` → `am start -W` → 轮询到 `state=COMPLETE`）。现有 3 次**不能**报 p95（R4:21、R4:510）；只报 median/max 不算过门。
2. **装置**：至少 1 台 API 34 emulator（现有 `kb_test_device`）+ 1 台真机；记录 AVD/CPU/内存/API；emulator 与真机**分别报**，不混算。
3. **固定**：同 APK sha、同内容包 sha、同 `contentVersion`、同语言/屏幕/电量档；不跨轮次拼样本。
4. **读法**：p50 / p95 / max + 样本数 + 装置；p95 是最近秩（nearest-rank），不得插值美化。
5. **T0 一致性**：`firstSubjectReadyMs` 与 `materialsCompleteMs` 必须来自同一次安装的同一 T0；跨次拼两个读数算无效。

### 3.5 报告义务（§7 至少包含）

- 20 次冷装的逐次 `firstSubjectReadyMs` / `materialsCompleteMs`、p50/p95/max、装置清单。
- G3 的逐轮询点 p50/p95/max/overBudget、静默空计数（必须 0）、每次返回的 completeness 分布。
- 崩溃注入 20 次的逐次结果（续跑位置、integrity_check、计数一致性）。
- 状态机：每次安装的 `state/phase/next_sidecar_index` 轨迹；Phase A/B 的 `install finished` 日志（分段耗时）。
- 两阶段**不劣化**证据：全量材料完成 p95 ≤45 s 与现有 47.5–51.8 s 的关系；若 NORMAL 让导入变慢，单列 §4.5。
- 双门 exit code；§5 逐文件 post-sha；判官三个 sha（设备/JVM/金标）。

### 3.6 装置门（双门 = 0）

| # | 命令 | 通过条件 |
|---:|---|---|
| 1 | `./gradlew :core:database:compileDebugUnitTestKotlin :core:database:assembleDebugAndroidTest --console=plain` | **exit 0** |
| 2 | `./gradlew :core:data:compileDebugUnitTestKotlin :core:data:assembleDebugAndroidTest --console=plain` | **exit 0** |

- 门 1：schema 74、迁移、实体/DAO、降级查询的 DB 面。门 2：installer 分阶段、逐卷解析、completeness 迁移、状态机 JVM 测试。
- 任一门红 ⇒ 不跑设备、不判读；不得先跑设备再补门。日志落 `build/agent-batch33/b33-progressive/gates/`（建议）。

---

## 4. 安全修复：`synchronous = OFF → NORMAL`（冻结）

### 4.1 现状与风险

`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt:52`：

```kotlin
connection.executeDdl("PRAGMA synchronous = OFF")
```

- 作用域：**连接级**；在 `withImportTuning` 期间覆盖写连接，`finally` 恢复旧值（`RoomKnowledgeImportMaintenance.kt:48-75`）。
- 风险：SQLite 文档原文——OFF 下「the database might become corrupted if the operating system crashes or the computer loses power before that data has been written to non-volatile storage」（R4:142）。`StudyDatabase` 同时存学生错题、复习队列、掌握度等**用户数据**，不是「可从资产重建的临时库」；把它当可重建库用 OFF 的适用前提不成立（R4:158）。

### 4.2 改动（写死）

```kotlin
connection.executeDdl("PRAGMA synchronous = NORMAL")
```

- 只改这一行；`previousSynchronous` 的读取与 `finally` 恢复逻辑**一字不动**（成功/异常/取消三条路径都恢复）。
- 不改 `cache_size = -32768`、不改 `journal_mode`、不改 `withDeferredFeatureIndexes`。
- `NORMAL` 在 WAL 下的官方语义：**不腐蚀**，但「might roll back following a power loss or system crash」（R4:140）——对「可从资产重导的知识内容」可接受，对用户数据的安全面远好于 OFF。

### 4.3 为什么必须做（消灭的具体失败）

**OS 崩溃/断电时用户错题库与复习状态损坏**。这不是「更保守所以更好」：OFF 的收益只是少 fsync；代价是整个库文件的损坏面，且**连接级设置**意味着即使只写索引表，崩溃也可能损坏包含用户数据的同一文件（R4:474）。可指认的失败是「断电后 `study.db` 打不开 / 错题丢失」，不是「导入慢一点」。

### 4.4 回归测试（冻结）

1. **`ImportSynchronousPragmaInstrumentedTest`（设备，真 Room）**
   - 在 `withImportTuning { }` 内用**写连接**读 `PRAGMA synchronous`，断言 = **1（NORMAL）**（SQLite 取值：0=OFF、1=NORMAL、2=FULL、3=EXTRA）；
   - block 正常返回后，断言恢复为进入前的值；
   - block 抛异常后，断言仍恢复；block 被取消后，断言仍恢复且 `CancellationException` 原样抛出。
2. **源码守卫（JVM）**：读 `core/database/src/main/.../RoomKnowledgeImportMaintenance.kt`，断言不包含 `PRAGMA synchronous = OFF`（防回退；失败信息直接指向本预注册 §4）。
3. **性能回归**：§3 的 G2（p95 ≤45 s、max ≤60 s）就是 NORMAL 的性能门。若 NORMAL 使导入超出硬上限，**不得**回退 OFF；按 R4:474 的备选（独立 staging 库，OFF 只作用于可重建库）另立预注册。

### 4.5 与两阶段的顺序（冻结）

- **先落 §4，再落 §1–§2**。§4 是**独立可采纳**的一行修复：不依赖 schema 74、不依赖状态机、不改查询；它自己的证据是 §4.4 的三条测试 + 现有 F6 导入 ≤60 s。
- 若两阶段臂回滚，§4 **保留**（它不属两阶段写面；§5.4 的回滚步骤明确不恢复这一行）。
- 若两阶段臂采纳，§4 已是前置提交；§7 分别记录两次提交的证据。

---

## 5. 回滚（触碰清单 + pre/post sha）

### 5.1 A. 生产源码（pre-sha 为本轮 04:04:46 工作树实算）

| # | 路径 | 预期动作 | pre-sha256（写前） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt` | **§4**：`OFF → NORMAL`（一行） | `6059173aa6fb4fbad89e4a7649f265d89eedf1026882cbb0d07ec9b877d46fe1` | §7 |
| 2 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgePackResources.kt` | `loadBasePacks()` / `loadSidecar(i)`；`load()` 仅留测试/旧路径 | `05e1e79d339ecb44e3bdd59c8047bff5af3afff89c29b3bd804a23c56989c4a9` | §7 |
| 3 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstaller.kt` | A→回调→B；续跑；完成戳只在 B 后 | `989549a0989d76bc1bd0f7c9b08d31a2ea55feef2c9eb2b1d5180fc5ddd54f1e` | §7 |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeContentReconciler.kt` | 拆 `applyStructureUpdate` / `applyMaterialsUpdate`；旧入口委托两者 | `e6ef7cc01342ca2cd3035b08d395d4ad1e8d61b5d7e72334f6c255bdd64e3142` | §7 |
| 5 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt` | version 73→74；两实体入列；迁移挂载；DAO 暴露 | `b82369a17641c1eb71a005bea51f12f01b6f66bcf6c1cac241ac0b54a4bec308` | §7 |
| 6 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt` | 新增 `recallSubjectKnowledge` 返回 `KnowledgeRecallResult`；降级矩阵；`state=IMPORTING` 时不调 ensure；**旧方法原签名保留** | `8cdbda580ef8b81952cf6cbb400abe7e20740a680a5edca97bcb9a386adc31de` | §7 |
| 7 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 新召回口 + 科级可用性读取 | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | §7 |
| 8 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 委托新 DAO/新召回口 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | §7 |
| 9 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt` | 两处 install 块改为 `install(database) { markReady() }`；释放移到 A+B 后；Phase B 失败不降级 | `20bc8de8ce6e7f928c0ad9bc52efbb71f054822524f92198208aba5e67efc219` | §7 |
| 10 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/mistake/RoomMistakeOrganizationRepository.kt` | `prepare()` 增科目级 `status=READY` 检查；召回改用 `recallSubjectKnowledge` | `97137a54c726a376a9fae3e940cc58f276b58f0aea654940965ddde6d4d5b7db` | §7 |
| 11 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt` | `KNOWLEDGE_READ`（`:431`）改用 `recallSubjectKnowledge`；`PREPARING/PARTIAL` 文案 | `2de1e68f71bc0615ce073fa797086dc63178744cd9e7e153c275e26400b37fd4` | §7 |
| 12 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/RoomTutorKnowledgeContextLoader.kt` | 知识上下文召回改用 `recallSubjectKnowledge` | `7fc8cb143ac2aa81b8707a5a597852f88bef5d292a3fe6b2ff3bbceb64b12f62` | §7 |

### 5.2 B. 新增文件（写前均不存在）

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/database/src/main/kotlin/.../entity/KnowledgeInstallProgressEntity.kt` | `knowledge_install_progress` 实体 |
| 2 | `core/database/src/main/kotlin/.../entity/KnowledgeSearchIndexProgressEntity.kt` | `knowledge_search_index_progress` 实体 |
| 3 | `core/database/src/main/kotlin/.../dao/KnowledgeInstallProgressDao.kt` | 读写/推进/重置 |
| 4 | `core/database/src/main/kotlin/.../KnowledgeInstallProgressMigration_73_74.kt` | 建两张空表 |
| 5 | `core/database/schemas/.../74.json` | Room 导出（生成） |
| 6 | `core/domain/src/main/kotlin/.../KnowledgeRecallResult.kt`（或 `core/data`） | `RecallCompleteness` + `KnowledgeRecallResult` |
| 7 | `core/database/src/androidTest/.../ProgressiveImportPhaseInstrumentedTest.kt` | Phase A/B、续跑、完成戳只在 COMPLETE 后 |
| 8 | `core/database/src/androidTest/.../DegradedRecallInstrumentedTest.kt` | 降级矩阵 + 静默空 = 0 |
| 9 | `core/database/src/androidTest/.../ImportSynchronousPragmaInstrumentedTest.kt` | §4.4 的 1 |
| 10 | `core/database/src/test/kotlin/.../ImportPragmaSourceGuardTest.kt` | §4.4 的 2 |
| 11 | `core/data/src/test/kotlin/.../InstallPhaseStateMachineTest.kt` | 状态转移/续跑/失败不降级（JVM 夹具） |

### 5.3 C. 零触碰（登记供核验，不得改）

| # | 路径 | 期望 | pre-sha256 |
|---:|---|---|---|
| 1 | `core/database/.../KnowledgeSearchIndexBuilder.kt` | 零改（64 节点/块、锚点最后写） | `db734746f0d3c255af63647febac1ad85867e7f160eee8edebf74b58c45fc416` |
| 2 | `core/database/.../entity/ContentInstallStateEntity.kt` | 零改（完成戳字段不变） | `ca784bd31e9f450575b7b7d6d3446083dea999454de756f8f92a5cf5bbc71213` |
| 3 | `core/database/.../entity/KnowledgeSearchIndexStateEntity.kt` | 零改（科级进度另建表） | `7fbde61caad99060107d2a71443bc441b31da226a64cfe83b8fdf7ec6a193108` |
| 4 | `core/data/.../knowledge/dense/DenseFusion.kt` | 零改 | `103cce57c09c2e0f5152f8d085c446b4b37f6c134af26e7c1896f3f722d8ed71` |
| 5 | `core/database/src/main/.../DenseRecallReranker.kt` | 零改 | `9fcfb67f23a3bd9f525250a5044a2c66dca706e923f82df656bd39b6c90bc41d` |
| 6 | `core/database/.../KnowledgeSearchFeatureExtractor.kt` | 零改（`INDEX_VERSION=1`） | `b65a6e9988a59e7b5c7b12822b9f66c42965909f136c2460e393595e8bac699c` |
| 7 | `app/src/main/kotlin/.../SmartMistakeBookRoot.kt` | 零改（全局门在 Phase A 后放行，文案不变） | `f3eb0036dfababc44a30bf2c1a03cebb20fee43dea3e20b366798b6f07982d3d` |
| 8 | `core/domain/.../KnowledgeBaseAvailability.kt` | 零改（三态语义不变，只是 `Ready` 的触发点提前） | `43c4a1283461d45a4f33f7265866fcc4930bb419554965fb8a8b6147ca1b56c6` |
| 9 | `core/database/schemas/.../73.json` | 零改（历史 schema 对照） | `c7e7586a4eaab6d7309e91f2b715a74229369d95bc11966cfe96a94755f39f1e` |
| 10 | `tools/kb_coverage/tables/golden_queries_v2.json` | 零改 | `eabef10228916d46a18aaaa4e3084f5bacdf6e07ce3a3f8384d31ff71c6f2102` |
| 11 | `core/data/src/androidTest/.../GoldenRetrievalInstrumentedTest.kt` | 零改（设备判官；旧召回方法签名保留，故判官无需改） | `9fae203f059645af6290549cd996263474cd047fae771c22f9f6695a2431e13e` |

### 5.4 写前备份 / 回滚 / 采纳

**写前备份（执行轮第 1 步之前）**

1. 把 §5.1 的 12 件复制到 `build/agent-batch33/b33-progressive/backup/`，落 `sha256sum` 全表。
2. `git status --porcelain -- <§5.1 路径>` 必须为空；若非空先归因他线并停止。**另**：本文件写作后 HEAD 已被他线推进，执行轮必须重跑 §0.1 守卫并记录新 HEAD。
3. 复核身份：manifest `version=26` / `contentVersion=aea886e42b36dcb2`；主包 `96246c78…`；dense `70329115…`；金标 `eabef102…`；DB 73；`INDEX_VERSION=1`。

**回滚（G1–G3 任一失败 / 双门红 / 静默空非 0）**

1. **不得**整树 `git checkout`；按 §5.1/§5.2 逐文件恢复/删除。
2. 用 `backup/` 覆盖 §5.1 的 12 件（**§5.1 #1 除外**——`synchronous=NORMAL` 是独立安全修复，回滚两阶段时**保留**它；若要回滚 §4，必须单独说明理由并重跑 §4.4）；删除 §5.2 的 11 件（含生成的 `74.json`）。
3. **schema 74 → 73 不可降级**：必须 `adb shell pm clear <appId>`（或卸载重装）后装回旧 APK；学生数据会丢，这是本臂回滚的真实代价，必须写进 §7。**不得**用「覆盖安装」假装回滚。
4. 核验：`knowledge_install_progress` / `knowledge_search_index_progress` 不存在；`content_install_state` 回到 v26 的戳；DB 73；主包/dense/金标 sha 与 §0 一致。
5. 复跑 §3.6 双门；回滚后仍红则登记为他线/基线问题。

**采纳（分两步）**

1. **第一步只采纳 §4**：`synchronous=NORMAL` + 三条回归测试 + F6 导入 ≤60 s；不需要 schema 74，变更面 1 个文件。
2. **第二步采纳两阶段**：G1–G3 全过 + 崩溃收敛 20/20 + 双门 0；保留 schema 74；§7 分别记录两步的读数与 sha。
3. §7 登记 stateAfter：manifest/contentVersion（预期不变）、DB 74、两表行数、`firstSubjectReadyMs`/`materialsCompleteMs` 分布、G3 轮询读数、崩溃注入结果、双门 exit code。

---

## 6. 未做 / 未验证（写作时点）

- **未写任何代码/测试/迁移**：§1–§4 的边界、字段、状态、降级矩阵、`NORMAL` 改动均为冻结定义。
- **未跑 Gradle/设备/冷装/压测/故障注入**：§3 是判据与协议，不是通过声明。
- **R4 的 47.5–51.8 s、5.4–5.9 s、20.7–21.2 s 未重测**：沿用 R4/AC 的实测记录；本臂的 G1–G3 仍为 `NOT_RUN`。
- **Phase A 的时间推断未实测**：R4:536 的 2–4 s / 3–5 s 是线性外推；本文件不把它当预期读数。
- **`synchronous=NORMAL` 的实际导入代价未测**：可能比 OFF 慢；G2 硬上限 60 s 是唯一裁决者（§4.4-3）。
- **WorkManager/expedited 未做**：Phase B 仍在 `applicationScope`；进程被杀会丢当前卷之后的进度（可从 `next_sidecar_index` 续跑，但需要进程重启后再次 install）。
- **“材料是否参与首查”未由产品确认**（R4:539）：本文件按「节点+索引即可查、材料后补」冻结；若产品要求首查带教材引用，Phase A 的可用性定义要改。
- **崩溃注入未做**：§3.3 的 20 次是判据，不是已完成。
- **p95 样本**：本文件冻结 ≥20 次冷装；现有 3 次不足以估 p95（R4:21、R4:510）。
- **工作树在会话内被他线推进**（HEAD `62522930` → `364ee025`）：pre-sha 是 04:04:46 时点值，执行轮须重新实算。

---

## 7. 执行轮追加位（只追加，不改 §1–§5）

> 执行轮在此追加：写前/写后 porcelain 与 HEAD（须为新 HEAD）；备份与回滚核验；§5 逐文件 post-sha；双门 exit code；20 次冷装的 `install.firstSubjectReadyMs` / `install.materialsCompleteMs` 逐次值与 p50/p95/max；G3 轮询点 p50/p95/max/overBudget 与静默空计数（须 0）；completeness 分布；崩溃注入 20 次结果（续跑位置/integrity_check/计数一致性）；schema 74 与两表行数；§4 的三条回归测试输出；回滚时的 `pm clear` 与数据丢失说明。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
