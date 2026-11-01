# 批次 33 记录（2026-10-29）：结构门修复复跑 + KD-80 收口 + 预算口径统一

> 记录时点：2026-10-29（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` **只读**核对计划、基线、两份研究文档、T1 判据、`docs/research/evidence/2026-10-29-batch-33/` 十份 `.md`、`build/device-track-batch33/` 原始日志与源码锚点后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码。** 本轮执行的是只读命令：`git status/rev-parse/rev-list/diff/ls-files`、`grep`、`wc`、`sha256sum -c`、`python` 解析 JSON、`ls/find`，命令与输出见 §10；另写了本记录、`docs/known-defects.md`、`docs/REFACTOR-MASTER-LINE.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`（见 §9）。
> 计划：`.zcode/plans/plan-2026-10-29-batch-33-structure-gates-kd80.md`（32 行，已完整读）。
> 基线：`docs/research/2026-10-29-batch-33-baseline.md`（384 行）。
> 研究：R1 `docs/research/2026-10-29-checkpoint-budget-accounting.md`（293 行）、R2 `docs/research/2026-10-29-backup-visual-reference-checks.md`（739 行）。
> 判据：`docs/research/2026-10-29-batch-33-device-criteria.md`（**849 行**；工单摘要写 830）。
> 设备证据：`docs/research/evidence/2026-10-29-batch-33/00-index.md` 与 `step00`–`step08`（**10 份 / 1,007 行 / 47,487 B**，记录员 `find -printf` 实测）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门（修复环后）`exit=0`。记录员未复跑任何 Gradle 任务；仓内**没有**批次 33 的 gate 日志目录（`build/main-session-gate-batch32` 是上一批），只有 `core/data/build/test-results/testDebugUnitTest/` 的 JUnit XML 旁证（§6）。
>
> **本记录不声称"批次全绿"**：14 个设备套件在 14:44–15:13 的最终 APK 上全绿（§4.2），但设备构建源快照 `src-sha-before-c4c.txt` 对现树是 **858/862 通过、4 个测试文件不一致**（§4.4）。其中仓库套件设备执行 **11 例**、现树 **12 例**——第 12 例（`stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`）与同轮补强的"plan 淘汰集含在飞行行"断言**从未上设备**。80-C2 设备侧未逐名（PARTIAL）、80-C3 JVM 半边无仓内命令日志（PARTIAL）→ **KD-80 保持 open**。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 9 项**（§1）：结构门红例修复、KD-80 补 80-C4、stage 预算口径统一、`sceneSha256` 内容绑定、digest 负向补事务性断言、一致门盲集诚实化、文档回改、KD 状态裁决、热区条件轨。
- **实际执行面**：两研究线（R1 预算会计 / R2 备份视觉四表）+ T1 判据 + T2–T7 六实施轨 + 集成交接 + 设备轨（`emulator-5562`，串行 14 套件）+ 证据 10 份 + 复核修复轮（设备轨之后补测试/KDoc）+ 本记录与三份台账同步。
- **与计划的三处已知偏差**：
  1. **任务 1 无需再改代码**：结构门修复已在 HEAD 祖先 `9d91e6b0`（2026-10-29 13:05:17）落码，计划 §0 的 `:221` 红因对应 11:14 设备跑的旧源；T2 只做了"核实现树 + 设备复跑"。现树对 HEAD 的 diff 来自复核修复轮改了 KDoc（`git diff --stat` = **+18/−6**，全为 KDoc），不是断言。
  2. **任务 3 的生产代码已统一**：T3 核实现树 stage/commit 两侧已消费同一份 plan，本批**未改生产代码**，只补测试。
  3. **任务 6 没有新增 1 px 实操例**：选择把 N13 降级为"仅覆盖 ≥2 px"，不把 parity 套件从 16 加到 17（否则批次 32 的 `OK (16 tests)` 证据不再覆盖现树）；理由与 N09-P07 已覆盖 1 px 的实测见 §3.5。
- **不做（全部未做）**：core/export 集成与 PDF `/AIGC`（KD-75 热区）；双后端装配；JS 广告（MVA 未过）；真实 Provider（无凭据）；锁文件生成/提交（KD-72）；AIDL 之外的接口变更；热区 UI。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature 五模块、core/ui、core/markdown、core/export、core/domain、`settings.gradle.kts`、`.gitignore`、app Root/SecondaryScreens、docs/current 其余 M、tools/kb、knowledge-production。
2. **schema 不变（仍 71）**：`StudyDatabase`/迁移/schema 目录零改动（§1.3）。
3. **core/database / core/data 开工即脏**：按"整文件纳入并注明"处理。
4. 一致性门仍是**原型门**，不得引用为产品/发版结论；JS 不广告；真实 Provider 无凭据；AIDL 只以 Gradle 任务验证；AAR/stdlib 不变。
5. 设备只 `assemble*` + `adb install -r -t` + `am instrument`，禁 `connectedAndroidTest`；同一落点多个任务合并给一个拥有者。
6. 不许为变绿放宽断言；结构门修复只允许改"读哪一行"。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`6abd807f`；`origin/main` 本地跟踪引用同为 `6abd807f`；`git rev-list --left-right --count origin/main...HEAD` = `0 0`（**未 `git fetch`**，远端实时 hash UNVERIFIED）。
- **工作树**：**710 条 = 223 M + 472 ?? + 15 staged**（`git status --porcelain` 记录时点实测）。
- **DB schema**：`STUDY_DATABASE_VERSION = 71`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`）；导出目录 **71 个 json**（1..71）；`71.json` **已跟踪**（`git ls-files` exit=0）、`version=71` / `identityHash=714c0ecb85ee5edf74f0013740148138` / **90 实体 / 2 视图**（python 实读）；`SmbkSelectionPlan.kt:44/51/54` 为 `71` / `714c…` / `3`；`git status --porcelain -- core/database/schemas` **为空**；无 `*71_72*` 迁移文件。
- **请求 schema**：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（本批零改动）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、feature/review **12**、feature/capture **16**、feature/profile **4**（feature 合计 134）、core/ui **35**、core/markdown **1**、core/export **6**、core/domain **5**、docs/current **7**、app **28**、tools/kb **0**；`settings.gradle.kts`/`.gitignore` 均 M。本批对上述路径零写入。
- **锁文件**：`git ls-files '*lockfile'` = **0**；git 可见未跟踪 **13**；盘上（maxdepth 3）**14**（多出 `core/markdown/gradle.lockfile`）；`benchmark/gradle.lockfile` 不存在。KD-72 窗口仍不满足。

### 1.4 本批增量点名（按现树实读）

