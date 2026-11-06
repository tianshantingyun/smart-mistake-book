# R6：增量更新后四类读者对 xref 形态、混合 `/Prev` 链与重叠偏移的容忍度

- 日期：2026-11-03（批次 41 研究项 R6）
- 性质：**研究 + 桌面可复跑实测**；未改仓库代码、未跑 Gradle、未做 git 写、未跑设备仪器化
- 输入：`.zcode/plans/plan-2026-11-03-batch-41.md:21`（本项）、`:41-47`（WS2 KD-75 设备往返）、`:117-121`（硬约束）；`docs/research/2026-11-02-batch-40-record.md:291`（发现 5：PDF 设备用例不在树）、`:314`（KD-75 阻塞）；`docs/research/2026-11-02-pdf-incremental-aigc.md`（下称 R1）§2/§6/§8
- 一句话：**四类读者对"文件还能不能打开"极度宽容，对"元数据是否落在你以为的对象上"极不宽容**；而 Android `PdfRenderer` 恰好只能证明前者。设备绿若只断页数，等于没验 `/AIGC`。

---

## 0. 结论摘要

1. **读者宽容的是"打开"，不是"读对"。** PDFium、pdf.js、PdfPig 三家在 xref 读不出来时都会**整文件重建**（PDFium `RebuildCrossRef`、pdf.js `indexObjects`、PdfPig `XrefBruteForcer`），所以畸形链几乎总能"打开"；但重建/逐对象修复是**静默**的，没有任何接口告诉你"你声明的偏移是错的"。规范明确允许重建（ISO 32000-1 Annex C.2：“may attempt to rebuild the table by scanning all the objects”），并提示重建会丢 deleted 条目的 generation 信息。
2. **Android `PdfRenderer` = PDFium，且没有 metadata API。** AOSP 实现直接调 `FPDF_LoadCustomDocument` / `FPDF_GetPageCount` / `FPDF_RenderPageBitmapWithMatrix`，没有任何 `FPDF_GetMetaText` 暴露（`libs/hwui/jni/pdf/PdfRenderer.cpp:42-113`、`PdfUtils.cpp:101-131`）。因此**设备上页数正确 ≠ `/AIGC` 被正确写入/可读**。
3. **偏移错一位，设备仍绿、标签消失。** 本轮实测：把更新段里 Info 的偏移写成 `+1`（或指向对象 5 的对象头），PDFium 153 仍打开 2 页，但 `Title`/`Author` 读成空——因为 PDFium 的 `VerifyCrossRefTable` **只抽检合并表里第一个非零条目**（`cpdf_parser.cpp:371-395`），Info 不是第一个，不会被发现；对象加载时对象号不匹配只返回 `nullptr`（`:1144-1157`），不会触发整表重建。这是本项最重要的一条。
4. **四读者对"表条目字节宽度"不一致。** PDFium 把表条目当**固定 20 字节**块读（`cpdf_parser.cpp:540-594`）；pdf.js 与 PdfPig 按**token** 读（`xref.js:230-320`、`XrefTableParser.cs:50-52`）。19/21 字节的条目在后者可读、在 PDFium 会让后续条目整体错位。本仓写入恰好 20 字节（`PdfAigcInjector.kt:268`），这一点是对的，必须钉住。
5. **混型 `/Prev` 链（表↔流）没有读者障碍**：本轮把"表基线 + 流更新"与"流基线 + 表更新"两个夹具喂给 PDFium 153、pdf.js 5.7.284、pypdf 6.16.2、MuPDF 1.28.2，**全部 2 页且 `/AIGC` 可读**；PdfPig 读码也逐 offset 探测（`FirstPassParser.cs:198-225`）。PDFium 逐 offset 先试流后试表（`:703-754`），pdf.js 按首 token 分派（`xref.js:765-784`）。所以"更新段与上一段同型"是**写入惯例**（R1 §6.2/§6.3），不是读者硬要求。
6. **`/Prev` 写成间接引用是灾难**：PDFium 的 `GetDirectIntegerFor("Prev")` 对非整数返回 0，链在此终止（`:707-710` 代码注释明说）；pdf.js 有显式 fallback（`xref.js:799-808`）；PdfPig 只认 `NumericToken`（`XrefTable.cs:38-46`）；**pypdf 6.16.2 直接抛 `AttributeError: 'PdfReader' object has no attribute 'stream'`**；MuPDF 1.28.2 读成 **0 页**。本仓写直接整数（`PdfAigcInjector.kt:447`、`:450`），必须保持。
7. **漏写 `/Prev` 同样致命但被部分读者掩盖**：本轮实测 PDFium/pdf.js/pypdf 都能恢复出 2 页，**MuPDF 只有 0 页**；PdfPig 按读码推断会打不开（链非空 → 不触发 brute force，而根对象不在链里）。
8. **重复对象条目：PDFium 后者胜，pdf.js/pypdf/MuPDF 前者胜**。同一段内对象 7 写两条（先新 Info、后旧 Info），PDFium 读到 "Second Copy"，其余三家读到新 Info（含 `/AIGC`）。本仓解析器直接拒绝同段重复（`PdfCrossReference.kt:312-317`），写作也从不产生重复。
9. **pdf.js 的元数据是"软失败"**：`documentInfo` 里 `xref.trailer.get("Info")` 抛非 `MissingDataException` 时只记日志，返回不含 Title/自定义键的基础信息（`document.js:1594-1601`）。实测偏移错时 pdf.js 返回 `Title=null`、无 `/AIGC`，**不抛异常、不影响页数**——只看"能打开"会漏。
10. **PdfPig 会逐对象全文件扫描兜底**（`PdfTokenScanner.cs:890-902`），所以它对错偏移的自愈能力比 PDFium 强；**把 PdfPig 当唯一 oracle 会假绿**。
11. **Acrobat 不能假设宽容**：Adobe 官方帮助明确写“If your PDF file is corrupt or damaged and won't open, you cannot directly repair it”，并在结构问题上报 “An incorrect structure was found in the PDF file”。它的 xref 优先级/修复策略没有一手文档 → 本文列为**未验证**，建议按"严格读者"对待。
12. **设备侧最小矩阵 = 3 条（M1–M3），含一条阴性对照**：M1 产品表型往返（设备页数 + 主机三读者读回 `/AIGC`）；M2 二次注入不同值（页数 + 只有一个 `/AIGC` + 新值）；M3 **故意错偏移的阴性对照**（必须观察到"页数绿、标签缺"）。没有 M3，就无法证明矩阵能抓住第 3 条的静默失败。完整 8 条见 §6。
13. **设备 PDFium ≠ 桌面 PDFium**：本机桌面 pypdfium2 5.13.0 捆包 PDFium **153.0.7999.0**；实测两台模拟器上 `libpdfium.so` 分别是 2024-07-11（API 34，4,198,624 B）与 **2017-07-12**（API 23，约 5 MB）。minSdk=23（`app/build.gradle.kts:28`），所以设备矩阵必须覆盖 API 23 与当前 API，并记录 fingerprint / libpdfium 大小与 mtime。
14. **本轮未跑设备**：树内没有 PDF 注入的设备用例（批次 40 记录 §8 发现 5），在共享模拟器上装一次性 APK 超出研究项范围且污染并发轨；因此 §6 是"待 WS2 执行"的矩阵，不是已通过的门。

