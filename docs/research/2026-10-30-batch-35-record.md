# 批次 35 记录（2026-10-30）：双后端收口 + 批次 34 缺陷修复 + KD-80/81 裁决

> 记录时点：2026-10-30（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 写入，依据：计划（完整读）、批次 35 基线、设备判据（573 行）、R1/R2 两研究文档（386/250 行，规模与节结构实读核对）、`docs/research/evidence/2026-10-30-batch-35/` 三份证据（E-01/E-02 索引 + 设备轨 + T7 KD 裁决轨）、各轨工单摘要与复核轨发现，另对关键锚点/门/设备原始日志做了本会话实跑复核（§10）。**记录员未运行 Gradle、未安装/执行设备用例、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码**；本轮改动 = 本记录 + 四份台账（§附录 A）。
> 计划：`.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md`（57 行，已完整读；16 项任务）。
> 基线：`docs/research/2026-10-30-batch-35-baseline.md`（计划 §0 的 8 条缺陷 6 条已在批次 34 落树/回填、仍成立 5 项——本批据此收敛执行面）。
> 研究：R1 `docs/research/2026-10-30-capability-driven-tools.md`（**386 行，9 节**）、R2 `docs/research/2026-10-30-reconcile-and-unattributed.md`（**250 行，9 节**）。
> 判据：`docs/research/2026-10-30-batch-35-device-criteria.md`（**573 行**）。
> 设备证据：`docs/research/evidence/2026-10-30-batch-35/`（**3 份 `.md`，实测 44,996 B**；E-ID 全批唯一 E-01/E-02；`build/device-track-batch35/` 原始日志在 gitignore 内）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 2 轮 `exit=0`；强门（修复环后）`exit=0`；修复环共两条发现均已在第 1 轮处置、第 2 轮复核"处置在位、无需新改动"（§5.0）。修复环第 1 轮前单测门复跑曾 `exit=1`（红），处置后复绿——工单材料完整转述了发现 1（VisualFrameInputs.kt 提交期 hazard），第二条发现的完整内容未包含在记录员收到的工单材料中，仅有门禁轨迹（红→修复→强门绿）佐证其存在与处置，如实登记（§5.0）。
>
> **本记录不声称"批次全绿"或"4E.2 就绪"**：R-04 **K6 红=阻塞**（release androidTest 设备往返在本仓现 keep 规则组合下不成立，`androidx.tracing.Trace` 被主包 R8 收缩 + j$ desugar 错位）；A-04~A-06 只落码**不接通**（`availableMethods` 未动、JS 不广告）；JVM 用例执行=主会话门禁串行（工单给定，记录员未复跑）；生产签名/arm64/P-12③ 设备例 UNVERIFIED。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 16 项**（§1 A–D 四组）：A 批次 34 缺陷修复（任务 1–5）、B KD 裁决（6–8）、C 4E.2 双后端收口（9–14）、D R-04 与窗口（15–16）。
- **实际执行面**：两研究线（能力快照驱动的工具描述与 prompt 注入 / 对账与不可归属诊断）+ T1 设备判据 + T2 对账测试 + T3 门补全 + T4 能力面（A-04~A-06）+ T5 A-08/A-13 + T6 P 组（P-11/P-12②③/M-04/Q-01）+ T7 KD 裁决 + T8 条件轨 + 集成交接 + 设备轨（`emulator-5562`，两轮 18 套件 + R-04 K1–K6）+ 证据 3 份 + 复核轨（18+ 项发现全数独立复核）+ 修复环（2 条发现）+ 本记录与四份台账同步。
- **基线驱动的收敛**（`2026-10-30-batch-35-baseline.md` §0/§三）：计划 §0 的 8 条"批次 34 缺陷"中 6 条已过时（对账 JS 区分、Runner 第二道门、T5b、源快照判据补 core/model、KD-80 回填、A-08 回填均已在批次 34 落树）→ 任务 1/2/4/5 收敛为**收紧+补测/复核**而非新修；仍成立的 5 项（真库测试未上设备、`.pro` 幽灵门、痕迹渲染缺失、parsers 注释过时、R-04 K1–K6 未执行）全部在本批处置。
- **与计划的三处已知偏差**：
  1. **M-04 落案 B 而非研究推荐案 A**：案 A 需在 core/domain 新增 `MEASUREMENT_UNAVAILABLE` 枚举——该模块是热区；案 B 折既有 `ENGINE_CLOSED` + 文件级诊断标记补偿（§3.6）。
  2. **P-11 前缀不晋升 core/domain**：R2 §2.3.1 推荐晋升，但 core/domain 热区禁改 → 常量留 core/data internal，消费方协调器同模块直接引用（§3.6）。
  3. **R-04 K2 首跑红**：`DebugModelFailureCaptureInstrumentedTest` 引 app debug 源集独有类，release androidTest 变体不可编译——R-04 配置首次实测暴露；处置=该文件**逐字节不变**移动至 `app/src/androidTestDebug/`（断言零改动，debug 测试 APK 字节不变，设备轨唯一树改动）。
- **不做（全部未做）**：`availableMethods` 接通与广告开关（全门绿后最后一步）；JS 广告；真实 Provider（无凭据）；锁文件生成/提交（KD-72）；`docs/current` 热区（`visual-generation-contract.md:208` 过时句留契约轨）；热区五模块与 core/ui/core/domain/core/export/core/markdown。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature/tutor、feature/library、feature/review、feature/capture、feature/profile、core/ui 全部、core/markdown 整目录、core/export 全部、core/domain（开工即脏 M）、core/data 他线 M 与 knowledge/dense 面、settings.gradle.kts、.gitignore、app Root 与开工即脏 M、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production。本批触碰的共享脏文件（`FigureGenerationPolicy.kt`、`FigureGenerationPolicyTest.kt`、`DefaultFigureGenerationCoordinator.kt`、`QuickJsVisualExecutionEngine.kt`/其设备测试）均"整文件纳入并注明"（§8）。
2. **能力快照 / prompt / 广告文本零改动直到全门绿**：A-04~A-06 只落代码不接通 `availableMethods`；JS 不广告（复核面 2 独立确认默认态逐字节等价，§5.2 #19）。
3. **DB schema 保持 71**（`StudyDatabase.kt:139`；本批 DB 面触碰仅 `FigureGenerationReconcileDao.kt` 的 @Query 文本）；请求 schema 只由对应轨写（`explicitUserRetry` 不进 OpenAiModelProtocol wire schema，集成轨 grep=0 确认）。
4. KD-80 按四条全绿裁决关闭（批次 34 写就、批次 35 复核维持）；KD-81 按"db+wal 进身份、-shm 排除"裁定关闭（补 T5b）。
5. 设备绿绑定源快照；新增用例必须重跑；修复环后必须复跑强门。
6. 不许为变绿放宽断言；K6 红按判据阻塞回报，不删例、不换 internal 变体、不引 `-dontobfuscate`。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`4341f5adcabe0ec45736d9e40cc11482ccf1fd1c`；构建/绿轮绑定 `902d7acc`（设备轨开工值）；期间他线 KB 两笔 **docs-only** 提交 `a3b8a534`/`4341f5ad`（E-00 索引 §0）。
- **工作树**：**726 条 = 238 M + 486 ?? + 1 A（VisualFrameInputs.kt 已暂存）+ 1 D（DebugModelFailureCaptureInstrumentedTest 旧路径）**（`git status --porcelain` 记录时点实测；开工基线 751 → 复核修复轮后回落）。
- **DB schema**：`STUDY_DATABASE_VERSION = 71`；`71.json` **378,961 B**、sha256 `4a13d9d0b5ee…`、`identityHash=714c0ecb…`、**90 实体 / 2 视图**（本会话 python 实读 = 设备绿轮 asset 逐字节同源）；S=71 静态门 `RESULT=PASS`/`EXIT=0`（本会话实跑）。
- **热区（记录时点实测，仍脏）**：feature/tutor **59**、feature/library **43**、core/ui **35**、feature/capture **16**、feature/review **12**（T8 开工复核实跑）；core/domain 4 个 M 均他线开工即脏（复核轨标记扫描 0 批次 35 痕迹）；本批对上述路径零写入。
- **锁文件**：`git ls-files '*lockfile'` = **0**；git 可见未跟踪 **13**；盘上（maxdepth 3）**14**；`benchmark/gradle.lockfile` 不存在（本会话实跑）。
- **KD 台账**：`docs/known-defects.md` **82 条**（尾条 KD-82，本批 T3 新开）；KD-80/KD-81 closed、KD-82 open。

