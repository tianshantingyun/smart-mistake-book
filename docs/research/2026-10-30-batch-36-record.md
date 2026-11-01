# 批次 36 记录（2026-10-30）：4E.2 纵向链 V-01 + R-04 K6 keep 解除 + A-10~A-12 边界

> 记录时点：2026-10-31（+08:00；批次设备/落盘窗口为 2026-10-30）。本文件由记录员在共享工作树 `D:\smart mistake book` 写入，依据：计划（完整读）、批次 36 基线（225 行）、两研究文档（R1 217 行 / R2 250 行，规模与节结构实读核对）、`docs/research/evidence/2026-10-30-batch-36/` 七份证据（E-00…E-05）、各轨工单摘要与复核轨发现，另对关键门/锚点/源快照做了本会话实跑复核（§10）。**记录员未运行 Gradle、未安装/执行任何设备用例、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码**；本轮改动 = 本记录 + 四份台账（§附录 A）。
> 计划：`.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md`（45 行，已完整读；11 项任务，§1 A R-04 解除 / B 纵向链与边界 / C 台账与文档 / D 工具与环境）。
> 基线：`docs/research/2026-10-30-batch-36-baseline.md`（计划 §0 主干相符；两处过时已勘正——A-04~A-06 行刷新已随批次 35 v3 补记 8 在树、keep 幽灵门已脚本化）。
> 研究：R1 `docs/research/2026-10-30-r04-k6-keep-rules.md`（**217 行**，R8keep：K6 两机制一手取证 + 最小 keep 方案 + K6 重跑判据/回滚）、R2 `docs/research/2026-10-30-v01-chain-seams.md`（**250 行**，V-01 缺 seam 与最小仪器化 + A-10/A-11/A-12 边界落位 + P-12③ 方案 B）。
> 设备证据：`docs/research/evidence/2026-10-30-batch-36/`（**7 份 `.md`，52,370 B**，本会话 `find -printf %s` 复算一致；E-ID E-00…E-05 全批唯一；`build/device-track-batch36/` 原始日志 64 文件在本机不入仓）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门（修复环后）`exit=0`；修复环第 1 轮一条处置（KD-82 证据句更正）已收口（§5.0）。
>
> **本记录不声称"批次全绿"或"R-04 解除"**：R-04 **维持阻塞（UNVERIFIED）**——keep 两规则已落树且 K1–K4 绿、tracing 半边 keep 实测已落主包 dex，但 K5 未按判据执行（仅 so 双 ABI 半边）、**K6 两例在扩充后 keep 的 release 轨无执行证据**，且**记录员产物级预检实测：j$ 半边 keep 未让名（测试 mapping 仍含 `Comparators$NaturalOrderComparator -> j$.util.g`，机制①名称碰撞仍在）→ 按判据 K6 上设备被预检门拦下、机制①解锁路径待重估**；V-01 设备 FAIL 2/4 且**语义底线未满足**（注入进程内 `QuickJsVisualExecutionEngine` 非隔离 `VisualJsClientEngine`，套件落点/降级口径待主会话裁定）；A-10"耗尽→文字降级"的账本半边红于夹具、编排半边未落；`availableMethods` 未接通、JS 不广告；JVM 用例执行=批次构建轮报告 XML 读值（记录员未复跑 Gradle）；生产签名/arm64 UNVERIFIED。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 11 项**（§1 A–D 四组）：A R-04 解除（任务 1 keep 扩充、任务 2 K1–K6 重跑）；B 纵向链与边界（3 A-10~A-12、4 P-12③、5 V-01、6 P-11 errorKind、7 M-04 案 A）；C 台账（8 准入清单刷新、9 KD 台账）；D 工具/环境（10 KD-72/73/77、11 热区条件轨）。
- **实际执行面**：两研究线（R8keep keep 规则机制研究 / V-01 链 seam 研究）+ T1 设备判据（工单内联）+ T2 keep 规则落码 + T3 A-10/A-11/A-12 边界 + T4 V-01 实施 + T5 台账 + T6 KD-72/73/77 + T7 条件轨 + 集成交接 + 设备轨（`emulator-5562`，18 套件 + step19 V-01 + step20 A-10 + R-04 K1–K4）+ 证据 7 份 + 复核轨（20 条发现全数独立复核）+ 修复环（1 条处置）+ 本记录与四份台账同步。
- **与计划的已知偏差（七处）**：
  1. **任务 1 前提修正（结构性）**：计划原文"对 `androidx.tracing.Trace` 与 `j$/util/g` 扩 `app/r04-test-keep.pro`"——研究证得 `androidx.tracing.Trace` 半边**结构性不可能**在该文件修（类不在测试 R8 输入，keep 不能造类，R1 §三.2/§四.2），必须落 `app/proguard-rules.pro`；按工单前言裁定按研究结论执行，两文件两规则落树（§3.2）。
  2. **任务 2 全链重跑未完成**：设备轨 R-04 窗口只到 K4；K5 仅 so 双 ABI 半边补查、dex 计数未跑；K6 无执行证据 → R-04 按"阻塞（UNVERIFIED）"登记而非关闭，也不是新红（§4.5）。判据不放宽。
  3. **任务 5 V-01 语义缺口**：设备套件注入 `core/data` 进程内 `QuickJsVisualExecutionEngine`（`V01VisualChainInstrumentedTest.kt:118`/`:142-149`），非 app 侧隔离 `VisualJsClientEngine`（对 `com.tingyun.smartmistakebook.visualjs` import 计数 0）——即使两红例修复转绿也不满足判据"真 `VisualJsClientEngine`（`:visual_js` 隔离进程）"；套件迁 app 模块 or 明示降级口径**移交主会话裁定**（E-02 §5、复核发现 #1）。
  4. **任务 6 P-11 errorKind 半边：登记不落**。`RoomTutorToolRunner.kt` git 干净（基线 §二实跑）使"git 视角前置"成立，但他线是否仍认领该文件仓库状态无法证明（基线 §六明示口径由主会话裁定），且 runner 今日无 JS 生产调用点（清单 `:134` 原文"JS 路径未接工具环"）→ 落类型化 errorKind **指认不出它消灭的现实失败**（纪律 12.2 门）；`UNAVAILABLE` status 通道 + KDoc（`FigureGeneration.kt:549-556`）已承载语义。
  5. **任务 7 M-04：维持案 B**。core/domain 仍 4 个源文件 M（`BatchImportRepository`/`MistakeDetailRepository`/`SchedulingEvaluation`/`TutorJudgedReview`，git status 实证）→ 案 A 枚举落点热区未清空，按计划原文走"维持案 B 并登记"分支（批次 35 已落的 `MEASUREMENT_UNAVAILABLE_DIAGNOSTIC_MARKER=QuickJsVisualExecutionEngine.kt:62` + 设备/JVM 双半边断言不动）。
  6. **任务 8 的 A-04~A-06 刷新已过时**：批次 35 v3 补记 8（提交 `492b349e`）已刷新九行——T5 只做现树复核（补记 9：四锚点文件 git 干净、`JS !in` 0 命中、A-05 锚点精化 `:964-965`）；`visual-generation-contract.md:208` 因 `docs/current` 热区未清空（开工与收尾两次实测均 7 条 M）→ **登记不落笔**。
  7. **T1 设备判据未落独立文档**：批次 35 有 573 行判据文档，批次 36 判据以工单内联形式给出（判据与命令清单全文见工单；设备轨按其执行）——登记为交付形态偏离。
- **不做（全部未做）**：`availableMethods` 接通与广告开关；JS 广告；真实 Provider（无凭据）；锁文件生成/提交（KD-72 窗口仍不满足）；`docs/current` 热区正文；feature 五模块与 core/ui、core/domain、core/markdown、core/export。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature/tutor、feature/library、feature/review、feature/capture、feature/profile、core/ui 全部、core/markdown 整目录、core/export 全部、core/domain（开工即脏 M）、core/data 他线 M 与 knowledge/dense 面、settings.gradle.kts、.gitignore、app Root 与开工即脏 M、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production。本批触碰的共享脏文件（`FigureGenerationPolicy.kt`、`DefaultFigureGenerationCoordinator.kt`、`VisualJsClientEngine.kt`/隔离测试、两 `.pro`）均"整文件纳入并注明"（§8）。
2. **能力快照 / prompt / 广告文本零改动**：本批 7 个 M 文件 diff 的 ±行对 `广告|availableMethods|CapabilitySnapshot` grep = 0（集成轨实跑）；`FigurePromptAssembler.kt` 与 `ModelTasks.kt`（能力快照声明）git 干净；本批 availableMethods 仅 4 处新增且均为 KDoc/测试夹具，非接通。
3. **DB schema 保持 71**（`StudyDatabase.kt:139`；本会话 S=71 静态门 PASS/EXIT=0 实跑）；请求 schema 只由对应轨写（本批零 wire schema 改动）。
4. **R-04 判据不放宽**：禁 internal 变体、禁 `-dontobfuscate`；keep 扩充逐条给"消灭哪个具体失败"；K6 红按判据阻塞回报——本批 K6 无执行证据，同按阻塞处理，不以 K1–K4 绿替代。
5. 设备绿绑定源快照（含 `core/model/src`；1548 文件，本会话 `sha256sum -c` EXIT=0）；新增用例必须重跑；修复环后必须复跑强门。
6. 锁文件与 `tmp/` 不提交（勘正：`tmp/` **不在** `.gitignore`，"不提交"是纪律惯例非 ignore 规则——集成轨实测 `git check-ignore` exit 1）。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`4f11cd5432e6c259038fc92bd2e975aee8d304b4`（与批次 35 收口一致；本会话实跑）。
- **工作树**：**709 条**脏/未跟踪（本会话 `git status --porcelain | wc -l`；E-00 时点 706、基线时点 686、ask 时点 689——多线在飞滚动，差值未逐项追认 UNVERIFIED）。
- **DB schema**：71（`StudyDatabase.kt:139`）；`71.json` 在树；S=71 静态比对 `RESULT=PASS`/`EXIT=0`（本会话实跑，`PLAN_HASH=JSON_HASH=714c0ecb…` 逐字相等）。
- **keep 两文件现树态**：`app/r04-test-keep.pro` 74 行（M，+34 纯追加；`:74` `-keep class j$.** { *; }`）、`app/proguard-rules.pro` 39 行（M，+20 纯追加；`:35-39` Trace 三签名）；`app/build.gradle.kts` 干净（本会话实读+实跑）。
- **热区（沿基线 §二，本轨未重跑全量计数）**：feature/tutor 59、feature/library 43、core/ui 35、feature/capture 16、feature/review 12、core/domain 4 M——他线在飞未触碰。
- **设备**：4 台在线（5562/5566/5584/5596，E-00 §2 本会话转引；5596 = `api23_smoke64.avd`，T6 专用；18 套件/强门轨只用 5562）。
- **KD 台账**：`docs/known-defects.md` 82 条（尾条 KD-82 open；KD-80/81 closed；KD-72/73/77/82 open——本记录追加批次 36 Progress 块，§附录 A）。

