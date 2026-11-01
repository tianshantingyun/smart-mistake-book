# 批次 37 记录（2026-10-31）：V-01 真隔离引擎迁移 + R-04 K6 全链执行（K6 红=机制①阻塞）+ A-10 夹具修复 + KD-82 program 面对齐 + KD-77 关闭

> 记录时点：2026-10-31（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 写入，依据：计划（完整读，40 行）、批次 37 基线（174 行）、两研究文档（R1 `2026-10-31-v01-isolated-wiring.md` **224 行** / R2 `2026-10-31-r04-k6-verify.md` **282 行**，规模与关键锚点本会话实读核对）、T1 设备判据（518 行）、`docs/research/evidence/2026-10-31-batch-37/`（`00-index.md` v2 **180 行** + KD-77 迁移六件 00–05 + `step-device-track.md` **255 行**）、`build/device-track-batch37/` 原始产物 90 件、各轨工单摘要、复核轨发现与修复环处置，另对关键门/锚点/出数做了本会话实跑复核（§10）。**记录员未运行 Gradle、未安装/执行任何设备用例、未做任何 git 写操作、未改任何 Kotlin/JS/Gradle 产品代码**；本轮改动 = 本记录 + 三份台账（§附录 A；其中 `known-defects.md` 与准入清单为共享脏文件——开工时已 M（T5 台账轨 + 复核修复轮在飞），整文件纳入并注明，见 §8）。
> 计划：`.zcode/plans/plan-2026-10-31-batch-37-v01-isolated-r04-k6.md`（40 行，已完整读；§1 主会话裁定 6 条，§2 任务清单 9 项）。
> 基线：`docs/research/2026-10-31-batch-37-baseline.md`（174 行）。
> 研究：R1 `docs/research/2026-10-31-v01-isolated-wiring.md`（V-01 app 版真隔离引擎迁移装配研究，驱动 T2）、R2 `docs/research/2026-10-31-r04-k6-verify.md`（R-04 K6 重跑判据复核 + 产物级三值预检 + K6 归因规则 + L8 结构性机制证据，驱动设备轨 R-04 窗）。
> 判据：`docs/research/2026-10-31-batch-37-device-criteria.md`（**518 行独立文档**；批次 36 曾把"判据未落独立文档"登记为交付偏离，本批恢复批次 35 形态）。
> 设备证据：`docs/research/evidence/2026-10-31-batch-37/`（v2 索引 E-01…E-17 + E-77-01…06；`build/device-track-batch37/` 原始日志 90 文件在本机不入仓）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门（修复环后）`exit=0`；单测门复跑 `exit=0`；修复环后强门复跑 `exit=0`。KD-82 新 JVM 套件与牵动面既有套件的逐套件出数 = `build/test-results/` JUnit XML 读值（记录员本会话实读，见 §6 表与 §10；执行者=门禁串行，记录员未复跑 Gradle）。
>
> **本记录不声称"批次全绿"或"R-04 解除"**：R-04 **维持阻塞**——K1–K5 全绿（新 K2 产物、双证书、双口径 K5b 补齐），但三值预检②不过（分支 P2 → 按计划 §1.3 选项 B 全链执行），**K6 实测红**：`Process crashed before executing the test(s)`（两例 0 跑），崩溃栈 `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（declaration 在 offline.test base.apk!classes2.dex，WorkManager 启动栈），三类签名计数 **4**——按判据 §1.7 六行表第 1 行逐字对号**机制①（j$ L8 改名碰撞）**，R2 verify 证得该机制在 AGP 9.3 + desugar_jdk_libs 2.0.3 架构下**结构性不可由 `.pro` 用户规则解锁**（j$ 改名由每变体独立的 L8 编译治理，`testProguardFiles` 到不了该阶段）；关闭前置 = 主会话在两合规路径（选项 A Keeper/L8 结构解锁 vs 维持预检硬门口径）间裁定。V-01 设备绿（app 版 3/3、真隔离引擎语义满足）但**④ PDF 腿无本批设备证据（严格口径 = PARTIAL 3/4 子项）**，最终收口归主会话/台账轨；A-10 设备绿 3/3 含"耗尽→终答位保留"子门；KD-82 program 面对齐落码且 JVM 4 例 XML 出数全绿，但 **imagePrompt 残余待广告解冻批裁定 → 状态保持 open（known limitation）**；KD-77 **closed**（证据入仓 + 关闭裁决）；JS 仍不广告（`availableMethods` 未动）；生产签名/arm64 UNVERIFIED。

## 1. 范围与基线

### 1.1 计划范围与实际执行面

- **计划 9 项**（§1 裁定 6 条 + §2 任务清单）：两研究线（1 R1 V-01 装配、2 R2 K6 复核）+ T1 设备判据（3）+ T2 V-01 迁移落码（4）+ T3 A-10 夹具修复（5）+ T4 KD-82 对齐（6）+ T5 台账回填（7）+ T6 热区条件轨（8）+ 执行链（9：集成 → 双门 → 设备轨 → 证据 → 双复核 → 修复环 → 记录）。
- **实际执行面**：两研究落盘 + T1 判据独立文档 + T2/T3/T4 落码 + T5 台账 + T6 条件轨 + 集成（零接线）+ 门禁五轮（工单给定）+ 设备轨（`emulator-5562`：20 步套件 + step19 V-01 app 版 + step20b A-10 + R-04 K1–K6 + 步 5/步 6）+ 证据落盘（v2 索引）+ 复核轨（第 1 轮 7 条 + 后续复核发现全数独立复核）+ 修复环（第 1 轮处置 = 两台账文件）+ 本记录与台账同步。
- **与计划的已知偏差（八处，全部显式登记）**：
  1. **V-01 app 版 3 例（判据枚举 4 例）**：R1 §2.3-4 断言 app androidTest 可达 `VisualDrawOpsPainter`/`VisualSnapshotRestore`/`REPLAY_TEXT_PAINT_FLAGS` **被 T2 落码实读证伪**——三者均为 core:data `internal`（跨模块编译单元不可见），PDF/painter/restore 腿留 core/data 降级变体（只有那里能测）；app 链腿改为公共面不变量（DrawOps 文本+contentIds 逐字节穿越"提交→真实重开"），"度量真实发生过"的设备级证明转移到链腿 contentIds 断言（墨迹级证明留在 core/data 变体）；两侧 KDoc 互为指路。R1 :94 原句与 §5.1"4 例原样迁移"计划（:170）在案，证伪登记于 app 套件 KDoc :72-76 与设备轨 §8-2。
  2. **包名差**：判据预期 `…smartmistakebook.visual`，T2 落树 `…smartmistakebook.visualjs`（与引擎包一致——正是无 import 同包引用成立的条件，判定为正确落点；设备轨 §8-5 登记）。
  3. **源快照 1175 vs 判据 1173**：T2/T3 落码落树增量；判据 §4.1 授权设备轨重取（五模块集含 `core/model/src`）。
  4. **T4 diff 计数**：`OpenAiModelProtocol.kt` 实测 `git diff --numstat` = **+8/−1**（7 行 KDoc 注释 + 1 行改动），T4/集成申报 '+7/−1' 少计 1——复核修复轮已在清单 v3 补记 12 更正，以 +8/−1 为准（本会话实跑同值）。
  5. **T2 申报"29 条 import"与复核实测 57 条不符**（`grep -c '^import '` 实测 57：com.tingyun.* 37 + 其他 20）——申报精度问题非实质，设备编译+运行绿本身证明全部符号可达；本记录以 57 为准。
  6. **KD-77 证据六件 vs 计划"五件套"**：盘上实为 6 文件（00–05），全量迁移以 6 文件为准（基线 :10 已登记同口径差）。
  7. **K6 三值预检②不过 → 分支 P2 → 选项 B 执行**：预检②（测试 mapping ` -> j$.util.g:` 期望 0 实测 1）不过即"预检硬门拦 K6"的批次 36 口径；设备轨按计划 §1.3（`plan-2026-10-31-batch-37-v01-isolated-r04-k6.md:18`，主会话裁定章节明文"K1–K6 全链重跑……仍红按阻塞登记，不放宽"）执行选项 B（全链上设备、②不过登记为背景证据）；判据 §1.5 自注其为更晚裁定。两分支 K6 判据/命令相同；若主会话追溯裁定应维持预检硬门口径，本批 K6 结果不变（同为机制①阻塞），仅流程口径差登记在案（设备轨 §5.4/§8-8）。
  8. **K1 首次尝试无效红**（主包未卸载即装机 + worker 死亡环境形状）——登记不采用，卸装重做后取得有效红（Permission Denial 签名门，批次 27 E-03 §3.1 形态）。
- **不做（全部未做）**：选项 A（Keeper/L8 结构解锁——触 `settings.gradle.kts` 热区+新依赖钉版，无主会话裁定不擅施）；JS 广告与 `availableMethods`；core/data 降级变体的 3 行级按键取修复（R1 §3.3 选项 1——按工单只做标注，修复待主会话裁定）；`docs/current` 热区正文（契约 `:208` 登记不落笔）；真实 Provider；锁文件提交（KD-72 窗口不满足）；`connectedAndroidTest`；任何 git 写。
- **能力快照 / prompt / 广告文本零改动（沿裁定）**：本批生产代码足迹 = `OpenAiModelProtocol.kt` 单 hunk（数值 facet、`if (jsAdvertised)` 支路内，`jsAdvertised` 开关行未动）——生产默认（IMAGE_MODEL-only）请求体逐字节不变由结构保证；工作树中 `TutorToolDescriptions.kt`（他线 SafeTruncation 措辞）等广告相邻 M 均不在本批各轨申报足迹内（集成轨如实登记，归因他线在飞）。
- **DB schema 保持 71**（`StudyDatabase.kt:139`；`71.json` 在树；S=71 静态比对 `RESULT=PASS`/`EXIT=0`，`PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138`，设备轨与证据落盘员两轮实跑）。

### 1.2 沿用裁定与纪律（本批执行，未改）

1. **热区禁改**：feature/tutor、feature/library、feature/review、feature/capture、feature/profile、core/ui 全部、core/markdown 整目录、core/export 全部、core/domain（开工即脏 M）、core/data 他线 M（dense/`RoomMistakeDetailRepository` 等）、settings.gradle.kts、.gitignore、app Root 与开工即脏 M、docs/current 其余 M（契约 `:208` 归此）、docs/research 他线未跟踪记录、tools/kb、knowledge-production。本批触碰的共享脏文件（`core/data` V01 变体测试、A-10 夹具测试、`OpenAiModelProtocol.kt`、两台账 docs）均"整文件纳入并注明"（§8）。
2. **R-04 判据不放宽**：禁 internal 变体、禁 `-dontobfuscate`、禁删例、禁改判据文本；keep 既有规则不回退（keep 三文件 git 干净、末次提交 `b31b5d02`，R-04 窗口 sha before/after 全程不变）。
3. **设备绿绑定源快照（含 `core/model/src`）**：三个构建窗口（debug 主窗 / R-04 窗 / step14 舞步重跑窗）源快照 before/after diff 均空；1175 文件为本轨实跑数。
4. **新增用例必须重跑**：step19-v01-app、step20b-a10-budget 均为新增例重跑（3/3、3/3）；修复环后强门复跑（工单给定 exit=0）。
5. 锁文件与 `tmp/` 不提交；设备只 `assemble*` + `install -r -t` + `am instrument`（含 release 变体验 R-04），禁 `connectedAndroidTest`——全程未触。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa`（批次 29 收口提交；本会话实跑 `git rev-parse HEAD`）。
- **工作树**：**738 条**脏/未跟踪（本会话 `git status --porcelain | wc -l`；批次 37 基线时点 688、T5 落盘时点 696——多线在飞滚动，差值未逐项追认 UNVERIFIED）。
- **DB schema**：71；**KD 台账**：`docs/known-defects.md` 82 条（KD-77 本批转 closed；KD-72/73/82 open 维持）。
- **keep 两文件现树态**：`app/r04-test-keep.pro`、`app/proguard-rules.pro` git 干净（批次 36 `b31b5d02` 落定的两规则不回退；本会话 `git status --porcelain` 两文件空输出）。
- **热区**：feature/tutor 59、feature/library 43、core/ui 35（T6 本批再核，clean=false 与脚本判定一致）；`docs/current` 7 条 M（T5 实跑）。