## 2. 两研究线

### 2.1 R1 能力快照驱动的工具描述与 prompt 注入（`2026-10-30-capability-driven-tools.md`，386 行 9 节）

驱动 T4/T5 落码。核心裁定（记录员按节结构实读 + 轨摘要转录）：

- **A-04/A-06 = "能力参数化、生产默认逐字节不变"**（§2）：prompt 装配与工具描述按真实方法集合分支，新参数全部带缺省值且缺省下与旧文本逐字节一致——默认参数快照测试钉住现文本，消灭"接通即改广告"的耦合。
- **A-05 = 双就绪位是方法级广告位的纯函数派生**（§3）：`GENERATE_FIGURE` 广告 = `imageBackendReady ∨ (jsEngineReady ∧ jsMeasurementReady)`；快照**每轮重算**而非跨轮状态机；执行失败经既有 outcome/熔断通道传播、**不回写广告位**；唯一降位路径是装配级死亡（→下一轮 `toolDeclarationsFor` 重算）。不造第二份跨轮健康度状态机。
- **A-08 = 入口侧约束落 policy 而非 coordinator**（§4.2）：program 摘要是权威；显式 basis 必须过方法一致复核；冲突 fail-closed（新码 `BASIS_FINGERPRINT_MISMATCH`）。三入口只构造 IMAGE proposal + Runner:949 第二道门 → **今日不可达**，但 A-02 接线前必须立。
- **A-13 = grep 门脚本化**（§4.3）：恰 3 处白名单精确相等，本轮实跑复核成立；**幽灵门教训**登记（`.pro` 注释声称存在的门不存在 → 要么落脚本要么改注释，不得"声称成真"）。
- GitHub 参考（§5）：能力协商/工具描述派生方向评分，零依赖引入。

### 2.2 R2 对账与不可归属诊断（`2026-10-30-reconcile-and-unattributed.md`，250 行 9 节）

驱动 T2/T6 落码。核心裁定（节结构实读 + 轨摘要转录）：

- **对账 JS 区分的不可伪造判据**（§一）：批次 34 落树的 JS 区分**判据可伪造**——候选查询裸 `AND method <> 'JS'`、操作级确认谓词裸 `confirmed.method = 'JS'`，都缺 `reservation_id IS NULL` 连词。写入径枚举证明 `method='JS' ∧ reservation_id IS NOT NULL` 生产不可达（attempt 只有 `insertLocalAttempt` 恒 null 序号与 `beginFigureGenerationAttempt` 恒 IMAGE_MODEL 两路写）→ 该形状只可能出自上游回归；但裸 method 判据会让"JS 名义预留行"被当本地确认成功且被 `method <> 'JS'` 永久排除在对账外。最小收紧：候选 `NOT (method = 'JS' AND reservation_id IS NULL)`、操作级补同连词。
- **P-11 链路五站保真成立、缺口在两端**（§二）：分类器→服务 encodeRejection→AIDL payload/summary 双路→客户端 decodeRejection 逐字保真；缺口=跨进程保留无断言（且 app 模块 JVM 测试无 org.json → wire 往返门必须设备侧）+ 前缀常量 internal 无消费方。
- **P-12② 生产调用点设计**（§三）：`FigureGenerationRequest.explicitUserRetry` 默认 false（三入口零变化、不进 wire schema 两轨不冲突）+ 协调器 `generateLocalJs` 透传；远程路径不消费（熔断恒等物 programHash 仅 JS 有）；预算承继=JVM 断言。
- **M-04 折点唯一**（§四）：`QuickJsVisualExecutionEngine.kt:381-390`（现树 `:396-410`）把宿主度量设施失败折 `INVALID_PROGRAM`，与程序侧预算越界分界=异常来源；推荐案 A（core/domain 枚举）/备选案 B（ENGINE_CLOSED+标记）。
- **Q-01 洞确认**（§四）：`validateJsProposal` 只查 parametric 不查 pixel；三生产入口恒 false → 洞当前不可达但纯门必须自洽。
- GitHub 参考五项评分（§五）：namastack-outbox（Apache-2.0）模式印证采纳不引依赖；temporal/trillian/jobrunr 各按 license/维护度裁定不引入。

## 3. 八实施轨 + 集成 + 设备 + 证据

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 门禁工单给定 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据文档落盘（573 行）；本轨零 Gradle/零 adb/零 git 写** | §3.1 |
> | T2 对账测试 | 完成 | **谓词收紧落树 + 3 设备用例 + python/sqlite 探针红→绿闭环；Mimosa 误报 4 次拦截改 stdin 实跑** | §3.2 |
> | T3 门补全 | 完成 | **①源快照门复核（无脚本对象）②T5b 红相位实跑 ③幽灵门→`check_r04_keep_rules.py` ④maxLength→KD-82** | §3.3 |
> | T4 能力面 | 完成 | **A-04/A-05/A-06 落码不接通；生产广告文本零改动；JVM 留门禁串行** | §3.4 |
> | T5 A08A13 | 完成 | **`validateBasisFingerprint`+新码落 policy；入口门脚本 PASS；JVM 用例登记 UNVERIFIED→门禁给定绿** | §3.5 |
> | T6 P 组 | 完成 | **P-11 消费点+P-12②③+M-04 案 B+Q-01+痕迹渲染+注释更正；保守分支两处登记** | §3.6 |
> | T7 KD 裁决 | 完成 | **KD-80 关闭维持（复核回链）；KD-81 closed（按主会话裁定）；A-08 口径+反向虚报清理** | §3.7 |
> | T8 条件轨 | 完成 | **分支 D，九项仍 pending，零改动** | §3.8 |
> | 集成交接 | 完成 | **无需接线，零文件改动；12 主源文件 diff 全读 + 新增引用逐个落点** | §3.9 |
> | 设备轨 | 完成 | **18 套件两轮全绿；R-04 K1–K5 PASS、K6 红按判据阻塞回报；唯一树改动=逐字节移动** | §4 |
> | 证据轨 | 完成 | **3 份 .md（44,996 B）；回链检查 6/7，批次记录回链由本文件完成** | §4.6 |
> | 修复环（主会话） | 完成 | **2 条发现第 1 轮处置、第 2 轮复核无新改动；VisualFrameInputs.kt 已暂存** | §5.0 |

### 3.1 T1 设备判据 —— **文档落盘（573 行）**

- `docs/research/2026-10-30-batch-35-device-criteria.md`：新增用例设备判据（§1：本地序号/对账 JS/P-11 wire/P-12③/M-04/Q-01 逐项判据与命令）、R-04 K1–K6 判据与可执行命令（§二步 0–6）、15 套件不回退清单（§三）、重建重装硬门（§四：find 集合在批次 34 基础上**扩 `core/domain/src`**；core/domain 开工即脏不阻塞快照——记哈希+注明含他线在飞内容）、批次 35 专用陷阱 10 条。
- 本轨零 Gradle/零 adb/零 git 写；未验证项 7 条明列。设备轨按其 §四.7 顺序执行（§4）。
- **复核轨更正一处（§5.1 #13）**：判据 `:81` 负向 A 描述声称 `reservation_id` 有指向账本预留表的 RESTRICT 外键（引 `FigureGenerationEntities.kt:117-121`）——该外键**不存在**（实体仅 operation_id 与 output_asset_id 两条外键；夹具无预留行落库成功反证）。危害有限：收紧谓词不依赖该护栏自证，负向用例恰因无外键才可构造该形状。判据文档未回改（登记在案）。

### 3.2 T2 对账测试 —— **谓词收紧落树 + 探针红→绿闭环**

