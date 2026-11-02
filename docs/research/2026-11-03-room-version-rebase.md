# R3：Room 版本重基／孤儿 schema JSON 的工程实践（批次 41）

> 范围：`@Database(version)` 被回滚/重基后，同号 schema JSON 的处置、Room 3.0.0 + KSP 的真实行为、
> `KernelWave0` 那类 `version == max(schema)` 契约的正确写法、同号冲突的工程约定。
> 输入：批次 40 记录 §8/§9（`docs/research/2026-11-02-batch-40-record.md:281-340`）。
> 立场：本文件只做研究与 patch 草案，不改任何源码（R3 是研究项）。

---

## 0. 结论摘要

| 问题 | 结论（一句话） | 依据 |
|---|---|---|
| 版本号能否复用 | **不能**，只要该号曾落到任何设备上。Room 在每次 open 时先比 `room_master_table.identity_hash`，不符即抛 `IllegalStateException`，不会退化为结构校验。Room 3 编译器对同号文件**静默覆盖**，不报错，所以"能不能复用"不受编译期保护，只受运行期惩罚。 | runtime 字节码 `checkIdentity`；compiler 字节码 `Database.exportSchema` |
| 孤儿 JSON 是否必须删除 | 构建**不要求**（非当前版本的文件编译器根本不读）；契约/测试**要求**处置：要么删，要么显式登记为 `SOURCE_ONLY` 并保证其出边收敛。本仓 72.json **不应直接删**——它对应过 mainline 上真实提交过的 72 态，删了就再也没有可重建的 72 快照。 | §2 提交史；`SmbkSelectionPlan`/L2 预注册文档 |
| `version == max(schema)` 怎么才不误报 | 换成五条库内不变式（文件名=内容版本、无未来快照、无重复版本、当前版本唯一、每个旧版本可达或有白名单理由）+ 一条跨模块 hash 绑定，**不要**要求 1..N 连续。 | §4-Q3 契约草案 |
| 同号冲突的工程约定 | 版本号 = 一次性占用、append-only；占用必须原子（常量+JSON+迁移+plan hash 同一提交）；合并时后到者重编号；CI 必须有"JSON 变了但版本号没变 → 红"的守卫。 | §4-Q4 |

**本批最需要立刻处理的一条（不是理论风险）**：批次 40 的 10:13 设备包是 `73 / 93 表 / identityHash aa9f0313…`，
当前树是 `73 / 92 表 / 1d0860…`。**同一个版本号，两套 schema。** 若该设备上存在
`smart-mistake-book.db`（应用被真正启动过），当前构建 `install -r` 之后打开即抛
`Room cannot verify the data integrity…`。详见 §6.1。

---

## 1. 方法与证据来源（本文每条断言都可复核）

### 1.1 本轮实读/实跑（非转录）

| 动作 | 命令/路径 | 用途 |
|---|---|---|
| 读 schema 头与表数 | `python -I -c "json.load(open('N.json'))"`（71/72/73） | version / identityHash / entities / views |
| 集合差 | 同上，对 `entities[].tableName` 求差 | 证明 72 与 73 是**分叉**不是祖先 |
| 读历史版本 | `git show <c>:.../StudyDatabase.kt`、`git show <c>:.../72.json` | 证明 72 被两套不同 schema 用过 |
| 反编译编译器 | `unzip room3-compiler-3.0.0.jar` + `javap -p -c androidx/room3/vo/Database.class` | 同号文件覆盖行为 |
| 反编译运行时 | `unzip room3-runtime.aar → classes.jar` + `javap -p -c androidx/room3/BaseRoomConnectionManager.class` | identity 校验 / 迁移后校验 / 更新 hash |
| 读生成物 | `core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt` | 本仓真实 `RoomOpenDelegate(73, …)` 与 `onValidateSchema` |

> 本轮**未运行 Gradle、未上设备**。凡涉及运行时行为的结论来自 3.0.0 官方制品字节码与本仓 KSP 生成物，
> 属"代码级已核、设备未复现"，在 §8 单列。

### 1.2 官方文档（采纳）

| 来源 | 采纳内容 | URL |
|---|---|---|
| Room 迁移官方页（更新 2026-10-23） | ① 导出 JSON 是"schema history"，必须进 VCS，用于重建旧版本与自动化迁移；② 找不到迁移路径 → `IllegalStateException`；③ `room3-testing` 的 `MigrationTestHelper(createDatabase(n), runMigrationsAndValidate(n, migrations))` 用法 | https://developer.android.com/training/data-storage/room/migrating-db-versions |
| Room 2.x → 3.0 迁移指南（更新 2026-10-23） | Room 3 = 新包 `androidx.room3`、**KSP only**、迁移函数 `suspend migrate(SQLiteConnection)`、`SQLiteDriver` 取代 SupportSQLite | https://developer.android.com/training/data-storage/room/migration-2-to-3 |