## 2. 两研究线

### 2.1 R1 R8keep：K6 keep 规则机制研究（`2026-10-30-r04-k6-keep-rules.md`，217 行）

驱动 T2 落码。核心裁定（记录员按节结构实读 + 轨摘要转录；机制证据全部来自崩溃当次产物对——两 APK sha 与 `build/device-track-batch35/r04-two-apk-sha.txt` 逐字一致）：

- **K6 三类崩溃签名是同一根因的两个面**：minified 主 APK 与 minified 测试 APK 由**两次独立 R8**产出、无改名协调，经 instrumentation **合并 classloader**（测试 dex 在前，logcat `:46`/`:53`）合并后互相打架。
- **机制①（改名碰撞）**：主包 R8 把 `j$.util.DesugarCollections$SynchronizedMap` 改名 `j$.util.g`（主 mapping `:871393`），测试包 R8 把 `j$.util.Comparators$NaturalOrderComparator` 改名**同一个** `j$.util.g`（测试 mapping `:49004`；dexdump 实证主=有 `<init>(Ljava/util/Map;)V` 的 Map 包装类、测=无该构造器的枚举 Comparator）→ 主包 `DesugarCollections.synchronizedMap` 的调用解析到测试 dex 枚举 → `NoSuchMethodError`。**勘正两条**：4 条日志是同一次失败的 4 条通道渲染（同 PID 同栈），非 4 个调用点；不是计划猜的 `LinkedHashMap$Entry`。
- **机制②（类消失）**：`androidx.tracing.Trace` 由 `androidx.work:work-runtime 2.10.5` 以 runtime 依赖引入（POM 实证）、被 `androidx.test:monitor 1.8.0` 以 api 依赖引用（POM 实证），但主包 R8 **整类内联删除**（主 mapping 仅内联帧记录、主 dex 0 定义）、测试 APK 因共享依赖不重复打包而从未装载（测试 dex 0 定义/0 mapping 条目；旁证：测试 dex 244 个 compose.ui/945 个 androidx.test/0 work/0 tracing 类定义）→ 测试 dex 30 处按名 `invoke-static`（beginSection/endSection/forceEnableAppTracing 三签名）悬空 → CNFE/NCDFE。
- **最小修复 = 两条规则两个文件**：`r04-test-keep.pro` 加 `-keep class j$.** { *; }`（测试 dex 让出全部改名 j$ 名 + 同名遮蔽变超集，修①，仅测试 APK 膨胀）；`proguard-rules.pro` 加 `-keep class androidx.tracing.Trace { 三个实测签名 }`（让类真实存在于主包，修②）——后者结构性不可能在 r04 文件修（keep 不能造不在 R8 输入里的类），是对计划任务 1 前提的修正。被拒备选：更窄 keep（改名分配不确定）、对称 j$ keep 进生产文件（生产膨胀换不来确定性）、`androidTestImplementation(tracing)`（大概率被共享依赖排除，未验证）、slackhq/Keeper（Apache-2.0/活跃，结构性备选不引入新插件）。
- **K6 重跑判据照抄批次 35 §2.5 不放宽**（`OK (2 tests)` + 三类签名 0；仍红按阻塞回报）；新增两条**产物级预检**（主 mapping 仍含 `DesugarCollections$SynchronizedMap -> j$.util.g`、测试 mapping ` -> j$.util.g:` 计数=0、主包 dex 按名存在 `androidx/tracing/Trace`）；回滚 = 还原两文件追加块（无 schema/请求 schema/能力面/lockfile 影响）。
- 外部参考 8 项评分（AGP DSL 5/5、Guardsquare 4/5、android-test #2167 OPEN 4.5/5、Cookbook 3.5/5、Keeper 3.5/5、两 POM 5/5、shrink-code 页两种通道未取到 → UNVERIFIED 未引用）。
- **对台账的勘误建议**（§七）：计划 §0"4×"勘正为"一次失败四通道渲染"；基线 §3.3"keep 需覆盖两侧"对 tracing 半边不成立。

### 2.2 R2 V-01 链 seam 研究（`2026-10-30-v01-chain-seams.md`，250 行）

驱动 T3/T4 落码。核心裁定：

- **V-01 全链组件全部在树，缺的是生产装配而非组件**：`visualEngine`/`visualFrameInputs` 生产恒 null（`SmartMistakeBookApplication.kt:363-369` 三参调用；工厂四/五参默认 null 为既有公共缝 `DefaultFigureGenerationCoordinator.kt:805-827`）；屏/PDF painter 零生产调用点（grep 全仓仅 androidTest 命中）；`VisualSnapshotRestore` 无生产构造方（记录构造唯一在测试）→ **V-01 设备用例走测试内装配即可闭环，不需要任何生产装配改动**；UI 卡片段降级登记（`VisualSceneCard` 全仓 0 命中，计划任务 5 原文允许）。
- **0 Provider 派发 = 4 个独立断言位**（任一即证伪）：审计 operation `reserved/used=0`（`:682-694`）、attempt `method='JS' AND reservation_id IS NULL`（`:608-624`）、账本零触碰（`:240-249` KDoc，JS 路径不调 `ledger.reserve`）、产物恒 `Program` 非 `ImageAsset` + `networkRequestsAllowed=false` 凭证门恒 null（`ImageCredentialGate.kt:74-78`）。
- **A-10 十阈值盘点**：6 次派发/512KiB payload/64KiB 单份/换 method 不重置**在树**；128KiB 累计源码、≤6 布局尝试、10s 累计墙钟、两份最终图、文字降级**五项不在树**（`FigureGenerationTrace` 亦 0 命中）→ 边界用例须先补常量（先红后绿），逐条给了落位建议与"消灭的失败"指认；第 7 向量（第 7 次派发拒）引用既有 `FigureGenerationBudgetDeclarationTest` 载体族。
- **A-11**：三协议 × 7 向量按 `ModelWireProtocol` API 面（requestBody/parseCompletion/reconstructedBody，三实现 `supportsNativeTools=false`）给 21 例表驱动设计。
- **A-12**：golden 扩测定为 inline 逐字节风格（现状 42 @Test 无 golden 资源文件；dense/ 资源是他线在飞面勿引），4 类扩测例。
- **P-12③ 可构造性再评估 → 采纳方案 B（状态注入 seam）**：4 熔断写点（`VisualJsClientEngine.kt:278/:441/:517/:916`）+1 解除点（`:296-298`，恒等物=被重试程序自身 digest）；批次 35"重试挂死只会再挂"判定成立的原因是解除要求同一程序渲染成功而触发程序必然挂死；方案 A（时钟/预算注入）需标定 300/1500/2500ms 三窗口仅 ~2x 余量且每次熔断走 10s 重连/10s ready 的进程重建，脆且慢，不推荐。
- GitHub 端到端仪器化参考评分（复用仓内 `PdfRenderer` 回读约定，不引第三方）。

## 3. 实施轨 + 集成 + 设备 + 证据

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 门禁工单给定 / 报告 XML 读值 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **判据以工单内联交付（未落独立文档）；本轨零 Gradle/零 adb install/instrument/零 git 写** | §3.1 |
> | T2 keep 规则（R8keep） | 完成 | **两文件两规则纯追加 54 行（0 删改）；门脚本判定无需更新；K4 字面 grep 发现登记归裁** | §3.2 |
> | T3 边界 | **部分** | **A-10 四常量+执行器+协调器四道门+JVM 5 例+A-11 15 例+A-12 7 例落码；"耗尽→文字降级"编排半边登记未落** | §3.3 |
> | T4 V-01 实施 | 完成 | **V01 套件 4 例 + P-12③ seam + 隔离套件 14→15；任务三登记不落码；套件语义缺口移交裁定** | §3.4 |
> | T5 台账 | 完成 | **补记 9 + KD-82 Progress（后经复核更正）；R-04 未代裁（按纪律正确）；契约 :208 不落笔** | §3.5 |
> | T6 KD-72/73/77 | 完成 | **KD-72 不可行维持；KD-73 兼容证据脚本化+全图扫描 PASS；KD-77 API 23 冒烟全绿、关闭不代裁** | §3.6 |
> | T7 条件轨 | 完成 | **分支 D，九项仍 pending，零改动** | §3.7 |
> | 集成交接 | 完成 | **无需接线，零文件改动；11 文件 import 审计 DANGLING=0；零广告/零 schema 双确认** | §3.8 |
> | 设备轨 | 完成 | **18 套件全绿；step19 V-01 FAIL 2/4、step20 A-10 PARTIAL 2/3（均红例归因本批夹具）；R-04 K1–K4 PASS、K5 部分、K6 无执行证据** | §4 |
> | 证据轨 | 完成 | **7 份 .md（52,370 B）；回链检查 5/7——记录回链由本文件完成、KD Progress 引用由本记录员写入** | §4.6 |
> | 复核/修复环 | 完成 | **非低 7 条 + 低 N 条全数独立复核；修复环第 1 轮 1 处处置（KD-82 证据句）** | §5 |

### 3.1 T1 设备判据 —— **工单内联交付；本轨零执行**

