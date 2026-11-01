# Room 3.x schema 生成链路与 70→71 迁移设计（批次 32 / 驱动 T3-T4）

> **性质**：研究/设计文档，本文件是本轮**唯一**写盘产物。
> **本轮实跑**：只读源码与构建产物、`python 3.13 + sqlite3 3.50.4` 探针、`javap` 读 room3 3.0.0 字节码、
> `gh api` / `curl` 读 AndroidX 源码与 SQLite 官方文档。**零 Gradle、零 adb / am instrument、零 git 写操作、零源码改动。**
> **口径**：`已落` = 当前树有代码（本会话实读，引 `path:line`）；`未落` = 本会话零命中；
> `建议` = 本文判断，T3 落码时确认或否决；`裁定` = 需主会话决定。
> **0 项在本轮转"通过"**；所有构建/设备结论一律 UNVERIFIED。
>
> **纪律沿用**：热区禁改；schema 71 只由 T3 轨写（同一轨生成+回填）；JS 不广告；一致门仍是原型门；
> 真实 Provider 无凭据（模型质量 UNVERIFIED）；不许为变绿放宽断言。

---

## 0. 结论速览

| # | 结论 | 它消灭的具体失败 | 证据等级 |
|---|---|---|---|
| **C1** | `identityHash` 是 **KSP（room3-compiler）在注解处理期算出的产物**：`Database.identityHash = md5Hex(SchemaIdentityKey(entities+views))`，随后写进两处生成物——`StudyDatabase_Impl.kt` 的 `RoomOpenDelegate(version, identityHash, legacyIdentityHash)` 与 `<version>.json` 的 `database.identityHash` / `setupQueries`。**不跑带 KSP 的构建就没有 71 的真值** | 手写/估算哈希 → 新装库把错身份写进 `room_master_table`，首次打开即 `Room cannot verify the data integrity`；或备份裁剪器拿错哈希 fail-closed | 生成物实读（`StudyDatabase_Impl.kt:264`、`70.json`）+ room3-compiler 3.0.0 字节码 + AndroidX 源码 |
| **C2** | **不能从导出的 JSON 可靠重算 `identityHash`**。本会话按 room3-compiler 3.0.0 字节码还原了 `SchemaIdentityKey`/`Property`/`PrimaryKey`/`Index`/`ForeignKey` 的 `getIdKey` 公式，对 `70.json` 跑了 192 组参数组合（notNull 缺省语义 × 属性排序 × PK 列表格式 × orders × 实体排序 × 视图去留 × affinity 缺省），**全部算不出 `11a8a701…`** | 把"反解公式"当提取手段，会得到一个看似合理的错值；错值比不做更糟（把备份绿轮打红并把原因伪装成"备份坏了"） | 本会话实跑（见 §1.3）+ 批次 31 §9.2 同结论 |
| **C3** | 最小可信生成命令是 `./gradlew :core:database:kspDebugKotlin`：KSP 任务输出 `StudyDatabase_Impl.kt`，并由 `finalizedBy` 触发 `:core:database:copyRoomSchemas` 把 `<version>.json` 落到 `core/database/schemas/`。**不需要 `assembleDebug`**；但若 KSP 报解析错误，退回 `:core:database:assembleDebug` | 直接跑大构建浪费门禁时间；或跑错任务（`copyRoomSchemas` 单跑）拿到空目录/旧文件 | 插件源码 `AndroidPluginIntegration.kt:97,234-246`、`RoomSchemaCopyTask.kt:38-58`、`RoomArgumentProvider.kt:35-49`；本仓 daemon 日志实测任务名 `:core:database:copyRoomSchemas` / `:core:database:kspReleaseKotlin` |
| **C4** | 提取顺序：**JSON `identityHash` = Impl `RoomOpenDelegate` 第二参**，且 JSON `setupQueries` 里 `INSERT … room_master_table … '<hash>'` 必须等于它；最终以真机新装库的 `SELECT identity_hash FROM room_master_table WHERE id = 42` 为准 | 只看一处会把"JSON 与代码不同源"的旧故障（批次 15 的 68.json）重新引进来 | `ExportedSchemaContractTest.kt:91-114`、`Impl:698-699`、运行时 SQL（`RoomConnectionManager.kt:361-377`） |
| **C5** | 70→71 **不需要 `ALTER TABLE`**：`renderer_version`/`font_manifest_version` 两列 v70 已存在。只需 `DROP INDEX`（旧三列唯一索引）+ `CREATE UNIQUE INDEX`（新五列键）。新键是旧键的**严格超集**，存量行不可能冲突 ⇒ 迁移不会因数据失败 | 误以为要加列/重建表 → 写不必要的 `ALTER`，同时踩 Room 校验与 `TutorVisualSchemaContractTest` 两条断言 | `TutorVisualEntities.kt:121,166,168`；探针 P1/P4（§2.5） |
| **C6** | `CREATE UNIQUE INDEX IF NOT EXISTS` **同名**执行会静默保留旧定义（探针 P2：再插"同三列、不同版本"的行被旧键拒绝）。必须**先 DROP 再建**，或换新名 | 迁移"看似成功"，写入侧仍按三列去重，S1 退化成"静默复用旧行"（就是本批要消灭的失败） | 探针 P2 + 官方文档（`lang_createindex.html`："becomes a no-op"） |
| **C7** | Room **不校验触发器**（`TableInfo.equals` 只比列/FK/索引），所以触发器不改变 `identityHash`、不进 JSON；但 **fresh install 不会自动建自定义触发器**：生成的 `onCreate` 是空的（`Impl:797-798`），`onPostMigrate` 只在迁移路径被调用（`RoomConnectionManager.kt:296-301`），`createAllTables` 里只有 Room 自己生成的 FTS 触发器（`Impl:540-543`） | 只在迁移里建触发器 → 全新安装的库没有 backstop，漏洞在 fresh install 上原样存在 | 生成物 + 运行时源码 + `TableInfo.kt:224-252,292-299` |
| **C8** | Room 迁移在 `BEGIN EXCLUSIVE TRANSACTION` 里跑（`RoomConnectionManager.kt:194-207`）；事务内 **DDL 允许**、**`PRAGMA foreign_keys` 是 no-op**、**`VACUUM` 被拒**。因此 SQLite 官方 12 步改表法在本仓迁移上下文里**不可用**；本批方案也不需要它 | 试图在迁移里关外键重建子表 → 关不掉，且存量违规行会让 `INSERT…SELECT` 拷贝失败，把"有坏行"升级成"升级中断" | 探针 P3/P7/P9 + 官方文档 + 运行时字节码（`BEGIN EXCLUSIVE` 字符串） |
| **C9** | Room 的 schema 校验会**兜住"忘了 DROP 旧索引"**：它把表的索引集合与期望集合比较，多出旧三列索引 → `Migration didn't properly handle`。注意索引名规则：期望名以 `index_` 开头时只比较"对方也以 `index_` 开头"，但**列/唯一性/排序仍逐项比较** | 迁移只 CREATE 新索引、没 DROP 旧索引 → 真机升级在 `onValidateSchema` 炸；有了这条就知道它会在设备上红，而不是静默多一个索引 | `TableInfo.kt:239-252,292-299` |
| **C10** | 71 只改**索引**（不加表/列/FK），所以备份清单的结构数字**不变**：90 表 / 2 视图 / 100 FK 边 / 21 软引用 / 82 裁剪项。回填面只有 `SCHEMA_VERSION`、`IDENTITY_HASH` 与少数测试字面量 | 以为要同步表清单 → 白改一堆常量；或漏改 `SmbkSelectionPlanTest` 的 `70.json` 路径 → JVM 必红 | `SmbkSelectionPlan.kt:537-554`、`SmbkSelectionPlanTest.kt:45-55,611` |

