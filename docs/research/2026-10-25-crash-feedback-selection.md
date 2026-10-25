# R2 崩溃收集与反馈闭环选型（决定 T6）

日期：2026-10-25 · 研究线：批次 20 R2（计划 `.zcode/plans/plan-2026-10-25-batch-20-4e-js-beta-prep.md` §3 R2 / §4 T6）
硬约束：**本地优先、零外部服务、零新增隐私面**。一票否决项：默认外发不可用。

---

## 1. 结论（推荐）

**自写最小 UncaughtExceptionHandler + ApplicationExitInfo 启动自检 + 环形文件缓冲 + 复用既有 SAF 导出。**

一句话：三个候选里只有这条路径**同时**交付 T6 要求的三件事（崩溃落盘、ANR 覆盖、启动自检），且结构性零外发（strictOffline flavor 无 INTERNET 权限，物理不可联网）、零新增依赖、零新增隐私面。ACRA 是活跃维护的优质库，但它的能力集中在"崩溃时富采集 + 送去某处"，对 T6 的核心三件（ANR、启动自检、本地导出）均无原生支持——采用它意味着在其上再自写缺失的 60%，还引入 294KB 依赖与默认偏宽的采集面。若未来需要"富设备上下文崩溃报告"，ACRA（Apache-2.0）是保留的后备项。

---

## 2. 项目内一手事实闭包（本会话实测，path:line 为证）

| # | 事实 | 证据 |
|---|------|------|
| F1 | minSdk=23 / targetSdk=36 / compileSdk=37 | `app/build.gradle.kts:24-25` |
| F2 | 双 flavor：strictOffline / localFirst | `app/build.gradle.kts:35-44` |
| F3 | **strictOffline 移除 INTERNET 与 ACCESS_NETWORK_STATE 权限**——任何联网方案在离线 flavor 结构性不可用 | `app/src/strictOffline/AndroidManifest.xml:4-9`（`tools:node="remove"`） |
| F4 | 主清单只有 POST_NOTIFICATIONS + RECEIVE_BOOT_COMPLETED；INTERNET 仅 localFirst flavor 添加 | `app/src/main/AndroidManifest.xml:4-5`；`app/src/localFirst/AndroidManifest.xml:3` |
| F5 | **全库无既有 UncaughtExceptionHandler / ApplicationExitInfo / 运行时 ANR 检测**（主树 grep 零命中；命中均为 worktree 副本或 "planRequestId" 类子串误中） | 本会话 `grep -rn UncaughtExceptionHandler\|ApplicationExitInfo` 实测 |
| F6 | 现状唯一兜底：`CoroutineExceptionHandler` 只写 logcat，重启即丢 | `app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:117-119` |
| F7 | 启动状态/诊断编号面已存在（StartupState 带 diagnosticId、横幅展示"诊断编号"）——启动自检的天然挂点 | `app/src/main/kotlin/com/tingyun/smartmistakebook/StartupState.kt:10-17`；`StartupStateBanner.kt:85`；`SmartMistakeBookApplication.kt:141-142` |
| F8 | 进程启动时刻已记录（`processStartedAtEpochMillis`）——启动自检"上次退出晚于本进程启动前"的时间判据现成 | `SmartMistakeBookApplication.kt:145` |
| F9 | SAF 保存通道既有两种模式可照抄：备份导出用 `ActivityResultContracts.CreateDocument("application/octet-stream")`；PDF 用 `ACTION_CREATE_DOCUMENT` intent + content Uri `rwt` 写入 + **SHA-256 复读校验** | `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:695-698`；`core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/MistakePdfDelivery.kt:382-389`、`:310-344` |
| F10 | 系统分享通道既有：`ACTION_SEND` + FileProvider 授权 URI | `MistakePdfDelivery.kt:391-405`、`:408-430` |
| F11 | 设置侧有"存储、备份与导出"屏，三个 launcher（导出/验证/恢复）模式齐备——"导出诊断包"入口的落点 | `SecondaryScreens.kt:865`、`:695-756` |
| F12 | release/benchmark/internal 均 R8 minify——新增库需 consumer proguard 规则可用性评估 | `app/build.gradle.kts:50-70` |
| F13 | 工具链：AGP 9.3.0 / Kotlin 2.3.21 / kotlinx-serialization 已在目录（JSONL 落盘无需新依赖） | `gradle/libs.versions.toml`（本会话实测） |
| F14 | app 单测为纯 JUnit（无 Robolectric）——框架 API（ApplicationExitInfo）读取必须抽 seam 才可 JVM 测试 | `app/src/test/kotlin/com/tingyun/smartmistakebook/` 目录实测；toml 无 robolectric 条目 |
| F15 | 本批计划倾向零 DB schema（QuickJS 走独立运行库不加表）——崩溃记录走文件环形缓冲而非 Room 表，与批内方向一致 | 计划 §0（"本批预计零 schema"） |

