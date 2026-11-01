# 批次 28 记录（2026-10-28）：SceneV1 修复后设备全绿复跑 + painter 落码与设备冒烟 + MVA 剩余项排序 + 设备轨五步

> 记录时点：2026-10-28（+08:00，批次开工 2026-10-28）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、两研究文档、七件设备 E-file、`build/device-track-batch28/` 原始日志、`git status` 与关键源码锚点后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改产品代码。** 本记录员本轮唯一测试执行 = Node 编译器测试台（`node tools/visual_compiler/run_tests.mjs`，89/89 EXIT=0）。
> 计划：`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`（下称"计划"，22 行已完整读）。
> 基线：`docs/research/2026-10-28-batch-28-baseline.md`（下称"基线"；只读核对文件）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环第 1 轮**全部处置完成**（dash 缓冲串状态【高】已修复并加回归例；`#RRGGBBAA` alpha 覆盖【中】与 T2 三项移交并入 KD-78；T3 移交未闭合【中】已由 KD-78 + 契约注记补上）。
> **本记录不声称"批次全绿"**：**修复轮新增的两例 painter 设备用例（残尾 dash / 空 dash）设备复跑 UNVERIFIED**（设备 17/17 是修复轮前产物，现树 19 例）；**R-04（R8 下隔离往返）阻塞维持**（当前树两重新鲜复现，"接线修复后应可过"被实测否证）；**KD-76 保持 open**（③ 产品面/29.5 s 链/READY_TIMEOUT 设备校准/arm64 未出数）；KD-72/75/77/78 open；**4E.2 MVA 仍不成立**（painter 屏半边落层 ≠ PDF 回放/持久化/双后端/质量门就绪）；arm64/真机、生产 release 签名、真实 Provider/模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）与实际执行面

- **做**：R1 painter 设计参考（DrawOps V1→Android Canvas 映射）；R2 4E.2 MVA 剩余项排序；R3 锁窗口再判定；T1 修复后设备复跑（隔离 13 + Race 2 + 编译器套件 + 引擎 26）；T2 painter 落码（Rendered→Bitmap 冒烟，PDF 回放留后）；T3 台账/契约同步；T4 热区条件轨；设备轨（五步 + R8 往返复跑）；证据落盘；双门；双复核；修复；记录；提交推送。
- **实际执行面**：两研究线落盘（R1/R2）+ 四实施轨（T1–T4）+ 设备轨五步（E-01…E-05）+ 证据落盘（7 个 `.md`）+ 复核（含两条先红后绿的测试层更正）+ 修复环第 1 轮 + 本记录与台账同步。
- **不做**：4E.2 交互件与保真（后段）；JS 广告（MVA 未过）；PDF `/AIGC`（KD-75，热区）；锁文件（窗口未开）；热区 UI 实现；真实 Provider；Wave X。**全部未做**（设备轨 `R-04` 只做 release 往返复跑、零构建改动；painter 未写任何 PDF/持久化/导出代码）。

### 1.2 沿用裁定（本批执行，未改）

1. **JS 不广告**：MVA 未成立；能力快照/提示词/契约广告文本本批零改动（T1/T2 footprint 核实；R2 排序**建议**把广告开关列为全门绿后的最后动作）。
2. **4E.2 交互件/保真与 PDF `/AIGC`（KD-75）不做**；锁文件不生成（KD-72 等窗口）。
3. **AIDL 只以 Gradle 任务验证**；AAR `quickjs-kt-android:1.0.15` 与 stdlib 2.3.21 不变。
4. **painter 只做 DrawOps V1→Canvas 映射与 Rendered→Bitmap 冒烟**；PDF 回放/持久化/导出留后续（R2 排序批次 29/30/31/32）。
5. **真实 Provider 无凭据**：模型质量 UNVERIFIED。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`8116ad98`（`git rev-parse --short HEAD` 记录员实测）= 基线 §1 实测值 = `origin/main` **本地跟踪引用**（`git rev-parse origin/main` 实测同值；**未 `git fetch`**，远端实时 hash UNVERIFIED）。
- **工作树**：基线冻结（2026-10-28 05:24:16）**673 行 = 201 M + 472 ??，staged 0**；**记录时点（本记录写盘前）685 行 = 205 M + 480 ??（实测），staged 0**——增量恰为**本批声明落点 4 M + 8 ??**：4 M = `QuickJsSceneCompilerInstrumentedTest.kt`（测试面更正）、`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`；8 ?? = 4 个 painter 文件 + 3 个研究文档 + `docs/research/evidence/2026-10-28-batch-28/` 目录。**本记录与三份台账同步写盘后实测 689 行 = 208 M + 481 ??**（+3 M：`REFACTOR-MASTER-LINE.md`、`algorithm-version-ledger.md`、`agent-first-refactor-decisions-2026-09-23.md`；+1 ??：本文件），staged 仍 0。
- **版本串**：DB schema 69（`StudyDatabase.kt:134`）、请求 schema 18（`ModelTasks.kt:546-547`）、`PROTOCOL_VERSION` 2（`VisualJsProtocol.kt:32`）、`drawSchemaVersion` 1——记录员本轮 grep 复核**全部未动**；算法台账 §3.38 零 bump（本记录员登记）。
- **落点基线**（基线 §3）：四个落点开工时**全部干净**且在 HEAD：`core/data/src/main/kotlin/.../visual/`（10 文件）、`app/src/main/kotlin/.../visualjs/`（5 文件）、`docs/known-defects.md`、`docs/current/visual-generation-contract.md`；painter 输入面 `visual-compiler.js`/`visual-sdk.js`/`tools/visual_compiler/run_tests.mjs` 亦干净。
- **热区**（基线 §2 实测）：feature 五模块（tutor 37/library 30/capture 15/review 11/profile 3 M）、core/ui 11 M、core/markdown 整目录未跟踪、core/export 5 M、core/domain 4 M、core/data 模块 10 M（src/main 2）、`settings.gradle.kts`、`.gitignore`、Root/SecondaryScreens 全脏——本批零越权（§5 确认项）。

## 2. 两研究线与复核结论

### 2.1 R1 painter 设计研究（`docs/research/2026-10-28-painter-design.md`，记录员实测 236 行，唯一落盘）

- **核心发现（§0/§2.2/§3.4）**：DrawOps V1 的坐标空间是**二分**的——`path` 携带局部坐标（随 `concat` 回放），而 `clip`（烘焙组合坐标）与 `label`（投影后坐标）携带**根空间**坐标，却发射在组的 `concat` 之后。朴素顺序回放在**嵌套变换组内的 clip** 和**任何变换组内的 label** 两类场景必然二次变换出错。painter 必须自维护矩阵镜像，对 clip/label 走"`setMatrix(root) → 应用 → setMatrix(mirror)`"舞步（`Canvas.setMatrix` 非 deprecated；`getMatrix` 已 deprecated 故不用）。推导依据：编译器 `visual-compiler.js:820-824,853,857,918-927`（clip 用 composed 烘焙、concat 发 localMatrix、label 投影一次算定）与 Node 台金样 `run_tests.mjs:221-223`（label x=107−10×0.5=102）。
- **落位与分层（§3.1）**：`core:data` `visual` 包 `internal`（不新建 `core:visual` 模块——`settings.gradle.kts` 热区）；**纯计算层（JVM 可测，无 android 符号）与应用层分离**——`VisualMatrix.toAndroidValues()`（9 值装配 `[a,c,e,b,d,f]`）、`parseDrawOpsColor`、`drawOpsAlpha255`（floor）、label 内容 DTO。
- **状态机与 Paint（§3.2）**：实例持有、线程独占；`pathPaint`/`textPaint` 常驻 + **每次绑定无条件设置全部相关字段**（style/color/alpha/strokeWidth/cap/join/miterLimit/pathEffect，fill 相位显式清空 DashPathEffect）；`textPaint` **严格复制度量 Paint 配置**（`Paint()` 默认 flags + 仅 textSize）——parity 规则。
- **冒烟判据（§4）**：判据 1 = 同设备同指令同像素（`Bitmap.getPixels` 整阵 `Arrays.equals`，两遍 + 跨测试方法各一遍）；判据 2 = 结构探针（矩阵列序正例 + 误装原序负例、嵌套 clip、alpha floor 手算混色、label 双对齐负例、背景/角点、状态隔离、stroke 相位）；判据 3 = fail-closed 矩阵（6 码 → 实落 8 码）。**不主张跨设备/跨 API 字节一致**（字体/Skia 版本差异，UNVERIFIED）。
- **GitHub 与官方源（§1）**：lottie-android（Apache-2.0，35,757★，pushed 2026-02-15）、androidsvg（Apache-2.0，`setMatrix` 复位舞步先例 `SVGAndroidRenderer.java:596-620`、dash `i%n` wrap `:2477-2495`）、react-native-svg、MPAndroidChart 评分表（`gh api` 2026-10-28）；**只借鉴模式、不引依赖**。AOSP 原文核证：`Canvas.concat`=preconcat、单参 `clipPath`=INTERSECT 且非 deprecated、`Canvas.setMatrix:809` 非 deprecated/`getMatrix:824-825` deprecated、`drawText` y=baseline、Matrix 9 值序（`Matrix.java:36-44`）→ `[a,c,e,b,d,f]`、`DashPathEffect` 要求**偶数且 ≥2**（Java 侧只拦 `length<2`）。
- **缺口（§5）**：① stroke-only 合法但无颜色来源；② dash 未钉偶数；③ measure/replay flags 不在环境指纹；④ typed doc 产出位置（painter 不收裸文本）。①②③ 后由 KD-78 收口登记。