- 判据全文在工单（§0 现树事实 / §一 R-04 K1–K6 重跑判据（K6 新期望=扩 keep 后两例绿+三类签名 0）/ §二 V-01 与 A-10~A-12 的设备判据（若落码）/ §三 15 套件不回退清单（18 步 + 条件新增 step19-v01-chain、step20-A-10）/ §四 重建重装硬门与源快照门（含 core/model/src；R-04 窗口另取 keep 三文件 sha））。
- **落码形态双分支核对（本轨实跑）**：开工时点两 `.pro` 均零 keep 落（git 干净）→ 判据的 R1/R2 分支届时由实现轨择一；本记录时点 R1 形态已落（§3.2）。
- **本轨纪律遵守**：零 Gradle、零 `adb install/instrument`、零 git 写；实跑仅只读 python 门 ×2（S=71 静态比对、keep 门）、grep 计数、`git status`、`ls`、`adb devices -l`。18 套件现树计数开工复读 = 批次 35 重绑轮逐套件一致（step14 当时 14，P-12③ 落地后现树 15）。
- **登记**：①判据未落独立文档（与批次 35 的 573 行判据文档对照，交付形态偏离）；②ask 末尾"串行执行"一–五未由 T1 执行（设备执行归设备轨，T1 为前置判据轨）；③5566 未在 ask 设备清单、5596 属 T6 API 23 专用——可用 serial 归属按各轨任务指派执行，张力登记。

### 3.2 T2 keep 规则（R8keep）—— **两文件两规则落树；门脚本无需更新**