---

## 3. 候选判定表

判据列 = 任务指定判据；❌=一票否决触发。

| 判据 | A. ACRA 5.14.2（本地 reporter 模式） | B. Sentry Android | C. 自写最小 handler | 附：xcrash（附加候选） |
|---|---|---|---|---|
| 崩溃覆盖 | ✅ 完整（handler 链 + 线程详情 + 堆栈哈希） | ✅ 完整（Java；native 需 NDK 变体） | ✅ `setDefaultUncaughtExceptionHandler` → 同步落盘 → 链回系统 handler（进程照常被杀，行为不变） | ✅（含 native） |
| ANR 覆盖 | ❌ **无运行时 ANR 检测**。仅 DropBoxCollector 读系统标签（`system_app_anr` 等），第三方应用在现代 Android 上读不到系统 DropBox 条目，且仓库零 `ApplicationExitInfo` 集成（code search 实测空） | ✅ 最强（`ANRWatchDog.java` + `AnrV2Integration`，sentry-android-core 内置） | ⚠️ 分层：API 30+ 启动时读 `ApplicationExitInfo`（`REASON_ANR=6`，官方 API 含 trace 流）→ 完整；API 23–29 **已知缺口**（只记 handler 捕获的崩溃，如实告知） | ✅（运行时 ANR 捕获） |
| 启动自检（上次异常退出） | ❌ 无此概念（ACRA 是崩溃时报告框架） | ✅（AnrV2 基于退出信息 + 会话关闭逻辑） | ✅ API 30+：`ApplicationExitInfo` 枚举 CRASH/ANR/SIGNALED/LOW_MEMORY；时间判据复用 F8 | ❌ 非其形态 |
| 体积 | ⚠️ acra-core AAR **294,307 字节**（Maven Central 实测 HEAD）+ androidx.annotation | ⚠️ sentry-android-core 数百 KB 量级（**未实测，待验证**）；meta `sentry-android` 再加 native .so（MB 级 + 16KB/ABI 面，与 T8 撞车） | ✅ ~0（自有代码约 150–200 行，无依赖） | ⚠️ native .so（16KB/ABI 面与 T8 撞车） |
| 维护度 | ✅ 优：5.14.2 发布于 2026-10-01，5.13.1 于 2025-09（gh api 实测） | ✅ 优：pushed 2026-10-23 | ⚠️ 自担（约 150–200 行纯 Kotlin + 官方平台 API，无第三方跟随维护面） | ⚠️ pushed 2025-06-27（14 个月未动） |
| License | ✅ Apache-2.0（gh licenseInfo 实测） | ✅ MIT（客户端） | ✅ N/A（自有代码） | ❌ **混合**（MIT+公有域+BSD，GitHub 识别为 Other；LICENSE 原文实测）——license 一票否决不干净 |
| SAF/分享通道复用度 | ⚠️ 半程：须自写 `ReportSender` 把 `CrashReportData` 写进自有文件库（acra-core 无文件 sender：sender 包只有 ReportSender/Factory 接口 + NullSender + 服务，`acra-http` 才有 HttpSender——目录实测），导出端照抄 F9/F10 | ❌ 无本地导出路径：无 DSN → `NoOpTransport`（源码路径实测存在）事件即弃；纯本地须自写 Transport 落盘（无官方文档背书，**待验证**），导出端仍要全自写 | ✅ 100%：落盘自有文件；导出直接照抄 F9（CreateDocument + rwt 写 + 摘要复读）与 F10（FileProvider 分享，可选） | ⚠️ 有本地 tombstone 落盘，但导出/分享仍需自写 |
| 默认外发？ | ✅ 不触发否决：仅引 acra-core 时 sender 落到 `NullSender`（源码实测："reports will NOT be sent - no valid ReportSender was found!"）。**但默认采集面偏宽**：ReportField 含 DEVICE_ID / USER_IP / INSTALLATION_ID / LOGCAT / SETTINGS_SECURE 等（枚举实测）——满足"零新增隐私面"须显式收窄 reportContent | ⚠️ 结构性危险：SDK 的设计目的就是发往 Sentry 服务端；无 DSN 时惰性化但一无用处，配 DSN 即外发。**纯本地形态无官方支持**（自写 Transport 属 hack，单一来源=源码推断，待验证）。按"零外部服务"的约束本意：**不采纳** | ✅ 结构性零外发：strictOffline 无 INTERNET 权限（F3），物理不可联网；导出 = 用户手动 SAF/分享 | ✅ 默认本地落盘（设计如此），但被 license/维护度/16KB 三项排除 |
| 对 T8（16KB/ABI）影响 | ✅ 无 native | ⚠️ meta 包含 .so；即使 core-only 也无收益 | ✅ 零 native | ❌ 新增 .so 对齐面 |
| R8（F12） | ✅ 自带 consumerProguardFile（acra-core/build.gradle.kts 实测） | ⚠️ 未核（被否决，未继续） | ✅ 无库无规则 | ⚠️ 未核（被否决） |