### 2.2 R2 4E.2 MVA 剩余项排序（`docs/research/2026-10-28-mva-remaining-order.md`，记录员实测 237 行，唯一落盘）

- **排序（§0）**：批次 28 T1 为**一切设备面的公共前提**；T2 painter 解锁**批次 29**（PDF 回放半场 + 屏/PDF 一致门雏形 + S-04 序列化往返 + R-05 许可证载体）→ **批次 30**（持久化 D-01 四表 v70+/D-02 身份+CAS/D-05 冷恢复，与 31 可换序）→ **批次 31**（双后端装配 A-01～A-06/A-08/A-13 + P-11 + P-12②③ + M-04 + Q-01，**广告开关不动**）→ **批次 32**（V-01 全链 + V-02/A-09/A-10 + Q-04，P-04/P-09/P-10 设备负向并轨）；发布矩阵批与热区解锁批**挂起**；持续/裁定项（Q-05 盲测、KD-72 锁窗口、KD-76 ③ 残留与 R3 文本升级）。
- **现状复核（§1/§5）**：本轮 grep 实测——持久化四表符号全仓 0、`VisualSnapshotPdfBlock` 0、`AndroidScenePainter`/`class VisualDrawOpsPainter` 0（写作时点在 painter 落码前）、DB=69、proguard 无 visualjs/quickjs keep。A4 登记 **hitRegions 口径差**（编译器 `:950-975` 允许非空并透传，清单 §1.1 要求空/不存在）待主会话一句话裁定。
- **解锁条件（§3）**：KD-76 五条（③ 判定、29.5 s 链、READY_TIMEOUT 校准、修复后设备全绿、R3 文本升级归主会话）；KD-72 五条（6 build 面入库、干净树重生成、覆盖补齐、负控、CI step）；KD-75 六条（热区空出、`PdfAigcInfoInjector` append-only、双写入判据、缓存升版、验收闭环、与 MVA 关系）。
- **GitHub 优先纪律**：painter 不引第三方库（R1 §0 已否决 14+ 候选为依赖）；本轮复核 lottie-android 仍 Apache-2.0/未归档，仅作矩阵桥接参考。R8 测试面社区解法（testProguardFiles/testable 变体）标注**单一来源待验证**，不替代主会话裁定。

### 2.3 研究复核结论

- 两份研究文档均在盘、行数实测吻合（236/237）、有来源分级与未验证清单；T2 落码逐项对齐 R1 §3（落位/分层/矩阵列序/舞步/Paint binder/parity/DTO/异常面/判据），T3 与 R2 排序逐项对齐（S-04/S-05/S-06、§2 行 6、E-05、R-05、组级排序注记）。
- R2 明标"**不是排期裁定、不是广告许可**"，与计划/纪律一致；T1/T2 在 R2 写作时点在飞，R2 只按"完成后"排序、不预填判定。
- R1 §6 的"未验证项"在设备冒烟落地后部分转实测（嵌套 clip、组内 label 均由 E-03 探针实测）；**跨设备字节一致与奇数 dash native 行为仍 UNVERIFIED**（后者按"无规约" fail-closed 处置，不猜测）。

## 3. 四实施轨 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 工具实跑 / 无证据=UNVERIFIED）。文件清单按工单 JSON、`git status` 与源码实读交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 设备复跑核验 | 完成 | **判据表成立（七项中六项有设备/工具实跑证据、一项补充面绿）/ 设备执行归设备轨** | T1 轨只读+判据准备，未跑设备；记录员逐项对 raw log 复核 |
> | T2 painter 落码 | 完成 | **落码成立（4 新文件、零既有改动）/ JVM 10 例为轨自报未复跑 / 设备 17/17（修复轮前）** | 记录员实读源码锚与 E-03；现树 19 例、后 2 例无设备证据 |
> | T3 状态同步 | 完成 | **完成（3 文件、纯文档；69 行不变、0 项转通过）** | 三文件编辑前干净、编辑后 3 M；时点注记后被证据超越处已由记录步刷新（§5/§8） |
> | T4 条件轨 | 完成 | **完成（分支 D 登记，零改动、零文件落盘）** | 九排队项逐项复核全部仍 pending |
> | 设备轨 | 五步执行完毕 | **五步全执行：两套件 PASS / 编译器+引擎 PASS（先红后绿）/ painter PASS（先红后绿）/ 备份 PASS / R-04 阻塞维持** | 判定边界见 §3.5/§4 |

### 3.1 T1 修复后设备复跑（设备轨执行；记录员逐项对 raw log 复核）—— **四套件全绿**

- **判据表七项核销**（工单 T1 给定，记录员实读 `build/device-track-batch28/` 原始日志逐项验证）：
  1. **隔离套件** `VisualJsIsolationInstrumentedTest`：`OK (13 tests)` / `Time: 61.749` / `INSTRUMENTATION_CODE: -1`（`step01-isolation.log` 实读）。批次 27 的 10 红（`Tests run: 13, Failures: 10`）**全转绿**；3 个原本不达 compile 的绿例（两注入夹具 + BUSY 负向）保持绿。
  2. **Race 套件**：`OK (2 tests)` / `Time: 1.078`（`step01-race.log` 实读），不回退（fake-binder 面，无 worker spawn）。
  3. **编译器套件**（`QuickJsSceneCompilerInstrumentedTest`，6 例，**设备首跑**）：v1 `Tests run: 6, Failures: 1`（首红点 `aFullSceneCompilesIntoTypedDrawOps` :144 `expected:<1.0> but was:<0.5>`）→ 测试层更正 → v2 `OK (6 tests)` / `Time: 0.554`（`step02-compiler.log`/`step02-compiler-v2.log` 实读）。含 `aMaxBudgetSceneCompilesWithinTheCooperativePhaseWindow`（256 节点/4080 展开命令）落在 100 ms 协作期内：**套件总时长 0.554 s、未触 PHASE_TIMEOUT、无需预算复判**（不放大协作期）。
  4. **引擎套件**：`OK (26 tests)` / `Time: 8.034`（`step02-engine.log` 实读）；批次 27 的 9 红全转绿、17 绿保持绿（进程内 QuickJS，无隔离 worker）。
  5. **签名/worker/kill**：9 类崩溃签名对 6 份套件 logcat（isolation/race/compiler/engine/painter/backup）**逐文件计数全 0**（记录员本轮 `grep -cE` 实测 6×0）；`Start proc …:visual_js` **17** ↔ `warm-up finished` **17**（记录员实测两计数；E-01 §4 按 pid 集合比对 1:1，唯一额外 `Start proc` 为测试进程 15298）；kill 事件 **5 + 4 + 7 + 1 = 17**（记录员实测：`hard deadline (1500 ms) for request` 5 条请求行 / `terminate(generation` 4 / `host unbound the visual_js worker` 7 / AMS `due to start instr` 1）——**零意外杀**，5 条 `has died` 均对 watchdog 负控注入（记录员实测 `has died`=5）。
  6. **备份抽验**（补充不回退）：`OK (34 tests)` / `Time: 45.831`（`step04-backup.log` 实读），与批次 27 逐值一致。
  7. **Node 台前置**：`node tools/visual_compiler/run_tests.mjs` → `89 passed, 0 failed`，**EXIT=0（记录员本轮实跑；upper-bound 计时 2.1 ms，为同一命令的运行间信息性差异，非判据）**。
