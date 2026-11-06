# R2 备份包格式前向/后向兼容研究（closure 版本协商、缺表按空、行数对账）

| 字段 | 值 |
|---|---|
| evidence-id | `B41-R2-BACKUP-COMPAT-01` |
| executed-at | 2026-11-02（本机时间；文档命名按计划批次 41） |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（与 `origin/main` 同值，见批次 41 基线 §1.1） |
| 执行者 | 数据格式兼容研究员（子代理） |
| 授权范围 | 只读：读计划/记录/源码/schema JSON + `git show`/`grep`/`python` 只读命令 + 官方文档与 GitHub 源码 |
| 未执行 | 任何 Gradle 任务、任何设备/模拟器命令、任何 git 写操作、任何源码改动、任何测试运行 |
| 输入 | 计划 `plan-2026-11-03-batch-41.md` §1 R2 / §2 item 5–6；批次 40 记录 §8 #16、§9.1-9；研究交叉复核 `B41-RESEARCH-CROSSCHECK-01` 对 R2 的三问 |
| 口径变更 | 批次 40 记录 §8 #16 的处置是"产品裁定：接受并写升级说明，或加兼容读侧；本轮不擅自实现"；批次 41 计划 item 5 已直接要求"closure 4→5 旧 v3 包**兼容读侧**"。本文件按计划执行，旧"待裁定"状态视为被覆盖（同 `research-crosscheck.md` C5 的登记要求） |

> 本文件区分三类陈述：**实读**（给出路径:行号）、**外部来源**（给出 URL 与采纳/拒绝理由）、**建议**（未实施，标 `UNVERIFIED`）。本会话零 Gradle、零设备、零测试执行，任何"行为成立"的判断都只来自源码阅读，不来自运行。

---

## 0. 结论（可施工的 9 条）

1. **协商单位不是 `formatVersion`，而是 `closureVersion`**。`formatVersion` 决定"清单怎么读"（v1/v2/v3），`closureVersion` 决定"用哪张规则表解释 `rowCounts`/`closureDigest`"。把后者当版本号硬等值比较（`SmbkArchiveCodec.kt:444-447`、`:1162-1167`）是当前旧包被拒的直接原因。
2. **接受 closure 4，但只在不可变规则表下接受**。在代码里冻结一份 `closure=4` 规则表快照（91 键 / 83 裁剪 / `smbk-selection-v4` 前缀 / mask {9,13,15} / `INCLUDED`），读侧按**声明版本**的规则校验 digest 与键集合，绝不按 v5 规则重算。
3. **支持窗口 = N-1**：`closure ∈ {4,5}`。`closure ≤ 3` 明确拒绝（仓内没有不可变规则表，digest 无法按当时规则重算）；`closure ≥ 6` 明确拒绝并提示升级 App。理由与替代方案见 §3.4。
4. **缺表填充只允许"版本增量表"**：`closure 4→5` 的增量是新增 `error_cause_observation` / `error_cause_binding` 两表、删除 `knowledge_search_material_feature` 一表（§1.2，git 实读）。只有新增表允许以 0 行补齐；任何其他缺表都是拒绝，不是 0。
5. **被删表必须"验空后丢弃"，不得直接忽略**：`knowledge_search_material_feature` 在 v4 是恒裁派生表（`BackupSnapshotTrimmer.kt:500`），声明值必须为 0 且载荷实测必须为 0/不存在；非 0 即拒（这是"不得静默丢行"在删除方向上的判据）。
6. **行数对账分三层**：R1 声明 vs 载荷（按**声明版本**的键空间，逐键 `getValue`，禁止 `?: 0L`）；R2 新增表实测为 0、被删表实测为 0/缺失；R3 迁移后复算（共享表不变、新增表为 0、总行数不变）。当前实现只有 R1 的一部分，且 R1 用的是**当前**键空间（`AndroidBackupRepository.kt:1263-1283`）。
7. **当前实现有 4 个具体缺口**（§5.1）：`?: 0L` 掩盖缺键、被删表从不检查、无迁移后复算、manifest 的 `databaseSchemaVersion` 声明从未与载荷 `user_version` 对拍。另有 1 个结构残留：`72→73` 迁移不 DROP `knowledge_search_material_feature`（§1.4）。
8. **失败码目前不存在**：`BackupValidation.Invalid` 只有 `reason: String`（`core/domain/.../BackupRepository.kt:140`）。建议在 `core/data` 内建内部 `enum BackupFailureCode`，测试/证据用码、用户文案保留中文；`core/domain` 是热区，本批不改（§6）。
9. **不得静默丢行的 8 条判据**见 §7；其中 D1/D2/D3 可在 JVM codec 测试里钉住，D4/D5 需要 instrumented 或仓库层用例。

---

## 1. 基线实读

### 1.1 四类版本维度（互不等价，不能合并成一个号）

| 维度 | 字段 | 现值 | 读侧位置 | 语义 |
|---|---|---|---|---|
| 容器/清单格式 | `manifest.formatVersion` | 3（写侧），可读 1..3 | `SmbkArchiveCodec.kt:92,99,102,1039-1044` | "这份清单该怎么解析"。v1 无 settings/版本戳；v2 加版本戳；v3 加必填 `selection` |
| 载荷库 schema | `manifest.databaseSchemaVersion` + 包内 `PRAGMA user_version` | 73（`StudyDatabase.kt:142`） | `SmbkArchiveCodec.kt:42-43,1118`；恢复在 `AndroidBackupRepository.kt:1110-1180` | "这份库需要跑哪些迁移"。协商由 Room 迁移链完成，不由 codec 完成 |
| 选择记录形状 | `selection.schemaVersion` | 2 | `SmbkArchiveCodec.kt:144,1156-1160` | "selection 块的字段形状" |
| 闭包规则表 | `selection.closureVersion` | 5（`SmbkSelectionPlan.kt:70`） | 写侧 `:444-447`；读侧 `:1162-1167` | "用哪张规则表解释 rowCounts/digest"；**本文件的协商对象** |

关键事实：`closureVersion` 不是 `schemaVersion` 的别名（`SmbkSelectionPlan.kt:69` 自述），但它每次 bump 都伴随表集合变化。因此"旧包可恢复"必须同时回答两层：规则表认不认识（closure）+ 库能不能迁到当前 schema（Room）。

### 1.2 closure 4 与 closure 5 的精确差异（`git show` 实读）

`git show 70509247:.../SmbkSelectionPlan.kt`（closure 4 时点）与 HEAD 对比：

