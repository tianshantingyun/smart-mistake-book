# R4 历史 v4 备份包夹具的合法构造（冻结字节 + digest golden，避开"同规则自证"）

| 字段 | 值 |
|---|---|
| evidence-id | `B42-R4-V4-PACK-FIXTURE-01` |
| executed-at | 2026-11-03（本机时间；文档命名按计划批次 42） |
| head | `faab17f5`（批次 42 计划 §0 基线；本会话未做任何 git 写） |
| 执行者 | 备份格式研究员（子代理） |
| 输入 | 计划 `plan-2026-11-04-batch-42.md` §1 R4 / §2 item 42–47；批次 41 记录 §9.3（F7/F8/F10/F11/F12，本项与它们无重叠但共享"工具假绿"同一病因）；批次 41 研究 `2026-11-03-backup-format-compat.md`（R2，closure 4/5 差异与投影） |
| 本会话实跑 | `git show`/`git diff` 只读、`python`（3.13.14）只读分析、**自写脚本实跑**（生成/校验/负控）、`sqlite3` CLI（Android SDK 3.50.6, 32-bit）、`adb devices`（只列设备，未下任何命令到设备）、`WebFetch`/`searchd extract_content` 取官方文档 |
| 本会话未跑 | 任何 Gradle 任务、任何单测/仪表化用例、任何设备用例、任何 git 写、任何真实 v4 包在 App 内的恢复 |
| 产出 | 本文 + 可直接落地的生成/校验脚本（§5）+ 三份夹具的冻结数字（§5.4）+ 判据（§3）与残余风险（§8） |

> 三类陈述分开：**实读**（路径:行号）、**实跑**（命令 + 输出）、**外部来源**（URL + 采纳/拒绝理由）。凡"App 会接受"的判断，本会话只到"按读码推理，未运行"，一律标 `UNVERIFIED`。

---

## 0. 结论（可施工的 11 条）

1. **现有 v4 夹具确实是"同规则自证"，两处都自证**。`SmbkArchiveCodecTest` 的 v4 用例用的是 `ClosureRuleTables.v4.rowCountKeys` 造键集合、`ClosureRuleTables.digest(rule = ClosureRuleTables.v4, …)` 造摘要（`SmbkArchiveCodecTest.kt:1620-1645`），而读侧用的是**同一份** `ClosureRuleTables.v4` 与**同一个** `digest`（`SmbkArchiveCodec.kt:1197-1205,1261-1278`）。规则表写错（键集少一张、前缀写错、closureVersion 写错）时，夹具与断言一起错，用例照样绿。
2. **更隐蔽的一层**：`ClosureRuleTables.v4` 的键集不是"冻结自历史"，而是**从当前 v5 表集合算术派生**——`SmbkSelectionPlan.rowCountKeys - errorCauseTables + materialFeatureTable`（`ClosureRuleTables.kt:71-78`）。它的 KDoc 写"逐字取自 `git show 70509247`"（`ClosureRuleTables.kt:16-18`），代码里却是减法。今天两边恰好相等（§1.3 实跑），但**没有任何东西钉住这件事**：v5 若再加一张表而忘记补 v4 快照，v4 键集会跟着变，而夹具跟着一起变，测试仍绿。
3. **"从当前 v5 包去掉 error_cause 两张表 + closureVersion=4" 这条路是伪造，不是构造**。理由三条，都可核：
   - v5 包的载荷库是 schema **73**，`PRAGMA user_version=73`；删两张表不会把 `user_version` 变成 72；
   - 73 库里**没有** `knowledge_search_material_feature`（v5 已删该表，`73.json` 实体差集 = `−material_feature`），而 v4 的 91 键**必须**包含它（§1.3）；于是夹具的声明键空间与载荷形状系统性不一致；
   - 后果不是"少测一点"，而是**测错了对象**：载荷已是 73，Room 打开时 72→73 迁移根本不会跑（`AndroidBackupRepository.kt:1138-1164` 只要求"迁移后版本 == 实时版本"），KD-83 要验的"旧库经迁移到 73 且不丢行"被整条跳过。而 manifest 声称 72、载荷实际 73 这件事**今天没有任何门会拦**：`databaseSchemaVersion` 只被写入（`:264`）与回显进回执（`:741`），从不与载荷 `user_version` 对拍（R2 §5.1 G4 / A14 仍未实现）。
4. **合法构造 = 用 v4 时点的导出 schema 造一份真 72 库，再按 v4 规则打包**。仓内已有现成路径：`core/database/schemas/.../72.json`（91 实体、2 视图、`setupQueries` 含 `room_master_table` 与 `identityHash=aa5e8e8e6536…`），以及把它物化的两个实现——设备侧 `createDatabaseFromExportedSchema`（`ExportedSchemaTestDatabase.kt:7-48`）与主机侧 `sqlite3` CLI（本文 §5 脚本，等价地建 entities + indices + **views** + setupQueries + `user_version=72`）。
5. **三条独立性是"不自证"的全部内容**（对应 §3 的 L1 / L3 / L4）：键集三源一致（git v4 源码 / 72.json 实体 / 生产规则表）、行数由**独立实现**实测（Android SDK 的 `sqlite3` CLI，与 CPython 内置 SQLite 是两份实现）、摘要由**另一门语言**重算（Python `hashlib`，不调用 `ClosureRuleTables.digest`）。
6. **两类 golden 的可复算等级不同，必须分开记**：
   - `closureDigest`（值摘要）**可永久复算**：只依赖（前缀、closureVersion、类别名、assetPolicy、91 个 (表名, 行数)），与工具链无关；
   - 包字节的 `sha256` **只在记录的构建工具链上可复现**：它含 SQLite 页面布局。本会话实测同机重跑逐字节相同（§5.5），但换 SQLite 版本/Python 版本不保证。所以 CI 的职责是**校验**不是**重生成**；重新冻结必须是一次带新 golden 的显式提交。
7. **生产包本身不可字节复现，这不是缺陷但必须写明**：`create()` 用 `ZipEntry(path)`（`SmbkArchiveCodec.kt:400-412,1391-1408`），JDK 的 `java.util.zip.ZipEntry(String)` 以**当前时间**初始化条目时间（JDK 行为陈述，本会话未实测），所以两次成包字节不同。因此"夹具与生产 v4 包逐字节一致"是不可证命题；夹具是**同格式的合成容器**，验的是读侧与迁移，不是写侧。
8. **夹具必须带真实行，且行数必须实测**。全 0 的 91 键包能被"声明全 0"恒过（R1 的 `declared == actual` 退化成 `0 == 0`）。本会话的黄金夹具在四张表写入真实行（跨 ISSUE_BOOK / MASTERY / ALWAYS 三类，§5.4），行数由 `COUNT(*)` 得出。
9. **负向夹具要与黄金夹具同批冻结，且要"自洽伪造"那一种**：`declared problem=5` 且**重算摘要**的包能过 codec（摘要自洽），只有恢复侧 R1（`AndroidBackupRepository.kt:1284-1288`）能抓。只改声明不改摘要的负向样本抓的是 digest 门，不是 R1 门——两者不能互相替代（本会话已产出两份负向样本，§5.6）。
10. **冻结位置**：权威源放仓库 `tools/backup_fixtures/v4/`，构建期 `Copy` 到 `core/data/src/androidTest/assets/backup/v4/`，JVM 侧直接按仓库相对路径读权威源并做逐字节比对。这套三段式是仓内已有、已被使用过的模式（`core/data/build.gradle.kts:42-77`；守卫测试 `GoldenRetrievalJvmTest.kt:287-310`），不新造机制。
11. **判据里必须有一条"生产规则表 == 冻结键集"的断言**（双向：91 键相等、83 裁剪上限、前缀 `smbk-selection-v4`）。它才是把 §0.2 那个算术派生钉死的钉子；否则夹具再真，规则表漂了也没人知道。

