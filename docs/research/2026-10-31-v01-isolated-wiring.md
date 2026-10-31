# V-01 真隔离引擎迁移装配研究（2026-10-31，批次 37）

> 研究轨（R1）产出，驱动 T2 落码。方法：只读实读仓内源码 + 联网参考（gh CLI / WebSearch / 直取 androidx 源码），零 Gradle、零设备命令、零代码改动。
> 所有行号为本会话实读；命令清单见附录。凡本会话未能核实的一律标注 **UNVERIFIED** 或"待验证"。
> 对照计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md` §1.1；基线：`docs/research/2026-10-31-batch-37-baseline.md` §3。

## 0. 结论摘要

1. **装配形态有仓内设备绿先例，T2 不需要新机制**：app androidTest 已有 `VisualJsIsolationInstrumentedTest`（批次 22–36 设备 6/6）证明 `VisualJsClientEngine(context)` 直构 + 每测试一引擎 + `@After` 统一 close 的装配在仪器化环境成立。V-01 app 版照此装配，把引擎注入 `FigureGenerationCoordinationFactory.create` 的既有第 4/5 参缝即可（`DefaultFigureGenerationCoordinator.kt:859-877`，参数默认 null，生产装配仍传三参 `SmartMistakeBookApplication.kt:364-368`）。
2. **度量器复用缺陷的根治形态 = 断言按键取（键 = 引擎回传的 `executed.contentIds`），而非"每次渲染独立度量器"**；且迁移到 app 后该缺陷**大半自然消失**：`VisualJsClientEngine` 构造不接收度量器（`VisualJsClientEngine.kt:100`），真实度量发生在 `:visual_js` 服务进程内的 `AndroidVisualContentMeasurer`（`VisualContentMeasuring.kt:53`，服务进程单线程、无累积状态）——core/data 套件里的 `DeviceTextMeasurer` 记录夹具本身就是依赖方向逼出来的降级物，迁移后删除。
3. **不采用 `ServiceTestRule`**：androidx 源码实证其默认连接超时 5 s（`DEFAULT_TIMEOUT = 5L`），与设备实测冷启动 attach 4.6–7.5 s（E-01d）直接相撞；且它只做"bind→测→unbind"，没有 ready 门/死亡重建语义——用它会测的是 harness 而不是引擎。
4. **零派发见证不变量**四断言位在 app 版**全部可保持**（审计表/账本/端口全部经 app 的 `implementation(project(":core:data"))`/`:core:database` classpath 可达，既有 app androidTest 套件同规则已设备绿）；建议顺带把 `seedParentOperation` 补进 C/D 共用骨架，填补基线登记的"见证③在 C/D 无观测对象"缺口。
5. **一个需要主会话/实现轨裁定的决策点**：core/data 原套件按计划"不删"，若原样保留，其 2/4 夹具红在每次 core/data 设备轨上继续红——要么在原文件同步按方案 B 修（3 行级改动），要么在设备轨清单里显式降级登记。本文给出两条路的代价，裁定不代做。

## 1. 一手来源清单（本会话实读）

| 文件 | 读法 | 关键行 |
|---|---|---|
| 计划 `.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md` | 全文 | §0/§1.1/§3 |
| `docs/research/2026-10-31-batch-37-baseline.md` | 全文 | §3.1（夹具缺陷①②现树形态）、§3.2（引擎构造/绑定行号） |
| `core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt` | 全文 940 行 | 见 §2/§3 引用 |
| `app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt` | 全文 1219 行 | 见 §2 引用 |
| `app/src/main/.../visualjs/VisualJsClientEngine.kt` | 全文 1283 行 | 见 §2.2 |
| `app/src/main/.../visualjs/VisualContentMeasuring.kt` | 全文 508 行 | :53、:69、:490-508 |
| `app/src/main/.../visualjs/VisualJsHandshakeRaceInstrumentedTest.kt` | 头 120 行 | 装配/清理先例 |
| `core/data/src/main/.../visual/DefaultFigureGenerationCoordinator.kt` | :232/:271-360/:854-877 | JS 分支、engine.execute、工厂缝 |
| `core/data/src/main/.../visual/VisualFrameInputs.kt` | 全文 | :19 `fun interface`（app 可见） |
| `core/data/src/main/.../visual/QuickJsVisualExecutionEngine.kt` | 定位 | :168-169 构造收度量器、:395 measure 调用点、:317 close |
| `core/domain/src/main/.../VisualExecutionEngine.kt` | 常量区 | :126 `SERVICE_HARD_DEADLINE_MILLIS=1500`、:129 `HOST_PHASE_DEADLINE_MILLIS=2500` |
| `app/src/main/kotlin/.../SmartMistakeBookApplication.kt` | :350-380 | :364-368 生产三参装配 |
| `app/build.gradle.kts` | 依赖区 | :171-182（core:data/database 等 implementation）、:200-205（androidTest） |
| `app/src/main/AndroidManifest.xml` | 定位 | :69-72 `isolatedProcess="true"` + `:visual_js` |

## 2. V-01 app 版套件装配设计

### 2.1 隔离客户端装配先例（事实，设备绿）

`VisualJsIsolationInstrumentedTest` 的装配骨架（批次 24 设备 6/6、批次 36 增至 15 例，先例文件 KDoc :48-98）：

- 引擎直构：`VisualJsClientEngine(context)`，context 取 `ApplicationProvider.getApplicationContext()`（:102-103、:119-120）；构造参数必须是 application context（`VisualJsClientEngine.kt` KDoc :93-94：引擎生命周期长于任何 Activity）。
- 引擎逐个登记进 `CopyOnWriteArrayList<VisualJsClientEngine>`，`@After closeEverything()` 统一 `close()`（:105-113）——close 是终态闩（`closed.compareAndSet`，`VisualJsClientEngine.kt:302-322`），**一个引擎实例不能跨测试复用**，这是"每测试一个引擎"的硬理由。
- 连接前置：`engine.connectForTest()`（生产 `ensureConnected` 的测试面，`VisualJsClientEngine.kt:1154`），失败即断言红并带"initial connect failed"归因（先例 :296）。这把冷启动 attach 从被测结果里隔离出来：后续 `execute` 复用已 CONNECTED 的 link。
- 观测 seam：`connectionCountForTest()`/`generationForTest()`/`workerUidForTest()`/`awaitConnectedForTest(timeout)`（:1154-1218），必要时断言进程隔离事实（`Process.myUid() != workerUid`）。

### 2.2 VisualJsClientEngine 绑定生命周期（事实表）

| 阶段 | 行为 | 依据 |
|---|---|---|
| bind | 惰性：首次 `execute`（或 `connectForTest`）时 `Intent(context, VisualJsService::class.java)` + `Context.BIND_AUTO_CREATE` | `VisualJsClientEngine.kt:648-650` |
| 服务进程 | `android:process=":visual_js"` + `android:isolatedProcess="true"`（无自身权限、不挂数据目录）；握手校验 `workerUid != Process.myUid()`，同 UID 即 fail-closed | `AndroidManifest.xml:69-72`；引擎 :1057-1064 |
| 首连预算 | 15 s（覆盖实测冷启动 attach 4.6–7.5 s E-01d + 握手） | :1223-1227 |
| 重建预算 | 10 s（原生库已在页缓存） | :1229-1233 |
| ready 门 | 握手成功后轮询 `isReady()`，独立 10 s 预算、100 ms 间隔；warm-up 未完 = loading，不锁存、不扣重建预算 | :739-773、:1245-1254 |
| 宿主阶段期限 | 2500 ms（软期限；**bind/ready 时间不计入**——`ensureConnected` 在 `withTimeout(HOST_PHASE)` 之外） | core/domain `VisualExecutionEngine.kt:129`；引擎 :268-272 |
| 服务硬期限 | 1500 ms（watchdog SIGKILL，隔离 UID 才允许 killProcess） | core/domain `:126`；隔离先例 :90-97 |
| 死亡重建 | 确认死亡后最多一次；loading 超时豁免重建预算（KD-76 ⑤） | :890-955、:600-610 |
| close() | 闩死队列→排队帧回 ENGINE_CLOSED；`terminate(generation)` oneway→`unbindService`→`markDead(unexpected=false)` 完成两个等待点；不再重建 | :302-322 |
| 进程回收 | unbind 后无绑定 → 服务 onDestroy → killSelf（引擎注释 :691-694"解绑会触发服务 onDestroy→killSelf，不留孤儿进程"） | :691-694 |

**给 T2 的直接含义**：仪器化环境里不需要也不应该手动管理 bind/unbind/ready——引擎自管；测试只负责 (a) 预热 `connectForTest()`、(b) `@After` close、(c) 断言用 seam 读状态。

### 2.3 设计：装配形状（推荐）

```kotlin
@RunWith(AndroidJUnit4::class)
class V01VisualChainInstrumentedTest {   // 落 app/src/androidTest/.../visualjs/，方法名无空格（D8 SimpleName，文件 KDoc :110 既有约束）

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private val engines = CopyOnWriteArrayList<VisualJsClientEngine>()
    // databases / databaseNames / createdFiles 沿现套件 :119-121 原样迁