- **两处先红后绿（均归因测试层，产品源零改动；整文件纳入登记）**：
  ① **编译器 dot alpha 期望 1.0→0.5**（`QuickJsSceneCompilerInstrumentedTest.kt:141-151`，记录员 `git diff` 实读单 hunk +9/−1）：四源证据链——契约 `visual-generation-contract.md:98`"group alpha 乘到子节点"、编译器 `visual-compiler.js:860`（`opacity * groupOpacity` 合成下传）与 `:884`/`:937`（path/label 均发合成 alpha）、Node 台 `run_tests.mjs:219`（`label.alpha === 0.5`）、V8 探针 `build/device-track-batch28/probe-path-alpha.log`（记录员实读：q/dot/label 三值皆 0.5）→ **0.5 是契约值，原期望是批次 27 T1 新写测试的缺陷**；delta 仍 0.0（非放宽），并补了 Node 台未覆盖的 path 面断言。
  ② **painter 夹具建位图顺序**（`VisualDrawOpsPainterInstrumentedTest.kt`，T2 未跟踪新文件内更正）：`anUnusableReplayScaleFailsClosed` 原用坏 `pxPerDu` 先算位图尺寸（0×0 → `Bitmap.createBitmap` 抛 `IllegalArgumentException`，崩在**夹具** :71），painter 的 `PAINTER_BOUNDS` 入口门（`VisualDrawOpsPainter.kt:81-83`，先于 `:84` `rootMatrix.setScale`）从未被执行；修复 = `render` 增第四参 `bitmapPxPerDu` 解耦位图尺寸与回放比例（默认=pxPerDu，16 个既有调用点逐位不变）。
- **KD-66/70/76 不受影响核对**：KD-66（closed）的 `aHungWorkerReleasesTheCallerWithinHardPlus500ms` 在 E-01 新源上重验绿，5 条 watchdog hard-deadline 全对负控注入，无 reopen 条件；KD-70（closed）5 个内存例在引擎套件 26/26 内保持绿、修复不触内存归属面；KD-76 仍 open（见 §3.3/§6），E-01 只刷新 ⑤ 行为层证据。

### 3.2 T2 painter 落码 —— **落码成立（4 新文件、零既有文件改动）/ JVM 10 例为轨自报 / 设备 17/17（修复轮前）**

- 文件（4，均 `??` 新文件；记录员实测行数）：`core/data/src/main/kotlin/.../visual/VisualDrawOpsPainterPure.kt`（187 行，零 android 导入）、`VisualDrawOpsPainter.kt`（311 行）、`core/data/src/test/.../visual/VisualDrawOpsPainterPureTest.kt`（173 行，**10 个 @Test**）、`core/data/src/androidTest/.../visual/VisualDrawOpsPainterInstrumentedTest.kt`（586 行，**现 19 个 @Test**；设备运行时 17）。
- **纯计算层**（记录员实读）：`VisualMatrix.toAndroidValues()`（`:85`，列序 `[a,c,e,b,d,f]` + 透视行 `0,0,1`）、`drawOpsFloat`（`PAINTER_BOUNDS` 窄化门 `:96-99`）、`parseDrawOpsColor`（严格 `#RRGGBB`/`#RRGGBBAA` 词表 `:112-132`，拒 `Color.parseColor` 宽容形态）、`drawOpsAlpha255`（floor，`:140-142`）、`VisualLabelContent` DTO（Text 携带显式行段 + heightDu；Math 本批 fail-closed）、9 个 `PAINTER_*` 码（记录员 grep：SCHEMA_VERSION/BALANCE/UNKNOWN_LABEL/STROKE_WITHOUT_FILL/DASH_ODD/LABEL_MATH/BOUNDS/COLOR/ALPHA）。
- **应用层**（记录员实读）：入口门 `drawSchemaVersion`（`:75-79`）与 `pxPerDu` 有限/正（`:81-83`）；`canvas.setMatrix(rootMatrix)` 后 `drawColor(background)`（`:87-89`）；`concat` = `canvas.concat` + `mirror.preConcat` 同步（`:108-112`）；`clip` = `setMatrix(root) → clipPath → setMatrix(mirror)`（`:118-123`）；`label` = `save → setMatrix(root) → translate/rotate → 逐行 drawText → restore`（`:196-203`），**不重放对齐偏移**（hAlign/vAlign 是回显元数据，编译器已折进 x,y）；`fill` 先画、`stroke` 后画且 stroke 色取 fill 色（R1 §5-① 过渡裁定）；`hitRegions` 不绘制；`textSize = sizeSp × fontScale`（**du 空间值**，KDoc 登记 R1 §3.5 句面的 ×pxPerDu 是设备 px 目标、照抄会双乘，`:259-270`）。
- **修复环 F1（【高】dash 缓冲复用串状态）**：删除常驻 `dashBuffer`/`ensureDashCapacity`，`bindPathPaint` 按 `FloatArray(dash.size)` 精确分配（`:245-250`）；`playDrawPath` 前置形状门扩为 `size % 2 != 0 || size < 2`（`:149-154`，空 dash 折进同一 `PAINTER_DASH_ODD` 有码族，位置在 fill/stroke 两相位之前）；回归例 `aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern`（`:251`，victim 带内 strip 与"只画 victim"逐位一致）+ `anEmptyDashFailsClosedEvenThoughValidationAcceptsIt`（`:323`）。
- **测试**：JVM 10 例（列序正/负例、平移列、透视行、`PAINTER_BOUNDS`、颜色 8 断言、floor 钉、`PAINTER_ALPHA`、诊断口径、DTO）——**轨自报真跑全绿，记录员未运行 Gradle，UNVERIFIED**；仪器化套件覆盖 R1 §4 全量判据（同设备确定性 ×2 方法、切变列序探针、嵌套 clip、组内 label + parity 钉、alpha 手算 `0xFFFF8080`、背景/透明角点、状态隔离、stroke 相位几何探针、fail-closed 8 例、真编译器 e2e `aLegalSceneGoesFromCompileToColoredBitmap`）。
- **不做边界**：未写任何 PDF 回放/持久化/导出代码（计划 T2 明定"PDF 回放留后"）；math 盒走查 fail-closed（`PAINTER_LABEL_MATH`），留回放/PDF 批与 core/ui parity 核对同做。

### 3.3 T3 台账/契约同步 —— **完成（3 文件、纯文档；69 行不变、0 项转通过）**

- 文件（3，均 `M`）：`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`。
- **KD-76**：新增批次 28 T3 Progress 块（记录员实读 `known-defects.md:2853`）：按批次 27 E-04 数字（p50=686/p95=725/max 738 ms、16+10=26/26 ready）归一为"装载成本已出数 + 剩余解锁条件清单"（③ 产品 UI 面 / 29.5 s 链 / arm64 口径归发布矩阵批 / R3 文本升级归主会话）；**open 与 JS 不广告维持**。
- **准入清单**：头部 v3 补记 2、§0 责任轨六行 R2 排序归属、S-04（未落→部分落，DTO 已立/往返无测试）、S-05（部分落 + hitRegions 口径差登记待裁，编译器锚 `:732`/`:738`/`:950-975`/`:1077-1088` 实证）、S-06（painter 过渡落点 `core:data` visual 包）、§2 行 6/E-05/R-05 行内注记、§3.4/§3.6 组级排序注记、附录 E 核对清单；**69 行不变、0 项转通过、MVA 不成立维持**（记录员 `git diff` 实读 67 行改动 = 头部补记 + 六行责任轨 + 三行 S + 行 6/E-05/R-05 + 两组排序注记 + 附录 E）。
- **契约 §5/§5.1**：三处 painter 落层注记（表头时点、`:115` 现状行、`:119` 归位段）；§6 grep 证实无 painter 锚（如实登记）。
- **时点边界（复核发现）**：T3 写于 T1/T2 出数前，S-04/S-06/§2 行 6 与契约 `:115` 的"落码未发生/类名全仓零命中/未出数"均带"本注记时点"限定，**在写作时点真实**；本批后发证据已超越——契约 `:115` 由记录步刷新（§8），清单内时间戳句保留不代改（已在句内自带时点限定，不构成虚假陈述），登记于 §6 #12。

### 3.4 T4 条件轨 —— **完成（分支 D 登记，零改动、零文件落盘）**

