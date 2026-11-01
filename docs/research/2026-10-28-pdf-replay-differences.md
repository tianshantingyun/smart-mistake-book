# PDF 回放差异研究（批次 29 R1；驱动 T3/T4）— 同一 DrawOps 在屏与 `PdfDocument` 上的语义差

日期：2026-10-28 ｜ 任务：批次 29 计划 T3/T4 的 R1 驱动研究（`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:23`）。
输入（本会话逐字读过）：计划全文；`docs/current/visual-generation-contract.md` PDF 段（`:99,:102,:115,:192`）；`docs/known-defects.md` KD-78（`:2869-2882`）；`docs/research/2026-10-28-painter-design.md` 全文；`docs/research/2026-10-28-batch-29-baseline.md` §5/§8/§11；树内 painter 三文件与设备轨用例；AOSP `PdfDocument.java`/`Canvas.java`/`PdfDocument.cpp`/`PdfRenderer.java`（raw 实读）；Skia `src/pdf/*`（raw 实读）；`notofonts/noto-cjk` 文件字节。
产出性质：**设计 + 来源**。本会话未改任何 Kotlin/JS/资源文件、未运行 Gradle、未上设备；本文档是唯一写盘产物。

---

## 0. 结论摘要（T3/T4 直接可用的十条）

1. **`PdfDocument.Page.getCanvas()` 不是 PDF 画布，是一块 `SkPictureRecorder` 记录画布**；绘制先被录成 `SkPicture`，直到 `PdfDocument.writeTo()` 才 `SkPDF::MakeDocument` → `beginPage` → `drawPicture`（`libs/hwui/jni/pdf/PdfDocument.cpp:24,35,72,82,89-95`）。所以"PDF 语义"= **Skia SkPDF 后端的语义**，Android 框架只决定页面尺寸/内容矩形。
2. **坐标同构，只差单位**：PDF 页单位是 pt（1/72 英寸），原点左上、y 向下；y 翻转由 SkPDF 的初始矩阵负责（`src/pdf/SkPDFDocument.cpp:297-302`）。同一份 DrawOps 只需把 `pxPerDu` 换成 `ptPerDu`，**绘制代码零分支**（与 MVA A1 判据一致）。
3. **页面尺寸是 int pt**（`PdfDocument.java:281,290,331-337`）→ `ptPerDu × viewBox` 必须取整；本设计取 **ceil**（向上取整，宁多不少），余条由 `drawColor` 背景整页覆盖（记录画布的 clip = 整页）。
4. **clip**：PDF 只有"求交路径裁剪"，non-zero→`W n`、evenodd→`W* n`（`SkPDFGraphicStackState.cpp:100-118`）；`REVERSE_DIFFERENCE` 等扩展裁剪官方明列为不支持（`PdfDocument.java:408-412`），Skia 侧对逆填充型裁剪是 `NOT_IMPLEMENTED`。DrawOps V1 只发 nonzero/evenodd 且只求交（`VisualDrawOps.kt:37-40` 词表、契约"clip 只相交"）→ **语义被完整覆盖**，无缺口。
5. **dash 在 PDF 里不是 dash**：SkPDF 不写 `d` 算符，而是把虚线**压平成填充几何**（`SkPDFDevice.cpp:691-706`：`FillPathWithPaint` → `style=FILL` + `pathEffect=null`），压平容差 `0.0625/matrixScale`（`:735-737`）。→ 视觉可达但**段结构（段数、段起点）必须一致**；压平失败时退化为 `width=0` 描边（`:704-706`），是静默劣化路径，须登记。
6. **alpha/混合**：PDF 用 ExtGState 常量 alpha `ca`/`CA`，取值来自 `Paint.getColor4f().fA`（`SkPDFGraphicState.cpp:65-102`）——即"`setColor` 后 `setAlpha`"的最终值；**PDF 侧不引入任何新的 alpha 语义**，批次裁定 2（`floor(colorA/255 × op.alpha)`）在两端自动一致。不支持的 blend（含 `XOR`/`PLUS`）静默降为 SrcOver（`:52-57`）；DrawOps V1 只有 SrcOver → 无差异。
7. **文本是真文本，且字体不替换**：TrueType 走子集嵌入（CIDFontType2；子集失败退回整字体，`SkPDFFont.cpp:469-482`）；**CFF/可变字体/fsType 禁止嵌入/带 maskFilter → 强制 Type3 字形轮廓**（`:332-352`, `:695-930`, `:780` 的 `d0` 着色字形）。系统中文默认字体 `NotoSansCJK-Regular.ttc` 是 OTTO(CFF)（`data/fonts/fonts.xml:1411-1412` + 本会话实读 ttc 首字体 sfnt 标签 = `OTTO`）→ **中文 label 在 PDF 里几乎必然落 Type3**（仍是矢量，但不是"字体"）。字距/kerning 由逐字形 `Td` 保留（`GlyphPositioner`，`SkPDFDevice.cpp:776-850,1103`）；文本可复制靠 `ActualText` 标记（`:1055-1060`）。
8. **静默丢字形是真实失败面**：`SkPDFStrike::Make` 或 `SkPDFFont::GetMetrics` 失败即 `return`（`:950-957`）——不抛异常、不出墨。**T4 必须有"应墨处 ink>0"的硬判**。
9. **抗锯齿不可对齐**：PDF 内容流没有 AA 参数；屏侧 `pathPaint` 开 AA、`textPaint` 默认关 AA；而一致性门若用 `PdfRenderer` 回读，它跑的是 **Pdfium**（`PdfRenderer.java:113,442` 的 `sPdfiumLock`）——两套栅格器。→ 边缘像素**不可比**，只能做结构/内部色/带宽断言。
10. **数值精度**：`rg`/`RG` 颜色分量 4 位小数（`SkPDFUtils.h:84-93`，`kFloatColorDecimalCount=4`）；坐标用"最短可回读"十进制（`:97-101` + `SkFloatToDecimal.h:14-16`：`sscanf("%f")` 回读等于原 float）→ **几何数值无损**。非 PDF/A 模式不写 OutputIntent（`SkPDFDocument.cpp:637-641`），查看器色彩管理不可控。

---

## 1. `PdfDocument` 的 page.canvas 到底是什么（骨架事实）