## 2. 两研究线

### 2.1 R1 V-01 app 版真隔离引擎迁移装配研究（`2026-10-31-v01-isolated-wiring.md`，224 行，驱动 T2）

核心裁定（记录员按节结构实读 + 关键锚点抽查；:94 可达性句与 :170"4 例"计划原文本会话实读确认）：

- **装配形态**：套件照仓内设备绿先例装配——每测试一个 `VisualJsClientEngine(context)` 直构（隔离先例 `VisualJsIsolationInstrumentedTest.kt:120` 同构造）+ `connectForTest()` 显式预热（首连 15s / ready 10s 生产预算自管，**测试侧零新超时常量**）+ `@After` 统一 close（terminate→unbind→服务 `killSelf` 回收进程）；引擎注入走 `FigureGenerationCoordinationFactory.create` **既有第 4/5 参缝**（`DefaultFigureGenerationCoordinator.kt:859-876`，默认 null，**生产装配保持三参零改动**）。
- **度量器复用缺陷的根治形态**：夹具缺陷①（单次使用）根治 = **断言按键取**——键 = 引擎回传的 `executed.contentIds`（服务度量器确定性形状 `c0-<digest8>`），对渲染次数不敏感；且**迁移后测试侧度量器整个删除**（真实度量在 `:visual_js` 服务进程内的 `AndroidVisualContentMeasurer`，`VisualJsClientEngine` 构造本就不收度量器）——`singleOrNull` 对累积列表的脆弱形态结构性消除。
- **不采用 androidx ServiceTestRule**（gh api 实读其 `DEFAULT_TIMEOUT=5L` 与实测冷启动 attach 4.6–7.5s 相撞，且无 ready 门/死亡重建语义）。
- **§2.3-4 可达性判断被本批落码证伪（如实登记）**：R1 曾断言 app androidTest 可达 `VisualDrawOpsPainter`/`VisualSnapshotRestore`/`REPLAY_TEXT_PAINT_FLAGS`（:94），T2 实读证伪三者均为 core:data `internal`（`VisualDrawOpsPainter.kt:44`、`VisualSnapshotRestore.kt:181`、`VisualDrawOpsPainterPure.kt:87`）→ §5.1 的"4 例原样迁移"不可行，屏栅格逐位相等/PDF/restore 策略腿留 core/data 降级变体。
- **两夹具缺陷的根治与腿转移**：缺陷②（见证③在链腿无观测对象）= `seedParentOperation` 提进链腿骨架 + `readParentDispatchCount==0` 收尾断言；"度量真实发生过"转移 = 链腿 contentIds 断言（KDoc 登记转移）。

### 2.2 R2 R8/release 构建研究（`2026-10-31-r04-k6-verify.md`，282 行，驱动设备轨 R-04 窗）

核心裁定（关键锚点本会话实读：L8 mapping 段行号 :39430/:860808、`shrinker_config` 语义句、Keeper 选项 A :178）：

- **K6 判据与命令复核**：与批次 35 §2.5/§2.8、批次 36 record §4.5 **逐字一致、未放宽**（两例 `OK (2 tests)` + 三类签名 0）；两 `.pro` 已随 `b31b5d02` 入库、keep 门本会话实跑 OK/OK/OK EXIT=0、K6 两例现树行号 `:123`/`:245`（本会话 grep 同值）。
- **三值预检（R2 会话对批次 36 K2 窗口产物实跑）**：① 主 mapping 含 `DesugarCollections$SynchronizedMap -> j$.util.g` =1 过；③ 主包 dex 含 `androidx/tracing/Trace` =1 过（dexdump 确认真实类定义 + beginSection/endSection/forceEnableAppTracing 三方法名存活——机制② keep 生效）；**② 测试 mapping ` -> j$.util.g:` =1 不过**。
- **本会话新证：预检②在 AGP 9.3.0 + desugar_jdk_libs 2.0.3 架构下结构性不可满足**——j$ 改名由**每变体独立的 L8 编译**（`l8DexDesugarLib*` 任务，规则 = desugar.json `shrinker_config` + AGP 生成式 L8 keep 规则）治理，`testProguardFiles` 的用户 keep 规则**到不了该阶段**。实证链：`-keep class j$.** { *; }` 已进测试 R8 配置（`configuration.txt:75`）但测试 mapping 仍 **476 个 j$ 类改名**（恒等仅 6 个，恰 = 该变体生成式 L8 规则 7 条 keep 的精确对应；主包 40 恒等 ↔ 52 条）；两 mapping 均含 `# L8 Desugaring Mapping` 段（主 `:860808` / 测 `:39430`）且崩溃碰撞对（主 `:871645` / 测 `:49037`）都在段内；两变体各带独立 L8 dex（desugar_lib_dex 中间产物实测含 `j$/util/g`）；jar 内 `java/` 包名 0 个 j$ 条目。**批次 36 R1 §4.1"测试 dex 全量让名"预测被产物证据否定并给出机制修正**（修正模型表在 R2 §三）。
- **解锁路径**：`.pro` 侧无解锁路径；可行解锁 = slackhq/Keeper 类 L8 规则合并（Apache-2.0 / 269★ / pushed 2026-06；**AGP 9.3 兼容性 UNVERIFIED**；触 `settings.gradle.kts` 热区）——**归主会话裁定，本批未实施**。
- **§4.2 六行 K6 归因表**（判据 §1.7 逐字转录）：机制①复现 → 阻塞；机制②复现 → 对照 keep 窗口 sha；A/B/C/D 归因步；环境；D 旧包。本批 K6 实测命中第 1 行。

## 3. 实施轨 + 集成

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 门禁工单给定 / 报告 XML 读值 / 无证据 = UNVERIFIED）。

> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备判据 | 完成 | **518 行独立判据文档落盘（恢复批次 35 形态）；本轨零 Gradle/零 adb/零 git 写；只读门实跑=keep 门 OK×3 EXIT=0、S=71 PASS EXIT=0** | §3.1 |
> | T2 V-01 迁移 | 完成 | **app 套件 787 行 3 例落树（真隔离引擎 + 度量器根治）；两夹具缺陷根治；core/data 变体 13 行 KDoc 降级标注（整文件纳入）；④ PDF 腿留降级变体（偏差 1）** | §3.2 |
> | T3 A-10 夹具 | 完成 | **+69/−5 单文件：seedModelTask 走生产迁移链落 QUEUED + 终答位子门①②补齐；断言只增不减** | §3.3 |
> | T4 KD-82 | 完成 | **单 hunk +8/−1（program facet 16384）+ 新 JVM 4 例落码；牵动面既有用例零回退（XML 实证）；imagePrompt 残余登记** | §3.4 |
> | T5 台账 | 完成 | **KD-77 关闭裁决 + 六件证据入仓 + KD-73 回链 + 清单 v3 补记 11 + 契约 :208 登记不落笔；KD-82 关闭裁决文本为运行时摘要未落仓，台账现态=open（复核修复轮落批次 37 块）** | §3.5 |
> | T6 条件轨 | 完成 | **分支 D，九项只读复核全部仍 pending，零文件落盘** | §3.6 |
> | 集成交接 | 完成 | **无需接线（零文件改动）；import 无悬空；零广告/零 schema 双确认（附他线脏污如实登记）** | §3.7 |
> | 设备轨 | 完成 | **20 步全绿（step14 环境红舞步后绿）+ step19 3/3 + step20b 3/3；R-04 K1–K5 绿、三值预检②不过（P2→选项 B）、K6 红=机制① → 阻塞登记** | §4 |
> | 复核/修复环 | 完成 | **第 1 轮 7 条处置（2 台账文件）+ 复核发现全数独立复核** | §5 |

### 3.1 T1 设备判据 —— **518 行独立文档；本轨零执行**

- 四部分：① R-04 K1–K6 重跑判据与命令（判据总表逐字转引批次 35 §2.8 不放宽；K6 期望=两例 `OK (2 tests)` + logcat 三类签名 0；命令逐字沿批次 35 §2.5、目录换 `build/device-track-batch37/`；本批四处变化=目录、K5b dex 计数补齐（批次 36 欠账）、三值预检须在新 K2 产物重跑（②不过=机制①结构性不可满足，给分支 P1/P2 两合规路径——P2 选项 A Keeper 触热区禁擅施、选项 B 按计划 §1.3 全链重跑须主会话显式确认口径切换）、步 5 回归 + 步 6 恢复 debug 态（批次 36 欠账）必补）；② V-01 app 版判据（语义底线六条 + core/data 降级变体处置）与 A-10 修复版判据（QUEUED 守卫落库 + 3 例全绿 + 终答位子门断言面不得以两绿例替代）；③ 15+ 套件不回退 = 20 步表（step01–18 + step19-v01-app + step20b-a10-budget，可选 step21 降级变体）；④ 重建重装硬门与源快照门（find 集含 `core/model/src`；keep 三文件 sha before/after；顺序不可颠倒）。
- **本轨纪律**：零 Gradle/零 adb/零 git 写；只读门实跑 = keep 门 OK×3 EXIT=0、S=71 静态比对 PASS EXIT=0。
- **登记**：判据计数列 34/2/5/9/5/15/4/3/12/12/16/24/16/15/2+1/6/27 为 T2/T3 落码前现树形态，设备轨以落树后构建前 grep 为准（step19 旧位 4 的口径差见 §4 表）；8 项未验证显式登记于判据文档。

### 3.2 T2 V-01 迁移落码 —— **app 版 3 例 + 两夹具缺陷根治；生产代码零触碰**

- **新套件** `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt`（?? 未跟踪，787 行，`@Test`=3 本会话 grep 同值：:136/:247/:289）：① 用例 A（生成腿 + 零派发四断言位 + `workerUid != myUid` 防退化闸）；② 生产装配负控（不注入引擎恒 UNAVAILABLE，原样迁移）；③ 链腿（同一 program 在同一真引擎二次执行直取 Rendered → `TutorVisualCommitRepository` 一次事务提交 → 真实关闭+同名重开 → 行回读 `drawCommandsJson` 逐字节相等 / contentIds 回读 / 版本列回读 + 见证③整链复核）。
- **真隔离引擎语义**（复核轨独立复核 + 本会话抽查）：`VisualJsClientEngine(context)`（:500-501，与先例 `VisualJsIsolationInstrumentedTest.kt:120` 同构造；引擎测试面 `connectForTest`/`workerUidForTest`/`close` 为批次 36 已提交 internal 钩子，app androidTest 为 friend 源集可达）；`QuickJsVisualExecutionEngine` 代码引用 0（仅 KDoc :68，本会话 grep 同值）；`visual_js` 进程声明 `app/src/main/AndroidManifest.xml:72`（本会话实读）；**引擎主代码 `VisualJsClientEngine.kt`/`VisualJsService.kt` git 干净 = 生产零触碰**（本会话 `git status` 空输出）。
- **两夹具缺陷根治**：①度量器单次使用 → contentIds 键取（正则 `c0-[0-9a-f]{8}` 校验 + `singleOrNull` 形态结构性消除）；②见证③链腿无观测 → `seedParentOperation` 提进链腿（:350）+ 双处 `readParentDispatchCount==0`（用例 A :226-231、链腿 :465-470）。**零派发见证四断言位全部保持未缩水**（operation 0/0、attempt `method='JS' AND reservation_id IS NULL`、账本零触碰、产物恒 Program + `networkRequestsAllowed=false` + UnreachableConfigurationStore tripwire）。
- **core/data 降级变体**（`core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt`，M **+13/0** 本会话 `git diff --numstat` 同值）：KDoc 追加降级口径标注，测试逻辑零改动（13 行逐行全为注释）；其已知 2/4 夹具红挂账、明示不进本批套件单（不跑 step21，判据 §2.1 第 6 条"不允许已知红例留在套件单里"）。
- **seam 与回滚**：S1 工厂注入缝=既有第 4/5 参默认 null；S2 引擎测试面=批次 36 已提交钩子；S3 真实度量=服务 warm-up 自构；S4 账本观测缝=`seedParentOperation` 库文件直写+SQL 直读；S5 core/data 变体=KDoc 标注。回滚 = 删 app 新文件 + `git checkout` core/data 该文件；生产代码零触碰、无契约/schema/快照影响。
- **T2 未验证项（设备轨执行，本轨零 Gradle 为纪律）**：3 例断言绿性、套件墙钟、进程回收无残留、编译——全部由设备轨 step19 出数闭合（§4）。

### 3.3 T3 A-10 夹具修复 —— **生产链路落 QUEUED + 终答位子门；+69/−5 单文件**

- **夹具修复**（`FigureDispatchBudgetBoundaryInstrumentedTest.kt`，M +69/−5 本会话 numstat 同值）：`seedModelTask` 在 `createModelTask` 后走生产迁移链路——`TransitionModelTaskCommand` 执行 WAITING_FOR_MODEL→QUEUED（stage=PREPARING），与生产 `RoomModelTaskRepository.prepareForExecution`（`core/data/.../model/RoomModelTaskRepository.kt:629-650`）同参，**不开旁路**；`SeededParent` 回传迁移后 stateVersion/status 使测试 3 的任务级预留双 CAS 对上真实快照；时钟核算满足两处单调性 require。
- **"耗尽→终答位保留"子门补齐**（第 3 例 `theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion`，原断言零放宽只增不减）：子门① applied 终答预留必须带回 durable 运行快照（snapshot.status==RUNNING 且 attemptCount==6，钉住端口硬不变量 `ModelTaskDatabasePort.kt:162-166`，灭"applied 假绿、编排半边接不上"）；子门② 终答消费第 6 名额后预算闭死——两次账本 reserve 探针（`chatFinalAnswer=true` 与 `=false`）都必须 `Denied BUDGET_EXHAUSTED` 且消息含真实计数"已派发 6"与"未出网"（消息措辞与生产 `RoomFigureDispatchLedger.kt:75-87` 逐字核对一致；灭"终答位只是未计数的余量、消费后图像可继续偷用"）。
- **先红后绿的修复前红构造（静态链实读验证，与批次 36 step20 设备实测红一致）**：旧夹具返回 `status=created.snapshot.status`（初态 WAITING_FOR_MODEL，`ModelTaskTransactionDao.kt:204`），撞 `ReserveModelTaskRemoteDispatchCommand` 的 `require(expectedStatus == QUEUED)`（`ModelTaskDatabasePort.kt:52-54`）在命令构造时即抛 IAE——断言未跑即红（测试 1/2 不构造该命令所以绿，与批次 36 "3 例 1 红"吻合）。
- **UNVERIFIED（本轨）**：编译与设备例——由设备轨 step20b 出数闭合（§4，OK (3 tests)）；批次 36 记录要求的修复后复跑以 step20b-a10-budget 落地（新增日志不覆盖旧证据）。