---

## 1. 范围与规范基线

### 1.1 本项回答什么

增量更新写入之后，同一份字节在 **Android PdfRenderer（PDFium）/ pdf.js / PdfPig / Acrobat** 眼里是否读到同一个对象图、同一个 Info。三个维度：

- **形态**：更新段是经典 xref 表还是 xref 流；上一段与之不同型时如何。
- **链**：`/Prev` 是否直接整数、是否漏写、`/XRefStm`（hybrid）如何参与。
- **偏移**：条目偏移错位、指向旧副本、两个对象号共用一个偏移、同一对象重复条目。

### 1.2 规范原文（本轮在 `D:/pdfres/2026-11-02/spec/spec.txt` 重新检索，非转述）

ISO 32000-1:2008 §7.5.6（Incremental Updates）：

> “When updating a PDF file incrementally, changes shall be appended to the end of the file, leaving its original contents intact.”
> “The added trailer shall contain all the entries except the Prev entry (if present) from the previous trailer, whether modified or not. In addition, the added trailer dictionary shall contain a Prev entry giving the location of the previous cross-reference section.”
> “Each trailer shall be terminated by its own end-of-file (`%%EOF`) marker.”
> “the most recent copy of each object shall be the one accessed from the file.”

§7.5.8.4（hybrid-reference）关键三句：

> “the XRefStm entry shall not be used in the trailer dictionary of the main cross-reference section but only in an update cross-reference section.”
> “if an entry is not found in any given standard cross-reference section, the search shall proceed to a cross-reference stream specified by the XRefStm entry before looking in the previous cross-reference section”
> “A conforming reader shall look in the cross-reference stream first, shall find the object there, and shall ignore the free entry in the previous section.”

Annex C.2（重建）:

> “When a conforming reader reads a PDF file with a damaged or missing cross-reference table, it may attempt to rebuild the table by scanning all the objects in the file. However, the generation numbers of deleted entries are lost …”

**对本仓的直接含义**：规范允许读者重建，所以"文件能打开"不是正确性证据；可判定的只有"读者按你声明的偏移读到了期望对象"。

### 1.3 本仓写入侧现状（只读核对，未改）

| 事实 | 证据 |
|---|---|
| 更新段类型跟随上一段（表→表、流→流） | `core/data/.../pdf/PdfAigcInjector.kt:60-80` |
| 表条目固定 20 字节 `%010d %05d n \n` | `PdfAigcInjector.kt:268` |
| `/Prev` 写直接整数 | `PdfAigcInjector.kt:447`（流）、`:450`（表） |
| 追加前补 EOL | `PdfAigcInjector.kt:54-55` |
| 流段 `/Index` 按对象号升序分组、`/W=[1,off,gen]` | `PdfAigcInjector.kt:306-310`、`:396-413` |
| 读取侧拒绝 `/Encrypt` 与 `/XRefStm`（hybrid） | `PdfCrossReference.kt:773-780` |
| 读取侧要求表条目恰好 20 字节、EOL 只允许三种 | `PdfCrossReference.kt:304-357` |
| 读取侧拒绝同段重复对象号 | `PdfCrossReference.kt:312-317` |
| 读取侧 `/Prev` 必须是直接整数 | `PdfCrossReference.kt:760-771` |
| 读回器与注入器共用解析器（**不是独立读者**） | `PdfAigcReader.kt:1-12` |

---

## 2. 四个读者怎么读（本轮逐份源码读，非记忆）

### 2.1 PDFium（Android PdfRenderer 的引擎）