---

## 1. 基线实读

### 1.1 v4 兼容读侧已经落地（批次 41 item 5），但测试仍自证

| 位置 | 事实 |
|---|---|
| `ClosureRuleTables.kt:57-64` | `v5` 直接引用 `SmbkSelectionPlan`（92 键 / 84 裁剪 / `smbk-selection-v5`） |
| `ClosureRuleTables.kt:71-78` | `v4 = SmbkClosureRule(closureVersion=4, digestPrefix="smbk-selection-v4", rowCountKeys = SmbkSelectionPlan.rowCountKeys − errorCauseTables + materialFeatureTable, trimOrderSize=83, added={error_cause_*}, dropped={knowledge_search_material_feature})` |
| `ClosureRuleTables.kt:80-91` | `init` 只断言"v4 有 91 键"、"v5 键数 == 当前表数"、"增删集合与当前表互斥/包含"——**没有**断言 v4 键集等于历史 91 键 |
| `ClosureRuleTables.kt:104-129` | `digest(rule, categories, assetPolicy, rowCounts)`：payload = `prefix\nclosureVersion=N\ncategories=…sorted\nassetPolicy=…\n` + 每表 `table=count\n`（按表名排序），SHA-256 小写 hex |
| `SmbkArchiveCodec.kt:1197-1205` | 读侧按包自称的 `closureVersion` 取规则表，不认识 → `SelectionRead.Invalid` |
| `SmbkArchiveCodec.kt:1233-1240` | `rowCounts.keys == rule.rowCountKeys`（v4 比 91） |
| `SmbkArchiveCodec.kt:1246-1254` | 被删表声明非 0 → 整包拒（D3） |
| `SmbkArchiveCodec.kt:1255-1260` | `trimmedTableCount ∈ 0..rule.trimOrderSize`（v4 上限 83） |
| `SmbkArchiveCodec.kt:1261-1278` | 摘要用 `rule` 重算并与声明比对 |
| `SmbkArchiveCodec.kt:1280-1297` | 投影：删表移出键空间、增表补 0 |
| `SmbkArchiveCodecTest.kt:1486-1525` | "v4 包可读 + 投影 92 键"用例 |
| `SmbkArchiveCodecTest.kt:1528-1567` | "v4 键集合 + v5 前缀重算 → 拒"用例 |
| `SmbkArchiveCodecTest.kt:1620-1645` | 夹具本体：`v4EmptyRowCounts() = ClosureRuleTables.v4.rowCountKeys.associateWith { 0L }`；`v4SelectionFixture()` 的 `closureVersion` 与 `closureDigest` 全部取自 `ClosureRuleTables.v4` / `ClosureRuleTables.digest` |

**自证链条**一句话：夹具的键集合、版本号、前缀、摘要算法全部来自被测实现；**并且**行数恒 0、载荷是 `tempFile("database.sqlite", "database-content")` 写出的 **16 字节假文件**（`SmbkArchiveCodecTest.kt:1487`，实跑核过 `len("database-content") == 16`），所以"声明 vs 实测"这一层也在测试里缺席（JVM 侧本来就不数行，数行在恢复仓储，见 §1.4）。

### 1.2 生产成包不可字节复现（实读）

`SmbkArchiveCodec.kt:399-413` 与 `:1391-1408`：`ZipOutputStream.putNextEntry(ZipEntry(path))`，未设 `time`/`lastModifiedTime`。`java.util.zip.ZipEntry(String)` 默认取当前时间 → 条目头 DOS 时间逐次不同。**推论**：任何"夹具 == 生产包字节"的断言都不可实现；夹具字节只能自成一体地冻结。

### 1.3 v4 键集的三源核对（实跑）

命令（只读）：

```bash
git show 70509247:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt \
 | python -c "…解析 categoryTables + alwaysTables + derivedTables + splitTables…"
```

输出（实跑）：

```text
A(git v4 names) 91   B(72.json entities) 91
A-B: []              B-A: []
equal: True
73 entities 92       v4->v5 added: ['error_cause_binding', 'error_cause_observation']
                     removed: ['knowledge_search_material_feature']
derived(v5 −2 +1) 91 equal True
trimOrder: 70509247 → 83 项；HEAD → 84 项；两版派生表都在裁剪顺序里
```

结论：**今天**三者一致（历史源码 / v4 时点导出 schema / 当前代码的算术派生）。这恰好说明"算术派生"能工作，但也说明**没有任何断言在守它**——一致是巧合级事实，不是被钉住的契约。

### 1.4 恢复侧才是"行数对账"的所在地（实读）

`AndroidBackupRepository.kt:1235-1335`：

- `:1260-1263` 要数的表 = 声明键空间 + 新增表（v4 即 91 + 2）；
- `:1271-1281` 缺表按 0（**这是 v4 被删表那一条的合法落点**，但共享表缺表同样会被洗成 0）；
- `:1284-1288` R1：`declaredRowCounts.forEach { declared != actual → mismatched }`（`getValue`，不兜底）；
- `:1290-1295` R2：新增表实测必须 0；
- `:1297-1302` R2/D3：被删表实测必须 0。

`AndroidBackupRepository.kt:1110-1180`：恢复前对 staged 库依次做 `PRAGMA quick_check`、`PRAGMA foreign_key_check`（`:1121-1135`），再在**副本**上用生产 builder 跑迁移（`:1144-1152`），最后要求 `migratedVersion == liveVersion`（`:1153-1164`）。**这就是 v4 夹具必须带真 72 库的理由**：只有真 72 库才会让 72→73 这条迁移真的跑一遍。

`AndroidBackupRepository.kt:1076`：`readSchemaVersion()` 存在；`:264`/`:741` 说明 manifest 的 `databaseSchemaVersion` 只写回显，**与载荷对拍仍缺**。

### 1.5 仓内已有的 v72 库物化实现（实读）

`ExportedSchemaTestDatabase.kt:7-48`：按 `assets/…/<version>.json` 建库——**entities.createSql**（`:22-24`）→ **indices.createSql**（`:25-33`）→ **views.createSql**（`:35-41`，占位 `${VIEW_NAME}`）→ **setupQueries**（`:42-43`，含 `room_master_table` + identityHash）→ `database.version = version`（`:44`）。

