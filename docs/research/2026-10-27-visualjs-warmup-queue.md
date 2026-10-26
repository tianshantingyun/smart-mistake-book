# R1'：绑定服务 warm-up/ready 与队列/背压设计研究（批次 25，驱动 T2/T3）

- 日期：2026-10-27
- 研究线：R1'（批次 25 R1；驱动 T2 warm-up/ready（KD-76）与 T3 队列/背压（契约 §64）；只给签名级设计与断言清单，不写实现）
- 上游衔接：R1 基线研究 [`2026-10-26-visualjs-isolated-process.md`](2026-10-26-visualjs-isolated-process.md)（进程形态/AIDL/watchdog 落层）、[批次 25 基线核对](2026-10-27-batch-25-baseline.md) §5/§6（现状行号）、[KD-76](../known-defects.md)（warm-up/ready 缺口）、[KD-66](../known-defects.md)（issue #240 关闭门）
- 范围：① 绑定服务 warm-up/ready 的成熟形态（含"ready 前不 arm 硬期限"）；② §64 队列/背压（1 在飞 + ≤2 待绘 + latest-wins + 满→BUSY 保帧 + 消费者校验）实现模式；③ 契约 §64 → 可测试断言清单；④ `executeMutex` 串行与 §64 的冲突与最小改造。
- 不在范围：4E.2 交互/保真、JS 广告、真实 Provider（模型质量 UNVERIFIED）、Gradle/设备执行（共享纪律：本线不跑 Gradle；所有运行时结论均标 UNVERIFIED 或引用既有设备证据）。

## 0. 结论先行

1. **warm-up/ready（T2，KD-76 ①方向）**：成熟形态 = **"服务进程在服务任何请求之前，于专用后台线程完成一次性引擎装载，装载完成置 ready 标志；未 ready 时所有需要引擎的操作拒绝/等待；宿主只在 ready 后发首个请求；ready 等待有独立预算、超时 fail-closed 显示加载态"**。三个独立来源：官方 `ZygotePreload`（官方 preload 钩子，API 29+，本项目暂缓仅作形态引用）、AOSP `Watchdog`（只监视已注册的线程/监视器——"arm 在已 ready 的对象上"）、Chromium `ChildProcessService`（onCreate 起专用线程装载原生库→`mLibraryInitialized` ready 标志→原生依赖操作在 ready 前 refuse→父进程经 `setupConnection/finishSetupConnection` 握手才认为子进程可用）。落到本仓库：服务端 `onCreate` 后台 warm-up（构造一次性 `QuickJsVisualExecutionEngine` 即触发 `QuickJs.create` 原生装载，[QuickJsVisualExecutionEngine.kt:420](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt)）+ AIDL 新增 `isReady()`（两路只读，PROTOCOL_VERSION 1→2）+ 宿主 IO 轮询 ready（独立预算，不占 2500 ms 宿主阶段期限）；**已 ready 后的真实程序执行仍 1500 ms 硬杀不变**（[VisualJsService.kt:305](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)）。
2. **队列/背压（T3，§64）**：`Channel(CONFLATED)`（单槽 DROP_OLDEST）与 `MutableStateFlow`（全 key 单值）都**单独**表达不了 §64 的组合语义（跨 program FIFO + 每程序 latest-wins + 容量 2 满→BUSY）；直接可用的官方原语是 `trySend` 的不挂起失败（BUSY 信号）与 CONFLATED 的"只留最新"语义，组合需要**自定义 keyed 有界队列**（`ArrayDeque` + 短临界区 `Mutex`，无新依赖）。被同 program 新状态顶掉的旧等待者回 `Rejected(BUSY)`（端口语义=背压保原帧，[VisualExecutionEngine.kt:281](../../core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt)），不共享新结果（有 ledger/指纹错配风险）。
3. **§64 → 断言清单**：§64 逐句映射出 14 条断言（§4 表）；现状已有证据 6 条（隔离 UID/回调过滤/有界读写/过期拒绝等），新增 8 条，其中"≤2 待绘/latest-wins/满→BUSY"三条是 T3 的核心新增，"phase 字段"一条是现状 wire 缺口（需裁定映射或新增）。
4. **executeMutex 冲突（T3 最小改造）**：`executeMutex.withLock`（[VisualJsClientEngine.kt:167](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)）把"连接（≤15 s）+ 会话（≤2.5 s）+ 死亡确认（≤2 s）"整体串行，排队方等待无上界且无 latest-wins——与 §64"1 在飞 + ≤2 待绘 + 满→BUSY"直接冲突（契约 [:72](../current/visual-generation-contract.md) 已如实登记）。最小改造 = **队列只管排队、锁不跨会话**：`executeMutex` 收缩为保护 `ArrayDeque<PendingExecution>`（cap 2）与 `inFlight` 标志的短临界区，会话跑在锁外，常驻消费者协程按序排水；改动面收敛在 `VisualJsClientEngine.kt` 单文件 + 测试，端口/服务端/AIDL（除 T2 的 `isReady`）零改动。

## 1. 证据通道与等级

| 通道 | 结果 | 说明 |
|---|---|---|
| `gh api`（chromium/chromium，pin `ed3e89c78a09a1809239407e7cdaa66155279e7f`，与 R1 §1.1 同 pin） | 成功（`ChildProcessService.java` 两次、`ChildProcessConnection.java` 一次；中间一次 HTTP 429，换时段重试成功） | warm-up/ready 实例源码 |
| `gh api`（aosp-mirror/platform_frameworks_base，pin `1cdfff555f4a21f71ccc978290e2e212e2f8b168`，与 R1 §1.1 同 pin） | 成功（`Watchdog.java`、`ZygotePreload.java`） | "arm 在已注册对象上"与官方 preload 钩子 |
| WebFetch `developer.android.google.cn`（官方镜像，R1 §1 同通道） | 部分成功（`ZygotePreload` 页面仅返回导航层，正文改从 AOSP 源码 javadoc 取得） | 官方文档镜像直取失败→换 AOSP 通道 |
| WebFetch `kotlinlang.org`（kotlinx.coroutines 官方 API 文档） | 成功（`Channel`、`MutableStateFlow` 两页） | 队列原语语义 |
| 本仓库只读 | 成功 | 契约/KD/代码/AIDL/仪器化测试清单（行号为 2026-10-27 实读，HEAD `d783ae84`） |
| Gradle/设备 | **未运行** | 按共享纪律；运行时行为结论要么引既有设备证据（E-01b/d 等），要么标 UNVERIFIED |

