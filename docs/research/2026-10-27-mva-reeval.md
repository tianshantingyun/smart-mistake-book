# 4E.2 MVA 复判（批次 26 R2，2026-10-27）：P-04/P-07/P-09/P-12 现状刷新 + MVA-JS-STATIC 维持不成立

> **性质**：只读审计，零代码、零 Gradle、零设备执行、零 git 写操作。本文件只产出**增量表**，供批次 26 T5 合并进 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（下称**清单**）；清单文件本轮未改（`git status --porcelain -- <清单>` = 0 行，本轮实测）。**本文件不是广告许可**：JS 仍 `UNAVAILABLE`、不广告（MVA 判定见 §2）。
>
> **时点**：HEAD `405c8373`（批次 25 收口，`git rev-parse --short HEAD` 本轮实测）；基线 = 清单 v2（69 行，随 `961c6a0e` 提交，工作树干净）。计划 `.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md`（已完整读，:7 基线、:14 T1/T2/T5 定义）。
>
> **输入**：批次 25 记录 `docs/research/2026-10-27-batch-25-record.md`；批次 25 设备证据 `docs/research/evidence/2026-10-27-batch-25/`（00-index、step01/02）；KD 台账 `docs/known-defects.md`（KD-66 `:2573` open、KD-70 `:2688` closed、KD-72 `:2740` open、KD-76 `:2813` open、KD-77 `:2842` open）；契约 `docs/current/visual-generation-contract.md` §4 `:62-72`；R2 `docs/research/2026-10-27-contract-admission-gap.md`（L-1 `:44`、G-7 `:102-105`）；本轮只读 grep / `git log -S` / 源码通读（§5）。
>
> **口径**：沿用清单 v2 §0 勾选规则——代码存在不算证据，"已落"只表示将来跑该用例不会因类型缺失不可编译；本轮**未运行**任何 Gradle/设备用例，全部设备绿为对批次 25 E-file 与 KD 台账的**转录**，不是本轮实跑；0 项转"通过"。

---

## 0. 结论摘要

1. **四行现状刷新**：P-04 由"客户端无 LOADING/退避重试"→ **LOADING/有界重试代码已落 + 注入形状设备绿（部分）**；P-07 由"设备证据为零"→ **⑤ 首份（部分）设备证据在手，③ 未执行、④ 弱口径，本门仍未过**；P-09 由"未落（无队列）"→ **代码 + JVM 10 例全落、设备 2/3（1 红=A-14 夹具已修未复跑）**；P-12 的 v2"未落"是**口径错误**（把"清单无行"读成了"代码未落"）——熔断状态机与设备断言自批次 20/21/22 已存在，批次 25 T3 补了队列路径的出队二次复查；真正仍缺的是**显式重试预算承继（②）**与熔断专项 JVM/设备断言。
2. **MVA-JS-STATIC 维持不成立**（§2）：批次 25 的落层全部在**前提门/执行面**（warm-up、LOADING、队列、熔断并轨、竞态用例），广告面六处仍全拒，S/M/D/E/A/Q/V/R/X 组未动；隔离面 12+2 未全绿（A-14 未复跑）；KD-66 hard+500 ms 用例未写（E-02 NOT-WRITTEN）。
3. **批次建议**（§3）：A-14 复跑与 KD-76 ③④⑤ 归批次 26 T1/T2（计划已列）；P-04 双态证据、P-08 dumpsys、P-09 客户端队列设备负向、P-12 G-7 ①③ 断言可**并入 T2 同一设备轮**（同夹具、低增量）；P-12 ② 预算承继绑定 A-01/A-10 生产装配（`后续-W`），不单独立批。
4. **给 T5 的漂移登记**（§4）：v2 清单 6 处文本需按本文件刷新，其中 **P-12 行对既有用例的描述与源码不符**是本轮新发现（记录 §5 R22 只登记了 P-04/P-09/`:15` 三处）。

---

## 1. P-04/P-07/P-09/P-12 现状增量表（对照清单 v2 :126/:129/:131/:134）

### 1.1 P-04（冷启动/加载态可解释）——v2"部分落"→ 维持"部分落"，内容刷新