    @After
    fun tearDown() {
        engines.forEach { runCatching { it.close() } }   // 先引擎（隔离进程回收）
        engines.clear()
        databases.forEach { runCatching { it.close() } } // 后库与临时文件（现套件 :123-133 原样）
        /* deleteDatabase / delete files 同现套件 */
    }

    private fun isolatedEngine(): VisualJsClientEngine =
        VisualJsClientEngine(context).also { engines.add(it) }

    private suspend fun preconnectedEngine(): VisualJsClientEngine =
        isolatedEngine().also { engine ->
            assertTrue("initial connect failed", engine.connectForTest())  // 预热：冷启动预算在测试体内显式消费
        }
```

要点与理由：

1. **每测试方法一个引擎**（与隔离先例同形）：close 终态闩决定不能复用；跨测试共享会把 `fusedDigests`、`rebuildConsumed`、`connectionCountForTest` 状态泄漏进下一条用例，破坏断言位①②④的独立性。
2. **显式预热而非首渲染内隐式连**：`runChainToRestoredReplay` 里第一次 `engine.execute` 发生在 `coordinator.generate` 内部，隐式连会把"冷启动 5–8 s"混进生成腿的失败面；预热后失败归因只剩链本身。代价是每测试多一次显式断言，与 KD-66 关闭门用例的预热手法同型（隔离先例 :483-489）。
3. **注入缝不动生产**：`FigureGenerationCoordinationFactory.create(..., visualEngine = engine, visualFrameInputs = VisualFrameInputs { _, _ -> frozenFrame() })`（现套件 :147-148/:418-419 同形）；生产装配仍三参（`SmartMistakeBookApplication.kt:364-368`），负控用例 `theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows`（现套件 :237-266）原样迁移，继续钉"生产 null → UNAVAILABLE"。
4. **classpath 可达性已由既有事实背书**：app 对 core:data/core:database 是 `implementation`（`app/build.gradle.kts:173-174`），而既有 app androidTest 套件（隔离套件）import 了 `core.domain.*`/`core.model.*` 并设备绿——同一可见性规则覆盖 V-01 需要的 `FigureGenerationCoordinationFactory`、`VisualFrameInputs`、`VisualDrawOpsPainter`、`VisualSnapshotRestore`、`StudyDatabasePort`、`REPLAY_TEXT_PAINT_FLAGS`。
5. **同进程单测不算隔离验证**的纪律沿袭（隔离先例 :86-88）：本套件起真实 `:visual_js` 进程，`workerUid != myUid` 是结构事实，不需要在 V-01 里重复断言（隔离套件已钉），但若要在迁移后保留一道轻量防退化闸，可在用例 A 加一行 `assertTrue(engine.workerUidForTest() != Process.myUid())`——可选，非判据。

### 2.4 超时表（迁移后套件的时间预算）

| 段 | 常量/预算 | 出处 | 对套件的影响 |
|---|---|---|---|
| 首连 attach | 15 s 上界（实测 4.6–7.5 s） | 引擎 :1223-1227 | `connectForTest()` 最坏 15 s；实测均值 ~5 s |
| ready warm-up | 10 s（与首连串行） | :1245-1251 | 预热最坏 ~25 s |
| 宿主阶段期限 | 2500 ms/帧 | core/domain :129 | 每次渲染的最坏等待 |
| 服务硬期限 | 1500 ms/程序 | core/domain :126 | 正常渲染远低于此 |
| 队列 | 1 在飞 + ≤2 待绘 | 引擎 :104-109 | 本套件串行渲染，不触背压 |
| 套件最坏 | 4 例 × (预热 25 s + 2 帧 × 2.5 s) ≈ 2 min | — | 设备轨预期 1–2 min 量级；与隔离套件（15 例含 watchdog 6 s 观察）同量级 |

不需要测试侧超时常量：所有超时都已在生产语义里（这正是"真引擎"迁移的意义）；测试侧唯一要避免的是在 `runBlocking` 外再套一层不确定的等待。

### 2.5 清理与进程回收

- `@After` 顺序：**先 close 引擎**（terminate + unbind + 完成等待点，:302-322），**再关库**（`StudyDatabasePort.close()`），**再删库文件与临时 PDF**（现套件 :123-133 手法原样）。顺序错了不会崩，但先关库会让在飞审计写（best-effort，`DefaultFigureGenerationCoordinator.kt:838-845` 吞异常）落在已关闭连接上产生噪音。
- 孤儿进程：close 的 unbind 是回收的充分路径（BIND_AUTO_CREATE + 无绑定 → onDestroy → killSelf，:691-694）；即使某用例在渲染中途失败抛出，`@After` 的 close 也按同一路径回收——这是引擎 close 语义自带的安全网，不需要测试额外 `adb shell am force-stop`。**本轨未实测验证进程回收时延**（UNVERIFIED，归设备轨：可在套件跑完后 `adb shell ps -A | grep visual_js` 复核 0 残留）。
- 死亡确认等待：close 不走 2 s 死亡确认（`markDead(unexpected=false)` 直接完成，:319），teardown 不会被拖 2 s。

### 2.6 替代方案：类级共享引擎（Chromium @Batch 依据）——不推荐，登记理由

Chromium 的 `@Batch` 注解把同一批测试放进同一次 instrumentation 调用（浏览器进程不重启），理由是"每测试重启进程最高 10 s、通常 ~2 s"（Chromium 文档 android_instrumentation_tests.md，来源 [C2]）。映射到本仓：类级（companion/`@ClassRule` 风格）共享一个 `VisualJsClientEngine` 可省 3 次冷启动 ≈ 15 s。**不推荐**：(a) 引擎无"重置"seam，`fusedDigests`/重建预算跨用例累积，四断言位的独立性受污染；(b) 一次死亡/锁存就让整个类后续用例级联红，归因成本高于省下的 15 s；(c) 仓内设备绿先例（隔离套件 15 例）全程每测试一引擎，套件墙钟可接受。该方案留档，仅在设备轨实测墙钟超预期时重议。

## 3. 度量器复用缺陷根治

### 3.1 缺陷机理（行号级，基线 §3.1 + 本会话源码复核一致）

- `runChainToRestoredReplay()` 只建一个 `DeviceTextMeasurer`（现套件 :411），同一程序被执行两次：协调器 `generate`（:421，内部 `generateLocalJs` → `engine.execute`，`DefaultFigureGenerationCoordinator.kt:339`）+ 测试直取 drawOps 的 `engine.execute`（:432）。
- `QuickJsVisualExecutionEngine` 每事务调一次 `measurer.measure(batch)`（`QuickJsVisualExecutionEngine.kt:395`）；夹具 `measure()` 每次向 `measured` **追加**（现套件 :636）→ 两次执行后 `measured.size == 2`。
- 三处读取假定"整链只度量一次"：:225（用例 A，单次执行不受影响=绿）、:550（`ChainArtifacts.labelResolver`）、:582（`hasLabelInk`）——`singleOrNull()` 对 2 条记录返回 null → `requirePresent` 抛 → 用例 C（:271）与 D（:317）红。与设备 2/4 红归因一致（`docs/research/evidence/2026-10-30-batch-36/step02-v01-chain.md:79-80`，基线转录）。

### 3.2 方案对比

**方案 A：每次渲染独立度量器**
- 形态 1：每渲染新建 engine+coordinator——重放腿（C/D 骨架 :429-444）的语义就是"同一 program 在同一引擎上的第二次执行"（同 digest 非熔断），换引擎会弱化被测语义，且 coordinator 由工厂持有引擎，中途换不进去。
- 形态 2：给夹具加 `reset()`/代次机制——等于把"按次隔离"塞回夹具，复杂度不降，且"断言读累积列表"的脆弱模式仍在，将来第三次渲染/第二个 label 再红一次。
- 判定：**不采纳**。它修的是症状（计数为 1），不修根因（断言依赖"整链只渲染一次"的隐含假设）。

**方案 B：断言按键取（键 = 引擎回传事实，非测试自造）**
- app 版自然形态：**迁移后测试侧根本没有度量器可复用**——`VisualJsClientEngine` 构造无度量器参数（`VisualJsClientEngine.kt:100`），真实度量在 `:visual_js` 服务进程内的 `AndroidVisualContentMeasurer`（`VisualContentMeasuring.kt:53`；由 `visualContentMeasurerForService` 在服务 warm-up 构造，:490-508；服务单线程纪律 :38-41）。其 contentId 本身就是键：`"c" + index + "-" + digest8`（:69）。
- 三处读取的迁移后落点：
  1. `labelResolver()`（:549-563）：键取 `executed.contentIds.single()`——这是**引擎回传的、prepare() 批次条数决定的确定性事实**（本程序 prepare 恰回 1 条 text，现套件 :904），不是累积记录；对渲染次数不敏感。值侧 payload/sizeSp/fontScale/heightDu 用测试内 `Paint`（`REPLAY_TEXT_PAINT_FLAGS=0`、16sp×density×fontScale）同构现算——与回放侧 painter 装配同口径（KD-78 ④，`VisualMeasureEnvironment.DEFAULT_PAINT_FLAGS=0`，`VisualContentMeasuring.kt:186-192`），不是桩固定值。原 `measurer.payload`/`measured` 字段随之消失。
  2. `hasLabelInk()`（:575-595）：`measurePaint` 现算 advance 的手法原样保留，仅把 `measurer.measured.singleOrNull()` 的 fontSize 来源换成同一次现算（或直接用 sizeSp 常量 16f = `CANONICAL_SIZE_SP`，`VisualContentMeasuring.kt:202`）。
  3. 用例 A 的"度量真实发生过"（:224-227）：**断言职责转移并如实登记**——用例 A 手里只有 `generated.artifact = Program`（协调器不回传渲染结果），拿不到 contentIds；真实度量的设备级证明由 C/D 的墨迹断言承担（服务度量若被伪造/降桩，编译出的 label 几何与墨迹带失配 → C/D 红）。KDoc 里写明这条职责转移，不许静默丢覆盖。
- 判定：**采纳为根治形态**。它消灭的具体失败：断言与"整链只渲染一次"假设的耦合（当前 2/4 红）；将来渲染次数、条目数变化时不再有第二类红。新增物（`contentIds.single()` 取键 + 测试内现算 Paint）各自指认：前者替代"累积列表 singleOrNull"这一具体失败形状，后者替代"夹具度量值"来源（保持真实字体 metrics 语义）。

### 3.3 core/data 残留变体的决策点（不代裁定）

计划 §1.1"core/data 侧留进程内引擎变体作为降级口径显式标注，不删"。但现树该变体 2/4 夹具红：
- **选项 1（推荐）：在原文件同步落方案 B**——`labelResolver`/`hasLabelInk` 改按键取（键仍可用夹具的 index 键 "c0" 或现算），约 3 行级改动；用例 A 不受影响。代价：core/data 套件行为变化需在降级标注里写明；设备轨若跑 core/data 套件则转绿。
- **选项 2：原样保留 + 设备轨登记**——套件不进设备绿门或红例按夹具归因挂账。代价：每次设备轨 2 红，"15+ 套件不回退"清单上多两个长期噪音；违背"测试失败不得靠归因说明长期存在"的精神。
- 裁定归主会话/实现轨；无论哪条，降级标注（KDoc：真隔离语义由 app 模块套件承担，本文件为依赖方向受限的进程内降级变体）都是必做项。

### 3.4 零派发见证不变量的迁移保持（夹具缺陷②）

四断言位（现套件 KDoc :85-94）在 app 版全部可保持，classpath 同 §2.3-4：
- ① 审计 operation 行 0/0（:177-185 SQL 直读）；② attempt 行 `method='JS' AND reservation_id IS NULL`（:187-215）；③ 账本零触碰（:217-222 `readParentDispatchCount`）；④ 产物恒 Program + `networkRequestsAllowed=false` + 配置门 tripwire（:238-265 的 `UnreachableConfigurationStore` 原样）。
- **迁移时建议补强**（基线 §3.1-缺陷② 登记的缺口）：`seedParentOperation`（:732-768）现只在使用例 A（:140）调用，C/D 共用骨架（:395-540）不 seed 父操作行 → 见证③在重放/PDF 两例上无观测对象。把 `seedParentOperation(store, PARENT_REQUEST_ID)` 提进 `runChainToRestoredReplay()` 开头 + `readParentDispatchCount` 断言加进骨架收尾，见证③即覆盖 C/D。该改动是**加严**（不改任何生产语义，账本若被 JS 路径误触即红），符合"新增必须指认消灭的失败"：它消灭的是"账本被触碰只在用例 A 被观测"的具体盲区。

## 4. GitHub/官方参考（跨进程引擎的仪器化测试）评分

| # | 来源 | 类型/license | 与本题相关点 | 评分（1–5） | 采纳判定 |
|---|---|---|---|---|---|
| R1 | androidx `ServiceTestRule` 源码 `android/android-test` `runner/rules/java/androidx/test/rule/ServiceTestRule.java`（gh api 实读） | 官方测试库，Apache-2.0，活跃维护 | `DEFAULT_TIMEOUT = 5L` 秒；`bindService` 走 `BIND_AUTO_CREATE` + latch 等连接 + `TimeoutException`；测试后自动 unbind；`withTimeout()` 工厂可调 | 4 | **判定不适用于本套件装配**：默认 5 s 与冷启动 attach 4.6–7.5 s 相撞；无 ready 门/死亡重建；用它测的是 harness 而非 `VisualJsClientEngine`。但它的"bind→测→@After 收"形状佐证了本仓先例的结构 |
| R2 | 官方文档 Test your service（developer.android.com/training/testing/other-components/services） | 官方文档 | ServiceTestRule 的规范用法（service 测试的官方推荐面） | 3 | 参考；被 R1 源码实证覆盖 |
| R3 | Chromium `android_instrumentation_tests.md`（chromium.googlesource.com 实读 base64 解码） | Chromium 官方测试文档，BSD-3 | `@Batch`：同批测试共享 instrumentation 进程不重启；每测试进程重启成本最高 10 s（通常 ~2 s）；超时注解体系（@SmallTest 10+60 s 等） | 4 | 采纳其**批处理权衡事实**（§2.6 不推荐共享引擎的对照面）与"每测试进程重启成本"量化，支撑"每测试一引擎的墙钟代价可接受"论证 |
| R4 | AOSP CTS `tests/tests/security/src/android/security/cts/IsolatedProcessTest.java`（android.googlesource.com，搜索命中提交 09e0424/dde2817） | AOSP CTS，Apache-2.0 | 官方对 `isolatedProcess` 服务的仪器化测试先例（隔离进程安全属性），配 `isolatedProcess="true"` manifest 的测试 APK 形态 | 3 | 参考（官方先例背书"隔离进程可在仪器化测试中真实 bind 并断言"）；未逐行实读该文件 → 具体断言手法 **UNVERIFIED** |
| R5 | `android/testing-samples`（Google 官方样例仓，搜索命中） | Apache-2.0 | service/AIDL 测试样例族 | 2 | 未深读；与本仓已有设备绿先例相比无增量，仅存档 |

评分口径：更新度/维护状态/license/与当前技术栈（AIDL+Kotlin 协程+JUnit4 仪器化）兼容性。**结论：跨进程引擎仪器化测试没有比仓内 `VisualJsIsolationInstrumentedTest` 更贴合的现成实现**——外部先例（R1/R3/R4）共同佐证的三件事（bind→测→收结构、进程重启成本、隔离进程可测性）先例均已设备实证，T2 直接沿用仓内先例即可，不引入任何外部测试框架。

## 5. 迁移步骤（T2 可执行清单）

1. **新建** `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt`：4 例（用例 A、生产装配负控、C 屏重放、D PDF）按 §2.3 装配（每测试一引擎 + `connectForTest()` 预热 + `@After` close）；常量/夹具（`PROGRAM_JS`/`chainRequest`/`jsProposal`/`assistantCommand`/SQL 直读/`withStore`）从现套件原样迁。
2. **删** `DeviceTextMeasurer` 与 `engine(measurer)` 工厂（:605-641/:667-668）；`labelResolver`/`hasLabelInk` 按 §3.2-B 重写（键 = `executed.contentIds`，值 = 测试内同构现算 Paint）；用例 A 的度量断言按 §3.2-B-3 转移到 C/D 墨迹腿并在 KDoc 登记。
3. **补强**：`seedParentOperation` 提进共用骨架（§3.4）。
4. **降级标注**：core/data 原文件 KDoc 追加"进程内引擎降级变体；真隔离语义由 app 模块 `visualjs.V01VisualChainInstrumentedTest` 承担"；并按 §3.3 的裁定执行选项 1 或选项 2。
5. **不改**：`VisualJsClientEngine`/`VisualJsService`/`VisualJsIsolationInstrumentedTest`/Manifest/能力快照/广告面/生产装配点——全部零改动；迁移的全部改动面 = 1 个新文件 + 1 处 KDoc 标注（+ 可选 §3.3 选项 1 的 3 行）。
6. **设备轨验收**（T1/设备轨执行，本轨不跑）：app V-01 4 例绿；隔离套件 15 例不回退；core/data 变体按 §3.3 裁定；源快照含 core/model（基线 §二：core/model src 现干净）；`adb shell ps | grep visual_js` 无残留（进程回收复核）。

## 6. 回滚

- **回滚单元 = 新文件 + 标注**：删除 `app/src/androidTest/.../visualjs/V01VisualChainInstrumentedTest.kt`、还原 core/data 原文件的 KDoc（与可选的 3 行修复）→ 仓库回到迁移前状态。生产代码零触碰，无契约/schema/快照影响。
- **回滚触发条件**（按"失败先归因"纪律）：app 套件红且两轮修复后仍无法归因到夹具/环境时——若归因指向隔离引擎本身（如 coordinator 注入路径与真引擎的交互缺陷），按阻塞登记回主会话，core/data 进程内变体继续作为降级口径运行；此时不撤标注（标注描述的是事实分工，不依赖套件绿）。
- 中间态回退：若只是 §3.3 选项 1（core/data 修复）引发回归，单独还原该 3 行即可，app 套件不受影响。

## 7. 未验证项与假设

| # | 项 | 状态 |
|---|---|---|
| U1 | 仪器化进程回收时延（close → `:visual_js` 进程消失）未实测；机制依据是引擎 :691-694 注释与 BIND_AUTO_CREATE 语义 | **UNVERIFIED**，设备轨复核 |
| U2 | CTS `IsolatedProcessTest.java` 的具体断言手法未逐行实读（仅命中提交与路径） | **UNVERIFIED**，不影响本仓结论（R4 仅作存在性背书） |
| U3 | app androidTest 对 `implementation` 依赖的可见性：由既有隔离套件 import core.domain/core.model 且设备绿**归纳**，未单独做编译验证 | 强归纳（仓内设备绿事实），非本轮新跑 |
| U4 | 迁移后套件墙钟（4 例 × 冷启动）的实测值 | **UNVERIFIED**，设备轨实测；预估 1–2 min |
| U5 | 测试内现算 heightDu（`Paint` fontMetrics）与服务进程内 `AndroidVisualContentMeasurer` 的值逐位一致：两端同设备同字体同 flags（0），理论一致；若 API 33+ 文本感知 metrics 在两端取值路径不同（服务端走 `PaintVisualTextMeasureBackend.lineMetricsPx`，测试侧需同构实现）可能引入微小差异 | 假设，T2 落码时让测试侧直接复用同构换算（与现夹具 :605-641 同法）并在设备轨首跑时验证 |
| U6 | ServiceTestRule 与 isolatedProcess 服务组合的兼容性（未实测，也无需实测——判定不采用） | UNVERIFIED（不采納故不阻塞） |

## 8. 来源

- [C1] androidx.test ServiceTestRule 源码（`android/android-test`，Apache-2.0）：https://github.com/android/android-test/blob/main/runner/rules/java/androidx/test/rule/ServiceTestRule.java（本会话经 `gh api repos/android/android-test/contents/...` 实读 `DEFAULT_TIMEOUT = 5L` 等常量与方法体）
- [C2] Chromium Android Instrumentation Tests：https://chromium.googlesource.com/chromium/src/+/main/docs/testing/android_instrumentation_tests.md（本会话 `?format=TEXT` 实读：@Batch 与进程重启成本、超时注解）
- [C3] 官方文档 Test your service：https://developer.android.com/training/testing/other-components/services
- [C4] ServiceTestRule 参考页：https://developer.android.com/reference/androidx/test/rule/ServiceTestRule
- [C5] AOSP CTS IsolatedProcessTest 命中提交：https://android.googlesource.com/platform/cts/+/09e042454800a67090e1be7da0314157e8dc11fd/ 及 https://android.googlesource.com/platform/cts/+/dde2817af43ade27a003aced0375d24ef99842e8
- [C6] android/testing-samples：https://github.com/android/testing-samples
- [C7] android-test issue #935（ServiceTestRule 绑定失败案例）：https://github.com/android/android-test/issues/935

仓内一手来源见 §1 表。

## 附录：本会话实跑命令（可重跑）

```bash
# 源码定位与实读（Read 工具全文）：V01VisualChainInstrumentedTest.kt / VisualJsIsolationInstrumentedTest.kt / VisualJsClientEngine.kt / VisualContentMeasuring.kt 等，见 §1
grep -rn "VisualJsIsolation|DeviceTextMeasurer" --include=*.kt .   # 定位先例与夹具
sed -n '1,80p' core/data/src/main/.../visual/VisualFrameInputs.kt  # :19 fun interface
sed -n '350,380p' app/src/main/.../SmartMistakeBookApplication.kt  # :364-368 生产三参
grep -n "QuickJsVisualExecutionEngine(\|fun close(\|measurer" core/data/src/main/.../visual/QuickJsVisualExecutionEngine.kt  # :168-169/:317/:395
grep -n "androidTestImplementation\|implementation(project" app/build.gradle.kts   # :171-182/:200-205
grep -n "visualEngine\|visualFrameInputs" core/data/.../DefaultFigureGenerationCoordinator.kt  # 工厂缝定位
sed -n '800,877p' + sed -n '320,360p' core/data/.../DefaultFigureGenerationCoordinator.kt      # :859 create / :339 engine.execute
grep -n "MILLIS\|BYTES\|const val" core/domain/src/main/.../VisualExecutionEngine.kt            # :126/:129 预算
grep -rn "isolatedProcess" app/src/main/AndroidManifest.xml                                      # :65/:71
# 联网参考
gh api "search/code?q=ServiceTestRule+repo:android/android-test+in:path"                          # 命中 ServiceTestRule.java
gh api repos/android/android-test/contents/runner/rules/java/androidx/test/rule/ServiceTestRule.java  # 实读 DEFAULT_TIMEOUT=5L
# WebSearch: ServiceTestRule 文档 / isolated process AIDL instrumented / chromium renderer instrumentation / CTS IsolatedProcessTest
# extract_content: chromium android_instrumentation_tests.md (?format=TEXT 实读) / developer.android.com ServiceTestRule 参考页（首两次超时/404 后换通道取到包索引，最终以 gh 源码实读为准）
```