---

## 1. 任务一：Room 3.x 的 `identityHash` 生成路径

### 1.1 生成链（Room 3.0.0 / KSP）

```
@Database(version = STUDY_DATABASE_VERSION, exportSchema = true, entities = [...], views = [...])
        │
        ├─ KSP 任务 ksp<Variant>Kotlin
        │     ├─ room3-compiler: Database.identityHash
        │     │     = SchemaIdentityKey().appendSorted(entities).appendSorted(views).hash()
        │     │     = md5Hex(每个 idKey + "?:?" 连接)
        │     ├─ 输出 A: build/generated/ksp/<variant>/kotlin/.../StudyDatabase_Impl.kt
        │     │     RoomOpenDelegate(version, "<identityHash>", "<legacyIdentityHash>")
        │     └─ 输出 B: build/intermediates/room/schemas/ksp<Variant>Kotlin/.../<version>.json
        │
        └─ finalizedBy :core:database:copyRoomSchemas
              └─ 复制到 core/database/schemas/.../<version>.json（内容相同则跳过写盘/复制）
```

仓内已落事实：

| 事实 | 证据 |
|---|---|
| 版本常量 70；`@Database(version = STUDY_DATABASE_VERSION, exportSchema = true)` | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139,242` |
| Room Gradle 插件开启 schema 导出 | `core/database/build.gradle.kts`：`alias(libs.plugins.androidx.room3)` + `room3 { schemaDirectory("$projectDir/schemas") }` |
| 生成物已存在（本会话实读） | `core/database/build/generated/ksp/debug/kotlin/.../StudyDatabase_Impl.kt:264` |
| `RoomOpenDelegate(70, "11a8a701bae767a8a3c5bc810e4743a4", "055c690dc8a9199ae525f939a5f60a61")` | 同上 `:264` |
| 新装库身份写入：`INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '11a8a701…')` | 同上 `:698-699`；`70.json` 的 `setupQueries` 逐字同值（本会话 python 实读） |
| `70.json`：`version=70`、`identityHash=11a8a701…`、90 entities / 2 views、`formatVersion=1` | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json` |

**两个生成物同源**：`70.json` 的 `identityHash` 与 `StudyDatabase_Impl.kt:264` 第二参逐字相同。
`70.json` 的 mtime 是 `2026-10-29 02:05`，Impl 是 `06:18`——同 schema 的后续 KSP 运行没有重写 JSON，
这正是编译器 `exportSchema` 的"内容相同就不写"规则（AndroidX `Database.kt:99-120`）。

### 1.2 官方任务链（为什么 `kspDebugKotlin` 就够）

Room Gradle 插件把 schema 输入/输出目录作为 **KSP processor args** 传进去：

- `RoomArgumentProvider.kt:35-49`：`room.internal.schemaInput` / `room.internal.schemaOutput`（KSP 不加 `-A` 前缀）。
- `AndroidPluginIntegration.kt:97`：`apTask.finalizedBy(config.copyTask)`。
- `AndroidPluginIntegration.kt:234-246`：KSP 任务名为 `ksp${Variant}Kotlin` / `ksp${Variant}KotlinAndroid` / `ksp${Variant}`。
- `RoomSchemaCopyTask.kt:38-58`：`copyRoomSchemas<Variant>` 把中间目录复制到 `schemaDirectory`；
  该任务**故意不声明 outputs**，靠 `finalizedBy` 触发；编译器不写新文件时它会被 `@SkipWhenEmpty` 跳过。
- `RoomExtension.kt:272-286`：`copyRoomSchemas`（ALL_MATCH 时后缀为空）。

本仓实测任务名（只读 daemon 日志，未跑 Gradle）：

```
$ grep -o ":[A-Za-z0-9:_-]*ksp[A-Za-z0-9:_-]*" .gradle/daemon/9.6.1/daemon-61580.out.log | sort -u
:core:database:kspReleaseKotlin
$ grep -n "copyRoomSchemas" .gradle/daemon/9.6.1/daemon-61580.out.log
41839::core:database:copyRoomSchemas SKIPPED
```

⇒ 本仓确有 `:core:database:copyRoomSchemas` 任务，且它可能被 SKIPPED（schema 未变时）。
**71 是新文件，不会 SKIP；但仍要核对生成物存在与 mtime，不能只看任务 exit=0。**

### 1.3 为什么"不能从 JSON 重算"（本会话实测）

按 room3-compiler **3.0.0 字节码**反解出的公式：

- `SchemaIdentityKey`：`SEPARATOR = "?:?"`；`append(x)` = `x + SEPARATOR`；`hash()` = `md5Hex(sb.toString())`
  （`javap -c androidx/room3/vo/SchemaIdentityKey.class`，与 AndroidX 源码 `SchemaIdentityKey.kt:28-46` 一致）。
- `Database.identityHash` = `appendSorted(entities)` 再 `appendSorted(views)` 后 `hash()`
  （字节码 `Database.identityHash_delegate$lambda$0`；源码 `Database.kt:66-72`）。
- `Entity.getIdKey` = `tableName` + `primaryKey` + 排序后的 `properties` + 排序后的 `indices` + 排序后的 `foreignKeys`
  （+ `withoutRowId` 为真时追加 `"1"`），每个元素后跟 `?:?`，最后再 md5
  （`Entity.kt:47-57`）。
- `Property.getIdKey` = `"$columnName-${affinity?.name ?: TEXT}-$nonNull"`，`defaultValue != null` 时追加 `"-defaultValue=$defaultValue"`
  （`Property.kt:58-67`）。
- `PrimaryKey.getIdKey` = `"$autoGenerateId-${algorithm.name}-$columnNames"`（非 AUTOINCREMENT 且 autoGenerate）或 `"$autoGenerateId-$columnNames"`
  （`PrimaryKey.kt:45-50`）。
- `Index.getIdKey` = `"$unique-$name-${columnNames.joinToString(",")}"`，orders 非空时追加 `"-…"`
  （`Index.kt:43-49`）。
- `ForeignKey.getIdKey` = `parentTable-parentCols-childCols-onDelete-onUpdate-deferred`
  （`ForeignKey.kt:30-37`）。
- `DatabaseView.getIdKey` = `md5(query.original + "?:?")`
  （`DatabaseView.kt:53-55`）。

把上述公式落到 `70.json`（字段/主键/索引/FK/视图全在），跑了 192 组参数组合：

```
notNull 缺省 ∈ {false,true} × properties 排序 ∈ {是,否} × PK 列表格式 ∈ {[a, b], a,b, a, b}
× index orders ∈ {含,不含} × entities 排序 ∈ {是,否} × views ∈ {含,不含} × affinity 缺省 ∈ {TEXT, ""}
```

结果：

```
computed: a83c23e117f6e7b908b6a59aede0d960   （最常见组合）
expected: 11a8a701bae767a8a3c5bc810e4743a4
match: False
```

