# 批次 29 记录（2026-10-28）：PDF 回放半场 + 屏/PDF 一致性门雏形 + KD-78 收口 + 锁窗口再判定

> 记录时点：2026-10-28（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、三份研究文档、T1 判据文档、`docs/research/evidence/2026-10-28-batch-29/` 七份 E-file、`build/device-track-batch29/` 原始日志、`core/data` JVM 结果 XML、`git status` 与关键源码锚点后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改任何 Kotlin/JS 产品代码。** 本记录员本轮唯一测试执行 = Node 编译器测试台（`node tools/visual_compiler/run_tests.mjs`，`94 passed, 0 failed`，EXIT=0）。
> 计划：`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md`（下称"计划"，33 行已完整读）。
> 基线：`docs/research/2026-10-28-batch-29-baseline.md`（下称"基线"；301 行，只读核对）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 2 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；修复环第 1 轮全部非低项处置完成（flags 假设【高】已修；PDF fail-open【高】已修并新开 KD-79；门序不一致【中】已对齐；F5 隔离带【中】已收紧；`drawRendered` 零判据【中】已补两例）。
> **本记录不声称"批次全绿"**：**修复后的设备复跑全部 UNVERIFIED**——设备证据的 8 个套件是在修复轮**之前**的树上取得（painter 21 例 1 红、隔离 13 例 12 红、flags 2 例 1 红、PDF 13 例 1 红）；修复后现树 painter 24 例、一致门 11 例、PDF 13 例（写侧已改）、app flags 2 例、隔离 13 例**均未重新上设备**。**KD-76/KD-78/KD-79 全部 open**；KD-72/75/77 open；4E.2 MVA 仍不成立；arm64/真机、API 23、生产 release 签名、真实 Provider/模型质量 UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1/§2）与实际执行面

- **做**：R1 PDF 回放差异研究（`PdfDocument` page canvas 的 alpha/clip/dash/文本/字体，GitHub 参考）；R2 屏/PDF 一致性对照门设计（方法、容差、失败分类）；R3 锁窗口再判定 + 批次 30 前置 seam + R-04 路径可行性；T1 设备判据核验；T2 S-04 解析器（`Rendered.drawCommandsJson` → DrawOps V1 类型化文档 + 往返）；T3 `VisualDrawOpsPdfPainter`；T4 一致性门雏形；T5 KD-78 四缺口收口；T6 台账/契约同步；T7 热区条件轨；设备轨；证据；双门；双复核；修复环；记录；提交推送。
- **实际执行面**：三研究线落盘 + 七实施轨（T1–T7）+ 集成交接核验 + 设备轨（6 步串行）+ 证据落盘（7 个 `.md`）+ 复核与修复环 + 本记录与四份台账同步。
- **不做（全部未做）**：core/export 集成与 PDF `/AIGC`（KD-75 热区）；持久化四表/CAS/冷恢复（批次 30，本批只列 seam）；双后端装配（批次 31）；V-01 全链（批次 32）；锁文件生成（窗口判定为分支 B）；JS 广告；真实 Provider；热区 UI。

### 1.2 主会话裁定（计划 §1，本批执行，未改）

1. **hitRegions**：允许非空并由编译器透传，painter **不绘制**；准入清单"必须为空/不存在"作废。
2. **8 位色 alpha**：`effectiveAlpha = floor(colorA/255 × op.alpha)` 的计划字面与实际落码/契约的 `floor(colorA × op.alpha)`（0..255 域）**不同形但同义**（契约写明了两种写法等价）；落码与契约为准（见 §5 #13）。
3. **dash 形状门**：生成侧两门拒绝奇数或 `<2` 条（`PAINTER_DASH_ODD`），回放侧兜底保留。
4. **stroke-only**：生成侧拒绝无 `fill` 的 stroke（`PAINTER_STROKE_WITHOUT_FILL`），回放侧兜底保留。
5. **flags 指纹**：度量环境指纹加 Paint flags；两侧显式装配同一值（修复轮把"保持默认"改成"显式 0"）。
6. **PDF 回放落位**：`core:data` visual 包，**不进 core/export**。
7. **一致性门是雏形**：允许容差，标签"原型门"，**不得当作产品门/发版门**。

### 1.3 基线摘要（记录时点实测）