等级标记：**[官方]** = 官方文档/API 参考；**[AOSP]** = AOSP 源码 javadoc/实现（GitHub mirror，非实时 HEAD）；**[样例]** = 开源项目实际代码；**[推断]** = 由以上推出、未设备验证。

### 1.1 来源 pin（本轮实读）

| 仓库 | 读取的 commit / 日期 |
|---|---|
| `chromium/chromium` | `ed3e89c78a09a1809239407e7cdaa66155279e7f`（2026-10-25 钉版，沿用 R1；本轮 2026-10-27 实读 3 个文件） |
| `aosp-mirror/platform_frameworks_base` | `1cdfff555f4a21f71ccc978290e2e212e2f8b168`（2025-03-27 mirror，沿用 R1；本轮 2026-10-27 实读 2 个文件） |
| kotlinx.coroutines 官方文档 | `kotlinlang.org/api/kotlinx.coroutines/...`（`-channel/` 与 `-mutable-state-flow/` 两页，2026-10-27 抓取） |

## 2. 主题一：绑定服务 warm-up/ready 的成熟形态

### 2.1 三个独立来源的公共形状

**形状 A（官方钩子）：`ZygotePreload` —— 官方"服务任何请求前完成预载"**。[AOSP] `core/java/android/app/ZygotePreload.java`（pin commit，`:19-44`）原文：

> "It is responsible for preloading application code and data, that will be shared by all isolated services that have the `android:useAppZygote` attribute of the `<service>` tag set to `true`."
> "This method is called once every time the Application Zygote is started. **It is normally started the first time an isolated service that uses it is started.**"

即官方对"隔离进程冷启动装载成本"的正面回答是：把装载挪到服务被使用之前的钩子里（`doPreload`），且**在第一个隔离服务启动时执行一次**。限制：API 29+ 且必须 `useAppZygote=true`（多一个常驻 app zygote 进程，官方 javadoc 明说"代价是多一个进程占内存"——R1 §2.4 已按此暂缓本项目使用）。**对本项目的意义：不是采用项，而是"官方承认冷启动装载成本应当 warm-up 而不是让首个请求承担"的定性依据。**

**形状 B（AOSP watchdog）：arm 只发生在"已存在、已 ready"的对象上**。[AOSP] `services/core/java/com/android/server/Watchdog.java`（pin commit）：

- `addThread(Handler)` / `addThread(Handler, long)`（`:645-658`）：被监视对象通过**显式注册**进入 `mHandlerCheckers`——watchdog 只检查注册过的线程；`addMonitor` 同理（`:640-643`）。
- `init(Context, ActivityManagerService)`（`:533`）：watchdog 与被监视对象的绑定在系统就绪时建立；`:1028` 注释 "**If a watched thread hangs before init() is called, we don't have a valid mActivity**"——watchdog 自身就区分"init 前"与"init 后"两个阶段。

**对本项目的意义：watchdog 的正确 arm 时机是"被监视的工作对象已建立之后"，而不是"进程启动即全局计时"。** 这直接支持 KD-76 的"ready 之前不 arm 程序 watchdog"：1500 ms 程序 watchdog 监视的对象是"已装载完成的 QuickJS 引擎上的一次事务"（R1 §5.1 的"期限起点=JS 工作真正开始"，[R1 §5.1](2026-10-26-visualjs-isolated-process.md)）；一次性原生装载不是被监视对象，不应占用它的预算。

**形状 C（生产级实例）：Chromium 子进程服务的 warm-up/ready 握手**。[样例] `base/android/java/src/org/chromium/base/process_launcher/ChildProcessService.java`（pin commit；BSD-3-Clause）：

| 证据点 | 位置（pin commit） | 原文/事实 |
|---|---|---|
| **装载发生在 onCreate、服务任何请求之前** | `onCreate` javadoc `:227-229`："**Loads Chrome's native libraries and initializes a ChildProcessService**"；`onCreate` 起专用线程 `mMainThread` 跑 `mainThreadMain()` | 装载不在 Binder 线程、不在主 looper——专用后台线程 |
| **装载完成才置 ready 标志** | `mainThreadMain()` `:289-293`：`mDelegate.loadNativeLibrary(getApplicationContext())` → `synchronized (mLibraryInitializedLock) { mLibraryInitialized = true; mLibraryInitializedLock.notifyAll(); }` | ready 标志 + 条件变量通知；装载与 ready 置位在同一临界区语义下先后发生 |
| **未 ready 时，需要引擎的操作 refuse（不排队、不计时）** | `onSelfFreeze` `:199-206`、`dumpProcessStack` `:211-217`：`if (!mLibraryInitialized) { Log.w(TAG, "Cannot do SelfFreeze before native is loaded"); return; }` | "ready 前不接工作"的现成写法 |
| **宿主经握手才知道子进程可用** | `setupConnection` `:155-181`：父进程调 `setupConnection`，子进程完成后回 `parentProcess.finishSetupConnection(pid, zygotePid, startupTimeMillis, relroInfo)`（两路回调）；父进程侧 `ChildProcessConnection.java` `:238-240`："**This has to be called exactly once after setupConnection(), even if setup fails, so that the caller doesn't hang**"，`:477-479`：`start()` 后**必须**跟 `setupConnection()` 才算可用 | 宿主侧"连接成功 ≠ 可用"，可用性由 ready 握手定义；且**握手必须一次性终结（成功或失败都通知），调用方不得悬挂** |
| **bind 失败立即通知、不留悬挂** | `ChildProcessConnection.start()` `:485-503`：`if (!bind(...)) { Log.e(...); notifyChildProcessDied(); }`（"We have to notify the caller so that they can free-up associated resources"） | 失败路径必须显式终结，不得让调用方等到外部超时 |
| **硬终止仍是子进程自杀** | `forceKill()` `:193-196`：`Process.killProcess(Process.myPid())` | 与本项目 L2 同形（R1 §2.3 已裁定宿主杀不了隔离 UID） |

三个来源交叉一致：**（i）装载挪到服务请求路径之前；（ii）ready 是显式状态，不是"连接成功"的隐含推论；（iii）ready 前到达的工作要么等待（宿主侧有界）要么拒绝，绝不占用程序执行预算；（iv）ready 握手必须有界终结，失败 fail-closed。**

### 2.2 本仓库现状（为什么这是缺口）

