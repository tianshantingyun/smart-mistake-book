# 屏渲染与 PDF 回放一致性对照门（原型门）设计 — 批次 29 T4 驱动（R2）

日期：2026-10-28 ｜ 任务：批次 29 计划 T4 的驱动研究（`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:23`）。
输入（已完整读）：计划 §0–§3；`docs/research/2026-10-28-painter-design.md`（R1 全篇）；`core/data/.../visual/` 的 `VisualDrawOps.kt`、`VisualDrawOpsPainter.kt`、`VisualDrawOpsPainterPure.kt`、`VisualDrawOpsValidator.kt`、`VisualDrawOpsPainterInstrumentedTest.kt`；`docs/current/visual-generation-contract.md:90-106,185-198`；`docs/known-defects.md:2869-2882`（KD-78）；`docs/research/2026-10-28-batch-29-baseline.md` §3。

产出性质：**设计 + 外部来源**。本任务**未改任何 Kotlin/JS 源码**，**未运行 Gradle**，**未上设备**（门禁与设备轨由脚本串行执行）；文中一切设备侧数字都是**待实测的初始值**，不是已通过的判据。

一句话：同一个已验证的 `VisualDrawOpsDocument` 分别回放到屏 Bitmap（Skia）与 `PdfDocument` 页画布（Skia PDF 写侧），后者再经 `PdfRenderer`（PDFium 栅格化）回到同尺寸 Bitmap；门的判决只由四组**可复算的数字**（掩膜 IoU、内部逐通道色差分布、文本墨量剖面、整像素位移灵敏度）与一组**必须红的负控**给出，没有任何"看起来像"的人工判断。

---

## 0. 结论摘要

1. **两侧不是同一个栅格器**：写侧是 Skia（`PdfDocument` → `SkPDF::MakeDocument`，AOSP `PdfDocument.cpp:22,89`），栅格化侧是 PDFium（`PdfRenderer` → `FPDF_RenderPageBitmapWithMatrix`，`PdfRenderer.cpp:82-107`；构建链 `libs/hwui/Android.bp:438` 链 `libpdfium`）。因此"逐像素完全相等"是**错误判据**（跨栅格器必红，且无法归因）。本门是**相对门**：只证"PDF 路径相对屏路径没有引入新偏差"，屏侧本身的正确性由 painter 套件（设备 19 例 + JVM 10 例）负责。
2. **文本必错于逐像素**：屏侧 `textPaint = Paint()` 后**显式** `flags = REPLAY_TEXT_PAINT_FLAGS`（0，AA 关；平台 `Paint()` 构造默认是 1283，含 AA，见 KD-78 ④ 设备证据，`VisualDrawOpsPainter.kt:50-54,266-286`）；PDF 写侧把字体**规范化成 AA + 无 hinting + 线性度量**（`SkPDFFont.cpp:166-172` 的 `setBaselineSnap(false)`/`setEdging(kAntiAlias)`/`setHinting(kNone)`/`setLinearMetrics(true)`）；栅格化侧再用 PDFium 的 AA，display 模式还会追加 `FPDF_LCD_TEXT`（`PdfRenderer.cpp:85-90`，LCD 子像素彩边）。→ 文本必须用**轮廓剖面 / 质心 / 墨量**度量，不能用逐像素色差。
3. **栅格化模式选 `RENDER_MODE_FOR_PRINT`**：它只置 `FPDF_PRINTING`、**不**置 `FPDF_LCD_TEXT`（`PdfRenderer.cpp:85-90`），避免把 LCD 子像素彩边当成"PDF 与屏不一致"，这是**测量仪器的选择**，不是产品行为。
4. **归一**：两侧都落到同一像素网格（夹具 48×32 du @ 4 px/du = 192×128 px；PDF 页 48×32 pt，`PdfRenderer` 变换 `scale(4)`）。DPI 在比较中被**完全消去**——比的是像素网格，不是物理尺寸（§3.2）。
5. **度量四件**：非背景掩膜 IoU；内部（腐蚀后）逐通道最大差的分位数与超阈占比；边缘带均值；文本列/行墨量剖面 TV 距离 + 包围盒/质心/墨量比。另加**最优整像素位移**（`[-3,3]²` 搜索）作为几何位移的直接读数（§4）。
6. **分类**：F1 字体、F2 抗锯齿、F3 alpha 舍入、F4 真语义错、F5 不可归因。F5 有登记表、上限 2 个、两批到期，**不得平均掉、不得默认为通过**（§5）。
7. **门的自证**：每条负控（1 px 位移、+16 色偏、丢 clip、标签 +1 du、换字形、alpha 0.5→0.6、丢 ops）必须在同一套件里**红**；负控变绿 = 门坏了，而不是"更稳了"（§6.2）。
8. **本门不能证明的**（明写）：屏侧本身画得对；≤1/255 的 alpha 取整方向（在部分配色上会表现为 ±1 级输出，但无法与 PDFium 自身的混合取整区分，方向必须由 `drawOpsAlpha255` 纯测钉，`VisualDrawOpsPainterPure.kt:140-145`）；真导出链路（`core/export`，KD-75 热区）；跨设备/跨 API。

---

## 1. 事实闭包（全部本次实读源码或官方源）

### 1.1 屏路径（批次 28 painter，已在树）

| 事实 | 出处 |
|---|---|
| 入口 `draw(document, canvas, pxPerDu, labelContent)`；先校验 schema 与 `pxPerDu`，再 `canvas.setMatrix(scale(pxPerDu))`、`drawColor(background)`、逐 op 回放 | `VisualDrawOpsPainter.kt:69-90` |
| **`draw()` 进入绘制后的第一条动作就是 `setMatrix(rootMatrix)`**——调用方画布上的既有矩阵被替换（"根变换只做一次 du→px"的唯一执行者） | `VisualDrawOpsPainter.kt:84-87`；契约 `:100` |
| `pathPaint = Paint(ANTI_ALIAS_FLAG)`（路径 AA 开）；`textPaint = Paint()`（AA 关、仅设 textSize） | `VisualDrawOpsPainter.kt:50-51,266-272` |
| label 的 textSize 取 **du 空间值** `sizeSp × fontScale`，由根矩阵再乘 `pxPerDu` | `VisualDrawOpsPainter.kt:270` 及 KDoc `:254-265` |
| alpha 一律 floor 到 0..255：`floor(alpha*255)` | `VisualDrawOpsPainterPure.kt:140-145` |
| 颜色严格词表 `#RRGGBB(AA)` | `VisualDrawOpsPainterPure.kt:112-132`；校验器 `VisualDrawOpsValidator.kt:45` |
| clip/label 走"根空间舞步"（`setMatrix(root) → 应用 → setMatrix(mirror)`） | `VisualDrawOpsPainter.kt:115-124,196-208` |
| 校验器钉：viewBox 必须回显 viewport（`x=0,y=0,w=viewport.widthDu,h=viewport.heightDu`）；alpha∈[0,1]；dash 逐项 (0,1e5]、≤32 条（**尚未钉偶数/最小条数**）；fill/stroke 至少一个（**stroke-only 放行**） | `VisualDrawOpsValidator.kt:93-100,346,389,424-442,340-342` |