| 项 | closure 4（commit `70509247`） | closure 5（HEAD） | 证据 |
|---|---|---|---|
| `SCHEMA_VERSION` | 72 | 73 | v4 `:44`；HEAD `:54` |
| `CLOSURE_VERSION` | 4 | 5 | v4 `:54`；HEAD `:70` |
| digest 前缀 | `smbk-selection-v4` | `smbk-selection-v5` | v4 `:57`；HEAD `:73` |
| 表数 | 91 | 92 | v4 `:548`；HEAD `:571` |
| 裁剪顺序 | 83 | 84 | v4 `:557`；HEAD `:581` |
| 派生表 | 3（含 `knowledge_search_material_feature`） | 2 | v4 `:174-179`；HEAD `:193-197` |
| 新增表 | — | `error_cause_observation`、`error_cause_binding`（MASTERY 类，HEAD `:164-166`） | `git diff` 表行 |
| 删除表 | `knowledge_search_material_feature` | — | `git diff` 表行 |
| 允许 mask | {9,13,15} | {9,13,15} | v4 `:60-63`；HEAD `:76-79` |
| 资产策略 | `INCLUDED` | `INCLUDED` | v4 `:69`；HEAD `:85` |

schema JSON 交叉核对（`python` 实读）：

```text
72 version 72 hash aa5e8e8e6536 entities 91   material_feature True  | error_cause False
73 version 73 hash 1d086050204a entities 92   material_feature False | error_cause True
```

结论：closure 4→5 的增量恰好是 **+2 表 / −1 表 / 裁剪 +1 项 / 前缀 v4→v5**。没有类别归属、mask 语义、资产策略的变化——这让"投影"（projection）成为可能，也把风险集中到"被删表是否有行"这一处。

### 1.3 现有拒绝点（实读）

| 位置 | 条件 | 现文案（节选） |
|---|---|---|
| `SmbkArchiveCodec.kt:444-447` | 写侧 `selection.closureVersion != 5` → `IllegalArgumentException` | "本批写出 5" |
| `SmbkArchiveCodec.kt:1162-1167` | 读侧 `record.closureVersion != 5` → `SelectionRead.Invalid` | "不受支持，本机规则表为 5" |
| `SmbkArchiveCodec.kt:1156-1160` | `selection.schemaVersion != 2` | "本机只读 2" |
| `SmbkArchiveCodec.kt:1195-1201` | `rowCounts.keys != SmbkSelectionPlan.rowCountKeys`（当前 92） | "必须覆盖 92 表：missing=… extra=…" |
| `SmbkArchiveCodec.kt:1222-1227` | digest 重算不一致（用当前 v5 算法） | "declared=… recomputed=…" |
| `SmbkArchiveCodec.kt:1039-1044` | `formatVersion > 3` / `< 1` | "版本过新/过旧" |
| `SmbkArchiveCodec.kt:1054-1068` | v3 却无 `selection` | "缺少内容选择块" |

因此一份 closure 4 的包今天在**第一道**（`:1162`）就被拒，永远走不到 digest/行数；即使放行，第二道（`:1195`）也会因 91≠92 再拒一次（`missing=[error_cause_binding, error_cause_observation]`，`extra=[knowledge_search_material_feature]`），第三道（`:1222`）还会因前缀/版本不同重算出不同摘要。**三处必须一起改成"按声明版本取规则表"**，只改第一处会得到一个新的、更难解释的拒绝。

### 1.4 现有可复用的迁移与测试设施（实读）

- `MIGRATION_71_73`（`ErrorCauseMigration_72_73.kt:34-38`）与 `ERROR_CAUSE_MIGRATION_72_73`（`:40-44`）都只调用 `createErrorCauseTables`；两条共用同一组 DDL（`:47-57`）。
- 迁移自述"**只建空表 + 索引，不回填任何历史行**"（`ErrorCauseMigration_72_73.kt:17-19`）。这正是"新增表按空表"的权威依据。
- 迁移已在生产 builder 注册：`StudyDatabase.kt:423-424`。
- 已有 instrumented 用例钉住"v72 旧行保留 + 两新表为空 + 唯一索引存在"：`FullMigrationMatrixInstrumentedTest.kt:2040-2082`（用 `createDatabaseFromExportedSchema(..., version = 72)` 造 v72 库，`:2045`）。
- **结构残留（新发现）**：`ERROR_CAUSE_MIGRATION_72_73` 全文无 `DROP`（`grep -n "DROP\|material_feature"` 返回空，rc=1），而 72 库含 `knowledge_search_material_feature`、73 不含。即：一个真实 v72 库迁移到 73 后会**多留一张 73 规则里不存在的表**。当前没有任何用例断言 `sqlite_master` 与 `73.json` 完全相等（`FullMigrationMatrixInstrumentedTest` 只做"存在性"断言，如 `:1038,1046,1567`）。它不破坏运行，但会让"恢复后库 = 73 形状"不成立；见 §10 待裁定。

---

## 2. 外部实践与采纳

### 2.1 Protocol Buffers：显式 presence

来源：<https://protobuf.dev/programming-guides/field_presence/>（官方，单一来源但属规范文档）

实读要点（引原文）：

- "explicit presence, where the API also stores whether or not a field has been set"；生成 `has_foo` 区分"未设置"与"设成默认值"。
- "the serialization contains no information about not-present values."
- "Repeated fields and maps do not track presence"；"there is no distinction between an empty and a not-present repeated field."

**映射**：`selection` 块用 `null` 表示 v1/v2 的"全量"语义，正是显式 presence（`SmbkArchiveCodec.kt:82-88,1054-1062`）；而 `rowCounts` 是 map，**没有 presence**——"键缺失"与"值为 0"在 JSON 里可区分，但一旦读侧用 `?: 0L` 兜底，语义上就退化成"空=缺席"（见 §5.1 G1）。采纳结论：键集合必须**精确相等**，不允许用默认值补缺键。

补充来源：<https://protobuf.dev/programming-guides/proto3/>（"Updating A Message Type"）

- "Adding new fields is safe."；旧读者忽略未知字段；"Proto3 messages preserve unknown fields"。
- "Field numbers should never be reused."；删除字段要 `reserved`。

**映射/拒绝**：字段号复用 ≈ 表名复用。本项目 `rowCounts` 以表名为键，**表名一旦从规则表删除，不得在后续版本里复用同名表表达不同含义**。另外：本仓 `Json { ignoreUnknownKeys = false }`（`SmbkArchiveCodec.kt:270-273`），所以"同 formatVersion 内加字段"对旧读者是硬失败；这与 protobuf 的"未知字段保留"相反。采纳结论：**任何 manifest 新字段都必须 bump `formatVersion`**，不得在 v3 内追加。