`72.json` 事实（`python` 实读）：`version=72`、`entities=91`、`views=[library_catalog, knowledge_question_lattice]`、`identityHash=aa5e8e8e6536e0c1f3adcda6867814cb`、`setupQueries` 两条（建 `room_master_table` + 插入 42/identityHash）。

> 这条实现细节救了我一次：本文脚本初版**漏建视图**，读 `ExportedSchemaTestDatabase.kt:35-41` 才发现。漏建不会让 JVM codec 测试变红（它不读库），只会让"夹具库 = v4 时点真实库"这句话变假。

---

## 2. 外部实践与采纳

| 来源 | URL | 实读要点（引原文） | 采纳/拒绝 |
|---|---|---|---|
| SQLite Database File Format（官方） | <https://www.sqlite.org/fileformat2.html> | "This document describes and defines the on-disk database file format used by all releases of SQLite since version 3.0.0 (2004-06-18)."；1.3.3："both of these values are 1 for rollback journalling modes and 2 for WAL journalling mode"；1.3.10："Format 4 … New database files created by SQLite use format 4 by default."；4.6："unlike the database and WAL file formats which store all values as big endian" | **采纳**：主机侧造库可跨 SQLite 版本读，前提是 write/read version 为 1（回滚日志）。本会话实测夹具库头部 `write=1 read=1 page=4096 schema_format=4`（§5.5），满足该前提。**拒绝**把"跨版本可读"推广成"字节可复现"——格式稳定 ≠ 布局唯一 |
| Room：Migrate your Room database（官方，页面注明 Last updated 2026-10-23） | <https://developer.android.com/training/data-storage/room/migrating-db-versions> | "The exported JSON files represent your database's schema history. Store these files in your version control system so that you can recreate lower versions of the database for testing and support automated migration generation."；"The testing package provides a `MigrationTestHelper` class, which can read exported schema files."；"`MigrationTestHelper` automatically verifies the schema changes, but you need to validate that the data was migrated properly." | **采纳**：①"用已入库的旧 schema JSON 重建旧版库"是官方钦定做法——这正是本文 §5 的主机造库路径；②"schema 自动校验 ≠ 数据校验"——这直接支持"夹具必须带真实行并数行"，而不是只断言 schema 通过。**拒绝**用 `fallbackToDestructiveMigration` 的等价手段（`AndroidBackupRepository` 本来就没用，别加） |
| protobuf conformance（官方仓库 README） | <https://github.com/protocolbuffers/protobuf/blob/main/conformance/README.md> | "This directory contains the tester process conformance-test, which contains all the tests themselves."；被测实现 "should implement the testing protocol defined in conformance.proto"，只读写 pipe | **采纳**："断言与被测实现是两个进程"是避免自证的制度形状——本文用"Python 重算 + sqlite3 CLI 数数 + git 历史取键集"三份不同来源复刻同一形状。**拒绝**照搬它"测试即代码、无独立 golden 文件"的做法：本项目要的是可归档、可人审的冻结字节 |

**来源等级声明**：三条都是各自项目的官方文档/官方仓库，但本次每项只取到一个页面，且 developer.android.com 的 `WebFetch` 直连超时、改由 `searchd` 抓取（同一 URL，同一正文）——按纪律标**单一来源**（未跨引擎独立复核）。三者互不矛盾，也与仓内实读不冲突。

**未采用的替代做法**：

- **把 v5 包"改写"成 v4**（重算 v4 摘要）：等于让被测规则给自己出题，且库里根本没有 material_feature，已由 §0.3 否决。
- **用设备现成 v4 包**：仓库/设备上不存在真实 v4 包（批次 40 记录称 72 未面向真实用户发布，R2 §10 风险 1）。这个事实本文也无法证伪，只能登记。
- **把夹具建成 `@Test` 里的内联 `ByteArray`**：无法冻结、无法人审、无法给设备侧共用同一份字节。

---

## 3. "合法"的判据（L1–L8，缺一条就不是这个夹具）

| # | 判据 | 判定方式（可脚本化） | 违反即说明 |
|---|---|---|---|
| L1 | **键集三源一致**：git `70509247` 的表名集 == `72.json` 实体集 == 冻结字面量 == 生产 `ClosureRuleTables.v4.rowCountKeys` | 三个集合两两 `==`，并断言 `size == 91` | 规则表漂了，或冻结字面量被手改 |
| L2 | **载荷是真 72 库**：`user_version=72`；91 张声明表 + 2 视图 + `room_master_table` 全在；`error_cause_*` **不存在**；`knowledge_search_material_feature` **存在且 0 行** | `sqlite_master` 查询 + 头部 `PRAGMA user_version` | 这就是"从 v5 包删表"糊不过去的那道门 |
| L3 | **声明行数 = 库内实测**（逐表，91 键全覆盖；缺表即失败，不补 0），且非 0 表 ≥ 3、跨 ≥ 2 个类别 | 逐表 `COUNT(*)` 与 manifest 逐键比对 | "全 0 恒过"与"伪造行数" |
| L4 | **摘要由独立实现重算**：不同语言、不同进程、不 import 被测代码；且重算输入里 `closureVersion=4`、前缀 `smbk-selection-v4` 来自**历史源码文本**而非当前常量 | Python `hashlib` 版 `v4_digest()` 与 manifest 比对 | 自证 |
| L5 | **字节冻结 + 封存**：`.smbk` 的 `sha256` 写进 `.sha256` 与 golden JSON；工具链版本（`sqlite3 --version`、Python 版本）一并记录 | `sha256sum` / `hashlib` | "重建过但没人知道重建了什么" |
| L6 | **负控可红**：改一位载荷 → 摘要/封存不符；改一处声明并**重算摘要**（自洽伪造）→ 只有 R1 能抓；`material_feature` 非 0（自洽）→ codec D3 必须拒 | 三份负向样本（§5.6） | 校验脚本是空壳 |
| L7 | **生产规则表与冻结键集双向绑定**：一个断言把 `ClosureRuleTables.v4.rowCountKeys` 与冻结 91 键、`trimOrderSize==83`、`digestPrefix` 绑死 | JVM 单测（§7 T1） | §0.2 的算术派生漂移 |
| L8 | **可读性有代价声明**：fixture 的 `appVersionName` 明确写 `v4-fixture`，`createdAt` 是固定常量，不被当成"某用户真实备份"的证据 | golden JSON 记录 + 文档 | 把合成件伪装成用户证据 |

**"不是同规则自证"的操作性定义**（本文采用）：**生成夹具所需的每一项输入，都必须能在不执行被测代码的前提下被第三方重算**。当前 v4 用例的每一项输入都执行了被测代码（`ClosureRuleTables`），所以它是自证；本文方案的每一项输入分别来自 git 历史 blob、已入库的 72.json、以及非 Kotlin 的两份实现。

---

