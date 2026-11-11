# KD-76 判据预研：READY_TIMEOUT=10 s 设备校准判据与 29.5 s worst-case 链测量方案（2026-11-01，批次 38）

> 研究轨（判据预研，计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md` §1 F.20 任务 20）。**只读/设计**：零 Gradle、零设备命令、零代码改动、零 git 写；本文件为唯一落盘产物，不动热区（`docs/current` 契约正文、台账本体均未触碰）。
> 方法：本会话实读源码/契约/台账/既有设备证据 + 只读 grep。所有行号为本会话实读；凡未实跑/未实测的一律标 **UNVERIFIED** 或"设计原型（未执行）"。
> 关联台账：KD-76（`docs/known-defects.md:2863`，open）；清单 P-07/P-04 行（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`）。

## 0. 一句话结论

`READY_TIMEOUT_MILLIS = 10_000L` 是 R1' §2.3-4 的**待设备校准起点值**（`VisualJsClientEngine.kt:1244-1252` KDoc 原文"10 s 是 R1' §2.3-4 的待设备校准起点值……不是实测上界"）。本文件把"设备校准"落成**可执行的判据表**：① 观测口径（三时刻两分段，服务端锚已有、客户端分段锚缺 seam——给最小计时 seam 设计）；② 条件矩阵（冷启动不沉降 / 冷启动+CPU 竞争 / 低内存 / 沉降态对照）与每条件样本量；③ 三档判定规则（维持 10 s / 登记余量 / 上报调常量）与调常量的**牵动面清单**；④ 29.5 s worst-case 链给出两口径测量方案（分段近上限注入的合成上界 + 真实冷启动端到端实测）与既有分段证据盘点。**边界**：③（产品进程/UI 首请求）在生产装配点落地前结构性不可执行（生产恒三参、引擎 null，负控用例钉住）——校准出数只关闭"READY_TIMEOUT=10 s 设备校准"与"29.5 s 链"两个子缺口，不关闭 KD-76；**KD-76 保持 open、设备验证前 JS 不广告**。

## 1. 现状事实表（本会话实读锚点）