### 2.2 Avro：schema resolution 与 default

来源：<https://avro.apache.org/docs/1.12.0/specification/>（官方规范，Schema Resolution 节）

实读要点：

- 读侧字段在写侧不存在时："the reader should use the default value from its field"；无 default 则 "an error is signalled"。
- 写侧字段在读侧不存在时："the writer's value for that field is ignored"。
- "files or systems that store Avro data should always include the writer's schema"。
- aliases 是可选机制（"An implementation may optionally use aliases"）。

**映射**：

- "数据自带 writer schema" ≈ 包自带 `closureVersion` + `closureDigest`。没有它，读侧无法判断该用哪张规则表。
- "reader-only 字段必须有 default" ≈ **只有规则表显式声明 `default=0` 的新增表**才允许补空；不是"凡是缺表都补 0"。
- "writer-only 字段被忽略" ≈ 被删表。**这里我们比 Avro 更严**：Avro 可以安全忽略，是因为被忽略的字段对读者无意义；SQLite 里被忽略的表可能带着用户数据。所以判据是"验空后才允许丢弃"，非空即拒（§7 D3）。

**采纳**：resolution + 显式 default；**拒绝**照搬"writer-only 静默忽略"。

### 2.3 SQLite Backup API / `user_version` / Room migrations

来源：

- <https://www.sqlite.org/backup.html>（官方）
- <https://www.sqlite.org/pragma.html#pragma_user_version>（官方）
- <https://developer.android.com/training/data-storage/room/migrating-db-versions>（官方，2026-10-23 更新）

实读要点：

- Backup API 产出 "a bit-wise identical copy of the source database as it was when the copying commenced"；目标是替换目标库内容；page size 差异由 API 处理。**页面不承诺跨 schema 版本可读**——版本语义完全由应用负责。
- `PRAGMA user_version`：应用自有整数，存于 header offset 60，"SQLite does not use it itself"；`schema_version` 是 SQLite 内部计数器，不可乱写；`application_id` 是文件类型标识。
- Room：`Migration(startVersion, endVersion)`；缺迁移路径时抛 `IllegalStateException`；`fallbackToDestructiveMigration` "permanently deletes all data"；`exportSchema` 是自动迁移与 `MigrationTestHelper` 的前提。

**映射**：

- 恢复 = 整库替换，所以"旧包可恢复"= 包内旧库 + 生产迁移链能到 73。仓内正是这条路径：`inspectStagedDatabase` 用生产 builder 在副本上跑迁移，再要求 `migratedVersion == liveVersion`（`AndroidBackupRepository.kt:1138-1164`）。**采纳现状，不新造迁移机制**。
- `manifest.databaseSchemaVersion` 应当就是快照库的 `user_version`（写侧取自 `snapshotFile.readSchemaVersion()`，`AndroidBackupRepository.kt:264`）。但读侧从未把它与载荷实测对拍（§5.1 G4）。
- **明确拒绝**在恢复路径使用 `fallbackToDestructiveMigration`：它会把"迁移失败"变成"静默清空"，直接违反 §7。

### 2.4 Joplin（JEX）：容器无版本号，逐项自描述

来源：

- <https://joplinapp.org/help/apps/import_export/>（官方帮助）：JEX 是 tar，"lossless format … can be re-imported into Joplin"；另有 raw 目录格式，每项一个文件。
- GitHub 源码（官方仓库，dev 分支）：
  - `packages/lib/services/interop/InteropService_Exporter_Jex.ts`：JEX 只是把 raw 导出目录 `tarCreate` 成单文件，**没有任何容器级版本字段**。
  - `packages/lib/services/interop/InteropService_Exporter_Raw.ts`：每项序列化后按 `systemPath` 写一个文件，附 `resources/`。
  - `packages/lib/services/interop/InteropService_Importer_Raw.ts`：逐文件 `BaseItem.unserialize`，按 `item.type_` 分派；`malformedItem` → `result.warnings.push("Skipped malformed item…")` 后 `continue`；缺资源文件也只进 warnings。

**映射/采纳**：

- 采纳"逐项自描述"的思想 → 我们用 `rowCounts` 让每张表自描述行数，用 `closureVersion` 让整包自描述规则版本。
- **拒绝**"坏项跳过 + warning"：Joplin 的项彼此独立，跳过一项不破坏其余项；SQLite 整库有外键/闭包，跳过一张表就是数据丢失。恢复路径只能是"整包成功或整包拒绝"。
- JEX 无版本号能工作，是因为它导出的是**文本事实**（Markdown + 元数据），读者可以忽略不认识的键；二进制库没有这个自由度。这也是 Obsidian 与本项目不可类比的原因。

### 2.5 Obsidian：明文文件 + 配置目录，无版本协商

来源：<https://obsidian.md/help/data-storage>（官方；页面为 JS 渲染，经 GitHub 官方帮助仓 raw markdown 复核：`obsidianmd/obsidian-help`，`en/Files and folders/How Obsidian stores data.md`）

实读要点：笔记是 "Markdown-formatted plain text files"；vault 是本地文件夹；配置在 `.obsidian`；明文可被其它编辑器打开。**官方文档不提格式版本或兼容策略**。

**采纳/拒绝**：仅作对照。Obsidian 的"无版本"是文件级开放格式的红利，不是可移植到 SQLite 归档的策略。**拒绝**把"不加版本号"当成本项目的简化理由。

### 2.6 采纳矩阵

| 来源 | 采纳 | 拒绝 | 消灭的具体失败 |
|---|---|---|---|
| protobuf presence | 显式 presence；键集合精确相等；新字段必须 bump formatVersion | 用默认值补缺键 | "缺键被当成 0"→伪造/截断包通过 |
| protobuf 字段号 | 表名不复用 | — | 同名表换含义导致旧包被误读 |
| Avro resolution | 按 writer schema 解析；reader-only 字段必须有显式 default | writer-only 静默忽略 | 被删表带行时静默丢数据 |
| SQLite backup | 快照不迁移；迁移由 Room 负责 | 在 backup API 层做版本转换 | 在错误层做版本适配 |
| Room | 生产迁移链 + `MigrationTestHelper`；拒绝 destructive fallback | `fallbackToDestructiveMigration` | 迁移失败被伪装成成功（清空） |
| Joplin JEX | 逐项/逐表自描述 | skip + warning | 半恢复 |
| Obsidian | —（仅对照） | 无版本策略 | — |

---

## 3. 版本协商模型

### 3.1 不可变规则表注册表

把"当前规则"从单例常量改成"按版本取表"：

