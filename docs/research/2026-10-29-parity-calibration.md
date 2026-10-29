# 一致性原型门（屏/PDF）阈值标定 — 批次 31 T4 驱动（R2）

日期：2026-10-29 ｜ 任务：`.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md:24-25`（任务 7）的研究半边。
输入（本会话实读）：计划 §0/§2/§3；`docs/research/2026-10-28-screen-pdf-parity-gate.md`（下称 R2 门设计，全篇）；
`core/data/src/androidTest/.../visual/VisualDrawOpsParityMetrics.kt`（853 行全文）、`VisualDrawOpsParityInstrumentedTest.kt`（718 行全文）；
`core/data/src/main/.../visual/VisualDrawOpsPainter.kt:40-60,250-295`、`VisualDrawOpsPainterPure.kt:87`；
`docs/research/evidence/2026-10-29-batch-30/step05-parity.md`（全文）与 `00-index.md:40-90`；
原始设备日志 `build/device-track-batch30/step05-parity-logcat.txt:66-128`、`step05-parity-rerun-logcat.txt:122-128`、`build/device-track-batch29/step04-parity-logcat.txt:115-122`。

产出性质：**标定方法 + 阈值建议 + 来源**。本任务**未改任何 Kotlin/测试/产品源码**，**未运行 Gradle**，**未上设备**；
文中"建议阈值"是**待设备绿轮确认的候选值**，不是已通过的判据。门仍是**原型门**（R2 §7），不得引用为产品/发版结论。

> **批次 31 设备更正（2026-10-29，E-08/E-08b）。** 本文多处断言"1 px 整块平移使质心恰好变化
> 1.000（平移不变性）"，该断言**被设备否证**：`centroidDeltaPx` 是屏栅格与 PDF 栅格之间的
> **跨栅格欧氏距离**，不是同侧平移差；PDF 侧平移 t 后读 |v + t|（v = 基线屏↔PDF 质心偏移），
> **方向相关**。实测 P06 基线 |v|=0.32、+1 px 读 `1.2624767812258337`
> （`build/device-track-batch31/step08-parity-logcat.txt:125`）；由同轮 N12-P07 的 +4 读数
> `3.56014654994809` 反解 v=(-0.4492,0.2582)，P07 +1 px 应读 ≈**0.608 < 0.75**。
> 因此：（a）0.75 的**读数锚**改为噪声 0.518（上侧余量 0.232）与 N11 旋转实测 1.1665；
> （b）1 px 位移的抓取路径是 **bbox**（P06 基线 bboxDev=1 → +1 后 2，设备理由
> `text bboxDev=2px > 1px`，`:126`）与 **bestShift**（P06 +1 后 `(1,0)`、gain≈0.195 > 0.02），
> **不是质心**；（c）N09-P07 因 P06 断言中止**从未执行**，整包 FAIL 14/15，`calibrated-r1`
> 的 15 例绿轮仍 **UNVERIFIED**；（d）"文本亚像素几何 (0,0.76) px 不可分辨"这一标量盲区声明
> **撤回**。下文凡出现 "1.000 / 平移不变性 / (0,0.76) / 下余量 0.250" 的旧句，均以本块为准
> （原文按补记惯例保留，但不得再引用为事实）。

一句话：P07 的 0.518 px 不是"几何错"，而是**屏侧 AA 关 + hinting 开 + 字形位置吸附整像素**与**PDF 侧 AA 开 + hinting 关 + 线性度量 + 内容流浮点文本位置**之间的结构性差；
0.5 px 阈值在这个结构下**从一落地就不可能被正例满足**（结构性噪声上界 ≥0.5 px）。本轮给出 `calibrated-r1`：质心 0.75 px（噪声 0.518 < 0.75；**不是**被 "1 px 信号 1.000" 锚定——见顶部设备更正）、墨量比 ±7%（AA 结构摆幅 4.9 pp）、bbox 1 px 不变、剖面 TV 0.08 不变，
并用**新增负控 N09/N10/N11** 与**固定到 TEXT 类的 F5 归零**保证"整体错画仍会红、只是不再拿 AA 当几何错"。

---

## 0. 结论摘要

1. **P07 归因成立且可定量**：AA 关前后同设备同夹具一线对照——批次 29（AA 开，`PARITY_ENV … replayTextPaintFlags=1283`，即平台 `Paint()` 默认值；`step04-parity-logcat.txt:66`）P07 `maskS=1434 / centroid=0.44 / mass=1.006 → F2_AA`；
   批次 30（AA 关，`flags=0`）P07 `maskS=1224 / centroid=0.518…/ mass=0.957 → F4_GEOMETRY`（`step05-parity-logcat.txt:122`；复跑同值见 `step05-parity-rerun-logcat.txt:122-124`）。
   屏掩膜 −210 px（−14.6%）、PDF 掩膜 1431 不变 —— 与"屏侧不再出半透明边缘像素、PDF 侧仍 AA"逐项一致（Skia `Edging::kAlias` "no transparent pixels on glyph edges"，`include/core/SkFont.h:43-47`）。
2. **0.5 px 是结构性不可达阈值**：屏侧 `flags=0` 同时意味着 **hinting 开**（`LINEAR_TEXT_FLAG` 未置位）与**字形位置吸附整像素**（`SUBPIXEL_TEXT_FLAG` 未置位；AOSP `Paint.java:207-239`）。
   对**任意**夹具，仅"位置吸附"一项就允许 ≤0.5 px 的系统性平移；但注意本门的 `centroidDeltaPx` 是**跨栅格**欧氏距离，一侧平移 t 只让读数变成 |v + t|（v = 基线偏移），**不是** |v| + |t|（设备更正已否证旧"平移不变性"读法）；P07 的原点恰好整像素（15·4=60、20·4=80），所以它的 0.518 由 AA 边缘 + hinting（S1+S2）解释，位置吸附这一项未消耗额度。两条合起来说明：**0.5 px 不是能容纳结构差的阈值**。
3. **解析上界太松、不能拿来定阈值**：按"AA 只改边界带"推 P07 的质心上界 `(ΔM/M)·R ≈ (1431−1224)/1224 × 35 ≈ 5.9 px`，是实测 0.518 的 11 倍。**结论：位移类指标只能经验标定 + 负控锚定，不能由第一原理推阈值**。
4. **阈值必须夹在噪声与已测形状错之间（设备更正后的口径）**：噪声实测 0.518；阈值候选 0.75（上余量 0.232）。**原"1 px 位移信号 = 1.000"不成立**（跨栅格距离方向相关；P07 +1 px 反解约 0.608），故 0.75 的下侧锚是 **N11 旋转实测 1.1665**（余量 0.4165），不是 1 px 平移。**0.8 不采纳**：对 N11 仍有余量但离噪声更近；**≥1.0 不可接受**：N11 的 1.1665 会逼近阈值。1 px 平移的检测由 bbox / bestShift 负责。
5. **R2 §4.3-1 的"2× 余量"规则对跨栅格质心不适用**：旧推导 `2×0.518 = 1.036 > 1.000` 建立在错误的"1 px = 1.000"上；按设备更正，同一 1 px 在不同方向可读 0.608（P07）到 1.2625（P06），2× 规则既非恒不可满足、也非恒可满足。这不是数据不好，是规则类型用错（该规则只适用于 `interiorMax=0`、`IoU=1.000` 这类近零噪声量）。必须改写成**双侧绝对余量**规则，且锚点取**实测**而非推导信号，否则下一轮还会出现同型红。
6. **"F2/F3 先于 F4"不能字面实现**：字面把 `edgeMean >= 1` 提到 bbox/质心之前，现有 N04（label +1 du）会以 `edgeMean=127.79` 直接判 F2（不红）——门当场失去分辨力（`step05-parity-logcat.txt:66-68`）。
   正确形态是**带硬护栏的 AA/字体归因优先**：硬几何护栏（墨缺失、bbox>1、质心>0.75、bestShift≠(0,0)）先判红，护栏内才允许 F2/F1。