### 1.3 社区来源（仅旁证，不作规范）

- `https://stackoverflow.com/questions/44197309/room-cannot-verify-the-data-integrity`：同一异常的常见成因与
  "删 `room_master_table` / 换初始库"式偏方。**拒绝采纳其处置方式**——删主表或 `fallbackToDestructiveMigration`
  会丢用户数据，只可作为开发机应急，不能进生产路径。异常原文以 §3.2 的运行时字节码为准。

---

## 2. 本仓库的当前事实（全部实读）

### 2.1 三个导出物

`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/`：

| 文件 | version | entities | identityHash | 与 71 的差集 |
|---|---|---|---|---|
| `71.json` | 71 | 90 | `714c0ecb85ee5edf74f0013740148138` | — |
| `72.json` | 72 | **91** | `aa5e8e8e6536e0c1f3adcda6867814cb` | **+** `knowledge_search_material_feature` |
| `73.json` | 73 | **92** | `1d086050204a0da1ed6b6e3299dd9733` | **+** `error_cause_binding`、`error_cause_observation` |

- `73 - 72 = {error_cause_*}`，`72 - 73 = {knowledge_search_material_feature}` ⇒ **72 与 73 是分叉**，
  73 不是"72 再加两表"。`StudyDatabase` 版本常量同步为 73：`StudyDatabase.kt:142`。
- 迁移图：`70→71`（`VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71`）、`71→73`（`MIGRATION_71_73`）、
  `72→73`（`ERROR_CAUSE_MIGRATION_72_73`），注册于 `StudyDatabase.kt:349-424`。
  **72 没有入边**（71→72 已随 L2 回滚删除），但有出边——所以它是"源孤岛"，不是"无迁移链"。
- 两处 KDoc 互相矛盾：`ErrorCauseMigration_72_73.kt:12-15` 说 72.json"不删不改"；
  而 L2 预注册文档 §4.3 第 13 项（`docs/research/2026-11-02-kb-l2-material-features-prereg.md:368`）
  明写 `72.json` 是"L1 孤儿……回滚时删除，不恢复"。**两者只能留一个**，见 §4。

### 2.2 版本号 72 已经被用过两套 schema（关键）

`git show <commit>:.../72.json` 实读：

| 提交 | 日期 | 常量 | 72.json 内容 | identityHash |
|---|---|---|---|---|
| `e33969d2`（"feat(export)…T10"） | 2026-11-01 | **72** | 90 表，+`detailed_solution`/`detailed_solution_manifest`/`mistake_edit_job`/`mistake_export_group`，−四张视觉表 | `cd7a792ae31a46e939651e0e2bfd986c` |
| `70509247`（"fix(backup): schema 72 备份清单同步…72.json 随附入库"） | 2026-11-02 05:15 | 71（常量）/ plan=72 | 91 表，+L2 | `aa5e8e8e…` |
| `HEAD` | — | 73 | 91 表，+L2 | `aa5e8e8e…` |

- `e33969d2` **是 HEAD 的祖先**（`git merge-base --is-ancestor` = 0）。
- 同一路径 `72.json` 在 `e33969d2` 与 `70509247` 之间**被原地覆盖**，版本号没变。
  这正是 §3.1 所述的编译器行为在真实仓库里发生过的实例。
- 批次 39 证据还记录过第三态：`docs/research/evidence/2026-11-01-batch-39/step09-tail-rebuild-install-gates.md:125-130`
  （91 表 / `aa5e8e8e` / 与集成轨转述的 94 表 / `3570ca22…` 不一致）——说明工作树里的 72.json
  在两天内被本地构建反复重写。

**结论**：本仓的"同号冲突"已经不是假想，而是已经发生≥2 次、并且**编译器一次都没拦**。

### 2.3 设备侧已存在的同号异形库

`docs/research/evidence/2026-11-02-batch-40/step01-rebuild-install-gates.md:76` 实读：
10:13 设备包 `database.version=73`、`identityHash=aa9f0313fcc417505471e3ab15ae0932`、`entities=93`。

> 93 = 71 的 90 + L2 + 错因两表。也就是说 **version 73 也被两套 schema 用过**（93 表 / 92 表）。
> 设备是否真的创建过生产库 `smart-mistake-book.db`，本轮**无法核实**（step01 只记录 `install -r -t`，
> 未记录启动；KD-76 被 BLOCKED 未跑）。见 §8。

---

## 3. Room 3.0.0 的实际行为（反编译一手结论）

### 3.1 编译期：同号 schema 文件内容不同 → **静默覆盖**

`androidx/room3/vo/Database.class#exportSchema(Path inputPath, Path outputPath)` 的字节码路径：

