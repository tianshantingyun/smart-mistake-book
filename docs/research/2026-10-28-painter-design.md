# painter 设计研究（批次 28 T2 驱动；R1）— DrawOps V1 → Android Canvas 映射

日期：2026-10-28 ｜ 任务：批次 28 计划 T2 的 R1 驱动研究（GitHub 参考评分 / painter 设计 / Rendered→Bitmap 冒烟判据）。
输入：`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`（已完整读）、批次 27 R1 §3-§4（`docs/research/2026-10-27-scenev1-compiler-design.md:95-128,171-182`）、批次 28 基线 §5（`docs/research/2026-10-28-batch-28-baseline.md:102-134`）。
产出性质：**设计**（落码归 T2）。本批未改任何 Kotlin/JS 源码；未运行 Gradle（门禁由脚本串行）；本文档是本任务唯一落盘文件。

---

## 0. 结论摘要

1. **核心回放规则（本次研究的关键发现）**：DrawOps V1 的坐标空间是**二分**的——`path` 携带局部坐标（随 `concat` 回放），而 `clip`（烘焙组合坐标）与 `label`（投影后坐标）携带**根空间**坐标，却发射在组的 `concat` 之后。朴素顺序回放在**嵌套变换组内的 clip** 和**任何变换组内的 label** 两类场景必然二次变换出错。painter 必须自维护矩阵镜像，对 clip/label 走"setMatrix(root) → 应用 → setMatrix(mirror)"舞步（androidsvg 有同型生产先例）。
2. painter 落位 core:data `visual` 包（工单既定），**纯计算层与 android.graphics 应用层分离**（app 模块 JVM 单测无 Robolectric，`VisualContentMeasuring.kt:175-179` seam 先例）——矩阵列序装配/颜色解析/alpha 取整可用 JVM 门钉住。
3. 两个**契约缺口**（两道门都没钉住，painter 不能自愈，须登记裁定）：① stroke-only 路径合法但无颜色来源（`VisualStrokeSpec` 无 color；测试台 `run_tests.mjs:193` 有 stroke-only 夹具）；② dash 未钉偶数长度（`DashPathEffect` 契约要求偶数）。建议在 compiler+validator 两侧各加一行收紧；painter 侧先 fail-closed。
4. 冒烟判据：同设备"同指令 → 同 Bitmap 像素"用 `Bitmap.getPixels` 整阵 `Arrays.equals` 钉死；再配结构探针（alpha 混色可精确算、矩阵列序正例+错误映射负例先红、嵌套 clip 正例、label 双对齐负例、fail-closed 矩阵）。**跨设备/跨 API 字节一致性不主张**（字体/Skia 版本差异，未验证项）。

---

## 1. GitHub 参考实现评分（工单第一项）

评分维度：license（可借鉴性）、维护度（pushed_at/issues，`gh api` 2026-10-28 实取）、与本任务的结构适配度。
采纲：**只借鉴模式与语义先例，不引入任何依赖**（AAR/stdlib 不变纪律）——license 因此只影响借鉴自由度，四个仓库全部允许。