**为什么算不出**：JSON 是编译器模型的**有损投影**——它不含 `withoutRowId`、FK `deferred`、PK `algorithm`，
视图的 `query.original` 也被 `createSql` 模板包了一层；而 `idKey` 的输入里包含编译器内部规范化后的
字段/默认值文本。**结论：JSON 不是重算 `identityHash` 的合法输入集。**
（批次 31 §9.2 已用 65/70 两个样本得到同一结论；本轮补上了"公式已知仍算不出"的直接证据。）

### 1.4 运行时如何用这个值（错值的后果）

`RoomConnectionManager.kt:361-377`（bytecode 亦一致）：

```sql
SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1
```

- 与 `openDelegate.identityHash` **或** `openDelegate.legacyIdentityHash` 任一相等即通过；
- 都不等则抛：`Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number. … Expected identity hash: <生成值>, found: <库内值>`。

因此：**错哈希不会在编译期报错，会在第一次打开已升级/已新建库时报错**。
`FullMigrationMatrixInstrumentedTest` 用 JSON 的 `setupQueries` 造库（`ExportedSchemaTestDatabase.kt:37-52`），
再经 `StudyDatabaseFactory.open` 打开——这一步会拿 **JSON 里的哈希 vs 生成代码里的哈希** 做交叉校验，
所以它同时是"71.json 与 Impl 同源"的运行时门（见 §3.5）。

### 1.5 任务一结论（给 T3 的硬要求）

1. **必须先构建**：没有带 KSP 的运行，就没有 71 的真值；不得用估算、占位、或"从 70.json 推导"。
2. 最小命令：`./gradlew :core:database:kspDebugKotlin`（KSP + `copyRoomSchemas`）。
   若怀疑增量/缓存：加 `--rerun-tasks`，或先删
   `core/database/build/intermediates/room/schemas/kspDebugKotlin` 与 `core/database/build/generated/ksp/debug`。
3. 生成后必须从**两处生成物**取值并比对（§3.3），不得只手抄一处。

---

## 2. 任务二：70→71 迁移与唯一键变更

### 2.1 目标变更（S1：版本并入快照身份）

**它消灭的具体失败**：painter/字体实现升级后，同内容再提交会被按三列唯一键**静默复用旧行**；
恢复侧看到 `rendererVersion != CURRENT` → `RENDERER_VERSION_MISMATCH` → 只读（`VisualSnapshotRestore.kt:223-234`），
而没有任何写路径能补出一行新版本快照 ⇒ **一次版本 bump 把历史图不可逆降级为只读**。

三处必须**同批同源**（派生键 = 唯一键 = 查重键）：

| 落点 | 现状 | 71 目标 |
|---|---|---|
| 唯一索引 | `TutorVisualEntities.kt:121`：`(program_id, state_sha256, draw_commands_sha256) UNIQUE` | 追加 `renderer_version`、`font_manifest_version` |
| `snapshotId` 派生 | `TutorVisualIdentity.kt:71-81`：3 参 `sha256(programId+stateSha+drawSha)[:32]` | 5 参，**全部长度前缀**（沿用 `deriveProgramId` 的 `:89-100` 写法） |
| 内容查重回读 | `TutorVisualDao.kt:83-95`：3 列 WHERE | 5 列 WHERE（否则查到旧版本行 → 静默复用） |

派生式建议：

```kotlin
snapshotId = "visual-snapshot-" + sha256(
    长度前缀(programId) ‖ 长度前缀(stateSha256) ‖ 长度前缀(drawCommandsSha256)
  ‖ 长度前缀(rendererVersion) ‖ 长度前缀(fontManifestVersion)
)[:32]
```

> 旧行处理：v70 行的 `snapshot_id` 按旧公式算，PK 不变仍合法；生产库四表在 v69→70 只 CREATE
> （`VisualSnapshotMigration_69_70.kt:15-20`），写入仓储此前无生产调用者 ⇒ 存量行应为 0。
> **不回填、不改历史 id**；DB 层复派生门只对"正在写入的行"生效（`RoomStudyDatabase.kt:2169-2210` 已是 require 形态）。

### 2.2 迁移 70→71（只需 DROP + CREATE UNIQUE）

```kotlin
internal val VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71 =
    object : Migration(70, 71) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71)
            connection.execSQL(VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71)
            // backstop 触发器（§2.4）；与迁移同事务，失败一起回滚
            VISUAL_LINK_GUARD_DDL_70_71.forEach(connection::execSQL)
        }
    }

internal val DROP_VISUAL_SNAPSHOT_CONTENT_INDEX_70_71 =
    "DROP INDEX IF EXISTS `index_tutor_visual_snapshot_program_id_state_sha256_draw_commands_sha256`"

internal val VISUAL_SNAPSHOT_IDENTITY_INDEX_DDL_70_71 =
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tutor_visual_snapshot_content_render_identity` " +
        "ON `tutor_visual_snapshot` " +
        "(`program_id`, `state_sha256`, `draw_commands_sha256`, `renderer_version`, `font_manifest_version`)"
```

配套 `@Entity`：

```kotlin
Index(
    name = "index_tutor_visual_snapshot_content_render_identity",
    value = ["program_id", "state_sha256", "draw_commands_sha256",
             "renderer_version", "font_manifest_version"],
    unique = true,
)
```

**为什么显式起名**：Room 默认名会把五列全拼进去（约 100 字符），可读性差且测试字面量长；
显式名仍以 `index_` 开头，不影响 `TableInfo.Index` 的宽松名比较（见 §2.6），列集合才是判定依据。

**红线**（沿用批次 31 §9.4）：

- 必须 `DROP` 旧索引；**不得**用同名 `CREATE … IF NOT EXISTS` 覆盖（探针 P2：会静默保留旧三列定义）。
- 新键是旧键严格超集 ⇒ `CREATE UNIQUE INDEX` 对存量行不可能失败（探针 P1）；但**若将来改成非超集，必须先在测试里证明**。
- 迁移 DDL 与 `@Entity` 的索引用列/唯一性/排序必须一致；迁移语句本身要与 v71 导出的
  `createSql` 结构一致（不要求逐字，但测试会按列/唯一性断言）。

注册点：`StudyDatabase.kt:409` 之后追加 `VISUAL_SNAPSHOT_IDENTITY_MIGRATION_70_71`；
版本常量 `:139` 改 71；`@Database` 的 `version = STUDY_DATABASE_VERSION` 自动跟随。

### 2.3 写入侧 EXISTS 谓词（已在树，保留）

`TutorVisualDao.kt:298-315` 的初次 link 已是：

```sql
INSERT INTO tutor_message_visual (...)
SELECT :messageId, :ordinal, :programId, snapshot_id, 1, ...
FROM tutor_visual_snapshot
WHERE snapshot_id = :snapshotId
  AND status = :liveStatus
  AND program_id = :programId      -- 批次 31 补
```

指针 CAS（`:367-397`）与题面 pin（`:443-470`）也已有同形 `EXISTS`。
**它们是"当前写法正确"，不是"未来任何写法都正确"**——后者由触发器 backstop 承担（§2.4）。
`CREATE TRIGGER` 被 Room 归为 UNKNOWN query type，不能写进 `@Query`（`LibraryFtsSearchDao.kt:246-250`）。

### 2.4 触发器 backstop（DB 级不变量）

四条触发器，覆盖 INSERT 与"指针/引用列" UPDATE；`RAISE(ABORT)` 带自定义码：

```sql
CREATE TRIGGER IF NOT EXISTS tutor_message_visual_guard_insert
BEFORE INSERT ON tutor_message_visual
FOR EACH ROW WHEN NOT EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = NEW.current_snapshot_id
      AND s.program_id   = NEW.program_id
      AND s.status       = 'LIVE')
