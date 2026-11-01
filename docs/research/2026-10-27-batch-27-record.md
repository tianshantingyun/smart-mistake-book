# 批次 27 记录（2026-10-27）：4E.2 MVA 静态子集开工（SceneV1 编译器/SDK + 内容度量 + 深冻结）+ 设备审计 + T1 缺陷修复环

> 记录时点：2026-10-28（+08:00，批次开工 2026-10-27）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、两研究文档、七件证据 E-file、`git status`、关键源码锚点（引擎修复点、编译器查表、协议/服务/域类型）后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改产品代码。** 本记录员本簿新增交付共七处：本文件、`docs/current/visual-generation-contract.md`（:72 warm-up 装载成本出数回填，牵动面闭合）、`docs/known-defects.md`（KD-76 批次 27 设备轨 Progress 回填块，KD-66/72/75/77 未动）、`docs/REFACTOR-MASTER-LINE.md`（顶部滚动三段）、`docs/research/algorithm-version-ledger.md`（§3.37 零 bump，登记新增产物面版本 DrawOps V1）、`docs/agent-first-refactor-decisions-2026-09-23.md`（批次 27 主会话队列裁定确认条目）、证据索引 `00-index.md` §6 体量三测注记，见 §8。
> 计划：`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md`（下称"计划"，21 行已完整读）。
> 基线：`docs/research/2026-10-27-batch-27-baseline.md`（下称"基线"；只读核对文件）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 3 轮 `exit=0`；修复环复跑 `exit=0`；强门第 2 轮 `exit=0`；修复环第 1 轮进入——发现 1+2（T1 compile 接线缺陷【高】，同根因）与发现 3（编译器原型链逃逸【中】）**均已修复**（修复形态见 §4/§5）。仓内没有本批门禁日志，§4 只作转录。
> **本记录不声称"批次全绿"**：**隔离套件相对批次 26 的 13/13 属真实回退**（设备 13 例 10 红 = 本批 T1 compile 接线缺陷，设备首曝；修复已落码 + Node 台绿，但**修复后设备复验 UNVERIFIED**——隔离套件未再上设备）；R-04（R8 下隔离往返）**阻塞**（三重实证，解除路径归主会话）；**KD-76 保持 open**（③ 产品面 UNVERIFIED、R3 广告门文本升级未做；warm-up 装载成本本批出数但不改变本体状态）；**KD-72 保持 open**（锁窗口未开）；**4E.2 MVA 仍不成立**（编译器/度量/冻结三块落层后，painter 回放/持久化/双后端/质量门仍缺）；arm64/真机、生产 release 签名、真实 Provider/模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）

- **做**：R1 SceneV1 编译器/SDK 设计参考；R2 无 UI 线程内容度量设计；R3 队列语义两处待确认项裁定的落法；T1 SceneV1 编译器+SDK 落码（core:data visual 侧，JS sandbox 内 `compile(sceneJson)→drawCommands` 纯计算，契约 §3.2 纯计算边界）；T2 真实内容度量接线（替换 Unwired）；T3 input/metrics 深冻结；T4 队列裁定与准入清单/契约同步；T5 台账/契约状态同步；T6 热区条件轨；设备轨（P-08 dumpsys 审计、A-11 负向、R-02 R8 隔离往返、warm-up 装载成本实测、隔离回归）；证据落盘；双门；双复核；修复；记录；提交推送。
- **实际执行面**：两研究线落盘（R1/R2）+ 五实施轨（T1–T5，R3 并入 T4/T5 与决策台账）+ T6 条件轨分支 D 登记 + 设备轨五步 + 证据落盘 + 双复核 + 修复环第 1 轮（三条发现处置）+ 本记录与同步。
- **不做**：4E.2 交互件/保真升级（4E.3）；JS 广告（MVA 过门后才议）；PDF `/AIGC`（KD-75，热区）；锁文件（窗口未开）；热区 UI 实现；真实 Provider；Wave X。**全部未做，零越线**（T1 footprint 核实：能力快照/提示词/契约广告文本本批零改动）。

### 1.2 沿用裁定（本批执行，未改）

1. **JS 不广告**：本批落的是 MVA 静态子集的编译器/度量/冻结，广告门不动（KD-76 open 维持）。
2. **4E.2 交互件与 4E.3 不做**；PDF `/AIGC`（KD-75）不做；锁文件不生成（KD-72 等窗口）。
3. **AIDL 只以 Gradle 任务验证**；AAR 钉版 `quickjs-kt-android:1.0.15` 与 stdlib 2.3.21 不变；AAR/引擎终止语义不变。
4. **SceneV1 编译器在 JS sandbox 内纯计算**（契约 §3.2）：输入 RenderScene JSON，输出绘制指令；无 IO 无网络无时钟无随机（种子注入）。
5. **队列"取消在飞帧不再 abandon worker"——主会话裁定确认采纳**（取消只出队自己的等待帧，不打断在飞执行；KDoc/契约按此定稿，本记录员已登记决策台账，§8）；死亡清队语义仍待主会话确认。
6. **真实 Provider 无凭据**：模型质量 UNVERIFIED。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：记录时点 `caa0726a`（`git rev-parse --short HEAD` 记录员实测）= 基线 §1 实测值；**与计划 §0:5 的偏差**——计划写 "HEAD = `7b2d6905`"，实测 HEAD = `caa0726a`（`7b2d6905` 为 HEAD~1），差异 = 批次 26 记录补收口的 1 文档提交（基线 §1 已登记，非内容冲突）。
- **工作树**：基线冻结（2026-10-27 23:42:45）`673` 行（201 M + 472 ??，staged 0）；设备索引头记开工即脏 698 条；**本记录员记录时点 `699` 行 = 214 M + 485 ??（实测），staged 0**——增量 = 本批 T1–T5 落层文件 + 两研究文档 + 证据目录 + 他线在飞。
- **版本串**：DB schema 69（`StudyDatabase.kt:134`）、请求 schema 18（`ModelTasks.kt:546`）、`PROTOCOL_VERSION` 2（`VisualJsProtocol.kt:32`）——记录员本轮 grep 复核全部未动；算法台账 §3.37 零 bump（本记录员登记，**新增**产物面版本 `drawSchemaVersion=1` 见该节）。
- **落点基线**（基线 §3）：批次 27 全部落点开工时干净——`core/data/src/{main,test,androidTest}/.../visual/` 三目录、`app/src/main/kotlin/.../visualjs/` 四文件、`core/domain/.../VisualExecutionEngine.kt`（288 行）、`docs/known-defects.md`（2,861 行）、`docs/current/visual-generation-contract.md`（220 行）。**注意 core/domain 开工即脏 M 四文件不含 VisualExecutionEngine.kt**（基线 §2.1 逐路径实测）；core/data "两 M" 仅 main 源码口径成立（总 M 计 10，基线 §2.2 口径歧义登记）。
- **跨线在飞（如实登记）**：批次执行期间 T3 修改了 T1 落码的同一引擎文件（bootstrap 冻结 + DEEP_FREEZE_JS + 6 个仪器化用例）、T2 删除了 `UnwiredVisualContentMeasurer`——T1 接线基于重读后最新态叠加；T4 线曾把 `VisualJsClientEngine.kt`/`VisualJsFrameQueue.kt`、T1 线曾把 `QuickJsVisualExecutionEngine.kt` 与 3 个新 core:data visual 文件变脏（T2 视角登记）；记录时点各文件均收敛为本批终态（§3 各轨）。

## 2. 两研究线与复核结论

### 2.1 R1 SceneV1 编译器设计研究（`docs/research/2026-10-27-scenev1-compiler-design.md`，262 行，唯一落盘）