| 仓库 | license | 维护度（gh api 实取 2026-10-28） | 适配度 | 借鉴点 |
|---|---|---|---|---|
| airbnb/lottie-android（35,757★） | Apache-2.0 | pushed 2026-02-15，issues 82，未归档——活跃 | **最高**：DrawOps→Canvas 同构 | ① Paint 常驻字段复用+用前全字段重设（`BaseLayer.java:84-88,313`）；② group alpha 链预乘公式 `(int)((parentAlpha/255f*opacity/100f)*255)`——截断=floor，与 R1 §4 的 `floor(alpha*255)` 同口径（`BaseLayer.java:264`）；③ dash：`float[] dashPatternValues` 复用 + `paint.setPathEffect(new DashPathEffect(dashPatternValues, offset))`（`BaseStrokeContent.java:48,365`）；④ 文本：`save() → concat(parentMatrix) → draw → restore()`（`TextLayer.java:173-184`） |
| BigBadaboom/androidsvg（1,324★，Maven `com.caverock:androidsvg` 的上游） | Apache-2.0 | pushed 2025-06-01——缓慢维护（维护模式多年，仍是 Android SVG→Canvas 事实标准） | **高**：SVG 语义同源（根变换/逐元素 transform/clip/dash） | ① 双 Paint 常驻（fill/stroke 各一），固定 flags 且 `setHinting(HINTING_OFF)` 求稳定（`SVGAndroidRenderer.java` RendererState 构造段）；② 根 viewBox 变换一次 `canvas.concat`（`:704`），逐元素 `canvas.concat(obj.transform)`（`:753,:973,:1146…`）；③ **`canvas.setMatrix(new Matrix()) → 画 → canvas.setMatrix(currentMatrix)` 设备空间回放舞步**（非缩放描边路径，`:596-620` 附近）——本文 §3 clip/label 舞步的直接先例；④ dash 奇数长度按 SVG 规范 `intervals[i] = dashArray[i % n]` wrap 成偶（`:2477-2495`，见 §5-②） |
| software-mansion/react-native-svg（8,007★） | MIT | pushed 2026-10-25——极活跃 | 中高 | 组层绘制：`mLayerPaint` 常驻一次构造（`GroupView.java:35-39`），层 alpha `(int)(mOpacity*255)` 截断（`:175`），save/setMatrix/restore 层舞步（`:110-177`） |
| PhilJay/MPAndroidChart（38,189★） | Apache-2.0 | pushed 2026-10-09 | 中 | Paint 复用惯例（每 renderer 常驻 Paint），无新借鉴点 |

**AOSP 官方语义核证**（aosp-mirror/platform_frameworks_base，2026-10-28 gh 实取原文；developer.android.com 直连超时后换此主源通道）：

| 事实 | 出处 |
|---|---|
| `Canvas.concat(m)` = **preconcat** 当前矩阵（顺序 concat 的嵌套语义成立） | `graphics/java/android/graphics/Canvas.java`，`public void concat` javadoc |
| `Canvas.clipPath(Path)` = 与当前 clip **求交**（内部即 `Region.Op.INTERSECT`；单参重载非 deprecated，`Region.Op` 参重载 API 26 起 deprecated——painter 用单参重载即可） | 同上，`public boolean clipPath(@NonNull Path path)` javadoc |
| `Canvas.setMatrix` **非 deprecated**（`Canvas.java:809`）；`getMatrix` 已 deprecated（`:824-825`）→ painter 自维护矩阵镜像，不用 getMatrix | 同上行号 |
| `drawText` 的 y = **baseline**（"y: The y-coordinate of the baseline of the text being drawn"） | 同上，`drawText(char[],int,int,float,float,Paint)` javadoc |
| Matrix 9 值序 = `[MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, MPERSP_0..2]`（`Matrix.java:36-44` 常量定义）→ 契约 `[a,b,c,d,e,f]` 装载序 = **`[a,c,e,b,d,f]`**（与批次 27 R1 §4 警示一致，此处为 AOSP 原文级独立复核） | `graphics/java/android/graphics/Matrix.java:36-44` |
| `DashPathEffect`："intervals array must contain an **even number of entries (>=2)**"；Java 侧只拦 `length < 2`（奇数长度静默进 native，行为无文档规约） | `graphics/java/android/graphics/DashPathEffect.java` javadoc + 构造体 |

---

## 2. 仓内事实闭包（设计输入，全部实读）

### 2.1 六原语类型与校验器不变量