- **规则 1**（`app/r04-test-keep.pro:42-74`，纯追加 +34）：`-keep class j$.** { *; }`（`:74`）。消灭的具体失败 = `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（K6 签名）。注释 33 行含：机制（两次独立 R8 无改名协调 + 合并 classloader 测试 dex 在前）；同名碰撞对的一手 mapping 行号（主 `:871393` DesugarCollections$SynchronizedMap vs 测试 `:49004` Comparators$NaturalOrderComparator；同源类主包 → `j$.util.e` 为无协调直接证据）；为何全域 `j$.**`（碰撞与包无关、`j$` 是 R8 保留前缀不属于生产混淆面）；为何 `{ *; }`（无成员块的 -keep 只保类+无参构造器，NoSuchMethodError 会挪到别的成员）；为何刻意不镜像进生产文件（生产 dex 膨胀换不来确定性）；指认另一半（tracing）在本文件结构性修不了。
- **规则 2**（`app/proguard-rules.pro:21-39`，纯追加 +20；规则本体 `:35-39`）：`-keep class androidx.tracing.Trace { void beginSection(java.lang.String); void endSection(); void forceEnableAppTracing(); }`。消灭的具体失败 = `NoClassDefFoundError: Landroidx/tracing/Trace;` + `ClassNotFoundException: androidx.tracing.Trace`（K6 签名）。机理 = androidx.test 按名引用（测试 dex 30 处、恰三签名）+ AGP 把共享依赖排除出测试 APK + 主包 R8 整类内联删除 → 合并 classloader 里两 APK 都没这类；keep 不能造不在 R8 输入里的类 → 唯一落点主包保类（R1 §三.2/§四.2 结构性修正）；成员集 = 测试 dex 实测引用的恰三个签名；生产代价 = 约 1KB 门面类不被内联，其余混淆面不动。
- **keep 门脚本判定无需更新**（证据）：门口径（docstring `:14-23`：仅 `-dontobfuscate`、剥注释、按文件 0 命中）与新规则集正交；`DEFAULT_RULE_FILES` 两文件不变、`app/build.gradle.kts` 接线未动；改后实跑门 OK/OK、EXIT=0（本会话复跑同）、门自测 12 passed、负控仍红 exit 1（轨实跑）。未新增"规则在场"检查：按"新增必须指认消灭的失败"，规则被删已由强制 K1–K6 重跑 + 两条 mapping 预检兜住，文本在场 grep 反而加假信心。
- **任务四**：两文件无 `-dontobfuscate`（权威门 0 命中 EXIT=0 实跑证实）；未引 `-dontobfuscate`、未动判据、未动 internal。
- **牵动面闭合**：`app/build.gradle.kts` 三 minified 变体共用 `proguard-rules.pro` → tracing 规则同样作用于 benchmark/internal（与既有 Room/ML Kit keep 的"规则集与 release 对齐"模式一致）；`tools/tests/test_r04_keep_rules.py` 真树断言只查 exit 0 + 文件存在；仓内无其他文件引用这两个 `.pro`（grep 实证）；lockfile/依赖图零变化（keep 只影响 R8 收缩）。
- **发现（归他线/既有，非本批引入）**：批次 35 判据 K4 字面命令 `grep -cE "dontobfuscate|dontshrink|dontoptimize"` 在 HEAD 即不可满足——`app/r04-test-keep.pro:17`（批次 35 头注释自述门）注释里含 `-dontobfuscate` 字样，HEAD 计数 = 1/0，本次改动前后计数不变。权威 0 命中口径是注释感知的 python 门（EXIT=0）。**判据口径裁定项归主会话**：建议把 K4 重引为注释剥离版或直接以 python 门作 K4；不为凑字面 grep 改历史注释。

### 3.3 T3 边界（A-10/A-11/A-12）—— **落码完成（工单状态"部分"的"部分"=编排半边未落）**

- **A-10 四项缺口常量落 `core/model`**：`FigureGenerationPolicy.kt:434-446` 新增 `object FigureGenerationLocalJsBudget`（`MAX_CUMULATIVE_SOURCE_UTF8_BYTES=128*1024`、`MAX_LAYOUT_ATTEMPTS=6`、`MAX_CUMULATIVE_WALL_CLOCK_MILLIS=10_000L`、`MAX_FINAL_FIGURES_PER_OPERATION=2`；本会话实读逐值核对）。
- **新执行器** `core/data/.../visual/FigureOperationLocalBudgets.kt`（?? 新文件；per-operation 进程内累计器，键窗口 256 上界 `:131`）+ **协调器四道门**（`DefaultFigureGenerationCoordinator.kt` +49；REJECTED：未执行、未预留、不建 attempt 行——属设计，诊断有界可审计）。
- **JVM 5 例**（`FigureGenerationLocalBudgetBoundaryTest.kt`，?? 新文件）：128 KiB 恰线放行/+1B 拒双向、第 7 次布局尝试拒（executeCount 恒 6）、累计墙钟 10 s 拒（`>=` 判下一次，比计划原文">10s 拒"更严）、第 3 份最终图拒（IMAGE+JS 两路，引擎前/后端前两形态）。
- **512 KiB 精确边界 3 例**（`OpenAiSseToolCallAggregationTest.kt` +95，M）：恰 512KiB 收、+1B 拒、非流式同门；既有只钉超线 16 字节。
- **设备真库 3 例**（`FigureDispatchBudgetBoundaryInstrumentedTest.kt`，?? 新文件）：真 Room 库 + 生产 `RoomFigureDispatchLedger`——第 7 次派发拒（带真实计数）、换 method 计数单调 1..6 不重置、终答保留位（该例红，§4.4）。
- **A-11 15 例**（`ModelWireProtocolSourceJsFidelityTest.kt`，?? 新文件）：3 协议 × 5 方法（anthropic*/gemini*/responses*），每方法内部覆盖 7 向量（清单 `:199` 行义口径：4 文本向量 UTF-8 逐字节 + 空 content=strict schema 构造层拒绝 + 截断=终帧声明进重建体 + `IncompleteModelOutputException` + 取消=整条拒 + 空 delta 帧重建保真）；请求半边（visualContext→prompt 载体）在树未接线，如实登记。
- **A-12 7 例**（`ModelTaskVisualContextGoldenTest.kt`，?? 新文件）：v17 行 Plan/Respond 两半 decode→re-encode 逐字节往返、逻辑投影 SHA-256 字节 golden、非空 context 精确键形状、inner-null 形状立碑、假稳定防伪；既有 `ModelTaskFingerprintStabilityTest` 42 例未动。
- **先红后绿**：四道协调器门+常量的红是结构性的（常量落树前向量编译不过/断言红）；512KiB 精确边界、第 7 次派发、换 method、终答保留位是既有语义的新增覆盖，从未被观察到红——不虚报。本轨零 Gradle，红/绿观察 UNVERIFIED→ 由批次构建轮执行（E-05 报告读值）。
- **登记（"部分"的来源）**：A-10"耗尽→文字降级"的**编排半边**（RoomTutorToolRunner 轮次协议：预算耗尽后用原派发余额终答）未落——归属归主会话裁定（研究 §2.2 标注）；跨 method 合计最终图（同 operationId 先 JS 1 份 + 再 IMAGE 1 份 → 第 3 份拒）无直接协调器级测试（复核 #14，覆盖缺口非放宽）。
- **工单摘要转抄漂移**：T3 自述 A-12"6 例"，现树/E-05/本会话 grep 均为 **7** 例——以现树为准，登记。

### 3.4 T4 V-01 实施 —— **套件落码 + P-12③ seam；语义缺口移交**

- **V01 套件**（`core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt`，?? 新文件，4 例，本会话 `grep -c '@Test'`=4）：全部走测试内装配（工厂 4/5 参既有公共缝，`DefaultFigureGenerationCoordinator.kt:810-827`），不接生产装配点。① 生成腿 + 0 Provider 派发四断言位（审计 0/0、attempt `method='JS' AND reservation_id IS NULL`、账本零触碰、产物恒 Program）+ `networkRequestsAllowed=false` + 只抛异常配置门 tripwire；② 生产装配负控（不注入引擎 → UNAVAILABLE 且零审计行，钉住"生产三参恒 UNAVAILABLE"边界）；③ 冻结重放（保存→`store.close()` 同名重开→port 读→测试内映射→`VisualSnapshotRestore.restore` 得 Replay + 重绘栅格逐位相等）；④ PDF（`VisualDrawOpsPdfPainter.writeFile` + `PdfWriteIntegrity` + `PdfRenderer` 回读）。
- **度量降级登记**：生产 `AndroidVisualContentMeasurer` 在 app 模块不可达，测试内 `DeviceTextMeasurer` 按同构口径（flags=REPLAY_TEXT_PAINT_FLAGS=0、textSize=sizeSp×density×fontScale）做真机真实文本度量，两处已知差异写入 KDoc。
- **P-12③（R2 §3.3 方案 B）**：`VisualJsClientEngine.kt:1201` 增 `internal fun fuseDigestForTest(digest: String)`（+20 纯追加，KDoc `:1185-1199` 逐条给消灭的失败/digest 口径/回滚；本会话实读锚点）；隔离套件新增 `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse`：预置 digest → 无重试 `PROGRAM_FUSED`（检查位真路径）→ `explicitUserRetry=true` 真渲染（真 `:visual_js` 进程）→ 解除 → 再无重试 `Rendered`（解除持久）。隔离套件 14→15 例（本会话 grep=15），既有 `PROGRAM_FUSED` 基线用例未动（基线断言现树 `:372`，批次 35 时点值 `:367`——+5 行漂移，三 `explicitUserRetry=true` 调用点现树 `:377/:381/:392`，本会话 grep 实证）。
- **任务三（登记，不落码）**：M-04 案 A 维持案 B（§1.1 偏差 5）；P-11 errorKind 半边登记（§1.1 偏差 4）。
- **任务四（seam 来源与回滚）**：S1 工厂注入缝=既有公共缝（回滚=删测试文件）；S2 `fuseDigestForTest`=本批新增（回滚=删该 fun+唯一消费者即完整回退，`fusedDigests` 与生产路径零改动）；S3 测试内件全在测试文件内。**R-04 关联移交项**：`fuseDigestForTest` 为 internal 成员，现 `r04-test-keep.pro` 无任何 test-seam 成员 keep——隔离套件若进 release 轨套件单，按名引用的 internal 成员须逐条补 keep 并给理由，否则明确套件单不进 release 轨（套件单不在树 UNVERIFIED；r04-test-keep.pro 归 T2 活动文件，为避撞车未由本轨编辑）。

### 3.5 T5 台账 —— **补记 9 + KD-82；R-04 未代裁（按纪律正确）**

- 准入清单 v3 补记 9：①计划 §0"A-04~A-06 行仍写未落"过时（v3 补记 8 已随 `492b349e` 在树）→ 只按现树复核并登记，不重复刷新；A-05 行锚点精化 `:958-963`→`:964-965`（现状格+证据格两处；本会话实读 `RoomModelTaskRepository.kt:965`=`jsMeasurementReady=false` 显式透传成立）；② KD-82 按证据更新（本批零动作、锚点全在树、状态保持 open）+ known-defects KD-82 节追加批次 36 Progress 块（后经复核更正一处失实证据句，§5.0）；③ R-04 **未作裁决**（按纪律正确）：keep 规则当时未落、K1–K6 重跑未发生、evidence 无 batch-36 目录 → 登记"裁决待设备轨证据（K6 判据原文不变），由记录员回链"——**该回链由本文件完成**（§4.5 + 清单 v3 补记 10）；④ 契约 `:208`：docs/current 热区未清空（7 条 M）→ 不落笔，登记过时句原文与热区文件清单。
- 改动仅两个目标文件（开工均干净；checklist 3 行改、known-defects +2 行），提交时整文件纳入。

### 3.6 T6 KD-72/73/77 —— **三态：不可行维持 / 脚本化收口 / 冒烟全绿不代裁**

- **KD-72（锁窗口复核）**：W1–W7 全部实跑，结论**分支 B（不可行）**维持——W1 六文件 6/6 不满足（5 M + `?? core/markdown/build.gradle.kts`；J1b-md/J1b-include=MISSING、J1c 两门 RED）；W2 树未收口；W3 暂存无锁（满足）；W4 跟踪锁 0 / 盘上 14 / git 可见 13；W5 `:benchmark` 缺锁；W6 负控从未执行（需 Gradle 解析期红，禁跑）；W7 Gradle 环境预检归门禁轨。J4 前半两门实跑 exit 0（`check_lockfiles.py` skip 模式 + `check_dependency_pins.py`；记录员本会话复跑同）。KD-72 保持 open。
- **KD-73（stdlib 兼容证据脚本化）**：新增 `tools/ci/check_stdlib_compat.py` + `tools/tests/test_check_stdlib_compat.py`（先红后绿：实现前 ImportError → 实现后 18/18 OK；记录员复跑 18 tests OK）。真实全图扫描两轮结论一致（记录员复跑同值）：图 = 340 坐标/13 个盘上锁文件；扫描 329/340 构件；唯一 kotlin/* 引用 **23,847** 条 → fail-class 0、fail-member 0（**OK，0 unresolved**，exit 0）；分桶 resolved 1,210 / resolved-secondary 19,842（owner 由图内其它 jar 声明，信息级）/ non-stdlib 2,795（信息级）；metadata 腿复现批次 30 口径（全图唯一声明 stdlib 2.4.10 的坐标 = `quickjs-kt-android:1.0.15`）。语义注记：解析图 = 盘上未跟踪锁（脏树产物）非 HEAD 重解析（脚本每次打印 NOTE）；11 个无构件坐标未扫描（6 BOM、kotlin-stdlib-common klib 容器、4 个 jb-compose KMP 空重定向）；遮蔽语义 fail-closed（有单测钉住）。批次 30 T7 自述的 10.3 万 class/138,225 条引用仍无可复跑产物（UNVERIFIED 不变），其结论方向现由本可复跑门支持（口径不直接可比）。**KD-73 保持 open：静态半边已可复跑成立，"设备/R8 冒烟无 stdlib 归因崩溃"半边本批未做。**
- **KD-77（API 23 冒烟）**：最小冒烟全绿——镜像可启动（emulator-5596 = `api23_smoke64.avd`，system-images/android-23/default/x86_64，Android 6.0/SDK 23）；修复后 APK = 批次 35 绿绑定包（宿主 sha256 `1593b25ae33d10dc…` 与批次 35 证据 `step-device-track.md:69/:127` 设备侧记录一致）；清卸 2 旧包 Success×2 → `adb install -r -t` Success（INSTALL_EXIT=0；设备 firstInstallTime 2026-10-29 22:52:44 UTC = 本轮安装时刻，干净装机）→ `am start -W` Status ok / TotalTime 1611ms → 全量 logcat（416 行）四类签名 FATAL EXCEPTION/ANR/NoSuchMethodError/NoClassDefFoundError 各 **0 hits**（UnsatisfiedLinkError、任意 FATAL 亦 0）。证据落盘 `tmp/kd77-api23-smoke/00–05`（记录员实读 01/02/04/05 逐值同上；`tmp/` 不在 .gitignore——T6 摘要"gitignored"表述不精确，已勘正）。**关闭裁决不代裁**：最小闭环已齐，但 KB 线跨账本分歧（kb-batch25:401 自称已关）待主会话裁定，且批次 35 口径"API 23 项不得标绿"——仅交证据与命令，处置归台账 owner；冒烟未含 `am instrument` 全套件（超出"最小冒烟"范围）。

### 3.7 T7 条件轨 —— **分支 D，九项仍 pending，零改动**

- 开工再核热区布尔与指派一致（feature/library 30 M+13 ??、feature/tutor 37 M+22 ??、core/ui 11 M+24 ??）；按批次 24 R4 既有九项序位逐项只读复核：**九项全部仍 pending、载体均在、声明热区目标文件均 M**（G2 AI 标识 grep 0 命中、G1 core/ui/AiGeneratedBadge.kt 不存在、AWAITING 缺口行 `ExplicitRedrawService.kt:431`/`:267` 仍在、5b-1/KD-68 `:2652` open、AUD-08 收口半边待 feature/capture、6B-3 `paperEditAssistant` app 0 命中、5b-2 F8/F9/F18 裁定未见、阶段6 三文件均 M、5c `MistakeDetailRoute.kt:692` 仍自由文本）。未新建 KD/条目/文件。
- 漂移复核：`app/r04-test-keep.pro` 现 M（本批 T2 keep 扩充在飞，非本轨）；`ui-queue-sequencing.md:46` 仍写"schema 70（预计；当前 69）"而现树 71（批次 34 原登记仍有效不重复建）；新观察一处——WeaknessRow 自 `LearningMasteryScreen.kt` 移至 `feature/profile/ProfileRoute.kt`（阶段6 pending 判定不受影响，仅登记）。

### 3.8 集成交接 —— **无需接线，本轨零文件改动**

- 三处候选接线点逐一核实均由各轨自闭环：① T2 keep 门脚本口径与新规则集正交（不误报、负控红）；② T3 边界常量链闭合（`FigureGenerationLocalJsBudget` object → `FigureOperationLocalBudgets.kt:3` 唯一跨模块 import、四常量全被消费 `:58/:71/:78/:102`、协调器同包引用+新构造参默认值、23 处构造点对新默认参零改动——两生产构造点 `ConfiguredCleanImageGenerator.kt:71-75`/`TutorFigureGenerator.kt:311-315` 实读全命名参数）；③ T4 V-01 seam 自闭环（`fuseDigestForTest` 消费端隔离测试 `:429`、digest 口径同源、同模块 internal 经 friend-module 可见、V01 同包符号全存在）。
- **import 无悬空审计**：11 文件（T3 8 + T4 3）com.tingyun.* import 逐条解析到仓内声明（DANGLING=0）；外部 import 155 条全有 tracked 文件先例——静态证据非编译证明（禁 Gradle）。
- **零广告/零 schema 双确认**：本批 M 文件 diff 广告面词 0 命中；S=71 静态比对 PASS（本会话实跑）；`git diff` 无 `StudyDatabase.kt`/schemas 变化；core/database 的 M 文件为 FTS/导入他线，非 schema 载体。
- **登记**：`tmp/` 实际不在 `.gitignore`（`check-ignore` exit 1；只有 `.tmp/`、`tmp_*.py`、`.superdesign/tmp/`）——T6 摘要称"gitignored"不精确，"不提交"是纪律惯例非 ignore 规则。

## 4. 设备与证据

> 环境：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096 / fp `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys`）；执行窗 **08:17–08:59**（20 步 + R-04 K1–K4）；落盘会话 22:35–22:5x 只读复核 + keep 门复跑（零 Gradle、零 `am instrument`、零设备写）。设备时钟较宿主约 −8h，本目录只写宿主窗。

### 4.1 产物身份（00-index §2）

| 轮/用途 | 产物 | 字节 | sha256 |
|---|---|---:|---|
| 设备轨 debug | `app-strictOffline-debug.apk` | — | `4e964e599754759e…` |
| 设备轨 debug | `app-strictOffline-debug-androidTest.apk` | — | `1c35a969a766645f…` |
| 设备轨 debug | `data-debug-androidTest.apk` | — | `269e2a9a0fb750e2…` |
| 设备轨 debug | `database-debug-androidTest.apk` | — | `6af00fa1190db4dd…` |
| R-04 release（08:57） | `app-strictOffline-release.apk` | 155,749,569 | `1c7fe3f7892efb43…` |
| R-04 release（08:59） | `app-strictOffline-release-androidTest.apk` | 1,118,864 | `03d1d426d0cd0834…` |
| asset 门 | `71.json`（data 测试包内） | — | `4a13d9d0b5ee561f…`（= 现树 schema 同源） |

keep 三文件（R-04 窗口 08:17 与 08:59 两读逐字相同）：`r04-test-keep.pro` 40→74 行（sha `35cd987f…`）、`proguard-rules.pro` 19→39 行（sha `38901b59…`）、`build.gradle.kts` 未改（sha `1bcc0247…`）→ 08:57/08:59 两包确实用**扩充后规则**构建（复核轨重算全等）。

### 4.2 重建重装硬门（E-00 §3 / E-04 §3）

源快照 before/after（find 集 `app/src core/model/src core/data/src core/database/src core/domain/src`）diff **空**（`r04-src-sha-diff.txt` 0 B）；**1548 文件对现树逐文件 sha256 全等**（本会话 `sha256sum -c … --quiet` EXIT=0 实跑）→ 设备轨 20 步 + K1–K4 所绑定的源与当前工作树一致。R-04 窗口 keep 三 sha before/after2 逐字相同。dex 符号门三测试包全过（`dex-gate-result.txt`）；asset 门 `71.json` sha 同表。DB schema 71。

### 4.3 18 套件全绿（E-01）

| step | 套件 | 设备结果 | 源 @Test（本会话抽核） |
|---|---|---|---|
| 01 | 备份 | OK (34 tests) | 34 ✓ |
| 02 | rowCounts | OK (2 tests) | 2 |
| 03 | 结构门 | OK (5 tests) | 5 |
| 04 | 裁剪器 | OK (9 tests) | 9 |
| 05 | C4 视觉四表 | OK (5 tests) | 5 |
| 06 | 迁移矩阵 1→71 | OK (15 tests) | 15 ✓ |
| 07 | 69→70 | OK (4 tests) | 4 |
| 08 | 70→71 | OK (3 tests) | 3 |
| 09 | 提交 | OK (12 tests) | 12 |
| 10 | 仓库 | OK (12 tests) | 12 |
| 11 | 一致门（原型） | OK (16 tests) | 16 |
| 12 | painter | OK (24 tests) | 24 |
| 13 | PDF | OK (16 tests) | 16 |
| 14 | 隔离（14→15：P-12③ 例 `:406` 起） | OK (15 tests) | **15** ✓ |
| 15 | flags | OK (2 tests) | 2 |
| 16 | 本地 attempt 负序号 | OK (1 test) | 1 |
| 17 | 对账 JS 区分 | OK (6 tests) | 6 |
| 18 | 引擎邻居门 | OK (27 tests) | 27 ✓ |

- **跳过余量 0**（20 份 step*.log 的 ignored/skipped/assumption grep 0 命中）；**9 类崩溃签名 20/20 份 logcat 全 0**（E-01 §3 本会话转引证据轨逐文件计数）。
- step19/step20 不在本 E-ID（红/部分红，不并入本 PASS，§4.4）。

### 4.4 step19 V-01（FAIL 2/4）与 step20 A-10（PARTIAL 2/3）（E-02/E-03）

- **step19**：`Tests run: 4, Failures: 2`。通过=生成腿（0 派发四断言位全钉）+ 生产装配负控；失败=冻结重放例与 PDF 例——同因：`runChainToRestoredReplay` 复用同一 `DeviceTextMeasurer`（`:411`）并让程序执行两次（协调器 `generate` `:421` + 取 drawOps `engine.execute` `:432`），度量器每次 `measure` 追加（`:636`）→ `measured` 2 条 → `labelResolver` 的 `requirePresent(measured.singleOrNull())`（`:550`，定义 `:898`）抛 `IllegalStateException: one label entry must be measured`。**归因=本批新增用例夹具缺陷**（与"整链只度量一次"假设不一致）；断言未放宽、未删例。修复后须新增 `step02b-*` 复跑（不覆盖旧文件）。
- **语义底线未满足（独立于红绿）**：套件注入 `core/data` 进程内 `QuickJsVisualExecutionEngine`（`:667-668`），非 app 侧隔离 `VisualJsClientEngine`（`VisualJsClientEngine.kt:100`）；对 `com.tingyun.smartmistakebook.visualjs` import 计数 0 → 判据 §2.1①"真隔离进程"即使转绿也未满足，**移交主会话裁定**（套件迁 app 模块用真隔离引擎，或明示降级口径）。
- **step20**：`Tests run: 3, Failures: 1`。通过=第 7 次派发拒（`BUDGET_EXHAUSTED` 带真实计数）+ 换 method 不重置；失败=终答保留位——夹具 `seedModelTask` 走 `createModelTask` 落库初态 `WAITING_FOR_MODEL`（`ModelTaskTransactionDao.kt:204`），而 `ReserveModelTaskRemoteDispatchCommand` 前置守卫要求 `expectedStatus == QUEUED`（`ModelTaskDatabasePort.kt:52-54`，本会话实读）→ `IllegalArgumentException: Only a queued model task may reserve a remote dispatch`。**归因=夹具状态前置**，非预算语义被证伪；**"耗尽→终答位保留"子门未获证据**，不得以两例通过代替。修复后须新增 `step03b-*` 复跑。

### 4.5 R-04 K1–K6（E-04）—— **K1–K4 绿；K5 未按判据；K6 无执行证据 → 阻塞**

| K | 结果 | 关键证据 |
|---|---|---|
| K1 红基线 | **PASS** | release 主包 + debug 测试包：`r04-step1-red.log:2` `Permission Denial … does not have a signature matching the target`；九类签名 0（keep 扩充不改 K1 红形态，与判据预期一致） |
| K2 release 测试轨构建 | **PASS** | `-Psmbk.testBuildType=release` → `r04-step2-build.log:543` `BUILD SUCCESSFUL in 56s`、末行 `K2_BUILD_EXIT=0` |
| K3 证书一致 | **PASS** | 两包 `certificate SHA-256 digest: bb3e8dee8c1c08ad…` 逐字相同（`r04-k3-main.txt:2`/`r04-k3-test.txt:2`）；未重签 |
| K4 keep 门 0 命中 | **PASS**（脚本权威） | `r04-k4-keepgate.txt` 三行 OK；落盘会话与本记录员复跑 `python tools/ci/check_r04_keep_rules.py` 均 OK/OK、EXIT=0 |
| K5 so 双 ABI + 测试类未改名 | **未按判据执行** | 批内无 `r04-k5b-count.txt`；本会话只读补查 so 半边（`lib/arm64-v8a/libquickjs.so` 924,936 B + `lib/x86_64/libquickjs.so` 911,248B）；**dex 计数未跑** |
| K6 设备两例往返 | **无执行证据（无产物）** | `find build/device-track-batch36 \( -iname '*k6*' -o -iname '*k5*' \)` → 0；K6 最近一次实测仍是批次 35 红（`build/device-track-batch35/r04-k6-logcat.txt:86/:92/:110`） |

- **R-04 最终裁决（E-04 §5，判据原文照抄不放宽）**：**不关闭；按"阻塞"登记（K6 无执行证据）。关闭建议不成立**——"规则写了"≠"K6 绿了"；也不能反过来判"红"（无新红证据）。准确状态：**K1–K4 绿、K5 未按判据执行、K6 无执行证据 → R-04 阻塞（UNVERIFIED）**。
- 解除条件（不放宽）：按 `docs/research/2026-10-30-r04-k6-keep-rules.md:134` 命令在 5562 装两包跑 K6 两例，取 `OK (2 tests)`（两方法名都在 `-r` 流）+ logcat 三类签名 grep=0，并补 K5 dex 计数（`apkanalyzer dex` 计 `VisualJsIsolationInstrumentedTest` ≥1）；**新增两条产物级预检**（主 mapping 仍含 `DesugarCollections$SynchronizedMap -> j$.util.g`；测试 mapping ` -> j$.util.g:` =0；主包 dex 按原名存在 `androidx/tracing/Trace` ≥1）任一不满足不上设备。
- 明确禁止：删例、换 internal 变体、加 `-dontobfuscate`、以 K1–K4 绿 + 机制分析替代 K6 设备往返。
- **产物级预检实测（记录员本会话写盘前，对 08:57/08:59 窗口产物对实跑；命令见 §10）**：① 主 mapping `DesugarCollections$SynchronizedMap -> j$.util.g` = **1 命中**（判据预期"仍含"→ 满足）；② **测试 mapping ` -> j$.util.g:` = 1 命中（判据预期 0）→ 预检②不满足**——`j$.util.Comparators$NaturalOrderComparator -> j$.util.g:` 仍在，与主 mapping `SynchronizedMap -> j$.util.g` 的名称碰撞在当前产物对中**依旧存在**；③ 主包 `classes.dex` 按名含 `androidx/tracing/Trace` = **1**（判据 ≥1 → 满足；批次 35 主 dex 为 0 定义）→ **tracing 半边的 keep 已真实落类**（precheck ③ 过）。**判定含义**：keep 规则确已进 R8 配置（test `configuration.txt:75` 含 `-keep class j$.** { *; }`；两 `.pro` sha 与窗口记录逐字同 `35cd987f…`/`38901b59…`），但 j$ 改名仅**部分**让名——`j$.time.Clock -> j$.time.Clock` 恒等，而 `j$.sun.misc.DesugarUnsafe -> j$.sun.misc.a`、`j$.util.DesugarCollections -> j$.util.i`、`Comparators$NaturalOrderComparator -> j$.util.g` 仍改名 → **R1 §4.1"测试 dex 全量让名"的预测被当前产物证据否定**；为何 keep 对 j$ 类只部分生效（推手假设=R8 desugared-library 合成类对 keep 匹配的豁免，未读 R8 源码证实）须查清。按 T1 判据"任一不满足→不上设备，先查 keep 真落/真生效"，**K6 在预检②满足前不得上设备**。这不改变 E-04 的"阻塞"裁决，反而把阻塞从"无执行证据"加重为"预检实测不满足 + 无执行证据"；机制①（j$ 碰撞）的解锁路径需重估（applymapping 改名协调 / slackhq-Keeper / 主包侧保名等——均超出本轮范围，归主会话裁定，且本研究 §八未验证项第 1 条"修复的实际效果"自此有了否定性部分证据）。
- 步 5 回归（无属性构建回落 debug）与步 6 恢复 debug 态：本批 R-04 窗口内未发生（窗口止于 K4）——恢复 debug 态半边亦未执行，登记。一次性 keystore 与口令载体在 `build/`（不入仓，不记录值）；生产签名 UNVERIFIED。

### 4.6 证据轨与回链状态

- `docs/research/evidence/2026-10-30-batch-36/` **7 个 `.md`**（`00-index` + `step00-environment` + `step01-suite-regression` + `step02-v01-chain` + `step03-a10-budget-device` + `step04-r04-k1-k6` + `step05-jvm-boundaries`），52,370 B（本会话复算一致）；E-ID E-00…E-05 全批唯一；`build/` 只以 raw-log 身份出现。
- 回链检查：脱敏 0（home paths 唯一命中=扫描正则自身，人工排除）、体量、E-ID 唯一、链接检查（`test -f`+`wc -l` 逐条无 MISSING）均通过；**两条未勾项**——"记录 §4 回链"由本文件完成（本节全部以 E-ID + path:line 引用）；"KD Progress 引用本索引"由本记录员写入 known-defects 批次 36 Progress 块（§附录 A；R-04 未关闭，KD 台账未写任何 closed）。
- **JVM 半边（E-05）**：四套件 5/15/7/42 全过、0 失败 0 跳过——数字读自批次构建轮 JUnit XML（07:56–07:59；**执行者非落盘会话**，Gradle 记录员未复跑）；报告对现树成立依赖 E-00 的 1548 文件快照全等（本会话复核 EXIT=0）。

## 5. 复核发现与处置

### 5.0 修复环（工单给定）

- 第 1 轮非低 7 条复核发现中，1 条需仓库改动并已处置：**KD-82 Progress 段失实证据句更正**（`docs/known-defects.md:3149` 区域，1 句替换——原句"锚点文件三路径 git 干净"与现树不符：`FigureGenerationPolicy.kt` 为 M（本批 A-10 在该文件尾部 +29，本条锚点行均在其之前、未漂移）；更正句由复核修复员写入，共享脏文件整文件纳入）。其余 6 条为核实+移交裁定、无仓库改动（其中 V-01 措辞不准确仅存在于运行时任务摘要，无可编辑落点）。
- **门禁序列（工单给定，记录员未复跑）**：单测门第 1 轮 exit=0 → 修复环 → 修复环复跑 exit=0 → 强门（修复环后）exit=0。

### 5.1 复核轨发现（20 条，全部"已独立复核"；记录员转录，关键项本会话抽查）

| # | 级别 | 位置 | 发现 | 处置/状态 |
|---|---|---|---|---|
| 1 | 高 | `V01VisualChainInstrumentedTest.kt:118,142-149,411-421,432` | V-01 套件注入进程内引擎，不满足判据 §2.1①"真 VisualJsClientEngine 隔离进程"——即使转绿也不构成 V-01 收口 | **核实 + 移交主会话裁定**（套件迁 app 模块 or 明示降级）；不准确措辞仅在运行时摘要，无可编辑落点 |
| 2 | 中 | `step04-r04-k1-k6.md` §5；`build/device-track-batch36/` 无 k5/k6 产物 | K6 无执行证据、R-04 正确按阻塞登记不关闭；K5 仅 so 半边；批次结论必须表述为"R-04 阻塞（UNVERIFIED）" | 已独立复核（两 release sha 与 keep 窗口三 sha 重算全等 → 两包确用扩充后规则构建） |
| 3 | 中 | 清单补记 9②/R-04 行；batch-36-record 不存在 | 台账时点性漂移 + 批次记录未落盘：R-04 行注记被 T2 落码与 E-00…E-05 落盘超越；A-10/A-11/A-12 行未刷新=记录员待办 | **已处置（本记录 + v3 补记 10）** |
| 4 | 中 | `app/r04-test-keep.pro:42-74`；`app/proguard-rules.pro:21-39`；keep 门脚本 | T2 两条新增 keep 逐条必要性成立；无 -dontobfuscate 变体绕过；门真会红（负控 FAIL/EXIT=1） | 已独立复核（本会话复跑门 OK/OK、EXIT=0；`app/build.gradle.kts` 干净、无第三文件绕门） |
| 5 | 中 | `step04` `:97-105`；`00-index` `:72-80` | T2/T5 交叉：K6 无执行证据，台账未把"keep 写了"冒充"K6 绿了"（E-04 §5 原文"规则写了不等于 K6 绿了"） | 已独立复核（三文件口径一致；00-index §7 明示不得在 KD 台账写 closed） |
| 6 | 中 | `docs/known-defects.md:3145` 区域 KD-82 Progress | 证据句失实："三路径为空"与现树不符（`FigureGenerationPolicy.kt` 实为 M）；其余声明为真、open 判定不受影响 | **已处置（修复环第 1 轮更正）** |
| 7 | 中 | `step02` `:55-82`；`step03` `:54-79` | V-01 FAIL 2/4 与 A-10 PARTIAL 2/3：三关键子门未获设备证明，但无断言放宽、无删例、归因经源码核实成立（measured 累积 2 条 / QUEUED 守卫） | 已独立复核（修复后须 step02b/step03b 复跑；"耗尽→终答位保留"未证明） |
| 8 | 低 | `VisualJsIsolationInstrumentedTest.kt:423,429`；`VisualJsClientEngine.kt:1201` | P-12③ 新设备例消费 internal `fuseDigestForTest` 但 release 轨无成员 keep——K6 判据不受影响（-e class 仅两方法），整包进 release 轨将 NoSuchMethodError | **移交项**（T4/R2 已列；keep 真落归 R-04 轨/主会话裁定套件单） |
| 9 | 低 | `tmp/kd77-api23-smoke/`；`.gitignore` | T6 摘要把 tmp/ 称"gitignored"不精确：tmp/ 不在 .gitignore，"不提交"是纪律惯例 | 已独立复核（check-ignore exit 1；T6 其余证据坐实：18 tests OK、冷启 Status ok/1611ms） |
| 10 | 低 | `step05-jvm-boundaries.md:11,42` | JVM 全绿（5/15/7/42）为批次构建轮报告 XML 读值，落盘会话与复核轨均未复现执行（禁 Gradle）；S=71 全量比对无仓内脚本 | 已独立复核（快照 1548 全等绑定现树；@Test 源计数与 E-05 表逐一相等——本会话复跑同） |
| 11 | 低 | 本批全部 M/新增文件 vs 禁改清单 | 热区零越权、广告面与 schema 零改动：七文件 diff 全为纯新增（N,0）；禁改区 M 均为他线在飞内容 | 已独立复核（diff --numstat 全 (N,0)；广告词 grep=0；S=71 PASS 本会话实跑） |
| 12 | 低 | `FigureOperationLocalBudgets.kt:56-107` 等 | T3 五类阈值方向全部 fail-closed，无一处边界放宽为 <=（128KiB 恰线放行/+1B 拒、墙钟 `>=` 比计划原文更严、第 3 份在 openBackend/进引擎之前） | 已独立复核（逐行实读；JVM 结果本身未复跑） |
| 13 | 低 | `DefaultFigureGenerationCoordinator.kt:271-422` 等 | T4 V-01 派发观测点未被绕开：四独立断言位 + tripwire + 生产装配负控；`generateLocalJs` 源码级确认无任何 ledger 调用；生产装配实为三参（负控与生产现实一致） | 已独立复核（fuseDigestForTest 全仓唯一调用者是隔离测试，非生产路径） |
| 14 | 低 | `FigureGenerationLocalBudgetBoundaryTest.kt:154-212` | T3 残留覆盖缺口：跨 method 合计最终图（同一 operationId 先 JS 1 份 + 再 IMAGE 1 份 → 第 3 份拒）无直接协调器级测试（现由构造层面+设备账本层保证） | 登记在案（覆盖缺口，非放宽非绕过） |
| 15 | 低 | 清单补记 9/R-04 行；known-defects KD-77/KD-73 | T5 台账欠账：R-04 行注记落后于树与证据（回链悬置）；KD-77 冒烟通过、KD-73 新门 PASS 两项新证据无台账落点 | **已处置（本记录员：补记 10 回链 R-04；KD-72/73/77 批次 36 Progress 块）** |
| 16 | 低 | `step04` §4.1 表 | T5 低危引用偏差：tracing 规则引用为 `:20-38`/`:35-38`，实际 `:21-39`/`:35-39`（1 行偏移） | 登记在案（本记录按现树行号 `:21-39`/`:35-39` 引用） |
| 17 | 低 | 清单 `:190-198` 区域 | T5 正向核对：v3 补记 9 抽验成立（`JS !in` 0 命中、A-05 双就绪条件与透传 false 实读）；A-10/A-11/A-12/V-01 行未做行内刷新 | **已处置（v3 补记 10 四行刷新）** |
| 18 | **高** | `app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt`（08:59 K2 产物）；`configuration.txt:75` | **记录员新发现：j$ 半边的 keep 未按 R1 §4.1 预测生效**——规则已进 R8 配置但测试 mapping 仍含 `j$.util.Comparators$NaturalOrderComparator -> j$.util.g:`（与主包 `SynchronizedMap -> j$.util.g` 碰撞依旧）；j$ 仅部分让名（`j$.time.Clock` 恒等 vs `DesugarUnsafe`/`DesugarCollections` 仍改名）；tracing 半边相反已生效（主 dex 按名含 `androidx/tracing/Trace`=1，批次 35 为 0）。按判据 §5.5 预检②不过 → K6 不得上设备；R-04 阻塞加重 | **登记在案（本记录 §4.5/§10 + 台账三处同步）；机制①解锁路径重估归主会话** |

**复核轨其余结论**（全部"已独立复核"）：JVM 阈值方向、派发观测点、import 审计、零广告/零 schema、KD-77/T6 证据链、T7 只读登记——均已并入 §3/§4 对应节。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 记录员未复跑（禁 Gradle） |
| 修复环复跑 / 强门（修复环后） | **exit=0**（工单给定） | 同上；修复环第 1 轮处置 1 条（§5.0） |
| R-04 keep 规则门 | **OK/OK、EXIT=0** | 记录员本会话实跑 `python tools/ci/check_r04_keep_rules.py`（两 `.pro` 规则文本 0 命中；负控 exit 1 由 T2 轨实跑） |
| S=71 静态清单门 | **RESULT=PASS / EXIT=0** | 记录员本会话实跑（批次 34 判据 §1.4 原命令；PLAN_HASH=JSON_HASH=`714c0ecb…`） |
| kotlin-stdlib 兼容门（KD-73） | **OK，SCAN_EXIT=0** | 记录员本会话实跑：23,847 unique refs / 329 构件 / 0 unresolved；自测 18 tests OK |
| 锁门 skip 模式 + 钉版门（KD-72） | **exit 0 ×2** | 记录员本会话实跑（skip 模式明点 13 未跟踪锁 + `:benchmark` 缺锁；quickjs 1.0.15 / stdlib 2.3.21） |
| 源快照绑定 | **1548/1548 全等，EXIT=0** | 记录员本会话实跑 `sha256sum -c build/device-track-batch36/src-sha-after.txt --quiet` |
| 设备 18 套件 | **全绿，计数=源 @Test** | E-01 §3；记录员抽核 34/15/15/27 四套件源计数一致；未复跑设备 |
| 9 类崩溃签名 | 20/20 份 logcat 全 0 | E-01 §3（证据轨逐文件计数） |
| step19 V-01 | **FAIL（4 例 2 失败）** | E-02；夹具缺陷归因 + 语义底线未满足（§4.4） |
| step20 A-10 设备 | **PARTIAL（3 例 1 失败）** | E-03；夹具状态前置归因；"耗尽→终答位"未证明 |
| R-04 K1–K4 | **PASS** | E-04 §3（K4 记录员本会话复跑同绿） |
| R-04 K5/K6 | **K5 未按判据（so 半边补查）；K6 无执行证据** | E-04 §3/§5 → **阻塞（UNVERIFIED）** |
| R-04 产物级预检（记录员实测） | **①③ 满足；② 不满足** | 本会话实跑（§4.5/§10）：测试 mapping 仍含 `-> j$.util.g:` =1（预期 0）、主 dex `androidx/tracing/Trace`=1（预期 ≥1）→ K6 上设备被 §5.5 预检门拦下 |
| A-10/A-11/A-12 JVM | **5/15/7/42 全过（报告 XML 读值）** | E-05；记录员未复跑 Gradle；源计数本会话 grep 一致 |
| 生产签名 / arm64 / K6 设备往返 / V-01 隔离语义 | **UNVERIFIED** | §7 |

## 7. 遗留 / UNVERIFIED / 待裁

**R-04 与设备面**

1. **R-04 K6 阻塞未解除且加重**：keep 两规则已落树（K1–K4 绿），但 K6 两例从未在扩充后 keep 的 release 轨执行；**记录员产物级预检实测：预检②不过（测试 mapping 仍含 `Comparators$NaturalOrderComparator -> j$.util.g`，机制①名称碰撞仍在；j$ keep 已进 R8 配置却仅部分让名，R1 §4.1 预测被否定）**、预检③过（tracing 已落主包 dex）→ 按 §5.5"任一不满足不上设备"，K6 须先查清 keep 真生效问题并重估机制①解锁路径（applymapping/Keeper/主包侧保名等，归主会话裁定）；另补 K5 dex 计数；步 5 回归与步 6 恢复 debug 态半边本批未发生。
2. **V-01 语义底线**：套件迁 app 模块用真隔离引擎 or 明示降级口径——**待主会话裁定**；两红例修复后须新增 `step02b-*`/`step03b-*` 复跑，不得覆盖旧证据文件。
3. **A-10"耗尽→文字降级"**：账本半边红于夹具（未置 QUEUED）、编排半边（轮次协议）未落——归属归主会话裁定；跨 method 合计最终图无直接协调器级测试（覆盖缺口登记）。
4. **P-12③ internal seam 与 release 轨**：隔离套件进 release 轨套件单须逐条补成员 keep（`fuseDigestForTest` 等）并给理由——套件单不在树，移交项。
5. **生产签名 UNVERIFIED**（一次性本地 key）；arm64 真机面未测（x86_64 模拟器）；设备身份两处未采集（AVD 名、设备数据目录/包残留）。

**代码 / 文档残留**

6. **K4 字面 grep 口径**：批次 35 判据 K4 字面命令在 HEAD 即 1/0（`r04-test-keep.pro:17` 注释含 `-dontobfuscate` 字样）——建议主会话改口径为注释剥离版或以 python 门为准；不为凑字面 grep 改历史注释。
7. **JVM 用例执行**：A-10/A-11/A-12 的 JVM 绿 = 批次构建轮报告 XML 读值（E-05），无 Gradle 复跑；对现树成立依赖 1548 文件快照全等。
8. **KD-77 关闭裁决不代裁**：API 23 最小冒烟证据已齐（§3.6），跨账本分歧（KB 线 vs 全局台账）+ 批次 35"不得标绿"口径 → 处置归主会话/台账 owner；冒烟未含 instrument 全套件。
9. **KD-73 设备/R8 冒烟半边未做**；批次 30 T7 的 10.3 万 class 产物仍无落仓（UNVERIFIED 不变，方向已由可复跑门支持）。
10. **M-04 案 A / P-11 errorKind 半边**：维持登记不落（热区 4 M 未清空 / 无生产消费）。
11. **契约 `docs/current/visual-generation-contract.md:208`** 过时句：`docs/current` 热区 7 M 未清空 → 登记不落笔（补记 9 ③）。
12. **T1 判据未落独立文档**（工单内联交付）；判据/摘要的行号转抄漂移三处已按现树更正（A-12 7 例、隔离基线 `:372`、E-04 `:21-39`）。

**环境 / 口径**

13. **工作树计数滚动**（686→689→706→709）差值未逐项追认；他线在飞内容功能正确性归各线。
14. **`tmp/` 不在 .gitignore**（勘正）；`tmp/kd77-api23-smoke/`、`tmp/kd73-stdlib-scan/` 证据按纪律不提交。
15. **设备时钟较宿主约 −8h**；证据目录只写宿主窗。
16. **4E.2 MVA 仍不成立**：本批落的是 keep 面/纵向链用例/边界用例的代码层与门；双后端就绪、质量门、JS 广告均未宣称；一致性门（step11）仍是原型门。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前按门禁序列复跑单测门/强门并留 exit=0 日志；热区、全部 `*.lockfile`（未跟踪 13+1）、`build/`（含设备原始日志与 R-04 keystore）、`tmp/`（KD-77/KD-73 证据目录）、`docs/current/**`、`tools/kb*`、`knowledge-production/**` 一律排除；共享脏文件整文件纳入并在提交说明注明本批与他线来源。按 ≤6 路径/块：

- **块 1（R-04 keep，纯追加）**：`app/r04-test-keep.pro`（M +34；**整文件纳入**）、`app/proguard-rules.pro`（M +20；**整文件纳入**）
- **块 2（V-01 套件 + P-12③ seam）**：`core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt`（??）、`app/src/main/.../visualjs/VisualJsClientEngine.kt`（M +20；**整文件纳入**）、`app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt`（M +57；**整文件纳入**）
- **块 3（A-10 边界）**：`core/model/.../FigureGenerationPolicy.kt`（M +29；**整文件纳入：A-10 常量 + KD-82 锚点所在文件**）、`core/data/.../visual/FigureOperationLocalBudgets.kt`（??）、`core/data/.../visual/DefaultFigureGenerationCoordinator.kt`（M +49；**整文件纳入**）、`core/data/src/androidTest/.../visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`（??）
- **块 4（A-11/A-12 与 512KiB 边界 JVM）**：`core/data/src/test/.../model/wire/ModelWireProtocolSourceJsFidelityTest.kt`（??）、`core/data/src/test/.../model/OpenAiSseToolCallAggregationTest.kt`（M +95；**整文件纳入**）、`core/model/src/test/.../ModelTaskVisualContextGoldenTest.kt`（??）
- **块 5（tools/ci KD-73 门与自测）**：`tools/ci/check_stdlib_compat.py`（??）、`tools/tests/test_check_stdlib_compat.py`（??）
- **块 6（研究/基线）**：`docs/research/2026-10-30-r04-k6-keep-rules.md`（??）、`docs/research/2026-10-30-v01-chain-seams.md`（??）、`docs/research/2026-10-30-batch-36-baseline.md`（??）
- **块 7（证据 + 记录）**：`docs/research/evidence/2026-10-30-batch-36/`（7 个 `.md`）、`docs/research/2026-10-30-batch-36-record.md`（本文件，新）
- **块 8（台账，整文件纳入）**：`docs/known-defects.md`（M；KD-82 Progress+更正 + KD-72/73/77 批次 36 块）、`docs/REFACTOR-MASTER-LINE.md`（M；批次 36 滚动行）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；v3 补记 9/10）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；批次 36 节）

**明确排除（他线在飞/不安全）**：全部 `*/gradle.lockfile` 与 `settings-gradle.lockfile`；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/ui`、`core/domain`、`core/markdown`、`core/export`；core/data 他线 M（`RoomMistakeDetailRepository.kt`、`OpenAiNativeToolsProtocolTest.kt`（mastery 域 +18）、`GoldenRetrievalJvmTest.kt`、`DenseTestFixtures.kt`、`TutorFigureProposalExecutionTest.kt`、`FigureGenerationAuditRecorderTest.kt`、`VisualDrawOpsParserTest.kt` 等）；core/database 其余开工即脏 M（`RoomBatchImportStore`、`TutorConversationDatabaseContract`、DAO×3、`CleanRedrawTaskPort`、LibraryFts 三测）；app 其余 M（Root、ReviewReminder*、SecondaryScreens、SchedulingSettingsScreen、LearningMasteryScreen、ExportPdfWorker、SmartMistakeBookDestinations、StartupStateBanner 等）；`docs/current/**`；docs/research 他线未跟踪记录；`build/**`；`tmp/**`。