1. 用当前实体构造 `SchemaBundle`.
2. `SchemaFileResolver.readPath(inputPath)` 读已存在的文件；`IOException` → 当作不存在。
3. 若读到了，`SchemaBundle.isSchemaEqual(new)` → **相等就直接 return**（不重写，保持 mtime 稳定）。
4. 否则 `writePath(outputPath)` + `serialize(...)` —— **没有任何 `Context.getLogger` 调用、没有异常**。

调用点 `DatabaseProcessingStep`：`Path.of(dir, subdir, file)` 两次，**input 与 output 是同一个路径**。
因此"同一版本号、不同内容"既不会报错也不会警告，只会把旧文件覆盖掉。

- 与官方文档对照：文档只说"导出 JSON 代表 schema 历史、要进 VCS"，**没有**承诺编译器会拦截同号改写。
- 影响：任何"版本号占用"的守卫都必须建在 git/CI/测试层，不能指望 KSP。

### 3.2 运行期 open：identity hash 先于结构校验，且**硬失败**

`androidx/room3/BaseRoomConnectionManager`：

- `onOpen` 无条件调用 `checkIdentity(connection)`。
- `checkIdentity`：读 `SELECT identity_hash FROM room_master_table WHERE id = 42`；
  - 等于 `RoomOpenDelegate.identityHash` 或 `legacyIdentityHash` → 通过；
  - 否则直接抛 `IllegalStateException`，消息字符串实读为：
    > `Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number. You can simply fix this by increasing the version number. Expected identity hash: `
  - 只有 `room_master_table` 不存在时，才走"`onValidateSchema` 通过 → `onPostMigrate` → `updateIdentity`"。

**关键点**：identity 不符时**不会**退回到 `TableInfo` 比对。所以"同号 + 不同 schema + 设备上已有该号的库"
= 每次 open 必抛，且是启动路径上的崩溃，没有降级余地。

### 3.3 迁移后：只校验"期望表"，额外表不可见，且改写 identity

`onMigrate$suspendImpl` 实读调用序列：
`findMigrationPath(from,to)` → `onPreMigrate` → 逐条 `Migration.migrate(connection)` → `onValidateSchema` →
`onPostMigrate` → `updateIdentity`。

`StudyDatabase_Impl.kt:833` 起的 `onValidateSchema` 只对**实体清单里每一张表**做
`tableInfoRead(connection, "xxx")` 与期望 `TableInfo` 比较；**从不枚举 `sqlite_master`**。
生成物里的 `dropAllTables`（`StudyDatabase_Impl.kt:717`）同样只 `DROP` 声明过的 92 表 + 2 视图——
但它只在 `allowDestructiveMigrationForAllTables == false` 时被使用；为 `true` 时基类会枚举
`sqlite_master` 全删（`BaseRoomConnectionManager#dropAllTables` 的 `SELECT name, type FROM sqlite_master` 分支）。

因此三条推论（代码级确定）：

1. 从 72/L2（91 表）迁到 73，只执行 `CREATE error_cause_*`，**`knowledge_search_material_feature` 留在库里**，
   `onValidateSchema` 看不见它，`updateIdentity` 仍把 hash 写成 `1d0860…`。
   结果：**一个 93 表、却自称 73/`1d0860…` 的库**。
2. 从 72/`cd7a792a`（90 表，已发布过的那套）迁到 73，结果恰好是 92 表——正确。
3. 同一个"73"下，两台设备可能分别是 92 表与 93 表，且 `room_master_table` 完全相同。
   **任何以 `room_master_table.identity_hash` 判等来替代表集合比对的校验都会被骗过**
   （`BackupSnapshotTrimmer.kt:285` 正是只比 version + identityHash）。

### 3.4 本仓生成物核对

```
core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt:270
  RoomOpenDelegate(73, "1d086050204a0da1ed6b6e3299dd9733", "9a7be7bbf25053497da9f8bd4ca23204")
```

- `aa9f0313…`（10:13 包）与 `cd7a792a…`（11-01 的 72）都**不等于** 73 或 legacy `9a7be7bb…` ⇒ 两条路都不会被身份校验放行。
- `SmbkSelectionPlan.IDENTITY_HASH`（`SmbkSelectionPlan.kt:64`）与 `73.json`、生成物三者同值，这一点目前是自洽的。

---

## 4. 逐问回答

### Q1 版本号能否复用？

**不能复用"曾经存在于任何一台设备上的版本号"**；能否复用只取决于"这个号有没有被写进过任何 `user_version`"。

分三种情形：