- **加载失败 → 整文件重建**：`StartParseInternal` 里 `LoadAllCrossRefTablesAndStreams` 失败即 `RebuildCrossRef()`；`GetRoot` 失败也会重试重建（`cpdf_parser.cpp:248-300`）。
- **表条目固定 20 字节**：`kEntrySize = 20`，按块读取，`pEntry[17] == 'f'` 判 free，否则取前 10 位偏移、11 位起生成号（`cpdf_parser.cpp:540-594`）。
- **只抽检一个偏移**：`VerifyCrossRefTable` 找到第一个 `pos > 0` 的条目，读该处首个 token，对象号不符则整表判定失败（`:371-395`）。合并表按对象号升序，所以实际抽检的几乎总是对象 1。
- **对象加载时校验对象号**：`ParseIndirectObjectAt(pos, objnum)` 里 `result->GetObjNum() != objnum` 返回 `nullptr`（`:1144-1157`）；`ParseIndirectObject` 对此不做逐对象重扫（`:1089-1093`）。
- **链遍历分型**：对每个 `/Prev` 偏移先试 `LoadCrossRefStream`，失败再按表读；流/表混链天然支持（`:703-754`）。
- **hybrid 优先级**：先处理各更新段的 `XRefStm`，再让表条目覆盖（“Cross reference table entries take precedence over cross reference stream entries”，`:449-465`）；**链上最早一段（列表 index 0）的 `XRefStm` 被跳过**（`:453-456`，注释原文 “XRefStm entries should only be used in update sections”）。对单段文件，最早段就是主段——即 §7.5.8.4 所说的“主段不得带 `XRefStm`”；规范文件不会触发这个差异，非规范文件会。
- **`/Prev` 非整数即终止**：代码注释 “When the trailer doesn't have Prev entry or Prev entry value is not numerical, GetDirectInteger() returns 0. Loading will end.”（`:707-710`）。
- **重复条目后者胜**：`AddNormal` 直接覆盖同号旧值（`cpdf_cross_ref_table.cpp:63-78`）。
- **重建按文件正序扫描**，后出现的副本覆盖先出现的（`cpdf_parser.cpp:756-856`）。

### 2.2 pdf.js（Apache-2.0）

- **链遍历**：队列式，先处理 `startxref`；每个 offset 读首 token，`xref` → 表，整数 → 期望 `N G obj` 流；表 trailer 的 `XRefStm` 入队；`/Prev` 为整数入队，**为 `Ref` 时显式 fallback 到 `obj.num`**（`src/core/xref.js:735-820`，fallback 在 `:799-808`）。
- **表解析是 token 式**，不要求 20 字节；`entries[first+i] ??= entry` → **同号先读到的胜**（新段先读，故新段胜）；`entries[0]` 非 free 会整段抛错（`xref.js:230-320`）。
- **段落级错误吞掉**：单段解析异常只 `info()` 记录，继续处理队列其余段；最终无 `topDict` 才抛 `XRefParseException`（`xref.js:735-830`）。
- **整体恢复一次**：初始加载遇到 `XRefParseException` 即以 `recoveryMode=true` 重来一次，走 `indexObjects()` 全文件扫描（`src/core/worker.js:340-350`）。
- **取对象时校验头**：offset 处必须是 `num gen obj`，否则 `XRefEntryException`；生成号偏小有一档 “previous generation” fallback（`xref.js:909-946`）。
- **元数据软失败**：`documentInfo` 对 `trailer.get("Info")` 的非 `MissingDataException` 只记日志，返回无 Title 的基础信息（`document.js:1594-1601`）；未在白名单的自定义键（含 `AIGC`）以字符串收进 `Custom`（`:1614-1651`），经 worker 端口后是**普通对象**（`meta.info.Custom.AIGC`），不是 `Map`。

### 2.3 PdfPig（Apache-2.0）

- **链遍历**：`GetXrefPartsDirectly` 逐个读，表段额外读其 `XRefStm`（任意段都读，不区分主段/更新段）；任一段读不出（`null`）→ 返回空 → 触发 `XrefBruteForcer` 全文件扫描（`FirstPassParser.cs:143-195`、`:28-54`）。
- **合并顺序按文件偏移升序**，后写覆盖先写（`FirstPassParser.cs:56-116`）；`/Prev` 链本身不参与排序（相关逻辑被注释，issue #1208）。
- **表条目偏移容错**：声明偏移处不是 `xref` 时，先试 `%PDF-` 头偏移修正，再在 ±10 字节窗口里找 `xref`（`XrefTableParser.cs:12-48`、`:268-300`）。
- **表解析是 token 式**（不要求 20 字节），需要 `-1 offset gen type` 四元组结构（`XrefTableParser.cs:50-52` 定义 sentinel，`:155-240` 按四元组消费）。
- **`/Prev`、`/XRefStm` 只认 `NumericToken`**，间接引用视为不存在（`XrefTable.cs:38-56`）。
- **逐对象兜底**：offset 处对象号不匹配时，对该引用做全文件扫描找对象（`PdfTokenScanner.cs:890-902`）。

### 2.4 Acrobat

无公开源码，也没有一手的行为规范。能核到的官方口径只有两条：

- Adobe 帮助（Can't open PDF）：“If your PDF file is corrupt or damaged and won't open, **you cannot directly repair it**. You can recreate it with the original source file.”（https://helpx.adobe.com/acrobat/kb/cant-open-pdf.html，2025-04-27 更新，本轮抓取）
- Adobe 帮助（incorrect structure）：“Acrobat may display ‘An incorrect structure was found in the PDF file’ error when you organize pages in a PDF file that contains corrupted elements or has structural issues caused by previous edits or incomplete saves.”（https://helpx.adobe.com/acrobat/kb/incorrect-structure-in-pdf-file.html，2025-04-25，本轮抓取）