| 项 | 值 | 出处 |
|---|---|---|
| `READY_TIMEOUT_MILLIS` | 10_000L（"待设备校准起点值，不是实测上界"） | `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:1251`（KDoc :1244-1252） |
| `READY_POLL_INTERVAL_MILLIS` | 100L（10 s 预算内 100 个判定点） | 同文件 `:1254` |
| ready 等待位置 | 握手成功后轮询 `isReady()`；独立于 attach 预算与 2500 ms 宿主阶段期限；loading 形状不锁存、不扣重建预算 | 引擎 `:739-773`（KD-76 ⑤ 已落码）；`readyBudgetMillis()` `:810`（测试 seam `readyTimeoutOverrideForTest` `:1183`） |
| `FIRST_CONNECT_TIMEOUT_MILLIS` | 15_000L（覆盖实测冷启动 attach 4.6–7.5 s E-01d + 握手） | `:1227` |
| `RECONNECT_TIMEOUT_MILLIS` | 10_000L | `:1233` |
| `DEATH_CONFIRM_TIMEOUT_MILLIS` | 2_000L（"产品选择……不是平台 SLA"） | `:1240` |
| `SERVICE_HARD_DEADLINE_MILLIS` / `HOST_PHASE_DEADLINE_MILLIS` | 1500L / 2500L | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`/`:129` |
| 29.5 s 句（契约原文） | "最坏约 29.5 s（首连 15 s + warm-up ready 10 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销；ready 预算只在每次新进程 bind 后等待一次，已 ready 的连接复用不重付）" | `docs/current/visual-generation-contract.md` §4"终止层落地状态"段（本会话实读；该段即台账所引契约 `:72` 区域，docs/current 热区未改） |
| warm-up 实测装载成本 | 控制组 n=10 p50=686/p95=725/**max 738 ms**；套件 churn 组 n=16 中位 675.5 ms、churn 尾 1259–1746 ms；16+10=26/26 worker 全部 ready；口径 = AMS `Start proc` → 同 pid `warm-up finished`（logcat threadtime 毫秒、按 pid 配对）；`READY_TIMEOUT=10 s` 对实测 p95 余量 ≈14×（churn 尾 ≈5.7×） | `docs/research/evidence/2026-10-27-batch-27/step04-warmup-cost.md` §1/§3/§6（本会话实读 §1 口径定义 + §6 边界） |
| 服务端日志锚（已有） | `visual_js warm-up finished; worker ready for requests`（成功）/`visual_js warm-up failed`（失败） | `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:153`/`:155` |
| 客户端日志锚（现状） | **无握手/ready 分段日志**：全文件 `Log.` 仅 `:652`（bindService threw）与 `:828` 两处 `Log.w`（grep 实证）→ 客户端分段计时无现成锚 | `VisualJsClientEngine.kt` grep `Log\.` 实跑 |
| 冷启动方法先例 | `adb reboot` + boot_completed 后不沉降即测（loadavg 0.75）；隔离 13 例全绿、零意外杀 | 批次 26 E-03（`docs/research/evidence/2026-10-27-batch-26/step03-kd76-criteria.md`，台账 `:2893` 转录） |
| 既有校准缺口登记 | ③ 产品进程/UI 首请求、29.5 s worst-case 链、低内存、`READY_TIMEOUT=10 s` 设备校准、arm64/真机——均 UNVERIFIED | 台账批次 28/29/30 块（`docs/known-defects.md:2897`/`:2899`/`:2903`） |
| 文件脏态（本会话实跑） | `VisualJsClientEngine.kt`、`VisualJsService.kt`、两 V-01 测试文件 `git status --porcelain` 均干净；`DefaultFigureGenerationCoordinator.kt` 他线 M（本方案不触它） | `git status --porcelain -- <paths>` 实跑 |

## 2. READY_TIMEOUT=10 s 设备校准判据（给设备轨/实现批的可执行设计）

### 2.1 观测口径：三时刻、两分段

定义三个时刻（全部同一 worker、同一请求链）：

- **T_spawn**：AMS `Start proc <pid>:…:visual_js` 行时刻（logcat threadtime，毫秒分辨率；E-04 同口径）。
- **T_attach**：宿主 `onServiceConnected`（bind 完成）时刻。
- **T_ready**：同 pid `visual_js warm-up finished` 行时刻（`VisualJsService.kt:153`；ready 单调，之后不回退）。

由此两分段：

| 分段 | 定义 | 由哪个预算覆盖 | 现状 |
|---|---|---|---|
| **attach 段** | T_spawn → T_attach（进程 fork + attach + `onCreate` 启动） | `FIRST_CONNECT_TIMEOUT_MILLIS`（15 s）与 attach 部分重叠 | E-01d 实测 4.6–7.5 s（冷启动负载 5.25） |
| **ready 段** | max(0, T_ready − T_handshake)（握手后等 ready 的可感时长；warm-up 若先于握手完成则为 0） | `READY_TIMEOUT_MILLIS`（10 s）——**本条校准对象** | E-04 的 Start proc→warm-up finished 是 (attach+ready) 的合并观测，客户端可感的 ready 段**未单测**（E-04 §6-4 自认"宿主 bind→ready 客户端可感延迟未测，无计时 seam"） |

**计时 seam 缺口的两个补法（实现批二选一，判据表按 (b) 写）**：

- (a) **零代码近似**：`connectForTest()` 总墙钟（测试内 `SystemClock.uptimeMillis` 前后夹）+ logcat 两锚（T_spawn、T_ready）三点合成。缺点：attach 段与 ready 段只能间接分离（握手时刻无锚，ready 段下界 = T_ready − T_attach 上界被并入总墙钟），出数精度不足以支撑调常量裁定。
- (b) **最小计时 seam（推荐）**：`VisualJsClientEngine` 增加一个**测试观测面**（沿 `connectionCountForTest()`/`awaitConnectedForTest()` 同族命名与可见性，`internal` 即可——app 模块 androidTest 同源集可达，无跨模块问题）：`connectStageTimestampsForTest(): ConnectStages?`，携带 `attachAt/handshakeAt/readyAt` 三个 uptime 值（null = 未连上）。**新增指认的失败（新增纪律）**：消灭"E-04 §6-4 登记的'宿主 bind→ready 客户端可感延迟无计时 seam'缺口"——没有它，READY_TIMEOUT 的校准永远只有服务端合并口径。牵动面见 §2.4。
- 不可接受：改 `READY_TIMEOUT_MILLIS` 本身来"校准"（那是被校准对象，不是观测工具）；放宽 1500 ms 程序期限或 2500 ms 宿主期限（KD-76 红线）。

### 2.2 条件矩阵与样本量

| # | 条件 | 操作原型（设计，未执行） | 样本量 | 目的 |
|---|---|---|---|---|
| C1 | 冷启动不沉降（基线条件，E-03 方法） | `adb reboot`；`sys.boot_completed=1` 后立即 `am instrument` 校准用例，不沉降 | ≥10 轮（每轮 1 新 worker；E-04 控制组 n=10 同量级） | 复现 E-01d/E-03 冷启动形状 |
| C2 | 冷启动 + CPU 竞争（逼近 E-01d 负载 5.25） | C1 + 后台 busy-loop：`adb shell "for i in 1 2 3 4; do sh -c 'while :; do :; done' & done"`（进程数 k 以 `cat /proc/loadavg` 实测 ≥5 为准，k 的取值由设备轨校准、写进证据头） | ≥10 轮 | 压出 warm-up/attach 的竞争尾 |
| C3 | 低内存 | 专用低 RAM AVD（如 1 GB RAM 档）或真机低内存档；x86_64/API 34 常规 AVD **不构成**本条件 | ≥5 轮（资源允许时） | 填"低内存条件 UNVERIFIED"缺口的第一层 |
| C4 | 沉降态对照 | E-04 口径复跑（沉降态、单用例 ×N） | ≥10 轮 | 与 E-04 数字对账（p50≈686 ms） |

每轮记录：四时刻（C2 另记 `loadavg`）、两分段毫秒值、worker pid、是否 ready（`warm-up failed` 计 0 并按失败归因）、APK sha256、HEAD、工作树脏态、设备指纹（R4 规范最低字段）。

### 2.3 判定规则（预置；出数后由主会话裁定采纳，本轨不代裁）

以 **ready 段**（2.1 定义）在**全部条件全轮次的最大值 max** 为判定输入（不是 p95——校准的是上界常量）：

| 档 | 实测 ready 段 max | 判定 | 依据与动作 |
|---|---|---|---|
| 维持 | ≤ 2 000 ms | **10 s 维持**，校准缺口登记"已出数：max=X ms（条件），余量 ≥5×" | 5× 余量与既有判读一致（churn 尾 1746 ms ≈5.7× 被台账判为"无数据压力"；E-04 p95 余量 ≈14×） |
| 登记 | 2 000 < max ≤ 5 000 ms | 10 s 维持（余量 ≥2×），KD-76 台账登记实测 max 与条件，**不判 ③ 达成** | 常量不动；余量数字入账供后续批次复核 |
| 上报 | max > 5 000 ms（<2× 余量）或出现任一 `warm-up failed` 且归因非夹具 | **按阻塞上报主会话**：建议值 = ⌈max × 5⌉，且联动面逐项过一遍（§2.4）；不自动改码 | 现有 10 s 的整倍余量跌破 2×，属"慢但会到"被误判 loading 的边界 |

**显式禁止**：为凑"维持"档而放宽条件矩阵（C2 的 loadavg 门、C3 的低 RAM）；把 attach 段数字并入 ready 段虚增观测值；出数后直接改常量而不走主会话裁定。

### 2.4 调常量（若走上报档）的牵动面清单（改一处先枚举，全部要过）

1. `VisualJsClientEngine.kt:1251` 常量本体 + `:1244-1252` KDoc（"起点值"表述改为实测依据）。
2. 契约 `docs/current/visual-generation-contract.md` §4 的 29.5 s 句（"warm-up ready 10 s"分项与合计）——**热区**，须主会话解冻窗口。
3. 29.5 s 链测量用例的断言常量（§3 设计稿中的期望上界）。
4. loading 形状语义复核：超时时长变化影响"P-04 生命周期限次"的重新调度节奏（清单 `:127`）。
5. 既有 ready 超时用例不受影响：`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` 用的是 `readyTimeoutOverrideForTest=250`（`:1183` seam 覆盖预算），断言与常量解耦（KD-76 T2 块原文）。
6. 设备绿绑定源快照：常量文件被改 → 绑定套件全量复跑（纪律原文）。

### 2.5 计时 seam（若采纳 §2.1-b）的牵动面清单

- 新增成员在 `VisualJsClientEngine.kt`（现干净）：`internal` 可见性 + `ForTest` 命名（仓内 `connectForTest`/`fuseDigestForTest` 同族先例 `:1154`/`fuseDigestForTest` P-12③）；app androidTest 同源集可见（internal 跨模块不可达只发生在**别的模块**，同模块 androidTest 是 friend compilation）。
- R8/release 轨：该成员仅被 androidTest 引用 → 主包 R8 可能收缩 → R-04 release 轨上悬空引用（机制②同型）。处置沿 P-12③ 移交项先例："internal seam 进 release 轨须补成员 keep"（`app/r04-test-keep.pro`，现干净）；若 Keeper（R-04 任务 22 预研对象）接入，其 androidTest 用法推断 keep 会自动覆盖——两路二选一，实施批登记。
- 源快照：引擎文件变更 → 绑定设备套件复跑。
- `proguard-rules` 若配置了 Log 裁剪需复核（本轮 grep `proguard-rules.pro` 未见 `assumenosideeffects` Log 规则——**未逐行全读该文件**，实施批核）。

## 3. 29.5 s worst-case 链测量方案

### 3.1 链的构成与"最坏"的语义（先说清，防误测）

契约的 29.5 s = **四个独立预算常量之和**（15+10+2.5+2），语义是"一次冷启动首个请求从 bind 到拿到确定结果/如实失败，宿主可感上界 ≈29.5 s + Binder 开销"。四段并非都能在同一请求内同时到上限（1500 ms 服务硬杀先于 2500 ms 宿主期限触发时，后者的上限只出现在"服务活着但不回"的形状，如排队在另一在飞帧之后）。因此测量方案分两口径，**不把"单请求四段全顶满"当作必须复现的断言**：

- **口径 A（分段近上限注入 → 合成上界证明）**：把每段单独推到其预算的近上限，各段实测值相加 + 实测 Binder/调度开销，证明合成上界 ≤29.5 s。
- **口径 B（真实冷启动端到端实测）**：C1/C2 条件下测冷启动 → `connectForTest()` → 首个 `execute` 返回 `Rendered` 的全链分段计时（这是 KD-76 ③ 判据的计时半边，也是用户实际感受到的数）。

### 3.2 分段注入设计（口径 A；全部为测试夹具改动，零生产代码）

| 段 | 注入手法（现成夹具形态） | 近上限值 | 断言 |
|---|---|---|---|
| attach（15 s） | `DelayedAttachContext` 夹具同型（现值 `COLD_START_ATTACH_DELAY_MILLIS=7_600L`，`VisualJsIsolationInstrumentedTest.kt:763`），改注入 ~14.5 s | 14.5 s（<15 s 预算，仍应成功） | connect 成功且 attach 段实测值 ∈ 注入值 ± 轮询粒度 |
| ready（10 s） | 现有恒 not-ready fake binder 的**定时恢复变体**（isReady 前 ~9.5 s 返回 false 后转 true）；平台前提已独立取证（AOSP `Binder.linkToDeath` 本地 no-op，KD-76 T2 块）。注意 `readyTimeoutOverrideForTest` 是改预算的 seam，不造延迟——造延迟在夹具侧 | 9.5 s（<10 s 预算） | ready 段实测 ≈9.5 s，后续请求可用 |
| 宿主阶段（2.5 s） | 期限到点 → abandon 的路径已有设备语义（批次 24 abandon 6/6）；近上限注入 = 令在飞帧占满 2.5 s 后才完成（程序内可控延迟程序），或按 3.1 的互斥性说明**单段验证**：断言"期限到点后调用方在 abandon+死亡确认窗口内得到确定结果" | ≤2.5 s | 段界成立 |
| 死亡确认（2 s） | 既有 abandon 计时语义（`DEATH_CONFIRM_TIMEOUT_MILLIS`；E-01/E-01b 已设备绿）；近上限 = 死亡事实最晚到达形态（fake binder 延迟 binderDied ~1.9 s） | ≤2 s | 确认等待 ≤2 s 且不并发放大进程数 |
| Binder 开销 | 无注入；在 C4 沉降态把"无注入"用例的端到端实测值与分段和之差记为开销观测 | 实测登记 | 只登记，不设门 |

**合成断言（判据）**：各段近上限实测值之和 + 开销观测 ≤ 29 500 ms + **容差**；容差只覆盖轮询粒度/调度/Binder 开销，**实现批以首轮实测值登记常量（建议 ≤1 500 ms），不得反向放宽任何段预算**。超 29.5 s 时按缺口登记（契约句"最坏约 29.5 s"是上界承诺，实测超=缺口，不放宽契约文本）。

### 3.3 落点与纪律

- 新增 1–2 例进 `VisualJsIsolationInstrumentedTest`（现干净）或独立套件；用例为**新增**，触发"新增用例必须重跑"纪律；断言不得与既有 6/6 门重叠缩水（ready 超时 loading 语义、KD-66 hard+500 ms 门原样保持）。
- 出数落盘按 R4 规范（`docs/research/2026-10-26-device-evidence-convention.md`），台账回填 KD-76 Progress 块 + 清单 P-04/P-07 行。
- **边界如实**：口径 A 的注入形态由夹具制造，不是产品装配形状；③ 的"产品进程/UI 首请求"仍需生产装配批（生产恒三参、引擎 null——负控 `theProductionAssemblyWithoutAnInjectedEngineKeepsJsUnavailableWithoutAuditRows` 钉住该现状，`app/.../V01VisualChainInstrumentedTest.kt:247-275`）。在装配点落地前，③ 结构性不可执行——本方案不声称覆盖 ③。
- arm64/真机口径：沿发布矩阵批挂起（台账批次 28 块 (c) 项），本方案全部数字仅 x86_64/API 34 模拟器口径。

## 4. 未验证项

| # | 项 | 状态 |
|---|---|---|
| U1 | 本文件全部命令/注入值为**设计原型，未执行**（零设备纪律） | UNVERIFIED，归设备轨 |
| U2 | C2 的 busy-loop k 值与 loadavg 门是否可在目标 AVD 稳定复现负载 ≥5 | UNVERIFIED |
| U3 | C3 低 RAM AVD 的可用性与数字 | UNVERIFIED |
| U4 | §2.1-b 计时 seam 的 API 形态（返回类型/字段）与实现批落码 | 设计建议，未落码 |
| U5 | `proguard-rules.pro` 是否含 Log 裁剪规则（影响 §2.1-(a) 备选的日志可用性） | 本轮未逐行全读，UNVERIFIED |
| U6 | 口径 A 四段是否可在单请求内复现合成（3.1 已按互斥性说明处置） | 设计判断，出数时复核 |

## 5. 来源（本会话实读/实跑）

- 仓内：`VisualJsClientEngine.kt`（`:652`/`:810`/`:1154`/`:1183`/`:1227`/`:1233`/`:1240`/`:1251`/`:1254`）；`VisualJsService.kt:153`/`:155`；`VisualExecutionEngine.kt:126`/`:129`；`VisualJsIsolationInstrumentedTest.kt:763`（夹具常量）；`app/.../V01VisualChainInstrumentedTest.kt:247-275`（负控）；契约 §4 终止层段与 29.5 s 句；`docs/known-defects.md:2863-2905`（KD-76 全块）；`docs/research/evidence/2026-10-27-batch-27/step04-warmup-cost.md`（§1 口径、§6 边界）；`docs/research/2026-10-31-v01-isolated-wiring.md` §2.4（超时表先例）。
- 实跑命令：`grep -n` 常量与 Log 锚；`git status --porcelain -- <相关文件>` 脏态核对（引擎/服务/两 V-01 文件干净，coordinator 他线 M）。台账/契约数字为转录 + 抽源复核，未重跑任何设备。