| 情形 | 是否可复用 | 理由 |
|---|---|---|
| 该号从未被任何构建写过（只在本地草稿里出现过，且没有任何 APK 落设备） | 技术上可以 | 没有 `room_master_table` 带着旧 hash 存在；但必须同一次提交里把 `N.json` 换成新内容，并留下"该号曾作废"的记录 |
| 该号曾被某台设备打开过（哪怕只有 debug 包） | **绝对不行** | `checkIdentity` 硬失败，见 §3.2。用户侧表现是"升级后一启动就崩" |
| 该号只在历史提交里出现过、从未构建落设备 | 谨慎可用 | 但 `N.json` 的旧内容只存在于 git 历史，`MigrationTestHelper` 无法同时保留两套同名快照——等于永久失去对旧态的可测性 |

本仓的判断：

- **73 已被占用且已落设备**（10:13 包，93 表 / `aa9f0313`）。所以 **73 不能再改形状**；当前 rebase 成 92 表
  等于在同一个 73 下换了内容。这不是"能否复用"的抽象问题，是**已经发生了**的同号冲突。
- **72 曾以两套不同内容出现过**（`cd7a792a` 与 `aa5e8e8e`），其中 `cd7a792a` 对应 mainline 提交 `e33969d2`。
  即便没有设备证据，也应把 72 视为已消耗。

正确做法不是"复用"，而是**递增**：任何形状变化 → `version + 1` + 新 JSON + 新迁移。这条在 Room 里没有例外，
因为 identity hash 是 schema 内容的函数，不是版本号的函数。

### Q2 孤儿 JSON 是否必须删除？

分三层回答：

1. **构建层：不必删。** 编译器只读写"当前 `@Database(version)` 对应的那一个文件"（§3.1 的 `inputPath=outputPath=<N>.json`）。
   更老/更晚的 JSON 既不参与编译，也不进 APK（`schemas/` 不是 assets；测试里是靠
   `androidTest.assets.srcDirs` 或构建脚本单独注入）。
2. **测试层：不删就必须"认领"。** 本仓的矩阵用例 `FullMigrationMatrixInstrumentedTest.kt:39`
   `for (version in 1..STUDY_DATABASE_VERSION)` 会为**每一个**存在 JSON 的版本建库再迁移。
   留着 `72.json` 就等于宣称"世界上存在 72 态用户"，并让这条用例去证明一条真实用户不会走的路径。
   更糟的是它**证明不了形状对**：该用例只断言 `libraryCatalogCount == 0`（`:44-47`），
   72/L2 → 73 留下的那张多余表它看不见（§3.3）。
3. **工程层：要么删干净，要么"保留 + 收敛 + 白名单"三件套。** 半保留（留 JSON、留边、但不收敛形状）
   是当前树的状态，也是最差的一种：多一张表、identity 却一样。

**本仓建议（72）**：保留 `72.json` 与 `72→73`，但在迁移里把分叉收敛掉。

```
internal val ERROR_CAUSE_MIGRATION_72_73 = object : Migration(72, 73) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 72 是分叉态：90 表（已发布的那套）或 91 表（L2 试验态）。
        // L2 表是派生索引（见下），73 的形状里没有它，必须显式丢弃，
        // 否则 72→73 会产出 93 表却带 73 的 identityHash（§3.3）。
        connection.execSQL("DROP TABLE IF EXISTS `knowledge_search_material_feature`")
        createErrorCauseTables(connection)
    }
}
```

- `DROP TABLE` 会连带删掉该表上的两个索引，无需单独 `DROP INDEX`。
- 安全性依据：该表列只有 `(subject, material_id, knowledge_node_id, search_feature)`，全部由
  `knowledge_teaching_material` / `knowledge_node` 派生，无用户创作载荷；
  L2 预注册文档亦明写"材料表是派生数据……恢复后按 §1.3 重建"
  （`docs/research/2026-11-02-kb-l2-material-features-prereg.md:120`、`:144-153`）。
- 先红后绿用例：`createDatabaseFromExportedSchema(72)` → 打开 → 断言
  `tableNames() == 73.json 的 92 张表集合`。不加 DROP 时该用例必红（多出 L2 表），加了转绿。
  这条断言正是现有矩阵用例缺的那一条。

**不建议直接删 72.json 的理由**：它对应过 mainline 上真实存在的 `cd7a792a`/`aa5e8e8e` 两态；
删文件不会让曾经安装过 72 的设备消失，只会让你**再也无法重建**那个状态去做迁移测试。
"删"只在以下条件同时成立时才对：确认没有任何 72 APK 落过设备 **且** 决定了不再支持从 72 升级。

### Q3 `version == max(schema)` 这类契约怎么写才不误报？

#### 现状缺陷（`KernelWave0SchemaContractTest.kt:32-58`、`:280-288`）

```kotlin
val latestExported = File("schemas").walkTopDown()
    .filter { it.isFile && it.extension == "json" }
    .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
    .max()
assertEquals(latestExported, STUDY_DATABASE_VERSION)
```

