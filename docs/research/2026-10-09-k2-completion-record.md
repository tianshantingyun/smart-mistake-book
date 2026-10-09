# 3C · K2「存量库升级演练与 ④-6 探针落档」· 完成记录（2026-10-09/10）

> 依据：实施计划 `docs/research/2026-10-09-k2-plan.md`（`d90a384b`，2026-10-09 批准）；
> K1 完成记录 `docs/research/2026-10-08-k1-completion-record.md`（§10.4 v13 升级成本 UNVERIFIED、§11#8 计数缝）；
> S19 检索诊断 `docs/research/2026-10-02-s19-retrieval-diagnosis.md` §6（探针已入库 + 复测）；
> 投影回滚演练 `docs/research/2026-10-03-projection-rollback-drill.md`（归档无清理策略登记）。
>
> 执行方式：两批**严格串行**——每批「实施 → 正式门（显式分支 exit code）→ 独立只读复核 → 显式清单提交」；
> 综合门由协调方收尾（冷启机：全量 JVM → DB 全套 → 设备清场 → app 全套 → R8 冒烟）。
> **门红先判失败构成**；DB 全套裸口径红只按既知边际门 `catalogFacetsPlanAndLatency` 登记接受（§5）。
>
> 交付边界：**存量库升级演练**（JVM 逐位 + 真 Room 四断言）+ **升级规模数字**（记录项、无门）+
> **④-6 探针落档**与两处陈旧表述更正 + **计数缝测试说明与上限**；**不含** KF-30 语义改动（等用户裁定）、
> D-1/D-2/D-3 与 D-0 包内落账（KB 线）、KB 线文件面（`tools/kb_*` 只跑不改）、台账折入（他线在飞文件，见 §7）。

## 1. 提交

| 批次 | 提交 | 内容 |
|---|---|---|
| 计划 | `d90a384b` | K2 实施计划（存量库升级演练 + 探针落档 + 陈旧表述更正 + 计数缝说明与上限） |
| 批 1 | `f8aedbdf` | 存量库升级演练：JVM 新用例（`ReplayFingerprintDrainerTest`：旧版 displaced + 12 事件非平凡账本，四断言）+ 真 Room 仪器化新类 `LegacyProjectionUpgradeDrillInstrumentedTest`（raw SQL 播种旧版库 → 真 drainer → 四断言）+ 升级规模数字（`LearningProjectorReplayFingerprintTest` 新记录用例，数字落 `docs/research/2026-10-03-projection-rollback-drill.md`）；4 文件、+992/-0；零 schema、零版本 bump |
| 批 2 | `329b01ab` | ④-6 探针当次复跑与数字落档 + K1 计划 §1 / K1 记录 §9.1 各追加一行日期更正（原文保留）+ 计数缝 KDoc 改准 + `MAX_ISSUED_QUERY_RECORDS=1024` 停止追加上限（含 JVM 上限回归用例）；4 文件、+52/-5；零 schema、零版本 bump；`tools/kb_*` 未改 |
| 收尾 | （随本记录提交） | 本完成记录 + 主线 3C 行回填 |

## 2. 交付内容（每项指认它消灭的失败）

| 交付 | 消灭的具体失败 |
|---|---|
| JVM 升级演练（旧版 + 非平凡账本，四断言） | 「v13 首开 = 先归档再重放」的顺序与保真此前只被三条各缺一角的用例覆盖（纯守卫**空账本** / 真 drainer 但**账本恒空** / 旧版+真账本但 **fake 端口 + 6 事件**）——归档版本、写入顺序、逐位保真没有一条组合演练 |
| 真 Room 升级演练（四断言） | 「真 Room 库躺旧版投影 + 真账本 → 当前代码打开 → 先归档再重放」从未被演练（既有仪器化回滚演练归档的是**当前版本**快照、迁移矩阵用**空库**）——存量库首开是 D-1/D-5 落包后同类链路的唯一现实入口 |
| 升级规模数字（记录项、无门） | 归档 JSON 体积 / encode 成本 / 全量重放成本全仓无实测数字（此前只有「通常几十 KB」文案），「归档无清理策略」的影响面无法评估——数字补齐后「维持只增」裁定有了量级依据（50k 事件 ≈ 13.4MB/行） |
| ④-6 探针落档与两处更正 | K1 计划 §1 与 K1 完成记录 §9.1 的「探针未入库、本批未复测」已成过期表述（KB 线 `ff4f8605` 已入库、s19 §6 已复测）——陈旧表述会误导后续读者把已闭合项当未闭合 |
| 计数缝说明与上限 | KDoc 原文「判定路径实际下发的 SQL 流水」易被读作真实 SQL 执行计数（room3 无钩子，无法观察真实执行）；`issuedVerificationQueries` append-only 无上限（生产只写不读，长进程无界累积） |

