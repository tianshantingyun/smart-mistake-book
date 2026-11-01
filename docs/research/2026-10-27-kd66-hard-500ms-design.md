# KD-66 关门设计：watchdog 硬杀 → 宿主在 hard+500 ms 内返回的仪器化用例

批次 26 T1 设计（R1 轨，2026-10-27）。输入：计划 `.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` §1（T1）；契约 `docs/current/visual-generation-contract.md` §4；KD-66 原文 `docs/known-defects.md:2573-2617`；批次 26 基线 §6（`2026-10-27-batch-26-baseline.md`）。

**验证边界（如实）**：本轨只读——未跑 Gradle、未编译、未执行任何 JVM/仪器化/设备用例（禁 Gradle 纪律；编译与设备执行归门禁/设备轨）。文中行号为 2026-10-27 当前工作树实读值，源文件逐一打开核对过（非转引基线）。目标文件 `VisualJsIsolationInstrumentedTest.kt`（git 干净）与本设计不冲突：本文只设计、不改码。

---

## 1. 判据原文与语义

### 1.1 关闭门原文（一手实读）

`docs/known-defects.md:2617`（KD-66 Reopen condition，逐字）：

> 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）

契约 §4 对同门的复述（`docs/current/visual-generation-contract.md:72`）：关闭门值不变（"issue #240 形状在 hard+500 ms 内返回拒绝且后续请求可用"的设备证据）；现有 watchdog 用例以 ≤6 s 判死、弱于 hard+500 ms。

### 1.2 三个期限的语义（契约 §4:80 表，逐字）

> 单 JS 阶段协作期限 / 服务硬期限 / 宿主阶段期限 | 100 ms / 1500 ms / 2500 ms；**期限不是实时调度保证**

