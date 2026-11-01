# 批次 30 记录（2026-10-29）：修复后设备复跑 + 视觉持久化四表/CAS/冷恢复 + PDF 写安全 + 缺陷清仓

> 记录时点：2026-10-29（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` **只读**核对计划、基线、两份研究文档、T1/PDF 判据、`docs/research/evidence/2026-10-29-batch-30/` 十一份 `.md`、`build/device-track-batch30/` 原始日志、`docs/research/2026-10-29-batch-30-t12-doc-consistency.md`、台账锚点与关键源码后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码。** 本轮记录员执行的只有只读命令：`git status/rev-parse/log`、`grep -c`、`wc -l`、`find`、`du`、`sha256sum`、`python` 读取 JSON（命令与输出见 §10）。
> 计划：`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md`（下称"计划"，46 行已完整读）。
> 基线：`docs/research/2026-10-29-batch-30-baseline.md`（282 行，只读核对）。
> 设备判据：`docs/research/2026-10-29-batch-30-device-criteria.md`（672 行）、`docs/research/2026-10-29-batch-30-pdf-write-device-criteria.md`（183 行）。
> 设备证据：`docs/research/evidence/2026-10-29-batch-30/00-index.md` 与 `step00`–`step08`、跨轨 `step-aud08-page-ruling.md`。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 2 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；复核修复轮后第 2 轮复跑 `exit=0`（工单给定）。仓内无本轮 gate 日志，记录员未复跑。
> **本记录不声称"批次全绿"**：设备面 **7 套件绿 / 2 套件红 / 1 套件部分红**（隔离 13、flags 2、painter 24、PDF 16、迁移矩阵 15、提交 8、仓库 3 绿；parity 11 例 1 红、备份 34 例 8 红、69→70 迁移 2 例 1 红）。两个红例均在设备轨完成时点（02:25–02:41）**尚未修复**；`SmbkSelectionPlan` 的 70 同步（03:35）、`BackupRestoreInstrumentedTest` 期望回改（03:42）、69→70 断言修复（03:43）、`TutorVisualCommitRepository` 的 checkpoint 接线（03:47）都在设备轨之后落树，**全部未复跑**。冷恢复设备用例**不存在** → UNVERIFIED。**本批验收「五套件全绿」「备份 34 例不回退」均不成立。**

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）与实际执行面

- **计划 12 项**：A 修复后设备面（painter 24/隔离 13/flags 2/PDF 13/一致门 11 复跑；KD-78 ④ 与 KD-79 逐条核销）；B 4E.2 持久化核心（D-01 四表 v70 / D-02 身份与 CAS / D-05 冷恢复 + D-03 备份半场）；C 缺陷清仓（KD-79 修复、KD-71 竞态、解析器三缺口、KD-73、AUD-08、KD-77/KD-72）；E 文档一致性。
- **实际执行面**：两研究线（R1 视觉持久化 / R2 PDF 写安全）+ T1 判据 + T2/T3/T4/T5/T6/T7 七实施轨 + AUD-08 生产者半边 + T12 文档回改 + D 条件轨（零改动）+ 集成交接 + 设备轨（`emulator-5562`，串行十步）+ 证据 11 份 + 本记录与四份台账同步。
- **不做（全部未做）**：core/export 集成与 PDF `/AIGC`（KD-75 热区）；双后端装配（批次 31）；V-01 全链（批次 32）；JS 广告（MVA 未过）；真实 Provider（无凭据）；锁文件生成（KD-72 仍 open）；热区 UI。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature 五模块、core/ui 全部、core/markdown 整目录、core/export、core/domain、`settings.gradle.kts`、`.gitignore`、Root/SecondaryScreens、docs/current 其余 M、tools/kb、knowledge-production。
2. **schema 70 单写者**：`StudyDatabase.kt`/migrations/`schemas` 只由 T2 轨写；其余轨不得改同一文件。
3. **core/database 开工即脏 8 M**：按"整文件纳入并注明"处理；`StudyDatabase.kt`/`schemas` 基线干净。
4. 一致性门仍是**原型门**，不得引用为产品/发版结论；JS 不广告；真实 Provider 无凭据；AIDL 只以 Gradle 任务验证；AAR/stdlib 不变。
5. 设备只 `assemble*` + `adb install -r -t` + `am instrument`，禁 `connectedAndroidTest`；同一落点多个任务合并给一个拥有者。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`a806525e`（`git rev-parse --short HEAD`）；`origin/main` 本地跟踪引用同为 `a806525e`；`git rev-list --left-right --count origin/main...HEAD` = `0 0`（**未 `git fetch`**，远端实时 hash UNVERIFIED）。
- **工作树**：**737 行 = 236 M + 501 ??，staged 0**（记录时点实测）；基线快照为 673 = 201 M + 472 ??（增量 = 本批新文件与新证据，另含他线在飞）。
- **版本串（记录员本轮 grep 复核）**：DB schema **70**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`）；请求 schema 仍 **18**（`core/model/.../ModelTasks.kt:546` `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`；设计 C8：本批无需 19）；`PROTOCOL_VERSION` 2、`drawSchemaVersion` 1、AAR `quickjs-kt-android:1.0.15`、stdlib 2.3.21 均未动。
- **schema 产物**：`core/database/schemas/.../` 71 个条目 = 69 个历史 JSON + `70.json` + `70.json.NOT_EXPORTED.md`。`70.json` 实测 **378,885 B / version=70 / identityHash=11a8a701bae767a8a3c5bc810e4743a4 / 90 entities / 2 setupQueries**，**未跟踪**（`git ls-files` 无命中）；`70.json.NOT_EXPORTED.md` 仍写"尚未导出"，与盘上产物矛盾（§5 #9）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、feature/review **12**、feature/capture **16**、feature/profile **4**、core/ui **35**、core/markdown **1**（整目录未跟踪）、core/export **6**、core/domain **5**；`settings.gradle.kts`/`.gitignore` M；core/data 他线 M 未触碰。本批零越权。
- **锁文件**：盘上 `*.lockfile` **14**（13 个 `gradle.lockfile` + `settings-gradle.lockfile`）；git 可见未跟踪条目 **13**（`core/markdown/gradle.lockfile` 被折叠进 `?? core/markdown/`）；跟踪锁 **0**；staged **0**。W1 六文件 6/6 不干净（§6）。

### 1.4 本批增量点名

- **新增代码（T2–T6，core/database + core/data visual）**：`TutorVisualEntities.kt`(314)、`TutorVisualDao.kt`(492)、`VisualSnapshotMigration_69_70.kt`(188)、`TutorVisualIdentity.kt`(115)、`TutorVisualCommit.kt`(470)、`port/TutorVisualPort.kt`(106)、`TutorVisualCommitRepository.kt`(450)、`VisualSnapshotRestore.kt`(252)、`VisualCheckpointRetention.kt`(214)、`VisualRendererVersion.kt`(20)、`PdfWriteIntegrity.kt`(214)、`VisualDrawOpsDuplicateKeys.kt`(229)。
- **修改（本批，`git diff --numstat`/`--stat` 实测）**：`StudyDatabase.kt` (+14/−1)、`RoomStudyDatabase.kt` (+261/−0)、`StudyDatabasePort.kt` (+3/−1)、`CanonicalAssetReferenceSites.kt`（第六站点）、`SmbkSelectionPlan.kt`（70/90/82/21/CLOSURE 3；106 行变动）、`BackupSnapshotTrimmer.kt` (+108)、`SmbkArchiveCodec.kt`、`AndroidBackupRepository.kt`、`VisualDrawOpsParser.kt` (+155/−39)、`VisualDrawOpsPdfPainter.kt` (+151/−47)、`VisualDrawOps.kt` (+14/−0)、`QuickJsVisualExecutionEngine.kt` (+20/−0)、`VisualDrawOpsPdfPainterInstrumentedTest.kt` (+98)、`VisualDrawOpsParserTest.kt` (+212)、`QuickJsVisualExecutionEngineJvmTest.kt` (+58)。
- **新增测试**：`TutorVisualSchemaContractTest.kt`(496/8 例)、`TutorVisualIdentityTest.kt`(138/5)、`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`(850/2)、`TutorVisualCommitInstrumentedTest.kt`(512/8)、`TutorVisualCommitRepositoryInstrumentedTest.kt`(228/3)、`VisualSnapshotRestoreTest.kt`(266/12)、`VisualCheckpointRetentionTest.kt`(321/13)、`PdfWriteIntegrityTest.kt`(244/16)。
- **文档**：R1（539 行）、R2（405 行）、T1 判据（672）、PDF 判据（183）、T12 记录（180）、设备证据 11 份、本记录 + 四份台账同步。

## 2. 两研究线

### 2.1 R1 视觉持久化核心设计（`docs/research/2026-10-29-visual-persistence-design.md`，记录员实测 539 行）

