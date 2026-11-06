# KB B34 首启导入性能（SQLite 批量 / 材料索引 / 流式 JSON）：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-04T01:45:56+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 实跑）。
> **上游**：`docs/research/2026-11-03-kb-r7-import-performance.md`（下称 R7）的事实与建议；R7 的取舍与外部来源不在此复述，本文只把 B34 的落地项写死。
> **本步唯一新增写盘**：本文件。未改代码/内容包、未跑 Gradle/设备/基准；§0 的读数是本会话用 `python -I` / `sha256sum` 实读实算。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T01:45:56+0800
git rev-parse HEAD                             # 0fef943641f53560b92ce2afec4da22bf52f5e86
git status --porcelain | wc -l                 # 555
ls docs/research/2026-11-03-kb-b34-import-perf-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 写前工作树**不干净**，且本臂触碰面里已有他线改动，必须逐条记住（不得回滚、不得裹挟）：
  - `M core/database/.../RoomKnowledgeImportMaintenance.kt`：他线把 `PRAGMA synchronous = OFF` 改成了 `NORMAL`（`git diff` 实读，仅 3 行注释/DDL 变化）。本臂在**该工作树值**之上继续编辑；§4 的 pre-sha 是工作树值，不是 HEAD 值。
  - `M core/data/build.gradle.kts`：他线 KD-83 / WS5 的 `syncV4PackFixtures` 任务。本臂只在其后追加一条依赖，不回滚。
  - `?? core/data/gradle.lockfile`：未跟踪的生成文件（`core/data/gradle.lockfile`，126 行，头两行声明由 Gradle 生成）。本臂新增依赖后由 Gradle 重生成；它不在 git 跟踪面内，§4 登记工作树 pre-sha。
- 555 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。

### 0.2 现状（实读 / 实算）

| 事实 | 值 | 来源 |
|---|---:|---|
| 主包 | `knowledge/moe-2025-four-subjects-v1.json`（4,538,305 B）+ `moe-2020-foundation-v1.json`（11,751 B） | 实读 `stat` |
| update manifest | `version=28` / `contentVersion=ce8edb8f89d91fe7` / retired 1,222 | 实读 manifest；sha `d69ebb8b…` |
| teaching 侧车 | **26 个**（2025 的 25 卷 + 2020 的 1 卷），合计 **119,573,849 B** | `glob` + `stat` 实算 |
| 材料 / 绑定 | **64,855 / 64,857**（2025：64,853 / 64,853；2020：2 / 4） | 逐侧车 `json.load` 实算 |
| 节点 | **3,733**（2025：425 topic + 3,280 atomic；2020：9 topic + 19 atomic） | 实算 |
| 唯一 source | **62**（侧车 49 + 合成源 13：2025 四科 + 2020 九科） | 实算；侧车 206 行 |
| node_source_binding / relation | **3,299 / 350** | 实算 |
| 当前 `synchronous`（写连接） | **NORMAL**（工作树 `RoomKnowledgeImportMaintenance.kt:52`） | 实读；他线已从 OFF 改为 NORMAL |
| 当前 `cache_size`（写连接） | **-32768** = **32 MiB**（`RoomKnowledgeImportMaintenance.kt:53`） | 实读；负值单位 KiB |
| 当前材料写入 | **一次 `@Upsert(List)`**，**64,855 行**级一条 IMMEDIATE 事务（`RoomKnowledgeContentReconciler.kt:83-86`） | 实读；R7 §1.4 |
| 当前材料索引 | 4 条：2 UNIQUE（`stable_code`、`content_fingerprint`）+ 2 普通（`source_id`、`subject,material_type`） | schema `73.json:1674-1708` 实读 |
| 当前特征索引窗口 | 导入期 DROP 2 条，`ensureFeatureIndexes()` 起点自愈（`RoomKnowledgeImportMaintenance.kt:34-39,81-105`） | 实读 |
| 当前 JSON 解析 | `readClasspathResource()` → `readText()`；`parseToJsonElement()` 建整棵树；再手工走树 | `BundledKnowledgePackResources.kt:157-162,174-175`；`ReviewedTeachingMaterialSidecarJsonCodec.kt:102-103` |
| 当前 `PRAGMA optimize` | **未执行** | `rg` 实查，0 命中 |
| 基线读数（R7 引用，本文未重测） | 首启全量 **47,504 / 51,775 / 51,646 ms**；两阶段后 G2 **68,408 ms（N=1）**；分段 JSON 20.7–21.2 s、材料/绑定 20.8–25.2 s、索引 5.4–5.9 s | R7 §0/§1.6；原始 `docs/research/2026-11-02-kb-r4-progressive-import.md:4`、`docs/kb-batch29-2026-10-31.md:46-64` |
| 当前材料分块数（按 1,500 行/事务） | 2025 **44** 块（64,853 = 43×1,500 + 353）、2020 **1** 块；合计 **45** 个事务 | 实算 |

### 0.3 本臂的边界声明（先说清楚，避免把门当成它没测的东西）

1. **这是一次单遍导入的性能改造，不是分阶段可用改造**。本臂不新增"第一科 READY"状态、不把材料推到后台、不改变 `BundledKnowledgeBaseInstaller.install()` 的调用时序（`SmartMistakeBookApplication.kt:756-784` 仍在后台协程里一次 `install()`，全部完成后才 `markReady()`）。
2. 因此 §2 的 **G1（首查可用）** 与 **G3（导入期查询无静默空）** 在本臂冻结的改动下**没有机制保证能达标**：G1 仍要等内容写完，G3 在特征索引建好前仍可能返回空。§3 的采纳条件（按 ask 原文）只含 **G2 + 崩溃注入 20/20**；G1/G3 必须如实报告，**不得**因为"没进采纳条件"就写成绿。
3. 若 owner 要求 G1/G3 也成为采纳条件，则 §1 必须增加"分阶段可用/查询降级"改造；那超出本预注册的冻结清单，应另立预注册，不在本臂偷偷加。

---

## 1. 改动清单（写死）

### 1.1 总表（只允许这 6 项；每项都要能指名它消灭的失败）