| 项 | v2 时点（清单 `:126`） | 批次 25 落层后（本文件时点） |
|---|---|---|
| LOADING/重试 | "客户端无 LOADING/退避重试"；"连接失败仍一次即永久 unavailable" | **代码已落（批次 25 T2）**：attach/ready 预算超时 = loading 形状——`VisualJsClientEngine.kt:708-711`（attach 超时置 `lastBindFailedOnLoading=true` + loading 诊断）、`:764-765`（ready 超时同型）、`:598-602`/`:633`/`:640`（loading 旗标豁免重建预算检查，不锁存 `unavailable`）、`:684-686`/`:753-754`（死亡/握手成功清旗标）；下一次 execute 重新 bind + 等 ready（ready 轮询 `:785-796`，间隔 100 ms `:1233`，预算 `READY_TIMEOUT_MILLIS=10_000` `:1230`，测试 seam `:809`/`:1176`）；`close()` 在 attach/ready 等待中立即终结（`:301-319`）。**死亡与结构性失败仍锁存**（`:686`/`:723`），"最多重建一次"记账不变（KD-76 台账 Progress 块 `known-defects.md:2829` 段） |
| 最坏等待 | "仍约 19.5 s" | **29.5 s**（首连 15 s + ready 10 s + 宿主阶段 2.5 s + 死亡确认 2 s；契约 `:72` 已同步，记录 §5 R22 指出清单未同步） |
| 设备证据 | 批次 24 E-01/E-01b 冷启动 attach 7.6 s 用例绿；双态未跑 | **新增 2 例注入形状设备绿（批次 25 E-01）**：`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`（attach 推迟 bind + 16 s > 15 s 预算：两次 execute 如实 FAILED、诊断含 loading、不锁存、connectionCount==0）与 `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`（恒 not-ready fake binder + `readyTimeoutOverrideForTest=250`：ENGINE_CLOSED + not-ready 诊断、不锁存、connectionCount 1→2 证明重新握手）——E-01 `step01-visualjs-isolation.md:86-92`（逐例 1→0）、`:173-175`（绿侧结论）。**均为沉降态 + 注入形状**，非真实冷启动 attach 分布 |
| 仍缺 | 双态设备证据；失败后加载/重试态 | ① R2 §三 双态定义（`am force-stop` 立即 vs force-stop 后 ≥2 min 沉降、窗口落 E-file）的**成对**证据未按定义执行——批次 25 设备轮是"emu 冷启动 + 150 s 沉降"单态（`00-index.md:24-26`）；② "满队列 BUSY 后由可见/交互生命周期限次重新调度"调用方生命周期层未落（契约 `:72` 自认属 P-04）；③ 29.5 s worst-case 未设备实测（`00-index.md:95-96` 缺口 7）；④ KD-76 ③ 真实冷启动首请求未执行（见 1.2） |

### 1.2 P-07（warm-up/ready 门，KD-76）——v2"未验、设备证据为零"→ "代码层已落 + ⑤ 首份（部分）设备证据；本门未过"