- 类型面：`core/data/src/main/kotlin/.../visual/VisualDrawOps.kt:96-140`（sealed interface 六原语）、`:83-93`（`VisualMatrix`，KDoc 自带 `[a,c,e,b,d,f]` 警示）、`:55-61`（`VisualStrokeSpec`：width/cap/join/miterLimit/dash，**无 color**）、`:127-136`（`Label`：x,y 为"最终文本原点（左缘 + baseline）"，hAlign/vAlign 为回显元数据）。
- 校验器保证（painter 可依赖的输入契约）：save/restore 严格配平（`VisualDrawOpsValidator.kt:134-156`）；矩阵有限且 `|det| ≥ 1e-12`（`:296-299`）；路径非空且 M 起始、仅 M/L/C/Z（`:450-484`）；颜色 `#RRGGBB(AA)` 正则（`:45`）；alpha ∈ [0,1]（`:343-347,386-390`）；dash ≤32 项且每项 (0, 1e5]（`:424-442`，**未钉偶数**）；schemaVersion == 1（`:80-84`）；未知 op 在校验层拒绝 `DRAWOPS_UNKNOWN_OP`（`:396`）。
- 引擎现状：校验通过后只回传 JSON 文本（`QuickJsVisualExecutionEngine.kt:783-792`）——类型化文档目前只在校验器内部存在；painter 管线须为 **validator(text) → doc → painter**（冒烟夹具同形）。

### 2.2 编译器发射语义（painter 正确性的根据，`visual-compiler.js`）

- 文件头钉定组发射顺序：`save → [clip(烘焙组合坐标)] → [concat(本地矩阵)] → children → restore`（`:27-28`）；实现：clip 烘焙用 `composed = parent*local`（**含本组自身矩阵**，`:820-824,853`），concat 发射 `localMatrix`（`:857`），label 投影用 composed 一次算定（`:918-927`）。
- **坐标空间二分由此成立**：`path` = 局部坐标；`clip`/`label` = 根空间（烘焙/投影均用从根算起的 composed）。
- **朴素回放的两类必错场景**（推导，依据上两行代码）：
  1. 嵌套组：G1(M1){ G2(M2, clip) } —— G2 的 clip 烘焙坐标在 M1×M2 空间，但回放到该 op 时 canvas 矩阵已是 rootT×M1（G1 的 concat 生效中）→ 直接 clipPath 会得 rootT×M1×(M1×M2×clipLocal)，M1 双乘。
  2. 组内 label：Node 测试台钉了投影金样 `x = 107 − 10×0.5 = 102`（`tools/visual_compiler/run_tests.mjs:221-223`）；若在 M1 生效下回放 (102,71)，translate(5,5) 双乘 → (107,76) ≠ 投影金样。
- dash 校验（编译器）与校验器同宽：逐项 (0,1e5]、≤32，**均未钉偶数长度**（`visual-compiler.js:472-483`）。
- stroke spec 无 color 键，且 `checkKeys` 白名单**拒绝** stroke 带色（`:454`）；stroke-only 路径被两道门接受且测试台有夹具（`run_tests.mjs:193`）→ §5-① 缺口。

### 2.3 度量/单位链（label 落位的根据，`app/.../visualjs/VisualContentMeasuring.kt`）

- 单位换算单一规则：`px = sp × density × fontScale`；`du = px / density`（`:112-118`）。度量结果全为 du（`:257-263`），metrics 五字段 `{contentId,widthDu,heightDu,baselineDu,fontSizeSp,digest}`。
- 度量 Paint 构造：`Paint()` 默认 flags + 仅设 textSize（`PaintVisualTextMeasureBackend.forEnvironment`，`:409-420`），KDoc 明文"度量与（未来的）重放必须用同一常量配置"（`:409-414`）→ **重放 Paint parity 规则**（§3-5）。
- 断行 artifact 走内部 seam 且"未来 painter 复用同一 seam"（`:33-36,199-227`）：`VisualMeasuredLayout.lines` 为显式段列表（`\n` 切分、段尾 `\r` 归一，`:353-376`）。painter 不应重推分段规则，应由内容 DTO 携带（§3-5）。
- JVM 单测无 Robolectric：`VisualTextMeasureBackend` seam 的存在理由即此（`:171-179`）→ painter 分层同样理由。
- math 盒：`core/model/.../MathBudget.kt:262-345` `MathBox` sealed 纯布局数据（width/height/baseline + Leaf.content），core:data 已依赖 core:model（`core/data/build.gradle.kts:169`）——盒树 Canvas 走查可行，本批只设计（§3-6）。
- 契约钉子：`1 du = 1 dp`、`根变换只做一次 du→px`（`docs/current/visual-generation-contract.md:100`）；`AndroidScenePainter 共用于 Compose nativeCanvas 和 PDF page.canvas`（`:102`）；PDF `ptPerDu` 复用 painter（`:192`）；Path/Paint/StaticLayout 只是进程内缓存不序列化（`:121`）。