## 3. 逐项落证

### 3.1 JVM 升级演练四断言（批 1）

**用例**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/ReplayFingerprintDrainerTest.kt:125`（`a v11-era projection is archived before a nontrivial ledger is replayed bit for bit`）。夹具：v11 时代 displaced 快照（复合串 `learning-core-v11(projector-v11,evidence-v5,curve-v3,skip-v4,attribution-v3,ledger-v2)`，`:396`）+ 12 事件账本（`upgradeLedger(cycles = 2)`，`:253`：两遍六事件形态，序列 1..12 连续、五类齐、含修正）；函数体先断言夹具非平凡（序列连续、事件类数=5）。

| # | 断言 | 落点 |
|---|---|---|
| ① | 归档行 `projectorVersion` = **旧版本**（不是当前版本） | 「归档行必须点名被替换快照的旧版本」断言 |
| ② | 写入顺序 `archive` 早于 `commit`：`projectionWriteOrder == listOf("archive","commit")` | 「顺序即机制」断言 |
| ③ | 重放输出与 `LearningProjector().replay(LEARNER_ID, events).snapshot` **逐位相同**；且输出带当前二进制版本（`LearningProjector.VERSION`） | 等价断言 |
| ④ | 归档 JSON 往返保真：`LearnerSnapshotJson.decode(archived.snapshotJson) == displaced`，且**不是**重放后的新值（`assertNotEquals(drained.snapshot, …)`） | 归档保真断言 |

**门读数**：综合门全量 JVM 内该类 `tests=3 failures=0`（`core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.study.ReplayFingerprintDrainerTest.xml`，含新用例；mtime 2026-10-10 05:07）。

### 3.2 真 Room 升级演练四断言（批 1）

**用例**：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/LegacyProjectionUpgradeDrillInstrumentedTest.kt:74`（`anExistingDatabaseWithAV11ProjectionIsArchivedBeforeTheLedgerIsReplayed`，791 行新类）。库形态 = 上一台二进制（v11 复合串）留下的完整存量库：旧版本投影头（checkpoint 追平账本头 5）+ 一张 v11 口径记忆卡 + 5 条账本行（4 类：ATTEMPT ×2 / ANSWER_REVEAL_OUTCOME / ATTEMPT_CORRECTION / CHAT_EVIDENCE_SUBMITTED）+ 2 条 attempt 行 + 分配头 5 + 两张呈现态行。播种全部 raw SQL、列集**逐列对齐当前 `63.json`**（外键开启、依赖序插入；值为规范指纹，读边界逐行 SHA-256 复算——播错一位即 CONFLICT）；用 `StudyDatabaseFactory.open` 打开、`RoomBackedStudyExperienceRepository.refresh()`（既有 drill 的驱动方式）触发真实 drainer。

| # | 断言 | 落点 |
|---|---|---|
| ① | **先归档**：归档表 1 行、版本=旧版、JSON 解回**逐位等于被替换那一份**、且非重放后新值、schema DDL 含 `learner_projection_snapshot`（可回退） | `:112-129` |
| ② | 投影快照版本升到当前（`LearningCoreVersions.PROJECTION_COMPOSITE`）≠ 旧版；checkpoint 追平账本头 5；`appliedAttemptRecords.size == 2` | `:132-141` |
| ③ | **重放确定性/幂等**：再 `refresh()` 结果与第一次一致；不追加归档（仍 1 行） | `:143-153` |
| ④ | **用户数据零丢失**：`projection_outbox`=5、`attempt_event`=2、分配头=5，与播种后一致 | `:155-168` |

**门读数**：综合门 DB 全套 XML 内该类 `tests=1 failures=0 errors=0 skipped=0`（`core/database/build/outputs/androidTest-results/connected/debug/TEST-test_device(AVD) - 14-_core_database-.xml`）。批 1 门时同类连同 `ProjectionRollbackDrillInstrumentedTest`（9）与 `FullMigrationMatrixInstrumentedTest`（10）= **20/20、failures=0 errors=0**（见 §4）。