7. **放宽必须同步收紧一处**：TEXT 的隔离带（`isolationBandOnly`，`VisualDrawOpsParityMetrics.kt:590-625`）允许 `bboxDev=2` 降级成 F5（不红）。质心放宽到 0.75 后，"bboxDev=2 + 质心 0.7"会成为**新的静默通过窗口**。calibrated-r1 必须让 TEXT 的 F5 **归零**（严格红）。
8. **新增 N09/N10/N11/N12 是本轮的"放宽许可证"**：N09（PDF 侧 +1 px 整块平移 → **门必须红**；设备实测 P06 由 `bboxDev=2` 与 `bestShift=(1,0)` 抓，质心读 1.2625 而非 1.000；P07 未执行）、N10（整页换成别的夹具 → 带内无墨 → 必须红）、N11（旋转 180° → 实测质心 1.1665 / TV 0.371 抓 → 必须红）、N12（+4 px → 设备实测 bboxDev=5 / centroid=3.56 → 必须红）。任一条绿 = `PARITY_GATE_DESENSITIZED`。**注意**：N09 与 N12 的质心读数不能互换当作"1 px 信号"证明。
9. **外部成熟门的一致做法**（§3）：逐像素**色差阈值**普遍是"感知距离的可调档"（pixelmatch 0.1 / Playwright 0.2 / Paparazzi ±2 / looks-same JND 2.3），**没有任何一个成熟工具对"跨栅格器的文本位移"给出可移植的绝对像素阈值**；Paparazzi 更是在源码注释里直说原生文本渲染"extremely difficult to get consistent cross-platform"（`ImageUtils.kt:83-84`）。本门的"经验标定 + 负控锚定"与业界一致，而非权宜。
10. **本轮不动的部分**：V/A/G/D/G-c 的 IoU 下限、`interiorMax≤2`、`frac2` 上限、`bestShift` 规则、掩膜阈值 `τ_bg=6` 全部不变——所以"整体错画"在非文本夹具上的分辨力**不受本次标定影响**（§2.6）。

---

## 1. 一、文本质心 / bbox 差异的合理上界

### 1.1 两侧到底差在哪（结构源，逐项有源）

| # | 结构源 | 屏侧（回放） | PDF 侧（`PdfDocument` 写 + `PdfRenderer` 读） | 证据 |
|---|---|---|---|---|
| S1 | 边缘 | **AA 关**：`Edging::kAlias`，"no transparent pixels on glyph edges" | **AA 开**：写侧规范化 `kAntiAlias`；栅格化侧 PDFium AA（print 模式无 LCD） | `SkFont.h:43-47`；`SkPDFFont.cpp:164-176`；AOSP `PdfRenderer.cpp:85-90` |
| S2 | Hinting | **开**：`flags=0` 未置 `LINEAR_TEXT_FLAG`，其官方语义是"启用时禁用 hinting"⇒ 不启用即 hinting 生效 | **关**：写侧 `setHinting(kNone)` + `setLinearMetrics(true)` | AOSP `Paint.java:207-226`；`SkFont.h:160-167`；`SkPDFFont.cpp:164-176` |
| S3 | 字形位置 | **吸附整像素**：`flags=0` 未置 `SUBPIXEL_TEXT_FLAG`；官方语义是"防止字形位置吸附到整像素值"⇒ 不置位即吸附；Skia `SkGlyphPositionRoundingSpec` 给出非 subpixel 的 rounding=1/2 | canonical font 亦 `setSubpixel(false)` + 线性度量；内容流的文本位置是**浮点**（advance 不一致时写显式 `Td`），PDFium 按文本矩阵 AA 栅格化 | AOSP `Paint.java:227-239`；Skia `src/core/SkGlyph.h:224-233`；`src/pdf/SkPDFFont.cpp:164-176`（本会话实读）；`SkPDFDevice.cpp:795-832` |
| S4 | 字形来源 | 设备本地 typeface（含 CJK fallback） | 嵌入子集或 **Type3 矢量回退**；不引用系统字体名 | R2 门设计 §1.2；`SkPDFFont.cpp:251-258,330-380,951-954` |

**S1+S2 是 P07 差值的直接来源；S3 是任何非整像素原点夹具的额外额度**。三者可叠加，因此单个"AA 修正"或"hinting 修正"都不足以把差值稳定压到 0.5 px 以下。

### 1.2 每个结构源给出的界（含推导，标注哪些是推导而非实测）

- **S1（AA 开/关）**：AA 只改变"部分覆盖"的边界像素；`kAlias` 语义保证屏侧这类像素不存在（边缘像素全有或全无）。因此
  (a) 墨迹**内部**（两侧掩膜腐蚀后的交集）不受 AA 影响——与实测一致：P07 `p50=p95=p99=0`、`frac2=0.0085`（那 0.85% 是细笔画只剩 1~2 px 的"内部"里混进了单侧边缘像素，见 `interiorMax=58`、`firstOverPx=(105,50)`）；
  (b) 墨量差 `ΔM ≤ 周长 × 1 px`；P07 实测 `ΔM = 1431 − 1224 = 207 px`（占 PDF 侧墨量 14.5%），与 CJK 多笔画周长同量级；
  (c) 边界位移 ≤1 px ⇒ **bbox 每边偏离 ≤1 px**（实测 P06/P07 两态均 ≤1）。
- **S2（hinting）**：TrueType/OpenType 的 grid-fitting 把轮廓点与距离**舍入到像素网格**（`MDAP[1]` round to grid、`RTG` round to grid，OpenType `tt_instructions`），规范**不给出**位移幅度的数值上界 ⇒ 无法解析定界，只能实测。这是"必须有设备读数"的第二个理由。
- **S3（位置吸附）**：非 subpixel 时字形位置舍入到整像素（rounding=1/2，`SkGlyph.h:224-233`；AOSP 文档："`SUBPIXEL_TEXT_FLAG` … prevent glyph positions from snapping to whole pixel values"）⇒ **单字形相对精确 du 位置的系统性平移 ≤0.5 px**。
  而整字形平移 `t` 使质心**恰好**变化 `|t|`（质心对平移等变）。**注意口径**：这 0.5 px 是"屏侧相对标称位置的偏差"，只有当 PDF 侧**不**做同样的吸附时才变成"两侧差"；PDF 侧是否也在栅格阶段吸附**本会话未验证**（§5-6）。P07 的标称位置恰为整像素，故该项对本次 0.518 的贡献为 0——但任何**非整像素原点**的文本夹具都会直接吃掉这 0.5 px 额度（这就是 §4.2 "原点整像素"前置条件的由来）。
- **S4（字体替换）**：本管线**预期不发生**（嵌入子集或 Type3）。若真发生（缺字、子集不全、回退到别的轮廓），位移是**多个像素**量级——实测 N05（"H"→"M"，同一 DTO、换 payload）`bboxDev=11 / centroid=5.45 / mass=1.492 / tvCol=0.480`（`step05-parity-logcat.txt:85-88`）。⇒ 字体类差异**不需要容差容忍**，它必须是红（F1/F4）。这条把"字体替换"从"上界"问题里排除出去，只保留"同一字形的覆盖率差异"进容差面。

### 1.3 为什么解析上界不能当阈值用