BEGIN SELECT RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM'); END;

CREATE TRIGGER IF NOT EXISTS tutor_message_visual_guard_update
BEFORE UPDATE OF current_snapshot_id, program_id ON tutor_message_visual
FOR EACH ROW WHEN NOT EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = NEW.current_snapshot_id
      AND s.program_id   = NEW.program_id
      AND s.status       = 'LIVE')
BEGIN SELECT RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM'); END;

CREATE TRIGGER IF NOT EXISTS problem_revision_visual_guard_insert
BEFORE INSERT ON problem_revision_visual
FOR EACH ROW WHEN NOT EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = NEW.snapshot_id
      AND s.program_id   = NEW.program_id
      AND s.status       = 'LIVE')
BEGIN SELECT RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM'); END;

CREATE TRIGGER IF NOT EXISTS problem_revision_visual_guard_update
BEFORE UPDATE OF snapshot_id, program_id ON problem_revision_visual
FOR EACH ROW WHEN NOT EXISTS (
    SELECT 1 FROM tutor_visual_snapshot s
    WHERE s.snapshot_id = NEW.snapshot_id
      AND s.program_id   = NEW.program_id
      AND s.status       = 'LIVE')
BEGIN SELECT RAISE(ABORT, 'TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM'); END;
```

（列名以 `TutorVisualEntities.kt` 的 `problem_revision_visual` 实际列名为准；T3 落码前逐列核对。）

**fresh install 路径（必须补，否则 backstop 只在升级库存在）**：

- 迁移里建一次（升级库）；
- **另**在 `StudyDatabaseFactory.open` 注册 `RoomDatabase.Callback.onOpen`（room3 提供 suspend `onOpen(connection)`，
  `javap` 已验证）或用仓内 `RoomLibrarySearchStore.ensureSearchTriggers()` 同款"首次使用前幂等建"
  （`RoomLibrarySearchStore.kt:342-380`、`LibrarySearchFtsMigration.kt:117-133`）。
- 二者都用 `IF NOT EXISTS`，重复调用无副作用（探针 P5：重复 CREATE 不报错）。

**为什么不进 JSON / 不改 identityHash**：Room 的 `onValidateSchema` 只比
name/columns/FKs/indices（`TableInfo.kt:292-299`），触发器不在其中；`createAllTables` 也不会生成自定义触发器
（生成物 `Impl:797-808`：`onCreate` 空、`onPostMigrate` 只有 FTS 触发器）。

### 2.5 Room 迁移事务内的 SQLite 行为（本会话探针 + 官方文档）

探针环境：Python 3.13 / **SQLite 3.50.4** / `:memory:` / `PRAGMA foreign_keys=ON`；事务形态模拟 Room 的
`BEGIN EXCLUSIVE … END/ROLLBACK`（`RoomConnectionManager.kt:194-207`）。

| # | 场景 | 结果 | 含义 |
|---|---|---|---|
| P1 | 有存量行时 `BEGIN EXCLUSIVE` → `DROP INDEX` → `CREATE UNIQUE INDEX`（五列）→ `COMMIT` | 成功；索引只剩新五列 | 迁移可在事务内完成 |
| P1b | 之后插"同三列、新 renderer/font"的同内容行 | **接受**（COUNT=2） | S1 生效：新版本产生第二行 |
| P1c | 再插"五列全同"的行 | `UNIQUE constraint failed`（列出五列） | 新键确实生效 |
| P2 | 不 DROP，直接同名 `CREATE UNIQUE INDEX IF NOT EXISTS` 改列 | `sqlite_master` 仍是旧三列定义；再插"同三列、不同版本"被拒 | **必须 DROP 或换名**（官方："becomes a no-op"） |
| P3 | `BEGIN` 后 `PRAGMA foreign_keys=OFF` | 读回仍 **1**；`COMMIT` 后再设才变 0 | 事务内关不掉外键（官方：no-op within a transaction） |
| P4 | 事务内对新五列建唯一索引，但表里已有五列重复行 | `UNIQUE constraint failed`，`ROLLBACK` | 非超集/脏数据会让迁移失败；本方案是超集，不触发 |
| P5 | 事务内 `CREATE TRIGGER`；重复执行一次 | 成功；触发器数 1 | 触发器可随迁移建，幂等 |
| P5b | 触发器 `RAISE(ABORT,'…')` 命中 | `IntegrityError: TUTOR_VISUAL_…` | 自定义错误码可分流 |
| P6 | 在已有违规行的表上 `CREATE TRIGGER`（外键关，模拟历史坏行） | 成功；旧行保留；新违规行被拒 | backstop 只拦后续写，不阻塞升级 |
| P7 | 事务内 `DROP INDEX` 后 `ROLLBACK` | 旧索引恢复 | DDL 随事务回滚 |
| P8 | `INSERT … SELECT … WHERE EXISTS(status/program_id)` 跨 program | 0 行；同 program 1 行 | 写入侧 EXISTS 的语义 |
| P9 | 事务内 `CREATE TABLE` 后 `ROLLBACK` | 表不存在 | DDL 原子 |
| P10 | 事务内 `VACUUM` | `cannot VACUUM from within a transaction` | 迁移里不能 VACUUM（官方同结论） |
| P11 | `BEFORE UPDATE OF current_snapshot_id, program_id` 触发器 | 改这两列才检查；`UPDATE … SET revision=revision+1` 不被拦 | UPDATE 触发器不会误伤无关列 |
| P12 | `RAISE(ABORT)` 在显式事务内 | 只终止该语句；`in_transaction` 仍真，后续语句可执行；`ROLLBACK` 才整体回滚 | Room 捕获异常后回滚整个迁移/写事务，语义正确 |

官方交叉核对（两个独立来源一致）：

- SQLite 事务：`CREATE`/`DROP` 属于 write statements，可在事务内执行（`lang_transaction.html`）。
- `PRAGMA foreign_keys`："This pragma is a **no-op within a transaction**; … may only be enabled or disabled when there is no pending BEGIN or SAVEPOINT."（`pragma.html#pragma_foreign_keys`）。
- `CREATE [UNIQUE] INDEX [IF NOT EXISTS]`：同名索引存在时该命令 **becomes a no-op**（`lang_createindex.html`）。
- `VACUUM`："A VACUUM will fail if there is an open transaction on the database connection…"（`lang_vacuum.html`）。
- `RAISE(ABORT, …)` 在触发器内返回 `SQLITE_CONSTRAINT` + 指定消息（`lang_createtrigger.html`）。
- SQLite 12 步改表法要求先 `PRAGMA foreign_keys=OFF`（`lang_altertable.html` §7）——**在本仓迁移上下文做不到**。

**对设计的直接约束**：

1. 本批**不要**做"重建子表 + 复合外键"（需要关外键；关不掉；且有坏行时升级中断）。
2. 不要用 `ALTER TABLE`：两列已存在。
3. 不要在迁移里 `VACUUM`（备份/裁剪器自己的 `vacuumed` 逻辑不在 Room 事务内，另说）。
4. 触发器错误信息用 `RAISE(ABORT, '<有界码>')`，便于调用方按码分流。