- **HEAD**：`3d9372e5`（`git rev-parse --short HEAD` 实测）= `origin/main` **本地跟踪引用**（实测同值；`git rev-list --left-right --count origin/main...HEAD` = `0 0`）。R3 记录显示批次 28 的推送重试**已落**（本地 remote-tracking 读数；未 `git fetch`，远端实时 hash UNVERIFIED）。计划 §0 把 HEAD 写成 `c93241d5`，实际已是其子提交 `3d9372e5`（纯文档 +42 行），与基线 §11-1 一致。
- **工作树**：**703 行 = 215 M + 488 ??，staged 0**（记录时点、本记录写盘前实测）；基线快照为 673 = 201 M + 472 ??。增量 = 本批落点（见 §1.4）。**本记录与三份台账写盘后实测 707 = 218 M + 489 ??**（+3 M：`REFACTOR-MASTER-LINE.md`、`algorithm-version-ledger.md`、`agent-first-refactor-decisions-2026-09-23.md`；+1 ??：本文件），staged 仍 0。
- **版本串（记录员本轮 grep 复核，全部未动）**：DB schema **69**（`StudyDatabase.kt:134`）、请求 schema **18**（`ModelTasks.kt:546`）、`PROTOCOL_VERSION` **2**（`VisualJsProtocol.kt:32`）、`drawSchemaVersion` **1**；AAR `quickjs-kt-android:1.0.15` 与 stdlib 2.3.21 不变。算法台账 §3.39 零 bump（本记录员登记）。
- **落点基线**：`core/data/src/{main,test,androidTest}/.../visual` 开工时干净（基线 §3）；现树该包 **16 条** = **9 新增**（`VisualDrawOpsParser.kt`、`VisualDrawOpsPainterRendered.kt`、`VisualDrawOpsPdfPainter.kt`、`VisualDrawOpsPdfPainterPure.kt`、`VisualDrawOpsParserTest.kt`、`VisualDrawOpsPdfPainterPureTest.kt`、`VisualDrawOpsPdfPainterInstrumentedTest.kt`、`VisualDrawOpsParityMetrics.kt`、`VisualDrawOpsParityInstrumentedTest.kt`）+ **7 修改**（`VisualDrawOpsPainter.kt`、`VisualDrawOpsPainterPure.kt`、`VisualDrawOpsValidator.kt`、`VisualDrawOpsPainterPureTest.kt`、`VisualDrawOpsValidatorTest.kt`、`VisualDrawOpsPainterInstrumentedTest.kt`、`QuickJsSceneCompilerInstrumentedTest.kt`）。
- **落点清单外的本批改动（计划 §1.5 授权，须整文件纳入）**：`app/.../visualjs/VisualContentMeasuring.kt`（M）、`app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（M）、`app/src/androidTest/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt`（新增），以及 `core/data/src/main/resources/visual/visual-compiler.js`（M）、`tools/visual_compiler/run_tests.mjs`（M）。
- **热区**（记录时点实测，仍脏）：feature/library **43**、feature/tutor **59**、feature/review **12**、feature/capture **16**、feature/profile **4**、core/ui **35**、core/markdown **1**（整目录未跟踪）、core/export **6**、core/domain **5**、`settings.gradle.kts`/`.gitignore` M；core/data 他线 M 未触碰。本批零越权。

### 1.4 本批增量点名

- 修改（10，`git diff --numstat` 实测 +/−）：`VisualDrawOpsPainter.kt`（+38/−15）、`VisualDrawOpsPainterPure.kt`（+52/−11）、`VisualDrawOpsValidator.kt`（**+15/−510**，结构解析下沉为薄委托，净删 495 行）、`VisualDrawOpsPainterPureTest.kt`（+39/−0）、`VisualDrawOpsValidatorTest.kt`（+61/−1）、`VisualDrawOpsPainterInstrumentedTest.kt`（+187/−45）、`QuickJsSceneCompilerInstrumentedTest.kt`（+1/−1，夹具补 fill）、`visual-compiler.js`（+14/−0）、`VisualContentMeasuring.kt`（+67/−5）、`VisualContentMeasuringTest.kt`（+28/−0）；另 `tools/visual_compiler/run_tests.mjs`（+32/−1）。
- 新增代码/测试（10）：Parser(853)、PainterRendered(38)、PdfPainter(154)、PdfPainterPure(92)、ParserTest(517 行/15 例)、PdfPainterPureTest(110/6)、PdfPainterInstrumentedTest(523/13)、ParityMetrics(853)、ParityInstrumentedTest(718/11)、VisualMeasureReplayPaintFlagsInstrumentedTest(67/2)。
- 文档：三研究线 + 基线 + T1 判据 + 证据 7 份 + 本记录 + 台账同步四份。

## 2. 三研究线

### 2.1 R1 PDF 回放差异研究（`docs/research/2026-10-28-pdf-replay-differences.md`，记录员实测 405 行）

- **核心事实（§0/§1）**：`PdfDocument.Page.getCanvas()` 不是 PDF 画布，而是 `SkPictureRecorder` 记录画布；直到 `writeTo()` 才 `SkPDF::MakeDocument → beginPage → drawPicture`（AOSP `libs/hwui/jni/pdf/PdfDocument.cpp:24,35,72,82,89-95`）。因此 PDF 语义 = **Skia SkPDF 后端语义**。
- **T3 直接采用的五条**：坐标同构只换单位（pt，左上原点、y 向下，初始 `scale(1,-1)`）；页尺寸 int pt 且取 **ceil**；clip 只有求交（nonzero `W n` / evenodd `W* n`），逆填充不支持但词表外；**dash 在 PDF 被压平成填充几何**（不写 `d` 算符，`SkPDFDevice.cpp:691-706`，容差 `0.0625/matrixScale`）；alpha 由 ExtGState `/ca` `/CA` 取 `Paint` 颜色浮点 alpha，两端自动同值。
- **文本边界（§2.4/§5.1）**：TrueType 子集嵌入（CIDFontType2）；**CFF/可变字体/fsType 禁嵌 → 强制 Type3 字形轮廓**；系统中文 `NotoSansCJK-Regular.ttc` 上游首字体 sfnt 标签经实读为 `OTTO`（CFF）→ 中文 label 预期落 Type3。静默丢字形是真实失败面（`SkPDFDevice.cpp:950-957`），因此必须有"应墨处 ink>0"硬判。
- **AA 不可比（§2.5）**：内容流无 AA 参数，`PdfRenderer` 实际跑 **PDFium**（`PdfRenderer.java:113,442`），与屏 Skia 是两套栅格器 → "整图像素相等"是**设计上必然红**。
- **对照组取舍（§6.1）**：明确否掉 G1 指令序列相等（比的是同一份输入，恒真）、G2 内容流解析（需新依赖 + Flate 压缩）、G3 整图像素相等；采用 **G4 结构探针 + 归一化光栅对拍**。
- **允许/不可消除差异（§4/§5）**：20 行判定表，逐项标"等/容差/不可比"；边界=内部像素 ≤±2/255、边缘 2px 带任意差、bbox 位移 >2px 硬失败、单设备主张（跨设备只主张 PDF 自包含）。
- **GitHub 评分（§3）**：skia（BSD-3，事实源）、androidsvg（Apache-2.0，"渲染器只认 Canvas"先例）、PdfBox-Android（Apache-2.0，停滞）、apache/pdfbox、svg2pdf（archived，拒）、krilla（Apache-2.0）。**只借模式、零依赖**。
- **UNVERIFIED（§9）**：设备侧全部未实测；设备 ttc 格式未读；`MAX_PAGE_POINTS=14400` 是自定预算非规约条文；规范原文未直读；AOSP/Skia 经 `gh api` 单一通道取得（`developer.android.com` 与 `googlesource` 本轮 443 超时），**未做第二通道交叉核证**。

### 2.2 R2 屏/PDF 一致门设计（`docs/research/2026-10-28-screen-pdf-parity-gate.md`，记录员实测 477 行）

- **定位（§0）**：相对门——只证"PDF 路径相对屏路径没有引入新偏差"；屏侧正确性由 painter 套件负责。文本必错于逐像素（屏 AA 关 vs PDF 写侧规范化 AA vs PDFium AA），栅格化模式选 `RENDER_MODE_FOR_PRINT`（避开 `FPDF_LCD_TEXT`）。
- **归一（§3.2）**：夹具 48×32 du；屏 `d=4 px/du` → 192×128；PDF `ptPerDu=1` → 48×32 pt，`PdfRenderer` `scale(4)` → 同一网格；DPI/density 不参与比较。
- **度量（§4）**：非背景掩膜 IoU、腐蚀后内部逐通道最大差分布、边缘带均值、文本 bbox/质心/墨量比/行列剖面 TV、`[-3,3]²` 最优整像素位移；背景距离阈值 `τ_bg=6`；内部容差 ±2（Paparazzi `OffByTwo` 先例）；位移灵敏度 `iou0−iou1≥0.02`。
- **阈值（§4.2）**：V/A/G IoU≥0.990、D≥0.970、文本只报 IoU；`interiorMax≤2`、`interiorFracOver2` V/G 0.2% / A/D 0.5%；文本 bbox ±1px、质心 ≤0.5px、`massRatio∈[0.95,1.05]`、TV≤0.08。**全部是未标定的初始值**，附四条校准规则（正例 2× 余量、负控必红、三轮逐位稳定、落盘）。
- **分类（§5）**：F1 字体 / F2 抗锯齿 / F3 alpha 舍入 / F4 真语义错 / F5 不可归因；F5 有隔离带、登记表、**每轮上限 2 个**、登记批 +2 到期、到期未处置即门红。
- **负控（§6.2）**：N01–N07 必须红（+1px 位移、单通道 +16、删 clip、label +1du、换字形、alpha 0.5→0.6、丢 ops）；负控变绿=门坏了。
- **边界（§7/§8）**：不接 CI、不作发版门；升格为产品门还缺 ≥3 轮 × ≥2 API 标定、3 档 fontScale、真导出链路（KD-75 解禁）、文本 AA 产品裁定；PDFium 混合取整/未绘制区/flags 内部行为均未实测。

### 2.3 R3 锁窗口/批次 30 前置/R-04（`docs/research/2026-10-28-lock-window-and-batch30-prep.md`，记录员实测 254 行）

- **锁窗口判定 = 分支 B（不可行）**：复跑 LP §1 的 J1–J4——J1 六文件入库**红**（5 M + `?? core/markdown/build.gradle.kts`；`git cat-file` 证其不在 HEAD；HEAD 侧 `richtext.commonmark` 仍存在、`kotlinx.serialization.json` 未引入）、J2 暂存区无锁**绿**、J3 干净树**红**（**674→676** 两次实测，他线在飞、计数在动）、跟踪锁 **0**、盘上 **14** 个锁全未跟踪、git 可见未跟踪锁条目 **13**、J4 两脚本 **EXIT=0**（只证钉版门）。故只产出**最小等待清单 W1–W7**，执行序列与回滚直接引 LP §2 分支 A（A0–A8）+ §2.A 回滚矩阵；**登记 LP T0 快照已过期**（`405c8373`→`3d9372e5`，J3 计数 674→676 在动，执行前必须重跑 J1–J4）。
- **批次 30 seam（只列不实现，S1–S8；下列"现状"是 R3 写作时点）**：S1 canonical 编码器（逐字节往返；R3 时只有单向 `validate`、仓内 visual 测试 0 个往返用例——**T2 已部分前进**：`serialize` + 15 例含逐位往返）；S2 版本不支持的机器可判码（R3 时折成 `DRAWOPS_TYPE`——**T2 已拆出 `DRAWOPS_SCHEMA_VERSION`**，但"未知版本只读"语义未落）；S3 环境/回放身份持久化载体（指纹只在 app、原无 flags，与裁定 5 落点存在**范围矛盾**，需主会话一句话定 (i)/(ii)；T5 已加 flags，载体仍只在 app）；S4 冻结输入纯函数化（`Rendered` 不携 programId/viewport/环境）；S5 PDF 目标几何显式化（`draw` 会替换调用方变换）；S6 contentId 稳定绑定；S7 hitRegions 往返不丢；S8 `DRAW_SCHEMA_VERSION` 镜像（R3 时 = 引擎 `:905`、painter `:303-307`、校验器字面量；**T2 后 = 引擎 `:905`、painter `:330`、parser `:47`，校验器改为委托**——镜像仍是 3 处，位置变了）。
- **R-04 三选一**：技术面均有公开依据；关键新证据 = AGP 9.3 `VariantDimension.testProguardFiles` 官方语义为"test code 套用主代码同一混淆"，故"可测 release 变体"不必以 `-dontobfuscate` 为代价（与 E-03 §6 第 2 项旧表述不同，已登记）。**建议优先级 b1>b2>a>c（建议非裁定）**；端到端 UNVERIFIED（未构建/未上设备）；`developer.android.com` 与 issuetracker 本轮不可达，官方文档经 `.cn` 镜像取得。

### 2.4 研究复核结论

- 三份文档均在盘、行数实测 405/477/254、有来源分级与未验证清单；R1 的 PDF 语义结论（dash 压平、Type3、AA 不可比）在 T3/T4 落码里逐条对应；R2 的度量/阈值/负控与 T4 实现逐项对应；R3 的判定与基线 §9 一致，且**未产出任何执行序列**（分支 B 纪律）。
- R1/R2 的设备侧数字全部标为"待实测初始值"，T4 落地时未把"设计通过"写成"设备通过"；R3 的 R-04 建议明确标注"建议非裁定"。

## 3. 七实施轨 + 集成交接

> 判定口径：**落码成立** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / 仓内产物 / 无证据=UNVERIFIED）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T1 判据核验 | 阻塞 | **判据文档已刷新、原阻断消失；设备执行归设备轨** | T1 写入时为 19 例阻断；复核轮已刷新为 24 例（`:17-22`），现树 `grep -c` = 24 |
> | T2 解析器 | 部分 | **落码成立 / JVM 15 例有结果 XML / 设备无解析器面** | 记录员未复跑 Gradle；设备轨 `step03` 明记无解析器用例 |
> | T3 PDF 回放 | 完成 | **落码成立（4 文件、零既有文件改动）+ JVM 6 例 + 设备 13 例 1 红（12 绿）** | 设备红为 KD-79 写失败面；修复后未复跑 |
> | T4 一致门原型 | 完成 | **落码成立（2 文件）+ 现树 11 例 + 设备 10/10（N08 未上设备）** | N08 是复核轮新增；原型门非产品门 |
> | T5 KD-78 收口 | 部分 | **四缺口落码 + Node 94/0 + 设备 flags 红（已修待复跑）；KD-78 维持 open** | 修复轮把"默认 flags"改"显式 0"；设备证据未更新 |
> | T6 台账/契约同步 | 完成 | **完成（3 文件纯文档）；设备出数后的计数已由设备/修复轮接续，但附录 G 仍停在 21/10** | 见 §5 #9 |
> | T7 条件轨 | 完成 | **完成（分支 D 登记，零改动）** | 热区 43/59/12/16/4 + core/ui 35 全脏，队列项不可开工 |
> | 集成交接核验 | 完成 | **无需接线（同一 `VisualDrawOpsDocument` 契约）+ 发现 1 个阻断缺陷（已修）** | 见 §3.8 |
> | 设备轨 | 六步执行完毕 | **8 套件：4 绿 4 红；两独立产品根因** | 见 §4 |
> | 证据轨 | 完成 | **7 个 `.md` / 46,504 B；9 类签名 ×8 logcat 全 0** | 见 §4.3 |
> | 复核修复环 | 完成 | **非低项全部处置；JVM 结果 XML 0 failures；设备绿轮 UNVERIFIED** | 见 §5 |

### 3.1 T1 设备判据核验 —— **文档已刷新；原阻断不成立**

- 文件：`docs/research/2026-10-28-batch-29-device-criteria.md`（**记录员实测 412 行**；工单摘要写 428 行，与实际不符，按实测登记）。
- 文档内容：① painter 逐例判据（现树 **24** 例为硬判据 `OK (24 tests)`，含 T5 三例改写与修复轮三例）；② 隔离 13 + Race 2 不回退判据（spawn↔ready 按 pid 1:1、kill 归因、9 类签名）；③ 设备轨串行命令清单与逐步判据；④ 红例五步归因规则（先测试层后产品层、禁止放宽）。
- **关键时间点修正**：T1 首版判据写"painter 19 例、13/14/15 三例会先被生成门拒、`OK (19)` 不可达"；复核轮已就地刷新（文档头部 `:10-22`、§3.1 `:132-148`），与现树 24 例一致。本记录不再复述旧阻断为现行事实。
- 本轮记录员未运行 Gradle/设备；T1 轨同样只读。

### 3.2 T2 解析器（`Rendered.drawCommandsJson` ↔ DrawOps V1）—— **落码成立；15 例 JVM 有结果产物**

- 文件（3 新 + 2 改）：`VisualDrawOpsParser.kt`（853 行）、`VisualDrawOpsPainterRendered.kt`（38）、`VisualDrawOpsParserTest.kt`（517/15 例）、`VisualDrawOpsValidator.kt`（+15/−510，改为薄委托）、`VisualDrawOpsValidatorTest.kt`（+61/−1，35 例）。
- **API（记录员实读）**：`parse(text, frozenContentIds, viewport)`（`:83`，保留引擎提交门：viewBox 回显 + contentId 冻结 + 配平 + stats 复算）、`parse(rendered)`（`:112`，回放入口，只验形状/有限性/冻结引用/版本）、`serialize(document)`（`:620`，固定键序，供逐位往返）。
- **码面（实读）**：`DRAWOPS_MISSING_FIELD`(`:51`)、`DRAWOPS_SCHEMA_VERSION`(`:53`，从旧 `DRAWOPS_TYPE` 独立)、`DRAWOPS_MISSING_COMMANDS`(`:55`)、`DRAWOPS_UNKNOWN_OP`(`:487`)、`DRAWOPS_UNKNOWN_FIELD`(`:804`)；未知字段/未知指令/坏版本/缺字段全部 fail-closed。`VisualDrawOpsValidator.validate` 已是同一实现的提交门委托（`VisualDrawOpsValidator.kt:35-42`），消除双份词表漂移。
- **回放 seam**：`drawRendered(rendered, canvas, pxPerDu, labelContent)` 把"取文本→解析→画"钉成一次调用；文本缺失/解析拒绝折 `PAINTER_DRAW_COMMANDS` 并内嵌原始 `DRAWOPS_*` 码；通过后原样调用既有 `draw`（不复制任何门）。
- **验证边界**：仓内 `core/data/build/test-results/testDebugUnitTest/TEST-...VisualDrawOpsParserTest.xml` 实测 `tests="15" skipped="0" failures="0" errors="0"`（时间戳 `2026-10-28T15:50:09Z`）；记录员未复跑 Gradle。设备轨 `step03-parser-t5-device.md` 明记**无解析器设备面**（`grep` = 0 文件）；新增的 `drawRendered` 两例设备判据在修复轮补入、未上设备。

### 3.3 T3 `VisualDrawOpsPdfPainter` —— **落码成立（组合而非第二套状态机）**

- 文件（4 新）：`VisualDrawOpsPdfPainter.kt`（**154 行**；KD-79 修复后比契约注记的 104 行多出跟踪流）、`VisualDrawOpsPdfPainterPure.kt`（92，零 android 导入）、`VisualDrawOpsPdfPainterPureTest.kt`（110/6 例）、`VisualDrawOpsPdfPainterInstrumentedTest.kt`（523/13 例）。
- **形状（实读）**：`write(document, out, ptPerDu, labelContent)` 建整页 `PageInfo`（尺寸由纯层 `visualPdfPageSize` 取 ceil）；唯一绘制入口是共享的 `VisualDrawOpsPainter.draw(document, page.canvas, ptPerDu, labelContent)`；本类**没有自己的 `when(op)`**，矩阵列序/clip·label 舞步/背景首步/fill→stroke/dash 门/颜色/alpha 全由单一实现决定；`hitRegions` 不读不画。
- **生命周期**：`draw` 抛异常时先 `finishPage`（丢弃）再原样上抛，避免 `close()`/`writeTo()` 的 `IllegalStateException` 掩盖原始有码异常；`IOException`→`PAINTER_PDF_IO`，页几何非法→`PAINTER_PDF_PAGE_BOUNDS`（均走 `paintingFailure`，与屏 painter 同码族）。
- **PDF 特有差异登记**：KDoc 表格逐行登记（pt 单位与 int/ceil、背景=纸白、clip 只求交、dash 压平成填充几何、文本子集/Type3、alpha ExtGState 同值、AA 不可比、PDF 字节不稳定），未静默改语义。
- **测试**：JVM 6 例覆盖 ceil 对 round/floor 的负例、非有限/退化输入、14400 边界与诊断预算；设备 13 例覆盖页尺寸/ceil、DPI 归一（1pt×scale2 与 2pt×scale1 同网格）、原点左上、屏/PDF 关键区域同色、文本硬判 ink>0、0.5 alpha 两侧 ±3、背景覆盖 ceil 余条、嵌套 clip 裁外零墨、hitRegions 不绘制、栅格确定性（不主张字节）、ptPerDu/超页/写失败 fail-closed、异常同码族。
- **偏离说明**：纯层落在新文件 `VisualDrawOpsPdfPainterPure.kt` 而非追加 `VisualDrawOpsPainterPure.kt`——后者同批正被 T5 改脏；语义与可见性不变、仍零 android 导入。

### 3.4 T4 屏/PDF 一致门（原型门）—— **落码成立；设备 10/10（仅原型）**

- 文件（2 新，均 androidTest）：`VisualDrawOpsParityMetrics.kt`（853 行，度量 + 判决）、`VisualDrawOpsParityInstrumentedTest.kt`（718 行，**现树 11 例**）。
- **采集（实读）**：同一份 `validate` 一次的 `VisualDrawOpsDocument` → 屏 `VisualDrawOpsPainter` 得 Bitmap；→ T3 `VisualDrawOpsPdfPainter.write` 生成 PDF；→ `PdfRenderer`（`scale(4)` + `RENDER_MODE_FOR_PRINT`）栅格化到 **192×128**（`GRID_WIDTH/HEIGHT` `:626-627`）；前置确定性门（屏两遍、PDF 栅格两遍、PDF 重建两遍，`PARITY_INSTRUMENT_*`）与环境指纹（记录 device/API/density/fontScale/真实回放 Paint flags；**本批无历史基线，只记录、不计算 `PARITY_ENV_DRIFT`**）。
- **判决（实读）**：`INTERIOR_COLOR_TOLERANCE=2`（`:191`）、`SHIFT_SEARCH_RADIUS=3`（`:194`）、`MIN_SHIFT_SENSITIVITY_GAIN=0.02`（`:197`）、文本 `massRatio∈[0.95,1.05]`（`:849-850`）；F5 每轮上限由 `MAX_F5_PER_RUN` 控制（`VisualDrawOpsParityInstrumentedTest.kt:129-130`）。
- **门自身纪律**：类 KDoc 与运行时 banner 明确 `scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`（`:80`）；失败只写 screen/pdf/diff PNG 并带首个超阈像素与两侧 ARGB；判决不读 PNG。
- **复核修正 R8**：F5 隔离带原只按 `interiorMax ∈ (2,8]` 吸收，整块系统偏色会以"不可归因"放行；修复轮加占比条件（`VisualDrawOpsParityMetrics.kt:587-620`，`interiorFracOver2` 超夹具类上限即保持 F4_COLOR），并新增 `n08UniformInteriorColorCastStaysRedInsteadOfBeingDowngradedToF5`（`:243-264`）。N08 只在树，**未上设备**（设备跑的是 10 例旧版）。

### 3.5 T5 KD-78 四缺口收口 —— **落码成立；KD-78 维持 open**

- **① stroke-only**：`visual-compiler.js:894-896` 与 `VisualDrawOpsParser.kt:424-432` 同码拒 `PAINTER_STROKE_WITHOUT_FILL`；回放侧 `VisualDrawOpsPainter.kt:150-155` 兜底。
- **② dash 形状**：`visual-compiler.js:488-492` 与 `VisualDrawOpsParser.kt:534-546` 同码拒奇数/`<2`（`PAINTER_DASH_ODD`）；回放侧 `VisualDrawOpsPainter.kt:144-149` 兜底；**复核轮把回放门序改为 dash → stroke-only，与两生成门同序**（KD-78 修复块；新增负例 `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`）。
- **③ 8 位色 alpha**：纯层 `effectiveDrawOpsAlpha255(colorArgb, alpha) = floor(colorA × op.alpha)`（`VisualDrawOpsPainterPure.kt:169`），`drawOpsAlpha255` 收敛为其 colorA=255 特例（`:186`）；painter 在 `setColor` 后以该值 `setAlpha`（`VisualDrawOpsPainter.kt:170/177/203`）。契约 §5 写入公式。**夹具词表更正**：`#80ff0000` 在仓内四侧一致的 `#RRGGBBAA` 词表下 alpha 字节是 `00`，按契约取不到 0x80；正例改用 `#ff000080`，已在 KD-78 如实登记。
- **④ flags 指纹**：`VisualMeasureEnvironment` 增 `paintFlags`（默认 0，`VisualContentMeasuring.kt:144`），进指纹（`:174`）；`forEnvironment` 构造后**显式** `paint.flags = DEFAULT_PAINT_FLAGS`（`:469`）再与实际值比对，不一致 fail-closed（`:474-478`）；回放 `textPaint = Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }`（`VisualDrawOpsPainter.kt:54`），常量 0（`VisualDrawOpsPainterPure.kt:87`）。**这是按设备证据改的**：API 34 `Paint()` 默认 flags = 1283，不是 0。
- **生成门同批夹具影响**：`QuickJsSceneCompilerInstrumentedTest.kt:277` 的全节点场景原用 stroke-only dot，改为 `fill:'#ff0000' + stroke`（节点/命令计数不变）；Node 台同场景同步补 fill。
- **验证（工单给定 + 仓内产物）**：Node 台 `94 passed, 0 failed`（记录员本轮实跑；T5 自述先红 `90 passed, 4 failed` → 绿）；core JVM 结果 XML 实测 `PainterPureTest 13/0`、`ParserTest 15/0`、`PdfPainterPureTest 6/0`、`ValidatorTest 35/0`；app `VisualContentMeasuringTest 16/0`。设备面见 §4。