| 事实 | 出处（本会话 raw 实读） |
|---|---|
| `startPage` 调 `nativeStartPage(...)` 并用返回值构造 `PdfCanvas extends Canvas` | `graphics/java/android/graphics/pdf/PdfDocument.java:121,252-258` |
| 原生侧 `PageRecord` 持 `SkPictureRecorder`；`startPage` 里 `beginRecording(SkRect::MakeWH(contentRect.width(), contentRect.height()))` | `libs/hwui/jni/pdf/PdfDocument.cpp:35,72` |
| `finishPage` 把录制结果固化成 `SkPicture` | 同上 `:82` |
| `writeTo` 时才 `SkPDF::MakeDocument(stream)`，逐页 `beginPage(width,height,&contentRect)` + `drawPicture` | 同上 `:89-95` |
| 页宽/页高/内容矩形单位 = PostScript point（1/72"），内容矩形相对页面左上；尺寸是 **int**，必须为正 | `PdfDocument.java:281,290,299,331-337,346-362` |
| 内容矩形缺省 = 整页 | `PdfDocument.java:369-375` |
| `SkDocument::beginPage` 对 contentRect 做 `clipRect(inner)` + `translate(inner.x, inner.y)` | `src/core/SkDocument.cpp:18-30` |
| 初始变换 = `scale(+1,-1)` + y 平移（顶左原点、y 向下 → PDF 底左原点） | `src/pdf/SkPDFDocument.cpp:297-302` |
| MediaBox = 设备像素尺寸 × `1/rasterScale`；`rasterScale = fRasterDPI/72`，`fRasterDPI` 默认 `SK_ScalarDefaultRasterDPI`（Android 的 `SkPDF::MakeDocument(stream)` 用默认 metadata） | `SkPDFDocument.cpp:241-242,296-302,380-390`；`include/docs/SkPDFDocument.h:146,258`（"PDF pages are sized in point units. 1 pt == 1/72 inch"） |

**架构后果（T3 必须吃进设计里）**：

- 页面内容在 `writeTo` 之前只存在于内存里的 `SkPicture` 中；**"不支持的操作"不会在调用点报错**，而是在 `writeTo` 的回放期被静默忽略/降级（见 §2.8 官方清单）。所以"PDF 目标的能力子集"必须在**生成侧/词表侧**保证，而不是靠运行期抛错。
- 记录画布的 clip = 内容矩形；`drawColor` 会把整页（含 ceil 余条）刷成背景色 → **ceil 取整不会留白边**（§7.2）。
- 因为 `trim()` 会在回放时 `translate(contentRect.left, contentRect.top)`，若未来要把图嵌进带页边距的大页，图画会整体平移到内容矩形原点——本批取全页 contentRect，平移量为 0。

---

## 2. 逐项语义：PdfDocument page canvas vs 普通 Bitmap Canvas

### 2.1 alpha 与混合

- 屏：`Paint.setAlpha(a)` 覆盖颜色 alpha 通道（KD-78 ③ 的 AOSP 依据），Skia 光栅按 a 合成。
- PDF：`populate_graphic_state_entry_from_paint` 取 `paint.getColor4f()`，把 `fA` 经 `SkPDFGraphicState` 写成 `ca`（填充）/`CA`+`ca`（描边）（`SkPDFGraphicState.cpp:65-102`）。PDF 的常量 alpha 与屏幕的逐像素 alpha 在**不重叠的单次绘制**上数学等价；同一路径 fill+stroke 两相位在两侧都是两次独立合成。
- **blend mode**：`pdf_blend_mode()` 对"PDF 没有的名字 + `kXor` + `kPlus`"返回 `SrcOver`（`SkPDFGraphicState.cpp:24-57`）→ 静默替换。官方另一份清单点名 `SRC_ATOP/DST_ATOP/XOR/ADD` 不支持（`PdfDocument.java:422-426`）。DrawOps V1 只有默认 SrcOver，**不触发**。
- 结论：alpha 语义两端一致；混合模式是"未来扩展才需要门"的面。

### 2.2 clipPath

- 屏：`Canvas.clipPath(Path)` = `clipPath(path, Region.Op.INTERSECT)`（`Canvas.java:1088-1090`），无 AA 参数（AA 版重载才有）。
- PDF：Skia 把裁剪栈按路径发出：`W* n`（evenodd）/`W n`（other，即 non-zero），矩形裁剪走 `re W* n`（`SkPDFGraphicStackState.cpp:100-118,120-133`）。
- **复杂裁剪**（裁剪栈含 replace 型重置）走 `SkClipStack_AsPath` + pathops；`Op()` 失败时**静默不加裁剪**（`:135-140` 注释明写）。我们的裁剪都是单级 intersect、bounds ≪ ±30000（`:76-79` 的 `kHuge` 界限），既无 replace 也无 difference，不走这两条分支。
- 逆填充型裁剪（`INVERSE_*`）是 `NOT_IMPLEMENTED`（`:111-112`），官方 javadoc 也把 `REVERSE_DIFFERENCE` 列为不支持（`PdfDocument.java:408-412`）。DrawOps 只收 nonzero/evenodd（`VisualDrawOps.kt:37-40`）→ 不触发。
- **裁剪边界抗锯齿**：内容流无 AA 开关，查看器怎么栅格化裁剪边不可控；屏侧我们的调用是不带 AA 的 `clipPath`。所以"裁剪区域内部/外部"可比，"裁剪边 1px 带"按允许差异处理。

### 2.3 dash

- 屏：`DashPathEffect` 在本地空间把虚线压成描边轮廓，再随矩阵变换。
- PDF：Skia 在 `internalDrawPath` 里对 `paint->getPathEffect()` 调 `FillPathWithPaint(modifiedPath, *paint, &builder)`，成功则 `style=FILL`、`strokeWidth` 保留用于生成轮廓、`pathEffect=null`（`SkPDFDevice.cpp:691-706`）。**内容流里没有 `d` 算符**，产物是填充几何。旁证（本会话实跑）：对 `src/pdf/{SkPDFDevice,SkPDFUtils,SkPDFGraphicState,SkPDFGraphicStackState,SkPDFFormXObject}.cpp` 做大小写不敏感 `dash` 全扫，**0 命中**——该后端没有"写 dash 数组"的代码路径。
- 几何误差来源：压平容差 `kToleranceScale=0.0625 / matrixScale`（`:735-737`），即 scale=1 时 ≤0.0625 用户单位；曲线离散化后与屏幕 Skia 的 stroker 结果可以有亚像素差。
- 静默劣化：`FillPathWithPaint` 失败 → `style=STROKE` 且 `strokeWidth=0`（`SkPDFDevice.cpp:704-706`）——虚线变发丝实线，无诊断。**登记**（§9 未验证：本会话未构造出该分支）。
- 判定边界：**段数/段起点属于语义，必须一致**；亚像素轮廓差属于允许差异。

### 2.4 文本（字距 / 字体替换 / 子集嵌入）