## 9. 收口状态

- **研究**：R1 217 行（K6 两机制一手取证/最小 keep 逐条理由/K6 重跑判据与回滚/外部 8 项评分/9 项未验证）；R2 250 行（V-01 缺 seam 实证/4 断言位/A-10 十阈值盘点/P-12③ 方案 B/GitHub 评分）落盘；未验证项如实。
- **实施**：T1 判据内联交付；T2 两文件两规则（纯追加 54 行）+ 门脚本无需更新判定；T3 A-10 四常量+执行器+四道门+A-11 15 例+A-12 7 例+512KiB 3 例（编排半边登记）；T4 V01 套件 4 例+P-12③ seam+隔离套件 15（语义缺口移交）；T5 补记 9+KD-82；T6 KD-72 不可行/KD-73 脚本化/KD-77 冒烟；T7 分支 D 零改动；集成无需接线。
- **设备**：18 套件全绿（34/2/5/9/5/15/4/3/12/12/16/24/16/**15**/2 + 1/6/27，全部=源 @Test；step14=15 含 P-12③ 例）；9 类签名 20/20 全 0；源快照 1548 文件全等；step19 V-01 FAIL 2/4、step20 A-10 PARTIAL 2/3（红例均本批夹具、断言未放宽）；R-04 K1–K4 PASS、K5 未按判据、**K6 无执行证据 → 阻塞**；记录员产物级预检实测：预检②不过（j$ 碰撞仍在、j$ keep 仅部分生效）、预检③过（tracing 已落主包 dex）→ K6 上设备被 §5.5 拦下，机制①解锁路径待重估。
- **门禁**：单测门第 1 轮/修复环复跑/强门（修复环后）exit=0（工单给定）；keep 门/S=71 门/stdlib 门/锁与钉版门记录员本会话实跑全绿。
- **台账同步（本轮记录员写入）**：`docs/known-defects.md`（KD-72/73/77 批次 36 Progress 块；KD-82 已有块+更正维持）、`docs/REFACTOR-MASTER-LINE.md`（批次 36 滚动行）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（v3 补记 10 + A-10/A-11/A-12/V-01/R-04 行回链）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 36 节）；均标注"记录员只读核对，未运行 Gradle/设备"。命令与输出见 §10 与 `docs/research/evidence/2026-10-30-batch-36/`。
- **本批不声称全绿/全就绪**：R-04 阻塞未解除（K6 无执行证据）；V-01 隔离进程语义未覆盖且落点待裁；A-10 终答位子门未证明；JS 不广告（availableMethods 未动）；生产签名/arm64 UNVERIFIED；4E.2 MVA 不成立。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse HEAD                                   # 4f11cd5432e6c259038fc92bd2e975aee8d304b4
git status --porcelain | wc -l                       # 709（多线在飞滚动）
ls docs/research/2026-10-30-batch-36-record.md       # 写盘前 No such file（本文件即该欠账）

