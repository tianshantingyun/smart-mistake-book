# R2：KD-76 计时校准——`connectStageTimestampsForTest` seam 设计 + `READY_TIMEOUT=10 s` 校准判据 + 29.5 s worst-case 链测量方案（批次 39，驱动 T4/T5）

- 研究线：R2（批次 39 计时校准；只给字段/语义/判据/测量方案与参考评分，**不落实现、不跑 Gradle、不跑设备**——共享纪律"门禁由脚本串行执行"）
- 上游衔接：[批次 39 计划](../../.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md) §1 任务 5/6（KD-76 计时 seam 落 `VisualJsClientEngine` 非热区文件 + 校准/29.5 s 链设备轨）；[KD-76](../known-defects.md)（`known-defects.md:2865` 起，open）；KD-66 关闭门先例（`VisualJsIsolationInstrumentedTest.kt:479`）；R1' §2.3-4（`2026-10-27-visualjs-warmup-queue.md:111`）；E-04 装载成本（`evidence/2026-10-27-batch-27/step04-warmup-cost.md`）
- 批次 38 台账已登记本判据设计文档缺口（`known-defects.md:2909`："判据设计文档截至本块登记时点未落盘"）——本文补该缺口。
- 本轮实读基线：`git status --porcelain` 对 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/` 返回空（该目录**不在任何他线热区**，与本 seam 落点声明一致）；`grep connectStageTimestampsForTest` 全仓库零命中（seam 尚不存在，任务 5 是"落"，本研究给设计）。

---

## 0. 结论摘要（pick）

1. **seam 形状**：`connectStageTimestampsForTest` = 按 generation 记账的 **5 个时刻戳 / 4 个阶段段**（bind 请求→连接发布、连接发布→ready、ready→execute ACCEPTED、ACCEPTED→结果首字节），全部 `@Volatile` 单写者字段挂在 `Link` 上，写入点共 5 处、全在 `VisualJsClientEngine.kt` 一个文件内，零生产行为改动、零新增同步原语；回滚 = 删 1 类 + 5 写点 + 1 访问器。
2. **READY_TIMEOUT=10 s 校准判据**：主口径 = 经 seam 的客户端可感 ready 段（bind 请求→ready）在**真实冷启动轮**（reboot 后即跑、不沉降、n≥10）的 p50/p95/max；维持条件 = p95 ≤ 10 s × 1/2（≥2× 余量；现状 E-04 服务端口径余量 ≈14×、churn 尾 ≈5.7×，`known-defects.md:2897`——判据只是把"无数据压力"显式化，不是放宽线）。超条件才动 `READY_TIMEOUT_MILLIS` 单常量，loading 语义/1500 ms/2500 ms 红线零改动。
3. **29.5 s 链测量**：**分段实测相加**，不做单例端到端故障链（四段满额需同时注入"慢 attach + 慢 warm-up + 挂死 + 死亡不确认"，注入相互干扰且死亡不确认段是 fail-closed 语义、不可靠复现）。S1/S2 段（15 s/10 s 预算）经 seam 冷启动轮实测；S3 段（2.5 s）由既有 #240 挂死夹具 + KD-66 hard+500 ms 已绿门佐证；S4 段（2 s）由 `abandonForTest` 既有路径观测。报告 = 实测分段之和 vs 名义 29.5 s 的余量。
4. **anti-flaky 全套沿仓库内 KD-66 先例**（三轮设备绿的最强先例）：归因断言先于计时断言、单钟 `uptimeMillis` 相减、无扣除式计时、相对门形式、门后可用性断言、n≥10 分位数报告（macrobenchmark 方法论同型）。
5. **参考评分**：仓库内 KD-66 用例（最高，直接复用）> macrobenchmark 方法论（中高，只借统计口径）> Chromium ChildProcessService（中，R1' 已引的语义先例）> SystemClock 官方文档（中，时钟依据）> awaitility（低，不引入——与仓库既有 poll-loop 惯用法重复，且 instrumented 面兼容性摩擦单一来源待验证）。

---

## 1. 现状证据：四阶段在代码里的真实落点（本轮实读）

### 1.1 阶段→代码锚点

| 阶段 | 代码事实 | 锚点（本轮实读） | 对应预算 |
|---|---|---|---|
| ① bind（bindService→连接发布） | `context.bindService(intent, candidate.connection, BIND_AUTO_CREATE)`（`VisualJsClientEngine.kt:650`）→ AMS fork/attach → `onServiceConnected`（`:1013`）内做 protocolVersion/initialize/workerUid 握手（`:1047-1064`）→ `markConnected` 同锁发布 CONNECTED（`:1108-1119`，`connections.incrementAndGet` `:1117` 与 `connected.complete(true)` `:1118` 同临界区） | attach+握手整段 | 首连 15 s / 重建 10 s（`:1227`/`:1233`） |
| ② warm-up（连接发布→ready） | 服务端 `onCreate` 起在 `jsExecutor` 做一次性装载（`VisualJsService.kt:119-121`），完成置单调 `ready`（`:80`、`:152-153` `warm-up finished` 日志）；宿主 `awaitWorkerReady` 每 100 ms 轮询 `isReady()`（`VisualJsClientEngine.kt:784-808`） | 客户端只能测**投影段**：服务端 warm-up 本体从 onCreate 已开始，早于宿主握手 | ready 10 s（`:1251`，间隔 `:1254`） |
| ③ ready（ready→execute ACCEPTED） | `awaitWorkerReady` 见 `isReady()` 首真返回（`:796`）→ link 复用路径零复查（ready 单调，`:776-782` KDoc）→ 首个请求 `service.execute(...)` 返回 ACCEPTED（`runSession` `:359`） | 已 ready 连接复用时①②为零、③从复用点起算 | — |
| ④ 首字节（ACCEPTED→结果首字节） | `service.execute` ACCEPTED（`:359`）→ 信封写协程（`:435`）→ worker 回调 `onResult`（`:457`）→ `readResultPayload` 首字节 `input.read(buffer)`（`:551`）；当前实现 payload **全量读完后**才 `pending.complete`（`:479-484`），`pending.await()`（`:437`）在 2500 ms `withTimeout`（`:270`）内 | 首字节打点必须在 `readResultPayload` 读循环内（见 §2.3 风险注） | 宿主阶段 2.5 s（core/domain `VisualExecutionEngine.kt:129`） |

补充事实：服务硬期限 1500 ms（`VisualExecutionEngine.kt:126`）、协作期限 100 ms（`:123`）、死亡确认 2 s（`VisualJsClientEngine.kt:1240`）。

### 1.2 既有测试 seam（新 seam 的挂法与命名沿此）

`VisualJsClientEngine.kt:1152-1218` 已有 9 个 internal 测试 seam：`connectForTest`（`:1154`）、`abandonForTest`（`:1161`）、`connectionCountForTest`（`:1167`）、`unavailableForTest`（`:1174`）、`readyTimeoutOverrideForTest`（`:1183`）、`fuseDigestForTest`（`:1201`）、`workerUidForTest`（`:1205`）、`generationForTest`（`:1207`）、`awaitConnectedForTest`（`:1210`）。每个 seam 的 KDoc 都显式写"它消灭的失败"——新 seam 沿此体例（§2.3 逐字段给出）。

### 1.3 缺口的一手出处（为什么需要这个 seam）

- **E-04 明示缺口**：`evidence/2026-10-27-batch-27/step04-warmup-cost.md:34`——现有口径"AMS `Start proc` → 同 pid `warm-up finished`"**"不含：宿主 bind/握手排队（未测，无客户端侧计时 seam）"**。`known-defects.md:2897` 同句复述（"宿主 bind→ready 客户端可感延迟未测（无计时 seam）"）。这是本 seam 消灭的第一个具体失败。
- **29.5 s 链只有名义值**：契约 `visual-generation-contract.md:72` 给出"最坏约 29.5 s（首连 15 s + warm-up ready 10 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销；ready 预算只在每次新进程 bind 后等待一次，已 ready 的连接复用不重付）"；KD-76 台账三处一致登记"29.5 s worst-case 链 UNVERIFIED"（`known-defects.md:2899` 剩余解锁条件 (b)、`:2865` 判据面、`:2909` 批次 38 滚动）。**链是算术拼出来的，四段没有任何一段有客户端侧实测数字**——本 seam + §4 方案消灭"名义值当实测值"的风险。
- **KD-76 ③ 残余的分段归因缺口**：冷启动轮若首请求超预算，现有证据只能给端到端总时长，无法回答"慢在 attach、握手排队、ready 轮询还是首字节"——批次 26 E-03（`step03-kd76-criteria.md`）的判定卡在"29.5 s 链与低内存 UNVERIFIED"，分段计时是其前置之一。

---

## 2. seam 设计（任务 5；T4 照此落码）

### 2.1 字段与语义

```kotlin
// VisualJsClientEngine.kt 内（internal，仪器化用例可见；同模块 androidTest 可见——与既有 9 个 seam 同面）
internal class ConnectStageTimestamps private constructor() {
    /** 阶段①起点：bindService 调用前一刻（connectLocked :650 之前）。 */
    @Volatile var bindRequestedAtMillis: Long = 0L
    /** 阶段①终点=②起点：markConnected 同锁发布 CONNECTED（:1108-1119；与 connectionCount 记账同刻）。 */
    @Volatile var bindConnectedAtMillis: Long = 0L
    /** 阶段②终点=③起点：isReady() 首次返回 true（awaitWorkerReady :796）。 */
    @Volatile var readyAtMillis: Long = 0L
    /** 阶段③终点=④起点：首个请求 service.execute 返回 STATUS_ACCEPTED（runSession :359）。 */
    @Volatile var executeAcceptedAtMillis: Long = 0L
    /** 阶段④终点：结果 payload 首字节 read 成功（readResultPayload :551）。 */
    @Volatile var firstByteAtMillis: Long = 0L