- **hard** = 服务硬期限 1500 ms：`SERVICE_HARD_DEADLINE_MILLIS = 1500L`（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`，本轮 grep 实证；同文件 `:129` = `HOST_PHASE_DEADLINE_MILLIS = 2500L`）。由 `VisualJsService` 的独立 watchdog 执行 `killProcess(myPid())`（`VisualJsService.kt:484-498`、`:154-170`）。
- **+500 ms** = 宿主从提交 #240 形状请求到**拿到有界拒绝**的额外余量。门是**调用方可见**的上界：`1500 + 500 = 2000 ms`。
- "返回拒绝" = 端口 `VisualExecutionEngine.execute` 返回 `VisualExecutionResult.Rejected`（不是 `Rendered`，不是抛异常，不是悬挂）；"后续请求可用" = 死亡后新请求返回 `Rendered`。
- 2000 ms **严格紧于**宿主自己的 2500 ms 阶段期限——这是设计的关键 discriminator：若死亡通知慢到只能靠宿主 2500 ms 超时兜底返回，用例必然红（见 §3.3）。
- 2000 ms **不是**实时调度保证：watchdog 由 `ScheduledThreadPoolExecutor` 调度、binder death 延迟无平台 SLA（`VisualJsClientEngine.kt:851`、`:1216-1218` 注释明说"2 s 是产品选择，不是平台 SLA"）。500 ms 余量正是为此而设（§4.5）。

---

## 2. 现状计时结构（一手实读，锚定基线 §6）

`VisualJsIsolationInstrumentedTest.kt`（1,001 行，12 个 `@Test`，git 干净；本轮全文实读）：

| 位置 | 内容 | 口径 |
|---|---|---|
| `:186-232` | `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`：harness 直连 AIDL；`:197` async 提交 `issue240Request()`；**:198 `withTimeoutOrNull(6_000L) { first.deathSignal().await() }`**；`:207-212` 断言 `outcome.status != RESULT_OK`；`:214-228` 新 harness 新 generation 断言 `RESULT_OK` | **6 s 死亡观察**（弱口径 #1）；KD-66 `:2613` 引用即此行 |
| `:456` | `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` 末尾 `withTimeoutOrNull(6_000L) { harness.deathSignal().await() }` | 同型 6 s 观察（弱口径 #2）；作用是清理挂死 worker，非门 |
| `:285-339` | `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`：**client 引擎面**，`:294` `engine.execute(issue240Request())` → `:294-299` 断言 `HARD_DEADLINE_EXCEEDED`，`:301-320` 重建后 `Rendered` | **无计时**——同样的 #240 流已设备绿，但拿不到"多快返回" |
| `:610-628` | `issue240Request()`：`function f() { Promise.resolve().then(f); while (true) {} } f();` | #240 注入形状（§4.2） |
| `:998-999` | harness `EXECUTION_WAIT_MILLIS = HOST_PHASE_DEADLINE_MILLIS + 8_000L`（=10.5 s） | 直连面的兜底观察窗 |
| `:881-890` / `:993` / `:996` | `awaitReady`（30 s 预算 / 100 ms 轮询） | warm 门（KD-76 ④ 分界） |

**为什么 6 s 弱**：6 s 只证明"L2 最终会杀"，对"调用方等多久"零约束——KD-66 Impact 的原话就是"调用方拿不到 1500 ms 硬上界"（`docs/known-defects.md:2575` 区段）。且它测在 **harness 直连面**（测试专用代码，见 §4.1），不是生产调用路径。

---

## 3. kill→return 全链路分解（500 ms 余量的预算表）

本轮把两端源码全文实读后得到的时间线（每个环节都有行号锚点）：

```
t0   调用方 engine.execute(#240)                     VisualJsClientEngine.kt:160
     └ 入队→消费者会话（队列为空时直跑）              VisualJsFrameQueue.kt:88-91
     └ AIDL execute → ACCEPTED                        VisualJsService.kt:227-284
        ready 门 :242 / 单在飞 CAS :256 / dup :267 / jsExecutor 排队 :277
     └ 宿主写信封（IO 协程）                          VisualJsClientEngine.kt:434
     └ 服务读信封+解码                                VisualJsService.kt:324-370
t_arm   armWatchdog（deadline 从真实 JS 工作起算）     VisualJsService.kt:377（注释 :374-376）
     └ runBlocking { engine.execute } 挂死（#240）    VisualJsService.kt:393
t_kill  watchdog 到点：CAS 令牌 → Log.w "hard deadline
        (1500 ms) for request <id>; killing the worker"
        → killSelf → Process.killProcess(myPid())     VisualJsService.kt:485-498, :494, :169
t_ret   宿主 binder death 通知 → Link.deathRecipient  VisualJsClientEngine.kt:1007-1009
        → markDead → handleDeath → 在飞 pending 完成
        （HARD_DEADLINE_EXCEEDED, "visual_js worker died: …"）
        → waiter.complete → 调用方 await 返回          VisualJsClientEngine.kt:1131-1142, :889-939
          （在飞完成点 :913-921；waiter :298；await :223）
```

**关键事实（设计据此成立）**：

1. **1500 ms 从 t_arm 起算**，t_arm 在信封读取+解码**之后**（服务明确不把传输计入期限，`VisualJsService.kt:320-323` 注释）。宿主计时从 t0 起算，因此实测 elapsed = (t0→t_arm 传输开销) + 1500 + (t_kill→t_ret 死亡传播开销)。**500 ms 余量覆盖两端开销**。
2. **在飞返回路径上没有轮询**：`handleDeath` 直接完成 `activeCall.pending`（`:913-921`）；2 s `DEATH_CONFIRM_TIMEOUT` + 50 ms `isBinderAlive` 轮询只属于 `abandon()` 死亡确认路径（`:853-878`），不在本用例的时间线上。所以 t_kill→t_ret 理论上只是 binder 通知 + 几次协程调度（毫秒级）。
3. **竞争的兜底返回路径**：若死亡通知丢失/过慢，宿主在 `withTimeout(HOST_PHASE_DEADLINE_MILLIS)`（`:269`）到点走 2500 ms 超时 abandon 路径返回，诊断为 `"host phase deadline (2500 ms) exceeded"`（`:279-283`）。**该路径返回时刻 ≥2500 ms > 2000 ms 门**——计时断言天然把它判红；且其诊断文本不含 "died"，归因断言（§5 A4）在计时之前就能把它单独点名。
4. **#240 硬杀不投递任何回调**：watchdog 直接 `killSelf`（`VisualJsService.kt:494`），不经 `fatal()` 的 `onFatal`（`:470-482` 只在 runRequest 错误路径走）；SIGKILL 后进程内不再执行任何代码。宿主侧唯一事实来源是 binder death / `onServiceDisconnected`（`VisualJsClientEngine.kt:1007-1009`、`:1080-1086`）。

---

## 4. 用例设计

### 4.1 测试面：client 引擎（L3 端口），不用 harness 直连

- 门的主体是**调用方**（KD-66 Impact："调用方拿不到 1500 ms 硬上界"）。生产调用面 = `VisualJsClientEngine.execute`（端口实现，`:160`）。harness 是**测试专用代码**（`VisualJsIsolationInstrumentedTest.kt:812-817` KDoc 自述"不复用 VisualJsClientEngine 是为了让 generation 校验……直接打 AIDL 表面"），其 DIED 返回来自测试自己的 `deathWatcher`（`:968-973`）——在 harness 上计时，测的是测试代码，不是任何生产行为。
- client 面的死亡→拒绝映射已被既有用例证明**能工作**（`:294-299` 设备绿）；新用例在其上加**时间门**，不是从零建路径。
- 结论：新用例构造 `VisualJsClientEngine`（类内既有 `client()` 夹具 `:108-109`），走 `connectForTest()` → 计时段 `execute` → 重建后 `execute`。

### 4.2 注入形状：原样复用 `issue240Request()`（自续 job + 自旋），不用普通死循环

- 注入是**源码级**的（不可信 JS，本来就是生产信任边界内的输入），**不需要任何生产测试 seam**——新增机制为零，消灭的失败唯一且明确（KD-66 关闭门）。
- 形状必须两件套：`Promise.resolve().then(f)` 自续 + `while (true) {}` 自旋。**为什么不能只写 `while(true)`**：上游 issue #240（本轮实访 `https://github.com/dokar3/quickjs-kt/issues/240`，open）的机制是——`interruptEvaluation()` **能**打断单个 job，但每个 job 在被打断前已排好了后继 job，pending-job 排水循环永不结束，且 `jsMutex` 全程被持、`close()` 同步卡死。普通死循环会被 L1 协作中断打断（契约 `:20`：探针 6 项检查含"死循环"），**不构成 #240 形状**——用它会让用例在 L1 就绿掉，测不到 L2/L3。
- "不吃取消"的机制保证：JS 在服务专用线程 `runBlocking` 内同步执行（`VisualJsService.kt:393`），协程取消触达不到 native 自旋；`close()` 卡 `jsMutex`（KD-66 Symptom，批次 21 线程栈证据 `docs/known-defects.md:2575` 区段）。该形状的设备真实性已有三批证据（批次 21 `timeout 60 → Terminated`；批次 23/24 watchdog 用例绿）。
- 复用既有 `issue240Request()` 逐字不改（`:610-628`）——它就是判据原文里"issue #240 形状"的仓库内具象。

### 4.3 计时起止点

- **t_submit**：`SystemClock.uptimeMillis()` 紧贴 `engine.execute(issue240Request())` 调用之前取值。前提已全部前置完成：`connectForTest()` 返回 true（其内部 `connectLocked` 的 `awaitWorkerReady` 通过才发布连接，`VisualJsClientEngine.kt:741`、`:772`——**连接成功蕴含 ready**，warm-up 装载耗时结构性排除在计时外），再加一次**预热 execute**（见下）。
- **t_return**：`execute` 返回后立即取值。`elapsed = t_return − t_submit`。
- 实测区间**包含**：队列派发、AIDL accept、信封写读、arm 前 1500 ms、kill、死亡传播、拒绝映射——这就是调用方可见延迟的全部，正是门所指的量。**不做"扣除式"计时**（比如减去测得的传输段）：扣除需要额外探针与跨钟对齐，引入新机制与新的 flaky 面，且门原文就是"返回"这个端到端事实。
- **预热 execute**（计时前一次 `pureGeometryRequest()`，断言 `Rendered`）：双职责——①负控，证明 worker 在注入挂死前功能正常（否则门红可能被误读为"L2 坏了"而实际是"worker 从没跑通"）；②压低 t0→t_arm 开销（JIT/AIDL/pipe 路径热）。与计时段**顺序**执行（await 完再开始计时），不占服务单在飞槽（`VisualJsService.kt:256`），不与 #240 帧并发。

### 4.4 时钟来源：`SystemClock.uptimeMillis()`，不注入时钟

- **单钟无跨钟偏差**：t_submit 与 t_return 都在宿主进程测试线程取值，同一时钟直接相减；服务端时刻只作为**诊断**（logcat 时间戳），不进断言，无需对齐。
- **候选对比**：
  - `System.nanoTime()`：单调、纳秒精度，测区间同样正确；
  - `SystemClock.uptimeMillis()`：单调、毫秒精度，深度睡眠不计——对本用例 2000 ms 量级精度绰绰有余；
  - **注入时钟（Clock 抽象进生产代码）：否决**。被测对象是真实时钟下生产栈（`ScheduledThreadPoolExecutor` watchdog + binder death + 协程调度器）的真实延迟；注入假钟等于用测试替换了被测量。且当前生产代码没有时钟 seam，为计时用例新增一个属于"指认不出所消灭失败的多余机制"（全局规则 §12.2）。
- **选择**：`SystemClock.uptimeMillis()`——与本文件既有夹具/引擎代码同惯用法（`VisualJsClientEngine.kt:785/:859/:1190`；测试 `:697/:883`），不引入第二种时钟习惯。`nanoTime` 可作等价替代（T1 实现轨若选它，语义不受影响）。

### 4.5 flaky 余量与重试策略

**余量结构（不追加任何容差）**：

| 段 | 名义 | 波动来源 | 实测锚点 |
|---|---|---|---|
| t0→t_arm | ~0–10 ms（预热后） | 协程调度、AIDL、pipe 写读 | 挂死前路径毫秒级（既有往返用例稳定绿） |
| t_arm→t_kill | 1500 ms | watchdog 调度延迟（CPU 竞争下可能晚触发） | `ScheduledThreadPoolExecutor` 温和负载下抖动毫秒级 |
| t_kill→t_ret | ~1–50 ms | binder death 传播、`handleDeath`、协程唤醒 | E-01b：attach→SIGKILL 16 ms 量级（KD-66 `:2607` 引）；`theClientRebuildsOnce...` 全链设备绿 |

500 ms 是 KD-66 门**自带**的余量，测试在它之上**不加**任何放宽系数（不许放宽断言）。若环境慢到吃穿 500 ms，用例红是门语义内的真红，按下面的归因树分类处理，而不是把常数改大。

**flaky 源枚举**：① watchdog 晚触发（模拟器 CPU 竞争）；② binder death 延迟无平台 SLA（`VisualJsClientEngine.kt:851` 自己声明了这一点）；③ 宿主 GC 停顿（百 ms 级、罕见）；④ 模拟器整机负载（本仓库设备基线里冷启动 attach 4.6–7.5 s 的同一类因素）。

**重试策略（三层）**：

1. **用例内不重试**。结构性原因：死亡后 #240 摘要已熔断（`VisualJsClientEngine.kt:915`、`:437-441`），重建预算已扣（`rebuildConsumed`，`:641`）——同一 engine 实例内第二次计时型 #240 尝试只会得到 `PROGRAM_FUSED`/`ENGINE_CLOSED`，"重试"必然意味着换新 engine = 新用例。语义原因：计时门的 flake 应当**显性化**（归因树），用例内静默重跑会把环境噪声洗成绿，削弱门证据。
2. **套件级两轮**：沿用本仓库既有设备轨惯例（批次 24 E-01/E-01b 双轮 `OK (6 tests)`，KD-66 `:2607`）——**关闭证据要求该用例在两轮独立运行中均绿**，单轮绿不作为关闭依据。
3. **红时归因决策树**（对应"失败先归因本批/他线/环境"纪律）——取 logcat（R4 规范设备轨已捕获全量 logcat），查 `VisualJsService` 的 watchdog 行 `hard deadline (1500 ms) for request <requestId>; killing the worker`（`VisualJsService.kt:489-493`）的时间戳：
   - **按时杀、迟返回**（kill 行 ≈ t_submit+1500+ε，而 elapsed > 2000）：死亡传播/handleDeath 慢 → **产品缺陷**，真门失败，KD-66 不得关闭；
   - **迟杀**（kill 行显著晚于 t_submit+1500）：watchdog 调度被环境拖慢 → **环境归因**，在沉降设备上复跑；复跑仍红且同形，升级为真红；
   - **无 kill 行**：watchdog 没打中（令牌 CAS 失败/generation 不符/根本没 arm）→ **产品缺陷**（L2 回归），与计时无关的更严重失败。
   - 归因信息必须进入设备轨证据记录（E-file），只写"重跑变绿"不算归因。

### 4.6 失败时的诊断输出

- **断言消息自带数字**：计时断言的失败消息携带 `elapsed`、门界（hard 值 + 500 + 合成值）、完整 `outcome`（reason + diagnostics）——沿用本文件断言消息携带状态与诊断的既有风格（如 `:296`、`:449`）。这样即使只看 instrumentation 失败摘要也能直接归因。
- **logcat 锚点**：设备轨按 R4 规范捕获全量 logcat；`VisualJsService` 的 `warm-up finished`（`:132`）与 `hard deadline … killing the worker`（`:489-493`）两行是时间轴锚点。
- **路径区分断言**（§5 A4）先于计时断言执行：若返回走的是 2500 ms 超时兜底，诊断 `"host phase deadline (2500 ms) exceeded"`（`VisualJsClientEngine.kt:281-283`）不含 "died"，归因断言单独红并给出明确消息——不会与"死亡传播慢"混成同一类红。

---

## 5. 伪码与断言清单

### 5.1 伪码（按类内既有风格；命名/常量落点归 T1 实现轨最终定）

```kotlin
// 类 companion 内新增（相对形式：hard 值改动自动跟随，门语义 = hard+500 不写死 1500）：
// /** KD-66 关闭门（docs/known-defects.md:2617）：#240 形状须在 hard+500 ms 内
//  *  从调用方返回拒绝。500 ms 是门自带余量，测试不追加容差。 */
// const val HARD_PLUS_500_GATE_MILLIS: Long =
//     VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS + 500L

@Test
fun aHungWorkerReleasesTheCallerWithinHardPlus500ms() = runBlocking {
    val engine = client()
    // connectForTest()==true 蕴含 ready（connectLocked 内 awaitWorkerReady 通过才发布连接）：
    // 1500 ms 从真实 JS 工作起算，warm-up 装载不进计时（KD-76 ④ 分界）。
    assertTrue("initial connect failed", engine.connectForTest())

    // 预热（非计时）：负控 worker 功能正常 + 压低 submit→arm 开销；顺序执行不占在飞槽。
    val warm = engine.execute(pureGeometryRequest())
    assertTrue("the worker must render before the hang is injected: $warm",
        warm is VisualExecutionResult.Rendered)

    // ── 计时段：t_submit → t_return，端到端，不扣除 ──
    val submittedAt = SystemClock.uptimeMillis()
    val outcome = engine.execute(issue240Request())
    val returnedAt = SystemClock.uptimeMillis()
    val elapsed = returnedAt - submittedAt

    // A1+A2+A3：必须是死亡路径的硬期限拒绝（不是成功、不是其他理由）。
    assertTrue("expected a rejection, got $outcome", outcome is VisualExecutionResult.Rejected)
    assertEquals(
        "the #240 shape must map to the hard-deadline rejection, got ${outcome.diagnostics}",
        VisualExecutionRejection.HARD_DEADLINE_EXCEEDED,
        (outcome as VisualExecutionResult.Rejected).reason,
    )
    // A4：归因——拒绝必须来自死亡路径。2500 ms 超时兜底的诊断不含 "died"，
    //     该断言把两种失败形状在计时之前分开。
    assertTrue(
        "the rejection must come from the worker death (not the host 2500 ms timeout): " +
            outcome.diagnostics,
        outcome.diagnostics.contains("died"),
    )
    // A5：关闭门本体。
    assertTrue(
        "the caller was released in $elapsed ms; the KD-66 gate is " +
            "$HARD_PLUS_500_GATE_MILLIS ms (hard " +
            "${VisualExecutionBudget.SERVICE_HARD_DEADLINE_MILLIS} + 500). outcome=$outcome",
        elapsed <= HARD_PLUS_500_GATE_MILLIS,
    )

    // A6+A7：关闭门第二句——后续请求可用（死亡后重建，新 program 可执行）。
    assertTrue(
        "the client did not rebuild after the hard-deadline death",
        engine.awaitConnectedForTest(10_000L),   // 与 RECONNECT_TIMEOUT_MILLIS 同量级（既有 :303 同值）
    )
    assertEquals(
        "exactly one rebuild is expected after the death",
        2, engine.connectionCountForTest(),
    )
    val fresh = engine.execute(pureGeometryRequest())   // 不同 program 摘要，无熔断交互
    assertTrue(
        "a fresh program must render on the rebuilt worker (BUSY would mean the hung " +
            "engine or its in-flight slot leaked): $fresh",
        fresh is VisualExecutionResult.Rendered,
    )
}
```

### 5.2 断言清单（每条指认它钉住的失败）

| # | 断言 | 钉住的失败 / 依据 |
|---|---|---|
| A1 | `outcome is Rejected` | #240 形状不得产出成功结果（继承现 `:207-212` 面，换到 client 面） |
| A2 | `reason == HARD_DEADLINE_EXCEEDED` | 端口语义映射正确（对齐既有 `:294-299` 的同型断言） |
| A3 | （包含在 A1/A2 的消息里）非 `Rendered`、非 `BUSY`/`PROGRAM_FUSED` 等其他理由 | 拒绝理由不被静默换类 |
| A4 | `diagnostics.contains("died")` | 区分死亡路径（`:919` "visual_js worker died: …"）与宿主 2500 ms 超时兜底（`:281-283` 不含 "died"）；归因先于计时 |
| A5 | `elapsed ≤ SERVICE_HARD_DEADLINE_MILLIS + 500` | **KD-66 关闭门本体**（`:2617`）；2000 ms 紧于宿主 2500 ms 内部预算，超时兜底路径不可能蒙混 |
| A6 | `awaitConnectedForTest(10s)` 为真 | 死亡后重建发生（非预期死亡路径 `:938` → `rebuildAfterDeath`） |
| A7 | `connectionCountForTest() == 2` 且新 program `Rendered` | "后续请求可用"（门第二句）；`Rendered` 传递性证明旧进程真死——否则 #240 线程仍持单在飞槽，新请求必得 `STATUS_BUSY`（`VisualJsService.kt:256`） |
| — | `@After closeEverything()` 既有清理 | 用例结束不泄漏 binding/进程（类既有 `:97-103`，无需新增） |

### 5.3 本用例**不**断言的事项（防过断言）

- **不断言** kill 的精确时刻（1500 ms ± ε）：契约 `:80` 明说"期限不是实时调度保证"；kill 时刻只进 logcat 诊断。
- **不断言** daemon 线程泄漏消失：#240 泄漏是已知登记形态（KD-66 `:2593`"观察用例绿=已知形态，不是可终止"），本门只管调用方上界。
- **不断言**挂死期间并发调用行为（BUSY 背压已由 `:427-458` 覆盖，不重复）。

---

## 6. 与既有用例的关系：**共存**（裁定）

| 用例 | 面 | 现口径 | 新角色 |
|---|---|---|---|
| `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（`:186-232`） | harness 直连 | 6 s 死亡观察 | **保留**，角色降格为"L2 kill 发生 + 新 generation 无 BUSY"的服务级回归观察；**非关闭门证据**（KD-66 `:2613` 已如此定性） |
| `aSecondConcurrentRequest…` 末尾 6 s（`:456`） | harness 直连 | 6 s 清理观察 | 保留不动（清理挂死 worker 的观察窗，与门无关） |
| `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（`:285-339`） | client 引擎 | 无计时 | **保留**，状态机回归（重建记账、`PROGRAM_FUSED` 熔断、二次死亡→`ENGINE_CLOSED` 端局）；与本用例共享前三拍（死亡→`HARD_DEADLINE_EXCEEDED`→重建→可用），但覆盖面不重叠到可删除 |
| **新用例**（§5.1） | client 引擎 | **hard+500 ms 计时** | KD-66 关闭门专属证据 |

**为什么共存而不是替代**：

1. 门原文把 6 s 用例定性为"弱于本条件、不能作为关闭证据"（`:2617`），没有说删除；它另有两个本用例不覆盖的断言（harness 面的新 harness 新 generation 直连、`delay(1_800)` 后健康 worker 不被迟到 watchdog 误杀 `:146-155`）。
2. 删除既有断言需要独立正当性，不在本门范围内（纪律：不许放宽/删除断言）。
3. 两面各有价值：harness 面隔离了 client 状态机、纯看服务；client 面是生产调用路径。删任何一个都缩小回归网。
4. **后续可选收尾**（不在本批）：新用例拿到 ≥2 批次设备绿史后，未来批次可论证收敛/紧化 6 s 观察（届时单独论证，本设计不预支）。

**批次连带修订点（提示 T2/T5）**：类内 `@Test` 由 12 → 13；T2 的隔离套件判据文本"12+2 全绿"与 T5 台账/契约同步须相应更新为 13+2（Race 2 例另计）。**KD-66 关闭还需要记录回链**（关闭判据 = 设备用例绿 + known-defects 回链，计划 §2:21）——本设计文档本身不构成关闭。

---

## 7. 验证边界与未决项

**本轨未做（UNVERIFIED，不冒充已验证）**：未编译（禁 Gradle；伪码的 API 名全部对照当前源码实读——`connectForTest` `:1153`、`awaitConnectedForTest` `:1189`、`connectionCountForTest` `:1166`、`VisualExecutionBudget` 导入已在测试文件 `:17`、`SystemClock` 导入 `:14`，但最终编译归门禁轨）；未设备执行；未测 500 ms 余量在真实设备负载分布下的实测裕度（§4.5 表的锚点是历史批次证据，非本轮实测）。

**未决/待实现轨定夺**：用例名与常量名（本文名为建议）；A7 的 `connectionCount==2` 是否保留为硬断言（确定性分析见 §5.2，T1 可按实现复核）；两轮设备运行由 T2 设备轨统一安排。

**背景观察（单一来源，待验证）**：上游 issue #240 页面（本轮实访）显示 2026-10-25 有 commit "Stop draining pending jobs once an evaluation is interrupted or closed" 引用该 issue——若日后升级钉版，按测试内既有注释（`:199-201`）应把本门**反转为"协作中断在 100 ms 内返回且进程不死"**，而不是放宽 2000 ms。AAR 钉版 1.0.15/commit `a081755` 不变（批次纪律），该观察不改变本设计。

---

## 来源

- `docs/known-defects.md:2573-2617`（KD-66 原文；`:2613` 批次 25 设备轨回填；`:2617` Reopen condition——一手实读）
- `docs/current/visual-generation-contract.md` §4（`:64` 队列语义、`:68` watchdog 语义、`:72` L2/L3 落地状态与关闭门复述、`:80` 100/1500/2500 ms 预算表——一手实读）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:106/:126/:129`（预算常量——grep 实证）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt`（全文实读：ready 门 `:242-251`、arm 点 `:377`、watchdog `:484-498`、killSelf `:154-170`、单在飞 `:256`）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（全文实读：`execute` `:160-224`、2500 ms 期限 `:267-293`、在飞死亡完成点 `:913-921`、deathRecipient `:1007-1009`、markDead `:1131-1142`、常量 `:1199-1234`、测试 seam `:1151-1197`）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt`（全文实读：直跑分支 `:88-91`）
- `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（全文实读：6 s 两处 `:198`/`:456`、#240 请求 `:610-628`、harness `:818-1001`）
- 批次 26 基线 §6（`docs/research/2026-10-27-batch-26-baseline.md:285-336`，行号锚定交叉核对一致）
- 上游 quickjs-kt issue #240：https://github.com/dokar3/quickjs-kt/issues/240 （本轮 WebFetch 实访：open；自续 job 使排水循环永不结束、`close()` 卡 `jsMutex`；页面显示 2026-10-25 有引用该 issue 的修复 commit——单一来源观察，待验证）
- 计划 `.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` §1/§2（T1 任务与 KD-66 关闭判据）
