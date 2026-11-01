# 外部模式研究：SQLite 导入耐久性、Room 预置库与配额/MMR 先例（2026-10-31）

> **性质**：只读研究轨。本会话唯一写盘产物是本文件；**零 Gradle、零设备、零 git 写、零源码改动**。
> **通道记录**（全部本会话实跑）：WebFetch（sqlite.org 两页成功、developer.android.com 两次超时）、
> `mcp__searchd__extract_content`（developer.android.com 与 docs.opensearch.org 正文）、
> `mcp__searchd__search_text`（两问，引擎 bing / yahoo 返回）、`gh api`（RoomAssetHelper 元数据与 README）、
> `javap`（room3-runtime 3.0.0 字节码）、本仓 `rg` / `Read`。
> **标注口径**：`原文`＝本会话取到外部原文；`转引`＝引本仓既有研究/产物，本轮未复跑；
> `待验证`＝单一来源或关键细节未核对；`推断`＝由已读事实推出、未实测。

---

## 0. 结论摘要

1. **SQLite 侧**：批次 29 的「事务分组」与官方 FAQ Q19 的首选建议一致；`synchronous=OFF` 的
   **性能结论**与 Q19/PRAGMA 文档一致，但**耐久性许可只覆盖「从零建库且过程可重复」的冷装路径**。
   升级路径（已有用户数据的库 + 新 `contentVersion` 触发全量导入）正落在官方许可的字面范围之外，
   而它恰好是批次 29 **没有复测**的那条路径（`docs/kb-batch29-2026-10-31.md:236`）。判定：**部分一致**，
   建议补三项只读验证（§1.5），不建议为此改码。
2. **Room 预置库**：官方 prepopulate 页 WebFetch 两次超时，同一 URL 经第二通道取到正文
   （页面自述 Last updated 2026-10-23）。官方语义有三条硬约束会直接决定收益/成本：
   ① 预置库只在**无库首建**或 **fallback migration** 时被使用；实现了迁移路径时**不使用**预置文件；
   ② 库 schema 必须与 `@Database` 一致（Room 会校验）；③ 内存库不支持。
   本项目 `STUDY_DATABASE_VERSION = 71` 且迁移链 1→71 已实现、无 `fallbackToDestructiveMigration`
   ⇒ 「F6 下一级：预置 DB」**对已装用户零收益，只对首装生效**；且每次 schema bump 都让资产失效。
   **结论：不立项**，给 C1–C10 约束清单与重开条件（§2.5–§2.6）。
3. **RoomAssetHelper**（`github.com/ueen/RoomAssetHelper`）：7★、最后提交 2020-05-09、
   **无 license 字段**（`license: null`）、Kotlin、Room 2.x 时代（`androidx.room`）设计。
   → 只能作**结构参考**，不可复制代码，也不适配本项目 `androidx.room3:3.0.0`（后者本地字节码已含
   `createFromAsset`，无需第三方库）。
4. **配额/MMR/探索槽**：批 21 研究结论（并集是主流；每腿独立 min-max；「缺腿=0」在 α=0.5 下
   结构上限 0.5；K=64 不是瓶颈；本地批次 18/21 同口径零增益）**转引成立**；本轮引擎可用，
   复核到 OpenSearch 归一化原句与 OpenSearch **3.3 原生 MMR** 的参数/限制原文。
   **MMR 在本项目指认不出它消灭的具体失败**（本地 MISS 是「不在词面 512 域内」而非「结果冗余」，
   且 OpenSearch 自身明确 MMR 不支持 hybrid/bool 内的 knn）→ 不立项，仅登记备选。
   **探索槽（救援配额=1）**已有冻结预注册、结构上限 +2（119/129），是 ③ 内**优先级最高**的一项。
5. 立项建议与优先级见 §4。

---

## 1. SQLite FAQ Q19：原文与批次 29 做法的一致性判定

### 1.1 原文（本会话取得）

来源：<https://www.sqlite.org/faq.html>（WebFetch，本会话成功；以下为页面 Q19 原文，
省略号处为省略的过渡句）：

> **(19) INSERT is really slow - I can only do few dozen INSERTs per second**
>
> Actually, SQLite will easily do 50,000 or more INSERT statements per second on an average desktop
> computer. But it will only do a few dozen transactions per second. Transaction speed is limited by
> the rotational speed of your disk drive. …
>
> Transaction speed is limited by disk drive speed because (by default) SQLite actually waits until
> the data really is safely stored on the disk surface before the transaction is complete. …
>
> By default, each INSERT statement is its own transaction. But if you surround multiple INSERT
> statements with BEGIN...COMMIT then all the inserts are grouped into a single transaction. The time
> needed to commit the transaction is amortized over all the enclosed insert statements and so the
> time per insert statement is greatly reduced.
>
> Another option is to run PRAGMA synchronous=OFF. This command will cause SQLite to not wait on data
> to reach the disk surface, which will make write operations appear to be much faster. But if you
> lose power in the middle of a transaction, your database file might go corrupt.
>
> Update 2024-11-19: The text above is a really old answer. … But the gist of the answer above remains
> correct: Putting multiple operations inside a single transaction can improve performance
> dramatically by avoiding the overhead of transaction control after each individual operation.