| # | 缺陷 | 后果 |
|---|---|---|
| 1 | 用**文件名**而不是 JSON 里的 `database.version` | `73.json` 内容写成 72 也能过；改名即可绕过 |
| 2 | `walkTopDown()` 扫整棵 `schemas/` | 一旦按 variant 配多个 schemaDirectory，跨 variant 取 max 无意义 |
| 3 | 只比 max | 中间留一个孤儿（本仓 72）**完全检测不到** → 假绿 |
| 4 | 高版本残留时失败信息错位 | 真正原因是"多了一个 74.json"，报的却是"版本常量不等于最高版本" |
| 5 | 不校验重复版本 | 合并后 `73.json` + `73 (1).json`、或两个子目录各一份，取哪个取决于遍历顺序 |
| 6 | 不校验 hash 绑定 | `73.json` 是 93 表旧版（`aa9f0313`）时照样绿——本仓真实发生过 |
| 7 | 相对路径 `File("schemas")` 依赖 CWD | 换工作目录就 `NoSuchElementException`，红得莫名其妙 |

#### 建议契约：五条不变式，全部点名失败文件

```kotlin
// core/database/src/test/.../SchemaHistoryContractTest.kt（草案，未落码）
private val DIR = File("schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase")

@Test fun `schema history is internally consistent`() {
    val files = DIR.listFiles { f -> f.isFile && f.extension == "json" }!!

    // R4 先做：同一版本号不得出现多份文件（'73.json' + '073.json' / '73 (1).json'）。
    // 若放在 associate 之后做，重复键已经被 Map 吞掉，断言变成空转。
    val byNameVersion = files.groupBy { it.nameWithoutExtension.toIntOrNull() }
    assertTrue(
        "同一版本号出现多份文件: ${byNameVersion.filterValues { it.size > 1 }.keys}",
        byNameVersion.values.all { it.size == 1 },
    )
    assertTrue(
        "存在非数字 schema 文件名: ${byNameVersion[null]?.map { it.name }}",
        null !in byNameVersion,
    )

    val docs = files.associate { file ->
        val nameVersion = requireNotNull(file.nameWithoutExtension.toIntOrNull()) {
            "非数字 schema 文件名: ${file.name}"
        }
        val db = json.decodeFromString<Wave0SchemaFile>(file.readText()).database
        // R1 文件名 == 内容版本
        assertEquals("${file.name} 的文件名与 database.version 不一致", nameVersion, db.version)
        nameVersion to db
    }

    // R2 无未来快照（残留的 74.json 要点名，而不是伪装成"版本不符"）
    val future = docs.keys.filter { it > STUDY_DATABASE_VERSION }
    assertTrue("存在高于当前版本的快照，请删除: $future", future.isEmpty())

    // R3 当前版本唯一
    assertEquals(
        "当前版本必须恰好一份导出物",
        1,
        docs.keys.count { it == STUDY_DATABASE_VERSION },
    )

    // R5 每个旧版本要么可达，要么在"版本 → 理由"白名单里
    val edges = STUDY_MIGRATIONS.map { it.startVersion to it.endVersion }
    val reachable = mutableSetOf(docs.keys.minOrNull()!!)
    var changed = true
    while (changed) {
        changed = false
        edges.forEach { (from, to) ->
            if (from in reachable && to <= STUDY_DATABASE_VERSION && reachable.add(to)) changed = true
        }
    }
    val unexplained = docs.keys - reachable - SOURCE_ONLY_SNAPSHOTS.keys
    assertTrue("无法从任何更早版本到达的快照，请删除或登记 SOURCE_ONLY: $unexplained", unexplained.isEmpty())
    // R6（hash 绑定）不在这里：core:database 不能依赖 core:data。该断言在
    // core:data 的 SmbkSelectionPlanTest:53（73.json vs IDENTITY_HASH）已存在，
    // 并由 §4-Q4 的 CI 守卫负责"JSON 变了但版本号没变"那一半。
}

/** 显式认领的"只能作为迁移源、没有入边"的快照；key=版本，value=理由，必须写清为什么留着。 */
private val SOURCE_ONLY_SNAPSHOTS = mapOf(
    72 to "L2 试验态快照；71→72 已随 L2 回滚删除，保留用于测试 72→73 收敛",
)
```

关键设计取舍（也就是"为什么不误报"）：

