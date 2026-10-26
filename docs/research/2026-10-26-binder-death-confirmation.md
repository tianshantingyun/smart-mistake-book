# Binder death 确认语义与 isolatedProcess SIGKILL 时序（R1，驱动批次 24 T1）

> **研究边界（先声明）**：本文是源码/文档/仓库证据研究，不修改产品代码，不运行 Gradle，不执行设备用例，不做 git 写操作。
> `developer.android.com` 与 `android.googlesource.com` 在本机不可达（`curl` exit 28 / `WebFetch` 10 s 超时），因此官方 Javadoc 逐字读自 AOSP 镜像源码（`IBinder.java`、`ServiceConnection.java`），AOSP 服务端路径读自 `aosp-mirror/platform_frameworks_base`（`android-14.0.0_r1`）与 `LineageOS/android_frameworks_native`（`lineage-21.0`，Android 14）。社区实现读自 GitHub 钉版 commit，并核过 license。
>
> **快照时点**：2026-10-26T20:08:13+08:00，HEAD `5bc8bdf868e1`。工作树极脏（200+ M / 400+ 未跟踪），且**研究期间批次 24 T1 至少两波并发修改了目标代码**：`VisualJsClientEngine.kt` 最新为 +93/−9（20:02 时还是 +65/−8），`VisualJsIsolationInstrumentedTest.kt` 最新为 +113/−5。工作树修复在本报告落盘期间仍可能变化；本文同时记录 **HEAD 的失败形态**与 **工作树（20:08 快照）的未提交修复形态**，行号以该快照为准。不把工作树修复当作已提交或已验证。

---

## 0. 结论摘要

1. **`binderDied` 是唯一按“binder 实例”证明进程死亡的事件**。它由 binder 驱动在进程销毁时以 `BR_DEAD_BINDER` 投递，在客户端的一条 Binder 线程上直接回调；`onServiceDisconnected` 是框架自己注册的 `DeathMonitor` 收到同一事件后**再 post 到主线程**的回调；`onBindingDied` 走的是 AMS `bringDownServiceLocked` 的另一条路径，**不是普通 SIGKILL 的主回调**；`onNullBinding` 只表示 `onBind()` 返回 null。
2. **“先 `unlinkDeath` 再等 death”是结构性丢事件**。官方语义：`unlinkToDeath` 返回 `true` 就是“保证 `binderDied()` 不会再被调用”；只有返回 `false` 才表示目标已死、回调已经或即将发生。HEAD 的 `abandon()` 忽略返回值、先解注册、再把 `link.binder` 清空，而 `link.death` 的唯一完成点又只由 `markDead()` 触发 → 在 E-01c 的时序下（unlink 时服务还活着、随后 16 ms 被 SIGKILL）1 s 内必然等不到，四次独立运行稳定复现。
3. **可靠判据是“per-bind DeathRecipient + 保持 proxy 引用 + 有界等待”**。`isBinderAlive()` 不是主动探测：它只读 `BpBinder::mAlive` 缓存位；只有收到 `BR_DEAD_BINDER`（`sendObituary()`）或一次事务返回 `DEAD_OBJECT` 才会变 false。保留注册时轮询它是有效的第二观察点；**先解注册再轮询则可能永远为 true**。若要主动探测，用 `pingBinder()`（会真发一次事务，但远端活着但卡死时可能阻塞，不能当超时机制）。超时只能 fail-closed，不能重建。
4. **冷启动 4.6–7.5 s > 5 s 是实测事实；正确处理不是只把 5 s 改大，也不是用同样 5 s 退避重试**。推荐：首连 15 s（工作树已改）+ 明确的“加载中”状态/回调 + 可重试失败态（不在单次 execute 内自动阻塞重试）；同时补**首个 execute 的预热/就绪态**，因为 E-01d 的 1500 ms 服务硬期限会在冷启动 CPU 竞争下被触发（请求被杀）。稳态 1500/2500 ms 不动；冷启动超时不应写进 `programHash` 熔断。
5. **工作树修复已按同一方向收口，但仍有 4 个实质缺口（外加 1 个文档小漂移）**：最新工作树已把 death 顺序、2 s 死亡预算、10 s 重建预算、超时分支的 `markDead`、未确认时丢弃迟到死亡的日志、16 ms 文案全部收口（§4.4 列出对应行号）；**仍未解决**：① 首个 execute 在冷启动负载下触发 1500 ms 硬期限（E-01d，需要 warm-up/ready，本批未做）；② 连接超时仍直接永久 `unavailable`，没有 LOADING/可重试状态（`:552-557`）；③ `close()` 的死亡等待仍在 `unlinkDeath` 之后（只作诊断，不影响关闭语义）；④ 全部修复与新增用例**未设备复跑**，且仓库没有 ms 级 death 延迟实测；另有 `DEATH_POLL_INTERVAL_MILLIS` 的“1 s 预算”旧文案未同步 2 s。

---

## 1. 方法与证据边界

### 1.1 实际读过的权威源

| 主题 | 读取对象 | 用途 |
|---|---|---|
| `IBinder` / `DeathRecipient` 契约 | `aosp-mirror/platform_frameworks_base@android-14.0.0_r1`：`core/java/android/os/IBinder.java` | `linkToDeath`/`unlinkToDeath`/`isBinderAlive`/`pingBinder` 的逐字语义 |
| `ServiceConnection` 契约 | 同仓库 `core/java/android/content/ServiceConnection.java` | 四个回调的官方触发条件、线程声明 |
| 框架死亡回调链 | 同仓库 `core/java/android/app/LoadedApk.java`、`ContextImpl.java` | `DeathMonitor` → 主线程 → `onServiceDisconnected`；unbind 的 `doForget()` |
| AMS 侧 bring-down / restart | 同仓库 `services/core/java/com/android/server/am/ActiveServices.java` | `onBindingDied` 的来源；SIGKILL 后是否重启 |
| Binder 原生死亡通知 | `LineageOS/android_frameworks_native@lineage-21.0`：`libs/binder/BpBinder.cpp`、`libs/binder/IPCThreadState.cpp` | `BR_DEAD_BINDER` → `sendObituary()` → `binderDied()`；`mAlive` 的真实含义 |
| 社区成熟实现 | nextvol、AndroidX Work/PDF、Chromium（见 §8） | “确认死亡后重建”的参考顺序 |
| 仓库设备证据 | `docs/research/evidence/2026-10-26-batch-23/step01b…step01e`、`00-index.md` | 实测 attach / SIGKILL / 失败时间线 |
| 仓库代码 | `VisualJsClientEngine.kt`（HEAD + 工作树）、`VisualJsService.kt`、`VisualJsIsolationInstrumentedTest.kt`、契约与预算 | 现状与修复落点 |

### 1.2 没有做的事（不得读成已通过）

- **没有运行 Gradle / JVM 测试 / 仪器化测试 / `adb am instrument`**。本文所有“红/绿”都是对既有设备证据的引用或对代码路径的推演，不是本轮执行结果。
- **没有做 ms 级 Binder death 延迟测量**。仓库没有 `binderDied`/`onServiceDisconnected` 的埋点日志；本文只给文档语义、AOSP 路径和可复跑探针设计。
- **没有验证 API 23–27 的运行时差异**（官方文档标注 `onBindingDied`/`onNullBinding` 自 API 26 起；本轮官方页不可达，未逐字复核，也未在旧设备实测）。
- **没有验证 OEM/冻结/深度睡眠下的偏差**。AOSP 14 源码是本文平台语义的版本边界。

