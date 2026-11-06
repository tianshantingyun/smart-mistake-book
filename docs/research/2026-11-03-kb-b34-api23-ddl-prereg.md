# KB B34 API 23 raw DDL 兼容（`CREATE/DROP INDEX` 目录门控 + `BEGIN IMMEDIATE`）：预注册（2026-11-03）

> **性质**：出数前冻结。§1–§4 写死；执行轮只允许在文末追加 §6，不得回改 §1–§4。
> **写作时点**：2026-11-04T07:57:38+0800（`date '+%Y-%m-%dT%H:%M:%S%z'` 实跑）。文件名日期 `2026-11-03` 是批次日。
> **HEAD**：`0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 实跑）。
> **上游**：`docs/research/2026-11-03-kb-r8-api23-raw-ddl.md`（下称 R8）。R8 的一手来源、版本对照与候选方案表不在此复述；本文只把 B34 的修法、判据与回滚写死。
> **本步唯一新增写盘**：本文件。未改代码/未跑 Gradle/设备；§0 的读数是本会话用 `sha256sum` / `rg` / `javap` 实读实查。

---

## 0. 写前守卫与口径

### 0.1 porcelain 守卫（写前实跑）

```bash
date '+%Y-%m-%dT%H:%M:%S%z'                   # 2026-11-04T07:57:38+0800
git rev-parse HEAD                             # 0fef943641f53560b92ce2afec4da22bf52f5e86
git status --porcelain | wc -l                 # 576
git status --porcelain | awk '{print $1}' | sort | uniq -c
#   452 ??
#   124 M
ls docs/research/2026-11-03-kb-b34-api23-ddl-prereg.md
#   No such file or directory  → 本次新建，只写一次
```

- 本臂唯一要改的生产文件 `core/database/.../RoomKnowledgeImportMaintenance.kt` 写前已是 `M`：他线把 `PRAGMA synchronous = OFF` 改成了 `NORMAL`（`git diff` 实读，仅 3 行注释/DDL 变化）。本臂在**该工作树值**上继续编辑；§4 的 pre-sha 是工作树值，不是 HEAD 值。**不得回滚**他线的 NORMAL 改动。
- 576 项脏项属他线/已写面；本预注册不触碰、不裹挟、不回滚。**不得**整树 `git checkout`、`stash` 或 `reset`。

### 0.2 现状（实读）

| 事实 | 值 | 来源 |
|---|---|---|
| Room / sqlite 版本 | `room3=3.0.0` / `sqlite=2.7.0` | `gradle/libs.versions.toml:12,14` |
| `core:database` minSdk | **23** | `core/database/build.gradle.kts:13` |
| 驱动 | `AndroidSQLiteDriver()`（生产与 `openInMemory` 都是） | `StudyDatabase.kt:426,438` |
| 失败入口 | `ensureFeatureIndexes()` → `executeDdl()` → `withRawConnection(isReadOnly=false)` | `RoomKnowledgeImportMaintenance.kt:35-39,108-110`；`RawConnectionAccess.kt:29-32` |
| 失败异常 | `Cannot execute this statement because it might modify the database but the connection is read-only.` | R8 §1.1 设备栈 |
| 两条索引 | `index_knowledge_search_feature_knowledge_node_id`（`knowledge_node_id`）、`index_knowledge_search_feature_subject_knowledge_node_id`（`subject, knowledge_node_id`） | schema `73.json` 实读；两者均非 UNIQUE |
| 当前 DDL 常量 | `FEATURE_INDEX_ENSURE_DDL` / `FEATURE_INDEX_DROP_DDL` 是 **`List<String>`**；JVM 测试按字符串 `contains(name)` 比对 schema | `RoomKnowledgeImportMaintenance.kt:95-105`；`KnowledgeSearchFeatureImportEquivalenceTest.kt:421` |
| 当前 `ensure` | 无目录查询，直接对两条 `CREATE INDEX IF NOT EXISTS` 逐条 `step()` | `:35-39` |
| 当前 `drop` | 无目录查询，直接两条 `DROP INDEX IF EXISTS` | `:81-90` |
| 当前事务 | **没有** `immediateTransaction`/`BEGIN IMMEDIATE`；DDL 各走一次短连接 | 实读 |
| API 23 / API 34 SQLite | **3.8.10.2 / 3.39.2**（R8 逐 AOSP 分支核对，本会话未在设备重测） | R8 §1.4 |
| 现有 API23 端到端失败样本 | `BundledKnowledgeBaseInstallerInstrumentedTest` 在 API23 上 install 第一步即失败（R8 记录） | R8 §4；本会话未重跑 |

### 0.3 本臂的边界

1. **只修 DDL 执行路径**，不改索引定义、不改 schema、不改 `PRAGMA` 调优、不改导入业务逻辑。
2. 不引入 `BundledSQLiteDriver`、不改 minSdk、不切 Room 版本；只用 Room 3 已有的公开 API（`useWriterConnection` / `immediateTransaction`）与 SQLite 自带的 `sqlite_master`。
3. 不把 `PRAGMA synchronous/cache_size` 塞进事务；`withImportTuning` 原样保留（R8 §3.2/§3.3）。
4. 不在本臂做"另一种驱动/另一条建索引路径"的试验；R8 §2 的 (a)/(b)/(d)/(g) 都是明确不采纳项。

---

## 1. 根因（写死）

### 1.1 API 23 的 SQLite 3.8.10.2 把 no-op DDL 判成只读

- `CREATE INDEX IF NOT EXISTS` 在**索引已存在**时，`sqlite3CreateIndex` 的 `ifNotExist` 分支只做 `sqlite3CodeVerifySchema()` 然后 `goto exit_create_index`；这个跳转发生在任何 `sqlite3BeginWriteOperation()` **之前**，所以 VDBE 里没有带写标志的 `OP_Transaction`，`resolveP2Values()` 里 `p->readOnly` 保持 1 ⇒ `sqlite3_stmt_readonly()` 返回 **true**。R8 §1.4（一手：`sqlite` `version-3.8.10.2` 的 `src/build.c`）。
- `DROP INDEX IF EXISTS` 在**索引不存在**时同理：`sqlite3DropIndex` 在 `sqlite3BeginWriteOperation()` 之前 `goto exit_drop_index`，no-op DROP 也是 readOnly=1。R8 §1.4。
- API 34 的 SQLite 3.39.2 在这些分支加了 `sqlite3ForceNotReadOnly(pParse)`，no-op 也被强制判为非只读；该函数 **3.35.0 不存在、3.36.0 已存在**（源码逐版本对比）⇒ **API ≤ 33 都具备触发条件，API 34 已修**。R8 §1.4。

### 1.2 Android 框架据此申请只读连接并拦截

1. `SQLiteProgram` 构造时用 `SQLiteStatementInfo.readOnly` 填 `mReadOnly`；执行时 `getConnectionFlags() = mDatabase.getThreadDefaultConnectionFlags(mReadOnly)`，`true` 时加 `CONNECTION_FLAG_READ_ONLY`。R8 §1.2 第 3 条（一手：`SQLiteProgram.java`）。
2. `SQLiteConnectionPool.finishAcquireConnectionLocked` 用该 flag 调 `connection.setOnlyAllowReadOnlyOperations(readOnly)`；`SQLiteConnection.execute` 先 `throwIfStatementForbidden(statement)`，条件为 `mOnlyAllowReadOnlyOperations && !statement.mReadOnly` ⇒ 抛 §0.2 的异常。R8 §1.2 第 4 条（一手：`SQLiteConnectionPool.java`、`SQLiteConnection.java`）。
3. 事务内的连接会被整段复用（`SQLiteSession.acquireConnection` 在 `mConnectionUseCount > 0` 时复用事务连接），`mOnlyAllowReadOnlyOperations` 只在取连接时设置一次 ⇒ **写事务内执行 DDL 天然不会命中该拦截**。R8 §1.2 第 5 条（一手：`SQLiteSession.java`）。

### 1.3 Room 3 在该驱动路径上不消费 `isReadOnly`

- `AndroidSQLiteDriver.hasConnectionPool()` 返回 `true` ⇒ `RoomConnectionManager` 创建 **`PassthroughConnectionPool`**；其 `useConnection(boolean, ...)` 字节码**不读取**该 boolean。`AndroidSQLiteDriver.open(fileName)` 也没有只读参数，只用 `SQLiteDatabase.openOrCreateDatabase(fileName, null)`。R8 §1.3（一手：`room3-runtime-android:3.0.0`、`sqlite-framework-android:2.7.0` 字节码）。
- 因此 `RawConnectionAccess.kt:29-32` 的 `withRawConnection(isReadOnly = false)` 是**装饰性参数**：`true/false` 走同一条连接；真正决定"这条语句能不能写"的是 §1.2 的框架逐语句判定。
- **结论**：修法不能是"把 `isReadOnly` 传对"、也不能是 `allowMainThreadQueries` 或 `execSQL`；必须让 DDL 自身在一次**真正的写事务、且语句不是 no-op** 的形态下执行。

---

## 2. 修法（写死）

### 2.1 目标形态（只改 `RoomKnowledgeImportMaintenance.kt`）

```kotlin
internal data class IndexDdl(
    val name: String,
    val createSql: String,
    val dropSql: String,
)