- 服务端**无任何 warm-up/ready**：`VisualJsService` 的 `initialize(generation)` 只做 generation 记账（[VisualJsService.kt:150-165](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)），`QuickJsVisualExecutionEngine` **每请求现造**（`:309-310`），真正的原生装载发生在引擎事务内的 `QuickJs.create(...)`（[QuickJsVisualExecutionEngine.kt:420](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt)）——即**冷启动的装载成本落在首个用户请求的 1500 ms watchdog 预算内**（watchdog 在引擎工作前 arm，`:305`、`:412-428`；常量 [VisualExecutionEngine.kt:126](../../core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt)）。批次 23 设备日志已在冷启动捕捉到同形状："hard deadline (1500 ms) … killing the worker"（KD-76 引 `step01d-visualjs-isolation-coldboot.md:65`，归因冷启动负载）。
- 宿主**无 ready 语义**：`warm/ready/loading` 在 `visualjs` 源码与 AIDL 全部零命中（[批次 25 基线 §5](2026-10-27-batch-25-baseline.md) `:289` 实测）；首连 15 s 预算（[VisualJsClientEngine.kt:920](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)）只覆盖 attach+握手，不覆盖"服务内引擎装载完成"。
- AIDL 面没有 ready 查询：`IVisualJsService.aidl` 只有 `protocolVersion/workerUid/currentGeneration/initialize/execute/terminate`（`:17-36`）。

### 2.3 T2 设计（签名级；最终形态按 KD-76 需主会话在①/②间裁定，本节给①的完整配方与②的对照）

**服务端（`VisualJsService`，改动面：服务 + AIDL + 协议版本）：**

1. **warm-up 任务**：`onCreate`（或首个 `initialize` 前）向 `jsExecutor` 投一个一次性任务（复用单线程执行器天然串行，warm-up 与首个请求不可能并发执行 JS）：

   ```kotlin
   private val ready = AtomicBoolean(false)
   private val warmupFailure = AtomicReference<Throwable?>(null)

   private fun warmUpOnJsExecutor() {
       jsExecutor.execute {
           try {
               // 一次性装载：构造即触发 QuickJs.create（libquickjs.so 装载 + 运行时表预热），
               // 立即关闭——不执行任何用户程序；[推断] 装载点在 QuickJs.create，
               // E-01d 设备证据已证明首个 execute 存在一次性的 >1500 ms 原生装载成本。
               QuickJsVisualExecutionEngine(measurer = UnwiredVisualContentMeasurer).close()
               ready.set(true)
           } catch (failure: Throwable) {
               warmupFailure.set(failure)
               killSelf("visual_js warm-up failed: ${failure.javaClass.simpleName}")
           }
       }
   }
   ```

   - **它消灭的失败**：KD-76 的"冷启动首个 execute 在 CPU 竞争下被 1500 ms watchdog 误杀"（E-01d 形状）——装载成本移出任何请求的 watchdog 预算。
   - warm-up **不 arm 程序 watchdog**（形状 B：watchdog 的对象是"已装载引擎上的事务"）；warm-up 自身是否加独立期限（如 10 s 到点 killSelf）**待主会话裁定**：不加则依赖宿主 ready 预算兜底 fail-closed（下条），加了则多一层防"装载永久挂死"——两者都不放宽已 ready 后的 1500 ms 程序期限（KD-76 红线）。
   - warm-up 失败（引擎都构造不出）= 进程不可信 → `killSelf`（与现有 `fatal()` 同型，[VisualJsService.kt:398-410](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)），宿主走既有死亡→重建预算→fail-closed 路径，不新增第三条恢复路径。