- **代码层（v2 已记，本轮逐符号复核无误）**：服务 `onCreate` 经单线程 `jsExecutor` 一次性 warm-up（`VisualJsService.kt:113`/`:127-132` → `QuickJsVisualExecutionEngine.warmUpNativeRuntime()`，引擎侧 `QuickJsVisualExecutionEngine.kt:825`，批次 25 新增；warm-up 失败 killSelf `VisualJsService.kt:135`）后置单调 `ready`（`:75` AtomicBoolean）；ready 前 `execute` 回 `SHUTTING_DOWN`、不排队不 arm watchdog（`:242-246`，"结构性保证"）；ready 后每请求仍 arm 1500 ms（`:375-376`；常量 `core/domain/.../VisualExecutionEngine.kt:126` 不变）；AIDL `isReady()` + `PROTOCOL_VERSION=2`（`IVisualJsService.aidl:38`、`VisualJsProtocol.kt:35`）；客户端握手后轮询 ready（`VisualJsClientEngine.kt:785-796`/`:809`）。
- **设备证据：从零 → 首份（部分）**：批次 25 E-01 的 T2 第三例 `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`（warm-up 中 acquire 有界拒绝、无 fatal/误杀、ready 后同 worker 可用）设备绿（E-01 `step01-visualjs-isolation.md:90`、`:173-175`）；连同 1.1 的两例 loading 用例，**KD-76 ⑤（ready 失败/首连失败走明确加载态、不永久 unavailable）拿到行为层首份设备证据**——KD-76 台账批次 25 设备轨 Progress 块自认（`known-defects.md:2813-2841` 内"Progress（2026-10-27 批次 25 设备轨；记录员回填）"段：⑤ 首份部分证据、③ 未达、④ 弱口径）。
- **仍缺（KD-76 关闭门逐项，`known-defects.md:2823` 验收 / `:2825` Reopen）**：
  - **③ 未满足**：真实冷启动（含 CPU 竞争）首个 `execute` 返回 `Rendered` 且日志无该请求 hard-deadline 签名——E-01 是沉降态 + 注入形状；契约 §4 worst-case 29.5 s 启动面未设备实测；隔离进程内一次性装载实测成本 UNVERIFIED。
  - **④ 部分（弱口径）**：warm 服务挂死负控走既有 `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（绿），但计时断言是 `withTimeoutOrNull(6_000L)`（`VisualJsIsolationInstrumentedTest.kt:198`），**无"1500 ms 内"断言**——与 KD-66 同一弱口径。
  - **⑤ 收尾未做**：加载态的 UI/文案面、R3/清单广告门文本同步未完成（`known-defects.md` KD-76 设备轨 Progress 段；记录 §7 第四项）；`READY_TIMEOUT=10 s` 待设备校准。
- **判定**：本门未过；KD-76 open（`:2813`）；**warm-up/ready 落层且设备验证前不得广告**（v2 `:15` 口径维持）。

### 1.3 P-09（队列/背压与消费者校验）——v2"未落"→ "代码 + JVM 全落、设备部分；门未过"

- **v2 现状已失真**（记录 §5 R22 已登记）：v2 写"客户端 `executeMutex` 只串行…无队列、无 *QueueTest*"，依据是附录 B grep `executeMutex` 仅 `:84`/`:167`（清单 `:383`）。批次 25 T3（同提交 `961c6a0e`）落地后，现文件 `executeMutex` **仅存于 KDoc**（`VisualJsClientEngine.kt:65`/`:106`/`:227`，注释明说"不再是旧 executeMutex 的无上界挂起"）。
- **落层事实**：新文件 `VisualJsFrameQueue.kt`（216 行）——全局 1 在飞（`inFlight` 不变量 `:70`/`:88-89`/`:174-180`）、最多 2 待绘（`MAX_PENDING_FRAMES=2` `:206`，满判 `:101`）、同 program latest-wins 原地替换保 FIFO 位、旧等待者回 BUSY（`:95`）、满队列 BUSY 且在飞/待绘帧一概不动、`close()` 闩死清队、`dropPending`（死亡/abandon 清队不闩死）、调用方取消出队；客户端接入 `VisualJsClientEngine.kt:104-108`（`frameQueue` 常驻消费者）、`:210-223`（入队判定短临界区 + `CompletableDeferred` 等待）、`:235-299`（`executeFrame` 消费者会话体：出队**二次熔断复查** `:242-250`、独立 `sessionResult` `:264`、每帧新取 requestId/generation `:263`）。消费者校验：宿主回调身份过滤抽成纯函数 `VisualJsResultFrameFilter`（伪造 generation/requestId/UID/null requestId 全拒、全中才受，JVM 5 例）；服务端 `requireHost` 逐事务 UID、双向 256 KiB 有界读保持（记录 §3.3 `:118`）。
- **证据**：
  - JVM 10 例：`VisualJsFrameQueueTest.kt` 5 例（1 在飞+排队+第 3 个不同 program BUSY、latest-wins 不丢其他 program、排队中取消出队不执行、close 闩死回 ENGINE_CLOSED、死亡清队后重建可再收帧）+ `VisualJsResultFrameIdentityTest.kt` 5 例；复核轨实跑 `python tmp/verify_batch25_integration_jvm_tests.py` → COMPILE_OK + 两类各 `OK (5 tests)`（记录 §3.3 `:119`、R18 `:210`；脚本本轮确认在盘 `tmp/`；**记录员与我均未重跑**，Gradle 门仍空）。
  - 设备 2/3（批次 25 E-01）：`anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead`（INPUT_OVER_BUDGET 有界拒）与 `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight`（**服务端**单在飞 BUSY，直连 AIDL `VisualJsIsolationInstrumentedTest.kt:427-457`）绿；`aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` **红** = A-14 夹具缺陷（第三个负向调用复用已关闭 `pipe[0]`，客户端编组期抛 `Bad file descriptor`，事务未达服务端；E-01 `:139-168` 栈与逐项归因）。
  - **A-14 已修未复跑**：树内已新开 `callbackPipe`（`VisualJsIsolationInstrumentedTest.kt:372-383`，断言不放宽，本轮通读确认）；修复后该用例无设备证据（E-01 维持 PARTIAL，`00-index.md:46`）。
- **仍缺**：① A-14 复跑（批次 26 T2 判据 12+2 全绿，计划 `:14`）；② R2 G-2 的**客户端队列层**设备负向未执行——设备 BUSY 用例在服务端层，经 `engine.execute` 的"4 program 并发 → 1 在飞 + ≤2 待绘 + 第 4 个 BUSY / latest-wins"只在 JVM 队列状态机上验过；③ "可见/交互生命周期限次重调度"调用方层未落（P-04 面）；④ 两项语义决策待主会话确认（死亡清队回 ENGINE_CLOSED；取消在飞帧不再 abandon worker——记录 §3.3 `:121`、契约 `:72` 双标"待确认"）。

### 1.4 P-12（programHash 熔断与本地重试预算承继）——v2"未落"是口径错误 → "部分落；G-7 ② 无证据"

**勘误（本轮新发现，R22 未登记）**：v2 行（清单 `:134`）写"未落（v1 63 行无覆盖；`grep "熔断"` 仅命中 X-03 的 strictOffline 语境，R2 L-1）"，并写"对照既有 `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（只覆盖'重建后不自动执行'，不覆盖 programHash 熔断与显式重试路径）"。**这两句与源码不符**：R2 L-1（`contract-admission-gap.md:44`）的 grep 对象是**清单文本**（63 行清单缺 P-12 行），v2 把它误读成代码层结论。实际：