private const val FEATURE_INDEX_TABLE = "knowledge_search_feature"

private val FEATURE_INDEX_DDL = listOf(
    IndexDdl(
        name = "index_knowledge_search_feature_knowledge_node_id",
        createSql = "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_knowledge_node_id` " +
            "ON `knowledge_search_feature` (`knowledge_node_id`)",
        dropSql = "DROP INDEX IF EXISTS `index_knowledge_search_feature_knowledge_node_id`",
    ),
    IndexDdl(
        name = "index_knowledge_search_feature_subject_knowledge_node_id",
        createSql = "CREATE INDEX IF NOT EXISTS `index_knowledge_search_feature_subject_knowledge_node_id` " +
            "ON `knowledge_search_feature` (`subject`, `knowledge_node_id`)",
        dropSql = "DROP INDEX IF EXISTS `index_knowledge_search_feature_subject_knowledge_node_id`",
    ),
)

// 保留现有类型/语义：JVM 测试按 List<String> 比对 schema
internal val FEATURE_INDEX_ENSURE_DDL: List<String> = FEATURE_INDEX_DDL.map { it.createSql }
internal val FEATURE_INDEX_DROP_DDL: List<String> = FEATURE_INDEX_DDL.map { it.dropSql }

/** 目录读取：用 sqlite_master，不用 PRAGMA index_list（旧版本列集/含义有差异）。 */
private suspend fun PooledConnection.indexNames(table: String): Set<String> {
    val names = mutableSetOf<String>()
    usePrepared("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?") { statement ->
        statement.bindText(1, table)
        while (statement.step()) names += statement.getText(0)
    }
    return names
}

