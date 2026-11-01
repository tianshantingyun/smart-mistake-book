# 批次 31 记录（2026-10-29）：持久化语义收口 + 设备绿轮 + 一致性门标定

> 记录时点：2026-10-29（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` **只读**核对计划、基线、两份研究文档、T1 判据、T12 记录、`docs/research/evidence/2026-10-29-batch-31/` 十五份 `.md`、`build/device-track-batch31/` 原始日志与源码锚点后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码。** 本轮执行的是只读命令：`git status/rev-parse`、`grep`、`wc`、`sha256sum`、`python` 解析 JSON、`ls`/`find`/`du`，命令与输出见 §10；另写了本记录与三份台账（MASTER-LINE / decisions / KD-80 回链，见 §8）。
> 计划：`.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md`（44 行，已完整读）。
> 基线：`docs/research/2026-10-29-batch-31-baseline.md`（339 行，只读核对）。
> 研究：R1 `docs/research/2026-10-29-snapshot-identity.md`（551 行）、R2 `docs/research/2026-10-29-parity-calibration.md`（381 行）。
> 判据与台账轨：`docs/research/2026-10-29-batch-31-device-criteria.md`（901 行）、`docs/research/2026-10-29-batch-31-t12-doc-consistency.md`（351 行）。
> 设备证据：`docs/research/evidence/2026-10-29-batch-31/00-index.md` 与 `step00`–`step13`（15 份，含 `step08b`）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 2 轮 `exit=0`。仓内无本轮 gate 日志，记录员未复跑。
>
> **本记录不声称"批次全绿"**：设备面 **11 套件绿 / 1 套件红**（备份 34、69→70 迁移 4、迁移矩阵 15、painter 24、隔离 13、flags 2、PDF 16、提交 9、仓库 4、结构门 4、trimmer 9 绿；一致性原型门 `Tests run: 15, Failures: 1`）。红例为 N09-P06 的旧 `centroid=1.000` 等式（归因 A），门级 `red=true`、产品源零改动、未放宽断言，但 **`calibrated-r1` 的 15 例绿轮 UNVERIFIED**。**更关键：设备 APK 构建于 06:20 前后，现树在 07:53–08:03 另有 7 个源文件改动（852/859 源文件与构建快照相同），提交/仓库/结构门/parity 四套件的设备结果不对应当前源码；两例新增测试从未上设备。** 批次验收"设备全绿 + 一致门按新阈值重跑并记录绿轮"**不成立**；"迁移 1→70 全过"成立；schema 71 按裁定推迟批次 32。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 11 项**（§1）：A 设备绿轮（全量复跑 + KD-80 关闭核销）；B 持久化语义收口（69→70 断言修复、checkpoint 接线、身份与指针结构门、~~内容唯一 vs 渲染版本~~ 推迟批次 32）；C 一致性门标定（P07 归因 + `calibrated-r1`）；D 文档与台账（计数回改、KD-71/72/73/77/80）；E 工具与环境（AUD-08 真设备生产者复跑、KD-72/KD-77）。
- **实际执行面**：两研究线（R1 快照身份与数据库约束 / R2 一致性门标定）+ T1 判据 + T2–T7 六实施轨 + 集成交接 + 设备轨（`emulator-5562`，串行 13 步）+ 证据 15 份 + 复核修复轮 + 本记录与三份台账同步（MASTER-LINE / decisions / KD-80 回链）。T3 的 schema 71 半边按主会话裁定**整项推迟**（计划 §1-6、§2、R1 §9）。
- **不做（全部未做）**：schema 71 / 70→71 迁移 / `71.json`（批次 32）；渲染版本并入身份（批次 32）；core/export 集成与 PDF `/AIGC`（KD-75 热区）；双后端装配；JS 广告（MVA 未过）；真实 Provider（无凭据）；锁文件生成/提交（KD-72）；API 23 修复后 APK 冒烟（KD-77）；热区 UI。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature 五模块、core/ui 全部、core/markdown 整目录、core/export、core/domain、`settings.gradle.kts`、`.gitignore`、Root/SecondaryScreens、docs/current 其余 M、tools/kb、knowledge-production。
2. **schema 71 单写者 = T3**；本批裁定不升 schema，因此 T3 也不得改 `StudyDatabase.kt`/迁移/`schemas`。实测三者开工与收尾均干净（`git status` 无输出）。
3. **core/database 开工即脏 11 条（10 M + 1 ??）、core/data 14 条（13 M + 1 ??）**：按"整文件纳入并注明"处理；本批落点文件（DAO/RoomStudyDatabase/visual 包）开工时干净，只在 T3 改动后变 M。
4. 一致性门仍是**原型门**，不得引用为产品/发版结论；JS 不广告；真实 Provider 无凭据；AIDL 只以 Gradle 任务验证；AAR/stdlib 不变。
5. 设备只 `assemble*` + `adb install -r -t` + `am instrument`，禁 `connectedAndroidTest`；同一落点多个任务合并给一个拥有者。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`59d4a03f`；`origin/main` 本地跟踪引用同为 `59d4a03f`；`git rev-list --left-right --count origin/main...HEAD` = `0 0`（**未 `git fetch`**，远端实时 hash UNVERIFIED）。
- **工作树**：**711 行 = 232 M + 479 ??，staged 0**（记录时点实测）；基线冻结为 690 = 218 M + 472 ??（基线 `:5`），增量 = 本批代码/测试/研究/证据 + 他线在飞。
- **DB schema**：`STUDY_DATABASE_VERSION = 70`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`）；导出目录 **70 个 json**（1..70），`git ls-files core/database/schemas` = **70**；**无 `71.json`**（`ls …/71.json` = No such file or directory）。
- **`70.json`**：`version=70` / `identityHash=11a8a701bae767a8a3c5bc810e4743a4` / **90 实体** / 2 视图（`library_catalog`、`knowledge_question_lattice`），python 实读。
- **请求 schema**：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（基线 `:24`；本批零改动）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、feature/review **12**、feature/capture **16**、feature/profile **4**、core/ui **35**、core/markdown **1**（整目录未跟踪）、core/export **6**、core/domain **5**；`settings.gradle.kts`/`.gitignore` M。本批对上述路径零写入。
- **锁文件**：盘上 `*.lockfile` **14**；`git ls-files '*lockfile'` = **0**；W1 六文件 **6/6 不干净**（5 M + `core/markdown/build.gradle.kts` ??）。KD-72 窗口仍不满足。

### 1.4 本批增量点名（按现树实读）

- **改（T3 持久化语义）**：`dao/TutorVisualDao.kt`（515 行；消息 link 初次插入补 `program_id`、evict SQL 加 `status=STAGING`、投影/查询带 status）、`RoomStudyDatabase.kt`（2277 行；快照 digest/身份复派生、checkpoint 投影 status、提交事务 demote/evict）、`TutorVisualCommit.kt`（15 行变动，`TutorVisualCheckpointSummary.status`）、`port/TutorVisualPort.kt`（6 行变动）、`VisualCheckpointRetention.kt`（233 行；LIVE 不进淘汰候选、未知 status fail-closed）、`TutorVisualCommitRepository.kt`（480 行；`checkpointPlanLock`、STAGING 已有行重跑 plan、预算映射）。
- **改（T4 标定）**：`VisualDrawOpsParityMetrics.kt`（977 行；`THRESHOLD_VERSION=calibrated-r1`、`TextThresholds` 唯一事实源、硬几何护栏、TEXT F5 删除）、`VisualDrawOpsParityInstrumentedTest.kt`（859 行；N09–N12）。
- **改（T2 迁移测试）**：`TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`（1047 行；结构读数 + 同源断言 + 负控）。
- **测试增量（现树计数）**：`TutorVisualStructureGatesInstrumentedTest.kt`（**新文件**，551 行 / 4 例，未跟踪）；`TutorVisualCommitInstrumentedTest.kt` 10 例、`TutorVisualCommitRepositoryInstrumentedTest.kt` 5 例、`VisualCheckpointRetentionTest.kt` 17 例（JVM）。
- **研究/判据/记录**：R1 551、R2 381、基线 339、T1 判据 901、T12 351、设备证据 15 份、本记录。
- **零改动**：`StudyDatabase.kt`、`schemas/`、迁移注册、`SmbkSelectionPlan`/`BackupSnapshotTrimmer`、`TutorVisualIdentity.kt`、`entity/TutorVisualEntities.kt`、`settings.gradle.kts`、AAR/stdlib。