```text
ClosureRuleTable(
  version: Int,
  digestPrefix: String,          // "smbk-selection-v4" / "-v5"
  rowCountKeys: Set<String>,     // v4=91, v5=92
  trimOrderSize: Int,            // v4=83, v5=84
  allowedMasks: Set<Int>,        // 两版同为 {9,13,15}
  assetPolicy: String,           // 两版同为 "INCLUDED"
  digest(categories, assetPolicy, rowCounts): String,
  upgradeToNext: UpgradeProjection?,   // 只对"有后继"的版本非空
)
UpgradeProjection(addedTables: List<String>, droppedTables: List<String>)
```

三条纪律：

1. **v4 快照逐字冻结**，来源是 `git show 70509247:.../SmbkSelectionPlan.kt` 的表集合/顺序/前缀；不得"顺手同步"到当前 schema。冻结方式建议：在测试里钉一个 golden（v4 空库 mask=15 的 digest 常量），任何编辑都会红。
2. **不得把 v4 塞进 `SmbkSelectionPlan`**：该对象的 `init` 断言当前形状（92 表 `:571`、84 裁剪 `:581`、2 派生 `:580`），塞进去必然自相矛盾。
3. **每次 closure bump 必须同时提交上一版的快照**（本批补 v4；将来 5→6 时补 v5）。这条是"下次不再硬拒"的唯一结构性保障。

### 3.2 协商状态机

```text
读 manifest
  ├─ formatVersion > 3 → 拒（BKP-FMT-NEWER），提示升级 App
  ├─ formatVersion < 1 → 拒（BKP-FMT-OLDER）
  ├─ selection == null
  │    ├─ formatVersion < 3 → 全量语义（既有通路，逐字不变）
  │    └─ formatVersion >= 3 → 拒（BKP-SEL-ABSENT）
  └─ selection != null
       ├─ schemaVersion != 2 → 拒（BKP-SEL-SCHEMA）
       ├─ closureVersion ∉ {4,5} → 拒（BKP-CLOSURE-UNSUPPORTED）
       ├─ 取 rule = registry[closureVersion]
       ├─ mask/categories/assetPolicy 按 rule 校验
       ├─ rowCounts.keys == rule.rowCountKeys
       ├─ trimmedTableCount ∈ 0..rule.trimOrderSize
       ├─ closureDigest == rule.digest(...)        ← 用声明版本的算法
       └─ 通过 → 计算 projection（§4），进入恢复 Phase 2
```

### 3.3 接受旧 closure 的条件（逐条，缺一即拒）

| # | 条件 | 判据/来源 | 不满足时 |
|---|---|---|---|
| A1 | `formatVersion == 3` | v3 才定义 selection（`SmbkArchiveCodec.kt:147`） | BKP-FMT-* |
| A2 | `selection != null` | `:1054-1062` | BKP-SEL-ABSENT |
| A3 | `selection.schemaVersion == 2` | v4 时点亦为 2（v4 时点 `SmbkArchiveCodec.kt:144`） | BKP-SEL-SCHEMA |
| A4 | `closureVersion == 4`（或 5） | 本文件 §3.4 的窗口 | BKP-CLOSURE-UNSUPPORTED |
| A5 | `categoryMask ∈ {9,13,15}` | 两版一致 | BKP-CLOSURE-MASK |
| A6 | `categories` 与 mask 位和一致、无重复、无未知枚举 | `:1174-1188` | BKP-CLOSURE-CATEGORIES |
| A7 | `assetPolicy == "INCLUDED"` | `:1189-1194` | BKP-CLOSURE-ASSET-POLICY |
| A8 | `rowCounts.keys == rule.rowCountKeys`（**v4 比 91，不比 92**） | 本文件 §1.2 | BKP-CLOSURE-KEYSET |
| A9 | 所有计数 `>= 0` | `:1202-1204` | BKP-CLOSURE-NEGATIVE |
| A10 | `trimmedTableCount ∈ 0..rule.trimOrderSize`（v4 比 83） | v4 `:557` | BKP-CLOSURE-TRIMCOUNT |
| A11 | `closureDigest == rule.digest(...)`（v4 用 v4 前缀 + `closureVersion=4`） | `SmbkSelectionPlan.kt:666-689` 的 v4 副本 | BKP-CLOSURE-DIGEST |
| A12 | `databaseSchemaVersion <= 73` 且 Room 有到 73 的路径 | `StudyDatabase.kt:423-424` | BKP-SCHEMA-NEWER / BKP-SCHEMA-MIGRATION-FAIL |
| A13 | 声明版本与当前版本的差集，恰好等于 `rule.upgradeToNext` 声明的增/删集合 | §4 | BKP-PROJECTION-UNKNOWN-DELTA |

补充（建议新增，A14）：`manifest.databaseSchemaVersion == 载荷 user_version`。理由：清单是包自己带的，可以撒谎；当前恢复只信载荷（Room 打开后比 `migratedVersion` 与 `liveVersion`，`:1153-1163`），manifest 的 schema 声明只进诊断/回执（`:1118`），从未核对。对拍成本是一次 `readSchemaVersion()`（已有私有扩展，`:1076-1084`），消灭"回执写 73、载荷是 71"的诊断失真。

### 3.4 支持窗口与替代方案

| 声明 closure | 处理 | 理由 |
|---|---|---|
| 1–3 | 拒 `BKP-CLOSURE-UNSUPPORTED`，文案说明"包来自更旧规则版本，请用当时的 App 重新导出" | 仓内无不可变规则表；`closureDigest` 无法按当时算法重算（前缀与 `closureVersion=` 行都不同），强行用 v4/v5 规则会得到"摘要不符"的假破损 |
| 4 | 按 v4 规则校验 + 投影 | N-1；schema 72→73 迁移在册 |
| 5 | 现行规则 | — |
| ≥6 | 拒，提示升级 App | 未来规则不可猜；与 `formatVersion` 过新同一纪律 |

**替代方案（未采纳）**：

- **B. 拒绝 closure 4，只给升级文案**（批次 40 的原"待裁定"选项）。代价：违反计划 item 5；且用户手里的旧包永久不可用。不选。
- **C. 把 v4 包"改写"成 v5 再校验**（重算 v5 digest）。问题：v5 digest 没有任何可对照的真值，等于把"校验"降级成"自洽"；且会掩盖被删表的行。不选。
- **D. 支持 closure 1–4**（从 git 逐个恢复规则表）。代价：每个版本都要冻结表/顺序/前缀 + 测试矩阵；收益仅覆盖极旧包。可作为后续扩展，不在本批。

---

## 4. 缺表填充（projection）

### 4.1 允许与禁止

