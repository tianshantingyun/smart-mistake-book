# 批次 32 记录（2026-10-29）：一致门标定纠正 + schema 71 生成回填 + 持久化原子性

> 记录时点：2026-10-29（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` **只读**核对计划、基线、两份研究文档、T1 判据、T6 记录、`docs/research/evidence/2026-10-29-batch-32/` 十五份 `.md`、`build/device-track-batch32/` 原始日志与源码锚点后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码。** 本轮执行的是只读命令：`git status/rev-parse`、`grep`、`wc`、`sha256`（python）、`stat`、`cmp`、`python` 解析 JSON、`ls`/`find`，命令与输出见 §10；另写了本记录与三份台账（MASTER-LINE / algorithm-version-ledger / decisions，见 §9）。
> 计划：`.zcode/plans/plan-2026-10-29-batch-32-parity-schema71.md`（40 行，已完整读）。
> 基线：`docs/research/2026-10-29-batch-32-baseline.md`（338 行，未跟踪，只读核对）。
> 研究：R1 `docs/research/2026-10-29-parity-metric-correction.md`（455 行）、R2 `docs/research/2026-10-29-schema-generation-flow.md`（669 行）。
> 判据与台账轨：`docs/research/2026-10-29-batch-32-device-criteria.md`（**914 行**；工单写 890）、`docs/research/2026-10-29-batch-32-t6-doc-consistency.md`（296 行）。
> 设备证据：`docs/research/evidence/2026-10-29-batch-32/00-index.md` 与 `step00`–`step13`（15 份）。
> **门禁状态（工单给定，非记录员实跑）**：KSP 生成 `exit=0`；单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环后单测门复跑 `exit=0`。仓内**没有**批次 32 的 gate 日志目录（`build/main-session-gate-batch31` 是上一批），只有 12:36–12:38 的 JVM JUnit XML 旁证（§6）。记录员未复跑任何 Gradle 任务。
>
> **本记录不声称"批次全绿"**：设备面 **12 步骤绿 / 1 套件红**（备份 34、69→70 迁移 4、70→71 迁移 3、迁移矩阵 15、提交 11、仓库 8、parity 16、裁剪器 9、painter 24、PDF 16、隔离 13、flags 2 绿；结构门 `Tests run: 5, Failures: 1`）。**更关键：设备 APK 构建于 11:14–11:17，现树在 12:26–12:28 另有 11 个源文件改动（850/861 相同），提交/仓库/结构门/parity/70→71 五套件的设备结果不对应当前源码；修复轮新增的仓库第 9 例与结构门修复从未编译/上设备。** schema 71 的**静态三段**（`71.json` / 生成 Impl / 回填常量）成立且记录员实跑 S=71 清单门 `RESULT=PASS`，但"当前树设备全绿"**不成立**。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 10 项**（§1）：A 一致性门（N09 纠正、负控重建、P07 连带表述）；B schema 71（代码侧五列身份、KSP 生成、回填、1→71 迁移与备份）；C 持久化原子性（预算进门、evict 端到端/pin 分辨力、digest 绑定与租约）；D 文档与工具（反向虚报、契约 LIVE、T1 行锚、KDoc 命令、AUD-08 WAL）。
- **实际执行面**：两研究线（R1 度量纠正 / R2 schema 生成与迁移）+ T1 判据 + T2–T7 六实施轨 + 集成交接 + 设备轨（`emulator-5562`，串行 13 步）+ 证据 15 份 + 复核修复轮（实测改动落在 12:26–12:28）+ 本记录与三份台账同步。
- **计划与现树的两处已知偏差**：① 计划硬约束"schema 71 单写者 = 任务 4/5 同一轨（生成与回填必须同轨）"**未遵守**——T3 做代码/迁移，T4 做生成与回填；结果自洽但属计划偏差（§5.2 #4）。② 计划任务 4 的"触发器 backstop"**未实现**，迁移 KDoc 给出三条具体理由并登记待裁（§5.2 #5）。
- **不做（全部未做）**：core/export 集成与 PDF `/AIGC`（KD-75 热区）；双后端装配；JS 广告（MVA 未过）；真实 Provider（无凭据）；锁文件生成/提交（KD-72）；AIDL 之外的接口变更；热区 UI。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature 五模块、core/ui 全部、core/markdown 整目录、core/export、core/domain、`settings.gradle.kts`、`.gitignore`、app Root/SecondaryScreens、docs/current 其余 M、tools/kb、knowledge-production。
2. **schema 71 单写者 = T3**（计划 §2 实际要求 T3/T4 同轨，见 §1.1）；本批 T3 只写 schema 代码，T4 生成 + 回填，属偏差。
3. **core/database / core/data 开工即脏**：按"整文件纳入并注明"处理；本批落点文件开工时多数干净，落码后变 M 或 A。
4. 一致性门仍是**原型门**，不得引用为产品/发版结论；JS 不广告；真实 Provider 无凭据；AIDL 只以 Gradle 任务验证；AAR/stdlib 不变。
5. 设备只 `assemble*` + `adb install -r -t` + `am instrument`，禁 `connectedAndroidTest`；同一落点多个任务合并给一个拥有者。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`0b4bdbbe`；`origin/main` 本地跟踪引用同为 `0b4bdbbe`；`git rev-list --left-right --count origin/main...HEAD` = `0 0`（**未 `git fetch`**，远端实时 hash UNVERIFIED）。基线文档记录时 `origin/main=59d4a03f`（落后 11 提交），说明批次 31/32 的本地提交此后已推到本地跟踪引用。
- **工作树**：**721 行 = 240 M + 478 ?? + 3 staged**（记录时点实测）；设备证据索引自述"240 M / 480 ??"是 11:1x 时点，`??` 与 staged 的归类不同。
- **DB schema**：`STUDY_DATABASE_VERSION = 71`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`）；导出目录 **71 个 json**（1..71）；`71.json` 存在、**已 `git add`（`A `）**。
- **`71.json`**：`version=71` / `identityHash=714c0ecb85ee5edf74f0013740148138` / **90 实体** / 2 视图（`library_catalog`、`knowledge_question_lattice`）/ 378,961 B / mtime 10:13，python 实读。
- **请求 schema**：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（本批零改动）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、feature/review **12**、feature/capture **16**、feature/profile **4**、core/ui **35**、core/markdown **1**、core/export **6**、core/domain **5**；`settings.gradle.kts`/`.gitignore` M。本批对上述路径零写入。
- **锁文件**：盘上 `*.lockfile` **14**；`git ls-files '*lockfile'` = **0**。KD-72 窗口仍不满足。

### 1.4 本批增量点名（按现树实读）

- **改（T3 schema）**：`StudyDatabase.kt`（版本 71 + 迁移注册 `:411`）、`entity/TutorVisualEntities.kt`（五列唯一索引 `:132-155`）、`TutorVisualIdentity.kt`（`deriveSnapshotId` 五参、五段长度前缀 `:89-113`）、`dao/TutorVisualDao.kt`（`findSnapshotByContent` 五列 WHERE `:82-101`；evict 租约 `:208-235`）、`RoomStudyDatabase.kt`（stage/commit 事务内预算重判、digest require、evict 接线）、`TutorVisualCommit.kt`、`port/TutorVisualPort.kt`。
- **新增（T3）**：`VisualSnapshotIdentityMigration_70_71.kt`（`DROP INDEX` + `CREATE UNIQUE INDEX`，无 `ALTER`）。
- **改（T2）**：`VisualDrawOpsParityMetrics.kt`（`THRESHOLD_VERSION=calibrated-r2` `:266`；阈值数值 0.75/[0.93,1.07]/bbox 1/TV 0.08 不变；KDoc 改为方向相关 + 盲盘）、`VisualDrawOpsParityInstrumentedTest.kt`（N09 重写、新增 N13、P06/P07 常量）。
- **改（T4/T5 data）**：`SmbkSelectionPlan.kt`（`SCHEMA_VERSION=71` `:44`、`IDENTITY_HASH=714c…` `:51`、`CLOSURE_VERSION=3` `:54`）、`TutorVisualCommitRepository.kt`（stage 路径带淘汰集、事务后处置）、`VisualCheckpointRetention.kt`、`SmbkSelectionPlanTest.kt`（`SCHEMA_71_PATH`）。
- **测试增量（现树 `grep -c '@Test'` 实跑）**：parity **16**、painter **24**、结构门 **5**、提交 **11**、仓库 **9**、70→71 **3**、69→70 **4**、迁移矩阵 **15**、备份 **34**、裁剪器 **9**；JVM `SmbkSelectionPlanTest` 12、`TutorVisualIdentityTest` 6、`TutorVisualSchemaContractTest` 9、`KernelWave0SchemaContractTest` 8、`VisualCheckpointRetentionTest` 17。
- **研究/判据/记录**：R1 455、R2 669、基线 338、T1 判据 914、T6 296、设备证据 15 份、本记录。
- **零改动**：`settings.gradle.kts`、`.gitignore`、AAR/stdlib、请求 schema、`drawSchemaVersion`、`PROTOCOL_VERSION`；生产装配（`TutorVisualCommitRepository` 仍只在测试构造）。