---

## 3. painter 设计（工单第二项；T2 落码规格）

### 3.1 落位与分层

- 落位：`core/data/src/main/kotlin/.../visual/VisualDrawOpsPainter.kt`（`internal`，消费 typed doc；与 `VisualDrawOps.kt:13-14` 的可见性裁定一致）。不新建 `core:visual` 模块（需改 settings.gradle.kts，热区禁改；契约 `:115` 已登记归位条件）。
- **纯计算与应用层分离**（消灭"JVM 门覆盖不了列序/颜色/alpha"的失败，同 `VisualTextMeasureBackend` seam 理由）：
  - 纯层（JVM 可测，无 android 符号）：`VisualMatrix.toAndroidValues(): FloatArray`（9 值装配，§3.3 工作例）、`parseDrawOpsColor(String): Int`、`drawOpsAlpha255(Double): Int`（floor）、label 内容 DTO。
  - 应用层（androidTest 覆盖）：Canvas/Paint/Path/Matrix 调用。

### 3.2 状态机与 Paint 复用策略（借鉴 lottie/androidsvg/rnsvg）

- **实例持有、线程独占**：painter 为有状态实例（复用缓冲），同一实例只在单线程使用（同 `AndroidVisualContentMeasurer` 线程纪律 KDoc 形状，`VisualContentMeasuring.kt:38-40`）。`draw()` 入口做实例状态自复位。
- **Paint 常驻复用 + 全字段 binder**（lottie `BaseLayer.java:84-88,313` 先例；消灭"跨 op 状态泄漏"的失败——DashPathEffect/xfermode 残留是最常见事故）：
  - `pathPaint`：`ANTI_ALIAS_FLAG`，每次绑定**无条件设置全部**相关字段：style、color、alpha、strokeWidth、cap、join、miterLimit、pathEffect（fill 相位设 null——`DashPathEffect` javadoc 明文 FILL 忽略之，仍显式清空防泄漏）、shader/colorFilter=null。
  - `textPaint`：**严格复制度量 Paint 配置**（`Paint()` 默认 flags + 仅 textSize；§2.3 parity 规则）；AA 关闭是 MVA 视觉取舍，升级须两侧同步 + 进环境指纹。
  - `Path` ×1（`reset()` 重建——不用 `rewind()`，因 fillType 每 op 可变）、`Matrix` ×1、`values[9]` ×1、dash `FloatArray` 按需重建（≤32 项，复用容量）。
- fill 先画、stroke 后画（同一 op 两个相位；与 Canvas/SVG 惯例一致，R1 §4 `:178` 已钉）。

### 3.3 concat → Matrix：列序装配（头号陷阱的正钉）

```kotlin
// 纯层；JVM 测试钉死。契约 x' = a·x + c·y + e; y' = b·x + d·y + f（VisualDrawOps.kt:77）
fun VisualMatrix.toAndroidValues(): FloatArray = floatArrayOf(
    a.toFloat(), c.toFloat(), e.toFloat(),   // [MSCALE_X, MSKEW_X, MTRANS_X]
    b.toFloat(), d.toFloat(), f.toFloat(),   // [MSKEW_Y, MSCALE_Y, MTRANS_Y]
    0f, 0f, 1f,                              // [MPERSP_0, MPERSP_1, MPERSP_2]
)
```
回放：`matrix.setValues(values); canvas.concat(matrix)`（preconcat 语义，AOSP Canvas javadoc）。
**工作例**（进 JVM 断言）：切变 `[a,b,c,d,e,f]=[1,1,0,1,0,0]`（x'=x+y）→ values `[1,0,0, 1,1,0, 0,0,1]`，点 (2,1)→(2,3)。若按原序误装 `[a,b,c,d,e,f]` 进 values[0..5]，同点 → (2,1)——两装法像素必须不同（冒烟负例，§4-判据 2）。