### 1.3 研究期间的目标文件变化（纪律记录）

开工时 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/` 是干净的；研究期间批次 24 T1 并发写入：

```text
 M app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt      (+93/−9；20:02 曾为 +65/−8)
 M app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt (+113/−5)
```

工作树 `abandon()` 已把 `unlinkDeath` 移到确认之后，并新增 `awaitDeathConfirmation()`（死亡事实 + `isBinderAlive` 轮询）、首连 15 s/重建 10 s 分离预算、2 s 死亡预算；`abandon` 未确认时还会记录“late death is dropped and never used to rebuild”。本文 §4.4 对该修复做源码级审查；**不修改它，也不替它宣称设备绿**。`docs/known-defects.md` KD-66 与 `docs/current/visual-generation-contract.md` 也已被同一线改写，相关句子在本报告落盘时已是“代码已修、设备复跑 UNVERIFIED”的口径。

---

## 2. Q1：四个回调的触发条件、线程与 SIGKILL 时序

### 2.1 回调矩阵

| 回调 | 谁注册/谁触发 | 触发条件 | 线程 | 延迟 | 本地 unbind 之后还会来吗 |
|---|---|---|---|---|---|
| `IBinder.DeathRecipient.binderDied()` | 客户端自己 `linkToDeath`；binder 驱动 `BR_DEAD_BINDER` | **承载该 binder 对象的进程消失**（SIGKILL、崩溃、正常退出都算），或 binder 节点被销毁；只对远程 binder 有效 | 客户端进程的 **Binder 线程**（读驱动命令的线程） | 无平台 SLA；进程销毁后由驱动排队，下一次 binder 线程调度即回调 | **会**，只要自己的 recipient 仍注册且仍持有 proxy 引用；`unbindService` 不撤销它 |
| `ServiceConnection.onServiceDisconnected()` | 框架 `LoadedApk.ServiceDispatcher` 在每次连接成功时注册自己的 `DeathMonitor`（`LoadedApk.java:2170`） | 服务进程崩溃/被杀；框架的 `doDeath()` 校验同一 binder 后回调（`LoadedApk.java:2206-2219`） | **主线程**（`death()` post `RunConnection`，`LoadedApk.java:2128-2146`；类文档 `ServiceConnection.java:25-26` 明说） | 至少多一次主线程 Handler 跳；**与 `binderDied` 的执行先后不保证**（两者是同一批 death 的不同 recipient；主线程可能先跑到）；主线程忙则更晚 | **不会**。本地 `unbindService` 先走 `forgetServiceDispatcher`（`LoadedApk.java:1955-1966`）→ `doForget()`，解注册框架 `DeathMonitor` 并清空连接（`ContextImpl.java:2163-2178`、`LoadedApk.java:2092-2104`） |
| `ServiceConnection.onBindingDied()` | AMS `ActiveServices.bringDownServiceLocked()` 调 `cr.conn.connected(name, null, true)`（`ActiveServices.java:5539-5568`） | **绑定本身被判死、接口不会再给新连接**：服务被 bring down（如宿主应用更新/卸载、服务被停止、崩溃重试超限等）。`LoadedApk.doConnected(dead=true)` 回调（`LoadedApk.java:2191-2196`） | 主线程（同一 `RunConnection` 机制） | 由 AMS bring-down 时机决定，与“进程被 SIGKILL”没有固定关系 | 不会（连接已被框架移除；旧平台可能没有这个默认方法，官方标注 API 26 引入，本轮未逐字复核） |
| `ServiceConnection.onNullBinding()` | AMS 在绑定成功但 `onBind()` 返回 null 时回调 | `onBind()` 返回 null，绑定永远不可用 | 主线程 | 在连接建立阶段 | 不会；官方要求仍然 `unbindService` 释放跟踪资源 |

**关键区分**：普通 SIGKILL 一个**仍然被绑定**的服务进程，走的是框架 `DeathMonitor` → `onServiceDisconnected`；AMS 的 `killServicesLocked()` 里手动 `connected(...)` 的通知块是被注释掉的，AOSP 注释原文是 “we are letting the client link to the service for death notifications”（`ActiveServices.java:6404`）。`onBindingDied` 是“AMS 主动 bring down”的路径，不要把它当 SIGKILL 的主信号。

官方对 `onServiceDisconnected` 的补充（`ServiceConnection.java:48-57`）：它**不解除 binding**本身；如果服务之后被系统重新拉起，客户端还会收到 `onServiceConnected`。这正是“不 unbind 就可能被重启”的来源；如果不想重建，应在死亡回调里显式 `unbindService`（AndroidX PDF 的注释是同一经验的独立佐证，`PdfServiceConnectionImpl.kt:59-67`）。

### 2.2 SIGKILL 时序（源码推导）

```text
宿主进程                                  服务进程（isolatedProcess）
  | bindService / linkToDeath(recipient)      |
  |------------------------------------------>| onBind → Binder 对象
  |                                           |
  | 宿主调 terminate(gen) [oneway] 或服务 watchdog 到点
  |------------------------------------------>| Process.killProcess(myPid()) → SIGKILL
  |                                           X 无 onDestroy/onUnbind 可执行（SIGKILL 不可捕获）
  | binder 驱动：服务节点销毁，向所有请求过 death 的客户端排 BR_DEAD_BINDER
  |<------------------------------------------|
  | Binder 线程：BpBinder::sendObituary()      |
  |   mAlive = 0                               |
  |   reportOneDeath → BinderProxy.sendDeathNotice → 你的 binderDied()
  |   同一批也通知框架的 DeathMonitor
  |                                           |
  | 框架 DeathMonitor → post 主线程 → onServiceDisconnected()
  |                                           |
  | AMS 收尸：killServicesLocked()
  |   binding 仍在且 allowRestart → scheduleServiceRestartLocked()（可能再起一个新进程）
  |   否则 bringDownServiceLocked() → connected(..., true) → onBindingDied()