## 2. 两研究线

### 2.1 R1 快照身份与跨 program 结构门（`docs/research/2026-10-29-snapshot-identity.md`，551 行）

- **性质（`:3-12`）**：设计/研究文档，零 Gradle/零设备/零源码改动；SQLite 行为来自本会话 Python 3.13 / SQLite 3.50.4 内存库探针（A–H 八组）与官方文档交叉核对；`已落`/`未落`/`建议`/`裁定` 四分。
- **九条结论（§0 `:18-28`）**：
  - **C1**：`renderer_version` + `font_manifest_version` 必须**同时**进①唯一键②`snapshotId` 派生式③内容查重键；只并入其一会退化成两套口径。它消灭的失败是"painter 升级后同内容提交被静默吸收进旧行 → 恢复侧 `RENDERER_VERSION_MISMATCH` → 该图永久只读"。
  - **C2**：计划 §0:10 的"painter 升级后无法持久化同内容"**与树不符**——能持久化，只是被静默吸收；结论成立、前提必须改写；S1 是对批次 30 裁定的**升级而非否定**。
  - **C3**：70→71 **不需要 `ALTER TABLE`**（两列 v70 已有），只需 `DROP INDEX` + `CREATE UNIQUE INDEX`；新键是旧键严格超集 ⇒ 对存量行不可能失败。
  - **C4**：跨 program 漏洞的真实位置是**消息 link 初次插入**（`TutorVisualDao.kt:278-298` 当时缺 `program_id`），不是 CAS、不是题面 pin（后两者批次 30 已修）。后果是别人的快照被永久 pin（pin/evict 只按 `snapshot_id` 关联）。
  - **C5**：写入侧 EXISTS 必做（1 行）；触发器作 71 的 DB 级 backstop；**复合外键否决**——Room 迁移在 `BEGIN EXCLUSIVE` 事务内（`RoomConnectionManager.kt:184-207`），`PRAGMA foreign_keys` 事务内 no-op（实测 E4 + 官方文档），且存量违规行会让表重建的 `INSERT…SELECT` 直接失败（实测 H3）。
  - **C6**：题面 STAGING 门**已在树**（`:407-428`），残余是**零调用者、零用例 + 失败原因折叠**，应补负向用例与有码拒绝。
  - **C7**：`snapshotId` 写库前不复派生（DB 层），与 program 的复派生不对称；补 3 行 `require`，且必须与 C1 同一个派生函数。
  - **C8**：71 改 `identityHash` → 必须同批改 `SmbkSelectionPlan.SCHEMA_VERSION`/`IDENTITY_HASH`，否则备份裁剪器 fail-closed，把 KD-80 绿轮打红。
  - **C9**：现在是改派生式的唯一低风险窗口（四表在任何升级库中为空；写入仓储在 app/feature **零调用者**）。
- **S1 四方案取舍（§2.2 `:116-124`）**：S1 并入身份（推荐）/ S2 只改派生（被唯一索引静默吞掉，`changes=0`）/ S3 版本冲突拒绝（每次同内容提交都失败）/ S4 维持现状（永久只读）。推荐形态（§2.3）：派生式五字段全加长度前缀；唯一键五列；查重键必须等于唯一键；迁移 DROP+CREATE（换名，禁用同名 `IF NOT EXISTS`）；旧行不回填（生产库 v70 行为 0）。
- **三种修法对比（§3.2 `:243-252`）**：O1 写入侧 EXISTS（覆盖 INSERT、不动 schema、零存量影响）；O2 复合外键（覆盖任何写、要重建两张子表、存量违规行让升级失败）；O3 触发器（覆盖任何写、无表结构变化、fresh install 需 `onOpen` 幂等补建）。推荐 O1 + O3（§3.5 `:293-324`）。
- **裁定（§9 `:499-551`）**：**本批不做 schema 71**，任务 6 整体推迟批次 32；理由=`identityHash` 只能由 KSP 生成、本批无回填环节、伪造比不做更糟。批次 32 顺序：KSP 构建导出 71.json → 同批回填常量 → 备份 34/34 + 迁移 1→71 → 设备。红线：不得估算/占位哈希、不得手写 71.json、三处身份同批同源。本批已落的无 schema 变更部分（`:544-551`）与 71 互不依赖。
- **未验证（§7 `:437-449`）**：全部"已落"来自源码实读；SQLite 探针是桌面引擎不是设备；`VISUAL_FONT_MANIFEST_VERSION` 常量不存在；71 的 identityHash 无法预知；设备 `PRAGMA foreign_keys` 是否恒 ON 未实测。

### 2.2 R2 一致性原型门阈值标定（`docs/research/2026-10-29-parity-calibration.md`，381 行）

- **性质（`:10-11`）**：标定方法 + 阈值建议 + 来源；未改产品源码、未跑 Gradle、未上设备；门仍是原型门。**顶部设备更正（`:13-25`）**已把"1 px 平移 = 1.000"标为被设备否证。
- **P07 归因（§4.1 `:238-246`）**：**A 类 = 原型门阈值未标定 × KD-78④ 文本 AA 固定关**。判据链：两轮同值 0.5181329158118464；AA 开 0.44 → AA 关 0.518（屏掩膜 1434→1224，PDF 1431 不变）；`flags=0` 同时关 AA、开 hinting、关 subpixel 定位；`bestShift=(0,0)`、bboxDev=1、neighborIou 未改善；与产品行为一致；同设备新包；改阈值而非改期望。
- **0.5 px 结构性不可达（§0-2 `:38-39`）**：屏侧 `flags=0` 允许 ≤0.5 px 位置吸附；P07 原点恰整像素所以该项未消耗，0.518 由 AA 边缘 + hinting 解释；但跨栅格距离不是 |v|+|t|，方向相关。解析上界 `(1431−1224)/1224×35≈5.9 px` 比实测大 11 倍，**不能由第一原理推阈值**。
- **`calibrated-r1`（§4.2 `:248-261`）**：质心 0.5→**0.75**、墨量比 0.95/1.05→**0.93/1.07**、bbox ≤1 与 TV ≤0.08 **不变**、TEXT 的 F5 **删除（恒 0）**、新增文本 `bestShift=(0,0)` 硬护栏、新增"label 原点整像素"前置条件、`THRESHOLD_VERSION=calibrated-r1`；其余全部不变。
- **"F2/F3 先于 F4"不可字面实现（§0-6 `:43-44`、§2.4 `:150-177`）**：把 `edgeMean≥1` 提到 bbox/质心之前会让 N04（`edgeMean=127.79`）判 F2（不红），门当场失去分辨力；正确形态 = **带硬几何护栏的归因优先**。
- **放宽许可证 N09–N12（§2.3 `:139-148`）**：N09（+1 px 整块平移必须红；设备实测 P06 由 `bboxDev=2` 与 `bestShift=(1,0)` 抓，质心读 1.2625 而非 1.000）、N10（整页错画 → 带内无墨）、N11（180° 旋转 → 质心 1.1665 / TV 0.371）、N12（+4 px → centroid 3.56）。任一条绿 = `PARITY_GATE_DESENSITIZED`。
- **规则改写（§4.3 `:263-274`）**：R2 §4.3-1 的"2× 余量"对跨栅格质心不适用（旧推导 2×0.518=1.036>1.000 建立在已被否证的"1 px=1.000"上）；改为位移类**双侧绝对余量**（阈值−最大正例 ≥0.15 px、信号−阈值 ≥0.25 px，下侧锚改用 N11 1.1665）、幅度类**包住结构摆幅且 < 最弱真缺陷**、整数指标豁免。
- **未验证（§4.6/§5 `:333-351`）**：设备已跑但套件 FAIL、N09-P07 未执行；0.75 对其它 CJK 未验证；文本 bestShift 护栏只在 P06/P07 有 (0,0) 读数；N11 的 TV 预期未逐位复算；PDFium/Skia 内部未读。

### 2.3 研究复核结论