| 情形 | 允许补 0？ | 依据 |
|---|---|---|
| 表在 `rule.upgradeToNext.addedTables` 中（v4→v5 的两个 `error_cause_*`） | **允许**，且必须 | Room 迁移只建空表（`ErrorCauseMigration_72_73.kt:17-19`）；Avro 的"reader default"类比 |
| 表在声明版本的 `rowCountKeys` 中，但载荷里不存在 | **禁止** | 说明包被截断或伪造；`?: 0L` 会把它洗成合法 |
| 表在声明版本里不存在、也不在 `addedTables` 中（规则表漂移） | **禁止** | 无法解释它为什么出现/消失 → `BKP-PROJECTION-UNKNOWN-DELTA` |
| 表在 `droppedTables` 中 | 不补；要求声明 0 且实测 0/缺失 | §7 D3 |

### 4.2 v4→v5 的具体投影表

| 表 | 方向 | 声明侧（v4 包） | 载荷（schema 72） | 迁移后（schema 73） | 验收 |
|---|---|---|---|---|---|
| `error_cause_observation` | 新增 | 不存在（隐式 0） | 表不存在 | 由迁移建空表 | 迁移后 `COUNT(*)==0` |
| `error_cause_binding` | 新增 | 不存在（隐式 0） | 表不存在 | 由迁移建空表 | 迁移后 `COUNT(*)==0` |
| `knowledge_search_material_feature` | 删除 | 必须为 0（v4 恒裁派生表） | 必须 0 或不存在 | 当前迁移**不删**（§1.4 残留） | 声明 0 且实测 0；非 0 即拒 |
| 其余 90 表 | 共享 | 逐表计数 | 必须逐表相等 | 必须逐表不变 | R1 + R3 |

注意：`error_cause_*` 属 MASTERY 类别（`SmbkSelectionPlan.kt:164-166`）。mask=9/13 的包不含 MASTERY，因此这两张新增表同时受"未勾选类别必须 0 行"约束（`AndroidBackupRepository.kt:1284-1287`）——两条判据在这里重叠，不冲突。

### 4.3 投影后的形状

```text
normalizedRowCounts = (declared.rowCounts − droppedTables) ∪ { t -> 0 | t ∈ addedTables }
```

- 只用于**恢复侧对账**，不回写 manifest，不重算并替换 `closureDigest`。
- 必须保留 provenance（`declaredClosureVersion`），否则 UI/证据无法说明"这是按旧规则校验的包"。受 `core/domain` 热区限制，本批可只在 `core/data` 内部与日志/证据里保留（§8 P3）。

---

## 5. 行数对账

### 5.1 现状与缺口（实读）

现状 `verifyDeclaredSelectionAgainstStagedDatabase`（`AndroidBackupRepository.kt:1234-1310`）：

- 以**当前** `SmbkSelectionPlan.allTables`（92）为循环基准（`:1263-1278`）；
- 缺表按 0（`:1267-1277`：`if (table in existing) … else 0L`）；
- 声明侧缺键按 0（`:1280`：`declaredRowCounts[table] ?: 0L`）；
- 未勾选类别非空即拒（`:1284-1287,1304-1309`）；
- 不匹配即拒（`:1297-1303`）。

| # | 缺口 | 具体失败 | 位置 |
|---|---|---|---|
| G1 | `?: 0L` 把"声明缺键"洗成 0 | 一旦键集合按版本放宽，截断包/伪造包可少声明一张共享表而通过（若实测恰为 0） | `:1280` |
| G2 | 只遍历当前 92 表 | v4 包的 `knowledge_search_material_feature` 从不被检查 → 带行也会被静默丢弃 | `:1263` |
| G3 | 无迁移后复算；且被提升的是**未迁移**文件 | 迁移在 `validationCopy` 上跑完即删（`:1140-1178`），Phase 3 复制的是 `stagingDb`（`:616`）→ "校验过的字节 ≠ 换上去的字节"；迁移若在下次开库时丢行，恢复阶段看不到 | `:616,1140-1178` |
| G4 | manifest 的 `databaseSchemaVersion` 从不与载荷 `user_version` 对拍 | 回执/诊断可显示错误 schema | `:1118` vs `:1153-1163` |
| G5 | 行数对账在**迁移前**文件上做 | 新增表"迁移后为 0"没有运行时证据，只有迁移测试 | `:551` |

### 5.2 三层对账（建议）

**R1 声明 vs 载荷（按声明版本键空间）**

```text
for table in rule.rowCountKeys:
    declared = declaredRowCounts.getValue(table)     // 禁止 ?: 0
    actual   = if (table in existing) COUNT(*) else 0
    require(declared == actual)
```

对 v4 包，`rule.rowCountKeys` 是 91 键；新增两表不在此循环，由 R2 处理。

**R2 增量表**

```text
for t in projection.addedTables:   require(actual(t) == 0)          // 迁移前：表不存在=0
for t in projection.droppedTables: require(declared(t) == 0 && actual(t) == 0)
```

**R3 迁移后复算（推荐与 G3 一起做）**

在 `validationCopy`（已跑完生产迁移的那份）上复算：

```text
for t in sharedTables: require(post(t) == pre(t))     // 迁移不得增删共享表行
for t in addedTables:  require(post(t) == 0)
require(sum(post over current keys) == sum(pre over shared keys))
```

代价与取舍：R3 要求 `validationCopy` 活到 Phase 2c.1 之后（当前在 `finally` 删除）。峰值磁盘多一份库（设备实测 365 MB 级，`SmbkArchiveCodec.kt:181-195` 的预算注释），`requiredSpace`（`:506-507`）需要把这份算进去。若不做 R3，则必须在迁移测试里钉死"72→73 行保留"（已有 `FullMigrationMatrixInstrumentedTest.kt:2040-2082`），并如实登记"运行时未复算"。

**G3 的更强修法**：直接把 `validationCopy`（迁移后）作为 `nextDb` 的来源，而不是 `stagingDb`。收益：换上去的字节就是校验过的字节；下次开库不再跑迁移。代价：要处理 `-wal/-shm`（`wal_checkpoint(TRUNCATE)` 或一并复制）与回滚代际语义，属于恢复核心路径改动，建议单独立项评审，不在 item 5 里顺手做。

---

## 6. 失败码

现状：`BackupValidation.Invalid(reason: String, manifest: …)`（`core/domain/.../BackupRepository.kt:139-142`），`BackupRestoreException(message, cause)`（`AndroidBackupRepository.kt:1623`）。**没有任何稳定码**；`unpack` 的拒因原样变成异常文案（`:483-487`）。