- **§0 结论**：无一个现成开源库可在裸 QuickJS 承担"严格 JSON 场景 → 有界绘制指令"（14+ 候选全核 license/维护度，**全部否决为依赖**）；采纳形态 = 自研小编译器 + SDK（JS 资产估 15–25 KiB）+ 算法级移植（弧→三次曲线 κ=4/3·tan(θ/4) 分段、Q→C 标准次数提升，均数学事实）+ 架构借鉴（zrender PathProxy M/L/C/Q/Z 数值流词表旁证、vello/typst 扁平指令流、lottie-android Canvas 映射）。
- **§1 GitHub 调研**：15 仓库评分表（license/★/推送/发布，`gh` CLI 2026-10-27 当日实跑；zrender LICENSE 文件与 kurbo `arc.rs:77-86` 一手源码复核）；两类仓库名猜错经 `gh search repos` 纠正；`antvis/g` license 歧义、`d3-shape` 维护度定性为单一来源待验证（§8 如实登记）。
- **§2 管线定位**：compile 为 prepare/render 之外**第三上下文**（bootstrap + 编译器资产、无用户源码——编译器完整性从防御性升级为结构性）；"输入 RenderScene JSON"（任务书序言）与 `compile(sceneJson)`（计划 T1）的命名歧义已登记、**不擅自改裁定，留主会话对齐契约文本**。
- **§3 JS 侧设计**：`__visualCompile(sceneJsonText, metricsJsonText, expect)` 唯一暴露面 + 每项决定对应消灭的失败表；DrawOps V1 词表（save/concat/restore + clip/path/label 六 op）与 7 条不变量（Q 已提升、无 A 命令、绘制路径带 concat 保"stroke 随变换缩放"、clip/命中区烘焙组合坐标、save/restore 配平、预算计数进 stats、标签位置一次算定）；SDK 六原语（circle/arc/arrow/axis/polyline/random）+ 明确不设项的逆向裁剪（ellipse/rect 等"没有它们也没有已发生的失败"）；9 码族 fail-closed 错误面（SCENE_PARSE/FIELD/NODE/PATH/STYLE/REF/MATRIX/BUDGET + SceneSdkError）→ 一切违规折 `INVALID_SCENE` 且诊断 ≤512 字符；严格解析器（~120 行，null-proto 产物）在文本边界落实契约 §4 重复键/污染键拒绝；与 bootstrap/T3 深冻结接口（§3.5）。
- **§4 宿主映射点**：DrawOps → Android Canvas（**Android Matrix 列序 `[a,c,e,b,d,f]` 陷阱登记为头号隐性错误**）；只到指令序列，painter 归 4E.2 后段。
- **§5 预算可行性**：8 MiB 客体堆桌面 13/13、单帧 7.5–35 ms（Wasm 实测外推，原生更快）；峰值内存 ≪16 MiB；κ 误差 2.7253e-4·r 与 Q→C 精度 1.8e-15 附完整可复跑 `node -e` 命令；**上限场景设备实测 UNVERIFIED**（列 T1/设备轨证据项）。
- **§6 备选与已否决六项**（含 Kotlin 原生编译器诚实备选——沿计划裁定登记不擅自改）；**§7 T1 测试建议**（JVM 校验器负向矩阵、tools/ 金样、引擎/设备轨）；**§8 未验证项**；**§9 来源**。

### 2.2 R2 内容度量研究（`docs/research/2026-10-27-content-measurement.md`，349 行，四部分设计已落盘）

- **推荐组合（§0 速览）**：度量在 **:visual_js 服务 Kotlin 层**（`jsExecutor` 单线程 + `Paint` 线程独占——官方文档对 Paint 无线程安全声明，按非安全对待、单线程使静默区不构成风险，§1.4）做；**JS 不注入字体**（契约 §3.2 第 2 步"原生度量"是处方，JS 内度量让"暗改宽度"冻结检查失效，§3）；math 走**案A**：复用 core:model 纯 Kotlin 链（`parseAndBuildBox`/`MathBoxBuilder`，服务进程 classpath 已有），失败按契约**降级为可读文本**而非全量占位（案B 无新增失败可指认、反而引入"占位度量伪装正确图"，§2）；端口五字段零改动，断行留在内部 artifact seam 供未来 painter 复用（§4）；warm-up 加一次度量探针把字体子系统首初始化移出 1500 ms 硬期限（D6，消灭"首个含 text 请求把字体初始化计入期限误杀"的 KD-76 同族失败）。
- **官方口径 [引证]**：`StaticLayout.Builder` 无线程限制、builder 不可复用、`setUseLineSpacingFromFallbacks` 默认 false 需置 true；androidx `PrecomputedTextCompat` 官方推荐后台线程度量（正面证据）；API 33+ 文本感知 `getFontMetricsInt(CharSequence,…)`、minSdk 23 回退主字体 metrics（**混排行高可能低估 = 精度边界 P1 如实登记**）；math 盒宽 0.5em/字符估算 CJK 低估约一半（**P2**，修正必须落在共享 lowering 不得服务侧另起一套）。
- **假设登记**：A1（隔离进程内 resources/字体可用）[待验证]→ fail-closed + 设备验证项；A2（同环境跨进程逐位一致）[待验证]。**§6 测试判据**是 T2 实施的红绿标准（本批研究轨未运行）；**§7 来源**含官方文档 URL 与仓内路径行号。

### 2.3 研究复核结论

- 两份研究文档均在盘、行数实测吻合（262/349）、有来源分级与未验证清单；实施与它们逐项对齐：R1 的第三上下文/词表/错误面/金样建议被 T1 逐条落码，R2 的案A/单线程/warm-up 探针/降级语义被 T2 逐条落码；R1 §2 的命名歧义按裁定登记未自裁。
- **R1 唯一落盘纪律**：R1 报告声明"新增文件仅本报告"——落码由 T1 承担，研究/实施两段式未混淆。

## 3. 五实施轨 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 工具实跑 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 编译器SDK | 完成 | **落码成立（Node 台记录员复跑 89/89 绿）/ Kotlin 编译与设备面归双门 / 设备首曝 10 红缺陷已修复、复验 UNVERIFIED** | Kotlin 侧沙箱无 Gradle/kotlinc（T1 如实自登记 UNVERIFIED）；设备红为 T1 缺陷，修复环已处置 |
> | T2 内容度量 | 完成 | **落码成立（JVM 14 例就绪）/ 运行时门归脚本（UNVERIFIED）** | T2 未跑 Gradle（纪律），单测门第 3 轮 exit=0 为工单给定 |
> | T3 深冻结 | 完成 | **落码成立（本地 21/21 断言红→绿，轨自报）/ 设备绿证据当前被 T1 缺陷阻断，复验 UNVERIFIED** | freeze 用例终断言 Rendered 强转，compile 必败下先红——与隔离套件同轮复验 |
> | T4 契约同步 | 完成 | **完成（纯文档/KDoc，零行为变更；核对清单全过）** | 见 §3.4 |
> | T5 状态同步 | 完成 | **完成（三文件编辑前均 git 干净、编辑后 3 个 M；69 行不变、0 项转通过）** | 设备出数后的 warm-up 回填由本记录员完成（§8） |
> | T6 条件轨 | 完成 | **完成（分支 D 登记，零改动、零文件落盘）** | 九排队项逐项复核全部仍 pending |
> | 设备轨 | 五步执行完毕 | **五步全执行：P-08 PASS / A-11 观测级 / R-04 阻塞 / warm-up 出数 / 隔离套件红（T1 缺陷）+ 备份绿** | 判定边界见 §3.6/§4 |

### 3.1 T1 SceneV1 编译器 + SDK 落码 —— **落码成立 / Node 台 89/89 绿（记录员复跑）/ 设备首曝接线缺陷已修复、复验 UNVERIFIED**