- **改（本批）**：`core/database/src/main/kotlin/.../RoomStudyDatabase.kt`（+19：scene digest 第三条 `require` 与 KDoc）、`core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`（+79：scene 负向例 + state/draw 负向补 `countPrograms == 0`）、`core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（+297：stage 负向/commit 正例/第 12 例/补强断言）、`core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`（+18/−6：仅 KDoc）、`core/data/src/androidTest/.../visual/VisualDrawOpsParityInstrumentedTest.kt`（26 行增删，全为注释）、`core/data/src/test/.../visual/VisualCheckpointRetentionTest.kt`（KDoc）、`docs/known-defects.md`、`docs/research/2026-10-29-batch-32-device-criteria.md`。
- **新增（已 staged）**：`core/data/src/androidTest/.../backup/BackupVisualReferenceInstrumentedTest.kt`（**1,181 行 / 5 例**）、`docs/research/2026-10-29-checkpoint-budget-accounting.md`、`docs/research/2026-10-29-backup-visual-reference-checks.md`、`docs/research/2026-10-29-batch-33-baseline.md`、`docs/research/2026-10-29-batch-33-device-criteria.md`。
- **证据目录**：`docs/research/evidence/2026-10-29-batch-33/` 10 份，均已 staged（`A`）。
- **测试计数（记录员 `grep -c '@Test'` 实跑）**：结构门 **5**、备份 **34**、裁剪器 **9**、C4 **5**、迁移矩阵 **15**、69→70 **4**、70→71 **3**、提交 **12**、仓库 **12**、parity **16**、painter **24**、PDF **16**、隔离 **13**、flags **2**；JVM `SmbkSelectionPlanTest` **12**、`VisualCheckpointRetentionTest` **17**、`VisualDrawOpsPainterPureTest` **13**、`KernelWave0SchemaContractTest` **8**、`TutorVisualIdentityTest` **6**、`TutorVisualSchemaContractTest` **9**。
- **零改动**：schema/迁移/`StudyDatabase`、`settings.gradle.kts`、`.gitignore`、AAR/stdlib、请求 schema、`drawSchemaVersion`、`PROTOCOL_VERSION`；生产装配（`TutorVisualCommitRepository` 仍只在测试构造）。

## 2. 两研究线

### 2.1 R1 checkpoint 预算口径会计：stage / commit 的"净值 vs 全量"（`docs/research/2026-10-29-checkpoint-budget-accounting.md`，293 行）

- **性质（`:1-6`）**：只读源码 + `gh api` + 官方文档；零 Gradle/零设备/零 git 写；所有"当前行为"是源码实读，不是执行结果。
- **结论 1：现树两路径已是同一口径**。事务前 plan 按"淘汰后净值"预检，事务内**先执行 demote/evict、再按实测总量重判**。stage 已把 plan 的淘汰集带进事务（`TutorVisualCommitRepository.kt:279`；`RoomStudyDatabase.kt:1963-1971`），commit 同（`:329-330`；`:2056-2071`）。修复来自 `3087c8ae`。
- **结论 2：计划 §0 的"stage 事务按全量"对上一版也不准确**。`3087c8ae^` 的 stage 事务**既没有执行淘汰、也没有预算重判**（`git show` 中 `enforceVisualCheckpointBudget` 零命中）——不是"事务按全量"，而是"stage 没有第二道门"（§0 第 2 条）。
- **结论 3："净值"单独不是安全门**。plan 扣除的 `E` 是乐观集合；事务内 SQL 会再查 pin/kind/status/owner 租约，实际删除集 `E' ⊆ E`。`E' ⊊ E` 时净值不超也可能落成实际超限，所以**事务内"淘汰后再量"是正确性的一环**（§3.1 的反例：`r2` 在别人租约内、`net=4≤B`、`gross=7>B`；SQL 拒删后 `actual=7>B`）。
- **四种组合**（§3）：A（现树）= plan 净值 + 事务实测淘汰后判决，**符合 §8.1**；B（净值 plan + 不先淘汰的全量事务）会误拒；C（全量 plan）根本没尝试淘汰，违反"超限只淘汰中间态"；D（事务不重判）会落成超限，**不安全**。两路径在"预算记账"投影上没有独立口径；真正差异只在是否 demote / 是否提升 LIVE / 回滚里有没有消息与 link，三者都不改变 `b` 或 `E` 的成员资格。
- **方案 A 四条（§4.1）**：A-1 stage/commit 都把 `plan.evictUnpinnedSnapshotIds` 传进事务（现树满足）；A-2 事务内先淘汰后量（满足）；A-3 量的是全量含受保护行（满足）；A-4 plan 只是快速预检（KDoc 已写明）。**stage 不需要 demote**：新行仍是 STAGING，提前摘旧 LATEST 会在放弃 stage 时留下角色空洞；且 demote 对 `b` 零影响。现树 `StageTutorVisualSnapshotCommand` 只有 `evictCheckpointSnapshotIds` 字段（`TutorVisualCommit.kt:185-198`），不是漏接线。
- **§8.1 逐条符合度（§4.3）**：淘汰范围（未固定 ∧ kind∈{NULL,LATEST} ∧ STAGING）符合；LIVE 永不淘汰符合；淘汰后仍超才拒符合；全部固定/磁盘不足则本次不提交符合；活动 owner 预备产物不 GC 符合；首张可见图与消息/link 同事务符合。**待裁定的解释**：受保护字节是否计入预算分母；现行计入，建议契约补一句定谳（本轨不改 docs）。
- **三项必须跟进（§4.4 / §5.4）**：
  1. **`reconcileStaleStaging` 零生产调用点**（记录员 `grep` 复核：仅 `TutorVisualCommitRepository.kt:359-360` 定义 + 两条仪器化测试；main 源无调用者）。后果：被取消的孤儿 STAGING 永久占预算；首个 stage 留下的 `STAGING + INITIAL` 行既不被 evict SQL 删（kind 过滤）也不被备份裁剪删（INITIAL 无条件保留），且让 `nextCheckpointKind` 永远补不出可见 INITIAL（`VisualCheckpointRetention.kt:151-156`；`BackupSnapshotTrimmer.kt:768-777`）。
  2. **plan 的两次读不原子**：先读本 program 汇总、再读全局总量（`TutorVisualCommitRepository.kt` 的 `planCheckpoint` 内两次调用），中间可被并发删除；`VisualCheckpointRetention.kt:183-185` 的 `require(globalStoredBytes >= existingBytes)` 会抛**无界 `IllegalArgumentException`**，把并发窗口变成调用方无法处理的"参数错误"（记录员实读确认该 `require` 仍在）。
  3. **补 stage 的"淘汰后仍超 → 拒绝"负向用例**：现树已有（§3.3），但在设备轨之后才补，UNVERIFIED（§4.4）。
- **GitHub 参考（§6）**：只借形态不引依赖；Kubernetes 的"高/低水位 + 在用保护 + 释放不足显式失败"、Bazel/restic 的"GC 单飞、两段式、崩溃可续"最值得抄；避免 etcd 的"先写后报错"和 Caffeine 的"近似配额"。license/stars/last push 为 `gh api` 实测，评分为主观。

### 2.2 R2 备份/恢复后视觉四表引用校验（KD-80 80-C4）（`docs/research/2026-10-29-backup-visual-reference-checks.md`，739 行）

- **性质（`:1-8`）**：只读代码与文档，零 Gradle/零设备/零 git 写；"应绿/应红"是设计判据，不是执行结果。
- **四表与删除语义（§1.1）**：`tutor_visual_program`（内容派生、无 UPDATE）、`tutor_visual_snapshot`（`status∈{STAGING,LIVE}`、五列唯一键、RESTRICT FK）、`tutor_message_visual`（消息 link，LIVE-only）、`problem_revision_visual`（题面 pin，LIVE-only）。
- **恢复侧已有校验（§1.4）**：Phase 2b `quick_check` + `foreign_key_check`；Phase 2c.1 manifest `rowCounts` 与包内库 90 表逐表对拍；Phase 2c.3 `RestoreSoftReferenceRecheck` **不覆盖视觉表**（`grep tutor_visual` = 0）；Phase 3 `integrity_check` 不含 FK。**真实缺口**：没有用例用已知源数据证明四表行数与引用是期望的那一组；外键之外的语义（复合 program、LIVE-only、身份/摘要同源）无恢复侧复核；裁剪切面未被钉住。
- **不变量集（§2）**：V-R1–R4 行数/集合；V-R5–R11 引用完整性（FK、复合 program、预览资产、软引用 no-new-dangling）；V-R12–R16 状态/角色（LIVE⇔promoted_at、被引用快照必须 LIVE、kind 域、revision≥1）；V-R17–R21 身份/内容同源（program/snapshot 重派生、state/draw/scene 摘要、五列唯一键）。
- **两段式 checker（§3）**：先对包内库跑 checker 并与 Kotlin 从源行计算的期望集合比对（抓裁剪/成包缺陷），再对恢复库跑同一 checker 与包内库逐行比对（抓恢复缺陷）；判定表见 §3.4。通过 SQLite 连接读，不复制主文件字节（WAL 中已提交行只在连接视图里可见，KD-81）。
- **设备侧设计（§5）**：新增独立测试类 5 例，不向既有 34 例基线加例（否则 KD-80 80-C1 的 `OK (34 tests)` 对照关系失效）；旧包用例只追加"四表存在且 0 行"断言。5 例：全选 15 往返、子集 9、子集 13、归档 vs 恢复逐行、第三方重裁包被拒。
- **读码发现的候选缺陷/待裁（§7.2）**：恢复侧不重查 V-R14（被引用快照必须 LIVE）与 V-R8/R9（复合 program 一致性）；`scene_sha256` 在恢复侧无校验（记录员复核：`core/data/src/main/.../backup/` 对 `scene_sha256` **0 命中**）；`checkpoint_kind` 无域校验；子集备份会丢"失去引用的 LIVE 快照"（keep 规则只认引用 ∪ INITIAL）。本批 C4 用例只证明"检测与拒绝面的现状"，不改产品恢复行为。
- **GitHub 参考（§6）**：SQLite 官方 PRAGMA 分层（`integrity_check` 不含 FK，必须另跑 `foreign_key_check`）25/25、androidx Room3 `MigrationTestHelper` 24/25、litestream 23/25、restic 21/25、meshtastic（GPL-3.0，只借模式）16/25。
- **落码对应**：新类 `BackupVisualReferenceInstrumentedTest.kt`（1,181 行 / 5 例）已存在、已 staged；`setUp` 用 `StudyDatabaseFactory.DEFAULT_DATABASE_NAME`（`:70-77`，与恢复路径一致，这是 E-08 红轮 A 类修复）；5 例方法名与判据 §5.3 一致（`:86/:123/:157/:196/:223`）。

### 2.3 研究复核与落码对应

- R1 的 A-1–A-4 在现树逐条成立（§3.3），但第 1/2 项"真实缺陷"（reconcile 接线、plan 原子读）**本批未做**，只登记；第 3 项负向例已补但未上设备。
- R2 的 V-R1–R21 与 5 例逐条对应（E-04 §4 列出检查器覆盖）；V-R20（scene digest）依赖本批任务 4 的 `require`，已落码（`RoomStudyDatabase.kt:2277`）。
- 两份研究都强调"设计/静态 ≠ 设备通过"；设备结论见 §4，且**设备源与现树有 4 个测试文件差**（§4.4）。

## 3. 七实施轨 + 集成交接 + 设备 + 证据

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 轨自述 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（849 行）；T1 行锚已过期** | §3.1；结构门方法行 +4/+11、删除断言 +5 |
> | T2 结构门修复 | 完成 | **修复已在 HEAD `9d91e6b0`；本批零代码改动；设备 5/5** | 现树仅 KDoc diff（+18/−6），非断言 |
> | T3 预算统一 | 部分 | **口径已统一（A-1–A-4）；本批只补测试；设备 11/11，第 12 例未跑** | §4.4 |
> | T4 内容绑定 | 完成 | **scene require 落码；三条 digest 负向补 `countPrograms==0`；设备 12/12** | §3.4 |
> | T5 盲集诚实化 | 完成 | **N13 降级 ≥2 px、代数句改正、设备侧 24 统一；JVM 10 vs 13 遗留** | §3.5 |
> | T6 文档回改 | 完成 | **台账/判据/KDoc 已改；仍有三处残留** | §3.6 |
> | T7 条件轨 | 完成 | **分支 D 登记，零改动；九项仍 pending** | §3.7 |
> | 集成交接 | 完成 | **无需新增接线；schema 零改动；悬空引用已入索引** | §3.8 |
> | 设备轨 | 14 套件绿 | **日志与证据一致；4 个测试文件源漂移；未复跑设备** | §4 |
> | 证据轨 | 完成 | **10 份 `.md` / 47,487 B；索引数字过期** | §4.3 |
> | 复核修复轮 | 完成 | **补第 12 例 + 断言 + KDoc；均未上设备** | §3.9 |

### 3.1 T1 设备判据 —— **文档落盘；行锚局部过期**

- 文件 `docs/research/2026-10-29-batch-33-device-criteria.md`（**849 行**；工单摘要写 830）。内容：结论速览（§0）、现树事实（§1）、结构门判据（§2）、KD-80 四条（§3）、重建重装硬门（§4）、设备串行命令与 dex 门（§5）、红例归因（§6）、未验证项（§7）、来源（§8）。
- **§0 的时点说明处理诚实**：先给 T1 快照、再用 `→ 最终` 标设备轨读数；正文不改写，避免把 T1 的"UNVERIFIED"读成现树事实。
- **§1.4 的 80-C2 静态脚本由 T1 轨自报实跑 PASS**（`RESULT=PASS`、`EXIT=0`，输出见 §1.4）；**记录员未重跑该脚本**，只读了输出。
- **行锚过期（记录员实测）**：§1.1/§2 写 5 个 `@Test` 在 `:59/:134/:169/:235/:280`，现树是 **`:63/:138/:173/:239/:291`**；写 `snap-staging-middle` 删除断言在 `:213-214`，现树是 **`:218-219`**；写 pinned/linked 在 `:218-225`，现树是 **`:223-230`**；读侧 `:228-230`，现树 **`:232-235`**。原因是复核修复轮在第 5 例 KDoc 增加了约 7 行"分辨力边界"说明。字符串 grep 判据（§2.2）不受影响，按行号复核会偏 4–11 行。
- **计数说明**：§1.3 的 14 套件计数与记录员 `grep -c '@Test'` 一致（5/34/9/12/11→现树 12/5/15/4/3/16/24/16/13/2）；§1.3 已注明"仓库 T1 为 11，复核修复轮新增第 12 例，UNVERIFIED"。

### 3.2 T2 结构门修复 —— **修复在 HEAD；本批零代码改动；设备 5/5**

- **修复位置（实读）**：`TutorVisualStructureGatesInstrumentedTest.kt` 的 `evictionDeletesOnlyUnpinnedStagingIntermediates`：候选集 `:202-209`（6 行，含 `snap-staging-middle`、`snap-pinned-staging`、`snap-linked-staging`），`deleted == 1`（`:218`），`assertNull(snap-staging-middle)`（`:219`），`snap-initial`/`snap-latest`/`snap-live-middle` 保留（`:220-222`），pinned（`:223-226`）与 linked（`:227-230`）保留，读侧 pinned 1/1/0（`:232-235`）。`snap-staging-keeper` 种子在 `:192`，**不在**候选列表。
- **修复提交**：`9d91e6b0` 是 HEAD 祖先（基线实跑 `git merge-base --is-ancestor` = true）；据 T2 摘要与基线 §3，其对 HEAD 的唯一相关改动是 `getValue("snap-staging-middle")` → `getValue("snap-staging-keeper")` 并新增 keeper 种子，未删任何断言（记录员未复跑 `git show`，以现树断言完整性与 T2/基线材料为据）。
- **pin 分辨力保留**：`snap-staging-middle`（STAGING + kind=NULL + 同 owner）与 `snap-pinned-staging` 形状相同，唯一差别是题面 pin；结果一删一留；仓储层 `repositoryPlanRetainsAStagingIntermediateOnlyBecauseItIsPinned`（`:291` 起）用两条同形状候选再钉一层。**限制（复核修复轮已写入 KDoc）**：该端到端用例只断言最终库状态，**不能单独**区分 plan 层 `!pinned` 与 SQL 的 `NOT EXISTS`；plan 层由 JVM `VisualCheckpointRetentionTest` 覆盖，SQL 层由 DAO 用例覆盖。这是表述边界的更正，不是断言放宽。
- **设备**：`OK (5 tests)`（`build/device-track-batch33/step02-structure-gates.log`，记录员 grep 复核）；5 个方法名逐一出现；logcat 四类失败签名 0。
- **现树 diff**：`git diff --stat HEAD` = **+18/−6（全为 KDoc）**；T2 摘要"对 HEAD 零 diff"是 T2 时点事实，现树已不成立（复核修复轮与 T6 改了 KDoc）。

### 3.3 T3 预算统一 / 计划任务 3 —— **口径已统一；本批只补测试；第 12 例未上设备**

- **现树口径（实读）**：stage 在 `TutorVisualCommitRepository.kt:253-284` 调 `planCheckpoint`（`:262`），把 `checkpointPlan.evictUnpinnedSnapshotIds` 传入命令（`:279`）；`RoomStudyDatabase.kt:1955-1984` 事务内先 `evictUnpinnedCheckpoints`（`:1963-1971`），再 `enforceVisualCheckpointBudget`（`:1975-1982`）。commit 在 `:298-337` 做同一件事，额外传 demote（`:329`）与 evict（`:330`）；事务内 demote（`:2056-2062`）→ evict（`:2063-2071`）→ 重判（`:2076-2083`）。重判实现 `:2175-2197`（先 per-program 后 global）。
- **stage 不 demote**：`StageTutorVisualSnapshotCommand` 只有 `evictCheckpointSnapshotIds`（`TutorVisualCommit.kt:185-198`），KDoc 写明理由；这是 R1 §4.1 的设计，不是漏接线。
- **plan 语义**：`nextCheckpointKind`（`VisualCheckpointRetention.kt:151-156`）、可淘汰集 `:187-192`、`retained = existing − evictable + incoming`（`:194`）、`globalAfter`（`:209`）。`require(globalStoredBytes >= existingBytes)` 仍在 `:183-185`（无界异常风险，§2.1）。
- **本批测试（实读）**：仓库套件现 **12 例**。T3 新增：`stagingWhenEvictionIsRefusedByAnInFlightLeaseStillRejectsWithACode`（`:507`，plan 放行、事务 SQL 因租约拒删、实测 87>70、有码 `PER_PROGRAM_BUDGET`、本次行回滚、在飞行仍 STAGING、基线 LIVE 不动；并断言 **plan 淘汰集真的含在飞行行**、端口收到 1 条命令，证明拒绝来自事务内重判）；`commitIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`（`:691`，commit 正例）。复核修复轮再加 `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`（`:604`，预算 40 < 净值 58，plan 写库前拒绝、快照零新增、program/行集合不变、无 message/link）。既有 stage 正例 `stagingIntoAnOverBudgetProgramEvictsAnExpiredUnpinnedStagingIntermediate`（`:441`）保留。
- **设备**：`OK (11 tests)`（`step09-visual-repo-final.log`）——**第 12 例与"plan 淘汰集含在飞行行"断言是在设备轨之后补的，未上设备**（`known-defects.md:3032` 自认；§4.4 sha 复核确认现树与设备源不同）。
- **先红后绿**：本轨禁 Gradle/设备，红/绿未执行；可给出变异路径（删 stage 的 `enforceVisualCheckpointBudget` 调用 → 负例"有界拒绝"失败；commit 删 evict 传入或把 enforce 挪到 evict 之前 → 正例"提交成功/LIVE"失败）。

### 3.4 T4 内容绑定 / 计划任务 4–5 —— **scene 第三条 require + 事务性断言；设备 12/12**

- **`sceneSha256` 绑定（实读）**：`ensureTutorVisualSnapshot` 在既有 state（`:2271`）/draw（`:2274`）之后新增 `require(write.sceneSha256 == TutorVisualIdentity.sha256Hex(write.sceneJson))`（`:2277-2279`）；stage（`:1958`）与 commit（`:2000`）共用此函数，故两条写路径同时受门。生产仓储始终按 `sha256Hex(sceneJson)` 计算（`TutorVisualCommitRepository.kt:511`），门只挡直接调 DB 端口的写者；异常 `IllegalArgumentException` 与 state/draw 两轴同形，仓储 `translateFullDisk` 不翻译它（KDoc 已列为可能结果）。
- **负向例（实读）**：`sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`（`TutorVisualCommitInstrumentedTest.kt:235`，第 12 例）；state/draw/scene 三条负向都补了 `countPrograms == 0`（`:176/:224/:270`）——program 行在快照校验前已由 `ensureTutorVisualProgram` 插入（`:1957/:1999`），只有 `countPrograms==0` 才证明整事务回滚。
- **仍不校验（记录员复读，T4 自述一致）**：`tutor_visual_program.source_sha256`/`initial_state_sha256` 未与 `sha256Hex(source_js)`/`sha256Hex(initial_state_json)` 比对（`RoomStudyDatabase.kt:2205-2226` 只派生 programId；`sourceSha256` 的 main 命中在无关的 `:1372`）——建议批次 34 补；`tutor_message_visual.form_digest` 无载荷可绑；`scene_sha256` 不在 `deriveSnapshotId`/唯一键里（schema 71 键为 program+state+draw+renderer+font），"同 state+draw、不同 scene"靠 `sameSnapshotPayload` 判冲突（`TutorVisualCommit.kt:431`），属有意设计；迁移/结构门用例直接以 entity 或 raw SQL 播种的旧行不经此门（既有行不追认）。
- **设备**：提交套件 `OK (12 tests)`（`step08-visual-commit.log`）；logcat 0 异常。

### 3.5 T5 盲集 / 计划任务 6 —— **N13 降级、代数句改正；JVM 10 vs 13 遗留**

- **N13 结论降级（实读）**：`VisualDrawOpsParityInstrumentedTest.kt:79` 写"**仅覆盖 ≥2 px 位移**；不覆盖 1 px 盲集——1 px 盲集由 N09 的 P07 覆盖"。1 px 真盲集由 N09-P07 在真实夹具上覆盖（批次 32 设备实测 P07 `centroid=0.608282132824767`、`bboxDev=2`、`bestShift=(1,0)`、`F4_GEOMETRY`），不另造 1 px 合成用例（避免 16→17 例、使批次 32 的 `OK (16 tests)` 证据失效）。
- **代数句改正（实读）**：`:317-318` 的"`b²=a²+2·vx+1 ⇒ d(+1)=b`"是循环定义，已改为"受基线精度限制的是反解出的 `v_P06` 区间，不是直接测量的 `d(+1)`"（复核修复轮）。
- **计数**：parity 文件仍写"设备 24 例 + **JVM 10 例**"（`:52`），`VisualDrawOpsParityMetrics.kt:15` 同；现树 painter pure = **13**（记录员 grep）。设备侧 19→24 已统一，JVM 侧 10 vs 13 **未裁定/未改**（基线 §6.3 已登记）。
- **diff 性质**：`git diff -U0 HEAD` 的 26 行增删**全部为注释**（记录员过滤非注释行后为空）；`@Test` 仍 16，N10/N11/N12 用例体未动。
- **设备**：parity `OK (16 tests)`、painter `OK (24 tests)`、PDF `OK (16 tests)`（`step10/11/12`）。

### 3.6 T6 文档回改 / 计划任务 7–8 —— **已改；三处残留**

- **反向虚报回改（实读）**：`known-defects.md:2996` 的"未在本批执行"已加时点回链；KD-80 标题已改为批次 33 实际读数，并新增 T6/设备轨/复核修复轮块；KD-71/72/73/77 均有批次 33 T6 块；批次 32 判据 §5.1/§5.2/§5.3/§5.4/§9 已回改或标旧源；三处测试 KDoc 的"未上设备"已按实际读数改写（retention 仍 UNVERIFIED，因为 JVM 未被设备轨执行）。
- **KD 逐条**：KD-71/72/73/77 无新执行证据 → 保持 open（§7）；KD-80 80-C1/C4 在最终 data APK 上核销、C2/C3 PARTIAL → 保持 open（§4.5）。
- **三处残留（记录员实测）**：① `BackupRestoreInstrumentedTest.kt:482` KDoc 仍写"`SmbkSelectionPlan.SCHEMA_VERSION`，现为 70"（常量已是 71），判据 §1.3 已注明但 T6 未改；② parity/metrics 的"JVM 10 例"与现树 13 不符；③ 证据索引 `00-index.md:11` 写"45,424 B / 988 行"，实测 **47,487 B / 1,007 行**（复核修复轮回写后未更新）。均不影响产品行为/断言。

### 3.7 T7 条件轨 —— **分支 D 登记，零改动**

- 开工复核：热区仍脏且与基线同值（feature 134、core/ui 35、app 28 等）；按批次 24 R4 九项序位逐项复核，九项全部仍 pending、载体均在，未新建任何 KD/条目/文件。
- AUD-08 仍门级 FAIL；5b-2/阶段 6/5c 的 3D 硬门仍开；九项无一条具备开工条件。零文件落盘、零 git 写、零 Gradle、零设备。

### 3.8 集成交接 —— **无需新增接线；schema 零改动；悬空引用已入索引**

- **T3 预算口径对 stage/commit 两侧调用点**：现树两侧已消费同一份 plan（§3.3），唯一不对称是 stage 不 demote（R1 §4.1 的设计），不是漏接线；T3 新增两例的期望与代码逐条对齐（29×3=87>70、2×29=58≤70）。
- **T4 新 require 对仓储翻译**：require 在 stage/commit 共用函数内；仓储始终用 `sha256Hex(sceneJson)`，自身永不触发；`translateFullDisk` 不翻译 `IllegalArgumentException`，与 state/draw 两轴一致；直接调 DB 端口的写者收到 `IllegalArgumentException` 且整事务回滚。
- **T5 阈值/用例对判据文档**：T5 只改注释；判据文档 parity/painter 期望仍 16/24，与现树一致；JVM 10 vs 13 是批次 33 基线已登记的待裁口径，非 T5 引入。
- **零 schema 改动**：`git status --porcelain -- core/database/schemas` 为空；`git diff --stat -- core/database/schemas` 为空；`git ls-files core/database/schemas | wc -l` = 71；`StudyDatabase.kt` / `SmbkSelectionPlan.kt` 对 HEAD 零 diff（§10）。
- **悬空引用已解决**：被 tracked KDoc 引用的三份研究文档（checkpoint-budget / backup-visual / batch-33-baseline）与 C4 测试源均已 `git add`（`A`），记录员 `git status` 复核为 staged；判据文档也已 staged。**剩余**：`docs/research/2026-10-29-batch-33-record.md`（本文件）尚未被引用方感知为已存在——设备证据头的 `record-ref` 现在有实际落点（§9）。

### 3.9 设备轨与证据轨（摘要，详见 §4）

- 设备轨串行 14 套件（`emulator-5562` / API 34 / x86_64 / 4096）；四包 `assemble*` EXIT=0、源在构建窗口内稳定（device track 自述）、安装四次 `Success`、宿主与设备 sha 逐字节一致、dex 符号门与 schema 资产门通过（E-01）。
- 14 套件全绿：5 / 34 / 9 / 12 / 11 / 5 / 15 / 4 / 3 / 16 / 24 / 16 / 13 / 2（记录员 grep 原始日志复核）。
- C4 首轮 2 红归因 **A 类**（测试 `setUp` 用自定义库名，而 `doRestore` 固定换 `DEFAULT_DATABASE_NAME`），只改测试夹具后同命令复跑 `OK (5 tests)`；产品源零改动（E-08）。红轮 raw log 被同路径绿轮覆盖，只有控制台转录。
- 证据 10 份、47,487 B、最大 6,910 B（00-index）；R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触；记录员抽查未发现密钥/凭据。
- **复核修复轮（设备轨之后）**：仓库第 10 例补"plan 淘汰集含在飞行行"断言、新增第 12 例"净值也超 → 写库前拒绝"、结构门第 5 例 KDoc 补分辨力边界、三处 KDoc 回填实际读数。**这些改动均未编译/上设备**（§4.4）。

### 3.10 门禁

- 工单给定：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门（修复环后）`exit=0`。记录员**未复跑**，仓内无批次 33 gate 日志目录。
- 旁证：`core/data/build/test-results/testDebugUnitTest/TEST-…SmbkSelectionPlanTest.xml` 与 `…VisualCheckpointRetentionTest.xml`，mtime **2026-10-29 16:17:46**，`tests="12"/"17"`、`skipped=0`、`failures=0`、`errors=0`。属 `build/` 旁证，不是仓内命令日志；命令归属 UNVERIFIED。
- 结论：五条门禁**不能由记录员独立复现**；不得把工单给定写成"记录员验证通过"。

## 4. 设备与证据

> 环境：`emulator-5562`（AVD `test_device`；API 34、x86_64、PAGE_SIZE 4096）；零 `connectedAndroidTest`、零 `adb root`、零 git 写。

### 4.1 产物身份（E-00/E-01）

| 产物 | 字节 | sha256（宿主=设备） |
|---|---:|---|
| `app-strictOffline-debug.apk` | 189,532,816 | `b8b2e9d85b377d5b7d559affba6113c36cc243b497e22006c91d162207e55bed` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk`（最终，含 C4） | 197,770,282 | `36f79b195e6c43d7841a85da3385887eb092518d1a0aa0f7462a619fb06f7627` |
| `database-debug-androidTest.apk` | 196,829,970 | `a46960416329cb26de37924edaaed3b7bb643e77b9a99b467080cd7555a778e0` |