结论口径：**不把 Acrobat 当"会自动修"的读者**；它的 xref 优先级与修复策略在本轮**未验证**（U2）。

---

## 3. 容忍度矩阵

### 3.1 形态与 `/Prev` 链

| 场景 | PDFium（桌面 153 实测 / 设备读码） | pdf.js 5.7.284 实测 | PdfPig（读码） | Acrobat |
|---|---|---|---|---|
| 表基线 + 表更新 | 2 页，`/AIGC` 可读 | 2 页，`/AIGC` 可读 | 表+表，正常 | 未验证 |
| 流基线 + 流更新 | 2 页，`/AIGC` 可读 | 2 页，`/AIGC` 可读 | 流+流，正常 | 未验证 |
| **表基线 + 流更新**（混型） | 2 页，`/AIGC` 可读 | 2 页，`/AIGC` 可读 | 逐 offset 探测，可读 | 未验证 |
| **流基线 + 表更新**（混型） | 2 页，`/AIGC` 可读 | 2 页，`/AIGC` 可读 | 同上 | 未验证 |
| 更新 trailer 漏 `/Prev` | 重建 → 2 页，Title 可读 | 恢复 → 2 页，`/AIGC` 可读 | 推断打不开（链非空→不 brute force，根对象不在链内） | 未验证 |
| `/Prev` 写成 `N 0 R` | 链截断 → 重建 → 2 页 | 显式 fallback → 2 页，`/AIGC` 可读 | 视为无 `/Prev` → 推断打不开 | 未验证 |
| hybrid（表段带 `/XRefStm`） | 只纳入**更新段**的 XRefStm，最早段（单段文件即主段）的 XRefStm 跳过；表条目覆盖流条目 | 任意表段的 XRefStm 入队并按队列顺序合并 | 任意表段的 XRefStm 读取，按文件偏移合并 | 未验证 |
| `%%EOF` 与后续对象之间无换行 | 实测 2 页，`/AIGC` 可读 | 实测 2 页，`/AIGC` 可读 | 未测（其 writer 会补 EOL） | 未验证 |

> “实测”= §5 本轮在桌面跑的 12 个夹具；PdfPig 一列为**读码推断**（本机无 .NET 运行时），已标 U3。

**结论**：混型链不需要额外兼容工作（四个读者都吃，PdfPig 读码同样支持）；真正要防的是 **`/Prev` 的类型与是否存在**——这两项在 MuPDF/pypdf 上直接翻车（§5.2）。

### 3.2 偏移与重复条目

把更新段里 Info 条目的偏移做四种破坏（外加上正确偏移作对照），看"页数"与"标签"是否一起翻：

| 场景 | 页数（四家） | `/AIGC`（PDFium） | `/AIGC`（pdf.js） | `/AIGC`（pypdf） | `/AIGC`（MuPDF） |
|---|---|---|---|---|---|
| 正确偏移 | 2/2/2/2 | 有 | 有 | 有 | 有 |
| 指向**旧** Info 副本（stale） | 2/2/2/2 | 无 | 无 | 无 | 无 |
| 偏移 `+1` | 2/2/2/2 | **无**（Title 也空） | **无**（Title=null，软失败） | **有**（自动修） | **有**（自动修） |
| 指向对象 5 的对象头（crossed） | 2/2/2/2 | **无**（Title 空） | **无**（Title=null） | **无** | **有**（逐对象扫描） |
| 同段内对象 7 重复两条（新前旧后） | 2/2/2/2 | **后者胜**（Title=Second Copy） | 前者胜（有 AIGC） | 前者胜 | 前者胜 |

**结论**：
- **页数是四个读者里最钝的指标**，五种破坏全部 2 页。
- PDFium 与 pdf.js 对"偏移错位"**不修**，且失败方式是静默丢元数据。
- pypdf/MuPDF 的"自动修"让它们**不能**用来证明偏移写对了——它们会把错偏移掩盖掉。
- 唯一能同时抓住"偏移写错"和"重复条目"的组合是 **PDFium（或 pdf.js）+ 至少一个不修的读者 + 一条阴性对照**。

### 3.3 可复跑判据总表（本轮全部执行）

| 读者 | 版本 | 观察到 |
|---|---|---|
| PDFium | 153.0.7999.0（pypdfium2 5.13.0） | 混型 OK；`+1`/crossed 静默丢 Info；重复条目后者胜 |
| pdf.js | 5.7.284（pdfjs-dist legacy, Node 26.4.0） | 混型 OK；漏 `/Prev` 与间接 `/Prev` 均恢复；元数据软失败 |
| pypdf | 6.16.2 | 混型 OK；漏 `/Prev` OK；间接 `/Prev` **崩**；`+1` 自动修 |
| MuPDF | 1.28.2 | 混型 OK；漏 `/Prev` / 间接 `/Prev` **0 页**；crossed 自动修 |

---

## 4. 为什么这些差异会出现（机制对照）