### 2.6 Room 的校验边界（决定"迁移写错会不会被发现"）

| 项 | Room 行为 | 证据 |
|---|---|---|
| 列/FK/索引 | 逐项比较；不一致 → `Migration didn't properly handle: …` | `RoomConnectionManager.kt:296-301`；`TableInfo.kt:292-299` |
| 索引集合 | 期望集合 vs 实际集合；**多一个旧索引也会失败** | `TableInfo.kt:292-299` |
| 索引名 | 期望名以 `index_` 开头时，只要求对方也以 `index_` 开头（不比全名）；列/唯一性/orders 仍严格比较 | `TableInfo.kt:239-252` |
| 触发器 | **不比较**（不在 TableInfo 里） | `TableInfo.kt` 字段只有 columns/foreignKeys/indices |
| `identityHash` | 打开库时与 `room_master_table` 比对（非 schema 结构校验） | `RoomConnectionManager.kt:361-377` |

**推论**：

- 若迁移忘了 `DROP` 旧索引，`onValidateSchema` 会因为"实际索引集合多一个"而失败——这是设备上的硬红，不会被静默放过。
- 若迁移把索引名写错但列/唯一性一致，Room **可能**接受（宽松名规则）；所以测试仍要断言名字，别依赖 Room。
- 触发器写错**不会**被 Room 校验；只能靠用例覆盖（§2.8）。

### 2.7 牵动面清单（改一处先枚举）

| 落点 | 改什么 | 漏掉的后果 |
|---|---|---|
| `TutorVisualEntities.kt:121` | 索引换五列 + 显式名 | Room 校验/导出不一致 → 真机升级炸 |
| `TutorVisualIdentity.kt:71-81` | `deriveSnapshotId` 加两参 + 长度前缀 | 派生与唯一键不同源 → S2（派生新 id 被旧键吞掉） |
| `TutorVisualDao.kt:83-95` | `findSnapshotByContent` 加两参 | 查到旧版本行 → 静默复用（S4 复发） |
| `RoomStudyDatabase.kt:2169-2210` | 复派生传两版本；查重同步 | 同上；且 `require` 会拒掉正确的新 id |
| `TutorVisualCommitRepository.kt:183-190,422-455` | `snapshotIdFor` / `toWrite` 传两版本 | 编译期会报错（好），但若改成常量则身份错 |
| 新 `VisualSnapshotIdentityMigration_70_71.kt` | DROP + CREATE UNIQUE + 4 触发器 | 缺 DROP → 旧键仍生效；缺触发器 → 无 backstop |
| `StudyDatabase.kt:139,409` | 71 + 注册 | 新库/升级库版本不一致 |
| fresh install 触发器路径 | `Callback.onOpen` 或首次使用前 ensure | 全新安装无 backstop |
| `SmbkSelectionPlan.kt:40,43` | 71 + 新 hash | 备份裁剪器 fail-closed（`BackupSnapshotTrimmer.kt:267-284`） |
| `SmbkSelectionPlanTest.kt:45-55,611` | 路径/字面量改 71.json | JVM 必红（读不到 70 的版本断言） |
| `KernelWave0SchemaContractTest.kt:33-56` | 字面量 70 → 71 + 注释 | JVM 必红（最高导出 == 常量） |
| `TutorVisualSchemaContractTest.kt:182,238-248,394-402` | 保留 v70 冻结断言，另加 v71 断言 | 现断言会把 71 的正确索引判错（若误改） |
| 新增 `TutorVisualSnapshotMigration_70_71InstrumentedTest` | 带数据迁移 + 触发器负向 | 只有空库矩阵，看不出"旧行还在/新键生效" |
| `docs/current/*`（热区，只读） | 由主会话决定是否同步 | 文档漂移 |

### 2.8 用例矩阵（先红后绿；全部新增）

| # | 用例 | 期望 |
|---|---|---|
| M1 | 用 70.json 建库、灌 1 行 v70 快照（三列键），打开 app（70→71） | 迁移成功；行还在；旧索引消失；新索引存在（五列、unique） |
| M2 | 接 M1：同 `(program,state,draw)`、新 `renderer/font` 再提交 | 新增一行（COUNT=2）；`snapshot_id` 与旧行不同 |
| M3 | 接 M2：同五列再插 | `UNIQUE constraint failed`（五列） |
| M4 | 事务内故意不 DROP 旧索引（测试夹具） | `Migration didn't properly handle`（证明 C9 的兜底真实存在） |
| M5 | 触发器直写：跨 program link / STAGING link / 跨 program 重指 | `SQLiteConstraintException`，message 含 `TUTOR_VISUAL_LINK_NOT_LIVE_OR_FOREIGN_PROGRAM` |
| M6 | 触发器直写：同 program + LIVE | 接受 |
| M7 | `UPDATE … SET revision=revision+1` | 接受（UPDATE 触发器不误伤） |
| M8 | fresh install（新库）后 `sqlite_master` | 四条触发器存在（证明 onOpen/ensure 路径生效） |
| M9 | 迁移失败注入（在迁移里 `RAISE`） | 库仍是 v70；旧索引/数据/触发器状态不变（回滚） |
| M10 | 71.json 的 `setupQueries` 哈希 vs 生成代码 | 打开即通过；改坏 JSON 哈希 → `Room cannot verify the data integrity` |

---

## 3. 任务三：生成 → 回填 → 再验证（可复跑流程）

### 3.1 顺序硬约束

```
① 所有影响 schema 的代码先落齐（版本 71、实体索引、迁移、触发器、写入侧 5 列）
        ↓
② 跑一次带 KSP 的构建（生成 71.json + Impl，二者同源）
        ↓
③ 从两处生成物提取 identityHash 并比对
        ↓
④ 用生成值回填 SmbkSelectionPlan（SCHEMA_VERSION=71、IDENTITY_HASH=<值>）与测试字面量
        ↓
⑤ 跑 JVM 契约门 → 迁移矩阵 1→71 → 70→71 专用用例 → 备份 34/34 → 设备轨
```

**违反顺序的具体失败**：

- 先改常量再生成 → 中间态 `SCHEMA_VERSION=71` 但 `71.json` 不存在/旧 → `KernelWave0` 红、裁剪器 fail-closed。
- 先生成、后改实体 → 71.json/Impl 对应旧实体；回填后真实升级会 `Migration didn't properly handle` 或哈希不符。
- 只改派生/查重、不改唯一键 → 唯一键仍三列，新 id 被 `INSERT OR IGNORE` 吞掉（S2）。

### 3.2 生成命令与任务链

```bash
# 推荐：只跑 KSP（+ finalizedBy copyRoomSchemas）
./gradlew :core:database:kspDebugKotlin

# 若 KSP 因源码解析失败/增量缓存可疑，二选一：
./gradlew :core:database:kspDebugKotlin --rerun-tasks
./gradlew :core:database:assembleDebug --rerun-tasks
```

生成物位置（本会话实读确认存在）：

- `core/database/build/generated/ksp/debug/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.kt`
- `core/database/build/intermediates/room/schemas/kspDebugKotlin/…/71.json`（中间目录）
- `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json`（copy 后）