```

**顺序说明**：上图是“因果链”而不是严格执行序。我们的 `DeathRecipient` 与框架 `DeathMonitor` 是同一批 `BR_DEAD_BINDER` 的不同 recipient；`BpBinder::sendObituary()` 按注册顺序遍历并直接回调，框架的 `DeathMonitor` 先注册（`doConnected` 在 `onServiceConnected` 之前注册），它的 `binderDied()` 只是 post 主线程；主线程可能在 Binder 线程回调完我们的 recipient 之前就执行 `onServiceDisconnected`。**不要依赖 `binderDied` 与 `onServiceDisconnected` 的先后**；两者都只用于幂等地完成同一个死亡状态。

**两种宿主状态**：

- **宿主仍绑定**：自己注册了 recipient 就会收到 `binderDied`；框架会 post 主线程 `onServiceDisconnected`；AMS 若仍有 binding 且允许重启，可能 `scheduleServiceRestartLocked`，随后再来 `onServiceConnected`。普通 SIGKILL 的主回调不是 `onBindingDied`；若 AMS 同时决定 bring down（崩溃重试超限、用户停止、包更新等），仍可能收到 `onBindingDied`。
- **宿主已 unbind（本项目的 abandon 路径）**：框架 `doForget()` 已解注册 `DeathMonitor`，`onServiceDisconnected` **不会来**；只剩客户端自己的 `binderDied`（前提：仍注册且持 proxy）。isolated 服务在 unbind 后会被 AMS 以 “isolated not needed” 收掉（E-01c logcat），或由服务自己的 `onUnbind()` → `killSelf()` 收掉（`VisualJsService.kt:94-97`）；两条都会产生进程死亡，因此保留 recipient 就能观测到。**如果这时又 `unlinkDeath`，宿主端将没有任何死亡信号**——这正是 E-01c。

源码依据：

- `IPCThreadState.cpp:1474-1480`：`case BR_DEAD_BINDER` → `proxy->sendObituary()`，然后回 `BC_DEAD_BINDER_DONE`。
- `BpBinder.cpp:505-553`：`sendObituary()` 先置 `mAlive = 0`，再对每个 `Obituary` 调 `reportOneDeath()`；`reportOneDeath()` 直接 `recipient->binderDied(...)`。
- `BinderProxy.java:699-708`：`sendDeathNotice()` 直接调 `recipient.binderDied(binderProxy)`，并捕获 `RuntimeException`（回调异常不会杀掉 Binder 线程）。
- `LoadedApk.java:2250`：框架 `DeathMonitor.binderDied()` → `death(name, service)`；`2128-2146` post 到主线程；`2206-2219` 校验同一 binder 后 `onServiceDisconnected`。
- `ActiveServices.java:6404` 的注释块 + `6545` 的 `scheduleServiceRestartLocked`，解释“为什么有时会看到服务又被拉起来”。

**`unbindService` 的位置**：它只移除 AMS 的绑定理由，并让框架 `doForget()` 解注册**框架自己的** `DeathMonitor`；它**不会**撤销客户端自己注册的 `DeathRecipient`。所以“先 unbind、保留自己的 recipient 等 death”是可行的；“先 unbind、再 `unlinkDeath`、再等 death”则两条信号都没了。

### 2.3 仓库实测证据能证明什么、不能证明什么

| 证据 | 时间线（logcat） | 能证明 | 不能证明 |
|---|---|---|---|
| E-01b `step01b-visualjs-isolation-rerun.md:76-79` | 30.934 test start；31.662 attach（0.728 s）；31.678 `Killing … isolated not needed`；32.719 断言失败 | 服务在 attach 后 **16 ms** 被 SIGKILL；客户端 1 s 后仍未确认 | 16 ms 是 **attach→SIGKILL**，不是 `binderDied` 延迟；当次 recipient 已被 `unlinkDeath` 摘掉，所以本来就不会有回调 |
| E-01c `step01c-abandon-confirmed.md:70-84` | 13.747 Start proc；14.390 attach；14.406 SIGKILL | 同上；失败可定位到 `VisualJsClientEngine.kt:570-571` 的顺序 | 同上 |
| E-01d `step01d-visualjs-isolation-coldboot.md:61-72` | 41.104 Start proc → 45.682 attach（**4.578 s**）；46.523 第二次 Start proc → 54.005 attach（**7.482 s**） | 冷启动 attach 4.6–7.5 s，确实超过旧 5 s 连接预算；第二次（重建）在冷系统上也要 7.5 s | 不能证明稳态重建也慢；E-01b/E-01e 沉降后 attach 0.7–0.88 s |
| E-01e `step01e-visualjs-isolation-postboot-settled.md:65-76` | 00.604 Start proc → 01.482 attach（**0.878 s**）；14.106 watchdog SIGKILL；唯一红仍是 abandon | 加载态与结构性失败已分离；死亡→重建路径在注册 kept 时可用 | 没有 `binderDied`/`onServiceDisconnected` 的毫秒级配对日志 |

**结论（延迟）**：平台文档**没有**承诺 `binderDied` 的延迟上界；AOSP 只能证明“驱动命令被 Binder 线程读到就回调”。`onServiceDisconnected` 必然比 `binderDied` 多一次主线程调度。仓库现有证据**没有**测过这两个回调的延迟；HEAD 的 1 s / 工作树的 2 s 都是产品自设预算，不是平台保证。

### 2.4 16 ms 的正确读法（工作树早期 KDoc 已修正）

研究期间工作树早期版本曾在 `VisualJsClientEngine.kt` 的 KDoc 写“只轮询而不保留注册则会把正常的 16 ms 死亡拖到轮询间隔”。这句话没有证据；20:08 快照已改成（`:614-615`）：

> “预算 2 s 是产品选择，不是平台 SLA：AOSP 不承诺 `binderDied` 的延迟上界（仓库也无毫秒级 death 延迟实测；E-01b 的 16 ms 是 attach→SIGKILL，不是 death 延迟）。”

这与本文一致。证据链：

- 16 ms 来自 E-01b 的 **attach 时刻（31.662）→ SIGKILL 时刻（31.678）**，见 `step01b-visualjs-isolation-rerun.md:84`（“服务进程在 attach 后 16 ms 内被 … SIGKILL 终止”）。
- 该次运行没有 death 回调送达（先 unlink），因此不能用它推断死亡通知延迟。
- **不要用一个未经测量的数字支撑“轮询间隔 50 ms 足够”的结论**；50 ms 只是工程选择。当前 `DEATH_POLL_INTERVAL_MILLIS` 的 KDoc 仍写“1 s 预算内至少 20 个判定点”（`:892-893`），与 2 s 预算不一致，建议改成 2 s 口径。

---

## 3. Q2：unbind 与 unlinkDeath 的顺序陷阱

### 3.1 官方语义（逐字）

`IBinder.unlinkToDeath`（`IBinder.java:344-364`）：

> “@return `true` if the recipient is successfully unlinked, **assuring you that its `binderDied()` method will not be called**; `false` if the target IBinder has already died, meaning the method has been (or soon will be) called.”

`IBinder.linkToDeath`（`IBinder.java:322-342`）：

> “If this binder object unexpectedly goes away (typically because its hosting process has been killed), then the given `DeathRecipient`'s `binderDied()` method will be called.”
> “This will automatically be unlinked when all references to the linked binder proxy are dropped.”
> “@throws RemoteException if the target IBinder's process has already died.”

`IBinder.isBinderAlive`（`IBinder.java:216-224`）：

> “@return `false` if the process is not alive. **Note that if it returns true, the process may have died while the call is returning.**”

`IBinder.pingBinder`（`IBinder.java:206-215`）：

> “@return Returns `false` if the hosting process is gone, otherwise the result … returned by the `pingBinder()` implementation on the other side.”

### 3.2 HEAD 为什么必然丢（E-01c 的结构性原因）

HEAD `abandon()`（`git show HEAD:app/src/main/kotlin/.../VisualJsClientEngine.kt`，行号与 `docs/research/2026-10-26-batch-24-baseline.md:321` 一致）：

```kotlin
runCatching { link.service?.terminate(link.generation) }   // oneway
link.service = null
runCatching { context.unbindService(link.connection) }     // 框架 doForget()，解注册框架 DeathMonitor
unlinkDeath(link)                                          // ← 先解注册自己的 recipient，并清空 link.binder
val confirmed = withTimeoutOrNull(1000) { link.death.await() }
```

`link.death` 的**唯一完成点**是 `Link.markDead()`；`markDead` 只由 `DeathRecipient`/`ServiceConnection` 回调触发（E-01c §5、baseline §5.1）。所以：

- 如果 `unlinkDeath` 时服务还活着（E-01b/E-01c 的 16 ms 窗口正是这种情况），`unlinkToDeath` 返回 `true`，官方保证 `binderDied()` **不会**被调用；同时 `unbindService` 已让框架不会再回调 `onServiceDisconnected` → `death` 永远不完成。
- 如果 `unlinkDeath` 时服务已死，`unlinkToDeath` 返回 `false`，回调“已经或即将”发生；这种情况下等 `death` 可能成功。但 HEAD **忽略返回值**，也把 `link.binder` 清空了，无法区分“已死”与“还活着”。这不是可以靠运气绕过的竞态，而是两条完成路径都被主动切断。

**回答“先 unlinkDeath 再等 death 是否必然丢事件”**：

- **在目标仍活着时，是（E-01c 就是这种情况）**。`true` 分支保证不再回调；`false` 分支才可能已死。把“等 death”放在 `unlinkDeath` 之后，等于把成功条件押在 `unlinkToDeath` 返回 `false` 这个小概率分支上。
- E-01c 是设备实测：服务确实 SIGKILL、客户端 1 s 内确实没有 death。这是结构性缺陷，不是“1 s 太短”单独造成的。
- 若有人主张“先 unlink 再等”也能工作，必须同时证明 `unlinkToDeath` 返回 `false`；那说明服务在 unlink 前已死，正确的做法是直接读返回值/走已死分支，而不是等一个 Deferred。

### 3.3 社区成熟写法

| 实现 | 顺序 | 说明 | License |
|---|---|---|---|
| **nextvol** `IsolatedWorkerConnection.kt:43-67`（commit `c6e82c5`） | `bind` 时 `linkToDeath({ died.complete(Unit) })`；`close()`：`terminate()` → `if (remote != null) withTimeoutOrNull(2000) { died.await() }` → `finally { unbindService(connection) }` | **先等死亡，再 unbind**；`died` 由 `linkToDeath`/`onServiceDisconnected`/`onBindingDied`/`onNullBinding` 四路完成。这是与本任务最接近的“确认死亡后重建”客户端形状 | Apache-2.0 |
| **AndroidX Work** `RemoteExecute.kt:58-106`（commit `4ea3147`） | 每次调用 `linkToDeath`；死亡时 `continuation.resumeWithException("Binder died")`；`finally { deathRecipient?.let { binder.unlinkToDeathSafely(it) } }` | **先等结果/死亡，再在 finally 解注册**；`unlinkToDeathSafely` 捕获 `NoSuchElementException`。适合“每次调用一个死亡观察者”的短生命周期 | Apache-2.0 |
| **AndroidX PDF** `PdfServiceConnectionImpl.kt:59-67`、`:94-117` | 不注册自己的 recipient；`onServiceDisconnected` 里“如果还有处理就不 unbind，否则立即 unbind，避免系统立即重启服务”；`disconnect()` 先把状态置 `Disconnected`，再 close 文档，再 unbind（catch `RemoteException`/`IllegalArgumentException`） | 框架回调 + 主动 unbind 去掉重启理由；不承担“确认旧进程终结后重建”的 generation 语义 | Apache-2.0 |
| **Chromium** `ChildProcessConnection.java:580-600`、`:701-714` | `kill()`：先 `unbind()`，再 `service.forceKill()`（oneway），再把 `mKilledByUs=true` 并 `notifyChildProcessDied()`；`onServiceDisconnected` 里 `stop()`，注释“We don't want to auto-restart on crash. Let the browser do that.” | **客户端自己拥有 kill 确认**（不依赖 binder death 计时）；unbind 先于 forceKill，但它自己立刻 notify died，不“等一个可能不来的回调” | BSD-3-Clause |

**维护度核对（2026-10-26 `gh api`）**：nextvol `pushed_at=2026-10-13`、stars 2、open issues 55、未归档——它是最近的同型样例，不是广泛使用的库，只借结构、不引依赖；AndroidX Work/PDF 与 Chromium 是官方/大型活跃仓（`androidx/androidx` `pushed_at=2026-10-26`、stars 6121；`chromium/chromium` `pushed_at=2026-10-26`、stars 25143），作为“成熟写法”的主证据；三者 license 分别为 Apache-2.0/Apache-2.0/BSD-3-Clause。

**共同点**：

1. 没有人把 `unlinkToDeath` 放在“等待死亡”之前。
2. 要么“等 death 再 unbind”（nextvol），要么“unbind 但保留自己的 death 观察者/自己拥有 kill 事实”（Chromium 的 self-notify；AndroidX Work 的 per-call recipient）。
3. 都显式处理 `onServiceDisconnected`/`onBindingDied`/`onNullBinding` 的幂等，避免多回调重复完成。

### 3.4 推荐顺序（兼顾“去重启理由”和“不丢 death”）

```text
1. state = ABANDONED（拒绝新请求；旧 generation 的回调全部丢弃）
2. service.terminate(generation)            // oneway，服务自己 SIGKILL
3. context.unbindService(connection)        // 去掉 AMS 重启理由；不影响自己的 DeathRecipient
4. 保留 link.binder（proxy 强引用）与 recipient，awaitDeath(≤2 s)
     4a. death 完成 / isBinderAlive=false → markDead → confirmed
     4b. 超时 → DEATH_UNCONFIRMED，unavailable=true，不重建