### 3.6 T6 台账/契约同步 —— **完成（3 文件纯文档）**

- `docs/current/visual-generation-contract.md` §5 落为 **DrawOps V1.1 五条语义**：8 位色 alpha 乘法、dash 偶数≥2、stroke-only 拒绝、hitRegions 可非空但不绘制、flags 指纹（显式 0，非平台默认 1283）；§5.1 表行与归位段补 PDF 半场（`VisualDrawOpsPdfPainter`，core:data visual，非 core:export）与一致门原型；复核轮又把 §5 flags 条从"默认"改成"显式装配"。
- `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`：v3 补记 3 刷新 S-04/S-05/S-06（hitRegions 旧口径作废）、附录 F 落实际执行命令；设备出数后另加**附录 G**（回链 21/10 时的设备结论）。
- `docs/known-defects.md`：KD-78 追加四块（T5 收口 / T6 台账 / 设备轨 / 复核修复）；KD-76 追加三块（T6 台账 / 设备轨 / 复核修复）；KD-79 新登记。
- **时点边界**：T6 写于设备轨前，S-04/S-05/S-06 的"设备证据未出数"自带时点限定；设备轨与修复轮已用新块/附录接续（保留原文不回改）。**附录 G 的 21/10 计数在修复后已过期**（现树 24/11），本记录登记为残留（§5 #9）。