- 开工实测复核脚本布尔：`feature/library` 43 行脏、`feature/tutor` 59、`core/ui` 35，九热区全脏（HEAD `8116ad98` 未动）。按批次 24 R4 顺序（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段 6→5c）对九个排队项逐项复核：**全部仍 pending**、零代码判定 grep 全 0、各项目标文件 porcelain 均 `M`（R4 §8.2 口径 = 全部登记、不可开工）；载体全部在位（KD-68 `:2652`、R3 §9.2 `:213`、决策台账 6B-3 `:3012/:3025/:3036`、MASTER-LINE AUD-12 `:153`）；KD 台账尾条原为 KD-77(`:2857`)，本批未重复建条目（KD-78 为本批复核/移交收口新登记，非队列项）。
- **锚点漂移登记**：MASTER-LINE AUD-08 门行 `:148→:149`、阶段行整体下移（5B/6/6B `:81/:82/:83`、顺序刚性 `:88`）、KD-77 头 `:2855→:2857`（归因 T3 在 KD-76 追加 2 行）。
- **状态面**：AUD-08 工具半边已闭、收口半边仍排队；本轨零写盘。

### 3.5 设备轨（五步串行；记录员未复跑，以下转录 E-file 并对 raw log 抽验）

> 环境：`emulator-5554`（test_device，API 34，x86_64，PAGE_SIZE 4096，沉降态 up ~24 h/load 0.11）跑全部五步；`emulator-5556`（ps16k_x86_64，API 36，16384）**只读核**；5584 属他线全程未触碰。HEAD `8116ad98`；Gradle 仅 `assemble*`（debug 三件套 + 两轮 core:data androidTest + release）；**零 `connectedAndroidTest`、零 `adb root`、零 git 写、零产品源改动**。设备已恢复 debug 三件套（v3）态，设备侧 sha256 与产物逐字节一致，无存活 worker。

| 步 | 判定 | 关键事实（E-file 锚点） |
|---|---|---|
| step00 环境/构建/产物身份 | PASS（信息性） | 6 个产物 sha256 表（app debug `9ad2cd73…`/189,406,722 B **含 F1/F2 修复**；app androidTest `cde7e27b…` **与批次 27 逐字节一致**——本批 app androidTest 源零改动；core:data androidTest v1→v2→v3，收口在装 v3 `a24d2c85…`；release `e90ce7f3…`/155,732,659 B，`CN=Batch28 Local Smoke` 一次性本地 key）；dex 套件符号防旧包跑新门 |
| step01 隔离 13 + Race 2 + 签名/worker/kill | **PASS** | `OK (13 tests)`/61.749 s（批次 27 10 红全转绿）+ `OK (2 tests)`/1.078 s；签名 18 项全 0；17/17 spawn→ready；17 kill 全归因零意外杀（5 负控 watchdog + 4 terminate + 7 unbind + 1 start-instr） |
| step02 编译器 6（设备首跑）+ 引擎 26 | **PASS（含先红后绿）** | 编译器 v1 5/6（:144 红 = 测试期望缺陷，四源归因）→更正→`OK (6 tests)`/0.554 s；引擎 `OK (26 tests)`/8.034 s（9 红全转绿）；协作期门未触 |
| step03 painter 设备冒烟 17 例 | **PASS（含先红后绿）** | 首跑 16/17（红 = 夹具建位图顺序缺陷，`PAINTER_BOUNDS` 门未达）→更正→`OK (17 tests)`/0.476 s；e2e `aLegalSceneGoesFromCompileToColoredBitmap` 与确定性 ×2 绿 |
| step04 备份抽验（不回退） | **PASS** | `OK (34 tests)`/45.831 s，与批次 27 逐值一致 |
| step05 R8 下隔离往返（R-04 复试） | **阻塞维持** | ① 签名门 `SecurityException`（与批次 27 同型）；② 同 key 重签 test 包后 instrument 真启，app 进程测试体执行前崩 `NoClassDefFoundError: Lkotlin/jvm/internal/Intrinsics;` + `Landroidx/tracing/Trace;`（`shortMsg=Process crashed`）；③ BAL 通道未复试（避免 `adb root` 设备态变更，沿批次 27 证据）；可交付半边：libquickjs.so 双 ABI 在包（924,936 / 911,248 B）+ 12 类名审计（AIDL/协议/引擎全改名）；**工单前提"接线修复后应可过"被实测否证**（F1 在包内，阻塞与之正交） |
| 清单行结论（E-05 §8） | 供台账轨引用 | P-01 维持成立无回退（六法含于 13 例、hard+500 ms 用例转绿）；R-02 维持 UNVERIFIED（arm64 本机不可采证）；R-04 阻塞维持（v2 判据在含 F1 的 release 包上仍不可执行） |

### 3.6 证据轨 —— **按 R4 落盘；记录员独立复测体量与脱敏**

- **落盘**：`docs/research/evidence/2026-10-28-batch-28/` **7 个 `.md`**（00-index + step00–05），记录员实测 **52,600 B**（`find … -printf %s | awk` 实跑），`du -sk` 65 KiB；单文件最大 `step05` 9,803 B/121 行；R4 上限（单文件 64 KiB/400 行、单批 16 个/512 KiB）未触。**体量漂移链如实登记**：设备轨初扫 50,306 B → 补记 §8 判据结论后复测 **52,600 B**（+2,294 B 为索引 §8 清单行结论与更正行，非新文件；纯记账，判定不受影响）。
- **脱敏（索引 §6 实跑 + 记录员复核）**：secrets 0 / credential assignments 0 / emails 0 / home paths **1**（= `00-index.md:91` 扫描命令行自身正则，逐条确认为自匹配）；`local-smoke-b28` 除 `<local-smoke-b28>` 占位外 0 明文；keystore 与已删除的 `.kspass` 均在 `build/`（不入仓）。
- **回链**：索引 `:3-4` 已预写"记录：`docs/research/2026-10-28-batch-28-record.md`（后写）"——本文件即该回链目标；`build/device-track-batch28/` 只作原始全量，不是判定证据。
- **附加产物**：`probe-path-alpha.mjs/.log`（V8 跨引擎同值探针，四源归因之一）在 `build/`（不入仓），行号与内容在 E-02 §4 登记。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。"工单给定"行来自任务材料；设备数字来自 E-file，记录员逐项对 `build/device-track-batch28/` 原始日志做了 grep 复核（§9）。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 仓内无本批门禁日志；记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | F1 处置后复跑 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 六 androidTest 编译 + `:app:assembleLocalFirstDebug`（工单口径） |
| 修复环第 1 轮 | **全部处置完成**（工单给定） | 高×1 已修复（dash 缓冲）+ 回归例；中×2 登记 KD-78；低项（台账时点句）由记录步刷新 |
| 设备 隔离 13 + Race 2 | **PASS（E-01）** | 记录员对 raw log 复核：`OK (13 tests)`/61.749、`OK (2 tests)`/1.078 |
| 设备 编译器 6 + 引擎 26 | **PASS（E-02）** | `Tests run: 6, Failures: 1` → `OK (6 tests)`/0.554；`OK (26 tests)`/8.034 |
| 设备 painter 17 | **PASS（E-03）** | `Tests run: 17, Failures: 1` → `OK (17 tests)`/0.476；**修复轮后 19 例未上设备** |
| 设备 备份 34 | **PASS（E-04）** | `OK (34 tests)`/45.831，不回退 |
| 设备 签名/worker/kill | **PASS** | 6 份 logcat×9 签名全 0（记录员实测）；17/17 spawn→ready；17 kill 全归因 |
| 设备 R-04（R8 隔离往返） | **阻塞维持（E-05）** | 签名门 + R8 类解析崩溃两重新鲜复现；BAL 未复试；解除路径归主会话 |
| Node 编译器测试台 | **89/89 passed，EXIT=0（记录员实跑）** | `node tools/visual_compiler/run_tests.mjs` |
| 锁文件生成 | **未执行（窗口未开）** | 基线 §6 判"锁窗口未开"；KD-72 open |
| 生产 release 签名 | **UNVERIFIED** | 一次性本地 key `CN=Batch28 Local Smoke`（非生产证书，口令占位 `<local-smoke-b28>`，keystore 不入仓） |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；JS 不广告 |

**记录员对门禁的保留**：各门 `exit=0` 没有本批日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file 且记录员对 raw log 实跑 grep 复核，但**未复跑设备**；修复轮新增的 2 例 painter 设备用例**没有任何设备证据**，不得表述为已通过。

## 5. 复核发现与处置

