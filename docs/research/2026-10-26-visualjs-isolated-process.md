# R1：`:visual_js` 隔离进程 / AIDL / 进程级硬终止研究（批次 22，4E.2 前置）

- 日期：2026-10-26
- 研究线：R1（驱动 T1；不写业务实现，只给签名级配方与来源）
- 范围：`android:isolatedProcess` vs `android:process`、AIDL 回调/oneway/generation/DeathRecipient、watchdog 自杀形状、app 模块 AIDL 构建、契约 §4 三层终止映射。
- 不在范围：4E.2 交互图/4E.3 保真、SceneV1 compiler、模型质量（无真实 Provider 凭据，`UNVERIFIED`）、Gradle/设备执行（共享纪律：本线不跑 Gradle，门禁由脚本串行执行）。

## 0. 结论先行

**选型：`android:isolatedProcess="true"` + `android:process=":visual_js"` 的非导出绑定 Service；硬终止只由服务进程自己执行 `Process.killProcess(Process.myPid())`（watchdog 到点或 AIDL `oneway terminate(generation)` 核对通过），宿主侧只做“废弃 generation → 请求终止 → unbind → Binder death 确认 → 最多重建一次”，不假设宿主能杀不同 UID。**

一句话理由：隔离进程的 UID 与宿主不同、没有自己的权限、不挂载应用数据目录，宿主用 `Process.killProcess(servicePid)`/`ActivityManager.killBackgroundProcesses` 都杀不到它；能杀任意循环（含 issue #240 的自续 job）的只有服务进程内的 SIGKILL。`android:process=":visual_js"`（同 UID 普通独立进程）虽然宿主可以直接 kill，但保留全部应用权限/文件/网络，违反契约 §4 的隔离前提，只能作为被拒候选。

## 1. 证据通道与等级（先说清楚“我是怎么知道的”）

| 通道 | 结果 | 说明 |
|---|---|---|
| `developer.android.com` 直连 | 失败（`curl` 20s 超时，000） | 本机网络到 Google 直连不可达 |
| `developer.android.google.cn` 官方镜像 | 成功（302/200） | 官方文档同一内容，本次经此取文 |
| `gh api`（GitHub，已认证） | 成功 | Chromium/AndroidX/AOSP mirror 源码、AIDL、文档 |
| `gh search code` | 成功 | 开源样例定位（nextvol/TinaIDE/duckduckgo 等） |
| 本地 `javap`（`C:/Android/Sdk/platforms/android-37.0/android.jar`） | 成功 | 只核签名存在性，不证明运行时行为 |
| Gradle/设备 | **未运行** | 按共享纪律，本线不跑 Gradle；设备行为均标 `UNVERIFIED` |

等级标记：**[官方]** = Android 开发者文档；**[AOSP]** = AOSP 源码（GitHub mirror）；**[样例]** = 开源项目实际代码；**[本地实测]** = 本次 `javap`；**[推断]** = 由前两者推出、未设备验证。

### 1.1 来源 pin（行号按这些 commit）

| 仓库 | 读取的 commit / 日期 |
|---|---|
| `aosp-mirror/platform_frameworks_base` | `1cdfff555f4a21f71ccc978290e2e212e2f8b168`（2025-03-27；mirror 非实时 HEAD，**AOSP 行为可能已漂移**） |
| `chromium/chromium` | `ed3e89c78a09a1809239407e7cdaa66155279e7f`（2026-10-25） |
| `androidx/androidx` | `4ea31479e2bd1d24759aad359f60e80ff35beccb`（2026-10-25） |
| `Renakoni/nextvol` | `c6e82c56f2cb8ded078662082c63da7c787211fe`（2026-10-13，Apache-2.0） |
| `wuxianggujun/TinaIDE` | `e9f4e852c1e2adf246fd3ba880b362312e131415`（2026-10-25，GPL-3.0，仅参考思路，不复制） |
| `mozilla-firefox/firefox` | `efd2d1e6f77570ae6bc5294731a68101e45c81a3`（2026-10-24，文件头 MPL-2.0） |
| `duckduckgo/Android` | `71cba3eafee571cd88682a4fc4932e539bab2efe`（2026-10-23，Apache-2.0） |

## 2. 两种形态：`android:process=":visual_js"` vs 再加 `isolatedProcess`

### 2.1 官方定义