### 一票否决核验结果

- **默认外发**：A（ACRA core-only + 自定义 sender）与 C 不触发；B 触发约束本意（服务端导向 + 纯本地无官方支持）；xcrash 不触发外发项但触发 **license 项**（混合 license 不满足"逐项核对 license"的干净判定）。
- **license**：xcrash 淘汰；其余过。

### ANR-Watchdog（SalomonBrys/ANR-Watchdog，API 23–29 的可选补件）

MIT、2978 stars、**2022-03-11 后停更**（gh 实测）。代码极小且稳定，但"停更库 + 新依赖"不符合本批最小面目标。**裁定：本批不引入**；API 23–29 的 ANR 缺口如实登记（见 §6），若 beta 期发现 <30 设备 ANR 是主诉，再作为独立小步评估引入（需复验其 minSdk/线程安全声明——单一来源待验证）。

---

## 4. 为什么不选 ACRA（GitHub 优先规则的诚实适用）

用户规则"能在 GitHub 抄的高质量代码不自己造"。ACRA 是好库（Apache-2.0、活跃、6.5k stars），**但 T6 的三个核心需求它一个都不原生覆盖**：

1. **ANR**：无运行时检测，DropBox 路径对第三方应用在现代 Android 无效（§3 表）；无 ApplicationExitInfo 集成（仓库 code search 零命中）。
2. **启动自检**：无此概念，仍要自写 ApplicationExitInfo 对账。
3. **本地导出**：无文件 sender（目录实测），仍要自写 ReportSender + 导出。

即：选 ACRA = 294KB 依赖 + 注解初始化 + 自定义 sender + 仍自写 ANR/自检/导出 + 收窄默认采集面（否则违反零新增隐私面）。库覆盖不了需求时用库是惯性不是复用；剩余真正要写的部分（handler + ApplicationExitInfo + 环形文件 + 导出）全部是官方平台 API，总量约 150–200 行。**ACRA 保留为后备**：若后续需要富设备上下文（配置变更、内存态等）的崩溃报告，再引入并显式配置 reportContent 白名单。