- **不要求 1..N 连续**：`71→73` 直跳是合法的，缺 `72.json` 不该红。判据是"每个存在的快照可达/被认领"，不是"每个号都有文件"。
- **不要求"每个旧 JSON 都有出边"**：本仓 72 确实有出边（`72→73`），所以用"出边"当判据会漏掉它。真正的异常是**没有入边**。
- **不把"高版本残留"和"版本不符"混成一个断言**：分开报，红的时候直接给出文件名与处置动作。
- **抽出 `STUDY_MIGRATIONS`**（`core/database` 内 `internal val`，用于 `addMigrations(*STUDY_MIGRATIONS)`）：
  否则测试只能靠反射或手抄列表，注册与测试必然漂移。`Room 3` 的 `Migration` 暴露了
  `public final int startVersion / endVersion`（`androidx/room3/migration/Migration.class` 实读），可直接读。
- **白名单必须是"版本 → 理由"**，不是 `Set<Int>`；空集合是默认值，等于"本仓不允许孤儿"。这样新增孤儿时
  必须写一句人话解释，评审能看见。

#### 失败矩阵（用本仓真实文件态验证这套判据）

| 文件态 | 现契约 | 建议契约 | 是否符合预期 |
|---|---|---|---|
| 1..73 正常 | 绿 | 绿 | ✅ |
| 73 + 72（本仓现状） | 绿 | 红（72 不可达，未认领）；登记白名单后绿 | ✅ 这正是要抓的 |
| 73 + 残留 74.json | 红（信息错位） | 红，点名 `74.json` | ✅ |
| 73 内容实为 93 表旧版 | 绿 | 红（`core:data` 的 hash 绑定 + CI diff 拦同号改写） | ✅ |
| 71→73 直跳、无 72.json | 绿 | 绿 | ✅ 不误报直跳 |
| `73.json` + `73 (1).json` | 绿 | 红（R4） | ✅ |
| `73.json` 内容 version=72 | 绿 | 红（R1） | ✅ |

### Q4 同号冲突的工程约定

1. **版本号 append-only、一次性占用。** 号一旦被任何提交写入 `STUDY_DATABASE_VERSION`，该号就绑定一份 schema
   （由 identityHash 唯一标识），永不再赋予第二种形状。改形状 = 加号，不改号。
2. **占用必须原子。** 同一个提交里必须同时包含：常量、`N.json`、迁移注册、`SmbkSelectionPlan.SCHEMA_VERSION/IDENTITY_HASH`
   （若涉及备份）、以及 golden 重算。本批的三次 KD-80 类漂移都源于"这五样被拆到多个提交/工作树"。
3. **合并顺序定归属，后到者重编号。** 批次 40 对 UI 分支的裁定（本线占 73，UI 合并时改 74）方向正确；
   但要注意 **UI 分支自身已提交的常量是 72**（`git show refs/heads/codex/ui-green-pages-2026-10-27:...StudyDatabase.kt:139`），
   合并时必须一次跳到 74，不能落回 72（72 已被 L2 用掉）。
4. **"改 JSON 不改版本号"必须由 CI 拦。** 编译器不会拦（§3.1），只能在 CI 上做：
   `git diff --name-only <base>...HEAD -- '*/schemas/*.json'` 非空时，要求
   `STUDY_DATABASE_VERSION` 的 diff 非空且新号 == 变化文件的号；否则红。
   这条能拦住 `70509247` 那次覆盖。落地位置建议并入 WS1 计划中的
   `tools/ci/check_backup_plan_sync.py`（同一份机器块解析即可复用）。
5. **重编号只允许发生在"尚未落任何设备"之前。** 一旦某号进过设备（哪怕 debug），只能递增。判断依据：
   该号是否出现在任何一个已安装 APK 的 `PRAGMA user_version` 里——这是可探测的，不是靠记忆。
6. **台账化。** 维护 `版本 → identityHash → 首次提交` 的机器可读记录（可放进 `SmbkSelectionPlan` 机器块或
   独立 json），契约测试比对该记录。没有台账，"72 到底是哪套"只能靠 `git show` 考古——本文件的 §2.2 就是被迫考古。

---

## 5. 迁移测试的正确写法（Room 3）

现状与本仓设施：

- 本仓**没有**引入 `androidx.room3:room3-testing`（全仓 grep 零命中）；矩阵用例用的是自建
  `createDatabaseFromExportedSchema(context, name, version)`（`ExportedSchemaTestDatabase.kt:7-48`）：
  按 JSON 的 `createSql`/`indices`/`views`/`setupQueries` 建库，再 `database.version = version`。
  这条路可用，且比 `MigrationTestHelper` 更贴本仓（能直接注入 92 表资产）。
- 已有结构比对工具 `assertStructurallyEqual`（`ExportedSchemaTestDatabase.kt:58-82`），**双向比较对象清单**
  （`missing`/`extra`/`differing` 都断言），但目前只有三处调用
  （`LibrarySearchMigrationInstrumentedTest.kt:104`、`MasterySchedulingMigrationInstrumentedTest.kt:147`、
  `PredictionAuditMigrationInstrumentedTest.kt:111`），**没有一处覆盖 72→73**。

