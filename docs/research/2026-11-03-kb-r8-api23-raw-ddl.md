# API 23 上 `RoomKnowledgeImportMaintenance.executeDdl` 报 read-only 的根因与兼容做法（KB-R8）

> 日期：2026-11-03。范围：**只出研究，不改代码**。
> 结论一句话：**这不是 Room 的 `isReadOnly` 没传对，而是 API 23 自带 SQLite 3.8.10.2 把
> `CREATE INDEX IF NOT EXISTS`（索引已存在时的 no-op）判成 read-only 语句，Android 框架据此申请
> 只读连接，随后 `SQLiteConnection.throwIfStatementForbidden` 拒绝执行；Room 3 在该驱动路径上对
> `isReadOnly` 的传参本就是空操作。**修复必须让 DDL 自身"确实是写"，而不能靠切换 API 或传布尔值。
>
> 证据分级：`[一手]` = 官方文档/源码原文；`[推断]` = 由一手事实推出、未在设备上直接观测；
> `[待验证]` = 单一来源或未核对。

## 1 事实

### 1.1 现场与调用链（仓库事实）

- 依赖：`room3 = "3.0.0"`、`sqlite = "2.7.0"`（`gradle/libs.versions.toml:12,14`），
  `core:database` 的 `minSdk = 23`（`core/database/build.gradle.kts:13`），
  驱动为 `AndroidSQLiteDriver`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:426,438`）。
- 失败点：`RoomKnowledgeImportMaintenance.ensureFeatureIndexes`（同目录 `RoomKnowledgeImportMaintenance.kt:35-39`）
  → `executeDdl`（`:108-110`，`connection.usePrepared(sql) { it.step() }`）→
  `RawConnectionAccess.kt:29-32` 的 `withRawConnection(isReadOnly = false)`。
- 设备栈（API 23，来自实际运行记录）：
  `SQLiteConnection.throwIfStatementForbidden` → `SQLiteConnection.execute:548` →
  `SQLiteSession.execute:619` → `SQLiteStatement.execute:44` →
  `FrameworkSQLiteStatement.execute` → `SupportSQLiteStatement$OtherSQLiteStatement.step` →
  `RoomKnowledgeImportMaintenanceKt.executeDdl:109` →
  `PassthroughConnectionPool.useConnection:88` → `RoomConnectionManager.useConnection` →
  `RoomDatabase.useConnection` → `RawConnectionAccessKt.withRawConnection:32`。
  异常文案：`Cannot execute this statement because it might modify the database but the connection is read-only.`

### 1.2 框架的 read-only 语义

1. **打开标志与 `isReadOnly()`**：`OPEN_READWRITE = 0x0`、`OPEN_READONLY = 0x1`
   （`SQLiteDatabase.java` `android-6.0.1_r81`，第 214/206 行附近），
   `isReadOnlyLocked()` 只看 `openFlags & OPEN_READ_MASK == OPEN_READONLY`，
   **文件权限、WAL 目录、磁盘满都不会让 `isReadOnly()` 变 true**——它们要么让打开失败，要么在写入时
   抛 Native 的 `SQLITE_READONLY`/`attempt to write a readonly database`，与本处的消息不同。
   `[一手]` https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r81/core/java/android/database/sqlite/SQLiteDatabase.java
   ；标志文档 https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#OPEN_READONLY
   - `SQLITE_OPEN_READONLY` 只在框架带 `OPEN_READONLY` 打开时由 native 层映射；文件权限不足或
     目录不可写导致的是**打开失败 / 写入时的 `SQLITE_READONLY` / `SQLITE_READONLY_DIRECTORY`**，
     与本处"连接被标只读"是两条不同的故障路径（本条即用户看到的框架消息）。
     `[一手]` https://sqlite.org/rescode.html
   - WAL 目录（`-wal`/`-shm`）不可写会让 WAL 相关操作失败，但同样不改变 `isReadOnly()`；
     只读库上 WAL 直接被禁用（见下条）。
2. **WAL 与只读库**：`enableWriteAheadLogging()` 对 `isReadOnlyLocked()` 直接返回 false
   （同文件 1935-1937 行）；`ENABLE_WRITE_AHEAD_LOGGING` 对只读库被忽略。
   `[一手]` 同上文件 + https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#isReadOnly()
3. **单条语句的 read-only 判定**：`SQLiteProgram` 构造时用 `SQLiteStatementInfo.readOnly`
   填 `mReadOnly`（来自 `sqlite3_stmt_readonly`），执行时
   `getConnectionFlags() = mDatabase.getThreadDefaultConnectionFlags(mReadOnly)`；
   `getThreadDefaultConnectionFlags(true)` 加上 `CONNECTION_FLAG_READ_ONLY`。
   `[一手]` https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r81/core/java/android/database/sqlite/SQLiteProgram.java
   （`mReadOnly` 赋值、`getConnectionFlags()`）
4. **连接标记与拦截**：`SQLiteConnectionPool.finishAcquireConnectionLocked` 用
   `readOnly = (connectionFlags & CONNECTION_FLAG_READ_ONLY) != 0` 调
   `connection.setOnlyAllowReadOnlyOperations(readOnly)`；
   `SQLiteConnection.execute` 先 `throwIfStatementForbidden(statement)`，条件为
   `mOnlyAllowReadOnlyOperations && !statement.mReadOnly` → 抛上述异常。
   `[一手]` https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r81/core/java/android/database/sqlite/SQLiteConnectionPool.java
   、https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r81/core/java/android/database/sqlite/SQLiteConnection.java
   （`throwIfStatementForbidden`、`nativeIsReadOnly`）
5. **事务内连接会被整段复用**：`SQLiteSession.acquireConnection` 在 `mConnectionUseCount > 0` 时直接复用
   `Transaction` 上已取的连接，连接上的 `mOnlyAllowReadOnlyOperations` 只在取连接时设置一次。
   `[一手]` https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r81/core/java/android/database/sqlite/SQLiteSession.java
   → 写事务内执行 DDL 天然不会命中该拦截。

### 1.3 Room 3.0.0 在 API 23 上根本不消费 `isReadOnly`

- `RoomDatabase.useConnection(isReadOnly, block)` 只把布尔转交 `RoomConnectionManager.useConnection`；
  `RoomConnectionManager` 构造时若 `sqliteDriver.hasConnectionPool()` 为 true，就创建
  **`PassthroughConnectionPool`**；`AndroidSQLiteDriver.hasConnectionPool()` 返回 `true`
  （已用 javap 读 room3-runtime-android 3.0.0 / sqlite-framework-android 2.7.0 的字节码）。
- `PassthroughConnectionPool.useConnection(boolean, ...)` 的字节码里**没有读取该 boolean**，
  即 `isReadOnly = true/false` 走同一连接。`AndroidSQLiteDriver.open(fileName)` 也没有只读参数，
  只用 `SQLiteDatabase.openOrCreateDatabase(fileName, null)`。
- 因此调用方显式 `isReadOnly = false` 是**装饰性参数**；真正决定"这条语句能不能写"的是
  §1.2 第 3-4 步的框架逐语句判定。仓库在 `core/data/.../AndroidBackupRepository.kt:1665` 的注释
  ("`withRawConnection(isReadOnly=true)` 仍是同一条可写连接") 与此一致。
  `[一手]` 字节码证据见 `androidx.room3:room3-runtime:3.0.0`、`androidx.sqlite:sqlite-framework:2.7.0`；
  Room 3 的 `useWriterConnection`/`useReaderConnection` 也只是转调
  `useConnection(false/true)`（字节码 `iconst_0`/`iconst_1`），不改变上述结论。

### 1.4 为什么只有 API 23：SQLite 3.8.10.2 的 `sqlite3_stmt_readonly`

- 各 API 自带 SQLite 版本（AOSP 分支 `dist/sqlite3.h` 的 `SQLITE_VERSION`，逐分支核对）：

  | Android | API | SQLite | 有 `sqlite3ForceNotReadOnly` |
  |---|---|---|---|
  | 6.0 | 23 | 3.8.10.2 | 否 |
  | 7.0 | 24 | 3.9.2 | 否 |
  | 8.0 | 26 | 3.18.2 | 否 |
  | 9 | 28 | 3.22.0 | 否 |
  | 10 | 29 | 3.22.0 | 否 |
  | 11 | 30 | 3.28.0 | 否 |
  | 12 | 31 | 3.32.2 | 否 |
  | 13 | 33 | 3.32.2 | 否 |
  | 14 | 34 | 3.39.2 | 是 |

  来源（一手）：https://android.googlesource.com/platform/external/sqlite/+/android-6.0.1_r81/dist/sqlite3.h
  、`.../android-14.0.0_r1/dist/sqlite3.h`；其余分支同理（`android-7.0.0_r1`、`android-8.0.0_r1`、
  `android-9.0.0_r1`、`android-10.0.0_r1`、`android-11.0.0_r1`、`android-12.0.0_r1`、`android-13.0.0_r1`）。
- `sqlite3_stmt_readonly` 的定义：返回 true 当且仅当语句**不直接修改数据库文件**；并明确
  "`CREATE TABLE IF NOT EXISTS` 在表已存在时是只读 no-op，但 `sqlite3_stmt_readonly()` 仍返回 false"
  ——这是**新版**行为。 `[一手]` https://sqlite.org/c3ref/stmt_readonly.html
- **旧版不是这样**：3.8.10.2 的 `sqlite3CreateIndex` 在"索引已存在 + IF NOT EXISTS"分支里
  只调 `sqlite3CodeVerifySchema()` 后 `goto exit_create_index`，发生在任何
  `sqlite3BeginWriteOperation()` 之前，VDBE 里没有带写标志的 `OP_Transaction`，
  `resolveP2Values()` 里 `p->readOnly` 保持 1 → `sqlite3_stmt_readonly()` 返回 **true**。
  `[一手]` https://github.com/sqlite/sqlite/blob/version-3.8.10.2/src/build.c
  （`sqlite3CreateIndex` 的 `ifNotExist` 分支）、同版本 `src/vdbeaux.c` `resolveP2Values`
- 同样地，3.8.10.2 的 `sqlite3DropIndex` 在 `DROP INDEX IF EXISTS` 且索引**不存在**时，
  也在 `sqlite3BeginWriteOperation()` 之前 `goto exit_drop_index` → 该 no-op DROP 同样是 readOnly=1。
  `[一手]` 同文件 `sqlite3DropIndex`
- 3.39.2 在**同一类分支**加了 `sqlite3ForceNotReadOnly(pParse)`；该函数注释原文：
  "used by CREATE TABLE IF NOT EXISTS and similar if the table already exists, so that the prepared
  statement for CREATE TABLE IF NOT EXISTS will return false for sqlite3_stmt_readonly() even if that
  statement is a read-only no-op."
  4 处调用分别位于 `sqlite3StartTable`（CREATE TABLE IF NOT EXISTS）、`sqlite3DropTable`、
  `sqlite3CreateIndex`、`sqlite3DropIndex` —— 即 CREATE/DROP × TABLE/INDEX 四种 no-op 全部覆盖。
  `[一手]` https://github.com/sqlite/sqlite/blob/version-3.39.2/src/build.c
- 逐版本对比：`sqlite3ForceNotReadOnly` 在 **3.35.0 不存在、3.36.0 已存在**（同一 `src/build.c` 对比），
  即 **SQLite < 3.36 的 Android（API ≤ 33）都具备这个触发条件**；API 34 的 3.39.2 已修。3.36.0 的
  官方 release log 未单列此条 `[一手]` https://sqlite.org/releaselog/3_36_0.html —— 结论以源码为准。
- 由此的完整因果（前 3 步一手，末步 `[推断]`）：
  1) API 23 上"索引已存在"的 `CREATE INDEX IF NOT EXISTS` 被 SQLite 判为只读 no-op；
  2) 框架在 `SQLiteProgram` 处以该判定申请 `CONNECTION_FLAG_READ_ONLY`，把连接标成只读；
  3) 同一 SQL 在真正执行的那条连接上被重新 prepare，若该连接看到的 schema 状态/判定为可写
     （`mReadOnly = false`），`throwIfStatementForbidden` 立刻抛错。
  仓库的 DDL 恰好是这种"必然是 no-op"的形态（索引由 Room schema 在 `onCreate`/迁移里建好），
  所以 `ensureFeatureIndexes()` 在 install 起点**第一次**调用就可能 100% 命中。

### 1.5 已知 issue / 修复版本检索

- Room 3 release notes（3.0.0 → 3.1.0-alpha03，2026-10-21）**没有** read-only 连接或
  "raw connection 执行 DDL" 的修复条目；3.1.0-alpha03 新增的 `setPreparedStatementCacheSize`
  是针对 Room 自己的语句缓存，与框架连接只读策略无关。
  `[一手]` https://developer.android.com/jetpack/androidx/releases/room3
- `androidx.sqlite` release notes 未检索到对应条目 `[待验证]`
  https://developer.android.com/jetpack/androidx/releases/sqlite
- Issue Tracker（Room 组件 413107）针对
  "read-only / might modify the database / CREATE INDEX IF NOT EXISTS" 的定向检索**零命中**
  （多次查询、多引擎；零命中不等于不存在）。
  https://issuetracker.google.com/components/413107
- 唯一相邻的公开信息：`BundledSQLiteDriver` 的连接池默认 "4 read-only + 1 read-write"
  （issue 528792471）——说明 AndroidX 本身就把只读连接做成常态，但那是另一种驱动。
  `[一手，单一来源]` https://issuetracker.google.com/issues/528792471
- 官方 KMP 文档明确建议用 `BundledSQLiteDriver`，理由是"避免平台 SQLite 实现之间的不一致"，
  与本问题（平台 SQLite 版本差异）指向同一类风险。
  `[一手]` https://developer.android.com/kotlin/multiplatform/room

## 2 候选方案与可行性表

| 方案 | 机制 | 可行性 | 依据 / 说明 |
|---|---|---|---|
| (a) 改用 `SupportSQLiteDatabase.execSQL` 走普通写连接 | 以为 `execSQL` 会走另一条"可写"路径 | **不可行** | API 23 的 `SQLiteDatabase.execSQL` 最终仍是 `new SQLiteStatement(...).executeUpdateDelete()`，`getConnectionFlags()` 依旧来自 `SQLiteProgram.mReadOnly`，no-op 判定不变。Room 3 默认也不再暴露 `openHelper`（需 `room3-sqlite-wrapper`）。`[一手]` `SQLiteDatabase.java` 的 `executeSql`；https://developer.android.com/jetpack/androidx/releases/room3 |
| (b) `allowMainThreadQueries` | 以为是主线程/权限问题 | **无关** | 该开关只跳过主线程断言（`assertNotMainThread`），不触碰连接只读标志；本异常来自 `mOnlyAllowReadOnlyOperations`。 |
| (c) API<26 换另一条"建索引路径" | 想绕开框架逐语句判定 | **部分可行，且只有一种绕法** | 不存在"绕过判定"的 SQL API；有效做法是**把 no-op 变成真写**：先查目录，缺失才 `CREATE INDEX`。或改用 `BundledSQLiteDriver`（打包 SQLite ≥3.36，`sqlite3ForceNotReadOnly` 生效），但改变运行时、增体积。 |
| (d) 把 DDL 前移到 openHelper 的 onCreate/onUpgrade | 想借 schema 创建时机 | **不可行（作为自愈手段）** | Room schema 本就在 `onCreate`/迁移里建这两条索引；`onCreate` 之后再 ensure，索引已存在 → 仍是 no-op；`onUpgrade` 只在版本变化时跑，无法承担"崩溃后每次 install 自愈"。Room 3 也没有 `openHelper`（只有 `Callback.onCreate/onOpen(SQLiteConnection)`），回调里执行同一 SQL 仍走同一框架路径。 |
| (e) **目录门控：先查 `sqlite_master`，缺失才执行 DDL**（推荐） | 让语句在执行/准备时**确实是写** | **可行，最小改动** | 索引缺失时 `CREATE INDEX` 会发写事务 → `stmt_readonly=false` → 申请可写连接，与 API 版本无关；SQL 保留 `IF NOT EXISTS` 兜并发/重入。`[一手]` SQLite `CREATE INDEX` 语法 https://sqlite.org/lang_createindex.html |
| (f) **把 DDL 包进写事务**（`immediateTransaction`，推荐配套） | 事务连接在 BEGIN 时按可写 flags 取得，整段复用 | **可行** | `SQLiteSession` 在事务期间复用同一连接，`mOnlyAllowReadOnlyOperations` 只在取连接时按 `readOnly=false` 设置，事务内任何语句都不再被拦截；同时给多条 DDL 原子性。`[一手]` `SQLiteSession.java`；`SupportSQLiteStatement$TransactionSQLiteStatement` 把 `BEGIN IMMEDIATE` 映射到 `SupportSQLiteDatabase.beginTransactionNonExclusive()`（字节码）。 |
| (g) 换 `BundledSQLiteDriver`（可选，治本但改运行时） | 打包 SQLite ≥3.36 | **可行但成本高** | 需核对 bundled 版本、APK 体积、minSdk（23 可）、`sqlite-bundled` 依赖；官方推荐它正是为避免平台 SQLite 差异。`[待验证]`（未核对 2.7.x 打包的具体 SQLite 版本） |

**不要做的事**：继续依赖 `withRawConnection(isReadOnly = false)` 来"保证可写"——在该驱动路径上它没有语义（§1.3）。

## 3 推荐落法（含代码位置建议）

### 3.1 目标形态

在 `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt` 内：

1. 增加一个**目录读取**：只读 `sqlite_master`，取表上的索引名。
   ```kotlin
   private suspend fun PooledConnection.indexNames(table: String): Set<String> {
       val names = mutableSetOf<String>()
       usePrepared("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?") { st ->
           st.bindText(1, table)
           while (st.step()) names += st.getText(0)
       }
       return names
   }
   ```
   用 `sqlite_master` 而非 `PRAGMA index_list`：后者在旧版本上列集/含义有差异（`origin`/`partial` 是后加的），
   `sqlite_master` 从 3.x 起稳定。`[一手]` https://sqlite.org/schematab.html
2. `ensureFeatureIndexes()`：读一次目录，**只对缺失的索引执行原 DDL**；DDL 常量保留
   `CREATE INDEX IF NOT EXISTS`（兜并发/重入）。
3. 同窗口内用写事务包裹（`useWriterConnection { it.immediateTransaction { ... } }`）：
   ```kotlin
   database.useWriterConnection { transactor ->
       transactor.immediateTransaction {
           val present = transactor.indexNames(TABLE)
           FEATURE_INDEX_ENSURE_DDL.forEach { (name, ddl) ->
               if (name !in present) transactor.executeDdl(ddl)
           }
       }
   }
   ```
   - `useWriterConnection` 是 Room 3 的**公开扩展**（`RoomDatabaseKt__RoomDatabaseKt`，已核 3.0.0 字节码），
     可替代现在的 `@SuppressLint("RestrictedApi")` `useConnection` 包装（`RawConnectionAccess.kt`）——
     建议**另开一次小改**做这个收敛，不和本修复混在一起。
   - `immediateTransaction` 是 `Transactor` 的公开扩展（`TransactorKt`，3.0.0 已有）。
   - `withDeferredFeatureIndexes` 的 `DROP` 侧同样处理：只在**存在**时执行
     `DROP INDEX IF EXISTS`；`finally` 的恢复逻辑复用同一 `ensureFeatureIndexes()`。

### 3.2 为什么这样改（每一项消灭的具体失败）

| 改动 | 消灭的失败 |
|---|---|
| 目录门控 | API ≤ 33 上"索引已存在 → no-op DDL 被 `sqlite3_stmt_readonly` 判只读 → 只读连接拒绝执行"的确定性崩溃（当前 API 23 上 install 第一步即失败） |
| `immediateTransaction` 包裹 | 两条 DDL 之间进程被杀导致"只建了一条索引"的半状态；并且即使某条 DDL 仍被 SQLite 判成只读，事务连接也不会被标只读（兜底） |
| 保留 `IF NOT EXISTS` | 检查与执行之间的重入/并发（install 之外的手工 ensure、未来多入口） |
| 不动 `PRAGMA` 调优 | `PRAGMA synchronous/cache_size` 是连接级、不动数据库文件，`sqlite3_stmt_readonly=true` 也不会被 `throwIfStatementForbidden` 拒绝（语句自身被判只读，与连接标记一致），无需改；且 `PRAGMA` 不能随便塞进事务里。 |

### 3.3 崩溃安全（API 23）

- SQLite 的 DDL 是事务性的：`CREATE/DROP INDEX` 在事务内执行，回滚即撤销
  `[一手]` https://sqlite.org/lang_transaction.html 。
- API 23 的 `beginTransactionNonExclusive()` = `BEGIN IMMEDIATE`，WAL 下读者不被阻塞；
  Room 的 `immediateTransaction` 正是映射到它（`SupportSQLiteStatement$TransactionSQLiteStatement` 字节码）。
  `[一手]` https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#beginTransactionNonExclusive()
- 事务内 DDL 会让框架语句缓存按 schema 变化失效，下一次 prepare 自动重编（API 23 `isCacheable`
  只缓存 UPDATE/SELECT，DDL 本就不进缓存）`[一手]` `SQLiteConnection.java` `isCacheable`。
- 注意 KD-51：`withDeferredFeatureIndexes` 的窗口很长，**不要把导入主体塞进本事务**；只在
  ensure/drop 这几条 DDL 上开短事务。

## 4 双机验证判据

矩阵：本机已有 AVD `api23_smoke`（`system-images/android-23/default/x86_64`，SQLite 3.8.10.2）
+ `kb_test_device`（`system-images/android-34/google_apis/x86_64`，SQLite 3.39.2）。
两者分别覆盖"无 `sqlite3ForceNotReadOnly`"与"有"的两侧。

新增用例（放 `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/`）：

`RoomImportDdlCompatibilityInstrumentedTest`：

| 用例 | 判据 | 修复前预期 |
|---|---|---|
| `ensureFeatureIndexesSucceedsWhenIndexesAlreadyExist` | 建库（Room schema 已建索引）后调用 `ensureKnowledgeSearchFeatureIndexes()` 不抛异常；`SELECT count(*) FROM sqlite_master WHERE type='index' AND name IN (两条名字)` = 2 | **API23 失败**（本异常），API34 通过 |
| `ensureFeatureIndexesIsIdempotentAcrossThreeCalls` | 连调 3 次均成功；`PRAGMA index_info` 的列名与 schema 一致 | API23 失败 |
| `deferredWindowDropsThenRestoresBothIndexes` | 窗口内 `sqlite_master` 查不到两条；退出后 = 2 | API23 失败（恢复阶段） |
| `deferredWindowRestoresIndexesWhenBlockThrows` | block 抛 `IllegalStateException` 后仍 = 2（finally 路径） | API23 失败 |
| `ensureAfterSimulatedCrashWindowRepairsMissingIndex` | 手工 `DROP INDEX` 模拟崩溃残留 → ensure → = 2 | API23 失败 |
| `ddlRunsInsideWriteTransaction` | 在修复实现的 block 内断言 `transactor.inTransaction() == true`（钉住机制，不钉 SQLite 版本行为） | 新增用例，修复后通过 |

执行：

```bash
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.RoomImportDdlCompatibilityInstrumentedTest
# 再跑既有回归（安装器端到端）
./gradlew :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgeBaseInstallerInstrumentedTest
```

通过判据（两条都要满足）：

1. **API 23**：上述用例全绿；logcat 中 `grep -c "might modify the database but the connection is read-only"` = 0。
2. **API 34**：同一批用例全绿（修复不得引入回归）。
3. 记录实际引擎版本：`SELECT sqlite_version()` 在 API23 应为 `3.8.10.2`、API34 为 `3.39.2`
   （仅记录/软断言；OEM 机可能不同）。

`BundledKnowledgeBaseInstallerInstrumentedTest.installsEverySupportedSubjectWithGroundedAtomsAndIsIdempotent`
在 API23 上是当前可复现的端到端失败样本，修复后必须转绿；API34 上必须保持绿。

## 5 未验证项

1. **最后一环是推断**：已证实"SQLite 3.8.10.2 把该 no-op DDL 判只读"与"框架据此标记只读连接并拦截可写语句"，
   但未在设备上抓取执行瞬间 `PreparedStatement.mReadOnly` 的值（需要临时加日志/反射或 patch framework）。
2. `sqlite3ForceNotReadOnly` 引入于 3.36.0 是**源码逐版本对比**结论；3.36.0 官方 release log 未列此条。
3. API 25 / 27 / 32 的 SQLite 版本未逐一核对；设备实际版本还可能随系统更新/OEM 变化（表中为 AOSP 分支值）。
4. (a) "`execSQL` 同样失败" 是基于 `SQLiteDatabase.executeSql` 源码的推断，未在 API23 实测。
5. (g) `BundledSQLiteDriver` 的实际 SQLite 版本、APK 体积增量、minSdk 23 行为均未实测。
6. Room 3.1.0-alpha03 的 `setPreparedStatementCacheSize` 与本问题的关系未验证（本文判断为无关）。
7. Issue Tracker 检索为"零命中"；零命中不代表上游没有该缺陷（检索词/索引覆盖有限）。