## 4. 构造路径：为什么是"造 72 库"而不是"删 73 表"

### 4.1 两条路径的形状差

| 维度 | 删表路径（计划原文写法） | 造 72 库路径（本文建议） |
|---|---|---|
| 载荷 `user_version` | 73（删表不改它） | 72 |
| `knowledge_search_material_feature` | 不存在（73 库里就没有） | 存在，0 行 |
| `error_cause_*` | 手工删掉 | 从来不存在 |
| 恢复时 72→73 迁移 | **不跑**（已是 73） | **真跑**（`ERROR_CAUSE_MIGRATION_72_73`） |
| manifest 声称 schema | 只能二选一：写 72（与载荷自相矛盾，今天无人拦）或写 73（那它就不是 v4 包） | 72，且与载荷一致 |
| 能证明什么 | 只证明"选择块解析不崩" | 证明"v4 旧库经生产迁移链到 73、旧行不丢、新表为空" |
| 结论 | 伪造（且掩盖 KD-83 的真风险） | 合法 |

### 4.2 两条造库路线（A 主机 / B 设备），优先级与理由

**路线 A（本会话实跑，默认）**：主机 `sqlite3` CLI 按 `72.json` 建库。
- 优点：可复算、无需设备、CI 侧也能校验；steps 完全对应官方 Room 文档"用入库的 schema JSON 重建旧版库"。
- 代价：库由**主机 SQLite（3.50.6）**写出，而恢复时读它的是**Android 的 SQLite**；`PRAGMA quick_check`/`foreign_key_check` 已在主机侧通过（§5.5），但 Room 的 `TableInfo` 逐列比对只能在设备上成立（§8 R1）。

**路线 B（设备侧，备选/仲裁）**：用仓内 `createDatabaseFromExportedSchema(context, name, 72)`（`ExportedSchemaTestDatabase.kt:7-48`）在设备上建库，`adb pull` 回来后走同一打包脚本。
- 优点：建库实现与迁移测试完全同源；SQLite 引擎与运行时一致，R1 风险归零。
- 代价：需要设备 + 一次 Gradle 仪表化（本会话禁止，属门禁阶段）；字节不可在别的机器复现（同样只做一次冻结）。
- 触发条件：路线 A 的夹具在设备上被 Room 判"迁移校验失败"（`AndroidBackupRepository.kt:1166-1171` 的失败路径）。此时**不要修补迁移**，改用路线 B 重建并重新冻结。

---

## 5. 生成脚本（实跑过）与冻结数字

### 5.1 键集的权威取法（可重跑，不属于被测代码）

```bash
git show 70509247:core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt \
 | python -c "
import sys,re
src=sys.stdin.read()
def block(n):
    m=re.search(r'val '+n+r'\s*:\s*List<String>\s*=\s*listOf\((.*?)\n    \)', src, re.S)
    return re.findall(r'\"([a-z0-9_]+)\"', m.group(1))
cats=re.search(r'val categoryTables.*?\n    \)\n', src, re.S).group(0)
names=sorted(set(re.findall(r'\"([a-z0-9_]+)\"', cats)+block('alwaysTables')+block('derivedTables')+block('splitTables')))
print(len(names)); print('\n'.join(names))"
```

实跑输出 `91` + 91 个表名；这批名字逐字进入脚本的 `V4_ROW_COUNT_KEYS` 字面量（提交进仓库，才能脱离 git 历史运行）。

### 5.2 脚本（节选；完整文件已实跑，sha256 见下；建议落点 `tools/ci/make_v4_pack_fixture.py`）

> 本会话实跑的**完整**文件 `sha256=ee9372894daaa44373086a0283017eb5897276b3f15190721d2fbc007c2dd86f`（含 91 项字面量与 `verify` 主体）。下面为**节选**：把 91 项字面量缩成注释、`verify_pack` 主体缩成签名，其余与实跑版本逐行相同。提交版必须把 §5.1 的输出逐项写进 `V4_ROW_COUNT_KEYS`（字母序）并补全 `verify_pack`。