以 P07 为例取上界：质心位移 `≤ (ΔM / M) · R`，其中 `R` = 墨迹到质心的最大半径（64 px 字号下 `R ≤ ~35 px`）：
`(207 / 1224) × 35 ≈ 5.9 px` —— **比实测 0.518 大 11 倍**，且比 1 px 的位移信号还大 5 倍。
⇒ 任何"由 AA 面积推出阈值"的路线都会得到**无法分辨 1 px 位移**的阈值，等于关掉几何检测。
**方法结论：位移类（质心、位移、IoU 位移面）必须经验标定，并用"1 px 位移负控"钉住上界；幅度类（内部色差、墨量、剖面）可以用"噪声带上界 + 结构摆幅"两侧夹。**

### 1.4 设备实测（同一台 emulator-5562 / API 34 / x86_64 / PAGE_SIZE 4096；两批各自确定性复跑）

| 夹具 | 状态 | maskS | maskP | IoU | bestShift | bboxDev | centroid | mass | tvCol/tvRow | edgeMean | 判定 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| P06 "H" | AA 开（批29） | 754 | 754 | 1.0000 | (0,0) | **0** | **0.03** | 1.018 | 0.013/0.003 | 5.45 | F2_AA |
| P06 "H" | AA 关（批30） | 667 | 754 | 0.8846 | (0,0) | **1** | **0.32** | 0.991 | 0.037/0.011 | 20.96 | F2_AA |
| P07 "标" | AA 开（批29） | 1434 | 1431 | 0.9424 | (0,0) | **1** | **0.44** | 1.006 | 0.010/0.028 | 17.52 | F2_AA |
| P07 "标" | AA 关（批30） | 1224 | 1431 | 0.8412 | (0,0) | **1** | **0.5181329158118464** | 0.957 | 0.032/0.042 | 28.11 | **F4_GEOMETRY** |

出处：`build/device-track-batch29/step04-parity-logcat.txt:116-121`；`build/device-track-batch30/step05-parity-logcat.txt:119-122`；复跑同值见 `step05-parity-rerun-logcat.txt:122-125`。

**AA 状态切换的结构摆幅**（同一夹具、同一设备、同一 APK 构建链）：

- 墨量比：P06 `1.018 → 0.991`（−2.7 pp）；P07 `1.006 → 0.957`（−4.9 pp）。
- 质心：P06 `0.03 → 0.32`（+0.29 px）；P07 `0.44 → 0.518`（+0.078 px）。
- 屏掩膜：P06 −87 px（−11.5%）、P07 −210 px（−14.6%）。

⇒ **CJK 对 AA 状态更敏感**（笔画密度高 ⇒ 边缘像素占比高：同一 64 px 字号下 P07 墨量约为 P06 的 1.8~1.9 倍）。这解释了为什么先红的是 CJK 而不是拉丁：**阈值不是"整体偏小"，而是"没有按字形密度分层标定"**。

### 1.5 合理上界（本轮的结论）

| 量 | 上界 | 依据 | 余量 |
|---|---|---|---|
| bbox 每边 | **≤1 px（整数指标，保持不变）** | AA 边界带 ≤1 px（S1）；实测两态均为 1 | 整数性：下一个可表示的是 2 px ⇒ 1 是"能通过的最大正整数"；**不存在"1 与 2 之间"的阈值空间** |
| 质心 | **≤0.75 px（由 0.5 上调）** | 实测噪声 0.518（CJK，AA 关，由 S1+S2 解释）+ S3 对**非整像素原点**夹具可再贡献 ≤0.5 px（本次夹具整像素，未消耗）；**设备更正**：跨栅格距离，1 px 平移不是恒 1.000 信号（P07 反解约 0.608）；下侧锚改为 N11 旋转实测 1.1665 | 上余量 0.232 / 对 N11 余量 0.4165 |
| 墨量比 | **∈[0.93, 1.07]（由 [0.95,1.05] 放宽）** | AA 结构摆幅：CJK 4.9 pp、拉丁 2.7 pp；真实覆盖率缺陷（缺笔/子集不全）≥10% | 带宽半宽 7 pp ≈**1.4×** 结构摆幅（4.9 pp）；距实测 0.957 为 2.7 pp；距 10% 缺陷 3 pp |
| 剖面 TV | **≤0.08（保持不变）** | 实测最大 0.042（1.9× 余量）；最弱实测字体缺陷 N04=0.558 / N05=0.480（6~7×） | **不因"2× 规则不满"而上调**：上调会把 (0.08,0.14) 这一段无证据区间变成盲区（§4.3） |

**明确不主张**：本表不给"任意字形/任意 fontScale"的普适上界。它只对**同一设备 + 同一 AA 状态 + 字形位置整像素**的夹具成立；跨 fontScale、跨 API、跨设备必须先重基线（R2 §3.3 `PARITY_ENV_DRIFT`）。

---

## 2. 二、负控：如何证明"放宽后整体错画仍会红"

### 2.1 三条不可违反的规则（本轮建议写进 R2 门设计 §4.3 的替换条款）

1. **夹逼**：`噪声实测 < 阈值 < 该指标要抓的最小真缺陷`。对跨栅格质心，"1 px 平移"**不是**一个可用的信号值（方向相关：P06 读 1.2625、P07 反解约 0.608）；可用锚是实测形状错（N11 旋转 1.1665）与整数位移的 bbox/bestShift 出口。
2. **一条放宽，一条负控**：每条被放宽的阈值必须指明"由哪条负控钉住它的上界"；没有负控的放宽**不允许合并**。
3. **负控必须有手算期望值**：只断言"红"不够——红的原因也要对上（`assertRed` 只查 `isRed`，`VisualDrawOpsParityInstrumentedTest.kt:412-430`）。手算值让人能复核门的灵敏度是几何级还是数量级。

### 2.2 现有 10 条负控守住了什么（`VisualDrawOpsParityInstrumentedTest.kt:154-270`；批30 实测全红、`DESENSITIZED=0`）

| 负控 | 施加面 | 手算/期望 | 守住的阈值 | 与文本标定的关系 |
|---|---|---|---|---|
| N01 ×4 夹具 | 栅格整体 +1 px | P01 `(24−1)/(24+1)=0.920`、P02 `0.969`、P03 `0.939`（d=4）、P05≈`0.78` | 几何类 IoU 下限 0.990/0.970 + `bestShift` 规则 | 不涉及（文本类当前无位移断言）→ **本轮必须补 N09** |
| N02 | 栅格单通道 +16 | `interiorMax≥16` | `interiorMax≤2` | 不涉及 |
| N03 | 输入删 `clip` | P03 墨 256→1024 px、`IoU=0.25` | 几何 IoU | 不涉及 |
| N04 | 输入 label x +1 du | 实测 `bboxDev=5 / centroid=4.25 / tvCol=0.558` | **文本 bbox/质心** | 直接钉住质心上界（4.25 ≫ 0.75） |
| N05 | 输入 payload "H"→"M" | 实测 `bboxDev=11 / centroid=5.45 / mass=1.492 / tvCol=0.480` | **文本墨量/剖面/bbox** | 钉住墨量上界（1.492 ≫ 1.07） |
| N06 | 输入 alpha 0.5→0.6 | P02 内部 Δ≈26 | 内部色差 | 不涉及 |
| N07 | 输入 ops 清空 | `count(M_P)=0 → IoU=0` | 空掩膜/几何 | 不涉及 |
| N08 | 合成整块偏色 +4 | `frac2≈1 > 0.002` | 隔离带不得吞掉系统性错色 | 不涉及 |
| 度量自证 | 并集分母 + 腐蚀边界 | — | 门自身 | 不涉及 |