/** 执行一条无参数 DDL；Transactor 继承 PooledConnection，复用同一执行器。 */
private suspend fun PooledConnection.executeDdl(sql: String) {
    usePrepared(sql) { statement -> statement.step() }
}

/**
 * DDL 写事务：BEGIN IMMEDIATE（Room 的 immediateTransaction）。
 * 仅用于索引 ensure/drop 这几条短 DDL；不包导入主体（KD-51）。
 */
internal suspend fun <R> inIndexDdlTransaction(block: suspend (Transactor) -> R): R =
    database.useWriterConnection { transactor ->
        transactor.immediateTransaction { block(transactor) }
    }

suspend fun ensureFeatureIndexes() {
    inIndexDdlTransaction { transactor ->
        val present = transactor.indexNames(FEATURE_INDEX_TABLE)
        FEATURE_INDEX_DDL.forEach { index ->
            if (index.name !in present) transactor.executeDdl(index.createSql)
        }
    }
}

suspend fun <R> withDeferredFeatureIndexes(block: suspend () -> R): R {
    ensureFeatureIndexes()
    inIndexDdlTransaction { transactor ->
        val present = transactor.indexNames(FEATURE_INDEX_TABLE)
        FEATURE_INDEX_DDL.forEach { index ->
            if (index.name in present) transactor.executeDdl(index.dropSql)
        }
    }
    try {
        return block()
    } finally {
        ensureFeatureIndexes()
    }
}
```

- **先查 `sqlite_master`，缺失才 `CREATE`**：让语句在"索引已存在"时**根本不执行**，从源头绕开 3.8.10.2 的 no-op 只读判定；索引缺失时 `CREATE INDEX` 是真写，`stmt_readonly=false`，与 API 版本无关。
- **`BEGIN IMMEDIATE` 包裹**：`Transactor.immediateTransaction` 先取写连接再 `BEGIN IMMEDIATE`；事务期间连接被整段复用（§1.2 第 5 条），即使某条 DDL 仍被判只读，也不会被 `throwIfStatementForbidden` 拒绝；同时两条 DDL 原子（不会出现"只建了一条"的半状态）。
- **DROP 侧同样门控**：只在目录里**存在**该索引时才执行 `DROP INDEX IF EXISTS`；不存在时根本不执行，绕开 3.8.10.2 的 no-op DROP 只读判定。
- **保留 `IF NOT EXISTS` / `IF EXISTS`**：目录检查与执行之间仍有重入/并发窗口；SQL 内的 `IF ...` 是兜底，不替代目录门控。
- `inIndexDdlTransaction` 是 `internal` 测试缝：用例 6 用它断言 `transactor.inTransaction() == true`，钉住"DDL 在写事务里"这一机制，不依赖 SQLite 版本行为。

### 2.2 `SupportSQLiteStatement` 的映射（为什么 `BEGIN IMMEDIATE` 能拿到写连接）

- `androidx.room3.Transactor` 继承 `PooledConnection`；`immediateTransaction` 是 `TransactorKt` 的公开扩展（本会话 `javap` 实查：`androidx.room3.TransactorKt.immediateTransaction` 存在）。
- `androidx.sqlite.driver.SupportSQLiteStatement$TransactionSQLiteStatement` 的 `step()` 字节码在 `TransactionOperation.IMMEDIATE` 分支调用 `SupportSQLiteDatabase.beginTransactionNonExclusive()`（本会话 `javap -c` 实查；同 R8 §3.3）。
- API 23 的 `beginTransactionNonExclusive()` = `BEGIN IMMEDIATE`；WAL 下读者不被阻塞。R8 §3.3。

### 2.3 明确不做（每条都指回 R8 的失败/成本）

| 不做的方案 | 为什么 |
|---|---|
| 改 `withRawConnection(isReadOnly=false)` / 传对布尔 | 该驱动路径不消费 boolean（§1.3）；改了也不会改变语句判定。 |
| `execSQL` / `SupportSQLiteDatabase` 普通写 API | 最终仍按 `SQLiteProgram.mReadOnly` 申请连接；no-op 判定不变（R8 §2(a)）。 |
| `allowMainThreadQueries` | 只跳过主线程断言，与连接只读标志无关（R8 §2(b)）。 |
| 把 DDL 前移到 `onCreate/onUpgrade` | 索引本就在 schema 创建时建好；`onCreate` 之后再 ensure 仍是 no-op；`onUpgrade` 只在版本变化时跑，不能承担每次 install 自愈（R8 §2(d)）。 |
| 换 `BundledSQLiteDriver` | 可行但改运行时/体积，另立预注册；本臂只做最小修复（R8 §2(g)）。 |
| 把 `PRAGMA synchronous/cache_size` 塞进事务 | PRAGMA 是连接级设置，不应放进事务；`withImportTuning` 原样保留（R8 §3.2）。 |
| 把导入主体塞进 `immediateTransaction` | 会长时间占住唯一写连接（KD-51 实测 270 s）；只包 ensure/drop 这几条短 DDL（R8 §3.3）。 |

---

## 3. 验收（写死）

### 3.1 双机矩阵

| 项 | 冻结要求 |
|---|---|
| 设备 A | `api23_smoke`（`system-images/android-23/default/x86_64`，SQLite 3.8.10.2） |
| 设备 B | `kb_test_device`（`system-images/android-34/google_apis/x86_64`，SQLite 3.39.2） |
| 用例数 | **6**（§3.2），每台 **6/6 全绿**；合计 12 个结果 |
| 版本记录 | 用例内 `SELECT sqlite_version()`；API23 期望 `3.8.10.2`、API34 期望 `3.39.2`（软断言/仅记录，OEM 可能不同） |
| 日志 | 每台 `logcat -c` 后跑用例，结束后 `adb -s <serial> logcat -d \| grep -c "might modify the database but the connection is read-only"` = **0** |
| 既有回归 | `BundledKnowledgeBaseInstallerInstrumentedTest` 在 API23 转绿、API34 保持绿 |

### 3.2 6 个用例（`RoomImportDdlCompatibilityInstrumentedTest`）

新文件：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/RoomImportDdlCompatibilityInstrumentedTest.kt`。