- **性质与口径（头部 `:3-10`）**：设计文档，零 Gradle/零设备/零源码改动；服务 T2/T3/T4，不是"已实现"声明；`已落`/`未落`/`建议` 三分。
- **八条结论 C1–C8（§0 `:14-26`）**：① 四表纯非破坏 v69→70，只 CREATE、旧行零改动；② `programId = visualId` 由内容派生（sourceJs + initial state + basis + api/sdk + role 的 SHA-256），program 行永不 UPDATE；③ `STAGING→LIVE` 单向，首张可见图与 message/link 同一事务，link 用 `INSERT…SELECT…WHERE status='LIVE'` 做 SQL 级结构门，指针 CAS 以命中行数判赢；④ `canonical_source_asset` 引用站点五类→六类（新增 `tutor_visual_snapshot.preview_asset_id`），单源常量一处改、清理与备份两侧同变；⑤ 备份机器清单 86 表/78 裁剪项/20 软引用 → **90/82/21**，`CLOSURE_VERSION` 2→3；⑥ 冷恢复结构性不执行 JS（恢复 DTO 不含 `sourceJs`、恢复包不依赖引擎、设备 0 spawn 观测）；⑦ 三档兼容（再执行/重放/只读），未知版本一律只读；⑧ 本批不需要请求 schema 19。
- **现状闭包（§1）**：DB 69→迁移形态/DDL 必须与导出 JSON 逐字一致/`room3-testing` 不在 classpath（`§1.1`）；规范资产内容寻址与五类引用站点（`§1.2`，`CanonicalAssetReferenceSites.kt:28-47`）；备份机器清单硬 `require` 门（`§1.3`，`SmbkSelectionPlan.kt:510-528`、`:600-613`）；CAS 以命中行数判赢、消息+link 同写法（`§1.4`）；`Rendered` / `VisualExecutionRequest` / parser 两入口 / `FigureArtifactRef.JsSnapshot` 已有形状（`§1.5`）。
- **四表设计（§2）**：不变量 INV-1…INV-6；`tutor_visual_program`（`:116`）、`tutor_visual_snapshot`（`:140`）、`tutor_message_visual`（`:181`）、`problem_revision_visual`（`:200`）；外键/删除语义全图（`:216`）；与既有图/资产表逐项裁定（`:232`）；备份机器清单归位（`:242`）；**v69→70 DDL 草案 15 条 execSQL**（`:258-289`，4 表 + 11 索引）。
- **D-02（§3）**：`programId` 派生式 `visual-program-` + 六段长度前缀 SHA-256（`:300-318`，role 进哈希为建议、备选被否决）；状态机与"没有 LIVE→*"（`:320-342`）；S0–S5 事务边界与回滚（`:344-357`，S4 必须一条事务一次提交）；link 的 SQL 门（`:359-374`）；指针 CAS（`:376-390`）；六类仪器化用例矩阵（`:392-401`）。
- **D-05（§4）**：冷恢复三层保证（类型层/依赖层/观测层，`:407-417`）；三档兼容与字体附加门（`:419-431`）；判定序（`:433-451`）；checkpoint 与配额（`:453-465`）。
- **牵动面与边界（§5–§8）**：GitHub 只借鉴不引依赖；`§6` 改一处先闭合影响面；`§7` 未验证项；`§8` 来源。
- **未验证（§7，如实）**：设计中的所有设备/真机行为未实测；`70.json` 的 identityHash 只能由 KSP 生成，设计不伪造。

### 2.2 R2 PDF 写盘可靠性（`docs/research/2026-10-29-pdf-write-safety.md`，记录员实测 405 行）

- **机制核实（§1–§2）**：API 34 的 `PdfDocument.writeTo` 在 native 适配器 `SkJavaOutputStream::write` 处 `ExceptionDescribe() → ExceptionClear() → return false`，Java 侧只看到正常返回；调用链每一跳都不把失败送回 Java（`§1` 树状图）；API 35/36 同构（`§2.5`）；`flush/close` 在这条路径上根本不出现（`§2.4`）。
- **检测方案（§3）**：A 跟踪流（现状）；B 字节计数 + 尾标 `%%EOF` 校验；C 临时文件 + `fd.sync` + 同目录 `renameTo`；D 写后 `PdfRenderer` 回读页数；矩阵与"最小无冗余组合"= A+B（流目标）/ A+B+C+D（文件目标）（`§3.6`）。
- **GitHub 参考（§4）**：androidx `AtomicFile.finishWrite` **吞** sync/close/rename 失败（会把 fail-open 装回去）；okio/commons-io 的原子移动依赖 `java.nio.file.Files`（API 26+，与 minSdk 23 冲突）→ **不引外部库**。
- **最小可信判据（§5）**：D1 无首失败 ∧ D2 字节>0 且尾窗含 `%%EOF` ∧ D3（文件目标）`fd.sync` + `renameTo` 返回 true + `PdfRenderer` 页数相符；D0（`writeTo` 不抛）单独不成立。
- **负向用例（§6）**：ENOSPC、静默短写、页数不符、rename 失败、流中途失败、只读目标等。
- **外溢登记（§2.6，报而不改）**：KD-79 机制在 `core:export` 有两处生产面，其中 `MistakePdfDelivery.writeSubset` 无缓冲写法当前即 fail-open（属热区，未修）。
- **未验证（§8）**：设备侧 ENOSPC/静默短写/页数不符/rename 失败未实测；`developer.android.com` 两轮 `WebFetch` 超时，全部以源码 javadoc 为准。

### 2.3 研究复核结论

- R1 的 C1–C8 在 T2/T3/T4 落码中逐条对应：四表 + 迁移（T2）、身份/事务/CAS/link 门（T3）、恢复 DTO/判定序/保留策略（T4）；C5 的 90/82/21/CLOSURE 3 已在现树 `SmbkSelectionPlan.kt:40/43/46/538/546/547/554` 实测。
- R2 的 D1–D3 在 T5 落码中逐条对应：`PdfWriteIntegrity.kt` 的跟踪流/遥测/文件回读，`VisualDrawOpsPdfPainter.write` 接 D1∧D2、`writeFile` 接 D1∧D2∧D3。
- 两份研究都明确"设计通过 ≠ 设备通过"；设备证据见 §4。

## 3. 十实施轨 + 集成交接

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 轨自述 / 无证据=UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（672 行）；设备执行由设备轨完成** | 判据与现树计数一致；设备红例归因见 §4 |
> | T2 schema 70 / D-01 | 部分 | **落码成立；设备矩阵 15/15、69→70 1/2、提交 8/8；备份 8 红** | 70.json 由设备构建生成但未跟踪；备份同步在设备轨后才落树、未复跑 |
> | T3 CAS / D-02 | 部分 | **落码成立；设备 8/8 + 3/3 绿；生产装配点缺失** | 设备跑的是 03:47 checkpoint 接线前版本 |
> | T4 冷恢复 / D-03 | 部分 | **落码成立；JVM 25 例轨自述；设备 0 用例** | 冷恢复设备面 UNVERIFIED |
> | T5 PDF 写安全 | 完成 | **落码 + 设备 16/16 绿；D3 四缺口 UNVERIFIED** | 计划任务 6 的"磁盘满模拟"未做 |
> | T6 竞态与解析 | 完成 | **落码成立；执行证据缺失** | JVM 未跑，先红/后绿均为路径推演 |
> | T7 环境缺陷 | 完成 | **轨自述完成；仓内无日志，关键结论未复核** | KD-73/KD-77 仍 open |
> | AUD-08 生产者半边 | 完成 | **门值 FAIL 可复核；新增测试未跑** | app-split 无溯源块 → 逐页来源 UNVERIFIED |
> | T12 文档回改 | 完成 | **完成（7 处回改 + 本记录 = 8 路径）；部分计数已被后续落码再次超越** | PDF 13→16 已在 known-defects 更正 |
> | D 条件轨 | 完成 | **完成（分支 D 登记，零改动）** | 九项仍 pending |
> | 集成交接 | 完成 | **发现 3 项（现树均已修；均未复跑）** | 见 §3.11 |
> | 设备轨 | 十步完毕 | **7 绿 / 2 红 / 1 部分红** | 见 §4 |
> | 证据轨 | 完成 | **11 份 `.md` / 66,871 B（du 88K）** | 见 §4.3 |

### 3.1 T1 设备判据 —— **文档落盘；判据与现树一致**