- **收紧（实读 `FigureGenerationReconcileDao.kt:134/:184`）**：候选查询 `AND NOT (method = 'JS' AND reservation_id IS NULL)`；操作级确认谓词 `OR (confirmed.method = 'JS' AND confirmed.reservation_id IS NULL)`；两处 KDoc 同步；`FigureGenerationReconciler.kt:28-34`、`DefaultFigureGenerationCoordinator.kt:634-638`（succeed() KDoc，仅注释）同步。
- **3 个设备用例**（`FigureGenerationReconcilerInstrumentedTest.kt`，现树 6 例 = 批次 34 底线 3 + 新 3）：JS 名义预留行必须被改判 FAILED+UNCONFIRMED 诊断且父操作落 FAILED（`:375`）；同操作真 JS 成功保住操作、预留行仍被纠正（`:455`）；JS FAILED/CANCELLED 终态不被对账改写、僵尸 RUNNING 仍被收尾（`:547`）。夹具 `FigureGenerationAuditFixtures.kt` 加可选 `reservationId`/`attemptDiagnostics`（默认 null，9 处既有调用源兼容）。
- **红→绿在真实 SQLite 引擎闭环（轨实跑）**：python+sqlite3 逐字抽取 8 条 @Query 原文（named 参数原生绑定）模拟三段对账——收紧前 RED（A1-A3/B1 FAIL，JS 名义预留行逃逸，counts 2/2），落收紧后 GREEN（21/21 全过、counts 4/3）。
- **任务 2 采纳入批**：工作树既有 untracked `FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（内容恰为计划任务 3 验收：连续两次本地 attempt 得 -1/-2、与账本正序 1/2 共存、远程序号 1 重复写 ABORT 撞唯一索引），逐符号核对与现源一致后纳入本批。
- **登记**：①Mimosa 对 tmp 探针文件写入连续 4 次拦截（静态误报），改为一次性 stdin 命令实跑，tmp/ 零新增；②行进中他线把 `DefaultFigureGenerationCoordinator.kt` 与两个 QuickJs 文件写脏（P-11/P-12② hunks，标注批次 35）——协调器按"整文件纳入并注明"处理。

### 3.3 T3 门补全 —— **四项全落**

1. **设备源快照门（任务 4 半）**：判据含 `core/model/src` 已由批次 34 复核修复轮落树（criteria `:284/:302/:310-314`、evidence step01 §8）；仓内确无快照脚本（`grep tools/` = 0，3 处 Kotlin 命中均为注释）→"若有脚本则改脚本"无对象；历史批 find 集合不改写历史。
2. **KD-81 T5b**：用例已在 `tools/tests/test_split_gate.py:829`（计划 §0 过时）；本轨实跑 pytest **37 passed ×3**；红相位证明：临时禁用 `export_split_eval.py:291` 后比对 → T5b FAILED(exit1)，Edit 还原后 sha256 与 HEAD 逐字节一致、套件复绿，未新增重复用例。
3. **`.pro` 幽灵门**：选"实现脚本"——新建 `tools/ci/check_r04_keep_rules.py`（默认查 build.gradle.kts 实接线的两文件、只查规则文本的 `-dontobfuscate`、注释剔除、词边界+大小写不敏感、文件缺失即 FAIL、位置参数=负控旋钮）+ 12 例自测；门对真树 exit 0、负控 exit 1(带行号)；`app/r04-test-keep.pro:12-18` 注释改为指向可复跑命令（消灭"声称成真"）。
4. **maxLength（任务 15 半）**：选"登记已知限制"——读码证实 `OpenAiModelProtocol.kt:552/:583` 把字节常量(4096/65536，`FigureGenerationPolicy.kt:32-33`)塞进按码点计的 JSON Schema maxLength，本地门按 UTF-8 字节；python 数值演示 1500 汉字=schema 合法但 4500 字节超门；方向性"本地过⇒schema 合法"恒真、反向不成立，无安全洞只浪费轮次；不改值理由=广告面冻结+⌊B/4⌋ 是产品取舍+本地门零放宽+A-04 同面排队；**KD-82** 入 `docs/known-defects.md`（open，known limitation 不排期）。

### 3.4 T4 能力面（A-04/A-05/A-06）—— **落码不接通；生产广告文本零改动**

- **A-05**（实读 `Capabilities.kt:69-97`）：`withDependencies` 增 `jsEngineReady`/`jsMeasurementReady` 双就绪位（缺省 false=生产现状逐位不变）；`GENERATE_FIGURE` 保留条件 `imageBackendReady || (jsEngineReady && jsMeasurementReady)`（`:92`），缺省退化为旧条件；`RoomModelTaskRepository.toolDeclarationsFor` 显式透传 false/false（`:958-963`，真实装配值留 L3）；失败传播语义落 KDoc（执行失败不降位，唯一降位路径=装配级死亡）。
- **A-04**：删 `FigurePromptAssembler` 两处 `require(JS !in)` fail-closed（现树 `JS !in` 0 命中，保留空方法集拒绝）；方法能力段/输出段/预算段按真实方法集合分支；新增"JS 静态程序契约"段（程序形状 const visual={prepare(input),render(input,metrics)} + visualSdk 六原语签名 + 13 行画轴 + 最小样例，全程无 reduce/form/controls 字面量；签名清单 `JS_SDK_SIGNATURES` 与 visual-sdk.js 逐名对拍单测钉住）。
- **A-06**（实读 `TutorToolDescriptions.kt:19`）：两描述函数增 `figureMethods` 参数（缺省 IMAGE_MODEL-only 逐字节同前），`GENERATE_FIGURE` 长/短描述按方法派生（JS 态含 method=JS+program 字段句、删"本地未配置可用的图像模型时也不会生成"旧句），其余 7 工具不读该参数；生产消费点（`OpenAiModelProtocol.kt:268`、`OpenAiModelTaskAdapters.kt:658/:726`）不传参 → 广告文本零改动。
- 测试先红后绿以构造方式成立（新用例编码改前不存在的行为；两处 fail-closed 用例按判据语义翻转并注释登记，负向兄弟用例原样保留）；JVM 执行按共享纪律留给门禁脚本（工单给定绿）。

### 3.5 T5 A-08/A-13 —— **入口侧约束落 policy；门脚本 PASS**

- **A-08**（实读 `FigureGenerationPolicy.kt:198/:351-382/:447`）：`validateGenerate` 尾部（payload 校验含 Q-01 JS pixel 门通过后）追加 `validateBasisFingerprint`——显式 basis null（旧调用，A-12 口径）不介入；非 null 按 `proposal.method` 重算摘要（JS→`figureJsBasisFingerprint(program)`、IMAGE_MODEL→`figureBasisFingerprint(imagePrompt)`，源段取 `request.source?.contentSha256`），不一致拒 `BASIS_FINGERPRINT_MISMATCH`（有码、有界诊断，经既有 REJECTED 路径）。影响面闭合：validate 生产唯一调用点 `DefaultFigureGenerationCoordinator.kt:165`；三入口显式 basis 恒为 IMAGE 语义推导且 contentSha256 恒由字节派生 → 生产 IMAGE 复核恒过、行为零变化；枚举无 exhaustive when 消费者、无 DB/schema/广告面改动。
- **A-13 门脚本**（本会话实跑 PASS/EXIT=0，命中 `ConfiguredCleanImageGenerator.kt:100`/`AttachedImageGenerator.kt:155`/`TutorFigureGenerator.kt:377`）：门 1 字节级扫描、命中集与硬编码白名单精确相等（第 4 处/缺失/重复任一即 exit 1）；门 2 三入口 `basisFingerprint = figure` 子串哨兵（复核轨弱点登记：注释命中亦可满足——KDoc 已自述静态前哨，真门是 policy 运行时复核）。脚本用例 6 条（真树绿 + 4 种违规红 + CLI 双退出码）。
- JVM 用例 5 条新增（IMAGE 匹配过/IMAGE 错值拒/JS 带图像语义 basis 拒/JS 带 program 摘要过/同显式 basis 第二 program 拒）+ jsProposal helper。

### 3.6 T6 P 组（P-11/P-12②③/M-04/Q-01 + 任务五）—— **全部落层，两处保守分支登记**

- **P-11**：消费点落协调器 Rejected 分支（`DefaultFigureGenerationCoordinator.kt:358-366`）——`INVALID_PROGRAM` ∧ 诊断以 `UNATTRIBUTED_DIAGNOSTIC_PREFIX`（`VisualJsFailureClassification.kt:141`，internal 同包）开头 → 折 `UNAVAILABLE`（与 ENGINE_CLOSED 同出口），不再按程序错回喂；诊断原文经 boundedRejection 逐字存活。三层保留断言：JVM 协调器用例（outcome+attempt 审计双保留）、设备 wire 用例（真实 Binder+PFD+org.json，AIDL 参数与 payload 信封双路逐字一致）、低堆注入负向既有（`VisualJsFailureClassificationTest:122` 小节）。**保守分支**：前缀晋升 core/domain 不执行（热区）；tool-round 类型化 errorKind 半边不落（无生产消费方，以既有 status 通道承载，`FigureGenerationStatus.UNAVAILABLE` KDoc 写明设施类语义）。
- **P-12②③**：`FigureGenerationRequest.explicitUserRetry`（`FigureGeneration.kt:341`，默认 false，三入口零变化、不进 wire schema）+ 协调器 `generateLocalJs` 透传（`:309`），远程路径不读。JVM 承继断言三例：同 operationId 重试→账本零触碰/`-1→-2` 负序号链/两次终态 0-0/引擎收 false→true；异 operationId→两条独立链；零余额+flag=true 仍被预算门拒。**登记**：③"成功显式重试解除熔断"直接设备断言不可确定性构造（客户端熔断只在死亡/硬期限路径置位 `VisualJsClientEngine.kt:441/:517/:916`）；重试位绕过预检半边由既有设备用例 `:376` 钉住（`issue240Request(explicitUserRetry=true)` 到达重建 worker 而非 PROGRAM_FUSED；`PROGRAM_FUSED` 基线断言现树 `:367`——记录员 grep 实证，T6 摘要的 `:318` 为批次 27 时点值）；逐阈值边界（第 7 次派发拒/128KiB+1B 拒/墙钟超 10s 拒）属 A-10 面→**批次 36**。
- **M-04 案 B**（实读 `QuickJsVisualExecutionEngine.kt:62/:396-410/:443-449`）：度量设施（宿主）运行时抛 → 折 `ENGINE_CLOSED`（协调器唯一映射 UNAVAILABLE 的拒绝类）+ `MEASUREMENT_UNAVAILABLE_DIAGNOSTIC_MARKER` 常量（`:62`）保留异常类名；程序侧近邻（预算越界 IllegalArgumentException）仍 `INVALID_PROGRAM` 不动；设施失败不进熔断集合（仅 HARD_DEADLINE 熔断）。测试：设备例 `aMeasurementFacilityFailureFoldsToEngineClosedWithAStableMarker`（修复前红=曾折 INVALID_PROGRAM）+ JVM 协调器标记存活例 + 对照臂既有用例钉住。**保守分支**：案 A（core/domain 枚举）不执行——热区；案 B 弱点（两类宿主设施故障在 reason 层混同）由标记补偿并写进 KDoc。
- **Q-01**（实读 `FigureGenerationPolicy.kt:236-244`）：`validateJsProposal` 增无条件拒绝——`requiresPixelFidelity=true` → `TARGET_UNSATISFIABLE`，与 IMAGE 分支 `:325-333` 镜像（不看能力位）。JVM 两例（负向 + 防过拒对照）；可达性复核（grep 实证）：三生产入口恒 false（`ConfiguredCleanImageGenerator.kt:105`/`AttachedImageGenerator.kt:161,167`/`TutorFigureGenerator.kt:360,366`）→ 洞当前不可达、门结构性闭合。
- **任务五**：`TutorToolTrace.kt:428` `tutorToolRefusalText` 增 `figure_method_unavailable` → '本机没有接通本地绘图通道，没有生成'（`TutorToolTraceTest` 增断言）；`OpenAiModelResponseParsers.kt:619-622` 注释更正为 A-02 实际行为（构造器接受 JS+program，fail-closed 在轮次层/执行器层两道门）。
- **共享脏文件**：`FigureGenerationPolicy.kt`/`FigureGenerationPolicyTest.kt` 含并行 A-08 轨改动；`DefaultFigureGenerationCoordinator.kt` 含对账轨 succeed() KDoc 一行 + 他线 hunks——三文件提交须整文件纳入并注明。

### 3.7 T7 KD 裁决 —— **KD-80 关闭维持；KD-81 closed；反向虚报清理**

- **KD-80**：按基线核对收敛为复核回链——known-defects KD-80 节末新增"裁决复核与回链（2026-10-30 批次 35）"块，四条逐条回链全部成立（C1 34/34、C2 rowCounts 逐名 OK(2)+静态门复跑 PASS、C3 设备 9+JVM 12/0+17/0 口径保留、C4 5/5）→ **关闭维持**。
- **KD-81**：按主会话裁定关闭——标题 open→**closed 2026-10-30 批次 35**；新增"裁决：KD-81 关闭"块（内容身份=db+wal 即 inputs-v1、`-shm` 显式排除按裁定接受为最终口径——派生共享内存索引、只读导出会重建/改写它，纳入即假漂移；关闭条件②由"字面覆盖"改判"按排除口径满足"；③AUD-08 设备复跑按裁定不再作为关闭前置并留残留登记）；T5b 回链 `test_split_gate.py:829`；本会话实跑 pytest **37 passed**。
- **A-08 清单行**：现状改"JS 半边已落"+登记入口侧半边（后由 T5 落码，v3 补记 8 刷新）；头注 v3 补记 7（仍 69 行、0 项转"通过"）。
- **反向虚报句清理**：KD-80"无设备证据"已被批次 34 回填块标注取代（复核确认）；KD-81"保持 open"由关闭块显式取代（历史块不改写）；`REFACTOR-MASTER-LINE.md:4` 关键限定句更新；`docs/current/visual-generation-contract.md:208` 过时句属热区不改、已在关闭块登记留契约轨。
- 证据落盘 `step-t7-kd-rulings.md`（该目录首份证据）。

### 3.8 T8 条件轨 —— **分支 D，九项仍 pending**

- 热区布尔复核与指派一致（library/tutor/ui 均脏），全部只读；按批次 24 R4 既有九项序位逐项复核：九项全部仍 pending、载体均在、声明热区目标文件均 M；未新建 KD/条目/文件。
- **漂移复核**：① `app/build.gradle.kts` 的 R-04 内容已随 `1c3f4ed8` 提交（现 clean），但 `app/r04-test-keep.pro` 现 M（T3 门脚本注释在飞）——非本轨；② 5c 载体 `:46` 仍写"schema 70（预计；当前 69）"（现树 71），批次 34 已登记的漂移仍未修，沿用原登记不重复建条目。台账现状 82 条（第 82 条 KD-82 由 T3 在飞新增，非本轨所建）。

### 3.9 集成交接 —— **无需接线，本轨零文件改动**

- 三条链（T4 能力面 / T6 P 组 / T2 对账）均已自洽闭合或按计划 §2 硬约束冻结在"落码未接通"态——现在接线反而违规。逐一核验（12 个主源文件 git diff 全读 + 新增引用逐个落点）：双就绪位缺省退化=旧条件；描述消费点全不传参；A-04 契约段全部门控；P-11 引用 `UNATTRIBUTED_DIAGNOSTIC_PREFIX` 同包 internal 无需 import；M-04 折既有端口枚举；`explicitUserRetry` 在 core/domain 端口已存在（批次 34 提交、clean）链路闭合且 core/domain 本批零改动；DAO @Query 仅谓词收紧方法签名未变；夹具新参追加带默认全兼容。
- **痕迹渲染闭环**：`figure_method_unavailable` 生产者（批次 34 已提交）→ `TutorToolTrace.kt:428` 新渲染分支——本批唯一一条"补上的接线"。
- **零广告/零 schema 确认**：A-13 门实跑 PASS；`STUDY_DATABASE_VERSION=71`（clean）；explicitUserRetry 在 wire 层 grep=0；快照数据类未加属性（withDependencies 只过滤 tools）→ 快照序列化零变化。
- **登记两条提交期事实**：①`VisualFrameInputs.kt` 未跟踪但 HEAD 协调器已引用（HEAD:2 处）——批次 34 遗留 hazard，本批提交必须整文件纳入否则新 checkout 编译不过（修复环已处置，§5.0）；②四轨新增的未跟踪测试/脚本文件须随各自轨道纳入提交。

## 4. 设备与证据

> 环境：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096 / fp `UE1A.230829.050`）；零 `connectedAndroidTest`、零 `adb root`、零 git 写。执行窗口 02:22–02:57（首轮）+ 03:08–03:33（重绑轮）；首轮 18 步日志被重绑轮 `tee` 覆盖，首轮数字为会话转录（两轮逐套件同绿）。

### 4.1 产物身份（E-01，`00-index.md` §2）

| 轮次 | 产物 | 字节 | sha256 |
|---|---|---:|---|
| 重建轮 | app-strictOffline-debug | 199,839,655 | `bb9485c6b287f9e3…` |
| 重建轮 | app androidTest | 1,587,372 | `f58cf083f35cd538…` |
| 重建轮 | data androidTest | 210,278,405 | `5fa9717debe75128…` |
| 重建轮 | database androidTest | 209,338,093 | `00dddb3de28dc5f9…` |
| 恢复轮（含两处源变化） | app 主包 | — | `1593b25ae33d10dc…` |
| 恢复轮 | app androidTest | — | `f58cf083…`（**不变**：源集移动不改 debug 测试 APK 字节） |
| 恢复轮 | data androidTest | — | `0b710c5f…`（他线 dense 内容） |
| 恢复轮 | database androidTest | — | `394faa86…` |
| R-04 release | app-strictOffline-release | 155,749,587 | `70e21ee495ec18e0…` |
| R-04 release | app-strictOffline-release-androidTest | 1,118,085 | `2f794a2d60bc8674…` |

四包 `install -r -t` 均 Success；设备侧 `pm path`+`sha256sum` 与宿主逐一相等。asset 门 `71.json` sha `4a13d9d0…`（= 现树 schema 逐字节同源，本会话复算一致）。

### 4.2 重建重装硬门（E-01 §1–§2）

设备五值再登记 → 一致性门 V=71/P=71 → S=71 静态比对 `RESULT=PASS`/`EXIT=0`（PLAN_HASH=JSON_HASH=`714c0ecb…`）→ 源快照 before（**1161 文件**，`app/src core/model/src core/data/src core/database/src core/domain/src`）→ 四包 `BUILD_EXIT=0`（43s）→ 快照 after diff **空** → 四包 sha 宿主=设备 → 71.json asset 门 → dex 符号门三包全过（含批次 35 全部新增方法名；唯一归因 A=判据文档转抄笔误 CasUse↔源码 CasUses，修正门清单后全过、非旧包）。

### 4.3 18 套件两轮全绿（记录员 grep 原始日志复核）

| step | 套件 | 设备结果 | 构建前源计数（记录员 `grep -c '@Test'` 实跑） |
|---|---|---|---|
| 01 | 备份 | OK (34 tests) | 34 |
| 02 | rowCounts | OK (2 tests) | 2 |
| 03 | 结构门 | OK (5 tests) | 5 |
| 04 | 裁剪器 | OK (9 tests) | 9 |
| 05 | C4 视觉四表 | OK (5 tests) | 5 |
| 06 | 迁移矩阵 1→71 | OK (15 tests) | 15 |
| 07 | 69→70 | OK (4 tests) | 4 |
| 08 | 70→71 | OK (3 tests) | 3 |
| 09 | 提交 | OK (12 tests) | 12 |
| 10 | 仓库 | OK (12 tests) | 12 |
| 11 | 一致门（原型） | OK (16 tests) | 16 |
| 12 | painter | OK (24 tests) | 24 |
| 13 | PDF | OK (16 tests) | 16 |
| 14 | 隔离（含 P-11 wire 例） | OK (14 tests) | 14（=13+P-11） |
| 15 | flags | OK (2 tests) | 2 |
| 16 | 本地 attempt 负序号（新） | OK (1 test) | 1 |
| 17 | 对账 JS 区分（新） | OK (6 tests) | 6（判据预期 3/5，实现轨落 3 新例→以构建前计数为准，无回退） |
| 18 | 引擎邻居门（M-04 强制复跑，新） | OK (27 tests) | 27 |

- 9 类崩溃签名：两轮 **18/18 份 logcat 全 0**（唯一非零=r04-k6-logcat 的 6，K6 本体）。worker 生命周期 8 例方法名在流且绿。
- 记录员实测：18 套件 OK 计数逐个在 `build/device-track-batch35/step*.log` 复现；4 个新用例方法名（本地序号/对账负向 A/引擎 M-04/P-11 wire）grep 命中对应 step 日志（§10）。
- **重绑轮缘由**：R-04 窗口内两处源变化（本轨逐字节移动 1 文件 + 他线 dense 写 14 文件含生产 Kotlin `DenseRecallAssembly.kt`）→ 稳定性探测 `DENSE_SETTLED` → 恢复 debug 态后按"修复环后必须复跑强门"重跑 18 步并把绿绑定到恢复后四包；重跑窗口快照 diff 空。他线 dense 14 文件的功能正确性归 dense 轨。

### 4.4 R-04 K1–K6（E-01 §4；独立设备状态舞步）

| K | 结果 | 关键证据 |
|---|---|---|
| K1 红基线 | **PASS** | release 主包+debug 测试包 instrument 必须红：红因=签名门 Permission Denial（`r04-step1-red.log`）；R8 崩溃签名 0 |
| K2 release 测试轨构建 | **PASS**（首跑红→源集逐字节移动→同窗重建） | 首跑红=`DebugModelFailureCaptureInstrumentedTest` 13 处 Unresolved + 2 级联（索引 §6.3 更正口径）引 debug 独有类；处置=该文件逐字节不变移动至 `app/src/androidTestDebug/`（sha256 前后同 `f55fd14f…`，断言零改动，debug 测试 APK 字节不变）；他线 dense 14 文件在窗 → 稳定性探测后同窗重建 `BUILD_EXIT=0`（3m12s） |
| K3 证书一致 | **PASS** | 两包证书 SHA-256 逐字相同 `4c5cf033…`（`r04-k3-main/test.txt`）；未重签 |
| K4 关闭混淆 0 命中 | **PASS**（脚本权威） | `check_r04_keep_rules.py` exit=0（记录员本会话复跑同）；判据原样 grep 唯一命中=`r04-test-keep.pro:17` 注释（R8 不执行），差异如实登记；mapping 796,290 真实改名行 |
| K5 so 双 ABI+测试类未改名 | **PASS** | libquickjs.so arm64-v8a 924,936B + x86_64 911,248B；dex 计 275 ≥1 |
| K6 设备两例往返 | **RED（阻塞）** | `shortMsg=Process crashed`，签名计 6：`NoClassDefFoundError Landroidx/tracing/Trace;`+`ClassNotFoundException androidx.tracing.Trace`（记录员各 grep=1）+ `NoSuchMethodError j$/util/g <init>(Ljava/util/Map;)V`（记录员 grep=4）——主包 R8 收缩掉 test 运行时所需类 + test 侧 desugared-library 与主包错位 |

- **K6 处置（判据失败处置逐字遵守）**：不删例、不换 internal 变体、不引入 `-dontobfuscate`、不改判据——按"阻塞"回报：本仓组合下"test dex 套用主包同一混淆"路线在现有 keep 规则集上不成立。扩 `app/r04-test-keep.pro` 属实现轨（该文件本批已是他线 M 在飞）；本轨不越权。**R-04 不因此判通过也不放宽。**
- 步 5 回归（无属性构建回落 debug）+ 步 6 恢复 debug 态（卸 release 装四包、sha 对齐）均 PASS。
- 一次性 keystore 与口令文件已删（口令只走环境变量）；生产签名维持 UNVERIFIED。

### 4.5 KD-80/81 裁决（记录员复核证据后）

| 裁决 | 判定 | 证据 |
|---|---|---|
| KD-80 | **关闭维持**（closed 2026-10-29 批次 34；2026-10-30 批次 35 复核确认） | E-02 §1 四条逐条回链；静态半边与设备半边对同一份 schema 字节（71.json sha `4a13d9d0…` 逐字节相同，本会话复算一致） |
| KD-81 | **closed（2026-10-30 批次 35）** | 按主会话裁定"内容身份=db+wal、`-shm` 显式排除"；T5b 在树（`test_split_gate.py:829`）且 37 项回归绿（本会话复跑同）；残留（不阻塞）：身份无锁/不绑定读事务的 ABA 上限已登记、`docs/current/visual-generation-contract.md:208` 过时句归契约轨 |
| R-04 | **K1–K5 绿、K6 红=阻塞移交实现轨/主会话** | §4.4；R-04 不判通过 |

### 4.6 证据轨

- `docs/research/evidence/2026-10-30-batch-35/` **3 个 `.md`**（`00-index`、`step-device-track`、`step-t7-kd-rulings`），实测 44,996 B（索引 §0 自述同）；R4 上限未触。**命名偏离登记**：两份 step 文件无 `stepNN-` 前缀、无独立 step00（环境五值在设备轨 §0），映射以索引 E-ID 为准。
- 回链检查 6/7 完成（脱敏 0、体量、E-ID 唯一、KD 裁决块在树、引用存在性）；**唯一未勾项"批次 35 记录 §4 回链"由本文件完成**（§4.1–§4.5 全部以 E-ID+path:line 引用）。

## 5. 复核发现与处置

### 5.0 修复环（主会话，工单给定）

- 第 1 轮两条发现，均处置完毕；第 2 轮复核"处置在位、无需新改动"。
- **发现 1（记录员复验通过）**：`VisualFrameInputs.kt` 未跟踪但 HEAD 协调器已引用（HEAD grep=2）——提交期 hazard，不纳入则新 checkout 编译不过。处置：`git add` 整文件（只暂存不提交，提交归主会话）；补强静态证据=HEAD 全仓无该符号定义、4 个 import 符号定义文件均已被 HEAD 跟踪、`VisualFrameSnapshot` 唯一定义在本文件。记录员复验：`git status --porcelain` 显示 `A `、`git diff --cached --name-only` 恰该文件一个（不夹带）。
- 发现 2 的完整内容未包含在记录员工单材料中；门禁轨迹=单测门复跑 exit=1 → 修复 → 单测门第 2 轮 exit=0、修复环后强门 exit=0。
- **门禁序列（工单给定，记录员未复跑）**：单测门第 1 轮复跑 exit=1 → 修复环 → 单测门第 2 轮 exit=0 → 强门（修复环后）exit=0。

### 5.1 复核轨发现（18+ 条，全部"已独立复核"；记录员转录并抽查关键项）

| # | 级别 | 位置 | 发现 | 处置/状态 |
|---|---|---|---|---|
| 1 | 低 | `FigureGenerationReconcileDao.kt:134/:184` | 对账 JS 不可伪造判据成立；写径枚举+python/sqlite 探针逐分支一致 | 已独立复核（记录员 grep 谓词在树） |
| 2 | 低 | `Capabilities.kt:84-97` 等 | A-04/A-05/A-06 零广告改动成立：双就绪位缺省 false、缺省退化=旧条件、availableMethods 无生产接线、消费点不传参、`reduce|form|controls` 零命中 | 已独立复核 |
| 3 | 低 | tools/ci 三组门/测试 | 门实跑复现（A-13 PASS、R-04 keep 0 命中、18 passed、37 passed） | 已独立复核（记录员本会话重跑同绿） |
| 4 | 低 | step-device-track.md + build/ | R-04 K1–K6 真执行，证据级核实；K1–K5 PASS、K6 RED 按判据阻塞回报 | 已独立复核 |
| 5 | 低 | known-defects/MASTER-LINE/checklist | KD-80/81 关闭未越权（均有计划与共享裁定背书） | 已独立复核 |
| 6 | 低 | 热区全集 | 热区零越权（core/domain 4 个 M 零批次 35 标记；`RoomTutorToolRunner.kt` 干净且 HEAD 已含 A-02 第二道门 :949-958） | 已独立复核 |
| 7 | 低 | 各轨 summary | UNVERIFIED 冒充：未发现（全部 JVM 用例如实登记未运行、设备结果只出现在设备证据） | 已独立复核 |
| 8 | 低 | checklist A-08 行 vs `FigureGenerationPolicy.kt:351` | 【时点漂移·少报】清单行写"入口侧半边未落"但 T5 已落码 → 本轮记录员已刷新（v3 补记 8） | **已处置（本轮）** |
| 9 | 低 | T2/T6 summary 行号 | 【转抄漂移】三新对账测试实际 `:375/:455/:547`（偏 ~10 行）；丢失哨兵小节实际 `:122` 起。内容均在树不改变结论 | 登记在案（本记录一律按现树行号引用） |
| 10 | 低 | step-device-track.md:12 | 【表述欠精确】设备轨称两笔 KB 提交"仅 docs/kb-*.md"——`a3b8a534` 实为 7 文件（含 4 个非 kb-*.md 的 docs 文件）；"不触 src、零 APK 影响"核心主张仍成立 | 登记在案（本记录 §1.3 按实况转述） |
| 11 | 中 | `VisualFrameInputs.kt` | 提交期 hazard 独立确认（同 §5.0 发现 1） | **已处置（已暂存）** |
| 12 | 低 | 判据 §三 vs 设备轨 §3 | 判据静态计数预测隔离 13/引擎 26 vs 实际 14/27——判据绑定"构建前源计数且不低于批次 34 值"自洽，非回退；设备轨已显式解释 | 已独立复核（记录员 grep 计数 14/27/1/6 吻合） |
| 13 | 中 | 判据 `:81` | 负向 A 描述声称 reservation_id 有 RESTRICT 外键——该外键不存在（实体仅两条外键）；夹具无预留行落库成功反证。危害有限：收紧谓词不依赖该护栏；负向用例恰因无外键才可构造 | **登记在案（判据文档未回改）** |
| 14 | 低 | step-t7-kd-rulings §3/§4.4 与 v3 补记 7 锚点 | T7 快照后批次 35 后续落码使行号漂移（`:646→:664`、`:698→:718`、`:463→:633`）；符号名均在原文件 | **已处置（v3 补记 8 按现树刷新）** |
| 15 | 低 | checklist A-04~A-06 行 | 台账欠报（安全方向）：A-04~A-06 落码与 A-08 半边落码未刷新；0 项转"通过"纪律未被破坏 | **已处置（v3 补记 8）** |
| 16 | 低 | `check_r04_keep_rules.py:50-60` vs 判据 K4a | 裸 grep 命中 1（注释行）vs 脚本 0——按判据前言"脚本落地后以脚本为准"如实登记，非静默放宽 | 已独立复核（记录员两口径均实跑） |
| 17 | 低 | `check_figure_entry_gates.py:47/:120` | 门 2 是子串哨兵（注释命中亦可满足）——KDoc 已自述静态前哨，真门是 policy 运行时复核；门 1 无此弱点（精确相等） | 登记在案（checklist A-13 行已注明） |
| 18 | 低 | 判据 §1.2（预期 3/5）vs step17（OK 6） | 对账套件 6 例超出判据预设两档；设备轨按"构建前源计数为准"登记无回退——处置正确，数字口径差已进本记录 | 已独立复核 |

**复核轨四个审查面结论**（全部"已独立复核"）：①T2/T6 不可伪造性成立（连词判据+四层门+Q-01+M-04 期望值已更新）；②T4 默认态逐字节等价（diff 结构验证 + golden 与 HEAD 旧实现逐字比对 LONG 371/371、SHORT 158/158 + 生产三入口硬编码 IMAGE_MODEL_ONLY + explicitUserRetry 不进指纹）；③T3/T5 门有效非恒真（keep 门注释排除+词边界+缺文件 FAIL+负控旋钮；入口门 1 精确相等、门 2 哨兵弱点已登记）；④T7 裁决与证据一致（KD-80 维持/KD-81 关闭有裁定背书；R-04 未被任何台账宣称解除）。

### 5.2 记录员新增/独立发现

| # | 级别 | 发现 | 状态 |
|---|---|---|---|
| α | 低 | P-12 域 `PROGRAM_FUSED` 断言行号 `:318`（批次 27 时点值）现树为 `:367`；重试位用例 `:376` 精确命中 | 已复核（grep），v3 补记 8 已按现值引用 |
| β | 低 | KD-77 跨账本不一致：KB 线 `4341f5ad`（docs-only）自账本 `kb-batch25:401` 称"KD-77 已关闭"，但同文件 `:200` 自留"融合 golden 红、建议保持 open 或拆分"，且全局台账 `known-defects.md` KD-77 仍 open | 已登记（known-defects 批次 35 块；本批不代裁） |
| γ | 低 | 修复环发现 2 内容缺失于工单材料（§5.0）——门禁轨迹佐证处置闭环，但内容不可转录 | 登记在案（如实报告材料边界） |
| δ | 低 | 判据文档 K2 口径更正（15 行 e: = 13 Unresolved + 2 级联）已在索引 §6.3 落——本记录沿索引口径 | 已核对 |

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（修复环后第 2 轮） | **exit=0**（工单给定） | 记录员未复跑；第 1 轮复跑曾 exit=1（§5.0） |
| 强门（修复环后） | **exit=0**（工单给定） | 同上 |
| 修复环第 2 轮 | **处置在位、无需新改动** | §5.0 |
| A-13 入口门 | **PASS EXIT=0** | 记录员本会话实跑 `python tools/ci/check_figure_entry_gates.py` |
| R-04 keep 规则门 | **0 hits EXIT=0** | 记录员本会话实跑 `python tools/ci/check_r04_keep_rules.py` |
| 两门自测 | **18 passed** | 记录员本会话实跑 pytest（`test_check_figure_entry_gates.py`+`test_r04_keep_rules.py`） |
| KD-81 工具回归（含 T5b） | **37 passed** | 记录员本会话实跑 pytest `test_split_gate.py` |
| S=71 静态清单门 | **RESULT=PASS / EXIT=0** | 记录员本会话实跑（T7 §4.2 同命令） |
| 71.json 内容身份 | **sha `4a13d9d0…` / 90 实体 / 2 视图** | 记录员本会话 python 实读 = 设备绿轮 asset 同源 |
| 设备 18 套件两轮 | **全绿，计数=构建前源计数** | E-01 §3；记录员 grep 原始日志复核（§4.3） |
| 9 类崩溃签名 | 两轮 18/18 全 0 | E-01；K6 本体 logcat 6 为唯一非零 |
| R-04 K1–K5 | **PASS** | E-01 §4.1–§4.5；K4 记录员本会话复跑 |
| R-04 K6 | **RED=阻塞** | E-01 §4.6；不放宽不改判据 |
| 批次 35 新增 JVM 用例（T4/T5/T6） | 门禁给定绿；记录员未复跑 | 轨自述"编译未验证"→ 门禁串行执行后纳入工单给定 |
| 生产签名 / arm64 / P-12③ 设备例 | **UNVERIFIED** | §7 |

**记录员保留**：两条 Gradle 门 `exit=0` 无本轮日志落仓，记录员未运行 Gradle；设备数字有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；修复环第 1 轮的单测门红因（发现 2）不可转录（§5.2 γ）。

## 7. 遗留 / UNVERIFIED / 待裁

**R-04 与设备面**

1. **R-04 K6 阻塞未解除**：`androidx.tracing.Trace` NoClassDefFoundError + j$ desugar 错位；扩 `r04-test-keep.pro` 归实现轨（该文件在飞 M）；K6 两例从未在 release 轨执行过；R-04 收口状态=K1–K5 绿 + K6 阻塞。
2. **生产签名 UNVERIFIED**（一次性本地 key）；arm64 真机面未测（x86_64 模拟器）。
3. **P-12③ 设备例未落树未跑**（隔离套件 14 = 13 + P-11 wire）；逐阈值边界（A-10 面）→批次 36。
4. **Q-01/P-12② 预算承继无设备面**（按判据口径 JVM 即可）；P-12③ 解除断言按裁定不可确定性构造——边界已登记。
5. **他线 dense 14 文件**（含生产 Kotlin）功能正确性归 dense 轨；本批仅保证构建窗口源稳定与套件绿绑定。

**代码 / 文档残留**

6. **A-04~A-06 接通与广告开关**：全门绿后最后一步；真实装配值（jsEngineReady/jsMeasurementReady）留 L3 引擎装配批。
7. **A-13 逐入口同果断言（R2 G-10 JVM）未落**；门 2 哨兵弱点已登记（真门=policy 运行时复核）。
8. **tool-round 类型化 errorKind 半边未落**（P-11）；前缀常量未晋升 core/domain（热区）。
9. **判据文档 `:81` 外键描述错误未回改**（§5.1 #13）；T7 证据与 v3 补记 7 的旧行号已由 v3 补记 8 刷新，step-t7-kd-rulings.md 自身为时点快照不改写。
10. **KD-81 残留（不阻塞）**：身份无锁/不绑定读事务的 ABA 上限已登记；`docs/current/visual-generation-contract.md:208` 过时句归契约轨。
11. **KD-77 跨账本不一致待裁**：KB 线自账本称关闭 vs 全局台账 open（§5.2 β）。
12. **5c 载体 `:46` "schema 70" 漂移**：批次 34 已登记，仍未修。
13. **KD-72/73 open**（本批零动作；窗口 6/6 不满足、stdlib 全图扫描仍无落仓产物）。
14. **KD-82 open**（maxLength 码点 vs 字节，known limitation 不排期）。

**环境 / 口径**

15. **一致性门仍是原型门**（step11，`OK (16 tests)`）；不得引用为产品/发版结论。
16. **4E.2 MVA 仍不成立**：本批落的是能力面/对账/失败分类的代码层与门，双后端/质量门就绪未宣称。
17. **首轮设备日志被重绑轮覆盖**（`tee` 同名）：首轮数字为会话转录，盘上为重绑轮（绿绑定轮）。
18. **修复环发现 2 内容缺失**（§5.2 γ）；单测门/强门 exit=0 为工单给定。
19. **热区在飞未触碰**：feature 五模块、core/ui、core/domain（4 个他线 M）、core/markdown、core/export、settings.gradle.kts、.gitignore、app Root 与其余开工即脏 app 文件、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production、tmp/（含设备库副本绝不 `git add`）。
20. **锁文件 14 个**（盘上）：KD-72 窗口不满足，一律不提交。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前按门禁序列复跑单测门/强门并留 `exit=0` 日志；暂存区已有 `VisualFrameInputs.kt`（A），提交时不得夹带其他暂存内容（记录员复验暂存区恰该文件一个）；热区、14 个 `*.lockfile`、`build/`、`tmp/` 一律排除；共享脏文件整文件纳入并在提交说明注明两轨/他线来源。按 ≤6 路径/块：

- **块 1（core/model 能力面主源）**：`core/model/.../Capabilities.kt`、`TutorToolDescriptions.kt`、`FigureGeneration.kt`、`TutorToolTrace.kt`
- **块 2（policy + 两轨共享脏文件）**：`core/model/.../FigureGenerationPolicy.kt`（M；**整文件纳入：A-08 轨 + T6 轨 Q-01/M-04 面**）、`core/model/src/test/.../FigureGenerationPolicyTest.kt`（M；**整文件纳入：两轨用例**）
- **块 3（协调器与引擎；含他线 hunks）**：`core/data/.../visual/DefaultFigureGenerationCoordinator.kt`（M；**整文件纳入：对账 KDoc + T6 P-11/P-12 + 他线 P-11/P-12② hunks**）、`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（M；**整文件纳入：M-04 案 B + 他线 hunks**）、`core/data/.../visual/VisualFrameInputs.kt`（**已暂存；不纳入则新 checkout 编译不过**）、`core/data/.../visual/FigureGenerationReconciler.kt`（M；仅 KDoc）
- **块 4（core/data model 面）**：`core/data/.../model/FigurePromptAssembler.kt`、`core/data/.../model/RoomModelTaskRepository.kt`、`core/data/.../model/OpenAiModelResponseParsers.kt`（M；仅注释）
- **块 5（对账持久层）**：`core/database/.../dao/FigureGenerationReconcileDao.kt`（M；谓词收紧+KDoc）
- **块 6（JVM 用例）**：`core/model/src/test/.../TutorCapabilitySnapshotDualReadinessTest.kt`（新）、`TutorToolDescriptionsSnapshotTest.kt`（新）、`TutorToolTraceTest.kt`、`core/data/src/test/.../FigurePromptParityTest.kt`、`FigureToolPromptInjectionTest.kt`（新）、`FigurePromptCapabilityInjectionTest.kt`（新）
- **块 7（JVM 用例续）**：`core/data/src/test/.../visual/FigureGenerationCoordinatorTest.kt`（M）
- **块 8（设备用例）**：`core/data/src/androidTest/.../visual/FigureGenerationReconcilerInstrumentedTest.kt`、`FigureGenerationAuditFixtures.kt`、`FigureGenerationLocalAttemptOrdinalInstrumentedTest.kt`（新）、`QuickJsVisualExecutionEngineInstrumentedTest.kt`、`app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt`
- **块 9（R-04 面）**：`app/r04-test-keep.pro`（M；注释改指真实门）、`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/DebugModelFailureCaptureInstrumentedTest.kt`（` D`）、`app/src/androidTestDebug/kotlin/com/tingyun/smartmistakebook/DebugModelFailureCaptureInstrumentedTest.kt`（??；**逐字节移动，整文件纳入并注明**）
- **块 10（tools/ci 门与自测）**：`tools/ci/check_figure_entry_gates.py`（新）、`tools/ci/check_r04_keep_rules.py`（新）、`tools/tests/test_check_figure_entry_gates.py`（新）、`tools/tests/test_r04_keep_rules.py`（新）
- **块 11（研究/判据/基线）**：`docs/research/2026-10-30-capability-driven-tools.md`、`2026-10-30-reconcile-and-unattributed.md`、`2026-10-30-batch-35-baseline.md`、`2026-10-30-batch-35-device-criteria.md`
- **块 12（证据 + 记录）**：`docs/research/evidence/2026-10-30-batch-35/00-index.md`、`step-device-track.md`、`step-t7-kd-rulings.md`、`docs/research/2026-10-30-batch-35-record.md`（本文件，新）
- **块 13（台账，整文件纳入）**：`docs/known-defects.md`（M；KD-80/81 批次 35 两块 + KD-82 + KD-72/73/77 批次 35 块）、`docs/REFACTOR-MASTER-LINE.md`（M；批次 35 滚动行 + KD-81 句）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；v3 补记 7/8 + 九行刷新）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；**含批次 33/34 未提交节 + 批次 35 节**）