5. confirmed 后：unlinkToDeath（此时返回 false 或已无注册）、清 link、允许下一次执行扣唯一一次重建预算
6. 未 confirmed：保持 fail-closed；若要保留迟到死亡的诊断，可继续持有 recipient/proxy，但绝不再 bind；
   若选择解注册，必须在文档/日志里写明“迟到死亡被丢弃，永不据此重建”
```

**为什么第 3 步可以早于第 4 步**：`unbindService` 只解注册**框架的** `DeathMonitor`（`LoadedApk.doForget`），不触碰客户端自己的 `DeathRecipient`；只要 `link.binder` 仍持有 proxy 引用，驱动仍会向该 proxy 投递 `BR_DEAD_BINDER`。E-01c 的失败不是“unbind 太早”，而是“unbind 后又 `unlinkDeath`”。

**为什么 nextvol 选择先等再 unbind**：它没有在 `terminate` 后立刻 unbind，避免 AMS 在死亡前按 binding 重启；代价是若 `terminate` 的 oneway 丢失/服务卡死，AMS 可能先把服务拉起来。两种顺序都可用，但**都必须保留自己的 death 观察者**。对本项目，`VisualJsService.onUnbind()` 会 `killSelf`（`VisualJsService.kt:94-97`），且 isolated 服务在 unbind 后会被 AMS 以 “isolated not needed” 杀掉（E-01c logcat），所以“先 unbind、保留 recipient 等 death”是可用的，且更符合契约“不能并发放大进程数”。

---

## 4. Q3：宿主侧“确认旧实例终结”的可靠判据

### 4.1 判据分级

| 判据 | 能证明什么 | 不能证明什么 | 建议 |
|---|---|---|---|
| 该 `Link` 自己的 `DeathRecipient.binderDied` | **该 binder proxy 对应的旧进程已消失**；per-bind 新建，旧实例的死亡不会污染新实例 | 不证明新实例可用；不证明 AMS 已收尸/不会重启 | **主判据**。必须在整个等待期间保持注册，并持有 proxy 强引用 |
| `isBinderAlive() == false` | 本地 `BpBinder::mAlive` 已清零：收到过 `BR_DEAD_BINDER`，或某次事务返回 `DEAD_OBJECT` | `true` 不证明活着；**未注册 death 且没有失败事务时，进程死后它可能一直为 true** | **第二观察点**。保留注册时轮询有意义；不可单独作为“旧实例终结”的主动探测 |
| `pingBinder() == false` | 主动事务失败，远端已不可达 | 远端活着但 Binder 线程卡死时可能阻塞；无超时参数；不能当硬超时 | **可选诊断**，放 IO/独立线程；不要在主状态机里无界等待它 |
| `unlinkToDeath` 返回值 | `true`：解除时还活着，回调保证不会来；`false`：已死，回调已发生/即将发生 | 不能替代等待（返回值本身就是“你是否解早了”的判据） | 清理时记录返回值；`true` 说明确认逻辑有问题 |
| `onServiceDisconnected` | 框架也观察到同一 binder 死亡 | 主线程可能延迟；本地 unbind 后不会来；可被 AMS 重启后的新连接覆盖 | 作为幂等补充，不作为唯一判据 |
| generation nonce | 新实例与旧实例的结果/回调不会混 | 不证明旧实例已死 | 与 death 判据配合，防止“旧帧当新帧” |
| PID / 进程名 / “terminate 已返回” | 几乎不能证明 | PID 可复用；`killProcess` 异步；隔离进程 PID 宿主不可靠获取；terminate 是 oneway | **不要用** |

### 4.2 `isBinderAlive` 的真实语义（为什么它不是“poll 就能确认”）

`IBinder.isBinderAlive()` 的 Javadoc 只说“false if the process is not alive”，并明确“返回 true 时进程可能在返回途中死亡”。实现（`BpBinder.cpp:309-312`）：

```cpp
bool BpBinder::isBinderAlive() const { return mAlive != 0; }
```

`mAlive` 只在两处清零：

1. `sendObituary()`（`BpBinder.cpp:505-531`）：收到 `BR_DEAD_BINDER` 后。
2. `BpBinder::transact()` 返回 `DEAD_OBJECT` 时（`BpBinder.cpp:393`）。

如果客户端**没有**注册 death、也没有发起事务，`mAlive` 不会因为远端进程死亡而自动变 false。因此：

- “保留 `DeathRecipient` + 轮询 `isBinderAlive`”是合理的：death 通知会同时把 `mAlive` 清零；轮询是第二观察点，能覆盖 Java 回调异常/丢失。
- “先 `unlinkDeath`，再轮询 `isBinderAlive`”可能永远看到 true；这不是可靠的兜底。
- 若要真正主动探测，用 `pingBinder()`；它会走一次事务，远端已死时返回 `DEAD_OBJECT`（`IBinder.java:206-215`），但远端活着但卡死时可能阻塞，故只能作为可选诊断，不能作为超时机制。

### 4.3 推荐状态机

```text
CONNECTED
  ├─ 正常结果 → CONNECTED
  ├─ abandon 请求/宿主期限 → ABANDONED
  │     terminate(gen) → unbind()（保留 recipient + proxy）
  │     awaitDeath(≤2 s; death 优先, isBinderAlive 每 50 ms 复核)
  │       ├─ confirmed → DEAD → unlink → link=null → 下一次 execute 扣一次重建预算
  │       └─ timeout   → DEATH_UNCONFIRMED → unavailable=true → 永不重建
  └─ 非预期 death（binderDied/onServiceDisconnected/onBindingDied）
        → DEAD → unlink → unbind（去重启理由）→ 最多重建一次
             └─ 重建后再死 → unavailable=true（熔断，不再 bind）