- **熔断代码自批次 20/21/22 已存在**（`git log -S` 本轮实测）：端口字段 `explicitUserRetry`（`core/domain/.../VisualExecutionEngine.kt:72`，引入 `29b46f21` 批次 20）；引擎侧 `fusedProgramDigests`（`QuickJsVisualExecutionEngine.kt:194`，引入 `1a6bf486` 批次 21 / `69e5f591` 批次 22）；客户端 `fusedDigests`（`VisualJsClientEngine.kt:112`，引入 `69e5f591` 批次 22）。
- **设备断言自批次 22 已存在**：`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 内 `:311-316` 显式断言"熔断：同一 programHash 不显式重试不再下发（契约 §4:70）"→ `PROGRAM_FUSED`（`git log -S PROGRAM_FUSED` 该文件仅 `69e5f591` 一次变更）——该用例随批次 24 E-01/E-01b 6/6 与批次 25 E-01 绿，**G-7 ①（同 hash 自动重发被拒）已有设备绿**。
- **批次 25 T3 增量**：熔断并入队列路径——入队前预检 `VisualJsClientEngine.kt:188-193`、出队二次复查 `:242-250`（排队期间同源码可能已被硬期限/死亡熔断）、宿主阶段超时熔断 `:277`、死亡熔断 `:440`/`:516`/`:915`、显式重试成功后解除 `:294-297`；`Frame` 携带 `digest`/`explicitUserRetry`（`VisualJsFrameQueue.kt:59`"熔断指纹（源码 SHA-256）"/`:62`）。
- **结构性事实**：digest = 源码 SHA-256（`VisualJsClientEngine.kt:187` `figureSha256Hex(sourceJs UTF-8)`）→ 源码变更即新 digest、不在熔断集，G-7 ③（源码变更解除）**结构性成立但无直接断言**。

**刷新后仍缺（G-7 三断言对照，`contract-admission-gap.md:104-105`）**：

| G-7 | 现状 |
|---|---|
| ① 同 hash 自动重发被拒 | **设备绿已有**（`:311-316`，批次 22 起随 E-01 各轮绿）；无"ledger/attempt 计数不变"侧断言（ledger 未接引擎，A-01 未落） |
| ② 显式重试预算承继（不开新逻辑操作） | **无任何证据**：生产侧无 `explicitUserRetry=true` 调用点——全仓 grep 仅测试构造（`VisualJsIsolationInstrumentedTest.kt:319`/`:323`/`:334`、`core/domain` 契约测试 `VisualExecutionEngineContractTest.kt:425`）；生产 `VisualExecutionRequest` 唯一构造在 wire 层 `VisualJsProtocol.kt:185`，且 L3 客户端**无生产装配**（契约 `:72`）；预算承继依赖 A-10 ledger × 引擎装配 |
| ③ 源码变更解除熔断 | 结构性成立（digest=SHA-256）；无直接断言；设备用例 `:319-320`（换 program 后 Rendered）是旁证非门 |

另缺：`theClientRebuildsOnce...` 只验"显式重试旁路熔断再执行"，**"一次成功的显式重试即解除熔断"**（`VisualJsClientEngine.kt:295-297`）无专门断言；G-7 要求的**客户端熔断 JVM 状态机专项测试未建**（现有 JVM 仅端口契约层 `VisualExecutionEngineContractTest.kt:422` + fake `:198`；`VisualJsFrameQueueTest` 5 例只测队列状态机，不触熔断）。

**判定**：部分落。设备 ① 绿 + 结构性 ③；② 与解除路径断言缺；生产装配零（无广告面，无泄漏路径）。

---

## 2. MVA-JS-STATIC 复判：**维持不成立**

批次 25 的全部落层在**前提门/执行面**（隔离进程的可靠性），MVA 的成立条件——"同一规则→模型选方法→一张图→保存重启→PDF"的完整链在设备上闭合且广告面与执行面一致（清单 §1.4）——**没有任何一环因此变绿**。逐组核对（全部为本轮实跑/实读）：

1. **广告面六处仍全拒**（同源变更是广告的完整含义，清单 §1.2）：
   - prompt assembler：`FigurePromptAssembler.kt:40`/`:108` `require(JS !in availableMethods)`；
   - native schema：`OpenAiModelProtocol.kt:453` `method.enum` 仅 `IMAGE_MODEL`；
   - tool loop：`TutorToolLoop.kt:344`（`require(figureProgram == null)`）/`:347`（仅 IMAGE_MODEL）；
   - coordinator：`DefaultFigureGenerationCoordinator.kt:182` 非 IMAGE 分支 → `UNAVAILABLE`（`:164`/`:187`/`:200`）；
   - capability：`Capabilities.kt:71`/`:75` `imageBackendReady` 单就绪位；
   - 隔离执行面本身无生产装配：`VisualExecutionRequest` 生产构造仅 wire 层 `VisualJsProtocol.kt:185`，L3 客户端无生产装配点（契约 `:72`）。
2. **S/M/D/E 组类型零命中未变**（本轮 grep，9 类全 0）：`VisualSceneValidator`/`VisualSceneCompiler`/`VisualStateCodec`/`AndroidScenePainter`/`NativeContentLayout`/`MathContentLowering`/`SceneSvgWriter`/`VisualSceneCard`/`VisualSnapshotPdfBlock`；`visual-sdk*` 资产 0；`settings.gradle.kts:18-34` 无 `:core:visual`/`:core:visual-js`（S-06/X-01 未落）；深冻结仍只有 `Object.freeze(Math)`（`QuickJsVisualExecutionEngine.kt:851`，S-09 未落；行号自 v2 的 `:813` 漂移）；度量仍是 `UnwiredVisualContentMeasurer`（`VisualJsService.kt:382` 注入、`VisualJsProtocol.kt:398` 定义，M-02 未落）。
3. **持久化未动**：DB 仍 v69 两表（`StudyDatabase.kt:134`，D-01 未落）。
4. **P 组内未过的门**：P-05（KD-66 open `:2573`；hard+500 ms 专属用例 **NOT-WRITTEN**，E-02 `step02-kd66-hard-500ms.md:48`；现有 watchdog ≤6 s 弱口径 `VisualJsIsolationInstrumentedTest.kt:52` KDoc 自认 + `:198`）；P-07 ③④⑤（§1.2）；P-08（`dumpsys` 设备审计未执行，静态 grep 基线为 v2 附录 B `:381`）；P-10（死亡未确认 fail-closed 路径在 `VisualJsClientEngine.kt:829`，无 fallback UI、无专属设备负向；E-01 `death was not confirmed`=0 只覆盖确认路径）；P-11（协调器 grep `VisualExecutionEngine` 0 命中——无引擎注入，`[UNATTRIBUTED]` 未被消费）；P-12 ②（§1.4）。
5. **隔离面 12+2 未全绿**：11/12 + 2/2，A-14 修复未复跑（§1.3）；KD-70 的 arm64 轴 UNVERIFIED（E-03 `step03-kd70-p2-ps16k.md` 判定；`00-index.md:67-69`：只可写"16KB × x86_64 已验证"）。
6. **质量门**：真实 Provider 无凭据，Q-05 UNVERIFIED（纪律沿用）。

**结论**：`availableMethods` 不得加入 JS；v2 `:15` 的总体判定句**结论**（不成立）维持，但其**理由表述**需按 §1 刷新（P-07/P-09 已非"未落"，见 §4 第 4 条）。

---

## 3. 仍未落必需项 × 批次建议

| # | 必需项（清单行） | 现状一句话 | 批次建议 |
|---|---|---|---|
| 1 | A-14 用例复跑（P-01/P-09 面） | 夹具已修（`VisualJsIsolationInstrumentedTest.kt:372-383`），无设备证据 | **批次 26 T2**（计划已列"隔离套件复跑修正 A-14+R15 后 12+2 全绿判据"） |
| 2 | KD-66 hard+500 ms 用例（P-05） | NOT-WRITTEN（E-02 `:48`） | **批次 26 T1**（计划已列；hard 杀→宿主按时返回的计时判据 + 夹具） |
| 3 | KD-76 ③ 真实冷启动首请求 + ④ 1500 ms 时限断言（P-07） | ③ 未执行；④ ≤6 s 弱口径（`:198`） | **批次 26 T2 设备判据执行**（计划已列）；④ 与 #2 同夹具，建议一次落两个时限断言 |
| 4 | KD-76 ⑤ UI/文案 + R3/清单广告门文本同步（P-07） | 行为层设备绿在手，文案/门文本未同步 | **批次 26 T5**（合并本文件 §1.2 口径）+ 主会话裁定文案 |
| 5 | P-04 双态成对设备证据 + 29.5 s 实测 | 单态（沉降 150 s）+ 注入形状 | **并入批次 26 T2 同一设备轮**：`am force-stop` 立即 vs ≥2 min 沉降各跑 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`，窗口落 E-file（R2 §三 判据现成） |
| 6 | P-09 客户端队列层设备负向 | 仅 JVM 队列状态机 + 服务端层设备 BUSY | **并入批次 26 T2**：经 `engine.execute` 的 4 并发 → 第 4 个 BUSY / latest-wins（同 harness 低增量）；另需主会话确认记录 §3.3 `:121` 两项队列语义决策 |
| 7 | P-12 G-7 ①③ 专项断言（JVM 状态机 + 设备） | ① 设备绿（批次 22 起）；解除路径无断言 | **并入批次 26 T2**（JVM 专项测试 + `theClientRebuildsOnce...` 补"成功显式重试解除"断言）；不另立批 |
| 8 | P-12 ② 预算承继 + P-11 前缀消费 + A-01~A-09 广告面 | 协调器无引擎注入、无生产装配 | **`后续-W` 装配批**（与 A-10 ledger 接线同批），热区/core:data 归位（X-01）一并裁定；主会话排期 |
| 9 | P-08 dumpsys 设备审计 | 未执行；R2 G-1 命令与断言已备 | **并入批次 26 T2 设备轮**顺带执行（5 条命令，`00-index` 无需新夹具） |
| 10 | S/M/D/E/Q/V/R/X 各组（S-01~S-09、M-01~04、D-01~06、E-01~05、Q-01~06、V-01~04、R-01~05、X-01~03） | 本轮 grep 零命中未变（§2.2/2.3） | 沿用清单 §0 `后续-*` 责任人轨，**未立项，主会话排期**；本批无变化，不因批次 25 落层松门 |