- R1 的 C1–C9 与 T3 落码逐条对应：C4 → 消息 link `program_id` 谓词（`TutorVisualDao.kt:305-310`）；C6 → 题面 STAGING/跨 program 负向（结构门 4 例）；C7 → `ensureTutorVisualSnapshot` 复派生（`RoomStudyDatabase.kt:2180-2186`）；C8/C9 → schema 71 未做、常量未动；C3/C5 留作批次 32 的迁移形态与触发器依据。
- R2 的 `calibrated-r1` 与 T4 落码逐条对应：阈值唯一事实源（`Metrics.kt:248-256`）、硬护栏（`:647-724`）、TEXT F5 删除（`:727`）、N09–N12（测试 `:298/338/354/388`）。设备跑出的 N09-P06 红否证了旧等式，不否证归因。
- 两份研究都强调"设计通过 ≠ 设备通过"；设备证据见 §4，且**设备源与现树仍有 7 文件差**（§4.4）。

## 3. 七实施轨 + 集成交接

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 轨自述 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（901 行）；设备由设备轨执行** | 判据含设备结果回填；计数冻结与现树脱节（§5 #3） |
> | T2 迁移测试修复 | 完成 | **落码成立；设备 4/4 绿；文件与设备构建快照逐字节相同** | 断言由 DDL 文本改为结构读数 |
> | T3 持久化语义 | 部分 | **无 schema 变更的门全部落码；设备 9/4/4 绿（旧版本）；schema 71 按裁定推迟** | 现树另有 2 例新增测试未跑（§4.4） |
> | T4 门标定 | 完成 | **落码成立；设备 FAIL 14/15（A 类）；现树 N09 已重写但未复跑** | `calibrated-r1` 绿轮 UNVERIFIED |
> | T5 文档回改 | 完成 | **完成（5 路径 + 本记录）；复核轮又回改 5 处** | 见 §3.5 |
> | T6 工具与环境 | 部分 | **AUD-08 机制复跑成立、设备数据度量 UNVERIFIED；KD-72/77 零动作** | 见 §3.6 |
> | T7 条件轨 | 完成 | **分支 D 登记，零改动；九项仍 pending** | 见 §3.7 |
> | 集成交接 | 完成 | **三处接线核完，补 1 处真缺口（T1 判据漏结构门套件）** | 见 §3.8 |
> | 设备轨 | 13 步完毕 | **11 绿 / 1 红；4 套件与现树不同源** | 见 §4 |
> | 证据轨 | 完成 | **15 份 `.md` / 85,170 B（du 120K）** | 见 §4.3 |

### 3.1 T1 设备判据 —— **文档落盘；含设备结果回填；计数冻结已被现树超越**

- 文件 `docs/research/2026-10-29-batch-31-device-criteria.md`（**901 行**）。内容：快照事实与计数冻结（§1）、八套件硬判据（§2）、KD-80 关闭门（§3）、串行命令与 dex 符号门（§4）、签名扫描（§5）、红例归因（§6）、设备回填（§7）。
- **记录员核对**：现树 `grep -c '@Test'` = 备份 34、69→70 **4**、矩阵 15、painter 24、隔离 13、flags 2、PDF 16、parity **15**，与判据 §1.2 一致；但补充套件现树为 **提交 10 / 仓库 5**，判据 §4.5 仍写 **9 / 4**（§1.2 的八套件表不含补充套件；§5 #3）。
- **判据已含设备回填**：§0 与 §7 明确 `Tests run: 15, Failures: 1`、N09-P06 旧等式、归因 A、`calibrated-r1` 绿轮 UNVERIFIED；N09/N10/N11/N12 期望与行锚已按复核修复轮回改（`:313-319`；现树锚 `:298/338/354/388` 实测一致）；盲区文案已把 `(0,0.76)` 标为作废（`:310-312`）。
- **计数冻结规则（§1.3）**：`N_run < N_frozen` = D（旧包）；`N_run > N_frozen` = 重建；构建前后源快照必须一致。设备轨自身满足"构建前后 859 文件快照相同"（`src-snapshot-before.txt` 与 after 逐字节相同），但**构建之后**现树又改了 7 个文件（§4.4），按同一条规则应视为"结果只对旧源成立"。

### 3.2 T2 迁移测试修复 / 计划任务 3 —— **落码成立；设备 4/4 绿；与构建快照逐字节相同**

- 文件 `core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`（**1047 行 / 4 例**；`sha256` 与设备构建快照一致）。
- **改法（实读）**：删除对 `sqlite_master.sql` 的 DDL 文本断言；列序用 `PRAGMA table_info`（`:853`）、索引名/唯一性用 `PRAGMA index_list`（`:865`、`:896`）、索引键列序用 `PRAGMA index_info`（`:880`）、外键用 `PRAGMA foreign_key_list`（`:922`）；11 条索引逐条比对列序与 UNIQUE（`:343-401`）。
- **执行顺序**：数据保全 → 外键负向 → 形状（`:74-79` 附近），形状红不会让旧行/外键证据为零；幂等二次校验同时重跑两者。
- **同源与负控**：`indexExpectationsStayInSyncWithTheMigrationDdlConstants`（`:228`）把期望与迁移 DDL 常量双向对齐；`indexShapeAssertionsCanFailOnWrongColumnOrderAndNonUniqueIndex`（`:265`）在临时库造错列序/非唯一/缺失/多一条四种反例，证明断言能红。
- **设备结果（E-02）**：`OK (4 tests)`（`build/device-track-batch31/step02-migration-69-70.log`）。
- **残留（§5 #10）**：KDoc 声称资产/题/修订/修订附图/会话/消息/消息附图全部原样读回，`assertOldV69RowsPreserved` 实际只断言 message、problem_revision、asset 三行（seed 8 行）。

### 3.3 T3 持久化语义 / 计划任务 4–5（+6 推迟） —— **无 schema 变更的门全部落码；设备绿为旧版本**

- **checkpoint 接线（实读）**：`planCheckpoint` 按既有行 status 分流——LIVE → 不进策略、幂等；STAGING → 排除自身后重跑 plan，把 demote/evict 带入提交事务；新快照 → plan，`Rejected` 映射 `TutorVisualCommitRejectedException`（`TutorVisualCommitRepository.kt:346-400`）。`checkpointPlanLock`（`:167-177`）把预算读—判—写串成进程内临界区，消灭"两个并发提交同时过 4 MiB/32 MiB 门"。
- **保留策略（实读）**：`evictUnpinnedCheckpoints` 只删 `status=STAGING` + `kind ∈ {NULL, LATEST}` + 未被消息/题面引用（`TutorVisualDao.kt:169-189`）；`VisualCheckpointRetention` 的 evictable 同样只认 STAGING，LIVE/INITIAL/未知 status 一律保留。
- **指针结构门（实读）**：消息 link 初次插入 `INSERT…SELECT…WHERE snapshot_id=? AND status='LIVE' AND program_id=:programId`（`TutorVisualDao.kt:305-310`）；指针 CAS 与题面 pin 同形（`:362`、`:438-439`）。消灭"跨 program 快照被永久 pin"。
- **身份门（实读）**：`ensureTutorVisualSnapshot` 先 `require(stateSha256 == sha256Hex(stateJson))`、`require(drawCommandsSha256 == sha256Hex(drawCommandsJson))`，再用同一个 `deriveSnapshotId` 复派生并 `require` 相等（`RoomStudyDatabase.kt:2174-2186`）。这同时关闭了"id 自洽但 digest 与内容脱钩"的入口（新增用例 `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`，`TutorVisualCommitInstrumentedTest.kt:124`）。
- **测试**：JVM `VisualCheckpointRetentionTest` 17 例（本机 XML 17/0，`build/` 旁证）；设备提交 9、仓库 4、结构门 4 全绿（E-09）。现树新增/修改但**未上设备**的：提交第 10 例（digest 负向）、仓库第 5 例（plan→command evict 端到端）。
- **schema 71**：按裁定不做；`StudyDatabase`/迁移/`schemas`/`SmbkSelectionPlan`/`BackupSnapshotTrimmer` 零改动；`TutorVisualIdentity` 三参签名未改。
- **生产装配**：`TutorVisualCommitRepository` 仍只在 androidTest 构造；`DefaultFigureGenerationCoordinator` 无引擎注入；四表写入仍无产品调用者（A-01/A-13 保持后批）。

### 3.4 T4 门标定 / 计划任务 7 —— **落码成立；设备 FAIL 14/15；现树已重写 N09 但未复跑**

