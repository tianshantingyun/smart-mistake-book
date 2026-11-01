# R2 内容度量研究：:visual_js 服务进程的 text/math 度量设计（批次 27，驱动 T2）

日期：2026-10-27。任务来源：批次 27 计划（`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md` §1 R2/T2）。
对象：把 `UnwiredVisualContentMeasurer`（fail-closed 桩）替换为真实内容度量——在 `:visual_js`
隔离进程（非 UI 线程）对 prepare 返回的 text/math 批量请求给出真实宽/高/baseline（契约 §3.2 事务第 2 步）。

证据分级声明：标 **[引证]** 的内容来自本轮实际抓取的官方文档/androidx 源码或本仓源码（附路径/行号）；
标 **[待验证]** 的为单一来源或未证明项，不当定论。本批纪律禁跑 Gradle，全部仓库侧核验为静态读码；
测试判据（§6）是给 T2 实施的红绿标准，本批**未运行**。

---

## 0. 结论速览（推荐组合）

| 问题 | 推荐 |
|---|---|
| 非 UI 线程度量姿势 | 度量跑在服务 `jsExecutor`（单线程）上；`Paint` 线程独占（每度量器实例一个，不跨线程共享）；MVA 文本用"按显式 `\n` 分段 + `Paint.measureText`"的内蕴度量，`StaticLayout` 留给未来定宽换行 |
| math 形态 | **案A**：复用 core:model 纯 Kotlin 链（`parseAndBuildBox`/`MathBoxBuilder`，app 已依赖 core:model），失败按契约降级为可读文本（text 路径度量原文），不做全量占位 |
| 度量位置 | **服务进程 Kotlin 层**（`VisualContentMeasurer` seam 原样），**不**注入字体进 JS——契约 §3.2 第 2 步"原生度量"是处方，且 JS 内度量会让"暗改宽度"冻结检查失效 |
| 接口 | `AndroidVisualContentMeasurer : VisualContentMeasurer` + `VisualMeasureEnvironment` 快照（density/fontScale/字体/locale/sizeSp 参与摘要）；端口五字段不变，断行留在内部 artifact seam，未来 painter 复用同一 seam |

---

## 1. 非 UI 线程做 text 度量的正确姿势

### 1.1 本仓线程模型事实（读码）