### 1.2 PDF 写侧（`android.graphics.pdf.PdfDocument`）

| 事实 | 出处 |
|---|---|
| `PdfDocument` 的 native 实现把页面内容录进 `SkPictureRecorder`，最终 `SkPDF::MakeDocument` 输出 | AOSP `libs/hwui/jni/pdf/PdfDocument.cpp:22-24,62-93` |
| 页画布坐标系是 **PostScript 点（1/72"）**，`PageInfo.Builder(pageWidth, pageHeight, pageNumber)` 的宽高即点数 | AOSP `graphics/java/android/graphics/pdf/PdfDocument.java:281-345` |
| 仓内实证：`DeterministicMistakePdfRenderer` 直接以 `PageInfo.Builder(595, 842, …)` 建页，并用 `marginPt` 量级的点坐标直接 `drawText`，无任何 density 预缩放 | `core/export/.../DeterministicMistakePdfRenderer.kt:65-89` |
| 页画布不支持：反向路径裁剪（`REVERSE_DIFFERENCE`）、`drawVertices`、color filter、mask filter、部分 XFER 模式 | AOSP `PdfDocument.java:403-425`（painter 一条都不碰：只用 `drawColor`/`drawPath`/`drawText`/单参 `clipPath`/`concat`/`save`/`restore`/`setMatrix`） |
| 文本：可嵌入字体 → Type0/CIDFont 子集；不可嵌入 / 字形路径被修改 / 变量字体 / CFF → **Type3 回退**（字形过程本身是矢量路径）；极少数无 metrics 的 typeface 直接跳过不画 | Skia `src/pdf/SkPDFFont.cpp:251-258,330-380,951-954`；`src/pdf/SkPDFDevice.cpp:933-958` |
| 写侧的字体被**规范化**：`setEdging(kAntiAlias)`、`setHinting(kNone)`、`setLinearMetrics(true)`、`setBaselineSnap(false)`、`setForceAutoHinting(false)` | Skia `src/pdf/SkPDFFont.cpp:166-172` |
| 逐字形位置：当累计 advance 与真实位置不一致（非 1000 EM 字体或 Type3）时写显式 `Td` 重定位；仅 1000 EM 且「查看器 advance 与字体一致」时按 advance 累进 | Skia `src/pdf/SkPDFDevice.cpp:795-832`（`fViewersAgreeOnAdvancesInFont` 判定 `:798-800`） |
| alpha：写进 ExtGState 的 `/ca`（填充）与 `/CA`（描边），值是浮点分量而非 8 位字节 | Skia `src/pdf/SkPDFGraphicState.cpp:75,95-96` |
| 因为字体要么嵌入子集、要么走 Type3 路径，**PDF 不依赖系统装了什么字体**；查看器层面的"字体替换"不是主要风险，风险在 Type3/路径回退与字形栅格化差异 | 由上面三行推出（**推导**，非实测） |

### 1.3 PDF 栅格化侧（`android.graphics.pdf.PdfRenderer`）

| 事实 | 出处 |
|---|---|
| native 用 **PDFium**：`#include "fpdfview.h"`、`FPDF_InitLibrary`、`FPDF_LoadCustomDocument`、`FPDF_GetLastError`；构建链 `libpdfium` | AOSP `libs/hwui/jni/pdf/PdfUtils.cpp:18-130`；`libs/hwui/Android.bp:438` |
| 渲染调用：`FPDF_RenderPageBitmapWithMatrix(bitmap, page, &transform, &clip, renderFlags)`；位图格式 `FPDFBitmap_BGRA` | AOSP `libs/hwui/jni/pdf/PdfRenderer.cpp:82-107` |
| flags：恒置 `FPDF_REVERSE_BYTE_ORDER`；`RENDER_MODE_FOR_DISPLAY` 追加 `FPDF_LCD_TEXT`；`RENDER_MODE_FOR_PRINT` 追加 `FPDF_PRINTING` | 同上 `:85-90` |
| `render(bitmap, destClip, transform, mode)`：`transform` 把**页点**映射到**位图像素**；为 null 时按 `postScale(clipWidth/width, clipHeight/height)` **纯缩放**（即左上原点、无 y 翻转）；目标位图必须 `ARGB_8888`；矩阵必须仿射 | AOSP `graphics/java/android/graphics/pdf/PdfRenderer.java:350-390,427-444` |
| 页宽高以点（1/72"）返回 | 同上 `:333-345` |
| `FPDF_LCD_TEXT` 的官方语义："Set if using text rendering optimized for LCD display. This flag will only take effect if anti-aliasing is enabled for text."；`FPDF_PRINTING`："Render for printing." | PDFium `public/fpdfview.h:798-825` |
| `FPDF_RenderPageBitmapWithMatrix` 要求矩阵可逆 | PDFium `public/fpdfview.h:908-931` |

### 1.4 两侧差异的**结构性**来源（决定哪些差异是"预期"，哪些才是缺陷）

| 差异源 | 机制 | 本门处置 |
|---|---|---|
| 路径 AA | 屏 Skia 的 `ANTI_ALIAS_FLAG` vs PDFium 自己的 AA 扫描转换，内核不同 | 归 **F2**；边缘带只报均值不判死；内部（腐蚀后）才判色 |
| 文本栅格化 | 屏：AA 关 + 度量 Paint 默认 hinting；写侧：AA 开 + hinting 关 + 线性度量；栅格化：PDFium AA（print 模式无 LCD） | 文本**不判逐像素**；用 bbox/质心/墨量/剖面 |
| alpha 路径 | 屏：`setAlpha(int)` 后 Skia 8 位混合；PDF：ExtGState 浮点 `/ca`，PDFium 自己量化并混合 | 内部色差容差 ≤2（对齐 Paparazzi `OffByTwo` 的 ±2 先例）；**取整方向不可归因**（§4.5） |
| 几何 | 两侧都走同一 `Matrix` 列序装配与同一 op 序列；PDF 多一次页点→像素变换 | 归一后必须同网格；整像素位移必须为 (0,0) |
| 裁剪 | 屏 `clipPath`（INTERSECT）vs PDF clip path（同样相交） | IoU 直接暴露 clip 丢失/错位 |
| 字体 | 嵌入子集或 Type3 路径（§1.2） | 文本类 F1；bbox/质心守住位置，质量守住字重/子集 |
| 背景/未绘制区 | PDF 内容流**不自带白底**（查看器通常自行铺白；仓内 `DeterministicMistakePdfRenderer.kt:71` 就显式 `drawColor(Color.WHITE)`）；PDFium 对未绘制像素是保留还是清空**未实测** | 判决用夹具**强制不透明 background** + 两侧先 `eraseColor`（§3.4），把该不确定性移出判决面 |