建议：在 `core/data/.../backup/` 内新增 `internal enum class BackupFailureCode`，测试与证据引用码；用户文案保留现有中文（可加 `[码]` 后缀，是否展示由产品定）。`core/domain` 是热区，本批不改 `BackupValidation` 的形状。

| 码 | 触发条件 | 现位置 | 用户动作 |
|---|---|---|---|
| `BKP-FMT-NEWER` | `formatVersion > 3` | `SmbkArchiveCodec.kt:1039-1041` | 升级 App |
| `BKP-FMT-OLDER` | `formatVersion < 1` | `:1042-1044` | 无（不支持） |
| `BKP-SEL-ABSENT` | v3 但无 selection | `:1056-1061` | 包损坏/被剥离 |
| `BKP-SEL-SCHEMA` | `selection.schemaVersion != 2` | `:1156-1160` | 升级 App 或包过新 |
| `BKP-CLOSURE-UNSUPPORTED` | closure ∉ {4,5}（含 ≥6 与 ≤3） | `:1162-1167` | 升级 App / 用旧版重导出 |
| `BKP-CLOSURE-MASK` | mask ∉ {9,13,15} | `:1168-1173` | 包非法 |
| `BKP-CLOSURE-CATEGORIES` | 类别与 mask 不一致/重复/未知 | `:1174-1188` | 包非法 |
| `BKP-CLOSURE-ASSET-POLICY` | `assetPolicy != INCLUDED` | `:1189-1194` | 升级 App |
| `BKP-CLOSURE-KEYSET` | `rowCounts.keys != rule.rowCountKeys` | `:1195-1201` | 包损坏/伪造 |
| `BKP-CLOSURE-NEGATIVE` | 任一计数 < 0 | `:1202-1204` | 包非法 |
| `BKP-CLOSURE-TRIMCOUNT` | `trimmedTableCount` 越界 | `:1205-1210` | 包非法 |
| `BKP-CLOSURE-DIGEST` | 按声明版本重算不一致 | `:1211-1227` | 包被改过 |
| `BKP-PROJECTION-UNKNOWN-DELTA` | 声明版本与当前的差集 ≠ 规则表声明 | 新增（§4） | 内部规则漂移，报缺陷 |
| `BKP-PROJECTION-DROPPED-NONEMPTY` | 被删表声明/实测非 0 | 新增（§5 R2） | 包带旧数据，拒 |
| `BKP-ROWCOUNT-MISMATCH` | 共享表声明 ≠ 实测 | `AndroidBackupRepository.kt:1297-1303` | 包损坏/伪造 |
| `BKP-UNSELECTED-NONEMPTY` | 未勾选类别仍有行 | `:1304-1309` | 包非法 |
| `BKP-ROWCOUNT-DB-UNREADABLE` | 载荷库打不开/读计数失败 | `:1291-1295` | 包损坏 |
| `BKP-SCHEMA-CLAIM-MISMATCH` | manifest schema ≠ 载荷 `user_version` | 新增（A14，`:1076-1084`） | 包被改过/打包错 |
| `BKP-SCHEMA-NEWER` | 载荷 `user_version > 73` | 新增（Room 前显式预检） | 升级 App |
| `BKP-SCHEMA-MIGRATION-FAIL` | Room 迁移抛错/无路径 | `:1166-1171` | 包过旧或迁移缺陷 |
| `BKP-SCHEMA-DRIFT` | 迁移后版本 ≠ 实时版本 | `:1159-1163` | 包不兼容 |

注：`BKP-SCHEMA-NEWER` 现在只会以 `BKP-SCHEMA-MIGRATION-FAIL` 的形式出现（Room 打开即抛），文案是"无法通过 Room 迁移校验"——用户看不出是"包太新"还是"包坏了"。加一次 `readSchemaVersion()` 预检即可分开。

---

## 7. 不得静默丢行的判据（D1–D8）

| # | 判据 | 可测形式 | 违反即 |
|---|---|---|---|
| D1 | 声明版本的每个表键都必须**显式存在**且等于载荷实测；不允许 `?: 0` 兜底 | JVM：构造缺键的 v4 包 → 拒；instrumented：改一行计数 → 交换前拒、原库不动 | `BKP-CLOSURE-KEYSET` / `BKP-ROWCOUNT-MISMATCH` |
| D2 | 新增表在迁移前后都必须为 0；迁移若回填任何行即拒 | instrumented：v72 库 + 伪造"迁移回填"不可行，改为断言迁移后两表 `COUNT==0`（已有 `:2072-2073` 先例） | `BKP-ROWCOUNT-ADDED-NONEMPTY` |
| D3 | 被删表：声明必须 0，实测必须 0/缺失；**非 0 时拒绝整包，不丢弃该表** | instrumented：在 v72 库里给 `knowledge_search_material_feature` 写行 → 恢复拒 | `BKP-PROJECTION-DROPPED-NONEMPTY` |
| D4 | 共享表迁移前后行数不变（若实现 R3）；未实现时以迁移测试 + 代码审查（迁移只含 DDL）作为替代证据 | 迁移测试（`FullMigrationMatrixInstrumentedTest.kt:2040-2082`）+ R3 | `BKP-SCHEMA-DRIFT` |
| D5 | 整包原子：任一 D1–D4 失败都发生在交换前；不存在"恢复一半" | 既有 Phase 2b/2c/2c.1 都在 Phase 3 之前（`:537-575` vs `:582+`） | — |
| D6 | 恢复路径**不得**逐表 catch-and-continue（不同于 Joplin 的 skip+warn） | 代码审查 + 负向用例：单表失败必须整包失败 | — |
| D7 | 总行数守恒：声明总和 = 实测总和（声明键空间）；共享表迁移前后总和相等 | R1/R3 的 sum 断言 | `BKP-ROWCOUNT-MISMATCH` |
| D8 | `trimmedTableCount` 只是诊断字段（不在 digest 覆盖范围内，`SmbkSelectionPlan.kt:666-689` 只覆盖 categories/assetPolicy/rowCounts），**不得**用它作为"删了多少行"的接受判据 | 代码审查；若要让它可判，下一次 closure bump 把它纳入 digest | — |

补充：资产闭合（AUD-05）与 closure 版本正交——`referencedAssets` 的逐条存在性/摘要校验（`SmbkArchiveCodec.kt:1096-1104`）与 DB→资产反查（`AndroidBackupRepository.kt:1192-1210`）不因接受 v4 包而放宽。

---

## 8. 建议改动（patch 草案，本会话未改任何代码）

> 全部落在 `core/data`（非热区）。`core/domain` 不动；`SmbkSelectionPlan` 的当前规则不动。