### 3.4 T4 KD-82 maxLength 对齐 —— **program 面 16384 落码；JVM 4 例 XML 出数全绿；KD-82 仍 open**

- **schema maxLength 65536→16384**：`OpenAiModelProtocol.kt` 单 hunk（`git diff --numstat` = **+8/−1** 本会话同值；唯一改动 `put("maxLength", MAX_PROGRAM_UTF8_BYTES / 4)` = ⌊65536/4⌋，带 KD-82 派生注释）——只落 `:571 if (jsAdvertised)` 的 program 支路；**生产默认（IMAGE_MODEL-only）请求体逐字节不变**（该路径不执行）。**本地字节门 65536 三执行点零改动**：`FigureGenerationPolicy.kt:33` 常量、`:222` 策略门（`> 65536` 拒）、`TutorToolLoop.kt:363` 解析门（`<= 65536` require）、`CapturedQuestionDocument.kt:130`。
- **新 JVM 4 例**（`core/data/src/test/.../model/ProgramSchemaMaxLengthAlignmentTest.kt`，?? 新文件，避开他线在飞的 `OpenAiNativeToolsProtocolTest.kt` 脏文件）：① facet=字节安全码点上界钉子（且 ×4 ≤ 字节门、imagePrompt 钉 4096 不动）；② 16384 ASCII 正例双门过；③ 16384×4B=65536 恰在含边界门内放行（边界钉子）；④ 16385×4B=65540>65536 被拒且消息含 "65536 UTF-8 bytes" 字节计数、16385 码点超 facet——fail-closed 方向。**负例取 16385 系对任务原文"16384 个 4 字节字符被拒"算术误的更正**（16384×4=65536 恰在含边界门内=放行；"16384 码点合法却被字节门拒"正是本修复按定义消灭的失败；更正在 KDoc :36-38 登记）。
- **JVM 出数（记录员本会话实读 JUnit XML；执行=门禁串行，未复跑 Gradle）**：`ProgramSchemaMaxLengthAlignmentTest` **tests=4 / failures=0 / errors=0 / skipped=0**（`core/data/build/test-results/testDebugUnitTest/TEST-…ProgramSchemaMaxLengthAlignmentTest.xml`）——**KD-82 program 半边的"JVM 用例实跑绿"关闭前置已由门禁轮满足**（复核修复轮台账块写作时的 UNVERIFIED 状态就此被后到证据闭合）。
- **牵动面既有用例零回退（XML 实证同轮）**：`TutorFigureMethodDualParseTest` 14/0（core/data，测字节门常量 +1 非 facet）、`TutorFigureProposalCallTest` 11/0（core/model，同上）、`FigureGenerationLocalBudgetBoundaryTest` 5/0、`ModelWireProtocolSourceJsFidelityTest` 15/0；全仓 grep 无任何测试断言 program facet==65536、无 golden 字节级 schema 测试；`OpenAiNativeToolsProtocolTest` 的 maxLength 断言是 ADVISORY 600/description 200 不同字段（该文件他线 M 本批未触）。
- **A-02 契约面核对**：`docs/current/visual-generation-contract.md:78`/`:86` 声明的是本地字节预算（本批未动，声明继续为真）；schema maxLength 是模型广告面非契约预算声明，契约无需回改（且 docs/current 属热区归契约轨）。其他 64 KiB 引用均为字节口径预算不动；schema 构建单点（`strictFunctionSchema`），wire/SSE 无第二处 maxLength 广告；prompt/能力快照零改动。
- **KD-82 状态裁定（记录员落笔口径）**：**状态保持 open（known limitation，不排期）**——关闭条件另一半（广告解冻批对 **imagePrompt 残余**的裁定：`OpenAiModelProtocol.kt:552` 仍广告 4096 码点面对 4096 字节门，最坏 4× 过度广告；Fix direction ① 对 imagePrompt = 4096→1024）本批未做且属广告面裁定；program 半边已达 Fix direction ①。"任何文档/证据不得把现 schema 写成与本地门同一口径"的禁令对 imagePrompt 继续有效（台账块原文）。

### 3.5 T5 台账回填 —— **KD-77 关闭 + KD-73 回链 + 补记 11；契约 :208 登记不落笔**

- **KD-77 关闭**：`tmp/kd77-api23-smoke/` 证据 6 件（00–05）迁入 `docs/research/evidence/2026-10-31-batch-37/`（sha256 迁移前后逐件相等，v2 落盘员重算同值）；按既有惯例补 `00-index.md`（含 provenance/命令与输出/关闭条件映射/缺口登记）；`docs/known-defects.md` KD-77 头部 `:2909` open→**closed**（2026-10-31 批次 37 T5，主会话裁定计划 §1.5），关闭裁决块 `:2939` 逐条对照 Reopen condition（装机 INSTALL_EXIT=0+干净装机时戳、冷启动 ok/1611ms/MainActivity、416 行 logcat 四类签名 0、APK 指纹=批次 35 绿绑定包 sha `1593b25ae33d10dc…` 与批次 35 证据同值）；**跨账本分歧以此裁决为准**（kb-batch25:401 的"已关闭"与现口径一致、:200/:245 自留表述标记为不再反映裁决后状态，他线文书不改）；边界登记：KD-77 最小冒烟不满足也不声称满足 R-01。
- **KD-73 回链句**（`docs/known-defects.md:2824` 后）：补 `tools/ci/check_stdlib_compat.py`（git 已跟踪）+ `tmp/kd73-stdlib-scan/run.log` 与 `run-final.log` 路径（tmp/ 按纪律不入仓，脚本+自测为在树可复跑载体），T5 一手复跑同值（18 tests OK、23,847 unique refs 0 unresolved）、**状态保持 open**（设备/R8 半边仍未做）。
- **清单 A-10/A-11/A-12/V-01 行刷新**：T5 时点实核为"核验登记"（v3 补记 11：四行 0 改动、0 项转通过——当时 V-01 app 迁移未在树、A-10 夹具为在飞 diff）；**A-10/V-01 两行的批次 37 出数注记由复核修复轮 v3 补记 12 落**（step19/step20b 绿 + PARTIAL 口径）；R-04 行正式回链归本记录员（本批落笔，§附录 A）。
- **契约 :208**：T5 实跑 `git status --porcelain -- docs/current/` = 7 条 M（README/architecture-contract/data-schema/implementation-status/model-task-contracts/product-contract/release-gates）→ 热区未清空 → **维持登记分支不落笔**（`visual-generation-contract.md:208` 的 KD-81 句矛盾待回改）；`release-gates.md:38`/`:50` 的"API 23"为门清单句非状态句，关闭后仍成立无需回改（联动面已核）。

### 3.6 T6 条件轨 —— **分支 D，九项只读复核，零文件落盘**

- 热区布尔再核：feature/library 43（30 M+13 ??）、feature/tutor 59（37 M+22 ??）、core/ui 35（11 M+24 ??）——clean=false 与脚本判定一致。九项（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段 6→5c）逐项复核全部仍 pending、载体均在、声明热区目标文件均 M。新漂移仅一处：MASTER-LINE AUD-08 审计行锚自批次 32 登记的 `:153` 漂至 `:158`（维护日志前置行所致）；KD-68 `:2652` 未漂移；`ui-queue-sequencing.md:46` 仍写"schema 70（预计；当前 69）"而现树 71——批次 34 原登记仍有效不重复建条。KD 台账现 82 条（本轮未动）。未新建 KD/条目/文件。

### 3.7 集成交接 —— **无需接线（零文件改动）**

- **T2 迁移套件依赖面**：29 条 import（按 T2 申报口径；复核实测全文 `^import` 57 条）+ 全部未 import 引用逐一解析到真实声明，零悬空——core.data.visual 六符号（工厂/仓库/`VisualFrameInputs` fun interface SAM）、core.database 八符号（契约/`StudyDatabase.open`/port 表尾含 TutorVisualPort）、core.domain 九符号（ModelConfigurationStore/`VisualExecutionEngine.Rendered` 字段名与测试用名一致）、core.model 十六符号；`VisualJsClientEngine` 无 import 属同包设计（测试包=引擎包，internal 测试面经 friend 源集可达）；同源集既有 tracked 套件（批次 24 设备 6/6 绿）已证明第三方依赖面可执行。Gradle 面核对 `app/build.gradle.kts`：全部既有依赖，**未新增任何 build/manifest/源文件接线**；类名无冲突（app androidTest 仅此一份，core/data 变体异包异模块）。
- **T4 牵动面**：既有用例零回退（§3.4 XML 实证）；全仓无 facet==65536 断言。
- **零广告改动成立（附他线脏污如实登记）**：批次 37 生产代码足迹仅 `OpenAiModelProtocol.kt` 一 hunk + 三个测试文件 + core/data KDoc 13 行 + T1/T5 文档，不含 prompt/广告文本/能力快照载体；工作树中 `TutorToolDescriptions.kt`（M，KNOWLEDGE_READ 安全截断措辞）、`TutorToolDescriptionsSnapshotTest.kt`、app 能力快照相邻 M 等均不在本批各轨申报足迹内，归因他线在飞（精确归属线无法从 git 证明，如实标注）；`app/src/main/assets/`（??）实查仅开源 LICENSE/NOTICE 文本。
- **零 DB schema 改动成立**：`STUDY_DATABASE_VERSION = 71`（`StudyDatabase.kt:139`，文件不在 M 列表）；core/database schemas 下 porcelain 0 条；批次 37 足迹不含任何 @Entity/DAO/契约/schema 文件（core/database 下 M 为他线 dense/F6 同族，本核验读到现树态、其变更归属他线）。