| # | 改动 | 冻结值 / 形态 | 消灭的**具体**失败 |
|---:|---|---|---|
| 1 | **`cache_size=32 MiB`** | `PRAGMA cache_size = -32768`（负值 KiB；= 32 MiB）。**保持**，不加/不减。 | 默认约 2 MB 页缓存下，64,855 材料 + 547k 特征的随机页命中率低；调大减少导入期读页。R7 §1.1 |
| 2 | **材料写入 1,500 行/事务** | `MATERIAL_UPSERT_BATCH_SIZE = 1_500`；`materials.upserts.chunked(1_500)` 逐块 `upsertMaterials(...)`。每块由 `@Upsert(List)` 落成一条 IMMEDIATE 事务。 | 单条 64,855 行大事务让 WAL 持续增长、checkpoint 无法完成、失败整段回滚、同进程其它写被长时间挡住（R7 §1.2/§2.2）。1,500 行仍把提交摊销到可忽略。 |
| 3 | **材料两张非唯一索引延后建 + 缺失自愈** | 导入窗口内 `DROP INDEX IF EXISTS` 两条：`index_knowledge_teaching_material_source_id`、`index_knowledge_teaching_material_subject_material_type`；结束时 `CREATE INDEX IF NOT EXISTS` 建回。**保留**两条 UNIQUE。每次 `install()` 起点先 ensure。 | 64,855 行 × 2 条普通索引的逐行 B-tree 维护；崩溃在窗口内会留下"索引缺失"而无门发现（R7 §1.3/§2.2 的"移除索引后崩溃且无自愈"）。 |
| 4 | **JSON 按需字段解析** | 主包 + 26 侧车改 **`Json.decodeFromStream` + `@Serializable` DTO/自定义 `KSerializer`**，直接吃 `InputStream`；**不再** `readText()`、**不再**对整份文档 `parseToJsonElement()`、**不再**手工走整棵 `JsonObject`。严格校验语义逐条保留。 | 当前 `String` + `JsonElement` 树 + 最终 DTO 三份驻留；单文件峰值约等于整份 UTF-16 字符串 + 每个节点一个 Kotlin 对象（R7 §1.5）。解析 20.7–21.2 s 的大部分花在这里。 |
| 5 | **导入后 `PRAGMA optimize`** | 全量路径结束、两条索引窗口都关闭后，**执行一次** `PRAGMA optimize`；快速路径不执行；不每卷/每块执行。 | 建索引后没有统计信息，后续查询计划可能选错；官方明确 "All applications should run PRAGMA optimize; after a schema change, especially after one or more CREATE INDEX statements."（R7 §1.3）。 |
| 6 | **保留 `synchronous=NORMAL`；OFF 是禁止项** | 写连接导入期显式 `PRAGMA synchronous = NORMAL`；退出恢复旧值。**任何代码路径不得设 `synchronous=OFF`**，也不得设 `journal_mode=MEMORY/OFF`、跳 fsync、延迟提交、`locking_mode=EXCLUSIVE`、`foreign_keys=OFF`。 | OFF 下 "the database **might become corrupted** if the operating system crashes or the computer loses power"（R7 §1.1 原文）；本仓库同库存学生错题/掌握度，重装不等于可恢复。NORMAL 在 WAL 下不腐蚀，只可能丢最近提交。 |

### 1.2 材料写入 1,500 行/事务（代码级冻结）

**位置**：`core/database/.../RoomKnowledgeContentReconciler.kt:83-86`。

**改法**（语义不变，只改提交粒度）：

```kotlin
private const val MATERIAL_UPSERT_BATCH_SIZE = 1_500

if (materials.upserts.isNotEmpty()) {
    val dao = database.knowledgeTeachingMaterialDao()
    materials.upserts
        .map(KnowledgeTeachingMaterialRecord::toEntity)
        .chunked(MATERIAL_UPSERT_BATCH_SIZE)
        .forEach { chunk -> dao.upsertMaterials(chunk) }
}
```

- **行集合、行序、`inserted/updated` 计数、跳过原因、`acceptedIds`、退役集合全部不变**；只把"一次 List"切成"45 次 List"（2025 44 + 2020 1）。
- 每块是独立事务：崩溃在块 k 之后 ⇒ 前 k 块已提交、后块未写；**完成戳不在 `applyKnowledgeContentUpdate` 里写**，由 `BundledKnowledgeBaseInstaller.reconcile()` 在整包调和返回后写（`BundledKnowledgeBaseInstaller.kt:146-156`），所以崩溃后下次启动**不会命中快路径**，会重跑差分。
- 重跑的收敛性：`planMaterials()` 逐页读库里已有行，`existing == material.toEntity()` 的行不进 upserts；已提交块不会被重复写，未提交块会补写。绑定在所有材料块之后整体替换（`RoomKnowledgeContentReconciler.kt:462-478`）；若崩在材料块与绑定之间，重跑会重新删/写绑定。
- **不得**改成"每行一条事务"（提交开销回到 R7 §1.2 反例），也不得改成"整包一条事务"（本项要消灭的正是后者）。

### 1.3 材料两张非唯一索引：延后 + 自愈（代码级冻结）

**DDL 原文**（来自 `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/73.json:1692-1708`，逐字同义）：

```sql
CREATE INDEX IF NOT EXISTS `index_knowledge_teaching_material_source_id`
  ON `knowledge_teaching_material` (`source_id`)
CREATE INDEX IF NOT EXISTS `index_knowledge_teaching_material_subject_material_type`
  ON `knowledge_teaching_material` (`subject`, `material_type`)
```

**改法**：

1. `RoomKnowledgeImportMaintenance.kt` 新增：
   - `MATERIAL_INDEX_ENSURE_DDL` / `MATERIAL_INDEX_DROP_DDL`（与特征索引同形态）。
   - `suspend fun ensureMaterialIndexes()`：`CREATE INDEX IF NOT EXISTS` 两条。
   - `suspend fun <R> withDeferredMaterialIndexes(block)`：先 `ensureMaterialIndexes()` 收敛上一次崩溃，再 DROP 两条，`finally` 无条件 ensure。