```

**边界**：

- `onNullBinding`/握手失败/协议版本不符/UID 相同：`unavailable=true`，不重试（同一坏配置重试没有意义）。
- `close()` 不是确认路径：它只需要尽力解绑/解注册；`VisualJsClientEngine.close()` 当前先 `unlinkDeath` 再 `withTimeoutOrNull` 等 death（`VisualJsClientEngine.kt:224-239`），该等待只作诊断，不影响关闭语义。不要把它当“确认死亡”的参考实现。
- `dispose()` 用于替换未连接/陈旧 link，不承担死亡确认；它先 `unlinkDeath` 也不等 death，这是可以接受的，因为它不据此重建。

### 4.4 对工作树修复的源码审查（20:08 快照，未提交，未设备验证）

工作树 `abandon()`（`VisualJsClientEngine.kt:575-602`）与 `awaitDeathConfirmation()`（`:617-642`）已经：

- 保持 `link.binder` 到确认完成，先 `terminate` → `unbind` → 等 death，最后才 `unlinkDeath`；
- 死亡事实（`link.death`/`markDead`）优先，`isBinderAlive` 每 50 ms 复核；false 时走同一 `markDead`（CAS 幂等）；
- 未确认才 `unavailable=true`；确认后清 link，由下一次 execute 在 `connectLocked` 扣唯一一次重建预算；
- `handleDeath` 在 `ABANDONED` 状态短路，不会为 abandon 自动 bind；
- 首连 15 s（`:877`）、重建 10 s（`:883`）、死亡确认 2 s（`:890`）；
- 超时最后一段（`:634-640`）在 `isBinderAlive` 翻转时先 `markDead`，保证 `link.death`/`activeCall` 有界完成；
- 未确认时记录“late death is dropped and never used to rebuild”（`:589-596`），随后解注册；
- KDoc 已把 16 ms 写成 attach→SIGKILL（`:614-615`）。

**§4.4 待收口点 1（超时分支）已修复**；`docs/known-defects.md:2600` 引用的“R1 §4.4 待收口点 1”即此条，可在设备复跑后回链。剩余 4 项：

1. **首个 execute 的冷启动超时未修**：15 s 连接预算只覆盖 attach；首个 execute 仍会在冷启动负载下被 1500 ms 服务硬期限杀掉（E-01d）。需要 warm-up/ready（§5.3），不是继续加大超时常量。
2. **`close()` 仍先 `unlinkDeath` 再等 death**（`:224-239`）：只作诊断，不影响关闭语义；不要把它当确认路径，也别用它证明死亡顺序。
3. **`DEATH_POLL_INTERVAL_MILLIS` 的 KDoc 仍写“1 s 预算内至少 20 个判定点”**（`:892-893`），而预算已是 2 s；文档小漂移，建议改成 2 s 口径（每 50 ms 一次，至多 40 个判定点）。
4. **未设备复跑**：工作树与新增用例都未执行；`docs/known-defects.md:2577` 和契约 `:72` 已写 UNVERIFIED，口径一致。

`connectLocked()` 的首连/重建预算（`:539-550`、`:877-893`）用 `connections.get() == 0` 区分：首次**成功**连接前都是 15 s，成功后才用 10 s。这能覆盖 E-01d 的第二次 attach（7.5 s，因为第一次连接失败、`connections` 仍为 0）；“已成功连接过、之后系统再次重负载”仍无实测，10 s 是余量而不是证据。

---

## 5. Q4：冷启动 4.6–7.5 s 与 5 s 客户端超时

### 5.1 事实

- `bindService()` 是异步的：返回 `true` 只表示请求已交给 AMS；`onServiceConnected` 何时来没有保证，服务在创建期崩溃时**可能永远不来**（`ServiceConnection.java:30-45` 的 note）。
- 客户端旧 `CONNECT_TIMEOUT_MILLIS = 5_000` 是产品自设，不是平台 API。
- 实测（E-01d）：冷启动后立即跑，隔离进程 attach 4.578 s / 7.482 s；客户端 5 s 到点 unbind，AMS 以 “isolated not needed” 杀掉刚 attach 的进程（`step01d…:61-72`）。
- 沉降后（E-01e）：attach 0.878 s；5 s 足够。E-01b：0.728 s。
- **首连之外还有第二个冷启动成本**：首个 `execute` 里 `QuickJsVisualExecutionEngine` 才 `QuickJs.create(...)`（`core/data/.../QuickJsVisualExecutionEngine.kt:420`），要装载 `libquickjs.so`、初始化原生 runtime；E-01d 里 1500 ms 服务硬期限在冷启动负载下杀掉了 `live-after-stale-terminate` 请求（`step01d…:72`）。这不是连接超时，改 15 s 连接预算不能解决。

### 5.2 三种方案比较

| 方案 | 做法 | 优点 | 缺点 / 会消灭哪个失败 |
|---|---|---|---|
| A. 只延长连接预算 | 5 s → 15 s（工作树已做） | 直接覆盖实测 attach 上界；改动最小 | 不能解决首个 execute 在冷启动负载下触发 1500 ms 硬期限；15 s 到点仍永久 `unavailable`；调用方最坏等待 19.5 s |
| B. 同预算退避重试 | 5 s 失败 → unbind → 500 ms 后重试 | 不改超时常量 | **用 5 s 重试 7.5 s 的冷启动是失败循环**；会反复 fork/kill 隔离进程，放大进程数与日志；需要“加载中”状态才能不让用户看到失败 |
| C. 状态回调/加载态 + 预热 | `bind` 后进入 LOADING；首连 15 s；`onServiceConnected` 后先 warm-up/ready，再广告可用；首个用户请求不套 1500 ms | 把“加载慢”与“程序卡死”分开；不误熔断 programHash；UI 可显示加载 | 需要新状态/AIDL `warmUp`/`ready` 或“首次执行宽限”；改动比 A 大；仍要有超时兜底 |

### 5.3 推荐

**推荐 C（状态回调/加载态 + 预热），A 作为必要条件，B 只作为有界兜底；不要只做 A 或只做 B。**

具体数字与顺序：

1. **首连 15 s**（覆盖 7.5 s 的 2 倍），**重建 10 s**（工作树已改；E-01b 的 0.7 s 只覆盖沉降态，“已连接成功后再次重负载”没有证据）。连接期间状态是 `LOADING`，不是 `ENGINE_CLOSED`。
2. **首连超时不要立刻永久 `unavailable`**：先 `unbind`，进入可重试的 `LOADING`/`START_FAILED` 状态并暴露给调用方；不要在单次 `execute` 里再阻塞 15 s 自动重试（最坏会变成 30 s）。若产品要求自动重试，最多一次且总等待有界（建议 ≤20 s）；重试前必须 unbind 旧连接，避免多个 ServiceConnection 并存（`ActiveServices` 对 isolated 服务每次 start 都可能起新进程）。
3. **连接成功后先 warm-up/ready**：在服务端 JS executor 上创建并关闭一个最小 QuickJS 实例（或把 `QuickJs.create` 的原生装载前移到 `initialize` 的异步预热），完成后再把 `READY` 回给宿主；预热阶段**不 arm 1500 ms watchdog**（它只创建/关闭空实例、不执行用户程序，不属于契约 §4:68 的“JS 阶段”），但要有自己的有界预算（例如 10 s）。宿主只在 `READY` 后广告 JS 可用/接用户请求。这消灭的失败正是 E-01d 的“冷启动负载下 1500 ms 硬期限杀掉本应成功的请求”。
4. **首个用户 execute 仍按 1500/2500 ms 稳态预算**；若预热后仍超时，按真实硬期限处理（可熔断）。不要把“冷启动宽限”做成所有请求的常态放宽。
5. **冷启动超时不写 `programHash` 熔断**：熔断的语义是“这个程序卡死/崩溃”，不是“进程还没起来”。冷启动失败应在连接层重试，不消耗程序熔断预算；否则用户第一次打开就被永久 `PROGRAM_FUSED`。
6. **调用方最坏等待要在契约里写清**：15 s 首连 + 2.5 s 宿主阶段 + 2 s 死亡确认 ≈ 19.5 s（另加 Binder 开销），不是 1500/2500 ms 硬上界。20:08 快照的契约 `docs/current/visual-generation-contract.md:72` 已写 2000 ms / 19.5 s，与代码一致；KD-66 `docs/known-defects.md:2600` 也已写 2 s。

### 5.4 还没解决的缺口

工作树新增的 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`VisualJsIsolationInstrumentedTest.kt:247-261`）只把 `onServiceConnected` 推迟 7.6 s，验证的是**连接预算**；它没有模拟“首个 execute 时 CPU 被 system_server/GMS 抢占，QuickJS 原生装载超过 1500 ms”。因此：

- 该用例能证明 15 s 首连预算有效（若设备跑绿），**不能**证明 E-01d 的 1500 ms 冷启动问题已修复。
- 需要单独的 warm-up/ready 用例：连接后先等 `READY`，再执行；或在冷启动负载下跑一个真实首个 execute，断言不被 watchdog 杀掉。该用例属于 T1 的下一步，不在本轮研究执行范围。

---

## 6. 修复配方（T1 可执行；状态为 20:08 工作树快照）

### 6.1 必须做（P0；括号内为工作树状态）

1. **保留 death 注册到确认完成**：`terminate(gen)` → `unbind` → `awaitDeath` → 确认后才 `unlinkDeath`。`unbind` 可以早于等待；`unlinkDeath` 不可以。**（已做：`VisualJsClientEngine.kt:575-602`）**
2. **持住 proxy 强引用**：`link.binder` 在等待期间不能置空；否则 proxy 被 GC，`linkToDeath` 会自动解注册（`IBinder.java:328-330`）。**（已做：`:619` 读取本地引用，确认后才 `unlinkDeath`）**
3. **确认判据**：`link.death` 优先；`isBinderAlive` 每 50 ms 复核；false 时走 `markDead`。超时 fail-closed，不重建。**（已做：`:617-642`）**
4. **修 `awaitDeathConfirmation` 超时分支**：`:634-640` 的 `!binder.isBinderAlive` 为 true 时先 `markDead`，保证 `link.death`/`activeCall` 完成。**（已做）**
5. **修 KDoc 的 16 ms 误读**：改成“E-01b 中 attach 后 16 ms 即被 SIGKILL”，不要用它论证 death 延迟。**（已做：`:614-615`；但 `DEATH_POLL_INTERVAL_MILLIS` 的“1 s 预算”文案仍是旧口径，见 §4.4）**
6. **冷启动首连 15 s**（已做：`:877`）；连接超时进入可重试的 `LOADING`/`START_FAILED` 状态，不要一次失败就永久 `unavailable`。**（未做：`connectLocked` 超时仍在 `:552-557` 置 `unavailable=true`）**

### 6.2 建议做（P1；括号内为工作树状态）

1. **死亡确认预算 2 s**：与 nextvol 一致；无平台 SLA；降低负载下“迟到死亡被误判未确认”的概率。**（已做：`:890`；契约 `:72` 与 KD-66 `:2600` 已同步 2 s / 19.5 s）**
2. **重建预算 10 s**：覆盖“已连接成功、之后系统再次重负载”的重建；若坚持 5 s，在契约里写明“仅沉降态”，并加设备用例。**（已做：`:883`；仍无重负载重建实测）**
3. **warm-up/ready**：连接后先在 JS executor 上完成 QuickJS 原生装载，再广告可用；预热不 arm 1500 ms。消灭 E-01d 的首个 execute 冷启动超时。**（未做；这是当前最大剩余缺口）**
4. **迟到死亡处理**：未确认路径若解注册，明确记录“迟到 death 丢弃、永不据此重建”；若持有到 close，注意 binder 引用对进程状态的影响，并在 close 里释放。**（已做：`:589-596` 日志 + `:600` 解注册；策略是丢弃）**
5. **设备探针**：在 `DeathRecipient.binderDied`、`onServiceDisconnected`、服务 `killSelf` 前各打一条带 `SystemClock.uptimeMillis()` 的日志；跑一次 SIGKILL 场景，测 `kill → binderDied`、`kill → onServiceDisconnected`、`rebuild → onServiceConnected` 的分布。仓库目前**没有**这组数据；HEAD 的 1 s 与工作树的 2 s 都是工程选择。**（未做）**

### 6.3 测试与验证（本轮未执行）

已有/新增用例：

- `theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`VisualJsIsolationInstrumentedTest.kt:213-240`）：首次 abandon 确认 → 重建一次 → 新实例可直接 `Rendered` → 第二次 abandon 确认 → 不再 bind。
- `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`VisualJsIsolationInstrumentedTest.kt:247-261`）：把 attach 回调推迟 7.6 s，断言首次 execute 成功且只连接一次。
- 补充建议：`deathAfterAbandonTimeoutIsNeverUsedToRebuild`（模拟 2 s 内不死、之后才死，断言 `unavailable` 且不 bind）；`coldStartFirstExecuteIsWarmedUp`（冷负载下首个 execute 不被 1500 ms 杀掉）。

设备复跑命令（来自 E-01 索引 §8，**本轮未运行**）：

```bash
MSYS_NO_PATHCONV=1 C:/Android/Sdk/platform-tools/adb.exe -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
```

判据：

- **红→绿**：HEAD 版本在 E-01b/E-01c 红；工作树版本的同一用例预期绿，但**必须设备实跑**才能写“通过”。本轮没有跑，故只能写 `UNVERIFIED`。
- **负控**：把 `awaitDeathConfirmation` 改回“先 unlink 再等”，同一用例应重新红；不改断言、不放宽 2 s 死亡预算来“变绿”。
- **冷启动**：先 `adb reboot`，安装后立即跑，记录 attach 时间；再沉降 2 min 跑一次。两次都要区分“加载态”和“结构性红”。

### 6.4 牵动面清单（改一处先枚举）

| 落点 | 改动 | 牵动 | 是否需同步 |
|---|---|---|---|
| `VisualJsClientEngine.abandon()` | 保留注册/等待顺序 | `close()`（诊断等待）、`dispose()`（替换旧 link）、`handleDeath()`（幂等）、`rebuildConsumed` | 是：`close` 的注释/行为要说明“不等 death 也可关闭”；`dispose` 不承担确认 |
| `awaitDeathConfirmation()` | 新增 helper/轮询 | `activeCall.pending`（超时/取消路径）、`link.death` 完成语义 | 是：超时分支必须保证 pending 有界完成或明确由上层超时兜底 |
| 首连/重建预算 | 15 s/10 s（工作树已改） | `connectLocked`、`ensureConnected`、调用方最坏等待、契约 §4:72、E-01d 证据 | 是：契约数字同步；测试用 `DelayedAttachContext` |
| 冷启动 warm-up | 新 AIDL 方法/ready 状态 | `IVisualJsService.aidl`、`PROTOCOL_VERSION`、Gradle AIDL 门、服务 JS executor、宿主连接状态机 | 是：AIDL 只以 Gradle 任务验证；不改 AAR/stdlib |
| `programHash` 熔断 | 冷启动超时不写 fused | `fusedDigests`、`explicitUserRetry`、契约 §4:70 | 是：需明确“冷启动失败”与“程序超时”分类 |
| 测试 | 新增用例/夹具 | 仪器化类、`DelayedAttachContext`、设备轨命令 | 是：测试源码同样只经 Gradle/设备门验证 |

---

## 7. 未验证与缺口

1. **设备复跑 UNVERIFIED**：工作树修复与新增用例未在本轮执行；不能写“T1 已绿”。E-01b/E-01c 的红来自 HEAD 版本；工作树是否真绿需设备轨按 §6.3 跑。
2. **death 延迟未测**：没有 `binderDied`/`onServiceDisconnected` 的 ms 级配对日志；2 s 死亡窗口与 50 ms 轮询都是产品选择，不是平台数据。
3. **developer.android.com 不可达**：官方页面未在本机打开，Javadoc 逐字读自 AOSP 镜像源码；URL 列在 §8 供复核。
4. **API 23–27 未测**：官方标注 `onBindingDied`/`onNullBinding` 自 API 26 起（本轮官方页不可达，未逐字复核）；旧平台不会调用，但 Kotlin override 的运行时安全未在旧设备验证（R1 §9 已登记同项）。
5. **OEM/冻结/深度睡眠未测**：不同厂商的进程回收、冻结、AMS 重启策略可能改变 `onServiceDisconnected` 时机；AOSP 14 是本文的语义边界。
6. **`pingBinder()` 的阻塞性未量化**：本文只建议把它当可选诊断，没有在“远端活着但 Binder 线程卡死”的设备场景测过阻塞时长。
7. **工作树文档小漂移**：16 ms 文案已在 `:614-615` 修正；但 `DEATH_POLL_INTERVAL_MILLIS` 的 KDoc 仍写“1 s 预算内至少 20 个判定点”（`:892-893`），而预算已是 2 s；建议改成 2 s 口径。
8. **迟到死亡策略已选“丢弃并记录”**：工作树在超时后 `unlinkDeath` + 清 link，并在 `:589-596` 明确 “a late death is dropped and never used to rebuild”；fail-closed 下功能可接受。若产品以后要保留迟到死亡做诊断，需另行实现（持有 recipient/proxy 到 close），不要改变“未确认不重建”。

---

## 8. 来源

### 8.1 官方文档（canonical URL；本机不可达，逐字内容读自 AOSP 镜像）

1. `IBinder`：https://developer.android.com/reference/android/os/IBinder
2. `ServiceConnection`：https://developer.android.com/reference/android/content/ServiceConnection
3. AIDL：https://developer.android.com/develop/background-work/services/aidl
4. Bound services：https://developer.android.com/develop/background-work/services/bound-services

### 8.2 AOSP 源码（GitHub 镜像，钉 commit）

1. `IBinder.java`（`android-14.0.0_r1`）：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/os/IBinder.java#L78-L85、#L206-L224、#L306-L364
2. `ServiceConnection.java`：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/content/ServiceConnection.java#L25-L92
3. `LoadedApk.java`（`ServiceDispatcher`、`doForget`、`doConnected`、`doDeath`、`DeathMonitor`）：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/app/LoadedApk.java#L2092-L2254
4. `ContextImpl.java`（`unbindService` → `forgetServiceDispatcher`）：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/app/ContextImpl.java#L2163-L2178
5. `ActiveServices.java`（`bringDownServiceLocked`、`killServicesLocked`、`scheduleServiceRestartLocked`）：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/services/core/java/com/android/server/am/ActiveServices.java#L5539-L5568、#L6400-L6435、#L6545
6. `BinderProxy.java`（`isBinderAlive`、`sendDeathNotice`）：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/os/BinderProxy.java#L491-L496、#L699-L708
7. `BpBinder.cpp`（`isBinderAlive`、`transact` DEAD_OBJECT、`linkToDeath`/`unlinkToDeath`、`sendObituary`/`reportOneDeath`）：https://github.com/LineageOS/android_frameworks_native/blob/lineage-21.0/libs/binder/BpBinder.cpp#L309-L312、#L349-L397、#L402-L500、#L505-L553
8. `IPCThreadState.cpp`（`BR_DEAD_BINDER` → `sendObituary`）：https://github.com/LineageOS/android_frameworks_native/blob/lineage-21.0/libs/binder/IPCThreadState.cpp#L1474-L1480

### 8.3 社区实现（钉 commit，license 已核）

1. nextvol `IsolatedWorkerConnection.kt`（Apache-2.0）：https://github.com/Renakoni/nextvol/blob/c6e82c56f2cb8ded078662082c63da7c787211fe/app/src/main/kotlin/indi/renakoni/nextvol/sourceexecution/IsolatedWorkerConnection.kt#L43-L67
2. AndroidX Work `RemoteExecute.kt`（Apache-2.0）：https://github.com/androidx/androidx/blob/4ea31479e2bd1d24759aad359f60e80ff35beccb/work/work-multiprocess/src/main/java/androidx/work/multiprocess/RemoteExecute.kt#L58-L106
3. AndroidX PDF `PdfServiceConnectionImpl.kt`（Apache-2.0）：https://github.com/androidx/androidx/blob/4ea31479e2bd1d24759aad359f60e80ff35beccb/pdf/pdf-document-service/src/main/kotlin/androidx/pdf/service/connect/PdfServiceConnectionImpl.kt#L59-L117
4. Chromium `ChildProcessConnection.java`（BSD-3-Clause）：https://github.com/chromium/chromium/blob/ed3e89c78a09a1809239407e7cdaa66155279e7f/base/android/java/src/org/chromium/base/process_launcher/ChildProcessConnection.java#L580-L600、#L701-L714
5. license 核对：nextvol `repos/Renakoni/nextvol/license` → Apache-2.0；androidx → Apache-2.0；chromium → BSD-3-Clause（`gh api` 读取，2026-10-26）。

### 8.4 仓库内证据与代码

1. 设备证据索引：`docs/research/evidence/2026-10-26-batch-23/00-index.md:57`、`:85`
2. E-01b：`docs/research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:76-84`
3. E-01c：`docs/research/evidence/2026-10-26-batch-23/step01c-abandon-confirmed.md:70-84`
4. E-01d：`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:61-72`
5. E-01e：`docs/research/evidence/2026-10-26-batch-23/step01e-visualjs-isolation-postboot-settled.md:65-76`
6. 基线（HEAD 失败形态）：`docs/research/2026-10-26-batch-24-baseline.md:321`、`:336`
7. 工作树修复：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:575-642`、`:877-893`
8. 测试：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:213-261`
9. 服务：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:94-97`（onUnbind 自杀）、`:216-222`（terminate）、`:412-428`（watchdog）
10. 引擎首次原生装载：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:420`
11. 预算：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:123`（100 ms）、`:126`（1500 ms）、`:129`（2500 ms）
12. 契约：`docs/current/visual-generation-contract.md:68`（确认死亡后最多重建一次/不把 unbind 当同步终止）、`:70`（不自动重跑）、`:72`（终止层落地状态与最坏等待）