**[官方]** `<service>` 属性文档（[service-element](https://developer.android.com/guide/topics/manifest/service-element)）：

- `android:process`：进程名；以 `:` 开头时是“a new process, private to the application”。
- `android:isolatedProcess`：“If set to 'true', this service runs under a special process that is isolated from the rest of the system and **has no permissions of its own**. The only communication with it is through the Service API, with binding and starting.”
- `android:exported`：`false` 时“only components of the same application or applications with the same user ID can start the service or bind to it”；无 intent-filter 时默认就是 `false`。

**[AOSP]** 同义源码：`attrs_manifest.xml:3102-3105`（“The only communication with it is through the Service API (binding and starting)”）；`ServiceInfo.FLAG_ISOLATED_PROCESS = 0x0002` 的 javadoc（`ServiceInfo.java:53-58`）。

### 2.2 逐项差异（含证据）

| 维度 | `android:process=":visual_js"`（同 UID） | `android:isolatedProcess="true"` + `:visual_js` |
|---|---|---|
| UID | 与应用同 UID | AMS 分配隔离 UID（`Process.FIRST_ISOLATED_UID = 99000`，`Process.java:346`；`ProcessList.newProcessRecordLocked` 分配，`ProcessList.java:3385`） |
| 权限 | 继承应用全部运行时/普通权限 | 不取包 gids：`if (!app.isolated) { getPackageGids(...) }`（`ProcessList.java:1885-1890`）；“no permissions of its own” |
| 文件 | 可读写 `filesDir`/数据库/偏好 | **不挂载自己的应用数据目录**：“If it's an isolated process, it should not even mount its own app data directories, since it has no access to them anyway.”（`ProcessList.java:2515-2518`） |
| 网络 | 有 `INTERNET` 即可 | 无权限；AOSP 注释“isolated apps which cannot access network related services”（`ActivityThread.java:7636-7639`）；Mozilla 文档亦记“tighter SELinux confinement / isolated_app”（[GeckoView isolated-process](https://github.com/mozilla-firefox/firefox/blob/efd2d1e6f77570ae6bc5294731a68101e45c81a3/mobile/android/docs/geckoview/project/isolated-process/overview.md)） |
| 系统服务 | 正常 | `getCommonServicesLocked(true)` 的 bind 参数预取只给 `package`/`permissionmgr`，“so that we don't violate the rules about which services they have access to”（`ActivityManagerService.java:2779-2790`）；其余服务是否可用由 SELinux/权限决定，**不要假设与普通进程等价** |
| Binder | 可用 | 可用；官方限定“只能通过 Service API（binding/starting）通信”；不能像普通进程那样 start/bind 大多数其他组件（Mozilla 文档；DuckDuckGo 注释“most system services return null and bindService() is forbidden”，[MultiProcessApplication.kt:30-34](https://github.com/duckduckgo/Android/blob/71cba3eafee571cd88682a4fc4932e539bab2efe/app/src/main/java/com/duckduckgo/app/global/MultiProcessApplication.kt#L30-L34)） |
| 进程复用 | 可复用同 UID 进程 | **不能复用**：“If this is an isolated process, it can't re-use an existing process.”（`ProcessList.java:2689`）；AMS 每次 `startProcessLocked(isolated)` 都拿新进程（`ActiveServices.java:5877-5883`） |
| Application | 每个进程都建 Application | 同样建 Application（除非走 `runIsolatedEntryPoint` 的内部路径，本方案不用）→ **guard 必需** |
| 宿主 kill | 同 UID，`Process.killProcess(pid)` 可用 | **不可用**：不同 UID，kernel 拒绝（[Process.killProcess 文档](https://developer.android.com/reference/android/os/Process#killProcess(int))：“only the process running the caller's packages/application and any additional processes created by that app; packages sharing a common UID...”）；`ActivityManager.killBackgroundProcesses` 也不行（见 §2.3） |
| 硬终止 | 宿主 SIGKILL | **只能服务自己 SIGKILL**：`Process.killProcess(Process.myPid())` |

**同一 manifest 里的对照实例**：nextvol 把不可信源执行放在 `isolatedProcess=true` 的 `:source_execution`（`app/src/main/AndroidManifest.xml:128-132`），同时把普通浏览器服务放在 `android:process=":source_browser"` / `:source_browser_native`（同文件 `:141-144`，无 `isolatedProcess`）。这正是“普通独立进程 vs 隔离进程”的现成对照：前者是同 UID 的兄弟进程，后者才是无权限沙箱。

### 2.3 宿主到底能不能杀？

三个候选逐个排除：

1. **`Process.killProcess(servicePid)`**：`Process.java:1358-1367` 的实现是 `sendSignal(pid, SIGNAL_KILL)`；官方 javadoc 明确 kernel 只允许杀“自己的包/应用、以及该应用创建的进程、同 UID 的包”。隔离进程的 UID 不同，不属于同 UID；AIDL 官方指南里的示例注释也逐字写了这句话（[AIDL guide](https://developer.android.com/develop/background-work/services/aidl)，`Binding` 示例的 `kill` 按钮）。**结论：不可用。**

2. **`ActivityManager.killBackgroundProcesses(packageName)`**：
   - 文档（[ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String))）：“Have the system immediately kill **all background processes** associated with the given package… On devices that run Android 14 or higher, third party applications can only use this API to kill their own processes.”
   - AOSP 实现：`killBackgroundProcesses` → `killPackageProcessesLSP(packageName, appId, userId, SERVICE_ADJ, ...)`（`ActivityManagerService.java:3673`）；该函数有两道过滤：
     - `if (app.mState.getSetAdj() < minOomAdj) continue;`（`ProcessList.java:3098`）——只杀 **adj ≥ SERVICE_ADJ** 的进程；被可见宿主绑定的服务 adj 更低，直接跳过；
     - `if (!isDep && UserHandle.getAppId(app.uid) != appId) continue;`（`ProcessList.java:3124`）——隔离进程的 `uid` 是隔离 UID（appId 99000+），不等于包 appId，也跳过。
   **结论：双重过滤，至少一道排除；不是可用终止手段，更不是单实例定向终止。**

3. **`unbindService` / `stopService`**：只触发 Service 生命周期；如果 JS 线程卡在 native/自续 job，`onDestroy` 可能根本执行不到；`unbind` 也不是同步终止（契约 §4 原文“不把 unbind 当同步终止”）。**结论：只能作为“移除重启理由”的配套动作，不是 kill。**

因此：**“主进程在 deadline 后杀执行进程并重建”在 isolated 形态下必须翻译成“服务进程自杀 + 宿主确认死亡 + 宿主最多重建一次”**；宿主没有可用的 kill API。这正是契约 §4:68 的原文设计（“硬超时由服务自己 killProcess(myPid())”“不假定宿主能杀不同 UID”）。

**严格读法与效果读法（必须在 T1 说清，不能静默降级）：**

- 若“主进程杀”被严格理解为“由主进程向执行进程发 SIGKILL”：`isolatedProcess` **不满足**（宿主不同 UID，无权限）；只有同 UID `:process` 满足，但它同时丢掉文件/网络/权限隔离。
- 若按效果理解（deadline 后执行进程确实死掉、宿主能确认并重建）：`isolatedProcess` **满足**，代价是“杀”由服务自己执行（watchdog 或 `terminate`），宿主只发请求/确认。
- 契约 §4:68 明确采用效果读法（“硬超时由服务自己 killProcess(myPid())”“不假定宿主能杀不同 UID”）。若产品后来硬性要求“宿主亲自发信号”，那是一次需要裁定的范围变更（退回同 UID 形态或引入系统权限），**不是本批可以静默做的降级**。

### 2.4 被拒/暂缓候选（各自消灭不了哪个失败，或引入哪个新失败）

| 候选 | 判定 | 理由 |
|---|---|---|
| `:visual_js` 同 UID（不加 isolated） | **拒** | 宿主可直接 kill，但保留全部文件/网络/权限；契约 §4:62“隔离进程不申请文件/网络主机绑定”直接不成立。安全边界换来的只是“宿主少写一个 terminate”，不值得。 |
| `bindIsolatedService(..., instanceName, ...)` 多实例 | **暂缓/拒（本批）** | 官方文档说它“allows the caller to generate multiple instances of a service from a single component declaration”（[Context.bindIsolatedService](https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection))），且需 API 29；本项目 minSdk 23。契约要求“不能并发放大进程数”，多实例正好相反。 |
| `android:useAppZygote="true"` + `ZygotePreload` | **暂缓** | 可降低冷启动/共享内存，但官方 `ServiceInfo.FLAG_USE_APP_ZYGOTE` javadoc 明确“代价是多一个 app zygote 进程占内存，是否划算要按典型负载实测”（`ServiceInfo.java:67-83`）。第一版不引入第二个进程，等设备测量后再议。 |
| SDK Sandbox（`android.app.sdksandbox`） | **拒** | API 34+，模型/生命周期/客户端 API 都不同，minSdk 23 不适用。 |
| 宿主侧 `adb shell am force-stop` / shell | **拒** | 不是应用 API；不能作为产品路径。 |

## 3. 契约 §4 三层终止 → API 映射

### 3.1 编号先统一（沿用批次 22 基线 §4.2 的发现）

契约正文**没有**“三层”字样；既有文档编号互相矛盾（研究文档把“进程级”编为第②层、宿主编第③层；批次 21 记录把进程级叫“第三层”；引擎 KDoc 自述“三层落两层”）。**本文件改用“按动作命名”的 L1/L2/L3，并在括号里注明旧编号，避免继续传播歧义。**

### 3.2 映射表

| 层 | 名字 | 目标 | 落到哪些 API | 在哪个进程 |
|---|---|---|---|---|
| **L1** | 协作中断（100 ms） | 让守规矩的 JS 循环自己退出；不杀进程 | QuickJS 原生 interrupt handler（现有引擎 `evaluationTimeoutMillis`）+ `withTimeout`/协程取消；`QuickJsInterruptedException` → `VisualExecutionRejection.PHASE_TIMEOUT`（`core/data/.../QuickJsVisualExecutionEngine.kt:72-80`，`VisualExecutionEngine.kt:122-123`） | 服务进程的 JS 执行线程 |
| **L2** | 服务硬终止（1500 ms；旧文献的“进程级/第三层”或“②层”） | 终止**任何**循环（含 native/自续 job） | ① 独立 watchdog 线程：`ScheduledThreadPoolExecutor` + `SystemClock.uptimeMillis()` + 令牌 CAS + **`Process.killProcess(Process.myPid())`**；② AIDL `oneway terminate(generation)`：Binder 线程上原子核对 generation，通过即 **同一 `killProcess`**；③ `onUnbind`/`onDestroy` 的兜底自杀（样例 nextvol `IsolatedExecutionService.kt:141-150`） | **服务进程自己**（隔离 UID） |
| **L3** | 宿主阶段期限（2500 ms；旧文献的“③层”） | 放弃、确认死亡、最多重建一次、熔断 | `generation` 废弃（宿主状态机）；`IVisualJsService.terminate(gen)`（oneway）；`Context.unbindService`；`IBinder.linkToDeath`/`DeathRecipient`；`ServiceConnection.onServiceDisconnected`/`onBindingDied`/`onNullBinding`；一次 `bindService` 重建；`programHash` 熔断；fallback。**不含任何 kill API** | 宿主主进程 |

关键句：**L2 是唯一的“杀”；L3 不是终止 API，而是放弃与确认协议。** 旧文档“进程级=第三层”的说法，按动作应归 L2；旧文档“宿主=第三层”按动作是 L3，且 L3 不杀任何进程。

### 3.3 与现有引擎的关系

现有 `QuickJsVisualExecutionEngine` 的“宿主硬期限层”在 1500 ms 到点后只做 `forceAbandon`/`close()`（`:164-192`、`:610-633`），而 issue #240 下 `close()` 也会卡在 `jsMutex`；它不是进程级终止。**T1 不修改这个引擎的终止语义**：服务层 watchdog 在同一个 1500 ms 到点直接 SIGKILL，引擎内部的 1500 ms + 500 ms 宽限永远等不到（进程已死），因此不需要给引擎再加第三条期限。引擎的 `Transaction.onHardDeadline()` 里“先核对仍是当前事务”的写法（`:604-613`）是服务层令牌的现成先例。

补充：如果引擎在 watchdog 到点前**自己返回了有界拒绝**（哪怕是 `HARD_DEADLINE_EXCEEDED`），正常路径先 CAS/cancel，watchdog 不杀；SIGKILL 只留给“引擎不返回/close 也卡死”的情况。这样普通慢事务不被误升级成进程死亡，只有 issue #240 类硬卡死才走 L2。

## 4. AIDL 形状（签名级配方）

### 4.1 模块与目录

- 按计划 §2：**AIDL、Service、宿主 client 全部落 `app` 模块**；`settings.gradle.kts` 是热区，禁止新增 Gradle 模块。
- AIDL 路径：`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/*.aidl`；`package` 必须逐字匹配目录。
- 生成 Java 接口包名：`com.tingyun.smartmistakebook.visualjs`；Kotlin 实现 `Stub` 没有障碍。

### 4.2 `IVisualJsCallback.aidl`

```aidl
package com.tingyun.smartmistakebook.visualjs;

import android.os.ParcelFileDescriptor;

oneway interface IVisualJsCallback {
    // status: 0=OK, 1=REJECTED, 2=PROCESS_DYING；diagnostics 有界（沿用现有诊断长度上限）
    void onResult(long generation, String requestId, int status,
                  in ParcelFileDescriptor payload, String diagnostics);

    // 尽力而为的死亡前原因；不保证送达（见 §4.5）
    void onFatal(long generation, String requestId, int code, String diagnostics);
}
```

### 4.3 `IVisualJsService.aidl`

```aidl
package com.tingyun.smartmistakebook.visualjs;

import android.os.ParcelFileDescriptor;
import com.tingyun.smartmistakebook.visualjs.IVisualJsCallback;

interface IVisualJsService {
    int protocolVersion();                 // 不匹配即 fail-closed
    int workerUid();                       // 宿主据此校验回调来源；也用于断言 != 宿主 UID
    long currentGeneration();              // 诊断用；不作为控制依据

    // 两路同步控制：只做 UID/generation/令牌校验与投递，立刻返回
    // 返回：0=ACCEPTED, 1=BUSY, 2=STALE_GENERATION, 3=BAD_REQUEST, 4=SHUTTING_DOWN
    int initialize(long generation);
    int execute(long generation, String requestId,
                in ParcelFileDescriptor request,
                IVisualJsCallback callback);

    // 控制面 oneway：不排 JS 队列；Binder 线程上核对 generation 后自杀
    oneway void terminate(long generation);
}
```

设计要点：

- `execute` 是**两路**（two-way）调用，只是为了同步返回 ACCEPTED/BUSY/STALE；它**不得**在 Binder 线程跑 JS，只做校验 + `inFlight.compareAndSet` + 投递到单线程执行器。
- `terminate` 是 **`oneway`**：官方 AIDL 指南说 oneway“sends the transaction data and immediately returns… implementation eventually receives this as a regular call from the Binder thread pool”（[AIDL guide](https://developer.android.com/develop/background-work/services/aidl)）。它不会等 JS，因为 JS 在另一条线程；这正是契约要求的“不排在 JS 执行队列”。另一个原因：如果 `terminate` 是两路，服务在回复前 `killProcess` 会让宿主收到 `DeadObjectException`；oneway 让宿主只等 Binder death，语义更干净。
- `ParcelFileDescriptor` 只走 `in`；源码/状态/scene JSON 都走 PFD，不逐图元/大 JSON 走 Binder（Binder 事务缓冲 1 MB，且全进程共享：[TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException)）。读端要在流式读取时按字节计数，超过 `MAX_TRANSACTION_JSON_BYTES`（256 KiB）立即中止并回 `INPUT_OVER_BUDGET`；源码 64 KiB / state 16 KiB 仍由 `VisualExecutionBudget` 在引擎边界再核一遍，**不先无界读入再拒绝**。
- 回调接口整体 `oneway`：服务不等宿主 UI；同一 IBinder 上的 oneway 调用保持顺序（[IBinder.FLAG_ONEWAY](https://developer.android.com/reference/android/os/IBinder#FLAG_ONEWAY)：“dispatched in the other process one at a time, with the same order as the original calls”），但**不要混用 oneway 与 two-way 来保证顺序**（同一文档明确不保证）。
- 单实例：不使用 `bindIsolatedService` 的 `instanceName` 多实例；一个宿主同时只 bind 一个实例。

**generation 核对的现成形状**：TinaIDE 在 `invoke` 里把 generation 存在 `RuntimeEntry`，旧 generation 直接回 `STALE_GENERATION`，不落到新 runtime：

```kotlin
val entry = runtimes[request.pluginId] ?: throw IllegalStateException("Plugin runtime is not loaded")
if (entry.generation != request.generation) {
    return@runOperation JsonArray(emptyList()) to PluginRuntimeResponseStatus.STALE_GENERATION
}
```

（`wuxianggujun/TinaIDE` `core/plugin/.../PluginRuntimeService.kt:41-44`、`:73-75`；GPL-3.0，只借形状，不复制代码。）本方案把同一形状用在 `execute`/`terminate`：generation 不匹配的请求/终止都 no-op 或 `STALE_GENERATION`，绝不落到新实例。

### 4.4 Service 实现骨架（Kotlin，app 模块）

```kotlin
class VisualJsService : Service() {
    private val jsExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "visual-js-executor").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val watchdog = ScheduledThreadPoolExecutor(1) { r ->
        Thread(r, "visual-js-watchdog").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }

    private val activeGeneration = AtomicLong(0L)
    private val initialized = AtomicBoolean(false)
    private val abandoned = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val token = AtomicReference<ExecutionToken?>(null)
    private val scope = CoroutineScope(SupervisorJob() + jsExecutor)
    private val engine by lazy { /* 构造 core:data QuickJsVisualExecutionEngine + 服务端 measurer */ }
    private var hostDeathRecipient: IBinder.DeathRecipient? = null

    override fun onBind(intent: Intent?): IBinder = stub

    private fun requireHost() {
        // onBind 的 calling UID 不可靠；必须在每个 Binder transaction 里查
        if (Binder.getCallingUid() != applicationInfo.uid) {
            throw SecurityException("unauthorized_caller")
        }
    }

    private val stub = object : IVisualJsService.Stub() {
        override fun protocolVersion(): Int = PROTOCOL_VERSION
        override fun workerUid(): Int { requireHost(); return Process.myUid() }
        override fun currentGeneration(): Long { requireHost(); return activeGeneration.get() }

        override fun initialize(generation: Long): Int {
            requireHost()
            if (!initialized.compareAndSet(false, true)) {
                return if (activeGeneration.get() == generation) STATUS_OK
                else STATUS_STALE_GENERATION
            }
            activeGeneration.set(generation)
            return STATUS_OK
        }

        override fun execute(
            generation: Long, requestId: String,
            request: ParcelFileDescriptor, callback: IVisualJsCallback,
        ): Int {
            requireHost()
            if (abandoned.get()) return STATUS_SHUTTING_DOWN
            if (generation != activeGeneration.get()) return STATUS_STALE_GENERATION
            if (!inFlight.compareAndSet(false, true)) {
                runCatching { request.close() }
                return STATUS_BUSY
            }

            val t = ExecutionToken(generation, requestId, uptimeDeadline())
            token.set(t)
            t.watchdogFuture = armWatchdog(t)
            watchHostDeath(callback)

            scope.launch {
                try {
                    val result = engine.execute(readBoundedRequest(request))
                    if (t.active.compareAndSet(true, false)) t.watchdogFuture?.cancel(false)
                    inFlight.set(false)               // 先置空闲再投递，避免紧随的 execute 误判 BUSY
                    deliver(callback, t, result)
                } catch (t: Throwable) {
                    if (t.active.compareAndSet(true, false)) t.watchdogFuture?.cancel(false)
                    inFlight.set(false)
                    safeFatal(callback, t, CODE_INTERNAL)
                } finally {
                    token.compareAndSet(t, null)
                    runCatching { request.close() }
                }
            }
            return STATUS_ACCEPTED
        }

        override fun terminate(generation: Long) {
            requireHost()
            if (generation != activeGeneration.get()) return   // 过期 terminate 必须 no-op
            if (!abandoned.compareAndSet(false, true)) return
            Process.killProcess(Process.myPid())
        }
    }

    private fun armWatchdog(t: ExecutionToken): ScheduledFuture<*> = watchdog.schedule({
        if (!t.active.compareAndSet(true, false)) return@schedule
        if (t.generation != activeGeneration.get()) return@schedule
        Log.w(TAG, "hard deadline gen=${t.generation} req=${t.requestId}")
        Process.killProcess(Process.myPid())
    }, VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS, TimeUnit.MILLISECONDS)

    override fun onUnbind(intent: Intent?): Boolean {
        Process.killProcess(Process.myPid())   // unbind 不是同步终止；这里只是兜底
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
```

`ExecutionToken` 与 `deliver` 的形状：

```kotlin
private class ExecutionToken(
    val generation: Long,
    val requestId: String,
    val deadlineUptimeMs: Long,
) {
    val active = AtomicBoolean(true)
    @Volatile var watchdogFuture: ScheduledFuture<*>? = null
}
```

- 正常完成/取消：`active.compareAndSet(true,false)` 成功后 `future?.cancel(false)`；watchdog 若已先 CAS 成功则进程已被杀，不再有“下一任务”。
- `uptimeDeadline()` 用 `SystemClock.uptimeMillis() + 1500`；**不要用 `System.currentTimeMillis()`**（墙钟可跳）。AOSP system_server 的 `Watchdog` 明确解释为什么用 uptime：

  > “NOTE: We use uptimeMillis() here because we do not want to increment the time we wait while asleep. If the device is asleep then the thing that we are waiting to timeout on is asleep as well and won't have a chance to run, causing a false positive on when to kill things.”（`Watchdog.java:858-862`）

- `deliver`：`ParcelFileDescriptor.createReliablePipe()`（API 19），`callback.onResult(...)` 传 read end，然后在守护线程写 write end；**传完后关闭本地 read end**（对端已有 dup），写完关闭 write end；`RemoteException`/`IOException` 一律 `Process.killProcess(Process.myPid())`（nextvol 同型：`IsolatedExecutionService.kt:134-138`）。`createReliablePipe` 的意义：写端可以 `closeWithError(msg)`，读端 `checkError()` 能区分“正常 EOF”与“对端崩溃/错误”（[ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor#createReliablePipe())）。
  > **2026-10-26 设备实测修正（批次 22 复核 #2）**：上面的“传完后关闭本地 read end”在**跨进程接收方**看来不是无害的。宿主在 `execute()` 返回后关闭自己那份 read end，服务端用 `AutoCloseInputStream` 读到 EOF 时 `checkError()` 会抛 `IOException: Remote side is dead`——**即使 64 KiB 数据已完整缓冲**（设备探针 A/E）。修复：**接收端先把收到的 PFD `dup()`（dup 出的数据 fd 不带 comm 通道）再读**；服务端请求方向在 Binder 线程 dup，宿主结果方向在 `readResultPayload` dup。设备 T1 修复前 4/4 红即此形状，见 `docs/known-defects.md` KD-66。
- `watchHostDeath(callback)`：

  ```kotlin
  private var hostBinder: IBinder? = null
  private var hostDeathRecipient: IBinder.DeathRecipient? = null

  private fun watchHostDeath(cb: IVisualJsCallback) {
      // 先解绑旧 callback 的 recipient（作用在旧 binder 上），再 link 新 binder
      hostDeathRecipient?.let { r -> runCatching { hostBinder?.unlinkToDeath(r, 0) } }
      val b = cb.asBinder()
      val r = IBinder.DeathRecipient { Process.killProcess(Process.myPid()) }
      try {
          b.linkToDeath(r, 0)
          hostBinder = b
          hostDeathRecipient = r
      } catch (_: RemoteException) {
          Process.killProcess(Process.myPid())   // 宿主已死：不留孤儿进程
      }
  }
  ```

  更简单的做法是协议固定 callback：`initialize(generation, callback)` 时 link 一次，`execute` 不再传 callback。二者都可，关键是**每个 link 都有对应的 unlink**。

  注意：`linkToDeath` 只对远程 binder 有效（[IBinder.linkToDeath](https://developer.android.com/reference/android/os/IBinder#linkToDeath(android.os.IBinder.DeathRecipient,int))：“You will only receive death notifications for remote binders”）；同进程单测里没有这个信号，必须用真进程仪器化测试（nextvol 的 README 明确“A Robolectric service is not a substitute”）。

### 4.5 宿主 client 骨架（实现 `core:domain.VisualExecutionEngine`）

```kotlin
class AndroidVisualExecutionEngine(
    private val context: Context,
    private val programFuse: ProgramFuse,       // 宿主侧 programHash 熔断
) : VisualExecutionEngine {

    private val generation = SecureRandom().nextLong()   // 每次 bind 一个新 nonce；跨宿主重启不复用
    private var service: IVisualJsService? = null
    private var workerUid = -1
    private val state = MutableStateFlow<LinkState>(LinkState.Idle)
    private val death = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 每次重建必须新建 connection + death + deathRecipient；旧的一律 unlink/unbind。
    // 下面只画一个实例的形状。

    private val deathRecipient = IBinder.DeathRecipient { onDeath() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            // 已放弃/已死亡时收到新连接：立刻终止并解绑，绝不采纳成“旧实例”
            if (state.value is LinkState.Abandoned) {
                runCatching { IVisualJsService.Stub.asInterface(binder).terminate(generation) }
                runCatching { context.unbindService(this) }
                return
            }
            try {
                binder.linkToDeath(deathRecipient, 0)
                val s = IVisualJsService.Stub.asInterface(binder)
                if (s.protocolVersion() != PROTOCOL_VERSION) { failAndUnbind(); return }
                if (s.initialize(generation) != STATUS_OK) { failAndUnbind(); return }
                val uid = s.workerUid()
                if (uid == Process.myUid()) { failAndUnbind(); return }  // 不是隔离进程：fail-closed
                workerUid = uid
                service = s
                state.value = LinkState.Connected
            } catch (_: RemoteException) {
                onDeath()
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { onDeath() }
        override fun onBindingDied(name: ComponentName) { onDeath() }
        override fun onNullBinding(name: ComponentName) { onDeath() }
    }

    override suspend fun execute(request: VisualExecutionRequest): VisualExecutionResult {
        val digest = figureSha256Hex(request.sourceJs.toByteArray(StandardCharsets.UTF_8))
        if (programFuse.isFused(digest) && !request.explicitUserRetry) {
            return rejected(PROGRAM_FUSED, "program digest ${digest.take(12)} is fused")
        }
        if (!ensureConnected()) return rejected(ENGINE_CLOSED, "visual_js service unavailable")

        val requestId = UUID.randomUUID().toString()
        val pending = CompletableDeferred<VisualExecutionResult>()

        return try {
            withTimeout(VisualExecutionBudget.HOST_PHASE_DEADLINE_MILLIS) {
                val pipe = ParcelFileDescriptor.createReliablePipe()
                writeRequest(pipe[1], request)          // Dispatchers.IO；有界、可取消；失败即关 read end
                // 注意：writeRequest 要在 IO 线程写；服务死亡/超时后关闭 pipe，写端报错即结束，
                // 不要把 256 KiB 的写放在主线程或 Binder 回调线程。
                val cb = object : IVisualJsCallback.Stub() {
                    override fun onResult(gen: Long, id: String, status: Int,
                                          payload: ParcelFileDescriptor?, diagnostics: String?) {
                        if (gen != generation || id != requestId) {
                            runCatching { payload?.close() }        // 旧 generation/旧帧：丢弃并关 fd
                            return
                        }
                        // Binder 回调线程只入队；PFD 读取放 IO，避免占住 Binder 线程
                        scope.launch { pending.complete(readAndMap(status, payload, diagnostics)) }
                    }
                    override fun onFatal(gen: Long, id: String, code: Int, diagnostics: String?) {
                        if (gen != generation || id != requestId) return
                        pending.complete(rejected(HARD_DEADLINE_EXCEEDED, diagnostics.orEmpty()))
                    }
                }
                val accepted = requireNotNull(service).execute(generation, requestId, pipe[0], cb)
                when (accepted) {
                    STATUS_ACCEPTED -> Unit
                    STATUS_BUSY -> return rejected(BUSY, "visual_js busy")
                    STATUS_STALE_GENERATION -> return rejected(ENGINE_CLOSED, "stale generation")
                    else -> return rejected(INPUT_OVER_BUDGET, "bad request")
                }
                pending.await()
            }
        } catch (_: TimeoutCancellationException) {
            // 竞态：结果与超时同时到；已完成则不误熔断
            if (pending.isCompleted) return pending.await()
            abandon()                                     // 不阻塞；宿主期限到
            programFuse.fuse(digest)                      // 超时即熔断；不重跑
            rejected(HARD_DEADLINE_EXCEEDED, "host phase deadline exceeded")
        } catch (cancel: CancellationException) {
            abandon()                                     // 调用方取消：停掉服务里的工作
            throw cancel                                  // 与现有引擎一致，不折成拒绝
        }
    }

    private fun onDeath() {
        // 幂等：binderDied / onServiceDisconnected / onBindingDied 可能都来
        service = null
        death.complete(Unit)
        if (state.value is LinkState.Abandoned) return
        // 非预期死亡：当前 programHash 熔断，最多重建一次
        scope.launch {
            val rebuilt = rebuildOnce()
            if (!rebuilt) programFuse.fuse(currentDigest())
        }
    }

    private fun abandon() {
        state.value = LinkState.Abandoned
        runCatching { service?.terminate(generation) }
        runCatching { context.unbindService(connection) }
        // 等待死亡确认；超时则 fallback，不再 bind
        scope.launch {
            val confirmed = withTimeoutOrNull(1000) { death.await() } != null
            if (!confirmed) state.value = LinkState.DeathUnconfirmed
        }
    }
}
```

要点：

- **`ensureConnected()` 的坑**：`bindService` 返回 false、`onNullBinding`、`onBindingDied`、连接超时都必须 fail-closed 成 `ENGINE_CLOSED`；不要在 `onServiceConnected` 之前把 `service` 置为可用；连接等待放 IO/后台，不阻塞主线程。
- **宿主期限先于/晚于服务期限的分工**：正常情况服务 1500 ms 先自杀；宿主 2500 ms 是外层安全网（进程被系统冻结、watchdog 也没跑时）。宿主到点**不能杀**，只能 `abandon`。
- **不要 `Process.killProcess(pid)` / `killBackgroundProcesses`**：见 §2.3。
- **不要并发 bind**：`ActiveServices` 对 isolated 服务的每次 `startProcessLocked` 都可能开新进程（`ActiveServices.java:5877-5883`）；宿主状态机只允许一个 in-flight bind。
- **死亡确认后最多重建一次**：`rebuildOnce()` 用计数器/状态；若重建后再死，fallback，不再 bind。重建只恢复运行环境，**不重跑刚才超时/崩溃的 request**（契约 §4:70）。
- **旧 generation 的回调一律丢弃**：`gen != generation || id != requestId` 直接 return；这消灭“另一张图的结果被当成旧帧”的失败。
- **`onServiceConnected` 在 ABANDONED 状态下必须“杀新实例 + unbind”**：这是 nextvol 的现成写法（`IsolatedWorkerConnection.kt:40-42`），因为 unbind 不是同步终止、AMS 可能在你 unbind 之前就按残留 binding 重启服务（`ActiveServices.killServicesLocked` 里 `scheduleServiceRestartLocked`，`ActiveServices.java:7308-7333`）。

### 4.6 oneway / 回调 / 线程纪律（坑位）

| 坑 | 事实 | 配方 |
|---|---|---|
| Binder 线程不是主线程 | AIDL 官方：“Calls from a remote process are dispatched from a thread pool the platform maintains inside your own process… calls can happen at the same time… implementation must be completely thread-safe” | Service 的 AIDL 方法只做校验/CAS/投递；JS 在专用单线程；callback stub 在宿主只入队，不碰 UI |
| 回调线程 | AIDL guide 示例：“IPC calls are dispatched through a thread pool running in each process, so the code executing here is NOT running in our main thread” | 宿主 callback 用 `trySend`/`CompletableDeferred`；UI 更新 post 到主线程 |
| oneway 不是可靠/背压通道 | oneway 立即返回；同 IBinder 顺序有保证，但混用 two-way 时顺序不保证（`IBinder.FLAG_ONEWAY`） | 只把 oneway 用于控制/结果通知；大 payload 走 PFD；不要高频逐帧回调 |
| 1 MB Binder 缓冲 | “currently 1MB, shared by all transactions in progress for the process”（TransactionTooLargeException 文档） | 源码/JSON 全部 PFD；控制消息只带 primitive/小 String |
| `onBind` 的 calling UID 不可靠 | nextvol README：“checks the calling UID inside Binder transactions… never against `onBind`'s calling UID or an Intent extra” | 每个 AIDL 方法 `Binder.getCallingUid()` |
| `ServiceConnection` 回调在主线程 | 官方：“Like many callbacks from the system, the methods on this class are called from the main thread”（`ServiceConnection` 文档） | `onServiceConnected` 只做握手/CAS；不要在里读 PFD/解析大 JSON；重活丢 IO |
| 回调来源校验 | 只有隔离进程能调宿主 callback | 宿主记录 `workerUid`，callback stub 里 `Binder.getCallingUid() == workerUid`，否则丢弃（nextvol `AndroidIsolatedExecutor.kt:239-247`） |
| `onBindingDied` / `onNullBinding` | 官方：`onBindingDied` “interface will never receive another connection… unbind and rebind”；`onNullBinding` 永远不可用，仍需 `unbindService` | 三者都进同一个幂等 `onDeath()`；`onNullBinding` 直接 fallback |
| `onServiceDisconnected` 不解除 binding | 官方：“This does not remove the ServiceConnection itself… you will receive onServiceConnected when the Service is next running” | 不重建时必须显式 `unbindService`，否则 AMS 可能重启服务（AndroidX PDF 的注释也观察到了：“If we do not call unbind, then it will try to restart the service immediately”，`PdfServiceConnectionImpl.kt:59-67`） |
| `linkToDeath` 泄漏/竞态 | 官方：`linkToDeath` 在 binder 已死时抛 `RemoteException`；`unlinkToDeath` 未注册且 binder 活时抛 `NoSuchElementException`；`isBinderAlive` 返回 true 仍可能在返回途中死亡 | `onServiceConnected` 里 link 并 catch；`close` 里 `unlinkToDeathSafely`；死亡状态用 `CompletableDeferred`/`AtomicBoolean` 幂等 |
| `unlinkToDeath` 清理 | AndroidX `RemoteExecute.kt:96-106` 的成熟写法：`finally { deathRecipient?.let { binder.unlinkToDeathSafely(it) } }`，并 catch `NoSuchElementException` | 照抄形状（Apache-2.0） |
| 多回调 vs 单回调 | `RemoteCallbackList` 在注册接口进程死亡时自动移除，并可 override `onCallbackDied`（[RemoteCallbackList](https://developer.android.com/reference/android/os/RemoteCallbackList)） | 当前单宿主、单在飞：单 callback + 手写 `linkToDeath` 更小；未来若允许多宿主/多屏回调，改用 `RemoteCallbackList`，不要自己维护多个 recipient |
| 同进程单测不算数 | `Stub.asInterface` 在同进程返回本地对象；Binder/隔离 UID 都没被验证 | 仪器化测试必须起真实 `:visual_js` 进程，断言 `workerUid != Process.myUid()`、进程名、死亡回调（nextvol 18 例 API 24/35 的做法） |

## 5. watchdog 线程形状与主/服务协调

### 5.1 形状

- **一条独立 watchdog 线程**（`ScheduledThreadPoolExecutor(1)`，daemon），与 JS 执行线程分离；watchdog 只读原子量 + 一次 `Log.w` + `killProcess`，**不拿引擎锁、不做 Binder/网络 IPC、不写文件**。现有引擎的 `forceCloseLiveEngine()` 已经把 `close()` 放专用守护线程、不占 watchdog（`QuickJsVisualExecutionEngine.kt:616-631`）——服务层 watchdog 更极端：直接 SIGKILL。
- **每个请求一个令牌** `ExecutionToken(generation, requestId, deadline)`；`watchdog.schedule` 返回的 `future` 存回令牌，正常完成/取消先 CAS `active` 再 `cancel`。
- **迟到定时器不杀下一任务**：watchdog 先 `active.compareAndSet(true,false)`；失败即返回；成功后再核对 `generation == activeGeneration`。正常完成路径同样 CAS，二者只有一个赢家。
- **时钟**：`SystemClock.uptimeMillis()`（不含深睡）；AOSP Watchdog 的注释解释了原因（`Watchdog.java:858-862`）。不要用 `System.currentTimeMillis()`。
- **期限起点**：在第一个 JS 阶段真正开始前 arm；不要在请求排队/等 Binder 线程时就开始计时（否则 Binder 线程池忙也会“超时”）。契约 §4:68 原文是“每个 JS 阶段开始前启动”；本配方把它落实为“整个请求的 JS 工作开始时 arm，最后一次 JS 工作结束后 disarm”，与 1500 ms 服务硬期限/2500 ms 宿主期限的数值关系一致。
- **不走 `close()`**：issue #240 下 `evaluate()`/`close()` 都可能永不返回；任何“先 close 再杀”的路径都给不出上界。SIGKILL 是唯一上界。

### 5.2 谁先死

正常超时（服务 1500 ms 先到）：

```
宿主                        服务进程（isolated UID）
 |-- execute(gen, req, cb) -->|  Binder 线程：校验/令牌/投递，立即返回
 |                            |  JS 线程跑；watchdog 线程睡到 1500ms
 |                            |  watchdog CAS 成功 → killProcess(myPid)
 |<-- binderDied / onServiceDisconnected
 |  标记死亡；programHash 熔断；不重跑 req；最多重建一次
```

宿主主动放弃（用户取消/外层 2500 ms 到）：

```
宿主: state=ABANDONED → service.terminate(gen) [oneway] → unbindService
服务: Binder 线程 terminate(gen)：gen 匹配 → killProcess
宿主: 等 binderDied/onServiceDisconnected（1s 上限）
      确认 → 可重建一次；未确认 → fallback，不再 bind（防并发放大进程数）
```

**谁先死：服务先死，宿主观察。** 宿主绝不 `killProcess` 自己，也不声称 unbind 等于同步终止（契约 §4:68/70）。

### 5.3 避免误杀

1. **令牌 + generation 双条件**：正常完成先 CAS；watchdog 只有 CAS 成功且 generation 仍是当前才杀。
2. **单在飞**：`inFlight.compareAndSet(false,true)`；`execute` 的 BUSY 路径不开新任务。
3. **过期 terminate no-op**：`terminate(gen)` 第一行 `if (gen != activeGeneration) return`。
4. **不要用墙钟**：墙钟跳变会提前/延后杀；用 uptime。
5. **不要在 `onBind`/`onCreate` 里 arm watchdog**：那时还没有 JS 阶段；初始化失败走 `onFatal`/拒绝，不占用 1500 ms 预算。
6. **调试器豁免**：AOSP Watchdog 在 debugger attached 时不杀（`Watchdog.java:940-950`）。本方案**不建议**加这个豁免：T1 的设备用例需要真的验证 SIGKILL；若调试确实需要，用 `BuildConfig.DEBUG` + 显式测试开关，不要在产品路径静默放宽。

### 5.4 最多重建一次 & 防“第二个进程”

- `ActiveServices` 对 isolated 服务的每次 `startProcessLocked()` 都会得到**新进程**；它用 `r.isolationHostProc` 缓存当前实例，但注释明确“if we are currently waiting for a previous process to come up”会再起一个（`ActiveServices.java:5877-5883`）。所以宿主**任何时候只允许一个 bind 在飞**。
- 进程死亡后，`killServicesLocked` 会对仍有 binding/started 需求的 Service 调 `scheduleServiceRestartLocked`（`ActiveServices.java:7308-7333`）；也就是说**不 unbind 的话，AMS 可能在你决定重建之前就把服务又拉起来**。AndroidX PDF 的注释是同一现象的独立佐证（`PdfServiceConnectionImpl.kt:59-67`）。
- 配方：
  1. 宿主先置 `ABANDONED`（拒绝新请求）；
  2. `terminate(gen)`（oneway）；
  3. **立刻 `unbindService`**（移除重启理由）；
  4. 等 `binderDied`/`onServiceDisconnected`（≤1 s）；
  5. 确认死亡后，`rebuildCount < 1` 才 `bindService` 一次，新 generation；
  6. 任何在 `ABANDONED` 状态到达的 `onServiceConnected`：立即 `terminate(新 gen?)` + `unbind`，不采纳；
  7. 死亡未确认：fallback，**不 bind**；
  8. 重建后的实例再死：不再重建；熔断 programHash。
- 重建**不自动重跑**刚才超时/崩溃的源码；programHash 熔断到用户明确本地重试或源码改变（契约 §4:70）。重试仍受同一 100/1500/2500 预算。

### 5.5 失败矩阵（服务层视角）

| 失败 | 检测 | 动作 | 熔断？ |
|---|---|---|---|
| 普通死循环（可协作） | engine `PHASE_TIMEOUT` | 回 `REJECTED`，不杀进程 | 否 |
| 自续 job / 同步 JNI 卡死（#240） | 服务 watchdog 1500 ms | `killProcess(myPid)`；宿主 death | 是 |
| `terminate(gen)` 命中 | Binder 线程 | `killProcess(myPid)` | 用户取消/主动放弃：否；外层 2500 ms 到点：是（按超时） |
| native crash / SIGSEGV | 进程死亡 | 宿主 death → fallback + 最多重建一次 | 是 |
| QuickJS OOM | engine `MEMORY_LIMIT`（若捕获） | 回 `REJECTED`，不杀 | 否 |
| engine 在服务已取得 `inFlight` 后仍回 `BUSY` | 说明引擎实例内部 `inFlight` 卡死（#240 残留） | 视为引擎损坏：丢弃实例并 `killProcess(myPid)` 重建；**不要**把这种 BUSY 当正常背压回给宿主 | 是 |
| 进程被 LMK/系统杀 | 宿主 death | fallback；保守按“崩溃”处理 | 是（保守） |
| 宿主死 | 服务 callback binder death | 服务 `killProcess(myPid)` | N/A |
| 设备深睡 | uptime 不含深睡 | 不误杀；醒来继续 | 否 |
| 服务被冻结（cgroup freezer） | watchdog 也不跑 | 宿主 2500 ms 到点 → abandon/unbind；死亡未确认 → fallback，不重建 | 由宿主决定 |

## 6. Gradle / Manifest / 构建坑

### 6.1 `buildFeatures.aidl`

- 本项目 AGP = **9.3.0**（`gradle/libs.versions.toml:2`），app 模块 `buildFeatures` 目前只有 `compose`/`buildConfig`（`app/build.gradle.kts:82-85`）；`gradle.properties:6` 只有 `android.defaults.buildfeatures.resvalues=false`，没有 AIDL 开关。**不显式 `aidl = true`，`app/src/main/aidl` 不会编译。**
- AGP 8.0 起 `android.defaults.buildfeatures.aidl` 默认 `false`（[AGP 8.0 release notes](https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes)：“AGP 8.0 doesn't enable AIDL support by default. You need to specify this option using the DSL…”）。AGP 9.3 的 `BuildFeatures.aidl` 仍存在且默认 `false`（[BuildFeatures 9.3](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/BuildFeatures)：“Default value is false”）。
- 配方：

  ```kotlin
  android {
      buildFeatures {
          compose = true
          buildConfig = true
          aidl = true       // 新增；否则 app/src/main/aidl 不编译
      }
  }
  ```

- AIDL 文件放 `app/src/main/aidl/<package path>/`；`package` 与目录逐字一致；`ParcelFileDescriptor` 需在 AIDL 里 `import android.os.ParcelFileDescriptor;`。
- `oneway` 方法只能 `void`，参数只能 `in`；`oneway interface` 的所有方法都 oneway。
- 不需要 `kotlin-parcelize`（本方案没有自定义 Parcelable；只用 `ParcelFileDescriptor` 和 primitive/String）。
- 生成的 `Stub` 是 Java；Kotlin `object : IVisualJsService.Stub()` 正常。
- 变体：AIDL 在 `src/main` 对 `strictOffline`/`localFirst` 都生效；若以后要做 flavor 差异，放 `src/<flavor>/aidl`。
- **R8**：`benchmark`/`internal`/`release` 都 `isMinifyEnabled = true`（`app/build.gradle.kts:63/69/78`），且 benchmark/internal 目前没有 `proguardFiles`（KD-69）。Service 由 manifest 引用会被 keep；AIDL `Stub`/生成的 `asInterface` 由代码引用；先跑 release/benchmark 验证，不要凭“AIDL 需要 keep”就加盲规则（每条 keep 必须指认它消灭的 NoClassDefFoundError）。

### 6.2 Manifest

```xml
<service
    android:name=".visualjs.VisualJsService"
    android:exported="false"
    android:isolatedProcess="true"
    android:process=":visual_js" />
```

- **显式 `exported="false"`**：无 intent-filter 时默认已是 false，但 Android 12+ 的组件导出规则让显式声明更可审计；不要加 intent-filter（否则默认变 true）。
- `android:process=":visual_js"` 给进程一个可读名字；`isolatedProcess` 负责 UID/权限隔离。两者同时写不冲突。
- 不要 `android:permission`（同应用绑定，不需要）；不要 `android:foregroundServiceType`（见 §6.3）。
- 不建议第一版加 `android:useAppZygote="true"`；理由见 §2.4。
- `android:exported=false` 的 `tools:ignore="MissingServiceExportedEqualsTrue"` 只有 lint 需要时才加；AndroidX PDF 的 manifest 用了这个 ignore（`androidx/pdf/pdf-document-service/src/main/AndroidManifest.xml:20-23`）。

### 6.3 Foreground / background 限制

- **不要在隔离进程里 `startForeground`**：
  - `FOREGROUND_SERVICE` 是 normal 权限（`core/res/AndroidManifest.xml:7700-7703`），但 FGS **type** 策略用 `r.app.uid`（隔离 UID）检查（`ActiveServices.java:2851-2855`），隔离 UID 没有包权限；`Service.startForeground` 文档明确 target 34+ 缺 type 权限会抛 `SecurityException`（`Service.java:761-764`）。
  - **[推断]** 隔离进程也不继承 `POST_NOTIFICATIONS` 等运行时权限；通知/渠道/AppOps 都不可靠。FGS type 的 `SecurityException` 是 [AOSP] 直接证据（上一行）；通知面是推论，未设备验证。
  - 结论：**本方案只用绑定服务，不做 FGS**；渲染只在宿主可见时进行（契约 §4 自动播放后台暂停）。若未来必须后台保活，FGS 应放在宿主主进程，隔离进程仍只是子计算进程——那是另一项产品/权限决策，不在 T1。
- **后台绑定**：官方《Background execution limits》原文：“These rules don't affect bound services in any way. If your app defines a bound service, other components can bind to that service whether or not your app is in the foreground.”（[Background execution limits](https://developer.android.com/about/versions/oreo/background)）。所以后台不会阻止绑定；但绑定进程的 oom adj 随宿主下降，系统仍可因内存压力杀它。宿主把死亡当正常失败处理（fallback/重建一次），不要假设“绑定 = 保活”。
- `ActivityManager.killBackgroundProcesses` 不可用（§2.3），不要再把它写进 fallback 路径。

### 6.4 测试形状

- **单元测试（JVM）**只能测协议常量、令牌状态机、generation 比较；`Stub.asInterface` 在同进程返回本地对象，**不验证 Binder/UID/死亡**。
- **仪器化测试**必须起真实 `:visual_js` 进程：
  - 绑定后断言 `service.workerUid() != Process.myUid()`；API 28+ 服务端可断言 `Process.isIsolated()`（[Process.isIsolated](https://developer.android.com/reference/android/os/Process#isIsolated()) API 28）；API 23–27 只能用 `myUid != applicationInfo.uid` + `ps -Z` 的 `isolated_app` 观察（`Process.isIsolatedUid` 是 API 34）。
  - 无限循环 → 服务在 1500 ms 左右死亡；宿主收到 death；不重跑；熔断。
  - `terminate(staleGen)` no-op（服务仍活）；`terminate(currentGen)` 立即死亡。
  - 两次快速 `execute`：第二次 BUSY；进程数仍为 1（`ps -A | grep visual_js`）。
  - 宿主主动杀自己的进程**不能**在仪器化测试里直接做（测试进程一起死）；改成：(a) 测试专用进程里持有 callback，杀该进程后观察服务自杀；或 (b) 用 `callback.asBinder()` 的死亡路径做单元级替身。验证服务无孤儿。
  - `onNullBinding`/`onBindingDied` 路径：用测试专用 Service 子类返回 null / 主动 unbind，验证宿主 fallback。
- 参考 nextvol 的 18 例：API 24/35 真进程，覆盖远程 Binder、独立 UID、外来 isolated UID 转发 Binder 拒绝、超时终止、后续新 source 成功（`sources/execution/README.md:51-61`）。这是“同进程单测不算隔离验证”的现成先例。

## 7. Application guard 与启动路径审计

### 7.1 必需的 guard

`SmartMistakeBookApplication.onCreate()`（`app/src/main/kotlin/.../SmartMistakeBookApplication.kt:285`）必须在**任何有副作用的初始化之前**短路（放在 `super.onCreate()` 之后即可）：

```kotlin
if (Process.myUid() != applicationInfo.uid) return
```

- 隔离进程的 `Process.myUid()` 是隔离 UID（99000+），`applicationInfo.uid` 是应用 UID；两者不等 → 跳过全部宿主初始化。依据：`ProcessList.newProcessRecordLocked` 把 `info`（应用 `ApplicationInfo`，`info.uid` 仍是应用 UID）与 `uid`（隔离 UID）分开持有（`ProcessList.java:3360-3400`）；nextvol 的 `enforceHost` 也用 `applicationInfo.uid` 做宿主校验并在 API 24/35 真机跑通（`IsolatedExecutionService.kt:53-58`）。**这是 [AOSP]+[样例]，不是本仓库设备实测。**
- 只有 AMS 内部的 `isolatedEntryPoint` 路径“just call an entry point instead of being bound to an application”（`ActivityManagerService.java:4726-4732`）；`bindService` 走的是 `bindApplication`，Application 必建，所以 guard 不能省。
- 为什么不用 `Process.isIsolated()`：API 28 才有，minSdk 23；`Process.myUid() != applicationInfo.uid` 在 API 23 可用，且是契约 §4:62 明确点名的公开 API 写法。
- 为什么不用 `applicationInfo.processName`/`getProcessName()`：进程名可被 native 改；UID 是内核事实。
- 同 UID 的普通 `:process` 不会被这个 guard 识别（UID 相等）——本方案不用同 UID 形态，故不是缺口；如果未来加同 UID 进程，需要另加进程名判断。

### 7.2 审计面（不能只在 onCreate 加一行就宣称隔离）

契约 §4:62 要求审计“构造/字段与 companion 初始化、实际进入该进程的 ContentProvider/AndroidX Startup”。本仓库的实测材料：

- 合并清单（`app/build/intermediates/merged_manifest/strictOfflineDebug/processStrictOfflineDebugMainManifest/AndroidManifest.xml`；**构建产物，可能落后于最新源码，T1 落地后要重新合并再审计**）里有：
  - `androidx.startup.InitializationProvider`（`:311-329`），注册了 `WorkManagerInitializer`、`EmojiCompatInitializer`、`ProcessLifecycleInitializer`、`okhttp3.internal.platform.PlatformInitializer`、`ProfileInstallerInitializer`；
  - `com.google.mlkit.common.internal.MlKitInitProvider`（`:439-443`）；
  - 多个 FileProvider（`:146`、`:255`、`:264`、`:273`）。
  - **这些 provider 都没有 `android:process`**，默认落主进程；但契约要求审计，不能只靠“默认”。
- AOSP 路径：AMS 用 `generateApplicationProvidersLocked(app)` → `queryContentProviders(app.processName, app.uid, ...)`（`ContentProviderHelper.java:1246-1253`）生成 provider 列表，再随 `bindApplication` 传进进程（`ActivityManagerService.java:4615`）。隔离进程的 `processName` 是 `:visual_js`、`uid` 是隔离 UID；声明 provider 的应用 UID 不同，**通常不会命中**——但这是 [推断]，不是设备实测；审计仍要做。
- Application 字段初始化：`SmartMistakeBookApplication` 的 `applicationScope`/`dataStoreScope`/`startupState` 等属性在构造函数阶段执行；当前这些只是分配对象/创建 CoroutineScope，没有打开 Room/写文件。但要逐项确认：
  - `DiagnosticsBootstrap.install(this, ...)`（`:292`）会装崩溃 handler/写 filesDir → 必须在 guard 之后；
  - `CaptureCacheMaintenance.pruneExpiredFiles(this)`（`:311`）删文件 → guard 之后；
  - `BackupRestoreStartupRecovery.recoverOnStartup`/`StudyDatabaseFactory.open`/`DataStore`（`:317-319`）→ guard 之后；
  - 任何 `object` 单例/companion 的 `init {}` 若在类加载时打开数据库/WorkManager，也要移进 `onCreate` 的 guard 之后或 `by lazy`。
- 隔离进程内**预期可用**（[推断]，设备验证前不要当保证）：`System.loadLibrary`（QuickJS AAR 的 `.so` 从 APK 映射）、系统字体/`StaticLayout`（系统资源）、`ParcelFileDescriptor` 读写（宿主传进来的 fd）。**不允许**：`filesDir`/`database`/`SharedPreferences`/`Socket`/`OkHttp`/Room/WorkManager/Provider/Activity。
- 服务类本身不要引用任何宿主仓库/工厂；只依赖 `core:domain` 端口 + `core:data` 引擎 + Android 文本度量。`QuickJsVisualExecutionEngine` 当前落 core:data（批次 21 KDoc 已登记落位偏差），服务在 app 模块直接复用；**不要在 core:data 新增 AIDL**（契约 §5 红线）。

## 8. 实现配方清单（T1 可直接照做）

| 文件 | 动作 | 关键点 |
|---|---|---|
| `app/build.gradle.kts` | 在 `buildFeatures` 加 `aidl = true`（`:82-85`） | AGP 9.3 默认 false；不要新增 Gradle 模块 |
| `app/src/main/AndroidManifest.xml` | 加 `<service>`：`exported=false`、`isolatedProcess=true`、`process=":visual_js"` | 无 intent-filter；不设 FGS type；不加 permission |
| `app/src/main/aidl/.../IVisualJsCallback.aidl` | 新建 | `oneway interface`；`onResult` 带 PFD；`onFatal` 尽力而为 |
| `app/src/main/aidl/.../IVisualJsService.aidl` | 新建 | `initialize`/`execute` 两路；`terminate` oneway；`workerUid`/`currentGeneration` 诊断 |
| `app/src/main/kotlin/.../visualjs/VisualJsService.kt` | 新建 | 每个 AIDL 方法 `requireHost`；单线程 JS；独立 watchdog；令牌 CAS；`terminate` generation 核对；`onUnbind`/`onDestroy` 自杀；`deliver` 用 reliable pipe |
| `app/src/main/kotlin/.../visualjs/VisualJsProtocol.kt` | 新建（可选） | 状态码/常量/`ExecutionToken`；协议版本 |
| `app/src/main/kotlin/.../visualjs/VisualJsClient.kt` | 新建 | 实现 `core:domain.VisualExecutionEngine`；bind 状态机；generation nonce；linkToDeath；回调 UID 校验；2500 ms 宿主期限；abandon/重建一次/熔断 |
| `app/src/main/kotlin/.../SmartMistakeBookApplication.kt` | 在 `onCreate` 的 `super.onCreate()` 之后、任何副作用初始化之前加 guard（`:285`） | `if (Process.myUid() != applicationInfo.uid) return`；审计字段初始化/provider |
| `core/domain/.../VisualExecutionEngine.kt` | **不改**（或只加注释） | 端口已有 `BUSY`/`HARD_DEADLINE_EXCEEDED`/`PROGRAM_FUSED`/`ENGINE_CLOSED`，映射够用 |
| `core/data/.../QuickJsVisualExecutionEngine.kt` | **不改**（T6 共享热文件） | 服务层 watchdog 取代其“宿主硬期限层”的终止职责；不要再加第三条期限 |
| `settings.gradle.kts` | **不改** | 热区；AIDL/Service 都在 app |

### 8.1 结果映射（AIDL → `VisualExecutionRejection`）

| 服务状态 | 宿主映射 | 是否熔断 |
|---|---|---|
| `STATUS_ACCEPTED` + callback `OK` | `Rendered` | 否 |
| callback `REJECTED` + reason `PHASE_TIMEOUT` | `PHASE_TIMEOUT` | 否 |
| callback `REJECTED` + reason `MEMORY_LIMIT` | `MEMORY_LIMIT` | 否 |
| callback `REJECTED` + reason `INVALID_PROGRAM`/`SANDBOX_VIOLATION`/`INVALID_SCENE` | 同名 | 否 |
| callback `REJECTED` + reason `INPUT_OVER_BUDGET` | `INPUT_OVER_BUDGET` | 否 |
| `STATUS_BUSY` | `BUSY` | 否（宿主背压，保留原帧） |
| `STATUS_STALE_GENERATION` | `ENGINE_CLOSED`（诊断“stale generation”） | 否 |
| watchdog kill / native crash / LMK / 外层 2500 ms 触发的 terminate | `HARD_DEADLINE_EXCEEDED`（诊断“process died”） | **是** |
| 用户主动取消（协程 `CancellationException`） | 重新抛出 `CancellationException`（与现有引擎 `:193-195` 一致）；同时 `abandon()` 终止服务 | 否 |
| bind 失败 / `onNullBinding` / 重建预算耗尽 | `ENGINE_CLOSED`（诊断“visual_js unavailable”） | 由宿主决定；若因崩溃则熔断 |

### 8.2 落地顺序（T1 建议，测试先红后绿）

1. **红**：新增仪器化用例 `VisualJsIsolationInstrumentedTest`，绑定后断言 `service.workerUid() != Process.myUid()`、进程名 `:visual_js`、`terminate(currentGen)` 后收到 Binder death；此时无 Service/AIDL，必红。
2. **绿**：`buildFeatures.aidl = true` + manifest service + 两个 AIDL + 最小 Service（`initialize`/`workerUid`/`terminate`）+ 宿主 bind/DeathRecipient；上述用例转绿。
3. **红**：加“无限循环在 1500 ms 左右被 SIGKILL”用例（真实 `:visual_js` 进程）；当前只有协作中断，必红。
4. **绿**：watchdog + 令牌 CAS + `killProcess(myPid)`；用例转绿；同时加“正常完成不被误杀”用例。
5. **红**：`terminate(staleGen)` 不得杀当前实例；`ABANDONED` 后到达的 `onServiceConnected` 不得被采纳；当前无 generation 状态机，必红。
6. **绿**：generation + 宿主状态机 + unbind/重建一次/熔断；用例转绿。
7. **红**：隔离进程启动不得打开 Room/DataStore/WorkManager/写 filesDir；当前 Application 无 guard，必红（用服务进程日志/文件探测）。
8. **绿**：Application guard + 字段/provider 审计；用例转绿。
9. 最后接 PFD 请求/结果、真实 `QuickJsVisualExecutionEngine`、2500 ms 宿主期限；再做 ps16k/API 23/arm64 设备矩阵。

> 本文件不执行上述 Gradle/设备命令；T1 落地时按计划门禁串行跑。建议命令（写入 evidence，不在本线执行）：
> `./gradlew :app:assembleStrictOfflineDebug`、`./gradlew :app:connectedStrictOfflineDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest`；设备侧 `adb shell ps -A -Z | grep visual_js`、`adb shell dumpsys activity processes | grep visual_js`。

## 9. 未验证 / 剩余风险

以下全部标 `UNVERIFIED`（本线未跑 Gradle/设备）：

1. QuickJS AAR 的 `libquickjs.so` 在 `isolated_app` SELinux 域内可正常 `System.loadLibrary` 并从 APK 映射（预期可以；Chromium 在 isolated 进程加载 native 库是同类先例，但未在本项目设备上跑）。
2. 隔离进程内 `Typeface`/`StaticLayout`/系统字体可用；自定义字体若只在 `filesDir`，隔离进程读不到，需要 PFD 传字节或改用 `res/font`/系统字体。这会影响 `VisualContentMeasurer` 放服务端还是宿主端（本文件未裁决；T1 可先用系统字体最小实现，4E.2 再定）。
3. 合并清单里的 `androidx.startup`/ML Kit provider 在 `:visual_js` 是否真的一个都不装（AOSP `queryContentProviders(processName, uid)` 的 `isSameApp` 过滤是推断；需设备 `dumpsys activity providers` + 日志验证）。
4. `Process.myUid() != applicationInfo.uid` 在隔离进程中的实际值（nextvol README 声明其 `enforceHost` 用 `applicationInfo.uid` 且 API 24/35 测试通过；本仓库未直接验证）。
5. `Process.killProcess(myPid())` 在 isolated UID 下的实际信号权限/行为（预期自杀允许；未设备验证）。
6. 宿主对隔离进程调用 `Process.killProcess(pid)` 是抛 `SecurityException` 还是静默失败（未验证；设计上不用）。
7. `killBackgroundProcesses` 对绑定中的隔离服务是否真的完全不命中（AOSP 两道过滤推断；未设备验证；即使命中也不该用）。
8. AMS 在服务自杀后、宿主 unbind 前是否/多快重启服务（AOSP `killServicesLocked` + AndroidX 注释；OEM/API 差异未测）。配方用 `ABANDONED` 状态 + `onServiceConnected` 立即终止来兜底，但设备上要数进程数。
9. **已实证（2026-10-26 复核 #2）**：`ParcelFileDescriptor.createReliablePipe` 的 read end 带状态 comm 通道；发送方关闭自己那份 read end 后，接收方在 EOF 的 `checkError()` 会抛 `IOException: Remote side is dead`，即使数据已完整到达（设备探针：64 KiB 全缓冲仍抛）。接收方先 `dup()` 再读可消除（探针 B/C/D 绿）；`closeWithError` 的读端 `checkError()` 行为仍未单独测，但正常关闭路径已足够解释 T1 4/4 红。剩余：写端被宿主关闭时的 EPIPE 时序（未测）。
10. `oneway` 在高频/大并发下的异步缓冲行为（文档只给 1 MB 共享缓冲；未实测；配方已把大 payload 全部走 PFD）。
11. API 23–27 上 `ServiceConnection.onBindingDied`/`onNullBinding` 的默认方法（接口在旧平台没有这两个方法；Kotlin override 是否在所有 ART 版本安全）——预期安全（多出的方法不被调用），但需 API 23 设备/模拟器验证。
12. 设备深睡/Doze 下 uptime 与宿主 `withTimeout` 的时钟一致性（预期都排除深睡；未测）。
13. T1 的 watchdog 设备用例必须在真实 `:visual_js` 进程触发 SIGKILL；现有 `QuickJsVisualExecutionEngineInstrumentedTest` 的 issue #240 用例只观察“不返回”，不是进程终止验证。
14. 真实 Provider 无凭据：模型质量、真实 JS 生成质量全部 `UNVERIFIED`，与本进程层无关。

## 10. 来源

### 10.1 官方文档（经 `developer.android.google.cn` 官方镜像取文；canonical 链接如下）

1. `<service>` 元素（`android:process`、`android:isolatedProcess`、`android:exported`、`android:permission`）：https://developer.android.com/guide/topics/manifest/service-element
2. AIDL 指南（oneway、Binder 线程池、回调线程、`Process.killProcess` 示例注释）：https://developer.android.com/develop/background-work/services/aidl
3. `ServiceConnection`（`onServiceDisconnected`/`onBindingDied`/`onNullBinding`、主线程回调）：https://developer.android.com/reference/android/content/ServiceConnection
4. `Process`（`killProcess`、`myPid`、`myUid`、`isIsolated`、`isIsolatedUid`）：https://developer.android.com/reference/android/os/Process
5. `IBinder`（`linkToDeath`/`unlinkToDeath`/`isBinderAlive`/`FLAG_ONEWAY` 顺序）：https://developer.android.com/reference/android/os/IBinder
6. `TransactionTooLargeException`（1 MB 事务缓冲，全进程共享）：https://developer.android.com/reference/android/os/TransactionTooLargeException
7. `ParcelFileDescriptor`（`createPipe`/`createReliablePipe`/`closeWithError`/`checkError`）：https://developer.android.com/reference/android/os/ParcelFileDescriptor
8. `Context.bindIsolatedService`（多实例，API 29）：https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,int,java.lang.String,java.util.concurrent.Executor,android.content.ServiceConnection)
9. `ActivityManager.killBackgroundProcesses`（只杀 background；Android 14+ 只能杀自己的进程）：https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)
10. `RemoteCallbackList`（回调死亡自动移除、`onCallbackDied`）：https://developer.android.com/reference/android/os/RemoteCallbackList
11. AGP 8.0 release notes（`android.defaults.buildfeatures.aidl` 默认 false）：https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes
12. AGP 9.3 `BuildFeatures.aidl`（默认 false）：https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/BuildFeatures

### 10.2 AOSP 源码（`aosp-mirror/platform_frameworks_base`，commit `1cdfff555f4a21f71ccc978290e2e212e2f8b168`，2025-03-27；行号按该 commit）

> URL 模板：`https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/<path>#L<start>-L<end>`。mirror 非实时 HEAD，当前 AOSP 可能有漂移；设备/API 37 行为以实测为准。
>
> 例：`https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/services/core/java/com/android/server/Watchdog.java#L858-L862`

- `core/res/res/values/attrs_manifest.xml:3102-3105`（isolatedProcess 文档）、`:3108-3111`（externalService）、`:3116-3136`（useAppZygote）
- `core/java/android/content/pm/ServiceInfo.java:53-58`（`FLAG_ISOLATED_PROCESS`）、`:67-83`（`FLAG_USE_APP_ZYGOTE` 代价）
- `core/java/android/os/Process.java:346`（`FIRST_ISOLATED_UID=99000`）、`:929-951`（`isIsolated`/`isIsolatedUid`）、`:1358-1367`（`killProcess` → `sendSignal`）
- `services/core/java/com/android/server/am/ProcessList.java:1885-1890`（隔离进程不取 gids）、`:2515-2518`（不挂载 app 数据目录）、`:2689`（不能复用进程）、`:3098`/`:3124`（killBackgroundProcesses 的两道过滤）、`:3385`（隔离 UID 分配）
- `services/core/java/com/android/server/am/ActivityManagerService.java:2779-2790`（隔离进程只有 package/permissionmgr）、`:3673`（killBackgroundProcesses → killPackageProcessesLSP）、`:4615`/`:4762`（provider 列表/common services）、`:13588`（直接进程重启排除 isolated）
- `services/core/java/com/android/server/am/ActiveServices.java:5877-5883`（isolated 每次 startProcessLocked 新进程）、`:7308-7333`（服务死亡后 scheduleServiceRestartLocked）、`:2159-2200`（FGS 权限检查）、`:2851-2855`（FGS type 用 `r.app.uid`）
- `services/core/java/com/android/server/Watchdog.java:259`/`:317`（`mCompleted` 状态）、`:858-862`（uptime 理由）、`:940-960`（`Process.killProcess(myPid)` + `System.exit`）
- `core/java/android/app/ActivityThread.java:7617-7639`（isolated 跳过 graphics/TrafficStats，网络服务不可用）、`:7740`（跳过字体预载）
- `core/java/android/app/Service.java:761-764`（FGS type 缺权限 → `SecurityException`）
- `services/core/java/com/android/server/am/ContentProviderHelper.java:1246-1253`（`queryContentProviders(app.processName, app.uid)`）
- `core/res/AndroidManifest.xml:7700-7703`（`FOREGROUND_SERVICE` normal）、`:4403-4406`（`KILL_BACKGROUND_PROCESSES` normal）

### 10.3 开源样例（仓库/path:line；license 逐项标注）

> URL 模板（commit 钉版见 §1.1）：
> - Chromium：`https://github.com/chromium/chromium/blob/ed3e89c78a09a1809239407e7cdaa66155279e7f/<path>#L<start>-L<end>`
> - AndroidX：`https://github.com/androidx/androidx/blob/4ea31479e2bd1d24759aad359f60e80ff35beccb/<path>#L<start>-L<end>`
> - nextvol：`https://github.com/Renakoni/nextvol/blob/c6e82c56f2cb8ded078662082c63da7c787211fe/<path>#L<start>-L<end>`
> - TinaIDE：`https://github.com/wuxianggujun/TinaIDE/blob/e9f4e852c1e2adf246fd3ba880b362312e131415/<path>#L<start>-L<end>`
> - DuckDuckGo：`https://github.com/duckduckgo/Android/blob/71cba3eafee571cd88682a4fc4932e539bab2efe/<path>#L<start>-L<end>`
> - Firefox：`https://github.com/mozilla-firefox/firefox/blob/efd2d1e6f77570ae6bc5294731a68101e45c81a3/<path>`
>
> 例（Chromium 沙箱 Service 声明）：`https://github.com/chromium/chromium/blob/ed3e89c78a09a1809239407e7cdaa66155279e7f/chrome/android/java/AndroidManifest.xml#L1372-L1380`

1. **Chromium（BSD-3-Clause）** — 生产级 isolated 子进程 + AIDL + forceKill 的完整先例：
   - `chrome/android/java/AndroidManifest.xml:977-983`（`DecoderService`：`exported=false` + `isolatedProcess=true` + `android:process=":decoder_service"`）、`:1372-1380`（`SandboxedProcessService0/1`：`android:process=":sandboxed_process{{i}}"` + `isolatedProcess=true` + `exported=false` + 自签名 `CHILD_SERVICE` 权限；0 号用 `useAppZygote=true`）
   - `base/android/java/src/org/chromium/base/process_launcher/IChildProcessService.aidl:16-27`（`bindToCaller` 两路身份校验；`oneway setupConnection`；`oneway forceKill`）
   - `base/android/java/src/org/chromium/base/process_launcher/ChildProcessService.java:192-196`（`forceKill()` → `Process.killProcess(Process.myPid())`）
   - `base/android/java/src/org/chromium/base/process_launcher/ChildProcessConnection.java:580-600`（`kill()`：先 `unbind` 再 `forceKill`，然后 notify died）、`:701-714`（`onServiceDisconnected`：不自动重启，让上层决定）
   - `base/android/java/src/org/chromium/base/process_launcher/IParentProcess.aidl`（`oneway finishSetupConnection`；`reportExceptionInInit`/`reportCleanExit` 非 oneway，注释“to ensure the browser receives the message before child exits”）
   - `docs/security/android-sandbox.md`（isolatedProcess 是 layer-one sandbox；browser 通过 Binder 传 fd 建立 IPC；AMS 创建进程的 6 步）
2. **AndroidX（Apache-2.0）** — 官方 AndroidX 的三个直接先例：
   - `pdf/pdf-document-service/src/main/AndroidManifest.xml:20-23`（`isolatedProcess="true"` + `exported="false"` + 无 intent-filter）
   - `pdf/pdf-document-service/src/main/kotlin/androidx/pdf/service/connect/PdfServiceConnectionImpl.kt:59-67`（`onServiceDisconnected` 里“如果不 unbind，系统会立即尝试重启服务”的经验注释）、`:94-113`（`disconnect` 先置状态再 close 再 unbind；catch `RemoteException`/`IllegalArgumentException`）
   - `work/work-multiprocess/src/main/java/androidx/work/multiprocess/RemoteExecute.kt:63-106`（per-call `DeathRecipient` → 协程 resume “Binder died”；`finally` 里 `unlinkToDeathSafely`，catch `NoSuchElementException`）
3. **Renakoni/nextvol（Apache-2.0）** — 与本任务最接近的 isolated + AIDL + PFD + terminate 真机样例（API 24/35）：
   - `app/src/main/AndroidManifest.xml:128-132`（`exported=false` + `isolatedProcess=true` + `:source_execution`）
   - `app/src/main/kotlin/.../IsolatedExecutionService.kt:53-113`（每个 Binder transaction 校验 `Binder.getCallingUid() == applicationInfo.uid`；`terminate()` → `killProcess`；单线程 executor；内存采样自杀）、`:141-150`（`onUnbind`/`onDestroy` 自杀）
   - `app/src/main/kotlin/.../IsolatedWorkerConnection.kt:38-66`（`linkToDeath`；`onServiceDisconnected`/`onNullBinding`/`onBindingDied` 统一 `died.complete`；`close()` 先 terminate、等 death ≤2 s、再 unbind；已关闭/已死亡时收到新连接立即 terminate）
   - `app/src/main/kotlin/.../AndroidIsolatedExecutor.kt:204-212`（宿主断言 `workerUid != Process.myUid()`、authority 校验）、`:239-266`（callback 校验 `Binder.getCallingUid() == workerUid`、单次结果、timeout/retire/keepWorker）、`:175-184`（连接/死亡/超时 select）
   - `sources/execution/README.md:5-7`、`:17`（onBind 的 calling UID 不可信；Application 在 isolated UID 跳过初始化；进程名用下划线避免 API 24 安装失败）、`:51-61`（18 例 API 24/35 真进程测试）
   - `app/src/main/aidl/.../IExecutionCallback.aidl`（`oneway interface` + `onResultFile(in ParcelFileDescriptor)`）、`IIsolatedExecutionService.aidl`（`void execute(...)` 两路；`oneway void terminate()`）
4. **wuxianggujun/TinaIDE（GPL-3.0，仅参考思路，不复制代码）** — generation 检查 + 自杀的样例：
   - `core/plugin/src/main/java/.../PluginRuntimeService.kt:41-44`（`RuntimeEntry(generation, runtime)`）、`:73-75`（`entry.generation != request.generation` → `STALE_GENERATION`）、`:99-101`（`terminate()` → `killProcess`）、`:131-147`（watchdog 采样内存 → `killProcess`）
5. **DuckDuckGo Android（Apache-2.0）** — Application guard 的独立佐证：
   - `app/src/main/java/com/duckduckgo/app/global/MultiProcessApplication.kt:30-34`（isolated 进程“most system services return null and bindService() is forbidden”，跳过全部 DI/初始化）
6. **Mozilla Firefox / GeckoView（文件头 MPL-2.0）** — isolated 进程的系统性说明：
   - `mobile/android/docs/geckoview/project/isolated-process/overview.md`（API 16 起支持；`bindIsolatedService` 需 API 29；`Process.isIsolated()` 在 28 前 hidden；隔离进程不能 start/bind 大多数组件；UID/权限/文件/SELinux 差异；`ps -Z` 的 `isolated_app`/`u0_i` 观察法）

### 10.4 本地检查（本次实际运行）

- `javap -classpath C:/Android/Sdk/platforms/android-37.0/android.jar` 对以下类的签名核对（**只证明 compileSdk 37 的 API 存在，不证明运行时行为**）：
  - `android.os.Process`：`killProcess(int)`、`myPid()`、`myUid()`、`isIsolated()`、`isIsolatedUid(int)`
  - `android.os.IBinder`：`FLAG_ONEWAY`、`isBinderAlive()`、`linkToDeath(DeathRecipient,int)`、`unlinkToDeath(DeathRecipient,int)`
  - `android.content.ServiceConnection`：`onServiceDisconnected`、`onBindingDied`、`onNullBinding`
  - `android.os.ParcelFileDescriptor`：`createPipe()`、`createReliablePipe()`、`closeWithError(String)`、`checkError()`
  - `android.content.pm.ServiceInfo`：`FLAG_ISOLATED_PROCESS`、`FLAG_USE_APP_ZYGOTE`
  - `android.content.Context`：`bindService(...)`、`bindIsolatedService(...)`
  - `android.os.SystemClock`：`uptimeMillis()`、`elapsedRealtime()`、`elapsedRealtimeNanos()`、`uptimeNanos()`
  - `android.app.ActivityManager`：`killBackgroundProcesses(String)`
- 读取本仓库：`app/build.gradle.kts:9/21/22/63/69/78/82-85`、`gradle/libs.versions.toml:2`、`app/src/main/AndroidManifest.xml`（无 service）、`SmartMistakeBookApplication.kt:285`、`core/data/.../QuickJsVisualExecutionEngine.kt:72-105/164-192/604-631`、`core/domain/.../VisualExecutionEngine.kt:40-46/106-139/218-288`、`docs/current/visual-generation-contract.md:60-70/74-88`。
- 合并清单：`app/build/intermediates/merged_manifest/strictOfflineDebug/processStrictOfflineDebugMainManifest/AndroidManifest.xml:146/255/264/273/311-329/439-443`（providers 均无 `android:process`）。