### 3.3 升级规模数字（批 1；记录项、无门）

**用例**：`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/LearningProjectorReplayFingerprintTest.kt:154`（`upgrade scale archive json bytes and encode cost at ten and fifty thousand events`）。口径：读边界口径（`validatedFingerprints()` 先算好，计时只包 `replay(...)` 调用本身，读边界 SHA-256 单独打印）；**纯 JVM 桌面口径**。该用例自身只断言「JSON 非空 + 能逐位解回」（记录项完整性锚点，不设门）。

数字（落 `docs/research/2026-10-03-projection-rollback-drill.md:123-131`，两次运行区间）：

| N（事件） | 归档 JSON 字节 | encode | 读边界指纹 | 全量重放 |
|---|---|---|---|---|
| 10,000 | **4,041,034**（两次逐位相同） | 130–135ms | 87–103ms | 34–35ms |
| 50,000 | **13,443,293** | 83–85ms | 470–486ms | 178–180ms |

结论（照录该补记）：「通常几十 KB」只对小账本成立——单行体积随账本事件数近线性增长（50k 事件 ≈ **13.4MB/行**）；裁定仍为**维持只增**（无清理/无上限，全仓无 DELETE），不新增清理机制；真机口径另行登记。

### 3.4 ④-6 探针当次复跑数字与 s19 §6 对照（批 2）

- **探针**：`tools/kb_perf/recall_read_cost.py`（KB 线 `ff4f8605` 入库，与守门 `tools/tests/test_kb_recall_read_cost_probe.py` 同批；本批**只跑不改**）。默认档 = 4 科 × 9,000 节点 / 每节点 24 条特征 = **86.4 万特征行**，内存库，n=24（脚本 `:26` 用法与 `:175` 默认值）。
- **K2 当次复跑**（批 2，提交信息 `329b01ab` 所记）：默认档 **p50 9.724ms / p95 10.17ms**；`--json` 复跑 **p50 10.02ms / p95 10.318ms**。机器口径：AMD Ryzen 9 9950X 16C/32T、总内存 64.5GB（运行期可用 ≈4.35GB）、CPU 负载 20–26%。
- **s19 §6 复测对照**（`docs/research/2026-10-02-s19-retrieval-diagnosis.md:95-100`）：同口径 **p50 9.534ms / p95 9.812ms**（min 9.314 / max 10.231）。
- 对照结论：K2 当次与 s19 §6 同量级（差值 0.19–0.51ms，两次运行都略高于 s19，方向一致）；探针 schema pin 在 57（当前 63，三表未变）——**不改**（KB 冻结面），登记（见 §8.1）。
- 诚实口径：本记录写作会话**未复跑探针**；K2 数字转录自批 2 提交信息（实施批实跑留档），s19 数字读自 s19 文档原文。

### 3.5 两处陈旧表述更正（批 2；原文保留、各追加一行）

- `docs/research/2026-10-08-k1-plan.md:18`（§1）追加："…更正（K2）：探针已在库 `tools/kb_perf/recall_read_cost.py`（KB 线 `ff4f8605`，含守门用例），并已于 s19 §6 复测（p50 9.534ms / p95 9.812ms）；本处『未入库』表述作废。K2 当次复跑数字见 K2 完成记录。"
- `docs/research/2026-10-08-k1-completion-record.md:169`（§9.1）同样追加一行日期更正；并直接指向本记录。
- 两处均为**追加、不动原文**（`329b01ab` 对该两文件各 +1 行）。

### 3.6 计数缝：说明与上限（批 2；仅测试可见、零行为变化）