- 文件：`docs/research/2026-10-29-batch-30-device-criteria.md`（**记录员实测 672 行**）。内容：现树计数与证据时点差（§1.1）、现盘 APK 旧包实测（§1.2）、快照期间并发改动登记（§1.3）、五套件逐例判据（§2，painter 24 / 隔离 13 / flags 2 / PDF 16 / parity 11）、KD-78 ④ 九项（§3）、KD-79 关闭判据（§4）、串行命令链与 dex 符号门（§5）、归因五步四类与批次 30 陷阱（§7）。
- **记录员核对**：现树 `grep -c '@Test'` = painter **24**、隔离 **13**、flags **2**、PDF **16**、parity **11**，与判据 §1.1 一致（§10）。
- **判据漂移（设备轨登记，不改判据）**：§2.5-2 写"`PARITY_GATE` 有且仅有一轮"，实测 **11**（`@Before` 每例打印，`VisualDrawOpsParityInstrumentedTest.kt:79-86`）；不构成门失效。
- 判据文档把 KD-79 关闭条件标为 `docs/known-defects.md:2921`，设备轨实测该行是 Impact，关闭条件在 **`:2925`**（行号漂移）。

### 3.2 T2 schema 70 / D-01 四表 —— **落码成立；迁移矩阵绿，69→70 测试断言已修未复跑**

- **装配（实读）**：`STUDY_DATABASE_VERSION = 70`（`StudyDatabase.kt:139`）；四实体在 `@Database entities` 末尾（`:237-240`）；`tutorVisualDao()` 注册（`:319`）；`VISUAL_SNAPSHOT_MIGRATION_69_70` 唯一注册点；迁移文件 188 行、DDL 15 条（`VisualSnapshotMigration_69_70.kt`，唯一索引 `CREATE UNIQUE INDEX IF NOT EXISTS …(program_id, state_sha256, draw_commands_sha256)` `:112-115`）。
- **第六引用站点（实读）**：`CanonicalAssetReferenceSites.kt:14` 注释第 6 条 + `:52-53` `tutor_visual_snapshot.preview_asset_id`；`BackupSnapshotTrimmer.kt:1324-1338` 注释"批次 30/D-01 起由五类扩到六类"、两处 `UNION SELECT preview_asset_id FROM tutor_visual_snapshot`（`:1317-1318`、`:1332-1333`）；单源钉测试 `CanonicalAssetReferenceSingleSourceTest.kt` 同步。
- **备份机器清单（实读）**：`SmbkSelectionPlan.kt:40` `SCHEMA_VERSION = 70`、`:43` identityHash `11a8a701bae767a8a3c5bc810e4743a4`、`:46` `CLOSURE_VERSION = 3`、`:538` `allTables.size == 90`、`:546` SPLIT 7、`:547` trimOrder 82、`:554` softReferences 21；裁剪器版本门 `BackupSnapshotTrimmer.kt:267-271` 对 `SCHEMA_VERSION` 比较。**记录员只读复算**：`70.json` version=70 / identity=`11a8…` / 90 entities（§10）。
- **设备结果（E-07）**：`FullMigrationMatrixInstrumentedTest` **`OK (15 tests)`**（循环 1..70，`StudyDatabase.kt:139`）；`TutorVisualSnapshotMigration_69_70InstrumentedTest` **`Tests run: 2, Failures: 1`**；`TutorVisualCommitInstrumentedTest` **`OK (8 tests)`**；`TutorVisualCommitRepositoryInstrumentedTest` **`OK (3 tests)`**（含 `diskFullRejectsBeforeAnyVisualRowIsWritten`）。
- **69→70 红例归因 = A（测试断言）**：旧断言用 `substringBefore(" IF NOT EXISTS")` 与裸前缀 `CREATE UNIQUE INDEX` 比，而 `sqlite_master` 返回的 DDL 已无 `IF NOT EXISTS`；`but was:` 明示 UNIQUE 三列键实际存在且正确。**现树已修**（`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:205-228`：`assertNotNull` + `startsWith("CREATE UNIQUE INDEX")` + `contains("(`program_id`, `state_sha256`, `draw_commands_sha256`)")`），mtime 03:43，**未复跑** → 设备绿轮 UNVERIFIED。
- **未跟踪产物**：`70.json` 378,885 B 未被 git 跟踪；`70.json.NOT_EXPORTED.md` 仍写"尚未导出"（§5 #9）。
- **JVM schema 契约 8 例**（`TutorVisualSchemaContractTest.kt`）未跑（轨禁 Gradle）；设备迁移/DAO 契约由 E-07 覆盖。

### 3.3 T3 CAS / D-02 身份与不可变 —— **落码成立；设备 8/8 + 3/3 绿，生产装配点缺失**

- **身份派生（实读）**：`TutorVisualIdentity.kt:46` `deriveProgramId`、`:71` `deriveSnapshotId`、`:89` 长度前缀、`:113` `SHA-256`；写库前 `RoomStudyDatabase.kt:2122` 重新派生并 `require` 相等，调用方无法声明与内容脱钩的 `programId`；同 id 不同非哈希载荷由 `TutorVisualCommit.kt` 逐列比对后抛 `ImmutablePayloadConflictException`。
- **staging→LIVE 与同事务（实读）**：`RoomStudyDatabase.kt:1970` `commitTutorTurnWithVisual` 在 `database.withWriteTransaction`（`:1972`）内依次 ensureProgram（`:1974`）→ ensureSnapshot（`:1975`）→ `appendAssistantMessage`（`:1978`）→ promote/link；DAO `insertMessageVisualIfLive` 是 `INSERT…SELECT…WHERE snapshot_id=? AND status=?`（`TutorVisualDao.kt:278-303`）；`repointMessageVisual` 以 `revision` + LIVE + **同 program** 为 CAS 条件（`:328-350`），命中行数判赢；`pinRevisionVisual` 同样只认 LIVE 且同 program（`:409-470`）。
- **回滚/取消/存储满**：S4 单一事务；`deleteStaleStagingSnapshots` 只删 STAGING + 过期 + 未被 link/题面引用（`:184-205`）；`TutorVisualCommitRepository.requireS0` 在写库前做 64 KiB 源码 / 16 KiB state / 256 KiB 事务预算 + 注入式磁盘检查，`SQLiteFullException` 折 `TutorVisualCommitRejectedException`；`deriveSnapshotId` 使同内容重复提交落到同一行。
- **设备结果（E-07 §2.3）**：`TutorVisualCommitInstrumentedTest` 8/8、`TutorVisualCommitRepositoryInstrumentedTest` 3/3（含磁盘满拒提交）绿。
- **边界**：**生产装配点仍不存在**——本批只到 port 层（`StudyDatabasePort : TutorVisualPort`），没有 coordinator/消息写入路径调用 `commitTutorTurnWithVisual`；设备跑的 APK 也早于 03:47 的 checkpoint 接线。
- **登记的高危/保守分支**：`learnerId` 不进哈希（同 id 不同 learner 以冲突暴露）；`requestSnapshot` 2 KiB 有界仍由调用方负责；备份侧（preview 六站点 / FULL pin 单源 / trimOrder 90 表清单）在现树均已落改动（`SmbkSelectionPlan.kt`、`BackupSnapshotTrimmer.kt:1317-1338`、单源钉测试），与 T3 summary 的"本轨未碰"表述不一致（见 §5 #12）；记录员按现树文件归属，不按 summary 文字。

### 3.4 T4 冷恢复 / D-03 半场 —— **落码成立；设备 0 用例，JVM 25 例未复核**

- **冷恢复（实读）**：`VisualSnapshotRestoreRecord` 结构上不含 `sourceJs`/运行时/编译器/API/SDK 字段（`VisualSnapshotRestore.kt:33-60`）；判定序 = 非 LIVE→只读、scene/render schema 未知→只读（先于解析）、renderer 版本不符→只读、字体清单未验证→只读、全过后用真实 `VisualDrawOpsParser.parse`（`:193-240`）；`VERIFIED_FONT_MANIFESTS = emptySet()`（`:125`）→ 默认策略恒走只读，**不承诺像素一致**（保守分支）。
- **D-03（实读）**：`nextCheckpointKind`（首个 INITIAL、之后 LATEST，`:123`）、`plan()`（demote/evict/每 program 4 MiB/全局 32 MiB/磁盘可用字节边界/有码 Rejected，`:137-199`）；`visualCheckpointStoredBytes` = state+scene+draw 的 UTF-8 字节和。
- **接线（现树，03:47）**：`TutorVisualCommitRepository` 的 `stageSnapshot`（`:191`）与 `commitAssistantTurnWithVisual`（`:233`）都调用 `planCheckpoint`，把 `incomingKind`/`demoteToIntermediate`/`evictUnpinnedSnapshotIds` 传入数据库命令；`RoomStudyDatabase.kt:2027-2040` 调 `demoteLatestCheckpoints`/`evictUnpinnedCheckpoints`；DAO 侧 `demoteLatestCheckpoints`（`:136-154`）只把 LATEST 置 NULL，`evictUnpinnedCheckpoints`（`:158-181`）SQL 重查 kind ∈ {NULL, LATEST} 且无 link/题面引用。**"当前未接线"的早期复核结论已不适用于现树**（§5 #11）。
- **测试**：JVM `VisualSnapshotRestoreTest` 12 例 + `VisualCheckpointRetentionTest` 13 例（轨自述离线跑绿，并修正一处"中文 3 字 = 9 字节"的错误期望）；**仓内无命令/日志/结果文件，记录员未复跑**。
- **设备面**：`grep -rn "VisualSnapshotRestore"` 在三个 androidTest 源集 **0 命中**（E-07 §2.4）→ **冷恢复设备用例不存在 → UNVERIFIED**；不得以 JVM 绿替代。