### 3.4 回放规则：状态机 + 根空间舞步

painter 维护**矩阵镜像栈**（`ArrayDeque<Matrix>` 快照 + 当前 `Matrix mirror`；不用已 deprecated 的 `canvas.getMatrix`）：

| op | 回放 | 说明 |
|---|---|---|
| `save` | `mirror 快照入栈; canvas.save()` | 配平由校验器保证 |
| `concat` | `mirror.preConcat(m); canvas.concat(m)` | preconcat = Canvas.concat 原语语义，镜像与 canvas 严格同步 |
| `clip` | `canvas.setMatrix(rootMatrix); canvas.clipPath(path); canvas.setMatrix(mirror)` | **舞步**：clip 坐标是根空间（§2.2）；setMatrix 不动 save 栈 → 与编译器的 save/restore 计数不脱钩；setMatrix 非 deprecated（`Canvas.java:809`）；单参 `clipPath` = INTERSECT 求交（契约"clip 只相交"）；fillType 由 `VisualFillRule` 映射（NONZERO→`Path.FillType.WINDING`，EVEN_ODD→`EVEN_ODD`）。androidsvg 同型先例：`SVGAndroidRenderer.java:598-620`（setMatrix 复位对） |
| `path` | 重建 Path（moveTo/lineTo/cubicTo/close + setFillType）→ 当前矩阵下 fill 相位、stroke 相位各一次 | 局部坐标，随 mirror 生效 |
| `label` | `canvas.save(); canvas.setMatrix(rootMatrix); canvas.translate(x,y); canvas.rotate(rotationDeg); 画内容; canvas.restore()` | (x,y) 已是最终左缘+baseline（§2.2 金样）；rotation 绕 (x,y)；label 不留状态故 save/restore 自包含，不触碰组状态栈 |
| `background` | `canvas.drawColor(argb)`（首步，ops 之前） | srcOver 唯一混合模式（契约 §5） |
| `hitRegions` | **不绘制**（原生点内判定，4E.2 交互段消费；R1 §4 `:181`） | |
| `restore` | `mirror 出栈; canvas.restore()` | 防御：栈空即抛 `PAINTER_BALANCE`（见 §3.7） |

- `rootMatrix` = `scale(pxPerDu)`（唯一根变换，契约 §5"根变换只做一次 du→px"）；viewBox==viewport 由校验器钉定（`VisualDrawOpsValidator.kt:93-99`），无平移项。PDF 目标= `ptPerDu` 替换 pxPerDu 即可复用（契约 `:192`）。
- **为什么舞步必须存在而不是"依赖发射顺序"**：编译器只保证"clip 在同组 concat 之前"，不保证"无祖先 concat 生效"（§2.2 场景 1）。舞步对单层/嵌套统一正确，且不增加分支。

### 3.5 label 文本落位（基线与对齐）

- **对齐不重放**：hAlign/vAlign 是回显元数据（`VisualDrawOps.kt:63,132`），编译器已折进 (x,y)（金样 `run_tests.mjs:221-223`）；painter 若再按对齐偏移 = 双重偏移（冒烟负例，§4-判据 2）。`drawText` y 语义 = baseline（AOSP javadoc）与 (x,y) 定义（"左缘 + baseline"）直接对齐，无需 Paint.textAlign 干预。
- **多行**：内容 DTO 携带冻结 artifact 的显式段（`lines: List<LineRange>`，§2.3 seam 复用——painter 不重推分段规则）；行 i 的 baseline = `y + i × lineHeightDu`，`lineHeightDu = heightDu / 行数`（heightDu=段数×行高，`VisualContentMeasuring.kt:262`；baselineDu=首行自顶偏移，`:263`）。逐行 `drawText` 子串，首行 baseline 恰为 y。
- **textSize**：`textPaint.textSize = (sizeSp × fontScale) × pxPerDu`。推导：度量时 px=sizeSp×density×fontScale、du=px/density ⇒ 文本 du 足印 = (sizeSp×fontScale)；回放目标 px = du×pxPerDu（§2.3）。屏上 pxPerDu=density 时与度量 Paint 逐像素同宽。fontSizeSp 取自 Label 引用的冻结度量（op 无 per-label size，R2 D3）。