- 四包 `install -r -t` 均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致（E-01 §3-4）。
- `data-debug-androidTest.apk` 另有一个前一版 `c73233a9…` 仅用于 C4 红轮（APK 本体未保留，完整 sha 不可考）；其后 34/9/11/16/24/16 与 C4 绿轮全部在最终 `36f79b19…` 上重跑（E-03/E-04/E-06）。

### 4.2 14 套件结果（原始日志在 `build/device-track-batch33/`，记录员 grep 复核）

| step | 套件 | 用例数 | 原始日志摘要 | 判定 |
|---|---|---:|---|---|
| 01 | 备份 | 34 | `OK (34 tests)`（`step01-backup-final.log`） | PASS |
| 02 | 结构门 | 5 | `OK (5 tests)` | PASS |
| 03 | 裁剪器 | 9 | `OK (9 tests)` | PASS |
| 04 | C4 视觉四表 | 5 | `OK (5 tests)` | PASS |
| 05 | 迁移矩阵 1→71 | 15 | `OK (15 tests)` | PASS |
| 06 | 69→70 迁移 | 4 | `OK (4 tests)` | PASS |
| 07 | 70→71 专用 | 3 | `OK (3 tests)` | PASS |
| 08 | 提交/CAS | 12 | `OK (12 tests)` | PASS |
| 09 | 视觉仓库 | **11** | `OK (11 tests)` | PASS（现树 12，第 12 例未跑） |
| 10 | 一致门（原型） | 16 | `OK (16 tests)` | PASS（**非产品门**） |
| 11 | painter | 24 | `OK (24 tests)` | PASS |
| 12 | PDF | 16 | `OK (16 tests)` | PASS |
| 13 | 隔离 | 13 | `OK (13 tests)` | PASS |
| 14 | flags | 2 | `OK (2 tests)` | PASS |