- 度量调用点：`QuickJsVisualExecutionEngine.runTransaction` 在 prepare 与 render 之间恰调一次
  `measurer.measure(batch)`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:339-348`）。
- 该调用发生在 `VisualJsService.runRequest` 内，即 `visual-js-executor` **单守护线程**上
  （`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:79-81` 投递、
  `:377-404` 依次 armWatchdog → 构造引擎（传入 measurer）→ `runBlocking { engine.execute }`）。
- **度量时间计入 1500 ms 服务硬期限**：watchdog 在引擎构造前 arm（`VisualJsService.kt:377`），
  到点 `killSelf`（`:484-500`）。因此度量必须远小于 1500 ms；批预算 128 条/6000 字符
  （`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:131-135`）
  下 `measureText` 量级为个位数毫秒，安全，但字体子系统**首次初始化**成本也落在该窗口（见 §1.5 D6）。
- 引擎对 measurer 异常的映射：任何 `Throwable` → `INVALID_PROGRAM`（"content measurement failed: 类名"）
  （`QuickJsVisualExecutionEngine.kt:339-348`）。环境性失败会被误归"程序错"——处置见 §5 D4。

### 1.2 StaticLayout.Builder 的官方口径 [引证]

来源：developer.android.com `StaticLayout.Builder` reference（本轮全文抓取）。

- `Builder.obtain(CharSequence source, int start, int end, TextPaint paint, int width)`；
  reference 页**没有任何线程限制表述**（无 @UiThread/主线程/线程安全字样）——官方未把
  `StaticLayout`/`Builder` 绑定到主线程。注意 `obtain` 收 `TextPaint`（`Paint` 子类）。
- **Builder 不可复用**：`build()` KDoc 原文 "Note: the builder object must not be reused in any way
  after calling this method. Setting parameters after calling this method, or calling it a second
  time on the same builder object, will likely lead to unexpected results." → 每次布局
  `obtain` 新 builder，不复用（这条是显式约束，不是静默区）。
- 相关默认值（精度口径相关）：`setBreakStrategy` 默认 `BREAK_STRATEGY_SIMPLE`；
  `setIncludePad` 默认 `true`；`setUseLineSpacingFromFallbacks` **默认 `false`，官方"strongly
  recommended"置 `true`，且当文本可能含缅甸文/藏文等回退字体语言时 required**——中文混排
  （回退字体行高更大）也应置 `true`，否则多行高度偏小。
- API 35 新增 `setUseBoundsForWidth`（按边界盒而非 advance 取宽）与 `setMinimumFontMetrics`
  （按 locale 取最小 font metrics）——本批不启用（MVA 无定宽换行），登记为未来精度开关。

### 1.3 "文本度量可在后台线程"的官方正面证据 [引证]

androidx `PrecomputedTextCompat` 源码（androidx-main，Apache-2.0，本轮抓取
`androidx/core/text/PrecomputedTextCompat.java`）：

- `create()` KDoc：*"This can be expensive, so computing this on a background thread before your
  text will be presented can save work on the UI thread."*
- `getTextFuture()` KDoc：*"PrecomputedText is suited to compute on a background thread … the app's
  layout work is largely done on a background thread"*——并接受调用方传入任意 `Executor`；
  库内部兜底 `sExecutor = Executors.newFixedThreadPool(1)`。
- 实现即 `StaticLayout`/`PrecomputedText`（同一套测量管线）在任意后台线程执行。
  `@UiThread` 只标注在"读取 TextView 参数并发起 future"的调用侧，计算本身在后台。

**结论 [引证+推断]**：`Paint` 测量/`StaticLayout` 构建不要求主线程；官方自己的推荐路径就是后台线程。
真正有线程约束的是 **Canvas 绘制**与 View 层 API——服务进程只度量不绘制，不触该面。

### 1.4 Paint.measureText 的线程安全口径

- `Paint` reference（本轮全文抓取）原文仅有 "Return the width of the text."（`measureText`），
  类级描述 "The Paint class holds the style and color information about how to draw geometries,
  text and bitmaps."——**官方文档对 Paint 无任何线程安全声明**（既没说安全也没说不安全）[引证]。
- 处置（保守口径）：把 `Paint` 当**非线程安全**对待——文档静默 ≠ 保证；并发共享一个实例
  （尤其一边 measure 一边改 textSize/typeface）无任何官方背书。
- 本仓落法：度量器实例与 `visual-js-executor` 单线程绑定（构造时创建、仅该线程使用），
  天然线程独占，静默区不再构成风险。**不引入**跨线程共享 Paint 或 Paint 池——新增即需
  指认其消灭的失败，这里没有（§5 D5）。

### 1.5 中文/数学混排的测量精度口径 [引证+设计]

1. **回退字体与行高**：`Paint.ascent()/descent()` 文档明示 *"these are the values for the main
   typeface, and actual text rendered may need a larger set of values because fallback fonts may
   get used in rendering the text."*——中英混排（中文走 Noto CJK 回退）用主字体 metrics 会**低估**
   行高。API 33+ 应使用 `Paint.getFontMetricsInt(CharSequence text, int start, int count, …)`：
   文档明示多字体渲染时返回"足以绘制所有字体"的 ascent/descent。API 33 以下（项目 minSdk 面内）
   无文本感知变体（项目 minSdk=23，`app/build.gradle.kts:21`）——登记为精度边界：混排行高
   可能低估，缓解是加安全余量或断言设备 API≥33（由 T2 按实际 minSdk 裁定，本批不预设）。
2. **locale 与 CJK 变体**：`Paint.setTextLocales` 文档明示中/日共用大量码位但字形不同，
   locale 决定渲染选字——度量 Paint 的 locale 必须与重放绘制侧**同一配置**（进环境快照，§5）。
3. **advance 与边界盒**：`measureText` 返回 advance 宽度（排版用）；`getTextBounds` 返回墨迹
   边界（文档并注明其忽略样式）。端口 `VisualMeasuredContent.widthDu` 采用 **advance 口径**；
   契约 §3.2 的 `inkBounds` 随批次二类型化布局落地（端口 KDoc 原文，`VisualExecutionEngine.kt:196-198`）。
4. **亚像素**：`Paint.SUBPIXEL_TEXT_FLAG` 开启时 advance 按亚像素精度计算；度量与重放两侧
   flag 配置必须一致（进环境快照），否则同文本两次测量可差亚像素级。本批两侧同一 Paint 配置常量。
5. **数学混排在 TEXT 里**：文本载荷中的行内数学记号（如 `x=1`）就是普通字形 run，
   `measureText` 走同一 shaping/回退路径，无需特判。
6. **math 载荷内的中文（`\text{若}`）**：`MathBoxBuilder.buildAtomBox/buildTextBox` 按
   `symbol.length * metrics.charWidth`（charWidth = 0.5×字号，`core/model/.../MathBudget.kt:217-243`、
   `:369-387`）估宽——CJK 全宽字符被**低估约一半**。这是既有 UI 渲染链的同一估算（自洽但非字体精确）；
   契约已处方"MathBox 的字符宽估算……必须补为真实字形度量"（`docs/current/visual-generation-contract.md`
   §5 末段）。**修正必须落在共享 lowering（MathBoxBuilder 或未来 core:visual），不得在服务侧另起一套**——
   否则度量与重放各一套数字（契约"不得继续增加第三套公式/坐标绘制逻辑"）。本批不做该修正，
   登记为已知边界 + 未来工作。
7. **换行口径（MVA）**：MVA 文本**只按显式 `\n` 分段，无自动换行**（自动换行需要 entry 协议
   携带可用宽度，属契约变更，见 §5 D3）。内蕴宽 = 各段 advance 最大值；高 = 段数 × 行高
   （文本感知 fontMetrics）；baseline = 首行 baseline（自盒顶，与 `Layout.getLineBaseline`/MathBox
   的"自顶偏移"约定一致）。单段超出画布预算（4096 du）时由原生越界检查给诊断拒绝
   （契约 §5："JS 根据真实 metrics 布局；原生检查越界并给诊断"）。

---

## 2. math 形态：两案对比

背景事实（读码）[引证]：

- 既有受限 LaTeX 链 = `Tokenizer → Parser → AST → Budget → BoxLayout`（`core/model`）+ Compose 绘制
  （`core/ui/MathBoxComposeRenderer.kt:26-43` 自述 "the production wrapper around
  `Tokenizer -> Parser -> AST -> Budget -> BoxLayout -> Compose draw`"；预算超限/解析失败降级
  `fallbackText ?: formula` 原文透传）。
- **解析+盒布局在 core:model，纯 Kotlin 无 Android 依赖**（`MathAst.kt`、`MathBudget.kt` 零
  `android.` import；`MathMetrics.of(fontSize)`/`MathBoxBuilder.buildBox` 纯函数无共享状态）。
- 模块可用性：`core/data/build.gradle.kts:169`（`implementation(project(":core:model"))`）、
  `app/build.gradle.kts:157`——**:visual_js 服务进程 classpath 上已有 core:model**，无需新依赖、
  无需动 core/ui（热区）。
- 契约处方 [引证]：§3.2 第 2 步"原生验证并度量 text/math"；§5 末段"现有 `MathBudget.kt` 的
  parser/AST/budget 可复用……第一版支持范围须由公式金样限定，未知 TeX 回可读文本，不宣称完整
  LaTeX。不得继续增加第三套公式/坐标绘制逻辑。"

### 案A：复用 core:model 纯 Kotlin 盒布局链（推荐）

服务进程内直接调 `parseAndBuildBox(payload)` → 成功则 `MathBoxBuilder.buildBox(node, MathMetrics.of(px))`
取盒的 width/height/baseline 作为度量结果；失败（`error != null` 或超预算）→ **按契约降级为可读文本**：
用 text 路径度量**原文 payload**，摘要标注 degraded（与 UI 侧 `fallbackText ?: formula` 同语义，
即契约"未知 TeX 回可读文本"）。

- 消灭的失败（新增指认）：
  1. "任何含公式的图都不可用"——现 `UnwiredVisualContentMeasurer` 对非空批次一律拒绝
     （`VisualJsProtocol.kt:398-407`），案A 让 math 度量真实可用；
  2. "服务侧重写一份 parser 与 UI 渲染不一致"——同一 core:model 链，无双实现漂移；
  3. "度量与重放各说各话"——未来 painter 重放数学必须消费同一 `MathBox` lowering
     （契约禁止第三套逻辑）。
- 诚实口径：盒宽是**布局盒估算**（0.5em/字符），非字形级精确；但度量与（未来的）盒式绘制
  同 lowering，自洽。与契约"必须补为真实字形度量"的关系：该升级是共享 lowering 的既定后续工作，
  不在本批 MVA 范围，且**不能只在度量侧做**（否则与绘制侧立刻不一致）。
- 成本：低。无新依赖、不触热区、JVM 可测（§6）。

### 案B：如实降级（估宽/占位）

math 条目不解析，按字符数×估宽（或固定占位框）给宽高，诊断注明"估算"。

- 与案A 相比消灭的失败：只有"不想接 math 链"的实施成本，**没有指认出任何案A 消灭不了的具体失败**。
- 引入的失败：若重放按真实内容绘制而度量是占位 → 场景与度量不一致，错误排版伪装成"正确图"
  （正是契约 §3.2/§4 反对的形态、也是 `UnwiredVisualContentMeasurer` KDoc 自述其存在要消灭的失败，
  `VisualJsProtocol.kt:388-397`）；若重放也画占位 → 公式在图里永远不真实，4E.2 数学图质量不成立，
  且与契约"原生验证并度量""未知 TeX **回可读文本**（而非占位）"直接抵触。

**裁定建议：案A。** 案B 仅保留为案A 内部的降级路径（解析/预算失败 → 可读文本度量），
不作为 math 的独立形态。

---

## 3. 与 sandbox 的边界：度量在服务 Kotlin 层还是 JS 内

**推荐：服务进程 Kotlin 层（宿主侧原生度量），JS 只消费冻结的 metrics。** 依据：

1. **契约 §3.2 是处方** [引证]：事务第 2 步原文"**原生**验证并度量 text/math，保留不可变布局
   artifact，返回宽、高、baseline、inkBounds、实际字号与内容摘要"；第 3 步 render "必须只引用本事务
   contentId。拒绝新内容、**暗改字体、宽度**或无限追加测量"。度量在 JS 内做（注入字体信息表）
   意味着程序自己计算并回填宽度——"暗改宽度"检查失去对象，冻结语义被程序侧计算绕开。
2. **纯计算边界的正确切分**：SceneV1 编译器在 JS sandbox 内做的是对 RenderScene JSON 的纯计算
   （输入/输出都是宿主给的确定数据）；字体度量不是对场景数据的纯计算，而是**宿主环境提供的
   测量 artifact**——fonts 属于宿主环境（同 §3.2 把 `timeMs`/`seed` 作为宿主注入的封闭源）。
   JS 内度量需要把字体 advance 表灌进沙箱：数据膨胀、且成为第二份必须与原生渲染一致的字形
   数据（漂移面）。
3. **双实现漂移**：JS 内复刻 CJK/Latin 混排 shaping 与回退不可行（等价移植 shaping 引擎）；
   任何 JS 侧近似都必然与原生重放绘制不一致，破坏"同快照屏幕/PDF/SVG 一致"门（契约 §4 末段门清单）。
4. **成本**：度量在 Kotlin 层是一次 `measureText` 调用面（§1.5 口径），预算受 128 条/6000 字符
   约束；JS 内方案额外要求协议扩展（注入字体表）、沙箱白名单扩展与对账逻辑——新增面巨大，
   消灭的失败为零。
5. **深冻结衔接（T3）**：input 与 metrics 深冻结（§3.2"每次调用的 input 与 metrics 深冻结"）
   的前提是 metrics 是**引擎外部注入的不可变数据**——服务 Kotlin 层度量产出不可变
   `VisualMeasuredContent` 列表，恰好是该形状；JS 内度量产出的是程序可再计算量，冻结无从谈起。

---

## 4. UnwiredVisualContentMeasurer 替换的最小接口设计

设计原则：端口类型（`VisualContentMeasurer`/`VisualContentMeasureBatch`/`VisualMeasuredContent`，
`core/domain/.../VisualExecutionEngine.kt:149-215`）**零改动**（端口冻结；`inkBounds` 按端口 KDoc
随批次二落地）；新文件只落 `app/src/main/kotlin/.../visualjs`（本批落点）。断行输出在**内部
seam**上，不进端口。

### 4.1 环境快照

```kotlin
/** 度量环境快照：参与 digest 与"布局环境完全一致"缓存判定（契约 §3.2）。 */
internal data class VisualMeasureEnvironment(
    val density: Float,      // resources.displayMetrics.density（假设 A1，见 §5）
    val fontScale: Float,    // resources.configuration.fontScale
    val typefaceId: String,  // 度量字体的稳定标识（本批仅 "system-default"）
    val localeTag: String,   // Paint.textLocales.toLanguageTags()
    val sizeSp: Float,       // 规范字号（契约 §4：sizeSp 限 12..48）
)
```

单位换算（单一规则，度量与未来重放共用）：`px = sp × density × fontScale`；
`du = px / density`（契约 §5："1 du=1 dp……字号使用允许字体的 sizeSp，原生解析成 du；
系统 fontScale 另计"）。故 `du per sp = fontScale`。

### 4.2 内部度量 seam（输出含断行）

```kotlin
/** 一行显式段（\n 切分后的段）：字符区间与该段 advance 宽（du）。 */
internal data class VisualMeasuredLine(val start: Int, val endExclusive: Int, val widthDu: Float)