| 差异 | 机制 | 证据 |
|---|---|---|
| 表条目宽度 | PDFium 固定 20 字节块读；pdf.js/PdfPig token 化 | `cpdf_parser.cpp:540-594`；`xref.js:230-320`；`XrefTableParser.cs:155-240` |
| 错偏移是否被发现 | PDFium 只抽检首个非零条目；pdf.js/PdfPig 在取对象时逐次校验 | `cpdf_parser.cpp:371-395`、`:1144-1157`；`xref.js:909-946` |
| 错偏移是否被修复 | PDFium 整表重建只在"加载阶段"发生；PdfPig 逐对象全文件扫描；pdf.js 只整篇恢复一次 | `cpdf_parser.cpp:248-300`；`PdfTokenScanner.cs:890-902`；`worker.js:340-350` |
| 重复条目谁胜 | PDFium `AddNormal` 覆盖；pdf.js `??=` 先到先得；PdfPig 按文件偏移后写覆盖 | `cpdf_cross_ref_table.cpp:63-78`；`xref.js:311-314`；`FirstPassParser.cs:56-116` |
| 间接 `/Prev` | PDFium 取直接整数失败→0；pdf.js 有 fallback；PdfPig 只认数字；pypdf 解引用后自身状态未就绪 | `cpdf_parser.cpp:707-710`；`xref.js:799-808`；`XrefTable.cs:38-46`；pypdf traceback（§5.2） |
| hybrid 的 `/XRefStm` | 规范禁止主段使用；PDFium 只纳入更新段（最早段跳过）；pdf.js/PdfPig 任意段都纳入 | §7.5.8.4；`cpdf_parser.cpp:449-456`；`xref.js:769-775`；`FirstPassParser.cs:165-186` |

---

## 5. 本轮实测（可复跑）

### 5.1 夹具

`D:/pdfr6/probe_readers.py`（纯 stdlib，内存构造）生成 12 个夹具：2 个基线 + 10 个变体（case1–case10）。命令与版本：

```
$ python -I D:/pdfr6/probe_readers.py            # pypdf 6.16.2 + PyMuPDF 1.28.2
$ python -I D:/pdfr6/probe_pdfium.py             # PDFium 153.0.7999.0（pypdfium2 5.13.0）
$ cd D:/pdfr6/node && node probe_pdfjs.mjs D:/pdfr6/out   # pdfjs-dist 5.7.284, Node v26.4.0
```

夹具全部 2 页、Info=对象 7、Title/Author 固定；更新段统一重定义对象 7 并加 `/AIGC`（7 键 JSON）。

### 5.2 关键输出（原文摘录）

```
base-table / base-stream        pypdf 2 页  AIGC 缺 | MuPDF 2 页 aigc=null | PDFium 2 页 | pdf.js 2 页 AIGC 缺
case1 表基线+流更新             pypdf 2 页  AIGC 有 | MuPDF aigc string | PDFium 2 页 | pdf.js AIGC 有
case2 流基线+表更新             pypdf 2 页  AIGC 有 | MuPDF aigc string | PDFium 2 页 | pdf.js AIGC 有
case3 表基线+表更新（对照）     pypdf 2 页  AIGC 有 | MuPDF aigc string | PDFium 2 页 | pdf.js AIGC 有
case4 偏移指向旧 Info           pypdf AIGC 缺 | MuPDF aigc null | PDFium 2 页 Title=Original Title | pdf.js AIGC 缺
case5 偏移 +1                   pypdf AIGC 有 | MuPDF aigc string | PDFium 2 页 Title='' | pdf.js pages=2 title=null AIGC 缺
case6 指向对象 5 头             pypdf AIGC 缺 | MuPDF aigc string | PDFium 2 页 Title='' | pdf.js pages=2 title=null AIGC 缺
case7 同段重复对象 7            pypdf AIGC 有 | MuPDF aigc string | PDFium Title=Second Copy | pdf.js AIGC 有
case8 版本间无 EOL              pypdf 2 页 AIGC 有 | MuPDF aigc string | PDFium 2 页 | pdf.js AIGC 有
case9 漏 /Prev                  pypdf 2 页 AIGC 有 | MuPDF pages=0 | PDFium 2 页 | pdf.js 2 页 AIGC 有
case10 /Prev 间接引用           pypdf AttributeError: 'PdfReader' object has no attribute 'stream' | MuPDF pages=0 | PDFium 2 页 | pdf.js 2 页 AIGC 有
```

pypdf 的异常栈（case10）：

```
File ".../pypdf/_reader.py", line 1118, in _read_xref
    return cast(int, new_trailer["/Prev"])
File ".../pypdf/generic/_data_structures.py", line 493, in __getitem__
    return cast(PdfObject, dict.__getitem__(self, key).get_object())
File ".../pypdf/_reader.py", line 553, in get_object
    if hasattr(self.stream, "getbuffer"):
AttributeError: 'PdfReader' object has no attribute 'stream'
```

### 5.3 设备侧只读事实（未装任何东西、未跑仪器化）

```
$ adb devices
emulator-5562 device / emulator-5566 device / emulator-5584 device / emulator-5596 device
$ adb -s emulator-5562 shell getprop ro.build.version.sdk      → 34
$ adb -s emulator-5596 shell getprop ro.build.version.release  → 6.0（sdk 23）
$ adb -s emulator-5562 shell ls -l //system/lib64/libpdfium.so → 4198624  2024-07-11 18:31
$ adb -s emulator-5596 shell ls -l //system/lib64/libpdfium.so → 4987064  2017-07-12 20:05
$ adb -s emulator-5562 shell getprop ro.build.fingerprint
google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys
```

`strings libpdfium.so | grep PDFium` 只得到 `PDFium`，**设备 .so 没有可读的版本串**（U4）。因此设备版本只能用 fingerprint + 文件大小/mtime 记账。

### 5.4 本轮没有做什么