### 3.5 T5 PDF 写安全 / KD-79 —— **落码成立；设备 16/16 绿；D3 四缺口 UNVERIFIED**

- **纯层（实读）**：`PdfWriteIntegrity.kt` 零 android 导入；D1 `PdfWriteFailureTrackingStream.firstFailure`（`:114-150`，捕获面放宽到 `Throwable`，首失败记录后重抛）；D2 遥测（`:51-100`，接受字节 > 0、1024 B 尾窗含 `%%EOF`）；D3 文件面 `pdfFileFailureReason`（长度 == 接受字节、尾窗含 `%%EOF`，`:181-199`）。
- **接入（实读）**：`VisualDrawOpsPdfPainter.write` 走 `writeTracked` 并在 `writeTo` 正常返回后按 D1∧D2 折 `PAINTER_PDF_IO`（`:76-85`、`:186-225`）；`writeFile`（`:108-179`）走同目录临时件 → `flush`+`fd.sync` → 长度/尾标回读 → `PdfRenderer` 页数回读 == 1 → `renameTo` 查 bool。
- **不复用 core:export 的理由（实读 KDoc `:99-103`）**：`MistakePdfExporter.prepare/validatedPageCount/publish` 与 `PreparedPdfStore` 均 private，`core:data` 不依赖 `core:export`，且 core/export 是热区；按同一序列在本包复制并注明。
- **负向**：设备新增 `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（首块后抛、保留首失败文本、失败前已接受字节>0）、`pdfFileWriteRoundTripsThroughReadback`、`pdfReadOnlyTargetFailsClosedBeforeAnyWrite`；JVM `PdfWriteIntegrityTest` 16 例覆盖首失败/Error/ENOSPC 文本/零字节/尾标缺失与尾窗回卷/跨块尾标/文件短写/缺文件/零接受字节。
- **设备结果（E-04）**：`OK (16 tests)`；`pdfWriteFailureIsCoded` 绿；logcat **230** 条 native `java.io.IOException: simulated write failure`。**D3 的 ENOSPC/静默短写（设备面）/页数回读不符/`renameTo` 失败四缺口无用例 → UNVERIFIED**；计划任务 6 的"磁盘满模拟"未做。
- **既有 13 例的夹具与断言**：T5 自述逐字未改、diff 只有新增；记录员未复跑，无法独立证明"先红后绿"。

### 3.6 T6 竞态与解析严格度 —— **落码成立；零执行证据**

- **KD-71 竞态**：修复（`stateLock` 单临界区）在批次 22 已在树；本批补**离设备确定性红测缝** `beforeRegistrationHookForTest`（`QuickJsVisualExecutionEngine.kt:196`，生产恒 null，调用点 `:234`）与 JVM 用例（`QuickJsVisualExecutionEngineJvmTest.kt` 3 例，+58 行）。
- **serialize 同门**：`serialize` 发射后回喂同一 `parse` 并比对解析回来的文档（`VisualDrawOpsParser.kt:707-733`），返回 sealed `VisualDrawOpsSerialization`（`VisualDrawOps.kt:189-191`），失败折 `DRAWOPS_ROUND_TRIP`；非有限数由 `require(value.isFinite())`（`:887`）折 `DRAWOPS_BOUNDS`。
- **路径元数门**：`expectedEntries`（M/L=3、C=7、Z=1，`:612-620`），多/缺分量一律 `DRAWOPS_PATH`。
- **重复键**：解码级扫描 `VisualDrawOpsDuplicateKeys.kt`（`\uXXXX` 归一、字符串值内不误报），`parse` 入口调用（`:106-112`）；深文档 >128 层返回 null 放行（已知残留）；重复键按契约 §4:90 拒 `DRAWOPS_DUP_KEY`。
- **文件大小门**：`VisualDrawOpsParser.kt` 969 行（<1000 硬线），扫描器拆出 229 行新文件。
- **未执行**：未跑 Gradle/JVM/设备；临时 `javac/java` 探针被 hook 拦截。`VisualDrawOpsParserTest` 现树 **26** 例、`QuickJsVisualExecutionEngineJvmTest` **3** 例，均无运行记录。先红/后绿是代码路径推演，**不是执行证据**。

### 3.7 T7 环境缺陷 —— **轨自述完成；仓内无日志，关键结论未复核**

- **KD-73（stdlib 全解析图）**：轨自述以 14 个 lockfile 为静态解析图（340 坐标）、329 落盘构件（103,041 class）、138,225 条 `kotlin/*` 引用在 2.3.21 全部可解析、仅 2.4.10 可解析 = 0；唯一 mv=[2,4,0] 的 quickjs 88 条去重引用全部可解析；未改构建文件。**记录员只复核到"14 个 `*.lockfile` 在盘"这一前提；10.3 万 class 扫描无脚本/日志留仓 → 未复核。**
- **KD-77（API 23）**：轨自述已装 `platforms;android-23` 与三套镜像、新建 3 个 AVD；`google_apis/x86_64` guest 不启动、`default/x86` 因 APK 仅 arm64+x86_64 装不上、`default/x86_64` 可启动；在旧 APK（sha `05a5b8bd…`）上冷启动 2.857 s、FATAL/ANR/NoSuchMethod/NoClassDef/UnsatisfiedLink 均 0，隔离套件 13 例 12 红（`paint flags 1280 vs 指纹 0`）。**记录员实测**：`platforms/android-23`、`system-images/android-23`、`api23_smoke{32,64}.avd` 均在盘；现盘 APK sha 已是 `1429ee0e…`（旧包被覆盖）→ **冒烟日志无仓内落盘，结论未复核**。KD-77 仍 open（需修复后 APK 重跑）。
- **KD-72（锁窗口）**：轨自述 W1–W7 仍不可行、零动作。**记录员实测**：W1 六文件 6/6 不干净（`settings.gradle.kts`/`core/ui`/`feature/tutor`/`core/export`/`.gitignore` M + `core/markdown/build.gradle.kts` ??）；盘上 `*.lockfile` 14、git 可见 13、跟踪 0、staged 0；`benchmark` 无锁；负控未执行。**T7 报"盘上 14"成立**（14 = 13 `gradle.lockfile` + `settings-gradle.lockfile`；只数 `gradle.lockfile` 则为 13）。
- **未复核**：KD-73 的扫描产物、KD-77 的冒烟 logcat 均不在仓内；T7 自述未改任何仓库文件，记录员无法从 737 行脏树中逐条归因他线/本批，故**不把"零文件改动"当独立结论**。

### 3.8 AUD-08 生产者半边 —— **门值 FAIL 可复核；逐页来源 UNVERIFIED**

- **生产者**：`tools/split_golden/export_split_eval.py` 自批次 26 在树且 tracked/clean（基线 §7）；本轨补的是下游断点：`measure_split_accuracy.py` 逐页透传 `appNameSource`/`appJobId`/`appPageIndex`/`appProvenance`（`:335-338`）与文件级 `app.source`，`check_split_gate.py` 新增 `report["appSource"]`（`:242`）并在逐页表渲染来源列（`:256-260`）。
- **门值（只读复算 `build/split-eval/gate-report.json`）**：`gate.state = FAIL`、`count.value = 0.5714285714285714`（阈值 0.9，未达标）、`fragment.value = 0.0`（阈值 0.05，达标）、`automation.enabled = false`；`app.bytes = 1482`、`sha256 = 9b49b0c4…`；`appSource.present = false`（应用导出无 `source` 块）。
- **证据**：`docs/research/evidence/2026-10-29-batch-30/step-aud08-page-ruling.md`（3,357 B，生成时刻 2026-10-28T17:11:02Z，**跨轨文件**，非设备轨 7 绿 + 2 红口径）；该文件含 3 处仓库绝对路径（盘符开头），按 R4 应由该轨改 `<repo>`，设备轨只登记不改。
- **未验证**：新增 5 例（导出溯源→measure、无溯源不猜、导出被 gate 直接消费、证据来源列、4C 历史导出锁门值）**未跑**；`tools/tests/test_split_gate.py` 现树 30 个 `def test_`，无运行记录；历史 `app-split.json` 无 source 块 → 逐页来源标 UNVERIFIED。

### 3.9 T12 文档回改 —— **完成（7 处回改 + 本记录文件 = 8 个路径 M/??）；部分计数已被后续落码再次超越**

- 改动：`docs/known-defects.md`（KD-78 T5 标题"余 ③"回改、KD-78/KD-79/KD-71 三块 Progress）、`docs/current/visual-generation-contract.md`（PDF painter 104→154、PureTest 10→13、parity 10→11）、`2026-10-26-batch-24-4e2-admission-checklist.md`（hitRegions 作废注记、S-04/05/06 刷新、附录 G）、`2026-10-28-pdf-replay-differences.md`（writeTo 更正注记）、`2026-10-28-screen-pdf-parity-gate.md`（19/10→24/13）、`2026-10-28-batch-29-device-criteria.md`（PureTest 13）、`evidence/2026-10-28-batch-29/step06-signatures.md`（13 = 12 warm-up + 1 `the host unbound`；`isolated not needed` 另有 2 行）。
- **记录员复核**：上述文件均 `M`（`git status`）；T12 写作时点（约 01:11）设备证据不存在，文首与附录已有复核轮更正；KD-78/79 的计数现为 24/11/16/13/2/13（PDF 由 13→16 已在 known-defects 更正）。
- **残留**：`docs/current/visual-generation-contract.md` 的"仪器化 13 例"（`:123`）在 T12 时点未回改（已知；本记录不代改文档正文）。

### 3.10 D 条件轨 —— **完成（分支 D 登记，零改动）**

- 开工复核热区仍脏（§1.3）；按批次 24 R4 顺序 G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2/阶段 6→5c 逐项复核，九项全部仍 pending；未重复建 KD/条目/文件（台账 79 条、尾条 KD-79）。零 Gradle、零 git 写、零文件落盘。
- 锚点漂移（相对批次 28）：MASTER-LINE 阶段行 +2、顺序刚性 `:90`、AUD-08 门行 `:151`；KD-68 `:2652`、R3 §9.2 `:213`、决策台账 6B-3 `:3012/:3025/:3036` 未漂。

### 3.11 集成交接核验 —— **装配成立；3 项发现均已在现树修复（均未复跑）**

- **装配链（实读）**：`StudyDatabasePort` 直接 `: TutorVisualPort`（`StudyDatabasePort.kt:37/122`）；`RoomStudyDatabase` 覆盖 stage/commit/repoint/reconcile/读与计数（`:1953/:1970/:2031/:2042/:2054-2075`）；`TutorVisualCommitRepository` 以 `TutorVisualPort` 构造并接 `planCheckpoint`；`VisualSnapshotRestore`/`VisualCheckpointRetention` 可编译接入；`Painter.write`/`writeFile` 接 `PdfWriteIntegrity`；`parse` 入口接重复键扫描；`VisualDrawOpsPainterRendered` 接 parse→draw。
- **零生产装配改动（成立）**：新类型目前只有测试引用；coordinator/UI 未接；A-01/A-13 原样。
- **发现 1（已修）**：schema 70 与备份计划 69 脱节 → 设备备份 8 红。现树 `SmbkSelectionPlan.kt:40` = 70、`BackupSnapshotTrimmer.kt:267-271` 对齐、`BackupRestoreInstrumentedTest.kt:488/585-592` 期望改为 `SmbkSelectionPlan.SCHEMA_VERSION`（mtime 03:40–03:42）；**34 例未复跑 → 是否转绿 UNVERIFIED**。
- **发现 2（已修）**：69→70 迁移测试断言用裸前缀比 `sqlite_master`；现树改为 `startsWith` + 三列 `contains`（mtime 03:43）；**未复跑**。
- **发现 3（已修）**：早期复核指"唯一键与 snapshotId 只含 (program, state, draw)，但复用前逐列比 `rendererVersion`/`fontManifestVersion`"→ painter 升级后同内容再提交会冲突。现树 `sameSnapshotPayload` **不再比较** renderer/font 版本（`TutorVisualCommit.kt:339-361`），KDoc 明写"身份 = 唯一键 = 内容三列；版本差异由恢复侧的重放/像素门按既有行上的版本判定"（`:336-338`）→ 同内容复用既有行、版本差异交恢复侧只读/预览。**口径已统一**；无设备复跑。

## 4. 设备与证据轨

> 环境：`emulator-5562`（`test_device`，API 34，x86_64，PAGE_SIZE 4096，fingerprint `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`）；`emulator-5566`/`-5584` 他线未触碰；零 `connectedAndroidTest`、零 `adb root`、零 git 写；Gradle 仅四条 `assemble*`（`:app:assembleStrictOfflineDebug`、`:app:assembleStrictOfflineDebugAndroidTest`、`:core:data:assembleDebugAndroidTest`、`:core:database:assembleDebugAndroidTest`）。

### 4.1 产物身份（E-00）

| 产物 | 字节 | sha256（宿主=设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,509,308 | `1429ee0e41a20c269825172bc16cf5cf006e1525ff413748f2219fa946b04ad3` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197,750,758 | `cb88851b828115f280061eb1b256cfa2f04111047576a8917ab62617ae4b1951` |
| `database-debug-androidTest.apk` | 196,810,446 | `c07669639e269eee7c64bfb2264aa44890cee56e9f7d172a455c3b74fa38d49b` |

- 四包均非批次 29 旧包；dex 符号门两包 `DEX OK`；构建前后 779 个源文件 sha256 快照 `diff` 为空（`SOURCE-UNCHANGED-DURING-BUILD`）。
- **记录员实测**：现盘 `app-strictOffline-debug.apk` sha256 = `1429ee0e…`，与 E-00 一致。

### 4.2 十步结果（原始日志在 `build/device-track-batch30/`，记录员 grep 复核）

| step | 套件 | 用例数 | 结果（记录员 grep 复核） | 判定 |
|---|---|---|---|---|
| 01 | 隔离 | 13 | `OK (13 tests)` / Time 60.277 | **PASS**；spawn↔ready 17↔17 逐 pid；failed=0；kill 四类 5+4+7+1=17 |
| 02 | 跨侧 flags | 2 | `OK (2 tests)` / 0.016 | **PASS**；KD-78 ④ 九项全满足 → ④ 可关闭 |
| 03 | painter | 24 | `OK (24 tests)` / 0.408 | **PASS**（批次 29 的 1283 红未复现） |
| 04 | PDF | 16 | `OK (16 tests)` / 4.776 | **PASS**；KD-79 台账最低口径满足；D3 四缺口 UNVERIFIED |
| 05 | 一致性原型门 | 11 | `Tests run: 11, Failures: 1` / 0.657；复跑同值 | **FAIL**：P07 `F4_GEOMETRY` centroid 0.5181329158118464 > 0.5 |
| 06 | 备份 | 34 | `Tests run: 34, Failures: 8` / 16.543 | **FAIL**：7×裁剪器 schema 69 vs DB 70；1×期望仍写 69 |
| 07 | 迁移矩阵 1→70 | 15 | `OK (15 tests)` / 266.473 | **PASS** |
| 08 | 69→70 迁移 | 2 | `Tests run: 2, Failures: 1` / 4.543 | **FAIL（A：测试断言）** |
| 09 | 视觉提交/CAS | 8 | `OK (8 tests)` / 1.8 | **PASS** |
| 10 | 视觉仓库 | 3 | `OK (3 tests)` / 0.861 | **PASS**（含磁盘满拒提交） |

- **9 类崩溃签名**：11 份 logcat 全部 **0**（`step08-signatures.md` §1）。
- **KD-78 ④ 专项**：三份 logcat `measure paint flags .* do not match the environment fingerprint` = 0；`PARITY_ENV` 含 `replayTextPaintFlags=0`。
- **KD-79 专项**：`grep -c "java.io.IOException: simulated write failure"` = **230**。
- **红例归因（均未放宽断言）**：
  - **parity P07 = A**：KD-78 ④ 的 AA-off 修复使屏掩膜 1434→1224（PDF 1431 不变），init 阈值 0.5 px（R2 §4.2 自述未标定）被触发；批次 29 同设备 P07 为 `maskS=1434 → F2_AA`，本批 `maskS=1224 → F4_GEOMETRY`；复跑同值。处置为零改动、交 R2 门所有者裁定后重跑；**不得把期望改成 0.518（放宽）**。P08 因 P07 `fail()` 中断未判决。
  - **备份 = C×7 + B×1**：7 条 `BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69`（`BackupSnapshotTrimmer.kt:267-271`）；1 条 `schema68ArchiveRestoresAndMigratesTo69` 期望 69/实际 70。**设备时点（02:33）`SmbkSelectionPlan` 仍 69**；现树已 70（03:35），测试期望已改权威常量（03:42），**未复跑**。
  - **69→70 = A**：断言与 `sqlite_master` 存储形态不符；UNIQUE 三列键实际正确；现树已修（03:43），**未复跑**。
- **设备跑过的源 ≠ 现树**：设备 APK 构建于 02:26–02:27（源快照 01:17:45），此后 03:35–03:47 的备份同步、迁移断言、checkpoint 接线都不在设备 APK 内。

### 4.3 证据轨

- 落盘：`docs/research/evidence/2026-10-29-batch-30/` **11 个 `.md`**（记录员 `find` 实测）；文件字节合计 **66,871 B**（`du -sh` 88K，索引按 88 KiB 记）；最大 `00-index.md` 13,184 B。R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触。
- 跨轨文件：`step-aud08-page-ruling.md`（3,357 B，AUD-08 轨产物，不是设备轨 7 绿 + 2 红口径）；含 3 处盘符绝对路径，已登记未改。
- 脱敏：索引 §8 四条 R4 正则各 0 命中；补充扫描手机号 3 命中均为同一长小数（P07 质心），非 PII；盘符路径 3 命中仅在跨轨文件。
- 回链：`00-index.md` §9 已预留本记录 §4 回链；本文件即目标。

## 5. 复核发现与处置

> 口径：本轮逐条回到现树核对；**凡"设备未复跑"一律 UNVERIFIED**。级别分布：**高 ×2、中 ×9、低 ×8**（19 项）+ 记录员追加 4 条。

| # | 位置 | 发现 | 状态 |
|---|---|---|---|
| 1 | `SmbkSelectionPlan.kt:40/43/46/538-554`；`BackupSnapshotTrimmer.kt:267-271`；`BackupRestoreInstrumentedTest.kt:488/585-592` | **【高】schema 70 与备份计划 69 脱节**：设备 34 例 8 红（7×裁剪器版本门 + 1×期望 69）。**现树已同步**：70 / identity `11a8…` / CLOSURE 3 / 90 表 / SPLIT 7 / trim 82 / soft 21；测试期望改为 `SmbkSelectionPlan.SCHEMA_VERSION` | **已修（设备轨后）**；34 例**未复跑** → 绿轮 UNVERIFIED |
| 2 | `TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:205-228` | **【高】69→70 唯一用例断言错**：`substringBefore(" IF NOT EXISTS")` 与裸前缀比；迁移与 UNIQUE 三列键实际正确 | **已修**（03:43：`startsWith` + 三列 `contains`）；**未复跑** |
| 3 | `docs/known-defects.md` KD-78/KD-79 | 设备证据已出数，KD 关闭条件需按证据裁决；此前 Progress 的"未出数"只对 01:11 时点成立 | **本记录裁决**：KD-78 ④ **关闭**（九项全满足）；整条 KD-78 **open**（①② 另核销）；**KD-79 关闭**（79-P1/P2/L1/L2 全满足），D3 四缺口登记残留 |
| 4 | `TutorVisualIdentity.kt:71-80`；`VisualSnapshotMigration_69_70.kt:112-115`；`TutorVisualCommit.kt:336-361`；`RoomStudyDatabase.kt:2201` | **【中】"内容唯一"两套口径矛盾（早期复核）**：唯一键与 snapshotId 只含 (program, state, draw)，但复用前曾逐列比 `rendererVersion`/`fontManifestVersion`；bump renderer 后同内容再提交会冲突 | **已修（现树）**：`sameSnapshotPayload` 不再比较 renderer/font，KDoc 明确"版本差异由恢复侧重放/像素门判定"；无设备复跑 |
| 5 | `VisualCheckpointRetention.kt:20-28/159-166`；`TutorVisualDao.kt:158-181` | **【中】保留策略的行投影不含 status**：`evict` 候选包含"未固定且 kind=LATEST"，可把未引用的 LIVE LATEST 列入淘汰；DAO 侧 SQL 重查 link/题面引用，**被引用 LIVE 不会被删**。与 T3 "LIVE 永不被删除"的表述冲突（语义歧义，非现行为失败） | **残留（低→中）**：实读确认；建议在投影加 status 或改表述 |
| 6 | `VisualSnapshotRestore.kt:125` | **【中】字体清单恒空**：`VERIFIED_FONT_MANIFESTS = emptySet()` → 默认策略恒走只读，重放路径在生产上不可达；不承诺像素一致（保守，但"重放"能力实际未启用） | **登记（保守分支）**：符合契约"不能只存 Typeface 名称"；UI/产品需知悉 |
| 7 | `docs/research/evidence/2026-10-29-batch-30/step07-persistence-migration.md:89-95` | **【中】冷恢复设备面 0 用例**：`grep -rn VisualSnapshotRestore` 三个 androidTest 源集 0 命中；JVM 25 例不在设备轨权限内 | **UNVERIFIED**：不得以 JVM 绿替代 |
| 8 | `step04-pdf.md:52-71` | **【中】D3 四缺口无设备用例**：ENOSPC、静默短写（设备面）、页数回读不符、`renameTo` 失败；计划任务 6 的"磁盘满模拟"未做 | **UNVERIFIED**；KD-79 关闭按台账最低口径，不覆盖 D3 |
| 9 | `70.json.NOT_EXPORTED.md:1-20`；`70.json` | **【中】占位说明与盘上产物矛盾**：说明写"尚未导出/不生成、不占位、不伪造"，同目录 70.json 已存在 378,885 B；两者均未跟踪，若只提交 `.md` 会把错误说明留在仓里 | **未修**：提交前须删占位说明或改写为"已生成" |
| 10 | `T6` 轨 summary；`VisualDrawOpsParserTest.kt`(26)、`QuickJsVisualExecutionEngineJvmTest.kt`(3) | **【中】T6 以"完成"报告四项代码任务，但零 Gradle/JVM/设备执行**；先红/后绿为路径推演 | **落码成立 / 执行 UNVERIFIED**：不得计为已通过 |
| 11 | `TutorVisualCommitRepository.kt:191/233/327-360`；`RoomStudyDatabase.kt:2027-2040` | **【中】早期"checkpoint 未接线"已不成立**：现树 `stageSnapshot`/`commitAssistantTurnWithVisual` 都调 `planCheckpoint`，demote/evict 传入数据库并执行；`checkpointKind` 默认值被 `incomingKind` 覆盖 | **已修（03:47）**；设备 8/8 + 3/3 跑的是接线前版本 → 新路径无执行证据 |
| 12 | T3 summary vs `SmbkSelectionPlan.kt`/`BackupSnapshotTrimmer.kt`/`CanonicalAssetReferenceSites.kt`/`CanonicalAssetReferenceSingleSourceTest.kt` | **【低】轨间登记与实际文件不一致**：T3 声称"未碰 BackupSnapshotTrimmer/CanonicalAssetReferenceSites/单源钉测试"，但四文件均有本批改动（六站点、FULL pin、90/82/21、单源测试同步），T2 的文件清单也列了它们 | **登记**：提交时按"本批 D-01 共同落点"纳入，不再按 T3 自述排除 |
| 13 | `docs/current/visual-generation-contract.md:123` | **【低】契约仍写"仪器化 13 例"**，现树 PDF 16 例 | **未修**：T12 时点残留；记录员不代改正文 |
| 14 | `00-index.md:8` vs 实测 | **【低】证据体量口径差**：索引写 88 KiB（du 口径），文件字节合计 66,871 B；均未触 R4 上限 | **登记**：不影响合规 |
| 15 | `step-aud08-page-ruling.md:21/22/27` | **【低】跨轨文件含盘符绝对路径**，R4 §5.3 要求 `<repo>` | **登记**：设备轨只登记不改他轨文件 |
| 16 | `docs/research/2026-10-28-screen-pdf-parity-gate.md` 阈值；`VisualDrawOpsParityMetrics.kt:185/847-848` | **【低】P07 红是原型门未标定阈值**：质心 0.5 px 是初始值，非权威契约值；期望改成 0.518 属放宽 | **正确处置 = 零改动交裁定**：记录员同意设备轨归因 A |
| 17 | `docs/current/visual-generation-contract.md:123`；`known-defects` 锚点 | **【低】行号/计数漂移**：KD-79 关闭条件 `:2925` 非判据文档写的 `:2921`；PDF 13→16 已在 known-defects 更正 | **部分已修**：本记录按现树引用 |
| 18 | `RoomStudyDatabase.kt:2151-2172`；`TutorVisualCommit.kt:108` | **【低】snapshotId 身份门不对称**：`programId` 写库前重新派生并强校验；`snapshotId` 只 `require(isNotBlank())`，插入后按内容键回读，直接走 port 的调用方可落一条 id 与内容不符的行 | **残留**：实际调用方 `TutorVisualCommitRepository.snapshotIdFor` 由内容派生，但 port 层未强制；无负向用例 |
| 19 | `VisualDrawOpsParser.kt` KDoc；`VisualDrawOpsDuplicateKeys.kt:20-30` | **【低】T6 两处语义边界**：serialize 的 frozen contentIds 取自文档自身（serialize 不能当冻结证明）；重复键扫描对 >128 层文档返回 null 放行 | **登记**：KDoc 自述；无执行证据 |

**记录员追加核对（不在上表）**：

1. **设备数字与现树计数的时点差**：设备 painter 24 / 隔离 13 / flags 2 / PDF 16 / parity 11 / 备份 34 / 迁移 15/2/8/3；现树测试计数同值，但**设备 APK 早于 03:35–03:47 的修复**，任何"设备绿"都不得套到现树的备份/迁移/checkpoint 接线。
2. **KD-79 关闭的边界**：关闭依据 = KD 自身 Reopen/关闭条件（79-P1+P2+L1+L2）；**D3 四缺口仍是 UNVERIFIED**，不得写成已覆盖。
3. **KD-78 ④ 关闭的边界**：九项判据（P1–P4/N1–N2/L1–L3）全满足；**整条 KD-78 仍 open**，①② 生成侧缺口不在本批设备面。
4. **备份 34 例的绿轮不存在**：现树同步只是静态可证，**没有复跑**；`BackupSelection*`/`BackupSnapshotTrimmer*` 等其它备份套件本批未跑。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 2 轮） | **exit=0**（工单给定） | 仓内无本轮 gate 日志；记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | 修复后 JVM |
| 强门（第 1 轮） | **exit=0**（工单给定） | androidTest 编译 + assemble |
| 复核修复轮第 2 轮 | **exit=0**（工单给定） | 记录员未复跑 |
| 设备 隔离 13 | **PASS `OK (13 tests)`** | spawn↔ready 17↔17；failed=0；kill 四类=17；无残留 worker |
| 设备 flags 2 | **PASS `OK (2 tests)`** | KD-78 ④ 九项全满足 |
| 设备 painter 24 | **PASS `OK (24 tests)`** | 批次 29 的 1283 红未复现 |
| 设备 PDF 16 | **PASS `OK (16 tests)`** | 230 条 native IOException |
| 设备 parity 11 | **FAIL `Tests run: 11, Failures: 1`** | P07 A 类；原型门，非产品门 |
| 设备 备份 34 | **FAIL `Tests run: 34, Failures: 8`** | 设备时点 schema 69/70 脱节；现树已同步未复跑 |
| 设备 迁移矩阵 15 | **PASS `OK (15 tests)`** | 1→70 |
| 设备 69→70 | **FAIL `Tests run: 2, Failures: 1`** | A 类测试断言；现树已修未复跑 |
| 设备 提交/CAS 8 + 仓库 3 | **PASS `OK (8)` / `OK (3)`** | 接线前版本 |
| 设备 冷恢复 | **UNVERIFIED** | 无设备用例 |
| 9 类签名 | **11 份 logcat 全 0** | 只证无崩溃，不代表套件全绿 |
| 锁文件生成 | **未执行（分支 B）** | W1 6/6 不干净、跟踪锁 0、盘上 14 |
| 生产 release 签名 / 真实 Provider | **UNVERIFIED** | 本批未跑；JS 不广告 |

**记录员保留**：各门 `exit=0` 无本轮 gate 日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file，且记录员对 raw log 做了 grep 复核，但**未复跑设备**；现树与设备 APK 不同源，不得混用。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面（本批最重）**

1. **备份 34 例绿轮不存在**：现树已把 `SmbkSelectionPlan` 同步到 70、测试期望改权威常量，但设备轨之后未复跑；是否 8 红全转绿 UNVERIFIED。
2. **69→70 迁移绿轮不存在**：断言已修（03:43），未复跑。
3. **checkpoint 接线无执行证据**：D-03 的 `planCheckpoint`/demote/evict 在 03:47 才接进提交路径；设备 8/8 + 3/3 是接线前版本。
4. **冷恢复设备面 0 用例**：类型层/依赖层静态可证，观测层（0 spawn/0 Provider 派发）未测。
5. **D3 四缺口**：ENOSPC、静默短写（设备面）、页数回读不符、`renameTo` 失败无设备用例；计划任务 6 的磁盘满模拟未做。
6. **JVM 面**：T4 25 例、T5 16 例、T6 26+3 例、T2 schema 契约 8 例均无本轮执行证据（轨禁 Gradle；记录员未跑）。
7. **T7 的 KD-73/KD-77 结论无仓内日志**；旧 APK 已被覆盖，冒烟无法复核。

**代码 / 文档残留**

8. **KD-78 整条 open**：④ 已关闭；①② 生成侧缺口另核销。
9. **KD-79 已关闭（台账最低口径）**，D3 残留登记；不得读成"写成功全路径已覆盖"。
10. **KD-71 open**：T7 的确定性红测已落码，但 JVM/设备执行证据未出现。
11. **KD-72 open**：W1–W7 未解除；14 个盘上锁任何分支下都不得 `git add`。
12. **KD-73 open**：全解析图扫描无脚本/日志；未改构建文件。
13. **KD-77 open**：API 23 平台/镜像已可得、旧 APK 冒烟已完成（无日志），修复后 APK 未重跑。
14. **`70.json` 未跟踪 + 占位说明过期**：提交前必须纳入 70.json 并处理 `.NOT_EXPORTED.md`。
15. **LIVE LATEST 可淘汰**（§5 #5）待裁：保留策略的行投影不含 status，`evict` 候选可含"未固定且 kind=LATEST"的 LIVE 行；DAO 侧 SQL 重查 link/题面引用，**被引用 LIVE 不会被删**，只影响"曾经 LIVE、后来失去全部引用"的行。rendererVersion 身份口径（§5 #4）已在现树收敛。
15b. **snapshotId 身份门不对称**（§5 #18）：`programId` 写库前强校验，`snapshotId` 只校验非空；生产调用方由内容派生，但 port 层未强制。**T6 两处语义边界**（§5 #19）：serialize 不能当冻结证明、>128 层重复键扫描放行。
16. **一致性门仍是原型门**：阈值未标定、P07 红、P08 未判决；**不得引用为产品/发版结论**。

**环境 / 口径**

17. **arm64/16 KB、真机、低内存/高负载未覆盖**；本批设备仅 x86_64/API 34/4 KB。
18. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
19. **4E.2 MVA 仍不成立**：四表/冷恢复/PDF 写安全是纵向切片，不等于持久化/双后端/导出/质量门就绪。
20. **热区在飞未触碰**：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、Root/SecondaryScreens、docs/current 其余 M、docs/research 他线未跟踪记录等。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；若引用备份/迁移/checkpoint 结论，先完成 §7 #1–#3；热区、锁文件（14 个 `*.lockfile` 全部 `??`）与 docs/current 其余 M 一律排除。按 ≤6 路径/块：

- **块 1（T2/D-01 schema 70）**：`core/database/src/main/.../StudyDatabase.kt`（改）、`entity/TutorVisualEntities.kt`（新）、`dao/TutorVisualDao.kt`（新）、`VisualSnapshotMigration_69_70.kt`（新）、`core/database/schemas/.../70.json`（新，**未跟踪必须显式 add**）、`core/database/schemas/.../70.json.NOT_EXPORTED.md`（**先删或改写再提交**）
- **块 2（D-01 资产/备份同步）**：`core/database/.../dao/CanonicalAssetReferenceSites.kt`（改）、`core/data/.../backup/SmbkSelectionPlan.kt`（改；**整文件纳入**）、`BackupSnapshotTrimmer.kt`（改；**整文件纳入**）、`SmbkArchiveCodec.kt`（改）、`AndroidBackupRepository.kt`（改）、`core/data/src/test/.../backup/{SmbkSelectionPlanTest,CanonicalAssetReferenceSingleSourceTest,SmbkArchiveCodecTest}.kt`（改；**整文件纳入**）
- **块 3（T3 身份/CAS）**：`core/database/.../TutorVisualIdentity.kt`（新）、`TutorVisualCommit.kt`（新）、`port/TutorVisualPort.kt`（新）、`StudyDatabasePort.kt`（改）、`RoomStudyDatabase.kt`（改；**整文件纳入**）、`core/data/.../visual/TutorVisualCommitRepository.kt`（新）
- **块 4（T4 冷恢复 / D-03）**：`core/data/.../visual/VisualSnapshotRestore.kt`（新）、`VisualCheckpointRetention.kt`（新）、`VisualRendererVersion.kt`（新）
- **块 5（T5 PDF 写安全）**：`core/data/.../visual/PdfWriteIntegrity.kt`（新）、`VisualDrawOpsPdfPainter.kt`（改；**整文件纳入**）、`core/data/src/androidTest/.../VisualDrawOpsPdfPainterInstrumentedTest.kt`（改；**整文件纳入**）
- **块 6（T6 解析/竞态）**：`core/data/.../visual/VisualDrawOpsParser.kt`（改；**整文件纳入**）、`VisualDrawOps.kt`（改）、`VisualDrawOpsDuplicateKeys.kt`（新）、`QuickJsVisualExecutionEngine.kt`（改；**整文件纳入**）
- **块 7（本批测试）**：`core/database/src/test/.../TutorVisualSchemaContractTest.kt`、`TutorVisualIdentityTest.kt`、`core/database/src/androidTest/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`、`TutorVisualCommitInstrumentedTest.kt`、`core/data/src/test/.../visual/{VisualSnapshotRestoreTest,VisualCheckpointRetentionTest,PdfWriteIntegrityTest,QuickJsVisualExecutionEngineJvmTest,VisualDrawOpsParserTest}.kt`、`core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（均新/改，**整文件纳入**）
- **块 8（研究与判据）**：`docs/research/2026-10-29-visual-persistence-design.md`、`2026-10-29-pdf-write-safety.md`、`2026-10-29-batch-30-baseline.md`、`2026-10-29-batch-30-device-criteria.md`、`2026-10-29-batch-30-pdf-write-device-criteria.md`、`2026-10-29-batch-30-t12-doc-consistency.md`（均新）
- **块 9（设备证据 + 本记录）**：`docs/research/evidence/2026-10-29-batch-30/00-index.md`、`step00`–`step04`（前半）、`step05`–`step08`（后半）、`step-aud08-page-ruling.md`（跨轨）、`docs/research/2026-10-29-batch-30-record.md`（均新）
- **块 10（台账/总路线/决策）**：`docs/known-defects.md`（改；**整文件纳入并注明**：KD-78 ④ 关闭、KD-79 关闭 + D3 残留、KD-71/72/73/77 Progress）、`docs/research/algorithm-version-ledger.md`（改；§3.40 schema 70）、`docs/REFACTOR-MASTER-LINE.md`（改；顶部批次 30 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改；批次 30 裁定节）

**明确排除（他线在飞/不安全）**：全部 `?? */gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`、`core/data` 他线 M（mistake/study/knowledge/model 面）与他线 test/androidTest M；core/database 开工即脏的 8 个 M（`LibraryFts*`、`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`TutorConversationDao`、`CleanRedrawTaskPort`，**不整文件纳入**）；app 开工即脏 M；docs/current 其余 M；docs/research 他线未跟踪记录；`build/**`（含 `build/device-track-batch30/` 原始日志）；`tmp/`。