## 4. 设备与证据

> 环境：`emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096 / fingerprint `google/…/UE1A.230829.050/12077443:userdebug/dev-keys`；5566/5584 属他线禁碰、5596 API 23 机零触碰）。执行窗：20 步轨 21:19–22:04，R-04 窗随后；判据执行顺序按 §4.8。零 `connectedAndroidTest`、零 git 写、零热区触碰。HEAD 全程 `5ee1f90f`。

### 4.1 产物身份（00-index §3）

| 产物 | sha256 | 用途 |
|---|---|---|
| app-strictOffline-debug.apk | `070b4485290b1388…71d6e1` | 20 步轨（舞步后重打包为 `59b6d444…`，zip 非确定性，源快照 diff 空证非源变更） |
| app-strictOffline-debug-androidTest.apk | `bba75fa32c4b7c99…fea3dd` | 20 步轨 |
| data-debug-androidTest.apk | `957b53a583c57cf7…3dd964` | 20 步轨 + A-10 |
| database-debug-androidTest.apk | `14240f5aed913cbe…864034` | 20 步轨 |
| app-strictOffline-release.apk | `9ec35dbac5e0e4b7…f11ea0` | R-04 K1–K6（≠批次 36 窗口值 `1c7fe3f7…` ✓） |
| app-strictOffline-release-androidTest.apk | `390ccec09a161cd3…a98801` | R-04 K1–K6 |
| 两 release 证书 | 同 `9fd478d96b3e8f6f…f633969` | K3（apksigner V2 逐字同值） |
| 71.json（asset） | `4a13d9d0b5ee561f…781d94` | schema 门（与批次 35/36 同值） |

keep 三文件（R-04 窗口 before/after 全程不变；git 干净 `b31b5d02`）。源快照三窗口（主窗/r04 窗/rerun 窗）before/after diff 均空；主窗 1175 文件（五模块集含 core/model/src）。

### 4.2 重建重装硬门与 20 步套件（E-02…E-09）

- **一致性门**：keep 门 OK×3 EXIT=0；S=71 静态比对 PASS；V=71/P=71/71.json 在树。
- **构建四包 BUILD_EXIT=0**；安装 4×Success（主包首撞 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`→按判据卸后重装）；设备侧 sha256 四包与宿主逐一相等；asset 门/dex 符号门（`need()`：app 包含 V01VisualChainInstrumentedTest 新类+K6 两方法+P-12③ 例，data 包含 A-10 三方法）全过。
- **20 步全绿**：step01–18 = 34/2/5/9/5/15/4/3/12/12/16/24/16/15/2/1/6/27（==构建前计数 `pre-build-counts.txt` 与判据 §3.1 表列逐一相等）+ **step19-v01-app OK (3 tests)** + **step20b-a10-budget OK (3 tests)**。
- **step14 环境红舞步（E-07）**：首跑 `Tests run: 15, Failures: 2`（watchdog 例 `:245` 断言 `:281` status=-3=HarnessExecution.DIED 由 harness RemoteException 合成；P-12③ 例 `:423/:439` Rejected HARD_DEADLINE_EXCEEDED "visual_js worker died: binder died"）→ 四源归因（测试文件与 visualjs 主代码 git 干净=批次 36 绿轮同源；dex 门+fresh 安装 sha 相等；设备 top 实测 219MB free/swap 355MB/iowait 22%，宿主 3 台他线模拟器负载）→ **类别=环境** → 按判据 §3.2 舞步停步重建（重跑窗口源快照 diff 空）重装重跑 → **OK (15 tests)**（Time 82.8s；logcat 9 类签名 0）。
- **step19 V-01 app 版（E-08）**：OK (3 tests) == 构建前计数 3；真隔离引擎语义满足（§3.2）；四断言位齐全未缩水；夹具度量器缺陷以 contentIds 键取根治。
- **step20b A-10（E-09）**：OK (3 tests) == 计数 3；第 3 例终答位子门断言面未放宽且强于判据最低面；A-10 夹具走 `createModelTask`→`transitionModelTask` 生产链路落 QUEUED 无端口绕行。
- **签名收口（E-16）**：20 步 + step14 重跑 21 份 logcat 9 类签名扫描全 0；K1 red logcat 9 类 0（红因 SecurityException 属签名门证据本体）；K6 logcat 9 类=5（FATAL×1+NoSuchMethod×4 = 机制①崩溃证据本体，按判据 §1.7 登记）。红例总表 3 条：2 环境红（舞步后绿）+ 1 机制①红（K6，阻塞登记）；**无夹具类红复现**。

### 4.3 R-04 K1–K6（E-10…E-15）—— **K1–K5 全绿；预检②不过（P2→选项 B）；K6 红=机制① → 阻塞**

| K | 结果 | 关键证据 |
|---|---|---|
| K1 红基线 | **PASS** | release 主包 + debug 测试包装机 → `K1_INSTRUMENT_EXIT=1`，红因 = `Permission Denial: …offline.test does not have a signature matching the target …offline`（`r04-step1-red.log:1-2`，批次 27 形态）；首次尝试无效红登记不采用（§1.1 偏差 8） |
| K2 release 测试轨构建 | **PASS** | `STEP2_BUILD_EXIT=0`（51s）；两产物 sha ≠ 批次 36 窗口值 ✓ |
| K3 证书一致 | **PASS** | 两包 V2 证书 SHA-256 逐字同 `9fd478d9…f633969`（CN=R04 Local） |
| K4 keep 门 | **PASS** | 权威注释感知门 OK×3 EXIT=0（字面 grep 口径差按判据 §1.1 登记）；K4b mapping 改名行 **797,123**（>0） |
| K5 so 双 ABI + 测试类未改名 | **PASS（双口径）** | K5a：libquickjs.so arm64-v8a 924,936B + x86_64 911,248B；K5b：apkanalyzer 计数 **289**（批次 35 实测 275，随树增长）/ unzip 兜底 **35**（批次 36 同值）——两口径单位不同各自登记不互替；**批次 36 K5b 欠账就此补齐** |
| 三值预检 | **②不过 → 分支 P2** | 新 K2 产物重跑：①=1（≥1 ✓）/ ②=1（期望 0 **不过**）/ ③=1+类定义级加固过（dexdump 实证 `Landroidx/tracing/Trace;` 类定义 `:137697` + 三签名方法）→ 选项 A 未擅施（触热区）；**选项 B（计划 §1.3）执行**，裁定依据在案（§1.1 偏差 7） |
| K6 设备两例往返 | **FAIL → 阻塞** | `Process crashed before executing the test(s)`（两例 0 跑，`-r` 流无方法名、无 `OK (2 tests)`）；崩溃栈 `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（declaration 在 **offline.test** base.apk!classes2.dex）← `j$.util.DesugarCollections.synchronizedMap` ← `androidx.work.impl.WorkDatabase.<init>` ← WorkManagerInitializer ← androidx.startup（WorkManager 启动栈）；三类签名计数 **4**（NoSuchMethodError×4 + FATAL×1，NoClassDef/ClassNotFound 0） |
| 步 5 回归 + 步 6 恢复 debug 态 | **PASS（批次 36 欠账补齐）** | 步 5 不带属性构建 EXIT=0 产物回落 debug 路径；步 6 四卸四装 Success，在装主包 sha=`59b6d444…`（=舞步后绿轮绑定包，落盘员 E-17 独立复核同值） |

- **K6 归因（判据 §1.7 六行表第 1 行逐字对号）**：现象 = instrument 未跑完即 Process crashed + `NoSuchMethodError … Lj$/util/g; <init>(Ljava/util/Map;)V`（WorkManager 启动栈）→ **先判机制①**；与三值预检②=1 的产物级预测闭环（预测→实测）。**NoClassDefFoundError（机制②）0 命中 = 批次 36 keep 扩充（`b31b5d02`）对机制②有效的设备级确认**；剩余崩溃全部来自机制①（`testProguardFiles` 规则到不了 L8 面——R2 verify §三 结构性裁定在本批产物级+设备级双复现）。
- **处置 = 按"阻塞"回报**：不改判据、不加 `-dontobfuscate`、不换 internal 变体、不删例——全部未触。**R-04 不虚关：K1–K5 绿不替代 K6，阻塞维持。关闭前置 = 主会话在两合规路径间裁定**（选项 A Keeper/L8 结构解锁 vs 维持批次 36 预检硬门口径）；两分支 K6 判据/命令相同且共同禁止清单在案。
- R-04 窗口源快照 diff 空、keep 三 sha 全程不变（复核落盘员逐一 diff 复算=空）。

### 4.4 证据轨与回链状态

- `docs/research/evidence/2026-10-31-batch-37/`：`00-index.md`（v2，180 行；E-01…E-17 + E-77-01…06 全批唯一）+ KD-77 迁移六件 + `step-device-track.md`（255 行 R4 摘要）；全目录 8 文件 118 KiB，全 `docs/research/evidence/` 1305 KiB < 8 MiB 预算。
- 脱敏：密钥/凭据/邮箱/主目录路径 0 hits（v2 终态；v1 两处假阳性/自引用已修正并重跑确认）；六件 KD-77 迁移件 byte-identical。
- 回链：批次记录 = 本文件（00-index §8 登记的欠账在此收口）；R-04/V-01/A-10/KD-82 台账回填 = 本批记录员落笔（§附录 A）。
- **raw 缺口三项（落盘员自认 UNVERIFIED-raw，如实转录）**：release 窗口装机与设备侧 sha 无独立 raw（`step-device-track.md:164-165` 为 runner 摘要声明；K6 舞步已收口无法补采）；K6 `-e class` 命令行无独立 raw（判据 §1.6 命令逐字在案）；step14 设备负载实测（iowait 22%/219MB free）无 raw。

## 5. 复核发现与处置

### 5.0 修复环第 1 轮（工单给定；第 1 轮复核发现 7 条）

- **改动 2 个文件（均为共享脏文件——会话开始前已 M，整文件纳入并注明）**：
  - `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（1/1 → 3/3）：`:5` 头部段尾追加 **v3 补记 12**；`:198` A-10 行现状格追加批次 37 注记；`:220` V-01 行现状格追加批次 37 注记。
  - `docs/known-defects.md`（5/1 → 7/1）：KD-82 条目末尾追加**批次 37 Progress 块**（+2 行；文件 3159→3161 行）。