> **口径说明**：下表转录自复核轨（不含记录员追加的确认项）；"复核轨判定"列保留其 `已独立复核`/`未复核` 标注；末列为本记录员本轮核验口径——`实跑复核吻合` = 记录员本轮重跑/实读同值；`抽验吻合` = 只读抽验；`转录` = 未重跑。级别分布：高 ×1、中 ×3、低 ×9；**高×1 已修复、中×2 已登记 KD-78、中×1 由记录步刷新**，无未处置高项。

| # | 位置 | 发现 | 复核轨判定 | 记录员核验口径 |
|---|---|---|---|---|
| R1 | `VisualDrawOpsPainter.kt:230-240`（装配）、`:291-296`（原 `ensureDashCapacity`）、`:143-148`（奇数门）、`:54`（初值） | **【高】dash 缓冲复用串状态**：`ensureDashCapacity` 只扩不缩，`bindPathPaint` 只覆写前 `dash.size` 项就把整个 buffer 交 `DashPathEffect`（AOSP 构造器按 `intervals.length` 全量消费，仅 `length<2` 抛错）。三种失败：(a) 同实例后一个更短合法 dash 混入前一个残尾，静默错画；(b) `dash=[]` 在未扩容实例上抛**无码** `ArrayIndexOutOfBoundsException`，破坏"全部折进有码异常族"契约；(c) `dash=[]` 跟在实 dash 后整条复用旧 pattern。修复一行成本 | 已独立复核 | **实跑复核吻合（修复侧）**：现树已按 `FloatArray(dash.size)` 精确分配（`:245-250`）、形状门扩为偶数且 ≥2（`:149-154`）、回归例 2 个（`:251`/`:323`）；**设备复跑 UNVERIFIED**（2 新例未上设备）。 |
| R2 | `VisualDrawOpsPainter.kt`（`setColor`/`setAlpha` 序）→ `Pure.kt:112-132`；validator `:45`；编译器 `requireColor` | **【中】`#RRGGBBAA` 的 alpha 字节被 `op.alpha` 静默覆盖**：三侧都接受该形态（纯测还钉了 AA→ARGB 语义），但 binder 先 `setColor`（含 AA）后 `setAlpha`，AOSP `Paint.setAlpha` 只改 alpha 通道 → 8 位色的 alpha 在回放侧永远失效（如 `#80ff0000` 无组透明按不透明画）；契约对该形态与 `op.alpha` 优先级零定义。建议 V1.1 拒绝 8 位或定义乘法/覆盖语义 | 已独立复核 | **实跑复核吻合（登记侧）**：已并入 **KD-78 ③**（known-defects 尾部，记录员实读）；Fix direction 二选一、Reopen condition 含设备像素正/负例；本批不改产品源。 |
| R3 | `Pure.kt:40-47` 注释、`painter-design.md:203-204`、`step03-painter-smoke.md §7`、`00-index.md §5-4` vs `known-defects.md`/契约本批 diff | **【中】T3 未闭合 T2 明示移交**：① stroke-only 无颜色源、② dash 未钉偶数、③ measure/replay flags 指纹钉——R1 与代码注释三次指名"归 T3 登记"，但 known-defects 本批（复核时点）仅增 KD-76 一块、契约仅落层注记三处，三缺口零登记；两道生成门至今放行这两个形态，后续调用方会重复踩坑 | 已独立复核 | **实跑复核吻合（修复侧）**：复核后已新增 **KD-78**（①②④ 全登记 + ③ 由 R2 指名）并回写契约 `:119`/`:110` 注记（记录员实读 diff）；**KD-78 open，生成侧放行维持**，未虚报为已关。 |
| R4 | `known-defects.md:2853`（KD-76 T3 块）；契约 `:115` 现状行 | **【低】收口时点两处台账陈述被本批后发证据反转**（保守方向）：T3 写于设备轨前，两处写"修复后设备复验 UNVERIFIED"/"落码与设备冒烟未出数、类名零命中"；本批 E-01（13/13）与 E-03（17/17、4 文件在树）已超越。T3 未把未达项写成刷新，0 项转通过、KD-76 open 维持，虚报方向干净；但记录步若不刷新，台账低于事实 | 已独立复核 | **已由本记录员处置**：KD-76 追加批次 28 设备轨 Progress 块（§8）、契约 `:115` 加记录步补记（落码入树 + 17/17 + 2 新例 UNVERIFIED）；清单时间戳句保留（自带时点限定，§6 #12）。 |
| R5 | `QuickJsSceneCompilerInstrumentedTest.kt:141-151`（ask 清单未列） | 本批自有 `M` 文件漏列：批次 28 设备轨测试面更正（dot alpha 1.0→0.5，整文件纳入登记），非热区越界；`git diff` 单 hunk 9 行、无他线内容；四源归因实证（契约 `:98`、编译器 `:860/:884/:937`、`run_tests.mjs:219`），delta 仍 0.0 非放宽 | 已独立复核 | **实跑复核吻合**：记录员本轮 `git diff` 实读单 hunk（+9/−1）；四源行号逐条实读核对一致。 |
| R6 | `docs/current/visual-generation-contract.md:115` 一带 | 契约 painter 现状行"落码与设备冒烟未出数——本注记时点 painter 类名全仓零命中"在同批次内被超越（T3 写作时点真实、有"本注记时点"限定） | 已独立复核 | **实跑复核吻合**；**已刷新**（§8）：现写落码入树、17/17 绿、2 新例 UNVERIFIED。 |
| R7 | `build/device-track-batch28/node-tests.log` vs T1 摘要 | T1 摘要引 upper-bound 1.7 ms，设备轨实跑 log 写 2.3 ms；记录员本轮复跑 **2.1 ms**——同一命令运行间信息性计时差异，非判据（判据 = 89 passed/0 failed EXIT=0，三处一致） | 已独立复核 | **实跑复核吻合**（记录员 `node …` 实跑 89/89，2.1 ms）。 |
| R8 | `evidence/2026-10-28-batch-28/step01–05` + raw logs | 设备全绿声明三方一致（确认项）：隔离 13/61.749、Race 2/1.078、编译器 5/6→6/0.554、引擎 26/8.034、painter 16/17→17/0.476、备份 34/45.831、Node 89/89；9 签名×6 logcat 全 0；17 spawn↔17 ready；kill 5+4+7+1=17 全归因 | 已独立复核 | **实跑复核吻合**：记录员本轮对 9 份 raw log 逐一 grep（§9）全部同值。 |
| R9 | `QuickJsSceneCompilerInstrumentedTest.kt:141-151` 与 `VisualDrawOpsPainterInstrumentedTest.kt:63-82` | 两处先红后绿为测试层唯一改动、整文件纳入属实（确认项）；painter 夹具增 `bitmapPxPerDu`（默认=pxPerDu）+ 三调用点 + 注释 | 已独立复核 | **抽验吻合**：记录员实读 `git diff`（编译器单 hunk）与 painter 文件 `:63-82`/`:368-378`；**painter 文件是未跟踪新文件，diff 无法显示新增后的夹具改动，以源码实读为据**。 |
| R10 | painter 主源/Pure | painter 语义与 R1/编译器约定一致（确认项）：`[a,c,e,b,d,f]`、clip/label 根空间舞步、label 不重放对齐且 y=baseline、fill 先 stroke 后且 stroke 色取 fill 色、textSize du 空间调和（KDoc 登记 R1 §3.5 句面会双乘）、`PAINTER_BOUNDS` 先于 `setScale`、dash 门前置、hitRegions 不读；确定性为整像素阵 `Arrays.equals` + 正负例探针 | 已独立复核 | **抽验吻合**：记录员实读 `:75-123`/`:136-203`/`:232-276`、`Pure.kt:85/96-99/112-142` 与仪器化 `:133-202`/`:205-248`/`:251-268`/`:312-326`。 |
| R11 | `known-defects.md`、清单、契约 | T3 三文件改动与声明一致、锚点成立（确认项）：KD-76 块 +2 行、清单 69 行不变、S-04/S-05/S-06 锚（`:732/:738/:950-975/:1077-1088`）实读成立；E-04 数字对源 | 已独立复核 | **实跑复核吻合**：记录员 `git diff --stat`（6/2/67 行，KD-78 另计）、`grep -cE` = 69、各锚实读；KD-76 现为 **7 个 Progress 块**（批次 25 T2/T3、批次 25 设备轨、批次 26 T5、批次 26 设备轨、批次 27 设备轨、批次 28 T3、批次 28 设备轨——记录员 `awk` 实测）。 |
| R12 | `step05-r8-roundtrip.md` + raw logs | R-04 如实报阻塞、无冒充（确认项）：attempt1 签名门、attempt2 `NoClassDefFoundError` 均在 raw log 实读；"接线修复后应可过被否证"与 F1 在包内一致；BAL 未复试登记为沿批次 27；R-02 行义=arm64 不可采证 | 已独立复核 | **抽验吻合**：记录员实读 E-05 §3/§4 与 `release` sha256 声明；未复跑设备。 |
| R13 | `git status` 全树 vs 基线 | 热区零越权（确认项）：205 M + 480 ?? = 基线 201+472 + 恰好本批落点（4 M + 8 ??）；`settings.gradle.kts`/`.gitignore`/core/domain/core/data 他线面/Root 等无本批新增；`app visualjs` 与引擎干净 | 已独立复核 | **实跑复核吻合**：记录员 `git status --porcelain | wc -l`=685、M=205、??=480、staged=0；本批落点 4 M 逐一实读，8 ?? 逐一点名（§1.3）。 |