/** 内部完整布局 artifact：端口五字段之上的行结构，供未来 painter/lowering 复用（同一 seam、同一数字）。 */
internal sealed interface VisualMeasuredLayout {
    val kind: VisualContentKind
    val widthDu: Float; val heightDu: Float; val baselineDu: Float   // baseline 自盒顶
    val lines: List<VisualMeasuredLine>?  // text = 显式段列表；math = null（盒树自带布局）
    val degraded: Boolean                 // math 解析/预算失败降级为可读文本时 true
}
```

- `measureTextIntrinsic(payload, env, paint): VisualMeasuredLayout`——按 `\n` 分段（段尾单个
  `\r` 去除，确定性归一），每段 `Paint.measureText`；行高用文本感知 `getFontMetricsInt`
  （API 33+，§1.5-1）；宽 = max 段宽；高 = 段数 × 行高；baseline = 首行自顶偏移。
- `measureMathBox(payload, env): VisualMeasuredLayout?`——`parseAndBuildBox` +
  `MathBoxBuilder.buildBox(node, MathMetrics.of(px))`；`null` = 解析/预算失败（调用方走降级）。
  该函数纯 Kotlin，JVM 可测。
- **断行的归属**：`render(input, metrics)` 收到的 metrics JSON 仍是端口五字段
  （`QuickJsVisualExecutionEngine.kt:571-582` metricsJson 现状）——JS 放置文本块只需宽/高/baseline；
  行结构属于"不可变布局 artifact"，随 painter/PDF 回放批次经同一 seam 重导出
  （契约 §5 持久化段："冻结的行范围与 run 位置"），本批只保证 seam 可复现。

### 4.3 端口实现

```kotlin
internal class AndroidVisualContentMeasurer(
    private val environment: VisualMeasureEnvironment,
    private val paint: Paint,   // 线程独占：仅 visual-js-executor 线程使用（§1.4）
) : VisualContentMeasurer {
    override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult
}
```

流程（每条目）：
1. `TEXT` → `measureTextIntrinsic`；
2. `MATH` → `measureMathBox`；null → 降级 `measureTextIntrinsic(原 payload)` + `degraded=true`
   （案A 降级路径，§2）；
3. `contentId = "c" + index + "-" + digest.take(8)`（确定性：同批次位置+内容+环境可复现，程序只回显）；
4. `digest = figureSha256Hex(kind + payload + environment.fingerprint())`（core:model 已有
   `figureSha256Hex`；环境参与摘要 = 契约"缓存只在内容摘要和布局环境完全一致时命中"）；
5. `fontSizeSp = environment.sizeSp`（"实际字号"= 实际使用的规范字号）；
6. 映射端口 `VisualMeasuredContent(widthDu, heightDu, baselineDu, fontSizeSp, digest)`；
   空批次返回空结果（既有合法事务，`UnwiredVisualContentMeasurer` 行为保持）。

装配：`VisualJsService.runRequest` 处 `QuickJsVisualExecutionEngine(measurer = UnwiredVisualContentMeasurer)`
改为服务级单例 `AndroidVisualContentMeasurer`（`VisualJsService.kt:382`）。环境快照在
`onCreate`/warm-up 时从 `resources` 构造一次（同配置生命周期内不变；fontScale 变化触发系统配置
更新 → 服务重建，天然重算）。失败映射（D4）：measurer 抛专用
`VisualMeasurementEnvironmentException`（字体/资源不可用）→ 引擎现有 catch 归 `INVALID_PROGRAM`
并带类名诊断；缓解：warm-up 时**先探针一次**（构造 Paint + 度量一个固定字符串），
环境性失败在 warm-up 阶段暴露（走既有 warm-up 失败 → killSelf 路径，`VisualJsService.kt:127-138`），
不留到请求期误归程序错。 Warm-up 探针（D6）**消灭的失败**：首个含 text 的请求把字体子系统
初始化成本计入 1500 ms 期限（KD-76 同族"装载成本落预算内误杀"形状）。

### 4.4 明确不做（新增必须指认失败——本批无）

不引入 Paint 池/跨线程共享（§1.4 无失败可消灭）；不实现自动换行（D3）；不做 inkBounds
（端口 KDoc 明示随批次二）；不改端口类型/拒绝枚举；不动 core/ui、core/markdown、core/export。

---

## 5. 决策点与假设登记

- **D1 度量位置**：服务 Kotlin 层（§3 推荐）。备选"JS 内注入字体"被契约处方与冻结语义否决。
- **D2 math 形态**：案A 复用 core:model 链 + 契约降级；案B 全量占位否决（§2）。
- **D3 换行口径**：MVA 内蕴度量（显式 `\n` only），无自动换行——这是**能力边界**，
  能力上下文不广告 wrap；定宽换行需 entry 协议加宽度字段（`VisualContentMeasureEntry` 目前
  仅 `{kind, payload}`，`VisualExecutionEngine.kt:172-179`），属契约变更，本批不做。
- **D4 环境失败映射**：暂走引擎既有 catch → `INVALID_PROGRAM`（诊断含异常类名可观测）；
  缓解靠 warm-up 探针（D6）。若设备轨显示仍需独立归因，再议端口枚举扩展（契约变更，本批不动）。
- **D5 Paint 线程纪律**：线程独占，不共享（官方文档静默 → 按非安全对待；服务单线程使问题不成立）。
- **D6 warm-up 探针**：扩展 `VisualJsService.onCreate` warm-up（或 `warmUpNativeRuntime`）加一次
  度量探针；属本批落点（app/visualjs、core:data visual），随 T2 实施。
- **假设 A1 [待验证]**：隔离进程内 `resources`（density/fontScale/locale）可用且与宿主一致、
  系统 typeface 可解析。官方 `android:isolatedProcess` 文档只说"isolated from the rest of the
  system and has no permissions of its own; Communication with it is only through the Service API"，
  **未提及资源/字体**；社区答案仅涉私有数据目录且为单一来源 [待验证]。
  处置：measurer 对环境获取失败 fail-closed（有界拒绝，绝不伪造度量）+ 设备验证项 D-1：
  扩仪器化隔离套件一条"AIDL 往返内 text 度量成功返回"用例（现套件 13 例 @Test
  `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`，
  计划基线"隔离门 13+2 全绿"已证明原生库/往返/warm-up 可用，字体面是增量）。
- **假设 A2 [待验证]**：同环境下服务进程与宿主进程对同 payload 度量结果逐位一致（未来 painter
  复用 seam 的前提）。由"同快照屏幕/PDF/SVG 一致"设备门判定，本批只保证 seam 单点。
- **精度边界 P1**：API<33 无文本感知 fontMetrics → 混排行高可能低估（§1.5-1）。
- **精度边界 P2**：math 盒宽 0.5em/字符估算，CJK 低估；真实字形度量为共享 lowering 后续工作（§1.5-6）。

## 6. 测试判据（T2 实施的红绿标准；本批未运行）

JVM 单测（app/src/test 或 core:model 既有测试扩展）：
1. math 金样：受支持公式集 → 期望盒尺寸（契约"公式金样限定支持范围"）；未知 TeX →
   `measureMathBox` 返回 null（降级触发）。
2. 降级组合：math 失败条目 → 文本路径度量原文 + `degraded=true`；digest 含 degraded 语义
   （同 payload 不同降级态摘要不同）。
3. contentId/摘要确定性：同 (index, payload, env) → 同 contentId/digest；env 变 → digest 变。
4. 单位换算：sp→du = ×fontScale 口径的纯函数断言。
5. 批预算 128/6000 由端口类型强制（既有 `VisualContentMeasureBatch` init，回归不放宽）。

仪器化（设备轨，含隔离进程）：
6. 隔离往返内度量：AIDL execute 一个 prepare 返回 text 条目的程序 → Rendered 且 contentIds
   非空（同时验证假设 A1；失败形态 = 有界拒绝，不伪造）。
7. 文本度量关系断言（不依赖绝对字体值）：CJK 段 advance > 同字符数 ASCII 段；
   `fontScale` 1/1.3/2 下 heightDu 随 fontScale 线性缩放（门清单 fontScale 矩阵的最小子集）。
8. 多段 `\n`：lines 数 = 段数；宽 = max 段宽。
9. 契约回归：引擎 + 真实 measurer 下 `VisualExecutionEngineContractTest` 既有断言不放宽
   （每事务恰一次 measure、空批次 0 调用——`StubMeasurer.calls` 机制，
   `core/domain/src/test/.../VisualExecutionEngineContractTest.kt:76-83,266-278`）。

## 7. 来源

本仓（静态读码，路径:行）：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:79-81,111-138,377-404,382`；
`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsProtocol.kt:388-410`；
`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:339-348,571-582`；
`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:131-135,149-215`；
`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MathBudget.kt:217-243,350-387`；
`core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/MathBoxComposeRenderer.kt:26-43`；
`core/data/build.gradle.kts:169`；`app/build.gradle.kts:157`；
`docs/current/visual-generation-contract.md` §3.2/§4/§5；`.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md`。

官方文档与源码（本轮实际抓取）：
- StaticLayout.Builder reference（builder 不可复用、无线程限制、setUseLineSpacingFromFallbacks/默认断行策略）：
  <https://developer.android.com/reference/android/text/StaticLayout.Builder>
- Paint reference（measureText/ascent/descent 回退字体注记、getFontMetricsInt(CharSequence,…) API 33、
  setTextLocales CJK 变体、equalsForTextMeasurement API 28、SUBPIXEL_TEXT_FLAG、getTextBounds 注记）：
  <https://developer.android.com/reference/android/graphics/Paint>
- androidx PrecomputedTextCompat 源码（后台线程度量正面证据、库内 fixedThreadPool）：
  <https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/text/PrecomputedTextCompat.java>
- `android:isolatedProcess` 属性（未提及资源/字体访问）：
  <https://developer.android.com/guide/topics/manifest/service-element>
- 社区参考（单一来源，[待验证]，仅涉私有数据目录非 APK 资产）：
  <https://stackoverflow.com/questions/32020880/>