| # | 用例 | 判据 | 修复前预期 |
|---:|---|---|---|
| 1 | `ensureFeatureIndexesSucceedsWhenIndexesAlreadyExist` | `Room` schema 已建两条索引；调 `store.ensureKnowledgeSearchFeatureIndexes()` 不抛异常；`sqlite_master` 两条 name 计数 = 2 | **API23 失败**（本异常）；API34 通过 |
| 2 | `ensureFeatureIndexesIsIdempotentAcrossThreeCalls` | 连调 3 次均成功；`PRAGMA index_info` 的列名/列序与 schema 一致（`knowledge_node_id`；`subject, knowledge_node_id`） | API23 失败 |
| 3 | `deferredWindowDropsThenRestoresBothIndexes` | `withDeferredKnowledgeSearchFeatureIndexes` 的 block 内 `sqlite_master` 两条 = 0；退出后 = 2 | API23 失败（恢复阶段） |
| 4 | `deferredWindowRestoresIndexesWhenBlockThrows` | block 抛 `IllegalStateException`；`assertThrows` 后 `sqlite_master` 仍 = 2（finally 路径） | API23 失败 |
| 5 | `ensureAfterSimulatedCrashWindowRepairsMissingIndex` | 手工 DROP 一条索引（模拟崩溃残留）→ `ensure` → 两条 = 2 | API23 失败 |
| 6 | `ddlRunsInsideWriteTransaction` | `maintenance.inIndexDdlTransaction { t -> assertTrue(t.inTransaction()) }` 通过（钉住 `BEGIN IMMEDIATE` 机制，不钉 SQLite 版本行为） | 新增用例，修复后通过 |