**缺口**：现有负控**没有任何一条**钉住"文本 1 px 位移"，因为文本类 `requiresShiftSensitivity=false`（`VisualDrawOpsParityMetrics.kt:78`）且文本判决不看 `bestShift`。本轮放宽的正是文本阈值，所以这个缺口就是"放宽许可证"的缺口。

### 2.3 新增负控（本轮的许可证，建议编号 N09–N11）

| id | 施加面 | 构造 | 期望读数（可手算部分加粗） | 期望判定 | 消灭的失败 |
|---|---|---|---|---|---|
| **N09** | P06+P07 栅格 | 对 `pair.pdf` 做 `shift(dx=1,dy=0)`（现成的 `VisualDrawOpsParityMetrics.shift`） | **门必须红**。设备实测 P06：`bboxDev=2`（基线 1 → 2，理由 `text bboxDev=2px > 1px`）、`bestShift=(1,0)`、gain=0.8846−0.6897≈0.195 > 0.02、`centroid=1.2625`（**不是 1.000**；|v|=0.32）。P07 未执行（P06 在旧等式上中止） | 红（`F4_GEOMETRY`；P06 由 bbox 抓，bestShift 分支同时成立） | "把质心放宽到 0.75 后，1 px 文本位移被当成 AA 放过" |
| **N10** | 整页替换 | PDF 侧改画 P01 的矩形文档（文本带 `x∈[52,140]` 内无墨，`VisualDrawOpsParityInstrumentedTest.kt:640`） | `pdfBox=null → bboxDeviation=Int.MAX_VALUE`；带内 `IoU≈0` | 红（`F4_GEOMETRY`：墨缺失；设备实测 `text ink missing on one side`） | **整体错画**（整页内容完全不同）被文本指标平均掉 |
| **N11** | 对抗不变性 | 对 P07 的 PDF 栅格做 180° 旋转（绕墨迹质心）或水平镜像 | bbox/墨量不变；设备实测 `centroid=1.1665`、`tvColumn=0.371`、`iou=0.233` → 红 | 红（`F4_GEOMETRY`：质心 + TV/IoU） | "形状错但位置与墨量对"——证明质心不是唯一防线，剖面 TV 必须留在判决面 |

**N09 的关键作用（设备更正后）**：它只证明"1 px 整块平移必须判红"，并记录了质心**不是**该信号的抓取路径——P06 由 bboxDev=2 抓（bestShift 分支同样成立），P07 未执行。旧结论"1 px 位移确实落在 1.000、bbox 抓不住"**作废**。
**N11 的关键作用**：它证明"质心 + 墨量"够不着的形状错由 `TV/IoU` 兜底——因此**不能**为了给质心让路而去动 TV。

### 2.4 为什么"F2/F3 先于 F4"不能字面实现（反例是现成的）

计划任务 7 写"把分类优先级改为 F2/F3 先于 F4"。字面做法是把 `rawTextVerdict` 的 `edgeMean >= EDGE_SIGNIFICANT_MEAN → F2_AA`（`VisualDrawOpsParityMetrics.kt:573-578`）提到 bbox/质心判断（`:556-562`）之前。后果用现成的 N04 读数即可证伪：

```
P06 N04（label x +1 du = +4 px）: bboxDev=5 centroid=4.25 edgeMean=127.79 tvColumn=0.558 → 现判 F4_GEOMETRY（红）
字面换序后:                         edgeMean=127.79 ≥ 1.0       → 判 F2_AA（不红）  ⇒ N04 变绿 ⇒ PARITY_GATE_DESENSITIZED
```
（`build/device-track-batch30/step05-parity-logcat.txt:66-68`）

**正确形态 = 带护栏的归因优先**（护栏内的 AA/字体类先判，护栏外的几何类先判）：

```
TEXT 类判决（calibrated-r1 建议形态）
0. 硬几何护栏（任一条成立 → F4_GEOMETRY，不允许被 F1/F2 抢占）
   a. 任一侧带内无墨（screenBox/pdfBox == null）
   b. bboxDev > 1
   c. !(centroid <= 0.75)
   d. bestShift != (0,0) 且 gain > 0.02          ← 新增（N09 钉）
1. 字体/覆盖率面（红）→ F1_FONT
   massRatio ∉ [0.93,1.07] 或 tvCol/tvRow > 0.08
2. AA 面（不红）→ F2_AA
   edgeMean >= 1.0 或 (非 NaN && iou < 0.99)
3. 全过 → PASS
```

这条形态与"F2/F3 先于 F4"的**字面**差别只在"谁被允许抢占谁"；**语义**上实现了计划想要的效果：P07 的 `centroid=0.518 / bboxDev=1 / edgeMean=28.11` 全部落在护栏内的 AA 面，判 `F2_AA`（不红）并进报告。
**F3 的换位在文本类无实际意义**：F3 要求 `interiorMax∈1..2`，P07 实测 58、P06 实测 0，永远不命中；对几何类 F3 已在 F4 之后且不冲突。故本轮只动 TEXT 的护栏顺序，不动 F3。

### 2.5 分辨力账本（放宽后"最小还能抓到什么"，必须随绿轮进证据）

| 指标 | calibrated-r1 阈值 | 仍能抓到的最小真实缺陷 | 依据 |
|---|---|---|---|
| bbox | ≤1 px（整数） | 位移/字形变化 ≥**2 px** | AA 边界带 ≤1 px（S1）；N04=5、N05=11 |
| 质心 | ≤0.75 px | **不是 1 px 位移的抓取路径**；可抓的实测形状错为 N11 旋转 1.1665 | 跨栅格欧氏距离、方向相关；P07 +1 px 反解约 0.608 < 0.75（E-08 模型） |
| bestShift（文本新增） | =(0,0) | **≥1 px** 整像素位移 | 设备实测 P06 +1 px → `(1,0)`、gain≈0.195；P07 未执行 |
| 墨量比 | [0.93,1.07] | 覆盖率变化 ≥**8%** | 结构摆幅 4.9 pp；缺笔/子集缺陷 ≥10%；N05=1.492 |
| 剖面 TV | ≤0.08 | 形状错 ≥**0.09**（实测最弱真缺陷 0.480） | N04=0.558、N05=0.480 |
| 内部色差（文本不判） | —（只报） | 不适用 | `interiorMax=58` 属 AA 边缘，文本类按 R2 §4.2 不判 |
| 非文本类 IoU/色差/位移 | **全部不变** | 不变 | §2.6 |

**未覆盖的窗口（设备更正后）**：**撤回**"文本几何误差在 (0, 0.76) px 不可分辨"这一标量声明——质心是跨栅格欧氏距离，对 1 px 平移的读数随方向在约 0.608（P07）到 1.2625（P06）之间变化，不存在单一尺度窗口。可写的事实是：**质心本身不能保证分辨 1 px 位移**；1 px 由 bbox（基线 bboxDev=1 时）与 `bestShift` 分支抓，两者都依赖基线/字形状态。若产品侧出现"亚像素文本错位"这一类缺陷，必须另配仪器（§3 的互相关亚像素估计），**不得**再动质心阈值。

### 2.6 为什么"整体错画"不受本次放宽影响

1. **作用域隔离**：`calibrated-r1` 只改 `ParityFixtureClass.TEXT` 的常量与 TEXT 分支（`VisualDrawOpsParityMetrics.kt:549-580`）；V/A/G/D/G-c 的 `iouFloor`、`interiorFracOver2Limit`、`bestShift` 规则一行不动（同文件 `:57-79,499-547`）。
2. **加性证据**：P01–P05、P08 的正例与 N01/N02/N03/N06/N07/N08 全部沿用旧阈值，批30 已全绿/全红（`step05-parity.md:58-70`），本轮不重新解释它们。
3. **整体错画的三种形态都有独立出口**：
   - 整页错（N10）→ 带内无墨 → bbox=MAX → 红；
   - 整页对但形状错、位置/墨量对（N11）→ `IoU` + `TV` → 红；
   - 非文本整体错（N01/N03/N07）→ 未变的几何面 → 红。