- 发射形态：`BT … ET`，逐字形编码 + `Tf` 字号 + `Td` 定位（`SkPDFDevice.cpp:1003-1104`，`GlyphPositioner:776-850`）。当实际位置 ≠ 累计 advance 时补一个 `Td`（`:815-825`）→ **kerning/字距被逐字形位置钉住**，不做整串重排。
- 子集嵌入：`emit_subset_type0` 里 `can_subset(metrics)` → `SkPDFSubsetFont(typeface, glyphUsage)`；失败退回**整字体数据**（`SkPDFFont.cpp:469-482`）。字体名带子集前缀（`:305-307`）。
- **Type3 降级条件**（`SkPDFFont.cpp:332-352`）：可变字体、wOFF 且子集器不支持、`kNotEmbeddable`（fsType 限制，`SkFontHost_FreeType.cpp:532-535`）、**CFF（`metrics.fType == kCFF_Font`）**、或 strike 带 maskFilter。Type3 用 `CharProcs` + `d0`/`d1` 过程写**字形轮廓**（`:695-717,780,818,930`）。→ 仍是矢量、仍用当前填充色，但 PDF 里没有"字体"对象，文本提取退化为 `ActualText` 标记（`:1055-1060`）。
- **中文必然落 Type3 的证据链**：AOSP `data/fonts/fonts.xml:1411-1412` 把 `zh-Hans` 指向 `NotoSansCJK-Regular.ttc`；本会话用 raw 读该文件字节：TTC 头 `ttcf`/version 1.0/numFonts=10，首字体偏移 0x34 处 sfnt 标签 = **`OTTO`**（CFF 轮廓）。→ FreeType 报 "CFF" → `kCFF_Font` → 强制 Type3。**设备上的 ttc 是否与上游同格式未在真机核验**（§9）。
- 字形位置精度：屏与 PDF 用的是同一份"本地空间 `drawText` 位置"（文本 blob 在记录/绘制时按本地字号算好，矩阵只做缩放）→ **几何位置两侧同源**（本会话为源码推导，非设备实测；§7.4 的 label 用例即其实测钉子）；差别只剩栅格器。
- 静默丢字形：`SkPDFStrike::Make`/`GetMetrics` 失败 `return`（`:950-957`）；`numGlyphs <= gid` 直接 `continue`（`:1067-1069`）。
- **字体替换**：生成侧不发生替换（要么子集嵌入、要么 Type3 轮廓），所以 PDF **不依赖查看器装了什么字体**；但**生成设备**的字体解析链（含逐字符 fallback）决定了 PDF 里的字形 → 跨设备/跨 OEM 的产物本就不同，一致性只主张**单设备**。

### 2.5 抗锯齿

- 屏：`pathPaint = Paint(ANTI_ALIAS_FLAG)`、`textPaint = Paint()` 后**显式** `flags = REPLAY_TEXT_PAINT_FLAGS`（0，无 AA）——批次 28 的 MVA 取舍（`VisualDrawOpsPainter.kt:50-54,266-286`）。注意平台 `Paint()` 构造默认是 1283（含 AA），**不能**把"构造默认"当成"无 AA"（2026-10-28 设备证据 R1 / KD-78 ④）。
- PDF：矢量输出，内容流无 AA 参数；查看器侧的抗锯齿不可控（`PdfRenderer.Page.render` 的签名里没有任何 AA 开关，`PdfRenderer.java:388-410`）。
- 结论：**形状边缘、文本边缘的像素必然不同**；任何"整图像素相等"的 PDF 门都会红，且是设计上必然红。

### 2.6 坐标与 DPI

- 屏：`pxPerDu`（= density 时 1 du = 1 dp）。
- PDF：页单位 pt；`ptPerDu` 由目标栏宽算（契约 `visual-generation-contract.md:192`）。1 du 在 PDF 里的物理长度 = `ptPerDu/72` 英寸——与屏上 1/160 英寸**不同**，这是"排版目标"而不是"物理同尺寸"。一致性门必须**先归一化比例**再比。
- PDF 画布的 `getDensity()` = `Bitmap.getDefaultDensity()`（`Canvas.java:132-141`）——**设备默认密度**，不是 DENSITY_NONE、也不是 72。对纯矢量无影响；但 `Canvas.drawBitmap` 会按"画布密度/位图密度"自动缩放（`Canvas.java:1605-1610`）→ **未来一旦加入位图 op，PDF 产物会随设备密度变化**。门：位图 op 加入时必须 `setDensity(DENSITY_NONE)` 或用显式目标矩形（仓内先例：`DeterministicMistakePdfRenderer.kt:116-121` 用 `drawBitmap(bitmap, null, bounds, paint)`）。

### 2.7 颜色与数值精度

- 填充/描边色：`rg`/`RG`，每分量 4 位小数（`SkPDFUtils.h:84-93`；`SkPDFGraphicStackState.cpp:20-27`）→ 误差 ≤0.00005（≤0.013/255），可忽略。
- 几何：`AppendScalar` 用 `SkFloatToDecimal`（`SkPDFUtils.h:97-101`），`SkFloatToDecimal.h:14-16` 保证 `sscanf("%f")` 回读等于原 float → **float32 无损**。
- 色彩空间：非 PDF/A 模式**不写 OutputIntent**（`SkPDFDocument.cpp:637-641`）；颜色按 DeviceRGB 写。查看器若做显示配置色彩管理，颜色可与屏不同（不可控）。
- PDF 元数据：`CreationDate/ModDate` 只在非零时写（`SkPDFMetadata.cpp:78-83`），Android 走默认 metadata（`fCreation = 0`）→ 不引入时间戳；但**仍不主张 PDF 字节级稳定**（字体子集 tag、Skia 版本内部实现都不是我们的稳定契约）。

### 2.8 官方"不支持"清单 vs DrawOps V1 词表

`PdfDocument.Page.getCanvas()` javadoc 明列（`PdfDocument.java:405-427`）：逆路径裁剪（`REVERSE_DIFFERENCE`）、`drawVertices`、`ColorFilter`、`MaskFilter`、部分 XFER 模式（`SRC_ATOP/DST_ATOP/XOR/ADD`）。

| DrawOps V1 op | 映射到的 Canvas 调用 | PDF 行为 | 风险 |
|---|---|---|---|
| `Save`/`Restore` | `save()/restore()` | Skia 按内容条目自动成对 `q/Q`，与我们的 save 栈无关 | 无 |
| `Concat` | `setValues` + `concat` | `cm`（仿射 6 值；无透视） | 无 |
| `Clip` | `setMatrix(root)` + `clipPath(path)` + `setMatrix(mirror)` | `W n`/`W* n` | 逆填充/差异裁剪不支持——词表外 |
| `DrawPath` | `drawPath(path, paint)`（fill 相位 → stroke 相位） | `f`/`f*`，`S`，`LW/LC/LJ/ML`；dash 压平 | 虚线压平（§2.3）；无 maskFilter/shader |
| `Label` | `save/setMatrix/translate/rotate/drawText` | `BT/ET/Tf/Td/Tj` + 字体子集或 Type3 | Type3、静默丢字形 |
| `background` | `drawColor(argb)` | 整裁剪区填充 | 非不透明背景与纸白语义差 |

**未出现的构造**（因此 PDF 侧不会踩坑）：无 `saveLayer`（无组 alpha）、无图片、无 shader、无 colorFilter/maskFilter、无 xfermode、无 `drawVertices`、无透视矩阵、无 `Path.FillType.INVERSE_*`。

---

## 3. GitHub 参考实现评分（只借鉴模式，不引依赖）

评分维度：license（借鉴自由度）、维护度（`gh api` 2026-10-28 实取 `pushed_at`/`open_issues_count`/`archived`/stars）、与本任务的结构适配度。**AAR/stdlib 不变**，故一律不引入依赖。