- **阈值（实读）**：`THRESHOLD_VERSION = "calibrated-r1"`（`VisualDrawOpsParityMetrics.kt:242`）；`TextThresholds` 唯一事实源（`:248-256`）：bbox 1、centroid 0.75、mass 0.93/1.07、TV 0.08、reportedIou 0.99。判决、证据日志、设备断言读同一份数值。
- **分类（实读）**：TEXT 判决 = 硬几何护栏（带内无墨 / bboxDev>1 / centroid>0.75（含 NaN）/ bestShift≠(0,0) 且 gain>0.02）→ F4；护栏内先 F1（mass/TV）后 F2（edgeMean/iou）→ PASS（`:647-724`）。计划字面的"F2/F3 先于 F4"未按字面实现，理由与反例写在 KDoc（N04 `edgeMean=127.79` 会被误判 F2）。F3 不搬（文本 interiorMax 永不命中）。TEXT 的 F5 删除（`:727`）。
- **新增负控**：N09（P06+P07 +1 px → 必须红 + F4_GEOMETRY，现树 `:315-326`）、N10（整页错画 → `pdfBox=null`、bboxDev=MAX、F4）、N11（180° 旋转 → bbox/墨量不变但仍红，新增整数置换 `rotate180WithinBand`）、N12（+4 px → centroid>0.75）。
- **设备结果（E-08/E-08b）**：`Tests run: 15, Failures: 1`，N09-P06 在旧等式 `centroid=1.000` 上失败（实测 1.2624767812258337）；同轮门级 `verdict=F4_GEOMETRY red=true`、理由 `text bboxDev=2px > 1px`；`PARITY_CONTROL` 14/14 红、`PARITY_GATE_DESENSITIZED`=0、`PARITY_F5`=0；两次复跑逐位同值（E-08b）。
- **复核修复（现树）**：撤掉 N09 的伪等式，改为"必须红 + F4_GEOMETRY"，质心/bbox/bestShift 只进证据日志；P07 的 `centroid > 阈值` 断言一并撤掉（P07 反解约 0.608 < 0.75，不能作为许可证）。**新测试未上设备**。
- **残留（§5 #4）**：N09 现在对 P07 也断言 F4_GEOMETRY，但 P07 的 +1 px 从未设备实测；按反解质心不会触发护栏，只能靠 bbox 或 bestShift；P07 基线 bboxDev=1，+1 后是否变 2 未实测 → 该断言的可满足性 UNVERIFIED。

### 3.5 T5 文档回改 / 计划任务 8–9 —— **完成；复核轮再回改 5 处**

- 改动（T5）：`docs/known-defects.md`（KD-79 13→16 就地表、KD-71/72/73/77/80 Progress）、`docs/current/visual-generation-contract.md`（§5.1 PDF 258 行、painter 334、Pure 228、parity 15、`calibrated-r1` 阈值 + 设备 FAIL）、`2026-10-26-batch-24-4e2-admission-checklist.md`（附录 G 批次 31 段）、`2026-10-29-batch-30-t12-doc-consistency.md`（顶部指针）、新增 T12 记录（351 行）。两处开工即 M 的文件按纪律**整文件纳入并注明**（T12 `:33`）。
- 复核修复轮追加：契约 §8.1 改为"LIVE 永不淘汰，只淘汰未固定且 STAGING 的中间态"（`:190`）、契约 `:123` 改为设备 FAIL 14/15、KD-80 Progress 回填设备轨结果并保持 open、T1 判据 N09/N11/N12 期望与行锚回改。
- **记录员核对**：上述五处现树均已生效（契约 `:123`/`:190`、`known-defects.md` KD-80 最新块、判据 `:313-319`）；计划 §3 的验收句已由"迁移 1→71"改为"迁移 1→70"（`:44`）。记录员另在 KD-80 最新块追加一行"记录回链"（指向本记录 §4.4 的 7 文件源差）。
- **残留（§5 #15）**：T3 摘要称 `docs/current/visual-generation-contract.md` 是热区未改，T5 实际编辑同一文件（计划任务 8 要求）；整文件纳入已注明，但两轨对同一文件的禁改口径不一致，需主会话统一。

### 3.6 T6 工具与环境（AUD-08 / KD-72 / KD-77） —— **部分**

- **AUD-08（实读产物）**：从 `emulator-5562` 取 schema 70 真设备库快照（`tmp/aud08-db-5562/smart-mistake-book.db`，12,775,424 B，sha256 `eb4d3271…f4dc`）。记录员只读查询该快照：`split_import_job` / `split_import_question` / `batch_import_page` 均 **0 行**；`export_split_eval.py` exit 0，导出 **0 页 0 框**；`gate-report.json`：count `0.2857 < 0.9` FAIL、fragment 0、`appSource.present=true` 但 `pageMap=null`；两次复跑逐字节一致；4C 存量导出复跑仍 `0.5714` FAIL。（`build/`、`tmp/` 均不入仓。）
- **溯源缺口（已登记未修）**：`source.db.sha256` 只覆盖主库字节、不覆盖 WAL；同一主库 + 不同 WAL 会得到不同导出却报同一快照 sha（README `:86-89` 有受控实验记录：主库 sha 同为 `946b896c…`，带 WAL 导出 1 页 2 框、无 WAL 0 页）。本次设备快照的 `-wal` 为 **0 B / 05:38**，主库 05:26，无法证明三件同刻取到。
- **结论**：设备数据上的度量复现仍 **UNVERIFIED**；不得用"0 页导出"断言设备库里没有分题数据。
- **KD-72（记录员实测）**：盘上 14 锁、跟踪 0、staged 0、W1 6/6 不干净 → 窗口仍不满足，零动作，保持 open。
- **KD-77**：本批未起 API 23 AVD；证据目录 15 份均为 API 34；旧 APK 冒烟 logcat 至今未落仓 → 保持 open，API 23 发布矩阵项不得标绿。

### 3.7 T7 条件轨 —— **分支 D 登记，零改动**

- 开工再核热区布尔与指派一致（feature/library 43、feature/tutor 59、core/ui 35 脏，记录员复核同值）。按 R4 九项序位 G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段 6→5c 逐项复核：九项全部仍 pending、载体均在；未新建 KD/条目/文件。
- 状态变化仅两处：批次 30 已落 schema 70（5c 的 schema 前置已满足，但 D-1/AUD-11 仍开）；`gate-report-rerun-batch31.json` 存在但 `appSource.present` 无逐页来源（记录员实测 `pageMap=null`），AUD-08 收口未达成。
- 锚点漂移：MASTER-LINE AUD-08 门行 `:148→:152`；KD-68 仍 `:2652`；`LearningMasteryScreen` 的 `KnowledgeMasteryRow` 仍不可点。热区另有未跟踪新产物（core/ui 24 ??、app 5 ?? 含 assets），未触碰。

### 3.8 集成交接 —— **三处接线核完；补 1 处真缺口**

- **T3 五层闭环**：投影/DTO/DAO/仓储/事务同源（`status` 轴贯穿 `TutorVisualCheckpointSummary` → `checkpointSummaries` → `VisualCheckpointRow` → `plan` → `evict`；`snapshotId` 复派生在写库前 `require`；digest 与 JSON 一致性同处校验）。
- **T4 常量与文档**：`calibrated-r1 / 0.75 / [0.93,1.07] / 1 / 0.08 / parity 15` 与判据、校准文档、契约逐值一致；唯一未改的旧句是 `Metrics.kt:191` 的"下余量 0.250"（§5 #9）。
- **T2 断言的三方一致**：11 条索引期望与迁移 DDL 常量、`70.json` 逐名逐列序逐 UNIQUE 一致（T2 用 Python 实跑；记录员复核断言改为结构读数）。
- **补的缺口**：T1 判据最初漏掉 `TutorVisualStructureGatesInstrumentedTest`（4 例）与两个提交套件的新方法，dex 门与串行命令都不覆盖；已补进 §4.3 dex 符号门、§4.5 计数与第三条 `am instrument` 命令，并修正 N09–N12 行锚。
- **生产装配零改动**：仍无产品调用者；`DefaultFigureGenerationCoordinator` 无引擎注入（A-01/A-13 后批）。

### 3.9 设备轨与证据轨（摘要，详见 §4）