- 没有在模拟器上装任何 APK、没有 `am instrument`、没有跑 Gradle（纪律：门禁阶段之外不跑；且设备绿必须绑定源快照 + APK sha）。
- 没有执行 PdfPig（无 .NET 运行时）；PdfPig 结论全部来自读码，标 U3。
- 没有执行 Acrobat（无安装/无自动化入口）；标 U2。

---

## 6. 设备侧最小校验矩阵（交 WS2 执行）

### 6.1 为什么"最小"是这三条

判据要能区分三种失败：**打不开**、**能打开但标签不在**、**标签在但值不对**。页数只能抓第一种；`/AIGC` 读回才能抓第二、三种。设备上 `PdfRenderer` 没有 metadata 接口，所以"读回"必须发生在**主机**（拉回产物）或**测试 APK 内的独立库**。三条缺一不可：

- M1 证明"产品形态能往返"；
- M2 证明"第二次修订仍只有一份标签且值更新"；
- M3 是**阴性对照**：证明 M1/M2 的判据真的能抓住"页数绿但标签缺"，否则 M1 的绿可能只是没测到。

### 6.2 矩阵

| # | 夹具 / 动作 | 设备侧断言（`PdfRenderer`） | 主机侧断言（独立读者） | 抓什么失败 |
|---|---|---|---|---|
| **M1** | 真实 `DeterministicMistakePdfRenderer` 2 页 → 表型注入 → 写入 app 外部目录 | `pageCount == 2`；`page(0).width/height` 与注入前一致；`page(0).render(64×64)` 非全白 | 拉回后 pypdf **与** PDFium **与** pdf.js 三读者：`Info./AIGC` 7 键全等；Title/Author 与注入前一致 | 打不开；标签缺失；Info 合并不覆盖失败 |
| **M2** | M1 产物再注入**不同** `ProduceID` | `pageCount == 2`；可渲染 | 最新 Info 只有 1 个 `/AIGC`；值=新；文件长度 > M1 | 追加段覆盖错误；多份标签；链断开 |
| **M3** | **阴性对照**：手工构造"Info 条目偏移 +1"的表型更新（或指向旧 Info） | **仍然 `pageCount == 2`、可渲染** | `/AIGC` **必须读不到**（或值不符） | 证明"页数绿"抓不住静默丢标签；若 M3 读到 AIGC，说明夹具或判据错了 |
| D4 | 流型夹具（防御形态，产品不产） | `pageCount == 2` | 三读者 `/AIGC` 可读；追加段含 `/Type /XRef` | 流型路径失效（R1 的 `previousIsStream` 分支） |
| D5 | 同值二次注入 | `pageCount == 2` | 字节与 M1 逐字节相等（JVM 已覆盖，设备只验可开） | 幂等回归 |
| D6 | 加密 / 线性化 / hybrid 输入 | 不产生文件 | 注入抛对应错误码（JVM 已覆盖 3 例；设备只验装配层不发布半成品） | fail-closed 被绕过 |
| D7 | 无 `/Prev` 的更新段（对照） | 记录实际值，**不作通过判据**（PDFium/pdf.js 会恢复成 2 页，MuPDF 已示 0 页） | 记录四读者差异 | 说明"恢复"是读者行为，不是文件正确 |
| D8 | `%%EOF` 后无 EOL 追加 | `pageCount == 2` | 三读者 `/AIGC` 可读 | 追加边界 |

### 6.3 证据绑定（沿用批次 40/41 纪律）

每轮必须记录并落盘：`git rev-parse HEAD`、APK `sha256`（`adb shell pm path` + 主机 `sha256sum`）、`ro.build.fingerprint`、`ro.build.version.sdk`、`libpdfium.so` 大小 + mtime、`am instrument -w -e class <单类>` 的 `OK (n tests)` 与 `INSTRUMENTATION_CODE`。**禁止** `:app:connectedLocalFirstDebugAndroidTest`（计划 §4）。

### 6.4 执行顺序建议

M3 先跑：如果 M3 出现"页数绿 + 标签也在"，先修夹具/判据，再跑 M1/M2；否则后面的绿没有意义。

---

## 7. 已知坑（按危害排序，每条给"怎么防"）