# 双门（本会话实跑）
python tools/ci/check_r04_keep_rules.py              # OK/OK、KEEP_GATE_EXIT=0（两 .pro 规则文本 0 命中）
# S=71 静态清单门（批次 34 判据 §1.4 原命令逐字）
#   SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90 / ONLY_PLAN=[] ONLY_JSON=[] DUP=[]
#   VIEWS_MATCH=True / PLAN_VERSION=71 JSON_VERSION=71 / PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138
#   RESULT=PASS / EXIT=0

# keep 规则落点（本会话实读）
#   app/r04-test-keep.pro:74  -keep class j$.** { *; }         （74 行，git M +34 纯追加）
#   app/proguard-rules.pro:35-39  -keep class androidx.tracing.Trace { 三签名 }  （39 行，git M +20 纯追加）
#   app/build.gradle.kts git 干净
# 代码锚点（本会话 grep/实读）
#   VisualJsClientEngine.kt:1201  internal fun fuseDigestForTest(digest: String)
#   FigureGenerationPolicy.kt:434-446  FigureGenerationLocalJsBudget（128KiB/6/10_000L/2）
#   ModelTaskDatabasePort.kt:52-54  QUEUED 守卫（step20 红因）
#   VisualJsIsolationInstrumentedTest：PROGRAM_FUSED :372（基线，批次 35 时点 :367）、
#     explicitUserRetry=true :377/:381/:392、新例 KDoc :409-417（fuseDigestForTest 消费）
# 差量（本会话 git diff --numstat）
#   proguard-rules.pro +20/0、r04-test-keep.pro +34/0、VisualJsIsolationInstrumentedTest +57/0、
#   VisualJsClientEngine +20/0、DefaultFigureGenerationCoordinator +49/0、OpenAiSseToolCallAggregationTest +95/0、
#   FigureGenerationPolicy +29/0（全部纯新增）