- 文件（14）：`core/data/src/main/resources/visual/visual-compiler.js`（新，**记录员实测 44,718 B / 1,107 行**）、`visual-sdk.js`（新，11,429 B / 284 行）、`VisualDrawOps.kt`（新，178 行）、`VisualDrawOpsValidator.kt`（新，536 行）、`VisualJsAssets.kt`（新，32 行）、`QuickJsVisualExecutionEngine.kt`（改）、`VisualJsFailureClassification.kt`（改）、`VisualDrawOpsValidatorTest.kt`（新，**记录员实测 32 个 @Test**）、`VisualJsFailureClassificationTest.kt`（改，19 个 @Test）、`QuickJsVisualExecutionEngineInstrumentedTest.kt`（改，26 个 @Test）、`QuickJsSceneCompilerInstrumentedTest.kt`（新，6 个 @Test）、`core/domain/.../VisualExecutionEngine.kt`（改）、`app/.../visualjs/VisualJsProtocol.kt`（改）、`tools/visual_compiler/run_tests.mjs`（新，636 行）。
- **编译器 + SDK**（R1 §3.5 单源）：JS 资产落 core:data resources，Node 测试台与生产读**同一文件**（`VisualJsAssets.kt` 惰性装载，JVM 单测"不执行任何 QuickJs 路径"不变量保留）；sandbox compile 第三上下文纯计算（无 IO/时钟/随机，Node 三态实测同上下文/新上下文/换种子均 byte-identical——T1 自报）；输入 schema 与既有设备夹具同构，Q→C 精确提升、无 A 命令、clip/命中区烘焙组合坐标（clip 发射在 concat 前）、组 save→clip→concat→children→restore、标签位置一次算定、`visible:false` 整树仍校验不发射。
- **宿主类型与校验**：`VisualDrawOps.kt` sealed 六原语 + 路径命令 + 矩阵/描边/对齐枚举（**含 Android Matrix `[a,c,e,b,d,f]` 列序警示**）；`VisualDrawOpsValidator.kt` 纯函数校验器（词表/预算/有限性/配平/冻结引用/stats 复算，Q/A 出现即拒）；`Rendered` 增 `drawCommandsJson`/`drawSchemaVersion`（`VisualExecutionEngine.kt:245-246`，默认参数保 fake/队列测试兼容——记录员实读）；painter 归 4E.2、类型化消费形态由 JVM 正向用例与仪器化用例演示。
- **错误面 fail-closed**：编译器/SDK 抛 `SceneCompileError`/`SceneSdkError`（SCENE_* 码族）→ 分类器新增 INVALID_SCENE 分支（令牌正则、先于 not defined 判据、类型优先不变）；宿主校验器另出 DRAWOPS_* 码——均折 INVALID_SCENE 类型化拒绝，不进 INVALID_PROGRAM 泛化。
- **测试**：Node 测试台先红后绿（合法场景×7/确定性×3/非法形状逐码/SDK×10/上限场景+预算边界负控）；JVM 校验器负向矩阵 + 分类器 INVALID_SCENE 用例就绪；仪器化新增 `QuickJsSceneCompilerInstrumentedTest`（全节点编译/逐字节确定性/非法形状/SDK 违规/超节点预算/256 节点-4080 命令 100 ms 协作期）并升级主仪器化文件全部 Rendered 夹具为 SceneV1 合法场景。**记录员复跑 Node 台：`node tools/visual_compiler/run_tests.mjs` → `89 passed, 0 failed`、EXIT=0**（T1 轨自报 84/84 为修复环前时点；**+5 = 修复环补的原型链逃逸负向**，`run_tests.mjs:433-450`——cap/join/hitrule/自有键/hAlign 五类继承键名逃逸）。
- **设备首曝缺陷与修复**：见 §3.6 step05 与 §5 R1/R5——`QuickJsVisualExecutionEngine.kt` 原 `:751` 把三 JSON 文本未加引号直插 `__visualCompile(...)`，编译器实参成了 JS 对象值，违反编译器三文本参数契约（`visual-compiler.js` typeof 门）→ 全部 render 事务折 INVALID_SCENE。修复环已改：调用点三参经 `jsStringLiteral(...)` 包裹（**记录员实测现值 :756**）、`jsStringLiteral` 转义函数（反斜杠/双引号/C0 控制字符，**现值 :1034**，KDoc 指认它消灭的失败）；影响面闭合声明：prepare/render 的 `$inputJson`/`$metricsJsonText` 插值是**对象表达式注入**（SDK 契约收对象图、DEEP_FREEZE_JS 吃对象），设计内不属同型缺陷；`__visualCompile` 全仓唯一调用点即该处（grep 证实）。
- **JS 不广告零违反**：能力快照/提示词/契约文档本批零改动（T1 footprint 核实）。
- **跨线协同（如实登记）**：T3 在飞修改同一引擎文件（T1 接线基于重读最新态叠加、T3 深冻结语义原样保留——compile 上下文无用户代码故不包 DEEP_FREEZE_JS，KDoc 登记理由）；隔离门夹具 `pureGeometryRequest` 场景经实资产预检可编译通过无需改动；DrawOps 词表/compile 上下文入契约按 R1 §2 裁定留主会话/T4-T5，本轨未动 docs。

### 3.2 T2 真实内容度量接线 —— **落码成立（代码/文档层）/ 运行时门归脚本（UNVERIFIED）**

- 文件（6）：`app/.../visualjs/VisualContentMeasuring.kt`（新，446 行）、`app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（新，**记录员实测 14 个 @Test**）、`VisualJsService.kt`（改）、`VisualJsProtocol.kt`（改，同 T1）、`docs/current/visual-generation-contract.md`（改）、`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`（改）。
- **真实度量器**：`AndroidVisualContentMeasurer`（`VisualContentMeasuring.kt:53`）实现 `core:domain.VisualContentMeasurer`——text 走 Paint 内蕴度量（显式 `\n` 分段 + 段尾单个 `\r` 归一、每段 `measureText`、整段一次行 metrics；API 33+ 文本感知 metrics、minSdk 23 回退 = P1 如实登记）；math 走 R2 案A（共享 lowering，解析/预算失败按契约降级为可读文本，`degraded=true` 量原文 payload，不做占位框）；`contentId="c{index}-"+digest.take(8)`，digest=sha256（含环境快照 density/fontScale/typefaceId/localeTag/sizeSp）；环境构造与后端输出异常 fail-closed（`VisualMeasurementEnvironmentException`→引擎既有 catch 折 INVALID_PROGRAM，warm-up 探针先行暴露）。
- **接口兼容与接线**：端口类型零改动（与批次 22 同形）；engine metricsJson 五字段不变；断行输出留内部 seam（`VisualMeasuredLayout.lines`）；`VisualJsService` warm-up 在 jsExecutor 单线程构造度量器 + `warmUpProbe()` 一次（**记录员实测 :149-150**；字体首初始化移出 1500 ms 窗口 = R2 D6；失败走既有 killSelf 路径），先 set 度量器再置 ready；`runRequest` 用服务级度量器替换 Unwired；`UnwiredVisualContentMeasurer` 与 `VisualMeasurementUnavailableException` 删除（**记录员 grep 全仓仅余 2 处 KDoc 历史提法**：`VisualContentMeasuring.kt:24`、`VisualJsService.kt:83`）。Paint 线程独占（warm-up 与请求同一单线程执行器串行），不设 locale/subpixel 可变项（R2 §1.5-4：可变前须先入指纹）。
- **测试**：`VisualContentMeasuringTest` 14 例（R2 §6 JVM 可达判据 1-5 + 四个方面）：纯几何空批次→后端零触达、端口预算 128 条/6000 字符回归不放宽、text 测宽/断行/区间 artifact/CRLF/尾换行空段、math 金样 ×2（`\frac{1}{2}`→24/37/18.5du、`x`→8/16/6.4du，共享 lowering 公式手工推导非回抄实现）、超预算降级如实标注、确定性 ×3、单位换算（fontSizePx=32、fontScale 线性、du 不再乘 fontScale）、环境非法构造 fail-closed ×6。
- **勾稽**：契约 :72 状态句与 :114/:115 现状表两行更新（含证据行号）；4E 计划 :97 验收项加部分落地注记（缓存面/painter 回放/设备轨如实标未做）。同批并行注记（T2 自登记）：T4 线改 `VisualJsClientEngine.kt`/`VisualJsFrameQueue.kt`、T1 线改引擎与新文件——T2 未触碰；契约 :72 同段落带 T4 队列裁定编辑（**目标文件变脏整文件纳入并注明**，§7 块 2）。落点外历史记录（决策台账 :3373、known-defects :2586 KD-66 历史 Progress、各批次 research 记录）仍留 Unwired 旧表述不改，台账同步归 T5/本记录员。

### 3.3 T3 input/metrics 深冻结 —— **落码成立（本地 21/21 断言）/ 设备绿证据被 T1 缺陷阻断，复验 UNVERIFIED**

- 文件（2）：`QuickJsVisualExecutionEngine.kt`（改，同 T1）、`QuickJsVisualExecutionEngineInstrumentedTest.kt`（改，同 T1）。均落本批落点且开工时干净。
- **冻结落地**：BOOTSTRAP_JS 追加 `Object.freeze(Object)`（把注入时刻后仍会被用到的 `Object.freeze`/`Object.getOwnPropertyNames` 两原语钉死——程序在源码求值期重绑它们即可让冻结空转，该失败已被本地 RED 基线证伪当前管线）；新增 `DEEP_FREEZE_JS` 常量（具名 IIFE 递归 freeze 整图含数组元素），prepare/render 两注入点分别包住 inputJson 与 metricsJsonText 字面量。**设计为内联函数表达式而非 bootstrap 全局函数**：调用点 evaluate 在用户源码之后执行、期间 `globalThis.*` 可被程序重绑（WRITER 用例实证），具名函数表达式递归名绑定在自身作用域不受重绑影响；配合 bootstrap 冻结 Object，两原语在调用点同样不可替换。冻结语义按 ES 规范钉死（sloppy 静默失败读回原值、strict 抛 TypeError）；登记规范特例（`Array.prototype.push` 以 throw-on-fail 写 length，对冻结数组 sloppy 下也抛——夹具用索引赋值表达扩展尝试）；登记边界（KDoc）：`Object.prototype` 与 `JSON.stringify/parse` 未冻（原型污染无法劫持自有字段读取、输出侧完整性属另一硬化面）。
- **测试**：仪器化 6 例（sloppy/strict 改写静默失败+读回原值+isFrozen 探针；strict 抛 TypeError→INVALID_PROGRAM 且 measurer.calls 钉住失败位置；`Object.freeze` 重绑攻击被 bootstrap 硬化拆解——**记录员实测用例 `rebindingObjectFreezeCannotDefeatTheDeepFreeze` 在 `QuickJsVisualExecutionEngineInstrumentedTest.kt:384`**；深冻结与 Date 封闭/种子 PRNG 同程序共存+同 seed 序列重放），metrics 用例兼得宿主侧双重证词。批次 21 的 Date 封闭/Math.random 值捕获配方零改动，既有 RANDOM_SOURCE 用例同走新管线。
- **本地验证（轨自报，记录员未复跑）**：Node v26.4.0 21/21 断言红→绿（RED 4 项证明改写逃逸成立、GREEN 17 项钉死目标语义）；shipping 两常量经 sed 提取与验证字面量 byte-exact diff 双 MATCH。设备门/Gradle 门按纪律由脚本串行执行，本任务未运行（如实登记）。**交叉影响（复核 R8 登记）**：freeze 用例终断言 `result as Rendered`（:392）在 compile 必败下先红——T3 机制未获本批设备绿证明，随隔离套件修复后复验。

### 3.4 T4 契约同步 —— **完成（纯文档/KDoc，零行为变更）**

- 文件（5）：`docs/current/visual-generation-contract.md`、`VisualJsFrameQueue.kt`、`VisualJsClientEngine.kt`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`、`docs/known-defects.md`（均改）。
- ① **队列"取消在飞帧不再 abandon worker"主会话裁定在契约 §4:72 与两处 KDoc 定稿**（语义=取消只出队自己的等待帧、不打断在飞执行、会话按自身预算跑完结果丢弃，标注"主会话已裁定确认采纳——批次 25 记录 §3.3 ②，批次 27 T4 定稿"；**本记录员已同步登记决策台账，§8**）；死亡清队（§3.3 ①）未经裁定、三处"待主会话确认"原样保留。② **phase 字段/软期限两开口项**：按工单保护条款落为契约 :72 "列主会话待裁"登记（含两读法全文、wire 信封无 phase 字段的实读复核、断言前置条件），**未自行定谳**。③ **准入清单**：P-08 剩余缺口①-⑤标待批次 27 设备轨；A-11 按基线"按清单行义同步"加口径注记；R-02/R-04 姊妹轨同步复核后未重复编辑；KD-76 ③④⑤在 T5 回填基础上补 T4 同步句。④ **核对全过**（轨自报）：定稿标记 grep 命中、旧"同列待确认"0 命中、死亡清队待确认完整保留、**KDoc diff 非注释行=0**、表格管道数 HEAD 对照一致、T5 内容未被破坏。