**提交说明**：建议 10 块、每块 ≤6 路径；六处整文件纳入的 M（`SmbkSelectionPlan`、`BackupSnapshotTrimmer`、`SmbkSelectionPlanTest`、`CanonicalAssetReferenceSingleSourceTest`、`SmbkArchiveCodecTest`、`RoomStudyDatabase`、`VisualDrawOpsPdfPainter`、`VisualDrawOpsParser`、`known-defects`）在提交说明中注明"整文件纳入，含本批多轨/他线改动"；`70.json` 必须显式 `git add`；提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1（539 行）/R2（405 行）落盘，结论与落码逐条对应，未验证项如实。
- **实施**：T1 判据 672 行；T2 四表 + 迁移 + 70.json + 备份 90/82/21；T3 身份/CAS/link/SQL 门 + 仓库；T4 冷恢复 + 保留策略（已接线）；T5 D1∧D2∧D3；T6 serialize 回喂/元数门/重复键/竞态红测缝；T7 环境轨自述；AUD-08 门值 FAIL 可复核；T12 七处回改 + 本记录文件。
- **集成**：装配链静态成立；备份同步与迁移断言已在设备轨后修复；rendererVersion 身份口径已在现树收敛（同内容复用、版本差异交恢复侧）。
- **设备**：十步，7 绿 / 2 红 / 1 部分红；两侧红例均归因且未放宽；9 类签名 11 份全 0；隔离 17↔17；PDF native IOException 230。
- **门禁**：单测门第 2 轮 / 修复环复跑 / 强门第 1 轮 / 复核第 2 轮 exit=0（工单给定）；记录员未复跑。
- **本批不声称全绿/全就绪**：备份 34 例与 69→70 的绿轮不存在；冷恢复/D3/JVM 面 UNVERIFIED；KD-71/72/73/77 与整条 KD-78 open；4E.2 MVA 不成立；arm64/API 23 修复后/生产签名/真实 Provider UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # a806525e
git rev-parse --short origin/main                            # a806525e（本地跟踪引用，未 fetch）
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain | wc -l                               # 737
git status --porcelain | grep -c '^ M'                       # 236
git status --porcelain | grep -c '^??'                       # 501
git diff --cached --name-only | wc -l                        # 0