```python
"""v4 备份包夹具生成器/校验器：独立于被测 Kotlin 代码（见 docs/research/2026-11-04-v4-pack-fixture.md）。"""
import argparse, hashlib, io, json, pathlib, re, subprocess, sys, zipfile

V4_PREFIX, V4_CLOSURE, SCHEMA_VERSION, ASSET_POLICY = "smbk-selection-v4", 4, 72, "INCLUDED"
CATEGORY_BITS = [("ISSUE_BOOK", 1), ("MASTERY", 2), ("SESSIONS", 4), ("IMAGES", 8)]
_IDENTIFIER = re.compile(r"^[a-z][a-z0-9_]*$")
TABLE_NAME_PLACEHOLDER = "$" + "{TABLE_NAME}"
VIEW_NAME_PLACEHOLDER = "$" + "{VIEW_NAME}"

# 91 项，逐字取自 git 70509247（§5.1 的输出）。下面只是省略写法，提交版必须完整。
V4_ROW_COUNT_KEYS = (
    "active_review_plan_slot", "agent_pending_request", # … 共 91 项，见 §5.1
)
V4_KEY_SET = frozenset(V4_ROW_COUNT_KEYS)

FIXED_SEED = 1_700_000_000_000   # 固定时间戳；同时是包内 createdAt
SEED_SQL = (  # 真实行：跨 ISSUE_BOOK(problem) / MASTERY(attempt_submission) / ALWAYS 三类
    "INSERT INTO `problem` (problem_id, canonical_fingerprint, subject, created_at_epoch_millis)"
    " VALUES ('v4fixture-problem-1','fp-v4-1','math',1700000000000);",
    "INSERT INTO `attempt_submission` (submission_id, learner_id, payload_fingerprint)"
    " VALUES ('v4fixture-submission-1','v4fixture-learner','fp-sub-1');",
    "INSERT INTO `attempt_submission` (submission_id, learner_id, payload_fingerprint)"
    " VALUES ('v4fixture-submission-2','v4fixture-learner','fp-sub-2');",
    "INSERT INTO `knowledge_node` (knowledge_node_id, stable_code, subject, display_name,"
    " taxonomy_version, created_at_epoch_millis)"
    " VALUES ('v4fixture-node-1','V4-FIXTURE-NODE-1','math','夹具知识点','v4-fixture',1700000000000);",
    "INSERT INTO `content_install_state` (pack_id, content_version, applied_at_epoch_millis)"
    " VALUES ('v4-fixture-pack','v4-fixture-content',1700000000000);",
)

def bare_identifier(name: str) -> str:
    """SQL 标识符白名单：小写表名 + 必须属于 v4 冻结键集。"""
    if not isinstance(name, str) or not _IDENTIFIER.match(name) or name not in V4_KEY_SET:
        raise SystemExit(f"非法或未登记的标识符：{name!r}")
    return name

def quoted_identifier(name: str) -> str:
    return '"' + bare_identifier(name) + '"'

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def run_sqlite(sqlite3_exe: str, db: pathlib.Path, script: str) -> str:
    """SQL 经 stdin 交给 sqlite3 CLI（不经 shell、不拼命令串）。"""
    done = subprocess.run([sqlite3_exe, str(db)], input=script, capture_output=True,
                          text=True, encoding="utf-8", check=False)
    if done.returncode != 0:
        raise SystemExit(f"sqlite3 执行失败：{done.stderr.strip()}")
    return done.stdout

def build_schema_db(schema_json, out_db, sqlite3_exe, seed) -> dict:
    schema = json.loads(pathlib.Path(schema_json).read_text(encoding="utf-8"))["database"]
    if int(schema["version"]) != SCHEMA_VERSION:
        raise SystemExit("schema JSON 版本必须是 72（v4 时点）")
    names = {bare_identifier(e["tableName"]) for e in schema["entities"]}
    if names != V4_KEY_SET:                      # L1 的第二源核对
        raise SystemExit(f"冻结键集与 72.json 不一致：{sorted(names ^ V4_KEY_SET)}")
    parts = ["PRAGMA journal_mode=DELETE;", "PRAGMA foreign_keys=OFF;"]
    for e in schema["entities"]:
        n = bare_identifier(e["tableName"])
        parts.append(str(e["createSql"]).replace(TABLE_NAME_PLACEHOLDER, n) + ";")
        for i in e.get("indices", []):
            parts.append(str(i["createSql"]).replace(TABLE_NAME_PLACEHOLDER, n) + ";")
    for v in schema.get("views", []):            # 少建视图 = 夹具库形状不对
        vn = str(v["viewName"]); assert _IDENTIFIER.match(vn)
        parts.append(str(v["createSql"]).replace(VIEW_NAME_PLACEHOLDER, vn) + ";")
    parts += [str(q) + ";" for q in schema.get("setupQueries", [])]
    parts.append("PRAGMA user_version = 72;")
    if seed:
        parts += list(SEED_SQL)
    parts.append("VACUUM;")
    if pathlib.Path(out_db).exists():
        pathlib.Path(out_db).unlink()
    run_sqlite(sqlite3_exe, pathlib.Path(out_db), "\n".join(parts) + "\n")
    return {"version": SCHEMA_VERSION, "identityHash": schema["identityHash"], "tables": len(names)}

def row_counts(db, sqlite3_exe) -> dict:
    """一次调用数完 91 张表：输出 `表名|行数`；缺表由 sqlite3 报错（非零退出）。"""
    script = "\n".join(
        "SELECT '" + t + "' || '|' || COUNT(*) FROM " + quoted_identifier(t) + ";"
        for t in V4_ROW_COUNT_KEYS)
    counts = {}
    for line in run_sqlite(sqlite3_exe, pathlib.Path(db), script + "\n").splitlines():
        line = line.strip()
        if not line:
            continue
        t, _, c = line.partition("|")
        if t not in V4_KEY_SET:
            raise SystemExit(f"未登记的表名：{t!r}")
        counts[t] = int(c)
    if set(counts) != V4_KEY_SET:
        raise SystemExit(f"计数缺表：{sorted(V4_KEY_SET - set(counts))}")
    return counts

def v4_digest(categories, asset_policy, counts) -> str:
    """v4 时点 payload 形状（`git show 70509247` 的 closureDigest 逐行对照），hashlib 重算。"""
    lines = [V4_PREFIX, "closureVersion=" + str(V4_CLOSURE),
             "categories=" + ",".join(sorted(set(categories))),
             "assetPolicy=" + asset_policy]
    lines += [t + "=" + str(counts[t]) for t in sorted(counts)]
    return sha256_bytes(("\n".join(lines) + "\n").encode("utf-8"))

def build_pack(db, counts, mask, out_pack) -> dict:
    db_bytes = pathlib.Path(db).read_bytes()
    categories = [n for n, bit in CATEGORY_BITS if mask & bit]
    digest = v4_digest(categories, ASSET_POLICY, counts)
    manifest = {
        "formatVersion": 3, "databaseSchemaVersion": SCHEMA_VERSION, "createdAt": FIXED_SEED,
        "problemCount": counts["problem"], "assetCount": 0, "hashAlgorithm": "SHA-256",
        "appVersionName": "v4-fixture", "appVersionCode": 0,   # L8：明确标注合成件
        "contentVersion": None, "hasSettings": False, "settingsDigest": None,
        "files": [{"path": "database.sqlite", "sha256": sha256_bytes(db_bytes),
                   "byteSize": len(db_bytes)}],
        "referencedAssets": [],
        "selection": {"schemaVersion": 2, "categoryMask": mask, "categories": categories,
                      "assetPolicy": ASSET_POLICY, "closureVersion": V4_CLOSURE,
                      "closureDigest": digest,
                      "rowCounts": {t: counts[t] for t in sorted(counts)},
                      "trimmedTableCount": 0},
    }
    manifest_bytes = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode()
    checksums = (sha256_bytes(db_bytes) + " *database.sqlite").encode()
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, payload in (("manifest.json", manifest_bytes),
                              ("database.sqlite", db_bytes),
                              ("checksums.sha256", checksums)):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))  # 固定 mtime
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o600 << 16
            info.create_system = 0
            z.writestr(info, payload)
    pathlib.Path(out_pack).write_bytes(buffer.getvalue())
    return {"packSha256": sha256_bytes(buffer.getvalue()), "packBytes": len(buffer.getvalue()),
            "databaseSha256": sha256_bytes(db_bytes), "databaseBytes": len(db_bytes),
            "closureDigest": digest,
            "nonZeroTables": {t: c for t, c in sorted(counts.items()) if c}}

def verify_pack(pack, sqlite3_exe) -> dict:
    """只读复核：条目集合、逐条摘要、载荷 user_version、声明行数 vs 实测、摘要重算。"""
    # 完整实现见 §5.3 的判据清单；本会话实跑版本逐项覆盖（下表中标注"未造样本"的两行除外）。
    ...
```

### 5.3 校验器要报的每一项（`verify` 模式的判据清单；除最后两行特别标注外，本会话均实跑触发过）

| 检查 | 触发样本 | 实跑输出 |
|---|---|---|
| 条目集合 == {manifest.json, database.sqlite, checksums.sha256} | 正常件 | 无问题（**本会话未造"多/少条目"样本**，该分支只有读码预期） |
| `database.sqlite` 的 sha256/byteSize == manifest | 载荷翻一位 | `database.sqlite 摘要与 manifest 不符` + `checksums.sha256 与载荷不符` |
| `checksums.sha256` 文本 == `"<sha> *database.sqlite"` | 同上 | 同上 |
| 载荷 `PRAGMA user_version == 72` | 删表派生的假 v4 | **本会话未造该样本**（它本就不该存在）；该分支为读码预期，未实跑 |
| 声明行数 == 实测（91 键） | `problem` 声明 5、实测 1 | `声明行数与实测不符：{'problem': (5, 1)}` |
| `closureDigest` == 独立重算 | 摘要改成 64 个 0 | `closureDigest 与独立重算不符` |
| 载荷库可读 | 第 1 页翻一位 | `载荷库不可读：sqlite3 执行失败：… database disk image is malformed (11)` |