---

## 4. 漂移与勘误登记（T5 合并清单时的输入）

1. **P-04 行（清单 `:126`）**："客户端无 LOADING/退避重试"、"最坏约 19.5 s"已失真 → 按 §1.1 刷新（记录 §5 R22 已登记前半）。
2. **P-09 行（清单 `:131`）**："未落…无队列、无 *QueueTest*"已失真 → 按 §1.3 刷新（R22 已登记）。
3. **P-12 行（清单 `:134`）**："未落"及其对 `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 的描述与源码不符（熔断代码批次 20/21/22 已落、`PROGRAM_FUSED` 设备断言批次 22 已落）→ 按 §1.4 勘误刷新。**R22 未登记此项，为本文件新发现。**
4. **总体判定句（清单 `:15`）**："P-07 warm-up/ready、P-09 队列/背压…均未落"表述过时——P-07 代码层已落 + ⑤ 部分设备证据、P-09 代码 + JVM 全落 + 设备 2/3；**"MVA 不成立"的结论本身维持**（§2），理由改引本文件。
5. **§6 半步条目（清单 `:294`）**："把 close() 完成等待点当加载/重试态已实现"需改写：LOADING/有界重试已落代码层且有注入形状设备绿，剩余缺口是双态真实冷启动证据与生命周期重调度（§1.1 仍缺栏）。
6. **契约 `:72` 的两处"设备复跑 UNVERIFIED"**（warm-up/ready 子句、队列子句）相对批次 25 设备轨 E-01（T2 3 例 + T3 2 例设备绿）滞后——T5 合并时按 `known-defects.md` KD-76 设备轨 Progress 块口径统一为"代码层 + 注入形状设备首验绿；③④⑤ 判据未达"。

---

## 5. 本轮实跑核对（全部只读；未跑 Gradle/设备/git 写）

```bash
git rev-parse --short HEAD                        # 405c8373
git log --oneline -3                              # 405c8373 / 961c6a0e / 25ddb602
git show 961c6a0e --stat                          # 批次 25 提交面：VisualJsFrameQueue.kt(+216)、
                                                  # ClientEngine(458±)、Service(74±)、引擎(58±)、清单(116±)、E-files