> 注意：`copyRoomSchemas` 可能被 SKIPPED（daemon 实测出现过）。**判定标准是 `71.json` 存在且
> `database.version == 71`，不是任务 exit=0。**

### 3.3 提取与交叉校验（可靠方式，按优先级）

**方式 A：导出的 71.json（首选，人可读、可 diff）**

```bash
python - <<'PY'
import json
p = r"core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json"
d = json.load(open(p, encoding="utf-8"))["database"]
print("version       =", d["version"])
print("identityHash  =", d["identityHash"])
print("entities      =", len(d["entities"]), "views =", len(d["views"]))
print("masterInsert  =", [q for q in d["setupQueries"] if "identity_hash" in q])
PY
```

**方式 B：生成的 `StudyDatabase_Impl.kt`（与 A 同源，必须相等）**

```bash
grep -n "RoomOpenDelegate(71" \
  core/database/build/generated/ksp/debug/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.kt
# 期望形如：RoomOpenDelegate(71, "<identityHash>", "<legacyIdentityHash>")
```

**方式 C：编译产物常量（A/B 都不可读时的兜底）**

```bash
javap -p -c \
  core/database/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.class \
  | grep -A2 "RoomOpenDelegate"
```

**方式 D：运行时真值（最终裁决）**

```sql
SELECT identity_hash FROM room_master_table WHERE id = 42;
```

在真机/仪器化里对**新装库**执行；它必须等于 A/B/C 的值。
A/B/D 三者一致才算提取成功；任一不一致 → 停项，不猜。

**不得用作提取手段**：

- 从 70.json 或其他版本 JSON 反推（§1.3 实测失败）；
- 手写/占位（`00000000000000000000000000000000` 只允许存在于 v16 历史占位，见 `ExportedSchemaContractTest.kt:67-84`）；
- 使用 `legacyIdentityHash`（第三个参数）冒充（运行时虽接受 legacy，但新装库写入的是新 hash；裁剪器只认新 hash）。

### 3.4 回填清单（只改这些；数字不变）

| 文件 | 行 | 旧值 | 新值 |
|---|---|---|---|
| `core/data/.../backup/SmbkSelectionPlan.kt` | 40 | `SCHEMA_VERSION = 70` | `71` |
| 同上 | 43 | `IDENTITY_HASH = "11a8a701…"` | 71.json 的 `database.identityHash` |
| 同上 | 6,10-13,39,42,195,538-539,562 等注释 | "schema 70"/"70.json" | 71（注释同步，**不动结构数字**） |
| `core/data/src/test/.../SmbkSelectionPlanTest.kt` | 45-47 | `70`/`SCHEMA_70_PATH` | `71`/`SCHEMA_71_PATH` |
| 同上 | 611 | `SCHEMA_70_PATH = ".../70.json"` | `".../71.json"` |
| `core/database/src/test/.../KernelWave0SchemaContractTest.kt` | 53 | 字面量 `70` | `71`（+ 注释追加 71 的来源） |
| `core/database/src/test/.../TutorVisualSchemaContractTest.kt` | 182 等 | 读 `70.json` 的 v70 断言 | **保留**；另加一组读 `71.json` 的 v71 断言 |

**不需要改**：`SmbkSelectionPlan` 的 `allTables.size==90`、`views.size==2`、`expectedClosure==5`、
`softReferences==21`、`trimOrder==82`（本批无表/列/FK 变化）。`BackupSnapshotTrimmer` 无字面量，
通过 `SmbkSelectionPlan` 自动跟随。

**已知运行影响（需主会话知悉）**：裁剪器要求快照库的 `user_version` 与 `room_master_table` 哈希**精确等于**
当前常量（`BackupSnapshotTrimmer.kt:267-284`）。升到 71 后，**旧版本（≤70）产生的 `.smbk` 快照不再可裁剪**。
这是既有 fail-closed 行为的延续，不是新缺陷；若要兼容，需另立"版本容忍"任务，不得在本批顺手放宽门。

### 3.5 回填后必须重跑的门

| 门 | 命令（由脚本串行执行） | 它证明什么 |
|---|---|---|
| 编译/KSP 产物可用 | `./gradlew :core:database:assembleDebug` | Impl 与实体一致、可编译 |
| 数据库 JVM 契约 | `./gradlew :core:database:testDebugUnitTest` | `KernelWave0`（最高导出==71）、`ExportedSchemaContractTest`（71.json 完整、hash 非占位、与 insert 同值）、`TutorVisualSchemaContractTest`（v70 冻结 + v71 新断言） |
| 备份/清单 JVM | `./gradlew :core:data:testDebugUnitTest` | `SmbkSelectionPlanTest`（71.json 的 90/2/100、version/hash 同源）、裁剪器用例 |
| 迁移矩阵 1→71 | `:core:database:connectedDebugAndroidTest`（`FullMigrationMatrixInstrumentedTest`，循环 `1..STUDY_DATABASE_VERSION`，`FullMigrationMatrixInstrumentedTest.kt:36-52`） | 每个历史版本都能升到 71；用 71.json 的 setupQueries 建库再打开 ⇒ **JSON↔Impl 哈希一致性** |
| 70→71 专用 | 同模块新 `TutorVisualSnapshotMigration_70_71InstrumentedTest` | 带数据迁移、索引形状、触发器负向（M1-M10） |
| 结构门 | 同模块 `TutorVisualStructureGatesInstrumentedTest` | DAO 级 EXISTS/CAS/pin 仍成立 |
| 备份 34/34 | `:core:data:connectedDebugAndroidTest` + `:app:connectedDebugAndroidTest`（按批次 31 的 34/34 组成） | 备份选择/恢复/裁剪/设备迁移演练 |
| 设备重装 | `assemble* + install -r -t + am instrument` | 批次 31 的 APK 与现树不同源，必须重建重装 |

> 门禁命令由脚本串行执行；本文件不执行它们。表中的"证明什么"是设计意图，**不是本轮结果**。

### 3.6 失败时如何回滚

| 失败点 | 回滚/修复 |
|---|---|
| KSP 编译失败 | 不产生新 JSON；修代码后重跑。清 `build/generated/ksp/debug` 与 `build/intermediates/room/schemas/kspDebugKotlin` 再跑 |
| JSON 生成但 hash 与 Impl 不一致 | **不要改 JSON**。说明两处不是同一次处理：清缓存 + `--rerun-tasks` 重跑；仍不一致则停项登记 |
| 回填后 JVM 门红（哈希不符） | 以生成物为准，重读并改正常量；**不得**反向改 JSON/Generator 去迁就常量 |
| 回填后迁移矩阵红（`Migration didn't properly handle`） | 迁移 DDL 与实体不一致（索引列/名/唯一性）；对照 71.json 的 `createSql` 修迁移 |
| 触发器用例红 | 先确认是"触发器没建"（fresh install 路径）还是"条件写错"；前者补 onOpen/ensure，后者修 SQL |
| 整批放弃 | ① `STUDY_DATABASE_VERSION` 回 70；② 删除 `71.json`；③ 还原 `SmbkSelectionPlan` / 两个测试的字面量；④ 删 `build/generated/ksp/debug`、`build/intermediates/room/schemas/kspDebugKotlin`；⑤ 跑 `KernelWave0` 确认回到"最高导出==70"。**不得留下 71 常量 + 无 71.json 的中间态** |
| 设备升级失败 | Room 迁移在 `BEGIN EXCLUSIVE` 里，异常自动 `ROLLBACK`（P7/P9/P12）；用户库保持 70，可重试。记录 logcat 的 `Migration didn't properly handle` 原文 |

