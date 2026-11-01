# 批次 34 记录（2026-10-29）：双后端装配起步 + KD-80 收尾 + AUD-08 溯源修复

> 记录时点：2026-10-29（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` **只读**核对计划、基线、两份研究文档、T1 判据、`docs/research/evidence/2026-10-29-batch-34/` 十一份 `.md`、`build/device-track-batch34/` 十五套件原始日志与源码锚点后写入。**记录员未运行 Gradle、未安装/执行设备用例（`am instrument`）、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码。** 本轮执行的是只读命令：`git status/rev-parse/diff`、`grep`、`stat`、`find`、`python`（读 `71.json`、S=71 清单门、`pytest tools/tests/test_split_gate.py`）、`adb devices` / `adb shell getprop`（只读），命令与输出见 §10；另写了本记录与四份台账（§9）。
> 计划：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md`（35 行，已完整读；含 R-04 停项登记的复核修复轮补记）。
> 基线：`docs/research/2026-10-29-batch-34-baseline.md`。
> 研究：R1 `docs/research/2026-10-29-dual-backend-wiring.md`（375 行）、R2 `docs/research/2026-10-29-snapshot-identity-wal-and-r8.md`（708 行）。
> 判据：`docs/research/2026-10-29-batch-34-device-criteria.md`（**784 行**）。
> 设备证据：`docs/research/evidence/2026-10-29-batch-34/`（**11 份**；索引自述 68,040 B，**记录时点 `find -printf '%s'` 实测 73,535 B**——修复轮追加 §8/§5-10 后未回写索引体积）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门（修复环后）`exit=0`；修复环第 1 轮复核完成（逐条处置见 §5）。记录员未复跑任何 Gradle 任务；仓内无批次 34 的 gate 日志目录，只有 `build/test-results/` 的 JUnit XML 旁证（§6）。
>
> **本记录不声称"批次全绿"或"4E.2 就绪"**：15 个设备套件在 18:43 重建的四包上全绿（§4），但**设备构建源快照的门集合不含 `core/model/src`**（§4.4），且复核修复轮在 20:57–21:06 新增/修改的 4 个测试与 3 个产品文件**均不在该绿轮 APK 内**（§4.5）。A-01/A-02/A-03 的 JVM 只有 `build/` XML 旁证（21:15:02，0 失败），**无仓内命令日志**；KD-81 仍 open（`-shm` 字面项与设备复跑未满足）；KD-72/73/77 零动作保持 open。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 9 项**（§1）：KD-80 收尾、补跑新测试源、KD-81 修复、A-01 双后端装配、A-02/A-03 工具环与 schema、R-04 解除路径、KD-72/73/77、文档/台账、热区条件轨。
- **实际执行面**：两研究线（4E 双后端装配 / 快照身份 WAL + R-04）+ T1 判据 + T2 rowCounts + T3 A-01 + T4 A-02/A-03 + T5 KD-81 + T6 R-04 与 KD-72/73/77 + T7 文档 + T8 条件轨 + 集成交接 + 设备轨（`emulator-5562`，串行 15 套件）+ 证据 11 份 + **复核修复轮**（20:57–21:15 修真实缺陷并补 4 例设备用例）+ 本记录与四份台账同步。
- **与计划的六处已知偏差**：
  1. **任务 3 的 `-shm` 字面项**：计划/KD-81 原文要求身份覆盖 `-wal`/`-shm`（或 checkpoint 证据）。实现按 R2 C3 显式**排除 `-shm`**（只读导出会重建/改写它，纳入即自指），只覆盖 db+wal。原始缺陷（同主库不同 WAL 同身份）已消灭，但关闭条件的字面项未满足 → KD-81 保持 open、待主会话裁定（§3.5）。
  2. **A-01 的本地 attempt 序号**：R1 建议 `COALESCE(MAX+1)`；实现改为**负数区间**（`-1,-2,…`，`reservation_id=NULL`），与远程正序在 `(operation_id, attempt_ordinal)` 唯一索引上不相交。理由与原建议的"避免静默丢审计行"一致，形态更好（§2.1/§3.3）。
  3. **R-04**：`app/build.gradle.kts` 已有属性门 + `testProguardFiles`（`git diff` +14 行）并新增 `app/r04-test-keep.pro`，但**未跑** `-Psmbk.testBuildType=release`；release 测试轨 **UNVERIFIED**。计划 §1 第 6 项已按复核修复轮回改为"走停项登记分支"（§3.6/§4.6）。
  4. **KD-80 裁决被设备轨取代**：T7 在 18:14 的"保持 open、唯一缺口是 80-C2 设备侧"只对那时点成立；18:42–19:02 的设备轨取得 `OK (2 tests)` 后，台账已回改为 **closed（2026-10-29 批次 34）**。记录员复核后认同（§4.5）。
  5. **修复轮改了热区文件 `RoomTutorToolRunner.kt`**：T4 原称该文件"他线 M 禁改、由轮次层代偿"，但 20:58 的修复轮在执行器层补了 A-02 第二道门（+43/−7，且 diff 混有他线的 `visualRole` 改动）。这是**流程偏差**，必须在提交时整文件纳入并注明（§5.3 #D）。
  6. **设备源快照门集合缺 `core/model/src`**：864 文件清单只含 `app/src core/data/src core/database/src`，而 A-02/A-03 的改动在 `core/model/src`；构建后核对"无新改动"成立，但门本身不覆盖（§4.4）。
- **不做（全部未做）**：core/export 集成与 PDF `/AIGC`（KD-75 热区）；JS 广告（`availableMethods` 未改）；真实 Provider（无凭据）；锁文件生成/提交（KD-72）；AIDL 之外的接口变更；release/R8 端到端（R-04）。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature 五模块、core/ui、core/markdown、core/export、core/domain、`settings.gradle.kts`、`.gitignore`、app Root/SecondaryScreens、docs/current 其余 M、tools/kb、knowledge-production。**例外**：修复轮改了 `RoomTutorToolRunner.kt`（核心 data 热区，见 §5.3 #D），须整文件纳入。
2. **能力快照 / prompt / 广告文本零改动**：`FigureGenerationCapabilities.IMAGE_MODEL_ONLY` 未改（`FigureGeneration.kt:160-161`，`git diff` 对该段 0 命中）；`FigurePromptAssembler.kt:40/:108` 仍 fail-closed；请求 schema 默认仍 `setOf(IMAGE_MODEL)`。
3. **DB schema 仍 71**；请求 schema（`ModelTaskRequest`）仍 18；`method.enum` 属模型可见工具声明，不进序列化/指纹（§2.1）。
4. 一致门仍是**原型门**，不得引用为产品/发版结论；真实 Provider / 模型质量 UNVERIFIED；AIDL 只以 Gradle 任务验证。
5. 设备只 `assemble*` + `adb install -r -t` + `am instrument`，禁 `connectedAndroidTest`；同一落点多个任务合并给一个拥有者。
6. 不许为变绿放宽断言；A-03 的广告做成**能力参数**（默认关），不得改成全局可变开关。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`db792fa8`；分支 `main`；`origin/main` 本地跟踪引用同为 `db792fa8`（**未 `git fetch`**，远端实时 hash UNVERIFIED）。
- **工作树**：**727 条 = 246 M + 481 ??**（`git status --porcelain` 记录时点实测；批次开工基线 689 → 设备证据时点 721 → 复核修复轮后 727）。
- **DB schema**：`STUDY_DATABASE_VERSION = 71`（`core/database/.../StudyDatabase.kt:139`）；`71.json` **378,961 B**、`version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、**90 实体 / 2 视图**（python 实读）；schema 目录 **71 个 json**、git 跟踪 **71**；无 `*71_72*` 迁移文件。
- **请求 schema**：`TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`core/model/.../ModelTasks.kt:546`）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、feature/review **12**、feature/capture **16**、feature/profile **4**（feature 合计 134）、core/ui **35**、core/markdown **1**、core/export **6**、core/domain **5**、app **30**、docs/current **7**、knowledge-production **12**、tools/kb **0**；`settings.gradle.kts`、`.gitignore` 均 M。本批对上述路径零写入（`RoomTutorToolRunner` 例外见 §5.3 #D）。
- **锁文件**：`git ls-files '*lockfile'` = **0**；git 可见未跟踪 **13**；盘上（maxdepth 3）**14**（多出 `core/markdown/gradle.lockfile`）；`benchmark/gradle.lockfile` 不存在。
- **KD 台账**：`docs/known-defects.md` **81 条**，尾条 KD-81。