# 热区（脏行数）
for p in feature/tutor feature/library feature/review feature/capture feature/profile \
         core/ui core/markdown core/export core/domain; do
  printf '%-22s %s\n' "$p" "$(git status --porcelain -- $p | wc -l)"
done                                                          # 59/43/12/16/4/35/1/6/5

# 版本与 schema
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # :139 = 70
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | wc -l  # 71
python -c "import json;d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json',encoding='utf-8'))['database'];print(d['version'],d['identityHash'],len(d['entities']))"
# 70 11a8a701bae767a8a3c5bc810e4743a4 90
git ls-files --error-unmatch core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json   # error: pathspec ... did not match（未跟踪）
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION" core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt  # :546 = 18

# 备份同步（现树）
grep -n "SCHEMA_VERSION\|IDENTITY_HASH\|CLOSURE_VERSION\|allTables.size ==\|trimOrder.size ==\|softReferences.size ==\|splitTables.size ==" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/SmbkSelectionPlan.kt
# :40 = 70；:43 = 11a8…；:46 = 3；:538 = 90；:546 = 7；:547 = 82；:554 = 21
sed -n '260,275p' core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupSnapshotTrimmer.kt   # 版本门在 :267-271

# 测试计数（grep -c '@Test'）
# painter 24 / parity 11 / PureTest 13 / PDF 16 / flags 2 / 隔离 13
# T2 schema 8 / 迁移 2 / 身份 5 / 提交 8 / 仓库 3 / restore 12 / retention 13 / pdf 16 / engine 3 / parser 26