- **KDoc 改准**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexCompleteness.kt:54-69`）：由「判定路径实际下发的 SQL 流水」改为「判定路径**计划下发**的标签集合（标签 = DAO 方法名）」，并写明「**不是真实 SQL 执行计数**——room3 3.0.0 没有 `setQueryCallback`（javap 已证实，另见 K1 记录 §11#8），无法从 Room 侧观察真实执行；本缝证明的是『判定路径走到了/没走到下发这些标签的代码位置』」。
- **上限**（`:97-116`）：`MAX_ISSUED_QUERY_RECORDS = 1024`，达到即**停止追加**（不整体清空、**保留前缀**）；`recordVerificationQuery` 在既有 `lock` 下检查上限（`:98-103`）。理由（KDoc）：截断保留前缀使「标签序列前缀」语义成立；清空会让「越界后的空列表」与「根本没下发」不可区分。1024 ≈ 340 余次完整验证（每次最多 3 条标签），远高于任何用例窗口。
- **JVM 上限回归用例**：`core/database/src/test/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexCompletenessTest.kt:146`（`verificationQuerySeamStopsAppendingAtTheCap`）：追加 `MAX+8` 条后断言 size==`MAX` 且首条前缀保留（`:148-158`）。
- 零 schema、零版本 bump、`tools/kb_*` 未改；生产逻辑零行为变化（缝仅同模块测试读）。

## 4. 批级门与综合门数字

| 门 | 结果 |
|---|---|
| 批 1 门（冷启机） | 全量 JVM `--rerun-tasks` + 仪器化 3 类（`LegacyProjectionUpgradeDrillInstrumentedTest` / `ProjectionRollbackDrillInstrumentedTest` / `FullMigrationMatrixInstrumentedTest`）**全绿**；同一命令原样重跑两次均绿——**20/20、failures=0 errors=0**（提交信息 `f8aedbdf`；本会话复核三类在综合门 DB XML 中计数 1+9+10=20、全绿） |
| 批 2 门（冷启机） | 全量 JVM `--rerun-tasks` + 仪器化 `KnowledgeSearchIndexCompletenessInstrumentedTest` **全绿**；定向自验 JVM **9/9**、仪器化 **4/4**（提交信息 `329b01ab`；本会话复核落盘 XML `tests=9 failures=0` 与 DB 套内 `tests=4 failures=0`） |
| 综合门 · 全量 JVM | **exit=0**（门指令回报）；本会话落盘复核：344 个结果 XML、mtime 单轮窗口 2026-10-10 05:04:54–05:07:20，根属性求和 = **2423 / 0 / 0 / 0**（tests/failures/errors/skipped；主树全部模块） |
| 综合门 · DB 全套 | **exit=1**（`core/database/build/outputs/androidTest-results/connected/debug/test-result-exit-code.txt`=1）；XML 根 `tests=239 failures=1 errors=0 skipped=0 time=941.276`；**失败构成 = 恰好一条已登记环境红**（§5），按门指令接受——**不等于全绿，如实登记为红** |
| 综合门 · app 全套 | **exit=0**；XML 根 `tests=53 failures=0 errors=0 skipped=0 time=105.064`（localFirst；门指令记「失败时已重试」——首轮失败与重试明细见工作流 verified 段，本记录写作会话不可读取 → UNVERIFIED，见 §8.4） |
| 综合门 · R8 冒烟 | **全部通过**（明细见下） |

**R8 冒烟明细**（协调方执行；本会话复核产物）：
- 构建 `:app:assembleLocalFirstRelease`（带 `RELEASE_*` 签名环境变量）→ `BUILD SUCCESSFUL in 3m 17s` / `376 actionable tasks: 68 executed, 308 up-to-date` / `EXIT_CODE=0`（`/tmp/r8_build.log:474,475,477`；`> Task :app:minifyLocalFirstReleaseWithR8` `:453`、`> Task :app:validateReleaseSigning` `:44`）。
- APK `app/build/outputs/apk/localFirst/release/app-localFirst-release.apk`：**170,721,115 字节**、mtime 2026-10-10 05:45；`output-metadata.json` applicationId=`com.tingyun.smartmistakebook.localfirst`、versionCode=2、versionName=0.2.0-localfirst。
- 包名映射核对：`app/build.gradle.kts:40-43` `create("localFirst") { applicationIdSuffix = ".localfirst" }` → `com.tingyun.smartmistakebook.localfirst`（与门指令一致；派单所记行号 37 实为 strictOffline 段，本记录以实读行号为准）。
- 装机/启动/门禁：`adb -s emulator-5554 install -r` → `Success`；monkey 启动 → `Events injected: 1`；PID **8220** 三次检查不变（无崩溃重启）；`dumpsys` 交叉核对 `topResumedActivity=…/MainActivity`；`logcat --pid=8220` **137 行**内 `FATAL|ClassNotFound|NoSuchMethod|NoClassDefFound` **命中 0**、ANR/SIGSEGV 标记 **0**（本会话重跑 grep 复核）；进程存活期间真实业务路径完成 `KnowledgeInstall: install finished in 110036 ms (full path: parse + reconcile)`。
- 截图 `build/r8-smoke/localfirst-release-monkey.png`（81,190 字节；PNG 魔数已校验；**本会话目视** = 首页真实渲染「晚上好，同学 / 10月9日·星期五 / 今日复习 / 暂无学习记录 / 暂无待复习题」+ 底栏四栏，非黑屏或崩溃框）。
- 清理：删除设备端 `/sdcard/r8_smoke.png`；关闭侦察期误启的 5038 端口 adb server（原 5037 server 未受影响）。

> 注（口径）：① 综合门 JVM 汇总为本会话在落盘目录对全部 `TEST-*.xml` 根属性求和（344 个文件、单轮 mtime 窗口；不含 `.worktrees/` 与独立工程 `tools/image-mcp-server` 的旧 XML）。② DB/app 套件「失败时已重试」的中间明细与工作流 verified 段本会话不可读取 → UNVERIFIED；本记录只认最终落盘 XML/exit 码。③ R8 冒烟为**模拟器（emulator-5554 / API 34 / x86_64）+ debug keystore** 口径，非真机、非 release 签名，不当发布背书。
> 批 1 门另有一则留档（`f8aedbdf` 提交信息）：首次「强门失败」经判为**基础设施失败**（`DeviceException: No connected devices!`——一条测试都没跑、当时无任何 TEST-*.xml，与上一轮同一现场），非测试失败；冷启 AVD `test_device` 后同一命令原样重跑两次均绿，**零代码/断言改动**。此为「门红先判失败构成」纪律的一次实证。

## 5. 已登记环境红：综合门 · DB 全套 · SECTION facet（逐条原始数字与出处）

- **失败用例**：`core:database:connectedDebugAndroidTest` → `LibraryCatalogScalePerformanceInstrumentedTest#catalogFacetsPlanAndLatency`（结构/EQP 断言全绿，**只**在计时 backstop 红）。
- **原始数字（落盘 XML 原文）**：`java.lang.AssertionError: 目录 facet SECTION p95=207ms 超过 backstop 200ms；samples=[212, 194, 197, 196, 194, 193, 198, 196, 196, 192, 194, 191, 190, 190, 193, 196, 194, 200, 208, 204, 202, 205, 203, 207]`。
- **XML 证据**：`core/database/build/outputs/androidTest-results/connected/debug/TEST-test_device(AVD) - 14-_core_database-.xml`，根 `tests=239 failures=1 errors=0 skipped=0 time=941.276`，timestamp `2026-10-09T21:23:12`（本地 2026-10-10 05:23:12），mtime 2026-10-10 05:23；`test-result-exit-code.txt`=1。
- **同族先例（同形）**：S18 尾首跑 SECTION 220ms → 安静机器绿；D-0 综合门 221–247ms 四次连续失败 → 冷启 `-wipe-data` 后 173ms 绿；K1 207–211ms 两次采样。**K2 与 K1 207–211ms 已登记环境红同族，本批零接口**（两批变更文件不触 facet/目录检索路径；批 2 的计数缝仅测试可见、生产行为零变化）。
- **本轮处置**：**未改任何断言/预算/夹具**（两批提交中无相关改动）；按门指令接受为已登记环境红。**本地裸预算是否调整属规则变更、留给用户裁定**——本记录不主张放宽。