### 1.4 本批增量点名（按现树实读）

- **A-01**：`core/data/.../visual/DefaultFigureGenerationCoordinator.kt`（参数 `:153/:158`、JS 分支 `:211`、`generateLocalJs` `:248-356`、本地 attempt `:589-605`、JS 指纹分支 `:698-707`、工厂 `:790-806`）、`VisualFrameInputs.kt`（新）、`FigureGenerationAuditRecorder.kt`（`:52`）、`RoomFigureGenerationAuditRecorder.kt`（`:74/:82`）。
- **A-01 持久层**：`core/database/.../dao/FigureGenerationDao.kt`（`:109-113`、`:125-145`）、`port/FigureGenerationRecorderPort.kt`（`:58`）、`RoomStudyDatabase.kt`（`:1387`）、`entity/FigureGenerationEntities.kt`（KDoc）。
- **A-02/A-03**：`core/model/.../TutorToolLoop.kt`（`:352-380`、`:431-441`）、`FigureGenerationPolicy.kt`（`:148`、`:157`、`:187/:192/:213`）、`core/data/.../model/OpenAiModelProtocol.kt`（`:130`、`:378-389`、`:486`、`:571-590`、`:742-744`）、`RoomModelTaskToolRounds.kt`（`:53-63`、`:118-131`）、`RoomTutorToolRunner.kt`（`:948-960`，热区）。
- **A-08 指纹**：`core/model/.../FigureGeneration.kt:646` `figureJsBasisFingerprint`。
- **对账修复**：`core/database/.../dao/FigureGenerationReconcileDao.kt`（`:115-128`、`:168-173`）、`FigureGenerationReconciler.kt`（KDoc `:31-32`）。
- **T5**：`tools/split_golden/export_split_eval.py`（`:78`、`:101-135`、`:213`、`:222`、`:290-300`、`:358-359`）、`tools/tests/test_split_gate.py`（新增 7 例，现 **37**）、`tools/split_golden/README.md`（`:87-104`）。
- **R-04**：`app/build.gradle.kts`（`+14`）、`app/r04-test-keep.pro`（新，36 行）。
- **测试计数（记录员 `grep -c '@Test'` 实跑）**：设备侧备份 **34**、rowCounts **2**、结构门 **5**、裁剪器 **9**、C4 **5**、迁移 **15/4/3**、提交 **12**、仓库 **12**、parity **16**、painter **24**、PDF **16**、隔离 **13**、flags **2**；JVM 协调器 **17**、记录器 **7**、Room 记录器 **4**、policy **22**、双解析 **14**、轮次门 **16**、proposal **11**。
- **零改动**：schema/迁移、`settings.gradle.kts`、`.gitignore`、AAR/stdlib、请求 schema 版本、`availableMethods`、生产装配（仍只传 3 参）。

## 2. 两研究线

### 2.1 R1 4E 双后端装配（`docs/research/2026-10-29-dual-backend-wiring.md`，375 行）

- **性质（`:1-7`）**：只读源码 + `gh api` + 官方文档；零 Gradle/零设备/零 git 写；"现状"来自本轮实读。
- **一句话结论（`:9-14`）**：A-01 最小切口 = 给 `DefaultFigureGenerationCoordinator` 加两个**默认 null** 的构造参数（`visualEngine` + 宿主帧输入），新增 JS 分支且**完全不经过 `ledger.reserve`**；A-01 的"记 attempt"半场被 `(operation_id, attempt_ordinal)` 唯一索引 + `insertAttempt ABORT` 挡住，必须由持久层分配序号。A-02/A-03 的关键不是解析层，而是 **`TutorToolCall` 构造器 + `toFigureGenerationProposal` + `RoomTutorToolRunner` 三处**。
- **A-01 切口与牵动面（§1.1–§1.4）**：app 装配点**不改**（默认 null 即生产 UNAVAILABLE，`:122`）；`core/domain/FigureGenerationCoordinator.kt:19-20` 的 KDoc 落地后变旧但属热区 → 停项登记；`program` 由 policy 保证非空（`:195-211`）。
- **绝不调用 `ledger.reserve` 的理由（§1.1 末）**：`RemoteModelDispatchRequest.configurationFingerprint` 非空，JS 无端点/模型配置；伪造指纹并给 `dispatch_count` +1 正是契约 `:163` 禁止的"本地执行占远程派发"。
- **policy 预算门（§1.3）**：`FigureGenerationBudget.allowsOneRemoteDispatch()` 只表达**远程**派发，余额 0 时纯本地 JS 会被误拒 → 建议加 `method == IMAGE_MODEL &&`。
- **A-02 三处（§2.2）**：`TutorToolCall.init` 一律拒 program；`toFigureGenerationProposal` 丢 program 且**无条件**填图像载荷（会让 JS 必然 `JS_PAYLOAD_INVALID`）；`RoomTutorToolRunner.generateFigure` 不看方法（会把 JS 当真图像模型出网）。三处只改一处等于没接通。
- **A-03 兼容性（§3.1–§3.3）**：请求 schema 有两层——模型可见工具声明 JSON（每次现生成、不进库）与持久化 `ModelTaskRequest`。加 JS 对指纹零影响、不升 18、无需迁移；**生产广告必须能力参数化**（默认 `setOf(IMAGE_MODEL)`），否则与"零广告"硬约束冲突。
- **attempt 与 0 派发（§4）**：本地序号若恒定 0/-1，换方法/重试必撞唯一索引 → 审计静默丢失；建议 `@Transaction` 内分配（本仓有 `AgentPendingRequestDao.kt:133-135` 先例）。"0 远程派发"四层可观测：协调器 fake、HTTP `MockWebServer`、审计 0/0、设备计数（第四层未落）。
- **GitHub 评分（§5，`gh api` 实测）**：Bazel `SpawnStrategy` 4.5/5（canExec + exec + 失败重试条目，形态最贴）、onnxruntime 3/5、wgpu 2.5/5（许可两来源不一致，待验证）、FFmpeg 1.5/5、quickjs-kt 为依赖面（Apache-2.0 wrapper，QuickJS 本体许可未核）。**采纳：不引任何外部依赖，只借"能力判定选后端 + 一次执行一条 attempt"两条语义。**
- **落码对应与偏差**：R1 §4.2 建议 `COALESCE(MAX+1)`，实现改为**负序号**（`FigureGenerationDao.kt:125-145`）；R1 §2.2 说 runner 要改或停项，修复轮实际改了（热区，§5.3 #D）；R1 §4.3 的"计数被覆盖成 0"缺陷仍**未修**（本批只保证 JS-only 写 0/0，未改 `finishOperation` 的绝对覆盖语义）。

### 2.2 R2 快照身份（WAL 面）与 R-04 `testProguardFiles`（`docs/research/2026-10-29-snapshot-identity-wal-and-r8.md`，708 行）