# 关键源码锚点（实读）
grep -n "INSERT INTO tutor_message_visual\|snapshot.program_id = tutor_message_visual.program_id" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorVisualDao.kt
grep -n "insertRevisionVisualIfLive\|evictUnpinnedCheckpoints\|demoteLatestCheckpoints" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/TutorVisualDao.kt
grep -n "planCheckpoint\|checkpointKind = checkpointPlan.incomingKind" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepository.kt
grep -n "CREATE UNIQUE INDEX" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/VisualSnapshotMigration_69_70.kt   # :112
sed -n '205,228p' core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt  # 已修断言
sed -n '585,596p' core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupRestoreInstrumentedTest.kt  # 期望改 SmbkSelectionPlan.SCHEMA_VERSION

# 设备 raw log 复核
for f in build/device-track-batch30/step*.log; do echo "== $f"; grep -E "OK \(|Tests run:|Time:" "$f" | tail -3; done
# 13/2/24/16/11-1/34-8/15/2-1/8/3；parity 复跑同 11-1
grep -c "java.io.IOException: simulated write failure" build/device-track-batch30/step04-pdf-logcat.txt   # 230
# 11 份 logcat 9 类签名逐份 0（step08 §1）

# 证据体量
find docs/research/evidence/2026-10-29-batch-30 -name '*.md' | wc -l            # 11
find docs/research/evidence/2026-10-29-batch-30 -name '*.md' -printf '%s\n' | awk '{s+=$1} END{print s}'  # 66871
du -sh docs/research/evidence/2026-10-29-batch-30                                # 88K