**P1 `SmbkArchiveCodec.kt` — 规则表注册表 + 按声明版本校验**

- 新增 `internal object ClosureRuleTables`：`v4`（逐字冻结自 `70509247`：91 键、83 裁剪、`smbk-selection-v4`、mask {9,13,15}、`INCLUDED`、`UpgradeProjection(added=[error_cause_binding, error_cause_observation], dropped=[knowledge_search_material_feature])`）与 `v5`（引用当前 `SmbkSelectionPlan`）。
- `readableSelection(record, formatVersion)` 改为先 `val rule = ClosureRuleTables[record.closureVersion] ?: return Invalid(BKP-CLOSURE-UNSUPPORTED)`，后续所有检查用 `rule`。
- digest 用 `rule.digest(...)`；v4 分支不得调用当前 `SmbkSelectionPlan.closureDigest`（后者硬编码 v5 前缀与 `closureVersion=5`，`SmbkSelectionPlan.kt:666-689`）。
- 键集合/裁剪上限用 `rule.rowCountKeys` / `rule.trimOrderSize`。
- 通过后计算 `SmbkSelectionProjection(declaredClosureVersion, addedTables, droppedTables, normalizedRowCounts)`，并让 `unpack` 的调用方能拿到它。
  - 最省事的接缝：新增 `internal fun unpackDetailed(...): Pair<BackupValidation, SmbkSelectionProjection?>`，`unpack` 委托它并丢弃投影；恢复仓储改用 `unpackDetailed`。这样不改 `BackupValidation`（`core/domain` 热区）。
- 写侧 `requireWritableSelection` **保持不变**（只写 5）：旧版本只读不写。

**P2 `AndroidBackupRepository.kt` — 版本感知对账**

- `verifyDeclaredSelectionAgainstStagedDatabase(staged, projection)`：
  - R1 用 `projection.declaredRowCounts.getValue(table)`（删掉 `?: 0L`）；
  - R2 检查 `addedTables` 实测 0、`droppedTables` 声明/实测 0；
  - 未勾选类别检查保持现状（基于当前 92 表，已覆盖新增表）。
- Phase 2c.1 之前加 A14：`stagingDb.readSchemaVersion()` vs `valid.manifest.databaseSchemaVersion`，不等即 `BKP-SCHEMA-CLAIM-MISMATCH`；`readSchemaVersion()` 返回 `-1` 时按 `BKP-ROWCOUNT-DB-UNREADABLE` 处理。
- （可选，单独立项）把 `validationCopy` 作为提升源，消掉 G3。

**P3 诊断/provenance**

- 在 `core/data` 内把 `declaredClosureVersion` 写进恢复日志/证据；不改 `BackupManifestSummary`（`core/domain`）。
- 若产品要求 UI 显示"此包为旧规则版本，已按空表补齐错因表"，需要 `core/domain` 加字段（`BackupManifestSummary`，`:145-172`），须热区清空后单独立项。

**P4 测试**（先红后绿，见 §9）。

**P5 守卫**

- `tools/ci/check_backup_plan_sync.py`（计划 item 8）追加：v4 规则表快照的 golden digest 与 git 历史一致；v4 键集合 = 91、v5 = 92；两版差集 = 声明的 `UpgradeProjection`。这条消灭"有人顺手把 v4 快照同步成新 schema"的漂移。

---

## 9. 测试矩阵（先红后绿）

> 现状：`SmbkArchiveCodecTest` 的 v3 读侧拒绝表已覆盖 closure/schema/mask/categories/assetPolicy/rowCounts/trim/digest/format（`SmbkArchiveCodecTest.kt:864-939`），夹具 `emptyRowCounts()` 直接用当前 `rowCountKeys`（`:1395-1396`）——**所有夹具都是 92 键**，没有 v4 夹具。这是"先红"的起点。

**JVM（`core/data/src/test/.../SmbkArchiveCodecTest.kt`）**

| # | 用例 | 期望 |
|---|---|---|
| T1 | v4 包（91 键、v4 digest、closure=4）validate | `Valid`；投影后 92 键、两新增表 0（**今天红：closure 拒**） |
| T2 | v4 包但 digest 用 v5 算法 | `BKP-CLOSURE-DIGEST` |
| T3 | v4 包但 rowCounts 写成 92 键 | `BKP-CLOSURE-KEYSET` |
| T4 | closure=3 | `BKP-CLOSURE-UNSUPPORTED` |
| T5 | closure=6 | `BKP-CLOSURE-UNSUPPORTED` |
| T6 | v4 包、被删表声明 1 | `BKP-PROJECTION-DROPPED-NONEMPTY`（在 codec 侧先拒声明值） |
| T7 | v4 包、`trimmedTableCount=84` | `BKP-CLOSURE-TRIMCOUNT`（v4 上限 83） |
| T8 | v1/v2 无 selection | `Valid`（回归，通路不变） |
| T9 | formatVersion=4 | `BKP-FMT-NEWER`（回归） |

**Instrumented（`core/data/src/androidTest/.../backup/`）**

| # | 用例 | 期望 |
|---|---|---|
| T10 | 用 `createDatabaseFromExportedSchema(..., 72)` 造 v72 库 + 一行 `attempt_submission`，按 v4 selection 打包并恢复 | 成功；旧行在；`error_cause_*` 存在且 0；无丢行 |
| T11 | v4 包、某共享表声明 +1 | 交换前拒；原库字节不变 |
| T12 | v4 包、`knowledge_search_material_feature` 有 1 行 | 交换前拒（D3） |
| T13 | manifest 声明 schema 73、载荷 72 | `BKP-SCHEMA-CLAIM-MISMATCH` |
| T14 | 72→73 迁移行保留（已有，`FullMigrationMatrixInstrumentedTest.kt:2040-2082`） | 复用为 D4 证据 |

**未纳入**：closure 1–3 的恢复（明确不支持）；`formatVersion` 4 的读侧（未来包）。

---

## 10. 未验证、风险与待裁定

**UNVERIFIED（本会话未执行）**

- 任何 Gradle 编译/单测/强门；任何设备或模拟器用例；任何真实 v4 包的构造与恢复。§9 的期望值全部来自源码阅读，不是运行结果。
- `72.json` 在 `MigrationTestHelper`/`createDatabaseFromExportedSchema` 下能否稳定造出可打包的 v72 库（用例存在，但本会话未跑）。
- 恢复路径把 `validationCopy` 作为提升源的实际磁盘峰值与 WAL 行为（§5.2 G3）。

**风险**