**明确排除（他线在飞/不安全）**：全部 `*/gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,export,domain}`、`core/markdown/`；core/data 他线 M（dense 面、`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt` 等）；core/database 其余开工即脏 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、`BatchImportDao`、`PendingCaptureDao`、`TutorConversationDao`、`CleanRedrawTaskPort`、LibraryFts 三测）；app 其余 M/??（Root、ReviewReminder*、SecondaryScreens、SchedulingSettingsScreen、LearningMasteryScreen、ExportPdfWorker、SmartMistakeBookDestinations、StartupSta* 等）；`docs/current/**`；docs/research 他线未跟踪记录；`tools/kb*`、`knowledge-production/**`；`build/**`（含设备原始日志）；`tmp/**`；`feature/tutor/.../feature/tutor/nul`（NUL 残留，另处置）。

## 9. 收口状态

- **研究**：R1 386 行（能力参数化形态/双就绪位纯函数派生/入口侧约束/grep 门设计/GitHub 评分）、R2 250 行（不可伪造判据/P-11 两端缺口/P-12② 生产调用点/M-04 折点/Q-01 洞/GitHub 评分）落盘；未验证项如实。
- **实施**：T1 判据 573 行；T2 谓词收紧+3 设备用例+探针红→绿；T3 门补全四项（快照门复核/T5b 红相位/keep 门脚本/KD-82）；T4 A-04~A-06 落码不接通；T5 A-08 入口侧+A-13 门脚本；T6 P-11/P-12②③/M-04 案 B/Q-01/痕迹渲染/注释更正；T7 KD-80 复核维持+KD-81 关闭；T8 分支 D 零改动。
- **集成**：无需接线；schema 71 不变；广告文本零改动；唯一补上的接线=痕迹渲染。
- **设备**：18 套件两轮全绿（34/2/5/9/5/15/4/3/12/12/16/24/16/14/2 + 1/6/27，全部=构建前源计数）；9 类签名 18/18 全 0；源快照 1161 文件含 core/model+core/domain 五窗 diff 空；R-04 K1–K5 PASS、K6 阻塞。
- **门禁**：单测门第 2 轮/强门 exit=0（工单给定）；五组非 Gradle 门/测试记录员本会话实跑全绿。
- **本批不声称全绿/全就绪**：R-04 K6 阻塞未解除；A-04~A-06 未接通（JS 不广告）；生产签名/arm64/P-12③ 设备例 UNVERIFIED；A-13 逐入口断言与 errorKind 半边未落；4E.2 MVA 不成立。
- **台账同步（本轮记录员写入）**：`docs/known-defects.md`（KD-72/73/77 批次 35 Progress 块；KD-80/81 与 KD-82 由 T7/T3 先行写入）、`docs/REFACTOR-MASTER-LINE.md`（批次 35 滚动行）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（v3 补记 8 + 九行刷新）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 35 裁定节）；均标注"记录员只读核对，未运行 Gradle/设备"。命令与输出见 §10 与 `docs/research/evidence/2026-10-30-batch-35/`。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 4341f5ad
git status --porcelain | wc -l                               # 726（238 M + 486 ?? + 1 A + 1 D）
git status --porcelain -- docs/known-defects.md docs/REFACTOR-MASTER-LINE.md \
  docs/agent-first-refactor-decisions-2026-09-23.md \
  "docs/research/2026-10-26-batch-24-4e2-admission-checklist.md"   # 4 × M（改动=T7/T3 前轨 + 本记录员追加）