- 零代码文件、零 Gradle、零 adb、零 git 写。**门禁序列（工单给定）**：单测门第 1 轮 exit=0 → 修复环 → 修复环复跑 exit=0 → 强门（修复环后）exit=0 →（复核轨后续轮）单测门复跑 exit=0 → 修复环后强门复跑 exit=0。

### 5.1 复核轨发现（关键项转录；全部"已独立复核"标注见工单）

| # | 级别 | 位置 | 发现 | 处置/状态 |
|---|---|---|---|---|
| 1 | 高 | `build/device-track-batch37/r04-k6.log:26,33,59,66` 等 | K6 实测不绿（Process crashed + NoSuchMethodError Lj$/util/g，0 例跑）；设备轨按判据 §1.7 第 1 行逐字对号归因机制①并按阻塞登记——**申报"K6 红、阻塞登记"与原始证据一致，不是虚报绿** | 已独立复核（实读崩溃栈原文、签名计数 4、三值预检 ①②③=1 闭环） |
| 2 | 中 | `OpenAiModelProtocol.kt:579-594` + 三执行点 | KD-82 对齐方向正确、不放宽（facet 收紧 16384、本地门 65536 三执行点零改动）；【偏差1】实测 +8/−1 vs 申报 +7/−1 少计 1；【偏差2/已闭合】KD-82 关闭裁决文本内嵌"待门禁回填"空槽——**本记录员后续 XML 实读：4/0/0/0 已出数**（§3.4），空槽载体本身不在仓内 | 已独立复核；计数更正入 v3 补记 12；JVM 出数入本记录 + KD-82 收口注记 |
| 3 | 中 | 准入清单 `:198`/`:220` | A-10/V-01 行现树滞后于 T2/T3 落树与设备绿——批次 37 完成结果未反映在行内（补记 11 系实时点陈述，非虚报） | **已处置（修复环第 1 轮 v3 补记 12 两行注记）** |
| 4 | 中 | `known-defects.md` KD-82 段 | KD-82 收口残余（imagePrompt 4096 码点 vs 4096 字节门）原悬在未落笔台账——若整条关闭而不登记残余即反向虚报 | **已处置（修复环第 1 轮 KD-82 批次 37 块落台账：残余登记 + open 维持）** |
| 5 | 中 | `step-device-track.md §8-2`；`00-index §0 V-01` | V-01 收口存在如实登记的缺口：④ PDF 腿本批无设备证据，严格口径 V-01 = PARTIAL（3/4 子项）；**若台账后续不加 PARTIAL 限定直接写 V-01 关闭，即构成虚报——落笔红线** | 已独立复核；本记录全程带 PARTIAL 限定（§3.2/§7/§附录 A） |
| 6 | 低 | app 套件 | T2 迁移保真 PASS（真隔离引擎、两夹具缺陷根治、四断言位未缩水、熔断语义一致）；申报"29 条 import"与实测 57 不符（精度问题非实质） | 已独立复核（tmp/t2_kt_static_check.py OK/EXIT=0；import 计数本会话同值 57） |
| 7 | 低 | `settings.gradle.kts` diff、`.gitignore`、keep 三文件、docs/current、`TutorToolDescriptions.kt` | 热区零越权：settings.gradle.kts diff 仅他线 `+":core:markdown"`（无任何 Keeper/L8 痕迹——选项 A 未实施证实）；keep 三文件干净 `b31b5d02`；docs/current 7 M 与 T5 登记逐一相同→契约 :208 登记分支成立；工作树广告脏污为他线 SafeTruncation 不在本批足迹 | 已独立复核（diff 实读逐项） |
| 8 | 低 | 设备轨原始产物抽查 | pre-build-counts 逐值相等、K3 证书逐字同值、step14 首跑红/舞步后绿两日志在、run20.sh 无 connectedAndroidTest、21 份 step 日志在、final-signature-sweep 全 0、s71 PASS——全部与记录一致 | 已独立复核（原始件实读） |
| 9 | 低 | 两研究文档 | R1 :94 可达性原句确实存在（T2 证伪是对真实存在判断的证伪且两侧 KDoc 登记）；R2 §4.2 六行归因表实读齐备 | 已独立复核（wc 224/282/174 + 锚点实读；本会话抽查同） |
| 10 | 低 | `FigureDispatchBudgetBoundaryInstrumentedTest.kt`（+69/−5） | A-10 夹具走生产链路且为加强非放宽（QUEUED 经生产迁移、无端口绕行、两道子门只增不减） | 已独立复核 |
| 11 | 低 | 判据 §2.1 第 1 条 | 判据字面"import ≥1"对同包落点不可满足（轻微口径差）——语义本体已由 grep 独立验证；建议台账落笔补口径说明 | 本记录 §3.2 已按此口径落笔 |
| 12 | 中 | core/data V01 变体（+13） | 降级变体处置合规（仅 KDoc、逻辑零改动、已知 2/4 红不进套件单） | 已独立复核（diff 逐行） |
| 13 | 低 | 00-index §7-2/3/4 | 三处 raw 缺口由索引自认 UNVERIFIED-raw（如实登记非虚报） | 已独立复核；本记录 §4.4 转录 |
| 14 | 低 | `known-defects.md` KD-77/KD-73 块 | KD-77 关闭与证据一致（sha 重算逐件相等、五类签名复算全 0、关闭块自带边界）；KD-73 回链在 :2824 后状态如实 open | 已独立复核 |
| 15 | 低 | JVM 面 | ProgramSchemaMaxLengthAlignmentTest 4 例与牵动面套件 XML 出数（记录员本会话实读，§3.4/§6）——门禁串行执行、记录员未复跑 Gradle | 记录员 XML 实读（本轮） |

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮 / 复跑） | **exit=0 ×2**（工单给定） | 记录员未复跑（禁 Gradle）；逐套件出数见下行 |
| 修复环复跑 / 强门（修复环后 ×2） | **exit=0 ×3**（工单给定） | 同上 |
| KD-82 新套件 JVM | **4/0/0/0（XML 实读）** | `core/data/build/test-results/testDebugUnitTest/TEST-…ProgramSchemaMaxLengthAlignmentTest.xml`（tests=4 failures=0 errors=0 skipped=0；记录员本会话解析） |
| T4 牵动面既有套件 | **零回退（XML 实读）** | TutorFigureMethodDualParseTest 14/0、TutorFigureProposalCallTest 11/0（core/model）、FigureGenerationLocalBudgetBoundaryTest 5/0、ModelWireProtocolSourceJsFidelityTest 15/0 |
| R-04 keep 规则门 | **OK×3、EXIT=0** | 设备轨 + 判据轨 + 落盘员多轮实跑 |
| S=71 静态清单门 | **RESULT=PASS / EXIT=0** | 设备轨实跑（PLAN_HASH=JSON_HASH=`714c0ecb…`） |
| kotlin-stdlib 兼容门（KD-73） | **OK，SCAN_EXIT=0** | T5 复跑：23,847 refs / 329 构件 / 0 unresolved；自测 18 tests OK |
| 源快照绑定 | **三窗口 diff 全空；1175 文件** | `src-sha-*/r04-src-sha-*/rerun-src-sha-*`；落盘员逐一 diff 复算 |
| 设备 20 步 | **全绿，计数=构建前计数** | §4.2 表；step14 环境红舞步后绿 |
| 9 类崩溃签名 | 21 份 logcat 全 0（K1 red 0 / K6=5 证据本体） | `final-signature-sweep.txt` |
| step19 V-01 app / step20b A-10 | **OK (3 tests) ×2** | `step19-v01-app.log` / `step20b-a10-budget.log` |
| R-04 K1–K5 | **全 PASS（含 K5b 双口径 289/35）** | §4.3 表 |
| R-04 三值预检 | **①=1 ②=1 ③=1 → ②不过 → P2 → 选项 B** | `r04-three-value-precheck.txt` + dexdump 加固 |
| R-04 K6 | **红：Process crashed（0 例）+ 签名 4 → 机制① → 阻塞登记** | `r04-k6.log` / `r04-k6-signature-count.txt` / `r04-k6-logcat.txt` |
| 生产签名 / arm64 真机 / 16KB release / API 23 全套件 | **UNVERIFIED** | §7 |