### 5.4 冻结数字（本会话实跑产出，作为 WS5 的对照基准）

**黄金件**（`mask=15`，带 4 张表的真实行，视图已建）：

| 项 | 值 |
|---|---|
| pack | `closure4-schema72-mask15.smbk`，**28,537 B** |
| pack sha256 | `91f88c10f5b0aa4deb0f282fe1ad55844247cc351f1b58ba2e438bcf8380aa71` |
| 载荷 db | `database.v72.sqlite`，**1,904,640 B** |
| db sha256 | `ce54c8246ed491f511fa94dd9108f50f1c877edebf6837fc1bd455dbed3980b6` |
| `closureDigest` | `49b5ab83fd2090b628d87d5cf59156c7c71413e940ae62b1a3ad45ab27f697d4` |
| `rowCounts` 摘要（排序后紧凑 JSON 的 sha256） | `9d2a64fb05e1fed0813112dffc37803c2d6378e0efa7484f808c2caa23da980e` |
| 非 0 表 | `attempt_submission=2`、`content_install_state=1`、`knowledge_node=1`、`problem=1` |
| 库元数据 | `identityHash=aa5e8e8e6536e0c1f3adcda6867814cb`、`tables=91`、视图 2、`sqlite_master` 共 97 表（含 `room_master_table`、`sqlite_sequence`、FTS5 影子表） |
| 形状校验 | `PRAGMA quick_check` → `ok`；`PRAGMA foreign_key_check` → 0 行；`error_cause*` 表 0 张；`knowledge_search_material_feature` 存在且 `COUNT(*)=0`；头部 `write=1 read=1 page=4096 schema_format=4` |

**全 0 对照（仅供 digest 参考，不作夹具）**：

| mask | `closureDigest`（91 键全 0） |
|---|---|
| 15 | `ee9a1b4d3f0ea7ed44eeec773f459df9d55c5df3abda32a69c62f7fdfac6b006` |
| 9 | `11141529bd9351769ffc9a3ace8eaa3410823e0e188dff7ee3141c971154ac3c` |
| 13 | `e4b41a40b4e632fc02822061ee8a9f1d10918ba993456aad28ab55f91b8f808b` |

> 这三个全 0 值由**同一个** `hashlib` 实现算出（未做第二实现的摘要交叉，别把它们当独立 oracle 的证据）。它们的用途是"摘要算法 + 键集"的最小可核指纹：任何人拿 §5.1 的键集与"全 0"就能复算出这三个值，从而判定"算法或键集是否被改过"。真正的独立性在三处：键集双源（git / 72.json）、行数双实现（`sqlite3` CLI / CPython 内置）、摘要跨语言（Python 而非 Kotlin）。

### 5.5 复现性实测（本会话）

```text
$ python make_v4_pack_fixture.py build --schema …/72.json --out-dir out  --sqlite3 …/sqlite3.exe --seed
$ python make_v4_pack_fixture.py build --schema …/72.json --out-dir out2 --sqlite3 …/sqlite3.exe --seed
$ sha256sum out/closure4-schema72-mask15.smbk out2/closure4-schema72-mask15.smbk
  91f88c10… *out/closure4-schema72-mask15.smbk
  91f88c10… *out2/closure4-schema72-mask15.smbk      ← 逐字节相同
$ sha256sum out/database.v72.sqlite out2/database.v72.sqlite
  ce54c824… *out/database.v72.sqlite
  ce54c824… *out2/database.v72.sqlite                ← 逐字节相同
```

工具链记录：`sqlite3 3.50.6 2025-09-22 18:50:24 … (32-bit)`（`C:/Android/Sdk/platform-tools/sqlite3.exe`）、`Python 3.13.14`、CPython 内置 SQLite `3.50.4`（用于第二实现交叉核对，非 0 表集合与 `sqlite3` CLI 一致）。

**边界**：这证明的是"同机同工具链可复现"。跨机（不同 SQLite 编译选项、不同 Python）未验证，也不该被当作契约——见 L5 与 §0.6。

### 5.6 负向样本（同批冻结，供 item 46 直接使用）

| 样本 | 做法 | sha256 | `closureDigest` | 应在哪一层被拒 |
|---|---|---|---|---|
| N1 自洽伪造行数 | `problem` 声明 5（实测 1），**并重算摘要** | `4c3066421eaa85c4acacdacdf2628870d09317cbebcb40397142febedf7efcb2`（28,540 B） | `34c02ded56ccf3bc091228ef621eb2aeb511071145f52c08d181831128b70f42` | **codec 放行**（摘要自洽）→ 只能由恢复侧 R1（`AndroidBackupRepository.kt:1284-1288`）拒。这是"自洽伪造"样本，最值钱 |
| N2 被删表非空 | `knowledge_search_material_feature` 写 1 行（FK 干净：先插 source/material），声明 1 并重算摘要 | `265e361e755fd10a857657a0988d55141eac37b382370d41bfb1427c7ad065b5`（28,924 B） | `3facc27fd4a192e8848b4aa1bb831558a085c36dc14fb22f1c39cc5852884e24` | **codec D3**（`SmbkArchiveCodec.kt:1246-1254`）应拒，早于任何行数对账 |
| N3 载荷改一位 | `database.sqlite[100] ^= 0xFF`，manifest 不动 | —（校验器构造物） | — | 结构门：摘要/checksum 不符；附带 `载荷库不可读` |

N1/N2 的 `PRAGMA foreign_key_check` 实测均为 0 行（N2 的父行已补齐），所以它们被拒的理由**只会是**目标判据，不会被"引用完整性"这类侧因掩盖。

---

## 6. 冻结位置与三道防线

沿用仓内已有的"权威源 → 构建期同步副本 → 逐字节守卫 + 运行时摘要"三段式（`core/data/build.gradle.kts:42-77` 与其 KDoc 第 1–4 条；守卫 `GoldenRetrievalJvmTest.kt:287-310`）。

```text
tools/backup_fixtures/v4/                     ← 权威源（提交进仓库）
  closure4-schema72-mask15.smbk               ← 黄金件（28,537 B）
  closure4-schema72-mask15.smbk.sha256        ← 封存值（一行 hex）
  forged-count.smbk                           ← N1
  dropped-nonempty.smbk                       ← N2
  v4-pack.golden.json                         ← 全部 golden：pack/db sha256、closureDigest、
                                                 rowCounts 摘要、非 0 表、工具链版本、
                                                 生成脚本 sha256、schema identityHash
core/data/src/androidTest/assets/backup/v4/   ← 构建期 Copy 的副本（设备侧读它）
```

接线（与既有 `syncGoldenQueryAssets` 同形，`core/data/build.gradle.kts:58-77`）：