git ls-files '*lockfile' | wc -l                             # 0
git status --porcelain | grep -ci lockfile                   # 13
find . -maxdepth 3 -name '*.lockfile' -not -path './.git/*' -not -path './.worktrees/*' | wc -l  # 14
ls benchmark/gradle.lockfile                                 # No such file
grep -n '^kotlin = ' gradle/libs.versions.toml               # 3:kotlin = "2.3.21"

# 非Gradle 门/测试（本会话实跑）
python tools/ci/check_figure_entry_gates.py                  # PASS / EXIT=0（3 构造点 :100/:155/:377）
python tools/ci/check_r04_keep_rules.py                      # 0 hits / EXIT=0
python -m pytest tools/tests/test_check_figure_entry_gates.py tools/tests/test_r04_keep_rules.py -q
                                                             # 18 passed in 0.25s
python -m pytest tools/tests/test_split_gate.py -q           # 37 passed in 2.25s（T5b :829 在内）
# S=71 静态清单门（T7 §4.2 同命令）                          # RESULT=PASS / EXIT=0
#   SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71
#   PLAN_HASH=714c0ecb85ee5edf74f0013740148138 JSON_HASH=714c0ecb85ee5edf74f0013740148138
# 71.json 内容身份（python 实读）                            # bytes 378961 sha256 4a13d9d0… / 90 实体 / 2 视图