## 2. 两研究线

### 2.1 R1 图像一致性度量：`centroidDeltaPx` 的定义纠正与判据形态（`docs/research/2026-10-29-parity-metric-correction.md`，455 行）

- **性质（`:3-10`）**：研究文档，零 Gradle/零设备/零源码改动；设备读数全部复读仓内既有日志；唯一计算是 §2 的 Python 反解。
- **定义（§1）**：`centroidDeltaPx` 是屏栅格与 PDF 栅格**各自**墨量加权质心的欧氏距离 `‖c_P − c_S‖₂`，不是同一侧平移前后的质心差 `‖t‖`。PDF 侧整块平移 `t` 后读数 `d(t)=‖v+t‖`（`v = c_P(0) − c_S`）；二者只在 `v ∥ t` 且同向时相等（§1.2–§1.3）。
- **反解（§2）**：由 P07 基线 `0.5181329158118464` 与 N12 的 +4 px 读数 `3.56014654994809` 解出 `v_P07=(−0.449227283, ±0.258179331)`（模长回代 15 位一致），预测 P07 的 +1 px 读数 **0.608282**；P06 由 `|v|=0.32` 与 +1 px 读数 `1.2624767812258337` 解出 `v_P06=(0.245724, ±0.204987)`。加性模型 `‖v‖+‖t‖` 被两例同时否证（§0-3）。
- **为什么"用质心保证 1 px"不可能（§1.5 命题 4）**：可行性条件 `2‖v‖ < s`，而 P07 的 `2‖v‖=1.036 > 1`；质心的保证分辨力是 `> 2·max‖v‖ ≈ 1.04 px`，1 px 必须交给 `bestShift`/`bboxDev`。
- **正确判据形态（§3）**：`bestShift` 主（带符号、整数 argmax，搜索半径 3 导致 `+4 px` 饱和到 `(3,0)`）、`bboxDev` 辅（整数）、跨栅格质心**只作形状/权重分布护栏**。阈值写法 = **语义 + 保证 + 盲区集合**：0.75 = 噪声上界 + 绝对余量，保证整像素 ≥2 px 必红、1 px 不保证；盲区 = `{t : ‖v+t‖ ≤ τ}` = **闭圆盘 `B(−v, τ)`**（P07 心 `(0.4492,−0.2582)`、半径 0.75，沿 `−v̂` 到 `‖t‖≤1.268`，沿 `+v̂` 仅 0.232）。
- **版本号随断言变**：`calibrated-r1` 同时承载过标量窗口与新文案，版本号已不标识判据 ⇒ 升 **`calibrated-r2`**（§3.6）。研究与落码一致：`VisualDrawOpsParityMetrics.kt:266` 现为 `calibrated-r2`，数值一个不动。
- **外部先例（§4）**：OpenCV `phaseCorrelate` "先取峰值，再在峰值周围 5×5 加权质心求亚像素"，支持"整数位移出口在前、质心只做局部精修"。
- **未验证（§5）**：0.75 对其它 CJK/设备/API/fontScale 未验证；`+1 px` 的 bbox 命中依赖基线方向；PDFium/Skia 内部未读。

### 2.2 R2 Room 3.x schema 生成链路与 70→71 迁移设计（`docs/research/2026-10-29-schema-generation-flow.md`，669 行）

- **性质（`:1-9`）**：研究/设计文档，零 Gradle/零 adb/零源码改动；实测手段 = 只读源码与构建产物、`python 3.13 + sqlite3 3.50.4` 探针、`javap` 读 room3 3.0.0 字节码、AndroidX 源码与 SQLite 官方文档。
- **C1 `identityHash` 只能由 KSP 生成**：`Database.identityHash = md5Hex(SchemaIdentityKey(entities+views))`，写进两处生成物——`StudyDatabase_Impl.kt` 的 `RoomOpenDelegate(version, identityHash, legacyIdentityHash)` 与 `<version>.json` 的 `database.identityHash` / `setupQueries`。错值不会在编译期报错，会在首次打开库时 `Room cannot verify the data integrity`。
- **C2 不能从 JSON 重算**：按字节码还原 `SchemaIdentityKey`/`Property`/`PrimaryKey`/`Index`/`ForeignKey` 的 `getIdKey` 公式，对 `70.json` 跑 **192 组参数组合**（notNull 缺省 × 属性排序 × PK 列表格式 × orders × 实体排序 × 视图去留 × affinity 缺省），**0 命中**：computed `a83c23e117f6e7b908b6a59aede0d960` vs expected `11a8a701bae767a8a3c5bc810e4743a4`。JSON 是编译器模型的有损投影（缺 `withoutRowId`、FK `deferred`、PK `algorithm`，视图 query 被 `createSql` 包了一层）。
- **C3 最小可信命令**：`./gradlew :core:database:kspDebugKotlin`，`finalizedBy :core:database:copyRoomSchemas` 落 `<version>.json`；不需要 `assembleDebug`；KSP 报解析错误时退回 `:core:database:assembleDebug`。
- **C4 提取顺序**：JSON `identityHash` = Impl 第二参 = JSON `setupQueries` 的 `room_master_table` 值；最终以真机新装库 `SELECT identity_hash FROM room_master_table WHERE id=42` 为准。
- **C5/C6 迁移只碰索引**：`renderer_version`/`font_manifest_version` 两列 v70 已有，**不需要 `ALTER TABLE`**；只需 `DROP INDEX`（旧三列）+ `CREATE UNIQUE INDEX`（新五列）；新键是旧键严格超集 ⇒ 存量行不可能冲突。同名 `CREATE … IF NOT EXISTS` 是 no-op，必须换名/先 DROP。
- **C7 触发器 backstop 的边界**：Room 不校验触发器（`TableInfo` 只比列/FK/索引），触发器不进 JSON；fresh install 的 `onCreate` 是空的，只在迁移里建会得到"升级库有、全新安装没有"的不一致。**本批裁定不加**，理由三条见迁移 KDoc：会改既有异常契约（4 条结构门断言钉的是 `ImmutablePayloadConflictException`）、会删一条有分辨力的用例（指向 STAGING 的 link）、fresh install 需独立 `Callback/ensure` 路径与用例。
- **C8 Room 迁移事务内行为**：`BEGIN EXCLUSIVE`；DDL 允许、`PRAGMA foreign_keys` 是 no-op、`VACUUM` 被拒 ⇒ SQLite 官方 12 步改表法不可用；本批方案也不需要。
- **C9 Room 会兜住"忘了 DROP 旧索引"**：多出旧三列索引 → 真机升级 `Migration didn't properly handle`。
- **C10 71 不改结构数字**：只换索引，90 表 / 2 视图 / 100 FK 边 / 21 软引用 / 82 裁剪项不变；回填面只有 `SCHEMA_VERSION`、`IDENTITY_HASH` 与少数测试字面量。
- **流程硬顺序（§3.1）**：先落全部 schema 代码 → 生成 → 从 `71.json` 与 `StudyDatabase_Impl` 两处取 hash 并比对 → 回填 `SmbkSelectionPlan` 两常量与测试字面量 → 跑 JVM 契约、1→71 矩阵、70→71 专用、备份 34/34、设备轨；失败以生成物为准回滚，绝不留"71 常量 + 无 71.json"中间态。

### 2.3 研究复核与落码对应