- **性质（`:1-15`）**：研究/设计文档；零 Gradle/零设备/零 git 写；SQLite 结论来自本会话探针（桌面 Python 3.13 / SQLite 3.50.4 / Windows 文件库）与仓内工具原样执行；AGP 结论来自本地 9.3.0 `javap` + 官方 9.3 DSL 页。
- **C1 复现（§1.2，实测 audit3）**：同主库（sha `16c9db64…`）+ 不同 WAL → 两次导出均 `exit=0`，一次 1 页 1 框、一次 1 页 2 框，`source.db.sha256` **逐字相同**。
- **C2 混合导出（§1.3 P3 + 代码）**：`collect()` 两条语句各自 autocommit，中间 WAL 变化会给出**混合状态**。
- **C3 `-shm` 不进身份（实测 P2b）**：只读导出本身改写 `-shm`（`4b42c88d…`→`0a7d520d…`），主库/WAL 不变；干净关闭会删除它。
- **C4 源库 `wal_checkpoint(TRUNCATE)+锁` 三项否决（§2.1）**：需写连接（违反只读红线）；有读者时返回 `(1,1,0)` 且 WAL 仍 4152 B；即使成功也只是产物身份不是内容身份（P9：同内容不同 `page_size`/`user_version` 字节不同）。
- **C5 推荐组合（§2.4）**：读事务（一致性）+ 输入清单（db+wal 的 sha 进身份）+ 导出后比对（变化即 `exit 2`）+ 载荷 sha（内容身份），**四件同批**。
- **C6–C8 R-04（§5）**：AGP 9.3 官方长句逐字存在（"Test code needs to be processed to apply the same obfuscation as was done to main code."）；`javap` 确认 `VariantDimension` 有 `getTestProguardFiles/testProguardFile/testProguardFiles` 且 `ProguardFileType.TEST` 被引用 ⇒ **可测 release 变体不必以 `-dontobfuscate` 为代价**。具体阻塞点是签名门（`validateReleaseSigning` 挂在任何含 `Release` 的 assemble/package 任务）与 `testBuildType` 的模块级改道；最小配置 = 属性门（默认 debug）+ release `testProguardFiles`；判据 K1–K6 可机检。
- **GitHub 评分（§6）**：sqlite 5/5/5（WAL 是持久状态、只读打开三路径、TRUNCATE 语义）、litestream 5/5/4（源库建表会改字节，反证 checkpoint 身份）、restic 5/5/4（身份建在规范化 JSON）、两个 AGP 镜像 license `null`（只读不抄）。**零依赖引入。**
- **落码对应与偏差**：四件已落（§3.5）；R2 §4.1 的 **T5b**（事务内写入仍须 `exit 2`）在复核修复轮补上（`test_split_gate.py:829`）；R2 §7 U5 只登记"取副本撕裂"，**"身份≠事务快照"（ABA / 撕裂 WAL 摘要）的上限未写进 README/KD** → 记录员列为残留（§5.3 #I）。

## 3. 八实施轨 + 集成交接 + 设备 + 证据

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 轨自述 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（784 行）；§8 已回填设备读数** | §3.1 |
> | T2 rowCounts | 部分 | **用例 2 例 + 设备 `OK (2 tests)` + 静态 S=71 本轮 PASS → 80-C2 设备侧已取到** | §3.2 |
> | T3 A-01 | 部分 | **落码成立；生产默认 null；JVM 有 XML 旁证 0 失败、无仓内命令日志** | §3.3 |
> | T4 A-02/A-03 | 完成 | **落码成立（含执行器/对账第二道门）；无新增设备用例；JVM 同上** | §3.4 |
> | T5 KD-81 | 完成 | **工具侧落码 + pytest 37 绿；`-shm` 字面项与设备复跑未满足 → KD open** | §3.5 |
> | T6 R-04/KD-72/73/77 | 部分 | **R-04 配置在树、release 未跑；KD-72/73/77 零动作** | §3.6 |
> | T7 文档 | 完成 | **台账/清单/决策已改；附录 H 4 项契约缺失** | §3.7 |
> | T8 条件轨 | 完成 | **分支 D，九项仍 pending，零改动** | §3.8 |
> | 集成交接 | 完成 | **无需新增接线；schema 71/18 不变；审计链闭合** | §3.9 |
> | 设备轨 | 完成 | **15 套件绿；源快照缺 `core/model`；修复轮 4 例未上设备** | §4 |
> | 证据轨 | 完成 | **11 份；记录时点 73,535 B（索引自述 68,040 B）；脱敏 0** | §4.7 |
> | 复核修复轮 | 完成 | **7 项真实缺陷已修（§5.1）；4 例设备用例与 JVM 重跑后的结论见 §4.5/§6** | §5 |

### 3.1 T1 设备判据 —— **文档落盘；§8 已回填**

- 文件 `docs/research/2026-10-29-batch-34-device-criteria.md`（**784 行**）：结论速览（§0）、KD-80 收尾（§1，含 §1.3 的 90 键清单与 §1.4 静态脚本）、重建重装硬门（§2，含 §2.1「不得引用批次 33 绿轮」）、新测试源重跑（§3）、串行命令与 dex 门（§4，15 套件命令在 §4.4）、红例归因（§5）、未验证项（§6）、来源（§7）、**设备结果回填（§8，18:42–19:02）**。
- 判据把"设备数 < 源计数 = 旧包"写成硬规则（§3.2），并要求 dex 符号门（§4.3）——本批设备轨的 15 套件命令与 E-01 的 dex 门均照此执行。
- **记录员未重跑任何判据命令**（除 §1.4 的 S=71 脚本，见 §3.2/§10）；§8 的回填读数与 `build/device-track-batch34/` 原始日志一致（§4.2）。

### 3.2 T2 rowCounts —— **用例落码 + 设备 2/2 + 静态 PASS**

- **设备用例（实读）**：`core/data/src/androidTest/.../backup/BackupRowCountsCoverageInstrumentedTest.kt` 两个 `@Test`：
  - `manifestRowCountsEnumerateTheNinetySchemaTablesByName`：从测试 APK 的 `assets/…/71.json` 取 90 个表名，对 manifest `rowCounts` 做**原始文本键序列**解析（绕开 `JSONObject` 静默合并同名键）+ 集合差集 + 逐名断言。
  - `everyRowCountMatchesThePackagedAndRestoredDatabaseByName`：从包内库与恢复库的 `sqlite_master` 读实际表集合，与 `71.json` 逐名双向比对，再逐表断言 `rowCounts == COUNT(*)`。
- **设备执行（记录员 grep 原始日志复核）**：`build/device-track-batch34/step02-rowcounts.log` 含两条方法名（`:7/:14/:21/:28`）与 `OK (2 tests)`（`:34`）；logcat 9 类签名 0。
- **静态 S=71（记录员本轮实跑）**：`PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90`、`VIEWS_MATCH=True`、`PLAN_VERSION=71 JSON_VERSION=71`、`RESULT=PASS`、`EXIT=0`（§10）。
- **限制（与复核发现一致）**：`assertTrue("rowCounts 缺少 $table")` 在三条集合差集断言通过后才可达，只增加失败消息、不增加检测力；恢复半边是"全量副本 → restore"的形态，抓不到静默 no-op 的 restore（§5.2 #E）。行数相等是第二层判据，不是唯一判据。

### 3.3 T3 A-01 —— **落码成立；生产仍 UNAVAILABLE；JVM 只有 XML 旁证**