## 6. 独立复核

- **批 1：approve-with-notes；批 2：approve-with-notes**（两批均为独立只读复核；结论由门指令回报给定，两批提交信息同记）。
- 复核意见的**明细（findings）未随提交留档、本记录写作会话不可读取工作流 verified 段 → UNVERIFIED**（本记录不转录未读到的条目）。可确证的落地面：批 1 门重跑轮**零代码/断言改动**（提交信息）；批 2 计数缝改动不对生产逻辑生效（§3.6）；两批均为显式清单提交、未越界改动他线文件（§1 文件清单与 §7）。

## 7. 台账折入待办

`docs/agent-first-refactor-decisions-2026-09-23.md` **仍是另一条会话的在飞文件**（工作树 `M`，`git diff --stat` = 225 行未提交新增；K2 两批全程未改动它——两批提交清单均不含该文件，`git status` 前后一致）。按纪律**不改动**，以下条目登记为**台账折入待办**：

1. 3C 前半（`1d297efc`，`docs/research/2026-10-04-stage3c-part1-completion-record.md`）
2. 3C 后半（`docs/research/2026-10-05-stage3c-part2-completion-record.md`）
3. S18 尾（`docs/research/2026-10-06-s18-fts-completion-record.md`）
4. D-0 内核侧（`docs/research/2026-10-07-d0-binding-verdict-completion-record.md`）
5. K1（`docs/research/2026-10-08-k1-completion-record.md`）
6. **K2（本记录）**
7. 阶段 5 挂起（`docs/research/2026-10-04-stage5-gate-check.md`）