- 9 类崩溃签名：17 份 `*-logcat.txt` 逐份 grep **全 0**（记录员实跑）。
- 备份日志五个 grep：`BackupSnapshotTrimException`/`expected:<70>`/`expected:<71>`/`裁剪器只支持 schema=`/`SmbkArchiveException` 均 **0**（记录员实跑；E-03 §3 同）。
- 一致门 15 条负控全红、`PARITY_GATE_DESENSITIZED` 0（E-06；记录员未逐条复算）。
- 隔离 spawn↔ready 与 worker 结果：E-07 记录 `OrphanAssetGcWorker`/`BatchImportDriverWorker` SUCCESS，无 FAILURE/RETRY/CANCELLED；logcat 缓冲清理不彻底是已知缺口（E-07 §5）。

### 4.3 证据轨

- 落盘：`docs/research/evidence/2026-10-29-batch-33/` **10 个 `.md` / 1,007 行 / 47,487 B**（记录员 `wc -l` 与 `find -printf` 实跑）；最大 `00-index.md` **6,910 B**；`du` 索引自述 72 KiB（块大小）。
- 索引自述"45,424 B / 988 行"是设备轨落盘时点；复核修复轮回写后实际为 47,487 B / 1,007 行（低危时点差，不影响合规）。
- 脱敏：证据文件自述 R4 扫描 0 命中；记录员抽查未发现密钥/凭据。