- 用例 1/2/5 只读 `sqlite_master`，不构造/改写 SQL 文本；用例 2 的 `PRAGMA index_info` 用固定索引名（常量），不拼接外部输入。
- 用例 3/4 的 block 内查询必须在**窗口内**（DROP 之后、finally ensure 之前）执行；查询走 `store.database.useWriterConnection` 的只读语句，不持有长事务。
- 用例 5 的"手工 DROP"用 `DROP INDEX IF EXISTS` 在测试自己的 `immediateTransaction` 里执行；这是**测试夹具**，不是生产路径。
- 6 个用例全部通过后，才允许跑 §3.3 的既有回归。

### 3.3 执行命令（双机各一遍）

```bash
# 设备 A / B 分别执行；ANDROID_SERIAL 选设备（若 AGP 忽略它，用下面的 am instrument 兜底）
ANDROID_SERIAL=<serial> ./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.RoomImportDdlCompatibilityInstrumentedTest

# 兜底（Gradle 已把 test APK 装上后）
adb -s <serial> shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.database.RoomImportDdlCompatibilityInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner

# 每台：清日志 → 跑 → 读零命中
adb -s <serial> logcat -c
# （跑上面的用例）
adb -s <serial> logcat -d | grep -c "might modify the database but the connection is read-only"
# 期望输出：0

# 既有端到端回归
ANDROID_SERIAL=<serial> ./gradlew :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.knowledge.BundledKnowledgeBaseInstallerInstrumentedTest
```

- 每台必须记录：`ro.product.model`、`ro.build.version.sdk`、`ro.product.cpu.abi`、`SELECT sqlite_version()`、APK sha、用例 XML 路径。
- **6/6 全绿**指测试方法级；任何 `@Ignore`/跳过都按未过处理。
- logcat 的 grep 计数必须是 **0**；出现 1 次即该设备不通过，不论用例是否绿。
- API23 的既有 `BundledKnowledgeBaseInstallerInstrumentedTest` 必须从红转绿；API34 必须保持绿。若 API34 出现新红，按回归处理，不得只报 API23。

### 3.4 修复前基线（必须记录，不得用修复后结果冒充）

- 在改代码前，先在 API23 上跑同一 6 用例 + 既有安装器用例，记录失败方法名与异常文案；预期：用例 1/2/3/4/5 与安装器用例红（3/4 在窗口入口的 `ensureFeatureIndexes()` 就失败），异常含 §0.2 的原文；用例 6 在修复前不存在，不能计入"修复前红"。
- API34 上先跑同一批，预期 6/6 绿（修复前基线）；这是"修复不得引入 API34 回归"的对照。
- 本会话**未跑**修复前基线（§5 `NOT_RUN`）；执行轮必须先跑再改。

---

## 4. 判读（写死）

### 4.1 采纳条件

**采纳 ⇔ 同时满足：**