### 3.7 复跑骨架（不执行，供脚本轨使用）

```bash
set -euo pipefail
DB=core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase

./gradlew :core:database:kspDebugKotlin
test -f "$DB/71.json" || { echo "71.json missing"; exit 1; }

HASH=$(python - "$DB/71.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding="utf-8"))["database"]
assert d["version"]==71, d["version"]
h=d["identityHash"]
assert len(h)==32 and all(c in "0123456789abcdef" for c in h), h
assert any(f"'{h}'" in q for q in d["setupQueries"]), "setupQueries hash mismatch"
print(h)
PY
)
echo "identityHash=$HASH"

IMPL=core/database/build/generated/ksp/debug/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.kt
grep -q "RoomOpenDelegate(71, \"$HASH\"" "$IMPL" \
  || { echo "Impl hash != 71.json hash"; exit 1; }

# 只有以上通过，才允许回填 SmbkSelectionPlan 并进入门禁阶段。
```

---

## 4. GitHub 参考（Android Room schema 演进）

> 通道：`gh api repos/<owner>/<repo>` 与 `gh api .../readme`（license/stars/pushed_at 均为**本会话实测**）。
> 评分是主观判断，只看已验证元数据 + 读到的原文。**只借形态，不引依赖**。

| 仓 | license（实测） | stars | 最近 push（实测） | 借哪一点（原文/位置） | 在本仓落地 | 评分（license/维护/可复用） |
|---|---|---|---|---|---|---|
| [androidx/androidx](https://github.com/androidx/androidx) | Apache-2.0 | 6,122 | 2026-10-29 | ① `Database.identityHash = SchemaIdentityKey(entities+views).hash()`（`room3/room3-compiler/.../vo/Database.kt:66-72`）；② `RoomOpenDelegate(version, identityHash, legacyIdentityHash)`（`RoomOpenDelegate.kt`）；③ 迁移 `BEGIN EXCLUSIVE`（`RoomConnectionManager.kt:194-207`）；④ `TableInfo.Index` 只比列/唯一性/orders、名字宽松（`TableInfo.kt:239-252`）；⑤ schema 输入/输出经 KSP args + `finalizedBy copyRoomSchemas`（`RoomArgumentProvider.kt:35-49`、`AndroidPluginIntegration.kt:97`） | §1 全节、§2.6、§3.2 的**决定性依据** | 5 / 5 / 5 |
| [MatrixDev/Roomigrant](https://github.com/MatrixDev/Roomigrant) | MIT | 371 | 2021-04-21 | README："automatically generate Android Room library migrations using compile-time code generation"——用 `room.schemaLocation` 的 JSON 做编译期迁移生成 | **不采用**：最后 push 2021，早于 Room 3/KSP；自动生成会与我们手写的触发器/回填冲突。只借"schema JSON 可作为机器输入"的思路 | 5 / 1 / 2 |
| [AdamMc331/mastering-room-migrations](https://github.com/AdamMc331/mastering-room-migrations) | **无 license** | 41 | 2024-09-14 | README 是迁移 SQL 速查表（加列/删列/改类型/加表/删表） | 只作人读参考；**无 license 不得复制代码** | 1 / 3 / 2 |
| [nick-bennett/room-schema-parser-plugin](https://github.com/nick-bennett/room-schema-parser-plugin) | Apache-2.0 | 1 | 2022-05-13 | README："extract DDL from an Android Room SQLite database schema file"；明确只支持 **Room 2.x** JSON | 反例参考：证明"解析 JSON 得 DDL"可行，但**不能**用它算 identityHash（§1.3） | 5 / 1 / 2 |
| [ntsk/room-schema-docs-gradle-plugin](https://github.com/ntsk/room-schema-docs-gradle-plugin) | MIT | 5 | 2026-10-28 | README：从 Room schema JSON 生成 Mermaid ER 图，"manage database changes over time" | 可选：把 71.json 的表关系画出来做 review；不影响门禁 | 5 / 4 / 3 |
| [cashapp/sqldelight](https://github.com/cashapp/sqldelight) | Apache-2.0 | 6,898 | 2026-10-25 | README："It verifies your schema, statements, and migrations at compile-time" | 思想参考：迁移也应有编译期校验；本仓对应物是 `KernelWave0SchemaContractTest`（把"提版本没导出"提前到 JVM） | 5 / 5 / 3 |
| [moploe/room-plugin-poc](https://github.com/moploe/room-plugin-poc) | MIT | 0 | 2026-08-14 | README：Room 3.0.1 (`androidx.room3`) 的 KSP 伴生插件，自动 schema 迁移（新增表/列/索引） | **不采用**：0 star、单人 POC，自动迁移会绕过我们的显式 SQL 与触发器；只说明"Room 3 + KSP 生态仍在早期" | 5 / 2 / 2 |

**取舍**：不引入任何依赖。理由：① 本仓已有 Room 3.0.0 + KSP 官方链路；② 迁移的难点不是"生成 SQL"，
而是"身份/唯一键/触发器/备份清单四方同源"，第三方自动迁移解决不了；③ 自动迁移会隐藏 DDL 差异，
与本批"每条 DDL 可审计"的方向相反。

---

## 5. 未验证项与边界（如实）

| # | 项 | 状态 |
|---|---|---|
| U1 | 本轮**零 Gradle、零仪器化、零设备**：所有"已落"来自源码/生成物只读与 `git status`；"通过/绿"一律未发生 | UNVERIFIED |
| U2 | 71 的 `identityHash` **未知**，必须由 T3 的 KSP 构建产出；本文不给具体值 | 未获取 |
| U3 | SQLite 探针是桌面 **3.50.4**，不是设备 Android SQLite；结论是标准语义，但设备实测未做 | 待设备轮复核（建议在 70→71 仪器化用例里覆盖 M1-M10） |
| U4 | `developer.android.com` 的**目标迁移页**本轮连接超时（`curl` 000/22s；同主机其他路径可通）；改用 `developer.android.google.cn` 镜像与 AndroidX 源码，二者与实现一致 | 通道限制，已换通道 |
| U5 | AndroidX 源码取自 `androidx-main`；本仓用 **room3 3.0.0**。已用 3.0.0 AAR/编译器字节码核对关键字符串（`BEGIN EXCLUSIVE`、`?:?`、`room_master_table` SQL、`SchemaIdentityKey` 公式）一致；但**行号**是 androidx-main 的 | 行为已核对，行号按 main |
| U6 | 触发器在 Android 设备上的实际错误类型（`SQLiteConstraintException`）未实测；探针是 Python 的 `IntegrityError` | 待设备 |
| U7 | `copyRoomSchemas` 对"新版本文件"是否必然 RUN（而非 SKIP）未实测；只观察到它对未变 schema 会 SKIP | 待生成轮确认（以 71.json 存在为准） |
| U8 | `TutorVisualCommitRepository` 是否应"仓储盖章" renderer/font 版本（不接受调用方入参）属裁定；本文只登记 | 待裁定 |
| U9 | 旧（≤70）`.smbk` 快照在 71 下不可裁剪，属既有 fail-closed 的延续；是否要兼容未定 | 待裁定 |

---

## 6. 来源

**仓内（本会话实读，`path:line`）**

- 版本/注册：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139,242,322,340,409`
- 生成物：`core/database/build/generated/ksp/debug/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase_Impl.kt:264,540-543,698-699,797-813`
- 导出：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json`（`version=70`、`identityHash=11a8a701bae767a8a3c5bc810e4743a4`、90 entities / 2 views、`setupQueries` 两条）
- 69→70 迁移：`core/database/src/main/kotlin/.../VisualSnapshotMigration_69_70.kt:15-33,37-53,111-114`
- 实体/身份：`.../entity/TutorVisualEntities.kt:107-125,160-170`；`.../TutorVisualIdentity.kt:44,71-81,89-100`
- DAO/DB：`.../dao/TutorVisualDao.kt:83-95,298-315,367-397,443-470`；`.../RoomStudyDatabase.kt:2169-2210`
- 仓储/恢复：`core/data/.../visual/TutorVisualCommitRepository.kt:81-82,183-190,422-455`；`.../VisualSnapshotRestore.kt:223-234`；`.../TutorVisualCommit.kt:339-361`
- 备份耦合：`core/data/.../backup/SmbkSelectionPlan.kt:40,43,537-554`；`.../BackupSnapshotTrimmer.kt:267-284`
- 测试/门：`core/database/src/test/.../ExportedSchemaContractTest.kt:33-40,67-114`；`.../KernelWave0SchemaContractTest.kt:33-56`；`.../TutorVisualSchemaContractTest.kt:182,238-248,394-402`；`core/database/src/androidTest/.../FullMigrationMatrixInstrumentedTest.kt:36-52`；`.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:57-96,227-263`；`.../ExportedSchemaTestDatabase.kt:37-52`；`core/data/src/test/.../SmbkSelectionPlanTest.kt:45-55,611`
- 触发器先例：`.../RoomLibrarySearchStore.kt:342-380`；`.../LibrarySearchFtsMigration.kt:117-133`；`.../dao/LibraryFtsSearchDao.kt:246-250`
- 构建配置：`core/database/build.gradle.kts`（`androidx.room3` 插件 + `room3 { schemaDirectory(...) }`）；`gradle/libs.versions.toml:12,77-79,125`（room3 3.0.0、KSP 2.3.10、AGP 9.3.0）

**外部（本会话实跑）**

- AndroidX 源码（`raw.githubusercontent.com/androidx/androidx/androidx-main`）：
  - `room3/room3-compiler/src/main/kotlin/androidx/room3/vo/SchemaIdentityKey.kt:28-46`
  - `.../vo/Database.kt:66-72,99-120`
  - `.../vo/Entity.kt:47-57`、`Property.kt:58-67`、`PrimaryKey.kt:45-50`、`Index.kt:43-49`、`ForeignKey.kt:30-37`、`DatabaseView.kt:53-55`
  - `room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomConnectionManager.kt:189-205,255-279,290-301,361-378`
  - `room3/room3-runtime/src/commonMain/kotlin/androidx/room3/util/TableInfo.kt:224-252,292-299`
  - `room3/room3-gradle-plugin/src/main/java/androidx/room3/gradle/RoomArgumentProvider.kt:35-49`、`RoomSchemaCopyTask.kt:38-58`、`RoomExtension.kt:272-286`、`integration/AndroidPluginIntegration.kt:97,234-246`
  - `room3/room3-testing/src/androidMain/kotlin/androidx/room3/testing/MigrationTestHelper.android.kt:80-94,183-208`
- room3 3.0.0 字节码（本会话 `javap`）：`~/.gradle/caches/modules-2/files-2.1/androidx.room3/room3-compiler/3.0.0/.../room3-compiler-3.0.0.jar`；`.../room3-runtime-android/3.0.0/.../room3-runtime.aar`
- SQLite 官方文档：
  - `https://www.sqlite.org/lang_transaction.html`（CREATE/DROP 是 write statements，可在事务内）
  - `https://www.sqlite.org/pragma.html#pragma_foreign_keys`（事务内 no-op）
  - `https://www.sqlite.org/lang_createindex.html`（UNIQUE、IF NOT EXISTS 同名 no-op）
  - `https://www.sqlite.org/lang_altertable.html`（12 步改表法要求先关外键）
  - `https://www.sqlite.org/lang_createtrigger.html`（BEFORE/AFTER、RAISE(ABORT)）
  - `https://www.sqlite.org/lang_vacuum.html`（事务内 VACUUM 失败）
- Android 官方文档镜像：`https://developer.android.google.cn/training/data-storage/room/migrating-db-versions?hl=en`（"Room exports … into a JSON file at compile time"；`schemaDirectory`；`MigrationTestHelper` 读导出 schema 并 `runMigrationsAndValidate`）；`developer.android.com` 的同一页面本轮连接超时未取到（同主机其他路径 200）
- GitHub 元数据：`gh api repos/{androidx/androidx,MatrixDev/Roomigrant,AdamMc331/mastering-room-migrations,nick-bennett/room-schema-parser-plugin,ntsk/room-schema-docs-gradle-plugin,cashapp/sqldelight,moploe/room-plugin-poc}`；README 经 `gh api repos/<r>/readme`

**本轮实跑命令（可重跑）**

```bash
# 1) 生成物与 JSON（只读）
grep -n "RoomOpenDelegate(70" core/database/build/generated/ksp/debug/kotlin/.../StudyDatabase_Impl.kt
python - <<'PY'  # 读 70.json：version/identityHash/entities/views/setupQueries
import json; d=json.load(open("core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json",encoding="utf-8"))["database"]
print(d["version"], d["identityHash"], len(d["entities"]), len(d["views"]), d["setupQueries"])
PY

# 2) 字节码（room3 3.0.0）
javap -p -c androidx/room3/vo/SchemaIdentityKey.class        # hash() -> md5Hex(toString())
javap -p -c androidx/room3/vo/Database.class                # identityHash = appendSorted(entities)+appendSorted(views)
javap -p -c androidx/room3/BaseRoomConnectionManager.class   # BEGIN EXCLUSIVE / room_master_table / onValidateSchema

# 3) identityHash 反算尝试（192 组组合，0 命中）
python - <<'PY'
# 公式取自上述字节码；对 70.json 组合 7 个参数位，输出 computed vs expected
PY
# 结果：computed=a83c23e117f6e7b908b6a59aede0d960, expected=11a8a701bae767a8a3c5bc810e4743a4, match=False

# 4) SQLite 探针（P1-P12，3.50.4 / :memory:）
python - <<'PY'
# BEGIN EXCLUSIVE + DROP/CREATE UNIQUE INDEX、同名 IF NOT EXISTS、PRAGMA foreign_keys、
# CREATE TRIGGER、RAISE(ABORT)、BEFORE UPDATE OF、VACUUM、ROLLBACK
PY

# 5) 任务名（只读 daemon 日志）
grep -o ":[A-Za-z0-9:_-]*ksp[A-Za-z0-9:_-]*" .gradle/daemon/9.6.1/daemon-61580.out.log | sort -u
grep -n "copyRoomSchemas" .gradle/daemon/9.6.1/daemon-61580.out.log
```

> 探针脚本未落仓（`build/` 为忽略目录；本会话按安全钩子要求不直接写脚本文件）。
> 每条探针的 SQL 与结果已在 §2.5 逐条列出，T3 可按表重跑。