## 8. 遗留与 UNVERIFIED

1. **探针当次数字为转录**：本记录未复跑 `tools/kb_perf/recall_read_cost.py`；K2 数字（9.724/10.17、10.02/10.318）来自批 2 提交信息（实施批实跑留档）。探针 schema pin 落后当前 schema 一版（57 vs 63，三表未变）——属 KB 冻结面未改，登记（脚本自校验「升级即报错」，非静默测旧表形）。
2. **升级规模数字为纯 JVM 桌面口径**（N=10k/50k 事件）；真机/大账本真 Room 口径**未测**（无凭据）→ UNVERIFIED（计划已登记「真机另行登记」）。
3. **`projection_archive` 无清理策略维持只增**（本轮只补体积量级、不新增机制）；单行体积随账本事件数近线性（50k ≈ 13.4MB/行），真机存量压力数字仍缺。
4. **综合门重试/首轮明细未取证**：app 全套「失败时已重试」的首轮失败与重试记录、DB 裸红 triage 原文存于工作流 verified 段，本会话不可读取 → UNVERIFIED；本记录只认最终落盘 XML 与 exit 码。
5. **复核意见明细未留档**（§6）→ UNVERIFIED。
6. **登记不动门**（任何增删/放宽等用户裁定）：50k 1k 档门口径歧义、50k 无结构/EQP 断言、安装落点失效重复且用例不敏感、写读两侧空绑定判定潜伏不一致（当前不可达）、P95 双口径——均在 K1 记录 §11/§3.5 已登记，本批**未动**。
7. **计数缝上限后的语义**：达到 `MAX_ISSUED_QUERY_RECORDS=1024` 即**停止追加**（保留前缀）——正常用例窗口远小于该值；若未来有用例需 >1024 条断言会读到截断前缀（当前无此用例）。
8. **R8 冒烟为模拟器 + debug keystore 口径**（§4 注③），非真机、非发布签名；KB 线 D-5 写入窗口（真实包三元组落账）仍**未开**。
9. **台账折入待办**（§7，含 K2）；台账文件他线在飞，未碰。
10. **KB 线文件面全程未碰**（`tools/kb_*`、`docs/kb-*`、`docs/agent-first-refactor-decisions-*`、`docs/superpowers/specs/*`）——工作树中他线改动保持原样（提交一律显式清单）。

## 9. 剩余风险

1. **v13 升级路径的真机成本仍未实测**：机制（先归档再重放 / 幂等 / 零丢失）已由 JVM 与真 Room 双侧演练钉住；规模数字为 JVM 桌面口径、真 Room 演练账本仅 5 行——**大账本 × 真设备**的「首开归档耗时/空间」仍是 UNVERIFIED 面。
2. **归档只增**：单行体积近线性增长；复看触发点 = 发布前置的存量压力观测（登记于回滚演练文档，本轮量级数字以该补记为准）。
3. **DB 边际门宿主环境敏感**：S18 尾 220ms / D-0 221–247ms / K1 207–211ms / K2 207ms 四次同形实证——本地裸预算 200ms 在当前宿主上状态紧；CI ×4=800ms 为决定轮（本轮未动预算）。
4. **app 全套与 DB 全套背靠背会 SQLITE_BUSY**（历史已实证）；综合门依赖「设备清场」纪律，重试不是万能——清场不彻底则首轮红仍可能出现。
5. **计数缝上限的截断语义**（§8.7）：防线只针对「生产只写不读的进程内累积」，不改变任何生产行为，也不改变任何断言的既有窗口。
6. **探针 schema pin 57 vs 63**：三表未变（脚本 fail-loud 自校验）；若未来三表变更，探针须由 KB 线同步升级，否则会报错而非静默测旧表。
7. **KF-30 语义改动未启**（等用户裁定）+ **阶段 5 挂起**（3D 硬门）——K2 不改变这两个挂起状态。