补充权威页（同一站点，本会话 WebFetch 成功）——
<https://www.sqlite.org/pragma.html#pragma_synchronous>，`synchronous = OFF` 段原文：

> With synchronous OFF (0), SQLite continues without syncing as soon as it has handed data off to the
> operating system. If the application running SQLite crashes, the data will be safe, but the database
> might become corrupted if the operating system crashes or the computer loses power before that data
> has been written to non-volatile storage. On the other hand, commits can be much faster with
> synchronous OFF.

同页还写明适用场景与耐久性定级：

> Setting synchronous to OFF is a good option when creating a new database from scratch, in a scenario
> where the process of creating the database can be repeated if a power loss occurs in the middle, and
> when performance is critical.

以及耐久性矩阵中 `OFF` 在 Rollback Mode 与 WAL Mode 两栏均为 **“Not consistent”**。

### 1.2 批次 29 的实际做法（本会话实读代码）

| 做法 | 锚点（本会话 Read 实读） | 事实 |
|---|---|---|
| 多行 `VALUES` 批量写，不自开事务 | 转引 `docs/kb-batch29-2026-10-31.md:102`、`:109`（`KnowledgeSearchIndexBuilder.kt:156-158`，事务边界不变，仍在调用方 `withWriteTransaction` 内） | 546,839 行特征从逐行 `step()` 变为每批 300 行 |
| 二级索引后置 | `RoomKnowledgeImportMaintenance.kt:81-91`（`withDeferredFeatureIndexes`，进出各 `ensureFeatureIndexes()`，`finally` 无条件补建） | 窗口内表内容/主键不变，只缺访问路径 |
| 导入窗口内 `synchronous=OFF` + `cache_size=-32768` | `RoomKnowledgeImportMaintenance.kt:49-53`（先读旧值再设）、`:57-76`（`finally` 恢复）、`:72`（恢复失败仅 `Log.w`） | 连接级设置，进程退出消失 |
| 不碰 `journal_mode` / `temp_store` | `RoomKnowledgeImportMaintenance.kt:13`（`synchronous` 为连接级）、`:15-16`（`journal_mode` 是数据库级且与 Room 的 WAL 形态冲突）、`:17`（`temp_store` 不采用） | 保留 WAL 形态，不把日志级别降级 |
| 导入在后台协程，与学习记录就绪并行 | `SmartMistakeBookApplication.kt:752-758`（`applicationScope.launch { … BundledKnowledgeBaseInstaller.install(database) }`）、`:773`（失败横幅文案「错题和复习可以继续使用，自动分类会暂缓」）、`:792`（`studyRepository.initialize()` 另一条协程） | 导入期间应用**仍可正常使用**（会写用户数据） |
| 单写连接 | `RoomKnowledgeImportMaintenance.kt:44-46` 注释「Room 的写池只有一条连接，所以两次拿到的是同一条」 | 同一连接上的**所有**写都继承该 pragma |
| 快路径与全量路径 | `BundledKnowledgeBaseInstaller.kt:35`（起点补索引）、`:46-55`（快路径）、`:58-66`（解析在窗口外、窗口包裹 reconcile）、`:81-85`（`isInstallUpToDate`） | 只有 `contentVersion` 不一致时才走全量 |

### 1.3 逐条对照

| FAQ / PRAGMA 文档的说法 | 批次 29 做法 | 判定 |
|---|---|---|
| 「把多条 INSERT 包进一个事务，提交成本被摊薄」是首选建议（Q19 正文 + 2024-11-19 更新都保留这句） | 已按 64 节点一块分组（共 60 个插入块事务），且**未**把多个块合并成一个大事务 | **一致**（且没有为了更快去改事务边界，避免长写事务） |
| `synchronous=OFF` 会让写「看起来快」，代价是掉电/OS 崩溃时库文件可能损坏 | 导入窗口内对写连接设 `OFF`，`finally` 恢复 | **性能侧一致**；「损坏」代价由本项目承担，**不是 Q19 背书** |
| 适用场景：**从零建库** + 过程可重复 | 冷装（`-wipe-data` → install，本批 F6 三次读数正是这条路径）确实是从零建库且可重复 | **冷装路径一致** |
| 同上 | 升级路径：库里已有用户数据，`contentVersion` 变化触发全量 `reconcile`，期间同一写连接仍为 `OFF`；而「重跑导入」**不能**把用户数据重建出来 | **落在字面许可之外**（推断：批 29 未区分这两条路径，`docs/kb-batch29-2026-10-31.md:236` 明写「未复测升级安装路径」） |
| 耐久性矩阵：`OFF` = Not consistent（两种日志模式） | 保留 WAL（不降级 `journal_mode`），但同步档位仍为 `OFF` | **部分一致**：不降级日志模式 ≠ 恢复一致提交 |