2. `port/KnowledgePort.kt` 新增默认 no-op 的 `ensureKnowledgeMaterialIndexes()`、`withDeferredKnowledgeMaterialIndexes(block)` 与 `optimizeKnowledgeImportStatistics()`（测试替身/历史适配器不受影响）。
3. `RoomStudyDatabase.kt` 把三个新方法委托给 `knowledgeImportMaintenance`（`ensureMaterialIndexes` / `withDeferredMaterialIndexes` / `optimize`）。
4. `BundledKnowledgeBaseInstaller.install()`：
   - 在**快路径判定之前**调用 `database.ensureKnowledgeMaterialIndexes()`（与现有 `ensureKnowledgeSearchFeatureIndexes()` 并列，`BundledKnowledgeBaseInstaller.kt:35`）。
   - 全量路径把 reconcile 包进 `withDeferredKnowledgeMaterialIndexes`（与特征窗口并列，`BundledKnowledgeBaseInstaller.kt:59-61`）。

**冻结边界**：

- 只 DROP 两条**非唯一**索引。`index_knowledge_teaching_material_stable_code` 与 `index_knowledge_teaching_material_content_fingerprint` 是幂等/去重语义的一部分，**保留**。
- 窗口内查询结果不变，只是可能全表扫描；窗口结束后的 schema 与 Room 期望逐字相同。
- 崩溃窗口：进程在 DROP 后、CREATE 前死亡 ⇒ 下次 `install()` 起点的 `ensureKnowledgeMaterialIndexes()` 补建。若下次启动先走了快路径，ensure 仍在快路径判定之前执行，所以仍会补建。
- **不允许**把 ensure 放到快路径之后；也不允许只在 `finally` 里 ensure 而删掉起点 ensure（起点 ensure 是崩溃自愈的唯一入口）。

### 1.4 JSON 按需字段解析（代码级冻结）

**范围**：2 个主包 + 26 个 teaching 侧车。**不在范围**：`moe-2025-teaching-support-v2-index.json`（小索引）与 `moe-2025-update-manifest.json`（111 KB 快速路径文件）——它们继续 `readText` + `parseToJsonElement`，避免把快速路径也拖进流式改造。

**当前形状（实读）**：

- `BundledKnowledgePackResources.load()` 先把所有包/侧车解析完才返回（`:83-99`）。
- `readClasspathResource()` = `bufferedReader().readText()`（`:157-162`）。
- `ReviewedKnowledgePackJsonCodec.decode(rawJson)` 第一行 `json.parseToJsonElement(rawJson)`（`:174-175`）。
- `ReviewedTeachingMaterialSidecarJsonCodec.decode(rawJson, pack)` 第一行 `json.parseToJsonElement(rawJson)`（`:102-103`）。
- 之后手工走树、逐字段转换、逐对象 `requireOnlyKeys`。

**改法（冻结）**：

1. 两个 codec 的入口改为吃 `InputStream`：
   - `ReviewedKnowledgePackJsonCodec.decode(stream: InputStream): KnowledgeBasePack`
   - `ReviewedTeachingMaterialSidecarJsonCodec.decode(stream: InputStream, pack: KnowledgeBasePack): ReviewedTeachingMaterialSidecar`
2. 用 **`kotlinx.serialization.json.io.decodeFromStream`** + `@Serializable` DTO；`Json` 配置保持 `isLenient=false`、`ignoreUnknownKeys=false`、`explicitNulls=false`。
3. 新增依赖（版本与现有 `serialization=1.9.0` 对齐）：
   - `gradle/libs.versions.toml`：`kotlinx-serialization-json-io = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json-io", version.ref = "serialization" }`
   - `core/data/build.gradle.kts`：`implementation(libs.kotlinx.serialization.json.io)`
   - 传递依赖 `kotlinx-io-core-jvm:0.6.0` 由该 artifact 带入；`core/data/gradle.lockfile` 由 `./gradlew :core:data:dependencies --write-locks` 重生成。**只允许**新增 json-io / kotlinx-io 相关条目（含 `-jvm` 变体），不得顺带升级/删除已有条目。
4. **严格校验逐条保留**（这是本项最容易"为了跑通而放松"的地方，冻结为不可谈判）：
   - 每个对象/根对象的**精确键集**（`requireOnlyKeys` 的语义）不变；未知键、缺失键、schema 版本不匹配仍必须拒收。
   - **必需但可空**字段（如 `sourceUri`、`publisher`、`edition`）必须区分"键缺失"与"值为 null"；不得用 DTO 默认值把"缺失"吞成 null。
   - **all-or-none** 三元组（绑定 verdict 三键、材料 provenance 三键）仍必须"全有或全无"。
   - 枚举、字符串 trim/非空、整数非字符串、slug 正则、重复 slug/stableCode、跨引用（source/subject/node/prerequisite/parentSlug 环）等全部现有校验不变。
   - 错误信息可以因 `decodeFromStream` 的异常类型变化而改写，但必须**指名违约字段**；现有断言里只检查 `contains("unknown keys")` 之类语义的，允许保留语义、改文案；**不得删断言或放宽 required**。
5. DTO 无法表达"键存在性"时，用**自定义 `KSerializer` / `CompositeDecoder`** 逐键读取；**不得**用 `JsonElement` 兜底整份文档，也**不得**打开 `ignoreUnknownKeys=true`。
6. 主包与侧车的输出对象（`KnowledgeBasePack` / `ReviewedTeachingMaterialSidecar`）必须与改前**逐字段相等**；用 §2.4 的 28 文件差分门证明，不靠"看起来一样"。

**禁止项（静态门会扫）**：

- 两个 codec 文件里不得再出现 `parseToJsonElement`，不得对主包/侧车调用 `readText()`。
- 不得在导入路径新增 `Json { ignoreUnknownKeys = true }`。
- 不得把 `KnowledgeBasePack.validate()` / `ReviewedTeachingMaterialSidecar` 的 `pack.copy(...).validate()` 删掉或弱化。

### 1.5 `PRAGMA optimize`（代码级冻结）

**位置**：`RoomKnowledgeImportMaintenance.kt` 新增：

```kotlin
suspend fun optimize() {
    database.withRawConnection(isReadOnly = false) { connection ->
        connection.executeDdl("PRAGMA optimize")
    }
}
```