### 4.4 设备源与现树不同源（**本批最重的事实**）

记录员用 `build/device-track-batch33/src-sha-before-c4c.txt`（最终构建源快照，862 文件）对现树跑：

```bash
sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt
# WARNING: 4 computed checksums did NOT match
# 862 行 / 858 OK / 4 FAILED
```

失败的 4 个文件（`grep -v ': OK$'` 实测）：

1. `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`
2. `core/data/src/test/.../visual/VisualCheckpointRetentionTest.kt`
3. `core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`
4. `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`

直接后果：

- **仓库套件**：设备跑 **11 例**（`step09-visual-repo-final.log`），现树 **12 例**（`grep -c '@Test'`）；第 12 例 `stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite` 与"plan 淘汰集含在飞行行"断言**均在设备轨之后补**，从未编译/上设备（`known-defects.md:3032` 自认）。因此 `OK (11 tests)` 只对补强前的源成立。
- **结构门**：现树 diff 为 +18/−6 KDoc，断言未变；设备 5/5 的行为口径仍成立，但"证据 APK sha 与提交源逐字节对应"不成立。
- **提交套件**：现树 12 例，设备 12 例；但文件与快照不同。现树对 HEAD 的 diff 含 `countPrograms`/scene 负向等**用例体改动**（非仅注释），记录员无法从 hash 还原快照内容，故**不能断言**设备 12 例覆盖的就是现树这 12 个方法体。
- **retention 测试**：现树对 HEAD 仅有注释/KDoc diff（`git diff -U0` 过滤注释后为空），但与设备快照不同；设备轨未执行任何 JVM 套件，故不影响设备结论，只说明"源快照 ≠ 现树"。
- **结论**：设备 14 套件的"绿"对 **14:44–15:13 的构建源**成立；对现树，**仓库第 12 例 UNVERIFIED**，其余 3 个文件与设备源不同（结构门/retention 对 HEAD 为 KDoc；提交测试含用例体改动，覆盖范围无法从 hash 判定）。按 T1 判据 §1.3 的"设备数 < 源计数 = 旧包"规则，仓库应判"设备数 < 源计数"，需重建重跑才能声称现树全绿。
- **KDoc 的反向虚报（同一事实的另一面）**：仓库测试与提交测试的 KDoc 都写"`sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt` 对现树全 OK，证明该绿轮对应的源与现树一致"；记录员实测 **858/862、4 FAILED**（且失败文件正是这两份 KDoc 所在的测试文件）。该句是复核修复轮时点后又被后续编辑推翻的**反向虚报**，不得当现树事实（低/中危，见 §5.2 #16）。