1. **closure 4 可能从未面向真实用户**：批次 40 记录称 71 是真实用户升级主路径、`72.json` 为 orphan（`2026-11-02-batch-40-record.md` §8 #2；`ErrorCauseMigration_72_73.kt:11-15`）。若产品确认 72 未发布，本兼容读侧的收益是"内部包/演练包可恢复"，成本仍是一张冻结规则表。**这是产品事实，本文件无法证明**。
2. **被删表残留**：`72→73` 迁移不 DROP `knowledge_search_material_feature`（§1.4）。接受 v4 包后，库会长期多一张 73 规则外的表。需要裁定：在迁移里补 `DROP TABLE IF EXISTS`（改 `core/database`，非热区，但改的是已登记迁移）或在恢复侧清理。未裁定前，D3 只保证"它没有行"，不保证"它不存在"。
3. **冻结规则表的维护成本**：每多支持一个旧版本就多一份表/顺序/前缀与测试。建议把"bump 时冻结上一版"写进流程，否则下次仍会硬拒。
4. **`ignoreUnknownKeys = false`**（`SmbkArchiveCodec.kt:270-273`）：任何在 v3 内追加 manifest 字段的改动都会让旧 App 报"manifest 解析失败"。必须坚持"新字段 = bump formatVersion"。
5. **provenance 无处安放**：`core/domain` 热区使 `BackupManifestSummary` 无法加 `declaredClosureVersion`；本批只能在日志/证据里保留。

**待裁定**

- R2-Q1：支持窗口是否就定 N-1（{4,5}）？若要支持 3，需要从 git 恢复 v3 规则表并补 schema 70→71→73 的设备矩阵。
- R2-Q2：`72→73` 是否补 `DROP TABLE knowledge_search_material_feature`？
- R2-Q3：是否把"提升迁移后的副本"（G3）纳入本批，还是单独立项？
- R2-Q4：失败码是否允许出现在用户文案里（`[BKP-…]` 后缀），还是只进日志/证据？

---

## 11. 来源

**仓内（实读）**

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkArchiveCodec.kt`：`:92-102`（格式常量）、`:270-273`（JSON 配置）、`:444-447`（写侧 closure）、`:1039-1068`（格式/selection 闸门）、`:1096-1104`（资产闭合）、`:1130`（rowCounts 透传）、`:1156-1227`（读侧 selection）、`:1234-1310`（行数对账）。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt`：`:54,64,70,73,76-85`（版本/前缀/mask）、`:164-166`（新增错因表）、`:193-197`（派生表）、`:213-224`（92 表/84 裁剪）、`:570-593`（形状断言）、`:666-689`（digest）。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt`：`:200-274`（成包/裁剪/rowCounts 来源）、`:479-487`（unpack 拒因→异常）、`:537-575`（Phase 2b–2c.3）、`:616`（提升 stagingDb）、`:1076-1084`（readSchemaVersion）、`:1110-1180`（Room 迁移预验证）、`:1234-1310`（声明 vs 载荷）。
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt`：`:24-28`（92 键口径）、`:500`（派生表恒裁）。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/ErrorCauseMigration_72_73.kt`：`:11-19,34-57`。
- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt`：`:142`（version 73）、`:423-424`（迁移注册）。
- `core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt`：`:2032-2082`（v72→73 行保留/新表空）。
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/BackupRepository.kt`：`:139-142`（Invalid 只有 reason）、`:145-172`（ManifestSummary）。
- `core/data/src/test/.../SmbkArchiveCodecTest.kt`：`:864-939`（v3 读侧拒绝表）、`:1395-1396`（当前 92 键夹具）。
- `git show 70509247:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt`（closure 4 / schema 72 / 91 表 / 83 裁剪 / prefix v4）。
- `python` 实读 `core/database/schemas/.../72.json`、`73.json`：91/92 entities、identityHash `aa5e8e8e6536…`/`1d086050204a…`、`knowledge_search_material_feature` 在 72 不在 73、`error_cause_*` 在 73 不在 72。
- 批次 40 记录 `docs/research/2026-11-02-batch-40-record.md` §8 #16、§9.1-9；批次 41 交叉复核 `docs/research/evidence/2026-11-03-batch-41/research-crosscheck.md` C5 与 R2 三问。

**外部（官方优先；单一来源标注）**

| 来源 | URL | 状态 | 采纳/拒绝 |
|---|---|---|---|
| Protobuf Field Presence | <https://protobuf.dev/programming-guides/field_presence/> | 官方，单一来源 | 采纳：显式 presence、键集合精确 |
| Protobuf proto3 "Updating A Message Type" | <https://protobuf.dev/programming-guides/proto3/> | 官方，单一来源 | 采纳：新增字段安全、字段号不复用；推导出"新字段必须 bump formatVersion" |
| Avro 1.12 Specification（Schema Resolution） | <https://avro.apache.org/docs/1.12.0/specification/> | 官方，单一来源 | 采纳：reader default / writer-only 忽略；**拒绝**静默忽略被删表 |
| SQLite Online Backup API | <https://www.sqlite.org/backup.html> | 官方，单一来源 | 采纳：快照 bit-wise、不跨版本；迁移交给 Room |
| SQLite PRAGMA（user_version/schema_version/application_id） | <https://www.sqlite.org/pragma.html#pragma_user_version> | 官方，单一来源 | 采纳：user_version 是应用自有版本位 |
| Android Room 迁移 | <https://developer.android.com/training/data-storage/room/migrating-db-versions> | 官方，2026-10-23 更新 | 采纳：Migration/缺路径抛错/exportSchema；**拒绝** destructive fallback |
| Joplin 导入导出（JEX=tar，lossless） | <https://joplinapp.org/help/apps/import_export/> | 官方，单一来源 | 采纳"自描述"思想 |
| Joplin JEX 导出实现 | <https://github.com/laurent22/joplin/blob/dev/packages/lib/services/interop/InteropService_Exporter_Jex.ts> | 官方仓库源码（dev） | 实读：容器无版本字段 |
| Joplin raw 导入实现（skip+warn） | <https://github.com/laurent22/joplin/blob/dev/packages/lib/services/interop/InteropService_Importer_Raw.ts> | 官方仓库源码（dev） | **拒绝** skip+warn |
| Obsidian 数据存储 | <https://obsidian.md/help/data-storage> | 官方；页面 JS 渲染，经 `obsidianmd/obsidian-help` raw markdown 复核 | 仅对照，不采纳"无版本" |

> 外部来源均为各自项目的官方文档/官方仓库，但每项在本次检索中只取到一个官方页面（未做跨引擎独立复核），按纪律标"单一来源"；它们之间无相互矛盾，且与仓内实读不冲突。