- **注入点（实读）**：`DefaultFigureGenerationCoordinator.kt:153` `visualEngine: VisualExecutionEngine? = null`、`:158` `visualFrameInputs: VisualFrameInputs? = null`；`:209` IMAGE_MODEL 原路径抽出为 `generateImageViaModel`，`:211` JS → `generateLocalJs`。
- **JS 分支（`:248-356`）**：无引擎 → `UNAVAILABLE`（保留原句）；无帧 → `UNAVAILABLE`；`state.begin()` → `state.beginLocalAttempt(JS, sourceSha)` → `engine.execute(...)`；Rendered → `GENERATED` + `FigureArtifact.Program`；`ENGINE_CLOSED` → `UNAVAILABLE`；其余 Rejected/异常 → 有界 `FAILED`；取消 → `NonCancellable` 落 `CANCELLED`。
- **0 远程派发**：全文件 `ledger.` 调用只有 `:402` 一处 `ledger.reserve`，位于图像路径；`:245-246` 明写 JS 不调。`reservedRemoteDispatches` 只在 `beginAttempt` 置 1（`:565`），本地路径不经过 → 终态写 `0/0`（`:583-584`）。
- **本地 attempt（实读）**：`FigureGenerationAuditRecorder.kt:52` → `RoomFigureGenerationAuditRecorder.kt:74/:82` → `FigureGenerationRecorderPort.kt:58` → `RoomStudyDatabase.kt:1387` → `FigureGenerationDao.kt:125-145` 的 `@Transaction insertLocalAttempt`：`ordinal = (minLocalAttemptOrdinal(operationId) ?: 0) - 1`，`reservation_id = NULL`，`status = RUNNING`。负序号与远程正序不相交（`:119-120` KDoc 写明）。
- **A-08 指纹**：`FigureGeneration.kt:646` `figureJsBasisFingerprint`；coordinator `:698-707` 按 method 分支取指纹（JS 用 program，IMAGE_MODEL 用 imagePrompt）。
- **生产装配（实读）**：`SmartMistakeBookApplication.kt:364-369` 仍只传 `database/configurationStore/networkRequestsAllowed`；工厂 `:790-806` 的两参数默认 null → 生产 JS 仍 `UNAVAILABLE`。
- **对账修复（修复轮）**：`FigureGenerationReconcileDao.kt:126-128` 的假成功候选加 `AND method <> 'JS'`；`:168-173` 的"操作已确认成功"条件认 `confirmed.method = 'JS'`。修掉了原复核发现的"每次启动把真实 JS 成功改写成 FAILED"的雷（§5.1 #1）。
- **JVM**：`FigureGenerationCoordinatorTest` **17** 例（9 例 JS 相关：`:215/:239/:259/:323/:347/:379/:407/:433/:463`）；`build/` XML mtime **21:15:02**、`tests="17" failures="0" errors="0"`。**但 XML 不是仓内命令日志**，门归属仍记 UNVERIFIED（§6）。

### 3.4 T4 A-02/A-03 —— **落码成立；执行器第二道门在热区文件里**

- **契约层（实读）**：`TutorToolLoop.kt:352-380` 的 `when (figureMethod)`：JS 要求非空 program、UTF-8 ≤ `MAX_PROGRAM_UTF8_BYTES`（64 KiB，`FigureGenerationPolicy.kt:30`）、且 `imageOperation/imagePrompt == null`；IMAGE_MODEL/缺省要求 `figureProgram == null`。`toFigureGenerationProposal` `:431-441` 按 method 分支：JS 只带 program，图像方法只带图像载荷。
- **请求 schema（实读）**：`OpenAiModelProtocol.kt:130` 默认 `setOf(IMAGE_MODEL)`，`:378-389` `jsAdvertised`，`:486` 才追加 `JS`，`:571-590` 独立 `program` 属性，`:742-744` 广告 JS 时才把 program 并入 required。生产调用点不传 → 线上工具声明不变。
- **执行器层 fail-closed（修复轮，实读）**：`RoomTutorToolRunner.kt:948-960` 对 `call.figureMethod == JS` 直接返回 `ok=false` + `JS_FIGURE_UNAVAILABLE_SUMMARY` + `figure_method_unavailable`；轮次层同门在 `RoomModelTaskToolRounds.kt:53-63`，常量 `:124-131`。**该文件是热区且在 `git diff` 中混有他线的 `visualRole`/`attachCleanRedrawAsset(role=…)` 改动**（§5.3 #D）。
- **JVM**：`TutorFigureMethodDualParseTest` **14**、`TutorToolRoundGateTest` **16**、`TutorFigureProposalCallTest` **11**；XML 0 失败（21:15:02 / 18:33:41）。
- **未覆盖**：无新增 androidTest（E-07 `(no androidTest hits)`）；A-02/A-03 的"同一源/同一 APK"对 `core/model/src` 不成立（§4.4）。

### 3.5 T5 KD-81 —— **工具侧落码；pytest 37 绿；KD 仍 open**

- **已落（实读）**：`export_split_eval.py:78` `IDENTITY_KIND = "inputs-v1"`；`:101-135` `_sidecar_paths`/`_input_manifest`（db+wal 进摘要，shm 只观察、不进摘要，mtime 不进摘要）；`:213` 打开前取清单、`:222` 显式 `BEGIN`、`:290` 关闭后取清单、`:286-294` 比对失败 `DataError("快照一致性…")`（先于写盘）；`:300` `payload.sha256 = sha256(canon(pages))`；`:358-359` stdout 如实打印三种身份。
- **回归（记录员本轮实跑）**：`python -m pytest tools/tests/test_split_gate.py -q` → **37 passed in 2.87s**。T5b `test_export_rejects_wal_change_inside_transaction` 已在 `:829`（复核修复轮补）。T7 记录的 **36** 是修复轮前时点，现树为 **37**。
- **旧断言未放宽**：`test_export_is_deterministic_and_consumable_by_measure` 与 `test_db_is_read_only` 保留；新增 T7 覆盖 `-wal`，未把只读断言改成"三件都不变"（shm 会被改写）。
- **口径偏差**：关闭条件 ② 字面要求 `-shm` 覆盖，实现按 R2 C3 排除 → **KD-81 保持 open**（待主会话裁定是否接受"内容身份不含派生 shm"的口径）。
- **残余**：AUD-08 设备库复跑未做；"身份只证明导出前后两次字节，不证明事务实际读到的快照（ABA/撕裂 WAL）"未写进 README/KD（§5.3 #I）。

### 3.6 T6 R-04 与 KD-72/73/77 —— **R-04 配置在树、release 未跑；三条 KD 零动作**

- **R-04（实读）**：`app/build.gradle.kts` 新增 `r04TestBuildType = providers.gradleProperty("smbk.testBuildType").orNull ?: "debug"`、`testBuildType = r04TestBuildType`、`release { testProguardFiles("r04-test-keep.pro") }`（`git diff` +14 行）；`app/r04-test-keep.pro` 36 行，`grep -cE "dontobfuscate|dontshrink|dontoptimize"` = **0**。默认 debug = AGP 默认，日常设备轨零变化。
- **未验证**：`./gradlew -Psmbk.testBuildType=release …` **未执行**；K1–K6 全部未跑，release 测试轨 **UNVERIFIED**。计划 §1 第 6 项已回改为"停项登记分支"（`plan:18-19`），`00-index.md` §8 有登记。
- **KD-72**：记录时点 `git ls-files '*lockfile'`=0、git 可见 13、盘上 14、`benchmark/gradle.lockfile` 不存在；W6 负控未执行、W7 Gradle 未跑 → 窗口仍不满足，零动作，保持 open（§5/§9 已补批次 34 块）。
- **KD-73**：`gradle/libs.versions.toml:3` 仍 `kotlin = "2.3.21"`；本批无新增全解析图扫描脚本/结果 → 零动作，保持 open。
- **KD-77**：本批设备轨全在 `emulator-5562` / API 34；记录时点 `adb devices` 仅 `emulator-5562`（sdk=34）与 `emulator-5584`（sdk=34）可响应，`emulator-5566` 列在 devices 但 `getprop` 返回 "device not found"；无 API 23 装机/冒烟证据 → 零动作，保持 open。

### 3.7 T7 文档 —— **四处已改；附录 H 四项契约缺失**

- `docs/known-defects.md`：KD-80 设备轨回填并关闭；KD-81 工具侧修复块；标题回改。
- `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`：v3 补记 4（A-01~A-03）+ 补记 5（A-08）+ 附录 H。
- `docs/research/evidence/2026-10-29-batch-34/step-t7-doc-ledger.md`：命令与逐行输出。
- **附录 H 四项**（`checklist:641-643`）：① `program`/`method.enum` 的 wire 形态未在契约正文定义；② "请求 schema 两层"未进契约；③ A-03 的 program 落点是 **proposal** 而非 `FigureGenerationRequest` 未写明；④ 生产广告口径（能力参数化、默认关）待主会话裁定（阻塞级，若被读成"生产必须同时广告两方法"则与硬约束冲突）。
- **未改**：`docs/current/*`（热区）。

### 3.8 T8 条件轨 —— **分支 D，九项仍 pending**