**调用点**：`BundledKnowledgeBaseInstaller.install()` 的全量路径，**在** `withKnowledgeImportTuning { withDeferredKnowledgeSearchFeatureIndexes { withDeferredKnowledgeMaterialIndexes { packs.forEach { reconcile } } } }` **整体返回之后**（此时两条索引的 `finally` 都已 ensure），调用 `database.optimizeKnowledgeImportStatistics()`；**只在全量路径**执行一次。

- 快速路径（`isInstallUpToDate`）直接 return，不执行。
- 不在每卷/每块/每包执行；不改成 `ANALYZE` 全表。
- 失败处理：`PRAGMA optimize` 是查询计划统计，不是内容写入；失败只 `Log.w("KnowledgeImportTuning", ...)`，**不改变** `install()` 的内容结果、不把成功改成失败。§6 必须记录实际是否发出/成功。
- **不**在导入中途 `VACUUM`；**不**切 `locking_mode`；**不**关外键（R7 §2.2）。

### 1.6 `synchronous=NORMAL` 与 OFF 禁止项（代码级冻结）

- 导入期写连接只允许：`PRAGMA synchronous = NORMAL`、`PRAGMA cache_size = -32768`。
- 退出 `finally` 恢复旧值；旧值读取/恢复逻辑保持现有形态（`RoomKnowledgeImportMaintenance.kt:48-75`）。
- **禁止**：`PRAGMA synchronous = OFF`、`PRAGMA synchronous=0`、`journal_mode=MEMORY`、`journal_mode=OFF`、跳过 fsync、应用层"先回已保存再异步 commit"、`locking_mode=EXCLUSIVE`、`foreign_keys=OFF`。
- 新增一个**静态守卫测试**：读取 `RoomKnowledgeImportMaintenance.kt` 与 `BundledKnowledgeBaseInstaller.kt`，断言：
  - 含 `PRAGMA synchronous = NORMAL`；
  - 不含大小写不敏感的 `synchronous` + `OFF` / `synchronous=0`；
  - 含 `PRAGMA cache_size = -32768`。
  这是源码扫描，只证明"这两个文件里没有该字符串"，不证明运行期所有路径；运行期由 §2 的 `PRAGMA synchronous` 读数兜底。

### 1.7 观测点（新增日志；不改变任何写入/判定）

为了让 20 次崩溃注入**可归因**、让 G1–G3 的段耗时**可拆分**，新增一组固定字段的 `Log.d`（tag 固定 `KnowledgeImport`）。它们只读局部变量，不写库、不改顺序、不参与任何断言：

| 日志行（字段顺序固定） | 发出位置 |
|---|---|
| `KnowledgeImport: pack parsed packId=<id> subjects=<n>` | `BundledKnowledgePackResources.loadResource()` 主包解码后、侧车循环前 |
| `KnowledgeImport: sidecar parsed index=<i>/<n> packId=<id>` | 每个侧车解码后（2025 包 n=25，2020 包 n=1） |
| `KnowledgeImport: sources+nodes written pack=<id> nodes=<n> sources=<n>` | `applyKnowledgeContentUpdate` 的节点/来源写完后 |
| `KnowledgeImport: node bindings+relations written pack=<id> bindings=<n> relations=<n>` | `replaceRelations` 返回后 |
| `KnowledgeImport: material chunk=<i>/<n> pack=<id> rows=<n>` | 每个 1,500 行块 `upsertMaterials` 返回后；2025 为 `1/44…44/44`，2020 为 `1/1` |
| `KnowledgeImport: material bindings written pack=<id> rows=<n>` | `replaceMaterialBindings` 返回后 |
| `KnowledgeImport: material retire pass done pack=<id> retired=<n>` | `retireMaterials` 返回后 |
| `KnowledgeImport: feature indexes dropped phase=window-enter` | `withDeferredFeatureIndexes` 的 DROP 之后 |
| `KnowledgeImport: material indexes dropped phase=window-enter` | `withDeferredMaterialIndexes` 的 DROP 之后 |
| `KnowledgeImport: feature index rebuilt subject=<subject>` | `RoomKnowledgeContentReconciler` 的 `rebuildSubject` 调用返回后 |
| `KnowledgeImport: feature indexes ensured phase=startup-heal / window-close` | `ensureFeatureIndexes()` 补建后（起点自愈 / 窗口 `finally` 用不同 phase） |
| `KnowledgeImport: material indexes ensured phase=startup-heal / window-close` | `ensureMaterialIndexes()` 补建后（起点自愈 / 窗口 `finally` 用不同 phase） |
| `KnowledgeImport: optimize issued` / `optimize done` | `PRAGMA optimize` 调用前/后各一行 |

- 日志不包含学生数据、文件内容或任何 PII；只含包 id、subject、计数、块号。
- 这些行是 §2.3 的 kill 触发点，也是 §3.3 的分段证据；**不允许**为了让日志好看而延迟/合并写入。
- 除这组日志外，不新增任何观测机制（不加 trace、不加性能库、不改事务边界）。

---

## 2. 验收（写死）

### 2.0 装置与口径（所有读数都必须带这些）

| 项 | 冻结要求 |
|---|---|
| 设备 | 同一台物理机/模拟器，记录 `ro.product.model`、`ro.build.version.sdk`、`ro.product.cpu.abi`、`/proc/meminfo` 前 3 行 |
| APK | 同一 `sha256`、同一 `versionCode`；debug/localFirst 变体与正式变体不得混报 |
| 内容包 | 同一主包 sha（当前 `10ede1bf…`）+ 同一 manifest（`version=28` / `ce8edb8f89d91fe7`） |
| T0 | `am start -W` 调用前的 `date +%s%3N`（进程启动/安装协程的同一轮） |
| 冷装 | `adb uninstall <pkg>` → 确认 `pm path` 为空 → `adb install -t <apk>` → `logcat -c` → `am start -W` |
| 全量完成 | logcat 出现 `KnowledgeInstall: install finished in <ms> ms (full path: parse + reconcile)`；`<ms>` 为该次全量导入时长 |
| p95 | **nearest-rank**，不插值；N≥20 才允许报 p95；N<20 只报 median/max，不报 p95 |
| 样本数 | **冷装 N ≥ 20**；**崩溃注入 20 次**（每次一个不同 kill 点，见 §2.3） |
| 计时来源 | `System.nanoTime()` / `date +%s%3N`；不得用"解析耗时 + 索引耗时"相加推算 G2 |