| # | 坑 | 证据 | 防 |
|---|---|---|---|
| P1 | **`PdfRenderer` 只有页数/渲染，没有 metadata API**；页数绿不能证明标签在 | AOSP `libs/hwui/jni/pdf/PdfRenderer.cpp:42-113`、`PdfUtils.cpp:101-131`；本文 §5.2 case5/6 | 设备断言必须加主机读回；不接受"页数正确"作为 `/AIGC` 判据 |
| P2 | **桌面 PDFium ≠ 设备 PDFium**；设备 API 23 的 .so 是 2017 年的 | §5.3（2024-07-11 vs 2017-07-12）；桌面 153.0.7999.0 | 设备矩阵覆盖 minSdk 23 与当前 API；记 fingerprint + .so 大小/mtime；桌面绿只作预筛 |
| P3 | 表条目必须是 20 字节；PDFium 按块读，宽度错会整体错位 | `cpdf_parser.cpp:540-594`；本仓 `PdfAigcInjector.kt:268` | 写入格式钉死 20 字节；解析器严格校验（`PdfCrossReference.kt:304-357`） |
| P4 | PDFium 只抽检合并表第一个非零条目，Info 错偏移不触发重建 | `cpdf_parser.cpp:371-395`；§5.2 case5/6 | 阴性对照 M3；主机 PDFium 读回必须是判据之一 |
| P5 | 同段重复对象：PDFium 后者胜，pdf.js/pypdf/MuPDF 前者胜 | `cpdf_cross_ref_table.cpp:63-78`；`xref.js:311-314`；§5.2 case7 | 写入永不重复；解析器拒绝重复（已有） |
| P6 | pdf.js 元数据软失败：Title 变 null、不抛异常 | `document.js:1594-1601`；§5.2 case5/6 | 断言"Title + AIGC 同时存在"，不能只断"getMetadata 成功" |
| P7 | PdfPig 逐对象 brute force 会掩盖错偏移 | `PdfTokenScanner.cs:890-902` | 不用 PdfPig 作唯一 oracle；oracle 至少含 PDFium/pdf.js |
| P8 | `/Prev` 写成间接引用：PDFium 截断链、PdfPig 无视、pypdf 崩、MuPDF 0 页 | `cpdf_parser.cpp:707-710`、`XrefTable.cs:38-46`、§5.2 case10 | 写直接整数（`PdfAigcInjector.kt:447/450`）；解析器拒绝间接（`PdfCrossReference.kt:760-771`） |
| P9 | 漏写 `/Prev`：MuPDF 0 页，其余靠重建/恢复掩盖 | §5.2 case9 | 写入必须带 `/Prev`；设备判据断言 `pageCount == 期望`，不是 `>= 0` |
| P10 | hybrid 的 `/XRefStm` 优先级：PDFium 只纳入更新段的 XRefStm（最早段跳过），pdf.js/PdfPig 任意段都纳入 | §7.5.8.4；`cpdf_parser.cpp:449-456`；`xref.js:769-775`；`FirstPassParser.cs:165-186` | 不写 `/XRefStm`；读取侧直接拒绝 hybrid（已有，`PdfCrossReference.kt:773-780`） |
| P11 | 无 EOL 追加：四读者都能开，但规范要求 `%%EOF` 独占末行 | §7.5.6；§5.2 case8 | 追加前补 EOL（`PdfAigcInjector.kt:54-55`） |
| P12 | “多数读者能开”不能证明偏移正确：pypdf/MuPDF 会自动修 | §5.2 case5/6 | 偏移由构造保证 + M3 阴性对照；不以"能打开"作偏移正确性证据 |
| P13 | Acrobat 不会承诺修复损坏 PDF；结构问题直接报错 | Adobe 帮助两页（§2.4） | 把 Acrobat 当严格读者；不以它做唯一验证，也不假设它容忍 |
| P14 | 重建会丢已删除条目的 generation；重建后的对象图可能与 xref 声明不同 | ISO 32000-1 Annex C.2；`cpdf_parser.cpp:756-856` | 重建只用于解释读者行为，不作为"文件正确"的证据 |

---

## 8. 对本仓的落地建议（只映射到计划已有项，不新增）

1. **WS2 第 16 项（设备实跑证据）的验收要改口径**：`PdfRenderer` 打开 + 页数一致只是必要条件；必须加 **M3 阴性对照**与**主机三读者读回 `/AIGC`**，否则"设备绿"不能支撑 KD-75 的"标签已写入"。这直接消灭的失败是 §5.2 case5/6 的"页数绿、标签缺"。
2. **WS2 第 11–13 项（表型/流型/幂等）**：按 §6.2 M1/M2/D4/D5 实现；流型只是防御形态（Skia 永不出流型，R1 §2.11）。
3. **WS2 第 14 项（畸形 PDF fail-closed 四类）**：设备侧只需验"失败不产出文件"；具体错误码已在 JVM 覆盖（`PdfAigcMalformedTest.kt:13-176`），设备不重复枚举。
4. **WS2 第 17 项（`core/export` 接缝 patch 草案）**：本文不涉及；但接缝处的判据应包含"注入失败 → 导出失败、不发布"（R1 §5.1）。
5. **不要为验证引入生产依赖**：主机拉回 + 现成三读者（pypdf / pdf.js / pypdfium2）已能覆盖；若必须端上读回，`pdfbox-android` 只能进 `androidTest`（Apache-2.0），且需单独核版本/锁文件——本项不建议，因为主机读回零新增依赖。
6. **保持现有的严格读取**：hybrid/加密/线性化/间接 `/Prev`/重复条目/非 20 字节条目一律 fail-closed。它们会拒绝一些"读者其实能打开"的外部文件，但本注入器的输入是自家 Skia 产物（单段表型），拒绝只影响非产品路径，换来的是不产出错标签文件。

---

## 9. 未验证与假设

| # | 项 | 当前证据 | 状态 | 消解动作 |
|---|---|---|---|---|
| U1 | API 23 / API 34 模拟器上 12 个夹具的实际表现 | 只读到了 .so 大小/日期与 fingerprint；树内无 PDF 注入设备用例 | **未跑设备** | WS2 按 §6 落用例后 `am instrument` 单类执行 |
| U2 | Acrobat 的 xref 优先级、是否静默重建、对混型链的态度 | 仅两条 Adobe 帮助页（不能修 / incorrect structure） | **未验证** | 有桌面 Acrobat 时用同一批夹具做一次人工矩阵；否则按严格读者处理 |
| U3 | PdfPig 对 case9（漏 `/Prev`）/case10（间接 `/Prev`）/case5/6 的实际行为 | 读码推断（`FirstPassParser.cs:143-195`、`XrefTable.cs:38-56`、`PdfTokenScanner.cs:890-902`） | **推断** | 有 .NET 时跑同一批夹具（`dotnet run` 小工具） |
| U4 | 设备 `libpdfium.so` 的具体版本 | `strings` 只得到 `PDFium`；无版本串 | **未取得** | 用 fingerprint + 大小/mtime 记账；或在设备上跑一个只读 `FPDF_GetVersion` 探针 |
| U5 | PDF 2.0 读者差异（UTF-8 串、xref 流规则） | 未取得 ISO 32000-2 全文 | **未验证** | 若未来接入 2.0 输入再核 |
| U6 | 设备 PDFium 与桌面 PDFium 在同一夹具上的差异量级 | 只有版本/日期差异，无同夹具对照 | **未验证** | 由 U1 顺带产出（设备结果 vs §5.2 桌面结果并排） |
| U7 | Android `PdfRenderer` 在 API 35+ 是否新增 metadata 接口 | 只核到 API 33 的 AOSP JNI；本项目 compileSdk 37 | **未验证** | 若要在设备直接读 metadata，先核 `PdfRenderer` 公开 API |