git log --oneline -S "explicitUserRetry" -- core/domain/.../VisualExecutionEngine.kt   # 29b46f21（批次 20）
git log --oneline -S "fusedProgramDigests" -- core/data/.../QuickJsVisualExecutionEngine.kt  # 1a6bf486 / 69e5f591
git log --oneline -S "fusedDigests" -- app/.../VisualJsClientEngine.kt                 # 69e5f591（批次 22）
git log --oneline -S "PROGRAM_FUSED" -- app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt  # 69e5f591
git status --porcelain -- <visualjs 三目录> <清单> # 0 行（树内干净；清单未改）
grep -n "READY_TIMEOUT_MILLIS|lastConnectFailure|lastBindFailedOnLoading|isReady" app/.../VisualJsClientEngine.kt
                                                  # :134/:143/:598-602/:708-711/:764-765/:785-796/:809…（§1.1）
grep -n "const val" app/.../VisualJsClientEngine.kt   # :1206 15s / :1212 10s / :1219 2s / :1230 READY 10s / :1233 100ms
grep -n "warmUp|ready|Ready" app/.../VisualJsService.kt   # :75/:113/:127-132/:135/:202-207/:242-246/:375-376
cat app/src/main/aidl/.../IVisualJsService.aidl   # :38 boolean isReady(); PROTOCOL_VERSION 1→2
grep -n "PROTOCOL_VERSION" app/.../VisualJsProtocol.kt    # :35 = 2
grep -n "inFlight|MAX_PENDING_FRAMES|latest" app/.../VisualJsFrameQueue.kt  # :70/:88-89/:95/:101/:174-180/:206=2
grep -c "@Test" app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt  # 12
grep -n -A1 "@Test" app/.../VisualJsHandshakeRaceInstrumentedTest.kt  # :76 markDead…、:126 aDeathDuring…
grep -rn "PROGRAM_FUSED|熔断" app/src/test core/data/src/test core/domain/src/test  # 契约测试 :422/:198 + fake；无客户端熔断专项
grep -rn "explicitUserRetry = true" --include=*.kt app core feature  # 仅测试构造 4 处（:319/:323/:334/:425）
grep -rn "VisualExecutionRequest(" --include=*.kt app core feature（剔测试） # 仅 VisualJsProtocol.kt:185
for p in 9 个 Scene/Painter/Layout/Lowering/ Svg/Card/PdfBlock 类名; do grep -rl "class $p" ...; done  # 全 0
find app core -name "visual-sdk*" -not -path "*/build/*"  # 0
sed -n '62,74p' docs/current/visual-generation-contract.md  # §4 L1/L2/L3 + 29.5 s + 批次 25 T2/T3 子句
sed -n '15,40p' settings.gradle.kts               # 无 :core:visual(-js)
sed -n '340,395p' + '262,339p' + '425,458p' app/.../VisualJsIsolationInstrumentedTest.kt
                                                  # A-14 修复 :372-383；PROGRAM_FUSED :311-316；设备 BUSY=服务端层 :427-457