### 1.4 判定

**部分一致。** 可以写成「批 29 采纳了 FAQ Q19 的首选建议（事务分组），并在一个明确受限的窗口内
使用了 Q19 提到的第二个选项（`synchronous=OFF`）」；**不能**写成「批 29 的做法符合 SQLite 官方建议」
——因为：

1. Q19 只陈述了 `OFF` 的**代价**（可能损坏），没有给出「只要导入可重建就可以用」的许可；
   给出许可的是 PRAGMA 页，而它的许可条件是「从零建库 + 过程可重复」。
2. 本项目的「可重复」只覆盖**知识库派生行**（`3,280` 节点 / `65,016` 材料 / `546,839` 特征行，
   `docs/research/2026-10-31-kb-rescue-quota-prereg.md:33` 口径），**不覆盖**同一文件里的用户数据
   （错题、复习、对话等实体同属一个 `smart-mistake-book.db`，`StudyDatabase.kt:150-243` 实体表）。
   SQLite 的损坏是**文件级**的，不会只损坏派生行。
3. 导入与正常使用**并行**（`SmartMistakeBookApplication.kt:752-792`），且写池只有一条连接
   （`RoomKnowledgeImportMaintenance.kt:44-46`）。**推断**：导入窗口内的用户写也会落在 `OFF` 上
   ——本轮未实测，但代码结构上没有隔离。

### 1.5 残余风险与最小验证（不改码，建议立为验证项）

| # | 风险 | 现有登记 | 建议验证（只读/设备） |
|---|---|---|---|
| R1 | 升级路径（已有用户数据）在 `OFF` 窗口内掉电/被系统杀进程 | `docs/kb-batch29-2026-10-31.md:236`（未复测升级路径） | 旧库 + 新 `contentVersion` 冷装后中途 `am force-stop`，重启跑 `PRAGMA integrity_check` + 关键表行数 |
| R2 | 窗口内并发用户写 | 未见既有登记（检索范围：`docs/kb-batch29-2026-10-31.md` §8 残留 1–9） | 导入期间连续提交复习/错题写入，结束后 `integrity_check` + 行数对账 |
| R3 | pragma 恢复失败分支 | `docs/kb-batch29-2026-10-31.md:232`（未实测） | 单测注入 `writerPragmaValue` 抛错，断言 `Log.w` 且连接仍可用 |
| R4 | 索引窗口崩溃自愈 | `docs/kb-batch29-2026-10-31.md:231`（未做故障注入） | drop 后杀进程，重启用 `PRAGMA index_list` 断言两条索引已补回 |
| R5 | 「冷装读数」外推到低端真机 | `docs/kb-batch29-2026-10-31.md:229`（emulator / API 34，余量 13.7%） | 真机或低 API AVD 同口径冷装 |

> 结论口径：R1/R2 不是「已发生的缺陷」，而是**官方许可边界之外的未验证窗口**。
> 在补测之前，代码注释「导入是可重建的幂等步骤，不需要默认的持久化强度」
> （`docs/kb-batch29-2026-10-31.md:77`）应读作**仅对冷装路径成立**。

---

## 2. Room prepopulate / createFromAsset 与 RoomAssetHelper

### 2.1 取数记录（逐通道如实）

| 通道 | 目标 | 结果 |
|---|---|---|
| WebFetch | `https://developer.android.com/training/data-storage/room/prepopulate` | **失败两次**：`fetch failed: Connect Timeout Error … timeout: 10000ms (UND_ERR_CONNECT_TIMEOUT)` |
| `searchd.extract_content` | 同一 URL | **成功**，返回完整正文（导航 + 正文），页面自述 `Last updated 2026-10-23 UTC`；正文内 API 链接指向 `androidx/room3/RoomDatabase.Builder#createfromasset` |
| `gh api repos/ueen/RoomAssetHelper` | 社区参考库 | **成功**（元数据见 §2.4） |
| `gh api .../readme` | 同上 | **成功**（README 摘录见 §2.4） |
| `javap` room3-runtime 3.0.0 | 本地字节码 | **成功**：`createFromAsset(String)`、`createFromFile(File)`、`createFromInputStream(Callable)` 均存在（含 `PrepackagedDatabaseCallback` 重载） |