- 开工复核热区布尔与指派一致（feature/library、feature/tutor、core/ui 均脏），执行分支 D；按批次 24 R4 既有九项序位（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段6→5c）逐项复核：九项全部仍 pending、载体均在、目标文件均为 M；未新建 KD/条目/文件（台账仍 81 条，尾条 KD-81）。
- **两处开局后漂移**（T8 登记）：① `app/build.gradle.kts` 由基线时干净变为 M（+14 行，R-04 内容，属本批 R-04 轨自身）；② 5c 载体文档仍写"schema 70（当前 69）"，现树已 71。
- 本轨零改动、零 git 写、零 Gradle、零设备。

### 3.9 集成交接 —— **无需新增接线；schema 不变；审计链闭合**

- 生产装配保持"不接引擎"是唯一同时满足计划 §2（不接通 `availableMethods`）与 A-01（不接生产开关）的形态；另两处直接 `new` 协调器的回退装配（`ConfiguredCleanImageGenerator.kt`、`TutorFigureGenerator.kt`）全用命名参数，追加末位默认参数不破坏调用。
- 审计写入链路完整：接口默认实现 → Noop → Room 记录器 → 端口默认 → Room 实现 → DAO `@Transaction`；本地 attempt 的 `reservation_id` 可空（实体 `:150`）。
- `figureMethods` 只作为带默认值的可选参数贯穿 `requestBody`/`encodeRequestBody`/`nativeToolSchemas`/`strictFunctionSchema`；生产链路不传 → 默认 IMAGE_MODEL-only。
- 两条解析路由收敛于同一 `toTutorToolCall`；JS 执行器未接线，fail-closed 联动接在轮次层与执行器层两道门。
- **零 schema 改动**：`git status --porcelain -- core/database/schemas` 为空；`git ls-files core/database/schemas` = 71；DB 仍 71、请求 schema 仍 18。

## 4. 设备与证据

> 环境：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096）；零 `connectedAndroidTest`、零 `adb root`、零 git 写。执行窗口 18:42–19:02。

### 4.1 产物身份（E-00/E-01，`00-index.md` §2）

| 产物 | 字节 | sha256（宿主=设备） |
|---|---:|---|
| `app-strictOffline-debug.apk` | 189,547,386 | `ead9d4a43c99e2345abb4dc1709005c42225e66e1467169d043bd599def60cad` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,361 | `2dd226511bd0be4a193df4d51344a080d6382f76f947caff3d648df59d950d19` |
| `data-debug-androidTest.apk` | 197,787,221 | `53e69f485a4bb4fe3d5c7bdff53ebbfb50008011376df1cbc133e4a946397caf` |
| `database-debug-androidTest.apk` | 196,846,909 | `f75d6fc8b036942a8408b21dff1b6626bc1882e16bd399a48fdb87e70e27b116` |

四包 `install -r -t` 均 `Success`；设备侧 `pm path` + `sha256sum` 与宿主逐字节一致（E-01 §4）。`app` 测试包 Gradle `UP-TO-DATE`、sha 与批次 33 相同，已核实 app 测试源自 02:27 包生成后无改动。

### 4.2 15 套件结果（记录员 grep 原始日志复核）

| step | 套件 | 用例数 | 原始日志 |
|---|---|---:|---|
| 01 | 备份 | 34 | `OK (34 tests)` |
| 02 | rowCounts | 2 | `OK (2 tests)`；两条方法名在 `-r` 流中 |
| 03 | 结构门 | 5 | `OK (5 tests)` |
| 04 | 裁剪器 | 9 | `OK (9 tests)` |
| 05 | C4 视觉四表 | 5 | `OK (5 tests)` |
| 06 | 迁移矩阵 1→71 | 15 | `OK (15 tests)` |
| 07 | 69→70 | 4 | `OK (4 tests)` |
| 08 | 70→71 | 3 | `OK (3 tests)` |
| 09 | 提交 | 12 | `OK (12 tests)` |
| 10 | 仓库 | 12 | `OK (12 tests)` |
| 11 | 一致门（原型） | 16 | `OK (16 tests)`（**非产品门**） |
| 12 | painter | 24 | `OK (24 tests)` |
| 13 | PDF | 16 | `OK (16 tests)` |
| 14 | 隔离 | 13 | `OK (13 tests)`；spawn 17 = ready 17、warm-up failed 0 |
| 15 | flags | 2 | `OK (2 tests)` |

- 15 个 `step*.log` 各恰有 1 个 `INSTRUMENTATION_CODE: -1`；15 份 logcat 的 9/12 类崩溃签名全 **0**（记录员 grep 实跑）。
- 隔离杀因：`step06-isolation-flags-signatures.md` §3 逐 pid 核对——15 个有服务主动杀因（5+3+7），余 2 个（`17733`、`17918`）在 AMS `Killing … isolated not needed` 列表内，属系统回收（不计杀因）；套件后无存活 worker。记录员 grep `step14-isolation-logcat.txt`：`Start proc`=17、`warm-up finished`=17、`warm-up failed`=0。

### 4.3 证据轨

- `docs/research/evidence/2026-10-29-batch-34/` **11 个 `.md`**（`00-index`、`step00`–`step08`、`step-t7-doc-ledger`），记录时点 **73,535 B**（`find -printf '%s' | awk` 实测；索引 §0 自述 68,040 B 是修复轮回写前时点；`du` 块大小 88 KiB）。R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触。
- 脱敏四条扫描 0 命中（`00-index.md` §6）；记录员抽查未发现密钥/凭据。

### 4.4 设备源快照的已知缺口（**本批最重的事实**）

- `src-sha-before.txt` / `after.txt` 各 **864 行**，`diff` 空（`SOURCE STABLE`）。**但 find 集合是 `app/src core/data/src core/database/src`，`core/model/src` 0 行**（记录员实测 `grep -c 'core/model'`=0）——A-02/A-03 与 `FigureGenerationPolicy` 的改动都在 model 模块。
- 事后核对（`step01` §8 实跑）：`find core/model/src -newermt "2026-10-29 18:43:40"` = **0**，即本批**恰好**构建后未改 model；但这不是构建时门。若要严格"同一源/同一 APK"口径，需扩集合重建并重取四包 sha。
- 另：`app` 测试包为内容等价的 `UP-TO-DATE` 包（sha 与批次 33 相同），不是字节级重打包；已核实源未变，仍执行了重装。

### 4.5 修复轮新增/修改的用例与产品代码不在绿轮内