grep -n "UnwiredVisualContentMeasurer" app/.../VisualJsService.kt .../VisualJsProtocol.kt  # :382/:398
grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt  # :134 = 69
grep -n "Object.freeze" core/data/.../QuickJsVisualExecutionEngine.kt  # :851（仅 Math）
ls tmp/verify_batch25_t2_compile.py tmp/verify_batch25_t2_jvm_regression.py tmp/verify_batch25_integration_jvm_tests.py  # 三个脚本在盘
grep -n "^## KD-66|^## KD-70|^## KD-72|^## KD-76|^## KD-77" docs/known-defects.md  # :2573/:2688(closed)/:2740/:2813/:2842
```

通读（Read 全文/长段）：`plan-2026-10-27-batch-26-kd66-gate-lockfiles.md`、清单 v2 全文、`00-index.md`、`step01-visualjs-isolation.md`、`VisualJsClientEngine.kt:158-320`、`VisualJsIsolationInstrumentedTest.kt:262-458`、`contract-admission-gap.md`（L-1/G-7/P-12 行）。

---

## 6. 未验证项与边界（如实）

- **本轮零 Gradle、零 adb/am instrument、零 git 写**；所有"设备绿"均为对批次 25 E-file（`step01-visualjs-isolation.md`）与 KD 台账 Progress 块的转录，**不是本轮实跑**。
- JVM 10 例（队列 + 身份过滤）的 `OK (5 tests)`×2 来自记录 §3.3/R18 的**转录**（复核轨实跑、记录员未重跑、我亦未重跑）；脚本在盘（`tmp/verify_batch25_integration_jvm_tests.py`），Gradle 门仍空。
- warm-up 实测装载成本、`READY_TIMEOUT=10 s` 校准、29.5 s worst-case、真实冷启动 attach 分布：UNVERIFIED（`00-index.md:95-96` 缺口 7）。
- A-14 修复后的用例：树内已修未设备复跑，**无设备证据**（E-01 维持 PARTIAL）。
- KD-72 锁文件、批次 26 T0 判定窗口：不在本文件范围（R3 预案轨）。
- 我未运行清单 v2 附录 A/B 之外的任何新检查命令的"全套重放"；引用的 v2 附录行号（如 `:381` 合并清单 Provider 基线）以 v2 文本为准，未逐条重跑。