- R1 的判据形态与 T2 落码逐条对应：`calibrated-r2`（`Metrics.kt:266`）、N09 跨栅格断言（测试 `:309-367`）、N13 合成栅格（`:370-449`）、KDoc 的盲盘/方向相关（`Metrics.kt:26-50`）。**但** N09 注释里仍残留一句错误代数（`:317-318`，§5.2 #7）。
- R2 的 C1–C10 与 T3/T4 落码逐条对应：五列唯一索引（实体 `:132-155`）、五参派生（Identity `:89-113`）、五列查重（DAO `:82-101`）、迁移 `DROP+CREATE`、注册 `:411`、`71.json` 与常量同值（§3.3/§3.4）。**触发器 backstop 未落**，与计划偏差（§5.2 #5）。
- 两份研究都强调"生成/设计 ≠ 设备通过"；设备结论见 §4，且**设备源与现树仍有 11 文件差**（§4.4）。

## 3. 七实施轨 + 集成交接 + 设备 + 证据

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 轨自述 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（914 行）；计数与现树局部漂移** | §5.1/§5.2 仍写 4/10/5，现树 5/11/9（§5.2 #12） |
> | T2 门纠正 | 部分 | **落码成立；设备 parity 16/16 绿，但当前文件在设备构建后被改 → 当前源绿轮 UNVERIFIED** | §4.4 |
> | T3 Room schema 单写者 | 部分 | **schema 代码/迁移/测试落码；生成与回填由 T4 做，违反同轨硬约束** | §5.2 #4 |
> | T4 回填 | 完成 | **`71.json` + Impl + 常量三处同值；S=71 清单门记录员实跑 PASS** | §3.4 |
> | T5 原子性 | 部分 | **预算/evict/租约/digest 落码；stage 路径修复与第 9 例未上设备** | §3.5、§4.4 |
> | T6 文档回改 | 部分 | **KD-77/KD-80/KD-81/契约/KDoc 已改；判据计数仍局部漂移** | §3.6 |
> | T7 条件轨 | 完成 | **分支 D 登记，零改动；九项仍 pending** | §3.7 |
> | 集成交接 | 完成 | **三处接线核完；补正判据文档两处；生产装配仍为零** | §3.8 |
> | 设备轨 | 13 步完毕 | **12 绿 / 1 红；5 套件与现树不同源** | §4 |
> | 证据轨 | 完成 | **15 份 `.md` / 65,677 B（du 96K）** | §4.3 |

### 3.1 T1 设备判据 —— **文档落盘；计数局部漂移**

- 文件 `docs/research/2026-10-29-batch-32-device-criteria.md`（**914 行**；工单摘要写 890）。内容：快照与计数冻结（§1）、判据 A 一致门（§2）、判据 B 迁移 1→71（§3）、判据 C 备份 34/34（§4）、判据 D 结构门（§5）、schema 71 三段（§6）、串行命令与 dex 门（§7）、红例归因（§8）。
- **记录员核对**：§1.2 的 09:58 计数与现树在多数套件一致（parity 16、结构门 5、提交 11、70→71 3、备份 34、矩阵 15），**但仓库写 8，现树 9**（修复轮新增第 9 例，§3.5）。§5.1 仍写"4 条方法 / `OK (4 tests)`"，§5.2 仍写"提交 10 / 仓库 5"，与现树 **5/11/9** 不符；00-index §5-6 自认计数漂移，但被点名的 §5.1/§5.2 未回改。
- §5.3/§5.4 **已按设备轨后记回改**：预算门在事务内（`RoomStudyDatabase.kt:2162-2184`）、evict 租约（`TutorVisualDao.kt:208-235`）、触发器 backstop 仍未落（`:384-386`）。
- §6.3 的 S=71 清单脚本记录员**实跑**（`RESULT=PASS`、`EXIT=0`，90/90/90、双向差集空、version/hash 同源，§10）。
- 计数冻结规则（§1.3）：`N_run < N_frozen` = D（旧包）；`N_run > N_frozen` = 重建；构建前后源快照必须一致。设备轨自身满足"861 文件构建前后相同"，但**构建之后**现树又改了 11 个文件（§4.4），按同一条规则应视为"结果只对 11:14 的源成立"。

### 3.2 T2 门纠正 / 计划任务 1–3 —— **落码成立；当前源设备绿 UNVERIFIED**

- **N09 重写（实读 `VisualDrawOpsParityInstrumentedTest.kt:309-367`）**：旧"1 px 平移 = 1.000"断言已删除；P06 期望 `1.2624767812258337`（容差 1e-6）、P07 期望 `0.608282132823731`（容差 0.02）；P07 另断言 `centroid <= 0.75`（证明质心看不见 1 px），判决必须 `F4_GEOMETRY`。设备 logcat 实测 P06 `1.2624767812258337`、P07 `0.608282132824767`（差 1.04e-12，落在 0.02 容差内），两条 `red=true`、理由 `text bboxDev=2px > 1px`（§10）。
- **新增 N13（`:370-449`）**：合成 192×128 栅格，块右移 2 px + 块外补 100 px 墨条 + 块内挖空 100 px ⇒ 质心 0、墨量比 1.0，`bboxDev=5`、`bestShift=(2,0)`、`gain≈0.0757`，判决 `F4_GEOMETRY`。它钉住"质心/墨量都看不见的位移仍被抓且归几何面"。**边界**：它用的是 +2 px / bboxDev=5，不在 1 px 真盲集（`bboxDev=1`）里；因此"质心盲区由 bbox/shift 兜底"这句在 N13 上只证到 2 px 档（§5.2 #8）。
- **N10/N11/N12 用例体未动**：`git diff -U0` 的 hunk 区间从 `@@ -336,0 +369,82 @@`（新增 N13）直接跳到 `@@ -763,0 +878,7 @@`（常量区），N10–N12 区间无 hunk；设备重跑三条 `red=true`（§10）。
- **阈值与版本**：`THRESHOLD_VERSION="calibrated-r2"`（`Metrics.kt:266`），数值 `bbox=1 / centroid=0.75 / mass=0.93..1.07 / TV=0.08`（`:274-277`）；KDoc 写明"整像素 ≥2 px 必红、1 px 不保证"、盲区为闭圆盘 `B(−v,τ)`、质心只作形状护栏（`:26-50`）。
- **设备**：`OK (16 tests)`（11:18，`step08-parity.log`），15 条 `PARITY_CONTROL` 全 `red=true`，`PARITY_GATE thresholds=calibrated-r2 … blindSet=…(direction-dependent) displacement-exits=bestShift(main)+bboxDev(aux)`。
- **残留**：设备构建后 parity 两文件在 12:28 被改（KDoc/计数/常量区），**当前文件从未重跑**；设备绿轮只对 11:14 的源成立（§4.4）。

### 3.3 T3 Room schema 单写者 / 计划任务 4 —— **落码成立；与计划同轨约束冲突**

- **三处同源（实读）**：唯一索引五列（`TutorVisualEntities.kt:132-155`，名 `index_tutor_visual_snapshot_content_render_identity`）；`deriveSnapshotId` 五参且五段全部 u32be 长度前缀（`TutorVisualIdentity.kt:89-113`）；`findSnapshotByContent` WHERE 五列（`TutorVisualDao.kt:82-101`）。只改一两处会退化为"旧键吞新 id"或"查到旧版本行被静默复用"（实体 KDoc `:98-99`）。
- **迁移（实读 `VisualSnapshotIdentityMigration_70_71.kt`）**：`DROP INDEX IF EXISTS` 旧三列名 → `CREATE UNIQUE INDEX IF NOT EXISTS` 新五列名；**无 `ALTER`**；新键是旧键严格超集 ⇒ 存量行不可能冲突。注册于 `StudyDatabase.kt:411`；`STUDY_DATABASE_VERSION=71`（`:139`）。
- **写入侧 EXISTS**：消息 link / 题面 pin / 指针 CAS 的既有 SQL 门保留（T3 未改其语义）。
- **触发器 backstop**：**未实现**，迁移 KDoc 给出三条具体理由（异常契约、删有分辨力用例、fresh install 不一致），登记待裁（§5.2 #5）。
- **测试（实读）**：`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` 3 例（v70 行存活且键变五列 / bump renderer 产生第二行而非复用 / 索引形状负控）；`TutorVisualIdentityTest` 6 例（含快照黄金向量 `visual-snapshot-1f055235820cba524d912db1488b1174`、版本变化换 id、相邻版本字段无歧义）；`TutorVisualSchemaContractTest` 9 例（v70 断言冻结 + v71 五列断言）；`KernelWave0SchemaContractTest` 8 例（版本 71）。JVM XML 旁证均 0 失败（12:36–12:38，§6）。
- **偏差**：生成与回填在 T4，不在 T3；计划 §2 要求同一轨（§5.2 #4）。