Sentry 同理更重：纯本地形态是"拿一个为服务端构建的管道，砍掉服务端，自写本地端"，且 native 面与 T8 相撞。

---

## 5. 最小集成面清单（T6 执行配方）

**零新依赖、零 manifest 改动（若只走 SAF 不走分享）、零 DB schema、零 native。**

### 5.1 新增文件（app 模块，全部新文件不碰热区）

建议落 `app/src/main/kotlin/com/tingyun/smartmistakebook/diagnostics/`：

1. **`CrashLedger.kt`**（~80 行）：环形文件写入器。
   - 位置 `filesDir/diagnostics/`（比 cacheDir 稳——cache 可被系统清理；PDF 用 cacheDir 是因为可再生成，崩溃记录不可再生成）。
   - 单条事件 = 一行 JSON（kotlinx-serialization 已有，F13）：时间戳、versionName/versionCode（本地已知字段，`AndroidBackupRepository.kt:308` 同款"读不到就 null 不假造"纪律）、flavor、线程名、堆栈、类别（CRASH/ANR_EXIT/LOW_MEMORY_EXIT/…）。
   - 轮转：N 个文件 × M KB 上限，超限丢最旧——总量封顶即"环形"。
   - 崩溃时写：`open→append→close` 一次完成（不依赖缓冲），写完链回 previous handler（保持系统杀进程行为不变）。
   - **不采集**：设备标识、logcat、系统设置、IP（零新增隐私面）。堆栈里若含用户内容路径，只在用户主动导出时离开设备。
2. **`StartupExitReconciler.kt`**（~40 行）：API 30+ 读 `ActivityManager.getHistoricalProcessExitReasons`，筛 `REASON_ANR/CRASH/CRASH_NATIVE/SIGNALED/LOW_MEMORY`，可trace的取 `getTraceInputStream()`，写入 CrashLedger；时间判据复用 `processStartedAtEpochMillis`（F8）。
   - **可测 seam**：ApplicationExitInfo 读取抽接口（F14：无 Robolectric，框架类不能进 JVM 单测），JVM 测试喂内存实现。
3. **`DiagnosticsBundleExporter.kt`**（~60 行）：聚合 ledger → 单文件（或 zip）→ 经 `contentResolver.openFileDescriptor(uri, "rwt")` 写入 + SHA-256 复读校验——逐字照抄 `MistakePdfDocumentSaver.save` 的模式（F9，`MistakePdfDelivery.kt:310-344`）。

### 5.2 既有文件改动（开工时复核 dirty；两处开工时均为本批干净区）

4. **`SmartMistakeBookApplication.kt`**（计划 §0 实测 clean）：
   - `attachBaseContext`/`onCreate` 装配 handler（保存并链回 previous）；
   - 启动异步（不阻塞主线程）跑 StartupExitReconciler；
   - 既有协程 backstop（`:117-119`）**追加**写 CrashLedger 一行（消灭的失败：现状协程未捕获异常只进 logcat 重启即丢）。
5. **`SecondaryScreens.kt`**（ask 标注 clean）："存储、备份与导出"屏（`:865`）加"导出诊断包"行，launcher 照抄备份导出模式（`:695-698`，`CreateDocument("application/octet-stream")` 或 zip MIME）。
   - 可选第二步（非本批必须）：结果也可经 `ACTION_SEND` + FileProvider 分享（F10 模式；若走此路需在 manifest 加一个 provider 声明——为最小面，**本批只做 SAF**，分享留待需要时）。

### 5.3 影响面枚举（纪律 12.1：改一处先闭合牵动面）