### 4.5 KD-80 四条裁决（记录员复核证据后）

| 条件 | 证据 | 判定 |
|---|---|---|
| 80-C1 备份 34/34（S=71） | E-03，最终 data APK `36f79b19…`；`step01-backup-final.log` grep `OK (34 tests)` | **PASS** |
| 80-C2 表清单逐名 | 静态 `S=71` 脚本 `RESULT=PASS`/`EXIT=0`（T1 轨自报；记录员未重跑）；**设备侧未逐名**：`grep -c "rowCounts"` = 0、`grep -c "tutor_visual_snapshot"` = 0（记录员实跑），E-03 §4 自认 raw log 不打印 90 行 | **PARTIAL**（静态 PASS / 设备侧 UNVERIFIED） |
| 80-C3 设备 9/9 + JVM 12/17 | 设备 `OK (9 tests)`（`step03-trimmer-final.log`）；JVM 半边本轨未执行（设备轨无 `testDebugUnitTest`），只有 build/ XML 旁证（12/17，0 失败，mtime 16:17:46） | **PARTIAL** |
| 80-C4 视觉四表 5/5 | E-04，`OK (5 tests)`；C4 类 1,181 行/5 例，34 例基线未改 | **PASS** |

**结论：KD-80 保持 open。** 四条不是全部由同一 APK 上可复核的证据闭合（80-C2 设备侧逐名、80-C3 JVM 半边仍 UNVERIFIED）；按判据 §3.5，不得写 closed。可写"80-C1 / 80-C4 在 `36f79b19…` 上已核销；80-C2 静态半边 PASS、设备侧未逐名；80-C3 设备半边 PASS、JVM 半边 PARTIAL"。

## 5. 复核发现与处置

> 口径：凡"修复后设备未复跑"一律 UNVERIFIED。级别分布：**中 ×7、低 ×9**；另列已核实正向结论。

### 5.1 复核修复轮已处置项（记录员核实现树已生效）

| # | 复核点 | 现树处置（记录员实读） |
|---|---|---|
| R1 | 结构门第 5 例 KDoc 分辨力表述过强 | **已修**：KDoc 明确"端到端黑盒不能单独证明 plan 层 pin 过滤"，指出 plan 层由 JVM、SQL 层由 DAO 用例覆盖；**断言未改** |
| R2 | T3 缺"净值也超 → 拒绝"的 stage 负例 | **已补**：`stagingWhenRetainedBytesStillExceedTheBudgetRejectsBeforeAnyWrite`（`:604`，预算 40 < 净值 58）；**未上设备** |
| R3 | 租约负例不能区分"事务外预检拒绝"与"事务内重判拒绝" | **已补**：断言 plan 淘汰集含在飞行行、端口收到 1 条命令；**未上设备** |
| R4 | 三处 KDoc/台账"未执行/未上设备"反向句 | **已改**：结构门 `OK (5 tests)`、提交 `OK (12 tests)`、仓库 `OK (11 tests)`；retention 仍 UNVERIFIED |
| R5 | KD-80 标题/块仍写 C4 零断言 | **已改**：标题与设备轨块回填 C4 PASS、C2 PARTIAL、C3 PARTIAL，保持 open |

### 5.2 本轮记录的独立发现