# 源套件计数（本会话 grep -c '@Test'）
#   V01VisualChainInstrumentedTest=4 / FigureDispatchBudgetBoundaryInstrumentedTest=3 /
#   FigureGenerationLocalBudgetBoundaryTest=5 / ModelWireProtocolSourceJsFidelityTest=15 /
#   ModelTaskVisualContextGoldenTest=7 / ModelTaskFingerprintStabilityTest=42 /
#   VisualJsIsolationInstrumentedTest=15 / 备份=34 / 迁移=15 / 引擎=27（=E-01/E-05 表逐一相等）

# 源快照绑定（本会话实跑）
sha256sum -c build/device-track-batch36/src-sha-after.txt --quiet   # 无输出、EXIT=0（1548 行）
find build/device-track-batch36 -type f | wc -l                     # 64
find docs/research/evidence/2026-10-30-batch-36 -name '*.md' | wc -l  # 7（字节合计 52,370）

# KD-73/KD-72 门（本会话实跑）
PYTHONPATH=tools python -m unittest discover -s tools/tests -p "test_check_stdlib_compat.py" -t tools
                                                    # Ran 18 tests, OK
python tools/ci/check_stdlib_compat.py              # OK: 0 unresolved … 23847 unique refs（SCAN_EXIT=0）
python tools/ci/check_lockfiles.py                  # OK（skip 模式；13 未跟踪锁 + :benchmark 缺锁）
python tools/ci/check_dependency_pins.py            # OK（quickjs 1.0.15 / stdlib 2.3.21）