# 代码锚点（本会话 grep）
# FigureGenerationPolicy.kt :198 validateBasisFingerprint 接线 / :351 实现 / :376 拒绝 / :447 枚举
# Capabilities.kt :87/:92 双就绪位与保留条件 / TutorToolDescriptions.kt :19 figureMethods 缺省
# VisualJsFailureClassification.kt :141 前缀 / DefaultFigureGenerationCoordinator.kt :360 消费、:309 透传、:718 JS 指纹分支
# QuickJsVisualExecutionEngine.kt :62 标记 / FigureGeneration.kt :341 explicitUserRetry、:664 figureJsBasisFingerprint
# FigureGenerationReconcileDao.kt :134/:184 收紧谓词 / TutorToolTrace.kt :428 figure_method_unavailable
# FigurePromptAssembler.kt "JS !in" = 0 命中（fail-closed 已删）
# 三入口 requiresPixelFidelity=false：ConfiguredCleanImageGenerator:105 / AttachedImageGenerator:161,167 / TutorFigureGenerator:360,366
# VisualJsIsolationInstrumentedTest :367 PROGRAM_FUSED / :376 explicitUserRetry=true

# 设备原始日志（build/device-track-batch35/，本会话 grep）
# step*.log 的 OK( 计数 = 34/2/5/9/5/15/4/3/12/12/16/24/16/14/2 + 1/6/27（18 套件逐个复现）
# grep -c @Test：对账 6 / 引擎 27 / 隔离 14 / 本地序号 1（=构建前源计数）
# 新用例方法名在流：step16-local-ordinal / step17-reconcile-js（reservedJavascriptNamedSuccess）/
#   step18-engine-neighbor（aMeasurementFacilityFailureFoldsToEngineClosedWithAStableMarker）/
#   step14-isolation（anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire）
# r04-k6-logcat.txt：NoClassDefFoundError|ClassNotFoundException=2、NoSuchMethodError=4（合计 6，与声明一致）