### 3.6 内容 DTO 与 math 面

```
sealed interface VisualLabelContent {
  data class Text(payload, sizeSp, fontScale, lines: List<LineRange>)   // 本批实现
  data class Math(box: MathBox, sizeSp, fontScale)                      // 本批 fail-closed（下）
}
```
- `MathBox`（core:model 纯数据，`MathBudget.kt:262-345`）可被 core:data 直接走查（依赖已存在，`core/data/build.gradle.kts:169`），但 glyph/分式线/根号的 Canvas 映射必须与 core/ui 的 Compose lowering 几何逐点一致（契约 §5"不得继续增加第三套公式/坐标绘制逻辑"）——**本批 fail-closed `PAINTER_LABEL_MATH`**，盒走查留 PDF/回放批与 core/ui parity 核对同做。降级 math（degraded=true）按度量契约本就是 text 路径，走 Text 正常渲染。
- 内容缺失（resolver 返回 null）→ fail-closed `PAINTER_UNKNOWN_LABEL`：DrawOps 校验只钉"contentId 在冻结集"（`VisualDrawOpsValidator.kt:357-363`），"绘制时内容可达"只有 painter 能看见。

### 3.7 异常面（fail-closed：未知指令/非法输入 = 抛有码异常，无部分输出）

- **未知指令在 Kotlin 层结构性不可表达**：消费 typed doc，sealed `VisualDrawOp` 的 `when` 穷尽受编译器强制；未知 op 的运行时面只剩 schema 门——`drawSchemaVersion != 1` → 抛 `PAINTER_SCHEMA_VERSION`（消灭"旧文档喂新 painter"的静默错画；校验器同值 `:80-84`）。
- 其余 painter 自守门（每个都指认具体失败）：

| 码 | 触发 | 消灭的失败 |
|---|---|---|
| `PAINTER_BALANCE` | restore 时镜像栈空 | 调用方绕过校验器手工构造未配平文档 → `Canvas.restore()` 静默 no-op 造成状态腐蚀（"painter 不自愈" `VisualDrawOps.kt:139` 的守门形态：不修，只拒） |
| `PAINTER_UNKNOWN_LABEL` | contentId 无内容 | 内容可达性与冻结引用是两道不同时刻的门（§3.6） |
| `PAINTER_STROKE_WITHOUT_FILL` | fill==null && stroke!=null | stroke 无颜色来源（§5-①），宁可拒不可默认色 |
| `PAINTER_DASH_ODD` | dash 长度为奇数 | `DashPathEffect` 契约要求偶数（AOSP javadoc），奇数进 native 行为无规约（§5-②） |
| `PAINTER_LABEL_MATH` | Math 内容 | 本批未实现盒走查（§3.6） |
| `PAINTER_BOUNDS` | Double→Float 溢出（|v| > Float.MAX 可表达域） | 校验器界限是 Double ±1e5，Float 化安全；此门只防未来界限松动，一行成本 |

- 诊断面与引擎对齐：异常消息 ≤512 字符带 op 索引上下文（同 `MAX_DIAGNOSTICS_CHARS` 口径）；painter 本批未接引擎，冒烟直接断言异常类型+码。

### 3.8 新增件 → 消灭的失败（12.2 自检表）

| 新增 | 消灭的具体失败 |
|---|---|
| 矩阵镜像 + setMatrix 舞步 | 嵌套变换组 clip 双乘 / 组内 label 双乘（§2.2 两类推导场景）——朴素回放**必错**，不是风格偏好 |
| 全字段 Paint binder | 跨 op DashPathEffect/xfermode/style 泄漏错画 |
| 纯计算层分离 | JVM 门（无 Robolectric）覆盖不了 `[a,c,e,b,d,f]`/颜色/alpha 三个错位陷阱 |
| 内容 DTO 携带 lines | painter 重推分段规则与度量 seam 漂移（同 payload 不同断行 → 对齐错位） |
| PAINTER_* 码族 | 六类静默错画/静默 no-op（§3.7 表逐条） |
| textPaint parity 规则 | 度量宽与回放宽漂移 → 编译器折算的 (x,y) 对齐失效（对齐金样基于度量 widthDu） |