| # | 级别 | 位置 | 发现 | 状态 |
|---|---|---|---|---|
| 1 | **中** | `build/device-track-batch33/src-sha-before-c4c.txt` vs 现树 | **设备源与现树 4 文件不同**（858/862 OK）：仓库套件设备 11、现树 12，第 12 例与补强断言从未上设备；`OK (11 tests)` 只对补强前源成立 | 已独立复核 |
| 2 | **中** | `core/data/src/androidTest/.../backup/BackupVisualReferenceInstrumentedTest.kt`；E-04 | **C4 是新增未跟踪文件**（已 staged，1,181 行/5 例）；其绿轮对应 `36f79b19…`，但**记录员未复跑设备**；红轮 raw log 被覆盖，只有控制台转录（E-08 §5） | 未复核（设备） |
| 3 | **中** | `build/device-track-batch33/step01-backup-final.log`；判据 §3.2 | **80-C2 设备侧未逐名**：raw log 对 `rowCounts`/`tutor_visual_snapshot` 均 0 命中；以"代码路径 + 34/34"代替 90 键清单不满足关闭条件 | 已独立复核（grep） |
| 4 | **中** | `core/data/src/main/.../visual/TutorVisualCommitRepository.kt:359-360`；`TutorVisualDao.kt:243-261` | **`reconcileStaleStaging` 零生产调用点**：孤儿 STAGING 永久占预算；首个 stage 的 `STAGING+INITIAL` 不被 evict/备份裁剪删除，且让 program 永远补不出可见 INITIAL。R1 §4.4 列为"必须跟进"，本批未接线、未登记 KD | 已独立复核（grep） |
| 5 | **中** | `core/data/src/main/.../visual/VisualCheckpointRetention.kt:183-185` | **plan 两次读不原子**：并发删除窗口可抛无界 `IllegalArgumentException`，破坏"预检失败 = 有界拒绝"契约；本批未改，未做并发实测 | 已独立复核（源码）；并发未复核 |
| 6 | **中** | `core/data/src/main/.../backup/`；C4 研究 §7.2 | **恢复侧不重查 V-R14/V-R8/R9 与 scene digest**：`grep scene_sha256` = 0；第三方重裁包把 link 指向 STAGING 或改 `link.program_id` 会被接受；C4 只证明检测/拒绝面现状 | 已独立复核（grep） |
| 7 | 低 | `docs/research/2026-10-29-batch-33-device-criteria.md:44-57,107-108`；T1/T2 摘要 | **行锚过期**：结构门 `@Test` 现 `:63/:138/:173/:239/:291`（判据 `:59/:134/:169/:235/:280`）；删除断言现 `:218-219`（判据 `:213-214`）；T2 摘要"对 HEAD 零 diff"现树为 +18/−6 KDoc | 已独立复核 |
| 8 | 低 | `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt` | **T3 摘要行锚过期**：stage 负例现 `:507`（摘要 `:497-578`）、commit 正例现 `:691`（摘要 `:580-643`）；第 12 例未在 T3 摘要中出现 | 已独立复核 |
| 9 | 低 | `VisualDrawOpsParityInstrumentedTest.kt:52`；`VisualDrawOpsParityMetrics.kt:15` | **JVM 例数 10 vs 现树 13** 仍未统一（设备侧 19→24 已统一）；基线 §6.3 已登记待裁 | 已独立复核 |
| 10 | 低 | `docs/research/evidence/2026-10-29-batch-33/00-index.md:11` | **证据体积/行数时点差**：写 45,424 B/988 行，实测 47,487 B/1,007 行（复核修复轮回写后未更新） | 已独立复核 |
| 11 | 低 | `core/data/src/androidTest/.../backup/BackupRestoreInstrumentedTest.kt:482` | **KDoc 残留**"现为 70"，常量已是 71；判据 §1.3 已注明，T6 未改；不影响运行时（`:590` 读常量） | 已独立复核 |
| 12 | 低 | `docs/research/evidence/2026-10-29-batch-33/step08-red-attribution.md:11,20,24,80-83` | **C4 红轮不可独立复现**：raw log 被同路径绿轮覆盖、红轮 APK 只留 sha 前缀 `c73233a9…`、失败信息为控制台转录；A 类归因与源码一致（`AndroidBackupRepository.kt:458` 固定默认库） | 已独立复核（证据）；红轮未复核 |
| 13 | 低 | `core/database/src/main/.../RoomStudyDatabase.kt:2205-2226` | **program 的 source/initial digest 未与内容绑定**：只派生 programId；`sourceSha256` 的 main 命中在无关的 `:1372`。错误值只会静默留库；建议批次 34 补（需改夹具常量） | 已独立复核 |
| 14 | 低 | `docs/research/2026-10-29-batch-33-baseline.md:182-186`；仓库测试 | **第 12 例是设备轨后补的**：基线 §4.4 的"复核修复轮补记"已自认 UNVERIFIED；本记录 §4.4 用 sha 复核证实 | 已独立复核 |
| 15 | 低 | `docs/known-defects.md:3024-3032` | **KD-80 块"862 全 OK"是复核修复轮时点**：现树 4 文件已变；块内已注明第 12 例 UNVERIFIED，但"全 OK"句需按时点读 | 已独立复核 |
| 16 | **中** | `core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（类 KDoc）、`core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`（类 KDoc） | **tracked KDoc 的反向虚报**：两处都写"`sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt` 对现树全 OK，证明该绿轮对应的源与现树一致"；记录员实测 **858/862、4 FAILED**，且失败文件正是这两份 KDoc 所在文件。设备绿轮只对构建源成立，不得据 KDoc 读成现树一致 | 已独立复核 |

**正向结论（已核实，不计入发现）**：

- schema 71 三处同值：`71.json` / `SmbkSelectionPlan` / 生成 Impl；71 个 json、90 实体 / 2 视图；schemas 目录 git 零改动（记录员实跑）。
- 结构门 5 例在设备上绿；pin 分辨力断言完整（DAO 层 + 仓储层），无 `@Ignore`/`Assume`/放宽形态。
- 预算 A-1–A-4 在现树成立；stage 不 demote 是设计；事务内"先淘汰后量"顺序正确。
- `sceneSha256` 第三条 require 在 stage/commit 共用函数内；state/draw/scene 三条 digest 负向都断言 `countPrograms==0`（证明整事务回滚）。
- 14 个设备套件在最终 APK 上全绿；9 类签名 17 份全 0；C4 5/5；34 例基线未改。
- 三份研究文档、判据、C4 测试源均已 staged，悬空引用风险已消除。
- 本批对热区零写入；schema/迁移零改动；生产装配仍为零。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 记录员未复跑；仓内无批次 33 gate 日志 |
| 修复环复跑 | **exit=0**（工单给定） | 同上 |
| 强门（修复环后） | **exit=0**（工单给定） | 同上 |
| JVM 旁证 | **12/0、17/0**（build/ XML） | mtime 2026-10-29 16:17:46；非仓内命令日志，命令归属 UNVERIFIED |
| 设备 备份 34 | **PASS `OK (34 tests)`** | `step01-backup-final.log`，最终 data APK |
| 设备 结构门 5 | **PASS `OK (5 tests)`** | `step02-structure-gates.log` |
| 设备 裁剪器 9 | **PASS `OK (9 tests)`** | `step03-trimmer-final.log` |
| 设备 C4 5 | **PASS `OK (5 tests)`** | `step04-c4-visual.log`（首轮 2 红 A 类已修） |
| 设备 迁移 15/4/3 | **PASS** | `step05/06/07` |
| 设备 提交 12 / 仓库 11 | **PASS（设备源）** | 现树仓库 12，第 12 例未跑 |
| 设备 parity 16 / painter 24 / PDF 16 | **PASS** | 一致门仍是**原型门** |
| 设备 隔离 13 / flags 2 | **PASS** | 9 类签名 17 份全 0 |
| S=71 清单门 | **PASS `RESULT=PASS`/`EXIT=0`** | 判据 §1.4 / E-03 §4 自跑；记录员未重跑脚本，但读了输出 |
| schema 71 三段 | **静态成立；当前树设备复跑 UNVERIFIED** | 71.json / 常量 / Impl 同值 |
| 80-C2 设备侧逐名 | **UNVERIFIED** | raw log 无 `rowCounts`/90 表清单 |
| 80-C3 JVM 半边 | **PARTIAL** | 无仓内命令日志 |
| 生产 release 签名 / 真实 Provider | **UNVERIFIED** | 本批未跑；JS 不广告 |

**记录员保留**：三条门禁 `exit=0` 无本轮日志落仓，记录员未运行 Gradle；JVM XML 是 `build/` 旁证；设备数字有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；设备源与现树 4 文件不同源，不得混用。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面**

1. **设备源与现树 4 文件不同**：仓库第 12 例与补强断言从未上设备（§4.4）；"现树全绿"不成立。
2. **80-C2 设备侧逐名**：raw log 不打印 90 表清单；以代码路径 + 34/34 代替不满足关闭条件。
3. **80-C3 JVM 半边**：无仓内命令日志；`build/` XML 只能作旁证。
4. **C4 红轮不可独立复现**：raw log 被覆盖，红轮 APK 未保留。
5. **强门对当前树**：设备轨后测试文件已改，androidTest 当前源未再编译（记录员未运行 Gradle）。

**代码 / 文档残留**

6. **`reconcileStaleStaging` 未接线**：孤儿 STAGING 永久占预算；`STAGING+INITIAL` 让 program 永远补不出可见 INITIAL。R1 §4.4 列为真实缺陷，建议登记 KD 并接线。
7. **plan 两次读不原子**：并发删除窗口可抛无界 `IllegalArgumentException`；建议改原子读或有界拒绝。
8. **恢复侧不重查 V-R14/V-R8/R9 与 scene digest**：第三方重裁包的语义破坏恢复侧不拒绝；建议 `RestoreVisualReferenceRecheck` 或登记残留。
9. **子集备份丢"失去引用的 LIVE 快照"**：keep 规则只认引用 ∪ INITIAL；需产品裁定是否接受。
10. **`source_sha256`/`initial_state_sha256` 未绑定内容**（program 层）；建议批次 34 补。
11. **`checkpoint_kind` 无域校验**：`TutorVisualSnapshotWrite` 不 require、schema 无 CHECK；只靠测试发现。
12. **JVM 10 vs 13**：parity/metrics KDoc 与 painter pure 计数不一致；待主会话裁定。
13. **`BackupRestoreInstrumentedTest.kt:482`** 的"现为 70"残留；证据索引体积/行数时点差。
14. **KD-71/72/73/77**：本批无新执行证据 → 保持 open（§9）；KD-72 窗口仍不满足（0 跟踪/13 可见未跟踪/14 盘上/benchmark 缺锁）。
15. **KD-80**：C1/C4 核销、C2/C3 PARTIAL → 保持 open。

**环境 / 口径**

16. **一致性门仍是原型门**：`product-gate=no`；不得引用为产品/发版结论。
17. **arm64/16 KB、真机、低内存/高负载、API 23、跨 fontScale** 未覆盖；本批设备仅 x86_64/API 34/4 KB。
18. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
19. **4E.2 MVA 仍不成立**：本批是结构门/KD-80/预算口径，不等于双后端/导出/质量门就绪。
20. **热区在飞未触碰**：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、app、docs/current 其余 M、docs/research 他线未跟踪记录等。
21. **锁文件 14 个**：KD-72 窗口仍不满足，一律不提交。
22. **两处 tracked KDoc 的"sha256sum -c 全 OK"已被 858/862 推翻**（§4.4、§5.2 #16）：设备源与现树不一致；记录员不改代码，留待主会话按"重建重跑或改 KDoc"二选一处置。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；若引用"设备绿"，先按 §4.4 重建重跑（仓库现树 12 例，设备只跑 11）；热区、14 个 `*.lockfile` 与 `build/`、`tmp/` 一律排除。按 ≤6 路径/块：

- **块 1（结构门/提交/仓库/C4 测试）**：`core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`（M；**整文件纳入，含 KDoc**）、`core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`（M；**整文件纳入**）、`core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（M；**整文件纳入**）、`core/data/src/androidTest/.../backup/BackupVisualReferenceInstrumentedTest.kt`（**A，新 5 例**）
- **块 2（生产代码）**：`core/database/src/main/kotlin/.../RoomStudyDatabase.kt`（M；**整文件纳入，含 scene require**）
- **块 3（parity/retention 注释面）**：`core/data/src/androidTest/.../visual/VisualDrawOpsParityInstrumentedTest.kt`（M；**仅注释**）、`core/data/src/test/.../visual/VisualCheckpointRetentionTest.kt`（M；**仅 KDoc**）
- **块 4（研究/判据）**：`docs/research/2026-10-29-checkpoint-budget-accounting.md`（A）、`2026-10-29-backup-visual-reference-checks.md`（A）、`2026-10-29-batch-33-baseline.md`（A）、`2026-10-29-batch-33-device-criteria.md`（A）
- **块 5（证据前半）**：`docs/research/evidence/2026-10-29-batch-33/00-index.md`、`step00-environment.md`、`step01-build-install-dex.md`、`step02-structure-gates.md`、`step03-backup-trimmer-commit-repo.md`、`step04-c4-visual-reference.md`
- **块 6（证据后段）**：`step05-migrations.md`、`step06-parity-painter-pdf.md`、`step07-isolation-flags-signatures.md`、`step08-red-attribution.md`
- **块 7（记录/台账）**：`docs/research/2026-10-29-batch-33-record.md`（新）、`docs/known-defects.md`（M；**整文件纳入**）、`docs/REFACTOR-MASTER-LINE.md`（M；**整文件纳入**）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；**整文件纳入**）