### 2.1 G1 首查（验收读数；本臂不保证达标，见 §0.3）

**定义**：冷装 T0 → **第一次返回非空检索结果**（固定首科 = 包内第一个 subject **MATH**；查询用该科金标第一条，API 见 §2.2）。记录 `firstNonEmptyQueryMs = 首次非空时刻 - T0`。

| 门 | 目标 | 硬上限 |
|---|---:|---:|
| G1 | **p95 ≤ 5,000 ms** | **max ≤ 10,000 ms** |

- 探针从 `T0+500 ms` 起每 250 ms 发一次首科查询，直到首次 `results.isNotEmpty()`；首次非空即停，记录时刻。
- 查询必须走生产检索路径（与 `GoldenRetrievalInstrumentedTest`/设备金标同一 API），不得用"直接查表"代替。
- **本臂 §1 不改变写入顺序**：`applyKnowledgeContentUpdate` 仍是 sources→nodes→bindings→relations→materials→bindings→retire→逐科 rebuildSubject（`RoomKnowledgeContentReconciler.kt:53-104`），特征索引在材料之后建。因此 G1 预计≈全量完成时间；§6 必须如实报，**不得**把 G1 写成绿。

### 2.2 G2 全量 / G3 导入期查询

**G2 全量完成**：

| 门 | 目标 | 硬上限 |
|---|---:|---:|
| G2 | **p95 ≤ 45,000 ms** | **max ≤ 60,000 ms** |

- 读数 = §2.0 的 `install finished in <ms> ms (full path…)`。
- 20 次冷装的逐次值、p50/p95/max、装置指纹全部落盘；同一次安装只允许一行 `install finished`。
- 现有基线 47,504 / 51,775 / 51,646 ms（R7 引用，**本文未重测**）；目标 45 s 是"不劣化并略优"的工程线，不是承诺 30 s。

**G3 导入期查询**：

| 门 | 目标 | 硬上限 |
|---|---:|---:|
| G3 延迟 | **p95 ≤ 250 ms**，`overBudget = 0`（>250 ms 的轮询点计数） | **max ≤ 1,000 ms** |
| G3 静默空 | **0 次** | — |

- 探针：每个科目取金标文件里**该科第一条**查询（MATH/PHYSICS/CHEMISTRY/BIOLOGY 各一条，共 4 条，按文件顺序冻结）；从 `T0+500 ms` 起每 250 ms 轮询一次，直到 G2 完成；样本 = **全部轮询点**，不是 4 条各一次。
- 每次记录：墙钟、`results.size`、返回的 completeness/状态（若有）。
- **静默空**的冻结定义：该轮询点返回 `results.size == 0`，且响应里没有明确的"内容尚未就绪/仅部分可用"标记。用金标查询（导入完成后必有结果）作为对照；导入期出现 0 且无标记 ⇒ 计 1 次静默空。
- 本臂 §1 没有"查询降级/部分可用"改造；若当前 API 在导入期返回空且无标记，G3 会红。§6 必须报实际次数，**不得**把"没有查询发生"当成 0 次静默空（探针必须真的发查询）。
- G3 的 p95 **只统计导入期**（`install` 开始到 `install finished`），不含完成后的查询。

### 2.3 崩溃注入（20 次，必须可归因）

**目标**：进程被杀后重开必须自愈，且 **user 表零丢失**。

**注入方式**：ADB 外部 kill（`adb shell am force-stop <pkg>` 或 `adb shell kill -9 <pid>`），在**指定进度标记**出现后立即执行；每次只杀一次。

**20 个 kill 点（冻结；每个点至少命中一次）**：

| # | 命中标记（新增结构化日志；见 §1.7） | 说明 |
|---:|---|---|
| 1 | `KnowledgeImport: pack parsed packId=moe-2025-four-subjects-v1` | 主包解析完、侧车未开始 |
| 2 | `KnowledgeImport: sidecar parsed index=1/25 packId=moe-2025-four-subjects-v1` | 首卷解析后 |
| 3 | `KnowledgeImport: sidecar parsed index=13/25 packId=…` | 中段解析 |
| 4 | `KnowledgeInstall: packs fully parsed in <ms> ms`（现有日志，`BundledKnowledgePackResources.kt:91`） | 全部包/侧车解析完、第一次写库前 |
| 5 | `KnowledgeImport: sources+nodes written pack=…` | 节点/来源写完 |
| 6 | `KnowledgeImport: node bindings+relations written pack=…` | 绑定/关系写完 |
| 7 | `KnowledgeImport: material chunk=1/44 pack=moe-2025-four-subjects-v1` | 首块材料提交后 |
| 8 | `KnowledgeImport: material chunk=5/44 pack=…` | |
| 9 | `KnowledgeImport: material chunk=10/44 pack=…` | |
| 10 | `KnowledgeImport: material chunk=20/44 pack=…` | |
| 11 | `KnowledgeImport: material chunk=30/44 pack=…` | |
| 12 | `KnowledgeImport: material chunk=40/44 pack=…` | |
| 13 | `KnowledgeImport: material chunk=44/44 pack=…` | 2025 材料全写完、绑定未写（2020 的 1 块另计，见 §1.7） |
| 14 | `KnowledgeImport: material bindings written pack=…` | 绑定写完 |
| 15 | `KnowledgeImport: material retire pass done pack=…` | 退役判定完 |
| 16 | `KnowledgeImport: feature indexes dropped phase=window-enter` | 特征索引窗口进入 |
| 17 | `KnowledgeImport: material indexes dropped phase=window-enter` | 材料索引窗口进入 |
| 18 | `KnowledgeImport: feature index rebuilt subject=…`（第一科） | 特征重建中 |
| 19 | `KnowledgeImport: material indexes ensured phase=window-close` | 材料索引窗口退出（此时完成戳已写；验证快路径 + 起点自愈） |
| 20 | `KnowledgeImport: feature indexes ensured phase=window-close` | 特征索引窗口退出（最外层 finally，最后补建） |