# 暂存区（修复环处置复验）
git diff --cached --name-only                                # 恰 VisualFrameInputs.kt 一个
git status --porcelain -- <VisualFrameInputs.kt>             # "A "

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 commit/push（VisualFrameInputs.kt 的 git add 暂存由修复环第 1 轮执行，提交动作归主会话，记录员只验状态）；
#         任何 JVM 单测复跑（门禁工单给定）、设备复跑。
```

## 附录 A：台账同步（本轮记录员写入）

| 文件 | 写入内容 |
|---|---|
| `docs/known-defects.md` | KD-72/KD-73/KD-77 各加"2026-10-30 批次 35 记录员复核"Progress 块（KD-72 窗口 6/6 不满足；KD-73 stdlib 2.3.21 不变+K6 desugar 非stdlib 证据口径澄清；KD-77 无 API23 动作+KB 线跨账本不一致登记）。KD-80/81 批次 35 块与 KD-82 由 T7/T3 先行写入，本轨未改 |
| `docs/REFACTOR-MASTER-LINE.md` | 新增"最近计划维护：2026-10-30（批次 35 …）"滚动行；原批次 34 行降为"上次维护" |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 头注 v3 补记 8；刷新 A-04/A-05/A-06/A-08/A-13/P-11/P-12/M-04/Q-01 九行现状（0 项转"通过"，全部"已落码/门已脚本化"） |
| `docs/agent-first-refactor-decisions-2026-09-23.md` | 追加"批次 35 双后端收口 + KD-80/81 裁定（2026-10-30）"节（裁定内容 + 边界） |
| `docs/research/2026-10-30-batch-35-record.md` | 本文件 |

以上均为 docs 追加/刷新；零 Kotlin/JS/Gradle 产品代码改动、零 git 写、零 Gradle、零设备。