## 7. 遗留 / UNVERIFIED / 待裁

**R-04 与设备面**

1. **R-04 阻塞维持（K6 机制①红）**：关闭前置 = 主会话在两合规路径间裁定——**选项 A**：Keeper 类 L8 规则合并（结构解锁，Apache-2.0/269★/pushed 2026-06，**AGP 9.3 兼容性 UNVERIFIED**，触 `settings.gradle.kts` 热区+新依赖钉版）；**选项 B'**：维持批次 36 预检硬门口径（= 承认 `.pro` 无解锁路径，R-04 以机制①结构性不可满足的现状长期阻塞或改判据——改判据被 R-04 判据不放宽纪律禁止，需主会话明示口径变更）。裁定前不关闭、不放宽；禁改判据/禁 -dontobfuscate/禁 internal 变体/禁删例全程未触。
2. **V-01 = PARTIAL（3/4 子项，严格口径）**：④ PDF/painter/restore 腿本批无设备证据（依赖 core:data internal 符号跨模块不可达）；降级变体已知 2/4 夹具红挂账、其 3 行级按键取修复（R1 §3.3 选项 1）待主会话裁定。**台账落笔红线：不得在不带 PARTIAL 限定的情况下写 V-01 关闭**。
3. **A-10 清单口径**：设备门 3/3 绿含终答位子门；清单行维持"部分落"（"耗尽→文字降级"的编排半边登记 + 跨 method 合计最终图无直接协调器级测试——覆盖缺口登记）；是否转"通过"归主会话按清单核验列裁定。
4. **release 窗口三项 raw 缺口**（§4.4）：UNVERIFIED-raw 如实登记，无法事后补采。
5. **生产签名 UNVERIFIED**（一次性本地 key）；arm64 真机/16KB release 面/API 23 全套件维持既有缺口。

**代码 / 文档残留**

6. **KD-82 open 维持**：program 半边已对齐 + JVM 4 例出数；**imagePrompt 残余待广告解冻批裁定**（4096 码点 vs 4096 字节门，最坏 4× 过度广告；Fix direction ①=4096→1024）；"任何文档/证据不得把现 schema 写成与本地门同一口径"的禁令对 imagePrompt 继续有效。
7. **KD-73 设备/R8 冒烟半边未做**（回链已落、open 维持）；**KD-72** 窗口不满足维持（本批无新动作）。
8. **契约 `docs/current/visual-generation-contract.md:208`** 过时句：热区 7 M 未清空 → 登记不落笔（补记 11/12 + 00-index §7-8）。
9. **K4 字面 grep 口径差**（批次 36 遗留）：判据 §1.1 已按注释感知门登记口径；字面口径差维持登记。
10. **判据字面"import ≥1"同包不可满足**（§5.1 #11）：语义本体已验证，口径差登记。
11. **T2 申报 import 计数 29 vs 实测 57**：精度偏差登记（非实质）。
12. **T4 负例算术更正**：任务原文"16384 个 4 字节字符过 schema 被本地门拒"算术不成立（16384×4=65536 恰在含边界门内=放行）——测试取 16385（65540 字节被拒），更正在 KDoc :36-38 与台账登记。

**环境 / 口径**

13. **工作树计数滚动**（688→696→738）差值未逐项追认；他线在飞内容功能正确性归各线。
14. **设备时钟差**；证据目录只写宿主窗（沿批次 36 惯例）。
15. **4E.2 MVA 仍不成立**：本批落的是 V-01 app 版真隔离链、A-10 夹具与子门、KD-82 program 半边；双后端就绪、质量门、JS 广告均未宣称；一致性门（step11）仍是原型门。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前按门禁序列复跑单测门/强门并留 exit=0 日志；热区、全部 `*.lockfile`、`build/`（含设备原始日志与 R-04 keystore）、`tmp/`、`docs/current/**`、`tools/kb*`、`knowledge-production/**` 一律排除；共享脏文件整文件纳入并在提交说明注明本批与他线来源。按 ≤6 路径/块：

- **块 1（V-01 app 版 + 降级标注）**：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt`（??）、`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/V01VisualChainInstrumentedTest.kt`（M +13/0；**整文件纳入**：+13 全为 KDoc 降级标注）
- **块 2（A-10 夹具）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`（M +69/−5；**整文件纳入**——该文件开工时干净，整文件变脏系本批夹具修复所致）
- **块 3（KD-82）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/OpenAiModelProtocol.kt`（M +8/−1；**整文件纳入**——共享脏文件，diff 恰一 hunk）、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/ProgramSchemaMaxLengthAlignmentTest.kt`（??）
- **块 4（研究与判据）**：`docs/research/2026-10-31-v01-isolated-wiring.md`（??）、`docs/research/2026-10-31-r04-k6-verify.md`（??）、`docs/research/2026-10-31-batch-37-device-criteria.md`（??）、`docs/research/2026-10-31-batch-37-baseline.md`（??）
- **块 5（证据）**：`docs/research/evidence/2026-10-31-batch-37/`（8 文件：00-index.md + step-device-track.md + 六件 KD-77 迁移 00–05）
- **块 6（记录 + 台账，整文件纳入）**：`docs/research/2026-10-31-batch-37-record.md`（本文件，新）、`docs/known-defects.md`（M；KD-77 closed + KD-73 回链 + KD-82 批次 37 块 + KD-82 记录员收口注记）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（M；v3 补记 11/12 + 13 + A-10/V-01/R-04 行注记）、`docs/REFACTOR-MASTER-LINE.md`（M；批次 37 滚动行）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；批次 37 节）

**明确排除（他线在飞/不安全）**：`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/ui`、`core/domain`、`core/markdown`、`core/export`；core/data 他线 M（`RoomMistakeDetailRepository.kt`、dense 面、`OpenAiNativeToolsProtocolTest.kt`、`SafeTruncationTest.kt`、`RoomTutorToolRunner.kt`、`TutorToolDescriptions.kt` 及 core/model 其余 M 等）；core/database 其余 M；app 其余 M（Root、ReviewReminder*、SecondaryScreens 等）；`docs/current/**`；docs/research 他线未跟踪记录；`build/**`；`tmp/**`；全部锁文件。

## 9. 收口状态

- **研究**：R1 224 行（装配形态/度量器根治/ServiceTestRule 拒用/可达性误判被证伪登记）；R2 282 行（判据逐字复核/三值预检/预检②结构性不可满足的 L8 机制一手证据/K6 六行归因表/Keeper 解锁路径/外部 9 项评分）落盘；未验证项如实。
- **实施**：T1 判据 518 行独立文档；T2 app 套件 3 例 + 两夹具缺陷根治 + core/data 13 行标注（生产零触碰）；T3 A-10 夹具 +69/−5（生产链路 QUEUED + 子门①②）；T4 KD-82 program 面 +8/−1 + JVM 4 例（牵动面零回退 XML 实证）；T5 KD-77 关闭 + KD-73 回链 + 补记 11 + 契约 :208 登记；T6 分支 D 零落盘；集成无需接线。
- **设备**：20 步全绿（34/2/5/9/5/15/4/3/12/12/16/24/16/15/2/1/6/27 + 3 + 3，全部=构建前计数；step14 环境红舞步后绿）；21 份 logcat 签名收口；R-04 K1–K5 全绿（K5b 双口径补批次 36 欠账）+ 三值预检②不过（P2→选项 B）+ **K6 红=机制① → R-04 阻塞维持**；步 5/步 6 补齐（debug 态恢复）。
- **门禁**：五轮 exit=0（工单给定）；KD-82 新套件 4/0 与牵动面 14/11/5/15 零回退（XML 实读）；keep 门/S=71 门/stdlib 门多轮实跑全绿。
- **台账同步（本轮记录员写入）**：`docs/known-defects.md`（KD-82 记录员收口注记：JVM 出数 + open 维持口径）、`docs/REFACTOR-MASTER-LINE.md`（批次 37 滚动行）、准入清单（v3 补记 13 + R-04 行批次 37 回链）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 37 节）；均标注"记录员只读核对，未运行 Gradle/设备"。命令与输出见 §10 与 evidence 目录。
- **本批不声称全绿/全就绪**：R-04 阻塞未解除（K6 机制①红）；V-01 PARTIAL（3/4）；KD-82 open（imagePrompt 残余）；A-10 清单行未转"通过"；JS 仍 UNAVAILABLE 不广告（availableMethods 未动）；生产签名/arm64 UNVERIFIED；4E.2 MVA 不成立。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse HEAD                                   # 5ee1f90f7e2411a6c1865890f6bf5da21f2b57aa
git status --porcelain | wc -l                       # 738（多线在飞滚动）