# KD-77 证据（本会话实读 tmp/kd77-api23-smoke/）
#   01-install.txt：Success×3 + INSTALL_EXIT=0；02-cold-start.txt：Status: ok / TotalTime: 1611
#   04-signature-scan.txt：四类签名各 0 hits；05-post-state.txt：宿主 APK sha=批次 35 绿绑定 1593b25a…

# R-04 产物级预检（本会话实跑，对 08:57/08:59 K2 窗口产物）
grep -F 'DesugarCollections$SynchronizedMap -> j$.util.g' app/build/outputs/mapping/strictOfflineRelease/mapping.txt | wc -l          # 1（预检①满足）
grep -F ' -> j$.util.g:' app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt | wc -l                              # 1（预检②不满足：预期 0）
grep -n 'j\$\.\*\*' app/build/outputs/mapping/strictOfflineReleaseAndroidTest/configuration.txt                                    # :75（规则已进 R8 配置）
grep -E '^j\$' app/build/outputs/mapping/strictOfflineReleaseAndroidTest/mapping.txt | head -5   # 混合：j$.time.Clock 恒等 / DesugarUnsafe->j$.sun.misc.a 仍改名
mkdir -p build/tmp-record-check/main-dex && unzip -o -q app/build/outputs/apk/strictOffline/release/app-strictOffline-release.apk 'classes*.dex' -d build/tmp-record-check/main-dex
grep -a -c -F 'androidx/tracing/Trace' build/tmp-record-check/main-dex/classes.dex                                                  # 1（预检③满足；批次 35 主 dex=0）

# 设备原始日志（build/device-track-batch36/，本会话只 ls/计数，未复跑设备）
#   18 套件 OK 计数与 step19/20 红例转录自 E-01…E-04（证据轨已对 raw log 逐文件计数）
#   r04-*：K1 红基线/K2 构建/K3 证书/K4 keepgate 产物在；k5/k6 产物无（find → 0）

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 commit/push/add；任何设备写。
```

## 附录 A：台账同步（本轮记录员写入）

| 文件 | 写入内容 |
|---|---|
| `docs/known-defects.md` | KD-72/KD-73/KD-77 各追加"2026-10-30 批次 36 Progress（+记录员复核）"块：KD-72 W1–W7 不可行维持（两门 exit 0 本会话复跑）；KD-73 兼容证据脚本化（check_stdlib_compat.py 23,847 refs 0 unresolved、18 tests OK；设备/R8 半边未做）；KD-77 API 23 最小冒烟全绿（5596=api23_smoke64、批次 35 绿绑定包、四签名 0）+ 关闭裁决不代裁登记。KD-82 批次 36 块与更正句已由 T5/复核修复员先行写入，本轨未改。全部状态保持 open（R-04 非 KD 条目，其裁决落清单 R-04 行 + 本记录 §4.5） |
| `docs/REFACTOR-MASTER-LINE.md` | 新增"最近计划维护：2026-10-31（批次 36 …）"滚动行；原批次 35 行降为"上次维护" |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 头注 v3 补记 10（批次 36 记录员收口）；R-04 行回链（keep 落树 + K1–K4 绿 + K5 部分 + K6 无执行证据 → 阻塞维持、解除条件不变）；A-10/A-11/A-12/V-01 四行现状按批次 36 落码与设备证据刷新——0 项转"通过" |
| `docs/agent-first-refactor-decisions-2026-09-23.md` | 追加"批次 36 纵向链 V-01 + R-04 keep 解除 + A-10~A-12 边界裁定（2026-10-30/31）"节（裁定内容 + 边界/待裁） |
| `docs/research/2026-10-30-batch-36-record.md` | 本文件（00-index §6.2 登记的记录欠账在此收口；§4 全部以 E-ID + path:line 回链） |

以上均为 docs 追加/刷新；零 Kotlin/JS/Gradle 产品代码改动、零 git 写、零 Gradle、零设备写。
