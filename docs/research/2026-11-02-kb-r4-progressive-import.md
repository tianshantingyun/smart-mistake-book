# KB R4：首启渐进导入、部分可用与验收口径（2026-11-02）

> **性质**：外部研究 + 本仓设计建议。只写本文件；未改源码、未跑 Gradle/设备/JVM。
> **本仓基线**：v26 包 65,021 材料 / 3,280 原子节点 / 40,841 别名；首启全量导入实测 **47,504 / 51,775 / 51,646 ms**（中位 51,646，最大 51,775）；四科索引段 5,436–5,928 ms；JSON 解析 20.7–21.2 s；其余调和 20.8–25.2 s（`docs/kb-acceptance-2026-10-17.md:824`、`docs/kb-batch29-2026-10-31.md:46-64`）。冷启 `TotalTime` 2,970 / 2,829 / 3,014 ms。
> **方法**：优先官方文档（developer.android.com / developer.android.google.cn、sqlite.org、nngroup.com）；每个关键数字给原文与 URL；单一来源标「待验证」；本仓事实逐条读源码，不靠旧报告转述。
> **结论级别**：§1 为外部事实与本仓实读；§2 为方案判断；§3 为**建议**（含推断数字，明确标注）；§4 为未验证项。本文不声称任何改动已完成。

---

## 0. 结论摘要

1. **Play Vitals 的「过慢」是启动阈值，不是导入阈值**：冷启 ≥5 s、温启 ≥2 s、热启 ≥1.5 s 被列为 excessive，口径是 **TTID**（`developer.android.com/google/play/vitals/launch-time`）。本仓 51.6 s 是「知识库首次可查」等待，不是 TTID；两者不能混报。
2. **NN/g 的三个感知阈值（1993）**：0.1 s  instantaneous、1 s 思维不断流、10 s 注意力仍停留。它们适合约束「首查可交互」，不适合直接当后台导入的完成时限。
3. **没有官方「渐进导入」端到端 API**。可用的官方原语只有：TTID/TTFD + `ReportDrawn` 的占位先显示、WorkManager 的持久任务与进度、Room 的 `createFromAsset`/`createFromFile` 预置库、Play Asset Delivery 的三种交付模式。两阶段导入是这些原语的工程组合，不是现成能力。**预置库在本仓实测解压后 ~407 MB（§1.5），不是可行的主方案。**
4. **长事务不是「越大越好」**：官方明确「批量放进一个事务」比逐条隐式事务快一个数量级；但 SQLite 同时只有一个写者，长事务会占住唯一写连接、阻止 WAL 重置，并让读性能随 WAL 增长而下降。正确形态是**短事务分块 + 块间让出**，本仓 `REBUILD_NODE_CHUNK_SIZE=64` 已是这个形态。
5. **本仓最大的两个真问题**（源码实读）：
   - 导入期把写连接设为 `PRAGMA synchronous = OFF`（`RoomKnowledgeImportMaintenance.kt:52`）。SQLite 原文说 OFF 在操作系统崩溃/断电时**可能损坏数据库**；这个库同时存学生错题，风险面不止「重导一次」。建议改 `NORMAL`（WAL 下防损坏，但可能回滚最近未 checkpoint 的提交）。
   - 全量解析（20.7–21.2 s）发生在**任何一行写库之前**；65k 材料的 upsert 与绑定又在索引之后（`BundledKnowledgeBaseInstaller.kt:59-61`、`RoomKnowledgeContentReconciler.kt:85`）。索引只占 5.4–5.9 s，真正让首查等 50 s 的是「解析 + 材料/绑定残差」。
6. **推荐两阶段边界**：Phase A 只解析主包（4.3 MB）→ 写节点/来源/关系/绑定 → **按科**建特征索引并逐科发布 READY；Phase B 逐卷（25 个 2025 侧车 + 2020 的 1 个侧车）流式解析并分块写材料/绑定，完成才写 `content_install_state` 完成戳。首查在 Phase A 的**第一科 READY** 时即可用，其余科与材料在后台补齐。
7. **建议验收数字**（工程建议，非行业标准）：首查 p95 ≤5 s（硬上限 10 s）；全量材料 p95 ≤45 s（硬上限沿用现有 60 s）；导入期间查询 p95 ≤250 ms、max ≤1 s；部分结果 100% 带 completeness 标注，0 次「静默空结果」。
8. **不要用 3 次冷装报 p95**。p95 至少需要 20 个样本（经验下限；严格分位估计还需更多）；现有 3 次只能报 median/max。验收协议必须写死样本数、装置、同 APK/同内容包。

---

## 1. 事实

### 1.1 冷启动与响应阈值：准确出处、原文、年份