**记录员追加核对（不在原清单）**：

1. **工单数字与现树的时点差（如实登记）**：T2 交付时仪器化套件 17 例、设备跑 17/17；修复轮新增 2 例后现树 **19 例**——`grep -c "@Test"` 记录员实测 19，设备证据只有 17。KD-78 已如实写"设备复跑 UNVERIFIED"，本记录沿用、不把 19 写成已验。
2. **两处先红后绿的"非放宽"判定**：编译器 delta 仍 0.0（只改期望值，且按契约/编译器/Node 台/跨引擎探针四源取权威值）；painter 只改夹具（`bitmapPxPerDu` 默认=原值，16 调用点逐位不变）——两者均不删断言、不缩小范围、不改成功含义。
3. **E-03 的 17 例与现树 19 例的差异点**：新增 `aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern` 与 `anEmptyDashFailsClosedEvenThoughValidationAcceptsIt`（修复环回归），其余 17 例与设备版一致（记录员按函数名逐一对照 E-03 §5 名单）。
4. **T1 轨的 16 vs 17 worker 预估**：T1 摘要预估 16，实测 17（实际计数），E-01 §4 按 pid 集合 1:1 配对成立。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门与设备**

1. **修复轮 2 例 painter 设备证据缺失（本批最重遗留）**：残尾 dash strip 逐位一致 + 空 dash 有码拒两例只在 JVM/编译层有据；现树 19 例、设备只跑过 17 例。归属=下一次设备窗口按 E-03 同命令复跑 `VisualDrawOpsPainterInstrumentedTest`（判据 `OK (19 tests)`）。
2. **门禁 `exit=0` 无仓内日志**（单测门第 1 轮/修复环复跑/强门第 1 轮均工单给定）；记录员未运行 Gradle。提交前建议主会话落盘命令与 tail。
3. **R-04 阻塞（当前树两重新鲜复现）**：签名门 + R8 类解析崩溃；BAL 未复试（沿批次 27 E-03 §3.3）；libquickjs 双 ABI + 12 类名审计为已交付半边。解除路径（keep 规则/可测 release 变体/判据改口径）**归主会话**；**"接线修复后应可过"已被否证**。
4. **KD-78 四缺口 open**（生成侧放行、painter fail-closed）：① stroke-only 无颜色源、② dash 形状门未钉、③ 8 位色 alpha 静默覆盖（唯一静默错画）、④ flags 不在环境指纹。V1.1 收口 + 词表两侧同改 + 负向矩阵后关闭；**不得以"设备 17/17 绿"读成缺口已关**。
5. **A-11 进程内负向仍未执行**（批次 27 遗留）：隔离进程内 Socket/filesDir 写用例需隔离进程内 runner 或产品 seam（构建/产品决策）；本批设备轨未重跑该面。
6. **T1 预算上限协作期**：本批编译器套件 0.554 s 全 6 例通过（含 256 节点/4080 命令），但**单例耗时**与第三上下文每事务创建成本仍无独立计时（受 1500 ms 硬期限兜底）；Node 台 2.1 ms 为 V8 参考值。

**代码/复核残留**

7. **measure/replay flags parity（KD-78 ④）**：`VisualContentMeasuring.kt:150-152` 环境指纹无 Paint flags 项；本批 painter 严格复制默认 flags，但无跨侧测试钉死——度量 Paint 改 flags 会静默失配。
8. **hitRegions 口径差（R2 A4）**：编译器 `:950-975` 允许非空并透传 DrawOps，清单 §1.1 要求空/不存在；S-05 第三条负向待主会话一句话裁定（R2 排序建议批次 29 或 31 顺手）。
9. **BAL 通道未复试**：本批避免 `adb root` 设备态变更，沿批次 27 证据；该通道无代码变更。
10. **painter 未接引擎**：本批 painter 只由测试夹具消费（validator→doc→painter），生产装配（A-01/A-13）留批次 31；`Rendered.drawCommandsJson` → 结构化 doc 的解析器仍未落（S-04 序列化往返无测试，批次 29 建议）。
11. **KD-76 剩余**：③ 产品进程/UI 首请求（低内存/CPU 竞争）、29.5 s worst-case 链、`READY_TIMEOUT=10 s` 设备校准（仅 x86_64 口径）、arm64/真机、R3 广告门文本升级（归主会话）。**JS 不广告。**
12. **清单内时间戳句保留**：S-04/S-05/S-06/§2 行 6/契约原句等"本注记时点/在飞"表述自带时点限定，记录员未逐行代改（避免扩大范围），其超越事实登记于 §3.3/R4/R6；下次清单同步轨可一并清理。
13. **KD-72/75/77**：锁窗口未开（基线 §6 六 build 面全脏、13+ 锁未跟踪）、core/export 热区（KD-75 不引第三方库、整批落地）、API 23 镜像未装（KD-77）——均维持 open，本批零动作。

**台账 / 数据 / 环境**

14. **arm64/16KB UNVERIFIED**（本机 x86_64 宿主原理性不可采证，批次 25 R4 定论）；ps16k（5556）本批只读核、未安装未执行；**16 KB ELF 检查本批无（无 16KB 安装面）**。
15. **origin/main 未 fetch**：远端实时 hash 未核（HEAD `8116ad98` 是否仍与远端一致 UNVERIFIED）。
16. **热区在飞未触碰**（feature 五模块、core/ui、core/markdown 整目录、core/export、core/domain 4M、core/data 他线 M、settings.gradle.kts、.gitignore、Root/SecondaryScreens、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production）；**4E.2 MVA 维持不成立**（painter 屏半边落层 ≠ PDF 回放/持久化/双后端/质量门）。
17. **待主会话裁定汇总**：R-04 解除路径三选一；KD-78 V1.1 收口方案（①②③④ 各二选一）；hitRegions 口径；A-01 装配形态（X-01 归位前）；Q-02 映射裁定；KD-76 关闭另一半（R3 文本升级）；批次 29–32 排期是否按 R2 建议；死亡清队语义（契约 :72 三处"待确认"仍未裁）。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；热区、锁文件（`*/gradle.lockfile` 全部 `??`）与 docs/current 其余 M 一律排除。按 ≤6 路径/块（新 = `??`，改 = `M`）：

- **块 1（T2 painter 纯计算层与 JVM 测试）**：`core/data/src/main/kotlin/.../visual/VisualDrawOpsPainterPure.kt`（新）、`core/data/src/main/kotlin/.../visual/VisualDrawOpsPainter.kt`（新）、`core/data/src/test/.../visual/VisualDrawOpsPainterPureTest.kt`（新）
- **块 2（painter 仪器化套件 + 编译器测试面更正）**：`core/data/src/androidTest/.../visual/VisualDrawOpsPainterInstrumentedTest.kt`（新）、`core/data/src/androidTest/.../visual/QuickJsSceneCompilerInstrumentedTest.kt`（改；**单 hunk**：dot alpha 1.0→0.5 + 四源归因注释）
- **块 3（两研究文档 + 基线）**：`docs/research/2026-10-28-painter-design.md`（新）、`docs/research/2026-10-28-mva-remaining-order.md`（新）、`docs/research/2026-10-28-batch-28-baseline.md`（新）
- **块 4（证据前半）**：`docs/research/evidence/2026-10-28-batch-28/00-index.md`、`step00-environment.md`、`step01-scenev1-app-suites.md`、`step02-scenev1-coredata-suites.md`（均新）
- **块 5（证据后半 + 本记录）**：`step03-painter-smoke.md`、`step04-backup-rerun.md`、`step05-r8-roundtrip.md`（均新）、`docs/research/2026-10-28-batch-28-record.md`（新）
- **块 6（台账/契约/清单/总路线）**：`docs/known-defects.md`（改；**整文件纳入并注明**：T3 KD-76 块 + KD-78 五段 + 记录步 KD-76 设备轨块，KD-66/72/75/77 区未动）、`docs/current/visual-generation-contract.md`（改；**整文件纳入并注明**：T3 三处落层注记 + KD-78 引用 + 记录步 painter 状态刷新）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改；v3 补记 2 + 附录 E）、`docs/REFACTOR-MASTER-LINE.md`（改；顶部滚动一行插入 + 两条重标）
- **块 7（算法台账 + 决策台账）**：`docs/research/algorithm-version-ledger.md`（改；§3.38 零 bump）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改；批次 28 painter 落位与 KD-78 条目）