> 因此本节**不再整体标「待验证」**：官方语义已取到正文原文，且 room3 API 有本地字节码佐证。
> 仍标 `待验证` 的只有：Room 校验 schema 的**具体机制/错误类型**（页面只写 “validates”，未写身份哈希细节）、
> 以及 room3 3.0 与 2.x 在预置库行为上的差异（页面未单列）。

### 2.2 官方语义（原文摘录 + 事实）

原文（`developer.android.com/training/data-storage/room/prepopulate`，本会话第二通道取得）：

> If you want your app to start with a database that's already loaded with a specific set of data, you
> can prepopulate the database. In Room, you can use APIs to prepopulate a database at initialization
> with contents from a prepackaged database file in the device's file system.

> **Note:** In-memory Room databases don't support prepopulating the database using `createFromAsset`
> or `createFromFile`.

> **Note:** When prepopulating from an asset, Room validates the database to ensure that its schema
> matches the schema of the prepackaged database. To use your database's schema as a reference when you
> create the prepackaged database file, export the schema.

> Room doesn't use the prepackaged database file, because Room uses prepackaged database files only in
> the case of a fallback migration.

> If your prepackaged database file is on version 2, Room determines that it doesn't match the target
> version and doesn't use it for the fallback migration.

归纳成五条硬事实：

1. **只有三条入口**：`createFromAsset`（`assets/` 相对路径）、`createFromFile`（文件系统，Room 复制）、
   `createFromInputStream`（room3 字节码实证存在，本地 `javap` 见 §2.1）。
2. **schema 必须匹配**：Room 会校验预置库 schema 与 `@Database` 声明；官方建议**导出 schema** 作为制作参考。
3. **生效时机极窄**：预置文件只在「设备上还没有库」的首次创建，或「fallback migration」时被使用；
   **实现了迁移路径时，Room 不使用预置文件**。
4. **版本必须等于目标版本**：fallback 场景下，预置文件版本 ≠ 目标版本 ⇒ 直接不用。
5. **内存库不支持**（与本项目无关，登记以免误用）。

### 2.3 本项目现状（本会话实读）

| 事实 | 锚点 |
|---|---|
| `STUDY_DATABASE_VERSION = 71`，`exportSchema = true` | `core/database/.../StudyDatabase.kt:139`、`:242-243` |
| 导出 schema 在树：`71.json`（372 KB） | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` |
| 迁移链 1→71 已实现，两处 builder 装配 | `StudyDatabase.kt:340`（`addMigrations(...)`）、`:426` |
| **无** `fallbackToDestructiveMigration` | 本会话 `rg -n "fallbackToDestructiveMigration" core/database/src/main/kotlin` → **0 命中** |
| **无** 任何 `createFromAsset` / `createFromFile` / `prepopulate` 用法 | 本会话 `grep -rn "createFromAsset\|createFromFile\|prepopulate" --include=*.kt --include=*.kts --include=*.toml .` → **0 命中** |
| 内容装配走「随包资源 + reconcile」，且 KDoc 声明这是**发布后更新知识库的唯一通道** | `BundledKnowledgeBaseInstaller.kt:15-22`、`:58-66`、`:131-154` |
| 安装与用户使用并行（后台协程） | `SmartMistakeBookApplication.kt:752-792` |
| F6 现状：三次冷装 47,504 / 51,775 / 51,646 ms（MET），余量 13.7% | `docs/kb-batch29-2026-10-31.md:229`、§7 |

### 2.4 RoomAssetHelper 评估（`gh api` 实证）

```text
{"archived":false,"default_branch":"master","desc":"A helper library to help using Room with
 existing pre-populated database.","lang":"Kotlin","license":null,"name":"ueen/RoomAssetHelper",
 "pushed":"2020-05-09T10:50:33Z","stars":7}