4. **F5 归零**：见 §4.2 的 `TEXT` 隔离带删除——否则"bboxDev=2 + 质心 0.7"这类读数会以"不可归因"姿态不红（这是**放宽引入的新静默窗口**，必须一起堵）。

---

## 3. 三、GitHub / 官方资料：成熟图像比对门的阈值与 license

取数方式：`gh api repos/<owner>/<repo>` 与 raw 文件（2026-10-29 实取）；license 用 GitHub 的 SPDX 判定；维护度看 `pushed_at` / `archived`。
**license 评分（0–5）**：5 = 宽松（MIT/Apache/BSD/ISC）且近 90 天有推送；4 = 宽松但 >90 天未动；3 = 宽松但已归档/疑似停更；1 = 强 copyleft（AGPL）——可读不可纳入。

| 项目 | license | 评分 | 维护度（实取） | 阈值语义（源码/文档原文要点） | 采纳 |
|---|---|---|---|---|---|
| [mapbox/pixelmatch](https://github.com/mapbox/pixelmatch) | **ISC** | **5** | 7,005★、pushed 2026-10-06 | `threshold=0.1`（0..1，最大 OKLab HyAB 距离，1 = 黑白之差）；`includeAA=false` 默认**检测并跳过**抗锯齿像素（Vysniauskas 2009）；`windowSize`："return the maximum number of diff pixels found in any N×N sliding window"（`index.js:21-33`） | **部分采纳**：AA 判定只做分类（F2），不做判决；`windowSize` 作为**夹具放大后**的备选（见下） |
| [cashapp/paparazzi](https://github.com/cashapp/paparazzi) | **Apache-2.0** | **5** | 2,638★、pushed 2026-10-28 | 默认 differ `OffByTwo`：逐通道 `abs(Δ)≤2`；`percentDifference > maxPercentDifferent(默认 0.01)` 即失败（`ImageUtils.kt:67-77`、`SnapshotHandler.kt:30-31`）；`DeltaE2000`：JND `2.3`，`percentDifference < 5%` 记 Similar（`DeltaE2000.kt:39,61-62`） | **采纳 ±2 与 0.01%**：±2 是"内部色差噪声带"的独立先例；0.01% 说明成熟门对**面积占比**的口径是极严的（本门 `frac2` 的 0.2%/0.5% 比它宽，属合理差异，见 §4.3 注） |
| [microsoft/playwright](https://github.com/microsoft/playwright) | **Apache-2.0** | **5** | 98,750★、pushed 2026-10-28 | `threshold` 默认 **0.2**（YIQ 感知色差）；`maxDiffPixels` 默认 **0**（严格）；`comparator='ssim-cie94'` 时用 `maxColorDeltaE94 = 1.0`（JND）逐像素判色再计数（`packages/utils/comparators.ts:81-101`） | **采纳"SSIM 只当逐像素色门、不当单一总分"**：与本门拒绝单一 SSIM 判据同向，且给了 JND=1.0 的第二个先例（另见 Paparazzi 2.3） |
| [gemini-testing/looks-same](https://github.com/gemini-testing/looks-same) | **MIT** | **4** | 828★、pushed 2025-08-18 | `tolerance` 默认 = `JND 2.3`（CIEDE2000）；`ignoreAntialiasing` 默认 **true**、`antialiasingTolerance` 默认 0；差异按 **cluster（10 px）** 聚合（`lib/constants.js:20-27`、`index.js:154-172`） | **只作旁证**：AA 忽略的默认开启说明成熟工具的实践是"AA 差异不该判失败"；但**对位移不构成证据**（它同样忽略 AA 后不做位移断言） |
| [americanexpress/jest-image-snapshot](https://github.com/americanexpress/jest-image-snapshot) | **Apache-2.0** | **5** | 3,918★、pushed 2026-10-17 | `failureThreshold` 默认 **0**、类型 `pixel`（`src/index.js:157-158`）；底层 pixelmatch `threshold=0.1` | **采纳口径**：默认零容忍像素计数，说明"计数型门"必须先证明噪声为 0 才能零容忍——本门 `frac2` 用分位/占比正是为此 |
| [kornelski/dssim](https://github.com/kornelski/dssim) | **AGPL-3.0** | **1** | 1,205★、pushed 2026-10-01 | 返回 `1/SSIM−1`（0 = 相同，无上界）；README 明说"Values are not directly comparable with other tools"、"The scale has changed between versions" | **不采纳**：AGPL 不可纳入；且读数跨版本漂移，做不了可复算的门 |
| [obartra/ssim](https://github.com/obartra/ssim)（npm `ssim.js`） | **MIT** | **3** | 317★、pushed 2023-12-07、**archived** | 多种 SSIM 实现（bezkrovny/fast-ssim/original） | **不采纳**：已归档，维护度不足 |
| [skimage](https://github.com/scikit-image/scikit-image) `phase_cross_correlation` | **BSD-3-Clause** | **5** | 6,628★、pushed 2026-10-27 | `upsample_factor` 默认 1；亚像素精度 = **1/upsample_factor 像素**；算法 = Guizar-Sicairos/Thurman/Fienup 2008（`docs: skimage.registration`） | **列为升级备选**：若将来需要 <1 px 的位移读数，用它做亚像素平移估计；**本轮不引入**（没有对应失败要消灭，见 §2.5） |
| [skimage](https://github.com/scikit-image/scikit-image) `structural_similarity` | BSD-3-Clause | 5 | 同上 | 默认 `gaussian_weights=False`；要与 Wang 2004 对齐需 `gaussian_weights=True, sigma=1.5, use_sample_covariance=False` 并显式给 `data_range` | **不采纳为判决**：SSIM 的分数依赖窗口/权重/位深选择，跨实现不可比（与 dssim 的警告同向） |
| [google/skia](https://github.com/google/skia) `skdiff` | **BSD-3-Clause** | **5** | 11,007★、pushed 2026-10-28 | 逐通道最大差 + 分类（`kEqualBits`/`kDifferentPixels`…，`tools/skdiff/skdiff.h:87-151`） | **已采纳形态**（本门 `chanDist` + 分类计数） |
| [cocodataset/cocoapi](https://github.com/cocodataset/cocoapi) | BSD-2-Clause | 5 | — | `iouThrs = [.5:.05:.95]`（`cocoeval.py:25`） | **只作对照**：0.5–0.95 是"不同图像上的检测"口径；本门比的是**同内容两次栅格化**，故 0.99 才合适（**不得**借用 0.5） |

**license 结论**：可借鉴的全部是 ISC/MIT/Apache-2.0/BSD；**AGPL（dssim）不得纳入**；已归档（ssim.js）不采纳。本设计仍**不复制代码、不新增依赖**（AAR/stdlib 不变）。

**从外部可迁移的三条做法（各指名它消灭的失败）**：

1. **AA 差异单独成类、不参与判决**（pixelmatch `includeAA=false`、looks-same `ignoreAntialiasing=true`、Paparazzi 用 ±2 吸收）→ 消灭"AA 差异被判成几何错"（本轮 P07 就是这一条）。
2. **局部窗口最大差**（pixelmatch `windowSize`）→ 消灭"大面积平均把小面积整体错画摊薄"。**本门当前不引入**：夹具只有 192×128，`frac2` 的上限（0.2%/0.5%）在 ≤500 px 的内部区上等价于 **1~2 个像素**，窗口化不会更严。**触发条件**：一旦按 R2 §7 升格条件③接入真导出链路（A4 页 = 数百万像素），占比阈值会随面积线性放大，届时必须换成"任意 8×8 窗口内超容差像素数 ≤k"，否则小面积错画会被面积摊薄——这是**那一刻**的真实失败。
3. **感知色差用 JND 作锚**（Playwright ΔE94 = 1.0、Paparazzi ΔE2000 = 2.3、looks-same 2.3）→ 说明"色差阈值该多大"业界也没有唯一值，用的是**感知阈值 + 项目标定**。与本轮"经验标定 + 负控锚定"同构。

---

## 4. 四、本轮 P07 的归因结论与建议阈值（`calibrated-r1`）

### 4.1 归因结论

**A 类：原型门阈值未标定 × KD-78 ④（文本 AA 固定关）的已知结构差**。判据链：

1. **确定性**：批30 两轮同值 `0.5181329158118464`、同掩膜 `1224/1431/1442`（`step05-parity-logcat.txt:122`、`step05-parity-rerun-logcat.txt:122-123`）⇒ 不是抖动。
2. **前后对照**：AA 开 → 0.44（F2）、AA 关 → 0.518（F4），掩膜 −210 px、PDF 掩膜不变（§1.4）⇒ 变化点唯一对应 KD-78 ④ 的 `flags=0`。
3. **结构解释**：`flags=0` 同时关 AA、开 hinting、关 subpixel 定位（§1.1 S1–S3）。P07 原点整像素（60/80 px），S3 的吸附额度未被消耗，故 0.518 由 **S1（AA 关 → 屏掩膜 −14.6%）+ S2（hinting 开 vs 关）** 解释；这两项都表现为"边缘像素增减 + 轮廓微变形"，与 `bboxDev=1`、`p99=0`、`frac2=0.0085` 的形态一致。
4. **几何排除**：`bestShift=(0,0)`；`bboxDev=1`（AA 边界带上限）；`neighborIou=0.7867 < iou=0.8412`（位移不能改善重叠）；N04 的 4 px 位移读数是 `bboxDev=5 / centroid=4.25`，与 0.518 差一个数量级。
5. **非产品缺陷**：产品行为与 KD-78 ④ 的显式钉死一致（`VisualDrawOpsPainter.kt:54`、`VisualDrawOpsPainterPure.kt:87`）；`PARITY_ENV` 里 `replayTextPaintFlags=0` 与之一致（`VisualDrawOpsParityInstrumentedTest.kt:568`）。
6. **非环境**：同设备（emulator-5562/API 34/x86_64/4096）、新 APK（sha256 与 dex 门已核，见 `step05-parity.md` 头）。
7. **不构成放宽**：把期望改成 0.518 是放宽（禁止）；本轮改的是**阈值表的版本与依据**，并新增负控锚定（§2.3）。

### 4.2 建议阈值表 `calibrated-r1`（只动 TEXT 类）

| 项 | 旧值（`R2-2026-10-28/§4.2-initial-uncalibrated`） | 新值（`calibrated-r1`） | 改/不改的理由 |
|---|---|---|---|
| `MAX_TEXT_BBOX_DEVIATION_PX` | 1 | **1（不变）** | 整数指标；AA 边界带 ≤1 px；下一个可表示值是 2（真缺陷）。**没有可调空间** |
| `MAX_TEXT_CENTROID_DELTA_PX` | 0.5 | **0.75** | 噪声 0.518（上余 0.232）；下侧锚是 N11 旋转实测 1.1665（余 0.4165）。**设备更正**：1 px 平移不是恒 1.000 信号（P06 1.2625、P07 反解约 0.608），旧"下余 0.250 / <1.0 硬约束"作废 |
| `MIN_TEXT_MASS_RATIO` | 0.95 | **0.93** | 旧下界距实测 0.957 仅 0.7 pp（违反余量规则）；AA 结构摆幅 4.9 pp |
| `MAX_TEXT_MASS_RATIO` | 1.05 | **1.07** | 与下界对称；AA 开时实测 1.006/1.018，上界不应比下界更紧 |
| `MAX_TEXT_PROFILE_TV` | 0.08 | **0.08（不变）** | 实测 0.042（1.9×）；上调无证据、且会压缩 N11 类形状错的抓取面 |
| TEXT 的 `bestShift` 规则 | 无（`requiresShiftSensitivity=false`） | **新增：`bestShift≠(0,0)` 且 gain>0.02 → F4** | 给"1 px 文本位移"第二条独立出口；P06/P07 实测 (0,0)，风险低但**必须同轮复验**（见 §5-3） |
| TEXT 的隔离带（F5） | 允许 `bbox∈(1,2]`、`mass∈[0.90,0.95)` 降级 F5 | **删除（TEXT 的 F5 恒为 0）** | 放宽质心后会把 "bboxDev=2 + 质心 0.7" 变成新静默窗口；F5 的上限本来就是 2，删掉不损失任何正当用途 |
| 文本夹具前置条件 | 无 | **新增：label 原点整像素**（`x·d`、`y·d` ∈ ℤ），否则 `PARITY_TEXT_SUBPIXEL_ORIGIN`（拒绝判决，不判红） | S3：非整像素原点让屏侧位置吸附贡献 ≤0.5 px，任何正例都不可能稳定通过；这条**消灭的是"夹具不可标定"** |
| `THRESHOLD_VERSION` | `R2-2026-10-28/§4.2-initial-uncalibrated` | **`calibrated-r1`** | 计划任务 7 指定名称；读数与它一起落证据（R2 §4.3-4） |
| 其余全部（V/A/G/D/G-c、τ_bg=6、interiorMax≤2、frac2 上限、位移规则、掩膜/合成/判别树非文本部分） | — | **不变** | 本轮无证据支持改动；动了就失去与批29/30 的可比性 |

### 4.3 与旧规则的冲突及其处置（必须一起改，否则下一轮同型红）

1. **§4.3-1 "正例余量至少 2×" 对跨栅格质心不适用**：旧推导 `2×0.518 = 1.036 > 1.000` 依赖已被否证的"1 px = 1.000"。按实测，1 px 在 P06 读 1.2625、在 P07 反解约 0.608，2× 规则既非恒不可满足也非恒可满足——规则类型用错。因此把它改写成：
   - **近零噪声量**（`interiorMax`、`IoU`、`frac2`）：保留缺省"2× / 绝对余量"；
   - **位移类量**（质心、`bestShift`）：改为**双侧绝对余量**——`阈值 − 最大正例 ≥ 0.15 px` 且 `信号 − 阈值 ≥ 0.25 px`；
   - **幅度类量**（墨量比、TV）：阈值区间必须**包住**该量的结构摆幅两端（AA 开/关同夹具读数都要落在界内），同时 `阈值 < 最弱真缺陷`。
   `calibrated-r1` 逐条核对：
   - 质心：`0.75 − 0.518 = 0.232 ≥ 0.15` ✓；下侧锚改用 N11 实测 `1.1665 − 0.75 = 0.4165 ≥ 0.25` ✓（**不再使用**旧的 `1.000 − 0.75`）。
   - 墨量：AA 开读数（P06 1.018 / P07 1.006）与 AA 关读数（P06 0.991 / P07 0.957）**全部落在 [0.93,1.07] 内** ✓；下界距最弱真缺陷（≈0.90）留 3 pp ✓。注意这不是"离某一端 0.049"的算法——幅度类的余量是"包住摆幅"，与位移类的"夹在两读数之间"是两种口径，**文档必须写明这个区别**，否则会被按 2× 规则机械驳回。
   - TV：`0.08 − 0.042 = 0.038`，虽不足 2×，但最弱真缺陷 0.480 是阈值的 6×（见下一条），按第 3 条口径成立。
2. **`bboxDev` 的零余量不是缺陷**：整数指标的最大可接受值 = 1，下一个可表示值是 2；"余量"在整数上无意义。规则里必须显式豁免整数指标，否则会被机械套用。
3. **`TV` 保持 0.08 而不追"2×"**：0.042×2 = 0.084 略超 0.08；但 0.084 与 0.08 的差别在实测分辨率之外，而为这点差别上调会把 (0.08,0.084) 变成无证据盲区。**结论：TV 不改，并在规则里写明"幅度类允许保留在 2× 以下，前提是最弱真缺陷 ≥5× 阈值"**（N04 0.558、N05 0.480 均为 6~7×）。

### 4.4 校准步骤（设备轨串行执行；本任务未执行，标 **UNVERIFIED**）

1. **冻结与取证**：记录 `HEAD`、`data-debug-androidTest.apk` sha256、`Build.FINGERPRINT`、API、`densityDpi`、`fontScale`、`replayTextPaintFlags`；`adb logcat -c`。
2. **先加仪器、后动阈值**（顺序不可换，否则无从归因）：
   a. 加 N09/N10/N11（§2.3），**先用旧阈值**跑一轮 → 期望：三条全红、P07 仍红、P01–P06/P08 读数与批30 一致。这一步证明"新仪器没有顺手把门弄钝"。
   b. 加文本 `bestShift` 护栏与"原点整像素"前置断言，跑同一轮 → 期望：P06/P07 `bestShift=(0,0)`、前置断言通过。
3. **应用 `calibrated-r1`**：改 3 个常量（质心、墨量下/上界）+ 加文本 `bestShift` 护栏 + 删 TEXT 隔离带 + 改 `THRESHOLD_VERSION`（只改 `VisualDrawOpsParityMetrics.kt`；测试文件除新增三条负控与前置断言外不动阈值）。重跑。
4. **判据（全绿才算绿轮）**：
   - `Tests run: 15`（最终落码 11 + N09 + N10 + N11 + N12；E-08 实跑 15）`Failures: 0`；
   - `PARITY_CONTROL` 行数 = 15（原 10 + N09 两夹具 2 行 + N10 1 + N11 1 + N12 1），且每行 `red=true`；
   - `PARITY_GATE_DESENSITIZED` = 0、`PARITY_SYSTEMATIC_DRIFT` = 0、`PARITY_F5` = 0；
   - `PARITY fixture=P07 … centroid=0.52 … verdict=F2_AA`（不再是 F4）；
   - `PARITY_DIAGNOSTIC_WRITTEN` = 0（绿轮零产物，R2 §6.3）；
   - `PARITY_GATE scope=PROTOTYPE … thresholds=calibrated-r1`。
5. **确定性与复现**：同设备连跑 3 轮，逐夹具读数**逐位相同**（R2 §4.3-3）。任一不同 → 阈值无效，回到 2 查确定性前置门，**不得**放宽。
6. **跨 API（原型门暂不强制、升格前必须）**：≥2 个 API 级别各 3 轮（R2 §7-1）。本轮若只跑一台，证据里必须写"跨 API UNVERIFIED"。
7. **回写文档**：R2 门设计 §4.2 表换成 `calibrated-r1`、§4.3 规则按 §4.3 改写、§4.4 补"N09 是位移类阈值的锚"、§6.2 表加 N09–N11；`docs/known-defects.md` 若登记了该阈值问题则按证据更新。

### 4.5 记录格式（每次标定一轮一条；落 `docs/research/evidence/<batch>/step05-parity-<threshold-version>.md`）

**运行头（必填）**

| 字段 | 说明 |
|---|---|
| `run_id` / `batch` / `step` | 例：`r1 / 2026-10-29-batch-31 / step05` |
| `head` / `apk-sha256` | 源码与安装包双锚 |
| `device-serial` / `api` / `abi` / `page-size` | 环境 |
| `densityDpi` / `fontScale` | 资源相关 |
| `replayTextPaintFlags` / `replayPathPaintFlags` / `pxPerDu` / `ptPerDu` | 与 `PARITY_ENV` 行逐字段一致 |
| `thresholdVersion` | `calibrated-r1` |
| `raw-log` | logcat 与 `am instrument` 日志路径（未脱敏的不入仓） |

**每夹具一行（机器可解析，照 `logReport` 的字段顺序扩展）**

```
fixture=<id> class=<V|A|G|D|G-c|T> iou=<4f> maskS=<n> maskP=<n> union=<n>
  bestShift=(<dx>,<dy>) bestIou=<4f> neighborIou=<4f> shiftGain=<4f>
  interiorMax=<n> p50/p95/p99=<n>/<n>/<n> frac2=<4f> edgeMean=<2f> sameSign=<2f>
  bboxDev=<n> centroid=<6f> mass=<4f> tvCol=<4f> tvRow=<4f>
  verdict=<enum> control=<Nxx|-> controlExpected=<手算值|-> controlHits=<bool>
  marginLo=<阈值−正例> marginHi=<信号−阈值> thr=<阈值摘要串>
```
`marginLo/marginHi` 只对位移/幅度类填；整数类填 `-`。**任何阈值变更必须带这两列**，否则无法复核"夹逼"是否成立。

**标定判定块（每轮一段）**

| 字段 | 要求 |
|---|---|
| `positiveMax{centroid,mass,tv,bbox}` | 全部 TEXT 正例的最大/最紧读数 |
| `signal{centroid1px}` | **不适用**（设备更正）：跨栅格距离对 1 px 平移方向相关（P06 1.2625 / P07 反解约 0.608），不存在单一 `signal` 值。改为记录 `positiveNoiseCentroid`=0.518 与 `shapeControlCentroid`=N11 1.1665 |
| `chosen` | 每项阈值 + 其两侧余量 |
| `controlsRed` | `14/14`（每行一条 `PARITY_CONTROL`），逐条列 id 与期望值是否命中 |
| `f5` | 必须 `0`（TEXT 已无隔离带） |
| `blindWindow` | **撤回标量窗口**；写"质心不能保证分辨 1 px 位移（跨栅格、方向相关）；1 px 由 bbox/bestShift 抓" |
| `decision` | `green` / `red(<fixture>:<verdict>:<reason>)` |
| `unverified` | 未跑的 API 档、未接的真导出链路、跨设备 |

### 4.6 残留风险与明写不主张

1. 本文写作时**未**在设备上验证 `calibrated-r1`；**批次 31 设备轨已实跑（E-08/E-08b：`Tests run: 15, Failures: 1`）**，失败点正是本文旧 N09 的 `centroid=1.000` 等式（P06 实测 1.2625）。**门级 14/15 红通过、N09-P07 未执行**，因此 `calibrated-r1` 的 15 例绿轮仍 **UNVERIFIED**；本文顶部已按该结果更正读数锚。
2. 0.75 px 的余量对**其它 CJK 字形**是否够用只实测了 "标" 一个；若下一个 CJK 夹具读数 >0.6 px（= 0.75 − 0.15，即 §4.3 位移类余量规则的下限），**不得**直接上调阈值，须先查明是字形密度、还是新的结构差（规则：任何新正例 >0.6 触发 `TEXT_READING_DRIFT` 调查）。
3. 新增的文本 `bestShift` 护栏是**新的可证伪断言**：若在别的字形上正例出现 `bestShift≠(0,0)`，说明 AA 边缘不对称已能骗过掩膜重叠面——此时要查的是掩膜阈值 `τ_bg` 与夹具，**不是**删掉护栏。
4. 门仍是**相对门**：屏侧画得对不对由 painter 套件负责（设备 24 例 + JVM 纯测 13 例，R2 §1.5）。本标定不改这个定位。
5. **不得**据此宣称"PDF 与屏一致"或"PDF 可用"；也不得据此关闭 KD-75（导出链路仍不可测）。

---

## 5. 未验证项（UNVERIFIED，不得作为事实引用）

1. 设备轨已跑（E-08/E-08b）：`Tests run: 15, Failures: 1`（N09-P06 旧等式）；**N09-P07 从未执行**；`calibrated-r1` 的 15 例绿轮**未取得**。门级 `PARITY_CONTROL` 14/14 红（N09-P07 缺位）。
2. 未验证 P07 在其它 CJK 字形 / `fontScale=1.3/2` 下的质心读数；0.75 的普适性**未知**。
3. 未验证新增文本 `bestShift` 护栏在所有正例上都保持 `(0,0)`（仅 P06/P07 在批30 有 (0,0) 的读数）。
4. 未逐位复算 `N11`（180° 旋转）的 `TV` 预期值——它依赖字形栅格，只能设备实测后写入账本。
5. 未读 PDFium 内部对 AA 边缘的量化实现（与 R2 §8-1 同一条未验证）。
6. 未验证 Skia 在当前版本对"非 subpixel 位置吸附"的实际生效路径（只据 `SkGlyph.h:224-233` 的注释与 AOSP `Paint.java:227-239` 文档推出"屏侧相对标称位置 ≤0.5 px"；**推导，非实测**）；也**未验证 PDF 侧栅格阶段是否做同样的吸附**——若两侧都吸附，S3 对"两侧差"的贡献趋近 0（P07 实测即不受影响），若只有屏侧吸附则上限 0.5 px。这一条决定"原点整像素"前置条件在其它 `fontScale` 下的必要性。
7. `1/SSIM−1`、SSIM 分数、ΔE 阈值均为**外部旁证**，未在本项目设备上取数。

---

## 6. 来源

**外部（2026-10-29 本会话 `gh api` 实取源码/元数据；docs 走 WebFetch）**

- pixelmatch：https://github.com/mapbox/pixelmatch —— `index.js:12,26,31,90`（`includeAA=false` 与 AA 检测、`threshold=0.1`、`windowSize`、`antialiased()` 跳过 AA 像素）；ISC、7,005★、pushed 2026-10-06。
- Paparazzi：https://github.com/cashapp/paparazzi —— `SnapshotHandler.kt:30-31`（`maxPercentDifferenceDefault = 0.01`）、`internal/ImageUtils.kt:51-77`（`percentDifference > maxPercentDifferent` 即失败）、`internal/ImageUtils.kt:83-84`（"AWT uses native text rendering … extremely difficult to get consistent cross-platform label text rendering, due to antialiasing"）、`internal/differs/OffByTwo.kt:65`（逐通道 ≤2）、`internal/differs/DeltaE2000.kt:39,61-62`（JND 2.3、<5% 记 Similar）；Apache-2.0、2,638★、pushed 2026-10-28。
- Playwright：https://github.com/microsoft/playwright —— `packages/utils/comparators.ts:81-101`（`threshold ?? 0.2`、`maxDiffPixels ?? 0`、`ssim-cie94` 下 `maxColorDeltaE94: 1.0`）；https://playwright.dev/docs/test-snapshots （"Screenshots differ between browsers and platforms due to different rendering, fonts and more."、建议"run tests in the same environment"）；Apache-2.0、98,750★、pushed 2026-10-28。
- looks-same：https://github.com/gemini-testing/looks-same —— `lib/constants.js:20-27`（`JND: 2.3`、cluster 10）、`index.js:154-172`（默认 `tolerance=JND`、`ignoreAntialiasing=true`、`antialiasingTolerance=0`）；MIT、828★、pushed 2025-08-18。
- jest-image-snapshot：https://github.com/americanexpress/jest-image-snapshot —— `src/index.js:157-158`（`failureThreshold=0`、`'pixel'`）；Apache-2.0、3,918★、pushed 2026-10-17。
- dssim：https://github.com/kornelski/dssim —— README（`1/SSIM−1`、跨版本不可比）；**AGPL-3.0**、1,205★、pushed 2026-10-01。
- ssim.js：https://github.com/obartra/ssim —— MIT、317★、pushed 2023-12-07、**archived=true**。
- scikit-image：https://github.com/scikit-image/scikit-image —— `LICENSE.txt`（BSD-3-Clause）、`docs: skimage.registration.phase_cross_correlation`（`upsample_factor` 默认 1、精度 1/upsample_factor 像素、Guizar-Sicairos 2008）、`skimage.metrics.structural_similarity`（默认 `gaussian_weights=False`；对齐 Wang 2004 需 `gaussian_weights=True, sigma=1.5, use_sample_covariance=False`）；6,628★、pushed 2026-10-27。
- COCO：https://github.com/cocodataset/cocoapi —— `PythonAPI/pycocotools/cocoeval.py:25`（`iouThrs [.5:.05:.95]`）。
- Skia：https://github.com/google/skia —— `include/core/SkFont.h:43-47`（`Edging::kAlias` "no transparent pixels on glyph edges" / `kAntiAlias`）、`:155-167`（`setSubpixel`/`setLinearMetrics`）、`include/core/SkFontTypes.h:21-26`（`SkFontHinting` 四级）、`src/core/SkGlyph.h:224-233`（`SkGlyphPositionRoundingSpec`：non-subpixel rounding=1/2、subpixel 4 档 `&3`）、`src/pdf/SkPDFFont.cpp:164-176,251-258,330-380`、`src/pdf/SkPDFDevice.cpp:795-832`、`tools/skdiff/skdiff.h:87-151`；BSD-3-Clause、11,007★、pushed 2026-10-28。
- AOSP：https://github.com/aosp-mirror/platform_frameworks_base（`android-14.0.0_r1`）—— `graphics/java/android/graphics/Paint.java:207-239`（`LINEAR_TEXT_FLAG=0x40`："When this flag is enabled, font hinting is disabled"；`SUBPIXEL_TEXT_FLAG=0x80`："prevent glyph positions from snapping to whole pixel values"）、`libs/hwui/jni/pdf/PdfRenderer.cpp:85-90`、`libs/hwui/jni/pdf/PdfDocument.cpp:22-93`。
- OpenType（grid-fitting 指令语义）：https://learn.microsoft.com/en-us/typography/opentype/spec/tt_instructions —— `MDAP[a=1]` round to grid、`RTG` round to grid、`INSTCTRL` 可抑制 grid-fitting；**规范未给位移幅度的数值上界**（故 §1.2 S2 只能实测）。

**仓内（本会话实读，行号见正文）**

- `.zcode/plans/plan-2026-10-29-batch-31-persistence-hardening.md:14-45`
- `docs/research/2026-10-28-screen-pdf-parity-gate.md`（§0、§1.1–1.5、§3.3、§4.1–4.5、§5.1–5.3、§6.1–6.4、§7、§8）
- `docs/research/evidence/2026-10-29-batch-30/step05-parity.md`（全文）、`00-index.md:40-90`
- `build/device-track-batch30/step05-parity-logcat.txt:66-128`、`step05-parity-rerun-logcat.txt:122-128`
- `build/device-track-batch29/step04-parity-logcat.txt:115-122`
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityMetrics.kt`（全文；关键：`:57-79,185-219,416-436,499-580,590-625,847-852`）
- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt`（全文；关键：`:98-133,154-270,292-316,405-430,559-579,640,690-717`）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt:51-54,277-290`、`VisualDrawOpsPainterPure.kt:87`