| 指标 | 数字 | 原文（逐字） | 来源 | 年份/访问 | 适用范围 |
|---|---:|---|---|---|---|
| Play Vitals 冷启「过慢」 | **≥5 s** | “Cold startup takes 5 seconds or longer.” | [App startup time \| Android vitals](https://developer.android.com/google/play/vitals/launch-time) | 页面无出版年；访问 2026-11-02 | App 启动，**TTID**；不是后台导入 |
| Play Vitals 温启「过慢」 | **≥2 s** | “Warm startup takes 2 seconds or longer.” | 同上 | 同上 | 同上 |
| Play Vitals 热启「过慢」 | **≥1.5 s** | “Hot startup takes 1.5 seconds or longer.” | 同上 | 同上 | 同上 |
| Play Vitals 口径 | TTID | “Android vitals uses the time to initial display (TTID) metric.” | 同上 | 同上 | 只测首帧，不测内容全部可用 |
| NN/g 瞬时感 | **0.1 s** | “0.1 second is about the limit for having the user feel that the system is reacting instantaneously, meaning that no special feedback is necessary except to display the result.” | [Response Times: The 3 Important Limits](https://www.nngroup.com/articles/response-times-3-important-limits/) | **1993-01-01**，Jakob Nielsen | 通用人机响应 |
| NN/g 思维不断流 | **1 s** | “1.0 second is about the limit for the user's flow of thought to stay uninterrupted, even though the user will notice the delay.” | 同上 | 同上 | 通用人机响应 |
| NN/g 注意力保持 | **10 s** | “10 seconds is about the limit for keeping the user's attention focused on the dialogue.” | 同上 | 同上 | 通用人机响应 |
| Android TTID | 首帧 | “TTID is the time it takes to display the first frame of the app's UI.” | [App startup time](https://developer.android.com/topic/performance/issues/launch-time) | 页面标注 2026-10-23；访问 2026-11-02 | 系统可自动测 |
| Android TTFD | 可交互 | “TTFD is the time it takes for an app to become interactive for the user.” | 同上 | 同上 | 需 app 调 `reportFullyDrawn` |

补充原文（同一页）：

> “Two important metrics for determining app startup are time to initial display (TTID) and time to full display (TTFD). … TTID lets the user know that the app is loading, and TTFD is when the app is actually usable.”

> “Load and display your UI with placeholder data first, and then later update visual properties that are dependent on bitmaps and other resources.”

**判读边界**（必须写清，否则会把 51.6 s 误报成 Play Vitals 违规）：

- Play Vitals 的 5 s 是**启动**的 excessive 线，测的是 TTID；后台导入再慢，不会直接进这个指标。它在本仓的意义是：**首帧不能因为导入被拖到 5 s 以上**；当前冷启 2.8–3.0 s，已经接近这条线，但没有 p95 读数。
- NN/g 的 1 s / 10 s 是**用户感知**阈值，不是 Google 的验收门。用它约束「学生第一次查知识库要等多久」是合理的类比，但要标明是类比。
- 上述 Play Vitals 阈值只有**一个官方来源**；2018 年二手文章复述了同一「Slow cold start (5 seconds or more)」口径（[Medium, 2018-05-21](https://medium.com/@gauravbansal_13551/android-vitals-e13b6e89144)），但不是独立官方来源。**待验证**：Play Console 报表是否按 session/device/day 聚合、是否要求 p95。
- NN/g 文章是**单一官方原文**；它的三个数字被广泛引用，但本文未找到第二个同等级原始来源。按「单一来源」标注，不当作 Google 标准。

**把 51.6 s 放进这两套阈值里**：

- 对 **Play Vitals**：不直接触发「slow cold start」——本仓冷启 `TotalTime` 2.8–3.0 s，TTID 没有 p95 读数；导入在后台。若把 51.6 s 报成 Play Vitals 违规，是**口径错误**。
- 对 **NN/g**：51.6 s 是 10 s 注意力上限的 **5 倍以上**；学生第一次查知识库时，这不是「慢」，是「离开」。所以产品上必须把「首查可用」从 51.6 s 拉到 ≤5 s（硬上限 10 s），而不是只满足「全量 ≤60 s」。

### 1.2 渐进式内容就绪：官方到底提供了什么

**没有**官方文档给出「数据库先部分可查、索引后台补」的端到端模式。检索到的官方能力是四块原语：

1. **占位先显示、内容后更新**（Android app startup）：
   > “Load and display your UI with placeholder data first, and then later update visual properties that are dependent on bitmaps and other resources.”
   以及 `ReportDrawn` / `ReportDrawnWhen` / `ReportDrawnAfter` 用于声明「何时算可交互」。这支持 UI 层渐进，不解决数据库层部分可见。

2. **WorkManager：可持久、可观察进度的后台任务**：
   > “WorkManager is intended for work that is required to run reliably even if the user navigates off a screen, the app exits, or the device restarts.”
   > “WorkManager takes care of ensuring that this work persists and is rescheduled across device reboots.”
   > “`ListenableWorker` now supports the `setProgressAsync()` API, which allows it to persist intermediate progress.”
   > “Progress information can only be observed and updated while the `ListenableWorker` is running.”
   来源：[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)、[Observe intermediate worker progress](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe)。这支持「后台补齐 + 进度条」，但 WorkManager 不提供「部分数据库可查」的语义。

3. **Room 预置库：原子基线，不是渐进**：
   > `.createFromAsset("database/myapp.db")` 的参数是 “relative path from the `assets/` directory to the prepackaged database file”.
   > `createFromFile` “creates a copy of the designated file rather than opening it directly”.
   > Room “validates the database to ensure that its schema matches the schema of the prepackaged database”.
   来源：[Prepopulate your Room database](https://developer.android.com/training/data-storage/room/prepopulate)。它是「一次拷完再打开」，不是「边导入边可查」。

4. **Play Asset Delivery：交付时机，不是查询就绪**：
   > install-time：“install-time 资源包在用户安装应用时分发。… 您可以在应用启动时立即使用这些资源包。”且“这些资源包会增加 Google Play 商店上列出的应用大小”，磁盘“要求至少为所有资源包大小的两倍”。
   > fast-follow：“fast-follow 资源包会在用户安装应用后立即自动下载；用户无需打开应用即可开始 fast-follow 下载。”下载期间“用户仍然可以进入应用”。
   > on-demand：“on-demand Asset Pack 会在应用运行时下载。”应用“不应假定这些文件存在或其位于何处”。
   来源：[Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery)（中文官方页）。这支持「先让用户进 app，资源后到」，但不解决「已到一半的数据库怎么查」。

**成熟开源实现**：本轮用 `searchd` 多引擎检索（Bing/Yahoo/DDG/Brave）未找到可直接复用的「渐进导入 + 部分查询 + completeness」开源实现；检索中出现的 Open Food Facts、AnkiDroid 从公开描述看更接近「下载/导入 + 进度条」，但本轮**未逐仓核实**，不能当作已证实参考。**未找到 ≠ 不存在**；这是检索结论，不是事实断言。标「待验证」。

**因此**：本仓要做的是把上述原语组合成一个**自定义状态机**；不能引用某篇官方文档说「Android 官方推荐两阶段导入」。

### 1.3 长事务 / 大批量写入：官方原话与边界

**(a) 批量放一个事务，确实更快。** Android 官方 SQLite 性能页原文：

> “Outside of an explicit transaction, each write runs in its own implicit transaction and pays the full cost of a commit, including an `fsync` in `FULL` synchronous mode. Batching pays that cost once, which can make a batch of writes an order of magnitude or more faster.”

> “A transaction commits multiple operations, which improves not only efficiency but also correctness.”

> “Only one write transaction can occur at a time. Use `MoreExecutors.newSequentialExecutor(Executor)` to serialize writes.”

来源：[Best practices for SQLite performance](https://developer.android.com/topic/performance/sqlite-performance-best-practices)。

**(b) 但「一个巨大事务」有明确代价。** SQLite WAL 原文：

> “Since there is only one WAL file, there can only be one writer at a time.”

> “A checkpoint can only complete when no other transactions are running, which means the WAL file cannot be reset in the middle of a write transaction. So a large change to a large database might result in a large WAL file.”

> “read performance deteriorates as the WAL file grows in size since each reader must check the WAL file for the content … it is important to keep the WAL file size down by running checkpoints at regular intervals.”

> “By default, SQLite does a checkpoint automatically when the WAL file reaches a threshold size of 1000 pages.”

来源：[Write-Ahead Logging](https://www.sqlite.org/wal.html)。

本仓已有实测证据：KD-51 之前「整科一条长写事务」把 room3 唯一写连接占住，同进程其它写排到 **270 s**；改成「读相 + 64 节点一块的短写事务」后，索引段从 88,913 ms 降到 5,436–5,928 ms（`KnowledgeSearchIndexBuilder.kt:20-76`、`docs/kb-acceptance-2026-10-17.md:824`）。这证明：**对本仓，分块短事务不是理论偏好，是已实测的必要条件。**

**(c) Room 的事务 API 语义**（`RoomDatabase.withTransaction` API 参考）：

> “Room will only perform at most one transaction at a time; additional transactions are queued and executed on a first come, first serve order.”

> “It is recommended that all Dao function invoked within the [block] be suspending functions.”

> “Performing blocking database operations is not permitted in a coroutine scope other than the one received by the suspending block.”

来源：[RoomDatabase.withTransaction](https://developer.android.com/reference/kotlin/androidx/room/RoomDatabase#withTransaction(kotlin.coroutines.SuspendFunction1))。注意：这条 API 参考是 room3 的；本仓用的就是 room3 3.0.0。

**(d) 批量 insert 的 chunk 大小：官方没有给行数。** 可引用的硬约束是 SQLite 绑定变量上限：

> “the maximum value of a host parameter number is SQLITE_MAX_VARIABLE_NUMBER, which defaults to 999 for SQLite versions prior to 3.32.0 (2020-05-22) or 32766 for SQLite versions after 3.32.0.”

来源：[SQLite limits](https://www.sqlite.org/limits.html)。

工程含义：

- Room 的 `@Insert`/`@Upsert` 接收 `List<T>` 时走**逐行 bind → step → reset**，不拼多行 `VALUES`，所以不受 999 绑定变量限制；本仓 `insertKnowledgeSearchFeatures(List)` 的 KDoc 已实读确认 room3 走 `EntityInsertAdapter.insert(Iterable)` 的 void 路径（`ProblemOrganizationDao.kt:107-121`）。
- 如果自己拼 `INSERT ... VALUES (?,?),(?,?)`，每行 3 列时，API <30 的设备上应把 chunk 控制在 `floor(900/3)=300` 行以内；API ≥30 可按 32766 计算，但仍建议留余量。
- **行数经验值**：本仓取「一块 ≈ 50–150 ms 墙钟」作为目标，而不是固定行数。现有索引块 64 节点 ≈ 12,288 行特征；F6 后四科索引总耗时 5,436–5,928 ms，按同结构日志的 60 个插入块折算约 **90–99 ms/块**（块数来自 127 s 轮的同结构日志，F6 轮未逐块打印；这是折算，不是逐块实测）。材料每行更小，可把 500–1,000 行作为一块的起点，再用设备实测调到 50–150 ms。**这是工程经验值，不是官方数字，标「待验证」。**

**(e) `PRAGMA synchronous`：OFF 的真实代价。** SQLite 原文：

> `NORMAL`：“WAL mode is safe from corruption with synchronous=NORMAL … WAL mode is always consistent with synchronous=NORMAL, but WAL mode does lose durability. A transaction committed in WAL mode with synchronous=NORMAL might roll back following a power loss or system crash.”

> `OFF`：“the database might become corrupted if the operating system crashes or the computer loses power before that data has been written to non-volatile storage. On the other hand, commits can be much faster with synchronous OFF.”

> “Setting synchronous to OFF is a good option when creating a new database from scratch, in a scenario where the process of creating the database can be repeated if a power loss occurs, and when performance is critical.”

来源：[PRAGMA synchronous](https://www.sqlite.org/pragma.html#pragma_synchronous)。

Android 官方页同时说明：

> “With WAL, the `NORMAL` mode doesn't sync on every commit and still protects the database from corruption.”

> “When you enable WAL through the framework, Android applies the device's default WAL synchronous mode, which is `NORMAL` in AOSP for Android 9 and higher.”

> “Room enables WAL by default on devices that aren't low-RAM devices.”

来源：同 1.3(a)。

**对本仓的直接含义**：`StudyDatabase` 同时存学生错题、复习队列等用户数据，不是「可重来的临时库」。在同一个库上把 `synchronous` 设为 OFF，若发生 OS 崩溃/断电，损坏面包括用户数据，而不只是「下次重导知识包」。这不是理论洁癖：SQLite 文档明确把 OFF 的适用场景限定为「可从零重建的库」。

### 1.4 导入期间可查询：WAL 并发与「中间态」到底长什么样

**(a) 读写可并发，但只有一个写者。**

> “WAL provides more concurrency as readers do not block writers and a writer does not block readers. Reading and writing can proceed concurrently.”

> “Since there is only one WAL file, there can only be one writer at a time.”

来源：[SQLite WAL](https://www.sqlite.org/wal.html)。

**(b) 单个读事务看到一致快照；不会读到「写了一半的事务」。**

> “a single read transaction only sees the database content as it existed at a single point in time.”

来源：同上。

Android `SupportSQLiteDatabase` 参考也给出同义表述：

> “While a write is in progress, readers on other threads will perceive the state of the database as it was before the write began.”

> “all of the work done in that transaction and all of the nested transactions will be committed or rolled back.”

来源：[SupportSQLiteDatabase](https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase)。

**(c) 但「分块提交」会让读者看到「语料前缀」，不是「脏页」。** 这是两阶段导入的核心一致性事实：

- 一个块事务提交后，下一块还没提交；读者在两次提交之间看到的是「已提交的块」。
- 所以不会读到半行、半条外键；但会读到**部分节点/部分材料/部分特征行**。
- 如果块内顺序是「先节点、后绑定」，在节点已提交、绑定未提交的窗口，读者能看到节点但看不到它的来源/材料绑定。**跨表引用完整性不是事务自动保证的；必须由写入顺序和发布状态保证。**
- 索引未完成时，`searchSubjectKnowledgeRecallCandidates` 依赖 `knowledge_search_feature`；特征行为空/不全，召回会**少结果甚至零结果**。这不是「数据不存在」，是「索引未完成」。必须用 completeness 区分，不能把零命中当答案。

**(d) 长读事务会挡住 checkpoint。** SQLite 原文：

> “A checkpoint can only complete when no other transactions are running … A checkpoint can only complete when no other transactions are running, which means the WAL file cannot be reset in the middle of a write transaction.”

> “if a database has many concurrent overlapping readers and there is always at least one active reader, then no checkpoints will be able to complete and hence the WAL file will grow without bound.”

来源：同 1.3(b)。工程含义：导入期间不要持有跨阶段的长读事务；查询用短读事务，读完即放。

**(e) `wal_checkpoint` 的模式差异（决定什么时候能截断 WAL）：**

> `PASSIVE`：“Checkpoint as many frames as possible without waiting for any database readers or writers to finish.”
> `FULL`：“blocks … until there is no database writer and all readers are reading from the most recent database snapshot. … FULL blocks concurrent writers while it is running, but readers can proceed.”
> `RESTART`：“works the same way as FULL with the addition that after checkpointing the log file it blocks … until all readers are finished with the log file.”
> `TRUNCATE`：“works the same way as RESTART with the addition that the WAL file is truncated to zero bytes upon successful completion.”

来源：[PRAGMA wal_checkpoint](https://www.sqlite.org/pragma.html#pragma_wal_checkpoint)。

工程含义：导入过程中用 `PASSIVE`（不阻塞读者），只在**导入完成且 UI 空闲**时用 `TRUNCATE`；不要在用户正在查询时用 `RESTART`/`TRUNCATE`。

### 1.5 预置库（createFromAsset）的能力边界

官方原文（同 1.2(3)）：

- `createFromAsset("database/myapp.db")`：参数是 assets 下的相对路径。
- `createFromFile(File(...))`：Room “creates a copy of the designated file rather than opening it directly”；需要读权限。
- 两者都会 “validates the database to ensure that its schema matches the schema of the prepackaged database”。
- 迁移语义：destructive fallback 时，版本号相同才会用预置库；实现了 migration 时，“Room uses prepackaged database files only in the case of a fallback migration”。

**本仓实测的预置库体积（关键约束）**：

| 事实 | 值（v23 内容） | 证据 |
|---|---:|---|
| 干净知识库文件 | **407,326,720 B ≈ 407 MB** | `build/kb-perf/A-pristine.db`（`stat` 实读） |
| 页数 × 页大小 | 99,445 × 4,096 B | `PRAGMA page_count/page_size`（只读打开实读） |
| `knowledge_search_feature` | **542,092 行** | `SELECT count(*)` 实读 |
| `knowledge_teaching_material` | **64,975 行**（v26 为 65,021） | 同上 |
| 用户数据表 | 0 行（`mistake`/`practice_unit` 等全空） | 同上 |

也就是说：**「预置库」不是几十 MB 的附加资产，而是 ~407 MB 的完整数据库**（v23 实测；v26 材料多 46 条，量级不变）。当前构建产物 163–185 MB，再加 407 MB 解压后资产；Play Asset Delivery 还要求 install-time 包至少 2× 磁盘（官方中文页），即 >800 MB 可用空间。这个量级直接排除了「把预置库当主方案」的可行性。

来源：本仓 `build/kb-perf/A-pristine.db` 只读打开与 `stat` 实读（2026-11-02）；PAD 磁盘要求见 S14。

**能力边界**：

- 预置库只解决「全新安装的第一次打开」；它不解决「已有用户数据的库怎么更新」。
- 它要求预置库的 schema 与当前 Room schema 一致；本仓已有 70+ schema 版本和完整迁移链（`core/database/schemas/`），每次升级都要同步生成预置库，构建链成本高。
- 预置库文件必须包含全部内容（本仓实测 407 MB）；它不能只装节点/别名，因为 `createFromAsset` 打开的就是最终库。
- **待验证**：`createFromAsset` 的拷贝发生在「首次 open」还是「Builder.build()」，以及 WAL/journal 旁文件如何处理（§4 第 4 条）。

### 1.6 本仓现状（源码实读，不是旧报告转述）

| 事实 | 证据 | 对本题的意义 |
|---|---|---|
| 安装已移出启动关键路径 | `SmartMistakeBookApplication.kt:756-760`：`markPreparing()` → 后台 `BundledKnowledgeBaseInstaller.install(database)` → `markReady()` | 首帧不等导入；但「第一次查知识库」仍要等 `Ready` |
| 依赖知识库的入口在未就绪时被挡住 | 复习页 `SmartMistakeBookRoot.kt:808`：`!knowledgeBaseAvailability.isReady` → 「知识点资料还在准备中」；自动整理 `RoomMistakeOrganizationRepository.kt:179` → `throw KnowledgeBaseNotReadyException` | 当前是「全有或全无」，没有部分结果 |
| 安装快路径以完成戳判定 | `BundledKnowledgeBaseInstaller.kt:47-56`：`manifest.contentVersion == recorded.contentVersion` 则直接返回 | 完成戳语义是「整包跑完」；**不能提前写**，否则下次跳过剩余材料 |
| 全量解析在任何写库之前 | `BundledKnowledgeBaseInstaller.kt:57-61`：`BundledKnowledgePackResources.load()` 后才进入 `withKnowledgeImportTuning` | 20.7–21.2 s 解析期间，库里一行新内容都没有 |
| 导入期 `synchronous = OFF` | `RoomKnowledgeImportMaintenance.kt:50-53`：`PRAGMA synchronous = OFF`、`cache_size = -32768` | 用户数据库在 OS 崩溃/断电时有损坏风险（§1.3(e)） |
| 导入期临时移除两条二级索引 | `RoomKnowledgeImportMaintenance.kt:81-96`；`FEATURE_INDEX_DROP_DDL/ENSURE_DDL` | 已把逐行维护索引的成本后置；崩溃由 `ensureFeatureIndexes()` 自愈 |
| 索引按 64 节点一块重建 | `KnowledgeSearchIndexBuilder.kt:227`；`docs/kb-batch29-2026-10-31.md:58-60` 的 `insertChunks=ceil(nodes/64)` | 分块短事务已验证；可复用到材料 |
| 每科索引有版本锚点 | `KnowledgeSearchIndexStateEntity`：`knowledge_search_index_state(subject, index_version)` | 天然支持「按科 READY」，不需要新造全局状态 |
| 完整性判定已有缓存与失效 | `KnowledgeSearchIndexCompleteness`；写路径三处失效 | 可扩展为「查询 completeness」的底座 |
| 材料在节点/索引之后才 upsert | `RoomKnowledgeContentReconciler.kt:80-90` | 65k 材料是残差主段；适合后置到 Phase B |
| 当前四科索引只占 5.4–5.9 s | `docs/kb-acceptance-2026-10-17.md:824` | 即使索引全后台，首查仍会被 20 s 解析 + 20 s 材料拖住；**只把索引挪后台不够** |

**当前 51.6 s 的分解（批次 29 的 127 s 轮与 F6 后的 51.6 s 轮不可混算；这里只取同口径的 F6 后读数）：**

| 段 | 读数 | 来源 |
|---|---:|---|
| JSON 解析（2 主包 + 26 侧车） | 20.7–21.2 s | `docs/kb-acceptance-2026-10-17.md:829` |
| 四科特征索引 | 5.4–5.9 s | 同上 `:824` |
| 其余（节点/材料/绑定/关系/残差） | 20.8–25.2 s | 同上 `:829` |
| `install finished in` | 47.5–51.8 s | 同上 `:824` |

**结论**：如果只做「索引后台化」，首查仍要等解析 + 材料，收益有限；必须把**解析与材料写入也分阶段**，并且让第一科在解析完主包后就可用。

---

## 2. 方案对比

| 维度 | A. 一次性导入（现状） | B. 两阶段导入 | C. 预置库（createFromAsset / PAD） | D. 混合（预置基线 + 增量） |
|---|---|---|---|---|
| 首查可用 | 等全量：47.5–51.8 s | Phase A 第一科 READY 即可查；目标 3–10 s | 首次打开即用；拷贝/校验时间取决于文件大小 | 首次打开即用；增量后台补 |
| 全量完成 | 47.5–51.8 s | 约 45–52 s（同量级；目标 ≤45 s，不劣化）；用户不必等 | 无导入；更新另算 | 增量通常远小于全量 |
| 一致性 | 一个完成戳，语义简单 | 需要 completeness/状态机；跨块可见 | Room 校验 schema；预置库是完整快照 | 需要基线版本 + 增量版本双重校验 |
| 事务/锁风险 | 已有分块索引；材料仍是大批量 | 分块短事务，风险最低 | 无写入（除拷贝） | 同 B |
| 崩溃恢复 | 完成戳最后写；重跑幂等 | 需要 per-phase 进度与续跑 | 拷贝中断由 Room 处理 | 需要基线与增量都可重入 |
| 包体/发布 | 现有构建产物 163–185 MB | 不变 | **解压后 ~407 MB**（本仓实测）；PAD 还要求 2× 磁盘 | 同 C |
| 升级路径 | 现有差分调和 | 同 A，但分阶段 | 每次 schema/内容升级都要重建预置库；已有用户仍需迁移 | 基线可换，增量必须兼容旧基线 |
| 实现复杂度 | 低 | 中高（状态机、降级查询、测试） | 中（构建链、schema 同步、资产交付） | 高 |
| 适合本仓 | 当前形态 | **推荐作为主方案** | 只适合作为「全新安装加速」备选 | 内容更新频繁时的长期形态 |

### 2.1 一次性导入（现状）

优点：完成戳语义单一；失败就重跑；没有「部分结果」的产品/测试负担。缺点：用户第一次查知识库必须等 50 s；解析阶段完全不产生可查询内容；材料写入把残差拉长；`synchronous=OFF` 的风险面覆盖用户数据。

**判断**：不适合作为最终形态。它已通过 F6（≤60 s），但「≤60 s」是导入完成门槛，不是「首查可用」门槛。

### 2.2 两阶段导入

核心不是「把索引挪后台」，而是**把「结构可查」与「材料/索引补齐」分开**：

- Phase A：主包（节点/别名/关系/来源）→ 写库 → 按科建索引 → 逐科 READY。
- Phase B：25 个 2025 侧车（114 MB）+ 1 个 2020 侧车，逐卷流式解析 → 材料 + 绑定分块写入 → 完成戳。

优点：首查从 50 s 降到「第一科 READY」；每科独立发布，数学先可用、生物后可用；材料后补不阻塞召回；崩溃可按卷续跑。缺点：必须定义 completeness；查询要能降级；需要 per-subject 状态与测试；部分结果的产品文案要诚实。

**判断**：与本仓现有「每科索引锚点」「内容完成戳」「64 节点分块」最贴合，改动是**重组现有阶段**，不是另起一套。

### 2.3 预置库

优点：全新安装首查几乎无等待；Room 原生支持；PAD install-time 可保证首启就有文件。缺点：只覆盖全新安装；schema/内容每次升级都要重建；**解压后库 ~407 MB（本仓实测）**，对 APK/商店体积和磁盘是硬约束；已有用户仍要走迁移/增量；`createFromAsset` 的拷贝时机与 WAL 处理需要额外验证。

**判断**：本仓内容版本迭代快（v23→v26→v27），预置库会把「内容发布」和「APK 发布」绑死；不建议作为主方案。可作为「首次安装加速包」的备选，且必须与增量导入共存。

### 2.4 混合（预置基线 + 增量）

长期最优：预置一个「基线快照」，增量只导入 `contentVersion` 差异。但需要：基线版本与增量包的双重校验、基线缺失时回退到全量、预置库与用户数据的合并策略。复杂度最高，本批不建议做；两阶段导入可作为混合方案的 Phase A/B 底座。

---

## 3. 对本仓的具体设计建议

> 本节是设计建议，不是已实现代码。所有时间数字除标注「实测」外均为**推断**，需按 §3.5 复测。

### 3.1 两阶段边界：切在哪、每阶段写什么

**Phase A（首查关键路径，目标 3–10 s）**

1. 只解析**主包**：`knowledge/moe-2025-four-subjects-v1.json`（4.33 MB）与 `knowledge/moe-2020-foundation-v1.json`（12 KB）。**不解析 teaching sidecar。**
2. 写 `knowledge_source`、`knowledge_node`、`knowledge_node_source_binding`、`knowledge_node_relation`；按父子先序、每 500–1,000 行一个短事务（节点总量 3,280，可一条事务，但为统一形态仍分块）。
3. 按科调用 `KnowledgeSearchIndexBuilder.rebuildSubject(subject)`；保留 64 节点/块。**每科完成后立即把该科 `status=READY`**，不要等四科全完。
4. 节点/关系/绑定写完后发布 `structureState=READY`（结构可查）；随后按科建索引。**第一科 `indexState=READY` 后该科即为 FULL；其余科在索引完成前走 `PARTIAL_INDEX`/`STRUCTURE_ONLY` 降级（§3.3）。**

**Phase B（后台，目标 ≤45 s，不劣化现有 47.5–51.8 s）**

1. 按 `moe-2025-teaching-support-v2-index.json` 的 sidecar 顺序，**一次只解析一卷**（0.3–9.6 MB；多数 4.6–5.2 MB），解析完即释放 JSON 对象。
2. 该卷的 `materials` + `materialBindings` 在同一个块事务里写入（建议 500–1,000 行/块）；每块提交时**同一事务**更新 `materialsWritten`；该卷**最后一块**提交时才同事务推进 `nextSidecarIndex` 到下一卷（否则崩溃后续跑会跳过本卷未写的部分）。
3. 每卷完成后可对该卷的材料引用可见；UI 显示「教材 12/26 卷」。
4. 按包分别写完成戳：2025 包在其 25 卷完成、2020 包在其 1 卷完成后，才写各自的 `content_install_state`（维持现有快路径语义；未完成的包不写）。

**Phase C（可选，后置）**

- 如果 L2「材料 title+summary 特征」被采纳，再按科建材料特征；不要放进 Phase A/B 的关键路径。
- dense `.vec` 资产保持现有懒加载，不进入导入状态机。

**明确不做**：

- 不在 Phase A 写 `content_install_state`。它的语义是「整包完成」，提前写会让下次启动命中快路径、跳过 Phase B。
- 不把 26 个 sidecar 一次性 `load()` 进内存再写。当前 `BundledKnowledgePackResources.load()` 会把所有卷聚合成一个对象图；两阶段必须改成逐卷 `loadSidecar(index)`，否则内存峰值和解析时间都下不来。

### 3.2 状态机字段

**(a) 新增 `knowledge_install_progress`（每个 pack 一行）**

| 字段 | 类型 | 语义 | 消灭的失败 |
|---|---|---|---|
| `pack_id` | TEXT PK | 内容包 id | 多包并列时进度不串 |
| `content_version` | TEXT | 与 manifest 对齐 | 旧版本进度被误当新版本 |
| `schema_version` | INTEGER | 进度表自身版本 | 未来字段演进 |
| `structure_state` | TEXT | `PENDING/PARSING/WRITING/READY/FAILED` | 查询不知道「结构是否可查」 |
| `materials_state` | TEXT | `PENDING/STREAMING/READY/FAILED` | 材料未完成被当成完整 |
| `nodes_expected` / `nodes_written` | INTEGER | 节点计数 | 进度百分比无分母 |
| `materials_expected` / `materials_written` | INTEGER | 材料计数；`expected` 可由 index 文件或 26 卷累加 | 材料进度不可见 |
| `next_sidecar_index` | INTEGER | 下一卷序号；**仅整卷最后一块提交后推进** | 崩溃后从头解析 26 卷 / 跳过未写完的卷 |
| `sidecars_total` | INTEGER | 总卷数 | 进度分母 |
| `started_at_ms` / `updated_at_ms` | INTEGER | 起止/心跳 | 卡死不可诊断 |
| `last_error` | TEXT | 最近失败 | 只看到「准备中」不知道原因 |
| `attempt` | INTEGER | 重试次数 | 无限重试/无法退避 |

**写入纪律**：进度行必须与数据块在**同一事务**提交。否则会出现「进度 100%、数据缺一块」的假完成——这正是本仓 `content_install_state` 用「最后写戳」避免的失败形态，不能在进度表上重新引入。

**(b) 扩展 `knowledge_search_index_state`（每科一行，已有 PK `subject`）**

现有字段：`subject`、`index_version`。建议增加：

| 字段 | 语义 | 消灭的失败 |
|---|---|---|
| `status` | `PENDING/BUILDING/READY/FAILED` | 查询只知道锚点版本，不知道「本科是否正在建/失败」 |
| `nodes_expected` | 本科应索引节点数 | 进度分母；也可由 `countReviewedKnowledgeNodesBySubject` 实时算 |
| `nodes_indexed` | 本科已写特征节点数 | 免去每次召回两条 COUNT（与现有 completeness 缓存互补） |
| `updated_at_ms` | 最后推进时间 | 识别卡死 |

**兼容策略**：可以不改现有表，另建 `knowledge_search_index_progress(subject, status, ...)`；优点是迁移小，缺点是多一次 join。推荐**先另建表**，等稳定后再合并。现有 `KnowledgeSearchIndexCompleteness` 继续做进程内缓存，不替代持久状态。**`status` 只是 UI/调度加速提示；查询正确性仍以「锚点当前 + 已索引 ≥ 已审校」为准**，这样即使 status 写丢也不会把未完成索引当完整。

**失效纪律**：任何写 `knowledge_search_feature` 的路径（安装、整科重建、只补缺、用户确认）都必须同时推进/失效对应的 per-subject status；否则内存缓存会把「重建中」当「已完整」，重现现有 `KnowledgeSearchIndexCompleteness` KDoc 里列出的三类漏失效失败。

**(c) 状态转移**

```
PENDING
  → PARSING_STRUCTURE        （开始解析主包）
  → WRITING_STRUCTURE        （写节点/关系/绑定）
  → STRUCTURE_READY          （结构可查；此时查询已可开始，按科降级）
  → INDEXING(subject)        （逐科建索引；每科独立 READY）
  → ALL_SUBJECTS_INDEXED     （四科都 READY）
  → MATERIALS_STREAMING      （逐卷写材料）
  → COMPLETE                 （写 content_install_state 完成戳）
任何阶段异常 → FAILED（保留已完成部分；下次从 last_success 继续）
```

关键规则：

- `STRUCTURE_READY` 不等于 `COMPLETE`，也不等于 `ALL_SUBJECTS_INDEXED`；查询看「结构 READY + 本科 index 状态」，快路径只看 `content_install_state`。
- 每科 `index_state=READY` 的判据保持现有「锚点当前 + 已索引 ≥ 已审校」；不要只写一个布尔。
- `FAILED` 不能把已提交数据回滚（分块事务已提交）；它只表示「还需要重试」，查询继续用已 READY 的科。

### 3.3 降级查询：怎么查、怎么标注、怎么不骗人

**(a) 查询返回值增加 completeness，不再用异常表达「部分可用」。**

建议的领域类型（名字可调整）：

```kotlin
enum class RecallCompleteness {
    FULL,                 // 结构 READY + 本科索引 READY + 材料不要求
    PARTIAL_INDEX,        // 结构 READY，但本科索引未 READY（结果来自已提交块 + 兜底）
    STRUCTURE_ONLY,       // 节点可读，但本科没有任何特征行
    PARTIAL_MATERIALS,    // 召回 FULL，但材料/引用未全
    STALE,                // contentVersion 不匹配 / 上次安装失败
    PREPARING             // 结构都未 READY
}

data class KnowledgeRecallResult(
    val nodes: List<KnowledgeNodeSeedRecord>,
    val completeness: RecallCompleteness,
    val indexedSubjects: Set<String>,
    val pendingSubjects: Set<String>,
    val progress: KnowledgeInstallProgress?,
)
```

`KnowledgeBaseNotReadyException` **保留**给「必须完整知识库才能做」的操作（如自动分类/绑定候选菜单）；复习页当前用 `!knowledgeBaseAvailability.isReady` 的整体门（`SmartMistakeBookRoot.kt:808`）应改为「按科判断 + 部分结果」，**普通查询不再被全局 Ready 挡住**。这正是现有 `RoomMistakeOrganizationRepository.prepare()` 的语义；不要把它扩散到查询。

**(b) 降级矩阵**

| 状态 | 查询行为 | 返回标注 | UI 文案（建议） |
|---|---|---|---|
| `PREPARING`（结构未 READY） | 不查特征表；返回 `PREPARING` + 进度 | `PREPARING` | 「知识库准备中（12%），完成后自动可查」 |
| 结构 READY，本科索引未 READY | 先查已提交特征行；再 `readSubjectKnowledgeNodes(subject, limit)` 兜底；按 id 去重 | `PARTIAL_INDEX` | 「数学已就绪；物理索引准备中，以下结果可能不全」 |
| 结构 READY，本科无特征行 | 只走 `readSubjectKnowledgeNodes` 兜底 | `STRUCTURE_ONLY` | 「已按目录列出，相关性排序将在索引完成后启用」 |
| 索引 READY，材料未 READY | 正常召回；材料引用为空时省略引用 | `PARTIAL_MATERIALS` | 「讲解材料 12/26 卷已就绪」 |
| `contentVersion` 不匹配 / 安装失败 | 用库里最后一份完整数据；标注过期 | `STALE` | 「内容更新未完成，当前显示上一版」 |
| 全 READY 且无命中 | 返回空 | `FULL` | 「没有找到相关知识点」 |

**铁律**：只有 `completeness == FULL` 的空结果才能说「没有找到」。其余空结果必须说「还没准备好/索引未完成」。这是本仓 `KnowledgeBaseAvailability` KDoc 已经写过的原则（「不是零命中的假结果」），两阶段只是把它扩展到查询层。

**(c) 兜底查询的代价与边界**

- `readSubjectKnowledgeNodes(subject, limit)` 是目录序，不是相关性序；只适合「先让用户看到东西」，不能冒充语义召回。
- 兜底上限建议 256（现有 `readSubjectKnowledgeNodes` 的调用已有 `coerceAtMost(256)` 先例，`RoomKnowledgeBaseStore.kt:180` 附近）。
- 兜底结果必须带 `partial=true`；UI 不能把兜底结果和正常结果混在同一个「最相关」列表里而不标注。

### 3.4 并发、事务与 WAL：具体做法

**(a) 事务边界**

| 写入单元 | 建议块大小 | 事务 | 理由 |
|---|---|---|---|
| 节点/来源/关系/绑定 | 500–1,000 行，或全部 3,280 行 | 一块一个短事务 | 总量小；保持与材料一致的块边界 |
| 特征行（每科） | 64 节点 ≈ 12,288 行 | 一块一个短事务（现有） | 已实测 ~90 ms/块；避免占住唯一写连接 |
| 材料 + 材料绑定 | 500–1,000 行，或 50–150 ms | 一块一个短事务，**同事务更新进度** | 65k 行不能一条大事务；进度不能先于数据 |
| 完成戳 | 1 行 | 单独短事务，最后写 | 保持「戳当前 ⟺ 整包完成」 |

**(b) 绝不做的事**

- 不在解析 JSON 时持有写事务。解析是 CPU/内存，放事务外；现有代码已经是「先 parse 再 tuning」，保持。
- 不把 26 卷材料放进一个事务。WAL 不能在中途重置，写连接被占住，读者看到的仍是导入前快照（不是渐进可用）。
- 不在导入期间持有长读事务（会阻塞 checkpoint，WAL 无界增长）。
- 不在 `withTransaction` 里做 `delay`/网络/大 JSON 解析；Room 的事务线程会被占住。

**(c) WAL / checkpoint**

1. 保持 Room 默认 WAL（官方：Room 在非低 RAM 设备默认开 WAL）。
2. 导入期间让 SQLite 自动 checkpoint（默认 1000 页）；不要在导入中手动 `TRUNCATE`。
3. 每完成一卷或每 N 个块，若当前无活跃查询，可跑一次 `PRAGMA wal_checkpoint(PASSIVE)`；它不等待读者。
4. 全部完成、UI 空闲时跑一次 `PRAGMA wal_checkpoint(TRUNCATE)`，把 WAL 截到 0；若返回 busy，下次空闲再试，不阻塞用户。
5. 监控 `-wal` 文件大小；若持续 >4 MB（1000 页）且 checkpoint 不前进，说明有长读事务或长写事务，按 §1.4(d) 排查。

**(d) `synchronous`：建议从 OFF 改为 NORMAL**

- 现状：`RoomKnowledgeImportMaintenance.kt:52` 在导入期设 OFF。它的收益是少 fsync；代价是 OS 崩溃/断电可能损坏**整个** `StudyDatabase`（含用户错题）。
- 建议：导入期设 `PRAGMA synchronous = NORMAL`，退出恢复原值。WAL + NORMAL 的官方结论是「不腐蚀，但可能回滚最近未 checkpoint 的提交」；对「可从资产重导的知识内容」可接受，对用户数据也安全得多。
- 如果实测 NORMAL 让导入明显变慢：**不要在主库上继续用 OFF**。可选方案是导入到独立的、可重建的 staging 库（该库可用 OFF），完成后再把数据合并/复制进主库；或者接受 NORMAL 的成本。OFF 是连接级设置，不是表级设置——即使只写索引表，崩溃也可能损坏整个数据库文件。
- 这不是「更保守所以更好」：它消灭的是一个具体的失败——OS 崩溃时用户错题库损坏。按本仓规则，新增/修改必须能指认失败；这里指认得出。

**(e) 用 WorkManager 承载 Phase B**

- 现状：安装在 `applicationScope.launch` 里；进程被杀就丢，下次从头解析。
- 建议：Phase A 仍在启动协程（要快）；Phase B 用**唯一** `CoroutineWorker`（`enqueueUniqueWork(..., KEEP)`），`setProgressAsync` 写卷进度，`getWorkInfoByIdFlow` 观察。
- 官方语义支持「app 退出/重启后仍可靠运行」（§1.2(2)）；进度只在 worker 运行时可见，所以 UI 还要读持久 `knowledge_install_progress`，不能只依赖 `WorkInfo`。
- 注意：WorkManager 可能因电量/约束延后；首启场景用 expedited/foreground 需另行评估（**待验证**：本仓 targetSdk 36 下的 expedited 配额与前台服务限制）。

### 3.5 验收口径：51.6 s 降到多少算「生产可接受」

**先说结论：没有可直接引用的「首启导入 p95」行业标准。** Play Vitals 只管启动 TTID；NN/g 只管感知延迟；Android 官方没有给「后台导入完成时间」的数字。现有 F6 门（≤60 s）会把 51.6 s 判绿，但它测的是**导入完成**，不是**首查可用**；本文建议新增首查门，而不是放宽或收紧 F6。下面的数字是**工程建议**，锚定在三个可引用事实：Play 5 s、NN/g 10 s、本仓现有查询预算 p95 250 ms。

| 门 | 判据（建议） | 依据/性质 | 现状 |
|---|---|---|---|
| G1 首帧 TTID | p95 ≤3,000 ms，硬上限 5,000 ms | 3 s 为工程目标；5 s 是 Play Vitals excessive 线 | 冷启 TotalTime 2.8–3.0 s；TTID p95 未测 |
| G2 结构可查 | p95 ≤5,000 ms | 工程目标；节点/关系已写、查询可开始 | 未实现；推断 2–4 s |
| G3 第一科可查 | p95 ≤5,000 ms，硬上限 10,000 ms | NN/g 10 s 注意力上限；5 s 对齐 Play | 未实现；推断 3–5 s |
| G4 四科可查 | p95 ≤10,000 ms | NN/g 10 s | 未实现；推断 7–10 s |
| G5 全量材料完成 | p95 ≤45,000 ms，硬上限 60,000 ms | 不劣化现有 47.5–51.8 s；60 s 沿用现有 F6 门；**不承诺 30 s** | 实测 47.5–51.8 s（一次性） |
| G6 导入期查询 | p95 ≤250 ms、max ≤1,000 ms、overBudget=0 | 本仓融合检索既有预算 p95 250 / p50 150 | 已有预算；导入期未压测 |
| G7 部分结果标注 | 100% 带 completeness；0 次静默空结果 | 产品正确性，不是性能 | 未实现 |
| G8 崩溃收敛 | 20 次阶段边界 kill，100% 收敛；`integrity_check=ok`；计数精确 | 工程可靠性 | 未做故障注入 |

**「首屏 <2 s」不是官方阈值**：Play Vitals 的原文数字是 5 s，NN/g 是 1 s/10 s；2 s 只能作为内部产品目标，不能写成行业判据。本文不把它放进硬门，而是用 G1 的 3 s/5 s 与 G3 的 5 s/10 s 组合表达。

**「生产可接受」的单句口径**：

> 首查可用 p95 ≤5 s（硬上限 10 s），全量材料完成 p95 ≤45 s（硬上限 60 s），导入期间查询 p95 ≤250 ms 且无静默空结果。

为什么不是「51.6 → 5 s 全量」：全量仍要解析 ~119 MB（主包 + 侧车）+ 写 65k 材料；现有一次性导入是 47.5–51.8 s，没有证据表明两阶段能把总 CPU/IO 工作量压到 30 s 以内。本文把「5 s」留给首查，把全量目标定为「不劣化现有读数（p95 ≤45 s）」。要真正把全量压到 5 s，需要预置库或改变内容格式，不是两阶段能单独做到的。**两阶段的目标是「首查 5 s」，不是「全量 5 s」。**

**测量协议（必须写死，否则数字不可比）**：

1. 装置：至少 1 台 API 34 emulator（现有 `kb_test_device`）+ 1 台真机；记录 AVD/CPU/内存。
2. 样本：**≥20 次真冷装**（`pm uninstall` → `adb install` → `logcat -c` → `am start -W` → 轮询完成）。20 是经验下限，严格 p95 估计需更多；现有 3 次只能报 median/max。
3. 固定：同 APK sha、同内容包 sha、同 `contentVersion`、同屏幕/语言/电量；不要混轮次。
4. 指标：`am start -W` 的 `TotalTime`（TTID 近似）、`KnowledgeInstall` 日志的 `install finished in`、新增的 `structureReadyAt`/`firstSubjectReadyAt`/`materialsFinishedAt`、查询 p50/p95/max。
5. 报法：p50/p95/max + 样本数 + 装置；不把 emulator 数字直接当量产结论。

### 3.6 最小落地顺序（每步消灭一个具体失败）

1. **`synchronous` OFF → NORMAL**（`RoomKnowledgeImportMaintenance.kt`）：消灭「OS 崩溃损坏用户库」。先做，独立可测。
2. **主包与 sidecar 分离解析**：新增 `loadBasePacks()` 与 `loadSidecar(index)`；Phase A 只解析主包。消灭「解析 20 s 期间零可查」。
3. **新增 `knowledge_install_progress` + 逐科 `index_state`**：消灭「部分完成被当完整」。
4. **Phase B 逐卷写入 + 进度同事务**：消灭「崩溃后重跑 26 卷」与「进度 100% 数据缺失」。
5. **查询降级 + completeness**：消灭「索引未完成时的假零命中」。
6. **WorkManager 承载 Phase B**：消灭「进程被杀丢进度」。
7. **按 §3.5 跑 20 次冷装 + 故障注入**：消灭「未验证就宣称达标」。

不要在第 1–2 步之前改查询 UI；否则会把「准备中」文案和真实部分结果混在一起，无法判断。

---

## 4. 未验证项

1. **Play Vitals 的聚合口径**：官方页只给阈值与 TTID，未说明 Play Console 是按 session、device-day 还是 p95 聚合；本文按「逐次启动超线即计入」理解。**待验证**（Play Console 帮助页本次连接超时）。
2. **Play Vitals 页面的出版/更新年份**：页面未显示出版年；本次只记录「访问 2026-11-02」。2018 年二手来源复述了 5 s 口径，但不是独立官方来源。**待验证**。
3. **NN/g 三个数字的现代适用性**：原文 1993 年，面向传统终端/网页；移动端首启是否仍以 1 s/10 s 为界，没有找到同等级新原始来源。本文只把它当「感知上限」类比。**待验证**。
4. **`createFromAsset` 的拷贝时机**：官方页明确 `createFromFile` 会 copy，但没有逐字说 `createFromAsset` 在「首次 open」还是「Builder.build()」时拷贝；WAL/journal 文件如何处理也未说明。**待验证**（需真机/源码复核）。
5. **Play Asset Delivery 的精确体积上限**：中文官方页说「允许超过 200 MB」「大小上限更高」，未给出 install-time/fast-follow/on-demand 各自的确切 GB 数；英文页本次连接超时。**待验证**。
6. **两阶段的时间推断**：Phase A 2–4 s、第一科 3–5 s、四科 7–10 s 都是按「解析 20.7 s / ~119 MB」「索引 5.4–5.9 s / 4 科」线性外推，**未实测**。全量材料完成本文不承诺 30 s，只要求不劣化现有 47.5–51.8 s。实际受 JSON 结构、内存、设备 I/O 影响。
7. **`synchronous=OFF` 在 Android 上的实际损坏率**：SQLite 文档说 OS 崩溃/断电可能损坏；本仓没有做断电/内核崩溃注入，无法量化。**未验证**。但风险方向明确，不需要等量化才修。
8. **各 API 级别的 SQLite 版本与绑定变量上限**：本文引 SQLite 官方「3.32.0 前 999、之后 32766」；本仓 minSdk 23，具体设备上 SQLite 版本未逐台枚举。**待验证**。
9. **材料是否参与「第一次查询」的产品语义**：本文假设节点 + 别名索引足以回答「查知识库」，材料只影响讲解引用。若产品要求首查必须带教材引用，则 Phase A 的可用性定义要改。**未验证**（需产品确认）。
10. **WorkManager 在首启的 expedited/前台限制**：targetSdk 36 下的配额、通知权限、后台启动限制未实测。**待验证**。
11. **成熟开源参考**：本轮多引擎检索未找到「渐进导入 + 部分查询 + completeness」的公开实现；这是检索结论，不代表不存在。**待验证**。
12. **p95 的统计有效性**：现有 3 次冷装不足以估 p95；本文建议 ≥20 次，但未规定置信区间。**待定**（可按 bootstrap 给 CI）。

---

## 5. 来源清单

| # | 来源 | URL | 用途 | 级别 |
|---|---|---|---|---|
| S1 | Android Developers, App startup time in Android vitals | https://developer.android.com/google/play/vitals/launch-time | 5/2/1.5 s、TTID | 官方，单一来源 |
| S2 | Android Developers, App startup time | https://developer.android.com/topic/performance/issues/launch-time | TTID/TTFD、占位先显示 | 官方 |
| S3 | NN/g, Response Times: The 3 Important Limits | https://www.nngroup.com/articles/response-times-3-important-limits/ | 0.1/1/10 s、1993 | 官方原文，单一来源 |
| S4 | Android Developers, Best practices for SQLite performance | https://developer.android.com/topic/performance/sqlite-performance-best-practices | 批量事务、WAL、NORMAL、单写者 | 官方 |
| S5 | SQLite, Write-Ahead Logging | https://www.sqlite.org/wal.html | 多读单写、快照、checkpoint、WAL 增长 | 官方 |
| S6 | SQLite, PRAGMA synchronous | https://www.sqlite.org/pragma.html#pragma_synchronous | OFF/NORMAL 语义 | 官方 |
| S7 | SQLite, PRAGMA wal_checkpoint | https://www.sqlite.org/pragma.html#pragma_wal_checkpoint | PASSIVE/FULL/RESTART/TRUNCATE | 官方 |
| S8 | SQLite, Limits | https://www.sqlite.org/limits.html | 999/32766 绑定变量 | 官方 |
| S9 | Android Developers, RoomDatabase.withTransaction | https://developer.android.com/reference/kotlin/androidx/room/RoomDatabase#withTransaction(kotlin.coroutines.SuspendFunction1) | 单事务排队、suspend 约束 | 官方 API 参考 |
| S10 | Android Developers, SupportSQLiteDatabase | https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase | 写中读者看到旧快照、事务原子 | 官方 API 参考 |
| S11 | Android Developers, Prepopulate your Room database | https://developer.android.com/training/data-storage/room/prepopulate | createFromAsset/File、schema 校验 | 官方 |
| S12 | Android Developers, WorkManager | https://developer.android.com/topic/libraries/architecture/workmanager | 可靠后台、重启恢复 | 官方 |
| S13 | Android Developers, Observe intermediate worker progress | https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe | setProgressAsync/WorkInfo | 官方 |
| S14 | Android Developers, Play Asset Delivery | https://developer.android.com/guide/playcore/asset-delivery | install-time/fast-follow/on-demand、体积/磁盘 | 官方中文页 |
| S15 | 本仓 `docs/kb-acceptance-2026-10-17.md` | 仓内 | 47.5–51.8 s、索引 5.4–5.9 s、2.8–3.0 s 冷启 | 本仓实测记录 |
| S16 | 本仓 `docs/kb-batch29-2026-10-31.md` | 仓内 | 127 s 轮的分段与 64 节点/块 | 本仓实测记录 |
| S17 | 本仓 `core/database/.../RoomKnowledgeImportMaintenance.kt` | 仓内 | OFF/NORMAL、索引窗口 | 源码实读 |
| S18 | 本仓 `core/database/.../KnowledgeSearchIndexBuilder.kt` | 仓内 | 64 节点/块、锚点最后写 | 源码实读 |
| S19 | 本仓 `core/data/.../BundledKnowledgeBaseInstaller.kt` | 仓内 | 快路径、解析、完成戳 | 源码实读 |
| S20 | 本仓 `core/data/.../RoomMistakeOrganizationRepository.kt` | 仓内 | 未就绪抛异常 | 源码实读 |

---

## 6. 一句话给决策者

**不要再优化「51.6 s 的全量导入」这一个数；把它拆成「首查 5 s」与「全量 45 s（不劣化）」两个数。** 前者靠两阶段 + 按科 READY + 降级查询，后者靠逐卷流式材料写入；两者都不需要预置库，也不需要牺牲用户数据安全。唯一必须立刻改的是 `synchronous=OFF`——它省下的几秒，换来的是用户错题库在断电时可能损坏。