### 3.7 T7 热区条件轨 —— **完成（分支 D 登记，零改动）**

- 开工再核热区仍脏（记录时点实测：feature/library 43、tutor 59、review 12、capture 16、profile 4、core/ui 35、core/markdown 整目录未跟踪、core/export 6、core/domain 5；HEAD `3d9372e5` 未动）。
- 按批次 24 R4 的九项顺序（G2→G1→AWAITING→5b-1/KD-68→AUD-08→6B-3→5b-2/阶段 6→5c）逐项复核，全部仍 pending、目标文件均 M，**未重复建 KD 条目**（T7 时点台账 78 条；修复轮新开 KD-79，与本轨无关）。零文件落盘、零 git 写、零 Gradle。
- 锚点漂移：MASTER-LINE AUD-08 门行在批次 28 记录时点为 `:149`；T7 记其漂移，未改文件。

### 3.8 集成交接核验 —— **无需接线；发现 1 个阻断缺陷（已在修复环处置）**

- **接线结论**：T2/T3/T4 以同一 `VisualDrawOpsDocument` 为契约——T4 在 androidTest 内 `VisualDrawOpsValidator.validate`（委托 T2 parser）得文档后分别喂 `VisualDrawOpsPainter.draw` 与 `VisualDrawOpsPdfPainter.write`；T3 的 `write` 内部只调共享 `draw`，没有第二套 `when(op)`。`drawRendered`/`parse(rendered)` 是供批次 31 装配的 seam（现仅设备测调用，无生产装配），无悬空 import。
- **零生产装配改动**：visual 包外无 painter/PDF/parser 引用；引擎仍只经 `VisualDrawOpsValidator.validate`；wire 协议与 coordinator 未动。
- **阻断缺陷（发现#1，高）**：T5 的 `Paint()` 默认 flags 假设错误——设备实测 1283，`forEnvironment` 恒抛，真实 `visual_js` worker warm-up 全灭（14 spawn / 0 ready），连带隔离 12 红、flags 1 红、painter 1 红。**已在修复环按"两侧显式装配 0"修复**（见 §5）。
- **其它发现**：门序声明与代码不符（修复环对齐）；F5 隔离带可放行整块偏色（修复环收紧）；`drawRendered` 零判据（修复环补两例）；文档计数漂移（部分修正）。