### 3.4 T4 回填 / 计划任务 5 —— **完成（生成物与常量三方同值）**

- **生成物存在**：`71.json` 378,961 B、mtime 10:13、`version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、90 entities / 2 views（python 实读）。
- **Impl 同值**：`StudyDatabase_Impl.kt:264` `RoomOpenDelegate(71, "714c0ecb85ee5edf74f0013740148138", "40cc…")`；`:699` `room_master_table` 同值。
- **常量回填**：`SmbkSelectionPlan.kt:44` `SCHEMA_VERSION=71`、`:51` `IDENTITY_HASH="714c…"`、`:54` `CLOSURE_VERSION=3`。
- **70↔71 结构 diff（记录员 python 实跑）**：90 entities、表名集合相同；只有 `tutor_visual_snapshot` 一个实体不同，其 `fields`/`foreignKeys`/`createSql` 全同，唯一变化是三列唯一索引 → 五列唯一索引；2 视图相同。⇒ 表清单/SPLIT/软引用/闭包/golden 不变，与 T4 结论一致。
- **S=71 清单门（记录员实跑，§10）**：`SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90`、`ONLY_PLAN=[]`、`ONLY_JSON=[]`、`DUP=[]`、`VIEWS_MATCH=True`、`PLAN_VERSION=71 JSON_VERSION=71`、`PLAN_HASH=JSON_HASH=714c…`、`RESULT=PASS`、`EXIT=0`。
- **JVM 旁证**：`SmbkSelectionPlanTest` 12/0（12:37 XML），已指向 `71.json`（`SCHEMA_71_PATH`）。
- **未做**：记录员未运行 Gradle，1→71 矩阵/备份 34/34 的设备面见 §4（且属 11:14 源）。

### 3.5 T5 持久化原子性 / 计划任务 7–9 —— **落码成立；修复轮新增件未执行**

- **预算门在事务内（实读）**：`RoomStudyDatabase.kt` 的 `stageTutorVisualSnapshot`（`:1953-1984`）与 `commitTutorTurnWithVisual`（`:1995-...`）都在 `withWriteTransaction` 内；在 demote/evict 之后重读 `programCheckpointStoredBytes`/`totalCheckpointStoredBytes`，超限抛 `TutorVisualCheckpointBudgetExceededException` 让 Room 回滚；仓储翻译成 PER_PROGRAM/GLOBAL 拒绝。
- **stage 路径修复（修复轮，实读）**：`stageTutorVisualSnapshot` 现在先执行 `command.evictCheckpointSnapshotIds`（`:1963-1971`），仓储 `stageSnapshot` 把 `checkpointPlan.evictUnpinnedSnapshotIds` 带进事务（`TutorVisualCommitRepository.kt:270-284`）。这消灭的是"plan 按淘汰后净值放行、事务内按全量重判拒绝"的方向相反缺陷（修复前只写 `incomingKind`）。
- **evict 端到端与租约**：`TutorVisualDao.kt:208-235` 的 evict SQL 带 `owner_request_id IS NULL OR = 本次 owner OR created_at <= 租约截止`；pin/kind/status 谓词保留。
- **digest 绑定**：`RoomStudyDatabase.kt:2259-2262` 对 `stateSha256` 与 `drawCommandsSha256` 双向 `require` 等于 `sha256Hex(json)`。**`sceneSha256` 仍未校验**（§5.2 #10）。
- **测试（实读）**：提交套件 11 例（含 state/draw digest 负向）；仓库套件 **9 例**（设备跑 8），新增第 9 例 `stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`（`:423-495`）专门钉 stage 路径的净值/全量口径；结构门 5 例含"仅因 pinned 才保留"。
- **未执行**：第 9 例与 stage 修复均未编译/上设备（设备 APK 11:14–11:17，文件 mtime 12:27）；仓库 8/8 与提交 11/11 的绿轮不覆盖当前 `RoomStudyDatabase`/`TutorVisualCommitRepository`/`TutorVisualCommit`/`port`/`VisualCheckpointRetention`（§4.4）。

### 3.6 T6 文档回改 / 计划任务 10 —— **已改；判据计数仍局部漂移**

- **台账反向虚报回改（实读）**：KD-77 标题已改为"镜像已装盘、旧包冒烟 logcat 未落仓、修复后 APK 未跑"（`docs/known-defects.md:2883`），并新增前提回改段；KD-80 追加批次 32 T6 / 集成 / 设备轨三段 Progress；新增 **KD-81**（AUD-08 的 `source.db.sha256` 不含 WAL/SHM，`:3006`）。
- **契约 §8.1**：批次 31 的 LIVE 语义正确，T6 补 `INITIAL` 排除与批次 32 T3 的 STAGING 租约锚点（`docs/current/visual-generation-contract.md:190`）；§8 追加快照身份边界（`:208`）。
- **T1 判据行锚/1.000**：批次 31 判据已按 10:10 现树回改（顶部漂移块 + §2.2/§2.7/§2.8/§3）；1.000 标为旧源期望并给出跨栅格推导。
- **KDoc 命令**：批次 31 点名的 2 个 KDoc + grep 枚举的第 3 个（`AssessmentItemVerificationStatusInstrumentedTest`）改为 `assembleDebugAndroidTest` + `install -r -t` + `am instrument`；commit/repo/70→71 的"未上设备/未执行"反向句已按设备结果回改（现读为 E-08/E-04 实测）。
- **AUD-08**：不改工具代码，登记 KD-81（受控实验 + 本轨 sha/stat 读数 + 检测命令），`tools/split_golden/README.md` 与契约同步。
- **残留**：T1 判据 §5.1/§5.2 计数仍 4/10/5（现树 5/11/9）；parity 两文件对屏侧 painter 计数仍写 19 vs 24（§5.2 #9/#12）。

### 3.7 T7 条件轨 —— **分支 D 登记，零改动**

- 开工复核：热区 library/tutor/ui 仍脏，13 项热区计数与批次 32 基线逐项同值；按批次 24 R4 九项序位逐项复核，九项全部仍 pending、载体均在，未新建 KD/条目/文件（台账 81 条，尾条 KD-81 为 T6 登记）。
- 本批状态变化：schema 71 已推进（`STUDY_DATABASE_VERSION=71`、`71.json`）；AUD-08 门行锚从 MASTER-LINE `:152` → `:153`；KD-81 新登记。

### 3.8 集成交接 —— **三处接线核完；补正判据两处**

- **schema 71 → 备份清单/引用站点/契约常量**：备份清单不需要改（记录员实跑 S=71 脚本 PASS）；引用站点符号化（`BackupSnapshotTrimmer:268-283` 与两个测试读 `SmbkSelectionPlan` 常量），无需字面量替换；三处 hash 同值（71.json / Impl / 常量）。
- **T2 阈值常量与判据文档**：`calibrated-r2` 唯一事实源；判据文档两处与代码不一致已修（`PARITY_GATE` 末段串、§0/§1.2 计数 4/10/5 → 5/11/8）；N10/N11/N12 用例体零增删。
- **T5 新增门与仓储调用点**：预算常量收口到 `core/database.TutorVisualCheckpointBudget`；事务内重判在 stage/commit 都接上；异常经 `translateFullDisk` 翻译；evict SQL 三选一租约；命令层允许 demote/evict 重叠并注明理由。
- **零生产装配改动**：app/feature 对 `TutorVisualCommitRepository`/`TutorVisualPort`/`TutorVisualIdentity` 与新增门符号 grep 零命中；`SmartMistakeBookApplication.kt` 工作树干净；A-01/A-13 未被触碰。
- **补正判据**：批次 32 判据文档补齐 71 生成/回填后的状态、计数、B3 套件、`PARITY_GATE` 串与 `SCHEMA_71_PATH`；`known-defects.md` KD-80 追加接线后记，纠正 T6 10:05 快照里"71.json 仍不存在/常量仍 70"的已不成立表述，同时保持 KD-80 open（S=71 设备门仍在 11:14 源上跑过，但现树未复跑）。

### 3.9 设备轨与证据轨（摘要，详见 §4）

- 设备轨串行 13 步（`emulator-5562` / API 34 / x86_64 / PAGE_SIZE 4096）；四包 `assemble*` **EXIT=0**（`step00-build.log` `BUILD SUCCESSFUL in 24s`；`step00b-db-rebuild.log` 4s），构建前后 861 源文件 sha256 相同；四包 install 成功、设备侧 sha256 与宿主一致（E-01）；去符号门 `DEX OK`；schema assets 71 个 json 含 71.json。
- 证据：15 份 `.md`、文件字节合计 **65,677 B**（记录员 `find -printf` 求和）、`du -sh` = **96K**、最大 8,509 B（00-index）；13 份 logcat 的 9 类崩溃签名记录员实跑 **全 0**；worker spawn 17 = ready 17、`warm-up failed` 0；PDF `simulated write failure` 命中 230。

## 4. 设备与证据

> 环境：`emulator-5562`（AVD `test_device`；API 34、x86_64、PAGE_SIZE 4096）；零 `connectedAndroidTest`、零 `adb root`、零 git 写。

### 4.1 产物身份（E-00/E-01）

| 产物 | 字节 | sha256（宿主=设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,532,939 | `fb235c0aedf33da3c1c0c12932b9951e188f61799a92bd29a968225c0a3a438e` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197,769,972 | `de27b081942695474091500d0d7f4a53e8b70e5b3106f1c152f3e1ae2325f15c` |
| `database-debug-androidTest.apk` | 196,829,660 | `90be7c2bc54da6f68067c421e1cc0cc8538f892dab77e1514a0d3f8d89115dda` |

- 记录员 `stat` 复核：四个 APK 的字节数与上表一致；mtime = 11:14:10 / 02:27:04 / 11:14:02 / 11:17:33。
- **`app-strictOffline-debug-androidTest.apk` 早于本次构建（02:27）**，但 `step00-build.log` 显示 app androidTest 编译/打包任务 `UP-TO-DATE`，且 `app/src` 在 11:14 前后快照一致 ⇒ 隔离/flags 结果对应当前 app 测试源（§4.4 的 11 文件均不在 app/）。

### 4.2 13 步结果（原始日志在 `build/device-track-batch32/`，记录员 grep 复核）

| step | 套件 | 用例数 | 结果（记录员 grep） | 判定 |
|---|---|---|---|---|
| 01 | 备份 | 34 | `OK (34 tests)` / 29.74 | **PASS**；批次 30 的 8 红逐条转绿 |
| 02 | 69→70 迁移 | 4 | `OK (4 tests)` | **PASS** |
| 03 | 迁移矩阵 1→71 | 15 | `OK (15 tests)` / 426.946 | **PASS** |
| 04 | 70→71 专用 | 3 | `OK (3 tests)` | **PASS** |
| 05 | 结构门 | 5 | `Tests run: 5, Failures: 1` | **FAIL**：`evictionDeletesOnlyUnpinnedStagingIntermediates` |
| 06 | 提交/CAS | 11 | `OK (11 tests)` | **PASS**（11:14 源） |
| 07 | 视觉仓库 | 8 | `OK (8 tests)` | **PASS**（11:14 源） |
| 08 | 一致性原型门 | 16 | `OK (16 tests)` / 0.989 | **PASS**（15 负控全红） |
| 09 | 裁剪器 | 9 | `OK (9 tests)` | **PASS** |
| 10 | painter | 24 | `OK (24 tests)` | **PASS** |
| 11 | PDF | 16 | `OK (16 tests)` | **PASS**；`simulated write failure` 230 |
| 12 | 隔离 | 13 | `OK (13 tests)` / 62.517 | **PASS**；spawn 17 = ready 17 |
| 13 | flags | 2 | `OK (2 tests)` | **PASS** |

- **红例归因（A，未修于设备轮）**：结构门 `:205` 先断言 `snap-staging-middle` 已删除，`:221` 又用 `checkpointSummaries.getValue` 读同一行，必抛 `NoSuchElementException`；产品淘汰/保留断言全过。**修复轮已改**：新增不在淘汰候选集的 `snap-staging-keeper` 种子行，读侧改读它（现树 `:187`/`:230`），未删断言、未放宽、未 `@Ignore`；**修复版从未编译/上设备**，因此"结构门全绿"当前不成立。
- **parity 负控**：`PARITY_CONTROL` 15 条，`red=true` 15；P06/P07 明细见 §10；`PARITY_GATE_DESENSITIZED` 0。
- **KD-80**：80-C1（S=71 备份 34/34）与 80-C2（S=71 清单 `RESULT=PASS`，记录员复跑）已有绿轮；80-C3 的 JVM 半边有 XML 旁证（12:37–12:38，12/0）但仓内无命令日志；80-C4 仍零断言（34 例对 `tutor_visual` 行/引用）→ **保持 open**。
- **9 类签名**：13 份 `*-logcat.txt` 逐份 grep **全 0**（记录员实跑）。

### 4.3 证据轨

- 落盘：`docs/research/evidence/2026-10-29-batch-32/` **15 个 `.md`**；文件字节合计 **65,677 B**（`find -printf '%s'` 求和），`du -sh` = **96K**；最大 `00-index.md` **8,509 B**；`build/device-track-batch32/*-logcat.txt` = **13** 份。R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触。
- 索引自述"实际字节 64,683 / 最大 8,235 B / 240 M + 480 ??"与记录员时点实测有小时点差（65,677 / 8,509 / 240 M + 478 ?? + 3 staged），属索引写后文件继续被编辑，不影响合规。
- 脱敏：证据索引自述 R4 四条扫描 0 命中；记录员未重跑 R4 正则（只读抽查未发现密钥/邮箱）。

### 4.4 设备源与现树不同源（本批最重的事实）

记录员用 `build/device-track-batch32/src-sha-before.txt`（构建前 **861** 文件 sha256；`cmp` 与 after 相同）逐文件对比当前树：

- **850 相同 / 11 不同 / 0 缺失**。11 个文件全部属本批 visual/parity/schema 面，mtime **12:26–12:28**（设备构建 11:14–11:17、设备跑 11:18–11:30）：
  1. `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（12:27）
  2. `core/data/src/androidTest/.../visual/VisualDrawOpsParityInstrumentedTest.kt`（12:28）
  3. `core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt`（12:28）
  4. `core/data/src/main/.../visual/TutorVisualCommitRepository.kt`
  5. `core/data/src/main/.../visual/VisualCheckpointRetention.kt`
  6. `core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`
  7. `core/database/src/androidTest/.../TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt`
  8. `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`（12:26）
  9. `core/database/src/main/.../RoomStudyDatabase.kt`（12:27）
  10. `core/database/src/main/.../TutorVisualCommit.kt`
  11. `core/database/src/main/.../port/TutorVisualPort.kt`
- 直接后果：
  - **提交 11/11、仓库 8/8、parity 16/16、70→71 3/3 的绿轮不对应当前源码**；`stageTutorVisualSnapshot` 的淘汰集接线、`stageSnapshot` 的 plan 消费、evict 租约改动都在设备构建之后落树，**从未在设备上执行**。
  - **仓库第 9 例**（stage 路径净值/全量口径）**从未编译、从未运行**；现树 `grep -c @Test` = 9，设备 run = 8。
  - **结构门修复版**（keeper 行）**从未编译、从未运行**；设备红例仍在（4/5）。
  - parity 两文件在 12:28 的改动至少包含 KDoc/计数，是否含行为改动无法从现有证据隔离；**当前文件的绿轮 UNVERIFIED**。
  - JVM 单测在 12:36–12:38 重跑过（XML 0 失败），说明 **main 源可编译**；但 **androidTest 源自 11:17 后未再编译**，强门的"androidTest 编译"对当前树 UNVERIFIED。
- 因此：**12 步骤的"绿"只对 11:14 的源成立**；对当前树，parity/提交/仓库/结构门/70→71 未复跑，修复轮新增件未验证。按 T1 判据 §1.3-5 自己的规则，应判"构建早于源改动"，需重建重跑。

## 5. 复核发现与处置

> 口径：凡"修复后设备未复跑"一律 UNVERIFIED。级别分布：**高 ×2、中 ×5、低 ×8**。

### 5.1 复核修复轮已处置项（记录员核实现树已生效）

| # | 复核点 | 现树处置（记录员实读） |
|---|---|---|
| R1 | 结构门红例自相矛盾 | **已修**：新增 `snap-staging-keeper` 种子行（`:187`），读侧改读它（`:230`）；未删断言、未放宽、未 `@Ignore`；**绿轮未跑** |
| R2 | 审查发现 stage 路径预算口径不一致 | **已修**：`stageTutorVisualSnapshot` 执行 `evictCheckpointSnapshotIds`（`:1963-1971`），仓储带 plan 淘汰集（`TutorVisualCommitRepository.kt:270-284`），并新增第 9 例；**未编译/未跑** |
| R3 | KDoc 仍写"未上设备/未执行" | **已修**：commit/repo/70→71 三处 KDoc 改为 E-08/E-04 实测计数，并注明第 9 例未上设备 |
| R4 | 判据 §5.3/§5.4 写"预算未入事务、evict 无租约" | **已修**：改为设备轨后记（事务内 `:2162-2184`、租约 `:208-235`） |
| R5 | N09 注释错误代数 | **未修**：`:317-318` 仍写"该值与基线精度无关（b² = a² + 2·vx + 1 ⇒ d(+1)=b）" |

### 5.2 本轮记录的独立发现

| # | 级别 | 位置 | 发现 | 状态 |
|---|---|---|---|---|
| 1 | **高** | `build/device-track-batch32/src-sha-before.txt` vs 现树；11 个文件 mtime 12:26–12:28 | **设备绿轮与现树不同源**：850/861 相同、**11 文件不同**；提交/仓库/parity/70→71/结构门五套件覆盖其中文件；stage 修复与第 9 例、keeper 修复从未编译/运行。设备"绿"不得套到当前树 | 已独立复核 |
| 2 | **高** | 计划 §3 `:40`；无批次 32 gate 日志 | **"双门 exit=0"未被记录员验证**：工单给定 exit=0，但仓内只有 12:36–12:38 JVM XML（0 失败）作旁证；无批次 32 的单测门/强门日志目录；androidTest 源自 11:17 后未编译 ⇒ 强门对当前树 UNVERIFIED | 已独立复核 |
| 3 | 中 | `build/device-track-batch32/step05-structure-gates.log`；`TutorVisualStructureGatesInstrumentedTest.kt:168-235` | **结构门当前仍不是全绿**：设备 `Tests run: 5, Failures: 1`；修复版未跑。批次验收"结构门全绿"不成立 | 已独立复核 |
| 4 | 中 | 计划 `:33`；T3/T4 摘要 | **计划硬约束"schema 71 单写者 = 任务 4/5 同一轨"被违反**：T3 代码/迁移、T4 生成/回填分两轨。三处 hash 当前同值、结果自洽，但属计划偏差，需主会话确认接受 | 已独立复核 |
| 5 | 中 | `VisualSnapshotIdentityMigration_70_71.kt:42-59`；计划 `:19` | **计划任务 4 的"触发器 backstop"未实现**，仅以三条理由登记待裁；本批不得声称 DB 级触发器生效 | 已独立复核 |
| 6 | 中 | `TutorVisualCommitRepositoryInstrumentedTest.kt:423-495`；`RoomStudyDatabase.kt:1959-1971` | **stage 路径修复只有静态证据**：新用例与代码均在 12:27 落树，设备跑的是修复前 8 例；修复是否真的让"总量超、净值不超"场景通过，**UNVERIFIED** | 已独立复核 |
| 7 | 低 | `VisualDrawOpsParityInstrumentedTest.kt:317-318` | **N09 注释的代数句不成立**："b²=a²+2·vx+1 ⇒ d(+1)=b"只是定义，不能推出 d(+1)=b；P06 的精确值来自设备读数 pin。建议改写注释；不影响断言 | 已独立复核 |
| 8 | 低 | `VisualDrawOpsParityInstrumentedTest.kt:370-449` | **N13 未覆盖 1 px 真盲集**：它用 +2 px、bboxDev=5，由 bbox 抓住；"质心盲区由 bbox/shift 兜底"只证到 2 px 档，1 px（bboxDev=1、bestShift 平局）路径仍存在（研究文档已声明"1 px 不保证"） | 已独立复核 |
| 9 | 低 | `VisualDrawOpsParityInstrumentedTest.kt:52` vs `VisualDrawOpsParityMetrics.kt:15` | **屏侧 painter 计数互相矛盾**：测试 KDoc 写"设备 19 例"，metrics KDoc 写"设备 24 例"；现树实为 24（`grep -c @Test`） | 已独立复核 |
| 10 | 低 | `RoomStudyDatabase.kt:2259-2262` | **`sceneSha256` 仍未校验**：只 require state/draw digest == sha256(json)，scene digest 可伪造落库；当前无消费方，故低危 | 已独立复核 |
| 11 | 低 | `TutorVisualCommitInstrumentedTest.kt:128-167,170-209` | **digest "写入前拒绝"用例只断言快照数为 0**：实际顺序是先 `ensureTutorVisualProgram` 再 `ensureTutorVisualSnapshot`，靠事务回滚才净效果为零；未断言 program 数为 0，将来把 digest 校验移出事务不会红 | 已独立复核 |
| 12 | 低 | `docs/research/2026-10-29-batch-32-device-criteria.md:343-370` | **判据 §5.1/§5.2 计数仍 4/10/5**：现树 5/11/9；00-index §5-6 已自认漂移，但被点名的两节未回改 | 已独立复核 |
| 13 | 低 | `docs/research/evidence/2026-10-29-batch-32/00-index.md:8,11` | **证据索引数字有小幅时点差**：写 64,683 B / 最大 8,235 B / 480 ??，实测 65,677 B / 8,509 B / 478 ?? + 3 staged；不影响合规 | 已独立复核 |
| 14 | 低 | `VisualDrawOpsParityInstrumentedTest.kt:907`；设备 logcat | **P07 常量与设备读数差 1.04e-12**：常量 `0.608282132823731` 是反解值，设备实测 `0.608282132824767`；容差 0.02 覆盖，非缺陷，但审计需知道 P07 期望不是设备 pin | 已独立复核 |
| 15 | 低 | `docs/known-defects.md:2996` | **KD-80 10:13 集成块仍含已被取代的"未在本批执行"句**：其后 11:19–11:35 设备轨块显式纠正（34/34、15/15、3/3、9/9），但历史块未标注"已被取代"。建议加回链，不改关闭判定 | 已独立复核 |

**正向结论（已核实，不计入发现）**：

- `71.json` / 生成的 `StudyDatabase_Impl` / `SmbkSelectionPlan` 三处 `identityHash` 逐字同值 `714c0ecb85ee5edf74f0013740148138`；`71.json` version=71、90 entities、2 views（python 实读）。
- 70↔71 只换 `tutor_visual_snapshot` 的唯一索引（三列→五列），字段/FK/createSql/表/视图全同；新键是旧键严格超集。
- S=71 备份清单门记录员实跑 `RESULT=PASS`、`EXIT=0`（90/90/90、双向差集空、version/hash 同源）。
- `deriveSnapshotId` 五参、五段长度前缀；`findSnapshotByContent` 五列 WHERE；唯一索引五列——三处同源。
- `calibrated-r2` 的判据声明（方向相关、bestShift 主/bboxDev 辅/质心护栏、盲盘、≥2 px 保证）在 KDoc 与设备 logcat 中一致。
- N09 的跨栅格期望与设备读数一致（P06 逐位同、P07 差 1.04e-12，容差 0.02）。
- 设备 1→71 矩阵 15/15、备份 34/34、70→71 3/3；9 类签名 13 份全 0。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| KSP 生成 | **exit=0**（工单给定） | `71.json` 378,961 B / 10:13；`step00-build.log:240` `kspDebugKotlin UP-TO-DATE` |
| 单测门（第 1 轮） | **exit=0**（工单给定） | 仓内无本轮 gate 日志；记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | 记录员未复跑 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 设备轨 `assemble*` 11:14/11:16 `BUILD SUCCESSFUL`；**修复后 androidTest 未再编译** |
| 修复环后单测门 | **exit=0**（工单给定） | 旁证：12:36–12:38 JVM XML `TutorVisualIdentityTest` 6/0、`TutorVisualSchemaContractTest` 9/0、`KernelWave0SchemaContractTest` 8/0、`SmbkSelectionPlanTest` 12/0、`VisualCheckpointRetentionTest` 17/0 |
| 设备 备份 34 | **PASS `OK (34 tests)`** | 批次 30 的 8 红逐条转绿 |
| 设备 69→70 4 | **PASS `OK (4 tests)`** | 结构读数断言 |
| 设备 70→71 3 | **PASS `OK (3 tests)`** | 身份负向（版本变更产生第二行） |
| 设备 迁移矩阵 15 | **PASS `OK (15 tests)`** | 1→71 |
| 设备 parity 16 | **PASS** | 15 负控全红；`calibrated-r2`；**11:14 源** |
| 设备 提交 11 / 仓库 8 | **PASS（11:14 源）** | 现树仓库 9，第 9 例未跑 |
| 设备 结构门 5 | **FAIL 1/5** | A 类；修复版未跑 |
| 设备 裁剪器 9 / painter 24 / PDF 16 / 隔离 13 / flags 2 | **PASS** | 隔离 spawn 17=ready 17；PDF write failure 230 |
| S=71 清单门 | **PASS `RESULT=PASS` / `EXIT=0`** | 记录员实跑（§10） |
| 9 类签名 | **13 份 logcat 全 0** | 只证无崩溃，不代表套件全绿 |
| schema 71 三段 | **Stage 1/2/3 静态与设备面成立；当前树复跑 UNVERIFIED** | Stage 2 是 11:14 源 |
| 生产 release 签名 / 真实 Provider | **UNVERIFIED** | 本批未跑；JS 不广告 |

**记录员保留**：五条门禁 `exit=0` 无本轮日志落仓，记录员未运行 Gradle；JVM XML 是 `build/` 旁证（非仓内证据）；设备数字均有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；现树与设备 APK 不同源（11 文件），不得混用。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面（本批最重）**

1. **设备绿轮与现树 11 文件不同源**：提交/仓库/parity/70→71/结构门的新内容未上设备；修复轮新增件（stage 修复、第 9 例、keeper 结构门修复）从未编译/运行。
2. **强门对当前树未验证**：androidTest 源自 11:17 后未再编译；无批次 32 gate 日志。
3. **结构门当前非全绿**：设备 4/5；修复版待跑。
4. **parity 当前文件未复跑**：设备 16/16 是 11:14 源；12:28 改动是否含行为变化无法隔离。
5. **KD-80 80-C4 零断言**：34 例不覆盖视觉四表引用保持；KD-80 保持 open。
6. **AUD-08**：设备数据度量仍 UNVERIFIED（批次 31 导出 0 页、门 FAIL）；WAL 溯源缺口登记为 KD-81（工具代码未改）。

**代码 / 文档残留**

7. **触发器 backstop 未落**（待裁；本批不声称 DB 级触发器生效）。
8. **`sceneSha256` 未校验**（§5.2 #10）。
9. **digest "写入前拒绝"用例未断言 program 数为 0**（§5.2 #11）。
10. **N13 未覆盖 1 px 真盲集**（§5.2 #8）；**N09 注释错误代数**（§5.2 #7）。
11. **parity 两文件屏侧计数 19 vs 24**（§5.2 #9）。
12. **T1 判据 §5.1/§5.2 计数 4/10/5** 与现树 5/11/9 不符（§5.2 #12）。
13. **证据索引小数字漂移**（§5.2 #13）。
14. **KD-80 历史块未标"已被取代"**（§5.2 #15）。

**环境 / 口径**

15. **一致性门仍是原型门**：`product-gate=no`；不得引用为产品/发版结论。
16. **arm64/16 KB、真机、低内存/高负载、API 23、跨 fontScale** 未覆盖；本批设备仅 x86_64/API 34/4 KB。
17. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
18. **4E.2 MVA 仍不成立**：本批是标定纠正 + schema 71 + 原子性，不等于双后端/导出/质量门就绪。
19. **热区在飞未触碰**：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、app、docs/current 其余 M、docs/research 他线未跟踪记录等。
20. **锁文件 14 个**：KD-72 窗口仍不满足，一律不提交。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；若引用"设备绿"，先按 §4.4 重建重跑（当前树与设备 APK 11 文件不同源）；热区、14 个 `*.lockfile` 与 `build/`、`tmp/` 一律排除。按 ≤6 路径/块：

- **块 1（T2 标定）**：`core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt`（改；**整文件纳入**）、`VisualDrawOpsParityInstrumentedTest.kt`（改；**整文件纳入**）、`docs/research/2026-10-29-parity-metric-correction.md`（新）
- **块 2（schema 71 实现）**：`core/database/schemas/.../71.json`（**新，已 staged**）、`core/database/.../VisualSnapshotIdentityMigration_70_71.kt`（**新，已 staged**）、`core/database/.../StudyDatabase.kt`（改）、`entity/TutorVisualEntities.kt`（改）、`TutorVisualIdentity.kt`（改）、`dao/TutorVisualDao.kt`（改；**整文件纳入**）
- **块 3（schema 测试）**：`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt`（**新，已 staged**）、`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`（改）、`TutorVisualSchemaContractTest.kt`（改）、`KernelWave0SchemaContractTest.kt`（改）、`TutorVisualIdentityTest.kt`（改）
- **块 4（T4/T5 主代码）**：`core/data/.../backup/SmbkSelectionPlan.kt`（改）、`SmbkSelectionPlanTest.kt`（改）、`core/data/.../visual/TutorVisualCommitRepository.kt`（改；**整文件纳入**）、`VisualCheckpointRetention.kt`（改）、`core/database/.../RoomStudyDatabase.kt`（改；**整文件纳入**）、`TutorVisualCommit.kt`（改）
- **块 5（T5 测试）**：`core/database/.../TutorVisualCommitInstrumentedTest.kt`（改）、`core/data/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（改）、`core/database/.../TutorVisualStructureGatesInstrumentedTest.kt`（改）、`core/database/.../port/TutorVisualPort.kt`（改）
- **块 6（研究/判据/记录）**：`docs/research/2026-10-29-batch-32-baseline.md`、`2026-10-29-schema-generation-flow.md`、`2026-10-29-batch-32-device-criteria.md`、`2026-10-29-batch-32-t6-doc-consistency.md`、`2026-10-29-batch-32-record.md`（均新/未跟踪）
- **块 7（证据前半）**：`docs/research/evidence/2026-10-29-batch-32/00-index.md`、`step00-environment.md`、`step01-build-install.md`、`step02-parity.md`、`step03-migration-69-70.md`、`step04-migration-70-71.md`
- **块 8（证据中段）**：`step05-migration-matrix.md`、`step06-backup.md`、`step07-structure-gates.md`、`step08-commit-repo.md`、`step09-trimmer.md`、`step10-painter-pdf.md`
- **块 9（证据后段 + 台账）**：`step11-isolation-flags.md`、`step12-signatures.md`、`step13-red-attribution.md`、`docs/known-defects.md`（改；**整文件纳入**）、`docs/REFACTOR-MASTER-LINE.md`（改；**整文件纳入**）、`docs/research/algorithm-version-ledger.md`（改；**整文件纳入**）
- **块 10（契约/决策/他批文档）**：`docs/current/visual-generation-contract.md`（改；**整文件纳入并注明他线 M**）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改）、`docs/research/2026-10-29-batch-31-device-criteria.md`（改；**整文件纳入**）、`tools/split_golden/README.md`（改）、`core/database/src/androidTest/.../AssessmentItemVerificationStatusInstrumentedTest.kt`（改；仅 KDoc）

**明确排除（他线在飞/不安全）**：全部 `?? */gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`；core/data 他线 M（mistake/study/knowledge/model/capture/backup 测试等）；core/database 开工即脏的其余 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`PendingCaptureDao`、`TutorConversationDao`、`CleanRedrawTaskPort`、LibraryFts 三测）；`VisualDrawOpsParserTest.kt`（他线改）；app 全部 M/??；docs/current 其余 M；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；`tmp/**`。

**提交说明**：建议 10 块、每块 ≤6 路径；整文件纳入的 M（`VisualDrawOpsParityMetrics`、`VisualDrawOpsParityInstrumentedTest`、`RoomStudyDatabase`、`TutorVisualCommitRepository`、`TutorVisualDao`、`known-defects`、`REFACTOR-MASTER-LINE`、`algorithm-version-ledger`、`visual-generation-contract`、批次 31 判据）在提交说明中注明"整文件纳入，含本批/他线改动"；三条 staged 新文件确认内容未变；提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1 455 行（跨栅格定义、反解、盲盘、r2）、R2 669 行（identityHash 生成链、192 组反算、迁移设计、触发器裁定）落盘；未验证项如实。
- **实施**：T1 判据 914 行；T2 `calibrated-r2` + N09/N13；T3 五列三处同源 + 迁移；T4 `71.json` + 常量回填；T5 事务内预算 + evict 租约 + digest + stage 修复；T6 台账/契约/KDoc；T7 分支 D。
- **集成**：三处接线静态成立；生产装配仍为零。
- **设备**：13 步，12 绿 / 1 红；红例 A 类、修复版未跑；9 类签名 13 份全 0；隔离 17↔17；PDF 230。
- **门禁**：五条 `exit=0`（工单给定）；JVM XML 旁证 0 失败；记录员未复跑。
- **本批不声称全绿/全就绪**：设备与现树 11 文件不同源、修复轮新增件未编译/未跑；结构门仍红；KD-80 C4 零断言 + C3 半边缺命令归属 → open；KD-71/72/73/77 open；KD-81 新开；触发器 backstop 未落；AUD-08 设备数据 UNVERIFIED；API 23/arm64/生产签名/真实 Provider 均 UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告；4E.2 MVA 不成立。
- **台账同步**：`docs/REFACTOR-MASTER-LINE.md`（批次 32 滚动行）、`docs/known-defects.md`（KD-80 记录回链）、`docs/research/algorithm-version-ledger.md`（schema 71 登记）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 32 裁定节）已由本轮记录员写入；均标注"记录员只读核对，未运行 Gradle/设备"。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 0b4bdbbe
git rev-parse --short origin/main                            # 0b4bdbbe（本地跟踪引用，未 fetch）
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain | wc -l                               # 721
git status --porcelain | grep -c '^ M'                       # 240
git status --porcelain | grep -c '^??'                       # 478
git diff --cached --name-only | wc -l                        # 3（71.json / 迁移 / 70→71 测试）

# 热区（脏行数）
for p in feature/tutor feature/library feature/review feature/capture feature/profile \
         core/ui core/markdown core/export core/domain; do
  printf '%-22s %s\n' "$p" "$(git status --porcelain -- $p | wc -l)"
done                                                          # 59/43/12/16/4/35/1/6/5

# 版本与 schema
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt   # :139 = 71
ls core/database/schemas/.../ | grep -c '\.json$'                     # 71
python -c "...71.json..."   # version 71 / hash 714c0ecb85ee5edf74f0013740148138 / 90 entities / 2 views
grep -n "714c0ecb85ee5edf74f0013740148138" core/database/build/generated/ksp/debug/.../StudyDatabase_Impl.kt
# :264 RoomOpenDelegate(71, "714c…", "40cc…")；:699 room_master_table 同值
grep -n "SCHEMA_VERSION\|IDENTITY_HASH\|CLOSURE_VERSION" core/data/.../backup/SmbkSelectionPlan.kt
# 44:71 / 51:714c… / 54:3

# 70↔71 结构 diff（python 实跑）
# diff tables ['tutor_visual_snapshot']；fields same True；fk same True；createSql same True；views same True
# entities 90/90，表名集合相同

# S=71 清单门（python，记录员实跑）
# SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90
# ONLY_PLAN=[] ONLY_JSON=[] DUP=[] VIEWS_MATCH=True
# PLAN_VERSION=71 JSON_VERSION=71
# PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138
# RESULT=PASS / EXIT=0

# 测试计数（grep -c '@Test'）
# parity 16 / painter 24 / 结构门 5 / 提交 11 / 仓库 9 / 70→71 3 / 69→70 4 / 矩阵 15
# 备份 34 / 裁剪器 9 / SmbkSelectionPlanTest 12 / TutorVisualIdentityTest 6
# TutorVisualSchemaContractTest 9 / KernelWave0SchemaContractTest 8

# 设备日志
for f in build/device-track-batch32/step*.log; do printf '%-40s ' "$(basename $f)"; \
  grep -E "OK \(|Tests run:|RESULT=" "$f" | tail -1; done
# 34/4/15/3/5-1/11/8/16/9/24/16/13/2
grep -c 'PARITY_CONTROL ' build/device-track-batch32/step08-parity-logcat.txt   # 15
grep -c 'red=true'         build/device-track-batch32/step08-parity-logcat.txt   # 15
grep -n 'PARITY_CONTROL_DETAIL N09-P0[67]' build/device-track-batch32/step08-parity-logcat.txt
# P06 centroid=1.2624767812258337 bboxDev=2 bestShift=(1,0)
# P07 centroid=0.608282132824767 bboxDev=2 bestShift=(1,0)
grep -c 'Start proc .*visual_js.*VisualJsService' build/device-track-batch32/step12-isolation-logcat.txt  # 17
grep -c 'worker ready' build/device-track-batch32/step12-isolation-logcat.txt                              # 17
grep -c 'warm-up failed' build/device-track-batch32/step12-isolation-logcat.txt                            # 0
grep -o 'IOException: simulated write failure' build/device-track-batch32/step11-pdf-logcat.txt | wc -l    # 230

# 设备构建源快照 vs 现树（关键）
wc -l build/device-track-batch32/src-sha-before.txt build/device-track-batch32/src-sha-after.txt   # 861 / 861
cmp -s before after && echo before==after                                                          # 相同
# 逐文件 sha256 比对：850 相同 / 11 不同 / 0 缺失（11 个见 §4.4）

# 9 类签名
for f in build/device-track-batch32/*-logcat.txt; do printf "%s " "$(basename $f)"; \
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f" || true; done
# 13 份逐份 0

# 证据体积
find docs/research/evidence/2026-10-29-batch-32 -name '*.md' | wc -l                 # 15
find docs/research/evidence/2026-10-29-batch-32 -name '*.md' -printf '%s\n' | awk '{s+=$1} END{print s}'  # 65677
du -sh docs/research/evidence/2026-10-29-batch-32                                   # 96K

# JVM XML（build/ 旁证，非仓内证据）
# TutorVisualIdentityTest 6/0；TutorVisualSchemaContractTest 9/0；KernelWave0SchemaContractTest 8/0
# SmbkSelectionPlanTest 12/0；VisualCheckpointRetentionTest 17/0；mtime 2026-10-29 12:36–12:38

# 关键源码锚点（实读）
grep -n "THRESHOLD_VERSION" core/data/src/androidTest/.../VisualDrawOpsParityMetrics.kt   # :266 calibrated-r2
grep -n "fun n09\|fun n13" core/data/src/androidTest/.../VisualDrawOpsParityInstrumentedTest.kt  # :309 / :370
grep -n "evictCheckpointSnapshotIds" core/database/.../RoomStudyDatabase.kt                # :1963
grep -n "evictUnpinnedSnapshotIds" core/data/.../visual/TutorVisualCommitRepository.kt      # :279
grep -n "sceneSha256" core/database/.../RoomStudyDatabase.kt                                # 无 require（2259/2262 只有 state/draw）

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 31 记录的结构对应

| 批次 31 节 | 本批对应 | 差异 |
|---|---|---|
| §2 两研究线 | §2 两研究线 | 本批为度量纠正 + schema 生成/迁移；R1/R2 均含 GitHub/官方文档外部参考 |
| §3 七实施轨 + 集成 | §3 七实施轨 + 集成 | 轨名不同（T1 判据 / T2 门纠正 / T3 schema / T4 回填 / T5 原子性 / T6 文档 / T7 条件） |
| §4 设备与证据 | §4 设备与证据 | 13 步；12 绿 1 红；新增"设备源与现树 11 文件差"小节 |
| §5 复核发现 | §5 复核发现 | 5 项复核轮处置 + 15 项独立发现 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
