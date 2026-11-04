# KB R7：首启导入性能（SQLite 批量写入 / 索引 / Room 3 / JSON 解析）事实与优化清单

> **性质**：外部研究 + 本仓源码实读。只写本文件；未改源码、未跑 Gradle / 设备 / JVM 基准。
> **本仓基线（沿用已冻结读数，本文未重测）**：v26 包 **65,021 材料 / 3,280 原子节点 / 40,841 别名**；首启全量导入 **47,504 / 51,775 / 51,646 ms**（`docs/research/2026-11-02-kb-r4-progressive-import.md:4`）；分段 **JSON 解析 20.7–21.2 s、材料/绑定残差 20.8–25.2 s、索引 5.4–5.9 s**（`docs/kb-batch29-2026-10-31.md:46-64`、`docs/kb-acceptance-2026-10-17.md:824`）。两阶段改造后 **G2 `materialsCompleteMs=68,408 ms`（N=1）**、**G1 `firstSubjectReadyMs=8,345 ms`（N=1）**（`docs/kb-batch33-2026-11-02.md:48,94`；原始 `build/agent-batch33/progressive/device/progressive-report.json`）。
> **方法**：优先官方参考手册（sqlite.org、developer.android.com、androidx 源码、AOSP 源码）与可复核基准；每个关键数字给原文与 URL。**单一来源一律标「待验证」**；本仓事实逐条读当前源码，不转述旧报告。**取页说明**：本轮 `developer.android.com` 直连多次超时，Android 文档实际读的是 `developer.android.google.cn` 同页镜像；`android.googlesource.com` 直连被拦截，AOSP/androidx 源码经 `github.com/aosp-mirror` 与 `github.com/androidx` 镜像读取（路径与内容同源）。
> **结论级别**：§1 为外部原文 + 本仓实读；§2 为按收益/风险的工程判断；§3 为**建议**（含推断数字，明确标注）；§4 为未验证项。本文不声称任何改动已完成。

---

## 0. 结论摘要

1. **WAL 下 `synchronous=NORMAL` 已是 Android 的默认值**：AOSP `db_wal_sync_mode=NORMAL`，`resolveSyncMode()` 在 WAL 且未显式指定时直接取它。本仓当前显式设 `NORMAL` 是**正确但近乎无收益**的安全修复；把它改成 `OFF` 才会把"OS 崩溃/断电可损坏整库"引回来（`RoomKnowledgeImportMaintenance.kt:52`）。
2. **`PRAGMA temp_store` 在 Android 上不可调**：AOSP 编译 SQLite 时写死 `SQLITE_TEMP_STORE=3`，官方表规定该宏为 3 时 TEMP 一律进内存；`PRAGMA temp_store` 因此是 no-op，也不能把建索引的排序内存赶去磁盘。
3. **事务数量才是批量写入的主要敌人**，不是 `INSERT` 条数：SQLite FAQ 原文 "SQLite will easily do 50,000 or more INSERT statements per second … But it will only do a few dozen transactions per second."（2024-11-19 更新仍保留该结论）。
4. **Room 3 的 `@Insert(List)` / `@Upsert(List)` 已经各自包在一条 IMMEDIATE 事务里**（androidx `performSuspending(__db, false, true)` → `internalPerform` 的 `inTransaction=true` 分支）。所以材料 65k 行不是"逐行隐式事务"，而是**一条超长事务**；剩余成本来自 65k 次 bind/step、材料表 4 条索引的逐行维护、约 110 MB 正文写入 WAL，而不是提交次数。
5. **先插入后建索引在机制上更便宜**：每插入一行都要维护该表上的每条索引 B-tree；批量 `CREATE INDEX` 只需一次排序 + 一次构建。官方参考手册没有给基准数字，SQLite 官方论坛的原话与机制推导见 §1.3（标「论坛来源」）。
6. **JSON 20.7 s 的大头不是"kotlinx 慢"，而是解析形状**：本仓用 `readText()` 得到完整 `String`，再 `parseToJsonElement()` 建整棵 `JsonElement` 树，再手工走树。官方文档明确 `parseToJsonElement()` 是 "parse the input into a JSON element tree without decoding or deserializing it"；改成 `@Serializable` DTO + `Json.decodeFromStream()` 可省掉整份 String 驻留与整棵树（官方 API 存在，标 `experimental-general`）。
7. **上一轮两阶段为什么 G2 反而 68.4 s**：现有日志**没有逐科/逐卷时间**，"Phase A 回调发生在第一科还是四科之后"无法确认；可确认的是 Phase B 仍要把 26 个侧车全部解析并写 65k 材料，**总工作量没有下降**。两阶段只改变"何时可用"，不改变"总共要做多少事"。这是设计边界，不是两阶段本身无效（§3.6，含未验证假设）。
8. **本仓可安全落地的最高收益三项**：① 流式/类型化解析（去掉 String + JsonElement 双份内存）；② 按侧车"解析一卷→写一卷→释放一卷"；③ 首装期延后材料表的两条非唯一二级索引 + 材料写入按 1k 行级事务。**不可碰**：`synchronous=OFF`、`journal_mode=MEMORY/OFF`、跳 fsync、延迟提交、`locking_mode=EXCLUSIVE`。

---

## 1. 事实

### 1.1 SQLite：`synchronous` / `journal_mode` / `cache_size` / `temp_store` / `mmap_size`