## 4. 设备与证据轨

> 环境：`emulator-5562`（**本批新建**，`test_device`，API 34，x86_64，PAGE_SIZE 4096，冷启动 22,437 ms）；`emulator-5584` 属他线全程未触碰；零 `connectedAndroidTest`、零 `adb root`、零 git 写；Gradle 仅 `assembleStrictOfflineDebug` / `assembleStrictOfflineDebugAndroidTest` / `core:data:assembleDebugAndroidTest`（`build/device-track-batch29-build.log` 实测 `BUILD SUCCESSFUL in 40s`，293 tasks / 17 executed）。

### 4.1 产物身份

| 产物 | 字节 | sha256（宿主=设备逐字节一致） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,412,776 | `05a5b8bd…` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,305 | `229cd58e…` |
| `data-debug-androidTest.apk` | 197,656,497 | `4d152b5a…` |

新代码在包内（防 UP-TO-DATE 假象）：core:data 测试 dex 含 `textPaintFlagsForTest` 与 flags 用例；app 主 dex 含 `paintFlagsForTest`/`forEnvironment`；app 测试 dex 含 `VisualMeasureReplayPaintFlagsInstrumentedTest`；dex 符号门含两组隔离套件、painter、PDF painter、parity。

### 4.2 六步结果（原始日志在 `build/device-track-batch29/`）

| step | 套件 | 用例数 | 结果（记录员 grep 复核） | 判定 |
|---|---|---|---|---|
| 01 | painter | 21 | `Tests run: 21, Failures: 1` / Time 0.265 | **FAIL**：`theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`，expected 0 / actual 1283 |
| 02 | 隔离 + Race | 13 / 2 | `Tests run: 13, Failures: 12` / Time 7.965；`OK (2 tests)` / 1.038 | **隔离 FAIL / Race PASS** |
| 03 | T5 flags + 编译器 | 2 / 6 | `Tests run: 2, Failures: 1` / 0.018；`OK (6 tests)` / 0.454 | **flags FAIL / 编译器 PASS** |
| 04 | PDF + 一致门 | 13 / 10 | `Tests run: 13, Failures: 1` / 1.167；`OK (10 tests)` / 0.535 | **PDF FAIL / 一致门原型 PASS** |
| 05 | 备份 | 34 | `OK (34 tests)` / 19.152 | **PASS**（不回退） |
| 06 | 9 类签名 + worker | — | 8 份 logcat 签名全 0；`Start proc .*:visual_js` **14**、`warm-up finished` **0**、`visual_js warm-up failed` **12**、`killing :visual_js worker` **13**（=12 warm-up + 1 `the host unbound`）、AMS `isolated … not needed` **2**、无存活 worker | 签名 PASS；**worker 收口 FAIL（与隔离同因）** |

- 记录员对 8 份 logcat 的 `OK (/Tests run:/Time:` 与 9 类签名逐文件计数复核，与上表一致；`simulated write failure` 在 PDF logcat 实测 **230** 次。
- **两个根因（均归本批产品层，未放宽断言）**：① flags 假设（见 §3.8/§5 #1）；② `PdfDocument.writeTo` 在 API 34 由 native `SkJavaOutputStream::write` 执行 `ExceptionDescribe → ExceptionClear → return false` 吞掉 OutputStream 异常，`catch (IOException)` 不可达，写盘失败**静默截断并报成功**（fail-open）。
- **判据口径**：`am instrument` 的 shell exit 恒 0、`INSTRUMENTATION_CODE: -1` 不是判据，只认 summary 行；painter 判据按落码后 `grep -c` 实测（设备时为 21，现树 24）。

### 4.3 证据轨

- 落盘：`docs/research/evidence/2026-10-28-batch-29/` **7 个 `.md` / 46,504 B**（记录员 `find -printf` 实测），最大 `00-index.md` 16,289 B / 204 行；R4 上限（单文件 64 KiB/400 行、整批 16 个/512 KiB）未触。初扫 38,901 B，追加 §9/§10 与计数更正后 46,504 B（纯记账）。
- 脱敏：索引 §6 实跑结论 secrets 0 / credential assignments 0 / emails 0 / home paths 1（扫描命令自身正则自匹配）。
- 回链：索引 §10 已预写本记录路径（"尚未创建，记录步在修复环之后"），本文件即目标；`build/device-track-batch29/` 只作原始全量，不是判定证据。

## 5. 复核发现与处置

> 口径：下表合并集成交接复核与修复环两张清单；"状态"列 = 记录员本轮是否独立读到代码/日志证据。级别分布：**高 ×2、中 ×5、低 ×12**（共 19 项）。**高×2、中×5 全部在修复环处置**（2 高修复，其中 PDF fail-open 同时新开 KD-79；3 中修复；契约 §5 flags 条更正；KD-79 登记补全）；低项 3 项已修正/刷新、其余保留登记或明确未复核。