1. API23 的 6 用例 **6/6 绿**；API34 的 6 用例 **6/6 绿**；
2. 两台设备的 `logcat` 对 `"might modify the database but the connection is read-only"` 计数均为 **0**；
3. API23 的 `BundledKnowledgeBaseInstallerInstrumentedTest` 转绿；API34 保持绿；
4. 两台设备的 `sqlite_version()` 已记录（API23 3.8.10.2 / API34 3.39.2；OEM 差异只记录，不据此改判）。

**否则回滚**（任一台任一用例红、logcat 非 0、既有回归红、或 6/6 无法计算，都按"不采纳"处理）。

### 4.2 不允许的"变绿"方式

- 把 `isReadOnly` 改成 `true`/`false` 来"试"；§1.3 已证明该参数在该驱动路径上是空操作。
- 把测试改成只断言"不抛异常"而不断言 `sqlite_master` 计数/列序。
- 在 API23 上跳过用例 1/2/5，或把 `@SdkSuppress` 加到 API23 上。
- 只跑 `assembleDebug`/JVM 测试就声称双机通过。
- 把 `logcat` 过滤成只 grep 某个 tag，导致框架异常行漏掉；必须全量 `logcat -d` 后 grep 原文。
- 用修复后的代码重跑"修复前基线"并写成修复前红。

### 4.3 报告义务（§6 至少包含）

- 写前/写后 HEAD、porcelain、§5 逐文件 pre/post sha。
- 修复前基线（API23 红的方法名/异常原文；API34 6/6 绿）。
- 修复后 API23/API34 的逐用例结果（6×2）、`sqlite_version()`、APK/设备指纹、`logcat` 计数（必须 0）。
- 既有 `BundledKnowledgeBaseInstallerInstrumentedTest` 在双机的结果。
- 用例 6 的 `inTransaction()` 证据；用例 3/4 的窗口内/外 `sqlite_master` 计数。
- 若任何用例仍红：给出完整异常栈与所在设备/引擎版本，不得汇总成"兼容性问题"。

---

## 5. 回滚（触碰清单 + pre/post sha）

### 5.1 A. 生产代码（写前 `M`：他线 OFF→NORMAL）