### 8.5 本地运行的检查（本轮）

| 检查 | 命令 | 结果 |
|---|---|---|
| 目标网络可达性 | `curl -sS -o /dev/null -w '%{http_code}' --max-time 12 <host>` | `api.github.com` 200；`developer.android.com`/`android.googlesource.com`/`cs.android.com`/`raw.githubusercontent.com` 连接失败（000/超时） |
| AOSP 源文件可取 | `gh api repos/aosp-mirror/platform_frameworks_base/contents/<path>?ref=android-14.0.0_r1 -H "Accept: application/vnd.github.raw"` | 成功；`LoadedApk.java` 98,310 B；`IBinder.java` 364 行；`ServiceConnection.java` 92 行 |
| 原生源文件可取 | `gh api repos/LineageOS/android_frameworks_native/contents/libs/binder/<file>?ref=lineage-21.0` | 成功 |
| 目标文件状态 | `git status --porcelain -- app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt ...VisualJsIsolationInstrumentedTest.kt` | 两个文件均为 `M`（研究期间并发修改；20:08 快照 `+93/−9` / `+113/−5`） |
| HEAD 失败形态复核 | `git show HEAD:app/src/main/kotlin/.../VisualJsClientEngine.kt \| sed -n '562,580p'` | `unlinkDeath(link)` 在 `withTimeoutOrNull(... link.death.await())` **之前**；与 baseline §5.1、E-01c 的定位一致 |
| HEAD 测试断言复核 | `git show HEAD:app/src/androidTest/.../VisualJsIsolationInstrumentedTest.kt \| grep -n "abandonForTest"` | `:210`（first abandon）、`:215`（second abandon），与 E-01b/E-01c 的失败行一致 |
| 设备/Gradle | 未运行 | `UNVERIFIED` |

---

## 9. 给 T1 的一句话交接

**核心顺序、2 s 死亡窗口、10 s 重建预算已落工作树（未提交、未设备验证）；接下来补 warm-up/ready 和连接超时的可重试 LOADING/START_FAILED，再按 E-01 命令设备复跑。复跑前，任何“已修复/已通过”都只能写 UNVERIFIED。**