- 这 20 行日志是**观测点**，不改变任何写入/判定；tag 固定 `KnowledgeImport`，字段固定，便于 `logcat -m` 精确匹配。
- 若某标记因设备太快/太慢没命中，该次注入**无效**，重试；20 次指**20 次有效命中**。额外尝试次数照实记，不计入 20。
- 若 kill 发生在 `install finished` 之后（例如 harness 延迟执行），下一次启动会走快路径；仍必须满足 §2.3 的恢复断言。

**每次注入的完整流程（冻结）**：

1. 装 APK → 等**首次**全量导入完成（`install finished in … (full path…)`）→ 确认内容计数达标。
2. 用驱动测试 seed 12 张用户表 → 取快照。
3. 驱动测试清掉 2025 包的 `content_install_state` 行（只清这一行，不动内容/用户数据）→ 启动第二次全量 `install()`。
4. ADB 等到指定 kill 标记 → `force-stop`/`kill -9`。
5. 重开 App（或跑恢复测试）→ 等 `install()` 结束（快路径或全量）→ 做 §2.3 的恢复断言与用户数据快照比对。

**每次注入前后的用户数据快照（"零丢失"的冻结口径）**：

1. 在注入前，用驱动测试 seed 以下 **12 张用户表**各至少 1 行（通过生产 DAO/Room，不用裸 SQL）：
   `problem`、`problem_revision`、`practice_unit`、`error_book_entry`、`review_plan`、`review_queue_item`、`review_log`、`learner_problem_memory_state`、`learner_knowledge_mastery_state`、`attempt_submission`、`tutor_conversation`、`tutor_message`。
2. 快照 = 每张表 `COUNT(*)` + 按主键排序后的**全字段规范化 sha256**（字符串/整数/可空/blob 一律带长度前缀；不忽略任何列）。
3. 注入后重开、等恢复完成，再取一次快照；**12/12 表计数与 sha 必须逐表相等**。
4. 另对**全部非知识表**取 `COUNT(*)` 快照（知识表 = 表名以 `knowledge_` 开头 + `content_install_state`）；任何非知识表计数变化 = 失败。
5. `PRAGMA integrity_check` 必须返回 `ok`。
6. 注入窗口内必须**静默**：不触发学习/复习写入（否则 `review_*`/`projection_*` 的合法变化会污染"零丢失"判定）；驱动测试在 seed 后停掉后台调度，只让知识导入协程运行。若某张用户表因合法后台任务必须变化，该表从 12 表快照中剔除并单列理由，不得默认忽略。

**恢复后的内容断言（每次注入都做）**：

| 表 | 期望（当前包；若基线安装有跳过材料，以基线实测为准并在 §6 登记） |
|---|---:|
| `knowledge_node` | **3,733** |
| `knowledge_source` | **62** |
| `knowledge_node_source_binding` | **3,299** |
| `knowledge_node_relation` | **350** |
| `knowledge_teaching_material` | **64,855** |
| `knowledge_teaching_material_node_binding` | **64,857** |
| 材料索引 | `PRAGMA index_list(knowledge_teaching_material)` 含两条非唯一索引名 |
| 特征索引 | `PRAGMA index_list(knowledge_search_feature)` 含两条二级索引名 |
| 重复行 | 无重复主键；`COUNT(DISTINCT material_id)` = 64,855 |
| 完成戳 | 最终 `content_install_state.contentVersion = ce8edb8f89d91fe7`（2025 包） |

- 恢复过程允许重跑全量（完成戳未写）或走快路径（完成戳已写）；两种都合法，但最终计数/索引/用户数据必须满足上表。
- 恢复时长不计入 G2；单独报告（诊断用）。

### 2.4 等价性门（JSON 改动的前置硬门）

在改 codec **之前**，用当前 codec 对 28 个文件（2 主包 + 26 侧车）生成**逐文件规范化摘要**，落 `core/data/src/test/resources/knowledge/decode-golden.txt`（28 行：`<relative-path>\t<sha256>`）。摘要覆盖解码后的**全部字段**（按记录产生顺序、带长度前缀、UTF-8），不是原始文件 sha。

改完后，新 codec 对同一 28 个文件重新解码并算摘要，必须**逐行相等**（28/28）。

- **时序冻结**：golden 必须在改 codec 前生成；若执行轮先改 codec 再生成 golden，该门无效，按未过处理。
- 该门之外，现有契约测试全部保持绿（不降级）：
  `ReviewedKnowledgePackJsonCodecTest`、`BundledKnowledgeNodesContractTest`、`BundledTeachingMaterialsContractTest`、`ReviewedTeachingMaterialSidecarBindingVerdictTest`、`ReviewedTeachingMaterialSidecarProvenanceTest`、`ReviewedKnowledgeUpdateManifestJsonCodecTest`、`KnowledgeBaseImportContractTest`。
- 另跑 `GoldenRetrievalJvmTest` + `ProductionLexicalLegExportTest`，证明解码结果进入检索/词面腿后读数不变（这三条若写前已红，按 §5 的已知红登记，不得归因本臂）。

---

## 3. 判读（写死）

### 3.1 采纳条件（按 ask 原文，只有两条）

**采纳 ⇔ 同时满足：**

1. **G2 全量 p95 ≤ 45,000 ms**（20 次冷装，nearest-rank）；
2. **崩溃注入 20/20 通过**（每次：恢复完成、内容计数/索引达标、`integrity_check=ok`、12 张用户表与全部非知识表计数零变化）。

**否则回滚**（任一失败、样本不足 20、装置/APK/内容包不一致、或 G2 的 p95 无法计算，都按"不采纳"处理）。

- G1/G3 是 §2 的验收读数；按 §3 的采纳条件它们**不阻断采纳**，但 §6 必须逐项报告实际值。若 G1/G3 红而 G2+崩溃绿，报告必须写明"G1/G3 未达标、未声称达标"，**不得**用"采纳"二字暗示全部门绿。
- G2 的硬上限 60,000 ms：任一单次 >60,000 ms ⇒ 该次记为硬失败；即使 p95 ≤45,000 ms，也必须在 §6 单列，并由 owner 决定是否按"硬上限失败"回滚（本预注册不擅自把硬上限写成第三条采纳条件，但也不允许把超硬上限的样本算进"达标"叙述）。
- 若 20 次冷装中有任一次不是真冷装（`pm path` 未清空、`firstInstallTime` 不是当轮），该样本作废，不能凑数。