**假设**（明确标注，不作为结论）：PdfPig 对 case9/case10 会打不开；MuPDF 的 0 页是"链终止后不重建页树"而非夹具错误（pypdf/PDFium/pdf.js 在同一字节上都能恢复，说明夹具本身合法可读）。

---

## 10. 来源清单

### 规范 / 官方

- ISO 32000-1:2008 全文（本地副本 `D:/pdfres/2026-11-02/spec/pdf32000.pdf`，sha256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`；本轮检索 `spec.txt` §7.5.6、§7.5.8.4、Annex C.2）：<https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf>
- Adobe 帮助 “Why can't I open a PDF”（2025-04-27；本轮抓取；原文含 “you cannot directly repair it”）：<https://helpx.adobe.com/acrobat/kb/cant-open-pdf.html>
- Adobe 帮助 “An incorrect structure was found in the PDF File”（2025-04-25；本轮抓取）：<https://helpx.adobe.com/acrobat/kb/incorrect-structure-in-pdf-file.html>

### 实现源码（本轮下载并逐段读取）

| 项目 | 版本/提交 | License | 本轮读的文件 |
|---|---|---|---|
| PDFium | `chromium/pdfium` main `a84323421e94f484faca52dd9d027934eba42ab8`（GitHub API `pushed_at` 2025-11-19，542★） | BSD-3-Clause（LICENSE 头 “Redistribution and use in source and binary forms…”；GitHub API `spdx` 报 NOASSERTION） | `core/fpdfapi/parser/cpdf_parser.cpp`、`core/fpdfapi/parser/cpdf_cross_ref_table.cpp` |
| pdf.js | `mozilla/pdf.js` master `6a12ce7c943bbed354f75243812b18bfe17ba480`（Apache-2.0，54,077★） | Apache-2.0 | `src/core/xref.js`、`src/core/document.js`、`src/core/worker.js` |
| PdfPig | `UglyToad/PdfPig` master `c46d2c70e072647c9036a353e87b99c5411d44f1`（Apache-2.0，2,611★） | Apache-2.0 | `Parser/FileStructure/FirstPassParser.cs`、`XrefTableParser.cs`、`XrefTable.cs`、`Tokenization/Scanner/PdfTokenScanner.cs`、`CrossReference/CrossReferenceTable.cs` |
| AOSP PdfRenderer | `aosp-mirror/platform_frameworks_base` `android-13.0.0_r1` | Apache-2.0 | `libs/hwui/jni/pdf/PdfRenderer.cpp`、`PdfUtils.cpp`、`PdfUtils.h` |

说明：PDFium 的 GitHub 镜像 `pushed_at` 停在 2025-11，但 `main` 的 HEAD sha 仍是本轮实际读取的那份；引用时以 sha 为准。

### 桌面读者（本轮实跑）

- pypdf 6.16.2；PyMuPDF 1.28.2（MuPDF 1.28.2）；pypdfium2 5.13.0（PDFium 153.0.7999.0）；pdfjs-dist 5.7.284 + Node v26.4.0。

### 仓内（本轮读取）

- `.zcode/plans/plan-2026-11-03-batch-41.md:21`、`:41-47`、`:117-121`、`:125`
- `docs/research/2026-11-02-batch-40-record.md:291`、`:314`
- `docs/research/2026-11-02-pdf-incremental-aigc.md` §2.2–§2.7、§6.2–§6.4、§8.3–§8.5
- `docs/known-defects.md:2879`（KD-75）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/pdf/PdfAigcInjector.kt:54-55`、`:60-80`、`:268`、`:306-310`、`:396-413`、`:447`、`:450`
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/pdf/PdfCrossReference.kt:304-357`、`:312-317`、`:760-771`、`:773-780`（hybrid 拒绝）
- `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/pdf/PdfAigcReader.kt:1-12`
- `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/pdf/PdfAigcMalformedTest.kt:13-176`
- `app/build.gradle.kts:16`、`:28-29`（compileSdk 37 / minSdk 23 / targetSdk 36）

### 本轮执行记录（可复跑）

```
python -I D:/pdfr6/probe_readers.py
python -I D:/pdfr6/probe_pdfium.py
cd D:/pdfr6/node && node probe_pdfjs.mjs D:/pdfr6/out
/c/Android/Sdk/platform-tools/adb.exe devices
/c/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell getprop ro.build.version.sdk
/c/Android/Sdk/platform-tools/adb.exe -s emulator-5562 shell ls -l //system/lib64/libpdfium.so
```

夹具生成器与三个探针在仓库外 `D:/pdfr6/`（`probe_readers.py`、`node/probe_pdfjs.mjs`），未入仓；本文只登记方法与结果。