```kotlin
val v4FixtureSourceDir = rootProject.layout.projectDirectory.dir("tools/backup_fixtures/v4")
val v4FixtureAssetDir  = layout.projectDirectory.dir("src/androidTest/assets/backup/v4")
val syncV4PackFixtures = tasks.register<Copy>("syncV4PackFixtures") {
    group = "verification"
    from(v4FixtureSourceDir) { include("*.smbk", "*.sha256", "v4-pack.golden.json") }
    into(v4FixtureAssetDir)
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("AndroidTestAssets") }
    .configureEach { dependsOn(syncV4PackFixtures) }
```

三道防线（缺一不可）：

1. **构建期同步**：副本永远是权威源的副本，人工不参与；
2. **JVM 逐字节守卫**（`locateRepoRoot()` 同 `GoldenRetrievalJvmTest.kt:319-331`）：权威源与副本 `contentEquals`，两者 sha256 都等于 golden——防"副本过期/被手改"；
3. **运行时摘要**：仪表化读 `assets/backup/v4/`，先断言 sha256，再做恢复——防"APK 里的字节不是这份字节"。

---

## 7. 判据如何落成用例（先红点写清楚）

**JVM（`core/data/src/test/…/backup/V4PackFixtureTest.kt`）**

| # | 用例 | 期望 | 先红点 |
|---|---|---|---|
| T1 | `ClosureRuleTables.v4.rowCountKeys` 与 golden 的 91 键 **集合相等**；`trimOrderSize==83`；`digestPrefix=="smbk-selection-v4"`；`closureVersion==4` | 全等 | 今天会绿（值恰好一致），但它是**唯一**能抓住 §0.2 算术派生漂移的钉子；改动 `ClosureRuleTables.v4` 必须让它红 |
| T2 | 黄金件 `sha256` == golden；`SmbkArchiveCodec.validate(bytes)` 为 `Valid` | Valid | 今天红不了（批次 41 已修 closure 4 读侧）；它是回归门 |
| T3 | `unpackDetailed(bytes).projection`：`declaredClosureVersion==4`、`declaredRowCounts.size==91`、`normalizedRowCounts.keys == SmbkSelectionPlan.rowCountKeys`、`error_cause_*` 为 0、`material_feature` 不在归一化键空间 | 同左 | 同上 |
| T4 | 用 golden 的 91 键 + golden 行数，`ClosureRuleTables.digest(v4, …)` == golden `closureDigest` | 相等 | 这是"生产实现 == 独立 oracle"的**单向**绑定；它与 T1 一起构成双向 |
| T5 | N1（自洽伪造）**过** codec；N2（被删表非空）**不过** codec 且拒因点 `knowledge_search_material_feature` | 同左 | N2 今天会绿；N1 必须"绿在 codec、红在仓储" |
| T6 | 权威源与 androidTest 副本逐字节相同（含 `.sha256`、`golden.json`） | 相同 | 副本不存在时红（提示跑 `syncV4PackFixtures`） |

**仪表化（`core/data/src/androidTest/…/backup/BackupV4PackRestoreInstrumentedTest.kt`）**

| # | 用例 | 期望 |
|---|---|---|
| T7 | 读 `assets/backup/v4/closure4-schema72-mask15.smbk`，断言 sha256 == golden，再 `restore` | 成功；恢复后 `user_version=73` |
| T8 | 恢复后 DAO 可读：`problem`/`attempt_submission` 行数与 golden 一致（1 / 2） | 逐表相等 |
| T9 | 恢复后 `error_cause_observation` / `error_cause_binding` 存在且 `COUNT(*)=0` | 0 / 0（与 `ErrorCauseMigration_72_73.kt:17-19` 的"只建空表不回填"对账） |
| T10 | 恢复后 `knowledge_search_material_feature` 不存在（v5 规则外）或其行数 0 | 0（R2 §10 风险 2 的现状登记） |
| T11 | N1 恢复 → `BackupRestoreException`，文案命中"逐表行数与声明不符"，**且本机库未被替换** | 拒 + 原库不动 |
| T12 | N2 恢复 → 在 codec/仓储任一前门被拒，文案命中"已废弃表" | 拒 |

> T7–T10 是本项唯一"数据侧"证据；T11 是唯一能证明 R1 不是摆设的用例（codec 对 N1 是绿的）。

---

## 8. 残余风险与未验证

**UNVERIFIED（本会话没跑，属门禁/设备阶段）**

1. `SmbkArchiveCodec` 真能读这份 Python 造的包——`UNVERIFIED`。逐条对照过读侧的每个门（条目集合、`checksums.sha256` 的 `"<sha> *path"` 解析 `SmbkArchiveCodec.kt:1101-1113`、`files` 与 staged 集合相等 `:1097-1100`、selection 全门 `:1197-1307`），但**没有执行**。
2. Room 能否在设备上打开这份主机造的 72 库并迁移到 73（`TableInfo` 逐列比对）——`UNVERIFIED`。主机 `quick_check`/`foreign_key_check` 已过（§5.4），但那不是 Room 的门。
3. 恢复后"无半行"（T8/T9）——`UNVERIFIED`，需设备。
4. 生产 `create()` 写出 v4 形态包的行为——不可测：写侧只写 v5（`SmbkArchiveCodec.kt:444-447`），v4 只有读侧。夹具的格式正确性由"读侧接受"间接证明，不由"写侧能产出"证明。

**风险（按严重度）**

| # | 风险 | 具体失败 | 处置 |
|---|---|---|---|
| R1 | 主机 SQLite 写出的库被 Room 判迁移失败 | T7 直接红，且失败发生在交换前（`AndroidBackupRepository.kt:1166-1171`）→ 不会损坏用户数据 | 走 §4.2 路线 B 重造并重冻结；**不要**改迁移去迁就夹具 |
| R2 | `ClosureRuleTables.v4` 的算术派生漂移（§0.2） | 将来 v5 加表，v4 键集跟着变；旧包被按"91 键"误判 | T1 双向绑定；`tools/ci` 静态门可加"v4 键集 == 冻结字面量"（本会话已可用 §5.1 命令复算） |
| R3 | 派生的 `categories` 枚举名变更 | v4 digest 依赖 `SmbkSelectionCategory` 的 **name**（`ClosureRuleTables.kt:116`）；改名 = 所有旧包摘要失配、不可恢复 | 冻结键集时同时冻结类别名字面量；T1 断言 `categoriesOf(15)` 的名字集合 == `{ISSUE_BOOK, IMAGES, MASTERY, SESSIONS}` |
| R4 | 夹具被当成"真实用户备份"的证据 | 一个合成件被引用来证明"用户旧包能恢复" | L8：`appVersionName="v4-fixture"`、`createdAt` 固定、golden 里注明合成；文档层面禁止这样引用 |
| R5 | 夹具无资产（`assetCount=0`），资产闭合/AUD-05 在 v4 路径上没被覆盖 | v4 旧包带图恢复的缺图/坏图分支无样本 | 二期可造带 1 张 `canonical_source_asset` + 1 个 `assets/` 文件的变体（需同时满足 DB→资产反查 `AndroidBackupRepository.kt:1182-1210`）；本会话未做 |
| R6 | 字节 golden 的生命周期 | 有人在新机器上"重生成"夹具并直接覆盖，golden 没更新 → 守卫红但不是缺陷 | 重新冻结必须是显式提交：新 golden + 变更说明 + 旧 sha256 存档 |
| R7 | 72 是否真实发布过 | 若产品确认 72 从未发布，v4 兼容读侧的收益只是"演练包可恢复" | 产品事实，本文无法证明（R2 §10 风险 1 同结论），交台账 |