### 1.5 仓内已钉 / 未钉（本设计的边界）

- 已钉（设备 19 例 + JVM 10 例）：屏侧矩阵列序、嵌套 clip、组内 label、alpha floor、背景、状态隔离、stroke 相位、dash 残尾、fail-closed 七码；同设备同指令**逐位相等**（`VisualDrawOpsPainterInstrumentedTest.kt:133-152`）。契约 `:115` 记 17/17 绿，修复轮新增 2 例设备复跑 UNVERIFIED（批次 28 遗留①）。
- 未钉：**PDF 回放**（T3 的 `VisualDrawOpsPdfPainter` 尚未在树，计划 §1.6 落位 `core:data` visual 包）；KD-78 四缺口生成侧仍放行（`docs/known-defects.md:2869-2882`）；measure/replay flags 不在度量指纹（KD-78 ④）。

---

## 2. 外部参考与 license（只借鉴算法，不引入依赖）

`gh api` 于 2026-10-28 实取元数据；license 与维护度如下。

| 项目 | license | 维护度（实取） | 借鉴点 | 是否采纳 |
|---|---|---|---|---|
| [mapbox/pixelmatch](https://github.com/mapbox/pixelmatch) | **ISC**（SPDX 实取） | pushed 2026-10-06、7,004★、未归档 | ① 默认 `threshold=0.1`，现版本用 **OKLab HyAB** 距离（`abs(ΔLr) + √(Δa²+Δb²)`，Abasi 2019；Lr 为 Ottosson toe-corrected OKLab 亮度）；② **抗锯齿像素判定**（`antialiased()`，基于 Vysniauskas 2009 "Anti-aliased Pixel and Intensity Slope Detector"）；③ 透明像素先与棋盘/白底混合再比 | **部分采纳**：AA 判定**只用于分类**（F2），不用于判决；色差用逐通道最大差（更直白、可手算），OKLab HyAB 不引入 |
| [cashapp/paparazzi](https://github.com/cashapp/paparazzi) | **Apache-2.0** | pushed 2026-10-27、2,637★ | ① 默认 differ = **OffByTwo**：逐通道 `abs(Δ)≤2` 记相似（`internal/differs/OffByTwo.kt`）；② `DeltaE2000` differ：JND 阈值 **2.3**，`percentDifference < 5%` 记 Similar（`internal/differs/DeltaE2000.kt`）；③ `maxPercentDifferenceDefault` 默认 **0.01**（`SnapshotHandler.kt:30`） | **采纳**：±2 作为"内部色差噪声带上界"的独立先例；ΔE2000/JND 2.3 作为报告里的可选诊断量，**不进判决** |
| [romankh3/image-comparison](https://github.com/romankh3/image-comparison) | **Apache-2.0** | pushed 2026-07-30、401★ | 像素级 `threshold=5`；`pixelToleranceLevel=0.1`；差异矩形聚合 | **只作旁证**：说明"容差 + 分位数"是成熟做法；不采纳其矩形聚合（我们要的是位移读数，不是红框） |
| [google/skia](https://github.com/google/skia) `tools/skdiff` | **BSD-3-Clause** | pushed 2026-10-28 | 逐通道最大差 + 结果分类（`kEqualBits` / `kDifferentPixels` / …，`skdiff.h:87-151`） | **采纳形态**：本门的 `chanDist` 与"分类 + 计数"报告同型 |
| [aosp-mirror/platform_frameworks_base](https://github.com/aosp-mirror/platform_frameworks_base) | Apache-2.0 | — | `PdfDocument`/`PdfRenderer` 的官方实现（§1.2/§1.3） | **事实主源** |
| [chromium/pdfium](https://github.com/chromium/pdfium) | BSD-3-Clause | — | render flags 官方语义（§1.3） | **事实主源** |

**license 结论**：四个比对项目全部允许借鉴；本设计**不复制任何代码、不新增任何依赖**（AAR/stdlib 不变纪律），因此 license 只影响"能否借鉴"，不影响交付物。

**不采纳的替代方案**（并说明它消灭不了哪个失败）：

- **golden image + 逐位相等**：跨 Skia/PDFium 两套栅格器必红；红了也无法区分"PDF 路径坏了"与"两套 AA 不同"——它消灭不了"误报"，也消灭不了"无法归因"。
- **单一 SSIM/单一总分**：给不出一张"哪里错、错在哪一类"的读数；低于阈值时无法定位，正是"看起来像"的翻版。
- **只做人工目视**：不可复跑、不可量化，直接违反本任务第 4 条。

---

## 3. 对照方法

### 3.1 采集管线（每一步都可复跑）

```
fixture JSON ──(1)validate 一次──▶ VisualDrawOpsDocument（两侧共享同一对象）
                                         │
              ┌──────────────────────────┴───────────────────────────┐
              ▼                                                      ▼
 (2) 屏：Bitmap(W,H,ARGB_8888)                          (3) PDF 写：
     eraseColor(backdrop)                                   PdfDocument.startPage(pageW,pageH)
     VisualDrawOpsPainter.draw(doc, Canvas, d, resolver)    VisualDrawOpsPdfPainter.draw(doc, page.canvas, ptPerDu, resolver)
                                                            finishPage → writeTo(cacheFile)
                                                                    │
                                                          (4) PdfRenderer.openPage(0)
                                                              Bitmap(W,H,ARGB_8888)
                                                              eraseColor(backdrop)
                                                              page.render(bitmap, null,
                                                                  Matrix(scale = d/ptPerDu),
                                                                  RENDER_MODE_FOR_PRINT)
              └──────────────────────────┬───────────────────────────┘
                                         ▼
                            (5) 度量 + 分类 + 判决（§4/§5）
```

硬约束：

1. **只 validate 一次**，两侧消费同一个 `VisualDrawOpsDocument` 实例——否则门在测"JSON 解析的确定性"，而不是渲染。
2. **只用一个 label resolver 实例**，两侧返回同一份内容 DTO——否则门在测 resolver。
3. 两侧都调用**生产回放入口**：屏侧 `VisualDrawOpsPainter`，PDF 侧 T3 的 `VisualDrawOpsPdfPainter`（计划 §1.6）。门内**不得**复制 painter 逻辑，否则证明不了生产路径。
4. PDF 必须经 `PdfDocument` 真写盘 + `PdfRenderer` 真读回：`PdfRenderer` 只吃可 seek 的 fd；测试写 `InstrumentationRegistry.getInstrumentation().targetContext.cacheDir/parity/`（同 `PdfBatchImportPageRenderer.kt:54-88` 的文件生命周期形态）。

### 3.2 尺寸 / DPI 归一（本门唯一的归一规则）

| 量 | 取值 | 说明 |
|---|---|---|
| 夹具 viewBox | `48 × 32 du` | 校验器要求 viewBox 严格回显 viewport（`VisualDrawOpsValidator.kt:93-100`），故夹具的 `VisualViewport` 也是 `(48,32)` |
| 屏设备比例 `d` | `4 px/du` | 位图 `192 × 128 px`（24,576 px，远低于契约 4 MP / 16 MiB 预算，`:91`） |
| PDF 页比例 `ptPerDu`（基准） | `1 pt/du` | 页 `48 × 32 pt`（整数，`PageInfo.Builder` 只收 int） |
| `PdfRenderer` 变换 | `scale(d / ptPerDu) = scale(4)` | 页点 → 位图像素；页 48×32 pt × 4 = 192×128 px，与屏**同一像素网格** |
| 原点 | 两侧都左上 | `PdfRenderer` 无变换时是纯缩放（`PdfRenderer.java:427-437`），所以矩阵约定与 Canvas 同向，**不需要手工 y 翻转** |
| 平移 | 无 | 页内容矩形 = 整页；夹具不进 `PageInfo.contentRect` 的非零偏移 |
| DPI / density | **不参与** | 比较对象是像素网格，不是物理尺寸；设备 `densityDpi` 只进环境指纹做溯源 |
| `ptPerDu=1.5`（诊断夹具 P08） | 页 `72 × 48 pt`，`scale(8/3)` | 非整数缩放会放大边缘差；P08 **只判**最优位移 `(0,0)` 与内部色差，**不判** IoU |

**整数性前置条件**（代码里 `require`）：`viewBox.w×d`、`viewBox.h×d`、`viewBox.w×ptPerDu`、`viewBox.h×ptPerDu` 全部为整数；`d` 与 `ptPerDu` 均为有限正数。

**为什么文本也精确归一**：painter 的 textSize 取 du 空间值 `sizeSp×fontScale`（`VisualDrawOpsPainter.kt:270`），再由根矩阵乘比例——屏上设备字号 = `sizeSp×fontScale×d` px；PDF 上 = `sizeSp×fontScale×ptPerDu` pt，再被 `scale(d/ptPerDu)` 栅格化成同一 px。两侧字号**逐像素同值**，归一不留残余。

### 3.3 前置门（不通过则**没有判决**，不是"失败"）

| 检查 | 方法 | 失败码 |
|---|---|---|
| 屏侧重复确定性 | 同一 doc 渲两遍（新 painter、新 bitmap），整阵 `Arrays.equals` | `PARITY_INSTRUMENT_SCREEN_NONDETERMINISTIC` |
| PDF **栅格**确定性 | 同一 PDF 字节，`PdfRenderer` 渲两遍，整阵相等 | `PARITY_INSTRUMENT_PDF_RASTER_NONDETERMINISTIC` |
| PDF **重建**确定性 | 同一 doc 写两份 PDF（新 `PdfDocument`），各自栅格化后整阵相等 | `PARITY_INSTRUMENT_PDF_REBUILD_NONDETERMINISTIC` |
| 环境指纹 | `Build.FINGERPRINT`、`Build.VERSION.SDK_INT`、`densityDpi`、`fontScale`、以及 **replay Paint flags 指纹**（KD-78 ④：与 `VisualMeasureEnvironment.fingerprint()` 同源的单一常量/函数） | `PARITY_ENV_DRIFT`（记录并重基线，不给产品判决） |

注意：**不比较 PDF 文件字节**——PDF 内含创建时间/ID，字节不同不代表可视面不同（这是设计选择，理由写在断言旁）。

### 3.4 背景与 alpha 归一

1. 判决用夹具**一律有不透明 `background`**（如 `#ffffff`）。
2. 两侧位图在绘制前一律 `eraseColor(backdrop)`；`backdrop` = 文档 background（不透明时），否则固定 `#FFFFFFFF`。
3. 比较前把两侧像素统一 `compositeOver(backdrop)` 强制 α=255：
   `out_c = (c·a + bg_c·(255−a) + 127) / 255`（固定取整，属于**门自己的**、与渲染器无关的口径）。
4. `α≠255` 的像素计数只作诊断量 `alphaMismatchCount`，不单独判死（PDFium 对未绘制区是保留还是清空**未实测**，§8）。
5. `background:null` 只用诊断夹具 P-BGNULL，**不进判决**，直到 §8-2 被实测。

---

## 4. 度量

记号：两侧合成后的像素为 `S(x,y)`、`P(x,y)`（ARGB，α=255），`W×H` 为网格。

### 4.1 定义

| 度量 | 定义 | 用途 |
|---|---|---|
| 背景距离 | `bgDist(c) = max(abs(R_c−R_bg), abs(G_c−G_bg), abs(B_c−B_bg))` | 掩膜 |
| **非背景掩膜** | `M = { p : bgDist(p) > τ_bg }`，初始 `τ_bg = 6` | 几何 |
| **掩膜 IoU** | `IoU = size(M_S ∩ M_P) / size(M_S ∪ M_P)`；`size(M_S ∪ M_P) = 0` → `PARITY_EMPTY_MASK`；`size(M_S) < 32 px` → 夹具空转，同样拒 | 几何 |
| 逐像素色差 | `chanDist = max(abs(ΔR), abs(ΔG), abs(ΔB))`（0..255） | 颜色 |
| **内部区** | `E = erode(M_S,1) ∩ erode(M_P,1)`（4 邻域腐蚀 1 次） | 排除 AA 边缘 |
| 内部统计 | `interiorMax`、`interiorP50/P95/P99`、`interiorFracOver2 = count(p∈E, chanDist>2) / size(E)` | 颜色/alpha |
| **边缘带** | `B = dilate(M_S ∪ M_P,1) \ E` | AA 诊断 |
| 边缘统计 | `edgeMean = mean(chanDist over B)` | 分类 F2 |
| **文本带**（仅 T 类夹具声明） | 带内墨量 `mass(x,y) = max(0, 1 − luma(p)/luma(bg))` | 文本 |
| 文本指标 | `bbox`（`M∩band` 的包围盒）、`centroid`（墨量加权）、`massRatio = Σmass_P / Σmass_S`、列/行墨量剖面归一后 `TV = ½Σ abs(p−q)` | 文本 |
| **最优整像素位移** | `(dx,dy) = argmax_{[-3,3]²} IoU(M_S, shift(M_P,dx,dy))`；`iou0 = IoU at (0,0)`；`iou1 = max over the 4 neighbors of (0,0)` | 几何位移 |

说明：

- 掩膜在**背景距离**上取阈值，而不是在"是否透明"上取阈值——两侧都合成到不透明底，`α==0` 的旧判据在这里无意义。
- `τ_bg = 6` 的取法：噪声带（alpha/混合取整）实测上界取 Paparazzi `OffByTwo` 的 `≤2`（`internal/differs/OffByTwo.kt`），2.4% 覆盖率（≈6 级）以上才算"墨"；`τ_bg=6` 在噪声带之上、在 10% 覆盖率（≈25 级）之下。**初始值，必须按 §4.3 校准**。
- `interior` 用 `M_S ∩ M_P` 的腐蚀而不是只用 `M_S`：单侧腐蚀会把"PDF 多出的一块墨"整块漏进内部统计。
- 文本的 `IoU` **只报不判**（理由见 §0-2：屏侧 aliased vs PDF 侧 AA，掩膜天然差一圈 ≈ 1 px）。

### 4.2 阈值表（**初始值**，未在设备上标定）

| 夹具类 | `IoU` 下限 | `interiorMax` | `interiorFracOver2` | 文本补充 | 位移要求 |
|---|---|---|---|---|---|
| **V** 不透明矢量（无文本、无 alpha） | ≥ 0.990 | ≤ 2 | ≤ 0.2% | — | `(0,0)` 且 `iou0 − iou1 ≥ 0.02` |
| **A** 半透明填充 | ≥ 0.990 | ≤ 2 | ≤ 0.5% | — | 同上 |
| **G** 几何（clip/矩阵/stroke） | ≥ 0.990 | ≤ 2 | ≤ 0.2% | — | 同上 |
| **D** 细线/dash | ≥ 0.970 | ≤ 2 | ≤ 0.5% | — | 同上 |
| **T** 文本 | 只报 | —（`E` 为空则跳过） | — | bbox 各边 ≤ ±1 px；质心 ≤ 0.5 px；`massRatio ∈ [0.95,1.05]`；列/行 `TV ≤ 0.08` | 质心即位移读数（1 px 位移 → 质心≈1 px） |
| **G-c** 非整数缩放（P08） | 不判 | ≤ 2 | ≤ 0.5% | — | `(0,0)` |

### 4.3 校准规则（阈值不是拍脑袋，也不是一次定死）

一个阈值只有在**同时**满足下列四条时才被采纳：

1. **正例余量**：正例读数与阈值之间至少 2× 余量（如正例 `interiorMax=0`，阈值 2；正例 `IoU=1.000`，阈值 0.990 是 10× 绝对余量，按绝对差记）。
2. **负例必红**：该阈值的指定负控（§6.2）在同一版本上红。
3. **重复稳定**：同一设备连续 3 轮，读数**逐位相同**（确定性前置门已保证），否则阈值无效。
4. **落盘**：读数与阈值版本一起写进 `docs/research/evidence/2026-10-28-batch-29/` 的 step 文件；阈值变更必须同时改文档与负控。

### 4.4 几何位移敏感性（"阈值必须夹在噪声与位移之间"）

对每个几何类夹具，运行时计算 `iou0` 与 `iou1` 并断言 `iou0 − iou1 ≥ 0.02`：

- 若 `iou0 − iou1 < 0.02`，说明夹具对 1 px 位移**不敏感**（大面积填充就是这种），此时 IoU 阈值再严也不能证明几何对——夹具必须以 `PARITY_INSENSITIVE_FIXTURE` 判**门本身无效**，而不是判通过。
- 解析核对（矩形，宽 `w` px，水平 1 px 位移）：`IoU = (w−1)/(w+1)`。
  - P01 宽 24 px → 0.920；P02 宽 64 px → 0.969；P03 宽 16 px → 0.882；P05 的 8-on/8-off dash ≈ 7/9 ≈ 0.78。
  - 上表阈值（0.99 / 0.97）全部**严格高于**对应位移值 → 1 px 位移必红。
- 最优位移 `(dx,dy)` 是最直接的"几像素错位"读数；`(dx,dy) ≠ (0,0)` 且 `IoU(argmax) − iou0 > 0.02` → 直接判 F4（几何语义错），报告里带位移量。

### 4.5 为什么不用 SSIM 单一分数、也不用 ΔE2000 作判决

- **SSIM** 是结构相似度，对"整块颜色错"不敏感、对"小面积几何错"也不敏感；且它把位置误差与结构误差混成一个标量，无法归因（Paparazzi 的 `Mssim.kt` 也是把它当可选 differ，不是默认）。
- **ΔE2000** 需要 JND 常数（Paparazzi 取 2.3，`DeltaE2000.kt`），且几何位移会以"大片色差"的形式冒出来，与颜色错混淆。
- 本门用 `chanDist` + `IoU` 两个正交量，各自有直接含义（"差几级" / "错几个像素"），并且都能手算复核——这才满足"判据不得是看起来像"。ΔE2000 与 SSIM 可作为**报告诊断量**，不参与判决。
- **不可归因声明（精确版）**：1/255 的 alpha 差**并非**在所有配色上都消失——白底红块 `alpha=127` 得 `0xFFFF8080`，`alpha=128` 得 `0xFFFF7F80`，绿色通道差 1 级。所以本门**可能**看到 1 级差；但该 1 级差既可能来自"floor vs round"，也可能来自"PDFium 与 Skia 的混合取整不同"，**在仪器层面不可区分**。因此门的判据把 1 级差归入 `F3`（噪声），取整方向仍由 `drawOpsAlpha255` 纯函数单测钉死（`VisualDrawOpsPainterPure.kt:140-145`；设备面 `halfAlphaFillsFloorTheQuantizedAlpha`）。本门的 alpha 夹具只证明"量级正确"（不是差 10+）。

---

## 5. 失败分类与不可归因登记

### 5.1 判决决策树（顺序确定，无人工介入）

```
1. 前置门失败 → PARITY_INSTRUMENT_* / PARITY_ENV_DRIFT，无判决
2. |M_S ∪ M_P| = 0 或 |M_S| < 32 → PARITY_EMPTY_MASK
3. batched per fixture, class in {V,A,G,D,G-c}:
   a. IoU < floor                    → F4_GEOMETRY
   b. best-shift ≠ (0,0) 且收益>0.02 → F4_GEOMETRY（附 (dx,dy)）
   c. interiorMax > 2 或 interiorFracOver2 > class 上限 → F4_COLOR
   d. 以上都不中，但 edgeMean 显著且 E 内干净 → F2_AA
   e. E 内 0 < chanDist ≤ 2 且同号主导 → F3_ALPHA_ROUNDING
   f. 全过 → PASS
4. class == T:
   a. bbox 任一边偏离 > 1 px 或 质心 > 0.5 px → F4_GEOMETRY
   b. massRatio ∉ [0.95,1.05] 或 剖面 TV > 0.08 → F1_FONT（字重/子集/路径回退）
   c. 全过，仅 IoU/edgeMean 大 → F2_AA（预期）
   d. 全过 → PASS
5. 不满足上述任何一条、但读数落在**隔离带**（§5.3）→ F5_UNATTRIBUTED
6. 读数超出全部阈值且不属于隔离带 → F4_SEMANTIC（默认"真错"，不允许降级成 F5）
```

### 5.2 五类定义（码面与判据）

**关于"字体替换"**：Skia 写侧要么嵌入字体子集（Type0/CIDFont）、要么把字形降级成 Type3 矢量过程（§1.2），PDF **不引用系统字体名**——所以"查看器找不到字体→替换"在本管线里**预期不发生**。本条 F1 实际抓的是"同一字形的**覆盖差异**"：子集不完整、CJK fallback 走了 Type3、字重/宽度不同。判据不依赖归因文案（文本四指标），将来若改走第三方 PDF 写侧，只改归因说明、不改判据。

| 类 | 含义 | 判据（全部可测） | 处置 |
|---|---|---|---|
| **F1** `F1_FONT` | 同一内容 DTO，PDF 侧字形覆盖率/轮廓不同（子集、Type3 回退、缺字、字重不同；"替换"预期不发生） | 文本夹具：bbox 与质心**合格**，但 `massRatio` 或列/行剖面 `TV` 超限 | 记入证据；若是 CJK 缺字 → 真缺陷（打开 KD）；若只是轮廓差 → 容差内观察 |
| **F2** `F2_AA` | 差异局限在 1 px 边缘带 | `E` 内全过；`B` 带 `edgeMean > 0`；文本的 bbox/质心/墨量/剖面全过 | **通过**（预期差异），但 `edgeMean` 进报告 |
| **F3** `F3_ALPHA_ROUNDING` | 内部出现**同号**的 ≤2 级差 | `0 < interiorMax ≤ 2`；`interiorFracOver2 ≤ 上限`；≥60% 的非零差同号 | 通过（记录）；若某夹具系统性出现，说明两侧混合器取整不同，登记为仪表差异 |
| **F4** `F4_GEOMETRY` / `F4_COLOR` / `F4_SEMANTIC` | 真语义错：丢 op/丢 clip/矩阵错/clip 根空间错/stroke 双乘/颜色错/alpha 量级错 | IoU < 下限；或 best-shift≠(0,0)；或 interiorMax > 2；或 bbox/质心超限 | **红**；报告带度量、位移、首个超阈像素坐标与两侧 ARGB |
| **F5** `F5_UNATTRIBUTED` | 读数越界但**无法**归入 F1–F4，且落在隔离带 | §5.3 | 登记、阻断门的升格（§7），不阻断构建 |

### 5.3 `F5_UNATTRIBUTED` 的登记口径（**不可归因 ≠ 忽略**）

**隔离带**（每夹具类各自定义；只有落在带内才允许 F5）：

| 类 | IoU | interiorMax | 文本 |
|---|---|---|---|
| V/A/G | `[floor−0.01, floor)` | `(2, 8]` **且** `interiorFracOver2 ≤` 该类 `interiorFracOver2Limit` | — |
| D | `[0.96, 0.97)` | `(2, 8]` **且** `interiorFracOver2 ≤` 该类上限 | — |
| T | — | — | bbox 偏离 `(1,2]` px 或 `massRatio ∈ [0.90,0.95)∪(1.05,1.10]` |

> **复核第 1 轮 R8 更正**：色差隔离带原有 `interiorMax ∈ (2,8]` 单条件会让"整块填充统一偏 3~8 级"（IoU≈1、无位移）落进 F5，从而以"不可归因"姿态放行系统性错色。现加占比条件：隔离带只吸收**少量孤立像素**的幅度越界，`interiorFracOver2` 超过夹具类上限（整块偏色 ≈1）保持 `F4_COLOR` 红。设备判据见 `VisualDrawOpsParityInstrumentedTest.n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5`。

**登记表**（每批一次，落 `docs/research/evidence/2026-10-28-batch-29/` 或后续批同名 step）：

| 字段 | 要求 |
|---|---|
| `id` | `F5-<批>-<序号>` |
| `fixture` / `class` | 夹具 id 与类 |
| `metrics` | 全套读数：`IoU / interiorMax / p95 / p99 / frac>2 / edgeMean / bestShift / 文本四指标` |
| `thresholdVersion` | 阈值表版本（本文件 §4.2 的编号） |
| `env` | `Build.FINGERPRINT`、API、`densityDpi`、`fontScale`、replay Paint flags 指纹、`d`、`ptPerDu` |
| `hypothesis` | 一句话假设（必须可证伪；"渲染器不同"这类不可证伪的说法不收） |
| `owner` / `from-batch` / `expiry` | 负责人、登记批、**到期批 = 登记批 + 2** |
| `status` | `open` → `resolved(如何解决的)` / `promoted(KD-xx)` / `fixture-narrowed(为何)` |

**硬规则**：

1. 每轮最多 **2 个** F5；出现第 3 个 → 系统性漂移，门整体红（不允许"多登记几个就绿"）。
2. F5 **不计入 PASS**，也不参与平均值；最终报告必须逐条列出。
3. 到期未处置 → 门红；处置只有三条合法路径：给出可证伪的修复并转绿 / 升级为 KD 条目 / 收窄或删除夹具（必须写明它不再消灭哪个失败）。
4. 环境指纹变化（换设备/换 API/换 fontScale）不是 F5，是 `PARITY_ENV_DRIFT`——先重基线，再谈判决。

---

## 6. 判据不得是"看起来像"：阈值、样例、负控

### 6.1 夹具表（P01–P08 判决/诊断 + 1 个不进判决的诊断夹具）

`d = 4 px/du`、`ptPerDu = 1`、网格 192×128 px。全部 `background:"#ffffff"`（P03 等原夹具的 `null` 在本门一律改白，理由 §3.4）。

| id | 类 | 内容（du 坐标） | 手算落点（px） | 被谁守住 |
|---|---|---|---|---|
| **P01** | V | 矩形 (4,2)-(10,8)，填充 `#ff0000` | (16,8)-(40,32)，内部探针 (32,20)=`#FFFF0000` | 不透明填充颜色 + 几何 |
| **P02** | A | 矩形 (4,4)-(20,12)，填充 `#ff0000`，`alpha:0.5` | (16,16)-(80,48)，中心 (48,32)=`#FFFF8080`（floor(0.5·255)=127，白底手算 r=255,g=b=128） | alpha 量级 |
| **P03** | G | 嵌套 clip（同 `DOC_NESTED_CLIP`）：G1(scale2) ⊃ G2(translate+2du, clip du (4,0)-(12,8))，路径局部 (0,0)-(8,8) | 交 (8,0)-(24,16) 有墨；绘制区 (8,0)-(40,32) | clip 根空间舞步 |
| **P04** | G | 切变 `[1,1,0,1,0,0]`，局部矩形 (2,1)-(6,5) | 平行四边形，面积 16 du²=256 px；顶宽 4du=16px | 矩阵列序 |
| **P05** | D | 横线 du y=28、x 4..44，stroke 宽 1du，`dash:[2,2]` | 4 px 带宽；8 px on / 8 px off | dash 形状与装配 |
| **P06** | T | 单行 label `"H"`，`(x,y)=(15,20)`，sizeSp 16，fontScale 1，heightDu 12；色 `#26354a` | 基线 y=80 px，带 x∈[56,120]、y∈[24,84] px | 文本位置/字重/AA |
| **P07** | T | 单行 label `"标"`（CJK，走 fallback 字体与嵌入） | 同上带 | CJK 子集/Type3 回退 |
| **P08** | G-c | P03 的 JSON，`ptPerDu=1.5`（页 72×48 pt，scale=8/3） | 同 P03 的 du 落点 | 非整数缩放下的位移/颜色（不判 IoU） |
| P-BGNULL | 诊断 | `background:null` 的空 ops | 只观察 PDFium 未绘制区行为 | **不进判决**（§8-2） |

**样例 1（P01 完整 JSON，可直接进 `VisualDrawOpsValidator.validate`，viewport `(48,32)`）**：

```json
{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},"background":"#ffffff",
 "ops":[{"op":"path","path":[["M",4,2],["L",10,2],["L",10,8],["L",4,8],["Z"]],
         "fill":"#ff0000","stroke":null,"alpha":1}],
 "hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}
```

**样例 2（P02，alpha）**：

```json
{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},"background":"#ffffff",
 "ops":[{"op":"path","path":[["M",4,4],["L",20,4],["L",20,12],["L",4,12],["Z"]],
         "fill":"#ff0000","stroke":null,"alpha":0.5}],
 "hitRegions":[],"stats":{"nodes":0,"pathCommands":5,"labels":0,"ops":1}}
```

**样例 3（P03，嵌套 clip；同 `VisualDrawOpsPainterInstrumentedTest.kt:477-482`，仅把 background 改白）**：

```json
{"drawSchemaVersion":1,"viewBox":{"x":0,"y":0,"w":48,"h":32},"background":"#ffffff",
 "ops":[{"op":"save"},{"op":"concat","m":[2,0,0,2,0,0]},{"op":"save"},
        {"op":"clip","path":[["M",4,0],["L",12,0],["L",12,8],["L",4,8],["Z"]],"rule":"nonzero"},
        {"op":"concat","m":[1,0,0,1,2,0]},
        {"op":"path","path":[["M",0,0],["L",8,0],["L",8,8],["L",0,8],["Z"]],
         "fill":"#00aa00","stroke":null,"alpha":1},
        {"op":"restore"},{"op":"restore"}],
 "hitRegions":[],"stats":{"nodes":2,"pathCommands":10,"labels":0,"ops":8}}
```

P02 的手算（写进断言注释）：`srcA=floor(0.5×255)=127`；白底：
`out_r = (255×127 + 255×128 + 127)/255 = 255`，`out_g = (0 + 255×128 + 127)/255 = 128.49 → 128`，故中心像素 `0xFFFF8080`。若实现改成 `round`（srcA=128），中心会变成 `0xFFFF7F80`（差 1 级，落进 F3 噪声带，§4.5），所以**这条断言只钉量级，不钉取整方向**。

### 6.2 负控（每条都必须在同套件里**红**；红了才证明门有分辨力）

负控分两种：**改栅格**（验证度量本身灵敏）与**改 Playback 输入**（验证门能抓住真回放缺陷）。

| id | 施加对象 | 操作 | 期望读数额 | 期望判定 |
|---|---|---|---|---|
| **N01** | PDF 栅格 | 整体平移 +1 px | P01 `IoU=0.920`、P02 `0.969`、P03 `0.882`、P05≈`0.78` | 全红（F4_GEOMETRY，best-shift=(1,0)） |
| **N02** | PDF 栅格 | 单一通道 +16 | `interiorMax=16` | 红（F4_COLOR） |
| **N03** | PDF 输入 | 删掉 `clip` op | P03：墨从 256 px 变 1024 px，`IoU=0.25` | 红（F4_GEOMETRY） |
| **N04** | PDF 输入 | label `x` +1 du（=+4 px） | P06 bbox 左缘偏 4 px | 红（F4_GEOMETRY） |
| **N05** | PDF 输入 | payload `"H"` → `"M"` | P06 剖面 `TV` 显著、bbox 宽变化 ≥4 px | 红（F1_FONT 或 F4；分类允许二者之一，但必须红） |
| **N06** | PDF 输入 | `alpha 0.5 → 0.6` | P02 内部 `Δ=26`（白底红块 g: 128→102） | 红（F4_COLOR） |
| **N07** | PDF 输入 | `ops: []`（保留背景） | `count(M_P)=0` → `IoU=0`（或 `PARITY_EMPTY_MASK`） | 红 |

注：N05 是唯一"分类二选一"的负控——因为"同一内容 DTO 下字形不同"与"位置对但轮廓不同"在信息上确有重叠；门只要求**判红**，分类以 `bbox` 是否合格作为分水岭（§5.1-4）。

### 6.3 复跑命令、报告行与产物

**复跑**（与批次 28 设备轨同型，`docs/research/evidence/2026-10-28-batch-28/step03-painter-smoke.md:30-36`）：

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

**每夹具一行报告**（logcat + 失败断言消息同形，机器可解析、人可手算复核）：

```
PARITY fixture=P03 class=G iou=1.0000 bestShift=(0,0) iou1=0.8820
       interiorMax=0 interiorP99=0 frac2=0.0000 edgeMean=11.7
       text=- verdict=PASS thr(iou>=0.990, interMax<=2, frac2<=0.002, shift=(0,0), dIoU>=0.020)
```

失败行的额外字段：`firstOverPx=(x,y) S=0xAARRGGBB P=0xAARRGGBB` 与 `metrics` 全量。

**产物**：只在**失败**时写 `context.cacheDir/parity/<fixture>.{screen,pdf,diff}.png` 供人看；**判决永不读 PNG**（读的是 `IntArray`），PNG 只是诊断附件。成功轮零产物（不污染设备）。

### 6.4 门自身也被测试

- 度量函数放在 **androidTest 源集**（`core/data/src/androidTest/.../visual/`），不进产品源码：它是测试仪器，不是产品行为；把 IoU/腐蚀/剖面塞进 `main` 会为原型门扩产品面（§7 定位）。
- 它的正确性由 N01–N07 在真栅格上证明（比 JVM 单测更强的证据面），并额外有两条纯算术断言：`IoU` 分母用并集（用交集当分母时 N01 不会红）、腐蚀边界不越界。
- 负控与正例在同一 `@Test` 类里；任何一个负控变绿 → 该类红（`PARITY_GATE_DESENSITIZED`）。

---

## 7. 原型门的定位与升格条件

**现在是什么**：批次 29 T4 的设备级对照用例 + 本设计。它是**原型门**（计划 §1.7 明令）：

- 不接入 CI、不作发版门、不作"PDF 可用"的证据；
- 阈值是标定阶段的初始值；每次读数必须同时报"阈值版本"；
- 相对门：只证 PDF 路径相对屏路径没有新偏差；屏侧正确性由 painter 套件负责。

**升格为产品门还缺什么**（全部满足才谈）：

1. 阈值在 ≥3 轮 × ≥2 API 级别上标定，且负控全红、F5 为 0；
2. 覆盖 3 档 fontScale（1 / 1.3 / 2）与生产 `ptPerDu`（目标栏宽），而不是基准 1；
3. 走**真导出链路**（`DeterministicMistakePdfRenderer` / `core/export`，KD-75 解禁后），而不是测试本地 `PdfDocument`——否则分页/版式变量没进门的覆盖；
4. 对"文本 AA 关 vs 开"做出产品裁定（当前屏侧 AA 关是 MVA 视觉取舍，两侧 AA 不同是文本类差异的主因）；
5. 明确 `ptPerDu` 与页面尺寸的整数性策略在生产参数下依然成立（否则非整数缩放会污染阈值）。

---

## 8. 未验证项（UNVERIFIED，不作为事实引用）

1. **PDFium 的 alpha 混合取整**：只从 Skia 写侧确认 `/ca` 是浮点（`SkPDFGraphicState.cpp:75,95-96`），PDFium 内部如何量化与混合未读实现、未实测；因此 `interiorMax ≤ 2` 是初始容差，不是推导值。
2. **PDFium 对未绘制/透明区的处理**（保留旧像素、清零、还是填白）：未实测；设计用"强制不透明 background + 两侧 `eraseColor`"绕开，`P-BGNULL` 只观察不判决。
3. **`FPDF_PRINTING` 是否在 PDFium 内部同时关闭 LCD 文本**：只确认 `PdfRenderer` 在 print 模式下**不置** `FPDF_LCD_TEXT`（`PdfRenderer.cpp:85-90`）；内部行为未读。
4. **设备上的实际读数与阈值标定**：本任务未上设备，§4.2 全部是初始值。
5. **T3 的 `VisualDrawOpsPdfPainter` 尚不在树**：落位按计划 §1.6（`core:data` visual 包）；签名 `draw(doc, page.canvas, ptPerDu, resolver)` **是本设计的假设**，若 T3 最终以别的入口提供，§3.1 第 3 步按实际入口替换，其余不变。
6. **KD-78 ③ 的公式域**：计划 §1.2 写 `effectiveAlpha = floor(colorA/255 × op.alpha)` 后 `setAlpha`（0..255）。本设计**不重算该式**——门的期望颜色必须调用 T5 落码的**同一纯函数**（单一真值源），否则门自己会变成第二套公式（正是本门要消灭的失败）。
7. **Skia 屏侧 `Paint` 的默认 hinting 值**：只确认写侧规范化为 hinting 关（`SkPDFFont.cpp:166-172`），屏侧默认值未查；文本差异的归因不依赖它（两侧 AA 不同已足够解释 F2）。
8. **CJK fallback 在 PDF 写侧是否总能拿到可嵌入字体**：由 `SkPDFFont.cpp` 的嵌入/Type3 规则推出"不会引用系统字体名"，但未在设备上验证 CJK 子集是否完整。

---

## 9. 来源

**外部（本会话 `gh api` 实取源码/元数据，2026-10-28）**

- pixelmatch：https://github.com/mapbox/pixelmatch —— `index.js`（`threshold=0.1`、OKLab HyAB、`antialiased()`/Vysniauskas 2009）；license ISC、7,004★、pushed 2026-10-06。
- Paparazzi：https://github.com/cashapp/paparazzi —— `paparazzi/src/main/java/app/cash/paparazzi/internal/differs/{OffByTwo,DeltaE2000,Mssim,PixelPerfect}.kt`、`SnapshotHandler.kt:30`、`SnapshotVerifier.kt:112-125`；license Apache-2.0、2,637★、pushed 2026-10-27。
- image-comparison：https://github.com/romankh3/image-comparison —— `ImageComparison.java`（`threshold=5`、`pixelToleranceLevel=0.1`）；license Apache-2.0、401★、pushed 2026-07-30。
- Skia：https://github.com/google/skia —— `src/pdf/SkPDFDevice.cpp:795-1010`（`writeGlyph`、`drawGlyphRunAsPath`、`internalDrawGlyphRun`）、`src/pdf/SkPDFFont.cpp:152-175,250-380,951-954`、`src/pdf/SkPDFGraphicState.cpp:75-96`、`tools/skdiff/skdiff.h:87-151`；license BSD-3-Clause、11,007★、pushed 2026-10-28。
- AOSP（`aosp-mirror/platform_frameworks_base`；`PdfRenderer.java`/`PdfRenderer.cpp` 在 `main` 镜像已 404，故取 `android-14.0.0_r1`；`PdfDocument` 的 main 与 14 分支内容一致）：
  - `libs/hwui/jni/pdf/PdfDocument.cpp:22-93`（`SkPDF::MakeDocument`）
  - `libs/hwui/jni/pdf/PdfRenderer.cpp:72-107`（`FPDF_RenderPageBitmapWithMatrix` + flags）
  - `libs/hwui/jni/pdf/PdfUtils.cpp:18-130`（PDFium 初始化/错误面）
  - `libs/hwui/Android.bp:438`（`libpdfium`）
  - `graphics/java/android/graphics/pdf/PdfDocument.java:281-345,398-425`
  - `graphics/java/android/graphics/pdf/PdfRenderer.java:299-390,427-444`
- PDFium：https://github.com/chromium/pdfium —— `public/fpdfview.h:798-825,908-931`（`FPDF_LCD_TEXT` / `FPDF_PRINTING` / `FPDF_RenderPageBitmapWithMatrix`）。

**仓内（本会话实读，行号见正文）**

- `.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md`（§0–§3）
- `docs/research/2026-10-28-painter-design.md`（§2–§4、§5）
- `docs/research/2026-10-28-batch-29-baseline.md`（§2.1、§3）
- `docs/current/visual-generation-contract.md:90-106,185-198`
- `docs/known-defects.md:2869-2882`（KD-78）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOps.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPure.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsValidator.kt`
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/capture/PdfBatchImportPageRenderer.kt`
- `core/export/src/main/kotlin/com/tingyun/smartmistakebook/core/export/DeterministicMistakePdfRenderer.kt`
- `docs/research/evidence/2026-10-28-batch-28/step03-painter-smoke.md`（复跑命令形态）