未新增：Paint 池（单线程纪律使共享不成立，`VisualContentMeasuring.kt:38-40` 同裁定）、缓存层、hitRegions 绘制、SVG writer、PDF 回放、label 避让——各自无已发生的失败可指认（R1 §3.3 同款逆向裁剪）。

---

## 4. Rendered→Bitmap 冒烟判据（工单第三项；T2 测试规格）

### 4.1 判据 1：同设备确定性（"同指令同像素"的硬门）

- 条件：`Bitmap.Config.ARGB_8888` + `Canvas(bitmap)`（软件位图，无硬件加速变量）、固定 `pxPerDu`（建议 2f）、固定小尺寸（建议 96×64，控设备轨时长）、新 Bitmap 各自渲染两遍。
- 断言：`Bitmap.getPixels(IntArray)` 整阵 `Arrays.equals` **完全相等**——不是"接近"。根据：painter 无时钟/无随机/无迭代收敛（编译期已把弧拆成 C 段、label 一次算定，R1 §3.2），软件 Skia 对同输入逐 op 光栅化确定；op 顺序即回放顺序（类型面 List 序）。
- 复跑形态：同一测试方法内两遍 + 跨两个测试方法各一遍（防单测初始化顺序耦合）。

### 4.2 判据 2：结构探针（不做 golden image；每条正例配一条"错实现必红"的负例）

| 探针 | 正例断言 | 负例（先红钉子） |
|---|---|---|
| 矩阵列序 | 切变矩阵填充方块：平行四边形内中心点=填充色（与背景精确混色可预计算） | 用 `[a,b,c,d,e,f]` 原序误装渲染同文档 → 像素阵**必须不同**（§3.3 工作例） |
| 嵌套 clip | G1(M1){G2(M2,clip){path}}：正确区域有墨、被裁区无墨（单层 clip 由测试台金样覆盖不了嵌套） | 朴素"当前矩阵下 clipPath"实现 → 本用例红 |
| alpha 取整 | 不透明背景上 alpha=0.5 纯色矩形**中心**（无 AA 边缘）像素 == 手算 `floor(0.5*255)` 预乘混色值（floor 规则进断言） | 用 round 实现者红 |
| 背景与角点 | background 设色 → 四角像素==背景色；null 背景 → 角点为透明 | — |
| label 落位 | 单行 label：metric 带内有墨（`widthDu×pxPerDu` 量级）；**双对齐负例**：若 painter 重放 hAlign 偏移，墨迹中心位移 ≈ widthDu/2×pxPerDu → 断言"带内左缘起墨"即红于错实现 | 组内 label（concat 生效下）按根空间落位；朴素回放者墨迹位移 = M1 平移量 → 红 |
| 状态隔离 | restore 后下一 op 在根空间按预期落墨 | — |
| stroke 相位 | fill+stroke 同 op：stroke 宽度带在 fill 之上（中心线颜色=stroke 色） | — |

- 夹具构造走生产管线：JSON → `VisualDrawOpsValidator.validate` → doc → painter（`VisualDrawOps.kt` typed 模型 hand-built 仅用于 PAINTER_BALANCE 这类"校验器会拒、painter 仍须守"的负例）。

### 4.3 判据 3：fail-closed 矩阵（§3.7 六码逐条：断言异常类型 + 码 + 消息含 op 索引）

### 4.4 判据边界（不主张的，明写）

- **跨设备/跨 API 字节一致：不主张**（系统字体、Skia 版本差异；UNVERIFIED，不进断言）。设备矩阵沿用批次设备轨（ps16k AVD，API 36）单设备钉确定性。
- 屏 vs PDF（ptPerDu）跨目标一致：留契约批，用同判据换 pxPerDu 复测。
- label 墨迹带探针是量级断言而非字形级精确——字体级保真按契约本就"不承诺跨机字体一致"（契约 `:192`）。