- 系统杀进程行为：链回 previous handler → 不变。
- 启动时长：对账挪到后台协程 → 主线程零新增。
- 存储：`filesDir/diagnostics/` 总量封顶（环形轮转），不进 Room、不进 DataStore、不进备份域（崩溃记录不属学生数据；导出与否由用户定）。
- strictOffline：无 INTERNET 也能全功能（对账/落盘/SAF 导出均本地）。
- R8：无新库无新规则。
- 热区：不改 Root/feature/*/core/ui/CleanRedrawTaskContract 等（§5.1 全新文件 + §5.2 两个 clean 文件）。

### 5.4 测试（先红后绿）

- CrashLedger：轮转封顶（写超量→最旧被丢→总字节不超限）、JSONL 往返、崩溃时半行容错（读到不完整行跳过不炸）。
- StartupExitReconciler：接口注入假退出记录 → ANR/CRASH/LOW_MEMORY 分类正确、时间窗过滤正确、API<30 空。
- Exporter：写后复读摘要一致（照 `MistakePdfDocumentSaver` 的验证语义）。

---

## 6. 已知缺口与未验证项

**已知缺口（如实登记，不伪覆盖）**：
1. API 23–29 无系统退出记录可用（ApplicationExitInfo 是 30+），此区间 ANR 检测缺失；崩溃捕获全版本有效。补法（引入 ANR-Watchdog 或自写看门狗）留待 beta 反馈驱动。
2. native 崩溃（SIGSEGV 等）不捕获——本应用 native 库为 litert（推理用）；native 崩坏本就超出本地反馈闭环能力，且引 native 捕获库必撞 license/16KB 面。如实知悉。

**未验证项（单一来源/未实测，不得当事实）**：
1. Sentry 纯本地 Transport 落盘无官方支持——结论来自源码结构（NoOpTransport 存在 + 无本地 sender）+ 未找到官方文档背书；因 Sentry 已被否决，未继续深挖。标**待验证**（若未来重议需查 sentry-java 官方文档）。
2. Sentry sentry-android-core AAR 实际体积未实测（未下载工件）。同上，已被否决未深挖。
3. ACRA DropBox ANR 路径"第三方应用读不到系统标签"——来自平台行为推断（DropBox 系统标签需系统侧权限），未单独取到书面来源；不影响主结论（ACRA 因三缺被否，非仅此项）。
4. ANR-Watchdog 的 minSdk/线程安全声明未核（其 README 声称全版本支持，单一来源）——本批不引入，引入前需核。

---

## 7. 来源

**项目内（一手源码，本会话实测）**：见 §2 表格 path:line；git status 实测确认 §5.2 两文件开工时干净（计划 §0 + ask 给出）。

**外部（gh CLI / curl / webReader 实测取回）**：
- ACRA 仓库：https://github.com/ACRA/acra — license Apache-2.0（gh repo view）；release acra-5.14.2 2026-10-01（gh api releases）；minSdk=14（`gradle/libs.versions.toml` via gh contents）；sender 包无文件 sender、NullSender 行为、DropBoxCollector/CoreConfiguration ANR 仅 DropBox、ReportField 枚举含 DEVICE_ID/USER_IP/LOGCAT 等、consumerProguardFile（均 gh contents 取源码实测）；AAR 294,307 字节（curl -I repo1.maven.org）。
- Sentry：https://github.com/getsentry/sentry-java — MIT/pushed 2026-10-23（gh api）；`sentry/src/main/java/io/sentry/transport/NoOpTransport.java`、`sentry-android-core/.../ANRWatchDog.java`/`AnrV2Integration.java`、`sentry-android-ndk/` 目录（gh contents/search 实测）。
- ApplicationExitInfo（官方文档，webReader 取回）：https://developer.android.com/reference/android/app/ApplicationExitInfo — API 30+；REASON_ANR=6/REASON_CRASH=4/REASON_CRASH_NATIVE=5；`getTraceInputStream()`（ANR 30+/native tombstone 31+）；记录存于全局环形缓冲、可能为 null。读取入口 `ActivityManager.getHistoricalProcessExitReasons`（https://developer.android.com/reference/android/app/ActivityManager）。
- ANR-Watchdog：https://github.com/SalomonBrys/ANR-Watchdog — MIT、pushed 2022-03-11（gh repo view 实测）。
- xcrash：https://github.com/iqiyi/xcrash — license "Other"（混合 MIT/公有域/BSD，LICENSE 原文 gh contents 实测）、pushed 2025-06-27。