| 仓库 | license | 维护度（实取 2026-10-28） | 适配度 | 借鉴点 / 不借鉴理由 |
|---|---|---|---|---|
| [google/skia](https://github.com/google/skia) | BSD-3-Clause | pushed 2026-10-28，issues 51，未归档，11,007★ | **事实源**（不是"参考"） | `src/pdf/*` 就是 Android `PdfDocument` 的 PDF 后端：clip 发射、dash 压平、字体嵌入/Type3、颜色精度全在这里定 |
| [BigBadaboom/androidsvg](https://github.com/BigBadaboom/androidsvg) | Apache-2.0 | pushed 2025-06-01，issues 51，未归档，1,324★（慢速维护，仍是 Android SVG→Canvas 事实标准） | **高** | ① `SVG.renderToCanvas(Canvas)`（`SVG.java:409-438`）证明"渲染器只认 `Canvas`、不认后端"——这正是 `PdfDocument` 页画布能直接复用 painter 的先例；② 非缩放描边用 `canvas.getMatrix() → setMatrix(identity) → 绘制 → setMatrix(还原)`（`SVGAndroidRenderer.java:598-616`），与批次 28 的 clip/label 舞步同型；③ 文字 `HINTING_OFF` 求稳定（`:182-196`）——我们因 KD-78 ④ 未签发不开 hinting，仅记录差异 |
| [TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | Apache-2.0 | pushed 2024-03-18（**约 2.5 年停滞**），issues 125，未归档，1,196★ | 中（词汇表参考） | 内容流词汇与语义对照：`moveTo/lineTo/curveTo/closePath` + `fill/fillEvenOdd/stroke` + `clip/clipEvenOdd` + `setLineDashPattern(pattern,phase)` + `setLineWidth/Join/Cap/MiterLimit`（`PDAbstractContentStream.java:1104,1205,1220,1238,1256-1332`）；`fillRect` 参数写"lower left"（`PDPageContentStream.java:1573-1597`）→ **PDF 原生 y 向上**，与 Canvas 相反。只作对照，不采用 |
| [apache/pdfbox](https://github.com/apache/pdfbox) | Apache-2.0 | pushed 2026-10-28，issues 46，3,138★ | 中（上游） | 同上；它证明"显式内容流 API"是另一条路线——**我们不选它**（要自己管坐标系翻转、文本编码、字体资源），选"Canvas 目标无关"路线 |
| [typst/svg2pdf](https://github.com/typst/svg2pdf) | Apache-2.0 | **archived**，pushed 2026-07-10，README 首行"not maintained anymore" | 低 | **不采用**；README 指向 `krilla` |
| [LaurenzV/krilla](https://github.com/LaurenzV/krilla) | Apache-2.0 | pushed 2026-10-26，issues 33，471★（活跃） | 中 | 现代 PDF 写入器的分层（内容流/资源/字体分开），可作"若将来必须绕开 Skia"的路线参考；本批不采用 |

**采纳结论**：只借三件模式——(a) "渲染器只认 `Canvas`"（androidsvg 的 API 形状 = 我们复用 `VisualDrawOpsPainter` 的依据）；(b) 根空间舞步（已在树）；(c) 字体嵌入/Type3 降级的存在性与后果（Skia 源码即证据，用于设定文本判据）。**零依赖**。

---

## 4. 同一 DrawOps 在屏与 PDF 上"允许的差异"清单

判定列含义：**等** = 语义与数值都应对齐（超差即硬失败）；**容差** = 允许限定带宽内的差；**不可比** = 结构上不可能相等，判据必须换形态。

| # | 面 | 屏（`Canvas(Bitmap)`） | PDF（`PdfDocument` → SkPDF → 查看器） | 判定 | 依据 / 边界 |
|---|---|---|---|---|---|
| 1 | 路径几何 | float32 本地坐标 → 矩阵 | 同 float32 → 最短回读十进制 | **等** | `SkPDFUtils.h:97-101` + `SkFloatToDecimal.h:14-16` |
| 2 | fill rule | clip 用声明值（`WINDING`/`EVEN_ODD`）；DrawPath 填充固定 `WINDING`（`VisualDrawOpsPainter.kt:159`） | `W n`/`W* n`（clip）、`f`（填充） | **等** | `SkPDFUtils.cpp:343-360`；`SkPDFGraphicStackState.cpp:110-117` |
| 3 | 裁剪求交 | `clipPath` = INTERSECT，非 AA | `W n` / `W* n` | **等**（区域内/外） | `Canvas.java:1088-1090`；`SkPDFGraphicStackState.cpp:100-118` |
| 4 | 裁剪边 | 非 AA 硬边 | 内容流无 AA，查看器决定 | **容差**（边 ≤1px 带） | §2.2 |
| 5 | 矩阵/变换 | preconcat | `cm` | **等** | `SkPDFGraphicStackState.cpp:151-195`（updateClip/updateMatrix） |
| 6 | 对象 alpha | `setAlpha` 后逐像素合成 | `ca`/`CA` 常量 alpha | **等**（内部像素 ≤±2/255） | `SkPDFGraphicState.cpp:65-102`；颜色 4 位小数 |
| 7 | 混合模式 | SrcOver（唯一） | SrcOver（唯一支持面） | **等** | `:52-57` + `PdfDocument.java:422-426` |
| 8 | 描边四参 | width/cap/join/miter | `LW/LC/LJ/ML` | **等** | `SkPDFGraphicState.cpp:30-48,95-102` |
| 9 | 虚线 | 本地空间虚线轮廓 | **压平成填充几何**（无 `d`） | **容差**（几何 ≤0.1 用户单位；段结构必须等） | `SkPDFDevice.cpp:691-706,735-737` |
| 10 | 文本位置 | 本地空间逐行 baseline | 逐字形 `Td` | **等**（bbox/advance 链，±1px） | `:776-850,1103` |
| 11 | 文本栅格 | 无 AA（显式 `flags = REPLAY_TEXT_PAINT_FLAGS = 0`；平台 `Paint()` 默认 1283 含 AA）、Skia hinting | 查看器 AA + 自己 hinting（Pdfium） | **不可比** | §2.5；`PdfRenderer.java:113,442` |
| 12 | 字体字形 | 设备字体链解析 | 同设备字体 → 子集嵌入或 Type3 轮廓 | **等**（单设备内） | `SkPDFFont.cpp:332-352,469-482` |
| 13 | 字体对象/可复制性 | 不适用 | TrueType→CIDFontType2；CFF→Type3；`ActualText` 保复制 | **不可比**（元数据面） | `:695-930,1055-1060` |
| 14 | 颜色值 | 8bit ARGB | `rg` 4 位小数；无 OutputIntent | **容差**（±2/255） | §2.7 |
| 15 | 背景 | null=透明；可半透明 | `drawColor` 整页填充；**页面无透明** | **不可比**（见 §5.3） | `PdfDocument.cpp:93-95` + trim |
| 16 | 页面尺寸 | 位图整数像素，可截断 | int pt，`contentRect` 必须落在页内 | **等**（按 ceil 规则，夹具固定） | `PdfDocument.java:331-337,346-362` |
| 17 | 页面外内容 | 位图外裁剪 | 记录 cull + `trim` 裁剪 | **等**（夹具不越界） | `SkDocument.cpp:18-30` |
| 18 | 不支持构造 | 无 | 官方清单静默忽略/降级 | **等**（词表外，结构性不存在） | §2.8 |
| 19 | PDF 字节 | 不适用 | 字体子集/Skia 内部 | **不可比** | 不主张 |
| 20 | 位图 op（未来） | 按画布/位图密度缩放 | PDF 画布密度 = **设备默认密度** | 现无此 op；**加入即需门** | `Canvas.java:132-141,1605-1610` |

---

## 5. 不可消除差异与判定边界

### 5.1 字体栅格化（hinting / 指令选择）

- 成因：PDF 只保存字形轮廓与位置；笔画填充由**查看器**的栅格器完成（若用 `PdfRenderer` 回读则是 Pdfium），与屏上 Skia 的栅格器不同。
- 判定边界：**字形级不可比**；可比的是 (a) 墨迹 bbox（±2px）、(b) 墨量比（文本 ∈ [0.7,1.3]）、(c) 应墨处 ink>0（硬判）。
- 不允许：把"文本像素相等"写成断言；把某台设备的字形像素当金样。

### 5.2 抗锯齿

- 成因：PDF 无 AA 参数；屏 `pathPaint` 开 AA、`textPaint` 关 AA。
- 判定边界：距墨迹 bbox 边 ≥2px 的**内部**像素必须等（±2/255）；边缘 2px 带内允许任意差；"带外出现墨"或"bbox 位移 >2px"是硬失败。

### 5.3 页面透明性

- 成因：PDF 页无 alpha 通道；`drawColor` 在 white 纸上等效不透明。屏上 `background=null` 得透明位图；半透明背景得半透明像素。
- 判定边界：**一致性夹具一律给不透明 background**（`#RRGGBB`）；`background=null` 在 PDF 目标的语义 = 纸白。这不是缺陷，是目标语义差，须进契约/复核（T6）而不是进门。

### 5.4 查看器色彩管理

- 成因：非 PDF/A 模式不写 OutputIntent（`SkPDFDocument.cpp:637-641`），查看器可能按显示配置做色彩管理。
- 判定边界：内部色 ±2/255；若某设备超出，先判"设备色彩管理"（F6 环境类），不判实现错。

### 5.5 字体族解析的跨设备边界

- 成因：PDF 嵌入的是**生成设备**解析出的字体（含逐字符 fallback）。
- 判定边界：一致性主张**只限单设备**（与批次 28 §4.4 "跨设备不主张"同款）；跨设备只主张"PDF 自包含、不依赖查看器字体"。

### 5.6 PDF 字节

- 不主张字节稳定（字体子集、Skia 内部实现、将来 metadata）。门用"回读栅格 + 结构探针"。

---

## 6. 一致性门雏形设计（T4；**原型门**，非产品门/发版门）

### 6.1 方法选择（并明确否掉另外三条）

| 方案 | 可行性 | 裁定 |
|---|---|---|
| G1 "画布指令序列序列化相等" | 比较的是**输入 DrawOps**（同一份），与后端无关 → 恒真，无信息量 | **否**（MVA `2026-10-28-mva-remaining-order.md:64` 的"指令序列相等"只能证明夹具相同） |
| G2 解析 PDF 内容流比对算符 | 需要 PDF 解析器（新依赖，违 AAR/stdlib 不变）；Skia 流是 Flate 压缩 | **否** |
| G3 整图像素相等 | Pdfium vs Skia 两套栅格器 + AA/hinting 差异 → **设计上必然红** | **否** |
| **G4 结构探针 + 归一化光栅对拍** | 只用框架 `PdfRenderer` + `Bitmap`，零依赖 | **采用** |

G4 的形态：同一 `VisualDrawOpsDocument` → 屏上渲到 `Bitmap`、PDF 写到临时文件 → `PdfRenderer` 按归一化比例栅格化 → **逐探针**断言（bbox/墨量/内部色）+ 一份**整图差异分布**（仅用于把差异分类到 F1–F6，不作通过判据）。

### 6.2 夹具与比例（归一化是关键）

- viewBox 48×32 du；屏 `pxPerDu = 2f` → 96×64 位图（与批次 28 设备轨同尺寸，`VisualDrawOpsPainterInstrumentedTest.kt:42,77-79`）。
- PDF `ptPerDu = 1f` → 页 48×32 pt；`PdfRenderer` 的 `transform = scale(2,2)`（点→像素）→ 96×64。两侧 1 du = 2 px，**逐像素网格对齐**。
- `PdfRenderer.Page.render(bitmap, null, Matrix().apply{setScale(2f,2f)}, RENDER_MODE_FOR_DISPLAY)`；页坐标是"点、左上原点、y 向下"（`PdfRenderer.java:333-346,358-363,427-436`：默认变换是纯正缩放+平移，无 y 翻转）→ 与我们的 PDF 画布坐标系一致，无需翻转。
- 临时文件写在 `InstrumentationRegistry` 的 `targetContext.cacheDir`，测后删除；`ParcelFileDescriptor` 需 seekable（文件满足）。

### 6.3 容差表（原型门；每项给来源）

| 判据 | 数值 | 来源/理由 |
|---|---|---|
| 墨迹 bbox（形状） | 每边 ±1.5 px | AA 边 + Pdfium 栅格 (±1) + 压平容差 0.0625 du×2px/du≈0.125px |
| 墨迹 bbox（文本） | 每边 ±2 px | 字体 hinting/AA 差异 |
| 墨量比（形状） | 0.9–1.1 | AA 覆盖率差 |
| 墨量比（文本） | 0.7–1.3 | 无 AA vs AA 的覆盖率差 |
| 内部像素色 | 每通道 ±2 | `rg` 4 位小数（≤0.013/255）+ 查看器合成 |
| alpha 探针色 | 每通道 ±3 | 8bit alpha 已按 floor 量化，两侧同值；余量为栅格 |
| 背景 | 四角 == 背景色 ±2 | `drawColor` 覆盖整页 |
| 虚线段结构 | **段数相等、段边界 ±2 px** | dash 被压平，段结构等价于 pattern 语义 |
| 应有墨处 | `ink > 0`（硬判） | 静默丢字形（`SkPDFDevice.cpp:950-957`） |
| 页面尺寸 | `page == ceil(viewBox × ptPerDu)` | `PdfDocument.java:331-337` int 约束 |
| 被裁区域 | 裁剪区外墨量 == 0（硬判） | clip 语义破坏/矩阵双乘 |
| 几何位移 | bbox 质心位移 ≤2 px（硬判） | 舞步/列序错位的量级远大于 2px |

### 6.4 失败分类（T4 的归因面）

| 类 | 症状 | 归因 | 处置 |
|---|---|---|---|
| F1 几何 | bbox 超差 / 被裁区有墨 / 质心位移 >2px | 矩阵列序、clip/label 舞步、页面尺寸算错 | 硬失败（实现缺陷） |
| F2 颜色 | 内部通道超差（方向一致） | alpha 语义 / 颜色解析 | 硬失败 |
| F3 内容缺失 | 应墨处 ink==0 | Skia 静默丢字形 / op 被 PDF 忽略 | 硬失败（并留 PDF 文件证据） |
| F4 栅格差 | 差异只落在 bbox 边 2px 带、内部全等 | AA/hinting | **允许**（计为证据计数，不判红） |
| F5 dash 结构 | 段数不等 / 段边界超差 | 压平失败退化为发丝（`SkPDFDevice.cpp:704-706`）或 pattern 不同 | 硬失败 |
| F6 目标语义 | 背景/页面余条/色彩管理 | 夹具或环境 | 夹具缺陷或设备类，不计实现红 |

### 6.5 门的纪律

- 类名与 KDoc 必须写"**原型门**"；**不进** `docs/current/release-gates.md`，不作产品门/发版门（计划 §1.7 裁定）。
- 不得用"整图 diff 百分比"当通过阈值（会掩盖 F3：一个大区域的墨全丢也仍可能低于 5% 像素）。
- **容差 ≠ 放宽既有断言**：屏侧批次 28 的 19 例逐像素/有码异常判据一字不改（`VisualDrawOpsPainterInstrumentedTest.kt` 全 19 `@Test` 保持原断言）；§6.3 的容差只作用于**新增的跨目标对拍**，且每项都在表里给了来源，不得事后按"红灯就调大"。

---

## 7. T3 落码规格：`VisualDrawOpsPdfPainter`

### 7.1 形状裁定：生命周期包装，**不复制状态机**

树内 `VisualDrawOpsPainter.draw(document, canvas, pxPerDu, labelContent)` 已经与后端无关（KDoc 明写 PDF 侧只替换比例，`VisualDrawOpsPainter.kt:33-36,58-67`）。再写一份 op 遍历 = 两份状态机必然漂移（批次 28 已修的矩阵舞步/全字段 binder/dash 按尺寸精配都不会自动同步到第二份）。因此：

- `VisualDrawOpsPdfPainter` = **组合**一个 `VisualDrawOpsPainter` 私有实例 + PDF 生命周期 + 页面几何 + I/O 归类。
- 它**没有**自己的 `when(op)`；既有 `PAINTER_*` 码族（schema/balance/unknown-label/stroke-only/dash/label-math/bounds/color/alpha）由共享 painter 原样抛出。

**它消灭的具体失败**：① `PdfDocument` 必须成对 `startPage/finishPage` 且必须 `close()`——`close()` 与 `writeTo()` 都调 `throwIfCurrentPageNotFinished()`（`PdfDocument.java:171,197-200,235-239`）：`draw()` 抛异常后若不 finish，`finally` 里的 `close()` 会再抛 `IllegalStateException("Current page not finished!")` **掩盖原始异常**；② int pt 页尺寸的取整/越界在调用点才炸（`PageInfo.Builder` 的 `IllegalArgumentException("page width must be positive")`，`PdfDocument.java:331-337`）——折成有码异常；③ `writeTo` 的 `IOException` 逃出有码异常族，调用方无法分类。

### 7.2 API 与页面几何

```kotlin
// 纯层（追加在 VisualDrawOpsPainterPure.kt，保持零 android 导入）
internal data class VisualPdfPageSize(val widthPt: Int, val heightPt: Int)

internal fun visualPdfPageSize(viewBoxW: Double, viewBoxH: Double, ptPerDu: Float): VisualPdfPageSize
// 规则：ptPerDu 有限且 >0（否则 PAINTER_PDF_PAGE_BOUNDS）；
//       w = ceil(viewBoxW * ptPerDu), h = ceil(viewBoxH * ptPerDu)；
//       1 <= w,h <= MAX_PAGE_POINTS（14400；超界同码拒绝——14400 是兼容预算，
//       规约条文本轮未取到，按 §9-11 处理，不当作条文事实）。

// 应用层
internal class VisualDrawOpsPdfPainter {
    private val shared = VisualDrawOpsPainter()
    fun write(
        document: VisualDrawOpsDocument,
        out: OutputStream,
        ptPerDu: Float,
        labelContent: (String) -> VisualLabelContent?,
    )
}
```

实现要点（每条对应一个已存在的失败）：

1. 生命周期固定为：`PdfDocument()` → `try { page = startPage(...); try { shared.draw(...) } catch (t) { runCatching { finishPage(page) }; throw t }; finishPage(page); writeTo(out) } finally { runCatching { document.close() } }`。
   **为什么要在 draw 失败时也 finish**：`close()` 与 `writeTo()` 都调 `throwIfCurrentPageNotFinished()`（`PdfDocument.java:171,197-200,235-239`），页未 finish 时 `close()` 抛 `IllegalStateException("Current page not finished!")`，会把 `finally` 里的收口异常盖在真实失败（例如 `PAINTER_BALANCE`）之上；先 finish（丢弃）再 close 才能让原始有码异常原样上抛。`close()` 用 `runCatching` 包住，避免收口异常二次掩盖。
2. `PageInfo.Builder(w, h, 1).create()`（不设 contentRect ⇒ 整页，`PdfDocument.java:369-375`）。
3. `shared.draw(document, page.canvas, ptPerDu, labelContent)` —— 唯一绘制入口。
4. `document.writeTo(sink)`，其中 `sink = PdfWriteFailureTrackingStream(out)`：API 34 的 native `SkJavaOutputStream::write` 会 `ExceptionDescribe/ExceptionClear` 掉 OutputStream 的 Java 异常并返回 false（`CreateJavaOutputStreamAdaptor.cpp`），直接 `catch (IOException)` **不可达**（2026-10-28 设备证据 R2 / KD-79）。跟踪流在异常离开 Java 前记录并重抛，`writeTo` 返回后若 `firstFailure != null` → `paintingFailure(PAINTER_PDF_IO, …)`；原 `catch (IOException)` 保留作平台行为变化的兜底。
5. 不读 `hitRegions`、不画 math（沿用共享 painter 的 `PAINTER_LABEL_MATH` fail-closed）。

**ceil 而不是 round 的理由**：`round` 会把右/下 ≤0.5pt 的内容裁掉（背景/边框墨迹贴边即红）；`ceil` 只多出 ≤1pt 的空白，而 `drawColor` 在记录画布上的 clip 就是整页 → 余条被背景色填满，不会白边。副作用可控且可断言（§6.3 页面尺寸一行）。

**不做的**（每条都无已发生的失败可指认）：多页拼装、把图嵌进大页的 `drawInto(pageCanvas, originPt, …)`、`PdfDocument` 复用池、PDF 元数据注入、`/AIGC`、core:export 集成（KD-75 热区）。

### 7.3 码面（新增 2 码，各指认消灭的失败）

| 码 | 触发 | 消灭的失败 |
|---|---|---|
| `PAINTER_PDF_PAGE_BOUNDS` | `ptPerDu` 非有限/≤0；ceil 后 <1 或 >14400 | `PageInfo.Builder` 抛无码 `IllegalArgumentException`（页宽必须为正）/ 巨型页导致查看器拒开；折进有码族 |
| `PAINTER_PDF_IO` | `writeTo` 正常返回但跟踪流记录了 OutputStream 写侧异常（含被 native 吞掉的 IOException）；或未来平台让异常逃回 Java 时的 `catch (IOException)` | 写侧异常逃出 `VisualDrawOpsPaintingException` 族 → 调用方无法按码分类；更严重的是 native 吞异常后**静默截断 PDF 并报成功**（fail-open，KD-79） |

诊断预算沿用 `paintingFailure(...)` 的 512 字符截断（`VisualDrawOpsPainterPure.kt:69-73`）。

### 7.4 设备轨用例（T3，先红后绿；与批次 28 判据同构）

| 用例 | 判据（与屏同文档对拍，§6.3 容差） |
|---|---|
| `pdfFillLandsOnTheSameBoundingBoxAsScreen` | 矩形 fill：bbox ±1.5px、内部色 ±2 |
| `pdfShearMatrixPlacesInkByContractColumnOrder` | 切变 `[1,1,0,1,0,0]`：墨落在 du(2,3)-(3,5) 对应像素带；错列序必红 |
| `pdfNestedGroupClipCutsInRootSpace` | 组内 clip：裁外 ink==0（硬） |
| `pdfGroupLabelLandsAtProjectedRootSpacePosition` | label bbox ±2、ink>0（硬） |
| `pdfHalfAlphaUsesTheFloorQuantizedAlpha` | 0.5 → 内部色 == screen ±3（floor(127.5)=127 的口径两侧同值） |
| `pdfBackgroundCoversTheRoundedUpPage` | 四角 == 背景色；页尺寸 == ceil |
| `pdfDashKeepsItsSegmentPattern` | 段数相等 + 段边界 ±2 |
| `pdfUnusablePtPerDuFailsClosed` | `PAINTER_PDF_PAGE_BOUNDS` |
| `pdfOversizePageFailsClosed` | `PAINTER_PDF_PAGE_BOUNDS` |
| `pdfWriteFailureIsCoded` | 抛 `IOException` 的 `OutputStream` → `PAINTER_PDF_IO` |
| `pdfTextIsEmittedAsVectorText`（可选探针） | PDF 字节（ISO-8859-1 视作字符串）含 `/Type3` 或 `/FontFile2`；**本会话未验证压缩/对象布局是否会让该探针失效**（§9） |

---

## 8. 新增件 → 消灭的失败（12.2 自检）

| 新增 | 消灭的具体失败 |
|---|---|
| `VisualDrawOpsPdfPainter`（包装类） | `PdfDocument` 生命周期/资源泄漏、页几何 int 越界无码抛错、I/O 失败不可分类（§7.1 三条） |
| 纯层 `visualPdfPageSize` | JVM 门覆盖不到 ceil/边界规则（无 Robolectric，同 `VisualDrawOpsPainterPure.kt:10-18` 的分层理由） |
| `PAINTER_PDF_PAGE_BOUNDS` / `PAINTER_PDF_IO` | 见 §7.3 表 |
| T4 一致性门用例 | "PDF 半边只有一句 UNVERIFIED"的状态；让 F1/F2/F3/F5 类缺陷在合入前变红 |
| （不加）第二套 op 状态机 | —（相反：加了才是制造"两份实现漂移"的失败） |
| （不加）PDF 内容流解析器 / SVG writer / 多页 / 位图 op | 无已发生失败；各自需要新依赖或新契约 |

---

## 9. 未验证项（UNVERIFIED，不作为事实引用）

1. **设备侧未跑任何东西**：`PdfDocument` 实际产物、`PdfRenderer` 坐标/比例、Type3 是否真的落在中文 label 上、静默丢字形是否可达——全部未实测（本会话未 Gradle、未 adb）。
2. **设备上的 `NotoSansCJK-Regular.ttc` 与上游同格式**：只核了 `notofonts/noto-cjk` 上游文件字节（OTTO），未读 `/system/fonts/`。验证命令（设备轨执行）：`adb shell "head -c 4 /system/fonts/NotoSansCJK-Regular.ttc"` 与 `fonts.xml` 对照。
3. **`SK_ScalarDefaultRasterDPI == 72`**：常量定义位置本轮未定位；只确认 `SkPDFDocument.h:258` 明言"页以 point 为单位"、AOSP 以整数 pt 传页尺寸，故实际 rasterScale 必为 1（否则 MediaBox 会与请求页尺寸不符）。
4. **dash 压平失败（`FillPathWithPaint` 返回 false）的触发条件**：源码存在该分支（`SkPDFDevice.cpp:704-706`），但本会话未构造出实例；按"静默劣化"登记，不猜测频率。
5. **`SkPDFSubsetFont` 是否编进 Android 的 libhwui**：源码有"无子集器则返回 nullptr、退整字体"的桩（`SkPDFSubsetFont.cpp:202-203`）；Android 产物是否启用未核。
6. **`/Type3`/`/FontFile2` 字节探针**是否会被压缩/对象流隐藏：未验证。
7. **查看器色彩管理**对 `rg` 值的影响：未验证（`PdfRenderer` 未实测）。
8. **`PdfRenderer` 的 y 方向/原点**：依据 `PdfRenderer.java:427-436` 默认变换无 y 翻转推出"左上原点、y 向下"，未真机验证（T4 第一步应先做一个已知矩形的"原点探针"）。
9. **AOSP 镜像归档**：`aosp-mirror/platform_frameworks_base` 的 `archived=true`、pushed 2025-11-12（镜像常态，非停更信号）；`PdfRenderer.java` 不在 main 树，本会话用 `android-14.0.0_r1` tag 读（API 34；`PdfDocument.java`/`Canvas.java` 用 main）。两者版本差未逐行 diff。
10. **`fillPath`/`stroke` 在多子路径上的一致性**：词表允许一条 op 内多子路径（`VisualPathCommand` 流），两侧都按一条 Path 处理；未做设备探针。
11. **`MAX_PAGE_POINTS = 14400`**：取"常见查看器单页上限"作兼容预算；**本会话未取到规约条文**（PDF 32000 页尺寸上限未检索），故它是自定预算常量，不是条文事实；若 T6 要写进契约，须补条文出处或明确标为预算。
12. **规范条文未直读**：本会话未查阅 ISO 32000-1/PDF 1.7 原文；§2 的全部结论都是"**实现事实**"（AOSP Java/JNI 源码、Skia 源码、官方 javadoc），不是"规范要求"。二者在裁剪无 AA、无页透明等点上应一致，但条文级引用须另补。
13. **通道单一**：`developer.android.com` 与 `android.googlesource.com` 两次 WebFetch 均 443 超时（本会话实试），`raw.githubusercontent.com` 的 curl 也不可达（HTTP 000）——本研究的 AOSP/Skia 原文只能经 `gh api` 单一通道取得，**未做第二通道交叉核证**。`gh api` 取的是同一仓库同一 ref 的原始字节，非二手摘要；但若要求"多独立来源一致"，本文件尚未满足，按单一主源（官方仓库 raw）处理。

---

## 10. 来源

**AOSP（`gh api -H "Accept: application/vnd.github.raw"` 本会话实读；URL 形态 `https://github.com/aosp-mirror/platform_frameworks_base/blob/<ref>/<path>`）**
- `graphics/java/android/graphics/pdf/PdfDocument.java`（ref `main`）：`:121,252-258`（PdfCanvas）、`:281,290,299,331-337,346-362,369-375`（PageInfo pt/int/contentRect）、`:405-427`（不支持清单）、`:433`（getCanvas）
- `libs/hwui/jni/pdf/PdfDocument.cpp`（ref `main`）：`:24,35,72,82,89-95`（SkPictureRecorder → SkPDF）
- `graphics/java/android/graphics/Canvas.java`（ref `main`）：`:132-141`（`Canvas(long)` 密度=设备默认）、`:1088-1090`（clipPath=INTERSECT）、`:1605-1610`（drawBitmap 密度缩放）
- `graphics/java/android/graphics/pdf/PdfRenderer.java`（ref `android-14.0.0_r1`，main 树无此文件）：`:113,442`（`sPdfiumLock`）、`:333-346,358-363,427-436`（页坐标 pt、变换语义）
- `data/fonts/fonts.xml`（ref `main`）：`:1411-1412`（zh-Hans → NotoSansCJK-Regular.ttc）

**Skia（`https://github.com/google/skia/blob/main/<path>`，本会话实读）**
- `src/pdf/SkPDFDevice.cpp`：`:674-757`（internalDrawPath/路径效果压平/EmitPath）、`:735-737`（容差）、`:776-850`（GlyphPositioner）、`:945-957`（丢字形 early-return）、`:1003-1104`（BT/Tf/Td/Tj）、`:1055-1060`（ActualText）
- `src/pdf/SkPDFFont.cpp`：`:250-256`（can_embed/can_subset）、`:305-307`（子集前缀）、`:332-352`（FontType/Type3 条件）、`:469-482`（子集嵌入/退整字体）、`:695-930`（Type3 CharProcs）
- `src/pdf/SkPDFGraphicState.cpp`：`:24-57`（blend 降级）、`:65-102`（ca/CA/LC/LJ/LW/ML）
- `src/pdf/SkPDFGraphicStackState.cpp`：`:20-27`（fill 色 4 位小数）、`:100-150`（clip `W n`/`W* n`/pathops 失败静默）、`:151-170`（updateClip）
- `src/pdf/SkPDFUtils.cpp:343-360`（PaintPath f/f*/B/S）；`src/pdf/SkPDFUtils.h:84-101`（ColorToDecimalF 4 位、AppendScalar）
- `src/utils/SkFloatToDecimal.h:14-16`（`sscanf("%f")` 回读等于原 float）
- `src/core/SkDocument.cpp:18-30`（trim）；`src/pdf/SkPDFDocument.cpp:241-242,271-307,380-390,637-641`（rasterScale、初始矩阵、MediaBox、OutputIntent 仅 PDF/A）；`include/docs/SkPDFDocument.h:146,258`（页单位 pt）
- `src/pdf/SkPDFMetadata.cpp:78-83`（CreationDate/ModDate 仅非零时写）
- `src/ports/SkFontHost_FreeType.cpp:532-541,575-605`（fsType/可变字体/AltDataFormat 标志）

**GitHub（`gh api repos/<owner>/<repo>` 实取 2026-10-28）**
- https://github.com/google/skia （BSD-3-Clause）
- https://github.com/BigBadaboom/androidsvg （Apache-2.0；`SVG.java:409-438`、`utils/SVGAndroidRenderer.java:182-196,598-616`）
- https://github.com/TomRoush/PdfBox-Android （Apache-2.0；`PDAbstractContentStream.java:1104,1205,1220,1238,1256-1332`、`PDPageContentStream.java:1573-1597`）
- https://github.com/apache/pdfbox （Apache-2.0）
- https://github.com/typst/svg2pdf （Apache-2.0，archived，README 声明不再维护）
- https://github.com/LaurenzV/krilla （Apache-2.0）
- https://github.com/notofonts/noto-cjk （`Sans/OTC/NotoSansCJK-Regular.ttc` 首字节实读：`ttcf` + 首字体 `OTTO`）

**仓内（本会话实读）**
- `core/data/src/main/kotlin/.../visual/VisualDrawOps.kt`、`VisualDrawOpsValidator.kt`、`VisualDrawOpsPainter.kt`、`VisualDrawOpsPainterPure.kt`
- `core/data/src/androidTest/kotlin/.../visual/VisualDrawOpsPainterInstrumentedTest.kt`（`:42,53-129,133-379`）
- `core/export/src/main/kotlin/.../DeterministicMistakePdfRenderer.kt`（`:62-140`，只读参照，热区未改）
- `docs/current/visual-generation-contract.md:99,102,115,192`；`docs/known-defects.md:2869-2882`；`docs/research/2026-10-28-painter-design.md`；`docs/research/2026-10-28-batch-29-baseline.md:206-220`；`docs/research/2026-10-28-mva-remaining-order.md:64,109-117`
- `.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:1-33`

**可重跑命令（本会话实际执行过的关键几条）**
```
gh api repos/google/skia --jq '{pushed_at, archived, license: .license.spdx_id, stars: .stargazers_count}'
gh api -H "Accept: application/vnd.github.raw" "repos/aosp-mirror/platform_frameworks_base/contents/graphics/java/android/graphics/pdf/PdfDocument.java?ref=main" | sed -n '395,440p'
gh api -H "Accept: application/vnd.github.raw" "repos/google/skia/contents/src/pdf/SkPDFDevice.cpp?ref=main" | sed -n '674,760p'
gh api -H "Accept: application/vnd.github.raw" "repos/notofonts/noto-cjk/contents/Sans/OTC/NotoSansCJK-Regular.ttc?ref=main" | od -An -c -N 24
gh api -H "Accept: application/vnd.github.raw" "repos/notofonts/noto-cjk/contents/Sans/OTC/NotoSansCJK-Regular.ttc?ref=main" | tail -c +53 | od -An -c -N 8   # → O T T O
for f in SkPDFDevice SkPDFUtils SkPDFGraphicState SkPDFGraphicStackState SkPDFFormXObject; do \
  gh api -H "Accept: application/vnd.github.raw" "repos/google/skia/contents/src/pdf/$f.cpp?ref=main" | grep -ci dash; done   # → 0 0 0 0 0
gh api -H "Accept: application/vnd.github.raw" "repos/aosp-mirror/platform_frameworks_base/contents/graphics/java/android/graphics/pdf/PdfRenderer.java?ref=android-14.0.0_r1" | sed -n '388,436p'
```