**明确排除（他线在飞/不安全）**：全部 `?? */gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`；core/data 他线 M（mistake/study/knowledge/model/capture 等）；core/database 开工即脏的其余 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`PendingCaptureDao`、`TutorConversationDao`、`CleanRedrawTaskPort`、LibraryFts 三测）；`VisualDrawOpsParserTest.kt`（他线改）；app 全部 M/??；docs/current 其余 M；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；`tmp/**`。

**提交说明**：建议 7 块、每块 ≤6 路径；整文件纳入的 M 在提交说明中注明"整文件纳入，含本批/他线改动"；`BackupRestoreInstrumentedTest.kt` 虽为 M（批次 30 残留，未在本批改），若提交需单列并注明；提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1 293 行（预算会计、四组合、§8.1 符合度、三项跟进）、R2 739 行（四表不变量、两段式 checker、5 例设计、GitHub 参考）落盘；未验证项如实。
- **实施**：T1 判据 849 行；T2 结构门（修复在 HEAD，设备 5/5）；T3 预算口径（A-1–A-4；T3 与复核修复轮共补 3 例）；T4 scene require + 事务性断言；T5 N13 降级/代数句/24 统一；T6 台账/判据/KDoc；T7 分支 D。
- **集成**：无需新增接线；schema 零改动；悬空引用已 staged。
- **设备**：14 套件全绿（5/34/9/12/11/5/15/4/3/16/24/16/13/2）；9 类签名 17 份全 0；C4 首轮 2 红 A 类已修。
- **门禁**：三条 `exit=0`（工单给定）；JVM XML 旁证 12/17；记录员未复跑。
- **本批不声称全绿/全就绪**：设备源与现树 4 文件不同、第 12 例未上设备；80-C2/C3 PARTIAL；KD-80 保持 open；reconcile/plan 原子性/恢复侧复核三项未做；KD-71/72/73/77 open；API 23/arm64/生产签名/真实 Provider 均 UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告；4E.2 MVA 不成立。
- **台账同步**：`docs/known-defects.md`（KD-71/72/73/77/80 的批次 33 记录回链）、`docs/REFACTOR-MASTER-LINE.md`（批次 33 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 33 裁定节）已由本轮记录员写入；均标注"记录员只读核对，未运行 Gradle/设备"。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 6abd807f
git rev-parse --short origin/main                            # 6abd807f（本地跟踪引用，未 fetch）
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain | wc -l                               # 710
git status --porcelain | awk '{print $1}' | sort | uniq -c    # 223 M / 472 ?? / 15 A
git status --porcelain -- core/database/schemas              # 空
git ls-files core/database/schemas | wc -l                   # 71
git status --porcelain -- core/database/src/main/.../RoomStudyDatabase.kt  # M

# schema 71（python 实读）
python - <<'PY'
import json
p="core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json"
d=json.load(open(p,encoding="utf-8"))["database"]
print(d["version"], d["identityHash"], len(d["entities"]), len(d["views"]))
PY
# 71 714c0ecb85ee5edf74f0013740148138 90 2
grep -n "SCHEMA_VERSION\|IDENTITY_HASH\|CLOSURE_VERSION" core/data/.../backup/SmbkSelectionPlan.kt  # 44:71 / 51:714c… / 54:3
ls core/database/schemas/.../*.json | wc -l                  # 71
find core/database/src -name '*71_72*' | wc -l               # 0

# 测试计数（grep -c '@Test'）
# 结构门 5 / 备份 34 / 裁剪器 9 / C4 5 / 矩阵 15 / 69→70 4 / 70→71 3
# 提交 12 / 仓库 12 / parity 16 / painter 24 / PDF 16 / 隔离 13 / flags 2
# JVM SmbkSelectionPlanTest 12 / VisualCheckpointRetentionTest 17 / painter pure 13
# KernelWave0SchemaContractTest 8 / TutorVisualIdentityTest 6 / TutorVisualSchemaContractTest 9

# 设备原始日志（记录员 grep）
for f in build/device-track-batch33/step*.log; do printf '%-38s ' "$(basename "$f")"; \
  grep -E "OK \(|Tests run:|Failures:" "$f" | tail -1; done
# 34/5/9/5/15/4/3/12/11/16/24/16/13/2
for f in build/device-track-batch33/*-logcat.txt; do ... 9 类签名 ...; done   # 17 份全 0
grep -c 'rowCounts' build/device-track-batch33/step01-backup-final.log        # 0
grep -c 'tutor_visual_snapshot' build/device-track-batch33/step01-backup-final.log  # 0
for p in 'BackupSnapshotTrimException' 'expected:<70>' 'expected:<71>' '裁剪器只支持 schema=' 'SmbkArchiveException'; do
  grep -c "$p" build/device-track-batch33/step01-backup-final.log; done      # 0,0,0,0,0

# 设备源 vs 现树（关键）
wc -l build/device-track-batch33/src-sha-before-c4c.txt                       # 862
sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt 2>/dev/null | grep -c ': OK$'  # 858
sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt 2>/dev/null | grep -v ': OK$'
# 4 个 FAILED：TutorVisualCommitRepositoryInstrumentedTest / VisualCheckpointRetentionTest /
#                TutorVisualCommitInstrumentedTest / TutorVisualStructureGatesInstrumentedTest

# 源码锚点（实读）
grep -n "@Test\|snap-staging-keeper\|snap-staging-middle" ...TutorVisualStructureGatesInstrumentedTest.kt
# @Test 63/138/173/239/291；keeper 192；middle 205/219；pinned 223-226；linked 227-230；read 232-235
grep -n "evict\|enforceVisualCheckpointBudget\|programCheckpointStoredBytes" RoomStudyDatabase.kt
# stage 1963-1971 evict / 1975-1982 enforce；commit 2056-2071 / 2076-2083；enforce 2175-2197
grep -n "sha256Hex(write.sceneJson)" RoomStudyDatabase.kt                      # 2277
grep -rn "reconcileStaleTutorVisualStaging" --include=*.kt core app feature    # 仅定义/测试
grep -rn "scene_sha256\|sceneSha256" core/data/src/main/.../backup/ | wc -l   # 0
grep -n "sourceSha256\|initialStateSha256" RoomStudyDatabase.kt               # 仅 :1372（无关）
grep -n "JVM 10" core/data/src/androidTest/.../VisualDrawOpsParityInstrumentedTest.kt  # :52
grep -n "现为 70" core/data/src/androidTest/.../BackupRestoreInstrumentedTest.kt       # :482

# 证据体积
find docs/research/evidence/2026-10-29-batch-33 -name '*.md' | wc -l          # 10
find docs/research/evidence/2026-10-29-batch-33 -name '*.md' -printf '%s\n' | awk '{s+=$1}END{print s}'  # 47487
wc -l docs/research/evidence/2026-10-29-batch-33/*.md | tail -1              # 1007 total

# JVM XML（build/ 旁证，非仓内证据）
ls -la --time-style=full-iso core/data/build/test-results/testDebugUnitTest/TEST-*SmbkSelectionPlanTest.xml ...
# mtime 2026-10-29 16:17:46；tests="12"/"17" skipped=0 failures=0 errors=0

# 锁文件
git ls-files '*lockfile' | wc -l                                              # 0
git status --porcelain | grep -c 'gradle.lockfile'                            # 13
find . -maxdepth 3 -name '*.lockfile' -not -path './.git/*' -not -path './.worktrees/*' | wc -l  # 14
ls benchmark/gradle.lockfile                                                  # No such file

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 git add/commit/push；任何 JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 32 记录的结构对应

| 批次 32 节 | 本批对应 | 差异 |
|---|---|---|
| §2 两研究线 | §2 两研究线 | 本批为预算会计 + 备份视觉四表；R1/R2 均含 GitHub/官方文档外部参考 |
| §3 七实施轨 + 集成 | §3 七实施轨 + 集成 | 轨名不同（T1 判据 / T2 结构门 / T3 预算 / T4 内容绑定 / T5 盲集 / T6 文档 / T7 条件） |
| §4 设备与证据 | §4 设备与证据 | 14 套件；新增"设备源与现树 4 文件差"小节 |
| §5 复核发现 | §5 复核发现 | 5 项修复轮处置 + 16 项独立发现 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