- 设备轨串行 13 步（`emulator-5562` / API 34 / x86_64 / PAGE_SIZE 4096）；四包 `assemble*` **EXIT=0**（`BUILD SUCCESSFUL in 37s`，记录员实读 `step00-build.log`），构建前后 859 源文件 sha256 相同；四包 install 成功、设备侧 `pm path` + sha256 与宿主一致（E-00）；三测试包 `DEX OK`（记录员实读 `step03-dex-gate.log`）；schema 资产门 V=70 / 70 个 json / 含 70.json（E-00）。
- 证据：15 份 `.md`、文件字节合计 85,170 B（`du -sh` = 120K）、最大 13,325 B（00-index）；13 份 logcat 的 9 类崩溃签名由记录员实跑 grep 复核（全 0）；PDF native IOException 230 与隔离 spawn 17=ready 17 引自 E-10（记录员未复算 pid）。

## 4. 设备与证据

> 环境：`emulator-5562`（API 34、x86_64、PAGE_SIZE 4096、fingerprint `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`）；零 `connectedAndroidTest`、零 `adb root`、零 git 写。

### 4.1 产物身份（E-00）

| 产物 | 字节 | sha256（宿主=设备） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,525,382 | `a3973a7a584e425ebaa1dc1e07f0cb96fb799fc80b2fe5111963691434ea42cc` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197,763,509 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952` |
| `database-debug-androidTest.apk` | 196,823,330 | `4e54f0de476712add5bc839dec16a419b3927a4fdfc27bf41beca4e290c7da2d` |

- 四包非批次 30 旧包；三测试包 dex 门 `DEX OK`；`schema` assets gate V=70、70 个 json。
- **记录员复核**：`src-snapshot-before.txt` 与 `src-snapshot-after.txt` 逐字节相同（`cmp` = before==after），859 文件；但**当前树**与 before 快照相比 852 同、7 差（§4.4）。

### 4.2 13 步结果（原始日志在 `build/device-track-batch31/`，记录员 grep 复核）

| step | 套件 | 用例数 | 结果（记录员 grep） | 判定 |
|---|---|---|---|---|
| 01 | 备份 | 34 | `OK (34 tests)` / 24.853 | **PASS**；批次 30 的 8 红逐条转绿 |
| 02 | 69→70 迁移 | 4 | `OK (4 tests)` / 5.365 | **PASS** |
| 03 | 迁移矩阵 1→70 | 15 | `OK (15 tests)` / 311.49 | **PASS** |
| 04 | painter | 24 | `OK (24 tests)` / 0.406 | **PASS** |
| 05 | 隔离 | 13 | `OK (13 tests)` / 60.779 | **PASS** |
| 06 | flags | 2 | `OK (2 tests)` / 0.021 | **PASS** |
| 07 | PDF | 16 | `OK (16 tests)` / 5.309 | **PASS**；native 230 |
| 08 | 一致性原型门 | 15 | `Tests run: 15, Failures: 1` / 0.827 | **FAIL**：N09-P06 旧等式 |
| 08b | 同包复跑 | 15 | `Tests run: 15, Failures: 1` / 0.802 | **FAIL**：逐位同值 → D 排除 |
| 09 | 提交/CAS | 9 | `OK (9 tests)` / 2.009 | **PASS**（旧版本） |
| 10 | 视觉仓库 | 4 | `OK (4 tests)` / 1.123 | **PASS**（旧版本） |
| 11 | 结构门 | 4 | `OK (4 tests)` / 1.341 | **PASS**（旧版本） |
| 12 | 裁剪器 | 9 | `OK (9 tests)` / 51.41 | **PASS** |
| 13 | KD-80 C2 比对 | — | `RESULT=PASS` / `EXIT=0` | **PASS**：90 表 / 2 视图 / 双向差集空 / identityHash 逐字同 |

- **红例归因（A，未放宽）**：P06 +1 px 的 `centroidDeltaPx` 是屏↔PDF 跨栅格欧氏距离（`VisualDrawOpsParityMetrics.kt` 的 `hypot`），不是同侧平移差；基线 |v|=0.32，形状上读数 = |v+(1,0)| = 1.2625；旧断言写死 1.0。N12（+4 px）用同一模型精确复现 3.56014654994809。门级判据已满足（F4_GEOMETRY、`red=true`、bboxDev=2、无 DESENSITIZED），产品源零改动。
- **KD-80**：80-C1 PASS（`OK (34 tests)`）、80-C2 PASS（`RESULT=PASS`）、80-C3 PARTIAL（设备 9/9；JVM 12 例本轨未执行）、80-C4 UNVERIFIED（34 例对视觉四表引用零断言）→ **关闭规则不满足，保持 open**。
- **9 类签名**：记录员对 13 份 `*-logcat.txt` 实跑 9 类签名 grep，全部 **0**；pid 生命周期（spawn 17=ready 17、kill 17）未复算，引自 E-10。

### 4.3 证据轨

- 落盘：`docs/research/evidence/2026-10-29-batch-31/` **15 个 `.md`**；文件字节合计 **85,170 B**（`find -printf '%s'` 求和），`du -sh` = **120K**；最大 `00-index.md` **13,325 B**；`build/device-track-batch31/*-logcat.txt` = **13** 份（含 step08b）。R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触。
- 索引自称"最大单文件 12,706 B / 12 份 logcat"，与记录员实测有小的时点差（§5 #13）。
- 脱敏：记录员实跑 R4 四条正则 → secrets 0、credential assignments 0、emails 0、home paths **1**（为 00-index §6 命令块里的正则模式串自身）；无真实密钥/凭据/邮箱/主目录。

### 4.4 设备源与现树不同源（本批最重的事实）

记录员用 `build/device-track-batch31/src-snapshot-before.txt`（构建前 859 文件 sha256）逐文件对比当前树：

- **852 相同 / 7 不同 / 0 缺失**。7 个文件全部属本批 visual/parity 面，mtime **07:53–08:03**（设备轨 06:20–06:54）：
  1. `core/data/src/androidTest/.../TutorVisualCommitRepositoryInstrumentedTest.kt`（08:03:35）
  2. `core/data/src/androidTest/.../VisualDrawOpsParityInstrumentedTest.kt`（08:01:48）
  3. `core/data/src/androidTest/.../VisualDrawOpsParityMetrics.kt`（07:53:50）
  4. `core/data/src/main/.../TutorVisualCommitRepository.kt`（08:00:45）
  5. `core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`（07:58:42）
  6. `core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`（07:59:39）
  7. `core/database/src/main/.../RoomStudyDatabase.kt`（07:58:23）
- 直接后果：
  - 现树 **提交 10 例**（设备 9）、**仓库 5 例**（设备 4）；新增的 `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite` 与 `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand` **从未上设备**。
  - 结构门文件与构建快照不同（mtime 07:59），现树含 `pinned` 读侧断言（`:207-209`）等新内容；设备跑的是旧内容。
  - parity 现树重写了 N09（F4_GEOMETRY 断言），设备跑的是旧 `centroid=1.000` 版本；设备日志的 `PARITY_GATE` 仍打印 `blindWindow=text-centroid(0,0.76)px`，现树打印 `direction-dependent`（测试 `:92-96`）——这是源漂移的直接凭据。
  - `RoomStudyDatabase` 现树新增了 state/draw digest 与 JSON 一致性 `require`（`:2174-2179`），设备的提交/仓库/结构门绿轮没有覆盖这两行。
- 因此：**11 套件的"绿"只对 06:20 的源成立；对当前树，parity 仍是未复跑的旧结果，提交/仓库/结构门的新内容未验证。** 按 T1 判据 §1.3-5 自己的规则，应判"构建早于源改动"，需重建重跑。

## 5. 复核发现与处置

> 口径：凡"设备未复跑"一律 UNVERIFIED。级别分布：**高 ×2、中 ×8、低 ×6**。

### 5.1 复核修复轮已处置项（记录员核实现树已生效）

| # | 复核点 | 现树处置（记录员实读） |
|---|---|---|
| R1 | N09 质心 1.000 许可不成立 | **已修**：撤掉伪等式，改断言"必须红 + `F4_GEOMETRY`"（`VisualDrawOpsParityInstrumentedTest.kt:315-326`），质心只进日志；KDoc 写明设备否证（`:299-314`） |
| R2 | 计划 §3 的"迁移 1→71"与 §2 矛盾 | **已修**：§3 改为"迁移 1→70；1→71 归批次 32"（计划 `:44`） |
| R3 | 契约 §8.1 保留语义漂移 | **已修**：改为"LIVE 永不淘汰，只淘汰未固定且 STAGING 的中间态"（`docs/current/visual-generation-contract.md:190`） |
| R4 | T1 判据含已被否证的 N09 期望/过期行锚 | **已修**：N09/N10/N11/N12 期望与行锚回改（判据 `:313-319`；锚 `:298/338/354/388` 与现树一致） |
| R5 | KD-80/契约 123 仍写"未出数" | **已修**：KD-80 Progress 回填 15 份证据与 80-C1/C2/C3/C4；契约 `:123` 写"设备 FAIL 14/15、绿轮 UNVERIFIED" |

### 5.2 本轮记录的独立发现

| # | 级别 | 位置 | 发现 | 状态 |
|---|---|---|---|---|
| 1 | **高** | `build/device-track-batch31/src-snapshot-before.txt` vs 现树；`step09/step10/step11/step08` 日志 | **设备绿轮与现树不同源**：852/859 相同、**7 文件不同**（mtime 07:53–08:03 vs 设备 06:20–06:54）；提交/仓库/结构门/parity 四套件覆盖其中文件，新增 2 例从未跑；`RoomStudyDatabase` 新增 digest `require` 未覆盖。设备"绿"不得套到当前树 | 已独立复核 |
| 2 | **高** | 计划 §3 `:44`；E-08/E-08b | **批次验收"设备全绿 + 一致门绿轮"未达成**：parity `Tests run: 15, Failures: 1`，N09-P07 未执行，`calibrated-r1` 绿轮 UNVERIFIED；红例归因 A 不等于通过 | 已独立复核 |
| 3 | 中 | `docs/research/2026-10-29-batch-31-device-criteria.md:31,711-730`；`build/device-track-batch31/dex-gate.sh` | **T1 计数冻结与 dex 门已被现树超越**：判据仍写提交 9 / 仓库 4，现树 10 / 5；dex 门未含 `snapshotDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite` 与 `commitEvictsAnUnreferencedStagingIntermediateThroughPlanToCommand`。若重跑，必须更新计数与 dex 门，否则"绿轮不覆盖本批修复" | 已独立复核 |
| 4 | 中 | `VisualDrawOpsParityInstrumentedTest.kt:315-326`；E-08 §5.2；校准 §4.6-2 | **N09-P07 无设备证据却新增了 F4_GEOMETRY 断言**：P07 的 +1 px 质心反解约 0.608 < 0.75，只能靠 bbox/bestShift；P07 基线 bboxDev=1，+1 后是否变 2 未实测。断言可满足性 UNVERIFIED | 已独立复核 |
| 5 | 中 | `docs/known-defects.md` KD-80 最新块；E-12 §6 | **KD-80 80-C4 零断言**：`grep -rln tutor_visual core/data/src/androidTest/.../backup/` = 0；34/34 只证归档/恢复不崩，不证视觉四表引用保持 → KD-80 保持 open（C1/C2 可核销） | 已独立复核 |
| 6 | 中 | `build/split-eval/device-5562/gate-report.json`、`app-split.json`；`tools/split_golden/README.md:86-89`；`tmp/aud08-db-5562/` | **AUD-08 真设备复跑不能支撑"设备库无分题数据"**：导出 `pages=[]`、gate count 0.2857 FAIL；WAL 0 B（05:38）晚于主库（05:26）；`source.db.sha256` 不覆盖 WAL（受控实验已复现）；仓内无 AUD-08 证据文件 | 已独立复核 |
| 7 | 中 | `TutorVisualCommitRepository.kt:167-177`；仓库测试 | **预算并发门只在进程内**：`checkpointPlanLock` 是实例级 Mutex，KDoc 自述"本库无跨进程写者"；多仓储实例/直接调 port 的写者仍可并发越过 4 MiB/32 MiB 门。无并发用例 | 已独立复核 |
| 8 | 中 | `TutorVisualDao.kt:169-189`；`port/TutorVisualPort.kt:61-68` | **evict SQL 不校验 `owner_request_id`/`created_at`**：会删掉另一个仍在飞的请求刚 stage 的 STAGING 行；port 文档把"STAGING 回收"限定为"租约过期且无引用"，两者不一致（被删方 commit 可自愈，但 staged id 会短暂失效） | 已独立复核 |
| 9 | 中 | `VisualDrawOpsParityMetrics.kt:191`；校准 `:24,:253` | **代码 KDoc 仍写"下余量 0.250"**：该数值已被设备否证并在校准文档撤回（下侧锚改为 N11 1.1665 / 余 0.4165）；同段上文已写"0.75 的依据是噪声+N11"，自相矛盾 | 已独立复核 |
| 10 | 中 | `TutorVisualSnapshotMigration_69_70InstrumentedTest.kt:408-447,601-690` | **"旧行零改动"测试覆盖不完整**：KDoc 声称资产/题/修订/修订附图/会话/消息/消息附图全部原样读回，`assertOldV69RowsPreserved` 只断言 message、problem_revision、asset 三行（seed 8 行） | 已独立复核 |
| 11 | 低 | `TutorVisualCommitRepositoryInstrumentedTest.kt:29`；`TutorVisualCommitInstrumentedTest.kt:29-30` | **测试 KDoc 仍写 `connectedDebugAndroidTest`**，与计划硬约束（只 assemble+install+am instrument）冲突；后者还写"本文件提交时是未执行状态"，与 E-09 实跑 9 例不符（现树 10 例仍未跑） | 已独立复核 |
| 12 | 低 | `git check-ignore` / `git status --porcelain -- tmp/` | **`tmp/aud08-db-5562` 未被 gitignore**（`git status` 显示 `?? tmp/`），12.7 MB 设备库 + WAL/SHM 处于未跟踪；T6 摘要称"gitignored"不准确。提交时必须显式排除 | 已独立复核 |
| 13 | 低 | `evidence/2026-10-29-batch-31/00-index.md:8,63` | **证据索引数字有时点差**：最大单文件写 12,706 B，实测 13,325 B；写 12 份 logcat，实测 13 份（含 step08b）；体积 120 KiB 是 `du` 口径，文件字节合计 85,170 B。不影响合规 | 已独立复核 |
| 14 | 低 | T4 轨摘要（工单）vs 现树 | **T4 摘要漂移**：摘要仍描述 N09 断言 `centroid=1.000±1e-6 且 >0.75`，现树已改为 F4_GEOMETRY、质心只进日志。读摘要会误判门形态 | 已独立复核 |
| 15 | 低 | `docs/current/visual-generation-contract.md` 状态；T3 摘要 | **热区口径冲突**：T3 称该文件"他线 M 热区未改"，T5 实际编辑（计划任务 8 要求）；T5 已注明开工即 M、整文件纳入，不判越权，但两轨对同一文件的禁改判断不一致 | 已独立复核 |
| 16 | 低 | `docs/research/algorithm-version-ledger.md`；`StudyDatabase.kt:139` | **schema 71 未交付**（按裁定）：无 70→71 迁移、无 71.json、常量未动；计划 §2/§3 已对齐 1→70。不是缺陷，但台账/路线必须写"推迟批次 32"而不是"待补" | 已独立复核 |

**正向结论（已核实，不计入发现）**：

- 消息 link 初次插入现已含 `program_id` 谓词（`TutorVisualDao.kt:305-310`），与 CAS、题面 pin 同形。
- `ensureTutorVisualSnapshot` 复派生 `snapshotId` 并校验 state/draw digest 与 JSON 一致（`RoomStudyDatabase.kt:2174-2186`），关闭"身份与内容脱钩"。
- 淘汰 SQL 以 `status = STAGING` 为正向条件，LIVE/INITIAL 不删（`TutorVisualDao.kt:169-189`）。
- 题面 STAGING/跨 program、消息 link 跨 program/STAGING、demote/evict 在提交事务内执行均有设备用例（结构门 4 例，E-09）。
- KD-80 80-C2 清单比对 `RESULT=PASS`（90/90/90、双向差集空、identityHash 逐字同，step13 原始输出）。
- 迁移矩阵 1→70 全过（`OK (15 tests)`，循环 1..70），`70.json` 与 `SmbkSelectionPlan` 常量一致。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 仓内无本轮 gate 日志；记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | 记录员未复跑 |
| 强门（第 2 轮） | **exit=0**（工单给定） | androidTest 编译 + assemble |
| 设备 备份 34 | **PASS `OK (34 tests)`** | 批次 30 的 8 红逐条转绿 |
| 设备 69→70 4 | **PASS `OK (4 tests)`** | 结构读数断言 |
| 设备 迁移矩阵 15 | **PASS `OK (15 tests)`** | 1→70 |
| 设备 painter 24 | **PASS** | 旧 flags 红未复现 |
| 设备 隔离 13 | **PASS** | 17↔17（E-10） |
| 设备 flags 2 | **PASS** | KD-78 ④ |
| 设备 PDF 16 | **PASS** | native 230 |
| 设备 parity 15 | **FAIL 1/15** | N09-P06 旧等式；A 类；绿轮 UNVERIFIED |
| 设备 提交 9 / 仓库 4 / 结构门 4 | **PASS（旧源）** | 现树 10 / 5 / 4，新增未跑 |
| 设备 裁剪器 9 | **PASS** | KD-80 80-C3 设备半边 |
| KD-80 80-C2 | **PASS `RESULT=PASS`** | 90 表/2 视图/差集空 |
| 9 类签名 | **13 份 logcat 全 0** | 只证无崩溃，不代表套件全绿 |
| schema 71 / 迁移 1→71 | **未执行（按裁定）** | 批次 32 |
| 生产 release 签名 / 真实 Provider | **UNVERIFIED** | 本批未跑；JS 不广告 |

**记录员保留**：三条门禁 `exit=0` 无本轮日志落仓，记录员未运行 Gradle；设备数字均有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；现树与设备 APK 不同源（7 文件），不得混用。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面（本批最重）**

1. **parity 绿轮不存在**：套件 FAIL 1/15；现树 N09 已重写但未上设备；N09-P07 从未执行。
2. **四套件与现树不同源**：提交/仓库/结构门/parity 的 7 个文件在设备轨后改动；新增 2 例（digest 负向、plan→command evict）未跑。
3. **迁移 1→70 已过，1→71 未做**：schema 71 按裁定推迟批次 32；不得写"已升 71"。
4. **KD-80 80-C4 零断言**：34 例不覆盖视觉四表引用保持；KD-80 保持 open。
5. **AUD-08**：设备数据度量 UNVERIFIED（导出 0 页、门 FAIL）；WAL 未纳入快照 sha；仓内无证据文件。
6. **D3 四缺口**：ENOSPC、静默短写（设备面）、页数回读不符、`renameTo` 失败仍无设备用例（KD-79 残留）。
7. **冷恢复设备面 0 用例**（批次 30 遗留，本批未补）。
8. **JVM 面**：`VisualCheckpointRetentionTest` 17/0、`SmbkSelectionPlanTest` 12/0、`QuickJsVisualExecutionEngineJvmTest` 3/0 有 `build/` 下 JUnit XML（记录员实读），但不是仓内证据，命令归属 UNVERIFIED。

**代码 / 文档残留**

9. **N09-P07 断言可满足性未验证**（§5 #4）。
10. **checkpoint 预算只进程内串行**（§5 #7）；**evict 不校验 owner/租约**（§5 #8）。
11. **迁移"旧行零改动"断言只覆盖 3/8 行**（§5 #10）。
12. **`Metrics.kt:191` 的"下余量 0.250"** 与已撤回结论矛盾（§5 #9）。
13. **测试 KDoc 的 `connectedDebugAndroidTest` / "未执行"** 未改（§5 #11）。
14. **KD-71/72/73/77 全部 open**：KD-71 无新设备执行；KD-72 锁窗口不满足（14 锁/跟踪 0）；KD-73 无扫描脚本/结果；KD-77 无 API 23 修复后 APK 冒烟。

**环境 / 口径**

15. **一致性门仍是原型门**：`product-gate=no`；不得引用为产品/发版结论。
16. **arm64/16 KB、真机、低内存/高负载、API 23、跨 fontScale** 未覆盖；本批设备仅 x86_64/API 34/4 KB。
17. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
18. **4E.2 MVA 仍不成立**：本批是持久化语义收口与原型门标定，不等于双后端/导出/质量门就绪。
19. **热区在飞未触碰**：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、Root/SecondaryScreens、docs/current 其余 M、docs/research 他线未跟踪记录等。
20. **`tmp/` 未忽略**：`tmp/aud08-db-5562/`（12.7 MB 设备库）与 `tmp/wal-*`、`tmp/smbk-probe/` 均未跟踪，提交时必须排除（§5 #12）。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；若引用"设备绿"，先按 §4.4 重建重跑（当前树与设备 APK 不同源）；热区、14 个 `*.lockfile` 与 `tmp/`、`build/` 一律排除。按 ≤6 路径/块：

- **块 1（T2 迁移测试）**：`core/database/src/androidTest/.../TutorVisualSnapshotMigration_69_70InstrumentedTest.kt`（改；断言结构读数化）
- **块 2（T3 DB 主代码）**：`core/database/.../dao/TutorVisualDao.kt`（改）、`core/database/.../RoomStudyDatabase.kt`（改；**整文件纳入**）、`core/database/.../TutorVisualCommit.kt`（改）、`core/database/.../port/TutorVisualPort.kt`（改）、`core/database/src/test/.../KernelWave0SchemaContractTest.kt`（改；**批次 30 未提交的 69→70 字面量，整文件纳入并注明**）
- **块 3（T3 data 主代码 + JVM）**：`core/data/.../visual/VisualCheckpointRetention.kt`（改）、`core/data/.../visual/TutorVisualCommitRepository.kt`（改；**整文件纳入**）、`core/data/src/test/.../visual/VisualCheckpointRetentionTest.kt`（改）
- **块 4（T3 仪器化）**：`core/database/src/androidTest/.../TutorVisualCommitInstrumentedTest.kt`（改）、`core/data/src/androidTest/.../visual/TutorVisualCommitRepositoryInstrumentedTest.kt`（改）、`core/database/src/androidTest/.../TutorVisualStructureGatesInstrumentedTest.kt`（**新，未跟踪必须显式 add**）
- **块 5（T4 标定）**：`core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt`（改；**整文件纳入**）、`VisualDrawOpsParityInstrumentedTest.kt`（改；**整文件纳入**）
- **块 6（研究/判据/记录）**：`docs/research/2026-10-29-snapshot-identity.md`、`2026-10-29-parity-calibration.md`、`2026-10-29-batch-31-baseline.md`、`2026-10-29-batch-31-device-criteria.md`、`2026-10-29-batch-31-t12-doc-consistency.md`、`2026-10-29-batch-31-record.md`（均新）
- **块 7（证据前半）**：`docs/research/evidence/2026-10-29-batch-31/00-index.md`、`step00-environment.md`、`step01-backup.md`、`step02-migration-69-70.md`、`step03-migration-matrix.md`、`step04-painter.md`
- **块 8（证据中段）**：`step05-isolation.md`、`step06-flags.md`、`step07-pdf.md`、`step08-parity.md`、`step08b-parity-rerun.md`、`step09-persistence-structure.md`
- **块 9（证据后段 + 台账）**：`step10-signatures.md`、`step11-trimmer.md`、`step12-kd80.md`、`docs/known-defects.md`（改；**整文件纳入并注明**）、`docs/current/visual-generation-contract.md`（改；**整文件纳入并注明他线 M**）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改；**整文件纳入**）
- **块 10（路线/决策）**：`docs/REFACTOR-MASTER-LINE.md`（改；批次 31 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改；批次 31 裁定节）

> 本记录**未**改 `docs/research/algorithm-version-ledger.md`：本批无 schema bump（仍 70），按该文件 §4 的"每次 bump 同一提交登记"规则无需新增行；若主会话要求补"批次 31 维持 70"的说明，可另加一行，但不得写成"71 已落"。

**明确排除（他线在飞/不安全）**：全部 `?? */gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`；core/data 他线 M（mistake/study/knowledge/model/capture/backup 测试等）；core/database 开工即脏的其余 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`PendingCaptureDao`、`TutorConversationDao`、`CleanRedrawTaskPort`、LibraryFts 三测）；`VisualDrawOpsParserTest.kt`（他线改）；app 全部 M/??；docs/current 其余 M；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；**`tmp/**`（含 `tmp/aud08-db-5562/` 12.7 MB 设备库与 WAL/SHM）**。

**提交说明**：建议 10 块、每块 ≤6 路径；整文件纳入的 M（`RoomStudyDatabase`、`TutorVisualCommitRepository`、`KernelWave0SchemaContractTest`、`VisualDrawOpsParityMetrics`、`VisualDrawOpsParityInstrumentedTest`、`known-defects`、`visual-generation-contract`、`4e2 checklist`）在提交说明中注明"整文件纳入，含本批/他线改动"；新增结构门测试必须显式 `git add`；提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1 551 行、R2 381 行落盘；S1 与 71 推迟裁定、触发器/复合外键取舍、`calibrated-r1` 与硬护栏均与落码对应；未验证项如实。
- **实施**：T1 判据 901 行；T2 结构断言 + 4 例设备绿；T3 无 schema 变更的门（status 轴、program 谓词、STAGING 拒绝、snapshotId/digest 复派生、进程内预算锁）落码；T4 `calibrated-r1` + N09–N12；T5 文档回改；T6 AUD-08 机制复跑 + KD-72/77 零动作；T7 分支 D。
- **集成**：三处接线静态成立；补 1 处 T1 判据漏项；生产装配仍为零。
- **设备**：13 步，11 绿 / 1 红；红例 A 类、未放宽；9 类签名 13 份全 0；PDF 230；隔离 17↔17。
- **门禁**：单测门/修复环/强门 `exit=0`（工单给定）；记录员未复跑。
- **本批不声称全绿/全就绪**：parity 红、`calibrated-r1` 绿轮 UNVERIFIED；设备与现树 7 文件不同源、新增 2 例未跑；KD-80 C4 零断言 + C3 PARTIAL → open；KD-71/72/73/77 open；schema 71 批次 32；AUD-08 设备数据 UNVERIFIED；D3 四缺口/冷恢复设备面/API 23/arm64/生产签名/真实 Provider 均 UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告；4E.2 MVA 不成立。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 59d4a03f
git rev-parse --short origin/main                            # 59d4a03f（本地跟踪引用，未 fetch）
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain | wc -l                               # 711
git status --porcelain | grep -c '^ M'                       # 232
git status --porcelain | grep -c '^??'                       # 479
git diff --cached --name-only | wc -l                        # 0

# 热区（脏行数）
for p in feature/tutor feature/library feature/review feature/capture feature/profile \
         core/ui core/markdown core/export core/domain; do
  printf '%-22s %s\n' "$p" "$(git status --porcelain -- $p | wc -l)"
done                                                          # 59/43/12/16/4/35/1/6/5

# 版本与 schema
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt   # :139 = 70
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/ | grep -c '\.json$'   # 70
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json                # No such file
git ls-files core/database/schemas | wc -l                                                              # 70
python -c "import json;d=json.load(open('core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/70.json',encoding='utf-8'))['database'];print(d['version'],d['identityHash'],len(d['entities']),[v['viewName'] for v in d['views']])"
# 70 11a8a701bae767a8a3c5bc810e4743a4 90 ['library_catalog', 'knowledge_question_lattice']

# 测试计数（grep -c '@Test'）
# 备份 34 / 69→70 4 / 矩阵 15 / painter 24 / 隔离 13 / flags 2 / PDF 16 / parity 15
# 提交 10 / 仓库 5 / 结构门 4 / 裁剪器 9

# 设备日志
for f in build/device-track-batch31/step*.log; do echo "== $(basename $f)"; grep -E "OK \(|Tests run:|Time:" "$f" | tail -3; done
# 34/4/15/24/13/2/16/15-1/15-1/9/4/4/9

# 设备构建源快照 vs 现树（关键）
cmp -s build/device-track-batch31/src-snapshot-before.txt build/device-track-batch31/src-snapshot-after.txt && echo before==after   # 相同
# 逐文件 sha256 比对：852 相同 / 7 不同 / 0 缺失（7 个见 §4.4）

# 关键源码锚点（实读）
grep -n "WHERE snapshot_id = :snapshotId" -A 3 core/database/.../dao/TutorVisualDao.kt    # :305-310 含 status + program_id
grep -n "status = :stagingStatus" core/database/.../dao/TutorVisualDao.kt                  # evict/对账
grep -n "deriveSnapshotId" core/database/.../RoomStudyDatabase.kt                          # :2180
grep -n "checkpointPlanLock" core/data/.../visual/TutorVisualCommitRepository.kt           # :167-177
grep -n "maxCentroidDeltaPx = \|minMassRatio = \|maxMassRatio = " core/data/src/androidTest/.../VisualDrawOpsParityMetrics.kt
# 251/252/253（0.75 / 0.93 / 1.07）；THRESHOLD_VERSION :242；"下余量 0.250" :191
grep -n "fun n09\|fun n10\|fun n11\|fun n12" core/data/src/androidTest/.../VisualDrawOpsParityInstrumentedTest.kt   # 298/338/354/388

# 证据与脱敏
find docs/research/evidence/2026-10-29-batch-31 -name '*.md' | wc -l                 # 15
find docs/research/evidence/2026-10-29-batch-31 -name '*.md' -printf '%s\n' | awk '{s+=$1} END{print s}'  # 85170
du -sh docs/research/evidence/2026-10-29-batch-31                                   # 120K
ls build/device-track-batch31/*-logcat.txt | wc -l                                   # 13
for f in build/device-track-batch31/step*-logcat.txt; do printf "%s " "$(basename $f)"; \
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f" || true; done
# 13 份逐份 0
# 四条 R4 正则：secrets 0 / credential assignments 0 / emails 0 / home paths 1（索引自身正则文本）
# 80-C4：grep -rln "tutor_visual" core/data/src/androidTest/.../backup/ | wc -l → 0

# AUD-08（只读复算）
python -c "import json;d=json.load(open('build/split-eval/device-5562/gate-report.json'));print(d['gate'])"
# state=FAIL, count=0.2857142857142857 < 0.9, fragment=0.0
python -c "import json;d=json.load(open('build/split-eval/device-5562/app-split.json'));print(len(d['pages']),d['source']['db']['sha256'])"
# 0 eb4d327147afa075ce28d143be5bc5aaf791eb59d202ca3ce8697b6351d8f4dc
ls -l tmp/aud08-db-5562/                                                             # 主库 12775424 B/05:26；-wal 0 B/05:38
git check-ignore -v tmp/aud08-db-5562/smart-mistake-book.db                          # 无输出（未忽略）；git status 显示 ?? tmp/
python - <<'PY'   # 只读打开设备库快照
import sqlite3
c=sqlite3.connect('file:tmp/aud08-db-5562/smart-mistake-book.db?mode=ro',uri=True)
for t in ('split_import_job','split_import_question','batch_import_page'):
    print(t, c.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0])
PY
# split_import_job 0 / split_import_question 0 / batch_import_page 0

# KD-72
find . -name "*.lockfile" -not -path "./build/*" | wc -l                             # 14
git status --porcelain -- settings.gradle.kts core/ui/build.gradle.kts feature/tutor/build.gradle.kts core/export/build.gradle.kts .gitignore core/markdown/build.gradle.kts  # 6/6 脏

# JVM XML（build/ 旁证，非仓内证据）
# SmbkSelectionPlanTest tests=12 failures=0；VisualCheckpointRetentionTest tests=17 failures=0；QuickJsVisualExecutionEngineJvmTest tests=3 failures=0

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 30 记录的结构对应

| 批次 30 节 | 本批对应 | 差异 |
|---|---|---|
| §2 两研究线 | §2 两研究线 | 本批为快照身份/数据库约束 + 一致门标定；PDF 写安全转批次 30 已关 |
| §3 十实施轨 + 集成 | §3 七实施轨 + 集成 | 少 PDF/冷恢复轨，多 T6/T7 条件轨与复核修复轮 |
| §4 设备与证据 | §4 设备与证据 | 13 步；11 绿 1 红；新增"设备源与现树 7 文件差"小节 |
| §5 复核发现 | §5 复核发现 | 5 项复核轮处置 + 16 项独立发现 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