建议补齐的三类用例（都与"同号/分叉"直接相关）：

| 用例 | 断言 | 杀掉的失败 |
|---|---|---|
| 72→73 形状收敛 | 迁移后 `sqlite_master` 表集合 == `73.json` 的 entity 集合（用 `assertStructurallyEqual`，reference 用现建 73 库） | L2 残留（§3.3-1） |
| 71→73 直跳 | 同上 + 错因两表存在 + 行数 0 | 直跳路径形状漂移 |
| identity 绑定 | 迁移后 `room_master_table.identity_hash == 1d0860…` 且 `PRAGMA user_version == 73` | 迁移没跑完/没 updateIdentity |

注意：**只有 identity 断言是不够的**——72→73 的 93 表库同样满足它（§3.3）。必须同时有表集合断言。

若将来引入官方 `room3-testing`，API 形状（官方文档实读）为：

```kotlin
@get:Rule val helper = MigrationTestHelper(
    instrumentation = instrumentation,
    databaseClass = StudyDatabase::class,
    driver = AndroidSQLiteDriver(),
    file = instrumentation.targetContext.getDatabasePath(TEST_DB),
)
val conn = helper.createDatabase(72)
val migrated = helper.runMigrationsAndValidate(73, listOf(ERROR_CAUSE_MIGRATION_72_73))
```

`runMigrationsAndValidate` 内部做的正是 `onValidateSchema`，**同样看不见额外表**——所以表集合断言仍要自己写。

---

## 6. 对本批的直接后果（需裁定，不是研究结论）

### 6.1 WS5 设备复现的前置阻塞

- 10:13 包在 `emulator-5584` 上装过（`step01:68`，`install -r -t ×4 Success`）；是否启动过应用**无记录**。
- 若 `smart-mistake-book.db` 存在（user_version=73 / identity=`aa9f0313`），当前 73/92 构建打开它：
  `user_version == 73` → 不走迁移 → `onOpen` 的 `checkIdentity` 比 `aa9f0313` vs `1d0860`/`9a7be7bb` → **抛异常**。
- 复现前必须先探测，再决定：

```
adb -s <serial> shell run-as <applicationId> \
  sh -c 'ls databases/ 2>/dev/null; sqlite3 databases/smart-mistake-book.db \
         "PRAGMA user_version; SELECT identity_hash FROM room_master_table WHERE id=42;"'
```

（无 `sqlite3` 时，用一次性的 instrumentation 读取，参照 `SchemaDumpInstrumentedTest.kt:34-46` 的写法，
但**必须用生产库名**才有意义。）

### 6.2 三条处置路径

| 路径 | 做法 | 代价 | 适用 |
|---|---|---|---|
| A（推荐） | 升到 **74**，`MIGRATION_73_74` 里 `DROP TABLE IF EXISTS knowledge_search_material_feature`（92 表库上是 no-op） | 多一个版本号；与 UI 分支的 73→74 重编号要合并成同一个 74 | 需要保留数据 |
| B | `fallbackToDestructiveMigrationFrom(73)` | 丢数据（这是唯一确定的代价）；`dropAllTables=true` 时会连未知表一起删，`false` 时只删声明表、L2 反而残留——两种都不是"收敛" | 不可取 |
| C | `adb uninstall` 后重装 | 只对开发模拟器成立 | 仅本地验证 |

路径 A 与批次计划 §4 "不改 schema 版本（本批 73；UI 合并时 73→74）" 不冲突——它正好落在 UI 会话要做的
73→74 里，**建议把 L2 的 DROP 一并放进那个 74 迁移**，避免再开一次版本 bump。

---

## 7. 落点草案（均未落码）

| # | 落点 | 类型 | 消灭的具体失败 |
|---|---|---|---|
| 1 | `core/database/.../StudyDatabase.kt`：把 `addMigrations(...)` 抽成 `internal val STUDY_MIGRATIONS: Array<Migration>` | patch 草案 | 测试侧无法枚举真实迁移边 → 注册与契约测试必然漂移 |
| 2 | `core/database/.../ErrorCauseMigration_72_73.kt`：`createErrorCauseTables` 前加 `DROP TABLE IF EXISTS knowledge_search_material_feature` | patch 草案 | 72→73 产出 93 表却带 73 identity（§3.3） |
| 3 | `core/database/src/test/.../SchemaHistoryContractTest.kt`：新增 §4-Q3 的五条不变式 | 新用例（先红后绿） | 孤儿/未来快照/重复版本/同号异形 四类漂移全部无守卫 |
| 4 | `core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt`：主循环内加"迁移后表集合 == 目标 JSON 表集合" | 用例增强 | 矩阵只证明"能打开"，不证明形状一致 |
| 5 | `tools/ci/check_backup_plan_sync.py`（WS1 #8）：增加"JSON 变更必须伴随版本号变更" | 脚本 | 拦住 §2.2 那类原地覆盖 |
| 6 | `KernelWave0SchemaContractTest.kt:32-58`：按 §4-Q3 重写 | 替换 | 现有 7 条缺陷 |