| # | 位置 | 发现 | 状态 |
|---|---|---|---|
| 1 | `VisualContentMeasuring.kt:144/174/469-478`；`VisualDrawOpsPainter.kt:54`；`VisualDrawOpsPainterPure.kt:87` | **【高】flags 假设错误**：`Paint()` 平台默认 flags = 1283（AOSP `Paint.java` 构造 `ANTI_ALIAS` + `DEV_KERN|EMBEDDED_BITMAP|FILTER_BITMAP`），而 `DEFAULT_PAINT_FLAGS=REPLAY_TEXT_PAINT_FLAGS=0`；`forEnvironment` 构造后立即比对，生产装配必抛，worker warm-up 全灭 | **已修复（修复环）**：两侧构造后显式 `flags = 0`；校验仍比实际 Paint（坏指纹 `paintFlags=1` 继续 fail-closed）；源码实读确认 |
| 2 | `VisualDrawOpsPdfPainter.kt:88-108`（修复后） | **【高】PDF 写失败 fail-open**：`writeTo` 吞异常，`PAINTER_PDF_IO` 不可达；写盘失败静默成功 | **已修复 + 新开 KD-79**：`PdfWriteFailureTrackingStream` 在异常离开 Java 前记录并重抛，`writeTo` 返回后按 `firstFailure` 发 `PAINTER_PDF_IO`；原 catch 保留兜底；设备绿轮 UNVERIFIED |
| 3 | `visual-compiler.js:488-492/894-896`；`VisualDrawOpsParser.kt:424-432/534-546`；`VisualDrawOpsPainter.kt:144-155` | **【中】门序声称"三侧同码同序"不成立**：生成侧 dash 门先于 stroke-only，回放侧原为 stroke-only 先于 dash；同一"无 fill + 奇数 dash"输入两侧报不同码 | **已修复（修复环）**：回放门序改为 dash → stroke-only，新增设备负例；源码实读确认 |
| 4 | `VisualDrawOpsParityMetrics.kt:587-620`；`VisualDrawOpsParityInstrumentedTest.kt:243-264` | **【中】F5 隔离带可放行"整块系统偏色"**：`interiorMax∈(2,8]` 单条件会把全图同向偏 3–8 级降级为不可归因 | **已修复（修复环）**：加 `interiorFracOver2` 占比条件 + N08 负控；N08 未上设备 |
| 5 | `VisualDrawOpsPainterRendered.kt` | **【中】`drawRendered` 零调用/零判据**，KDoc 的"消灭的失败"无执行证据 | **已修复（修复环）**：两例设备判据（逐像素一致；缺失/截断 → `PAINTER_DRAW_COMMANDS` 内嵌原始码）；未上设备 |
| 6 | `docs/current/visual-generation-contract.md` §5 flags 条 | **【中】契约规范条仍写"保持 `Paint()` 默认 flags（=0）"，与平台 1283 自相矛盾** | **已修复（修复环）**：§5 改为"显式置 0、非平台默认 1283"；源码实读确认 |
| 7 | `docs/known-defects.md` KD-78/KD-79 | 设备轨否证的 R2（PDF fail-open）一度未登记 KD | **已修复（修复环）**：新开 KD-79（台账现 79 条）；实读确认 |
| 8 | `docs/research/2026-10-28-pdf-replay-differences.md:294/306` | R1 设计文档原按"`writeTo` IOException 会逃出有码族"设计 | **已修复**：§7.2/§7.3 改为跟踪流 + KD-79 口径；实读确认 |
| 9 | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:58` 与附录 G | §3.2 总述仍写"hitRegions 必须为空"；附录 G 仍用 painter 21 / parity 10 的旧计数 | **未修（低）**：S-05 行与契约 §5 已改，但总述句与附录 G 未加作废/补记；现树 24/11。登记待下次清单同步轨 |
| 10 | `docs/research/evidence/2026-10-28-batch-29/step06-signatures.md:78` | worker 杀因记账与 raw log 不符：写"13 个杀因 = 12 warm-up + 1 AMS isolated not needed"，raw 为 **12 warm-up + 1 `the host unbound`**；AMS `isolated not needed` 另有 2 行 | **未修（低）**：记录员实测 `visual_js warm-up failed`=12、`killing :visual_js worker`=13、`the host unbound`=1、`isolated not needed`=2；worker 收口 FAIL 判定不受影响 |
| 11 | `.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:14` vs `VisualDrawOpsPainterPure.kt:169`/契约 §5 | 裁定原文公式 `floor(colorA/255 × op.alpha)`（0..1）与落码/契约 `floor(colorA × op.alpha)`（0..255）不同形；照计划字面实现会全透明 | **保留（低）**：代码/契约为准且契约注明等价；计划文本未回改（计划不是权威源）。记录员只登记 |
| 12 | `VisualDrawOpsParser.kt:620-657` | `serialize()` 不执行 V1.1 生成门：手建 stroke-only/奇数 dash 文档可被序列化成"parser 自己会拒绝"的文本（往返保证只对 parse 出来的文档成立） | **保留（低）**：实读确认；未执行 |
| 13 | `VisualDrawOpsParser.kt:558-592` | 路径命令数组不校验长度，`["M",1,2,99]`、`["Z",1,2]` 被接受；与 op/stroke 的 `checkKeys` 严格度不一致 | **保留（低）**：实读确认；未执行 |
| 14 | `VisualDrawOpsParser.kt:92-99`；契约 §4:90 | 无重复 JSON 键检测；契约写"严格拒绝重复键" | **未复核（低）**：代码确实无检查；kotlinx 对重复键的具体行为未验证 |
| 15 | `VisualDrawOpsPdfPainter.kt` KDoc 差异表；夹具 | 8 位 `background` 的屏/PDF 语义差（半透明底 vs 纸白）未登记也无测试（全仓夹具背景均为 6 位或 null） | **保留（低）**：实读确认；屏 painter 直接 `drawColor(argb)` |
| 16 | `VisualDrawOpsPdfPainterInstrumentedTest.kt`（13 例） | R1 §7.4 的 `pdfShearMatrixPlacesInkByContractColumnOrder`、`pdfDashKeepsItsSegmentPattern` 未落；PDF 侧矩阵列序/dash 段结构目前只由"复用同一 draw"推断 | **保留（低）**：实读 13 个用例名确认；记录为证明强度边界 |
| 17 | `2026-10-28-screen-pdf-parity-gate.md:14/80`；契约 §5.1 `:123` PDF 行数 | 文档仍写"设备 19 例 + JVM 10 例"、"104 行"；现树 painter 24、PureTest 13、PDF painter 154 行 | **部分修正（低）**：契约 §5.1 同格已就近写"修复轮后 24 例"；R2 设计与 PDF 行数未回改 |
| 18 | 计划落点清单 vs `app/.../visualjs` 三文件 | T5 改了 app 度量侧主源/测试，不在计划 §2 落点清单，但计划 §1.5 明确授权"度量侧环境指纹加 flags" | **成立（低）**：属"落点清单漏列"，提交须整文件纳入，不能当热区排除 |
| 19 | T1 判据文档首版"19 例阻断" | 写入时点结论后被 T5/复核轮超越 | **已修复**：文档头部与 §3.1 已刷新为 24；旧句按惯例保留但带"已不成立"注记 |

**记录员追加核对（不在两张清单）**：

1. **设备数字与现树计数的时点差**：设备 painter 21 / parity 10 / PDF 13（旧实现）/ flags 2；现树 painter **24**、parity **11**、PDF 13（写侧已改）、PureTest **13**。任何"设备绿"表述都不得套到现树计数上。
2. **修复轮"非放宽"判定**：R1 常量未改（仍 0），只改装配方式；R2 判据语义不变（写失败仍须 `PAINTER_PDF_IO`）；R3 只改门序不改码面；R8 只收紧 F5、N01–N07 断言未动；R10 只增判据不改既有断言。
3. **JVM 结果 XML 是仓内产物**：core 69 例（13+15+6+35）与 app 16 例均 `failures="0" errors="0"`，时间戳 23:48–23:50；记录员**未运行 Gradle**，只读产物。
4. **KD-76/KD-78 的同步已在树**：两节的 T6/设备/修复 Progress 块与设备证据逐值一致（记录员实读），本轮无需再加重复块；KD-79 为修复轮新开。

## 6. 门禁与设备证据

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 2 轮） | **exit=0**（工单给定） | 仓内无本轮 gate 日志；记录员未复跑 |
| 修复环复跑 | **exit=0**（工单给定） | 修复后 JVM |
| 强门（第 1 轮） | **exit=0**（工单给定） | androidTest 编译 + assemble；记录员未复跑 |
| 修复环第 1 轮 | **非低项全部处置**（工单给定） | 高×2 修复（PDF 项同时新开 KD-79）+ 中×3 修复 + 契约 §5 更正 + KD-79 登记；低项见 §5 #9–#19 |
| JVM 结果产物（只读） | **core 69/0、app 16/0** | `core/data/build/test-results/testDebugUnitTest/TEST-*VisualDrawOps*.xml`、`app/build/test-results/testLocalFirstDebugUnitTest/TEST-...VisualContentMeasuringTest.xml`；记录员未复跑 Gradle |
| Node 编译器测试台 | **94/94 passed，EXIT=0（记录员实跑）** | `node tools/visual_compiler/run_tests.mjs`，upper-bound 2.2 ms |
| 设备 隔离 13 + Race 2 | **隔离 FAIL（13 例 12 红）/ Race PASS** | raw：`Tests run: 13, Failures: 12`；`OK (2 tests)` |
| 设备 编译器 6 | **PASS** | `OK (6 tests)` / 0.454 |
| 设备 painter 21 | **FAIL（21 例 1 红，20 绿）** | 唯一红 = flags 观测面 1283 |
| 设备 flags 2 | **FAIL（2 例 1 红）** | `forEnvironment` 恒抛 |
| 设备 PDF 13 | **FAIL（13 例 1 红，12 绿）** | `pdfWriteFailureIsCoded` |
| 设备 一致门 10 | **PASS（仅原型门）** | `OK (10 tests)` / 0.535 |
| 设备 备份 34 | **PASS** | `OK (34 tests)` / 19.152 |
| 设备 修复后绿轮 | **UNVERIFIED** | 修复轮零 Gradle/零设备；painter 24 / parity 11 / flags 2 / PDF 13 / 隔离 13 均需重建重跑 |
| 锁文件生成 | **未执行（分支 B）** | J1/J3 红、跟踪锁 0、盘上 14 未跟踪 |
| 生产 release 签名 / 真实 Provider | **UNVERIFIED** | 本批未跑；JS 不广告 |

**记录员保留**：各门 `exit=0` 无本轮 gate 日志落在仓内，记录员未运行 Gradle；设备数字均有 E-file 且记录员对 raw log 做了 grep 复核，但**未复跑设备**；修复后现树计数与设备跑过的计数不同，不得混用。

## 7. 遗留 / UNVERIFIED / 待裁

**证据面（本批最重）**

1. **修复后设备复跑缺失**：R1（flags）与 R2（PDF 写失败）修完后，painter 24、隔离 13、flags 2、PDF 13、parity 11 全部未重新上设备；KD-79 关闭条件、KD-78 ④ 关闭条件都依赖这一轮。
2. **设备计数落后于现树**：painter 21→24（+门序负例、+2 `drawRendered`）、parity 10→11（+N08）、PDF 写侧实现已变（跟踪流）、PureTest 10→13——所有"绿"都只属于旧源。
3. **JVM 门**：只读到结果 XML（69+16/0），未复跑；`tmp/verify_batch29_kd78_{jvm,red,app_jvm,android_compile}.py` 存在但本轮未执行（其中部分会驱动 Gradle，属纪律禁止）。
4. **Node 门**：记录员实跑 94/0；T5 自述先红（90/4）→ 绿，先红证据未由记录员复跑。

**锁 / 批次 30 / R-04**

5. **锁窗口分支 B**：W1–W7 全部未解除；LP T0 快照过期（`405c8373`→`3d9372e5`），执行前必须重跑 J1–J4；14 个盘上锁**任何分支下都不得 `git add`**。
6. **批次 30 seam S1–S8 只列不实现**；其中 **S3（环境/回放身份载体）与裁定 5 的 app 落点存在范围矛盾**，需主会话选 (i) 把 app visualjs 并入落点 / (ii) 先在 core:data 定义回放环境值。S2（坏版本可判码）在 T2 已部分前进（`DRAWOPS_SCHEMA_VERSION` 独立），但"未知版本只读"语义未落。
7. **R-04 仍阻塞**：本批未跑 Gradle/设备；R3 只给可行性建议 b1>b2>a>c，端到端 UNVERIFIED；E-03 §6 第 2 项旧表述已登记差异。

**代码 / 文档残留**

8. **KD-78 维持 open**：四缺口生成门/语义/指纹已落，③ 设备像素证据已出（正例绿），但 ④ 的修复后设备面未复跑；"KD-78 已关"不成立。
9. **KD-79 维持 open**：修复已落码，设备绿轮 UNVERIFIED。
10. **文档计数漂移（低）**：清单附录 G（21/10）、R2 设计 §0/§1.5（19/10）、契约 PDF 行数（104 vs 154）、step06 杀因记账（1 AMS vs 1 unbound）——均登记未回改。
11. **解析器/PDF 证明强度边界**：`serialize()` 不跑生成门；路径数组不校验多余元素；重复 JSON 键未检测（库行为未验证）；8 位背景 alpha 的 PDF 语义差未登记；PDF 设备用例缺矩阵列序/dash 段结构。
12. **一致性门仍是原型门**：阈值未标定（单设备单 API）、N08 未上设备、真导出链路（KD-75）与 3 档 fontScale 未覆盖；**不得引用为产品/发版结论**。

**环境 / 口径**

13. **arm64/16KB、API 23、真机、低内存/高负载**：本机原理性不可采证或未安装（KD-77 open）；本批设备仅 x86_64/API 34/4 KB。
14. **真实 Provider / 模型质量**：无凭据，UNVERIFIED；JS 仍 `UNAVAILABLE`、不广告。
15. **4E.2 MVA 仍不成立**：PDF 回放半场 + 原型门 ≠ 持久化/双后端/导出链路/质量门就绪。
16. **热区在飞未触碰**：feature 五模块、core/ui、core/markdown、core/export、core/domain、settings.gradle.kts、.gitignore、Root/SecondaryScreens、docs/current 其余 M、docs/research 他线未跟踪记录等。

## 8. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话提交前复跑单测门/强门并留 `exit=0` 日志；修复后如需设备结论，先完成 §7 #1；热区、锁文件（`*/gradle.lockfile` 全部 `??`）与 docs/current 其余 M 一律排除。按 ≤6 路径/块：

- **块 1（T2 解析器与提交门委托）**：`core/data/src/main/kotlin/.../visual/VisualDrawOpsParser.kt`（新）、`VisualDrawOpsPainterRendered.kt`（新）、`core/data/src/test/.../visual/VisualDrawOpsParserTest.kt`（新）、`core/data/src/main/.../visual/VisualDrawOpsValidator.kt`（改；+15/−510 薄委托）、`core/data/src/test/.../visual/VisualDrawOpsValidatorTest.kt`（改；+61/−1）
- **块 2（T5 回放语义 + JS 两门）**：`VisualDrawOpsPainter.kt`（改）、`VisualDrawOpsPainterPure.kt`（改）、`VisualDrawOpsPainterPureTest.kt`（改）、`VisualDrawOpsPainterInstrumentedTest.kt`（改；**整文件纳入**：21→24 例、三例 hand-built、门序负例、`drawRendered` 2 例）、`core/data/src/main/resources/visual/visual-compiler.js`（改；+14）、`tools/visual_compiler/run_tests.mjs`（改；+32/−1）
- **块 3（T5 度量侧 + 编译器夹具）**：`app/src/main/kotlin/.../visualjs/VisualContentMeasuring.kt`（改；**落点清单外、计划 §1.5 授权，整文件纳入**）、`app/src/test/.../visualjs/VisualContentMeasuringTest.kt`（改）、`app/src/androidTest/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt`（新）、`core/data/src/androidTest/.../visual/QuickJsSceneCompilerInstrumentedTest.kt`（改；单 hunk 夹具补 fill）
- **块 4（T3 PDF 回放）**：`VisualDrawOpsPdfPainter.kt`（新）、`VisualDrawOpsPdfPainterPure.kt`（新）、`core/data/src/test/.../visual/VisualDrawOpsPdfPainterPureTest.kt`（新）、`core/data/src/androidTest/.../visual/VisualDrawOpsPdfPainterInstrumentedTest.kt`（新）
- **块 5（T4 一致门原型）**：`core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt`（新）、`VisualDrawOpsParityInstrumentedTest.kt`（新）
- **块 6（三研究线 + 基线与判据）**：`docs/research/2026-10-28-pdf-replay-differences.md`（新）、`2026-10-28-screen-pdf-parity-gate.md`（新）、`2026-10-28-lock-window-and-batch30-prep.md`（新）、`2026-10-28-batch-29-baseline.md`（新）、`2026-10-28-batch-29-device-criteria.md`（新）
- **块 7（证据前半）**：`docs/research/evidence/2026-10-28-batch-29/00-index.md`、`step01-painter.md`、`step02-isolation-race.md`、`step03-parser-t5-device.md`（均新）
- **块 8（证据后半 + 本记录）**：`step04-pdf-parity.md`、`step05-backup.md`、`step06-signatures.md`（均新）、`docs/research/2026-10-28-batch-29-record.md`（新）
- **块 9（台账/契约/清单/总路线）**：`docs/known-defects.md`（改；**整文件纳入并注明**：KD-78 T5/T6/设备/修复四块、KD-76 T6/设备/修复三块、KD-79 新条）、`docs/current/visual-generation-contract.md`（改；**整文件纳入**：§5 五条 V1.1、§5.1 PDF/一致门/修复轮注记）、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（改；**整文件纳入**：v3 补记 3 + 附录 F/G）、`docs/REFACTOR-MASTER-LINE.md`（改；顶部批次 29 滚动行）、`docs/research/algorithm-version-ledger.md`（改；§3.39 零 bump）、`docs/agent-first-refactor-decisions-2026-09-23.md`（改；批次 29 裁定节）

**明确排除（他线在飞/不安全）**：全部 `?? */gradle.lockfile` 与 `settings-gradle.lockfile`（分支 B，不得提交）；`settings.gradle.kts`、`.gitignore`；`feature/**`、`core/{ui,database,export}`、`core/markdown/`、`core/domain`、`core/data` 他线 M（mistake/study/knowledge/model 面）与他线 test/androidTest M；app 开工即脏 M；docs/current 其余 M；docs/research 他线未跟踪记录（`2026-10-*-ui-*` 等）；`docs/architecture-research-*`、`docs/audit-*`；`build/**`（含 `build/device-track-batch29/` 原始日志）；`tmp/`；证据目录内 `.mimosa/hook-status/*.json`（按显式文件路径 add，不按目录 add）。

**提交说明**：建议 9 块、每块 ≤6 路径；五处整文件纳入的 M 文档（known-defects/契约/清单/MASTER-LINE/决策台账）在提交说明中注明"整文件纳入，含本批多轨改动"；`VisualDrawOpsValidator.kt` 的 M 属本批 T2（不是他线）。提交/推送由主会话执行；非快进 merge，不 rebase。

## 9. 收口状态

- **研究**：R1/R2/R3 落盘（405/477/254 行），来源分级与未验证清单齐备。
- **实施**：T2 解析器 853 行 + 15 例；T3 PDF 154+92 行 + 6/13 例；T4 一致门 853+718 行 + 11 例；T5 四缺口落码 + Node 94/0；T6 契约 §5 五条 + 清单补记 3 + KD 三块；T7 分支 D 零改动。
- **集成**：无需接线；发现 flags 阻断（已修）、门序不符（已修）、F5 隔离带（已修）、`drawRendered` 零判据（已补）。
- **设备**：六步执行，8 套件 4 绿 4 红；隔离/Race/编译器/一致门原型/备份有绿证据，painter/flags/PDF/隔离有红证据；两独立产品根因；9 类签名 ×8 logcat 全 0；worker 14 spawn / 0 ready。
- **门禁**：单测门第 2 轮、修复环复跑、强门第 1 轮 exit=0（工单给定）；修复环非低项全部处置；JVM 结果 XML 69+16 例 0 失败（只读产物）；Node 94/0（记录员实跑）。
- **本批不声称全绿/全就绪**：修复后设备绿轮 UNVERIFIED；KD-72/75/76/77/78/79 open；4E.2 MVA 不成立；arm64/API 23/生产签名/真实 Provider UNVERIFIED；JS 仍 `UNAVAILABLE` 不广告；Mimosa 仍无完整扫描结论——**不宣称项目安全**。

## 10. 本记录的验证结果（记录员实跑/实读，写盘前执行）

```bash
cd "D:/smart mistake book"
git rev-parse --short HEAD                                   # 3d9372e5
git rev-parse origin/main | cut -c1-8                        # 3d9372e5（本地跟踪引用，未 fetch）
git rev-list --left-right --count origin/main...HEAD         # 0	0
git status --porcelain | wc -l                               # 703
git status --porcelain | grep -c '^ M'                       # 215
git status --porcelain | grep -c '^??'                       # 488
git diff --cached --name-only | wc -l                        # 0

# 计数（本记录员实跑 grep -c / wc -l）
# painter 仪器化 24、parity 11、PDF 仪器化 13、PureTest 13、ParserTest 15、PdfPureTest 6、ValidatorTest 35、app measure 16、flags 2
wc -l core/data/src/main/kotlin/.../visual/VisualDrawOpsParser.kt          # 853
wc -l core/data/src/main/kotlin/.../visual/VisualDrawOpsPdfPainter.kt      # 154
wc -l core/data/src/androidTest/kotlin/.../visual/VisualDrawOpsParityMetrics.kt  # 853
wc -l docs/research/2026-10-28-{pdf-replay-differences,screen-pdf-parity-gate,lock-window-and-batch30-prep}.md  # 405/477/254
wc -l docs/research/2026-10-28-batch-29-{baseline,device-criteria}.md        # 301/412

# JVM 结果产物（只读）
head -2 core/data/build/test-results/testDebugUnitTest/TEST-*VisualDrawOps*.xml   # 13/15/6/35，各 0 failures
head -2 app/build/test-results/testLocalFirstDebugUnitTest/TEST-...VisualContentMeasuringTest.xml  # 16，0 failures

# 设备 raw log 复核
grep -E "OK \(|Tests run:|Time:" build/device-track-batch29/*.log | tail -3
# painter 21/1、isolation 13/12、race OK 2、flags 2/1、compiler OK 6、pdf 13/1、parity OK 10、backup OK 34
grep -c "visual_js warm-up failed" build/device-track-batch29/step02-isolation-logcat.txt   # 12
grep -c "killing :visual_js worker" build/device-track-batch29/step02-isolation-logcat.txt   # 13
grep -c "the host unbound the visual_js worker" build/device-track-batch29/step02-isolation-logcat.txt  # 1
grep -c "isolated.*not needed" build/device-track-batch29/step02-isolation-logcat.txt       # 2
grep -c "Start proc .*:visual_js" build/device-track-batch29/step02-isolation-logcat.txt    # 14
grep -c "warm-up finished" build/device-track-batch29/step02-isolation-logcat.txt           # 0
grep -c "simulated write failure" build/device-track-batch29/step04-pdf-logcat.txt          # 230

# 证据体量
find docs/research/evidence/2026-10-28-batch-29 -name '*.md' | wc -l        # 7
find docs/research/evidence/2026-10-28-batch-29 -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s" B"}'  # 46504 B

# 唯一测试实跑
node tools/visual_compiler/run_tests.mjs                     # 94 passed, 0 failed, EXIT=0

# 未执行：任何 ./gradlew、adb、am instrument、connectedAndroidTest；任何 git 写命令；
#         JVM 单测复跑、设备复跑、门判定器。
```

## 附录 A：与批次 28 记录的结构对应

| 批次 28 节 | 本批对应 | 差异 |
|---|---|---|
| §2 两研究线 | §2 三研究线 | 本批多 R3（锁/前置/路径） |
| §3 四实施轨 | §3 七实施轨 + 集成 | T1–T7 更细；集成单列 |
| §3.5 设备轨 | §4 设备与证据 | 六步 8 套件；两个根因 |
| §5 复核发现 | §5 发现与处置 | 19 项，含修复环两轮 |
| §10 主会话收口 | 待主会话追加 | 本文件不预填提交/推送结果 |