**待裁定**

- Q1：黄金件是否要覆盖 `mask=9`（题+图）与 `mask=13`（题+会话+图）两档？本文只冻结 `mask=15`；另两档的 digest 已在 §5.4 给出（全 0 版），造带行版本是低成本增量。
- Q2：N1（自洽伪造）是否也进设备矩阵常驻？（它贵在"proves R1"，但每次跑都要走一次完整恢复失败路径。）
- Q3：v4 键集冻结字面量放 `tools/` 还是 `core/data/src/test`？本文倾向前者（可被 `tools/ci` 与设备侧共用）。

---

## 9. 对 WS5（item 42–47）的落地建议与关闭判据

| item | 用本文的什么 | 关闭判据（可核） |
|---|---|---|
| 42 构造 v4 夹具 | §5 脚本 + §5.4 数字；**不要**用"删 v5 包表"的写法 | 黄金件入库，`sha256` 与 §5.4 一致（或按路线 B 重冻结并记录原因） |
| 43 digest golden | §5.4 的 `closureDigest` + `rowCounts` 摘要 + 工具链 | golden JSON 里五项齐全（pack/db sha256、digest、rowCounts 摘要、工具链） |
| 44 JVM 恢复成功/新表空/行数对账 | T2–T4（JVM 只能到 codec 与投影层）；**行数对账在 JVM 不成立**（假库/Kotlin 侧不读库），须如实写"JVM 只证选择层" | T1–T6 全绿；报告里不把"JVM 绿"说成"行数对账通过" |
| 45 设备 v4→73 | T7–T10 | 设备日志含 sha256、`user_version=73`、逐表行数、两新表 0 |
| 46 负向 fail-closed | N1/N2（§5.6）+ T11/T12 | N1 在仓储被拒且原库不动；N2 在前门被拒 |
| 47 台账 | §8 风险 R1–R7 | 关闭或维持 open，按实测 |

---

## 10. 来源

**仓内（实读）**

- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/ClosureRuleTables.kt`：`:16-18`（KDoc 称"逐字取自 git"）、`:48-54`（两表常量）、`:57-64`（v5）、`:71-78`（v4 算术派生）、`:80-91`（init 断言）、`:94-98`（`of`）、`:104-129`（digest）、`:116`（类别用枚举 name）。
- `core/data/src/main/kotlin/…/backup/SmbkArchiveCodec.kt`：`:270-273`（JSON 严格）、`:298-425`（`create` 与 zip 写）、`:399-413`（无 mtime）、`:435-487`（写侧闸门）、`:508-572`（`validate`）、`:589-604`（`unpackDetailed`）、`:1046-1164`（`finalizeValidation`）、`:1097-1139`（文件清单/checksums/sha）、`:1182-1307`（`readableSelection` 全门）、`:1391-1408`（`copyEntry`）。
- `core/data/src/test/kotlin/…/backup/SmbkArchiveCodecTest.kt`：`:1486-1525`、`:1528-1567`、`:1570-1605`、`:1620-1645`、`:1487`（假库）。
- `core/data/src/main/kotlin/…/backup/AndroidBackupRepository.kt`：`:264`、`:741`（manifest schema 只写/回显）、`:1076`（`readSchemaVersion`）、`:1110-1180`（`quick_check`/`fk_check`/Room 迁移/版本对拍）、`:1182-1210`（DB→资产闭合）、`:1235-1335`（R1/R2/D3）。
- `core/database/src/main/kotlin/…/ErrorCauseMigration_72_73.kt`：`:17-19`（只建空表不回填）、`:34-44`（两条迁移）、`:47-149`（DDL）。
- `core/data/src/androidTest/kotlin/…/backup/ExportedSchemaTestDatabase.kt`：`:7-48`（按导出 schema 建库：entities/indices/views/setupQueries/version）。
- `core/data/build.gradle.kts`：`:34-38`（androidTest assets 引入 schemas）、`:42-77`（golden 副本构建期同步 + 三道防线 KDoc）。
- `core/data/src/test/kotlin/…/knowledge/GoldenRetrievalJvmTest.kt`：`:287-310`（副本逐字节守卫）、`:319-331`（`locateRepoRoot`）。
- `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/72.json`、`73.json`（`python` 实读：91/92 实体、2 视图、identityHash、setupQueries）。
- `git show 70509247:core/data/src/main/kotlin/…/backup/SmbkSelectionPlan.kt`（closure 4：91 表、83 裁剪、`smbk-selection-v4`、`closureVersion=4`、digest payload 形状）。
- 批次 41 研究 `docs/research/2026-11-03-backup-format-compat.md`（closure 4/5 差异、投影、D1–D8、A14 缺口、`72→73` 不 DROP 的残留）。

**外部（官方；每项单一来源，已标注）**

| 来源 | URL | 采纳/拒绝 |
|---|---|---|
| SQLite Database File Format | <https://www.sqlite.org/fileformat2.html> | 采纳：格式自 3.0.0 稳定、write/read version 1=回滚日志、schema format 4 为默认；拒绝把"格式稳定"推广为"字节可复现" |
| Room：Migrate your Room database（Last updated 2026-10-23） | <https://developer.android.com/training/data-storage/room/migrating-db-versions> | 采纳：用入库的 schema JSON 重建旧版库；"schema 自动校验 ≠ 数据校验"；拒绝 destructive fallback |
| protobuf conformance（官方仓库 README） | <https://github.com/protocolbuffers/protobuf/blob/main/conformance/README.md> | 采纳：断言与被测实现分属不同进程；拒绝"无独立 golden 文件"的做法 |

> 取数通道说明：`developer.android.com` 的 `WebFetch` 直连两次超时（`UND_ERR_CONNECT_TIMEOUT`），改由 `searchd` 以同一 URL 抓取正文（缓存 2026-11-03）。这属于"同一来源换通道"，不构成第二来源。

**本会话自产（可复核）**

- 生成/校验脚本（实跑）：`make_v4_pack_fixture.py`，`sha256=ee9372894daaa44373086a0283017eb5897276b3f15190721d2fbc007c2dd86f`（建议提交到 `tools/ci/`）。
- 黄金件/负向件：§5.4、§5.6 的 sha256 与尺寸；均为一次生成、两次重跑一致。
- 交叉核对输出：§1.3（三源键集）、§5.5（重跑逐字节相同、头部版本位、非 0 表两份实现一致）。