> 1/2/3/4/6 都在 `core/database`，**不在本批热区**；但属实施项，不在 R3 范围内，本轮只出草案。

---

## 8. 未验证项（不得当作通过）

- 设备上是否存在 `smart-mistake-book.db`（version=73 / `aa9f0313`）：**UNVERIFIED**（无启动记录、无探测输出）。
- 10:13 包、`e33969d2` 包的 DB 是否被真实打开过：**UNVERIFIED**。
- §3.2/§3.3 的运行时结论来自 3.0.0 制品字节码与本仓 KSP 生成物，**未在设备上复现**；未跑 Gradle、未跑
  instrumentation、未执行 `:core:database:testDebugUnitTest`。
- `DROP TABLE knowledge_search_material_feature` 的安全性基于表结构与 L2 预注册文档（派生数据），
  **未验证该表在生产库中是否可能有非派生行**（当前无写入方，但设备侧数据未抽样）。
- UI 分支 `codex/ui-green-pages-2026-10-27` 的实际合并形态（73 还是 74）未核对，仅读了其常量与 schema 目录。

---

## 9. 来源与采纳/拒绝理由

| 来源 | 类型 | 采纳/拒绝 | 理由 |
|---|---|---|---|
| `https://developer.android.com/training/data-storage/room/migrating-db-versions` | 官方 | **采纳** | 唯一权威说明"导出 JSON = schema history、进 VCS"与"无迁移路径 → IllegalStateException"；`room3-testing` 用法以此为准 |
| `https://developer.android.com/training/data-storage/room/migration-2-to-3` | 官方 | **采纳** | 确认 Room 3 = `androidx.room3` / KSP-only / `suspend migrate(SQLiteConnection)` / driver API；解释本仓为何用 `execSQL` 而非 `execSQL` 的 SupportSQLite 版本 |
| `room3-compiler-3.0.0.jar` 反编译（`Database.exportSchema`、`DatabaseProcessingStep`） | 一手制品 | **采纳（最高权重）** | 同号覆盖行为在文档里没有描述，只有制品能证；同一制品的 `Migration.startVersion/endVersion` 也支撑契约草案 |
| `room3-runtime.aar` 反编译（`BaseRoomConnectionManager`） | 一手制品 | **采纳（最高权重）** | 异常原文、identity 优先于结构校验、迁移后 `updateIdentity`，三点都直接决定 Q1/Q2 的答案 |
| `core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt` | 本仓生成物（13:35 构建） | **采纳** | `RoomOpenDelegate(73, 1d0860…, 9a7be7bb…)`、`onValidateSchema` 只遍历期望表，是本仓的确定事实 |
| 批次 40 证据 `step01:76`、`step02b` §1/§2 | 本仓证据 | **采纳（作为快照事实）** | 10:13 包 93 表 / `aa9f0313` 的唯一出处；同时明确"设备绿只绑该 APK" |
| `docs/research/2026-11-02-kb-l2-material-features-prereg.md:120,144-153,368` | 本仓设计文档 | **采纳（作为 L2 表为派生的依据）**；与 `ErrorCauseMigration_72_73.kt:12-15` 的"不删不改"**冲突**，以"派生数据 + 回滚时删除"为准并记入 §2.1 | 表结构本身（无用户载荷列）与该文档一致；两文档冲突需产品裁定 |
| StackOverflow 44197309 / issuetracker 322668000 | 社区 | **仅旁证，处置方式拒绝** | 症状一致（identity hash 不符）；其"删 `room_master_table`/换初始库"的修法会丢数据，不能进生产 |
| 搜索通道说明 | — | 记录 | 本轮 `developer.android.com` 直连 WebFetch 超时，改走搜索 MCP 的正文提取成功；bing/brave/ddg 部分返回空，按纪律未据此下"不存在"的结论 |

---

## 10. 一句话交付

**版本号不是标签，是 schema 内容的身份。** Room 3 在编译期对同号改写完全沉默，在运行期对同号异形直接崩溃；
所以"回滚后留下孤儿 JSON"不是文件卫生问题，而是必须用"可达性 + identity 绑定 + 原子占用"三条契约去守的
数据完整性问题。本仓 72 与 73 都已被两套 schema 用过，建议：72 保留但收敛，73 不再动、下一次形状变化用 74，
并把 §4-Q3 的契约测试补进 `core:database`。