# 锁窗口
find . -name "*.lockfile" -not -path "./build/*" | wc -l    # 14（13 gradle.lockfile + settings-gradle.lockfile）
git status --porcelain | grep -c lockfile                   # 13（可见条目）
git ls-files '*gradle.lockfile' | wc -l                     # 0
git status --porcelain -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/export/build.gradle.kts .gitignore core/markdown/build.gradle.kts  # 6/6 脏

# AUD-08（只读复算）
python -c "import json;d=json.load(open('build/split-eval/gate-report.json'));print(d['gate'],d['app']['sha256'],d['appSource'])"
# FAIL 0.5714/0.0；app 1482B 9b49b0c4…；appSource.present=False
sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk   # 1429ee0e…

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 29 记录的结构对应

| 批次 29 节 | 本批对应 | 差异 |
|---|---|---|
| §2 三研究线 | §2 两研究线 | 本批持久化 + PDF 写安全；锁窗口降为条件轨 |
| §3 七实施轨 + 集成 | §3 十实施轨 + 集成 | 多 T4/T5/T6/T7、AUD-08、T12、D 轨 |
| §4 设备与证据 | §4 设备与证据 | 十步 10 套件；7 绿 2 红 1 部分红 |
| §5 复核发现 | §5 发现与处置 | 19 项 + 4 条追加 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