### 3.2 不允许的"变绿"方式

- 删断言、放宽 `requireOnlyKeys`、开 `ignoreUnknownKeys=true`、把 `synchronous` 改回 OFF。
- 把 1,500 行/事务改成 1 行或 64k 行。
- 只跑 3 次冷装就报 p95（R7/R4 明确 3 次只能报 median/max）。
- 崩溃注入只做"正常退出/重启"而不杀进程。
- 用 `pm clear` 代替 `uninstall` 后声称"冷装"；用同一进程热重启代替冷装。
- 把 G1/G3 的 `NOT_RUN` 写成 0/绿。

### 3.3 报告义务（§6 至少包含）

- 写前/写后 HEAD、porcelain、§4 逐文件 pre/post sha。
- 28 文件 decode-golden 的生成时点（必须在改 codec 前）与 28/28 结果。
- 现有契约测试的逐类结果；若有失败，逐条列文件名/断言，不得汇总成"部分失败"。
- 20 次冷装的逐次 `install finished ms`、p50/p95/max、装置/APK/内容包指纹、WAL 峰值、分段日志。
- G1/G3 的逐轮询点 p50/p95/max、overBudget、静默空计数（必须逐次列，不是只报总数）。
- 20 次崩溃注入的逐次：kill 点编号、命中标记、恢复路径（fast/full）、`integrity_check`、六张知识表计数、两条材料索引/两条特征索引是否在、12 张用户表快照比对结果、全部非知识表计数比对结果。
- `PRAGMA optimize` 是否实际发出/成功；材料索引窗口内的查询退化（如有）。
- 未跑项一律 `NOT_RUN` / `UNVERIFIED`。

---

## 4. 回滚（触碰清单 + pre/post sha）

### 4.1 A. 既有代码（写前 pre-sha 为工作树值；带 `M` 的是他线已有改动，本臂在其上追加）

| # | 路径 | 预期动作 | pre-sha256（写前实测） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt` | 加材料索引 ensure/drop/withDeferred、`optimize()`、pragma 常量/守卫 | `c8ee60f518f57f1c478977bfe4edf7c76cf79736766382d72246c064e1a54edb`（`M`，他线 OFF→NORMAL） | §6 |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | 三个默认 no-op 端口方法（材料索引 ensure/窗口 + optimize） | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` | §6 |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | 三个 override 委托 | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` | §6 |
| 4 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeContentReconciler.kt` | 材料 upsert 按 1,500 行分块 | `e6ef7cc01342ca2cd3035b08d395d4ad1e8d61b5d7e72334f6c255bdd64e3142` | §6 |
| 5 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstaller.kt` | 起点 ensure 材料索引、材料索引窗口、optimize、进度日志 | `989549a0989d76bc1bd0f7c9b08d31a2ea55feef2c9eb2b1d5180fc5ddd54f1e` | §6 |
| 6 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgePackResources.kt` | 主包/侧车改 `InputStream` + 流式解码；保留 manifest/index 旧路径 | `4525edaa1f65b5457e8fc80116ec700df5dc15cac5868f6526108932598bd7d2` | §6 |
| 7 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/ReviewedTeachingMaterialSidecarJsonCodec.kt` | 侧车改 `InputStream` + 流式解码；严格校验逐条保留 | `80053e2d7bc458bb05a14a594cae04e20b1e048ba0bc5f6c18dad94e5cb5ecf7` | §6 |
| 8 | `gradle/libs.versions.toml` | 加 `kotlinx-serialization-json-io` 坐标 | `a3b18a448f6629f2b1df7c7d4842606ca8fd412934f5d23ea891de877a830856` | §6 |
| 9 | `core/data/build.gradle.kts` | 加一条 `implementation(libs.kotlinx.serialization.json.io)` | `a63e5beceab8ddb7764adf2a82dd2c069bd944d45efd295dbbf68b3588875ed3`（`M`，他线 KD-83） | §6 |
| 10 | `core/data/gradle.lockfile` | Gradle 重生成（新增 json-io / kotlinx-io 条目） | `daca64b930a498f8eede7df3c707ccbb5745d8731374db8957c7e3194358525d`（`??`，未跟踪） | §6 |

### 4.2 B. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/data/src/test/kotlin/.../knowledge/BundledPackStreamingDecodeEquivalenceTest.kt` | 28 文件差分门（golden 对照） |
| 2 | `core/data/src/test/resources/knowledge/decode-golden.txt` | 改 codec **前**用旧 codec 生成；28 行 |
| 3 | `core/database/src/test/kotlin/.../KnowledgeImportMaintenanceTest.kt` | 材料索引 DDL/幂等、pragma 常量、静态 OFF 守卫 |
| 4 | `core/data/src/androidTest/kotlin/.../knowledge/KnowledgeImportCrashDriverInstrumentedTest.kt` | seed 用户数据、清完成戳、启动全量导入、等待外部 kill（必须在 `core:data`：只有它能依赖 installer） |
| 5 | `core/data/src/androidTest/kotlin/.../knowledge/KnowledgeImportRecoveryInstrumentedTest.kt` | 恢复后计数/索引/用户数据断言 |
| 6 | `tools/kb_perf/cold_install_20x.sh` | 20 次真冷装（uninstall→install→logcat→am start→等完成） |
| 7 | `tools/kb_perf/crash_inject_20x.sh` | 20 个 kill 点编排 |
| 8 | `tools/kb_perf/summarize_import_perf.py` | nearest-rank p95、G1/G3 汇总、崩溃 20 次汇总（只读原始日志，不出判词） |

### 4.3 C. 零触碰