    /** 0 = 未发生（防"零值被当时间戳"歧义）；任一字段非 0 即 started。 */
    fun started(): Boolean = bindRequestedAtMillis != 0L
}
```

**四阶段段定义（首尾相接的完整时间轴）**：

| 段 | 起→止 | 语义与边界裁定 |
|---|---|---|
| S-bind | `bindRequestedAt` → `bindConnectedAt` | bindService→AMS fork/attach→回调线程握手→CONNECTED 发布。**终点取 `markConnected` 锁内**（与 `:1117-1118` 记账同刻），不用 `connected.await()` 返回时刻——后者晚一个调度周期，会把调度抖动算进 S-bind |
| S-warmup | `bindConnectedAt` → `readyAt` | 客户端可感的 warm-up 投影段。**它不是服务端 warm-up 本体**（本体从 onCreate 起、早于握手）：与 E-04 服务端口径（`Start proc`→`warm-up finished`）相减即得 bind/握手排队段——这正是 E-04 `:34` 声明缺失的量 |
| S-ready-gap | `readyAt` → `executeAcceptedAt` | ready 到首个请求被 ACCEPTED（含队列排队：`VisualJsFrameQueue` 1 在飞 + ≤2 待绘；冷启动轮单请求下应≈0，非零即队列背压参与——诊断信息） |
| S-firstbyte | `executeAcceptedAt` → `firstByteAt` | ACCEPTED→结果首字节，⊂ 宿主 2500 ms 阶段期限。**不含 payload 全量读取/JSON 解码**（全量读完在 `pending.complete` `:479-484` 之后，另属 2500 ms 期限面） |

**两处语义裁定（防误读）**：
1. **ready 单调 + per-bind 记账**：ready 是单调量（`VisualJsClientEngine.kt:776-782`），S-warmup 只在新进程 bind 后被测一次；已 ready 连接复用不重付（契约 `:72` 原口径）。因此 timestamps **挂在 `Link` 上、按 generation 天然隔离**——与"per-bind 状态全放每次 bind 新建的 Link"（R1 高危项 2，`VisualJsClientEngine.kt:85-87` KDoc）同构，旧实例时间戳不会污染新实例。
2. **"首字节"只对首个请求记账**：`executeAcceptedAt`/`firstByteAt` 写点在 `runSession`/`readResultPayload`，会随每个请求覆盖——按"最后一次请求"语义覆盖写即可（时间戳类的诊断面，不做历史累积；历史累积=多余机制，§12.2 指认不出消费方）。

### 2.2 写点枚举（§12.1 影响面闭合——共 5 处，全在本文件内）

| # | 写点 | 位置 | 线程 | 说明 |
|---|---|---|---|---|
| 1 | `bindRequestedAt` | `connectLocked` `:645-650` 之间（`Link` 构造后、`bindService` 前） | IO 协程（connectMutex 内） | bind 失败分支（`:655-661`）不影响：时间戳照写，`bindConnectedAt` 留 0 即"未连接"事实 |
| 2 | `bindConnectedAt` | `Link.markConnected` `:1108-1119` **锁内**（`state = CONNECTED` 同一 `stateLock`） | binder 主线程 | 与 `connections.incrementAndGet`（`:1117`）同刻，消灭"发布与打点间窗口"歧义 |
| 3 | `readyAt` | `awaitWorkerReady` `:796` `if (readyNow) return true` 处 | IO 协程 | 超时/死亡/closed 出口（`:788`/`:800-807`）不写——`readyAt==0` 即"未 ready"，与 loading 语义如实对应 |
| 4 | `executeAcceptedAt` | `runSession` `:395-396` `STATUS_ACCEPTED` 分支 | IO 协程 | 非 ACCEPTED 出口不写（BUSY/STALE/SHUTTING_DOWN 不属于首请求成功路径） |
| 5 | `firstByteAt` | `readResultPayload` `:551` 首个 `read` 成功后（幂等：仅首次非 0） | IO 协程 | **风险注**：这是生产热读循环；代价 = 一次 volatile 写 + 一次判 0，语义零改动。备选"在 `onResult` 打点"被否——那是回调到达时刻，不是首字节时刻（fd 读取在 `scope.launch` 内，`:479`） |

多线程写者（binder 主线程 + IO 协程）、每字段单写者、单调性由阶段顺序保证 → `@Volatile Long` 足够，**零新增锁/CAS**（新增必须指认消灭的失败：锁在这里没有它消灭的失败，不加）。

### 2.3 访问器与逐字段"消灭的失败"

```kotlin
/** 测试 seam：取当前 link（或指定 generation）的四阶段时间戳；link 为空/未 started 返回 null。 */
internal fun connectStageTimestampsForTest(generation: Long? = null): ConnectStageTimestamps? {
    val current = synchronized(linkLock) { link } ?: return null
    if (generation != null && current.generation != generation) return null
    return current.timestamps.takeIf { it.started() }
}
```

| 字段/段 | 消灭的具体失败（一手出处） |
|---|---|
| S-bind | E-04 口径缺口：bind/握手排队"未测，无客户端侧计时 seam"（`step04-warmup-cost.md:34`）——首连 15 s 预算的"attach 4.6–7.5 s（E-01d）之外还剩多少余量"首次可测 |
| S-warmup | READY_TIMEOUT=10 s 校准只握有服务端口径（`Start proc`→`warm-up finished`），客户端可感口径（bind→ready，含握手排队）无数据（`known-defects.md:2897`）——校准判据（§3）的**主口径**数据源 |
| S-ready-gap | 冷启动轮若首请求超时，无法区分"还没 ready"与"ready 后队列背压"——批次 26 E-03 判定只能给端到端结论的归因缺口（`step03-kd76-criteria.md:130`） |
| S-firstbyte | 29.5 s 链的 S3 段（宿主阶段 2.5 s）与 S4 段之前的部分只在名义值上存在；首字节段实测才能把"2500 ms 期限内有界"从读码推演变成设备事实 |
| 整体 | `known-defects.md:2909`：批次 38 已登记"READY_TIMEOUT+29.5 s 校准判据设计文档未落盘"——本文 + T4 落码后该缺口收口 |

### 2.4 回滚与禁改面

- 回滚：删 `ConnectStageTimestamps` 类 + 5 个写点行 + 1 个访问器 = 完整回退；生产路径（bind/握手/ready/execute/超时/loading 语义）零行为改动，`readyTimeoutOverrideForTest` 及其余 9 个 seam 零改动。
- 禁改面核对（共享纪律"改一处先枚举牵动面"）：AIDL 两文件、`VisualJsService`、`VisualJsFrameQueue`、`VisualJsProtocol`、预算常量、`unavailable`/`lastBindFailedOnLoading`/重建记账——全部**不在本 seam 触动面内**；`Link` 新增唯一成员 `val timestamps`（构造时随 Link 新建，满足 per-bind 隔离）。

---

## 3. `READY_TIMEOUT=10 s` 校准判据（任务 6 前半）

### 3.1 已有数据（不出数，转录既有证据）

| 口径 | 数据 | 出处 |
|---|---|---|
| 服务端装载成本（`Start proc`→`warm-up finished`） | 控制组 n=10：p50 686 / p95 725 / max 738 ms；套件 churn 组 n=16 中位 675.5 ms、churn 尾 1259–1746 ms；26/26 全部 ready | E-04 `step04-warmup-cost.md:10`、`:123` |
| 客户端可感口径（bind→ready） | **无数据**（无计时 seam——本 seam 补） | `step04-warmup-cost.md:34`、`known-defects.md:2897` |
| 现预算余量 | `READY_TIMEOUT=10 s` 对实测 p95 ≈14×、对 churn 尾 ≈5.7× | `known-defects.md:2897` |
| 冷启动 attach（S-bind 的下界参照） | 4.6–7.5 s（E-01d） | `VisualJsClientEngine.kt:1224-1227` KDoc |

### 3.2 判据（T5 设备轨执行时逐条对照）

**主口径**：经 seam 的 S-bind + S-warmup 之和（bind 请求→ready），真实冷启动轮（`adb reboot` 后不沉降即跑，对齐批次 26 E-03 冷启动形状），n≥10，报 p50/p95/max。

1. **维持判据**：主口径 p95 ≤ `READY_TIMEOUT_MILLIS`（10 s）× 1/2，即预算对实测 p95 保有 ≥2× 余量。现状服务端口径余量 ≈14×（`known-defects.md:2897`），判据只是把"校准无数据压力"从隐含判断变成显式门槛；**≥2× 是本研究的建议值（沿 E-04/2897 表述"无数据压力"的量化下界），最终数值由主会话裁定**。
2. **调参判据**：p95 > 10 s × 1/2，或冷启动轮出现任何一次"ready 段吃满 10 s"→ 调 `READY_TIMEOUT_MILLIS`（单常量 `:1251`），并回写契约 `:72` 口径表与 R1' `:111`（"待验证起点"→"已校准"）。上下界：下界 = 实测 p95 × 2；上界 = 不得高于首连 15 s 预算（ready 是 attach 之外的**第二段独立预算**，语义上不得吃掉 attach 段的余量——R1' `:111`"与首连 15 s/重建 10 s 同级"）。
3. **红线（校准不触碰）**：loading 语义零改动（超时不锁存 `unavailable`、不扣重建预算、下次 execute 重新 bind，`VisualJsClientEngine.kt:758-769`）；1500 ms 服务硬期限、2500 ms 宿主阶段期限、死亡确认 2 s、重建"最多一次"——全部不在校准参数面内（KD-76 Fix direction 红线"不得放宽 1500 ms 程序期限"）。

### 3.3 注入形状（与既有夹具的分工，零新夹具）

| 形状 | 用途 | 既有落点 |
|---|---|---|
| **真实冷启动**（reboot 后即跑、不沉降） | 校准**数字**的唯一主口径——注入形状测不出真实装载分布 | 批次 26 E-03 冷启动轮同法（`step03-kd76-criteria.md` §2） |
| `DelayedAttachContext`（推迟 attach 回调 16 s > 15 s 预算） | loading 分支**行为**断言（不锁存/如实 FAILED），不产校准数字 | `VisualJsIsolationInstrumentedTest.kt:903`、`:883`（既有，复用） |
| `NotReadyWorkerContext`（本地 fake binder 永不 ready + `readyTimeoutOverrideForTest=250`） | ready 超时分支**行为**断言；真实 warm-up 总会完成、该分支只有假 binder 才能确定性触达（seam KDoc `:1177-1183`） | `:965`、`:748-751`（既有，复用） |

**裁定**：校准**不加**新的注入形状——"慢 warm-up"注入（如在 fake binder 上让 `isReady()` 第 N 次才真）能测行为分支但不能测装载分布，对校准数字无贡献；既有两夹具已覆盖全部行为分支。这同时是 §12.2 门：新增"慢 ready 注入夹具"指认不出它消灭的行为失败（既有 NotReadyWorkerContext 已钉超时分支），不做。

### 3.4 anti-flaky（沿 KD-66 先例 + macrobenchmark 方法论）

1. **归因先于计时**：任何计时断言前先断言拒绝/结果的归因形状（KD-66 A4 先例，`2026-10-27-kd66-hard-500ms-design.md:139`、测试 `:507-513`）——loading 超时（诊断含 loading/not-ready）、结构失败、成功三种形状分开点名，不与"测量值大"混红。
2. **单钟相减、无注入时钟**：全部段用 `SystemClock.uptimeMillis()` 同进程单钟相减；**不注入 Clock 抽象**（KD-66 §4.4 裁定原文 `:104-109`："注入假钟等于用测试替换了被测量……属于指认不出所消灭失败的多余机制"）。时钟语义：`uptimeMillis` 单调、毫秒精度、**不计深度睡眠**（官方 SystemClock 文档；KD-66 `:107` 同口径）——仪器化运行期屏幕常亮，深度睡眠面不存在；若设备轨发现设备入睡导致时间戳漂移，换 `elapsedRealtime()`（monotonic 含 deep sleep）并换**同一个钟**重跑全部段，不做混钟。
3. **无扣除式计时**：段与段相加（S-bind+S-warmup 等），不做"总时长减去某段"的反推（KD-66 `:99`"扣除需要额外探针与跨钟对齐，引入新机制与新的 flaky 面"）。
4. **n≥10 + 分位数报告，不做单点门**：校准是测量不是 gate——p50/p95/max 落盘 evidence（R4 规范），单次离群不红；这与 macrobenchmark 的"iterations→min/median/max、看 median 不看 max"方法论一致（§5 参考表 #3）。
5. **logcat 交叉归因**：seam 数字与 E-04 服务端口径（`Start proc`/`warm-up finished`/`hard deadline` kill 行）同轮留 logcat，按 pid 配对互验；两端数字的差 = bind/握手排队段，超一个数量级的偏差按环境问题归因（失败先归因本批/他线/环境）。
6. **相对形式**：若校准产出任何行为门（如"首请求必须在 ready 段 × k 内 Rendered"），门常量用相对形式（`READY_TIMEOUT_MILLIS * k`），沿 `HARD_PLUS_500_GATE_MILLIS = SERVICE_HARD_DEADLINE_MILLIS + 500L`（`VisualJsIsolationInstrumentedTest.kt:890-891`）先例——常量改动自动跟随，不追加容差。

---

## 4. 29.5 s worst-case 链测量方案（任务 6 后半）

### 4.1 链的名义构成（契约 `:72` 原口径）

```
S1 首连（bind→连接发布）      预算 15 000 ms（VisualJsClientEngine.kt:1227）
S2 warm-up ready（→ready）    预算 10 000 ms（:1251）
S3 宿主阶段（→结果/放弃）      预算  2 500 ms（core/domain VisualExecutionEngine.kt:129）
S4 死亡确认（abandon→确认）    预算  2 000 ms（:1240）
Binder/调度开销                 非预算项（契约"另加 Binder 开销"）
名义合计 29 500 ms
```

注意契约原文的两条限定：ready 预算只在每次新进程 bind 后等待一次（已 ready 复用不重付）；该 29.5 s 是**宿主单次失败路径的上界，不是用户等待承诺**——loading 超时可无限次重试（KD-76 ⑤），用户可见累积等待归 P-04 生命周期限次、**不入本链**（否则把"上界"误读成"典型值"）。

### 4.2 方案：分段实测相加（不做单例端到端故障链）

**否决单例方案的理由**：要一次性触达 29.5 s 需同时满足"attach 拖满 15 s + warm-up 拖满 10 s + 首请求拖满 2.5 s + 死亡确认拖满 2 s"——前两段满额意味着进程仍在 loading（夹具注入），后两段意味着进程已死或挂死（#240 注入），四段互相约束（attach 满额超时后**不会再有同 link 的 ready 等待**，`connectLocked :690-714` 直接返回 loading）；拼出来的"单例"是夹具接力，不是任何真实执行形状，测出来的 29.5 s 没有归因价值。**分段各自贴真实形状实测，相加与名义值对照。**

| 段 | 测法 | 数据源 | 既有支撑 |
|---|---|---|---|
| S1+S2 | 冷启动轮 seam 实测（bind 请求→ready），n≥10 报 p50/p95/max；与 15 s/10 s 预算对照出余量 | `connectStageTimestampsForTest` | E-04 服务端口径可作 S2 下界参照（p95 725 ms） |
| S3 | #240 挂死注入端到端（KD-66 A5 门已钉 hard+500 ≤ 2000 ms 三轮设备绿）；2500 ms 段本身取名义值，以 hard+500 实测上界佐证"调用方有界返回" | 既有 KD-66 用例（`VisualJsIsolationInstrumentedTest.kt:479-541`），零新码 | E-02 三轮绿（`known-defects.md:2619`） |
| S4 | `abandonForTest()` 既有路径：terminate→死亡确认的墙钟（死亡事实 + `isBinderAlive` 组合判据 `:854-879`）；毫秒值如需精确可读 S 系时间戳或在 abandon 路径加一个观测点（同 seam 体例），不改收尾语义 | 既有 abandon 用例（`:293-319`） | DEATH_CONFIRM 2 s 是产品选择非平台 SLA（`:850-853` KDoc） |
| 汇总 | 报告 = Σ实测段上界 vs 名义 29 500 ms 的余量；Binder 开销按段内 AIDL 调用次数 × 单次实测（`isReady` 轮询、`execute`、`terminate` 各一笔）估计 | evidence 按 R4 落盘 | — |

### 4.3 执行边界（如实）

- 冷启动轮与 S1/S2 数字归 T5 设备轨；本研究未跑任何设备（共享纪律）。
- 口径边界沿 E-04 §6：x86_64/API 34 模拟器口径；arm64/真机/低内存 UNVERIFIED（`known-defects.md:2899` (c)：arm64 运行时本机原理性不可采证，归发布矩阵批挂起）。
- 29.5 s 链实测**不单独构成 KD-76 关闭**：KD-76 关闭另一半（R3 广告门 6/6 文本升级 + 加载态 UI/文案面）归主会话（`known-defects.md:2899` (d)）。

---

## 5. GitHub / 官方参考评分（仪器化计时）

评分维度：可复用性（对四阶段 seam/校准的直接贡献）、兼容性（与仓库现有测试栈）、维护度（更新/响应）、license。GitHub 优先纪律：核 license 与维护度并引来源。

| # | 参考 | license / 维护度（gh api 实查） | 可复用点 | 评分 | 裁定 |
|---|---|---|---|---|---|
| 1 | **仓库内 KD-66 关闭门用例** `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（`VisualJsIsolationInstrumentedTest.kt:479-541`）+ 设计文档（`2026-10-27-kd66-hard-500ms-design.md` §4/§5） | 仓库内；设备三轮绿（E-01/E-02 ×2，`known-defects.md:2619`） | 四拍结构全套：归因先于计时、t_submit/t_return 单钟、无扣除、相对门、门后可用性断言（A6/A7）、"预热 execute 双职责" | **最高** | T4/T5 主判据模板，本 seam 的计时用例照此写 |
| 2 | **androidx.benchmark.macro 方法论**（androidx monorepo，Apache-2.0，pushed 2026-10-31，gh api 实查；[metrics 文档](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)、[overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)） | 极活跃 | StartupTimingMetric 输出 min/median/max、"评估改进看 median 不看 min/max"；COLD/WARM/HOT 启动模式；iterations/warmupIterations | 中高（**方法论**） | 采纳其统计口径（§3.4-4）：冷启动轮 n≥10、分位数报告。**不引入模块**——macrobenchmark 面向 activity 启动（`startActivityAndWait`），本链是 in-app service bind 无 activity 面；引入=面不匹配的额外机制 |
| 3 | **Chromium `ChildProcessService`** warm-up/ready 握手（BSD-3-Clause；R1' 已引 pin `ed3e89c7`，`2026-10-27-visualjs-warmup-queue.md:55-64`） | 上游稳定 | "连接成功 ≠ 可用"、ready 握手必须一次性终结（成功或失败都通知，调用方不得悬挂）——四阶段语义（bind/warm-up/ready/可用）的上游同型 | 中（**语义先例**） | 已被 R1' 采纳为服务端 warm-up/ready 形态来源；本 seam 不新增引用面，仅在文档里保留溯源 |
| 4 | **Android `SystemClock` 官方文档**（[API reference](https://developer.android.com/reference/android/os/SystemClock)；AOSP `SystemClock.cpp`） | 官方 | `elapsedRealtime` monotonic 含 deep sleep（一般区间计时推荐）；`uptimeMillis` 不计深度睡眠；同进程单钟相减 | 中（**时钟依据**） | 沿 KD-66 §4.4（`:104-109`）裁定 `uptimeMillis`（与仓库既有夹具/引擎同惯用法）；入睡面出现才换 `elapsedRealtime` 并全段同钟 |
| 5 | **awaitility/awaitility**（[repo](https://github.com/awaitility/awaitility)，Apache-2.0，4035★，末 commit 2026-04-17，tag `awaitility-parent-3.1.2`，gh api 实查；[Kotlin 扩展](https://github.com/awaitility/awaitility/blob/master/kotlin/src/test/kotlin/org/awaitility/kotlin/KotlinTest.kt)） | 活跃（JVM 面） | poll-until 断言 DSL（`await().atMost().until`） | **低** | **不引入**。理由：① 仓库已有同等惯用法且设备绿——`awaitConnectedForTest`（`VisualJsClientEngine.kt:1210-1218`）、harness `awaitReady`（`VisualJsIsolationInstrumentedTest.kt:1099-1108`）都是"有界轮询到事实"，awaitility 指认不出它额外消灭的失败（全局规则 §12.2）；② Android instrumented 面有 hamcrest/`MethodHandles` classpath 摩擦的社区报告（[SO awaitility 标签](https://stackoverflow.com/questions/tagged/awaitility)）——**单一来源，待验证**，不据此单独否决，与 ① 合并否决 |

**一致性核验**：#2/#5 维护度与 license 为本轮 `gh api` 实查输出（awaitility：`{"archived":false,"license":"Apache-2.0","pushed_at":"2026-04-17", "stars":4035}`；androidx：`{"license":"Apache-2.0","pushed_at":"2026-10-31"}`）；awaitility `releases/latest` 返回 404（该库未标记 latest release），tag 面实查取 `awaitility-parent-3.1.2`。官方 SystemClock 文档直连一次超时、换 searchd 通道取回的是页面导航噪声、最终经 WebSearch 摘要确认时钟语义——与仓库内 KD-66 `:107` 一致，**双源采纳**。

---

## 6. 判据汇总表（T4/T5 验收对照）

| 项 | 判据 | 执行轨 | 本研究状态 |
|---|---|---|---|
| seam 落码 | `connectStageTimestampsForTest` + `ConnectStageTimestamps` 按 §2 落 `VisualJsClientEngine.kt`；5 写点、零生产行为改动、既有用例零放宽 | T4 | 设计已交付（本文 §2）；未落码 |
| seam 用例 | 冷启动轮断言：`started()` 真、`bindConnectedAt ≥ bindRequestedAt`、`readyAt ≥ bindConnectedAt`、与 `connectionCountForTest()==1` 同轮一致；归因断言先行 | T4 | 判据已交付；未落码未跑 |
| READY_TIMEOUT 校准 | §3.2 三条（维持/调参/红线）；n≥10 冷启动轮 p50/p95/max 落 evidence | T5 | 判据已交付；**未出数** |
| 29.5 s 链 | §4.2 分段实测表；Σ实测 vs 名义 29 500 ms；单例故障链**不做**（§4.2 否决理由入档） | T5 | 方案已交付；未出数 |
| 红线 | 1500 ms/2500 ms/loading 语义/重建记账/广告开关零改动；KD-76 保持 open | 全程 | 设计即遵守 |
| 参考评分 | §5 表；awaitility 不引入、macrobenchmark 只借口径 | — | 已评分（gh api 实查） |

---

## 7. 未验证项（如实）

1. **未跑 Gradle / 未跑设备**：本研究全部判据为设计面，任何"绿"都未在此轮发生——共享纪律"不运行 Gradle（门禁由脚本串行执行）"。
2. **READY_TIMEOUT 校准数字、29.5 s 各段实测值**：不存在（T5 出数前 UNVERIFIED）；§3.1 表格数字全部转录自 E-04 与 known-defects 台账（出处逐行标注），非本轮实跑。
3. **awaitility 在 Android instrumented 面的兼容性摩擦**：单一来源（SO 标签页搜索综合），按 11.3 标**待验证**；不作为单独否决依据，与"仓库已有同等惯用法"合并构成不引入裁定。
4. **官方 SystemClock 文档直连**：一次超时 + searchd 取回导航噪声，最终采纳 WebSearch 摘要 + 仓库内 KD-66 设计文档 `:107` 双源；官方原文句子未逐字核到，时钟语义（uptimeMillis 不计 deep sleep、elapsedRealtime 含且 monotonic）按双源一致采纳。
5. **arm64/真机/低内存口径**：沿 E-04 §6 与 `known-defects.md:2899` (c) UNVERIFIED，不在本批解锁。
6. **seam 落码后的既有用例回归**：新 seam 理论上零生产行为改动，但"设备绿绑定源快照"纪律要求落码后重跑隔离套件——归 T4/T5 执行面。

## 8. 来源

**仓库内（本轮实读）**：
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（:48-99 类 KDoc、:623-774 connectLocked、:784-810 awaitWorkerReady、:1013-1119 ServiceConnection/markConnected、:330-449 runSession、:535-565 readResultPayload、:1152-1218 测试 seam、:1220-1254 预算常量）
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt`（:56-80、:119-159 warm-up/ready）
- `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt`（:106-152 预算单源）
- `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`（:479-541 KD-66 门、:748-784 ready 超时、:863-892 夹具常量、:903/:965 夹具类、:1036-1219 harness）
- `docs/current/visual-generation-contract.md:72`（29.5 s 链定义、终止层落地状态）
- `docs/known-defects.md:2865-2909`（KD-76 本体 + 批次 27/28/38 进度块）
- `docs/research/2026-10-27-visualjs-warmup-queue.md:111`（READY_TIMEOUT=10 s 待验证起点）、`:276`
- `docs/research/2026-10-27-kd66-hard-500ms-design.md`（:97-123 计时设计、:139 归因先于计时、:216-222 A1-A7）
- `docs/research/evidence/2026-10-27-batch-27/step04-warmup-cost.md`（:10 verdict、:34 口径缺口、:123 PASS）
- `docs/research/evidence/2026-10-27-batch-26/step03-kd76-criteria.md`（:130 缺口、§5/§7 判据对照）
- `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md`（:20-21 任务 5/6）

**外部（本轮 gh api / WebSearch 实查）**：
- https://github.com/awaitility/awaitility （gh api：Apache-2.0 / 4035★ / pushed 2026-04-17 / tag awaitility-parent-3.1.2 / non-archived）
- https://github.com/androidx/androidx （gh api：Apache-2.0 / pushed 2026-10-31）
- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics 、https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- https://developer.android.com/reference/android/os/SystemClock 、https://android.googlesource.com/platform/frameworks/native/+/jb-dev/libs/utils/SystemClock.cpp
- https://awaitility.java.org 、https://stackoverflow.com/questions/tagged/awaitility （待验证来源）
- https://stackoverflow.com/a/11092021 （System.nanoTime/uptimeMillis deep sleep 社区佐证）