---

## 5. 缺口与裁定建议（两道门未钉、painter 不能自愈；建议 T3 进契约/known-defects）

1. **stroke 颜色缺口**：stroke-only 路径合法（validator `:340-342`、测试台夹具 `run_tests.mjs:193`）但 `VisualStrokeSpec`/scene grammar 无 color（compiler `:454` 白名单拒绝 stroke 带色）。建议：**V1.1 给 stroke spec 加 color 字段**（scene→compiler echo→validator→`VisualStrokeSpec` 四处同改，词表两侧同改纪律）；备选（改动最小）：钉"fill 必填（可 `#00000000` 透明）、stroke 色取 fill 色"。过渡期 painter 按 §3.7 fail-closed + KD 登记。
2. **dash 奇数长度缺口**：两道门都只钉逐项值域（compiler `:472-483`、validator `:424-442`）。建议两侧各加一行"长度为偶"（对齐 `DashPathEffect` javadoc 契约）；androidsvg 的 `i%n` wrap（`SVGAndroidRenderer.java:2483`）是 SVG 规范语义备选，但静默改周期，不如源头拒绝。过渡期 painter fail-closed `PAINTER_DASH_ODD`。
3. **measure/replay Paint parity**：flags 不在环境指纹（`VisualContentMeasuring.kt:150-152`）——度量 Paint 改 flags 不会被 digest 捕获。建议：replay textPaint 配置与 `PaintVisualTextMeasureBackend.forEnvironment` 以跨侧测试或共享常量钉死（本批 painter 侧按 §2.3 严格复制）。
4. **typed doc 的产出位置**：引擎现回传 JSON 文本（`QuickJsVisualExecutionEngine.kt:783-792`）；painter 管线钉为 validator→doc→painter，painter 入口不收裸文本（消灭"未验 JSON 交 painter"——校验器 KDoc `:22` 明令）。

## 6. 未验证项（UNVERIFIED，不作为事实引用）

- 嵌套 clip / 组内 label 的朴素回放必错，是**编译器源码+测试台金样的推导**，未在设备渲染对比实测（冒烟用例落地后自动转为实测钉子）。
- Skia 对奇数 dash 的 native 实际行为：官方 javadoc 只规定 even，未做设备实验——因此按"无规约"处置（fail-closed），不猜测行为。
- textPaint 严格 parity（默认 flags，AA 关）的视觉质量未在真机评估（MVA 保真不做，属既定裁剪）。
- developer.android.com 直连超时；AOSP 镜像原文替代核证（同源框架代码，属官方主源）。

## 7. 来源

**GitHub（gh api 实取 2026-10-28）**
- lottie-android：https://github.com/airbnb/lottie-android （`BaseLayer.java`、`BaseStrokeContent.java`、`TextLayer.java`）
- androidsvg：https://github.com/BigBadaboom/androidsvg （`androidsvg/src/main/java/com/caverock/androidsvg/utils/SVGAndroidRenderer.java`）
- react-native-svg：https://github.com/software-mansion/react-native-svg （`android/src/main/java/com/horcrux/svg/GroupView.java`）
- MPAndroidChart：https://github.com/PhilJay/MPAndroidChart
- AOSP：https://github.com/aosp-mirror/platform_frameworks_base （`graphics/java/android/graphics/{Canvas,Matrix,DashPathEffect}.java`）

**仓内（本会话实读，行号见正文）**
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOps.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidator.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt`
- `core/data/src/main/resources/visual/visual-compiler.js`
- `tools/visual_compiler/run_tests.mjs`
- `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt`
- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/MathBudget.kt`
- `core/data/build.gradle.kts`
- `docs/research/2026-10-27-scenev1-compiler-design.md`（R1 §3-§4）
- `docs/research/2026-10-28-batch-28-baseline.md`（§5）
- `docs/current/visual-generation-contract.md`（§5/§8）
- `.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md`