| # | 路径 / 对象 | 约束 |
|---:|---|---|
| 1 | `core/data/src/main/resources/knowledge/**`（主包/侧车/manifest/index/向量） | 一个字节都不改；本臂只改解析代码与 DB 写入节奏 |
| 2 | `core/data/src/main/kotlin/.../knowledge/KnowledgeUpdateManifest.kt` | 不改（manifest 仍走旧 `readText` 快速路径） |
| 3 | `core/data/src/main/kotlin/.../knowledge/BundledKnowledgePackResources.kt` 的 `updateManifest`/`indexedSidecarNames` 语义 | 不改；只改主包/侧车的解码入口 |
| 4 | `.vec` / `.vec.json` / `DenseRecallAssembly` | 不改；与导入性能无关 |
| 5 | 学生数据表 / 备份/恢复 / 金标 / alias_map | 不改；崩溃注入只读快照，不写学生数据 |
| 6 | `RoomKnowledgeImportMaintenance.kt` 里他线的 `OFF→NORMAL` 改动 | **不得回滚**；本臂保留 NORMAL |

### 4.4 写前备份 / 回滚 / 采纳

**写前备份**

1. 把 §4.1 的 10 件复制到 `build/agent-batch34/import-perf/backup/`，落 `sha256sum` 全表。
2. 复核：§4.1 的 10 个 pre-sha 与上表逐件相同；`git status --porcelain -- <10 路径>` 与 §4.1 标注一致（2 个 `M` + 1 个 `??` + 7 个干净）。
3. 复核内容基线：主包 `10ede1bf…`、manifest `version=28`/`ce8edb8f89d91fe7`、材料 64,855、绑定 64,857、节点 3,733、侧车 26/119,573,849 B。

**回滚**

1. 不得整树 `git checkout` / `stash` / `reset`；用备份覆盖 §4.1 的 10 件，删除 §4.2 的 8 件（若已入库则回滚提交）。
2. **不得 `pm clear`、不得 `uninstall`**（会删学生数据）。用 `adb install -r` 装回基线 APK，保留应用数据。
3. 若回滚发生在"材料索引已 DROP、未 CREATE"的窗口内：先让新 APK 启动一次以触发 `ensureKnowledgeMaterialIndexes()`，或由调试工具执行 §1.3 的两条 `CREATE INDEX IF NOT EXISTS`；旧 APK 不认识这两条索引，但缺索引只会让查询变慢。**前提是 Room 打开只校验 schema identity、不逐条索引校验**——现有特征索引窗口已依赖这一点（本会话未重新验证 Room 3 的 open 行为；执行轮若发现旧 APK 因缺索引打不开，必须在回滚前先 ensure 两条索引，并把这个事实登记进 §6）。**不得**用删库/重建来"修复"。
4. 复核：`PRAGMA integrity_check=ok`；知识表计数与 §2.3 表一致；12 张用户表快照与注入前一致；两条材料索引存在。

**采纳**

只有 §3.1 的两条同时满足才采纳；保留 §4.1/§4.2；§6 登记 post-sha、28/28 差分、20 次冷装、20 次崩溃注入的全部读数。G1/G3 若红，按 §3.1 的措辞报告，不阻止采纳但不得写成绿。

---

## 5. 未做 / 未验证（写作时点）

- **未改任何代码/内容包**：§1 是冻结设计；所有性能数字来自 R7/R4 的引用，**本文未重测**。§2 的 G1/G2/G3 与 20+20 全部 `NOT_RUN`。
- **未跑 Gradle/设备/基准/崩溃注入**：`NOT_RUN`。
- **`decodeFromStream` 的编译兼容性未验证**：`kotlinx-serialization-json-io:1.9.0` 在 Maven Central 存在（本会话 `WebFetch` 实查：jar/pom/模块元数据齐全，POM 传递 `kotlinx-serialization-json-jvm:1.9.0` + `kotlinx-io-core-jvm:0.6.0`），但未在本仓 Kotlin 2.3.21 / minSdk 23 / Android 上编译过。执行轮先跑 `:core:data:compileDebugKotlin` 验证；编译不过就停，不得换别的库偷偷加依赖。
- **严格键集能否用 DTO 完整保留未验证**：这是本臂最大风险。若自定义 `KSerializer` 无法在合理范围内保留全部 `requireOnlyKeys`/all-or-none/必需可空语义，**停**，回退到"流式分词 + 逐对象校验"或另立预注册；**不得**开 `ignoreUnknownKeys=true`、删契约测试、放宽 required。
- **G1/G3 在单遍导入下预计不达标**：§1 没有分阶段可用/查询降级；G1 预计≈G2，G3 预计在特征索引建好前有静默空。§3 的采纳条件不含它们，但 §6 必须如实报；若 owner 要求它们也阻断，需另立分阶段预注册。
- **材料索引窗口内的查询退化未测**：哪些读路径会全扫、p95 退化多少，未测。
- **`PRAGMA optimize` 的收益未测**：未做导入前后 `EXPLAIN QUERY PLAN` 对比。
- **`cache_size=-32768` 的收益未测**：R7 只给机制与默认值（默认约 2 MB），未给本仓基准。
- **材料 1,500 行/事务的最优点未测**：1,500 是冻结值，不是从本仓扫参得出；若 G2 不达标，不得在本臂内改成 2,000/1,000 来凑门（那是另一个预注册）。
- **20 个 kill 点依赖新增进度日志**：日志尚未实现；若某标记无法稳定命中，按 §2.3 重试，不得把未命中的 kill 算作通过。
- **用户表快照的 DAO seed 夹具未建**：12 张表的 seed/规范化 sha 代码是设计，不是已实现。
- **`core/data/gradle.lockfile` 未跟踪**：本臂新增依赖后由 Gradle 重生成；若 CI 不使用该锁文件，需在 §6 说明实际依赖解析结果。

---

## 6. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；备份与回滚核验；§4.1/§4.2 逐文件 post-sha；decode-golden 的生成时点与 28/28；契约测试逐类结果；20 次冷装的逐次 `install finished ms`、p50/p95/max、装置/APK/内容包指纹、WAL 峰值、分段日志；G1/G3 逐轮询点 p50/p95/max、overBudget、静默空计数；20 次崩溃注入逐次 kill 点/命中标记/恢复路径/`integrity_check`/六张知识表计数/四条索引/12 张用户表与全部非知识表快照比对；`PRAGMA optimize` 实际执行情况；§3.1 的采纳/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