来源：[PRAGMA Statements](https://www.sqlite.org/pragma.html)；[Write-Ahead Logging](https://www.sqlite.org/wal.html)；[SQLite FAQ Q19](https://www.sqlite.org/faq.html#q19)。以下引号内为原文。

#### `PRAGMA synchronous`

| 值 | 官方原文（逐字） | 耐久性含义 |
|---|---|---|
| `OFF (0)` | "With synchronous OFF (0), SQLite continues without syncing as soon as it has handed data off to the operating system. If the application running SQLite crashes, the data will be safe, but the database **might become corrupted** if the operating system crashes or the computer loses power before that data has been written to non-volatile storage. On the other hand, commits can be much faster with synchronous OFF." | **进程崩溃安全；OS 崩溃/断电可损坏整库** |
| `NORMAL (1)` | "WAL mode is safe from corruption with synchronous=NORMAL … WAL mode is always consistent with synchronous=NORMAL, but WAL mode does lose durability. A transaction committed in WAL mode with synchronous=NORMAL might roll back following a power loss or system crash. **Transactions are durable across application crashes regardless of the synchronous setting or journal mode.**" | WAL 下不腐蚀；断电可能回滚最近提交 |
| `FULL (2)` | "When synchronous is FULL (2), the SQLite database engine will use the xSync method of the VFS to ensure that all content is safely written to the disk surface prior to continuing. This ensures that an operating system crash or power failure will not corrupt the database." | WAL 下 ACID 全满足（含 durable） |
| `EXTRA (3)` | "EXTRA is no different from FULL in WAL mode." | WAL 下与 FULL 等价 |

原文还明确推荐："The synchronous=NORMAL setting provides the best balance between performance and safety for most applications running in WAL mode."

**关键边界（常被误读）**：`OFF` 并不因"应用崩溃"而丢数据；它的问题是**OS 崩溃/断电**。所以"导入可重跑、坏了重装"只在**库内只有可重建内容**时成立；本仓同库还存学生错题/掌握度，重装不等于可恢复。这是 §2.2 的第一条红线。

#### `PRAGMA journal_mode`

- WAL："The WAL journaling mode is persistent; after being set it stays in effect across multiple database connections and after closing and reopening the database."（**数据库级、写进库文件**）
- `MEMORY`："The MEMORY journaling mode stores the rollback journal in volatile RAM. This saves disk I/O but at the expense of database safety and integrity. If the application using SQLite crashes in the middle of a transaction when the MEMORY journaling mode is set, then the database file will very likely go corrupt."
- `OFF`："The OFF journaling mode disables the rollback journal completely. … The OFF journaling mode disables the atomic commit and rollback capabilities of SQLite. … If the application crashes in the middle of a transaction when the OFF journaling mode is set, then the database file will very likely go corrupt."
- WAL 的性能与代价："WAL is significantly faster in most scenarios."；"Write transactions are very fast since they only involve writing the content once (versus twice for rollback-journal transactions) and because the writes are all sequential."；但 "read performance deteriorates as the WAL file grows in size since each reader must check the WAL file for the content and the time needed to check the WAL file is proportional to the size of the WAL file."
- checkpoint 默认："By default, SQLite does a checkpoint automatically when the WAL file reaches a threshold size of 1000 pages."；"A checkpoint is only able to run to completion, and reset the WAL file, if there are no other database connections using the WAL file."

#### `PRAGMA cache_size`

- "The default suggested cache size is -2000, which means the cache size is limited to 2048000 bytes of memory."
- "If the argument N is positive then the suggested cache size is set to N. If the argument N is negative, then the number of cache pages is adjusted to be a number of pages that would use approximately abs(N\*1024) bytes of memory based on the current page size."

即本仓 `-32768` = **32 MiB** 页缓存（约 8,192 个 4 KiB 页），是默认 2 MB 的 16 倍。它是**每连接**建议值；WAL 下 Room 通常 4 读 + 1 写，只有写连接被本仓显式调大。

#### `PRAGMA temp_store`

- "When temp_store is DEFAULT (0), the compile-time C preprocessor macro SQLITE_TEMP_STORE is used to determine where temporary tables and indices are stored. When temp_store is MEMORY (2) temporary tables and indices are kept as if they were in pure in-memory databases. When temp_store is FILE (1) temporary tables and indices are stored in a file."
- 宏与 pragma 的交互表最终一行：`SQLITE_TEMP_STORE=3` → **"any"** → **memory**（无论 pragma 设什么）。

**Android 事实**：AOSP SQLite 的构建文件直接写死该宏——`"-DSQLITE_TEMP_STORE=3"`，注释为 "SQLITE_TEMP_STORE=3 causes all TEMP files to go into RAM. and thats the behavior we want"（[AOSP `external/sqlite/dist/Android.bp`](https://github.com/aosp-mirror/platform_external_sqlite/blob/main/dist/Android.bp)，约 :47、:56）。因此**在 Android 上 `PRAGMA temp_store` 不改变任何行为**，建索引/排序用的临时 B-tree 一定在 RAM，低端设备只能靠减少排序量（例如延后建索引、减少索引条数）来控制内存，不能靠 `temp_store=FILE`。

#### `PRAGMA mmap_size`

- "Query or change the maximum number of bytes that are set aside for memory-mapped I/O on a single database. … If N is zero then memory mapped I/O is disabled."
- "The size of the memory-mapped I/O region cannot be changed while the memory-mapped I/O region is in active use … the mmap_size pragma may be a no-op if the prior mmap_size is non-zero and there are other SQL statements running concurrently on the same database connection."
- 编译期："**SQLITE_DEFAULT_MMAP_SIZE=N** … If N is zero, then memory mapped I/O is disabled by default."（[Compile-time options](https://www.sqlite.org/compile.html#default_mmap_size)）

官方页**没有**给"Android 默认多少"的数字；AOSP `Android.bp` 也未声明 `SQLITE_DEFAULT_MMAP_SIZE`。**待验证**：Android 上 mmap 默认是否为 0（上游默认值需读 `sqlite3.c` 宏确认，本文未做）。在证实前，不建议把 `mmap_size` 当优化项。

### 1.2 单事务 vs 分批：官方只给方向，本仓需要"短事务分块"

- FAQ Q19（原文）："By default, each INSERT statement is its own transaction. But if you surround multiple INSERT statements with BEGIN...COMMIT then all the inserts are grouped into a single transaction. The time needed to commit the transaction is amortized over all the enclosed insert statements and so the time per insert statement is greatly reduced."
- 同一答案 2024-11-19 更新："SQLite will do far more than 50K inserts/second now, and everybody uses SSD nowadays … But the gist of the answer above remains correct: Putting multiple operations inside a single transaction can improve performance dramatically by avoiding the overhead of transaction control after each individual operation."
- 官方**没有**给"每事务多少行最优"的推荐值。SQLite 只有一个写者（WAL 下 "readers do not block writers and a writer does not block readers"，写写仍串行，见 [WAL](https://www.sqlite.org/wal.html)），长事务会阻止 checkpoint 完成（§1.1 的 WAL 原文），并让读者随 WAL 变长而变慢。本仓已有独立实测：一直占住唯一写连接会让同进程其它写排到 270 s（`RoomKnowledgeImportMaintenance.kt:27-28` 的 KD-51 记录）。

**判读**：正确形态是**中等大小的短事务 + 块间让出**，不是"一条事务包 65k 行"，也不是"每行一条事务"。批大小的上限由三件事共同决定：单事务 WAL 增量、崩溃后重放/回滚时间、同进程其它写请求的等待上限。

### 1.3 索引：先插入后建索引 vs 边插边维护

**官方参考手册没有给出两者的基准对比**（`CREATE INDEX` 页只讲语法与 `IF NOT EXISTS` 的 no-op 语义：https://www.sqlite.org/lang_createindex.html）。可引用的官方资源是 SQLite 用户论坛（SQLite 项目托管，但不是参考手册）：

- Keith Medcalf："You will pay the cost of maintaining and using the index if you have an index."；建议 "perform your inserts in order in order to avoid b-tree rebalances."
- Roman："Or you can insert first, create unique indexes and vacuum."（该页没有直接称其"更快"）
- 同一论坛另一帖，Bill Wade："Often, appends are faster than inserts."；"Are your inserts in primary key order?"；"If so, an insert of ~100 records touches a couple of leaf pages … If not, it touches about 100 leaf pages and about 100 index pages."；原帖作者回报："Changing my code to do inserts in primary key order improved the performance by around 80%."
  - 来源：https://sqlite.org/forum/info/9ceedec748ecfaa91a7955f22b4322dad7749b88811e7251499a2ce9d50c6a7a 、https://sqlite.org/forum/info/96f5882ba7c8d47d

**机制推导（非原文，标「推断」）**：普通表每插一行，SQLite 要更新该行涉及的每条索引 B-tree（定位叶页、可能分裂）；`CREATE INDEX` 则对全表做一次扫描/排序后批量构建 B-tree。索引条数越多、行数越大，"边插边维护"的累计成本越高；"先插后建"把 N 次随机 B-tree 插入换成 1 次有序构建。**这是机制，不是官方基准**；本仓只能用自测数字支撑（§3.5）。

**`ANALYZE` 的官方时机**（[ANALYZE](https://www.sqlite.org/lang_analyze.html)）：

- "The ANALYZE command gathers statistics about tables and indices … the query optimizer can access the information and use it to help make better query planning choices."
- "The use of ANALYZE is never required."
- "**All applications should run "PRAGMA optimize;" after a schema change, especially after one or more CREATE INDEX statements.**"
- "Statistics gathered by ANALYZE are not updated as the content of the database changes. If the content of the database changes significantly … one should consider rerunning the ANALYZE command."
- "In most cases PRAGMA optimize will not run ANALYZE, but it will occasionally do so either for tables that have never before been analyzed, or for tables that have grown significantly since they were last analyzed."

**判读**：导入结束、索引建回之后跑**一次** `PRAGMA optimize;`（或 `ANALYZE` 指定表），不要每批都跑。

**FTS 与普通表混合**：

- FTS5 是编译期开关："if sqlite3.c is compiled using some other build system, by arranging for the `SQLITE_ENABLE_FTS5` pre-processor symbol to be defined."（[FTS5](https://www.sqlite.org/fts5.html)）
- **AOSP 的 SQLite 只开了 FTS3/FTS4，未声明 `SQLITE_ENABLE_FTS5`**（`Android.bp` 的 cflags 只有 `-DSQLITE_ENABLE_FTS3`、`-DSQLITE_ENABLE_FTS3_BACKWARDS`、`-DSQLITE_ENABLE_FTS4`）。本仓 `LibraryFtsSearchDao` 的注释也明确写 "the index is FTS4"（`core/database/.../dao/LibraryFtsSearchDao.kt:15-20`）。因此本仓不能用 FTS5 的 `bm25()`，也不应在导入路径引入 FTS5 依赖。
- FTS 索引的合并代价（FTS5 原文，FTS4 同类机制）："This command merges all individual b-trees that currently make up the full-text index into a single large b-tree structure. … Because it reorganizes the entire FTS index, the optimize command can take a long time to run."
- 本仓知识导入**不写** FTS4 表（`knowledge_*` 与 `library_search_fts` 无交叉；FTS 只服务错题库检索）。所以"FTS/普通表混合"不是本次 65k 材料导入的瓶颈；但若未来把材料标题并入 FTS，`room_fts_content_sync` 触发器会把每行变成 FTS 的 delete + insert，写放大明显。

### 1.4 Android / Room 3 特有开销

#### 默认 journal mode

Room 3 源码（`androidx.room3.RoomDatabase.Builder`）：

> "Sets the journal mode for this database. … **The default value is JournalMode.WRITE_AHEAD_LOGGING.**"
> "for JournalMode.TRUNCATE a single connection is used, while for JournalMode.WRITE_AHEAD_LOGGING multiple connections are used, four reader and one writer."

来源：https://developer.android.com/reference/kotlin/androidx/room3/RoomDatabase.Builder 、https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomDatabase.kt

本仓 `StudyDatabaseFactory.open()` **没有调用** `setJournalMode`（`core/database/.../StudyDatabase.kt:345-427` 实读），因此走默认 WAL。

#### Android 框架在 WAL 下把 `synchronous` 设成什么

AOSP `SQLiteDatabaseConfiguration.resolveSyncMode()`：

```java
if (isWalEnabledInternal()) {
    if (isLegacyCompatibilityWalEnabled()) {
        return SQLiteCompatibilityWalFlags.getWALSyncMode();
    } else {
        return SQLiteGlobal.getWALSyncMode();
    }
} else {
    return SQLiteGlobal.getDefaultSyncMode();
}
```

而 `SQLiteGlobal.getWALSyncMode()` 读 `com.android.internal.R.string.db_wal_sync_mode`；AOSP `core/res/res/values/config.xml` 的值是：

```xml
<string name="db_default_sync_mode" translatable="false">FULL</string>
...
<string name="db_wal_sync_mode" translatable="false">NORMAL</string>
```

来源：https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/database/sqlite/SQLiteDatabaseConfiguration.java 、https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/res/res/values/config.xml（约 :2535、:2547）

**结论（强推断）**：在未显式指定 syncMode 的普通 Android 设备上，WAL 连接的 `synchronous` **已经是 NORMAL**。本仓 `withImportTuning` 显式设 `NORMAL` 不产生性能收益，只保证"不被别处改成 OFF"；它的价值是**安全回归保护**，不是加速。OEM 若改过该 config 则另说（§4 未验证）。

#### Room 3 的事务 API

- `withWriteTransaction`：官方 KDoc 原文 "Acquire a WRITE connection and start a `IMMEDIATE` transaction"，等价于 `roomDatabase.useWriterConnection { it.withTransaction(IMMEDIATE) { block() } }`；`withReadTransaction` 对应 `DEFERRED`。两者都是 suspend，块内可嵌套事务。
- 同一页的连接 KDoc："**A connection is a limited resource**"，并建议 "avoid executing long-running computations" 在连接块内。
  - 来源：https://developer.android.com/reference/kotlin/androidx/room3/package-summary
- Room 3 迁移指南：旧 `withTransaction` / `runInTransaction` → "`withWriteTransaction` / `withReadTransaction`"；"A `SQLiteDriver` is now *required* to build a `RoomDatabase`"；"All database operations are now Coroutine APIs based."
  - 来源：https://developer.android.com/training/data-storage/room/migration-2-to-3

#### Room 3 的 `@Insert` / `@Upsert` 到底怎么执行

参考手册只说 "When an @Insert function is called, Room inserts each passed entity instance into the corresponding database table."，**没有**事务/性能说明（https://developer.android.com/training/data-storage/room/accessing-data）。真正的事实要从生成代码读：

1. 生成实现把整个 List 交给 `performSuspending(__db, false, true)`（本仓 `KnowledgeTeachingMaterialDao_Impl.kt:155-157` 实读；第三参 `true`）。
2. androidx `DBUtil.performSuspending(db, isReadOnly, inTransaction)` 在 `inTransaction=true` 时进入 `internalPerform`，写路径固定用 `Transactor.SQLiteTransactionType.IMMEDIATE`（https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/util/DBUtil.kt:42-67）。
3. `@Upsert` 的逐行实现是 **INSERT → 捕获唯一约束异常 → UPDATE**："When the insertion fails due to a unique constraint conflict (i.e. primary key conflict), it will perform an update."（https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/EntityUpsertAdapter.kt:24-81）

**因此**：

- `@Insert(List)` / `@Upsert(List)` **不是** 65k 条隐式事务，而是**一条 IMMEDIATE 大事务**；提交次数不是瓶颈。
- 首装空库时 `@Upsert` 不会走 UPDATE 分支，成本≈`@Insert`；但**重复导入/版本升级**时，冲突行会走"异常 + UPDATE"，成本明显高于 `@Insert(ABORT)` 或"先比后写"。
- 单条超长事务的代价是 WAL 持续增长、checkpoint 无法完成、同进程其它写被阻塞、失败时整段回滚——不是提交太慢。

### 1.5 JSON 解析：20 秒的合理优化

#### 本仓当前的解析形状（源码实读）

- `BundledKnowledgePackResources.load()` 一次把所有包（2 个主包 + 26 个侧车）全部解析完才返回（`:83-99`）。
- 每个文件走 `readClasspathResource()`：`stream.bufferedReader().use { it.readText() }`（`:157-162`）→ **完整 String**。
- 主包走 `ReviewedKnowledgePackJsonCodec.decode(rawJson)`，第一行就是 `json.parseToJsonElement(rawJson)`（`:174-175`）；侧车同样（`ReviewedTeachingMaterialSidecarJsonCodec.kt:102-103`）→ **完整 JsonElement 树**。
- 之后手工走树、逐字段转换、逐对象校验。

所以单文件峰值内存 ≈ 整份 `String`（UTF-16；中文内容约为文件 UTF-8 字节的 2/3，纯 ASCII 可接近 2 倍）+ `JsonElement` 树（每个对象/数组/字符串都是独立 Kotlin 对象）+ 最终 DTO。官方对 `parseToJsonElement` 的定义是："parse the input into a JSON element tree **without decoding or deserializing it**"（https://kotlinlang.org/docs/serialization-json-elements.html）。

#### 流式 API 是否存在

存在，官方文档专门有一页 "Serializing JSON with I/O sources"：

- `Json.decodeFromStream<T>(InputStream)` / `encodeToStream`（JVM）
- `decodeFromSource` / `decodeFromBufferedSource`（kotlinx-io / Okio）
- `decodeSourceToSequence` / `decodeBufferedSourceToSequence`：懒解析多个 JSON 值
- 页面顶部标注 `<primary-label ref="experimental-general"/>`，即 API 稳定性需自行评估。
- 来源：https://kotlinlang.org/docs/serialization-json-io-sources.html

kotlinx.serialization 仓库里有专门的流式基准 `benchmark/src/jmh/.../json/TwitterFeedStreamBenchmark.kt`，说明 `decodeFromStream` 与 `decodeFromString` 是两条被维护的路径（https://github.com/Kotlin/kotlinx.serialization/blob/master/benchmark/src/jmh/kotlin/kotlinx/benchmarks/json/TwitterFeedStreamBenchmark.kt）。**但仓库没有发布该基准的结果数字**。

#### 跨库吞吐基准（都不是 4.5 MB / 不是 Android 真机）

| 来源 | 载荷 | 结果 | 限制 |
|---|---|---|---|
| [ZacSweers/json-serialization-benchmarking（Android 结果）](https://github.com/ZacSweers/json-serialization-benchmarking/blob/main/docs/results/android.md) | `largesample_minified.json` = **173,671 B** | 读（string）：kserializer 5.77 ms、moshi codegen 6.67 ms、gson autovalue 4.85 ms；读（buffered）：moshi codegen 5.74 ms（kserializer 无 buffered 条目）。结论句 "Fastest reader: Moshi" | 跑在 2018 MacBook Pro / JVM，**不是 Android 真机**；作者是 Moshi 维护者（利益相关） |
| [teaddict：Kotlin JSON performance](https://tech.teaddict.net/kotlin/programming/json/2025/02/15/kotlin-json-performance/) | 100 / 1k / 10k 行 | 10k 行：Jackson 1435.56 ms、kotlinx 1541.89 ms、Moshi 1888.05 ms | 只给 Docker/JDK，无 CPU/RAM；载荷按行数不按字节 |
| [GLD SerializerBenchmark](https://leo-gan.github.io/GLD.SerializerBenchmark/theory/401/kotlin-kotlinx-json-vs-moshi/) | 440 B，n=1 | Moshi codegen 21k ops/s vs kotlinx 12k ops/s（encode+decode） | 极小载荷，无硬件/日期 |

**判读**：

- 三份基准**互相矛盾**（小载荷 Moshi 快、中载荷 kotlinx 快、大载荷 Jackson 快），且**没有一份**在 4.5 MB / 119 MB 级、Android 真机上测过。**不能**用它们推断本仓 "换库能省 X 秒"。
- 可外推的只有机制：`org.json`/`JsonElement` 这类"先建树"的路径，内存与分配次数随节点数线性增长；类型化流式解码是单遍、无中间树。**收益大小必须在本仓数据集上实测**（§4）。

### 1.6 本仓当前的真实形状（逐条实读）

| 事实 | 证据 |
|---|---|
| 解析在**任何写库之前**完成；解析不在 pragma/索引窗口内 | `BundledKnowledgeBaseInstaller.kt:56-66` |
| 一次 `load()` 解析 2 主包 + 26 侧车；侧车合计 **114.31 MB**（25 卷 + 2020 的 1 卷），主包 4.33 MB；资源目录实测总计 **119 MB** | `BundledKnowledgePackResources.kt:44-152`；`du -ch core/data/src/main/resources/knowledge/...` |
| 解析路径 = `readText()` + `parseToJsonElement()` + 手工走树 | `BundledKnowledgePackResources.kt:157-162,174-175`；`ReviewedTeachingMaterialSidecarJsonCodec.kt:102-103` |
| 写序：sources → nodes → node_source_bindings → relations → **materials → material_bindings** → retire → **逐科 rebuildSubject** | `RoomKnowledgeContentReconciler.kt:53-104` |
| 材料 upsert 是一次 `@Upsert(List)`（65,021 行），生成代码是一条 IMMEDIATE 事务 | `RoomKnowledgeContentReconciler.kt:83-86`；`KnowledgeTeachingMaterialDao_Impl.kt:155-157` |
| `knowledge_teaching_material` 有 **4 条索引**（2 条 UNIQUE：`stable_code`、`content_fingerprint`；2 条普通：`source_id`、`(subject, material_type)`）；`material_node_binding` 有 2 条普通索引 | `core/database/schemas/.../73.json` |
| 特征表 `knowledge_search_feature` **547k 行**（四科 546,839），2 条二级索引在导入窗口内被 DROP，结束后补建 | `RoomKnowledgeImportMaintenance.kt:21-26,81-105`；`docs/kb-batch29-2026-10-31.md:58-64` |
| 特征插入按 300 行/语句（900 绑定参数，低于旧 999 上限），64 节点/事务 | `KnowledgeSearchFeatureBatchInsert.kt:26-29`；`KnowledgeSearchIndexBuilder.kt:136-170,227` |
| 导入期 `synchronous = NORMAL`、`cache_size = -32768`；退出恢复原值 | `RoomKnowledgeImportMaintenance.kt:48-75` |
| `PRAGMA temp_store` 未碰（正确，见 §1.1） | 同上类注释 `:17-18` |
| 读材料为空时**静默返回 emptyList**（无"材料尚未导入"区分） | `RoomTutorTeachingReferenceRepository.kt:22-31` |
| 上一轮两阶段：Phase A 逐科重建索引、Phase B 逐卷解析/写材料；G1 8,345 ms、G2 68,408 ms（N=1）；**逐科/逐卷时间未记录** | `docs/research/2026-11-02-kb-b33-progressive-import-prereg.md:§1.1`；`build/agent-batch33/import/final.md:§0` |

---

## 2. 优化清单（按收益/风险排序）

排序口径：先看"能否动到 20 s 量级的两段（解析、材料写）"，再看风险；**任何会降低崩溃安全的项一律排到反例区，不进建议清单**。

### 2.1 可落地清单

| # | 措施 | 机制/依据 | 预期收益 | 风险 | 判定 |
|---:|---|---|---|---|---|
| 1 | **类型化流式解码**：`@Serializable` DTO + `Json.decodeFromStream`，替代 `readText()+parseToJsonElement()+手工走树` | 不再驻留整份 String 与 JsonElement 树；单遍解析（§1.5） | 解析 20.7–21.2 s → **5–9 s（推断）**；峰值内存显著下降 | 需要 DTO 与现有严格校验逐字段对齐；官方 API 标 experimental | **可安全落地**（先做等价性测试） |
| 2 | **逐卷解析→写入→释放**：26 个侧车不再一次性全部装入 `residentPacks` | 当前 `load()` 先解析完所有包（§1.6）；逐卷可把峰值内存从"全量对象图"降到"单卷对象图" | 解析与写库可重叠；OOM 风险下降；总 CPU 不变 | 续跑/幂等必须按卷锚定 | **可安全落地** |
| 3 | **材料写入按 1,000–2,000 行/事务**，卷边界推进进度 | §1.2；当前是一条 65k 行大事务 | 材料段 20.8–25.2 s → **10–16 s（推断）**；WAL 峰值下降 | 失败重跑要幂等 | **可安全落地** |
| 4 | **首装期延后材料表的 2 条非唯一二级索引**（`source_id`、`(subject, material_type)`），结束补建，并加幂等自愈 | §1.3 机制；本仓已有特征索引的同款窗口 | 65k 行 × 2 条索引的维护成本消失；**推断 2–5 s** | 窗口内相关查询走全扫；崩溃需自愈 | **可安全落地**（照抄 `withDeferredFeatureIndexes` 的自愈形态） |
| 5 | **首装空表用 `@Insert(ABORT)` 而非 `@Upsert`**；非空才走 upsert | `EntityUpsertAdapter` 的异常+UPDATE 路径（§1.4） | 首装省掉异常分支检查；重复导入收益更大 | 需要"表是否为空"的原子判定 | **可安全落地** |
| 6 | **导入结束跑一次 `PRAGMA optimize`** | 官方明确 "after … one or more CREATE INDEX statements"（§1.3） | 后续查询计划更稳；导入本身不变 | 极低 | **可安全落地** |
| 7 | 保留 `synchronous=NORMAL` + `cache_size=-32768` | NORMAL 是 Android WAL 默认（§1.4）；32 MiB 是默认 16 倍（§1.1） | 基本无加速；防止回归到 OFF | 无 | **可安全落地（安全项，不是性能项）** |
| 8 | 解析并行（2 个 worker 解析不同侧车） | 解析是 CPU 密集、当前单线程；设备 4–8 核 | 墙钟时间最多 ~2×（推断） | 峰值内存 ×2；调度与顺序写入需改造 | **待验证**（先测单卷内存） |
| 9 | 把 `knowledge_search_feature` 行数从 547k 降下来（去重/上限） | 每节点 ~148 条特征；写量与索引量线性 | 索引段与 WAL 同步下降 | 改变召回声学，需金标回归 | **待验证**（不属于纯性能改动） |
| 10 | `mmap_size` 调大 | 官方无 Android 默认值证据（§1.1） | 未知 | 32 位地址空间/内存压力 | **暂不做**（证据不足） |
| 11 | `temp_store=MEMORY` | Android 已 `SQLITE_TEMP_STORE=3`（§1.1） | **0** | 无 | **不做（no-op）** |

### 2.2 反例：会破坏崩溃安全 / 数据完整性的"优化"

| 反例 | 为什么危险（原文/机制） |
|---|---|
| `PRAGMA synchronous=OFF` | "the database might become corrupted if the operating system crashes or the computer loses power"（§1.1）。本仓库同时存学生错题/掌握度，"重装可重建"不成立 |
| `PRAGMA journal_mode=MEMORY` 或 `OFF` | "the database file will very likely go corrupt"（§1.1）；且 WAL 是持久设置，误设会跨重启保留 |
| 跳过 fsync / 延迟提交（应用层先回"已保存"再异步 commit） | 破坏"提交即持久"的语义；WAL+NORMAL 本来就可能丢最近提交，再延迟只是把可丢窗口扩大。官方："might roll back following a power loss or system crash" |
| `PRAGMA locking_mode=EXCLUSIVE` 长时间持有 | 官方用途之一是 "prevent other processes from accessing the database file"；导入期会让同进程/其它进程读被挡，且 WAL 下 checkpoint 无法完成（§1.1） |
| 为提速关闭外键（`PRAGMA foreign_keys=OFF`） | 本仓 8 张表以 RESTRICT 引用节点/材料（`RoomKnowledgeContentReconciler.kt:20-27` 的注释）；关闭后悬空绑定会被静默写入，破坏的是数据完整性而非崩溃安全 |
| 导入中途 `VACUUM` | 需要约 2× 库体积的临时空间与整库重写；低端设备上可能直接 ENOSPC |
| 移除索引后崩溃且无自愈 | 本仓已有 `ensureFeatureIndexes()` 在每次 `install()` 起点补建（`RoomKnowledgeImportMaintenance.kt:34-39`）；新增任何"延后建索引"都必须复制这条自愈路径 |
| 一条事务包住"解析 + 全部写库" | 事务持有写连接数分钟，阻塞同进程其它写（KD-51 实测 270 s），并把 WAL 推到不可控大小；官方建议避免在连接块内做长计算 |

---

## 3. 对本仓的具体建议

> 以下均为**建议**，未实施。数字除标注"实测"外均为**推断**，必须按 §3.5 的协议实测后才能写入验收。

### 3.1 阶段划分：只把"材料正文"推到第二阶段

**边界原则**：第一阶段写"查询语义需要的最小集"，但**不要**把 sources/bindings/relations 也推后——它们总量小（节点 3,280、绑定/关系同量级），推后只会换来"引用缺失/前置关系缺失"的一致性缺口，收益接近 0。真正的大块只有两张材料表（约 110 MB 正文）。

| 表 / 索引 | 阶段 | 理由 | 若延后会破坏什么 |
|---|---|---|---|
| `knowledge_source` | A | 引用展示需要 | 节点有源但显示不出出处 |
| `knowledge_node` | A | 召回主表 | 无法召回 |
| `knowledge_node_source_binding` | A | 引用定位 | 同上 |
| `knowledge_node_relation` | A | 前置/后继扩展 | 讲题缺前置链 |
| `knowledge_search_feature` | A（按科） | 召回打分 | 该科不可查 |
| `knowledge_search_feature` 两条二级索引 | A 结束（现有窗口） | 批量构建（§1.3） | 窗口内查询变慢，不丢数据 |
| `knowledge_search_index_state`（每科锚点） | A（每科最后） | 逐科 READY | 无法判断某科是否完整 |
| `knowledge_install_progress`（新增，若采用） | A/B 同事务推进 | 续跑锚点 | 崩溃后续跑会重复/跳过 |
| `knowledge_teaching_material` | **B** | 仅"打开讲解"时读 | 讲解暂时为空 |
| `knowledge_teaching_material_node_binding` | **B** | 与材料同批 | 材料无法归属 |
| `content_install_state`（完成戳） | **B 最后** | 语义是"整包完成" | 提前写会让下次启动命中快路径、跳过 B |
| `library_search_fts` / 学生数据表 | 不碰 | 与知识导入无关 | — |

**第二阶段能延后到什么程度**：可以延后到"所有学科 READY 之后、进程存活期间"，但**不能跨进程无锚点**。必须满足：

1. Phase B 的每一卷写入与"卷进度"在**同一事务**提交；卷边界才推进 `next_sidecar_index`。
2. 完成戳只在全部材料 + 绑定写完后写；Phase A 绝不写完成戳。
3. **查询侧必须能区分"没有材料"与"材料尚未导入"**。当前 `RoomTutorTeachingReferenceRepository.kt:31` 对空列表直接返回 `emptyList()`，会把"未导入"显示成"没有讲解"——这是两阶段**最需要先补的用户可见一致性缺口**。落地方案：Phase A 写"materials_ready=false"，Phase B 完成写 true；读路径在 false 时返回"材料准备中"状态，而不是空。
4. 外键保持开启：Phase B 只引用 Phase A 已提交的节点；RESTRICT 会挡住悬空绑定，失败即回滚该卷。

### 3.2 pragma：只动有证据的，其余保持

| pragma | 当前值（实读） | 建议 | 依据 | 预期 |
|---|---|---|---|---|
| `journal_mode` | 未设置 → Room 3 默认 WAL（§1.4） | **保持 WAL**，不要为导入切换 | WAL 持久、读写并发、写更快（§1.1） | 不变 |
| `synchronous` | 导入期显式 `NORMAL`（`RoomKnowledgeImportMaintenance.kt:52`） | **保持 NORMAL**；禁止 OFF | Android WAL 默认即 NORMAL；NORMAL 不腐蚀（§1.1/§1.4） | 安全项，≈0 加速 |
| `cache_size` | 导入期 `-32768`（32 MiB） | 保持；若设备内存 ≥6 GB 可试 `-65536`，但必须实测 | 默认仅 2 MB（§1.1） | 未知，可能 0–1 s |
| `temp_store` | 未设置 | **不要设置** | Android 编译期 `SQLITE_TEMP_STORE=3`，永远内存（§1.1） | 0 |
| `mmap_size` | 未设置 | **不要设置**（证据不足） | 官方无 Android 默认值；32 位风险 | 未知 |
| `wal_autocheckpoint` | 默认 1000 页 | 保持默认；导入结束可显式 `PRAGMA wal_checkpoint(TRUNCATE)` 一次并计时 | 官方默认 1000 页；WAL 越大读越慢（§1.1） | 降低导入后首查长尾 |
| `PRAGMA optimize` | 未执行 | **导入结束执行一次** | 官方明确建索引后应跑（§1.3） | 后续查询计划收益 |

### 3.3 批大小

| 写入 | 建议批 | 理由 |
|---|---|---|
| 特征行 | 保持 **300 行/语句**（900 参数）、**64 节点/事务** | 已低于旧 999 参数上限；改大没有证据收益（§1.6） |
| 材料行 | **1,000–2,000 行/事务**（约 1.7–3.4 MB 正文）或 **50–100 ms/事务**，取先到者 | 65k 行单事务的 WAL 峰值与回滚成本过高；1k 行仍能把提交摊销到可忽略（§1.2） |
| 材料绑定 | 500–1,000 行/事务；`IN` 列表继续用 900 参数上限 | 与现有 `MAX_IN_PARAMETERS` 一致 |
| 节点/来源/关系 | 总量小，单事务可接受；为统一形态可用 500–1,000 行/事务 | 3,280 节点，不是瓶颈 |
| 进度行 | 与所在数据块**同一事务** | 崩溃后不跳卷/不重卷 |

### 3.4 索引时机

1. **特征表**：沿用现有"导入窗口内 DROP 两条二级索引 → 结束 `CREATE INDEX IF NOT EXISTS` 补建"（`FEATURE_INDEX_ENSURE_DDL`）。这是本仓已验证的形态，不改成"边插边维护"。
2. **材料表**（新增建议）：首装窗口内 DROP 两条**非唯一**索引 `index_knowledge_teaching_material_source_id`、`index_knowledge_teaching_material_subject_material_type`，Phase B 结束后补建；**保留**两条 UNIQUE 索引（`stable_code`、`content_fingerprint`），因为它们是幂等/去重语义的一部分。补建必须幂等，并在 `install()` 起点自愈（照抄特征索引的做法）。
3. **`ANALYZE`**：全部建索引完成后跑**一次** `PRAGMA optimize;`，不要每卷跑。
4. **不要**在导入中 `VACUUM`、不要切 `locking_mode`、不要关外键。

### 3.5 预期数字（全部为推断，必须实测）

| 段 | 现状（实测） | 建议后目标（推断） | 推断依据 |
|---|---:|---:|---|
| JSON 解析（全量） | 20.7–21.2 s | **5–9 s** | 不再驻留整份 String + JsonElement 树；官方流式 API 单遍；无 4.5 MB 基准，倍数保守取 2.3–4× |
| 材料/绑定写入 | 20.8–25.2 s | **10–16 s** | 1k 行/事务 + 2 条索引延后 + 空表 `@Insert`；WAL 正文约 110 MB 的下限仍在 |
| 特征索引 | 5.4–5.9 s | **4.5–5.9 s** | 已有 DROP 窗口；仅受 I/O 竞争影响 |
| 全量合计 | 51.6–68.4 s | **25–35 s（乐观）/ 35–45 s（保守）** | 三段相加；未计 GC/调度抖动 |
| 首查 G1 | 8,345 ms（实测 N=1） | **3–5 s** | Phase A 只建**第一科**索引并**在该科 READY 时立即回调**；解析只读 4.33 MB 主包（当前 8.3 s 的偏高风险见 §3.6） |

**验收口径（沿用 R4，不改）**：

- 首查 p95 ≤ 5 s，硬上限 10 s；全量 p95 ≤ 45 s，硬上限 60 s；导入期查询 p95 ≤ 250 ms、max ≤ 1 s；0 次静默空结果。
- **N ≥ 20 次冷装**才允许报 p95；3 次只能报 median/max（R4 §0 第 8 条）。崩溃注入 ≥ 20 次（每卷边界 + 索引窗口 + 完成戳两步之间）。
- 每次必须记录：APK sha、内容包 sha、设备型号/API/ABI、WAL 峰值、各段 `Log.d`。

### 3.6 上一轮 G2=68,408 ms 的复盘（假设，未验证）

已确认的事实：

- 预注册把 Phase A 写成"4 个科目逐个判断 → 每科 READY → **第一科 READY 时**回调 `markReady()`"（`docs/research/2026-11-02-kb-b33-progressive-import-prereg.md:§1.1 第 4 条`）。**日志没有公开逐科时间**，因此"回调发生在第一科之后还是四科之后"**无法从现有证据确认**；G1=8,345 ms 明显高于"主包解析 + 单科索引"的推断值（约 3–4 s），最可能的解释是回调前仍完成了不止一科的索引（**未验证**）。
- Phase B 仍要解析 26 个侧车（每个 `readText()+parseToJsonElement`），并写 65k 材料；这两段的**总工作量与改造前相同**。两阶段只改变"何时可用"，不改变"总共多少活"，所以 G2 不会因为分阶段而自动下降。
- G2=68,408 ms 是任务给定观测区间 **51.6–68.4 s** 的上沿，N=1，且当轮新增了进度表/状态机/DAO 改动；**不能**把回归归因到单一原因。

未验证假设（按可疑度排序）：

1. 逐卷 `planMaterials` 重新构建 `nodesById/sourcesById` 映射 26 次（改前整包只建一次，注释记载该优化把 64,973 条材料的这段从 33,650 ms 降下来）。逐卷后若仍每卷重建，会重新引入这部分成本。
2. 逐卷写入的 `@Upsert(List)` 每卷一条事务，卷间进度更新也在同一事务，导致 WAL 频繁增长/checkpoint 竞争。
3. Phase A 与 Phase B 之间没有让出写连接，首查与 Phase B 写竞争。
4. 设备/内容包与基线不同（v26 vs 当轮内容），不可直接横向比较。

**下一步应先做**：在**不改语义**的前提下，只加分段计时（parse per sidecar、material plan、upsert、binding、checkpoint），用 N=1 定位 Phase B 的 60 s 花在哪，再决定改哪一层。不要在未定位前叠加更多机制。

---

## 4. 未验证项

1. **G1/G2 都是 N=1**：68,408 ms / 8,345 ms 不能当 p95，也不能证明回归或改善。预注册要求的 ≥20 冷装 + 20 次崩溃注入均 `notRun`。
2. **Android 上 `mmap_size` 的默认值**：官方页只给"0 = 禁用"，AOSP `Android.bp` 未声明 `SQLITE_DEFAULT_MMAP_SIZE`；未读 `sqlite3.c` 确认上游默认值。**待验证**。
3. **OEM 是否覆盖 `db_wal_sync_mode`**：本文只验证了 AOSP main 的 `config.xml`；厂商定制 ROM 可能不同。若某设备实际为 FULL，则显式 `NORMAL` 会产生真实收益；反之无收益。**待验证**（需在目标设备上读 `PRAGMA synchronous`）。
4. **"先插后建索引"的量化收益**：官方参考手册无基准；论坛只有方向性陈述；本仓只有特征表 5.4–5.9 s 的间接证据。材料表延后 2 条索引能省多少，**未测**。
5. **JSON 库在 4.5 MB / 119 MB、Android 真机上的吞吐**：三份公开基准的载荷（173 KB、440 B、按行数）与硬件都与本仓不同，且互相矛盾。**没有可用数字**；§3.5 的 5–9 s 是机制推断，不是基准外推。
6. **`decodeFromStream` 的稳定性**：官方文档标 `experimental-general`；在 Room/Android 的 Kotlin 版本上是否有 API 兼容问题，未验证。
7. **流式解析对本仓对象图峰值内存的实际降幅**：未做 heap dump / `Debug.getNativeHeapAllocatedSize` 对比。
8. **逐卷解析能否与写库重叠**：受 `installMutex` 与单写连接限制，实际并行度未测。
9. **材料索引延后期间的查询退化幅度**：哪些读路径会走全扫、p95 会退化多少，未测。
10. **`PRAGMA optimize` 在本仓查询上的收益**：未做导入前后 `EXPLAIN QUERY PLAN` 对比。
11. **WAL 峰值与 checkpoint 行为**：导入期 WAL 最大多大、是否触发自动 checkpoint、导入后首查是否受 WAL 长度拖累，未测。
12. **FTS5 在目标 API 级别是否可用**：本文只验证了 AOSP main 的 `Android.bp` 未开 FTS5；不同 API/厂商镜像可能不同。本仓当前用 FTS4，不依赖该结论。

---

## 参考来源（按首次引用顺序）

- https://www.sqlite.org/pragma.html （synchronous / journal_mode / cache_size / temp_store / mmap_size / locking_mode）
- https://www.sqlite.org/wal.html
- https://www.sqlite.org/faq.html#q19
- https://www.sqlite.org/lang_createindex.html
- https://www.sqlite.org/lang_analyze.html
- https://www.sqlite.org/fts5.html
- https://www.sqlite.org/compile.html#default_mmap_size
- https://sqlite.org/forum/info/9ceedec748ecfaa91a7955f22b4322dad7749b88811e7251499a2ce9d50c6a7a
- https://sqlite.org/forum/info/96f5882ba7c8d47d
- https://developer.android.com/reference/kotlin/androidx/room3/RoomDatabase.Builder
- https://developer.android.com/reference/kotlin/androidx/room3/package-summary
- https://developer.android.com/training/data-storage/room/migration-2-to-3
- https://developer.android.com/training/data-storage/room/accessing-data
- https://developer.android.com/jetpack/androidx/releases/room3
- https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/RoomDatabase.kt
- https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/util/DBUtil.kt
- https://github.com/androidx/androidx/blob/androidx-main/room3/room3-runtime/src/commonMain/kotlin/androidx/room3/EntityUpsertAdapter.kt
- https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/database/sqlite/SQLiteDatabaseConfiguration.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/res/res/values/config.xml
- https://github.com/aosp-mirror/platform_external_sqlite/blob/main/dist/Android.bp
- https://kotlinlang.org/docs/serialization-json-elements.html
- https://kotlinlang.org/docs/serialization-json-io-sources.html
- https://github.com/Kotlin/kotlinx.serialization/blob/master/benchmark/src/jmh/kotlin/kotlinx/benchmarks/json/TwitterFeedStreamBenchmark.kt
- https://github.com/ZacSweers/json-serialization-benchmarking/blob/main/docs/results/android.md
- https://tech.teaddict.net/kotlin/programming/json/2025/02/15/kotlin-json-performance/
- https://leo-gan.github.io/GLD.SerializerBenchmark/theory/401/kotlin-kotlinx-json-vs-moshi/