```

README（本会话 `gh api .../readme` 实取）要点：

- 「Thanks to MikeT … An Android helper class to manage database creation and version management
  using an application's raw asset files.」
- 用法是**替换** `Room.databaseBuilder`：`RoomAssetHelper.databaseBuilder(ctx, AppDatabase::class.java, "chinook.db", 1)`，
  资产路径与库名约定绑定；额外提供 `TablePreserve` **选择性迁移**（升级时保留指定表/列的用户数据）。
- 依赖 `com.github.ueen:RoomAssetHelper:1.2`（JitPack）。

评估：

| 维度 | 事实 | 结论 |
|---|---|---|
| 许可证 | `license: null`（仓库无 SPDX 许可证） | **不可复制代码**；只能读思路 |
| 维护度 | 最后提交 2020-05-09（本会话只取最近 3 条提交，均为同日） | 6 年半停更，无 issue 响应证据 |
| 技术栈 | README 以 `androidx.room` 2.x 为基（`Room.databaseBuilder` 同签名） | 本项目 `androidx.room3:3.0.0`（`gradle/libs.versions.toml:12,77-79`）；兼容性 `待验证`，但**不需要**——room3 自带 `createFromAsset` |
| 可借鉴点 | 「选择性迁移/保留用户表」是预置库升级时的真问题形态 | 我们的迁移链已实现，不需要该机制；若未来做预置库，C2/C3 才是主矛盾 |

> **结论**：RoomAssetHelper 只能作为「预置库 + 选择性迁移」的**形态参考**，不构成引入理由。

### 2.5 「F6 下一级：预置 DB」约束清单（若将来立项，必须先满足）

| # | 约束 | 依据 |
|---|---|---|
| C1 | 资产 `user_version`/schema 必须等于 `STUDY_DATABASE_VERSION`（当前 71）；**每次 schema bump 都让资产失效**，需重出并重跑全量回归 | 官方「Room validates … schema matches」+ 本地 `StudyDatabase.kt:139` |
| C2 | 收益只发生在**首装**；已装用户走已实现迁移，官方明写此时**不用**预置文件 | 官方原文（§2.2 事实 3/4）+ 本仓迁移链已实现、无 destructive fallback |
| C3 | `content_install_state.contentVersion` 必须在**构建期**写入资产，否则首启快路径判定不命中，仍走全量 reconcile（白做） | `BundledKnowledgeBaseInstaller.kt:81-85`、`:46-55` |
| C4 | 资产必须由**当前 reconcile 代码路径**产出（内容管线唯一），不得另写一条构建脚本模拟 | `BundledKnowledgeBaseInstaller.kt:15-22`「唯一通道」KDoc；批次 29 等价门同纪律 |
| C5 | 资产必须是**已 checkpoint / 干净关闭**的单文件；否则首启丢已提交行 | 批 34 研究实测（转引，`docs/research/2026-10-29-snapshot-identity-wal-and-r8.md:88-92`：只拷主库 → `no such table`；干净关闭后 `-wal`/`-shm` 消失） |
| C6 | 构建期 `PRAGMA integrity_check` + 行数/指纹 + sha 钉值（节点 3,280 / 材料 65,016 / 特征 546,839 至少三项） | 复用 `docs/research/2026-10-31-kb-rescue-quota-prereg.md:26-49` 的身份口径 |
| C7 | APK 体积增量必须实测（主包 4.5 MB + `.vec` 26.1 MB 已随包，预置库再叠加一份派生内容；**本轮未测**） | 不得猜；见 §5 未验证 |
| C8 | 预置库**绕过** `pack.validate()` 与 reconcile 的「坏对象只跳过自己」 | `BundledKnowledgeBaseInstaller.kt:62-63`、`:131-154` |
| C9 | 错版本资产的失败形态必须先做负面测试（抛异常还是静默忽略），且**禁止**用 `fallbackToDestructiveMigration` 兜底 | 官方只写「不使用」；销毁式回退会删用户数据 |
| C10 | 首启日志口径要能区分「资产复制」与「全量导入/快路径」，否则 F6 分段读数失去可比性 | `BundledKnowledgeBaseInstaller.kt:49-53`、`:67-70` 现有两种日志 |

### 2.6 不实施的理由（本轮结论）

1. **它要消灭的失败没有被测量**（第 12.2 条：新增必须指认具体失败）。F6 已 MET（47.5–51.8 s，
   门槛 60 s，余量 13.7%）；预置库消灭的假设失败是「低端真机首装越过 60 s」——`待验证`，
   现有唯一读数是 emulator/API 34（`docs/kb-batch29-2026-10-31.md:229`）。
2. **schema 版本耦合是主要成本**：`v=71`、迁移链 1→71、`exportSchema` 已开；每加一版都要
   重出资产（C1）+ 重跑一致性/等价门（C4/C6），而收益只覆盖首装（C2）。
3. **构建链成本**：需要一个可复现的宿主侧构建（打开同版本 schema → 跑 reconcile → checkpoint →
   integrity → sha 钉值 → 打包），且必须与内容包版本联动（内容包已迭代到 v24，
   `docs/research/2026-10-31-kb-rescue-quota-prereg.md:30`）。这条链本身
   就会成为新的「未验证内容来源」（与「唯一通道」KDoc 冲突）。
4. **第三方库不适用**：RoomAssetHelper 无 license、停更、Room 2.x；room3 自带 `createFromAsset`。

**重开条件（写死）**：① 真机/低 API 冷装读数 >60 s，或 F6 余量 <5%；② 或首装耗时被产品立为独立指标；
③ 且 C1–C10 能落成可复跑的门（资产生成纳入现有 gate）。

---

## 3. 配额 / MMR / 探索槽

### 3.1 批 21 研究结论（转引，本轮未复跑）

来源：`docs/kb-batch21-2026-10-26.md:118-127`（其上游为
`docs/research/2026-10-26-dense-union-fusion-precedents.md`）。

| 结论 | 转引位置 | 与本项目的关系 |
|---|---|---|
| **并集是主流**：Weaviate 源码注释「The two sets are unioned, never intersected」；Qdrant 融合集合 `D` = 各分支并集 | 先例报告 `:14`、`:46-56` | 本地「词面集 ∪ dense-only」方向本身不逆主流 |
| **每腿独立 min-max 是主流**（Weaviate `relativeScoreFusion`、OpenSearch per-clause normalization） | 先例报告 `:15`、`:77-88` | 与 `DenseFusion.minMax` 一致 |
| **「缺腿=0」是主流但有结构代价**：OpenSearch 原文「contribute `0` to the numerator but still counts in the denominator」；α=0.5 时 dense-only 融合分上限 **0.5** | 先例报告 `:16-17`、`:186-220`（算式实跑） | 解释批次 18/21「追加发生但 top-5 不变」 |
| **K=64 不是瓶颈**：3/4 absent 目标 dense 名次 ≤7；第 4 个（`等和线`）全科 336 | 先例报告 `:19`；`docs/kb-batch21-2026-10-26.md:124` | 再跑一遍同口径 K=64 无意义 |
| **本地同口径已两次零增益**：批次 18 lever-1（100/129）与批次 21 并集臂（117/129，与 A1 逐位相同） | `docs/kb-batch21-2026-10-26.md:9`、`:184` | 强先例：不应重复同一臂 |
| **建议三条替代路线**：补真实词面分 / **独立救援配额（探索位）** / 提高 α | 先例报告 `:261-264` | 探索槽的直接出处；α 需先修零漂移装置 |

> 注：批 21 报告本身是 `parked-gate-red / 未采纳未回滚`，但**其后已逐字回滚结案**
> （`docs/kb-batch21-2026-10-26.md:280-285`），基线维持 A1 117/129。转引时不要把「并集实现还在树」当成现状。

### 3.2 本地现状（本会话实读）

| 事实 | 锚点 |
|---|---|
| α=0.5 写死；每腿独立 min-max；`high <= low` ⇒ 全体 0 | `DenseFusion.kt:21`、`:24-31`、`:32` |
| 缺腿 `?: 0.0`，且不参与该腿 min-max | `DenseFusion.kt:52-57` |
| **救援配额（探索槽）只有预注册，未实现** | 本会话 `rg -n "DenseRescueSelector\|rescueSelector" core app --glob '!**/build/**' -l` → **0 命中**；预注册见 `docs/research/2026-10-31-kb-rescue-quota-prereg.md` |
| 预注册已冻结：配额=1、只换第 5 槽、I1–I10 不变量、上限 119/129、装置须独立预注册 | `2026-10-31-kb-rescue-quota-prereg.md:57-135`、`:222-235`、`:169-178` |
| **MMR 在本地零实现、零口径** | 同上 rg 无命中；本会话 `grep -rln "MMR" docs/research docs/current` → **0 命中**（批次 21 报告亦未含 MMR） |

### 3.3 本轮引擎复核（可用性 + 复核到什么）

- `mcp__searchd__engine_status`：bing / brave / duckduckgo / yahoo 均 `enabled`、`suspended=false`；
  qwant/google/mojeek/startpage 在禁用名单内（引擎自带说明）。→ **引擎可用**。
- 两问实际返回：bing（MMR 问）与 yahoo（OpenSearch 归一化问）；brave/duckduckgo 报
  `No results found`、mojeek 超时（一次失败不等于来源不存在，见引擎事件）。
- **复核成功**：OpenSearch Normalization processor 原句（yahoo 摘要 + 正文）：
  「Documents that the clause did not return contribute nothing to its statistics. … the normalized
  scores depend on how many results each clause returns.」
  → 与批 21 转引一致。
- **新增取到（批 21 未覆盖）**：OpenSearch **3.3 原生 MMR** 正文（`extract_content` 实取，见 §3.4）。
- **未复核**：Weaviate `searcher.go` 注释、Qdrant `D` 定义、Bruch 论文——沿用批 21 转引，
  本轮未重新抓取（标 `转引`，不外推为新证据）。

### 3.4 MMR 先例与它在本项目消灭不了什么

**外部事实（本会话实取，单一来源 → 标 `待验证` 的细节已注明）**：

- OpenSearch 3.3 起提供原生 MMR rerank（`docs.opensearch.org/latest/vector-search/specialized-operations/vector-search-mmr/`）：
  - 公式：`MMR = (1 − λ) × relevance_score − λ × max(similarity_with_selected_docs)`；
    `diversity`（即 λ）默认 **0.5**，1=最大多样性、0=关闭；
  - `candidates` 默认 **3 × size**（先取候选再 MMR 重排）；
  - **限制（原文）**：MMR 只支持 `knn` 或 `neural` 作为**顶层查询**；
    「If a knn or neural query is nested inside another query type (such as a bool query or hybrid
    query), MMR is not supported.」→ **混合检索的融合结果上不能直接套用**。
- Qdrant：Rust 客户端有 `Mmr` 结构体（`docs.rs/qdrant-client/.../struct.Mmr.html` 检索命中），
  官方博客有 MMR 教程（`qdrant.tech/blog/mmr-diversity-aware-reranking/`）。
  → 参数化细节 `待验证`（仅命中标题/摘要，未逐页取正文）。

**为什么在本项目不立项**：

1. **指认不出具体失败**（第 12.2 条）。本项目的 top-5 缺口是 `absent`
   （目标不在词面 512 候选域内，`docs/research/2026-10-31-kb-rescue-quota-prereg.md:227`），
   不是「5 条结果互相冗余」。MMR 只在**已有候选集内部**重排，无法把一个不在集合里的节点变出来；
   若要把它作用到「全科原子节点」上，就变成改主序，与既有约束「前 4 槽不变 / 主融合分域不变」
   （预注册 I2/I3）直接冲突。
2. **先例本身不支持该用法**：OpenSearch 明确 MMR 不适用于 hybrid/bool 嵌套——我们的场景恰是
   混合融合后的结果。
3. **代价**：MMR 需要候选两两相似度（本地有 512 维 dense 向量可算），但收益无任何本地证据；
   属「为了多样性而多样性」的多余机制。

> 结论：**登记为备选，不立项**。重开条件：出现「同一查询返回多条近重复节点」的实测证据，
> 且能给出该失败的可复现夹具。

### 3.5 探索槽（救援配额=1）的判据形状

已在 `docs/research/2026-10-31-kb-rescue-quota-prereg.md` 冻结，关键形状（转引）：

- 配额=1，只替换第 5 槽，前 4 槽成员与次序不变；`result.size < 5` 不补位（`:57-67`、`:93-113`）；
- 关闭时与 v24 逐字节同行为（`null` selector，`:130-135`）；
- 结构上限 **119/129（+2）**，且明确「上限是算术上界，不是预期读数」（`:222-235`）；
- 采纳门：主集 ≥118/129 + 24 章不劣 + 负向守卫 CI 上界 ≥0 + 零漂移 0 + 20k/seed0 显著性（`:143-153`）；
- **前置**：判官装置必须单独预注册（JVM 镜像 `RetrievalBenchmark.kt:482` 不认 dense-only id，
  否则零漂移 `NOT_COMPUTABLE`，本臂不判读，`:169-178`）。

---

## 4. 结论：值得立项的项、前置条件与优先级

| 项 | 是否立项 | 前置条件 | 优先级 | 依据 |
|---|---|---|---|---|
| **SQLite 导入耐久性补测**（升级路径 + 并行用户写 + 故障注入 + 恢复失败分支） | **建议立项**（验证型，非改码） | 可重复的旧库→新 `contentVersion` 冷装脚本；`integrity_check` + 行数口径 | **P1** | §1.4–§1.5；官方许可边界外 |
| **探索槽（救援配额=1）** | **已有预注册，待执行** | 装置独立预注册（JVM 镜像感知 dense-only id）；v24 资产复算三目标 dense 名次 | **P1** | §3.5；`2026-10-31-kb-rescue-quota-prereg.md:169-178`、`:222-235` |
| 预置 DB（F6 下一级） | **暂不立项**（条件触发） | C1–C10 全部落成门；真机/低 API 读数触发 | **P3** | §2.5–§2.6 |
| MMR 多样性重排 | **不立项**（登记备选） | 需先出现「近重复结果」实测证据 | **P4** | §3.4 |
| 并集 K=64 复跑 | **不立项** | —（批次 18/21 两次零增益） | — | §3.1 |
| 提高 α / 缺腿补真实分 | **不立项（暂缓）** | 先修零漂移装置，再按 α×缺腿网格单独预注册 | **P3** | 先例报告 `:265-266` |
| 引入 RoomAssetHelper / 任何外部依赖 | **不立项** | —（license 缺失、停更、room3 自带 API） | — | §2.4 |

**一句话**：本轮唯一值得立刻做的是 ① 的**验证**（让一个已知的未验证窗口变成证据）和 ③ 的
**探索槽执行**（已有冻结预注册，只差装置预注册）；预置库与 MMR 都不满足「新增必须指认具体失败」。

---

## 5. 未验证项与来源

### 5.1 未验证（逐条）

1. **未在本会话运行任何 Gradle / 设备 / 仪器化测试**；§1.5 的 R1–R5 全部是验证设计，未执行。
2. **设备库的 `journal_mode` 未读回**。代码注释称 Room 为 WAL 形态（`RoomKnowledgeImportMaintenance.kt:15-16`），
   本轮未跑 `PRAGMA journal_mode` 举证 → `待验证`。
3. **「导入窗口内用户写继承 `synchronous=OFF`」是推断**：依据是单写池注释（`:44-46`）与后台并行安装
   （`SmartMistakeBookApplication.kt:752-792`），未做并发实测。
4. **Room 校验预置库 schema 的具体机制/失败类型**：官方页面只写 “validates”，未写 identity hash 细节
   → `待验证`（本节结论不依赖该细节）。
5. **APK 体积增量未测**：主包 4.5 MB / `.vec` 26.1 MB 为既有身份数字，预置库自身大小与增量均未知。
6. **room3 3.0 与 Room 2.x 在预置库行为上的差异**：未找到官方对照说明 → `待验证`。
7. **Qdrant MMR 的参数细节**：只命中标题/摘要（docs.rs、博客），未逐页取正文 → `待验证`。
8. **Weaviate / Qdrant / Bruch 的原始引用**：沿用批 21 转引，本轮未重新抓取。
9. **`contentVersion` 变化频率未统计**：只读到 v24 的基线身份（`2026-10-31-kb-rescue-quota-prereg.md:30`），
   没有对历史内容包做全量计数。
10. **低端真机 F6**：无真机读数，全部结论只对 emulator/API 34 成立。

### 5.2 来源

外部（本会话实取）：

- <https://www.sqlite.org/faq.html>（Q19 原文，WebFetch 成功）
- <https://www.sqlite.org/pragma.html#pragma_synchronous>（`OFF` 段与适用场景原文，WebFetch 成功）
- <https://developer.android.com/training/data-storage/room/prepopulate>（WebFetch 两次超时；正文经
  `searchd.extract_content` 取得，Last updated 2026-10-23）
- <https://docs.opensearch.org/latest/vector-search/specialized-operations/vector-search-mmr/>（MMR 正文，`extract_content` 实取）
- <https://docs.opensearch.org/latest/search-plugins/search-pipelines/normalization-processor/>（归一化原句，检索摘要复核）
- <https://github.com/ueen/RoomAssetHelper>（`gh api` 元数据与 README 实取）
- <https://qdrant.tech/blog/mmr-diversity-aware-reranking/>、`docs.rs/qdrant-client` 的 `Mmr`（仅检索命中，`待验证`）

仓内（本会话实读/实跑）：

- 代码：`core/database/.../RoomKnowledgeImportMaintenance.kt`、`core/data/.../BundledKnowledgeBaseInstaller.kt`、
  `core/database/.../StudyDatabase.kt`、`core/data/.../dense/DenseFusion.kt`、
  `app/.../SmartMistakeBookApplication.kt`、`gradle/libs.versions.toml`、
  `core/database/schemas/.../71.json`
- 文档：`docs/kb-batch29-2026-10-31.md`、`docs/kb-batch21-2026-10-26.md`、
  `docs/research/2026-10-26-dense-union-fusion-precedents.md`、
  `docs/research/2026-10-31-kb-rescue-quota-prereg.md`、
  `docs/research/2026-10-29-snapshot-identity-wal-and-r8.md`（WAL 行为为**转引**，本轮未复跑）
- 命令（原样）：
  - `gh api repos/ueen/RoomAssetHelper --jq '{name:.full_name,desc:.description,stars:.stargazers_count,pushed:.pushed_at,license:.license.spdx_id,archived:.archived,lang:.language,default_branch:.default_branch}'`
  - `gh api repos/ueen/RoomAssetHelper/readme --jq '.content' | base64 -d`
  - `cd /tmp/room3chk && unzip -o -q "/c/Users/听云/.gradle/caches/modules-2/files-2.1/androidx.room3/room3-runtime-android/3.0.0/5b34acf1a770ab2ee96c9d6c8a13f229522af0c7/room3-runtime.aar" classes.jar && javap -classpath classes.jar 'androidx.room3.RoomDatabase$Builder' | grep -i "createFrom"`
  - `rg -n "DenseRescueSelector|rescueSelector" core app --glob '!**/build/**' -l`（0 命中）
  - `rg -n "fallbackToDestructiveMigration" core/database/src/main/kotlin`（0 命中）
  - `grep -rn "createFromAsset\|createFromFile\|prepopulate" --include=*.kt --include=*.kts --include=*.toml .`（0 命中）
  - `grep -rln "MMR" docs/research docs/current`（0 命中）