### 3.5 T5 台账/契约状态同步 —— **完成（登记时点设备未出数；warm-up 出数回填归本记录员，§8）**

- 文件（3）：`docs/known-defects.md`、`docs/current/visual-generation-contract.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（均改）。
- **KD-76**（known-defects 设备判据口径块）按批次 26 设备证据刷新：③ 尝试成功、不判定达成（PARTIAL）；④ 强证据（v3 补记解除弱口径）；⑤ 行为层 ×2 轮绿（UI/文案面与 R3 文本升级未做，归主会话）；「warm-up 实测装载成本/收益」缺口标注归批次 27 设备轨（**当时点正确，设备本批已出数，回填见 §8**）。同事实在契约 :72 同步刷新两处状态句（队列"待主会话确认"两处标注未动，归 T4）。**准入清单**：P-07 行刷新；P-12 行注销"批次 26 T2 已执行且未含本行判据"；R-02 行补标签失配注记 + 证据归批次 27 设备轨；R-04 行登记批次 26 E-04 复跑与批次 27 承接、KD-69 锚改现值；§0 记号行"设备轨未出数"改已出数。**行号勘误收尾**：批次 26 step03 §9 勘误传播复核完整（三台账零旧值残留、更正值经源码实读正确）；KD-76 Symptom 漂移锚补时点注并给实测现值。**核对清单落为清单新增附录 D**（69 行不变、0 项转通过、`git diff --check` exit=0），并如实登记未改项（P-01/P-04/P-09 行与 v1 时代漂移锚，归属后续清单复核轨/T4）。三文件编辑前均 git 干净，编辑后为 3 个 M；未触碰任何热区与队列裁定标注。

### 3.6 T6 条件轨 —— **完成（分支 D 登记，零改动、零文件落盘）**

- 开工再核：脚本布尔（library/tutor/ui clean=false）与实测一致——feature/library M=30/??=13、feature/tutor M=37/??=22、core/ui M=11/??=24、core/domain M=5、feature/review M=11、feature/capture M=15、feature/profile M=3、app M=27/??=7，全部脏。按批次 24 R4 顺序（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2→阶段6→5c）对九个排队项逐项复核：**全部仍 pending**，载体均在现锚点，未重复建任何 KD/条目/文件。唯一状态变化：AUD-08 工具半边（R4 波次 0）已由批次 26 T4 闭环，仅剩收口（波次 4，设备/模型前提）排队。登记三处锚点漂移：KD-68 :2646→:2652；MASTER-LINE AUD-08 门行 :146→:148；LearningMasteryScreen WeaknessRow→KnowledgeMasteryRow 改名（批次 25 已登记，本次复核仍在且行仍不可点）。另观察到 `?? tools/visual_compiler/` 为本批 T1 落点内新产物，本轨未触碰。批次 26 未单列 T6（热区登记沿批次 24 R4 顺序维持），本批为批次 25 后首次逐项复核。

### 3.7 设备轨（五步串行；记录员未复跑，以下转录自 E-file 并经索引/step 文件实读核对）

> 环境：`emulator-5554`（test_device，API 34，x86_64，PAGE_SIZE 4096）跑全部五步；`emulator-5556`（ps16k_x86_64，API 36，16384）只读核（三值核对，未安装未执行）；5584 属他线全程未触碰。HEAD `caa0726a`；构建只跑 Gradle `assemble*`（debug 三件套 exit=0/43 s、release exit=0/2 m57 s，`RELEASE_*` 用一次性本地 key `CN=Batch27 Local Smoke` 非生产证书）+ 裸 `am instrument`，`connectedAndroidTest` 零次；git 写操作零次；产品/测试源码零改动。全程唯一设备态变更 = `adb root`→`adb unroot`（R-04 步内，已恢复）。设备已恢复 debug 三件套（设备侧 sha256 与产物逐字节一致）。

| 步 | 判定 | 关键事实（E-file 锚点） |
|---|---|---|
| step00 环境/构建/产物身份 | PASS（信息性） | 产物 sha256 表（app debug 189,399,345 B；app debug androidTest **与批次 26 逐字节一致**——本批 app androidTest 源零改动；core:data androidTest 197,639,024 B 含 T1/T3 新用例；release 155,732,032 B） |
| step01 一 P-08 隔离启动面审计 | **PASS（0 Provider 暴露 + 短路实证）** | 合并清单 6 Provider 全部 default 进程、无 `:visual_js` 归属（aapt2 dump xmltree 实测）；`dumpsys package/activity providers` grep visual_js 均 0 命中；16/16 worker 日志面 sqlite=0/room=0/startup=0（worker 侧仅 VisualJsService 98 行+nativeloader/ziparchive），主进程对照 Room 打开+189 条 StrictMode——**Application 短路（`SmartMistakeBookApplication.kt:287-304`，guard 后才有 StrictMode/Room）结构性实证**；AndroidX Startup 不进隔离进程。**命令修正登记**：R2 G-1 ① 字面 `pidof` 对存活 worker 也恒空（4 变体实测），可用等价=`ps -A \| grep visual_js`+AMS `Start proc` 行 |
| step02 二 A-11 隔离进程网络/filesDir 负向 | **观测级成立** | 存活 worker 实测 `u:r:isolated_app:s0:c512,c768`、Uid 99034（隔离段）、补充组仅 1065 3009（无 3001-3005 网络组）；数据目录 0700 u0_a214 + **MCS 类别集不支配**（worker 域 {c512,c768} ⊉ 文件 {c214,c256,c512,c768}）→ DAC/SELinux 双拒（原"不交"措辞与实测矛盾，step02 §9 更正）；`workerUid()!=myUid` 断言在套件内实际执行并通过（失败点在其后 :134）；进程内 Socket/filesDir 写用例未执行（无 runner 不加 seam，工单允许"用例**或**观测"） |
| step03 三 R-04/R-02 release 隔离往返 | **阻塞（三重独立实证）** | libquickjs.so 双 ABI 在包 ✓（arm64 924,936 B + x86_64 911,248 B）；① R8 把 AIDL/协议/引擎类型全部改名（dexdump 仅 12 个 manifest 组件类保留名），重签 test 包后 instrument 真实启动但 app 进程首触即 `NoClassDefFoundError Intrinsics/Trace` 崩溃；② instrument 签名门（debug 签 test vs 本地 key 签 release）；③ 非 exported 服务 shell 拒启（uid 2000）、root 通道 BAL 拒启（uid 0，已 root/unroot 恢复）。解除路径（keep 规则/可测 release 变体/判据改口径）归主会话，本轨零构建改动；**且 compile 缺陷修复前任何变体都无 Rendered 往返** |
| step04 四 warm-up 装载成本实测 | **出数** | 控制组（沉降态、单用例×10、每次 1 新 worker）样本 652–738 ms：**p50=686 / p95=725 / max 738 / mean 688**；套件 churn 组 n=16 中位 **675.5 ms**（churn 尾 1259–1746 ms 为挂死注入负控负载同窗，非环境故障）；口径=AMS `Start proc`→`warm-up finished`（logcat threadtime 毫秒、按 pid 配对）；**26/26 worker 100% ready**；收益半边无对照（warm-up 已结构性前置，只能由批次 23 E-01d 历史红例反证）、arm64/真机 UNVERIFIED（step04 §6）；第一轮采样流程缺陷（每轮 logcat -c 只留末轮）作废重跑已登记（§5）；`READY_TIMEOUT=10 s` 对 p95 余量 ≈14×（churn 尾 ≈5.7×）——设备校准当前无数据压力，仅 x86_64 口径 |
| step05 五 隔离套件与备份抽验 | **隔离红 + 备份绿** | **Isolation 13 例 10 红**——统一签名 `INVALID_SCENE『SCENE_FIELD_TYPE: __visualCompile expects three JSON text arguments』`（`Tests run: 13, Failures: 10, Time: 56.723`），归因**本批 T1 compile 接线缺陷**（设备首曝；3 绿=两注入夹具+BUSY 负向，均不达 compile）；**交叉证**：core:data 引擎套件 26 例 9 红同因（`Tests run: 26, Failures: 9`，首红点 `:626 assertTrue(first is Rendered)`），排除夹具问题；**Race 2/2 绿、备份 34/34 绿（不回退）**；16/16 ready、3 条 watchdog kill 全部归因负控注入、9 类崩溃签名 0；`workerIsIsolated` 用例在失败前 :123 UID 断言已通过。**隔离面相对批次 26 的 13/13 属回退，回退源定位到文件行级并给出最小修复形态**（编译器三参须以 JS 字符串字面量传递+正确转义；render 两参对象字面量插值不动）——修复环已按此落码（§4） |

### 3.8 证据轨 —— **按 R4 落盘并独立复核；本记录补三测注记**

- **落盘**：`docs/research/evidence/2026-10-27-batch-27/` **7 个 `.md`**（00-index + step00–05），记录员实测 **66,120 B**（单文件最大 `step05` 11,222 B / 176 行）；R4 上限（单文件 64 KiB/400 行、单批 16 个/512 KiB）未触。**体量漂移链如实登记**：设备轨首测 62,826 B → 落盘员复核重测 65,830 B → 记录步三测 **66,120 B**（+290 B 为索引回链与更正行追加，非新文件；纯记账误差，判定不受影响）——三测注记由本记录员补入索引 §6（§8）。
- **脱敏复核（索引 §6，轨实跑）**：secrets 0 / credential assignments 0 / emails 0 / home paths 1 = 扫描正则自身（逐条确认为自匹配）；`<local-smoke-b27>` 占位外无明文口令（追加 0 命中核）。keystore 在 `build/` 不入仓。
- **回链**：索引 :3-4 已预写"记录：docs/research/2026-10-27-batch-27-record.md（后写）"——本文件即该回链目标；`build/device-track-batch27/` 只作原始全量，不是判定证据。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。"工单给定"行来自任务材料；设备数字来自 E-file，记录员实读判定行（step04 全文、step05 判定行 grep）与索引 §3/§4，未复跑。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 3 轮，修复后） | **exit=0**（工单给定） | 无本批仓内日志；记录员未复跑。含 JVM 校验器 32 例、分类器 19 例、度量 14 例 |
| 修复环复跑 | **exit=0**（工单给定） | 发现 1+2+3 处置后复跑 |
| 强门（第 2 轮） | **exit=0**（工单给定） | 六 androidTest 编译 + `:app:assembleLocalFirstDebug`（工单给定口径，记录员未复跑） |
| 设备 P-08 | **PASS（E-01）** | step01 §3；0 Provider 暴露 + Application 短路实证 + pidof 命令修正登记 |
| 设备 A-11 | **观测级成立（E-02）** | step02 §3/§9；进程内负向未执行（无 runner），按工单"用例或观测"以观测落盘 |
| 设备 R-04（R8 隔离往返） | **阻塞（E-03）** | 三重实证（R8 改名/签名门/BAL 拒启）；解除路径归主会话 |
| 设备 warm-up 装载成本 | **出数（E-04）** | p50=686/p95=725 ms（控制组 n=10）；churn n=16 中位 675.5 ms；26/26 ready |
| 设备隔离套件 | **红 10/13（E-05）= T1 缺陷，修复环已修复** | 统一签名 SCENE_FIELD_TYPE；Race 2/2 绿、备份 34/34 绿不回退；**修复后设备复验 UNVERIFIED** |
| Node 编译器测试台 | **89/89 passed，EXIT=0（记录员实跑）** | `node tools/visual_compiler/run_tests.mjs`；+5 原型链负向为修复环新增 |
| 锁文件生成 | **未执行（窗口未开）** | 计划 §0:7"锁窗口仍未开"；KD-72 open |
| 生产 release 签名 | **UNVERIFIED** | 一次性本地 key `CN=Batch27 Local Smoke`（非生产证书，口令占位 `<local-smoke-b27>`，keystore 不入仓） |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；模型质量/费用未验；JS 不广告 |

**修复环第 1 轮处置（工单转录 + 记录员树内核验）**：

1. **发现 1 + 发现 2（同根因，T1 compile 接线缺陷【高】）——已修复（一处修复覆盖两条）**：静态读码证实两发现描述一致且互为印证（三 JSON 文本裸插值违反编译器三文本参数契约）。影响面闭合（静态确认）：prepare/render 的 `$inputJson`/`$metricsJsonText` 插值是对象表达式注入（SDK 契约收对象图、DEEP_FREEZE_JS 吃对象），设计内不属同型缺陷；`__visualCompile` 全仓唯一调用点即该处；`QuickJsSceneCompilerInstrumentedTest` 走真引擎无自有接线。修复 = 调用点改 `jsStringLiteral(...)` 包裹三文本（**记录员实测现值 :756**）+ 新增 `jsStringLiteral`（转义集：反斜杠/双引号/C0 控制字符具名+`\u00XX`，**现值 :1034**；KDoc 指认它消灭的失败）。
2. **发现 3（编译器原型链逃逸【中】）——已修复（3 处；单测门复跑 exit=0）**：FILL_RULES/CAPS/JOINS/H_ALIGNS/V_ALIGNS 查表改 `nullProtoTable`（**记录员实测现值 `visual-compiler.js:438-450`**），checkKeys 的 allowed 改自有键判定——继承键名（constructor/toString/valueOf）不再能绕过任何 fail-closed 门；**两端各补负向用例**（Node 台 5 例 `run_tests.mjs:433-450` + JVM 校验器原型链用例 `VisualDrawOpsValidatorTest.kt:278/:296` 等——**该测试文件由 T1 自报 27 例增至记录员实测 32 例**）。修复前形态：cap='constructor' 绕过 SCENE_STYLE_CAP 并发射、hitRegion rule='valueOf' 同型、场景自有键 toString 绕过 SCENE_FIELD_UNKNOWN（③ 无下游兜住，带垃圾字段的场景会随 Rendered 保留——现已由解析层自有键判定封死）。

**记录员对门禁的保留**：各门 `exit=0` 没有本批日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file 且记录员实读 step04 全文与 step05 判定行/索引 §3/§4，但未复跑。修复后隔离套件未再上设备——**"修复后设备全绿"不存在任何证据，不得表述为已通过**。

## 5. 复核发现与处置

> **口径说明**：下表 R1–R10 转录自复核轨（"复核轨判定"列为其标注的 `已独立复核`/`未复核`；R1/R5 为两条复核线对同一缺陷的独立证实，并列保留）；末列为本记录员本轮核验口径——`实跑复核吻合` = 记录员本轮重跑/实读同值；`抽验吻合` = 只读抽验子项；`转录` = 未重跑、按复核材料与在盘证据转述。级别分布：高 ×2（同根因）、中 ×1、低 ×7；已修复 ×3 条（覆盖高×2+中×1），无未处置高/中项。

| # | 位置 | 发现 | 复核轨判定 | 记录员核验口径 |
|---|---|---|---|---|
| R1 | `QuickJsVisualExecutionEngine.kt:750-752`（对照 `visual-compiler.js:1079-1081`；step05 §2/§3.1） | **【高】T1 compile 接线缺陷**：三 JSON 文本未加引号直插 JS 源，编译器实参成 JS 对象值，typeof 门即拒——Isolation 13 例 10 红、引擎套件 26 例 9 红、相对批次 26 属真回退；3 绿例确不达 compile、16/16 ready、崩溃签名 0——缺陷只在 compile 接线；T1 声明 Kotlin 侧 UNVERIFIED 属如实申报，但批次收口不得把 SceneV1 compile 表述为设备通过 | 已独立复核 | **实跑复核吻合（修复侧）**：修复在树（`jsStringLiteral` :756/:1034 实读）、Node 台 89/89 EXIT=0 实跑；设备红证据 E-file 实读（step05 判定行 grep 命中）。**修复后设备复验 UNVERIFIED**（登记 §6 #1）。 |
| R2 | `docs/current/visual-generation-contract.md` §5.1 现状表 | 契约 §5.1 表『直接引擎由 app 侧编排』行证据锚漂移：所引 `:141-150` 在现树是 close KDoc，measurer 实位于 `:155-156`；疑为 T3 同文件落层后行位移未同步；建议随批次收口勘误，不影响该行结论 | 已独立复核 | **实跑复核吻合**（`sed -n '138,152p'` = close KDoc、`grep -n measurer` = :156，记录员本轮实测）。**本记录未代改**（工单同步清单未含此项，登记 §6 #10 留契约同步轨/主会话）。 |
| R3 | `evidence/2026-10-27-batch-27/00-index.md:127-129` | 证据体量读数二次漂移：索引记 65,830 B，按 §6 原命令复跑实测 66,120 B（+290 B）——『落盘员复核重测』段自身入盘后未再重测；上限仍全满足，纯记账误差 | 已独立复核 | **实跑复核吻合**（记录员 `find -printf %s \| awk` 实测 66,120 B 同值）。**已由本记录员处置**：索引 §6 补三测注记（62,826→65,830→66,120 漂移链，§8）。 |
| R4 | 契约 `:72` 与 `known-defects.md:2843`（warm-up 缺口句） | 两处仍写『warm-up 实测装载成本归批次 27 设备轨』而设备已出数（p50=686/p95=725 ms）——非虚假陈述（T4/T5 编辑在设备出数前，时序自洽），属记录步收口待回填项；回填应带 step04 §6 边界 | 已独立复核 | **实跑复核吻合**（两处原文实读 + step04 数字分位算术复算吻合）。**已由本记录员回填**：契约 :72 与 KD-76 批次 27 Progress 块均带上 §6 边界（§8）。 |
| R5 | `QuickJsVisualExecutionEngine.kt:751`（第二复核线独立证实） | 同 R1：Node 台直调导出面、编译器本身健康故该台对此缺陷盲；JVM 门不跑 QuickJS；Kotlin 侧 UNVERIFIED 归双门；6 个编译用例设备轨全红；以真实资产 Node 复现两态对照（raw 直插→SCENE_FIELD_TYPE；字符串实参→编译成功） | 已独立复核 | 同 R1（同一修复覆盖）。 |
| R6 | `visual-compiler.js:426-430`（查表）、`:378-384`（checkKeys） | **【中】编译器 fail-closed 面原型链逃逸（3 处）**：cap='constructor' 绕过 SCENE_STYLE_CAP 并发射进 DrawOps；hitRegion rule='valueOf' 同型；场景自有键 toString 绕过 SCENE_FIELD_UNKNOWN（无下游兜住）；hAlign='toString' 经 Bounds 兜住但错误码错归；测试覆盖缺口（三测试文件与 Node 台 grep 原型链键名 0 命中）；修复方向=查表改 null-proto 或自有键判定+两端补负向用例 | 已独立复核 | **实跑复核吻合（修复+测试侧）**：`nullProtoTable` :438-450 实读在树；Node 台 5 例负向（:433-450）+ JVM 校验器原型链用例（:278/:296）在树且随 89/89 绿。 |
| R7 | `visual-compiler.js:633-654`（parseMetrics 无条数上限） | 【低】编译器侧对 metrics 数组无条数预算（场景/命令/命中区/输出均有）；当前安全（metricsJsonText 由宿主构造器强制 ≤128 条/6000 字符、16 MiB 堆+100 ms 协作期兜底），但与文件头"与 Kotlin 侧同值"的预算镜像声明不完整——将来若有非宿主构造 metrics 进入 compile 上下文，此面无 JS 侧门；信息性加固项 | 已独立复核 | 转录（信息性加固项，未随本批修复环处置，登记 §6 #9）。 |
| R8 | 引擎 `:1003-1006`（DEEP_FREEZE_JS）、`:349-353/:381-385`（注入点）、`:731-735` KDoc、`:966-972`（bootstrap） | 【低】冻结覆盖三注入点闭合无遗漏；bootstrap 冻 Object 防重绑、设备用例钉住；两个边界（Object.prototype/JSON 不冻）有 KDoc 理由；**风险（将来新增）**：冻结是调用点纪律——每个新注入实参必须记得包 DEEP_FREEZE_JS，无结构性保证，建议新注入点以 grep/审查门核对；**T3 设备绿证据当前被高等级缺陷阻断**（freeze 用例终断言 Rendered 强转会在 compile 必败下先红——T3 机制未获本批设备绿证明，本地 RED 基线为主张来源） | 已独立复核 | 转录（记录员未复跑本地 21/21 断言）；复验归隔离套件修复后同轮（§6 #1/#4）。 |
| R9 | `VisualJsService.kt:248-305/:318-471/:518-534/:624-627`；`VisualContentMeasuring.kt:58-80/:379-421`；引擎 `:361-372` | 【T2·无发现（结构核过，未执行）】度量不会阻塞 binder/watchdog：AIDL 在 Binder 线程只做检查即返 ACCEPTED；runRequest 在专用单线程 runBlocking 内同步度量；watchdog 只做 CAS+Log+SIGKILL；度量输入有界（≤128 条/6000 字符）；度量计入 1500 ms 服务硬期限而非 100 ms 协作期；warm-up 探针已移出首初始化（step04 p50=686 ms 出数佐证） | 未复核 | 转录（读码结构复核而非执行验证，原口径如实保留）。 |
| R10 | 契约 `git diff HEAD`（§4/§5 两行）；`VisualJsProtocol.kt:75-100/:214-217/:280-289`；KD-76 勘误；清单 v3 补记 | 【T4·无越权】契约改动全落裁定范围：队列取消语义定稿与实现-KDoc-契约三方一致；两开口项显式登记未擅自裁；wire 加法字段读取侧兼容、未升 PROTOCOL_VERSION；Rendered 加可空默认字段零破坏；KD-76 为勘误+证据刷新保持 open；清单不新增行、0 项转通过、MVA 不成立维持 | 已独立复核 | 抽验吻合（协议加法字段 `VisualJsProtocol.kt:97-98/:217-218` 与域默认参数 `VisualExecutionEngine.kt:245-246` 记录员实读；契约/KDoc 三方一致抽核）。 |

**记录员追加核对（不在原清单）**：

1. **工单数字与现树的三处时点差（如实登记，非失真）**：① T1 轨自报 Node 台 84/84 与 JVM 校验器 27 例——修复环补原型链负向后**现值 89/89 与 32 例**（记录员复跑/实测）；② T1 轨自报编译器 43 KiB——修复后现值 **44,718 B**；③ 修复环文字给的 `:748-759`/`:1013-1058` 为修复时点行号——现树实测 `:756`/`:1034`（同文件 T3 层落定后位移）。本记录一律以记录员实测现值为准，工单值保留于括注。
2. **修复形态与设备轨最小修复形态的一致性**：step05 §3.2 给出的最小修复形态（"编译器三参须以 JS 字符串字面量传递，需正确转义；render 两参对象字面量插值不动"）与修复环落码逐点吻合（记录员实读双方）。
3. **T6 观察项核验**：`?? tools/visual_compiler/` 实测存在且只含 `run_tests.mjs`——与本记录 §7 白名单一致。
4. **工单材料"62,826 B/7 md"为设备轨首测值**：终值 66,120 B（三测链，§3.8）。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门与设备**

1. **修复后设备复验 UNVERIFIED（本批最重遗留）**：隔离套件 10 红已归因修复（Node 台 89/89、单测门复跑 exit=0），但**修复后未再上设备**——"隔离 13/13 恢复绿""T3 深冻结用例设备绿""core:data 引擎套件 26/26""`QuickJsSceneCompilerInstrumentedTest` 6 例"全部缺设备证据；归属=修复后设备轨复验（下一轮设备窗口或本批收口后补跑，R4 模板照旧）。
2. **门禁 `exit=0` 无仓内日志**（单测门第 3 轮/修复环复跑/强门第 2 轮均工单给定）；记录员未运行 Gradle。提交前建议主会话把命令与 tail 落盘 `build/`。
3. **R-04 阻塞（三重独立实证）**：R8 改名→test dex 类解析崩溃、instrument 签名门、BAL/未导出拒启；libquickjs.so 双 ABI 在包 ✓ 为已交付半边。解除路径（keep 规则/可测 release 变体/判据改口径）**归主会话裁定**，本轨零构建改动；且 compile 缺陷修复的设备复验完成前，任何变体都无 Rendered 往返可跑。
4. **T3 深冻结设备绿被阻断**：freeze 用例终断言 Rendered 强转在 compile 必败下先红——T3 机制未获本批设备绿证明（本地 21/21 断言为轨自报、记录员未复跑）；与 #1 同轮复验。
5. **A-11 进程内负向未执行**：进程内 Socket/filesDir 写用例需隔离进程内 runner 或产品 seam（构建/产品决策）；以观测落盘是工单允许口径，负向用例仍缺。

**代码/复核残留**

6. **AIDL 门**：本批未跑 Gradle AIDL 任务（设备轨只用 assemble*/am instrument）；`aidl.exe` 双输入禁用规则继续有效。
7. **R2 假设 A1/A2 与精度边界 P1/P2**：隔离进程内字体/资源面（设备轨度量往返仍未验证——本批设备轨无 text 度量往返绿证据，因隔离套件红）；同环境跨进程逐位一致；API<33 混排行高可能低估；math 盒宽 0.5em 估算 CJK 低估（真实字形度量为共享 lowering 后续工作，不能只在度量侧做）。
8. **P-08 命令修正待清单吸收**：R2 G-1 ① 字面 `pidof` 对存活 worker 恒空（4 变体实测）；等价命令（`ps -A | grep visual_js`+AMS `Start proc` 行）已落 step01，准入清单 P-08 行的命令文本待下次清单同步轨更新。
9. **parseMetrics 无 metrics 条数预算**（R7，信息性加固项）：当前由宿主构造器有界链兜住；将来契约扩展允许非宿主构造 metrics 进入 compile 上下文前，须补 JS 侧门（与"与 Kotlin 侧同值"镜像声明对齐）。
10. **契约 §5.1 证据锚漂移未勘误**（R2 发现）：`QuickJsVisualExecutionEngine.kt:141-150`→现值 `:155-156`；记录员已实读证实，未代改（工单同步清单未含）；归契约同步轨/主会话随下次收口勘误。
11. **T1 预算上限场景设备实测 UNVERIFIED**（R1 §5.2/§8）：256 节点/4096 命令在设备 QuickJS 的实测耗时只有 Wasm 外推（Node 台 1.9 ms 为 V8 口径参考）；第三上下文每事务创建成本设备 UNVERIFIED；受 1500 ms 硬期限兜底。
12. **工单同步清单外的 4E 计划注记**：T2 在 `docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md:97` 加验收项部分落地注记——该文件不在本批落点文字内亦不在热区清单，属 4E 线自有计划文件；提交时整文件纳入并注明（§7 块 2），归属登记于此防无声丢失。

**台账 / 数据 / 环境**

13. **KD-76 open（本批不关闭）**：warm-up 装载成本缺口已出数（本记录员回填），但 ③ 产品进程/UI 首请求、29.5 s worst-case 链、低内存条件、`READY_TIMEOUT=10 s` 设备校准（当前无数据压力但仅 x86_64 口径）、收益半边对照、R3 广告门文本升级（**关闭另一半，归主会话**）均 UNVERIFIED。**JS 不广告。**
14. **KD-72 open**：锁窗口未开（他线 6 build 文件未入库）；锁未生成未登记争议；沿用批次 26 R3 预案（W1→W2→W3 等待清单）。
15. **arm64/16KB UNVERIFIED**（本机原理性不可行定论沿用）；**KD-77 open**（API 23 镜像未装未测）；生产 release 签名 UNVERIFIED；ps16k/KD-70/16KB 面本批未执行（5556 只读核登记）。
16. **origin/main 未 fetch**：远端实时 hash 未核（HEAD `caa0726a` 是否仍与远端一致 UNVERIFIED）；Mimosa 无完整扫描结论，不宣称项目安全。
17. **热区在飞未触碰**（feature 五模块、core/ui、core/markdown 整目录、core/export、core/domain 开工即脏 4M、core/data 他线 M、settings.gradle.kts、.gitignore、app Root 等、docs/current 其余 M、docs/research 他线未跟踪记录、tools/kb、knowledge-production）；MVA 剩余缺口（painter/PDF 回放、持久化/导出、双后端、质量门、缓存面）本批未做，4E.2 MVA 维持不成立。
18. **待主会话裁定汇总**：§64 phase 字段两读法与 `:70` 软期限落门（契约 :72 已列待裁）；死亡清队语义确认；R-04 解除路径三选项；契约 §5.1 锚勘误归属；KD-76 关闭另一半（R3 文本升级归属与文案）；隔离套件修复后设备复验编排；R-04 与 P-08/A-11 判据是否随批次 27 证据转清单行。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；热区、锁文件（`*/gradle.lockfile` 全部 ??）与 docs/current 其余 M 一律排除。按 ≤6 路径/块（新 = ??，改 = M）：

- **块 1（T1 编译器/SDK 资产 + 测试台）**：`core/data/src/main/resources/visual/visual-compiler.js`（新）、`core/data/src/main/resources/visual/visual-sdk.js`（新）、`tools/visual_compiler/run_tests.mjs`（新）——**注**：`?? core/data/src/main/resources/visual/` 与 `?? tools/visual_compiler/` 为整新目录，add 按文件路径不按目录
- **块 2（T1 宿主类型/校验/资产装载）**：`core/data/.../visual/VisualDrawOps.kt`（新）、`VisualDrawOpsValidator.kt`（新）、`VisualJsAssets.kt`（新）
- **块 3（T1/T3 引擎 + 分类器 + 域类型）**：`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（改；**整文件纳入并注明**：T1 compile 接线与 jsStringLiteral 修复 + T3 深冻结两常量与注入点叠加）、`core/data/.../visual/VisualJsFailureClassification.kt`（改）、`core/domain/.../VisualExecutionEngine.kt`（改；Rendered 加字段——**该文件开工时干净**，非 core/domain 开工即脏 4M 之列）
- **块 4（T1 测试 + T2 新文件）**：`core/data/src/test/.../visual/VisualDrawOpsValidatorTest.kt`（新）、`core/data/src/test/.../visual/VisualJsFailureClassificationTest.kt`（改）、`app/src/main/kotlin/.../visualjs/VisualContentMeasuring.kt`（新）
- **块 5（T2 测试 + 服务 + 协议 + 队列 KDoc）**：`app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（新）、`app/src/main/.../visualjs/VisualJsService.kt`（改）、`app/src/main/.../visualjs/VisualJsProtocol.kt`（改；T1 信封字段 + T2 度量器引用）、`app/src/main/.../visualjs/VisualJsFrameQueue.kt`（改；T4 KDoc）
- **块 6（T1/T3 仪器化测试 + 客户端 KDoc）**：`core/data/src/androidTest/.../visual/QuickJsVisualExecutionEngineInstrumentedTest.kt`（改；T1 夹具升级 + T3 深冻结 6 例）、`QuickJsSceneCompilerInstrumentedTest.kt`（新）、`app/src/main/.../visualjs/VisualJsClientEngine.kt`（改；T4 KDoc）
- **块 7（契约 + 准入清单 + 4E 计划注记）**：`docs/current/visual-generation-contract.md`（改；**整文件纳入并注明**：T2 :72/:114/:115 + T4 队列定稿与开口项 + T5 KD-76 状态 + 记录员 warm-up 回填）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改）、`docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md`（改；T2 :97 注记，整文件纳入并注明）
- **块 8（两研究文档 + 基线）**：`docs/research/2026-10-27-scenev1-compiler-design.md`（新）、`docs/research/2026-10-27-content-measurement.md`（新）、`docs/research/2026-10-27-batch-27-baseline.md`（新）
- **块 9（证据前半）**：`docs/research/evidence/2026-10-27-batch-27/00-index.md`（新；含记录员三测注记）、`step00-environment.md`、`step01-p08-isolation-surface.md`、`step02-a11-isolated-negative.md`、`step03-r04-release-roundtrip.md`（均新）
- **块 10（证据后半 + 本记录）**：`step04-warmup-cost.md`、`step05-isolation-suite-backup.md`（新）、`docs/research/2026-10-27-batch-27-record.md`（新）
- **块 11（台账同步）**：`docs/known-defects.md`（改；**整文件纳入并注明**：T5 状态同步 + T4 KD-76 同步句 + 记录员 KD-76 批次 27 回填块，KD-66/72/75/77 区未动）、`docs/REFACTOR-MASTER-LINE.md`（改；顶部滚动三段）、`docs/research/algorithm-version-ledger.md`（改；§3.37）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改；队列裁定确认条目）

**明确排除（他线在飞/不安全，一律不动）**：

- 全部 `?? */gradle.lockfile`（core/data、core/domain、core/database、core/model、core/ui、feature/* 等 13+，KD-72 open 不得提交）；`settings.gradle.kts`、`.gitignore`（M，他线）。
- `feature/{capture,review,tutor,library,profile}/**`、`core/{ui,database,export}/**`、`core/data` 他线 M（mistake/study/knowledge/model 面）与他线 androidTest/test M、`core/markdown/` 整目录、app 开工即脏 M（Root/SecondaryScreens/Destinations/StartupStateBanner/ExportPdfWorker/ReviewReminder* 等）、docs/current 其余 M（README/architecture/data-schema/implementation-status/model-task-contracts/product-contract/release-gates 等）、`docs/architecture-research-2026-09-22.md`、`docs/audit-2026-09-12-kernel-readiness.md`、`docs/research` 他线未跟踪记录、`docs/superpowers/**` 其余文件、`tools/` 他线 4 M（dense_build/kb_build 面）、`knowledge-production/**`、`build/**`（含 `build/device-track-batch27/` 原始日志与一次性 keystore，.gitignore 忽略）、`tmp/`。
- 批次 23/24/25/26 证据目录（本批零改动）。

**提交说明**：建议 11 块、每块 ≤6 路径；三个整文件纳入的 M 文档（引擎/契约/known-defects）与 4E 计划在提交说明中注明"整文件纳入，含本批多轨改动"。提交/推送由主会话执行；非快进 merge，不 rebase。

## 8. 收口状态（工单给定 + 记录员执行项；提交/推送待主会话）

- **门禁（工单给定）**：单测门第 3 轮 `exit=0`；修复环复跑 `exit=0`；强门第 2 轮 `exit=0`；修复环第 1 轮进入并处置 3 条发现（高×2 同根因 + 中×1），全部修复，无放宽断言（负向用例为加法、查表修复不放宽任何既有门）。仓内无本批门禁日志；记录员未运行 Gradle。
- **设备（工单给定 + E-file）**：五步执行完毕——P-08 PASS、A-11 观测级成立、R-04 阻塞、warm-up 装载成本出数（p50=686/p95=725 ms）、隔离套件红（T1 缺陷，修复环已修复）+ Race 2/2 与备份 34/34 绿；证据 7 个 `.md`/66,120 B 落盘（三测链登记），脱敏全过（1 处自匹配已逐条确认）；设备恢复 debug 三件套 + adb unroot。
- **研究/实施**：R1/R2 两研究文档落盘（262/349 行）；T1–T5 五轨落码/落档完成（T1 Node 台记录员复跑 89/89）；T6 分支 D 登记零改动。
- **台账/契约同步（本记录员执行）**：契约 :72 warm-up 出数回填；known-defects KD-76 批次 27 Progress 回填块（含修复环状态与"修复后设备复验 UNVERIFIED"如实登记）；MASTER-LINE 顶部滚动三段；算法台账 §3.37 零 bump（**登记新增产物面版本 DrawOps V1 `drawSchemaVersion=1`**——非既有串 bump）；决策台账新增批次 27 队列裁定确认条目（取消不 abandon 在飞——主会话已裁定采纳，死亡清队仍待裁）；证据索引 §6 三测注记。
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
- **本批不声称全绿/全就绪**：修复后设备复验 UNVERIFIED（隔离套件/T3 深冻结/编译器 6 例全缺设备绿）；R-04 阻塞；KD-72/75/76/77 open；4E.2 MVA 不成立维持（编译器/度量/冻结落层 ≠ painter/持久化/双后端/质量门就绪）；arm64/真机/生产签名/真实 Provider UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告。
- Mimosa 提交仍无完整扫描结论——不宣称项目安全。

## 附录 A：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
git rev-parse --short HEAD                                   # caa0726a
git status --porcelain | wc -l                               # 699
git status --porcelain | grep -c '^ M'                       # 214
git status --porcelain | grep -c '^??'                       # 485
git diff --cached --name-only | wc -l                        # 0（暂存区空）

# T1 修复与资产（现树实测）
grep -n "jsStringLiteral" core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt
#   → :751(KDoc)/:756(调用点包裹三参)/:1034(函数定义)
grep -n "nullProtoTable\|FILL_RULES = " core/data/src/main/resources/visual/visual-compiler.js
#   → :438(nullProtoTable)/:446-450(五查表全 null-proto)
ls -la core/data/src/main/resources/visual/                  # visual-compiler.js 44,718 B / visual-sdk.js 11,429 B

# T1 Node 测试台复跑（本记录员本轮唯一测试执行）
node tools/visual_compiler/run_tests.mjs
#   → "[timing] upper-bound scene (256 nodes / 4096 commands): 1.9 ms on Node v26.4.0"
#   → "89 passed, 0 failed"，EXIT=0

# 测试面计数
grep -c "@Test" core/data/src/test/.../VisualDrawOpsValidatorTest.kt            # 32
grep -c "@Test" core/data/src/test/.../VisualJsFailureClassificationTest.kt     # 19
grep -c "@Test" app/src/test/.../visualjs/VisualContentMeasuringTest.kt         # 14
grep -c "@Test" core/data/src/androidTest/.../QuickJsVisualExecutionEngineInstrumentedTest.kt  # 26
grep -c "@Test" core/data/src/androidTest/.../QuickJsSceneCompilerInstrumentedTest.kt          # 6
grep -c "@Test" app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt          # 13

# 协议/域/服务/度量接线锚
grep -n "PROTOCOL_VERSION" app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt   # :32 = 2
grep -n "STUDY_DATABASE_VERSION = " core/database/.../StudyDatabase.kt                                     # :134 = 69
grep -n "TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = " core/model/.../ModelTasks.kt                              # :546 = 18
grep -n "drawCommandsJson\|drawSchemaVersion" core/domain/.../VisualExecutionEngine.kt                     # :245-246/:254
grep -n "visualContentMeasurerForService\|warmUpProbe" app/src/main/.../visualjs/VisualJsService.kt        # :149-150
grep -rn "UnwiredVisualContentMeasurer" --include="*.kt" app core                                           # 仅 2 处 KDoc

# 研究文档行数与证据体量
wc -l docs/research/2026-10-27-scenev1-compiler-design.md    # 262
wc -l docs/research/2026-10-27-content-measurement.md        # 349
find docs/research/evidence/2026-10-27-batch-27 -type f -name '*.md' | wc -l            # 7
find docs/research/evidence/2026-10-27-batch-27 -type f -name '*.md' -printf '%s\n' \
  | awk '{s+=$1} END {print s " B total"}'                    # 66120 B total

# 台账/契约锚点（回填后现值）
sed -n '72p' docs/current/visual-generation-contract.md      # warm-up 回填句在 KD-76 状态括注内
grep -n "Progress（2026-10-27 批次 27 设备轨；记录员回填）" docs/known-defects.md   # KD-76 块内 1 处
grep -n "^### 3.37" docs/research/algorithm-version-ledger.md
grep -n "批次 27 主会话队列裁定确认" docs/agent-first-refactor-decisions-2026-09-23.md

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         T3 本地 21/21 断言台、JVM 单测、门判定器、设备任何步骤的复跑。
```

## 9. 本记录的验证结果（记录员实跑，写盘后执行）

- **文档/证据引用检查**：记录内引用的代码锚（`jsStringLiteral` :756/:1034、`nullProtoTable` :438-450、`drawCommandsJson` 域 :245-246、服务度量器 :149-150、协议 :32/:97-98/:217-218、测试计数 32/19/14/26/6/13、`rebindingObjectFreezeCannotDefeatTheDeepFreeze` :384、度量器实位 :156）与 E-file 判定行（step04 全文、step05 `Tests run: 13, Failures: 10`/`OK (2 tests)`/`OK (34 tests)`/`Tests run: 26, Failures: 9`/统一签名/16/16 ready、索引 §3/§4/§6）均为本轮实读/实跑实测值；工单材料与轨自报值的差异（84→89、27→32、43KiB→44,718 B、:1013→:1034、62,826→66,120）已逐项登记归因（§5 追加核对 1–4）。
- **补丁/空白检查**：`git diff --check` → 见下方实跑结果（含本记录员七处文档改动的空白检查）。
- **回填后锚自洽**：known-defects 插入 1 块后 KD-77 标题锚由 `:2853` 下移 2 行（实测现值 `:2855`）；契约 :72 行内替换不改变行数（实测 220 行前后一致）；证据索引插入三测注记 +5 行（实测现值 161 行，体量读数以记录步三测为准）。
- **未执行**：Gradle、设备、git add/commit/push；设备用例与门禁门未由本记录员复跑（口径见 §4 保留与 §6 #1/#2）；T3 本地断言台与 JVM 单测未复跑（记录员核验限于 Node 编译器台与只读核对）。

## 9. 主会话收口（2026-10-28，实跑证据）

- **单测门**（11 模块 `--continue`）：**exit=0**（228 up-to-date，修复环后的树未再变）；**强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（8 executed / 264 up-to-date）。
- **复核两条非低的处置确认**：① 高（compile 接线 `$` 直插）——修复轮已落 `jsStringLiteral()` 编码（:1034，反斜杠/引号/控制字符全转义；:756 三实参全部经编码），主会话读码核验编码器实现正确；② 中（原型链逃逸）——修复轮已落 `nullProtoTable`（FILL_RULES/CAPS/JOINS/H_ALIGNS/V_ALIGNS 全部 null-proto）+ `checkKeys` 改自有键判定（`ObjectProtoHasOwn.call`）+ 危险键拒绝（:270）+ 负向用例在库。设备复跑归批次 28。
- **提交**：`a7b28a47`（37 文件，7,466 插入/128 删除），按 §7 的 11 块分块 add；锁文件与热区未纳入。
- **推送**：`405c8373..a7b28a47` 已上 origin/main（`ls-remote` 复核一致）。
- **本批不声称全绿/全就绪**：设备隔离 13 例 10 红/引擎套件 26 例 9 红是**接线缺陷修复前**的运行（修复后设备复跑归批次 28）；4E.2 MVA 仍不成立（painter/PDF 回放/持久化/双后端/质量门未做）；KD-72/75/76 open；arm64/真机/生产签名 UNVERIFIED；真实 Provider UNVERIFIED；JS 仍不广告。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