# 关键 diff（本会话 git diff --numstat）
#   OpenAiModelProtocol.kt = 8/1（+8/−1，与复核更正一致）
#   FigureDispatchBudgetBoundaryInstrumentedTest.kt = 69/5
#   core/data V01VisualChainInstrumentedTest.kt = 13/0（全 KDoc）
#   VisualJsClientEngine.kt / VisualJsService.kt：git status 空输出（生产零触碰）

# 套件形态（本会话 grep）
#   app V01VisualChainInstrumentedTest.kt：@Test=3、QuickJsVisualExecutionEngine 仅 :68 KDoc、
#     '^import ' = 57、VisualJsClientEngine(context) :500-501
#   core/data FigureDispatchBudgetBoundaryInstrumentedTest.kt：@Test=3
#   K6 两方法：VisualJsIsolationInstrumentedTest.kt :123/:245
#   AndroidManifest.xml:72  android:process=":visual_js"

# KD-82 JVM 出数（本会话 python -I 解析 JUnit XML；执行=门禁串行，记录员未复跑 Gradle）
#   ProgramSchemaMaxLengthAlignmentTest   tests=4 failures=0 errors=0 skipped=0
#   TutorFigureMethodDualParseTest        tests=14 failures=0 errors=0 skipped=0
#   TutorFigureProposalCallTest           tests=11 failures=0 errors=0 skipped=0   (core/model)
#   FigureGenerationLocalBudgetBoundaryTest tests=5 failures=0 errors=0 skipped=0
#   ModelWireProtocolSourceJsFidelityTest tests=15 failures=0 errors=0 skipped=0

# 研究与判据文档（本会话 wc -l）
#   v01-isolated-wiring.md=224 / r04-k6-verify.md=282 / device-criteria.md=518 / baseline.md=174
#   evidence/2026-10-31-batch-37/：00-index.md=180、step-device-track.md=255
#   R2 锚点：'# L8 Desugaring Mapping' 段 :39430/:860808、shrinker_config 语义、Keeper 选项 A :178
#   R1 锚点：:94 可达性句（被证伪原句）、:170 "4 例"计划

# 设备原始产物（build/device-track-batch37/，本会话 ls/计数 + 抽查；未复跑设备）
#   90 文件；r04-k6.log（崩溃栈原文）、r04-k6-signature-count.txt、r04-three-value-precheck.txt、
#   step19-v01-app.log / step20b-a10-budget.log、pre-build-counts.txt 等在盘

# 未执行：任何 ./gradlew、adb install / am instrument、connectedAndroidTest；
#         任何 commit/push/add；任何设备写。
```

## 附录 A：台账同步（本轮记录员写入）

| 文件 | 写入内容 |
|---|---|
| `docs/research/2026-10-31-batch-37-record.md` | 本文件（00-index §8 登记的记录欠账在此收口；§4 全部以 E-ID + path:line 回链） |
| `docs/known-defects.md` | KD-82 条目追加"记录员收口注记"（JVM 门 4 例 XML 出数闭合 UNVERIFIED 空槽；KD-82 open 维持口径与 imagePrompt 残余边界重申）。**该文件为共享脏文件（开工时已 M：T5 的 KD-77 closed + KD-73 回链 + 复核修复轮的 KD-82 批次 37 块在飞），整文件纳入并注明**。KD-77/KD-73/KD-82 批次 37 块本体由 T5/修复环先行写入，本轨不重写；R-04 非 KD 条目，其裁决落清单 R-04 行 + 本记录 §4.3 |
| `docs/REFACTOR-MASTER-LINE.md` | 新增"最近计划维护：2026-10-31（批次 37 …）"滚动行；原批次 36 行降为"上次维护" |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | 头注 v3 补记 13（批次 37 记录员收口）；R-04 行追加批次 37 回链（K1–K5 绿 + 三值预检②不过 P2→选项 B + K6 红机制① → 阻塞维持、两合规路径待主会话裁定）；**该文件为共享脏文件（开工时已 M：T5 补记 11 + 复核修复轮补记 12 与 A-10/V-01 行注记在飞），整文件纳入并注明** |
| `docs/agent-first-refactor-decisions-2026-09-23.md` | 追加"批次 37 V-01 真隔离迁移 + R-04 K6 全链执行 + KD-82 program 对齐 + KD-77 关闭裁定（2026-10-31）"节（裁定内容 + 边界/待裁） |

以上均为 docs 追加/刷新；零 Kotlin/JS/Gradle 产品代码改动、零 git 写、零 Gradle、零设备写。

## 10. 主会话收口（2026-10-31，提交与推送执行者实跑）

- **双门**：单测门 `UNIT_EXIT=0`（`BUILD SUCCESSFUL in 49s`，3 任务执行）；强门 `STRONG_EXIT=0`（`BUILD SUCCESSFUL in 24s`）。日志在 `build/main-session-gate-batch37/`。
- **KD-82 证据槽回填**：主会话单测门实跑执行 `ProgramSchemaMaxLengthAlignmentTest` → `tests="4" skipped="0" failures="0" errors="0"`（`core/data/build/test-results/testDebugUnitTest/`）。**裁定：KD-82 保持 open**——program 半边已对齐且有 JVM 证据（schema facet=MAX/4=16384、本地字节门 65536 零改动）；**imagePrompt 残余**（`:552` 仍广告 4096 码点面对 4096 字节门，Fix direction 4096→1024）未处理，整条关闭即反向虚报。残余归批次 38。
- **R-04 裁定：阻塞维持，解锁路径登记**。K6 设备实测仍红（机制①：`NoSuchMethodError Lj$/util/g; <init>(Ljava/util/Map;)V`，两例 0 跑）；R2 研究产物级证据证明预检②**结构性不可满足**——j$ 改名由每变体独立 L8 编译治理（desugar.json shrinker_config + AGP 生成式 L8 keep），`testProguardFiles` 用户 keep 到不了该阶段（批次 36"测试 dex 全量让名"预测被产物证据否定）。唯一合规解锁=Keeper 类 L8 规则合并（Apache-2.0，AGP 9.3 兼容性 UNVERIFIED），**触 `settings.gradle.kts` 热区**——按热区纪律推迟到该文件可提交时执行；不引 `-dontobfuscate`、不换 internal 变体。
- **V-01 裁定：PARTIAL（3/4 子项）**——app 版真隔离引擎 3 例设备绿、夹具缺陷根治（按键取断言）、零派发四断言位保持；④ PDF/painter/restore 腿因 core:data internal 跨模块不可达留降级变体，本批无设备证据。台账不得写 V-01 关闭。
- **A-10**：夹具修复走生产链路（createModelTask→transitionModelTask QUEUED），3 例设备绿 + 终答位子门两道新增断言，加强非放宽。
- **提交**：6 块（V-01 app 版+降级标注 / A-10 夹具 / KD-82 / 研究判据 / 证据 / 记录+台账），全部显式路径，提交前验暂存区。

### 10.1 暂存区卷入事件（2026-10-31，主会话登记）

`1912180a..HEAD` 实际提交 **91 个路径**，其中约 75 个为 KB 线（另一会话）已暂存未提交的成果：知识包资产（`core/data/src/main/resources/knowledge/*`、dense 向量）、`tools/kb_build`/`tools/kb_coverage`/`tools/ci/check_pack_sync.py`、`core/data`+`core/database` 的 knowledge 面（含 `RoomStudyDatabase.kt`）、`docs/kb-batch26~29` 等。归因：KB 会话先暂存未提交，本会话逐块 `git add 显式路径` 后 `git commit` 把整个暂存区一并提交。
**处置=保留不回滚**：这批文件互相引用且整体在工作树状态通过批次 37 全部门禁（单测/强门/设备 20 步）；部分剥离会在 origin/main 制造破损树。KB 批次 26–29 的成果已随本批到达 origin/main，但**提交归属误记在本线提交信息下**——KB 会话后续提交时请以本节为准核对，其自有提交说明缺口由本节弥合。
**流程加固（本会话即刻生效）**：此后每次 `git commit` 前必须 `git diff --cached --name-only` 对照申报清单，发现申报外路径立即 `git reset` 该路径（不 reset 整个暂存区，避免破坏他线暂存意图）再提交；已在本会话后续批次执行。