2. **ready 查询（AIDL 变更）**：`IVisualJsService.aidl` 新增 `boolean isReady();`（两路、只读原子量；每方法照旧 `requireHost()`）。**`PROTOCOL_VERSION` 1→2**（两端同 APK 发布，旧/新组合在握手处 fail-closed 解绑，既有语义 [VisualJsClientEngine.kt:776-779](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)）。
   - 备选（不采用的理由）：`oneway onReady(generation)` 回调——多一条回调生命周期（linkToDeath/unlink 配对）；`initialize()` 改为阻塞至 ready——它跑在两路 Binder 事务上，宿主 `onServiceConnected` 在**主线程**回调（[官方 ServiceConnection](https://developer.android.com/reference/android/content/ServiceConnection)：回调发生在 main thread），阻塞秒级有 ANR 风险，且违背 R1 §4.6"JS/重活不进 Binder 线程"纪律。
3. **防御**：`execute` 在 `!ready.get()` 时回既有 `STATUS_BUSY`？**不**——BUSY 的语义是"在飞背压"（契约 §64"满队列回 BUSY"）；未 ready 是另一事实。最小方案：宿主在 ready 前不发请求（下条），服务端再加一道 `if (!ready.get()) return STATUS_SHUTTING_DOWN;`（语义"现在不接"，宿主既有映射把它折 `ENGINE_CLOSED` + fail-closed，不会与 BUSY 背压混淆）。是否新增独立状态码（如 `STATUS_NOT_READY`）**待主会话裁定**（新增=协议面变更更大，但诊断更精确）。

**宿主端（`VisualJsClientEngine`，改动面：client + 测试）：**

4. **ready 轮询**：`connectLocked` 握手成功后、返回 `Link` 前，在 IO 协程轮询 `isReady()`（间隔 ~100 ms，复用 `awaitDeathConfirmation` 的轮询先例 `:622-647`）：

   - **ready 预算独立**（推荐 `READY_TIMEOUT_MILLIS = 10_000L`，与首连 15 s/重建 10 s 同级；设备实测冷启动 attach 4.6–7.5 s（E-01d），装载叠加其上需实测校准，此值是**待验证起点**）；**不占**宿主 2500 ms 阶段期限（与 `ensureConnected()` 现状一致，期限外预算，契约 [:72](../current/visual-generation-contract.md) 口径表要同步加一列）。
   - ready 超时 → `markAbandoned` + `unbindService` + **不置永久 `unavailable`**（这是与现状"首连超时永久关闭"的关键差异；KD-76 "ready 超时/失败必须 fail-closed 并如实显示加载态，不得把加载失败写成程序错误" + P-04 的 LOADING/有界重试缺口）→ 本请求折 `Rejected(ENGINE_CLOSED, "visual_js not ready within Xms")`，下一次 `execute` 允许重新 bind+等待 ready（**有界重试**的语义落点；重试次数上界属 P-04 的生命周期限次，本设计不引入无限重试）。
   - 这改变 `unavailable` 的一次性语义面（现状 13 处写入、0 处复位，[基线 §5 :288](2026-10-27-batch-25-baseline.md)）——**ready 超时不写 `unavailable`**，只清当前 link；结构性失败（bind 失败/协议不匹配/同 UID/死亡未确认）照旧写。改动需逐点核对这 13 处的归类。
5. **请求路径不变**：ready 后的 `execute` 与现状完全一致（2500 ms `withTimeout` 只包会话；服务端 per-request watchdog 不变）。**不在宿主实现"首请求豁免"**——KD-76 明令"不得用首请求放行掩盖 #240 挂死"；挂死负控（warm 服务上 #240 形状仍 1500 ms 硬杀）是 KD-76 设备判据 ④。

**②方向对照（首个请求一次性装载单独计时）**：服务端把"进程启动后第一次 `QuickJs.create`"从 per-request watchdog 预算里拆出、单独计时（如独立 10 s），完成后再 arm 同一 1500 ms watchdog。优点：无 AIDL/协议变更、无宿主改动；缺点：(a) "首请求"仍承担装载等待（用户感知的第一个请求延迟不变），(b) 服务端需要跨请求记住"是否首次"，(c) 宿主 2500 ms 期限会先到（装载 >2.5 s 时宿主已 abandon，②的拆分对首个请求无效——**除非同时把宿主首请求期限也拆分**，改动反而更大）。**因此推荐①**；若主会话裁定②，宿主侧仍需处理 2500 ms 期限问题，这一点 KD-76 未展开，须回签。

### 2.4 ready 与 T3 队列的衔接

队列排水（§5）只在 `Link` 处于 `CONNECTED && isReady` 后开始；ready 轮询期间到达的 `execute` 在宿主队列里排队（占 ≤2 槽）还是直接拒？**推荐：ready 未定时队列不启用，首个请求失败即返回**（与 2.3-4 的"下一次 execute 重新 bind"配合）；避免"装载期排了 2 个等待者、ready 超时后全队失败"的放大。最终口径随主会话对 ②/加载态的裁定一并定。

## 3. 主题二：§64 队列/背压的实现模式

### 3.1 官方原语语义（能直接借用什么）

| 原语 | 官方语义（本轮实读引文） | 对 §64 的适配与不适配 |
|---|---|---|
| `Channel(CONFLATED)`（[kotlinx.coroutines Channel 文档](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/)） | "creates a buffer for a single element and automatically changes the buffer overflow strategy to `BufferOverflow.DROP_OLDEST`" | **latest-wins 单槽**正是"每 program 只留最新 state"的语义；但它是**每通道**的：要 per-program 就得每 program 一个 Channel（程序数量无上界，与"最多两个待绘"的容量门冲突），且 CONFLATED **永远不会满**（旧元素自动被顶掉），给不出"满队列→BUSY"；也没有跨 program 的 FIFO 顺序保证（多通道消费顺序需另建仲裁） |
| `trySend`（同上页） | 非 suspend 对应 `send`；**通道满/关闭时立即返回失败的 `ChannelResult`，不等待不抛** | 这是"满→BUSY"信号的正确原语形态（调用方拿失败值决定回 BUSY）；但容量语义要自定义队列配合（`Channel(2)` + `trySend` 可给出"≤2 待绘 + 满→BUSY"，仍缺 per-program 合并——需先做 keyed 去重再入队） |
| `onUndeliveredElement`（同上页） | "called once for each element … that failed to be delivered"（溢出丢弃/发送取消/通道取消） | 被顶掉/被丢弃帧的**fd/信封清理钩子**（本队列元素是 `ByteArray`+`CompletableDeferred`，需完成旧等待者——见 §3.2-4） |
| `MutableStateFlow`（[官方页](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-mutable-state-flow/)） | 本轮直接抓到的句子："all emission-related operators, such as `value`'s setter, `emit`, and `tryEmit`, are conflated using `Any.equals`"；（该页指向的 StateFlow 页有"slow collector 只见最新值/emit 不挂起"的完整措辞——本轮未直接抓取该页原文，**该句按 [官方]-摘要转述处理**） | 全局单值 conflate：只适合**单 program 单帧**的"rendering state"槽（§6 的"latest-wins 绘制"在 UI 层可用它）；做不了多 program FIFO、无容量、`tryEmit` 永不失败（无 BUSY 信号） |

**结论：没有任何单一现成原语直接等于 §64 的组合（1 在飞 + ≤2 待绘 + per-program latest-wins + 跨 program FIFO + 满回 BUSY）。** 可组合路径有两条：

- **路径甲（推荐）**：自定义 `ArrayDeque<PendingExecution>`（cap 2）+ 短临界区 `Mutex`（或 `synchronized`）+ `CompletableDeferred` 等待者 + 常驻消费者协程。latest-wins 与 FIFO 在一个数据结构里同时成立，BUSY 是一次显式判断；零新依赖（coroutines stdlib 已在用）。
- **路径乙**：`HashMap<programId, PendingExecution>`（每 key 单槽=latest-wins）+ `ArrayDeque<programId>`（FIFO 顺序）+ 容量判断——当 program 数恒为 2-3 时与甲等价，但两结构要手动保持一致（多一个不变量），无收益。**不推荐。**

### 3.2 队列设计（T3；与 §2.4 ready 衔接）

```kotlin
// VisualJsClientEngine 内；替换 executeMutex 的职责（见 §5）
private data class PendingExecution(
    val programId: String,
    val digest: String,                       // 熔断指纹（与现 executeOffMainThread 一致）
    val envelope: ByteArray,                  // 已编码请求信封
    val waiter: CompletableDeferred<VisualExecutionResult>,
)

private val queueMutex = Mutex()              // 只保护下面的状态，绝不跨会话持有
private val pending = ArrayDeque<PendingExecution>()   // 容量 2；同 program 只留最新
private var inFlight = false                  // §64"全局一个在飞"
private val queueConsumer = scope.launch { drainLoop() }  // 常驻消费者（close() 取消）
```

入队判定（`queueMutex` 内，全部 O(容量)）：

1. `!inFlight` → 置 `inFlight=true`，元素直接交给消费者（等价旧路径首帧直跑）。
2. `inFlight && pending.any { it.programId == 新.programId }` → **latest-wins**：替换该元素；旧等待者 `complete(Rejected(BUSY, "superseded by a newer state of the same program"))`——端口语义 BUSY=背压保留原帧（[VisualExecutionEngine.kt:32](../../core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt)、enum `:281`；契约 §6"绘制丢帧不得丢状态更新" [:129](../current/visual-generation-contract.md)：state 更新在协调器/持久层，不随帧丢）。**不采用**"旧等待者共享新结果"：结果按 dispatch 指纹记账（`RoomFigureDispatchLedger`），跨 dispatch 复用结果会错配台账；且旧 revision 的调用方拿新 revision 的场景违反 §8"sceneRevision 只可回显"。
3. `inFlight && pending.size == 2 && 无同 program 可合并` → **满队列回 BUSY**：`Rejected(BUSY, "visual_js frame queue is full (1 in-flight + 2 pending)")`；在飞与两个排队帧**一概不动**（§64"保留原帧…不把另一张图误当旧帧丢掉"）。
4. 否则 `pending.addLast`（跨 program FIFO 由 ArrayDeque 天然成立）。
5. 调用方协程在排队期间被取消 → `queueMutex` 内移除该元素（`waiter` 已 cancel，无泄漏）；队列元素只持 `ByteArray`/`String`，无 fd（fd 只在会话内创建），被顶掉帧的清理=完成等待者，无 `onUndeliveredElement` 类资源泄漏面。

消费者循环：`drainLoop()` 取队头 → `ensureConnected()`（连接预算照旧在期限外）→（T2 后）`awaitReady()` → 复用现有 `runSession` → 完成后回到 1。**在飞期间 `ensureConnected`/会话不持 `queueMutex`**——这是与现状 `executeMutex` 的本质区别（§5）。死亡/abandon：当前会话由既有 `handleDeath` 收尾（熔断 + `HARD_DEADLINE_EXCEEDED`）；**队列处置推荐"清空 + 全部 `ENGINE_CLOSED`"**（与"不并发放大进程数"一致、最保守；备选"留队等待重建"会放大死亡时的不确定窗口）——**待主会话确认**。`close()`：取消消费者、清队列、所有等待者 `ENGINE_CLOSED`。

服务端保持现状单在飞（`inFlight` CAS → `STATUS_BUSY`，[VisualJsService.kt:186-190](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)）作为纵深防御：客户端队列使服务端 BUSY 在正常路径不可达，但若两个客户端进程/未来多宿主并发，服务端语义仍正确。

### 3.3 消费者校验：调用身份 / 描述符方向 / 长度（§64 末句）

§64："消费者须验证服务调用身份、描述符方向与长度，不暴露宿主通用 RPC。" 两个方向的消费者都要验：

| 校验 | 请求方向（服务消费宿主请求）现状 | 结果方向（宿主消费服务结果）现状 |
|---|---|---|
| 调用身份 | 每个 AIDL 方法 `requireHost()`：`Binder.getCallingUid() != applicationInfo.uid` → `SecurityException`（[VisualJsService.kt:127-132](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)；R1 §4.4：`onBind` 的 calling UID 不可靠，必须逐事务查） | 回调内 `Binder.getCallingUid() != connection.workerUid` → 丢弃并关 fd（[VisualJsClientEngine.kt:388-391](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)；nextvol 同型先例 R1 §10.3） |
| 描述符方向 | AIDL 显式 `in ParcelFileDescriptor request`（[IVisualJsService.aidl:32-33](../../app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl)）；接收端 `dup()` 后只读（`:197-204`） | AIDL 显式 `in ParcelFileDescriptor payload`（[IVisualJsCallback.aidl:30](../../app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsCallback.aidl)）；宿主 `dup()` 后只读（`:440-449`） |
| 长度 | `readBoundedRequest` 流式计数，>256 KiB 立即 `OverBudget`（[VisualJsService.kt:444-464](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)） | `readResultPayload` 同口径上限（[VisualJsClientEngine.kt:459-461](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)） |
| 不暴露通用 RPC | 接口仅 6 个具名控制方法，无任意方法派发；generation/requestId 全程核对（服务端 `:182-185`/`:216-222`，宿主 `:384-387`） | 回调仅 `onResult`/`onFatal` 两个具名 oneway；旧 generation/旧 requestId/非 worker UID 帧一律丢弃关 fd |

**T3 需要补的不是校验本身，而是这些校验的负向测试**（现状 6 例仪器化测试全走合法路径；外来 UID 拒绝在 nextvol 有先例、本仓库无用例）——见 §4 断言 A-13/A-14。

## 4. 主题三：契约 §64 → 可测试断言清单

§64 原文（[visual-generation-contract.md:64](../current/visual-generation-contract.md)，本轮实读）逐句编号 S1–S7；每条断言给：现状证据（文件:行）、断言（可测试形式）、建议测试层级、状态。

| # | §64 句 | 断言（可测试） | 层级 | 现状 |
|---|---|---|---|---|
| A-1 | S1 控制消息走 AIDL，源码/JSON 走有界 PFD，不逐图元发 Binder | 64 KiB 源码 + 16 KiB state 的请求经 `execute(generation, requestId, readEnd, callback)` 往返成功（Binder 事务只带控制字段） | 仪器化 | **已有绿**：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（[VisualJsIsolationInstrumentedTest.kt:101](../../app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt)，批次 24 E-01/E-01b 6/6 之一） |
| A-2 | S1 读写循环计字节、可取消 | ① 服务端读 >256 KiB → `INPUT_OVER_BUDGET` 拒绝且**不**先无界读入（断言：读循环计数单调、超限即返）；② 宿主结果读 >256 KiB → 结果按截断拒绝；③ 会话超时/取消后写协程被解除（`writeJob.cancel` + 关写端） | JVM（读循环）+ 仪器化（端到端超预算） | 部分：超预算路径代码在（[VisualJsService.kt:455](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)、[VisualJsClientEngine.kt:459-461](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)）；**超预算负向用例未见**（6 例无此形）→ 新增 |
| A-3 | S2 每任务有 requestId、generation | 每次执行 `execute(generation, requestId, …)` 双标识齐全；回调按二者过滤 | 仪器化 | **已有绿**：`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（`:146`）；回调过滤代码在（[VisualJsClientEngine.kt:384-387](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)） |
| A-4 | S2 "phase" | **现状缺口**：wire 信封（[VisualJsProtocol.kt:69-80](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt)）无 phase 字段。若 §64 的 phase 指"初生成 vs 重绘帧/帧序"，T3 需裁定：映射 `sceneRevision`（已在信封内）或新增字段（PROTOCOL_VERSION 再升）。断言（裁定后）：队列内同 program 新帧的 revision 单调递增 | 待裁定 | 无 |
| A-5 | S3 全局一个在飞 | ① 服务端：第二并发 `execute` → `STATUS_BUSY` 且请求 fd 已关；② 客户端：任一时刻至多一个活动会话（`inFlight` 不变量） | 仪器化（①）+ JVM（②） | ① BUSY 分支代码在（[VisualJsService.kt:186-190](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)）；**并发第二请求 → BUSY 的直接负向用例无**（`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`:171`）证的是"watchdog 杀掉后新 generation 无 BUSY"，不是并发 BUSY，不能计为覆盖）→ 新增；②现状是 `executeMutex` 串行（不满足"≤2 待绘"）→ T3 新增 |
| A-6 | S3 最多两个待绘 program | 第 3 个不同 program 在 1 在飞 + 2 待绘时到达 → 立即 `Rejected(BUSY)`，前 2 个待绘帧与其后完成不受影响 | JVM 状态机 + 仪器化 | **无**（现状无界排队）→ T3 新增（核心） |
| A-7 | S3 各保留一个最新状态（latest-wins） | 同 program 连发两个 state（rev N、rev N+1）：rev N 的等待者收到 `Rejected(BUSY, "superseded…")`，rev N+1 正常渲染；队列长度不因同 program 增长 | JVM 状态机 + 仪器化 | **无** → T3 新增（核心） |
| A-8 | S3 跨 program 按序处理 | program A、B、C 依序入队 → 完成顺序 = A（在飞）→ B → C（FIFO），无交错 | JVM 状态机 + 仪器化 | **无** → T3 新增 |
| A-9 | S4 满队列回 BUSY 并保留原帧，由生命周期限次重调度 | BUSY 拒绝时：在飞帧照常完成、两个待绘帧照常完成；BUSY 调用方重试 N 次内不放大队列（重调度限次由调用方生命周期约束，断言连续 BUSY 不改变 `pending.size`） | JVM 状态机 | **无** → T3 新增 |
| A-10 | S4 不把另一张图误当旧帧丢掉 | 不同 program 的帧互不合并：program B 的帧永不因 program A 的新 state 被顶掉（A-7 只作用于同 programId） | JVM 状态机 | **无** → T3 新增 |
| A-11 | S5 初次工具生成按已有工具序列执行 | 队列只作用于重绘/重排路径：`DefaultFigureGenerationCoordinator` 调用面零改动（断言：协调器现有测试全绿 + 引擎端口签名不变） | JVM | **已成立**（T3 设计约束，落地后回归验证） |
| A-12 | S6 旧帧与不再匹配场景的事件拒绝 | 旧 generation/旧 requestId 的 `onResult` 被丢弃且 fd 关闭（代码在 `:384-387`）；服务端 stale generation 拒绝（`:182-185`） | 仪器化 | **已有绿**：`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`；**旧 requestId 帧丢弃的独立负向用例未见** → 补一条单测（回调直接以错 requestId 调用） |
| A-13 | S7 消费者验证调用身份 | 非宿主 UID 调服务 → `SecurityException`（`requireHost`）；非 worker UID 的回调帧被丢弃关 fd（`:388-391`） | 仪器化（服务端负向；nextvol 用外来 isolated UID 转发的先例，R1 §10.3-3）+ JVM（宿主回调过滤） | 服务端负向**无用例** → 新增；宿主回调过滤随 A-12 单测覆盖 |
| A-14 | S7 描述符方向与长度、不暴露通用 RPC | 断言保持结构性：AIDL 中两处描述符均显式 `in`；读写两端均有字节上限（A-2）；接口无任意派发面（具名方法清单固定） | JVM（结构断言/编译面） | **已成立**（A-2 落地后完整） |

**与 KD-76 设备判据的衔接**（不在本表重复展开，KD-76 原文 §验收判据）：③冷启动首请求绿且无 watchdog hard deadline 签名、④warm 服务挂死负控仍 1500 ms 硬杀、⑤ready 超时走加载态不永久 unavailable——三条是 T2 的设备门；T3 的 A-5~A-10 中标"仪器化"的条目与之同批落盘（按 [设备验证证据规范](2026-10-26-device-evidence-convention.md)）。

## 5. 主题四：`executeMutex` 串行与 §64 的冲突及最小改造

### 5.1 冲突点（现状 vs §64，逐条）

| 现状（[VisualJsClientEngine.kt](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)） | §64 要求 | 冲突 |
|---|---|---|
| `executeMutex = Mutex()`（`:84`）+ `withLock` 包住**整个会话**（`:167-221`）：`ensureConnected`（首连 ≤15 s，`:180`）+ 2500 ms 会话（`:191`）+ 超时后 `NonCancellable` 死亡确认（≤2 s） | "全局一个在飞绘制" | 无冲突（串行 ⊃ 单在飞），但**排队无上界**：第 N 个等待者最坏等 (N-1)×(15+2.5+2) s；契约 [:72](../current/visual-generation-contract.md) 已登记"排队方的等待不设上界（与引擎端口的 BUSY 背压语义不同）" |
| 排队 = `Mutex` 挂起的调用方 FIFO，无容量、无合并 | "最多两个待绘 program（各保留一个最新状态），只合并相同 program 的旧状态，跨 program 按序处理" | **冲突**：无容量门（第 3、4、5… 个都排队）；同 program 多帧全串行执行（旧帧结果浪费 1500 ms 预算且无人消费）；无 latest-wins |
| 满/忙无客户端级信号（`STATUS_BUSY` 只来自服务端，而 `executeMutex` 下客户端从不并发到服务端） | "满队列回 BUSY 并保留原帧" | **冲突**：调用方只能无限挂起，拿不到 BUSY |
| `ensureConnected` 在锁内：连接期间（≤15 s）所有等待者都不动 | （隐含）队列不因连接/装载放大 | **冲突**：冷启动场景第 2 个调用者挂在锁上比首个多等整个首连 |

### 5.2 最小改造方案（不引入新依赖；改动面=1 个文件 + 测试）

1. **锁职责收缩**：`executeMutex` 改名/改用途为 `queueMutex`——只保护 `pending`（`ArrayDeque`，cap 2）、`inFlight: Boolean` 两个量；**任何连接、IPC、`withTimeout`、`await` 都不发生在持锁区间**。这消灭"一个慢会话阻塞所有入队判定"的现状根因。
2. **入队判定**（§3.2 的 5 条规则）+ **常驻消费者** `drainLoop()`（`scope.launch`；`close()` 取消）。会话体**原样复用**现有 `runSession`（含先 execute 后写信封、dup 读取、超时 abandon 全套），只是调用点从"调用者协程内联"移到"消费者协程"；调用者只持 `waiter.await()`。等待者的超时/取消语义：调用方自己的 `withTimeout`/取消作用于 `await()`，超时后消费者侧会话照常由既有 finally 收尾（`activeCall` 清理 `:212-214`、写端关闭 `:363-367`），无需新机制。
3. **等待者结果映射**：被顶掉= `Rejected(BUSY, "superseded by a newer state of the same program")`；满队列= `Rejected(BUSY, "frame queue is full")`；死亡/关闭= `Rejected(ENGINE_CLOSED, …)`（§3.2-3/§3.2-5）。端口零改动（`BUSY`/`ENGINE_CLOSED` 已在 [VisualExecutionEngine.kt:281](../../core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt) 与拒绝清单），**这是"最小"的关键：不改端口、不改服务端、不改 AIDL（T2 的 `isReady` 除外）、不新增 rejection 枚举**。
4. **熔断/预检位置不变**：字节预算、`PROGRAM_FUSED`、信封编码仍发生在入队前（`:126-165` 的预检原样保留在 `execute()` 前段）——超预算/熔断的拒绝不占队列槽，语义与现状一致。
5. **死亡与 close 的队列处置**：见 §3.2（推荐清空 + `ENGINE_CLOSED`；备选留队待重建，需主会话确认）。
6. **测试（先红后绿）**：
   - JVM 状态机（`runTest` + 可控 fake 会话延迟）：A-5②/A-6/A-7/A-8/A-9/A-10 六条不变量；死亡/关闭清队；排队中取消。
   - 仪器化（真进程）：三 program 并发 → 第三者 BUSY、前两个 `Rendered`；同 program 两 state → 仅最新渲染；跨 program FIFO 顺序（结果按 `sceneRevision`/programId 断言完成序）。
   - 回归：现有 6 例隔离套件必须原样绿（尤其 `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`——队列改动不得破坏重建后首请求直跑路径）。

**不采用的替代**：① 服务端实现队列（把 1+2 挪进 `VisualJsService`）——服务在隔离进程，多积压帧会拉长进程生命周期与内存（16 MiB JS 堆外的信封缓冲），且 BUSY 背压本就该在调用方边界；② `Channel(2)` + 先 keyed 合并再入队——两段结构仍需自写合并锁，与路径甲等价但多一层通道语义（挂起 send/trySend 混用易错）；③ `bindIsolatedService` 多实例分流——直接违反契约"不能并发放大进程数"（R1 §2.4 已拒）。

## 6. 未验证 / 剩余风险（如实）

1. **本线未运行 Gradle/测试/设备**：§4 清单中"已有绿"条目全部引自批次 23/24 设备证据（KD-66/KD-76 与 [批次 25 基线](2026-10-27-batch-25-baseline.md) 实读），本轮未复跑；"新增"条目是断言，不是已通过的检查。
2. **warm-up 收益本身 UNVERIFIED**：装载点在 `QuickJs.create`（[QuickJsVisualExecutionEngine.kt:420](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt)）是读码结论；"warm-up 后首个 execute 不再被 1500 ms 误杀"必须按 KD-76 验收判据在设备轨（冷启动 + CPU 竞争）证明，本设计不预支。READY_TIMEOUT=10 s 是**待设备实测校准的起点值**。
3. **②方向（首请求装载单独计时）未展开宿主 2500 ms 期限的配套改动**：若主会话裁定②，本研究的宿主侧结论（§2.3-4）需重做。
4. **被顶掉等待者回 `BUSY`（而非新增 SUPERSEDED 枚举）**：端口语义贴合（背压保原帧），但"等待者如何向 UI 解释"属加载态/呈现层（P-04 轨），本研究只定引擎层映射。
5. **死亡时清队列 vs 留队待重建**：推荐前者（§3.2/§5.2-5），需主会话确认；两者对"A-9 生命周期限次重调度"的实现方（调用方）均成立。
6. **Chromium/AOSP 行号按 pin commit**（mirror 非实时 HEAD）；`ChildProcessConnection.java` 首次抓取遇 GitHub 429，重试成功，引用内容以重试所得为准。StateFlow 页"slow collector 只见最新值"句经 `MutableStateFlow` 页转引，未直接抓取 StateFlow 页原文（§3.1 已标注）。
7. **真实 Provider 无凭据**：模型质量与 JS 生成质量 UNVERIFIED，与本研究无耦合。
8. **§64 "phase" 的语义裁定**（A-4）：本研究未在契约其他处找到 phase 字段定义；若指 §3.2 事务阶段（prepare/measure/render），则它是服务端内部事实、无需 wire 传输——两种读法都成立，需 R2/主会话定谳后才能写断言。

## 7. 来源

### 7.1 官方文档与 AOSP（本轮实读）

1. `ZygotePreload.java`（AOSP mirror，pin `1cdfff555f4a21f71ccc978290e2e212e2f8b168`）：`https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/core/java/android/app/ZygotePreload.java`（`:19-44` javadoc 引文）；官方参考页（正文抓取失败，仅存导航层）：https://developer.android.google.cn/reference/android/app/ZygotePreload
2. `Watchdog.java`（同 pin）：`.../services/core/java/com/android/server/Watchdog.java`（`addThread` `:645-658`、`init` `:533`、hang-before-init 注释 `:1028`；uptime 理由 `:858-862` 已由 R1 §5.1 引）
3. kotlinx.coroutines 官方 API 文档（kotlinlang.org，2026-10-27 抓取）：
   - `Channel`：https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/ （CONFLATED=单槽 DROP_OLDEST、trySend 不挂起失败、onUndeliveredElement 引文）
   - `MutableStateFlow`：https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-mutable-state-flow/ （"conflated using `Any.equals`" 引文）
4. `ServiceConnection`（主线程回调事实，§2.3-2 采用理由）：https://developer.android.com/reference/android/content/ServiceConnection

### 7.2 开源样例（本轮实读；pin 与 license）

5. **Chromium `ChildProcessService.java`**（BSD-3-Clause，pin `ed3e89c78a09a1809239407e7cdaa66155279e7f`）：`https://github.com/chromium/chromium/blob/ed3e89c78a09a1809239407e7cdaa66155279e7f/base/android/java/src/org/chromium/base/process_launcher/ChildProcessService.java`（onCreate javadoc `:227-229`、`mLibraryInitialized` `:86/:101-102/:290-293`、refuse-before-ready `:199-206/:211-217`、`setupConnection`/`finishSetupConnection` `:155-181`、`forceKill` `:193-196`）
6. **Chromium `ChildProcessConnection.java`**（同 pin/license）：`.../ChildProcessConnection.java`（`start()` bind 失败即通知 `:485-503`、`setupConnection` 必须一次性终结的回调契约 `:238-240`、与 `onServiceConnected` 的顺序 `:551-570`）

### 7.3 本仓库（本轮实读；行号 2026-10-27，HEAD `d783ae84`）

7. [visual-generation-contract.md](../current/visual-generation-contract.md) `:58`（阶段能力）、`:62`（隔离与 guard）、`:64`（§64 队列/背压/消费者校验）、`:72`（终止层落地状态与已知限制）、`:84`（丢帧合并）、`:129`（事件/渲染队列、latest-wins 绘制、丢帧不丢状态）
8. [known-defects.md](../known-defects.md) KD-66 `:2573-2613`、KD-76 `:2805-2821`
9. [VisualJsClientEngine.kt](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt)（`:84` executeMutex、`:167-221` 锁内全会话、`:180` ensureConnected、`:191` 2500 ms、`:384-391` 回调过滤、`:440-461` dup+上限、`:486-503` ensureConnected、`:622-647` 死亡确认轮询、`:920/:926/:933` 预算）
10. [VisualJsService.kt](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt)（`:127-132` requireHost、`:150-165` initialize、`:186-190` 单在飞 BUSY、`:305` watchdog arm 点、`:309-321` 每请求现造引擎、`:371-383` 收尾顺序、`:412-428` armWatchdog、`:444-464` 有界读）
11. [VisualJsProtocol.kt](../../app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt)（`:31` PROTOCOL_VERSION、`:69-80` 信封结构——无 phase 字段）
12. [VisualExecutionEngine.kt](../../core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt)（`:32` 单在飞/BUSY 端口语义、`:123/:126/:129` 三层期限常量、`:281` `BUSY` 枚举）
13. [QuickJsVisualExecutionEngine.kt](../../core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt)（`:420` `QuickJs.create` 装载点）
14. [IVisualJsService.aidl](../../app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl) `:29-36`、[IVisualJsCallback.aidl](../../app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsCallback.aidl) `:30-31`
15. [VisualJsIsolationInstrumentedTest.kt](../../app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt)（6 例 `:101/:146/:171/:216/:251/:266`）
16. [2026-10-26-visualjs-isolated-process.md](2026-10-26-visualjs-isolated-process.md)（R1 基线：§2.3 宿主杀不了、§5.1 watchdog 形状、§5.4 重建一次、§10 来源）、[2026-10-27-batch-25-baseline.md](2026-10-27-batch-25-baseline.md)（§5/§6 现状行号、§7 P-04/P-07/P-09）
17. 本轮执行的取证命令（可复跑）：`gh api repos/chromium/chromium/contents/...ChildProcessService.java?ref=ed3e89c7…`（两轮）、`…ChildProcessConnection.java?ref=ed3e89c7…`（两轮，首轮 429 重试成功）、`gh api repos/aosp-mirror/platform_frameworks_base/contents/…Watchdog.java?ref=1cdfff55…`、`…ZygotePreload.java?ref=1cdfff55…`、WebFetch kotlinlang.org 两页与 developer.android.google.cn 一页；本地 `grep`/`sed`/`Read` 引用行号逐条核对。

## 8. 实现期修正（2026-10-27，T2 落地时登记）

研究复核高危项裁定：**§2.3-1 的 warm-up 草图不成立，已按保守分支修正实现**。

- **草图错误**：`QuickJsVisualExecutionEngine(measurer = UnwiredVisualContentMeasurer).close(); ready.set(true)` 的注释称"构造即触发 QuickJs.create（libquickjs.so 装载 + 运行时表预热）"。T2 读码复核（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`，HEAD `d783ae84`）：构造函数只初始化 measurer/期限、stateLock/fused 集合与 watchdog 线程池，**不引用 QuickJs 类型**；`QuickJs.create` 全文件唯一调用点在 `runPhase` 的事务路径内（本节写作时的 `:420`）。按草图实现会得到 ready=true 但 libquickjs.so 根本未装载——恰是 KD-76 ③ 要抓的失败形状，且偏离 KD-76 ①「完成 libquickjs.so 装载与一次空程序 warm-up」原文（草图丢掉了空程序执行）。本节 §2.3-1 与 §0.1 的同款表述以本节为准。
- **修正实现**：warm-up **直接触达装载点**——`QuickJsVisualExecutionEngine` companion 新增 `warmUpNativeRuntime()`：`QuickJs.create(Dispatchers.Default)` → 一次 `void 0;` evaluate（①「装载 + 一次空程序」的原文口径；上游 `evaluate` 是 suspend，函数内 `runBlocking` 在调用线程完成；无用户 job，不经过 issue #240 的排水循环）→ close。服务 `VisualJsService.onCreate` 经 jsExecutor（单线程，与请求天然串行）调用；失败 → killSelf（既有进程级恢复，不新增第三条路径）。
- **§2.3-3 的待裁定项按保守分支落地**：不新增 STATUS_NOT_READY，`execute` 在 `!ready` 时回既有 `STATUS_SHUTTING_DOWN`（正常流程不可达——宿主只在 `isReady()` 为真后发请求；可达即协议违规，fail-closed）。**待主会话追认**。
- **§2.3-4 按原文落地**：宿主 `connectLocked` 握手成功后轮询 `isReady()`（100 ms 间隔）；`READY_TIMEOUT_MILLIS=10_000`（待设备校准起点）；ready/attach 预算超时不写 `unavailable`、不扣重建预算（`lastBindFailedOnLoading` 旗标），下一次 execute 重新 bind + 等 ready；诊断经 `lastConnectFailure` 区分 loading 与结构失败。补充事实（本研究未展开、T2 落地时确立）：loading 放弃后的晚到 attach 经 `Link.abandonedForLoading` 收尾（markDead(unexpected=true)，handleDeath 在 ABANDONED 上短路）——否则首连超时的重试会被 1 s 后的晚到 attach 锁存；死亡路径上的重建照旧按「最多重建一次」记账。
- **与 T3 的合成事实**：T2 落地时 `VisualJsClientEngine` 已被批次 25 T3 重构（`executeMutex` → `VisualJsFrameQueue` 常驻消费者），T2 的 ready 轮询/loading 语义落在 T3 版本之上（ready 等待发生在消费者的 `ensureConnected` 调用内，排队帧在 loading 期间按 §64 队列语义等待）；两轨改动联合编译通过（`tmp/verify_batch25_t2_compile.py` 全阶段 COMPILE_OK）。
- **验证边界**：本轨未运行 Gradle/设备；非 Gradle 编译检查与既有 JVM 回归（`QuickJsVisualExecutionEngineJvmTest` 2/2）是本轮全部执行证据；KD-76 ③④⑤ 设备判据 UNVERIFIED。