**明确排除（他线在飞/不安全，一律不动）**：

- 全部 `?? */gradle.lockfile`（13 个 + `settings-gradle.lockfile`，KD-72 open 不得提交）；`settings.gradle.kts`、`.gitignore`（M，他线）。
- `feature/{capture,review,tutor,library,profile}/**`、`core/{ui,database,export}/**`、`core/markdown/` 整目录、`core/domain` 开工即脏 4M、`core/data` 他线 M（mistake/study/knowledge/model 面）与他线 androidTest/test M、app 开工即脏 M（Root/SecondaryScreens/Destinations/StartupStateBanner/ExportPdfWorker/ReviewReminder* 等）、docs/current 其余 M、`docs/research` 他线未跟踪记录（含 `?? docs/research/2026-10-*-ui-*` 等他线产物）、`docs/architecture-research-*`、`docs/audit-*`、`docs/superpowers/**`、`tools/` 他线 4 M、`knowledge-production/**`、`build/**`（含 `build/device-track-batch28/` 原始日志、一次性 keystore 与已删 `.kspass`，`.gitignore:15` 忽略）、`tmp/`。
- 证据目录内的 `docs/research/evidence/2026-10-28-batch-28/.mimosa/hook-status/*.json`（402 B hook 产物）**不入提交**：按显式文件路径 add，不按目录 add。
- 批次 23–27 证据目录（本批零改动）。

**提交说明**：建议 7 块、每块 ≤6 路径；四处整文件纳入的 M 文档（known-defects/契约/清单/MASTER-LINE）与决策台账在提交说明中注明"整文件纳入，含本批多轨改动"。提交/推送由主会话执行；非快进 merge，不 rebase。

## 8. 收口状态（工单给定 + 记录员执行项；提交/推送待主会话）

- **门禁（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环第 1 轮全部处置（高×1 修复 + 回归例；中×2 → KD-78；低项 → 记录步刷新），无放宽断言。
- **设备（E-file + 记录员 raw-log 复核）**：五步执行完毕——隔离 13/13、Race 2/2、编译器 6/6（设备首跑，先红后绿）、引擎 26/26、painter 17/17（先红后绿）、备份 34/34；9 签名×6 logcat 全 0；17/17 spawn→ready；17 kill 全归因；**R-04 阻塞维持**。证据 7 个 `.md`/52,600 B（两测链登记），脱敏全过（1 处自匹配已确认）；设备恢复 debug 三件套 + 无存活 worker。
- **研究/实施**：R1/R2 落盘（236/237 行）；T1–T4 四轨完成（T1 判据表六项有实跑证据 + 一项补充面；T2 落码 4 文件；T3 三文件纯文档；T4 零改动）。
- **台账/契约同步（本记录员执行）**：KD-76 追加批次 28 设备轨 Progress（前置达成、⑤ 刷新、open 维持）；契约 `:115` painter 状态刷新（落码入树 + 17/17 + 2 新例 UNVERIFIED）；MASTER-LINE 顶部新增批次 28 滚动行（批次 27→上次、26→更早）；算法台账 §3.38 零 bump（DB 69/请求 18/PROTOCOL 2/drawSchemaVersion 1/AAR 不变）；决策台账新增批次 28 painter 落位 + KD-78 条目。
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行。
- **本批不声称全绿/全就绪**：修复轮 2 例 painter 设备用例未上设备；R-04 阻塞；KD-72/75/76/77/78 open；4E.2 MVA 不成立维持；arm64/真机/生产签名/真实 Provider UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告。
- Mimosa 提交仍无完整扫描结论——不宣称项目安全。

## 9. 本记录的验证结果（记录员实跑，写盘前/后执行）

- **设备声明复核（记录员本轮实跑，`build/device-track-batch28/`）**：9 份 raw log 的 `OK (/Tests run:/Time:` 逐文件 grep——隔离 `OK (13 tests)`/61.749、Race `OK (2 tests)`/1.078、编译器 `Tests run: 6, Failures: 1` → `OK (6 tests)`/0.554、引擎 `OK (26 tests)`/8.034、painter `Tests run: 17, Failures: 1` → `OK (17 tests)`/0.476、备份 `OK (34 tests)`/45.831、Node `89 passed, 0 failed`；9 类崩溃签名 ×6 logcat 逐文件计数 **6×0**；`Start proc .*:visual_js`=17、`warm-up finished`=17；kill 分类 `hard deadline (1500 ms) for request`=5 / `terminate(generation`=4 / `host unbound the visual_js worker`=7 / `due to start instr`=1（合计 17）、`has died`=5。
- **工具实跑**：`node tools/visual_compiler/run_tests.mjs` → `89 passed, 0 failed`，EXIT=0（upper-bound 2.1 ms）。
- **文档/源码引用检查**：R1/R2 行数 236/237（`wc -l` 实测）；证据 7 个 `.md`/52,600 B（`find|awk` 实测）；painter 4 文件行数 311/187/173/586 与 `@Test` 计数 0/0/10/19（`wc -l`/`grep -c` 实测）；KD-78 在 `known-defects.md` 尾部（实读）；契约 `:115`/`:119` 注记（`git diff` 实读）；编译器测试单 hunk（`git diff` 实读）。
- **工作树**：HEAD `8116ad98`、`git status --porcelain | wc -l`=685、M=205、??=480、staged=0（实测）；本批 4 M + 8 ?? 逐路径点名吻合。
- **空白/回填检查**：`git diff --check` → **EXIT=0**（仅 CRLF 提示，无空白错误）；回填后锚自洽——KD-76 新增 1 块（+2 行）后 KD-77 标题由 T4 登记值 `:2857` 下移至**实测 `:2859`**、KD-78 头实测 `:2869`；契约行内替换不改变行数（实测 220 行不变）；清单 69 行不变；MASTER-LINE 仅顶部滚动行与两条标签变更（285 行）；台账新增 §3.38（630 行）；决策台账追加一节（3,456 行）；本记录 299 行。
- **未执行**：Gradle、设备、git add/commit/push；JVM 单测与 painter 设备 19 例未复跑（口径见 §4 保留与 §6 #1/#2）。

## 附录 A：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 8116ad98
git rev-parse origin/main                                    # 8116ad98…（本地跟踪引用，未 fetch）
git status --porcelain | wc -l                               # 685
git status --porcelain | grep -c '^ M'                       # 205
git status --porcelain | grep -c '^??'                       # 480
git diff --cached --name-only | wc -l                        # 0（暂存区空）

# 设备 raw log 复核（本记录员实跑）
cd build/device-track-batch28
grep -E "OK \(|Tests run:|Time:" step01-isolation.log | tail -3   # Time 61.749 / OK (13 tests)
grep -E "OK \(|Tests run:|Time:" step01-race.log | tail -3        # Time 1.078 / OK (2 tests)
grep -E "OK \(|Tests run:|Time:" step02-compiler.log | tail -3    # Tests run: 6, Failures: 1
grep -E "OK \(|Tests run:|Time:" step02-compiler-v2.log | tail -3 # Time 0.554 / OK (6 tests)
grep -E "OK \(|Tests run:|Time:" step02-engine.log | tail -3      # Time 8.034 / OK (26 tests)
grep -E "OK \(|Tests run:|Time:" step03-painter.log | tail -3     # Tests run: 17, Failures: 1
grep -E "OK \(|Tests run:|Time:" step03-painter-v2.log | tail -3  # Time 0.476 / OK (17 tests)
grep -E "OK \(|Tests run:|Time:" step04-backup.log | tail -3      # Time 45.831 / OK (34 tests)
grep -E "passed,|failed" node-tests.log                           # 89 passed, 0 failed
for f in step01-isolation-logcat.txt step01-race-logcat.txt step02-compiler-logcat.txt \
         step02-engine-logcat.txt step03-painter-logcat.txt step04-backup-logcat.txt; do
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoClassDefFoundError|UnsatisfiedLinkError|SIGSEGV|CANNOT LINK|failed to map segment|death was not confirmed" "$f"
done                                                              # 6×0
grep -c "Start proc .*:visual_js" step01-isolation-logcat.txt      # 17
grep -c "warm-up finished" step01-isolation-logcat.txt             # 17
grep -c "hard deadline (1500 ms) for request" step01-isolation-logcat.txt  # 5
grep -c "terminate(generation" step01-isolation-logcat.txt         # 4
grep -c "the host unbound the visual_js worker" step01-isolation-logcat.txt # 7
grep -c "due to start instr" step01-isolation-logcat.txt           # 1
cat probe-path-alpha.log                                           # q/dot/label alpha 均 0.5