- 20:57–21:06 落码：`FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（新，1 例，20:59）、`FigureGenerationReconcilerInstrumentedTest.kt`（3 例，20:59，含 2 例新）、`FigureGenerationAuditFixtures.kt`（M）、`TutorFigureProposalExecutionTest.kt`（5 例，21:06，含 1 例新）、`RoomModelTaskToolRounds.kt`（20:58）、`RoomTutorToolRunner.kt`（20:58）、`DefaultFigureGenerationCoordinator.kt`（20:58）、`FigureGenerationReconciler.kt`（20:57）。
- 设备 APK 构建于 18:43 → **上述用例一个都不在 15 套件绿轮内**，执行状态 **UNVERIFIED**（`00-index.md` §5-10 已登记）。
- JVM 侧 XML mtime **21:15:02**（`core/data` **1313** 例、`core/model` **516** 例、`core/database` **226** 例，全 0 失败），且 `find app/src core/*/src -name '*.kt' -newermt "2026-10-29 21:15:02"` = **0** → 结果对应当前源快照；但 XML 仍不是仓内命令日志（§6）。

### 4.6 R-04 条件轨登记

- 已落：`app/build.gradle.kts` 两段（属性门 + `testProguardFiles`）、`app/r04-test-keep.pro`（36 行、0 条关闭混淆指令）。
- 默认行为零变化：属性未传时 `testBuildType = "debug"`。
- **未验证（UNVERIFIED）**：`./gradlew -Psmbk.testBuildType=release …` 未执行；release 测试轨两例隔离往返**没有运行证据**。不得写"R-04 已解除"。
- 复跑入口：`./gradlew -Psmbk.testBuildType=release :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest`，再按判据 K1–K6 跑两例 `am instrument`。

### 4.7 KD-80 四条裁决（记录员复核证据后）

| 条件 | 证据 | 判定 |
|---|---|---|
| 80-C1 备份 34/34 | E-02，`data` APK `53e69f48…`，`OK (34 tests)` | **PASS** |
| 80-C2 静态 + 设备侧逐名 | 静态本轮实跑 `RESULT=PASS`/`EXIT=0`；设备 `OK (2 tests)`，90 键取自同源 asset `4a13d9d0…` | **PASS** |
| 80-C3 设备 9/9 + JVM 12/17 | 设备 `OK (9 tests)`；JVM 沿用批次 33 仓内命令日志 `step-jvm-80c3.md`（本批未重跑） | **PASS（口径保留）** |
| 80-C4 视觉四表 5/5 | E-02，`OK (5 tests)` | **PASS** |

**结论**：同一批设备证据上四条全满足 → **KD-80 closed（2026-10-29 批次 34）**。唯一口径保留：80-C3 的 JVM 是批次 33 的命令日志，本批未重跑；若主会话要求同批重跑，则该半边记 `UNVERIFIED（本轮）`，其余三条仍成立。

## 5. 复核发现与处置

> 口径：凡"修复后设备未复跑"一律 UNVERIFIED。复核修复轮报告共列 30+ 条（工单材料），本节只保留对结论有影响者，并标注记录员是否独立复核。

### 5.1 复核修复轮已在树生效（记录员实读确认）

| # | 复核点 | 现树处置（实读） |
|---|---|---|
| 1 | JS 成功会被启动对账改判"失败待核实" | **已修**：`FigureGenerationReconcileDao.kt:126-128` 假成功候选加 `method <> 'JS'`；`:168-173` 认 `confirmed.method='JS'` 为确认成功；`FigureGenerationReconciler.kt:31-32` KDoc 写明 |
| 2 | 执行器层不看 `figureMethod`，JS 可落进图像生成器 | **已修**：`RoomTutorToolRunner.kt:948-960` 第二道门；与轮次层 `RoomModelTaskToolRounds.kt:53-63` 共用 `JS_FIGURE_UNAVAILABLE_SUMMARY`/`figure_method_unavailable` |
| 3 | 本地 attempt 序号无真库用例 | **已补**：`FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（-1/-2 与远程 1/2 不相交、重复远程 1 ABORT）；**未上设备** |
| 4 | T5b（事务内 WAL 变化仍须拒绝）缺失 | **已补**：`test_split_gate.py:829`；pytest 现 **37 passed**（记录员本轮实跑） |
| 5 | KD-80 台账/T7 仍写"设备侧未逐名" | **已回改**：KD-80 标题与设备轨回填块记 closed；设备轨块列出 90 键与 4 条 PASS |
| 6 | A-08 行仍判"未落" | **已回改**：`checklist:196` 记"已落（批次 34，v3 补记 5）"；`FigureGeneration.kt:646` + coordinator `:691-705` |
| 7 | `step01` 源快照缺 `core/model/src` | **已登记**：`step01` §8 复核更正；判据 §2 已改写 find 集合；**未重建重跑** |
| 8 | `step08` 头部写"18 条 am instrument" | **已更正**：`00-index` §5-8 核为 15 条（15 文件各 1 个 `INSTRUMENTATION_CODE`） |

### 5.2 仍未闭合 / 未复核（按严重度）

| # | 级别 | 位置 | 发现 | 状态 |
|---|---|---|---|---|
| A | **中** | 设备源快照（`src-sha-before/after.txt`，864 行） | find 集合不含 `core/model/src`；A-01~A-03 的"同一源/同一 APK"对 model 模块不成立。事后 `find` 为空只是事后核对 | 已独立复核（`grep -c`=0） |
| B | **中** | 修复轮 4 例设备用例（`FigureGenerationLocalAttemptOrdinalInstrumentedTest`、`FigureGenerationReconcilerInstrumentedTest` 2 例、`TutorFigureProposalExecutionTest` 1 例） | 18:43 的 APK 不含它们；`OFF` 设备绿轮不覆盖；执行状态 UNVERIFIED | 已独立复核（mtime 20:59/21:06 vs APK 18:43） |
| C | **中** | `core/data/.../study/RoomTutorToolRunner.kt` | 热区文件被修复轮修改（+43/−7），diff 混有他线的 `visualRole`/`attachCleanRedrawAsset(role=…)`；T4 摘要"本轨未改"已不成立 | 已独立复核（`git diff`） |
| D | **中** | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` A-01/A-02 行 | 仍写"`RoomTutorToolRunner.kt` 为他线 M，本轨未改"；与现树不符 → 本轮记录员补记 6 更正 | 已独立复核 |
| E | 低 | `BackupRowCountsCoverageInstrumentedTest.kt:119-149`；`step02` 证据 | 逐名 `assertTrue` 在集合差集通过后才可达，检测力未增加（只多失败消息）；"恢复库"半边是"全量副本→restore"，静默 no-op 的 restore 仍可绿 | 已独立复核 |
| F | 低 | `FigureGenerationCoordinatorTest.kt:286-287/:318` vs `00-index.md:73` | 证据把 `reservedRemoteDispatches == 0` 引到 `:259`（测试声明行），实际断言在 `:286-287`、`:318` | 已独立复核 |
| G | 低 | `TutorToolTrace.kt` 的渲染表 | 新行码 `figure_method_unavailable` 未进渲染映射，UI 那行小字会落到 `else -> "没有完成"`，与回喂摘要不同口径 | 已独立复核（grep 0 命中） |
| H | 低 | `OpenAiModelProtocol.kt:583-590` vs `TutorToolLoop.kt:362-367` | 广告 `program.maxLength` 是**字符数**，本地门按 **UTF-8 字节**；含 CJK/emoji 的 program 可过 schema 却在解析层被拒（fail-closed，不越权） | 已独立复核 |
| I | 低 | `export_split_eval.py:101-135`；`README.md:87-104` | 身份=导出前后两次字节清单，无锁、不绑定 SQLite 读事务；ABA/撕裂 WAL 时"身份≠事务实际读到的快照"这一上限未写进 README/KD | 已独立复核 |
| J | 低 | `FigureGenerationPolicy.kt:157-162` | 预算门已按 method 拆，但 `dispatchParent == null` 仍对 JS 拒绝，文案是"不能出网"；本地 JS 仍被要求绑定远程派发父操作 | 已独立复核 |
| K | 低 | `app/r04-test-keep.pro:13-14` | 自称"R-04 gate greps the proguard rule files…"，但仓库不存在该 gate（无脚本/任务） | 已独立复核（grep 仅命中自身） |
| L | 低 | `step-t7-doc-ledger.md`、`known-defects.md` KD-81 块 | 记 36 项回归绿；现树已 37（T5b 补入）。KD-81 标题已在本轮改 | 已独立复核（pytest 37） |
| M | 低 | `tmp/aud08-db-5562/`（db 12.7 MB + wal/shm） | 设备库副本未被 `.gitignore` 忽略（`git check-ignore` rc=1），一次 `git add -A` 可误提交学习者数据 | 已独立复核 |
| N | 低 | `T5` 结果自述 | "6 个回归用例全部先红后绿"无红相位日志；可复现的只有终态 `37 passed` → 先红后绿属过程断言，只能证绿 | 已独立复核（无红日志） |
| O | 低 | `TutorFigureMethodDualParseTest.kt:390` | 注释称"生产默认…与本批之前逐字节相同"，但该用例只断言 IMAGE_MODEL 存在、JS/program 不出现；"逐字节"由 diff 静态推出，测试本身未与改前 golden 字节串比对 | 已独立复核 |
| P | 低 | `docs/known-defects.md`（KD-72/73/77） | 批次 33 块后无批次 34 块；T6 的"零动作"只在工单摘要里，未落台账 → 本轮补（§9） | 已独立复核 |
| Q | 低 | 计划 §1 第 6 项 vs T6 自述 | 计划已回改"停项登记"，T6 摘要仍写"开工时 `app/build.gradle.kts` 干净，故落最小配置"；两种处置并存，基线事实（开工是否干净）无法从现树回溯 | 未复核（基线事实） |

### 5.3 记录员新增/独立发现

| # | 级别 | 位置 | 发现 | 状态 |
|---|---|---|---|---|
| α | 低 | `git status` | 工作树 727 条（246 M + 481 ??），设备证据时点 721（241 M + 480 ??），复核修复轮后又增 6 条；索引/评审引用 721 时须注明时点 | 已独立复核 |
| β | 低 | T3/T4 工单状态 vs 现树 | T4 标"完成"，但其新增的 4 例设备用例从未上设备；T3 标"部分"更准确。结论只能写"落码成立 + JVM XML 0 失败 + 设备 UNVERIFIED" | 已独立复核 |
| γ | 低 | `build/` XML（21:15:02） | JVM 结果晚于全部源改动且全 0 失败，比评审时的 18:37 快照更强；但仍非仓内命令日志，门归属 UNVERIFIED | 已独立复核 |
| δ | 低 | `docs/research/evidence/2026-10-29-batch-34/00-index.md:8` | 索引自述工作树 721、执行窗口 18:42–19:02；修复轮 20:57–21:15 的产物不在索引 §3 计数内（§5-10 已登记） | 已独立复核 |

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | 同上 |
| 强门（修复环后） | **exit=0**（工单给定） | 同上 |
| JVM 旁证 | **core/data 1313 / core/model 516 / core/database 226，0 失败** | `build/test-results/**`，mtime 21:15:02；无源文件晚于它；非仓内命令日志 |
| 设备 备份 34 | **PASS `OK (34 tests)`** | `step01-backup.log` |
| 设备 rowCounts 2 | **PASS `OK (2 tests)`** | `step02-rowcounts.log`；两条方法名 |
| 设备 结构门 5 / 裁剪器 9 / C4 5 | **PASS** | `step03/04/05` |
| 设备 迁移 15/4/3 | **PASS** | `step06/07/08` |
| 设备 提交 12 / 仓库 12 | **PASS** | `step09/10` |
| 设备 parity 16 / painter 24 / PDF 16 | **PASS** | 一致门仍是**原型门** |
| 设备 隔离 13 / flags 2 | **PASS** | spawn 17 = ready 17、signatures 0 |
| S=71 清单门 | **PASS `RESULT=PASS`/`EXIT=0`** | 记录员本轮实跑（§10） |
| KD-81 工具回归 | **37 passed** | 记录员本轮实跑 `pytest` |
| A-01~A-03 JVM | **build/ XML 0 失败；门归属 UNVERIFIED** | 无仓内命令日志 |
| A-01~A-03 设备 | **无新增 androidTest（E-07）** | 修复轮 4 例未上设备 |
| R-04 release 测试轨 | **UNVERIFIED** | 未跑 `-Psmbk.testBuildType=release` |
| 真实 Provider / 模型质量 | **UNVERIFIED** | 无凭据；JS 不广告 |

**记录员保留**：三条门禁 `exit=0` 无本轮日志落仓，记录员未运行 Gradle；JVM XML 是 `build/` 旁证；设备数字有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；设备源与现树对 `core/model` 不构成同一源门。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面**

1. **`core/model/src` 不在源快照门集合**：A-01~A-03 的"同一源/同一 APK"对 model 模块不成立；事后无漂移不等于构建时门。
2. **修复轮 4 例设备用例未上设备**：`FigureGenerationLocalAttemptOrdinalInstrumentedTest`、`FigureGenerationReconcilerInstrumentedTest` 2 例、`TutorFigureProposalExecutionTest` 1 例。
3. **A-01~A-03 的 JVM 无仓内命令日志**：只有 `build/` XML（21:15:02，0 失败）。
4. **80-C3 的 JVM 半边**：沿用批次 33 命令日志，本批未重跑。
5. **R-04 release 轨**：K1–K6 全部未跑。

**代码 / 文档残留**

6. **A-02 的执行器门落在热区文件**：`RoomTutorToolRunner.kt` 的 diff 混有他线 `visualRole` 改动；提交必须整文件纳入并注明。
7. **对账"计数覆盖成 0"缺陷未修**（R1 §4.3）：`finishOperation` 仍是绝对覆盖；同 operation 先 IMAGE_MODEL 后 JS 会把 `used_remote_dispatches` 冲成 0。本批只保证 JS-only 写 0/0。
8. **JS 仍要求 `dispatchParent`**：本地方法被远程派发语义的拒绝码约束（`DISPATCH_UNBOUND` 文案"不能出网"）。
9. **`figure_method_unavailable` 未进痕迹渲染表**：UI/重开会话小字与回喂摘要不同口径。
10. **`program.maxLength` 字符 vs 字节**：广告上限与本地门不一致（fail-closed）。
11. **KD-81 的 `-shm` 字面项**：实现排除 shm，关闭条件待主会话裁定；AUD-08 设备复跑未做。
12. **身份≠事务快照（ABA/撕裂 WAL）**：未写进 README/KD。
13. **`app/r04-test-keep.pro` 引用了不存在的 gate**：需改文案或补 gate。
14. **`tmp/aud08-db-5562/` 未忽略**：设备库副本有误提交风险。
15. **KD-72/73/77**：本批零动作，保持 open（§3.6）。
16. **KD-81**：保持 open（工具侧修 + 37 绿；`-shm` 口径与设备复跑未满足）。

**环境 / 口径**

17. **一致性门仍是原型门**：`product-gate=no`；不得引用为产品/发版结论。
18. **arm64/16 KB、真机、低内存/高负载、API 23、跨 fontScale** 未覆盖；本批设备仅 x86_64/API 34/4 KB。
19. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
20. **4E.2 MVA 仍不成立**：本批只落装配与 schema 的代码层，不等于双后端/质量门就绪。
21. **热区在飞未触碰**（`RoomTutorToolRunner` 例外）：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、app Root、docs/current 其余 M、docs/research 他线未跟踪记录。
22. **锁文件 14 个**：KD-72 窗口仍不满足，一律不提交。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；若引用"设备绿"，先按 §4.4 扩 find 集合重建重跑（或明确"设备绿只对 app/data/database 三源集成立"）；热区、14 个 `*.lockfile`、`build/`、`tmp/` 一律排除。按 ≤6 路径/块：

- **块 1（A-01 协调器与指纹）**：`core/data/src/main/kotlin/.../visual/DefaultFigureGenerationCoordinator.kt`（M；**整文件纳入**）、`core/data/src/main/kotlin/.../visual/VisualFrameInputs.kt`（新）、`core/model/src/main/kotlin/.../FigureGeneration.kt`（M；含 `figureJsBasisFingerprint` 与 KDoc）、`core/model/src/main/kotlin/.../FigureGenerationPolicy.kt`（M）
- **块 2（审计链与持久层）**：`core/data/.../visual/FigureGenerationAuditRecorder.kt`、`core/data/.../visual/RoomFigureGenerationAuditRecorder.kt`、`core/database/.../port/FigureGenerationRecorderPort.kt`、`core/database/.../dao/FigureGenerationDao.kt`、`core/database/.../RoomStudyDatabase.kt`（M；**整文件纳入**）、`core/database/.../entity/FigureGenerationEntities.kt`
- **块 3（A-02/A-03 与对账；含热区整文件）**：`core/model/.../TutorToolLoop.kt`、`core/data/.../model/OpenAiModelProtocol.kt`、`core/data/.../model/RoomModelTaskToolRounds.kt`、`core/data/.../study/RoomTutorToolRunner.kt`（M；**他线热区，整文件纳入并注明含他线 `visualRole` 改动**）、`core/database/.../dao/FigureGenerationReconcileDao.kt`（M）、`core/data/.../visual/FigureGenerationReconciler.kt`（M；仅 KDoc）
- **块 4（JVM 用例）**：`core/data/src/test/.../visual/FigureGenerationCoordinatorTest.kt`、`FigureGenerationAuditRecorderTest.kt`、`RoomFigureGenerationAuditRecorderTest.kt`、`core/model/src/test/.../FigureGenerationPolicyTest.kt`、`core/data/src/test/.../model/TutorFigureMethodDualParseTest.kt`、`TutorToolRoundGateTest.kt`
- **块 5（设备用例）**：`core/data/src/androidTest/.../backup/BackupRowCountsCoverageInstrumentedTest.kt`、`core/data/src/androidTest/.../visual/FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（新）、`FigureGenerationReconcilerInstrumentedTest.kt`、`FigureGenerationAuditFixtures.kt`、`core/data/src/test/.../study/TutorFigureProposalExecutionTest.kt`、`core/model/src/test/.../TutorFigureProposalCallTest.kt`
- **块 6（KD-81 工具）**：`tools/split_golden/export_split_eval.py`、`tools/tests/test_split_gate.py`、`tools/split_golden/README.md`
- **块 7（R-04）**：`app/build.gradle.kts`（M；**整文件纳入，含 R-04 两段**）、`app/r04-test-keep.pro`（新）
- **块 8（研究/判据/证据前半）**：`docs/research/2026-10-29-dual-backend-wiring.md`、`2026-10-29-snapshot-identity-wal-and-r8.md`、`2026-10-29-batch-34-baseline.md`、`2026-10-29-batch-34-device-criteria.md`、`docs/research/evidence/2026-10-29-batch-34/00-index.md`、`step00-environment.md`
- **块 9（证据后半）**：`step01-build-install-dex.md`、`step02-backup-rowcounts-trimmer-c4.md`、`step03-structure-gates.md`、`step04-migrations-commit-repo.md`、`step05-parity-painter-pdf.md`、`step06-isolation-flags-signatures.md`
- **块 10（证据收尾 + 记录/台账）**：`step07-a01-a03-static.md`、`step08-red-attribution.md`、`step-t7-doc-ledger.md`、`docs/research/2026-10-29-batch-34-record.md`（新）、`docs/known-defects.md`（M；**整文件纳入**）
- **块 11（台账）**：`docs/REFACTOR-MASTER-LINE.md`（M；**整文件纳入**）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；**整文件纳入**）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；**整文件纳入**）

**明确排除（他线在飞/不安全）**：全部 `*/gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`；core/data 他线 M（mistake/study/knowledge/model/capture 中非本批文件）；core/database 开工即脏的其余 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`PendingCaptureDao`、`TutorConversationDao`、`CleanRedrawTaskPort`、LibraryFts 三测）；`VisualDrawOpsParserTest.kt`；app 其余 M/??；docs/current 其余 M；docs/research 他线未跟踪记录；`build/**`（含设备原始日志）；`tmp/**`。

**提交说明**：建议 11 块、每块 ≤6 路径；整文件纳入的 M 在提交说明中注明"整文件纳入，含他线改动"；`RoomTutorToolRunner.kt` 必须单列并注明"含他线 `visualRole` 改动 + 本批 A-02 第二道门"；提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1 375 行（A-01 切口/三处 A-02/两层 schema/负序号 attempt/0 派发四层/GitHub 评分）、R2 708 行（WAL 探针 P1–P9、三方案否决、四件推荐组合、R-04 AGP 核实与 K1–K6）落盘；未验证项如实。
- **实施**：T1 判据 784 行；T2 rowCounts（2 例，设备 2/2）；T3 A-01（注入点 + JS 分支 + 负序号 attempt + 0 reserve）；T4 A-02/A-03（契约层 + schema 参数化 + 执行器/轮次两道 fail-closed）；T5 KD-81（四件 + 7 例回归，37 绿）；T6 R-04 配置在树/未跑 + KD-72/73/77 零动作；T7 文档；T8 分支 D。
- **集成**：无需新增接线；schema 71/18 不变；审计链闭合。
- **设备**：15 套件全绿（34/2/5/9/5/15/4/3/12/12/16/24/16/13/2）；9/12 类签名 0；隔离 17↔17；KD-80 四条满足并关闭。
- **门禁**：三条 `exit=0`（工单给定）；JVM XML 0 失败（21:15:02）；记录员未复跑 Gradle。
- **本批不声称全绿/全就绪**：`core/model` 不在源快照门、修复轮 4 例未上设备、A-01~A-03 JVM 无仓内日志、R-04 release UNVERIFIED、KD-81 仍 open、KD-72/73/77 open、API 23/arm64/生产签名/真实 Provider 均 UNVERIFIED、JS 仍 `UNAVAILABLE` 不广告、4E.2 MVA 不成立。
- **台账同步（本轮记录员写入）**：`docs/known-defects.md`（KD-80 记录回链 + KD-81 复核修复轮块/标题 + KD-72/73/77 批次 34 零动作块）、`docs/REFACTOR-MASTER-LINE.md`（批次 34 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 34 裁定节）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（补记 6）；均标注"记录员只读核对，未运行 Gradle/设备"。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # db792fa8
git status --porcelain | wc -l                               # 727
git status --porcelain | awk '{print $1}' | sort | uniq -c    # 246 M / 481 ??
git status --porcelain -- core/database/schemas              # 空
git ls-files core/database/schemas | wc -l                   # 71
git ls-files '*lockfile' | wc -l                             # 0
git status --porcelain | grep -ci lockfile                   # 13
find . -maxdepth 3 -name '*.lockfile' -not -path './.git/*' -not -path './.worktrees/*' | wc -l  # 14
ls benchmark/gradle.lockfile                                 # No such file

# schema 71（python 实读）
# bytes 378961 version 71 identity 714c0ecb85ee5edf74f0013740148138 entities 90 views 2
# S=71 清单门（本轮实跑）：
# PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71
# RESULT=PASS / EXIT=0

# KD-81 工具回归（本轮实跑）
python -m pytest tools/tests/test_split_gate.py -q
# 37 passed in 2.87s

# 设备原始日志（本轮 grep）
for f in build/device-track-batch34/step*.log; do ... "OK (" ...; done
# 34/2/5/9/5/15/4/3/12/12/16/24/16/13/2；各 1 个 INSTRUMENTATION_CODE
grep -cE "Start proc .*:visual_js|warm-up finished|warm-up failed" \
  build/device-track-batch34/step14-isolation-logcat.txt      # 17 / 17 / 0
# 15 份 logcat 的 9/12 类崩溃签名：全 0

# 源快照（本轮核实）
wc -l build/device-track-batch34/src-sha-before.txt           # 864
grep -c 'core/model' build/device-track-batch34/src-sha-before.txt  # 0
diff -q build/device-track-batch34/src-sha-before.txt build/device-track-batch34/src-sha-after.txt  # 相同

# JVM 旁证（build/，非仓内命令日志）
# core/data 155 XML / 1313 tests / 0 fail；core/model 50/516/0；core/database 47/226/0
# 关键 XML mtime 2026-10-29 21:15:02；无源文件晚于它

# 设备（只读）
adb devices -l                                                # 5562 / 5566 / 5584 列出
adb -s emulator-5562 shell getprop ro.build.version.sdk       # 34
adb -s emulator-5584 shell getprop ro.build.version.sdk       # 34
adb -s emulator-5566 shell getprop ro.build.version.sdk       # device not found（stale transport）

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 git add/commit/push；任何 JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 33 记录的结构对应

| 批次 33 节 | 本批对应 | 差异 |
|---|---|---|
| §2 两研究线 | §2 两研究线 | 本批为 4E 双后端装配 + 快照身份/R-04；均含 GitHub/官方文档外部参考 |
| §3 七实施轨 + 集成 | §3 八实施轨 + 集成 | 轨名不同（T1 判据 / T2 rowCounts / T3 A-01 / T4 A-02A03 / T5 KD-81 / T6 R-04+KDs / T7 文档 / T8 条件） |
| §4 设备与证据 | §4 设备与证据 | 15 套件；新增"源快照缺 core/model"与"修复轮用例不在绿轮"两小节 |
| §5 复核发现 | §5 复核发现 | 8 项已修 + 17 项未闭合/低危 + 4 项记录员新增 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