| # | 路径 | 预期动作 | pre-sha256（写前实测） | post-sha256 |
|---:|---|---|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeImportMaintenance.kt` | `IndexDdl` + `sqlite_master` 目录门控 + `useWriterConnection`/`immediateTransaction`；drop 侧门控；保留 `FEATURE_INDEX_*_DDL: List<String>` | `c8ee60f518f57f1c478977bfe4edf7c76cf79736766382d72246c064e1a54edb`（`M`，他线 OFF→NORMAL） | §6 |

### 5.2 B. 新增文件

| # | 路径 | 动作 |
|---:|---|---|
| 1 | `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/RoomImportDdlCompatibilityInstrumentedTest.kt` | 6 用例 |

### 5.3 C. 零触碰（本臂不改，但执行/回归会读）

| # | 路径 | pre-sha256 |
|---:|---|---|
| 1 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RawConnectionAccess.kt` | `9890d70c2e2c1db9875499fea3ee57d5afb0debe05bc98c4d06c0a41c21d4d35` |
| 2 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt` | `1d5f371378c73e8eec19815dd2e33f45328b5f1771f670eeaec4cfd44b43114c` |
| 3 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/KnowledgePort.kt` | `d18dd074df0ca0a2d9bbb018b43816e7e32adb7c966c1f0a020adf62030b8cfe` |
| 4 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstaller.kt` | `989549a0989d76bc1bd0f7c9b08d31a2ea55feef2c9eb2b1d5180fc5ddd54f1e` |
| 5 | `core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchFeatureImportEquivalenceTest.kt` | `27e1ef312fa1fcdd881fe1df54fc098e06808ce466c0c26a8cae6afd70f60c17` |
| 6 | `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/knowledge/BundledKnowledgeBaseInstallerInstrumentedTest.kt` | `a149b11537eedefa588305b333ef80d09b36098c90151cbc3feec8574504f372` |

- `FEATURE_INDEX_ENSURE_DDL` / `FEATURE_INDEX_DROP_DDL` 的**类型与字符串内容不变**，所以 #5 的 JVM 测试不需要改；新逻辑用新增的 `IndexDdl` 对列表，不改旧常量。
- `RawConnectionAccess.withRawConnection` 仍服务 `PRAGMA synchronous/cache_size`（那两条是真写、不会被判只读）；本臂不删它，也不改它的布尔参数语义。
- 不新增/修改 schema JSON、迁移、Room 版本、依赖；没有数据库版本变化。

### 5.4 写前备份 / 回滚 / 采纳

**写前备份**

1. 把 §5.1 的 1 件复制到 `build/agent-batch34/api23-ddl/backup/`，落 `sha256sum`；§5.3 的 6 件只记 sha，不备份（零触碰）。
2. 复核：§5.1 的 pre-sha 与上表相同；`git status --porcelain -- <§5.1 路径>` = `M`（他线 NORMAL 改动）。
3. 复核失败现场：API23 上先跑一次 §3.2 用例 1，确认修复前红（异常原文 §0.2），再改代码。

**回滚**

1. 不得整树 `git checkout` / `stash` / `reset`；用备份覆盖 §5.1，删除 §5.2 的新测试（若已入库则回滚提交）。
2. 无 schema/迁移变化；回滚后不需要 `pm clear`、不需要重建数据库、不动学生数据。
3. 若回滚发生在 DDL 事务内（进程被杀），SQLite 会回滚未提交的 DDL；下一次 `ensureFeatureIndexes()`（旧代码）在 API23 上可能再次触发本异常——这是回滚后的已知状态，必须在 §6 如实写，不得把"回滚后 API23 仍红"当成修复未生效。
4. 复核：`sqlite_master` 两条索引存在（若缺失，先让新 APK 启动一次触发 ensure，或手工执行两条 `CREATE INDEX IF NOT EXISTS`）；`PRAGMA integrity_check=ok`。

**采纳**

保留 §5.1/§5.2；§6 登记双机 6×2、logcat 0、既有回归、`sqlite_version()`、逐文件 post-sha。

---

## 6. 未做 / 未验证（写作时点）

- **未改代码/未跑 Gradle/设备**：§1–§4 是冻结定义；修复前基线、双机 6 用例、logcat、既有回归全部 `NOT_RUN`。
- **API23/API34 的 SQLite 版本未在本会话重测**：3.8.10.2 / 3.39.2 来自 R8 的逐 AOSP 分支核对；设备实际版本可能因 OEM/系统更新不同，执行轮用 `SELECT sqlite_version()` 记录（软断言）。
- **Room 3 字节码结论未在本会话重跑全部反编译**：本会话用 `javap` 确认了 `Transactor`/`TransactorKt.immediateTransaction`/`SupportSQLiteStatement$TransactionSQLiteStatement` 的 `beginTransactionNonExclusive` 映射；`PassthroughConnectionPool.useConnection` 忽略 boolean 的结论沿用 R8 §1.3。
- **`sqlite_master` 目录读取的并发语义未在双机压测**：目录检查与执行之间有窗口；SQL 内 `IF NOT EXISTS/IF EXISTS` 兜底，但"另一连接同时建/删同名索引"未构造测试。
- **`inIndexDdlTransaction` 的测试缝**：新增 `internal` 方法只为用例 6；未验证它在所有 Room 版本上的可见性/内联行为（本仓固定 3.0.0）。
- **API 25/27/32 等中间版本**：未逐台验证；按 R8 的源码结论（SQLite < 3.36 均可能触发）推断，未实测。
- **`BundledSQLiteDriver` 方案**：未评估/未采用；本臂不碰驱动。
- **修复前基线**：R8 记录 API23 的 install 第一步即失败，但本会话未重跑；执行轮必须先复现再改。

---

## 7. 执行轮追加位（只追加，不改 §1–§4）

> 执行轮在此追加：写前/写后 HEAD 与 porcelain；备份与回滚核验；§5.1/§5.3 逐文件 post-sha；修复前基线的 API23 失败方法/异常原文与 API34 6/6；修复后 API23/API34 的 6×2 逐用例结果、`sqlite_version()`、APK/设备指纹、`logcat` 计数（必须 0）；既有 `BundledKnowledgeBaseInstallerInstrumentedTest` 双机结果；用例 6 的 `inTransaction()` 证据；用例 3/4 的窗口内/外 `sqlite_master` 计数；§4.1 的采纳/回滚判词。未跑项一律 `NOT_RUN` / `UNVERIFIED`。