# 测试/文档计数（本记录员实跑）
node tools/visual_compiler/run_tests.mjs                     # 89 passed, 0 failed, EXIT=0
wc -l docs/research/2026-10-28-painter-design.md docs/research/2026-10-28-mva-remaining-order.md  # 236 / 237
grep -c "@Test" core/data/src/test/.../visual/VisualDrawOpsPainterPureTest.kt          # 10
grep -c "@Test" core/data/src/androidTest/.../visual/VisualDrawOpsPainterInstrumentedTest.kt  # 19
wc -l core/data/src/main/.../visual/VisualDrawOpsPainter.kt core/data/src/main/.../visual/VisualDrawOpsPainterPure.kt  # 311 / 187
find docs/research/evidence/2026-10-28-batch-28 -name '*.md' | wc -l                   # 7
find docs/research/evidence/2026-10-28-batch-28 -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s" B"}'  # 52600 B

# 台账/契约/源码锚
grep -n "painter 落层已裁定" docs/current/visual-generation-contract.md   # :115
git diff -- docs/known-defects.md | head -20                              # KD-76 设备轨块 + KD-78
grep -n "^## KD-78" docs/known-defects.md                                 # 尾部
grep -n "toAndroidValues\|PAINTER_BOUNDS\|PAINTER_DASH_ODD\|setMatrix(rootMatrix)" core/data/src/main/.../visual/VisualDrawOpsPainter*.kt

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#          JVM 单测、painter 19 例设备复跑、门判定器。
```

## 10. 主会话收口（2026-10-28，提交与推送执行者实跑）

> 本节由主会话在记录落盘后追加；上文 §1–§9 为记录员产物，未代改。

### 10.1 双门实跑（主会话本轮执行，日志落 `build/main-session-gate-batch28/`）

- **单测门**：`./gradlew.bat :core:model:test :core:domain:test :core:data:testDebugUnitTest :core:database:testDebugUnitTest :core:export:testDebugUnitTest :core:ui:testDebugUnitTest :feature:review:testDebugUnitTest :feature:library:testDebugUnitTest :feature:capture:testDebugUnitTest :feature:tutor:testDebugUnitTest :app:testLocalFirstDebugUnitTest --continue` → **`UNIT_EXIT=0`**、`BUILD SUCCESSFUL in 2s`、`228 actionable tasks: 228 up-to-date`（交付轮与修复轮已用同输入跑过，Gradle 判 UP-TO-DATE）。
- **UP-TO-DATE 的排除性核对**（避免把"未执行"读成"通过"）：`core/data/build/test-results/testDebugUnitTest/TEST-...VisualDrawOpsPainterPureTest.xml` 实测 `tests="10" skipped="0" failures="0" errors="0"`、mtime `2026-10-28T19:40:41`——本批新增 painter 纯层 10 例**确有执行产物且零失败**，非空跑。
- **强门**：`./gradlew.bat :core:data:compileDebugAndroidTestKotlin :core:database:compileDebugAndroidTestKotlin :feature:library:compileDebugAndroidTestKotlin :feature:capture:compileDebugAndroidTestKotlin :feature:tutor:compileDebugAndroidTestKotlin :core:export:compileDebugAndroidTestKotlin :app:compileLocalFirstDebugAndroidTestKotlin :app:assembleLocalFirstDebug` → **`STRONG_EXIT=0`**、`BUILD SUCCESSFUL in 21s`、`272 actionable tasks: 7 executed, 265 up-to-date`、无 `FAILED`/`error:` 行。
- **结论口径**：双门 exit=0 成立；`exit=0` 覆盖的是"本批改动可编译 + 既有 JVM 用例集合无回归"，**不覆盖**修复轮新增 2 例 painter 设备用例（仍 UNVERIFIED，见 §6 #1）与 R-04 阻塞。

### 10.2 复核项（高/中）在树的实读确认（主会话，非转录）

- **R1【高】dash 缓冲串状态已修**：`core/data/src/main/kotlin/.../visual/VisualDrawOpsPainter.kt:245` 为 `val buffer = FloatArray(dash.size)`（精确分配，`ensureDashCapacity`/`dashBuffer` 全仓零命中）；形状门前置在 `:149`（`it.size % 2 != 0 || it.size < 2`，空数组与奇数同折 `PAINTER_DASH_ODD`）；回归例 `aShorterDashAfterALongerDashOnTheSamePainterKeepsItsOwnPattern`（`:251`）与 `anEmptyDashFailsClosedEvenThoughValidationAcceptsIt`（`:323`）在树，`grep -c "@Test"` 实测 **19**。
- **R2/R3【中】已并入 KD-78**：`docs/known-defects.md:2869` 起为 `## KD-78 (open, 2026-10-28 …)`，标题即四缺口（stroke-only 无颜色源 / dash 形状门未钉 / `#RRGGBB(AA)` 与 `op.alpha` 优先级未定义 / measure-replay flags 不在环境指纹）；全文 2882 行。**open 维持，生成侧仍放行**。
- **R4【低】台账时点句已刷新**：记录员在 §8 声明的两处刷新（KD-76 设备轨 Progress 块、契约 painter 现状行）已随块 6 入库。

### 10.3 提交（7 块、20 个路径，全部显式路径 add；无目录 add）

| 提交 | 内容 | 路径数 |
|---|---|---|
| `df7ee1d9` | painter 纯计算层 + 应用层 + JVM 测试 | 3 |
| `d5b7ec71` | painter 设备套件 19 例 + 编译器测试 dot alpha 更正 | 2 |
| `f1465e71` | 两份研究文档 + 基线 | 3 |
| `ec2a0ebe` | 证据前半（索引/环境/隔离+Race/编译器+引擎） | 4 |
| `f52477be` | 证据后半（painter/备份/R-04）+ 本记录 | 4 |
| `3e1762e5` | known-defects / 契约 / 准入清单 / MASTER-LINE（整文件纳入，含本批多轨改动） | 4 |
| `c93241d5` | 算法台账 §3.38 零 bump + 决策台账 | 2 |

- **排除核对**（`git log --name-only 8116ad98..HEAD` 逐行实读）：20 个路径全部落在 `core/data/.../visual/` 与 `docs/` 声明面；**零 `*/gradle.lockfile`、零 `settings.gradle.kts`/`.gitignore`、零 feature 五模块、零 core/ui·export·database·markdown、零 app 热区、零 `build/`、零证据目录内 `.mimosa/` 产物**。
- 提交前 `git status --porcelain` 实测 689 行（208 M + 481 ??），本批 20 路径提交后剩余脏项全部为他线在飞（含 13+ 锁文件），未触碰。

### 10.4 推送

- 首次 5 连试全部 `Failed to connect to github.com:443`（21107/21093/21048/21080/21066 ms 超时，GitHub 侧连接故障，非认证问题）；转后台 20 次重试（60 s 间隔）。**推送最终结果见下方追加行；本节写作时点未确认远端已前进。**

### 10.5 收口不改动的既有判定

- KD-76 保持 **open**（③ 产品面 / 29.5 s 链 / READY_TIMEOUT 校准 / arm64）；KD-72/75/77/78 **open**；KD-66/70 **closed 维持**（本批设备证据只作复验，未发现 reopen 条件）。
- **4E.2 MVA 不成立维持**；JS 仍 `UNAVAILABLE`、不广告；arm64/真机、生产 release 签名、真实 Provider/模型质量 **UNVERIFIED**。
- Mimosa 仍 `scanner_enobufs`、无完整扫描结论——**不宣称项目安全**。
